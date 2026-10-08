<a id="back-to-top"></a>

# SSL bundles và cấu hình TLS runtime

## Menu
- [Vì sao Boot cung cấp SSL bundle có tên?](#ssl-bundle-purpose)
- [Cấu hình bundle JKS/PKCS12 và PEM như thế nào?](#ssl-bundle-jks-pem)
- [Danh mục `SslBundles` được auto-configure cung cấp điều gì?](#sslbundles-catalog)
- [Một `SslBundle` có thể cung cấp những thành phần runtime nào?](#sslbundle-material)
- [Các thành phần runtime được hỗ trợ dùng lại bundle có tên như thế nào?](#ssl-bundle-consumers)
- [Trách nhiệm SSL bundle tổng quát bàn giao sang TLS của web server ở đâu?](#ssl-web-runtime-handoff)
- [Phần nào vẫn thuộc hạ tầng TLS và PKI thay vì Boot runtime?](#ssl-pki-boundary)

## <a id="ssl-bundle-purpose">Vì sao Boot cung cấp SSL bundle có tên?</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng thường cần dùng cùng trust/key material cho nhiều kết nối bảo mật. Nếu không có abstraction dùng lại được, mỗi tích hợp client/server có thể tự khai báo đường dẫn keystore, mật khẩu, certificate và tùy chọn protocol riêng.

SSL bundle của Spring Boot đặt material đó dưới một tên trong `spring.ssl.bundle`. Thành phần runtime được hỗ trợ có thể tham chiếu bundle theo tên thay vì lặp lại source material. Bundle trở thành ranh giới do Boot quản lý giữa cấu hình và thành phần cần các đối tượng SSL.

Chương này tập trung vào abstraction runtime đó. Việc cấp certificate, thiết kế trust PKI, bảo mật cipher/protocol và xoay vòng key thuộc phần kiến thức security/network; ở đây người học chỉ cần hiểu Boot đóng gói cấu hình thành bundle dùng lại thế nào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ssl-bundle-jks-pem">Cấu hình bundle JKS/PKCS12 và PEM như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot 3.3 hỗ trợ bundle có tên từ định dạng Java keystore và PEM. Bundle JKS/PKCS12 nằm dưới `spring.ssl.bundle.jks.<name>`, còn PEM nằm dưới `spring.ssl.bundle.pem.<name>`.

```yaml
spring:
  ssl:
    bundle:
      pem:
        partner-api:
          truststore:
            certificate: classpath:partner-ca.crt
```

Với key material, bundle cũng có thể khai báo certificate và private key. Cấu hình JKS/PKCS12 dùng vị trí/mật khẩu phù hợp với định dạng keystore/truststore.

Lựa chọn thiết kế quan trọng là ranh giới và tên bundle. Ví dụ học không nên hard-code secret; credential thật phải đi qua externalized configuration và cách quản lý secret phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sslbundles-catalog">Danh mục `SslBundles` được auto-configure cung cấp điều gì?</a>

<details>
<summary>Xem chi tiết</summary>

Khi các bundle có tên đã được cấu hình, Boot auto-configure một `SslBundles` bean. Nó hoạt động như danh mục runtime: ứng dụng hoặc code tích hợp lấy bundle theo tên mà không cần biết trust/key material ban đầu được biểu diễn bằng PEM hay Java keystore.

```java
SslBundle bundle = sslBundles.getBundle("partner-api");
SSLContext sslContext = bundle.createSslContext();
```

Sự tách lớp giúp thành phần sử dụng phụ thuộc vào SSL abstraction, còn cấu hình sở hữu material. Thay cách biểu diễn được Boot hỗ trợ không buộc mọi thành phần sử dụng tự tạo hợp đồng mới.

Danh mục này không phải certificate authority hay secret store; nó cung cấp cấu hình SSL đã có trong môi trường ứng dụng dưới dạng đối tượng runtime có cấu trúc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sslbundle-material">Một `SslBundle` có thể cung cấp những thành phần runtime nào?</a>

<details>
<summary>Xem chi tiết</summary>

`SslBundle` cung cấp SSL material theo nhiều lớp để thành phần sử dụng lấy đúng mức abstraction cần thiết. `getStores()` cho truy cập key/trust stores, `getManagers()` cho key/trust manager factories và managers, còn `createSslContext()` tạo `SSLContext`. Bundle cũng mang protocol/options và chi tiết key liên quan.

API theo lớp giúp thành phần sử dụng không phải tự dựng đối tượng cấp thấp hơn từ đường dẫn file. Thư viện chỉ cần `SSLContext` nên dùng method cấp cao; tích hợp cần manager factory mới đi xuống lớp đó.

Không nên hạ abstraction thấp hơn nếu thành phần sử dụng không yêu cầu, vì mỗi bước làm ứng dụng sở hữu thêm chi tiết TLS và tiến gần trách nhiệm JSSE/TLS tổng quát ngoài module này.

### Tài liệu tham khảo

- [Spring Boot 3.3 — SSL](https://docs.spring.io/spring-boot/3.3/reference/features/ssl.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ssl-bundle-consumers">Các thành phần runtime được hỗ trợ dùng lại bundle có tên như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Bundle có tên có giá trị khi tích hợp Boot được hỗ trợ cho phép tham chiếu theo tên hoặc code ứng dụng lấy bundle từ `SslBundles`. Nhờ đó một định nghĩa có thể phục vụ nhiều thành phần runtime mà không lặp trust/key properties.

Hỗ trợ phía thành phần sử dụng vẫn phải tường minh. Thành phần phải hiểu tích hợp bundle của Boot hoặc chấp nhận đối tượng SSL có thể tạo từ bundle. Bundle tồn tại không đồng nghĩa mọi client bên thứ ba trên classpath tự động được cấu hình.

Khi tích hợp thư viện, trước tiên kiểm tra Boot auto-configuration của công nghệ đó có property nhận tên bundle hay không. Nếu không, dùng SSL hook chính thức của thư viện và chỉ chuyển từ `SslBundle` sang cấp đối tượng mà thư viện thực sự hỗ trợ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ssl-web-runtime-handoff">Trách nhiệm SSL bundle tổng quát bàn giao sang TLS của web server ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Module này sở hữu việc tạo và dùng lại SSL bundle có tên ở mức tổng quát. Việc áp dụng bundle cụ thể cho embedded web server, chọn HTTPS port, cấu hình connector và phân tích hành vi TLS của server thuộc `web-runtime`.

```text
spring.ssl.bundle.*
  -> named SslBundle / danh mục SslBundles     [application-runtime]
  -> server.ssl.bundle=<name> và server TLS    [web-runtime]
```

Tách hai giai đoạn giúp client khác vẫn dùng cùng abstraction mà chương SSL không biến thành chương web server. Đồng thời tùy chọn riêng của server không bị hiểu nhầm thành ngữ nghĩa chung của bundle.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ssl-pki-boundary">Phần nào vẫn thuộc hạ tầng TLS và PKI thay vì Boot runtime?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot có thể nạp và cung cấp key/trust material đã cấu hình, nhưng Boot không quyết định PKI của tổ chức. Việc cấp/gia hạn certificate, thiết kế trust chain, chính sách kiểm tra hostname, lựa chọn protocol/cipher, xoay vòng key, HSM và threat modeling thuộc security/network.

Tương tự, biết bundle có truststore khác hoàn toàn với quyết định *CA nào nên được tin cậy*. Boot cung cấp cấu trúc cấu hình; miền security cung cấp chính sách.

Khi chẩn đoán SSL, hãy tách lỗi binding/tích hợp của Boot khỏi lỗi TLS handshake/trust. Tên bundle không tồn tại hoặc resource không đọc được là vấn đề Boot/cấu hình. Certificate chain sai hoặc hostname mismatch thuộc điều tra TLS/PKI.

</details>

- [Quay lại đầu trang](#back-to-top)
