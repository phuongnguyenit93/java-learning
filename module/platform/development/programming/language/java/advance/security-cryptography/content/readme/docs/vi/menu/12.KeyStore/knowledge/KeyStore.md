<a id="back-to-top"></a>

# 12. KeyStore

## Menu
- [12.1 Mô hình KeyStore và phân biệt với truststore hoặc CertStore](#key-store-001)
- [12.2 PrivateKeyEntry, SecretKeyEntry, TrustedCertificateEntry và alias](#key-store-002)
- [12.3 PKCS12, JKS và các loại kho do provider cung cấp](#key-store-003)
- [12.4 Tạo kho mới, load, store và kiểm tra đúng kiểu entry](#key-store-004)
- [12.5 Phân biệt mật khẩu kho với thông tin bảo vệ từng khóa](#key-store-005)
- [12.6 Đọc khóa cùng chuỗi chứng thư và kiểm tra alias hoặc mật khẩu sai](#key-store-006)
- [12.7 KeyStore.Builder, LoadStoreParameter và bảo vệ mật khẩu trong ứng dụng](#key-store-007)
- [12.8 Tạo, liệt kê, nhập, xuất và chuyển đổi kho bằng keytool](#key-store-008)
- [12.9 Phạm vi của cacerts, kho tin cậy riêng và tác động khi thay đổi tập CA được tin](#key-store-009)

## <a id="key-store-001">12.1 Mô hình KeyStore và phân biệt với truststore hoặc CertStore</a>

<details>
<summary>Xem chi tiết</summary>

# 12.1 Mô hình KeyStore và phân biệt với truststore hoặc CertStore

Trong Java Cryptography Architecture (JCA), **`KeyStore`** là một API quản lý **khóa mật mã và chứng chỉ** dưới những tên gọi (*alias*) có thể tra cứu. Trong hệ thống TLS/PKI, người ta còn nói **keystore**, **truststore** và **`CertStore`**. Ba thuật ngữ nghe gần giống, nhưng khác nhau về **loại dữ liệu**, **mục đích**, **cách sử dụng trong cơ chế xác thực** và **khái niệm tin cậy**:

- **KeyStore** (viết hoa) là **class/API JCA** `java.security.KeyStore` có các entry như private key kèm certificate chain, secret key, hoặc certificate được đánh dấu tin cậy.
- **keystore dùng làm kho danh tính** (*identity keystore*, thường viết thường) là **vai trò triển khai** của một `KeyStore`: nơi ứng dụng tìm private key và certificate chain của **chính mình** để ký/chứng minh danh tính.
- **truststore** là **vai trò triển khai** của một `KeyStore`: nơi lưu chứng chỉ/điểm tin cậy mà ứng dụng sử dụng để **đánh giá bên đối tác**. Java không có một JCA class tiêu chuẩn độc lập tên `TrustStore`.
- **`CertStore`** là **API khác** thuộc `java.security.cert`, chuyên **tìm/thu thập certificates và CRLs** làm dữ liệu *ứng viên* để xây dựng hoặc kiểm tra đường dẫn chứng chỉ. Có certificate trong `CertStore` **không khiến nó trở thành trusted**.

Bài thuộc nhóm **12. KeyStore** và hướng tới Java 17+. Ta sẽ xây dựng mental model, đối chiếu cấu trúc và luồng TLS, thực hành lưu/đọc PKCS#12, tạo identity keystore + truststore bằng `keytool`, dùng `KeyManagerFactory`, `TrustManagerFactory`, `CertStore` và `PKIXParameters`, rồi phân tích những sai lầm thường gặp trong production.

## 1. Hình dung bốn thành phần trong một kết nối TLS

Một JVM có thể đồng thời cần **chứng minh danh tính của mình** và **kiểm chứng danh tính của bên kia**. Hai trách nhiệm này đi qua những đối tượng khác nhau:

```text
                       ỨNG DỤNG JAVA / TLS
                               |
              +----------------+----------------+
              |                                 |
       "Tôi là ai?"                       "Tôi tin ai?"
              |                                 |
    Identity KeyStore                      Truststore
    +--------------------+                 +----------------------+
    | alias: server      |                 | alias: trusted-ca     |
    | PrivateKeyEntry    |                 | TrustedCertificate-   |
    | - PRIVATE KEY      |                 | Entry (CA/cert được   |
    | - leaf cert        |                 | chấp thuận)          |
    | - chain (nếu có)   |                 +----------------------+
    +--------------------+                           |
              |                                      |
    KeyManagerFactory                       TrustManagerFactory
              |                                      |
              v                                      v
        KeyManager[]                           TrustManager[]
              |                                      |
              +------------- SSLContext -------------+
                                     |
                      Handshake TLS xác thực
                                     |
                      +--------------+--------------+
                      |                             |
              certificate chain               trust anchor
                bên đối tác                    từ truststore
                      |                             |
                      v                             v
              CertPathBuilder/             CertPathValidator/
              CertPathValidator             PKIX trust policy
                      ^                             ^
                      |                             |
              CertStore (certificates/CRLs ứng viên,
              tra cứu để tìm intermediate/revocation)
```

Sơ đồ biểu diễn **vai trò logic**, không phải mọi provider hay mọi phiên TLS đều thực hiện đúng một chuỗi gọi method bên trong. Nhiều môi trường dùng KMS/HSM hoặc cấu hình JSSE mặc định thay vì các file trên đĩa.

**Câu hỏi phải đặt đúng:** *“Khóa riêng của **tôi** nằm ở đâu?”* khác với *“Tôi chấp nhận certificate **của ai**?”*; và cả hai khác với *“Tôi tìm thêm intermediate certificate/CRL **ở đâu** để đánh giá một chuỗi?”*

## 2. So sánh KeyStore, truststore và CertStore

| Tiêu chí | KeyStore (API / kho danh tính) | Truststore (vai trò của KeyStore) | CertStore (API Java) |
| --- | --- | --- | --- |
| Class | `java.security.KeyStore` | Vẫn thường là **`java.security.KeyStore`** | **`java.security.cert.CertStore`** |
| Mục đích chính | Quản lý khóa và chứng chỉ theo alias; với identity store là chứng chỉ + private key của ứng dụng | Xác định cert/trust anchor được cấu hình tin cậy khi kiểm tra đối tác | Cung cấp **certificates và CRLs ứng viên** cho tra cứu/PKIX |
| Dữ liệu | `PrivateKeyEntry`, `SecretKeyEntry`, `TrustedCertificateEntry` tùy type/provider | Chủ yếu `TrustedCertificateEntry` theo chính sách trust | `Certificate` và `CRL`, **không chứa private key** |
| Nhận diện item | Alias như `server`, `root-ca`, `jwt-signing` | Alias như `company-ca` | `CertSelector`, `CRLSelector` để lọc |
| Java gọi | `getInstance`, `load`, `getEntry`, `getKey`, `store` | `KeyStore.load` + `TrustManagerFactory.init(store)` hoặc PKIX trust anchors | `CertStore.getInstance("Collection", params)`, `getCertificates`, `getCRLs` |
| File định dạng | Thường PKCS#12 (`.p12`, `.pfx`), JKS; có backend không phải file | Có thể cũng dùng PKCS#12/JKS hoặc provider khác | Không phải một file-key-store tiêu chuẩn; có thể là Collection, LDAP hay nguồn khác |
| Trust decision? | **Không tự động**: phụ thuộc entry và API tiêu thụ | Định nghĩa/chứa **trust anchors** theo chính sách bên nhận | **Không**: tìm được certificate chưa có nghĩa nó đáng tin |
| Khi dùng TLS | `KeyManagerFactory` tạo `KeyManager` dùng private key + certificate chain | `TrustManagerFactory` tạo `TrustManager` kiểm tra peer | Có thể hỗ trợ PKIX tìm intermediate/CRL, **không thay thế TrustManager** |
| Ai cần truy cập? | Bên giữ khóa riêng/secret key; quyền rất chặt | Thành phần kiểm chứng bên đối tác; thay đổi anchor phải được kiểm soát | Thành phần PKI cần truy vấn certificate/CRL và có cơ chế kiểm tra tính xác thực |
| Lỗi tư duy hay gặp | “KeyStore chỉ là file chứa password” | “Truststore là một lớp Java hoặc thuật toán đặc biệt” | “Nhét cert vào CertStore là nó thành trusted CA” |

**Điểm quyết định:** *truststore không phải một API lưu trữ mới*. Nó dùng cùng cấu trúc `KeyStore` nhưng **được giao một nhiệm vụ bảo mật khác**. Một file PKCS#12 có thể chứa nhiều loại entry, song **không nên trộn quyền sở hữu private key và trust anchors tùy tiện**, vì việc gộp làm tăng phạm vi ảnh hưởng của sự cố và khó quản lý chính sách.

## 3. Kiến trúc KeyStore: engine, type, provider, alias và entry

`KeyStore` là một **engine class** của JCA: hành vi lưu trữ phụ thuộc **type** và **provider**. Class biểu diễn **kho logic**, không ràng buộc tất cả kho phải là file trên filesystem.

```text
KeyStore.getInstance("PKCS12")
          |
          v
 JCA tìm provider hỗ trợ PKCS12
          |
          v
  KeyStore instance (chưa load)
          |
      load(inputStream, password)  hoặc load(null, password) để tạo kho mới
          |
          v
      aliases -> Entries
          |
      getEntry / getKey / getCertificate / ...
          |
      store(outputStream, password)  (với backend hỗ trợ ghi stream)
```

### 3.1 Ba Entry quan trọng

JDK định nghĩa các loại entry chính:

| Entry | Có gì bên trong? | Ví dụ | API nhận diện |
| --- | --- | --- | --- |
| **`KeyStore.PrivateKeyEntry`** | Một `PrivateKey` và **certificate chain** tương ứng với public key của private key | Khóa TLS server, client mTLS, signing key có certificate | `entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class)`, `isKeyEntry(alias)` |
| **`KeyStore.SecretKeyEntry`** | Một `SecretKey` đối xứng; không có X.509 certificate chain | AES key dùng mã hóa dữ liệu khi type/provider cho phép | `entryInstanceOf(alias, KeyStore.SecretKeyEntry.class)`, `isKeyEntry(alias)` |
| **`KeyStore.TrustedCertificateEntry`** | **Một certificate** được chủ kho cấu hình tin cậy | CA root hoặc peer cert được chính sách cho phép | `entryInstanceOf(alias, KeyStore.TrustedCertificateEntry.class)`, `isCertificateEntry(alias)` |

`KeyStore.Entry` là interface cha; ba lớp trên không phải cùng một định dạng bằng bytes. Provider/type có thể giới hạn loại entry được hỗ trợ hoặc cách bảo vệ chúng.

**Một số lời gọi dễ nhầm:**

- `store.containsAlias(alias)`: kiểm tra alias có tồn tại.
- `store.aliases()`: liệt kê tên alias (không được giả định thứ tự).
- `store.isKeyEntry(alias)`: đúng cho cả `PrivateKeyEntry` và `SecretKeyEntry` trong type/provider phù hợp.
- `store.isCertificateEntry(alias)`: đúng cho **certificate-only entry**, không phải lời khẳng định mọi alias có chứa certificate.
- `store.getKey(alias, password)`: trả key cho key entry; **certificate-only entry không trả private key**.
- `store.getCertificate(alias)`: có thể trả certificate của `TrustedCertificateEntry` hoặc certificate đầu tiên trong chain của `PrivateKeyEntry`. Vì vậy **`getCertificate(alias) != null` không có nghĩa alias là trusted certificate entry**.
- `store.getCertificateChain(alias)`: dùng cho private-key entry có chain; không mặc định có cho trusted-cert entry.
- `store.getEntry(alias, protection)`: trả kiểu `KeyStore.Entry`; dùng `instanceof`/ `entryInstanceOf` để kiểm tra loại và tránh cast sai.
- `store.setEntry`, `setKeyEntry`, `setCertificateEntry`, `deleteEntry`: thao tác quản trị, có ràng buộc provider/type và quyền thay đổi.

**Alias là định danh tra cứu**, không phải hostname hoặc identity được chứng nhận bởi PKI. Một alias tên `production-root` không làm certificate trở thành CA hợp lệ; chính sách trust phải kiểm tra nguồn certificate, key usage và mục đích sử dụng. **Phân biệt chữ hoa/chữ thường trong alias tùy implementation**; không chọn hai alias chỉ khác nhau về case.

### 3.2 Keystore password và key password

Một keystore file có thể cần **store password** khi `load`/`store` để bảo vệ tính toàn vẹn hoặc cơ chế lưu trữ; từng **key entry** có thể yêu cầu password để giải mã/truy cập. Mối quan hệ này **phụ thuộc type/provider**:

```text
PKCS12 file
  |
  +-- store password      (theo định dạng/provider)
  |
  +-- alias server
        +-- PrivateKeyEntry (key password/protection khi đọc khóa)
        +-- leaf certificate
        +-- certificate chain
```

Trong nhiều cấu hình PKCS#12 thực tế, store password và key password được sử dụng giống nhau để tương thích, nhưng không được coi đây là quy tắc áp dụng cho mọi provider. `KeyManagerFactory.init(identity, keyPassword)` nhận password dùng truy cập **key entries**, trong khi `KeyStore.load(stream, storePassword)` dành cho **load kho**.

Không dùng mật khẩu demo `changeit` trong production. Với triển khai thật, lấy credential từ secret manager/cơ chế triển khai được phê duyệt, phân quyền filesystem chặt chẽ, áp dụng rotation và tránh lưu password trên command line (có thể lộ qua process listing, shell history hay pipeline logs). Password bảo vệ PKCS#12 **không thay thế** quản lý khóa trong HSM/KMS khi cần key non-exportable.

### 3.3 File store và hardware/token store

- **PKCS#12:** định dạng mang tính tương tác tốt để chứa khóa và chứng chỉ, thông dụng ở JDK hiện đại; `KeyStore.getInstance("PKCS12")`. **Đây là default type từ JDK 9** trong triển khai JDK điển hình nhưng có thể bị đổi bởi security property `keystore.type`.
- **JKS:** định dạng Java legacy; vẫn gặp trong hệ thống cũ; không nên mặc định chọn chỉ vì thói quen nếu hệ thống mới có khả năng dùng PKCS#12.
- **JCEKS:** một keystore type lịch sử của provider Java cho một số secret/private key; không nhầm với `JKS` hay PKCS#12; đánh giá lại policy và migration.
- **PKCS#11:** API/provider có thể liên kết smart card/HSM, nơi key tồn tại dưới dạng **handle**. Không có nghĩa có file `.p12` và không nhất thiết cho phép `getEncoded()` private key. `load(null, ...)` có thể là phương thức phù hợp để kết nối token trong cấu hình provider, **không** phải “tạo file keystore mới”.
- **Windows-MY/macOS keystore provider** (tùy môi trường): tích hợp kho của hệ điều hành; không phải type được bảo đảm có trên mọi JDK/Linux container.

**Điều cần nhớ:** `KeyStore.getDefaultType()` trả type theo cấu hình runtime, **không bảo đảm luôn là `"PKCS12"`** trên mọi hệ thống. Trong ví dụ và các luồng import/export, ghi type tường minh để tránh bất ngờ do cấu hình thay đổi.

## 4. Identity keystore và truststore trong TLS/mTLS

Với **TLS server authentication**, server thường trình bày certificate chain để client kiểm tra. **Server identity keystore** giữ private key và certificate chain. **Client truststore** chứa trust anchors/certificates mà client chấp nhận theo chính sách.

```text
SERVER                                      CLIENT
+---------------------------+               +----------------------------+
| identity KeyStore         |               | truststore (KeyStore)      |
| alias "server"            |               | trusted CA/cert           |
| PrivateKeyEntry           |               | TrustedCertificateEntry   |
| private key + cert chain  |               +----------------------------+
+---------------------------+                         |
             |                                         v
       KeyManagerFactory                       TrustManagerFactory
             |                                         |
       Server KeyManager                        Client TrustManager
             |                                         |
             +----------- TLS handshake --------------+
              server gửi certificate chain, chứng minh nắm private key
                    client xác minh certificate/path/hostname/policy
```

Với **mTLS**, client **cũng có identity keystore** để trình bày private key + certificate chain của client, và server **cũng có truststore**/trust policy để kiểm tra certificate client. Cả hai đầu có thể có cả **KeyManager** và **TrustManager**, nhưng **vai trò không đổi**.

### 4.1 KeyManagerFactory và TrustManagerFactory

| API | Nguồn chính | Chức năng |
| --- | --- | --- |
| `KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())` | `KeyStore` chứa private key và certificate chain; `init(identityStore, keyPassword)` | Cấp `KeyManager[]` để chọn danh tính/local credentials |
| `TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())` | `KeyStore` chứa cert/trust anchors; `init(trustStore)` | Cấp `TrustManager[]` để đánh giá chứng chỉ bên đối tác |
| `SSLContext.getInstance("TLS")` và `init(keyManagers, trustManagers, random)` | Cả hai nhóm manager | Tạo cấu hình bảo mật cho TLS sockets/engines |

**Không đảo hai factory:** thêm private key vào nơi dùng `TrustManagerFactory` không có nghĩa private key sẽ được gửi cho peer. Tương tự, chỉ thêm CA certificate vào identity store không cung cấp private key cho server để hoàn tất authenticated TLS handshake.

Trong production, tránh tự viết `X509TrustManager` bỏ qua xác thực:

```java
// ANTI-PATTERN: chấp nhận mọi server certificate để "sửa SSLHandshakeException".
// Không sử dụng trong ứng dụng thật. Đây chỉ là đoạn nhận diện lỗi.
public void checkServerTrusted(
        java.security.cert.X509Certificate[] chain, String authType) {
    // Không kiểm tra gì cả => vô hiệu hóa xác thực peer.
}
```

Đoạn trên là **method minh họa, không phải một class đầy đủ**; mục đích là nhận diện hành vi nguy hiểm. Giải pháp đúng là phân tích certificate chain, trust anchors, hostname/SAN, thời hạn, EKU, policy, revocation phù hợp, chứ không phải vô hiệu hóa `TrustManager`.

### 4.2 Truststore không tự xác thực hostname

Một certificate chain có thể bắt nguồn từ CA được tin cậy nhưng certificate cuối cùng **không dành cho hostname** mà client đang truy cập. Việc kiểm tra **danh tính điểm cuối** (ví dụ `api.example.com` so với DNS Subject Alternative Name) là một yêu cầu **riêng** phải được thực thi bởi TLS client/giao thức phù hợp.

Đặc biệt, khi tự tạo `SSLSocket` hay `SSLEngine`, kiểm tra endpoint identification đã được bật theo contract API; đừng suy ra mọi low-level TLS socket tự bật hostname verification giống `HttpsURLConnection` hoặc các HTTP client cấp cao. **Trust anchor hợp lệ không đồng nghĩa chứng chỉ khớp hostname.**

### 4.3 Truststore có thể tin root CA hoặc leaf cert

Truststore thường chứa CA root/CA chuyên dụng làm **trust anchor**. Với kiểm thử hoặc một số thiết kế pinning, người ta có thể chấp thuận trực tiếp certificate của endpoint. Hai cách có hệ quả khác nhau:

- **Tin CA root:** CA có thể cấp nhiều certificate hợp lệ theo policy/constraints; cần đánh giá chain, tên máy, thời gian, EKU, usages, revocation nếu được cấu hình.
- **Tin trực tiếp leaf:** giới hạn danh tính theo certificate cụ thể, nhưng rotation/renewal sẽ phức tạp; cách diễn giải trust anchor phụ thuộc API/policy.
- **Tin intermediate CA:** cần suy nghĩ kỹ phạm vi ủy quyền và constraints; không thêm tùy tiện chỉ vì handshake đang lỗi.

**Không nhập một certificate không rõ nguồn rồi mặc nhiên tin nó.** `keytool -importcert` thiết lập một trust decision của người quản trị, không thể thay thế bước xác minh fingerprint/CA/nguồn certificate.

## 5. CertStore là gì, và vì sao nó không phải truststore?

`java.security.cert.CertStore` cung cấp một **repository để truy xuất certificates và Certificate Revocation Lists (CRLs)**. JCA dùng nó trong các công việc liên quan **certificate path**:

```text
Peer sends leaf certificate
          |
          v
CertPathBuilder cần tìm intermediate chứng chỉ còn thiếu
          |
          +------> CertStore (Collection/LDAP/nguồn hỗ trợ)
          |             | certificates & CRLs ứng viên
          |             v
          |       lọc bằng CertSelector/CRLSelector
          v
Construct candidate certification path
          |
          v
CertPathValidator + PKIXParameters
  - trusted anchors (từ truststore / TrustAnchor)
  - algorithm and certificate constraints
  - validity, policies, optional revocation sources
          |
          v
Accept/reject path theo chính sách
```

Một certificate nằm trong **CertStore** chỉ có nghĩa nó được **cung cấp để xem xét**, không phải nó là một trust anchor. Ngược lại, việc một certificate là **trust anchor** là quyết định thuộc **PKIX parameters / truststore / policy**. Hai tập dữ liệu này có thể chứa cùng một certificate nhưng **ý nghĩa được gán bởi API sử dụng** khác nhau.

| Loại | Kết quả API | Vai trò |
| --- | --- | --- |
| `CertStore.getCertificates(CertSelector)` | Danh sách certificate phù hợp tiêu chí | Ứng viên để xây dựng/kiểm tra chain |
| `CertStore.getCRLs(CRLSelector)` | Danh sách CRL phù hợp | Dữ liệu có thể dùng trong kiểm tra thu hồi |
| `PKIXParameters(trustStore)` | Các `TrustAnchor` từ `TrustedCertificateEntry` | Cơ sở tin cậy cho PKIX |
| `PKIXParameters.addCertStore(certStore)` | Thêm nguồn tìm certificate/CRL | **Không** thêm chứng chỉ trong CertStore thành trust anchors |
| `CertPathBuilder` | Xây đường dẫn ứng viên đến trust anchor | Không tự biết trust policy nếu không có parameters |
| `CertPathValidator` | Kiểm tra đường dẫn với trust anchors và policy | Không phải công cụ lưu private key |

### 5.1 Những loại CertStore

- **`"Collection"`**: dùng `CollectionCertStoreParameters` để cung cấp một collection chứa certificates và CRLs; loại tiêu chuẩn mà Java phải hỗ trợ. Hữu ích cho ví dụ chạy tại chỗ và khi ứng dụng có sẵn tập dữ liệu.
- **LDAP/nguồn ngoài**: có thể được hỗ trợ tùy provider; cho phép tìm certificates/CRLs trong directory hay repository. Không giả định mọi JVM có provider cho tất cả type.
- **Ứng dụng có thể kết hợp nhiều CertStore** trong `PKIXParameters` để cung cấp intermediate/CRL. Bên kiểm tra vẫn cần trust anchors và các chính sách xác minh.

`CertStore` là API **đọc/truy xuất**, không có một cặp `load(file,password)` / `store(file,password)` tương đương `KeyStore`; nó không quản lý private key. Nguồn dữ liệu bên dưới có thể thay đổi hoặc không đáng tin; **người xây/kiểm tra đường dẫn phải xác thực certificate chain và CRL** theo chính sách trước khi tin kết quả.

### 5.2 CRL, OCSP và certificate revocation

Một certificate có thể còn trong hạn nhưng đã bị thu hồi. **CRL** là danh sách certificate bị thu hồi do issuer ký; **OCSP** cung cấp một cơ chế khác để hỏi trạng thái. CertStore có thể **cung cấp CRL** cho quá trình PKIX nhưng không tự gọi mọi OCSP server hoặc tự bảo đảm việc xác minh thu hồi đã được bật.

Cần cấu hình/kiểm tra **PKIX revocation policy**, nguồn dữ liệu, thời hạn/cache, xử lý lỗi mạng và quy tắc fail-open/fail-closed theo yêu cầu. **Đừng nói “có CertStore là đã kiểm tra revocation”**. Với TLS, chính sách và hành vi mặc định có thể khác nhau theo provider/JDK/client; phải đo bằng môi trường thật.

## 6. Java 17+: tạo PKCS#12 KeyStore trong bộ nhớ và nạp lại

Chương trình sau chứng minh **`KeyStore` là API quản lý entry**, không nhất thiết phải bắt đầu từ một certificate hay file. Ta sinh **AES-256 SecretKey**, lưu thành `SecretKeyEntry` trong một PKCS#12 mới, serialize vào `ByteArrayOutputStream`, nạp lại và kiểm tra key có giống ban đầu không.

Lưu tệp **`KeyStoreBasicsDemo.java`**:

```java
import java.io.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;

public final class KeyStoreBasicsDemo {
    public static void main(String[] args) throws Exception {
        // Mật khẩu cố định chỉ để minh họa, KHÔNG dùng trong production.
        char[] password = "demo-changeit".toCharArray();
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, password); // Kho mới rỗng, file-backed type.

            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            SecretKey original = generator.generateKey();

            store.setEntry(
                    "app-aes",
                    new KeyStore.SecretKeyEntry(original),
                    new KeyStore.PasswordProtection(password));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            store.store(out, password);
            byte[] p12 = out.toByteArray();

            KeyStore restored = KeyStore.getInstance("PKCS12");
            restored.load(new ByteArrayInputStream(p12), password);

            Key recovered = restored.getKey("app-aes", password);
            System.out.println("Entry count: " + restored.size());
            System.out.println("Secret key entry: "
                    + restored.entryInstanceOf(
                            "app-aes", KeyStore.SecretKeyEntry.class));
            System.out.println("Is key entry: "
                    + restored.isKeyEntry("app-aes"));
            System.out.println("Is trusted cert entry: "
                    + restored.isCertificateEntry("app-aes"));
            System.out.println("AES key same: "
                    + MessageDigest.isEqual(
                            original.getEncoded(), recovered.getEncoded()));
            System.out.println("Encoded PKCS12 nonempty: "
                    + (p12.length > 0));
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
```

Biên dịch và chạy với Java 17+ và provider hỗ trợ PKCS#12 SecretKeyEntry/AES:

```bash
javac -encoding UTF-8 KeyStoreBasicsDemo.java
java KeyStoreBasicsDemo
```

Stdout ổn định:

```text
Entry count: 1
Secret key entry: true
Is key entry: true
Is trusted cert entry: false
AES key same: true
Encoded PKCS12 nonempty: true
```

**Phân tích:**

1. `KeyStore.getInstance("PKCS12")` chọn **format/provider**, chưa load. `load(null, password)` tạo kho rỗng cho file-backed store trong demo; với PKCS#11/token không được tự suy ra ý nghĩa tương tự.
2. `SecretKeyEntry` chứa AES key. `setEntry` có `PasswordProtection`; điều đó khác `setCertificateEntry` chỉ thêm certificate.
3. `store(out,password)` serialize kho thành PKCS#12 bytes. `load(in,password)` phục hồi kho; `getKey` dùng password để truy cập key.
4. `isKeyEntry` là `true`; `isCertificateEntry` là `false`, vì AES secret key **không phải X.509 trusted certificate**.
5. `MessageDigest.isEqual` kiểm tra byte key khớp khi demo cho phép export. Với HSM/non-extractable key, `getEncoded()` có thể trả null và phải test bằng **operation được cấp phép**, không ép xuất key.

**Bảo mật:** `"demo-changeit"` là mật khẩu minh họa, không dùng cho kho khóa thật. Chương trình giữ password dưới dạng `char[]` và xóa buffer cuối chương trình, nhưng key bytes/PKCS#12 bytes vẫn có bản sao trong heap; `Arrays.fill` không bảo đảm xóa mọi bản sao trong JVM/provider. Trong production, tránh lưu raw key/PKCS#12 bytes trong log, response, repository hoặc artifact không được bảo vệ.

## 7. Thực hành đầy đủ: identity keystore, truststore và CertStore

Phần này tạo **hai file PKCS#12 khác nhau** bằng công cụ `keytool` đi kèm JDK, sau đó chạy Java code kiểm tra đúng vai trò của chúng:

- `demo-identity.p12`: chứa **private key** và self-signed certificate chain dưới alias `server`.
- `demo-trust.p12`: chứa **certificate-only** dưới alias `lab-server`.
- `demo-server.crt`: bản PEM certificate được export để **chuyển certificate**, **không chứa private key**.
- Trong Java, dùng certificate từ truststore tạo **`CertStore("Collection")`** — chỉ là **nguồn candidate cert**, không phải trust anchor mới.

### 7.1 Sinh dữ liệu test với keytool

Chạy tuần tự các lệnh dưới đây trong **một thư mục test riêng** trên JDK có `keytool`. Mỗi lệnh cố ý để trên một dòng nên dùng được trong shell phổ biến; lệnh không cần network và không yêu cầu chứng chỉ CA thật.

```bash
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 365 -dname "CN=localhost, OU=Training, O=Demo, C=VN" -ext "SAN=dns:localhost" -keystore demo-identity.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt

keytool -exportcert -alias server -rfc -keystore demo-identity.p12 -storetype PKCS12 -storepass changeit -file demo-server.crt

keytool -importcert -alias lab-server -file demo-server.crt -keystore demo-trust.p12 -storetype PKCS12 -storepass changeit -noprompt
```

**Các lệnh chỉ dùng trong lab**: password `changeit` nằm ngay trong command line và certificate là **self-signed**. Trong production, không chấp thuận certificate bằng `-noprompt` khi chưa xác minh nguồn/fingerprint và chính sách trust; dùng secret management và quá trình cấp/luân chuyển chứng chỉ được phê duyệt. Certificate test `localhost` **không chứng minh** một CA hoặc endpoint sản xuất có danh tính hợp lệ.

Có thể kiểm tra metadata tại chỗ:

```bash
keytool -list -keystore demo-identity.p12 -storetype PKCS12 -storepass changeit
keytool -list -keystore demo-trust.p12 -storetype PKCS12 -storepass changeit
```

Kết quả `keytool -list` có thể khác phiên bản JDK, locale và thời gian cấp certificate; vì vậy không lấy một stdout cứng để làm contract. Điều quan trọng là identity store có alias **PrivateKeyEntry**, còn truststore có alias **trustedCertEntry**.

### 7.2 Java 17+: kiểm tra ba kiểu kho và cấu hình TLS

Lưu đoạn sau thành **`KeyStoreTrustCertStoreDemo.java`** trong **cùng thư mục** có hai file `.p12` vừa tạo:

```java
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;

public final class KeyStoreTrustCertStoreDemo {
    private static KeyStore load(String file, char[] password)
            throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in =
                Files.newInputStream(Path.of(file))) {
            ks.load(in, password);
        }
        return ks;
    }

    public static void main(String[] args) throws Exception {
        // Chỉ dành cho lab: password không được hardcode trong production.
        char[] pass = "changeit".toCharArray();
        try {
            KeyStore identity = load("demo-identity.p12", pass);
            KeyStore trust = load("demo-trust.p12", pass);

            System.out.println("Identity private-key entry: "
                    + identity.entryInstanceOf(
                            "server", KeyStore.PrivateKeyEntry.class));
            System.out.println("Identity certificate chain size: "
                    + identity.getCertificateChain("server").length);
            System.out.println("Truststore trusted certificate entry: "
                    + trust.isCertificateEntry("lab-server"));
            System.out.println("Truststore contains private key: "
                    + trust.isKeyEntry("lab-server"));

            X509Certificate certificate =
                    (X509Certificate)
                            trust.getCertificate("lab-server");
            CertStore candidates = CertStore.getInstance(
                    "Collection",
                    new CollectionCertStoreParameters(
                            List.of(certificate)));

            X509CertSelector selector = new X509CertSelector();
            selector.setSubject(
                    certificate.getSubjectX500Principal());
            System.out.println("CertStore matching candidates: "
                    + candidates.getCertificates(selector).size());

            PKIXParameters pkix = new PKIXParameters(trust);
            pkix.addCertStore(candidates);
            System.out.println("PKIX trust anchors: "
                    + pkix.getTrustAnchors().size());
            System.out.println("PKIX candidate stores: "
                    + pkix.getCertStores().size());

            KeyManagerFactory kmf =
                    KeyManagerFactory.getInstance(
                            KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(identity, pass);

            TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trust);

            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(
                    kmf.getKeyManagers(),
                    tmf.getTrustManagers(),
                    null);
            System.out.println("TLS context initialized: "
                    + ssl.getProtocol().equals("TLS"));
        } finally {
            Arrays.fill(pass, '\0');
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 KeyStoreTrustCertStoreDemo.java
java KeyStoreTrustCertStoreDemo
```

Với các file test sinh đúng theo bước 7.1 trên provider JDK phổ biến, stdout:

```text
Identity private-key entry: true
Identity certificate chain size: 1
Truststore trusted certificate entry: true
Truststore contains private key: false
CertStore matching candidates: 1
PKIX trust anchors: 1
PKIX candidate stores: 1
TLS context initialized: true
```

**Giải thích tám kết quả:**

1. `identity` có `PrivateKeyEntry` bởi `keytool -genkeypair` tạo private key + self-signed certificate chain.
2. Certificate chain có **1** certificate trong lab vì certificate tự ký, chưa có CA trung gian; trong production, chain có thể dài hơn.
3. `trust` chứa `TrustedCertificateEntry` vì ta nhập riêng **public certificate**, không nhập private key.
4. `trust.isKeyEntry("lab-server")` là `false`: truststore không có private key của server, và **không cần** private key để kiểm tra certificate peer.
5. `CertStore("Collection")` trả **một certificate ứng viên** phù hợp `X509CertSelector`. Nó không đưa ra kết luận về trust/CA/hostname.
6. `new PKIXParameters(trust)` lấy **một trust anchor** từ trusted certificate entry trong truststore. **Đây mới là bước đưa certificate vào tập trust anchor**, hoàn toàn khác `CertStore.getCertificates`.
7. `pkix.addCertStore(candidates)` thêm một nguồn candidate certificate/CRL vào tham số kiểm tra đường dẫn, **không** tự động bổ sung trust anchor khác.
8. `SSLContext.init(keyManagers,trustManagers,null)` thiết lập một TLS context. Dòng `true` chỉ chứng minh **khởi tạo thành công**, **không thực hiện network handshake** và **không chứng minh** một endpoint thực tế sẽ qua được certificate path, hostname/SAN, EKU, revocation hoặc policy.

**Trust trong lab là quyết định có chủ ý:** ta đã nhập chính certificate tự ký vào truststore, rồi lấy nó làm anchor qua `PKIXParameters`. Một certificate self-signed do attacker tự phát hành **không được tin** chỉ vì tự ký hoặc có CN `localhost`; nó chỉ là trust anchor nếu administrator/client **cho phép** thông qua quy trình xác minh nguồn, pinning hoặc trust chính sách phù hợp.

### 7.3 Khi nào nên tách KeyStore identity và truststore?

Nên tách **vật lý hoặc tối thiểu tách quyền truy cập và cấu hình** khi có thể:

- Identity store chứa **private key** và certificate chain, nên chỉ các process/role cần dùng private key được đọc.
- Truststore chứa trust anchors/certificates, có thể được phân phối rộng hơn nhưng **quyền sửa** vẫn cực kỳ nhạy cảm: thêm CA không được phép có thể phá vỡ xác thực đối tác.
- Rotate certificate/private key không nhất thiết là rotate trust anchors; kế hoạch lifecycle và chủ sở hữu khác nhau.
- Tách các tập anchor cho **prod/staging/dev**, các tenant/partner khác nhau hoặc các trust domain khác nhau để giảm blast radius.
- Với mTLS, một app có thể cần **cả hai store**: một để trình bày danh tính của chính nó, một để xác minh client/server bên kia.

## 8. JSSE mặc định: cacerts, jssecacerts và các system properties

Trong nhiều runtime JDK, JSSE có thể tạo default `SSLContext`/`TrustManagerFactory` dựa trên cấu hình hệ thống. Đối với **SunJSSE reference implementation** (và cấu hình phù hợp), cần hiểu những tên thường gặp:

| Thuộc tính/file | Vai trò |
| --- | --- |
| `javax.net.ssl.keyStore` | Đường dẫn identity keystore cho default KeyManager theo cấu hình JSSE |
| `javax.net.ssl.keyStoreType` | Type của identity keystore (ví dụ `PKCS12`) |
| `javax.net.ssl.keyStorePassword` | Mật khẩu được default JSSE dùng theo contract |
| `javax.net.ssl.trustStore` | Đường dẫn truststore khi cấu hình tường minh |
| `javax.net.ssl.trustStoreType` | Type truststore |
| `javax.net.ssl.trustStorePassword` | Password dùng khi load/kiểm tra tính toàn vẹn store theo implementation |
| `java-home/lib/security/jssecacerts` | File truststore JSSE-specific được tìm trước `cacerts` trong logic default của SunJSSE khi không chỉ định `trustStore` |
| `java-home/lib/security/cacerts` | Tập root CA đi kèm JDK được tìm sau `jssecacerts` nếu có; nội dung có thể khác theo nhà phát hành/bản phân phối JDK |

**Không mặc định có “một truststore toàn cục áp dụng cho mọi library”.** HTTP client, framework, custom `SSLContext`, provider hoặc container có thể dùng trust source khác, ignore/override properties. Khi dùng `SSLContext.init` với `TrustManager[]` riêng, logic trust của context đó đi theo manager do ứng dụng truyền vào.

Trong SunJSSE, việc **cấu hình trustStore trỏ đến file không tồn tại** có thể dẫn đến truststore hiệu dụng **rỗng** thay vì âm thầm fallback sang `cacerts`; không sửa bằng cách tắt trust checks. Khi TLS fail, cần kiểm tra đúng path, type, password, runtime, trust anchors và certificate chain.

Một ví dụ **minh họa cấu hình** (mật khẩu phải lấy qua cơ chế secret management an toàn, không đặt hardcode như dưới đây):

```text
-Djavax.net.ssl.trustStore=/path/to/approved-trust.p12
-Djavax.net.ssl.trustStoreType=PKCS12

-Djavax.net.ssl.keyStore=/path/to/identity.p12
-Djavax.net.ssl.keyStoreType=PKCS12
```

Đây chỉ là danh sách **system properties** tùy ngữ cảnh khởi động, không phải các dòng lệnh tự chạy được. Cần password và key selection theo contract ứng dụng; đừng đưa password vào command-line production nếu có giải pháp secret injection tốt hơn.

### 8.1 Không sửa cacerts của cả JDK chỉ để chữa một ứng dụng

Thêm một CA vào `cacerts` có thể làm **nhiều ứng dụng** cùng tin nó, vượt khỏi phạm vi dịch vụ ban đầu. Một nâng cấp JDK/container image cũng có thể ghi đè thay đổi. Khi một hệ thống chỉ cần trust CA nội bộ của đối tác, thường nên cấu hình **truststore riêng cho app**, có versioning, owner, nguồn certificate kiểm chứng và kế hoạch rotation.

Không dùng các thủ thuật nguy hiểm như `trustAllCerts`, `HostnameVerifier` trả `true` vô điều kiện, tắt validation hoặc bỏ qua `SSLHandshakeException`.

## 9. Chuỗi chứng chỉ, trust anchor và certificate path

**Certificate chain** trong identity keystore và **trust anchors** của truststore là hai tập dữ liệu có quan hệ nhưng không giống nhau.

Một chain thường là:

```text
Leaf / end-entity certificate  (CN/SAN của dịch vụ)
        |
  issued by Intermediate CA
        |
  issued by Root CA
        |
Trusted anchor từ truststore / PKIX trust policy
```

- **PrivateKeyEntry** liên kết private key của leaf với certificate chain cần trình bày cho peer. Khi triển khai TLS, server thường gửi leaf và intermediate phù hợp; không nhất thiết gửi root CA.
- **Truststore** xác định nơi kết thúc chuỗi được chấp thuận, ví dụ root CA được operator tin.
- **CertStore** có thể chứa intermediate certificates và CRLs để builder/validator tìm thêm dữ liệu khi peer không cung cấp đủ chain. **CertStore không tự chọn root CA đáng tin**.
- **CertPathBuilder/Validator** dùng trusted anchors, các constraint, validity, chữ ký, policy và dữ liệu revocation theo cấu hình để quyết định có hợp lệ hay không.
- **Hostname/SAN verification** cho TLS vẫn phải đúng: một chain hợp lệ đến CA tin cậy chưa đủ nếu certificate dành cho tên host khác.
- Một certificate đã hết hạn, không hợp lệ theo EKU/key usage hoặc dùng thuật toán bị policy cấm không trở thành tốt chỉ vì có mặt trong một store.

### 9.1 PKIXParameters và TrustAnchor

`PKIXParameters(KeyStore)` đọc **trusted certificate entries** và chuyển thành trust anchors theo contract API. Nếu kho không có certificate nào thích hợp làm anchor, constructor có thể thất bại vì trust-anchor set rỗng. Điều này khác với `CertStore` chứa vô số certificate ứng viên nhưng không chỉ định được điểm bắt đầu tin cậy.

```java
// Đoạn minh họa trong phương thức đã có trustStore/candidates phù hợp.
// Không phải chương trình độc lập.
PKIXParameters parameters = new PKIXParameters(trustStore);
parameters.addCertStore(candidates);
Set<TrustAnchor> anchors = parameters.getTrustAnchors();
```

`parameters.getTrustAnchors()` và `parameters.getCertStores()` là **hai danh sách khác nhau**; thêm một `CertStore` không làm số anchors tăng.

Nếu xây certificate path phức tạp, `PKIXBuilderParameters` có mục tiêu certificate selector và trust anchors, còn `CertPathBuilder` sử dụng parameters để tìm path; `CertPathValidator` dùng path đã có để đánh giá. Không coi `PKIXParameters` khởi tạo thành công là certificate path đã được xác minh — bước xác minh thực sự phải chạy.

## 10. Chọn công cụ đúng theo tình huống

| Tình huống | Nên dùng | Không nên nhầm với |
| --- | --- | --- |
| Web server cần giữ private key để trình bày TLS certificate | Identity `KeyStore` + `KeyManagerFactory` hoặc key handle của HSM | Chỉ import public certificate vào truststore |
| Client REST cần tin CA nội bộ phục vụ `api.company` | Truststore riêng + `TrustManagerFactory` + endpoint/hostname verification | Sửa `cacerts` toàn JDK hoặc bỏ hostname check |
| mTLS: client cũng cần certificate riêng | **Client identity KeyStore** và **server truststore**; server cũng có identity và trust | Nghĩ chỉ server mới cần keystore |
| PKIX thiếu intermediate certificate khi build chain | `CertStore`/AIA/nguồn tra cứu phù hợp theo policy | Thêm intermediate thành root trust anchor tùy tiện |
| PKIX kiểm tra thu hồi | CRL/OCSP, revocation policy, optional `CertStore` chứa CRLs | Tin rằng `CertStore` tự kiểm tra thu hồi |
| Lưu AES secret key cho dữ liệu cục bộ | `KeyStore.SecretKeyEntry` nếu type/provider hỗ trợ, hoặc KMS/HSM | `TrustedCertificateEntry` |
| Xác minh chữ ký số từ certificate đối tác | Public key/cert chain + trust policy/Pkix validation khi cần | Chỉ lấy public key từ certificate không rõ nguồn rồi kết luận trusted |
| Luân chuyển private key server | Rotate identity keypair/certificate và cập nhật deployment | Tự động thay toàn bộ trusted CA của client |
| Xóa một CA không còn tin | Quản trị **trust anchors** trong truststore/policy | Xóa certificate ứng viên khỏi CertStore rồi nghĩ CA đã bị loại khỏi trust anchors |

**Mô hình tối thiểu:** private key của **bên mình** → identity keystore. Certificate của **bên được phép tin** → truststore. Certificate/CRL **chỉ để tra cứu** → CertStore. Kết quả có chấp thuận kết nối hay không còn phụ thuộc **trust manager/PKIX/endpoint policy**, không phụ thuộc một file có tên `truststore.p12` hay `certs.p12`.

## 11. Các lỗi thường gặp và cách chẩn đoán

| Dấu hiệu | Nguyên nhân có thể | Hướng kiểm tra |
| --- | --- | --- |
| `java.io.IOException` khi `KeyStore.load` | Path không tồn tại, bytes hỏng, password/type/provider không phù hợp | Kiểm tra file, type, quyền đọc, nguồn file, password; giữ cause trong log an toàn |
| `UnrecoverableKeyException` | Không thể khôi phục key entry bằng password/protection đang dùng | Phân biệt **key password** với store password; kiểm tra alias/provider |
| `KeyStoreException` | Store chưa load, alias/entry/type không hợp lệ hoặc thao tác không hỗ trợ | Gọi load trước, kiểm tra `entryInstanceOf`, provider capability |
| `NoSuchAlgorithmException` | Provider không hỗ trợ thuật toán integrity/keystore/key protection được yêu cầu | Kiểm tra JDK/provider/security policies |
| `CertificateException` | Lỗi parse/kiểm tra certificate khi load | Kiểm tra format, certificate chain và provider |
| `NoSuchProviderException` | Chỉ định provider không tồn tại qua overload/provider name | Kiểm tra registration và runtime |
| `SSLHandshakeException: PKIX path building failed` | Không có path hợp lệ tới anchor, thiếu intermediate, CA chưa trusted, constraint khác | Kiểm tra **đúng truststore hiệu dụng**, chain, issuer, ngày, usages, policy; không trust-all |
| TLS báo hostname mismatch | Certificate có chain tin cậy nhưng SAN không khớp endpoint | Kiểm tra tên host, SAN và endpoint identification |
| Server `no suitable certificate` hoặc handshake không tìm thấy key | Identity keystore thiếu PrivateKeyEntry/chain, alias sai, algorithm không phù hợp, key password không đúng | Kiểm tra identity store, `KeyManagerFactory`, certificate/key usage |
| `CertStoreException` | Truy vấn CertStore/LDAP/backend thất bại | Kiểm tra selector, kết nối, cache, quyền và xử lý timeout |
| `InvalidAlgorithmParameterException` khi tạo PKIX params | Không có trust anchors hợp lệ hoặc tham số PKIX sai | Đọc trusted entries, set TrustAnchor; không chuyển mọi candidate thành trusted |
| Private key `getEncoded() == null` | Key non-extractable hoặc provider không hỗ trợ export | Dùng operation của HSM/KMS, không bypass boundary an toàn |

Có thể dùng **debug flags của JDK** khi điều tra trong môi trường kiểm soát, ví dụ `-Djavax.net.debug=ssl,handshake` hoặc `-Djava.security.debug=certpath`. Log TLS/certpath có thể rất lớn và có thông tin cấu hình nhạy cảm; không bật vô thời hạn trong production, không paste nguyên log chứa secret nội bộ lên nơi công khai.

**Đọc đúng lỗi:** “`PKIX path building failed`” không nhất thiết là “thiếu private key”. Nếu client không tìm được CA của server, đó thường là luồng **truststore/trust manager**; nếu server không chọn được certificate của chính mình, đó thường là luồng **identity keystore/key manager**.

## 12. Sai lầm thiết kế cần tránh

1. **“Truststore là class `TrustStore`.”** Không: với Java/JSSE thường là một `KeyStore` được dùng cho chức năng trust.
2. **“Có certificate trong một file keystore nghĩa là đã trusted.”** Không: `PrivateKeyEntry` cũng có chain, nhưng `isCertificateEntry(alias)` không đúng cho key entry; trust decision phụ thuộc vai trò và policy.
3. **“CertStore chứa CA thì tự thành trust anchor.”** Không: CertStore chứa **candidate certificates/CRLs**; trust anchor được cấu hình riêng.
4. **“Khi SSLHandshakeException, thêm mọi certificate tải về vào cacerts.”** Rất nguy hiểm: chấp thuận nguồn không xác thực và làm tăng phạm vi tin cậy.
5. **“KeyStore luôn là một file `.jks`.”** Không: `PKCS12`, JKS, PKCS#11, OS-specific providers và backend hardware đều có thể xuất hiện.
6. **“Đổi alias từ `server` thành `trusted-ca` sẽ biến cert thành root CA.”** Không: alias chỉ là nhãn; ý nghĩa từ loại entry và trust policy.
7. **“CertStore kiểm tra certificate revocation tự động.”** Không: cần PKIX revocation configuration/OCSP/CRL handling đúng.
8. **“Đã tin CA thì không cần kiểm tra hostname.”** Sai: endpoint identity và chain trust là hai kiểm tra liên quan nhưng độc lập.
9. **“Password PKCS#12 là bí mật duy nhất cần quản lý.”** Sai: private key, quyền filesystem, backup, deployment, KMS/HSM, rotation và integrity đều cần bảo vệ.
10. **“Chỉ cần gọi `SSLContext.init` thành công thì TLS đã bảo mật.”** Sai: context initialized ≠ handshake/trust/hostname validation verified.
11. **“Trong mTLS, client không cần private key.”** Sai: khi server yêu cầu client certificate authentication, client cần identity credential riêng.
12. **“Có thể xuất mọi private key khỏi HSM qua `getEncoded()`.”** Không: non-exportability là tính năng bảo mật và phải được tôn trọng.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Một API server có `server.p12` chứa private key và leaf+intermediate chain, còn client có `ca-trust.p12` chứa root CA. Bên nào là identity keystore, bên nào là truststore?  
**Trả lời:** `server.p12` là **identity keystore** của server; `ca-trust.p12` là **truststore** của client. Cả hai đều có thể được đọc bằng class `KeyStore`.

**Câu 2.** Vì sao `identity.getCertificate("server") != null` không có nghĩa `server` là trusted certificate entry?  
**Trả lời:** PrivateKeyEntry cũng chứa certificate chain và `getCertificate` có thể trả leaf certificate của nó. Kiểm tra bằng `isCertificateEntry` hoặc `entryInstanceOf(TrustedCertificateEntry.class)`.

**Câu 3.** Có 100 intermediate certificates trong CertStore; có nghĩa JVM tin 100 CA đó không?  
**Trả lời:** **Không.** Chúng là ứng viên để build/validate path. Điểm tin cậy phải nằm trong **TrustAnchor/truststore/PKIX policy** được xác thực.

**Câu 4.** Nếu mTLS client cần trình bày certificate, nó chỉ cần truststore không?  
**Trả lời:** Không. Nó cần **identity credential** chứa private key + chain và key manager; còn truststore giúp nó xác minh certificate server.

**Câu 5.** `KeyStore.getInstance("PKCS12")` đã tự đọc `identity.p12` trên đĩa chưa?  
**Trả lời:** Chưa. Thông thường phải `load(inputStream,password)`; một số overload mới hỗ trợ chọn file/probe type và load ngay, nhưng `getInstance(String type)` chỉ lấy instance chưa load.

**Câu 6.** Nhập một self-signed certificate vào truststore thử nghiệm có an toàn để xác thực mọi server production không?  
**Trả lời:** Không. Đó là một quyết định tin cậy cần nguồn, fingerprint và phạm vi xác nhận; certificate demo không đại diện CA thật và không có quy trình cấp phát/thu hồi production.

**Câu 7.** Muốn tìm CRL để phục vụ PKIX revocation, dùng class nào?  
**Trả lời:** `CertStore` có thể cung cấp CRLs qua `getCRLs`, nhưng vẫn cần cấu hình PKIX revocation checker/policy và xác minh CRL đúng nguồn.

**Câu 8.** Thay đổi `javax.net.ssl.trustStore` có ảnh hưởng mọi `SSLContext` trong app không?  
**Trả lời:** Không nhất thiết. Framework hoặc context được cấu hình với trust managers riêng có thể không dùng default system properties.

**Câu 9.** Nếu một certificate đã qua chain validation nhưng SAN không khớp URL, kết nối HTTPS có nên được chấp nhận?  
**Trả lời:** **Không.** HTTPS cần kiểm tra danh tính host/endpoint phù hợp với certificate; không bỏ qua hostname verification.

**Câu 10.** Nếu private key trong HSM không xuất được, phải lưu vào PKCS#12 trước khi dùng với TLS?  
**Trả lời:** Không. Dùng KeyStore/provider/key-manager tích hợp HSM theo khả năng được cấp phép; không cố export private key.

## 14. Checklist vận hành

**Quản trị identity keys:**

- Đặt private key ở đúng boundary (keystore có kiểm soát, KMS/HSM khi phù hợp).
- Xác định alias, key type, certificate chain đầy đủ, key usage, EKU, SAN và ngày hết hạn.
- Bảo vệ key access password/role/permissions; có quy trình renew, key rotation, backup/restore và thu hồi.
- Chỉ load private key ở những process thực sự cần; không log/export/commit material.

**Quản trị trust anchors:**

- Chỉ nhập CA/peer certificate khi đã xác thực nguồn và mục đích tin cậy.
- Tách trust domain theo app/tenant/environment; tránh sửa `cacerts` toàn JVM không kiểm soát.
- Theo dõi expiry, root/intermediate rotation, algorithm deprecation, revocation policy.
- Bật endpoint identification/hostname validation cho TLS client đúng API.
- Không tắt trust checks để “giải quyết nhanh” lỗi PKIX.

**Quản trị CertStore và certificate path:**

- Phân biệt **nguồn ứng viên** với **trust anchors**.
- Giới hạn nguồn tra cứu, timeout, cache, kích thước dữ liệu, xử lý backend lỗi.
- Cấu hình đúng certpath builder/validator, EKU/policy, revocation check và trust anchor.
- Test tình huống missing intermediate, wrong CA, expired, hostname mismatch, revoked và untrusted cert.

**Triển khai Java:**

- Ghi rõ `PKCS12` hay type phù hợp thay vì phụ thuộc default không cần thiết.
- Dùng `KeyManagerFactory` với identity keystore, `TrustManagerFactory` với truststore, `CertStore` khi cần nguồn certificate/CRL ứng viên.
- Kiểm thử bằng đúng JDK/provider/container image và hệ thống TLS client/server thực.
- Kết quả `SSLContext.init` thành công chưa phải là test handshake/hostname/trust decision.

## Tài liệu chính thức

- [Java SE 21 — `KeyStore`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyStore.html): các loại Entry, alias, load/store, provider, các phương thức quản lý khóa/chứng chỉ.
- [Java SE 21 — `CertStore`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/CertStore.html): nguồn certificate/CRL để tra cứu, phân biệt với KeyStore.
- [Java SE 21 — `PKIXParameters`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/PKIXParameters.html): trust anchors từ KeyStore và các CertStore ứng viên.
- [Java SE 21 — `TrustAnchor`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/TrustAnchor.html): điểm tin cậy trong PKIX.
- [Java SE 21 — `KeyManagerFactory`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/KeyManagerFactory.html) và [`TrustManagerFactory`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html): identity và trust managers của TLS.
- [Java SE 21 — JSSE Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html): SSLContext, key/truststore defaults, cacerts/jssecacerts, cấu hình và mô hình TLS.
- [Java SE 21 — Java PKI Programmer's Guide](https://docs.oracle.com/en/java/javase/21/security/java-pki-programmers-guide.html): xây/kiểm tra certification path, CertStore, CRL/revocation.
- [Java `keytool` documentation](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html): tạo identity keypair, export/import certificate, quản trị PKCS#12/JKS.

**Kết luận:** `KeyStore` là **API/kho quản lý khóa và chứng chỉ**; *identity keystore* dùng private key + certificate chain **của chính ứng dụng**; *truststore* cũng thường là `KeyStore` nhưng đại diện **các điểm tin cậy để kiểm tra đối tác**; `CertStore` lại là **nguồn tra cứu certificates/CRLs ứng viên**, không tự định nghĩa trust. Phân biệt đúng ba vai trò giúp thiết kế TLS, PKIX, mTLS và quản lý khóa an toàn, dễ kiểm thử và dễ vận hành.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-store-002">12.2 PrivateKeyEntry, SecretKeyEntry, TrustedCertificateEntry và alias</a>

<details>
<summary>Xem chi tiết</summary>

# 12.2 PrivateKeyEntry, SecretKeyEntry, TrustedCertificateEntry và alias

**Java KeyStore** (`java.security.KeyStore`) là một API quản lý các **entry mật mã có tên** trong một kho khóa: khóa riêng cùng chuỗi chứng chỉ, khóa bí mật đối xứng, hoặc chứng chỉ công khai được lưu riêng để làm điểm tin cậy. Mỗi entry được tìm qua **alias**. Việc hiểu sai loại entry thường gây lỗi TLS, không đọc được private key, ghi đè alias, hoặc nhầm một certificate đơn lẻ với một danh tính có private key.

Bài học này phân biệt rõ **`KeyStore.PrivateKeyEntry`**, **`KeyStore.SecretKeyEntry`**, **`KeyStore.TrustedCertificateEntry`** và các phương thức quản lý alias. Ví dụ Java 17+ tạo đủ ba loại trong một **PKCS#12** (`.p12`), lưu ra file, mở lại và kiểm loại entry một cách thực tế.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt nội dung và mục đích của ba loại `KeyStore.Entry`: **khóa riêng kèm certificate chain**, **khóa đối xứng**, **chứng chỉ tin cậy độc lập**.
2. Giải thích **alias** là gì, phạm vi duy nhất và các thao tác `aliases`, `containsAlias`, `isKeyEntry`, `isCertificateEntry`, `entryInstanceOf`.
3. Dùng đúng `setEntry` / `getEntry` với `KeyStore.PasswordProtection`; hiểu khác nhau giữa **store password** và **entry password**.
4. Phân biệt **keystore danh tính** với **truststore của các chứng chỉ CA/peer được tin**, và hiểu vì sao lưu một certificate không tự xác minh được nguồn gốc của nó.
5. Triển khai và chạy chương trình Java 17+ dùng **PKCS12**, `keytool`, `KeyGenerator` và `KeyStore`; kiểm tra tất cả các loại entry sau khi lưu/nạp lại.
6. Tránh lỗi ghi đè alias, sai certificate chain, dùng JKS khi cần secret key, lộ password và cập nhật file không an toàn.

## 2. Mô hình tổng thể: KeyStore chứa những gì?

Một KeyStore có thể được hiểu như bản đồ **alias → entry** (thực tế mỗi provider/type có cách lưu trữ riêng):

~~~text
KeyStore: demo.p12  (type = PKCS12)
│
├── alias "identity-demo"
│    └── PrivateKeyEntry
│         ├── PrivateKey         [BÍ MẬT]
│         └── Certificate[]      [công khai, chain cho key này]
│
├── alias "api-aes"
│    └── SecretKeyEntry
│         └── SecretKey (AES)    [BÍ MẬT]
│
└── alias "trust-demo"
     └── TrustedCertificateEntry
          └── Certificate       [chỉ chứng chỉ công khai]
~~~

Một entry có **một loại cụ thể**, không phải một “object mơ hồ vừa chứa cert vừa chứa AES key”. Một `PrivateKeyEntry` có thể có certificate chain, nhưng nó **khác** `TrustedCertificateEntry`. Chứng chỉ X.509 mang **public key và thông tin định danh do issuer ký**, không chứa **private key**.

**Bảng đối chiếu:**

| Thuộc tính | `PrivateKeyEntry` | `SecretKeyEntry` | `TrustedCertificateEntry` |
| --- | --- | --- | --- |
| Chứa | Private key và certificate chain tương ứng | Secret key, ví dụ AES/HMAC key | Một certificate công khai |
| Có secret cần bảo vệ | **Có: private key** | **Có: secret key** | **Không có private key** |
| Có certificate chain | **Có**, bắt buộc khi tạo entry | Không | Chỉ chứa certificate đơn lẻ, không phải private-key chain |
| Tình huống chính | Server TLS identity, client TLS identity, ký số | AES-GCM data key, HMAC key, key-encryption key | CA certificate hoặc peer certificate đã được xác thực và chủ đích tin cậy |
| API get chuyên biệt | `getPrivateKey()`, `getCertificateChain()` | `getSecretKey()` | `getTrustedCertificate()` |
| `isKeyEntry(alias)` | `true` | `true` | `false` |
| `isCertificateEntry(alias)` | `false` | `false` | `true` |
| Entry protection | Thường `PasswordProtection` hoặc cơ chế provider | Thường `PasswordProtection`/provider | Thường `null` cho `getEntry/setEntry` kiểu certificate |
| Có thể dùng như identity có private key | Có, nếu certificate chain/identity/key đã được kiểm đúng | Không | Không |

**Ý nghĩa quan trọng:** `isKeyEntry` **không phân biệt private key và secret key**: cả hai đều là “key entry”. `isCertificateEntry` chỉ nhận loại certificate **đứng độc lập**; một `PrivateKeyEntry` có chứng chỉ đi kèm vẫn có thể trả `isCertificateEntry=false`.

## 3. Ba loại Entry trong Java

### 3.1. `KeyStore.PrivateKeyEntry`

`PrivateKeyEntry` bao gồm:

- Một `java.security.PrivateKey`: vật liệu khóa riêng để ký hoặc giải mã theo thuật toán phù hợp; không bao giờ in log hoặc xuất ra nơi công khai.
- Một mảng `java.security.cert.Certificate[]` thể hiện certificate chain. Phần tử **đầu tiên** là certificate end-entity tương ứng với private key, tiếp theo thường là các certificate cấp trên theo đường đi phát hành. Số lượng/tính hợp lệ chain phụ thuộc cấu hình.
- Có thể có các thuộc tính entry (`Attribute`) tùy provider/định dạng, nhưng không nên dùng để thay thế metadata ứng dụng đã quy định.

Ví dụ khái niệm:

~~~java
KeyStore.PrivateKeyEntry identity =
        (KeyStore.PrivateKeyEntry) store.getEntry(
                "identity-demo",
                new KeyStore.PasswordProtection(keyPassword));

PrivateKey signingKey = identity.getPrivateKey();
Certificate[] chain = identity.getCertificateChain();
~~~

Đoạn này **chỉ là trích đoạn**, cần khởi tạo `store`, biến `keyPassword` và xử lý exception; chương trình hoàn chỉnh ở phần 9.

Đặc điểm:

1. Nếu chỉ lưu `Certificate` mà **không có private key**, không thể dùng alias đó làm identity để ký hoặc chứng minh sở hữu khóa trong TLS.
2. Certificate chain phải phản ánh đúng private/public key của entity và cơ chế phát hành. Không trộn chain của domain/khóa khác.
3. **Có chain không đồng nghĩa certificate được tin**. Phía đối tác vẫn phải xác minh chữ ký, đường dẫn tin cậy, hạn dùng, tên host/SAN, key usage và chính sách liên quan khi sử dụng.
4. Nếu bạn dùng `KeyStore.setKeyEntry(alias, privateKey, password, chain)`, tham số `chain` là cần thiết cho private key. Với `setEntry`, tạo `new PrivateKeyEntry(privateKey, chain)`.
5. Private key có thể là **non-extractable** nếu nằm trong HSM/PKCS#11 hoặc provider đặc biệt. Đừng giả định `privateKey.getEncoded()` luôn trả byte hay có thể export.

**Thường dùng ở đâu?** TLS server identity thường dùng một `PrivateKeyEntry` để lấy private key và certificate chain; mutual TLS (mTLS) phía client cũng có thể dùng entry kiểu này. Private key phải tương ứng với public key trong certificate leaf; alias chỉ giúp tìm nó, không chứng minh sự tương ứng nếu dữ liệu được ghép sai.

### 3.2. `KeyStore.SecretKeyEntry`

`SecretKeyEntry` chứa `javax.crypto.SecretKey`, thường là khóa **đối xứng** hoặc khóa HMAC. Ví dụ:

~~~java
KeyGenerator generator = KeyGenerator.getInstance("AES");
generator.init(128);
SecretKey aesKey = generator.generateKey();

store.setEntry(
        "api-aes",
        new KeyStore.SecretKeyEntry(aesKey),
        new KeyStore.PasswordProtection(password));
~~~

Đây cũng là **trích đoạn minh họa**, chưa phải class độc lập. Một `SecretKeyEntry` **không có X.509 certificate chain**, vì AES key là secret dùng cho thuật toán đối xứng, không phải key pair public/private để phát hành certificate.

Ứng dụng:

- Lưu **AES key** dùng encrypt/decrypt hoặc key-encryption key (KEK) nếu provider/storage policy cho phép.
- Lưu HMAC key dùng `Mac`, tùy provider và định dạng.
- Tránh nhầm `SecretKeyEntry` với khóa riêng RSA/EC hoặc chứng chỉ công khai.

Lưu ý:

- `JKS` theo provider truyền thống không hỗ trợ mọi kiểu secret key entry; nếu cần khóa đối xứng, dùng **PKCS12 hỗ trợ tương ứng**, HSM/PKCS#11 hoặc giải pháp quản lý khóa được kiểm chứng thay vì giả định một loại KeyStore nào cũng hỗ trợ.
- `JCEKS` từng được dùng để cất secret key trong các hệ thống cũ, nhưng không nên mặc định là lựa chọn mới vì các khác biệt lịch sử về thuật toán bảo vệ và interoperability. Ưu tiên loại/provider hiện đại phù hợp chính sách.
- Không lưu plaintext secret trong alias hay property file. Alias **không phải** mật khẩu; nó chỉ là chuỗi định danh entry.
- Với non-extractable key trong KMS/HSM, bản thân API/provider và vòng đời key có thể khác ví dụ file-based PKCS12.

### 3.3. `KeyStore.TrustedCertificateEntry`

`TrustedCertificateEntry` chứa một certificate (thường X.509) **không có private key**. Ví dụ:

~~~java
Certificate verifiedCaCertificate = /* certificate đã được kiểm bằng kênh tin cậy */;
store.setEntry(
        "corp-ca",
        new KeyStore.TrustedCertificateEntry(verifiedCaCertificate),
        null);
~~~

Đoạn này giải thích API, **không phải code Java hoàn chỉnh**: biến `verifiedCaCertificate` đại diện cho certificate đã có từ nguồn đáng tin, không được lấy tùy ý từ Internet rồi tự đánh dấu “trusted”.

Ứng dụng thực tế:

- Lưu **trust anchor** của một CA nội bộ hoặc CA được quản lý theo chính sách.
- Lưu peer certificate đã được xác minh ngoài băng để thực hiện chính sách pinning/trust cụ thể.
- Làm nguồn certificate cho `TrustManagerFactory` khi ứng dụng quyết định sử dụng **KeyStore này làm truststore**.

**Cảnh báo lớn:** Gọi `setCertificateEntry` hoặc tạo `TrustedCertificateEntry` **không phải hành động kiểm chứng certificate**. Nó biểu thị bạn chủ động đặt certificate trong một tập tin cậy khi cấu hình trust manager đọc store đó. Nếu certificate lấy từ đối thủ hoặc nhầm nguồn, việc “trust” có thể tạo ra lỗ hổng MITM. Với TLS, trust manager vẫn cần thực hiện các bước validation liên quan; chỉ có cert trên disk không bảo đảm endpoint hợp lệ.

**Phân biệt certificate trong `PrivateKeyEntry` và certificate đứng độc lập:**

~~~text
"identity-demo" --> PrivateKeyEntry [private key + leaf + chain]
"trust-demo"    --> TrustedCertificateEntry [một certificate, không có private key]
~~~

Một certificate có thể xuất hiện trong chain của `PrivateKeyEntry` và trong một trusted entry độc lập. **Không suy ra hai alias là cùng một loại entry** hoặc có cùng quyền sử dụng. Chương trình demo chủ động lưu bản sao cert self-signed dưới alias trusted để học API; **không được dùng chứng chỉ self-signed demo đó làm CA tin cậy trong sản phẩm**.

## 4. Alias: định danh logic, không phải khóa/mật khẩu/quyền truy cập

**Alias** là chuỗi ký tự để truy vấn entry trong **một KeyStore cụ thể**. Các API thiết yếu:

| API | Hành vi |
| --- | --- |
| `store.aliases()` | Liệt kê các alias hiện có; **không có cam kết thứ tự** |
| `store.size()` | Số lượng entry trong store |
| `store.containsAlias(name)` | Kiểm alias có tồn tại không |
| `store.isKeyEntry(name)` | Có phải key entry (private **hoặc** secret) không |
| `store.isCertificateEntry(name)` | Có phải certificate entry độc lập không |
| `store.entryInstanceOf(name, EntryType.class)` | Kiểm loại entry cụ thể, ví dụ `PrivateKeyEntry.class` |
| `store.getEntry(name, protection)` | Lấy entry theo alias và tham số bảo vệ tương ứng |
| `store.setEntry(name, entry, protection)` | Tạo hoặc **thay entry** gắn với alias |
| `store.deleteEntry(name)` | Xóa entry được chỉ định |
| `store.getCertificate(name)` | Trả certificate cho trusted entry hoặc certificate đầu của private-key entry khi có |
| `store.getCertificateChain(name)` | Trả chain của private-key entry nếu có; loại khác thường không có |
| `store.getKey(name, password)` | Có thể trả key cho key entry; dùng `getEntry` khi cần kiểm loại chính xác |
| `store.getCertificateAlias(cert)` | Tìm một alias cho certificate tương ứng, **không** bảo đảm là alias mục đích mà bạn mong đợi |

**Bốn quy tắc quan trọng về alias:**

1. **Duy nhất trong một store**, không phải duy nhất toàn hệ thống. Hai file khác nhau có thể có cùng alias với nội dung hoàn toàn khác.
2. **Không có namespace riêng theo entry type**: một alias không nên cùng lúc đại diện cả private key và trusted cert. `setEntry` với alias đang tồn tại có thể **thay thế** dữ liệu cũ; phải kiểm và backup trước.
3. **Case-sensitivity và normalization phụ thuộc provider/type.** Một số triển khai PKCS12 có thể canonicalize alias/không phân biệt hoa thường; **không dựa vào `"AppKey"` và `"appkey"` để tạo hai entry khác nhau**. Chọn quy ước alias ASCII thường, nhất quán và test trên provider mục tiêu.
4. **Alias không phải bí mật và không phải Access Control List**: biết alias không cấp quyền đọc key nếu không có quyền truy cập keystore/provider và bảo vệ entry; ngược lại đặt alias khó đoán cũng không làm store an toàn hơn.

Ví dụ sơ đồ đặt tên hữu ích cho vận hành:

~~~text
server-tls-prod-2026-10
client-mtls-backup-v2
payments-data-aes-v3
corp-root-ca-v1
~~~

Đây là **ví dụ cấu trúc tên**, không phải alias tự được Java hiểu đặc biệt. Có thể bổ sung key ID/version vào metadata ứng dụng; **không phân tích alias như bằng chứng quyền sở hữu hoặc độ tin cậy**.

### 4.1. Alias có phải tên Common Name/SAN trong certificate không?

**Không.** Alias là nhãn cục bộ của KeyStore; `CN` và `subjectAltName` (SAN) là các trường trong X.509 certificate. TLS hostname validation căn cứ tên được phép trong certificate/SAN và chain trust theo chính sách, **không** dựa vào tên alias bạn đặt.

Một TLS server có thể dùng alias `"identity-demo"` trong store chứa certificate `CN=Learning Demo`; alias đó không làm certificate hợp lệ cho `api.example.com`. Java `KeyManager` chọn private-key entry qua cơ chế riêng, còn `TrustManager` xử lý tin cậy certificate của peer.

### 4.2. `getCertificateAlias(cert)` không xác định mục đích entry

Nếu cùng certificate xuất hiện trong private-key chain và trusted entry, `getCertificateAlias(cert)` có thể tìm alias không như dự đoán. Đừng dùng kết quả đó để suy luận “đây là trusted root” hoặc “đây là signing key”. Thay vào đó, bạn nên lưu **đúng alias được cấu hình** và kiểm entry type, issuer/trust source, fingerprint, mục đích và policy.

## 5. Các loại KeyStore: PKCS12, JKS, JCEKS và PKCS11

`KeyStore` là **API trừu tượng**. `KeyStore.getInstance("PKCS12")` hoặc `"JKS"` chọn một **type/provider**, không quyết định mọi cấu trúc file giống nhau.

| Type / provider | Dùng khi | Lưu ý |
| --- | --- | --- |
| **PKCS12** | Định dạng file phổ biến cho private keys, certificates và (với provider phù hợp) secret keys; chọn làm demo | Khả năng tương tác có thể khác giữa Java/OpenSSL/thiết bị, nhất là secret key và cách bảo vệ/password |
| **JKS** | Hệ thống Java cũ và file truststore lâu đời | Provider JKS truyền thống chủ yếu phục vụ private-key/certificate entries, không nên giả định có SecretKeyEntry |
| **JCEKS** | Một số ứng dụng Java legacy lưu secret key | Quy tắc/mức bảo vệ và tương thích khác; cân nhắc migration có kiểm soát thay vì mặc định dùng cho hệ thống mới |
| **PKCS11** | Liên kết tới token/HSM/thiết bị bảo mật qua provider | Alias, mật khẩu/PIN, khả năng export key và lưu trữ phụ thuộc HSM; không nhất thiết có file `.p12` |
| **Provider tùy biến/KMS integration** | Hệ thống phân quyền/rotation/audit chuyên dụng | Kiểm `getEntry/setEntry`, supported key types và nghiệp vụ theo tài liệu provider |

Trên nhiều JDK hiện đại, `KeyStore.getDefaultType()` thường là `"pkcs12"`, nhưng **có thể bị cấu hình hoặc thay đổi theo môi trường**. Nếu định dạng cần tương thích, hãy chỉ rõ type và kiểm cấu hình thay vì dựa vào default.

**Cùng đuôi `.p12` không chứng minh file là PKCS12 hợp lệ**, và cùng type PKCS12 không bảo đảm secret keys được mọi công cụ bên ngoài đọc như nhau. Khi chuyển qua OpenSSL, HSM, hệ điều hành khác hoặc runtime khác, phải kiểm danh sách entry, certificate chain, policy encryption/password và khả năng import/export.

## 6. Store password, entry password và ProtectionParameter

Hai khái niệm cần phân biệt:

- **Store password**: đưa vào `store.load(input, storePassword)` để mở/kiểm bảo vệ kho theo provider; và `store.store(output, storePassword)` khi ghi lại.
- **Entry password**: dùng trong `KeyStore.PasswordProtection(entryPassword)` khi đọc/ghi **key entry** nếu provider/type hỗ trợ password riêng. Có thể khác store password ở một số type/provider.

`TrustedCertificateEntry` thường lấy bằng:

~~~java
KeyStore.TrustedCertificateEntry trusted =
        (KeyStore.TrustedCertificateEntry)
                store.getEntry("corp-ca", null);
~~~

`PrivateKeyEntry` hoặc `SecretKeyEntry` thường cần bảo vệ phù hợp:

~~~java
KeyStore.Entry entry = store.getEntry(
        "identity-demo",
        new KeyStore.PasswordProtection(entryPassword));
~~~

Đây là hai **trích đoạn** chứ không phải chương trình độc lập.

**Quan trọng với PKCS12:** Không áp đặt rằng mọi provider hỗ trợ mật khẩu riêng cho từng entry. Một số provider Java triển khai PKCS12 đặt ràng buộc rằng key password phải trùng store password. Ví dụ chạy dùng **cùng một password demo** để tránh nhầm tương thích.

`KeyStore.PasswordProtection` giữ password trong vùng nhớ có thể yêu cầu `destroy()`; `char[]` của bạn nên được xóa bằng `Arrays.fill(password, '\0')` sau sử dụng. Việc xóa mảng không bảo đảm JVM/provider xóa mọi bản sao hoặc snapshot RAM. Tránh lưu password trong `String` lâu dài; riêng chương trình demo dùng chuỗi literal chỉ để dễ tái hiện và **không được sao chép cách này vào production**.

Cơ chế bảo vệ có thể khác khi dùng HSM/PKCS#11: `ProtectionParameter` có thể đại diện PIN/callback hoặc cơ chế provider. Đừng tự đặt password mặc định trong code khi triển khai thật.

### 6.1. Lỗi mật khẩu và lỗi định dạng

Các API có thể ném `IOException`, `UnrecoverableKeyException`, `UnrecoverableEntryException`, `KeyStoreException` hoặc các lỗi đặc thù provider khi:

- Store password sai hoặc file bị hỏng;
- Alias không tồn tại hoặc loại entry không đúng;
- Password của entry sai, key không giải bọc được;
- Kiểu store/file thực tế không phù hợp provider;
- Provider thiếu hỗ trợ `SecretKeyEntry` hoặc định dạng bảo vệ yêu cầu.

**Không cố đoán thêm password hoặc bỏ kiểm tính toàn vẹn khi đọc lỗi**; phải xử lý chính sách, giám sát và khôi phục từ bản backup an toàn. Không đưa password hoặc secret ra error messages/logs.

## 7. Phân biệt KeyStore danh tính và truststore

Cả hai có thể cùng được biểu diễn bằng `java.security.KeyStore`, nhưng **vai trò vận hành khác nhau**.

| Khái niệm | Identity keystore | Truststore |
| --- | --- | --- |
| Chứa trọng tâm | Private key + certificate chain cho chính ứng dụng | CA/peer certificates mà ứng dụng **chủ đích** chấp nhận làm trust anchors |
| Kiểu entry quan trọng | `PrivateKeyEntry` | `TrustedCertificateEntry` |
| Ai tiêu thụ trong JSSE/TLS | `KeyManagerFactory` chọn danh tính/certificate chain để xuất trình | `TrustManagerFactory` kiểm certificate của đối tác theo chính sách |
| Thao tác kiểm tra | Đúng private key, chain, hạn dùng và mục đích | Nguồn trust anchor đúng, chính sách certificate path và hostname validation |
| Rủi ro nhập nhằng | Chỉ có certificate nhưng tưởng có private key | Tự import certificate từ nguồn không tin cậy và coi là CA đáng tin |

Trong thực tế, một PKCS12 có thể chứa cả hai loại entry, **nhưng tách keystore và truststore** thường dễ quản trị quyền, backup, rotation và kiểm toán hơn.

### 7.1. Giao thức TLS dùng chúng thế nào?

~~~text
TLS Server / Client (mTLS)
  Identity KeyStore:
    PrivateKeyEntry(alias = server-identity)
        -> private key + end-entity certificate + chain

  TrustStore:
    TrustedCertificateEntry(alias = authorized-ca)
        -> trust anchor

KeyManagerFactory -> chọn/đưa identity certificate và ký chứng minh private key
TrustManagerFactory -> đánh giá certificate chain đối phương
~~~

**Có certificate trong truststore chưa đủ để TLS client tự động bỏ qua hostname verification hoặc bỏ qua mọi ràng buộc.** Cần dùng TLS stack an toàn, xác minh hostname khi truy cập HTTPS, kiểm certificate path, key usage, hiệu lực, revocation policy khi yêu cầu. Nếu bạn tự cấu hình `SSLContext`/`TrustManager`, đừng tạo trust-all manager.

**Đừng đưa private key vào truststore để “tin” đối tác**: chứng chỉ công khai là đủ cho trust anchor, còn private key của đối tác tuyệt đối không phải thứ ứng dụng cần biết.

## 8. Chuẩn bị ví dụ với `keytool`

Trong JDK có công cụ `keytool` để tạo, xem và quản lý KeyStore. Ta tạo **PKCS12 mới gồm một private key RSA và certificate self-signed** để làm dữ liệu mẫu. Chương trình Java sẽ thêm **AES SecretKeyEntry** và một **TrustedCertificateEntry** (cùng certificate tự ký của demo), sau đó ghi và mở lại.

**Điều kiện:** JDK 17+; chạy các lệnh trong **thư mục thử nghiệm trống** và không dùng cùng tên file dữ liệu production. Chọn password cố định chỉ để bài tập có thể tái hiện.

~~~bash
keytool -genkeypair -alias identity-demo -keyalg RSA -keysize 2048 -dname "CN=Learning Demo" -validity 7 -storetype PKCS12 -keystore demo.p12 -storepass demochangeit -keypass demochangeit -noprompt
~~~

Giải thích:

- `-genkeypair`: sinh private/public key pair và certificate self-signed demo (không phải certificate được CA thực xác minh).
- `-alias identity-demo`: tên entry để code tìm.
- `-keyalg RSA -keysize 2048`: chỉ để chạy demo phổ biến; production chọn thuật toán/cỡ khóa theo chính sách hiện hành và nhu cầu ứng dụng.
- `-dname`: Subject DN; **không** liên quan tới alias.
- `-validity 7`: certificate sống 7 ngày để demo; hết hạn sẽ không phù hợp TLS thực.
- `-storetype PKCS12 -keystore demo.p12`: ghi file PKCS12.
- `-storepass -keypass`: cùng password demo; đưa password trên dòng lệnh **có thể lộ qua history/process listing**, chỉ chấp nhận trong môi trường thử nghiệm cô lập.

Sau bước này, file `demo.p12` có **một** entry `identity-demo`. Chỉ sau khi chương trình Java chạy xong mới có thêm hai alias khác.

Bạn có thể xem nội dung entry **mà không xuất private key** bằng:

~~~bash
keytool -list -v -keystore demo.p12 -storetype PKCS12 -storepass demochangeit
~~~

Đầu ra `keytool -list` chứa alias, loại entry và thông tin certificate/fingerprint; một số trường có thể phụ thuộc provider/JDK. **Không đưa fingerprint hoặc certificate subject hiển thị vào logic authorization bằng cách đoán string**; nếu phải pinning thì quy định chính xác certificate/public-key fingerprint và cách xác minh.

**Cảnh báo:** Alias `"trust-demo"` được tạo từ certificate self-signed `identity-demo` trong cùng file **chỉ để thực nghiệm khác biệt API**. Việc thêm certificate này vào một truststore thật có thể khiến ứng dụng tin một nguồn không nên tin. Với CA/peer thật phải xác minh fingerprint/nguồn cấp qua kênh độc lập đã kiểm định trước khi import.

## 9. Ví dụ Java 17+ hoàn chỉnh, có thể biên dịch và chạy

Sau khi thực hiện lệnh `keytool` ở phần 8, lưu chương trình sau thành **`KeyStoreEntriesDemo.java`** cùng thư mục với `demo.p12`. Không cần Maven/Gradle hay dependency ngoài JDK.

~~~java
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collections;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class KeyStoreEntriesDemo {
    private static final String TYPE = "PKCS12";
    private static final Path FILE = Path.of("demo.p12");
    private static final String PRIVATE_ALIAS = "identity-demo";
    private static final String SECRET_ALIAS = "api-aes";
    private static final String TRUST_ALIAS = "trust-demo";

    public static void main(String[] args) throws Exception {
        // Chỉ cho demo. Production: mật khẩu từ secret manager.
        char[] password = "demochangeit".toCharArray();
        KeyStore.PasswordProtection entryPassword =
                new KeyStore.PasswordProtection(password);
        try {
            KeyStore store = KeyStore.getInstance(TYPE);
            try (InputStream input = Files.newInputStream(FILE)) {
                store.load(input, password);
            }

            // Entry được tạo bởi keytool: private key + certificate chain.
            KeyStore.PrivateKeyEntry original =
                    (KeyStore.PrivateKeyEntry) store.getEntry(
                            PRIVATE_ALIAS, entryPassword);
            System.out.println("Private found       = " + (original != null));
            System.out.println("Private algorithm   = "
                    + original.getPrivateKey().getAlgorithm());
            System.out.println("Chain length        = "
                    + original.getCertificateChain().length);

            // SecretKeyEntry: AES 128-bit ngẫu nhiên, không có certificate.
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(128);
            SecretKey aes = generator.generateKey();
            if (store.containsAlias(SECRET_ALIAS)
                    || store.containsAlias(TRUST_ALIAS)) {
                throw new IllegalStateException("Alias already exists");
            }
            store.setEntry(SECRET_ALIAS,
                    new KeyStore.SecretKeyEntry(aes), entryPassword);

            // Chỉ để phân biệt entry type; KHÔNG phải hành động
            // xác minh trust của certificate thật.
            Certificate demoCertificate = original.getCertificate();
            store.setEntry(TRUST_ALIAS,
                    new KeyStore.TrustedCertificateEntry(demoCertificate),
                    null);

            // Ghi kho ra file. Production cần ghi an toàn/atomic + backup.
            try (OutputStream output = Files.newOutputStream(FILE)) {
                store.store(output, password);
            }

            // Mở lại từ disk và kiểm đúng loại entry.
            KeyStore reopened = KeyStore.getInstance(TYPE);
            try (InputStream input = Files.newInputStream(FILE)) {
                reopened.load(input, password);
            }

            System.out.println("KeyStore type       = " + reopened.getType());
            System.out.println("Alias count         = "
                    + Collections.list(reopened.aliases()).size());
            System.out.println("PrivateKeyEntry     = "
                    + reopened.entryInstanceOf(PRIVATE_ALIAS,
                            KeyStore.PrivateKeyEntry.class));
            System.out.println("SecretKeyEntry      = "
                    + reopened.entryInstanceOf(SECRET_ALIAS,
                            KeyStore.SecretKeyEntry.class));
            System.out.println("TrustedCertEntry    = "
                    + reopened.entryInstanceOf(TRUST_ALIAS,
                            KeyStore.TrustedCertificateEntry.class));

            KeyStore.SecretKeyEntry restoredSecret =
                    (KeyStore.SecretKeyEntry) reopened.getEntry(
                            SECRET_ALIAS, entryPassword);
            KeyStore.TrustedCertificateEntry restoredTrusted =
                    (KeyStore.TrustedCertificateEntry) reopened.getEntry(
                            TRUST_ALIAS, null);
            System.out.println("Secret algorithm    = "
                    + restoredSecret.getSecretKey().getAlgorithm());
            System.out.println("Secret bits         = "
                    + restoredSecret.getSecretKey().getEncoded().length * 8);
            System.out.println("Secret restored     = "
                    + Arrays.equals(aes.getEncoded(),
                            restoredSecret.getSecretKey().getEncoded()));
            System.out.println("Trusted cert type   = "
                    + restoredTrusted.getTrustedCertificate().getType());
            System.out.println("Trusted is key      = "
                    + reopened.isKeyEntry(TRUST_ALIAS));
            System.out.println("Missing alias       = "
                    + reopened.containsAlias("no-such-entry"));
        } finally {
            entryPassword.destroy();
            Arrays.fill(password, '\0');
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac KeyStoreEntriesDemo.java
java KeyStoreEntriesDemo
~~~

Đầu ra được kiểm chứng với một PKCS12 mới do `keytool` tạo, theo lệnh trên:

~~~text
Private found       = true
Private algorithm   = RSA
Chain length        = 1
KeyStore type       = PKCS12
Alias count         = 3
PrivateKeyEntry     = true
SecretKeyEntry      = true
TrustedCertEntry    = true
Secret algorithm    = AES
Secret bits         = 128
Secret restored     = true
Trusted cert type   = X.509
Trusted is key      = false
Missing alias       = false
~~~

**Giải thích đầu ra:**

1. `Private found = true`: `keytool` đã thêm `identity-demo`, Java đọc được sau `load` đúng password. `Private algorithm = RSA` do tham số `-keyalg RSA`. Chuỗi một phần tử do certificate self-signed demo; **chain thật có thể dài hơn**.
2. `store.setEntry("api-aes", SecretKeyEntry, entryPassword)` thêm AES key 128 bit. Sau `store.store` và `reopened.load`, `getEntry` đọc được đúng byte AES (chỉ in `true`, không in key).
3. `store.setEntry("trust-demo", TrustedCertificateEntry, null)` lưu certificate demo dưới **alias mới**, không thêm private key. `entryInstanceOf` trả loại cụ thể, còn `isKeyEntry("trust-demo")` trả false.
4. `Alias count = 3` vì file ban đầu có một entry và chương trình thêm hai entry. Nếu chạy chương trình **lần hai trên cùng file**, nhánh kiểm alias sẽ ném lỗi để tránh vô tình ghi đè. Muốn lặp bài tập, tạo thư mục demo mới và sinh keystore mới bằng lệnh `keytool`.
5. `Missing alias = false` chứng minh `containsAlias` không tự tạo entry khi truy vấn, và tên không tồn tại không cung cấp key.
6. `KeyStore.PasswordProtection` được dùng để đọc/ghi key entries, còn certificate entry chỉ cần `null` protection trong provider demo. Phần `finally` cố xóa mật khẩu demo khỏi vùng nhớ có thể quản lý, nhưng **không bảo đảm zeroize mọi bản sao trong JVM**.

**Giới hạn của ví dụ:** Password cố định trên dòng lệnh/trong source, certificate tự ký, RSA demo và **ghi đè file trực tiếp** không phải cấu hình production. Với hệ thống thật: dùng secret manager/KMS/HSM, certificate/CA hợp lệ được kiểm nguồn, chính sách access, audit, backup, và ghi file theo cách crash-safe. `getEncoded()` có thể trả `null` với khóa không exportable; câu lệnh tính độ dài secret trong demo chỉ phù hợp key AES của `KeyGenerator` chuẩn.

## 10. Lấy đúng entry: `getEntry` hay `getKey` / `getCertificate`?

Một bug thường gặp là lấy object theo alias rồi **ép kiểu không kiểm tra**. Dùng `entryInstanceOf` hoặc `isKeyEntry`/`isCertificateEntry` theo nhu cầu, nhưng phải nhớ `isKeyEntry` bao trùm hai loại key.

| Bạn muốn | API nên dùng | Điều cần kiểm |
| --- | --- | --- |
| Private key + cert chain | `getEntry(alias, PasswordProtection)` rồi `PrivateKeyEntry` | Alias tồn tại và đúng loại; password đúng; chain hợp lệ |
| AES/HMAC key | `getEntry(alias, PasswordProtection)` rồi `SecretKeyEntry` | SecretKeyEntry được provider hỗ trợ, algorithm/usage phù hợp |
| Certificate được chọn làm trusted | `getEntry(alias, null)` rồi `TrustedCertificateEntry` | Nguồn tin cậy được xác minh **ngoài** KeyStore |
| Chỉ certificate leaf của identity | `getCertificate(alias)` | Đây có thể là cert trong PrivateKeyEntry, **không có nghĩa trusted** |
| Certificate chain | `getCertificateChain(alias)` | Chỉ có ý nghĩa với entry private key/chain |
| Kiểm loại trước cast | `entryInstanceOf(alias, EntryType.class)` | Kiểm đúng lớp, không chỉ `isKeyEntry` |
| Lấy key bằng API cũ | `getKey(alias, password)` | Có thể trả private hoặc secret key; cần kiểm kiểu/algorithm |

`getEntry` với alias không tồn tại có thể trả `null`. Alias tồn tại nhưng mật khẩu hoặc loại protection không phù hợp có thể ném exception theo provider, **không đồng nhất “null” với sai password**. Không bắt mọi exception rồi giả vờ như thiếu alias, vì có thể che giấu lỗi bảo vệ khóa.

### 10.1. Vì sao `getCertificate(alias) != null` chưa đủ?

Nếu alias trỏ tới `PrivateKeyEntry`, `getCertificate(alias)` thường trả **chứng chỉ đầu tiên** của chain. Nếu alias trỏ tới `TrustedCertificateEntry`, nó trả certificate được lưu riêng. Cùng trả `Certificate` nhưng ngữ nghĩa khác nhau. Khi cần trust anchor, kiểm **entry type**, nguồn certificate, policy và cấu hình trust manager; khi cần ký, kiểm **PrivateKeyEntry** có private key tương ứng.

### 10.2. Vì sao phải phân biệt `PrivateKey` và `SecretKey`?

`PrivateKey` dùng trong hệ mật mã khóa bất đối xứng; `SecretKey` thường dùng cho mã hóa đối xứng/MAC. `Key` là interface chung nên `getKey` có thể trả một trong hai. Nếu chỉ gọi `key.getAlgorithm()` hoặc kiểm “key != null”, ứng dụng có thể lẫn vai trò. Hãy kiểm kiểu đúng, thuật toán/suite và policy sử dụng khi lấy key ra khỏi KeyStore.

## 11. Vòng đời alias: tạo, đọc, thay, xóa, đổi tên

Một KeyStore là **kho lưu trạng thái**, việc thay alias có thể phá ứng dụng trỏ tới tên cũ.

~~~text
START
  |
  +-- containsAlias(name)?
         | YES --> kiểm purpose/type/version, không ghi đè âm thầm
         | NO
         v
      setEntry(name, entry, protection)
         |
         v
      store(output, storePassword)
         |
         v
      reload + verify entry types / aliases
~~~

### 11.1. Tạo alias

- Chọn tên có quy tắc, tránh ký tự nhập nhằng và tên case-fold collision theo provider.
- Kiểm `containsAlias`, backup và xác nhận không dùng tên thuộc mục đích khác.
- `setEntry` cập nhật **trạng thái của object KeyStore trong bộ nhớ**; để lưu với file-backed store, phải gọi `store(...)`. Nếu ứng dụng crash trước khi lưu, thay đổi chưa chắc tồn tại trên disk.
- Alias/metadata không cấp quyền; kiểm quyền theo filesystem, secret manager, HSM/KMS.

### 11.2. Xóa alias

~~~java
// Trích đoạn minh họa; không chạy nếu chưa kiểm backup/phụ thuộc.
if (store.containsAlias("old-identity")) {
    store.deleteEntry("old-identity");
}
~~~

Xóa entry trong bộ nhớ **không tự xóa file**; sau đó cần ghi lại file theo cách an toàn. Xóa private key đang được TLS listener, signing service hoặc job khác sử dụng có thể làm chúng mất khả năng xác thực/ký. Xóa certificate trust anchor có thể khiến kết nối hợp lệ bị từ chối. Xóa key đã dùng mã hóa dữ liệu lưu trữ có thể khiến bản mã **không thể giải mã** nếu không còn key backup.

### 11.3. Đổi alias

`KeyStore` không có một phương thức chuẩn `renameEntry(alias1, alias2)`. Có thể dùng công cụ `keytool -changealias` cho file format/provider hỗ trợ hoặc di chuyển entry bằng logic kiểm soát (copy với protection đúng, xác minh alias đích, lưu an toàn rồi xóa cũ). Khi đổi alias, cần cập nhật cấu hình ứng dụng và xác minh chain/key vẫn đúng; không làm thủ công trong production nếu chưa kiểm backup/rollback.

**Lưu ý:** Một số provider không cho copy/export private key hoặc đổi alias theo cách bạn muốn; trong HSM phải dùng API quản lý khóa của provider.

### 11.4. Rotation và key versions

Không dùng một alias cố định rồi âm thầm **thay nội dung** nếu dữ liệu cũ vẫn cần key cũ hoặc peer cũ đang dùng certificate cũ. Có thể quản lý versioned aliases và key IDs, ví dụ `"payments-data-aes-v3"`, đồng thời có migration/re-encryption/rewrap, thời gian chuyển tiếp và chính sách xóa key cũ.

Với private-key TLS, certificate renewal có thể thay chain dù giữ key cũ (không phải luôn nên giữ key cũ). Với key rotation có thể cần tạo key pair mới, CSR, certificate mới, reload cấu hình và giám sát certificate expiry. Với truststore, thêm CA mới trước khi loại CA cũ phải theo chính sách tin cậy và kết nối thực tế.

## 12. Trạng thái KeyStore và thao tác lưu file an toàn

Thao tác thường gặp:

~~~text
KeyStore.getInstance("PKCS12")
  -> load(InputStream, char[] storePassword)
  -> containsAlias / aliases / getEntry / setEntry / deleteEntry
  -> store(OutputStream, char[] storePassword)
  -> load lại trong instance độc lập để xác minh
~~~

Tạo **store rỗng mới** trong Java có thể dùng `store.load(null, password)` trước khi thêm entries. Ngược lại, `load(input, password)` trên file có sẵn là thao tác đọc dữ liệu; không gọi `load(null, ...)` khi đang muốn mở file cũ vì có thể tạo trạng thái mới rỗng trong bộ nhớ.

### 12.1. Đừng ghi trực tiếp vào file quan trọng nếu có rủi ro crash

Ví dụ ở phần 9 dùng `Files.newOutputStream(FILE)` để minh họa `store`, nhưng có thể **truncate file cũ trước khi ghi xong**. Trong production:

1. Khóa hoặc điều phối thao tác thay đổi để tránh hai process cùng sửa file.
2. Load và kiểm phiên bản/nội dung store hiện tại.
3. Ghi sang **file tạm trong cùng filesystem** với quyền/ACL phù hợp.
4. Flush và đảm bảo dữ liệu đã được ghi ổn định theo yêu cầu hệ thống.
5. Di chuyển/replace một cách **atomic nếu filesystem hỗ trợ** (Java `Files.move` với `ATOMIC_MOVE`; có thể cần fallback an toàn tùy platform).
6. Giữ backup được bảo vệ và kiểm khả năng khôi phục.
7. Mở lại store sau thay đổi và kiểm aliases, types, certificate fingerprints/expiry, chain và khả năng giải bọc key theo test không nhạy cảm.

**Không dùng KeyStore file như database có transaction đa người ghi**. Khi nhiều instance cùng quản lý keys, cân nhắc hệ quản lý secret/key tập trung với phân quyền và audit.

## 13. Công cụ `keytool` và những hành động thường gặp

| Lệnh | Mục đích | Lưu ý |
| --- | --- | --- |
| `keytool -genkeypair` | Tạo private key + certificate demo/CSR workflow | Chọn key algorithm, identity DN, thời hạn, store type |
| `keytool -list -v` | Xem alias, entry type và certificate details | Không dùng output làm bản ghi bí mật; tránh đưa storepass vào lịch sử lệnh thật |
| `keytool -exportcert` | Xuất certificate công khai | **Không** xuất private key; biết đúng alias và format |
| `keytool -importcert` | Thêm certificate đã được xác minh hoặc certificate reply/chain theo ngữ cảnh | Kiểm certificate fingerprint và nguồn từ kênh tin cậy |
| `keytool -delete` | Xóa entry theo alias | Có backup, đánh giá phụ thuộc và cách reload |
| `keytool -changealias` | Đổi alias khi provider hỗ trợ | Kiểm collision, cấu hình và backup trước |
| `keytool -importkeystore` | Di chuyển entries giữa store/type | Kiểm storepass/keypass, certificate chain, secret key support, provider |

**Khi import cert vào trusted entry**, rủi ro lớn nhất không phải lệnh gõ sai mà là việc **tin certificate sai nguồn**. Đừng dùng lựa chọn bỏ qua xác minh fingerprint chỉ để import thành công. Một certificate self-signed được lưu vào truststore có thể có mục đích hợp lệ trong hệ thống riêng nhưng phải đi qua **quy trình kiểm nguồn/out-of-band**.

**Không lấy private key/certificate của môi trường production để chạy bài học.** Tất cả ví dụ dùng khóa thử nghiệm.

## 14. Kịch bản áp dụng

### 14.1. TLS server không khởi động vì chỉ có certificate

Một nhóm triển khai import cert leaf vào store dưới alias `"api-server"` như `TrustedCertificateEntry`, rồi cấu hình `KeyManagerFactory` chọn alias đó. Server không thể ký handshake bằng cert vì **không có private key**. Cách đúng là nạp `PrivateKeyEntry` chứa **đúng private key** và chain tương ứng; nếu khóa nằm trong HSM thì cấu hình provider nhận private key từ HSM.

### 14.2. Thêm AES key vào JKS bị lỗi

Ứng dụng cũ mở `KeyStore.getInstance("JKS")`, gọi `setEntry(..., SecretKeyEntry, ...)` và nhận lỗi provider không hỗ trợ. Không thể sửa bằng đổi file extension sang `.p12`: phải **tạo/import PKCS12 thật** hoặc chọn provider/KMS hỗ trợ secret key, sau đó xác minh đọc lại key. Cũng cần kiểm thuật toán bảo vệ key, quyền file và backup.

### 14.3. Đổi alias mà ứng dụng vẫn trỏ tên cũ

Keystore đã có `"new-tls-v2"` nhưng cấu hình `server.ssl.key-alias` hoặc key manager vẫn chọn `"old-tls"`; kết quả có thể là không tìm thấy key hoặc chọn danh tính sai. Alias là một **giao diện cấu hình**, cần kiểm cross-service deployment, restart/reload, certificate validity và rollback.

### 14.4. Nhầm chứng chỉ identity với trust anchor

`getCertificate("identity-demo")` trả certificate của key pair. Một người dùng certificate ấy để tự xây “trust-all” logic cho server khác. Đây là **vượt qua kiểm nguồn**; chứng chỉ identity của mình không chứng minh CA của đối tác đáng tin. Để xác minh peer, xây truststore từ CA/peer được chấp nhận có kiểm tra nguồn, và dùng trust manager đúng chuẩn.

### 14.5. SecretKeyEntry đã restore nhưng hệ thống vẫn không giải mã được

`Secret restored = true` chứng minh byte key trong ví dụ được lưu/nạp chính xác, nhưng decrypt thực tế còn cần **đúng thuật toán, mode, nonce/IV, tag, AAD, schema và ciphertext**. Alias trỏ đúng key là điều kiện cần, không phải đủ. Với AES-GCM, không bao giờ dùng lại cặp key/nonce cho hai lần encrypt, và phải xác minh tag trước khi xử lý plaintext.

### 14.6. File chứa cả key và trusted certificates

PKCS12 có thể lưu cả ba entry types; điều đó không bắt buộc mọi ứng dụng phải chấp nhận tất cả. Khi cấu hình TLS, nên giữ identity keystore/truststore tách biệt theo trách nhiệm, kể cả khi cùng dùng PKCS12. Việc chuyển format phải kiểm chính sách certificate validation và alias selection.

## 15. Những lỗi phổ biến

| Sai lầm | Vì sao nguy hiểm/sai | Cách khắc phục |
| --- | --- | --- |
| Gọi `isKeyEntry` rồi cast `PrivateKeyEntry` | `isKeyEntry` cũng trả true cho SecretKeyEntry | Dùng `entryInstanceOf(alias, PrivateKeyEntry.class)` |
| Tưởng cert ở `PrivateKeyEntry` là `TrustedCertificateEntry` | Lẫn identity của mình với trust anchor | Phân biệt type và chính sách sử dụng |
| Tưởng TrustedCertificateEntry có private key | Không thể ký/giải mã bằng private key không tồn tại | Dùng PrivateKeyEntry có đúng key + chain |
| Lưu một public key X.509 mà chưa kiểm nguồn rồi gọi là trusted | Mở cửa MITM/trust sai | Xác minh fingerprint, CA/path, quyền đưa vào truststore |
| Ghi `setEntry` trùng alias | Có thể thay entry cũ và làm mất key/cert quan trọng | `containsAlias`, backup, versioned aliases |
| Giả định alias case-sensitive | Collision/nghi vấn khi provider canonicalize alias | Đặt alias ASCII theo quy ước, test provider |
| Dựa vào thứ tự `aliases()` để chọn key | Enumeration order không được cam kết | Chọn alias rõ ràng theo cấu hình |
| Sử dụng JKS để lưu AES SecretKeyEntry | Provider truyền thống không hỗ trợ | PKCS12 tương thích hoặc KMS/HSM phù hợp |
| Dùng store password ở mọi provider mà không kiểm entry protection | Sai keypass/không mở được key | Hiểu Store vs Entry protection theo type/provider |
| Ghi password trong code/command production | Lộ qua source, shell history, process list | Secret manager/callback, kiểm quyền và audit |
| Quên gọi `store` sau `setEntry` | Thay đổi chỉ ở RAM, mất sau restart | Store an toàn rồi reload xác nhận |
| Lưu trực tiếp đè file production | Crash có thể làm hỏng file keystore | Temp file, atomic move khi hỗ trợ, backup |
| Xóa alias của key còn dùng decrypt | Không thể phục hồi dữ liệu cũ | Rotation với key ID, retention/rewrap và backup |
| Coi certificate chain là đã xác minh trust | Peer phải validate chain, name, expiry, usage | Trust manager/certificate validation đúng |
| Dùng `getCertificateAlias` để suy ra entry type | Một cert có thể xuất hiện ở nhiều alias | `entryInstanceOf` + mục đích cấu hình |
| Tưởng KeyStore file thay KMS | Thiếu audit/rotation/ACL tập trung; key có thể xuất RAM | Quản lý file/OS/HSM/KMS theo threat model |

## 16. Bài tập tự kiểm tra (có đáp án)

**Câu 1.** Một alias chứa private key RSA và certificate chain. Đây là entry gì? API nào nhận đúng loại?

**Đáp án:** `PrivateKeyEntry`. Dùng `getEntry(alias, PasswordProtection)` kết hợp `entryInstanceOf(alias, PrivateKeyEntry.class)`; private key và chain phải khớp và được bảo vệ.

**Câu 2.** Alias `"api-aes"` chứa khóa AES. `isKeyEntry("api-aes")` trả true. Có thể cast thành `PrivateKeyEntry` không?

**Đáp án:** Không. `SecretKeyEntry` cũng là key entry. Kiểm chính xác `SecretKeyEntry.class` hoặc nhận `SecretKey` theo provider phù hợp.

**Câu 3.** Một certificate được lưu với `setCertificateEntry("corp-ca", cert)`. Nó có chứa private key không?

**Đáp án:** Không, đó là `TrustedCertificateEntry` độc lập. Chỉ certificate công khai; không thể dùng để ký hoặc chứng minh sở hữu private key.

**Câu 4.** Một private key entry có certificate chain, vậy `isCertificateEntry(alias)` có true không?

**Đáp án:** Không theo nghĩa API: đây là **key entry**. `isCertificateEntry` dành cho certificate entry độc lập.

**Câu 5.** Tại sao `getCertificateAlias(cert)` không đủ để biết cert là trust anchor?

**Đáp án:** Một cert có thể nằm trong chain của PrivateKeyEntry và được lưu dưới TrustedCertificateEntry. Alias tìm được không chứng minh nguồn tin cậy hay loại sử dụng; phải xem entry type và chính sách trust.

**Câu 6.** Nhóm vận hành tạo `"MyKey"` và `"mykey"`, nghĩ sẽ có hai entry. Có chắc không?

**Đáp án:** Không. Alias case handling phụ thuộc provider; có thể canonicalize/collision. Dùng tên duy nhất không nhập nhằng theo chuẩn đặt tên và kiểm trước khi ghi.

**Câu 7.** Đã gọi `store.setEntry(...)` trong chương trình nhưng sau restart entry mới biến mất. Vì sao?

**Đáp án:** Vì sửa state trong RAM không tự ghi file. Cần gọi `store(output, password)` và kiểm reload, lưu bằng quy trình an toàn.

**Câu 8.** Có nên lưu chứng chỉ self-signed vừa tải từ website lạ như TrustedCertificateEntry để “khỏi bị báo TLS lỗi”?

**Đáp án:** Không. Không xác minh nguồn/kênh độc lập nghĩa là có thể tin khóa của attacker. Phải đánh giá certificate, identity, fingerprint/CA và trust policy.

**Câu 9.** Một JKS hiện có certificate/key pair nhưng khi thêm AES SecretKeyEntry bị lỗi. Đổi đuôi file sang `.p12` có chữa được không?

**Đáp án:** Không. Type/provider và cấu trúc dữ liệu thực tế mới quyết định support. Cần chuyển đổi/import có kiểm soát hoặc dùng store/provider phù hợp.

**Câu 10.** Vì sao không nên xóa alias của DEK cũ ngay khi tạo DEK v2?

**Đáp án:** Bản mã cũ còn cần key cũ để giải mã/rewrap/re-encrypt. Xóa key trước migration và backup có thể gây mất dữ liệu không thể phục hồi.

## 17. Checklist áp dụng KeyStore

- [ ] Biết rõ store **type/provider** và compatibility (PKCS12/JKS/HSM); không dựa vào phần mở rộng tên file.
- [ ] Phân biệt **PrivateKeyEntry**, **SecretKeyEntry**, **TrustedCertificateEntry** bằng type, không chỉ `isKeyEntry`.
- [ ] Private-key entry có đúng private key và chain; leaf certificate là của key đó, identity/usage/expiry được xác minh theo nhu cầu.
- [ ] Secret key đúng thuật toán, độ dài, usage và provider; không mặc nhiên exportable.
- [ ] Trusted certificate được kiểm **nguồn, fingerprint và chính sách** trước khi đưa vào truststore; không biến cert lạ thành trust anchor.
- [ ] Alias theo quy ước ổn định, unique theo provider; không ghi đè nhầm; không suy ra quyền từ alias.
- [ ] Store password và entry protection được quản lý đúng, không hard-code trong production, có cơ chế rotation/audit.
- [ ] Đã backup và có quy trình **store an toàn/reload verify** trước khi rollout.
- [ ] Identity keystore và truststore có trách nhiệm rõ ràng; KeyManager/TrustManager được cấu hình an toàn.
- [ ] Key ID/alias version và retention phù hợp lifecycle; không xóa key còn dùng đọc dữ liệu cũ.
- [ ] Có kiểm thử trên **JDK/provider/type thật** để xác nhận secret key support, alias behavior, certificate chain và password policy.

**Kết luận:** `KeyStore` là **kho entry được đánh chỉ mục bằng alias**, không phải một kiểu key duy nhất. **`PrivateKeyEntry`** lưu private key với certificate chain; **`SecretKeyEntry`** lưu secret key đối xứng; **`TrustedCertificateEntry`** lưu certificate độc lập được ứng dụng chủ đích chấp nhận làm nguồn tin cậy. Alias chỉ là nhãn tra cứu; sự an toàn còn đến từ đúng provider/format, nguồn certificate, protection, quyền file, quản lý khóa và quy trình cập nhật có kiểm soát.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-store-003">12.3 PKCS12, JKS và các loại kho do provider cung cấp</a>

<details>
<summary>Xem chi tiết</summary>

# 12.3 PKCS12, JKS và các loại kho do provider cung cấp

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Mô tả đúng vai trò của **java.security.KeyStore** như một API lưu/truy cập khóa và chứng chỉ do **Security Provider** triển khai, không đồng nhất KeyStore với một tệp có đuôi .jks.
2. So sánh **PKCS12**, **JKS**, **JCEKS**, **DKS**, **PKCS11**, kho của hệ điều hành (Windows-MY/Windows-ROOT, macOS KeychainStore) và loại kho do provider bên thứ ba cung cấp.
3. Phân biệt **keystore** chứa khóa định danh của server/client với **truststore** chứa các chứng chỉ/điểm tin cậy cho xác minh đối tác, và hiểu việc này phụ thuộc **vai trò sử dụng**, không chỉ tên đuôi tệp.
4. Chọn định dạng lưu khóa phù hợp cho ứng dụng Java 17+, hiểu **mặc định PKCS12 của JDK 9+**, các hạn chế tương thích và lý do tránh JKS/JCEKS cho thiết kế mới.
5. Dùng KeyStore.getInstance(...), load(...), setEntry(...), getEntry(...), store(...), kiểm tra alias/entry/provider, thực hiện ví dụ Java 17+, và nhận biết cách cấu hình KeyStore dựa trên HSM/PKCS11.
6. Lập kế hoạch **migration JKS → PKCS12**, bảo vệ file/mật khẩu, kiểm tra quyền sử dụng private key, cấu hình TLS, rotation và backup mà không coi “đã có keystore” là đủ để bảo mật.

**Ghi nhớ:** **KeyStore là một giao diện trừu tượng; PKCS12/JKS/PKCS11/... là các kiểu triển khai của nó**. Loại kho quyết định cách biểu diễn, bảo vệ và truy cập các entry, còn provider quyết định tính năng thực tế. Không có một quy tắc “mọi KeyStore đều là file”, “mọi loại kho chứa được mọi loại key” hoặc “mọi password đều tương đương với bảo vệ bằng HSM”.

## 1. Bức tranh tổng quan: KeyStore, type, provider và location

Java Cryptography Architecture (JCA) dùng mô hình **engine API + provider**. Lập trình viên gọi các phương thức chuẩn của java.security.KeyStore, trong khi triển khai cụ thể là KeyStoreSpi của provider.

~~~text
Ứng dụng Java 17+
      |
java.security.KeyStore (API)
      |
KeyStore.getInstance("TYPE" [, provider])
      |
+--------------------------------------------+
| Provider cung cấp KeyStoreSpi               |
|                                            |
| SUN        -> PKCS12, JKS, DKS, ...         |
| SunJCE     -> JCEKS                         |
| SunPKCS11  -> PKCS11 token / HSM            |
| SunMSCAPI  -> Windows-MY, Windows-ROOT      |
| Apple      -> KeychainStore trên macOS      |
| Third-party-> các keystore type riêng       |
+--------------------------------------------+
      |
File .p12/.jks, hệ thống OS, token/HSM,
domain keystore hoặc backend chuyên biệt
~~~

**Bốn khái niệm phải phân biệt:**

- **KeyStore API:** tập phương thức dùng chung như load, getEntry, setEntry, aliases, store.
- **KeyStore type:** định danh kiểu kho, ví dụ "PKCS12", "JKS", "PKCS11"; mô tả triển khai/định dạng hoặc cách truy cập.
- **Provider:** nhà cung cấp hiện thực KeyStoreSpi và thuật toán liên quan; ví dụ SUN, SunJCE, SunPKCS11, SunMSCAPI, Apple. Tên type và khả năng phụ thuộc provider đã cài/cấu hình.
- **Backing store:** nơi giữ thông tin: file trên đĩa, Windows certificate store, macOS Keychain, smartcard/HSM hoặc hệ thống chuyên biệt. Không phải type nào cũng có một file tương ứng.

Trong JDK, việc chọn provider chịu ảnh hưởng bởi danh sách provider, cấu hình bảo mật và đôi khi chính sách provider ưu tiên. Vì thế **KeyStore.getInstance("PKCS12")** và **KeyStore.getInstance("PKCS12", "SUN")** không hoàn toàn là cùng một yêu cầu. Mặc định, chỉ định type rồi để JCA chọn provider thường linh hoạt hơn; ghim provider chỉ khi thật sự cần tính năng, cơ chế HSM hoặc khả năng tương thích được kiểm định.

Ví dụ quan sát loại kho và provider:

~~~java
// Đoạn minh họa, không phải chương trình độc lập.
KeyStore ks = KeyStore.getInstance("PKCS12");
System.out.println(ks.getType());
System.out.println(ks.getProvider().getName());
~~~

**getType()** trả tên type của instance, **getProvider()** trả provider được chọn; hai giá trị này giúp chẩn đoán chứ không chứng minh file/khóa đã an toàn. Trước khi dùng phương thức đọc entry, instance thường phải được **load(...)**.

## 2. KeyStore có những loại entry nào?

**java.security.KeyStore** có các entry chính:

| Entry | Nội dung | Tình huống |
| --- | --- | --- |
| **KeyStore.PrivateKeyEntry** | PrivateKey và chuỗi certificate chain phù hợp | Khóa TLS của server/client, khóa ký, định danh mTLS |
| **KeyStore.SecretKeyEntry** | SecretKey đối xứng | AES/HMAC key được lưu theo chính sách provider hỗ trợ |
| **KeyStore.TrustedCertificateEntry** | Chứng chỉ được xem là trust anchor hoặc điểm tin cậy theo cấu hình | CA nội bộ, chứng chỉ CA tin cậy cho TLS |
| **Provider-specific entries** | Có thể có tính năng hoặc hạn chế riêng | Key handle, token/HSM/OS store; không giả định giống file store |

Ví dụ, một kho PKCS12 cho HTTPS server có thể chứa **PrivateKeyEntry** gồm private key và certificate chain. Một truststore để kiểm tra chứng chỉ server có thể chứa các **TrustedCertificateEntry**. Tuy nhiên cả hai vẫn có thể dùng cùng **format PKCS12**: thuật ngữ keystore/truststore chủ yếu nói về **vai trò ứng dụng**.

Các phương thức thường dùng:

- **aliases():** lấy danh sách tên entry (Enumeration).
- **containsAlias(alias):** alias tồn tại hay không.
- **entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class):** kiểm tra lớp entry.
- **isKeyEntry(alias):** alias chứa key entry, không nhất thiết là private key.
- **isCertificateEntry(alias):** alias là một trusted certificate entry theo API; không đồng nghĩa mọi certificate trong chain đều là entry độc lập.
- **getEntry(alias, ProtectionParameter):** truy cập entry với cơ chế bảo vệ như PasswordProtection.
- **setEntry(alias, Entry, ProtectionParameter):** thêm/sửa entry nếu provider cho phép.
- **getCertificateChain(alias), getCertificate(alias):** truy cập chứng chỉ theo khả năng của loại entry.

**Alias không phải khóa mật mã**. Alias là tên để tra cứu một entry. Provider có thể áp dụng quy tắc phân biệt hoặc chuẩn hóa chữ hoa/thường khác nhau. Khi tích hợp nhiều provider, không nên tạo các alias chỉ khác nhau ở chữ hoa/thường rồi kỳ vọng chúng luôn tách biệt.

### 2.1 Private key và certificate chain

Một **PrivateKeyEntry** dùng cho TLS cần private key khớp với public key trong certificate chain tương ứng. KeyStore lưu trữ không thay thế việc:

- Có chứng chỉ đúng tên máy chủ, mục đích (EKU/key usage), thời hạn;
- Có chuỗi chứng chỉ đầy đủ theo yêu cầu triển khai;
- Private key có thể thực hiện ký/xác thực trong provider/HSM;
- TLS engine được cấu hình **KeyManagerFactory** và **TrustManagerFactory** phù hợp;
- Chính sách trust, hostname verification, thời gian và trạng thái thu hồi chứng chỉ được kiểm tra.

Không đồng nhất “file chứa certificate” với “file chứa private key”. **TrustedCertificateEntry không có private key**.

### 2.2 Secret key và khả năng hỗ trợ type

Không phải provider/KeyStore type nào cũng hỗ trợ lưu **SecretKeyEntry**. Trên JDK Sun điển hình, PKCS12 và JCEKS hỗ trợ entry secret key, còn JKS truyền thống **không lưu SecretKeyEntry**.

Khi một ứng dụng từ JKS chuyển sang PKCS12, khả năng hỗ trợ SecretKeyEntry là một điểm khác biệt hữu ích. Tuy nhiên **PKCS12 secret key bag do JDK tạo không đảm bảo tương thích hoàn toàn với mọi công cụ ngoại ngữ**, dù PKCS12 là format chuẩn. Mỗi loại entry, thuật toán bảo vệ, mật khẩu và cách encode đều phải được kiểm tra với ứng dụng đối tác.

Với HSM, một số secret key **không cho phép xuất raw bytes**. Đừng coi việc getEncoded() trả null là lỗi cần “vượt rào”; đó có thể là thuộc tính bảo vệ được thiết kế.

## 3. PKCS12: định dạng nên cân nhắc đầu tiên cho file keystore mới

**PKCS12 (PKCS #12, thường có đuôi .p12 hoặc .pfx)** là định dạng được chuẩn hóa để trao đổi/lưu các đối tượng như private key và chứng chỉ cùng thông tin liên quan. Nó có tính liên thông tốt hơn JKS với nhiều hệ thống khác, đặc biệt khi trao đổi **private key + certificate chain**.

JDK 9 trở lên dùng **PKCS12 làm default keystore type** trong cấu hình phân phối tiêu chuẩn. Với Java 17, kết quả thường gặp:

~~~java
String type = KeyStore.getDefaultType();  // Thường là "pkcs12".
~~~

Tuy nhiên kết quả là **giá trị cấu hình bảo mật keystore.type** trong runtime đang chạy, nên có thể đã được administrator, bản JDK hoặc môi trường triển khai thay đổi. Không hardcode rằng mọi Java process trên thế giới đều có cùng default.

Ưu điểm thực tế của PKCS12:

1. Phù hợp nhiều sản phẩm, công cụ và runtime ngoài Java khi sử dụng thuật toán/định dạng tương thích.
2. Là lựa chọn mặc định hiện đại của JDK cho file-based keystore.
3. Có thể lưu private keys, certificate chains và các loại entry khác khi provider hỗ trợ.
4. Hỗ trợ cơ chế bảo vệ bằng mật khẩu và các thuật toán PBE/MAC tùy triển khai/provider.
5. Có thể dùng với **keytool**, JCA, TLS KeyManagerFactory và các công cụ thao tác PKCS12 phù hợp.

**Không suy ra từ chữ “PKCS12” rằng file tự động có bảo vệ tối ưu.** Mức an toàn còn phụ thuộc:

- Thuật toán mã hóa key bag/PBE, MAC và tham số của provider;
- Độ mạnh của mật khẩu, khả năng bị brute-force offline;
- Quyền truy cập filesystem, backup, CI artifacts, volume và audit;
- Phiên bản JDK, lựa chọn chế độ tương thích legacy;
- Việc private key có exportable hay không;
- Cách ứng dụng lưu password và nơi giải mã key vào bộ nhớ.

File PKCS12 **không tương đương HSM**: tiến trình có password và file có thể lấy key khi chính sách cho phép, nên key material có thể đi vào bộ nhớ ứng dụng. Nếu cần private key non-exportable và chính sách ký trong phần cứng, dùng HSM/KMS/OS keystore phù hợp.

### 3.1 Đuôi tệp .p12 / .pfx không phải bằng chứng format

Tên file là quy ước. Một file tên credentials.p12 có thể không chứa PKCS12 hợp lệ; file tên old.jks đôi khi là PKCS12 vì người vận hành đặt tên sai. Phải biết format thực, kiểm tra bằng công cụ phù hợp, tránh đoán từ extension.

Khi muốn giảm sự phụ thuộc vào default và phát hiện lỗi rõ hơn:

~~~java
KeyStore store = KeyStore.getInstance("PKCS12");
try (InputStream input = Files.newInputStream(path)) {
    store.load(input, password);
}
~~~

Đây là đoạn minh họa cần import java.security.KeyStore, java.io.InputStream, java.nio.file.Files/Path và định nghĩa biến path/password; **không phải chương trình độc lập**. Trong thiết kế bảo mật, không tiếp tục nếu tải kho gặp exception hoặc integrity check thất bại.

### 3.2 Mật khẩu kho và mật khẩu entry

Ở nhiều file keystore, có:

- **Store password:** bảo vệ/kiểm tra dữ liệu của kho khi load/store.
- **Key/entry password:** dùng để giải mã private key hoặc secret key entry cụ thể khi getKey/getEntry.

**Không phải mọi type/provider hỗ trợ hai password độc lập**. Với PKCS12 của nhiều provider, hạn chế về entry password và store password có thể làm sự khác biệt so với JKS. Nếu chuyển kho JKS vốn có nhiều key password khác nhau, cần kiểm thử migration, KeyManagerFactory và khả năng unlock entry. Một số bộ công cụ yêu cầu hoặc thực tế dùng cùng password.

Không bỏ qua lỗi bằng cách nhập password rỗng, truyền null bừa bãi hoặc thử mọi password từ các nguồn không được phép.

## 4. JKS: legacy Java-specific, không nên chọn cho hệ thống mới

**JKS (Java KeyStore)** là định dạng kho file proprietary được Java dùng phổ biến trước JDK 9. Nó hỗ trợ những mẫu entry lâu đời như private key + certificate chain và trusted certificates.

Đặc điểm:

- Format **riêng của Java/SUN**, không phải định dạng trao đổi được nhiều thư viện ngoài Java hỗ trợ mặc định.
- Có cơ chế bảo vệ private key và integrity bằng password theo cách legacy; các cơ chế cũ không phù hợp để coi là lựa chọn hiện đại khi triển khai mới.
- **Không hỗ trợ SecretKeyEntry** theo JKS truyền thống của JDK SUN.
- Được giữ lại để tương thích hệ thống, ứng dụng Java cũ và một số truststore hiện hữu.

**Khuyến nghị:** với file keystore mới cho Java 17+, ưu tiên PKCS12 khi đáp ứng nhu cầu. Nếu phải đọc JKS legacy, hãy xác định thật sự loại file, dùng đúng mật khẩu, khóa/chain và lên kế hoạch chuyển đổi thay vì chỉ đổi đuôi file.

### 4.1 JKS khác JCEKS

**JCEKS** là một type Java proprietary khác do **SunJCE** cung cấp. Nó có hỗ trợ secret key entry và cơ chế bảo vệ key khác với JKS. Tuy nhiên các thuật toán bảo vệ legacy của JCEKS không khiến nó trở thành lựa chọn ưu tiên cho hệ thống mới.

| Tiêu chí | JKS | JCEKS |
| --- | --- | --- |
| Provider điển hình | SUN | SunJCE |
| Format | Proprietary Java | Proprietary Java |
| Trusted certificate entry | Có | Có |
| PrivateKeyEntry | Có theo điều kiện provider | Có theo điều kiện provider |
| SecretKeyEntry | Không trong JKS truyền thống | Có |
| Liên thông ngoài Java | Hạn chế | Hạn chế |
| Khuyến nghị cho hệ thống mới | Ưu tiên PKCS12 thay thế | Ưu tiên PKCS12/KMS theo nhu cầu |
| Rủi ro chính | Cơ chế legacy, dễ phụ thuộc type | Cơ chế PBE legacy và phụ thuộc provider |

**Đừng nhận định “JCEKS tốt hơn PKCS12” chỉ vì JCEKS lưu được AES key.** PKCS12 của JDK hiện đại cũng hỗ trợ nhiều loại entry, và tính bảo vệ thực sự phụ thuộc thuật toán PBE/provider/configuration.

### 4.2 JKS compatibility mode là bẫy kiểm thử

Một số phiên bản JDK SUN dùng cơ chế **dual-format compatibility** để nhận diện và đọc cả kho JKS/PKCS12 trong các trường hợp, tùy thuộc tính tương thích và API. Vì thế việc load thành công qua **KeyStore.getInstance("JKS")** không đủ để khẳng định nội dung file thực sự được lưu theo định dạng JKS.

Muốn kiểm tra migration, hãy:

1. Dùng công cụ và cấu hình báo rõ **keystore type/format thực tế**, không suy từ alias hoặc tên file.
2. Xác minh **keytool -list** với loại kho mong muốn trong môi trường đã kiểm soát.
3. Kiểm tra private key/certificate chain bằng TLS hoặc thao tác ký/giải mã đúng chính sách.
4. Tránh dựa vào tính tự nhận diện format chỉ vì hiện tại một JDK chấp nhận file; các provider khác có thể không chấp nhận.

**Chuyển đổi format không phải chỉ đổi tên file.** Phải chạy công cụ nhập/xuất keystore đúng loại và xác nhận kết quả.

## 5. Các loại kho khác từ JDK/provider

### 5.1 PKCS11: cửa ngõ tới token, smartcard hoặc HSM

**PKCS11** trong JCA là KeyStore type liên quan đến PKCS #11 token. Với **SunPKCS11** được cấu hình, ứng dụng có thể truy cập các đối tượng khóa/chứng chỉ trên phần cứng hoặc phần mềm PKCS #11, thông qua provider thay vì một file .p12 thông thường.

~~~text
Java KeyStore API
        |
KeyStore.getInstance("PKCS11")
        |
SunPKCS11 Provider (đã cấu hình module/library)
        |
PKCS#11 native library
        |
Token / smartcard / HSM
        |
Key handle và policy sử dụng khóa
~~~

Mẫu code **minh họa, không chạy độc lập nếu chưa cấu hình thiết bị và provider**:

~~~java
KeyStore tokenStore = KeyStore.getInstance("PKCS11");
tokenStore.load(null, pin); // pin là char[] cấp qua kênh an toàn.
~~~

Điểm rất quan trọng: **load(null, pin) đối với token KHÔNG có nghĩa “tạo kho trống mới” như với file keystore**. Nó thường mở kho/token theo provider và đăng nhập nếu thiết bị cần PIN. Cùng lời gọi KeyStore.load nhưng semantics của backend khác nhau.

Một số khóa trên HSM không xuất được raw key bytes; getEncoded() trả null hoặc API không cho export. Việc ứng dụng có thể gọi sign/decrypt bằng key handle không đồng nghĩa có quyền backup private key thành file. Một số thao tác setEntry/store có thể bị hạn chế hoặc không được hỗ trợ.

Để dùng PKCS11 thực tế cần kiểm tra:

- Provider **SunPKCS11** được cấu hình đúng native library/token slot;
- Driver/vendor PKCS#11 hỗ trợ thuật toán và key usage cần dùng;
- Quy trình PIN, callback, session, số lần thử, khóa token và lifecycle;
- Quyền của ứng dụng, alias/object label, mapping key handle;
- Token offline/reconnect, nhiều tiến trình và concurrency;
- Backup/HA/rotation theo quy trình HSM, không tự tạo file bằng store(...).

**Không hardcode PIN trong source.** Có thể dùng KeyStore.Builder với CallbackHandlerProtection theo thiết kế đăng nhập tương tác hoặc chính sách doanh nghiệp.

### 5.2 Windows-MY và Windows-ROOT: SunMSCAPI

Trên Windows với provider **SunMSCAPI**, Java có thể truy cập kho chứng chỉ và private key do Windows quản lý. Các type thường gặp là:

- **Windows-MY** (alias của kho cá nhân current user, với các biến thể current-user/local-machine do provider hỗ trợ).
- **Windows-ROOT** (kho chứng chỉ root tin cậy, cùng các biến thể tùy cấp quyền).

Mẫu code **minh họa phụ thuộc Windows/SunMSCAPI**, không portable:

~~~java
KeyStore windowsMy = KeyStore.getInstance("Windows-MY");
windowsMy.load(null, null);
~~~

Khi được hỗ trợ, hệ thống lưu trữ và chính sách bảo mật OS mới là nơi quản lý private key. Điều đó có thể giúp tránh xuất private key sang file ứng dụng, nhưng **không có nghĩa mọi key đều non-exportable hoặc mọi thao tác đều được phép**; điều này do Windows key provider, quyền tài khoản và cấu hình key quyết định.

Với service, account chạy ứng dụng khác với account của người dùng tương tác, nên **current-user store có thể không chứa certificate/key bạn đang thấy trong giao diện Windows**. Nếu cần local-machine store, kiểm tra type tương ứng và phân quyền thích hợp theo phiên bản JDK/provider.

### 5.3 macOS KeychainStore: Apple provider

JDK có **Apple provider** cho phép truy cập **macOS Keychain** qua KeyStore type **KeychainStore** trên môi trường hỗ trợ.

~~~java
// Minh họa chỉ khi chạy trên macOS có Apple provider.
KeyStore mac = KeyStore.getInstance("KeychainStore");
mac.load(null, null);
~~~

Không giả định KeychainStore tồn tại trên Windows/Linux hoặc mọi loại entry đều có thể export/store theo cách giống PKCS12. Sự chấp thuận quyền truy cập, tương tác người dùng, code signing, user account và môi trường chạy service có thể ảnh hưởng.

### 5.4 DKS: một domain gồm nhiều keystore

**DKS (Domain KeyStore)** là type do provider SUN cung cấp, dùng để trình bày **nhiều keystore như một logical keystore** theo **DomainLoadStoreParameter** và file/cấu hình domain tương ứng.

Nó không phải định dạng nhị phân PKCS12 được đổi tên. DKS chủ yếu giải quyết vấn đề **kết hợp/ủy quyền nhiều store** trong một giao diện logical; security của từng keystore thành phần vẫn phụ thuộc type, provider, password và quyền nguồn.

DKS có những quy tắc alias, cấu hình và password riêng. Không dùng KeyStore.getInstance("DKS") rồi gọi load(null, password) như cách khởi tạo kho trống PKCS12 và kỳ vọng đã liên kết nhiều file tự động.

### 5.5 CaseExactJKS, third-party types và KMS

**CaseExactJKS** là biến thể JKS của SUN với xử lý alias theo case khác JKS thông thường. Đây là lý do không nên xây quy trình dựa trên việc alias luôn case-sensitive/case-insensitive ở mọi provider.

Một provider bên thứ ba như thư viện cryptography/JCA provider độc lập có thể bổ sung type kho riêng (ví dụ BKS/BCFKS khi provider tương ứng thực sự đã được cài và hỗ trợ). **Tên type không có nghĩa Java 17 mặc định hỗ trợ**. Trước khi sử dụng, phải kiểm tra danh sách provider, thuật toán, giấy phép/chính sách bảo mật và khả năng khôi phục.

**Cloud KMS** không tự động là một KeyStore type. Nhiều dịch vụ KMS có API riêng để generate, wrap, encrypt, sign, rotate và audit, không ánh xạ thành KeyStoreSpi trừ khi đã cài adapter/provider tương ứng. Không tự gọi KeyStore.getInstance("KMS") rồi kỳ vọng mọi cloud KMS đều hỗ trợ.

## 6. Bảng so sánh nhanh

| Loại kho | Backing store điển hình | Đặc tính/ứng dụng | Khả năng liên thông | Điểm cần chú ý |
| --- | --- | --- | --- | --- |
| **PKCS12** | File .p12/.pfx | Private key, cert chain, secret key khi provider hỗ trợ; mặc định JDK 9+ | Khá tốt cho key+cert; chi tiết entry/PBE khác nhau | Password mạnh, provider, thuật toán bảo vệ, backup |
| **JKS** | File .jks (legacy) | PrivateKeyEntry/trusted cert, hệ thống Java cũ | Hạn chế ngoài Java | Không dùng cho thiết kế mới nếu không cần |
| **JCEKS** | File .jceks (legacy) | Có secret key entry | Hạn chế | Phụ thuộc Java/provider, thuật toán PBE cũ |
| **DKS** | Tập keystore cấu hình theo domain | Một logical view từ nhiều store | Phụ thuộc cấu hình Java | Không tự chứa tất cả khóa một cách đơn giản |
| **PKCS11** | Token/smartcard/HSM qua native driver | Key handle, quyền dùng khóa trong thiết bị | Chuẩn API PKCS #11, phụ thuộc vendor | PIN, slot, policy, session, non-exportable keys |
| **Windows-MY/ROOT** | Windows certificate stores | Identity/trust theo account/machine | Gắn với OS | Quyền tài khoản, provider và platform |
| **KeychainStore** | macOS Keychain | Identity/cert/OS keychain | Gắn với macOS | App authorization, keychain login, runtime |
| **Third-party store** | File/token/backend tùy provider | Định dạng/tính năng chuyên biệt | Phụ thuộc nhà cung cấp | Không tự có trên Java chuẩn, phải kiểm chứng |

**Cách chọn thường gặp:**

- File keystore mới, Java 17, cần lưu khóa và cert thông dụng: **PKCS12**.
- Đọc file JKS/JCEKS hiện hữu: giữ khả năng tương thích trong migration nhưng lên kế hoạch chuyển sang **PKCS12** hoặc hệ thống quản lý khóa được phép.
- Private key cần không rời HSM và sử dụng qua PKCS #11: **PKCS11** với provider đúng.
- Dùng chứng chỉ hệ điều hành Windows/macOS: **SunMSCAPI/Apple KeychainStore** khi platform và quyền phù hợp.
- Nhiều kho logic: xem xét **DKS** hoặc cấu trúc ứng dụng có ranh giới quyền rõ ràng.
- Cloud KMS: dùng API/SDK/provider tích hợp chính thức nếu có, không tự giả định là một KeyStore file.

## 7. Java 17+ thực hành: PKCS12, JKS và JCEKS

Ví dụ dưới đây được thiết kế để chạy trong một tiến trình **không cần chứng chỉ tự ký, không cần file ngoài**. Nó:

1. Tạo AES-128 secret key ngẫu nhiên bằng KeyGenerator.
2. Tạo một **PKCS12 trong bộ nhớ**, ghi **SecretKeyEntry** dưới alias demo-aes.
3. Serialize PKCS12 ra byte[] qua ByteArrayOutputStream rồi load lại từ ByteArrayInputStream.
4. Xác nhận entry AES được khôi phục đúng.
5. Thử lưu SecretKeyEntry vào JKS truyền thống để thấy provider SUN từ chối.
6. Thử JCEKS hỗ trợ SecretKeyEntry (chỉ nhằm minh họa khác biệt, **không phải lời khuyên triển khai mới bằng JCEKS**).

**Mật khẩu "demo-only-password" là dữ liệu minh họa công khai trong mã học tập, tuyệt đối không dùng trong môi trường thật.** Trong hệ thống thực, lấy password/PIN từ secret manager hoặc nguồn cấp quyền an toàn, không commit mật khẩu vào repository.

Tệp **KeyStoreTypesDemo.java** (UTF-8):

~~~java
import java.io.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;

public class KeyStoreTypesDemo {
    public static void main(String[] args) throws Exception {
        // Chỉ dùng cho ví dụ công khai. Production: secret manager.
        char[] password = "demo-only-password".toCharArray();

        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey original = generator.generateKey();

        KeyStore pkcs12 = KeyStore.getInstance("PKCS12");
        // Với file store: null InputStream tạo kho rỗng.
        pkcs12.load(null, password);

        KeyStore.PasswordProtection protection =
                new KeyStore.PasswordProtection(password);

        pkcs12.setEntry("demo-aes",
                new KeyStore.SecretKeyEntry(original), protection);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        pkcs12.store(output, password);

        KeyStore restored = KeyStore.getInstance("PKCS12");
        restored.load(new ByteArrayInputStream(output.toByteArray()),
                password);

        KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry)
                restored.getEntry("demo-aes",
                        new KeyStore.PasswordProtection(password));

        System.out.println("PKCS12 alias exists: "
                + restored.containsAlias("demo-aes"));
        System.out.println("PKCS12 AES key restored: "
                + Arrays.equals(original.getEncoded(),
                        entry.getSecretKey().getEncoded()));
        System.out.println("PKCS12 store has bytes: "
                + (output.size() > 0));

        KeyStore jks = KeyStore.getInstance("JKS");
        jks.load(null, password);
        boolean rejects = false;
        try {
            jks.setEntry("demo-aes",
                    new KeyStore.SecretKeyEntry(original),
                    new KeyStore.PasswordProtection(password));
        } catch (KeyStoreException expected) {
            rejects = true;
        }
        System.out.println("JKS rejects SecretKeyEntry: " + rejects);

        KeyStore jceks = KeyStore.getInstance("JCEKS");
        jceks.load(null, password);
        jceks.setEntry("demo-aes",
                new KeyStore.SecretKeyEntry(original),
                new KeyStore.PasswordProtection(password));
        System.out.println("JCEKS supports SecretKeyEntry: "
                + jceks.entryInstanceOf("demo-aes",
                        KeyStore.SecretKeyEntry.class));

        Arrays.fill(password, '\0');
    }
}
~~~

Biên dịch và chạy bằng **JDK 17+ có SUN/SunJCE provider hỗ trợ**:

~~~bash
javac -encoding UTF-8 KeyStoreTypesDemo.java
java KeyStoreTypesDemo
~~~

Stdout dự kiến với các provider tiêu chuẩn của Oracle/OpenJDK:

~~~text
PKCS12 alias exists: true
PKCS12 AES key restored: true
PKCS12 store has bytes: true
JKS rejects SecretKeyEntry: true
JCEKS supports SecretKeyEntry: true
~~~

**Giải thích kết quả:**

- **PKCS12 alias exists: true:** sau serialize/deserialize, alias demo-aes vẫn hiện diện.
- **PKCS12 AES key restored: true:** original và restored secret key có cùng bytes trong demo phần mềm. Khóa không được in ra.
- **PKCS12 store has bytes: true:** store(OutputStream, ...) thực sự serialize kho file vào mảng bytes. Với token/HSM, store(...) không thể được suy luận giống file store.
- **JKS rejects SecretKeyEntry: true:** SUN/JKS truyền thống không có cơ chế lưu SecretKeyEntry như PKCS12/JCEKS.
- **JCEKS supports SecretKeyEntry: true:** type legacy JCEKS lưu secret key theo provider đã thử; không có nghĩa nó là lựa chọn tốt cho ứng dụng mới.

Ví dụ chỉ đánh giá **hành vi của JDK/provider điển hình**. Nếu chạy trên môi trường không có provider/algorithm tương ứng, KeyStore.getInstance hoặc KeyGenerator.getInstance có thể thất bại; đó là yêu cầu kiểm tra compatibility, không phải lý do bỏ xác thực để “vượt qua” lỗi.

**Về bảo mật trong bộ nhớ:** chương trình demo dùng getEncoded() để so sánh nên đã tạo bản sao raw AES bytes. Trong production, tránh export key không cần thiết. Arrays.fill(password, '\0') chỉ xóa mảng char[] này; không đảm bảo xóa mọi bản sao trong JVM/chuỗi literal hoặc vùng native memory. Với JDK, cơ chế cấp password qua char[] thường tốt hơn giữ password trong String lâu dài, nhưng **không mang nghĩa xóa sạch bí mật tuyệt đối**.

### 7.1 Vì sao ví dụ không tạo PrivateKeyEntry?

Một **PrivateKeyEntry** đòi private key và certificate chain phù hợp. Việc tạo chứng chỉ self-signed đúng chuẩn bằng Java standard API đơn giản hơn qua **keytool**, không nên ép chương trình crypto minh họa phụ thuộc vào internal API như sun.security.x509. Phần tiếp theo dùng keytool để tạo một file PKCS12 chứa keypair/certificate dành cho **lab**.

### 7.2 Chạy demo bằng một JDK khác thì sao?

Một provider có thể hỗ trợ PKCS12 nhưng không hỗ trợ lưu SecretKeyEntry; hoặc provider khác có chính sách riêng cho password/key protection. Khi triển khai, kiểm tra:

- Provider nào đang được chọn: ks.getProvider().
- Type có thật sự là PKCS12/JKS/JCEKS theo yêu cầu không.
- Khả năng xử lý **đúng loại entry** ứng dụng muốn dùng.
- Có thể store, load, backup, restore và dùng key trên **mọi JDK/provider trong phạm vi hỗ trợ**.
- Cơ chế PBE/MAC, thuật toán được chính sách cấp phép có tương thích với phần mềm đối tác.

Không thay sự kiểm thử bằng nhận xét “type chuẩn nên chắc chắn hoạt động”.

## 8. keytool: tạo và kiểm tra kho PKCS12

**keytool** là công cụ JDK để quản lý khóa và chứng chỉ. Đối với lab, có thể tạo một keypair và chứng chỉ self-signed trong **demo.p12** như sau:

~~~bash
keytool -genkeypair -alias demo-tls -keyalg RSA -keysize 3072 -validity 30 -dname "CN=localhost, OU=Training, O=Example, C=VN" -keystore demo.p12 -storetype PKCS12
~~~

Lệnh sẽ hỏi password theo cách tương tác thay vì nhúng vào command line. Khóa và certificate tạo ra chỉ dành cho **thực hành**, không đủ điều kiện thay cho CA-issued certificate khi phục vụ HTTPS công khai. Chứng chỉ self-signed và CN=localhost không tự tạo trust hoặc hostname verification đúng cho môi trường production.

Kiểm tra các entry:

~~~bash
keytool -list -v -keystore demo.p12 -storetype PKCS12
~~~

Quan sát **alias, entry type, certificate chain, subject, issuer, validity và fingerprint**. Trước khi đem kho vào TLS server thật, cần có certificate chain đúng danh tính máy chủ và chính sách tin cậy. Không dùng một kết quả keytool -list thành công để suy ra keypair được trusted bởi người dùng khác.

**Không đặt storepass/keypass trực tiếp vào shell command** trong quy trình thật khi có nguy cơ lộ qua history/process list/CI logs. Với CI cần secret injection phù hợp, hạn chế log và ghi quyền truy cập kho.

### 8.1 Nhận diện type, password và alias

Khi gặp file key store do nhóm khác cung cấp:

1. Không đoán type từ đuôi .jks/.p12.
2. Hỏi người cấp **format thực tế**, alias, loại entry, algorithm và yêu cầu password.
3. Chạy keytool -list với -storetype rõ ràng trong môi trường đáng tin.
4. Nếu cần private key, kiểm tra **PrivateKeyEntry**, không chỉ một TrustedCertificateEntry.
5. Kiểm tra certificate chain, hạn dùng, key usage và khả năng client/server TLS đọc đúng alias.
6. Không xuất private key ra text/PEM trừ khi workflow được phép và bảo vệ đúng.

Lưu ý rằng một số JDK có chế độ tự nhận diện JKS/PKCS12 legacy; vì vậy test cần xác thực **format cuối cùng được ghi ra**, không chỉ việc file được load thành công.

## 9. Migration JKS/JCEKS → PKCS12

Migration không phải đổi tên file. Nó là hoạt động có nguy cơ làm mất private key hoặc sai certificate chain nếu làm ẩu.

### 9.1 Quy trình đề xuất

1. **Inventory:** tập hợp nơi dùng kho legacy, type, alias, private key/cert/trust entries, quyền, password, ứng dụng đọc và JDK/provider.
2. **Backup an toàn:** lưu bản gốc với quyền hạn chế, kiểm tra khả năng restore; không đưa password/keystore lên ticket hoặc repository.
3. **Đánh giá tương thích:** chuẩn hóa alias, xác định các entry có thể chuyển; đặc biệt JCEKS secret key và private key non-exportable có thể cần quy trình riêng.
4. **Chọn đích PKCS12:** xác nhận provider, thuật toán PBE/MAC, password và policy của môi trường sẽ dùng.
5. **Import vào file mới**, không overwrite bản gốc.
6. **Kiểm tra kết quả:** so alias, entry type, số lượng certificate, chain, fingerprint, key usage; thực hiện signing/TLS handshake hoặc giải mã thử khi nghiệp vụ cho phép.
7. **Thay cấu hình dịch vụ:** cập nhật keystore path/type/secret reference, xác định thời điểm chuyển đổi và rollback.
8. **Theo dõi:** lỗi load, password, chain, expired certificate, handshake; chỉ xóa bản sao legacy khi migration đã ổn và chính sách retention cho phép.

Với kho JKS legacy (ví dụ tên **legacy.jks**), lệnh minh họa:

~~~bash
keytool -importkeystore -srckeystore legacy.jks -srcstoretype JKS -destkeystore migrated.p12 -deststoretype PKCS12
~~~

Lệnh có thể yêu cầu store password và key password; có thể xảy ra cảnh báo hoặc yêu cầu nhập lại theo provider. Kiểm tra file migrated.p12:

~~~bash
keytool -list -v -keystore migrated.p12 -storetype PKCS12
~~~

Không cho rằng **file import thành công** bảo đảm ứng dụng TLS dùng được. Cần kiểm tra **PrivateKeyEntry**, chain, password, alias cấu hình trong ứng dụng và handshake thật.

### 9.2 Vấn đề về key password và chính sách PKCS12

Một JKS legacy có thể dùng **mỗi private key một password riêng**. Khi sang PKCS12, provider/tool có thể yêu cầu sử dụng password theo cấu trúc khác, đôi khi cùng store password cho nhiều entry. Điều này có thể làm KeyManagerFactory hoặc code getKey(alias, password) thất bại nếu cấu hình giữ nguyên password cũ.

**Không ép về mật khẩu yếu hơn chỉ cho thuận tiện.** Phải điều chỉnh quy trình cấp secret và rollout; đặt password mạnh và kiểm thử các alias riêng biệt.

### 9.3 Với HSM/OS store không được dùng importkeystore như file thông thường

Đối với **non-exportable private key**, có thể **không có khả năng xuất khóa** để chuyển sang PKCS12. Trong tình huống đó, phải:

- Dùng thủ tục backup/migration riêng của HSM hoặc vendor.
- Cấp lại chứng chỉ cho key trên thiết bị nếu phù hợp.
- Sinh cặp khóa mới trong thiết bị mới và chạy quy trình rotation/cert enrollment.
- Tôn trọng quy tắc key usage, audit và quyền người quản trị.

**Không tự coi private key không export được là sự cố để tìm cách bypass**.

## 10. KeyStore trong TLS: keystore khác truststore thế nào?

Cùng một file PKCS12 có thể được dùng cho nhiều vai trò, nhưng ứng dụng TLS phải gán đúng:

~~~text
TLS SERVER                               TLS CLIENT

Server KeyManager                        Client TrustManager
-> private key + server cert chain       -> CA/trust anchors
-> dùng khi server chứng minh            -> xác minh server cert
   quyền giữ private key

Nếu mTLS:
Client KeyManager                        Server TrustManager
-> client key + client cert chain        -> CA/trust anchors
-> chứng minh client identity            -> xác minh client cert
~~~

Trong Java JSSE:

- **KeyManagerFactory** lấy khóa định danh và certificate chain từ KeyStore theo quyền/alias.
- **TrustManagerFactory** xây trust manager từ truststore, phục vụ xác minh chứng chỉ của đối tác.
- **SSLContext** sử dụng KeyManagers và TrustManagers tương ứng theo giao thức TLS.

**Đặt chứng chỉ bất kỳ vào truststore có thể mở rộng phạm vi tin cậy nguy hiểm.** Không import root CA không rõ nguồn gốc hoặc tắt certificate/hostname verification vì “TLS bắt tay không chạy”.

Các property JSSE thường gặp:

~~~text
javax.net.ssl.keyStore
javax.net.ssl.keyStoreType
javax.net.ssl.keyStorePassword

javax.net.ssl.trustStore
javax.net.ssl.trustStoreType
javax.net.ssl.trustStorePassword
~~~

Đây là danh sách property, không phải đoạn Java/bộ lệnh độc lập. Type chỉ định format/provider dự kiến, không thay đổi nội dung chứng chỉ/khóa. Lưu password qua system properties có thể mang rủi ro lộ trong diagnostics/argument listing; ứng dụng production nên ưu tiên cơ chế cung cấp secret có kiểm soát theo framework và môi trường.

### 10.1 Truststore mặc định và cacerts

JDK cung cấp kho trust anchor phổ biến **cacerts**, nhưng **nội dung, format cụ thể, đường dẫn và tập root CA có thể thay đổi theo bản JDK/vendor và cấu hình runtime**. Không mặc định “cacerts luôn là JKS” hoặc “mọi bản Java đều tin cùng CA”.

Dùng công cụ và cấu hình phù hợp để kiểm tra trust anchors khi chẩn đoán lỗi TLS. Không sửa kho hệ thống mặc định trên diện rộng nếu ứng dụng chỉ cần một CA nội bộ; cân nhắc truststore riêng theo dịch vụ để quản trị tin cậy.

### 10.2 PKCS12 có tự bảo đảm trust không?

Không. Một PKCS12 chứa CA certificate chỉ là kho dữ liệu cho tới khi ứng dụng/trust manager dùng các entry ấy làm trust anchors đúng cách. Một private key và certificate trong PKCS12 cũng không tự chứng minh chứng chỉ thuộc về hostname nào; TLS cần xác minh danh tính và chuỗi chứng chỉ theo quy tắc.

Hãy phân biệt **storage**, **key usage**, **certificate validation** và **authorization**.

## 11. Phát hiện provider và capability tại runtime

Nếu bạn cần biết những type KeyStore thực sự hiện có trên JVM:

~~~java
// Đoạn mã minh họa; cần import java.security.Security.
java.util.Set<String> types =
        java.security.Security.getAlgorithms("KeyStore");
System.out.println(types);
~~~

Danh sách thay đổi theo provider đã đăng ký. Lệnh này **không chứng minh một HSM đang hoạt động, có token online hay đã cấp quyền sử dụng khóa**.

Bạn cũng có thể lấy provider cụ thể:

~~~java
// Minh họa, không phải chương trình độc lập.
KeyStore ks = KeyStore.getInstance("PKCS12");
System.out.println(ks.getProvider().getName());
~~~

Với việc cần ép provider đặc thù, JCA hỗ trợ các overload:

~~~java
// Minh họa, có thể ném exception khi provider không hiện diện:
KeyStore ks = KeyStore.getInstance("PKCS12", "SUN");
~~~

**Không ghim "SUN" bừa bãi** nếu ứng dụng có mục tiêu portable hoặc muốn sử dụng provider hardware/OS. Khi triển khai, xác minh provider danh sách đúng ở JDK target, quyền và tính tương thích với key/entry.

### 11.1 Những gì danh sách provider không cho biết

- Token PKCS11 đã đăng nhập được hay chưa.
- Certificate chain có valid hay không.
- Password store/key có đúng không.
- File đã bị thay đổi hoặc permission bị nới lỏng chưa.
- Private key có cho phép ký/decrypt hay export không.
- Format PKCS12 từ công cụ bên ngoài có dùng PBE/MAC được JDK hỗ trợ không.
- Tính ổn định khi chạy service account khác so với người dùng tương tác.

Do đó **capability discovery + thực hiện thao tác trong sandbox kiểm thử + kiểm tra policy** mới tạo được bằng chứng triển khai thực tế.

## 12. Bảo vệ file, password và quyền truy cập

### 12.1 File-based keystore vẫn cần bảo vệ ở lớp OS

Khi lưu file .p12, quyền filesystem thường quan trọng không kém chọn type:

- Giới hạn read access cho account dịch vụ thực sự cần dùng.
- Không commit file private-key keystore vào Git, image container, artifact chung hoặc backup không mã hóa.
- Mã hóa volume/backup và hạn chế quyền giải mã backup.
- Dùng secret manager/secret injection thích hợp cho password, hạn chế hiện lên trong command line/log.
- Có chính sách backup, kiểm tra restore và rotation khi private key hoặc password bị lộ.
- Không mount kho shared read-write cho mọi container/host không cần thiết.
- Không tắt integrity check bằng cách bỏ password tùy tiện chỉ để load thành công.

Một file PKCS12 với mật khẩu yếu vẫn có thể bị dò offline nếu attacker sao chép được file; không coi passphrase vài ký tự là thay thế cho KMS/HSM.

### 12.2 char[] giúp gì và không giúp gì?

Java khuyến nghị nhiều API nhận **char[]** cho password, để ứng dụng có thể làm giảm thời gian sống của buffer bí mật và xóa nội dung bằng Arrays.fill khi không dùng nữa. Tuy nhiên:

- Password có thể đã có bản sao trong biến môi trường, String, shell command, native library hoặc library API.
- Xóa char[] không bảo đảm byte copy trong JVM, GC heap/native memory đều biến mất.
- Không log password, PIN hoặc raw key khi exception.
- Khi cần, dùng callback/secret manager thay vì hardcode secret.

**Trong bài demo** password là hằng công khai phục vụ chạy thử; không được copy sang production.

### 12.3 Rotation và incident response

Đối với private key TLS hoặc key ký:

1. Xác định key ID/alias/certificate chain đang dùng.
2. Cấp key/certificate mới theo chính sách và bảo vệ private key.
3. Triển khai key mới cùng cấu hình đúng loại kho/provider.
4. Cập nhật chứng chỉ/trust của đối tác nếu cần, kiểm tra mTLS, hostname, key usage.
5. Thu hồi certificate/khóa cũ khi cần và theo kế hoạch chuyển đổi.
6. Rà soát bản sao cũ trong backup, CI/log, snapshot và các instance chưa cập nhật.

**Đổi mật khẩu file PKCS12 không giải quyết việc private key đã bị lộ.** Nếu key material bị compromise, phải đánh giá sinh/đăng ký khóa mới, cấp lại chứng chỉ và thu hồi theo chính sách. Tương tự, di chuyển JKS sang PKCS12 không làm khóa từng bị đánh cắp trở nên bí mật trở lại.

## 13. Chẩn đoán lỗi thường gặp

| Hiện tượng | Nguyên nhân cần kiểm tra | Hướng xử lý |
| --- | --- | --- |
| **KeyStoreException: type not found** | Provider không hỗ trợ hoặc chưa cấu hình (PKCS11/Keychain/Windows) | Kiểm tra Security.getAlgorithms, platform, provider registration |
| **IOException khi load file** | Sai password, sai file/format, lỗi integrity, I/O | Xác minh format/path/password và backup; không cố bỏ password để bypass |
| **UnrecoverableKeyException** | Sai entry password, key protection không phù hợp | Đối chiếu key password và provider, phân quyền |
| **Alias tồn tại nhưng getKey trả null** | Alias là certificate entry hoặc không có key accessible | Kiểm tra entry type và key usage |
| **JKS setEntry(SecretKeyEntry) thất bại** | JKS không hỗ trợ secret key entry | Dùng PKCS12/JCEKS theo chính sách, ưu tiên PKCS12 |
| **PKCS12 từ tool khác đọc được cert nhưng không lấy private key** | Khác encoding/PBE, thiếu password đúng, unsupported private-key format | Kiểm tra phiên bản tool/provider và định dạng nhập |
| **KeyManager không chọn được alias** | Chain, thuật toán, EKU, password, certificate, alias selection | Kiểm thử TLS handshake, xem config key manager |
| **Windows-MY không thấy chứng chỉ** | Sai user/machine store hoặc service account | Dùng store phù hợp, kiểm tra access và phiên bản provider |
| **PKCS11 getInstance/load lỗi** | Native library/slot/PIN/permission/provider chưa phù hợp | Chẩn đoán theo vendor, không chuyển key ra file trái policy |
| **Private key getEncoded() trả null** | Khóa non-exportable theo provider | Thao tác bằng handle/thiết bị, không cưỡng bức export |
| **Migration import thành công nhưng TLS fail** | Key password, chain, truststore, type, hostname, alias | Test end-to-end bằng service thật và rollback |
| **getInstance("JKS") mở file PKCS12** | Cơ chế dual-format compatibility ở một số JDK | Xác minh format thực và công cụ migration, không tin type giả định |

**Lưu ý:** loại exception và thông điệp lỗi có thể khác theo provider, phiên bản JDK và tình huống. Khi kiểm tra input không tin cậy, không tiết lộ password, key material hay chi tiết provider nhạy cảm ra response.

## 14. Những hiểu nhầm cần tránh

1. **“KeyStore là một file .jks.”** Không: KeyStore là API, có nhiều type; PKCS11/Windows/Apple có thể không phải file ứng dụng.
2. **“PKCS12 và JKS chỉ khác đuôi file.”** Không: khác định dạng, khả năng entry và cách bảo vệ.
3. **“Java 17 mặc định JKS.”** Không trong cấu hình JDK 9+ tiêu chuẩn: mặc định là PKCS12, nhưng security property có thể được thay đổi.
4. **“PKCS12 tự động an toàn dù password yếu.”** Sai: PBE/MAC/provider và password, ACL, backup vẫn quyết định an toàn.
5. **“JCEKS là giải pháp tốt nhất vì lưu secret key.”** Không: có cơ chế legacy; PKCS12/KMS mới thường phù hợp hơn.
6. **“Mọi PKCS12 đều tương thích 100% giữa Java/OpenSSL/Windows.”** Không: loại entry, bag attributes, PBE và phiên bản tool có thể khác.
7. **“KeyStore.load(null, pin) luôn tạo kho trống.”** Sai: với PKCS11, nó có thể đăng nhập/mở token đã có.
8. **“Nếu KeyStore.getInstance("PKCS11") thành công, HSM chắc đã online.”** Không: cần config, token login và thao tác thực.
9. **“Private key trong HSM phải export được để Java dùng.”** Không: provider có thể sử dụng key handle trực tiếp.
10. **“PasswordProtect có nghĩa mọi entry được bảo vệ bằng cùng chuẩn.”** Không: cách dùng password tùy type/provider và loại entry.
11. **“JKS tự động chứa mọi secret key Java.”** Không: JKS truyền thống không hỗ trợ SecretKeyEntry.
12. **“TrustedCertificateEntry là PrivateKeyEntry.”** Sai: trusted cert không có private key.
13. **“Keystore và truststore là hai định dạng bắt buộc khác nhau.”** Không: có thể đều là PKCS12 nhưng vai trò TLS khác.
14. **“Import CA bất kỳ vào truststore giúp TLS an toàn hơn.”** Không: thêm CA làm mở rộng bề mặt tin cậy.
15. **“Đổi password PKCS12 là đủ sau khi private key rò rỉ.”** Không: phải xử lý compromise của chính key.
16. **“Chuyển JKS sang PKCS12 chỉ cần đổi tên old.jks thành new.p12.”** Sai: cần importkeystore và kiểm tra kết quả.
17. **“Ghim provider SUN luôn là tốt.”** Không: có thể phá portability hoặc bỏ lỡ provider hardware.
18. **“Alias và format luôn phân biệt hoa/thường như nhau.”** Không: quy tắc alias phụ thuộc provider.
19. **“Đã load được keystore nghĩa là cert hợp lệ cho domain.”** Sai: certificate validation và hostname verification là tầng khác.
20. **“Cloud KMS luôn sử dụng KeyStore.getInstance.”** Không: chỉ nếu có adapter/provider tương ứng.

## 15. Tình huống phân tích

### Tình huống A — Dịch vụ chuyển Java 8 sang Java 17

Ứng dụng cũ dùng code KeyStore.getInstance(KeyStore.getDefaultType()) nhưng file đang là JKS. Runtime mới mặc định PKCS12. Một số cấu hình tương thích có thể khiến file vẫn load được, nhưng **không nên dựa vào việc này cho tương lai**.

**Hướng xử lý:** kiểm kê format file, ghi rõ type dự kiến, chuyển sang PKCS12 có kế hoạch, kiểm tra alias/chain/key password và TLS end-to-end.

### Tình huống B — Microservice cần ký JWT bằng private key trên HSM

PrivateKey.getEncoded() trả null. Developer định xuất key ra file PKCS12 để ký dễ hơn.

**Phân tích:** non-exportable có thể là chủ đích. Hãy dùng JCA provider PKCS11 hoặc API KMS ký phù hợp; giữ private key trong thiết bị. Test key usage/algorithm và quyền dịch vụ.

### Tình huống C — Có file truststore.p12 nhưng HTTPS vẫn lỗi

File đã chứa chứng chỉ đối tác nhưng ứng dụng không dùng TrustManagerFactory từ file đó, hoặc hostname/san/chain không đúng.

**Phân tích:** lưu cert không tự cấu hình trust. Kiểm tra SSLContext, trust manager, certificate path, hostname verification và môi trường thực.

### Tình huống D — Container chạy bằng account khác

Developer chạy app trên Windows thấy alias "my-cert" trong Windows-MY. Khi triển khai thành service account, alias biến mất.

**Phân tích:** Windows-MY current-user ràng buộc tài khoản. Kiểm tra store scope, quyền tài khoản và loại kho máy cục bộ theo provider thay vì copy private key sang file trái chính sách.

### Tình huống E — PKCS12 import thành công nhưng không ký được

Alias trùng tên nhưng entry thực tế là TrustedCertificateEntry; private key bị bỏ sót hoặc password không đúng.

**Phân tích:** keytool -list cho biết entry type; cần PrivateKeyEntry, private key khớp certificate chain và quyền sử dụng khóa phù hợp. Không dùng certificate-only entry để giả làm identity.

### Tình huống F — Cần lưu AES secret key cho service

JKS không nhận SecretKeyEntry, JCEKS nhận được. Chọn loại nào?

**Phân tích:** với file store hiện đại và provider phù hợp, nên dùng PKCS12 hoặc secret manager/KMS/HSM theo yêu cầu. JCEKS legacy không phải mặc định tốt nhất chỉ vì chạy được. Test recovery, password, quyền và độ nhạy của DEK.

## 16. Bài tập và tự kiểm tra

**Câu 1.** Java KeyStore khác KeyStore type như thế nào?

**Trả lời:** KeyStore là API engine, còn type như PKCS12, JKS, PKCS11 xác định một triển khai/định dạng qua provider.

**Câu 2.** Java 17 mặc định type nào?

**Trả lời:** Trên JDK 9+ tiêu chuẩn thường là **PKCS12**; có thể bị cấu hình lại qua security property keystore.type. Hãy gọi KeyStore.getDefaultType() để biết runtime đang dùng gì.

**Câu 3.** Tại sao PKCS12 thường được ưu tiên hơn JKS cho file mới?

**Trả lời:** PKCS12 là định dạng chuẩn hơn, liên thông tốt hơn và là mặc định hiện đại; JKS là proprietary legacy với cơ chế bảo vệ cũ.

**Câu 4.** JKS hỗ trợ SecretKeyEntry không?

**Trả lời:** JKS truyền thống của SUN không hỗ trợ, còn JCEKS và PKCS12 của JDK hiện đại có thể hỗ trợ.

**Câu 5.** Keystore và truststore có bắt buộc dùng hai type khác nhau không?

**Trả lời:** Không. Cả hai có thể là PKCS12; khác ở vai trò: identity/private key và trust anchors/certificate validation.

**Câu 6.** getEncoded() trả null khi lấy private key từ HSM có nghĩa gì?

**Trả lời:** Key có thể không được export. Cần thao tác trong provider/HSM bằng key handle, không cưỡng bức xuất raw key.

**Câu 7.** KeyStore.load(null, pin) với PKCS11 có tạo token mới không?

**Trả lời:** Không theo cách hiểu file keystore. Thường là mở và/hoặc đăng nhập token đã được cấu hình.

**Câu 8.** Một service trên Windows thấy Windows-MY thiếu certificate mặc dù GUI của user có, vì sao?

**Trả lời:** Store current-user phụ thuộc account chạy service. Cần kiểm tra scope user/local machine và quyền.

**Câu 9.** Chuyển legacy.jks sang migrated.p12 đúng cách bằng gì?

**Trả lời:** Dùng keytool -importkeystore với -srcstoretype JKS, -deststoretype PKCS12 rồi kiểm tra entries/chain/key password và thử ứng dụng thật.

**Câu 10.** PKCS12 có bảo vệ được bí mật khi người khác có file và password không?

**Trả lời:** Không thể đảm bảo; người có quyền và password phù hợp có thể mở khóa. KMS/HSM và tách quyền cần được cân nhắc khi yêu cầu mạnh hơn.

**Câu 11.** Khi load keystore thành công, đã đủ để TLS tin server chưa?

**Trả lời:** Chưa. Phải có trust manager, chain/path validation, kiểm tra hostname và các điều kiện bảo mật phù hợp.

**Câu 12.** Khi loại kho provider-specific không được hỗ trợ trên JDK target, nên làm gì?

**Trả lời:** Kiểm tra provider registration/cấu hình, driver, quyền và API chính thức; không tự downgrade hoặc xuất private key sang file trái policy.

## 17. Tài liệu tham khảo chính thức

- [Oracle Java SE 17 — KeyStore API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html): API, entry types, load/store, getInstance, default type và provider.
- [Oracle Java SE 17 — Java Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): PKCS12, JKS, JCEKS, DKS, PKCS11.
- [Oracle Java SE 17 — JDK Providers Documentation](https://docs.oracle.com/en/java/javase/17/security/oracle-providers.html): SUN, SunJCE, SunPKCS11, SunMSCAPI, Apple.
- [Oracle Java SE 17 — PKCS#11 Reference Guide](https://docs.oracle.com/en/java/javase/17/security/pkcs11-reference-guide1.html): SunPKCS11, token login, HSM và KeyStore.Builder.
- [Oracle Java SE 17 — keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): generate, list, importkeystore và lưu ý password.
- [Oracle Java SE 17 — Security Developer’s Guide](https://docs.oracle.com/en/java/javase/17/security/): lựa chọn KeyStore type, bảo mật keys/certificates và cách triển khai.

## Kết luận

**PKCS12 là lựa chọn file-based KeyStore thường phù hợp cho Java 17+, còn JKS/JCEKS chủ yếu phục vụ tương thích legacy.** Khi khóa nằm trong HSM hoặc kho của hệ điều hành, **PKCS11, SunMSCAPI và KeychainStore** cho phép Java truy cập bằng cùng API KeyStore nhưng với semantics, khả năng export, quyền và cơ chế lưu khác file .p12. Chọn type theo loại khóa, mục tiêu bảo vệ và provider thực tế; **kiểm thử load, entry type, password, crypto operation, TLS/trust và backup/restore** trước khi chuyển môi trường. Không đánh đồng lưu được khóa với việc đã quản lý khóa an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-store-004">12.4 Tạo kho mới, load, store và kiểm tra đúng kiểu entry</a>

<details>
<summary>Xem chi tiết</summary>

# 12.4 Tạo kho mới, `load`, `store` và kiểm tra đúng kiểu entry

## Mục tiêu bài học

`java.security.KeyStore` là API chuẩn của Java Security Architecture (JCA) dùng để quản lý **khóa mật mã và chứng chỉ** theo các `alias`. Một kho có thể chứa khóa đối xứng (`SecretKey`), khóa riêng kèm chuỗi chứng chỉ (`PrivateKey` + certificate chain), hoặc chứng chỉ công khai được tin cậy (`TrustedCertificateEntry`). Nhưng **ba loại entry không được dùng lẫn**: cùng xuất hiện trong KeyStore không đồng nghĩa cùng phương thức lấy, cùng ý nghĩa tin cậy hay cùng cách bảo vệ.

Sau bài này, bạn sẽ thực hiện được toàn bộ vòng đời cơ bản:

1. Chọn type và tạo đối tượng `KeyStore`.
2. Khởi tạo **kho trống** bằng `load(null, password)`; phân biệt với **đọc kho tồn tại** bằng `load(inputStream, password)`.
3. Ghi `KeyStore.Entry` đúng loại bằng `setEntry` / `setCertificateEntry`; phân biệt **store password** và **entry password/protection**.
4. Dùng `store(outputStream, password)` để **serialize/lưu** rồi dùng một đối tượng mới `load` lại.
5. Kiểm tra đúng loại entry trước khi đọc/cast, tránh nhầm `isKeyEntry()` với `SecretKeyEntry` hoặc `PrivateKeyEntry`.
6. Nhận biết lỗi sai mật khẩu, sai type, chưa load, alias không tồn tại và rủi ro ghi đè file thật.
7. Chạy một chương trình **Java 17+** tự tạo kho PKCS12 ở RAM, thêm AES SecretKey, lưu/đọc lại, kiểm tra các kiểu entry và xác minh key không thay đổi.

> **Phạm vi:** Bài tập thao tác KeyStore, không phải hướng dẫn dựng PKI sản xuất. File `.p12` được bảo vệ bằng mật khẩu **không tự thay thế** quyền truy cập file, KMS/HSM, quản lý danh tính, thu hồi chứng chỉ hoặc chính sách sử dụng khóa.

## 1. KeyStore là gì và không phải là gì?

Một KeyStore là kho logic gồm các entry được định danh bằng **alias**. API `KeyStore` tách việc dùng khóa khỏi định dạng lưu trữ cụ thể và triển khai `Provider` của JCA:

```text
Ứng dụng Java
     |
     | KeyStore.getInstance("PKCS12")
     v
JCA KeyStore API ----> Provider hỗ trợ type PKCS12
     |
     +-- alias "api-aes"       -> SecretKeyEntry
     +-- alias "server-tls"    -> PrivateKeyEntry + cert chain
     +-- alias "partner-root"  -> TrustedCertificateEntry
     |
     +-- load(...) / store(...) <--> byte của kho / file / stream
```

**KeyStore không phải map `String → Object` thông thường**: entry có kiểu mật mã, yêu cầu bảo vệ, phương thức tạo/khôi phục và ràng buộc từ provider. Mặc dù API expose các lớp `Entry`, không phải mọi implementation/type của KeyStore hỗ trợ tất cả loại entry hoặc cách bảo vệ giống nhau.

**Phân biệt ba tầng:**

- **`KeyStore` object:** trạng thái ở bộ nhớ sau khi load; gọi `setEntry` chỉ sửa trạng thái này.
- **Keystore bytes:** kết quả `store`, có thể lưu vào file hoặc gửi tới nơi lưu trữ bảo vệ; `load` xây dựng lại trạng thái từ bytes.
- **Key/certificate cryptographic object:** `SecretKey`, `PrivateKey`, `Certificate`; đây là các tài nguyên có yêu cầu bảo mật và ngữ nghĩa riêng.

`getInstance(...)` **không** tương đương `load(...)`; `setEntry(...)` **không** tự cập nhật file; `getKey(...)` không nói rõ đó là secret hay private key nếu không kiểm tra trước.

## 2. Chọn loại kho: `PKCS12` và những khác biệt triển khai

Trong Java SE 17, **`PKCS12`** là keystore type chuẩn mà các implementation Java SE phải hỗ trợ. `KeyStore.getDefaultType()` lấy giá trị mặc định trong cấu hình bảo mật runtime (`keystore.type`); nhiều JDK hiện đại dùng `pkcs12` làm default, nhưng nếu lưu dữ liệu cần tương thích dài hạn, nên chọn **`"PKCS12"` tường minh**.

| Type | Tính chất | Dùng khi nào? |
| --- | --- | --- |
| `PKCS12` | Chuẩn container phổ biến cho key/certificate; được Java SE yêu cầu hỗ trợ | Lựa chọn thực hành cho key/certificate portable, nhưng vẫn phải kiểm tra tính tương thích của từng entry type/provider. |
| `JKS` | Java KeyStore truyền thống | Thường gặp trong hệ thống legacy; không mặc định dùng cho secret keys và không tự coi là tốt hơn PKCS12. |
| `JCEKS` | Legacy Java Cryptography Extension keystore | Có thể gặp ở hệ thống cũ; không ưu tiên để thiết kế mới chỉ vì trước đây lưu được secret key. |
| Provider-specific / token | Có thể đại diện thiết bị HSM, kho hệ điều hành hoặc dịch vụ | Dùng theo chính sách; không giả định `store(OutputStream)` hoặc `getEncoded()` hoạt động như file PKCS12. |

**Không dùng đuôi file làm bằng chứng type tuyệt đối**: `.p12`, `.pfx`, `.jks` là quy ước; dữ liệu bên trong và provider xử lý mới quyết định. Khi tạo hoặc đọc theo định dạng đã biết, gọi `KeyStore.getInstance("PKCS12")`. Một số overload của JDK có thể probe type từ file, nhưng đó là hành vi API khác với cách bài thực hành chọn type tường minh.

Một điểm quan trọng: **hỗ trợ type `PKCS12` không có nghĩa mọi provider đều hỗ trợ mọi tổ hợp entry type, cipher, password hoặc thuộc tính file**. Demo bên dưới dựa trên JDK 17+ thông dụng có hỗ trợ `SecretKeyEntry` trong PKCS12; khi triển khai cross-provider phải kiểm tra thực tế.

## 3. Ba loại `KeyStore.Entry` bắt buộc phải phân biệt

### 3.1 `SecretKeyEntry` — khóa bí mật đối xứng

Đây là một `SecretKey` như khóa AES hoặc HMAC. Nó có thể dùng cho `Cipher`, `Mac` theo thuật toán của key, **không có public-key certificate chain**.

```java
// Minh họa trong phương thức có xử lý checked exceptions:
KeyStore.SecretKeyEntry item = new KeyStore.SecretKeyEntry(aesKey);
keyStore.setEntry("api-aes", item,
        new KeyStore.PasswordProtection(entryPassword));
```

- `aesKey` là **bí mật**, không log hay chép vào certificate.
- `entryPassword` bảo vệ việc lưu/khôi phục entry theo khả năng type/provider.
- Dùng `getEntry(...)` và kiểm tra `instanceof KeyStore.SecretKeyEntry` hoặc `entryInstanceOf(...)` trước cast.
- Với `SecretKeyEntry`, `getCertificateChain(alias)` thường `null`, vì không có chứng chỉ kèm theo.

### 3.2 `PrivateKeyEntry` — khóa riêng và certificate chain

Là **`PrivateKey` + chuỗi `Certificate[]`** tương ứng. Ví dụ: khóa riêng RSA/ECDSA để ký hoặc xác thực TLS và chứng chỉ công khai của chủ sở hữu.

```java
// Minh họa khi privateKey và certificateChain đã có, được kiểm tra:
KeyStore.PrivateKeyEntry item =
        new KeyStore.PrivateKeyEntry(privateKey, certificateChain);
keyStore.setEntry("server-tls", item,
        new KeyStore.PasswordProtection(entryPassword));
```

**`PrivateKeyEntry` khác `SecretKeyEntry`:** private key thuộc hệ mật khóa công khai, phải có certificate chain theo hợp đồng của KeyStore; `PrivateKeyEntry.getCertificateChain()` dùng để lấy chuỗi đó. **Không tự tạo `PrivateKeyEntry` với chain rỗng/null** chỉ để lưu private key; ví dụ tự cấp chứng chỉ bằng API nội bộ `sun.security.x509` không phù hợp cho hướng dẫn portable Java SE.

Certificate chain đi kèm giúp bên ngoài kiểm tra public key/danh tính theo hệ thống tin cậy, nhưng **sự hiện diện của certificate chain trong KeyStore không có nghĩa chuỗi đã được xác thực/trusted**. Việc validate PKIX, hostname, validity/revocation và key usage là trách nhiệm của TLS/PKI hoặc ứng dụng.

### 3.3 `TrustedCertificateEntry` — một chứng chỉ tin cậy, **không có khóa riêng**

Là một `java.security.cert.Certificate` (thường `X509Certificate`) được kho xem là **trust anchor hoặc chứng chỉ được tin cậy theo chính sách**. Không thể ký hoặc giải mã bằng nó vì **nó không chứa private key**.

```java
// Minh họa với Certificate đã được xác minh qua kênh đáng tin:
keyStore.setCertificateEntry("partner-root", trustedCertificate);
// Tương đương về loại entry:
// keyStore.setEntry("partner-root",
//         new KeyStore.TrustedCertificateEntry(trustedCertificate), null);
```

**Cảnh báo:** chỉ chạy `setCertificateEntry` không làm một chứng chỉ bên ngoài **trở nên đáng tin về mặt nghiệp vụ**; thao tác này đang *cấp quyền tin cậy* theo chính sách của kho/ứng dụng. Phải kiểm tra nguồn, fingerprint, thuộc tính/chủ thể, mục đích và quy trình phê duyệt trước khi import certificate thật.

### 3.4 Bảng chọn API đúng loại

| Câu hỏi / API | `SecretKeyEntry` | `PrivateKeyEntry` | `TrustedCertificateEntry` |
| --- | --- | --- | --- |
| Chứa | `SecretKey` | `PrivateKey` + certificate chain | Một `Certificate` |
| `isKeyEntry(alias)` | `true` | `true` | `false` |
| `isCertificateEntry(alias)` | `false` | `false` | `true` |
| `entryInstanceOf(alias, SecretKeyEntry.class)` | `true` | `false` | `false` |
| `entryInstanceOf(alias, PrivateKeyEntry.class)` | `false` | `true` | `false` |
| `entryInstanceOf(alias, TrustedCertificateEntry.class)` | `false` | `false` | `true` |
| `getKey(alias, password)` | Trả `SecretKey` | Trả `PrivateKey` | `null` |
| `getCertificate(alias)` | Thường `null` | Trả **leaf certificate** đầu chuỗi | Trả chứng chỉ tin cậy |
| `getCertificateChain(alias)` | `null` | Trả certificate chain | `null` |
| Thao tác ứng dụng | AES/HMAC theo key | Ký, giải mã, TLS identity theo key | Xác minh/đánh giá trust theo PKI policy |

**Bẫy phổ biến:** `isKeyEntry(alias) == true` **không xác định** đó là `SecretKeyEntry` hay `PrivateKeyEntry`. `getCertificate(alias) != null` cũng **không chứng minh đó là `TrustedCertificateEntry`**, vì với `PrivateKeyEntry` phương thức này trả leaf certificate. Khi cần **đúng kiểu entry**, sử dụng `entryInstanceOf(...)` hoặc kiểm tra kiểu trên `getEntry(...)`, không suy đoán từ tên alias hoặc `getCertificate`.

## 4. Chu trình tạo kho mới, đọc kho cũ và lưu kho

### 4.1 `getInstance` chỉ chọn một implementation

```java
// Đoạn minh họa:
KeyStore ks = KeyStore.getInstance("PKCS12");
```

`ks` lúc này chưa có nội dung được khởi tạo theo `load`; nhiều thao tác như `size()`, `getKey`, `setEntry` khi chưa load có thể ném `KeyStoreException` (*uninitialized keystore*).

**Không dùng `getInstance("PKCS12")` xong là `setEntry(...)` ngay**.

### 4.2 Tạo **mới**: `load(null, password)`

```java
KeyStore fresh = KeyStore.getInstance("PKCS12");
fresh.load(null, storePassword);  // khởi tạo kho trống
```

`load(null, password)` nói với provider khởi tạo **một keystore rỗng**, không phải "hãy đọc file trống". Có thể dùng password được yêu cầu ở thời điểm khởi tạo, sau đó dùng `store` để serialize theo format. **File đích chưa được tạo bởi `load(null, ...)`.**

Phải phân biệt `load(null, password)` với `load((InputStream) null, password)` ở các overload khác hoặc `load(LoadStoreParameter)`: khi cần thể hiện rõ ý nghĩa, dùng overload `load(InputStream, char[])` như ví dụ trên. Nếu gọi `load(null,...)` lần nữa trên cùng object đã có entries, việc load lại sẽ **reinitialize** và làm mất các thay đổi chưa lưu trong object đó.

### 4.3 Đọc **tồn tại**: `load(inputStream, password)`

```java
try (java.io.InputStream in = java.nio.file.Files.newInputStream(path)) {
    KeyStore restored = KeyStore.getInstance("PKCS12");
    restored.load(in, storePassword);
    // Đọc alias/entry ở đây.
}
```

Khác biệt:

- `inputStream != null`: **parse keystore bytes**, kiểm tra password/integrity theo cơ chế type/provider và tạo entries.
- `inputStream == null`: tạo kho mới rỗng theo API contract (không phát hiện file cũ).
- Mật khẩu store bị sai hoặc dữ liệu lỗi có thể gây `IOException`, với cause như `UnrecoverableKeyException` trong một số trường hợp.
- `load(in, null)` trên một số keystore không thực hiện kiểm tra tính toàn vẹn của store, theo Java API; **không dùng nó như cách bỏ qua lỗi mật khẩu** cho dữ liệu cần tin cậy.
- Sau khi `load(...)` thành công, mới dùng `containsAlias`, `size`, `getEntry`, `entryInstanceOf`.

### 4.4 Ghi entry vào object chưa đủ: phải `store(...)`

```java
ks.setEntry(alias, entry, protection);

try (java.io.OutputStream out =
        java.nio.file.Files.newOutputStream(path)) {
    ks.store(out, storePassword);
}
```

`setEntry` **sửa KeyStore trong RAM**. `store` mới viết bytes xuống output. Nếu chương trình dừng sau `setEntry` mà không `store`, file cũ không có thay đổi. Ngược lại, `store` **không tự thay đổi khóa** thành một cặp khóa khác; nó serialize trạng thái hiện có theo type và provider.

`store(out, password)` bảo vệ tính toàn vẹn của kho theo khả năng/type, nhưng **không phải cam kết file an toàn trước mọi tấn công**. Các cấu hình PKCS12 thường dùng bảo vệ dựa trên mật khẩu; cần đủ entropy cho password, giới hạn quyền truy cập file và quản lý khóa riêng.

**Không viết trực tiếp đè file keystore đang hoạt động nếu cần khôi phục an toàn.** `Files.newOutputStream(path)` theo cách trên có thể truncate/overwrite file. Trong production, tạo file tạm **cùng filesystem** với quyền truy cập hạn chế, `store` vào file tạm, flush/fsync nếu cần tính bền vững, sau đó rename/move atomically nếu hệ thống hỗ trợ, và lưu bản backup/version hợp lệ. `ATOMIC_MOVE` không được mọi filesystem hỗ trợ; phải xử lý `AtomicMoveNotSupportedException` theo chính sách, không tự động ghi đè bừa. Khi có nhiều writer, thêm lock/transaction hoặc external key-management service; `KeyStore` object không phải một database có concurrency control.

## 5. Hai loại mật khẩu/protection khác nhau

| Thành phần | Dùng ở đâu? | Ý nghĩa |
| --- | --- | --- |
| **Store password** | `load(in, storePassword)` / `store(out, storePassword)` | Dùng để kiểm tra/bảo vệ kho theo implementation, bao gồm integrity protection. |
| **Entry password/protection** | `setEntry(alias, entry, new PasswordProtection(...))`, `getEntry(alias, ...)` | Dùng bảo vệ/khôi phục một key entry cụ thể, nếu type/provider hỗ trợ. |
| **Trusted certificate entry** | `setCertificateEntry(...)` hoặc `getEntry(alias, null)` | Không có secret/private key để "mở"; việc tin cậy certificate là chính sách, không phải một mật khẩu cho private key. |

Ví dụ nhỏ:

```java
KeyStore.ProtectionParameter protection =
        new KeyStore.PasswordProtection(entryPassword);
ks.setEntry("api-aes",
        new KeyStore.SecretKeyEntry(aesKey),
        protection);

KeyStore.Entry loaded = ks.getEntry("api-aes",
        new KeyStore.PasswordProtection(entryPassword));
```

**Trong nhiều triển khai PKCS12**, cách dùng một password chung cho store và key entries là cấu hình tương thích thực tế, và một số tool/implementation có ràng buộc về password khác nhau. Demo dùng cùng password để chắc chắn tương tác trên JDK phổ biến; **không kết luận Java API cấm mọi entry password riêng**.

Nên giữ mật khẩu trong `char[]` thay vì `String` bất biến khi có thể và xóa mảng bằng `Arrays.fill` sau khi không cần nữa. Tuy vậy đây **không bảo đảm xóa mọi bản sao bí mật** bên trong JDK, provider hoặc heap; không hard-code password production, không đưa password vào command-line có thể bị lộ qua process list/shell history. Ưu tiên secret manager/HSM/KMS và quyền truy cập phù hợp.

**Sai mật khẩu ở hai điểm cho hành vi khác nhau:**

- Mật khẩu `load()` sai: thường không đọc được keystore, xuất hiện `IOException` / vấn đề integrity hoặc định dạng.
- Mật khẩu của `getEntry()` sai: có thể `UnrecoverableEntryException` / `UnrecoverableKeyException`; dùng store password thành công **không bảo đảm** luôn giải mã được mọi private/secret entry nếu entry dùng protection riêng.

Cần xử lý lỗi rõ ràng; không trả về `null` tự tạo như thể không có alias khi thực chất đã sai password.

## 6. Java 17 chạy độc lập: tạo kho, store/load lại, nhận diện đúng entry

Chương trình sau không cần certificate tự ký hay thư viện nội bộ `sun.*`. Nó dùng `KeyGenerator` để tạo AES-128 secret key, đưa vào `PKCS12 SecretKeyEntry`, **store keystore vào `ByteArrayOutputStream` ở RAM**, rồi khởi tạo một KeyStore mới và `load` byte array đó. Với `store(...)` thành công, thao tác này kiểm tra **cùng encoding/persistence path** mà file I/O sẽ dùng, nhưng không tạo hoặc sửa file trên máy.

Tạo file **`KeyStoreLifecycleDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public final class KeyStoreLifecycleDemo {
    public static void main(String[] args) throws Exception {
        char[] password = "demo-password-123".toCharArray(); // demo only
        String alias = "api-aes";

        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, password); // create a NEW empty keystore
        System.out.println("Type: " + store.getType());
        System.out.println("Empty at start: " + (store.size() == 0));

        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey original = generator.generateKey();
        store.setEntry(alias, new KeyStore.SecretKeyEntry(original),
                new KeyStore.PasswordProtection(password));

        System.out.println("Is key entry: " + store.isKeyEntry(alias));
        System.out.println("Is certificate entry: " +
                store.isCertificateEntry(alias));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        store.store(output, password);
        byte[] encodedStore = output.toByteArray(); // persist in memory only

        KeyStore restored = KeyStore.getInstance("PKCS12");
        restored.load(new ByteArrayInputStream(encodedStore), password);
        System.out.println("Restored entries: " + restored.size());
        System.out.println("Secret-key kind: " + restored.entryInstanceOf(
                alias, KeyStore.SecretKeyEntry.class));
        System.out.println("Private-key kind: " + restored.entryInstanceOf(
                alias, KeyStore.PrivateKeyEntry.class));
        System.out.println("Trusted-cert kind: " + restored.entryInstanceOf(
                alias, KeyStore.TrustedCertificateEntry.class));

        if (!restored.entryInstanceOf(alias, KeyStore.SecretKeyEntry.class)) {
            throw new IllegalStateException("Wrong entry type");
        }
        KeyStore.Entry entry = restored.getEntry(
                alias, new KeyStore.PasswordProtection(password));
        SecretKey recovered = ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        System.out.println("Recovered key matches: " +
                MessageDigest.isEqual(
                        original.getEncoded(), recovered.getEncoded()));
        System.out.println("Missing entry is null: " +
                (restored.getEntry("missing",
                        new KeyStore.PasswordProtection(password)) == null));

        boolean wrongPasswordRejected = false;
        try {
            KeyStore invalid = KeyStore.getInstance("PKCS12");
            invalid.load(new ByteArrayInputStream(encodedStore),
                    "wrong-password".toCharArray());
        } catch (IOException expected) {
            wrongPasswordRejected = true;
        }
        System.out.println("Wrong store password rejected: " +
                wrongPasswordRejected);
        Arrays.fill(password, '\0');
    }
}
```

Chạy trên JDK 17+ hỗ trợ AES và PKCS12 SecretKeyEntry:

```bash
javac --release 17 KeyStoreLifecycleDemo.java
java KeyStoreLifecycleDemo
```

Stdout của chương trình trên JDK hỗ trợ tính năng như môi trường thử nghiệm:

```text
Type: PKCS12
Empty at start: true
Is key entry: true
Is certificate entry: false
Restored entries: 1
Secret-key kind: true
Private-key kind: false
Trusted-cert kind: false
Recovered key matches: true
Missing entry is null: true
Wrong store password rejected: true
```

### 6.1 Giải thích từng phần và tại sao output đáng tin

- **`getInstance("PKCS12")` + `load(null, password)`:** tạo kho mới rỗng, nên `size() == 0`. Nếu quên `load`, các thao tác đọc/ghi entries sẽ báo chưa khởi tạo.
- **`KeyGenerator.getInstance("AES")` + `init(128)`:** tạo một secret key 16 byte ngẫu nhiên. Chúng ta **không in key bytes**; test chỉ so sánh nội bộ.
- **`setEntry("api-aes", new SecretKeyEntry(...), PasswordProtection)`:** thêm key entry. `isKeyEntry == true` vì đây là key; `isCertificateEntry == false` vì không phải certificate-only trust entry.
- **`store(output, password)`:** serialize toàn bộ kho vào byte array ở bộ nhớ. **Không tự ghi một file** và không làm khóa mới xuất hiện nếu không gọi `setEntry`.
- **`restored.load(new ByteArrayInputStream(encodedStore), password)`:** đọc lại bytes như một kho lưu trữ; `size()` vẫn `1`.
- **`entryInstanceOf(alias, SecretKeyEntry.class)`:** `true`. Hai phép kiểm tra class còn lại `false`; đây là ý nghĩa của yêu cầu “kiểm tra đúng kiểu entry”.
- **`getEntry(...)` rồi cast:** thực hiện **sau khi đã xác định loại**. `getSecretKey()` lấy lại secret key, so sánh thông qua `MessageDigest.isEqual` để không cần in khóa.
- **`getEntry("missing", ...)`:** `null` vì alias không tồn tại; không tương đương “đã có alias nhưng nhập password sai”.
- **`load` với mật khẩu sai:** với kho PKCS12 vừa được ghi và bảo vệ, JDK demo ném `IOException`, ta in `true`. Không dựa vào **exact exception message**, vì nó phụ thuộc provider/JDK.
- **`Arrays.fill(password, '\0')`:** hạn chế thời gian mảng password demo còn trong RAM, không bảo đảm xóa được các copy khác; trong production nên có quy trình secret management.

Kết quả random của AES key và byte serialized PKCS12 thay đổi giữa các lần chạy; chương trình chỉ in các trạng thái có ý nghĩa **ổn định**.

### 6.2 Vì sao không dùng `instanceof` rồi cast ngay ở mọi trường hợp?

Có hai cách hợp lệ:

```java
// Cách A: kiểm tra loại trước, hữu ích khi muốn tránh giải mã key sai loại.
if (store.entryInstanceOf("api-aes", KeyStore.SecretKeyEntry.class)) {
    KeyStore.SecretKeyEntry secret = (KeyStore.SecretKeyEntry)
            store.getEntry("api-aes",
                    new KeyStore.PasswordProtection(entryPassword));
}

// Cách B: lấy Entry rồi pattern-match an toàn theo Java 17:
KeyStore.Entry item = store.getEntry("api-aes",
        new KeyStore.PasswordProtection(entryPassword));
if (item instanceof KeyStore.SecretKeyEntry secretEntry) {
    SecretKey aes = secretEntry.getSecretKey();
} else if (item instanceof KeyStore.PrivateKeyEntry privateEntry) {
    // privateEntry.getPrivateKey(), privateEntry.getCertificateChain()
} else if (item instanceof KeyStore.TrustedCertificateEntry trustEntry) {
    // trustEntry.getTrustedCertificate()
} else if (item == null) {
    // Alias không tồn tại.
}
```

Các đoạn trên là **minh họa** dùng biến `store`, `entryPassword` được khởi tạo từ ngữ cảnh chương trình (không phải lớp Java độc lập). Nếu dùng nhánh `getEntry` cho cả ba loại thì **`TrustedCertificateEntry` thường truy xuất với `null` protection**, còn key entries cần `PasswordProtection`. Vì vậy trong code production, hãy phân loại trước rồi gọi `getEntry` với **protection phù hợp**, thay vì luôn đưa password vào cả `TrustedCertificateEntry` và mong mọi provider đều chấp nhận.

Cần kiểm tra `containsAlias(alias)` nếu muốn xử lý riêng chuyện alias không tồn tại, nhưng `containsAlias == true` **không bảo đảm** entry thuộc loại/thuật toán/mục đích được mong đợi. Khi alias thuộc type không mong muốn, fail closed thay vì ép cast.

## 7. Thực hành bổ sung với `keytool`: PrivateKeyEntry và TrustedCertificateEntry

Vì Java SE **không cung cấp API public đơn giản để tự phát hành self-signed X.509 certificate**, bài code chính dùng SecretKeyEntry để chạy thuần Java, không đưa `sun.security.x509`/reflection/third-party library vào một ví dụ portable. Tuy nhiên, JDK có công cụ **`keytool`** giúp tạo và xem đúng các entry type khác.

Các lệnh sau là **bài thực hành riêng**, chạy trong **thư mục lab có quyền ghi**, không dùng tên file sản xuất. Không đưa password vào command line; `keytool` sẽ hỏi tương tác.

### 7.1 Tạo cặp khóa riêng kèm certificate chain

```bash
keytool -genkeypair -alias lab-signer -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 30 -dname "CN=Local Lab Only" -storetype PKCS12 -keystore lab-identity.p12
```

Lệnh này tạo private-key entry với cặp RSA mới và **self-signed certificate** dành cho lab. Xem metadata:

```bash
keytool -list -v -storetype PKCS12 -keystore lab-identity.p12
```

Trong mô hình keytool phổ biến, alias `lab-signer` có:

```text
Entry type: PrivateKeyEntry
Certificate chain length: 1
```

**Không sao chép private key ra file text** hoặc coi certificate tự ký này là chứng chỉ production. PKCS12 private-key entry chứa key và certificate chain, khác secret-key-only entry trong Java demo.

### 7.2 Export public certificate rồi import vào truststore

```bash
keytool -exportcert -rfc -alias lab-signer -file lab-signer.cer -storetype PKCS12 -keystore lab-identity.p12
keytool -importcert -alias lab-signer-trusted -file lab-signer.cer -storetype PKCS12 -keystore lab-trust.p12
keytool -list -v -storetype PKCS12 -keystore lab-trust.p12
```

Sau khi bạn **chủ ý xác minh certificate** và chấp nhận import trong lab, file `lab-trust.p12` có **trustedCertEntry**, chứ không chứa private key của `lab-signer`. Nếu keytool hỏi xác nhận tin cậy, chỉ xác nhận vì bạn vừa tự sinh certificate **trong môi trường lab**; không bỏ kiểm tra fingerprint ở môi trường thực.

`keytool -exportcert` export **chứng chỉ công khai**, không phải private key. Lưu một certificate ở truststore không cho phép ứng dụng ký thay chủ thể certificate đó. Đây là cách trực quan phân biệt một bên **identity keystore** (`PrivateKeyEntry`) với **truststore** (`TrustedCertificateEntry`). Cả hai đều có thể dùng file `.p12`, nhưng **ý nghĩa security khác nhau**.

### 7.3 Thêm secret key bằng `keytool` (tuỳ JDK/provider)

```bash
keytool -genseckey -alias lab-aes -keyalg AES -keysize 128 -storetype PKCS12 -keystore lab-secrets.p12
keytool -list -v -storetype PKCS12 -keystore lab-secrets.p12
```

Với provider hỗ trợ `SecretKeyEntry` cho type này, entry type sẽ hiện secret-key entry. Nếu provider không hỗ trợ, phải xử lý theo thiết kế được phép, không ép chuyển sang JKS rồi tưởng mọi type đều chứa được secret key.

**Lưu ý bảo mật đối với keytool:** dùng các file và passwords dành riêng cho học tập; tool có thể lưu password-derived protected key material trong file, nên bảo vệ quyền file và xóa dữ liệu lab theo chính sách. Output `keytool -list -v` không nên chứa raw secret/private key.

## 8. Phân biệt KeyStore và truststore trong TLS

`KeyStore` là API/type, còn “keystore” và “truststore” trong cấu hình TLS thường là **vai trò ứng dụng**:

- **Identity keystore:** chứa `PrivateKeyEntry` + certificate chain của service/client để **trình chứng chỉ và dùng private key khi xác thực/ký**.
- **Truststore:** chứa `TrustedCertificateEntry` (CA roots hoặc certificate được pin) để **đánh giá chứng chỉ của peer** theo policy.
- **Secret-key keystore:** chứa `SecretKeyEntry` cho AES/HMAC hoặc secret key khác, **không thay certificate identity**.

Một file keystore có thể hỗn hợp nhiều loại entry, nhưng việc đặt tên file `truststore.p12` **không tự** bảo đảm chỉ chứa trusted cert. Đừng chỉ nhìn đuôi `.p12` hoặc alias để quyết định một kho có an toàn dùng làm TLS truststore hay không.

Khi cấu hình TLS, còn cần chọn algorithm policy, `KeyManager`/`TrustManager`, chứng chỉ được phép sử dụng, hostname/identity validation, chain validation và revocation theo hệ thống. `KeyStore` chỉ cung cấp nguồn entry; **không tự chạy validation PKIX** khi bạn thêm một chứng chỉ bất kỳ bằng `setCertificateEntry`.

## 9. Những sai lầm thường gặp trong code và vận hành

| Lỗi/nhận định | Vì sao sai | Cách đúng |
| --- | --- | --- |
| `getInstance("PKCS12")` rồi `setEntry` không gọi `load` | Keystore chưa được initialized | `load(null, pass)` khi tạo mới, hoặc `load(in, pass)` khi đọc cũ. |
| Dùng `load(null, pass)` để “mở file cũ” | Tạo kho mới rỗng; có thể dẫn tới xóa nội dung cũ nếu `store` đè vào cùng file | Đọc file cũ bằng `InputStream`. |
| Gọi `setEntry(...)` rồi thoát | Mới đổi trạng thái RAM, chưa persist | `store` ra output sau khi sửa. |
| Gọi `store` trực tiếp lên file đang dùng không backup | Có thể truncate file nếu lỗi/crash giữa chừng | Temp file + kiểm tra + atomic replace/backup khi hỗ trợ, quyền file chặt. |
| `isKeyEntry(alias)` rồi cast thẳng `SecretKeyEntry` | Cũng `true` cho `PrivateKeyEntry` | `entryInstanceOf(alias, SecretKeyEntry.class)` trước cast. |
| `getCertificate(alias) != null` tức trusted cert | PrivateKeyEntry cũng có leaf certificate | `isCertificateEntry(alias)` hoặc `entryInstanceOf(... TrustedCertificateEntry.class)`. |
| Tin certificate đã `setCertificateEntry` là hợp lệ | App vừa tự **chỉ định trust**, không hề chứng thực từ nguồn ngoài | Xác minh nguồn, fingerprint, PKIX/identity/usage. |
| Store password đúng thì mọi entry đều mở được | Entry password/protection có thể khác | Gọi `getEntry` với đúng `ProtectionParameter`, xử lý lỗi riêng. |
| Alias không tồn tại nên `getEntry` ném lỗi | API trả `null` nếu thiếu alias | Kiểm tra `containsAlias`, `null`, báo missing riêng. |
| Password sai thì coi như không có alias | Thất bại bảo vệ key, không phải bản ghi thiếu | Phân biệt `UnrecoverableEntryException`/lỗi load. |
| Đặt entry mới với alias đã tồn tại | `setEntry` có thể **ghi đè** entry cũ | Check alias, policy replace/migration và backup. |
| Gọi `load(null,pass)` lần 2 để “refresh” | Có thể reinitialize và làm mất state hiện tại | Reload từ file/stream thực hoặc tạo object mới. |
| Cho rằng `getDefaultType()` luôn là PKCS12 | Mặc định runtime cấu hình được | Chọn type tường minh với format được yêu cầu. |
| Lưu private key trong repo/với password nằm trong source | Lộ vật liệu khóa khi repo/log bị lộ | Secret manager/HSM/permissions/rotation. |
| Tin PKCS12 giải quyết mọi backup/concurrency | KeyStore không phải DB transactional hoặc HSM | Thiết kế atomic update, lock/backup, quản lý key lifecycle. |
| `getKey` trả `Key` rồi cast tùy ý thành `PrivateKey` | Alias có thể là `SecretKey` | Kiểm tra loại key/entry trước cast; dùng API đích phù hợp. |

## 10. Lỗi ngoại lệ và cách phát hiện chính xác

| Giai đoạn | Lỗi có thể xuất hiện | Hướng kiểm tra |
| --- | --- | --- |
| `KeyStore.getInstance("PKCS12")` | `KeyStoreException` nếu type/provider không được hỗ trợ | Xem Java version, provider registry, policy. |
| `size()`, `getKey`, `setEntry`, `store` trước `load` | `KeyStoreException` vì keystore chưa initialized | Gọi `load` trước. |
| `load(input, wrongPassword)` | `IOException`, có thể chứa cause `UnrecoverableKeyException` | Không tiếp tục; phân biệt mật khẩu sai với file corrupt/type không đúng. |
| `load(input, password)` | `NoSuchAlgorithmException`, `CertificateException`, `IOException` | Thuật toán integrity/cert parsing hoặc stream bytes không hợp lệ. |
| `setEntry` | `KeyStoreException` khi type/provider không hỗ trợ entry, alias/operation sai | Kiểm tra khả năng provider và thiết kế entry. |
| `getEntry(alias, wrongProtection)` | `UnrecoverableEntryException`, `UnrecoverableKeyException` | Entry còn đó nhưng không được mở bằng protection này. |
| `getEntry("missing", protection)` | **`null`** | Không ném lỗi chỉ vì alias thiếu; không cast nếu `null`. |
| `entryInstanceOf(alias, class)` | `false` nếu alias không có/khác loại; có thể `KeyStoreException` khi uninitialized | Dùng như gate trước cast và trước logic dùng key. |
| Cast `KeyStore.Entry` sai kiểu | `ClassCastException` do code | Phân loại bằng `entryInstanceOf` hoặc `instanceof`. |
| `store(output, password)` | `IOException` / `CertificateException` / `NoSuchAlgorithmException` | Có thể output chưa hoàn chỉnh; không thay file thật nếu lưu chưa thành công. |

**Phân biệt lỗi xác thực/integrity với lỗi parse:** Mật khẩu sai, file bị chỉnh sửa hoặc file không cùng type có thể cùng xuất hiện dưới dạng `IOException` ở `load`. Không hiển thị chi tiết nhạy cảm cho người dùng cuối hoặc tự fallback sang `load(..., null)` để “bỏ qua” kiểm tra integrity.

### 10.1 Hệ quả của `null`

Có ba `null` rất khác nhau:

```text
KeyStore.load(null, password)   -> TẠO MỚI kho trống.
KeyStore.getEntry("missing", p)  -> trả null vì alias KHÔNG CÓ.
KeyStore.getKey("cert-only", p)  -> trả null vì entry không chứa key.
```

Với `getEntry` của trusted certificate, protection thường là `null` vì không có private/secret key cần mở. Nhưng `load(in, null)` lại là quyết định khác: có thể bỏ integrity check. **Đừng áp một ý nghĩa chung cho mọi chữ `null` trong API.**

## 11. Thiết kế lưu file an toàn và quản lý khóa production

### 11.1 Tạo file mới không vô tình đè file cũ

Khi tạo kho, trước hết xác định rõ operation là `CREATE_NEW` hay `OPEN_EXISTING`; tránh logic:

```text
Nếu file có lỗi khi load -> load(null, password) -> store đè file cũ
```

Đó là một **lỗi mất dữ liệu nghiêm trọng**: sai password hoặc file corrupt có thể khiến code tạo kho trống rồi ghi đè mọi key/certificate trước đó.

Thay vào đó:

1. **Create new:** kiểm tra file đích chưa tồn tại, dùng thao tác tạo file **không cho phép replace** (chẳng hạn `CREATE_NEW`) và quyền truy cập hạn chế. Nếu tên đã có, báo lỗi rõ.
2. **Open existing:** nếu file không có hoặc password sai, dừng và điều tra; **không tạo kho mới như fallback**.
3. **Change existing:** tải kho thành công, ghi entry, serialize vào temp file, kiểm tra kết quả và bản backup phù hợp trước khi replace.
4. **Multiple processes:** phối hợp lock/transaction ở lớp ứng dụng; tránh lost updates khi hai process cùng đọc-ghi phiên bản khác nhau.
5. **Backup/recovery:** version store, backup an toàn, thử restore; lưu cả cấu hình type, algorithm/provider cần thiết và chính sách thay key.

`Files.move(..., ATOMIC_MOVE)` là công cụ hữu ích khi được filesystem hỗ trợ, nhưng bản thân nó không bảo đảm tự động việc fsync thư mục, backup, khôi phục hay khóa file; thiết kế cần phù hợp môi trường.

### 11.2 Bảo vệ keystore bytes không đồng nghĩa bảo vệ toàn bộ ứng dụng

- Password nên được cấp qua secure config/secret manager, không nằm trong Git, command-line, stdout, log, exception message hoặc test fixture production.
- Phân quyền file, owner, backup, volume/cloud bucket phù hợp; tránh file `.p12` world-readable ngay cả khi có password.
- Không ghi raw `PrivateKey.getEncoded()` hoặc `SecretKey.getEncoded()` lên log để debug. Provider/HSM có thể trả `null` vì khóa không xuất được; đây là đặc điểm an toàn, không tự coi là lỗi.
- Với HSM/PKCS#11 hoặc provider chuyên dụng, khóa có thể không thể export; KeyStore có thể biểu diễn reference/slot, không phải file PKCS12. Không tự sao chép sang file chỉ để thỏa `store`.
- Quản lý **key usage**: secret AES cho mã hóa không phải RSA private key cho chữ ký; certificate public không cho phép ký.
- Rotation, expiry, revocation và trust policy là quy trình **ngoài** `KeyStore.load/store`; có thể có nhiều alias cho key cũ/mới và thời gian chuyển đổi được kiểm soát.
- Nếu cung cấp truststore cho TLS, kiểm tra danh tính host/chain/usage, không chỉ để một CA certificate vào file.

## 12. Kiểm thử và checklist trước khi dùng

| Test | Điều cần chứng minh |
| --- | --- |
| `getInstance` trước `load` | Chưa init, không thể thao tác entry như kho đã mở. |
| `load(null, password)` | Tạo kho rỗng; `size() == 0`. |
| Thêm AES `SecretKeyEntry` | `isKeyEntry=true`; `isCertificateEntry=false`. |
| `store` rồi tạo object mới `load` | `size()` và alias/entry type giữ đúng. |
| `entryInstanceOf` cho ba type | Đúng một loại kỳ vọng trả `true`. |
| `getEntry(alias, protection)` | Đúng loại được lấy; key bytes so khớp nội bộ; không log secret. |
| Alias không tồn tại | `containsAlias=false`, `getEntry` trả `null`. |
| `load` với store password sai | Bị từ chối trên kho protected; không fallback tạo mới. |
| `getEntry` với key password sai | Bị từ chối theo `UnrecoverableEntryException` hoặc provider-specific lỗi. |
| Alias xung đột | Không âm thầm overwrite; có chính sách migration. |
| PKCS12 khác provider/JDK | Các entry types/algorithms và passwords thực sự interoperable. |
| Certificate/trust | Chứng chỉ có nguồn đáng tin, không gán trust chỉ vì import thành công. |
| Crash giữa `store` | File cũ/backup còn khôi phục được; không tạo kho rỗng thay thế. |

**Checklist code review:**

- [ ] `KeyStore.getInstance("PKCS12")` theo type đã thống nhất; không lệ thuộc `getDefaultType()` bất ngờ.
- [ ] Có đường đi rõ: tạo mới `load(null, pass)` **hoặc** đọc cũ `load(in, pass)`; không fallback destructive.
- [ ] `setEntry` hoặc `setCertificateEntry` đúng loại và bảo vệ key phù hợp.
- [ ] `store` được gọi sau thay đổi; stream được đóng, file update an toàn, không vô tình overwrite bản tốt.
- [ ] Phân biệt `isKeyEntry` với `entryInstanceOf(SecretKeyEntry.class)`/`entryInstanceOf(PrivateKeyEntry.class)`.
- [ ] Không xem `getCertificate != null` là đủ để xác định trusted certificate.
- [ ] Xử lý alias `null`/missing, sai password và sai type riêng; không cast mù.
- [ ] Password/key không nằm trong source/log/CLI; quản lý quyền file và backup.
- [ ] Có kiểm thử reload thực tế và kiểm thử provider/JDK mục tiêu.
- [ ] Truststore/identity keystore được phân biệt về **chính sách**, không chỉ tên file.

## 13. Câu hỏi tự kiểm tra

**1. `getInstance("PKCS12")` có mở hoặc tạo file ngay không?**  
**Không.** Nó chọn implementation, object chưa load. Muốn tạo kho mới cần `load(null, password)` rồi `store(...)` khi cần persist.

**2. `load(null, pass)` có an toàn để thử khi đọc file lỗi không?**  
**Không.** Nó tạo state rỗng, nếu sau đó `store` vào file đang lỗi sẽ **ghi đè và mất nội dung**. Phải điều tra lỗi password/format.

**3. Sau `setEntry`, file đã có key mới chưa?**  
**Chưa.** Phải `store` output rồi mới có bytes lưu trữ mới.

**4. Vì sao `isKeyEntry("x") == true` không đủ để cast thành `SecretKeyEntry`?**  
Vì cả `PrivateKeyEntry` và `SecretKeyEntry` đều là key entries; dùng `entryInstanceOf(..., SecretKeyEntry.class)` để xác nhận.

**5. `getCertificate("x") != null` có chứng minh `TrustedCertificateEntry` không?**  
**Không.** `PrivateKeyEntry` cũng trả leaf certificate qua API này.

**6. Khác biệt giữa `PrivateKeyEntry` và `TrustedCertificateEntry` là gì?**  
`PrivateKeyEntry` chứa **private key + chain** để thực hiện thao tác private-key; `TrustedCertificateEntry` chỉ chứa **public certificate** phục vụ chính sách trust, không có private key.

**7. Store password và key entry password có nhất thiết khác nhau không?**  
**Không.** Java API có hai vai trò khác nhau; khả năng tách password phụ thuộc type/provider. Demo PKCS12 dùng chung một password để tương thích.

**8. Alias không tồn tại và password sai có cùng kết quả không?**  
**Không.** Alias thiếu thường trả `null`; password sai để mở key entry gây lỗi recover, còn password sai khi `load` có thể là `IOException`.

**9. Tại sao demo không sinh self-signed certificate bằng Java public API?**  
Vì JDK không có bộ API public đơn giản dùng cho việc cấp X.509 self-signed. Demo Java thuần tập trung SecretKeyEntry; `keytool` minh họa PrivateKeyEntry và TrustedCertificateEntry.

**10. `PKCS12` có thay thế HSM/KMS và việc xác thực PKI không?**  
**Không.** Nó là định dạng/keystore implementation, không tự cung cấp chính sách enterprise, cách ly phần cứng, kiểm tra chuỗi trust hay quản lý vòng đời.

## Tài liệu chính thức

- [Java SE 17 — `java.security.KeyStore`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html): các entry types, `getInstance`, `load`, `store`, `getEntry`, `setEntry`, `entryInstanceOf`, `isKeyEntry`, `isCertificateEntry` và ngoại lệ.
- [Java SE 17 — `KeyStore.SecretKeyEntry`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.SecretKeyEntry.html): lưu và lấy `SecretKey`.
- [Java SE 17 — `KeyStore.PrivateKeyEntry`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.PrivateKeyEntry.html): private key và certificate chain.
- [Java SE 17 — `KeyStore.TrustedCertificateEntry`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.TrustedCertificateEntry.html): chứng chỉ được tin cậy.
- [Java SE 17 — `KeyStore.PasswordProtection`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.PasswordProtection.html): protection parameter cho key entries.
- [Java SE 17 — `keytool` manual](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): `-genkeypair`, `-genseckey`, `-exportcert`, `-importcert`, `-list -v`, keystore type.
- [Java SE 17 — Java Cryptography Architecture Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html): KeyStore engine và provider SPI.
- [Java SE 17 — Java Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): type `PKCS12` và các tên chuẩn.
- [Java SE 17 — `KeyGenerator`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/KeyGenerator.html): cách sinh AES `SecretKey` trong ví dụ.

**Ghi nhớ:** `getInstance` chọn KeyStore; `load(null, ...)` tạo kho trống; `load(stream, ...)` đọc kho hiện có; `setEntry` sửa RAM; `store` mới lưu; `entryInstanceOf`/`instanceof` mới chứng minh **đúng kiểu entry**. Khả năng giữ khóa an toàn còn phụ thuộc mật khẩu, provider, quyền file, backup, PKI trust và key-management policy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-store-005">12.5 Phân biệt mật khẩu kho với thông tin bảo vệ từng khóa</a>

<details>
<summary>Xem chi tiết</summary>

# 12.5 Phân biệt mật khẩu kho với thông tin bảo vệ từng khóa

Trong Java, `KeyStore` có thể chứa **private key**, **secret key** và **trusted certificate**. Khi dùng một file `.p12`, `.pfx` hoặc `.jks`, lập trình viên thường gặp hai giá trị trông như nhau nhưng có **vai trò khác nhau**:

1. **Mật khẩu kho (keystore password, store password, thường gọi `storepass`):** được cung cấp khi **nạp/lưu kho**, thường dùng để kiểm tra tính toàn vẹn của dữ liệu kho và, tùy định dạng/provider, bảo vệ các phần nội dung của kho.
2. **Thông tin bảo vệ từng khóa (entry/key protection, thường là `keypass`):** được cung cấp khi **đọc/ghi một key entry cụ thể**; nó có thể là một mật khẩu riêng, hoặc một loại `ProtectionParameter` khác như callback/PIN/provider-specific mechanism.

Hai giá trị này **có thể bằng nhau trong một cấu hình**, nhưng **không nên mặc định là một khái niệm**. Có thể mở được keystore, nhìn thấy alias và certificate, nhưng vẫn **không lấy được private/secret key** do thông tin bảo vệ key entry không đúng. Ngược lại, biết key password mà không có đường truy cập hợp lệ vào kho không đồng nghĩa đã mở được file keystore.

Mục tiêu bài: hiểu hai lớp bảo vệ; chọn đúng tham số trong `KeyStore.load/store`, `getKey/setKeyEntry`, `getEntry/setEntry`; phân biệt **JKS, PKCS12, truststore và HSM/PKCS11**; xử lý lỗi đăng nhập TLS/Java; và thiết kế quản lý mật khẩu, xoay vòng, backup một cách an toàn. Các ví dụ dùng Java 17+.

## 1. Mô hình tư duy: chiếc kho và các vật được cất

Hãy tưởng tượng một keystore là một **chiếc hộp có danh mục**. Mật khẩu kho giống một cơ chế kiểm tra/mở hộp; một số vật quan trọng bên trong có thể có **mật khẩu bảo vệ riêng**. Đây chỉ là phép so sánh để nhớ **phạm vi thao tác**; mức bảo vệ mật mã thực tế phụ thuộc vào **keystore type, provider, cách cài đặt và file permissions**.

```text
                    Java KeyStore
           +-----------------------------+
           | store password / protection |
           |  load(...)     store(...)   |
           +-----------------------------+
                     |
           Aliases / Entry metadata
       +-------------+-------------+--------------+
       |             |             |              |
   app-private    data-aes      ca-root       server-cert
   PrivateKey     SecretKey     TrustedCert   chain/public
       |             |             |              |
   entry-level    entry-level   certificate    certificate
   protection     protection    (no private     (public data)
       |             |          key password)
   keypass-A      keypass-B
```

Bên trong cùng một `KeyStore` có thể có nhiều **alias** (tên định danh entry). Nhưng **alias không phải mật khẩu**, không phải cơ chế phân quyền và không tự mang theo tính bí mật. Việc biết tên `app-private` không cấp quyền lấy private key. Mỗi alias trỏ đến một entry thuộc kiểu cụ thể; ứng dụng phải kiểm tra kiểu và dùng đúng protection khi thao tác.

### 1.1. Hai câu hỏi riêng

- **Tôi có thể nạp kho và kiểm tra dữ liệu kho có nguyên vẹn không?** → thường liên quan **store password** trong `KeyStore.load(input, storePassword)`.
- **Tôi có thể khôi phục hoặc sử dụng khóa dưới alias này không?** → liên quan **entry protection** trong `getKey(alias, keyPassword)` hoặc `getEntry(alias, protection)`.

Trả lời “có” cho câu thứ nhất **không tự động** trả lời “có” cho câu thứ hai.

### 1.2. Lưu ý về cơ chế bảo vệ thực tế

Không được giải thích đơn giản rằng mọi keystore “dùng store password để mã hóa toàn bộ file và key password để mã hóa từng entry”. Điều đó **không đúng cho mọi định dạng/provider**.

- **JKS truyền thống:** store password chủ yếu dùng để kiểm tra tính toàn vẹn; các private key entry có cơ chế bảo vệ riêng của format. JKS là định dạng legacy, không nên dùng làm lựa chọn bảo mật mới chỉ vì nó cho phép đặt hai mật khẩu khác nhau.
- **PKCS12:** có cơ chế PBE/MAC bảo vệ nội dung kho/các bag theo cấu hình; chi tiết thuật toán và cách dùng mật khẩu tùy implementation. Nhiều công cụ/ứng dụng yêu cầu hoặc giả định **storepass = keypass** cho tương thích.
- **PKCS11/HSM:** “keystore password” có thể đóng vai trò PIN/đăng nhập token; khóa có thể **không xuất được**, entry protection không nhất thiết là một chuỗi mật khẩu dùng cho `getEncoded()`.
- **Keystore không có password hoặc load với `null`:** hành vi xác minh toàn vẹn/bảo vệ khác nhau theo provider. Không coi khả năng đọc alias hoặc certificate là bằng chứng đã kiểm tra integrity.

## 2. Những entry nào được KeyStore quản lý?

Java `java.security.KeyStore` có các entry quan trọng:

| Entry | Chứa gì? | Có secret/private không? | Thông tin bảo vệ ở mức entry |
| --- | --- | --- | --- |
| `KeyStore.PrivateKeyEntry` | Private key và chuỗi certificate tương ứng | **Có** — private key | Thường được bảo vệ bằng password/PIN/key handle tùy provider |
| `KeyStore.SecretKeyEntry` | Khóa đối xứng, ví dụ AES hoặc HMAC key | **Có** — secret key | Thường có entry protection; hỗ trợ tùy keystore type |
| `KeyStore.TrustedCertificateEntry` | Certificate mà ứng dụng đánh dấu tin cậy | **Không có private/secret key** | Không có “mật khẩu riêng của khóa bí mật” vì không chứa private key |
| Certificate chain gắn với `PrivateKeyEntry` | Certificate public mô tả khóa/danh tính | Certificate là thông tin công khai, **không phải private key** | Có thể đọc certificate qua API mà không khôi phục private key |

Trong Java có các phương thức:

- `keyStore.isKeyEntry(alias)`: entry có chứa **khóa** (private/secret), không kết luận ứng dụng đã mở được khóa.
- `keyStore.isCertificateEntry(alias)`: entry certificate tin cậy độc lập; **khác** certificate chain của `PrivateKeyEntry`.
- `keyStore.getCertificate(alias)`, `getCertificateChain(alias)`: phục vụ phần **certificate/public data**; không phải API lấy private key.
- `keyStore.getKey(alias, keyPassword)`: yêu cầu khôi phục một key theo alias và password đúng của entry/provider.
- `keyStore.getEntry(alias, protection)`: lấy entry dưới một `ProtectionParameter` phù hợp.
- `keyStore.aliases()`: liệt kê alias, không giải mã toàn bộ private key bên trong.

**Truststore** thường là một `KeyStore` được dùng để giữ **trusted certificates** cho quá trình kiểm tra chuỗi chứng chỉ của peer. Nó khác **identity keystore** chứa private key + certificate chain của chính ứng dụng. Sự khác biệt do **vai trò và nội dung**, không phải do class Java riêng; cả hai có thể dùng PKCS12.

## 3. Mật khẩu kho (store password)

### 3.1. API nạp kho: `load`

Ví dụ với file PKCS12:

```java
KeyStore store = KeyStore.getInstance("PKCS12");
char[] storePassword = obtainStorePasswordSecurely();
try (InputStream in = Files.newInputStream(Path.of("service.p12"))) {
    store.load(in, storePassword);
}
```

Đây là **mã minh họa**, chưa phải chương trình hoàn chỉnh: `obtainStorePasswordSecurely()` phải được triển khai bằng secret manager hoặc cơ chế nhập bảo mật; các import nằm trong namespace `java.security`, `java.io` và `java.nio.file`.

Ý nghĩa quan trọng:

- `getInstance("PKCS12")` chọn **keystore type/provider**, chưa nạp file.
- `load(in, storePassword)` đọc cấu trúc keystore, kiểm tra điều kiện load/integrity theo provider và mật khẩu được cung cấp. Nếu sai, thường thất bại với `IOException` hoặc exception phù hợp.
- `storePassword` trong lời gọi `load` **không phải** password mà `getKey` mặc định sẽ dùng cho mọi private/secret key.
- `load(null, password)` thường dùng để **tạo một kho rỗng mới**, không phải nạp file từ disk; trong ví dụ Java ở dưới chúng ta dùng cách này.
- Java API cho phép password `null` ở một số tình huống; theo contract JCA, khi không cung cấp password, **không thực hiện kiểm tra toàn vẹn dựa trên password theo cách thông thường**. Provider cũng có thể có hành vi cụ thể về truy cập nội dung. **Không dùng `null` để né lỗi “sai storepass” trong production.**

### 3.2. API ghi kho: `store`

```java
try (OutputStream out = Files.newOutputStream(Path.of("service-new.p12"))) {
    store.store(out, storePassword);
}
```

Mã trên cũng chỉ minh họa; trong sản phẩm cần ghi file an toàn (tệp tạm, quyền file, atomic move nếu khả dụng), bảo vệ đường dẫn và backup. Tham số `storePassword` được dùng **khi lưu cấu trúc kho** theo format/provider; nó **không phải lệnh đổi mọi keypass**.

**Quan trọng:** việc dùng mật khẩu mới khi lưu có thể thay đổi hình thức bảo vệ phần kho, nhưng bạn không thể giả định mọi entry trong mọi định dạng đều đã được **đổi password bảo vệ** theo đúng mong muốn. Quản lý migration/rotation phải được kiểm tra qua **lưu → nạp → recover từng entry** bằng thông tin mới/cũ tương ứng.

### 3.3. Mật khẩu kho là bảo vệ gì?

Trong API JCA, tham số password của `load` được mô tả chủ yếu là để **kiểm tra tính toàn vẹn** của dữ liệu kho. Một số implementation, đặc biệt PKCS12, còn dùng password trong bảo vệ/encryption những phần dữ liệu theo thiết kế. Vì vậy, mô tả chính xác là:

> **Store password là thông tin bảo vệ/cấp quyền ở phạm vi kho theo định dạng và provider; không được suy ra rằng nó đồng nhất với mọi key entry password hoặc là cơ chế mã hóa toàn bộ nội dung file.**

Nếu bạn mở được danh sách alias bằng một công cụ nhưng không trích được key, đây có thể là hành vi hoàn toàn hợp lý. Ngược lại, không nên dựa vào “alias list hiện ra” làm bằng chứng rằng storepass đã được xác minh hoặc private key đã sẵn sàng.

## 4. Thông tin bảo vệ từng key entry

### 4.1. API cũ `setKeyEntry/getKey`

Khi đặt một khóa vào kho, API có dạng:

```java
// Minh họa: secretKey/keystore đã được tạo đúng kiểu.
keyStore.setKeyEntry("aes-key", secretKey, entryPassword, null);
Key recovered = keyStore.getKey("aes-key", entryPassword);
```

Đoạn mã này là minh họa; `setKeyEntry` hỗ trợ loại key/chain tùy provider. Với **`PrivateKey`**, cần certificate chain phù hợp trong những trường hợp lưu entry chứa private key, còn `SecretKey` không có chuỗi certificate như một TLS private key. Provider/format có thể không nhận `SecretKeyEntry` (ví dụ không áp dụng mẫu này cho JKS legacy chỉ vì class Java có `setKeyEntry`).

Tham số thứ ba `entryPassword` trong `setKeyEntry` có ý nghĩa bảo vệ **key entry cụ thể**. Khi đọc, `getKey(alias, entryPassword)` cần giá trị tương ứng (hoặc cơ chế mà provider yêu cầu).

Nếu alias không tồn tại hoặc không chứa key, kết quả có thể là `null` theo contract của phương thức; nếu mật khẩu để giải mã key không đúng, implementation thường báo `UnrecoverableKeyException`. **Không dùng một vòng lặp thử nhiều mật khẩu tùy ý để “cứu” entry**, dễ làm rộng quyền và tạo dấu hiệu lộ bí mật.

### 4.2. API entry hiện đại `setEntry/getEntry`

Java dùng một abstraction rõ ràng hơn:

```java
// Cần import java.security.KeyStore.
KeyStore.ProtectionParameter protection =
        new KeyStore.PasswordProtection(entryPassword);

keyStore.setEntry("aes-key",
        new KeyStore.SecretKeyEntry(secretKey),
        protection);

KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry)
        keyStore.getEntry("aes-key", protection);
SecretKey restored = entry.getSecretKey();
```

Đây là trích đoạn minh họa, không phải chương trình độc lập. Trong code thật nên kiểm tra alias/type, xử lý exception và gọi `PasswordProtection.destroy()` khi dùng xong.

Các kiểu chính:

- **`KeyStore.PasswordProtection`:** giữ password bảo vệ entry; có `destroy()` để hủy thông tin trong object (theo khả năng implementation).
- **`KeyStore.CallbackHandlerProtection`:** cho provider gọi callback để lấy credential/đăng nhập phù hợp; thường liên quan UI/token hoặc login flow đặc thù.
- **`KeyStore.ProtectionParameter`:** interface chung để provider hỗ trợ hình thức bảo vệ khác nhau.
- **`KeyStore.LoadStoreParameter`:** một loại tham số khác phục vụ nạp/lưu kho, không đồng nhất với `ProtectionParameter` của từng entry.

Không phải mọi provider hỗ trợ mọi loại protection hoặc mọi entry type. API Java là **hợp đồng chung**, implementation quyết định các cơ chế có thể dùng.

### 4.3. Các mật khẩu có thể độc lập

Ví dụ keystore có ba bí mật logic:

```text
Keystore file: app-keys.p12
    store password = STORE_P

Alias "tls-server":
    PrivateKeyEntry
    entry protection = ENTRY_TLS

Alias "finance":
    SecretKeyEntry
    entry protection = ENTRY_FINANCE

Alias "audit":
    SecretKeyEntry
    entry protection = ENTRY_AUDIT
```

Một hệ thống/provider hỗ trợ các password độc lập sẽ yêu cầu:

- `load(file, STORE_P)` để nạp kho;
- `getKey("finance", ENTRY_FINANCE)` để recover finance key;
- `getKey("audit", ENTRY_AUDIT)` để recover audit key.

`STORE_P` không tự mở khóa finance hoặc audit nếu hai entry được bảo vệ khác. `ENTRY_FINANCE` không được quyền mở audit chỉ vì cùng file. **Tuy nhiên**, nếu application đã nắm tất cả password, thì ranh giới phân quyền thực tế phụ thuộc cách application giữ credential, file access và provider — không phải bản thân tên alias.

### 4.4. Cùng password cho store và key có sai không?

**Không phải lỗi cú pháp**, nhưng làm giảm mức phân tách credential nếu thiết kế dựa vào hai lớp quyền độc lập. Trong thực tế **một số provider hoặc công cụ** chỉ hỗ trợ/đảm bảo tương thích khi **storepass = keypass** (đặc biệt một số quy trình PKCS12). Khi đó:

- Hãy ghi rõ rằng ứng dụng đang dùng **cùng một credential cho hai vai trò**, không được mô tả chúng là hai bí mật độc lập.
- Giới hạn truy cập file và secret, dùng workload identity/IAM, nguyên tắc quyền tối thiểu và rotation phù hợp.
- Nếu cần tách quyền mạnh giữa nhiều khóa hoặc nhiều tenant, **đừng mặc định một file PKCS12 cộng nhiều mật khẩu là cơ chế isolation đủ mạnh**. Cân nhắc HSM/KMS/secret manager, phân tách keystore hoặc key handles/provider permission.

## 5. Java 17+: chứng minh hai lớp bảo vệ bằng hai secret key độc lập

Chương trình **KeyStoreProtectionDemo.java** sau tạo **PKCS12 in-memory**, thêm **hai AES key** bảo vệ bằng **hai entry password khác nhau**, rồi lưu cấu trúc kho bằng **store password thứ ba**. Khi load lại:

- Store password đúng cho phép nạp kho.
- Đúng entry password phục hồi đúng key.
- Dùng store password để đọc entry có password khác sẽ thất bại.
- Dùng password của finance để đọc audit sẽ thất bại.
- Dùng store password sai để nạp file sẽ thất bại ở provider JDK của ví dụ.

**Chú ý:** Các password dạng literal trong bài là **giá trị demo cố ý**, không sử dụng trong production; trong phần mềm thật phải đọc từ secret manager/kênh bảo mật, tránh để trong source/historical commits. Mã không in raw password hoặc key material. JDK/provider có thể khác nhau; ví dụ đã được chạy trên JDK hỗ trợ `PKCS12` và Java API nêu dưới đây.

### 5.1. File `KeyStoreProtectionDemo.java`

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class KeyStoreProtectionDemo {
    static SecretKey aesKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        return generator.generateKey();
    }

    public static void main(String[] args) throws Exception {
        // Demo only: không hardcode các password này trong sản phẩm.
        char[] storePassword = "sample-store-phrase-01".toCharArray();
        char[] financePassword = "sample-finance-phrase-02".toCharArray();
        char[] auditPassword = "sample-audit-phrase-03".toCharArray();

        try {
            KeyStore created = KeyStore.getInstance("PKCS12");
            created.load(null, storePassword); // Khởi tạo keystore rỗng.

            SecretKey financeKey = aesKey();
            SecretKey auditKey = aesKey();

            created.setEntry("finance",
                    new KeyStore.SecretKeyEntry(financeKey),
                    new KeyStore.PasswordProtection(financePassword));
            created.setEntry("audit",
                    new KeyStore.SecretKeyEntry(auditKey),
                    new KeyStore.PasswordProtection(auditPassword));

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            created.store(bytes, storePassword);
            byte[] blob = bytes.toByteArray();

            KeyStore loaded = KeyStore.getInstance("PKCS12");
            loaded.load(new ByteArrayInputStream(blob), storePassword);

            boolean financeRestored = Arrays.equals(
                    financeKey.getEncoded(),
                    loaded.getKey("finance", financePassword).getEncoded());
            boolean auditRestored = Arrays.equals(
                    auditKey.getEncoded(),
                    loaded.getKey("audit", auditPassword).getEncoded());

            boolean storeCannotUnlockFinance = false;
            try {
                loaded.getKey("finance", storePassword);
            } catch (UnrecoverableKeyException expected) {
                storeCannotUnlockFinance = true;
            }

            boolean financeCannotUnlockAudit = false;
            try {
                loaded.getKey("audit", financePassword);
            } catch (UnrecoverableKeyException expected) {
                financeCannotUnlockAudit = true;
            }

            boolean wrongStoreRejected = false;
            try {
                KeyStore wrongStore = KeyStore.getInstance("PKCS12");
                wrongStore.load(new ByteArrayInputStream(blob),
                        "wrong-store-phrase".toCharArray());
            } catch (IOException expected) {
                wrongStoreRejected = true;
            }

            System.out.println("store type: " + loaded.getType());
            System.out.println("aliases: " + loaded.size());
            System.out.println("finance key restored: " + financeRestored);
            System.out.println("audit key restored: " + auditRestored);
            System.out.println("store password cannot recover finance key: "
                    + storeCannotUnlockFinance);
            System.out.println("finance password cannot recover audit key: "
                    + financeCannotUnlockAudit);
            System.out.println("wrong store password rejected: "
                    + wrongStoreRejected);

            Arrays.fill(blob, (byte) 0);
        } finally {
            // Chỉ là dọn mảng tạm theo khả năng, không bảo đảm xóa mọi bản sao.
            Arrays.fill(storePassword, '\0');
            Arrays.fill(financePassword, '\0');
            Arrays.fill(auditPassword, '\0');
        }
    }
}
```

Biên dịch và chạy với **Java 17+**:

```bash
javac -encoding UTF-8 KeyStoreProtectionDemo.java
java KeyStoreProtectionDemo
```

Stdout kỳ vọng ở provider PKCS12 phổ biến trên JDK (đã thực nghiệm, không phụ thuộc các byte khóa AES ngẫu nhiên):

```text
store type: PKCS12
aliases: 2
finance key restored: true
audit key restored: true
store password cannot recover finance key: true
finance password cannot recover audit key: true
wrong store password rejected: true
```

### 5.2. Phân tích kết quả và giới hạn

1. **`KeyStore.getInstance("PKCS12")`:** chọn kho chuẩn PKCS12. Không cần file thật vì dữ liệu được lưu vào `ByteArrayOutputStream` và nạp lại qua `ByteArrayInputStream`.
2. **`created.load(null, storePassword)`:** khởi tạo kho mới. Mật khẩu kho được dùng khi `store` và `load` lại.
3. **`setEntry`:** hai entry dùng `KeyStore.SecretKeyEntry` và các `PasswordProtection` **khác nhau**, không phải hai alias chỉ đổi tên.
4. **`store(bytes, storePassword)`:** xuất kho dưới định dạng PKCS12. Không thấy finance/audit raw keys trong stdout.
5. **`load(blob, storePassword)`:** nạp thành công chứng minh store password đúng cho file và provider trong demo, nhưng **chưa chứng minh recover được hai key**.
6. **`getKey(alias, entryPassword)`:** mỗi entry được đọc bằng password riêng. `Arrays.equals` chỉ chứng minh byte khóa vừa đọc tương ứng với byte khóa được tạo; nó không đo độ mạnh cryptographic của password.
7. **`getKey("finance", storePassword)`:** ném `UnrecoverableKeyException` vì finance entry được bảo vệ bằng password riêng.
8. **`getKey("audit", financePassword)`:** thất bại vì finance password không phải audit password.
9. **`load(blob, wrongStorePassword)`:** kho có kiểm tra password/integrity nên bị provider từ chối với `IOException`.
10. **`Arrays.fill`:** chỉ dọn những mảng còn truy cập được, không bảo đảm JVM/provider không còn bản sao. `String` literal chứa password demo vẫn có thể tồn tại trong memory; vì vậy production không dùng cách tạo literal trong source.

**Độ chính xác của kết luận:** minh họa này phản ánh **cấu hình PKCS12/JDK đã kiểm tra**, chứ không chứng minh mọi tool PKCS12 và provider đều hỗ trợ password khác nhau cho các entry. Cần kiểm chứng định dạng và provider đích khi triển khai.

### 5.3. Nếu muốn dùng `getEntry` thay `getKey`

Khi đã có `KeyStore loaded` và password của finance:

```java
// Mã minh họa, tiếp nối loaded/financePassword từ chương trình trên.
KeyStore.PasswordProtection p =
        new KeyStore.PasswordProtection(financePassword);
try {
    KeyStore.Entry entry = loaded.getEntry("finance", p);
    if (entry instanceof KeyStore.SecretKeyEntry secretEntry) {
        SecretKey key = secretEntry.getSecretKey();
        // Chỉ đưa key vào hoạt động mật mã được ủy quyền; không log key.
    }
} finally {
    p.destroy();
}
```

`KeyStore.getEntry` trả một `Entry`, còn kiểu của entry phải được nhận diện. Cũng không có cơ chế “lấy được key vì đã biết alias”. Nếu dùng sai protection, provider có thể ném `UnrecoverableEntryException` hoặc exception tương ứng.

## 6. JKS và PKCS12: đừng đánh đồng tính năng hoặc kỳ vọng mật khẩu

Java hỗ trợ một số keystore type, mỗi loại khác nhau về cách lưu dữ liệu và thuật toán bảo vệ. **`KeyStore.getDefaultType()` thường là `"pkcs12"` trên các JDK hiện đại**, nhưng vẫn có thể được cấu hình lại. Hãy chọn type rõ ràng khi cần tính tương thích và xác định file.

### 6.1. Bảng so sánh thực hành

| Tiêu chí | JKS | PKCS12 |
| --- | --- | --- |
| Mục tiêu lịch sử | Định dạng keystore truyền thống của Java | Định dạng trao đổi khóa/certificate phổ biến giữa hệ sinh thái |
| Dùng cho ứng dụng mới | Nên cân nhắc tránh nếu không có ràng buộc legacy; tính năng/bảo vệ cũ | Thường là lựa chọn file keystore phổ biến hơn; thuật toán/provider vẫn cần kiểm tra |
| Mật khẩu kho | Kiểm tra integrity của file JKS theo cơ chế legacy | Dùng cho cơ chế integrity/PBE theo implementation |
| Bảo vệ private key entry | Có key password riêng trong nhiều luồng JKS/keytool | API Java có thể hỗ trợ per-entry password; CLI/third-party có thể yêu cầu trùng storepass |
| SecretKeyEntry | JKS thông thường **không hỗ trợ lưu secret keys** | PKCS12 Sun provider có thể hỗ trợ secret key entry (đã minh họa ở trên) |
| TrustedCertificateEntry | Có | Có |
| Tính tương thích | Chủ yếu Java và công cụ tương thích JKS | Phổ biến hơn nhưng không phải mọi công cụ xử lý PBE/MAC/per-entry password giống nhau |

Đừng xem file extension `.jks` hoặc `.p12` là bằng chứng tuyệt đối về type: **type do dữ liệu và provider quyết định**. Một file đặt tên `.p12` nhưng không có nội dung đúng format không thể nạp hợp lệ.

### 6.2. `keytool` và hai password

Công cụ `keytool` có các tùy chọn:

- `-storetype`: định dạng keystore (ví dụ `JKS` hoặc `PKCS12`).
- `-keystore`: đường dẫn file.
- `-storepass`: password của keystore; khi không chỉ định, công cụ thường hỏi tương tác.
- `-keypass`: password bảo vệ private key entry cho lệnh/keystore có hỗ trợ.
- `-alias`: định danh entry.
- `-keypasswd`: lệnh đổi password của **key entry** với type/command hỗ trợ, không phải đổi store password.

**Điểm đặc biệt quan trọng:** trên nhiều bản `keytool` của JDK, khi tạo **PKCS12** và cung cấp `-keypass` khác `-storepass`, công cụ có thể **cảnh báo và bỏ qua** password riêng. Một số công cụ PKCS12 khác cũng yêu cầu hai password **bằng nhau**. Tuy nhiên **API Java `KeyStore` không buộc mọi provider/luồng tạo PKCS12 đều phải dùng cùng mật khẩu**, như ví dụ `setEntry` cho thấy. Hai điều này không mâu thuẫn: **giới hạn của công cụ và khả năng của API/provider là hai lớp khác nhau**.

### 6.3. Thử nhập mật khẩu khi tạo JKS bằng `keytool`

Ví dụ thực hành trong **môi trường test riêng**, không đưa private key production vào lệnh học tập. Để công cụ **hỏi mật khẩu tương tác** (thay vì viết mật khẩu trong lịch sử shell), chạy:

```bash
keytool -genkeypair \
  -alias local-test \
  -keyalg RSA -keysize 3072 \
  -validity 30 \
  -dname "CN=Local Test" \
  -storetype JKS \
  -keystore local-demo.jks
```

Trong nhiều shell Windows, dấu `\` dùng để xuống dòng của Bash không áp dụng; có thể gõ **một dòng** tương đương. Khi được hỏi, nhập store password rồi nhập **password của private key entry** khác với store password nếu công cụ/format cho phép. Nếu nhấn Enter ở prompt key password, `keytool` thường có thể dùng cùng store password.

Liệt kê metadata và certificate bằng store password:

```bash
keytool -list -v -storetype JKS -keystore local-demo.jks
```

Sau khi liệt kê được alias/certificate, **chưa chắc** service đã khôi phục private key: bước dùng private key vẫn cần key password đúng hoặc cơ chế bảo vệ của provider.

Nếu muốn thử thay key password cho JKS, xem các tùy chọn tương ứng của `keytool -keypasswd` trên JDK đang chạy; **không giả định `-keypasswd` hỗ trợ PKCS12 tương tự JKS**. Thao tác đổi password thật nên backup, thử nghiệm và có rollback/recovery trước.

### 6.4. Di chuyển từ JKS sang PKCS12

Một quy trình import/export có thể có **storepass nguồn**, **keypass nguồn**, **storepass đích** và **keypass đích**. Chúng khác vai trò; nếu bỏ sót keypass nguồn, công cụ có thể thử storepass nguồn rồi yêu cầu nhập đúng password. Nếu đích là PKCS12, cần xét giới hạn của công cụ nhận và các hệ thống thứ ba về việc dùng cùng password.

**Không coi import thành công là đã bảo đảm ứng dụng khởi động được.** Sau khi di chuyển, kiểm tra:

- Alias và entry type có đúng không?
- Certificate chain gắn với private key có đủ và đúng thứ tự không?
- Ứng dụng load keystore bằng storepass mới được không?
- TLS/crypto runtime recover private key bằng credential mà provider yêu cầu được không?
- Provider/JDK đích có đọc được thuật toán bảo vệ/PBE/MAC trong file PKCS12 không?
- Backup cũ, quyền truy cập và rotation policy có được cập nhật không?

## 7. Trong TLS/SSL Java: chỗ nào dùng store password, chỗ nào dùng key password?

Một ứng dụng HTTPS server thường có **identity keystore** chứa private key và certificate chain; `KeyManagerFactory` cần truy cập private key đó để thực hiện chứng minh quyền sở hữu khóa trong handshake.

Mô hình API phổ biến:

```java
// Minh họa ranh giới; cần thêm imports và cấu hình provider thật.
KeyStore identity = KeyStore.getInstance("PKCS12");
try (InputStream in = Files.newInputStream(Path.of("server.p12"))) {
    identity.load(in, storePassword);
}

KeyManagerFactory kmf = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm());
kmf.init(identity, privateKeyPassword);
```

- `identity.load(..., storePassword)`: mở/kiểm tra **keystore**.
- `kmf.init(identity, privateKeyPassword)`: cung cấp thông tin để **khôi phục private key** mà key manager cần trong các provider thông thường.
- Nếu `privateKeyPassword` khác `storePassword` mà bạn truyền nhầm storePassword vào `kmf.init`, có thể nạp file thành công nhưng khởi tạo KeyManagerFactory thất bại với `UnrecoverableKeyException` hoặc wrapped exception.
- `KeyManagerFactory.init(KeyStore, char[])` nhận một password phục vụ khôi phục các khóa; nếu keystore có **nhiều private key entry với các password độc lập**, không mặc định rằng overload đơn giản này xử lý được mọi entry. Cân nhắc cấu hình chọn alias/provider, `KeyStore.Builder`/`KeyStoreBuilderParameters` hoặc thiết kế kho/key manager phù hợp với thư viện/runtime.
- Không suy ra **mọi** framework TLS và mọi provider đều nhận cùng hai biến cấu hình; phải đọc contract cụ thể của ứng dụng.

### 7.1. Truststore dùng thế nào?

Một TLS client/server thường có truststore gồm **trusted CA certificates** để kiểm tra peer. Cơ chế:

```java
KeyStore trust = KeyStore.getInstance("PKCS12");
try (InputStream in = Files.newInputStream(Path.of("trust.p12"))) {
    trust.load(in, trustStorePassword);
}
TrustManagerFactory tmf = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
tmf.init(trust);
```

Truststore chứa trusted certificates **không cần private-key password để giải mã một private key**, vì thường **không chứa private key**. Việc đọc certificate truststore không đồng nghĩa hoàn tất xác thực peer; trust manager còn phải kiểm tra chain, thời hạn, mục đích, hostname (ở lớp TLS endpoint identification), trạng thái trust/policy tùy môi trường.

**Đừng nhầm:** đặt một keystore password cho truststore không biến certificate trong đó thành bí mật. Certificate thường là dữ liệu public. Store password có thể dùng kiểm tra integrity theo provider; thay đổi tập trusted anchors là thay đổi **trust policy**, không phải đổi private key.

### 7.2. Hệ thống config thường có hai trường

Nhiều framework cung cấp một giá trị tương đương **`key-store-password`** và một giá trị **`key-password`**. Trường đầu để nạp kho; trường thứ hai để khôi phục key entry. Một số framework mặc định key password theo store password khi key password bỏ trống, nhưng **đó là hành vi framework/tool, không phải quy luật universal của `KeyStore` API**.

Nếu nhận lỗi “đã đọc được `.p12` nhưng TLS không khởi động”, hãy kiểm tra **alias, key type, key password và certificate chain**, thay vì chỉ thay store password rồi thử lại.

## 8. Nếu không có password: HSM, PKCS11 và non-exportable key

Trong hệ thống có HSM hoặc token, private key có thể nằm **trong thiết bị** và không bao giờ được xuất ra file dạng byte. `KeyStore` là một interface để tham chiếu/điều khiển key entry, nhưng semantics khác file JKS/PKCS12.

- **Token PIN/credential** có thể được dùng khi đăng nhập provider. Nó không tự có một file password để mở file `.p12`.
- Việc gọi `KeyStore.load(null, pin)` ở một số PKCS11 provider có thể là cách đăng nhập/khởi tạo keystore của token, nhưng phải theo provider docs; không dùng quy tắc này cho mọi format.
- `getKey` có thể trả key handle mà `getEncoded()` trả **`null`**. Không coi `null` đồng nghĩa key không hợp lệ hoặc phải “tải private key ra”.
- Quyền sử dụng khóa có thể do **token ACL, partition, IAM, mechanism flags, PIN policy** hoặc cơ chế khác quyết định. Per-entry password không nhất thiết tồn tại.
- Nếu ứng dụng cần ký số bằng private key trong HSM, hãy dùng `Signature` với key handle/provider tương ứng; tránh ép secret ra ứng dụng bằng một “đường vòng” gây mất lợi ích bảo vệ.

**Bài học:** “Thông tin bảo vệ từng khóa” là khái niệm **rộng hơn keypass**. Trong ví dụ file đơn giản nó là một `char[]`; ở HSM có thể là một chính sách sử dụng khóa mà ứng dụng chỉ có quyền gọi operation, không có quyền xuất raw key.

## 9. Tình huống lỗi và cách phân biệt phạm vi thất bại

### 9.1. Matrix chẩn đoán

| Hiện tượng | Khả năng thường gặp | Nên kiểm tra |
| --- | --- | --- |
| `KeyStore.load(...)` thất bại vì password/integrity | Storepass không đúng, file lỗi/corrupt, provider hoặc format không tương thích | File/type/provider, store password được cấp phát, integrity và backup |
| `load(...)` thành công nhưng `getKey(alias, pass)` ném `UnrecoverableKeyException` | Keypass sai hoặc không khớp bảo vệ entry | Alias, kiểu entry, key protection, provider |
| `getKey(...)` trả `null` | Alias không có hoặc alias không phải key entry theo contract | `containsAlias`, `isKeyEntry`, danh sách alias |
| `getEntry(...)` không recover được | ProtectionParameter sai/không được hỗ trợ, entry corrupt | Entry type, `PasswordProtection`/provider, error cause |
| `keytool -list` hiển thị chứng chỉ nhưng service TLS lỗi | Có thể chưa recover được private key, key alias sai, chain thiếu, key manager config sai | `KeyManagerFactory.init`, alias, type, keypass, chain |
| `getCertificate(alias)` hoạt động nhưng private key thất bại | Certificate public có thể đọc, key entry còn bị bảo vệ riêng | Keypass và quyền sử dụng key |
| Chuyển PKCS12 sang công cụ khác thất bại | Khác biệt provider/PBE/MAC/cách áp dụng mật khẩu | Format compatibility và export profile được hỗ trợ |
| Provider trả `getEncoded() == null` | Key non-exportable hoặc policy provider | Dùng crypto operation qua provider, không ép dump secret |
| KeyManagerFactory dùng một password cho nhiều key entries | Các entry có thể có keypass khác nhau | Builder/provider hoặc phân chia keystore theo cấu hình |

Lưu ý **exception không phải bằng chứng tuyệt đối** về nguyên nhân. Một `IOException` khi `load` cũng có thể do file lỗi, stream hỏng hoặc thuật toán không được hỗ trợ. Không ghi thông điệp chi tiết chứa secret trong log hoặc trả về client. Khi chẩn đoán cần xem root cause từ provider, JDK, type, permissions và điều kiện file.

### 9.2. Chiến lược chẩn đoán có kiểm soát

1. Xác nhận file path, permissions, format/type và provider đang dùng. **Không thử sửa password liên tục** trên file production.
2. Xác minh storepass bằng công cụ/API trong môi trường có thẩm quyền, không in password.
3. Kiểm tra alias tồn tại và kiểu entry trước khi gọi `getKey`. Danh sách alias là metadata, cần bảo vệ nếu chứa thông tin hệ thống nhạy cảm.
4. Chỉ dùng **entry password đúng phạm vi** đã được provision. Đừng thử mọi mật khẩu khác nhau theo danh sách; quy trình cứu hộ phải có phê duyệt.
5. Kiểm tra private key certificate chain (TLS) hoặc thuật toán secret key tương ứng (ứng dụng crypto).
6. Nếu provider/format khác nhau, kiểm chứng trên bản **backup cô lập**, không chỉnh file đang hoạt động trực tiếp.
7. Sau thay đổi config, chạy test thật: load keystore, recover key theo nhu cầu, ký/giải mã hoặc khởi tạo TLS; **không dừng ở `keytool -list`**.

## 10. Quản lý bí mật: password kho và password từng key

### 10.1. Nên bảo vệ hai loại password như thế nào?

Cả store password và entry password đều là **secret** khi chúng được dùng trong hệ thống thật:

- Lưu trong **secret manager/KMS/HSM** hoặc kênh cấu hình bí mật có kiểm soát; không commit vào repo, cấu hình public, ví dụ Markdown, issue tracker, chat hoặc CI log.
- Dùng **workload identity/least privilege**: service chỉ nên đọc secret nó thực sự cần.
- Tránh `-storepass actualSecret` hoặc `-keypass actualSecret` trong command line của production vì process list, shell history và log có thể ghi lại đối số. Với `keytool`, prefer prompt hoặc cơ chế secret loading phù hợp và hạn chế truy cập.
- Bảo vệ **file keystore** bằng file permission, storage encryption, backup protection và access control. Một mật khẩu mạnh không thay thế việc giữ private key material khỏi người trái phép.
- Định kỳ đánh giá password strength, KDF/PBE format support, JDK/provider version và chính sách rotation. Đừng đặt mật khẩu demo ngắn hay mặc định `"changeit"` cho tài sản nhạy cảm.
- Không log `String.valueOf(passwordCharArray)`, raw byte key, certificate private components hay stack trace chứa thông tin cấu hình bí mật.

### 10.2. Thay store password khác thay key password

Có hai thao tác độc lập về **ý định quản trị**:

| Thay đổi | Thao tác được nhắm tới | Phải kiểm chứng sau đó |
| --- | --- | --- |
| **Đổi store password** | Bảo vệ cấu trúc kho ở mức file/keystore | Có load/store bằng password mới được không? File cũ/backup có còn chứa password cũ không? |
| **Đổi entry password của một key** | Thay protection cho alias/key cụ thể | `getKey` bằng password mới có thành công? Bằng password cũ có còn được provider chấp nhận không? |
| **Rotate private/secret key material** | Tạo **khóa mật mã mới** chứ không chỉ đổi password bảo vệ | Ứng dụng có dùng khóa mới? Chứng chỉ, wrapped data, token, handshake và dữ liệu cũ có được migrate không? |
| **Chuyển keystore type/provider** | Đổi cơ chế lưu trữ, mật mã và interoperability | Toàn bộ alias, key, certificate chain, PBE/MAC, API/TLS đều hoạt động đúng? |

**Đổi password bảo vệ không tự biến khóa AES/RSA cũ thành khóa mới.** Mức độ ảnh hưởng khi lộ **mật khẩu** và khi lộ **private key raw bytes** khác nhau. Nếu key bytes bị lộ, chỉ thay password kho/keypass là **không đủ**: cần rotate vật liệu khóa và xử lý dữ liệu/chữ ký/certificate bị ảnh hưởng theo chính sách.

### 10.3. Sao lưu và khả năng khôi phục

Khi mất password bảo vệ khóa, có thể **không thể recover key**, kể cả còn file keystore. Nếu mất private key, ciphertext cũ hoặc danh tính TLS có thể không còn dùng được. Cần:

- Sao lưu file keystore an toàn và **backup credential/key metadata** theo chính sách tách quyền.
- Ghi phiên bản file, keystore type, provider, alias, purpose, thuật toán, cert chain và chính sách rotation.
- Kiểm tra khôi phục định kỳ trong **môi trường cô lập** bằng load + operation thật, không chỉ kiểm tra file tồn tại.
- Với PKCS12 tạo bởi tool/provider đặc thù, lưu thông tin **tương thích phiên bản/phương thức bảo vệ** để tránh không mở được sau nâng cấp hoặc chuyển vendor.
- Khi rotate, giữ bản cũ cho thời gian cần thiết (ví dụ decrypt dữ liệu lịch sử), giới hạn phạm vi sử dụng và có kế hoạch hủy an toàn.

### 10.4. Mật khẩu khác nhau chưa chắc có phân quyền mạnh

Nếu cả storepass và mọi entrypass đều được inject vào **cùng một process** có quyền đọc file keystore, process bị compromise có thể lấy được mọi key mà nó được quyền recover. Alias và password riêng **không** tạo ranh giới giữa các service/tenant nếu runtime đều giữ cùng quyền và tất cả credential.

Muốn tách biệt mạnh hơn, hãy xem xét:

- Keystore/secret riêng theo service, workload hoặc tenant.
- HSM/KMS có IAM và audit ở mức thao tác/khoá.
- Khóa **non-exportable** và API chỉ cho phép ký/giải mã theo policy.
- Không để service có quyền một thao tác tự tải toàn bộ file, tất cả keypass và raw private keys.

## 11. Ba kịch bản áp dụng trong dự án Java

### 11.1. HTTPS server: kho nạp được nhưng handshake không chạy

Giả sử `server.p12` chứa alias `https-key` là `PrivateKeyEntry`. App có biến `STORE_PASS` và `KEY_PASS`. Nếu `STORE_PASS` đúng nhưng `KEY_PASS` sai:

- `KeyStore.load` có thể thành công;
- `KeyManagerFactory.init` hoặc phép dùng private key có thể thất bại;
- Certificate public có thể vẫn liệt kê được.

Hướng sửa: xác định type/provider, alias và entry protection; cập nhật đúng credential của key entry hoặc cấu hình provider, không đổi certificate ngẫu nhiên và không bỏ password.

### 11.2. Hai khóa AES trong cùng PKCS12

Cấu hình có `finance` và `audit`; finance service không nên có `auditPassword`. Demo ở trên cho thấy hai entry có thể được bảo vệ bằng các password riêng dưới provider hỗ trợ. Tuy vậy, nếu cả file và hai secret đều nằm trong cùng tiến trình, phạm vi bí mật không thực sự được tách. Với yêu cầu tuân thủ/tenant separation, cân nhắc thiết kế khác thay vì chỉ đổi alias/keypass.

### 11.3. Truststore chứa CA, không chứa TLS private key

`trust.p12` có trusted certificates, được nạp bằng truststore password để kiểm tra cấu trúc/trust anchors. Khi khởi tạo `TrustManagerFactory` từ truststore, **không có private-key password cần cung cấp** cho các `TrustedCertificateEntry`. Nếu mTLS client còn phải gửi certificate của chính nó, đó là **identity keystore** và cần private key tương ứng, không thể chỉ dùng truststore.

## 12. Lỗi thường gặp và cách sửa

| Nhầm lẫn | Vì sao sai | Cách hiểu đúng |
| --- | --- | --- |
| “`load(storepass)` thành công nghĩa là đã giải mã hết private keys.” | Nạp kho và recover key là hai thao tác khác | Dùng `getKey/getEntry` hoặc khởi tạo crypto/TLS để kiểm tra entry |
| “`storepass` luôn bằng `keypass`.” | API có thể cho password riêng, tùy provider/tool | Ghi rõ policy thực tế, không đồng nhất về khái niệm |
| “`keypass` có thể mở cả file keystore.” | Keypass bảo vệ một entry; không phải credential của kho | Cung cấp storepass cho `load` và đúng entry protection cho key |
| “Đổi tên alias là đổi key password.” | Alias chỉ là tên định danh | Dùng API/tool thay protection thực tế |
| “Certificate có private-key password.” | Public certificate không chứa private key | Phân biệt certificate/truststore với `PrivateKeyEntry` |
| “`keytool -list` chứng minh TLS private key hoạt động.” | Chỉ cho biết một phần cấu trúc/certificate | Kiểm tra key recovery và TLS handshake phù hợp |
| “PKCS12 từ mọi tool đều hỗ trợ keypass riêng.” | Interoperability/tool limitations | Test provider và công cụ đích, xét yêu cầu storepass = keypass |
| “JKS có hai password nên bảo mật hiện đại hơn PKCS12.” | JKS legacy có cơ chế bảo vệ khác | Chọn format theo bảo mật và tương thích thực tế |
| “`getEncoded() == null` nghĩa key hỏng.” | Non-exportable key có thể trả null | Dùng provider/key handle thao tác trực tiếp |
| “Đổi storepass là rotate RSA key.” | Vẫn là cùng private/public key material | Rotate key pair, cert và dữ liệu liên quan khi cần |
| “`String` password trong source là an toàn vì không in ra.” | Source/heap/history/decompiler có thể lộ secret | Secret manager, char[] khi phù hợp, hạn chế lifetime |
| “Dùng storepass null để bỏ lỗi integrity.” | Có thể bỏ xác thực toàn vẹn hoặc có hành vi khác | Giải quyết password/provider/corruption đúng cách |
| “Có nhiều entrypass là đã cô lập mọi service.” | Còn phụ thuộc quyền đọc file và secret injection | Least privilege, tách workload/KMS/HSM khi cần |
| “Chỉ backup `.p12` là đủ.” | Mất password/provider info có thể không recover | Backup an toàn credential, version, type, alias và test restore |

## 13. Checklist kiểm tra trước khi triển khai

1. **Đúng file/type/provider:** biết rõ JKS hay PKCS12 hay PKCS11? Đừng suy ra từ extension.
2. **Store password:** được cấp từ secret manager, đúng cho `load/store`, không log hoặc hardcode?
3. **Entry type:** alias cần dùng là `PrivateKeyEntry`, `SecretKeyEntry` hay `TrustedCertificateEntry`? Có chuỗi certificate khi cần TLS không?
4. **Key protection:** entry keypass/`ProtectionParameter` đã được xác định, provider hỗ trợ và đúng trong thao tác recover?
5. **Không đồng nhất khái niệm:** nếu storepass = keypass, có ghi rõ đây là quyết định cấu hình/tương thích, không gọi là hai bí mật độc lập?
6. **PKCS12 interoperability:** Java API, `keytool`, ứng dụng khác và version JDK có đồng ý cách bảo vệ entry không?
7. **TLS integration:** `KeyStore.load` và `KeyManagerFactory.init` nhận đúng password theo vai trò; `TrustManagerFactory` dùng đúng trust anchors?
8. **File/secret access:** IAM, file permissions, backup encryption và secret log/heap dump policy có hợp lý?
9. **Rotation:** tách đổi storepass, đổi entrypass và **rotate key material**; mỗi thao tác có test/rollback/backup?
10. **HSM/non-exportable:** đã dùng API provider phù hợp thay vì ép export key chưa?
11. **Negative tests:** thử sai storepass, sai keypass, alias sai, type sai, file corrupt, provider khác trong môi trường test?
12. **Recovery:** có thể khôi phục backup và thực hiện crypto/TLS operation thật với credentials được bảo quản hợp lệ?

## 14. Bài tập tự kiểm tra

**Câu 1.** `KeyStore.load(input, storePassword)` đã thành công. Có chắc `getKey("server", storePassword)` cũng thành công không?  
**Đáp án:** **Không**. Entry `server` có thể có keypass khác, không phải key entry, hoặc provider yêu cầu protection khác.

**Câu 2.** Hai AES keys `finance` và `audit` trong một PKCS12 có cùng storepass nhưng khác entrypass. Người chỉ biết finance password có thể luôn lấy audit key không?  
**Đáp án:** Không theo mô hình/provider bảo vệ entry riêng đã nêu. Nhưng khả năng cô lập thực tế còn phụ thuộc file access, quyền/provider và việc ai đang nắm các credentials.

**Câu 3.** `keytool -list` cho thấy certificate của server. Đó có phải bằng chứng private key đã recover được không?  
**Đáp án:** **Không.** Cần thử recover private key hoặc khởi tạo TLS key manager/handshake với đúng entry protection.

**Câu 4.** Một `TrustedCertificateEntry` có cần password riêng để đọc private key không?  
**Đáp án:** **Không**, bởi entry này không chứa private key. Truststore password phục vụ bảo vệ kho theo provider.

**Câu 5.** Tại sao có thể chạy code Java `setEntry(..., PasswordProtection(entryPass))` cho PKCS12 nhưng `keytool` lại cảnh báo khác storepass?  
**Đáp án:** Java `KeyStore` là API trừu tượng; implementation và từng luồng tool có giới hạn riêng. Không suy ra mọi tool hỗ trợ cùng cách per-entry protection.

**Câu 6.** Đổi store password nhưng không thay private key material. Certificate công khai phải đổi vì keypair đã khác chưa?  
**Đáp án:** **Không tự động.** Khóa mật mã chưa đổi; chỉ cơ chế bảo vệ kho được thay theo định dạng/provider. Nếu bị lộ private key thật, cần rotate keypair và certificate theo quy trình.

**Câu 7.** `KeyStore.getKey(alias, password)` trả `null`. Có nên thử thêm 1.000 password khác không?  
**Đáp án:** **Không.** Trước hết kiểm tra alias có tồn tại, `isKeyEntry` hay không; xử lý theo workflow được ủy quyền.

**Câu 8.** Trong HSM, `PrivateKey.getEncoded()` trả `null`. Điều đó có chứng minh khóa không hợp lệ không?  
**Đáp án:** **Không.** Khóa non-exportable có thể vẫn thực hiện `Signature`/`Cipher` qua provider/key handle được phép.

**Câu 9.** Một TLS server khai báo đúng storepass, nhưng `KeyManagerFactory.init` dùng nhầm storepass thay cho keypass. Triệu chứng?  
**Đáp án:** Có thể nạp kho được nhưng không khôi phục private key, khiến key manager/TLS không hoạt động. Phải sửa credential entry hoặc provider configuration.

**Câu 10.** Nếu storepass và keypass cùng một giá trị vì hệ thống yêu cầu tương thích, cần làm gì để giảm rủi ro?  
**Đáp án:** Quản lý credential đó như bí mật có quyền truy cập cao, giới hạn IAM/file access, rotate và backup an toàn; không giả vờ có hai ranh giới độc lập.

### Tài liệu tham khảo

- [Java SE 21 — KeyStore](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyStore.html): `load`, `store`, `getKey`, `setKeyEntry`, `getEntry`, `setEntry` và phạm vi password.
- [Java SE 21 — KeyStore.PasswordProtection](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyStore.PasswordProtection.html): bảo vệ entry bằng password và `destroy()`.
- [Java SE 21 — KeyStore.Builder](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyStore.Builder.html): chọn provider/keystore, lấy protection parameter.
- [Java SE 21 — Java Cryptography Architecture Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html): KeyStore, entry type, integrity và các API bảo mật.
- [Java SE 21 — keytool](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html): `-storepass`, `-keypass`, key migration và giới hạn công cụ.

### Ghi nhớ

**Store password phục vụ cấp độ kho; entry/key protection phục vụ một khóa cụ thể.** Cùng chuỗi password có thể được tái sử dụng trong một cấu hình, nhưng không biến hai vai trò thành một. Muốn chẩn đoán chính xác, hãy tách **`KeyStore.load/store`** khỏi **`getKey/getEntry/KeyManagerFactory`**, xác định **JKS/PKCS12/PKCS11 và provider**, rồi quản lý đúng secret, alias, key material và vòng đời của chúng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-store-006">12.6 Đọc khóa cùng chuỗi chứng thư và kiểm tra alias hoặc mật khẩu sai</a>

<details>
<summary>Xem chi tiết</summary>

# 12.6. Đọc khóa cùng chuỗi chứng thư và kiểm tra alias hoặc mật khẩu sai

Java `KeyStore` là một **kho chứa các entry khóa và chứng thư** được truy cập qua `java.security.KeyStore`. Một tệp PKCS#12 (`.p12`/`.pfx`) có thể chứa **private key** và **certificate chain** của khóa đó, hoặc các entry khác. Để sử dụng đúng, ứng dụng phải phân biệt **mở kho bằng store password**, **chọn entry bằng alias**, **mở khóa entry bằng key password**, và **kiểm tra chain/chứng thư có phù hợp mục đích và đáng tin hay không**.

Trong bài **12.6** thuộc chủ đề **“12. KeyStore”**, chúng ta dùng Java 21 và `keytool` để tạo một PKCS#12 thử nghiệm, đọc private key cùng chuỗi gồm hai chứng thư, kiểm tra alias tồn tại/sai, mô phỏng hai loại mật khẩu sai và phân tích exception. Một chương trình Java chạy được giúp bạn nhìn thấy kết quả thực tế, sau đó bài học chỉ ra giới hạn của demo và các bước cần có trong sản phẩm.

## Mục tiêu

Sau bài này, bạn có thể:

1. Phân biệt `KeyStore`, **PKCS12/JKS**, `PrivateKeyEntry`, `SecretKeyEntry`, `TrustedCertificateEntry` và alias.
2. Sử dụng `KeyStore.getInstance("PKCS12")` và `load(InputStream, char[])` để mở kho; hiểu khác biệt giữa **store password** và **entry/key password**.
3. Dùng `containsAlias`, `isKeyEntry`, `isCertificateEntry`, `getKey`, `getCertificate`, `getCertificateChain` và `getEntry` đúng trường hợp.
4. Đọc và kiểm tra chuỗi chứng thư theo thứ tự **leaf → issuer → ...**, nhưng không nhầm **đọc được chain** với **chain đáng tin theo PKIX**.
5. Phân biệt tình huống **alias không tồn tại**, alias có sai loại entry, **store password sai**, **key password sai**, file lỗi/không đúng định dạng và provider thiếu thuật toán.
6. Biên dịch/chạy Java 21 trên fixture có chain hai phần tử; hiểu cách bảo vệ file, password, private key, log và cấu hình TLS/JSSE sau bước đọc.

## 1. Mô hình KeyStore và những thứ thực sự được lưu

`KeyStore` là một **API trừu tượng**: cách lưu trữ và hành vi cụ thể do `KeyStoreSpi` của provider quyết định. Trong ví dụ chúng ta chỉ rõ `PKCS12` để không phụ thuộc `KeyStore.getDefaultType()` hoặc cấu hình môi trường.

Một cấu trúc khái niệm:

```text
demo.p12 (PKCS12)
|
+-- alias: service-signing
|     type: PrivateKeyEntry
|     privateKey: RSA private key (bảo vệ bằng password theo provider)
|     chain[0]: chứng thư leaf CN=service.example.test
|     chain[1]: chứng thư issuer CN=Demo CA
|
+-- alias: demo-ca
      type: PrivateKeyEntry (lab CA có private key riêng)
      privateKey: khóa ký CA dùng để tạo leaf certificate
      chain[0]: chứng thư CA tự ký
```

*Lưu ý:* Hai alias ở đây đều là **PrivateKeyEntry** vì tạo bằng `keytool -genkeypair`. Một CA certificate được import riêng vào truststore (không kèm private key) mới thường là **TrustedCertificateEntry**. Trong hệ thống thật, **không** nên đặt private key CA vào cùng tệp ứng dụng cần triển khai trên server; nó chỉ nằm chung ở đây để bài lab tạo chuỗi chứng thư dễ kiểm tra.

| Khái niệm | Ý nghĩa | API nhận diện |
| --- | --- | --- |
| `alias` | Tên entry nội bộ, ví dụ `service-signing`; **không phải danh tính đã được xác thực** | `containsAlias(alias)`, `aliases()` |
| `PrivateKeyEntry` | Private key + **certificate chain** tương ứng, dùng cho ký hoặc xác thực TLS tùy mục đích | `isKeyEntry`, `getEntry`, `getKey`, `getCertificateChain` |
| `SecretKeyEntry` | Khóa đối xứng, ví dụ AES; không có X.509 chain theo nghĩa private-key entry | `isKeyEntry`, `getEntry` |
| `TrustedCertificateEntry` | Một chứng thư được lưu theo chính sách tin cậy (không chứa private key) | `isCertificateEntry`, `getCertificate` |
| `store password` | Dùng lúc `load` để mở và/hoặc kiểm tra tính toàn vẹn file | `load(input, storePassword)` |
| `key/entry password` | Dùng khi lấy protected key từ entry | `getKey(alias, keyPassword)`, `getEntry(alias, ProtectionParameter)` |
| Certificate chain | Chứng thư leaf và các chứng thư issuer để hỗ trợ xây dựng đường dẫn chứng thực | `getCertificateChain(alias)` |
| Trust anchor | CA/public key được tin **độc lập với dữ liệu do peer gửi** | `TrustAnchor`, PKIX validator/truststore policy |

Một `KeyStore` có thể làm **identity keystore** (có private key của server/client) hoặc **truststore** (chứa các certificate được tổ chức cho phép tin), nhưng hai vai trò này không nên gộp theo cách khó kiểm soát.

## 2. Quy trình đọc: bốn bước khác nhau

Giả sử bạn cần private key và chain dưới alias `service-signing`:

```text
[Path demo.p12]
    |
    v
KeyStore.getInstance("PKCS12")
    |
    v
load(InputStream, storePassword)    <-- sai password/file -> lỗi load
    |
    v
containsAlias("service-signing")    <-- false nếu alias không có
    |
    v
isKeyEntry(alias) / getEntry type   <-- sai loại entry -> không dùng làm private key
    |
    +--> getKey(alias, keyPassword)        <-- password mở khóa entry
    |
    +--> getCertificateChain(alias)        <-- leaf ở [0], tiếp issuer...
                  |
                  v
    Kiểm tra key phù hợp chứng thư, mục đích, PKIX trust/policy
```

Những thao tác này trả lời các câu hỏi **khác nhau**:

- **`load` thành công:** provider đã đọc/khởi tạo KeyStore và dùng tham số được cấp theo cơ chế hỗ trợ. Điều đó không chứng minh tất cả alias bạn muốn đều tồn tại, cũng không xác thực một chứng thư bên trong với CA tin cậy.
- **`containsAlias == true`:** có entry mang tên ấy; không nói nó là private key, chứng thư trusted hay secret key.
- **`getKey` thành công:** entry cung cấp khóa và password phù hợp. Khóa có thể là `PrivateKey` hoặc `SecretKey`; phải kiểm tra kiểu, algorithm và quyền sử dụng trước khi dùng.
- **`getCertificateChain` có giá trị:** tệp lưu một chuỗi chứng thư. Nó **không phải** lệnh kiểm tra tin cậy CA, hostname, thời hạn, EKU, key usage, revocation hoặc path constraints.
- **Private key và leaf public key phù hợp:** có thể kiểm tra bằng phép ký/xác minh thử với scheme phù hợp. Đây vẫn chưa phải kết luận chain thuộc một tổ chức đáng tin.

**Đừng coi alias là username.** Alias giúp tìm entry trong file; danh tính và quyền sử dụng phải dựa vào nguồn tin cậy và chính sách ứng dụng.

## 3. Khởi tạo fixture PKCS#12 có chain hai chứng thư

Bài lab dùng **JDK 21** hoặc JDK mới hơn có `keytool` hỗ trợ `-signer`. Bạn cần một thư mục trống để chứa `demo.p12` và file Java. Dưới đây dùng **RSA 3072 bit**, chữ ký `SHA256withRSA` và thời hạn chứng thư **30 ngày**. CA tự ký là **chứng thư thử nghiệm**, không được hệ điều hành/trình duyệt tự tin cậy.

### 3.1 Đặt password chỉ dùng cho lab

`keytool` hỗ trợ modifier `:env` để lấy password từ biến môi trường. Để tái lập bài học, hai lệnh dưới đặt **password giả**. Không dùng chuỗi này trong hệ thống thật; tránh đặt secret thật trong source, lịch sử shell hoặc log.

**Windows PowerShell:**

```powershell
$env:DEMO_KS_PASS = 'DemoOnly-ChangeMe42'
```

**Linux/macOS (Bash):**

```bash
export DEMO_KS_PASS='DemoOnly-ChangeMe42'
```

Trong production, lấy secrets từ secret manager/KMS, protected input hoặc cơ chế triển khai được kiểm soát. Biến môi trường vẫn có rủi ro bị lộ bởi cấu hình/process/debugging; `:env` chỉ tránh **trực tiếp đưa password vào tham số** `-storepass value` trên command line.

### 3.2 Tạo một CA tạm để ký chứng thư dịch vụ

Chạy từ thư mục demo:

```text
keytool -genkeypair -alias demo-ca -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=Demo CA,O=Training,C=VN" -ext "BC:critical=ca:true,pathlen:0" -ext "KU:critical=keyCertSign,cRLSign" -validity 30 -keystore demo.p12 -storetype PKCS12 -storepass:env DEMO_KS_PASS -keypass:env DEMO_KS_PASS -noprompt
```

Đây là cặp khóa CA thử nghiệm; `BC=ca:true` và `keyCertSign` diễn tả mục đích ký certificate. `keytool` tạo chứng thư CA tự ký, nên **chain của `demo-ca` chỉ có một phần tử**.

### 3.3 Tạo khóa dịch vụ và chứng thư được CA thử nghiệm ký

```text
keytool -genkeypair -alias service-signing -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=service.example.test,O=Training,C=VN" -ext "SAN=dns:service.example.test" -ext "KU:critical=digitalSignature" -signer demo-ca -signerkeypass:env DEMO_KS_PASS -validity 30 -keystore demo.p12 -storetype PKCS12 -storepass:env DEMO_KS_PASS -keypass:env DEMO_KS_PASS -noprompt
```

`-signer demo-ca` sử dụng private key tại alias CA để ký chứng thư cho `service-signing`. Vì vậy entry mới có **chuỗi hai chứng thư**: leaf rồi CA. Điều này giúp kiểm tra `getCertificateChain`, **không cấp trust** cho CA tự ký.

Trong ví dụ, store password và key password cùng dùng `DEMO_KS_PASS` vì nhiều implementation/tích hợp PKCS#12 yêu cầu hoặc ưu tiên cùng password. **Về API, store password và entry password vẫn là hai đầu vào khác nhau**, dù có thể cùng giá trị trong fixture. JKS và một số provider/store khác có quy ước khác; không mặc định mọi PKCS#12 trên mọi JDK hỗ trợ hai password độc lập.

### 3.4 Dùng `keytool` kiểm tra cấu trúc trước khi viết Java

```text
keytool -list -v -alias service-signing -keystore demo.p12 -storetype PKCS12 -storepass:env DEMO_KS_PASS
```

Các dòng quan trọng (khác biệt nhỏ về cách in tùy JDK):

```text
Alias name: service-signing
Entry type: PrivateKeyEntry
Certificate chain length: 2
Owner: CN=service.example.test, O=Training, C=VN
Issuer: CN=Demo CA, O=Training, C=VN
...
Owner: CN=Demo CA, O=Training, C=VN
Issuer: CN=Demo CA, O=Training, C=VN
```

Trên Java có thể chọn các key provider khác nhau, nhưng **alias, entry type và chain length** nói lên cấu trúc cần kiểm tra. `-list -v` chỉ mô tả metadata/chain và **không xuất private key**.

Nếu chạy lệnh `-genkeypair` lần hai trên cùng file/alias đã tồn tại, `keytool` sẽ từ chối trùng alias. Bài lab nên làm trên thư mục riêng, không thao tác với truststore/cacerts thực tế hoặc kho khóa sản xuất.

## 4. Java 21: đọc private key + certificate chain và kiểm tra lỗi

Tạo file đúng tên **`KeyStoreReadDemo.java`** trong cùng thư mục với `demo.p12`. Chương trình nhận **đường dẫn** và **alias** ở command line; đọc password lab từ `DEMO_KS_PASS`. Nó in các boolean và độ dài chain, **không in private key, password hay nội dung bản mã bí mật**.

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;

public final class KeyStoreReadDemo {
    private static KeyStore open(Path file, char[] storePassword)
            throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(file)) {
            store.load(input, storePassword);
        }
        return store;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: java KeyStoreReadDemo <file.p12> <alias>");
        }
        String passwordFromEnvironment = System.getenv("DEMO_KS_PASS");
        if (passwordFromEnvironment == null
                || passwordFromEnvironment.isEmpty()) {
            throw new IllegalStateException("Missing DEMO_KS_PASS");
        }

        Path path = Path.of(args[0]);
        String alias = args[1];
        char[] password = passwordFromEnvironment.toCharArray();
        char[] incorrect = "intentionally-wrong".toCharArray();
        try {
            KeyStore store = open(path, password);

            System.out.println("keystore type: " + store.getType());
            System.out.println("alias present: " + store.containsAlias(alias));
            System.out.println("is key entry: " + store.isKeyEntry(alias));
            System.out.println("missing alias: " +
                    !store.containsAlias("not-present"));
            System.out.println("missing key is null: " +
                    (store.getKey("not-present", password) == null));
            System.out.println("missing chain is null: " +
                    (store.getCertificateChain("not-present") == null));

            if (!store.containsAlias(alias) || !store.isKeyEntry(alias)) {
                throw new IllegalStateException("Missing/private key alias");
            }
            Key key = store.getKey(alias, password);
            if (!(key instanceof java.security.PrivateKey privateKey)) {
                throw new IllegalStateException("Not a private key entry");
            }
            Certificate[] chain = store.getCertificateChain(alias);
            if (chain == null || chain.length < 2) {
                throw new IllegalStateException(
                        "Expected leaf and issuer certificates");
            }
            X509Certificate leaf = (X509Certificate) chain[0];
            X509Certificate issuer = (X509Certificate) chain[1];
            System.out.println("certificate chain length: " + chain.length);
            System.out.println("leaf first: " +
                    leaf.equals(store.getCertificate(alias)));
            System.out.println("issuer name linked: " +
                    leaf.getIssuerX500Principal().equals(
                            issuer.getSubjectX500Principal()));
            leaf.verify(issuer.getPublicKey()); // Signature link only.
            System.out.println("issuer signature verifies: true");

            byte[] message = "certificate-key match".getBytes(
                    StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(message);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(leaf.getPublicKey());
            verifier.update(message);
            System.out.println("private key matches leaf: " +
                    verifier.verify(signature));

            boolean wrongStoreRejected = false;
            try {
                open(path, incorrect);
            } catch (IOException expected) {
                wrongStoreRejected = true;
            }
            System.out.println("wrong store password rejected: " +
                    wrongStoreRejected);

            boolean wrongKeyRejected = false;
            try {
                store.getKey(alias, incorrect);
            } catch (UnrecoverableKeyException expected) {
                wrongKeyRejected = true;
            }
            System.out.println("wrong key password rejected: " +
                    wrongKeyRejected);
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(incorrect, '\0');
        }
    }
}
```

**Biên dịch và chạy:**

```text
javac -version
javac --release 21 -encoding UTF-8 KeyStoreReadDemo.java
java KeyStoreReadDemo demo.p12 service-signing
```

Khi file được tạo đúng bằng các lệnh ở trên, stdout từ chương trình Java:

```text
keystore type: PKCS12
alias present: true
is key entry: true
missing alias: true
missing key is null: true
missing chain is null: true
certificate chain length: 2
leaf first: true
issuer name linked: true
issuer signature verifies: true
private key matches leaf: true
wrong store password rejected: true
wrong key password rejected: true
```

Các chương trình đã được kiểm tra với compiler hỗ trợ `--release 21` và JDK provider tiêu chuẩn; chi tiết thông báo lỗi của `keytool`/JDK khác nhau theo nhà cung cấp/bản vá, nhưng **dòng stdout Java nêu trên phản ánh đúng fixture đã tạo và điều kiện chương trình**. Nếu bạn chọn alias khác, chain khác, password khác hay provider khác thì kết quả hoặc loại lỗi có thể khác; đừng “sửa” expected output bằng cách bỏ kiểm tra.

### 4.1 Phân tích điểm then chốt

- `KeyStore.getInstance("PKCS12")` chọn định dạng xác định. `load(InputStream, storePassword)` đọc bytes và dùng password theo chính sách provider; `try-with-resources` đóng luồng file kể cả khi load lỗi.
- `containsAlias(alias)` và `isKeyEntry(alias)` kiểm tra cấu trúc trước khi truy xuất. `getKey` trả về kiểu `java.security.Key` chung, nên `instanceof PrivateKey` mới giúp xác nhận có private key cho thao tác ký.
- `getCertificateChain(alias)` trả chuỗi hoặc `null`. Với `PrivateKeyEntry` fixture, chain dài 2, phần tử **0** là leaf, phần tử **1** là issuer. Với root tự ký hoặc một fixture khác, chain có thể chỉ dài 1; đó không phải lỗi Java API.
- `getCertificate(alias)` trả leaf certificate cho private-key entry. `leaf.equals(store.getCertificate(alias))` giúp xác nhận chúng tương ứng trong fixture.
- `leaf.getIssuerX500Principal() == issuer.getSubjectX500Principal()` trong code dùng `equals` để kiểm tra liên kết tên; sau đó `leaf.verify(issuer.getPublicKey())` kiểm chữ ký leaf bằng public key của issuer. **Không dòng nào trong hai bước tự tạo trust anchor!**
- Chương trình thử ký bytes mới bằng private key, rồi xác minh bằng **leaf public key**. Kết quả `true` xác nhận cặp khóa phù hợp cho `SHA256withRSA` trong fixture; để dùng production còn phải kiểm tra policy, key usages, validity, trust.
- Alias `not-present` không có: `containsAlias` trả `false`, `getKey` và `getCertificateChain` trả `null`. Đó là trường hợp tra cứu bình thường, **không mặc định ném exception**.
- Store password sai được thử bằng `open(path, incorrect)` → `IOException`; key password sai được thử **sau khi kho đã load thành công** bằng `getKey(alias, incorrect)` → `UnrecoverableKeyException`. Hai phép kiểm nằm ở **hai giai đoạn khác nhau**.

**Cần phân biệt:** chương trình chỉ thử mật khẩu không đúng trong một fixture tự tạo. Không xử lý "sai password" bằng cách thử vô hạn nhiều giá trị hoặc ghi toàn bộ password/stacktrace ra log API. Trong thực tế, việc kiểm tra password ở endpoint/CLI cần hạn chế retry, thông tin lỗi cho người dùng và quản lý tài nguyên phù hợp.

## 5. `KeyStore.getEntry`: cách đọc entry theo đúng loại

Ngoài `getKey` và `getCertificateChain`, có thể đọc qua `KeyStore.getEntry(alias, new KeyStore.PasswordProtection(keyPassword))` rồi kiểm tra loại entry.

Đoạn sau **minh họa**, được dùng **bên trong chương trình có biến `store` đã load**, `alias` và `keyPassword` phù hợp (không phải class độc lập):

```java
KeyStore.Entry entry = store.getEntry(
        alias, new KeyStore.PasswordProtection(keyPassword));

if (entry instanceof KeyStore.PrivateKeyEntry privateEntry) {
    java.security.PrivateKey key = privateEntry.getPrivateKey();
    java.security.cert.Certificate[] chain =
            privateEntry.getCertificateChain();
    // Áp policy trước khi dùng key; không log private key.
} else if (entry instanceof KeyStore.SecretKeyEntry) {
    throw new IllegalArgumentException("Alias contains a secret key");
} else if (entry instanceof KeyStore.TrustedCertificateEntry) {
    throw new IllegalArgumentException("Alias is certificate only");
} else if (entry == null) {
    throw new IllegalArgumentException("Alias not found");
}
```

`KeyStore.PrivateKeyEntry` biểu diễn khóa riêng + chain; `SecretKeyEntry` không có chain X.509 như private key; `TrustedCertificateEntry` có certificate nhưng không có private key. `KeyStore.PasswordProtection` chứa password phục vụ truy xuất entry, nên phải có vòng đời bí mật phù hợp (có thể gọi `destroy()` khi dùng xong).

**Ngoại lệ:** `getEntry` có thể ném `UnrecoverableEntryException` nếu tham số bảo vệ không đủ/không hợp lệ, bao gồm password không đúng. Khi dùng `getKey` trực tiếp, thường gặp `UnrecoverableKeyException`. Đây là hai cách gọi API với exception được khai báo khác nhau.

### 5.1 `isKeyEntry` chưa đủ để khẳng định “là private key”

`isKeyEntry(alias)` có thể đúng cho cả private-key entry và secret-key entry. Nếu ứng dụng cần chứng thư TLS cho server, cần **PrivateKeyEntry + chain + phù hợp keyUsage/purpose**; một `SecretKey` AES trong KeyStore không thể thay thế chỉ vì `isKeyEntry` trả `true`.

Ngược lại, `isCertificateEntry(alias)` đúng cho alias là **TrustedCertificateEntry**. Với alias leaf có private key, nó thường trả `false` dù `getCertificate(alias)` trả một leaf certificate. Đây là điểm hay bị nhầm khi kiểm tra keystore bằng các boolean.

## 6. Khi alias hoặc password sai, Java xử lý thế nào?

| Tình huống | API | Hành vi thường thấy/được tài liệu quy định | Cách xử lý |
| --- | --- | --- | --- |
| Alias không tồn tại | `containsAlias("missing")` | `false` | Báo cấu hình alias không hợp lệ; có thể liệt kê alias nội bộ một cách an toàn. |
| Alias không có key | `getKey(alias, keyPass)` | `null` nếu alias không tồn tại hoặc không đại diện key | Kiểm tra `containsAlias` và loại entry; không dereference `null`. |
| Alias không có chain | `getCertificateChain(alias)` | `null` nếu alias thiếu/không có chain | Kiểm tra đúng `PrivateKeyEntry` và yêu cầu nghiệp vụ về chain. |
| Alias là certificate-only | `isCertificateEntry(alias)` | Có thể `true`, `isKeyEntry` là `false` | Không dùng làm signing private key. |
| Store password sai | `load(input, storePass)` | Thường `IOException` với nguyên nhân liên quan password/integrity | Dừng load, phân biệt với file/format lỗi ở logging nội bộ; không giả định loại lỗi chi tiết luôn giống nhau. |
| Key password sai | `getKey(alias, keyPass)` | `UnrecoverableKeyException` | Không coi là alias thiếu; kiểm tra secret reference và chính sách provider. |
| Entry password sai | `getEntry(alias, protection)` | `UnrecoverableEntryException` / subclass phù hợp | Kiểm tra `PasswordProtection`, không bỏ qua lỗi và tiếp tục với key null. |
| File không tồn tại/không đọc được | `Files.newInputStream(path)` | `NoSuchFileException`/`AccessDeniedException` hoặc `IOException` khác | Kiểm tra path, permission, mount, working directory. |
| Store type/provider/algorithm không hỗ trợ | `KeyStore.getInstance`/`load`/`getKey` | `KeyStoreException` / `NoSuchAlgorithmException` hoặc lỗi liên quan | Kiểm tra JDK/provider, format, thuật toán bảo vệ. |
| Chứng thư không parse được | `load` | Có thể `CertificateException` | Kiểm tra dữ liệu, provider, bản mã hóa và trust configuration. |
| Chưa `load` nhưng dùng store | `getKey`/`aliases` | `KeyStoreException` | Gọi `load` đúng trước khi truy xuất. |
| Mật khẩu đúng nhưng chain không đáng tin | `getCertificateChain` | Vẫn có thể trả chuỗi | Phải làm path validation và identity checks riêng. |

### 6.1 Hai password có thể cùng một chuỗi nhưng không đồng nghĩa

Có hai điểm bảo vệ khác nhau:

```text
demo.p12
   +-- load(storePassword) -----------------> integrity/open
   +-- getKey("service-signing", keyPassword) -> recover private key
```

Trong bài lab, cả hai đều lấy từ `DEMO_KS_PASS`. Với nhiều tệp PKCS#12, việc đặt hai giá trị khác nhau **không** tương thích rộng; một số JDK/provider chuẩn hóa key password = store password. Với JKS hoặc HSM, quy tắc có thể khác. Do đó API nên **phân biệt hai trường cấu hình**, nhưng quy trình tạo kho phải phù hợp khả năng của định dạng/provider thực tế.

**Tránh mẹo nguy hiểm:** gọi `load(input, null)` chỉ để “đọc được file khi không biết password”. Theo hợp đồng `KeyStore`, khi không cung cấp password kiểm tra toàn vẹn, **integrity checking có thể không được thực hiện**; provider khác có thể có ràng buộc riêng. Đây không phải giải pháp xử lý password sai và không thay thế kiểm tra integrity.

### 6.2 Không tự đoán alias từ tên file hoặc `CN`

Alias `service-signing` không bắt buộc bằng `CN=service.example.test`. Alias do người tạo kho chọn; `CN`/SAN nằm trong certificate subject/extension; định danh server phải được xác thực từ certificate **theo policy TLS/ứng dụng**, không phải từ alias.

Một số provider có quy tắc chuẩn hóa alias (ví dụ lower-case) hoặc phân biệt hoa/thường khác nhau. **Không dựa vào giả định alias luôn case-sensitive hay case-insensitive.** Với kho thực tế, dùng `keytool -list -keystore ...` hoặc `store.aliases()` để kiểm tra những alias được provider trả về, rồi cấu hình tên chính xác.

Ví dụ code rà soát alias **dùng trong nội bộ công cụ vận hành** (không in thông tin đó cho client công khai):

```java
java.util.Enumeration<String> names = store.aliases();
while (names.hasMoreElements()) {
    String name = names.nextElement();
    System.out.println(name + ": key=" + store.isKeyEntry(name)
            + ", trustedCert=" + store.isCertificateEntry(name));
}
```

Không in `key.getEncoded()`, `password` hoặc toàn bộ chứng thư/private key nếu không có yêu cầu rõ ràng. Metadata và alias cũng có thể tiết lộ cấu trúc hệ thống, nên hạn chế log.

## 7. Đọc certificate chain không có nghĩa là xác minh trust

`getCertificateChain(alias)` trả các chứng thư đã lưu theo thứ tự **leaf first**; nguồn có thể là chính PKCS#12 hoặc bên gửi cung cấp. Một chuỗi tự ký/xây dựng sai vẫn có thể parse và được trả về.

Trong demo:

```text
service-signing:
  [0] CN=service.example.test    ← leaf; public key tương ứng private key
  [1] CN=Demo CA                 ← issuer certificate (tự ký thử nghiệm)
```

`leaf.verify(issuer.getPublicKey())` chỉ kiểm tra **chữ ký của leaf bằng public key issuer được cấp**, chưa xác nhận issuer được tin. `leaf.checkValidity()` (nếu gọi) kiểm tra thời hạn ở một thời điểm, nhưng cũng không kiểm hostname, key usage, revocation hoặc trust anchor.

### 7.1 Các bước kiểm tra thực sự đối với chain

Nếu bạn nhận một chain và cần biết nó có được **tổ chức của bạn chấp nhận** không, phải xác định mục đích và trust policy, ví dụ:

1. **Tin một trust anchor độc lập:** có truststore/CA root/pinned key do tổ chức cấu hình và bảo vệ, không lấy trust anchor từ chính chain chưa xác thực của peer.
2. **Xây dựng và xác minh PKIX path:** kiểm chữ ký từng certificate, giới hạn basic constraints/path length, tính hợp lệ theo thời gian, thuật toán/key strength, các constraints và chính sách phù hợp.
3. **Kiểm tra tên đích/identity:** ví dụ TLS HTTPS cần DNS SAN khớp hostname sau khi xác minh chain; việc `CN` “có vẻ đúng” không phải đủ trong các profile hiện đại.
4. **Kiểm tra mục đích sử dụng:** key usage, extended key usage khi applicable, policy ứng dụng và quyền signer/tenant.
5. **Thu hồi:** khi threat model/chính sách yêu cầu, cấu hình OCSP/CRL hoặc cơ chế revocation thích hợp; không mặc định mọi call `CertPathValidator` tự bật và hoàn tất revocation.
6. **Quản lý pin/truststore vòng đời:** expiration, rotation, removal, thay CA, kiểm soát quyền cập nhật và audit.

Java có `CertPathValidator`, `CertPathBuilder`, `PKIXParameters` và `TrustAnchor` cho các trường hợp phải tự xử lý PKIX. Tuy nhiên, với TLS, tốt hơn để **JSSE TrustManager/`SSLContext`** thực thi validation theo profile và hostname verification của client, tránh viết bộ xác minh certificate ad hoc.

**Không nhập CA của demo vào truststore production.** Đó là CA tự ký chỉ có ý nghĩa lab; việc chữ ký chain kiểm được hoàn toàn không xác nhận nó thuộc một tổ chức tin cậy thực tế.

### 7.2 Private key có khớp certificate leaf không?

Một keystore entry đúng cấu trúc nên có private key phù hợp với public key ở leaf. Chương trình dùng `SHA256withRSA` để ký message thử bằng private key rồi xác minh với leaf public key. Nếu `verify` trả `true`, cặp khóa phù hợp **theo thuật toán RSA signature đã chọn**.

Kiểm tra này không thay thế kiểm certificate chain, không xác minh thẩm quyền cấp certificate, và không phải loại key nào cũng dùng RSA/`SHA256withRSA`. Nếu thực tế là EC hoặc Ed25519, phải chọn scheme tương ứng và policy hợp lệ; đừng dùng `Signature.getInstance("SHA256withRSA")` trên mọi private key vì code lab đang giả định RSA.

## 8. PKCS#12 và KeyStore khi cấu hình TLS/JSSE

Khi một Java server cần trình ra chứng thư TLS kèm private key, cấu hình thường dùng:

```text
PKCS12 (private key + certificate chain)
    |
    v
KeyManagerFactory (dùng key password nếu provider yêu cầu)
    |
    v
SSLContext / SSLEngine / SSLSocket (server certificate + private-key proof)
                       |
                 client verifies:
        truststore + PKIX + hostname/service identity
```

Một truststore riêng của client chứa **CA/public key đáng tin** cho xác minh server; `TrustManagerFactory` giúp tạo trust manager. Việc server tải thành công `demo.p12` hoặc lấy `PrivateKey` **không chứng minh TLS handshake thành công** hay client đã tin certificate của server.

Trong TLS, server certificate còn cần **extendedKeyUsage/keyUsage** phù hợp, tên SAN đúng và chuỗi issuer hợp lệ đến trust anchor; cần kiểm protocol version/cipher suites, quản lý khóa, expiration/rotation và error handling. Fixture bài lab dùng `KU=digitalSignature` và SAN dịch vụ để minh họa, nhưng CA tự ký không được tin cậy ngoài lab, nên **không dùng nó làm chứng thư TLS thực tế**.

Nếu có nhiều alias chứa private key, `KeyManager` có thể lựa chọn alias theo thuật toán/certificate request. Cấu hình đúng alias và certificate chain giúp tránh chọn nhầm khóa, nhưng việc lựa chọn cuối cùng do JSSE/provider và chính sách ứng dụng quyết định.

## 9. Quy tắc bảo vệ KeyStore, certificate chain và password

- **Bảo vệ file và backup:** PKCS#12 có thể có password-based encryption/integrity nhưng vẫn chứa key material nhạy cảm; giới hạn OS permissions, ownership, backup access, đường dẫn mount/container và quyền đọc từ service account.
- **Không hardcode secrets production:** dùng KMS/HSM/secret manager hoặc protected input. Tránh password trong source, Git, tham số dòng lệnh, CI output, exception message và logs. Env var tiện cho demo/automation nhưng vẫn có thể bị lộ.
- **Dùng `char[]` khi API hỗ trợ:** sau khi dùng, gọi `Arrays.fill(password, '\0')`. Điều này không bảo đảm xóa mọi bản sao trong JVM/provider; nếu lấy bằng `System.getenv` sẽ tồn tại `String` bất biến không thể dọn hoàn toàn.
- **Không in private key:** `PrivateKey.getEncoded()` (nếu provider cho phép) có thể chứa khóa riêng thực sự. Chỉ log alias/loại key/metadata cần thiết, giới hạn quyền đọc.
- **Đừng tự động thử password liên tiếp:** password sai phải dừng và phân loại rõ; lỗi alias không tồn tại khác với lỗi password, nhưng phản hồi public có thể cần thống nhất để tránh information leak.
- **Tránh password null để bỏ xác minh:** bảo vệ integrity và availability; xử lý exceptions theo chính sách, kiểm tra checksum/signatures khi phù hợp, không tự “recover” bằng cách vô hiệu validation.
- **Luân chuyển khóa:** thay entry alias, private key và certificate chain theo quy trình có rollback, kiểm tra trust chain client trước cutover; không deploy một chain mới thiếu issuer và chờ client tự tải được.
- **Không giả định chain luôn dài 2 hoặc chứa root:** nhiều hệ thống lưu leaf + intermediates, có thể không lưu root; `getCertificateChain` có thể có độ dài 1, nhiều hơn 2 hoặc null. Điều kiện `length >= 2` của demo là **yêu cầu fixture**, không phải kiểm tra PKIX tổng quát.

## 10. Các lỗi thường gặp qua ví dụ

| Hiện tượng | Gốc vấn đề | Bước xử lý hợp lý |
| --- | --- | --- |
| `containsAlias("service-signing") == false` | Sai alias, store khác, alias bị đổi sau import/convert | Liệt kê alias nội bộ; kiểm đúng file/type và cấu hình. |
| `getKey(...) == null` | Alias không tồn tại hoặc không đại diện khóa | Phân biệt với password sai; kiểm `containsAlias`, `isKeyEntry`. |
| `getCertificateChain(...) == null` | Alias không có chain / không phải private-key entry | Kiểm entry type, import chain đầy đủ, policy. |
| `getKey` ném `UnrecoverableKeyException` | Password entry sai hoặc provider không recover được | Kiểm nguồn secret/config; không thử ngẫu nhiên password. |
| `load` ném `IOException` | Store password sai, file lỗi/truncated hoặc format không phù hợp | Kiểm format, path, quyền, password qua kênh bảo mật; xem cause nội bộ. |
| `getEntry` ném `UnrecoverableEntryException` | PasswordProtection sai/không phù hợp | Xem entry type và provider contract. |
| `InvalidKeyException` khi ký | Private key không phù hợp scheme `Signature` | Kiểm `key.getAlgorithm()`, provider, key usage, scheme. |
| Chain len = 1 trong bài | Tạo leaf tự ký thay vì `-signer demo-ca`, hoặc dùng alias CA | Kiểm lệnh tạo fixture và kết quả `keytool -list -v`. |
| Leaf có issuer name nhưng verify fail | Certificate bị thay, sai issuer public key, signature lỗi | Không dùng chain; xây path và kiểm bằng PKIX/trust policy. |
| Leaf verify được nhưng client TLS vẫn không tin | CA chưa là trust anchor, SAN/EKU/validity/policy không đúng | Dùng TLS TrustManager/truststore đúng; không vô hiệu hostname/cert checks. |
| Local chạy đúng, container báo alias/password lỗi | Container mount nhầm file, secret/config khác, dùng JDK/provider khác | Kiểm hash file/cấu hình triển khai một cách an toàn, tránh log secret. |
| Khóa riêng đọc được nhưng chứng thư đã hết hạn | Keystore storage không tự thực thi PKIX policy | Theo dõi expiration, luân chuyển chứng thư và kiểm chứng ở tầng TLS/ứng dụng. |

## 11. Bài tự thực hành

1. **Alias và type:** sau khi tạo `demo.p12`, chạy `keytool -list -v` cho `demo-ca` và `service-signing`. Vì sao cả hai đều là `PrivateKeyEntry` nhưng chain length khác nhau?
2. **Wrong alias:** thay đối số Java thành `unknown-key`. Với code demo hiện tại, những dòng nào chuyển thành `false`, và vì sao chương trình dừng tại guard thay vì thực hiện `getKey` theo alias sai?
3. **Sai store password:** tạo biến môi trường có password khác trước khi chạy Java; dự đoán exception phát sinh tại `load`. Khôi phục password lab đúng sau khi quan sát, tuyệt đối không thay mật khẩu production để thử.
4. **Sai key password:** giữ store password đúng, đưa password sai vào riêng `getKey(alias, wrongPassword)`. Vì sao `containsAlias` vẫn `true` dù không recover được khóa?
5. **Đọc chain:** in **subject/issuer names** của `chain[0]`/`chain[1]` (chỉ trên dữ liệu lab), xác định đâu là leaf và CA; giải thích vì sao `leaf.verify(issuer.getPublicKey())` chưa đủ để tin CA.
6. **Entry types:** thiết kế một truststore có certificate-only entry và thử `isCertificateEntry`, `getCertificate`, `getCertificateChain`, `getKey`; nhận xét kết quả từng API.
7. **Trust:** nếu kẻ tấn công tự tạo CA và ký chứng thư leaf giả nhưng tên giống thật, bước nào trong validation sẽ ngăn client tin chain đó?
8. **Lỗi đóng gói:** đổi tên file, đổi đường dẫn, cấp thiếu quyền đọc; so sánh với lỗi alias sai và password sai để tìm vị trí lỗi trong pipeline.
9. **Chuỗi trung gian:** nếu có root CA → intermediate CA → leaf, `getCertificateChain` nên xếp thứ tự nào? Có nhất thiết phải lưu root certificate trong entry không? Vì sao không?
10. **Thiết kế dịch vụ:** phác thảo cấu hình server TLS với identity PKCS12 và client truststore, quy trình rotate alias/cert và hành vi khi cert hết hạn.

**Tiêu chí hoàn thành:** bạn có thể chỉ ra rõ **file nào đang được mở, store type nào, alias nào, entry type nào, password nào dùng ở giai đoạn nào, khóa có khớp leaf hay không, chain được lưu theo thứ tự nào và cần tin cậy chain bằng cơ chế nào**. Không nhầm “đã đọc được private key + chain” với “đã xác thực chứng thư” hoặc “hệ thống TLS đã an toàn”.

## Tài liệu tham khảo

- [Java SE 21 — `java.security.KeyStore`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyStore.html): `load`, `getKey`, `getEntry`, `getCertificateChain`, `containsAlias`, exception và contract của các entry.
- [Java SE 21 — `KeyStore.PrivateKeyEntry`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyStore.PrivateKeyEntry.html): private key, chain, thứ tự và điều kiện entry.
- [Java SE 21 — `java.security.cert.X509Certificate`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/X509Certificate.html): subject, issuer, verify, validity và extensions.
- [JDK 21 — `keytool` command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html): `-genkeypair`, `-signer`, `-list`, `-storetype`, `-storepass:env`.
- [Java SE 21 — `CertPathValidator`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/CertPathValidator.html): xác minh certificate path qua PKIX (cần trust/policy cấu hình).
- [Java SE 21 — `PKIXParameters`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/cert/PKIXParameters.html): trust anchors và ràng buộc xác minh.
- [Java 21 JSSE Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html): KeyManager/TrustManager, truststore, SSLContext và TLS certificate validation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-store-007">12.7 KeyStore.Builder, LoadStoreParameter và bảo vệ mật khẩu trong ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

# 12.7 KeyStore.Builder, LoadStoreParameter và bảo vệ mật khẩu trong ứng dụng

Ứng dụng Java có thể cần mở private key để ký số, secret key để mã hóa dữ liệu, hoặc certificate tin cậy để xác minh đối tác. **KeyStore** là API thống nhất cho việc chứa và truy cập các loại entry này, nhưng bản thân API không tự giải quyết toàn bộ bài toán **lấy mật khẩu từ đâu, thời điểm mở khóa, vòng đời bí mật trong RAM, quyền truy cập file/token và thay đổi khóa khi ứng dụng đang chạy**.

Bài học tập trung vào ba thành phần:

1. **KeyStore.Builder**: mô tả cách khởi tạo và cung cấp protection cho entry, có thể trì hoãn tải kho đến lúc cần.
2. **KeyStore.LoadStoreParameter**: giao diện mô tả tham số cho **KeyStore.load(...)** và **KeyStore.store(...)**; provider/loại kho quyết định cách diễn giải.
3. **KeyStore.ProtectionParameter**, nhất là **PasswordProtection** và **CallbackHandlerProtection**: cung cấp mật khẩu hoặc cơ chế hỏi mật khẩu.

Sau bài, bạn có thể phân biệt password bảo vệ **file/keystore** và **entry**, chọn overload đúng, xây dựng hai ví dụ Java 17+ chạy được, xử lý sai password, dọn char[]/protection đúng vòng đời, và thiết kế service không làm lộ private key hay password qua log/cấu hình.

## 1. Nền tảng cần nhớ khi dùng Builder

`KeyStore` quản lý các entry theo alias; ba loại chính là **PrivateKeyEntry** (private key kèm certificate chain), **SecretKeyEntry** (khóa đối xứng) và **TrustedCertificateEntry** (chứng chỉ công khai được cấu hình làm nguồn tin cậy). Một keystore dùng làm **identity store** khác với một **truststore** ở *vai trò tiêu thụ*; bản thân PKCS12 có thể được dùng cho cả hai.

Bài này tập trung **cách ứng dụng nạp kho và cấp secret theo thời điểm**, chứ không phân loại lại mọi entry: `Builder` quản lý nguồn/lifecycle, `LoadStoreParameter` truyền điều kiện load/store cho provider, còn `PasswordProtection` và `CallbackHandlerProtection` cung cấp thông tin bảo vệ. Với PKCS11/HSM, entry có thể được truy cập qua handle và PIN thay vì key exportable trong file.

**Giới hạn cần giữ rõ:** alias không phải quyền truy cập; password mở kho có thể khác password mở từng entry; builder không tự biến file PKCS12 thành secret manager, cũng không tự kiểm tra certificate chain hay quyền ký.

## 2. Vòng đời cơ bản của KeyStore

~~~text
KeyStore.getInstance("PKCS12")  -> đối tượng chưa load
        |
        v
KeyStore.load(...)  -> đọc file hoặc khởi tạo theo provider
        |
   lỗi / thành công
        |
        v
KeyStore.getEntry(alias, protection)
        |
        v
key/certificate để dùng cho nghiệp vụ
        |
        v
KeyStore.store(...) nếu có thay đổi cần ghi lại
~~~

Cách truyền thống trong một method có đủ import và các biến Path/char[]:

~~~java
KeyStore ks = KeyStore.getInstance("PKCS12");
try (InputStream in = Files.newInputStream(path)) {
    ks.load(in, password);
}
KeyStore.Entry entry = ks.getEntry(
        "service-key", new KeyStore.PasswordProtection(password));
~~~

**Đây là mảnh code minh họa**, không phải chương trình đầy đủ. Khi tạo mới một kho rỗng, có thể dùng **ks.load(null, password)** với implementation phù hợp; để mở **kho đã tồn tại**, phải thật sự cung cấp file/stream hoặc cấu hình provider. **KeyStore.getInstance("PKCS12") không tự đọc file**. Sau khi đọc entry, phải bảo vệ key và quyền sử dụng, không chỉ bảo vệ password mở file.

## 3. KeyStore.Builder: cấu hình nguồn kho và trì hoãn load

**KeyStore.Builder** là abstract nested class, tạo qua các static factory **newInstance**. Builder giữ thông tin để instantiate/load KeyStore và cung cấp protection cho entry. Nó hữu ích khi chưa muốn mở hết mọi file, chưa muốn hỏi password sớm hoặc framework cần danh sách các nguồn khóa.

| Factory | Vai trò | Điều cần chú ý |
| --- | --- | --- |
| **newInstance(KeyStore, ProtectionParameter)** | Bọc KeyStore đã khởi tạo cùng protection. | Nếu KeyStore chưa được load thì không hợp lệ; getKeyStore trả lại object được bọc. |
| **newInstance(String type, Provider, File, ProtectionParameter)** | Mô tả KeyStore của **file hiện có**. | Lần getKeyStore đầu mới mở/load; các lần sau trả cùng instance. |
| **newInstance(File, ProtectionParameter)** | Tự dò loại kho từ file (Java 9+). | File phải hợp lệ; kiểm thử khả năng nhận dạng. |
| **newInstance(String type, Provider, ProtectionParameter)** | Tạo KeyStore type cụ thể, load bằng LoadStoreParameter chứa protection. | Mỗi getKeyStore tạo object mới theo API; provider phải hỗ trợ kiểu tham số. |

Tham số **Provider provider = null** nghĩa dùng lựa chọn provider của JCA, không có nghĩa là không bảo vệ khóa. Pin provider trong yêu cầu HSM/FIPS hoặc tương thích cụ thể, nhưng phải kiểm tra độ di động khi triển khai.

### 3.1. Builder file-based có lazy loading và cache instance

~~~text
Builder.newInstance("PKCS12", null, existingFile, protection)
                  |
                  | Chưa gọi load
                  v
           builder.getKeyStore()
                  |
              load file lần đầu
                  |
                  v
           KeyStore đã initialized
                  |
       +----------+----------------------+
       |                                 |
builder.getKeyStore()               builder.getProtectionParameter(alias)
lần sau: cùng KeyStore               sau getKeyStore mới hợp lệ
~~~

Theo contract của Java 17:

- File-based Builder **getKeyStore() lần đầu** tạo/load; **những lần sau trả cùng đối tượng**. Nếu lần đầu thất bại bằng KeyStoreException thì các lần sau cũng báo lỗi, không tự thử lại.
- **getProtectionParameter(alias)** phải gọi **sau getKeyStore()**; nếu không có thể nhận IllegalStateException.
- File-based Builder trả **PasswordProtection** chứa mật khẩu dùng khi load file. Nó **không tự biết mật khẩu khác** được dùng để bảo vệ một entry cụ thể.
- Nếu một entry có password riêng khác password file, việc **getEntry** có thể cần PasswordProtection khác đúng entry hoặc cơ chế chuyên biệt tùy provider.
- Một Builder đã load **không tự hot reload file** khi file/secret thay đổi. Muốn rotation, tổ chức lại vòng đời đối tượng và tạo builder mới.
- Builder **không làm KeyStore mutable thread-safe** để đồng thời thêm/xóa entry hoặc store.

### 3.2. Chọn Builder hay load trực tiếp?

**Dùng Builder** khi framework nhận một tập nguồn khóa, khi lazy loading giúp giảm nhu cầu password sớm, hoặc khi callback/provider khác nhau. **Dùng KeyStore.getInstance + load(InputStream,char[])** khi ứng dụng chỉ mở một file một lần lúc startup và không cần abstraction Builder. Cả hai đều là API hợp lệ; chọn cách đơn giản đáp ứng ownership, provider và lifecycle.

## 4. Bảo vệ entry: ProtectionParameter và PasswordProtection

**KeyStore.ProtectionParameter** mô tả cơ chế mở/bảo vệ entry, và một số API dùng nó khi load/store. Loại phổ biến là **KeyStore.PasswordProtection**, nhận **char[]**.

Trong method có nguồn password tin cậy:

~~~java
char[] password = readSecretFromTrustedSource();
KeyStore.PasswordProtection protection =
        new KeyStore.PasswordProtection(password);
try {
    // Dùng protection để load keystore hoặc lấy entry.
} finally {
    protection.destroy();
    Arrays.fill(password, '\0');
}
~~~

**Mảnh code này không độc lập** vì readSecretFromTrustedSource là trách nhiệm của ứng dụng; hai chương trình bên dưới hoàn toàn runnable và minh họa vòng đời bằng password test.

### 4.1. Constructor clone char[] và vì sao cần destroy()

Java 17 **PasswordProtection(char[])** **clone** mảng password đầu vào. Điều này có hai hệ quả:

1. Xóa mảng **password của caller** bằng Arrays.fill không tự xóa **bản sao** bên trong PasswordProtection.
2. **PasswordProtection.destroy()** xóa password nội bộ; **isDestroyed()** kiểm tra trạng thái và **getPassword() sau destroy** phát sinh IllegalStateException.

Ngược lại, **getPassword() trả tham chiếu tới mảng bên trong**, không phải một bản sao an toàn để phát tán. Không thay đổi mảng trả về tùy tiện; không cho thread khác truy cập; hủy object khi chắc chắn builder/getEntry/provider không còn dùng nó.

**new PasswordProtection(password, protectionAlgorithm, protectionParameters)** có thể chỉ định thuật toán/params để bảo vệ entry ở provider hỗ trợ. Không tự cho rằng mọi KeyStore type hỗ trợ mọi thuật toán bảo vệ theo tên; cần kiểm thử.

### 4.2. Mật khẩu file và mật khẩu entry không đồng nghĩa

Một sơ đồ có thể dùng cùng mật khẩu khi tạo file và setEntry, như ví dụ runnable để đơn giản hóa. Trong production, có thể có:

~~~text
Keystore:
  storePassword ---> bảo vệ/kiểm tra integrity file
  alias A -------> entryPasswordA ---> private key A
  alias B -------> entryPasswordB ---> private key B
~~~

Tuy nhiên **không phải provider nào cũng cho dùng password riêng từng entry**. Khi chạy Builder file-based, **builder.getProtectionParameter(alias)** trả protection gắn với password đã dùng load file, không tự lấy password riêng A/B. Nếu cần tách, dùng **getEntry(alias, new PasswordProtection(entryPassword))** với provider có hỗ trợ, hoặc triển khai strategy nhập PIN/password theo provider.

### 4.3. PasswordProtection không phải password hashing

Password của keystore dùng làm **protection secret** cho dữ liệu/entry; nó không phải cùng một thứ với **password verifier cho người dùng đăng nhập**. Không đem PBKDF2 output của password người dùng dùng như secret keystore tùy tiện; không dùng keystore để lưu database password plaintext. Các profile PKCS12 có thể dùng password-based encryption/MAC theo định dạng và provider; muốn biết thuật toán/cost cụ thể phải kiểm tra implementation/cấu hình của môi trường.

## 5. CallbackHandlerProtection: yêu cầu mật khẩu khi cần

**KeyStore.CallbackHandlerProtection** giữ một **javax.security.auth.callback.CallbackHandler**. File-based Builder có thể gọi handler với **PasswordCallback** khi thực sự cần mật khẩu để mở kho. Đây là cách trì hoãn việc lấy secret cho tới lúc load, hữu ích khi secret nằm trong một nguồn được quản lý hoặc có tương tác với người vận hành.

~~~text
Builder.newInstance(file, new CallbackHandlerProtection(handler))
                        |
                getKeyStore lần đầu
                        |
                 handler.handle(...)
                        |
             PasswordCallback.setPassword(...)
                        |
                  KeyStore.load
~~~

**Điều quan trọng:**

- Callback không biến password thành “không cần giữ trong RAM”; lúc sử dụng, secret vẫn đi qua buffer trong process.
- Handler cần xử lý đúng loại callback, hủy mảng tạm sau khi truyền cho PasswordCallback (API setPassword sao chép dữ liệu), và xử lý cancellation/timeout.
- Nếu provider yêu cầu loại callback khác, phải kiểm tra khả năng hỗ trợ thay vì luôn cast sang PasswordCallback.
- Không dùng System.console() trực tiếp trong request handler của web server chạy không tương tác; cần kiến trúc secret source có quyền, giới hạn truy cập và audit phù hợp.
- Không lưu password dài hạn trong lambda/field singleton nếu có thể tránh. Handler chỉ nên lấy secret khi cần và xóa bản sao khi hoàn thành.
- File-based Builder API chấp nhận **PasswordProtection hoặc CallbackHandlerProtection**; các implementation ProtectionParameter khác có thể bị IllegalArgumentException.
- Việc cấp password hoặc PIN của HSM không đồng nghĩa khóa private có thể được xuất. Key handle và chính sách usage của token vẫn có hiệu lực.

Một snippet dùng CallbackHandler trong method đã nhận một **Supplier<char[]> secretSupplier** đáng tin; đây là mảnh code minh họa chứ không phải chương trình độc lập:

~~~java
CallbackHandler handler = callbacks -> {
    for (Callback cb : callbacks) {
        if (!(cb instanceof PasswordCallback pc)) {
            throw new UnsupportedCallbackException(cb);
        }
        char[] supplied = secretSupplier.get();
        try {
            pc.setPassword(supplied);
        } finally {
            Arrays.fill(supplied, '\0');
        }
    }
};
KeyStore.ProtectionParameter param =
        new KeyStore.CallbackHandlerProtection(handler);
~~~

Các import liên quan thuộc javax.security.auth.callback.*, java.security.KeyStore, java.util.Arrays và java.util.function.Supplier. **Supplier được ứng dụng cài từ nguồn secret phù hợp**. PasswordCallback còn phải được clear đúng thời điểm bởi thành phần quản lý callback/provider; không giả định đoạn minh họa đã xóa hết bản sao mọi nơi.

## 6. LoadStoreParameter: trừu tượng hóa thông số load và store

**KeyStore.LoadStoreParameter** là interface nested của KeyStore, có một method:

~~~java
KeyStore.ProtectionParameter getProtectionParameter();
~~~

Nó có thể được triển khai bằng một anonymous class/lambda cho Java 17:

~~~java
KeyStore.PasswordProtection protection =
        new KeyStore.PasswordProtection(password);
KeyStore.LoadStoreParameter parameters = () -> protection;
~~~

**Mảnh code này nằm trong method có char[] password đã khai báo**, không phải main độc lập. **LoadStoreParameter không phải file path, không chứa ciphertext, và không tự cung cấp một chuẩn chung để load bất kỳ định dạng**. Provider đọc tham số và quyết định nó có được hỗ trợ hay không.

### 6.1. Phân biệt overload của KeyStore

| API | Nguồn dữ liệu | Dùng khi nào | Bẫy thường gặp |
| --- | --- | --- | --- |
| **load(InputStream, char[])** | Dữ liệu lấy từ stream (null để khởi tạo mới) | File-based JKS/PKCS12 phổ biến | Cần đóng stream; password không tự được xóa. |
| **load(LoadStoreParameter)** | Theo provider + protection parameter | Provider hiểu cách tải dữ liệu, có thể là token hoặc khởi tạo mới | **Không có InputStream trong interface**, không giả định tự mở file. |
| **store(OutputStream, char[])** | Ghi bytes vào stream | File-based keystore, xuất bản ghi mới | Cần quản lý ghi file an toàn và quyền đọc, không chỉ gọi store. |
| **store(LoadStoreParameter)** | Theo provider + param | Token/provider có cơ chế lưu riêng | Có thể ném IllegalArgumentException/UnsupportedOperationException nếu provider không hỗ trợ. |
| **KeyStore.Builder.newInstance(type, provider, protection)** | Tạo KeyStore và gọi load với LoadStoreParameter bọc protection | Khi lifecycle do provider quản lý | Đọc contract: mỗi getKeyStore tạo mới cho overload này, khác file Builder. |

**Cảnh báo:** gọi **load(parameters)** với một LoadStoreParameter do bạn tự tạo **không có nghĩa PKCS12 tự tìm file**. Nếu chỉ có password protection, thiếu đường dẫn file là bình thường. Tùy provider, điều này có thể dùng để khởi tạo kho rỗng hoặc mở token; muốn đọc file cụ thể, dùng load(InputStream, char[]) hoặc file Builder phù hợp.

### 6.2. LoadStoreParameter khác ProtectionParameter thế nào?

~~~text
ProtectionParameter = thông tin/phương pháp bảo vệ
                    PasswordProtection / CallbackHandlerProtection / provider-specific

LoadStoreParameter = một container/contract dành cho load/store,
                     có method getProtectionParameter()

KeyStore.load(parameters) -> provider diễn giải container đó
KeyStore.getEntry(alias, protection) -> protection của entry cụ thể
~~~

Không thay hai interface cho nhau trong method signature. Builder file-based nhận **ProtectionParameter**, trong khi **KeyStore.load(param)** yêu cầu **LoadStoreParameter**. Một số provider có LoadStoreParameter chuyên dụng (ví dụ **DomainLoadStoreParameter** phục vụ cấu hình domain), và không phải mọi implementation đều chấp nhận một lambda chứa PasswordProtection.

### 6.3. Nên dùng khi nào?

- Provider keystore cần PIN/mật khẩu hoặc cấu hình load/store không thể diễn tả bằng InputStream.
- Hệ thống dùng token/hardware hoặc nhiều keystore có định nghĩa domain/provider-specific.
- Framework muốn một abstraction chung cho khởi tạo kho với protection, nhưng vẫn phải kiểm tra support.
- Trong file PKCS12 đơn giản, **load(InputStream,char[])** và **store(OutputStream,char[])** thường rõ ràng, dễ debug hơn.

**Đừng bắt UnsupportedOperationException rồi tự chuyển sang lưu plain text/key bytes**. Phải chọn đúng provider/method và chính sách bảo vệ.

## 7. Java 17+ — Demo 1: KeyStore.Builder mở PKCS12 và đọc SecretKeyEntry

Chương trình sau là ví dụ **độc lập**, tạo một keystore PKCS12 tạm chứa khóa AES 128 bit, lưu vào file, sau đó dùng **Builder.newInstance(type, null, file, PasswordProtection)** để load. Nó kiểm tra alias, nội dung SecretKey, việc file Builder cache instance và sai password.

Tạo file **BuilderKeyStoreDemo.java**:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class BuilderKeyStoreDemo {
    public static void main(String[] args) throws Exception {
        char[] storePassword = "demo-password-not-for-production".toCharArray();
        char[] wrongPassword = "wrong-password".toCharArray();
        Path tempFile = Files.createTempFile("builder-demo-", ".p12");
        KeyStore.PasswordProtection writeProtection = null;
        KeyStore.PasswordProtection builderProtection = null;
        KeyStore.PasswordProtection entryProtection = null;
        try {
            KeyStore created = KeyStore.getInstance("PKCS12");
            created.load(null, storePassword);

            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(128);
            SecretKey aesKey = generator.generateKey();

            writeProtection = new KeyStore.PasswordProtection(storePassword);
            created.setEntry("service-aes",
                    new KeyStore.SecretKeyEntry(aesKey), writeProtection);
            try (OutputStream out = Files.newOutputStream(tempFile)) {
                created.store(out, storePassword);
            }

            builderProtection = new KeyStore.PasswordProtection(storePassword);
            KeyStore.Builder builder = KeyStore.Builder.newInstance(
                    "PKCS12", null, tempFile.toFile(), builderProtection);
            KeyStore loaded = builder.getKeyStore();
            entryProtection = (KeyStore.PasswordProtection)
                    builder.getProtectionParameter("service-aes");
            KeyStore.SecretKeyEntry restored = (KeyStore.SecretKeyEntry)
                    loaded.getEntry("service-aes", entryProtection);

            System.out.println("Alias tồn tại: " +
                    loaded.containsAlias("service-aes"));
            System.out.println("Khóa khôi phục đúng: " +
                    MessageDigest.isEqual(aesKey.getEncoded(),
                            restored.getSecretKey().getEncoded()));
            System.out.println("File Builder cache cùng KeyStore: " +
                    (loaded == builder.getKeyStore()));

            KeyStore.PasswordProtection wrongProtection =
                    new KeyStore.PasswordProtection(wrongPassword);
            try {
                KeyStore.Builder wrongBuilder = KeyStore.Builder.newInstance(
                        "PKCS12", null, tempFile.toFile(), wrongProtection);
                boolean rejected = false;
                try {
                    wrongBuilder.getKeyStore();
                } catch (KeyStoreException expected) {
                    rejected = true;
                }
                System.out.println("Sai mật khẩu bị từ chối: " + rejected);
            } finally {
                wrongProtection.destroy();
            }
        } finally {
            if (entryProtection != null) entryProtection.destroy();
            if (builderProtection != null) builderProtection.destroy();
            if (writeProtection != null) writeProtection.destroy();
            Arrays.fill(storePassword, '\0');
            Arrays.fill(wrongPassword, '\0');
            Files.deleteIfExists(tempFile);
        }
    }
}
~~~

Biên dịch/chạy với JDK 17 trở lên (provider hỗ trợ lưu AES SecretKey trong PKCS12):

~~~bash
javac --release 17 -encoding UTF-8 BuilderKeyStoreDemo.java
java BuilderKeyStoreDemo
~~~

Kết quả trên JDK đã kiểm thử:

~~~text
Alias tồn tại: true
Khóa khôi phục đúng: true
File Builder cache cùng KeyStore: true
Sai mật khẩu bị từ chối: true
~~~

### 7.1. Giải thích từng bước

1. **KeyStore.getInstance("PKCS12")** chọn type, **load(null, storePassword)** khởi tạo kho mới.
2. **KeyGenerator.getInstance("AES")** tạo SecretKey test 128 bit; không hardcode key bytes trong ứng dụng.
3. **setEntry("service-aes", SecretKeyEntry, PasswordProtection)** đặt entry vào kho; password entry dùng cùng password file **chỉ để đơn giản hóa demo**.
4. **store(OutputStream, char[])** ghi kho ra file; việc đóng stream được đảm bảo bởi try-with-resources.
5. **Builder.newInstance(..., file, ...)** nhận file đang tồn tại; **getKeyStore** thực hiện tải và kiểm tra password, **getProtectionParameter** được gọi sau khi load.
6. **getEntry** khôi phục entry AES; MessageDigest.isEqual dùng để assert bytes trong bài test; không nên in raw secret key.
7. **getKeyStore hai lần trả cùng object** đúng contract file Builder; code không chứng minh auto reload khi file thay đổi.
8. **Sai password** khi gọi getKeyStore ở builder mới được bắt bằng KeyStoreException; lỗi cần được vận hành phân loại đúng và không log password.
9. **destroy** cho ba đối tượng PasswordProtection và **Arrays.fill** cho hai char[] do demo sở hữu. Cuối cùng xóa file tạm.

**An toàn production:** password literal trong chương trình chỉ dùng để làm demo chạy độc lập. Không copy cách hardcode password vào dự án thật. Temporary file không được xem là vị trí lưu khóa production: file permissions, backup, monitoring, filesystem race và key lifecycle đều cần được quản trị theo hệ thống.

### 7.2. Cần nhớ gì khi đổi alias hoặc password entry?

Alias có thể bị provider chuẩn hóa/case-fold và không nên tạo hai alias chỉ khác chữ hoa/thường. Với file Builder, getProtectionParameter(alias) không biết password riêng cho mỗi alias; nếu một entry cần password độc lập và type/provider cho phép, cấp PasswordProtection phù hợp khi gọi getEntry. Các ứng dụng quy mô lớn nên chuẩn hóa alias và key version, kiểm soát quyền của từng alias trong service thay vì cho client gửi tên alias tùy ý.

## 8. Java 17+ — Demo 2: LoadStoreParameter và PasswordProtection

Ví dụ thứ hai tạo một **LoadStoreParameter** bằng lambda trả về PasswordProtection, truyền cho **KeyStore.load(parameters)**, tạo SecretKeyEntry rồi làm **round-trip** qua **store(OutputStream,char[])** và **load(InputStream,char[])**.

**Phạm vi provider:** trên JDK PKCS12 provider đã kiểm thử, **load(LoadStoreParameter)** với PasswordProtection có thể khởi tạo kho mới rỗng. Đây không phải lời hứa mọi provider/type đều chấp nhận đúng loại parameter; một provider khác có thể từ chối bằng IllegalArgumentException hoặc UnsupportedOperationException. **LoadStoreParameter không mang file path**, nên chương trình dùng ByteArrayOutputStream để chứng minh lưu/đọc bytes theo overload phổ biến.

Tạo file **LoadStoreParameterDemo.java**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class LoadStoreParameterDemo {
    public static void main(String[] args) throws Exception {
        char[] password = "demo-only-password".toCharArray();
        KeyStore.PasswordProtection protection =
                new KeyStore.PasswordProtection(password);
        try {
            KeyStore.LoadStoreParameter parameters = () -> protection;
            KeyStore created = KeyStore.getInstance("PKCS12");
            created.load(parameters); // Supported by the tested JDK PKCS12 provider.
            System.out.println("Kho mới trống: " + (created.size() == 0));

            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(128);
            SecretKey original = generator.generateKey();
            created.setEntry("app-key",
                    new KeyStore.SecretKeyEntry(original), protection);

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            created.store(bytes, password);

            KeyStore restored = KeyStore.getInstance("PKCS12");
            restored.load(
                    new ByteArrayInputStream(bytes.toByteArray()), password);
            KeyStore.SecretKeyEntry loaded = (KeyStore.SecretKeyEntry)
                    restored.getEntry("app-key", protection);
            System.out.println("Round-trip đúng: " +
                    MessageDigest.isEqual(original.getEncoded(),
                            loaded.getSecretKey().getEncoded()));
            System.out.println("Protection có mật khẩu: " +
                    (parameters.getProtectionParameter() instanceof
                            KeyStore.PasswordProtection));
        } finally {
            protection.destroy();
            Arrays.fill(password, '\0');
        }
        System.out.println("PasswordProtection đã hủy: " +
                protection.isDestroyed());
    }
}
~~~

~~~bash
javac --release 17 -encoding UTF-8 LoadStoreParameterDemo.java
java LoadStoreParameterDemo
~~~

Kết quả:

~~~text
Kho mới trống: true
Round-trip đúng: true
Protection có mật khẩu: true
PasswordProtection đã hủy: true
~~~

### 8.1. Quan sát API nào trong ví dụ?

- **LoadStoreParameter parameters = () -> protection** là cách viết ngắn của một implementation interface có method getProtectionParameter().
- **created.load(parameters)** yêu cầu provider diễn giải protection; đoạn này chỉ khởi tạo kho rỗng trong môi trường đã chạy, **không đọc file có sẵn**.
- **store(bytes, password)** dùng overload OutputStream với char[]; **không phải store(LoadStoreParameter)**. Việc provider chấp nhận **load(parameters)** không chứng minh provider đó hỗ trợ **store(parameters)**.
- **restored.load(ByteArrayInputStream, password)** chứng minh dạng PKCS12 byte có thể được đọc lại trên provider đã kiểm thử.
- **getEntry("app-key", protection)** truy xuất SecretKeyEntry; test dùng MessageDigest.isEqual để so sánh bytes và chỉ in true/false.
- **PasswordProtection.destroy()** hủy bản sao password bên trong protection; **Arrays.fill(password, '\0')** hủy mảng caller.
- **ByteArrayOutputStream** trong demo vẫn giữ bytes keystore đã mã hóa/bảo vệ trong RAM và không tự được xóa; ở production cần quản lý buffer, backup và quyền đọc.

### 8.2. Tránh suy diễn từ một test thành công

Test trên cùng JVM/provider với AES SecretKey được tạo bằng KeyGenerator chỉ chứng minh tổ hợp cụ thể đó hoạt động. Đừng khẳng định:

- Mọi PKCS12 provider hỗ trợ SecretKeyEntry hoặc mọi thuật toán secret key.
- Mọi LoadStoreParameter có thể đọc một file được chỉ định bằng password.
- Mọi provider đều dùng cùng password cho store và entry.
- Có thể xuất private key hardware/HSM qua getEncoded().
- File keystore tự an toàn chỉ vì password dài; còn có OS permissions, filesystem policy, backup và key lifecycle.

## 9. Nguồn password trong ứng dụng Java production

Một quyết định kiến trúc quan trọng nằm ở **nơi lấy password và ai được phép dùng nó**.

| Nguồn password | Ưu điểm | Cần cân nhắc |
| --- | --- | --- |
| **Secret manager / Vault / KMS** | Policy truy cập, rotation, audit, cấp secret theo identity workload | Sẵn sàng dịch vụ, cache ngắn hạn, quyền IAM, không log response. |
| **HSM/PKCS11 token** | Private key có thể không rời hardware; PIN/slot/session policy | Đặc thù provider, key handle, PIN, timeout, session pool, khả năng export hạn chế. |
| **Password nhập lúc startup** | Tránh secret cố định trong file config | Không phù hợp headless/auto-scaling nếu không có nhập bí mật an toàn. |
| **File secret mount có ACL hạn chế** | Tích hợp deployment đơn giản | Ownership/ACL/volume/backup/rotation và secret tồn tại trên đĩa. |
| **Environment variable** | Dễ triển khai runtime/container | Có thể lộ qua dump/process introspection, diagnostic, quyền quản trị; không an toàn tuyệt đối. |
| **Source code, Git, CLI arguments, log** | Không có | **Không dùng để lưu/đưa password production** vì dễ lộ qua repository/history/process listing/log. |

**Ưu tiên workload identity + secret manager** hoặc KMS/HSM khi phù hợp. Nếu dùng env var hoặc file mount, vẫn coi là secret cần phân quyền và rotation; không in giá trị để debug.

### 9.1. Vì sao char[] hữu ích nhưng không phải phép màu?

char[] cho caller tự **Arrays.fill** phần bộ nhớ mình sở hữu, khác String bất biến và không thể xóa tại chỗ. Tuy vậy:

- HTTP framework, config parser, callback hoặc provider có thể đã tạo **bản sao** khác.
- PasswordProtection constructor clone char[]; phải **destroy() riêng**.
- getPassword() của PasswordProtection trả tham chiếu nội bộ, nên thành phần khác có thể truy cập khi password còn sống.
- JVM GC, native memory, crash dumps và logging có đặc tính riêng; **không thể bảo đảm xóa sạch mọi bản sao** chỉ bằng Arrays.fill một lần.
- char[] không thay TLS, IAM, entropy password hoặc policy dùng HSM.

Quy tắc: **hạn chế bản sao, thời gian sống và nơi ghi**, dùng finally để clear mảng thuộc sở hữu, destroy protection đúng thời điểm và không lưu password dài hạn trong singleton.

### 9.2. Lifecycle khi dùng Builder

~~~text
SecretSource (phân quyền)
      |
      v
char[] password tạm
      |
      v
PasswordProtection clones char[]
      |
      v
Builder.newInstance(..., protection)
      |
      v
getKeyStore + getEntry (hoàn tất công việc cần secret)
      |
      v
destroy các PasswordProtection đang sở hữu
      |
      v
Arrays.fill mảng caller
      |
      v
Đóng streams, quản lý key handle và file/session
~~~

**Không destroy protection khi Builder còn cần password để load sau này.** Vì Builder có thể lazy-load, phải xác định thời điểm cuối cùng truy cập keystore. Nếu giữ Builder dài hạn, chọn chiến lược secret retrieval/lifecycle tương thích, có thể dùng callback/lazy secret source thay vì tạo PasswordProtection rồi destroy ngay sau cấu hình.

## 10. Load lúc khởi động hay khi có request?

### 10.1. Load lúc startup

Ưu điểm: phát hiện password/file/cert hỏng trước traffic, giảm request latency. Nhược điểm: process cần secret ngay khi khởi động; rotation cần reload đúng.

Workflow:

1. Nhận path, keystore type, alias và key version từ config đáng tin, không từ request trực tiếp.
2. Lấy password từ secret source được phân quyền.
3. Load kho một lần, xác minh alias/type/certificate chain và key usage.
4. Công bố **key handle/service abstraction** sau khi bootstrap thành công.
5. Không log password; xóa buffers khi không cần.
6. Khi rotate, tải **instance mới**, kiểm tra rồi chuyển tham chiếu atomically; giải phóng phiên/handle cũ theo policy.

### 10.2. Lazy load bằng Builder

Ưu điểm: không tải mọi kho sớm, có thể trì hoãn callback. Nhược điểm: lần đầu có I/O, xác thực password hoặc lỗi vận hành đúng lúc request; phải có timeout, monitoring và retry tại lớp điều phối.

Builder **không phải cache secret** và không tự nhận thông báo file thay đổi. Nếu file/password rotate, builder cũ có thể trả **object đã load**; cần xây dựng lại builder/KeyStore hoặc điều phối restart.

### 10.3. Concurrent access

**KeyStore** là container mutable. Nhiều thread cùng setEntry/deleteEntry/store cần được **phối hợp** để tránh lost updates và file không nhất quán. Với keystore chỉ đọc sau bootstrap, xây façade chỉ cung cấp thao tác cần thiết; vẫn phải tôn trọng contract provider/HSM về concurrency và session.

**SecretKey/PrivateKey object** có thể là wrapper/provider handle; không chia sẻ một thao tác mutable đang chạy như Cipher hoặc Signature giữa các thread. KeyStore.Builder không đồng bộ hóa workflow load/rotate/use. Với HSM, có thể cần session pool, timeout và backpressure; retry PIN sai liên tục có thể khóa token.

## 11. Lưu keystore an toàn và quản lý file

File được bảo vệ bằng password vẫn có thể bị sao chép. Cần:

- **File permissions:** hạn chế owner/ACL cho đúng process, không cấp mọi user trên host.
- **Backup:** bản sao được bảo vệ tương đương, có kế hoạch restore và truy xuất password đúng.
- **Atomic update:** ghi file tạm với quyền phù hợp, flush/fsync theo yêu cầu rồi rename atomically nếu filesystem hỗ trợ; nếu crash khi ghi trực tiếp file gốc có thể hỏng kho. Kiểm tra trường hợp rename không atomic và bản sao dự phòng.
- **Key rotation:** chuẩn hóa alias, key ID và phiên bản; không lẫn entry của hai generation.
- **Password rotation:** đổi protection/re-encrypt bằng workflow đúng, kiểm tra kho mới trước khi loại bỏ bản cũ; không phá khả năng đọc dữ liệu lịch sử.
- **Audit:** ghi identity workload, alias, operation và kết quả trong phạm vi an toàn, không log key/password.
- **Environment isolation:** không tái sử dụng password/keys từ dev/test sang production.

Demo 1 dùng **Files.createTempFile** rồi xóa để không chạm file thật; demo 2 dùng **ByteArrayOutputStream** để không ghi cố định. Hai demo không phải quy trình bảo vệ file production hoàn chỉnh.

## 12. Xử lý sai password, sai alias và lỗi provider

Không nên gom mọi lỗi thành một dòng “sai mật khẩu” trong log nội bộ: một vấn đề về file, provider hoặc cấu hình cần cách sửa khác với sai password.

| Tình huống | Kết quả thường thấy | Hướng xử lý |
| --- | --- | --- |
| **File không tồn tại khi tạo file Builder** | IllegalArgumentException ngay tại newInstance(file overload) | Kiểm tra path, quyền và deployment; không tạo file trống giả rồi tiếp tục. |
| **Password file sai** | getKeyStore() của file Builder báo KeyStoreException; load trực tiếp thường IOException, có thể có cause UnrecoverableKeyException | Không retry password sai vô hạn; không log password. |
| **Password entry sai** | getEntry() có thể báo UnrecoverableEntryException | Phân biệt file đã mở được với entry chưa được giải mã. |
| **Alias không tồn tại** | getEntry(alias,...) trả null theo contract khi không có entry | Kiểm tra alias theo cấu hình, không cast null; không tự tạo khóa thay thế. |
| **getProtectionParameter trước getKeyStore** | IllegalStateException | Tuân thủ lifecycle của Builder. |
| **Builder từ KeyStore chưa load** | IllegalArgumentException | Load KeyStore trước khi bọc Builder. |
| **Loại ProtectionParameter không hợp lệ với file Builder** | IllegalArgumentException | Dùng PasswordProtection hoặc CallbackHandlerProtection được hỗ trợ. |
| **Không hỗ trợ load(LoadStoreParameter)** | IllegalArgumentException, UnsupportedOperationException hoặc lỗi provider | Chọn overload đúng theo tài liệu provider, không đổi sang lưu plaintext. |
| **KeyStore không hỗ trợ loại entry/algorithm** | KeyStoreException hoặc lỗi provider phù hợp | Kiểm tra KeyStore type, provider và key algorithm; không tự làm yếu mật mã. |
| **PasswordProtection đã destroy** | getPassword() ném IllegalStateException | Không hủy trước khi Builder/operation cần password; tạo protection mới từ nguồn bí mật khi cần. |
| **File bị hỏng hoặc sai format** | IOException, CertificateException, NoSuchAlgorithmException tùy tình huống | Giữ bản sao dự phòng, kiểm tra integrity và format, không bỏ qua lỗi. |

Nếu Builder file-based gặp lỗi trong lần tải đầu, **đừng chỉ gọi lại getKeyStore trên cùng Builder**: theo API, lỗi được giữ lại. Sửa nguyên nhân, lấy lại secret và tạo Builder mới; các cơ chế retry/health check nên thuộc tầng orchestration có giới hạn và audit.

Một thông báo cho người dùng cuối thường chỉ cần lỗi chung phù hợp ngữ cảnh; log vận hành có thể ghi path logic/alias/key ID/error class **không nhạy cảm**, không ghi password/PIN, private key hoặc stream dump.

## 13. Thiết kế ứng dụng thực tế: dịch vụ ký số

Giả sử service Java phải ký thông báo giao dịch bằng private key trong KeyStore. Có thể tổ chức:

~~~text
Deployment configuration
    | type=PKCS12, path, alias, keyVersion (không chứa password)
    v
Secret manager / workload identity
    | cấp password theo quyền
    v
Key loading component
    | KeyStore.Builder hoặc KeyStore.load
    | getEntry(alias, entryProtection)
    v
SigningService
    | kiểm tra key usage/policy, chuẩn hóa message bytes
    | Signature instance riêng từng request
    v
signature + algorithm/keyVersion metadata
~~~

Các bất biến:

1. **Đường dẫn, type, alias và thuật toán** đến từ cấu hình được phép, không do request tùy ý chỉ định.
2. **Secret retrieval** qua nguồn được phân quyền, với timeout, audit và cache phù hợp; không lưu password trong code.
3. **KeyStore được load** thành công và entry type được kiểm tra trước khi dùng. PrivateKeyEntry còn cần certificate chain và trust/usage policy thích hợp.
4. **Mỗi request** tạo một Signature instance phù hợp, không dùng một Signature mutable chung khi ký đồng thời.
5. **Key version** được gắn với signature để phía xác minh chọn public key đúng; rotation có chiến lược chuyển tiếp/thu hồi.
6. **Nếu khóa/phần cứng lỗi**, từ chối ký thay vì tự sinh khóa mới làm thay đổi danh tính hay chuyển sang secret hardcoded.
7. **Không xuất private key** chỉ để “kiểm tra cho chắc”: với HSM, getEncoded có thể trả null, và test byte[] của demo không áp dụng cho non-exportable key.

### 13.1. Ví dụ ứng dụng mã hóa bằng secret key

Một dịch vụ dùng SecretKeyEntry cho AES-GCM cần lưu ý thêm: keystore lưu **khóa**, không tự sinh nonce/IV đúng cho từng message, không mã hóa dữ liệu thay bạn. Với AES-GCM phải dùng IV duy nhất theo **cùng khóa** ở mỗi phép mã hóa, kiểm tra tag khi giải mã và thiết kế key rotation theo record version. Builder không tự mang lại bảo đảm đó.

### 13.2. Khi nào dùng CallbackHandlerProtection?

Khi secret không được lấy sớm và các consumer muốn load key lazily, cấu hình CallbackHandler có thể phù hợp. Tuy nhiên lifecycle của callback và rotation phải được đồng bộ với thời điểm file Builder thực sự load, đồng thời tránh prompt GUI/terminal trong web server. Với token/HSM, hãy tuân thủ hướng dẫn provider cụ thể về việc cấp PIN, session và password callback.

## 14. Kế hoạch kiểm thử cho Builder, LoadStoreParameter và password

| Test | Thao tác | Kỳ vọng |
| --- | --- | --- |
| **Tạo kho trống** | KeyStore.getInstance("PKCS12") + load(null,password) | Kho initialized, chưa có entry. |
| **Ghi SecretKeyEntry** | Sinh AES key, setEntry với password hợp lệ, store | Tạo file/byte PKCS12 dùng được trên provider đã chọn. |
| **Builder lazy load** | Tạo file Builder rồi gọi getKeyStore | getKeyStore thực sự mở file. |
| **Builder cache** | Gọi getKeyStore hai lần với file overload | Cùng KeyStore instance. |
| **getProtectionParameter lifecycle** | Gọi trước và sau getKeyStore | Trước load lỗi; sau load có protection phù hợp. |
| **Khôi phục entry** | getEntry(alias, protection) | Đúng key/certificate theo entry type. |
| **Sai file password** | Builder mới với mật khẩu sai rồi getKeyStore | Bị từ chối; không xuất khóa. |
| **Sai entry password** | Kho load đúng, getEntry với password sai | Từ chối hoặc exception phù hợp provider. |
| **Alias thiếu** | getEntry alias không có | Xử lý null; không dùng key giả hoặc fallback. |
| **PasswordProtection lifecycle** | destroy rồi isDestroyed/getPassword | isDestroyed true; getPassword không còn cho truy cập. |
| **LoadStoreParameter** | Custom lambda -> PasswordProtection; load(params) | Hành vi được xác định theo provider, không suy ra mọi provider hỗ trợ. |
| **Store/load round-trip** | store(OutputStream,char[]) rồi load(InputStream,char[]) | Khôi phục đúng trên môi trường được kiểm thử. |
| **Corrupt/truncated file** | File sai format/đứt giữa | Lỗi có kiểm soát; không tự khởi tạo mới để ghi đè bản cũ. |
| **Provider matrix** | JDK/provider/PKCS11/FIPS khác nhau | Chỉ chọn type/algorithm/params được policy cho phép. |
| **Rotation** | Cập nhật file/password/key ID khi request đang hoạt động | Không tự hot reload Builder cũ; chuyển sang instance mới có kiểm soát. |
| **Concurrency** | Nhiều request đọc khóa và ký song song | Không trộn trạng thái, không làm mất entry hoặc hỏng store. |
| **Logging** | Giả lập mọi nhánh lỗi | Không để password, private/secret key bytes lọt log/telemetry. |

Các chương trình chạy minh họa ở phần 7–8 kiểm tra **đúng key, wrong password, file Builder cache, LoadStoreParameter và destroy**. Với production còn phải có integration test cho file permission, HSM timeout/slot, key alias/rotation, certificate chain và policies.

## 15. Sai lầm thường gặp

1. **Đặt password keystore trực tiếp trong source hoặc commit vào Git.** Mọi người có quyền đọc code/bản build có thể lấy password.
2. **Đưa password vào CLI args hoặc log** để tiện debug, làm lộ qua process listing/observability.
3. **Mở một file bằng load(null,password)** rồi nghĩ đã đọc file. Null stream khởi tạo kho rỗng theo provider.
4. **Dùng getProtectionParameter trước getKeyStore.** Sai lifecycle Builder.
5. **Giả định file Builder luôn reload khi file thay đổi.** Nó giữ cùng KeyStore sau load đầu.
6. **Giả định loại Builder không file cũng cache cùng object.** Với overload type/provider/protection, contract nói mỗi getKeyStore tạo KeyStore mới.
7. **Coi getProtectionParameter(alias) là mật khẩu riêng của mọi entry.** File Builder trả password đã dùng load kho; entry có thể cần secret khác.
8. **Tự truyền LoadStoreParameter cho mọi provider rồi mong tự tìm file.** Interface không chứa path; semantics do provider.
9. **Dùng store(LoadStoreParameter) như thay thế phổ quát cho OutputStream.** Provider có thể không hỗ trợ.
10. **Chỉ Arrays.fill char[] caller mà không destroy PasswordProtection.** Constructor đã clone password.
11. **Destroy PasswordProtection trước khi lazy Builder load.** Lúc cần mở khóa sẽ không còn password.
12. **Coi char[] bảo đảm xóa sạch mọi secret khỏi heap/native memory.** Còn bản sao trong framework, callback, dumps.
13. **Dùng cùng file, password và key cho dev/prod.** Tăng blast radius và khó rotate an toàn.
14. **Cho user request chọn alias/key ID tùy ý.** Có thể dùng nhầm khóa hoặc vượt quyền nghiệp vụ.
15. **In raw key bytes để xác nhận round-trip thành công.** Dùng kiểm thử boolean, không tiết lộ key.
16. **Lưu file mới đè file cũ trực tiếp không có chiến lược atomic/backup.** Crash có thể phá hủy kho.
17. **Tái sử dụng một Cipher/Signature mutable giữa các thread.** KeyStore không bảo đảm an toàn trạng thái của engine.
18. **Chờ HSM xuất private key khi cần ký.** HSM có thể cố ý không cho export; thực hiện operation thông qua provider/key handle.
19. **Chấp nhận cert chỉ vì nằm trong KeyStore.** Cần nguồn tin cậy, chain/usage/identity policy cụ thể.
20. **Bắt mọi lỗi rồi silently tạo khóa mới.** Có thể mất khả năng giải mã dữ liệu cũ và thay đổi danh tính ký số.

## 16. Câu hỏi tự kiểm tra

**1. KeyStore.Builder giúp tránh mở file ngay lúc newInstance hay không?**  
Có, với file Builder, việc load được kích hoạt khi gọi **getKeyStore lần đầu**. Các lần sau dùng cùng KeyStore đã load.

**2. Có được gọi builder.getProtectionParameter(alias) trước getKeyStore không?**  
Không. API có thể ném **IllegalStateException**; gọi getKeyStore trước.

**3. File Builder đã tải keystore rồi, nếu file mới xuất hiện do rotation thì builder cũ tự đọc lại không?**  
Không. Phải xây dựng chiến lược reload và thường tạo builder/keystore mới.

**4. PasswordProtection nhận char[] thì sau khi xóa mảng đầu vào có chắc secret trong protection đã bị xóa?**  
Không. Constructor clone mảng. Phải **destroy()** protection riêng sau khi không còn dùng, đồng thời xóa mảng gốc.

**5. PasswordProtection.getPassword có trả bản sao không?**  
Không. Java 17 API nói nó trả một **tham chiếu tới mảng nội bộ**; cần bảo vệ và không sửa/giữ lâu.

**6. LoadStoreParameter chỉ chứa PasswordProtection có đủ để chỉ ra file PKCS12 phải đọc không?**  
Không. Interface không mang đường dẫn; provider hoặc overload khác phải xác định nguồn dữ liệu.

**7. store(LoadStoreParameter) có chắc chạy trên mọi KeyStore type không?**  
Không. Phải kiểm tra provider hỗ trợ. Khi dùng file-based PKCS12, **store(OutputStream,char[])** rõ ràng hơn.

**8. Sai password mở kho và sai entry password có phải cùng một lỗi?**  
Không. Mở file có thể gặp IOException hoặc Builder KeyStoreException; truy cập entry có thể gặp UnrecoverableEntryException.

**9. Có thể lưu private key HSM thành file bằng privateKey.getEncoded không?**  
Không chắc và thường không được phép. Key handle không xuất được có thể trả null; dùng operation qua provider/HSM.

**10. char[] được xóa trong finally có loại bỏ mọi bản sao từ callback/HTTP framework không?**  
Không. Xóa chỉ mảng được sở hữu; cần thiết kế giảm bản sao, không log và lifecycle toàn hệ thống.

## 17. Kết luận và tài liệu chính thức

**KeyStore.Builder** tách cấu hình nguồn khóa khỏi thời điểm load, nhưng không phải hot reload hay quản lý khóa tự động. **LoadStoreParameter** cung cấp một interface cho tham số load/store, còn ý nghĩa thực tế tùy provider. **PasswordProtection** lưu bản sao password và hỗ trợ destroy; **CallbackHandlerProtection** có thể trì hoãn việc yêu cầu password nhưng không xóa rủi ro secret tồn tại trong memory.

Thiết kế ứng dụng an toàn cần kết hợp **nguồn password tin cậy, giới hạn quyền truy cập, đúng lifecycle, phân biệt store/entry protection, kiểm soát file/HSM, xử lý lỗi và rotation có kiểm thử**. Hai ví dụ Java 17 minh họa cơ chế chạy thực tế, không thay thế các biện pháp vận hành production.

Tài liệu đối chiếu:

- [Java SE 17 — KeyStore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html)
- [Java SE 17 — KeyStore.Builder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.Builder.html)
- [Java SE 17 — KeyStore.LoadStoreParameter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.LoadStoreParameter.html)
- [Java SE 17 — KeyStore.PasswordProtection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.PasswordProtection.html)
- [Java SE 17 — KeyStore.CallbackHandlerProtection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.CallbackHandlerProtection.html)
- [Java SE 17 — PasswordCallback](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/callback/PasswordCallback.html)
- [Java SE 17 — KeyStoreSpi](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStoreSpi.html)
- [Java SE 17 — Java Cryptography Architecture Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-store-008">12.8 Tạo, liệt kê, nhập, xuất và chuyển đổi kho bằng keytool</a>

<details>
<summary>Xem chi tiết</summary>

# 12.8. Tạo, liệt kê, nhập, xuất và chuyển đổi kho bằng `keytool`

## Mục tiêu

`keytool` là tiện ích dòng lệnh đi cùng JDK để **tạo và quản lý khóa, chứng chỉ X.509, certificate signing request (CSR) và Java KeyStore**. Trong thực tế, bạn dùng nó để tạo khóa cho môi trường thử nghiệm, xem loại entry/alias, xuất chứng chỉ **công khai**, nhập một chứng chỉ đáng tin, cập nhật certificate chain, hoặc chuyển kho cũ từ JKS sang PKCS12.

Sau bài học, bạn có thể:

1. Phân biệt `PKCS12`, `JKS`, `PKCS11` và các loại entry `PrivateKeyEntry`, `SecretKeyEntry`, `trustedCertEntry`.
2. Dùng `keytool -genkeypair` hoặc `-genseckey` để tạo kho phù hợp; `-list` để kiểm kê và `-printcert` để kiểm tra chứng chỉ.
3. Dùng đúng `-exportcert`, `-importcert`, `-certreq` và `-importkeystore`; biết thao tác nào **không** đưa private key ra ngoài và thao tác nào có thể **sao chép private key**.
4. Chuyển `JKS → PKCS12` và kiểm tra dữ liệu/alias/chứng chỉ sau chuyển; hiểu các vấn đề password, key password, định dạng, trust và automation.
5. Áp dụng biện pháp an toàn tối thiểu: backup trước thao tác thay đổi, bảo vệ mật khẩu, xác thực fingerprint từ nguồn đáng tin và không chỉnh sửa truststore mặc định tùy tiện.

Mọi ví dụ trong bài hướng đến **JDK 17+**; các lệnh đã được kiểm tra trên JDK có hỗ trợ những thuật toán được dùng. Các tập tin minh họa `*.p12`, `*.jks`, `*.pem` và `*.csr` là **dữ liệu học tập**, không nên coi chứng chỉ tự ký là chứng chỉ được CA công cộng tin cậy. Trong production, dùng CA/KMS/HSM và chính sách certificate/key lifecycle phù hợp.

## 1. Nhận diện đúng mục tiêu trước khi gọi keytool

`keytool` thao tác **alias và entry** của `KeyStore`, theo type/provider và quyền của người vận hành. Khi chạy CLI phải phân biệt ba loại dữ liệu được lưu:

- **PrivateKeyEntry**: private key và certificate chain; thường tạo bằng `-genkeypair`, dùng khi ký hoặc trình danh tính TLS.
- **SecretKeyEntry**: secret key như AES; có thể tạo bằng `-genseckey` nếu store/provider hỗ trợ.
- **trustedCertEntry**: một public certificate đã được quản trị viên **chủ động chấp thuận**; có thể tạo bằng `-importcert` dưới alias mới.

Các thao tác khác nhau về **tác động**: `-list` và `-printcert` đọc metadata; `-exportcert` chỉ xuất certificate công khai; còn `-importcert`, `-importkeystore`, `-changealias`, `-delete` hoặc lệnh tạo khóa **thay đổi kho, có thể thay cả trust policy hoặc sao chép private key**. Vì vậy cần kiểm tra alias, source certificate, password và backup trước khi dùng câu lệnh ghi. Đoạn tiếp theo tập trung thao tác CLI thực tế, không lặp bảng API quản lý entry.

## 2. Các định dạng và lý do chỉ rõ `-storetype`

| Loại kho | Mô tả | Lưu ý |
| --- | --- | --- |
| `PKCS12` | Định dạng cross-platform thường dùng cho private key, certificate và trong provider JDK hiện đại có thể chứa secret key | **Mặc định từ JDK 9** trong cấu hình OpenJDK/Oracle JDK thông thường; file thường mang đuôi `.p12` hoặc `.pfx` |
| `JKS` | Định dạng kho Java lịch sử, riêng của Java | Gặp nhiều ở ứng dụng cũ; thường nên đánh giá migration sang PKCS12 |
| `JCEKS` | Định dạng Java legacy hỗ trợ secret key | Tính tương thích và cơ chế bảo vệ khác PKCS12; không chọn chỉ vì tên quen thuộc |
| `PKCS11` | API đến token/HSM thông qua provider, không mặc định là một file kho có thể copy | Có thể dùng `-storetype PKCS11 -keystore NONE` với provider/config đã được thiết lập; quyền và khả năng xuất key phụ thuộc thiết bị |
| `Windows-MY`/provider khác | Keystore gắn với hệ điều hành/provider nếu được hỗ trợ | Không giả định mọi Java runtime/provider đều có |

Java 17 thường có `KeyStore.getDefaultType()` là `pkcs12`. Tuy nhiên **đừng suy loại kho chỉ từ đuôi file**: đặt tên `legacy.jks` không tự biến nội dung thành JKS nếu `-storetype` để mặc định. Trong bài này mọi thao tác quan trọng đều ghi rõ `-storetype` để hạn chế mơ hồ.

Tên `PKCS12`/`JKS` có thể được nhận dạng không phân biệt hoa thường. Một số JDK/provider có khả năng tự phát hiện định dạng và đọc tương thích; điều đó **không** có nghĩa file được chuyển format. Việc chuyển format thực sự dùng `-importkeystore` và **xác minh kho đích** sau thao tác.

Các file `.crt`/`.cer`/`.pem` thường chứa **certificate công khai** hoặc chuỗi PEM, không phải kho private key chỉ nhờ tên đuôi. Còn file `.p12` là container có thể mang **private key**: đối xử như dữ liệu nhạy cảm ngay cả khi có mật khẩu.

## 3. Danh sách lệnh cần biết

| Lệnh | Mục đích | Tác động điển hình |
| --- | --- | --- |
| `keytool -genkeypair` | Tạo private/public key pair và self-signed certificate ban đầu | **Ghi** `PrivateKeyEntry` vào keystore |
| `keytool -genseckey` | Tạo khóa bí mật đối xứng | **Ghi** `SecretKeyEntry` |
| `keytool -list` / `-list -v` | Liệt kê alias, entry type, certificate chain và fingerprint | **Đọc** metadata |
| `keytool -exportcert` | Xuất certificate của alias ra DER hoặc PEM (`-rfc`) | **Không xuất private key** |
| `keytool -printcert` | Đọc thông tin certificate từ file/nguồn | **Đọc** certificate, không tự chứng minh trust |
| `keytool -certreq` | Tạo CSR cho alias có private key | Tạo yêu cầu chứng chỉ; **chưa** phải certificate CA cấp |
| `keytool -printcertreq` | Xem CSR | **Đọc** CSR |
| `keytool -importcert` | Import trusted certificate hoặc CA reply | **Thay đổi truststore/certificate chain** theo alias |
| `keytool -importkeystore` | Copy một hoặc nhiều entry sang kho đích; có thể chuyển type | **Có thể sao chép private/secret key**, cần xử lý như bí mật |
| `keytool -changealias` | Đổi alias entry | Metadata có thể ảnh hưởng cấu hình ứng dụng |
| `keytool -storepasswd` / `-keypasswd` | Đổi mật khẩu kho hoặc entry ở type/provider được hỗ trợ | Có thể làm ứng dụng không mở được kho nếu config chưa cập nhật |
| `keytool -delete` | Xóa alias/entry | Có thể làm mất khả năng phục vụ/giải mã nếu chưa có backup |
| `keytool -list -cacerts` | Xem truststore CA mặc định do JDK quản lý | Chỉ nên **đọc** khi điều tra |

Kiểm tra danh sách tùy JDK đang dùng:

```bash
java -version
keytool -help
keytool -genkeypair -help
keytool -importkeystore -help
```

`keytool` từ JDK được chọn trên `PATH` có thể **khác phiên bản `java` chạy ứng dụng**. Hãy xác định `JAVA_HOME`/PATH và loại provider thực tế; khi cần chỉ rõ đường dẫn `$JAVA_HOME/bin/keytool` hoặc `%JAVA_HOME%\\bin\\keytool.exe` tương ứng hệ điều hành.

## 4. Chuẩn bị phòng thí nghiệm an toàn

Thực hành trong một thư mục mới, **không dùng file keystore production**:

```bash
mkdir keytool-lab
cd keytool-lab
```

Mọi thao tác ở dưới giả định `keytool` đã có trên PATH, và người học có quyền tạo file trong thư mục này. Hãy chọn một mật khẩu riêng đủ dài cho lab; không dùng mật khẩu thật của cơ sở hạ tầng.

### Nhập password mà không ghi trực tiếp vào command line

JDK 17 `keytool` hỗ trợ modifier **`:env`** hoặc **`:file`** sau các tùy chọn password:

```text
-storepass:env LAB_PASS
-keypass:env LAB_PASS
-srcstorepass:env LAB_PASS
-deststorepass:env LAB_PASS
```

**`LAB_PASS` là tên biến môi trường**, không phải mật khẩu literal. Các ví dụ ở dưới giả định biến này đã được tạo. Nếu không muốn dùng env, có thể **bỏ hẳn** `-storepass:env LAB_PASS` ở nhiều lệnh để `keytool` hỏi password tương tác; việc này phù hợp hơn khi vận hành thủ công.

Một cách nhập password tạm **không hiển thị ký tự** trên PowerShell:

```powershell
$secret = Read-Host "Lab keystore password" -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    $env:LAB_PASS =
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}
# Sau khi hoàn tất lab:
# Remove-Item Env:LAB_PASS
```

Hoặc trên **Bash** (không chạy block này trong PowerShell):

```bash
read -r -s -p "Lab keystore password: " LAB_PASS
echo
export LAB_PASS
# Sau khi hoàn tất lab:
# unset LAB_PASS
```

**Lưu ý bảo mật:** biến môi trường vẫn có thể bị đọc trong một số bối cảnh (process inspection, crash dumps, child processes, môi trường CI). `:env` chỉ giúp tránh chèn password trực tiếp vào câu lệnh và shell history; **không phải secret vault**. Trong production, sử dụng cơ chế secret manager/credential provider phù hợp, đặt ACL cho file, kiểm soát thời gian sống của credential; không in env hoặc paste password vào log. Không chèn mật khẩu thật qua `-storepass plaintext` hoặc `-keypass plaintext` trong tài liệu/pipeline.

Với lab, chọn password ít nhất 6 ký tự theo yêu cầu của `keytool`; các môi trường thực nên đặt chính sách mạnh hơn, không dùng lại password nơi khác.

## 5. Tạo kho PKCS12 chứa private key và certificate

Lệnh tạo private/public RSA key pair, alias `service` và self-signed X.509 certificate 30 ngày dùng cho bài học:

```bash
keytool -genkeypair -alias service -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 30 -dname "CN=demo.example.invalid, OU=Training, O=Example, C=VN" -keystore service.p12 -storetype PKCS12 -storepass:env LAB_PASS -keypass:env LAB_PASS
```

**Giải thích từng tham số:**

- `-genkeypair`: tạo key pair và self-signed certificate ban đầu. Certificate tự ký **chưa tự động được tin cậy bởi browser/client**; chỉ là điểm bắt đầu học CSR hoặc kiểm tra API.
- `-alias service`: tên entry; việc trùng alias trong cùng kho có thể gây lỗi hoặc cần xử lý theo loại thao tác. Dùng tên alias ổn định gắn với cấu hình ứng dụng.
- `-keyalg RSA -keysize 2048`: chọn thuật toán/kích thước key cho lab. Hệ thống mới cần tuân theo policy bảo mật và hạn sử dụng key; RSA-2048 là thông số minh họa phù hợp cho nhiều test, không bảo đảm ứng dụng phù hợp mọi tiêu chuẩn/cryptoperiod.
- `-sigalg SHA256withRSA`: thuật toán ký self-signed certificate trong lệnh này. Đừng nhầm với `RSASSA-PSS`; đây chỉ là cách ký chứng chỉ test.
- `-validity 30`: số ngày hiệu lực của self-signed certificate lab, **không** đặt thời hạn bảo mật của private key hay chính sách key rotation.
- `-dname ...`: subject distinguished name. `CN` không thay thế SAN và việc CA phát hành khi dùng TLS thật.
- `-keystore service.p12 -storetype PKCS12`: tạo kho đúng type, không dựa vào extension.
- `-storepass:env LAB_PASS -keypass:env LAB_PASS`: đọc password từ env. Dùng cùng password giúp tương thích phổ biến của PKCS12; không có nghĩa hai lớp bảo vệ hoàn toàn đồng nhất về chức năng.

Kỳ vọng **không phụ thuộc nội dung key ngẫu nhiên**: file `service.p12` xuất hiện; entry `service` có loại **`PrivateKeyEntry`** và chứa một certificate chain ban đầu (thường là self-signed certificate duy nhất). Thời điểm tạo, serial/fingerprint và phần chi tiết public key khác nhau giữa các lần chạy; **không** sao chép fingerprint mẫu như hằng số.

**Nếu muốn SAN trong chứng chỉ lab**, có thể tạo kho mới với `-ext "SAN=dns:demo.example.invalid"` trong lệnh `-genkeypair`. Đừng sửa trực tiếp `service.p12` đang tồn tại bằng cách lặp lại `-genkeypair` cùng alias nếu chưa hiểu tác động; hãy tạo test store mới hoặc backup có kiểm soát.

## 6. Liệt kê kho, alias và loại entry

Liệt kê tổng quan:

```bash
keytool -list -keystore service.p12 -storetype PKCS12 -storepass:env LAB_PASS
```

Liệt kê chi tiết đúng alias:

```bash
keytool -list -v -alias service -keystore service.p12 -storetype PKCS12 -storepass:env LAB_PASS
```

Trong kết quả chi tiết, chú ý các trường:

```text
Alias name: service
Entry type: PrivateKeyEntry
Certificate chain length: 1
Owner: CN=demo.example.invalid, ...
Issuer: CN=demo.example.invalid, ...
...
SHA256: <fingerprint thay đổi theo certificate>
```

Đây là **mẫu trường/nhãn tham khảo**, **không phải stdout chính xác**: nhãn có thể thay đổi theo locale/JDK, và chain/fingerprint phụ thuộc certificate thực tế.

- `PrivateKeyEntry`: có private key gắn với certificate chain; **`-list` không in private key**.
- `trustedCertEntry`: chỉ certificate công khai được đặt làm trust anchor trong kho tương ứng.
- `SecretKeyEntry`: một khóa đối xứng; không xuất raw key bytes khi liệt kê.

Có thể chạy `keytool -list -v -keystore service.p12 ...` không có `-alias` để xem tất cả entry. Khi có nhiều alias, `-alias` giúp xác định chính xác object đang điều tra. Không mặc định coi cùng alias trong hai file khác nhau là cùng key.

**Truy vấn không có password:** một số thao tác `-list` có thể cho phép hiển thị thông tin mà chưa xác minh integrity của keystore, đồng thời in cảnh báo. Đừng dùng dữ liệu từ một kho chưa được xác minh integrity như cơ sở ra quyết định trust/migration. Hãy cung cấp credential thích hợp khi cần xác minh.

## 7. Xuất certificate công khai bằng `-exportcert`

Xuất certificate đang gắn với alias `service` sang file PEM:

```bash
keytool -exportcert -rfc -alias service -file service.pem -keystore service.p12 -storetype PKCS12 -storepass:env LAB_PASS
```

- `-exportcert` **chỉ xuất X.509 certificate**, không xuất private key.
- `-rfc` tạo bản mã hóa Base64 PEM dạng `-----BEGIN CERTIFICATE-----` ... `-----END CERTIFICATE-----`. Nếu không có `-rfc`, mặc định xuất dạng DER binary.
- `-alias service` khi trỏ vào `PrivateKeyEntry` sẽ xuất certificate đầu của chain, tương ứng public key của private key đó. Để sao chép cả private key, `-exportcert` **không làm được**.
- `-file service.pem` tạo file certificate công khai; dù PEM có thể chia sẻ theo quy tắc trust, **không nên** xem nội dung PEM tự động chứng minh identity.

In thông tin certificate:

```bash
keytool -printcert -file service.pem
```

Thông tin cần xem trước khi dùng: Subject, Issuer, validity, serial, signature algorithm, public key algorithm/key size, SHA-256 fingerprint, SAN và các extension liên quan (nếu có). Certificate tự ký lab có `Issuer==Subject`; điều đó **không chứng minh** certificate đã được bên độc lập xác thực.

**Phân biệt PEM certificate với private key:** việc file có phần đuôi `.pem` không bảo đảm nó chỉ chứa certificate; nhiều công cụ cũng lưu private key ở PEM. Hãy xem nhãn BEGIN/END và kiểm soát dữ liệu trước khi chia sẻ. File `service.pem` ở quy trình này là certificate công khai vì do `keytool -exportcert` tạo.

## 8. Nhập trusted certificate vào kho tin cậy

Một truststore mẫu mới, tách biệt với `service.p12`:

```bash
keytool -importcert -alias service-demo -file service.pem -keystore trust.p12 -storetype PKCS12 -storepass:env LAB_PASS
```

Với alias **chưa tồn tại**, `-importcert` thêm một **`trustedCertEntry`**. Khi cần hỏi có tin certificate hay không, **không tự động bấm yes cho chứng chỉ chưa xác thực**. Trong lab, ta tự tạo certificate và có thể chủ động quan sát quy trình; trong thực tế, việc import phải đi sau **xác minh fingerprint qua một kênh độc lập đáng tin**, certificate chain và identity policy.

Kiểm tra sau import:

```bash
keytool -list -v -alias service-demo -keystore trust.p12 -storetype PKCS12 -storepass:env LAB_PASS
```

Kết quả được kỳ vọng có **`Entry type: trustedCertEntry`**, không có private key. Nếu đang điều tra truststore, hãy kiểm chứng đúng fingerprint được đối tác/CA xác nhận bên ngoài, không chỉ so fingerprint của hai file **cùng nhận từ một nguồn không tin cậy**.

### Vì sao không dùng `-noprompt` tùy tiện?

`-noprompt` có tác dụng bỏ qua tương tác ở một số lệnh, kể cả nhập trusted certificate. Trong automation có policy được kiểm chứng từ trước, nó có thể hữu ích; nhưng trên input không tin cậy, lệnh này có thể biến certificate attacker gửi thành trust anchor **mà không có bước xác nhận**. Trong bài học, cố ý **không gắn `-noprompt`** vào lệnh nhập truststore.

**Không thực hành trên `cacerts` hệ thống:** `keytool -list -cacerts` chỉ giúp xem các CA mặc định. Thêm hoặc xóa CA khỏi JDK-wide truststore ảnh hưởng nhiều ứng dụng cùng JDK, làm phát sinh drift khi nâng cấp JDK và có thể mở rộng trust quá mức. Dùng truststore riêng của ứng dụng khi thích hợp.

## 9. Tạo CSR và nhập certificate reply từ CA

Đối với private key đã có ở `service.p12`, tạo CSR:

```bash
keytool -certreq -alias service -file service.csr -keystore service.p12 -storetype PKCS12 -storepass:env LAB_PASS -keypass:env LAB_PASS -ext "SAN=dns:demo.example.invalid"
```

Xem CSR:

```bash
keytool -printcertreq -file service.csr
```

`service.csr` chứa thông tin và public key cần CA xét duyệt, **không phải certificate đã được CA cấp**. Không đưa private key vào CSR. CA sẽ kiểm tra thông tin/identity theo chính sách của CA rồi trả certificate hoặc chain tương ứng. Trong sản phẩm thật, SAN và EKU/KeyUsage cần theo giao thức (TLS server/client, code signing...), không chỉ nhìn subject CN.

Nếu CA trả certificate chain hợp lệ trong file ví dụ `signed-chain.pem` và đã kiểm chứng nguồn/chuỗi CA, **nhập certificate reply về đúng alias private key `service`**:

```bash
keytool -importcert -alias service -file signed-chain.pem -keystore service.p12 -storetype PKCS12 -storepass:env LAB_PASS -keypass:env LAB_PASS
```

**Lệnh nhập reply này chỉ chạy khi bạn thực sự đã nhận được `signed-chain.pem` hợp lệ từ CA**; nó không chạy trong lab tự ký mặc định vì file đó chưa có.

Cách `-importcert` hoạt động phụ thuộc alias:

- **Alias chưa tồn tại:** tạo trusted certificate entry, tức **thêm tin cậy**, không tạo private key.
- **Alias đang là `PrivateKeyEntry`:** xử lý **certificate reply/chain**, kiểm tra public key khớp với private key tương ứng và cập nhật chain khi đủ điều kiện. Đây là thao tác khác hoàn toàn với thêm trust anchor.
- Nếu certificate chain không tạo được đường tin cậy hoặc reply không tương ứng key, có thể thất bại. Kiểm tra CA roots/intermediates, chain order, file encoding và `-trustcacerts` **chỉ khi** chính sách tin cậy cho phép xem CA mặc định.

**Tránh tạo thêm alias mới để “nhập” CA reply** nếu mục tiêu là thay self-signed certificate gắn với private key ở `service`: alias mới chỉ tạo trustedCertEntry, không đưa chain vào private key entry cũ. Sau nhập, dùng `-list -v -alias service` để xem lại chain length, subject, issuer, validity và fingerprint.

## 10. Tạo khóa đối xứng trong kho với `-genseckey`

Một số ứng dụng cần `SecretKeyEntry` thay vì private/public key pair. Với JDK/provider hỗ trợ secret key trong PKCS12, có thể tạo AES key:

```bash
keytool -genseckey -alias aes-demo -keyalg AES -keysize 256 -keystore secrets.p12 -storetype PKCS12 -storepass:env LAB_PASS -keypass:env LAB_PASS
```

Kiểm tra:

```bash
keytool -list -v -alias aes-demo -keystore secrets.p12 -storetype PKCS12 -storepass:env LAB_PASS
```

Kỳ vọng entry **`SecretKeyEntry`**. `-genseckey` không tạo X.509 certificate chain, nên không thể dùng `-exportcert` trên alias secret key để “lấy public certificate AES”; **AES là thuật toán đối xứng và không có public key/certificate tương ứng**.

**Bảo mật:** đây là ví dụ key dạng phần mềm và keystore file. Trong production, hệ thống yêu cầu non-exportable keys, policy wrap/unwrap, audit hoặc separation of duties có thể nên đặt key ở KMS/HSM. Đừng giả định PKCS12, JKS và PKCS11 hỗ trợ cùng một loại entry với cùng khả năng export. Việc `keytool -genseckey` thành công **không** đồng nghĩa ứng dụng đã gán quyền sử dụng key đúng mục đích.

## 11. Chuyển kho JKS sang PKCS12 bằng `-importkeystore`

Đây là thao tác **chuyển format sao chép entry**, khác việc chỉ đổi tên file. Trong lab, tạo **một kho JKS legacy riêng** để có nguồn an toàn:

```bash
keytool -genkeypair -alias legacy -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 30 -dname "CN=legacy.example.invalid" -keystore legacy.jks -storetype JKS -storepass:env LAB_PASS -keypass:env LAB_PASS
```

`JKS` ở đây dùng **chỉ để minh họa conversion**, không phải khuyến nghị tạo kho mới bằng JKS. Kiểm tra nguồn:

```bash
keytool -list -v -alias legacy -keystore legacy.jks -storetype JKS -storepass:env LAB_PASS
```

Sao chép alias `legacy` vào kho PKCS12 mới, có thể đổi alias đích thành `migrated`:

```bash
keytool -importkeystore -srckeystore legacy.jks -srcstoretype JKS -srcstorepass:env LAB_PASS -srcalias legacy -srckeypass:env LAB_PASS -destkeystore migrated.p12 -deststoretype PKCS12 -deststorepass:env LAB_PASS -destalias migrated -destkeypass:env LAB_PASS
```

Giải thích:

- `-srckeystore` và `-srcstoretype` chỉ **kho nguồn**; `-destkeystore` và `-deststoretype` chỉ **kho đích**.
- `-srcalias legacy` nhập **một entry**; `-destalias migrated` đặt alias ở đích. Nếu **bỏ `-srcalias`**, lệnh sẽ cố nhập **toàn bộ các entry** từ nguồn; cần kiểm tra va chạm alias và entry type.
- `-srcstorepass`, `-srckeypass` và các phiên bản `dest...` dùng để mở kho và key. Trong lab cùng `LAB_PASS`, nhưng production có thể khác. Khi chuyển sang PKCS12 cho công cụ thứ ba, nhiều phần mềm yêu cầu **key password = store password**; đặt `-destkeypass` phù hợp hoặc đối chiếu yêu cầu của công cụ đích.
- Khi alias đích đã có, `keytool` có thể hỏi có ghi đè hoặc đổi tên; **không tự động chọn ghi đè** trên kho production. Không dùng `-noprompt` nếu chưa có thiết kế backup/collision handling, vì có thể overwrite entry.
- `-importkeystore` **có thể mang cả private key hoặc secret key qua kho mới**. File đích `migrated.p12` phải được bảo vệ như một bản sao private key, khác hoàn toàn file certificate công khai `service.pem`.
- Lệnh sao chép không đảm bảo mọi provider/entry/algorithm có thể nhập sang mọi storetype: trường hợp unsupported hoặc password sai có thể bị bỏ qua/nhắc tương tác/thất bại. Phải kiểm kê và đối chiếu từng entry.

Kiểm tra đích:

```bash
keytool -list -v -alias migrated -keystore migrated.p12 -storetype PKCS12 -storepass:env LAB_PASS
```

Mong đợi `Entry type: PrivateKeyEntry`, certificate/public key metadata tương ứng với alias nguồn. Vì đây là **chuyển cùng key pair**, certificate fingerprint của nguồn và đích **phải khớp**, dù tên alias thay đổi. Nếu sau chuyển fingerprint/key type/chain length bất ngờ khác, không đưa kho mới vào production trước khi giải thích được.

### Checklist migration không mất dữ liệu

1. Lập inventory tất cả alias, entry type, certificate chain và những ứng dụng đang tham chiếu từng alias. Với nhiều alias, ghi lại số lượng entry trước/sau.
2. **Backup kho nguồn** bằng cơ chế bảo mật được phê duyệt; xác minh backup có thể phục hồi và đặt quyền truy cập thích hợp. Không commit keystore/key vào Git.
3. Chọn file **đích mới** để không đè kho nguồn, ghi rõ `-srcstoretype` và `-deststoretype`; xác định mật khẩu nguồn/entry/đích có thể mở.
4. Chuyển bằng `-importkeystore`, xử lý từng lỗi/va chạm alias có kiểm soát. Không giả định exit code thành công là đã nhập đủ alias trong mọi tình huống nếu có warning/skip.
5. Chạy `-list -v` ở cả hai kho, đối chiếu alias mapping, entry type, chain length, certificate fingerprint và khả năng truy xuất private key bằng ứng dụng thực tế.
6. Kiểm thử hành vi end-to-end: TLS handshake/mTLS hoặc ký/verify, alias selection và mật khẩu runtime. Private key không được coi là hoạt động chỉ vì `-list` thấy tên alias.
7. Chuyển cấu hình ứng dụng (đường dẫn, type, alias, password/secret reference), triển khai canary và có rollback đã thử.
8. Chỉ retire kho cũ sau khi dữ liệu, backup, đường rollback, retention và dependent services đều đã được rà soát; bảo đảm không còn reader cũ.

### Chuyển toàn bộ entries khi cần

Nếu muốn copy tất cả entry (và đã xử lý va chạm/trust/cert policy), bỏ `-srcalias` và `-destalias`:

```bash
keytool -importkeystore -srckeystore legacy.jks -srcstoretype JKS -srcstorepass:env LAB_PASS -destkeystore migrated-all.p12 -deststoretype PKCS12 -deststorepass:env LAB_PASS
```

Lệnh trên có thể **hỏi thêm mật khẩu từng entry** nếu không thể truy xuất key bằng source store password. Chạy có người giám sát trên lab; đừng vội thêm `-noprompt` để “khắc phục” prompt vì nó có thể khiến entry xung đột bị ghi đè hoặc entry lỗi bị bỏ qua. Đừng dùng nguyên lệnh này để chuyển toàn bộ keystore thật nếu chưa lập danh sách alias và test.

## 12. Đừng nhầm `-exportcert` với “export private key”

Bảng này là trọng tâm khi thao tác với kho có dữ liệu nhạy cảm:

| Nhu cầu | Thao tác phù hợp | Có xuất private/secret key? |
| --- | --- | --- |
| Gửi certificate public key cho bên xác minh, CA hoặc đối tác | `-exportcert -rfc` | **Không** |
| Xem certificate/fingerprint không cần key | `-printcert -file ...` | **Không** |
| Đưa CA/trusted certificate vào truststore | `-importcert` dưới alias mới | **Không** |
| Gắn CA certificate reply vào private key đã tạo | `-importcert` **cùng alias PrivateKeyEntry** | Private key **giữ trong kho**, không được xuất qua cert reply |
| Copy private key entry từ JKS sang PKCS12 | `-importkeystore` | **Có thể**, vì entry đích chứa private key |
| Tạo AES secret key trong kho | `-genseckey` | Sinh key material và lưu `SecretKeyEntry` |
| Chuyển PKCS12 thành file certificate `.pem` bằng cách đổi đuôi | **Sai thao tác** | Không chuyển định dạng; có thể làm lộ kho |

Nếu cần một `.p12` chứa private key cho phần mềm khác, `-importkeystore` là cách đưa **key entry** sang kho đích với credential bảo vệ phù hợp. File PKCS12 **chứa private key**, không an toàn để phát tán như public certificate. `keytool` không phải một công cụ chuyên dụng để xuất private key thô dưới dạng PEM không mã hóa; tránh bổ sung các bước xuất plaintext key trừ khi bài toán, chính sách và môi trường thực sự cho phép.

**Với PKCS11/HSM**, private key có thể **không xuất được**. Việc `-importkeystore` hoặc `-exportcert` có thể xuất public certificate không có nghĩa HSM cho phép copy private key. Cần đọc provider/token policy và sử dụng key handle.

## 13. Liên kết với Java `KeyStore` trong ứng dụng

`keytool` tạo kho trên CLI; ứng dụng Java phải mở **đúng format/password/alias** và chọn đúng mục đích:

```java
KeyStore store = KeyStore.getInstance("PKCS12");
try (InputStream input = Files.newInputStream(Path.of("service.p12"))) {
    store.load(input, storePassword);
}
boolean signingEntry = store.isKeyEntry("service");
Certificate certificate = store.getCertificate("service");
```

Đây là **snippet minh họa**, không phải một chương trình độc lập. Cần import `java.security.KeyStore`, `java.security.cert.Certificate`, `java.io.InputStream`, `java.nio.file.Files` và `java.nio.file.Path`; `storePassword` phải là `char[]` có được qua secret manager/prompt, không hard-code. Java xử lý checked exceptions cho file, store integrity, provider và password.

Các API hữu ích:

- `KeyStore.getInstance("PKCS12")`: chọn type cụ thể.
- `store.load(input, password)`: mở/kiểm chứng kho bằng credential cần thiết.
- `store.aliases()`, `store.containsAlias(alias)`, `store.size()`: inventory.
- `store.isKeyEntry(alias)` và `store.isCertificateEntry(alias)`: phân loại key entry vs trusted cert entry. `isKeyEntry` đúng cho private/secret key, không nói chính xác loại key.
- `store.getCertificate(alias)`: với `PrivateKeyEntry` trả certificate đầu chuỗi; với `trustedCertEntry` trả certificate; với `SecretKeyEntry` thường không có certificate.
- `store.getCertificateChain(alias)`: dùng với alias có private key và chain.
- `store.getEntry(alias, new KeyStore.PasswordProtection(entryPassword))` hoặc `getKey(alias, entryPassword)`: truy xuất key khi được phép; **không log raw key**.
- Với provider đặc biệt (HSM/PKCS11), cách `load` và `getKey` có thể khác keystore file; cần theo provider API và đừng mặc định `getEncoded()` của key không null.

Một chương trình có thể thấy entry trong `keytool -list` nhưng vẫn lỗi runtime do **chọn sai alias**, khác store type, password entry không khớp, thiếu permission đọc file, certificate chain không được client tin, hoặc thuật toán không được provider hỗ trợ.

## 14. Sự cố thường gặp khi dùng `keytool`

| Triệu chứng/lỗi | Nguyên nhân có thể | Cách xử lý |
| --- | --- | --- |
| `keytool` không tìm thấy trên terminal | Chưa cài JDK hoặc PATH/JAVA_HOME sai | Kiểm tra `java -version`, `keytool -help` và đường dẫn JDK |
| `Keystore was tampered with, or password was incorrect` hoặc lỗi mở kho | Mật khẩu sai; file/type/provider không đúng; file lỗi | Xác minh path, password và storetype từ nguồn đáng tin; không đổi đuôi để “chữa” |
| `Alias <name> does not exist` | Alias không có trong kho hiện tại | `-list` đúng path/storetype, so chính xác alias và source/dest |
| `alias already exists` khi tạo/import | Đã có entry tên đó | Kiểm kê trước; chọn alias mới hoặc quy trình thay có backup |
| File `.jks` đọc được dù không khai `-storetype JKS` | JDK/provider có thể autodetect format | Đặt type rõ ràng; không suy file đã là PKCS12 |
| `-exportcert` tạo file nhưng không có private key | Đây là hành vi **đúng** | Dùng `-importkeystore` cho entry migration có key, giữ file đích an toàn |
| Import chứng chỉ CA vào alias mới nhưng TLS server vẫn dùng self-signed | Entry mới chỉ là `trustedCertEntry` | Nhập certificate reply vào **alias PrivateKeyEntry hiện có** nếu đúng workflow |
| Certificate không được tin cậy dù `-importcert` thành công | Sai truststore runtime, fingerprint/chain không phù hợp, client khác JDK | Xác định đúng trust configuration, chain và hệ thống xác minh |
| `-certreq` chạy xong nhưng chứng chỉ vẫn tự ký | CSR không tự trở thành CA-signed cert | Gửi CSR tới CA, nhận reply, kiểm chứng và import cùng alias |
| `SecretKeyEntry` không tạo được trong type/provider | Không hỗ trợ secret key hoặc thuật toán/size đó | Kiểm tra `-genseckey -help` và provider; chọn storetype/key management phù hợp |
| `-importkeystore` nhập thiếu entries | Entry type không hỗ trợ, keypass sai, alias collision, skip/warning | Kiểm tra toàn bộ log, inventory và từng entry, không tin chỉ vào exit code |
| Sau conversion ứng dụng lỗi `UnrecoverableKeyException` | Key password và store password không phù hợp runtime | Xác minh credential cho từng entry; kiểm tra tương thích PKCS12 |
| Dùng `-noprompt` trên certificate do người ngoài gửi | Bỏ qua trust verification | Kiểm tra fingerprint qua kênh độc lập; yêu cầu phê duyệt |
| Sửa `cacerts` khiến nhiều ứng dụng thay đổi trust | Shared JDK truststore có phạm vi quá rộng | Sử dụng truststore riêng theo ứng dụng và rollout có kiểm soát |
| Kết quả `-list` hiển thị certificate nhưng không có signing ability | Đó là trustedCertEntry, không phải private key | Kiểm tra `Entry type` và cách cấp private key |
| Backup/copy `.p12` nhầm thành tài liệu công khai | File có thể mang private key | Bảo vệ như credential, giới hạn truy cập và mã hóa backup |

**Không dùng `-delete` để “làm sạch” kho khi chưa hiểu alias**. Xóa `PrivateKeyEntry` có thể làm mất private key không thể khôi phục, xóa `trustedCertEntry` có thể làm ngừng các kết nối TLS/mTLS, và xóa `SecretKeyEntry` có thể khiến dữ liệu đã mã hóa không đọc lại được.

## 15. Quy trình production: bảo mật và trách nhiệm vận hành

1. **Phân loại kho theo vai trò:** tách keystore giữ private key của service khỏi truststore chỉ chứa trusted certificates khi có ích cho least privilege. Không dùng một file chứa toàn bộ private keys của mọi tenant.
2. **Bảo vệ vật lý và logic:** chỉ cấp quyền đọc file và mật khẩu cho đúng principal; đặt filesystem ACL, encryption-at-rest khi phù hợp, secret manager, backup/restore và audit. Không commit `*.p12`, `*.jks` hoặc file password vào source repo.
3. **Dùng keytool đúng môi trường:** luôn xác nhận `java -version`, `keytool -help` và provider. Chọn `-storetype` rõ, tên alias ổn định và phân biệt key password với store password.
4. **Quản lý certificate trust:** không tự import certificate chỉ vì nó có định dạng X.509. Kiểm tra subject/SAN, issuer, key usage, thời hạn, fingerprint/chain theo đúng yêu cầu protocol; xem nguồn trust anchor là gì.
5. **Sao lưu trước khi ghi:** thao tác `-genkeypair`, `-genseckey`, `-importcert`, `-importkeystore`, `-changealias`, `-storepasswd` và `-delete` có thể thay kho. Backup có bảo vệ và phục hồi thử được quan trọng hơn chỉ copy file một lần.
6. **Định nghĩa key lifecycle:** ngày hết hạn certificate không đồng nghĩa private key đã tự xoay; một PKCS12 conversion cũng không phải key rotation. Key rotation cần phát hành key mới, cập nhật certificate/peer trust và kiểm tra migration/rollback.
7. **Hỗ trợ non-exportable keys:** với yêu cầu HSM/KMS/PKCS11, dùng cơ chế token/provider và policy thay vì lén chuyển key ra file PKCS12. Key extraction có thể bị cấm hoặc không hỗ trợ.
8. **Kiểm soát kết quả/telemetry:** ghi alias, entry type, storetype, key ID/serial/fingerprint có kiểm soát và mã lỗi; không ghi storepass/keypass, private key, plaintext key hoặc secret dump.
9. **Tách môi trường:** dev/test/prod dùng keys, CA, truststore, password và policy riêng. Chứng chỉ `CN=*.example.invalid` tự ký trong lab không được đưa làm CA/prod identity.
10. **Tự động hóa an toàn:** tránh dùng `-noprompt` nếu không có bước kiểm tra trước; đặt secret source riêng, đảm bảo file đích mới/alias không xung đột, kiểm tra exit code + cảnh báo và xác minh entry inventory sau write.

Các hệ thống TLS/mTLS thực tế còn cần runtime cấu hình đúng Java `javax.net.ssl.keyStore` / `trustStore` và password/type, hoặc cấu hình TLS của framework. **Không mặc định** việc file đã tồn tại là service sẽ tự nạp file đó; test handshake/cert chain/authorization trước khi phát hành.

## 16. Bài tập thực hành có tiêu chí

1. **Tạo kho:** chạy `-genkeypair` trong thư mục test, nêu khác biệt giữa file `service.p12`, alias `service`, private key và self-signed certificate. Xác minh `PrivateKeyEntry` bằng `-list -v`.
2. **Xuất chứng chỉ:** chạy `-exportcert -rfc`, xem PEM bằng editor và `-printcert`. Giải thích vì sao có thể gửi file certificate nhưng **không được chia sẻ `service.p12`**.
3. **Nhập truststore:** kiểm tra fingerprint của certificate lab rồi `-importcert` vào `trust.p12` dưới alias mới. Chứng minh entry đích là `trustedCertEntry`, không phải `PrivateKeyEntry`.
4. **CSR:** chạy `-certreq` và `-printcertreq`. Chỉ ra public key/subject/extension trong CSR, giải thích vì sao chưa phải certificate CA-signed.
5. **Secret key:** tạo `secrets.p12` với AES-256 bằng `-genseckey`. Kiểm tra `SecretKeyEntry`; thử `-exportcert` trên alias AES và giải thích vì sao không thể lấy X.509 certificate.
6. **Chuyển format:** tạo `legacy.jks` rồi dùng `-importkeystore` sang `migrated.p12`. So sánh entry type, certificate fingerprint và khả năng truy cập alias. Tại sao đổi extension file không tương đương conversion?
7. **Chuyển nhiều entries:** bổ sung một alias vào kho nguồn **lab**, sau đó chuyển toàn bộ sang kho đích mới. Đếm entries trước/sau, xử lý password prompt/alias collision mà không dùng `-noprompt` bừa bãi.
8. **Phân biệt password:** tạo mô hình có source key password khác source store password (nếu JKS provider hỗ trợ), rồi giải thích công dụng của `-srcstorepass` và `-srckeypass` khi import. Kiểm tra chính sách dest keypass/storepass của ứng dụng đích.
9. **Threat modeling:** một file `.p12` đã được đẩy lên repository và bị clone. Tại sao xóa commit hiện tại không đủ? Hãy liệt kê các bước xử lý private-key compromise, thu hồi/reissue và dọn lịch sử/phạm vi tác động theo quy trình bảo mật.
10. **Trust mismatch:** bên gửi một certificate tự ký mới rồi yêu cầu import vào kho trust hệ thống. Những bước xác thực độc lập nào phải xảy ra trước khi chấp nhận?
11. **Runtime:** một service báo lỗi “không tìm thấy alias” dù `keytool -list` từ máy vận hành thấy alias. Kiểm tra PATH/JDK, file path, container mount, storetype, alias case/entry password và quyền truy cập theo thứ tự.
12. **Review migration:** xem xét một script chạy `-importkeystore -noprompt` lên kho đích đã chứa alias quan trọng. Xác định rủi ro overwrite/skip và đề xuất quy trình có backup + inventory + verify.

**Tiêu chí hoàn thành:** tự tạo PKCS12 lab, nhận diện đúng cả ba loại entry, xuất certificate không lộ private key, nhập trusted certificate có xác minh, biết CSR/CA reply cùng alias, chuyển JKS sang PKCS12 mà không mất key/chain, và mô tả rõ những rủi ro password, trust, backup, alias collision và provider.

## Tài liệu tham khảo chính thức

- [Oracle Java SE 17 — The keytool Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): cú pháp, options, import/export/conversion, bảo vệ password `:env`/`:file`.
- [Java SE 17 `KeyStore` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html): `PrivateKeyEntry`, `SecretKeyEntry`, `TrustedCertificateEntry`, `load`, `getEntry`.
- [Java SE 17 Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): `PKCS12`, `JKS`, thuật toán hỗ trợ và tên chuẩn.
- [Dev.java — keytool: Managing Your Keystore](https://dev.java/learn/jvm/tool/security/keytool/): hướng dẫn chính thức về lệnh và nhập certificate.
- [Oracle Java SE 17 `KeyStore.Builder`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.Builder.html): cách cấu hình tạo và mở `KeyStore` qua builder/provider.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-store-009">12.9 Phạm vi của cacerts, kho tin cậy riêng và tác động khi thay đổi tập CA được tin</a>

<details>
<summary>Xem chi tiết</summary>

# 12.9 Phạm vi của cacerts, kho tin cậy riêng và tác động khi thay đổi tập CA được tin

Khi ứng dụng Java gọi một dịch vụ HTTPS, nó không thể chỉ kiểm tra rằng “server có gửi một chứng thư X.509”. Ứng dụng phải quyết định **chứng thư đó có được tin cậy theo chính sách của kết nối hay không**, thường thông qua một đường chứng thư (*certificate path*) nối tới một **trust anchor** được cấu hình. Đối với Java, tập trust anchor thường đến từ **truststore**. Trong nhiều JDK, truststore mặc định có liên quan đến file `cacerts`; nhưng tên “mặc định” không đồng nghĩa một file `cacerts` cố định cho mọi process, mọi thư viện mạng hoặc mọi bản phân phối Java.

Bài này phân biệt ba tầng:

1. **`cacerts`**: kho chứng thư CA được phân phối/quản lý cùng một Java runtime, thường là nguồn mặc định cho JSSE nếu không có cấu hình ưu tiên khác.
2. **Kho tin cậy riêng của ứng dụng** (*application-specific truststore*): một tập trust anchor do ứng dụng hoặc môi trường chỉ định; có thể thay hoặc, nếu thiết kế rõ ràng, kết hợp với nguồn mặc định.
3. **Phạm vi tác động của thay đổi tập CA đáng tin**: thêm/xóa root hoặc intermediate CA làm thay đổi kết nối nào có thể xác thực được, mở rộng/thu hẹp bề mặt tin cậy và ảnh hưởng tới triển khai, rollback, TLS/mTLS và vận hành.

**Điểm quan trọng:** thêm một CA vào truststore không làm server “an toàn hơn” một cách tự động; nó **cấp thêm quyền được xét là nguồn tin cậy** trong các điều kiện kiểm chứng. Xóa CA không nhất thiết làm mọi phiên kết nối hiện hữu lập tức ngắt, nhưng có thể khiến **những TLS handshake mới** thất bại. Vì vậy, sửa CA store phải được đối xử như một thay đổi chính sách bảo mật có kiểm thử, audit và phương án rollback.

## 1. Mục tiêu học tập

Sau bài học, bạn có thể:

1. Giải thích vai trò của **truststore**, **keystore**, **trusted certificate entry**, **key entry** và **trust anchor** trong Java.
2. Biết `cacerts` thường nằm ở đâu, Java runtime/provider nào thực sự đọc nó, và khi nào không được giả định nó được sử dụng.
3. Phân biệt nguồn trust mặc định của JSSE với kho tin cậy riêng được chỉ định bằng `javax.net.ssl.trustStore` hoặc `SSLContext` / `TrustManagerFactory`.
4. Hiểu **custom truststore có thay thế hay hợp nhất với public roots**: mặc định không tự union tập CA chỉ vì đặt một file mới.
5. Phân tích tác động khi **thêm, loại bỏ, thay thế hoặc thu hẹp tập root/intermediate CA**, gồm nguy cơ chấp nhận đối tác không mong muốn hoặc làm đứt kết nối.
6. Tách **xác minh certificate chain** khỏi **hostname/SAN matching**, certificate usage, thuật toán, revocation, quyền nghiệp vụ và mTLS client authentication.
7. Tạo một trust manager riêng bằng Java 17+, không cần sửa `cacerts`, và kiểm tra test chứng thư/đường tin cậy theo policy.
8. Có quy trình vận hành để thay truststore an toàn trong container/Kubernetes, đa JDK, ứng dụng chạy dài hạn và những kết nối đang giữ trong pool.

## 2. Những đối tượng dễ bị nhầm

Một truststore Java thường là `KeyStore` (định dạng PKCS12 hoặc JKS tùy file/runtime) được dùng để tìm những **chứng thư được tin cậy**. Một keystore cho **danh tính TLS của chính ứng dụng** có thể chứa private key kèm certificate chain. Hai loại thường dùng cùng API `KeyStore` nhưng mục đích khác nhau.

| Khái niệm | Công dụng | Có private key không? | Dùng trong TLS để làm gì? |
| --- | --- | --- | --- |
| **Truststore** | Chứa certificate/trust anchor để kiểm peer | Thường không cần | `TrustManager` kiểm chain của phía đối diện |
| **`cacerts`** | Nguồn root CA mặc định trong nhiều runtime JDK | Thông thường là các trusted cert entries | Nguồn CA được runtime/JSSE dùng khi được cấu hình làm mặc định |
| **Identity keystore** | Chứa private key và certificate chain của local endpoint | Có thể có | `KeyManager` trình certificate và dùng private key khi cần |
| **TrustedCertEntry** | Một certificate được lưu trong `KeyStore` dưới alias | Không | Có thể làm trust anchor tùy cách trust manager nạp |
| **PrivateKeyEntry** | Private key cùng certificate chain | Có | Phục vụ local identity cho TLS hoặc signing |
| **Trust anchor** | Điểm kết thúc đường chứng thư mà verifier đã tin theo policy ngoài băng | Không cần private key của anchor | PKIX chấp nhận chain nếu mọi điều kiện và trust anchor phù hợp |
| **Intermediate CA** | CA cấp chứng thư dưới một root/CA khác | Certificate public của intermediate; private key giữ riêng | Xây dựng chain; không nhất thiết cần cài như trust anchor |
| **Leaf/end-entity certificate** | Certificate của server/client cụ thể | Bản certificate không chứa private key | Xác minh peer và tên/mục đích sử dụng |

**Truststore không phải nơi “để lấy khóa AES”**: nó thường quản lý các certificate dùng để xác thực peer. **`cacerts` không phải nơi lưu private key của HTTPS server**; private key của server có thể ở identity keystore/HSM/KMS. Một file PKCS#12 có thể chứa cả key và certificate entries, nhưng ý nghĩa khi được nạp phụ thuộc vào `KeyManagerFactory` hay `TrustManagerFactory`, không chỉ extension `.p12`.

### Định nghĩa “tin CA” có phạm vi

Một CA trusted có thể được xem là trust anchor khi xây dựng chain phù hợp. Tuy nhiên điều kiện chấp nhận leaf còn phụ thuộc:

- Đường chứng thư hợp lệ theo thuật toán/path-building và các ràng buộc hiện hành.
- Thời hạn, Basic Constraints, Key Usage và Extended Key Usage thích hợp; algorithm/key-size constraints.
- Endpoint identification: kết nối đến DNS/IP **đúng** tên trong Subject Alternative Name (*SAN*) của leaf.
- Revocation policy, distrust policy hoặc các hạn chế riêng của runtime/ứng dụng nếu được cấu hình.
- Trust anchor nào được phép cho **mục đích** kết nối đó và theo tenant/environment nào.

Vì vậy, có một CA trong truststore **không tự** cấp quyền truy cập API, cho phép mọi certificate của CA làm server certificate, hay chứng minh một request có quyền đọc dữ liệu.

## 3. `cacerts` nằm ở đâu và thuộc phạm vi nào?

Đối với JDK hiện đại, vị trí thường gặp là:

```text
<java.home>/lib/security/cacerts
```

Ví dụ nếu Java được cài ở `/opt/jdk-17` thì file có thể là `/opt/jdk-17/lib/security/cacerts`. Trên máy có nhiều JDK/JRE/container image, **mỗi runtime có thể có tập CA khác nhau**. Đường dẫn thật còn phụ thuộc package/vendor, symbolic link và cơ chế tích hợp CA của hệ điều hành.

Để quan sát runtime đang chạy:

```bash
java -XshowSettings:properties -version
```

Trong output, tìm thuộc tính `java.home` và kiểm cách cấu hình launcher/provider. Dùng `java` đúng đường dẫn mà ứng dụng production chạy; việc terminal sử dụng một JDK khác với service (hoặc một container image khác) rất phổ biến.

Để liệt kê chứng thư trong store mặc định được lệnh `keytool` của JDK chỉ định nhận diện:

```bash
keytool -list -cacerts
```

`keytool` có thể hỏi mật khẩu để kiểm tính toàn vẹn; không nhập password quản trị thật lên command line hoặc chia sẻ output chứa thông tin không cần thiết. Lệnh chỉ phản ánh **`cacerts` của JDK đi cùng `keytool` đang được chạy**, không xác nhận mọi Java process khác đang dùng chính file ấy.

### Tác động của bản phân phối Java và hệ điều hành

Không có một danh sách CA bất biến gọi là “bộ CA Java”. Runtime JDK có thể mang một tập root riêng, nhận cập nhật root store từ vendor, hoặc liên kết với hệ điều hành trong một số bản đóng gói. Container image được build ở thời điểm khác có thể có root store khác, kể cả nếu ứng dụng cùng version.

Hệ quả thực tế:

- Một service chạy JDK A tin certificate chain X, service chạy JDK B có thể từ chối X.
- Cập nhật JDK/container base image có thể thay tập CA được tin mà **không thay mã nguồn ứng dụng**.
- Sửa `cacerts` của máy host chưa chắc ảnh hưởng process đang dùng JDK trong container.
- Sửa file khi process đã khởi tạo `SSLContext`/trust manager không đảm bảo những kết nối mới tự dùng tập CA mới; thường cần **tạo lại trust manager/SSLContext, thay connection pool hoặc restart có kiểm soát**.
- Một thư viện có TLS stack/native store riêng có thể không dùng JSSE truststore.
- Đừng suy đoán từ `java -version` trên terminal nếu deployment dùng image/dịch vụ khác; cần quan sát đúng artifact và process.

**Không sửa `cacerts` làm lựa chọn mặc định** cho một đối tác duy nhất. Việc thêm CA vào JDK-wide truststore có thể mở rộng tin cậy của nhiều client trong cùng runtime và ảnh hưởng các ứng dụng khác nếu dùng chung JDK. Tốt hơn là tạo **truststore riêng có scope rõ**, hoặc client-specific `SSLContext`.

## 4. JSSE chọn truststore như thế nào?

Với SunJSSE trong OpenJDK/Oracle JDK thông dụng, khi tạo default trust manager (`TrustManagerFactory.init((KeyStore) null)`), cơ chế tìm nguồn trust thường xét:

1. Nếu có cấu hình `javax.net.ssl.trustStore`, dùng truststore được chỉ định (cùng type/password/provider phù hợp).
2. Nếu không được chỉ định, có thể ưu tiên file `jssecacerts` trong thư mục security của runtime nếu tồn tại.
3. Nếu không có `jssecacerts`, thường dùng `cacerts`.
4. Nếu không có tập trust anchor khả dụng, việc xác thực TLS có thể thất bại vì không tìm được path tới CA tin cậy.

**Đây là mô hình áp dụng cho nhiều JSSE provider/JDK phổ biến, không phải cam kết mọi Java runtime hay mọi HTTP client.** Tên file ưu tiên, khả năng load, system properties, cách tích hợp OS store và các trường hợp file không tồn tại/phân quyền lỗi có thể khác theo provider/phiên bản. Với một SunJSSE implementation, đặt `javax.net.ssl.trustStore` trỏ tới file không có thật có thể dẫn đến truststore trống hoặc lỗi load, **không nên giả định fallback an toàn về `cacerts`**.

Các thuộc tính liên quan:

| Cấu hình | Ý nghĩa | Cảnh báo |
| --- | --- | --- |
| `javax.net.ssl.trustStore` | Đường dẫn truststore cho JSSE default trust | Có thể **thay** tập CA mặc định thay vì bổ sung |
| `javax.net.ssl.trustStoreType` | Kiểu keystore, ví dụ `PKCS12` hoặc `JKS` | Phải phù hợp file/provider |
| `javax.net.ssl.trustStorePassword` | Mật khẩu khi tải/kiểm tính toàn vẹn truststore | Command-line system property có thể lộ qua process args/log |
| `javax.net.ssl.trustStoreProvider` | Provider đọc store khi cần | Không tùy tiện chọn provider chưa kiểm tương thích |
| `javax.net.debug` | Debug TLS handshake/trust processing | Dùng có giới hạn, log có thể lộ metadata nhạy cảm |
| `java.security.debug` | Một số luồng debug security/certpath | Không bật quá rộng ở môi trường production |

Có thể cấu hình JVM lúc khởi động theo ý tưởng:

```text
-Djavax.net.ssl.trustStore=/etc/app/trust/outbound.p12
-Djavax.net.ssl.trustStoreType=PKCS12
```

Đây là **ví dụ các tùy chọn JVM**, không phải lệnh độc lập cần chạy. Phải cung cấp cơ chế mở/kiểm integrity file theo chính sách, chẳng hạn secret manager và code tạo `SSLContext`. Đừng đặt plaintext password bằng `-Djavax.net.ssl.trustStorePassword=secret` trên command line vì có thể lộ qua công cụ giám sát process và log.

### Truststore riêng có tự cộng thêm root công khai của `cacerts` không?

**Thông thường không.** Nếu chỉ đặt một file custom qua `javax.net.ssl.trustStore`, JSSE mặc định sẽ lấy tập trust từ file đó cho default trust manager theo provider/config. Nếu file chỉ chứa CA của đối tác, các HTTPS call tới website công khai khác có thể **không còn** xây dựng được chain tới public CA vì những root này không nằm trong kho mới.

Đừng xem “custom truststore” = “cacerts + một CA”. **Hợp nhất hai bộ tin cậy là một quyết định kiến trúc riêng**, không phải hiệu ứng mặc định của một property.

Có những chiến lược khác nhau:

- **Replace:** custom store chỉ chứa CA cho mục đích cụ thể, dùng trong một `SSLContext` của client. Đây là lựa chọn có scope hẹp, dễ audit.
- **Curated bundle:** một file chứa tập approved public roots + private CA cần thiết, quản lý tập đó như một artifact có version. Tác động có thể rộng nếu cấu hình JVM-wide.
- **Composite trust manager có kiểm soát:** kết hợp hai trust managers, nhưng phải định nghĩa chính xác điều kiện OR/AND, thuật toán/hostname/revocation, xử lý lỗi, môi trường sử dụng và scope. Không tự ghép code “thử trust manager này, fail thì manager kia” nếu chưa security-review.
- **Per-client contexts:** outbound client A dùng public CAs; client B dùng private CA. Tránh làm rộng policy của tất cả outbound connections.

Hợp nhất không phải lúc nào cũng tốt: nếu một client chỉ được phép nói chuyện với một đối tác private CA, tự thêm toàn bộ public roots sẽ **mở rộng** tập certificate chains được xét đáng tin.

## 5. Phân biệt truststore mặc định, truststore riêng, và identity keystore

Xét một Java service gọi ra ngoài tới hai đích:

```text
Java service
   |
   +-- Public HTTPS API
   |       uses SSLContext A -> approved public root CAs
   |
   +-- Internal HTTPS API
           uses SSLContext B -> private CA / tightly scoped trust
```

Nếu service còn nhận kết nối HTTPS/mTLS:

```text
Inbound server side:
  KeyManager -> private key + certificate chain của chính server
  TrustManager -> certificate CAs mà server tin khi xác thực client mTLS

Outbound client side:
  KeyManager -> client identity nếu endpoint yêu cầu mTLS
  TrustManager -> CA/path của HTTPS server phía đối diện
```

**Đừng nhập hai vai trò vào một**. Việc thêm root CA vào **client truststore** không tự làm server gửi certificate mới hoặc tạo client certificate. Việc nạp private key vào identity keystore không tự làm client tin một CA trên endpoint đích.

Ở server mTLS, truststore dùng để xác thực **client certificates** có thể nên chứa riêng những issuing CAs được phép. Nếu vô tình cho toàn bộ public WebPKI roots làm trust anchors của mTLS client auth, phạm vi client certificate được chấp nhận có thể rộng hơn mong muốn; vẫn cần mapping identity/authorization, nhưng tốt nhất thu hẹp trust roots ngay từ đầu.

### Scope của một JSSE SSLContext

- **JVM default:** properties và `SSLContext.getDefault()` có ảnh hưởng rộng tới những thư viện thực sự dùng default context.
- **Client-specific:** một `SSLContext` mới được cấu hình từ `TrustManagerFactory` riêng và gắn vào HTTP/TLS client cụ thể. Phạm vi thay đổi rõ hơn.
- **Provider/native TLS:** không phải tất cả thư viện mạng đều dùng JSSE. Cần kiểm tài liệu runtime/client.
- **Long-lived process:** thay file truststore không buộc các manager/context đã init cập nhật tự động. Version, reload/refresh và connection-pool lifecycle phải được thiết kế.

Ứng dụng có thể ghi audit về fingerprint/version của **trust bundle**, số CA và thời điểm load; tránh in đầy đủ chứng thư hoặc key material vào log nếu không cần.

## 6. Root CA, intermediate CA, leaf certificate và trust anchor

Đường chứng thư thường như sau:

```text
Root CA [trusted anchor installed/configured]
   |
   +-- Intermediate CA [signed by root]
          |
          +-- HTTPS server leaf [signed by intermediate]
                 +-- DNS SAN = service.example.test
                 +-- appropriate serverAuth purpose
```

Trong chain thông thường, **chỉ root CA** cần làm trust anchor; server gửi leaf và intermediate(s), hoặc path builder tìm intermediate từ nguồn thích hợp. Không mặc định phải import mọi intermediate vào truststore. Nếu server thiếu intermediate, sửa cấu hình **certificate chain của server** thường hợp lý hơn là thêm intermediate thành trust anchor trên mọi client.

**Root vs intermediate:** Khi đưa intermediate trực tiếp vào tập trust anchor, bạn có thể thay đổi trust model: intermediate được tin theo cách khác với việc chỉ chấp nhận nó dưới root và các kiểm tra của chain. Ràng buộc extensions đối với trust anchor có thể được xử lý khác với CA nằm trong chứng thư chain, tùy implementation và chính sách. Chỉ anchor một intermediate nếu bạn hiểu **phạm vi trust** và đã kiểm các ràng buộc cần thiết.

**Leaf pinning:** Import một leaf certificate vào trustedCertEntry có thể giống cách tin trực tiếp certificate đó đối với một số validator, nhưng **không phải thay thế toàn diện** cho certificate pinning, expiry management, hostname validation và revocation policy. Khi leaf renew, pin/truststore có thể phải cập nhật. Một số TLS stack có semantics riêng đối với trust anchor được cấu hình làm leaf.

### Chain validation khác hostname verification

Một certificate được public CA ký hợp lệ cho `api-a.example.test` **không được** chấp nhận cho `api-b.example.test` chỉ vì CA là trusted. Hostname/IP phải được kiểm đối chiếu SAN theo chính sách HTTPS/TLS.

- `X509Certificate.getPublicKey()` chỉ lấy public key; không kiểm trust.
- `X509Certificate.verify(issuerKey)` kiểm chữ ký với key đã cung cấp; không kiểm đầy đủ chain/trust/hostname.
- `CertificateFactory` parse certificate, không tự quyết định certificate được tin.
- `CertPathValidator` với `PKIXParameters` có thể thực hiện kiểm path phù hợp cấu hình; hostname kiểm theo endpoint identification riêng.
- Với `HttpsURLConnection` hoặc `java.net.http.HttpClient` cấu hình chuẩn, dùng chức năng xác minh tên máy chủ của TLS stack; không tắt nó để “sửa” lỗi certificate.
- Với `SSLSocket` tự quản, có thể cần cấu hình `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` đúng ngữ cảnh; đừng giả định việc cài trust manager đã bật hostname verification ở mọi socket API.

Một TLS handshake thành công vẫn không quyết định **authorization** của request; service phải kiểm quyền/tenant và ngữ cảnh nghiệp vụ riêng.

## 7. Thêm một CA: tác động và vùng tin cậy mở rộng

Giả sử ứng dụng trước đây tin CAs `A` và `B`; bạn nhập thêm CA `C` vào truststore mà một client dùng để xác thực server:

```text
Before: trusted anchors = {A, B}
After:  trusted anchors = {A, B, C}
```

Nếu CA C có thể phát hành certificate phù hợp tên máy chủ và policy, client đó có thể xác thực một chain mới trước đây bị từ chối. **Điều này có thể đúng mục đích** (kết nối đối tác private PKI) nhưng đồng thời mở rộng những bên có khả năng cấp certificate được xét hợp lệ.

Tác động cần phân tích:

1. **Blast radius:** Có áp dụng cho một HTTP client cụ thể, một JVM, một host, toàn cluster hay nhiều service?
2. **Tên miền/identity:** CA mới có thể cấp cho những DNS nào? Có name constraints được thực thi và application-level allowlist không?
3. **Nguồn chứng thư:** CA root được lấy từ kênh độc lập đáng tin nào? Fingerprint SHA-256 có được đối chiếu ngoài kênh vận chuyển file không?
4. **Mục đích:** Đây là CA dùng cho server TLS, client mTLS, code signing hay mục đích khác? Truststore dùng đúng phạm vi chưa?
5. **Mức độ rủi ro:** Nếu CA bị compromise hoặc governance yếu, attacker có thể lợi dụng để tạo chain hợp lệ cho endpoint mà client gọi (nếu các điều kiện khác như SAN/hostname cũng khớp).
6. **Vòng đời:** CA mới có expiry, rotation, revocation mechanism và chủ thể vận hành rõ ràng không?
7. **Rollback:** Nếu CA mới không nên tin nữa, có thể loại bỏ nhanh và khiến các context/pool liên quan nạp lại chính sách không?
8. **Môi trường:** Dev/staging CA có vô tình xuất hiện trong production truststore không?

**Không “sửa lỗi PKIX” bằng cách import mọi certificate gặp trên mạng.** Trước tiên xác định vấn đề thật: thiếu intermediate, sai chain, hostname khác, certificate hết hạn, hệ thống đồng hồ sai hay public root không nằm trong runtime.

### Một CA có thể ảnh hưởng nhiều hostname

Một root CA được tin trong client WebPKI nói chung có khả năng tạo đường tin cậy cho các hostname khác nhau khi chứng thư được cấp đáp ứng quy tắc/constraint. Thêm CA vào truststore dùng toàn JVM có thể **mở rộng cả các kết nối không liên quan tới đối tác được thêm**.

Giới hạn TCP hostname đích, xác thực tên máy chủ và application authorization giúp giảm rủi ro, nhưng chúng không thay thế việc **thu hẹp tập trust anchor** cho client chỉ cần kết nối một domain nội bộ.

## 8. Xóa hoặc thay thế CA: nguy cơ mất kết nối

Giả sử chain server trước đây xây dựng tới CA A. Khi CA A bị xóa khỏi truststore:

- **TLS handshake mới** có thể thất bại với lỗi kiểu `PKIX path building failed`, `unable to find valid certification path` hoặc `SSLHandshakeException` tùy provider và nguyên nhân.
- Nếu vẫn có **một đường chứng thư khác hợp lệ** tới anchor B được tin, handshake có thể tiếp tục thành công; không mặc định “xóa A chắc chắn mọi kết nối tới server X đều fail”.
- Các connection pool, TLS session resumption hoặc kết nối TLS đã thiết lập có thể **không lập tức** áp policy mới. Để thay chính sách nhanh, cần đánh giá đóng pool/reload context/restart và giới hạn session lifetime.
- Một số ứng dụng chạy custom `SSLContext` nạp truststore lúc khởi động sẽ không thấy thay đổi file cho đến khi được reload.

**Root CA rollover** thường cần một giai đoạn overlap:

```text
T0: client tin OldRoot; server trình chain tới OldRoot
T1: phân phối trust bundle {OldRoot, NewRoot}
T2: server chuyển sang chain tới NewRoot
T3: xác minh không còn client quan trọng cần OldRoot
T4: loại OldRoot theo risk/retention policy và kiểm hậu kiểm
```

Nếu xóa OldRoot trước khi client và server được cập nhật đồng bộ, có thể gây outage. Nếu giữ OldRoot quá lâu, bạn kéo dài trust đối với một CA không còn cần thiết hoặc đã có rủi ro. **Overlap phải có thời hạn và inventory**, không phải để hai root tồn tại mãi.

### Cross-sign và alternate path

Một certificate của intermediate hoặc root có thể được cross-sign, tạo nhiều đường path. Việc bạn xóa một root không phải lúc nào loại được mọi đường hợp lệ qua một root khác. Do đó, test phải dùng **chain thực tế, hostname, algorithm policy và truststore chính xác trên từng client/runtime**, thay vì chỉ so sánh số lượng CA entries.

### Khi bị nghi compromise CA

Trong một security incident, ưu tiên containment và distrust nhanh có thể quan trọng hơn duy trì mọi kết nối. Tuy nhiên cần đánh giá mức ảnh hưởng, thay CA/chain, cập nhật client trust, bảo vệ identity và log, thông báo đối tác, giám sát handshake. Việc chỉ xóa một root mà server vẫn dùng chain cũ sẽ dẫn đến outage nếu chưa xử lý luồng trust migration.

## 9. Tạo truststore riêng bằng keytool, không sửa trực tiếp cacerts

Quy trình minh họa cho **một private CA của đối tác**:

### 9.1 Nhận CA và xác minh nguồn gốc

Bạn nhận file `partner-root.pem` qua kênh đã được kiểm soát. Kiểm fingerprint SHA-256 và metadata certificate:

```bash
keytool -printcert -file partner-root.pem
```

Đây là bước **kiểm tra**, chưa phải tạo trust. Fingerprint cần đối chiếu với giá trị được xác nhận **qua kênh độc lập đáng tin**, ví dụ trang chính thức/đầu mối bảo mật đã được xác minh, thay vì chép fingerprint từ cùng email có thể đã bị sửa. Kiểm subject/issuer, Basic Constraints CA, key usage, thời hạn, thuật toán và phạm vi sử dụng phù hợp.

### 9.2 Import vào một file ứng dụng riêng

```bash
keytool -importcert -alias partner-root-2026 \
  -file partner-root.pem \
  -keystore app-outbound-trust.p12 \
  -storetype PKCS12
```

Các dấu `\` cuối dòng là cú pháp shell kiểu POSIX; trên Windows PowerShell, nhập thành một dòng hoặc dùng cú pháp tiếp dòng phù hợp. Công cụ có thể yêu cầu tạo/nhập password và xác nhận chứng thư. **Không dùng `-noprompt` chỉ để tránh kiểm nguồn tin cậy.** Đừng đưa mật khẩu vào `-storepass password` trên shell history/process arguments.

Kiểm lại:

```bash
keytool -list -v -keystore app-outbound-trust.p12 \
  -storetype PKCS12 -alias partner-root-2026
```

File vừa tạo chứa **một tập tin cậy do bạn chọn**, không tự chứa tất cả public CAs của `cacerts`. Nếu bạn cần public roots, thiết kế rõ ràng client nào dùng nguồn nào và cách duy trì approved bundle; không tự ghép không kiểm soát.

### 9.3 Triển khai

- Đặt file trong đường dẫn chỉ ứng dụng và người vận hành có quyền đọc, ví dụ mounted Secret/ConfigMap tùy chính sách; truststore chứa certificate công khai nhưng **tính toàn vẹn cấu hình rất quan trọng**, nên hạn chế người có quyền sửa.
- Version artifact, ghi fingerprint/certificate inventory và đường dẫn source-of-truth. Có review thay đổi CA và change window.
- Không commit password hoặc private key vào repository.
- Cấu hình đúng JDK/runtime chạy ứng dụng; chỉ định `trustStoreType` nếu có khả năng runtime khác default.
- Chạy test handshake với endpoint thật **sử dụng chính app trust manager** và kiểm hostname; không chỉ `keytool -list`.
- Có kế hoạch rollover certificate/CA và rollback nếu endpoint outage.
- Nếu custom store cần chứa public roots, nó phải được cập nhật theo lịch và chính sách distro/vendor, không copy `cacerts` một lần rồi quên nhiều năm.

## 10. Java 17+: tạo TrustManager và SSLContext có phạm vi riêng

Ví dụ độc lập minh họa **ranh giới phạm vi tin cậy** mà không cần kết nối mạng và không sửa `cacerts`. Nó lấy default trust manager từ provider, tạo một PKCS#12 KeyStore **rỗng trong RAM**, sau đó (nếu JDK có default anchors) thêm đúng một certificate được chọn từ default issuers để chứng minh custom manager chỉ nạp certificate đã thêm.

**Chú ý:** việc demo lấy phần tử đầu của danh sách public CAs là cách tạo input test từ runtime, **không phải chính sách lựa chọn CA cho ứng dụng thật**. Production phải chọn CA theo danh tính/fingerprint được xác minh độc lập, không theo vị trí trong mảng hoặc tên issuer đơn thuần. Chương trình cũng **không thực hiện TLS handshake**, nên không chứng minh hostname, PKIX hoặc revocation của endpoint nào.

Lưu thành **`TrustScopeDemo.java`**:

```java
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class TrustScopeDemo {
    private static X509TrustManager firstX509(TrustManager[] managers) {
        for (TrustManager manager : managers) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        throw new IllegalStateException("No X509TrustManager");
    }

    private static X509TrustManager trustManagerFor(KeyStore store)
            throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        return firstX509(factory.getTrustManagers());
    }

    public static void main(String[] args) throws Exception {
        // null means the configured/provider default trust source, not
        // necessarily a single fixed cacerts file on every Java runtime.
        X509TrustManager defaults = trustManagerFor(null);
        X509Certificate[] defaultIssuers = defaults.getAcceptedIssuers();

        KeyStore isolated = KeyStore.getInstance("PKCS12");
        isolated.load(null, "only-a-demo".toCharArray());
        X509TrustManager emptyManager = trustManagerFor(isolated);
        System.out.println("Custom empty entries: " + isolated.size());
        System.out.println("Custom empty accepted issuers: " +
                emptyManager.getAcceptedIssuers().length);

        if (defaultIssuers.length == 0) {
            System.out.println("Import demonstration skipped: no default CA");
            return;
        }

        // DEMO ONLY: do not select a production trust anchor by array position.
        isolated.setCertificateEntry("demo-ca", defaultIssuers[0]);
        X509TrustManager scoped = trustManagerFor(isolated);

        SSLContext clientContext = SSLContext.getInstance("TLS");
        clientContext.init(null, new TrustManager[] {scoped},
                new SecureRandom());

        System.out.println("Custom entries after import: " + isolated.size());
        System.out.println("Custom accepted issuers after import: " +
                scoped.getAcceptedIssuers().length);
        System.out.println("TLS context initialized: " +
                (clientContext.getSocketFactory() != null));
        System.out.println("Separate trust manager objects: " +
                (defaults != scoped));
        System.out.println("Default issuer list unchanged: " +
                (defaults.getAcceptedIssuers().length == defaultIssuers.length));
    }
}
```

Lệnh chạy với JDK 17+:

```bash
javac --release 17 TrustScopeDemo.java
java TrustScopeDemo
```

**Stdout chính xác trong môi trường thử nghiệm có default CA roots:**

```text
Custom empty entries: 0
Custom empty accepted issuers: 0
Custom entries after import: 1
Custom accepted issuers after import: 1
TLS context initialized: true
Separate trust manager objects: true
Default issuer list unchanged: true
```

Nếu runtime không có bất kỳ default CA được công bố trong `getAcceptedIssuers()`, hai dòng đầu vẫn xuất hiện, sau đó chương trình in `Import demonstration skipped: no default CA` và kết thúc. Đây là khác biệt môi trường được xử lý có chủ đích, không phải test secret hoặc lỗi TLS.

### Phân tích từng bước

1. `TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())` lấy implementation theo provider. `factory.init((KeyStore) null)` chọn nguồn trust **mặc định đã cấu hình**, không chứng minh đã mở đúng file `cacerts` cụ thể.
2. `KeyStore.getInstance("PKCS12")` + `load(null, ...)` tạo một keystore mới **rỗng trong RAM**. Nó không sửa `cacerts`, không ghi file và không làm default JSSE đổi trạng thái.
3. Khởi tạo factory với empty custom store cho `getAcceptedIssuers().length == 0` trong môi trường/provider đã thử nghiệm. Một trust manager không có trust anchor sẽ không được dùng như client trust policy cho public web.
4. `setCertificateEntry("demo-ca", certificate)` thêm **đúng một** certificate vào custom KeyStore. Tạo `TrustManagerFactory` mới nạp đúng nguồn này; nó không tự lấy union tất cả public CAs.
5. `clientContext.init(null, new TrustManager[] {scoped}, ...)` thiết lập một SSL context riêng dùng trust manager được chọn, không thay đổi `SSLContext.getDefault()`.
6. `getSocketFactory() != null` cho biết context đã khởi tạo, **không** chứng minh kết nối tới hostname bất kỳ đã được xác thực. Test mạng/cert chain/hostname là việc khác.
7. `defaults != scoped` minh họa hai đối tượng trust manager độc lập. `Default issuer list unchanged` kiểm rằng code không sửa state của manager ban đầu; nó không đánh giá CA trust của hệ thống ngoài.

Một số JDK có default truststore bị cấu hình rỗng hoặc `javax.net.ssl.trustStore` trỏ tới một file riêng. Vì vậy **số lượng root mặc định không cố định** giữa các máy hoặc build; ví dụ chỉ in các thuộc tính kiểm tra ổn định trên môi trường đã thử.

### Tạo SSLContext từ truststore file thật

Ví dụ sau là **đoạn minh họa trong method**, không phải chương trình độc lập. Giả sử `char[] password` được lấy an toàn từ secret manager và `Path path` trỏ tới file `PKCS12` được duyệt:

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

KeyStore store = KeyStore.getInstance("PKCS12");
try (InputStream input = Files.newInputStream(path)) {
    store.load(input, password);
}

TrustManagerFactory tmf = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
tmf.init(store);

SSLContext scopedContext = SSLContext.getInstance("TLS");
scopedContext.init(null, tmf.getTrustManagers(), null);
```

Đoạn này không cài JVM-wide truststore. `password` là biến nhạy cảm, cần dọn dẹp khi có thể và không log. `KeyStore.load` với null password có semantics integrity-check tùy định dạng/provider; hãy dùng đúng password và policy thay vì mặc định file có thể được tải “không cần bảo vệ”.

`scopedContext` chỉ có tác dụng với client/socket **thực sự được cấu hình dùng nó**. Chẳng hạn Java `HttpClient` hỗ trợ builder với `.sslContext(scopedContext)`; nếu bạn tạo context rồi không gắn vào HTTP client, các request vẫn có thể dùng default context.

**Lưu ý endpoint verification:** trust manager xác minh chain nhưng hostname vẫn phải được kiểm theo TLS API. Java `HttpClient`/HTTPS client thông thường có cơ chế endpoint identification, trong khi một raw `SSLSocket` có thể cần cấu hình `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")`. Không tắt hostname verification, không dùng “trust all” để chữa chain lỗi.

## 11. Thay đổi root CAs có ảnh hưởng TLS đang hoạt động ngay không?

Không nên giả định một sự kiện “thay file truststore” có tác dụng tức thì với toàn bộ process. Có nhiều tầng cache/state:

- **File truststore:** cập nhật file trên disk.
- **`KeyStore` object trong JVM:** đã load certificate entries tại một thời điểm.
- **`TrustManagerFactory` / `X509TrustManager`:** đã init trên một tập trust anchors nhất định.
- **`SSLContext`:** đã init với các trust managers, key managers và session caches.
- **Connection pool / TLS session:** các TCP/TLS connection đã handshake trước đó có thể được tái sử dụng.
- **Process/container:** có thể chưa reload file/config sau khi cập nhật secret mount.

Thay CA ở file **không** bảo đảm tất cả các tầng trên thay đổi ngay. Một thiết kế reload đúng thường:

1. Nhận diện version mới và kiểm tính toàn vẹn của trust bundle.
2. Tải keystore mới vào bộ nhớ, xác minh các certificates/anchors dự kiến.
3. Tạo lại `TrustManagerFactory` và `SSLContext` theo cấu hình mới.
4. Cấu hình client/connection pool mới và chuyển traffic theo cơ chế an toàn.
5. Đóng hoặc quay vòng pool/session cũ theo threat model, đặc biệt khi **thu hồi trust** khẩn cấp.
6. Theo dõi handshake metrics, canary health, rollback với phiên bản đã duyệt.

`SSLContext` không có API “sửa một CA trong mọi connection đang tồn tại” theo nghĩa chung. Cần hiểu lifecycle của HTTP/TLS library cụ thể và xem liệu session resumption có dùng lại trạng thái xác thực cũ hay không.

## 12. Kiểm tra certificate chain và hostname trong production

Sau khi có truststore, phải kiểm **TLS kết nối thực tế** với endpoint theo đúng hostname/SNI và chain:

| Kiểm tra | Câu hỏi |
| --- | --- |
| Trust anchor | Certificate chain có kết thúc ở một anchor được policy chấp nhận? |
| Path building | Intermediate(s) có đầy đủ, path constraints và signature hợp lệ? |
| Validity | Các certificate trong chain có đáp ứng thời hạn theo policy? |
| Algorithm | Algorithm/key size có nằm trong danh sách cho phép của runtime và ứng dụng? |
| ServerAuth/EKU | Leaf có mục đích phù hợp để làm TLS server? |
| SAN/hostname | Endpoint DNS/IP có khớp danh tính được cấp trong certificate? |
| Revocation/distrust | Có yêu cầu OCSP/CRL, denylist hoặc cơ chế distrust khác? |
| Scope | Client này chỉ dùng đúng tập CA cần thiết, không tin nhầm dev/staging CA? |
| Application authorization | Dù TLS hợp lệ, request có quyền và đúng tenant/resource? |

**Không phải mọi JSSE trust manager mặc định bật OCSP/CRL revocation checking giống nhau trong mọi cấu hình.** Revocation là quyết định policy và cách cấu hình; đừng tuyên bố “có cacerts thì CA bị thu hồi sẽ bị từ chối ngay”. Cơ chế browser WebPKI (có thể có danh sách distrust/CT policy riêng) cũng không thể suy ra tương đương với mọi Java HTTP client.

**Đừng tự viết kiểm hostname đơn giản** như `host.endsWith("example.com")`. Nó dễ mắc lỗi suffix/punycode/wildcard và spoofing. Ưu tiên endpoint identification do TLS stack hỗ trợ.

## 13. Ví dụ sự cố: thay tập CA làm ứng dụng lỗi

**Bối cảnh:** Java service gọi `https://billing.partner.test` và một public API khác. Hệ thống trước đây dùng default JSSE roots. Đội vận hành thêm private CA của đối tác bằng cách đặt:

```text
javax.net.ssl.trustStore=/etc/app/partner-only.p12
javax.net.ssl.trustStoreType=PKCS12
```

File mới chỉ chứa một private root CA.

**Hiện tượng:** kết nối tới `billing.partner.test` hoạt động, nhưng kết nối tới public API bắt đầu thất bại với lỗi `PKIX path building failed`, dù trước đây vẫn hoạt động.

**Nguyên nhân có thể:** custom truststore trở thành tập CA của default trust manager thay cho public roots. Việc tạo file mới **không** làm JSSE tự cộng private CA vào `cacerts`. Đó là tác động có phạm vi **JVM default TLS**, không phải chỉ endpoint đối tác.

**Phương án ưu tiên:**

- Public HTTP client dùng default trust policy được duyệt.
- Partner HTTP client dùng `SSLContext` riêng chỉ tin private CA/approved anchors.
- Cả hai vẫn bật hostname verification, giới hạn HTTP hosts và kiểm request authorization.
- Nếu product bắt buộc một JVM-wide bundle chung, tạo **curated bundle** có version gồm tất cả CA được phép và kiểm mọi downstream trước rollout; hiểu blast radius lớn hơn.

**Trường hợp khác:** xóa một root CA để hạn chế rủi ro nhưng không recreate long-lived connection pools. Trong một khoảng thời gian, kết nối cũ vẫn có thể hoạt động; sau restart mới thấy handshake fail. Kiểm thử phải bao gồm **new handshakes** và behavior sau refresh context/pool, không chỉ reuse kết nối đang mở.

### Sai lầm nghiêm trọng hơn: “trust all certificates”

Code như sau là **mã giả anti-pattern**, không phải triển khai hợp lệ để sử dụng:

```java
// KHÔNG dùng: chấp nhận mọi certificate chain mà không kiểm trust.
X509TrustManager trustAll = /* custom implementation
                               với checkServerTrusted() rỗng */;
```

Dù sửa được lỗi bắt tay ở môi trường thử, nó loại bỏ ranh giới xác thực peer và mở đường man-in-the-middle. Tương tự, tắt hostname verification không làm chain đáng tin hơn. Phải sửa nguyên nhân: đúng root/intermediate, hostname, endpoint certificate, thời hạn, thuật toán và cấu hình client.

## 14. Phân tích lỗi TLS theo từng lớp

Một số thông báo thường gặp:

| Hiện tượng | Nguyên nhân khả dĩ | Nên kiểm trước |
| --- | --- | --- |
| `PKIX path building failed` | Không dựng được chain tới trust anchor được phép | Runtime nào đang chạy, truststore nào, root/intermediate chain thực tế |
| `unable to find valid certification path` | Thiếu trust anchor/path phù hợp | `cacerts` vs custom truststore, chain server, constraints |
| `No subject alternative DNS name matching ...` | Hostname không khớp SAN | URL/SNI/hostname thực tế và leaf SAN |
| `CertificateExpiredException` / `CertificateNotYetValidException` | Leaf/chain certificate hết hoặc chưa tới hiệu lực | Server cert, chain, đồng hồ hệ thống |
| `Algorithm constraints check failed` | Key size/algorithm/signature bị policy loại | JDK security properties, certificate chain, kế hoạch chuyển thuật toán |
| `Invalid keystore format` | File type sai hoặc dữ liệu không phải keystore phù hợp | `PKCS12` vs `JKS`, file đúng, quyền đọc, integrity |
| `Keystore was tampered with, or password was incorrect` hoặc lỗi password tương tự | Mật khẩu sai, file hỏng hoặc format/provider không khớp | Secret/config/KeyStore.load parameters |
| Handshake lỗi chỉ sau restart | Process trước đó còn manager/context cũ | Lifecycle reload, phiên bản container/JDK, property startup |
| Public HTTPS fail sau khi import private CA | Custom store đã thay public roots | Kiểm phạm vi custom truststore, nên tách context |
| mTLS client auth fail | Server truststore hoặc client identity/chain không khớp | Server trust policy, client cert, KeyManager, EKU/identity |

**Không suy nguyên nhân tuyệt đối từ một chuỗi exception.** Lỗi PKIX có thể đến từ missing intermediate, expired/algorithm constraints, truststore sai môi trường hoặc provider policy. Thu thập evidence theo thứ tự: endpoint hostname, server chain, JVM runtime, effective truststore configuration, provider, key/certificate metadata và logs handshake đã được làm sạch.

Các tùy chọn debug (chỉ bật có kiểm soát):

```text
-Djavax.net.debug=ssl,handshake,trustmanager
-Djava.security.debug=certpath
```

Output có thể rất lớn và lộ endpoint metadata hoặc thông tin nhạy cảm liên quan. Không bật toàn bộ ở production lâu dài, không gửi debug log thô cho người ngoài mà chưa rà soát.

## 15. Quy trình thay trust bundle an toàn

Trước khi **thêm hoặc xóa** trusted CAs, tạo change plan:

1. **Định nghĩa scope:** đây là truststore outbound riêng của một client, JVM default, host JDK, container, hay inbound mTLS truststore?
2. **Inventory:** liệt kê trusted roots, intermediate anchors nếu có, fingerprints SHA-256, expiry, owner, mục đích, môi trường, file format và version.
3. **Xác minh nguồn CA:** nhận certificate từ nguồn uy tín, đối chiếu fingerprint độc lập, kiểm Basic Constraints/key usage/algorithm/expiry.
4. **Đánh giá ảnh hưởng:** endpoint nào đang dùng chain tới CA bị xóa? CA mới có thể cấp cho những tên nào? Có cross-signed alternate paths?
5. **Tạo bundle version mới:** không sửa trực tiếp trên production runtime như một thay đổi không theo dõi; có review/approval và cơ chế rollback.
6. **Kiểm thử âm/dương:** endpoint đúng phải kết nối được, endpoint lạ/sai hostname hoặc CA không được phép phải bị từ chối.
7. **Triển khai theo canary:** cập nhật một subset instances, tạo TLS handshakes mới, theo dõi lỗi và độ trễ.
8. **Reload/rotate:** đảm bảo các trust managers, SSLContexts và pools thực sự dùng bundle mới; với distrust khẩn cấp, xem xét đóng session cũ.
9. **Giám sát:** metrics handshake failures theo endpoint/JVM version, success rate, các đổi root/chain ngoài dự kiến, và audit truststore deployment.
10. **Rollback an toàn:** giữ bundle trước đó nếu vẫn được phép theo security policy; trong sự cố CA bị compromise, không được “rollback” đưa CA độc hại trở lại chỉ để giảm outage.
11. **Review sau rollout:** đếm lỗi mới, cập nhật tài liệu root inventory và hạn loại bỏ CA chuyển tiếp.
12. **Đồng bộ vòng đời:** theo dõi expiry, vendor root program updates, CA rotation, JDK image rebuild và chính sách security hardening.

### Canary cần test cả phép “không tin”

Nếu chỉ test `curl/java client tới partner => 200`, bạn chưa chứng minh trust policy được thu hẹp. Một suite an toàn nên có:

- Endpoint đúng chain được duyệt → **thành công**.
- Endpoint dùng CA thử nghiệm khác, không được duyệt → **thất bại**.
- Endpoint certificate có SAN sai tên → **thất bại**.
- CA đã bị xóa và không còn valid alternate path → **thất bại**.
- Chain hợp lệ nhưng application identity/tenant không được phép → TLS có thể thành công, request **bị từ chối bởi authorization**.
- Connection pool sau reload → handshake mới dùng đúng policy, không dựa vào session cũ.
- Multi-region/container images khác nhau → nhất quán theo version hoặc khác biệt được phép có tài liệu.

Đây là cách kiểm **phạm vi trust** thực tế thay vì chỉ nhìn số certificate entries.

## 16. Các ranh giới đặc biệt: client/server và môi trường

### 16.1 HTTPS outbound tới public internet

Thường phù hợp sử dụng vetted public root bundle của runtime/vendor, với hostname verification và lifecycle update. Nếu cần pinning/CA restriction cho một ứng dụng có threat model đặc biệt, phải thiết kế cả rotation/recovery; pin một leaf có thể gây outage khi certificate renew.

### 16.2 HTTPS outbound tới private PKI

Dùng truststore riêng chỉ chứa private issuing CAs/approved anchors cần thiết, gắn vào HTTP client cụ thể. Hạn chế DNS destinations và context AAD/authorization ở ứng dụng nếu cần. Không đưa private CA dev/staging vào production JVM-wide `cacerts`.

### 16.3 Inbound mTLS

Server dùng `TrustManager` để xác minh client certificate. Việc server tin CA không tự trao mọi role cho mọi cert do CA cấp. Cần mapping certificate identity (SAN/subject với profile đúng) tới principal đã được cấp quyền, EKU clientAuth khi yêu cầu, revocation, bảo vệ chống certificate spoofing và chính sách rotation.

### 16.4 CI/CD và container

Pipeline build có thể cập nhật JDK/root bundle khi rebase image. Image chạy mới không nhất thiết dùng cùng `cacerts` với host. Ghi lại JDK vendor/version, `java.home`, trust bundle digest/version và runtime flags trên release artifact. Với Kubernetes mounted Secrets/ConfigMaps, update file không bảo đảm JVM tự hot reload; có thể cần rollout restart hoặc reload logic đã test.

### 16.5 Nhiều HTTP libraries

JSSE là cơ chế TLS phổ biến cho Java, nhưng một số native clients, gRPC/Netty/OpenSSL provider, OS-native stack hoặc custom factories có thể dùng trust configuration khác. Khi gặp hai client trong cùng JVM có hành vi khác nhau, kiểm **client implementation thực tế**, không suy rằng cả hai đang dùng `javax.net.ssl.trustStore`.

## 17. Bảng so sánh nhanh

| Tiêu chí | `cacerts`/JSSE default | Custom JVM-wide truststore | Per-client custom `SSLContext` |
| --- | --- | --- | --- |
| Nguồn trust | Runtime/provider default, có thể khác nhau | File được chỉ định cho default trust manager | Keystore/manager do client thiết kế |
| Phạm vi thông thường | Mọi client dùng JSSE default | Mọi client dùng JSSE default trong JVM | Chỉ client/context được gắn |
| Có tự cộng public roots? | Tùy nguồn default | **Không** tự cộng chỉ vì file được chỉ định | **Không**, trừ khi code chủ động cấu hình |
| Cập nhật | JDK/vendor/OS policy hoặc custom maintenance | Deploy file+reload/restart | Replace/recreate client/context theo lifecycle |
| Kiểm soát riêng partner | Khó nếu dùng chung runtime | Có nhưng blast radius lớn | Thường rõ và hẹp nhất |
| Nguy cơ lớn | JDK root drift, root ngoài scope nếu dùng chung | Làm mất public roots hoặc thêm private CA quá rộng | Quên gắn context, hostname check hoặc reload pool |
| Phù hợp | Outbound public HTTPS thông thường | Bundle approved chung đã được quản lý | Private PKI, endpoint trust policy hẹp |

Bảng mô tả **hành vi thường gặp** với JSSE và kiến trúc hợp lý, không phải lời bảo đảm mọi runtime đều nạp cùng default roots. Chỉ có kết quả kiểm tra trên **ứng dụng và JDK thực tế** mới chứng minh chính sách hiệu lực.

## 18. Lỗi thường gặp cần tránh

1. **Import CA vào một `cacerts` nhưng app vẫn fail:** Có thể app chạy JDK khác, custom SSLContext, container, native TLS hoặc chưa reload. Xác định effective runtime và trust manager.
2. **Đặt custom truststore rồi tưởng vẫn tin public web:** Custom file có thể thay default roots; cần tách context hoặc xây curated bundle theo policy.
3. **Chỉ thêm leaf server certificate để “sửa” chain:** Dễ hết hạn/renew hỏng; không giải quyết hiểu đúng trust anchor, CA và hostname.
4. **Đổi truststore password bằng lệnh và ghi trong shell history:** Rò credential. Dùng secure prompt/secret manager và quyền file.
5. **Cho rằng password truststore làm certificate thành bí mật:** Certificate công khai; password chủ yếu bảo vệ cách lưu/integrity tùy format. **Quyền sửa trust bundle** cực kỳ nhạy cảm dù cert public.
6. **Tin CA có nghĩa tin mọi application user:** CA trust là peer authentication ở TLS, authorization là tầng khác.
7. **Chỉ kiểm `CertificateFactory` parse được:** Không chứng minh chain/hostname/trust.
8. **Tắt `HostnameVerifier` hoặc cài `trustAll` để cứu production:** Loại bỏ xác thực peer và tạo MITM risk.
9. **Không nhớ mTLS server truststore:** Thêm public CA vào server client-auth trust có thể mở rộng tập principal client có certificate được xét, dù ứng dụng còn authorization.
10. **Sửa trust file mà không refresh manager/pool:** Process có thể dùng policy cũ, nhất là khi phải distrust CA nhanh.
11. **Remove root đột ngột khi partner chưa rotate:** Gây outage handshake mới. Cần overlapping trust và kế hoạch cutover.
12. **Không kiểm alternate chain/cross-sign:** Xóa một anchor có thể không làm certificate bị từ chối nếu còn valid path khác; kiểm bằng test thực tế.
13. **Copy `cacerts` vào riêng một lần và quên cập nhật:** Tập public roots stale, có thể mất CA mới hoặc giữ CA đã bị distrust.
14. **Nhầm TLS trust với DNS/hostname/IP allowlist:** CA có thể cấp nhiều tên; giới hạn đích và xác minh hostname vẫn cần.
15. **Không kiểm Java version/vendor và các runtime flags:** Hai instance cùng source code có thể có hành vi trust khác.
16. **Ghi log toàn bộ certificate và private key chỉ để debug:** Tránh lộ dữ liệu và hạn chế retention logs.
17. **Không có test negative:** Chỉ chứng minh một endpoint hoạt động, chưa chứng minh tập CA được tin là tối thiểu.
18. **Đưa certificate không được xác minh vào truststore:** Kẻ tấn công có thể tự gửi root certificate và làm hệ thống tự tin nó; chỉ import CA từ nguồn đã xác minh độc lập.

## 19. Bài tập thực hành

### Bài 1 — Xác định effective truststore

Trên một máy có nhiều JDK, tìm `java.home` của runtime chạy một service. So sánh với `keytool` trên `PATH` và các startup properties `javax.net.ssl.trustStore`. Trả lời: service sử dụng JSSE default hay client custom, và file trust thực tế nào được nạp? Không tự đọc thêm file nhạy cảm hoặc ghi đè CA store production.

### Bài 2 — Isolated trust bằng Java

Chạy `TrustScopeDemo`. Giải thích vì sao custom empty manager không tự kế thừa CA default, vì sao sau import đúng một certificate manager chỉ có một issuer trong ví dụ, và tại sao chương trình **chưa chứng minh** TLS tới hostname nào thành công. Bổ sung test với private CA certificate được xác minh fingerprint từ kênh đáng tin thay vì `defaultIssuers[0]`.

### Bài 3 — Replace vs union

Một Java app có hai HTTPS clients: client A gọi public API; client B gọi internal API dùng private CA. So sánh:

- Set một custom `javax.net.ssl.trustStore` có duy nhất private CA cho cả JVM.
- Merge public roots với private CA vào một truststore.
- Tạo hai `SSLContext` khác nhau và gắn đúng cho từng client.

Đánh giá ảnh hưởng khi private CA bị lộ và khi public root set của JDK thay đổi. Chọn cách có trust scope phù hợp nhất, giải thích trade-off về maintainability.

### Bài 4 — Rollover CA không downtime

Đối tác chuyển từ OldRoot sang NewRoot trong 60 ngày. Lập kế hoạch canary: nạp cả hai anchors trước cutover, kiểm chain và hostname, chuyển server certificate, đếm clients còn dùng chain cũ, xóa OldRoot sau thời hạn có bằng chứng. Test cả handshake mới và client có keep-alive/TLS resumption.

### Bài 5 — Kiểm thử phạm vi tin cậy

Dựng hai CA test độc lập trong lab và hai endpoint HTTPS test. Client chỉ tin CA A phải kết nối endpoint A thành công và từ chối endpoint B, đồng thời từ chối hostname không khớp dù chain do CA A cấp. Sau đó thêm CA B và chứng minh scope thay đổi **chỉ đối với client được cấu hình dùng store mới**. Không tạo CA test như trust anchor của production runtime.

### Bài 6 — mTLS và quyền nghiệp vụ

Một server bật mTLS và truststore chứa CA phát client certificates. Thêm một root CA mới dẫn đến nhiều client có khả năng trình certificate được chấp nhận hơn. Xác định vì sao vẫn cần mapping certificate identity tới tài khoản/role, EKU clientAuth, revocation và authorization. Lập negative test cho certificate của tenant khác.

### Bài 7 — Incident response

Một root CA nội bộ bị nghi compromise. Hãy mô tả hành động: inventory clients/server chains, giới hạn CA trust, thu hồi/thay chain, rotate danh tính liên quan, deploy trust bundle mới theo canary, refresh SSLContexts/sessions, log chứng cứ và theo dõi lỗi. Chỉ rõ tại sao khôi phục bundle cũ để sửa outage **có thể không được phép** trong security incident.

### Bài 8 — Debug một lỗi PKIX

Một ứng dụng mới chạy JDK trong container báo `unable to find valid certification path`, trong khi lệnh `curl` trên host thành công. Liệt kê các giả thuyết: runtime root khác, custom JVM truststore thay default, server thiếu intermediate, HTTPS hostname/SNI khác, chính sách algorithm, certificate expired, proxy TLS interception. Chọn dữ liệu kiểm thử từng giả thuyết và không tắt trust verification.

**Điều kiện hoàn thành:** bạn phân biệt được `cacerts` là một **nguồn tin cậy mặc định có phạm vi runtime/provider**, kho tin cậy riêng có thể **thay thế hoặc thu hẹp** trust anchors, và mọi thay đổi tập CA tin cậy có tác động tới **bảo mật lẫn khả năng kết nối**. Một quyết định đúng phải có proof về certificate chain, hostname, trust scope và cập nhật lifecycle thật sự của TLS client.

## 20. Tài liệu tham khảo

- [Java 17 — Java Secure Socket Extension (JSSE) Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): default truststore search order, `TrustManagerFactory`, SSL contexts và cấu hình.
- [Java 17 — TrustManagerFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html): khởi tạo trust manager từ KeyStore hoặc nguồn mặc định.
- [Java 17 — SSLContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html): init và phạm vi context TLS.
- [Java 17 — X509TrustManager](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/X509TrustManager.html): kiểm đường chứng thư và accepted issuers.
- [Java 17 — KeyStore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html): trustedCertEntry, load và storage.
- [Java 17 — SSLParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): endpoint identification algorithm.
- [Java 17 — HttpClient](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.Builder.html): cấu hình per-client SSLContext.
- [Java 17 — keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): `-cacerts`, `-list`, `-printcert` và `-importcert`.
- [RFC 5280 — X.509 PKI Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280): certificate path, trust anchors và các ràng buộc.
- [RFC 6125 — Service Identity (historic HTTPS identity rules)](https://www.rfc-editor.org/rfc/rfc6125): danh tính TLS; với thiết kế mới, đối chiếu chuẩn được cập nhật của protocol và TLS stack.
- [RFC 9525 — Service Identity in TLS](https://www.rfc-editor.org/rfc/rfc9525): phương pháp xác minh tên máy chủ/định danh dịch vụ hiện đại.
- [OWASP Transport Layer Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html): TLS peer validation và vận hành.


</details>

- [Quay lại đầu trang](#back-to-top)
