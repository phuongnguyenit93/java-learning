<a id="back-to-top"></a>

# 16. JSSE

## Menu
- [16.1 Vai trò của SSLContext, KeyManagerFactory và TrustManagerFactory](#jsse-001)
- [16.2 Khởi tạo SSLContext từ khóa riêng, chuỗi chứng thư và tập CA tin cậy](#jsse-002)
- [16.3 SSLParameters cho protocol, cipher suite, SNI, ALPN và endpoint identification](#jsse-003)
- [16.4 SSLSocket, SSLServerSocket và điều khiển thời điểm handshake](#jsse-004)
- [16.5 Yêu cầu hoặc đề nghị chứng thư phía khách bằng needClientAuth và wantClientAuth](#jsse-005)
- [16.6 X509ExtendedKeyManager chọn alias và X509ExtendedTrustManager kiểm tra đối tác](#jsse-006)
- [16.7 HttpsURLConnection, HttpClient và ranh giới cấu hình SSLContext hoặc xác minh hostname](#jsse-007)
- [16.8 SSLEngine với wrap, unwrap, delegated task và quản lý trạng thái handshake](#jsse-008)
- [16.9 Xử lý BUFFER_UNDERFLOW, BUFFER_OVERFLOW, đóng kết nối và tài nguyên bộ đệm](#jsse-009)
- [16.10 Tránh cấu hình tin mọi chứng thư hoặc bỏ qua kiểm tra hostname trong ví dụ ứng dụng](#jsse-010)
- [16.11 Chẩn đoán SSLHandshakeException, lỗi chứng thư và lỗi không có cấu hình chung](#jsse-011)

## <a id="jsse-001">16.1 Vai trò của SSLContext, KeyManagerFactory và TrustManagerFactory</a>

<details>
<summary>Xem chi tiết</summary>

# 16.1 Vai trò của SSLContext, KeyManagerFactory và TrustManagerFactory

**JSSE** (*Java Secure Socket Extension*) là tầng API bảo mật TLS/SSL trong Java, dùng những đối tượng như `SSLContext`, `SSLSocket`, `SSLServerSocket`, `SSLEngine`, `KeyManager` và `TrustManager`. Ba lớp thường xuyên xuất hiện khi cấu hình HTTPS hoặc mTLS là **`SSLContext`**, **`KeyManagerFactory`** và **`TrustManagerFactory`**. Chúng liên quan với nhau nhưng thực hiện **ba trách nhiệm khác nhau**:

- **`KeyManagerFactory`**: từ **identity keystore** chứa private key và certificate chain của ứng dụng, tạo các **KeyManager** để chọn và sử dụng **danh tính của chính bên mình** trong TLS.
- **`TrustManagerFactory`**: từ **truststore/trust policy**, tạo các **TrustManager** để quyết định có thể **tin certificate của bên đối tác** hay không.
- **`SSLContext`**: là **ngữ cảnh TLS đã cấu hình** gồm key managers, trust managers, nguồn ngẫu nhiên và TLS provider; nó tạo `SSLSocketFactory`, `SSLServerSocketFactory`, `SSLEngine`, và ngữ cảnh phiên TLS. Nó không tự sinh chứng chỉ hoặc thay thế quyết định trust.

Bài này mở đầu nhóm **16. JSSE** của Java Advance – security-cryptography. Trọng tâm là **phân biệt đúng trách nhiệm** của ba đối tượng và xác định ai giữ private key, ai kiểm certificate của peer, ai tạo socket/engine cho handshake. Để tự kiểm chứng mà không cần tạo chứng thư, mục 9 có một chương trình Java 17+ độc lập minh họa SSLContext và SSLEngine trước handshake. Việc dựng CA, certificate chain và TLS loopback được học chuyên sâu ở bài 16.2; việc buộc hoặc đề nghị client certificate được kiểm chứng bằng ma trận need/want ở bài 16.5.

> **Phạm vi:** mọi sơ đồ, snippets và phép thử trong bài nhằm làm rõ trách nhiệm JSSE; việc khởi tạo SSLContext thành công **chưa chứng minh** một phiên thực tế đã xác minh CA, SAN/hostname, client certificate hoặc authorization. Các lab chứng thư tự ký ở những bài chuyên sâu chỉ áp dụng trong môi trường thử nghiệm và phải giữ ranh giới tin cậy riêng.

## 1. Sơ đồ tổng thể: ba trách nhiệm trong JSSE

```text
                      ỨNG DỤNG JAVA
                            |
                            v
                   +----------------+
                   |   SSLContext   |
                   | protocol="TLS" |
                   +----------------+
                     ^            ^
                     |            |
     "Tôi là ai?"    |            |    "Tôi tin ai?"
                     |            |
      KeyManagerFactory          TrustManagerFactory
       (identity keystore)        (truststore / policy)
               |                          |
        KeyManager[]                 TrustManager[]
               |                          |
      PrivateKeyEntry             TrustedCertificateEntry
       - private key                 - CA/peer cert tin cậy
       - leaf certificate           - trust anchor policy
       - certificate chain
                     |            |
                     +------+-----+
                            |
                  SSLContext.init(km, tm, random)
                            |
              +-------------+-------------+
              |             |             |
      SSLSocketFactory  SSLServerSocket  SSLEngine
                            |
                       TLS handshake
                       + protected data
```

**Cách nhớ nhanh:**

| Câu hỏi trong TLS | Ai chịu trách nhiệm? | API chính |
| --- | --- | --- |
| **Bên mình trình bày certificate nào?** | `KeyManager` chọn local identity và sử dụng private key tương ứng | `KeyManagerFactory`, `X509KeyManager`/`X509ExtendedKeyManager` |
| **Certificate peer có đáng tin không?** | `TrustManager` kiểm tra chain/trust policy/điều kiện liên quan | `TrustManagerFactory`, `X509TrustManager`/`X509ExtendedTrustManager` |
| **Các manager đó được dùng khi tạo kết nối thế nào?** | `SSLContext` nhận managers rồi cấp socket/engine có cấu hình TLS | `SSLContext.getInstance`, `init`, `getSocketFactory`, `createSSLEngine` |
| **TLS client dùng hostname `localhost` hay `api.example.com`?** | Endpoint identity phải được kiểm tra theo cấu hình/giao thức | `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` trên socket/engine khi phù hợp |
| **Server yêu cầu certificate từ client hay không?** | Server socket/engine cấu hình client authentication | `setNeedClientAuth(true)`, `setWantClientAuth(true)` hoặc `SSLParameters` |
| **Record nào được mã hóa bằng key phiên?** | TLS provider/engine quản lý handshake, key schedule và record encryption | `SSLSocket`, `SSLEngine` — không tự xử lý bằng `Cipher` trong app |

Lưu ý: cả **TLS client lẫn TLS server** đều có thể dùng **KeyManager** và **TrustManager**. Khác biệt không nằm ở chữ “client/server”, mà ở việc **bên nào cần chứng minh danh tính của mình** và **bên nào cần xác minh peer**. Với **mTLS**, cả hai phía làm **cả hai việc**.

## 2. KeyStore và truststore là nguồn dữ liệu, không phải TLS manager

Trước khi cấu hình JSSE phải tách **file/kho lưu trữ** khỏi **đối tượng quản lý hành vi TLS**.

| Đối tượng | Chức năng | Có private key không? | Có thực thi trust decision không? |
| --- | --- | --- | --- |
| **`KeyStore`** | API JCA chứa entry dưới alias, có thể dựa trên PKCS#12, JKS, HSM, OS store | Có thể có | Không tự động; phụ thuộc dữ liệu và API sử dụng |
| **Identity keystore** | Một `KeyStore` được dùng làm nguồn **private key + certificate chain của ứng dụng** | Thường **có** | Không phải mục đích chính |
| **Truststore** | Một `KeyStore` được dùng chứa **certificate/CA được phép tin** | Thường **không cần** | Bản thân file không ra quyết định; **TrustManager** thực thi chính sách |
| **`KeyManager`** | Chọn local alias/certificate chain và dùng private key theo yêu cầu của handshake | Được cấp quyền dùng key | Không có nhiệm vụ xác thực peer |
| **`TrustManager`** | Xác minh certificate chain peer theo chính sách | Không cần private key peer | **Có**, khi thực hiện kiểm tra trong handshake |
| **`CertStore`** | Nguồn **certificate/CRL ứng viên** cho PKIX path building/revocation | Không | Không phải trust anchor tự thân |

**Java không có class JCA tiêu chuẩn `TrustStore`.** Từ “truststore” mô tả **vai trò** của một `KeyStore`, thường chứa `KeyStore.TrustedCertificateEntry`. Một certificate nằm trong truststore theo alias không có nghĩa mọi client/SSLContext trong JVM đều sử dụng file đó: chỉ các trust managers được cấu hình với store ấy (hoặc logic default tương ứng) mới dùng nó.

### 2.1 Các KeyStore.Entry quan trọng

| Entry type | Nội dung | Tác dụng |
| --- | --- | --- |
| `KeyStore.PrivateKeyEntry` | Private key + X.509 certificate chain | TLS server/client có certificate để chứng minh quyền sở hữu private key |
| `KeyStore.TrustedCertificateEntry` | Certificate được operator đưa vào tập tin cậy | Cung cấp trust anchor/peer certificate cho trust policy |
| `KeyStore.SecretKeyEntry` | Secret key đối xứng (ví dụ AES) | Phục vụ ứng dụng mật mã khác; **không** trở thành TLS certificate identity |

Ví dụ một file `identity.p12` chỉ có certificate nhập bằng `keytool -importcert`, **không có private key**, thì không thể cho server chứng minh nắm private key. Ngược lại, file `trust.p12` chứa CA certificate không phải là nơi server phải lấy private key khi làm TLS.

`KeyStore.isKeyEntry(alias)`, `isCertificateEntry(alias)` và `entryInstanceOf(alias,...)` giúp kiểm tra đúng loại. `getCertificate(alias)` có thể trả certificate của **PrivateKeyEntry** *hoặc* certificate-only entry, nên không đủ để kết luận alias là trusted certificate entry.

## 3. KeyManagerFactory: biến identity store thành KeyManager

Mục đích của `KeyManagerFactory` là **khởi tạo một hay nhiều `KeyManager` từ vật liệu xác thực local**. Với X.509 TLS, `X509KeyManager` hoặc `X509ExtendedKeyManager` có thể:

- Liệt kê alias local phù hợp với **loại key** và yêu cầu của peer;
- Chọn certificate chain tương ứng khi server/client cần trình bày danh tính;
- Cung cấp private key **được phép sử dụng** để TLS chứng minh quyền sở hữu trong handshake;
- Lựa chọn key khác theo SNI, key type hoặc ngữ cảnh socket/engine nếu provider và cấu hình hỗ trợ.

Một luồng điển hình:

```text
identity.p12
  |
  | alias "server"
  | PrivateKeyEntry:
  |   private RSA/EC key
  |   certificate chain: leaf -> intermediate...
  v
KeyStore.load(input, storePassword)
  |
  v
KeyManagerFactory.getInstance(
    KeyManagerFactory.getDefaultAlgorithm())
  |
  +-- init(identityStore, keyPassword)
  |
  +-- getKeyManagers() -> KeyManager[]
  |
  v
SSLContext.init(keyManagers, trustManagers, random)
  |
  v
Khi TLS cần local certificate:
  KeyManager chọn key/certificate phù hợp
```

### 3.1 Đoạn Java thiết yếu

```java
// Minh họa nằm trong method có imports/throws phù hợp.
// identity và keyPassword đã được cấp an toàn trước đó.
KeyManagerFactory kmf = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm());
kmf.init(identity, keyPassword);
KeyManager[] keyManagers = kmf.getKeyManagers();
```

`KeyManagerFactory.getDefaultAlgorithm()` đọc cấu hình mặc định/provider theo JDK (thường liên quan security property `ssl.KeyManagerFactory.algorithm`). **Không hardcode tên provider-specific** nếu ứng dụng không có yêu cầu tương ứng. Nếu chính sách buộc một provider (ví dụ HSM), hãy kiểm tra rõ thuật toán, provider và key capability trước khi khởi tạo.

**Không nhầm `KeyManagerFactory` với `KeyPairGenerator` hoặc `KeyFactory`:**

- `KeyPairGenerator` **sinh** cặp key mới;
- `KeyFactory` chuyển **biểu diễn** key/key spec;
- `KeyManagerFactory` chuẩn bị **cách JSSE sử dụng identity keys đã có** trong handshake.

### 3.2 Store password và key password

```java
// Minh họa, không phải chương trình độc lập.
KeyStore ks = KeyStore.getInstance("PKCS12");
try (InputStream in = Files.newInputStream(Path.of("identity.p12"))) {
    ks.load(in, storePassword);
}
KeyManagerFactory kmf = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm());
kmf.init(ks, keyPassword);
```

`storePassword` dùng để mở/kiểm tra kho theo type/provider. `keyPassword` dùng để lấy **private-key entries**. Hai password **có thể trùng**, đặc biệt trong ví dụ PKCS#12 để tương thích, nhưng **không được giả định luôn phải trùng** với mọi key store/provider. Nếu password đúng cho `load` nhưng `kmf.init` thất bại, cần kiểm tra key protection, alias và provider thay vì đổi sang chế độ TLS thiếu xác thực.

Trong production, password không nên hardcode như mẫu lab, không ghi trên command line/hệ thống log không bảo vệ, và không lưu private key trong repo.

### 3.3 Tên alias không phải danh tính TLS tự động

Alias `server` chỉ là **nhãn cục bộ**. Certificate mà peer kiểm tra có SAN, subject, issuer, key usage/EKU, validity, signature, trust chain và các chính sách khác. Đổi alias thành `api.example.com` **không tự sửa SAN**. Khi có nhiều private keys trong một store, key manager/provider có thể chọn alias dựa trên handshake; nếu cần chọn alias đặc biệt, cấu hình `X509ExtendedKeyManager`/framework đúng cách và kiểm thử SNI, key type, signature algorithms.

## 4. TrustManagerFactory: biến trust policy thành TrustManager

`TrustManagerFactory` khởi tạo **các `TrustManager` dùng để đánh giá chứng chỉ đối tác**. Đối với X.509, đối tượng thường là `X509TrustManager` hoặc `X509ExtendedTrustManager`.

```text
trusted-roots-and-peers.p12
  |
  | TrustedCertificateEntry: CA/peer cert được chấp thuận
  v
KeyStore.load(input, storePassword)
  |
  v
TrustManagerFactory.getInstance(
    TrustManagerFactory.getDefaultAlgorithm())
  |
  +-- init(trustStore)
  |
  +-- getTrustManagers() -> TrustManager[]
  |
  v
SSLContext.init(keyManagers, trustManagers, random)
  |
  v
Khi TLS peer trình bày certificate chain:
  TrustManager kiểm tra trust/path/constraints theo cấu hình
  => reject handshake nếu không đạt
```

### 4.1 Đoạn Java thiết yếu

```java
// Minh họa, không phải chương trình độc lập.
// trustStore là KeyStore đã load và được operator quản lý.
TrustManagerFactory tmf = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
tmf.init(trustStore);
TrustManager[] trustManagers = tmf.getTrustManagers();
```

**`TrustManagerFactory.init(trustStore)` khác `KeyManagerFactory.init(identity, password)`:** `TrustManagerFactory` không cần private key của peer. Nó làm việc với certificates/trust anchors. Không đổi hai loại factory cho nhau vì cùng dùng `KeyStore`.

Trong Java có overload `TrustManagerFactory.init(ManagerFactoryParameters)` dành cho một số cấu hình nâng cao như `CertPathTrustManagerParameters` để truyền `PKIXBuilderParameters` theo provider hỗ trợ. Đó là cách quản lý trust policy phức tạp hơn; không phải mọi dự án phải sử dụng. Trọng tâm bài này là luồng `init(KeyStore)` thường gặp.

### 4.2 Trust manager và hostname verification

Có **hai câu hỏi khác nhau** khi client kết nối `https://api.example.com`:

1. *Certificate chain có dẫn đến CA/peer key đáng tin theo chính sách và thuật toán không?*
2. *Certificate đích có thực sự **dành cho hostname api.example.com** và mục đích sử dụng đúng không?*

PKIX chain trust là nền tảng của câu hỏi thứ nhất; **endpoint identification** đảm bảo phần định danh của câu hỏi thứ hai trong TLS client được cấu hình đúng.

Khi tự tạo `SSLSocket` hoặc `SSLEngine`, cần cân nhắc tường minh:

```java
// Minh họa: socket là SSLSocket ở client mode.
SSLParameters params = socket.getSSLParameters();
params.setEndpointIdentificationAlgorithm("HTTPS");
socket.setSSLParameters(params);
socket.startHandshake();
```

Đây là **đoạn minh họa**, không phải full class. `"HTTPS"` là tên thuật toán endpoint identification tiêu chuẩn phù hợp ngữ cảnh HTTPS/TLS client. Một HTTP client cấp cao có thể đã thực hiện kiểm tra hostname, trong khi `SSLSocket`/ `SSLEngine` cấp thấp phải được cấu hình đúng để không bỏ sót. **Đừng tự coi kiểm tra chain là đủ thay thế so khớp SAN/hostname.**

### 4.3 Truststore là danh sách trust anchors, không phải kho tất cả certificate

Một `CertStore` có thể cung cấp **intermediate certificates và CRLs ứng viên** để xây/kiểm tra path PKIX. Chúng không tự trở thành trust anchors khi được tải về. Thêm một intermediate vào truststore như **trusted CA** chỉ để “chữa PKIX path building failed” có thể làm rộng phạm vi tin cậy hơn dự kiến. Trước khi thêm certificate vào truststore, cần xác minh nguồn/fingerprint, mục đích và phạm vi chấp thuận.

**TrustManager không tự chống replay ở tầng nghiệp vụ.** TLS hỗ trợ bảo vệ bản tin trong phiên, nhưng việc một HTTP request nghiệp vụ bị gửi lại theo cách hợp lệ cần xử lý ở lớp ứng dụng, ví dụ idempotency key, authorization, anti-replay theo hợp đồng giao thức.

## 5. SSLContext: phối hợp managers và tạo TLS engines

`SSLContext` là **ngữ cảnh giao thức TLS** do provider triển khai. Nó chứa cấu hình logic cho cách TLS socket/engine có thể sử dụng local credentials, trust decisions, randomness, các giao thức và phiên.

```text
SSLContext context = SSLContext.getInstance("TLS");
context.init(
    KeyManager[] localIdentityManagers,
    TrustManager[] peerTrustManagers,
    SecureRandom randomSource
);

Từ context:
  context.getSocketFactory()        -> SSLSocketFactory (client)
  context.getServerSocketFactory()  -> SSLServerSocketFactory (server)
  context.createSSLEngine(...)      -> SSLEngine (TLS engine không ràng buộc socket)
  context.getDefaultSSLParameters() -> parameters mặc định của context
  context.getSupportedSSLParameters()-> khả năng hỗ trợ
```

API căn bản:

```java
// Minh họa trong method đã tạo keyManagers và trustManagers.
SSLContext context = SSLContext.getInstance("TLS");
context.init(keyManagers, trustManagers, new SecureRandom());

SSLSocketFactory clients = context.getSocketFactory();
SSLServerSocketFactory servers = context.getServerSocketFactory();
SSLEngine engine = context.createSSLEngine("api.example.com", 443);
```

**`getInstance("TLS")` chưa có nghĩa đã cấu hình truststore hoặc đã bắt đầu handshake.** Phải gọi `init` (trừ `SSLContext.getDefault()` vốn đã sẵn sàng theo contract), sau đó tạo socket/engine và **thực hiện handshake**. Mỗi socket/engine có trạng thái kết nối riêng; `SSLContext` có thể được dùng để tạo nhiều kết nối.

### 5.1 Những hiểu nhầm về init

| Lời gọi | Nghĩa đúng |
| --- | --- |
| `SSLContext.getInstance("TLS")` | Chọn một implementation TLS; chưa kết nối và thường cần `init`. |
| `context.init(kms, tms, random)` | Thiết lập managers và nguồn ngẫu nhiên cho context; **chưa xác minh bất cứ server certificate nào**. |
| `context.init(null, null, null)` | Yêu cầu JSSE/provider dùng cơ chế/default managers và random phù hợp; **không có nghĩa bỏ qua trust validation**. Chi tiết nguồn default phụ thuộc JDK/provider/config. |
| `SSLContext.getDefault()` | Lấy context mặc định đã dùng được; có thể phản ánh system properties, provider, hoặc context được đặt bởi `setDefault`. |
| `SSLContext.setDefault(ctx)` | Đổi default context cho các lời gọi sau có sử dụng default; **không buộc mọi thư viện/khách hàng đang có context riêng chuyển đổi**. |
| `context.getSupportedSSLParameters()` | Tham số **có thể hỗ trợ**, không phải chắc chắn đang bật cho mọi socket. |
| `context.getDefaultSSLParameters()` | Giá trị mặc định của context/provider, cần kiểm tra/chính sách trước khi kết luận bảo mật. |

**Phân biệt default với an toàn:** nếu `tm` là `null`, SSLContext dùng default trust manager theo contract/provider; điều này **không đồng nghĩa tin mọi certificate**. Đừng tự tạo `X509TrustManager` với `checkServerTrusted` rỗng để tránh `SSLHandshakeException`. Nếu default truststore không chấp thuận CA nội bộ, hãy quản lý truststore riêng và xác minh nguồn certificate.

Đối với `KeyManager` `null` trên một context client, hành vi không nhất thiết giống “ứng dụng đã có certificate”. Đặc biệt, **server TLS cần private key/certificate identity** để thực hiện handshake X.509 thông thường; chỉ tạo `SSLContext` và không đưa key manager thích hợp không khiến server tự có certificate.

### 5.2 SSLContext không phải KeyStore

Khi xây một `SSLContext` từ `identity.p12` và `trust.p12`:

```text
identity.p12 -- KeyManagerFactory -> KeyManager[] --+
                                                    |
trust.p12    -- TrustManagerFactory -> TrustManager[]+--> SSLContext
                                                    |
                        new SecureRandom() ---------+
                                                    |
                                                   TLS
```

`SSLContext` **không phải file** `.p12`, không lưu private key ra đĩa bằng `store`, không duy trì danh sách CA như một `KeyStore` mới và không tự sinh keypair hay chứng chỉ. Nó là công cụ **kết nối chính sách và nguồn credential vào TLS provider**.

## 6. TLS một chiều và mTLS: ai cần factory nào?

### 6.1 TLS server authentication (thường gọi “one-way TLS”)

Trong trường hợp client HTTPS kiểm tra server nhưng server **không yêu cầu certificate từ client**:

```text
SERVER                                   CLIENT
  |                                        |
  | Identity KeyStore                      | Truststore
  | Private key + certificate chain        | Trusted CA/peer certificate
  |         |                              |          |
  | KeyManagerFactory                      | TrustManagerFactory
  |         |                              |          |
  | KeyManager[]                           | TrustManager[]
  |         |                              |          |
  +----- SSLContext -----------------------+----- SSLContext
  |                                        |
  | ---- server certificate/handshake ---> |
  |                                        |
  |          client validates chain + hostname
  |                                        |
  +------ encrypted TLS records <--------->+
```

- **Server** cần **KeyManagerFactory** với identity key để trình bày chứng chỉ.
- **Client** cần **TrustManagerFactory** với truststore để kiểm tra server; identity key manager phía client có thể không cần.
- Server có thể đã khởi tạo trust manager nhưng **không sử dụng nó để xác thực client** nếu không yêu cầu client certificate.

### 6.2 Mutual TLS — mTLS

Trong **mTLS**, ngoài server authentication, server yêu cầu và xác minh certificate của client:

```text
SERVER                                           CLIENT
  Identity key + KeyManager                    Identity key + KeyManager
  Truststore + TrustManager                    Truststore + TrustManager
            |                                            |
   server certificate/possession  ------------> xác minh server
            |                                            |
   xác minh client certificate  <------------ client certificate/possession
            |                                            |
           +-------------- TLS secure session -----------+
```

Cả hai phía đều cần nguồn identity/trust tương ứng, dù **không bắt buộc chung một CA**. Với mTLS, server bật `setNeedClientAuth(true)` để **bắt buộc** client certificate; `setWantClientAuth(true)` chỉ **yêu cầu nếu có** theo contract, không tương đương bắt buộc.

Quan trọng: mTLS chứng minh bên tham gia handshake có credential được tin theo chính sách, **không tự cấp quyền nghiệp vụ**. Ứng dụng vẫn phải ánh xạ certificate identity (SAN, subject, SPIFFE ID hoặc thuộc tính đã phê duyệt) sang tài khoản/role/tenant và kiểm tra authorization.

### 6.3 So sánh trong một bảng

| Tình huống | Server KeyManager | Server TrustManager | Client KeyManager | Client TrustManager |
| --- | --- | --- | --- | --- |
| HTTPS/TLS server auth | **Có**: server identity | Không cần để xác thực client nếu không yêu cầu client cert | Có thể `null` | **Có**: CA/peer cho server |
| mTLS | **Có** | **Có**: CA/peer cho client | **Có**: client identity | **Có**: CA/peer cho server |
| TLS client chỉ nói chuyện với server public CA | Không thuộc bên client | Không thuộc bên client | Thường không cần client identity | Dùng default trust hoặc truststore được kiểm soát |
| Server xác thực client bằng cơ chế khác (OAuth/session) | **Có** để server auth | Không cần client cert nếu không dùng mTLS | Có thể `null` | **Có** để kiểm tra TLS server |

Không bật mTLS chỉ vì có `TrustManagerFactory` ở server: phải cấu hình **server socket/engine** yêu cầu client authentication và client phải có identity credential phù hợp.

## 7. Ma trận quyết định: thành phần nào cần được cấu hình?

Đặt câu hỏi trước khi viết code: **bên nào phải trình identity**, **bên nào phải xác minh peer**, và **ai điều khiển handshake**? Bảng dưới dùng TLS X.509 thông dụng, không thay thế chính sách của provider hoặc framework cụ thể.

| Tình huống | Local key managers | Peer trust managers | Hành động tiếp theo |
| --- | --- | --- | --- |
| Java client gọi HTTPS public API, không có client cert | Thường không cần client identity | Tin public CA phù hợp | HTTP client cần thực hiện hostname identification và handshake. |
| Java server có HTTPS certificate | Cần private key + leaf/chain phù hợp | Tùy chính sách xác thực client | SSLServerSocket/engine có server credentials, chưa mặc nhiên bật mTLS. |
| Java client gọi internal API bằng mTLS | Cần client private key + certificate chain | Tin đúng CA/peer của server | Cấu hình client SSLContext rồi kiểm server name khi handshake. |
| Java server bắt buộc mTLS | Cần server private key + certificate chain | Cần trust policy cho client certs | Đặt needClientAuth trước handshake và ánh xạ peer identity thành principal. |

**Ví dụ chẩn đoán nhanh:** client nhận `PKIX path building failed` dù server có private key hợp lệ. Trước tiên kiểm **trust policy phía client**, chain server thực gửi và CA có được chấp thuận hay không; đưa thêm private key vào client thường không giải quyết nguyên nhân này. Ngược lại, khi server không tìm thấy certificate/private key dùng được, cần kiểm **KeyManagerFactory, alias, chain, key password** và sự tương thích với handshake.

**Biên trách nhiệm:** trust manager xác minh certificate theo chính sách, nhưng ứng dụng phải kiểm soát endpoint identity theo TLS client stack và authorization của nghiệp vụ. Có truststore không làm server tự bắt buộc client certificate. Mỗi lời gọi chỉ có ý nghĩa trong vai trò của nó.

## 8. Bài thực hành suy luận trước khi dùng TLS loopback

Hãy tự giải ba tình huống, nêu rõ **dữ liệu đầu vào → manager được tạo → điều kiện để handshake thành công → bằng chứng cần kiểm**:

1. **Một chiều:** server có `server-identity.p12` chứa private key và SAN `localhost`; client có `server-trust.p12` chỉ chứa public certificate tin cậy. Bên nào chạy `KeyManagerFactory.init`, bên nào chạy `TrustManagerFactory.init`, và client kiểm hostname ở thời điểm nào?
2. **mTLS:** client được cấp `client-identity.p12`, server thêm CA client vào truststore và đặt `needClientAuth(true)`. Khác biệt nào xuất hiện khi client không có certificate, có certificate đúng CA hoặc có certificate sai CA?
3. **Thay CA:** một team thêm CA mới chỉ cho partner API. Vì sao nên cấu hình `SSLContext` riêng cho partner thay vì nhập CA đó vào truststore toàn JVM, và phải test những kết nối nào?

Đối với mỗi tình huống, một `SSLContext` được tạo thành công là **điểm bắt đầu**, không phải kết luận bảo mật. Xác minh bằng handshake thật, peer certificate, endpoint identity và negative tests. Bài 16.2 triển khai cấu hình keystore/CA và handshake đầy đủ; bài 16.5 kiểm chứng bốn trường hợp client authentication; bài 16.11 hướng dẫn đọc nguyên nhân thất bại. Bài này giữ việc **giải thích trách nhiệm** và ví dụ Java độc lập về trạng thái trước handshake ở phần tiếp theo.

## 9. Ví dụ Java 17+ độc lập: SSLContext và SSLEngine chưa phải handshake

Để thấy **SSLContext không tự kết nối mạng**, chạy chương trình thứ hai **không cần bất kỳ file `.p12` nào**. Ta tạo context, init với default managers do provider chọn, tạo một `SSLEngine` và chỉ định đây là client HTTPS — **không gọi beginHandshake, không truyền bytes qua mạng**.

Lưu thành **`JsseParametersDemo.java`**:

```java
import java.security.SecureRandom;
import javax.net.ssl.*;

public final class JsseParametersDemo {
    public static void main(String[] args) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, new SecureRandom());

        SSLEngine engine = context.createSSLEngine("localhost", 443);
        engine.setUseClientMode(true);

        SSLParameters params = engine.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        engine.setSSLParameters(params);

        System.out.println(
                "Context protocol: " + context.getProtocol());
        System.out.println(
                "Engine client mode: " + engine.getUseClientMode());
        System.out.println(
                "HTTPS endpoint check configured: "
                        + "HTTPS".equals(
                                engine.getSSLParameters()
                                        .getEndpointIdentificationAlgorithm()));
        System.out.println(
                "No network handshake performed: true");
    }
}
```

```bash
javac -encoding UTF-8 JsseParametersDemo.java
java JsseParametersDemo
```

Stdout:

```text
Context protocol: TLS
Engine client mode: true
HTTPS endpoint check configured: true
No network handshake performed: true
```

Điều chương trình **đã làm**: khởi tạo một `SSLContext`, tạo `SSLEngine`, đặt client mode và cấu hình endpoint verification. Điều **chưa làm**: không nhận certificate, không kiểm tra chain, không validate hostname thực tế, không trao đổi key phiên và không mã hóa record. **`SSLContext.init` + `SSLParameters` không thay thế handshake**.

### 9.1 Vì sao SSLEngine tồn tại nếu đã có SSLSocket?

`SSLSocket` là TLS giao tiếp qua socket và thuận tiện cho I/O blocking. `SSLEngine` cung cấp **TLS engine không gắn cứng vào một Socket**; ứng dụng/framework điều khiển việc `wrap`/`unwrap` các `ByteBuffer` và vận chuyển bytes qua NIO/network layer do mình quản lý.

```text
SSLSocket:
  TCP socket + TLS engine + streams (I/O blocking interface)

SSLEngine:
  TLS handshake + record processing
             |
         application-managed buffers/network transport
```

Cả hai dùng các **managers từ SSLContext**, nhưng `SSLEngine` đòi hỏi state machine/handshake tasks/buffer management phức tạp. **Không** gọi `SSLEngine.wrap` một lần rồi coi như kết nối đã hoàn tất. Framework như Netty có thể quản lý phần này, nhưng vẫn cần cấu hình trust, hostname và identity đúng.

## 10. Vai trò các factory trong TLS handshake

Một TLS handshake thành công có nhiều bước. Sơ đồ sau là **luồng khái niệm**, không phản ánh mọi message TLS 1.2/1.3:

```text
CLIENT                                                   SERVER
   |                                                        |
   | ClientHello (versions, ciphers, key share, SNI...)      |
   |------------------------------------------------------->|
   |                                                        |
   |                                  server chọn chính sách TLS
   |<---------------------- ServerHello / certificate / proof
   |                                                        |
   | TrustManager(client):                                   |
   |  - chain tới CA/peer tin cậy?                            |
   |  - certificate/path/algorithm policy?                    |
   | SSLParameters/endpoint:                                  |
   |  - hostname/SAN khớp không?                              |
   |                                                        |
   |      [Nếu mTLS: server yêu cầu client certificate]       |
   |                                                        |
   | KeyManager(client):                                     |
   |   chọn client identity/certificate/private key          |
   |------------------------------------------------------->|
   |                                                        |
   |                           TrustManager(server):
   |                             client certificate hợp lệ?
   |                                                        |
   |<================= authenticated TLS records ===========>|
```

**Chi tiết đúng vai trò:**

- `KeyManager` hỗ trợ chứng minh **private key của local identity** khi protocol yêu cầu; không tự quyết định peer đáng tin.
- `TrustManager` đưa ra quyết định **peer certificate có chấp nhận được** theo cấu hình, nhưng cần bảo đảm endpoint identification và policy ứng dụng không bị bỏ qua.
- `SSLContext` cấp tài nguyên TLS provider, session cache, factories/engines và cấu hình; nó không tự phát chứng chỉ, quản lý root CA hay chọn route TCP.
- TLS provider còn thực hiện **key exchange, key schedule, record encryption, transcript integrity**, không cần app tự phối `Cipher`/ `KeyAgreement` cho từng record.
- **TLS 1.3** và **TLS 1.2** khác về thông điệp/kỹ thuật; không gắn các dòng sơ đồ trên với thứ tự packet bất biến của một phiên bản.

## 11. Cấu hình client authentication đúng nơi

### 11.1 need và want khác nhau

| Cài đặt server | Hành vi |
| --- | --- |
| `setNeedClientAuth(true)` | Yêu cầu client trình bày certificate được chấp nhận, nếu không thì handshake thất bại theo policy |
| `setWantClientAuth(true)` | Server **muốn** có client certificate và kiểm tra nếu được trình bày, nhưng **không bắt buộc** như need |
| Không yêu cầu client cert | Chỉ có server authentication theo cấu hình thông thường |
| Có TrustManager server nhưng không bật client auth | **Chưa tạo mTLS**: server không tự yêu cầu client certificate chỉ do có truststore |

Một công cụ test mTLS đúng phải thử cả **client có identity hợp lệ**, **client không gửi certificate**, và **client gửi certificate không được trust**. Nếu server đặt `want` và client không có certificate mà handshake vẫn thành công, điều đó **có thể đúng theo contract** — không nên đọc thành lỗi TrustManager.

### 11.2 Certificate identity khác authorization

Khi server TrustManager chấp thuận một client certificate, app vẫn phải quyết định client được phép **gọi API nào**, tenant nào, và bằng chứng identity có áp dụng cho endpoint đó không. Không tự ánh xạ `CN=admin` hoặc alias file `admin` thành quyền admin; nên dùng danh tính đã xác thực theo chính sách (SAN/URI/SPIFFE ID/fingerprint pinning hoặc PKI policy) và các quy tắc authorization tách biệt.

## 12. Default JSSE và custom SSLContext

Hệ thống Java có **cấu hình mặc định** và **context do ứng dụng dựng riêng**. Đừng nhầm chúng.

### 12.1 SSLContext.getDefault()

`SSLContext.getDefault()` trả về một context đã sẵn sàng. Nếu chưa có context được đặt bằng `setDefault`, lần đầu có thể tạo một implementation `"Default"` theo JDK/provider. Có thể có system properties như:

```text
javax.net.ssl.keyStore
javax.net.ssl.keyStoreType
javax.net.ssl.keyStorePassword

javax.net.ssl.trustStore
javax.net.ssl.trustStoreType
javax.net.ssl.trustStorePassword
```

Các thuộc tính là một phần của cấu hình JSSE theo provider phù hợp; không mặc nhiên mọi HTTP client, Netty config, HSM hoặc custom SSLContext đều sử dụng chúng. **`-Djavax.net.ssl.trustStore` không có nghĩa mọi context trong process sẽ tự thay trust managers**, đặc biệt nếu framework truyền trust manager tường minh.

Trong implementation SunJSSE, mặc định truststore có thể tham chiếu đến `jssecacerts`, rồi `cacerts` theo quy tắc khi không chỉ định đường dẫn; chi tiết phụ thuộc JDK/distribution. Một path truststore được cấu hình sai/không tồn tại có thể dẫn tới tập trust rỗng hoặc handshake lỗi, không an toàn để trông chờ tự động fallback.

### 12.2 Vì sao nên dùng context riêng cho trust domain?

Ví dụ một ứng dụng gọi API công khai và API nội bộ:

```text
HTTPS public endpoint  ---> SSLContext dùng public CA trust policy
HTTPS internal API     ---> SSLContext dùng dedicated internal CA trust policy
mTLS to partner        ---> SSLContext có client identity + partner trust policy
```

Điều này giảm **blast radius** khi thêm một CA. Việc sửa file `cacerts` toàn JVM hoặc gọi `SSLContext.setDefault` để hỗ trợ một đối tác có thể tác động nhiều kết nối không liên quan. Tốt hơn là cấu hình context/truststore đúng scope mà thư viện HTTP thực sự sử dụng, rồi test.

### 12.3 Không đánh đồng default với provider-independent

`KeyManagerFactory.getDefaultAlgorithm()` và `TrustManagerFactory.getDefaultAlgorithm()` có thể khác theo JDK/provider/security properties. `SSLContext.getInstance("TLS")` cũng phụ thuộc provider đã đăng ký. Khi ứng dụng yêu cầu HSM/FIPS hoặc một trust implementation cụ thể, phải kiểm tra **provider thực tế, thuật toán được hỗ trợ, cỡ khóa, cipher suites và chứng nhận compliance** — không kết luận chỉ dựa trên việc API xuất hiện.

## 13. Certificate chain, PKIX và hostname trong TrustManager

Khi peer trình bày certificate chain, TrustManager của JSSE thường có logic dựa trên **X.509/PKIX** và truststore:

```text
Peer leaf -> intermediate(s) -> trust anchor được chấp thuận
                |
                +-- signature checks
                +-- validity and algorithm constraints
                +-- basic/key usage/extended key usage phù hợp
                +-- certificate policies, name constraints nếu áp dụng
                +-- revocation theo cấu hình/policy
                +-- endpoint identity/hostname theo TLS client configuration
```

**Đừng tự kết luận provider đã thực hiện mọi mục** từ một cặp `SSLContext + TrustManagerFactory`. JSSE APIs/provider, hostname algorithm, revocation settings, algorithm constraints, TLS role và custom trust managers quyết định điều gì được thực thi. Với HTTPS, hostname/SAN phải khớp endpoint; với mTLS, client certificate cần được mapping vào một identity/authorization policy rõ ràng.

### 13.1 Nếu chỉ import intermediate vào truststore thì sao?

Một intermediate là **mắt xích của chain**, không nhất thiết phải được cấu hình như một **trusted CA anchor**. Nếu thêm thẳng intermediate làm anchor, ứng dụng có thể vô tình mở rộng quyền tin cậy. Khi không tìm đủ chain, hướng đúng thường là:

- Kiểm tra server đã gửi **leaf + intermediate** phù hợp hay chưa;
- Tìm nguồn intermediate đáng tin cậy và cung cấp cho path builder/provider theo chính sách;
- Giữ trust anchors trong truststore được quản lý;
- Xác minh issuer/subject, signature, key usage, SAN, certificate path và thời hạn;
- Không tắt kiểm tra chứng chỉ hoặc hostname để qua handshake.

### 13.2 Revocation không tự bật chỉ vì có truststore

CRL và OCSP là các cơ chế kiểm tra certificate bị thu hồi. `TrustManagerFactory.init(trustStore)` không có nghĩa một hệ thống đã chọn được **revocation policy hoàn chỉnh** cho mọi loại certificate. Cần đánh giá `PKIXRevocationChecker`, provider defaults, CRL/OCSP source, stapling, cache, timeout và hành vi fail-open/fail-closed theo threat model. Không copy một dòng `setRevocationEnabled(false)` từ unit test PKIX vào hệ thống thật mà không có lý do.

## 14. SSLParameters: chính sách của từng connection

Ngoài managers của context, một socket/engine còn có **cấu hình phiên**: protocol/cipher suites, endpoint identification, SNI, ALPN và client auth.

| Thuộc tính | API ví dụ | Ghi chú |
| --- | --- | --- |
| Endpoint identification | `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` | Dành cho client TLS cần xác minh hostname theo hợp đồng HTTPS |
| TLS protocols | `SSLParameters.setProtocols(...)` / socket `setEnabledProtocols` | Kiểm tra default và policy; không tự bật SSLv3/TLSv1.0 |
| Cipher suites | `SSLParameters.setCipherSuites(...)` | Chỉ chọn suites được provider hỗ trợ và chính sách chấp thuận |
| Client certificate bắt buộc | `SSLServerSocket.setNeedClientAuth(true)` / `SSLParameters.setNeedClientAuth(true)` | Cấu hình phía server để yêu cầu mTLS |
| Client certificate tùy chọn | `setWantClientAuth(true)` | Không tương đương yêu cầu cứng |
| Server Name Indication | `SSLParameters.setServerNames(...)` | Giúp server chọn virtual host/cert khi client gửi SNI; không thay thế hostname verification |
| ALPN | `SSLParameters.setApplicationProtocols(...)` | Đàm phán giao thức ứng dụng như HTTP/2 khi stack hỗ trợ |
| Socket/Engine | `SSLSocket.setSSLParameters(...)`, `SSLEngine.setSSLParameters(...)` | Áp dụng tham số trước handshake |

**Không nên hardcode mọi protocol/cipher trong một bài nhập môn**: tập supported và default thay đổi theo JDK/provider/security updates. Chọn từ chính sách bảo mật tổ chức, tránh thuật toán đã bị loại bỏ, kiểm thử interop. Nên dùng TLS 1.3 hoặc TLS 1.2 được cấu hình an toàn theo chính sách khi tương thích; không tự bật TLS cũ chỉ vì đối tác handshake thất bại.

## 15. Xử lý lỗi: phân biệt KeyManager, TrustManager và SSLContext

| Triệu chứng / Exception | Khâu có thể gây ra | Hướng xử lý |
| --- | --- | --- |
| `KeyStoreException`, `IOException` khi load PKCS#12 | Identity/truststore file sai path, hỏng, password/type không đúng | Kiểm tra path, loại store, quyền và password, không log bí mật |
| `UnrecoverableKeyException` khi `KeyManagerFactory.init` | Key password không đúng/entry không truy cập được | Phân biệt key password với store password và quyền HSM |
| `NoSuchAlgorithmException` khi tạo factory/context | Provider/JDK không hỗ trợ thuật toán yêu cầu | Kiểm tra security providers và tên algorithm |
| `KeyManagementException` khi `SSLContext.init` | Manager/config/provider không thể khởi tạo | Giữ cause, fail fast và không fallback trust-all |
| `SSLHandshakeException: PKIX path building failed` | Client/server TrustManager không tìm được path hợp lệ đến anchor theo chính sách | Kiểm tra **truststore hiệu dụng**, chain, intermediate, root, thời hạn, usages, algorithm constraints |
| `SSLHandshakeException: No subject alternative ...` | Endpoint identification/SAN không khớp hostname/IP | Cấp certificate đúng SAN và dùng hostname đúng; không bỏ hostname verification |
| `SSLHandshakeException: bad_certificate` / `certificate_unknown` | Peer certificate không được chấp thuận hoặc không phù hợp | Kiểm tra role, CA, chain, EKU, mTLS policy; tránh đoán dựa trên alert đơn lẻ |
| `handshake_failure` / `no cipher suites in common` | Không thống nhất protocol, cipher, key type, signature scheme hoặc identity | Kiểm tra JDK/provider, enabled protocols, server certificate và peer requirements |
| `SSLPeerUnverifiedException` khi đọc peer cert | Chưa có authenticated peer/session phù hợp hoặc peer không cung cấp cert | Chỉ dùng sau handshake và theo role; không coi là lỗi CA duy nhất |
| `SSLException: Connection reset` / `SocketTimeoutException` | Mạng, firewall, timeout, peer đóng hoặc kết nối treo | Tách lỗi transport với lỗi certificate; có timeout và retry chính sách |
| `IllegalStateException` của `SSLEngine`/TLS API | Gọi handshake/engine sai mode/state hoặc reinit không đúng | Tuân thủ state machine, không tự bỏ qua handshake |

### 15.1 Quy trình chẩn đoán gọn

1. **Tách phía lỗi:** client từ chối server hay server từ chối client?
2. **Kiểm tra role:** identity keystore có `PrivateKeyEntry` đúng alias/chain không? Truststore có CA/peer được chấp thuận không?
3. **Kiểm tra đúng runtime:** ứng dụng dùng custom SSLContext hay default? Có system properties bị override trong framework/container không?
4. **Xem chain:** leaf/intermediate/trust anchor, expiration, SAN, EKU, key usage, algorithm policy.
5. **Kiểm tra hostname:** client kết nối tên nào? Certificate có SAN tương ứng? IP literal khác DNS name.
6. **Kiểm tra mTLS:** server có `needClientAuth` hay chỉ `wantClientAuth`? Client có key manager không?
7. **Kiểm tra protocols/cipher suites:** TLS 1.2/1.3, provider, key type, signature algorithms, disabled algorithms.
8. **Ghi log có kiểm soát:** `-Djavax.net.debug=ssl,handshake` trong môi trường test; tránh đưa secret hoặc thông tin nhạy cảm lên nơi công khai.

**Lỗi PKIX không phải lúc nào cũng do thiếu private key.** Nếu client không tin certificate server, đó thường là luồng **TrustManager**; nếu server không có local certificate phù hợp, đó thường là luồng **KeyManager**. Phân loại đúng sẽ giúp không “sửa” nhầm bằng cách import hàng loạt CA vào JDK `cacerts`.

## 16. Nguyên tắc an toàn khi tùy chỉnh Manager

### 16.1 Không tạo trust-all X509TrustManager

Một anti-pattern hay gặp:

```java
// ANTI-PATTERN minh họa, KHÔNG dùng.
// Đây là một method, không phải class độc lập:
public void checkServerTrusted(
        java.security.cert.X509Certificate[] chain, String authType) {
    // Không kiểm tra gì -> chấp nhận certificate giả mạo.
}
```

Nếu muốn trust một CA nội bộ, hãy xác thực CA đó và **khởi tạo `TrustManagerFactory` từ truststore có đúng CA**. Không dùng empty implementation của `checkServerTrusted`, không đặt `HostnameVerifier` luôn `true` hoặc bỏ qua certificate errors. Cách “chữa nhanh” ấy vô hiệu hóa xác thực TLS và mở cửa cho MITM.

### 16.2 Hạn chế tự viết KeyManager và TrustManager

Tùy chỉnh `X509ExtendedKeyManager` có thể cần khi chọn alias theo SNI, tenant hoặc key store đặc biệt. Tùy chỉnh `X509ExtendedTrustManager` có thể cần với pinning/PKIX policy riêng. Khi làm điều đó:

- Tái sử dụng chain/path verification tiêu chuẩn, không chỉ so sánh subject string;
- Duy trì kiểm tra endpoint identity và chính sách cipher/algorithm đúng role;
- Bảo đảm mọi overload dùng `SSLSocket` và `SSLEngine` đều được triển khai đúng;
- Không chấp nhận certificate thiếu trust anchor vì “app đã tự kiểm tra fingerprint” khi fingerprint chưa được quản lý đáng tin;
- Đặt tests cho public CA, internal CA, certificate rotation, wrong hostname, expired, revoked, wrong EKU, attacker/self-signed và TLS provider khác.

### 16.3 HSM và private key không export được

Một key manager có thể sử dụng **private key handle** từ HSM/PKCS#11 provider mà không trích raw key bytes. `PrivateKey.getEncoded() == null` không nhất thiết là lỗi; đó có thể là chính sách **non-extractable**. Không cố export key chỉ để đẩy vào `.p12`. Cần cấu hình đúng keystore type/provider, permissions và khả năng thao tác ký theo TLS stack.

## 17. Tình huống tự kiểm tra

**Tình huống A — Client gọi HTTPS nhưng báo `PKIX path building failed`.**  
**Đáp:** Thường bắt đầu từ **TrustManager/TrustManagerFactory** và truststore hiệu dụng của client: root/intermediate có hợp lệ không, SAN/hostname và algorithm constraints ra sao. Không import private key vào client hoặc tắt cert verification.

**Tình huống B — Server có `trust.p12` nhưng không có `identity.p12`, muốn trình bày certificate.**  
**Đáp:** Truststore không có private key. Server cần local identity/key manager phù hợp. `SSLContext` không tự sinh certificate.

**Tình huống C — Server có trust manager chứa CA của client nhưng client không hề gửi certificate.**  
**Đáp:** Trust manager có sẵn **không kích hoạt mTLS**. Server phải bật yêu cầu client authentication (`needClientAuth`), và client phải có KeyManager/certificate đúng chính sách.

**Tình huống D — Client gọi `context.init(null, null, null)`. Có phải vừa “trust all certificates” không?**  
**Đáp:** Không. `null` yêu cầu default behavior do implementation/provider/JDK xác định; **không** có nghĩa bỏ qua xác thực peer. Kiểm tra default trust path nếu không đáp ứng policy.

**Tình huống E — `SSLContext` đã init thành công, vậy hostname đã được xác minh?**  
**Đáp:** Không. Chưa có peer certificate hoặc handshake. Hostname/endpoint identification áp dụng khi thực hiện handshake theo cấu hình client.

**Tình huống F — `KeyManagerFactory.init(store, pass)` thành công nhưng server vẫn không có certificate phù hợp.**  
**Đáp:** Xem alias/private-key entry/chain, certificate validity/usage, key type/signature algorithms, SNI và TLS provider. Khởi tạo factory không bảo đảm mọi handshake sẽ tìm được identity đúng.

**Tình huống G — `TrustManagerFactory` dùng truststore chỉ có public CA, không có private key. Có thiếu thông tin không?**  
**Đáp:** Không. Để kiểm tra chữ ký certificate chain của peer, **không cần private key của CA/peer**; chỉ dùng public certificate/trust anchors và policy.

**Tình huống H — Đã tin root CA nhưng URL `https://wrong.example` không trùng SAN của leaf.**  
**Đáp:** Phải từ chối nếu endpoint identity theo HTTPS không khớp. Chain trust và hostname là hai kiểm tra khác nhau.

**Tình huống I — Mọi dịch vụ trong process đều dùng default SSLContext; muốn thêm CA chỉ cho một partner.**  
**Đáp:** Nên tạo/customize **SSLContext riêng** cho trust domain của partner nếu client library hỗ trợ, thay vì mở rộng `cacerts` toàn JVM.

**Tình huống J — mTLS client certificate hợp lệ nhưng caller muốn thao tác thuộc tenant khác.**  
**Đáp:** mTLS chỉ là bước xác thực credential; cần **authorization ở tầng ứng dụng** gắn identity đã xác thực với quyền/tenant.

## 18. Checklist trước khi đưa JSSE vào production

- Xác định rõ **TLS server auth** hay **mTLS**; bên nào cần local private key, bên nào cần trust policy.
- Cung cấp identity `KeyStore` có `PrivateKeyEntry` và certificate chain phù hợp; tránh dùng certificate-only alias như identity.
- Cung cấp truststore đúng scope; chỉ tin CA/peer đã xác minh nguồn và được chấp thuận.
- `KeyManagerFactory.init(identity, keyPassword)`; `TrustManagerFactory.init(trustStore)`; `SSLContext.init(km, tm, random)` đúng vai trò.
- Bật **HTTPS endpoint identification** ở low-level TLS client nếu framework chưa đảm bảo; không bỏ SAN/hostname checks.
- Với mTLS, server cấu hình **needClientAuth** phù hợp, và ứng dụng ánh xạ client certificate vào authorization policy.
- Sử dụng TLS versions/cipher suites và thuật toán khóa đáp ứng chính sách của runtime hiện tại; không fallback yếu.
- Kiểm tra revocation, chain, EKU/key usage, certificate expiry, rotation, trust anchor changes và provider compatibility.
- Không hardcode private key/password, không log secret/handshake secrets, không import certificate chưa tin chỉ để chữa lỗi.
- Có thử nghiệm **tích cực** (server/client hợp lệ) và **tiêu cực** (sai CA, hostname, expired, missing private key, mTLS client không có cert).
- Kiểm thử trên đúng JDK, JSSE provider, OS/container và HTTP client được triển khai; không suy ra toàn bộ từ unit test `SSLContext.init`.
- Tách context theo trust domain khi hợp lý; quản trị CA root và private key theo quy trình bảo mật riêng.

## Tài liệu chính thức

1. [Java SE 17 — `SSLContext`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html): `getInstance`, `init`, socket factories, `SSLEngine`, default context và parameters.
2. [Java SE 17 — `KeyManagerFactory`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/KeyManagerFactory.html): tạo `KeyManager` từ local identity keystore.
3. [Java SE 17 — `TrustManagerFactory`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html): tạo `TrustManager` từ truststore hoặc parameters.
4. [Java SE 17 — `SSLSocket`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSocket.html), [`SSLServerSocket`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLServerSocket.html), [`SSLEngine`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLEngine.html): TLS handshake với socket hoặc engine độc lập transport.
5. [Java SE 17 — `SSLParameters`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): endpoint identification, client auth, protocols, cipher suites và SNI.
6. [Java SE 17 — `KeyStore`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html): loại entry và load/alias/password.
7. [Java SE 17 — JSSE Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): mô hình key/trust manager, default keystore/truststore và TLS configuration.
8. [Java SE 17 — keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): tạo và export/import certificate của lab.
9. [RFC 8446 — TLS 1.3](https://www.rfc-editor.org/rfc/rfc8446.html): các vai trò xác thực, key schedule và bảo vệ record trong TLS 1.3.
10. [RFC 5280 — X.509 PKIX](https://www.rfc-editor.org/rfc/rfc5280.html): certificate path, validity, certificate policies và trust anchors.

**Kết luận:** Hãy nhớ ba hướng tác động khác nhau: **KeyManagerFactory = cung cấp local identity/private-key credentials**, **TrustManagerFactory = xác minh certificate/trust của peer**, còn **SSLContext = ngữ cảnh phối hợp managers và TLS provider để tạo socket/engine**. Một kết nối an toàn còn đòi hỏi TLS handshake hoàn chỉnh, hostname/endpoint identity đúng, CA/trust anchors được quản lý, và **authorization riêng** khi dùng mTLS.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jsse-002">16.2 Khởi tạo SSLContext từ khóa riêng, chuỗi chứng thư và tập CA tin cậy</a>

<details>
<summary>Xem chi tiết</summary>

# 16.2 Khởi tạo SSLContext từ khóa riêng, chuỗi chứng thư và tập CA tin cậy

Trong **JSSE (Java Secure Socket Extension)**, đối tượng **`javax.net.ssl.SSLContext`** là nơi ứng dụng kết hợp hai nhóm thông tin hoàn toàn khác nhau:

1. **Danh tính của chính ứng dụng (local identity):** **private key** và **chuỗi chứng thư X.509** tương ứng, để phía Java có thể chứng minh mình sở hữu private key khi TLS handshake yêu cầu. Nguồn thường là **identity keystore**.
2. **Các CA/chứng thư mà ứng dụng chấp nhận tin cậy (peer trust):** tập **trust anchors** dùng để kiểm tra chứng thư của phía bên kia, thường được cấp phát qua **truststore** và `TrustManagerFactory`.

Hai nhóm dữ liệu được đưa vào `SSLContext` qua **`KeyManagerFactory`** và **`TrustManagerFactory`**. Tên gọi “SSLContext” là tên API lịch sử: trong chương trình hiện đại, dùng **TLS**, không bật SSLv2/SSLv3 hoặc TLS cổ không còn được phép theo chính sách.

Bài này hướng dẫn đầy đủ mô hình private key → certificate chain → `KeyManager`, trust anchors → `TrustManager`, các bước gọi `SSLContext.init(...)`, cấu hình socket và endpoint identification; có **bộ lệnh `keytool` tạo CA demo, server certificate do CA ký, truststore**, cùng **chương trình Java 17+** đã kiểm thử bắt tay TLS 1.3 qua loopback. Cuối bài phân tích mTLS, thay chứng thư, mật khẩu kho/khóa, lỗi handshake và các lỗi bảo mật phổ biến.

> **Ghi nhớ:** `KeyManager` quyết định **“tôi trình bày danh tính và private key nào?”**; `TrustManager` quyết định **“tôi có tin certificate chain của peer hay không?”**. Chúng không thể thay thế nhau.

## 1. Bốn thành phần cần phân biệt trước khi viết code

| Thành phần | Bản chất | Ai giữ | Vai trò trong TLS |
| --- | --- | --- | --- |
| **Private key** | Khóa bí mật bất đối xứng, chẳng hạn RSA hoặc EC | Chỉ bên sở hữu danh tính, hoặc HSM/KMS của bên đó | Thực hiện phép ký/chứng minh sở hữu khóa trong TLS handshake |
| **Leaf certificate** | X.509 certificate mang public key và định danh DNS/IP/SAN | Có thể công bố | Ràng buộc public key với danh tính theo thông tin do CA xác nhận |
| **Certificate chain** | Leaf rồi CA trung gian tương ứng; có thể gồm root tùy kho/format | Có thể gửi qua TLS; không phải secret | Cho phép peer xây đường dẫn đến CA mà **peer** đã tin từ trước |
| **Trusted CA/root** | Trust anchor được ứng dụng chấp thuận theo chính sách | Nằm trong truststore hoặc nguồn tin cậy được cấu hình | Là điểm kết thúc đường dẫn xác minh của certificate **peer** |

### 1.1. “Có certificate” không có nghĩa “có private key”

File `server.crt` hoặc `server.pem` thường chứa **certificate công khai**, không tự chứa private key. Khi ứng dụng cần làm TLS server và trình bày danh tính, `KeyManagerFactory` cần lấy được **private key khớp với public key của leaf certificate**, kèm chain phù hợp. Nếu chỉ nạp một trusted certificate vào `KeyStore` bằng `setCertificateEntry(...)`, ứng dụng không tự trở thành TLS server có danh tính tương ứng.

### 1.2. Root CA trong chuỗi khác root CA trong truststore

Ví dụ:

```text
                              SERVER                          CLIENT
             +--------------------------------+    +-------------------------+
             | Identity Keystore (PKCS12)     |    | Truststore (PKCS12)     |
             |   Alias: service               |    |  TrustedCertEntry: ca   |
             |     PrivateKey (secret)        |    |   Root CA certificate   |
             |     Leaf: CN=localhost         |    +------------+------------+
             |     Issuer: Demo Root CA       |                 |
             |     Chain: [leaf, Root CA]     |                 |
             +----------------+---------------+                 |
                              |                                 |
                     KeyManagerFactory                   TrustManagerFactory
                              |                                 |
                          KeyManager                       TrustManager
                              |                                 |
                              +------------+--------------------+
                                           |
                                SSLContext.init(...)
                                           |
                             SSLSocket / SSLServerSocket
```

Trong **sản phẩm**, chuỗi certificate gửi trên TLS thường gồm **leaf + intermediate**, còn **root trust anchor** được peer giữ độc lập và thông thường không cần gửi. Fixture của bài có chain **2 chứng thư [leaf, root]** vì chỉ dùng một CA demo ký trực tiếp, giúp thấy đầy đủ mối liên hệ. Việc root có mặt trong chain phía server **không làm client tự tin root đó**: client vẫn phải có root trong truststore đã cấp phát trước.

### 1.3. Identity keystore và truststore đều có thể là `KeyStore`

`java.security.KeyStore` là API trừu tượng để giữ keys/certificates. Không có hai class Java riêng tên “IdentityKeyStore” và “TrustKeyStore”:

- **Identity keystore** thường có `PrivateKeyEntry` và certificate chain của chính ứng dụng.
- **Truststore** thường có `TrustedCertificateEntry` cho CA được tin. Nó **không cần** private key của CA đó.
- Cả hai có thể dùng định dạng `PKCS12`; tên `.p12`/`.jks` không phải bằng chứng tuyệt đối về kiểu.
- `KeyStore` cũng có thể đại diện HSM/PKCS11, nơi private key không xuất ra byte.
- Java hiện đại thường dùng `PKCS12` làm keystore type mặc định, nhưng nên chọn rõ loại khi viết bài học và kiểm tra provider.

Một `PKCS12` chứa CA certificate do ứng dụng **tự nhập từ nguồn không đáng tin** không biến CA đó thành đáng tin về mặt nghiệp vụ. **Đầu vào truststore phải được xác minh và phê duyệt độc lập**.

## 2. Toàn bộ đường đi từ certificate/key đến SSLContext

Có hai factory quan trọng của JSSE:

```text
[identity key file + certificate chain]       [approved trusted CA certificates]
             |                                               |
       KeyStore identity                               KeyStore trusted
             |                                               |
       KeyManagerFactory                              TrustManagerFactory
       init(identity, keyPass)                         init(trusted)
             |                                               |
          KeyManager[]                                  TrustManager[]
             |                                               |
             +-------------------+---------------------------+
                                 |
          SSLContext.getInstance("TLS")
          SSLContext.init(keyManagers, trustManagers, SecureRandom)
                                 |
                +----------------+---------------+
                |                                |
        getSocketFactory()              getServerSocketFactory()
        SSL client socket               SSL server socket
                |                                |
        SSLParameters/handshake/peer certificate validation
```

### 2.1. `KeyStore.load` — đọc kho, chưa phải lấy key

```java
KeyStore identity = KeyStore.getInstance("PKCS12");
try (InputStream in = Files.newInputStream(Path.of("server.p12"))) {
    identity.load(in, storePassword);
}
```

Đây là **trích đoạn minh họa**, cần các `import` và biến `storePassword`. `storePassword` thường dùng khi nạp/bảo vệ tính toàn vẹn của kho theo provider và format. **Nạp thành công không có nghĩa** đã khôi phục được private key dưới alias mong muốn.

Kiểm tra nội dung cần dùng:

```java
boolean hasKey = identity.isKeyEntry("service");
Certificate[] chain = identity.getCertificateChain("service");
```

Nếu `hasKey == false`, alias đó có thể không tồn tại hoặc chỉ là certificate entry. Nếu `chain == null`, không có certificate chain thích hợp cho alias; không gọi `chain.length` trước khi kiểm tra trong code production.

### 2.2. `KeyManagerFactory.init` — phục hồi private key identity

```java
KeyManagerFactory kmf = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm());
kmf.init(identity, keyPassword);
KeyManager[] managers = kmf.getKeyManagers();
```

`keyPassword` là **mật khẩu bảo vệ private key entry** theo keystore type/provider. Nó **có thể khác** `storePassword`. Một số công cụ PKCS12, đặc biệt nhiều phiên bản `keytool`, có thể yêu cầu hoặc giả định hai mật khẩu giống nhau. Hãy dựa trên cách tạo file/provider thực tế, không coi hai khái niệm là một.

`KeyManagerFactory.getDefaultAlgorithm()` lựa chọn thuật toán key manager mặc định của môi trường; tránh hardcode tên provider/algorithm không cần thiết. `kmf.init(...)` có thể ném `UnrecoverableKeyException` khi keypass không đúng dù `KeyStore.load(...)` vừa thành công.

**Lưu ý quan trọng:** overload `KeyManagerFactory.init(KeyStore, char[])` cung cấp **một key password**. Với nhiều private-key entries được bảo vệ bằng các mật khẩu khác nhau, provider/framework có thể cần `KeyStore.Builder`/`KeyStoreBuilderParameters`, cấu hình alias hoặc nhiều context/keystore riêng. Không tự giả định mọi entry sẽ mở được.

### 2.3. `TrustManagerFactory.init` — danh sách CA tin cậy

```java
TrustManagerFactory tmf = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
tmf.init(trustStore);
TrustManager[] managers = tmf.getTrustManagers();
```

Trích đoạn này dùng `trustStore` đã được nạp và **chứa trust anchors được phê duyệt**. Factory tạo trust manager phù hợp provider, thường có `X509TrustManager`/`X509ExtendedTrustManager` thực hiện kiểm tra certificate chain theo chính sách PKIX/JSSE.

- **Không** lấy mọi certificate server vừa gửi và gọi `setCertificateEntry` để “tạo CA tin cậy” tức thời.
- **Không** thay `tmf.init(trustStore)` bằng một `X509TrustManager` tự chế trả về mà không kiểm tra: đó là **trust-all**, vô hiệu hóa xác thực peer.
- Nếu truyền `null` thay truststore cho `TrustManagerFactory.init((KeyStore)null)`, provider thường dùng **nguồn trust mặc định của môi trường**; không có nghĩa “không kiểm tra certificate”.
- Nếu truyền một truststore rỗng, thông thường handshake tới certificate không có trust anchor phù hợp sẽ thất bại.

### 2.4. `SSLContext.init` — nơi ghép hai tập manager

```java
SSLContext context = SSLContext.getInstance("TLS");
context.init(kmf.getKeyManagers(), tmf.getTrustManagers(),
             new SecureRandom());
```

Đây là đoạn kết hợp trọng tâm. Phương thức:

```text
SSLContext.init(KeyManager[] km, TrustManager[] tm, SecureRandom random)
```

Ý nghĩa:

- **`km`:** cung cấp credential của **local endpoint** khi TLS cần trình bày danh tính. TLS server thông thường cần server identity; TLS client chỉ cần client identity khi server yêu cầu **mTLS/client certificate**.
- **`tm`:** kiểm tra **peer certificate chain** khi có peer authentication. TLS client cần trust CA của server; mTLS server cũng phải tin CA của client.
- **`random`:** nguồn ngẫu nhiên mật mã cho provider JSSE, thường có thể là `new SecureRandom()` hoặc provider tự quản lý nguồn thích hợp. Không dùng `java.util.Random` thay thế.
- **`SSLContext.getInstance("TLS")`:** chọn một họ TLS được provider hỗ trợ; **không tự buộc** các socket chỉ dùng TLS 1.3. Phiên bản giao thức, cipher suites, endpoint ID và client-auth là chính sách cấu hình **trên socket/engine/SSLParameters** hoặc tầng HTTP framework.

`context.init(...)` **không mở cổng mạng, chưa gửi certificate, chưa bắt tay TLS**. Handshake chỉ xảy ra khi dùng `SSLSocket`/`SSLEngine` với kết nối thật.

## 3. Phân biệt ba cách khởi tạo thường gặp

### 3.1. TLS server một chiều (server authentication)

Server có private key + certificate chain. Client xác minh certificate server qua CA.

```text
SERVER: init(KeyManager[server identity], TrustManager[optional peer CA], random)
CLIENT: init(null, TrustManager[server CA], random)
```

Ở một chiều, server **không bắt buộc** phải có truststore dành cho client certificates nếu nó không yêu cầu client-auth; một trust manager được cấu hình phía server có thể không được dùng trong handshake đó. Client có truststore để xác thực server.

### 3.2. Mutual TLS — mTLS

Server **và** client đều sở hữu private key + certificate chain, và cả hai đều có danh sách CA tin cậy để xác thực peer.

```text
SERVER: init(KeyManager[server identity], TrustManager[trusted client CA], random)
CLIENT: init(KeyManager[client identity], TrustManager[trusted server CA], random)
SERVER: setNeedClientAuth(true) / cấu hình SSLEngine tương đương
```

**Chỉ cấu hình trust manager phía server không tự bật mTLS.** Server phải yêu cầu client certificate qua `SSLServerSocket.setNeedClientAuth(true)` hoặc `SSLParameters.setNeedClientAuth(true)` ở tầng framework. Client phải có key manager với certificate/key tương ứng.

Một số ứng dụng có thể dùng `setWantClientAuth(true)`, nghĩa là yêu cầu nhưng có thể cho phép không có client certificate; nó **không tương đương** yêu cầu bắt buộc. Nếu nghiệp vụ đòi hỏi client authentication, hãy dùng `needClientAuth` và kiểm tra ánh xạ identity/quyền.

### 3.3. Client chỉ cần xác thực HTTPS server

Một HTTPS client không có client certificate có thể khởi tạo:

```java
SSLContext clientContext = SSLContext.getInstance("TLS");
clientContext.init(null, tmf.getTrustManagers(), new SecureRandom());
```

**`km == null`** không phải “không xác thực server”; trách nhiệm kiểm tra server nằm ở **trust manager**. Trái lại, cấu hình `TrustManager[]` kiểu trust-all mới làm nguy hiểm quá trình peer authentication.

**`tm == null`** trong `SSLContext.init` cho phép provider dùng trust manager mặc định; điều đó **khác** đưa vào một array trust manager tùy chỉnh với logic cho phép tất cả.

## 4. Khởi tạo identity keystore từ private key và certificate chain

Trường hợp phổ biến nhất là đọc một **PKCS12 chứa sẵn private key và chain**. Nhưng về mặt API, ứng dụng cũng có thể đã có một `PrivateKey` và `Certificate[]` được parse/khôi phục hợp lệ, rồi tạo một `KeyStore` trong bộ nhớ:

```java
// Fragment minh họa, KHÔNG phải chương trình độc lập.
// privateKey: PrivateKey ứng với public key của leaf.
// chain: Certificate[] có thứ tự [leaf, issuer, ...].
// storePassword/keyPassword: char[] từ secret manager hoặc nguồn an toàn.

KeyStore identity = KeyStore.getInstance("PKCS12");
identity.load(null, storePassword);
identity.setKeyEntry("service", privateKey, keyPassword, chain);

KeyManagerFactory kmf = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm());
kmf.init(identity, keyPassword);
```

### 4.1. Yêu cầu với certificate chain

Chuỗi certificate cần có thứ tự từ **leaf** tới CA cấp trên. Nếu có các CA trung gian, mỗi chứng thư phải do CA ở vị trí tiếp theo ký và đáp ứng BasicConstraints, KeyUsage, thuật toán, validity, path-length, name constraints và policy.

Ví dụ:

```text
chain[0] = Leaf:       Subject=CN=api.example, Issuer=CN=Intermediate
chain[1] = Intermediate: Subject=CN=Intermediate, Issuer=CN=Root
# Root thường được client tin riêng trong truststore.
```

**Không** lấy một danh sách chứng thư bất kỳ và nối thành chain theo thứ tự tên. **Không** lấy cert của CA không tin rồi tự thêm vào truststore chỉ để TLS không báo lỗi.

Private key phải **khớp public key của leaf**. `KeyStore.setKeyEntry` có thể chấp nhận một số dữ liệu ở cấp API nhưng điều đó chưa bảo đảm handshake được; nên kiểm tra operation thật và validate đầu vào qua quy trình phát hành chứng thư.

### 4.2. Nếu private key ở file PEM

Một file PEM private key có thể ở dạng **PKCS#8**, **encrypted PKCS#8**, dạng chuyên biệt của thuật toán, hoặc dạng do một công cụ xuất. Không thể đơn giản gọi `Base64.getDecoder()` trên một blob PEM bất kỳ rồi truyền cho `SecretKeySpec`:

- `SecretKeySpec` dành cho **khóa đối xứng**, không phải `PrivateKey` RSA/EC.
- Nếu là PKCS#8 chưa mã hóa, sau khi parse đúng DER có thể dùng `PKCS8EncodedKeySpec` + `KeyFactory` của thuật toán tương ứng.
- Nếu là encrypted PKCS#8, cần giải mã bằng password/PBE phù hợp và provider; không log key bytes.
- Với HSM/PKCS11, private key có thể chỉ tồn tại dưới dạng **key handle không xuất được**, không nên chuyển thành file PEM.
- Việc parse PEM/certificate **không thay** kiểm tra khóa/certificate chain và nguồn trust.

Vì bài này tập trung **JSSE**, ví dụ chạy được sẽ dùng PKCS12 do `keytool` phát hành, tránh viết parser PEM/private-key tùy tiện.

## 5. Tạo truststore từ CA đã được phê duyệt

Trong code, chỉ khi đã có một `X509Certificate trustedCa` nhận từ **kênh cấp phát tin cậy** và kiểm chứng fingerprints/chính sách, ứng dụng mới nên đưa nó vào truststore:

```java
// Fragment: trustedCa đã được xác minh ngoài bối cảnh code này.
KeyStore trustStore = KeyStore.getInstance("PKCS12");
trustStore.load(null, trustStorePassword);
trustStore.setCertificateEntry("approved-ca", trustedCa);

TrustManagerFactory tmf = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
tmf.init(trustStore);
```

`setCertificateEntry` không cần private key của CA và **không** cần keypass riêng cho CA certificate. Nhưng **đưa certificate vào truststore là hành động cấp quyền tin cậy**, vì nó có thể cho phép chấp nhận tất cả leaf certificates hợp lệ theo CA đó và chính sách kiểm tra tương ứng.

### 5.1. CA nào nên tin?

- Với hệ thống nội bộ, truststore có thể chỉ có **CA nội bộ được ủy quyền**, không cần nhập toàn bộ CAs công cộng.
- Với HTTPS công cộng, truststore mặc định của JDK/hệ điều hành có thể đã có tập CA được quản lý; không tự sao chép danh sách CA từ một website không xác thực.
- Với mTLS, server có thể cần CA nội bộ chuyên ký **client certificates**, khác tập CA dành cho server.
- Với Certificate Pinning, nên hiểu việc pin đúng định danh/khóa và kế hoạch rotation; pin sai có thể gây outage khi đổi chứng thư.

**Truststore nhỏ** có thể giảm phạm vi tin cậy nhưng tăng nhu cầu vận hành CA rotation. **Truststore quá rộng** có thể mở rộng những issuer mà ứng dụng chấp nhận ngoài yêu cầu nghiệp vụ. Cân nhắc theo môi trường và mức rủi ro.

### 5.2. Trust store không thay hostname verification

Certificate chain có thể được một CA đã tin ký hợp lệ **nhưng mang SAN của một dịch vụ khác**. TLS client phải kiểm tra danh tính endpoint dựa trên **hostname hoặc IP đang kết nối**, không chỉ xác thực CA. Với `SSLSocket`, bật:

```java
SSLParameters params = socket.getSSLParameters();
params.setEndpointIdentificationAlgorithm("HTTPS");
socket.setSSLParameters(params);
socket.startHandshake();
```

Đây là fragment; `socket` là `SSLSocket` đang được cấu hình **trước handshake**. Tên `"HTTPS"` điều khiển quy tắc xác minh endpoint phù hợp với tên DNS/IP mà client cung cấp khi tạo socket/SSLEngine. **Không** dùng `""` hoặc bỏ thuộc tính này để lách lỗi certificate “không khớp hostname”.

Với HTTP client API cấp cao (ví dụ `HttpClient`), nhiều bước endpoint identification được framework xử lý theo hợp đồng riêng; đừng cấu hình thủ công gây xung đột hoặc vô hiệu hóa kiểm tra đã có.

## 6. Dựng CA thử nghiệm, certificate chain và truststore bằng `keytool`

Để thấy rõ **private key + certificate chain + CA tin cậy**, ta tạo ba kho/tệp trong một **thư mục test tạm, trống**:

```text
ca.p12       = CA demo, chứa private key của CA (chỉ dùng cấp chứng thư)
ca.pem       = public certificate của CA demo
server.p12   = private key server + chain [server leaf, demo CA]
server.csr   = certificate signing request (CSR) của server
server.crt   = leaf certificate do demo CA ký
trust.p12    = trusted certificate entry chứa demo CA, không có CA private key
```

**Cảnh báo:** các lệnh bên dưới dùng mật khẩu mẫu `demo-changeit` dưới dạng command-line để ai cũng tái hiện được trong môi trường cô lập. **Không làm vậy với production secret**: tham số command-line có thể lộ qua shell history, process list, CI logs. Tạo fixture ở thư mục test, không commit private key/PKCS12, xóa sau khi kiểm thử; production dùng secret manager, prompt và quy trình phát hành chứng thư được kiểm soát.

Các lệnh dưới đây chạy một dòng trên Bash, PowerShell hoặc CMD khi `keytool` của JDK ở `PATH`. Chạy theo đúng thứ tự trong **thư mục trống**.

### 6.1. Bước 1 — tạo CA demo

```bash
keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -validity 30 -dname "CN=Demo Local CA,O=Learning" -ext "BC:critical=ca:true,pathlen:0" -ext "KU:critical=keyCertSign,cRLSign" -storetype PKCS12 -keystore ca.p12 -storepass demo-changeit -keypass demo-changeit -noprompt

keytool -exportcert -rfc -alias ca -keystore ca.p12 -storepass demo-changeit -file ca.pem
```

- `ca.p12` chứa **private key CA**, tuyệt đối không đưa vào ứng dụng client/server hoặc chia sẻ lên network.
- `ca.pem` chỉ chứa **CA public certificate**, dùng để xác thực chữ ký leaf và nhập truststore test.
- `BasicConstraints: CA=true` và `KeyUsage: keyCertSign,cRLSign` giúp chứng thư có mục đích CA phù hợp trong demo.
- CA tự tạo này **không** được trình duyệt/JDK mặc định tin. Chỉ ứng dụng test chủ động import nó mới tin.

### 6.2. Bước 2 — tạo identity key và CSR của server

```bash
keytool -genkeypair -alias service -keyalg RSA -keysize 2048 -validity 30 -dname "CN=localhost,O=Learning" -ext "SAN=dns:localhost,ip:127.0.0.1" -storetype PKCS12 -keystore server.p12 -storepass demo-changeit -keypass demo-changeit -noprompt

keytool -certreq -alias service -keystore server.p12 -storepass demo-changeit -file server.csr -ext "SAN=dns:localhost,ip:127.0.0.1"
```

`server.p12` trước khi nhập chứng thư CA có một private key và certificate tự ký tạm. `server.csr` đưa public key và thông tin đề xuất để CA phát hành **leaf certificate chính thức cho demo**. SAN phải có **`DNS:localhost`** để client kết nối với tên `localhost` và kiểm tra hostname. SAN `IP:127.0.0.1` phục vụ kiểm tra khi client dùng địa chỉ IP trực tiếp.

Chỉ có `CN=localhost` **không** phải lý do để bỏ qua SAN; các TLS stacks hiện đại ưu tiên/đòi SAN để xác minh tên endpoint.

### 6.3. Bước 3 — CA ký leaf certificate

```bash
keytool -gencert -alias ca -keystore ca.p12 -storepass demo-changeit -infile server.csr -outfile server.crt -rfc -validity 30 -ext "SAN=dns:localhost,ip:127.0.0.1" -ext "KU:critical=digitalSignature,keyEncipherment" -ext "EKU=serverAuth" -ext "BC:critical=ca:false"
```

Các extension có ý nghĩa:

- **SAN:** ràng buộc chứng thư server với tên hoặc IP dự kiến.
- **KeyUsage:** phục vụ các thao tác TLS cho phép theo thuật toán và protocol.
- **ExtendedKeyUsage: `serverAuth`:** certificate được cấp cho mục đích chứng thực TLS server.
- **BasicConstraints: `ca:false`:** leaf không có vai trò CA.

CA certificate của demo và leaf đều có hạn `30 ngày` để giảm tuổi thọ fixture. Thời hạn dùng công cụ vẫn phụ thuộc lịch/đồng hồ máy; nếu hết hạn, hãy tạo fixture mới.

### 6.4. Bước 4 — lắp chain cho server và truststore cho client

```bash
keytool -importcert -alias root-ca -file ca.pem -keystore server.p12 -storepass demo-changeit -noprompt

keytool -importcert -alias service -file server.crt -keystore server.p12 -storepass demo-changeit -noprompt

keytool -importcert -alias root-ca -file ca.pem -keystore trust.p12 -storetype PKCS12 -storepass demo-changeit -noprompt
```

**Thứ tự quan trọng:** import CA certificate vào `server.p12` **trước** khi import certificate reply cho alias `service` để `keytool` có thể dựng chuỗi. Sau khi hoàn thành:

- `server.p12` alias `service` là **PrivateKeyEntry** có chain `[server leaf, CA]`.
- `trust.p12` alias `root-ca` là **TrustedCertificateEntry** không có private key.
- Client tin CA demo vì ta **chủ động** đưa CA vào truststore test; đây là hành động cấu hình tin cậy, không phải hệ quả tự nhiên của certificate server.

Kiểm tra certificate chain bằng:

```bash
keytool -list -v -alias service -keystore server.p12 -storepass demo-changeit
```

Trong output mong đợi có **`Certificate chain length: 2`**. Đừng dùng mỗi lệnh `-list` làm bằng chứng handshake hoàn chỉnh: còn phải kiểm tra private key truy cập được, trust anchor, SAN, EKU và TLS handshake thực tế.

### 6.5. Vì sao không dùng CA self-signed leaf luôn?

Một server có thể dùng certificate tự ký trong một số môi trường test và client tin pin trực tiếp. Nhưng khi học chủ đề **“khóa riêng, chuỗi chứng thư và CA tin cậy”**, việc dùng **CA demo ký leaf** cho thấy rõ các ranh giới:

- CA ký leaf **không** trao private key CA cho server.
- Server trình bày **private key server** và **certificate chain**; client không cần private key server.
- Client chỉ nắm **CA public certificate** làm điểm tin cậy.
- Một certificate leaf do CA demo ký vẫn phải có SAN phù hợp và chưa hết hạn.

## 7. Java 17+ runnable: khởi tạo hai SSLContext và bắt tay TLS 1.3

Chương trình **`JsseSslContextDemo.java`** là một minh họa đầy đủ, sử dụng các file từ mục 6. Nó:

1. Nạp `server.p12` bằng `KeyStore.load`; kiểm tra alias `service` có key entry và chain hai chứng thư.
2. Nạp `trust.p12` có một trusted CA certificate.
3. Khởi tạo `KeyManagerFactory` từ identity keystore; `TrustManagerFactory` từ truststore; ghép vào **server SSLContext**.
4. Khởi tạo **client SSLContext** chỉ từ truststore (không có private key client vì đây là **one-way TLS**).
5. Mở `SSLServerSocket` trên địa chỉ loopback `127.0.0.1` và port được hệ điều hành chọn; dùng `SSLSocket` client, thiết lập `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` rồi bắt tay **TLS 1.3**.
6. Trao đổi một byte giá trị `41` rồi server trả về `42`, chứng minh hai phía có kênh TLS thực sự, không chỉ tạo được `SSLContext`.

Không cần internet, DNS bên ngoài hay một server public. Server chạy một task nội bộ để chấp nhận **một** kết nối, sau đó đóng socket/Executor. File chỉ dùng cho test; không triển khai thành một server nhận nhiều client.

### 7.1. Mã nguồn hoàn chỉnh: `JsseSslContextDemo.java`

```java
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
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

public class JsseSslContextDemo {
    private static KeyStore loadPkcs12(Path file, char[] password)
            throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(file)) {
            store.load(in, password);
        }
        return store;
    }

    private static SSLContext createServerContext(KeyStore identity,
            char[] privateKeyPassword, KeyStore trustStore) throws Exception {
        KeyManagerFactory keys = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        keys.init(identity, privateKeyPassword);

        TrustManagerFactory trust = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trust.init(trustStore);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(keys.getKeyManagers(), trust.getTrustManagers(),
                new SecureRandom());
        return ctx;
    }

    private static SSLContext createClientContext(KeyStore trustStore)
            throws Exception {
        TrustManagerFactory trust = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trust.init(trustStore);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trust.getTrustManagers(), new SecureRandom());
        return ctx;
    }

    private static void exchangeOnce(SSLContext serverContext,
            SSLContext clientContext) throws Exception {
        try (SSLServerSocket listener = (SSLServerSocket) serverContext
                .getServerSocketFactory()
                .createServerSocket(0, 20,
                        InetAddress.getByName("127.0.0.1"))) {
            listener.setEnabledProtocols(new String[]{"TLSv1.3"});
            listener.setSoTimeout(10_000);

            ExecutorService workers = Executors.newSingleThreadExecutor();
            Future<String> serverJob = workers.submit(() -> {
                try (SSLSocket peer = (SSLSocket) listener.accept()) {
                    peer.setEnabledProtocols(new String[]{"TLSv1.3"});
                    peer.setSoTimeout(10_000);
                    peer.startHandshake();
                    int value = peer.getInputStream().read();
                    peer.getOutputStream().write(value + 1);
                    peer.getOutputStream().flush();
                    return peer.getSession().getProtocol();
                }
            });

            try {
                // Socket TCP đi đến 127.0.0.1, nhưng danh tính TLS là localhost.
                try (Socket tcp = new Socket("127.0.0.1",
                        listener.getLocalPort());
                     SSLSocket tls = (SSLSocket) clientContext
                             .getSocketFactory()
                             .createSocket(tcp, "localhost",
                                     listener.getLocalPort(), true)) {
                    tls.setEnabledProtocols(new String[]{"TLSv1.3"});
                    tls.setSoTimeout(10_000);
                    SSLParameters parameters = tls.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    tls.setSSLParameters(parameters);

                    tls.startHandshake();
                    tls.getOutputStream().write(41);
                    tls.getOutputStream().flush();
                    int response = tls.getInputStream().read();

                    System.out.println("client protocol: "
                            + tls.getSession().getProtocol());
                    System.out.println("response 42: " + (response == 42));
                }
                System.out.println("server protocol: "
                        + serverJob.get(10, TimeUnit.SECONDS));
            } finally {
                workers.shutdownNow();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: java JsseSslContextDemo server.p12 trust.p12");
        }
        String demoValue = System.getenv("JSSE_DEMO_PASSWORD");
        if (demoValue == null || demoValue.isEmpty()) {
            throw new IllegalArgumentException(
                    "Set JSSE_DEMO_PASSWORD for the local fixture");
        }
        char[] password = demoValue.toCharArray();
        try {
            KeyStore identity = loadPkcs12(Path.of(args[0]), password);
            KeyStore trustStore = loadPkcs12(Path.of(args[1]), password);

            System.out.println("identity key entry: "
                    + identity.isKeyEntry("service"));
            System.out.println("identity chain length: "
                    + identity.getCertificateChain("service").length);
            System.out.println("trusted CA entries: " + trustStore.size());

            SSLContext server = createServerContext(
                    identity, password, trustStore);
            SSLContext client = createClientContext(trustStore);
            exchangeOnce(server, client);
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
```

**Mật khẩu demo:** phần code lấy password từ biến môi trường `JSSE_DEMO_PASSWORD`, không đặt secret trực tiếp trong Java source. Biến môi trường **không phải kho bí mật an toàn mặc định**: nó vẫn có thể bị lộ qua công cụ debug, process dump hoặc runtime tùy hệ điều hành. Bài chỉ dùng password mẫu trong môi trường local; sản phẩm dùng secret manager/credential provider phù hợp. `String demoValue` không được xóa khỏi Java heap bởi `Arrays.fill(char[])`, vì Java `String` immutable; đây là hạn chế của demo.

### 7.2. Biên dịch và chạy bằng Bash/Linux/macOS

Sau khi tạo fixture ở cùng thư mục với file Java:

```bash
javac -encoding UTF-8 JsseSslContextDemo.java
export JSSE_DEMO_PASSWORD=demo-changeit
java JsseSslContextDemo server.p12 trust.p12
unset JSSE_DEMO_PASSWORD
```

### 7.3. Biên dịch và chạy bằng PowerShell/Windows

```powershell
javac -encoding UTF-8 JsseSslContextDemo.java
$env:JSSE_DEMO_PASSWORD = "demo-changeit"
java JsseSslContextDemo server.p12 trust.p12
Remove-Item Env:JSSE_DEMO_PASSWORD
```

**Không chạy trên host public**: đây là loopback TLS test, không thực hiện authorization. Đừng commit `server.p12` hoặc `ca.p12` lên repository. Nếu runtime không hỗ trợ TLS 1.3, chương trình sẽ lỗi ở bước `setEnabledProtocols`/handshake; bài này yêu cầu **Java 17+ có provider hỗ trợ TLSv1.3**, không tự fallback sang TLS cũ.

### 7.4. Stdout kỳ vọng đã kiểm thử

```text
identity key entry: true
identity chain length: 2
trusted CA entries: 1
client protocol: TLSv1.3
response 42: true
server protocol: TLSv1.3
```

Mã được **biên dịch với `javac --release 17`** và chạy trên JDK tương thích: server chain có 2 certificates, truststore có 1 CA, client thực hiện handshake TLS 1.3 với endpoint identification `HTTPS`, và byte response bằng `42`. Đây là kết quả của fixture mới được tạo đúng các bước; nếu certificate hết hạn hoặc bị tạo khác cấu trúc, số lượng chain/điều kiện handshake có thể thay đổi.

### 7.5. Giải thích đoạn code trọng tâm

**`loadPkcs12`:** tách nạp file khỏi logic TLS, dùng `try-with-resources` để đóng stream, không dùng `FileInputStream` bị quên close. Nhận `char[]` password theo vai trò nạp kho. Dùng một password demo cho hai file không biến chúng thành cùng một loại kho.

**`createServerContext`:** dùng **server identity** để tạo key managers, dùng **truststore** để tạo trust managers. Trong one-way TLS, trust manager phía server được cài sẵn để chuẩn bị cho mTLS nhưng không bắt buộc được gọi khi server không yêu cầu client certificate.

**`createClientContext`:** truyền `null` cho `keyManagers` vì client **không có identity certificate** trong one-way TLS. Client vẫn **có trust manager** của CA demo để xác minh chain server.

**`SSLContext.getInstance("TLS")`:** chọn TLS provider; `setEnabledProtocols(new String[]{"TLSv1.3"})` trên socket giới hạn profile TLS cho demo. Production phải theo khả năng tương thích và chính sách, không mở TLS cũ để sửa lỗi thiếu tin cậy.

**Socket lớp ngoài và hostname:** client mở kết nối TCP tới `127.0.0.1`, sau đó bọc bằng `SSLSocketFactory.createSocket(tcp, "localhost", port, true)` để danh tính TLS mong muốn là `localhost`. Certificate có SAN DNS:localhost, và `setEndpointIdentificationAlgorithm("HTTPS")` yêu cầu xác minh tên này. Nếu bỏ tên đích hoặc bỏ kiểm tra endpoint ID trong một raw `SSLSocket` app, có thể chấp nhận certificate của host khác dù CA đáng tin.

**`startHandshake()`:** tại đây mới thực hiện handshake và certificate validation. Nếu chain không hợp lệ, CA không được tin hoặc SAN không khớp, handshake phải thất bại theo chính sách mặc định; không xử lý lỗi bằng trust-all.

**Đọc/ghi 41→42:** chỉ để chứng minh sau handshake có một kênh ứng dụng đã được bảo vệ bởi TLS. Không có logic kiểm tra quyền người dùng, chống replay cấp ứng dụng hoặc xác thực client trong ví dụ.

**Timeouts:** `setSoTimeout(10_000)` giới hạn hoạt động socket đọc/accept theo API; trong production còn cần connect timeout, handshake-timeout phù hợp, deadline tổng thể, giới hạn concurrency và xử lý exception/retry.

**`Arrays.fill`:** giảm thời gian tồn tại của mảng password tạm; không đảm bảo xóa hết bản sao password ở môi trường/heap, cũng không thay thế secret management.

## 8. Ba điểm bảo mật mà demo cố ý không bỏ qua

### 8.1. SSLContext chỉ chuẩn bị chính sách, không xác minh endpoint ngay lập tức

Nhiều người tạo `SSLContext` thành công rồi cho rằng certificate chain đã được kiểm tra. Thực tế:

```text
SSLContext.init(...)       => Factory/manager configuration accepted
SSLSocket.startHandshake  => Protocol negotiation + certificate validation
Application authorization => Quyết định user/service có được làm nghiệp vụ?
```

`SSLContext.init` không kết nối đến host nào nên không biết hostname thực tế cần xác minh. Nó cũng không tự ép TLS 1.3. **Sai CA, sai SAN hoặc sai key entry có thể chỉ lộ ra khi khởi tạo manager hoặc handshake**, không nhất thiết ngay tại `SSLContext.getInstance`.

### 8.2. Tập tin certificate chain không tự là truststore

Khi server gửi một chain [leaf, intermediate], client sẽ dùng nó làm **candidate path**, nhưng **root phải đã nằm trong trust policy của client**. Nếu client tự nhận một root do server cung cấp và nâng thành trust anchor, attacker có thể gửi một CA tự tạo rồi ký một leaf giả.

Tương tự, việc nhập **CA demo** ở mục 6 vào `trust.p12` chỉ có ý nghĩa trong sandbox. Đừng nhập CA này vào truststore thực tế của hệ thống học tập, website public hay ứng dụng khách hàng.

### 8.3. JSSE trust manager kiểm tra chain; hostname phải được xử lý phù hợp

Trust manager mặc định dựa trên chuẩn JSSE/PKIX, nhưng **hostname verification không phải lúc nào cũng tự bật khi sử dụng raw `SSLSocket` hoặc `SSLEngine`**. Bài đã bật `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` ở phía client trước handshake.

Nếu ứng dụng dùng HTTP client cấp cao, framework có thể đã thực hiện bước này; vẫn cần kiểm thử và **không vô hiệu hóa verifier** chỉ để tránh lỗi SAN.

## 9. Khi nào cần thêm client certificate cho mTLS?

Trong **mTLS**, server không chỉ xác minh user/password hoặc bearer token; nó yêu cầu client chứng minh sở hữu một private key có certificate chain được tin theo **client-CA policy**.

Bổ sung cần thiết:

1. Tạo **client key pair + client certificate chain**, với SAN/EKU phù hợp `clientAuth` theo chính sách.
2. Cấp **client identity keystore** cho client và gọi `KeyManagerFactory.init(clientIdentity, clientKeyPassword)`.
3. Cấp **CA phát hành client certificate** vào **server truststore**. Nếu CA server và CA client khác nhau, cần các truststore phù hợp hai phía.
4. Khởi tạo client context bằng **cả** `keyManagers` và `trustManagers`, server context cũng có cả hai.
5. Bật **`needClientAuth`** ở socket/server socket/SSL engine hoặc framework; nếu không bật, server có thể không yêu cầu certificate client dù đã cấu hình trust manager.

Fragment minh họa server:

```java
// Sau khi có serverContext đã cấu hình và SSLServerSocket đã tạo:
serverSocket.setNeedClientAuth(true);
```

Fragment minh họa client:

```java
KeyManagerFactory clientKeys = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm());
clientKeys.init(clientIdentityKeyStore, clientKeyPassword);

TrustManagerFactory serverCaTrust = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
serverCaTrust.init(trustStoreOfServerCAs);

SSLContext clientContext = SSLContext.getInstance("TLS");
clientContext.init(clientKeys.getKeyManagers(),
                   serverCaTrust.getTrustManagers(),
                   new SecureRandom());
```

Các đoạn này **không phải chương trình độc lập**; giả định đã có đúng keystore/truststore. **Đừng bật `needClientAuth(true)` vào demo one-way phía trên mà không chuẩn bị client identity**, vì server có thể từ chối client không có certificate, đó là hành vi đúng.

### 9.1. mTLS chỉ xác thực vật liệu khóa; quyền nghiệp vụ còn riêng

Ngay cả khi TLS báo client certificate hợp lệ, ứng dụng còn phải ánh xạ certificate sang identity được ủy quyền theo **SAN/subject/public key** và chính sách lifecycle/tenant. Một CA đáng tin có thể cấp nhiều certificate cho nhiều service hoặc người dùng; **không có nghĩa mọi certificate do CA đó ký đều có quyền gọi mọi API**.

Cần xem xét:

- `ExtendedKeyUsage clientAuth` khi yêu cầu;
- Chain đến đúng **client CA** và revocation/expiry policy;
- Danh tính service cụ thể (SPIFFE ID/SAN hoặc quy ước nội bộ có kiểm soát);
- Quyền endpoint/method, tenant và các ràng buộc ở tầng ứng dụng.

### 9.2. Khác biệt giữa `needClientAuth` và `wantClientAuth`

| Cấu hình | Ý nghĩa | Hệ quả |
| --- | --- | --- |
| `setNeedClientAuth(true)` | Bắt buộc có client authentication | Client không có certificate hợp lệ phải bị từ chối theo giao thức/chính sách |
| `setWantClientAuth(true)` | Muốn client authentication nếu có | Có thể cho phép client không trình bày certificate, không đủ cho yêu cầu bắt buộc |
| Không bật client auth | One-way TLS thường gặp | Server trình bày identity; client xác minh server |

Một số framework tạo lại `SSLParameters` hoặc quản lý handshake theo cách riêng; hãy xác minh các cài đặt có hiệu lực trong runtime thực tế, không chỉ nhìn config file.

## 10. Định dạng kho và chuỗi chứng thư khi tích hợp với Java

### 10.1. PKCS12/JKS/JCEKS/PKCS11

| Loại | Vai trò thường gặp | Lưu ý |
| --- | --- | --- |
| `PKCS12` | Identity/truststore dạng file, có thể chứa private key/certificates | Khả năng thuật toán PBE/entry-password khác nhau theo provider và công cụ |
| `JKS` | Keystore Java legacy | Không tự chọn JKS mới chỉ để có keypass riêng; cần xem mức bảo vệ/khả năng tương thích |
| `JCEKS` | Legacy store có hỗ trợ secret keys | Không phải câu trả lời mặc định cho TLS identity; cần policy bảo mật |
| `PKCS11` | KeyStore-like view của token/HSM | Private key có thể non-exportable; PIN/provider handles thay cho password file |
| `KeyStore.getDefaultType()` | Type mặc định của runtime | Có thể khác do security properties; xác định type rõ khi cần interoperability |

Nhiều hệ thống dùng certificate dưới PEM nhưng private key trong HSM: vẫn có thể tạo `KeyManager` qua provider phù hợp. **Không bắt buộc export private key ra PKCS12** để dùng JSSE nếu provider hỗ trợ private-key operations trong thiết bị.

### 10.2. Độ dài/thuật toán khóa không thay chuỗi tin cậy

Nếu leaf RSA-2048 có chain hợp lệ nhưng CA không được tin, TLS không tự chấp nhận chỉ vì RSA key dài 2048 bit. Nếu leaf dùng RSA-3072 nhưng SAN là `other.example` trong khi client kết nối `localhost`, endpoint identification vẫn phải từ chối. Tính đúng của TLS identity gồm **public/private key match, certificate chain, trust anchor, validity, key usage/EKU, thuật toán và tên endpoint**.

### 10.3. Không nạp nhầm file

`ca.p12` chứa private key CA dùng phát hành, **không phải** identity store của server. `trust.p12` chứa CA certificate, **không phải** keystore chứa server private key. Nếu chỉ nhìn tên file và nạp nhầm `ca.p12` vào server identity, ứng dụng có thể trình bày certificate không phù hợp hoặc vô tình cho code server quyền truy cập private key CA — một sự cố nghiêm trọng.

**Chỉ private key của service** được cấp cho service đó; root CA private key phải được giữ theo quy trình CA/HSM với quyền cực hạn chế.

## 11. Khởi tạo SSLContext trong thư viện HTTP/framework

### 11.1. Java `HttpClient` (Java 11+)

Với `java.net.http.HttpClient`, có thể chuyển SSLContext tùy chỉnh vào builder:

```java
// Fragment minh họa, không phải chương trình độc lập:
HttpClient client = HttpClient.newBuilder()
        .sslContext(clientContext)
        .build();
```

`clientContext` ở đây phải dùng trust managers mong muốn. `HttpClient` quản lý connection/hostname validation theo JSSE và hợp đồng API riêng; không bật “insecure hostname verifier” để bỏ qua lỗi chứng thư. Không lấy `serverContext` có private key CA test và dùng cho client production.

### 11.2. Spring Boot, Tomcat, Jetty và các framework khác

Framework thường có các trường cấu hình tương đương:

- Keystore path/type, **store password**, alias và **key password**;
- Truststore path/type/password;
- Client authentication mode;
- Protocol/cipher-suites policy;
- Certificate reload/rotation settings.

Tên thuộc tính và hành vi mặc định khác nhau theo **framework version**. Đừng copy một tập YAML bất kỳ rồi cho rằng mọi framework đều hiểu. Cách xác minh là chạy handshake thật, kiểm tra certificate chain, SAN, TLS version, client-auth policy và hành vi sau khi rotate.

### 11.3. Default SSLContext và system properties

Java có các system properties như `javax.net.ssl.keyStore`, `javax.net.ssl.keyStorePassword`, `javax.net.ssl.trustStore`, `javax.net.ssl.trustStorePassword` ảnh hưởng đến **một số default JSSE configurations**. Tuy nhiên:

- Một `SSLContext` tự khởi tạo qua `kmf/tmf` **không đương nhiên** sử dụng các system properties nếu bạn đã truyền explicit key/trust managers.
- `SSLContext.getDefault()` là context mặc định của tiến trình; sửa system properties trong runtime sau khi context/connection pool đã khởi tạo **không đảm bảo** mọi socket tự cập nhật.
- Dùng nhiều trust policies cho các tenant/service thì thường nên **tạo các context độc lập và quản lý lifecycle rõ**, tránh thay system properties global liên tục.

## 12. Chẩn đoán lỗi SSLContext, key manager và handshake

| Vị trí/lỗi | Nguyên nhân phổ biến | Cách kiểm tra |
| --- | --- | --- |
| `KeyStore.load` báo `IOException` | Sai storepass, file hỏng, format/PBE không tương thích | Type/provider, đường dẫn, storepass, backup |
| `KeyManagerFactory.init` ném `UnrecoverableKeyException` | Sai key password hoặc không đọc được entry | Alias, keypass, provider, key entry |
| `identity.isKeyEntry(alias) == false` | Alias sai hoặc chỉ là certificate entry | `keytool -list -v`, kiểm tra entry type |
| `getCertificateChain(alias) == null` | Không có chain phù hợp | Tái nhập leaf/intermediate đúng thứ tự, kiểm tra key match |
| `SSLContext.init` không tạo được manager | Không tương thích provider/algorithm, tham số không hợp lệ | JDK security providers, private key handle, key/trust manager type |
| `SSLHandshakeException: PKIX path building failed` | Không có đường tin cậy đến CA trong truststore, thiếu intermediate, ràng buộc PKIX khác | Truststore, CA approved, chain, EKU/validity, algorithm constraints |
| `SSLHandshakeException` với hostname/SAN mismatch | Certificate server không hợp tên/địa chỉ client kết nối | SAN DNS/IP, endpoint ID, cách tạo socket, SNI |
| Handshake không có certificate phù hợp | Server không có key entry hoặc không chọn được certificate/key type | `KeyManager`, alias, thuật toán TLS, certificate chain |
| mTLS báo thiếu client certificate | Client không gửi identity hoặc server cần clientAuth mà truststore không khớp | Client key manager, server trust CA và `needClientAuth` |
| `protocol_version` / `handshake_failure` | Không có TLS protocol/cipher suite trùng nhau, chính sách không phù hợp | EnabledProtocols, JDK version, crypto policy |
| `certificate_expired` | Certificate ngoài `notBefore/notAfter` | Kiểm tra đồng hồ UTC, phát hành lại bằng CA đúng |
| Truststore có CA nhưng vẫn từ chối | Sai SAN/EKU/algorithm/policy, chain sai, revocation | Đọc nguyên nhân PKIX và kiểm thử từng ràng buộc |
| Test demo báo `Connection refused` | Socket server chưa mở, bị chặn loopback/firewall hoặc port sai | Loopback support, port, timeout; không tắt TLS để sửa lỗi TCP |

### 12.1. Biết lỗi xuất hiện ở bước nào

Cách đọc log theo quá trình:

```text
1) KeyStore.load(identity)      -> Đọc file/mật khẩu kho
2) KeyManagerFactory.init(...)  -> Mở private key entries
3) TrustManagerFactory.init(...) -> Chuẩn bị trust anchors
4) SSLContext.init(...)         -> Ghép managers
5) SSLSocket.startHandshake()   -> Chọn giao thức, certificate chain,
                                   peer validation, key proof
6) Application request          -> Authorization và logic nghiệp vụ
```

Nếu dừng tại bước 1 hoặc 2, lỗi không nhất thiết liên quan certificate của remote peer. Nếu dừng tại bước 5, cần xét **trust path, hostname, protocol, key usage, algorithm constraints, client-auth và revocation policy**. Không chỉ thử thay một chuỗi password rồi coi đã sửa mọi vấn đề.

### 12.2. Debug TLS nhưng không tiết lộ secrets

JDK cung cấp debug log của JSSE, ví dụ property `javax.net.debug=ssl,handshake` trong môi trường test. Debug log có thể chứa **certificate details, hostnames, SNI và metadata kết nối** nên cần phân quyền và retention, không đẩy trực tiếp lên hệ thống log public. **Tuyệt đối không log private key bytes, mật khẩu kho/keypass hoặc secret TLS**.

Không bật log bí mật khóa phiên kiểu `SSLKEYLOGFILE` hoặc options tương đương trên production mà không có kiểm soát nghiêm ngặt; các tệp khóa phiên có thể giúp giải mã traffic TLS và phải được bảo vệ như secret nếu được dùng cho mục đích phân tích được ủy quyền.

### 12.3. Những kiểm thử âm nên có

- Thay truststore bằng một truststore không chứa demo CA: **handshake phải thất bại** vì không có trust anchor thích hợp.
- Dùng một hostname khác `localhost` khi endpoint identification vẫn bật: **phải bị từ chối** nếu SAN không bao gồm tên đó.
- Nhập một server certificate hết hạn hoặc không có EKU phù hợp: **bị từ chối** theo PKIX/JSSE policy.
- Dùng private-key password sai: khởi tạo `KeyManagerFactory` phải thất bại nếu entry protection yêu cầu password riêng.
- Bật `needClientAuth(true)` nhưng client không có identity: mTLS phải thất bại theo policy.
- Dùng TLS version/cipher suite không có điểm chung: handshake thất bại; không được fallback sang protocol cũ không an toàn chỉ để test chạy.

Các ca âm là **kỳ vọng bảo mật cần kiểm tra**, không phải stdout cố định của chương trình một chiều. Kiểu exception có thể khác theo provider/JDK.

## 13. Quản lý vòng đời SSLContext, chứng thư và CA

### 13.1. Khi private key hoặc certificate được rotate

Có thể có nhiều thay đổi độc lập:

1. **Đổi store password:** thay credential bảo vệ kho, không nhất thiết đổi private key/certificate.
2. **Đổi key password:** thay thông tin bảo vệ entry, không đổi vật liệu khóa nếu không tạo key mới.
3. **Gia hạn/đổi certificate:** cùng private key có thể nhận certificate mới theo chính sách, nhưng vẫn cần chain/SAN/EKU cập nhật.
4. **Rotate private key:** tạo keypair mới và certificate tương ứng; không dùng chain cũ không khớp.
5. **Rotate CA/trust anchors:** client/server truststores phải có kế hoạch chuyển tiếp đúng nguồn, không tự “trust mọi CA” để tránh downtime.

**`SSLContext` đã init không tự biết file PKCS12 trên disk đã thay đổi.** Một số framework có reload hỗ trợ, nhưng JSSE context tạo bằng code thông thường không phải live watcher mặc định. Cần build context mới với kho/cert mới, kiểm tra rồi chuyển traffic/connection pool theo cơ chế an toàn.

### 13.2. Hai giai đoạn thay CA

Ví dụ CA server đổi từ A sang B:

- Chuẩn bị: client truststore được cập nhật để **tin B** theo quy trình đã phê duyệt; có thể tạm tin A và B trong cửa sổ chuyển tiếp.
- Thay server identity bằng leaf+chain do B phát hành.
- Kiểm thử handshake và hostname với ứng dụng thật.
- Sau cửa sổ chuyển tiếp phù hợp: loại A khi không còn cần thiết, thu hẹp trust policy.

Không đơn giản thay CA bên server trước khi client biết CA B, vì handshake sẽ thất bại, và cũng không giữ trust anchors cũ vô hạn khi không còn được phép.

### 13.3. Private key bị lộ

Nếu raw private key server bị lộ, **thay mật khẩu `.p12` không làm khóa đó an toàn trở lại**. Phải rotate keypair/certificate, thu hồi chứng thư bị ảnh hưởng theo PKI, đánh giá dữ liệu/phiên kết nối và cập nhật server/client theo incident response. Private key CA bị lộ có phạm vi ảnh hưởng lớn hơn, có thể cần thay trust anchor và toàn bộ chuỗi cấp phát liên quan.

### 13.4. Revocation checking không tự bảo đảm theo mọi cấu hình

TLS dùng chain validation qua `TrustManager`/PKIX, nhưng việc **CRL/OCSP revocation checking** có được bật và hoạt động thế nào phụ thuộc **JDK/provider/configuration**. Đừng mặc định rằng vì đã có truststore và `SSLContext.init` nên mọi chứng thư thu hồi đều bị phát hiện ngay.

Nếu ứng dụng có yêu cầu revocation nghiêm, cấu hình PKIX revocation checker/OCSP/CRL và network/cache policy rõ ràng. Cân nhắc fail-closed/soft-fail theo mô hình rủi ro và kiểm thử với certificates thật sự bị thu hồi.

## 14. Những lỗi tư duy phổ biến

| Sai lầm | Vì sao sai | Cách đúng |
| --- | --- | --- |
| “SSLContext chỉ cần private key.” | Thiếu chain identity và trust policy | Key manager từ private key + chain; trust manager từ CA được phê duyệt |
| “Đưa CA vào server identity keystore là client đã tin CA.” | Client có truststore độc lập | Cấp CA đúng cho client truststore |
| “Đưa leaf của server vào truststore mỗi lần kết nối.” | Tự động nâng dữ liệu ngoài thành tin cậy, dễ bị MITM | Tin CA/pin qua kênh được xác thực và policy |
| “`KeyStore.load` thành công là TLS đã sẵn sàng.” | Chưa lấy key, chưa handshake | Kiểm tra alias/chain/keypass và chạy TLS |
| “`storepass` luôn bằng `keypass`.” | Vai trò khác, tool/provider có giới hạn | Cấu hình đúng từng bước, ghi rõ khi dùng chung |
| “Chỉ cần `TrustManagerFactory` là server có danh tính.” | Trust manager xác minh peer, không chứng minh local identity | `KeyManagerFactory` và private key/certificate chain |
| “`KeyManagerFactory` xác minh CA của server remote.” | Key manager quản local identity | Client trust manager xác minh remote chain |
| “`SSLContext.getInstance("TLS")` luôn ép TLS 1.3.” | Chỉ chọn TLS context/provider | Chọn protocol policy trên socket/engine |
| “Raw `SSLSocket` luôn tự kiểm tra hostname.” | Endpoint identification có thể phải bật tường minh | `setEndpointIdentificationAlgorithm("HTTPS")` |
| “Certificate CN đúng là đủ, không cần SAN.” | Quy tắc hiện đại dùng SAN | Cấp certificate có SAN DNS/IP đúng |
| “Trust-all manager để sửa `PKIX path building failed`.” | Vô hiệu hóa xác thực, dễ MITM | Sửa truststore/chain/hostname đúng nguồn |
| “mTLS chỉ cần server có truststore.” | Server có thể không yêu cầu client certificate | Bật `needClientAuth` và cấp client identity |
| “Mọi CA ký client cert đều cho phép gọi mọi API.” | Authentication khác authorization | Ánh xạ SAN/identity và quyền cụ thể |
| “Sau rotate file `.p12`, SSLContext cũ tự đổi.” | Context/connection pool không tự reload | Rebuild và swap context an toàn |
| “Nếu TLS hoạt động thì CA root private key phải ở server.” | Server chỉ cần private key leaf | CA private key nằm ở CA/HSM, truststore chỉ cần public cert |
| “Tin tất cả CAs sẽ tránh outage.” | Mở rộng trust ngoài yêu cầu | Quản trị anchors theo scope và chính sách |

## 15. Checklist trước khi triển khai SSLContext

1. **Danh tính:** private key service có được cấp và bảo vệ đúng không? Leaf certificate có public key khớp, SAN DNS/IP đúng?
2. **Chain:** `getCertificateChain(alias)` có leaf rồi intermediate đúng thứ tự, không thiếu issuer cần thiết? Chứng thư còn hiệu lực và đủ KeyUsage/EKU?
3. **Store/key password:** `KeyStore.load` dùng storepass, `KeyManagerFactory.init` dùng keypass phù hợp provider? Không hardcode/log?
4. **CA trust:** tập CA đã được xác minh nguồn, scope hợp lý, không tự import certificates từ peer?
5. **Factory:** dùng `KeyManagerFactory` cho local identity, `TrustManagerFactory` cho peer trust, ghép đúng vào `SSLContext.init`?
6. **TLS protocol:** chỉ bật TLS version/cipher suites được policy phê duyệt, không cho phép fallback không an toàn?
7. **Endpoint ID:** với raw JSSE client, đã cấu hình hostname verification đúng tên đích và kiểm tra SAN chưa?
8. **mTLS:** nếu cần, client có key manager, server có trust manager đúng client CA và bật `needClientAuth(true)` chưa?
9. **Authorization:** có kiểm tra service/user identity và quyền nghiệp vụ sau TLS handshake, không coi một CA hợp lệ là toàn quyền?
10. **Thử nghiệm:** handshake thực giữa client/server đã thành công; các ca thiếu CA, sai SAN, sai keypass, thiếu client cert có thất bại đúng cách?
11. **Vận hành:** rotation, reload context, trust anchor overlap, backup/restore, revocation policy và audit được kiểm tra?
12. **Bảo vệ môi trường:** không lưu private key/`.p12` có bí mật vào source control, không đưa CA private key vào runtime và không log TLS secrets?

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Muốn dựng HTTPS server từ `server.p12`, gọi `KeyStore.load` bằng storepass là đã đủ chưa?  
**Trả lời:** **Chưa.** Cần key manager recover private key bằng keypass, certificate chain hợp lệ, `SSLContext.init`, cấu hình socket và handshake thật.

**Câu 2.** Server đã có chain [leaf, intermediate, root]. Client không tin root, có thể tin server chỉ vì root được gửi cùng chain không?  
**Trả lời:** **Không.** Root trust anchor phải được client cấp phát đáng tin **từ trước**, không tự nâng root do server gửi thành CA được tin.

**Câu 3.** `KeyManagerFactory` và `TrustManagerFactory` khác nhau thế nào?  
**Trả lời:** `KeyManagerFactory` chuẩn bị **private key + certificate chain local identity**; `TrustManagerFactory` chuẩn bị bộ **quy tắc/tập CA** để xác minh chứng thư của **peer**.

**Câu 4.** TLS client không dùng client certificate thì có thể gọi `SSLContext.init(null, tm, random)` không?  
**Trả lời:** **Có.** `null` key managers nghĩa không cung cấp local client identity ở cấu hình này; trust managers vẫn kiểm tra server certificate.

**Câu 5.** TLS server đã cài trust manager của client CA. Đã tự thành mTLS chưa?  
**Trả lời:** **Chưa.** Server còn phải yêu cầu client authentication (`needClientAuth`), client có key manager đúng và phải đáp ứng certificate policy.

**Câu 6.** Một server certificate có CA chain hợp lệ nhưng SAN chỉ là `wrong.example`, client đang đến `localhost`. Nên xử lý sao?  
**Trả lời:** **Từ chối** khi hostname verification bật đúng; chữ ký CA hợp lệ không thay thế kiểm tra danh tính endpoint.

**Câu 7.** Chuyển từ server certificate do CA A ký sang CA B ký mà không cập nhật client truststore, chuyện gì có thể xảy ra?  
**Trả lời:** Handshake có thể thất bại vì không có trust anchor B. Cần kế hoạch overlap và rotation tin cậy giữa hai phía.

**Câu 8.** Nếu biết private key của demo CA, có nên nhập nó vào `trust.p12` của client không?  
**Trả lời:** **Không.** Truststore chỉ cần **public CA certificate**. CA private key phải được bảo vệ bởi hệ thống CA.

**Câu 9.** `SSLContext.getInstance("TLS")` có đảm bảo kết nối luôn dùng TLS 1.3 và xác minh hostname chưa?  
**Trả lời:** **Không.** Cần cấu hình protocol/SSLParameters hoặc dùng framework có default được kiểm tra. Demo chủ động bật TLS 1.3 và endpoint identification.

**Câu 10.** Sau khi thay file `server.p12` trên disk, một `SSLContext` đã init tự dùng certificate mới trong mọi kết nối không?  
**Trả lời:** **Không thể giả định.** Cần lifecycle/reload context và connection pools theo framework/provider.

**Câu 11.** Nếu certificate server hết hạn, có thể sửa bằng cách cho `TrustManager` bỏ qua ngày hết hạn không?  
**Trả lời:** **Không.** Phải phát hành/rotate certificate hợp lệ, kiểm tra đồng hồ và chain; không bỏ kiểm tra bảo mật.

**Câu 12.** Trong demo, `server.p12` có chain length 2 và `trust.p12` có một CA. Vì sao kết quả không có nghĩa đây là bộ certificate production an toàn?  
**Trả lời:** CA được tự tạo trong sandbox, khóa/mật khẩu là fixture demo, scope chỉ localhost và thời hạn ngắn. Production cần PKI/trust distribution, secret management, authorization, revocation, vận hành và kiểm thử phù hợp.

### Tài liệu tham khảo

- [Java SE 21 — SSLContext](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLContext.html): `getInstance`, `init` và socket factories.
- [Java SE 21 — KeyManagerFactory](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/KeyManagerFactory.html): nạp local identities/khóa riêng.
- [Java SE 21 — TrustManagerFactory](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html): tạo trust managers từ KeyStore.
- [Java SE 21 — SSLParameters](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLParameters.html): endpoint identification và client-auth.
- [Java SE 21 — Java Secure Socket Extension (JSSE) Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html): TLS handshake, managers, truststores, SSLContext.
- [Java SE 21 — keytool](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html): tạo keypair, CSR, ký certificate, nhập chain, xuất/import CA.
- [RFC 5280 — X.509 Public Key Infrastructure](https://www.rfc-editor.org/rfc/rfc5280): certificate path validation, key usage và X.509 policy.

### Ghi nhớ

**`SSLContext` được cấu hình bằng hai hướng độc lập: `KeyManagerFactory` cho private key + certificate chain của chính ứng dụng, `TrustManagerFactory` cho các CA mà ứng dụng đã tin để xác minh peer.** Một chuỗi certificate gửi từ server không tự tạo trust anchor; một truststore không cung cấp server private key; tạo `SSLContext` không thay thế handshake, hostname verification hay authorization. Hãy kiểm thử luồng **nạp kho → phục hồi khóa → TLS handshake → endpoint identity → nghiệp vụ** bằng certificate và CA đúng chính sách, đồng thời bảo vệ key, password và cơ chế rotation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jsse-003">16.3 SSLParameters cho protocol, cipher suite, SNI, ALPN và endpoint identification</a>

<details>
<summary>Xem chi tiết</summary>

# 16.3 SSLParameters cho protocol, cipher suite, SNI, ALPN và endpoint identification

Một Java client có thể dùng `SSLContext` và `SSLEngine` hoặc `SSLSocket` để tạo kết nối TLS. Tuy nhiên, câu lệnh `SSLContext.getInstance("TLS")` **không tự giải thích** phiên bản TLS nào sẽ được bật, bộ mã (*cipher suite*) nào được chọn, client gửi tên server qua SNI hay không, giao thức ứng dụng được thương lượng qua ALPN thế nào, và đặc biệt client có kiểm tra **chứng thư thực sự thuộc đúng hostname đích** hay không.

Trong JSSE (*Java Secure Socket Extension*), `javax.net.ssl.SSLParameters` là một đối tượng cấu hình cho những điều khoản handshake và xác thực liên quan. Nó bao gồm danh sách protocol, cipher suites, SNI, ALPN, endpoint identification và một số lựa chọn khác. Nhưng cần phân biệt **cấu hình đã được thiết lập** với **kết quả handshake đã thương lượng và xác thực thành công**: `setProtocols` không bảo đảm peer hỗ trợ protocol, `setApplicationProtocols` không có nghĩa đã dùng HTTP/2, `setServerNames` không thay thế kiểm hostname, và `setEndpointIdentificationAlgorithm("HTTPS")` không tự tạo một trust anchor đáng tin.

Bài học dùng **Java 17+** và tập trung đúng năm nhóm:

1. `protocols`: giới hạn phiên bản TLS được phép.
2. `cipherSuites`: giới hạn bộ thuật toán TLS tương thích với phiên bản và chính sách.
3. `serverNames` / **SNI**: thông báo server name để máy chủ chọn cấu hình/certificate thích hợp.
4. `applicationProtocols` / **ALPN**: thương lượng giao thức tầng ứng dụng, ví dụ `h2` hoặc `http/1.1`.
5. `endpointIdentificationAlgorithm`: yêu cầu JSSE kiểm danh tính server, nhất là hostname của kết nối HTTPS.

Mục tiêu là biết **đặt đúng thuộc tính ở đúng phía client/server, trước handshake**, giữ kiểm chứng TLS đầy đủ, xác định giới hạn của APIs và có bài test dương/âm. Ví dụ Java chạy được ở mục 9 không cần mạng để tránh phụ thuộc server công khai và không giả vờ rằng cấu hình thành công đồng nghĩa đã xác thực một TLS peer.

## 1. Mục tiêu học tập

Sau khi học, bạn có thể:

- Giải thích quan hệ giữa `SSLContext`, `SSLEngine`, `SSLSocket`, `SSLParameters`, `TrustManager` và `KeyManager`.
- Lấy cấu hình từ `getSSLParameters()`, thay đổi đúng các thuộc tính cần thiết, rồi áp dụng bằng `setSSLParameters(...)` **trước khi bắt đầu handshake**.
- Phân biệt `getSupportedProtocols()` với `getEnabledProtocols()` và tránh bật phiên bản TLS cũ chỉ để kết nối.
- Hiểu tên cipher suite trong TLS 1.2 so với TLS 1.3, mối liên hệ với protocol, certificate, signature và key exchange.
- Dùng `SNIHostName` và `SSLParameters.setServerNames(...)` ở **client**, biết server-side `SNIMatcher` không thay chứng thư/hostname validation.
- Dùng `setApplicationProtocols(...)` để đề xuất ALPN và đọc giao thức **đã thương lượng** sau handshake bằng `SSLEngine.getApplicationProtocol()` hoặc `SSLSocket.getApplicationProtocol()`.
- Bật endpoint identification `HTTPS` với socket/engine thích hợp, hiểu `SNI != hostname verification != trust chain validation`.
- Nắm những khác biệt quan trọng giữa `SSLEngine`, `SSLSocket`, `HttpsURLConnection` và `java.net.http.HttpClient`.
- Kiểm tra các trường hợp cấu hình sai, downgrade, ALPN mismatch, SNI sai, certificate SAN sai, CA không tin, mTLS và tác động của TLS connection pooling.

## 2. SSLParameters nằm ở đâu trong JSSE?

Một lớp nhìn tổng quát:

```text
Java application
    |
    +-- java.net.http.HttpClient / HTTPS library
    |       |
    |       +-- JSSE/TLS implementation (tùy library/provider)
    |
    +-- SSLSocket hoặc SSLEngine trực tiếp
            |
            +-- SSLContext
            |      +-- KeyManager (local identity, nếu cần)
            |      +-- TrustManager (peer certificate verification)
            |      +-- SecureRandom / provider
            |
            +-- SSLParameters (cấu hình handshake / endpoint)
                   +-- protocols
                   +-- cipher suites
                   +-- SNI server names
                   +-- ALPN application protocols
                   +-- endpoint identification algorithm
                   +-- client auth / algorithm constraints / các tùy chọn khác
```

`SSLContext` được khởi tạo bằng key managers/trust managers/provider theo chính sách. Một `SSLEngine` hoặc `SSLSocket` được tạo từ context và mang cấu hình sử dụng khi giao tiếp. `SSLParameters` thường là **một đối tượng cấu hình có thể sửa**, không phải “kết quả kiểm tra bảo mật”.

Mẫu thay đổi thường thấy:

```java
// Mã minh họa trong một method; giả sử SSLEngine engine đã tạo.
SSLParameters p = engine.getSSLParameters();
p.setEndpointIdentificationAlgorithm("HTTPS");
engine.setSSLParameters(p); // Áp lại vào engine.
```

Đừng chỉnh `p` rồi quên `setSSLParameters(p)`. Việc có object `p` với dữ liệu mới không có nghĩa engine/socket đã nhận thay đổi.

### Năm câu hỏi phải tách rời

| Câu hỏi | Thuộc tính/API liên quan | Chứng minh được điều gì? |
| --- | --- | --- |
| Tôi cho phép TLS version nào? | `setProtocols` | Những version được cấu hình bật trên endpoint |
| Tôi cho phép thuật toán TLS nào? | `setCipherSuites` | Những suite được cấu hình; chưa chắc peer chọn được |
| Server nên nhận tên đích nào để chọn certificate? | `setServerNames` / SNI | Client gửi tên như một TLS extension khi handshake hỗ trợ |
| Client/server sẽ nói HTTP/2 hay HTTP/1.1? | `setApplicationProtocols` / ALPN | Danh sách đề xuất; giao thức thật chỉ biết sau thương lượng |
| Certificate có đại diện đúng endpoint không? | `setEndpointIdentificationAlgorithm("HTTPS")` | Bật chính sách endpoint identity cho engine/socket phù hợp; còn cần trusted path và handshake thực |

**Không thay thế lẫn nhau:** SNI là thông tin client gửi để server chọn virtual host; ALPN là chọn giao thức ứng dụng; hostname verification là xác thực tên server từ chứng thư. Cả ba có thể dùng cùng một hostname trong ứng dụng HTTPS, nhưng là **ba cơ chế độc lập**.

## 3. Phân biệt supported, enabled, configured và negotiated

Đây là chìa khóa để tránh hiểu sai output của JSSE.

- **Supported:** provider/runtime có triển khai một TLS protocol hoặc cipher suite, thể hiện qua `getSupportedProtocols()` và `getSupportedCipherSuites()`.
- **Enabled:** tập tính năng runtime đang bật cho endpoint đó theo chính sách mặc định và cấu hình đã áp, thể hiện qua `getEnabledProtocols()` và `getEnabledCipherSuites()`.
- **Configured/allowed:** tập mà ứng dụng chủ động đặt qua `SSLParameters`; cần là subset phù hợp với provider và policy.
- **Negotiated:** kết quả peer thực sự thống nhất sau handshake; có thể xem qua `SSLSession.getProtocol()`, `SSLSession.getCipherSuite()` và `getApplicationProtocol()`.

```text
Provider SUPPORTED
       |
       v
Runtime security policy + endpoint defaults
       |
       v
ENABLED / app override via SSLParameters
       |
       v
ClientHello + ServerHello + certificate checks + handshake
       |
       v
NEGOTIATED protocol, cipher suite, ALPN, authenticated peer
```

Một protocol có mặt trong **supported** nhưng không nằm trong **enabled** có thể bị tắt vì đã cũ, nguy hiểm hoặc không phù hợp policy. **Không bật lại chỉ vì nó có mặt trong danh sách supported**. Java 17 thường hỗ trợ TLS 1.2 và 1.3 trên các JSSE provider phổ biến, nhưng danh sách thực tế phụ thuộc vendor/build/provider, security properties, các cài đặt `jdk.tls.disabledAlgorithms` và runtime.

### Cấu hình ưu tiên an toàn

Trong production, nên bắt đầu từ **JSSE defaults đã được quản trị** hoặc một allowlist được review. Nếu có yêu cầu bắt buộc tối thiểu TLS 1.2, hãy kiểm giao của các version cho phép với **enabled protocols** tại runtime cụ thể. Nếu giao rỗng, **fail closed** thay vì ép bật TLS 1.0/1.1.

Không lấy mảng từ `getSupportedProtocols()` rồi đưa nguyên vào `setProtocols(...)`; hành vi đó có thể bật những protocol mà mặc định JDK cố ý loại bỏ.

## 4. Protocol: TLSv1.2, TLSv1.3 và phiên bản handshake

Cách đặt:

```java
// Mã minh họa: chỉ dùng khi các protocol đã được kiểm là enabled và được policy cho phép.
SSLParameters p = engine.getSSLParameters();
p.setProtocols(new String[] { "TLSv1.3", "TLSv1.2" });
engine.setSSLParameters(p);
```

Ở mức ý tưởng, đây là danh sách version **được phép thử thương lượng**. Nó không buộc server phải chọn TLS 1.3; server có thể chỉ hỗ trợ TLS 1.2, hoặc giao các phiên bản có thể rỗng và handshake thất bại.

**TLS 1.3** có thay đổi đáng kể so với TLS 1.2 về quá trình handshake, bộ mã được định nghĩa và cơ chế khóa tạm thời. Đừng suy rằng một cipher suite có tên `TLS_AES_128_GCM_SHA256` dùng được với TLS 1.2 hoặc rằng một tên `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256` là TLS 1.3 suite.

### Các thiết lập thường gây rủi ro

- Bật `TLSv1` / `TLSv1.1` / `SSLv3` để “hỗ trợ server cũ” mà không có review chính sách.
- Coi `SSLContext.getInstance("TLS")` là cố định chỉ TLS 1.2/1.3 bất kể provider.
- Đọc `setProtocols` thành “handshake đã dùng TLS 1.3”.
- Xóa TLS 1.2 trên một hệ thống có client/peer chưa hỗ trợ TLS 1.3 mà không có kiểm thử tương thích.
- Cho user request quyết định danh sách protocol/cipher rồi chuyển trực tiếp vào `SSLParameters`.
- Thay global security properties hoặc provider chỉ để làm một kết nối legacy chạy được, vô tình ảnh hưởng toàn bộ JVM.
- Đổi allowlist khi `SSLSocket` đã handshake; kết nối đã thiết lập không tự thương lượng lại theo cấu hình mới.

### Khi nào nên chỉ cho TLSv1.3?

Khi threat model và tất cả peer có thể hỗ trợ, **TLS 1.3 only** có thể là một chính sách hợp lý. Với hệ thống cần tương thích, cho phép TLS 1.2 và 1.3 theo profile mạnh cũng có thể hợp lệ. Cần đo telemetry và có lộ trình loại bỏ TLS cũ; việc chọn protocol là **quyết định security/compliance và compatibility**, không phải đoán dựa trên một bài demo.

## 5. Cipher suites: TLS 1.2 khác TLS 1.3

**Cipher suite** mô tả tổ hợp thuật toán TLS được dùng khi handshake và bảo vệ record. Tuy nhiên, mức thông tin trong tên suite khác nhau theo TLS version.

| Phiên bản | Ví dụ tên suite | Điều cần hiểu |
| --- | --- | --- |
| TLS 1.3 | `TLS_AES_128_GCM_SHA256` | AEAD cipher + hash dùng trong TLS 1.3; thuật toán key exchange và xác thực được thương lượng qua cơ chế khác |
| TLS 1.3 | `TLS_AES_256_GCM_SHA384` | AEAD AES-GCM và hash tương ứng |
| TLS 1.3 | `TLS_CHACHA20_POLY1305_SHA256` | AEAD ChaCha20-Poly1305 nếu provider hỗ trợ |
| TLS 1.2 | `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256` | ECDHE key exchange, RSA-based authentication trong profile TLS 1.2, AES-GCM |
| TLS 1.2 | `TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256` | ECDHE với ECDSA certificate/authentication trong profile TLS 1.2 |
| TLS 1.2 | `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384` | Profile TLS 1.2 có ECDHE và AES-GCM |

**Đừng diễn giải tên TLS 1.3 suite bằng logic tên TLS 1.2.** Trong TLS 1.3, cipher suite không còn mã hóa tên thuật toán chứng thư RSA/ECDSA hoặc key exchange như TLS 1.2.

### Chọn cipher suite đúng cách

- Bắt đầu với **enabled suites**, không bật toàn bộ supported suites.
- Chỉ giữ suite theo allowlist bảo mật được cập nhật và **tương thích với protocol**.
- Không cố ép một TLS 1.3 suite khi chỉ cho TLS 1.2; có thể dẫn đến “no shared cipher”/handshake failure.
- Với TLS 1.2, key type của certificate và khả năng của server cũng quan trọng. Một suite ECDSA có thể không phù hợp server chỉ triển khai RSA identity, tùy cấu hình và provider.
- Không coi `setCipherSuites(...)` là cơ chế pin certificate hoặc ép peer identity.
- Với đa số ứng dụng HTTPS thông thường, mặc định JSSE/provider được cập nhật và không tự giới hạn cứng một suite thường giúp duy trì compatibility tốt hơn. Chỉ pin danh sách suite khi có yêu cầu cụ thể và đã test cả hai phía.

### Cấu hình cipher suite order

`SSLParameters.setUseCipherSuitesOrder(true)` có ý nghĩa ưu tiên thứ tự suite của **phía server** trong những trường hợp/protocol/provider áp dụng. Nó không bảo đảm server luôn chọn suite đầu nếu không tương thích hoặc không được hỗ trợ. TLS 1.3 và TLS 1.2 có quy tắc thương lượng khác nhau; hãy test negotiated cipher thực tế.

### Cảnh báo mạnh

Không dùng hoặc bật lại cipher suites đã bị chính sách JDK tắt chỉ vì một peer cũ yêu cầu. Các quyết định như bỏ static RSA key exchange trong TLS 1.2, CBC/3DES/RC4 hay thuật toán chữ ký yếu cần theo security policy, không thực hiện bằng cách copy một list cũ từ internet.

## 6. SNI: client thông báo tên server đang muốn kết nối

**SNI** (*Server Name Indication*) là TLS extension cho phép client gửi một server name tới endpoint, thường giúp server/CDN/reverse proxy đang dùng chung IP:port chọn certificate và virtual host tương ứng. Một server có thể phục vụ nhiều hostname trên cùng địa chỉ IP.

Ví dụ:

```text
Shared IP 203.0.113.10:443
   |
   +-- api.example.test    -> certificate A / virtual host A
   |
   +-- admin.example.test  -> certificate B / virtual host B

Client connects to api.example.test
   ClientHello SNI = api.example.test
   Server chooses certificate A
```

**SNI không phải bằng chứng danh tính.** Client có thể nói nó muốn server name `api.example.test`, nhưng TLS client vẫn phải xác minh certificate của server đúng tên ấy và có chain đến trust anchor được phép. Nếu server trả chứng thư cho `admin.example.test`, việc client đã gửi đúng SNI **không** cứu được lỗi hostname mismatch.

### Java client dùng SNIHostName

```java
// Mã minh họa trong method.
String hostname = "api.example.test";
SSLParameters p = engine.getSSLParameters();
p.setServerNames(List.of(new SNIHostName(hostname)));
engine.setSSLParameters(p);
```

Đoạn này cần import `java.util.List` và `javax.net.ssl.SNIHostName`, và giả sử `engine` đã được tạo cho đúng peer. `SNIHostName` biểu diễn host_name SNI; không đưa URL nguyên `https://api.example.test/path`, cổng `:443`, wildcard `*.example.test`, IP literal hoặc chuỗi không hợp lệ vào chỗ tên DNS.

**Trong nhiều HTTPS client JSSE hiện đại, SNI có thể được điền tự động theo hostname đích**. Việc set thủ công chỉ cần khi bạn có lý do và chắc chắn không tạo mâu thuẫn với host mà endpoint identification dùng. Khi kết nối tới một IP nhưng cần xác minh DNS name, phải hiểu rõ API của TLS engine/socket, peerHost, SNI, HTTP Host header và security policy; đừng chỉnh một chuỗi duy nhất để bypass xác thực.

### SNI trên server

Phía server có thể dùng `SSLParameters.setSNIMatchers(...)` hoặc logic TLS stack để quyết định chấp nhận/sắp xếp tên và lựa chọn local identity. Nhưng:

- `SNIMatcher` không cấp certificate cho hostname tự động.
- `setServerNames` là cách client cung cấp danh sách tên, **không phải** cấu hình “tên máy chủ mà server chắc chắn sở hữu”.
- Server thường phải có `KeyManager`/alias selection/certificate chain tương ứng với SNI.
- Nếu server yêu cầu một SNI cụ thể, thiếu/sai SNI có thể bị từ chối theo policy hoặc chọn một virtual host mặc định; hành vi tùy server.
- SNI truyền thống không phải lúc nào được giữ bí mật khỏi quan sát mạng; không chứa token, secret hay dữ liệu nghiệp vụ nhạy cảm trong SNI.
- SNI và HTTP `Host`/`:authority` nên được xử lý nhất quán, không cho Host header giả mạo vượt qua TLS peer/route authorization.

### SNI khác hostname verification

| Cơ chế | Ai chủ động? | Kiểm điều gì? |
| --- | --- | --- |
| SNI | Client gửi tên trong handshake | Giúp server biết virtual host được yêu cầu |
| Certificate SAN | CA/issuer cấp danh tính vào certificate | Danh sách DNS/IP names được certificate khai báo |
| Endpoint identification | Client kiểm chứng certificate đối với peer host kỳ vọng | Xác nhận certificate hợp lệ cho hostname/IP đích |
| HTTP Host / :authority | HTTP client gửi tên của origin | Định tuyến HTTP; không thay TLS xác thực |
| App authorization | Ứng dụng kiểm tra caller/tenant/endpoint | Người dùng có quyền thực hiện tác vụ hay không |

Không kết luận “SNI đúng” khi mới chỉ gọi `getServerNames()`. Đó là kiểm local config; handshake thật phải được thực hiện và endpoint certificate vẫn phải xác thực thành công.

## 7. ALPN: thương lượng HTTP/2, HTTP/1.1 và giao thức khác

**ALPN** (*Application-Layer Protocol Negotiation*) là TLS extension cho client và server chọn giao thức ứng dụng chạy **trên** kết nối TLS. Ví dụ HTTPS client có thể đề xuất:

```text
Client ALPN offers: ["h2", "http/1.1"]
Server ALPN supports: ["http/1.1"]
Negotiated ALPN: "http/1.1"
```

Nếu cả hai bên hỗ trợ và policy cho phép, kết quả có thể là `h2`. ALPN **không tự triển khai HTTP/2**: ứng dụng phải có HTTP/2 framing, stream management, flow control và nội dung protocol. Nếu Java socket raw chỉ set ALPN `h2` mà chương trình vẫn ghi HTTP/1.1 text, kết nối sẽ không vận hành đúng.

### Java đặt ALPN offers

```java
SSLParameters p = engine.getSSLParameters();
p.setApplicationProtocols(new String[] { "h2", "http/1.1" });
engine.setSSLParameters(p);
```

Thứ tự offer có thể mang ý nghĩa ưu tiên tùy TLS stack/server. `setApplicationProtocols` khai báo danh sách ALPN được cấu hình, **chưa phải kết quả thương lượng**. Sau khi handshake hoàn tất:

```java
// Mã minh họa khi handshake thực sự đã thành công.
String selectedProtocol = engine.getApplicationProtocol();
```

Nếu ứng dụng **bắt buộc HTTP/2**, phải kiểm result là `"h2"` trước khi phát HTTP/2 frames. Nếu ứng dụng cho fallback HTTP/1.1 thì phải chuyển sang protocol implementation tương ứng. Khi peer không thương lượng ALPN hoặc trả kết quả không chấp nhận được, hành vi fail/fallback phải theo đặc tả của giao thức, không mặc nhiên coi là `"http/1.1"` cho mọi ứng dụng.

### ALPN server selection

JSSE cung cấp API phía server như `SSLSocket.setHandshakeApplicationProtocolSelector(...)` hoặc biến thể cho `SSLEngine` trong môi trường phù hợp. Server phải chọn **một protocol mà client đề xuất** và server thực sự triển khai, theo policy. Không trả tên arbitrary hoặc chọn `h2` mà ứng dụng không hỗ trợ.

ALPN còn xuất hiện ở TLS-based gRPC, HTTP/2 và nhiều protocol đặc thù. Một service gRPC thường cần kiểm yêu cầu `h2`/HTTP/2; đừng nghĩ đặt tên ALPN là đủ để service lập tức thành gRPC server.

### ALPN không giải quyết certificate trust

Cho dù chọn `h2` thành công, client vẫn phải xác minh chain, hostname, KU/EKU, protocol security và application authorization. Không dùng ALPN output như bằng chứng peer là một service cụ thể.

## 8. Endpoint identification: ràng buộc certificate với hostname đích

**Endpoint identification** là bước kiểm certificate của peer có đại diện cho **định danh dịch vụ được yêu cầu** hay không. Trong HTTPS, đó thường là DNS hostname hoặc IP literal của URL. Đây là lớp bảo vệ riêng với việc certificate được CA đáng tin ký.

Ví dụ:

```text
Requested endpoint: https://payments.example.test
Certificate chain: valid to trusted CA
Leaf SAN: DNS:files.example.test

CA path valid?  yes
Name valid?     no
Result:         reject connection
```

Chỉ tin CA không đủ: một CA đáng tin có thể phát hành chứng thư cho nhiều hostname. Nếu kiểm chain mà bỏ kiểm tên, kẻ tấn công có một certificate hợp lệ cho hostname của họ có thể đưa nó vào một kết nối mà ứng dụng tưởng thuộc hostname khác.

### Java bật HTTPS endpoint identification

```java
SSLParameters p = engine.getSSLParameters();
p.setEndpointIdentificationAlgorithm("HTTPS");
engine.setSSLParameters(p);
```

Thuộc tính `"HTTPS"` yêu cầu dùng thuật toán kiểm định danh HTTPS được JSSE hỗ trợ khi handshake. Điều kiện quan trọng:

- Engine/socket phải có **peer hostname kỳ vọng** được tạo/thiết lập đúng theo API; một `SSLEngine` không biết peer host có thể không có đủ ngữ cảnh để kiểm tên như bạn nghĩ.
- `SSLContext` phải có **trust manager** phù hợp để xác minh chain. Bật hostname check không tự biến một certificate thành đáng tin.
- Kết nối thực sự phải được handshake và trust manager/provider thực hiện validation. Chỉ nhìn `getEndpointIdentificationAlgorithm()` trả `"HTTPS"` là kiểm cấu hình, **không** chứng minh certificate.
- Với HTTP clients cấp cao như `java.net.http.HttpClient`, cơ chế endpoint verification thường được thư viện quản lý. **Không tắt hoặc vô tình ghi đè** các thiết lập nội bộ đó.
- Với raw `SSLSocket`, phải kiểm đúng lúc cấu hình, peer host và TLS context; không giả định mọi socket factory giống nhau.
- Với TLS server kiểm chứng **client certificate mTLS**, identity policy không giống HTTPS server hostname. Cần kiểm chain, KU/EKU clientAuth, mapping certificate identity tới principal và authorization.

### Những điều không nên làm

- `p.setEndpointIdentificationAlgorithm(null)` hoặc `""` để làm một certificate sai tên được chấp nhận.
- Cài `HostnameVerifier` luôn trả true, hoặc `X509TrustManager.checkServerTrusted` luôn rỗng.
- Lấy tên do user cung cấp bất kỳ rồi dùng làm “expected hostname” để chấp nhận certificate của attacker.
- So Subject CN bằng `contains(...)` hoặc `endsWith(...)` thay cho các quy tắc SAN/IDN/wildcard chuẩn.
- Coi SNI hostname là trusted identity mà không đối chiếu certificate.
- Tắt verification khi dùng proxy, service mesh, private CA: phải cấu hình đúng trust anchors, SNI/hostname và endpoint identity mà môi trường yêu cầu.

### SNI, ALPN và endpoint identification trong một luồng

```text
Client knows trusted URL/hostname = api.example.test
    |
    v
SSLParameters:
    protocol allowlist
    cipher suite allowlist
    SNI host = api.example.test
    ALPN offers = ["h2", "http/1.1"]
    endpointIdentificationAlgorithm = "HTTPS"
    |
    v
TLS ClientHello / server selection
    |
    v
Server certificate + agreed TLS version/cipher + ALPN
    |
    v
Trust chain validation + hostname SAN + TLS policy
    |
    +--> reject if any mandatory check fails
    |
    v
Authenticated TLS channel
    |
    v
Application speaks negotiated ALPN protocol
    |
    v
Application still checks authorization/tenant/resource
```

## 9. Chương trình Java 17+ chạy được, không cần kết nối mạng

Chương trình sau chỉ **cấu hình một `SSLEngine` client**, không gọi handshake và không gửi yêu cầu tới một hostname thật. Nó chọn **một protocol đang enabled** từ allowlist `TLSv1.3`/`TLSv1.2`, chọn **một cipher suite đã enabled và phù hợp với version được chọn**, rồi đặt SNI, ALPN và `HTTPS` endpoint identification.

Việc cố ý chọn **một** protocol và **một** suite giúp đọc code và kết quả dễ dàng. Đây **không phải khuyến nghị** production luôn ép một suite; production thường nên dùng allowlist tương thích hoặc defaults bảo mật được quản trị để tránh pin cứng làm giảm khả năng kết nối. Code fail closed nếu runtime không có lựa chọn nào trong list. Kết quả dưới đây chỉ chứng minh config được áp vào engine.

Lưu đúng tên **`SslParametersDemo.java`**:

```java
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SNIHostName;

public class SslParametersDemo {
    static String choose(String[] preferred, String[] enabled) {
        List<String> candidates = Arrays.asList(enabled);
        for (String value : preferred) {
            if (candidates.contains(value)) return value;
        }
        throw new IllegalStateException("No approved enabled option");
    }

    public static void main(String[] args) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null); // Demo: provider default key/trust managers.
        String host = "api.example.test";
        SSLEngine engine = context.createSSLEngine(host, 443);
        engine.setUseClientMode(true);

        String protocol = choose(
                new String[] {"TLSv1.3", "TLSv1.2"},
                engine.getEnabledProtocols());
        String[] preferredSuites = protocol.equals("TLSv1.3")
                ? new String[] {
                    "TLS_AES_128_GCM_SHA256",
                    "TLS_AES_256_GCM_SHA384",
                    "TLS_CHACHA20_POLY1305_SHA256"
                  }
                : new String[] {
                    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                    "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"
                  };
        String suite = choose(preferredSuites, engine.getEnabledCipherSuites());

        SSLParameters parameters = engine.getSSLParameters();
        parameters.setProtocols(new String[] {protocol});
        parameters.setCipherSuites(new String[] {suite});
        parameters.setServerNames(List.of(new SNIHostName(host)));
        parameters.setApplicationProtocols(new String[] {"h2", "http/1.1"});
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        engine.setSSLParameters(parameters);

        SSLParameters applied = engine.getSSLParameters();
        boolean protocolApplied = Arrays.equals(
                new String[] {protocol}, applied.getProtocols());
        boolean suiteApplied = Arrays.equals(
                new String[] {suite}, applied.getCipherSuites());
        boolean sniApplied = Objects.equals(
                List.of(new SNIHostName(host)), applied.getServerNames());
        boolean alpnApplied = Arrays.equals(
                new String[] {"h2", "http/1.1"},
                applied.getApplicationProtocols());

        System.out.println("Client mode: " + engine.getUseClientMode());
        System.out.println("Protocol in enabled allowlist: " +
                Arrays.asList(new String[] {"TLSv1.3", "TLSv1.2"})
                        .contains(protocol));
        System.out.println("Protocol applied: " + protocolApplied);
        System.out.println("Cipher suite applied: " + suiteApplied);
        System.out.println("SNI host applied: " + sniApplied);
        System.out.println("ALPN offers applied: " + alpnApplied);
        System.out.println("Endpoint identification: " +
                applied.getEndpointIdentificationAlgorithm());
        System.out.println("No handshake performed: true");
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac --release 17 SslParametersDemo.java
java SslParametersDemo
```

**Stdout đã đối chiếu sau khi chạy trên runtime có một trong các protocol/suite tương ứng đang enabled:**

```text
Client mode: true
Protocol in enabled allowlist: true
Protocol applied: true
Cipher suite applied: true
SNI host applied: true
ALPN offers applied: true
Endpoint identification: HTTPS
No handshake performed: true
```

Nếu runtime/provider không enable TLS 1.2/1.3 hoặc không có bất kỳ suite được phép, `choose` ném `IllegalStateException` một cách có chủ đích; đó là **fail closed**, không phải lý do bật suite yếu. Đây là output có điều kiện của môi trường test đã chạy, không cam kết mọi vendor/JDK giống hệt nhau.

### 9.1 Phân tích từng bước

1. `SSLContext.getInstance("TLS")` chọn implementation TLS từ provider. `context.init(null,null,null)` sử dụng key/trust configuration mặc định của provider cho mục đích demo; **nó không xác minh một server cụ thể** và không nên hiểu `null` luôn trỏ đúng file `cacerts` trong mọi runtime.
2. `createSSLEngine(host,443)` tạo engine có peer host/port tham chiếu, quan trọng cho các kiểm tra endpoint và SNI trong một handshake thật. Nó **không** kết nối mạng.
3. `setUseClientMode(true)` thiết lập vai trò TLS client. Cần đặt chế độ trước handshake và trước khi sử dụng những thông số riêng theo client.
4. `getEnabledProtocols()` cung cấp phiên bản đang enabled. `choose` chỉ lấy `TLSv1.3` hoặc `TLSv1.2` từ tập đó, không bừa bãi bật mọi supported protocol.
5. Danh sách cipher được chọn **phụ thuộc protocol**. Đây là ví dụ tối giản để tránh phối TLSv1.3 với TLSv1.2 suite. Một server thực có certificate/key/signature/ALPN và policy riêng nên handshake vẫn có thể thất bại dù local suite đã enabled.
6. `setProtocols` và `setCipherSuites` áp các lựa chọn đã kiểm; `setServerNames` đưa hostname SNI, `setApplicationProtocols` đề xuất `h2`/`http/1.1`, `setEndpointIdentificationAlgorithm("HTTPS")` bật kiểm định danh mong đợi.
7. `engine.setSSLParameters(parameters)` là bước **áp lại**. Sau đó `getSSLParameters()` kiểm những giá trị cục bộ, không kiểm trạng thái server.
8. Các biến boolean xác nhận các thuộc tính được giữ. Không dòng nào trong output là “TLS handshake successful” hay “server trusted”.
9. `No handshake performed: true` là thông báo về thiết kế của chương trình, **không phải một TLS security assertion**. Chương trình không gọi `engine.beginHandshake()`, `wrap` hoặc `unwrap`.

### 9.2 Vì sao không đưa toàn bộ code SSLEngine handshake vào demo?

`SSLEngine` là API nonblocking dành cho các chương trình biết quản lý buffer, I/O, handshake status và delegated tasks. Handshake thật cần vòng `wrap/unwrap`, `NEED_TASK`, `NEED_WRAP`, `NEED_UNWRAP`, timeout/EOF xử lý đúng, và transport peer thực tế. Một ví dụ ngắn mà chỉ gọi `beginHandshake()` rồi in “connected” sẽ **không đúng**.

Để tập trung vào `SSLParameters`, demo chỉ tạo/configure engine và kiểm bằng getters. Với ứng dụng HTTP thật, nên dùng HTTP client/JSSE library đã được kiểm chứng thay vì tự xây handshake machine.

### 9.3 Không nên copy hardcode suite sang production

Ví dụ ưu tiên `TLS_AES_128_GCM_SHA256` nếu dùng TLS 1.3, hoặc một suite ECDHE-GCM phù hợp khi dùng TLS 1.2. Đây là lựa chọn cho demo deterministic. Production cần:

- Không đặt mặc định TLS 1.2 nếu tổ chức bắt buộc TLS 1.3.
- Không ép một RSA/ECDSA suite TLS 1.2 khi server certificate/key không phù hợp.
- Không coi bộ mã có trong provider là đủ để được policy cho phép.
- Không để client tự chọn các mục tiêu bảo mật bất chấp policy.
- Test với server thật, dự phòng thay certificate và cập nhật JDK/root store.

## 10. SSLSocket và handshake thực: cấu hình phải có hiệu lực trước khi gửi

Với `SSLSocket`, bạn cũng có thể gọi `getSSLParameters()`, thay đổi và `setSSLParameters(...)` trước `startHandshake()`. Ví dụ **minh họa bên trong một method** (không phải chương trình độc lập, cần exception handling/import tương ứng và một `SSLContext context` đã được cấu hình đáng tin):

```java
String host = "api.example.test";
int port = 443;

try (SSLSocket socket = (SSLSocket)
        context.getSocketFactory().createSocket(host, port)) {
    SSLParameters p = socket.getSSLParameters();
    p.setServerNames(List.of(new SNIHostName(host)));
    p.setApplicationProtocols(new String[] {"h2", "http/1.1"});
    p.setEndpointIdentificationAlgorithm("HTTPS");
    socket.setSSLParameters(p);

    socket.startHandshake(); // Có thể thất bại do CA/SAN/protocol/suite.
    SSLSession session = socket.getSession();
    String negotiatedTls = session.getProtocol();
    String negotiatedCipher = session.getCipherSuite();
    String negotiatedApp = socket.getApplicationProtocol();

    // Chọn đúng implementation của negotiatedApp trước khi trao đổi dữ liệu.
}
```

Đoạn trên **không cấu hình giao diện HTTP/2 hay thực hiện HTTP request**. Nó chỉ cho thấy thông tin sau handshake và nhắc rằng TLS và HTTP là hai tầng khác nhau. Khi kết nối tới một test server thực tế, phải có truststore/CA phù hợp, DNS/SNI/hostname đúng và endpoint identification bật.

### Tại sao nên cấu hình trước handshake?

Handshake thực hiện thương lượng protocol, cipher suite, SNI/ALPN và peer authentication. Nếu sửa `SSLParameters` sau khi đã handshake, không thể mặc nhiên yêu cầu phiên hiện hữu bắt đầu lại hoặc kiểm certificate lần nữa. Trong thực tế, HTTP keep-alive, pooling và TLS session resumption còn làm policy reload phức tạp hơn. Khi policy thay đổi, cần tạo mới SSL contexts/clients hoặc quản lý pool/session theo threat model.

### SSLSocket với custom TrustManager

`SSLParameters` chỉ điều khiển một phần kiểm soát. Trust anchors đến từ `SSLContext`/`TrustManagerFactory`/`X509ExtendedTrustManager` phù hợp. Nếu tự viết `X509TrustManager`, phải hiểu đầy đủ việc JSSE có hay không áp endpoint checks với custom manager: không viết `checkServerTrusted` rỗng và mặc nhiên tin rằng `setEndpointIdentificationAlgorithm("HTTPS")` sẽ “bù lại” việc thiếu chain validation.

Ưu tiên trust manager của provider được quản lý đúng và kho CA được kiểm soát; tránh tự bỏ qua PKIX checks.

## 11. Java HTTP client cấp cao: tránh phá các ràng buộc do thư viện quản lý

`java.net.http.HttpClient` thường đã quản lý nhiều chi tiết TLS, HTTP/2 và hostname verification. Nếu không có yêu cầu đặc biệt, **giữ mặc định an toàn** của JDK và cập nhật runtime/trust policy. Một số API cho phép chỉ định:

```java
// Mã minh họa; giả sử SSLContext context được cấu hình phù hợp.
HttpClient client = HttpClient.newBuilder()
        .sslContext(context)
        .version(HttpClient.Version.HTTP_2)
        .build();
```

Đoạn trên không phải chương trình độc lập, cần import `java.net.http.HttpClient` và context đã tạo. `version(HTTP_2)` là sở thích/chế độ HTTP client ở lớp cao, **không tự chứng minh ALPN đã chọn h2 trên từng kết nối**. HTTP client có thể chọn/điều chỉnh ALPN hoặc fallback theo giao thức/API; kiểm thực tế qua response/client traces khi cần.

**Không nên** tạo một `SSLParameters` chứa mọi thứ rồi truyền bừa vào HTTP client: một số HTTP client quản lý ALPN/hostname verification của riêng nó và có thể thay đổi hoặc bỏ qua những thuộc tính do caller đưa vào. Đọc documentation của **đúng thư viện và phiên bản** được triển khai; kiểm behavior trong integration tests.

### Đừng tắt hostname verification qua global debug flags

Một số người dùng flag nội bộ hoặc custom `HostnameVerifier` luôn true để vượt lỗi certificate sai SAN. Làm vậy có thể xóa bảo đảm identity của HTTPS. Khi gặp vấn đề TLS, hãy sửa CA chain, SAN, URL hostname, SNI, proxy và truststore, không disable verification.

## 12. Cấu hình server khác client ở đâu?

Trên server, `SSLParameters` được áp vào `SSLServerSocket`, `SSLSocket` server-side hoặc `SSLEngine` server-side tùy kiến trúc. Các thuộc tính có thể được hiểu khác:

| Thuộc tính | Client TLS | Server TLS |
| --- | --- | --- |
| `setProtocols` | Những version client sẵn sàng offer | Những version server sẵn sàng accept |
| `setCipherSuites` | Những suite có thể thương lượng | Những suite server có thể chọn |
| `setServerNames` | Tên client gửi qua SNI | **Không** dùng để tuyên bố server sở hữu hostname |
| `setSNIMatchers` | Không thường dùng | Có thể kiểm/giới hạn SNI incoming theo policy |
| `setApplicationProtocols` | Danh sách ALPN client đề xuất | Danh sách ALPN server hỗ trợ |
| `setEndpointIdentificationAlgorithm("HTTPS")` | Dùng khi kiểm server HTTPS | Không phải cơ chế server công bố certificate/hostname |
| `setNeedClientAuth(true)` | Không phải thiết lập HTTPS server identity | Yêu cầu client certificate trong mTLS theo API thích hợp |
| `setWantClientAuth(true)` | Không thay authorization | Yêu cầu chứng thư client một cách tùy chọn theo server policy |
| `setUseCipherSuitesOrder` | Không ép server theo thứ tự client | Có thể thể hiện ưu tiên server trong trường hợp được hỗ trợ |

**Need vs want client auth:** `needClientAuth` thường làm handshake thất bại nếu không có client certificate hợp lệ theo policy; `wantClientAuth` cho phép client không cung cấp certificate trong tình huống được hỗ trợ. Hai cài đặt có tác động qua lại; không bật cả hai và đoán thứ tự override. Khi bật mTLS, server phải có truststore cho client CAs, identity mapping và authorization, chứ không chỉ `setNeedClientAuth(true)`.

### Server lựa chọn certificate theo SNI

Một reverse proxy hoặc Java server có nhiều certificate identities có thể dùng hostname SNI và `X509ExtendedKeyManager`/application server selection policy để chọn certificate/alias. Điều đó đòi hỏi key material/certificate chain đã được cấu hình. `SSLParameters.setSNIMatchers` không tự tạo alias hoặc thay key manager.

Nếu server chỉ có một certificate cho `api.example.test` mà client gửi SNI `admin.example.test`, server có thể chọn certificate default hoặc từ chối. Client vẫn phải kiểm SAN/endpoint identity, không chấp nhận certificate khác tên.

### Server chọn ALPN

Server phải chọn protocol có trong danh sách client gửi và server triển khai. Nếu server chọn `h2`, HTTP stack phải thật sự hiểu HTTP/2; nếu chọn `http/1.1`, dùng HTTP/1.1 parser. Nếu ứng dụng sử dụng TLS cho giao thức khác, chuỗi ALPN phải đúng chuẩn/registry tương ứng, không tự ý đặt tên vô nghĩa.

## 13. Tương tác giữa protocol, cipher suite, certificate và ALPN

Một cấu hình nhìn qua hợp lệ có thể thất bại do những ràng buộc chéo:

```text
Client enabled protocols:      TLSv1.3
Client configured cipher:      TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
Server supports:               TLSv1.3
Result:                        no mutually usable cipher suite for TLSv1.3

Client ALPN offers:            h2
Server ALPN supports:          http/1.1
Result:                        ALPN mismatch / no chosen protocol (policy-dependent)

Client target hostname:        a.example.test
Client SNI:                    b.example.test
Server presents cert:          b.example.test
Result:                        hostname verification must reject a.example.test
```

Từng dòng là **tình huống minh họa**, không phải output của một chương trình Java độc lập. Lỗi cụ thể có thể là `SSLHandshakeException` hoặc hành vi no-application-protocol/fallback tùy TLS version, provider và library; không xem câu chữ exception là contract cố định.

**Mấu chốt:** TLS có nhiều bước kiểm liên kết. Đừng chấp nhận thông điệp “Cipher suite applied: true” trong demo như bằng chứng handshake đã hợp lệ.

### Chữ ký chứng thư và cipher suite

TLS 1.2 suite tên `ECDHE_RSA` thường yêu cầu dạng chứng thư/authentication thích hợp; TLS 1.3 tách lựa chọn suite khỏi cơ chế authentication/signature algorithm và key-share negotiation. Do đó, chuyển từ TLS 1.2 sang TLS 1.3 có thể khiến các kiểm tra config “tên suite có RSA hay không” không còn phản ánh quy trình xác thực. Luôn xét version, certificate algorithm, signature schemes và provider policy.

### Session resumption và cấu hình mới

Nếu ứng dụng đổi trust roots, hostname policy, enabled versions hoặc ALPN, **các kết nối TLS đã thiết lập và sessions được resumption** có thể không lập tức theo policy mới. Trong thay đổi khẩn cấp (ví dụ distrust CA), cần kế hoạch thay client/context, đóng connection pools và kiểm tra các new handshakes/session caches. Không chỉ sửa một object `SSLParameters` rồi tuyên bố toàn cluster đã cập nhật.

## 14. Kiểm thử E2E: phải có cả positive và negative

Một bộ integration test nên phân biệt **configuration unit tests** với **TLS handshake tests**:

| Ca | Cấu hình hoặc dữ liệu đầu vào | Kết quả mong đợi |
| --- | --- | --- |
| Config protocol | Set TLSv1.3 enabled theo allowlist | `getSSLParameters` phản ánh cấu hình |
| Config suite | Set suite compatible và enabled | Được áp; **chưa chứng minh handshake** |
| TLS version thực | Client/server chỉ có chung TLSv1.2 | Handshake chọn TLSv1.2 nếu policy cho phép |
| No shared protocol | Client chỉ TLSv1.3, server chỉ TLSv1.2 | Handshake fail, không fallback ra ngoài policy |
| No usable cipher | Protocol chung nhưng suite không giao | Handshake fail |
| SNI đúng | Hai virtual hosts, client gửi tên A | Server chọn certificate/config đúng A nếu hỗ trợ |
| SNI sai | Client gửi tên B trong khi kỳ vọng A | Endpoint identity phải từ chối certificate B cho hostname A |
| SNI không có | Server cần SNI để chọn vhost | Fail hoặc behavior theo policy, không tin certificate sai |
| ALPN h2 | Cả hai hỗ trợ h2 | `getApplicationProtocol()` trả h2 sau handshake thành công |
| ALPN fallback | Client cho h2/http1, server chỉ http1 | Chọn http/1.1 nếu policy/implementation cho phép |
| ALPN mismatch | Không có protocol chung, ứng dụng yêu cầu h2 | Fail closed hoặc không tiến vào ứng dụng trái protocol |
| CA chưa tin | Server cert chain không tới trusted CA | Handshake reject |
| SAN mismatch | Chain hợp lệ cho hostname khác | HTTPS endpoint identification reject |
| Cert expired | Server leaf hết hạn | Handshake reject theo chính sách |
| Invalid KU/EKU | Leaf không cho serverAuth theo policy | TLS server auth reject |
| Client auth required | mTLS server need client cert, client không có | Handshake reject |
| Client certificate lạ | mTLS client có cert từ CA ngoài allowlist | Reject theo server trust policy |
| mTLS authorized? | Certificate hợp lệ nhưng principal không có quyền | TLS có thể pass; application rejects request |
| Downgrade attempt | Peer yêu cầu TLS cũ bị policy tắt | Reject; không tự enable legacy |
| Connection reused | Policy CA thay đổi trong lúc pool mở | Quan sát refresh/reload đúng theo lifecycle |
| Multi-runtime | JDK17/JDK khác nhau | Kiểm supported/enabled/ALPN/endpoint behavior thật |
| Proxy/mesh | TLS termination và SNI routing khác | Kiểm endpoint được xác thực đúng trust boundary |

**Kiểm thử đơn vị `SSLParameters` như mục 9 là cần nhưng không đủ.** Muốn chứng minh bảo mật kết nối, cần lab server có certificate chain/SAN được tạo có kiểm soát, truststore test và kiểm phiên thực sự được thương lượng.

### Tạo lab riêng

Bạn có thể dùng `keytool` tạo CA và leaf dành riêng cho `localhost`/tên test; dùng loopback port hoặc test container. **Không** import CA test vào `cacerts` của JDK sản xuất, không dùng trust-all. Cần kiểm certificate có SAN đúng hostname/loopback dùng, không chỉ `CN`. Với JSSE server có thể dùng `KeyStore`/`KeyManagerFactory`, client dùng `TrustManagerFactory` nạp CA test.

Muốn test ALPN đúng, server phải có implementation chọn `h2`/`http/1.1`, và client phải kiểm `getApplicationProtocol()` sau handshake. Với một server chỉ nói HTTP/1.1, đừng cấu hình ALPN `h2` rồi tuyên bố thành công của TLS đồng nghĩa HTTP/2 hoạt động.

## 15. Những lỗi phổ biến khi dùng SSLParameters

| Sai lầm | Hậu quả | Cách tránh |
| --- | --- | --- |
| Lấy toàn bộ `getSupportedProtocols()` để enable | Có thể bật version bị tắt do policy | Lấy allowlist giao với enabled và policy |
| Không xét TLS version khi chọn cipher suite | No shared cipher; cấu hình không tương thích | Dùng suite theo profile phiên bản; test handshake |
| Cố định một suite trong mọi service | Lỗi khi thay certificate hoặc peer policy | Ưu tiên defaults/curated compatible set |
| Chỉ sửa local `SSLParameters` mà quên `setSSLParameters` | Engine/socket chưa nhận thay đổi | Áp lại và kiểm getters |
| Cấu hình sau handshake | Session đã thương lượng trước đó | Cấu hình trước handshake, refresh connection lifecycle |
| Nhầm `setServerNames` là hostname verification | MITM/đồng nhất sai peer | Bật endpoint identification và trust validation |
| Dùng SNI khác URL hostname tùy ý | Server trả certificate sai identity | Ràng buộc SNI, peerHost và endpoint identity theo policy |
| Cho URL nguyên vào `SNIHostName` | Invalid SNI name / lỗi format | Chỉ hostname DNS thích hợp |
| Cho rằng ALPN offers = negotiated result | Gửi HTTP/2 frames tới HTTP/1.1 peer | Đọc negotiated ALPN sau handshake |
| Chỉ set `h2` ALPN nhưng không có HTTP/2 stack | Giao thức ứng dụng lỗi | Tích hợp library thực sự hiểu h2 |
| Tắt endpoint identification để sửa SAN lỗi | Chấp nhận server sai tên | Sửa certificate/URL/SNI/trust |
| Cài trust-all X509TrustManager | Loại bỏ CA/path validation | Trust manager PKIX đáng tin và truststore hẹp |
| Tự map clientAuth cert thành quyền admin | Lỗi authorization mTLS | Validate identity rồi áp RBAC/ABAC |
| Dùng một custom truststore cho mọi client mà không phân scope | Mở rộng hoặc mất public roots | Per-client SSLContext khi cần |
| Thay file truststore mà không reload context/pool | Sử dụng trust anchors cũ | Versioned config, reload và kiểm handshake mới |
| Dựa vào chuỗi exception text | Chẩn đoán sai qua các JDK | Log lý do đã làm sạch và test đúng provider |
| Coi `setSSLParameters` thành công = TLS an toàn | Bỏ qua chứng thư/cipher/ALPN thực | Xác minh qua handshake và negotiated session |
| Mặc định raw SSLEngine tự chạy I/O | Handshake không hoàn thành | Dùng đúng SSLEngine wrap/unwrap/tasks hoặc HTTP client |
| Dùng global debug flags ở production lâu dài | Log lớn, có thể lộ metadata | Bật có kiểm soát, tắt sau chẩn đoán |
| Không kiểm test âm | Không chứng minh boundary | Test CA sai, SAN sai, protocol/suite mismatch, ALPN mismatch |

## 16. Quy trình thiết kế cấu hình production

Một workflow có thể kiểm toán:

1. **Định nghĩa security policy:** TLS versions tối thiểu, cipher profiles, trust anchors, endpoint names, ALPN protocols và mTLS requirements; ghi rõ scope client/server.
2. **Kiểm provider/runtime:** Java version, JSSE provider, `getEnabledProtocols()`, enabled suites, `jdk.tls.disabledAlgorithms`, container image và root store.
3. **Ưu tiên default HTTPS client:** chỉ override khi có yêu cầu cụ thể và đã hiểu sự tương tác với HTTP/2, hostname verification, proxy/mesh.
4. **Nếu dùng raw SSLEngine/SSLSocket:** tạo context với trust/key managers đúng, dùng peer host thực, đặt client/server mode và cấu hình `SSLParameters` **trước handshake**.
5. **Dùng SNI đúng tên:** không dùng SNI như authorization; đối chiếu SAN/hostname của chứng thư.
6. **Đề xuất ALPN phù hợp ứng dụng:** HTTP/2 phải có stack h2; kiểm protocol đã thương lượng trước khi gửi dữ liệu.
7. **Giữ endpoint identification:** với HTTPS dùng thuật toán phù hợp, không bỏ chỉ vì private CA hoặc proxy.
8. **Kiểm cả chiều âm:** server sai hostname, CA không tin, EKU sai, protocol/suite yếu, ALPN mismatch đều phải được xử lý theo policy.
9. **Kiểm real negotiated state:** TLS version/cipher từ `SSLSession`, ALPN từ API sau handshake, peer certificate chain và identity thông qua TLS stack; không tự log secret.
10. **Quan sát và vận hành:** TLS handshake failure rate, protocol/cipher distribution, certificate renewal, truststore version và thay đổi ở server.
11. **Triển khai theo canary:** nhiều JVM/provider/regions, rollback được nhưng không bật lại chính sách yếu đã bị cấm.
12. **Thực hiện rotation/reload có chủ đích:** tạo context/client mới, quản lý session resumption/connection pooling và xử lý distrust CA khi cần.
13. **Đánh giá tương tác HTTP:** proxy, redirect, Alt-Svc, SNI/authority routing và HTTP/2 downgrade theo yêu cầu hệ thống.
14. **Không nhầm TLS authentication với application authorization:** dù TLS thành công, request vẫn phải được xác thực và cấp quyền nghiệp vụ.

### Ví dụ log nên và không nên

Nên (sau khi cân nhắc privacy): TLS version, cipher suite, ALPN negotiated, endpoint hostname, phiên bản trust bundle, loại lỗi handshake đã làm sạch, request/correlation ID. Không nên: private key, session secrets, raw application credentials, plaintext nhạy cảm, debug handshake chi tiết kéo dài hoặc bộ certificate chain chứa dữ liệu không cần cho mục đích giám sát.

## 17. Debug và xác định nguyên nhân handshake fail

Các nhóm lỗi hay gặp:

| Triệu chứng | Hướng kiểm tra |
| --- | --- |
| `SSLHandshakeException: no appropriate protocol` | Protocol allowlist, disabled algorithms, client/server versions |
| `no cipher suites in common` / `handshake_failure` | Giao giữa suites, TLS version, certificate algorithm và provider |
| `PKIX path building failed` | Truststore/CA chain, intermediate thiếu, CA policy, certificate validity |
| `No subject alternative DNS name matching` | SNI/URL host và leaf SAN, endpoint verification |
| ALPN chưa chọn `h2` | Server supported list, client offers, protocol implementations và negotiation |
| `no_application_protocol` alert | ALPN không giao trên peer yêu cầu ALPN và TLS stack báo lỗi |
| TLS thành công nhưng HTTP/2 fail | ALPN/config chưa thực sự có HTTP/2 codec hoặc proxy thay đổi |
| Hành vi khác sau restart | SSLContext/connection pools đã cache config cũ |
| SNI thiếu | Client tạo socket với IP, không set host, hoặc proxy/TLS library thay đổi |
| Certificate đúng nhưng mTLS client bị từ chối | Need/want client auth, client KeyManager, issuer trust và clientAuth EKU |
| Chỉ một JDK fail | Provider/version, truststore, disabledAlgorithms, cipher support khác |

Có thể bật JSSE debug trong môi trường kiểm soát:

```text
-Djavax.net.debug=ssl,handshake,trustmanager
```

Kết quả là log kỹ thuật lớn, có thể chứa hostnames, chứng thư và metadata nhạy cảm; chỉ thu thập khi cần và áp chính sách bảo vệ log. **Không** coi mọi thông điệp debug là bảo đảm protocol negotiation thành công; kiểm ứng dụng và TLS session thực tế.

## 18. Bài tập thực hành

### Bài 1 — Supported vs enabled

Chạy `SslParametersDemo` trên JDK 17. In danh sách `engine.getSupportedProtocols()` và `engine.getEnabledProtocols()` ở lab riêng, so sánh. Có protocol cũ nào supported nhưng không enabled? Giải thích tại sao không được bật lại chỉ để “tối đa compatibility”.

### Bài 2 — Tách protocol và cipher suites

Thay danh sách TLS versions được cho phép thành chỉ `TLSv1.2` trong lab, chọn một suite ECDHE-GCM đang enabled. Thiết lập suite TLS 1.3 để thấy cấu hình có thể không thương lượng được. Không dùng suite yếu để ép test pass.

### Bài 3 — Hai virtual hosts, một IP

Dựng hai TLS virtual hosts `api.example.test` và `admin.example.test` trong lab. Gửi SNI tương ứng, quan sát certificate. Sau đó yêu cầu kết nối tới `api.example.test` nhưng cố đặt SNI `admin.example.test`. Chứng minh endpoint identification vẫn từ chối nếu certificate không đúng hostname được yêu cầu.

### Bài 4 — ALPN và HTTP/2

Dùng server hỗ trợ cả `h2` và `http/1.1`, client offer hai giá trị, log `getApplicationProtocol()` sau handshake. Đổi server thành chỉ hỗ trợ `http/1.1` và kiểm fallback theo protocol policy. Tại sao ứng dụng không được gửi HTTP/2 frames trước khi xác định negotiated ALPN?

### Bài 5 — HTTPS endpoint identity

Tạo CA test và certificate có SAN `service.example.test`, thiết lập trust manager tin CA này. Test kết nối với đúng hostname và hostname khác, cả hai đều có thể đưa ra chain hợp lệ, nhưng chỉ hostname khớp mới được chấp nhận. Không tắt hostname check.

### Bài 6 — Per-client security policy

Một service gọi public API và private mTLS endpoint. Thiết kế hai `SSLContext`, truststores/KeyManagers phù hợp và `SSLParameters` riêng nếu cần. Phân tích SNI, ALPN, serverAuth/clientAuth, endpoint identity và authorization theo từng luồng.

### Bài 7 — Quản lý rollout

Hệ thống đang cho TLS 1.2 và 1.3; muốn dần chuyển sang TLS 1.3 only. Nêu telemetry cần thu, kiểm client compatibility, ALPN/HTTP2, cipher suite, JDK/provider và phương án rollback **không vi phạm** security policy.

### Bài 8 — Handshake state machine

Tạo một client `SSLEngine` thực sự kết nối tới test server qua TCP, xử lý `NEED_WRAP`, `NEED_UNWRAP`, `NEED_TASK` và `FINISHED` với timeout. So sánh với `SSLSocket` và HTTP client cấp cao. Nếu mục tiêu sản phẩm là HTTPS tiêu chuẩn, vì sao nên ưu tiên library hơn tự viết handshake loop?

### Bài 9 — Kiểm thử âm toàn diện

Tạo bảng test cho: CA không tin, certificate SAN sai, EKU chỉ clientAuth, không có shared TLS version, không có shared cipher suite, ALPN chỉ h2 khi server không hỗ trợ, SNI sai, mTLS client không có chứng thư, và connection pool giữ policy cũ. Chỉ rõ ca nào fail ở TLS, ca nào fail ở HTTP hoặc authorization.

**Điều kiện hoàn thành:** bạn biết chính xác giá trị nào thuộc `SSLParameters`, chúng được áp vào engine/socket thế nào, ba cơ chế **SNI – ALPN – hostname verification** khác nhau ở đâu, và tại sao chỉ khi handshake thành công cùng đầy đủ kiểm chứng peer thì mới được xem là có một kênh TLS xác thực.

## 19. Tài liệu tham khảo

- [Java SE 17 — SSLParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): `setProtocols`, `setCipherSuites`, `setServerNames`, `setApplicationProtocols`, `setEndpointIdentificationAlgorithm`.
- [Java SE 17 — SSLEngine](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLEngine.html): TLS nonblocking, `getSSLParameters`, `setSSLParameters`, `getApplicationProtocol`.
- [Java SE 17 — SSLSocket](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSocket.html): TLS socket, `startHandshake`, `getApplicationProtocol`.
- [Java SE 17 — SSLContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html): key/trust managers và tạo engine/socket.
- [Java SE 17 — SNIHostName](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SNIHostName.html): cấu trúc host_name SNI.
- [Java SE 17 — SSLSession](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSession.html): negotiated TLS version, cipher suite và peer metadata.
- [Java SE 17 — HttpClient.Builder](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.Builder.html): per-client SSL context, params và HTTP version.
- [Java 17 — JSSE Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): cấu hình/handshake/trust manager/ALPN và endpoint checks.
- [RFC 6066 — TLS Extensions](https://www.rfc-editor.org/rfc/rfc6066): SNI.
- [RFC 7301 — TLS ALPN Extension](https://www.rfc-editor.org/rfc/rfc7301): ALPN negotiation.
- [RFC 8446 — TLS 1.3](https://www.rfc-editor.org/rfc/rfc8446): handshake, TLS 1.3 cipher suites và certificate authentication.
- [RFC 5246 — TLS 1.2](https://www.rfc-editor.org/rfc/rfc5246): TLS 1.2 protocol và cipher suite model.
- [RFC 9525 — Service Identity in TLS](https://www.rfc-editor.org/rfc/rfc9525): khớp danh tính endpoint bằng certificate identifiers.
- [OWASP Transport Layer Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html): TLS protocol choices, certificates và vận hành.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jsse-004">16.4 SSLSocket, SSLServerSocket và điều khiển thời điểm handshake</a>

<details>
<summary>Xem chi tiết</summary>

# 16.4 SSLSocket, SSLServerSocket và điều khiển thời điểm handshake

## Mục tiêu và bối cảnh

Bài học thuộc **16. JSSE** của Java Advance – security-cryptography. Sau khi hoàn thành, bạn có thể:

- Phân biệt `SSLContext`, `SSLSocketFactory`, `SSLServerSocketFactory`, `SSLServerSocket`, `SSLSocket`, `SSLParameters` và `SSLSession`.
- Hiểu trình tự **TCP connect/accept → cấu hình TLS → handshake → xác thực danh tính → đọc/ghi application data → close**.
- Giải thích sự khác nhau giữa **gọi `startHandshake()` tường minh** và **handshake tự động** khi sử dụng I/O của `SSLSocket`.
- Chỉ ra API có thể **kích hoạt handshake ngoài ý muốn** (đặc biệt `getSession()`), và hiểu ý nghĩa/giới hạn của `getHandshakeSession()` và `HandshakeCompletedListener`.
- Cấu hình phiên bản TLS, cipher suites, endpoint identification, mTLS, SNI/ALPN và timeout **trước khi handshake bắt đầu**.
- Chạy ví dụ độc lập với **Java 17+**, `keytool` và hai `SSLContext`: server/client trên loopback `127.0.0.1`, có xác minh certificate bằng truststore; so sánh explicit handshake và implicit handshake với một lần trao đổi dữ liệu.
- Chẩn đoán lỗi `SSLHandshakeException` ở đúng thời điểm, tránh tự tạo trust manager không kiểm tra certificate hoặc lặp `startHandshake()` để cố “renegotiate” TLS 1.3.

**Thông điệp quan trọng:** Một kết nối TCP đã `connect()/accept()` **chưa đồng nghĩa** có kênh TLS được xác thực. Và `SSLServerSocket` là listener, **không phải** kênh ứng dụng của một client. Chỉ khi handshake thành công theo chính sách trust/identity, cộng thêm authorization ở tầng nghiệp vụ, ứng dụng mới nên xử lý request có quyền.

## 1. Các thành phần JSSE phối hợp với nhau

JSSE (*Java Secure Socket Extension*) cung cấp TLS trong JVM. Các class sau không có cùng trách nhiệm:

| Thành phần | Vai trò | Ví dụ API |
| --- | --- | --- |
| `SSLContext` | Tập hợp key manager, trust manager và nguồn ngẫu nhiên; cung cấp socket factories | `SSLContext.getInstance("TLS")`, `init(...)` |
| `KeyManagerFactory` | Chọn private key và certificate chain để **chứng minh danh tính phía mình** | `init(keyStore, keyPassword)` |
| `TrustManagerFactory` | Tạo kiểm tra trust anchor/chain cho certificate từ **peer** | `init(trustStore)` |
| `SSLSocketFactory` | Tạo kết nối TLS phía client hoặc bọc một socket đã connect | `createSocket(host, port)` |
| `SSLServerSocketFactory` | Tạo listener TLS ở server | `createServerSocket(port, backlog, bindAddress)` |
| `SSLServerSocket` | Bind/listen/accept; thiết lập TLS defaults cho các kết nối nhận | `accept`, `setEnabledProtocols`, `setNeedClientAuth` |
| `SSLSocket` | Một kết nối TLS cụ thể; handshake, stream I/O và session | `startHandshake`, `getInputStream`, `getOutputStream` |
| `SSLParameters` | Cấu hình SSL/TLS liên quan socket, như endpoint identification, ALPN và SNI | `setEndpointIdentificationAlgorithm`, `setApplicationProtocols` |
| `SSLSession` | Thông tin phiên TLS, ví dụ protocol/cipher suite/peer cert | `getProtocol`, `getCipherSuite`, `getPeerCertificates` |
| `HandshakeCompletedListener` | Nhận sự kiện hoàn tất handshake của `SSLSocket` | `addHandshakeCompletedListener` |

**Sơ đồ:**

```text
        CLIENT                                      SERVER
  truststore -> TrustManagerFactory         keystore -> KeyManagerFactory
                    |                                      |
               SSLContext                              SSLContext
                    |                                      |
            SSLSocketFactory                    SSLServerSocketFactory
                    |                                      |
               SSLSocket  -------- TCP -------->  SSLServerSocket
                    |                                 accept()
                    |                                      |
                    |                                 SSLSocket (peer)
                    |                                      |
          cấu hình trước TLS                       cấu hình trước TLS
                    +---------- TLS handshake -----------+
                    |                                      |
             kiểm tra identity                      client-cert nếu mTLS
                    |                                      |
                    +==== encrypted application bytes ====+
```

Đầu client có thể chỉ cần **trust manager** để kiểm tra server certificate; server cần **key manager** chứa server private key và chain để trình diện. Với mTLS, client cũng phải có key manager/client certificate và server phải có trust manager để đánh giá client cert.

`SSLContext.init(...)` thành công **chưa làm một handshake nào**. `SSLServerSocket.accept()` nhận một kết nối tới listener và trả một `SSLSocket`; handshake TLS có thể chưa được thực hiện. `SSLSession` chỉ nên được dùng để quan sát session sau khi handshake thành công; việc gọi `getSession()` sớm tự nó có thể bắt đầu handshake.

## 2. Phân biệt thời điểm TCP và TLS

| Giai đoạn | Điều chắc chắn | Điều chưa chắc chắn |
| --- | --- | --- |
| Tạo `SSLContext` | Có cấu hình cryptographic services được chọn | Chưa có peer/kết nối |
| Tạo `SSLServerSocket` | Server lắng nghe trên port | Chưa có client TLS authenticated |
| Client `SSLSocketFactory.createSocket(...)` | Socket được tạo, thông thường TCP đã connect | Chưa chắc handshake TLS đã xảy ra |
| Server `SSLServerSocket.accept()` | Đã nhận TCP connection | Chưa chắc certificate/client identity được xác minh |
| Cấu hình `setEnabledProtocols(...)` | Giới hạn TLS versions ứng dụng cho phép | Chưa biết negotiated version thực |
| `startHandshake()` hoặc I/O kích hoạt handshake | JSSE thực thi negotiation, key establishment và xác thực phù hợp | Có thể thất bại ngay tại đây |
| Handshake trả thành công | Có TLS channel được thiết lập theo chính sách JSSE đang dùng | Không tự cho client quyền truy cập API |
| Đọc `SSLSession` sau handshake | Có thể biết negotiated protocol/cipher | Không tự chứng minh resource authorization |

Trong TLS server authentication thông thường, client kiểm tra chain của server tới **trust anchor** đã tin từ trước và **hostname/IP đích** phải khớp certificate SAN. **Không coi** certificate hợp lệ dưới một CA bất kỳ là đúng server nếu SAN không khớp. Phía server cũng không thể suy ra danh tính người dùng chỉ từ việc một TCP connection đã được accept.

TLS là một **byte stream được bảo vệ**, không phải một hệ thống auth/authorization đầy đủ. Tầng ứng dụng vẫn phải phân tích request, kiểm tra quyền theo resource/tenant/user, chống replay nếu protocol yêu cầu và quản lý lifecycle.

## 3. Explicit handshake với `SSLSocket.startHandshake()`

Có thể yêu cầu handshake ở một điểm rõ ràng sau khi tạo và cấu hình socket:

```java
SSLSocket socket = (SSLSocket) sslFactory.createSocket(host, port);
socket.setEnabledProtocols(new String[] {"TLSv1.3"});
SSLParameters params = socket.getSSLParameters();
params.setEndpointIdentificationAlgorithm("HTTPS");
socket.setSSLParameters(params);
socket.startHandshake();  // TLS checkpoint: thành công hoặc báo lỗi.
SSLSession session = socket.getSession();
```

Đoạn trên **chỉ là snippet**, chưa phải chương trình độc lập: cần `sslFactory` được tạo từ `SSLContext` với trust manager đúng, `host/port`, imports `javax.net.ssl.*`, xử lý exceptions và đóng socket trong try-with-resources.

**Lợi ích của `startHandshake()` tường minh:**

- Lỗi certificate/trust, TLS protocol mismatch hoặc cipher negotiation thường xuất hiện ở **chính `startHandshake()`**, thay vì ở lần `write/read` đầu tiên.
- Ứng dụng có thể chỉ gửi dữ liệu nhạy cảm **sau khi** TLS đã được thiết lập và policy bổ sung được kiểm tra.
- Rõ ràng về logging, telemetry và timeout cho giai đoạn TLS, tách khỏi application request.
- Không vô tình làm thay đổi thứ tự handshake bằng việc gọi `getSession()` trước khi cấu hình endpoint identity.

Tại server, nhận `SSLSocket` từ listener và chủ động handshake:

```java
try (SSLSocket peer = (SSLSocket) serverSocket.accept()) {
    peer.setSoTimeout(10_000);
    peer.startHandshake();
    // Chỉ bắt đầu xử lý yêu cầu sau khi handshake thành công.
}
```

Đây cũng là **snippet**, giả định `serverSocket` là `SSLServerSocket` đã cấu hình, và chỉ minh họa điểm checkpoint. `peer.startHandshake()` có thể block trong khi chờ client; server cần giới hạn thời gian và tài nguyên. Không thực hiện blocking handshake vô hạn trên thread nhận kết nối duy nhất của server production.

**Lưu ý:** Một lời gọi `startHandshake()` chỉ “bắt đầu handshake” theo contract trên socket TLS; để xác thực đúng server, client vẫn phải có **truststore/trust manager và endpoint identification**. Nếu ứng dụng cấu hình “trust all” thì handshake có thể thành công mà không bảo đảm danh tính đúng.

## 4. Implicit handshake: lần I/O đầu tiên cũng có thể là handshake

`SSLSocket` thực hiện **initial handshake tự động** khi một thao tác cần kết nối TLS có thể sử dụng. Ví dụ:

```java
try (SSLSocket socket = (SSLSocket) sslFactory.createSocket(host, port)) {
    // Phải cấu hình protocol, hostname/trust, timeout từ trước.
    socket.getOutputStream().write(requestBytes); // Handshake nếu cần.
    socket.getOutputStream().flush();
    int firstResponseByte = socket.getInputStream().read();
}
```

Snippet trên chưa độc lập: `sslFactory`, `host/port`, `requestBytes` phải được cung cấp; thiếu cấu hình trust/endpoint là một lỗi bảo mật. Nếu chưa có handshake, thao tác `write`/`read` sẽ kích hoạt nó, vì **application bytes không thể được truyền dưới kênh TLS đúng khi chưa thiết lập khóa phiên**.

**Sự khác biệt quan trọng nhất là vị trí lỗi:**

| Flow | Điểm bắt đầu TLS | Lỗi TLS có thể được thấy ở đâu? |
| --- | --- | --- |
| Explicit | `socket.startHandshake()` | Ngay tại `startHandshake()` |
| Implicit | `getOutputStream().write(...)` hoặc `getInputStream().read()` | **Trong I/O** thay vì trong một lời gọi handshake rõ ràng |
| Đọc session sớm | `socket.getSession()` | `getSession()` có thể tự kích hoạt initial handshake |
| Server explicit | `peer.startHandshake()` sau `accept` | Trong handshake trên accepted socket |
| Server implicit | `peer.getInputStream().read()` | Trong lần I/O trên accepted socket |

Lấy `getInputStream()`/`getOutputStream()` không nên bị coi là một checkpoint xác thực trong mọi provider; khi **thực sự đọc/ghi** thì JSSE phải bảo vệ dữ liệu ứng dụng và có thể khởi handshake. Nếu muốn một điểm rõ ràng trước request, **chủ động gọi `startHandshake()`**.

### Bẫy `getSession()` và `getHandshakeSession()`

`SSLSocket.getSession()` có thể tự khởi tạo handshake nếu initial handshake chưa bắt đầu. **Không** dùng nó để “kiểm tra xem đã handshake chưa” trước khi thiết lập `SSLParameters`. Một getter có tác dụng phụ có thể khiến certificate được chấp nhận mà chưa bật endpoint identification đúng.

`getHandshakeSession()` dùng trong một **handshake đang diễn ra** (khi có handshake session) và có thể trả `null` khi không có handshake đang hoạt động. Nhưng `null` **không chứng minh** socket chưa bao giờ handshake: sau khi handshake kết thúc, cũng có thể không còn handshake session.

### Hai timeline

```text
EXPLICIT                                  IMPLICIT
createSocket()                            createSocket()
setEnabledProtocols(...)                  setEnabledProtocols(...)
set SSLParameters (HTTPS identity)         set SSLParameters (HTTPS identity)
startHandshake() ---------------- TLS     output.write(42) -------- TLS
  | success or SSLHandshakeException         | success or SSLHandshakeException
output.write(42)                           app byte 42 đi sau handshake
input.read()                               input.read()
getSession()                               getSession()
```

Cả hai có thể đạt cùng negotiated TLS version và trust policy. **Không phải implicit kém bảo mật hơn chỉ vì không gọi `startHandshake()`**; rủi ro phát sinh khi code không biết điểm handshake thật và **không xử lý lỗi hoặc cấu hình quá muộn**.

## 5. `SSLServerSocket`: điều khiển phía server

Các bước cho một server JSSE trực tiếp:

1. Tải keystore chứa private key và chain của server, khởi tạo `KeyManagerFactory` và `SSLContext`.
2. Tạo listener bằng `SSLServerSocketFactory.createServerSocket(...)`, có địa chỉ/port và backlog thích hợp.
3. Cài protocol/cipher allowlist, `SSLParameters` và chính sách client certificate **trên listener trước khi accept**.
4. `accept()` để nhận một `SSLSocket` cho **từng kết nối riêng biệt**.
5. Cài `setSoTimeout(...)` trên accepted socket, thêm listener/chỉnh policy cần thiết **trước handshake**.
6. Gọi `startHandshake()` hoặc để I/O đầu tiên kích hoạt.
7. Chỉ sau khi TLS thành công và ràng buộc danh tính/ủy quyền phù hợp mới xử lý application data.
8. Đóng socket kết nối khi xong; listener có thể tiếp tục accept connection khác.

`SSLServerSocket` không phải stream của ứng dụng. Các method `getInputStream` và `getOutputStream` nằm trên **accepted `SSLSocket`**, không nằm trên listener.

### mTLS: `setNeedClientAuth` và `setWantClientAuth`

- `setNeedClientAuth(true)`: server **yêu cầu** client certificate. Client phải có certificate/private key thích hợp và server phải có trust manager xác minh certificate đó; nếu không thỏa, handshake không thành công.
- `setWantClientAuth(true)`: server muốn nhận certificate nhưng có thể tiếp tục khi client không cung cấp; **không tự coi mọi client đã xác thực**.
- Nếu client certificate có thật và được trust, **vẫn cần ánh xạ** certificate identity tới user/service/tenant/role và kiểm tra quyền nghiệp vụ.

Hãy thiết lập chính sách client-auth **trước khi handshake**. Không “bật mTLS” sau khi connection đã được thiết lập mà mong client lập tức cung cấp chứng chỉ như một lần đăng nhập mới.

### TLS server không phải một thread duy nhất

`accept()` có thể block, handshake cũng có thể block khi chờ peer. Production cần accept loop, concurrency giới hạn, timeout, rate limiting, quota và khả năng shutdown. Một client có thể gửi TCP SYN/connect nhưng cố tình trì hoãn TLS handshake. Không để hàng nghìn socket chờ vô hạn và tiêu hết file descriptors/threads.

## 6. Những cấu hình phải đặt trước khi bắt đầu TLS

| Cấu hình | Thời điểm phù hợp | Ý nghĩa |
| --- | --- | --- |
| `SSLContext.init(keyManagers, trustManagers, random)` | Trước khi lấy socket factory | Nguồn private key/certificate và trust anchors |
| `SSLSocket.setUseClientMode(true/false)` | Trước handshake | Vai trò của đầu TLS; thay sau khi TLS bắt đầu có thể không hợp lệ |
| `SSLServerSocket.setEnabledProtocols(...)` | Trước accept/handshake của các kết nối | Allowed versions của peer sockets |
| `SSLSocket.setEnabledProtocols(...)` | Trước handshake trên socket | Allowed TLS versions; **không phải negotiated version** |
| `setEnabledCipherSuites(...)` | Trước handshake | Cipher suites được phép; hỗ trợ tùy provider |
| `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` | Client trước handshake | Yêu cầu kiểm tra hostname/IP đích dựa trên certificate |
| `SSLParameters.setServerNames(...)` | Client trước handshake nếu cần | SNI giúp chọn virtual host/certificate |
| `SSLParameters.setApplicationProtocols(...)` | Trước handshake nếu có ALPN | Danh sách application protocols được phép |
| `setNeedClientAuth(...)`/`setWantClientAuth(...)` | Server trước handshake | Chính sách client certificate |
| `addHandshakeCompletedListener(...)` | Trước handshake muốn quan sát | Callback khi handshake hoàn tất |

Java 17 hỗ trợ TLS 1.3 trong provider chuẩn phù hợp. Dùng `SSLContext.getInstance("TLS")` có thể cho phép nhiều phiên bản tùy provider/policy, còn `setEnabledProtocols(new String[]{"TLSv1.3"})` giới hạn socket. **Phải kiểm tra phiên bản thực sự negotiated** sau handshake qua `SSLSession.getProtocol()`. Không bật TLS 1.0/1.1 hay cipher suites yếu chỉ để tránh lỗi interoperability.

### Endpoint identification là một phần khác trust chain

Client cần **cả** đường tin cậy tới trust anchor **và** kiểm tra danh tính endpoint. Ví dụ connect tới `127.0.0.1` thì certificate lab bên dưới có **IP SAN 127.0.0.1**. `CN=localhost` riêng lẻ không thay thế IP SAN khi host đích là IP literal.

SNI có thể giúp server chọn certificate nhưng **không thay hostname verification**. Với IP literal trong lab, demo không dựa vào SNI; với DNS virtual hosts, chính sách SNI, SAN và trust manager phải phối hợp.

## 7. Lab Java 17: hai chế độ handshake có chứng thư được trust

Mục tiêu là tạo một ví dụ **thực sự chạy được** trên cùng máy, không truy cập Internet và không dùng trust-all.

### 7.1 Chuẩn bị keystore/truststore

Tạo certificate **tự ký dành riêng cho test** có `SAN=dns:localhost,ip:127.0.0.1`. Import public certificate vào truststore của client. Đây là **trust được cấp phát chủ động ngoài kết nối mạng**, khác hoàn toàn việc tự tin bất kỳ certificate nào server gửi.

Mở PowerShell có `java`, `javac` và `keytool.exe` từ JDK 17+ trên `PATH`. Chạy toàn bộ script dưới đây. Chỉ dùng thư mục `%TEMP%\jsse-handshake-lab`, **không sửa `cacerts` hoặc truststore hệ điều hành**:

```powershell
$ErrorActionPreference = 'Continue'
$qa = Join-Path $env:TEMP 'jsse-handshake-lab'
New-Item -ItemType Directory -Force -Path $qa | Out-Null
Set-Location $qa
$p = 'changeit'

function Run-Keytool([string[]]$arguments) {
    $out = & keytool.exe @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ("keytool failed: " + ($arguments -join ' ') + ' => ' + ($out -join ' '))
    }
}

if (Test-Path -LiteralPath 'server.p12') {
    Remove-Item -LiteralPath 'server.p12' -Force
}
if (Test-Path -LiteralPath 'trust.p12') {
    Remove-Item -LiteralPath 'trust.p12' -Force
}

Run-Keytool @("-genkeypair","-alias","localhost",
    "-keyalg","RSA","-keysize","2048",
    "-sigalg","SHA256withRSA","-validity","30",
    "-dname","CN=localhost,O=TLS Training",
    "-ext","SAN=dns:localhost,ip:127.0.0.1",
    "-ext","EKU=serverAuth",
    "-ext","KU=digitalSignature,keyEncipherment",
    "-storetype","PKCS12","-keystore","server.p12",
    "-storepass",$p,"-keypass",$p,"-noprompt")

Run-Keytool @("-exportcert","-alias","localhost",
    "-rfc","-file","server.pem",
    "-keystore","server.p12","-storepass",$p)

Run-Keytool @("-importcert","-alias","localhost",
    "-file","server.pem",
    "-keystore","trust.p12","-storetype","PKCS12",
    "-storepass",$p,"-noprompt")

Write-Output "Local TLS lab stores ready"
```

**Stdout cuối trên môi trường kiểm thử:**

```text
Local TLS lab stores ready
```

Ba tệp:

- `server.p12`: keystore có **private key + certificate của server**; chỉ phía server cần.
- `server.pem`: **public certificate** đã export; có thể phân phối nhưng nguồn cần được xác thực khi dùng trong thực tế.
- `trust.p12`: truststore phía client chứa certificate lab được **tin cậy có chủ đích**.

`changeit` là mật khẩu cố định **chỉ dùng cho lab**. Không đưa `server.p12` vào git, không dùng certificate này trong production, không tự tin file public certificate do attacker cung cấp. Với production, dùng CA/KMS/HSM, chính sách phát hành, renew/revoke và secure secret storage phù hợp.

**Ghi chú PowerShell:** Script chạy trong một thư mục **tạm riêng** và xóa các tệp `server.p12`/`trust.p12` trong chính thư mục lab khi tạo lại. Không đặt tệp cá nhân có cùng tên vào thư mục lab. Hãy giữ cú pháp PowerShell, không copy y nguyên vào Bash.

### 7.2 Chương trình `HandshakeTimingDemo.java`

Lưu source dưới đây trong **cùng thư mục** với `server.p12` và `trust.p12`. Dùng đúng tên file `HandshakeTimingDemo.java`:

```java
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
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

public final class HandshakeTimingDemo {
    private static final String PASSWORD = "changeit"; // Local lab ONLY.
    private static final String TLS_VERSION = "TLSv1.3";
    private static final String HOST = "127.0.0.1";

    private record ServerResult(boolean received, boolean event,
                                String protocol) {}

    public static void main(String[] args) throws Exception {
        SSLContext server = serverContext("server.p12");
        SSLContext client = clientContext("trust.p12");
        runScenario("EXPLICIT", true, server, client);
        runScenario("IMPLICIT", false, server, client);
    }

    private static SSLContext serverContext(String keyStoreFile)
            throws Exception {
        KeyStore store = loadKeyStore(keyStoreFile);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(store, PASSWORD.toCharArray());
        SSLContext context = SSLContext.getInstance(TLS_VERSION);
        context.init(kmf.getKeyManagers(), null, new SecureRandom());
        return context;
    }

    private static SSLContext clientContext(String trustStoreFile)
            throws Exception {
        KeyStore store = loadKeyStore(trustStoreFile);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store);
        SSLContext context = SSLContext.getInstance(TLS_VERSION);
        context.init(null, tmf.getTrustManagers(), new SecureRandom());
        return context;
    }

    private static KeyStore loadKeyStore(String file) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(Path.of(file))) {
            store.load(in, PASSWORD.toCharArray());
        }
        return store;
    }

    private static void runScenario(String label, boolean explicit,
                                    SSLContext serverContext,
                                    SSLContext clientContext) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SSLServerSocket listener = (SSLServerSocket)
                serverContext.getServerSocketFactory().createServerSocket(
                        0, 5, InetAddress.getByName(HOST))) {
            listener.setEnabledProtocols(new String[] {TLS_VERSION});

            Future<ServerResult> serverTask = executor.submit(() -> {
                try (SSLSocket peer = (SSLSocket) listener.accept()) {
                    peer.setSoTimeout(10000);
                    peer.setEnabledProtocols(new String[] {TLS_VERSION});
                    CountDownLatch event = new CountDownLatch(1);
                    peer.addHandshakeCompletedListener(done -> event.countDown());

                    if (explicit) {
                        peer.startHandshake();
                    }
                    int request = peer.getInputStream().read(); // Implicit if needed.
                    peer.getOutputStream().write(43);
                    peer.getOutputStream().flush();
                    boolean fired = event.await(2, TimeUnit.SECONDS);
                    return new ServerResult(request == 42, fired,
                            peer.getSession().getProtocol());
                }
            });

            try (SSLSocket socket = (SSLSocket)
                    clientContext.getSocketFactory().createSocket(
                            HOST, listener.getLocalPort())) {
                socket.setSoTimeout(10000);
                socket.setEnabledProtocols(new String[] {TLS_VERSION});
                SSLParameters parameters = socket.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                socket.setSSLParameters(parameters);
                CountDownLatch event = new CountDownLatch(1);
                socket.addHandshakeCompletedListener(done -> event.countDown());

                if (explicit) {
                    socket.startHandshake();
                }
                socket.getOutputStream().write(42); // Implicit if needed.
                socket.getOutputStream().flush();
                int answer = socket.getInputStream().read();
                boolean clientEvent = event.await(2, TimeUnit.SECONDS);
                ServerResult serverResult = serverTask.get(15, TimeUnit.SECONDS);
                boolean versionOk = TLS_VERSION.equals(
                        socket.getSession().getProtocol())
                        && TLS_VERSION.equals(serverResult.protocol());
                System.out.println(label + " TLSv1.3: " + versionOk);
                System.out.println(label + " handshake listeners: "
                        + (clientEvent && serverResult.event()));
                System.out.println(label + " one-byte exchange: "
                        + (answer == 43 && serverResult.received()));
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
```

Biên dịch và chạy trong PowerShell (vẫn đứng trong `jsse-handshake-lab`):

```powershell
javac --release 17 HandshakeTimingDemo.java
java -cp . HandshakeTimingDemo
```

**Stdout đã kiểm thử bằng JDK hỗ trợ TLSv1.3:**

```text
EXPLICIT TLSv1.3: true
EXPLICIT handshake listeners: true
EXPLICIT one-byte exchange: true
IMPLICIT TLSv1.3: true
IMPLICIT handshake listeners: true
IMPLICIT one-byte exchange: true
```

**Lưu ý về thời gian:** Chương trình dùng `setSoTimeout(10000)`, chờ callback tối đa **2 giây** và chờ server worker qua `Future.get(15,...)`; những giá trị này nhằm tránh **lab treo vô hạn**, không phải cam kết của API JSSE. Nếu máy quá tải, một phép thử có thể bị timeout trước khi sự kiện callback được xử lý; đó chưa chắc là lỗi thuật toán TLS.

### 7.3 Vì sao Java code thể hiện đúng hai mode?

**Server và client contexts khác nhau:** `serverContext` tải keystore, khởi tạo `KeyManagerFactory` để trình server certificate. `clientContext` tải truststore, khởi tạo `TrustManagerFactory` để xác minh certificate. Không sử dụng trust manager kiểu “return true”. Client bật `HTTPS endpoint identification` trước khi bắt đầu handshake, khớp với **IP SAN** của certificate lab.

**Chỉ loopback:** Listener bind `InetAddress.getByName("127.0.0.1")`, port `0` do OS cấp. Không expose một server chưa có authorization ra mạng ngoài. Port không cố định nên nhiều lượt chạy ít xung đột.

**Tính đồng thời:** `ExecutorService` chạy phần `accept/read/write` phía server; main thread là client. Nếu không tách, client và server có thể block nhau. `accept()` không tự xử lý application bytes; code sử dụng **accepted `SSLSocket`** trong lambda.

**Explicit:** cả hai phía gọi `startHandshake()` trước I/O. Nếu client không tin certificate server, lỗi thường xuất hiện ở điểm này (peer bên kia nhận TLS alert/lỗi tương ứng). Sau khi TLS hoàn tất, client gửi byte `42`, server trả `43`.

**Implicit:** không có lời gọi `startHandshake()`. Client bắt đầu `OutputStream.write(42)`, server đang chờ `InputStream.read()`; JSSE tự khởi initial handshake và bảo vệ bytes truyền đi. Kết quả một byte vẫn là `42/43` nhưng **địa điểm lỗi TLS có thể nằm trong `write/read`**.

**Listener:** `HandshakeCompletedListener` chỉ gọi `CountDownLatch.countDown()`. Callback có thể bất đồng bộ; code đợi event ngắn để kiểm thử. Đừng coi việc callback được kích hoạt là application authorization; cả hai đầu cần kiểm tra chính sách danh tính/role tùy use case.

**Protocol:** `SSLSession.getProtocol()` được đọc **sau** handshake/I/O. Chỉ khi đó mới xác nhận negotiated `TLSv1.3`; `setEnabledProtocols` trước đó là allowlist, không phải bằng chứng negotiation.

**Kiểm thử logic ứng dụng:** `answer == 43 && serverResult.received()` chứng tỏ hai đầu đã trao đổi byte mong đợi. Nó **không phải** một định dạng message production: TLS không cung cấp framing ứng dụng.

## 8. `startHandshake()` không phải công tắc renegotiation TLS 1.3

Phân biệt:

1. **Initial handshake** trên một `SSLSocket` mới.
2. **Session resumption** trên một **kết nối TLS mới**, có thể dùng session ticket/cache theo protocol và policy; dù resume vẫn có handshake của connection mới.
3. **Renegotiation** kiểu TLS cũ: **TLS 1.3 không hỗ trợ renegotiation theo kiểu TLS 1.2**. TLS 1.3 có những cơ chế khác như KeyUpdate, nhưng chúng không phải một lần `startHandshake()` để tự động đổi user/certificate hoặc làm lại full handshake.

**Không viết một workflow bảo mật dựa trên giả định gọi `startHandshake()` nhiều lần sẽ có phiên mới.** Hành vi lần gọi tiếp theo phụ thuộc TLS version/provider và có thể bị từ chối, không làm gì hoặc thực hiện một cơ chế khác. Nếu cần thiết lập một kết nối mới/identity mới, hãy thiết kế **kết nối TLS mới** và quy trình xác thực đúng.

`SSLSession.invalidate()` không chuyển một socket TLS đang hoạt động về trạng thái “chưa handshake”; nó liên quan khả năng reuse/cache của session. `getHandshakeSession()==null` cũng không là phép xác định “đã xác thực hay chưa”.

### TLS 1.3 early data và resumption

**0-RTT early data** trong TLS 1.3 có các điều kiện và nguy cơ replay riêng. Chương trình `SSLSocket` ở đây **không sử dụng 0-RTT**; không nhầm `OutputStream.write` trước một lời gọi `startHandshake()` với gửi 0-RTT. Với JSSE, gọi I/O có thể tự kích hoạt handshake thông thường trước khi truyền application data.

Không lấy `SSLSession` object làm login session nghiệp vụ. Authorization và chống replay ở ứng dụng cần token/role/nonce/sequence theo giao thức; không thể thay bằng số lần handshake callback đã bắn.

## 9. `HandshakeCompletedListener` có dùng để cấp quyền không?

**Không.** Listener chỉ thông báo về sự kiện handshake hoàn tất cho socket. Ví dụ tối giản (snippet):

```java
CountDownLatch latch = new CountDownLatch(1);
socket.addHandshakeCompletedListener(event -> latch.countDown());
socket.startHandshake();
```

Đây là snippet cần `socket` hợp lệ và `java.util.concurrent.CountDownLatch`; xử lý exceptions và thời hạn chờ trong code thật. **Không** giả định callback chạy đồng bộ trước dòng ngay sau `startHandshake()`, và đừng thực hiện network I/O, DB query hoặc blocking dài trong callback vì sẽ làm phức tạp concurrency/lifecycle.

Nếu dùng listener cho metrics, gắn **connection ID**, protocol/cipher và thời gian sự kiện. Với mTLS, `HandshakeCompletedEvent.getPeerCertificates()` có thể cung cấp peer chain khi được xác thực nhưng vẫn phải map identity tới authorization. Trong TLS server thông thường **không có client certificate**, vì vậy listener không phải bằng chứng user đã đăng nhập.

## 10. Khi handshake thất bại, exception xuất hiện ở đâu?

| Tình huống | Lớp lỗi/cách quan sát thường gặp | Điều tra |
| --- | --- | --- |
| Certificate server không nối tới trusted root | `SSLHandshakeException`, có thể có `CertPathValidatorException`/builder cause | Truststore, issuer, intermediates, key policy |
| Kết nối tới IP nhưng certificate chỉ có DNS SAN khác | `SSLHandshakeException`/certificate mismatch | Cấp đúng SAN, kiểm tra endpoint identity |
| Server yêu cầu client cert nhưng client không có | TLS alert/`SSLHandshakeException` ở một hoặc hai phía | `needClientAuth`, client key manager/cert, trust manager server |
| Không có TLS version/cipher suite chung | TLS negotiation failure | Allowlist versions/cipher, JDK/provider/policy |
| Client implicit và gửi request lần đầu | Exception TLS có thể phát sinh **tại `write` hoặc `read`** | Đừng chỉ catch quanh `startHandshake` |
| Server implicit chờ read | Handshake error có thể phát sinh **tại `read`** | Peer certificate nếu mTLS, protocol/trust, slow client |
| Peer không nói gì hoặc kết nối bị treo | `SocketTimeoutException`/timeout kết nối hoặc EOF tùy giai đoạn | Timeout/deadlines, firewall, load và resource quota |
| `getPeerCertificates` khi peer không được xác thực bằng cert | `SSLPeerUnverifiedException` | Cấu hình mTLS và vai trò TLS đúng |
| Handshake đã thành công, I/O về sau lỗi | `SSLException` hoặc IO exception khác | Không mặc định đó là lỗi initial TLS handshake |

`SSLHandshakeException` kế thừa `SSLException`. Lỗi TLS handshake có thể bị bọc trong error của thư viện HTTP/JDBC/network; xem `getCause()` một cách có kiểm soát. **Không tự sửa bằng cách đổi trust manager thành trust-all** hoặc tự import một root certificate chưa xác thực vào global `cacerts`.

**Cũng không được suy rằng mọi `SSLException` đều là lỗi certificate**. Có thể là TLS protocol alert, close/truncation, network, unsupported cipher hoặc lỗi tầng record sau khi kết nối đã established.

### Đọc debug theo ngữ cảnh

Có thể bật JSSE debug tạm ở môi trường test:

```powershell
java -Djavax.net.debug=ssl,handshake,trustmanager -cp . HandshakeTimingDemo
```

Output debug không cố định và có thể dài; logging có thể chứa certificate/metadata/chi tiết giao thức nên chỉ sử dụng theo quyền, không bật tràn lan trên production. Các thông tin cần ghi: connection ID, TCP connect/accept duration, TLS handshake duration, negotiated version/cipher, truststore version, expected identity, exception category và correlation ID — không ghi private keys, keystore password hay payload nhạy cảm.

## 11. Timeout, deadlock, I/O framing và quản lý tài nguyên

### Timeout handshake không bằng timeout TCP connect

`setSoTimeout(...)` đặt thời hạn cho **blocking read** trên socket và thường ảnh hưởng việc đọc TLS record trong handshake; nó không thay cho **TCP connect timeout** và không đảm bảo deadline tổng của request.

Ví dụ snippet khi cần connect deadline:

```java
Socket raw = new Socket();
raw.connect(new InetSocketAddress(host, port), 3_000);
SSLSocket tls = (SSLSocket)
        factory.createSocket(raw, host, port, true);
tls.setSoTimeout(10_000);
// Cấu hình SSLParameters/trust/endpoint identity trước khi handshake.
tls.startHandshake();
```

Đây là snippet, cần import `java.net.Socket`/`InetSocketAddress`, `factory/host/port` và quản lý đóng socket. `autoClose=true` khiến wrapper đóng raw socket theo contract khi TLS socket đóng. Production còn cần overall deadline, connection limits, cancellation và handling rõ cho timeout/partial I/O.

### Đừng để hai phía cùng chờ byte ứng dụng

Hai bên cùng `read()` trước khi bên nào gửi application data có thể deadlock **dù TLS đã được thiết lập**:

```text
CLIENT                         SERVER
read()  -- waiting             read() -- waiting
   (không ai gửi dữ liệu ứng dụng trước)
```

Với giao thức request/response, quy định rõ **bên nào gửi trước**, framing, timeout và cách xử lý EOF. Demo chọn client gửi `42`, server đọc rồi đáp `43`, tránh waiting cycle.

### TLS là byte stream, không là message queue

Một `read` không được giả định trả đúng toàn bộ application message. Với message dài, cần xử lý partial reads, length prefix hoặc delimiter, giới hạn size, timeout, checksums nếu protocol yêu cầu và handling đóng kết nối. Không trộn raw `Socket.getInputStream` với stream của `SSLSocket` khi đã bọc TLS lên socket đó — raw reads có thể ăn vào byte handshake/encrypted records.

## 12. ALPN, SNI và client authentication

**SNI** giúp server chọn virtual host/certificate tương ứng DNS name mà client yêu cầu. Có thể cấu hình bằng `SSLParameters.setServerNames(...)` trước handshake, khi cần. SNI không chứng minh certificate đáng tin; client phải tiếp tục xác thực trust chain và SAN/hostname. Demo IP loopback không dựa vào SNI.

**ALPN** thương lượng **tên giao thức ứng dụng** như `h2` hay `http/1.1` trong TLS handshake. `SSLParameters.setApplicationProtocols(...)` đặt danh sách trước handshake và `SSLSocket.getApplicationProtocol()` đọc kết quả sau đó. Chọn `h2` không tự triển khai HTTP/2; ứng dụng cần stack framing/parser thực. Demo `42/43` không đặt ALPN vì không có HTTP.

**mTLS** yêu cầu server xác thực certificate phía client và ánh xạ certificate identity tới service/role/tenant. Bật `setNeedClientAuth(true)` trước handshake và cấu hình đầy đủ trust manager, CA/client cert. `setWantClientAuth(true)` chỉ yêu cầu tùy chọn: một client không có certificate có thể vẫn có kết nối TLS thành công. Đừng coi tất cả client của server đều authenticated chỉ vì server sử dụng `SSLServerSocket`.

## 13. Checklist triển khai thực tế

**Trước handshake:**

- [ ] Server có private key và certificate chain thích hợp qua `KeyManagerFactory`; client có trust manager với trust anchors đáng tin.
- [ ] Client bật **endpoint identification** đúng hostname/IP; certificate có SAN phù hợp.
- [ ] Protocol/cipher allowlist được đặt trước khi socket bắt đầu I/O/TLS; tránh cấu hình quá muộn.
- [ ] Role client/server và mTLS/SNI/ALPN (nếu cần) được chuẩn bị từ trước.
- [ ] Định nghĩa handshake timeouts, connect/read timeouts, deadline tổng và giới hạn connection/CPU.

**Tại handshake và I/O:**

- [ ] Biết code chọn explicit hay implicit; `getSession()` không được sử dụng như probe trạng thái không có side effect.
- [ ] Nếu explicit: chỉ xử lý data sau `startHandshake()` thành công và policy checks.
- [ ] Nếu implicit: `write/read` đầu tiên có thể ném lỗi handshake; không nuốt exception.
- [ ] Sau handshake, kiểm tra negotiated `SSLSession.getProtocol/getCipherSuite` và role/identity theo policy.
- [ ] `HandshakeCompletedListener` chỉ dùng để quan sát, không làm request handler hoặc quyết định quyền.
- [ ] Quản lý EOF, close, record/stream boundaries, backpressure và concurrency đúng.

**Vận hành và bảo mật:**

- [ ] Không dùng trust-all, không bỏ qua hostname verification, không nhập root chưa đáng tin vào truststore.
- [ ] Certificate rotation/renewal, truststore updates và root revocation có quy trình.
- [ ] Nếu mTLS, map client certificate identity tới authorization; không chỉ dựa vào sự tồn tại của certificate.
- [ ] Không giả định TLS 1.3 cho phép renegotiation cổ điển hoặc rằng `startHandshake()` gọi lặp sẽ re-authenticate.
- [ ] Có test valid, untrusted server cert, SAN mismatch, protocol mismatch, mTLS thiếu client cert, slow peer và timeout.
- [ ] Log có correlation/metrics theo quyền; không ghi private key, password, session secrets hay thông tin nhạy cảm.

## 14. Bài tập

1. Trên `HandshakeTimingDemo`, chỉ ra dòng nào tạo **listener**, dòng nào `accept()` và dòng nào kích hoạt explicit TLS handshake. `accept()` có đảm bảo client certificate đã được xác thực không?
2. Đổi `explicit=true` sang `false`. Vì sao app byte `42` không được gửi “trần” dù không có `startHandshake()` trong branch này?
3. Tạo truststore thứ hai **không chứa** certificate server. Chạy hai mode và ghi vị trí/cause của exception. Vì sao ở implicit lỗi có thể xuất hiện từ `OutputStream.write`?
4. Cấp một certificate server chỉ có `DNS:localhost` và bỏ `IP:127.0.0.1`, giữ client kết nối `127.0.0.1` với endpoint identification `HTTPS`. Dự đoán kết quả; đề xuất cách sửa đúng không phải trust-all.
5. Di chuyển `socket.getSession()` **lên trước** `setEndpointIdentificationAlgorithm("HTTPS")`. Phân tích vì sao gọi getter này để “xem session” có thể gây handshake quá sớm.
6. In negotiated `getCipherSuite()`/`getProtocol()` sau TLS. Tại sao giá trị này khác bản chất với `getEnabledProtocols()` trước handshake?
7. Bật `SSLServerSocket.setNeedClientAuth(true)` mà không thêm client cert/private key cho client trong lab. Ghi kết quả; cấu hình nào phải bổ sung để có mTLS thật?
8. Cho client và server một danh sách TLS protocol không giao nhau. Khi explicit/implicit, exception phát sinh ở thao tác nào?
9. Đổi client và server đều gọi `read()` chờ application byte trước khi write. Giải thích deadlock tầng ứng dụng và thiết kế framing/timeout đúng.
10. Thiết kế vòng đời TLS connection của server production: accept queue, handshake timeout, quota, listener callback, peer identity, authorization, request parsing và close.
11. So sánh initial handshake, TLS session resumption và renegotiation. Vì sao không thể dùng `startHandshake()` lặp lại trên TLS 1.3 như API xác thực user lần nữa?
12. Ghi một checklist quan sát `SSLHandshakeException` có thể bị bọc từ `getCause()` trong ứng dụng HTTP; cách phân biệt truststore, SAN mismatch và protocol version mà không hạ bảo mật.

## Tài liệu tham khảo

- [Java SE 17 — SSLSocket](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSocket.html): `startHandshake`, `getSession`, `getHandshakeSession` và implicit handshake.
- [Java SE 17 — SSLServerSocket](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLServerSocket.html): listener, protocols và client authentication.
- [Java SE 17 — SSLContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html): SSLContext init, key/trust managers và socket factories.
- [Java SE 17 — SSLParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): endpoint identification, SNI, ALPN và SSL/TLS parameters.
- [Java SE 17 — SSLSession](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSession.html): negotiated protocol/cipher và peer.
- [Java SE 17 — HandshakeCompletedListener](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/HandshakeCompletedListener.html): callback khi TLS handshake hoàn tất.
- [Java SE 17 — SSLSocketFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSocketFactory.html) và [SSLServerSocketFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLServerSocketFactory.html): tạo client/server sockets.
- [Java Secure Socket Extension (JSSE) Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): khởi tạo JSSE, debugging, TLS socket coding và trust.
- [Java SE 17 — keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): thao tác với keystore và truststore.
- [RFC 8446 — TLS 1.3](https://www.rfc-editor.org/rfc/rfc8446): initial handshake, session resumption, 0-RTT, key update và bảo mật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jsse-005">16.5 Yêu cầu hoặc đề nghị chứng thư phía khách bằng needClientAuth và wantClientAuth</a>

<details>
<summary>Xem chi tiết</summary>

# 16.5. Yêu cầu hoặc đề nghị chứng thư phía khách bằng needClientAuth và wantClientAuth

## 1. Bài toán: server TLS có bắt buộc khách hàng trình chứng thư không?

Trong HTTPS/TLS thông thường, **máy chủ (server)** trình chứng thư để phía **khách (client)** xác minh danh tính server. Client thường **không cần** chứng thư X.509; người dùng có thể đăng nhập ở lớp ứng dụng bằng session, OAuth 2.0, token hoặc cơ chế khác.

Trong **mutual TLS (mTLS)**, server còn **yêu cầu hoặc đề nghị client cung cấp chứng thư**, và dùng cơ chế tin cậy tương ứng để xác minh client certificate. Java Secure Socket Extension (**JSSE**) cho phép cấu hình hai chế độ quan trọng:

- `setNeedClientAuth(true)`: **bắt buộc** client authentication trong TLS handshake. Không có chứng thư/thông tin xác thực client phù hợp thì server **không chấp nhận kết nối TLS**.
- `setWantClientAuth(true)`: server **đề nghị** client authentication. Nếu client không cung cấp chứng thư, handshake **vẫn có thể thành công**; nếu client trình chứng thư, chứng thư đó vẫn phải được kiểm tra theo trust policy.

**Cả hai là chính sách ở phía server**, không phải nút để client “bắt” server đưa chứng thư. Và **cả hai không tự cấp quyền nghiệp vụ** cho client: một certificate đáng tin về mặt mật mã chỉ tạo đầu vào để ứng dụng ánh xạ identity, role, tenant và scope.

Bài này tập trung vào vòng đời các cờ `needClientAuth` và `wantClientAuth` trong JSSE, trường hợp không có/có/sai certificate, vai trò `KeyManager`–`TrustManager`, việc kiểm tra peer bằng `SSLSession`, các khác biệt có thể quan sát ở TLS 1.3, cùng một chương trình **Java 17+** có thể chạy với bốn phép thử TLS thật trên loopback.

### Kết quả mong đợi sau bài

1. Phân biệt **required**, **requested/optional** và **not requested**.
2. Biết cấu hình trên `SSLServerSocket`, `SSLSocket` hoặc `SSLParameters` trước khi handshake.
3. Hiểu vì sao `wantClientAuth` **không** có nghĩa “chấp nhận certificate nào cũng được”.
4. Phân biệt **xác thực client TLS** với **authorization**.
5. Tạo keystore/truststore thử nghiệm bằng `keytool`, kiểm thử bốn tình huống trên JDK 17+.
6. Biết cách đọc `getPeerCertificates()`, xử lý client không xác thực và tránh những cấu hình bỏ qua trust validation.

## 2. Phân biệt server authentication và client authentication

### 2.1. HTTPS một chiều

Mô hình thường gặp:

```text
HTTPS CLIENT                                   TLS SERVER
    |                                            |
    | ----- ClientHello -----------------------> |
    | <----- Server cert / handshake ----------- |
    |                                            |
    | Client xác minh chuỗi CA, hostname/IP,     |
    | thời hạn, EKU và chính sách TLS            |
    |                                            |
    +---- Kênh TLS được thiết lập ---------------+
    |                                            |
    | POST /login, bearer token, session... ---> |
```

Server identity được xác minh qua chứng thư của **server**. Việc client có tài khoản hoặc token đăng nhập ở lớp ứng dụng là chuyện khác.

### 2.2. TLS hai chiều (mutual TLS)

Khi server có yêu cầu nhận dạng client bằng certificate:

```text
TLS CLIENT                                      TLS SERVER
    |                                                |
    | --------- ClientHello ------------------------> |
    | <-------- Server Certificate / handshake ----- |
    | <-------- CertificateRequest (nếu cấu hình) -- |
    |                                                |
    | ---- Client Certificate (nếu có/phù hợp) -----> |
    | ---- Bằng chứng sở hữu private key ----------> |
    |                                                |
    |                                  TrustManager xác minh
    |                                  cert chain / policy
    |                                                |
    +----- TLS handshake thành công hoặc bị từ chối --+
```

Đây là sơ đồ **logic**. Trình tự chính xác của thông điệp TLS khác nhau giữa **TLS 1.2 và TLS 1.3**. Trong TLS 1.3, client có thể gửi thông điệp `Certificate` rỗng khi không có certificate phù hợp; phía server sẽ áp dụng chính sách required/optional. Không nên suy từ thứ tự hình vẽ rằng mọi phiên TLS gửi certificate như nhau.

### 2.3. Client certificate là gì?

Client certificate là chứng thư X.509 có public key; client phải có **private key tương ứng** để tham gia client-authentication handshake. Một file `client.cer` chỉ chứa **public certificate** không đủ làm identity để client trình chứng cứ sở hữu khóa riêng.

Một cấu hình mTLS bình thường cần:

- **Server:** private key + certificate chain của **server**, và **TrustManager** biết tin loại client certificates được cho phép.
- **Client:** **TrustManager** xác minh server certificate; nếu được yêu cầu/đề nghị mTLS, **KeyManager** có private key và client certificate chain tương ứng.
- **Cả hai phía:** cấu hình thuật toán/TLS version, trust anchors, policy sử dụng chứng thư và danh tính endpoint hợp lệ.

## 3. Ba chế độ client authentication

Với accepted `SSLSocket` hoạt động ở **server mode**, có ba trạng thái logic:

| Chế độ | Cấu hình | Khi client **không có** chứng thư | Khi client có chứng thư **được trust và phù hợp** | Khi client **trình** chứng thư nhưng không được trust |
| --- | --- | --- | --- | --- |
| **Không yêu cầu** | `setNeedClientAuth(false)` hoặc `setWantClientAuth(false)` theo trạng thái | Có thể handshake thành công | Server thường không chủ động yêu cầu client cert ở handshake này | Không nên suy diễn: nếu không yêu cầu, client có thể không gửi cert |
| **Đề nghị / optional** | `setWantClientAuth(true)` | **Handshake có thể thành công** nhưng **không có identity mTLS** | Handshake thành công; server có peer certificate đã xác minh | Nếu certificate thật sự được gửi và validation fail, **handshake bị từ chối** |
| **Bắt buộc / required** | `setNeedClientAuth(true)` | **Handshake bị từ chối** | Handshake thành công; client-authentication TLS được thực hiện | **Handshake bị từ chối** |

Các kết quả “thành công” trong bảng còn phụ thuộc các điều kiện TLS khác: server certificate có tin cậy không, hostname có khớp không, version/cipher suite được phép không, và chứng thư client có phù hợp certificate policy không.

**Từ khóa cần nhớ:**

- `need` = **MUST**: không có chứng thư client phù hợp thì không cho handshake server-side hoàn tất.
- `want` = **ASK / OPTIONAL**: không có chứng thư client vẫn có thể cho TLS thành công.
- `want` **không** = “chứng thư sai vẫn chấp nhận”. Nếu đã gửi chứng thư và `TrustManager` từ chối, kết nối phải thất bại theo chính sách an toàn.
- Cờ client-auth không liên quan trực tiếp tới việc client xác minh **server hostname**; đừng bỏ quên kiểm tra HTTPS endpoint chỉ vì đang bật mTLS.

### 3.1. Optional client authentication có điểm nhạy cảm

Nếu server đặt `wantClientAuth(true)` và client không trình chứng thư, ứng dụng có thể vẫn nhận được một kết nối TLS hợp lệ về **mã hóa kênh**, nhưng **không có danh tính được xác thực bằng client certificate**.

Ví dụ hệ thống cho phép cả người dùng thông thường và robot có chứng thư:

```text
TCP/TLS accepted with wantClientAuth=true
                  |
                  v
Server kiểm tra: peer có client certificate đã xác minh?
        | không                              | có
        v                                    v
   Nhánh anonymous/                       Ánh xạ identity
   xác thực khác                           từ cert đáng tin
        |                                    |
   Chỉ cho API public                Kiểm role, tenant, scope
   hoặc yêu cầu login                trước API được bảo vệ
```

Nếu một API quản trị **bắt buộc mTLS**, chỉ đặt `wantClientAuth(true)` là **không đủ**. Phải từ chối phiên không có certificate ngay ở TLS bằng `needClientAuth(true)` (trên listener tương ứng), hoặc có rào chắn bắt buộc và được kiểm thử ở lớp ứng dụng/proxy trước khi thực thi, tùy kiến trúc. Không gắn nhãn “mTLS required” chỉ vì listener có `wantClientAuth`.

### 3.2. Required client authentication cũng chưa phải authorization

`needClientAuth(true)` chứng minh server đã yêu cầu một chứng thư **được trust manager chấp nhận** trong handshake; nó **chưa chứng minh client đó được phép gọi tất cả chức năng**.

Nếu truststore chứa một CA nội bộ cấp chứng thư cho nhiều ứng dụng hoặc nhiều tenant, một certificate hợp lệ của ứng dụng A có thể vẫn thuộc trust domain đó dù đang truy cập API của ứng dụng B.

Sau handshake cần ánh xạ certificate identity (theo policy DNS SAN, URI SAN, subject, issuer, public-key binding...), kiểm tra mục đích clientAuth/EKU, thời hạn, trạng thái thu hồi và cấp quyền theo **danh tính thật sự được hệ thống tin**. Không sử dụng `CN` hoặc `SAN` thô mà không định nghĩa cách xác thực/định danh.

## 4. JSSE dùng API nào để đặt needClientAuth/wantClientAuth?

### 4.1. SSLServerSocket: áp dụng cho kết nối mới được accept

Ví dụ **đoạn mã minh họa**:

```java
SSLServerSocket listener = (SSLServerSocket)
        serverContext.getServerSocketFactory().createServerSocket(8443);

listener.setNeedClientAuth(true); // Client cert bắt buộc.
System.out.println(listener.getNeedClientAuth()); // true
System.out.println(listener.getWantClientAuth()); // false
```

`SSLServerSocket` là **listening socket**; khi `accept()` tạo `SSLSocket` ở server mode, socket mới **kế thừa cấu hình ban đầu** của listener. Khi server gọi `setNeedClientAuth(true)`, client không cung cấp thông tin xác thực sẽ làm TLS negotiation dừng và kết nối bị ngắt theo Javadoc Java 17.

Khi dùng:

```java
listener.setWantClientAuth(true);
```

server chỉ **đề nghị** client authentication và cho phép client không trình chứng thư. Lời gọi đó **ghi đè** cấu hình `setNeedClientAuth` trước đó trên listener.

**Cần tránh:** thay cờ trên listening socket rồi mong những kết nối **đã accept/đã handshake** tự nhận cấu hình mới. Thay listener chỉ ảnh hưởng cách khởi tạo các accepted sockets tiếp theo; với accepted socket, cấu hình cần được đặt **trước initial handshake**. Đối với những session đã thiết lập, không nên trông chờ đổi cờ có thể bắt client xuất trình chứng thư giữa phiên.

### 4.2. SSLSocket ở phía server

Khi cần chính sách riêng từng kết nối:

```java
try (SSLSocket peer = (SSLSocket) listener.accept()) {
    peer.setNeedClientAuth(true); // Trước khi bắt đầu handshake.
    peer.startHandshake();
    // Peer auth/TLS handshake đã được kiểm tra theo cấu hình.
}
```

Đoạn mã phụ thuộc `listener` và `serverContext` đã được tạo; **không phải chương trình độc lập**. Phương thức `SSLSocket.setNeedClientAuth` hoặc `setWantClientAuth` chỉ hữu ích khi socket hoạt động ở **server mode**.

Phân biệt:

- `SSLServerSocket.setNeedClientAuth`: cấu hình **ban đầu cho accepted sockets mới**.
- `SSLSocket.setNeedClientAuth`: cấu hình **accepted socket cụ thể** trước handshake.
- `SSLParameters.setNeedClientAuth`: lưu chính sách để áp vào socket/engine, thường bằng `setSSLParameters(...)`.

Khi phát triển server có nhiều nhóm endpoint, hãy cấu hình listener/port hoặc policy chuyên biệt đã được kiểm soát, không dựa vào việc thay cờ theo dữ liệu client chưa xác thực.

### 4.3. SSLParameters: cần/want là trạng thái loại trừ nhau

Ví dụ:

```java
SSLParameters params = socket.getSSLParameters();
params.setNeedClientAuth(true);
System.out.println(params.getNeedClientAuth()); // true
System.out.println(params.getWantClientAuth()); // false

params.setWantClientAuth(true);
System.out.println(params.getNeedClientAuth()); // false
System.out.println(params.getWantClientAuth()); // true

socket.setSSLParameters(params); // Áp vào socket trước handshake.
```

**Quy tắc theo Javadoc Java 17:**

- `SSLParameters.setNeedClientAuth(...)` **xóa cờ wantClientAuth**.
- `SSLParameters.setWantClientAuth(...)` **xóa cờ needClientAuth**.
- Hai cờ **không cộng dồn**. Nếu vô tình gọi cả hai với `true`, lời gọi sau thay đổi chính sách.
- `SSLServerSocket` và `SSLSocket` cũng có cơ chế override các setting liên quan: gọi `setWantClientAuth(true)` sau `setNeedClientAuth(true)` khiến trạng thái cuối trở thành **want** chứ không còn **need**.

Nếu code cấu hình phân tán trong nhiều lớp (framework, adapter, method cấu hình bảo mật), cần đọc **trạng thái cuối cùng sau khi mọi setter đã chạy** bằng `getNeedClientAuth()` và `getWantClientAuth()`. Một thay đổi cấu hình nhỏ có thể vô tình hạ yêu cầu mTLS từ **required** xuống **optional**.

### 4.4. SSLEngine

Với NIO/Netty hoặc một transport tự quản lý bằng `SSLEngine`, có thể dùng:

```java
SSLEngine engine = sslContext.createSSLEngine();
engine.setUseClientMode(false); // Engine ở phía TLS server.
SSLParameters params = engine.getSSLParameters();
params.setNeedClientAuth(true);
engine.setSSLParameters(params);
```

Đây là đoạn mã minh họa. Phần tạo `SSLEngine`, cấu hình TLS/hostname, buffer, tasks và bắt tay non-blocking cần được thực hiện đầy đủ theo JSSE/transport library. Điều cốt lõi vẫn là **đặt chế độ server và client-auth policy trước handshake**. Nếu dùng framework, phải biết option `clientAuth=REQUIRE/OPTIONAL/NONE` của framework được ánh xạ vào JSSE ở đâu.

## 5. Chứng thư được chấp nhận bằng KeyManager và TrustManager như thế nào?

### 5.1. Hai vai trò dễ bị đảo ngược

Trong `SSLContext.init(KeyManager[], TrustManager[], SecureRandom)`:

- **`KeyManager`** chọn local private key + certificate chain để **chính bên này trình ra** khi giao thức cần.
- **`TrustManager`** xác minh certificate của **peer** (bên kia) khi certificate được trình ra.

Với server mTLS:

| Thành phần server | Cần có | Chức năng |
| --- | --- | --- |
| `KeyManagerFactory` khởi tạo từ `server.p12` | Server private key + certificate chain | Đưa danh tính server cho client; chứng minh sở hữu private key server. |
| `TrustManagerFactory` khởi tạo từ `server-trust.p12` | Các CA/client cert được server tin theo policy | Xác minh chứng thư được client trình ra. |
| `setNeedClientAuth(true)` hoặc `setWantClientAuth(true)` | Cờ policy của server-side socket | Quy định có bắt buộc client certificate trong handshake không. |

Với client:

| Thành phần client | Cần có | Chức năng |
| --- | --- | --- |
| `TrustManagerFactory` từ `client-trust.p12` | Certificate/CA của server được client tin | Xác minh server; **còn phải kiểm hostname/IP** nếu giao thức cần. |
| `KeyManagerFactory` từ `client.p12` | Private key + certificate chain của client (khi có) | Có thể chọn và gửi chứng thư client khi server đề nghị/yêu cầu. |
| `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` | Reference identity từ endpoint client định gọi | Kiểm tên server khi thực hiện HTTPS/TLS theo JSSE. |

**Lỗi phổ biến:** chỉ tạo `server.p12` cho server và bật `needClientAuth(true)`, nhưng không cấu hình truststore để server **tin issuer của client certificate**. Client có chứng thư vẫn có thể bị từ chối.

Ngược lại, client có `client.p12` nhưng **không có** truststore tin server certificate thì client sẽ từ chối **server authentication**; lỗi này độc lập với cấu hình want/need trên server.

### 5.2. Client phải có private key phù hợp, không chỉ certificate công khai

`KeyManagerFactory` chọn certificate chain/private key theo các thuật toán và danh sách acceptable issuers mà server gửi. Nếu không có alias/chứng thư phù hợp, client có thể **không gửi certificate**, dù keystore có một entry khác.

Với `wantClientAuth`, tình huống này có thể khiến handshake **thành công mà không có peer cert** — không có bằng chứng client đã xác thực bằng mTLS. Với `needClientAuth`, server sẽ **từ chối**.

Điều đó khác với “client chắc chắn đã gửi một certificate không được tin cậy”: nếu certificate **được gửi** nhưng trust validation fail, handshake không được tự động cho qua chỉ vì want=optional.

### 5.3. Trust không tự đồng nghĩa “có quyền”

`TrustManagerFactory` theo PKIX/truststore có thể công nhận nhiều client certificates do cùng CA phát hành. Để cho phép một service cụ thể:

- Chọn **identity attribute** và chuẩn ánh xạ ổn định, ví dụ URI SAN của workload theo profile service mesh, hoặc public-key identity được pin trong registry.
- Xác minh issuer/trust anchor và các thuộc tính liên quan (EKU `clientAuth`, key usage, validity, revocation nếu policy áp dụng).
- Ánh xạ certificate → principal ứng dụng → role/tenant/scope.
- Không chỉ so chuỗi `CN` như tên tài khoản tùy ý hoặc cho mọi chứng thư của CA nội bộ quyền admin.
- Cân nhắc certificate rotation: không gắn authorization vào serial number/subject dễ thay đổi nếu kiến trúc cần danh tính ổn định.

### 5.4. Bắt buộc client certificate vẫn không thay thế xác minh server

Client cần xác thực server trước khi gửi dữ liệu nhạy cảm hoặc credential; mTLS **không** cho phép client bỏ hostname verification. Nếu client kết nối theo URL `https://api.example.test`, server certificate phải có SAN phù hợp cho `api.example.test` và PKIX trust path phải được kiểm tra theo policy.

Trong lab dưới đây kết nối theo IP `127.0.0.1`, do đó chứng thư server thử nghiệm chứa **SAN:IP Address 127.0.0.1**. Client gọi `setEndpointIdentificationAlgorithm("HTTPS")` để minh họa kiểm tra endpoint ở lớp JSSE; không có trust-all manager.

## 6. Java 17+ lab: bốn TLS 1.3 handshakes thực sự

Ví dụ này dùng các tiện ích có sẵn trong JDK (`keytool`, `javac`, `java`) và một file Java duy nhất. Chúng ta tạo **bốn PKCS#12 test stores**:

```text
server.p12         -- private key + cert của localhost
client.p12         -- private key + cert của demo-client
server-trust.p12   -- server tin client certificate thử nghiệm
client-trust.p12   -- client tin server certificate thử nghiệm

Client không chứng thư:
  SSLContext.init(null, clientTrustManagers, random)

Client có chứng thư:
  SSLContext.init(clientKeyManagers, clientTrustManagers, random)

Server luôn có:
  SSLContext.init(serverKeyManagers, serverTrustManagers, random)
```

Ở đây chứng thư là **self-signed chỉ cho test**, nhưng mỗi bên **chủ động cài chứng thư bên kia vào truststore riêng**. Đây là cách cố tình tạo test anchors của lab, **không** phải mô hình đề xuất cho production. Trong môi trường thật, cần CA/PKI, provisioning và quản lý vòng đời chứng thư phù hợp.

### 6.1. Tạo chứng thư và truststores bằng keytool

Tạo **thư mục lab mới** và chạy các lệnh sau tại thư mục đó. Các command `keytool` bên dưới viết trên từng dòng để dùng được cả trong shell thông thường lẫn PowerShell (khi đã có JDK trên PATH):

```bash
mkdir client-auth-lab
cd client-auth-lab

keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 365 -dname "CN=localhost, O=TLS Demo, C=US" -ext "SAN=dns:localhost,ip:127.0.0.1" -ext "EKU=serverAuth" -keystore server.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt

keytool -genkeypair -alias client -keyalg RSA -keysize 2048 -validity 365 -dname "CN=demo-client, O=TLS Demo, C=US" -ext "EKU=clientAuth" -keystore client.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt

keytool -exportcert -alias server -keystore server.p12 -storepass changeit -file server.cer

keytool -exportcert -alias client -keystore client.p12 -storepass changeit -file client.cer

keytool -importcert -alias server -file server.cer -keystore client-trust.p12 -storetype PKCS12 -storepass changeit -noprompt

keytool -importcert -alias client -file client.cer -keystore server-trust.p12 -storetype PKCS12 -storepass changeit -noprompt
```

**Cảnh báo:** password `changeit`, self-signed certificates và khóa RSA thử nghiệm trong các lệnh này **chỉ để lab**. Không dùng cho môi trường thật, không commit `*.p12`, `*.jks`, `*.key` vào repository, không gửi private key vào chat/ticket, không dùng test CA làm trust anchor production. Sau lab, xóa thư mục thử nghiệm theo chính sách. Khóa test có thời hạn **365 ngày kể từ lúc sinh**, nên khi làm bài sau đó hãy sinh lại thay vì tái sử dụng keystore đã hết hạn.

Các file `server.cer` và `client.cer` chỉ chứa **chứng thư công khai**, không có private key. Chúng được import vào truststores để phục vụ trust decision; khi client xác thực mình, private key nằm ở `client.p12`.

### 6.2. Chương trình ClientAuthDemo.java

Tạo file `ClientAuthDemo.java` ngay trong thư mục có bốn `.p12`:

```java
import java.io.IOException;
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
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

public final class ClientAuthDemo {
    private static final char[] PASSWORD = "changeit".toCharArray(); // Test only.
    private record Outcome(boolean accepted, boolean peerCertificate) {}

    private static KeyStore load(Path file) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(file)) {
            store.load(in, PASSWORD);
        }
        return store;
    }

    private static SSLContext context(String identityFile, String trustFile)
            throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(load(Path.of(trustFile)));

        KeyManagerFactory kmf = null;
        if (identityFile != null) {
            kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(load(Path.of(identityFile)), PASSWORD);
        }

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf == null ? null : kmf.getKeyManagers(),
                tmf.getTrustManagers(), new SecureRandom());
        return context;
    }

    private static Outcome run(SSLContext serverContext,
                               SSLContext clientContext,
                               boolean need) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SSLServerSocket listener = (SSLServerSocket)
                serverContext.getServerSocketFactory().createServerSocket(
                        0, 5, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(5000);
            listener.setEnabledProtocols(new String[]{"TLSv1.3"});
            if (need) {
                listener.setNeedClientAuth(true);
            } else {
                listener.setWantClientAuth(true);
            }

            Future<Outcome> serverResult = executor.submit(() -> {
                try (SSLSocket peer = (SSLSocket) listener.accept()) {
                    peer.setSoTimeout(5000);
                    peer.startHandshake();
                    boolean hasCertificate;
                    try {
                        peer.getSession().getPeerCertificates();
                        hasCertificate = true;
                    } catch (SSLPeerUnverifiedException noCertificate) {
                        hasCertificate = false;
                    }
                    return new Outcome(true, hasCertificate);
                } catch (IOException handshakeOrSocketFailure) {
                    return new Outcome(false, false);
                }
            });

            try (SSLSocket client = (SSLSocket)
                    clientContext.getSocketFactory().createSocket(
                            "127.0.0.1", listener.getLocalPort())) {
                client.setSoTimeout(5000);
                client.setEnabledProtocols(new String[]{"TLSv1.3"});
                SSLParameters parameters = client.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                client.setSSLParameters(parameters);
                try {
                    client.startHandshake();
                } catch (IOException expectedForRejectedHandshake) {
                    // We report the server's result, not an early TLS 1.3 client result.
                }
                return serverResult.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            SSLContext server = context("server.p12", "server-trust.p12");
            SSLContext withoutCertificate = context(null, "client-trust.p12");
            SSLContext withCertificate = context("client.p12", "client-trust.p12");

            Outcome optionalAbsent = run(server, withoutCertificate, false);
            Outcome requiredAbsent = run(server, withoutCertificate, true);
            Outcome optionalPresent = run(server, withCertificate, false);
            Outcome requiredPresent = run(server, withCertificate, true);

            System.out.println("want + no cert: accepted=" +
                    optionalAbsent.accepted() + ", authenticated=" +
                    optionalAbsent.peerCertificate());
            System.out.println("need + no cert: accepted=" +
                    requiredAbsent.accepted() + ", authenticated=" +
                    requiredAbsent.peerCertificate());
            System.out.println("want + trusted cert: accepted=" +
                    optionalPresent.accepted() + ", authenticated=" +
                    optionalPresent.peerCertificate());
            System.out.println("need + trusted cert: accepted=" +
                    requiredPresent.accepted() + ", authenticated=" +
                    requiredPresent.peerCertificate());
        } finally {
            Arrays.fill(PASSWORD, '\0');
        }
    }
}
```

### 6.3. Chạy chương trình và stdout

```bash
javac ClientAuthDemo.java
java ClientAuthDemo
```

Sau khi các lệnh `keytool` trên hoàn tất, Java program in chính xác:

```text
want + no cert: accepted=true, authenticated=false
need + no cert: accepted=false, authenticated=false
want + trusted cert: accepted=true, authenticated=true
need + trusted cert: accepted=true, authenticated=true
```

**Giải thích bốn dòng:**

1. `want + no cert: accepted=true, authenticated=false`: server yêu cầu mềm. Client không có `KeyManager` chứa client identity nên không trình chứng thư; TLS server-side handshake hoàn tất; `SSLSession.getPeerCertificates()` ném `SSLPeerUnverifiedException`, được chuyển thành cờ `authenticated=false`.
2. `need + no cert: accepted=false, authenticated=false`: server yêu cầu cứng. Client không có chứng thư phù hợp, nên TLS server-side handshake bị từ chối; không có kết nối client-authentication để xử lý request.
3. `want + trusted cert: accepted=true, authenticated=true`: server chỉ đề nghị, nhưng client có private key và cert được server trust, nên client có thể trình chứng thư và TLS server handshake chấp nhận.
4. `need + trusted cert: accepted=true, authenticated=true`: server bắt buộc chứng thư, client có chứng thư được tin; handshake hoàn tất và server đọc được `peerCertificates` đã qua handshake validation.

Đây là các **kết quả từ server**. Demo có một thread riêng xử lý `listener.accept()` và `peer.startHandshake()`, trong khi main thread tạo client connection. Trong TLS 1.3, một số lỗi ở server có thể xuất hiện **sau khi một phía nghĩ rằng bước startHandshake cục bộ của mình đã hoàn tất**; vì vậy code trả kết quả **sau khi nhận Future của server** thay vì coi riêng return của `client.startHandshake()` là bằng chứng server đã cho phép client.

Cách bắt `IOException` và rút gọn thành boolean trong demo chỉ nhằm có stdout ổn định. **Production không nên bỏ qua nguyên nhân TLS failure**; cần ghi loại lỗi không nhạy cảm và có xử lý fail-closed. Thử nghiệm cũng dùng thời gian chờ 5–10 giây để tránh treo vô hạn trong môi trường lab; đó không phải timeout bắt buộc của JSSE.

### 6.4. Những gì chương trình thực sự chứng minh

- Nó mở **kết nối TCP trên 127.0.0.1**, dùng **SSLServerSocket/SSLSocket thực**, không mô phỏng handshake bằng cờ booleans thuần túy.
- Cả hai phía bật `TLSv1.3`; chương trình sẽ lỗi nếu JDK/provider đang dùng không hỗ trợ.
- Server sử dụng `server.p12` để trình chứng thư cho client; client có truststore riêng.
- Server dùng `server-trust.p12` để xác thực client certificate khi client gửi.
- Client bật `setEndpointIdentificationAlgorithm("HTTPS")` và kết nối theo `127.0.0.1`, khớp SAN IP thử nghiệm.
- Sau handshake, server kiểm tra `getPeerCertificates()` để biết peer có một certificate chain được xác minh trong chính phiên TLS hay không.
- Khi client không có identity, `SSLContext` nhận mảng `KeyManager[]` là `null`: client vẫn **xác minh server** bằng `TrustManager[]`, nhưng không có private key để xác thực client.

### 6.5. Những gì chương trình không chứng minh

- Demo **không phải public PKI** và không có CA certificate chain đúng mô hình production; truststores chứa test leaf certificates được import trực tiếp.
- Không có URL thật, không có DNS/public server, không có request HTTP, không có kiểm tra role/tenant/scope sau TLS.
- Nó không tự chứng minh mọi certificate có cùng CA đều được phép; lab tin đúng một certificate ở mỗi hướng để thử hành vi.
- Không kiểm tra thu hồi CRL/OCSP, HSM, certificate rotation, truststore updates hay phân tách credential production.
- Không kiểm thử tình huống **client đã gửi chứng thư thực sự nhưng chain không được tin**: khi JSSE nhìn danh sách acceptable CAs mà không chọn được alias, client có thể **không gửi** chứng thư ngay từ đầu. Vì vậy cần test riêng loại lỗi “untrusted presented certificate” bằng cấu hình lab thích hợp.
- Không bảo đảm rằng mọi phiên bản JDK/provider báo cùng thông điệp exception; kết quả logic required/optional là đặc điểm API, còn phần message/stack trace có thể khác.

## 7. SSLSession: xác định client đã xác thực bằng chứng thư chưa

Sau khi **server-side TLS handshake thành công**, có thể đọc certificate chain của peer:

```java
SSLSession session = peerSocket.getSession();
try {
    Certificate[] chain = session.getPeerCertificates();
    // Ở server mode: peer chính là client.
    // Còn phải ánh xạ danh tính/authorization theo policy.
} catch (SSLPeerUnverifiedException noClientCertificate) {
    // Có thể xảy ra với wantClientAuth; xử lý như chưa xác thực mTLS.
}
```

Đoạn mã minh họa trên cần imports `javax.net.ssl.SSLSession`, `javax.net.ssl.SSLPeerUnverifiedException` và `java.security.cert.Certificate`, đồng thời `peerSocket` là accepted `SSLSocket` của server đã thực hiện handshake thành công.

**Chú ý:**

- `SSLSession.getPeerCertificates()` có thể ném `SSLPeerUnverifiedException` nếu không có certificate-based peer authentication. Đừng gọi rồi giả định luôn có mảng khác rỗng.
- Ở **server-side socket**, peer certificate là **client certificate**. Ở **client-side socket**, peer certificate là **server certificate**. Đừng nhầm ngữ cảnh.
- Trong một TLS session được xác minh bằng trust manager đúng, chain đã được JSSE xem xét theo trust policy. Nhưng chain hiện diện **không thay thế** ánh xạ danh tính/authorization của ứng dụng.
- `session.getPeerPrincipal()` có thể cho biết principal liên quan; tuy nhiên chỉ `CN` hoặc `X500Principal.toString()` thường không đủ làm chính sách định danh cho mọi mô hình mTLS.
- `getLocalCertificates()` (nếu có) nói về chứng thư **của chính bên cục bộ**, khác với `getPeerCertificates()`. Dùng sai sẽ khiến code tưởng client đã xác thực trong khi mới chỉ thấy cert server.

### 7.1. Kiểm soát cả nhánh anonymous của wantClientAuth

Nếu muốn dùng optional mTLS cho hai nhóm API, phải có một quy tắc như:

```text
if TLS handshake failed:
    drop connection
else if verified client identity exists:
    map certificate -> approved principal -> authorized routes/scopes
else:
    restrict to anonymous/public routes or require other authentication
```

Đây là giả mã, không phải Java executable. Không cho phép logic kiểu `if (TLS session exists) allowAdmin()` vì **TLS session thành công với want/no-cert không có nghĩa mTLS identity tồn tại**.

Nếu chỉ một số endpoint yêu cầu chứng thư, còn phần khác cho anonymous, cân nhắc tách listener/hostname có `needClientAuth` riêng để việc enforce chính sách rõ ràng. Thay cờ sau handshake theo URL path **không làm client tự thực hiện lại bước authentication ban đầu**, đặc biệt ở TLS 1.3.

## 8. Tại sao wantClientAuth không có nghĩa chấp nhận mọi certificate?

Cần tách hai loại thất bại:

### A. Không có client certificate

Client có thể không sở hữu private key/client certificate hoặc KeyManager không tìm được alias phù hợp. Với `wantClientAuth`, TLS có thể thành công và ứng dụng nhận **unauthenticated client connection**.

### B. Certificate được gửi nhưng không qua validation

Khi client thực sự trình một certificate chain, server cần kiểm tra thông qua `TrustManager` được cấu hình đúng; ví dụ:

- Certificate không dẫn tới trust anchor mà server chấp nhận.
- Certificate hết hạn, chưa hiệu lực hoặc bị thu hồi theo policy.
- EKU/KeyUsage không phù hợp `clientAuth`.
- Certificate chain hoặc chữ ký không hợp lệ, thuật toán không được phép.
- Các hạn chế issuer/subject/name constraints hay các chính sách mTLS khác không đạt.

Nếu certificate đã được trình và bước xác minh chứng thư thất bại, server **không được** tiếp tục như client anonymous chỉ vì `wantClientAuth(true)`. Optional nghĩa là **có thể không có chứng thư**, không phải “một chứng thư sai cũng được”.

**Rủi ro khi kiểm thử:** JSSE KeyManager có thể không gửi một cert nếu acceptable issuers list không phù hợp. Khi server muốn cert nhưng client không gửi, optional mode có thể thành công. Kết quả đó **không chứng minh** server chấp nhận một cert không được tin. Muốn test “untrusted certificate was presented”, phải có bằng chứng từ handshake trace hoặc cấu hình KeyManager tùy biến test-only để đảm bảo cert thật sự được gửi.

Không tạo `X509TrustManager` “trust-all” để tránh lỗi client certificate. Việc đó làm mất ý nghĩa của cả need và want: một certificate tự ký từ attacker có thể bị xem là “đã xác thực”.

## 9. Need/want và TLS 1.2 so với TLS 1.3

### 9.1. Chính sách required/optional tồn tại ở cả hai

Ý nghĩa chức năng của `setNeedClientAuth` và `setWantClientAuth` không đổi ở cấp JSSE: server yêu cầu hay đề nghị client certificate. Thay đổi nằm ở **cơ chế và thời điểm thông điệp handshake**, alerts và session/resumption.

Với TLS 1.3, server có thể gửi `CertificateRequest` trong initial handshake. Nếu client không gửi certificate khi server yêu cầu, server sẽ từ chối (ví dụ với cảnh báo `certificate_required` theo TLS 1.3). Một số stack có thể báo exception ở các thời điểm khác nhau giữa client và server. Đừng kiểm thử chỉ bằng việc `client.startHandshake()` có trả về hay không; phải kiểm tra **phía server có thực sự hoàn tất/cho phép phiên**.

### 9.2. Không dựa vào TLS renegotiation để “nâng cấp” optional thành required

Một cách làm cũ từng tồn tại là: bắt đầu TLS không có client cert, rồi đến một HTTP route nhạy cảm mới bật required client auth và cố kích hoạt renegotiation. **Không nên thiết kế như vậy.** TLS 1.3 không có renegotiation theo kiểu TLS 1.2; post-handshake authentication có các điều kiện hỗ trợ riêng và **không tự xuất hiện** chỉ vì Java code gọi `setNeedClientAuth(true)` trên socket đã handshake.

Một thiết kế vững hơn:

- Định rõ từ **initial handshake** listener nào bắt buộc mTLS.
- Nếu ứng dụng muốn nâng cấp identity, dùng một kết nối mới có chính sách required hoặc giao thức có xác thực lại được thiết kế/kiểm toán.
- Không cho thao tác nhạy cảm chạy trước khi identity mong muốn được thiết lập và kiểm tra.
- Kiểm soát session resumption, TLS termination ở proxy và caching để không vô tình dùng lại trạng thái authentication không phù hợp.

### 9.3. Cần hiểu đâu là server “thực sự” chấm chứng thư

Trong hệ thống có load balancer, API gateway hoặc service mesh, TLS có thể được **terminate trước khi đến Java backend**. Lúc đó:

```text
Internet client --(mTLS)--> Gateway --(HTTP hoặc TLS mới)--> Java backend
                            |
                            +-- gateway kiểm client cert
```

Backend Java không thể chỉ gọi `SSLSocket.getPeerCertificates()` rồi giả định nó đang nhìn certificate của Internet client nếu TLS ngoài đã kết thúc ở gateway. Một số proxy đưa identity qua header, nhưng header chỉ được tin khi đến từ gateway đã xác thực, được chặn spoofing, có bảo vệ hop-by-hop và policy authorization. Nếu không, attacker có thể tự gửi header giả.

Cũng không thể bật `needClientAuth` ở backend rồi coi đã ép chứng thư cho kết nối **client → gateway** nếu TLS ngoài do gateway quản lý; cần cấu hình đúng điểm TLS termination.

## 10. SSLParameters.setNeedClientAuth và setWantClientAuth: lỗi thứ tự thường gặp

### 10.1. Hai cờ không thể vừa true

```java
SSLParameters p = new SSLParameters();
p.setNeedClientAuth(true);
System.out.println(p.getNeedClientAuth()); // true
System.out.println(p.getWantClientAuth()); // false

p.setWantClientAuth(true);
System.out.println(p.getNeedClientAuth()); // false
System.out.println(p.getWantClientAuth()); // true
```

Nếu có một module cấu hình đặt required trước, rồi module khác đặt optional sau, **chính sách cuối cùng là optional**. Ngược lại cũng vậy.

Hãy kiểm trạng thái **sau khi kết thúc xây dựng cấu hình**, không chỉ grep source code để xem đã có `setNeedClientAuth(true)` ở đâu hay chưa. Ở framework cấp cao, cần kiểm mapping ra JSSE và test handshake negative.

### 10.2. Không phải mọi lớp trong ứng dụng đều tự nhiên ở server mode

Một `SSLSocket` được khởi tạo để gọi API bên ngoài thường là **client-mode**; đặt `setWantClientAuth(true)` trên nó **không** có nghĩa client đang bảo server gửi chứng thư. Server authentication được thực hiện bằng trust manager và endpoint identification của TLS client, còn client certificate (nếu server yêu cầu) được chọn bằng KeyManager.

Phía **server-mode SSLSocket** mới là nơi need/want có tác dụng theo Javadoc. Nếu dùng `SSLEngine`, cần xác định `setUseClientMode(false)` khi vai trò là TLS server.

### 10.3. Không sửa trạng thái sau handshake như cách bắt buộc khách

Đặt client auth policy khi socket chưa bắt đầu handshake. Một khi có phiên đã xác lập, đổi cờ có thể không khiến peer cung cấp certificate ở cùng phiên và hành vi liên quan đến renegotiation không nên dựa vào cho thiết kế mới. Dùng listener/handshake mới có policy đúng.

### 10.4. Không quên cấu hình TrustManager

`setNeedClientAuth(true)` **không** chỉ định CA nào được phép ký client certificates. Đó là trách nhiệm của trust management. Nếu không cấu hình đúng, server có thể từ chối client hợp lệ hoặc chấp nhận phạm vi rộng hơn mong muốn nếu dùng truststore quá rộng/manager sai.

## 11. Thiết kế production với mTLS bắt buộc và optional

### 11.1. Khi nên dùng needClientAuth

- Kết nối service-to-service nội bộ mà policy yêu cầu mọi client phải có workload certificate.
- API quản trị/đặc quyền chỉ truy cập từ máy hoặc service identity được cấp chứng thư.
- Kênh nhận dữ liệu giữa đối tác có đăng ký client certificate và chính sách identity mapping rõ ràng.
- Listener dành riêng cho robot/batch jobs với yêu cầu xác thực ở TLS handshake.

Trong mỗi trường hợp, kiểm tra đủ: TLS client certificate **được tin** và **đúng client identity** cho service đó; sau đó gán quyền cụ thể. `needClientAuth` không thay thế phân quyền ứng dụng.

### 11.2. Khi muốn dùng wantClientAuth

- Một listener phục vụ cả client có mTLS identity và khách anonymous, với **phân tuyến/ủy quyền** rõ ràng sau handshake.
- Giai đoạn chuyển tiếp client certificates, nếu các API nhạy cảm vẫn được **chặn server-side** khi không có identity, và có kế hoạch chuyển sang required.
- Mô hình muốn lấy thêm tín hiệu nhận dạng máy/service bằng certificate nhưng **không mặc định** cho thêm quyền khi thiếu chứng thư.

**Nguy cơ cao:** cấu hình want trong khi marketing/security policy tuyên bố “mọi API đều bắt buộc mTLS”. Bất kỳ client không có cert nào có thể thiết lập TLS; nếu application check thiếu một route, kết nối anonymous có thể đi qua.

Nếu thật sự cần bắt buộc ở mọi đường xử lý, dùng `needClientAuth` hoặc lớp gateway enforcing client certificate tương đương, có negative tests.

### 11.3. Tránh truststore quá rộng

Tin một root CA công khai/bộ CA rộng để xác thực **client machine identity** có thể khiến server chấp nhận những chứng thư không được phát hành theo quy trình tổ chức bạn kỳ vọng. Để mTLS production an toàn, kiểm issuer/policy/OID/EKU, SAN identity, quyền cấp phát, trust rotation và revocation; có thể chọn một CA nội bộ giới hạn cho client certificates thay vì truststore mặc định dùng cho HTTPS server.

### 11.4. Certificate lifecycle và revocation

- Client private key cần bảo vệ: KeyStore có password mạnh, OS secret manager, HSM/TPM nếu threat model yêu cầu; không hardcode credential test.
- Certificate rotation phải tính trước việc cả hai phía cập nhật trust anchors, accepted issuers, identity mapping và overlap phù hợp.
- Nếu private key client bị lộ, cần revoke/disable identity và thay certificate/keys; **want/need không tự biết khóa đã bị đánh cắp**.
- PKIX validity/revocation/algorithm policy phải được cấu hình phù hợp môi trường; đừng tắt kiểm trust/thu hồi chỉ để demo pass.
- Không lấy subject DN do client cung cấp ngoài TLS và tự coi đó là authenticated certificate identity.

## 12. Các lỗi và biểu hiện khi vận hành

| Hiện tượng | Nguyên nhân khả dĩ | Hướng khắc phục |
| --- | --- | --- |
| `needClientAuth=true`, client không có cert, handshake fail | **Đúng thiết kế**: required nhưng không có chứng thư | Cấp client cert/private key hoặc điều chỉnh loại listener theo policy, không tắt trust. |
| `wantClientAuth=true`, client không có cert, handshake thành công | **Đúng thiết kế**: optional | Ở ứng dụng, không cho quyền mTLS khi peer cert vắng mặt. |
| Client có `client.p12` nhưng server vẫn thấy không có certificate | KeyManager không chọn được alias, issuer list/algorithm không phù hợp, private key không được load | Kiểm client key entry, certificate chain, acceptable issuers, handshake trace và KeyManager. |
| Client cert được trình nhưng handshake fail | CA không được trust, expired, EKU/key usage không đúng, chain invalid | Cấu hình truststore và PKIX policy, phát hành lại cert hợp lệ. |
| Server certificate được trình nhưng client từ chối | Client truststore không tin server hoặc SAN hostname/IP không khớp | Sửa server cert/truststore/reference identity, không bỏ hostname check. |
| `SSLPeerUnverifiedException` khi gọi `getPeerCertificates()` trên server | Không có peer certificate-based authentication | Xử lý nhánh anonymous của optional mTLS; nếu bắt buộc thì xem lại listener/policy. |
| `SSLHandshakeException` với `bad_certificate` hoặc alert khác | Trust/certificate/handshake policy fail; thông điệp tùy provider | Ghi nguyên nhân an toàn, kiểm thực tế handshake logs. |
| Server vẫn cho anonymous dù đã gọi `setNeedClientAuth(true)` | Có nơi gọi `setWantClientAuth(true)` sau đó; cấu hình đúng ở sai socket; proxy terminate TLS | Kiểm trạng thái cuối, TLS termination point và negative handshake test. |
| `needClientAuth` bật cho backend nhưng API gateway vẫn cho public clients | TLS client ngoài terminate ở gateway | Bật client-auth ở gateway hoặc bảo vệ backend hop đúng. |
| Một số client authenticate, một số không với want | Chỉ một phần client có private key và certificate chain phù hợp | Thiết kế identity fallback/authorization rõ, không giả định tất cả đã mTLS. |
| Certificate client hợp lệ nhưng bị từ chối API | TLS trust thành công nhưng authorization application thiếu role/scope | Xem mapping identity và policy, không hạ TLS. |
| Nâng want lên need nhưng kết nối cũ còn hoạt động | Session/handshake đã tạo trước thay đổi | Cắt/đóng session cũ theo policy hoặc áp dụng quy trình chuyển tiếp an toàn. |

### 12.1. Debug JSSE mà không làm yếu bảo mật

Trong lab, có thể bật chẩn đoán TLS handshake bằng JVM flag:

```bash
java -Djavax.net.debug=ssl:handshake -cp . ClientAuthDemo
```

Tùy hệ thống, output có thể rất dài và có chi tiết nhạy cảm (hostname, certificate chain, tên định danh, TLS metadata). Chỉ bật khi được phép, bảo vệ log và **không đưa private keys, passwords hay secrets** vào log. Chỉ dùng trên máy thử nghiệm, không mặc định mở trên production.

Cần phân biệt:

- **Client không trình certificate**: với want có thể tiếp tục; với need bị từ chối.
- **Server không trust client certificate đã trình**: bị từ chối, không “rơi xuống anonymous” như một thiết kế an toàn.
- **Client không trust server**: client từ chối, không liên quan directly need/want.
- **Application không cấp quyền**: TLS handshake vẫn có thể thành công; HTTP API trả 401/403 theo policy — đây là lớp khác.

Không khuyên cấu hình `trustAll()` hoặc `HostnameVerifier((h,s)->true)` khi debug. Cách sửa đúng là sửa danh tính/chứng thư/truststore hoặc policy.

## 13. Test matrix nên có trước khi phát hành

| Server mode | Client identity | Trình chứng thư? | Kết quả TLS server-side mong đợi | Identity trong ứng dụng |
| --- | --- | --- | --- | --- |
| None | Không có | Không yêu cầu | TLS có thể thành công | Không có mTLS identity. |
| Want | Không có keypair | Không | **Thành công** nếu các điều kiện khác đạt | Anonymous / xác thực khác, không mTLS identity. |
| Need | Không có keypair | Không | **Thất bại** | Không xử lý request. |
| Want | Có cert được trust | Có | **Thành công** | Có certificate identity, vẫn kiểm authorization. |
| Need | Có cert được trust | Có | **Thành công** | Có certificate identity, vẫn kiểm authorization. |
| Want | Có cert thật sự gửi nhưng không được trust | Có | **Thất bại** | Không xử lý request. |
| Need | Có cert thật sự gửi nhưng không được trust | Có | **Thất bại** | Không xử lý request. |
| Need | Có cert đúng CA nhưng sai role/tenant | Có | TLS có thể thành công | **Application phải từ chối** nếu không được phép. |
| Want | Có cert nhưng KeyManager không chọn được alias | Không | TLS có thể thành công | Không có mTLS identity. |
| Need | Client có cert nhưng client không trust server | Có thể chưa gửi | TLS thất bại do server identity/trust | Không xử lý request. |

**Đừng chỉ test positive case** `need + valid cert`. Ca âm **need + no cert** bắt buộc phải bị chặn; ca **want + no cert** bắt buộc không bị gán mTLS identity. Khi policy cho một route yêu cầu mTLS, phải test request qua **mọi điểm TLS termination**, không chỉ test một class JSSE riêng lẻ.

### 13.1. Assertions nên đưa vào integration test

1. `getNeedClientAuth()`/`getWantClientAuth()` phản ánh **trạng thái cuối cùng** của listener/engine.
2. Không có client cert + required → server-side TLS handshake **không thành công**.
3. Không có client cert + optional → server-side TLS handshake **có thể thành công**; `getPeerCertificates()` báo không có authenticated peer.
4. Client cert hợp lệ và được tin → handshake thành công; server nhận được peer cert.
5. Client cert sai hoặc bị thu hồi → handshake từ chối theo trust policy, kể cả optional khi cert thực sự được gửi.
6. Client cert hợp lệ nhưng không có quyền của tenant → application trả từ chối, không xem chứng thư là quyền admin chung.
7. Server cert có SAN sai → client handshake fail dù server yêu cầu client cert đúng.
8. Rotation và cập nhật truststore không tạo cửa sổ chấp nhận alias/issuer không mong muốn.
9. Proxy/gateway chặn header giả danh identity từ nguồn không đáng tin.
10. TLS 1.3 session reuse/connection pooling không tái sử dụng authentication state sai cho protected routes.

## 14. FAQ và những hiểu lầm cần loại bỏ

**Hỏi: `wantClientAuth(true)` khác `needClientAuth(true)` đúng một chữ “không bắt buộc” thôi sao?**

Khác nhau chính là **hành vi handshake khi client không cung cấp certificate**. Với want, server có thể chấp nhận kết nối không có chứng thư, nên **lớp ứng dụng bắt buộc phân biệt rõ anonymous**. Với need, server không nên cho handshake hoàn tất nếu client không thể xác thực bằng certificate phù hợp.

**Hỏi: Khi want=true, nếu client gửi certificate hết hạn hoặc không tin cậy, server có tiếp tục như anonymous không?**

Không nên. Khi **certificate đã được gửi và validation thất bại**, handshake phải thất bại. Optional nghĩa là không cần xuất trình chứng thư, chứ không phải được phép trình chứng thư sai. Tuy nhiên khi KeyManager không chọn được alias, client có thể **không gửi cert**, lúc đó muốn kết luận cần xem handshake trace.

**Hỏi: Khi need=true, tôi có thể bỏ KeyManager của server vì server chỉ kiểm client cert không?**

Không. Trong mô hình TLS server authentication tiêu chuẩn, server cũng cần local identity/private key để client xác minh server; mTLS thêm bước **client certificate**, không thay thế server certificate.

**Hỏi: `serverTruststore` cần chứa những gì?**

Những trust anchors hoặc certificates mà **server được phép tin để xác minh client** theo policy; có thể là CA nội bộ chuyên cấp client cert, không nhất thiết cùng store với truststore mà client dùng để tin server. Nhưng trust anchor rộng chưa đủ: cần identity/authorization mapping.

**Hỏi: Client có `client.cer` trong classpath thì đã được mTLS chưa?**

Chưa. Client phải có private key tương ứng và KeyManager có thể chọn credential phù hợp. Một public certificate file không tạo bằng chứng sở hữu khóa riêng.

**Hỏi: `getPeerCertificates()` cho biết điều gì?**

Nó cho biết certificate chain của peer trong session được xác minh bằng certificate authentication (với trust manager phù hợp); có thể ném `SSLPeerUnverifiedException` nếu peer không được xác minh theo cách đó. Nó không tự đánh giá ứng dụng có nên cấp role hay không.

**Hỏi: Có nên mở wantClientAuth ở mọi API trước, đến route admin mới đổi need không?**

Không nên dựa vào đổi cờ giữa phiên TLS. Đặt chính sách required trên listener phù hợp **ngay từ handshake**, hoặc thiết kế xác thực/ủy quyền cấp ứng dụng và gateway thật chặt. TLS 1.3 không có renegotiation kiểu cũ để tự nâng cấp như vậy.

**Hỏi: Nếu server TLS nằm sau load balancer, đặt needClientAuth trong Java có kiểm Internet client không?**

Không chắc. Nếu TLS terminate ở gateway, Java chỉ thấy kết nối tiếp theo từ gateway (hoặc plain HTTP). Cần enforce client certificate **ở đúng điểm kết thúc TLS** và truyền identity đã xác thực qua boundary đáng tin, có chống spoofing.

**Hỏi: Nếu tôi bật needClientAuth thì server certificate có tự được xác minh DNS SAN không?**

Không. Việc **client kiểm server** là hướng khác. HTTPS client cần trust path và endpoint identification phù hợp, không liên quan trực tiếp đến cờ server yêu cầu client cert.

## 15. Checklist triển khai

- [ ] Có quyết định rõ **NONE**, **WANT** hay **NEED** cho từng listener/port/route/proxy.
- [ ] Với API cần mTLS cứng, server-side handshake phải **từ chối client không có cert**.
- [ ] Đã kiểm tra trạng thái cuối của `getNeedClientAuth()` và `getWantClientAuth()`; không có cấu hình đè ngược ngoài ý muốn.
- [ ] Server có KeyManager/certificate/private key hợp lệ; client có TrustManager tin server và hostname verification đúng.
- [ ] Server có TrustManager tin đúng client issuers/certificates theo PKIX policy; không dùng accept-all.
- [ ] Client có private key/cert chain nếu muốn hoặc bắt buộc xác thực bằng certificate.
- [ ] Chứng thư client có EKU/KeyUsage, hiệu lực, policy/revocation phù hợp; root CA được phân phối qua kênh tin cậy.
- [ ] Khi want/optional, ứng dụng xử lý **không có peer certificate** như anonymous hoặc yêu cầu xác thực khác.
- [ ] Khi có peer cert, ứng dụng còn kiểm **identity mapping, authorization, role, tenant và scope**.
- [ ] Không cố nâng cấp optional thành required sau khi handshake mà thiếu giao thức hỗ trợ rõ ràng.
- [ ] Đã test bốn trường hợp căn bản và những ca client cert sai/hostname server sai.
- [ ] Truststore/keystore/password demo không được copy vào production; private keys không nằm trong repo.
- [ ] Đã xác định điểm TLS termination của gateway/proxy/service mesh; header identity không thể bị client giả.
- [ ] Có giám sát và kế hoạch rotation/revocation các client certificates, session/connection pooling và fail-closed.

## 16. Bài tập tự luyện

1. Phân biệt chính xác `setNeedClientAuth(true)` và `setWantClientAuth(true)` trong trường hợp client không có chứng thư.
2. Vì sao `wantClientAuth` **không** là cơ chế cho qua certificate bị TrustManager từ chối?
3. Server có `needClientAuth=true` nhưng không có truststore phù hợp; client có certificate. Handshake có chắc thành công không?
4. Trong `SSLContext.init(keyManagers, trustManagers, random)`, bên server dùng từng array để làm gì? Bên client thì sao?
5. Nếu client chỉ có `client.cer` mà không có private key, nó có thể trình chứng cứ sở hữu khóa riêng không?
6. Nếu `SSLParameters` gọi `setNeedClientAuth(true)` rồi `setWantClientAuth(true)`, trạng thái cuối là gì?
7. Vì sao `SSLSession.getPeerCertificates()` có thể ném `SSLPeerUnverifiedException` khi want=true?
8. Vì sao `needClientAuth=true` không cho biết role, tenant, scope hay quyền đọc database của client?
9. Khi TLS terminate tại gateway, backend Java cần tin thông tin client identity từ đâu và chống giả mạo thế nào?
10. TLS 1.3 có cho phép bạn chỉ đổi cờ trên socket đã handshake để buộc client trình cert ngay lập tức không? Vì sao không nên dựa vào điều đó?
11. Nếu server chỉ xác minh client cert nhưng HTTPS client tắt hostname verification cho server, còn rủi ro gì?
12. Bạn sẽ viết negative tests nào để chứng minh một API “mTLS required” thật sự không mở cho kết nối anonymous?

**Bài tập A:** chạy `ClientAuthDemo` và ghi lại bốn dòng. Sau đó đổi code từ `listener.setWantClientAuth(true)` thành `listener.setNeedClientAuth(true)` trong mọi trường hợp; dự đoán kết quả khi client không có cert.

**Bài tập B:** thay `server-trust.p12` bằng một truststore test không chứa issuer của `client.p12`. Kiểm handshake có thất bại hay client có thể chọn không gửi cert; dùng `javax.net.debug=ssl:handshake` trong môi trường test để phân biệt.

**Bài tập C:** thêm một local HTTP handler phía sau TLS với route `/public` và `/admin`. Khi listener ở want mode, đảm bảo `/public` cho phép client anonymous theo policy, còn `/admin` từ chối nếu session không có client certificate identity đáng tin và quyền admin. Sau đó so sánh với listener need mode.

**Bài tập D:** tạo một cert client thứ hai được cấp bởi cùng CA test nhưng ánh xạ sang tenant khác. Chứng minh `needClientAuth` có thể chấp nhận certificate theo trust policy nhưng authorization vẫn từ chối route của tenant không tương ứng.

**Bài tập E:** giả lập một lần đổi cấu hình bất cẩn: gọi need=true ở lớp cấu hình bảo mật rồi want=true ở lớp framework. Viết assertion `getNeedClientAuth() == true` tại thời điểm listener sẵn sàng nhận kết nối để phát hiện regression.

## 17. Tài liệu API chính thức

- [SSLServerSocket — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLServerSocket.html): `setNeedClientAuth`, `setWantClientAuth`, getter, trạng thái ba lựa chọn và ý nghĩa trên accepted sockets.
- [SSLSocket — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSocket.html): cấu hình server-side socket, `startHandshake` và `getSSLParameters`.
- [SSLParameters — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): `setNeedClientAuth` xóa want flag, `setWantClientAuth` xóa need flag, `setEndpointIdentificationAlgorithm`.
- [SSLContext — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html): `init(KeyManager[], TrustManager[], SecureRandom)` và TLS socket factories.
- [SSLSession — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSession.html): `getPeerCertificates()`, `getPeerPrincipal()` và `SSLPeerUnverifiedException`.
- [KeyManagerFactory — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/KeyManagerFactory.html): sử dụng key stores/private keys để chọn local identity.
- [TrustManagerFactory — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html): tạo trust managers từ truststore.
- [X509ExtendedTrustManager — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/X509ExtendedTrustManager.html): xác minh chứng thư theo TLS peer/socket/engine context.
- [Java Secure Socket Extension (JSSE) Reference Guide — Java SE 17](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): mô hình key/trust managers, TLS 1.3 và lập trình SSL/TLS.
- [RFC 8446 — The Transport Layer Security (TLS) Protocol Version 1.3](https://www.rfc-editor.org/rfc/rfc8446): CertificateRequest, client authentication, certificate_required alert và khác biệt TLS 1.3.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jsse-006">16.6 X509ExtendedKeyManager chọn alias và X509ExtendedTrustManager kiểm tra đối tác</a>

<details>
<summary>Xem chi tiết</summary>

# 16.6 X509ExtendedKeyManager chọn alias và X509ExtendedTrustManager kiểm tra đối tác

## 1. Hai trách nhiệm khác nhau trong JSSE

**JSSE (Java Secure Socket Extension)** thực hiện TLS bằng các thành phần như `SSLContext`, `SSLSocket`, `SSLEngine`, `KeyManager` và `TrustManager`. Trong handshake TLS dùng certificate, có **hai câu hỏi tách biệt**:

1. **“Tôi sẽ trình chứng thư và dùng khóa riêng nào?”** — `X509ExtendedKeyManager` chọn *alias* trỏ đến một cặp private key + certificate chain phù hợp cho **chính endpoint này**. Khi JSSE cần chữ ký hoặc thao tác dùng khóa riêng, key manager cung cấp private key qua alias đã chọn.
2. **“Tôi có tin certificate của đầu bên kia không?”** — `X509ExtendedTrustManager` xác minh **certificate chain của peer**, ràng buộc PKIX, mục đích sử dụng, thuật toán và **ngữ cảnh kết nối** (socket/engine) theo cấu hình và chính sách. Nếu không tin, nó phải ném `CertificateException` thay vì cho handshake hoàn thành.

Hai thành phần đều dùng thuật ngữ X.509, nhưng **không thay thế nhau**:

| Thành phần | Đầu vào chính | Quyết định | Đầu ra |
| --- | --- | --- | --- |
| `X509ExtendedKeyManager` | `keyType`, `issuers`, `Socket` hoặc `SSLEngine`, local keystore | Chọn local alias để client/server trình chứng thư phù hợp | `String alias` hoặc `null`; `getPrivateKey` và `getCertificateChain` |
| `X509ExtendedTrustManager` | `X509Certificate[] chain` từ **peer**, `authType` và kết nối | Peer chain có đáng tin, hợp lệ và phù hợp ràng buộc không? | Không trả về khi chấp nhận; ném `CertificateException` khi từ chối |
| `KeyManagerFactory` | KeyStore chứa private key và certificate chain, password/protection | Khởi tạo provider key managers | `KeyManager[]` |
| `TrustManagerFactory` | TrustStore hoặc PKIX trust anchors/policy | Khởi tạo trust managers | `TrustManager[]` |

Một certificate có thể **được chọn để gửi** bởi key manager nhưng **bị từ chối** bởi trust manager của đối tác. Ngược lại, đối tác có thể tin chain, nhưng handshake vẫn thất bại vì key manager không tìm được alias/khóa riêng tương thích.

### Vai trò client, server và mTLS

```text
                   TLS server                         TLS client
        +-----------------------------+    +-----------------------------+
        | KeyManager                  |    | KeyManager (khi mTLS)       |
        | chọn alias chứng thư server  |    | chọn alias chứng thư client |
        +--------------+--------------+    +--------------+--------------+
                       |                                  |
        gửi Certificate/CertificateVerify -->     <-- gửi Certificate/
                                              CertificateVerify (mTLS)
                       |                                  |
        +--------------v--------------+    +--------------v--------------+
        | TrustManager                 |    | TrustManager                |
        | kiểm chứng client (nếu mTLS) |    | kiểm chứng server           |
        +-----------------------------+    +-----------------------------+
```

Trong TLS **server-auth-only**, client thường không phải gửi certificate và key manager client có thể không được gọi để chọn alias. Trong **mutual TLS (mTLS)**, server yêu cầu client certificate; **cả hai phía** thường có key manager để trình chứng thư của mình và trust manager để kiểm chứng bên kia.

**Không bao giờ biến “chọn được alias” thành “certificate được peer tin”.** Alias chỉ là tên của local entry (thường trong `KeyStore`), không phải trust decision.

## 2. KeyManager, X509KeyManager và X509ExtendedKeyManager

`X509KeyManager` là interface dùng chọn credential X.509 với `Socket`. `X509ExtendedKeyManager` mở rộng lớp này, bổ sung overload cho **`SSLEngine`**. Khi viết TLS client/server trên NIO, HTTP framework, proxy hoặc custom network stack sử dụng `SSLEngine`, các overload extended là thiết yếu.

| Method | Chức năng |
| --- | --- |
| `getClientAliases(String keyType, Principal[] issuers)` | Liệt kê alias local có thể dùng làm client certificate cho loại key và issuer constraints |
| `chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket)` | Chọn một alias client phù hợp khi handshake bằng socket |
| `getServerAliases(String keyType, Principal[] issuers)` | Liệt kê alias có thể dùng làm server certificate cho loại key/issuers |
| `chooseServerAlias(String keyType, Principal[] issuers, Socket socket)` | Chọn một alias server trên handshake socket |
| `chooseEngineClientAlias(String[] keyTypes, Principal[] issuers, SSLEngine engine)` | Chọn client alias cho `SSLEngine` |
| `chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine)` | Chọn server alias cho `SSLEngine` |
| `getCertificateChain(String alias)` | Lấy chuỗi certificate trình với alias, thường từ leaf đến intermediate |
| `getPrivateKey(String alias)` | Lấy private key **của local alias** để ký/handshake; có thể là opaque provider handle |

### Ý nghĩa của keyType, issuers và alias

- **`keyType`:** loại khóa/thuật toán phù hợp ngữ cảnh handshake, ví dụ `"RSA"`, `"EC"`; các tên giá trị và ràng buộc lựa chọn có thể phụ thuộc provider, TLS version và signature schemes. **Không** tự kết luận TLS 1.3 cipher suite tên `TLS_AES_128_GCM_SHA256` yêu cầu khóa AES để chọn certificate; TLS 1.3 đàm phán signature scheme riêng.
- **`String[] keyTypes` phía client:** nhiều loại credential có thể phù hợp theo đề nghị của server. Key manager cân nhắc những loại được phép và issuer constraints; trả `null` nếu không có credential phù hợp.
- **`Principal[] issuers`:** danh sách CA distinguished names mà phía yêu cầu certificate sẵn sàng chấp nhận, hoặc `null` trong các trường hợp không có giới hạn. Đây là **gợi ý/ràng buộc lựa chọn**, không phải giấy xác nhận một chain đáng tin. Phải truyền/áp dụng constraints đúng ngữ nghĩa thay vì bỏ qua.
- **`alias`:** định danh local key entry được manager biết; có thể tương ứng một `KeyStore` alias, nhưng một provider có thể tạo alias nội bộ. Đừng giả định alias là hostname, user ID, chứng minh danh tính hay mã định danh ổn định giữa các provider.
- **`getCertificateChain(alias)` và `getPrivateKey(alias)`:** phải tương ứng với **cùng credential**. Private key phải khớp public key trong certificate leaf. Không trộn chain của alias A với private key của alias B.

Alias selection có thể cần thêm thông tin handshake như **SNI**, TLS version, signature algorithms và key usages. Với `Socket`, có thể xem handshake session trên `SSLSocket` khi có; với `SSLEngine` có thể xem `engine.getHandshakeSession()` và các thông tin liên quan. **Handshake session có thể là `null` nếu chưa sẵn sàng**; không coi giá trị thiếu là lý do trả credential bừa bãi.

### Vì sao không hardcode `"server"` làm alias cho mọi handshake?

Khi server có nhiều certificate (ví dụ RSA và ECDSA, hoặc nhiều virtual hosts), chọn alias phải tính đến:

- keyType và signature schemes được peer hỗ trợ;
- SNI và virtual host **đã được cấu hình/ủy quyền**;
- certificate SAN, chain, thời hạn, KU/EKU, các thuật toán bị disabled;
- issuer constraints và policy;
- cipher/TLS version, khả năng thao tác của provider hoặc HSM.

Chỉ vì một alias tên `"server"` tồn tại không có nghĩa nó là lựa chọn hợp lệ cho mọi SNI/key type. Nếu không có credential hợp lệ, **trả `null`/để handshake thất bại**, không chọn nhầm tenant hay trình certificate sai danh tính.

## 3. TrustManager và X509ExtendedTrustManager

`X509TrustManager` có ba method cơ bản:

```java
void checkClientTrusted(X509Certificate[] chain, String authType)
        throws CertificateException;
void checkServerTrusted(X509Certificate[] chain, String authType)
        throws CertificateException;
X509Certificate[] getAcceptedIssuers();
```

`X509ExtendedTrustManager` mở rộng với **bốn overload có ngữ cảnh**:

```java
void checkClientTrusted(
        X509Certificate[] chain, String authType, Socket socket)
        throws CertificateException;
void checkServerTrusted(
        X509Certificate[] chain, String authType, Socket socket)
        throws CertificateException;
void checkClientTrusted(
        X509Certificate[] chain, String authType, SSLEngine engine)
        throws CertificateException;
void checkServerTrusted(
        X509Certificate[] chain, String authType, SSLEngine engine)
        throws CertificateException;
```

Các overload nhận `Socket`/`SSLEngine` rất quan trọng: provider có thể sử dụng **SSLParameters, endpoint identification algorithm, handshake session, requested server name, negotiated signature algorithm và TLS-specific constraints** khi xác minh peer. Phần kiểm tra **certificate path** thường cần trust anchor, validity, chữ ký, BasicConstraints, KeyUsage/ExtendedKeyUsage, algorithm constraints, critical extensions và chính sách thu hồi theo profile.

**Ranh giới quan trọng:** overload `checkServerTrusted(chain, authType, socket)` **không được thay bằng** `delegate.checkServerTrusted(chain, authType)` khi đang có socket, vì bạn có thể mất các kiểm tra phụ thuộc ngữ cảnh, đặc biệt hostname/endpoint identification. Tương tự, trong `SSLEngine` phải truyền nguyên engine tới overload extended của delegate.

### “Chấp nhận” không phải trả true

Method `checkServerTrusted`/`checkClientTrusted` có return type `void`:

- **Thành công:** kết thúc bình thường (không exception).
- **Thất bại:** ném `CertificateException` (hoặc subtype) để TLS handshake từ chối peer.

Một custom trust manager như dưới đây **không an toàn**:

```java
// CỐ TÌNH SAI: KHÔNG DÙNG TRONG SẢN PHẨM.
@Override public void checkServerTrusted(
        X509Certificate[] chain, String authType) {
    // Do nothing => trust-all, không kiểm tra certificate chain.
}
```

Sự tồn tại `getAcceptedIssuers()==new X509Certificate[0]` cũng **không** làm các method `check...Trusted` tự an toàn. Không sử dụng trust-all để “fix PKIX path building failed” hoặc “fix hostname mismatch”.

### Vai trò của authType

`authType` là tham số do JSSE cung cấp về loại xác thực được sử dụng trong handshake theo hợp đồng API và provider, **không phải OID EKU `serverAuth`/`clientAuth`** và không phải giá trị do app chọn tùy ý. Không viết policy kiểu `authType.equals("serverAuth")`. Không tự suy từ tên cipher suite TLS 1.3 ra giá trị `authType` cố định: TLS version/signature schemes/provider có thể thay đổi cách biểu diễn.

## 4. KeyStore và TrustStore: vật liệu khác nhau

Trong JSSE thường có **hai store với vai trò khác nhau**:

| Store | Nội dung | Dùng bởi | Có chứa private key local? |
| --- | --- | --- | --- |
| **KeyStore** | `PrivateKeyEntry`: private key + certificate chain tương ứng | `KeyManagerFactory` | Có, với credential phần mềm; HSM có thể dùng handle |
| **TrustStore** | `TrustedCertificateEntry` hoặc trust anchors/certificates tin cậy | `TrustManagerFactory` | Thường **không cần** private key |
| **Server key store** | Server private key và server certificate/chain | Server key manager | Có |
| **Client key store** | Client private key và client certificate/chain | Client key manager khi mTLS | Có |
| **Server trust store** | CA/certificate được server tin cho **client certificate** | Server trust manager | Không |
| **Client trust store** | CA/certificate được client tin cho **server certificate** | Client trust manager | Không |

Khi mTLS, **server kiểm client**, **client kiểm server**. Nếu đặt sai truststore (ví dụ client chỉ tin client certificate thay vì server issuer), handshake có thể fail dù cả key managers chọn alias hợp lệ.

### Factory khởi tạo đúng cách

Đoạn minh họa trong một phương thức Java, chưa phải chương trình độc lập:

```java
KeyManagerFactory kmf = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm());
kmf.init(keyStore, keyPasswordChars);

TrustManagerFactory tmf = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
tmf.init(trustStore);

SSLContext ctx = SSLContext.getInstance("TLS");
ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(),
         new SecureRandom());
```

Chú ý `KeyManagerFactory.getDefaultAlgorithm()` hoặc `TrustManagerFactory.getDefaultAlgorithm()` **chọn algorithm factory**, không đồng nghĩa “đã dùng đúng KeyStore”, “đã bật hostname verification” hoặc “đã cấu hình mTLS”. Những điều này cần được cấu hình riêng trên socket/engine/server.

**Một số factory chỉ cung cấp `X509KeyManager`/`X509TrustManager` cơ bản hoặc một implementation khác với JSSE profile:** ví dụ chạy trong bài kiểm tra rõ ràng yêu cầu kiểu `X509Extended...`. Khi không có loại extended tương ứng, ví dụ **fail closed bằng `IllegalStateException`**, không tùy tiện cast hay tự tạo trust-all adapter.

## 5. Cấu hình handshake mTLS an toàn

Đối với một mTLS channel qua `SSLSocket`:

1. Server có certificate + private key phù hợp hostname/thuật toán và đã khởi tạo `KeyManagerFactory`.
2. Client có `TrustManagerFactory` gắn trust anchors cho server. Client bật **endpoint identification**: `socket.getSSLParameters().setEndpointIdentificationAlgorithm("HTTPS")` và **áp lại** `socket.setSSLParameters(...)` trước `startHandshake()`.
3. Server cấu hình **`setNeedClientAuth(true)`**, không chỉ `setWantClientAuth(true)`. `need` yêu cầu client phải cung cấp certificate hợp lệ; `want` vẫn có thể cho phép peer không có certificate.
4. Client có key manager cung cấp client certificate/private key phù hợp issuer/key type; server có trust manager tin issuer/client certificate theo policy.
5. Cả hai chạy TLS handshake; `X509ExtendedKeyManager` được gọi để chọn alias phù hợp và `X509ExtendedTrustManager` được gọi để kiểm peer.
6. Sau thành công, ứng dụng **vẫn phải** thực hiện authorization dựa trên danh tính đã xác minh: role, tenant, endpoint/purpose, ACL, scopes. mTLS xác thực certificate **không** cho phép tất cả API tự động.

Nếu ứng dụng dùng NIO/`SSLEngine`, phải cài **cả các overload `chooseEngine...Alias` và `check...Trusted(..., SSLEngine)`**, rồi đảm bảo peer host và endpoint identification configured hợp lý. Dùng các overload nhận `Socket` cho engine là sai bối cảnh.

### SSLParameters và hostname verification

Trong raw `SSLSocket` client, một `TrustManager` xác minh chain **không tự bảo đảm** hostname được kiểm nếu không bật endpoint identification tương ứng. Với HTTPS client frameworks, cơ chế này thường được stack cấu hình cho bạn; với raw socket/engine cần kiểm tra.

Ví dụ:

```java
// Đoạn minh họa, socket phải là SSLSocket client chưa bắt tay.
SSLParameters parameters = socket.getSSLParameters();
parameters.setEndpointIdentificationAlgorithm("HTTPS");
socket.setSSLParameters(parameters);
socket.startHandshake();
```

Chữ `"HTTPS"` ở đây là endpoint identification algorithm theo SSLParameters, **không phải** TLS protocol string, không phải cipher suite. Chứng thư server cho IP `127.0.0.1` cần SAN kiểu iPAddress phù hợp; chỉ `CN=localhost` không đủ thay thế SAN trong giao thức hiện đại.

## 6. Java 17+ thực hành: đóng gói extended managers không bỏ qua trust

Bài thực hành chạy **server và client trên loopback `127.0.0.1`** với certificate demo:

- Hai local KeyStores riêng `server.p12`/`client.p12`, mỗi store chỉ có một private key alias và certificate tự ký cho riêng peer.
- Hai truststores riêng `server-trust.p12`/`client-trust.p12`. Server-trust tin certificate client test; client-trust tin certificate server test.
- `ObservedKeyManager extends X509ExtendedKeyManager` **ủy quyền cho manager do KeyManagerFactory tạo**, đếm các lần chọn alias thành công. Không log alias/private key/certificate chain để tránh lộ metadata không cần thiết.
- `ObservedTrustManager extends X509ExtendedTrustManager` **ủy quyền cho delegate đúng overload**; tăng bộ đếm **chỉ sau khi delegate kiểm tra thành công**. Không tự cài “trust-all”.
- Handshake mTLS bắt buộc client certificate, kiểm tra HTTPS endpoint identity theo IP SAN và trao đổi một byte application data sau handshake.
- Chạy hai phiên riêng biệt **TLSv1.3** và **TLSv1.2**, kiểm tra cả hai phía thống nhất negotiated protocol.

**Bảo mật:** Đây là chương trình demo với **chứng thư tự ký tạm** được chính ứng dụng test import làm trust anchor. Trong sản phẩm, chỉ tin CA hoặc public key/certificate được phân phối bằng kênh đáng tin; không nhập chứng thư do remote peer đưa rồi dùng ngay làm trust anchor.

### 6.1 Tạo chứng thư và truststores (PowerShell)

Chạy những lệnh sau trong **cùng phiên PowerShell** với JDK 17+ có `keytool`. Hai certificate ở đây là test fixtures, password `changeit` chỉ để minh họa.

```powershell
$t = Join-Path $env:TEMP ("extended-managers-demo-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $t | Out-Null
$pw = "changeit"  # DEMO ONLY; không hardcode password sản phẩm.

# Server: SAN có iPAddress 127.0.0.1 để client xác minh hostname/IP.
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=localhost" -ext "SAN=ip:127.0.0.1,dns:localhost" -ext "KU:critical=digitalSignature,keyEncipherment" -ext "EKU=serverAuth" -validity 7 -storetype PKCS12 -keystore (Join-Path $t "server.p12") -storepass $pw -keypass $pw -noprompt

# Client: certificate dùng mTLS client authentication.
keytool -genkeypair -alias client -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=client-demo" -ext "KU:critical=digitalSignature" -ext "EKU=clientAuth" -validity 7 -storetype PKCS12 -keystore (Join-Path $t "client.p12") -storepass $pw -keypass $pw -noprompt

# Export CERTIFICATE PUBLIC, không phải private key.
keytool -exportcert -alias server -keystore (Join-Path $t "server.p12") -storepass $pw -rfc -file (Join-Path $t "server.pem")
keytool -exportcert -alias client -keystore (Join-Path $t "client.p12") -storepass $pw -rfc -file (Join-Path $t "client.pem")

# Server tin client, client tin server.
keytool -importcert -alias client -file (Join-Path $t "client.pem") -keystore (Join-Path $t "server-trust.p12") -storetype PKCS12 -storepass $pw -noprompt
keytool -importcert -alias server -file (Join-Path $t "server.pem") -keystore (Join-Path $t "client-trust.p12") -storetype PKCS12 -storepass $pw -noprompt
```

`-noprompt` chỉ dùng trong fixture mà bạn vừa tự tạo. **Không tự động import remote/unverified certificates** vào truststore production. Không reuse `server.p12`/`client.p12` này cho một dịch vụ thật.

### 6.2 Chương trình Java hoàn chỉnh

Lưu thành **`ExtendedManagersMutualTlsDemo.java`** trong thư mục làm việc (khác thư mục `$t` cũng được). Mã tương thích **Java 17+**, gồm toàn bộ sáu overload trust manager, bốn đường lựa chọn alias và các getter cần có của key manager.

```java
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.*;

public final class ExtendedManagersMutualTlsDemo {
    private static KeyStore load(Path path, char[] password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream stream = Files.newInputStream(path)) {
            store.load(stream, password);
        }
        return store;
    }

    private static X509ExtendedKeyManager keyManager(
            Path path, char[] password) throws Exception {
        KeyManagerFactory factory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        factory.init(load(path, password), password);
        for (KeyManager manager : factory.getKeyManagers()) {
            if (manager instanceof X509ExtendedKeyManager ext) return ext;
        }
        throw new IllegalStateException("Extended key manager unavailable");
    }

    private static X509ExtendedTrustManager trustManager(
            Path path, char[] password) throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init(load(path, password));
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509ExtendedTrustManager ext) return ext;
        }
        throw new IllegalStateException("Extended trust manager unavailable");
    }

    private static final class ObservedKeyManager extends X509ExtendedKeyManager {
        private final X509ExtendedKeyManager base;
        final AtomicInteger serverSelections = new AtomicInteger();
        final AtomicInteger clientSelections = new AtomicInteger();

        ObservedKeyManager(X509ExtendedKeyManager base) { this.base = base; }
        private String count(String alias, boolean server) {
            if (alias != null) {
                (server ? serverSelections : clientSelections).incrementAndGet();
            }
            return alias;
        }
        @Override public String chooseClientAlias(
                String[] types, java.security.Principal[] issuers, Socket socket) {
            return count(base.chooseClientAlias(types, issuers, socket), false);
        }
        @Override public String chooseServerAlias(
                String type, java.security.Principal[] issuers, Socket socket) {
            return count(base.chooseServerAlias(type, issuers, socket), true);
        }
        @Override public String chooseEngineClientAlias(
                String[] types, java.security.Principal[] issuers, SSLEngine engine) {
            return count(base.chooseEngineClientAlias(types, issuers, engine), false);
        }
        @Override public String chooseEngineServerAlias(
                String type, java.security.Principal[] issuers, SSLEngine engine) {
            return count(base.chooseEngineServerAlias(type, issuers, engine), true);
        }
        @Override public String[] getClientAliases(
                String type, java.security.Principal[] issuers) {
            return base.getClientAliases(type, issuers);
        }
        @Override public String[] getServerAliases(
                String type, java.security.Principal[] issuers) {
            return base.getServerAliases(type, issuers);
        }
        @Override public X509Certificate[] getCertificateChain(String alias) {
            return base.getCertificateChain(alias);
        }
        @Override public PrivateKey getPrivateKey(String alias) {
            return base.getPrivateKey(alias);
        }
    }

    private static final class ObservedTrustManager extends X509ExtendedTrustManager {
        private final X509ExtendedTrustManager base;
        final AtomicInteger serverChecks = new AtomicInteger();
        final AtomicInteger clientChecks = new AtomicInteger();

        ObservedTrustManager(X509ExtendedTrustManager base) { this.base = base; }
        @Override public void checkServerTrusted(
                X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            base.checkServerTrusted(chain, authType, socket);
            serverChecks.incrementAndGet();
        }
        @Override public void checkClientTrusted(
                X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            base.checkClientTrusted(chain, authType, socket);
            clientChecks.incrementAndGet();
        }
        @Override public void checkServerTrusted(
                X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            base.checkServerTrusted(chain, authType, engine);
            serverChecks.incrementAndGet();
        }
        @Override public void checkClientTrusted(
                X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            base.checkClientTrusted(chain, authType, engine);
            clientChecks.incrementAndGet();
        }
        @Override public void checkServerTrusted(
                X509Certificate[] chain, String authType)
                throws CertificateException {
            base.checkServerTrusted(chain, authType);
            serverChecks.incrementAndGet();
        }
        @Override public void checkClientTrusted(
                X509Certificate[] chain, String authType)
                throws CertificateException {
            base.checkClientTrusted(chain, authType);
            clientChecks.incrementAndGet();
        }
        @Override public X509Certificate[] getAcceptedIssuers() {
            return base.getAcceptedIssuers();
        }
    }

    private record Side(SSLContext context, ObservedKeyManager key,
                        ObservedTrustManager trust) {}

    private static Side side(Path keys, Path trust, char[] password)
            throws Exception {
        ObservedKeyManager km = new ObservedKeyManager(keyManager(keys, password));
        ObservedTrustManager tm = new ObservedTrustManager(trustManager(trust, password));
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(new KeyManager[]{km}, new TrustManager[]{tm}, new SecureRandom());
        return new Side(context, km, tm);
    }

    private static void test(String version, Side server, Side client)
            throws Exception {
        int sAlias = server.key.serverSelections.get();
        int cAlias = client.key.clientSelections.get();
        int sTrust = server.trust.clientChecks.get();
        int cTrust = client.trust.serverChecks.get();
        try (SSLServerSocket listener = (SSLServerSocket)
                server.context.getServerSocketFactory().createServerSocket(
                        0, 1, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(20000);
            listener.setEnabledProtocols(new String[]{version});
            listener.setNeedClientAuth(true);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<String> future = executor.submit(() -> {
                    try (SSLSocket peer = (SSLSocket) listener.accept()) {
                        peer.setSoTimeout(20000);
                        peer.setEnabledProtocols(new String[]{version});
                        peer.setNeedClientAuth(true);
                        peer.startHandshake();
                        int request = peer.getInputStream().read();
                        peer.getOutputStream().write(request + 1);
                        peer.getOutputStream().flush();
                        return peer.getSession().getProtocol();
                    }
                });
                try (SSLSocket socket = (SSLSocket)
                        client.context.getSocketFactory().createSocket(
                                "127.0.0.1", listener.getLocalPort())) {
                    socket.setSoTimeout(20000);
                    socket.setEnabledProtocols(new String[]{version});
                    SSLParameters p = socket.getSSLParameters();
                    p.setEndpointIdentificationAlgorithm("HTTPS");
                    socket.setSSLParameters(p);
                    socket.startHandshake();
                    socket.getOutputStream().write(17);
                    socket.getOutputStream().flush();
                    int response = socket.getInputStream().read();
                    String serverProtocol = future.get(20, TimeUnit.SECONDS);
                    System.out.println(version + " negotiated="
                            + (version.equals(socket.getSession().getProtocol())
                                && version.equals(serverProtocol)));
                    System.out.println(version + " serverAliasSelected="
                            + (server.key.serverSelections.get() > sAlias));
                    System.out.println(version + " clientAliasSelected="
                            + (client.key.clientSelections.get() > cAlias));
                    System.out.println(version + " clientVerifiedServer="
                            + (client.trust.serverChecks.get() > cTrust));
                    System.out.println(version + " serverVerifiedClient="
                            + (server.trust.clientChecks.get() > sTrust));
                    System.out.println(version + " applicationData="
                            + (response == 18));
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5)
            throw new IllegalArgumentException(
                    "server.p12 server-trust.p12 client.p12 client-trust.p12 password");
        char[] password = args[4].toCharArray();
        try {
            Side server = side(Path.of(args[0]), Path.of(args[1]), password);
            Side client = side(Path.of(args[2]), Path.of(args[3]), password);
            test("TLSv1.3", server, client);
            test("TLSv1.2", server, client);
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
```

### 6.3 Biên dịch, chạy và output đã xác minh

Trong **cùng phiên PowerShell** còn biến `$t`/`$pw`, đứng ở thư mục chứa `ExtendedManagersMutualTlsDemo.java`:

```powershell
javac --release 17 -encoding UTF-8 -d $t ExtendedManagersMutualTlsDemo.java
java -cp $t ExtendedManagersMutualTlsDemo (Join-Path $t "server.p12") (Join-Path $t "server-trust.p12") (Join-Path $t "client.p12") (Join-Path $t "client-trust.p12") $pw
```

Kết quả **12 dòng đúng như lần chạy được kiểm thử** trên JDK 17+ có SunJSSE:

```text
TLSv1.3 negotiated=true
TLSv1.3 serverAliasSelected=true
TLSv1.3 clientAliasSelected=true
TLSv1.3 clientVerifiedServer=true
TLSv1.3 serverVerifiedClient=true
TLSv1.3 applicationData=true
TLSv1.2 negotiated=true
TLSv1.2 serverAliasSelected=true
TLSv1.2 clientAliasSelected=true
TLSv1.2 clientVerifiedServer=true
TLSv1.2 serverVerifiedClient=true
TLSv1.2 applicationData=true
```

**Diễn giải từng nhóm:**

- `negotiated=true`: hai bên báo cùng TLS version, khớp danh sách duy nhất được bật cho lần thử. Không suy ra mọi đối tác Internet hỗ trợ hai versions.
- `serverAliasSelected=true`: delegate key manager phía server đã chọn local server certificate alias được chấp nhận; đây là **đếm selection thành công**, không phải một chứng minh riêng về trust.
- `clientAliasSelected=true`: vì server yêu cầu client certificate (`setNeedClientAuth(true)`), phía client đã chọn alias để trình mTLS certificate.
- `clientVerifiedServer=true`: client-side **extended trust manager** đã gọi đúng delegate và chỉ tăng counter sau khi check server thành công.
- `serverVerifiedClient=true`: phía server cũng kiểm chứng client certificate thành công, thể hiện khác biệt giữa mTLS và TLS server-only.
- `applicationData=true`: sau handshake, client gửi một byte `17` và server trả `18`, buộc cả hai bên hoàn tất luồng bắt tay và xác nhận kênh có thể dùng để trao đổi data. Một số handshake implementations có thể hoàn tất đọc/ghi ở các thời điểm khác nhau; đừng dựa riêng vào `Future.get` khi chưa có I/O/timeout đúng.
- Các counter được lấy **trước và sau từng phiên**, vì cùng objects `Side` còn sống trong hai lần thử. Counter chứng minh delegate callback **đã chạy và thành công** trong phiên tương ứng; chúng không phải công cụ phân tích toàn bộ TLS certificate policy.

**Không có trust-all ở đây:** `ObservedTrustManager` chuyển nguyên `chain`, `authType`, `socket/engine` cho `base`, đồng thời client bật HTTPS endpoint identification. Hai truststores tin **test certificate đã được tạo** và không tự chấp nhận mọi chain. Trong hệ thống thực, trusted roots cần xuất phát từ trust configuration có kiểm soát, không được tạo từ certificate do peer gửi không xác thực.

**Tại sao không in alias/private key?** Key alias, issuer, chain và SNI đôi khi là metadata vận hành nhạy cảm. Log tối thiểu boolean/count và error code không có secret sẽ an toàn hơn so với log `getPrivateKey(alias).getEncoded()` (không bao giờ làm việc đó).

Khi xong bài, xóa fixtures demo:

```powershell
if ($t -like (Join-Path $env:TEMP "extended-managers-demo-*")) {
    Remove-Item -LiteralPath $t -Recurse -Force
}
```

Xóa file không bảo đảm xóa sạch mọi bản sao trên SSD/snapshot; **chỉ dùng test private keys** và không lưu chúng trong repository.

## 7. Vì sao decorator phải triển khai và ủy quyền đầy đủ?

Ví dụ dùng hai lớp decorator có trách nhiệm chính là **quan sát, không thay đổi policy**. Một wrapper thực tế có thể thêm lựa chọn certificate hoặc kiểm tra authorization riêng, nhưng phải hiểu hợp đồng cả superclass.

### 7.1 Những method key manager không được quên

Khi extends `X509ExtendedKeyManager`, hãy triển khai:

- `getClientAliases` và `getServerAliases`: kết quả có thể là `null` nếu không có credential phù hợp.
- `chooseClientAlias` / `chooseServerAlias`: cho socket-based handshake.
- `chooseEngineClientAlias` / `chooseEngineServerAlias`: cho `SSLEngine`.
- `getCertificateChain` và `getPrivateKey`: JSSE cần lấy certificate chain/private key của alias đã chọn.

Không nên implements `chooseEngineServerAlias` rồi **luôn trả `null`** với suy nghĩ ứng dụng chỉ dùng socket: nhiều frameworks dùng engine ngay cả khi giao thức chạy trên TCP. Khi chuyển từ blocking socket sang NIO, certificate selection có thể dừng hoạt động nếu thiếu engine overload.

Nếu cần ép một alias cụ thể:

1. Cho delegate chọn các candidates theo keyType/issuers/provider.
2. Chỉ chấp nhận alias được phép theo cấu hình tenant/hostname, key usage, certificate chain, thuật toán và handshake context đã xác minh.
3. Nếu không có alias phù hợp, fail closed bằng `null`/lỗi rõ ràng.
4. Không return alias tùy ý không có private key hoặc certificate phù hợp.

**SNI không phải identity authentication.** SNI do client đề xuất giúp server chọn virtual host, nhưng bản thân tên SNI không chứng minh client được ủy quyền. Certificate server phải phù hợp hostname và client vẫn cần kiểm tra SAN/endpoint identification. Đừng dùng SNI để cho phép truy cập tenant hoặc quyết định trust client certificate thay thế chính sách mTLS.

### 7.2 Những method trust manager không được quên

Khi extends `X509ExtendedTrustManager`, có **sáu** overload:

- `checkServerTrusted(chain, authType)`, `checkClientTrusted(chain, authType)` (cơ bản);
- `checkServerTrusted(chain, authType, Socket)` và `checkClientTrusted(chain, authType, Socket)`;
- `checkServerTrusted(chain, authType, SSLEngine)` và `checkClientTrusted(chain, authType, SSLEngine)`.

Thêm `getAcceptedIssuers()`. Nếu là decorator cho một delegate tin cậy, mỗi method extended cần **gọi delegate đúng overload**; không được rơi về basic overload chỉ vì dễ lập trình. Caller có thể cần chính context để kiểm hostname/SNI/SSLParameters/signature algorithms.

**Khi muốn thêm kiểm tra sau xác minh chain**, làm theo nguyên tắc *monotonic restriction*: gọi delegate an toàn trước, sau đó **chỉ thắt chặt** chính sách, ví dụ kiểm tra certificate public-key pin đã được phân phối an toàn hoặc subject mapping. Khi yêu cầu không đạt, ném `CertificateException`. Không cắt bỏ trust chain verification vì đã làm pinning (nếu đó không phải một thiết kế pinning thay thế được phân tích và phê duyệt kỹ).

Đoạn ý tưởng (không phải chương trình độc lập):

```java
@Override
public void checkServerTrusted(X509Certificate[] chain, String authType,
                               SSLEngine engine) throws CertificateException {
    delegate.checkServerTrusted(chain, authType, engine);
    // Chỉ sau khi delegate xác minh thành công:
    // verifyApprovedPublicKeyPin(chain[0]); // tự ném CertificateException nếu sai
}
```

**Pinning cẩn thận:** đừng hardcode raw certificate DER không có kế hoạch rotation/backup. Public key pin (SPKI hash) và certificate pin có tuổi thọ/rotation khác nhau. Một pin có khả năng chặn cả certificate hợp lệ khi key rotation, nên phải có rollout/escape hatch được bảo vệ; không tự biến đây thành “bỏ qua CA trust khi pin trùng” nếu không có threat model rõ ràng.

## 8. Socket và SSLEngine: ngữ cảnh ảnh hưởng kết quả

| Tiêu chí | `SSLSocket` | `SSLEngine` |
| --- | --- | --- |
| Mô hình giao tiếp | Blocking I/O trên socket | TLS state machine với ByteBuffers trong NIO/async/framework |
| Method chọn local client key | `chooseClientAlias(...,Socket)` | `chooseEngineClientAlias(...,SSLEngine)` |
| Method chọn local server key | `chooseServerAlias(...,Socket)` | `chooseEngineServerAlias(...,SSLEngine)` |
| Method kiểm peer trên client | `checkServerTrusted(...,Socket)` | `checkServerTrusted(...,SSLEngine)` |
| Method kiểm peer trên server mTLS | `checkClientTrusted(...,Socket)` | `checkClientTrusted(...,SSLEngine)` |
| Endpoint identification | `SSLParameters` trên socket | `SSLParameters` trên engine, cần host context đúng |
| SNI/handshake context | Có thể qua `SSLSession`/`ExtendedSSLSession` tùy thời điểm | `engine.getHandshakeSession()` và SNI/peer host nếu có |
| Rủi ro khi wrapper thiếu overload | Có thể hoạt động trên socket nhưng fail khi chuyển framework | Engine path dễ fail/mất context kiểm tra |

**Đặc biệt với SSLEngine:** khi tạo engine cho TLS client, nên có tên peer/port thích hợp (`SSLContext.createSSLEngine(host, port)`) và bật endpoint identification nếu giao thức cần. Dùng `createSSLEngine()` không host rồi mặc định “HTTPS hostname verification sẽ tự chạy” có thể làm thiếu dữ liệu định danh. Nếu handshake diễn ra qua reverse proxy, bảo đảm hostname cần kiểm là service identity mong muốn, **không** chỉ IP của proxy.

Khi override alias selection theo SNI, đọc `ExtendedSSLSession.getRequestedServerNames()` nếu handshake session và thông tin hỗ trợ; không dựa `getSession()` cũ khi đang handshake và không cho null/unsupported SNI trở thành lý do bypass policy.

## 9. Chọn certificate và kiểm peer không đồng nghĩa authorization ứng dụng

Ngay cả khi mTLS thành công:

- **Trust manager** chứng minh certificate/chain đạt ràng buộc PKIX/TLS của môi trường đó. Nó **không** tự ánh xạ client certificate sang role “admin” hay tenant.
- **Certificate subject/CN** không phải định danh ứng dụng đủ mạnh trong mọi profile. Cần quy tắc mapping đã phê duyệt (SAN URI/DNS, SPIFFE ID hoặc field chứng thư tương ứng), issuer đáng tin, policy và key lifecycle.
- **KeyUsage/ExtendedKeyUsage** quy định certificate được dùng cho mục đích mật mã/giao thức: serverAuth khác clientAuth. Một clientAuth certificate không được tự coi là server identity.
- **Hostname identification** kiểm tra server identity theo TLS/HTTPS; không thay kiểm tra client authorization trên API.
- **KeyManager chọn alias** có thể phụ thuộc SNI, issuer, signature algorithms, key type; không có nghĩa owner của alias có quyền truy cập mọi service.

Ví dụ với một API quản lý dữ liệu nhạy cảm, chỉ “mTLS handshake thành công” không đủ; service phải so định danh client đã xác thực với permission của endpoint/dataset/tenant, rồi audit.

Đừng log raw private key hoặc certificate chain đầy đủ trong mỗi handshake. Khi cần forensic, có thể log key ID/certificate fingerprint, issuer, certificate serial, role và TLS version theo chính sách dữ liệu, không đưa bí mật vào log.

## 10. Vấn đề thực tế và cách chẩn đoán

| Dấu hiệu lỗi | Nguyên nhân khả dĩ | Kiểm tra/khắc phục |
| --- | --- | --- |
| `SSLHandshakeException: no suitable certificate` | KeyManager không có alias đúng keyType/issuers/signature schemes | Xem KeyStore entries, chain, provider, `choose...Alias` và certificate profile |
| `no available authentication scheme` | Server không tìm được credential phù hợp key type/protocol | Kiểm certificate, signature schemes, server key manager và TLS 1.3 compatibility |
| `PKIX path building failed` | TrustStore không tin CA/peer certificate hoặc thiếu intermediates | Kiểm đúng trust anchors, chain và trust manager; không trust-all |
| `No subject alternative names matching ...` | HTTPS endpoint identification xác định peer host khác SAN | Cấp lại cert đúng SAN hoặc kết nối đúng hostname; không tắt hostname checks |
| `bad_certificate` / `certificate_unknown` | Peer từ chối certificate/chain | Xem log hai đầu; phân biệt selection failure với trust failure |
| `certificate_required` / client handshake rejected | Server yêu cầu client certificate nhưng client key manager trả null/sai loại | Kiểm `setNeedClientAuth`, client KeyStore, issuer list, EKU clientAuth |
| mTLS “thành công” dù client không có cert | Server đặt `setWantClientAuth(true)` hoặc chưa bật need | Dùng `setNeedClientAuth(true)` đúng socket/engine |
| Socket TLS thành công nhưng SSLEngine framework fail | Wrapper chỉ override Socket methods, engine methods trả null/không delegate | Implement cả engine overloads |
| Hostname không được kiểm khi dùng wrapper | `checkServerTrusted(...,Socket)` gọi basic overload, hoặc thiếu SSLParameters HTTPS | Delegate đúng contextual overload và bật endpoint identification |
| `InvalidKeyException` / key not found | Alias không có private key hoặc key handle/provider không tương thích | Đồng bộ `getCertificateChain`, `getPrivateKey`, provider, key policy |
| Một server nhiều certificates gửi sai virtual host | Chọn alias chỉ theo `keyType`, bỏ qua SNI/tenant và SAN | Lựa chọn có policy SNI an toàn, test từng hostname |
| `getAcceptedIssuers()` trả list gây lỗi client selection | Advertised CA list không khớp issuer chain của client | Kiểm trust anchors, issuer constraints; không trả list tùy ý |
| Certificate chain hợp lệ nhưng ứng dụng từ chối client | mTLS xác thực xong nhưng không có role/permission | Map identity được xác minh sang authorization nghiệp vụ |
| Fail khi rotate certificate | Alias/chain cũ còn cache, new alias thiếu key, truststore chưa cập nhật | Rollout trust trước, rotate key/cert có version và test |
| Connection chỉ fail ở TLS 1.3 | Key/cert/sig scheme hoặc group/provider khác TLS 1.2 | Kiểm trace JSSE, signature algorithms và KeyManager alias compatibility |
| `ClassCastException` khi lấy extended manager | Factory trả implementation chỉ tuân thủ interface cũ | Kiểm `instanceof`, đổi provider có hỗ trợ; không ép cast hoặc trust-all |
| `KeyStoreException` / `UnrecoverableKeyException` | Sai password, alias, store type hoặc token permissions | Kiểm stores, PIN, key material và provider |

**Không tự khắc phục lỗi bằng cách trả alias bừa bãi hoặc trust-all.** Một handshake thất bại là ranh giới bảo mật hữu ích; phải sửa root cause qua đúng truststore/credential/hostname/profile.

### Debug và quan sát an toàn

Trong staging/test, có thể bật JSSE trace:

```powershell
java "-Djavax.net.debug=ssl,handshake,keymanager,trustmanager" -cp $t ExtendedManagersMutualTlsDemo (Join-Path $t "server.p12") (Join-Path $t "server-trust.p12") (Join-Path $t "client.p12") (Join-Path $t "client-trust.p12") $pw
```

Log chứa thông tin certificate, hostname, issuer, alias, handshake metadata, và tùy debug flags có thể rất nhạy cảm. **Chỉ dùng với test certificates, giới hạn quyền truy cập log và không gửi nguyên logs production công khai**. Không bật chế độ dump traffic secrets. Với nhiều framework, xem thêm danh sách enabled protocols/suites, SNI, ALPN, trust anchors và TLS termination hop.

### Những test nên có trước khi deploy

- Chứng thư server đúng SAN và EKU serverAuth được client xác minh; sai SAN bị từ chối.
- mTLS client certificate hợp lệ được server xác minh; thiếu client cert khi `needClientAuth=true` phải thất bại.
- Client/server certificate do issuer không tin bị từ chối, không được silently import vào truststore.
- ClientAuth-only certificate không được chấp nhận làm serverAuth identity theo profile.
- Không có key alias đúng type/issuer: handshake thất bại, không fallback sang một tenant khác.
- Dùng `SSLEngine`: overload engine được gọi và context/hostname checks còn hiệu lực.
- Certificate rotation: alias/cert chain và trust anchors thay đổi có kiểm soát, không drop service ngoài kế hoạch.
- Thử TLS 1.2 và 1.3 phù hợp policy; đừng hạ xuống giao thức yếu vì một lỗi certificate.

## 11. Checklist thiết kế X509Extended managers

- [ ] Xác định rõ **local credential selection** và **peer trust validation** là hai bước riêng.
- [ ] `KeyManagerFactory` nhận đúng KeyStore/private key chain; `TrustManagerFactory` nhận đúng TrustStore/trust anchors.
- [ ] Cài toàn bộ `X509ExtendedKeyManager` methods, đặc biệt `chooseEngineClientAlias` / `chooseEngineServerAlias`.
- [ ] Cài đủ sáu overload của `X509ExtendedTrustManager`; delegation nhận đúng `Socket`/`SSLEngine`.
- [ ] Không dùng `checkServerTrusted` rỗng, trust-all, hoặc `getAcceptedIssuers` giả tạo để vượt xác minh chain.
- [ ] Client kiểm endpoint identity qua HTTPS/SAN trên raw socket hoặc engine có peer host thích hợp.
- [ ] Server mTLS bật `setNeedClientAuth(true)` và xác minh client chain theo đúng truststore.
- [ ] Alias selection tôn trọng `keyType`, `issuers`, SNI/context, algorithm policy, chain và key usage.
- [ ] Không hardcode một alias cho mọi tenant/host; fail closed nếu credential không phù hợp.
- [ ] Authentication thành công phải đi tiếp qua authorization ứng dụng theo certificate identity được phép.
- [ ] Quản lý key/cert/truststore rotation, backup, audit và kiểm thử handshake thật.
- [ ] Giới hạn debug và logs; không xuất private keys hoặc TLS secrets để quan sát callback.

## 12. Tự kiểm tra kiến thức

1. **`X509ExtendedKeyManager` làm gì?** Chọn local key alias và cung cấp certificate chain/private key cho handshake.
2. **`X509ExtendedTrustManager` làm gì?** Kiểm certificate chain của peer, ném `CertificateException` nếu không tin.
3. **Server certificate do ai chọn và ai kiểm?** Server key manager chọn; client trust manager kiểm.
4. **Trong mTLS, ai chọn client certificate?** Client key manager, khi server yêu cầu client authentication.
5. **`setNeedClientAuth(true)` khác `setWantClientAuth(true)`?** Need bắt buộc client cung cấp certificate phù hợp; want có thể chấp nhận thiếu.
6. **Socket cần method alias nào?** `chooseClientAlias` và `chooseServerAlias`; engine cần `chooseEngineClientAlias`/`chooseEngineServerAlias`.
7. **Nếu tự viết extended trust decorator, vì sao phải giữ Socket/SSLEngine?** Để giữ ngữ cảnh hostname, handshake, SSLParameters và policy của delegate.
8. **`checkServerTrusted` trả `boolean` à?** Không, `void`; ném `CertificateException` khi từ chối.
9. **`getAcceptedIssuers()` trả trust decision cho một certificate cụ thể?** Không; nó trả danh sách issuer certificates theo contract, không xác minh chain hiện tại.
10. **`authType` có phải OID `serverAuth` không?** Không; đây là thông tin kiểu xác thực handshake do JSSE cung cấp.
11. **Một alias bất kỳ trong `KeyStore` có đủ để handshake không?** Không; cần private key/chain khớp, keyType, issuer, protocol và provider policy.
12. **SNI có tự xác thực quyền tenant của client không?** Không; SNI là tín hiệu chọn virtual host, không chứng minh quyền truy cập.
13. **Vì sao client cần `setEndpointIdentificationAlgorithm("HTTPS")` trong raw SSLSocket demo?** Để kiểm IP/DNS SAN của server identity.
14. **Nếu trust manager được bọc chỉ gọi `delegate.checkServerTrusted(chain,authType)` thì rủi ro nào?** Bỏ mất kiểm tra phụ thuộc socket/engine, có thể làm yếu hostname/connection checks.
15. **mTLS thành công có tự cấp quyền gọi mọi API không?** Không; phải map certificate identity sang role/permissions nghiệp vụ.
16. **Tại sao ví dụ có server-trust.p12 và client-trust.p12 riêng?** Hai phía tin đối tác **khác nhau**; server cần tin client, client cần tin server.
17. **Nếu client không chọn được alias trong mTLS, nên làm gì?** Trả `null`/handshake failure và sửa credential/issuer constraints; không gửi private key hoặc chọn tenant sai.
18. **Một factory không trả extended manager, xử lý thế nào?** Phát hiện kiểu, fail closed hoặc dùng provider/adapter được đánh giá; không cast mù.
19. **TLS 1.2 và 1.3 cùng thành công trên loopback chứng minh Internet service khác cũng hỗ trợ?** Không; kết quả chỉ trên hai local contexts đã cấu hình.
20. **Làm thế nào thêm custom restriction an toàn?** Delegate full peer validation trước, sau đó chỉ thêm điều kiện reject với `CertificateException`; không thay thế trust bằng “accept all”.

## Tài liệu chuẩn và Java API

- [X509ExtendedKeyManager, Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/X509ExtendedKeyManager.html)
- [X509ExtendedTrustManager, Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/X509ExtendedTrustManager.html)
- [X509KeyManager / X509TrustManager](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/X509KeyManager.html)
- [KeyManagerFactory / TrustManagerFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/KeyManagerFactory.html)
- [SSLContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html)
- [SSLParameters và endpoint identification](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html)
- [SSLSocket và SSLServerSocket](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSocket.html)
- [SSLEngine và handshake context](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLEngine.html)
- [ExtendedSSLSession, SNI và peer signature algorithms](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/ExtendedSSLSession.html)
- [Oracle Java 17 JSSE Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html)
- [RFC 5280: X.509 Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280.html)
- [RFC 8446: TLS 1.3](https://www.rfc-editor.org/rfc/rfc8446.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jsse-007">16.7 HttpsURLConnection, HttpClient và ranh giới cấu hình SSLContext hoặc xác minh hostname</a>

<details>
<summary>Xem chi tiết</summary>

# 16.7. HttpsURLConnection, HttpClient và ranh giới cấu hình SSLContext hoặc xác minh hostname

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt hai HTTP(S) client tích hợp JDK: **`HttpsURLConnection`** (kiểu kết nối truyền thống trong `java.net`) và **`java.net.http.HttpClient`** (API HTTP hiện đại từ Java 11, dùng được với Java 17+).
2. Chỉ ra trách nhiệm của **`SSLContext`, `KeyManagerFactory`, `TrustManagerFactory`, truststore, keystore, TLS handshake, kiểm certificate chain và kiểm hostname/SAN** trong mỗi client.
3. Hiểu **gắn một `SSLContext` tùy chỉnh không tự có nghĩa tắt hoặc thay hostname verification**; và một certificate chain hợp lệ vẫn phải khớp tên endpoint trong URL.
4. Cấu hình **trust anchors theo từng HTTP client** mà không tắt xác minh chứng thư; biết chỗ nào có thể tùy chỉnh `SSLSocketFactory` hoặc `HostnameVerifier` và chỗ nào API **không** có hook tương đương.
5. Nhận diện nguy cơ **global defaults**, trust-all managers, hostname-verifier luôn true, certificate pinning sai cách, cấu hình mTLS thiếu khóa riêng hoặc chọn nhầm danh tính.
6. Chạy lab **Java 17+** tạo root/leaf X.509 bằng `keytool`, khởi động HTTPS server cục bộ và gọi bằng cả hai API với **cùng `SSLContext` tin CA**, xác nhận cả hai nhận được phản hồi HTTPS đã được JSSE bảo vệ.
7. Thiết kế luồng kiểm tra khi gặp `SSLHandshakeException`/`PKIX path building failed`/hostname mismatch mà không “chữa lỗi” bằng cách bỏ bảo mật.

**Phạm vi:** Bài thuộc **Java Advance – security-cryptography / 16. JSSE**. Chủ đề chính là **ranh giới cấu hình TLS và xác minh hostname ở hai HTTP clients**. Đoạn lab dùng CA tự tạo để học, không thiết lập PKI production, OAuth hay authorization hoàn chỉnh.

---

## 1. Vấn đề kỹ thuật: Có `https://` chưa đủ để hiểu cấu hình bảo mật

Trong Java 17, cùng một endpoint:

```text
https://api.example.com/orders
```

có thể được gọi qua hai cách:

```java
// API truyền thống:
HttpsURLConnection connection =
        (HttpsURLConnection) new URL(url).openConnection();

// API hiện đại:
HttpClient client = HttpClient.newHttpClient();
HttpResponse<String> response =
        client.send(request, HttpResponse.BodyHandlers.ofString());
```

Đây là **các đoạn minh họa chưa độc lập**: cần import kiểu tương ứng, tạo biến `url`/`request`, xử lý checked exception và đóng/đọc response đúng cách. Phần code chạy đầy đủ ở mục 8.

Hai API đều có thể dùng **JSSE (Java Secure Socket Extension)** để thiết lập TLS, nhưng cách cấu hình và lifetime khác nhau. **Không thể suy ra hai API chia sẻ cùng trạng thái TLS hoặc cùng hooks chỉ vì cùng chạy trong một JVM.**

### 1.1. Điều gì phải được bảo đảm khi truy cập HTTPS?

Khi client kết nối đến một server HTTPS:

1. URL chỉ rõ **scheme `https`**, host, port, path, query.
2. Kết nối TLS phải thương lượng phiên bản/cipher suites phù hợp.
3. Server cung cấp chứng thư X.509 và các thành phần chain cần thiết.
4. Trust manager kiểm chain đến **trust anchor** được client chấp thuận, các ràng buộc PKIX/purpose/validity theo policy.
5. Client kiểm **hostname hoặc IP literal của endpoint dự định** khớp Subject Alternative Name (SAN) theo quy tắc HTTPS.
6. TLS handshake kiểm server thực sự sở hữu private key tương ứng, xác thực transcript/`Finished` và thiết lập traffic keys.
7. HTTP request/response chạy trên TLS channel đã được xác thực và bảo vệ record integrity/confidentiality.
8. Ứng dụng còn phải kiểm **authentication/authorization** nghiệp vụ, content-type/size, redirects, SSRF, dữ liệu nhận và key/certificate lifecycle.

**Hai phép kiểm dễ bị nhầm:** (4) *certificate chain trusted* và (5) *certificate dành cho host đang truy cập* là **hai điều kiện riêng**. Nếu bạn tạo `SSLContext` chỉ tin đúng một CA nhưng tắt hostname checking, attacker có certificate khác do CA đó ký có thể bị nhầm là đúng server theo rủi ro và policy của hệ thống.

### 1.2. Sơ đồ các lớp

```text
             URL: https://api.example.com:443/path
                               │
               HTTP client (URL, host, redirects, proxy)
                               │
              TLS configuration / SSLContext
                  ┌────────────┴────────────┐
                  │                         │
             KeyManagers              TrustManagers
       (private key của client)   (trust anchors của peer)
                  │                         │
                  └────────────┬────────────┘
                               │
                JSSE validates certificate path
                               │
             HTTPS endpoint identity / SAN check
                               │
              TLS handshake + authenticated keys
                               │
                      HTTPS request/response
                               │
                       Application policy
```

Sơ đồ trình bày **trách nhiệm logic**, không phải thứ tự từng lời gọi nội bộ của một provider. Một số kiểm tra certificate chain/hostname nằm trong cùng luồng trust manager có tham số socket/engine.

---

## 2. Bảng so sánh `HttpsURLConnection` và `HttpClient` trong Java 17

| Tiêu chí | `HttpsURLConnection` | `java.net.http.HttpClient` |
| --- | --- | --- |
| Package | `javax.net.ssl.HttpsURLConnection` (mở từ `java.net.URL`) | `java.net.http.HttpClient`, `HttpRequest`, `HttpResponse` |
| Phong cách | Mỗi connection/URL, gọi `getInputStream`, `getResponseCode`... | Client tái sử dụng, request/response kiểu builder, có `send`/`sendAsync` |
| HTTP versions | API legacy thường dùng HTTP/1.1; hành vi phụ thuộc handler | HTTP/1.1 và HTTP/2 khi hỗ trợ; chọn qua `HttpClient.Version` |
| TLS mặc định | JSSE và default SSLSocketFactory/hostname verifier theo cấu hình | JSSE và SSLContext mặc định nếu không cung cấp; HTTP stack quản lý TLS/identity |
| SSLContext riêng | Không có `connection.setSSLContext(...)`; dùng `context.getSocketFactory()` qua `setSSLSocketFactory` | `HttpClient.newBuilder().sslContext(context)` |
| Truststore riêng | Tạo trust manager/context rồi cài socket factory cho connection | Tạo trust manager/context rồi gắn vào builder của client |
| Client certificate cho mTLS | KeyManagers trong SSLContext dùng tạo socket factory | KeyManagers trong SSLContext của HttpClient |
| Hostname verification | Có hook `setHostnameVerifier` và default verifier; **không nên tắt** | **Không có public builder API `setHostnameVerifier` tương ứng**; giữ endpoint identification của HTTP client |
| Scope cấu hình | `setSSLSocketFactory`/`setHostnameVerifier` trên từng instance; `setDefault...` là các default process-wide cho future instances | `sslContext(...)`/`sslParameters(...)` gắn khi build một `HttpClient`; tái sử dụng client phù hợp |
| Connection reuse | Handler có thể keep-alive, cần đọc/đóng response đúng | Connection pooling và HTTP/2 multiplexing do client quản lý; nên tái sử dụng thay vì tạo một client/request |
| Redirects | Tùy `setInstanceFollowRedirects` và handler | `followRedirects(NORMAL/ALWAYS/NEVER)`; mặc định `NEVER` |
| Timeout | `setConnectTimeout` và `setReadTimeout` | `connectTimeout` trên client, `timeout` trên request; asynchronous cancellation theo semantics riêng |
| Asynchronous API | Chủ yếu blocking | `sendAsync` trả `CompletableFuture` |
| Tùy biến proxy | `openConnection(Proxy)` hoặc JVM handler policy | `proxy(ProxySelector)` trên builder |
| Cần lưu ý | Global defaults, instance lifetime, input/error stream, redirect & response ownership | Cấu hình client dùng chung, pooling, async, proxy/HTTP2, không dựa vào internal flags vô hiệu hostname |

**Một `SSLContext` có thể được dùng bởi cả hai API**, nhưng mỗi API vẫn giữ cấu hình HTTP/hostname/redirect/proxy của riêng nó. Không phải tất cả behavior, TLS session reuse, ALPN hoặc HTTP/2 đều giống nhau sau khi cùng cài một context.

### 2.1. Vì sao có hai APIs?

`HttpsURLConnection` là lựa chọn lâu đời: dễ áp dụng cho HTTP call đơn giản, nhiều ứng dụng Java legacy, framework cũ và proxy/URL handler truyền thống. `HttpClient` là API hiện đại: request builders, response handlers, reusable clients, async, HTTP/2 và chính sách redirect/timeouts rõ ràng.

Đừng đổi API chỉ vì tin rằng “HttpClient luôn bảo mật hơn” hoặc “HttpsURLConnection luôn bỏ kiểm hostname”. **Cả hai có thể xác minh TLS an toàn nếu cấu hình đúng; cả hai có thể bị phá nếu bạn dùng trust-all, không bảo vệ key hoặc thao tác URL/proxy sai.**

---

## 3. `SSLContext` làm gì và không làm gì?

Trong JSSE, `SSLContext` kết hợp:

- **`KeyManager[]`**: chọn private key/certificate identity của **chính phía này** khi TLS cần client authentication (với HTTP client) hoặc server authentication (với HTTPS server).
- **`TrustManager[]`**: xác minh chứng thư **phía đối tác** dựa trên trust anchors và policy.
- **`SecureRandom`**: nguồn ngẫu nhiên thích hợp cho hoạt động TLS của provider.
- Các tham số/triển khai TLS do JSSE/provider quản lý.

Khởi tạo sơ lược:

```java
SSLContext context = SSLContext.getInstance("TLS");
context.init(keyManagers, trustManagers, new SecureRandom());
```

Đây là **đoạn code minh họa chưa độc lập**, cần imports `javax.net.ssl.SSLContext`, `java.security.SecureRandom` và các biến `keyManagers`, `trustManagers` đã được cấu hình. Chữ `"TLS"` chọn engine giao thức TLS; **không có nghĩa ứng dụng tự đặt SSL version, hostname hoặc trust policy an toàn** chỉ với một dòng code.

### 3.1. `SSLContext` không tự quyết định URL đích

Trong ứng dụng HTTPS:

- URL/request xác định hostname dự định truy cập;
- context định nghĩa key/trust infrastructure;
- HTTP/TLS client quyết định cách kết hợp **hostname trong URL** với chứng thư trong handshake.

Nếu bạn dùng `SSLContext` tùy chỉnh, **đừng mặc nhiên suy ra hostname verification bị tắt**. Và cũng đừng suy ra một `SSLContext` trả về từ API là đủ để xác minh hostname nếu dùng **raw `SSLSocket`** không cấu hình endpoint identification. Ranh giới chính là **ai có hostname dự định và ai áp dụng quy tắc endpoint identity**.

### 3.2. Truststore và keystore khác nhau

```text
HTTPS client one-way:
    Truststore: CA/root hoặc server certificate được chấp nhận
    KeyStore:   thường KHÔNG cần client private key

HTTPS client dùng mTLS:
    Truststore: CA/root tin server
    KeyStore:   client private key + client cert chain

HTTPS server:
    KeyStore:   server private key + server cert chain
    Truststore: CA/root tin client nếu server yêu cầu mTLS
```

**Không copy server private key sang client truststore.** Truststore có trust anchors/certificates công khai, nhưng integrity của nó quan trọng; keystore có private material phải bảo vệ khỏi bị đọc/ghi trái phép.

`KeyManagerFactory` và `TrustManagerFactory` là hai phần khác nhau:

```java
KeyManagerFactory kmf = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm());
kmf.init(identityStore, password);

TrustManagerFactory tmf = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
tmf.init(trustStore);
```

Đây là **mã minh họa chưa độc lập** cần imports, các biến và exception handling. Với one-way HTTPS client không cần identity key, có thể truyền `null` cho key managers khi `SSLContext.init(...)`, nhưng vẫn cần trust managers hợp lệ.

### 3.3. `tmf.init(customStore)` thường thay tập trust anchors, không tự cộng thêm

Một lỗi thực tế là ứng dụng cần tin CA nội bộ và làm:

```java
TrustManagerFactory tmf = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
tmf.init(internalTrustStore);
```

Sau đó ngạc nhiên vì public HTTPS sites không còn được chấp nhận. **Khi khởi tạo từ một KeyStore chỉ có CA nội bộ, trust manager thường tin các anchors của store đó**, không tự động hợp nhất với truststore mặc định của JDK.

Các lựa chọn thiết kế:

1. **Tách client theo trust domain:** một `HttpClient` chỉ tin internal CA; một client khác dùng default trust anchors cho public Internet.
2. **Tạo truststore được quản lý chứa chính xác những roots cần tin:** merge có kiểm soát từ nguồn đáng tin, cập nhật theo lifecycle; không thêm tùy tiện mỗi CA nhận được qua mạng.
3. **Đưa ra quyết định tin cậy dựa trên policy/protocol cụ thể** nếu tổ chức yêu cầu pinning hoặc custom PKIX, ưu tiên thư viện/provider đã được đánh giá.

**Đừng** tự viết một `X509TrustManager` “thử tất cả, hễ một cái không lỗi thì tin” nếu không hiểu endpoint/algorithm constraints, exception handling và trust scope. Một custom trust manager có thể vô tình bỏ hostname/purpose checks.

### 3.4. Default SSLContext và JVM system properties

Khi không cài custom context, JSSE có thể dùng truststore/JDK default configuration (ví dụ `cacerts`, property `javax.net.ssl.trustStore` và truststore password/type, provider chính sách). **Đừng giả định mọi JVM/container có cùng tập root CA**: JDK distribution, image, hệ điều hành và enterprise policies có thể khác.

Lệnh minh họa cấu hình **truststore riêng cho process Java**:

```shell
java -Djavax.net.ssl.trustStore=truststore.p12 -Djavax.net.ssl.trustStoreType=PKCS12 -Djavax.net.ssl.trustStorePassword=changeit YourApp
```

Lệnh chỉ có tác dụng nếu class `YourApp` tồn tại, dùng JSSE default trust managers tương ứng và chạy trong thư mục có file. Đây **không phải lab chính**; lab chính truyền `SSLContext` rõ ràng. Tránh đặt password thật trên command line hoặc config công khai.

**Quan trọng:** một code path đã tạo `SSLContext` với custom `TrustManagerFactory` và truyền nó vào client **không nhất thiết bị override** bởi JVM `javax.net.ssl.trustStore`. Phân biệt **default context của JVM** với **explicit context per client** khi debug.

---

## 4. Hostname verification là một phép kiểm danh tính riêng

Certificate của `api.example.com` thường có:

```text
Subject: CN=Example API
Subject Alternative Name:
  DNS:api.example.com
  DNS:orders.example.com
Issuer: Trusted CA / Intermediate CA
Public key: ...
```

Giả sử certificate đó có chain hợp lệ tới CA trong truststore. Khi client gọi `https://admin.example.com`, chain vẫn có thể hợp lệ nhưng **hostname không khớp SAN**. Một HTTPS client an toàn **phải từ chối**.

### 4.1. Hai điều kiện AND, không phải OR

```text
            Server certificate
                │
        ┌───────┴───────────┐
        ▼                   ▼
  PKIX trust path      Endpoint identity
  / trusted CA         / hostname or IP SAN
        │                   │
        └───────── AND ─────┘
                     │
               Peer accepted
             (subject to other
               TLS policies)
```

`Certificate chain valid` chỉ giúp trả lời “certificate đã được một chuỗi issuer hợp lệ từ trust anchor chấp thuận cấp”. `Hostname valid` trả lời “certificate có dùng được cho **tên đích mà ứng dụng yêu cầu** không?”. Hai bên không nên thỏa hiệp.

Trong HTTPS hiện đại, kiểm tên dựa trên **SAN**, không “lấy CN substring cho khớp”. Nếu URL dùng IP literal, cần SAN loại **iPAddress**, không coi `DNS:127.0.0.1` là tương đương. Với Unicode domain/IDNA, wildcard và nhiều loại identifiers, **dùng endpoint identity implementation chuẩn** thay vì `String.contains`/regex tự chế.

### 4.2. `HostnameVerifier` của `HttpsURLConnection`

`HttpsURLConnection` có:

```java
HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
connection.setSSLSocketFactory(sslContext.getSocketFactory());
// Không gọi setHostnameVerifier(...) khi dùng verification chuẩn.
```

Đây là minh họa, biến `url` và `sslContext` phải được tạo trước. **Giữ verifier mặc định** cho HTTPS trừ khi bạn thực sự đang triển khai một chính sách identity thay thế đã được kiểm chứng; việc cung cấp custom verifier rất dễ làm hostname validation sai.

Một số API có `setDefaultHostnameVerifier(...)` ở phạm vi default toàn JVM/handler, có thể ảnh hưởng các kết nối tạo sau đó. Không coi thao tác global như lựa chọn “chỉ thay một request”. Tránh phụ thuộc hành vi global mutable defaults trong service đa luồng hoặc nhiều tenant.

#### Anti-pattern cực kỳ nguy hiểm

```java
// SAI: bypass hostname verification!
connection.setHostnameVerifier((hostname, session) -> true);
```

Kẻ tấn công có thể cung cấp chứng thư hợp lệ cho **tên khác**, hoặc kết hợp cấu hình trust quá rộng để MITM. Nếu gặp hostname mismatch, hãy sửa URL/certificate SAN hoặc PKI issuance; **không** đổi verifier thành always-true.

### 4.3. `HttpClient` không có `setHostnameVerifier` công khai

`java.net.http.HttpClient` cho phép:

```java
HttpClient client = HttpClient.newBuilder()
        .sslContext(sslContext)
        .build();
```

Đây là minh họa, cần `import java.net.http.HttpClient;` và biến `sslContext`. Trong cách dùng chuẩn, HTTP client thực hiện HTTPS endpoint identification trong quá trình TLS theo chính sách của implementation. **Không có `HttpClient.Builder.hostnameVerifier(...)`** để áp callback tương tự `HttpsURLConnection.setHostnameVerifier`.

`HttpClient.Builder.sslParameters(SSLParameters)` có thể điều chỉnh các tham số TLS được hỗ trợ; không phải hook hostname verifier dành riêng cho một URL. Các tham số không được implementation cho phép có thể bị bỏ qua, phụ thuộc JDK/provider. **Không dựa vào `sslParameters` hay internal JVM flags để vô hiệu hóa kiểm hostname**.

Cũng không nên dùng property nội bộ dạng `jdk.internal.httpclient.disableHostnameVerification` để sửa lỗi. Đây không phải thiết kế bảo mật hoặc public API ổn định; việc tắt endpoint identity gây rủi ro MITM.

### 4.4. Raw `SSLSocket` lại là câu chuyện khác

Nếu tự dùng `SSLSocket` thay vì HTTP client:

```java
SSLSocket socket = (SSLSocket)
        sslContext.getSocketFactory().createSocket("api.example.com", 443);
SSLParameters params = socket.getSSLParameters();
params.setEndpointIdentificationAlgorithm("HTTPS");
socket.setSSLParameters(params);
socket.startHandshake();
```

Đây **không phải chương trình độc lập**. Đừng mặc định raw `SSLSocket` tự kiểm tên URL khi ứng dụng chưa chỉ định algorithm; raw socket API không biết HTTP host semantics ở cùng mức như HTTP client. Khi dùng `HttpClient`/`HttpsURLConnection`, hãy **giữ** cơ chế hostname verification chuẩn của chính client.

---

## 5. Cấu hình `HttpsURLConnection`: scope cụ thể và lỗi global defaults

Quy trình sử dụng phổ biến:

1. Tạo `URL` bắt đầu bằng `https://`.
2. Tạo `HttpsURLConnection` từ `url.openConnection()`.
3. Trước khi kết nối, gọi `connection.setSSLSocketFactory(sslContext.getSocketFactory())`.
4. Đặt timeout, HTTP headers/verb cần thiết.
5. Đọc `getResponseCode()` và response/error streams đúng, sau đó đóng.
6. Gọi `disconnect()` khi không cần connection nữa; lưu ý underlying HTTP keep-alive có quy tắc pooling của handler.

```java
HttpsURLConnection connection =
        (HttpsURLConnection) new URL("https://api.example.com").openConnection();
connection.setSSLSocketFactory(sslContext.getSocketFactory());
connection.setConnectTimeout(5000);
connection.setReadTimeout(5000);
try {
    int status = connection.getResponseCode();
    // Đọc đúng input/error stream và xử lý status.
} finally {
    connection.disconnect();
}
```

Đây là **đoạn minh họa chưa độc lập**, cần imports, `sslContext` và exception handling. **Không gắn một `HostnameVerifier` không kiểm chứng**.

### 5.1. Phạm vi instance so với default global

| Cách gọi | Phạm vi ý nghĩa |
| --- | --- |
| `connection.setSSLSocketFactory(factory)` | Factory cho đối tượng `HttpsURLConnection` này, trước lúc connect |
| `connection.setHostnameVerifier(verifier)` | Policy callback hostname cho instance này; không dùng để bypass |
| `HttpsURLConnection.setDefaultSSLSocketFactory(factory)` | Thay default factory cho các instance về sau theo hành vi API; ảnh hưởng các phần khác JVM |
| `HttpsURLConnection.setDefaultHostnameVerifier(verifier)` | Thay default verifier cho các instance về sau; có thể phá identity checking trên diện rộng |
| `SSLContext.setDefault(context)` | Đổi default SSLContext JVM; không coi là thiết lập riêng một URL |

Trong ứng dụng web nhiều threads/tenants/libraries, **không đổi global defaults khi xử lý một request**. Thread khác có thể mở kết nối trong lúc defaults đã bị đổi; các clients tạo trước đó có thể còn giữ factory cũ. Chọn **per-connection factory** hoặc client được xây riêng cho trust domain.

### 5.2. Giới hạn và đặc điểm legacy API

- Response code/status, input vs error stream cần xử lý rõ; `getInputStream` có thể ném exception cho HTTP error statuses.
- Timeouts/caching/redirects và keep-alive do `HttpURLConnection`/handler quản lý. Đừng coi một instance như một client immutable dùng song song vô hạn.
- Không làm IO/blocking lâu trong event-loop quan trọng.
- Mỗi lần gọi `openConnection` là một đối tượng connection mới; underlying network connection có thể được reuse theo policy, vì vậy không tự suy luận một handshake mới cho mỗi HTTP request.
- Khi redirect từ host A sang B, phải đánh giá security policy: credentials/authorization headers, host allowlist và TLS identity của **host đích**; truststore không tự kiểm rủi ro SSRF/open redirect.

---

## 6. Cấu hình `HttpClient`: client reusable, TLS context theo client

Trong Java 17, cách sử dụng hiện đại:

```java
HttpClient client = HttpClient.newBuilder()
        .sslContext(customSslContext)
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .version(HttpClient.Version.HTTP_1_1)
        .build();

HttpRequest request = HttpRequest.newBuilder(
        URI.create("https://api.example.com/orders"))
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build();

HttpResponse<String> response =
        client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
```

Đây là **đoạn minh họa chưa độc lập**: imports `java.net.http.*`, `java.net.URI`, `java.time.Duration`, `java.nio.charset.StandardCharsets`, biến `customSslContext` và exception handling cần được cung cấp. Bản chạy hoàn chỉnh nằm ở mục 8.

### 6.1. Vì sao nên reuse `HttpClient`?

`HttpClient` được thiết kế để sử dụng qua nhiều requests: quản lý kết nối, HTTP/2 streams, threads và TLS sessions nội bộ. Tạo một `HttpClient` mới cho mỗi HTTP request có thể làm mất cơ hội reuse và tăng chi phí handshake/connection setup. **Nhưng reuse phải theo trust domain**: nếu tenant A dùng CA riêng, không nên bỏ CA đó vào một client tin tất cả tenants chỉ để tái sử dụng.

Một thiết kế service:

```text
ClientRegistry
 ├─ publicApiClient   : default/public trust policy
 ├─ internalApiClient : enterprise CA truststore
 └─ partnerClient     : partner CA + client identity (mTLS)

Each client:
  SSLContext fixed when built
  connection pool and HTTP protocol policy
  URL/host allowlist + request authorization policy
```

Dùng URL trong `HttpRequest` quyết định endpoint. `HttpClient` được build với một `SSLContext` cụ thể; muốn thay truststore/certificate rotation, tạo client/context mới theo cơ chế có kiểm soát và migrate/close các session liên quan theo lifecycle của JDK/framework. Không giả định sửa file `truststore.p12` trên disk làm các HttpClient đang chạy lập tức dùng key/trust anchors mới.

### 6.2. `sslParameters(...)` dùng để làm gì?

`HttpClient.Builder.sslParameters(SSLParameters)` cho phép đặt một tập SSL/TLS parameters, ví dụ protocols/cipher suites theo policy nếu provider/implementation chấp nhận. Tuy nhiên, theo Java API, **một số tham số có thể không được phép hoặc bị bỏ qua** trong ngữ cảnh HttpClient.

Đừng viết một `SSLParameters` với endpoint identification rỗng để “không kiểm hostname”. Khi cần hostname verification khác chuẩn (như workload URI identity), hãy chọn giao thức/HTTP stack và lớp kiểm chứng identity được hỗ trợ, thay vì bypass TLS rồi tự so chuỗi một cách thiếu an toàn.

### 6.3. Redirect, async và proxy là trách nhiệm khác TLS trust

- Mặc định `HttpClient.Redirect.NEVER`; nếu cho phép redirect, yêu cầu có thể đi tới host khác, và **URL mới phải được đánh giá** theo policy/allowlist. Một SSLContext tin nhiều CA không tự ngăn bị chuyển tới endpoint ngoài dự kiến.
- `sendAsync` trả `CompletableFuture`; exception có thể được wrap trong `CompletionException`; không nuốt lỗi TLS rồi coi request thành công.
- Proxy có thể dùng `ProxySelector`; nếu proxy chấm dứt TLS (gateway/SSL inspection), chứng thư mà JSSE quan sát có thể do CA doanh nghiệp cấp. Chỉ tin CA đó khi policy tổ chức có căn cứ; **không tắt kiểm certificate** chỉ vì bị proxy.
- HTTP/2/ALPN không thay nhiệm vụ xác minh certificate chain và tên endpoint.
- Đọc body stream/response đúng hạn mức; `HttpClient` có resource lifecycle và body handler khác với legacy `getInputStream`.

### 6.4. `HttpClient.sslContext()` chỉ cho biết context, không phải identity đã được kiểm

`client.sslContext()` trả về cấu hình context sử dụng bởi client. Một `SSLContext` object có thể được tạo với trust-all manager sai. **Không kiểm chứng bảo mật bằng cách chỉ assert client.sslContext()!=null**. Cần integration test với CA không trusted, host không khớp SAN, cert hết hạn và policy clientAuth phù hợp.

---

## 7. Cùng một truststore nhưng kết quả vẫn có thể khác

Giả sử hai HTTP clients cùng dùng `sslContext` tin root CA nội bộ. Vì sao kết quả vẫn khác nhau?

| Khác biệt | Ví dụ | Cách kiểm |
| --- | --- | --- |
| URL/hostname | Legacy gọi `localhost`, modern gọi `127.0.0.1` | SAN DNS vs SAN iPAddress |
| Proxy | Một client dùng proxy doanh nghiệp, client kia direct | Chứng thư nhìn thấy ở TLS endpoint, proxy policy |
| Redirect | Một client follow redirect sang host khác | Host mới, credentials transfer, trust policy |
| TLS protocols/ciphers | Một bên ép TLS 1.3, bên kia provider chọn TLS 1.2 | Enabled protocols, JSSE debug |
| Client identity | Một context có `KeyManager`, context kia không | mTLS `CertificateRequest`, alias, EKU |
| Trust anchors | Client mới được build sau rotation, client cũ giữ context trước | Keystore reload/rebuild, truststore inventory |
| Connection pool | Reused connection/session vs full handshake mới | TLS session identity, resumption và log |
| Hostname policy | Legacy custom `HostnameVerifier`, modern default strict | Loại bỏ unsafe override; kiểm SAN và endpoint |
| HTTP version | HTTP/1.1 vs HTTP/2, ALPN | Request format/network behavior, **không phải lý do tắt TLS** |
| JVM configuration | System properties, security providers hoặc internal flags khác | Effective runtime, process configuration, build/deployment |

**Thứ tự debug hợp lý:** kiểm URL/port/SAN → trust chain/root → key usage/EKU → SSLContext & key managers → TLS protocol/proxy → HTTP redirect/body. Đừng đổi cả trust manager và hostname verifier cùng lúc; khi đó bạn không còn biết lỗi thật từ đâu.

---

## 8. Lab Java 17+: HTTPS server cục bộ + hai HTTP clients

Lab sử dụng **training root CA** ký chứng thư `localhost`, rồi hai clients cùng tin root qua **`truststore.p12`**. Cả hai truy cập **một HTTPS endpoint thực** trong JVM, giữ hostname checking chuẩn của JDK.

**Tài sản:**

- `ca.p12`: chứa private key của training CA, **không đưa vào ứng dụng client**.
- `ca.pem`: public root CA certificate.
- `server.p12`: server private key + leaf certificate `DNS:localhost` + chain.
- `truststore.p12`: **chỉ chứa public CA certificate được tin**, không có private key.
- `HttpsClientsLab.java`: bắt đầu HTTPS server cục bộ, chạy `HttpsURLConnection` và `HttpClient` với cùng `SSLContext` cho client.

**Bảo mật lab:** mật khẩu `changeit` và private CA key chỉ phục vụ học; không dùng production. Chạy trong **thư mục lab trống**, không ghi đè keystore thật, không thêm root demo vào `cacerts` hệ thống. Lệnh để password trên command-line chỉ dành cho lab; production không để password lộ qua shell history/process list.

### 8.1. Sinh root CA giả lập

```shell
keytool -genkeypair -alias training-ca -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=HTTPS Lab Root CA, O=Training, C=VN" -validity 3650 -ext "BC:critical=ca:true,pathlen:0" -ext "KU:critical=keyCertSign,cRLSign" -keystore ca.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt
```

Export CA certificate công khai:

```shell
keytool -exportcert -alias training-ca -keystore ca.p12 -storepass changeit -file ca.pem -rfc
```

**Không dùng CA certificate chính nó để làm server certificate.** Trong lab này root ký trực tiếp leaf (path length không có intermediate). Một hệ thống thực có thể dùng intermediate CA, HSM và issuance policy cụ thể.

### 8.2. Tạo server private key và CSR cho `localhost`

```shell
keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=localhost, O=Training, C=VN" -validity 365 -keystore server.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt
keytool -certreq -alias localhost -keystore server.p12 -storepass changeit -file localhost.csr -ext "SAN=dns:localhost"
```

CSR xin chứng thư gắn public key của server với `localhost`, nhưng **chưa phải certificate tin cậy**; CA sẽ cấp leaf theo policy.

### 8.3. Root ký server leaf với SAN và EKU chính xác

```shell
keytool -gencert -alias training-ca -keystore ca.p12 -storepass changeit -infile localhost.csr -outfile server.pem -rfc -validity 30 -ext "SAN=dns:localhost" -ext "KU:critical=digitalSignature" -ext "EKU=serverAuth" -ext "BC=ca:false"
```

Import CA rồi certificate reply vào server keystore, bảo đảm private-key entry có chain:

```shell
keytool -importcert -alias training-ca -file ca.pem -keystore server.p12 -storepass changeit -noprompt
keytool -importcert -alias localhost -file server.pem -keystore server.p12 -storepass changeit -noprompt
```

`server.p12` giữ **server private key**; `server.pem` chỉ là bản chứng thư công khai, không bao gồm khóa riêng.

### 8.4. Client tin root trong truststore riêng

```shell
keytool -importcert -alias training-ca -file ca.pem -keystore truststore.p12 -storetype PKCS12 -storepass changeit -noprompt
```

Đây là **trust decision** của client trong lab. Không nhập root này vào truststore toàn JVM của hệ thống. Nếu dùng root lạ từ kênh không xác thực, attacker có thể khiến client tin chứng thư giả.

Kiểm các trường mục đích trước khi chạy:

```shell
keytool -printcert -file server.pem
keytool -list -v -keystore server.p12 -storetype PKCS12 -storepass changeit
keytool -list -v -keystore truststore.p12 -storetype PKCS12 -storepass changeit
```

Chờ thấy `SAN=dns:localhost`, `ExtendedKeyUsage=serverAuth`, server `PrivateKeyEntry` và root `trustedCertEntry`; serial, fingerprints và timestamps thay đổi, **không có stdout cố định cho keytool**.

### 8.5. Chương trình Java hoàn chỉnh

Tạo file **`HttpsClientsLab.java`** trong cùng thư mục với hai file `server.p12` và `truststore.p12`:

```java
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class HttpsClientsLab {
    private static final char[] LAB_PASSWORD =
            "changeit".toCharArray(); // DEMO ONLY

    private static KeyStore load(String path) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            store.load(in, LAB_PASSWORD);
        }
        return store;
    }

    private static SSLContext context(String ownKeyStore)
            throws Exception {
        KeyManager[] keys = null; // HTTPS client one-way: no client cert.
        if (ownKeyStore != null) {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(load(ownKeyStore), LAB_PASSWORD);
            keys = kmf.getKeyManagers();
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(load("truststore.p12")); // Explicit trusted CA(s).

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys, tmf.getTrustManagers(),
                new SecureRandom());
        return context;
    }

    private static String legacyGet(String endpoint,
            SSLContext tlsContext) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection)
                new URL(endpoint).openConnection();
        connection.setSSLSocketFactory(
                tlsContext.getSocketFactory());
        // No unsafe custom HostnameVerifier: HTTPS name checked normally.
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try {
            int status = connection.getResponseCode();
            if (status != 200) {
                throw new IllegalStateException(
                        "Legacy HTTP status: " + status);
            }
            try (InputStream in = connection.getInputStream()) {
                return new String(in.readAllBytes(),
                        StandardCharsets.UTF_8).trim();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String modernGet(String endpoint,
            SSLContext tlsContext) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .sslContext(tlsContext)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        HttpRequest request = HttpRequest.newBuilder(
                URI.create(endpoint))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Modern HTTP status: " + response.statusCode());
        }
        return response.body().trim();
    }

    public static void main(String[] args) throws Exception {
        SSLContext serverContext = context("server.p12");
        SSLContext clientContext = context(null);

        HttpsServer server = HttpsServer.create(
                new InetSocketAddress("localhost", 0), 0);
        server.setHttpsConfigurator(
                new HttpsConfigurator(serverContext));
        server.createContext("/hello", exchange -> {
            byte[] body = "OK\n".getBytes(StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().set(
                        "Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream output =
                        exchange.getResponseBody()) {
                    output.write(body);
                }
            } finally {
                exchange.close();
            }
        });

        server.start();
        try {
            int port = server.getAddress().getPort();
            String endpoint =
                    "https://localhost:" + port + "/hello";
            String legacy = legacyGet(endpoint, clientContext);
            String modern = modernGet(endpoint, clientContext);

            System.out.println("legacyBody=" + legacy);
            System.out.println("modernBody=" + modern);
            System.out.println(
                    "bothReadSamePayload=" + legacy.equals(modern));
            System.out.println(
                    "separateKeyAndTrustManagers=true");
        } finally {
            server.stop(0);
        }
    }
}
```

**Tại sao đây là ví dụ đúng ranh giới?**

- HTTPS server có **server private key** từ `server.p12` qua `KeyManagerFactory`.
- Cả hai HTTP clients có **truststore chứa CA** qua `TrustManagerFactory`, không có private-key identity (vì one-way HTTPS).
- `HttpsURLConnection` cài context bằng `setSSLSocketFactory` trên **instance**, không đổi global default; giữ hostname verifier mặc định.
- `HttpClient` nhận `sslContext` ở **builder**, không có custom verifier; cả hai gọi URL `https://localhost:port/hello` khớp `SAN DNS:localhost`.
- Server listen cổng tự động (port `0`) để tránh xung đột với dịch vụ đang chạy; chỉ truy cập endpoint cục bộ trong lab.
- Cả hai request được đọc qua JSSE HTTPS channel; chương trình **không tự thực hiện thủ công TLS handshake hoặc AES**.
- Chỉ in các giá trị application-level cố định, **không in private keys, truststore password, certificate fingerprint hay session secrets**.

### 8.6. Biên dịch và chạy Java 17+

Module `jdk.httpserver` chứa `com.sun.net.httpserver.HttpsServer` và `HttpsConfigurator`. Dùng cờ explicit để module được resolve cả khi biên dịch và chạy:

```shell
javac --add-modules jdk.httpserver HttpsClientsLab.java
java --add-modules jdk.httpserver HttpsClientsLab
```

**Stdout kỳ vọng** khi đã tạo keystore/truststore từ bước 8.1–8.4, chạy trong thư mục lab với một JDK 17 thông dụng hỗ trợ RSA/PKCS12/JSSE:

```text
legacyBody=OK
modernBody=OK
bothReadSamePayload=true
separateKeyAndTrustManagers=true
```

Giá trị serial/fingerprint và TLS session keys ngẫu nhiên không được in, vì vậy stdout của chương trình không phụ thuộc chúng. Nếu không tìm thấy `server.p12`, `truststore.p12`, sai password, thiếu private key/chain hoặc root không tin cậy, chương trình **không** in đủ kết quả trên mà báo exception; đó là một kiểm tra bảo mật hữu ích, không phải lý do tắt TLS.

### 8.7. Điều gì lab cố ý chưa minh họa?

- Đây là HTTPS server học tập chạy cục bộ, **không phải production web server**. Không có auth/token, IAM, quota, rate limiting, request validation, nhiều handlers, monitoring, revocation/OCSP infrastructure, certificate rotation, HSM/KMS.
- Chưa có client certificate/mTLS. Để làm mTLS cần bổ sung **client private key/certificate chain** và cấu hình server yêu cầu client certificate, truststore cho client CA; không chỉ gọi `setNeedClientAuth(true)` mà không cài keystore/trust managers.
- Chương trình không có negative tests làm thay đổi hostname: default behavior được giữ, còn thử negative test có hướng dẫn ở mục 9.
- `HttpsServer` dùng default TLS parameters từ `HttpsConfigurator`. Production cần kiểm protocol/cipher suites, key/certificate lifecycle và resource controls theo policy.
- Cấu hình một truststore root CA riêng trong lab có nghĩa **không mặc định tin toàn bộ public Web PKI**; đây là scope có chủ ý.

---

## 9. Các bài thử thất bại để xác thực ranh giới

Chỉ chạy trên **bộ certificate/truststore của lab**, không sửa truststore production hoặc hệ thống. Kiểm outcome và exception type/chuỗi chứng thư thực tế, **không so khớp toàn bộ exception message** vì thay đổi theo JDK/provider/OS.

### 9.1. Test 1 – Certificate chain không được tin

Tạo `untrusted.p12` không chứa `training-ca` hoặc CA ký server leaf. Tạo một `SSLContext` mới từ store đó rồi dùng cho từng API. Kết nối sẽ bị từ chối nếu peer không thể xây đường tin cậy đến trust anchor; lỗi thường có `SSLHandshakeException`, `PKIX path building failed` hoặc `unable to find valid certification path`.

**Không chữa bằng trust-all:** cần nhập đúng root CA đã được phê duyệt vào truststore theo kênh đáng tin và giữ certificate chain hợp lệ.

### 9.2. Test 2 – Hostname không khớp SAN

Server certificate chỉ có `SAN DNS:localhost`. Nếu server vẫn có thể được kết nối tại `127.0.0.1` nhưng request URL là `https://127.0.0.1:port/hello`, HTTPS endpoint identification phải kiểm SAN loại `iPAddress` và từ chối khi certificate **không** có `IP:127.0.0.1`.

**Chú ý:** môi trường có thể bind `localhost` tới IPv6 `::1`, IPv4 `127.0.0.1` hoặc hosts settings khác; nếu không thể connect qua IP literal thì đó là **lỗi kết nối**, không chứng minh đã chạy hostname check. Để test chắc chắn, cấu hình test server listen đúng interface và certificate SAN/connection rõ, sau đó kiểm exception liên quan tên.

Không giải quyết bằng `connection.setHostnameVerifier((h,s) -> true)`. Với client cần gọi bằng IP literal, certificate phải có IP SAN đúng, hoặc dùng DNS hostname được chứng thực.

### 9.3. Test 3 – Đổi server certificate nhưng giữ root CA đáng tin

Tạo leaf mới do **cùng root CA** ký, nhưng SAN chỉ chứa `DNS:other.example`. Chain vẫn có thể hợp lệ tới trust anchor, nhưng request `https://localhost` **phải bị từ chối** do identity mismatch.

Bài test này chứng minh **truststore tin CA không phải danh sách những host tự động được chấp nhận**. Một CA có thể cấp nhiều certificates cho nhiều names; client kiểm đúng name cho từng URL.

### 9.4. Test 4 – Client mTLS thiếu private key

Nếu server yêu cầu `clientAuth`, chỉ nhập public client certificate vào client truststore không làm client có khả năng chứng minh sở hữu private key. Client phải có **`PrivateKeyEntry` với cert chain** trong keystore, `KeyManagerFactory` nạp entry phù hợp và server phải tin client CA. Không dùng `clientAuth` cert làm serverAuth tùy tiện.

### 9.5. Test 5 – Mối nguy global defaults

Một JVM có service A tin CA đối tác A và service B tin CA đối tác B. Nếu service A gọi `HttpsURLConnection.setDefaultSSLSocketFactory(factoryA)` ở startup hoặc trong một request, những kết nối khác tạo sau đó có thể vô tình dùng trust configuration A.

**Thiết kế đúng:** tạo `SSLContext` theo trust domain, dùng `setSSLSocketFactory` cho từng legacy connection hoặc `HttpClient.Builder.sslContext` cho reusable client. Cấu hình global chỉ nên áp dụng khi **toàn process thật sự có cùng policy** và được quản lý như deployment config.

---

## 10. mTLS trên cả hai APIs: cấu hình nào thay đổi?

Trong **one-way HTTPS**, client cần trust manager để xác minh server, nhưng không bắt buộc có client private key. Trong **mTLS**, client phải có private key/certificate chain, server phải yêu cầu chứng thư và xác thực client.

Tương tự một phương thức khởi tạo context trong lab:

```java
SSLContext clientTls = context("client.p12");
```

Đây **chỉ là đoạn minh họa**: `client.p12` phải tồn tại, có `PrivateKeyEntry` đúng EKU `clientAuth` và CA của server phải được tin ở truststore. `context(...)` trong lab cũng khởi tạo `TrustManagerFactory` để **client tiếp tục kiểm server**. Bản thân dòng này **không ép server yêu cầu clientAuth**; server phải được cấu hình tương ứng.

### 10.1. `HttpsURLConnection` với mTLS

```java
connection.setSSLSocketFactory(clientTls.getSocketFactory());
```

Đây là phần cài đặt **identity/trust context** trên legacy connection. Khi server gửi `CertificateRequest` trong handshake, JSSE key manager chọn client certificate phù hợp để trình bày. Không nhầm client keystore với truststore server.

### 10.2. `HttpClient` với mTLS

```java
HttpClient mtlsClient = HttpClient.newBuilder()
        .sslContext(clientTls)
        .build();
```

Đây là phần cài đặt context ở builder. mTLS hoạt động nếu server thực sự yêu cầu và hai phía có key/certificate/trust chains đúng policy. Không có `HttpClient.Builder.clientCertificate(...)` chuẩn để thay thế toàn bộ `KeyManagerFactory` trong một bước đơn giản.

### 10.3. Chọn client certificate không chỉ là “có file”

Nếu keystore có nhiều aliases/certificates, `X509KeyManager`/`X509ExtendedKeyManager` có thể chọn dựa trên key type, CA hints, signature algorithms, TLS version và provider. Một alias có `clientAuth` sai, private key không khớp chain hoặc identity chưa được server chấp thuận sẽ làm handshake thất bại. Tùy framework/JDK, có thể cần policy chọn alias được kiểm soát; **không bỏ trust/hostname validation để sửa lỗi chọn key**.

### 10.4. mTLS và authorization

Dù client certificate được server xác thực thành công, server vẫn phải map identity (SAN URI, DNS, Subject DN theo CA policy) thành principal rồi kiểm **role, tenant, object và action**. Có client certificate không tự có quyền `admin`. Nếu mTLS kết thúc ở proxy/service mesh, ứng dụng phải tin đúng nguồn của forwarded principal, chặn client tự gửi header mạo danh.

---

## 11. Lỗi thường gặp và cách chẩn đoán

| Biểu hiện | Vùng nguyên nhân | Việc cần làm |
| --- | --- | --- |
| `PKIX path building failed` | Truststore thiếu root, chain thiếu intermediate, CA không được phép | Kiểm trust anchors và chain ở **phía client**; nhập qua kênh có thẩm quyền |
| `No subject alternative names matching IP address` | URL dùng IP nhưng cert chỉ có DNS SAN | Cấp IP SAN đúng hoặc dùng DNS hostname được chứng nhận |
| `No name matching ... found` | DNS SAN không khớp URL hostname | Kiểm URL/SAN/certificate issuance, không tắt hostname verifier |
| `certificate_unknown`/`bad_certificate` | Peer không chấp nhận cert/chain/purpose | Kiểm issuer, EKU, KU, validity, protocol và trust managers |
| `certificate_required`/`Empty client certificate chain` | Server yêu cầu mTLS nhưng client không có identity key | Nạp client keystore vào KeyManagerFactory, kiểm chain/alias |
| `handshake_failure` | TLS versions/cipher/signature incompatibility hoặc authentication | Kiểm effective protocols, certificate/key types và JSSE debug |
| `UnrecoverableKeyException` | Keystore/entry password sai hoặc private key không truy xuất | Kiểm keystore path, alias, provider và secret source |
| `FileNotFoundException` | Store file không nằm trong process working directory | Dùng path explicit và kiểm deployment filesystem |
| HTTPS trả `401`/`403` sau handshake | Ứng dụng không chấp nhận token/role/tenant | Kiểm application auth, không sửa TLS một cách mù quáng |
| Legacy thành công, modern `PKIX` fail | Hai client dùng SSLContext/truststores khác, proxy khác, hoặc cert khác | Ghi context source, URL và truststore policy; đừng suy luận cùng JVM ⇒ cùng trust |
| Legacy thành công, modern báo sai hostname | Legacy có unsafe HostnameVerifier custom hoặc URL khác | Khôi phục hostname verification chuẩn, kiểm certificate SAN |
| Một HttpClient gọi nhiều domains; cần CA riêng từng domain | Trust scope quá rộng hoặc thiếu cấu hình | Dùng clients riêng theo trust domain / policy |
| Sau CA rotation vẫn dùng chứng thư cũ | Context/client đã build trước, connections có session reuse | Rebuild/reload có kiểm soát và cập nhật trust store |
| Hệ thống hoạt động khi tắt validation | Config hoặc certificate thực tế sai; test che rủi ro MITM | Không đưa bypass vào production; sửa nguyên nhân gốc |
| Client tự theo redirect đến host ngoài allowlist | Redirect policy/SSRF/credential forwarding | Disable hoặc kiểm redirect destinations, secrets và host policy |
| Proxy chuyển TLS certificate | TLS termination/enterprise inspection | Xác định chính sách proxy/CA được tổ chức chấp thuận |

### 11.1. Debug JSSE có kiểm soát

Có thể bật debug trong môi trường lab:

```shell
java -Djavax.net.debug=ssl,handshake,trustmanager --add-modules jdk.httpserver HttpsClientsLab
```

Lệnh tạo nhiều debug lines về handshake, trust manager và certificate chain; stdout/stderr **không cố định** theo JDK/provider. **Không bật debug nhạy cảm dài hạn ở production**, không đưa private key, token, plaintext hoặc dữ liệu định danh ngoài phạm vi audit vào logs.

Một checklist debug thực tế:

1. Ghi chính xác URL/hostname/port mà mỗi API đang sử dụng.
2. Xác định có proxy/redirect/TLS termination nào thay endpoint thật.
3. Kiểm server leaf `SAN`, time validity, EKU `serverAuth` và intermediates.
4. Xác định trust manager đã load **root CA nào**, từ default store hay custom store.
5. Kiểm client identity `KeyManager` khi mTLS.
6. Kiểm provider/JDK TLS version/cipher suites, `jdk.tls.disabledAlgorithms`, TLS session reuse.
7. Nếu certificate trusted nhưng host sai, **sửa host/SAN**, không đổi trust manager.
8. Nếu host đúng nhưng chain không trusted, **sửa truststore/chain**, không đổi hostname verifier.

### 11.2. Chữ ký chứng thư đúng chưa đủ

Có thể gọi `cert.verify(issuerPublicKey)` thành công nhưng server vẫn bị từ chối vì:

- issuer public key không phải trusted CA;
- path constraints, key usage hoặc critical extensions không hợp lệ;
- cert hết hạn hoặc bị revoke;
- SAN hostname không khớp URL;
- peer không chứng minh sở hữu private key qua handshake;
- thuật toán TLS/certificate bị policy cấm.

**Đừng sửa bằng cách tự kiểm issuer signature rồi bỏ qua JSSE/HTTPS validation.** Cần duy trì toàn chuỗi kiểm tra theo protocol.

---

## 12. Các ranh giới vượt khỏi `SSLContext`

### 12.1. Certificate pinning

**Pinning** ràng buộc một certificate/public key/SPKI hoặc tập khóa được chấp thuận cho một service để giảm phạm vi tin cậy. Nhưng pinning **không được coi là lý do bỏ CA/hostname validation tùy tiện**. Phải có kế hoạch rotation, backup pins, pin set update và cơ chế recovery hợp lý để không tự khóa hệ thống khi certificate thay đổi.

`HttpClient` không có một public `HostnameVerifier` callback như legacy. Nếu tổ chức muốn pinning, ưu tiên thư viện/provider/HTTP stack hoặc custom trust logic được đánh giá đầy đủ; cần giữ **trust, identity, lifecycle** thay vì chỉ so fingerprint hay tắt hostname. Pinning thường dành cho threat model cụ thể, không nên ép tất cả APIs.

### 12.2. TLS client certificates và network identity

Một `SSLContext` với `KeyManagers` có thể cung cấp client certificate khi server yêu cầu, nhưng **không quyết định authorization**. Từ phía server, TLS xác thực principal theo PKI; ứng dụng còn phải kiểm permissions cho resource. Nếu client dùng certificate của tenant A để gọi API tenant B, authorization phải từ chối kể cả certificate chain hợp lệ.

### 12.3. SNI, IP literal và virtual hosts

Client muốn gọi `https://example.com` có thể gửi SNI `example.com`, server chọn certificate tương ứng; client vẫn kiểm SAN tên `example.com`. Nếu client kết nối một IP literal, certificate cần IP SAN tương ứng. **SNI không phải bằng chứng CA trust hoặc hostname match**; nó là input cho server virtual hosting/routing.

Không dùng một custom `SSLSocketFactory` để chuyển traffic đến IP nội bộ tùy tiện nhưng báo hostname khác, nếu không hiểu endpoint identity được JSSE sử dụng. Với reverse proxy, cần phân biệt URL host, TLS server name, Host/:authority HTTP header, certificate SAN và backend route.

### 12.4. Redirects và SSRF: trust đúng vẫn có thể gọi sai host

Một request HTTPS đến host tin cậy có thể trả `302` sang host khác. Nếu HTTP client tự theo redirect, thông tin/authorization headers, cookies hoặc data flow có thể bị ảnh hưởng. Client phải có allowlist/redirect policy, không chỉ dựa vào “TLS certificate của host đích cũng hợp lệ”. SSRF là lỗi chọn destination; certificate validation **không phải** bộ lọc host an toàn.

### 12.5. Khi làm việc qua proxy

Một HTTP proxy có thể chuyển CONNECT tunnel mà không giải mã TLS; một enterprise inspection proxy có thể **terminate/re-sign** TLS bằng CA doanh nghiệp. Client sẽ xác minh certificate mà nó **thực sự nhìn thấy**, theo trust policy của JVM/context. Không được import một CA không rõ nguồn hoặc tắt TLS verification chỉ vì kết nối qua proxy.

### 12.6. Runtime JDK và cập nhật chính sách thuật toán

Các JDK cập nhật danh sách disabled algorithms, TLS protocol defaults, root CA bundles và behavior của provider. Một cấu hình từng hoạt động có thể bị từ chối khi thuật toán/certificate yếu không còn chấp nhận. Giải pháp là nâng key/certificate và policy theo tài liệu, **không hạ mức `jdk.tls.disabledAlgorithms` tùy tiện** chỉ để pass test.

---

## 13. Câu hỏi tự kiểm tra và đáp án

**1. `SSLContext` có trực tiếp chứa URL hostname không?**  
Không. URL/request cung cấp endpoint; SSLContext cung cấp TLS key/trust infrastructure. HTTPS client/JSSE áp dụng endpoint identification phù hợp khi handshake.

**2. Tại sao certificate do CA đáng tin ký vẫn có thể bị HTTPS từ chối?**  
Vì hostname không khớp SAN, expired/revoked, EKU/KU/path constraints hoặc thuật toán không hợp lệ. “CA trusted” chỉ là một phần điều kiện.

**3. Cách gắn SSLContext riêng lên `HttpsURLConnection`?**  
Dùng `connection.setSSLSocketFactory(context.getSocketFactory())` **trước khi connect**, giữ hostname verifier chuẩn.

**4. Cách gắn SSLContext riêng lên Java 17 `HttpClient`?**  
Dùng `HttpClient.newBuilder().sslContext(context).build()`, tái sử dụng client cho trust domain tương ứng.

**5. `HttpClient.Builder` có `hostnameVerifier(...)` giống legacy không?**  
Không. Không nên dùng internal JVM flags hoặc `SSLParameters` để bypass HTTPS endpoint verification.

**6. Khi nào client cần `KeyManagerFactory`?**  
Khi HTTP client phải trình bày private-key identity, điển hình mTLS. One-way TLS thường cần trust manager, không cần client certificate.

**7. `tmf.init(internalStore)` có đương nhiên tin cả public Web PKI không?**  
Không. Trust scope phụ thuộc store/provider; một store chỉ có internal root thường chỉ tin root đó. Nếu phải tin các domain khác nhau, thiết kế clients/truststores theo policy.

**8. Vì sao không dùng `setHostnameVerifier((h,s)->true)`?**  
Nó từ bỏ kiểm endpoint identity và tạo nguy cơ chấp nhận certificate cho tên khác, tăng rủi ro MITM.

**9. Tại sao không thay `HttpsURLConnection.setDefaultSSLSocketFactory` theo từng request?**  
Đây là global default cho các kết nối mới trong JVM; gây ảnh hưởng ngoài phạm vi request và race condition với threads/clients khác.

**10. Hai client đều có SSLContext cùng truststore; tại sao một vẫn fail?**  
Có thể URL host khác, proxy/redirect khác, key managers/cert selection khác, TLS version/provider hoặc hostname verifier legacy bị custom sai. Cần so context **và** endpoint policy.

**11. Root CA mới được thêm vào truststore trên disk; HttpClient đã build có tự tin CA mới không?**  
Không nên giả định. Phải có cơ chế reload/rebuild SSLContext/HttpClient và xử lý connection/session lifecycle.

**12. Chỉ kiểm `X509Certificate.verify(caKey)` có thay HTTPS validation được không?**  
Không. Nó không tự kiểm trust anchor, PKIX path, hostname, revocation, proof-of-possession và TLS handshake integrity.

**13. Certificate `SAN DNS:localhost` có hợp lệ khi URL là `https://127.0.0.1` không?**  
Không theo kiểm IP identity chuẩn nếu thiếu SAN `iPAddress:127.0.0.1`. Tránh nhầm kết nối socket thành công với hostname verification hợp lệ.

**14. mTLS có làm client tự có quyền truy cập toàn bộ API không?**  
Không. TLS xác thực principal; IAM/ACL/tenant permissions là tầng ứng dụng.

**15. Có nên tạo `HttpClient` mới cho mọi request không?**  
Thông thường không; tái sử dụng để quản lý connection pooling và chi phí TLS, nhưng tách client theo trust domain/policy.

**16. Có cách nào chạy HTTPS khi chứng thư partner sai mà không bật trust-all?**  
Sửa nguyên nhân: cấp đúng certificate SAN/EKU, gửi đủ intermediates, dùng truststore chứa CA được phê duyệt và URL chính xác. Không dùng bảo mật giả cho production.

---

## 14. Checklist triển khai Java HTTPS an toàn

- [ ] **Chọn client API**: legacy nếu tích hợp code cũ, modern `HttpClient` khi cần reusable/async/HTTP2; biết rõ scope TLS configuration.
- [ ] **Đúng URL**: host/port/path/scheme và redirect policy; chống SSRF và credential leakage.
- [ ] **Trust anchors**: CA/cert đưa vào truststore từ nguồn đáng tin; không trust-all hoặc import CA không rõ nguồn.
- [ ] **Server certificate**: chain, validity, SAN, EKU `serverAuth`, KU/purpose, thuật toán/key size theo policy.
- [ ] **Hostname verification**: giữ HTTPS endpoint identity checking; không dùng verifier luôn true hoặc undocumented disable flags.
- [ ] **Scope**: `setSSLSocketFactory` trên instance legacy, `sslContext` trên builder modern; tránh mutation global defaults khi có nhiều clients.
- [ ] **mTLS**: nếu cần, có client `PrivateKeyEntry` + chain + KeyManagerFactory, server có trusted client CA và `needClientAuth`.
- [ ] **AuthZ**: mapping certificate identity → service principal → tenant/role/operation checks, tách khỏi TLS trust.
- [ ] **Lifecycle**: CA/root, leaf và private key có rotate/revoke/reload strategy; connection pool/resumption và long-lived sessions có chính sách.
- [ ] **Timeout/resource**: connect/read/request timeouts, HTTP status/error stream, body size, redirect và proxy controls.
- [ ] **Observability**: có log mức vừa đủ cho TLS handshake failure và certificate chain, không dump private key/password/plaintext.
- [ ] **Negative tests**: CA không tin, SAN sai host/IP, cert expired, EKU sai, thiếu intermediates, mTLS thiếu client key, redirect khác host, misconfigured proxy.
- [ ] **Production QA**: test trên đúng JDK/provider/container image và cấu hình JSSE thực tế; không lấy “demo pass” làm bằng chứng toàn bộ security policy.

**Kết luận cần nhớ:** `SSLContext` quản lý **nguồn key/trust và phần TLS engine**; **HTTPS endpoint identification** cần gắn certificate với **hostname đang gọi**. `HttpsURLConnection` cho phép cài `SSLSocketFactory` theo từng connection và có `HostnameVerifier` dễ bị lạm dụng. `HttpClient` nhận `SSLContext` ở builder và không cung cấp public hostname-verifier callback tương đương. **Sửa sai CA/hostname bằng trust-all hoặc tắt verifier là phá an ninh, không phải cấu hình đúng.**

## 15. Tài liệu tham khảo chính thống

- [Java SE 17 – HttpsURLConnection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/HttpsURLConnection.html): `setSSLSocketFactory`, `setHostnameVerifier`, các default factories/verifiers.
- [Java SE 17 – HttpClient](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html): reusable HTTP/1.1/HTTP2 client, SSLContext, request/response, async và client lifecycle.
- [Java SE 17 – HttpClient.Builder](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.Builder.html): `sslContext`, `sslParameters`, `connectTimeout`, `followRedirects` và `proxy`.
- [Java SE 17 – SSLContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html), [KeyManagerFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/KeyManagerFactory.html) và [TrustManagerFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html): tạo TLS context từ identity và peer trust.
- [Java SE 17 – SSLParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): endpoint identification/protocol/cipher parameters.
- [Java SE 17 – HttpsServer](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpsServer.html) và [HttpsConfigurator](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpsConfigurator.html): triển khai local HTTPS server cho lab.
- [Java Secure Socket Extension (JSSE) Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): TLS, truststore/keystore, PKIX, `SSLSocket` và debugging.
- [JDK 17 keytool manual](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): key pair, CSR, CA signing, import cert reply, PKCS12.
- [RFC 5280 – X.509 Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280): certificate path validation, CA constraints và SAN.
- [RFC 9525 – Service Identity in TLS](https://www.rfc-editor.org/rfc/rfc9525): HTTPS/TLS endpoint identity checks.
- [RFC 8446 – TLS 1.3](https://www.rfc-editor.org/rfc/rfc8446): handshake và server authentication.
- [OWASP Transport Layer Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html): cấu hình TLS an toàn và các anti-pattern cần tránh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jsse-008">16.8 SSLEngine với wrap, unwrap, delegated task và quản lý trạng thái handshake</a>

<details>
<summary>Xem chi tiết</summary>

# 16.8 SSLEngine với wrap, unwrap, delegated task và quản lý trạng thái handshake

`javax.net.ssl.SSLEngine` là tầng xử lý TLS/DTLS **không gắn với một socket cụ thể** của Java Secure Socket Extension (JSSE). Nó nhận/giao dữ liệu qua các `ByteBuffer` do ứng dụng quản lý; ứng dụng tự chuyển ciphertext trên TCP/transport, lên lịch đọc/ghi, chạy các tác vụ handshake được ủy quyền và bảo đảm thứ tự. API này phù hợp với server NIO, event loop, Netty-style transport, một số mô hình asynchronous I/O và chương trình muốn tách **TLS engine** khỏi **network transport**.

Trọng tâm của bài là **quản lý trạng thái thực**: `wrap()`, `unwrap()`, `getDelegatedTask()`, `SSLEngineResult.Status` và `SSLEngineResult.HandshakeStatus`. Một vòng lặp đúng phải xử lý được record đến từng phần, bộ đệm đầy, tác vụ có thể block, kết quả tiêu thụ/sinh byte bằng 0 và việc đóng TLS bằng `close_notify`. Chỉ gọi `wrap` rồi `unwrap` một lần là **không đủ** để tạo thành một kết nối TLS đáng tin cậy.

Bài dùng **Java 17+**, trình bày một demo TLS 1.3 client/server thật bằng hai `SSLEngine` nối với nhau bằng hàng đợi byte trong bộ nhớ, không cần mở cổng mạng. Demo này giúp quan sát đúng handshake, chứng thư và mã hóa, nhưng **cố ý không phải bản triển khai NIO production**: bộ đệm lớn, transport không phân mảnh và delegated tasks chạy đồng bộ để dễ kiểm tra. Phần sau giải thích chính xác những việc phải bổ sung khi chuyển sang `SocketChannel`/`Selector`.

## 1. `SSLEngine` nằm ở đâu trong JSSE?

Sơ đồ từ góc nhìn **một** endpoint:

~~~text
                 ỨNG DỤNG                          NETWORK / TRANSPORT
                  (plaintext)                         (TLS records)
                      |                                     ^
                      v                                     |
              outboundApp ByteBuffer                       |
                      |                                     |
                      v                                     |
             SSLEngine.wrap(appSrc, netDst) ----------------+
                      ^
                      |
   local TLS state: handshake, certificate, session,
   key schedule, traffic keys, record encryption/decryption
                      |
                      v
             SSLEngine.unwrap(netSrc, appDst) <------------+
                      |                                     ^
                      v                                     |
              inboundApp ByteBuffer                 inboundNet ByteBuffer
                  (plaintext)                         (TLS records)
~~~

**`wrap(srcApp, dstNet)`** lấy plaintext application bytes (khi ở giai đoạn được phép), biến thành dữ liệu TLS đã bảo vệ hoặc sinh handshake/alert records, rồi đưa vào `dstNet`. Ứng dụng phải **tự gửi bytes đó** qua transport.

**`unwrap(srcNet, dstApp)`** lấy bytes TLS từ transport, giải mã/xử lý handshake và (khi phù hợp) đưa application plaintext vào `dstApp`. Ứng dụng phải **tự cung cấp đủ bytes theo đúng thứ tự**.

Các cuộc gọi này **không tự đọc/ghi TCP** và không tự làm cho một `SocketChannel` “sẵn sàng” theo logic NIO của ứng dụng. `SSLEngine` là stateful TLS processor, còn I/O multiplexing và framing/buffer ownership là trách nhiệm của caller.

### `SSLSocket` so với `SSLEngine`

| Tiêu chí | `SSLSocket` | `SSLEngine` |
| --- | --- | --- |
| Transport | Tích hợp socket và TLS streams. | **Transport độc lập**; caller mang bytes đi/đến. |
| Lập trình | `InputStream`/`OutputStream`, tương đối đơn giản. | `ByteBuffer` + state machine + scheduler. |
| Handshake | `startHandshake()` điều phối việc trao đổi. | `beginHandshake()` chỉ khởi động; caller phải chạy `wrap/unwrap/task` tới khi xong. |
| Backpressure/partial I/O | Thư viện quản lý nhiều chi tiết của stream. | Caller quản lý `SocketChannel` đọc/ghi một phần và các hàng đợi. |
| Use case | Ứng dụng đồng bộ, ví dụ đơn giản, API socket trực tiếp. | Event loop/NIO, engine transport riêng, async protocols. |
| Mức khó | Thấp hơn. | **Cao hơn đáng kể**, dễ sai state/buffer/close. |

**Nguyên tắc:** Nếu chỉ muốn gọi HTTPS, ưu tiên `java.net.http.HttpClient` hay framework HTTP có cấu hình TLS phù hợp. Đừng tự viết `SSLEngine` driver khi thư viện mạng đáng tin đã giải quyết các vấn đề này.

## 2. Bốn vùng byte của một endpoint

Một ứng dụng dùng `SSLEngine` thường có bốn vùng logic; thư viện có thể biểu diễn chúng bằng nhiều buffer/queue khác nhau:

| Vùng | Byte chứa gì? | Ai ghi / ai đọc? |
| --- | --- | --- |
| **Outbound application** (`appOut`) | Plaintext app cần gửi. | App ghi; `wrap` tiêu thụ. |
| **Outbound network** (`netOut`) | TLS handshake/alert/encrypted record cần gửi. | `wrap` ghi; transport đọc/`SocketChannel.write` gửi. |
| **Inbound network** (`netIn`) | Bytes TLS nhận từ peer, có thể mới là **một phần record**. | Transport ghi; `unwrap` tiêu thụ. |
| **Inbound application** (`appIn`) | Plaintext mà `unwrap` đã giải mã và xác thực. | `unwrap` ghi; app đọc. |

**Quan trọng:** `wrap`/`unwrap` có thể tiêu thụ/sinh byte nhưng **không nhất thiết đồng thời**:

- `wrap(emptyApp, netOut)` có thể sinh handshake record dù không có application byte nào.
- `unwrap(netIn, appIn)` có thể tiêu thụ nhiều handshake bytes nhưng sinh **0 application byte**.
- Một `unwrap` gặp thiếu bytes có thể không tiêu thụ gì: caller phải chờ mạng thêm bytes, không được gọi lại mãi trên cùng phần dữ liệu.
- Một `wrap` sau handshake có thể sinh TLS post-handshake messages hoặc `close_notify`; không giả định nó luôn chỉ tạo application ciphertext.

### Buffer không phải “message boundary” của TCP

TCP là **byte stream**, không cam kết một lần `SocketChannel.read` mang đủ một TLS record hoặc chỉ có một record. Dữ liệu có thể được chia thành nhiều lần read hoặc nhiều TLS records cùng đến trong một read. `SSLEngine` giải mã TLS framing, nhưng ứng dụng vẫn phải **giữ nguyên phần chưa tiêu thụ** trong `netIn`.

Không dùng `read()` trả số byte bằng 0 như một tín hiệu “hết record”. Trong NIO, 0 thường có nghĩa **chưa có dữ liệu mới lúc này**. EOF của `SocketChannel.read` được biểu diễn bằng `-1`; cách xử lý EOF TLS phải khác 0 và khác `BUFFER_UNDERFLOW`.

## 3. Tạo engine, cấu hình client/server và bảo đảm danh tính

### 3.1. `SSLContext` phải có key/trust managers đúng chính sách

- **Server** dùng `KeyManagerFactory`/key provider để chọn chứng thư và private key phù hợp; có thể yêu cầu client cert nếu mTLS.
- **Client** dùng `TrustManagerFactory`/PKIX truststore đã được phê duyệt để xác minh server certificate.
- Ở raw `SSLEngine` client, nên đặt **peer host/port** khi tạo engine, rồi bật `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` nếu ứng dụng đang xác thực hostname HTTPS.
- `SSLContext.getInstance("TLS")` là provider TLS engine, không tự có nghĩa dùng giao thức SSL cũ. Có thể hạn chế `TLSv1.3` trong lab, còn production tuân theo security policy/peer compatibility.

API cơ bản:

~~~java
// Mảnh API minh họa: sslContext phải đã được init đúng key/trust policy.
SSLEngine client = sslContext.createSSLEngine("api.example.test", 443);
client.setUseClientMode(true);

SSLParameters p = client.getSSLParameters();
p.setProtocols(new String[] {"TLSv1.3"});
p.setEndpointIdentificationAlgorithm("HTTPS");
client.setSSLParameters(p);

// Khi cấu hình xong mới beginHandshake/wrap/unwrap.
client.beginHandshake();
~~~

Đoạn trên **không phải tệp độc lập**, vì `sslContext` được cung cấp từ cấu hình tin cậy của ứng dụng. `SSLContext` không tạo truststore tự động từ một certificate lạ trong mạng, và `setEndpointIdentificationAlgorithm("HTTPS")` **không thay thế** việc chọn nguồn CA/truststore đúng.

**Không sử dụng “trust-all”** hoặc bỏ endpoint identification để sửa nhanh lỗi certificate. Bước đó có thể cho phép MITM ngay cả khi `wrap/unwrap` và AES-GCM hoạt động hoàn hảo.

### 3.2. Client/server mode phải đặt trước handshake

~~~java
SSLEngine server = serverSslContext.createSSLEngine();
server.setUseClientMode(false);
~~~

Một engine chưa được đặt client/server mode có thể ném `IllegalStateException` khi gọi `wrap/unwrap`. Sau khi bắt đầu handshake, không thay mode tùy tiện. Nên tạo **mỗi kết nối một engine**, không dùng chung cùng một `SSLEngine` cho nhiều client.

`createSSLEngine(host, port)` cung cấp peer host/port phục vụ session/certificate/endpoint identification, **không mở TCP socket**. Transport vẫn do `SocketChannel` hoặc ứng dụng quản lý.

## 4. Hai loại status phải đọc đồng thời

Kết quả của `wrap`/`unwrap` là `SSLEngineResult`, cung cấp **hai loại trạng thái độc lập**:

~~~java
SSLEngineResult result = engine.wrap(appBytes, netBytes);
SSLEngineResult.Status status = result.getStatus();
SSLEngineResult.HandshakeStatus hs = result.getHandshakeStatus();

int inputBytes = result.bytesConsumed();
int outputBytes = result.bytesProduced();
~~~

Đoạn trên minh họa API; `engine` và hai ByteBuffer cần được thiết lập trong hệ thống chạy thật.

- **Operation status (`getStatus()`):** thao tác vừa gọi đã xử lý buffer thành công, đóng engine hay cần thêm dung lượng/dữ liệu?
- **Handshake status (`getHandshakeStatus()`):** engine muốn ứng dụng thực hiện bước **TLS state machine** nào tiếp theo?
- **`bytesConsumed()` và `bytesProduced()`:** số byte đã tiêu thụ từ source / tạo ở destination; là cơ sở cập nhật queue/backpressure và phát hiện tiến triển.

Ví dụ `Status.OK` **không đồng nghĩa handshake đã hoàn tất**. `OK + NEED_UNWRAP` có thể nghĩa là đã xử lý record hiện tại nhưng phải nhận thêm record từ peer. `OK + NEED_TASK` nghĩa là có công việc crypto/trust được giao cho ứng dụng thực thi.

### 4.1. `SSLEngineResult.Status`

| `Status` | Ý nghĩa | Việc phải làm |
| --- | --- | --- |
| `OK` | Thao tác hoàn tất không có lỗi buffer/close cho lần gọi đó. | Đọc handshake status, consumed/produced và tiếp tục đúng hướng. |
| `BUFFER_UNDERFLOW` | Dữ liệu nguồn chưa đủ cho thao tác (thường gặp ở `unwrap` với TLS record chưa đủ). | Giữ phần net bytes chưa xử lý, lấy thêm từ mạng; kiểm tra/grow `netIn` nếu record lớn hơn buffer. **Không spin** trên cùng bytes. |
| `BUFFER_OVERFLOW` | Destination không đủ chỗ giữ output. | Với `wrap`: flush/grow `netOut`; với `unwrap`: drain/grow `appIn`. Retry đúng khi còn chỗ. |
| `CLOSED` | Phần tương ứng đã đóng hoặc không xử lý tiếp vì engine đóng. | **Vẫn chuyển các bytes đã được tạo**, xử lý `close_notify`, close state và transport thích hợp. |

**Không được gộp `BUFFER_UNDERFLOW` và `BUFFER_OVERFLOW` thành một lỗi TLS**: chúng thường là tín hiệu **điều phối buffer**, không phải certificate/chữ ký sai. Ngược lại, `SSLException` có thể là lỗi bảo mật/protocol thật sự và phải xử lý fail-closed.

### 4.2. `SSLEngineResult.HandshakeStatus`

| `HandshakeStatus` | Ý nghĩa | Hành động tiếp |
| --- | --- | --- |
| `NEED_WRAP` | Engine cần phát TLS bytes (handshake/alert/post-handshake). | Gọi `wrap`, giữ/gửi toàn bộ ciphertext sinh ra. |
| `NEED_UNWRAP` | Engine cần TLS bytes từ peer. | Dùng `unwrap` trên bytes đã nhận; nếu chưa đủ thì chờ read. |
| `NEED_UNWRAP_AGAIN` | Engine còn dữ liệu đã nhận nội bộ cần xử lý tiếp. | Gọi `unwrap` **ngay**, có thể không cần thêm bytes mạng. |
| `NEED_TASK` | Engine chờ các `Runnable` delegated tasks. | Lấy tất cả tasks qua `getDelegatedTask()`, chạy/chờ hoàn tất theo threading policy, rồi resume engine. |
| `FINISHED` | **Lần gọi `wrap/unwrap` vừa hoàn tất handshake.** | Thường quan sát ở **result** vừa trả về; ghi nhận sự kiện hoàn tất và chuyển đúng sang application/transport handling. |
| `NOT_HANDSHAKING` | Hiện không có handshake đang diễn ra. | Có thể xử lý application data; vẫn cần flush queue, post-handshake messages và close protocol khi phát sinh. |

**Bẫy đặc biệt:** `FINISHED` mang tính **sự kiện trên `SSLEngineResult`**, không phải trạng thái bền vững. `engine.getHandshakeStatus()` thường chuyển sang `NOT_HANDSHAKING` sau khi hoàn tất. Đừng thiết kế vòng lặp chỉ thoát khi `engine.getHandshakeStatus() == FINISHED`: nó có thể không bao giờ đúng.

**`NEED_UNWRAP_AGAIN` không giống `BUFFER_UNDERFLOW`**. Trường hợp đầu: engine còn handshake data đã đưa vào nội bộ và **có thể cần gọi unwrap ngay cả khi netIn không có byte mới**. Trường hợp sau: dữ liệu input chưa đủ, cần thêm bytes mạng hoặc buffer phù hợp.

### 4.3. Không tự suy đoán tiến triển từ `bytesProduced() == 0`

Ví dụ:
- `unwrap` có thể tiêu thụ handshake bytes nhưng sinh 0 plaintext bytes.
- `getDelegatedTask()` có thể thay đổi state dù không có bytes đi qua.
- `wrap` có thể trả `Status.OK` và `HandshakeStatus.NEED_UNWRAP`, không phải “không làm gì”.
- Đầu ra TLS chỉ có ý nghĩa khi kết hợp status, handshake status, buffer positions và queue state.

Khi chẩn đoán deadlock, log **hai trạng thái + consumed/produced + buffer positions/capacities**, không log raw handshake secrets hoặc payload nhạy cảm.

## 5. Mô hình state machine điều khiển handshake

`beginHandshake()` **không đồng nghĩa handshake đã hoàn thành**. Nó chỉ bắt đầu tiến trình; ứng dụng cần lặp luân phiên gửi/nhận cho đến khi cả peer và TLS state đạt điều kiện.

Sơ đồ logic cấp driver:

~~~text
engine.beginHandshake()
          |
          v
  engine.getHandshakeStatus()
          |
          +-- NEED_TASK ----------> getDelegatedTask().run()
          |                         (hoặc offload & chờ completion)
          |                                  |
          |                                  v
          |                              Kiểm tra lại state
          |
          +-- NEED_WRAP ----------> wrap(app buffer hoặc EMPTY, netOut)
          |                         kiểm tra status, flush/cấp buffer
          |                         chuyển TLS bytes đi theo đúng thứ tự
          |                                  |
          |                                  v
          |                              Kiểm tra lại state
          |
          +-- NEED_UNWRAP --------> nhận thêm net bytes nếu cần
          |                         unwrap(netIn, appIn)
          |                         kiểm tra status, giữ phần chưa dùng
          |                                  |
          |                                  v
          |                              Kiểm tra lại state
          |
          +-- NEED_UNWRAP_AGAIN --> unwrap(...) dù có thể không có bytes mới
          |                                  |
          |                                  v
          |                              Kiểm tra lại state
          |
          +-- FINISHED (result) ---> ghi nhận handshake hoàn tất
          |
          +-- NOT_HANDSHAKING ----> data phase / post-handshake / close
~~~

**Đây là sơ đồ điều phối, không phải code production độc lập.** Một event loop thật còn cần xử lý `Status.CLOSED`, `BUFFER_OVERFLOW`, `BUFFER_UNDERFLOW`, EOF, cancel/timeout, close_notify, tasks chạy chậm, pending ciphertext và trạng thái network channel.

### 5.1. Driver pseudocode có phân biệt result status

~~~text
onTick(connection):
  if taskFuture đang chạy:
    return, đợi completion; không gọi wrap/unwrap như thể task đã xong

  while còn tiến triển hợp lệ trong ngân sách CPU:
    hs = engine.getHandshakeStatus()

    if hs == NEED_TASK:
      tasks = lấy tất cả engine.getDelegatedTask()
      chạy tasks theo policy và chờ hoàn tất
      nếu offload: return, hẹn resume khi future complete
      continue

    if hs == NEED_WRAP:
      result = engine.wrap(pendingApplicationOrEmpty, netOutWriteMode)
      inspect result.Status và result.HandshakeStatus
      nếu BUFFER_OVERFLOW: flush/grow netOut; retry khi có chỗ
      nếu CLOSED: flush bytes/close_notify, cập nhật outbound state
      nếu OK: enqueue ciphertext; đăng ký OP_WRITE nếu chưa flush hết
      continue hoặc yield tùy backpressure

    if hs == NEED_UNWRAP or hs == NEED_UNWRAP_AGAIN:
      netIn.flip()       # WRITE -> READ
      result = engine.unwrap(netIn, appInWriteMode)
      netIn.compact()    # giữ ciphertext chưa tiêu thụ; về WRITE
      nếu BUFFER_UNDERFLOW:
        nếu buffer quá nhỏ: grow đến packet size hợp lý
        đợi OP_READ / bytes mới; KHÔNG busy-spin
      nếu BUFFER_OVERFLOW:
        deliver/grow appIn; retry sau khi có chỗ
      nếu CLOSED: ghi nhận close_notify/trạng thái inbound
      nếu OK: xử lý app bytes và handshake status; continue

    if hs == NOT_HANDSHAKING:
      flush mọi ciphertext pending
      chỉ nhận/gửi application data khi handshake chính sách đã xác minh
      xử lý post-handshake/close/queued application I/O
      return

    FINISHED chỉ là event từ SSLEngineResult, không điều kiện duy nhất
~~~

**Điểm cần thêm ở production:** `NEED_WRAP` vẫn có thể xuất hiện sau application handshake do KeyUpdate, NewSessionTicket hoặc đóng kết nối. Ngược lại, `NOT_HANDSHAKING` không tự bảo đảm mọi ciphertext đã thực sự đến peer. Để ra quyết định “đã hoàn thành” phải dựa cả engine state và transport queues.

### 5.2. Tránh vòng lặp tốn CPU

Không viết:

~~~java
while (engine.getHandshakeStatus()
        == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
    engine.unwrap(netIn, appIn); // SAI nếu netIn không có bytes mới.
}
~~~

Khi `unwrap` trả `BUFFER_UNDERFLOW`, dữ liệu chưa đủ; gọi lại với cùng buffer nguyên trạng có thể tạo **busy loop** 100% CPU. Với `SocketChannel` non-blocking, phải **đợi kênh read-ready**, rồi nhận thêm bytes và thử lại.

Một scheduler hợp lý cần phân biệt:
- **Tiến triển nội bộ**: tasks đã hoàn tất, engine state chuyển, bytesConsumed/Produced.
- **Tiến triển transport**: có bytes nhận mới hoặc buffer đã được gửi/drain, có dung lượng trở lại.
- **Không tiến triển**: yield chờ sự kiện mới, không bận-spin trên `BUFFER_UNDERFLOW`.

## 6. ByteBuffer: `flip`, `compact` và dữ liệu chưa đọc

`ByteBuffer` có `capacity`, `position`, `limit`. Phương thức `put` thường ghi khi buffer ở **WRITE mode**; `get`/`unwrap` cần buffer nguồn ở **READ mode** sau khi gọi `flip()`.

### 6.1. Mô hình đúng cho `netIn`

~~~text
ByteBuffer netIn = allocate(...);  // WRITE mode
SocketChannel.read(netIn);         // ghi ciphertext mới
netIn.flip();                      // READ mode
engine.unwrap(netIn, appIn);       // tiêu thụ <= remaining()
netIn.compact();                   // giữ phần ciphertext chưa dùng
// netIn trở lại WRITE mode, có chỗ append khi read tiếp
~~~

`compact()` là **khác** `clear()`:
- `clear()` chuẩn bị ghi từ đầu và **coi bytes cũ đã bỏ**, nguy hiểm nếu còn TLS bytes chưa tiêu thụ.
- `compact()` dồn phần **còn remaining()** về đầu rồi chuyển sang WRITE mode; không mất phần record dở dang.

### 6.2. Java 17+: ví dụ nhỏ xác nhận `flip`/`compact`

Tệp **`ByteBufferModesDemo.java`**:

~~~java
import java.nio.ByteBuffer;
import java.util.Arrays;

public class ByteBufferModesDemo {
    public static void main(String[] args) {
        ByteBuffer inbound = ByteBuffer.allocate(8);

        inbound.put(new byte[] {1, 2, 3, 4});
        inbound.flip();                  // WRITE -> READ

        byte[] first = new byte[2];
        inbound.get(first);             // đã tiêu thụ 1, 2

        inbound.compact();              // giữ 3, 4; sang WRITE
        inbound.put(new byte[] {5, 6}); // append khi có thêm bytes
        inbound.flip();                 // sang READ

        byte[] rest = new byte[inbound.remaining()];
        inbound.get(rest);

        System.out.println("Consumed: " + Arrays.toString(first));
        System.out.println("Retained and appended: "
                + Arrays.toString(rest));
    }
}
~~~

~~~bash
javac ByteBufferModesDemo.java
java ByteBufferModesDemo
~~~

Stdout đã kiểm chứng:

~~~text
Consumed: [1, 2]
Retained and appended: [3, 4, 5, 6]
~~~

**Không nhầm ví dụ buffer đơn giản này với parser TLS.** TLS record cần đủ header/payload và state của engine; `ByteBuffer` chỉ đảm bảo bạn **không làm mất bytes còn dư** giữa các lần I/O.

### 6.3. Cách ghi `netOut` và partial writes

Khi `wrap` ghi ciphertext vào `netOut`, nó thường đang ở WRITE mode. Trước khi gửi:

~~~text
netOut.flip()                  // chuyển sang READ
SocketChannel.write(netOut)    // có thể chỉ gửi một phần
if netOut.hasRemaining():
    giữ nguyên bytes chưa gửi; hẹn OP_WRITE
else:
    netOut.clear()             // hoặc compact rồi nối record mới
~~~

Nếu output chưa gửi hết, **không được clear/overwrite** buffer và không được phát thêm record chồng lên phần chưa gửi mà làm đảo thứ tự. TLS record layer yêu cầu thứ tự byte chuẩn; một engine có thể có nhiều outbound records chờ transport.

Trong demo in-memory ở phần 10, hai hàng đợi luôn còn đủ chỗ, nên `peerInbound.put(encrypted)` mô phỏng việc chuyển **toàn bộ** record. Đây là cách cố ý loại bỏ partial writes để bài tập tập trung state machine. NIO thật phải xây chính sách enqueue + OP_WRITE + backpressure.

## 7. Phản ứng chính xác với `BUFFER_UNDERFLOW` và `BUFFER_OVERFLOW`

### 7.1. `BUFFER_UNDERFLOW` khi `unwrap`

Giả sử record TLS dài 1200 bytes nhưng TCP mới đưa đến 80 bytes. Không thể giải mã record đầy đủ:

~~~text
netIn chứa 80 bytes -> flip -> unwrap
Result.Status = BUFFER_UNDERFLOW
    |
    +-- Không bỏ 80 bytes
    +-- compact giữ 80 bytes
    +-- chờ read thêm bytes
    +-- lần sau append bytes vào netIn
    +-- flip -> unwrap lại
~~~

Nếu `netIn.capacity()` nhỏ hơn `SSLSession.getPacketBufferSize()` hoặc nhỏ hơn kích thước record mà engine cần, buffer có thể **không bao giờ đủ**: cần grow theo chính sách, nhưng cũng giới hạn kích thước/tài nguyên để chống DoS. `getPacketBufferSize()` là kích thước buffer đề xuất **có thể thay đổi trong quá trình session/handshake**.

**Không xử lý `BUFFER_UNDERFLOW` như TLS tampering ngay lập tức.** Nó thường có nghĩa dữ liệu đang đến từng phần. Tuy nhiên, nếu transport báo EOF khi một record còn dở dang, phải xử lý kết nối bị cắt đúng cách thay vì chờ vô hạn.

### 7.2. `BUFFER_OVERFLOW` khi `wrap`

Engine muốn tạo một TLS record nhưng `netOut` không đủ chỗ. Hướng xử lý:
1. Nếu có ciphertext chờ gửi, thử **drain netOut** qua transport.
2. Nếu dung lượng vẫn nhỏ hơn record, **grow** buffer phù hợp với `getPacketBufferSize()`.
3. Gọi lại `wrap` sau khi có đủ dung lượng.
4. Không bỏ plaintext khỏi `appOut` nếu nó **chưa được bytesConsumed**.

### 7.3. `BUFFER_OVERFLOW` khi `unwrap`

Engine muốn đưa application plaintext vào `appIn` nhưng `appIn` đầy. Hướng xử lý:
1. Nếu có dữ liệu plaintext đã được xác thực trong `appIn`, chuyển nó tới application consumer khi consumer còn capacity.
2. Nếu không thể drain, áp dụng **backpressure** lên network reads; không cứ tiếp tục đọc vô hạn.
3. Nếu buffer không đủ cho record/application packet theo `getApplicationBufferSize()`, grow hợp lý.
4. Retry `unwrap` khi có chỗ, giữ nguyên input chưa consumed.

### 7.4. Bảng tổng hợp tránh nhầm

| Trường hợp | Thiếu gì? | Hành động đúng |
| --- | --- | --- |
| `unwrap` -> `BUFFER_UNDERFLOW` | Input **network** TLS bytes chưa đủ. | Giữ netIn + nhận thêm, có thể grow netIn. |
| `unwrap` -> `BUFFER_OVERFLOW` | Destination **application** buffer không đủ. | Drain/grow appIn. |
| `wrap` -> `BUFFER_OVERFLOW` | Destination **network** buffer không đủ. | Flush/grow netOut. |
| `wrap` -> `Status.OK` + `bytesConsumed=0` | Có thể chỉ sinh handshake record. | Đọc handshake status + bytesProduced. |
| `unwrap` -> `Status.OK` + `bytesProduced=0` | Có thể chỉ xử lý handshake/alert. | Không giả định có app message. |
| `NEED_UNWRAP_AGAIN` | Còn xử lý nội bộ của handshake data. | Gọi `unwrap` tiếp, không cần chờ read mới nếu engine nói vậy. |

## 8. Delegated task: tại sao `SSLEngine` có thể vẫn làm event loop đứng?

Tài liệu JDK nói các phương thức `SSLEngine` được thiết kế **không thực hiện blocking transport I/O**, nhưng một số công việc handshake có thể đòi hỏi thời gian dài, ví dụ:

- Kiểm tra certificate chain/PKIX hoặc liên hệ dịch vụ kiểm tra chứng thư.
- Lấy private key từ token/HSM, thực hiện chữ ký hoặc thao tác provider có độ trễ.
- Tính toán mật mã, tra cứu trust/key manager, hoặc hành vi provider có thể chặn.

Engine có thể trả `HandshakeStatus.NEED_TASK`; caller phải lấy các `Runnable` qua `getDelegatedTask()` và cho chúng chạy.

### 8.1. Cách đồng bộ để học

~~~java
// Mảnh xử lý minh họa, phù hợp DEMO không có yêu cầu event-loop latency.
Runnable delegated;
while ((delegated = engine.getDelegatedTask()) != null) {
    delegated.run();
}
// Sau khi tất cả delegated tasks chạy xong:
// đọc engine.getHandshakeStatus() và resume wrap/unwrap.
~~~

**Đây không phải best practice cho một event-loop có hàng nghìn kết nối.** `Runnable.run()` có thể block: nếu chạy trên thread xử lý tất cả kênh, một certificate validation chậm làm ngưng mọi kết nối.

### 8.2. Production: offload có kiểm soát và resume đúng trạng thái

Pseudo-architecture:

~~~text
Event loop:
  engine reports NEED_TASK
      |
      v
  collect delegated tasks
      |
      v
  offload to bounded crypto/handshake executor
      |                     |
      |                     +-> kiểm soát concurrency, timeout, backpressure
      |
  mark connection WAITING_FOR_TASK
  do not spin or overlap wrap/unwrap as if tasks done
      |
  future completion / scheduled event
      |
      v
  check task error, verify engine/session state
  re-register needed interestOps
  resume handshake from engine.getHandshakeStatus()
~~~

Khi xử lý thực tế:
- Task phải chạy **đủ và hoàn tất** trước khi engine có thể tiến triển qua giai đoạn cần task đó. Không chỉ submit vào executor rồi gọi `wrap/unwrap` ngay.
- Dùng **bounded executor**; tránh `new Thread(task).start()` không giới hạn cho mỗi client trong server chịu tải.
- Không block event loop bằng `future.get()`; dùng callback/queue để gửi completion về **owner event-loop thread**.
- Phải xét điều kiện timeout, connection cancellation, task failure và race với close. Khi connection đã bị đóng hoặc bị thay generation, completion cũ không được làm sống lại engine.
- Với một số provider, exceptions của task có thể được truyền hoặc gây lỗi ở bước `wrap/unwrap` tiếp theo; log theo nguyên nhân nhưng không in private key/handshake secrets.
- Nên **serialize quyền điều phối state cho mỗi connection** (một event-loop owner) để tránh nhiều callback/tác vụ đồng thời sửa queue/engine vô trật tự.

### 8.3. Đừng nhầm `NEED_TASK` với `NEED_UNWRAP`

`NEED_TASK` nghĩa engine cần **kết quả công việc địa phương**; đọc thêm từ socket không tự chạy delegated task. `NEED_UNWRAP` nghĩa engine cần **dữ liệu TLS từ peer** hoặc có dữ liệu đã chờ; chạy một task không thay thế việc đọc network.

Kết hợp xử lý cả hai mới tránh deadlock như “client chờ ServerHello trong khi server chưa chạy key/cert validation task”.

## 9. Ranh giới giữa handshake phase và application phase

Đối với handshake ban đầu:

1. Tạo `SSLEngine`, gắn mode/trust/parameters.
2. Gọi `beginHandshake()` hoặc để lần `wrap/unwrap` đầu tiên tự khởi tạo.
3. Lặp `NEED_WRAP/NEED_UNWRAP/NEED_TASK/NEED_UNWRAP_AGAIN` cho tới khi handshake hoàn tất theo trạng thái engine/result.
4. Ghi nhận event `FINISHED` nếu ứng dụng cần, đồng thời kiểm tra `SSLSession` protocol/cipher và quyết định trust/identity theo JSSE policy.
5. Khi đã có TLS session hợp lệ, xử lý application bytes bằng `wrap`/`unwrap`.

**Lưu ý TLS 1.3:** Sau khi handshake ban đầu kết thúc, **NewSessionTicket**, **KeyUpdate** hoặc các post-handshake events vẫn có thể tạo nhu cầu `wrap/unwrap` và task tương ứng. Đừng viết logic “đã FINISHED một lần thì từ đó trở đi chỉ wrap plaintext, không xem handshake status nữa”.

`NOT_HANDSHAKING` là trạng thái engine tại một thời điểm; để coi **application đã được gửi ra mạng**, cần xác minh `netOut` đã flush hết; để coi **request đã nhận đầy đủ**, cần framing của giao thức ứng dụng (HTTP, custom length prefix, v.v.), không chỉ dựa trên một lần `unwrap`.

## 10. Demo runnable Java 17+: TLS 1.3 giữa hai `SSLEngine` qua bộ nhớ

### 10.1. Mục đích, giới hạn và môi trường

Ví dụ dùng hai `SSLEngine`: **client** và **server**, mỗi engine có cấu hình `SSLContext` riêng. Giữa chúng là hai `ByteBuffer` mô phỏng **các hàng đợi ciphertext inbound network**. Với mỗi trạng thái:
- `NEED_TASK`: chạy đủ delegated tasks **đồng bộ** trong demo.
- `NEED_WRAP`: gọi `wrap(EMPTY, output)`, copy output sang inbound queue của peer.
- `NEED_UNWRAP` / `NEED_UNWRAP_AGAIN`: `flip`, `unwrap`, `compact` và cập nhật state.
- `NOT_HANDSHAKING` ở cả hai đầu: demo coi handshake ban đầu hoàn tất.
- Sau đó client `wrap` thông điệp `hello`, server `unwrap` để nhận plaintext.
- Cuối cùng client gọi `closeOutbound()` và chuyển TLS `close_notify` tới server, chứng minh inbound close được nhận.

**Không mở network socket** trong ví dụ. Đây vẫn là một **TLS 1.3 handshake/mã hóa thật được JSSE thực hiện** bằng certificate test và crypto provider, chứ không phải mô phỏng giả mọi phép toán. Nhưng nó không kiểm tra các tình huống TCP partial-read/write, application backpressure, buffer growth, task cancellation, full duplex shutdown và tải nhiều client — những phần được phân tích riêng.

Chương trình yêu cầu:
- JDK 17+ với TLS 1.3 và `TLS_AES_128_GCM_SHA256`.
- Keystore PKCS#12 có private key + certificate server dùng cho hostname `localhost`.
- Truststore PKCS#12 của client đã tin certificate demo.
- `TLS_DEMO_PASS` chứa mật khẩu **test-only**.

### 10.2. Tạo hai keystore test

Chạy trong một **thư mục thử nghiệm mới**, không commit các file. Mật khẩu `changeit` chỉ để học, **không dùng cho production**.

~~~bash
# Tạo private key + self-signed certificate cho localhost (lab).
keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 \
  -sigalg SHA256withRSA -dname "CN=localhost" \
  -ext "SAN=dns:localhost,ip:127.0.0.1" -validity 2 \
  -storetype PKCS12 -keystore server.p12 \
  -storepass changeit -keypass changeit -noprompt

# Xuất certificate công khai để client dùng làm trust anchor test.
keytool -exportcert -rfc -alias localhost -keystore server.p12 \
  -storepass changeit -file server.crt

# Import chứng thư test vào truststore RIÊNG của lab.
keytool -importcert -alias localhost -file server.crt \
  -keystore trust.p12 -storetype PKCS12 \
  -storepass changeit -noprompt
~~~

Dấu `\` cuối dòng ở ví dụ là nối dòng của **Bash**. Trên Windows PowerShell/CMD, chạy từng lệnh trên **một dòng hoàn chỉnh** và bỏ dấu `\`. Cần dùng cùng tên file trong lệnh Java bên dưới.

**Chú ý:** import certificate tự ký do mình vừa tạo vào truststore test là **một quyết định tin cậy có chủ đích** cho lab. Không tự tin một certificate từ đầu vào không xác thực rồi import vào truststore production để “vượt lỗi PKIX”. Certificate và private key `server.p12` cần bảo vệ; xóa test artifacts sau khi thực hành.

### 10.3. Mã đầy đủ: `InMemoryEngineDemo.java`

~~~java
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.net.ssl.*;

public class InMemoryEngineDemo {
    private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

    static SSLContext serverContext(
            String file, char[] password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(file)) {
            store.load(in, password);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(store, password);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, new SecureRandom());
        return ctx;
    }

    static SSLContext clientContext(
            String file, char[] password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(file)) {
            store.load(in, password);
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), new SecureRandom());
        return ctx;
    }

    static boolean drive(
            SSLEngine engine, ByteBuffer inbound,
            ByteBuffer peerInbound) throws Exception {
        SSLEngineResult.HandshakeStatus hs =
                engine.getHandshakeStatus();
        switch (hs) {
            case NEED_TASK -> {
                Runnable task;
                int count = 0;
                while ((task = engine.getDelegatedTask()) != null) {
                    task.run();
                    count++;
                }
                if (count == 0) {
                    throw new IllegalStateException(
                            "NEED_TASK without task");
                }
                return true;
            }
            case NEED_WRAP -> {
                ByteBuffer output = ByteBuffer.allocate(
                        engine.getSession().getPacketBufferSize() * 2);
                SSLEngineResult result = engine.wrap(EMPTY, output);
                if (result.getStatus() != SSLEngineResult.Status.OK) {
                    throw new IllegalStateException("wrap: " + result);
                }
                output.flip();
                if (peerInbound.remaining() < output.remaining()) {
                    throw new IllegalStateException(
                            "Demo transport queue full");
                }
                peerInbound.put(output);
                return result.bytesProduced() > 0
                        || result.getHandshakeStatus() != hs;
            }
            case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> {
                inbound.flip();
                SSLEngineResult result;
                try {
                    ByteBuffer app = ByteBuffer.allocate(
                            engine.getSession()
                                    .getApplicationBufferSize() * 2);
                    result = engine.unwrap(inbound, app);
                } finally {
                    inbound.compact();
                }

                if (result.getStatus()
                        == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    return false;
                }
                if (result.getStatus() != SSLEngineResult.Status.OK) {
                    throw new IllegalStateException(
                            "unwrap: " + result);
                }
                return result.bytesConsumed() > 0
                        || result.bytesProduced() > 0
                        || result.getHandshakeStatus() != hs;
            }
            case NOT_HANDSHAKING, FINISHED -> {
                return false;
            }
            default -> throw new IllegalStateException(
                    "Unexpected handshake status");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println(
                    "Usage: java InMemoryEngineDemo server.p12 trust.p12");
            return;
        }

        String passText = System.getenv("TLS_DEMO_PASS");
        if (passText == null) {
            throw new IllegalStateException("TLS_DEMO_PASS missing");
        }
        char[] pass = passText.toCharArray();
        try {
            SSLEngine client = clientContext(args[1], pass)
                    .createSSLEngine("localhost", 443);
            client.setUseClientMode(true);

            SSLEngine server = serverContext(args[0], pass)
                    .createSSLEngine();
            server.setUseClientMode(false);

            SSLParameters cp = client.getSSLParameters();
            cp.setProtocols(new String[]{"TLSv1.3"});
            cp.setCipherSuites(
                    new String[]{"TLS_AES_128_GCM_SHA256"});
            cp.setEndpointIdentificationAlgorithm("HTTPS");
            client.setSSLParameters(cp);

            SSLParameters sp = server.getSSLParameters();
            sp.setProtocols(new String[]{"TLSv1.3"});
            sp.setCipherSuites(
                    new String[]{"TLS_AES_128_GCM_SHA256"});
            server.setSSLParameters(sp);

            // Hai inbound network buffers ở WRITE mode.
            ByteBuffer toServer = ByteBuffer.allocate(1024 * 1024);
            ByteBuffer toClient = ByteBuffer.allocate(1024 * 1024);

            client.beginHandshake();
            server.beginHandshake();

            boolean complete = false;
            for (int n = 0; n < 1000; n++) {
                boolean progress =
                        drive(client, toClient, toServer);
                progress |= drive(server, toServer, toClient);

                if (client.getHandshakeStatus()
                        == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
                        && server.getHandshakeStatus()
                        == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                    complete = true;
                    break;
                }
                if (!progress) {
                    throw new IllegalStateException(
                            "Handshake stalled");
                }
            }
            if (!complete) {
                throw new IllegalStateException(
                        "Handshake loop limit");
            }

            System.out.println(
                    "Handshake complete: " + complete);
            System.out.println("Protocol: "
                    + client.getSession().getProtocol());
            System.out.println("Cipher suite: "
                    + client.getSession().getCipherSuite());
            System.out.println("Peer certificate present: "
                    + (client.getSession()
                            .getPeerCertificates().length > 0));

            // Client application plaintext -> TLS ciphertext.
            ByteBuffer clear = ByteBuffer.wrap(
                    "hello".getBytes(StandardCharsets.US_ASCII));
            ByteBuffer encrypted = ByteBuffer.allocate(
                    client.getSession().getPacketBufferSize() * 2);
            SSLEngineResult wr = client.wrap(clear, encrypted);
            if (wr.getStatus() != SSLEngineResult.Status.OK
                    || clear.hasRemaining()) {
                throw new IllegalStateException(
                        "Application wrap: " + wr);
            }
            encrypted.flip();

            // Server TLS ciphertext -> authenticated plaintext.
            ByteBuffer received = ByteBuffer.allocate(
                    server.getSession().getApplicationBufferSize() * 2);
            SSLEngineResult ur = server.unwrap(encrypted, received);
            if (ur.getStatus() != SSLEngineResult.Status.OK) {
                throw new IllegalStateException(
                        "Application unwrap: " + ur);
            }
            received.flip();
            System.out.println("Application message: "
                    + StandardCharsets.US_ASCII.decode(received));

            // Chỉ minh họa client close_notify -> server.
            client.closeOutbound();
            ByteBuffer closeRecord = ByteBuffer.allocate(
                    client.getSession().getPacketBufferSize() * 2);
            SSLEngineResult cr = client.wrap(EMPTY, closeRecord);
            if (cr.getStatus() != SSLEngineResult.Status.CLOSED) {
                throw new IllegalStateException(
                        "Close wrap: " + cr);
            }
            closeRecord.flip();
            SSLEngineResult peerClose = server.unwrap(
                    closeRecord,
                    ByteBuffer.allocate(
                            server.getSession()
                                    .getApplicationBufferSize() * 2));

            System.out.println("Peer received close_notify: "
                    + (peerClose.getStatus()
                            == SSLEngineResult.Status.CLOSED
                            && server.isInboundDone()));
        } finally {
            Arrays.fill(pass, '\0');
        }
    }
}
~~~

### 10.4. Biên dịch và chạy

Bash (từ thư mục chứa file Java và hai keystore):

~~~bash
export TLS_DEMO_PASS=changeit
javac InMemoryEngineDemo.java
java InMemoryEngineDemo server.p12 trust.p12
unset TLS_DEMO_PASS
~~~

Windows PowerShell:

~~~powershell
$env:TLS_DEMO_PASS = "changeit"
javac InMemoryEngineDemo.java
java InMemoryEngineDemo server.p12 trust.p12
Remove-Item Env:TLS_DEMO_PASS
~~~

Đầu ra kiểm chứng trên JDK hỗ trợ TLS 1.3, keystore test đúng hướng dẫn:

~~~text
Handshake complete: true
Protocol: TLSv1.3
Cipher suite: TLS_AES_128_GCM_SHA256
Peer certificate present: true
Application message: hello
Peer received close_notify: true
~~~

**Đây là stdout xác minh trên bộ môi trường demo.** Nếu JVM không hỗ trợ TLS 1.3/AEAD suite, truststore không chứa đúng certificate, SAN không khớp `localhost` hoặc keytool thất bại, handshake có thể ném exception — không hạ mức bảo mật để cố xuất dòng `true`.

### 10.5. Giải thích điểm khó trong code

1. **Client/server identity:** Server tạo `SSLContext` với `KeyManagerFactory` từ `server.p12`; client tạo `SSLContext` với `TrustManagerFactory` từ `trust.p12`. Client bật `HTTPS` endpoint identification với peer host `localhost`.
2. **Buffers là mô phỏng network queues:** `toServer` là ciphertext inbound phía server; `toClient` là ciphertext inbound phía client. Mỗi hàng đợi được để ở **WRITE mode** giữa các lượt.
3. **`NEED_WRAP`:** `engine.wrap(EMPTY, output)` sinh record handshake dù không có application plaintext, rồi `peerInbound.put(output)` chuyển bytes sang phía còn lại.
4. **`NEED_UNWRAP`:** `inbound.flip()`, `engine.unwrap(inbound, app)`, `inbound.compact()`; bytes chưa tiêu thụ vẫn ở queue sau khi quay về WRITE mode.
5. **`NEED_UNWRAP_AGAIN`:** dùng cùng nhánh gọi `unwrap`, kể cả khi engine xử lý buffered internal handshake bytes.
6. **`NEED_TASK`:** lấy và chạy tasks cho đến khi `getDelegatedTask()` trả `null`. **Demo chạy đồng bộ**; production phải cân nhắc offload và không block event loop.
7. **Handshake complete:** khi **cả hai** engine không còn đang handshake sau những lần drive hợp lệ, demo chuyển sang application phase. Trong network thật phải còn xét queue ciphertext và trạng thái peer/network.
8. **Client `wrap` / server `unwrap`:** Client wrap `"hello"`; server nhận plaintext qua unwrap. Đây là record mã hóa thật. Plaintext không được truyền trực tiếp qua mô phỏng `toServer`.
9. **`close_notify`:** `client.closeOutbound()` khiến `wrap(EMPTY, ...)` tạo alert đóng; server `unwrap` thấy `Status.CLOSED` và `isInboundDone()`. Demo **chỉ kiểm chứng một chiều close_notify**; ứng dụng thực còn cần phản hồi đóng đúng protocol và flush mọi ciphertext trước khi đóng transport.
10. **Bảo vệ secret:** `Arrays.fill(pass, '\0')` chỉ làm sạch **mảng tạm** do demo tạo; `passText` từ environment là `String` immutable và không thể đảm bảo xóa mọi bản sao trong JVM. Không dùng `changeit` cho secret thực.

### 10.6. Chính xác demo chưa hỗ trợ gì?

Demo chạy trên bộ đệm trong RAM đủ lớn nên:
- Nó **ném `IllegalStateException`** nếu queue mô phỏng đầy hoặc xảy ra overflow, thay vì retry/resize như transport production.
- Nó không phân mảnh TLS ciphertext thành nhiều lần `SocketChannel.read`; vì vậy chưa thử kỹ `BUFFER_UNDERFLOW` trên network thực.
- Nó không triển khai `Selector`/`OP_READ`/`OP_WRITE`, timeout tổng thể, scheduling fairness hay handling của hàng nghìn connections.
- Nó chỉ nhận **một application record đơn giản**. Real application có framing/streaming, nhiều records, partial writes, EOF và backpressure.
- Nó chỉ thể hiện **client close_notify tới server**, chưa hoàn thành toàn bộ quy trình đóng cả hai chiều của cặp engine.
- Nó không triển khai mTLS, resumption policy, early-data/0-RTT hay monitor post-handshake.
- Nó dùng truststore test tự ký. Không lấy mô hình này làm thay thế PKIX/truststore production.

Vì thế **không copy nguyên demo đưa vào nonblocking server production**. Hãy dùng phần status/buffer/transport ở các mục tiếp theo để hiểu thứ gì framework như Netty đã xử lý.

## 11. Mapping sang `SocketChannel` và `Selector`

Trong NIO non-blocking, mỗi kết nối thường có:
- Một `SocketChannel` ở nonblocking mode, `SelectionKey` và event-loop owner.
- Một `SSLEngine` cấu hình client hoặc server.
- `netIn` chứa ciphertext bytes nhận được **chưa unwrap**.
- `netOut`/queue chứa ciphertext bytes **chưa flush hết**.
- `appIn`/`appOut` hoặc application streams/framing.
- Trạng thái: handshake in progress, tasks pending, closing/closed, timer, buffer limits.

### 11.1. Quy tắc đăng ký interest ops

| Tình trạng | Nên quan tâm sự kiện gì? | Vì sao |
| --- | --- | --- |
| `NEED_UNWRAP` nhưng `netIn` chưa đủ | `OP_READ` | Chờ bytes TLS mới, không spin. |
| `NEED_WRAP`, có chỗ netOut | Có thể chạy `wrap` ngay trên event loop | Sinh ciphertext/handshake record nội bộ. |
| `netOut` còn bytes chưa gửi | `OP_WRITE` | Channel chỉ writable khi event loop được thông báo; flush dần. |
| `NEED_TASK` và tasks offloaded | Tạm chờ completion, quản lý interest ops theo policy | Không block loop hoặc đồng thời gọi engine trước khi tasks hoàn tất. |
| `appIn` đầy, application tiêu thụ chậm | Hạn chế thêm `OP_READ`, giữ backpressure | Tránh RAM tăng vô hạn. |
| Outbound queue quá lớn | Dừng/giảm việc nhận thêm appOut | Không tạo ciphertext nhanh hơn mạng gửi được. |
| TLS closing/close_notify pending | Tiếp tục `OP_WRITE` đến khi gửi hết close record | Đóng TCP quá sớm sẽ cắt TLS alert. |

**Bẫy CPU:** Nếu luôn bật `OP_WRITE` khi không có dữ liệu để gửi, vì socket thường writable, Selector có thể trả về liên tục và tiêu thụ CPU. Chỉ bật `OP_WRITE` khi **thực sự có pending bytes**.

### 11.2. Khi channel đọc được

Mô hình:

~~~text
n = channel.read(netIn)    # netIn WRITE mode
if n > 0:
    netIn.flip()
    loop:
       result = engine.unwrap(netIn, appIn)
       handle result Status + HandshakeStatus
       if UNDERFLOW -> break, cần thêm network bytes
       if OVERFLOW -> drain/grow appIn, có thể retry
       if NEED_TASK -> offload tasks, tạm dừng
       if no progress -> break để tránh busy-loop
    netIn.compact()        # giữ bytes chưa consume

if n == 0:
    không có bytes mới: yield, đừng coi là EOF

if n == -1:
    network EOF: kiểm tra TLS close_notify đã nhận chưa
    xử lý closeInbound()/truncation theo protocol & policy
~~~

Đây là **pseudocode thuật toán**, không có mọi chi tiết concurrency/exception của production. Khi `NEED_UNWRAP_AGAIN`, driver có thể vẫn cần gọi unwrap khi không có network byte mới.

### 11.3. Khi channel ghi được

Mô hình:

~~~text
while netOut/queue còn ciphertext:
    count = channel.write(readModeBuffer)
    if count == 0:
        giữ nguyên pending bytes, hẹn OP_WRITE
        break
    if readModeBuffer còn remaining:
        giữ lại, hẹn OP_WRITE
        break
    else:
        release/recycle sent buffer

khi queue rỗng:
    bỏ OP_WRITE interest
    nếu engine NEED_WRAP: gọi wrap để tạo record tiếp
    nếu application appOut pending và đã handshake: wrap thêm khi có capacity
~~~

**Không đảo record order.** Nếu `wrap` sinh ciphertext A rồi ciphertext B, chúng phải được gửi trong đúng thứ tự, kể cả khi A bị partial write. Không dùng một buffer đang được network thread đọc để gọi `wrap` ghi đồng thời.

### 11.4. Backpressure hai chiều

- **Inbound:** Application không tiêu thụ plaintext đủ nhanh -> `appIn` đầy -> `unwrap BUFFER_OVERFLOW` -> tạm giảm đọc TCP, nhưng cần cân nhắc TLS control/close để tránh deadlock. Thiết kế queue bounded và scheduler có chính sách.
- **Outbound:** Peer hoặc network chậm -> `netOut` đầy -> `wrap BUFFER_OVERFLOW` -> dừng tạo records mới, flush dần qua `OP_WRITE`. Không cứ gọi `wrap` và cấp buffer không giới hạn.
- **Handshake:** Dù chưa có application data, TLS vẫn cần gửi/nhận messages. Đừng chặn mọi `NEED_WRAP/NEED_UNWRAP` chỉ vì app queue rỗng.

## 12. Tính toán buffer size và quản lý cấp phát

`SSLSession` cung cấp hai chỉ số then chốt:

~~~java
int packetSize = engine.getSession().getPacketBufferSize();
int appSize = engine.getSession().getApplicationBufferSize();
~~~

- `getPacketBufferSize()`: dung lượng gợi ý cho TLS/DTLS packet được sinh/tiêu thụ qua engine.
- `getApplicationBufferSize()`: dung lượng gợi ý cho application plaintext của một record.
- Kích thước có thể **thay đổi theo handshake/session**; đừng hard-code `16_384` bytes rồi giả định luôn đủ cả TLS framing và tag.
- Không cố cấp buffer một record rồi tiếp tục ghi ciphertext vô hạn. Mỗi connection cần memory cap, queue cap và quy tắc nới/gom buffer.

### 12.1. Mẫu grow buffer minh họa

~~~java
// Mảnh minh họa: source đang ở WRITE mode, sẽ giữ mọi byte đã ghi.
static ByteBuffer growWriteBuffer(ByteBuffer old, int requiredCapacity) {
    if (requiredCapacity <= old.capacity()) {
        return old;
    }
    ByteBuffer replacement = ByteBuffer.allocate(requiredCapacity);
    old.flip();
    replacement.put(old);
    return replacement; // WRITE mode, bytes cũ được bảo toàn.
}
~~~

Đây không phải phương thức tự hoàn thiện cho production:
- Cần **giới hạn dung lượng** tránh untrusted peer khiến memory tăng vô hạn.
- Nếu dùng direct buffers, xử lý resource/pooling và copy overhead.
- Với output đang ở READ mode có pending bytes, phải copy phần `remaining()` chứ không áp dụng máy móc logic `flip()` thêm lần nữa.
- Kích thước phù hợp phụ thuộc trạng thái engine và queue, không chỉ một hằng số cố định.

### 12.2. `ByteBuffer` có thể heap hoặc direct

`ByteBuffer.allocate(...)` tạo heap buffer, `ByteBuffer.allocateDirect(...)` tạo direct buffer có thể phù hợp một số kênh I/O. Lựa chọn phụ thuộc hiệu năng, GC, allocation, provider và giới hạn tài nguyên; **không phải tính chất mật mã**. `SSLEngine` không cần lấy raw heap array của mọi buffer, nên tránh giả định `buffer.array()` luôn hoạt động với direct/read-only buffers.

**Không dùng chung source/destination với cùng vùng nhớ** cho `wrap/unwrap`. Hợp đồng API yêu cầu input/output không alias theo cách nguy hiểm; nếu có overlap, dữ liệu có thể hỏng hoặc hành vi provider bị từ chối.

## 13. Delegated tasks và event-loop threading: thiết kế đúng cho tải cao

Một số quy tắc thực hành:

1. **Mỗi connection có owner điều phối state.** Cho một event loop quyết định khi nào gọi `wrap`, `unwrap`, giao buffer và close. Đừng cho hai callback bất kỳ cùng gọi `wrap` trên cùng engine đồng thời.
2. **`wrap` và `unwrap` có thể đồng thời ở mức API** nếu người dùng tự bảo đảm ordering/synchronization và trạng thái; nhưng hai lần `wrap` cạnh nhau hoặc hai lần `unwrap` cạnh nhau trên cùng engine có nguy cơ sai thứ tự. Thiết kế đơn giản là serialize mọi engine operations theo connection.
3. **Deferred tasks không phải network I/O.** Dùng pool riêng hoặc cơ chế tính toán phù hợp; không tạo vô hạn worker threads.
4. **Task completion phải đánh thức đúng connection.** Đưa một sự kiện trở lại event loop, kiểm tra connection còn sống, xử lý exception, tiếp tục trạng thái engine.
5. **Không giữ lock lâu khi task có thể block.** Thiết kế race với close, timeout, cancellation và provider state; tránh deadlock event loop vs task worker.
6. **Kiểm tra active timer.** Một peer có thể gửi vài bytes rồi không bao giờ hoàn tất handshake. Đặt handshake timeout, max queued bytes, max cert chain size theo framework/policy.
7. **Dùng error handling fail-closed.** Bất cứ `SSLHandshakeException`, `SSLProtocolException`, certificate trust failure hoặc buffer logic violation chưa xử lý đều phải từ chối kết nối, không fallback plaintext.
8. **Không leak TLS secrets trong diagnostics.** Có thể log negotiated protocol, cipher suite, connection ID, generic failure stage; không log raw keys, pre-master/traffic secrets, session ticket secrets hay plaintext application data nhạy cảm.

Tài liệu Java SE 17 mô tả delegated task có thể kéo dài/thậm chí block do trust manager, key manager hoặc crypto provider. Nên **lên lịch, await completion và khôi phục engine state** — không chỉ chạy tất cả `Runnable` “cho xong” trên Selector thread.

## 14. Đóng TLS đúng: `closeOutbound`, `closeInbound` và `close_notify`

TLS có **protocol-level closure** riêng, không đồng nghĩa đóng TCP tức thời. Khi ứng dụng muốn ngừng gửi:

~~~text
Application decides to close
    |
engine.closeOutbound()
    |
wrap(EMPTY, netOut) -> TLS close_notify record
    |
flush toàn bộ netOut qua transport
    |
peer unwrap(record) -> Status.CLOSED / isInboundDone()
    |
peer có thể gửi close_notify chiều ngược lại
    |
chỉ sau quy trình đóng/bảo vệ dữ liệu mới đóng underlying channel
~~~

Chi tiết:
- `closeOutbound()` báo cho engine không tạo thêm application outbound data. Nó **chưa tự gửi** byte nào; cần gọi `wrap` cho tới khi outbound TLS closure records đã được tạo và gửi.
- `isOutboundDone()` cho biết phía outbound của engine đã hoàn tất; **không chứng minh** ciphertext của record cuối đã được gửi hết bằng TCP.
- `isInboundDone()` sau `unwrap` nhận `close_notify` cho biết peer đã đóng chiều gửi TLS đúng protocol. Nếu `SocketChannel` EOF trước `close_notify`, phải xử lý khả năng **truncation** theo security policy; `closeInbound()` có thể ném `SSLException` để báo đóng không đầy đủ.
- `SSLEngine` đã đóng **không tái sử dụng** cho kết nối mới; cần tạo engine khác.
- TLS 1.3 có semantics half-close/alerts riêng; đừng cưỡng ép mô hình “một bên đóng thì cả hai TCP lập tức cắt” nếu còn dữ liệu/chế độ protocol cần trao đổi.

**Demo `InMemoryEngineDemo` chỉ minh họa một chiều close_notify**, không thực hiện đóng hai chiều đầy đủ. Trong ứng dụng thật cần quản lý outbound/inbound status độc lập, flush và thời hạn đóng để tránh treo vô hạn khi peer không phản hồi.

### 14.1. `Status.CLOSED` vẫn có thể có bytes phải gửi

Khi `wrap` trả `Status.CLOSED`, nó có thể đã sinh một TLS alert/close record vào `netOut`. **Đừng bỏ buffer và đóng TCP ngay**: phải flush record nếu transport còn hoạt động và chính sách cho phép. Khi `unwrap` trả `CLOSED`, kiểm tra inbound done và protocol control state trước khi giải phóng connection.

### 14.2. EOF với `BUFFER_UNDERFLOW` không phải đóng đẹp

Một peer có thể bị mất mạng khi một TLS record chưa đủ bytes, khiến `unwrap` tiếp tục trả `BUFFER_UNDERFLOW`, rồi transport `read` trả `-1`. Nếu bỏ qua và gọi app callback như “kết thúc bình thường”, ứng dụng có thể chấp nhận dữ liệu chưa đủ hoặc bị cắt. Cần phân biệt **truncation** với **đóng TLS có `close_notify`** và xử lý theo quy tắc giao thức ứng dụng.

## 15. Những lỗi triển khai phổ biến

| Sai lầm | Tác động | Cách đúng |
| --- | --- | --- |
| Chỉ gọi `beginHandshake()` rồi gửi application data | Handshake chưa được điều phối. | Chạy state machine `wrap/unwrap/NEED_TASK` đến khi hoàn tất. |
| Gọi `wrap`/`unwrap` nhưng bỏ `SSLEngineResult` | Không biết buffer overflow/underflow, handshake status, bytes consumed. | Luôn xử lý cả status và byte counts. |
| Chỉ xét `getStatus() == OK` | `OK` không đồng nghĩa handshake completed. | Đọc `getHandshakeStatus`/engine state. |
| Đợi `engine.getHandshakeStatus()==FINISHED` | `FINISHED` thường chỉ xuất hiện trên result vừa trả về. | Xử lý `result.getHandshakeStatus()==FINISHED` và `NOT_HANDSHAKING` đúng ngữ cảnh. |
| Không hỗ trợ `NEED_UNWRAP_AGAIN` | Handshake có thể treo với provider/TLS state phù hợp. | Cho phép unwrap tiếp không cần read bytes mới. |
| Gọi `unwrap` mãi trên partial input | Busy loop khi `BUFFER_UNDERFLOW`. | Giữ bytes và chờ `OP_READ`. |
| Sau `unwrap` dùng `netIn.clear()` | Bỏ bytes TLS record chưa xử lý. | `flip` trước unwrap, `compact` sau unwrap. |
| Sau `wrap` gọi `netOut.clear()` khi TCP chỉ gửi một phần | Mất ciphertext hoặc đảo record order. | Giữ remaining bytes, flush qua `OP_WRITE`. |
| Xử lý `BUFFER_OVERFLOW` bằng cách bỏ plaintext | Có thể mất application data và làm lệch record stream. | Drain/grow destination, retry; dựa trên bytesConsumed. |
| Thực thi delegated tasks trên Selector thread | Task crypto/trust chậm chặn mọi connection. | Offload có giới hạn, callback completion về event loop. |
| Submit task rồi gọi tiếp `wrap/unwrap` ngay | Engine chưa có kết quả task, có thể bế tắc/race. | Đợi tasks hoàn tất và resume đúng state. |
| Dùng một `SSLEngine` cho nhiều connection | Session/state/ciphers/queues bị trộn, không an toàn. | Một engine cho một TLS connection. |
| Không cấu hình `setUseClientMode` | Engine có thể ném `IllegalStateException`. | Đặt mode trước handshake. |
| Tắt hostname verification để “fix PKIX” | Có thể chấp nhận server sai identity. | Truststore và endpoint identity đúng. |
| Nhầm `SSLEngine` với `SSLSocket` tự thực hiện I/O | Không có bytes nào được gửi ra socket. | Tự chuyển ciphertext vào/ra `SocketChannel`. |
| Mặc định TCP read một lần có đủ record | TCP là stream, có partial/multiple records. | Giữ netIn và xử lý kết quả unwrap. |
| Luôn bật `OP_WRITE` | Busy loop khi writable nhưng không có gì để gửi. | Chỉ đăng ký OP_WRITE khi pending ciphertext. |
| Đóng TCP ngay sau `closeOutbound()` | `close_notify` chưa được tạo/gửi, có thể gây truncation. | `wrap` TLS close records, flush rồi đóng đúng quy trình. |
| Debug raw application/traffic secret | Có thể lộ dữ liệu/khóa và metadata nhạy cảm. | Log trạng thái/metadata tối thiểu, bảo vệ debug logs. |

## 16. Chẩn đoán lỗi và kiểm thử state machine

### 16.1. Exception không thay thế status

| Tín hiệu | Loại | Giải thích |
| --- | --- | --- |
| `BUFFER_UNDERFLOW` / `BUFFER_OVERFLOW` | `SSLEngineResult.Status` | Điều kiện buffer, **thường không phải security exception**. |
| `NEED_TASK` / `NEED_WRAP` / `NEED_UNWRAP` | `HandshakeStatus` | Engine yêu cầu bước điều phối tiếp theo. |
| `SSLHandshakeException` | Exception | Handshake thất bại (có thể do chain/trust, hostname, protocol/ciphers, key material). |
| `SSLProtocolException` | Exception | Lỗi protocol TLS/DTLS; cần fail-closed và chẩn đoán context. |
| `SSLException` | Exception nền tảng | Engine không thể xử lý dữ liệu/kết nối theo TLS; có thể bao gồm close/truncation. |
| `IllegalStateException` | Exception Java hoặc app-defined | Ví dụ chưa set client/server mode; demo cũng dùng nó khi queue quá nhỏ/stalled. |
| `SocketChannel.read() == -1` | Transport EOF | Mạng đã đóng; **không phải `close_notify`**. |

Không bắt toàn bộ `Exception` rồi cố gọi `wrap` thêm vô hạn hoặc chuyển sang plaintext. Nếu certificate validation fail, hãy kiểm tra truststore/PKIX/SAN và thuật toán, không tạo trust-all manager.

### 16.2. Bộ test tối thiểu cho driver NIO

| Test | Cách dựng | Kỳ vọng |
| --- | --- | --- |
| Handshake TLS 1.3 bình thường | Client/server trust hợp lệ | Completed, session protocol/cipher đúng, data hai chiều hoạt động. |
| Certificate không được tin | Dùng truststore khác/không có issuer | Handshake fail; không cấp plaintext application data. |
| Hostname sai | Certificate chỉ có SAN `localhost`, engine tạo với host khác | Client endpoint identification fail. |
| Ngắt record thành 2–3 TCP reads | Gửi phần header/record rồi phần còn lại | `BUFFER_UNDERFLOW`, giữ bytes, sau đó unwrap OK. |
| Một read chứa nhiều TLS records | Nối các ciphertext records | Driver unwrap lặp và giữ position, không mất record. |
| `netOut` nhỏ | Destination không đủ packet | `BUFFER_OVERFLOW`, flush/grow rồi retry. |
| `appIn` nhỏ | Destination không đủ plaintext | `BUFFER_OVERFLOW`, drain/grow appIn. |
| Delegated task chậm | Trust manager/provider giả lập delay có kiểm soát | Event loop khác vẫn chạy, task completion resume đúng connection. |
| `NEED_UNWRAP_AGAIN` | Provider/state trả status đó | Driver gọi unwrap tiếp, không đợi read mới một cách sai. |
| TCP partial write | Channel.write chỉ gửi vài bytes | Ciphertext còn lại được giữ đúng thứ tự, OP_WRITE flush dần. |
| Event loop không có pending netOut | Socket writable | OP_WRITE không spin. |
| Đóng TLS đúng | Hai phía trao close_notify | Cả inbound/outbound done theo policy, transport chỉ đóng sau flush. |
| Abrupt TCP EOF | Đóng không gửi close_notify | Phân biệt truncation/closeInbound error với đóng hợp lệ. |
| Concurrent close + delegated task | Đóng khi task đang chạy | Completion không làm hồi sinh connection hoặc leak buffer. |
| Handshake không có tiến triển | Peer gửi nửa record rồi im | Timeout và giới hạn memory; không bận-spin vô hạn. |
| Post-handshake TLS 1.3 | NewSessionTicket/KeyUpdate | Driver tiếp tục xử lý control messages dù đã hoàn tất handshake. |

Không nên kiểm tra test bằng chuỗi exception message cố định của một provider; dùng **loại trạng thái, vị trí lỗi, dữ liệu nhận và điều kiện fail-closed**. Provider khác nhau có thể có nhiều delegated tasks, số vòng wrap/unwrap, kích thước output hoặc sequence status khác nhau — đừng hard-code rằng handshake luôn cần đúng N lần.

### 16.3. Log trạng thái có kiểm soát

Ví dụ field logging:
- Connection/correlation ID, role client/server, JSSE provider hoặc JDK build trong phạm vi policy.
- `SSLEngineResult.Status`, `HandshakeStatus`, `bytesConsumed`, `bytesProduced`.
- `netIn.position/limit/capacity`, `netOut.remaining`, number of pending tasks/bytes.
- Protocol và cipher suite **sau** handshake.
- `SSLException` class, giai đoạn (handshake, read, write, close), exception category đã chuẩn hóa.

Không log private key, TLS traffic secrets, session tickets, client credential, raw record bytes hoặc decrypted plaintext nhạy cảm; dùng debug flags JSSE trong lab có kiểm soát, không bật bừa bãi trên production.

## 17. Tự kiểm tra kiến thức

**Câu 1.** `SSLEngine.wrap(app, net)` trả `OK` và `bytesConsumed=0` nhưng `bytesProduced>0`. Có phải API bị lỗi không?

**Đáp án:** Không. Khi handshake hoặc đóng TLS, engine có thể sinh TLS record mà không tiêu thụ application plaintext nào.

**Câu 2.** `unwrap` trả `BUFFER_UNDERFLOW` sau khi mới nhận 12 bytes. Nên gọi unwrap tiếp 10.000 lần để chắc sẽ xong không?

**Đáp án:** Không. Giữ bytes chưa xử lý bằng `compact`, chờ thêm dữ liệu từ mạng hoặc grow netIn nếu quá nhỏ, rồi retry; tránh busy loop.

**Câu 3.** `unwrap` trả `BUFFER_OVERFLOW`; nên tăng `netIn` hay `appIn`?

**Đáp án:** **`appIn`** hoặc drain output plaintext đã xác thực, vì **destination** của `unwrap` không đủ chỗ. `BUFFER_UNDERFLOW` mới chủ yếu liên quan input netIn thiếu.

**Câu 4.** `wrap` trả `BUFFER_OVERFLOW` thì thao tác thích hợp là gì?

**Đáp án:** Flush/grow outbound network buffer `netOut`, rồi retry khi đủ chỗ. Không bỏ plaintext chưa `bytesConsumed`.

**Câu 5.** Tại sao `FINISHED` không phải điều kiện duy nhất để thoát vòng lặp qua `engine.getHandshakeStatus()`?

**Đáp án:** `FINISHED` chủ yếu là sự kiện của `SSLEngineResult` khi wrap/unwrap vừa kết thúc handshake; engine thường đã chuyển về `NOT_HANDSHAKING`.

**Câu 6.** `NEED_UNWRAP_AGAIN` và `BUFFER_UNDERFLOW` có cùng hành động không?

**Đáp án:** Không. `NEED_UNWRAP_AGAIN` yêu cầu unwrap tiếp với dữ liệu TLS nội bộ đã nhận, có thể không cần bytes mạng mới; `BUFFER_UNDERFLOW` là thiếu dữ liệu input cần chờ nhận thêm.

**Câu 7.** `NEED_TASK` xuất hiện; có thể submit tasks vào executor rồi tiếp tục wrap ngay trước khi future complete không?

**Đáp án:** Không. Engine cần **kết quả đã hoàn tất** của task; phải chờ, được đánh thức và resume state theo connection owner.

**Câu 8.** `SocketChannel.write(netOut)` trả số byte nhỏ hơn `netOut.remaining()` trước khi gọi. Có được clear netOut không?

**Đáp án:** Không. Phải giữ bytes chưa gửi, tiếp tục `OP_WRITE`; clear sớm làm mất ciphertext và phá thứ tự TLS record.

**Câu 9.** Một TLS client dùng `createSSLEngine("bank.example", 443)` nhưng không bật `setEndpointIdentificationAlgorithm("HTTPS")`. Có thể suy ra hostname luôn được kiểm tra không?

**Đáp án:** Không. Khi dùng raw engine cần cấu hình endpoint identification phù hợp; host/port gợi ý peer context không tự tương đương mọi kiểm tra hostname.

**Câu 10.** Khi client gọi `closeOutbound()`, có thể đóng TCP socket ngay không?

**Đáp án:** Không. Cần tiếp tục gọi `wrap` tạo `close_notify`, flush toàn bộ ciphertext, xử lý inbound close policy rồi mới đóng transport.

**Câu 11.** Sau handshake TLS 1.3, `SSLEngine` còn có thể trả `NEED_WRAP` không?

**Đáp án:** Có. Post-handshake messages như NewSessionTicket/KeyUpdate và close_notify có thể yêu cầu thêm wrap/unwrap. Driver phải tiếp tục theo state thay vì coi TLS đã “bất biến”.

**Câu 12.** Vì sao demo in-memory không được đưa thẳng vào một NIO server production?

**Đáp án:** Demo giả định queue đủ lớn và bytes được chuyển nguyên vẹn, tasks chạy đồng bộ, chưa xử lý full duplex close, buffer growth, partial I/O, backpressure, Selector scheduling và các race/timeout. Tất cả là nghĩa vụ thiết kế của transport driver thực tế.

## 18. Tài liệu tham khảo

- [Java SE 17 — SSLEngine](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLEngine.html): hợp đồng `wrap/unwrap`, transport-independent processing, buffer semantics, delegated tasks, close và concurrency.
- [Java SE 17 — SSLEngineResult](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLEngineResult.html): `Status`, `HandshakeStatus`, `bytesConsumed()` và `bytesProduced()`.
- [Java SE 17 — SSLEngineResult.HandshakeStatus](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLEngineResult.HandshakeStatus.html): `NEED_WRAP`, `NEED_UNWRAP`, `NEED_UNWRAP_AGAIN`, `NEED_TASK`, `FINISHED` và `NOT_HANDSHAKING`.
- [Oracle Java 17 — JSSE Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): hướng dẫn `SSLEngine`, buffer overflow/underflow, delegated task, nonblocking I/O và TLS handshake.
- [Java SE 17 — SSLContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html): tạo `SSLEngine` từ key/trust managers.
- [Java SE 17 — SSLParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): protocol/cipher suites và endpoint identification.
- [Java SE 17 — SSLSession](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSession.html): `getPacketBufferSize()`, `getApplicationBufferSize()`, metadata phiên.
- [Java SE 17 — ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): `flip`, `compact`, `position`, `limit` và `remaining`.
- [Java SE 17 — Selector](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html) và [SelectionKey](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectionKey.html): event loop `OP_READ`/`OP_WRITE`.
- [Oracle JDK 17 — keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): PKCS#12 keystore và chứng thư thử nghiệm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jsse-009">16.9 Xử lý BUFFER_UNDERFLOW, BUFFER_OVERFLOW, đóng kết nối và tài nguyên bộ đệm</a>

<details>
<summary>Xem chi tiết</summary>

# 16.9. Xử lý `BUFFER_UNDERFLOW`, `BUFFER_OVERFLOW`, đóng kết nối và tài nguyên bộ đệm

## Mục tiêu và bối cảnh

Trong **JSSE (Java Secure Socket Extension)**, `SSLEngine` cung cấp TLS/DTLS ở mức bộ đệm `ByteBuffer` thay vì tự sở hữu socket. Ứng dụng chịu trách nhiệm mang dữ liệu qua mạng, xử lý trạng thái bắt tay, cấp phát bộ đệm, điều phối non-blocking I/O và **đóng TLS đúng giao thức**. Đặc biệt, hai kết quả `SSLEngineResult.Status.BUFFER_UNDERFLOW` và `BUFFER_OVERFLOW` **không đơn giản là lỗi cần đóng kết nối**: chúng là tín hiệu điều phối dữ liệu và bộ nhớ.

Sau bài này, bạn có thể:

1. Phân biệt **network packet buffers** và **application plaintext buffers** cho cả hai hướng của `SSLEngine`.
2. Đọc bốn loại `Status` (`OK`, `BUFFER_UNDERFLOW`, `BUFFER_OVERFLOW`, `CLOSED`) cùng `HandshakeStatus`, `bytesConsumed` và `bytesProduced`.
3. Chữa `BUFFER_UNDERFLOW` bằng **giữ lại bytes chưa xử lý, đọc thêm dữ liệu**, chỉ tăng capacity khi cần đủ chứa TLS record; không làm mất mảnh packet.
4. Chữa `BUFFER_OVERFLOW` bằng **drain/flush hoặc tăng destination buffer** đúng cho `wrap` và `unwrap`; không lặp vô hạn với buffer không đổi.
5. Gửi và nhận `close_notify` bằng `closeOutbound`, `wrap`, `unwrap`, theo dõi `isInboundDone/isOutboundDone`; nhận biết EOF đột ngột khác đóng TLS hợp lệ.
6. Thiết kế giới hạn dung lượng, pooling, backpressure, cleanup, timeout và test lỗi cho một ứng dụng Java 17+ non-blocking.

Bài có **hai chương trình Java chạy độc lập**: một probe tạo `BUFFER_OVERFLOW`/`BUFFER_UNDERFLOW`, và một vòng lặp TLS 1.3 giữa hai `SSLEngine` trong RAM, có KeyStore/truststore thử nghiệm, truyền dữ liệu, trao đổi `close_notify` và kiểm tra trạng thái đóng. Ví dụ vòng lặp chỉ là **mô phỏng trong bộ nhớ**: khi triển khai `SocketChannel + Selector` thực tế cần bổ sung I/O scheduling, concurrency, timeouts, policy và quản lý tài nguyên như phần sau.

## 1. Vì sao `SSLEngine` cần bốn hướng bộ đệm?

Hãy hình dung một TLS connection:

```text
ỨNG DỤNG GỬI                                            ỨNG DỤNG NHẬN
plaintext ByteBuffer                                        plaintext
         |                                                       ^
         v                                                       |
     engine.wrap(src, networkOut)                    engine.unwrap(networkIn, dst)
         |                                                       ^
         v                                                       |
TLS record bytes (ciphertext/handshake)       TLS record bytes nhận từ mạng
         |                                                       ^
         +-------- SocketChannel / network ---------------------+
```

Mỗi bên thường tổ chức các buffer riêng:

| Bộ đệm logic | Chứa gì? | Chủ thể ghi | Bên tiêu thụ |
| --- | --- | --- | --- |
| `applicationOut` | Plaintext **chờ gửi** | Application | `engine.wrap(...)` |
| `networkOut` | TLS records **chờ ghi ra socket** | `engine.wrap(...)` | `SocketChannel.write(...)` |
| `networkIn` | TLS records **đã đọc từ socket** | `SocketChannel.read(...)` | `engine.unwrap(...)` |
| `applicationIn` | Plaintext **đã giải mã** | `engine.unwrap(...)` | Application |

Đây là **bốn vai trò**, không bắt buộc đúng bốn instance `ByteBuffer` trên đời. Một ứng dụng có thể có hàng đợi nhiều plaintext messages, nhiều network buffers hoặc memory pool. Điều bắt buộc là **không nhầm hướng, trạng thái `position/limit` và quyền sở hữu dữ liệu**.

Một TLS record có thể gồm handshake content, application content hoặc alert. Vì vậy **`wrap` không phải lúc nào cũng lấy plaintext để mã hóa**, và **`unwrap` không phải lúc nào cũng trả plaintext**. Trong handshake hoặc closure, `wrap(EMPTY, networkOut)` vẫn có thể sinh record điều khiển, và `unwrap` vẫn có thể tiêu thụ network bytes nhưng sinh **0 application bytes**.

`SSLEngine` không tự đọc/ghi socket; nếu bạn nhận được `BUFFER_UNDERFLOW` mà không bố trí đọc mạng, trạng thái sẽ không tự tiến triển. Nếu có `BUFFER_OVERFLOW` mà không giải phóng buffer đích, cứ gọi lại cùng tham số sẽ dẫn đến vòng lặp bận (busy spin).

## 2. `ByteBuffer` là state machine thứ hai

Để điều phối chính xác, luôn nhớ:

- `capacity()`: dung lượng vật lý đã cấp.
- `position()`: vị trí đọc/ghi hiện tại.
- `limit()`: ranh giới đọc/ghi.
- `remaining()`: số bytes có thể đọc/ghi theo mode hiện tại, bằng `limit - position`.
- `flip()`: chuyển từ **write mode → read mode**, đặt `limit=position` và `position=0`.
- `compact()`: sau khi đọc, dịch **các bytes còn chưa đọc** về đầu buffer, chuyển sang **write mode** để đọc thêm dữ liệu vào phần trống.
- `clear()`: chuyển sang write mode, **không xóa bytes vật lý** và **không giữ dữ liệu chưa đọc về mặt logic**.
- `slice()/duplicate()`: có thể chia sẻ backing memory; phải tách quyền sở hữu, tránh sửa cùng vùng dữ liệu khi `wrap/unwrap` đang dùng.

Ví dụ nhận hai mảnh của **cùng một TLS record**:

```text
networkIn đang WRITE MODE:
  [record bytes đầu...][chưa được nhận]  position=k

flip():
  [record bytes đầu...]  position=0, limit=k  READ MODE

engine.unwrap(networkIn, applicationIn) -> BUFFER_UNDERFLOW

compact():
  [bytes chưa đọc giữ lại][chỗ trống nhận tiếp]  WRITE MODE

socket.read(networkIn) -> nối thêm dữ liệu mới

flip() -> unwrap(...) lần nữa
```

**Không gọi `networkIn.clear()` sau `BUFFER_UNDERFLOW`**, vì nó có thể làm mất phần record chưa tiêu thụ. `compact()` giữ lại phần còn thiếu, là thao tác phù hợp trước khi đọc thêm. Nếu `unwrap` đã tiêu thụ một phần dữ liệu, `compact` chỉ giữ **phần còn lại**, không sao chép lại bytes đã được xử lý.

Ví dụ nhỏ để tự hình dung (`buffer` là write mode có `position=3`):

```java
buffer.flip();           // position=0, limit=3
// đọc 1 byte từ buffer ...
buffer.compact();        // giữ 2 byte chưa đọc, position=2, limit=capacity
// socket.read(buffer);  // điền thêm sau 2 byte đó
```

Đây là snippet minh họa, không phải chương trình độc lập; buffer/socket cần tạo và quản lý phù hợp.

## 3. Đọc `SSLEngineResult`: hai lớp trạng thái độc lập

`SSLEngine.wrap` và `SSLEngine.unwrap` trả `SSLEngineResult` với:

```java
result.getStatus();
result.getHandshakeStatus();
result.bytesConsumed();
result.bytesProduced();
```

**`getStatus()`** cho biết tình trạng thao tác I/O của engine. **`getHandshakeStatus()`** chỉ ra việc tiếp theo cần làm cho handshake TLS/DTLS. Hai trường đó **không thay thế** nhau: ví dụ `Status.OK` vẫn có thể đi kèm `HandshakeStatus.NEED_TASK` hoặc `NEED_WRAP`.

| `SSLEngineResult.Status` | Diễn giải | Hành động thường dùng |
| --- | --- | --- |
| `OK` | Thao tác không bị chặn bởi trạng thái lỗi/buffer | Xử lý bytes consumed/produced, mạng và `HandshakeStatus`; không giả định đã hoàn tất toàn bộ message |
| `BUFFER_UNDERFLOW` | `unwrap` thiếu TLS record bytes trong input network buffer | Giữ bytes chưa tiêu thụ, kiểm tra capacity rồi **đọc thêm từ peer** |
| `BUFFER_OVERFLOW` | Destination buffer quá nhỏ/không còn chỗ cho output | **`wrap`: networkOut**; **`unwrap`: applicationIn**. Flush/drain hoặc mở rộng đúng destination |
| `CLOSED` | Engine đã đóng phần liên quan hoặc thao tác kết thúc vì trạng thái đóng | Xử lý close_notify và half-close; phân biệt thành công và EOF bất thường |

Hai bộ đếm quan trọng:

- `bytesConsumed()` là số byte bị tiêu thụ trong **input buffers** của lời gọi đó.
- `bytesProduced()` là số byte được ghi vào **output buffers** của lời gọi đó.

**Không đồng nhất** số bytes plaintext với TLS network bytes; TLS thêm record headers, authentication tags và metadata. Một lần `unwrap` có thể **tiêu thụ TLS record** rồi produce 0 plaintext vì đó là message handshake/alert. Một lần `wrap` có thể produce record dù consume 0 application bytes.

Một cách đo progress đúng hơn là **có bytes consumed/produced, handshake state thay đổi, task hoàn thành hoặc socket chuyển dữ liệu**. Nếu không có tiến triển và đã thiếu input, hãy đợi I/O thay vì spin. Dù vậy, một số hành vi TLS/DTLS có thể chuyển internal state không phản ánh đơn giản bằng hai bộ đếm; cần triển khai theo contract của provider.

## 4. `BUFFER_UNDERFLOW`: thiếu dữ liệu nhận, không phải “packet sai”

Trong TCP, `SocketChannel.read` **không đảm bảo trả về một TLS record hoàn chỉnh**. Dữ liệu TLS có thể đến theo nhiều mảnh, hoặc nhiều record cùng trong một lần read. `SSLEngine.unwrap` cần đủ bytes để giải mã record cần xử lý; nếu không đủ, nó báo `BUFFER_UNDERFLOW`.

### Quy trình xử lý chuẩn

1. `networkIn` ban đầu là **write mode** chứa bytes đã đọc từ socket.
2. `networkIn.flip()` để engine đọc.
3. Gọi `SSLEngineResult r = engine.unwrap(networkIn, applicationIn)`.
4. Nếu `r.getStatus()==BUFFER_UNDERFLOW`, **đừng loại bỏ bytes còn lại**. Gọi `networkIn.compact()` để trở về write mode và giữ bytes chưa tiêu thụ.
5. Kiểm tra `networkIn.capacity()` có nhỏ hơn `engine.getSession().getPacketBufferSize()` không. Nếu nhỏ, tăng capacity **sau khi bảo toàn bytes**. Nếu capacity đã đủ, **không mở rộng vô điều kiện** chỉ vì underflow.
6. Đăng ký quan tâm `OP_READ`/đợi socket nhận thêm bytes; chỉ thử `unwrap` tiếp sau khi dữ liệu mới hoặc state TLS cho phép.
7. Nếu socket trả **-1 (EOF)**, xử lý như **đóng transport**, không đợi vĩnh viễn record không bao giờ đến và không chuyển unknown thành thành công.

Snippet xử lý một kết quả (`networkIn` được quản lý theo mô hình write mode ở ngoài lời gọi; `appIn` là write mode):

```java
networkIn.flip();
SSLEngineResult r = engine.unwrap(networkIn, appIn);
networkIn.compact(); // bảo toàn network bytes còn lại

if (r.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
    int expected = engine.getSession().getPacketBufferSize();
    if (networkIn.capacity() < expected) {
        networkIn = growWriteModeBuffer(networkIn, expected);
    }
    // Chờ socket.read(networkIn) có thêm bytes, KHÔNG spin.
}
```

`growWriteModeBuffer(...)` là helper phải **copy bytes đang có trong write mode**, không phải lời gọi chuẩn JDK; ví dụ đầy đủ ở dưới có helper `grow(...)`. Vì `getPacketBufferSize()` có thể thay đổi theo session/handshake, đừng chỉ kiểm tra kích thước một lần lúc kết nối vừa tạo.

**Hai trường hợp rất dễ nhầm:**

- `BUFFER_UNDERFLOW` khi `networkIn.remaining()==0` ở read mode hoặc chỉ có header/fragment của record: **cần thêm bytes**.
- `BUFFER_OVERFLOW` trên `unwrap` vì `applicationIn` quá nhỏ: **không được giải bằng đọc thêm network bytes**. Cần xử lý output buffer.

**Đừng parse TLS record length để tự cấp phát tùy ý** dựa trên input chưa tin cậy. Hãy dùng `getPacketBufferSize` và giới hạn bộ nhớ cho mỗi kết nối, kiểm thử provider và đóng connection vi phạm chính sách.

## 5. `BUFFER_OVERFLOW`: thiếu chỗ cho output

### Trường hợp A — `wrap` overflow: `networkOut` quá nhỏ

`wrap(applicationOut, networkOut)` tạo TLS network bytes (record headers, ciphertext, tag, handshake/alert). Output không đủ chỗ khiến `BUFFER_OVERFLOW`. Không cố gắng ghi thêm ứng dụng vào `applicationOut` để giải quyết; nguồn không phải vấn đề.

Chọn một trong hai hướng:

1. Nếu `networkOut` đã chứa bytes từ các lần wrap trước, **flip và flush đến `SocketChannel`**. Nếu socket chỉ ghi một phần, giữ remainder ở read mode, đăng ký `OP_WRITE`, chờ socket writable rồi ghi nốt. Sau khi flush, `compact` trở lại write mode.
2. Nếu capacity thực sự nhỏ hơn packet cần sinh, tăng `networkOut` dựa trên `engine.getSession().getPacketBufferSize()` và upper bound của ứng dụng. **Bảo toàn bytes đã ghi nhưng chưa gửi**.

Snippet khái niệm:

```java
SSLEngineResult r = engine.wrap(applicationOut, networkOut);
if (r.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
    // Ưu tiên gửi TLS bytes đang có trong networkOut;
    // nếu vẫn thiếu: mở rộng destination phù hợp, không clear.
    int required = engine.getSession().getPacketBufferSize();
    networkOut = growWriteModeBuffer(networkOut, required);
    // Retry sau khi có chỗ thực sự.
}
```

Nếu output còn pending mà tăng buffer, helper phải giữ chính xác bytes chưa gửi, không gửi trùng/làm mất record. Trong mô hình event-loop, **đợi OP_WRITE khi socket không tiến triển**, không bận CPU bằng gọi `wrap` mãi.

### Trường hợp B — `unwrap` overflow: `applicationIn` quá nhỏ

`unwrap(networkIn, applicationIn)` giải mã TLS record ra plaintext. Khi plaintext không vừa destination, trả `BUFFER_OVERFLOW`. Khi đó:

1. Cho application **đọc/xử lý bytes đã có trong `applicationIn`**, sau đó `compact()/clear()` khi thực sự được phép.
2. Hoặc tăng `applicationIn` ít nhất theo `engine.getSession().getApplicationBufferSize()` và số bytes đang chứa.
3. Retry `unwrap` với đúng **network bytes chưa bị tiêu thụ**, không tự loại bỏ TLS bytes đang chờ.

Snippet:

```java
if (r.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
    int required = engine.getSession().getApplicationBufferSize();
    appIn = growWriteModeBuffer(appIn, required);
    // Sau đó gọi lại unwrap bằng network bytes chưa xử lý.
}
```

**Không bắt buộc cứ overflow là tăng bộ nhớ:** drain đến consumer có thể tốt hơn, đặc biệt khi ứng dụng giữ nhiều connection. Nếu consumer chậm, thực hiện **backpressure** và có giới hạn queue; không tăng buffer vô hạn để “xử lý hết”.

### Cách viết helper tăng buffer đúng mode

Giả định buffer hiện tại đang ở **write mode** và `position` là lượng bytes cần giữ:

```java
static ByteBuffer grow(ByteBuffer old, int minimum) {
    int capacity = Math.max(old.capacity() * 2, minimum);
    if (capacity > MAX_BUFFER_BYTES) {
        throw new IllegalStateException("Buffer cap exceeded");
    }
    ByteBuffer newer = ByteBuffer.allocate(capacity);
    old.flip();          // chuyển các bytes đã ghi sang read mode
    newer.put(old);      // copy đúng bytes đó
    return newer;        // write mode, position sau dữ liệu được giữ
}
```

`MAX_BUFFER_BYTES` là chính sách bạn tự đặt; helper **không phải API có sẵn**. Nếu buffer đang ở **read mode** với bytes chưa gửi, phải dùng cách **giữ `remaining()` rồi chuyển mode** phù hợp, không áp helper write-mode này nguyên xi. Ví dụ vòng lặp thứ hai có helper đầy đủ đúng giả định.

## 6. Kích thước `SSLSession`: packet khác application

Khi khởi tạo:

```java
SSLSession session = engine.getSession();

ByteBuffer networkIn =
        ByteBuffer.allocate(session.getPacketBufferSize());
ByteBuffer networkOut =
        ByteBuffer.allocate(session.getPacketBufferSize());
ByteBuffer applicationIn =
        ByteBuffer.allocate(session.getApplicationBufferSize());
```

Các số trả về là gợi ý/giới hạn từ session/provider cho việc chứa dữ liệu TLS và plaintext. Không coi chúng là kích thước message business (HTTP payload) hoặc là lượng RAM cố định bất biến suốt đời connection.

| Loại | Getter | Tình huống sử dụng |
| --- | --- | --- |
| TLS network packet buffer | `getPacketBufferSize()` | Đảm bảo network input có thể giữ record hoàn chỉnh và network output có thể chứa record do wrap tạo |
| Decrypted application buffer | `getApplicationBufferSize()` | Đảm bảo destination `unwrap` đủ chứa dữ liệu plaintext record |
| Application output/source | Không có số tối thiểu cứng cho kích thước business input | `wrap` có thể tiêu thụ một phần; dựa vào `bytesConsumed`/`hasRemaining` để gửi tiếp |

**Dữ liệu TLS không phải lúc nào cũng nở đúng một tỉ lệ cố định.** Overhead thay đổi theo TLS version, cipher suite, handshake hay application record. Đừng đoán `plaintext + 32`, hoặc hard-code `16 * 1024` làm capacity duy nhất. Hãy hỏi session và xử lý `SSLEngineResult` thực tế.

**Giới hạn bảo vệ tài nguyên:** ngay cả khi helper gợi ý tăng, ứng dụng vẫn cần giới hạn RAM và số connection; nếu vượt quota thì fail/close theo policy thay vì cấp phát vô hạn. Ví dụ bài học dùng cap minh họa **1 MiB cho mỗi buffer** — đây không phải con số tối ưu cho mọi ứng dụng. Thực tế đo handshake/certificate chains/record sizes và bounded concurrency.

## 7. Handshake status vẫn phải được xử lý sau khi buffer OK

`SSLEngineResult.HandshakeStatus` gồm:

| Trạng thái | Hành động ứng dụng |
| --- | --- |
| `NEED_WRAP` | Gọi `wrap` để sinh handshake/TLS control records, gửi hết network output |
| `NEED_UNWRAP` | Chờ TLS bytes từ peer và gọi `unwrap` |
| `NEED_TASK` | Lấy `engine.getDelegatedTask()` cho đến khi hết task; hoàn thành đúng cách trước khi tiến hành bước tiếp theo |
| `FINISHED` | Handshake **vừa** hoàn tất; chỉ xuất hiện trên kết quả `wrap/unwrap`, **không** phải từ `engine.getHandshakeStatus()` |
| `NOT_HANDSHAKING` | Không còn handshake đang diễn ra tại thời điểm kiểm tra; vẫn có thể có application data/closure để xử lý |
| `NEED_UNWRAP_AGAIN` | Yêu cầu gọi `unwrap` thêm mà không nhất thiết đọc thêm network data; Java 17 document mô tả trạng thái này cho **DTLS** để xử lý handshake data đã được buffer nội bộ |

Khi `NEED_TASK`, một số task (kiểm tra chứng chỉ, khóa, trust manager) có thể **block** dù `SSLEngine` I/O API hướng non-blocking. Trong real selector server, chạy task trong **bounded executor**/worker thích hợp thay vì giữ event-loop. Cẩn thận đồng bộ per-engine: không có nhiều thread cùng tự ý `wrap/unwrap`/thay ByteBuffer của một connection.

Một flow tổng quát:

```text
engine.getHandshakeStatus()
      |
      +-- NEED_TASK ------> lấy / thực thi delegated tasks ----+
      |                                                       |
      +-- NEED_WRAP ------> wrap(empty hoặc app bytes) --------+--> ghi networkOut
      |                                                       |
      +-- NEED_UNWRAP ----> có network input? ---- no: OP_READ |
      |                             | yes                      |
      |                             v                          |
      |                           unwrap                       |
      |                             |                          |
      +-- NEED_UNWRAP_AGAIN -------> unwrap (DTLS) ------------+
      |
      +-- FINISHED/NOT_HANDSHAKING --> application IO / close
```

Sau **mỗi `wrap/unwrap`**, xem cả `getStatus()` lẫn `getHandshakeStatus()`. Dù `OK`, engine vẫn có thể cần `NEED_TASK` hoặc `NEED_WRAP`. Và `FINISHED` không có nghĩa connection đã đóng; chỉ là handshake TLS vừa hoàn thành.

## 8. Dừng gửi dữ liệu không đồng nghĩa đóng TLS hoàn chỉnh

TLS có **closure alert `close_notify`**. Trên `SSLEngine`, quản lý **hai chiều độc lập**:

- `closeOutbound()` thông báo ứng dụng sẽ **không gửi plaintext mới**. Engine cần phát ra bản tin đóng qua `wrap(EMPTY, networkOut)`; ứng dụng phải **gửi toàn bộ TLS bytes** đó đến peer.
- `isOutboundDone()` cho biết engine đã hoàn tất xử lý phía outbound; **không đảm bảo socket đã truyền hết bytes trong `networkOut` hoặc kernel send queue**.
- Khi nhận `close_notify` của peer, gọi `unwrap(networkIn, applicationIn)` để engine xử lý; kết quả có thể là `Status.CLOSED` và `isInboundDone()==true`.
- Mỗi bên cần phát `close_notify` theo quy tắc TLS/protocol và policy ứng dụng. Với giao thức client/server phổ biến, bên chủ động đóng gửi trước, bên còn lại nhận rồi gửi close_notify của mình.
- Khi **cả hai hướng engine done** và toàn bộ network output đã flush, transport có thể đóng; thực tế cần xử lý half-close, outstanding app data, timeout và I/O errors.

```text
Client                                Server
------                                ------
stop application writes
closeOutbound()
wrap(EMPTY, networkOut)
          -- TLS close_notify -->   unwrap(...)
                                       isInboundDone == true
                                   closeOutbound()
                    <-- close_notify -- wrap(EMPTY, networkOut)
unwrap(...)
isInboundDone == true
Both outboundDone/inboundDone when fully processed
Close channels after pending network bytes are flushed
```

**Không được** gọi `SocketChannel.close()` ngay sau `engine.closeOutbound()` khi `networkOut` chưa gửi: peer có thể thấy EOF mà không có close_notify, mất tín hiệu kết thúc TLS hợp lệ.

### EOF đột ngột, `closeInbound()` và truncation

Nếu `SocketChannel.read() == -1` mà **chưa** nhận được close_notify:

- Đây là tình huống **truncated TLS stream / peer transport closed unexpectedly** (hoặc policy/protocol không thực hiện đóng đúng).
- `engine.closeInbound()` thông báo không còn incoming TLS bytes. Theo Java Javadoc, nó **có thể ném `SSLException` khi peer chưa phát proper closure message** — **không được nuốt lỗi rồi coi kết nối đóng sạch**.
- Có thể vẫn cần `closeOutbound()` để giải phóng trạng thái của engine theo policy, nhưng không được báo rằng peer đã hoàn thành TLS closure.
- Đối với dữ liệu nhạy cảm, thiết kế application-level framing và xác nhận completeness (ví dụ content length/expected message count) để không dùng dữ liệu bị cắt như kết quả đầy đủ. `close_notify` không thay thế ý nghĩa “business transaction đã commit thành công”.

Snippet mang tính minh họa:

```java
if (endOfStream) {
    try {
        engine.closeInbound();
    } catch (SSLException truncated) {
        // Peer không đóng TLS đúng; ghi nhận và từ chối dùng dữ liệu chưa hoàn chỉnh.
        throw truncated;
    } finally {
        engine.closeOutbound(); // cho phép cleanup phía gửi theo state machine
    }
}
```

`endOfStream` và `engine` là biến application đã định nghĩa; đoạn này **không** thay thế bước flush mạng, retry/backpressure/cleanup. Không gọi `closeInbound()` sớm chỉ để “ép” handshake/unwrap hoàn thành.

**TLS 1.3 lưu ý:** `close_notify` và chính sách half-close cần được phối hợp với application protocol. Bên nhận close_notify có thể vẫn có outbound data theo trạng thái và policy cụ thể, nhưng ứng dụng cần xác định rõ lúc nào ngừng gửi và không treo chờ. `SSLEngine` đã đóng **không tái sử dụng** cho connection mới.

## 9. Ví dụ Java đầu tiên: chủ động tạo OVERFLOW và UNDERFLOW

`EngineStatusProbe.java` chạy **không cần server thực, certificate hay socket**: tạo hai `SSLEngine` mới bằng default `SSLContext` để quan sát trạng thái. Client cần tạo TLS ClientHello nhưng buffer ghi ra mạng chỉ có **1 byte**, làm `wrap` trả `BUFFER_OVERFLOW`. Server mới bắt đầu handshake và chưa nhận byte nào, nên `unwrap` trả `BUFFER_UNDERFLOW`.

```java
import java.nio.ByteBuffer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;

public final class EngineStatusProbe {
    public static void main(String[] args) throws Exception {
        SSLContext context = SSLContext.getDefault();

        SSLEngine client = context.createSSLEngine("localhost", 443);
        client.setUseClientMode(true);
        client.beginHandshake();
        SSLSession session = client.getSession();

        SSLEngineResult overflow =
                client.wrap(ByteBuffer.allocate(0), ByteBuffer.allocate(1));

        SSLEngine server = context.createSSLEngine();
        server.setUseClientMode(false);
        server.beginHandshake();
        SSLEngineResult underflow =
                server.unwrap(ByteBuffer.allocate(0),
                        ByteBuffer.allocate(session.getApplicationBufferSize()));

        System.out.println("wrapStatus=" + overflow.getStatus());
        System.out.println("unwrapStatus=" + underflow.getStatus());
        System.out.println("wrapConsumed=" + overflow.bytesConsumed());
        System.out.println("unwrapProduced=" + underflow.bytesProduced());

        client.closeOutbound();
        server.closeOutbound();
    }
}
```

```bash
javac -encoding UTF-8 EngineStatusProbe.java
java EngineStatusProbe
```

**Stdout đã xác minh trên JDK thử nghiệm:**

```text
wrapStatus=BUFFER_OVERFLOW
unwrapStatus=BUFFER_UNDERFLOW
wrapConsumed=0
unwrapProduced=0
```

Giải thích:

- `wrapStatus=BUFFER_OVERFLOW` do **destination network output không đủ**, không có nghĩa certificate sai. Vì input là empty và đang handshake, engine chỉ đang cố tạo record điều khiển.
- `unwrapStatus=BUFFER_UNDERFLOW` do network input có **0 byte**, không có record nào để parse.
- `wrapConsumed=0` và `unwrapProduced=0` là hợp lý trong hai lời gọi này.
- Ví dụ **không hoàn tất handshake** và không mở socket. Hai lệnh `closeOutbound()` chỉ đánh dấu không gửi thêm; **không** được coi là trao đổi close_notify thành công.

Output chi tiết có thể khác nếu provider/JSSE version có hành vi đặc biệt; điều chính yếu là cách `Status` cung cấp hướng xử lý. Khi làm việc với provider thực tế, luôn xử lý `Status` thay vì giả định một lời gọi sẽ thành công.

## 10. Ví dụ Java thứ hai: hai `SSLEngine` TLS 1.3 chạy trong RAM

Ví dụ này thể hiện sự phối hợp đầy đủ:

- Một **server `SSLEngine`** với `PrivateKeyEntry` và self-signed certificate **lab** trong `server.p12`.
- Một **client `SSLEngine`** với `trust.p12` chỉ tin certificate lab được tạo. Ví dụ **không dùng `TrustManager` chấp nhận tất cả**.
- `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` để client kiểm tra `localhost` theo SAN. Việc tạo `SSLEngine("localhost",443)` **không tự đảm bảo** hostname được verify nếu không bật endpoint identification.
- Bắt tay **TLSv1.3**, trao đổi TLS records giữa hai ByteBuffer trong RAM, giải mã chuỗi `tls-data`.
- Client gửi `close_notify`, server nhận rồi gửi `close_notify`, client nhận; kiểm tra cả bốn trạng thái inbound/outbound hoàn tất.
- Xử lý `NEED_TASK`, `NEED_WRAP`, `NEED_UNWRAP`; giải quyết overflow bằng `grow` và underflow bằng đợi thêm bytes mà không làm mất dữ liệu.

**Ranh giới ví dụ:** helper `send` mô phỏng việc copy toàn bộ network output vào peer ngay trong RAM, **không** thay thế `SocketChannel.write` vốn có thể partial write. Task được chạy inline để test đơn luồng; production cần task executor, event-loop scheduling, fairness, cancellation và timeout. Cap **1 MiB** chỉ minh họa giới hạn bộ nhớ.

### 10.1. Tạo bộ certificate chỉ trong thư mục lab

Dùng một thư mục mới. Trên Bash, tạo biến password **chỉ dùng cho lab**:

```bash
mkdir jsse-engine-lab
cd jsse-engine-lab
read -r -s -p "Lab password: " LAB_PASS
echo
export LAB_PASS
```

Trên PowerShell, nếu dùng biến môi trường hãy **nhập password lab riêng**, không dùng credential production; ví dụ nhập tương tác qua cơ chế bảo mật phù hợp của môi trường. Cả `keytool -storepass:env` lẫn Java demo sẽ đọc biến `LAB_PASS`. Biến môi trường có thể bị đọc bởi process/diagnostic trong một số tình huống; **không coi `:env` là secret manager**.

Sau khi đã có `LAB_PASS` và ở trong thư mục lab, tạo server key pair/cert, xuất cert và đưa vào truststore lab:

```bash
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 1 -dname "CN=localhost" -ext "SAN=dns:localhost" -ext "EKU=serverAuth" -storetype PKCS12 -keystore server.p12 -storepass:env LAB_PASS -keypass:env LAB_PASS

keytool -exportcert -alias server -rfc -file server.pem -keystore server.p12 -storetype PKCS12 -storepass:env LAB_PASS

# Chỉ cho lab tự tạo: tin cert vừa được tạo và đã tự xác minh nguồn!
keytool -importcert -alias server -file server.pem -keystore trust.p12 -storetype PKCS12 -storepass:env LAB_PASS -noprompt
```

`-noprompt` ở đây **chỉ dùng với certificate do chính lab vừa tạo**; không dùng để âm thầm tin chứng chỉ của người lạ. Tệp `server.p12` **chứa private key**, cần bảo vệ/xóa sau lab; `server.pem` chứa certificate public. Certificate có SAN `localhost` để endpoint identification của client có thể thành công; không thực hiện public DNS/TLS call nào trong demo RAM.

### 10.2. Chương trình chạy đầy đủ: `EngineLoopbackDemo.java`

```java
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;
import javax.net.ssl.*;

public final class EngineLoopbackDemo {
    private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);
    private static final int LIMIT = 1024 * 1024;

    private static final class Side {
        final SSLEngine engine;
        ByteBuffer networkIn;
        ByteBuffer networkOut;
        ByteBuffer applicationIn;
        Side peer;

        Side(SSLEngine engine) {
            this.engine = engine;
            SSLSession session = engine.getSession();
            networkIn = ByteBuffer.allocate(session.getPacketBufferSize());
            networkOut = ByteBuffer.allocate(session.getPacketBufferSize());
            applicationIn = ByteBuffer.allocate(
                    session.getApplicationBufferSize());
        }
    }

    // Đầu vào phải đang ở WRITE MODE, giữ những bytes hiện có.
    private static ByteBuffer grow(ByteBuffer writeMode, int wanted) {
        int capacity = Math.max(writeMode.capacity() * 2, wanted);
        if (capacity > LIMIT) {
            throw new IllegalStateException("Buffer cap exceeded");
        }
        ByteBuffer larger = ByteBuffer.allocate(capacity);
        writeMode.flip();
        larger.put(writeMode);
        return larger;
    }

    // Trong lab, copy nguyên TLS bytes sang buffer nhận của peer.
    private static boolean send(Side side) {
        if (side.networkOut.position() == 0) return false;
        side.networkOut.flip();
        int needed = side.peer.networkIn.position() +
                side.networkOut.remaining();
        if (side.peer.networkIn.remaining() <
                side.networkOut.remaining()) {
            side.peer.networkIn = grow(side.peer.networkIn, needed);
        }
        side.peer.networkIn.put(side.networkOut);
        side.networkOut.clear();
        return true;
    }

    private static boolean handshakeStep(Side s) throws Exception {
        SSLEngineResult.HandshakeStatus before =
                s.engine.getHandshakeStatus();
        switch (before) {
            case NEED_TASK -> {
                Runnable task;
                while ((task = s.engine.getDelegatedTask()) != null) {
                    task.run();
                }
                return true;
            }
            case NEED_WRAP -> {
                SSLEngineResult result =
                        s.engine.wrap(EMPTY.duplicate(), s.networkOut);
                if (result.getStatus() ==
                        SSLEngineResult.Status.BUFFER_OVERFLOW) {
                    s.networkOut = grow(s.networkOut,
                            s.engine.getSession().getPacketBufferSize());
                    return true;
                }
                if (result.getStatus() != SSLEngineResult.Status.OK) {
                    throw new SSLException(
                            "Unexpected handshake wrap: " + result);
                }
                boolean sent = send(s);
                return sent || result.getHandshakeStatus() != before;
            }
            case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> {
                if (before ==
                        SSLEngineResult.HandshakeStatus.NEED_UNWRAP &&
                        s.networkIn.position() == 0) {
                    return false;
                }
                s.networkIn.flip();
                SSLEngineResult result =
                        s.engine.unwrap(s.networkIn, s.applicationIn);
                s.networkIn.compact();

                if (result.getStatus() ==
                        SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    if (s.networkIn.capacity() <
                            s.engine.getSession().getPacketBufferSize()) {
                        s.networkIn = grow(s.networkIn,
                                s.engine.getSession().getPacketBufferSize());
                        return true;
                    }
                    return false; // Đợi thêm TLS bytes của peer.
                }
                if (result.getStatus() ==
                        SSLEngineResult.Status.BUFFER_OVERFLOW) {
                    s.applicationIn = grow(s.applicationIn,
                            s.engine.getSession().getApplicationBufferSize());
                    return true;
                }
                if (result.getStatus() != SSLEngineResult.Status.OK) {
                    throw new SSLException(
                            "Unexpected handshake unwrap: " + result);
                }
                return result.bytesConsumed() > 0 ||
                        result.bytesProduced() > 0 ||
                        result.getHandshakeStatus() != before;
            }
            case NOT_HANDSHAKING, FINISHED -> {
                return false;
            }
        }
        return false;
    }

    private static void handshake(Side client, Side server)
            throws Exception {
        client.engine.beginHandshake();
        server.engine.beginHandshake();
        for (int i = 0; i < 500; i++) {
            boolean a = handshakeStep(client);
            boolean b = handshakeStep(server);
            boolean done =
                    client.engine.getHandshakeStatus() ==
                            SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING &&
                    server.engine.getHandshakeStatus() ==
                            SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
            if (done) return;
            if (!a && !b) {
                throw new SSLException("Handshake stalled");
            }
        }
        throw new SSLException("Handshake loop limit");
    }

    private static void deliverAndUnwrap(Side receiver)
            throws Exception {
        int attempts = 0;
        while (receiver.networkIn.position() > 0 && attempts++ < 100) {
            receiver.networkIn.flip();
            SSLEngineResult r = receiver.engine.unwrap(
                    receiver.networkIn, receiver.applicationIn);
            receiver.networkIn.compact();
            if (r.getStatus() ==
                    SSLEngineResult.Status.BUFFER_OVERFLOW) {
                receiver.applicationIn = grow(receiver.applicationIn,
                        receiver.engine.getSession().getApplicationBufferSize());
            } else if (r.getStatus() ==
                    SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                if (receiver.networkIn.capacity() <
                        receiver.engine.getSession().getPacketBufferSize()) {
                    receiver.networkIn = grow(receiver.networkIn,
                            receiver.engine.getSession().getPacketBufferSize());
                }
                return;
            } else if (r.getStatus() ==
                    SSLEngineResult.Status.CLOSED) {
                return;
            } else if (r.getStatus() != SSLEngineResult.Status.OK) {
                throw new SSLException("Unexpected data unwrap: " + r);
            }
        }
    }

    private static void write(Side sender, Side receiver,
            byte[] plaintext) throws Exception {
        ByteBuffer input = ByteBuffer.wrap(plaintext);
        int attempts = 0;
        while (input.hasRemaining() && attempts++ < 100) {
            SSLEngineResult r =
                    sender.engine.wrap(input, sender.networkOut);
            if (r.getStatus() ==
                    SSLEngineResult.Status.BUFFER_OVERFLOW) {
                sender.networkOut = grow(sender.networkOut,
                        sender.engine.getSession().getPacketBufferSize());
                continue;
            }
            if (r.getStatus() != SSLEngineResult.Status.OK) {
                throw new SSLException("Unexpected data wrap: " + r);
            }
            send(sender);
            deliverAndUnwrap(receiver);
        }
        if (input.hasRemaining()) {
            throw new SSLException("Data loop limit");
        }
    }

    private static void close(Side sender, Side receiver)
            throws Exception {
        sender.engine.closeOutbound();
        for (int i = 0; !sender.engine.isOutboundDone() && i < 100; i++) {
            SSLEngineResult r = sender.engine.wrap(
                    EMPTY.duplicate(), sender.networkOut);
            if (r.getStatus() ==
                    SSLEngineResult.Status.BUFFER_OVERFLOW) {
                sender.networkOut = grow(sender.networkOut,
                        sender.engine.getSession().getPacketBufferSize());
                continue;
            }
            if (r.getStatus() != SSLEngineResult.Status.CLOSED &&
                    r.getStatus() != SSLEngineResult.Status.OK) {
                throw new SSLException("Close wrap failed: " + r);
            }
            send(sender);
        }
        if (!sender.engine.isOutboundDone()) {
            throw new SSLException("Close stalled");
        }
        deliverAndUnwrap(receiver);
    }

    private static SSLContext context(Path store, char[] pass,
            boolean useIdentity) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(store)) {
            ks.load(in, pass);
        }
        if (useIdentity) {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, pass);
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(kmf.getKeyManagers(), null, null);
            return ssl;
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, tmf.getTrustManagers(), null);
        return ssl;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: java EngineLoopbackDemo server.p12 trust.p12");
        }
        String fromEnv = System.getenv("LAB_PASS");
        if (fromEnv == null || fromEnv.length() < 6) {
            throw new IllegalArgumentException("LAB_PASS is required");
        }
        char[] pass = fromEnv.toCharArray();
        try {
            SSLEngine a = context(Path.of(args[1]), pass, false)
                    .createSSLEngine("localhost", 443);
            a.setUseClientMode(true);
            a.setEnabledProtocols(new String[]{"TLSv1.3"});
            SSLParameters cp = a.getSSLParameters();
            cp.setEndpointIdentificationAlgorithm("HTTPS");
            a.setSSLParameters(cp);

            SSLEngine b = context(Path.of(args[0]), pass, true)
                    .createSSLEngine();
            b.setUseClientMode(false);
            b.setEnabledProtocols(new String[]{"TLSv1.3"});

            Side client = new Side(a), server = new Side(b);
            client.peer = server;
            server.peer = client;

            handshake(client, server);
            byte[] msg = "tls-data".getBytes(StandardCharsets.UTF_8);
            write(client, server, msg);
            server.applicationIn.flip();
            byte[] received =
                    new byte[server.applicationIn.remaining()];
            server.applicationIn.get(received);

            close(client, server);
            close(server, client);

            System.out.println("handshakeProtocol=" +
                    a.getSession().getProtocol());
            System.out.println("messageRecovered=" +
                    Arrays.equals(msg, received));
            System.out.println("clientInboundDone=" + a.isInboundDone());
            System.out.println("serverInboundDone=" + b.isInboundDone());
            System.out.println("clientOutboundDone=" +
                    a.isOutboundDone());
            System.out.println("serverOutboundDone=" +
                    b.isOutboundDone());
        } finally {
            Arrays.fill(pass, '\0');
        }
    }
}
```

### 10.3. Biên dịch, chạy và kiểm tra output

Sau khi tạo `server.p12`/`trust.p12` và set biến `LAB_PASS` đúng như phần trên:

```bash
javac -encoding UTF-8 EngineLoopbackDemo.java
java EngineLoopbackDemo server.p12 trust.p12
```

**Stdout mong đợi trên JDK 17+ có TLS 1.3/KeyStore provider tương thích:**

```text
handshakeProtocol=TLSv1.3
messageRecovered=true
clientInboundDone=true
serverInboundDone=true
clientOutboundDone=true
serverOutboundDone=true
```

Vì private/public key và TLS session được sinh ngẫu nhiên, **không** dựa vào việc TLS record bytes giống nhau qua các lần chạy. Đầu ra trên là trạng thái logic, không hiển thị secrets.

**Đọc code theo trình tự:**

1. `context(...,true)` tải KeyStore chứa private key của server qua `KeyManagerFactory`; `context(...,false)` tải truststore chứa certificate lab qua `TrustManagerFactory`. Client kiểm tra chứng chỉ server bằng truststore, không dùng trust-all; `HTTPS endpoint identification` kiểm tra tên `localhost`.
2. `Side` cấp `networkIn`/`networkOut` theo `getPacketBufferSize()` và `applicationIn` theo `getApplicationBufferSize()`. Chúng bắt đầu ở **write mode**, giúp `send` và `unwrap` xử lý nhất quán.
3. `handshakeStep` chạy `NEED_TASK`, `NEED_WRAP` và `NEED_UNWRAP`. Với `BUFFER_OVERFLOW`, mở rộng **đích** thích hợp; với `BUFFER_UNDERFLOW`, `compact` giữ bytes và chờ peer có thêm network data. Branch `NEED_UNWRAP_AGAIN` để biết trạng thái API, vốn chủ yếu dùng với DTLS.
4. `send` mô phỏng `write/network transport` bằng copy TLS bytes từ `networkOut` sang `networkIn` của peer. Trong lab, peer có thể nhận hết bytes ngay. **Socket thật có partial writes/reads**, nên không được đem `send` thay nguyên hàm I/O mạng.
5. `write` đưa `tls-data` qua `client.wrap`; server gọi `unwrap` để khôi phục plaintext. Mọi bước cần kiểm tra `Status` và không trả về plaintext chưa được bảo vệ/xác minh theo TLS.
6. `close(client,server)` gọi `closeOutbound`, sinh close_notify qua `wrap(EMPTY)` và đưa sang server `unwrap`. Sau đó server làm tương tự về client.
7. `isInboundDone/isOutboundDone` kiểm tra trạng thái engine sau khi **đã nhận/gửi close_notify trong mô phỏng**. Trên socket thật còn phải đảm bảo kernel đã gửi hết bytes trước khi đóng transport.
8. `grow` đặt giới hạn một buffer để ngăn bộ nhớ nở vô hạn; production cần buffer pools, per-connection budget và chiến lược xử lý nếu vượt giới hạn.
9. `LAB_PASS` được đọc từ environment chỉ để thao tác lab, không xuất raw private key. `Arrays.fill(pass, '\0')` giảm thời gian giữ bản char[]; biến environment/String khác vẫn cần quản trị bằng secret manager/ACL theo policy.

**Quan trọng:** chương trình là fixture chứng minh luồng cơ bản **cho một message nhỏ** trên TLS 1.3 trong RAM, không phải implementation NIO server sẵn sàng production. Nó không triển khai socket fairness, partial writes, nhiều request concurrent, memory pressure, cancellation, application framing, shutdown timeout, TLS renegotiation/DTLS retransmission hoặc mọi trường hợp provider-specific handshake. Các phần tiếp theo chỉ rõ những việc cần bổ sung.

Khi học xong, xóa riêng các tệp chứa **private key lab** và biến environment trong phiên shell, ví dụ `unset LAB_PASS` trên Bash. Không copy chứng chỉ tự ký lab vào truststore hệ thống/production. Nếu muốn kiểm thử TLS 1.2, cần thay protocol ở cả hai engine, thử lại đầy đủ chứ không giả định behavior close/handshake y hệt.

## 11. Từ mô phỏng RAM sang `SocketChannel + Selector`

Trong một Java NIO server thực tế, network buffers cần quản lý **các thao tác bất đồng bộ và partial I/O**.

### Luồng đọc

```text
OP_READ fired
    |
    v
readBytes = channel.read(networkIn in WRITE mode)
    |
    +-- >0 --> networkIn.flip()
    |          engine.unwrap(networkIn, applicationIn)
    |          networkIn.compact()
    |                |
    |                +-- OK: xử lý result, handshake và plaintext
    |                +-- UNDERFLOW: giữ remainder, đợi OP_READ hoặc tăng capacity cần thiết
    |                +-- OVERFLOW: drain/grow applicationIn, retry
    |                +-- CLOSED: xử lý inbound close_notify, lên lịch outbound close
    |
    +-- =0 --> chờ sự kiện kế tiếp, KHÔNG spin
    |
    +-- -1 --> peer transport EOF; kiểm tra close_notify / closeInbound
```

**Đọc đủ trước khi unwrap** không có nghĩa đợi `networkIn` đầy; một buffer có thể chứa một record hoàn chỉnh khi mới dùng vài trăm bytes. Thử `unwrap` với bytes đang có rồi quyết định dựa trên status. Khi có nhiều TLS records trong `networkIn`, tiếp tục xử lý đến khi dưới ngưỡng, thiếu input, output đầy hoặc fairness budget hết.

### Luồng ghi

```text
Có app bytes hoặc engine NEED_WRAP/CLOSING
    |
    v
engine.wrap(applicationOut, networkOut in WRITE mode)
    |
    +-- BUFFER_OVERFLOW --> flush networkOut hoặc grow networkOut
    +-- OK/CLOSED -------> networkOut.flip()
                           channel.write(networkOut)
                                |
                                +-- partial write --> giữ remainder,
                                |                    chờ OP_WRITE
                                |
                                +-- all sent -------> networkOut.compact()
                                |                    (hoặc clear khi hết bytes)
                                |
                                +-- exception ------> close/error policy
```

Nếu `channel.write(...)` ghi 0 bytes, **đừng** gọi lại trong vòng lặp bận. Đăng ký `OP_WRITE`, tiếp tục khi socket có khả năng nhận; trong nhiều selector implementation, để `OP_WRITE` luôn bật gây wakeup liên tục, nên tắt khi không có output pending.

**Bảo vệ dữ liệu khi write một phần:**

```java
networkOut.flip();
channel.write(networkOut);
networkOut.compact(); // giữ bytes chưa được channel tiêu thụ
```

Đây là snippet ngắn dùng khi `networkOut` ban đầu ở **write mode**. Nếu channel ghi hết, `compact()` trở lại write mode position=0; nếu ghi một phần, `compact()` giữ TLS bytes còn lại để lần `OP_WRITE` sau. Không gọi `clear()` sau partial write.

### Khi vừa có pending output vừa có handshake NEED_UNWRAP

Có thể phải **gửi xong bytes đã sinh** trước khi peer phản hồi handshake, nhưng vẫn cần đọc các bytes inbound đến và chạy tasks. Hãy thiết kế state machine mà mỗi connection có bounded work per event, queue/app-IO riêng, và logic phân bổ `OP_READ/OP_WRITE` theo trạng thái. Đừng giữ lock toàn hệ thống trong khi chạy trust manager hoặc network I/O.

## 12. Đóng kết nối theo trạng thái và bảo đảm cleanup

Một thiết kế production nên có các phase rõ ràng, không dùng `boolean closed` chung cho mọi nghĩa:

| Phase | Công việc cần hoàn thành |
| --- | --- |
| `OPEN` | Cho phép app writes, wrap/unwrap bình thường |
| `DRAIN_APP` | Ngừng nhận thêm app messages mới, đưa các messages đã chấp nhận vào TLS records theo policy |
| `SEND_CLOSE_NOTIFY` | `closeOutbound`, tiếp tục `wrap(EMPTY)` khi cần, flush toàn bộ TLS outbound bytes |
| `WAIT_PEER_CLOSE` | Chờ `unwrap` xử lý close_notify của peer, có deadline; không đợi vô hạn |
| `TRANSPORT_CLOSE` | Khi đạt policy hoặc đã xác định error, ngừng selector interest, hủy task/queue, đóng socket |
| `RELEASE` | Trả pooled buffers, giải phóng connection state, metrics, không tái dùng engine đã đóng |

Không nhất thiết mọi ứng dụng phải đợi vô hạn “đối phương close_notify trước”: đặt **shutdown timeout**. Nếu timeout và transport phải đóng, ghi nhận rằng connection **không đóng TLS hoàn chỉnh**, không biến forced close thành success về mặt bảo mật.

Với connection đột ngột bị reset, I/O exception hoặc EOF thiếu close_notify, cần đánh dấu **truncation/unclean close**. Nếu ứng dụng chỉ xử lý message đã đầy đủ và có framing/authentication riêng, có thể có chính sách riêng cho việc giữ các message đã commit; điều đó **khác** với tuyên bố TLS connection hoàn tất cleanly.

**Không để pool tái sử dụng `SSLEngine` đã đóng**, đặc biệt khi key/session state thay đổi. `SSLSession` có thể được cache/resumed trong JSSE theo cơ chế TLS khác, nhưng đây **không** phải tái sử dụng chính object `SSLEngine` cho nhiều peer.

### `closeInbound()` có phải luôn được gọi?

Không. Khi peer gửi close_notify được `unwrap` xử lý, `isInboundDone()` trở thành true theo engine; gọi `closeInbound` **để giả vờ đã nhận close_notify** là sai. Chỉ khi transport kết thúc mà engine chưa nhận closure, dùng `closeInbound` để báo inbound không còn dữ liệu, và **xử lý `SSLException` nếu có**. Đừng “dập lỗi” từ closure alert bằng `try/catch(Exception ignored)`.

### Tài nguyên bộ đệm sau khi close

- Java heap `ByteBuffer.allocate` không cần `free()`; không giữ reference để GC thu hồi khi không còn owner.
- Direct buffers `ByteBuffer.allocateDirect` nằm ngoài heap managed memory, việc giải phóng thực tế do JVM/cleaner và provider quản lý; tránh gọi API nội bộ/`sun.misc.Unsafe` để ép unmap/free không kiểm soát.
- Nếu dùng **buffer pool**, **trả buffer chính xác một lần** sau khi không còn `wrap/unwrap`/socket write/task sử dụng nó. Không đưa buffer vào pool khi `networkOut` vẫn còn bytes chưa gửi hoặc `networkIn` còn mảnh TLS record.
- Plaintext `applicationIn/applicationOut` có thể chứa nội dung nhạy cảm. `clear()` và `compact()` chỉ đổi metadata, **không xóa dữ liệu cũ**; khi cần zeroization, chủ động overwrite nội dung có kiểm soát trước khi tái sử dụng, nhận thức giới hạn JVM copies/GC.
- Tránh giữ `ByteBuffer.slice()`/`duplicate()` sau khi owner đã trả backing buffer vào pool; có thể tạo data race hoặc lộ plaintext giữa kết nối.

## 13. Capacity planning, pooling và backpressure

Một connection có thể chiếm nhiều buffer với capacity vài chục KiB hoặc cao hơn. Khi có hàng nghìn connection, việc cấp phát dự phòng quá lớn gây áp lực heap/direct memory/GC. Ngược lại buffer quá nhỏ dẫn đến overflow/underflow lặp lại, copy và allocation.

### Quy tắc thiết kế

1. **Lấy size theo `SSLSession`**, nhưng đừng coi session getter là một allocation bắt buộc cho tất cả buffer cùng lúc. Có thể cấp phát lazy, pool theo size classes, tùy tải.
2. Đặt **trần bytes per connection**, trần global direct memory/heap, số handshake đồng thời và số delegated tasks. Không vì một peer gửi dữ liệu sai mà tăng buffer vô hạn.
3. Không cộng dồn app messages chưa tiêu thụ: nếu application consumer chậm, **dừng đọc/giảm OP_READ** trong phạm vi hợp lý, có backpressure vào nguồn và giới hạn queue.
4. **Pool** network buffers theo kiểu direct nếu đo thực tế có lợi cho socket I/O; heap buffers có thể rẻ hơn cho logic business/plaintext. Không mặc định direct luôn nhanh nhất.
5. Đặt buffer ownership rõ ràng, không chia sẻ writable buffer giữa hai `SSLEngine` cùng lúc, kể cả khi muốn tiết kiệm memory.
6. Chỉ tăng buffer khi có bằng chứng cần thiết: `BUFFER_UNDERFLOW` với packet capacity quá nhỏ, `BUFFER_OVERFLOW` với output capacity thiếu hoặc consumer chưa drain.
7. Khi memory quota bị chạm, ưu tiên fail/close có metric và giảm tải thay vì OOM toàn JVM.
8. Hạn chế số retries của từng operation trong một event để tránh peer gây CPU starvation; nếu không còn progress, đợi I/O/task tương ứng.

Bảng công thức sơ bộ:

```text
memoryPerConnection ~= networkInCapacity
                     + networkOutCapacity
                     + applicationInCapacity
                     + queuedAppOutBytes
                     + bufferPoolOverhead
                     + TLS/session/key material overhead

total ~= sum(active connection memory)
         + global caches + executor queues + JVM overhead
```

Đây là **ước lượng để lập budget**, không phải phép đo chính xác JVM native memory. TLS provider còn dùng object/session caches/temporary buffers; nên đo peak/RSS/direct buffer pool, GC allocation, write queue và handshake concurrency.

### Metrics hữu ích

| Metric | Ý nghĩa |
| --- | --- |
| `sslengine_wrap_status_total{status="BUFFER_OVERFLOW"}` | Output network buffer bị thiếu |
| `sslengine_unwrap_status_total{status="BUFFER_UNDERFLOW"}` | Input TLS record bị phân mảnh hoặc capacity không đủ |
| `sslengine_unwrap_status_total{status="BUFFER_OVERFLOW"}` | Plaintext buffer đầy hoặc consumer chậm |
| `sslengine_handshake_duration`, `task_queue_delay` | Theo dõi latency/độ nghẽn TLS handshake và delegated tasks |
| `tls_pending_network_out_bytes` | Áp lực pending socket writes |
| `tls_pending_app_in_bytes` | Áp lực application consumer/backpressure |
| `tls_unclean_eof_total` | Kết nối bị cắt thiếu close_notify |
| `tls_clean_close_total` | Đã xử lý close_notify đầy đủ |
| `tls_buffer_grows_total`, `tls_buffer_cap_exceeded_total` | Churn/capacity pressure và malicious/oversized records |
| `tls_active_connections`, `pool_bytes_in_use` | Resource budget và pool leak |

Gắn metrics theo provider/TLS version và high-level service, tránh cardinality quá lớn do connection ID/remote IP cho mọi sample. Đừng log plaintext TLS hoặc private keys để debug; với `-Djavax.net.debug=ssl,handshake` chỉ bật trên hệ thống test có giới hạn và hiểu dữ liệu xuất ra.

## 14. Ma trận kiểm thử cần thiết

| Kịch bản | Cách tạo | Kết quả đúng |
| --- | --- | --- |
| `wrap` output quá nhỏ | `EngineStatusProbe` `ByteBuffer.allocate(1)` | `BUFFER_OVERFLOW`; không mất app data |
| `unwrap` input rỗng | Server engine mới, network buffer rỗng | `BUFFER_UNDERFLOW`; đợi network |
| TLS record chia thành 2–3 mảnh | Socket/test transport chỉ chuyển từng phần | Lưu bytes chưa xử lý qua `compact` và ghép đúng |
| Hai TLS records trong một lần read | Peer gửi nhiều record trước một `read` | Xử lý lần lượt đến underflow/no progress, không bỏ data |
| Plaintext buffer đầy | `unwrap` với destination thiếu | `BUFFER_OVERFLOW`; drain/grow app buffer, retry |
| Socket partial write | Channel chỉ tiêu thụ phần của `networkOut` | Giữ remainder, chờ `OP_WRITE`; không gửi trùng |
| `SocketChannel.read()==0` | Non-blocking I/O chưa có data | Không spin |
| `SocketChannel.read()==-1` khi chưa close_notify | Ngắt transport đột ngột | Truncation/unclean EOF; `closeInbound` theo policy và xử lý exception |
| Client close_notify rồi server phản hồi | `EngineLoopbackDemo` | Cả hai `isInboundDone`/`isOutboundDone` true |
| Mất close_notify ở một chiều | Bỏ bản tin close_notify trong test transport | Không báo clean close |
| Handshake `NEED_TASK` chậm | Trust manager tác vụ mô phỏng delay | Không block selector toàn hệ thống; bounded executor |
| Peer gửi liên tục, consumer chậm | Giới hạn application processing | Backpressure không bùng RAM |
| Vượt memory cap | Cấu hình buffer cap nhỏ có chủ ý | Fail có kiểm soát, không endless grow |
| Hết hạn/cert không đáng tin | Dùng keystore khác/hostname mismatch trong lab | Handshake thất bại, không tắt trust check để workaround |
| TLS 1.2/1.3 và provider khác nhau | Test trên JDK/provider mục tiêu | Xác minh state machine tương thích, không dựa vào một trace cụ thể |

Trong test tích hợp, **đừng tạo kết quả mong muốn bằng cách bắt mọi exception rồi trả success**. Đặc biệt lỗi certificate, TLS record integrity, close_notify/truncation và buffer cap phải là lỗi phân biệt được.

## 15. Lỗi thường gặp và hướng sửa

| Sai lầm | Vì sao sai | Sửa thế nào |
| --- | --- | --- |
| Xem `BUFFER_UNDERFLOW` là TLS corrupt | Thiếu bytes chưa có nghĩa record sai | Giữ remainder, chờ thêm bytes, kiểm tra capacity |
| `clear()` network input sau underflow | Xóa logic bytes của record còn thiếu | `compact()` rồi đọc tiếp |
| Gọi `unwrap` lặp vô hạn khi không có thêm bytes | Busy loop, CPU tăng | Đợi `OP_READ` hoặc event/task có progress |
| Cứ underflow là nhân đôi buffer | Tăng RAM vô hạn, che lỗi stall | So `getPacketBufferSize()` và giới hạn capacity |
| Thấy overflow ở `unwrap` rồi đọc thêm socket | Sai buffer: output app bị đầy | Drain/grow `applicationIn` |
| Thấy overflow ở `wrap` rồi tăng application buffer | Sai buffer: output network bị đầy | Flush/grow `networkOut` |
| `clear()` networkOut sau partial socket write | Mất TLS bytes, phá record stream | `flip → write → compact` |
| Bỏ qua `bytesConsumed()` | Có thể mất plaintext hoặc đếm sai | Dựa vào ByteBuffer positions và result counters |
| `OK` được xem là handshake xong | `NEED_TASK/NEED_WRAP` có thể còn | Xử lý cả `HandshakeStatus` |
| Chạy delegated task nặng trên selector | Block hàng trăm connections | Bounded executor và per-engine ownership |
| `closeOutbound()` rồi đóng socket ngay | close_notify chưa được gửi | `wrap(EMPTY)`, flush TLS bytes trước |
| `isOutboundDone()==true` nghĩa socket queue rỗng | Engine không quản lý kernel/socket pending bytes | Theo dõi networkOut và socket write completion |
| Nuốt `SSLException` từ `closeInbound` | Che connection bị truncated | Đánh dấu unclean EOF và kiểm tra app framing |
| Tái sử dụng `SSLEngine` đã đóng | Engine state không được reset cho connection mới | Tạo engine mới |
| Tưởng `ByteBuffer.clear()` xóa plaintext khỏi RAM | Chỉ reset metadata position/limit | Zeroization riêng theo policy và lifecycle |
| Trả buffer vào pool khi còn slice/duplicate đang dùng | Có thể rò dữ liệu / race | Buffer ownership, refcount/lifecycle rõ |
| Alloc per handshake không giới hạn | Tấn công tạo nhiều handshake gây OOM/GC | Connection quota, memory cap, bounded tasks |
| Chỉ test happy path | Lỗi xuất hiện dưới fragmentation/partial write | Thử underflow/overflow/truncation/slow consumer |
| Bật trust-all để demo chạy | Mất TLS identity verification | Truststore lab đúng và endpoint identification |
| Copy nguyên demo RAM vào production | Không có selector/backpressure/timeout/protocol integration | Xây event loop và tests theo môi trường thật |

## 16. Bài tập thực hành

1. **Nhận diện buffer:** với `wrap` và `unwrap`, chỉ ra buffer nguồn/đích và chứng minh vì sao cùng `BUFFER_OVERFLOW` nhưng phải tăng buffer khác nhau.
2. **Đọc trạng thái:** `Status.OK`, `HandshakeStatus.NEED_WRAP`, `bytesConsumed=0`, `bytesProduced=125`. Vì sao đây có thể là handshake record chứ không phải app ciphertext?
3. **Underflow có fragmentation:** peer gửi TLS record theo ba mảnh. Mô tả `position/limit` trước `unwrap`, sau `compact` và khi nhận mảnh thứ ba. Điều gì mất nếu gọi `clear` thay `compact`?
4. **Overflow khi socket chậm:** `networkOut` có pending record và `channel.write` chỉ ghi 20%. Thiết kế `OP_WRITE` và `compact` để gửi nốt, không tạo record trùng.
5. **Plaintext buffer đầy:** `unwrap` trả `BUFFER_OVERFLOW` trong khi `networkIn` có đủ bytes. Đề xuất hai phương án sửa: drain consumer hoặc grow buffer, nêu ưu/nhược dưới tải cao.
6. **Thực nghiệm Java:** chạy `EngineStatusProbe` và giải thích vì sao `wrapConsumed=0` không ngăn client sinh handshake data khi buffer đủ.
7. **TLS loopback:** chạy `EngineLoopbackDemo` trên TLSv1.3, giải thích vì sao output có `messageRecovered=true` và bốn trạng thái Done đều true.
8. **Tắt hostname check:** trong một bản copy lab, bỏ `setEndpointIdentificationAlgorithm("HTTPS")` rồi nghĩ lại tác động: vì sao client có thể chấp nhận cert trusted nhưng tên máy không đúng? Tránh đưa thay đổi này vào production.
9. **Close bất thường:** mô phỏng chỉ gửi TCP FIN không có close_notify. Vì sao gọi `closeInbound` có thể ném exception? Nên xử lý dữ liệu đã nhận thế nào?
10. **Memory budget:** giả sử 30.000 connections, mỗi connection có hai packet buffers và một app buffer. Xây bảng capacity thực đo và hạn mức tổng RAM, tính tác động của việc tự động gấp đôi mọi buffer.
11. **TLS states:** phân biệt `NEED_UNWRAP` và `NEED_UNWRAP_AGAIN`. Khi nào cần đọc socket, khi nào không? Trạng thái nào liên quan chủ yếu DTLS?
12. **Delegated task:** mô phỏng trust manager chậm 200ms và giải thích vì sao không chạy trực tiếp trên selector có nhiều kết nối.
13. **Result counters:** lý giải khi `unwrap` tiêu thụ 200 TLS bytes nhưng sinh 0 plaintext. Có nghĩa bị mất dữ liệu không?
14. **Production checklist:** viết pseudo-code cho `Selector` bảo đảm flush trước close, không spin khi I/O trả 0, chặn memory cap, ghi metric underflow/overflow theo thao tác và không dùng plaintext trước TLS record integrity checks.
15. **Peer reset:** trong test, ngắt transport ngay sau khi application gửi nửa message. Phân biệt TLS stream truncation và “business message hoàn chỉnh”, đề xuất framing/transaction boundary.
16. **Buffer pooling:** vì sao `ByteBuffer.allocateDirect()` không có nghĩa nên cấp vô hạn? Mô tả quy trình trả pool khi app có `slice()` và socket còn pending writes.

**Tiêu chí hoàn thành:** giải thích đúng `BUFFER_UNDERFLOW` (chờ data) và `BUFFER_OVERFLOW` (thiếu output space); bảo toàn `ByteBuffer` qua `flip/compact`; xử lý handshake và close_notify bằng `SSLEngineResult`, phân biệt clean shutdown/truncation; và vận hành bộ đệm có trần tài nguyên/selector/backpressure hợp lý.

## Tài liệu tham khảo

- [Java SE 17 `SSLEngine` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLEngine.html): `wrap`, `unwrap`, buffer overflow/underflow, closure, `closeInbound/closeOutbound`.
- [Java SE 17 `SSLEngineResult` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLEngineResult.html): `getStatus`, `getHandshakeStatus`, `bytesConsumed/bytesProduced`.
- [Java SE 17 `SSLEngineResult.Status`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLEngineResult.Status.html): `OK`, `BUFFER_OVERFLOW`, `BUFFER_UNDERFLOW`, `CLOSED`.
- [Java SE 17 `SSLEngineResult.HandshakeStatus`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLEngineResult.HandshakeStatus.html): `NEED_TASK`, `NEED_WRAP`, `NEED_UNWRAP`, `NEED_UNWRAP_AGAIN`, `FINISHED`.
- [Java SE 17 `SSLSession`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSession.html): `getPacketBufferSize` và `getApplicationBufferSize`.
- [Java SE 17 `ByteBuffer`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): `position`, `limit`, `flip`, `compact`, `clear`.
- [Java SE 17 `SocketChannel`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SocketChannel.html): non-blocking reads/writes và end-of-stream.
- [Oracle Java Security Developer's Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): JSSE, SSLEngine examples, TLS initialization và session configuration.
- [RFC 8446 — The Transport Layer Security (TLS) Protocol Version 1.3](https://www.rfc-editor.org/rfc/rfc8446): alerts, record layer và `close_notify`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jsse-010">16.10 Tránh cấu hình tin mọi chứng thư hoặc bỏ qua kiểm tra hostname trong ví dụ ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

# 16.10 Tránh cấu hình tin mọi chứng thư hoặc bỏ qua kiểm tra hostname trong ví dụ ứng dụng

Một đoạn Java dùng HTTPS có thể **bắt tay TLS thành công, mã hóa dữ liệu và trả về HTTP 200** nhưng vẫn không bảo vệ được người dùng trước kẻ đứng giữa nếu người viết ví dụ cố tình cấu hình **tin mọi chứng thư (trust-all)** hoặc **không xác minh tên máy chủ (hostname verification)**. Đây là một trong những anti-pattern nguy hiểm nhất khi dùng **Java Secure Socket Extension (JSSE)**: lỗi chứng thư bị che đi thay vì được sửa, đoạn mã “chạy được trên máy tôi” bị sao chép sang ứng dụng thật, và thông tin đăng nhập/token có thể được gửi tới đối tác không được xác thực.

**Nguyên tắc của bài:** Một TLS client an toàn phải kiểm **cả (1) đường dẫn chứng thư tới trust anchor đáng tin** và **(2) danh tính endpoint đúng hostname/IP mà ứng dụng chủ định kết nối**. Không vì môi trường thử nghiệm hoặc lỗi certificate mà bỏ một trong hai phép kiểm. Ví dụ hoàn chỉnh ở đây sử dụng **trust manager chuẩn của Java và endpoint identification `HTTPS`**, đồng thời chủ động kiểm các trường hợp kết nối **phải bị từ chối** — không xây dựng một trust-all implementation có thể bị copy nhầm.

Bài học thuộc **16. JSSE** và tập trung vào các ví dụ ứng dụng Java 17+: chọn truststore có kiểm soát, bật xác minh endpoint cho `SSLSocket` thấp tầng, giữ mặc định an toàn của HTTPS clients, phân biệt lỗi trust và hostname, cùng chiến lược kiểm thử/code review ngăn cấu hình không an toàn lọt vào production.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích vì sao **mã hóa TLS không đủ** nếu đối tác không được xác thực, và hai cơ chế **PKIX chain validation** với **hostname verification** giải quyết hai câu hỏi khác nhau.
2. Nhận diện anti-pattern: trust manager chấp nhận mọi certificate, `HostnameVerifier` luôn trả `true`, vô hiệu hóa endpoint identification, tắt kiểm chữ ký/hạn dùng, hoặc tự động retry với cấu hình “insecure”.
3. Viết **JSSE client dùng `TrustManagerFactory` chuẩn**, truststore xác định và `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` khi cần.
4. Hiểu hành vi của `HttpsURLConnection`, `java.net.http.HttpClient`, `SSLSocket` và `SSLEngine`, cùng ranh giới trách nhiệm của `KeyManagerFactory` và `TrustManagerFactory`.
5. Chạy demo với CA local và chứng thư có SAN `DNS:localhost`: **đúng CA + đúng hostname thì pass; sai CA hoặc sai hostname phải fail**.
6. Thiết kế test âm tính/CI và sửa tận gốc lỗi `PKIX path building failed`, hostname mismatch, thiếu intermediate và certificate expired mà **không bỏ bảo mật**.

## 2. Điều TLS bảo vệ, điều TLS không thể làm khi bỏ xác thực

TLS có nhiều thuộc tính:

- **Confidentiality:** lưu lượng ứng dụng được mã hóa khi bắt tay và cipher suite được dùng đúng.
- **Integrity:** bản tin có cơ chế bảo vệ chống sửa đổi trong phiên TLS.
- **Endpoint authentication:** client cần biết mình đã thiết lập phiên an toàn với **đúng đối tượng**.
- **Protocol policy:** phiên bản TLS, cipher suite, certificate purpose, trust, identity và các ràng buộc được kiểm theo chính sách.

Nếu client **tin mọi certificate**, một kẻ đứng giữa có thể tạo certificate tự ký mang khóa của họ, thiết lập TLS riêng với client, giải mã request rồi chuyển tiếp tới server thật qua một phiên TLS thứ hai. Cả hai phiên đều có thể “mã hóa mạnh”; **vấn đề nằm ở chỗ client đã xác thực nhầm đối tác**.

Nếu client **kiểm CA chain nhưng bỏ hostname check**, kẻ có certificate hợp lệ cho **một hostname khác** cũng có thể được chấp nhận khi ứng dụng muốn kết nối endpoint mục tiêu. Ví dụ CA nội bộ đã cấp chứng thư cho `inventory.internal`, nhưng client cấu hình gọi `payments.internal`: certificate chain có thể hợp lệ và vẫn **không phải danh tính cần gặp**.

~~~text
Ứng dụng cần gọi: https://payments.example.test
                |
                v
             TLS peer
                |
       +--------+---------+
       |                  |
       v                  v
   PATH TRUST         ENDPOINT NAME
  CA/PKIX chain       DNS/IP trong URL
  chữ ký/hạn dùng     so với SAN leaf
       |                  |
       +--------+---------+
                |
          CẢ HAI PASS?
           /       \
         Có         Không
         |            |
   có thể tiếp tục  HỦY bắt tay
   theo policy       KHÔNG fallback insecure
~~~

**Tin chuỗi chứng thư không đồng nghĩa khớp hostname**, và **khớp hostname không đồng nghĩa CA được tin**. Hai điều kiện đều bắt buộc trong HTTPS client đúng chuẩn; việc TLS có trả `SSLSession` hay phiên đã dùng AES-GCM không làm thay đổi yêu cầu này.

### 2.1. Bảng quyết định tối thiểu

| Chain/CA tin cậy | Hostname/IP đúng | Hành vi đúng |
| --- | --- | --- |
| Pass | Pass | Có thể chấp nhận khi các kiểm tra TLS và chính sách khác đạt |
| Pass | **Fail** | **Reject**: chứng thư của host khác |
| **Fail** | Pass | **Reject**: CA/trust anchor không phù hợp |
| **Fail** | **Fail** | **Reject**: cả hai điều kiện sai |

Từ “có thể” là quan trọng: còn phải xét **KeyUsage, EKU, thời hạn, thuật toán, revocation, policy**, và **authorization** ở lớp ứng dụng. Một chứng thư hợp lệ không mặc định cho phép thực hiện lệnh thanh toán hoặc truy cập tenant bất kỳ.

## 3. Cấu hình “trust-all” thực sự phá điều gì?

Trong JSSE, trust manager chịu trách nhiệm xác minh certificate chain theo chính sách. Một trust manager bình thường dùng tập **trusted root CAs/certificates được chọn trước**, kiểm chứng chain, thuật toán và các điều kiện liên quan trong ngữ cảnh TLS.

Anti-pattern trust-all thường có dạng tư duy:

~~~text
Nếu TLS báo "certificate not trusted":
    thay trust manager chuẩn bằng bản luôn trả "accept"
    hoặc bỏ hết logic checkServerTrusted(...)
    hoặc đổi accepted issuers thành thứ gì đó để tránh lỗi
Kết quả: handshake có thể pass với certificate của bất kỳ kẻ nào.
~~~

Đây là **mô tả anti-pattern**, không phải hướng dẫn viết/cài trust manager bỏ qua kiểm tra. Không đưa một `X509TrustManager` rỗng hoặc một `SSLContext` trust-all vào chương trình thực hành vì code dễ bị sao chép. Tương tự, `getAcceptedIssuers()` tự nó **không quyết định certificate hợp lệ**; việc giả dữ liệu ở đây không thay thế xác minh chain thực sự.

Một ứng dụng áp dụng trust-all có thể gửi:

- Bearer token, cookie phiên, API key hoặc thông tin tài khoản;
- Dữ liệu người dùng/thanh toán;
- Nội dung yêu cầu điều khiển hệ thống;
- Trả lời quan trọng mà ứng dụng tin là từ server thật;

tới một đối tượng sở hữu private key của certificate **không hề được CA mà người dùng tin chứng nhận**.

**Mã hóa nhưng không xác thực đúng peer không thể được coi là HTTPS an toàn.** Bật encryption cipher suite mạnh hơn không khôi phục việc xác thực đã bị loại bỏ.

### 3.1. “Chỉ trust-all cho localhost/dev” có được không?

Không nên dùng trust-all làm ví dụ mẫu hoặc workaround:

1. Flag dev/test có thể bị bật nhầm ở staging/production.
2. Một thư viện dùng `SSLContext.setDefault` hoặc property toàn JVM có thể làm mất bảo mật của **những HTTP clients khác** cùng process.
3. Proxy, công cụ kiểm thử mạng, Wi-Fi công cộng hoặc kẻ kiểm soát DNS có thể chủ động tạo certificate bất kỳ để đánh chặn lưu lượng.
4. Cách chữa tạm không hướng dẫn người học **nguyên nhân thực**: CA chưa trust, hostname/SAN sai, thiếu intermediate hoặc certificate hết hạn.
5. Test chạy thành công với trust-all không kiểm chứng điều quan trọng nhất: ứng dụng **có từ chối peer không hợp lệ không**.

**Thay thế đúng cho môi trường phát triển:** tạo **CA dev riêng**, cấp leaf SAN đúng endpoint, đưa certificate CA dev đã xác minh vào **truststore chỉ dành cho dev**, dùng trust manager mặc định trên store đó, vẫn kiểm hostname.

### 3.2. Tại sao không “pin certificate” bằng trust-all?

Pinning public key/certificate (khi threat model cần) là **một ràng buộc bổ sung**, không phải giấy phép bỏ CA/hostname validation mặc định. Các triển khai pinning phải có kế hoạch backup pins, rotation, tránh khóa nhầm ứng dụng, và xác định rõ mối quan hệ với trust anchors. Một đoạn code trust-all rồi so string Subject hoặc một fingerprint lấy động từ kết nối **không phải pinning an toàn**; nó có thể pin luôn certificate attacker đưa vào lần đầu.

## 4. Bỏ hostname verification gây lỗi gì?

Một client có thể **tin CA** của certificate nhưng đang gọi **sai host**:

~~~text
URL mục tiêu: https://pay.example.com
Leaf được CA tin ký:
    SAN DNS: files.example.com

Chain validation = PASS
Name verification = FAIL
Quyết định an toàn = REJECT
~~~

Tắt hostname verification nghĩa là client **không còn yêu cầu certificate chứng minh tên đích**. Với CA nội bộ có khả năng cấp nhiều dịch vụ, một server hợp lệ khác có thể vô tình hoặc cố ý được dùng để mạo danh endpoint mục tiêu.

Các anti-pattern cần phát hiện:

- Một `HostnameVerifier` của `HttpsURLConnection` **luôn trả `true`**;
- Một client library có flag hoặc policy `NoopHostnameVerifier`, `allowAllHosts` hoặc tên tương đương; chú ý API phụ thuộc thư viện/version;
- Raw `SSLSocket` dùng trust manager chuẩn nhưng **không bật endpoint identification** khi đang dùng làm HTTPS client;
- `SSLEngine` không được cấu hình thuật toán xác thực endpoint hoặc thiếu peer host chính xác;
- “Retry fallback”: sau `SSLHandshakeException`, thử lại với client đã tắt hostname checking;
- Lấy hostname cần kiểm từ **certificate mà peer gửi** thay vì tên trong URL/config tin cậy;
- Tự viết bộ so khớp DNS bằng `endsWith` hoặc chấp nhận mọi wildcard.

Không xem `CN` trong Subject hoặc tên **alias** trong `KeyStore` là đủ thay **SAN**. Với chứng thư HTTPS hiện đại, certificate cần có `dNSName SAN` hoặc `iPAddress SAN` đúng loại theo reference identity mà client đã chọn.

### 4.1. SAN DNS và SAN IP không hoán đổi

| Đích client chủ định kết nối | Leaf SAN | Name check |
| --- | --- | --- |
| `https://localhost` | `DNS:localhost` | Pass nếu chain được tin |
| `https://127.0.0.1` | Chỉ `DNS:localhost` | **Fail**: thiếu IP SAN |
| `https://127.0.0.1` | `IP:127.0.0.1` | Pass về name nếu chain cũng được tin |
| `https://api.example.test` | `DNS:api.example.test` | Pass về name |
| `https://api.example.test` | `DNS:another.example.test` | **Fail** |
| `https://192.0.2.10` | `DNS:192.0.2.10` | Không thay `iPAddress` SAN hợp lệ |

DNS có thể resolve `api.example.test` thành địa chỉ IP cụ thể nhưng **tên tham chiếu vẫn là hostname URL**, không phải reverse DNS của IP. Nếu ứng dụng dùng TCP tới IP nhưng mong identity DNS, cần giữ **logical peer hostname** đúng ở tầng TLS/HTTP theo API.

**SNI (Server Name Indication)** chỉ giúp server chọn certificate khi phục vụ nhiều virtual hosts; SNI **không tự xác minh certificate do server trả về**. Client vẫn phải kiểm trust chain và SAN.

### 4.2. CN fallback, wildcard và IDN

- Tên `CN=api.example.com` trong Subject không nên dùng thay SAN. Một số implementations lịch sử có fallback đặc biệt khi thiếu SAN, nhưng **không dựa vào fallback đó cho cấp certificate hoặc kiểm thử HTTPS mới**.
- Wildcard `*.example.com` không được coi là hợp lệ cho vô hạn subdomain hay mọi cấp nhãn. Dùng thuật toán endpoint identification của HTTPS client chuẩn, không tự viết regex đơn giản.
- Internationalized domain names đòi quy tắc IDNA/Punycode và chuẩn so khớp tên phù hợp; `equalsIgnoreCase` trên Unicode string không phải giải pháp đầy đủ.
- SAN kiểu URI/email/workload identity có quy tắc riêng, không mặc nhiên thay DNS SAN cho HTTPS.

## 5. JSSE: ai có trách nhiệm kiểm chứng?

Một số API Java thường xuất hiện trong code:

| API | Vai trò | Điều phải giữ đúng |
| --- | --- | --- |
| `KeyStore` | Lưu identity key/cert hoặc trusted CA certificate | File/type/password/quyền truy cập đúng |
| `KeyManagerFactory` | Lấy private key và certificate chain để **trình danh tính của chính ứng dụng** | Không dùng key manager thay peer trust checking |
| `TrustManagerFactory` | Tạo trust manager chuẩn từ truststore | Chỉ import CA/peer cert được xác minh, không trust-all |
| `X509TrustManager` | Interface kiểm chain server/client theo cơ chế provider | Không thay bằng implementation chấp nhận mọi certificate |
| `X509ExtendedTrustManager` | Có phiên bản xử lý certificate với ngữ cảnh `Socket`/`SSLEngine` | Không bỏ qua endpoint constraints từ JSSE |
| `SSLContext` | Tập hợp key managers, trust managers, randomness/policy phiên TLS | Không thay default toàn JVM hoặc dùng manager insecure bừa |
| `SSLParameters` | Cấu hình protocol, cipher, endpoint identification, SNI... | Với raw HTTPS TLS cần `setEndpointIdentificationAlgorithm("HTTPS")` |
| `SSLSocket` / `SSLEngine` | API TLS tương đối thấp tầng | Cần biết logical peer name và chủ động bật endpoint check phù hợp |
| `HttpsURLConnection` | HTTPS client API | Giữ hostname verifier và trust chính thống |
| `java.net.http.HttpClient` | HTTP client JDK hiện đại | Giữ mặc định xác minh HTTPS, chỉ tùy chỉnh trust đúng phạm vi |

**Điều quan trọng:** Có thể endpoint check được triển khai trong call stack trust manager khi handshake có thông tin hostname, nhưng **về mặt an ninh vẫn là hai điều kiện khác nhau**. Nếu chỉ gọi `CertPathValidator("PKIX")`, bạn phải biết rằng API đó không tự nhận hostname URL để kiểm.

### 5.1. HTTPS clients cấp cao và raw TLS khác nhau

- `HttpsURLConnection` có xử lý HTTPS hostname verification mặc định; cài một verifier luôn trả true là xóa lớp bảo vệ này.
- `HttpClient` JDK 11+ cũng được thiết kế cho HTTPS endpoint verification theo cấu hình an toàn; không dùng property hoặc option thư viện để tắt vì gặp chứng thư dev.
- **Raw `SSLSocket`** không nhất thiết biết bạn đang dùng nó cho giao thức nào. Dùng trust manager chuẩn **chưa đủ** nếu bạn không cấu hình HTTPS endpoint identity.
- **`SSLEngine`** tương tự; phải đảm bảo peer host/port và `SSLParameters` mang policy cần thiết trước khi handshake.

Ví dụ cấu hình an toàn của raw client:

~~~java
SSLSocket tls = /* đã tạo đúng peer host/connection */;
SSLParameters p = tls.getSSLParameters();
p.setEndpointIdentificationAlgorithm("HTTPS");
tls.setSSLParameters(p);
tls.startHandshake();
~~~

Đây chỉ là **trích đoạn API**, không phải chương trình độc lập. Quan trọng là `"HTTPS"` được dùng trước `startHandshake()`, và **hostname phải là tên từ URL/config đã xác thực**, không được lấy từ certificate server gửi tới.

### 5.2. Không dùng `SSLContext.setDefault(...)` tùy tiện

`SSLContext.setDefault(...)` thay context mặc định có ảnh hưởng ngoài phạm vi một HTTP request/client. Một thay đổi không an toàn ở môi trường test có thể làm toàn bộ process tin certificates sai.

Ưu tiên xây **`SSLContext` riêng cho client cần CA nội bộ** và truyền vào builder/client cụ thể. Cấu hình truststore riêng làm **các CA được tin khác với tập mặc định**, không nhất thiết tự cộng thêm root mặc định. Nếu ứng dụng cần cả hai tập, phải thiết kế rõ chính sách bổ sung trust anchors và kiểm interop; không nối các store bằng cách tự viết trust-all.

## 6. Cấu hình truststore riêng đúng cách

Để kết nối một service có certificate do **CA nội bộ đã được xác minh** cấp, hãy:

1. Lấy **public root CA certificate** qua kênh quản trị đáng tin. Đối chiếu fingerprint và đối tượng có quyền quản lý CA.
2. Tạo một truststore dành cho môi trường/dịch vụ, chẳng hạn PKCS12, chứa **đúng trust anchor được phép**.
3. Trong Java, dùng `KeyStore.load` và `TrustManagerFactory.init(trustStore)` để tạo trust managers **chuẩn**.
4. Dùng `SSLContext.init(null, tmf.getTrustManagers(), new SecureRandom())` để lấy TLS context cho **client cụ thể**.
5. Với raw HTTPS TLS, bật `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")`. Với HTTPS client cấp cao, giữ hostname verification mặc định và không override bằng verifier vô điều kiện.
6. Test cả endpoint **đúng** và endpoint **sai**. “Kết nối thành công” là test chưa đủ.

Trích đoạn đọc truststore chuẩn:

~~~java
KeyStore trust = KeyStore.getInstance("PKCS12");
try (InputStream in = Files.newInputStream(trustStorePath)) {
    trust.load(in, password);
}
TrustManagerFactory tmf = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
tmf.init(trust);

SSLContext clientContext = SSLContext.getInstance("TLS");
clientContext.init(null, tmf.getTrustManagers(), new SecureRandom());
~~~

Đây là **mảnh ghép minh họa**, chưa phải chương trình độc lập: `trustStorePath`/`password` cần được khai báo, exception cần được xử lý. Đoạn hoàn chỉnh và đã kiểm chứng ở phần 8.

**Không hard-code password production**. Password bảo vệ file truststore, còn **việc chọn root certificate để tin mới là quyết định an ninh cốt lõi**: không chỉ bảo vệ password mà nhập một CA attacker vào store thì vẫn mất tính xác thực.

### 6.1. Identity keystore không phải truststore

~~~text
Server:
    identity keystore
       PrivateKeyEntry: private key + server cert chain

Client:
    truststore
       TrustedCertificateEntry: approved root CA certificate
       (và trust policy ứng dụng)

Client checks: chain to approved trust anchor + URL hostname to leaf SAN
~~~

`KeyManagerFactory` giúp server chứng minh quyền sở hữu private key, trong khi `TrustManagerFactory` giúp client xác minh certificate của peer. Chuyển server private key vào client truststore để “hết lỗi certificate” là sai và có thể làm lộ secret. Với mTLS, mỗi bên có identity riêng và trust policy riêng.

### 6.2. CA nội bộ và truststore mặc định

JDK thường cung cấp tập CA mặc định như `cacerts` (vị trí, nội dung, chính sách có thể khác theo JDK/distribution). Một `SSLContext` khởi tạo với truststore riêng **không mặc nhiên hợp nhất** CA nội bộ với mọi root của JDK. Đừng giả định một API sẽ fallback sang default roots sau khi đã nhận custom trust managers.

Ứng dụng cần thêm CA nội bộ nên xác định rõ phạm vi:

- **Client chỉ dùng trong một security domain:** truststore riêng hạn chế được CA tin, thường là lựa chọn đơn giản.
- **Client cần CA công khai và nội bộ:** dùng cấu hình trust policy đã được thiết kế/kiểm kỹ theo platform hoặc thư viện; ghi rõ nguồn roots, không viết bộ trust manager thử lần lượt các CA tùy tiện không có kiểm soát.
- **Nhiều microservices:** mỗi môi trường/tenant có chính sách cấp certificate và rotation, không trust toàn bộ CA dev ở production.

## 7. Thực hành: tạo CA và chứng thư local nhưng vẫn giữ xác minh

Bài lab được thực hiện hoàn toàn ở **localhost/loopback** trong một **thư mục thử nghiệm mới**, dùng JDK 17+ và `keytool`. Ta tạo:

- `root.p12`: CA root demo đáng tin cho nhánh positive.
- `other-root.p12`: **CA khác** không liên quan, dùng làm trust anchor **sai** để chứng minh JSSE từ chối.
- `server.p12`: server private key và leaf được **root.p12** ký, SAN chỉ có `DNS:localhost`.
- `trusted.p12`: truststore client chứa **root thực sự cấp leaf**.
- `untrusted.p12`: truststore client chỉ chứa **other-root**, nên chain server không được tin.

**Không dùng các certificate/keystore demo trong production**; private key CA và server trong bài thực hành được bảo vệ bởi password cố định mẫu, dễ đoán; validity ngắn và không có hạ tầng CRL/OCSP thật. Hãy chạy từng lệnh trong **thư mục trống tạm**, không đụng các file keystore đang dùng.

### 7.1. Tạo root CA demo được phép tin trong bài lab

~~~bash
keytool -genkeypair -alias lab-root -keyalg RSA -keysize 2048 -dname "CN=Trusted Demo Root,O=Training,C=VN" -validity 7 -ext "bc=ca:true" -ext "ku=keyCertSign,cRLSign" -keystore root.p12 -storetype PKCS12 -storepass demo-pass-123 -keypass demo-pass-123 -noprompt
~~~

Root CA certificate được ký bằng chính private key CA. Self-signed **không có nghĩa tự động được tin**; trong bài, **client chủ động chọn root này** bằng cách import vào `trusted.p12`.

### 7.2. Xuất certificate root public

~~~bash
keytool -exportcert -alias lab-root -keystore root.p12 -storepass demo-pass-123 -rfc -file root.crt
~~~

`root.crt` chỉ chứa **certificate công khai**, không chứa private key CA. Trong hệ thống thực, phải xác minh **fingerprint và nguồn phân phối certificate** qua kênh độc lập trước khi import.

### 7.3. Tạo một CA khác để kiểm negative trust test

~~~bash
keytool -genkeypair -alias other-root -keyalg RSA -keysize 2048 -dname "CN=Unrelated Demo Root,O=Training,C=VN" -validity 7 -ext "bc=ca:true" -ext "ku=keyCertSign,cRLSign" -keystore other-root.p12 -storetype PKCS12 -storepass demo-pass-123 -keypass demo-pass-123 -noprompt
~~~

~~~bash
keytool -exportcert -alias other-root -keystore other-root.p12 -storepass demo-pass-123 -rfc -file other-root.crt
~~~

Việc root thứ hai tự ký và cũng có `CA=true` **không cho nó quyền tin cậy certificate server** vốn được cấp bởi root thứ nhất. Truststore `untrusted.p12` chỉ chứa root khác này sẽ là negative test có thể tái hiện.

### 7.4. Tạo server identity với SAN DNS localhost

~~~bash
keytool -genkeypair -alias server-id -keyalg RSA -keysize 2048 -dname "CN=localhost,O=Training,C=VN" -validity 7 -ext "san=dns:localhost" -keystore server.p12 -storetype PKCS12 -storepass demo-pass-123 -keypass demo-pass-123 -noprompt
~~~

Lúc mới tạo, `server.p12` có một `PrivateKeyEntry` và certificate self-signed tạm thời. Đó chưa phải leaf do CA demo ký. Alias `server-id` được giữ nguyên ở các bước sau.

### 7.5. Tạo CSR PKCS#10 và CA đáng tin ký certificate

~~~bash
keytool -certreq -alias server-id -file server.csr -rfc -keystore server.p12 -storepass demo-pass-123 -ext "san=dns:localhost"
~~~

~~~bash
keytool -gencert -alias lab-root -infile server.csr -outfile issued.crt -rfc -keystore root.p12 -storepass demo-pass-123 -validity 7 -ext "san=dns:localhost" -ext "ku=digitalSignature,keyEncipherment" -ext "eku=serverAuth"
~~~

CA demo ký leaf có `SAN DNS:localhost` và `EKU serverAuth`. Một CA thật phải kiểm quyền đối với identity, chứng thư và policy cấp trước khi ký, không tự tin mọi yêu cầu SAN trong CSR.

### 7.6. Cài root và certificate reply vào đúng server alias

~~~bash
keytool -importcert -alias lab-root -file root.crt -keystore server.p12 -storepass demo-pass-123 -noprompt
~~~

~~~bash
keytool -importcert -alias server-id -file issued.crt -keystore server.p12 -storepass demo-pass-123 -noprompt
~~~

Bước sau cài **certificate reply vào chính PrivateKeyEntry alias `server-id`** để public key của leaf khớp private key đang được giữ. Không import leaf vào alias mới rồi tưởng server identity đã được thay đúng.

### 7.7. Tạo hai client truststores có chính sách rõ ràng

~~~bash
keytool -importcert -alias lab-root -file root.crt -keystore trusted.p12 -storetype PKCS12 -storepass demo-pass-123 -noprompt
~~~

~~~bash
keytool -importcert -alias other-root -file other-root.crt -keystore untrusted.p12 -storetype PKCS12 -storepass demo-pass-123 -noprompt
~~~

- `trusted.p12` chỉ tin root đã ký leaf → kết nối tới `localhost` có khả năng pass.
- `untrusted.p12` chỉ tin root khác → server certificate không có path tới root đó → **JSSE phải reject**.
- `server.p12` có private key server và certificate chain; **không có private key của hai root**.

**Cảnh báo:** `-storepass` trên dòng lệnh có thể lộ qua process listing/history; chỉ dùng ở môi trường demo cô lập. `-noprompt` bỏ xác nhận của `keytool`; đừng dùng để tự động import CA từ nguồn chưa được xác minh trong production.

### 7.8. Xem lại certificates/aliases

~~~bash
keytool -list -v -alias server-id -keystore server.p12 -storetype PKCS12 -storepass demo-pass-123
keytool -list -v -alias lab-root -keystore trusted.p12 -storetype PKCS12 -storepass demo-pass-123
keytool -list -v -alias other-root -keystore untrusted.p12 -storetype PKCS12 -storepass demo-pass-123
~~~

Điều cần kiểm: leaf thực tế có `DNSName: localhost`, chain về `lab-root`, còn `untrusted.p12` chứa **root khác**. Root trong `trusted.p12` là `TrustedCertificateEntry` chứ không phải private key CA.

## 8. Java 17+ hoàn chỉnh: đúng CA/tên thì pass, sai CA/tên thì fail

Đây là chương trình **runnable**, có thể biên dịch/chạy trên JDK 17+ trong cùng thư mục với các file `server.p12`, `trusted.p12`, `untrusted.p12` ở phần 7.

Chương trình không có một dòng code trust-all hoặc hostname-verifier vô hiệu hóa nào:

1. Tạo server TLS cục bộ bằng `KeyManagerFactory` từ `PrivateKeyEntry` trong `server.p12`.
2. Tạo hai clients bằng **`TrustManagerFactory` chuẩn**: một có root đúng và một chỉ có root không liên quan.
3. Mở TCP loopback bằng `InetAddress.getLoopbackAddress()` nhưng gán **logical peerName** cho `SSLSocket`.
4. Luôn bật **`SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")`** trước handshake.
5. Chứng minh ba kết quả mong muốn: **trusted + localhost** thành công; **unrelated CA + localhost** bị từ chối; **trusted + 127.0.0.1** bị từ chối vì thiếu IP SAN.

### 8.1. Source `SafeJsseDemo.java`

~~~java
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

public class SafeJsseDemo {
    static KeyStore load(String file, char[] password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(Path.of(file))) {
            ks.load(in, password);
        }
        return ks;
    }

    static SSLContext serverContext(KeyStore identity, char[] password)
            throws Exception {
        KeyManagerFactory km = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        km.init(identity, password);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(km.getKeyManagers(), null, new SecureRandom());
        return context;
    }

    static SSLContext clientContext(KeyStore trust) throws Exception {
        TrustManagerFactory tm = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tm.init(trust);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tm.getTrustManagers(), new SecureRandom());
        return context;
    }

    static void handshake(SSLContext context, int port, String peerName)
            throws Exception {
        // TCP luôn nối loopback. peerName là identity logic tin cậy
        // mà TLS cần đối chiếu với Subject Alternative Name của leaf.
        Socket tcp = new Socket(InetAddress.getLoopbackAddress(), port);
        try (SSLSocket tls = (SSLSocket) context.getSocketFactory()
                .createSocket(tcp, peerName, port, true)) {
            SSLParameters params = tls.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            tls.setSSLParameters(params);
            tls.startHandshake();
        }
    }

    static boolean rejected(SSLContext context, int port, String name)
            throws Exception {
        try {
            handshake(context, port, name);
            return false; // LỖI: kết nối xấu không bị từ chối.
        } catch (SSLHandshakeException expected) {
            return true; // PASS: JSSE từ chối ngay ở handshake.
        }
    }

    public static void main(String[] args) throws Exception {
        char[] password = "demo-pass-123".toCharArray();
        try {
            SSLContext server = serverContext(
                    load("server.p12", password), password);
            SSLContext trusted = clientContext(
                    load("trusted.p12", password));
            SSLContext untrusted = clientContext(
                    load("untrusted.p12", password));

            try (SSLServerSocket listener = (SSLServerSocket)
                    server.getServerSocketFactory().createServerSocket(
                            0, 10, InetAddress.getLoopbackAddress())) {

                Thread responder = new Thread(() -> {
                    for (int i = 0; i < 3; i++) {
                        try (SSLSocket accepted =
                                (SSLSocket) listener.accept()) {
                            accepted.startHandshake();
                        } catch (Exception ignored) {
                            // Chỉ trong demo: negative tests có thể làm
                            // client hủy handshake; server tiếp tục test.
                        }
                    }
                });
                responder.start();
                int port = listener.getLocalPort();

                handshake(trusted, port, "localhost");
                System.out.println("Trusted + localhost = accepted");

                System.out.println("Wrong CA rejected   = " +
                        rejected(untrusted, port, "localhost"));

                System.out.println("Wrong name rejected = " +
                        rejected(trusted, port, "127.0.0.1"));

                responder.join(10000);
                if (responder.isAlive()) {
                    throw new IllegalStateException("Server not finished");
                }
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SafeJsseDemo.java
java SafeJsseDemo
~~~

**Stdout đã được kiểm chứng bằng JDK, `keytool`, `javac` và `java` trong thư mục tạm** sau khi thực hiện các lệnh phần 7:

~~~text
Trusted + localhost = accepted
Wrong CA rejected   = true
Wrong name rejected = true
~~~

Dù key pair và các chữ ký được tạo ngẫu nhiên ở mỗi lần chạy, ba kết quả này ổn định theo bộ certificate/KeyStore và chính sách demo. Một môi trường Java hoặc JCE provider có policy thuật toán đặc biệt có thể từ chối cả positive case; khi đó cần kiểm lỗi thực tế, **không hạ cấp xác minh để “làm output giống”**.

### 8.2. Phân tích đúng các dòng stdout

**`Trusted + localhost = accepted`**:

- Server xuất trình certificate leaf do root `lab-root` ký.
- Client sử dụng `trusted.p12` chứa chính root đó, với trust manager chuẩn.
- `peerName="localhost"` khớp `dNSName SAN localhost` trên leaf.
- `SSLSocket.startHandshake()` thành công. Ở đây chưa làm HTTP request hay phân quyền ứng dụng; chỉ kiểm lớp TLS.

**`Wrong CA rejected = true`**:

- Tên đích vẫn `localhost`, vẫn đúng SAN.
- Client lại dùng `untrusted.p12` chỉ chứa root **không liên quan** đến issuer leaf.
- Không có path hợp lệ tới trust anchor client chấp nhận; JSSE ném `SSLHandshakeException`.
- Chương trình coi việc **từ chối** là kết quả test **thành công**. Không thử lại với trust-all.

**`Wrong name rejected = true`**:

- Client vẫn dùng `trusted.p12` nên chain được trust.
- Logical peer identity đổi thành IP literal `127.0.0.1`.
- Leaf chỉ có `DNS:localhost`, không có `iPAddress SAN:127.0.0.1`.
- `HTTPS` endpoint identification khiến JSSE từ chối ngay cả khi CA chain hợp lệ. Đây là lớp phòng vệ riêng, **không thể bỏ**.

### 8.3. Vì sao ví dụ không in chi tiết exception?

Để test đơn giản và độc lập với message text của từng provider, `rejected` chỉ bắt `SSLHandshakeException` và trả boolean. Message/exception chain cụ thể có thể khác giữa JDK, CA profile và thuật toán; **không nên assert nguyên văn exception string**.

Trong production, hãy giữ nguyên stack/cause có lọc thông tin nhạy cảm và ghi nhật ký quản trị phù hợp. Đừng bắt mọi exception rồi “return success”; test fail nếu xuất hiện lỗi ngoài dự kiến. Thread server trong demo cố tình bỏ qua lỗi handshake ở **negative test** và không in dữ liệu nhạy cảm; **không dùng kỹ thuật nuốt exception như vậy trong server production**.

### 8.4. Vì sao không viết code trust-all để so sánh?

Để chứng minh hệ thống đúng, **không cần tạo một cài đặt không an toàn** rồi cho thấy nó chấp nhận CA giả. Thay vào đó, negative tests yêu cầu Java **phải từ chối** root khác và hostname sai; test sẽ báo thất bại nếu ai đó vô tình thay trust manager hoặc tắt HTTPS endpoint identification.

Đây là kiểu kiểm thử mạnh hơn một “demo chạy được với mọi server”, vì nó kiểm **hành vi từ chối quan trọng đối với an ninh**.

## 9. Với `HttpClient` hoặc `HttpsURLConnection` nên làm gì?

### 9.1. `java.net.http.HttpClient`

Đối với JDK 11+ (bao gồm JDK 17), khi cần dùng CA nội bộ, bạn có thể tạo `SSLContext` chứa trust manager chuẩn như phần 6 rồi đưa vào **`HttpClient.Builder`**:

~~~java
HttpClient client = HttpClient.newBuilder()
        .sslContext(clientContext)
        .build();
HttpRequest request = HttpRequest.newBuilder(
        URI.create("https://api.example.test/status"))
        .GET()
        .build();
HttpResponse<String> response =
        client.send(request, HttpResponse.BodyHandlers.ofString());
~~~

**Đây là mảnh ghép API**, không phải chương trình độc lập: `clientContext` phải được khởi tạo an toàn, các import/exception/URL thật phải được xác định. Mặc định HTTPS endpoint identification phải được giữ nguyên, không bật các switch vô hiệu hóa hostname verification.

**Sửa đúng:** nếu API ở `https://api.example.test` dùng CA nội bộ, đặt CA đã được kiểm trong truststore; certificate leaf phải có SAN cho `api.example.test`. Nếu gọi bằng `https://10.0.0.20`, hãy có IP SAN phù hợp hoặc sửa URL/tên logic theo chính sách. Không dùng request “insecure mode” vì đang ở staging.

### 9.2. `HttpsURLConnection`

`HttpsURLConnection` dùng hostname verification mặc định cho HTTPS. Có thể cung cấp một `SSLSocketFactory` được tạo từ `SSLContext` tin đúng CA:

~~~java
HttpsURLConnection connection = (HttpsURLConnection)
        new URL("https://api.example.test/status").openConnection();
connection.setSSLSocketFactory(clientContext.getSocketFactory());
// Giữ HostnameVerifier mặc định, KHÔNG override thành always-true.
int status = connection.getResponseCode();
~~~

Đây là **trích đoạn**, cần có import, `clientContext` an toàn, kiểm IO và lifecycle connection trong chương trình thực. Cách làm đúng là **thay nguồn trust anchors có kiểm soát**, không bỏ qua hostname verification.

### 9.3. OkHttp, Apache HttpClient, Netty, Spring và thư viện khác

Các thư viện có tên flag/API khác nhau theo version. Quy tắc bất biến:

- Trust manager thực sự kiểm chain với nguồn CA được chọn, không có `acceptAll`/trust-all.
- Hostname verifier mặc định hoặc policy xác thực tương đương vẫn hoạt động.
- Không tự tạo SSLContext “một lần cho toàn hệ thống” rồi thay tất cả clients bằng trust manager không an toàn.
- Bất kỳ option `NoopHostnameVerifier`, `TrustAllStrategy`, `InsecureTrustManagerFactory` hoặc khái niệm tương tự đều phải bị **báo động trong code review**, đặc biệt với ứng dụng có dữ liệu thật.
- Test trên đúng **version của client library và JDK** sẽ deploy; cấu hình chắc chắn an toàn trong một API không bảo đảm được copy y nguyên sang API khác.

## 10. Gỡ lỗi TLS mà không hạ cấp bảo mật

Một exception có thể phát sinh từ nhiều tầng. Hãy điều tra đúng nguyên nhân, không áp “fix chung” là bỏ trust/hostname.

| Triệu chứng | Nhóm nguyên nhân | Hướng khắc phục |
| --- | --- | --- |
| `PKIX path building failed` | Client không dựng được path tới trust anchor | Xác minh CA/truststore, intermediate chain, đúng runtime đang dùng |
| `unable to find valid certification path` | Không có trust anchor/path được policy chấp nhận | Chọn đúng root đã thẩm định, không import certificate tùy tiện |
| `No subject alternative DNS name matching ...` | Leaf SAN không chứa DNS host cần kiểm | CA cấp lại SAN đúng, hoặc dùng endpoint URL đúng identity |
| `No subject alternative names matching IP address ...` | URL dùng IP nhưng leaf không có IP SAN | Dùng IP SAN đúng loại hoặc URL DNS được chứng thực |
| `certificate expired` / `not yet valid` | Thời gian leaf/issuer hoặc đồng hồ sai | Renew certificate, kiểm thời gian, không disable validity |
| `Extended key usage does not permit use for TLS server authentication` | Leaf không có mục đích `serverAuth` phù hợp | Xin cấp certificate đúng EKU |
| `Algorithm constraints check failed` | Key/signature không đạt policy | Nâng key/CA profile/cipher suite phù hợp |
| `Certificate revoked` | Certificate bị thu hồi theo policy | Kiểm sự cố key/CA, rotate và xử lý đúng, không bỏ revocation |
| Lỗi sau khi chuyển Docker/JDK | Store/type/CA bundle/provider khác | Kiểm chính xác truststore file, quyền, JDK distribution và cấu hình tiến trình |
| “Works after allow-all-hosts” | Name check đã bị vô hiệu hóa | **Rollback bypass** và cấp certificate SAN đúng |
| “Works after trust-all manager” | Chain trust đã bị vô hiệu hóa | **Rollback bypass** và cấu hình CA đáng tin |
| Lỗi ngẫu nhiên sau redirect/proxy | Endpoint host thực tế khác hoặc proxy TLS | Kiểm URL từng hop, trust anchors và certificate SAN tại điểm TLS termination |

### 10.1. Quy trình điều tra có cấu trúc

~~~text
1. Xác định URL/hostname hoặc IP thật sự do client chủ định gọi.
2. Xác định HTTPS client API, SSLContext và truststore đang dùng.
3. Kiểm leaf SAN, issuer, thời hạn, KU/EKU và chain/intermediates.
4. Kiểm CA root từ nguồn tin cậy; đối chiếu truststore của runtime.
5. Test chain trust độc lập; test hostname matching độc lập.
6. Sửa certificate/URL/truststore đúng nguyên nhân.
7. Rerun positive + negative tests.
8. Không retry bằng trust-all hoặc skip-hostname.
~~~

Dùng `keytool -list -v` hoặc công cụ đã được tổ chức cho phép để xem cấu trúc truststore. Khi dùng `openssl s_client`, nhớ rằng **chỉ in certificate/`Verify return code` chưa chắc đã chạy hostname verification**; phải cung cấp options/trust phù hợp nếu muốn kiểm identity. Không lấy một kết quả path-valid rồi suy ra toàn bộ HTTPS client đã an toàn.

### 10.2. JSSE debug logs có thể chứa dữ liệu nhạy cảm

JDK có một số chế độ debug TLS, ví dụ:

~~~bash
java -Djavax.net.debug=ssl,handshake,trustmanager YourApplication
~~~

Đây là **lệnh khái niệm**, không phải lệnh chạy sample `SafeJsseDemo` vì class name chỉ đại diện ứng dụng cụ thể. Dùng trong môi trường kiểm soát, xem lại nội dung log trước khi chia sẻ: trace TLS có thể lộ metadata hệ thống, hostname, certificate details và thông tin không phù hợp đưa ra công khai. **Không bật verbose debug vô hạn trong production**, không đưa password, private keys, token hoặc toàn bộ body xác thực vào logs.

**Không sửa debug bằng cách tắt kiểm chứng để “tiếp tục quan sát request”**. Khi cần tái hiện service lỗi, tạo CA/test certificate đúng yêu cầu, hoặc kiểm endpoints trong môi trường lab cô lập như phần 7–8.

## 11. Không trust CA lạ chỉ vì import thành công

Một sai lầm gần trust-all là **import mọi CA/certificate mà server gửi** vào truststore của client. Dù không cài trust-all manager, cách này biến tập trust anchors thành một danh sách không có quy trình xác minh.

**Ví dụ rủi ro:**

~~~text
Client báo: issuer chưa được tin
Operator tải root.crt từ chính đường kết nối chưa xác thực
Operator import root.crt với -noprompt
Client kết nối thành công
=> Không có bằng chứng root này thuộc CA dự kiến.
~~~

Attacker có thể cung cấp một root giả và leaf do chính họ ký. **Chữ ký có thể kiểm đúng đối với root giả mới import**, nhưng quyết định tin root ban đầu đã sai.

Cách làm đúng:

1. Xác minh fingerprint/public key CA qua kênh quản trị độc lập.
2. Kiểm chủ sở hữu CA, phạm vi cấp certificate, constraints, expiration và chính sách quản lý CA.
3. Import **đúng certificate được phê duyệt** vào truststore của môi trường cụ thể.
4. Kiểm permission file, backup, review và rotation; không dùng global truststore nếu chỉ một client cần CA đó.
5. Giữ hostname verification và kiểm end-to-end.
6. Audit các thay đổi trust anchors, không tự động cập nhật truststore theo certificate peer gửi trên mạng.

**Certificate pinning đúng** (nếu cần) cũng dựa trên giá trị được phân phối **từ nguồn tin cậy**, không phải fingerprint lấy tự động từ một kết nối chưa được xác thực và được coi là an toàn vĩnh viễn.

## 12. Mô hình an toàn cho dev/staging/production

### 12.1. Dev localhost

Nếu test endpoint `https://localhost`, tạo certificate dev có SAN `DNS:localhost`, tin CA dev **chỉ trong dev truststore**. Nếu test bằng `https://127.0.0.1`, phải cấp SAN loại `IP:127.0.0.1` hoặc dùng DNS `localhost` cho đúng.

Không yêu cầu developer tắt toàn bộ HTTPS validation vì một server local chỉ có self-signed certificate. CA dev có thể tự ký, nhưng phải được **chủ động tin với phạm vi giới hạn**; khác với trust mọi certificate trên mạng.

### 12.2. Staging và môi trường kiểm thử tích hợp

Staging nên có hostname ổn định như `staging-api.example.test`, certificate SAN đúng, CA/truststore staging riêng hoặc public CA tùy thiết kế. Negative tests trong CI phải xác nhận:

- Root khác không được chấp nhận.
- Certificate SAN không khớp không được chấp nhận.
- Certificate hết hạn/không đạt policy bị từ chối.
- Sau redirect/proxy, endpoint mới vẫn được xác minh.
- Không có configuration flag bypass verification tồn tại trong artifact sẽ deploy.

### 12.3. Production và load balancer

Với ingress/LB terminate TLS, certificate tại điểm TLS termination phải phù hợp hostname URL mà client thấy. Nếu LB mở TLS riêng đến backend, kết nối LB→backend **cũng có identity/trust policy riêng**; không “kế thừa” xác thực từ client→LB nếu chưa thiết kế.

Trong microservices, service A gọi service B qua DNS nội bộ có thể cần CA công ty, identity SAN phù hợp, truststore scoped và mTLS. **mTLS không thay hostname verification của client**, và certificate client được CA công ty ký **không tự cấp quyền** gọi mọi endpoint: authorization identity-to-role phải được kiểm riêng.

### 12.4. Proxy doanh nghiệp và TLS inspection

Một proxy có thể trình certificate do **CA proxy** cấp thay certificate từ server gốc. Nếu tổ chức có chính sách TLS inspection hợp pháp, client cần quản lý CA đó theo quy trình riêng và vẫn kiểm hostname của URL đích, scope, logging và rủi ro vận hành.

Không tự import CA proxy được gửi bởi một kết nối không tin cậy. Việc thêm CA proxy mở rộng trust anchors và có thể cho phép proxy terminate nhiều kết nối; đây là **quyết định quản trị bảo mật**, không phải workaround ở dòng code.

## 13. Phòng lỗi trong code review và kiểm thử

### 13.1. Những dấu hiệu cần tìm trong pull request

| Dấu hiệu | Câu hỏi kiểm tra |
| --- | --- |
| Custom `X509TrustManager` | Có thực sự kiểm chuỗi, thuật toán và trust anchor? Vì sao không dùng provider chuẩn? |
| `checkServerTrusted` không có logic xác minh | Đây có phải trust-all đang được ẩn bằng try/catch? |
| `HostnameVerifier` luôn trả true | Có phải đang bỏ mọi endpoint identity checks? |
| `NoopHostnameVerifier` hoặc `allowAllHosts` | Có test negative mismatch để phát hiện bypass không? |
| `SSLContext.setDefault` | Có vô tình thay TLS policy toàn process không? |
| Raw `SSLSocket`/`SSLEngine` | Đã bật endpoint identification và truyền peer name đúng chưa? |
| `SSLParameters.setEndpointIdentificationAlgorithm("")` hoặc bỏ algorithm | Có làm mất name check trong context HTTPS không? |
| Cấu hình `disableHostnameVerification` | Có thể lọt vào production build/runtime không? |
| Truststore import certificate tự động khi lỗi | CA từ đâu và ai xác minh fingerprint? |
| Retry sau `SSLHandshakeException` | Có nới lỏng trust/hostname ở lần thử sau không? |
| Test HTTPS chỉ assert HTTP 200 | Có negative tests với wrong CA và wrong hostname không? |
| Wrapper SSL factory được dùng ở nhiều clients | Có áp phạm vi trust rộng hơn dự kiến không? |

**Một review tốt không chỉ grep từ khóa.** Có thể thư viện dùng tên khác hoặc configuration trong YAML/properties thay vì Java code. Cần xem **effective TLS settings**, dependency versions, container image và smoke tests.

### 13.2. Test matrix bắt buộc

| Test | Chain trust | Hostname | Kết quả mong muốn |
| --- | --- | --- | --- |
| Positive | CA đúng | SAN khớp | **Accept** |
| Wrong root | CA khác | SAN khớp | **Reject** |
| Wrong DNS | CA đúng | SAN không khớp | **Reject** |
| Wrong IP | CA đúng | Thiếu iPAddress SAN | **Reject** |
| Invalid date | CA đúng | SAN khớp | **Reject** |
| Missing intermediate | Path không dựng được | SAN khớp | **Reject** nếu không dựng được chain hợp lệ |
| Wrong EKU | Chain/usage policy không đạt | SAN khớp | **Reject** |
| Tampered certificate | Chữ ký không hợp lệ | SAN khớp | **Reject** |
| Revoked certificate | Revocation policy từ chối | SAN khớp | **Reject** |
| Wrong client identity (mTLS) | Client cert có thể hợp lệ | Không được authorization | **Deny** ở tầng tương ứng |

Hai negative tests **Wrong root** và **Wrong DNS** có giá trị nhất cho chủ đề này vì chúng trực tiếp phát hiện việc một developer đã vô tình bật trust-all hoặc bỏ name check.

### 13.3. Không làm test “đúng” bằng cách chấp nhận lỗi

Việc test máy phát triển không có CA đúng là **test setup chưa hoàn chỉnh**, không phải lý do sửa verifier:

- Không tắt certificate verification để có HTTP status.
- Không bắt `SSLHandshakeException` rồi giả lập thành công.
- Không dùng global insecure client trong test fixtures nếu cùng fixture được tái sử dụng trong production.
- Không tự import certificate root do server trả về mà không kiểm nguồn.
- Khi test cần self-signed CA, hãy **tạo và pin trust anchor trong test fixture riêng**, cấp leaf có SAN hợp lệ và giữ verification.

**Đúng là phải có ít nhất một test thất bại theo dự kiến.** Nếu kết nối với CA không tin cậy hoặc hostname sai vẫn thành công, hệ thống **có lỗ hổng xác thực**, không phải “test linh hoạt”.

## 14. Những lỗi dễ nhầm giữa certificate trust và hostname

### 14.1. `PKIX path building failed`

Đây thường là lỗi xác minh đường dẫn, không phải lỗi hostname. Hướng xử lý: kiểm certificate chain server gửi, intermediate, root mà client thực sự trust, policy JDK, thời hạn và thuật toán. Import một CA đúng có thể sửa trust issue; **không nên động đến hostname verifier**.

### 14.2. `No subject alternative DNS name matching ...`

Trust chain có thể đã hợp lệ nhưng SAN không chứa reference hostname. Cách sửa: certificate phải có đúng DNS SAN (CA cấp lại), hoặc ứng dụng dùng hostname thực sự được cấp theo thiết kế. **Không đổi trust manager để xử lý mismatch** và không tắt hostname verification.

### 14.3. Dùng IP thay vì DNS khi test

Server certificate được cấp cho `DNS:api.example.test` nhưng developer test bằng `https://10.10.0.20`. Đây là reference identity khác. Dùng endpoint DNS phù hợp và route DNS đến IP, hoặc xin cấp IP SAN nếu policy cho phép định danh qua IP.

### 14.4. Root import sai Java runtime

Máy dùng nhiều JDK/containers. Bạn import CA vào `cacerts` của JDK A nhưng ứng dụng chạy JDK B, hoặc ứng dụng có custom truststore riêng. Kiểm **đường dẫn thực tế, type, user permissions và effective config**. Không tạo trust-all vì không tìm đúng truststore.

### 14.5. Một server trả nhiều certificate theo SNI

Nếu SNI không phù hợp hoặc reverse proxy chọn default certificate, chain có thể hợp lệ nhưng SAN thuộc hostname khác. Sửa SNI routing/server certificate configuration, đảm bảo client gửi đúng hostname, **vẫn cần name check**. Đừng coi vì CA ký nên có thể bỏ SAN.

### 14.6. Chỉ một thư viện HTTP thất bại

`HttpClient`, `HttpsURLConnection`, OkHttp, Netty hoặc Apache HttpClient có thể dùng SSLContext/truststore/hostname policies khác nhau. Điều tra **client cụ thể và version cụ thể**, không áp flag JVM global “insecure SSL” hoặc tắt kiểm tên ở mọi client.

### 14.7. Certificate hết hạn hoặc thu hồi

Chứng thư có SAN đúng và CA được tin có thể hết hạn hoặc bị thu hồi. Không dùng trust-all hoặc set clock giả để bypass. Renew/rotate key/cert, xử lý incident với CA, kiểm đồng bộ thời gian và revocation policy.

## 15. Mật khẩu/khóa, logging và quản trị an toàn

Mật khẩu PKCS12 bảo vệ file theo provider, **không thay CA validation và hostname verification**. Certificate leaf/root có thể công khai, nhưng private keys, trust policy và các bản sao credential cần được bảo vệ.

- **Không commit** file `.p12` chứa private key, key pass, secret manager token, `javax.net.ssl.keyStorePassword` hay truststore passwords vào repo.
- Quản lý keystore/truststore bằng secret manager, deployment policy, volume permission hoặc HSM/KMS theo yêu cầu.
- Root/CA certificates từ nguồn chính thức cần được xem như **danh sách security policy**, có review/audit/rotation; đừng import không kiểm soát.
- Hạn chế thay đổi `SSLContext` mặc định toàn JVM; ưu tiên cấu hình per-client.
- Trong lỗi TLS, log mức phù hợp: hostname đích, loại lỗi, certificate issuer/SAN/expiry đã được lọc theo chính sách; **không log private key, password, session key, bearer token** hoặc full request chứa credential.
- Đừng vô hiệu hóa revocation theo thói quen: cơ chế CRL/OCSP và chính sách hard/soft-fail phụ thuộc ứng dụng; phải được quyết định rõ.
- Với server/client mTLS, theo dõi certificate expiry, rotation, cert usage và mapping identity-to-permissions; cả hai chiều vẫn có chính sách trust riêng.

Đoạn code demo xóa `char[] password` do ứng dụng nắm giữ sau khi sử dụng. Điều này giảm thời gian tồn tại của bản sao do ứng dụng kiểm soát nhưng không chứng minh JVM/provider đã xóa mọi bản copy; password **literal** trong ví dụ là cố ý đơn giản hóa, **không dùng trong sản phẩm**.

## 16. Bài tập tự kiểm tra (có đáp án)

**Câu 1.** Ứng dụng HTTPS kết nối thành công sau khi thay trust manager bằng implementation chấp nhận mọi certificate. Điều gì đã mất?

**Đáp án:** Mất **xác thực đường dẫn/chủ thể phát hành certificate**. Attacker có thể dùng self-signed certificate hoặc certificate do họ kiểm soát để làm peer, trong khi phiên vẫn có encryption.

**Câu 2.** Java client đã trust CA công ty nhưng gọi `payments.internal` và peer trình certificate `DNS:reports.internal`. Có được chấp nhận không?

**Đáp án:** **Không.** Chain hợp lệ không thay hostname match. Client cần cert đúng SAN `payments.internal` hoặc sửa endpoint hợp lệ, không tắt name check.

**Câu 3.** Một certificate có SAN `DNS:localhost` và client dùng URL `https://127.0.0.1`. Thiếu điều gì?

**Đáp án:** Thiếu SAN loại **`iPAddress` cho `127.0.0.1`**. Có DNS SAN localhost không làm URL dùng IP trở thành cùng reference identity.

**Câu 4.** Có nên cài `HostnameVerifier` luôn trả true vì TLS vẫn kiểm CA không?

**Đáp án:** Không. Một certificate hợp lệ của hostname khác có thể bị chấp nhận cho endpoint mục tiêu; đó là lỗi endpoint authentication riêng với path trust.

**Câu 5.** Raw `SSLSocket` dùng `TrustManagerFactory` chuẩn. Điều này tự bảo đảm HTTPS hostname đã được kiểm?

**Đáp án:** **Không chắc.** Raw TLS không biết mặc định giao thức ứng dụng; cần peer name đúng và cấu hình `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` trước handshake khi áp dụng HTTPS identity.

**Câu 6.** Vì sao test chỉ khẳng định HTTP 200 từ HTTPS server chưa đủ cho security QA?

**Đáp án:** Một client trust-all hoặc bỏ hostname vẫn có thể nhận HTTP 200. Phải có **negative tests** với CA không được tin và hostname không khớp, xác minh client từ chối.

**Câu 7.** Có thể import root certificate lấy từ cùng kết nối đang bị lỗi TLS để “sửa PKIX” không?

**Đáp án:** Không tự ý. Root có thể là của attacker; trust anchor phải được chọn và xác minh fingerprint/nguồn thông qua kênh đáng tin, rồi mới import.

**Câu 8.** SNI đã là `api.example.test` nhưng leaf server trả về `DNS:other.example.test`. Có đủ an toàn không?

**Đáp án:** Không. SNI chỉ là thông tin giúp server chọn chứng thư; client vẫn phải kiểm leaf SAN khớp reference hostname.

**Câu 9.** Test CA khác thất bại với `SSLHandshakeException`. Đó là bug của chương trình `SafeJsseDemo`?

**Đáp án:** Không. Đây là **expected failure** và được biểu diễn bằng `Wrong CA rejected = true`. Từ chối trust anchor sai là hành vi đúng.

**Câu 10.** Trong demo, CA đúng + `localhost` pass, CA đúng + `127.0.0.1` fail. Vì sao?

**Đáp án:** Do **danh tính endpoint khác nhau**. Leaf chỉ có SAN DNS localhost. Phép kiểm CA vẫn pass ở cả hai, nhưng IP literal không có SAN iPAddress tương ứng nên handshake bị từ chối.

**Câu 11.** Nếu `PKIX path building failed` sau khi chuyển sang container, có nên bật skip-verify?

**Đáp án:** Không. Cần xác minh truststore thực sự được JDK/container dùng, CA/intermediate, file permissions và algorithm policy; fix CA chain/config đúng nguyên nhân.

**Câu 12.** Một client cần CA công khai và một CA nội bộ. Dùng custom truststore có tự động giữ các root mặc định của JDK không?

**Đáp án:** Không mặc định. `TrustManagerFactory.init(customStore)` dùng nội dung store được cấp theo provider/policy; cần thiết kế rõ cách quản lý tập CA cần tin. Không fallback thành trust-all.

**Câu 13.** mTLS server tin CA của client, vậy mọi client certificate do CA đó ký được quyền gọi tất cả API?

**Đáp án:** Không. Trust certificate chain xác thực nguồn identity theo policy; ứng dụng vẫn phải **authorization** identity-to-role/tenant, kiểm mục đích certificate như `clientAuth` khi phù hợp.

**Câu 14.** Có thể sửa lỗi CA và hostname bằng cách giảm TLS version/cipher suite để bắt tay thành công không?

**Đáp án:** Không. Version/cipher suite là tầng khác; giảm chất lượng thuật toán không thay chain trust hoặc SAN. Sửa certificate/truststore/endpoint đúng chính sách.

**Câu 15.** Nếu team muốn đưa ví dụ TLS vào tài liệu public, nên minh họa như thế nào để người học không copy giải pháp nguy hiểm?

**Đáp án:** Dùng **CA test được tin có chủ đích, hostname SAN đúng, trust manager chuẩn, endpoint identification bật**, và thêm negative tests. Mô tả trust-all/skip-hostname như anti-pattern, không cung cấp helper insecure dễ copy sang production.

## 17. Checklist trước khi merge/deploy mã JSSE

- [ ] **Không** có trust manager bỏ qua kiểm chứng, trust-all SSLContext, certificate verifier “always accept”.
- [ ] **Không** có `HostnameVerifier` luôn trả true, `NoopHostnameVerifier` hoặc tắt endpoint identification khi dùng HTTPS.
- [ ] Không có retry insecure sau `SSLHandshakeException`; lỗi TLS phải fail closed.
- [ ] Trusted CA certificates được chọn từ **nguồn đã xác minh**, phạm vi truststore đúng môi trường/client.
- [ ] Server certificate có SAN **DNS/IP đúng type** và hostname thực tế từ URL/config; không dùng Subject CN/alias thay SAN.
- [ ] Client cấp cao giữ hostname verification mặc định; raw `SSLSocket`/`SSLEngine` bật `"HTTPS"` endpoint identification khi giao thức yêu cầu.
- [ ] Không thay `SSLContext.setDefault` toàn JVM bằng trust config bất cẩn.
- [ ] Chain đầy đủ/intermediate được xử lý, certificate validity, KU/EKU, algorithm constraints và revocation policy được kiểm đúng.
- [ ] Có test **đúng CA + đúng hostname ⇒ accept**.
- [ ] Có test **sai CA + đúng hostname ⇒ reject**.
- [ ] Có test **đúng CA + sai hostname/IP ⇒ reject**.
- [ ] Dev/staging/production có CA, truststore và SAN/endpoint riêng theo chính sách; không dùng root dev rộng ở production.
- [ ] HTTPS qua proxy/LB, redirect và mTLS vẫn xác minh đúng logical peer identity.
- [ ] Không hard-code password/keystore production, không log private key/session keys/API tokens; có audit và certificate rotation.
- [ ] CI hoặc code review phát hiện cấu hình insecure từ cả Java source, YAML/properties, system properties và thư viện HTTP đang dùng.

## 18. Kết luận

**Đừng biến lỗi TLS thành lý do bỏ xác thực.** Một HTTPS/JSSE client phải chứng minh **hai điều**: chain chứng thư hợp lệ với trust anchors **được chủ động tin**, và certificate leaf mô tả **đúng hostname/IP cần kết nối**. Trust-all bỏ lớp xác thực CA; skip-hostname bỏ lớp ràng buộc danh tính endpoint. Cả hai đều có thể để kẻ đứng giữa mạo danh peer dù lưu lượng vẫn được mã hóa.

Ví dụ Java 17+ trong bài không dùng các shortcuts này: nó thiết lập trust manager chuẩn, bật `HTTPS` endpoint identification và **bắt buộc từ chối sai CA/sai hostname**, bên cạnh trường hợp đúng đi qua. Khi gặp lỗi thực tế, sửa nguồn certificate, CA, SAN, URL hoặc client configuration **đúng nguyên nhân**, không giảm mức xác minh để có một lần kết nối “thành công”.

**Tài liệu tham khảo:** [Java 17 JSSE Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html), [RFC 5280 — PKIX Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280), [RFC 9525 — Service Identity in TLS](https://www.rfc-editor.org/rfc/rfc9525), [Java 17 keytool documentation](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jsse-011">16.11 Chẩn đoán SSLHandshakeException, lỗi chứng thư và lỗi không có cấu hình chung</a>

<details>
<summary>Xem chi tiết</summary>

# 16.11. Chẩn đoán SSLHandshakeException, lỗi chứng thư và lỗi không có cấu hình chung

**`SSLHandshakeException` không phải một lỗi duy nhất.** Đây là một lớp exception Java thường gặp khi hai phía không thể hoàn tất SSL/TLS handshake. Nguyên nhân có thể là **không tin certificate**, **certificate hết hạn hoặc sai hostname**, **không có phiên bản TLS dùng chung**, **không có cipher suite được phép ở cả hai phía**, **server không có certificate/key phù hợp**, **mTLS client thiếu chứng thư**, hoặc **bất đồng signature schemes, named groups, SNI/ALPN và chính sách bảo mật**.

Đọc mỗi dòng `javax.net.ssl.SSLHandshakeException: handshake_failure` rồi kết luận “server bị lỗi SSL” là thiếu căn cứ. Ngược lại, thấy `PKIX path building failed` rồi “sửa” bằng một `TrustManager` chấp nhận mọi chứng thư là một lỗi an ninh nghiêm trọng: ứng dụng có thể kết nối được, nhưng mất khả năng xác thực đúng đối tác và dễ bị người đứng giữa (MITM).

**Quy tắc của bài:** luôn chẩn đoán theo thứ tự **đúng endpoint → đúng giai đoạn handshake → đúng chuỗi nguyên nhân (cause chain) → đúng cấu hình hai bên → đúng trust/identity → mới quyết định cách sửa**. Chẩn đoán không đồng nghĩa với việc nới lỏng security policy. Tên exception/alert có thể khác tùy Java version, TLS version, provider, proxy và phía phát hiện lỗi; dùng nhiều dấu hiệu kết hợp, không đoán chỉ từ một message.

## 1. Mục tiêu học tập

Sau khi hoàn thành bài này, bạn có thể:

- Phân biệt lỗi **network/DNS/TCP**, lỗi **TLS handshake**, lỗi **certificate path/hostname** và lỗi **HTTP/application sau handshake**.
- Hiểu vị trí của `SSLHandshakeException` trong JSSE và cách lần theo `Throwable.getCause()`, `CertificateException`, `CertPathValidatorException`, `CertificateExpiredException` và các loại lỗi liên quan.
- Nhận diện lỗi “no common configuration” ở **TLS protocol versions, cipher suites, signature algorithms, supported groups/key shares, certificate/key types, ALPN, SNI và client authentication**.
- Bật tracing JSSE vừa đủ bằng `-Djavax.net.debug=ssl,handshake,trustmanager,keymanager` và cert-path tracing bằng `-Djava.security.debug=certpath`; hiểu giới hạn và nguy cơ dữ liệu nhạy cảm trong log.
- Dùng `keytool` và Java API đọc **cấu hình keystore/truststore, certificate chains, SAN, validity, KeyUsage, EKU** mà không biến certificate không tin cậy thành trusted root.
- Dùng `SSLContext`, `SSLEngine`, `SSLParameters` để kiểm tra capability và cấu hình trước handshake, nhưng không nhầm **supported** với **enabled** và **được negotiated**.
- Chạy chương trình Java 17+ có output xác minh được, phân loại một nguyên nhân certificate lỗi trong ví dụ tổng hợp và nhận diện hai cấu hình không có phần giao.
- Lập runbook bảo mật cho client/server/mTLS, reverse proxy, CA nội bộ, rotation và môi trường dev/staging/production.

## 2. Đọc đúng từ phía nào, ở tầng nào?

Trước khi chỉnh sửa TLS, hãy xác định **người phát hiện lỗi** và **kênh truyền cụ thể**.

```text
Java client            Reverse proxy / API gateway             Java backend
-----------            ---------------------------             ------------
DNS / TCP connect -->
                      TLS connection #1 may terminate here
                      HTTPS request -->
                               TCP/TLS connection #2 -------->
                                                  JSSE handshake may occur here
                                                  Application logic / HTTP
```

Một lỗi từ Java client tới gateway có thể xảy ra trước khi backend nhận request. Một lỗi mTLS giữa gateway và service khác có thể không xuất hiện trong Java client log. Nếu HTTP đã trả `401`/`403`/`500` qua một TLS connection hợp lệ thì thường **không phải** chứng cứ TLS handshake giữa hai phía đó thất bại.

Bảng định vị nhanh:

| Dấu hiệu | Tầng thường liên quan | Câu hỏi tiếp theo |
| --- | --- | --- |
| `UnknownHostException` | DNS/name resolution | Tên host có đúng? DNS/proxy/container khác môi trường? |
| `ConnectException: Connection refused` | TCP connect | Port/server listener/firewall có chạy? |
| `SocketTimeoutException` lúc connect/read | Network hoặc handshake/read bị treo | Timeout ở giai đoạn nào? Có nhận ServerHello không? |
| `SSLHandshakeException` | TLS handshake/authentication | Cause chain? Alert đến từ peer hay do local JSSE tạo? |
| `SSLProtocolException` | TLS protocol/framing | Alert/version/record nào? Có non-TLS data trên port? |
| `SSLPeerUnverifiedException` | Xác thực peer ở ngữ cảnh TLS | Chứng thư/peer identity có hiện diện/được verify không? |
| `CertificateException` / `CertPathValidatorException` trong cause | Certificate trust/validity/policy | Thiếu CA, lỗi thời gian, sai chain hay revocation? |
| HTTP `401/403` | Application auth/authz | TLS đã pass nhưng token/role/tenant không hợp lệ? |
| HTTP `502/503` từ proxy | Proxy/upstream health | Proxy có gặp TLS failure khi kết nối backend không? |

Đừng mặc định mọi `SSLException` xảy ra ở bước bắt tay: một connection có thể đã bắt tay xong rồi mới gặp lỗi record/EOF/alert, session resumption hoặc I/O. Ngay cả handshake cũng có thể khởi tạo khi lần đầu đọc/ghi, tùy API; muốn biết chính xác giai đoạn phải quan sát JSSE log hoặc đặt instrumentation tại `SSLSocket.startHandshake()`/`SSLEngine`.

### 2.1. Exception và TLS alert có nguồn khác nhau

Ví dụ:

```text
javax.net.ssl.SSLHandshakeException: Received fatal alert: handshake_failure
```

thường nói Java **nhận một alert từ peer**, nhưng peer phát alert vì nguyên nhân gì cần xem **log phía peer** hoặc capture handshake để biết. Không đủ căn cứ kết luận lỗi certificate hay cipher suite từ message chung này.

Ngược lại:

```text
javax.net.ssl.SSLHandshakeException:
  PKIX path building failed: ...
Caused by: sun.security.provider.certpath.SunCertPathBuilderException:
  unable to find valid certification path to requested target
```

thường cho thấy phía Java **không xây được đường tin cậy** tới anchor phù hợp. Đây là ví dụ dạng thông báo, **không phải một contract chuẩn hóa**; lớp nội bộ `sun.security...` có thể thay đổi, không import hay phụ thuộc vào nó trong code.

**Luôn ghi nhận:** host/port thực sự, TLS termination point, Java/JDK version, provider, đường truststore/keystore theo cấu hình, expected hostname, thời điểm, full/resumed handshake và message **đầu tiên** chỉ nguyên nhân thay vì chỉ xem hàng trăm dòng cascade phía sau.

## 3. Các giai đoạn TLS handshake giúp khoanh vùng lỗi

Một certificate-based TLS 1.3 handshake thông thường:

```text
Client                                                  Server
------                                                  ------
ClientHello (supported_versions, suites, SNI,
             key_share/groups, sig_algs, ALPN) -------->
                                        <--------------- ServerHello
                                        <--------------- EncryptedExtensions
                                        <--------------- Certificate
                                        <--------------- CertificateVerify
                                        <--------------- Finished
Client validates certificate path, hostname and
handshake authentication; key schedule finishes
Finished ---------------------------------------------->
              <====== encrypted application data ======>
```

**Sơ đồ khái niệm:** handshake có thể khác khi dùng PSK resumption, mutual TLS, HelloRetryRequest hoặc policy khác. Không phải mọi TLS 1.3 connection đều có đầy đủ `Certificate`/`CertificateVerify`, và nhiều handshake messages sau `ServerHello` đã được mã hóa.

Từ đó suy luận:

- **Không thấy ServerHello:** kiểm tra ClientHello có ra khỏi client không, server/port có nói TLS không, gateway/firewall có chặn không, protocol/version/suite/key-share có giao được không. Có thể server abort sớm vì SNI hoặc cipher policy.
- **Đến Certificate rồi fail:** ưu tiên certificate chain, trust anchor, thời hạn, SAN/hostname, algorithm constraints, key usage, revocation và mTLS nếu liên quan.
- **CertificateVerify/Finished fail:** có thể liên quan chữ ký handshake, private key, transcript/parameters hoặc implementation/proxy; cần provider logs ở đúng phía.
- **TLS Finished xong rồi HTTP trả lỗi:** không sửa truststore để chữa lỗi token/route/authorization.
- **Client nhận fatal alert sau khi gửi client certificate:** kiểm tra phía server mTLS trust/certificate policy, không chỉ truststore của client.

### 3.1. Lỗi ở bên nhận hay bên gửi?

Nếu client ghi `Received fatal alert: certificate_unknown`, có thể **server** đã từ chối certificate client của mTLS; nếu server ghi tương tự, có thể **client** đã từ chối certificate server. Cần biết phía nào phát alert và handshake stage nào.

`handshake_failure`, `bad_certificate`, `certificate_unknown`, `unknown_ca`, `protocol_version`, `no_application_protocol` là các alert/concept hữu ích, nhưng **không có ánh xạ một-một tuyệt đối từ alert sang một root cause**. Một implementation có thể dùng alert chung cho nhiều lỗi cục bộ.

## 4. Cách đọc nguyên nhân `SSLHandshakeException` có hệ thống

Không dùng một `contains("PKIX")` đơn lẻ làm cơ sở sửa môi trường. Hãy tách:

1. **Top-level exception:** tên class Java và message giới hạn.
2. **Cause chain:** từ `getCause()` tới nguyên nhân sâu nhất, kể cả `CertificateException`/`CertPathValidatorException`.
3. **TLS debug sequence:** ClientHello, ServerHello, certificate, trust validation, alert/handshake failure.
4. **Peer-side log:** server có certificate/key nào, có yêu cầu client certificate không, có từ chối suite/group/ALPN không?
5. **Config provenance:** cờ JVM, biến môi trường, truststore/keystore thực tế, framework overrides, reverse proxy.
6. **Security policy:** liệu sửa có giữ nguyên peer identity và thuật toán được phép không?

Một cấu trúc để ghi nhận sự cố mà không ghi secret:

```text
incidentId: 6ef4f9c2          # mã ví dụ không nhạy cảm
direction: Java client -> api.example.com:443
TLS termination: gateway-1
JDK runtime: Java 17 / provider SunJSSE
expected service identity: api.example.com
TLS configured: versions [TLSv1.3, TLSv1.2]
stage: received Certificate, path validation failed
top exception: SSLHandshakeException
cause category: CERT_PATH_UNTRUSTED
observed result: fail closed
last known good: deployment/config revision X
```

Đừng đưa `Authorization`, cookies, passwords, private keys, session tickets, application payload hoặc full TLS secrets vào ticket/log để “đủ dữ liệu”. Chẩn đoán mạnh nhất thường dùng **metadata, policy, certificate public fields và cause classes**, không cần xuất secrets.

### 4.1. Phân biệt root cause với triệu chứng cascade

Một dòng debug `Fatal (CERTIFICATE_UNKNOWN)` sau khi JSSE đã ghi `PKIX path building failed` thường là **hậu quả của thất bại trust validation**. Đừng chỉ tìm dòng `Fatal` cuối cùng. Tương tự, `Broken pipe` ở server sau khi client đã reject certificate không phải chứng cứ server có cấu hình cipher sai.

Một flow hiệu quả:
- Ghi lại **fatal đầu tiên** và **exception gây ra nó**.
- Đối chiếu thời điểm/log của **cả hai bên** bằng correlation ID hoặc timestamp.
- Xác minh error có lặp lại cho nhiều endpoints hay chỉ một host/SNI.
- So sánh với phiên bản cấu hình đang hoạt động tốt trước đó, không sao chép file keystore/truststore tùy tiện.
- Chỉ thay một nhóm cấu hình tại một thời điểm, chạy lại negative tests.

## 5. Nhóm 1 — Lỗi certificate và đường tin cậy

### 5.1. `PKIX path building failed`

Dạng thường gặp:

```text
SSLHandshakeException: PKIX path building failed
  ...
  unable to find valid certification path to requested target
```

Nguyên nhân khả dĩ:
- Client **không tin** CA root hoặc intermediate cần thiết cho server certificate.
- Server không gửi đủ intermediate certificates, client không xây được path.
- Java runtime/container dùng truststore khác OS/browser; CA mà trình duyệt tin chưa chắc JDK trust.
- App chỉ đến file truststore sai hoặc truststore bị rỗng/không đọc được.
- TLS inspection proxy/corporate gateway phát certificate từ một CA nội bộ chưa được app tin theo chính sách.
- Chain có issuer mismatch, signature mismatch, policy constraints hoặc algorithm không được phép.
- JDK cũ/CA set chưa phù hợp hệ PKI mới, hoặc khác nhau giữa staging/production.

**Bước sửa đúng:** xác định **CA issuer/chain thực tế**, xác minh nguồn public CA/enterprise CA bằng kênh quản trị đáng tin, sửa chain phía server hoặc cập nhật truststore **có kiểm soát**. Không tự “import certificate vừa nhận từ server vào truststore” mà chưa xác minh; điều đó có thể biến certificate attacker thành trusted root.

### 5.2. `trustAnchors parameter must be non-empty`

Tình huống này thường cho thấy **tập trust anchors dùng cho PKIX rỗng** hoặc configuration tạo validator không có CA tin cậy. Cần kiểm tra:
- File truststore có tồn tại/được đọc không?
- Kiểu store: PKCS12/JKS/PKCS11 theo policy và provider?
- Password/cấu hình truststore có phù hợp không?
- File có chứa certificate entries đã được phê duyệt hay không?
- Code có khởi tạo `TrustManagerFactory` với một `KeyStore` mới rỗng rồi vô tình dùng cho mọi HTTPS connection?
- Container có mount chính xác truststore vào runtime không?

**Không** sửa bằng cách thêm root không rõ nguồn hoặc tạo `TrustManager` bỏ qua kiểm tra.

### 5.3. Certificate hết hạn hoặc chưa có hiệu lực

```text
java.security.cert.CertificateExpiredException
java.security.cert.CertificateNotYetValidException
```

Chứng thư hoặc thành phần chain có khoảng `notBefore/notAfter` không phù hợp thời điểm kiểm tra. Hãy đối chiếu:
- UTC/clock của JVM/host/container và NTP.
- Thời gian của leaf và intermediate.
- Dịch vụ có vẫn gửi certificate cũ sau rotation không?
- Load balancer nhiều node có cert khác nhau không?
- Certificate policy có yêu cầu revocation/short-lived credentials không?

**Không sửa bằng cách đặt lại system clock sai hoặc bỏ `checkValidity()` trong production.** Đồng bộ thời gian và renew/rollout certificate chính xác.

### 5.4. Sai SAN hoặc hostname

Ví dụ:

```text
No subject alternative DNS name matching api.example.com found
No name matching api.example.com found
No subject alternative names present
```

Dạng message tùy provider. Nếu app gọi `api.example.com` nhưng cert chỉ có `db.example.com` hoặc CN/SAN không hợp lệ, phải **từ chối** dù chain CA trusted.

Cách kiểm tra:
1. Xác định expected hostname từ URL/service discovery đã tin.
2. Đọc `SubjectAlternativeName` loại DNS/IP tương ứng.
3. Kiểm tra SNI mà client gửi có đúng, server virtual host có chọn cert đúng không.
4. Với IP literal, xem `iPAddress` SAN và policy matching; không giả định DNS SAN đủ.
5. Sửa certificate/SNI/routing theo thực tế; không tắt hostname verification.

Nếu app gọi qua IP nội bộ nhưng certificate chỉ có public DNS, giải pháp là **kết nối tới tên được chứng nhận** với DNS/routing phù hợp hoặc cấp certificate hợp lệ cho identity mong muốn, không tự đặt HostnameVerifier luôn true.

### 5.5. Certificate chain đúng chữ ký nhưng sai chính sách

Các trường hợp:
- `BasicConstraints: CA=false` ở certificate dùng làm intermediate CA.
- CA thiếu `keyCertSign` khi policy/KeyUsage yêu cầu.
- Leaf certificate sai `ExtendedKeyUsage`, ví dụ chỉ được dùng `clientAuth` nhưng đem làm `serverAuth`.
- Certificate có critical extension không được hiểu/đáp ứng.
- `NameConstraints` giới hạn tên không phù hợp.
- Thuật toán ký/khóa quá yếu theo `jdk.certpath.disabledAlgorithms` và algorithm constraints của runtime.
- Không vượt qua policy revocation (CRL/OCSP) khi được yêu cầu.

Gọi `cert.verify(issuerPublicKey)` chỉ kiểm tra **chữ ký trên certificate**; nó không phải một hệ thống PKIX trust validation hoàn chỉnh. Đừng dùng kết quả verify đơn lẻ để đánh dấu trusted.

### 5.6. Revocation/OCSP/CRL

Chứng thư có thể **còn hạn** và chữ ký đúng nhưng **đã bị thu hồi**. Kiểm tra policy của PKIX/trust manager/OCSP, khả năng truy cập responder, thời hạn CRL, stapling, timeout và fail-closed/soft-fail theo quy định của hệ thống. Không mặc định “gặp lỗi OCSP thì tắt revocation là xong”. Một số deployment dùng chứng thư sống ngắn và cơ chế rotation thay thế hoặc bổ sung kiểm tra revocation; mọi lựa chọn cần threat model.

## 6. Truststore và keystore: hai vai trò khác nhau

| Kho/cấu hình | Giữ gì? | Dùng để làm gì? | Nếu cấu hình sai |
| --- | --- | --- | --- |
| **Truststore** | CA/anchors hoặc chứng thư được tin theo policy | **Xác minh certificate của bên kia** | `PKIX path building failed`, unknown CA, trust anchors rỗng |
| **Keystore / key entry** | Private key của chính bên này + certificate chain tương ứng | **Chứng minh danh tính của mình** khi handshake yêu cầu | No available authentication scheme, missing client cert, server cannot present cert |
| **JDK security policy** | Protocol/algorithm constraints | Loại bỏ thuật toán hoặc khóa yếu | No common config dù có suite trên danh sách supported |
| **SSLContext/JSSE config** | KeyManagers, TrustManagers, SecureRandom, protocol state | Tạo TLS engine/sockets | Dùng nhầm store, override mặc định, không có key |
| **SNI/endpoint config** | Service identity cần kết nối | Chọn virtual host/certificate, xác minh hostname | Wrong cert, hostname failure |

### 6.1. Client kết nối HTTPS thường chỉ cần truststore

Nếu Java là **HTTPS client**, nó cần tin certificate server; thông thường không cần certificate của chính client trừ khi server yêu cầu **mutual TLS (mTLS)**.

- Client truststore: CA kiểm tra certificate server.
- Client keystore: private key/cert của client **nếu** mTLS.
- Server keystore: private key/cert của server.
- Server truststore: CA kiểm tra client certificate **nếu** mTLS.

Câu “Tôi đã import CA vào keystore mà client vẫn báo PKIX” có thể do **import vào kho sai vai trò** hoặc app không sử dụng chính file đó.

### 6.2. Default truststore và custom truststore

Trong SunJSSE/JDK, trust material mặc định thường đến từ JSSE truststore configuration, ví dụ `javax.net.ssl.trustStore` nếu được khai báo, hoặc các location mặc định của runtime như `jssecacerts`/`cacerts` tùy provider và cấu hình. **Đừng giả định JVM chắc chắn đọc OS certificate store** hoặc file của một JDK khác.

Một file `-Djavax.net.ssl.trustStore=/app/config/truststore.p12` sai đường dẫn có thể dẫn tới trust không như kỳ vọng; cần xác minh trực tiếp file tồn tại, nội dung, quyền đọc và runtime nào đang chạy.

Nếu dùng custom `SSLContext`:
- `TrustManagerFactory.init(customTrustStore)` thay đổi nguồn anchors của context đó; nó không tự “merge” mọi CA của hệ điều hành/JDK.
- `KeyManagerFactory.init(keyStore, ...)` cấp khả năng trình certificate/key của chính bên này.
- `SSLContext.init(keyManagers, trustManagers, secureRandom)` kết hợp những quyết định trên.
- Một framework có thể tạo `SSLContext` riêng và bỏ qua JVM-wide properties; hãy kiểm tra thực tế context của từng HTTP client.
- Khi cần kết hợp CA nội bộ với CA công khai, hãy quản lý trust policy tường minh, không thêm CA không tin cậy tùy tiện.

### 6.3. Phân biệt key password và store password

`KeyStore.load(...)` dùng store password, còn private key entry có thể có key password/PKCS12 settings khác theo provider. Sửa “không đọc được private key” bằng cách dùng password truststore thay key password mà không hiểu cấu trúc có thể vẫn fail.

Không truyền password production trong command-line flags dễ bị ghi vào process-list, shell history, CI log. Dùng secret manager và API/cơ chế triển khai được phê duyệt; kiểm soát file permissions và audit.

## 7. Nhóm 2 — Không có cấu hình TLS chung (no common configuration)

Ngay cả khi certificates đều có thể tin, hai bên vẫn phải **thương lượng được** phiên bản TLS, thuật toán trao đổi khóa, chữ ký và cipher suite phù hợp.

### 7.1. Không có chung protocol version

Client chỉ bật `TLSv1.3`, server chỉ bật `TLSv1.2`:

```text
Client enabled protocols: [TLSv1.3]
Server enabled protocols: [TLSv1.2]
Intersection: EMPTY
Result: handshake cannot select common TLS version
```

Có thể thấy `protocol_version`, `handshake_failure`, `No appropriate protocol` hoặc thông báo khác. Không khẳng định một thông báo duy nhất sẽ xuất hiện ở mọi provider.

Điểm phân biệt:
- **Supported**: provider có khả năng hỗ trợ nếu chính sách cho phép.
- **Enabled**: danh sách cấu hình cho instance, ví dụ `SSLEngine.getEnabledProtocols()`.
- **Negotiated**: phiên bản được chọn **sau handshake**, có thể xem từ `SSLSession.getProtocol()`.
- **Disabled by security policy**: phiên bản tồn tại trong provider nhưng bị cấm qua cấu hình như `jdk.tls.disabledAlgorithms`.

Nếu server chỉ còn TLS 1.0/1.1, không nên bật những protocol yếu đó trong client production để “nối được”. Cần nâng cấp server/gateway và tuân thủ policy hiện hành.

### 7.2. Không có chung cipher suite

```text
Client enabled suites: [TLS_AES_128_GCM_SHA256]
Server enabled suites: [TLS_CHACHA20_POLY1305_SHA256]
Intersection: EMPTY
```

Đây là một ví dụ cấu hình **cố ý không giao nhau**. Chưa xét suite có được provider cụ thể hỗ trợ hay được security policy cho phép không; nếu hai bên chỉ cấu hình như trên, không có suite chung để chọn.

Trường hợp thực tế:
- Client/server không có suite được phép trùng nhau.
- Có suite chung trên giấy nhưng policy `jdk.tls.disabledAlgorithms` hoặc provider không cho dùng.
- TLS 1.2 suite yêu cầu một loại certificate/key exchange không có trong server keystore.
- Security gateway bỏ suite/curve theo policy, service runtime vẫn dùng cấu hình cũ.

### 7.3. Đặc điểm TLS 1.3: suite không kể hết handshake

TLS 1.3 cipher suite như `TLS_AES_128_GCM_SHA256` hoặc `TLS_AES_256_GCM_SHA384` mô tả chủ yếu thuật toán AEAD và HKDF hash. Nó **không tự chỉ định** server dùng RSA certificate, ECDSA certificate, Ed25519 signature hay nhóm ECDHE nào.

Vì vậy, sau khi xác nhận có cipher suite chung, vẫn cần xét:
- **Signature schemes:** `signature_algorithms` từ client và những thuật toán signer/server certificate hỗ trợ.
- **Supported groups/named groups:** nhóm X25519, P-256, P-384... theo policy.
- **Key shares:** có thể có `HelloRetryRequest` khi cần nhóm khác; nếu không có nhóm dùng chung, handshake fail.
- **Certificate/key algorithm:** server có private key và certificate chain phù hợp lựa chọn.
- **ALPN:** giao thức ứng dụng `h2`/`http/1.1` hoặc profile khác có được hai bên cho phép không.
- **SNI:** tên máy chủ dùng để chọn đúng certificate/vhost/backend.

Không giải thích toàn bộ lỗi TLS 1.3 bằng câu “cipher suite không chung” khi nguyên nhân có thể là **chữ ký hoặc key share**.

### 7.4. `No available authentication scheme`

JSSE/peer có thể báo lỗi kiểu:

```text
No available authentication scheme
No available certificate corresponding to the SSL cipher suites...
```

Đừng vội kết luận CA không tin. Ở **server side**, nó thường gợi ý không có certificate/private key phù hợp các thuật toán/schemes mà peer chấp nhận, hoặc key manager không chọn được alias đúng.

Kiểm tra:
1. `KeyManagerFactory` đã nạp đúng keystore?
2. Keystore có **PrivateKeyEntry** chứ chỉ là **trustedCertEntry**?
3. Certificate chain và private key có đi cùng nhau?
4. Key type RSA/EC/EdDSA, signature schemes, KeyUsage/EKU có được JSSE hỗ trợ và policy cho phép?
5. SNI đang yêu cầu virtual host nào, key manager chọn alias ra sao?
6. Java provider có hỗ trợ thuật toán key và signature phù hợp không?
7. Client có đang yêu cầu cấu hình chỉ hỗ trợ một scheme server không có không?

Với **mTLS client side**, cũng có thể không có client certificate phù hợp yêu cầu `CertificateRequest` của server. Hãy xem `keymanager` trace của client và trust policy của server.

### 7.5. `Handshake failure` vẫn có thể là ALPN/SNI issue

- Server dùng SNI để chọn certificate hoặc routing; nếu client không gửi tên host phù hợp, server có thể trả default certificate **không khớp SAN** hoặc từ chối.
- ALPN cho phép chọn protocol ứng dụng như `h2` hoặc `http/1.1`. Nếu policy yêu cầu một protocol mà không có phần giao, server có thể gửi alert như `no_application_protocol`.
- Một HTTP server có thể chấp nhận TLS handshake nhưng application protocol sau đó bị hiểu sai; đừng trộn lỗi HTTP 2 framing với PKIX.
- Proxy có thể terminate TLS, gửi request lên upstream với SNI/ALPN khác endpoint gốc; cần kiểm tra cấu hình ở đúng hop.

## 8. Nhóm 3 — mTLS: lỗi chứng thư phía client

Khi server yêu cầu client certificate:
- Server gửi tín hiệu yêu cầu certificate theo handshake TLS đang dùng.
- Client `KeyManager` phải tìm được cert/private key phù hợp với issuer/signature schemes/policy.
- Client trình certificate và thực hiện handshake proof tương ứng.
- Server `TrustManager` kiểm tra certificate chain/clientAuth constraints.
- Ứng dụng còn phải map certificate identity sang principal/tenant/role và làm authorization.

### 8.1. Những tình huống thường gặp

| Triệu chứng | Bên cần quan sát | Nguyên nhân khả dĩ |
| --- | --- | --- |
| Server báo `certificate_required` | Client và server | Client không gửi certificate khi server yêu cầu |
| Server báo `unknown_ca` hoặc cert-path error | Server trust config | CA phát client cert chưa được server tin |
| Client log `Received fatal alert: bad_certificate` | Server quyết định | Client certificate/usage/chain không đạt server policy |
| Client keymanager không chọn alias | Client | Không có PrivateKeyEntry/issuer/key type phù hợp |
| Handshake pass nhưng API 403 | Application server | Certificate identity không có quyền, không phải PKIX error |
| Một số nodes mTLS pass, một số fail | Gateway/backend fleet | Truststore, CA rollout hoặc cert rotation chưa đồng bộ |

**Truststore của Java client dùng xác minh server** khác **truststore của server dùng xác minh client**. Import CA client vào truststore của chính client thường không giúp server tin client certificate.

### 8.2. Client có cert không có nghĩa server chấp nhận

Phải xem:
- Certificate chain đầy đủ và issuer mà server chấp nhận.
- Private key matching certificate.
- EKU `clientAuth`, KeyUsage và validity.
- Certificate DN/SAN mapping tới workload identity được phép.
- CA/root và revocation policy phía server.
- TLS signature algorithms/grouplist/certificate request context.

Đừng bật mTLS bằng cách tắt server validation để client kết nối được; nó đổi từ authenticated client/server thành một kết nối dễ bị MITM.

## 9. JSSE debugging: bật đúng công tắc, đọc đúng giai đoạn

Đây là công cụ chính để chẩn đoán Java TLS trong môi trường kiểm thử được kiểm soát.

### 9.1. JSSE debug flags

Ví dụ command-line (dùng tên class ứng dụng của bạn):

```bash
java -Djavax.net.debug=ssl,handshake,trustmanager,keymanager MyTlsApp
```

Các nhóm debug phổ biến:
- `ssl`: SSL/TLS debug chung.
- `handshake`: các handshake messages và quá trình thương lượng.
- `trustmanager`: dấu vết trust manager và certificate validation theo implementation.
- `keymanager`: chọn key/certificate của chính endpoint.
- `session` hoặc `sessioncache`: hỗ trợ tìm hiểu resumption/cache khi cần.
- `record`: thông tin TLS records; cân nhắc dữ liệu được log.

Để xem các lựa chọn được **JDK đang chạy** hỗ trợ, có thể dùng:

```bash
java -Djavax.net.debug=help MyTlsApp
```

Tùy provider, chế độ này có thể hiển thị help và dừng ứng dụng. Không giả định log format là một API ổn định: Oracle lưu ý các diagnostic options có thể thay đổi và provider khác SunJSSE có thể không hỗ trợ cùng cách.

**Cảnh báo:** không bật mặc định `all`, `plaintext`, `packet` hoặc các chế độ dump bytes cho hệ thống production có dữ liệu thật. Traces có thể lộ certificate metadata, session identifiers, handshake content, hostname/tenant và thông tin nhạy cảm khác; cần access controls, retention và redaction.

### 9.2. CertPath debug flags

Khi gặp `PKIX path building failed` hoặc algorithm/chain constraints:

```bash
java -Djavax.net.debug=ssl,handshake,trustmanager -Djava.security.debug=certpath MyTlsApp
```

`java.security.debug=certpath` cung cấp dấu vết liên quan PKIX path building/validation trong Sun security provider khi tương ứng. Các bản JDK có thể khác format và lượng thông tin. Không cần bật certpath cho mọi lỗi như TCP refused hoặc ALPN mismatch.

### 9.3. Cách đọc JSSE trace hữu ích

Một trình tự đọc:

1. **ClientHello**: TLS versions được đề xuất, cipher suites, `signature_algorithms`, `supported_groups`, `key_share`, SNI và ALPN.
2. **ServerHello** nếu có: version/suite được chọn, key share/PSK hoặc HelloRetryRequest.
3. **Certificate/CertificateRequest** (nếu có): chain server, mTLS yêu cầu client cert.
4. **Trust/key manager log**: CA anchors đã load, alias/certificate được chọn; path failure/hostname mismatch.
5. **Fatal alert trước nhất**: tên alert, phía gửi/nhận và cause.
6. **Application data**: nếu đã tới đây, TLS ban đầu có thể đã thành công, kiểm tra lỗi ở tầng khác.
7. **Đối chiếu server-side trace**: đặc biệt khi client chỉ thấy `Received fatal alert: handshake_failure`.

Không sao chép toàn bộ debug log vào email/ticket công khai. Chỉ trích những trường đủ khoanh vùng lỗi, loại bỏ token/cookie, thông tin cá nhân, secret, key share/debug bytes không cần thiết.

### 9.4. Trước khi tăng debug level

Kiểm tra những thứ đơn giản ít xâm phạm:
- `java -version` và JDK thực sự đang chạy trong container.
- URL/hostname/port, TLS termination point, proxy, gateway route.
- Cờ startup liên quan `javax.net.ssl.trustStore`/`keyStore`/`https.protocols`/`jdk.tls.client.protocols`, nhưng cẩn trọng flag nào áp cho API/provider nào.
- Security properties như `jdk.tls.disabledAlgorithms`, `jdk.certpath.disabledAlgorithms`.
- File truststore/keystore thực sự đọc được, permissions, cert chain, alias.
- Time synchronization, certificate expiration, SAN, certificate rotation status.
- Server version/certificate policy, mTLS requirements và SNI/ALPN selection.

Một số thuộc tính nằm trong file `java.security` của JDK hoặc được override qua cơ chế JVM; đừng suy từ một máy dev rằng production container có security policy giống hệt.

## 10. `keytool`: kiểm tra chứng thư và stores mà không làm mất trust model

Các lệnh sau để **đọc/kiểm tra**, không tự thêm CA hoặc làm yếu TLS.

### 10.1. Quan sát certificate server

```bash
keytool -printcert -sslserver api.example.com:443 -rfc
```

Lệnh có thể hiển thị certificate được server trình trên kết nối tới host/port, tùy network và tool. **Certificate được in ra không đồng nghĩa certificate đã được tin cậy**; cần kiểm tra chain/trust anchor/expected hostname bằng TLS client chuẩn. Với vhost phức tạp, kiểm tra SNI/tooling tương thích để chắc chắn đã thấy đúng certificate.

### 10.2. Đọc certificate file

```bash
keytool -printcert -file server-certificate.pem
```

Xem:
- Subject/Issuer.
- Validity Not Before/Not After.
- Subject Alternative Name (DNS/IP names).
- KeyUsage, ExtendedKeyUsage.
- BasicConstraints/CA flag.
- SHA-256 fingerprint **sau khi xác minh nguồn đáng tin**.

Fingerprint chỉ giúp so sánh nếu có reference value từ kênh xác thực khác. Nếu attacker đưa certificate cùng fingerprint tính từ chính certificate ấy qua một kênh không tin cậy, phép so sánh không chứng minh ownership.

### 10.3. Kiểm tra truststore (ví dụ PKCS12)

```bash
keytool -list -v -keystore truststore.p12 -storetype PKCS12
```

Lệnh có thể yêu cầu password tương tác. Kiểm tra certificate entries/issuers và có đúng CA được cấp phép chưa. **Không** vội nhập password vào dòng lệnh hoặc CI log; đường dẫn ở đây chỉ là ví dụ.

### 10.4. Kiểm tra keystore chứa identity

```bash
keytool -list -v -keystore identity.p12 -storetype PKCS12
```

Điểm cần tìm:
- Alias mong muốn có loại `PrivateKeyEntry`, không chỉ `trustedCertEntry`.
- Certificate chain đi kèm private key.
- Thuật toán/độ dài key, chữ ký, EKU/KeyUsage, SAN và validity.
- Với mTLS, issuer/key type có phù hợp server `CertificateRequest` không.
- Service có đang đọc đúng keystore đó, đúng alias và mật khẩu key không.

**Không** in/export private key ra log/ticket để kiểm tra “key có đúng không”. Nếu cần kiểm tra key matching, dùng API/provider có kiểm soát, certificate public key và bước thử ký an toàn trong môi trường được cấp phép.

## 11. Java 17+: chương trình offline phân biệt capability và cấu hình không chung

Ví dụ đầu tiên **không mở socket, không gửi TLS ClientHello thật và không yêu cầu server**. Nó được thiết kế để kiểm tra được những điều dễ nhầm:

1. `SSLContext`/`SSLEngine` có danh sách **supported protocols** từ provider.
2. `SSLParameters` đặt `EndpointIdentificationAlgorithm("HTTPS")` ở client mode. Đây là **yêu cầu thực thi hostname verification khi có TLS handshake tương ứng**, không chứng minh đã xác minh một host trong demo offline.
3. Hai bộ cấu hình TLS protocol được mô phỏng **không có phần giao**, khác với một cặp có thể giao nhau.
4. Hai bộ cipher-suite names được mô phỏng **không có phần giao**. Đây mới là **điều kiện cần**, không đủ để bảo đảm handshake thật thành công.
5. Một `SSLHandshakeException` tổng hợp bọc `CertificateExpiredException` được phân loại theo **cause class**, thay vì đoán chỉ từ chuỗi message.
6. Không tuyên bố “đã sửa TLS” vì chưa có remote server hoặc full handshake.

Tạo file **`JsseHandshakePreflightDemo.java`**:

```java
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertPathValidatorException;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;

public class JsseHandshakePreflightDemo {
    static boolean overlap(String[] first, String[] second) {
        for (String a : first) {
            for (String b : second) {
                if (a.equals(b)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String safeCauseCategory(Throwable problem) {
        for (Throwable current = problem;
                current != null;
                current = current.getCause()) {
            if (current instanceof CertificateExpiredException) {
                return "CERTIFICATE_EXPIRED";
            }
            if (current instanceof CertificateNotYetValidException) {
                return "CERTIFICATE_NOT_YET_VALID";
            }
            if (current instanceof CertPathValidatorException) {
                return "CERT_PATH_INVALID";
            }
            if (current instanceof CertificateException) {
                return "CERTIFICATE_ERROR";
            }
        }
        return "OTHER_HANDSHAKE_ERROR";
    }

    public static void main(String[] args) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, new SecureRandom());

        SSLEngine engine = context.createSSLEngine(
                "api.example.invalid", 443
        );
        engine.setUseClientMode(true);

        SSLParameters parameters = engine.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        engine.setSSLParameters(parameters);

        System.out.println("Supports TLSv1.2: "
                + Arrays.asList(engine.getSupportedProtocols())
                        .contains("TLSv1.2"));
        System.out.println("Supports TLSv1.3: "
                + Arrays.asList(engine.getSupportedProtocols())
                        .contains("TLSv1.3"));
        System.out.println("Client mode: " + engine.getUseClientMode());
        System.out.println("Endpoint identification: "
                + engine.getSSLParameters()
                        .getEndpointIdentificationAlgorithm());

        System.out.println("Disjoint example protocols overlap: "
                + overlap(
                    new String[]{"TLSv1.3"},
                    new String[]{"TLSv1.2"}
                ));

        System.out.println("Compatible example protocols overlap: "
                + overlap(
                    new String[]{"TLSv1.3", "TLSv1.2"},
                    new String[]{"TLSv1.2"}
                ));

        System.out.println("Disjoint example suites overlap: "
                + overlap(
                    new String[]{"TLS_AES_128_GCM_SHA256"},
                    new String[]{"TLS_CHACHA20_POLY1305_SHA256"}
                ));

        SSLHandshakeException sample =
                new SSLHandshakeException("redacted");
        sample.initCause(
                new CertificateExpiredException("test fixture")
        );
        System.out.println("Synthetic failure category: "
                + safeCauseCategory(sample));

        System.out.println("Real handshakes performed: 0");
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac JsseHandshakePreflightDemo.java
java JsseHandshakePreflightDemo
```

Stdout đã kiểm chứng bằng JDK SunJSSE cung cấp TLSv1.2 và TLSv1.3 (kết quả hỗ trợ protocol có thể thay đổi nếu bạn dùng một provider đặc biệt):

```text
Supports TLSv1.2: true
Supports TLSv1.3: true
Client mode: true
Endpoint identification: HTTPS
Disjoint example protocols overlap: false
Compatible example protocols overlap: true
Disjoint example suites overlap: false
Synthetic failure category: CERTIFICATE_EXPIRED
Real handshakes performed: 0
```

### 11.1. Tại sao test này đáng tin và có giới hạn?

- `engine.getSupportedProtocols()` trả những version mà provider hỗ trợ; **không khẳng định** chúng đều enabled hoặc được phép sau security policy. Có thể dùng `getEnabledProtocols()` để xem cấu hình cụ thể của engine.
- `setEndpointIdentificationAlgorithm("HTTPS")` chỉ đặt policy cho một engine. Vì không có handshake, chương trình chưa kiểm tra một certificate hoặc hostname thật.
- `overlap(...)` làm việc với **các string đã cho**, chưa xét certificate, nhóm X25519/P-256, signature scheme, trust anchors và thuật toán bị disabled. **Có phần giao là điều kiện cần, không đủ**.
- `SSLHandshakeException sample` được tạo **có chủ đích từ test fixture**, không phải lỗi SSL thực tế. Phân loại `CERTIFICATE_EXPIRED` nhằm minh họa đọc cause chain; trong production, lỗi certificate còn cần kiểm tra cert/chain/peer logs.
- Việc debug thực tế có thể cần xem `SSLProtocolException`, `SSLException`, `CertPathBuilderException`, provider-specific classes và peer alert; `safeCauseCategory` không phải bộ phân loại tất cả nguyên nhân.
- Không in các bytes của session secrets, private key, certificate chain thô hay thông tin người dùng; các string về config đều công khai giả lập.

### 11.2. Vì sao không tự động sửa suite khi `overlap=false`?

Giả sử client chỉ cho phép TLS 1.3 và server chỉ còn TLS 1.2. Bước đúng là hỏi:
1. Vì sao phía server không hỗ trợ TLS 1.3? Có thể nâng cấp runtime/gateway được không?
2. Nếu policy tổ chức vẫn cho phép TLS 1.2, cả hai phía có ít nhất một suite/nhóm/chữ ký an toàn không?
3. Nếu server chỉ có protocol/suite đã bị cấm, **không mở lại cấu hình yếu** chỉ để kết nối.
4. Khi thay đổi protocol, chạy lại certificate validation, mTLS, ALPN/HTTP version và negative tests.

Tương tự, thấy `overlap=true` không phải lý do bỏ bước kiểm tra truststore/hostname; chỉ có cơ hội thương lượng một version, chưa chắc có authenticated TLS.

## 12. Java 17+: chương trình kết nối thật khi cần quan sát handshake

Ví dụ thứ hai **cần mạng và một HTTPS endpoint mà bạn được quyền kiểm tra**. Nó cho thấy cách:
- Kết nối TCP với connect timeout.
- Bọc socket thành `SSLSocket` dùng JSSE.
- Đặt **HTTPS endpoint identification** theo host bạn yêu cầu trước khi handshake.
- Gọi `startHandshake()` rõ ràng.
- Phân biệt `SSLHandshakeException` và những `IOException` khác, không bypass trust.
- Trả ra **chỉ metadata đã kiểm soát**, không ghi raw certificates/secrets.

Tệp **`JsseLiveProbe.java`**:

```java
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.IOException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

public class JsseLiveProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java JsseLiveProbe <hostname> <port>");
            return;
        }

        String host = args[0]; // Intended DNS identity from trusted configuration.
        int port = Integer.parseInt(args[1]);

        SSLContext context = SSLContext.getDefault();
        try (Socket tcp = new Socket()) {
            tcp.connect(new InetSocketAddress(host, port), 5000);
            tcp.setSoTimeout(5000);

            try (SSLSocket tls = (SSLSocket) context.getSocketFactory()
                    .createSocket(tcp, host, port, true)) {
                tls.setUseClientMode(true);
                tls.setSoTimeout(5000);

                SSLParameters parameters = tls.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                tls.setSSLParameters(parameters);

                tls.startHandshake();
                System.out.println("Handshake: OK");
                System.out.println("Protocol: " + tls.getSession().getProtocol());
                System.out.println("Cipher suite: "
                        + tls.getSession().getCipherSuite());
            }
        } catch (SSLHandshakeException failure) {
            System.err.println("Handshake: FAILED (see controlled JSSE trace)");
            System.err.println("Exception type: "
                    + failure.getClass().getSimpleName());
            throw failure; // In a lab; production API should map to safe error.
        } catch (IOException networkOrTls) {
            System.err.println("Connection/I-O failure: "
                    + networkOrTls.getClass().getSimpleName());
            throw networkOrTls;
        }
    }
}
```

Lệnh ví dụ trong lab:

```bash
javac JsseLiveProbe.java
java -Djavax.net.debug=ssl,handshake,trustmanager -Djava.security.debug=certpath JsseLiveProbe api.example.com 443
```

**Không có stdout cố định**: certificate, TLS protocol, cipher suite, DNS/network/proxy và truststore phụ thuộc endpoint/môi trường. `api.example.com` chỉ là tên minh họa, phải thay bằng hostname thực sự được phép kiểm tra. Không chạy chương trình trên endpoint không được quản trị/cho phép hoặc dùng tool để quét mạng ngoài phạm vi.

**Giới hạn:** đây là HTTPS TLS probe mức cơ bản; nó chưa gửi HTTP request, không thiết kế cho proxy authentication, SNI override, mTLS credentials riêng, ALPN/HTTP2 hoặc truststore tùy biến. Thư viện HTTPS client production như `java.net.http.HttpClient` thường phù hợp hơn, và có thể tự thực hiện endpoint identification/trust policy theo cấu hình. Nếu phải dùng custom `SSLSocket`, đừng quên explicitly bật hostname verification như trên.

### 12.1. Khi không thấy certificate error

Nếu `startHandshake()` thành công, đây là bằng chứng một TLS connection được tạo theo trust/identity policy của context đang dùng; **không** tự kết luận application principal được phép truy cập resource. HTTP 401/403 là tầng xác thực/phân quyền sau đó.

Nếu `startHandshake()` thất bại ngay trước certificate, không có nghĩa certificate “đúng” hoặc “sai” — có thể chưa tới bước ấy do protocol/suite/SNI mismatch.

## 13. Thực hành phân loại lỗi theo dấu hiệu

| Thông báo/nhóm lỗi | Giả thuyết ưu tiên | Kiểm tra để xác nhận | Không nên làm |
| --- | --- | --- | --- |
| `PKIX path building failed` | Thiếu CA/path, sai truststore, thiếu intermediate | JSSE trustmanager + certpath, actual truststore, chain | Trust-all/auto-import root từ peer |
| `trustAnchors must be non-empty` | Truststore rỗng/sai file | File path, store type, entries, custom SSLContext | Chấp nhận bất kỳ certificate |
| `CertificateExpiredException` | Leaf/intermediate expired, clock skew | Time NTP, validity toàn chain, cert rollout | Đặt giờ sai, bỏ validity |
| `CertificateNotYetValidException` | Certificate future/notBefore, đồng hồ lệch | Clock/timezone & cert validity | Bỏ thời gian kiểm tra |
| `No subject alternative ...` | Sai endpoint identity | Requested host, SAN, SNI, vhost | HostnameVerifier always true |
| `Received fatal alert: unknown_ca` | Một peer không tin CA của phía còn lại | Đọc logs cả hai bên, mTLS direction | Chỉ sửa client truststore vô căn cứ |
| `bad_certificate` | Certificate bị peer từ chối | Key usage, chain, EKU, thời gian, trust policy | Tắt client auth để “pass” |
| `certificate_required` | Thiếu client certificate khi mTLS | Server CertificateRequest, client keymanager alias | Thêm CA sai phía |
| `No available authentication scheme` | Không có private key/cert/signer phù hợp | Keymanager, PrivateKeyEntry, sig schemes, SNI | Bật tất cả cipher yếu |
| `No cipher suites in common` | Suite intersection rỗng hoặc bị constraint | Config cả hai phía, TLS version, JDK policy | Enable RC4/3DES/NULL |
| `No appropriate protocol` | Không có TLS version hợp lệ chung | Enabled protocols, disabled algorithms | Bật SSLv3/TLS1.0 |
| `Received fatal alert: protocol_version` | Peer không chấp nhận version | ClientHello/ServerHello và server TLS policy | Downgrade tùy tiện |
| `no_application_protocol` | ALPN mismatch | `h2`/`http/1.1`, gateway/app policy | Đổi certificate vô căn cứ |
| `handshake_failure` chung | Nhiều khả năng, kể cả SNI/keys/algorithms | JSSE+peer trace theo handshake stage | Kết luận chỉ từ một dòng |
| `Socket closed`/EOF sau ClientHello | Peer/network chặn, non-TLS listener, TLS policy | TCP path, load balancer logs, server listener | Đổi truststore không kiểm chứng |
| TLS success rồi HTTP `403` | Authorization của app | Access token, roles, tenant/resource | Xóa TLS session hoặc disable validation |

Bảng trên là **heuristic chẩn đoán**, không phải quy tắc message→cause chắc chắn cho mọi JDK, provider và phiên bản TLS.

## 14. Một ví dụ điều tra theo từng bước

### 14.1. Ca A: Java client fail nhưng browser truy cập được

Tình huống:
- Java `HttpClient` gọi `https://api.example.com`, ném `SSLHandshakeException` với `PKIX path building failed`.
- Trình duyệt trên laptop mở được URL.

Các bước:
1. Java có chạy trong container với truststore/JDK khác browser không?
2. Browser có đang dùng trust store OS, CA enterprise hoặc HTTP proxy/TLS inspection mà JVM không dùng?
3. Server có gửi đủ chain, đặc biệt intermediate certificates, cho đúng SNI?
4. Java runtime đang dùng `cacerts` nào, có properties override hoặc custom `SSLContext` không?
5. CA chain của server đã được xác minh từ kênh tin cậy chưa?
6. Nếu server thiếu intermediate, sửa cấu hình server/gateway để gửi đúng chain.
7. Nếu ứng dụng cần tin CA enterprise/internal, thêm **đúng CA đã kiểm chứng** theo chính sách, không trust certificate ngẫu nhiên từ network.
8. Chạy lại Java TLS handshake với expected hostname và bật test wrong-CA/wrong-hostname để chứng minh vẫn fail closed.

**Điểm học:** browser success không chứng minh CA đã có trong JDK; JVM trust là cấu hình cụ thể.

### 14.2. Ca B: mTLS fail sau deploy

Tình huống:
- Service A dùng client certificate gọi Service B qua mTLS.
- Sau rotation CA, client thấy `Received fatal alert: bad_certificate`.

Các bước:
1. Ghi nhận TLS termination point của Service B (gateway hay backend).
2. Ở client A, xem `keymanager` trace có chọn PrivateKeyEntry đúng key/certificate chain không.
3. Ở server B, xem `trustmanager` trace/PKIX có tin issuer CA mới không; certificate client có còn hạn/EKU clientAuth đúng không.
4. Kiểm tra B có nhiều nodes và rollout CA/truststore có đồng bộ không.
5. Xác minh service identity SAN và policy mapping của A.
6. Nếu server B đã từ chối CA mới, cập nhật truststore của B theo rotation plan (có giai đoạn overlap), không chỉnh truststore client A thay thế.
7. Sau handshake success, xác minh authorization service principal tới API vẫn đúng.

**Điểm học:** “lỗi certificate trên client” không đồng nghĩa **client** là bên thực hiện trust validation. Client có thể chỉ nhận alert từ server.

### 14.3. Ca C: TLS versions không giao nhau

Tình huống:
- Client chỉ bật TLS 1.3.
- Legacy gateway chỉ bật TLS 1.2.
- Java client thấy `protocol_version` hoặc `handshake_failure`.

Các bước:
1. Thu cấu hình enabled protocols hai bên.
2. So phần giao, xác nhận rỗng.
3. Đánh giá nâng cấp gateway, TLS termination hoặc chọn cấu hình TLS 1.2 **chỉ nếu chính sách cho phép**.
4. Sau thay đổi, kiểm tra suites, signature algorithms, named groups, certificate và hostname.
5. Giữ negative tests từ chối phiên bản yếu, không tắt `jdk.tls.disabledAlgorithms`.

**Điểm học:** đây là negotiation mismatch, không nhất thiết do certificate/truststore.

### 14.4. Ca D: có chung cipher suite nhưng vẫn `handshake_failure`

Tình huống:
- Hai bên cùng liệt kê `TLS_AES_128_GCM_SHA256`.
- Server chỉ có một certificate/key không được client chấp nhận hoặc signer/group không phù hợp.

Cần kiểm tra:
- TLS 1.3 `signature_algorithms`, `supported_groups`, `key_share`, certificate public key.
- Server keymanager có PrivateKeyEntry phù hợp không?
- CA/cert chain có áp policy đúng không?
- SNI đã chọn vhost/certificate đúng chưa?
- Security properties loại bỏ thuật toán/độ dài key nào?
- Có `CertificateRequest` và client cert requirements không?

**Điểm học:** TLS 1.3 suite intersection không bảo đảm có authentication/key-share configuration dùng chung.

### 14.5. Ca E: chỉ một hostname trên cùng server fail

Tình huống:
- `api.internal.example` pass, `billing.internal.example` fail.
- Cả hai DNS trỏ cùng gateway IP.

Khả năng:
- SNI route trả certificate/default backend sai.
- Billing cert thiếu SAN/expired; API cert đúng.
- Một virtual host chỉ cho phép ALPN/TLS policy khác.
- Load balancer một node có certificate cũ.
- App gọi IP literal nhưng kỳ vọng certificate DNS.

Đừng dùng `-Dcom.sun.net.ssl.checkRevocation=false` hay tắt hostname verification để chữa. Thu đúng certificate trên mỗi SNI và sửa virtual-host/certificate chain.

## 15. Runbook một lần theo thứ tự ít rủi ro

### Bước 1 — Chuẩn bị chứng cứ tối thiểu

Ghi:
- JDK runtime/provider cụ thể.
- Client→host:port và expected hostname (không thay bằng IP tùy ý).
- TLS termination point và có proxy/mTLS không.
- Thời điểm lỗi, deploy/CA rotation gần nhất.
- Loại exception/cause chain **đã sanitize**, handshake stage cuối đã xác định.

### Bước 2 — Tách network khỏi TLS

Xác nhận DNS, port, listener, firewall, proxy và upstream route. Nếu không tới được port TLS, không cần đào sâu PKIX. Nếu tới một HTTP-only port mà client nói TLS, sẽ có lỗi record/protocol mà import CA không sửa được.

### Bước 3 — Bật debug vừa đủ ở môi trường được phép

```bash
java -Djavax.net.debug=ssl,handshake,trustmanager,keymanager MyTlsApp
```

Nếu certificate trust path:
```bash
java -Djavax.net.debug=ssl,handshake,trustmanager -Djava.security.debug=certpath MyTlsApp
```

Giới hạn log retention, không upload traces thô chứa dữ liệu nhạy cảm vào kênh công khai.

### Bước 4 — So cấu hình hai phía

- Enabled TLS versions, cipher suites và algorithm constraints.
- TLS 1.3 supported groups/key shares/signature schemes.
- Server certificate/private key, mTLS client cert và CA.
- SNI expected host, ALPN application protocols.
- Resumption/full handshake và server node nếu failure chỉ theo traffic.

### Bước 5 — Xác minh PKIX/hostname

- Certificate chain leaf→intermediates→trust anchor.
- Truststore thực sự của từng endpoint, không chỉ file tên gần giống.
- Validity, SAN, issuer, BasicConstraints, EKU/KeyUsage, algorithm constraints, revocation policy.
- Expected hostname đúng từ client/service config.
- Chứng minh private key/handshake policy qua TLS stack, không chỉ `X509Certificate.verify(...)`.

### Bước 6 — Chọn cách sửa an toàn và kiểm thử âm

Ví dụ:
- Server thiếu intermediate: sửa server chain.
- CA enterprise chưa được JVM tin: cập nhật truststore **sau khi kiểm chứng CA**.
- Sai hostname: cấp cert đúng SAN, sửa SNI/route hoặc dùng expected host đúng.
- mTLS CA rollout lệch: sửa server trust anchors và kế hoạch rotation.
- No shared TLS version: nâng cấp server hoặc bật duy nhất version/suites được policy cho phép.
- Missing key entry: cấp/cấu hình PrivateKeyEntry phù hợp và bảo vệ private key.
- ALPN mismatch: cấu hình protocol ứng dụng được cả hai hỗ trợ.

Sau đó **đảm bảo các trường hợp sai CA, sai host, hết hạn, cipher yếu và thiếu cert mTLS vẫn bị từ chối**.

### Bước 7 — Ghi nhận nguyên nhân và phòng tái phát

- Phân loại lỗi đúng nhóm, tránh `SSLHandshakeException` chung chung.
- Health checks với certificate expiry/chain/hostname.
- CA/certificate rotation alert trước hạn, staged rollout và rollback plan.
- Kiểm tra truststore/key entry ngay khi khởi động (không in secrets).
- Kiểm thử contract TLS/mTLS giữa teams và gateways trong CI staging.
- Theo dõi handshake failures theo TLS termination point, JDK/provider, route, SNI và policy version.
- Duy trì runbook kiểm tra cả **peer log** khi client chỉ nhận fatal alert chung.

## 16. Những điều tuyệt đối không dùng làm “fix”

| Biện pháp sai | Vì sao nguy hiểm | Hướng thay thế |
| --- | --- | --- |
| TrustManager chấp nhận tất cả certificates | Bỏ chain/path validation, mở MITM | Dùng truststore chứa CA đã xác minh |
| HostnameVerifier luôn true | Certificate của host khác cũng được chấp nhận | Sửa SAN/SNI/expected hostname |
| Cài đặt một root lấy từ peer vào truststore | Tin nguồn CA có thể do attacker kiểm soát | Xác minh root qua kênh quản trị |
| Bật SSLv3/TLS 1.0/1.1 hoặc NULL/anonymous suites | Làm yếu protocol, có thể bị downgrade | Nâng cấp peer và giữ algorithm policy |
| Xóa `jdk.tls.disabledAlgorithms` để ép kết nối | Mở lại thuật toán/khóa đã bị cấm | Thay certificate/key/server config |
| Bỏ `CertificateException` rồi tiếp tục | Xác thực peer không còn đáng tin | Fail closed, sửa certificate/trust |
| Log private key, TLS session secrets, token | Rò rỉ lâu dài qua diagnostics | Chỉ log metadata và cause category |
| Đoán nguyên nhân chỉ từ `handshake_failure` | Alert generic, peer thường biết rõ hơn | JSSE+peer logs theo stage |
| Bắt `SSLHandshakeException` rồi retry vô hạn | Có thể DoS, che cấu hình sai, làm chậm recovery | Retry có kiểm soát khi lỗi tạm thời |
| Dùng HTTP plaintext để “tạm chạy” | Mất confidentiality/integrity/peer authentication | Sửa TLS hoặc chặn giao dịch |
| Bỏ kiểm tra mTLS clientAuth vì CA chưa đồng bộ | Mất client authentication và principal binding | Cập nhật CA theo rotation có kiểm soát |
| Chỉ test happy path sau “fix” | Security bypass có thể khiến test pass | Test wrong-CA, wrong-host, expired, mTLS rejection |

## 17. Checklist QA và regression

### Certificate trust và identity

- [ ] Valid chain tới approved CA + SAN đúng → handshake thành công.
- [ ] Self-signed untrusted → bị từ chối.
- [ ] CA không nằm trong truststore → bị từ chối.
- [ ] Leaf/intermediate expired/notYetValid → bị từ chối theo policy.
- [ ] Valid chain nhưng wrong SAN/hostname → bị từ chối.
- [ ] Issuer/chain bị sửa hoặc thiếu intermediate → được xử lý đúng (fail hoặc builder path hợp lệ).
- [ ] EKU/KeyUsage/BasicConstraints không phù hợp → bị từ chối.
- [ ] Revocation/OCSP/CRL theo policy được kiểm tra.

### Negotiation

- [ ] Client/server chỉ có protocol sets không giao nhau → handshake fail, không downgrade.
- [ ] Có protocol nhưng không có cipher suite chung → handshake fail theo policy.
- [ ] TLS 1.3 suite chung nhưng signature schemes không giao → fail thích hợp.
- [ ] Named groups/key shares không có điểm chung → fail/HelloRetryRequest theo protocol.
- [ ] ALPN bắt buộc không giao → fail/handled theo app profile.
- [ ] SNI sai → cert/route được chọn đúng policy, wrong host không pass.
- [ ] Security disabled algorithms không bị bỏ qua khi sửa.

### mTLS

- [ ] Client không có certificate khi server yêu cầu → bị từ chối.
- [ ] Client certificate do CA ngoài danh sách tin cậy cấp → bị từ chối.
- [ ] Client cert không matching private key/alias hoặc sai EKU → bị từ chối.
- [ ] Sau handshake, principal mapping và authorization đúng tenant/resource.
- [ ] CA/cert rotation của client và server có giai đoạn chuyển đổi an toàn.

### Observability và vận hành

- [ ] Có phân loại lỗi network/TLS/PKIX/identity/app.
- [ ] JSSE debug flags chỉ bật trong môi trường được phép và có retention/redaction.
- [ ] Server/peer-side log có thể truy vết theo correlation ID.
- [ ] Container/runtime truststore/keystore được kiểm chứng tại đúng JDK.
- [ ] Không hardcode password trong JVM args hoặc log/CI secrets.
- [ ] Runbook có rollback và negative tests để phát hiện bypass.

## 18. Câu hỏi tự kiểm tra

**1. `SSLHandshakeException` có luôn là lỗi certificate không?**  
Không. Nó bao gồm nhiều lỗi handshake: protocol/cipher/group/signature mismatch, mTLS, PKIX, endpoint identity và những vấn đề khác.

**2. `Received fatal alert: handshake_failure` có đủ để kết luận client thiếu CA không?**  
Không. Đây là alert chung từ peer; cần xem peer logs và handshake stage/cause.

**3. Khi Java báo `PKIX path building failed`, nên import certificate từ server ngay vào truststore không?**  
Không. Phải xác minh chain/CA qua nguồn tin cậy rồi sửa truststore hoặc chain có kiểm soát; không tự tin dữ liệu mạng chưa xác thực.

**4. Truststore và keystore khác nhau thế nào trong HTTPS client bình thường?**  
Truststore cung cấp CA để xác minh **server**. Keystore cung cấp private key/certificate của **client** nếu dùng mTLS.

**5. Nếu client chỉ TLS 1.3 và server chỉ TLS 1.2, certificate hợp lệ có giúp handshake không?**  
Không. Không có protocol version chung; phải sửa config/upgrade theo policy trước khi tới certificate authentication.

**6. Vì sao TLS 1.3 có chung suite `TLS_AES_128_GCM_SHA256` mà vẫn fail?**  
Vì authentication/signature schemes, supported groups/key shares, certificate/key type, SNI/ALPN và policy là những yêu cầu riêng.

**7. Một Java client log `Received fatal alert: bad_certificate` trong mTLS thì phía nào nên kiểm tra?**  
Cả hai, ưu tiên server/gateway nơi phát alert vì có thể server từ chối client certificate; xem client keymanager và server trustmanager.

**8. `SSLSession.getProtocol()` trả TLSv1.3 có chứng minh `CertificateVerify` đã thành công trong mọi resumed handshake không?**  
Không. TLS 1.3 có PSK resumption và không phải handshake nào cũng lặp certificate authentication; phải hiểu session/handshake mode. Đây cũng không chứng minh application authorization.

**9. Chạy `keytool -printcert -sslserver ...` ra certificate có nghĩa đã tin root chưa?**  
Không. Tool hiển thị certificate; trust validation/hostname matching là bước bảo mật khác.

**10. Nên bật debug `javax.net.debug` đến mức nào?**  
Bắt đầu `ssl,handshake,trustmanager,keymanager` theo lỗi; `java.security.debug=certpath` khi cần. Không bật data/plaintext/packet dump bừa bãi trên production.

**11. `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` dùng làm gì?**  
Yêu cầu endpoint identity/hostname checking khi JSSE handshake trong context phù hợp, không thay certificate path validation hoặc một network handshake hoàn chỉnh.

**12. Vì sao một truststore khác nhau giữa browser và Java container gây kết quả khác nhau?**  
Vì browser và JVM có thể dùng CA stores, proxy/TLS interception và security policy khác; browser success không tự chứng minh Java trust anchor đúng.

**13. Sau khi “fix” bằng cách bỏ HostnameVerifier, mọi request pass. Có hoàn thành nhiệm vụ không?**  
Không. Đây là security regression: client có thể chấp nhận cert của endpoint sai. Phải sửa SAN/SNI/trust configuration và kiểm thử âm.

**14. Nếu Java đã hoàn tất TLS rồi server trả HTTP 403, sửa `SSLHandshakeException` có đúng hướng không?**  
Không. HTTP 403 là application authorization/identity policy, phải debug ở layer đó.

**15. Khi client không có client certificate nhưng server yêu cầu mTLS, sửa bên nào?**  
Client cần PrivateKeyEntry/certificate chain phù hợp; server cần trust client issuer theo policy. Không thay bằng trust-all hoặc tắt mTLS.

**16. Vì sao cần biết TLS termination point?**  
Vì handshake/certificate được kiểm tra tại đúng hop (client↔gateway hay gateway↔backend); sửa keystore ở backend không giúp client↔gateway nếu TLS terminate ở gateway.

## 19. Sơ đồ quyết định cuối bài

```text
Connection failed
      |
      v
DNS/TCP success?
  |             |
 NO             YES
  |              |
Network/route   TLS handshake begins
                 |
                 v
          ClientHello/ServerHello overlap?
             |                 |
            NO                 YES
             |                  |
       version/cipher/          certificate/auth phase
       group/signature                 |
       ALPN/SNI policy                 v
                              PKIX certificate path valid?
                               |                 |
                              NO                 YES
                               |                  |
                           CA/chain/              expected hostname valid?
                           validity/               |                |
                           revocation/            NO                YES
                           algorithm               |                 |
                                                SAN/SNI/          mTLS + Finished?
                                                identity          |          |
                                                                 NO          YES
                                                                  |           |
                                                             keymanager/     HTTP/application
                                                             trustmanager    authorization

  At every failure:
  - Read cause chain and first meaningful fatal event
  - Check BOTH endpoints' logs + actual TLS termination hop
  - Preserve strong trust, hostname verification, algorithm constraints
  - Make a scoped fix and re-run negative tests
```

**Kết luận:** Chẩn đoán `SSLHandshakeException` phải phân biệt **certificate validation**, **đúng hostname/peer identity**, **mTLS client authentication**, và **khả năng thương lượng chung** về protocol, cipher suites, signature schemes, groups và ALPN. JSSE debug, certpath trace, `keytool` và `SSLParameters` giúp tìm gốc lỗi, nhưng chỉ có ý nghĩa khi xác định **đúng runtime và đúng TLS termination point**. Hãy sửa certificate/CA/config cho phù hợp chính sách thay vì vô hiệu trust hoặc downgrade. Một “fix” chỉ hoàn thành khi **handshake hợp lệ thành công và handshake sai vẫn bị từ chối**.

### Tài liệu tham khảo

- Oracle Java SE 17 — *Java Secure Socket Extension (JSSE) Reference Guide*, mục troubleshooting, certificate exceptions và debugging: https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html
- Java SE 17 `SSLHandshakeException`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLHandshakeException.html
- Java SE 17 `SSLEngine`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLEngine.html
- Java SE 17 `SSLParameters`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html
- Java SE 17 `SSLContext`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html
- Java SE 17 `SSLSocket`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSocket.html
- Java SE 17 `X509TrustManager`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/X509TrustManager.html
- Java SE 17 `CertPathValidatorException`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidatorException.html
- Java SE 17 `KeyStore`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html
- Oracle Java 17 — *keytool* command reference: https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html
- RFC 5280 — X.509 certificate path validation: https://www.rfc-editor.org/rfc/rfc5280
- RFC 9846 — TLS 1.3 specification current in 2026: https://www.rfc-editor.org/rfc/rfc9846


</details>

- [Quay lại đầu trang](#back-to-top)
