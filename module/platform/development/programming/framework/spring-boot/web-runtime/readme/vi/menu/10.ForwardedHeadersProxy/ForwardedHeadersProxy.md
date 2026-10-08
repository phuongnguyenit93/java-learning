<a id="back-to-top"></a>

# Forwarded headers và triển khai sau reverse proxy

## Menu
- [Vì sao triển khai sau proxy làm thay đổi request metadata?](#proxy-request-metadata)
- [Các chiến lược NONE, NATIVE và FRAMEWORK](#forward-header-strategies)
- [Khi cơ chế forwarded headers native của server đã đủ](#native-forward-headers)
- [Khi nào cần cơ chế forwarded headers của Spring Framework?](#framework-forward-headers)
- [Mặc định của Boot trên các nền tảng đám mây được hỗ trợ](#cloud-forward-header-defaults)
- [TLS termination, redirect và nhận biết scheme bên ngoài](#proxy-tls-termination)
- [Thiết lập proxy và remote IP riêng theo server](#server-specific-proxy-settings)
- [Forwarded headers và ranh giới tin cậy với proxy](#forwarded-header-trust-boundary)

## <a id="proxy-request-metadata">Vì sao triển khai sau proxy làm thay đổi request metadata?</a>

<details>
<summary>Xem chi tiết</summary>
Khi ứng dụng chạy sau reverse proxy, kết nối mà embedded server nhìn thấy có thể khác request công khai bên ngoài. Ứng dụng có thể nhận lưu lượng tại `10.0.0.5:8080` qua HTTP trong khi máy khách thật sự truy cập `https://example.org` trên cổng `443`. Nếu bỏ qua khác biệt này, redirect, liên kết được sinh ra, kiểm tra scheme và thông tin địa chỉ máy khách có thể sai.

Forwarded headers mang một phần thông tin của request ban đầu qua chặng proxy. Trách nhiệm của Boot là chọn cách ứng dụng/server tiêu thụ metadata đó; định tuyến proxy và việc header được tạo như thế nào thuộc hạ tầng.

### Tài liệu tham khảo

- [Spring Boot 3.3 How-to — Running Behind a Front-end Proxy Server](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html#howto.webserver.use-behind-a-proxy-server)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="forward-header-strategies">Các chiến lược NONE, NATIVE và FRAMEWORK</a>

<details>
<summary>Xem chi tiết</summary>
`server.forward-headers-strategy` chọn chế độ xử lý của Boot. `NONE` không bật xử lý forwarded header. `NATIVE` giao việc xử lý cho cơ chế native của embedded server. `FRAMEWORK` dùng hỗ trợ của Spring Framework trong stack ứng dụng.

Chọn chế độ theo hợp đồng triển khai. Nếu proxy cung cấp các header thông dụng và server xử lý đúng yêu cầu thì `NATIVE` giữ logic gần server. Nếu ứng dụng cần mô hình biến đổi của Spring Framework thì chọn `FRAMEWORK`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-forward-headers">Khi cơ chế forwarded headers native của server đã đủ</a>

<details>
<summary>Xem chi tiết</summary>
Spring Boot 3.3 hướng dẫn rằng `NATIVE` thường đủ khi proxy cung cấp `X-Forwarded-For` và `X-Forwarded-Proto` theo cách thông dụng và hỗ trợ native của server phù hợp môi trường triển khai.

Hành vi native phụ thuộc server. Tên header, quy tắc proxy tin cậy, việc viết lại địa chỉ remote và các chi tiết khác có thể khác nhau, vì vậy cần xem tài liệu của server khi chiến lược Boot chung đã đúng nhưng kết quả native vẫn cần tinh chỉnh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="framework-forward-headers">Khi nào cần cơ chế forwarded headers của Spring Framework?</a>

<details>
<summary>Xem chi tiết</summary>
Khi cơ chế xử lý native của server chưa đủ, `FRAMEWORK` kích hoạt khả năng xử lý forwarded header của Spring Framework: `ForwardedHeaderFilter` cho ứng dụng Servlet và `ForwardedHeaderTransformer` cho ứng dụng Reactive.

Boot chịu trách nhiệm cho quyết định chọn chiến lược này. Hành vi lọc/biến đổi chi tiết thuộc Spring Framework. Việc đổi chiến lược có thể làm metadata request mà ứng dụng nhìn thấy thay đổi dù kết nối socket thực tế tới embedded server không đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cloud-forward-header-defaults">Mặc định của Boot trên các nền tảng đám mây được hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>
Trong Boot 3.3, `server.forward-headers-strategy` mặc định là `NATIVE` khi ứng dụng chạy trên nền tảng đám mây được hỗ trợ. Ở các môi trường còn lại, mặc định là `NONE`.

Không nên sao chép giả định của môi trường đám mây sang môi trường triển khai khác. Cấu hình an toàn phụ thuộc việc toàn bộ lưu lượng trực tiếp có thực sự đi qua proxy tin cậy, và proxy đó có làm sạch/cung cấp đúng forwarded header hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-tls-termination">TLS termination, redirect và nhận biết scheme bên ngoài</a>

<details>
<summary>Xem chi tiết</summary>
TLS thường kết thúc tại proxy, để lại một chặng HTTP từ proxy tới ứng dụng. Kết nối cục bộ của server khi đó có vẻ không được mã hóa dù request phía máy khách là HTTPS. Cơ chế xử lý giao thức chuyển tiếp đúng giúp metadata của request mà ứng dụng nhìn thấy vẫn giữ scheme `https` bên ngoài.

Điều này ảnh hưởng redirect và URL tuyệt đối được sinh ra. Với Tomcat, tài liệu Boot nêu rõ `server.tomcat.redirect-context-root=false` khi SSL kết thúc ở proxy để `X-Forwarded-Proto` được xem xét trước khi context-root redirect được tạo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="server-specific-proxy-settings">Thiết lập proxy và remote IP riêng theo server</a>

<details>
<summary>Xem chi tiết</summary>
Khi chiến lược chung chưa đủ, namespace riêng theo server cung cấp quyền kiểm soát sâu hơn. Ví dụ Tomcat cho phép đổi tên forwarded header và mẫu proxy nội bộ tin cậy dưới `server.tomcat.remoteip.*`.

Chỉ nên dùng các thiết lập này khi hợp đồng triển khai cần chúng. Chúng làm cấu hình phụ thuộc vào một server cụ thể và không phải hành vi di động giữa các server của `server.*`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="forwarded-header-trust-boundary">Forwarded headers và ranh giới tin cậy với proxy</a>

<details>
<summary>Xem chi tiết</summary>
Forwarded headers chỉ đáng tin khi proxy tin cậy kiểm soát chúng và máy khách không đáng tin không thể đi vòng qua proxy để gửi trực tiếp vào ứng dụng. Nếu không, máy khách có thể tự gửi metadata chuyển tiếp và làm ứng dụng tin sai scheme, host hoặc địa chỉ remote.

Vì vậy tài liệu Boot khuyến nghị chỉ bật hỗ trợ forwarded header khi lưu lượng đến từ HTTP proxy hoặc mạng đáng tin cậy. Mô hình tin cậy proxy đầy đủ thuộc hạ tầng/bảo mật, nhưng cấu hình Boot phải giữ được ranh giới này thay vì xem mọi forwarding header đầu vào là nguồn có thẩm quyền.

</details>

- [Quay lại đầu trang](#back-to-top)
