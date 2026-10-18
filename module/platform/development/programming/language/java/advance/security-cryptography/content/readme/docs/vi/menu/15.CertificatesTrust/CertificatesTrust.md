<a id="back-to-top"></a>

# Chứng thư, certificate path và mô hình tin cậy

## Menu
- [Mô hình chứng thư X.509](#x509-certificate-model)
- [Certificate chain và certification path](#certification-path)
- [Trust anchor và path validation](#trust-anchor-validation)
- [Kiểm tra thu hồi chứng thư](#certificate-revocation)
- [Trust validation và identity verification](#trust-vs-identity)

## <a id="x509-certificate-model">Mô hình chứng thư X.509</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

X.509 certificate gắn **public key** với một subject/identity claim và metadata như issuer, validity period, extensions. Certificate được issuer ký để verifier có thể kiểm tra binding đó theo trust model.

Certificate không chứa private key. Private key thường nằm ở PrivateKeyEntry riêng; certificate có thể phân phối công khai.

Trong Java, X509Certificate cung cấp API đọc subject/issuer, validity, public key, key usage, SAN và extensions.

Điểm quan trọng: “certificate parse được” không có nghĩa “certificate trusted”. Parsing chỉ tạo object; trust cần certification-path validation với trust anchors và chính sách.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="certification-path">Certificate chain và certification path</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Một leaf certificate thường không được trust trực tiếp. Nó tham gia **certification path** dẫn dần về phía một trust anchor:

~~~text
leaf certificate
    ↓ signed by
intermediate CA
    ↓ issued toward
certificate issued by the trust anchor

trust anchor
    = trusted input được cấu hình riêng
    ≠ certificate nằm trong CertPath
~~~

Peer thường gửi leaf + intermediate chain cần thiết; trust anchor thường đến từ local trust configuration và không nhất thiết phải được peer gửi.

`CertificateFactory`/`CertPath` mô hình hóa certificate/path data; `CertPathBuilder` có thể xây path từ available certificates theo thuật toán/chính sách. Theo convention của Java X.509 `CertPath`, path bắt đầu từ target certificate và kết thúc ở certificate **được trust anchor cấp**, còn certificate đại diện chính trust anchor không được đưa vào `CertPath`.

“Có chain” chưa phải validation. Verifier còn phải kiểm signature, validity, constraints, usage và trust anchor theo PKIX rules.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="trust-anchor-validation">Trust anchor và path validation</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

CertPathValidator thực hiện validation của certification path theo algorithm như PKIX.

~~~java
CertPathValidator validator = CertPathValidator.getInstance("PKIX");
PKIXParameters params = new PKIXParameters(trustAnchors);
params.setRevocationEnabled(true);

CertPathValidatorResult result =
        validator.validate(certPath, params);
~~~

Validation trả lời câu hỏi “path này có dẫn tới trust anchor được cấu hình và thỏa chính sách không?”. `TrustAnchor` là input riêng trong `PKIXParameters`; kết quả PKIX cũng trả trust anchor riêng với `CertPath`. Validation này không tự trả lời “hostname api.example.com có khớp certificate này không?”; endpoint identity là bước riêng.

Trust anchor là đầu vào chính sách cục bộ. Nếu kẻ tấn công có thể thay truststore và thêm CA của họ, cryptographic validation vẫn có thể pass theo chính sách đã bị thay đổi.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="certificate-revocation">Kiểm tra thu hồi chứng thư</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Certificate có thể còn trong validity period nhưng private key đã bị compromise hoặc CA đã revoke certificate. Revocation checking giúp verifier xem trạng thái đó qua cơ chế như CRL/OCSP tùy PKIX configuration/provider.

Java CertPathValidator có thể cung cấp PKIXRevocationChecker để cấu hình option chi tiết.

Revocation có operational trade-off: network availability, cache, chính sách soft-fail/hard-fail và freshness đều ảnh hưởng hành vi. Không có một setting phù hợp cho mọi hệ thống.

Quan trọng là **quyết định chính sách xử lý lỗi có chủ đích**. Tắt revocation chỉ để hết lỗi certificate có thể biến incident signal thành trust bypass.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="trust-vs-identity">Trust validation và identity verification</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Hai kiểm tra khác nhau:

1. **trust validation**: certificate/path có được trust theo CA/chính sách trust anchor không;
2. **identity verification**: certificate có đại diện đúng endpoint/hostname mà ứng dụng định kết nối không.

Một certificate được CA tin cậy nhưng cấp cho attacker.example không nên được chấp nhận cho api.example.

TLS client thường cần cả hai. JSSE/HTTP layer có endpoint identification logic dựa trên hostname/SAN. Custom TrustManager trả true cho mọi certificate hoặc custom HostnameVerifier luôn true làm mất một phần security contract.

Trust nói “tôi tin issuer/path này”; identity nói “key này thuộc đúng peer tôi muốn nói chuyện”.
Khi key material, certificate path, trust và endpoint identity đã rõ, các mảnh này có thể được ghép lại trong secure-channel model của JSSE/TLS.

</details>

- [Quay lại đầu trang](#back-to-top)
