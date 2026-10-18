<a id="back-to-top"></a>

# JSSE và bảo mật TLS

## Menu
- [Mô hình JSSE](#jsse-model)
- [SSLContext, KeyManager và TrustManager](#ssl-context-managers)
- [TLS handshake và thiết lập phiên bảo mật](#tls-handshake-security)
- [Xác minh danh tính endpoint](#endpoint-identification)
- [Protocol và algorithm constraints trong TLS](#tls-algorithm-constraints)

## <a id="jsse-model">Mô hình JSSE</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

JSSE (Java Secure Socket Extension) đưa TLS vào Java thông qua các API như SSLContext, SSLSocket, SSLEngine, KeyManager và TrustManager.

Trong module này, trọng tâm là **ngữ nghĩa bảo mật**, không phải lập trình socket.

~~~text
local key/certificate material
        ↓ KeyManager
SSLContext
        ↑ TrustManager
trusted CA / peer trust material
        ↓
TLS handshake
        ↓
authenticated secure session
~~~

TLS kết hợp nhiều primitive: đàm phán tham số, certificate authentication, key establishment và bảo vệ record bằng cơ chế symmetric. Vì vậy JSSE xuất hiện cuối module sau khi người học đã hiểu từng khối nền tảng.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ssl-context-managers">SSLContext, KeyManager và TrustManager</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

`SSLContext` là factory và nơi giữ trạng thái cấu hình cho phần triển khai giao thức bảo mật. Nó được khởi tạo bằng `KeyManager[]`, `TrustManager[]` và `SecureRandom`.

~~~java
SSLContext context = SSLContext.getInstance("TLS");
context.init(keyManagers, trustManagers, secureRandom);
~~~

**KeyManager** chọn thông tin xác thực cục bộ để trình bày với peer. **TrustManager** quyết định thông tin xác thực của remote peer có được tin cậy hay không.

Hai vai trò này không đơn giản là “một cái cho client, một cái cho server”. Với mutual TLS, cả hai phía đều có thể vừa trình bày danh tính vừa kiểm tra peer.

Với `null`, cần tách **contract của Java SE API** khỏi behavior mà Oracle JSSE guide mô tả cho JDK:

- ở mức `SSLContext.init(...)` API contract, nếu `KeyManager[]` hoặc `TrustManager[]` là `null`, runtime sẽ tìm implementation ưu tiên cao của factory tương ứng từ các security provider đã cài đặt; `SecureRandom == null` cho phép dùng implementation mặc định;
- trong Oracle JSSE Reference Guide cho JDK, `KeyManager[] == null` được mô tả là tạo một empty `KeyManager` cho context, còn `TrustManager[] == null` dùng default `TrustManagerFactory` để lấy `TrustManager` thích hợp.

Vì vậy không nên suy một quy tắc “mọi `null` đều giống nhau”, cũng không được hiểu `null` là vô hiệu hóa certificate/trust validation. Khi code phụ thuộc vào default resolution, hãy kiểm tra behavior của provider/JDK đang triển khai.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tls-handshake-security">TLS handshake và thiết lập phiên bảo mật</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

TLS handshake thiết lập security context trước khi dữ liệu ứng dụng được bảo vệ. Ở mức mô hình tư duy, handshake cần:

- thỏa thuận protocol/cipher capabilities;
- xác thực peer theo chế độ đã cấu hình;
- thực hiện key establishment;
- dẫn xuất session traffic keys;
- xác nhận hai phía có transcript/state nhất quán.

Sau đó record layer dùng bảo vệ symmetric có xác thực cho lưu lượng.

Ứng dụng không nên tự lấy “TLS key” ra rồi tái sử dụng cho mục đích khác. TLS protocol sở hữu key schedule và context binding.

Lỗi handshake thường là tín hiệu về bảo mật hoặc cấu hình: certificate không được tin cậy, hostname sai, algorithm bị vô hiệu hóa, protocol không tương thích hoặc key material không phù hợp.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="endpoint-identification">Xác minh danh tính endpoint</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Certificate path được tin cậy vẫn chưa đủ; client còn phải kiểm tra **endpoint identity**.

`SSLParameters` cho phép cấu hình endpoint identification algorithm trong các API JSSE thích hợp. Ví dụ với một `SSLSocket` client dùng HTTPS-style hostname verification:

~~~java
SSLParameters parameters = socket.getSSLParameters();
parameters.setEndpointIdentificationAlgorithm("HTTPS");
socket.setSSLParameters(parameters);
~~~

Khi algorithm khác `null`/rỗng, endpoint identification phải được xử lý trong TLS handshake. HTTP client stack cấp cao thường cấu hình hostname verification theo quy tắc HTTPS cho trường hợp sử dụng của nó.

SNI và endpoint verification có liên quan nhưng không giống nhau: SNI giúp client nói server name mong muốn trong handshake; verification kiểm tra identity mà peer certificate trình bày có khớp target hay không.

Không dùng TrustManager “trust all” hoặc tắt hostname verification làm workaround môi trường thực tế. Nếu dev/test dùng certificate tự ký, hãy tạo trust material test rõ ràng thay vì vô hiệu hóa security check toàn cục.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tls-algorithm-constraints">Protocol và algorithm constraints trong TLS</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Hành vi TLS bị ảnh hưởng bởi nhiều lớp chính sách:

- protocol versions/cipher suites được enable trên SSLParameters;
- provider support;
- JDK security properties như disabled algorithms;
- certificate/key constraints;
- peer capabilities.

Do đó “set cipher suite X” không đảm bảo handshake sẽ dùng được X nếu chính sách/provider/peer không cho phép.

Khi nâng cấp JDK, một handshake cũ có thể bắt đầu thất bại vì algorithm/key size bị deprecate hoặc disable. Cách xử lý đúng là cập nhật certificate, cấu hình hoặc protocol theo chính sách, không tự động nới lỏng constraint cho toàn JVM nếu chưa đánh giá ảnh hưởng.

Việc security defaults thay đổi là một cơ chế bảo vệ hệ sinh thái, nhưng quá trình triển khai cần kiểm thử tương thích trước khi nâng cấp.
JSSE/TLS cho thấy nhiều primitive và trust mechanism phối hợp trong một protocol; chapter cuối sẽ tổng hợp các lỗi thiết kế thường gặp và checklist review end-to-end.

</details>

- [Quay lại đầu trang](#back-to-top)
