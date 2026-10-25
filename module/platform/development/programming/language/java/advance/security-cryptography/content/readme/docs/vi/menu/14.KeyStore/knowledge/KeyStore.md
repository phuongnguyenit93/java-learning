<a id="back-to-top"></a>

# Quản lý khóa với KeyStore

## Menu
- [Mô hình KeyStore](#keystore-model)
- [Các loại entry trong KeyStore](#keystore-entry-types)
- [Nạp, lưu và bảo vệ KeyStore](#keystore-protection)
- [Keystore và truststore khác nhau ở vai trò nào?](#keystore-vs-truststore)

## <a id="keystore-model">Mô hình KeyStore</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

KeyStore là abstraction của Java để lưu/truy xuất **key entry và certificate entry** qua một store type/provider. Nó không chỉ đồng nghĩa với một file .jks.

~~~java
KeyStore keyStore = KeyStore.getInstance("PKCS12");
try (InputStream in = Files.newInputStream(path)) {
    keyStore.load(in, storePassword);
}
~~~

Store có thể là file-based hoặc được provider ánh xạ tới nguồn khác. Ứng dụng nên phụ thuộc vào KeyStore contract và triển khai configuration hơn là giả định mọi môi trường dùng cùng format.

KeyStore giúp gom key/certificate material vào một API quản lý, nhưng không tự giải quyết chính sách xoay vòng khóa, kiểm soát truy cập của filesystem/HSM hay việc phân phối secret.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="keystore-entry-types">Các loại entry trong KeyStore</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Ba loại entry quan trọng:

- **PrivateKeyEntry**: private key kèm certificate chain tương ứng;
- **SecretKeyEntry**: symmetric secret key;
- **TrustedCertificateEntry**: certificate được lưu như trust material, không kèm private key.

Phân biệt entry type giúp tránh lỗi “có alias nên chắc lấy được private key”. Cùng KeyStore có thể chứa nhiều role khác nhau.

~~~java
KeyStore.Entry entry = keyStore.getEntry(
        alias,
        new KeyStore.PasswordProtection(entryPassword)
);
~~~

Code nên kiểm tra expected entry type và fail rõ nếu triển khai cung cấp alias sai. Không cast mù rồi biến misconfiguration thành ClassCastException khó hiểu.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="keystore-protection">Nạp, lưu và bảo vệ KeyStore</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

KeyStore có hai lớp password concept thường bị nhầm:

- **store password** dùng khi load/store một số store format;
- **entry protection** có thể bảo vệ private/secret key entry riêng.

Không nên mặc định chúng luôn giống nhau hoặc luôn tồn tại theo cùng semantics ở mọi provider.

Khi ghi store ra file, bảo vệ file permissions, backup và triển khai secret vẫn là trách nhiệm hệ thống. Password hard-code trong source code chỉ chuyển secret từ “keystore” sang một nơi yếu hơn.

Với môi trường thực tế, KeyStore API có thể là boundary để kết nối PKCS#11/HSM provider, nơi private key không nhất thiết export ra ứng dụng memory.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="keystore-vs-truststore">Keystore và truststore khác nhau ở vai trò nào?</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

**Keystore** và **truststore** thường là tên theo **vai trò sử dụng**, không phải hai Java type hoặc hai format bắt buộc khác nhau.

- local identity store thường chứa PrivateKeyEntry + certificate chain;
- trust store thường chứa TrustedCertificateEntry đại diện trust anchors/accepted issuers.

Cả hai có thể cùng là PKCS12 KeyStore instance. JSSE KeyManager đọc local credentials; TrustManager đọc trust material.

Tách file/store riêng thường giúp operations và least privilege rõ hơn, nhưng đó là triển khai design. Đừng suy từ extension file rằng content chắc chắn có role nào; hãy inspect entry types và configuration.
KeyStore cho biết material được lưu ở đâu và theo entry role nào; để quyết định một public key/certificate có đáng tin hay không, cần chuyển sang certificate path và trust validation.

</details>

- [Quay lại đầu trang](#back-to-top)
