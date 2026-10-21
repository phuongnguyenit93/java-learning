<a id="back-to-top"></a>

# CORS và an toàn web

## Menu
- [Same-origin policy, origin và điều kiện phát sinh CORS](#cors-web-security-001)
- [Yêu cầu đơn giản, preflight OPTIONS và các header CORS](#cors-web-security-002)
- [@CrossOrigin, cấu hình toàn cục và cách kết hợp chính sách](#cors-web-security-003)
- [CorsFilter, allowed origins, credentials và hạn chế ký tự đại diện](#cors-web-security-004)
- [Phân biệt CORS, xác thực, phân quyền và CSRF](#cors-web-security-005)
- [Ranh giới trách nhiệm của Spring Web và Spring Security](#cors-web-security-006)
- [ForwardedHeaderFilter, header do proxy cung cấp và ranh giới tin cậy](#cors-web-security-007)
- [Kiểm soát chuyển hướng mở, nội dung tải lên và thông tin nhạy cảm trong log](#cors-web-security-008)

## <a id="cors-web-security-001">Same-origin policy, origin và điều kiện phát sinh CORS</a>

<details>
<summary>Xem chi tiết</summary>

# Spring MVC — Same-origin policy, origin và điều kiện phát sinh CORS

## 1. Mục tiêu và câu hỏi cốt lõi

Giả sử ứng dụng frontend chạy ở `http://localhost:5500` và backend Spring MVC chạy ở `http://localhost:8080`. Khi browser thực thi:

~~~javascript
const response = await fetch("http://localhost:8080/api/info");
const data = await response.json();
console.log(data);
~~~

hai URL có **cùng host `localhost` nhưng khác cổng**, vì vậy chúng có **khác origin**. Browser áp dụng chính sách cùng nguồn gốc (same-origin policy, SOP). Muốn frontend JavaScript đọc được kết quả từ backend, server cần trả phản hồi CORS phù hợp. Ngược lại, nếu HTML/frontend cũng được phục vụ từ `http://localhost:8080`, lời gọi tới `/api/info` là same-origin và không cần cấp quyền CORS riêng.

Sau bài học, bạn có thể:

- Định nghĩa chính xác **origin = scheme + hostname + port hiệu lực** và phân biệt same-origin, cross-origin, same-site và cross-site.
- Giải thích SOP bảo vệ **đọc/khả năng tương tác** với tài nguyên từ origin khác, chứ không phải tường lửa chặn mọi yêu cầu mạng hay mọi nhúng tài nguyên.
- Xác định **khi nào browser dùng cơ chế CORS**, khi nào request cross-origin cần **preflight OPTIONS**, và khi nào không cần preflight nhưng response vẫn phải được browser cho phép đọc.
- Hiểu `Origin`, `Access-Control-Allow-Origin`, `Access-Control-Allow-Methods`, `Access-Control-Allow-Headers`, `Access-Control-Allow-Credentials`, `Access-Control-Expose-Headers`, `Access-Control-Max-Age` và `Vary: Origin`.
- Phân biệt CORS với authentication, authorization, CSRF, cookie SameSite, CSP và server-to-server HTTP requests.
- Cấu hình Spring MVC bằng `WebMvcConfigurer#addCorsMappings` hoặc `@CrossOrigin`, tích hợp Spring Security theo đúng filter order.
- Chạy ứng dụng Java 17 / Spring Boot 3.5.0 và kiểm thử CORS bằng MockMvc, browser DevTools và curl.

**Phạm vi:** trình duyệt web hiện đại, Spring Framework 6.2 / Spring MVC (Servlet stack). CORS là chính sách được **trình duyệt thực thi** theo Fetch Standard; bản thân server vẫn có thể nhận một HTTP request ngay cả khi script bị từ chối quyền đọc response.

## 2. Origin thực chất là gì?

Trong bối cảnh HTTP(S), origin thường được biểu diễn bằng bộ ba:

~~~text
origin = (scheme, host, port)
~~~

Ví dụ:

~~~text
https://app.example.com:443
  │           │            │
scheme       host         effective port
~~~

Nếu không viết cổng, browser dùng cổng mặc định của scheme: HTTP thường là 80, HTTPS là 443. Vì vậy `https://app.example.com` và `https://app.example.com:443` có cùng origin.

**Path, query và fragment không nằm trong origin.** Hai URL này cùng origin:

~~~text
https://shop.example.com/products?page=1
https://shop.example.com/account#settings
~~~

Nhưng chỉ một trong ba thành phần scheme, host hoặc port khác là đủ tạo cross-origin.

### 2.1. Bảng so sánh origin

Chọn origin của trang nguồn: `https://shop.example.com`.

| URL đích | Same-origin? | Lý do |
| --- | --- | --- |
| `https://shop.example.com/products` | Có | Scheme, host, port giống nhau; path không quan trọng |
| `https://shop.example.com:443/api` | Có | 443 là cổng HTTPS mặc định |
| `https://shop.example.com?q=java` | Có | Query không đổi origin |
| `http://shop.example.com/api` | Không | Khác scheme: HTTP thay vì HTTPS |
| `https://api.example.com/data` | Không | Khác hostname/subdomain |
| `https://shop.example.com:8443/api` | Không | Khác port |
| `https://example.com/api` | Không | Host gốc khác `shop.example.com` |
| `https://shop.example.com.evil.test/api` | Không | Đây là host khác hoàn toàn |

Với môi trường development:

| Trang nguồn | API đích | Kết luận |
| --- | --- | --- |
| `http://localhost:3000` | `http://localhost:8080` | Cross-origin: khác port |
| `http://localhost:8080` | `http://localhost:8080/api` | Same-origin |
| `http://localhost:8080` | `http://127.0.0.1:8080` | Cross-origin: hostname khác |
| `https://localhost:8080` | `http://localhost:8080` | Cross-origin: scheme khác |

Lưu ý hostnames DNS trỏ tới cùng IP **không** khiến chúng tự trở thành cùng origin. SOP dựa vào origin của URL, không dựa trên IP đích sau DNS resolution.

### 2.2. Same-origin khác same-site

Hai thuật ngữ này thường bị nhầm:

- **Origin** thường xét `scheme + host + port`.
- **Site** hiện đại thường là **scheme + registrable domain** (schemeful same-site); cổng không phải tiêu chí phân biệt site. Registrable domain dựa trên quy tắc public suffix; với `localhost` có xử lý đặc thù trình duyệt.

Ví dụ `https://app.example.com` và `https://api.example.com` **cross-origin** (host khác) nhưng thường **same-site** (cùng scheme và registrable domain `example.com`). `http://localhost:5500` và `http://localhost:8080` cũng cross-origin do khác port nhưng thường được coi là same-site trong môi trường local.

**Hệ quả:** cấu hình cookie `SameSite` không tự xử lý bài toán CORS. Cookie SameSite kiểm soát gửi cookie theo quan hệ site; SOP/CORS kiểm soát quyền đọc response giữa origins. Một request có thể **cross-origin nhưng same-site**.

### 2.3. Opaque origin, file và Origin: null

Không phải mọi tài liệu có origin dạng HTTP(S) rõ ràng. Một số tài liệu sandboxed, `data:`, hoặc `file:` có thể có **opaque origin**. Khi chuyển thành chuỗi trong request `Origin`, browser có thể dùng:

~~~http
Origin: null
~~~

Đừng hiểu `null` là một domain tin cậy, cũng đừng whitelist nó chỉ để làm thử nghiệm local. Những tài liệu không tin cậy khác nhau cũng có thể biểu diễn origin thành `null`.

**Thực hành tốt:** chạy frontend development qua một HTTP server như `http://localhost:5500` thay vì mở file HTML trực tiếp bằng `file:///...`; khi đó origin và request dễ kiểm thử hơn.

`document.domain` từng được sử dụng để nới tương tác giữa subdomains, nhưng cơ chế đó đã bị loại bỏ/không được khuyến nghị; không dùng như một cách chữa lỗi CORS trong ứng dụng hiện đại.

## 3. Same-origin policy hạn chế điều gì?

SOP là cơ chế cô lập các nội dung từ những nguồn khác nhau. Ví dụ, người dùng đăng nhập webmail `https://mail.example.com`; một website bất kỳ `https://attacker.test` không được tự đọc thư, token hay nội dung API private của webmail chỉ bằng cách chạy JavaScript `fetch()`.

### 3.1. Đọc, gửi và nhúng là ba hành vi khác nhau

| Hoạt động | Mặc định theo SOP/browser | Ví dụ |
| --- | --- | --- |
| Script từ origin A đọc response JSON/HTML của origin B bằng `fetch`/XHR | Bị giới hạn, trừ khi server B cấp quyền CORS phù hợp | `fetch("https://api.example.com/private")` |
| Điều hướng browser sang một site khác | Thường được phép | Click link, `window.location` |
| HTML form gửi POST cross-origin | Nhiều trường hợp được phép gửi | `<form action="https://other.test/pay" method="post">` |
| Nhúng ảnh/audio/video cross-origin | Thường được phép hiển thị theo loại resource/chính sách khác | `<img src="https://cdn.test/p.png">` |
| Nhúng script truyền thống từ origin khác | Thường được phép tải/chạy theo loại script và CSP | `<script src="https://cdn.test/lib.js">` |
| Module script cross-origin hoặc JS đọc pixels canvas từ ảnh cross-origin | Có thể yêu cầu CORS hoặc bị taint | `<script type="module">`, `canvas.toDataURL()` |
| Truy cập DOM của một iframe cross-origin | Bị hạn chế chặt | Parent script đọc DOM từ `iframe` khác origin |
| Truy cập `localStorage`/IndexedDB của origin B từ origin A | Bị cô lập theo origin | `localStorage.getItem(...)` ở site khác |

Do đó, câu “SOP chặn mọi cross-origin request” là **sai**. Một số request **được gửi** nhưng JavaScript **không được đọc response** nếu CORS không cho phép.

Một `<img>` cross-origin có thể hiển thị được, nhưng khi vẽ ảnh đó lên canvas, browser có thể đánh dấu canvas là “tainted” và chặn đọc pixels trừ khi resource đáp ứng chính sách CORS.

### 3.2. Vì sao CSRF vẫn tồn tại?

Vì cross-origin form submissions hoặc các request nhất định vẫn có thể gửi, một website độc hại có thể cố gây ra thay đổi trên site khác nếu browser gửi cookie đăng nhập tương ứng. **CORS không tự chống CSRF** và không phải nơi duy nhất chặn request tạo thay đổi trạng thái.

Backend vẫn cần các biện pháp như:

- CSRF tokens thích hợp cho cookie/session-based authentication.
- Cookie `SameSite`, `Secure` và `HttpOnly` phù hợp.
- Kiểm tra `Origin`/`Referer` trong những tình huống cần thiết, theo chính sách xác định.
- Authentication, authorization, idempotency và kiểm tra nghiệp vụ.
- Không thực hiện thao tác thay đổi trạng thái chỉ bằng GET.

CORS là **quy tắc chia sẻ quyền truy cập response cho trình duyệt**, không phải hệ thống phân quyền của server.

## 4. Khi nào một request trở thành CORS request?

Trong bài này, tập trung vào một JavaScript `fetch()` từ origin của tài liệu A tới URL B:

1. Nếu A và B **same-origin**, không cần CORS để script đọc response.
2. Nếu A và B **cross-origin**, browser thực hiện các kiểm tra theo Fetch/CORS; server B cần opt-in với các HTTP response headers phù hợp để JS đọc response.
3. Tùy HTTP method, headers và Content-Type, browser có thể gửi **preflight OPTIONS** trước request chính.
4. Request có thể được gửi thành công nhưng **response vẫn bị browser chặn** nếu headers không hợp lệ.

Ví dụ frontend ở `http://localhost:5500`:

~~~javascript
// Cross-origin vì backend chạy ở port 8080.
const result = await fetch("http://localhost:8080/api/info");
console.log(await result.json());
~~~

Request thường mang header:

~~~http
GET /api/info HTTP/1.1
Origin: http://localhost:5500
Accept: */*
~~~

Server có thể trả:

~~~http
HTTP/1.1 200 OK
Content-Type: application/json
Access-Control-Allow-Origin: http://localhost:5500
Vary: Origin

{"service":"cors-demo","status":"ok"}
~~~

Nếu chính sách hợp lệ, browser cho JS truy cập JSON. Nếu không có `Access-Control-Allow-Origin` tương thích, browser chặn JavaScript đọc kết quả dù Network tab có thể ghi nhận HTTP 200.

**Origin header do user agent tạo trong CORS flow.** Một request server-to-server hoặc `curl` có thể gửi/không gửi header Origin và vẫn nhận HTTP response vì không có browser enforcement. Vì vậy không coi `Origin` là bằng chứng xác thực người gọi.

### 4.1. CORS không nhất thiết tạo một HTTP request mới

Hai khái niệm độc lập:

- **CORS actual request:** request chính như GET/POST/PUT/... tới API.
- **CORS preflight request:** OPTIONS gửi **trước actual request** để hỏi server có cho phép method/headers dự định hay không.

Một cross-origin GET thông thường **không có preflight**, nhưng actual response vẫn phải có ACAO phù hợp. Một POST JSON cross-origin thường cần preflight trước khi gửi JSON body.

## 5. Những điều kiện tạo preflight OPTIONS

Preflight không phát sinh chỉ vì “frontend và backend khác cổng”. Nó phụ thuộc tổ hợp method, request headers và một số điều kiện khác của Fetch. Cách học phổ biến là so với tập **CORS-safelisted**.

### 5.1. Những request thường không cần preflight

Một request cross-origin có thể không cần preflight khi đồng thời đáp ứng các điều kiện tương ứng, ví dụ:

- Method là `GET`, `HEAD` hoặc `POST`.
- Request headers do script tự đặt chỉ thuộc nhóm CORS-safelisted (như `Accept`, `Accept-Language`, `Content-Language`, và một số trường hợp `Content-Type`, `Range`), với giới hạn hợp lệ của giá trị/header.
- Nếu có `Content-Type` do script đặt, essence media type phù hợp trong ba loại: `application/x-www-form-urlencoded`, `multipart/form-data`, `text/plain`.
- Không có các yếu tố bổ sung buộc preflight theo Fetch (một số streaming/upload cases và nền tảng browser có chính sách bổ sung).

Ví dụ:

~~~javascript
// Cross-origin GET thường không preflight,
// nhưng vẫn cần Access-Control-Allow-Origin để JS đọc body.
fetch("http://localhost:8080/api/info");
~~~

Một POST form-urlencoded cũng có thể không preflight:

~~~javascript
fetch("http://localhost:8080/api/form", {
  method: "POST",
  headers: {
    "Content-Type": "application/x-www-form-urlencoded"
  },
  body: "name=Lan"
});
~~~

**Không có preflight không có nghĩa backend đã cấp quyền.** Browser vẫn kiểm tra actual CORS response, và request POST có thể đã đến server trước khi browser chặn JS đọc response.

### 5.2. Những request thường cần preflight

| Yêu cầu script tạo | Preflight thông thường? | Nguyên nhân |
| --- | --- | --- |
| GET không custom headers | Không | Safelisted method/headers |
| POST `Content-Type: application/x-www-form-urlencoded` | Không | Safelisted content type |
| POST `Content-Type: text/plain` | Không | Safelisted content type |
| POST `Content-Type: application/json` | **Có** | `application/json` không thuộc safelisted Content-Type |
| PUT/DELETE/PATCH | **Có** | Method ngoài GET/HEAD/POST |
| GET thêm `Authorization: Bearer ...` | **Có** | Authorization không phải safelisted header |
| GET thêm `X-Request-ID`, `X-Tenant-ID` hoặc `X-API-Key` | **Có** | Custom request headers |
| POST có header `Content-Type: application/json` và credentials | **Có** | JSON content type đã đủ yêu cầu preflight |

Một client thêm header tùy chỉnh chỉ để “debug” có thể vô tình tạo preflight cho request trước đó không cần. Nhưng không nên bỏ authentication headers cần thiết chỉ để tránh OPTIONS; phải cấu hình CORS/authorization đúng.

### 5.3. Luồng preflight thực tế cho JSON POST

Frontend:

~~~javascript
const res = await fetch("http://localhost:8080/api/echo", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ message: "Xin chào" })
});
console.log(await res.json());
~~~

Browser gửi trước:

~~~http
OPTIONS /api/echo HTTP/1.1
Origin: http://localhost:5500
Access-Control-Request-Method: POST
Access-Control-Request-Headers: content-type
~~~

Response cho phép có thể như sau:

~~~http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: http://localhost:5500
Access-Control-Allow-Methods: GET, POST
Access-Control-Allow-Headers: Content-Type
Access-Control-Max-Age: 1800
Vary: Origin
~~~

Sau preflight thành công, browser gửi request chính:

~~~http
POST /api/echo HTTP/1.1
Origin: http://localhost:5500
Content-Type: application/json

{"message":"Xin chào"}
~~~

Actual response cần có header CORS phù hợp:

~~~http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: http://localhost:5500
Content-Type: application/json

{"echo":"Xin chào"}
~~~

Từ góc nhìn ứng dụng MVC, preflight thông thường được xử lý ở tầng CORS khi handler mapping/config đã xác định, **không cần bạn tự viết OPTIONS controller** chỉ để trả ACAO. Preflight có thể bị từ chối khi origin, method, headers không được cấu hình.

**Lưu ý:** Status/headers cụ thể (200/204, thứ tự, capitalization) có thể khác theo implementation. Điều cần kiểm tra là **origin/method/headers chính sách cho phép** và browser có tiếp tục actual request hay không. Browser có thể cache kết quả preflight trong giới hạn riêng, nên không chắc mỗi POST JSON đều tạo OPTIONS mới.

### 5.4. CORS preflight và credentials

Theo Fetch, preflight CORS thông thường **không mang credentials của actual request** (ví dụ cookies). Vì vậy hệ thống dùng session authentication mà chặn mọi OPTIONS nếu thiếu cookie có thể làm preflight thất bại.

Đó là lý do khi tích hợp Spring Security, CORS processing thường phải xảy ra **trước authentication/authorization filters**. Không có nghĩa bạn nên mở mọi OPTIONS trên toàn internet một cách không điều kiện; vẫn áp dụng origin/method/header allowlist ở CORS layer.

## 6. CORS response headers giải nghĩa

| Header server trả | Tác dụng |
| --- | --- |
| `Access-Control-Allow-Origin` | Cho phép **một origin cụ thể** hoặc `*` trong những điều kiện được phép |
| `Access-Control-Allow-Methods` | Trong preflight response, liệt kê methods mà cross-origin script được phép dùng |
| `Access-Control-Allow-Headers` | Trong preflight response, cho phép headers do script dự định gửi |
| `Access-Control-Allow-Credentials` | Cho phép browser expose response cho credentialed requests khi hợp lệ |
| `Access-Control-Expose-Headers` | Cho JS đọc thêm response headers ngoài tập safelisted |
| `Access-Control-Max-Age` | Cho browser cache kết quả preflight, subject to browser limits |
| `Vary: Origin` | Báo cho caches response có thể khác theo request Origin |

### 6.1. Access-Control-Allow-Origin không phải danh sách CSV

Header này có thể là:

~~~http
Access-Control-Allow-Origin: https://app.example.com
~~~

hoặc với resource public không dùng credentials:

~~~http
Access-Control-Allow-Origin: *
~~~

**Không** ghi nhiều origins ngăn bởi dấu phẩy trong một giá trị ACAO để hy vọng browser chọn:

~~~http
Access-Control-Allow-Origin: https://a.example, https://b.example
~~~

Đó không phải định dạng danh sách origins hợp lệ cho ACAO. Nếu server cho phép nhiều origins, nó thường so `Origin` request với allowlist rồi phản chiếu **một origin đã được kiểm tra** phù hợp. Khi phản hồi khác theo origin, dùng `Vary: Origin` để tránh shared caches dùng nhầm response.

### 6.2. Credentials và wildcard

Nếu frontend dùng cookie/cross-origin credentials:

~~~javascript
fetch("https://api.example.com/account", {
  credentials: "include"
});
~~~

và backend muốn cho script đọc response, thường cần:

~~~http
Access-Control-Allow-Origin: https://app.example.com
Access-Control-Allow-Credentials: true
Vary: Origin
~~~

**Không** kết hợp credentialed CORS response với:

~~~http
Access-Control-Allow-Origin: *
Access-Control-Allow-Credentials: true
~~~

Browser sẽ không cho script đọc credentialed response trong trường hợp này. Với credentialed flow, cách dùng wildcard cho allowed methods/headers/exposed headers cũng có các hạn chế; an toàn nhất là khai báo tường minh allowlist theo nhu cầu thực.

Ghi nhớ rằng `credentials: "include"` không bảo đảm cookie thật sự được gửi. Browser còn áp dụng cookie domain/path, `Secure`, `SameSite` và **third-party cookie policies**. Với cross-site cookies, cấu hình `SameSite=None; Secure` thường là điều kiện cần (không đủ) trong bối cảnh HTTPS phù hợp.

### 6.3. Expose-Headers không tự expose mọi header

Browser cho JS đọc một số CORS-safelisted response headers; muốn đọc header tùy chỉnh, ví dụ `X-Request-ID`, server có thể trả:

~~~http
Access-Control-Expose-Headers: X-Request-ID
X-Request-ID: 32fd0d93-2d40-43fe-bdd0-7e672f210501
~~~

Frontend:

~~~javascript
const res = await fetch("http://localhost:8080/api/info");
const id = res.headers.get("X-Request-ID");
console.log(id);
~~~

Nếu CORS chỉ cho đọc body nhưng chưa expose custom response header, JavaScript có thể nhận `null` từ `headers.get` dù Network tab có thấy header đó.

### 6.4. Max-Age và cache

`Access-Control-Max-Age: 1800` là thời gian browser được phép cache **preflight result**, tính theo giây, subject to browser implementation cap. Nó **không phải** cache lifetime của actual JSON response.

Không dùng preflight cache như cơ chế phân quyền người dùng. Khi thay đổi CORS policy, có thể cần chờ các preflight caches trên browser hết hạn theo giới hạn của chúng. HTTP caches và browser CORS preflight caches là các cơ chế khác nhau.

## 7. Không có CORS header thì server có xử lý request không?

Một nhầm lẫn rất thường gặp:

> “Trong Chrome thấy lỗi CORS nên server chắc chắn không nhận được request.”

Không nhất thiết đúng.

### 7.1. Trường hợp GET không preflight

Browser có thể gửi GET, server trả 200, **nhưng browser chặn JavaScript đọc** vì response không có ACAO phù hợp. Network panel có thể thấy GET và payload, còn `fetch` ở script báo lỗi như `TypeError: Failed to fetch` hoặc console CORS message.

### 7.2. Trường hợp POST JSON có preflight

Browser gửi OPTIONS trước. Nếu OPTIONS không được cho phép, browser **không gửi actual POST**. Đây là nguyên nhân backend controller POST không hề thấy request.

### 7.3. CORS không giống status HTTP

| Tình huống | Server thực tế | JS quan sát |
| --- | --- | --- |
| GET cross-origin, HTTP 200, thiếu ACAO | Server xử lý GET thành công | JS không được đọc response |
| POST JSON, preflight bị 403 | Actual POST có thể không được gửi | Fetch bị chặn bởi CORS |
| GET có ACAO đúng, HTTP 404 JSON | Server báo resource không tồn tại | JS đọc được status 404/body vì CORS cho phép |
| GET có ACAO đúng, HTTP 500 JSON | Server lỗi thật | JS có thể đọc status 500/body nếu CORS cho phép |
| GET same-origin HTTP 401 | Không cần CORS | JS thấy HTTP 401 theo semantics |
| `curl` tới server không ACAO | curl vẫn đọc response | curl không thực thi SOP |

Tức là **CORS approval chỉ quyết định quyền script truy cập response**, không thay thế HTTP status, network connectivity, TLS, DNS, authentication hoặc server-side authorization.

## 8. CORS khác các cơ chế an toàn khác như thế nào?

### 8.1. CORS và CSRF

- **CORS:** browser có cho JavaScript ở origin A đọc response của origin B không?
- **CSRF:** một trang độc hại có thể khiến browser của người dùng thực hiện hành động đã xác thực tại server khác hay không?

Một request HTML form POST cross-site có thể được gửi **không cần server cấp phép CORS để script đọc kết quả**. Vì vậy CSRF token / SameSite cookie / kiểm tra Origin phù hợp vẫn cần thiết cho cookie-authenticated state changes.

### 8.2. CORS và authentication/authorization

CORS không chứng minh người gọi là ai. `Origin` không phải token đăng nhập, và công cụ ngoài browser có thể tự gửi giá trị Origin bất kỳ. Backend phải xác thực JWT/session/API key riêng, kiểm tra quyền trên từng resource và tenant.

Một origin được allow CORS nhưng user chưa đăng nhập có thể nhận HTTP 401; nếu CORS response đúng thì frontend **đọc được response 401** để hiển thị/login. Không vì thế mà cấp quyền xem resource.

### 8.3. CORS và CSP

**Content Security Policy (CSP)** — chẳng hạn `connect-src` — do **trang nguồn** công bố để giới hạn frontend được phép kết nối tới đâu. CORS chủ yếu là lựa chọn của **server đích** về origin nào được đọc response. Một request có thể bị CSP chặn trước khi network call hoặc bị CORS chặn sau khi call. Hai chính sách bổ sung, không thay thế nhau.

### 8.4. CORS và SOP cho cookies/storage/DOM

CORS không làm localStorage hay IndexedDB của origin B tự hiện ra cho script của origin A. Nó cũng không cho trang A đọc DOM của iframe cross-origin tùy ý. Cookie có domain/path/site policies riêng và credentials mode của fetch; không suy ra "cho phép CORS" đồng nghĩa "chia sẻ mọi storage".

### 8.5. Browser versus backend, Postman và curl

Server-to-server HTTP clients, command-line `curl`, Postman và nhiều mobile native clients không chịu SOP enforcement giống browser JavaScript. Dù bạn cấu hình CORS nghiêm ngặt, một attacker vẫn có thể gọi trực tiếp public API endpoint nếu mạng và authentication cho phép.

**CORS không phải firewall hay server authorization.** Đừng dùng allowedOrigins như “chỉ ứng dụng hợp lệ được gọi API” nếu không có authentication/authorization.

## 9. Spring MVC xử lý CORS ở đâu?

Spring MVC có hỗ trợ CORS trong `HandlerMapping`. Khi request được map tới handler:

- Với **preflight**, MVC có thể xử lý OPTIONS trực tiếp theo CORS configuration, không cần vào controller business method.
- Với actual cross-origin request, MVC kiểm tra origin/config và thêm headers CORS phù hợp nếu được cho phép.
- Nếu không có CORS configuration phù hợp, preflight có thể bị từ chối; simple/actual request không nhận headers CORS để browser đọc được response.

Spring hỗ trợ hai cách khai báo phổ biến:

1. **Cục bộ** với `@CrossOrigin` ở class hoặc method controller.
2. **Toàn cục có giới hạn URL paths** qua `WebMvcConfigurer#addCorsMappings`.

Bạn cũng có thể dùng `CorsFilter` khi cần xử lý CORS ở mức filter chain hoặc tích hợp với Spring Security. Chỉ nên chọn **một policy chủ đạo** và kiểm tra cách nó kết hợp với các rule cục bộ, tránh allow quá rộng hoặc trả headers xung đột.

### 9.1. @CrossOrigin cho một handler

Ví dụ ngắn:

~~~java
@RestController
@RequestMapping("/api")
public class ExampleController {

    @CrossOrigin(origins = "https://app.example.com")
    @GetMapping("/catalog")
    public List<String> catalog() {
        return List.of("Java", "Spring");
    }
}
~~~

Imports từ `org.springframework.web.bind.annotation` và `java.util.List`. Khi mục đích chỉ là một số endpoint, local annotation dễ nhìn. Tuy nhiên khi API có nhiều endpoints cùng chính sách, global configuration rõ và ít copy-paste hơn.

**Không nên** thêm `@CrossOrigin(origins = "*")` tùy tiện lên mọi controller để "lỗi biến mất". Điều đó có thể vô tình cho các trang web tùy ý đọc dữ liệu API không credentialed khi các lớp bảo vệ khác chưa được thiết kế đúng.

### 9.2. Global CORS với WebMvcConfigurer

~~~java
@Configuration
public class ApiCorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("https://app.example.com")
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "Authorization")
                .exposedHeaders("X-Request-ID")
                .allowCredentials(false)
                .maxAge(1800);
    }
}
~~~

Imports:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
~~~

Cấu hình này hạn chế **URL path**, origin, method, headers và thời gian preflight cache. Không đặt `@EnableWebMvc` trong ứng dụng Spring Boot nếu chỉ cần tùy chỉnh CORS, vì nó có thể thay thế các phần MVC auto-configuration hữu ích.

**Lưu ý kết hợp:** CORS configuration global và `@CrossOrigin` cục bộ có cơ chế combine của Spring. Nhiều thuộc tính dạng danh sách có thể được hợp nhất, làm phạm vi được phép **rộng hơn mong đợi**. Review cấu hình cuối và viết tests cho các origin được phép và bị cấm.

### 9.3. Khi có Spring Security

CORS preflight thông thường không mang cookie/session credentials của actual request. Nếu Spring Security yêu cầu xác thực ngay trước khi CORS được xử lý, OPTIONS có thể bị từ chối.

Trong ứng dụng Spring Security Servlet:

~~~java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
            .cors(Customizer.withDefaults())
            // Khai báo authentication, authorization và CSRF phù hợp ứng dụng.
            .build();
}
~~~

Fragment này **chưa đủ** thành cấu hình security production: cần dependency Spring Security và chính sách bảo vệ/authorization. `http.cors(...)` giúp Security tích hợp CORS configuration đúng thứ tự. Không dùng `permitAll OPTIONS /**` như một cách chữa chung thay vì cấu hình CORS chính xác, và không tắt CSRF vô điều kiện cho ứng dụng dựa cookie/session.

Trong hệ thống có gateway, hãy xác định **gateway hay Spring backend chịu trách nhiệm CORS headers**, tránh cả hai thêm ACAO khác nhau hoặc phản hồi preflight không nhất quán.

## 10. Dự án chạy được: Spring Boot 3.5 và frontend khác port

Demo sau tạo backend API ở `http://localhost:8080`, chỉ cho browser frontend từ `http://localhost:5500` đọc response cross-origin. Có endpoint GET không preflight và POST JSON có preflight. Frontend chỉ dùng JavaScript fetch, không có session/cookies trong demo.

### 10.1. Cấu trúc

~~~text
cors-origin-demo/
├── pom.xml
├── src/main/java/demo/CorsOriginApplication.java
├── src/test/java/demo/CorsOriginTest.java
└── web-client/index.html
~~~

### 10.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>cors-origin-demo</artifactId>
    <version>1.0.0</version>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 10.3. CorsOriginApplication.java

Tất cả đoạn mã dưới đây nằm trong **một file** `src/main/java/demo/CorsOriginApplication.java`. Chỉ class `CorsOriginApplication` là public; các record/controller/config còn lại là package-private, nên Java 17 cho phép đặt chung file.

~~~java
package demo;

import java.util.UUID;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class CorsOriginApplication {
    public static void main(String[] args) {
        SpringApplication.run(CorsOriginApplication.class, args);
    }
}

@Configuration
class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // Đúng origin frontend local: scheme + host + port.
                .allowedOrigins("http://localhost:5500")
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type")
                .exposedHeaders("X-Request-ID")
                .allowCredentials(false)
                .maxAge(1800);
    }
}

record InfoResponse(String service, String status) {
}

record EchoRequest(String message) {
}

record EchoResponse(String echo) {
}

@RestController
@RequestMapping("/api")
class ApiController {
    @GetMapping(path = "/info",
                produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<InfoResponse> info() {
        return ResponseEntity.ok()
                .header("X-Request-ID", UUID.randomUUID().toString())
                .body(new InfoResponse("cors-demo", "ok"));
    }

    @PostMapping(path = "/echo",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EchoResponse> echo(
            @RequestBody EchoRequest request) {
        return ResponseEntity.ok()
                .header("X-Request-ID", UUID.randomUUID().toString())
                .body(new EchoResponse("Echo: " + request.message()));
    }
}
~~~

**Điều ứng dụng minh họa:**

- `GET /api/info` từ origin `http://localhost:5500`: actual request không bắt buộc có preflight nếu headers safelisted.
- `POST /api/echo` với `Content-Type: application/json`: browser cần preflight OPTIONS.
- Browser chỉ được đọc response khi response headers CORS hợp lệ; `X-Request-ID` được expose để frontend JS đọc.
- `allowCredentials(false)`: ví dụ không hỗ trợ cross-origin cookie-authenticated requests. Nếu dùng cookies, chính sách phải được thiết kế lại, không chỉ đổi false thành true.
- Chỉ origin `http://localhost:5500` được cho phép trong demo; `http://localhost:5501` hoặc `http://127.0.0.1:5500` là origin khác.

Chương trình **không bật Spring Security**, không giả lập đăng nhập hay quyền truy cập. Đừng lấy cấu hình CORS này làm chính sách authentication production.

### 10.4. Frontend web-client/index.html

~~~html
<!doctype html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <title>CORS từ localhost:5500</title>
</head>
<body>
  <h1>Thử cross-origin fetch</h1>
  <button id="get">GET /api/info</button>
  <button id="post">POST JSON /api/echo</button>
  <pre id="output"></pre>
  <script>
    const api = "http://localhost:8080";
    const output = document.querySelector("#output");

    async function callApi(path, options) {
      try {
        const response = await fetch(api + path, options);
        const requestId = response.headers.get("X-Request-ID");
        const payload = await response.json();
        output.textContent = JSON.stringify({
          status: response.status,
          requestId,
          payload
        }, null, 2);
      } catch (error) {
        // Nếu là lỗi CORS, browser không cho JS đọc response body.
        output.textContent = "Fetch failed. Xem Network/Console: "
          + error.message;
      }
    }

    document.querySelector("#get").addEventListener("click", () => {
      callApi("/api/info");
    });

    document.querySelector("#post").addEventListener("click", () => {
      callApi("/api/echo", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message: "Xin chào" })
      });
    });
  </script>
</body>
</html>
~~~

### 10.5. Khởi động backend và frontend

Terminal 1, trong thư mục `cors-origin-demo`:

~~~bash
mvn spring-boot:run
~~~

Terminal 2, cũng trong thư mục project, dùng Python 3:

~~~bash
python -m http.server 5500 --directory web-client
~~~

Mở bằng browser:

~~~text
http://localhost:5500
~~~

Nhấn nút GET rồi mở DevTools/Network. Bạn sẽ thường thấy **GET /api/info** từ frontend tới backend và response có `Access-Control-Allow-Origin: http://localhost:5500`. Nhấn POST JSON, thường có **OPTIONS /api/echo** trước POST do Content-Type là `application/json`.

Ví dụ kết quả GET (UUID thay đổi mỗi lần):

~~~json
{
  "status": 200,
  "requestId": "40eb6b51-c2f7-4b73-9921-73084776ad6f",
  "payload": {
    "service": "cors-demo",
    "status": "ok"
  }
}
~~~

Ví dụ kết quả POST:

~~~json
{
  "status": 200,
  "requestId": "39e5594b-6306-4715-9e3e-6fce6ba1b6ec",
  "payload": {
    "echo": "Echo: Xin chào"
  }
}
~~~

Các request IDs chỉ là ví dụ, không cố định; không phụ thuộc thời gian response hay số lần OPTIONS thực tế vì preflight cache có thể làm OPTIONS không xuất hiện ở mỗi lần thử.

### 10.6. Kiểm tra bằng curl: không thay thế kiểm thử browser

Mô phỏng actual GET với header Origin:

~~~bash
curl -i 'http://localhost:8080/api/info' \
  -H 'Origin: http://localhost:5500'
~~~

Bạn có thể thấy:

~~~http
HTTP/1.1 200
Access-Control-Allow-Origin: http://localhost:5500
Access-Control-Expose-Headers: X-Request-ID
Vary: Origin
Content-Type: application/json
~~~

Headers có thể còn thêm các trường khác; thứ tự và một số casing khác nhau tùy container.

Mô phỏng preflight:

~~~bash
curl -i -X OPTIONS 'http://localhost:8080/api/echo' \
  -H 'Origin: http://localhost:5500' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
~~~

Response preflight hợp lệ thường có:

~~~http
Access-Control-Allow-Origin: http://localhost:5500
Access-Control-Allow-Methods: GET,POST
Access-Control-Allow-Headers: content-type
Access-Control-Max-Age: 1800
~~~

Bây giờ đổi Origin thành origin không được phép:

~~~bash
curl -i -X OPTIONS 'http://localhost:8080/api/echo' \
  -H 'Origin: http://localhost:5501' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
~~~

Spring MVC có thể từ chối preflight (thông thường trả **403 Invalid CORS request**). Browser sẽ không gửi actual POST cho preflight bị từ chối.

**Nhưng:** `curl` **không thực thi SOP/CORS**; dù nhận HTTP response 200/403, nó vẫn hiển thị bytes. Một phép thử CORS hoàn chỉnh cần **browser DevTools** hoặc integration tests xác minh policy headers.

### 10.7. MockMvc tests: origin được phép và bị cấm

Tạo `src/test/java/demo/CorsOriginTest.java`:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CorsOriginTest {
    @Autowired
    MockMvc mvc;

    @Test
    void allowsActualGetFromTrustedOrigin() throws Exception {
        mvc.perform(get("/api/info")
                .header("Origin", "http://localhost:5500"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    "Access-Control-Allow-Origin",
                    "http://localhost:5500"))
            .andExpect(header().string(
                    "Access-Control-Expose-Headers", "X-Request-ID"))
            .andExpect(jsonPath("$.service").value("cors-demo"));
    }

    @Test
    void requestWithoutOriginNeedsNoCorsHeader() throws Exception {
        mvc.perform(get("/api/info"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(
                    "Access-Control-Allow-Origin"));
    }

    @Test
    void jsonPostPreflightIsApproved() throws Exception {
        mvc.perform(options("/api/echo")
                .header("Origin", "http://localhost:5500")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    "Access-Control-Allow-Origin",
                    "http://localhost:5500"))
            .andExpect(header().string(
                    "Access-Control-Allow-Methods",
                    containsString("POST")))
            .andExpect(header().string(
                    "Access-Control-Allow-Headers",
                    containsString("content-type")));
    }

    @Test
    void unknownOriginPreflightIsRejected() throws Exception {
        mvc.perform(options("/api/echo")
                .header("Origin", "http://localhost:5501")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthorizedMethodForCorsIsRejected() throws Exception {
        mvc.perform(options("/api/info")
                .header("Origin", "http://localhost:5500")
                .header("Access-Control-Request-Method", "DELETE"))
            .andExpect(status().isForbidden());
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

Những test này xác minh Spring MVC CORS handler: actual response headers, preflight permit/reject theo origin và method, request không có Origin không cần ACAO. Để thấy browser enforcement và credentials/cookie behavior, vẫn cần chạy browser hoặc E2E test.

**Lưu ý status:** implementation Spring CORS có thể chọn status khác cho một số preflight thành công theo cấu hình; test này nhắm đúng Spring MVC 6.2 điển hình. Nếu đổi framework/custom CorsProcessor, điều chỉnh expectation theo HTTP semantics thực tế, không bỏ kiểm tra origin/method/headers.

## 11. Các tình huống CORS phát sinh trong dự án thật

### 11.1. Frontend và API cùng domain nhưng khác port

Trang `http://localhost:5173` gọi `http://localhost:8080` là cross-origin. Cho phép đúng `http://localhost:5173` tại backend hoặc dùng development reverse proxy để frontend gọi `/api` cùng origin của dev server. Dev proxy là công cụ làm URL **same-origin từ góc nhìn browser**, không phải "tắt bảo mật của trình duyệt".

### 11.2. Frontend HTTPS nhưng backend HTTP

`https://app.example.com` gọi `http://api.example.com` vừa cross-origin vừa có nguy cơ **mixed content**. Trước khi tính CORS, browser có thể chặn request do chính sách mixed content. Giải pháp đúng là phục vụ API qua HTTPS; không dùng ACAO để hợp thức hóa HTTP không an toàn.

### 11.3. Proxy đổi Host/Origin

Khi có gateway hoặc reverse proxy, browser thấy public URL khác backend internal address. CORS policy cần dựa trên **public origin chính xác của frontend**; forwarded headers phải đến từ trusted proxies và được cấu hình an toàn. Không tự phản chiếu bất kỳ `Origin` nào mà không allowlist.

### 11.4. Cookie login và khác subdomain

Trang `https://app.example.com` gọi `https://api.example.com` là cross-origin, mặc dù thường same-site. Khi dùng `fetch(..., {credentials:"include"})` và server cho phép credentials, cần allow một origin cụ thể + `Access-Control-Allow-Credentials: true`; cookie policy và CSRF vẫn cần thiết. Không suy ra same-site là không cần CORS.

### 11.5. CORS error che lỗi thật

Server đang trả 500 trước khi CORS processor gắn headers hoặc error handler/gateway trả response khác. Browser chỉ báo “CORS policy blocked”, khiến developer tưởng origin sai. Kiểm tra logs server, HTTP status và actual headers bằng DevTools/curl, rồi sửa **lỗi backend** và error response CORS policy đúng, không bật wildcard bừa để che 500.

### 11.6. Preflight bị authentication filter chặn

Browser gửi OPTIONS không có session cookie; Security filter trả 401/403 trước CORS config. Backend POST controller không được gọi. Hãy cấu hình CORS với Spring Security ở đúng tầng và để policy CORS xử lý OPTIONS, đồng thời giữ authorization cho actual request. Không vì vậy mà bỏ auth của mọi endpoint.

### 11.7. Response cache theo nhầm origin

Server cho `https://a.example`, nhưng CDN cache phản hồi ACAO của A và trả cho B. Nếu ACAO được chọn động, header `Vary: Origin` giúp cache phân biệt variants. CORS không tự cấm caching nội dung nhạy cảm; riêng `Cache-Control` phải đặt theo data privacy.

### 11.8. Custom header làm phát sinh OPTIONS

Frontend thêm `X-Client-Version`, `X-Trace-ID` hoặc `Authorization`. Kể cả GET cũng có thể cần preflight. Nếu API thiếu `allowedHeaders` tương ứng, preflight bị từ chối. Cấu hình chỉ cho phép headers thực sự cần; tránh dùng `*` mặc định trên mọi endpoint nhạy cảm.

## 12. Những lỗi phổ biến cần tránh

1. **So origin theo domain mà quên port:** localhost:3000 khác localhost:8080.
2. **Nhầm path/query là thành phần origin:** `/a` và `/b` trên cùng scheme/host/port là same-origin.
3. **Nhầm same-site và same-origin:** subdomains có thể same-site nhưng vẫn cross-origin.
4. **Cho rằng mọi cross-origin request đều có preflight:** GET thông thường không cần OPTIONS.
5. **Cho rằng không có preflight thì không cần ACAO:** actual response vẫn phải cho phép JavaScript đọc.
6. **Nhầm CORS với authentication:** bất kỳ HTTP client ngoài browser vẫn có thể gọi API nếu không có auth.
7. **Cho rằng CORS là CSRF protection:** cross-site form POST có thể tới server dù không có CORS.
8. **Dùng `Access-Control-Allow-Origin: *` cho credentialed requests:** browser không expose response.
9. **Viết nhiều ACAO origins trong một header CSV:** không đúng format hợp lệ.
10. **Tin header Origin do client cung cấp là bảo mật:** non-browser clients có thể giả mạo.
11. **Whitelist `Origin: null` để hỗ trợ file://:** opaque origins không phải danh tính tin cậy.
12. **Thêm @CrossOrigin("*") lên tất cả controllers:** vô tình mở quyền đọc response rộng hơn yêu cầu.
13. **Cấu hình CORS ở Spring MVC nhưng Security chặn OPTIONS trước:** cần tích hợp CORS trước security checks.
14. **Quên expose custom response header:** JS không đọc được `X-Request-ID` dù Network tab thấy.
15. **Dùng `fetch(..., {mode:"no-cors"})` như cách sửa:** response thường opaque, JS không đọc JSON/status như mong muốn.
16. **Tắt browser web security để demo chạy:** che lỗi cấu hình, tạo rủi ro local environment và không phải giải pháp triển khai.
17. **Chỉ test bằng curl:** curl không thực thi SOP; phải có browser kiểm tra.
18. **Bỏ qua Vary: Origin ở reverse proxy/cache:** cached CORS headers có thể sai theo origin.
19. **Coi 200 ở Network là fetch thành công:** browser có thể đã chặn response.
20. **Không xem lỗi ngoài CORS:** TLS, mixed content, DNS, CSP, server error và redirect có thể là nguyên nhân thật.

## 13. Cách debug theo thứ tự

Khi Console báo CORS error:

1. **Xác nhận origin trang nguồn** bằng `window.location.origin` và origin URL API: so scheme, host, effective port.
2. **Quan sát Network:** có OPTIONS không? Nếu có, xem request `Origin`, `Access-Control-Request-Method` và `Access-Control-Request-Headers`.
3. **Kiểm tra response preflight:** ACAO có khớp? method/header có được allow? Có HTTP 401/403/redirect?
4. **Nếu actual request xảy ra**, xem HTTP status và response ACAO; nhớ 500/404 thật vẫn có thể bị console hiển thị như CORS block.
5. **Kiểm tra credentials mode:** `same-origin` mặc định hay `include`? Cookie có bị SameSite/Secure/third-party policy chặn không? ACA-Credentials có hợp lệ?
6. **Kiểm tra Spring config:** mapping path, `@CrossOrigin` vs `addCorsMappings`, allowed origins/methods/headers, exposed headers.
7. **Nếu có Spring Security**, kiểm tra filter order và `http.cors(...)`, CSRF handling, preflight không có session cookies.
8. **Nếu có proxy/gateway**, xem response ACAO có bị thêm trùng/xóa, Vary headers, public scheme/host có bị rewrite.
9. **Đừng sửa bằng wildcard ngay:** trước tiên xác định policy mong muốn và test một origin hợp lệ/một origin bị từ chối.
10. **Test browser + server:** curl để xem HTTP bytes, MockMvc để kiểm CORS processing, E2E browser để xác nhận browser enforcement.

## 14. Câu hỏi và bài tập

**Bài 1 — Tính origin:** Trang `https://shop.example.com:443/cart` gọi `https://shop.example.com/api`, `http://shop.example.com/api` và `https://api.example.com/api`. Xác định trường hợp nào same-origin và giải thích scheme/host/port.

**Bài 2 — Same-site:** `https://app.example.com` gọi `https://api.example.com`. Chúng same-origin hay same-site? Vì sao câu trả lời có ảnh hưởng khác nhau đối với CORS và Cookie SameSite?

**Bài 3 — Preflight:** Với bốn fetch requests: GET thuần, POST JSON, POST form-urlencoded và GET có header Authorization, hãy dự đoán cái nào cần OPTIONS và vì sao.

**Bài 4 — Đọc response:** Một GET cross-origin trả HTTP 200 JSON nhưng không có ACAO. Server đã nhận request chưa? Browser JavaScript có đọc body không?

**Bài 5 — Credentials:** Viết cấu hình CORS cho frontend `https://app.example.com` dùng cookie-authenticated API. Vì sao không thể dùng ACAO `*` với credentials? Cần kiểm tra thêm cookie policy và CSRF gì?

**Bài 6 — Spring MVC:** Dùng `WebMvcConfigurer#addCorsMappings` chỉ cho phép `https://admin.example.com` gọi GET `/api/admin/**`; không cho phép origin bất kỳ khác. Viết MockMvc preflight test cho GET cùng origin đó và DELETE bị từ chối.

**Bài 7 — Debug:** Trình duyệt báo CORS error khi API 500. Liệt kê ba cách phân biệt backend thật sự lỗi với thiếu CORS headers và vì sao bật wildcard không chữa root cause.

**Bài 8 — Proxy:** Gateway và backend đều tự thêm ACAO. Cách nào kiểm tra header trùng? Nên chọn một nơi chịu trách nhiệm ra sao?

**Bài 9 — Browser và curl:** Vì sao curl thành công với endpoint không cấu hình CORS nhưng fetch từ localhost:5500 thất bại? Nêu đúng lớp thực thi chính sách.

**Bài 10 — Security:** Giải thích vì sao CORS không bảo đảm endpoint thanh toán khỏi CSRF, và vì sao một server API cần authorization ngay cả khi allowedOrigins chỉ có một domain.

## 15. Ghi nhớ

- **Origin của HTTP(S) = scheme + host + effective port**; khác một thành phần là cross-origin.
- SOP hạn chế quyền truy cập của script giữa origins, **không chặn tuyệt đối mọi request/navigations/embeddings**.
- Cross-origin browser fetch có thể **không cần preflight**, nhưng JS chỉ đọc response nếu **actual CORS response cho phép**.
- **Preflight OPTIONS** thường xuất hiện với JSON POST, custom headers hoặc methods như PUT/DELETE.
- CORS là chính sách browser/server opt-in; **không thay authentication, authorization, CSRF protection hoặc cookie security**.
- Credentialed requests không thể dùng ACAO wildcard `*`; phải dùng origin được phép cụ thể cùng các điều kiện khác.
- Trong Spring MVC, chọn `@CrossOrigin` hoặc `addCorsMappings` hợp lý; khi có Spring Security, xử lý CORS trước authentication/authorization.
- Debug dựa trên **origin thật, OPTIONS, actual response, credentials, HTTP status và network** — không chỉ đọc câu thông báo CORS trong Console.

## 16. Tài liệu chính thức

- [MDN — Same-origin policy](https://developer.mozilla.org/en-US/docs/Web/Security/Defenses/Same-origin_policy): định nghĩa origin, các loại tương tác cross-origin, SOP.
- [MDN — Cross-Origin Resource Sharing](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS): safelisted/simple requests, preflight, credentials và headers.
- [MDN — Access-Control-Allow-Origin](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Access-Control-Allow-Origin): origin cụ thể, wildcard, credentials và Vary.
- [Fetch Standard](https://fetch.spec.whatwg.org/): định nghĩa hiện đại của CORS protocol và request/response handling.
- [Spring Framework 6.2 — MVC CORS](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc-cors.html): @CrossOrigin, WebMvcConfigurer, CorsProcessor và cấu hình toàn cục.
- [Spring Security — CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html): xử lý preflight trước Spring Security.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cors-web-security-002">Yêu cầu đơn giản, preflight OPTIONS và các header CORS</a>

<details>
<summary>Xem chi tiết</summary>

# Yêu cầu đơn giản, preflight OPTIONS và các header CORS trong Spring MVC

## 1. Bài toán thực tế: frontend khác origin gọi API

Một frontend chạy tại `https://app.example.test` gọi API tại `https://api.example.test`:

~~~javascript
const response = await fetch("https://api.example.test/api/demo/items");
const items = await response.json();
console.log(items);
~~~

Hai địa chỉ có hostname khác nhau nên là **khác origin**, dù đều dùng HTTPS. Trình duyệt áp dụng **same-origin policy**: JavaScript ở một origin không được tự ý đọc response từ origin khác nếu server không cấp quyền thông qua **Cross-Origin Resource Sharing (CORS)**.

Server có thể gửi:

~~~http
HTTP/1.1 200 OK
Content-Type: application/json
Access-Control-Allow-Origin: https://app.example.test

[{"id":1,"name":"Spring MVC"}]
~~~

Nếu request/response thỏa chính sách CORS, browser cho phép script đọc JSON. Nếu thiếu header phù hợp, server **vẫn có thể đã xử lý HTTP request và gửi response**, nhưng browser không cho JavaScript đọc kết quả. Đây là điểm căn bản: **CORS là cơ chế kiểm soát truy cập response của trình duyệt**, không phải hệ thống xác thực người dùng.

### 1.1. Mục tiêu của bài

Sau bài này, bạn cần phân biệt rõ:
1. **Origin** được tính từ scheme, host, port như thế nào.
2. Request **CORS-safelisted / simple** là gì, vì sao nó **không có preflight** nhưng vẫn cần response header.
3. Khi nào browser tự gửi **preflight OPTIONS**, nội dung `Origin`, `Access-Control-Request-Method`, `Access-Control-Request-Headers`.
4. Ý nghĩa chính xác của các header `Access-Control-Allow-Origin`, `-Methods`, `-Headers`, `-Credentials`, `-Expose-Headers`, `-Max-Age` và `Vary`.
5. Cách cấu hình CORS trong Spring MVC bằng `WebMvcConfigurer` hoặc `@CrossOrigin`, và cách tích hợp Spring Security đúng thứ tự.
6. Kiểm thử simple/preflight bằng MockMvc, nhận diện cấu hình quá rộng, origin sai, cookie/credential và lỗi security.

Bài dùng Java 17+, Spring Boot 3.x / Spring MVC Servlet stack. Các ví dụ Spring Boot 3.3.5 nhằm tái hiện cơ chế, còn tiêu chuẩn CORS do browser Fetch/HTTP quyết định, **không phải do Spring tự định nghĩa lại**.

## 2. Origin và same-origin policy

Một origin gồm bộ ba:

~~~text
origin = (scheme, hostname, effective port)
~~~

Ví dụ xét trang đang chạy ở `https://app.example.test`:

| URL đích | Same-origin? | Lý do |
| --- | --- | --- |
| `https://app.example.test/api/items` | Có | Cùng scheme, hostname và port mặc định 443 |
| `https://app.example.test:443/api/items` | Có | Port 443 là port mặc định của HTTPS |
| `http://app.example.test/api/items` | Không | Scheme HTTP khác HTTPS |
| `https://api.example.test/api/items` | Không | Khác hostname/subdomain |
| `https://app.example.test:8443/api/items` | Không | Port 8443 khác 443 |
| `https://app.example.test/other/path` | Có | Khác path **không làm khác origin** |

Origin không bằng site: trong một số trường hợp, hai subdomain khác origin nhưng được xem là same-site cho chính sách cookie. **CORS, SameSite cookie và CSRF không phải một cơ chế duy nhất**.

Request CORS thường mang header:

~~~http
Origin: https://app.example.test
~~~

Giá trị `Origin` **không có path**, không có dấu gạch cuối URL, gồm scheme + host + port nếu khác port mặc định. Một số trường hợp đặc biệt có origin `null` (sandboxed frame, file/opaque origins); **không cấp quyền `null` một cách tùy tiện** vì nhiều ngữ cảnh không tin cậy có thể mang giá trị này.

Request không có `Origin`, ví dụ nhiều client server-to-server hoặc curl mặc định, không có cùng quy trình thực thi CORS như browser. Có header `Origin` giả lập bằng curl chỉ giúp quan sát response server, **không tái hiện toàn bộ quyết định của browser**.

## 3. Request “simple” / CORS-safelisted là gì?

Thuật ngữ *simple request* thường dùng để chỉ request thuộc phạm vi **không cần CORS preflight** theo Fetch. Đây không phải “request GET thì luôn simple”: các điều kiện về method và request headers **phải đồng thời thỏa**.

### 3.1. Phương thức

Các phương thức CORS-safelisted cho trường hợp thường gặp:

- `GET`
- `HEAD`
- `POST`

`PUT`, `PATCH`, `DELETE` thường yêu cầu preflight khi được gọi cross-origin từ browser, kể cả không có body. `OPTIONS` là phương thức browser dùng để hỏi quyền trước khi gửi actual request; bạn **không cần** tự gọi OPTIONS từ JavaScript chỉ vì endpoint sử dụng PUT.

### 3.2. Request headers

Các request header được script chủ động đặt phải nằm trong nhóm **CORS-safelisted request headers** và đáp ứng thêm điều kiện về giá trị/kích thước. Nhóm thường gặp gồm:
- `Accept`.
- `Accept-Language`.
- `Content-Language`.
- `Content-Type` với ràng buộc media type bên dưới.
- `Range` trong một số trường hợp value đơn giản chỉ có **một** byte range.

Fetch còn có những ràng buộc chi tiết về giá trị, độ dài, byte không an toàn và user-agent; vì vậy bảng chỉ là hướng dẫn thực hành, **không phải thuật toán đầy đủ thay cho Fetch spec**. Header do trình duyệt tự đặt như `User-Agent`, `Host` và một số forbidden request headers không được coi như tùy ý do script gắn.

Với `Content-Type`, media type essence CORS-safelisted phổ biến là:

~~~text
application/x-www-form-urlencoded
multipart/form-data
text/plain
~~~

**`application/json` không nằm trong danh sách này.** Do đó `POST` JSON cross-origin thường kích hoạt preflight mặc dù `POST` là safelisted method. Ngược lại, một form POST được trình duyệt tạo với `application/x-www-form-urlencoded`, không có custom headers, thường không cần preflight.

Cũng cần chú ý tên “simple” là phân loại kiểm soát CORS, **không có nghĩa request an toàn hoặc chỉ đọc**. Simple `POST` vẫn có thể thay đổi trạng thái, và browser có thể gửi request trước khi được xác nhận quyền đọc response.

### 3.3. Bảng tình huống

| Request từ JS ở frontend khác origin | Preflight thông thường? | Giải thích |
| --- | --- | --- |
| `fetch(url)`, GET, không custom header | Không | GET + safelisted headers |
| `fetch(url, {method:"HEAD"})` | Không | HEAD |
| POST `application/x-www-form-urlencoded` | Không | POST + safelisted media type, nếu các điều kiện khác thỏa |
| POST `text/plain` | Không | Tương tự |
| POST `application/json` | Có | `Content-Type` không safelisted |
| GET với `Authorization: Bearer ...` | Có | Authorization không là safelisted request header |
| GET với `X-Request-Id` do script đặt | Có | Custom header |
| `PUT` với JSON | Có | Method và Content-Type đều ngoài safelist |
| `DELETE` không body | Có | DELETE không safelisted |
| POST multipart FormData với browser tự đặt boundary | Thường không | Nếu không thêm header không safelisted và các điều kiện còn lại thỏa |

**Không tự thêm `Content-Type: multipart/form-data` khi gửi `FormData` bằng fetch**: browser cần tạo boundary khớp body; tự đặt sai header có thể làm backend không parse multipart. Trường hợp có upload streaming, event listeners hoặc chính sách trình duyệt đặc biệt có thể ảnh hưởng preflight; hãy quan sát Network tab trên browser mục tiêu.

## 4. Luồng simple request: không có OPTIONS, vẫn phải có CORS headers

Frontend:

~~~javascript
const r = await fetch("https://api.example.test/api/demo/items", {
  method: "GET",
  headers: { Accept: "application/json" }
});
const items = await r.json();
~~~

Trình duyệt gửi trực tiếp request thực:

~~~http
GET /api/demo/items HTTP/1.1
Host: api.example.test
Origin: https://app.example.test
Accept: application/json
~~~

Server xử lý GET và thêm ít nhất một chính sách origin hợp lệ:

~~~http
HTTP/1.1 200 OK
Content-Type: application/json
Access-Control-Allow-Origin: https://app.example.test
Vary: Origin

[{"id":1,"name":"Spring MVC"}]
~~~

**Không có `Access-Control-Request-Method` trong GET bình thường**, và `Access-Control-Allow-Methods` không phải điều kiện bắt buộc trên response của simple GET. Browser kiểm tra `Access-Control-Allow-Origin` (và credentials nếu request có credentials) để quyết định script có được đọc response.

Nếu server thiếu `Access-Control-Allow-Origin` hoặc giá trị không khớp origin, JavaScript thường thấy lỗi CORS/`TypeError: Failed to fetch`, không được đọc HTTP body/status như một response bình thường. Trong server logs, GET vẫn có thể được ghi nhận là 200. Vì vậy **“Network thấy 200” không chứng minh JavaScript có quyền đọc**.

### 4.1. Tại sao simple request không phải bảo vệ CSRF?

Một trang độc hại có thể gây browser gửi form POST đơn giản tới website khác khi cookie được trình duyệt đính kèm theo chính sách cookie. Dù CORS cấm script đọc response, **side effect trên server có thể đã xảy ra**.

CORS không phải bộ lọc CSRF. Với session/cookie authentication và các method có thay đổi dữ liệu, cần CSRF token hoặc cơ chế chống CSRF phù hợp, kiểm soát `SameSite`/`Secure`, kiểm tra Origin/Referer khi thích hợp và authorization cho từng hành động. Không tắt CSRF chỉ vì đã đặt allowlist CORS.

## 5. Preflight OPTIONS: browser hỏi gì trước khi gửi request thực?

Giả sử frontend muốn tạo tài nguyên bằng JSON:

~~~javascript
await fetch("https://api.example.test/api/demo/items", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "X-Request-Id": "req-42"
  },
  body: JSON.stringify({ name: "Spring CORS" })
});
~~~

Vì JSON Content-Type và custom header không safelisted, browser thường gửi **preflight OPTIONS**:

~~~http
OPTIONS /api/demo/items HTTP/1.1
Host: api.example.test
Origin: https://app.example.test
Access-Control-Request-Method: POST
Access-Control-Request-Headers: content-type,x-request-id
~~~

Ý nghĩa:
- `Origin`: trang gọi API.
- `Access-Control-Request-Method`: phương thức **dự định dùng trên request sau**, ở đây POST. Phương thức hiện tại là OPTIONS.
- `Access-Control-Request-Headers`: các header không safelisted mà actual request muốn sử dụng. Browser thường chuẩn hóa tên/lowercase và có thể sắp xếp thứ tự.
- Preflight **không gửi JSON body của POST**. Nó chỉ là hỏi chính sách.
- Theo Fetch, CORS preflight **không gửi credentials** (ví dụ cookies) của actual request. Một số môi trường TLS client certificates có sai lệch triển khai; không thiết kế quy trình app dựa trên credentials xuất hiện trong preflight.

Nếu server cấp quyền:

~~~http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: https://app.example.test
Access-Control-Allow-Methods: GET, POST, PUT
Access-Control-Allow-Headers: Content-Type, X-Request-Id
Access-Control-Max-Age: 600
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
~~~

Dạng header `Vary` có thể được gộp thành một dòng danh sách thay vì ba dòng, ví dụ `Vary: Origin, Access-Control-Request-Method, Access-Control-Request-Headers`.

Sau khi preflight được chấp thuận, browser mới gửi actual request:

~~~http
POST /api/demo/items HTTP/1.1
Host: api.example.test
Origin: https://app.example.test
Content-Type: application/json
X-Request-Id: req-42

{"name":"Spring CORS"}
~~~

Response thực vẫn cần `Access-Control-Allow-Origin` phù hợp:

~~~http
HTTP/1.1 201 Created
Content-Type: application/json
Access-Control-Allow-Origin: https://app.example.test
Access-Control-Expose-Headers: X-Request-Id
X-Request-Id: create-1

{"id":2,"name":"Spring CORS"}
~~~

**Preflight thành công không thay thế kiểm tra CORS ở response thực.** Nếu response thực thiếu/không khớp allow-origin, browser vẫn không cho JavaScript đọc, dù POST đã chạy.

### 5.1. Browser không phải lúc nào cũng gửi OPTIONS trước từng request

Browser có thể cache một kết quả preflight theo origin/URL/method/headers và các quy tắc Fetch. `Access-Control-Max-Age` cho phép server đề xuất thời lượng cache; browser có thể đặt giới hạn riêng hoặc bỏ cache sau khi điều kiện thay đổi. Đây thường là cache riêng cho **kết quả preflight**, không phải cache HTTP payload của GET/POST.

Do đó việc DevTools không hiển thị OPTIONS ở lần thứ hai **không chứng minh API tự động trở thành simple**. Hãy thử “Disable cache”/cache-clearing, network conditions và origin/header cụ thể, nhưng hiểu rằng DevTools và user agent có thể quản lý preflight cache khác HTTP cache thông thường.

### 5.2. Khi preflight bị từ chối

Server có thể không trả allow headers tương ứng, hoặc Spring MVC/Spring Security có thể trả lỗi như **403 Invalid CORS request**. Browser lúc này **không gửi actual POST/PUT**. Ở JavaScript, ứng dụng thường nhận network/CORS rejection thay vì body 403 để phân tích bằng `response.json()`.

Không tạo controller `@RequestMapping(method=OPTIONS)` riêng chỉ để echo mọi header trong production. Spring MVC có CORS processor và policy tập trung; nếu tự echo `Origin`, `Access-Control-Request-Method` và `Access-Control-Request-Headers` vô điều kiện, bạn đang mở quyền cross-origin ngoài ý muốn.

## 6. Bảng tất cả header CORS cần nhớ

### 6.1. Request headers

| Header | Ai gửi? | Xuất hiện khi nào? |
| --- | --- | --- |
| `Origin` | Browser / user-agent | Cross-origin request và preflight; cũng có thể có trên một số same-origin request |
| `Access-Control-Request-Method` | Browser | Preflight OPTIONS; chỉ phương thức actual mà client dự định dùng |
| `Access-Control-Request-Headers` | Browser | Preflight khi có header cần xin phép; là **danh sách tên header**, không phải giá trị Authorization/token |

Không cấu hình frontend tự gán `Access-Control-Request-Method` hoặc `Origin` để “vượt CORS”; browser quản lý những header đó. Client dùng `fetch` với method và headers thật, trình duyệt tự quyết định có preflight.

### 6.2. Response headers

| Header | Nơi có ý nghĩa | Ý nghĩa |
| --- | --- | --- |
| `Access-Control-Allow-Origin` | Preflight và actual response | Cho phép một origin cụ thể hoặc `*` khi không dùng credentials |
| `Access-Control-Allow-Methods` | Preflight | Cho phép các HTTP methods được hỏi |
| `Access-Control-Allow-Headers` | Preflight | Cho phép request headers mà browser đã liệt kê |
| `Access-Control-Allow-Credentials` | Preflight và actual với credentialed requests | Giá trị hợp lệ `true` cho phép credentials theo điều kiện CORS |
| `Access-Control-Max-Age` | Preflight | Số giây cache kết quả preflight (subject to browser limits) |
| `Access-Control-Expose-Headers` | Actual response | Cho phép JavaScript đọc thêm response headers ngoài nhóm được expose mặc định |
| `Vary` | Response có nội dung phụ thuộc Origin/preflight headers | Hướng dẫn cache phân biệt các biến thể response |

**Không nhầm `Access-Control-Allow-Headers` với `Access-Control-Expose-Headers`.** Allow-Headers liên quan header mà **client được gửi** (ví dụ Authorization, X-Request-Id). Expose-Headers liên quan header **server trả về** mà script được đọc qua `Response.headers.get(...)` (ví dụ X-Request-Id).

Ví dụ JavaScript:

~~~javascript
const response = await fetch(
  "https://api.example.test/api/demo/items",
  { headers: { Accept: "application/json" } }
);

console.log(response.headers.get("X-Request-Id"));
~~~

Để JS đọc `X-Request-Id`, response cross-origin cần `Access-Control-Expose-Headers: X-Request-Id`. Header có thể vẫn hiển thị trong Network tab khi chưa expose, nhưng JS không được truy cập nó; cấu hình expose ở server, không tự thêm `Access-Control-Expose-Headers` vào request.

## 7. Wildcard, credentials, cookies và những giới hạn quan trọng

### 7.1. `Access-Control-Allow-Origin: *`

Với **request không dùng credentials**, policy `Access-Control-Allow-Origin: *` có thể cho phép code từ mọi origin đọc response. Nó phù hợp hơn với dữ liệu thật sự công khai, được chủ ý cung cấp cho mọi website.

Đối với request có credentials, **không được dùng `Access-Control-Allow-Origin: *`**. Browser yêu cầu allow-origin khớp một origin cụ thể; server cũng cần `Access-Control-Allow-Credentials: true`. Trong Spring Framework, `allowCredentials(true)` không tương thích với `allowedOrigins("*")`. Nếu thật sự cần pattern khớp nhiều origin, Spring có `allowedOriginPatterns` nhưng phải giới hạn pattern chặt chẽ; việc echo origin qua pattern quá rộng vẫn có thể mở quyền cho các website không đáng tin.

### 7.2. `fetch` và credentials

Frontend khi cần browser gửi cookie cross-origin:

~~~javascript
const response = await fetch("https://api.example.test/api/me", {
  credentials: "include"
});
~~~

`credentials:"include"` chỉ là **ý định của client**; cookie còn phải thỏa domain/path/`Secure`/`SameSite` và chính sách browser về third-party cookies. Server phải phản hồi allow-origin cụ thể và `Access-Control-Allow-Credentials: true` để script được đọc.

Ngay cả khi CORS cho phép, cookie với `SameSite=Lax` hoặc `Strict` có thể không được gửi trong một request thật sự cross-site. Với `SameSite=None`, browser hiện đại yêu cầu `Secure`; một số browser hoặc policy người dùng vẫn chặn third-party cookies. **CORS không ép cookie vượt qua những giới hạn đó**.

Ví dụ actual response cho credentialed request:

~~~http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: https://app.example.test
Access-Control-Allow-Credentials: true
Vary: Origin
Content-Type: application/json

{"user":"example"}
~~~

Không bật credentials chỉ vì API có authentication bearer token: bearer header `Authorization` thường kích hoạt preflight do không safelisted, nhưng việc có header auth và việc dùng cookie/`credentials:"include"` là hai khía cạnh riêng. Hãy chọn policy dựa trên mô hình xác thực thực tế.

### 7.3. Preflight và Spring Security

Với browser thông thường, preflight OPTIONS **không mang session cookie**. Nếu security filter yêu cầu authentication cho OPTIONS trước khi CORS policy được kiểm tra, server có thể từ chối bằng 401/403 và browser không gửi request thực. Trong Spring Security nên xử lý CORS ở vị trí phù hợp **trước khi yêu cầu xác thực**. Tuy nhiên, cho preflight đi qua **không** có nghĩa mở quyền cho actual POST/DELETE: phương thức thật vẫn phải được authenticate/authorize/CSRF-check theo cấu hình.

### 7.4. CORS không phải authorization

- Browser thực thi kiểm tra CORS; client server-to-server có thể gọi API mà không cần tuân theo `Access-Control-Allow-Origin`.
- Không dùng header `Origin` thay `Authentication`/`Authorization`. Header Origin không phải danh tính đã xác thực.
- CORS không bảo vệ server khỏi mọi request thay đổi trạng thái và không thay thế CSRF.
- CORS không quyết định role/permission của user sau khi login.
- Cross-origin không đồng nghĩa cross-site; cookie rules và origin rules khác nhau.
- Một lỗi CORS trên browser không chứng minh endpoint backend là 404/500; server có thể đã thành công nhưng response không được script đọc.

## 8. Cấu hình CORS trong Spring MVC

Có hai cách chính: annotation trên controller (`@CrossOrigin`) hoặc cấu hình URL patterns toàn cục qua `WebMvcConfigurer`. Cả hai dùng `CorsConfiguration` / CORS processor nội bộ của Spring MVC.

### 8.1. `@CrossOrigin`

Ví dụ fragment:

~~~java
@RestController
@RequestMapping("/api/public")
@CrossOrigin(origins = "https://app.example.test", maxAge = 600)
class PublicApiController {

    @GetMapping("/status")
    public String status() {
        return "ok";
    }
}
~~~

Đây là snippet cần imports Spring tương ứng và nằm trong project Spring MVC. `@CrossOrigin` có thể đặt cấp method hoặc class. Với cấu hình mặc định không chỉ rõ origins, Spring MVC có thể cho phép tất cả origins, nên **không dùng `@CrossOrigin` trống trên controller nhạy cảm mà chưa xét security model**.

Khi cấu hình class-level và method-level cùng tồn tại, Spring MVC có thể kết hợp cấu hình. Không tự giả định annotation ở method sẽ luôn “thu hẹp” toàn bộ origin ở class theo kiểu deny-by-default; kiểm tra thực tế bằng MockMvc nếu có nhiều lớp config.

### 8.2. `WebMvcConfigurer` và allowlist tường minh

Đối với nhiều endpoint, policy tập trung dễ kiểm soát hơn:

~~~java
package com.example.corsdemo.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/demo/**")
                .allowedOrigins("https://app.example.test")
                .allowedMethods("GET", "POST", "PUT")
                .allowedHeaders("Content-Type", "X-Request-Id", "Authorization")
                .exposedHeaders("X-Request-Id")
                .allowCredentials(false)
                .maxAge(600);
    }
}
~~~

Trong policy ví dụ:
- Chỉ allow `https://app.example.test`.
- Chỉ CORS-enable GET, POST, PUT trên các path `/api/demo/**`; không cấp quyền CORS cho tất cả URL của server.
- `Content-Type`, `X-Request-Id`, `Authorization` là những **header có thể xin phép qua preflight**. **Chúng không tự tạo authentication**; controller/security phải xử lý Authorization nếu dùng.
- `X-Request-Id` được expose để JavaScript đọc trong actual response.
- `allowCredentials(false)` cho biết demo không hỗ trợ credentialed CORS.
- `maxAge(600)` đề xuất cache kết quả preflight 600 giây; browser có giới hạn riêng.

**Đừng đồng nhất allowedMethods với methods thật của controller:** cấu hình CORS cho phép một method không làm Spring tạo endpoint chưa tồn tại. Ví dụ cho phép `PUT` nhưng handler không có `@PutMapping` vẫn có thể nhận 405 khi actual request được xử lý.

Spring MVC global config thường có các mặc định cho origins/headers/methods khi chưa đặt giá trị, và `maxAge` mặc định hay gặp là 1800 giây. Nhưng một đối tượng `new CorsConfiguration()` thuần không mặc nhiên cho mọi origin; các defaults được áp theo ngữ cảnh cấu hình. Với API có yêu cầu rõ ràng, **nêu tất cả giới hạn quan trọng tường minh** như snippet trên.

### 8.3. Global và local config chồng nhau

Nếu một method có `@CrossOrigin` và ứng dụng đồng thời khai báo `WebMvcConfigurer.addCorsMappings`, Spring MVC có thể kết hợp local/global `CorsConfiguration`. Với các thuộc tính collection như allowed origins, cấu hình ghép có thể rộng hơn dự định. Nếu mục tiêu là allowlist nghiêm ngặt, ưu tiên một nguồn policy rõ ràng hoặc viết MockMvc test chứng minh các origin/method/headers thực tế bị từ chối đúng.

Nếu có Spring Security, CORS filter và MVC configuration phải phối hợp. **Không thêm thủ công một CorsFilter trùng policy** mà không hiểu thứ tự filter chain và việc merge/override; hai bộ phát header khác nhau có thể tạo ra phản hồi khó dự đoán.

## 9. Ứng dụng hoàn chỉnh: Java 17 + Spring Boot 3.3.5

Một project Spring MVC nhỏ, **không bật Spring Security**, để quan sát CORS độc lập với login/CSRF. Sau đó mục 12 trình bày cách tích hợp Spring Security.

~~~text
cors-demo/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/corsdemo/
    │   ├── CorsDemoApplication.java
    │   └── web/
    │       ├── CorsConfig.java
    │       └── ItemController.java
    └── test/java/com/example/corsdemo/web/
        └── ItemCorsTest.java
~~~

### 9.1. Gradle

`settings.gradle`:

~~~groovy
rootProject.name = 'cors-demo'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

`spring-boot-starter-web` chứa Spring MVC và JSON converter cần cho ví dụ. Không có dependency Spring Security, nên **đừng suy luận demo đã bảo vệ API thực tế**. Trong sản phẩm production cần thêm cơ chế auth, authorization, CSRF/rate limiting theo use case.

### 9.2. Main application

`src/main/java/com/example/corsdemo/CorsDemoApplication.java`:

~~~java
package com.example.corsdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CorsDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(CorsDemoApplication.class, args);
    }
}
~~~

Cấu hình `CorsConfig.java` chính là file đầy đủ ở mục 8.2, lưu tại `src/main/java/com/example/corsdemo/web/CorsConfig.java`.

### 9.3. REST controller: GET, POST, PUT

`src/main/java/com/example/corsdemo/web/ItemController.java`:

~~~java
package com.example.corsdemo.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/items")
public class ItemController {

    public record Item(long id, String name) {}
    public record ItemInput(String name) {}

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Item>> list() {
        return ResponseEntity.ok()
                .header("X-Request-Id", "list-1")
                .body(List.of(new Item(1L, "Spring MVC")));
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Item> create(@RequestBody ItemInput input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Request-Id", "create-1")
                .body(new Item(2L, input.name()));
    }

    @PutMapping(
            path = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Item> update(
            @PathVariable("id") long id,
            @RequestBody ItemInput input) {
        return ResponseEntity.ok(new Item(id, input.name()));
    }
}
~~~

Giải thích:
- GET trả danh sách JSON và header `X-Request-Id`. CORS config expose header này cho JS.
- POST nhận JSON rồi tạo representation cố định ID 2; **không lưu database**, nên đây không phải API quản lý dữ liệu hoàn chỉnh.
- PUT chứng minh một method yêu cầu preflight khi gọi từ browser cross-origin.
- Controller không tự đọc header `Origin` và không set `Access-Control-Allow-Origin` bằng tay. Spring MVC CORS mapping chịu trách nhiệm về chính sách.
- `@PostMapping(consumes=application/json)` điều khiển request body/media type; **khác** việc CORS kiểm tra request headers. CORS success không bảo đảm JSON hợp lệ hoặc business action thành công.

## 10. Gọi thử HTTP và phân tích response

Chạy trong project có Gradle wrapper và Java 17:

~~~bash
./gradlew bootRun
~~~

Trên Windows PowerShell/CMD dùng `.\gradlew.bat bootRun`. Phần HTTP local chạy ở `http://localhost:8080`, còn allow-origin trong config là `https://app.example.test`: điều này **vẫn hợp lệ** để kiểm tra server CORS response bằng curl, vì `Origin` là giá trị nguồn do browser/curl mô tả, không cần chạy một trang frontend thực để dùng curl.

### 10.1. Mô phỏng GET simple

~~~bash
curl -i 'http://localhost:8080/api/demo/items' \
  -H 'Origin: https://app.example.test' \
  -H 'Accept: application/json'
~~~

Dự kiến HTTP **200**, body:

~~~json
[{"id":1,"name":"Spring MVC"}]
~~~

Quan sát response headers:
- `Access-Control-Allow-Origin: https://app.example.test`.
- `Access-Control-Expose-Headers: X-Request-Id`.
- `X-Request-Id: list-1`.
- Thường có `Vary: Origin` và các preflight-related Vary values do Spring thêm.

**Không thấy OPTIONS** vì curl không tự preflight như browser. Nếu gọi cùng GET từ browser với điều kiện CORS-safelisted, browser gửi GET trực tiếp.

### 10.2. Mô phỏng OPTIONS preflight cho POST JSON

~~~bash
curl -i -X OPTIONS 'http://localhost:8080/api/demo/items' \
  -H 'Origin: https://app.example.test' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type,x-request-id'
~~~

Với policy `CorsConfig`, dự kiến status 2xx (thường HTTP 200), và:
- `Access-Control-Allow-Origin` là origin được cho phép.
- `Access-Control-Allow-Methods` bao gồm POST.
- `Access-Control-Allow-Headers` bao gồm Content-Type và X-Request-Id.
- `Access-Control-Max-Age` là 600 giây.
- Response có `Vary` thích hợp.

Body preflight thường rỗng. **Không phải controller `create()` đang chạy**; OPTIONS được CORS processor xử lý. Vì vậy không kỳ vọng ID 2 hay JSON item ở response OPTIONS.

### 10.3. Mô phỏng actual POST sau preflight

~~~bash
curl -i -X POST 'http://localhost:8080/api/demo/items' \
  -H 'Origin: https://app.example.test' \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: req-42' \
  -d '{"name":"Spring CORS"}'
~~~

Dự kiến HTTP **201 Created**, body:

~~~json
{"id":2,"name":"Spring CORS"}
~~~

Response có `Access-Control-Allow-Origin`; header `X-Request-Id` do controller tạo ra có giá trị `create-1` và được expose trong CORS config. CORS không buộc server phải echo giá trị `req-42` từ request.

**curl chỉ gửi đúng một POST**, không tự gửi OPTIONS ở trước. Để quan sát hai requests browser gửi thật, cần trang frontend khác origin hoặc DevTools Network. Vì vậy kết quả của curl là phép thử **chính sách CORS ở server**, không chứng minh browser chắc chắn không bị lỗi cookie, CSP, mixed content hay preflight caching.

### 10.4. Origin không được cấp quyền

~~~bash
curl -i -X OPTIONS 'http://localhost:8080/api/demo/items' \
  -H 'Origin: https://evil.example.test' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
~~~

Trong Spring MVC với CORS mapping này, preflight có origin không hợp lệ thường bị từ chối **403 Invalid CORS request**. Browser không gửi actual POST nếu preflight không qua. Đối với request GET cross-origin thực, Spring có thể từ chối 403, trong khi những hệ thống khác có thể trả response không kèm allow-origin rồi để browser chặn đọc.

**Không viết frontend dựa trên body 403 của CORS rejection**, vì browser thường che response khỏi script. Quan sát status và log qua DevTools/server diagnostics.

### 10.5. Không có Origin

~~~bash
curl -i 'http://localhost:8080/api/demo/items'
~~~

Dự kiến vẫn trả HTTP 200 và JSON. Không có request origin thì policy CORS thường không thêm `Access-Control-Allow-Origin`. Điều này không phải lỗ hổng trong CORS; CORS không được thiết kế để ngăn mọi HTTP client ngoài browser gửi request. Authentication/authorization phải hoạt động độc lập.

## 11. MockMvc: kiểm thử cả simple và preflight

`src/test/java/com/example/corsdemo/web/ItemCorsTest.java`:

~~~java
package com.example.corsdemo.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ItemCorsTest {

    private static final String ORIGIN = "https://app.example.test";

    @Autowired
    MockMvc mvc;

    @Test
    void simpleGetHasCorsOriginAndExposesResponseHeader() throws Exception {
        mvc.perform(get("/api/demo/items")
                        .header("Origin", ORIGIN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string(
                        "Access-Control-Expose-Headers", "X-Request-Id"))
                .andExpect(header().string("X-Request-Id", "list-1"))
                .andExpect(jsonPath("$[0].name").value("Spring MVC"));
    }

    @Test
    void preflightOptionsAllowsConfiguredPostAndHeaders() throws Exception {
        mvc.perform(options("/api/demo/items")
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers",
                                "content-type,x-request-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        containsString("POST")))
                .andExpect(header().string("Access-Control-Max-Age", "600"));
    }

    @Test
    void actualJsonPostStillRequiresRealHandler() throws Exception {
        mvc.perform(post("/api/demo/items")
                        .header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Spring CORS\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Spring CORS"));
    }

    @Test
    void rejectedOriginCannotPreflight() throws Exception {
        mvc.perform(options("/api/demo/items")
                        .header("Origin", "https://evil.example.test")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getWithoutOriginDoesNotNeedCorsResponseHeader() throws Exception {
        mvc.perform(get("/api/demo/items"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
~~~

Trong file test, không cần import matcher `equalToIgnoringCase` vì chưa sử dụng; nếu IDE/Checkstyle kiểm tra unused imports, hãy xóa dòng đó. Các assertion còn lại đủ minh họa preflight và actual response. Chạy:

~~~bash
./gradlew test
~~~

MockMvc không mở browser thật và **không tự gửi preflight** khi test dùng `post(...)`. Việc tạo `options(...)` trong test là mô phỏng yêu cầu preflight và kiểm chính sách Spring. Với browser thật cần kiểm tra thêm network stack, cache, cookie policy, origin hiện tại, reverse proxy và security filters. `MockMvc` giúp kiểm những quyết định server, nhưng không thay thế E2E cross-origin test.

### 11.1. Test header permissions có thực sự được trả không?

Test trên chỉ kiểm allow-origin, method và max-age để tránh phụ thuộc format/casing của allow-headers. Hãy bổ sung một assertion **case-insensitive** hoặc tách danh sách header để xác nhận `Content-Type` và `X-Request-Id` trong `Access-Control-Allow-Headers` nếu đây là hợp đồng cần bảo vệ. Header names HTTP không phân biệt hoa/thường; đừng viết test thất bại chỉ vì server trả `content-type` thay vì `Content-Type`.

Với ứng dụng production, test **ít nhất**:
- Origin cho phép và origin từ chối.
- Method cho phép và method từ chối.
- Headers được phép và header không nằm trong allowlist.
- Credentials on/off, wildcard bị chặn đúng.
- Response actual có allow-origin, expose-headers khi cần.
- CORS đi qua Spring Security filter chain trước authentication/CSRF.
- Request không có Origin không bị CORS policy khóa nhầm.

## 12. Khi tích hợp Spring Security

Đối với Spring MVC có Spring Security, CORS phải được tích hợp để preflight không bị authentication challenge vô lý **trước khi** Spring xem CORS policy. Spring Security có `HttpSecurity.cors(...)` và có thể dùng CORS configuration do MVC cung cấp khi có đúng `HandlerMappingIntrospector`/integration và không có nguồn cấu hình CORS khác cạnh tranh.

Ví dụ **fragment** để đưa vào dự án đã có `spring-boot-starter-security`, controller và CorsConfig của mục 8:

~~~java
package com.example.corsdemo.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ApiSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/demo/**").authenticated()
                        .anyRequest().denyAll())
                .build();
    }
}
~~~

Đoạn code là file độc lập nếu đã thêm Spring Security starter, nhưng **không thuộc app runnable không-security ở mục 9**. Việc `authenticated()` ở đây chỉ để minh họa policy thực tế: request POST vẫn chịu CSRF mặc định nếu dùng cookie/session authentication; nếu không có credential phù hợp, actual request sẽ bị từ chối. Không mong tất cả MockMvc tests ở mục 11 vẫn đạt khi thêm class này mà không cấu hình test user/CSRF tokens. Ví dụ JWT resource server có chính sách CSRF/session khác và phải được thiết kế riêng.

**Không cấu hình security kiểu “permitAll OPTIONS” như giải pháp duy nhất** rồi quên CORS: OPTIONS phải được CORS processor kiểm tra origin/method/headers; cho phép tất cả OPTIONS ở security layer không tự làm browser nhận `Access-Control-Allow-Origin` hợp lệ. Ngược lại, CORS preflight được duyệt không làm user authorized cho request thực.

Nếu ứng dụng dùng nhiều `SecurityFilterChain`, một chain có `cors()` không đảm bảo chain khác cũng xử lý đúng route; hãy test đường dẫn thực dưới toàn bộ filter chain.

## 13. Các lỗi CORS phổ biến, phân biệt theo tầng

| Triệu chứng | Nguyên nhân thường gặp | Cách kiểm tra / sửa |
| --- | --- | --- |
| Console báo thiếu `Access-Control-Allow-Origin` | Route/proxy/error response không thêm CORS header | Kiểm Network + actual HTTP response và policy mapping |
| GET bằng curl được, fetch bị chặn | Browser kiểm CORS, curl không | Gửi `Origin` bằng curl và quan sát allow-origin |
| OPTIONS 401/403 trước POST | Security hoặc CORS policy chặn preflight | Kiểm filter ordering, `cors()`, origin, method, headers |
| POST form hoạt động nhưng POST JSON bị CORS | JSON Content-Type kích hoạt preflight | Cấu hình POST và Content-Type trong allowlist |
| GET với Authorization bị preflight | Authorization không safelisted | Cho phép Authorization header có chủ đích; vẫn xác thực actual |
| OPTIONS 200 nhưng POST/PUT không đến | Preflight response thiếu/mismatch allow-origin/method/headers | So `Access-Control-Request-*` với `Access-Control-Allow-*` |
| OPTIONS qua nhưng actual response bị chặn | Actual response không có CORS headers hợp lệ | Kiểm response thực/error handler/reverse proxy |
| Cookie không gửi với `credentials:include` | SameSite, Secure, domain/path, third-party cookie policy | Kiểm cookie attributes và browser policy |
| Có cookie nhưng JavaScript vẫn không đọc body | Thiếu `Access-Control-Allow-Credentials:true` hoặc allow-origin là `*` | Dùng origin cụ thể + credentials true |
| JS không đọc được `X-Request-Id` | Chưa expose response header | `Access-Control-Expose-Headers` |
| `@CrossOrigin` trên controller không áp dụng | Path không match, khác handler, Spring Security trước MVC | Kiểm handler mapping/filter chain |
| CORS vô tình mở mọi origin | `@CrossOrigin` quá rộng hoặc global/local config merge | Dùng explicit allowlist và negative tests |
| OPTIONS response bị redirect | Reverse proxy/auth redirect hoặc URL normalize | Preflight nên trả policy trực tiếp, kiểm gateway |
| DevTools không thấy OPTIONS lần hai | Preflight caching | Kiểm `Access-Control-Max-Age` và browser cache behavior |
| Header `Vary` thiếu khi origin phản hồi động | Cache dùng response sai biến thể | Thiết lập Vary đúng qua CORS processor/proxy |
| HTTP 500 bị báo như CORS error | Error path không thêm allow-origin | Xem server logs/status, xử lý CORS cho error responses đúng policy |
| Browser báo mixed content | HTTPS frontend gọi HTTP backend | Dùng HTTPS; đây không chỉ là CORS |
| Browser báo CSP `connect-src` | CSP của frontend chặn đích | Chỉnh Content Security Policy, không phải CORS ở backend |

### 13.1. Quy trình gỡ lỗi có thứ tự

1. Xác định **origin thực** của trang frontend, bao gồm scheme/host/port. Đừng lẫn origin của API với origin frontend.
2. Trong DevTools Network, tìm request OPTIONS (nếu có) và actual request. Ghi rõ method, URL, `Origin`, `Access-Control-Request-Method` và `Access-Control-Request-Headers`.
3. Với preflight, đối chiếu status và `Access-Control-Allow-Origin/Methods/Headers/Max-Age`. Nếu OPTIONS thất bại, đừng tập trung sửa controller POST trước khi kiểm policy.
4. Với actual response, đối chiếu allow-origin, allow-credentials, expose-headers; xem cookie flags và `fetch(credentials)`.
5. Kiểm mappings `@CrossOrigin` / `CorsRegistry`, có config chồng nhau hay không.
6. Nếu có Spring Security, đảm bảo CORS chạy trước bước auth cần credential; đồng thời kiểm actual permissions/CSRF độc lập.
7. Kiểm ingress/API gateway/CDN có tự xử lý OPTIONS, ghi đè/xóa header, cache sai `Vary` hoặc redirect HTTPS không.
8. Dùng curl với headers tương đương để xác nhận server behavior, rồi trở lại browser kiểm SameSite, mixed content, CSP và preflight cache.
9. Thêm MockMvc test cho allow + deny để regression không âm thầm mở origin hoặc header ngoài ý muốn.
10. Ghi log có kiểm soát route, origin/method/header names và error status; không log Authorization token/cookie/session ID.

## 14. Câu hỏi tự kiểm tra

**1.** Hai URL cùng hostname nhưng khác port có cùng origin không?

**Trả lời:** Không, trừ khi port được diễn giải cùng effective port; port là một phần của origin.

**2.** Vì sao POST JSON cross-origin thường có OPTIONS trong khi POST form urlencoded thường không?

**Trả lời:** `application/json` không nằm trong CORS-safelisted Content-Type. `application/x-www-form-urlencoded` có thể safelisted nếu các điều kiện method/headers khác thỏa.

**3.** Một simple GET không có OPTIONS thì có cần `Access-Control-Allow-Origin` không?

**Trả lời:** Có, để browser cho phép JavaScript đọc response cross-origin. “Không preflight” không đồng nghĩa “bỏ kiểm CORS”.

**4.** Header nào trong OPTIONS nói browser muốn dùng method thật là PUT?

**Trả lời:** `Access-Control-Request-Method: PUT`; còn HTTP method của request hỏi phép vẫn là `OPTIONS`.

**5.** Phân biệt Allow-Headers và Expose-Headers?

**Trả lời:** `Access-Control-Allow-Headers` cho biết header **request** nào được gửi; `Access-Control-Expose-Headers` cho biết header **response** nào script được đọc.

**6.** Có thể dùng `Access-Control-Allow-Origin:*` với cookie-authenticated credentialed CORS không?

**Trả lời:** Không. Với credentialed CORS, response cần origin cụ thể phù hợp và `Access-Control-Allow-Credentials:true`; cookie vẫn chịu SameSite/Secure/browser policy.

**7.** Vì sao `curl -H 'Origin: ...'` thành công không bảo đảm frontend chạy được?

**Trả lời:** curl không triển khai toàn bộ browser CORS enforcement, preflight cache, cookie policy, mixed content và CSP. Nó chỉ giúp quan sát response server.

**8.** Nếu browser preflight không mang cookie nên Spring Security trả 401 thì nên xử lý ở đâu?

**Trả lời:** Tích hợp CORS processor trước yêu cầu authentication, thường với `http.cors(...)` và MVC CORS configuration phù hợp; actual request vẫn phải đi qua security/CSRF rules.

**9.** CORS có bảo vệ khỏi CSRF khi browser đã gửi simple POST không?

**Trả lời:** Không. Simple request có thể được gửi và gây side effect trước khi browser quyết định có cho đọc response. Phải dùng CSRF/authorization controls phù hợp.

## 15. Checklist khi cấu hình CORS trên production

1. Định nghĩa explicit allowed origins có scheme/host/port đúng, không dùng wildcard cho dữ liệu nhạy cảm.
2. Giới hạn allowed methods theo API thực, và allowed request headers theo nhu cầu thực tế.
3. Chỉ bật credentials khi có use case, không kết hợp wildcard origin với credentials.
4. Định nghĩa exposed response headers tối thiểu cần thiết, tránh expose thông tin bí mật.
5. Đặt maxAge hợp lý, hiểu browser có cache giới hạn.
6. Đảm bảo `Vary` đúng khi response phụ thuộc Origin/request method/request headers.
7. Đưa CORS vào đúng filter chain nếu có Spring Security; actual request vẫn cần auth, authorization và CSRF.
8. Cấu hình reverse proxy/CDN/gateway không phá CORS headers, không cache phản hồi từ origin khác.
9. Kiểm cả browser và MockMvc: simple GET, JSON POST preflight, PUT preflight, allowed/denied origin, allowed/denied headers, credentialed flow.
10. Đừng dùng CORS làm substitute cho authentication, tenant validation, network ACL, CSRF hoặc rate limiting.
11. Kiểm URL HTTPS, frontend CSP `connect-src`, cookie SameSite/Secure/third-party policy.
12. Khi lỗi production, đối chiếu OPTIONS và actual request trước khi sửa logic controller.

## 16. Tài liệu chính thức

- [Spring Framework — CORS trong Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html): `@CrossOrigin`, global `WebMvcConfigurer`, credentialed requests, policy defaults và kết hợp local/global.
- [Spring Framework — CorsConfiguration Javadoc](https://docs.spring.io/spring-framework/docs/6.2.0/javadoc-api/org/springframework/web/cors/CorsConfiguration.html): allowed origins/origin patterns, methods, headers, credentials, maxAge và validate wildcard.
- [Spring Security — CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html): xử lý preflight trước authentication và tích hợp `http.cors`.
- [MDN — Cross-Origin Resource Sharing (CORS)](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS): simple/safelisted requests, preflight và giải thích các request/response headers.
- [MDN — Preflight request](https://developer.mozilla.org/en-US/docs/Glossary/Preflight_request): OPTIONS, access-control-request headers và preflight cache.
- [Fetch Standard — CORS Protocol](https://fetch.spec.whatwg.org/#http-cors-protocol): định nghĩa chuẩn về preflight, safelisted request headers, credentials và kiểm CORS.
- [MDN — Set-Cookie, SameSite](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie): cơ chế cookie tách biệt với CORS.
- [Spring Framework — CORS support in HandlerMapping](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html): CORS preflight handling và các điểm cấu hình ở MVC mapping.

**Kết luận cần nhớ:** request *simple* **không có preflight nhưng vẫn phải được browser cho phép đọc response**; request JSON/custom-header/non-safelisted method thường đi qua **OPTIONS preflight** để xin quyền trước actual request. Spring MVC phát header theo `CorsConfiguration`; Spring Security cần tích hợp đúng thứ tự. CORS bảo vệ việc script đọc dữ liệu cross-origin theo browser policy, **không thay authentication, authorization hoặc CSRF**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cors-web-security-003">@CrossOrigin, cấu hình toàn cục và cách kết hợp chính sách</a>

<details>
<summary>Xem chi tiết</summary>

# @CrossOrigin, cấu hình toàn cục và cách kết hợp chính sách trong Spring MVC

## 1. Mục tiêu học tập

Một trang frontend chạy tại `https://app.example.com` gọi API Spring MVC ở `https://api.example.com`. Dù cùng hệ thống nghiệp vụ, đây là **hai origin khác nhau**; JavaScript trong trình duyệt chịu quy tắc **same-origin policy**. CORS (**Cross-Origin Resource Sharing**) là cơ chế HTTP cho phép server quyết định những origin nào được trình duyệt cho phép đọc response và những request cross-origin nào được thực hiện theo cơ chế CORS.

Trong Spring MVC, có hai cấp cấu hình rất dễ bị hiểu nhầm:

- **Cục bộ**: annotation `@CrossOrigin` trên controller hoặc method.
- **Toàn cục**: `WebMvcConfigurer#addCorsMappings` hoặc các cấu hình `CorsConfiguration`/filter tương ứng.

Nếu áp dụng **cả hai**, Spring MVC có quy tắc **kết hợp chính sách** — thường **cộng gộp danh sách** nhưng **ghi đè thuộc tính đơn**. Không được mặc định rằng annotation method luôn “chặt hơn” hoặc “ghi đè hoàn toàn” cấu hình toàn cục.

Bài này giúp bạn:

1. Phân biệt **origin**, **site**, CORS, preflight, actual/simple request và các HTTP header quan trọng.
2. Dùng `@CrossOrigin` ở mức method/class, hiểu mặc định và giới hạn của từng thuộc tính.
3. Dùng `WebMvcConfigurer#addCorsMappings` để đặt chính sách theo URL path.
4. Giải thích chính xác `CorsConfiguration.combine`, additive list, local override scalar, wildcard, `allowedOriginPatterns` và credential rules.
5. Kết hợp Spring MVC CORS với Spring Security đúng thứ tự; phân biệt CORS với authentication, authorization, CSRF, cookie `SameSite` và reverse proxy.
6. Viết ví dụ chạy được bằng **Java 17+, Spring Boot 3.x, Spring Framework 6.x**, cùng MockMvc tests xác minh **preflight**, **actual request**, cấu hình gộp và origin bị từ chối.
7. Chẩn đoán lỗi CORS có hệ thống thay vì thêm `Access-Control-Allow-Origin: *` một cách mù quáng.

**Phạm vi phiên bản:** Spring MVC (Servlet stack), Spring Framework **6.x** và Spring Boot **3.x**. Code demo dùng `spring-boot-starter-web` và không mặc định thêm Spring Security. Với Spring Framework 7/Boot 4, cần kiểm tra Javadoc/API đúng phiên bản trước khi chuyển nguyên ví dụ.

## 2. Xác định origin trước khi phân tích chính sách kết hợp

Đối với HTTP(S), **origin = scheme + hostname + effective port**. Vì vậy `https://app.example.com` và `https://api.example.com` là hai origin khác nhau; path `/api/items` không ảnh hưởng phép so sánh. `http://localhost:3000` và `http://localhost:8080` cũng khác origin vì khác port.

Trong bài này, vấn đề không còn là học lại toàn bộ bảng same-origin, mà là **một handler có thể nhận quyền CORS từ hai nơi**. Ví dụ API tại `https://api.example.com` đặt chính sách toàn cục cho `https://app.example.com`; riêng handler đối tác khai báo thêm `https://partner.example.com`. Câu hỏi phải kiểm thử là danh sách origin được **cộng gộp hay thay thế**, và những thuộc tính như `maxAge` có cùng quy tắc hay không.

| Nguồn chính sách | Ví dụ | Trách nhiệm cần rà soát |
| --- | --- | --- |
| Global `WebMvcConfigurer` | `https://app.example.com` trên `/api/**` | Quyền chung cho nhiều route |
| Local `@CrossOrigin` | `https://partner.example.com` trên một handler | Quyền bổ sung, không mặc định thu hẹp global |
| Spring Security/proxy | Filter chain, gateway hoặc CDN | Chỉ xử lý CORS ở tầng đã được thiết kế, tránh nhiều policy độc lập xung đột |

Hai subdomain có thể **same-site đối với cookie nhưng cross-origin đối với JavaScript**. CORS không chứng thực principal và không ngăn `curl` gọi API; xác thực, phân quyền và CSRF vẫn là các lớp bảo vệ độc lập. Phần kế tiếp dùng actual/preflight requests để xác định cách Spring hợp nhất cấu hình, thay vì chỉ so các URL.

## 3. Hai luồng CORS: actual request và preflight

### 3.1. Actual request

Ví dụ request đọc dữ liệu:

~~~http
GET /api/items HTTP/1.1
Host: api.example.com
Origin: https://app.example.com
~~~

Server có thể trả:

~~~http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: https://app.example.com
Vary: Origin
Content-Type: application/json

[{"id":1,"name":"Book"}]
~~~

`Access-Control-Allow-Origin` quyết định origin được phép đọc response trong điều kiện CORS. `Vary: Origin` giúp cache trung gian biết response header có thể phụ thuộc Origin; các cache/proxy cần tôn trọng đầy đủ `Vary`.

**Không suy ra** có `Access-Control-Allow-Origin` là endpoint được bảo vệ bằng authentication. Nếu không có auth, người ngoài vẫn có thể gọi API trực tiếp, bất kể CORS.

### 3.2. Preflight request

Với request cần preflight, chẳng hạn dùng `PATCH` hoặc request header tùy chỉnh, browser thường gửi **OPTIONS** trước:

~~~http
OPTIONS /api/items/1 HTTP/1.1
Host: api.example.com
Origin: https://app.example.com
Access-Control-Request-Method: PATCH
Access-Control-Request-Headers: content-type,x-request-id
~~~

Server phản hồi nếu chính sách cho phép:

~~~http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: https://app.example.com
Access-Control-Allow-Methods: GET,POST,PATCH
Access-Control-Allow-Headers: content-type,x-request-id
Access-Control-Max-Age: 1800
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
~~~

Các header trên là **một ví dụ cú pháp**, không bảo đảm thứ tự dòng hoặc giá trị của mọi Spring project. `Access-Control-Max-Age` tính bằng **giây**, mô tả thời gian cache kết quả preflight; browser có thể áp giới hạn nhỏ hơn.

Nếu preflight thất bại, browser **không gửi actual request** mà nó định gửi theo quy trình CORS. Vì preflight là OPTIONS do trình duyệt gửi, frontend không cần tự gọi một OPTIONS riêng trước mỗi request.

**Simple request** (thuật ngữ thường gặp) không cần preflight khi thỏa điều kiện về method và các safelisted headers/content types. Ví dụ cross-origin `GET` thông thường có thể được gửi trực tiếp; trình duyệt vẫn kiểm tra response CORS trước khi cho JavaScript đọc. Với `Content-Type: application/json` trên một request POST cross-origin, preflight thường được thực hiện do media type này không nằm trong safelist cho trường hợp đó.

### Các header cần ghi nhớ

| Header | Chiều | Ý nghĩa |
| --- | --- | --- |
| `Origin` | Request | Origin của client theo HTTP/browser |
| `Access-Control-Request-Method` | Preflight request | Actual method dự kiến |
| `Access-Control-Request-Headers` | Preflight request | Các request headers không thuộc safelist dự kiến |
| `Access-Control-Allow-Origin` | Response | Origin được phép đọc theo CORS |
| `Access-Control-Allow-Methods` | Preflight response | Các HTTP methods được chấp nhận |
| `Access-Control-Allow-Headers` | Preflight response | Các request headers được cho phép |
| `Access-Control-Allow-Credentials` | Response | Cho phép credentialed CORS response khi bằng `true` |
| `Access-Control-Expose-Headers` | Actual response | Các response headers bổ sung mà JS được phép đọc |
| `Access-Control-Max-Age` | Preflight response | TTL của preflight permission |
| `Vary` | Response | Cache key phụ thuộc Origin/preflight headers |

`allowedHeaders` dành cho **headers client gửi**; `exposedHeaders` dành cho **headers của response mà JavaScript muốn đọc**. Nhầm hai nhóm này thường làm frontend báo “không đọc được `X-Request-Id`” dù request API trả 200.

## 4. Spring MVC xử lý CORS tại đâu?

Trên Servlet MVC stack, `HandlerMapping` hỗ trợ CORS. Khi một request được map đến controller, Spring có thể lấy `CorsConfiguration` theo URL (toàn cục) và theo `@CrossOrigin` (cục bộ), rồi kết hợp chúng cho handler.

- **Preflight** phù hợp được xử lý trực tiếp theo CORS configuration thay vì gọi business handler.
- **Actual/simple CORS** được kiểm tra rồi bổ sung response headers nếu được phép.
- Nếu không có cấu hình CORS phù hợp, server thường không thêm CORS headers cho actual request; browser sẽ không cho JavaScript đọc. Preflight không có cấu hình được phép thường bị từ chối.
- Nếu Origin không được phép hoặc method/headers của preflight không phù hợp, Spring `DefaultCorsProcessor` có thể trả **403 Invalid CORS request**.
- Nếu request **cùng origin**, CORS không phải tầng kiểm tra quyền tương ứng; controller vẫn được gọi bình thường theo security/business policies.

**Đặc biệt:** CORS hoạt động chủ yếu ở **rìa trình duyệt và MVC request mapping**. Nó không tự bảo vệ file static ở CDN, gateway hoặc endpoint không qua cùng HandlerMapping; những đường response khác phải được cấu hình CORS ở đúng tầng phục vụ chúng.

## 5. `@CrossOrigin` ở mức method và class

Một method REST:

~~~java
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    @CrossOrigin(origins = "https://app.example.com")
    @GetMapping("/items")
    public List<Item> items() {
        return List.of(new Item(1L, "Book"));
    }

    public record Item(long id, String name) {}
}
~~~

**Đây là đoạn minh họa controller**, cần import và file riêng. `@CrossOrigin` áp dụng cho handler `GET /api/catalog/items`; method khác trên cùng class không tự nhận annotation này.

Annotation có thể đặt trên class:

~~~java
@CrossOrigin(
        origins = {"https://app.example.com", "https://admin.example.com"},
        maxAge = 3600)
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    // Handler methods phù hợp trong class này dùng cấu hình CORS.
}
~~~

Đây cũng là ví dụ rút gọn. `@CrossOrigin` ở class level cung cấp cấu hình cho nhiều handler, method level có thể thêm customizations và chúng cũng được **combine**, không đơn thuần xóa sạch chính sách ở class.

### Mặc định nguy hiểm nếu không để ý

~~~java
@CrossOrigin
@GetMapping("/items")
public List<Item> items() {
    return service.findAll();
}
~~~

Đây là method minh họa. `@CrossOrigin` **không tham số** mặc định cho phép **mọi origin** và **mọi header**, các HTTP method được map đến handler, preflight maxAge thông dụng là **1800 giây**, và **không cho phép credentials mặc định**. Nó dễ giúp demo frontend chạy được, nhưng cũng có thể cấp khả năng đọc API public cho bất kỳ website nào. Không dùng `@CrossOrigin` trống trên API chứa dữ liệu nhạy cảm chỉ vì muốn “sửa CORS nhanh”.

### Những thuộc tính quan trọng

| `@CrossOrigin` | Ý nghĩa |
| --- | --- |
| `origins` / `value` | Danh sách origin cụ thể, ví dụ `https://app.example.com` |
| `originPatterns` | Pattern origin linh hoạt hơn, chỉ dùng khi cần và có giới hạn |
| `methods` | Các HTTP methods cho CORS; lưu ý mapping method của handler |
| `allowedHeaders` | Request headers có thể được trình duyệt gửi sau preflight |
| `exposedHeaders` | Response headers JS được phép đọc thêm |
| `allowCredentials` | Chuỗi `"true"`/`"false"` trong annotation của Spring Framework 6.x |
| `maxAge` | Cache TTL của preflight, đơn vị giây |

`@CrossOrigin(allowCredentials = "true")` dùng **String** ở annotation theo API Spring 6.x, không phải boolean Java literal. Khi bật credentials, `origins = "*"` bị từ chối vì không thể đáp ứng chuẩn CORS credentialed response. Hãy chỉ định origin cụ thể hoặc pattern được kiểm soát phù hợp.

## 6. Cấu hình CORS toàn cục bằng `WebMvcConfigurer`

Ví dụ Spring Framework 6.x/Spring Boot 3.x:

~~~java
@Configuration
public class ApiCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "https://app.example.com",
                        "https://admin.example.com")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type", "Authorization")
                .exposedHeaders("X-Request-Id")
                .allowCredentials(true)
                .maxAge(600);
    }
}
~~~

Đây là **class minh họa**, cần import `Configuration`, `WebMvcConfigurer`, `CorsRegistry`. Nó áp dụng cho những URL theo pattern `/api/**` do MVC xử lý; các route ngoài pattern không tự được cấp chính sách này.

**Lưu ý về credentials:** chỉ bật `allowCredentials(true)` nếu frontend thật sự gửi cookies, HTTP authentication hoặc credentials cần thiết và trust boundary phù hợp. `Authorization: Bearer ...` là một request header cần được cho phép qua preflight, nhưng **không mặc định phải bật cookie credentials** chỉ vì endpoint dùng bearer token. Hãy đánh giá cách frontend gửi auth và quy tắc Fetch cụ thể.

### Mặc định của global registration

Nếu gọi `registry.addMapping("/api/**")` mà không hạn chế thêm, cấu hình mặc định thường cho phép **mọi origin**, **mọi header**, **GET/HEAD/POST**, `maxAge=1800` giây, **không bật credentials**. Vì vậy cần đặt `allowedOrigins` tường minh cho ứng dụng có dữ liệu không public.

Trong ứng dụng Spring Boot thông thường, `WebMvcConfigurer` là cách mở rộng cấu hình MVC mà **không** cần `@EnableWebMvc`. Thêm `@EnableWebMvc` trong Boot chỉ để bật CORS là không cần thiết và có thể làm mất nhiều hành vi Web MVC auto-configuration hữu ích.

### Local và global không tự tách biệt

Nếu cả global `/api/**` và `@CrossOrigin` trên method đều khớp, Spring kết hợp **cả hai cấu hình**. Bạn không thể chỉ nhìn method annotation rồi kết luận chính sách cuối cùng mà browser sẽ thấy. Cần xem mọi config hoạt động, kể cả CORS filter, Spring Security và gateway.

## 7. Cơ chế kết hợp `CorsConfiguration.combine`: phần trọng tâm

Theo Spring MVC 6.x, khi **global và local đều áp dụng**, cấu hình được kết hợp theo quy tắc của `CorsConfiguration#combine`.

### 7.1. List-valued properties thường cộng gộp

Các trường dạng danh sách như **`allowedOrigins`**, **`allowedOriginPatterns`**, **`allowedMethods`**, **`allowedHeaders`**, **`exposedHeaders`** thường kết hợp theo hướng **additive / union**.

Giả sử:

- Global `allowedOrigins = [https://app.example.com]`.
- Method `@CrossOrigin(origins = "https://partner.example.com")`.

Kết quả cho endpoint đó có thể cho phép **cả** `https://app.example.com` **và** `https://partner.example.com`. Annotation method **mở rộng** nhóm origin so với global, **không** biến nó thành “chỉ partner”.

Với headers:

- Global `allowedHeaders = [Content-Type]`.
- Local `allowedHeaders = [X-Partner-Key]`.
- Kết hợp: request có thể dùng cả `Content-Type` và `X-Partner-Key` khi những điều kiện khác cho phép.

Tương tự, `exposedHeaders` của global và local được cộng gộp; frontend có thể đọc cả những response headers được liệt kê nếu response thực sự có chúng.

**Wildcard `"*"` đặc biệt:** nếu một danh sách được kết hợp chứa wildcard có nghĩa cho phép tất cả, kết quả có thể vẫn là wildcard, thay vì “thu hẹp” theo danh sách cụ thể. Các giá trị mặc định được `applyPermitDefaultValues()` có xử lý riêng khi gặp những giá trị explicit. Vì vậy không dựa vào khẩu hiệu “local always overrides” hoặc “global always wins”; phải nhìn kết quả sau combine và test thực tế.

### 7.2. Scalar properties: local override global

Những thuộc tính chỉ nhận một giá trị như:

- `allowCredentials`
- `maxAge`
- `allowPrivateNetwork` khi sử dụng trong phiên bản Spring tương ứng

được kết hợp theo hướng **giá trị local không-null ghi đè global**. Ví dụ:

- Global `maxAge = 600`.
- Method `@CrossOrigin(maxAge = 1200)`.
- Endpoint method có preflight `Access-Control-Max-Age: 1200` trong trường hợp hợp lệ.

Còn nếu method không khai báo `allowCredentials`, nó thường kế thừa chính sách từ global thay vì tự động đổi ngược giá trị. Hãy phân biệt thuộc tính explicit và giá trị `null`/không chỉ định.

### 7.3. Bảng quyết định

| Thuộc tính | Global | Local | Cách hiểu khi combine |
| --- | --- | --- | --- |
| origins | `app.example.com` | `partner.example.com` | Cho phép cả hai origin nếu không có wildcard khác |
| allowedMethods | GET, POST | PATCH | Thường hợp thành GET, POST, PATCH, song còn phụ thuộc handler mapping |
| allowedHeaders | Content-Type | X-Partner-Key | Cả hai tên header |
| exposedHeaders | X-Trace-Global | X-Trace-Partner | Cả hai tên response header |
| maxAge | 600 | 1200 | 1200 (local ghi đè) |
| allowCredentials | false | true | true (local ghi đè nếu được khai báo rõ) |

**Cảnh báo:** Bảng minh họa quy tắc `CorsConfiguration` chứ không khẳng định mỗi HTTP method có business handler tương ứng. CORS `allowedMethods` không tự tạo `@PatchMapping` và không cấp quyền update ở tầng security. Nếu một method không có route tương ứng, request vẫn có thể bị 404/405 hoặc security chặn.

### 7.4. Trường hợp không thể “thu hẹp” global bằng annotation method

Giả sử global áp dụng `allowedOrigins("*")` và local chỉ định một domain đáng tin, bạn **không thể coi method đã thu hẹp** tất cả các origin được phép. Cấu hình hiệu lực có thể vẫn rất rộng sau combine. Với cookie credentials, còn có nguy cơ cấu hình không hợp lệ nếu wildcard trộn với `allowCredentials(true)`.

**Cách sửa đúng** là làm global policy đủ hẹp từ đầu, chọn mapping chính xác (ví dụ `/api/public/**` và `/api/private/**`), hoặc đưa policy vào một `CorsConfigurationSource` duy nhất khi cần kiểm soát tuyệt đối. Không dùng annotation local như “deny rule” đối với một global `*"`.

## 8. Wildcard, `allowedOriginPatterns` và credentials

### 8.1. `allowedOrigins("*")` khi không dùng credentials

Một API tài nguyên hoàn toàn public có thể chọn `Access-Control-Allow-Origin: *` khi dữ liệu thực sự được mọi browser origin đọc và không dùng credentials. Đây là **quyết định product/security** cần làm có chủ đích, không phải cấu hình “chống mọi lỗi CORS”.

### 8.2. Không dùng `*"` với `allowCredentials(true)`

Browser không chấp nhận credentialed CORS response khi `Access-Control-Allow-Origin: *`. Spring MVC 6.x cũng kiểm tra và từ chối tổ hợp không hợp lệ giữa `allowedOrigins("*")` với `allowCredentials(true)`.

Đúng hơn:

~~~java
registry.addMapping("/api/private/**")
        .allowedOrigins("https://app.example.com")
        .allowedMethods("GET", "POST")
        .allowCredentials(true);
~~~

Đây là **đoạn minh họa** bên trong `addCorsMappings`, chưa phải class Java độc lập. Nếu dùng cookie session, phải quản lý CSRF và cookie scope/SameSite bên cạnh CORS.

### 8.3. `allowedOriginPatterns` cho những subdomain được kiểm soát

~~~java
registry.addMapping("/api/tenant/**")
        .allowedOriginPatterns("https://*.trusted.example.com")
        .allowedMethods("GET")
        .allowCredentials(true);
~~~

Trong trường hợp pattern phù hợp, Spring trả **origin đã khớp** vào `Access-Control-Allow-Origin`, không trả ký tự pattern hay `*"`. Điều này cho phép một số cấu hình credentialed CORS với pattern trong giới hạn trust boundary.

Tuy nhiên, `https://*.trusted.example.com` **không tự chứng minh mọi subdomain đều đáng tin**: subdomain do user kiểm soát, subdomain takeover hoặc DNS bị quản lý kém đều có thể mở rộng attack surface. Nếu có một danh sách tenant hữu hạn, allowlist origin cụ thể và quản lý bằng config là lựa chọn dễ audit hơn. Kiểm tra cả scheme và port, không cho `http` chỉ vì muốn tương thích dev.

### 8.4. Chính sách cache và exposed headers

`Access-Control-Max-Age` cho browser cache preflight, nhưng nhiều browser có giới hạn riêng. Nó **không** nói client/cache có thể lưu data của API lâu bao nhiêu; data caching thuộc `Cache-Control`, ETag và cơ chế HTTP caching.

`Access-Control-Expose-Headers` điều khiển response headers được JavaScript đọc (ngoài safelisted response headers). Đừng để lộ token nhạy cảm trong response header rồi “bảo vệ” bằng cách không expose: browser/script cùng origin và nhiều HTTP client khác vẫn có thể đọc; server vẫn phải thiết kế bảo mật đúng.

## 9. Ví dụ Spring Boot 3.x: global + local thật sự combine

Ta xây demo dễ kiểm chứng:

- **Global:** mọi route `/api/**` cho phép origin `https://app.example.com`, method GET/POST, headers `Content-Type` và `X-Requested-With`, exposed header `X-Global-Trace`, preflight maxAge **600**.
- **Local:** handler `GET /api/demo/partner` thêm origin `https://partner.example.com`, thêm request header `X-Partner-Key`, thêm exposed header `X-Partner-Trace`, và maxAge **1200**.
- **Kết quả:** endpoint `/api/demo/global` chỉ cho `app.example.com` từ global. Endpoint `/api/demo/partner` cho **app + partner** vì origins cộng gộp; cả `X-Global-Trace` và `X-Partner-Trace` được expose, và maxAge local là 1200.

Dự án Maven **Java 17+**, **Spring Boot 3.x**; dependencies **Spring Web** và **Spring Boot Starter Test**. Tạo bằng Spring Initializr, package `com.example.demo`.

Các file:

~~~text
src/main/java/com/example/demo/DemoApplication.java
src/main/java/com/example/demo/GlobalCorsConfig.java
src/main/java/com/example/demo/CorsDemoController.java
src/test/java/com/example/demo/CorsPolicyTest.java
~~~

### 9.1. `DemoApplication.java`

~~~java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
~~~

### 9.2. `GlobalCorsConfig.java`

~~~java
package com.example.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GlobalCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("https://app.example.com")
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "X-Requested-With")
                .exposedHeaders("X-Global-Trace")
                .allowCredentials(false)
                .maxAge(600);
    }
}
~~~

### 9.3. `CorsDemoController.java`

~~~java
package com.example.demo;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class CorsDemoController {

    @GetMapping("/global")
    public ResponseEntity<Map<String, String>> globalOnly() {
        return ResponseEntity.ok()
                .header("X-Global-Trace", "global-001")
                .body(Map.of("scope", "global"));
    }

    @CrossOrigin(
            origins = "https://partner.example.com",
            allowedHeaders = "X-Partner-Key",
            exposedHeaders = "X-Partner-Trace",
            maxAge = 1200)
    @GetMapping("/partner")
    public ResponseEntity<Map<String, String>> partner() {
        return ResponseEntity.ok()
                .header("X-Global-Trace", "global-002")
                .header("X-Partner-Trace", "partner-002")
                .body(Map.of("scope", "global-plus-local"));
    }
}
~~~

Mã trên chạy trong `@RestController` Spring Boot 3.x thông thường, không có Spring Security. Để chứng minh tính cộng gộp, tuyệt đối **không sửa global thành `allowedOrigins("*")`** rồi kỳ vọng local thu hẹp quyền; làm vậy sẽ thay đổi hoàn toàn nội dung bài test và có thể mở quá rộng CORS.

### 9.4. Vì sao cấu hình này khác với hai policy độc lập?

`/api/demo/global` không mang annotation, chỉ có global policy.

`/api/demo/partner` nằm trong `/api/**` nên **nhận global policy**, đồng thời có local annotation. Spring MVC kết hợp cả hai:

~~~text
/ api / demo / partner
        |
        +--- global (/api/**)
        |       allowedOrigins: app.example.com
        |       allowedHeaders: Content-Type, X-Requested-With
        |       exposedHeaders: X-Global-Trace
        |       maxAge: 600
        |
        +--- local (@CrossOrigin trên method)
                allowedOrigins: partner.example.com
                allowedHeaders: X-Partner-Key
                exposedHeaders: X-Partner-Trace
                maxAge: 1200
        |
        v
  effective CORS policy:
    origins: app + partner
    headers: Content-Type + X-Requested-With + X-Partner-Key
    exposed: X-Global-Trace + X-Partner-Trace
    maxAge: 1200
~~~

Đoạn trên là sơ đồ giải thích, không phải file cấu hình. Nó nhấn mạnh **không có phép AND tự động** giữa origin toàn cục và origin cục bộ.

## 10. Kiểm thử CORS bằng MockMvc

Với Spring Boot 3.x và `spring-boot-starter-test`, file **`src/test/java/com/example/demo/CorsPolicyTest.java`**:

~~~java
package com.example.demo;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CorsPolicyTest {

    private static final String APP = "https://app.example.com";
    private static final String PARTNER = "https://partner.example.com";

    @Autowired
    MockMvc mockMvc;

    @Test
    void globalEndpointAllowsConfiguredOrigin() throws Exception {
        mockMvc.perform(get("/api/demo/global")
                        .header(HttpHeaders.ORIGIN, APP))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.header()
                        .string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, APP))
                .andExpect(jsonPath("$.scope").value("global"));
    }

    @Test
    void localEndpointAlsoAllowsGlobalOrigin() throws Exception {
        mockMvc.perform(get("/api/demo/partner")
                        .header(HttpHeaders.ORIGIN, APP))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.header()
                        .string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, APP));
    }

    @Test
    void partnerOriginIsAddedOnlyForLocalEndpoint() throws Exception {
        mockMvc.perform(get("/api/demo/partner")
                        .header(HttpHeaders.ORIGIN, PARTNER))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.header()
                        .string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                PARTNER))
                .andExpect(jsonPath("$.scope")
                        .value("global-plus-local"));
    }

    @Test
    void globalOnlyEndpointRejectsPartnerPreflight() throws Exception {
        mockMvc.perform(options("/api/demo/global")
                        .header(HttpHeaders.ORIGIN, PARTNER)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void partnerPreflightCombinesHeadersAndOverridesMaxAge()
            throws Exception {
        MvcResult response = mockMvc.perform(
                        options("/api/demo/partner")
                                .header(HttpHeaders.ORIGIN, PARTNER)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET")
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "X-Partner-Key"))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(PARTNER, response.getResponse().getHeader(
                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("1200", response.getResponse().getHeader(
                HttpHeaders.ACCESS_CONTROL_MAX_AGE));

        String allowedHeaders = response.getResponse().getHeader(
                HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
        assertNotNull(allowedHeaders);
        assertTrue(allowedHeaders.toLowerCase(Locale.ROOT)
                .contains("x-partner-key"));
    }

    @Test
    void localEndpointExposesBothResponseHeaders() throws Exception {
        MvcResult response = mockMvc.perform(
                        get("/api/demo/partner")
                                .header(HttpHeaders.ORIGIN, PARTNER))
                .andExpect(status().isOk())
                .andReturn();

        String exposed = response.getResponse().getHeader(
                HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        assertNotNull(exposed);
        assertTrue(exposed.contains("X-Global-Trace"));
        assertTrue(exposed.contains("X-Partner-Trace"));
    }

    @Test
    void globalPreflightUsesGlobalMaxAge() throws Exception {
        MvcResult response = mockMvc.perform(
                        options("/api/demo/global")
                                .header(HttpHeaders.ORIGIN, APP)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET")
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "X-Requested-With"))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("600", response.getResponse().getHeader(
                HttpHeaders.ACCESS_CONTROL_MAX_AGE));
    }
}
~~~

Chạy:

~~~bash
./mvnw test
~~~

**Lưu ý về code:** trong ví dụ dùng lời gọi đầy đủ `MockMvcResultMatchers.header()` để không nhập nhằng `HttpHeaders`. Có thể dùng static import `header` để code ngắn hơn. Package/class đã đặt chính xác để test có thể tìm Spring Boot application context ở package gốc.

Các test thể hiện:

- Một origin global được phép truy cập cả endpoint chỉ global và endpoint có local annotation.
- Origin partner chỉ được cộng thêm tại handler được annotate.
- Preflight local có `Access-Control-Max-Age: 1200`, còn global dùng 600.
- Request header `X-Partner-Key` được cho phép trong preflight local.
- `exposedHeaders` từ global và local được union.
- Preflight từ partner tới endpoint global-only bị Spring chặn, thường với HTTP 403.

**Giới hạn:** MockMvc kiểm chứng chính sách mà Spring MVC tạo ra; nó **không chạy thuật toán CORS của một browser thật**, không thực hiện cookie store, không kiểm tra CDN/gateway và không đo vòng đời preflight cache trong Chrome/Firefox. Đối với production, bổ sung browser end-to-end test dưới các origin HTTPS thực và quan sát Network panel.

## 11. Kiểm tra CORS bằng curl mà không cần frontend

### 11.1. Actual request từ origin toàn cục

~~~bash
curl -i "http://localhost:8080/api/demo/global"   -H "Origin: https://app.example.com"
~~~

Status kỳ vọng **200 OK**, với:

~~~http
Access-Control-Allow-Origin: https://app.example.com
~~~

### 11.2. Actual request từ origin partner đến method local

~~~bash
curl -i "http://localhost:8080/api/demo/partner"   -H "Origin: https://partner.example.com"
~~~

Status kỳ vọng **200 OK**, với các header:

~~~http
Access-Control-Allow-Origin: https://partner.example.com
Access-Control-Expose-Headers: X-Global-Trace, X-Partner-Trace
~~~

Thứ tự hai exposed headers và cách format có thể khác nhau; nên so token header thay vì so thứ tự cứng.

### 11.3. Preflight cho header tùy chỉnh

~~~bash
curl -i -X OPTIONS   "http://localhost:8080/api/demo/partner"   -H "Origin: https://partner.example.com"   -H "Access-Control-Request-Method: GET"   -H "Access-Control-Request-Headers: X-Partner-Key"
~~~

Kết quả kỳ vọng status **200 OK** cùng `Access-Control-Allow-Origin: https://partner.example.com`, `Access-Control-Allow-Headers` chứa `X-Partner-Key`, và `Access-Control-Max-Age: 1200`. Đây là **preflight**; business method `partner()` không cần chạy để trả response OPTIONS.

### 11.4. Origin không được phép

~~~bash
curl -i -X OPTIONS   "http://localhost:8080/api/demo/global"   -H "Origin: https://partner.example.com"   -H "Access-Control-Request-Method: GET"
~~~

Với `DefaultCorsProcessor` và cấu hình trên, status thường **403 Forbidden**. `curl` vẫn đọc response bất kể CORS; trình duyệt chặn phía script theo cơ chế CORS.

Trên Windows PowerShell, dùng `curl.exe`, tránh alias `curl` và xử lý line continuation theo shell. Các dòng trên dùng `\` của Linux/macOS/Git Bash.

## 12. Tích hợp Spring Security: CORS cần chạy trước authentication

Khi ứng dụng có **Spring Security 6.x** (thường đi với Boot 3.x), preflight OPTIONS thông thường **không mang session cookie**. Nếu security filter yêu cầu authentication trước khi xử lý CORS, browser có thể thấy OPTIONS bị trả 401/403 và không bao giờ gửi actual request.

Spring Security cung cấp hỗ trợ CORS trong security filter chain. Nếu dùng cấu hình MVC `WebMvcConfigurer` và không tự khai báo `CorsConfigurationSource`, có thể cho Security dùng cấu hình của MVC:

~~~java
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll());
        return http.build();
    }
}
~~~

**Đây là class minh họa cho project có thêm dependency `spring-boot-starter-security`**, cần import `@Configuration`, `@Bean`, `SecurityFilterChain`, `HttpSecurity`, `Customizer`. Authentication provider/login/session strategy cần được cấu hình phù hợp với ứng dụng thật. Đừng sao chép code này vào demo phần 9 nếu chỉ muốn kiểm thử CORS thuần MVC.

Nếu ứng dụng cần một `CorsConfigurationSource` riêng, có thể đăng ký `UrlBasedCorsConfigurationSource` và cho Security dùng nguồn đó. Nhưng **không** cấu hình nhiều `CorsFilter`/nguồn CORS khác nhau một cách không chủ đích: response có thể có header CORS trùng hoặc chính sách ở Security/filter không tương ứng với annotation method.

### Lựa chọn đường tích hợp

| Kiến trúc | Cách bắt đầu |
| --- | --- |
| Spring MVC thuần, không Security | `@CrossOrigin` và/hoặc `WebMvcConfigurer` |
| Boot 3 + Spring Security 6 và MVC CORS đã cấu hình | `http.cors(Customizer.withDefaults())` dùng MVC khi phù hợp |
| Security nhiều filter chains, policy khác nhau | Chỉ định `CorsConfigurationSource` tương ứng từng chain |
| CORS cần xử lý trước MVC hoặc cho endpoint không qua handler | `CorsFilter`/gateway với ownership rõ |
| Reverse proxy hoặc API gateway sở hữu CORS | Cấu hình ở gateway và tránh duplicate/inconsistent policy tại ứng dụng |

Dù Spring Security xử lý preflight đúng, **actual request vẫn phải thỏa authentication và authorization**. Việc preflight OK không cho phép user bỏ qua security.

## 13. Credentialed requests, cookie SameSite và CSRF

Frontend có thể gọi API kèm cookie khi đúng policy:

~~~javascript
const response = await fetch("https://api.example.com/api/me", {
  method: "GET",
  credentials: "include"
});

if (!response.ok) {
  throw new Error("HTTP " + response.status);
}
const user = await response.json();
~~~

Để browser cho script đọc credentialed CORS response, backend cần trả **origin cụ thể** và `Access-Control-Allow-Credentials: true`. Thêm `credentials: "include"` **không** làm cookie bị browser cấm tự nhiên xuất hiện:
- Cookie còn bị giới hạn bởi `Domain`, `Path`, `Secure`, expiry và `SameSite`.
- Một số browser chặn third-party cookies và có partitioning/tracking protection.
- Cross-origin nhưng same-site có hành vi cookie khác cross-site.
- Preflight không phải một request được ủy quyền chỉ nhờ client muốn gửi credentials.

**SameSite** giới hạn việc browser gửi cookie theo quan hệ site. **CORS** giới hạn khả năng JavaScript cross-origin tương tác/đọc response theo chính sách server. **CSRF** là vấn đề request giả mạo khi browser có thể tự gửi cookie auth. CORS **không thay thế CSRF token**, cũng không chặn mọi HTML form hoặc điều hướng cross-site có side effect. Với cookie-based authentication, dùng CSRF protection phù hợp, phân quyền từng operation và tuân thủ safe method semantics.

**Origin allowlist phải gắn với trust boundary:** nếu bạn cho phép credentialed CORS từ một origin do bên thứ ba điều khiển, script tại origin đó có thể truy cập dữ liệu mà credentialed user được phép và CORS expose. Không thêm một origin chưa được kiểm soát chỉ vì nó thuộc cùng tổ chức trên danh nghĩa.

## 14. Khi combine làm chính sách rộng hơn dự kiến

### 14.1. Global quá rộng

~~~java
registry.addMapping("/**")
        .allowedOrigins("*")
        .allowedMethods("*");
~~~

Đây là cấu hình **minh họa phản diện** cho ứng dụng có dữ liệu riêng tư. Nếu sau đó một method gắn `@CrossOrigin(origins="https://trusted.example.com")`, bạn không được giả định endpoint chỉ còn cho trusted origin; list combine có thể giữ quyền cho mọi origin.

**Cách sửa:** dùng policy global đủ hẹp và tách route public/private; tránh `**` nếu chỉ `/api/public/**` cần CORS mở.

### 14.2. Local bật credentials ngoài ý định global

~~~java
@CrossOrigin(
        origins = "https://partner.example.com",
        allowCredentials = "true")
@GetMapping("/private")
public SecretView privateData() {
    // ...
}
~~~

Đây là **handler minh họa không độc lập** và cần authentication/authorization. `allowCredentials` là scalar, local explicit có thể override global. Nếu global origins rộng, sự kết hợp có thể trở nên không hợp lệ hoặc nguy hiểm. Kiểm tra effective CorsConfiguration và test không cho origin ngoài allowlist nhận dữ liệu private.

### 14.3. Wildcard headers và methods

Nếu cấu hình cho phép `allowedHeaders("*")`, Spring có thể phản ánh những request header cụ thể trong preflight response, đặc biệt khi credentials được bật; không được hiểu mọi header đều tự nhiên an toàn. Header như `Authorization`, `X-Admin-Key` cần chính sách ứng dụng và xác thực riêng.

`allowedMethods("*")` cũng không tự bảo đảm controller nhận mọi HTTP method, nhưng khiến CORS gate rộng hơn cần thiết. Trong API có mutating methods, chỉ cho phép những method thực sự cần.

### 14.4. Trộn `CorsFilter`, `@CrossOrigin` và gateway

Một reverse proxy có thể thêm ACAO `*` trong khi Spring MVC trả ACAO origin cụ thể. Browser có thể coi response có **nhiều Access-Control-Allow-Origin** là không hợp lệ. Không tự sửa bằng cách add thêm header tại controller; cần xác định **một tầng chủ sở hữu** chính sách CORS hoặc cấu hình nhất quán, tránh trùng.

## 15. CORS không thay authorization: minh họa đe dọa

Giả sử API `GET /api/admin/reports` trả báo cáo nhạy cảm. Nếu chỉ cấu hình CORS để cho origin nội bộ mà **không** yêu cầu role, một người dùng có thể gọi endpoint trực tiếp qua HTTP client. CORS chỉ là browser permission, không phải ACL.

Ngược lại, API đã có authentication vẫn có thể bị truy cập cross-origin từ script ở origin được cho phép. Nếu `allowCredentials(true)` và attacker kiểm soát một origin nằm trong allowlist, attacker có thể lợi dụng khả năng đọc response theo quyền người dùng bị đăng nhập. Vì vậy chọn origin là một phần của threat modeling.

Bảo vệ đúng cần kết hợp:
1. Authentication và authorization ở server.
2. CORS allowlist tối thiểu cho frontend thật.
3. CSRF protection khi auth dựa vào cookie và các thao tác thay đổi trạng thái.
4. Cookie flags `Secure`, `HttpOnly`, `SameSite` theo use case.
5. Hạn chế XSS trong frontend, vì script chạy tại origin đáng tin có thể dùng chính quyền CORS của origin đó.
6. Logging/tracing không làm lộ access token, session cookie hoặc dữ liệu nhạy cảm.

## 16. Khi gặp lỗi CORS: quy trình kiểm tra

Đừng chỉ dựa vào dòng console “blocked by CORS policy”. Cần xem **Network panel** và đối chiếu các lớp:

| Hiện tượng | Nguyên nhân có thể | Hướng xác minh |
| --- | --- | --- |
| OPTIONS trả 401/403 trước handler | Spring Security/filter chặn preflight | Cấu hình `http.cors(...)`, thứ tự filter, logs |
| OPTIONS 403 Invalid CORS request | Origin/method/headers không được cấp | Đối chiếu effective config sau combine |
| GET 200 nhưng JS không đọc được body | Thiếu `Access-Control-Allow-Origin`, credentials mismatch, CORS header trùng | Inspect actual response headers |
| API được dùng từ Postman nhưng browser lỗi | HTTP client không thực thi same-origin policy của browser | Gửi `Origin` và preflight để chẩn đoán server |
| Cookie không gửi | `credentials`, SameSite, Secure, Domain/Path, third-party policy | DevTools Cookies và Request Headers |
| `Access-Control-Allow-Origin: *` với credentials | Tổ hợp bị browser từ chối | Dùng allowlist origin cụ thể và ACAC true |
| Request header X-Token bị từ chối | Không nằm trong `allowedHeaders` hoặc preflight mismatch | Xem `Access-Control-Request-Headers` |
| JS không đọc được `X-Request-Id` | Chưa có `exposedHeaders` | Set `Access-Control-Expose-Headers` |
| Policy local “không thu hẹp” global | Combine origin/method/header theo union | Xem toàn cục + local, thu hẹp global |
| Chỉ production bị lỗi | Gateway/CDN thêm/trừ header, proxy rewrite, HTTPS redirect | So response tại backend và gateway |
| Cache trả ACAO của origin khác | CDN bỏ qua `Vary: Origin` | Cấu hình cache key/Vary đúng |
| Server trả redirect trong preflight | HTTP→HTTPS hoặc login redirect, browser xử lý khó thống nhất | Dùng URL HTTPS/API route đúng trước, xử lý auth sau CORS |
| Origin đúng nhưng port sai | Origin gồm cả scheme, host và port | So header Origin chính xác, không chỉ hostname |

### Debug bảy bước

1. Mở DevTools → **Network** → tìm OPTIONS preflight và actual request; so Origin, method, custom headers.
2. Kiểm tra response của preflight: status, ACAO, Allow-Methods, Allow-Headers, Max-Age. 401/403 có thể đến từ security/gateway khác Spring MVC.
3. Kiểm tra response actual: ACAO, ACAC, exposed headers và Vary. Xác minh không có nhiều ACAO trái nhau.
4. Đọc global path mapping `/api/**`, class-level/method-level `@CrossOrigin`, combine rules và cấu hình Security.
5. Kiểm tra frontend `fetch`: `credentials`, URL, method, Content-Type, headers, cookie policy và redirect.
6. Nếu sau proxy/CDN mới lỗi, gọi trực tiếp backend (trong môi trường được phép) và so headers với gateway.
7. Viết MockMvc tests cho **allowed origin**, **disallowed origin**, preflight **method/header**, credentials và **cấu hình kết hợp** trước khi triển khai lại.

**Không nên** xử lý bằng `@CrossOrigin(origins = "*")` trên toàn bộ controller hoặc bằng cách vô hiệu Spring Security CORS/CSRF. Các thay đổi đó có thể làm browser ngừng báo lỗi nhưng mở rộng bề mặt tấn công.

## 17. Câu hỏi ôn tập

**1. `@CrossOrigin(origins="https://partner.example.com")` đặt trên method có xóa danh sách origin toàn cục không?**

Không mặc định. Spring MVC 6.x kết hợp các danh sách origins theo hướng cộng gộp. Nếu global cho `app.example.com`, method có thể cho cả app và partner.

**2. Thuộc tính `maxAge` giữa global và local được kết hợp thế nào?**

Đây là scalar property: nếu local khai báo non-null thì local override global. Global 600 + local 1200 cho method → preflight maxAge 1200.

**3. Tại sao preflight OPTIONS thường không có session cookie?**

Preflight là bước kiểm tra chính sách browser, không tự gửi credential như một actual authenticated request. Security phải cho CORS được xử lý trước auth khi áp dụng.

**4. CORS có bảo vệ được API khi gọi từ curl không?**

Không. curl không thực thi chính sách same-origin của browser. API phải bảo vệ bằng auth, authorization và business validations.

**5. `allowedHeaders` khác `exposedHeaders` ở đâu?**

`allowedHeaders` kiểm soát request headers browser được gửi qua preflight; `exposedHeaders` cho phép JS đọc những response headers bổ sung từ actual response.

**6. Khi dùng cookie qua cross-origin fetch cần những yếu tố nào?**

Frontend `credentials` thích hợp, server ACAO origin cụ thể + ACAC true, cookie Domain/Path/Secure/SameSite đủ điều kiện, browser không chặn, và CSRF/authorization đúng thiết kế.

**7. `allowedOriginPatterns` có cho phép dùng wildcard cùng credentials?**

Có thể, vì với pattern khớp, Spring trả origin thực tế chứ không phải `*"`. Nhưng pattern quá rộng vẫn nguy hiểm và phải giới hạn trust boundary.

**8. Cấu hình global origins `*"` có thể được local origins cụ thể thu hẹp không?**

Không được giả định. Hãy sửa global policy, tách scope routes và kiểm thử effective config thay vì hy vọng local annotation sẽ đóng vai trò deny rule.

## 18. Checklist áp dụng thực tế

1. Liệt kê frontend origins thực tế theo **scheme + host + port**. Chỉ mở origin cần thiết.
2. Với API public và private, tách rõ URL patterns; không đặt wildcard global rồi cố thu hẹp bằng local.
3. Dùng `@CrossOrigin` cho ngoại lệ rõ ràng, hoặc cấu hình `WebMvcConfigurer` cho policy nhất quán. Khi dùng cả hai, review **combine**.
4. Ghi nhớ list-valued properties thường cộng gộp, scalar local non-null ghi đè global.
5. Chỉ bật `allowCredentials(true)` khi thực sự cần; không dùng `allowedOrigins("*")` với credentials.
6. Khi dùng Spring Security, xử lý CORS preflight trước authentication; tránh cấu hình nhiều filter/gateway khác nhau tạo header trùng.
7. Đặt `allowedMethods`/`allowedHeaders` tối thiểu, `exposedHeaders` chỉ cho những response headers frontend cần đọc.
8. Kiểm soát CSRF, cookie và XSS độc lập; CORS không phải xác thực hay phân quyền.
9. Test actual request và preflight ở cả origin hợp lệ/không hợp lệ; kiểm tra maxAge, exposed headers, credentials và Vary.
10. Kiểm tra HTTPS, proxy/CDN và browser integration, không chỉ MockMvc hay Postman.

## Nguồn chính thức

- [Spring Framework 6.2 — CORS trong Spring MVC](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc-cors.html)
- [Spring Framework 6.2 — `@CrossOrigin` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/bind/annotation/CrossOrigin.html)
- [Spring Framework 6.2 — `CorsConfiguration#combine` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/cors/CorsConfiguration.html)
- [Spring Security 6.5 — Servlet CORS](https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/cors.html)
- [MDN — Cross-Origin Resource Sharing](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS)
- [MDN — HTTP Access-Control-Allow-Origin](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Access-Control-Allow-Origin)
- [OWASP — Cross Site Request Forgery Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cors-web-security-004">CorsFilter, allowed origins, credentials và hạn chế ký tự đại diện</a>

<details>
<summary>Xem chi tiết</summary>

# Spring MVC — CorsFilter, allowed origins, credentials và hạn chế ký tự đại diện

## 1. Mục tiêu và bài toán thực tế

Một ứng dụng có frontend chạy tại `https://app.example.com`, API Spring MVC tại `https://api.example.com` và giao diện quản trị tại `https://admin.example.com`. Đây là các **origins khác nhau**. Trình duyệt áp dụng *same-origin policy* và chỉ cho JavaScript đọc phản hồi từ API khác origin nếu API khai báo quyền truy cập CORS thích hợp.

Một lỗi cấu hình thường gặp là bật:

~~~java
configuration.addAllowedOrigin("*");
configuration.setAllowCredentials(true);
~~~

Cách này **không hợp lệ** trong Spring Framework 6.2: khi cho phép credentials, `allowedOrigins` không được chứa `"*"` vì browser không chấp nhận kết hợp `Access-Control-Allow-Origin: *` với credentialed CORS response. Spring có cơ chế kiểm tra cấu hình và có thể từ chối kết hợp đó. Đổi sang một pattern rộng như `allowedOriginPatterns("*")` có thể khiến cấu hình hợp lệ về mặt kỹ thuật nhưng **vẫn nguy hiểm về chính sách**: gần như mọi website được echo origin để JavaScript đọc dữ liệu có credentials nếu các điều kiện cookie và trình duyệt cho phép.

Bài học này giúp bạn:

- Phân biệt `CorsFilter`, `CorsConfiguration`, `CorsConfigurationSource`, `UrlBasedCorsConfigurationSource` và `DefaultCorsProcessor`.
- Hiểu **allowed origins** chính xác theo scheme/host/port; phân biệt `allowedOrigins` với `allowedOriginPatterns`.
- Hiểu lúc nào dùng `allowCredentials(true)` và các yêu cầu đối với `Access-Control-Allow-Origin` / `Access-Control-Allow-Credentials`, cookies, `fetch(credentials: "include")`.
- Giải thích khác biệt giữa wildcard trong **origins**, **origin patterns**, **allowed methods**, **allowed headers** và **exposed headers**.
- Cấu hình CORS ở Servlet filter boundary, tích hợp đúng với Spring Security và tránh cấu hình chồng chéo.
- Thực hành Spring Boot 3.5.0, Java 17 với `CorsFilter`, allowlist rõ ràng, kiểm thử preflight/actual requests bằng MockMvc và browser.
- Nhận diện lỗi CORS giả do HTTP 401/403/500, preflight bị chặn, cookie không được gửi, proxy/CDN và header phản hồi sai.

**Phạm vi:** Spring Framework 6.2, Spring MVC **Servlet stack**, HTTP(S) origins và trình duyệt hiện đại. Với Spring WebFlux, loại tương ứng là `CorsWebFilter` — không dùng nhầm `CorsFilter` của servlet.

## 2. Nhắc lại CORS theo ranh giới xử lý của Servlet Filter

**Origin** của HTTP(S) là bộ ba scheme + hostname + effective port. Một trang ở `https://app.example.com` gọi API `https://api.example.com` là cross-origin; đổi path trên cùng scheme/host/port không tạo origin khác. `http://localhost:5173` và `http://localhost:8080` khác origin dù cùng host.

**Điểm mới của bài này là vị trí xử lý:** browser áp dụng Same-Origin Policy và kiểm tra phản hồi CORS, còn `CorsFilter` chạy ở **Servlet filter chain phía server, trước MVC controller** khi được đăng ký và sắp thứ tự tương ứng. Filter chọn `CorsConfiguration` cho request, kiểm tra preflight hoặc actual request và có thể kết thúc yêu cầu mà chưa gọi handler. Nó không tự cấp danh tính, quyền nghiệp vụ hay CSRF token.

Ví dụ, browser gửi `Origin: https://app.example.com` tới `/api/orders`. Nếu policy cho phép, CORS response có thể mang `Access-Control-Allow-Origin: https://app.example.com` cùng `Vary: Origin`; nếu không, frontend không được đọc body theo cơ chế CORS, ngay cả khi actual HTTP request đã tới server. **Một bộ header trả đúng không chứng minh authentication/authorization đúng**.

Bài trước đã giải thích cú pháp origin và preflight; các mục sau tập trung vào `CorsFilter`/`CorsConfigurationSource`, wildcard với credentials, thứ tự filter và rủi ro policy quá rộng. Với những bài đọc độc lập, chỉ cần nhớ rằng `curl` không thực thi giới hạn CORS của browser, và same-site cookie **không đồng nghĩa** same-origin.

## 3. CORS preflight diễn ra ở đâu?

Một request JavaScript cross-origin có thể **không cần OPTIONS** nhưng vẫn cần actual response được CORS cho phép.

Ví dụ GET chỉ dùng safelisted headers:

~~~javascript
fetch("https://api.example.com/api/status");
~~~

Thường browser gửi GET trực tiếp (không preflight). Response vẫn cần ACAO thích hợp cho JavaScript đọc.

Ngược lại, JSON POST:

~~~javascript
fetch("https://api.example.com/api/orders", {
  method: "POST",
  headers: {"Content-Type": "application/json"},
  body: JSON.stringify({productId: 10, quantity: 2})
});
~~~

thường được browser kiểm tra bằng preflight trước:

~~~http
OPTIONS /api/orders HTTP/1.1
Origin: https://app.example.com
Access-Control-Request-Method: POST
Access-Control-Request-Headers: content-type
~~~

Nếu cấu hình cho phép:

~~~http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: https://app.example.com
Access-Control-Allow-Methods: GET,POST
Access-Control-Allow-Headers: content-type
Access-Control-Max-Age: 1800
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
~~~

thì browser có thể gửi actual POST tiếp theo. Method trong `Access-Control-Request-Method` là **method của actual request**, không phải OPTIONS. Vì vậy trong `allowedMethods` thông thường khai báo GET/POST/PUT mà client cần thực hiện, **không cần thêm OPTIONS chỉ vì preflight dùng OPTIONS**.

Một số điều kiện thường tạo preflight:

| Request do script tạo | Preflight thông thường |
| --- | --- |
| GET không custom request headers | Không |
| POST `application/x-www-form-urlencoded` với safelisted headers | Không |
| POST `multipart/form-data` theo điều kiện safelisted | Không |
| POST `application/json` | Có |
| PUT/PATCH/DELETE | Có |
| GET có `Authorization` | Có |
| GET thêm `X-API-Key` hoặc `X-Request-ID` | Có |
| POST có custom header `X-Tenant-ID` | Có |

Các quy tắc safelisted còn có giới hạn về giá trị/độ dài headers và trường hợp đặc biệt theo Fetch/browser; bảng trên là các tình huống phổ biến, không phải toàn bộ thuật toán chuẩn.

**Nếu preflight thất bại**, browser thường **không gửi actual request**. Nếu GET không preflight nhưng response thiếu ACAO, server đã có thể xử lý request, chỉ việc đọc response bị chặn. Phân biệt hai trường hợp này khi debug.

## 4. CorsFilter trong Spring Servlet stack

`org.springframework.web.filter.CorsFilter` là servlet filter của Spring Framework; nó có thể dùng trước `DispatcherServlet` và trước việc handler/controller thực thi, tùy thứ tự đăng ký. Nó dùng một `CorsConfigurationSource` để quyết định cấu hình nào áp dụng cho request, rồi dùng `CorsProcessor` (mặc định `DefaultCorsProcessor`) để xử lý giao thức CORS.

Mô hình:

~~~text
Browser
  |
  v
Servlet Filter Chain
  |
  +-- CorsFilter
  |     |
  |     +-- CorsConfigurationSource -> chọn policy theo request URL
  |     |
  |     +-- CorsProcessor -> kiểm Origin, method, requested headers
  |           |
  |           +-- hợp lệ preflight: ghi CORS headers và kết thúc OPTIONS
  |           +-- không hợp lệ: reject, không đi tiếp controller
  |           +-- actual request hợp lệ: thêm CORS headers, tiếp tục chain
  |
  +-- Security filters (nếu được tích hợp/đặt thứ tự đúng)
  |
  v
DispatcherServlet -> Controller -> Service
  |
  v
HTTP response
~~~

### 4.1. Các lớp và trách nhiệm

| Thành phần | Vai trò | Ví dụ |
| --- | --- | --- |
| `CorsFilter` | Servlet filter thực hiện CORS processing ở filter chain | Bean/filter registration |
| `CorsConfiguration` | Dữ liệu policy: origins, methods, headers, credentials, maxAge | `config.setAllowedOrigins(...)` |
| `CorsConfigurationSource` | Chọn `CorsConfiguration` cho từng request | Custom source hoặc URL mapping |
| `UrlBasedCorsConfigurationSource` | Map các URL patterns sang CORS policy | `registerCorsConfiguration("/api/**", config)` |
| `CorsProcessor` | Thuật toán đánh giá CORS request và ghi headers | `DefaultCorsProcessor` |
| `WebMvcConfigurer#addCorsMappings` | Cấu hình CORS ở MVC handler mappings | Không nhất thiết tạo CorsFilter riêng |
| `@CrossOrigin` | Cấu hình CORS tại controller/method | Phạm vi endpoint |
| `http.cors(...)` | Spring Security tích hợp CORS processing trong filter chain | Dùng khi bật Security |

**Không cần triển khai `OPTIONS` endpoint cho từng controller** chỉ để trả preflight nếu filter/MVC CORS processing đã được cấu hình đúng. Việc mở toàn bộ OPTIONS để “sửa lỗi CORS” không phải thay thế cho origin/method/header policy.

### 4.2. Điều gì xảy ra với actual request không có Origin?

Một HTTP request thông thường không có header `Origin` có thể không cần CORS processing: filter để request đi tiếp mà không phải chặn vì origin. Ví dụ `curl http://localhost:8080/api/status` vẫn nhận HTTP response mặc dù chính sách CORS có allowlist hẹp. Đây là hành vi hợp lý: server authorization phải được kiểm tra **riêng**.

Khi có `Origin` không nằm trong policy, `DefaultCorsProcessor` thường reject CORS request (ví dụ HTTP 403). Cần test trạng thái thực tế của phiên bản và filter setup, không coi Origin là authentication credential.

### 4.3. Filter khác CORS trên HandlerMapping

CORS của Spring MVC cũng có thể được định nghĩa ở `HandlerMapping` qua `@CrossOrigin` và `WebMvcConfigurer#addCorsMappings`. Nhưng nếu Spring Security filter chain chặn preflight trước khi request vào MVC, policy đặt ở handler mapping có thể chưa được áp dụng đúng lúc. `CorsFilter` hoặc Security CORS integration xử lý sớm hơn là giải pháp.

**Chọn một nơi chủ đạo để quản lý policy**, thay vì đồng thời tự đăng ký `CorsFilter`, dùng `http.cors(...)` và thêm nhiều `@CrossOrigin("*")` mà không kiểm soát cách chúng kết hợp. Cấu hình chồng chéo có thể tạo duplicate ACAO headers, sai response hoặc mở quyền ngoài dự định.

## 5. allowedOrigins: danh sách origin chính xác

`CorsConfiguration#setAllowedOrigins(...)` được dùng khi bạn biết các frontend origins được cho phép:

~~~java
CorsConfiguration cors = new CorsConfiguration();
cors.setAllowedOrigins(List.of(
        "https://app.example.com",
        "https://admin.example.com",
        "http://localhost:5173"
));
~~~

**Origin gồm scheme + host + port**. `http://localhost:5173` không tự bao gồm `http://localhost:3000`. Tương tự `https://example.com` không tự cho phép `https://app.example.com`.

### 5.1. Allowed origins hợp lệ và không hợp lệ

| Giá trị cấu hình | Diễn giải |
| --- | --- |
| `https://app.example.com` | Cho phép đúng origin app |
| `http://localhost:5173` | Cho phép frontend local dev đúng port |
| `https://app.example.com:8443` | Cho phép đúng HTTPS host và port 8443 |
| `*` | Wildcard origin khi policy không dùng credentialed mode |
| `https://*.example.com` trong **allowedOrigins** | **Không phải** cú pháp wildcard hostname theo cách `allowedOriginPatterns` |
| `https://app.example.com/path` | Sai ý niệm: origin không có path |
| `https://app.example.com, https://admin.example.com` trong một HTTP ACAO header | Không phải định dạng ACAO chứa nhiều origins |

Spring có thể hỗ trợ chuỗi cấu hình phân tách bằng dấu phẩy trong `setAllowedOrigins(...)`, thuận tiện khi resolve property placeholder. Nhưng **HTTP header `Access-Control-Allow-Origin` chỉ nên biểu thị một origin được chọn hoặc `*`**, không trả danh sách CSV.

### 5.2. Không dùng prefix, contains hoặc suffix checks tự chế

Phản ví dụ:

~~~java
// Không phải cách kiểm origin an toàn.
if (origin.startsWith("https://app.example.com")) {
    response.setHeader("Access-Control-Allow-Origin", origin);
}
~~~

Giá trị `https://app.example.com.attacker.test` cũng bắt đầu bằng chuỗi trên nhưng là host khác. Các lỗi tương tự xuất hiện khi dùng `contains("example.com")`, regex không anchor đúng hoặc so domain mà bỏ scheme/port.

Dùng Spring `CorsConfiguration` với **exact origins** hoặc patterns được duyệt bảo mật. Với multi-tenant hoặc dynamic trusted origins, cần chính sách cho phép rõ ràng, kiểm soát danh tính/ownership của origin đã đăng ký và tránh attacker tự đăng ký một origin để đọc dữ liệu nhạy cảm của tenant khác.

### 5.3. Default deny và applyPermitDefaultValues

Một instance `new CorsConfiguration()` **mặc định chưa cho phép origins nào**, bạn phải cấu hình. Tuy nhiên:

~~~java
cors.applyPermitDefaultValues();
~~~

áp dụng một bộ giá trị mặc định tương đối rộng cho những field chưa được đặt: origin `*`, methods GET/HEAD/POST, allowed headers `*`, max age 1800 giây. Với API nhạy cảm, **đừng gọi method này mà chưa hiểu defaults**; nên thiết lập từng trường theo nhu cầu.

## 6. allowedOriginPatterns: wildcard hostname/port có điều kiện

Spring cung cấp:

~~~java
cors.setAllowedOriginPatterns(List.of(
        "https://*.example.com",
        "http://localhost:[5173,3000]"
));
~~~

`allowedOriginPatterns` hỗ trợ pattern theo hostname và port. Ví dụ Javadoc của Spring Framework 6.2:

~~~text
https://*.example.com
https://*.example.com:[8080,8081]
https://*.example.com:[*]
~~~

Lưu ý ví dụ `http://localhost:[5173,3000]` chỉ phù hợp nếu tất cả các origin dev này được phép; đừng dùng `:[*]` cho production nếu bạn muốn giới hạn port. Kiểm tra policy thực tế theo exact scheme/host/port và bộ test allow/deny.

### 6.1. Tại sao allowedOriginPatterns dùng được với credentials?

Khi request Origin khớp một pattern, Spring trả:

~~~http
Access-Control-Allow-Origin: https://tenant-a.example.com
~~~

thay vì trả wildcard pattern `https://*.example.com` hoặc `*`. Vì vậy `allowedOriginPatterns` có thể kết hợp với `allowCredentials(true)` về mặt API.

Tuy nhiên, đây chỉ giải quyết **định dạng ACAO**, không giải quyết **độ tin cậy của origin**. Nếu attacker có quyền kiểm soát `evil.example.com`, pattern `https://*.example.com` cũng có thể cho phép attacker-origin nếu nó match. Điều này có thể tạo khả năng đọc response credentialed trong browser của người dùng, tùy cookie và các kiểm soát khác.

### 6.2. allowedOriginPatterns("*") nguy hiểm thế nào?

~~~java
cors.setAllowedOriginPatterns(List.of("*"));
cors.setAllowCredentials(true);
~~~

Cấu hình này có thể qua được kiểm tra wildcard vốn áp dụng cho `allowedOrigins("*")`, vì Spring **echo actual matched origin** vào ACAO. Nhưng nó thực tế mở **credentialed CORS** tới nhiều origin không xác định, một rủi ro đặc biệt lớn nếu API dùng cookie session.

**Kết luận:** “Spring chấp nhận cú pháp” không đồng nghĩa “an toàn”. Với dữ liệu người dùng, ưu tiên explicit allowlist, chính sách kiểm soát tenant domain và tests cho origin độc hại.

### 6.3. Wildcard không nên thay thế trust policy

Rủi ro thường bị bỏ quên:

- Người ngoài có thể đăng ký tên miền phụ trong tổ chức thông qua dịch vụ tạo subdomain.
- DNS takeover khiến subdomain cũ trở thành host do attacker kiểm soát.
- Môi trường preview deployments có URLs không cố định nhưng cũng không phải origin đáng tin cho dữ liệu production.
- Một wildcard có thể cho cả origin HTTPS/HTTP hoặc nhiều ports nếu pattern không giới hạn chặt.
- Domain có Unicode/punycode/uppercase/port đặc biệt; tự so sánh chuỗi thủ công dễ sai.

Khi cần origin động theo tenant, lưu một allowlist có kiểm duyệt và chuẩn hóa origin chính xác; không dùng `allowedOriginPatterns("*")` để tránh quản trị danh sách.

## 7. Credentials: cookie, authentication và trình duyệt

`CorsConfiguration#setAllowCredentials(true)` điều khiển việc CORS response có thể cho browser expose kết quả của request sử dụng credentials.

Ví dụ frontend:

~~~javascript
const response = await fetch("https://api.example.com/api/me", {
  credentials: "include"
});
const me = await response.json();
~~~

Server thường cần:

~~~http
Access-Control-Allow-Origin: https://app.example.com
Access-Control-Allow-Credentials: true
Vary: Origin
~~~

**Không** dùng:

~~~http
Access-Control-Allow-Origin: *
Access-Control-Allow-Credentials: true
~~~

Browser sẽ không cho script đọc credentialed response. Spring `CorsConfiguration#validateAllowCredentials()` kiểm tra quy tắc wildcard origin tương ứng.

### 7.1. CORS credentials không phải login

Bật `allowCredentials(true)` **không tự xác thực user**, không tự set cookie và không cho một trang ngoại lai quyền gọi API nếu authorization backend từ chối. Nó chỉ là một phần của cơ chế browser chia sẻ response.

Bạn vẫn cần:

- Authentication (session, JWT, OIDC, v.v.) và authorization kiểm tra quyền theo user/resource/tenant.
- CSRF protection nếu browser có cookie/session auth cho các thao tác thay đổi trạng thái.
- Cookie policy `Secure`, `HttpOnly`, `SameSite`, Domain và Path thích hợp.
- TLS/HTTPS cho production.
- CORS allowlist đủ hẹp và có quy trình review.

### 7.2. Cookie có được gửi không?

`fetch(..., {credentials:"include"})` chỉ **cho phép** browser gửi cookies hợp lệ theo điều kiện. Cookie thật sự được gửi còn phụ thuộc domain/path, Secure, SameSite, third-party cookie blocking/partitioning và chính sách trình duyệt.

| Tình huống | Điểm cần nhớ |
| --- | --- |
| `app.example.com` gọi `api.example.com` | Cross-origin, thường same-site nếu scheme thống nhất |
| `app.example.com` gọi `api.other.test` | Cross-origin và cross-site |
| Cookie `SameSite=Lax` | Không đương nhiên được gửi trong cross-site fetch |
| Cookie `SameSite=None; Secure` | Thường là yêu cầu cho một số cross-site cookie flows, vẫn chịu browser policies |
| `credentials: "same-origin"` mặc định của fetch | Không tự gửi credentials tới một origin khác |
| `credentials: "include"` | Cho phép gửi credentials theo browser cookie rules |

CORS headers đúng nhưng cookie không được gửi dẫn đến backend 401; console có thể báo CORS nếu 401 response không có headers CORS hợp lệ. Phải debug đồng thời **cookie transmission**, **server authorization** và **CORS response**.

### 7.3. Cookie session + CORS không chống CSRF

Cross-origin form submissions hoặc những request không cần preflight vẫn có thể gây thao tác thay đổi trạng thái nếu server dựa vào cookie mà thiếu CSRF protection. CORS chủ yếu quản lý **quyền đọc response**, không thay cơ chế chống CSRF.

Đừng tắt CSRF cho mọi POST chỉ vì frontend SPA gọi API. Với cookie/session authentication, sử dụng CSRF token và cookie policies hợp lý; nếu ứng dụng dùng bearer token trong header do frontend kiểm soát, bề mặt CSRF khác nhưng vẫn cần phân tích thiết kế.

### 7.4. Preflight không cần authenticated session

CORS preflight thường không mang cookies của actual request. Nếu security filter chain yêu cầu đăng nhập ngay với OPTIONS, preflight nhận 401/403 trước khi controller chạy và actual POST không bao giờ được gửi.

Do đó **CORS processing phải chạy trước security authentication checks**. Spring Security hỗ trợ tích hợp sẵn khi cung cấp `CorsConfigurationSource` và kích hoạt `http.cors(...)`. Không giải quyết bằng cách bỏ mọi policy xác thực hoặc mở toàn bộ OPTIONS tùy tiện.

## 8. Wildcards trong methods, headers và exposeHeaders

Các dấu `*` ở các thuộc tính khác nhau **không có cùng ý nghĩa**.

| Thuộc tính | Ví dụ | Ý nghĩa | Khuyến nghị |
| --- | --- | --- | --- |
| `allowedOrigins` | `*` | Cho phép mọi origin, không dùng với credentials=true | Public non-credentialed resources nếu thực sự muốn public |
| `allowedOriginPatterns` | `https://*.example.com` | Match origins theo hostname/port pattern | Chỉ nếu toàn bộ host match được tin cậy |
| `allowedOriginPatterns` | `*` | Pattern match origin rất rộng và có thể echo origin | Không dùng cho cookie-based private APIs |
| `allowedMethods` | `GET,POST` | Actual methods được phép cho CORS | Liệt kê methods thật sự cần |
| `allowedMethods` | `*` | Spring có thể reflect requested method theo preflight, nhất là credentials mode | Tránh nếu methods có rủi ro cao |
| `allowedHeaders` | `Content-Type,Authorization` | Script được phép gửi các request headers tương ứng | Allowlist rõ |
| `allowedHeaders` | `*` | Có thể reflect requested headers theo Spring handling trong credentials mode | Không nên mở vô điều kiện |
| `exposedHeaders` | `X-Request-ID` | Cho JavaScript đọc response header ngoài safelist | Chỉ expose headers cần thiết |
| `exposedHeaders` | `*` | Có hành vi/spec/browser caveats khi credentials bật | Liệt kê tường minh để portable |
| `maxAge` | `1800` | Preflight cache lifetime (giây), browser có thể áp trần | Dùng khi policy tương đối ổn định |

**Điểm tinh tế của Spring:** với `allowedMethods("*")` hoặc `allowedHeaders("*")` và credentials enabled, Spring có thể ghi lại chính method/headers được yêu cầu trong preflight response, thay vì nguyên literal `*`. Với `exposedHeaders("*")`, Spring Framework có thể vẫn phát header literal `*` kể cả khi credentials bật do ràng buộc thời điểm xây dựng response headers. Tuy nhiên theo semantics CORS/Fetch và khác biệt trình duyệt, đừng dựa vào wildcard expose trong credentialed flows; dùng tên headers cụ thể.

Không để danh sách `allowedHeaders` bị hiểu là **authorization field allowlist**: nó chỉ kiểm soát browser có thể gửi một request header; backend vẫn phải validate content, token, scopes và business permissions.

### 8.1. Cấu hình cụ thể an toàn hơn

~~~java
CorsConfiguration cors = new CorsConfiguration();
cors.setAllowedOrigins(List.of("https://app.example.com"));
cors.setAllowedMethods(List.of("GET", "POST"));
cors.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-Request-ID"));
cors.setExposedHeaders(List.of("X-Request-ID", "Location"));
cors.setAllowCredentials(true);
cors.setMaxAge(1800L);
cors.validateAllowCredentials();
~~~

Ở đây việc `allowCredentials(true)` có thể phù hợp nếu bạn thực sự cần cookie credentials và origin đã được bảo đảm tin cậy. Với API public/không cookie, có thể đặt `allowCredentials(false)` hoặc không bật tính năng này.

### 8.2. allowPrivateNetwork và private network access

Spring Framework 6.2 có `setAllowPrivateNetwork(...)` cho các user agents áp dụng những quy tắc hạn chế truy cập mạng private/local. Đây là **vùng chính sách bổ sung**, không phải flag chung dùng để sửa mọi lỗi localhost. Spring có validation riêng ngăn `allowedOrigins("*")` kết hợp không an toàn khi bật private-network permission.

Nếu cần cho origin công khai gọi API trên private network, phải phân tích SSRF-like browser pivots, mức tin cậy của origin, trình duyệt hỗ trợ và threat model. Không thêm `allowPrivateNetwork(true)` cùng wildcard chỉ để preflight qua được.

## 9. CorsFilter và cấu hình URL-based

Một cấu hình bằng Java thuần:

~~~java
import java.util.List;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

CorsConfiguration cors = new CorsConfiguration();
cors.setAllowedOrigins(List.of("https://app.example.com"));
cors.setAllowedMethods(List.of("GET", "POST"));
cors.setAllowedHeaders(List.of("Content-Type", "Authorization"));
cors.setAllowCredentials(true);
cors.setMaxAge(1800L);

UrlBasedCorsConfigurationSource source =
        new UrlBasedCorsConfigurationSource();
source.registerCorsConfiguration("/api/**", cors);

CorsFilter corsFilter = new CorsFilter(source);
~~~

Đoạn này là **fragment** minh họa tạo object; nếu đặt thành `@Bean` trong Spring Boot servlet app, filter được đăng ký vào filter chain theo cách cấu hình tương ứng.

### 9.1. Vì sao giới hạn URL paths?

Một ứng dụng có thể có:

~~~text
/api/public/**
/api/account/**
/api/admin/**
/actuator/**
/internal/**
~~~

Không nên áp dụng một wildcard policy như nhau cho tất cả. Ví dụ:

- Public API có thể cho phép một vài origins không credentialed.
- API tài khoản dùng session cần explicit trusted origins và credentials policy.
- Admin API cần allowlist chặt, authentication/authorization và CSRF policy.
- Actuator/admin nội bộ không nên mặc nhiên nhận CORS từ `/**`.
- Endpoints nội bộ thường không cần cho browser origins bất kỳ đọc.

Khi dùng `UrlBasedCorsConfigurationSource`, có thể đăng ký **các path patterns khác nhau** với cấu hình khác nhau. Đảm bảo thứ tự/độ cụ thể và pattern matching phù hợp; test URL đúng path và URL ngoài phạm vi. Nếu proxy rewrite path, so policy với path backend thấy thật.

### 9.2. Có cần FilterRegistrationBean không?

Spring Boot servlet có thể tự đăng ký một bean `Filter` thành Servlet filter. Với yêu cầu ordering/dispatcher types/URL patterns riêng, có thể dùng `FilterRegistrationBean<CorsFilter>` **thay vì** để bean CorsFilter tự đăng ký theo cách không kiểm soát.

Ví dụ minh họa:

~~~java
@Bean
FilterRegistrationBean<CorsFilter> corsRegistration(
        UrlBasedCorsConfigurationSource source) {
    FilterRegistrationBean<CorsFilter> reg =
            new FilterRegistrationBean<>(new CorsFilter(source));
    reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
    reg.addUrlPatterns("/api/*");
    return reg;
}
~~~

Cần imports `org.springframework.boot.web.servlet.FilterRegistrationBean`, `org.springframework.core.Ordered`, `org.springframework.context.annotation.Bean`. Nếu chọn cách này, hãy tránh đăng ký **thêm một bean CorsFilter khác** làm filter chạy hai lần; chỉ đăng ký một path phù hợp và test request filter order.

### 9.3. Không bỏ qua ERROR/ASYNC dispatch

`CorsFilter` kế thừa cơ chế `OncePerRequestFilter`. Tùy filter registration và Servlet dispatch type, hành vi trên `REQUEST`, `ASYNC`, `ERROR` có thể khác. Nếu response 500 được xử lý ở ERROR dispatch và ACAO không có, browser có thể chỉ thấy lỗi CORS thay vì body lỗi. Khi có async/streaming/security/proxy, cần integration tests trên embedded server để xác định CORS headers vẫn áp dụng an toàn.

Không tùy tiện thêm ACAO bằng tay ở nhiều điểm (filter, error handler, reverse proxy) vì response có thể chứa header trùng hoặc không tương thích.

## 10. Ba chiến lược tích hợp CORS trong Spring Boot

### 10.1. Chỉ Spring MVC (không Spring Security)

Với ứng dụng Servlet MVC đơn giản, có thể chọn:

- `@CrossOrigin` ở controller/method; hoặc
- `WebMvcConfigurer#addCorsMappings` cho nhóm URL; hoặc
- `CorsFilter` với `UrlBasedCorsConfigurationSource` khi muốn xử lý CORS ở servlet filter boundary.

Các cách đều có thể hợp lệ tùy nhu cầu. Nhưng nếu bạn đang học `CorsFilter`, hãy triển khai rõ một filter và tập trung kiểm thử policy thay vì cấu hình đủ cả ba.

### 10.2. Có Spring Security: ưu tiên Security CORS integration

Spring Security hướng dẫn cung cấp bean `UrlBasedCorsConfigurationSource` và bật CORS integration để CORS xử lý trước các security checks cần authentication:

~~~java
@Configuration
class SecurityCorsConfiguration {

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of("https://app.example.com"));
        cors.setAllowedMethods(List.of("GET", "POST"));
        cors.setAllowedHeaders(List.of(
                "Content-Type", "Authorization", "X-CSRF-TOKEN"));
        cors.setExposedHeaders(List.of("X-Request-ID"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(1800L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .build();
    }
}
~~~

Imports cần dùng:

~~~java
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
~~~

Fragment này là **phương án thay thế độc lập**, không thêm cạnh cấu hình `CorsFilter` runnable ở mục 11. Cần dependency `spring-boot-starter-security`, authentication config và CSRF token flow phù hợp nếu dùng cookie session. Không sao chép snippet này như một cấu hình security production hoàn chỉnh.

Quan trọng: **không đăng ký cùng một CorsFilter bean global lẫn cấu hình `http.cors(...)` khác nhau** mà không hiểu thứ tự và trách nhiệm xử lý. Hãy dùng một `CorsConfigurationSource` chính thống, kiểm tra preflight trong security filter chain, 401/403 có CORS headers và không mở rộng CORS tùy tiện.

### 10.3. Khi gateway xử lý CORS

Một gateway/CDN có thể xử lý preflight và ACAO trước khi request tới Spring. Nếu gateway là nơi chủ đạo quản lý CORS, backend cần hiểu chính sách này và không phát ACAO trùng. Nhưng backend vẫn cần authentication/authorization riêng; đừng dùng việc gateway đã cho ACAO làm tín hiệu user đã được tin cậy.

Cần test toàn bộ request path từ browser qua gateway tới backend, đặc biệt khi gateway trả 502/503/504 hoặc khi app trả 401/403, vì các response này cũng cần chính sách CORS nhất quán nếu frontend được phép đọc chi tiết lỗi.

## 11. Dự án chạy được: CorsFilter, credentials và origins allowlist

Ứng dụng demo:

- Spring Boot 3.5.0, Java 17+, Maven.
- Backend chạy tại `http://localhost:8080`.
- Frontend demo chạy ở `http://localhost:5173`; cho phép thêm origin `https://app.example.com`.
- Chỉ URL `/api/**` có CORS policy này.
- Không dùng `@CrossOrigin` hay `WebMvcConfigurer#addCorsMappings` để tránh double configuration.
- Bật `allowCredentials(true)` để chứng minh ACAO **đúng origin** và `Access-Control-Allow-Credentials: true`. **Demo không có login/cookie**, nên điều này không biến API thành authenticated application; xem lưu ý bảo mật bên dưới.
- Có GET `/api/hello`, POST JSON `/api/echo`, custom response header `X-Request-ID` và tests.

### 11.1. Cấu trúc

~~~text
cors-filter-demo/
├── pom.xml
├── src/
│   ├── main/java/demo/CorsFilterApplication.java
│   └── test/java/demo/CorsFilterApiTest.java
└── web-client/
    └── index.html
~~~

### 11.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>cors-filter-demo</artifactId>
    <version>1.0.0</version>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 11.3. CorsFilterApplication.java

Lưu toàn bộ Java dưới đây vào `src/main/java/demo/CorsFilterApplication.java`. Chỉ một class public ở cấp top-level; các class/record còn lại package-private nên Java cho phép đặt chung file.

~~~java
package demo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@SpringBootApplication
public class CorsFilterApplication {
    public static void main(String[] args) {
        SpringApplication.run(CorsFilterApplication.class, args);
    }
}

@Configuration
class CorsFilterConfig {
    @Bean
    CorsFilter corsFilter() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "https://app.example.com"
        ));
        cors.setAllowedMethods(List.of("GET", "POST"));
        cors.setAllowedHeaders(List.of(
                "Content-Type",
                "Authorization",
                "X-Request-ID"
        ));
        cors.setExposedHeaders(List.of("X-Request-ID", "Location"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(1800L);

        // Fail fast với tổ hợp wildcard origins và credentials sai.
        cors.validateAllowCredentials();

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return new CorsFilter(source);
    }
}

record EchoRequest(String message) {
}

record EchoResponse(String echo) {
}

@RestController
@RequestMapping("/api")
class DemoApiController {
    @GetMapping(path = "/hello",
                produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, String>> hello() {
        return ResponseEntity.ok()
                .header("X-Request-ID", UUID.randomUUID().toString())
                .body(Map.of("message", "Hello from Spring MVC"));
    }

    @PostMapping(path = "/echo",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EchoResponse> echo(
            @RequestBody EchoRequest request) {
        return ResponseEntity.ok()
                .header("X-Request-ID", UUID.randomUUID().toString())
                .body(new EchoResponse("Echo: " + request.message()));
    }
}
~~~

**Vì sao config như vậy?**

- `CorsFilter` là bean Spring Boot tự đăng ký trong servlet environment; bạn không phải tự viết `OPTIONS` controller.
- `UrlBasedCorsConfigurationSource` chỉ áp dụng policy cho `/api/**`, không mặc nhiên cho Actuator/admin/internal endpoints.
- `setAllowedOrigins(...)` chứa **hai origins chính xác**, không chứa wildcard.
- `allowCredentials(true)` làm response cần ACAO theo một matched origin cụ thể, cộng `Access-Control-Allow-Credentials: true`.
- `setAllowedMethods(List.of("GET","POST"))` đủ cho actual GET/POST; preflight method OPTIONS không cần thêm vào list.
- `setAllowedHeaders` cho phép JSON Content-Type và hai request headers nếu frontend cần; không mở `*`.
- `setExposedHeaders` cho browser JavaScript đọc `X-Request-ID` hoặc `Location` khi response có các header ấy.
- `maxAge=1800` cho browser được cache preflight tối đa theo giới hạn trình duyệt.
- `validateAllowCredentials()` giúp phát hiện cấu hình không hợp lệ sớm. Khi thay origins bằng `*` đồng thời giữ credentials true, ứng dụng không nên khởi động thành công với policy đó.

**Quan trọng:** Demo có `allowCredentials(true)` để quan sát CORS response semantics, nhưng API hoàn toàn **không có đăng nhập hay dữ liệu private**. Khi triển khai API thực tế, chỉ bật credentials nếu origin đã được tin cậy và authentication/authorization/CSRF/cookie policies đã được thiết kế đầy đủ.

### 11.4. Frontend thử nghiệm web-client/index.html

~~~html
<!doctype html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <title>CorsFilter demo</title>
</head>
<body>
  <h1>Test CORS credentials + allowlist</h1>
  <button id="get">GET /api/hello</button>
  <button id="post">POST JSON /api/echo</button>
  <pre id="result"></pre>
  <script>
    const base = "http://localhost:8080";
    const output = document.querySelector("#result");

    async function request(path, options = {}) {
      try {
        const response = await fetch(base + path, {
          credentials: "include",
          ...options
        });
        const requestId = response.headers.get("X-Request-ID");
        const body = await response.json();
        output.textContent = JSON.stringify({
          httpStatus: response.status,
          requestId,
          body
        }, null, 2);
      } catch (error) {
        output.textContent =
          "Không đọc được response: " + error.message
          + ". Xem DevTools Network/Console.";
      }
    }

    document.querySelector("#get").onclick = () =>
      request("/api/hello");

    document.querySelector("#post").onclick = () =>
      request("/api/echo", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({message: "Xin chào"})
      });
  </script>
</body>
</html>
~~~

Mặc dù `credentials: "include"` được khai báo, demo không tạo cookie hoặc session. Do đó bạn đang kiểm tra **CORS response có cho phép credentials mode** chứ chưa kiểm thử đăng nhập thực tế. Nếu tích hợp cookie session sau này, cần xem xét `SameSite`, CSRF tokens, browser third-party cookie policy và Spring Security.

### 11.5. Khởi chạy

Terminal 1 tại thư mục có `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

Terminal 2:

~~~bash
python -m http.server 5173 --directory web-client
~~~

Mở browser:

~~~text
http://localhost:5173
~~~

Trong DevTools/Network:

1. GET /api/hello thường gửi actual GET không preflight khi không có custom request headers.
2. POST JSON /api/echo thường gửi OPTIONS preflight trước actual POST.
3. Headers trả về chứa ACAO đúng `http://localhost:5173`, ACAC true, và `Access-Control-Expose-Headers` cho X-Request-ID.
4. Đổi frontend sang cổng khác (ví dụ 5174) mà không đổi allowed origins: cross-origin script từ đó sẽ bị từ chối.

Response GET tương đương:

~~~json
{"message":"Hello from Spring MVC"}
~~~

Response POST tương đương:

~~~json
{"echo":"Echo: Xin chào"}
~~~

UUID của X-Request-ID thay đổi theo mỗi actual API request. Response preflight được filter xử lý trước controller nên không tự mang X-Request-ID do controller tạo; đây là hành vi mong đợi của demo.

### 11.6. Curl: phân biệt actual và preflight

Actual request từ trusted origin:

~~~bash
curl -i 'http://localhost:8080/api/hello' \
  -H 'Origin: http://localhost:5173'
~~~

Header điển hình:

~~~http
HTTP/1.1 200
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Credentials: true
Access-Control-Expose-Headers: X-Request-ID, Location
Vary: Origin
Content-Type: application/json
~~~

Preflight JSON POST:

~~~bash
curl -i -X OPTIONS 'http://localhost:8080/api/echo' \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
~~~

Preflight thành công có thể nhận:

~~~http
HTTP/1.1 200
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET,POST
Access-Control-Allow-Headers: content-type
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 1800
~~~

Dấu phẩy/casing/thứ tự headers có thể thay đổi tùy Spring/container; không so raw HTTP text để xác định policy đúng.

Dùng origin không nằm trong allowlist:

~~~bash
curl -i -X OPTIONS 'http://localhost:8080/api/echo' \
  -H 'Origin: https://evil.example' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
~~~

`DefaultCorsProcessor` của Spring Framework 6.2 thường trả **403 Invalid CORS request**. Browser sẽ không gửi actual POST nếu preflight bị từ chối. Nhưng `curl` vẫn in HTTP response dù không có ACAO vì curl không thực thi SOP.

### 11.7. MockMvc kiểm thử allowlist, credentials và wildcard limitations

File `src/test/java/demo/CorsFilterApiTest.java`:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CorsFilterApiTest {
    @Autowired
    MockMvc mvc;

    @Test
    void trustedOriginCanReadActualResponseWithCredentials() throws Exception {
        mvc.perform(get("/api/hello")
                .header("Origin", "http://localhost:5173"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    "Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(header().string(
                    "Access-Control-Allow-Credentials", "true"))
            .andExpect(header().string(
                    "Access-Control-Expose-Headers",
                    containsString("X-Request-ID")))
            .andExpect(jsonPath("$.message").value("Hello from Spring MVC"));
    }

    @Test
    void secondExactTrustedOriginIsAllowed() throws Exception {
        mvc.perform(get("/api/hello")
                .header("Origin", "https://app.example.com"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    "Access-Control-Allow-Origin", "https://app.example.com"));
    }

    @Test
    void requestsWithoutOriginStillWork() throws Exception {
        mvc.perform(get("/api/hello"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(
                    "Access-Control-Allow-Origin"));
    }

    @Test
    void approvedJsonPostPreflightDoesNotRunController()
            throws Exception {
        mvc.perform(options("/api/echo")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    "Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(header().string(
                    "Access-Control-Allow-Methods", containsString("POST")))
            .andExpect(header().string(
                    "Access-Control-Allow-Headers",
                    containsString("content-type")))
            .andExpect(header().string(
                    "Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void unknownOriginPreflightIsRejected() throws Exception {
        mvc.perform(options("/api/echo")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(
                    "Access-Control-Allow-Origin"));
    }

    @Test
    void unapprovedMethodIsRejected() throws Exception {
        mvc.perform(options("/api/hello")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "DELETE"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unapprovedHeaderIsRejected() throws Exception {
        mvc.perform(options("/api/echo")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-Private-Admin"))
            .andExpect(status().isForbidden());
    }

    @Test
    void wildcardOriginCannotBeCombinedWithCredentials() {
        CorsConfiguration invalid = new CorsConfiguration();
        invalid.setAllowedOrigins(List.of("*"));
        invalid.setAllowCredentials(true);
        assertThrows(IllegalArgumentException.class,
                invalid::validateAllowCredentials);
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

Các test phân biệt allowed origins, rejected origins, allowed methods/headers và validation của wildcard configuration. Test preflight xác nhận giao thức CORS xử lý trước actual controller flow; code controller chỉ có GET và POST nên không có business OPTIONS handler.

**Hạn chế:** MockMvc không thay thế browser khi kiểm thử actual cookie policies, third-party cookie restrictions, CSRF/authentication filters và gateway. Để kiểm thử production, bổ sung embedded-server/browser E2E với cookies và Spring Security.

## 12. Nếu muốn dùng allowedOriginPatterns trong thực tế

Giả sử toàn bộ subdomains của `trusted.example.com` thật sự do cùng tổ chức kiểm soát:

~~~java
CorsConfiguration cors = new CorsConfiguration();
cors.setAllowedOriginPatterns(List.of(
        "https://*.trusted.example.com"
));
cors.setAllowedMethods(List.of("GET"));
cors.setAllowedHeaders(List.of("Authorization"));
cors.setAllowCredentials(true);
~~~

Spring sẽ trả ACAO theo **matched request Origin**. Điều này có thể phù hợp cho host nội bộ do tổ chức sở hữu, nhưng chỉ sau khi đánh giá domain takeover, tenant isolation và cookies.

### 12.1. Test pattern trực tiếp

Dùng `CorsConfiguration#checkOrigin` để xác minh:

~~~java
CorsConfiguration config = new CorsConfiguration();
config.setAllowedOriginPatterns(List.of(
        "https://*.trusted.example.com"));
config.setAllowCredentials(true);

String matched =
        config.checkOrigin("https://team.trusted.example.com");
String rejected =
        config.checkOrigin("https://attacker.example");
~~~

Ở đây `matched` thường là giá trị origin cụ thể, còn `rejected == null`. Đây là **fragment** trong class/method có imports Java phù hợp, không phải chương trình độc lập. Nếu business policy cần host chính xác, vẫn nên chọn `setAllowedOrigins` thay vì patterns.

### 12.2. Không hiểu nhầm allowOriginPatterns với allowCredentials=false

Một số developer chuyển sang `allowedOriginPatterns` chỉ để tránh lỗi cấu hình wildcard, dù API không cần credentials. Nếu API thực sự là public non-credentialed resource, `allowedOrigins("*")` có thể là một lựa chọn có chủ ý; nếu API private, dùng explicit origins theo trust boundary. Không nên dùng pattern phức tạp chỉ vì “Spring hỗ trợ”.

### 12.3. Multiple trusted origins

Một API có frontend user và admin riêng:

~~~java
cors.setAllowedOrigins(List.of(
        "https://app.example.com",
        "https://admin.example.com"
));
~~~

Spring đánh giá `Origin` đến và phản hồi bằng **một matched origin**, không trả một ACAO header là chuỗi CSV của hai origins. Nếu phản hồi vary theo origin, cần `Vary: Origin` để CDN/proxy không cache sai ACAO.

Đặc biệt, nếu admin page có authentication/authorization riêng, việc cho origin admin trong allowlist vẫn không khiến user thường có quyền đọc admin data.

## 13. Debug CORS và các lỗi cấu hình phổ biến

| Triệu chứng | Nguyên nhân khả dĩ | Bước kiểm tra |
| --- | --- | --- |
| “CORS policy blocked”, Network hiện 200 | Actual response thiếu/sai ACAO | So Origin và ACAO, status/body |
| OPTIONS trả 401/403 rồi không có POST | CORS filter xử lý sau Security hoặc allowlist sai | Filter order, `http.cors(...)`, headers preflight |
| Lỗi khi bật `allowCredentials(true)` với origin `*` | Cấu hình wildcard không hợp lệ | Dùng exact origins hoặc pattern được kiểm duyệt |
| Trình duyệt báo wildcard ACAO không hợp lệ với cookie | ACAO `*` + credentialed fetch | Trả matched origin + ACAC true |
| HTTP 200 nhưng JS không đọc `X-Request-ID` | Thiếu `Access-Control-Expose-Headers` | Expose đúng header |
| GET chạy, POST JSON thất bại | POST có preflight, config thiếu POST/Content-Type | Kiểm OPTIONS preflight |
| GET từ local 5173 chạy, local 5174 thất bại | Khác origin do port | Add đúng trusted dev origin khi cần |
| CORS đúng nhưng /api/me trả 401 | Cookie/token không gửi/invalid | Credentials mode, SameSite, auth logs |
| CORS lỗi khi backend có exception 500 | Error response không có ACAO hoặc proxy trả lỗi | Kiểm backend error handling/gateway |
| CORS chập chờn sau CDN | CDN cache ACAO theo origin sai | Kiểm `Vary: Origin` và cache policy |
| Có hai ACAO headers khác nhau | Gateway + app hoặc nhiều filters tự thêm | Chọn một nơi quản lý CORS |
| Preflight lỗi khi gửi X-API-Key | Header không nằm allowedHeaders | Review policy rồi thêm header cần thiết |
| Security vẫn chặn POST sau preflight OK | Actual request cần auth/CSRF | Không nhầm preflight approval với authorization |
| `Origin: null` bị chặn | Opaque origin không được cho phép | Dùng dev HTTP server thay file:// |
| Curl đọc được dữ liệu còn JS không | Curl không thực thi SOP/CORS | Test trong browser/DevTools |

### 13.1. Thứ tự debug nên thực hiện

1. Xác định chính xác `window.location.origin` ở frontend và URL API.
2. Phân biệt request có OPTIONS hay không.
3. Nếu có OPTIONS, kiểm `Origin`, `Access-Control-Request-Method` và `Access-Control-Request-Headers` của preflight.
4. Kiểm server trả ACAO/ACAM/ACAH/ACAC phù hợp; với credentials, ACAO phải là origin cụ thể.
5. Nếu actual request đã tới server, kiểm HTTP status/backend logs/response ACAO. Một backend 500 không phải “đơn thuần lỗi CORS”.
6. Kiểm fetch credentials mode và cookie policy nếu sử dụng session.
7. Kiểm URL path matching `/api/**` và tình trạng nhiều CORS configurations.
8. Nếu bật Security, đảm bảo CORS processing trước authentication của preflight; sau đó vẫn kiểm authorization/CSRF của actual request.
9. Nếu có proxy/CDN, kiểm headers có bị xóa/nhân đôi và cache đúng theo Origin không.
10. Xác minh bằng MockMvc/embedded server và browser, không chỉ bằng curl/Postman.

### 13.2. Tránh mode:"no-cors"

Phản ví dụ:

~~~javascript
fetch("https://api.example.com/private", {
  mode: "no-cors"
});
~~~

Điều này **không cấp quyền đọc JSON**. Browser thường trả **opaque response** cho script, không cho đọc body/status như một API response bình thường. Đừng coi đây là cách sửa cấu hình server.

### 13.3. Không tắt browser security để phát triển

Khởi chạy Chrome/Firefox bằng những flags vô hiệu hóa web security có thể che lỗi chính sách và tạo nguy cơ cho phiên duyệt. Nên dùng dev proxy hoặc cấu hình CORS đúng cho origin frontend.

## 14. Security review cho credentialed CORS

Với cookie-based API, hãy tự hỏi:

- CORS cho phép **những origin nào chính xác**? Ai kiểm soát DNS/domain của từng origin?
- Có subdomain nào do tenant/user tự tạo không? Có dangling DNS hay preview domains chưa kiểm soát?
- Cookies có cần cross-site không, và browser có thực sự gửi chúng không?
- CSRF token được gửi/kiểm tra như thế nào? API endpoints ghi dữ liệu có được bảo vệ?
- Các lỗi 401/403/500 có response headers CORS hợp lệ cho trusted origin mà không làm lộ thông tin?
- Có policy khác cho admin/internal endpoints không?
- Mức độ tin cậy của wildcard hostname patterns có vượt quá ranh giới bảo mật không?
- Có third-party script trong allowed frontend origin có thể đọc user-specific API data không? CORS tin tưởng **origin**, không phân biệt từng đoạn JS chạy trong origin đó.
- Có yêu cầu chống XSS/CSP ở frontend không? Nếu origin đáng tin bị XSS, attacker script cùng origin có thể được hưởng CORS permission.
- Data cần bảo vệ có bị cache shared bởi CDN sau khi response có ACAO/ACAC không? Cần Cache-Control riêng.
- Có một quy trình thay đổi allowed origins, logging và audit thay vì để client tự đăng ký origin không kiểm chứng không?

CORS đúng chỉ là một lớp trong hệ thống bảo mật. Đặc biệt với `allowCredentials(true)`, hãy xem việc cho thêm một origin như **mở rộng trust boundary** cần review.

## 15. Bài tập tự luyện

**Bài 1 — Allowed origins:** Trang `https://app.example.com` gọi API `https://api.example.com`. Viết `CorsConfiguration` chỉ cho phép app, HTTP methods GET/POST, Content-Type/Authorization và không dùng wildcard. Tại sao `https://app.example.com.attacker.test` không được phép?

**Bài 2 — Wildcard credentials:** Giải thích tại sao `allowedOrigins("*") + allowCredentials(true)` bị từ chối và tại sao thay bằng `allowedOriginPatterns("*") + allowCredentials(true)` có thể chạy nhưng không an toàn.

**Bài 3 — Domain pattern:** Tổ chức có `a.trusted.example.com`, `b.trusted.example.com` nhưng có một subdomain do user tạo. Bạn có nên dùng `https://*.trusted.example.com` cho credentialed CORS không? Đề xuất exact allowlist.

**Bài 4 — Preflight:** Frontend POST JSON có Authorization header. Viết OPTIONS request với Origin, Access-Control-Request-Method và Access-Control-Request-Headers. Phân biệt method OPTIONS với actual POST trong allowedMethods.

**Bài 5 — Security:** CORS preflight trả 401 khi Spring Security có session authentication. Giải thích vì sao preflight không có cookie và viết cấu hình `http.cors(Customizer.withDefaults())` + `CorsConfigurationSource` để xử lý đúng thứ tự.

**Bài 6 — Cookies:** Vì sao `fetch(credentials:"include")` + ACAO/ACAC đúng vẫn có thể nhận 401? Phân tích SameSite, Secure, Domain, Path và browser third-party cookie policy.

**Bài 7 — Expose headers:** API trả X-Request-ID nhưng `response.headers.get("X-Request-ID")` là null. Bạn cần cấu hình nào ở server? Nó có làm X-Request-ID trở thành request header được phép gửi không?

**Bài 8 — Test:** Viết MockMvc preflight tests cho allow origin `http://localhost:5173`, deny `https://evil.example`, POST được phép và DELETE bị từ chối.

**Bài 9 — Error responses:** Một origin hợp lệ nhận lỗi 500 từ upstream nhưng browser chỉ báo CORS. Những tầng nào có thể làm mất ACAO? Tại sao mở ACAO "*" không phải sửa lỗi thực?

**Bài 10 — Architect:** Thiết kế policy cho `/api/public/**` (không credentials), `/api/account/**` (credentials + exact origins) và `/api/admin/**` (allowlist chặt + authorization). Chọn CorsFilter/MVC/Security integration sao cho policy không chồng chéo.

## 16. Tổng kết

- **`CorsFilter`** xử lý CORS ở Servlet filter boundary thông qua `CorsConfigurationSource` và `CorsProcessor`; thường phù hợp khi muốn CORS xử lý sớm, đặc biệt trước Spring Security.
- **`allowedOrigins`** nên liệt kê các origins chính xác (scheme, host, port). Không dùng `"*"` khi `allowCredentials(true)`.
- **`allowedOriginPatterns`** hỗ trợ wildcard hostname/port, có thể được dùng với credentials vì Spring echo origin khớp; nhưng pattern quá rộng có thể mở ra rủi ro lớn.
- **Credentials không phải authentication**, và CORS không thay CSRF protection, authorization hoặc cookie security.
- **Wildcards trong methods/headers/exposedHeaders** có quy tắc khác wildcard origins; ưu tiên allowlist rõ để giảm bất ngờ về semantics/browser.
- **Preflight OPTIONS** phải được xử lý trước xác thực; actual request vẫn phải đi qua authentication/authorization/CSRF thích hợp.
- Trong dự án Spring MVC, nên có **một chính sách CORS có ownership rõ**, kiểm thử positive/negative origins, methods, headers, credentials và các responses lỗi.
- Khi browser báo CORS error, hãy kiểm cả network, status, cookies, security filter chain và proxy — không chỉ thêm `@CrossOrigin("*")`.

## 17. Tài liệu chính thức

- [Spring Framework 6.2 — CORS trong Spring MVC](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc-cors.html).
- [Spring Framework 6.2 — CorsConfiguration Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/cors/CorsConfiguration.html): `allowedOrigins`, `allowedOriginPatterns`, `allowCredentials` và validation.
- [Spring Framework 6.2 — CorsFilter Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/filter/CorsFilter.html).
- [Spring Framework 6.2 — DefaultCorsProcessor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/cors/DefaultCorsProcessor.html).
- [Spring Security — CORS Servlet Integration](https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/cors.html).
- [MDN — CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS): preflight, headers, credentials.
- [MDN — Access-Control-Allow-Origin](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Access-Control-Allow-Origin).
- [WHATWG Fetch Standard](https://fetch.spec.whatwg.org/): normative CORS protocol, credentials và response tainting.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cors-web-security-005">Phân biệt CORS, xác thực, phân quyền và CSRF</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt CORS, xác thực, phân quyền và CSRF trong Spring MVC

## 1. Mục tiêu: bốn cơ chế giải quyết bốn câu hỏi khác nhau

Giả sử có một frontend tại `https://app.example.test` gọi API Spring Boot tại `https://api.example.test`. Người dùng đã đăng nhập, trình duyệt muốn tạo tài liệu mới, server chỉ cho role `EDITOR` thao tác, và kẻ tấn công có một website khác cố gửi lệnh thay người dùng.

Trong kịch bản này, có **bốn quyết định bảo mật** cần tách bạch:

1. **CORS (Cross-Origin Resource Sharing):** JavaScript của **origin nào** được trình duyệt cho phép đọc response của API và được browser cho phép thực hiện request CORS theo các quy tắc của Fetch?
2. **Xác thực (authentication):** Request đang đến từ **danh tính nào**, và bằng chứng đăng nhập có hợp lệ không?
3. **Phân quyền (authorization):** Danh tính đó **được phép làm gì** với endpoint, object và dữ liệu cụ thể?
4. **CSRF (Cross-Site Request Forgery):** Làm sao ngăn một website khác **kích hoạt request làm thay đổi trạng thái** bằng những thông tin xác thực mà browser có thể tự đính kèm thay người dùng?

**Chìa khóa:** CORS không phải authentication, không quyết định người dùng có quyền sửa dữ liệu, và **không thay thế CSRF protection**. Authentication không tự động cho phép mọi thao tác; authorization không tự bảo vệ khỏi request giả mạo từ browser đã đăng nhập.

Bài học bao gồm sơ đồ threat model, HTTP headers, request preflight, cookies/credentials, token CSRF, cùng một ứng dụng **Spring Boot 3.3.x / Java 17 / Spring Security 6.x** có cấu hình CORS + authentication + authorization + CSRF và kiểm thử MockMvc. Các đoạn code chạy ở Servlet MVC stack, khác một số API tương ứng của WebFlux.

## 2. Bảng so sánh nhanh

| Cơ chế | Trả lời câu hỏi | Thực thi chủ yếu ở đâu? | Dữ kiện quyết định | Nếu không đạt |
| --- | --- | --- | --- | --- |
| **CORS** | Origin khác có được JavaScript đọc response và gửi CORS request theo browser protocol không? | Browser, với chính sách phản hồi của server | `Origin`, `Access-Control-*`, method, headers, credentials | Browser chặn JS đọc response hoặc không gửi actual request sau preflight thất bại |
| **Authentication** | Bạn là ai? | Server/API/security filter | Session, cookie, HTTP Basic, bearer token, mTLS… | Thường **401** với API cần đăng nhập; có ngoại lệ tùy flow |
| **Authorization** | Bạn được làm việc này với resource này không? | Server/API, method/service/data layer | Principal, role/authority, ownership, tenant, policy | Thường **403** nếu đã xác thực nhưng thiếu quyền; đôi khi **404** có chủ ý để ẩn tồn tại |
| **CSRF protection** | Request thay đổi trạng thái có thật sự xuất phát từ luồng được tin cậy không? | Server và browser client phối hợp | CSRF token, Origin/Referer, SameSite, Fetch Metadata và chính sách session | Thường **403** nếu thiếu/sai token khi bật Spring Security CSRF |

Một request có thể **CORS hợp lệ nhưng authentication thất bại**, hoặc **authentication hợp lệ và đúng role nhưng CSRF thất bại**. Không suy đoán nguyên nhân chỉ từ 403; tìm exception/handler và xem loại kiểm tra nào từ chối.

## 3. Same-Origin Policy và CORS: browser mới là bên cưỡng chế

Một **origin** được xác định bằng tổ hợp **scheme + host + port**.

| URL | Origin so với `https://app.example.test` |
| --- | --- |
| `https://app.example.test/home` | Cùng origin |
| `https://app.example.test/api` | Cùng origin |
| `http://app.example.test/home` | Khác origin: scheme khác |
| `https://app.example.test:8443/home` | Khác origin: port khác |
| `https://api.example.test/home` | Khác origin: host khác |
| `https://evil.example.test/home` | Khác origin: host khác |

**Same-Origin Policy (SOP)** giới hạn khả năng JavaScript từ origin này đọc dữ liệu từ origin khác. CORS là cơ chế cho phép server **chủ động chia sẻ một số response với origin khác** thông qua HTTP headers. Quy tắc được trình duyệt thực thi; cURL, Postman hay script backend **không phải trình duyệt** và thông thường không tự áp cùng CORS restrictions.

### 3.1. Simple CORS request và response

Frontend:

```javascript
fetch("https://api.example.test/api/public", {
  method: "GET"
}).then(r => r.json()).then(console.log);
```

Browser tự thêm, trong các ngữ cảnh phù hợp:

```http
GET /api/public HTTP/1.1
Host: api.example.test
Origin: https://app.example.test
```

Server cho phép frontend này đọc response:

```http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: https://app.example.test
Vary: Origin
Content-Type: application/json

{"status":"ok"}
```

Nếu response **không** cho phép origin ấy, server vẫn **có thể** đã xử lý request và trả HTTP 200, nhưng browser chặn JavaScript truy cập response. Console thường hiện lỗi CORS. Đây không đồng nghĩa “API trả 403”. CORS error có thể xuất hiện từ **chính browser** mặc dù server đã xử lý request.

Đồng thời:

- Một form HTML từ website khác vẫn có thể gửi những request mà browser cho phép gửi cross-origin.
- Image/script/link/navigation có cơ chế tải tài nguyên riêng, không được hiểu giản đơn là mọi resource phải qua fetch CORS.
- Không dùng `Origin` request header như bằng chứng xác thực danh tính: client ngoài browser có thể giả mạo header.
- CORS không ngăn người dùng trực tiếp gọi API bằng cURL nếu không có authentication/authorization ở server.

### 3.2. Preflight OPTIONS là gì?

Các request CORS không thuộc nhóm browser coi là safelisted thường phải **preflight** trước. Ví dụ frontend gửi JSON cùng Authorization và CSRF header:

```javascript
fetch("https://api.example.test/api/documents", {
  method: "POST",
  credentials: "include",
  headers: {
    "Content-Type": "application/json",
    "X-CSRF-TOKEN": csrfToken
  },
  body: JSON.stringify({ title: "Spring MVC" })
});
```

Browser có thể hỏi server trước:

```http
OPTIONS /api/documents HTTP/1.1
Origin: https://app.example.test
Access-Control-Request-Method: POST
Access-Control-Request-Headers: content-type,x-csrf-token
```

Preflight hợp lệ có thể nhận:

```http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: https://app.example.test
Access-Control-Allow-Methods: GET,POST,OPTIONS
Access-Control-Allow-Headers: Content-Type,X-CSRF-TOKEN,Authorization
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 3600
Vary: Origin, Access-Control-Request-Method, Access-Control-Request-Headers
```

Đây là response **minh họa**, không phải chuỗi header byte-for-byte mặc định. Nếu preflight thành công, browser **mới gửi actual POST**; POST sau đó vẫn phải vượt qua authentication, authorization, CSRF và validation bình thường.

**Preflight không phải login và không mang CSRF token của actual request.** Preflight theo Fetch không gửi credentials như session cookie; vì vậy Spring Security cần xử lý CORS trước cơ chế kiểm tra đăng nhập để không từ chối OPTIONS vì vắng session. Preflight cũng không chứng minh người dùng có role `EDITOR`.

### 3.3. Vì sao POST dạng form vẫn nguy hiểm khi CORS bị từ chối?

Một trang độc hại có thể chứa:

```html
<form action="https://bank.example.test/transfer" method="post">
  <input type="hidden" name="to" value="attacker">
  <input type="hidden" name="amount" value="100">
  <button type="submit">Submit</button>
</form>
```

Đây là ví dụ **tư duy mối đe dọa**, không phải API được khuyến khích triển khai. Nếu người dùng đã đăng nhập ở `bank.example.test` và browser tự gửi cookie hợp lệ trong bối cảnh ấy, server thiếu CSRF protection có thể nhận và xử lý POST. Kẻ tấn công **không cần đọc response** để gây ra chuyển tiền.

Nhiều simple requests (trong cách gọi truyền thống) **không cần preflight**. CORS hạn chế **đọc response** của script ngoài origin, chứ không phải một hàng rào tổng quát chặn mọi yêu cầu có side effect. Cookie `SameSite` có thể chặn cookie trong một số tình huống cross-site, nhưng không phải lý do để bỏ mọi kiểm tra CSRF.

## 4. CORS credentialed requests: origin cụ thể, không dùng wildcard

Credentials có thể gồm cookie và HTTP authentication. Đối với một frontend cross-origin muốn browser gửi cookie, tùy ngữ cảnh cần:

```javascript
fetch("https://api.example.test/api/documents/1", {
  method: "GET",
  credentials: "include"
});
```

Server cho phép origin đó đọc response có credentials:

```http
Access-Control-Allow-Origin: https://app.example.test
Access-Control-Allow-Credentials: true
Vary: Origin
```

**Không kết hợp `Access-Control-Allow-Origin: *` với credentialed request.** Browser sẽ từ chối cho JavaScript đọc response. Khi có nhiều origin tin cậy, server phải kiểm tra allowlist và trả **một origin cụ thể khớp request**, không phản chiếu bất kỳ `Origin` nào không kiểm tra.

Các lưu ý quan trọng:

1. `credentials: "include"` là lựa chọn phía frontend; nó **không thay** xác thực server và không tạo cookie tự động nếu browser/session chưa có.
2. `Access-Control-Allow-Credentials: true` chỉ cho phép JavaScript nhận credentialed response khi origin cũng hợp lệ. Nó **không cấp role**.
3. Cookie cross-site còn chịu chính sách `SameSite`, `Secure`, domain/path, HTTPS và các giới hạn third-party cookies của trình duyệt. CORS thành công **không bảo đảm cookie được gửi**.
4. `Access-Control-Allow-Origin: null` không phải wildcard an toàn; origin `null` có thể phát sinh từ sandbox, file/data URL và các môi trường khó tin cậy.
5. Nếu cache lưu response thay đổi theo origin, cần `Vary: Origin`. Nếu response còn khác nhau theo cookie/login, phải áp dụng `Cache-Control` phù hợp để tránh lộ dữ liệu cá nhân qua shared cache.
6. Tránh `allowedOriginPatterns("*")` cộng `allowCredentials=true` cho nội dung riêng tư: tuy có thể phản chiếu origin theo cấu hình framework, đây thường là **allow-all** không an toàn về mặt chính sách.

**Phân biệt origin và site:** `https://app.example.com` và `https://api.example.com` là **khác origin**, nhưng có thể cùng “site” theo chính sách cookie. `SameSite` và SOP/CORS không đo cùng một ranh giới; một subdomain không đáng tin cậy có thể làm thay đổi phân tích rủi ro CSRF.

## 5. Authentication: ai đang gửi request?

Authentication kiểm chứng danh tính của client/người dùng dựa trên một cơ chế cụ thể, ví dụ:

- Session cookie: browser gửi ID session; server tra session tới principal.
- HTTP Basic: header `Authorization: Basic ...` mang thông tin tài khoản qua encoding Base64 (không phải encryption). **Chỉ dùng với HTTPS thực tế.**
- Bearer token: header `Authorization: Bearer <token>`; server xác thực chữ ký, thời hạn, issuer, audience hoặc introspection theo thiết kế.
- OAuth2/OIDC login: các protocol có token/flow và các bước kiểm chứng; không tự suy ra đó là authorization.
- mTLS/client certificates: xác thực danh tính client ở TLS layer.

Sau khi xác thực, Spring Security có một `Authentication` chứa `principal` và tập `GrantedAuthority`. Controller có thể đọc:

```java
@GetMapping("/api/me")
Map<String, String> me(Authentication authentication) {
    return Map.of("username", authentication.getName());
}
```

*Đây là đoạn minh họa trong controller bảo vệ bởi Spring Security, cần import phù hợp.*

Nếu request thiếu/không hợp lệ credentials khi cần xác thực, API dùng HTTP Basic/Bearer thường phản hồi **401 Unauthorized** cùng cơ chế challenge tương ứng. Với form login, server có thể **redirect tới trang đăng nhập** thay vì 401 JSON. Không suy đoán 401/302 chỉ từ việc thiếu cookie; phụ thuộc `AuthenticationEntryPoint` và request context.

**Không đồng nhất HTTP 401 với CORS:** một frontend có thể thấy lỗi CORS nếu API gửi 401 mà không kèm header cho phép đọc cross-origin; sửa riêng cách tích hợp CORS và authentication. Không mở `/**` thành permitAll chỉ để “hết lỗi CORS”.

### 5.1. Authentication thành công chưa có quyền gì?

Người dùng được xác thực là `reader` nhưng chỉ có role `READER`. Họ được xem tài liệu nhưng **không** được tạo hoặc xóa nếu policy đòi `EDITOR`. Authentication chỉ trả lời “bạn là ai”; hành động còn phải qua authorization.

## 6. Authorization: ai được phép làm gì?

Authorization kiểm tra policy trên **endpoint**, **method**, **resource** và đôi khi **field**:

```text
Principal: reader
Authorities: ROLE_READER
GET /api/documents/1        → allowed
POST /api/documents         → denied

Principal: editor
Authorities: ROLE_EDITOR
GET /api/documents/1        → allowed
POST /api/documents         → allowed nếu CSRF và các điều kiện khác đạt
```

Spring Security cung cấp `authorizeHttpRequests`:

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers(HttpMethod.GET, "/api/documents/**")
        .hasAnyRole("READER", "EDITOR")
    .requestMatchers(HttpMethod.POST, "/api/documents")
        .hasRole("EDITOR")
    .anyRequest().denyAll()
);
```

*Đoạn minh họa cho SecurityFilterChain; ví dụ hoàn chỉnh ở mục 10.*

`hasRole("EDITOR")` mặc định tương ứng authority `ROLE_EDITOR`, không phải string `EDITOR` nếu chưa chỉnh role prefix. `requestMatchers` chỉ bảo vệ theo HTTP path/method phù hợp; controller riêng vẫn cần check ownership và tenant.

### 6.1. Endpoint authorization chưa đủ cho object authorization

Giả sử hai người cùng có role `READER`. API:

```text
GET /api/invoices/101
GET /api/invoices/202
```

Nếu invoice 202 thuộc người khác, policy chỉ `hasRole("READER")` **không đủ**. Cần tra invoice trong phạm vi user/tenant và kiểm tra quyền trên object cụ thể. Đây là rủi ro **Broken Object Level Authorization (BOLA/IDOR)**.

Một mẫu ở service/repository:

```java
// Ý tưởng; không phải chương trình standalone.
Invoice invoice = repository.findById(invoiceId)
        .orElseThrow(NotFoundException::new);
authorizationService.assertCanRead(currentUser, invoice);
return invoice;
```

Với hệ thống multi-tenant, nhiều trường hợp nên query data **đã lọc theo tenant/user** ngay từ DB thay vì fetch object của người khác rồi mới kiểm tra, để giảm lộ metadata. Trả 404 thay vì 403 khi muốn không tiết lộ sự tồn tại là quyết định security/product; đừng dùng 404 như cách thay thế việc kiểm tra quyền thật sự.

### 6.2. 403 không có nghĩa chỉ một lỗi

Cả authorization failure **và CSRF token failure** thường có thể đưa đến HTTP 403. Log/metrics cần tách `AccessDeniedException` do thiếu authority khỏi `MissingCsrfTokenException` hoặc `InvalidCsrfTokenException`. Đừng kết luận người dùng thiếu role chỉ từ status 403.

Nếu CSRF filter nằm trước logic authorization và POST thiếu token, có thể nhận 403 **ngay cả khi người dùng chưa đăng nhập**. Để kiểm tra authentication riêng, thử GET bảo vệ hoặc request state-changing có token hợp lệ.

## 7. CSRF: giả mạo request nhờ credentials tự gắn của browser

CSRF là hành vi website/app khác khiến browser của người dùng đang đăng nhập gửi **request thay đổi trạng thái** tới ứng dụng đích, lợi dụng việc browser có thể tự gửi cookie, HTTP authentication hoặc một số loại credential khác.

Chuỗi tấn công kiểu form:

```text
Nạn nhân đăng nhập https://bank.example
    │
    ├─ Browser lưu session cookie hợp lệ
    │
    ▼
Nạn nhân mở website độc hại https://evil.example
    │
    ├─ Trang độc hại tạo form POST tới bank.example
    ├─ Browser có thể đính session credential theo cookie rules
    ▼
Server bank.example nhận POST
    ├─ Authentication: nạn nhân hợp lệ
    ├─ Authorization: nạn nhân có quyền chuyển tiền
    ├─ Nếu thiếu CSRF protection:
    │     → thực hiện transfer mà người dùng không chủ động thao tác
    └─ Nếu CSRF token sai/thiếu:
          → từ chối trước khi đổi trạng thái
```

**CSRF không đòi attacker đọc response của ngân hàng.** Kẻ tấn công chỉ cần kích hoạt request thành công. Bởi vậy CORS không ngăn được toàn bộ CSRF. Đây là lý do nên yêu cầu token hoặc áp dụng chính sách bảo vệ tương đương cho các phương thức tạo, sửa, xóa khi xác thực dựa vào credentials tự đính kèm.

### 7.1. CSRF token hoạt động thế nào?

Trong mô hình synchronizer token:

1. Server tạo/giữ một token khó đoán gắn với session hoặc ngữ cảnh bảo mật.
2. Frontend **hợp lệ** nhận token qua form HTML/meta/API endpoint được bảo vệ bởi SOP/CORS chính sách chặt chẽ.
3. Khi gửi POST/PUT/PATCH/DELETE, frontend gửi token trong hidden field hoặc header như `X-CSRF-TOKEN`.
4. Server so sánh/verify token với token kỳ vọng. Sai hoặc thiếu → request bị từ chối.

Ví dụ request:

```http
POST /api/documents HTTP/1.1
Cookie: JSESSIONID=<session-cookie>
X-CSRF-TOKEN: <token-đã-được-cấp>
Content-Type: application/json

{"title":"Tài liệu mới"}
```

Một website tấn công có thể khiến browser gửi session cookie trong một số bối cảnh, nhưng thông thường **không đọc được token bí mật từ origin đích** nếu SOP, CORS, XSS protections và cách cấp token được giữ đúng. Token không phải authentication: nắm token mà không có danh tính hợp lệ cũng chưa có quyền làm nghiệp vụ.

### 7.2. Spring Security bảo vệ method nào theo mặc định?

Với Spring Security Servlet mặc định, CSRF protection được bật cho **unsafe HTTP methods**, thường là mọi method trừ:

- `GET`
- `HEAD`
- `OPTIONS`
- `TRACE`

Tức `POST`, `PUT`, `PATCH` và `DELETE` thường yêu cầu CSRF token khi bật CSRF filter. Các phương thức an toàn **không được dùng để thay đổi trạng thái nghiệp vụ**: nếu server dùng GET để xóa data, attacker có thể kích hoạt URL ảnh/link ngay cả khi CSRF configuration xem GET là safe.

“Preflight OPTIONS không cần CSRF” **không** có nghĩa “actual POST không cần CSRF”. Hai request khác nhau về mục đích và credentials.

### 7.3. CSRF có cần cho stateless JWT/Bearer API?

Phụ thuộc **cách credential được gửi**, không chỉ phụ thuộc chữ “JWT”, “REST” hay “stateless”:

| Thiết kế | Browser tự gửi credential qua cross-site request? | Đánh giá CSRF |
| --- | --- | --- |
| Session cookie dùng cho login | Có thể, theo cookie policy | **Cần bảo vệ CSRF** cho state changes |
| HTTP Basic mà browser lưu credentials và gửi tự động | Có thể | **Không mặc định được tắt CSRF** |
| JWT nằm trong cookie tự đính kèm | Có thể | **Vẫn có nguy cơ CSRF** |
| Access token chỉ được JS chủ động gắn trong `Authorization: Bearer ...`, không có ambient cookie/basic auth | Attacker site thường không có token để đính header hợp lệ | CSRF theo kiểu classic thường giảm mạnh; có thể cân nhắc tắt CSRF **cho API đáp ứng thật sự giả định này** |
| Mixed-mode: Bearer header + session cookie ở endpoint khác | Có đường gửi credential tự động | Cần phân loại từng security chain/endpoint; đừng tắt toàn cục |

Nếu token được JS cất trong `localStorage` hoặc memory, nguy cơ **XSS/token theft** là một vấn đề khác; không nói “không cần CSRF” đồng nghĩa “đã an toàn mọi mặt”. Refresh token trong cookie cũng có thể tạo CSRF surface dù access token nằm trong memory.

### 7.4. SameSite và các lớp bổ sung

Cookie `SameSite=Lax`/`Strict` giảm nhiều trường hợp gửi cookie cross-site, nhưng:

- SameSite và **same-origin** khác nhau; các subdomain cùng site không mặc nhiên đáng tin.
- Một số flow cần cookie cross-site, thường yêu cầu `SameSite=None; Secure` và HTTPS, đồng thời bị third-party-cookie policies của browser chi phối.
- Các hành vi navigation/redirect, legacy browser và đường đăng nhập có tính chất riêng.
- Với request có credentials nhạy cảm, nên kết hợp token, strict origin policy, secure cookies và các biện pháp phù hợp.

Ngoài token, có thể kiểm tra `Origin`/`Referer`, Fetch Metadata headers như `Sec-Fetch-Site` và áp dụng custom request protections; đừng dùng một header tùy ý do non-browser client dễ giả để thay authentication.

### 7.5. Double-submit cookie vs session token

Một số SPA dùng cookie chứa CSRF token và JavaScript đọc giá trị ấy để gửi lại trong một header. Cách này phải được thiết kế đúng (binding/HMAC hoặc framework strategy phù hợp) để tránh cookie injection/subdomain attacks. Trong Spring Security, `CookieCsrfTokenRepository` phục vụ một số mô hình này. Tuy nhiên Spring Security 6 có **deferred token** và **XOR masking** để bảo vệ against BREACH trong rendering; việc đưa token ra SPA cần hiểu `CsrfTokenRequestHandler`, không đơn giản là “copy cookie XSRF-TOKEN vào header” với mọi configuration.

Bài demo ở mục 10 cố ý dùng **mặc định session-based CSRF token** và endpoint GET cấp token cho frontend tin cậy, tránh khẳng định các cấu hình SPA/cookie chuyên biệt luôn hoạt động cùng nhau mà không chỉnh handler.

## 8. Ma trận kết quả: cùng request, nhiều cổng kiểm soát

Xét endpoint:

```text
POST /api/documents
Origin: https://app.example.test
Role cần: EDITOR
CSRF protection: enabled
CORS allowlist: https://app.example.test
```

| Tình huống | CORS | Authentication | Authorization | CSRF | Kết quả phổ biến |
| --- | --- | --- | --- | --- | --- |
| Frontend origin được phép, editor đã login, CSRF token đúng | Pass | Pass | Pass | Pass | 201 Created |
| Origin được phép, không có credential, GET endpoint được bảo vệ | Pass | Fail | — | Không áp dụng GET | 401 / login flow |
| Origin được phép, reader có CSRF token hợp lệ, cố POST | Pass | Pass | Fail | Pass | 403 |
| Origin được phép, editor cố POST nhưng thiếu CSRF token | Pass | Pass | Pass theo role | Fail | 403, không tạo dữ liệu |
| Origin lạ, CORS preflight POST không được phép | Fail | Không đánh giá identity ở preflight | — | — | Browser không gửi actual POST; preflight server có thể 403 |
| Origin lạ, simple form POST và credentials tự gửi được | Browser không đọc response | Có thể pass | Có thể pass | Fail nếu CSRF bảo vệ đúng | Server chặn request làm thay đổi trạng thái |
| CORS allow-all public endpoint, không cần login | Pass | N/A | Public | N/A nếu chỉ đọc | Client có thể đọc public response |

**Không thể xác định chính xác một status duy nhất** cho toàn ma trận nếu chưa biết entry point, filter order, preflight type, CSRF policy, resource rules và browser cookie behavior. Ma trận là mô hình giải thích cho ứng dụng điển hình, không phải quy tắc bắt buộc mọi Spring Security app.

### 8.1. 401, 403 và lỗi CORS khác nhau ở đâu?

**401** nghĩa server cần credentials hợp lệ cho resource. Thường có `WWW-Authenticate` khi dùng Basic/Bearer.

**403** nghĩa server hiểu request nhưng từ chối; có thể vì thiếu authority, CSRF token sai hoặc filter khác. Authentication chưa thành công cũng có thể nhận 403 ở một số luồng, nhất là CSRF kiểm tra unsafe method.

**CORS failure** là browser chặn truy cập response hoặc bỏ actual request vì preflight không đạt. Không nhất thiết có HTTP 403: server có thể trả 200 nhưng thiếu header CORS; JavaScript vẫn thấy lỗi. Network tab/backend logs giúp phân biệt.

## 9. SecurityFilterChain: thứ tự xử lý quan trọng

Spring Security Servlet triển khai bằng filter chain (`FilterChainProxy`). MVC controller được gọi **sau** các kiểm tra trong security filters khi request đi qua chain.

Luồng khái niệm:

```text
Browser
  → Servlet container
    → Spring Security FilterChainProxy
       → CORS xử lý trước auth (khi tích hợp đúng)
       → Authentication filters / SecurityContext
       → CSRF token validation cho unsafe methods
       → Authorization decisions
       → DispatcherServlet
          → Spring MVC controller
          → HTTP response
```

Trên thực tế Spring Security có nhiều filters và thứ tự chính xác phụ thuộc version/security chains; sơ đồ **không** cam kết mọi ứng dụng dùng đúng bốn filters với thứ tự minh họa trên. Hãy dùng cấu hình Spring Security thay vì tự tạo nhiều Servlet Filter cạnh tranh thứ tự.

### 9.1. Tại sao CORS phải được xử lý trước authentication?

Preflight không gửi cookie xác thực. Nếu ứng dụng bắt preflight phải có login/session, browser không thể thực hiện cross-origin POST hợp lệ. Spring Security hướng dẫn tích hợp CORS bằng `CorsConfigurationSource` (ví dụ `UrlBasedCorsConfigurationSource`) và `http.cors(...)`, để preflight được xử lý đúng trước authentication.

**Chú ý:** Đừng giải quyết bằng cách tắt authentication cho tất cả endpoints. Chỉ xử lý CORS preflight độc lập; actual requests vẫn yêu cầu credentials/roles/CSRF theo policy.

### 9.2. Spring MVC @CrossOrigin và cấu hình Security

`@CrossOrigin` có thể khai báo CORS cho controller/method MVC. `WebMvcConfigurer#addCorsMappings` khai báo CORS chung ở Spring MVC layer. Nhưng khi dùng Spring Security, đặc biệt với preflight, cần tích hợp CORS đúng vào Security filter chain. Một `CorsConfigurationSource` tường minh giúp thống nhất policy ở filter layer.

Ví dụ:

```java
@CrossOrigin(origins = "https://app.example.test")
@GetMapping("/api/public")
String example() { return "public"; }
```

Đây là đoạn minh họa; **nó không tự cấp quyền truy cập** cho endpoint được bảo vệ. Nếu SecurityFilterChain từ chối vì chưa auth, thêm `@CrossOrigin` không làm user trở thành authenticated. Nếu đồng thời khai báo nhiều nguồn CORS, cần kiểm tra có bị xung đột/override policy hay không.

## 10. Ứng dụng Spring Boot 3 + Java 17: tích hợp đủ bốn lớp

Demo sử dụng:

- HTTP Basic để tạo danh tính `reader` và `editor` dễ quan sát. Password dạng `{noop}` **chỉ dành cho demo**, không dùng production.
- URL authorization: GET document cho READER hoặc EDITOR; POST tạo document chỉ cho EDITOR.
- CORS allowlist một origin `https://app.example.test`; credentialed responses có explicit origin.
- CSRF **bật mặc định** trên unsafe methods; demo tạo CSRF token qua GET trong cùng session.
- `MockMvc` để chứng minh 401, 403 do role, 403 do CSRF, 201 thành công, preflight cho phép/từ chối và allowed-origin response.
- Repository tạm dùng ConcurrentHashMap để tránh setup database; chỉ minh họa security behavior.

### 10.1. Cấu trúc dự án

```text
cors-auth-csrf-demo/
├── pom.xml
└── src/
    ├── main/java/example/
    │   ├── WebSecurityDemoApplication.java
    │   ├── SecurityConfig.java
    │   └── DocumentController.java
    └── test/java/example/
        └── SecurityBoundaryTest.java
```

### 10.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>cors-auth-csrf-demo</artifactId>
    <version>1.0.0</version>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.13</version>
        <relativePath/>
    </parent>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 10.3. WebSecurityDemoApplication.java

```java
package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebSecurityDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebSecurityDemoApplication.class, args);
    }
}
```

### 10.4. SecurityConfig.java

```java
package example;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        return http
                .cors(Customizer.withDefaults())
                // CSRF đang bật theo mặc định; ghi tường minh
                // để không hiểu nhầm REST API luôn tắt CSRF.
                .csrf(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            HttpMethod.GET, "/api/csrf")
                        .authenticated()
                    .requestMatchers(
                            HttpMethod.GET, "/api/documents/**")
                        .hasAnyRole("READER", "EDITOR")
                    .requestMatchers(
                            HttpMethod.POST, "/api/documents")
                        .hasRole("EDITOR")
                    .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "https://app.example.test"));
        config.setAllowedMethods(List.of(
                "GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-CSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    UserDetailsService userDetailsService() {
        UserDetails reader = User.withUsername("reader")
                .password("{noop}reader-pass")
                .roles("READER")
                .build();

        UserDetails editor = User.withUsername("editor")
                .password("{noop}editor-pass")
                .roles("EDITOR")
                .build();

        return new InMemoryUserDetailsManager(reader, editor);
    }
}
```

**Phân tích các phần:**

- `http.cors(Customizer.withDefaults())` tích hợp CORS vào Spring Security. Bean kiểu `UrlBasedCorsConfigurationSource` xác định mapping dưới `/api/**`.
- `setAllowedOrigins(...)` chỉ nhận origin đã khai báo; không dùng wildcard khi `allowCredentials=true`.
- `setAllowedMethods(...)` nêu các method được CORS chấp nhận, nhưng **không phải** HTTP method authorization; muốn POST cần role EDITOR.
- `setAllowedHeaders(...)` cho phép browser preflight các header thực sự cần gửi.
- `setMaxAge(3600L)` là thời gian browser có thể cache kết quả preflight, không phải thời gian login hoặc CSRF token hết hạn.
- `requestMatchers(GET,...).hasAnyRole(...)` và `requestMatchers(POST,...).hasRole(...)` tạo **authorization policy**, độc lập với CORS.
- `.csrf(...)` giữ default CSRF protection; POST của EDITOR vẫn có thể bị 403 khi thiếu token.
- `httpBasic(...)` tạo phương pháp authentication đơn giản. Browser có thể tự gửi cached Basic credentials, nên không dựa vào chữ “Basic” để bỏ CSRF.
- `{noop}` chứa password rõ là **bài học**, tuyệt đối không dùng ngoài demo. Production dùng password encoder phù hợp, secret handling, TLS, rate limiting và cơ chế quản lý danh tính thật.

### 10.5. DocumentController.java

```java
package example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

record CreateDocumentRequest(@NotBlank String title) {}

record DocumentResponse(long id, String title) {}

@RestController
public class DocumentController {

    private final AtomicLong nextId = new AtomicLong(2);
    private final Map<Long, DocumentResponse> documents =
            new ConcurrentHashMap<>();

    public DocumentController() {
        documents.put(1L, new DocumentResponse(1L, "Public sample"));
    }

    @GetMapping("/api/documents/{id}")
    ResponseEntity<DocumentResponse> read(@PathVariable long id) {
        DocumentResponse found = documents.get(id);
        return found == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(found);
    }

    @PostMapping("/api/documents")
    ResponseEntity<DocumentResponse> create(
            @Valid @RequestBody CreateDocumentRequest request) {

        long id = nextId.getAndIncrement();
        DocumentResponse doc =
                new DocumentResponse(id, request.title());

        documents.put(id, doc);
        return ResponseEntity.status(HttpStatus.CREATED).body(doc);
    }

    @GetMapping("/api/csrf")
    Map<String, String> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(
                CsrfToken.class.getName());

        // Chạm vào getToken() để deferred token được sinh/lưu.
        return Map.of(
                "token", token.getToken(),
                "headerName", token.getHeaderName());
    }
}
```

Endpoint `GET /api/csrf` **yêu cầu login** theo URL policy rồi trả token cho frontend tin cậy. Token được server tạo trong session khi accessed, browser cần lưu session cookie để gửi cùng POST. Ở Spring Security 6, cơ chế token mặc định có thể dùng **deferred loading và XOR masking**; frontend nên gửi **giá trị token nhận từ endpoint** trong header mà endpoint trả tên, không suy diễn giá trị của cookie độc lập.

**Lưu ý:** endpoint này có thể được đọc bằng JavaScript từ **origin đã allowlist** khi có credentials hợp lệ và CORS headers đúng. Không cache token qua CDN, không ghi token vào analytics/logs/URL. Đây là mô hình minh họa backend+frontend, chưa phải production account/session lifecycle, cookie setup hay persistence.

### 10.6. Chạy bằng Maven và kiểm tra bằng cURL

Dùng JDK 17+ và Maven:

```bash
mvn test
mvn spring-boot:run
```

GET không có authentication:

```bash
curl -i http://localhost:8080/api/documents/1
```

Kỳ vọng **401 Unauthorized** theo demo HTTP Basic. GET với tài khoản reader:

```bash
curl -i -u reader:reader-pass \
  http://localhost:8080/api/documents/1
```

Kỳ vọng 200, body:

```json
{"id":1,"title":"Public sample"}
```

POST với editor **không có CSRF token**:

```bash
curl -i -u editor:editor-pass \
  -H "Content-Type: application/json" \
  -d '{"title":"Tài liệu mới"}' \
  http://localhost:8080/api/documents
```

Kỳ vọng **403 Forbidden** vì CSRF; editor đã đúng role nhưng điều đó không loại bỏ yêu cầu token.

Lấy token gắn với session và dùng lại. Ví dụ trên shell POSIX có Python 3:

```bash
curl -sS -u editor:editor-pass -c cookies.txt \
  http://localhost:8080/api/csrf > csrf.json

TOKEN=$(python3 -c \
  'import json; print(json.load(open("csrf.json"))["token"])')

curl -i -u editor:editor-pass -b cookies.txt \
  -H "X-CSRF-TOKEN: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Tài liệu mới"}' \
  http://localhost:8080/api/documents
```

Kỳ vọng **201 Created** với body có `id` được cấp và title vừa gửi. ID có thể thay đổi giữa các lần chạy/test nên không hardcode một số cho mọi request. Với Windows PowerShell, dùng `curl.exe`, cú pháp biến và đọc JSON của PowerShell thay cho lệnh shell POSIX ở trên.

**Quan trọng:** cURL **không cưỡng chế CORS như browser**. Nếu bạn tự gửi header `Origin: https://evil.example.test` bằng cURL mà vẫn thấy response/status nào đó, nó không chứng minh browser được phép đọc. Muốn test CORS, cần xác nhận các response headers với preflight và thử trong browser origin tương ứng.

### 10.7. Frontend fetch minh họa

Giả sử website tin cậy đã có phương thức authentication phù hợp và browser giữ credentials cần thiết. Logic client có thể:

```javascript
const api = "https://api.example.test";

const csrfResponse = await fetch(api + "/api/csrf", {
  credentials: "include"
});
if (!csrfResponse.ok) throw new Error("Cannot read CSRF token");

const csrf = await csrfResponse.json();

const saveResponse = await fetch(api + "/api/documents", {
  method: "POST",
  credentials: "include",
  headers: {
    "Content-Type": "application/json",
    [csrf.headerName]: csrf.token
  },
  body: JSON.stringify({ title: "New document" })
});
if (!saveResponse.ok) throw new Error("Cannot create document");
```

**Đây là pseudocode luồng frontend**, không phải một demo browser đăng nhập Basic tự động hoàn chỉnh. Browser cần được xác thực trước; môi trường cross-site cookies phải đáp ứng SameSite/Secure policies; khi dùng HTTP Basic, có thể cần login/challenge/Authorization thích hợp. Không dùng ví dụ để kết luận chỉ thêm `credentials: "include"` là tạo được session hay tự đăng nhập.

Trong trang chạy cùng origin với API, thường **không cần CORS**. CORS chỉ liên quan khi scheme/host/port khác nhau. Không bật CORS rộng chỉ vì dùng fetch.

## 11. MockMvc: kiểm thử tách riêng bốn cổng bảo vệ

File `src/test/java/example/SecurityBoundaryTest.java`:

```java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityBoundaryTest {

    @Autowired
    MockMvc mvc;

    @Test
    void protectedGetWithoutAuthenticationIs401() throws Exception {
        mvc.perform(get("/api/documents/1")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void readerCanReadDocument() throws Exception {
        mvc.perform(get("/api/documents/1")
                    .with(httpBasic("reader", "reader-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.title").value("Public sample"));
    }

    @Test
    void readerCannotPostEvenWithValidCsrf() throws Exception {
        mvc.perform(post("/api/documents")
                    .with(httpBasic("reader", "reader-pass"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Forbidden document\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void editorCannotPostWithoutCsrf() throws Exception {
        mvc.perform(post("/api/documents")
                    .with(httpBasic("editor", "editor-pass"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Missing CSRF\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void editorCanPostWithCsrf() throws Exception {
        mvc.perform(post("/api/documents")
                    .with(httpBasic("editor", "editor-pass"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Allowed document\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Allowed document"));
    }

    @Test
    void allowedPreflightDoesNotRequireLogin() throws Exception {
        mvc.perform(options("/api/documents")
                    .header(HttpHeaders.ORIGIN,
                            "https://app.example.test")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                            "POST")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                            "authorization,content-type,x-csrf-token"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                    "https://app.example.test"))
            .andExpect(header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                    "true"));
    }

    @Test
    void deniedOriginFailsPreflight() throws Exception {
        mvc.perform(options("/api/documents")
                    .header(HttpHeaders.ORIGIN,
                            "https://evil.example.test")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                            "POST"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void allowedOriginStillNeedsAuthentication() throws Exception {
        mvc.perform(get("/api/documents/1")
                    .header(HttpHeaders.ORIGIN,
                            "https://app.example.test")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void allowedOriginCanReadAuthenticatedResponse() throws Exception {
        mvc.perform(get("/api/documents/1")
                    .with(httpBasic("reader", "reader-pass"))
                    .header(HttpHeaders.ORIGIN,
                            "https://app.example.test"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                    "https://app.example.test"));
    }

    @Test
    void csrfEndpointNeedsLoginAndReturnsToken() throws Exception {
        mvc.perform(get("/api/csrf"))
            .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/csrf")
                    .with(httpBasic("editor", "editor-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
            .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
```

**Điểm học tập của từng test:**

- `protectedGetWithoutAuthenticationIs401`: lỗi **authentication**, không phải CORS.
- `readerCannotPostEvenWithValidCsrf`: role không đủ → lỗi **authorization**; token không cấp quyền.
- `editorCannotPostWithoutCsrf`: danh tính/role đúng nhưng thiếu **CSRF token** → 403.
- `editorCanPostWithCsrf`: đủ các điều kiện liên quan → 201.
- `allowedPreflightDoesNotRequireLogin`: **CORS OPTIONS** có thể đạt dù không có session/login.
- `deniedOriginFailsPreflight`: policy CORS từ chối origin; không suy ra authentication của request thực.
- `allowedOriginStillNeedsAuthentication`: allowlist origin không làm user authenticated.
- `csrfEndpointNeedsLoginAndReturnsToken`: token issuance qua endpoint được bảo vệ.
- Các GET với `Origin` xác minh header CORS mà vẫn giữ policy authentication/authorization.

**Giới hạn:** MockMvc không chạy browser SOP/CORS enforcement. Nó kiểm tra **server response headers và Spring Security decisions**, chứ không thể mô phỏng hoàn toàn browser sẽ chặn JavaScript đọc response thế nào. Với integration test end-to-end, dùng browser thật ở hai origins, proxy/HTTPS và cookie settings như production.

## 12. Tắt CSRF chỉ khi có cơ sở rõ ràng

Đôi khi người phát triển chép:

```java
http.csrf(csrf -> csrf.disable());
```

vì “REST dùng JSON nên CSRF không cần”. **Đây là kết luận sai:** một endpoint dùng JSON vẫn có thể dùng session cookie, browser HTTP Basic hoặc JWT trong cookie. Media type JSON không quyết định có CSRF risk hay không; một số attack đường qua simple requests, content-type coercion, upload/form route, login/logout endpoint hoặc cấu hình sai vẫn có thể tạo side effect.

Chỉ cân nhắc tắt CSRF ở **một security chain/endpoint thực sự stateless** khi xác định rằng credentials **không bao giờ được browser tự đính kèm** và các cross-site requests không thể thừa hưởng quyền của user. Khi đó vẫn phải giữ authentication/authorization, HTTPS, token validation, CORS allowlist hợp lý và XSS protection.

Ví dụ cấu hình **minh họa riêng** cho API OAuth2 Resource Server:

```java
// Chỉ phù hợp nếu request không chấp nhận ambient credentials
// và auth bắt buộc là Bearer header được cấp hợp lệ.
http.securityMatcher("/stateless/**")
    .csrf(csrf -> csrf.disable())
    .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
```

*Không chèn đoạn này vào dự án Boot chạy ở mục 10. Để chạy cần OAuth2 Resource Server dependency, JwtDecoder/issuer configuration và authorization policy đầy đủ.* Dùng nhiều SecurityFilterChain cần hiểu matcher/order của từng chain, không vô tình tạo endpoint chưa được bảo vệ.

## 13. Những lỗi thiết kế thường gặp

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Xem CORS là cơ chế xác thực | Origin được phép nhưng anonymous có thể gọi nếu API permitAll | Bảo vệ endpoint bằng authentication |
| Xem CORS allowlist là authorization | User role không đủ vẫn mong được sửa | Kiểm tra role/authority/ownership ở server |
| Xem CORS là CSRF prevention | Form/simple request có thể gây side effect dù script không đọc response | Bật CSRF protection nếu có ambient credentials |
| Chặn OPTIONS preflight bằng login | Browser không gửi actual POST dù user đã login | Tích hợp CORS trước Security auth |
| Mở `Access-Control-Allow-Origin: *` + credentials | Browser từ chối credentialed response | Origin cụ thể từ allowlist; policy đúng |
| Phản chiếu mọi Origin cùng `Access-Control-Allow-Credentials: true` | Bất kỳ origin nào có thể đọc credentialed data | Allowlist nghiêm ngặt, không phản chiếu tùy ý |
| Thấy 403 thì chắc thiếu role | CSRF failure cũng thường 403 | Tách Missing/InvalidCsrfToken khỏi authorization denial |
| Thấy lỗi CORS thì API chắc trả 403 | Server có thể trả 200 nhưng JS không được đọc | Xem Network tab, OPTIONS/actual response headers |
| Chỉ bảo vệ `/api/**` bằng role | IDOR/BOLA với dữ liệu của user khác | Kiểm tra permission từng object/tenant |
| Dùng JWT trong cookie rồi tắt CSRF | Browser có thể tự gửi JWT cookie | Bảo vệ CSRF như session cookie |
| Dùng HTTP Basic mà coi luôn stateless an toàn CSRF | Browser có thể cache/gửi credential tự động | Phân tích browser credential behavior; giữ CSRF khi cần |
| Dùng `{noop}` cho mật khẩu thật | Lộ password khi config/DB bị đọc | Hash bằng encoder mạnh, TLS, secret policy |
| Client gửi `credentials:"include"` nhưng không thấy cookie | SameSite/Secure/third-party-cookie policy | Kiểm tra cookies, HTTPS và domain/path |
| Cho GET thực hiện cập nhật state | GET thường không CSRF-check, dễ bị trigger | Chuyển side effect sang unsafe methods |
| Tin `Origin` request header như danh tính | Non-browser có thể giả header | Authentication riêng bằng credentials |
| Chỉ dựa vào SameSite khi có subdomain không tin cậy | Same-site khác same-origin; có thể bị khai thác | Token, origin policy, trust boundaries |
| Token CSRF lộ qua log, URL, CDN | Attacker có thể tái sử dụng token | Không log/cache/shared expose; kiểm soát nơi cấp |
| Vô hiệu hóa toàn bộ CSRF để test 201 | Bỏ lớp bảo vệ production | Dùng test `with(csrf())` và sửa client token flow |
| Phát CORS headers cho lỗi nhưng quên auth | Frontend đọc 401/403 được nhưng data chưa an toàn | CORS, auth, authorization là các lớp tách biệt |
| Dùng browser localStorage cho bearer rồi khẳng định không có rủi ro | XSS có thể lấy access token | Hardening XSS, CSP, token rotation, hạn chế scope |
| Cho mọi method/header/origin tùy ý | Cross-origin attack surface quá rộng | Chỉ allowlist origins/methods/headers cần dùng |

## 14. Checklist triển khai thực tế

1. **Threat model**: app browser hay machine-to-machine? Credentials có tự được đính kèm không?
2. **Phân loại endpoint**: public GET, authenticated read, write, admin, object ownership, upload, login/logout.
3. **Chọn authentication**: session+form login, OIDC, HTTP Basic nội bộ, Bearer JWT/OAuth2, mTLS…; không trộn không kiểm soát.
4. **Authorization**: URL policy deny-by-default và checks ở service cho tenant/ownership; kiểm soát method/object/field.
5. **CORS**: chỉ cho origins tin cậy, methods/headers cần thiết; credentialed origins không dùng `*`; preflight đi trước auth.
6. **CSRF**: giữ protection cho browser dùng cookie/ambient credentials; client nhận và gửi token an toàn.
7. **HTTP semantics**: GET/HEAD không tạo state; unsafe methods có validation, transaction, audit.
8. **Cookies và sessions**: `HttpOnly`, `Secure`, `SameSite`, session fixation/logout, cookie scope, third-party cookie policies.
9. **Headers và caching**: `Vary: Origin`, private/no-store cho dữ liệu cá nhân/CSRF token; HTTPS và HSTS phù hợp.
10. **XSS**: CSRF token không chống được XSS; escape HTML, tránh unsafe eval/inline scripts, CSP.
11. **Test**: 401 anonymous, 403 reader write, 403 missing CSRF, 201 editor+token, preflight allowed/denied, origin vs role, object-level ownership.
12. **Quan sát lỗi**: log request ID, endpoint, authenticated principal/authority theo chính sách privacy và loại policy failure; không log token, password, session cookie.
13. **Deploy**: HTTPS trên reverse proxy và chính xác scheme/origin; cấu hình forwarded headers đúng để CORS/redirect/cookies không lệch.
14. **Browser thực**: test credentialed fetch, OPTIONS, SameSite cookies trên domains thật; MockMvc chưa đủ để kết luận browser behavior.

## 15. Tự kiểm tra kiến thức

**1.** Frontend tại `https://app.example.test` gọi `https://api.example.test` có phải cross-origin không?  
**Đáp án:** Có, host khác; dù có thể same-site ở một số chính sách cookie.

**2.** Server trả HTTP 200 nhưng thiếu `Access-Control-Allow-Origin`, browser JS đọc body được không?  
**Đáp án:** Không theo CORS nếu cross-origin. Server có thể đã xử lý request.

**3.** Cho phép CORS với origin X có làm user từ X tự đăng nhập không?  
**Đáp án:** Không. Authentication cần credentials hợp lệ.

**4.** User đã xác thực nhưng thiếu role EDITOR, CORS và CSRF đều hợp lệ; POST tạo document được không?  
**Đáp án:** Không. Authorization phải từ chối.

**5.** Editor đã login và có quyền tạo document nhưng thiếu CSRF token, kết quả trong demo?  
**Đáp án:** HTTP 403, không tạo document.

**6.** Preflight OPTIONS có cần session cookie không?  
**Đáp án:** Thông thường không có credentials theo browser CORS preflight, nên CORS phải được xử lý trước auth.

**7.** Vì sao CORS không thay thế CSRF?  
**Đáp án:** Browser có thể gửi một số cross-origin requests gây side effect dù attacker không đọc được response.

**8.** `Access-Control-Allow-Origin: *` và `Access-Control-Allow-Credentials: true` cho credentialed fetch có hợp lệ không?  
**Đáp án:** Không, browser không chấp nhận wildcard origin cho credentialed response.

**9.** Browser gửi cookie tự động còn API dùng JWT trong cookie. CSRF có thể tồn tại không?  
**Đáp án:** Có, vì JWT trong cookie vẫn có ambient credential behavior.

**10.** Bearer header do JS tự đính và server tuyệt đối không dùng cookie/basic credentials có cần CSRF kiểu session không?  
**Đáp án:** Có thể giảm/không cần classic CSRF trong threat model đó, nhưng vẫn cần các bảo vệ authentication, authorization, CORS, XSS và token security.

**11.** CORS preflight POST thành công có chứng minh user đủ quyền POST không?  
**Đáp án:** Không. Actual POST vẫn cần login/role/token.

**12.** HTTP 403 có luôn là lỗi authorization không?  
**Đáp án:** Không. CSRF failure và nhiều policy violations khác cũng có thể gây 403.

**13.** GET yêu cầu authentication sai thường trả gì trong HTTP Basic API?  
**Đáp án:** 401, thường có `WWW-Authenticate`.

**14.** URL authorize rule `hasRole("READER")` đủ để ngăn người đọc invoice của user khác không?  
**Đáp án:** Không; cần object-level/tenant ownership authorization.

**15.** Cookie `SameSite=Lax` có tự biến mọi cross-origin request thành an toàn không?  
**Đáp án:** Không, SameSite khác SOP/CORS, policy phụ thuộc điều hướng/context và còn rủi ro khác.

**16.** Vì sao không dùng cURL để “chứng minh browser không gặp CORS”?  
**Đáp án:** cURL không tự enforce browser SOP và CORS.

**17.** Với Spring Security test, vì sao dùng `with(csrf())` trong POST?  
**Đáp án:** Giả lập CSRF token hợp lệ để test tiếp authorization/controller thay vì bị filter từ chối trước.

**18.** Login bằng HTTP Basic mà request vẫn bị 403 khi tạo document, nên kiểm tra gì?  
**Đáp án:** CSRF token và role/policy, sau đó validation và exception logs; không chỉ CORS.

**19.** `X-CSRF-TOKEN` có phải là bearer access token không?  
**Đáp án:** Không. CSRF token chứng minh request hợp lệ theo policy chống giả mạo; nó không thay authentication.

**20.** CORS allowlist chỉ có `https://app.example.test`, request từ website độc hại có thể gọi API qua backend script không?  
**Đáp án:** Có thể gửi HTTP request ở ngoài browser; server phải dùng authentication/authorization để bảo vệ, không dựa vào CORS.

## 16. Tài liệu gốc nên đọc

- [Spring Security — CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html): CORS trước Spring Security, integration với `UrlBasedCorsConfigurationSource`.
- [Spring Security — CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html): token repository, deferred token, request handler và kiểm thử.
- [Spring Security — Authorize HTTP Requests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html): `authorizeHttpRequests`, rules theo URL/method và authorities.
- [Spring Security — Authentication Architecture](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html): `Authentication`, principal, authorities và SecurityContext.
- [Spring Security — Testing CSRF](https://docs.spring.io/spring-security/reference/servlet/test/mockmvc/csrf.html): `SecurityMockMvcRequestPostProcessors.csrf()`.
- [Spring Framework — CORS](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html): CORS trong Spring MVC.
- [MDN — CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS): origin, preflight, credentials và browser enforcement.
- [MDN — Same-origin policy](https://developer.mozilla.org/en-US/docs/Web/Security/Same-origin_policy): ranh giới scheme/host/port.
- [OWASP Cross-Site Request Forgery Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html): synchronizer/double-submit tokens, SameSite, origin checks.
- [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): deny-by-default và object-level permissions.

**Quy tắc cuối cùng:** **CORS** quyết định browser JS origin nào được chia sẻ response; **authentication** xác định danh tính; **authorization** kiểm tra quyền cụ thể; **CSRF protection** kiểm tra request làm thay đổi trạng thái có bị website khác giả mạo nhờ credentials tự gửi hay không. Bảo mật web đúng là **sự phối hợp của cả bốn**, không phải bật một header rồi coi các lớp khác đã xong.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cors-web-security-006">Ranh giới trách nhiệm của Spring Web và Spring Security</a>

<details>
<summary>Xem chi tiết</summary>

# Ranh giới trách nhiệm của Spring Web và Spring Security

## 1. Bài toán: cùng là HTTP nhưng không cùng trách nhiệm

Một REST API viết bằng Spring Boot có thể nhận request, đọc JSON, kiểm tra dữ liệu, xác thực người dùng, kiểm tra quyền, chống CSRF, áp dụng CORS và trả lỗi. Vì cả hai thư viện đều tham gia xử lý HTTP, lập trình viên dễ nhầm rằng `@RestController` tự bảo vệ endpoint, hoặc `SecurityFilterChain` tự biết cách validate DTO.

**Spring Web (Spring MVC)** chịu trách nhiệm chính về *web request/response application layer*: định tuyến tới controller, bind tham số, chọn converter đọc/ghi body, gọi handler, validate đầu vào theo cấu hình, resolve view hoặc serialize JSON, và xử lý các lỗi MVC.

**Spring Security** chịu trách nhiệm chính về *bảo vệ request và method*: cơ chế xác thực (authentication), quyết định quyền truy cập (authorization), lưu/truyền `SecurityContext`, phòng chống một số tấn công web như CSRF, thiết lập security headers, tích hợp CORS đúng thứ tự, quản lý session/authentication khi dùng các cơ chế tương ứng.

Hai thư viện **kết hợp với nhau nhưng không thay thế nhau**. Ví dụ:
- `@GetMapping("/orders/{id}")` định tuyến ở Spring MVC.
- `http.authorizeHttpRequests(...)` kiểm tra người dùng có được đi qua một URL/method ở security filter chain.
- `@PreAuthorize("hasRole('ADMIN')")` bảo vệ method/service khi method security được bật.
- `@Valid @RequestBody CreateOrder` kiểm tra cấu trúc/constraint của DTO, không xác minh user có quyền tạo đơn.
- `@RestControllerAdvice` biến lỗi từ MVC/service thành JSON, nhưng không mặc nhiên bắt được lỗi bị filter chặn **trước** khi vào DispatcherServlet.

### Kết quả học tập

1. Vẽ đúng pipeline Servlet container → security filters → `DispatcherServlet` → controller/service.
2. Phân biệt `SecurityFilterChain` với `HandlerMapping`, `AuthorizationFilter` với `@RequestMapping`.
3. Biết đặt CORS, CSRF, authentication, authorization, validation và business invariants ở tầng nào.
4. Phân biệt HTTP 400, 401, 403, 404, 415, 500 và nơi phát sinh chúng.
5. Viết một ứng dụng Spring Boot 3.x Java 17+ có Spring MVC + Spring Security + method security + MockMvc tests thực tế.
6. Tránh các sai lầm khi nhiều filter chains, nhiều `@ControllerAdvice`, chuyển sang stateless bearer token hoặc xử lý session/cookie.

**Phạm vi:** Servlet-based Spring MVC, Java 17+, ví dụ Spring Boot 3.3.5 với Spring Security 6.3.x theo BOM tương ứng. Spring WebFlux có `SecurityWebFilterChain` và kiến trúc reactive khác, không áp dụng nguyên xi cách cấu hình dưới đây.

## 2. Vị trí mỗi thành phần trong pipeline HTTP

~~~text
Browser / client
      |
      v
Reverse proxy / load balancer / API gateway (nếu có)
      |
      v
Servlet container (Tomcat, Jetty ...)
      |
      v
Servlet FilterChain
      |
      +--> Những filter ngoài Spring Security, nếu có
      |
      +--> DelegatingFilterProxy (thường bean "springSecurityFilterChain")
               |
               v
           FilterChainProxy
               |
               +--> chọn SecurityFilterChain phù hợp request
                      |
                      +--> CORS / CSRF / security headers
                      +--> SecurityContext, authentication
                      +--> authorization, exception translation
                      |
                      v
               tiếp tục filter chain
      |
      v
DispatcherServlet (Spring MVC)
      |
      +--> HandlerMapping / @RequestMapping
      +--> HandlerAdapter / argument resolvers
      +--> @RequestBody / @RequestParam / validation
      +--> Controller method --> Service/domain/data
      +--> ReturnValueHandler / HttpMessageConverter / ViewResolver
      +--> HandlerExceptionResolver / @ControllerAdvice
      |
      v
HTTP response đi ngược qua filters/proxy
      |
      v
Browser / client
~~~

Sơ đồ là mô hình kiến trúc, **không bảo đảm một thứ tự tuyệt đối cho mọi filter cụ thể**: thứ tự và số filter phụ thuộc cấu hình/phiên bản, dispatcher type, custom filters, nhiều chains, async/error dispatch. Điểm quyết định là security filters có thể **dừng request trước khi MVC chạy**; nếu đi tiếp thì MVC mới định tuyến đến controller.

### 2.1. `DelegatingFilterProxy`

Servlet container quản lý Servlet `Filter`, còn Spring quản lý các bean. `DelegatingFilterProxy` làm cầu nối: từ filter trong container đến bean Spring, thông thường tên `springSecurityFilterChain`. Spring Boot/Spring Security thường auto-configure cầu nối; không cần tự khai báo `web.xml` trong Boot starter hiện đại.

### 2.2. `FilterChainProxy` và `SecurityFilterChain`

`FilterChainProxy` quản lý nhiều `SecurityFilterChain`; **chỉ chain đầu tiên khớp request được áp dụng**. Một chain có thể yêu cầu session/cookie login, chain khác xử lý bearer token, chain khác chỉ áp dụng cho API; nếu path không khớp chain nào, request có thể **không được bảo vệ bởi các chains đó**.

Ví dụ khái niệm:

~~~text
Chain #1: /api/**       -> bearer token, stateless, API policies
Chain #2: /admin/**     -> web login, session, CSRF
Chain #3: /**           -> fallback deny/explicit policies
~~~

Đây không phải bộ cấu hình thực thi. **Thứ tự chain là một phần chính sách bảo mật**: chain `/**` đứng trước chain chuyên biệt có thể làm chuyên biệt không bao giờ được chọn. Không đánh đồng “match chain” với “permit URL”:
- `securityMatcher("/api/**")` quyết định **chain nào** xử lý request.
- `requestMatchers("/api/public/**").permitAll()` bên trong `authorizeHttpRequests` quyết định **quy tắc quyền** của request mà chain đã chọn.

`permitAll` không đồng nghĩa bỏ các filter CSRF, CORS, security headers hoặc logging; trong khi một đường bị loại hoàn toàn bởi `web.ignoring()` có thể bỏ qua các cơ chế bảo vệ khác. Thông thường nên ưu tiên `permitAll` cho tài nguyên công khai nếu vẫn muốn áp dụng security headers/chain.

### 2.3. `DispatcherServlet` và `HandlerMapping`

Sau security filters, `DispatcherServlet` phối hợp `HandlerMapping`/`HandlerAdapter` tìm `@Controller` hoặc `@RestController`. Spring MVC quyết định:
- `GET /api/orders/42` có handler không?
- `@PathVariable("id")`, `@RequestParam`, `@RequestHeader`, `@RequestBody` lấy dữ liệu ở đâu?
- JSON có giải mã được không?
- `@Valid`/method validation báo lỗi gì?
- Return DTO đi qua converter nào? Có render view hay trả body?
- Có `@ExceptionHandler`/`ResponseEntityExceptionHandler` nào xử lý exception được ném trong MVC không?

Spring Security không tự tạo endpoint `POST /api/orders` chỉ vì đã cấu hình `requestMatchers(HttpMethod.POST, "/api/orders")`. Ngược lại, MVC handler có tồn tại **không bảo đảm** user được phép tới nó. Hai cấu hình cần đồng bộ trong test.

## 3. Bảng trách nhiệm và ranh giới tin cậy

| Câu hỏi | Thành phần chính | Không nên nhầm với |
| --- | --- | --- |
| URL nào gọi method Java? | Spring MVC `HandlerMapping`, `@RequestMapping` | Quyền user được gọi method |
| Đọc JSON thành DTO? | MVC `HttpMessageConverter` và argument resolver | Authentication |
| Bind và validate form/query/body? | MVC + Jakarta Bean Validation | Permission/ownership |
| Ai đang gọi? | Spring Security authentication, `Authentication` | Field `userId` do client tự gửi |
| User được phép thao tác? | Security request rules, method security và service/domain authorization | CORS allow-origin |
| Chống form POST giả mạo dùng cookie? | CSRF protection và thiết kế cookie/session | `@Valid` trên DTO |
| Cross-origin browser có được đọc response? | CORS policy được MVC/Security phối hợp | Xác thực/ủy quyền người dùng |
| Thiết lập HSTS, frame options, CSP ...? | Security header configuration, proxy/web server phù hợp | JSON validation |
| Body của API và status thành công? | MVC controller/return handlers | Security filter quyết định mọi status |
| Lỗi thiếu credential/không đủ quyền? | Security `AuthenticationEntryPoint`, `AccessDeniedHandler` | Mặc định `@RestControllerAdvice` |
| Business invariant và phân quyền tài nguyên? | Domain/service + authorization logic | Chỉ kiểm URL prefix |

Một request có thể **vượt qua CORS nhưng bị 401**, có thể **đã authenticate nhưng bị 403**, hoặc **được phép hoàn toàn nhưng body JSON sai nên 400**. Đây là ba nguyên nhân khác nhau, không nên gom vào thông báo “CORS lỗi” hay “Spring controller không chạy”.

## 4. Authentication khác Authorization

**Authentication** trả lời “ai là principal?”. Trong Spring Security, một `Authentication` thường chứa principal, credentials (đã/đang được xử lý), authorities và trạng thái authenticated. Cơ chế xác thực có thể là username/password, HTTP Basic, form login, bearer token, OAuth2/OIDC hoặc custom; không mặc định phải dùng phiên Servlet cho mọi ứng dụng.

**Authorization** trả lời “principal có được thực hiện việc này không?”. Ví dụ `hasRole('ADMIN')`, `hasAuthority('orders:write')`, kiểm tra owner/tenant trong domain service, hoặc phương thức từ chối khi account bị khóa.

~~~text
Request --> xác thực identity --> kiểm quyền --> xử lý business
                |                   |
                |                   +-- không đủ quyền --> 403 thường gặp
                +-- thiếu/không hợp lệ --> 401 thường gặp với HTTP Basic/Bearer
~~~

Không tuyệt đối hóa status:
- HTTP Basic/Bearer thiếu xác thực thường dẫn đến **401 Unauthorized** (tên HTTP lịch sử dù nghĩa là “chưa xác thực”), có thể có `WWW-Authenticate`.
- Principal đã đăng nhập nhưng thiếu role thường **403 Forbidden**.
- `CsrfFilter` chặn POST thiếu CSRF token cũng có thể **403** dù credential đúng.
- Endpoint cố ý giấu sự tồn tại tài nguyên có thể trả 404 theo chính sách.
- Authentication mechanism, exception handlers và thứ tự filters ảnh hưởng status thực tế.

### 4.1. `SecurityContextHolder` và principal trong controller

Spring Security gắn `SecurityContext` với request/thread execution theo cơ chế đã cấu hình; `Authentication` có thể được truy cập qua `SecurityContextHolder` hoặc method argument `@AuthenticationPrincipal`. Trong MVC, tham số `Principal` hoặc `Authentication` cũng có thể được resolver cung cấp khi tích hợp Security.

~~~java
@GetMapping("/api/private/me")
public Map<String, String> me(Principal principal) {
    return Map.of("user", principal.getName());
}
~~~

Đây là fragment minh họa; không tự chạy độc lập. Không lấy `userId` từ body rồi tin nó là danh tính người dùng. Với API nhiều tenant, tenant ID từ path/header **phải được so khớp với principal/quyền**; chỉ kiểm `authenticated()` không đủ bảo vệ quyền truy cập đối tượng khác.

### 4.2. Role và authority

Spring Security thường lưu role với tiền tố `ROLE_`. Khi dùng `roles("ADMIN")` trong `UserDetails`, authority được tạo là `ROLE_ADMIN`. Biểu thức `hasRole('ADMIN')` kiểm `ROLE_ADMIN`, còn `hasAuthority('orders:read')` kiểm chính authority đó. Không tùy ý trộn `hasRole('ROLE_ADMIN')` với `hasRole('ADMIN')` mà không hiểu prefix và method configuration.

Role phù hợp nhóm quyền chung; permission theo tài nguyên, ownership và tenant có thể cần quyết định tại service/domain. `hasRole('ADMIN')` ở URL không thay thế mọi business rule.

## 5. Request-level authorization và method security

### 5.1. URL rules bên trong `SecurityFilterChain`

Một cấu hình request authorization có thể nói:

~~~java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/public/**").permitAll()
    .requestMatchers("/api/private/**").authenticated()
    .anyRequest().denyAll())
~~~

Đây là fragment trong `HttpSecurity`. Nó quyết định request có đủ điều kiện qua `AuthorizationFilter`; **chưa biết handler thuộc controller nào, DTO có hợp lệ không, hoặc dữ liệu có thuộc user không**.

Chú ý thứ tự matcher: đặt quy tắc chuyên biệt trước, fallback `anyRequest()` sau. `permitAll` chỉ nói không yêu cầu authenticated principal cho route; CSRF vẫn có thể áp dụng cho unsafe HTTP method và CORS vẫn kiểm origin.

### 5.2. `@EnableMethodSecurity` và `@PreAuthorize`

Với Spring Security 6.x, annotation `@PreAuthorize` trên method/service cần `@EnableMethodSecurity` (hoặc cấu hình tương đương). Boot security starter **không mặc định bật method-level authorization** cho mọi project.

~~~java
@Service
class ReportService {
    @PreAuthorize("hasRole('ADMIN')")
    public Report readReport(long id) {
        return new Report(id, "internal");
    }
}
~~~

Fragment chưa khai báo/import kiểu `Report`. Ý nghĩa: khi method được Spring-managed proxy chặn theo cấu hình, người dùng thiếu role ADMIN bị từ chối ngay cả khi URL-level chỉ yêu cầu `authenticated()`.

**Ranh giới AOP proxy:** self-invocation gọi từ method này sang method khác **trong cùng object** thường không đi qua proxy, nên `@PreAuthorize` của method đích có thể không chạy. Không đặt mọi kiểm soát quan trọng ở một method nội bộ rồi gọi qua `this.method()`. Test đường gọi thật qua Spring bean boundary, hoặc dùng security strategy khác phù hợp.

Trong reactive stack annotation/method interception có khác biệt về ngữ cảnh; tài liệu ở đây tập trung Servlet MVC.

### 5.3. Defence in depth

Thiết kế tốt dùng nhiều lớp:
- Tầng gateway/network hạn chế nguồn hoặc tốc độ nếu cần; không coi gateway là người quyết định duy nhất.
- Tầng Security filter chặn request chưa xác thực và rule URL thô.
- Method security bảo vệ service use cases, kể cả khi một controller khác gọi cùng service.
- Domain/repository kiểm owner/tenant/data scope, tránh IDOR (Insecure Direct Object Reference).
- Bean Validation kiểm tra cấu trúc/chất lượng input; không thay permission.

Không dùng một role string do client tự gửi trong JSON để quyết định được phép cập nhật trường `isAdmin`. Nguồn authority phải đến từ authentication và authorization configuration, không từ body.

## 6. Validation khác Security: HTTP 400 không phải 401/403

Một request:

~~~http
POST /api/private/notes HTTP/1.1
Authorization: Basic <encoded-credentials>
Content-Type: application/json

{"text":""}
~~~

Có nhiều bước:
1. Browser/client gửi HTTP request.
2. Security CORS/CSRF/authentication/authorization có thể từ chối trước MVC.
3. Nếu Security cho qua, MVC parse JSON vào DTO.
4. `@Valid` kiểm `@NotBlank` và có thể ném `MethodArgumentNotValidException`.
5. Controller/service chỉ chạy khi request đã qua những ranh giới bắt buộc.

Như vậy cùng một request có thể nhận:
- **401** nếu chưa xác thực theo cơ chế HTTP Basic.
- **403** nếu có credentials nhưng thiếu CSRF token (với CSRF đang bật) hoặc không đủ quyền.
- **400** nếu qua bảo vệ nhưng body JSON sai hoặc vi phạm validation.
- **201** nếu hợp lệ và method tạo resource thành công (nếu API thiết kế status 201).

Không dựa vào thứ tự cố định giữa mọi lỗi trên một request chứa đồng thời nhiều vấn đề (CSRF, auth, body invalid). Kết quả phải kiểm thử theo SecurityFilterChain thực tế, không đoán từ controller annotation.

### 6.1. `@Valid` và `@Validated` không là security rules

`@Valid @RequestBody` kích hoạt Bean Validation trên DTO theo điều kiện Spring MVC. `@Validated` của Spring thường dùng validation groups/AOP method validation, **khác** `@EnableMethodSecurity` và `@PreAuthorize`. Cả hai có từ “method validation/security” nhưng không giải quyết cùng một mục tiêu.

Một field ownerId có `@NotNull` chỉ bảo đảm nó khác null, **không chứng minh** user hiện tại sở hữu ownerId. Business authorization phải so với principal và dữ liệu ở trusted store.

## 7. CORS là điểm giao giữa Web và Security

CORS dùng `Origin` và response headers để browser quyết định script từ origin khác có thể đọc response hay không. Nó **không xác thực principal** và **không ngăn curl/server-side HTTP client gọi API**.

Spring MVC cung cấp `@CrossOrigin` và `WebMvcConfigurer.addCorsMappings`; Spring Security có `http.cors(...)` để tích hợp cơ chế CORS vào security filter chain đúng thời điểm.

~~~text
Cross-origin browser:
  OPTIONS preflight (thường không mang cookie)
     -> CORS policy đánh giá Origin, method, request headers
     -> Nếu được phép, actual request mới gửi
     -> Actual request vẫn qua authentication, CSRF, authorization
     -> Actual response vẫn phải có CORS headers phù hợp
~~~

Nếu security filter đòi session/login trước khi xử lý OPTIONS, browser có thể bị 401/403 ở preflight, không bao giờ gửi POST thật. Giải pháp là cấu hình CORS sớm/phù hợp, không phải bỏ authentication ở actual POST.

### 7.1. Một policy mẫu

~~~java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("https://app.example.test")
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "Authorization", "X-CSRF-TOKEN")
                .allowCredentials(true)
                .maxAge(600);
    }
}
~~~

Đây là phần Java chưa có imports; phần app hoàn chỉnh bên dưới có file đầy đủ. `allowCredentials(true)` yêu cầu origin cụ thể thay vì `"*"`. Browser vẫn có thể chặn cookie theo `SameSite`, `Secure` và third-party cookie policy. Ngược lại, chỉ vì CORS cho phép một origin không có nghĩa tất cả user từ origin đó được quyền đọc các tài nguyên riêng tư.

**Không đồng nhất `Access-Control-Allow-Headers` với permission:** cho phép gửi `Authorization` header không có nghĩa token hợp lệ. Không dùng Origin header làm authentication proof, và không tự echo mọi origin vô điều kiện.

## 8. CSRF, authentication cookie và ý nghĩa của stateless

### 8.1. Tại sao có CSRF?

Nếu browser tự đính kèm session cookie (hoặc một credential tự gửi như Basic trong một số ngữ cảnh), website độc hại có thể khiến browser phát request thay đổi dữ liệu đến site đích. Dù response bị CORS chặn đọc, request **có thể đã được server xử lý** nếu đó là simple request. CSRF protection yêu cầu một điều kiện bổ sung mà trang độc hại không dễ đáp ứng.

Spring Security mặc định cung cấp CSRF protection cho nhiều ứng dụng Servlet; những method “unsafe” như POST, PUT, PATCH, DELETE thường yêu cầu token hợp lệ khi CSRF được bật.

**CORS không thay CSRF**; `@Valid` và HTTP method mapping cũng không.

### 8.2. Vì sao demo không tắt CSRF?

Để minh họa ranh giới, ví dụ hoàn chỉnh giữ CSRF **bật**. Một POST với username/password đúng nhưng thiếu CSRF token có thể nhận 403 trước khi đến controller. Test POST có cả authentication và `csrf()` sẽ kiểm được input/response của MVC.

Trong resource server nhận **bearer token chỉ qua Authorization header** và không dùng credentials do browser tự đính kèm, ứng dụng có thể có mô hình CSRF khác và quyết định bỏ CSRF có chủ đích. Nhưng cấu hình `SessionCreationPolicy.STATELESS` **không tự chứng minh** rằng mọi browser client miễn nhiễm CSRF; phải đánh giá cơ chế credential và điểm tạo side effects trước khi tắt.

### 8.3. Session state và API stateless

`SessionCreationPolicy.STATELESS` nói Spring Security không tạo/dùng `HttpSession` để lấy `SecurityContext` theo chính sách đó. Nó không ngăn hoàn toàn code ứng dụng gọi `request.getSession(true)`. HTTP Basic có thể xác thực mỗi request; bearer token API thường không cần session login. Web UI login bằng form thường dùng session và CSRF token.

Vì vậy đừng đưa một cấu hình “stateless + csrf.disable()” vào mọi ứng dụng Spring Security như công thức tối ưu. Chọn xác thực/cookies/CSRF theo threat model.

## 9. Các HTTP error paths: ai thực sự trả response?

| Tình huống | Điểm xử lý thường gặp | Status điển hình |
| --- | --- | --- |
| Không có/không hợp lệ HTTP Basic credential trên route riêng tư | Security authentication + `AuthenticationEntryPoint` | 401 |
| Có principal nhưng thiếu role từ request-level rule | Security `AccessDeniedHandler` | 403 |
| POST thiếu CSRF token (CSRF bật) | Security `CsrfFilter`/access denied processing | 403 |
| CORS preflight không hợp lệ | CORS processor ở filter hoặc handler mapping | Thường 403 hoặc browser chặn |
| Route đúng, body JSON malformed | Spring MVC converter / `HandlerExceptionResolver` | 400 |
| `@Valid` DTO không đạt | Spring MVC validation / advice | 400 |
| Media type không được handler nhận | Spring MVC mapping/converter | 415 |
| Không có handler khớp | MVC có thể trả 404 **nếu request đi qua Security** | 404 |
| Method bảo vệ bởi `@PreAuthorize` và không đủ role | Method security proxy, lỗi đi ngược call chain | 403 thường gặp |
| Exception nội bộ trong service | Handler/advice/container theo cấu hình | 500 |
| Controller return DTO bình thường | MVC return value handler/converter | 200/201... theo code |

Lưu ý: với `anyRequest().denyAll()`, một URL chưa có MVC handler vẫn có thể nhận 401/403 **trước** khi MVC kết luận 404. Do đó **không dùng HTTP status duy nhất để suy luận controller mapping có tồn tại**. Khi debug, kiểm request đã qua filter chain chưa.

### 9.1. Vì sao `@RestControllerAdvice` không bắt mọi Security exception?

`@RestControllerAdvice` được MVC chọn qua `HandlerExceptionResolver` trong `DispatcherServlet`. Lỗi xảy ra **ngoài MVC** như 401 từ `AuthenticationEntryPoint` hoặc 403 được filter chain xử lý sẽ không tự động đi qua advice.

Spring Security dùng:
- `AuthenticationEntryPoint` khi cần bắt đầu/đòi xác thực.
- `AccessDeniedHandler` khi quyết định từ chối quyền phù hợp.
- `ExceptionTranslationFilter` để chuyển một số security exceptions thành hành vi HTTP.

Nếu API yêu cầu error schema RFC 9457 đồng nhất, có thể viết các handler trên trả `ProblemDetail` hoặc gọi một shared error serializer, nhưng phải giữ **status 401/403 chính xác**, headers như `WWW-Authenticate` khi protocol đòi, và không lộ auth details. Không dùng catch-all `@ExceptionHandler(Exception.class)` rồi trả 400/500 cho mọi lỗi an ninh.

### 9.2. Method security exception có thể xuất hiện trong MVC call

`@PreAuthorize` đặt trên service gọi từ controller thường ném `AccessDeniedException` bên trong call chain MVC. Tùy `HandlerExceptionResolver`, filter exception translation và custom advice, ngoại lệ có thể đi ngược về Security filter và trở thành 403. Nếu viết một advice catch-all nuốt `AccessDeniedException` và biến nó thành 500/200, bạn có thể làm thay đổi HTTP semantics và log. Hãy test đúng URL/service với đầy đủ filter chain.

## 10. Ví dụ Spring Boot 3.3.5: Web + Security đúng ranh giới

Ứng dụng demo có:
- Public GET không cần đăng nhập.
- GET `/api/private/me` yêu cầu HTTP Basic và trả principal name.
- GET `/api/private/reports/{id}` yêu cầu authenticated ở URL và **ADMIN tại service**.
- POST `/api/private/notes` cần HTTP Basic, **CSRF token** và DTO body hợp lệ.
- CORS cho một frontend origin cụ thể. OPTIONS preflight được xét mà không cần cookie.
- MockMvc tests giữ nguyên Security filter chain, kiểm 200/201, 400, 401, 403 và preflight.

Không có database/persistent login. Username/password hard-coded chỉ để **demo local**, không được đưa vào production. Độ dài các đoạn code được giữ ở mức đọc hiểu nhưng là các file Java hợp lệ khi đặt đúng cấu trúc.

~~~text
web-security-boundary/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/boundary/
    │   ├── BoundaryApplication.java
    │   ├── config/SecurityConfig.java
    │   ├── config/CorsConfig.java
    │   ├── domain/ReportService.java
    │   └── web/BoundaryController.java
    └── test/java/com/example/boundary/
        └── BoundaryIntegrationTest.java
~~~

### 10.1. Gradle dependencies

`settings.gradle`:

~~~groovy
rootProject.name = 'web-security-boundary'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

Các starter:
- Web: Servlet MVC, Jackson, controller/JSON.
- Security: `SecurityFilterChain`, HTTP Basic, CSRF, request authorization.
- Validation: Jakarta Bean Validation provider cho `@NotBlank`.
- `spring-security-test`: helpers `httpBasic()`, `csrf()` trong MockMvc.

Cấu hình ví dụ cố ý dùng Spring Boot 3.3.5 / Security 6.x APIs. Nếu dùng Boot 4.x / Security 7.x, đối chiếu migration guide vì DSL, package/module và deprecated APIs có thể thay đổi; không trộn code version theo docs “current”.

### 10.2. Entry point

`src/main/java/com/example/boundary/BoundaryApplication.java`:

~~~java
package com.example.boundary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BoundaryApplication {
    public static void main(String[] args) {
        SpringApplication.run(BoundaryApplication.class, args);
    }
}
~~~

### 10.3. Security filter chain

`src/main/java/com/example/boundary/config/SecurityConfig.java`:

~~~java
package com.example.boundary.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/private/**").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("alice")
                        .password(encoder.encode("local-user-password"))
                        .roles("USER")
                        .build(),
                User.withUsername("admin")
                        .password(encoder.encode("local-admin-password"))
                        .roles("ADMIN")
                        .build());
    }
}
~~~

**Vì sao mỗi dòng nằm ở đây?**
- `cors(...)` kết nối policy CORS MVC với security flow, để preflight được xử lý trước việc cần auth.
- `csrf(...)` giữ kiểm tra CSRF cho unsafe methods, giúp chứng minh POST thiếu token bị 403 ngay cả khi đăng nhập đúng.
- Public/Private matchers chỉ quyết định quyền ở request level.
- `anyRequest().denyAll()` là fallback đóng; với một URL không tồn tại nhưng rơi vào fallback có thể thấy 401/403 thay vì 404.
- `httpBasic(...)` bật cơ chế authentication ở lớp Security; MVC không tự giải mã username/password ở controller.
- `@EnableMethodSecurity` cần thiết để `@PreAuthorize` trên ReportService thực sự được thực thi.
- UserDetailsService in-memory và password hard-coded là local demo, không phải mẫu production user store. Việc dùng BCrypt là để không lưu raw password trong cấu trúc `UserDetails`.
- Không có `formLogin(...)`, vì bài tập trung HTTP Basic. Không gắn `@PreAuthorize` trên các controller để giả vờ có method security nếu chưa bật proxy support.

### 10.4. CORS policy do Spring Web định nghĩa, Security tích hợp

`src/main/java/com/example/boundary/config/CorsConfig.java`:

~~~java
package com.example.boundary.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("https://app.example.test")
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "Authorization", "X-CSRF-TOKEN")
                .allowCredentials(true)
                .maxAge(600);
    }
}
~~~

Đây là policy tường minh ở Web layer; Security `http.cors(...)` bảo đảm request đi qua xử lý CORS phù hợp trong security chain. Chỉ origin `https://app.example.test` được phép; wildcard `"*"` không thể kết hợp với credentialed CORS. Preflight có thể được chấp thuận **không cần xác thực**, còn actual GET/POST vẫn chịu `authorizeHttpRequests`, authentication và CSRF theo method.

Demo để `allowCredentials(true)` nhằm thể hiện giao diện credentialed; điều này **không tự khiến frontend gửi cookie**. Cookie còn phụ thuộc `fetch(credentials)`, SameSite, Secure và browser policy; HTTP Basic header không tự được CORS cho phép nếu không qua authentication.

Không cần thêm custom CorsFilter thứ hai khi policy này đã được Security/MVC tích hợp đúng. Nếu production có API gateway xử lý OPTIONS hoặc nhiều SecurityFilterChain, phải test toàn chuỗi cụ thể.

### 10.5. Service được bảo vệ bằng method security

`src/main/java/com/example/boundary/domain/ReportService.java`:

~~~java
package com.example.boundary.domain;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    public record Report(long id, String summary) {}

    @PreAuthorize("hasRole('ADMIN')")
    public Report read(long id) {
        return new Report(id, "internal-demo");
    }
}
~~~

Security request rules `/api/private/**.authenticated()` chỉ đòi một user hợp lệ, còn `@PreAuthorize` yêu cầu authority `ROLE_ADMIN` cho **một use case cụ thể**. Vì controller gọi bean `ReportService` được Spring quản lý qua proxy, method security kiểm thêm quyền.

Không gọi `this.read(id)` từ một method khác **bên trong cùng bean** nếu mục tiêu là rely vào AOP interceptor của `@PreAuthorize`. Với hệ thống thật, các use cases nhạy cảm còn cần kiểm object ownership/tenant từ database.

### 10.6. Web controller: chỉ xử lý HTTP/application input

`src/main/java/com/example/boundary/web/BoundaryController.java`:

~~~java
package com.example.boundary.web;

import com.example.boundary.domain.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BoundaryController {

    private final ReportService reports;

    public BoundaryController(ReportService reports) {
        this.reports = reports;
    }

    public record NoteInput(@NotBlank String text) {}
    public record NoteReceipt(String text, String actor) {}

    @GetMapping("/public/ping")
    public Map<String, String> ping() {
        return Map.of("status", "UP");
    }

    @GetMapping("/private/me")
    public Map<String, String> me(Principal principal) {
        return Map.of("user", principal.getName());
    }

    @GetMapping("/private/reports/{id}")
    public ReportService.Report report(@PathVariable("id") long id) {
        return reports.read(id);
    }

    @PostMapping(path = "/private/notes",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NoteReceipt> create(
            Principal principal,
            @Valid @RequestBody NoteInput input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new NoteReceipt(input.text(), principal.getName()));
    }
}
~~~

`@RestController`, `@GetMapping`, `@PostMapping`, `@PathVariable`, `@RequestBody` và `@Valid` đều là phần Web/MVC + Bean Validation. Không có code “tự kiểm HTTP Basic” trong controller. Đối với private endpoints, `Principal` được cung cấp khi security authentication đã thành công.

`NoteInput` kiểm tra dữ liệu text không blank, không chứng minh user có quyền tạo note cho đối tượng khác. Demo trả receipt tạm và không lưu dữ liệu; production cần service/persistence, constraints về độ dài/nội dung, audit và transaction. `ReportService.read()` là nơi method security làm việc; controller vẫn map report thành JSON qua converter.

## 11. Kiểm thử tích hợp MockMvc với SecurityFilterChain thật

`src/test/java/com/example/boundary/BoundaryIntegrationTest.java`:

~~~java
package com.example.boundary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BoundaryIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void publicRouteIsAccessibleWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/public/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void privateRouteRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/private/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void basicAuthenticationSetsPrincipalForMvc() throws Exception {
        mvc.perform(get("/api/private/me")
                        .with(httpBasic("alice", "local-user-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value("alice"));
    }

    @Test
    void methodSecurityBlocksNonAdmin() throws Exception {
        mvc.perform(get("/api/private/reports/9")
                        .with(httpBasic("alice", "local-user-password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void methodSecurityAllowsAdmin() throws Exception {
        mvc.perform(get("/api/private/reports/9")
                        .with(httpBasic("admin", "local-admin-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.summary").value("internal-demo"));
    }

    @Test
    void authenticatedPostWithoutCsrfStillFails() throws Exception {
        mvc.perform(post("/api/private/notes")
                        .with(httpBasic("alice", "local-user-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validPostWithAuthenticationAndCsrfReachesController()
            throws Exception {
        mvc.perform(post("/api/private/notes")
                        .with(httpBasic("alice", "local-user-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("hello"))
                .andExpect(jsonPath("$.actor").value("alice"));
    }

    @Test
    void invalidDtoIsMvcValidationNotAuthenticationFailure()
            throws Exception {
        mvc.perform(post("/api/private/notes")
                        .with(httpBasic("alice", "local-user-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void corsPreflightIsProcessedBeforeAuthentication() throws Exception {
        mvc.perform(options("/api/private/notes")
                        .header("Origin", "https://app.example.test")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers",
                                "content-type,x-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin",
                        "https://app.example.test"))
                .andExpect(header().string("Access-Control-Allow-Credentials",
                        "true"));
    }

    @Test
    void disallowedOriginCannotUsePreflight() throws Exception {
        mvc.perform(options("/api/private/notes")
                        .header("Origin", "https://evil.example.test")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers",
                                "content-type"))
                .andExpect(status().isForbidden());
    }
}
~~~

**Test phân tách trách nhiệm:**
- `publicRoute...` chứng minh `permitAll`, không cần Basic credential.
- `privateRoute...` nhận 401 trước khi controller hoạt động.
- `basicAuthentication...` chứng minh Security xác thực, MVC nhận principal.
- `methodSecurityBlocks...` và `methodSecurityAllows...` chứng minh role ADMIN được kiểm ở service, không chỉ URL.
- `authenticatedPostWithoutCsrf...` chứng minh CSRF là ranh giới riêng của authentication.
- `validPost...` chứng minh đủ auth + CSRF thì MVC mới đọc/validate body và trả 201.
- `invalidDto...` với auth/CSRF đầy đủ trả 400 do Web/validation.
- `corsPreflight...` chứng minh OPTIONS không cần username/password để kiểm CORS policy.
- `disallowedOrigin...` kiểm allowlist, không echo Origin tùy ý.

MockMvc có Spring Security test helpers nhờ `spring-security-test`. Không dùng `@AutoConfigureMockMvc(addFilters=false)` cho các test về auth/CSRF: làm vậy bỏ filter chain và biến 401/403 thành kết quả sai. Không chỉ test controller trực tiếp bằng gọi Java method nếu mục tiêu là xác minh HTTP security boundary.

Nếu code về Spring Security hoặc validation được tùy chỉnh trong dự án thực, statuses/body có thể đổi theo entry point/advice. Test ở đây mô tả app demo với cấu hình hiện tại. Để kiểm tra browser cookie, SameSite, reverse proxy và preflight cache, cần thêm end-to-end HTTP/browser tests.

## 12. Chạy ứng dụng qua curl: chứng minh từng tầng

Tạo Gradle wrapper tương thích với Java 17/Spring Boot 3.3 nếu project chưa có. Khi đã có wrapper:

~~~bash
./gradlew test
./gradlew bootRun
~~~

Trên Windows dùng `.\gradlew.bat test` và `.\gradlew.bat bootRun`. Hai lệnh chạy ở terminal/tác vụ phù hợp; `bootRun` thường giữ server chạy ở port 8080.

### 12.1. Public GET

~~~bash
curl -i 'http://localhost:8080/api/public/ping'
~~~

Dự kiến status 200, body:

~~~json
{"status":"UP"}
~~~

### 12.2. Private GET không có credentials

~~~bash
curl -i 'http://localhost:8080/api/private/me'
~~~

Dự kiến HTTP **401** (có thể có `WWW-Authenticate`). Không phải thiếu controller: request bị Security yêu cầu xác thực.

### 12.3. Private GET có Basic credentials

~~~bash
curl -i -u alice:local-user-password \
  'http://localhost:8080/api/private/me'
~~~

Dự kiến HTTP **200**:

~~~json
{"user":"alice"}
~~~

`curl -u` gửi credential HTTP Basic qua header Authorization sau encoding, **không mã hóa an toàn trên đường truyền nếu dùng HTTP thật**. Demo local dùng HTTP cho dễ kiểm; production phải dùng HTTPS và không đặt mật khẩu demo trong Git/config public.

### 12.4. Thiếu role ADMIN ở service

~~~bash
curl -i -u alice:local-user-password \
  'http://localhost:8080/api/private/reports/9'
~~~

Dự kiến **403 Forbidden**, dù request đã được xác thực ở SecurityFilterChain. Đó là quyết định của `@PreAuthorize` trên `ReportService.read`, không phải do `@GetMapping`.

~~~bash
curl -i -u admin:local-admin-password \
  'http://localhost:8080/api/private/reports/9'
~~~

Dự kiến **200**:

~~~json
{"id":9,"summary":"internal-demo"}
~~~

### 12.5. POST có Basic nhưng thiếu CSRF

~~~bash
curl -i -u alice:local-user-password \
  -X POST 'http://localhost:8080/api/private/notes' \
  -H 'Content-Type: application/json' \
  -d '{"text":"hello"}'
~~~

Dự kiến **403** do CSRF khi cấu hình demo giữ token validation. Đừng kết luận password sai chỉ từ status này. Để thử thành công trong integration test, dùng `SecurityMockMvcRequestPostProcessors.csrf()` như mục 11.

Với HTTP client thật, muốn POST hợp lệ phải lấy token từ cơ chế `CsrfTokenRepository` và gửi đúng cookie/header/parameter theo ứng dụng. Demo chưa mở endpoint cấp CSRF token, nên **không bịa một token hay lệnh curl thành công**. Nếu cần SPA cookie-based, cân nhắc `CookieCsrfTokenRepository` và thiết kế endpoint/CSRF token request handler phù hợp phiên bản, đồng thời bảo vệ against XSS.

### 12.6. Preflight OPTIONS không mang Basic credentials

~~~bash
curl -i -X OPTIONS 'http://localhost:8080/api/private/notes' \
  -H 'Origin: https://app.example.test' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type,x-csrf-token'
~~~

CORS config của demo cho phép origin, method, headers; status 2xx và `Access-Control-Allow-Origin: https://app.example.test` (thường 200). **Không có nghĩa browser đã được cấp quyền POST**: actual request vẫn cần authentication, CSRF và authorization.

Một preflight từ `https://evil.example.test` sẽ bị CORS từ chối (thường 403) trong app. curl không tự chạy cơ chế CORS như trình duyệt, nó chỉ gửi đúng HTTP request bạn mô tả.

## 13. Ranh giới Spring Security và CORS khi có nhiều chains

### 13.1. Multiple SecurityFilterChain

Khi xây ứng dụng vừa có web UI dùng form login, vừa có REST API dùng bearer token, thường cần nhiều chains. Ví dụ thiết kế khái niệm:

~~~text
@Order(1): /api/**    -> resource server, bearer auth, API authorization
@Order(2): /**        -> form login, session, CSRF, web pages
~~~

Không copy ví dụ này thành code runnable; cần dependency OAuth2 Resource Server/JWT, issuer/audience validation, session/CSRF policy, error handlers và matcher order cụ thể. Hãy thiết kế sao cho **không có gap** giữa các chains và tránh chain catch-all đứng trước chain chuyên biệt.

### 13.2. `securityMatcher` khác `requestMatchers`

~~~java
// Fragment khái niệm, không phải file cấu hình đầy đủ:
http.securityMatcher("/api/**")
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/public/**").permitAll()
        .anyRequest().authenticated());
~~~

`securityMatcher` quyết định request có được xử lý bởi chain này; `requestMatchers` quyết định quyền bên trong chain. Nếu bạn chỉ có chain giới hạn `/api/**` mà không có fallback chain cho `/admin/**`, admin routes có thể nằm ngoài SecurityFilterChain đó. **Kiểm tra `FilterChainProxy` và logs/debug chain matching** trong các integration tests quan trọng.

### 13.3. MVC matcher và Security matcher cần nhất quán

Spring MVC có semantics của URL patterns, path variables và context/servlet path; Spring Security có `RequestMatcher` phù hợp theo config. Mismatched pattern có thể tạo vùng không bảo vệ hoặc từ chối nhầm; nhất là khi custom servlet path, encoded path, reverse proxy rewrites, trailing slash, matrix variables.

Đừng dựa vào string-prefix check tự chế như `request.getRequestURI().startsWith("/admin")` làm authorization. Dùng matcher Spring Security đúng và test cả encoding/normalization nếu ứng dụng có input URL phức tạp.

## 14. Security headers và trách nhiệm hạ tầng

Spring Security có cơ chế phát security response headers (ví dụ cache-control cho protected responses, `X-Content-Type-Options`, `X-Frame-Options`, HSTS trên HTTPS khi cấu hình phù hợp). Spring MVC chịu trách nhiệm content type/response serialization; reverse proxy hoặc server có thể thiết lập TLS, HSTS, Content Security Policy và các headers khác.

| Nhu cầu | Thành phần thường chịu trách nhiệm | Lưu ý |
| --- | --- | --- |
| HTTPS termination | Proxy/load balancer/container | Forwarded headers phải được xử lý đáng tin |
| HSTS | Spring Security/proxy | Chỉ có ý nghĩa khi HTTPS và host đáp ứng policy |
| CSP cho HTML/JS | Security headers hoặc proxy | Cần policy phù hợp frontend, không bật wildcard script vô tội vạ |
| CORS allowlist | MVC `CorsConfiguration` + Security CORS integration | Không thay authentication |
| Session cookie flags | Container/Spring Boot/Security/proxy | `Secure`, `HttpOnly`, `SameSite` là chính sách khác CORS |
| Error serialization | MVC `ProblemDetail`/advice; Security entry point/denied handler | Đồng nhất schema cần hai đường xử lý |
| Rate limiting | Gateway/filter/service chuyên trách | Spring Security core không mặc định thay thế mọi rate limiter |
| Authorization dữ liệu/tenant | Security method + domain/repository | URL role chưa đủ |

Không khẳng định mọi security header đều được bật mặc định và đúng cho mọi product. Trong triển khai thật, hãy xem response thực qua browser/HTTP và thiết lập security policy có chủ đích.

## 15. Sai lầm thường gặp và cách chẩn đoán

| Triệu chứng | Nguyên nhân | Cách xác minh |
| --- | --- | --- |
| Thấy `@RestController` nên nghĩ route an toàn | Controller annotation không là authentication/authorization | Kiểm `SecurityFilterChain` và negative tests |
| Security cho `/api/**` nhưng URL trả 404 | Chỉ có security matcher, MVC handler không tồn tại | Kiểm MVC mappings |
| Controller breakpoint không chạy, HTTP 401 | Filter chain yêu cầu authentication trước MVC | Inspect Basic/bearer entry point và filter chain |
| Đã đăng nhập mà POST bị 403 | Thiếu CSRF hoặc quyền | Kiểm CSRF token và AccessDeniedHandler |
| HTTP 403 khi đọc report dù `authenticated()` | `@PreAuthorize` yêu cầu role ADMIN | Kiểm service method proxy/authorities |
| `@PreAuthorize` không có tác dụng | Chưa bật `@EnableMethodSecurity` hoặc self-invocation | Kiểm bean/proxy và integration tests |
| Basic user có `ADMIN` mà vẫn bị chặn | Nhầm role vs authority/prefix | Kiểm `ROLE_ADMIN` và `hasRole('ADMIN')` |
| CORS preflight 401 | Security đòi auth trước xử lý CORS | `http.cors(...)`, source policy và chain order |
| OPTIONS 200 nhưng POST vẫn 403 | CORS chỉ cho phép browser gửi; actual thiếu auth/CSRF | Inspect actual request filters |
| Browser báo CORS nhưng backend log 500 | Error response không có allow-origin | Kiểm status/log, CORS trên error path |
| Cookie không gửi dù CORS cho phép | SameSite/Secure/domain/third-party policy | Browser devtools và fetch credentials |
| `@ControllerAdvice` không bắt Basic auth 401 | Lỗi xử lý ở filter trước DispatcherServlet | Tạo `AuthenticationEntryPoint` nếu cần custom JSON |
| Catch-all advice biến AccessDeniedException thành 500 | Advice nuốt security exception trong MVC | Giữ semantics, test full filter chain |
| Unknown route trả 403 thay 404 | `anyRequest().denyAll()` chặn trước MVC | Xem fallback policy |
| Security test luôn nhận 200 | Test bỏ filter bằng `addFilters=false` | Bật filter chain, dùng `spring-security-test` |
| MVC test thiếu 400 validation | Thiếu validation provider/`@Valid` hoặc request chưa qua Security | Bổ sung starter và test auth/CSRF trước |
| Hai filter security chạy bất thường | Đăng ký filter thủ công và qua SecurityFilterChain | Kiểm double registration/filter order |
| Nhiều security chains, path bị lọt | Sai `securityMatcher` hoặc thứ tự `@Order` | Test từng route + fallback |
| Stateless nhưng vẫn có JSESSIONID | Component khác tạo session, không chỉ SecurityContext | Trace `getSession(true)`, filters/MVC |
| CORS allow-origin `*` và credentials=true | Browser không chấp nhận wildcard credentialed | Dùng allowlist chính xác |
| Client có `userId` trong JSON và sửa được đơn người khác | Thiếu object-level authorization | Kiểm principal với entity owner/tenant ở service |

## 16. Khi nào dùng API nào? Cây quyết định

~~~text
Yêu cầu cần xử lý?
|
+-- Định tuyến HTTP URL/method?          -> Spring MVC @RequestMapping
+-- Đọc query/path/header/JSON?          -> MVC argument resolvers/converters
+-- Kiểm JSON/form DTO?                  -> Jakarta Bean Validation @Valid
+-- Biến return value thành JSON?        -> @RestController / HttpMessageConverter
|
+-- Xác định user/client?                -> Spring Security Authentication
+-- User có được gọi route không?        -> authorizeHttpRequests
+-- User có được gọi service method?     -> @EnableMethodSecurity + @PreAuthorize
+-- User có sở hữu tài nguyên không?     -> domain authorization với principal
+-- Ngăn forged cookie-authenticated POST? -> CSRF policy
+-- CORS browser cross-origin?          -> MVC CorsConfiguration + Security cors
+-- Bảo vệ session/cookie/security headers? -> Security/container/proxy
|
+-- Tạo error JSON?
      +-- MVC parse/validation/domain exception -> @RestControllerAdvice
      +-- 401/403 từ filters                 -> AuthenticationEntryPoint
                                               / AccessDeniedHandler
~~~

**Thứ tự thiết kế hợp lý:** xác định authentication mechanism và threat model → định nghĩa request-level và method/domain authorization → xác định CSRF/CORS/session/header policies → định nghĩa Web DTO/validation/response → test xuyên filter chain và MVC. Không xây controller “cho chạy được” rồi mới cố bọc security bằng vài regex URL.

## 17. Checklist triển khai và review

1. Kiểm chính xác Spring Boot/Spring Framework/Security versions; dùng API/docs khớp version.
2. Vẽ request path từ Servlet container qua `DelegatingFilterProxy`/`FilterChainProxy` tới `DispatcherServlet`.
3. Định nghĩa một hoặc nhiều `SecurityFilterChain` có matcher và fallback rõ ràng; test chain order.
4. Chọn authentication method hợp với client, tránh hard-coded credential ở production.
5. Bật request authorization, và `@EnableMethodSecurity` nếu dùng `@PreAuthorize`.
6. Kiểm domain ownership/tenant/data scope; tránh IDOR dù role hợp lệ.
7. Xác định nơi bật/giữ CSRF dựa trên cookie/credential model; không disable chỉ vì là REST.
8. Cấu hình CORS allowlist ở nguồn rõ ràng, tích hợp Security trước authentication cho preflight.
9. Thiết kế request DTO + Bean Validation riêng khỏi authentication/authorization.
10. Chuẩn hóa 400 từ MVC và 401/403 từ Security mà không làm mất headers/protocol semantics.
11. Giữ filter chain thật trong MockMvc tests; test public/private, role, method security, CSRF, CORS, invalid DTO.
12. Kiểm cookies/TLS/forwarded headers, response headers và error paths qua E2E/proxy.
13. Tránh self-invocation cho method security và duplicate filter registration.
14. Kiểm application logs và security diagnostics có kiểm soát; không log password/token/cookie.
15. Đừng giả định Spring Security tự định tuyến MVC hay MVC tự bảo vệ endpoint.

### Câu hỏi tự kiểm tra

**1.** Spring MVC controller nhận JSON sai và user chưa xác thực thì HTTP sẽ luôn 400?

**Trả lời:** Không. Security filters có thể dừng request trước khi MVC đọc JSON; với HTTP Basic route private, response có thể 401. Khi muốn kiểm lỗi JSON, test với auth/CSRF hợp lệ.

**2.** Vì sao `@GetMapping("/admin")` không đảm bảo chỉ admin vào?

**Trả lời:** `@GetMapping` chỉ định tuyến. Phải thiết lập request/method/domain authorization riêng.

**3.** `permitAll()` có đồng nghĩa vô hiệu hóa CSRF/CORS không?

**Trả lời:** Không. Nó chỉ cho qua một rule authorization; các filter bảo vệ khác có thể tiếp tục kiểm.

**4.** Tại sao method có `@PreAuthorize` vẫn chạy nếu gọi từ `this.method()`?

**Trả lời:** Spring AOP proxy thường không intercept self-invocation. Dùng Spring bean boundary/test phù hợp.

**5.** CORS preflight được phép có nghĩa POST sẽ thành công?

**Trả lời:** Không. Actual POST vẫn phải qua authentication, CSRF, authorization, MVC validation và business logic.

**6.** Ai thường tạo HTTP 401 cho unauthenticated Basic request?

**Trả lời:** Spring Security authentication entry point, không phải controller `@ExceptionHandler` của MVC.

**7.** Tại sao có thể nhận 403 thay vì 404 ở route không tồn tại?

**Trả lời:** `anyRequest().denyAll()` ở filter chain có thể chặn trước khi request đến `DispatcherServlet`.

**8.** Vai trò của `@Valid` có thay `@PreAuthorize` không?

**Trả lời:** Không. `@Valid` kiểm dữ liệu; `@PreAuthorize` kiểm quyền gọi method.

## 18. Tài liệu chính thức

- [Spring Security 6.3 — Servlet Architecture](https://docs.spring.io/spring-security/reference/6.3/servlet/architecture.html): `DelegatingFilterProxy`, `FilterChainProxy`, `SecurityFilterChain` và thứ tự filter.
- [Spring Security 6.3 — Authorize HTTP Requests](https://docs.spring.io/spring-security/reference/6.3/servlet/authorization/authorize-http-requests.html): `authorizeHttpRequests`, `requestMatchers` và matcher scope.
- [Spring Security — Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): `@EnableMethodSecurity`, `@PreAuthorize`, AOP proxy và các annotations khác.
- [Spring Security — CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html): CSRF protection trong ứng dụng Servlet và SPA considerations.
- [Spring Security — CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html): preflight phải được xử lý trước authentication, tích hợp với Spring MVC.
- [Spring Security — Exception Handling](https://docs.spring.io/spring-security/reference/servlet/architecture.html#servlet-exceptiontranslationfilter): `ExceptionTranslationFilter`, `AuthenticationEntryPoint` và `AccessDeniedHandler`.
- [Spring Framework — Spring MVC DispatcherServlet](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html): handler mapping, adapter, view và exception resolution.
- [Spring Framework — Spring MVC Controllers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html): mappings, argument resolvers, response bodies.
- [Spring Framework — Validation trong Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html): `@Valid`, method validation và các loại lỗi.
- [Spring Boot 3.3 — Security](https://docs.spring.io/spring-boot/3.3/reference/web/spring-security.html): auto-configuration và cách ứng dụng tùy chỉnh `SecurityFilterChain`.

**Ghi nhớ:** Spring Web giải quyết việc **request được ánh xạ, đọc, validate và phản hồi như thế nào**; Spring Security giải quyết việc **ai đang gọi, được phép làm gì và request được bảo vệ ra sao**. CORS là phần giao, nhưng không phải authentication; CSRF và authorization là ranh giới riêng. Kiểm thử đủ **filter chain + DispatcherServlet + service** mới phản ánh đúng hành vi và an toàn của endpoint.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cors-web-security-007">ForwardedHeaderFilter, header do proxy cung cấp và ranh giới tin cậy</a>

<details>
<summary>Xem chi tiết</summary>

# ForwardedHeaderFilter, header do proxy cung cấp và ranh giới tin cậy trong Spring MVC

> **Phạm vi:** Spring Web MVC (Servlet stack), Java 17+, Spring Boot 3.x/Spring Framework 6.x, triển khai sau Nginx/ingress/load balancer/reverse proxy. Bài học giải thích `Forwarded` và `X-Forwarded-*`, cách Spring khôi phục scheme/host/port/prefix của URL công khai, cấu hình `ForwardedHeaderFilter` và `server.forward-headers-strategy`, cũng như **ranh giới tin cậy giữa client, proxy và ứng dụng**. Header forwarded do HTTP client tự gửi là **dữ liệu không đáng tin**; chỉ được sử dụng sau khi một proxy được kiểm soát đã **xóa và tái tạo** chúng. Bài có ví dụ Spring Boot chạy được, kiểm tra local, ví dụ cấu hình proxy minh họa, chiến lược chỉ loại bỏ header và checklist kiểm thử an toàn.

## 1. Bài toán thực tế: public HTTPS nhưng backend nhìn thấy HTTP

Giả sử người dùng mở:

```text
https://app.example.test/api/orders
```

Một reverse proxy nhận HTTPS, kết thúc TLS rồi gọi ứng dụng Java trong mạng riêng:

```text
Browser
  https://app.example.test:443
       |
       | TLS
       v
Trusted reverse proxy / ingress
       |
       | HTTP nội bộ
       v
Spring Boot / Tomcat
  http://10.10.0.15:8080/api/orders
```

Nếu ứng dụng chỉ đọc metadata của kết nối nội bộ, nó có thể thấy:

| Thuộc tính Servlet | Backend trực tiếp | Người dùng bên ngoài |
| --- | --- | --- |
| `request.getScheme()` | `http` | `https` |
| `request.getServerName()` | `10.10.0.15` hoặc internal hostname | `app.example.test` |
| `request.getServerPort()` | `8080` | `443` |
| `request.isSecure()` | `false` | Kết nối browser → proxy có TLS |
| Context/prefix | Có thể không có | Có thể là `/portal` |

Điều này ảnh hưởng đến link do server sinh, ví dụ:
- Header `Location` của redirect.
- URI tài nguyên được trả trong JSON hoặc HTML.
- URL trong email, canonical URL, OpenAPI server URL hay callback URL nếu code dựa vào request.
- Logic xử lý secure redirects/cookies hoặc các quyết định phụ thuộc scheme/host nếu được triển khai không cẩn thận.

Một ứng dụng cấu hình sai có thể tạo liên kết như `http://10.10.0.15:8080/api/orders/42` thay vì `https://app.example.test/api/orders/42`. Forwarded headers là cơ chế truyền **thông tin về yêu cầu công khai gốc** qua các proxy, không phải giấy chứng nhận danh tính người dùng.

## 2. Header chuẩn Forwarded và các header X-Forwarded-*

### 2.1. Chuẩn RFC 7239: một header tổng hợp

```http
Forwarded: for=192.0.2.10;proto=https;host=app.example.test
```

Ý nghĩa các tham số thường gặp:

- `for`: client hoặc hop trước từ góc nhìn proxy ghi header. Có thể là địa chỉ IP, mã hóa theo cách quy định, hoặc identifier không phải IP; **không tự động xác thực client**.
- `proto`: scheme ban đầu như `https`.
- `host`: hostname (có thể kèm port) ban đầu.
- `by`: proxy interface/địa chỉ được proxy khai báo.

Nhiều proxy có thể tạo một chuỗi `Forwarded` với nhiều phần tử, ví dụ phân tách bằng dấu phẩy. Header chỉ có giá trị để quyết định “client gốc” nếu biết chính xác proxy nào đáng tin, cách mỗi hop thêm/sửa trường và liệu dữ liệu client đã bị xóa từ đầu hay chưa.

### 2.2. Các header X-Forwarded không chuẩn nhưng phổ biến

```http
X-Forwarded-For: 192.0.2.10
X-Forwarded-Proto: https
X-Forwarded-Host: app.example.test
X-Forwarded-Port: 443
X-Forwarded-Prefix: /portal
```

| Header | Mục đích | Nguy cơ khi tin mù |
| --- | --- | --- |
| `Forwarded` | Chuẩn RFC 7239, gồm `for/proto/host/by` | Giả mạo client, scheme, host |
| `X-Forwarded-For` | Một hay nhiều địa chỉ theo các hop | Sai IP audit/rate-limit/quyền nếu client tự thêm |
| `X-Forwarded-Proto` | Scheme public `http/https` | Liên kết HTTP/HTTPS sai, ảnh hưởng security assumptions |
| `X-Forwarded-Host` | Host công khai | Host-header poisoning, email/reset link/redirect tới domain giả |
| `X-Forwarded-Port` | Port công khai | URI sai host/port, sai redirect |
| `X-Forwarded-Prefix` | Tiền tố path công khai khi proxy rewrite route | Link sai path, có thể bị lợi dụng khi prefix do client kiểm soát |
| `X-Forwarded-Ssl` | Chỉ báo TLS trong một số hệ thống | Không phải nguồn xác thực TLS độc lập |

**Không coi `X-Forwarded-For` là identity hoặc authentication.** Nó không chứng minh người dùng là ai; người dùng có thể chia sẻ IP, đi qua NAT/VPN, hoặc cố tình tự gửi một chuỗi có vẻ giống địa chỉ được tin cậy.

### 2.3. Ví dụ nhiều hop

```text
Client (192.0.2.10)
    |
Edge proxy (10.0.0.2)
    |
Internal ingress (10.0.0.3)
    |
Backend (10.0.0.15)
```

Nếu edge đã loại bỏ mọi giá trị không đáng tin, nó có thể đặt `X-Forwarded-For: 192.0.2.10`. Ingress phía sau có thể append địa chỉ edge nếu cả hai thuộc trust chain rõ ràng. Nhưng nếu edge dùng `$proxy_add_x_forwarded_for` trên một header do client tự cung cấp mà không scrub, chuỗi đầu có thể là IP giả.

Không đơn giản dùng:

```java
// ANTI-PATTERN: client có thể tự gửi địa chỉ đứng đầu.
String claimedIp = request.getHeader("X-Forwarded-For")
        .split(",")[0]
        .trim();
```

Nếu phải dùng IP cho security/rate limit, cần **trusted proxy list/CIDR** và một thuật toán/hạ tầng chuẩn hóa chuỗi hops; thường nên dùng native proxy handling hoặc edge policy đã được kiểm soát và kiểm thử kỹ. Đừng tự suy luận trust từ việc header tồn tại.

## 3. Ranh giới tin cậy là vấn đề quan trọng nhất

Một `ForwardedHeaderFilter` **không thể biết** header nào do proxy đáng tin tạo và header nào do client độc hại tự gửi. Đây là lưu ý bảo mật được Spring Framework nêu rõ trong tài liệu.

```text
UNTRUSTED                       TRUSTED
Internet client                   Private deployment zone
    |                                      |
    | GET /api/inspect                    |
    | X-Forwarded-Host: attacker.test      |
    v                                      |
[ Edge proxy: TRUST BOUNDARY ]            |
    |                                      |
    | 1) DELETE client-supplied            |
    |    Forwarded and X-Forwarded-*       |
    | 2) SET authoritative proto/host/port |
    |    and optional prefix/for           |
    | 3) RESTRICT backend network access   |
    v                                      |
[ Spring Boot app: parse sanitized headers ]
    |
[ Generate links using trusted public URI ]
```

**Điều kiện tối thiểu để tin forwarded headers:**

1. Ứng dụng chỉ được truy cập từ proxy/ingress đáng tin qua firewall, network policy hoặc security groups; không công khai cổng backend cho Internet.
2. Proxy **xóa các header `Forwarded` và `X-Forwarded-*` đến từ phía không tin cậy** trước khi thêm giá trị mới, bao gồm cả họ header mà backend hiện **không** dùng.
3. Proxy **tự đặt** scheme, host, port, prefix theo cấu hình/route đã xác thực hoặc theo metadata đáng tin; không giữ lại host tùy tiện client cung cấp nếu host chưa qua allowlist.
4. Các proxy nội bộ phía sau chỉ chấp nhận forwarded chain từ các hop được biết, không tin mọi nguồn trong cùng VPC theo mặc định.
5. Ứng dụng cấu hình nhất quán một cách diễn giải forwarded headers; tránh một lớp tin `Forwarded` còn lớp khác lại ưu tiên `X-Forwarded-Host` do client gửi.
6. Có test cố tình gửi spoofed headers từ bên ngoài để chứng minh proxy loại bỏ chúng.

### 3.1. Vì sao cần loại bỏ cả hai họ header?

Giả sử proxy chỉ ghi đè `X-Forwarded-Proto` nhưng để nguyên:

```http
Forwarded: proto=https;host=attacker.test
X-Forwarded-Proto: https
X-Forwarded-Host: app.example.test
```

Nếu một framework, plugin hoặc filter khác ưu tiên header chuẩn `Forwarded`, request có thể bị xem như đến từ `attacker.test`. Vì vậy nên **scrub cả chuẩn RFC và toàn bộ các trường X-Forwarded liên quan**, rồi phát hành đúng một dạng đáng tin theo hợp đồng giữa proxy và backend.

**Trust boundary nằm ở proxy/mạng**, không nằm ở đoạn code như:

```java
if (request.getHeader("X-Forwarded-Proto") != null) {
    // Không thể tự xác thực đây là proxy.
}
```

### 3.2. Nếu backend có thể truy cập trực tiếp thì sao?

Nếu ứng dụng bật `server.forward-headers-strategy=framework` nhưng cổng `8080` vẫn mở cho client ngoài, kẻ gọi trực tiếp có thể tự gửi:

```http
X-Forwarded-Proto: https
X-Forwarded-Host: attacker.test
```

Filter có thể dùng chúng để dựng request wrapper “như thể request đã đến qua proxy”. Nó **không tự kiểm tra source IP hoặc chữ ký header**. Đây là **misconfiguration** có thể dẫn đến URL poisoning hay sai assumptions. Cách khắc phục đầu tiên là **chặn direct access**, không phải chỉ viết thêm regex ở controller.

## 4. ForwardedHeaderFilter hoạt động thế nào?

`org.springframework.web.filter.ForwardedHeaderFilter` là Servlet Filter của Spring. Khi có forwarded headers và chế độ xử lý được bật, nó tạo `HttpServletRequestWrapper` để cung cấp lại một số thuộc tính của request theo metadata được chuyển tiếp.

Ví dụ các method downstream có thể quan sát kết quả đã được diễn giải:
- `getScheme()`.
- `getServerName()`.
- `getServerPort()`.
- `isSecure()`.
- `getRequestURL()` và URL-building bằng infrastructure của Spring.
- Prefix/context path tùy cấu hình/phiên bản và header được hỗ trợ.

Filter cũng xử lý/ẩn các forwarded headers để tránh những thành phần phía sau lại đọc chúng theo cách khác. Tuy nhiên **việc ẩn khỏi downstream không biến các giá trị đã sử dụng trở thành đáng tin**. An ninh phụ thuộc việc proxy đầu chuỗi đã kiểm soát headers.

### 4.1. Filter ảnh hưởng URL generation ra sao?

Trong MVC:

```java
String publicLink = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path("/api/orders/42")
        .toUriString();
```

Đây là **snippet** trong một HTTP request đã đi qua pipeline, cần import `org.springframework.web.servlet.support.ServletUriComponentsBuilder`. Khi Spring dùng thông tin forwarded hợp lệ từ proxy, URI tạo ra có thể là:

```text
https://app.example.test/api/orders/42
```

Nếu không có forwarded metadata và request vào Tomcat trực tiếp:

```text
http://localhost:8080/api/orders/42
```

Các URI trên là ví dụ với **context path rỗng**; nếu có `server.servlet.context-path`, reverse proxy rewrite hay `X-Forwarded-Prefix`, cần kiểm tra kết quả thực theo topology. Không tự cộng hai lần tiền tố.

### 4.2. Response redirect

Filter có thể wrap cả `HttpServletResponse` để điều chỉnh `sendRedirect(...)` theo public scheme/host/port từ request đã chuẩn hóa. `setRelativeRedirects(true)` thay behavior để hỗ trợ relative redirects theo cơ chế `RelativeRedirectFilter`.

**Không cam kết mọi redirect luôn trả absolute URL:** kết quả phụ thuộc cách ứng dụng tạo `Location`, loại redirect, cấu hình filter, container và các response wrappers. Khi test, phải kiểm tra HTTP status và header `Location` thực tế; đặc biệt lưu ý redirects từ Spring Security/OAuth2 hoặc redirect từ container xảy ra trước khi filter chạy.

### 4.3. Thứ tự filter

Vì các filter hoặc handler sau có thể dùng `getScheme()/getServerName()` để tạo link, `ForwardedHeaderFilter` cần chạy **trước** những thành phần cần đọc metadata đã chuẩn hóa. Spring Framework nêu ví dụ nên đứng trước `RequestContextFilter`.

Nếu auto-config của Spring Boot đang tạo filter qua `server.forward-headers-strategy=framework`, **không tự khai báo thêm một ForwardedHeaderFilter thứ hai** trừ khi bạn hiểu rõ registration/filter order. Double processing giữa container/native và Framework có thể làm hành vi khó đoán.

## 5. Spring Boot: NONE, NATIVE, FRAMEWORK

Spring Boot cung cấp property:

```properties
server.forward-headers-strategy=none
```

Ba giá trị:

| Strategy | Ai diễn giải headers? | Khi nào cân nhắc |
| --- | --- | --- |
| `NONE` | Boot không bật forwarded header handling thay ứng dụng | Không có trusted proxy hoặc không muốn dùng headers |
| `NATIVE` | Servlet container xử lý, ví dụ Tomcat RemoteIpValve | Khi container hỗ trợ các headers cần dùng và có cách kiểm soát trusted proxies phù hợp |
| `FRAMEWORK` | Spring Web sử dụng ForwardedHeaderFilter trên Servlet stack | Khi cần tích hợp của Spring như URL/prefix/redirect handling và proxy đã scrub headers |

Theo tài liệu Spring Boot 3.5, trên một số cloud platform được hỗ trợ, mặc định có thể là `NATIVE`; ở môi trường khác mặc định có thể là `NONE`. **Không suy đoán strategy đang chạy**: kiểm tra cấu hình effective, deployment profiles, container và version cụ thể.

### 5.1. Chọn FRAMEWORK

```properties
# Chỉ an toàn sau trusted proxy đã sanitize forwarded headers.
server.forward-headers-strategy=framework
```

Spring Boot dùng Spring Framework forwarded header support trong Servlet app. Dùng khi proxy đã đáp ứng đầy đủ trust boundary và backend không thể bị truy cập trực tiếp từ Internet.

Ưu điểm là builder/Servlet wrapper nhìn thấy scheme/host/port public cho nhiều loại MVC response. Nhược điểm bảo mật là nếu ingress để lọt client-supplied forwarded headers, **ứng dụng có thể tin giá trị giả**.

### 5.2. Chọn NATIVE

```properties
server.forward-headers-strategy=native
```

Với embedded Tomcat, hạ tầng như `RemoteIpValve` có thể hiểu những header phổ biến như `X-Forwarded-For` và `X-Forwarded-Proto` theo cấu hình. Container có các thiết lập tên header và trusted/internal proxies tùy version. Ví dụ cấu hình tên custom:

```properties
server.tomcat.remoteip.remote-ip-header=x-forwarded-for
server.tomcat.remoteip.protocol-header=x-forwarded-proto
```

**Lưu ý:** các property chỉ định tên header, **không phải tự tạo allowlist của proxy đáng tin**. Cần kiểm tra `internal-proxies`/`trusted-proxies` tương ứng và network isolation theo tài liệu container. Không nghĩ `NATIVE` luôn hỗ trợ `X-Forwarded-Prefix` giống FRAMEWORK: khả năng diễn giải prefix/path tùy container và cách ingress rewrite.

### 5.3. Chọn NONE

```properties
server.forward-headers-strategy=none
```

Dùng khi không có trusted proxy hoặc khi ứng dụng cố tình **không dùng** forwarded metadata. Cấu hình NONE không nhất thiết “xóa vật lý các header đầu vào”: code tự gọi `request.getHeader("X-Forwarded-Host")` vẫn có thể thấy header do client gửi nếu chưa có bộ lọc loại bỏ. Nếu cần strip tất cả headers khỏi Servlet request downstream, dùng `ForwardedHeaderFilter` ở chế độ `removeOnly` như mục 6.

### 5.4. Một số vấn đề liên quan Tomcat và TLS termination

Spring Boot có khuyến nghị khi dùng Tomcat với proxy kết thúc TLS:

```properties
server.tomcat.redirect-context-root=false
```

Property này giúp tránh redirect tới context root diễn ra trước khi metadata forwarded proto được áp dụng trong những topology thích hợp. Không nên dùng nó như “cách sửa mọi redirect”. Kiểm tra redirect thực và thứ tự xử lý container/filters.

## 6. Chế độ removeOnly: loại bỏ nhưng KHÔNG tin forwarded headers

Nếu application không cần diễn giải forwarded headers hoặc muốn bảo đảm code downstream không tùy tiện đọc chúng, có thể dùng:

```java
ForwardedHeaderFilter filter = new ForwardedHeaderFilter();
filter.setRemoveOnly(true);
```

Ý nghĩa: filter **che/loại bỏ** các `Forwarded` / `X-Forwarded-*` headers khỏi view downstream, nhưng **không dùng chúng** để ghi đè host/scheme/port. Theo Javadoc Spring đây là một chế độ được hỗ trợ chính thức.

Ví dụ Java config đầy đủ của bean registration (dùng ở **một phương án riêng**, không đồng thời với Boot FRAMEWORK):

```java
package com.example.demo;

import java.util.EnumSet;
import jakarta.servlet.DispatcherType;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
class ForwardedHeaderRemoveOnlyConfig {

    @Bean
    FilterRegistrationBean<ForwardedHeaderFilter>
    stripForwardedHeaders() {

        ForwardedHeaderFilter filter = new ForwardedHeaderFilter();
        filter.setRemoveOnly(true);

        FilterRegistrationBean<ForwardedHeaderFilter> bean =
                new FilterRegistrationBean<>(filter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        bean.setDispatcherTypes(EnumSet.of(
                DispatcherType.REQUEST,
                DispatcherType.ASYNC,
                DispatcherType.ERROR));
        bean.setAsyncSupported(true);
        return bean;
    }
}
```

Khi dùng config này đặt:

```properties
server.forward-headers-strategy=none
```

**Không** dùng cả `server.forward-headers-strategy=framework` và `stripForwardedHeaders()` vì lúc ấy hai thành phần có trách nhiệm xung đột. Nếu `NATIVE` ở container đã dùng header trước khi Servlet Filter chạy, `removeOnly` có thể quá muộn để đảo ngược metadata của container; cần loại bỏ ngay ở proxy hoặc tắt/đổi native handling.

Cũng không nên quảng cáo `removeOnly` như lớp bảo vệ duy nhất trước HTTP request smuggling/host validation. Nó chỉ xử lý một nhóm headers ở tầng Servlet request, không chặn mọi cách sửa Host/TLS metadata.

## 7. Ví dụ runnable Spring Boot 3.x: quan sát public URL

Ví dụ này cố tình dùng **Spring Web** không cần database/security để dễ quan sát headers. Ở chế độ lab, nó cho phép gửi forwarded headers giả **chỉ để chứng minh ảnh hưởng** của FRAMEWORK. **Không cấu hình như vậy trên backend công khai ở production.**

### 7.1. Tạo project

Tại [Spring Initializr](https://start.spring.io/), tạo Maven project với Java 17+, Spring Boot 3.x, dependency **Spring Web**. Dependency POM:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

Đây là fragment; Initializr cung cấp đầy đủ POM/versions/plugins.

### 7.2. Demo Java hoàn chỉnh

Tạo `src/main/java/com/example/demo/DemoApplication.java`:

```java
package com.example.demo;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

record RequestInfo(
        String scheme,
        String host,
        int port,
        boolean secure,
        String contextPath,
        String requestUrl,
        String generatedLink) {
}

@RestController
class InspectController {

    @GetMapping("/api/inspect")
    public RequestInfo inspect(HttpServletRequest request) {

        String generatedLink = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/resource/42")
                .toUriString();

        return new RequestInfo(
                request.getScheme(),
                request.getServerName(),
                request.getServerPort(),
                request.isSecure(),
                request.getContextPath(),
                request.getRequestURL().toString(),
                generatedLink);
    }
}
```

Mọi field của record là thông tin mà controller **thấy sau khi xử lý Servlet filter chain**. Endpoint `/api/resource/42` ở đây chỉ là **link mẫu được tạo**, không phải resource thật đã đăng ký. Ứng dụng không sử dụng forwarded host để thực hiện network call.

### 7.3. application.properties cho lab

Tạo `src/main/resources/application.properties`:

```properties
server.forward-headers-strategy=framework
# Chỉ bind loopback khi kiểm thử trên máy cá nhân:
server.address=127.0.0.1
server.port=8080
```

Trong môi trường này, chỉ process trên máy cá nhân có thể gọi cổng loopback. Production thường yêu cầu network/private-interface policy khác; không sao chép nguyên settings lab.

Chạy bằng:

```bash
# Linux/macOS
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

### 7.4. Request không có forwarded headers

```bash
curl -sS 'http://127.0.0.1:8080/api/inspect'
```

Các trường quan trọng trong JSON thường tương đương:

```json
{
  "scheme": "http",
  "host": "127.0.0.1",
  "port": 8080,
  "secure": false,
  "contextPath": "",
  "requestUrl": "http://127.0.0.1:8080/api/inspect",
  "generatedLink": "http://127.0.0.1:8080/api/resource/42"
}
```

Chúng phụ thuộc Host thực của request và cấu hình server; đây là đầu ra dự kiến theo lệnh curl dùng IP loopback, không đại diện proxy production.

### 7.5. Mô phỏng một proxy có forwarded headers

```bash
curl -sS 'http://127.0.0.1:8080/api/inspect' \
  -H 'X-Forwarded-Proto: https' \
  -H 'X-Forwarded-Host: app.example.test' \
  -H 'X-Forwarded-Port: 443'
```

Với Spring Framework forwarded header handling được bật, các trường quan trọng có thể trở thành:

```json
{
  "scheme": "https",
  "host": "app.example.test",
  "port": 443,
  "secure": true,
  "contextPath": "",
  "requestUrl": "https://app.example.test/api/inspect",
  "generatedLink": "https://app.example.test/api/resource/42"
}
```

**Đây đồng thời là minh họa tấn công** nếu người ngoài được phép gọi backend trực tiếp. Client tự đặt `X-Forwarded-Host` mà server chấp nhận thì generated links bị điều khiển bởi client. Cơ chế này **chỉ an toàn** khi headers trong request thật tới backend là do proxy đáng tin tạo ra sau khi xóa input không tin cậy.

### 7.6. Mô phỏng giả mạo host

```bash
curl -sS 'http://127.0.0.1:8080/api/inspect' \
  -H 'X-Forwarded-Proto: https' \
  -H 'X-Forwarded-Host: attacker.example.test' \
  -H 'X-Forwarded-Port: 443'
```

Trong lab đang bật FRAMEWORK, `host` và `generatedLink` có thể chứa `attacker.example.test`. Đây là behavior cần được **ngăn từ ranh giới proxy trong production**, không phải cách triển khai hợp lệ để lấy public host.

**Sau khi học xong, đổi strategy sang NONE nếu chạy service có thể bị truy cập từ mạng không tin cậy và chưa có trusted reverse proxy bảo vệ.**

### 7.7. Thử chế độ NONE để đối chiếu

Đổi config:

```properties
server.forward-headers-strategy=none
server.address=127.0.0.1
server.port=8080
```

Khởi động lại, gọi lệnh có `X-Forwarded-Host: attacker.example.test` như trên. Vì Boot không dùng forwarded strategy, các Servlet fields/link ở demo thông thường tiếp tục phản ánh URL kết nối trực tiếp `127.0.0.1:8080`. Nhưng nếu bạn tự viết code lấy `request.getHeader("X-Forwarded-Host")`, chuỗi header thô vẫn có thể hiện diện: NONE **không phải** một request header firewall.

## 8. Cấu hình reverse proxy minh họa: scrub rồi set

Ví dụ dưới đây là **fragment Nginx cho một topology cố định**, không phải cấu hình TLS production hoàn chỉnh. Nó giả định `app.example.test` đã được kiểm soát, proxy là HTTPS entry point, app chỉ nghe trên loopback `127.0.0.1:8080`, và không có routing prefix.

```nginx
# Đặt bên trong server/location HTTPS đã cấu hình TLS.
location / {
    proxy_pass http://127.0.0.1:8080;

    # Host backend nhất quán với public host hợp lệ.
    proxy_set_header Host app.example.test;

    # Xóa Forwarded chuẩn do client tự gửi.
    proxy_set_header Forwarded "";

    # Ghi đè bằng thông tin authoritative của edge proxy.
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header X-Forwarded-Host app.example.test;
    proxy_set_header X-Forwarded-Port 443;
    proxy_set_header X-Forwarded-For $remote_addr;

    # Không cho client tự gán các biến thể khác.
    proxy_set_header X-Forwarded-Ssl "";
    proxy_set_header X-Forwarded-Prefix "";
}
```

**Điều kiện đúng:** snippet phải được áp tại edge trust boundary, không bị một config Nginx inherited hoặc proxy phía trước/phía sau ghi đè trái chính sách. Cần xác nhận Nginx thực sự không chuyển các forwarded headers không liệt kê theo cách gây xung đột. Trong hệ thống phức tạp, scrub đầy đủ những tên header ứng dụng và toàn bộ middleware hỗ trợ, kể cả `Forwarded`, `X-Forwarded-For`, `X-Forwarded-Host`, `X-Forwarded-Proto`, `X-Forwarded-Port`, `X-Forwarded-Prefix` và các biến thể mà stack thực tế đọc.

Dòng `X-Forwarded-For $remote_addr` dùng IP của **kết nối tới Nginx**. Nếu Nginx thật sự là edge, đó là IP bên ngoài mà edge nhìn thấy. Nếu có CDN/proxy đáng tin ở trước Nginx, `$remote_addr` thường là IP của CDN, không phải client; muốn client thật cần thiết kế trusted-real-IP policy tại edge và đảm bảo CDN scrub headers trước.

### 8.1. Vì sao không mặc định dùng $proxy_add_x_forwarded_for?

Nginx có biến `$proxy_add_x_forwarded_for` để append IP hiện tại vào header `X-Forwarded-For` đã có. Nếu phần đã có do client tự gửi và chưa scrub, kết quả giữ cả IP giả do client đưa vào:

```text
Client: X-Forwarded-For: 1.2.3.4 (giả)
Proxy append: 1.2.3.4, 192.0.2.10
```

Một ứng dụng lấy phần tử đầu sẽ nhận IP giả. Việc append chỉ nên áp dụng khi **chuỗi trước đã được xây từ các hop tin cậy**, và hệ thống đọc chuỗi theo chính sách trusted proxies rõ ràng.

### 8.2. Có nhiều hostname public thì sao?

Không hardcode một host nếu sản phẩm hợp lệ dùng nhiều tenant domains. Nhưng **không được** chấp nhận mọi giá trị Host rồi copy nguyên vào `X-Forwarded-Host`. Proxy nên:
- Match `server_name`/routing rules dựa trên allowlist hostname/tenant domain hợp lệ.
- Từ chối default/unknown Host hoặc chuyển tới canonical host.
- Chỉ set `X-Forwarded-Host` bằng host đã qua validation/routing.
- Nếu app phải build password-reset/email links, ưu tiên public base URL nằm trong cấu hình/tài khoản tenant đã xác thực thay vì phụ thuộc raw request header.

### 8.3. X-Forwarded-Prefix khi gateway rewrite path

Ví dụ URL public là:

```text
https://app.example.test/portal/api/inspect
```

Proxy bỏ `/portal` và gửi backend:

```text
http://127.0.0.1:8080/api/inspect
```

Proxy có thể đặt `X-Forwarded-Prefix: /portal` để Spring dựng link công khai đúng context khi cơ chế/framework/version hỗ trợ. **Không** thêm `/portal` vào cả route backend lẫn code builder nếu framework đã áp dụng prefix; nếu làm vậy có thể xuất hiện `/portal/portal/...`.

Khả năng xử lý prefix khác nhau giữa `FRAMEWORK` và native container. Một số phiên bản Spring Framework cung cấp cấu hình riêng để bật việc sử dụng `X-Forwarded-Prefix`; cần kiểm tra Javadoc phiên bản thực của `ForwardedHeaderFilter`. Với API Spring hiện đại có constructor chọn family header và tùy chọn prefix, **không tự sao chép method từ Javadoc 7.x vào bản 6.x cũ**.

## 9. Nguy cơ an toàn web nếu tin header giả

### 9.1. Host header injection / password reset poisoning

Nếu code tạo email reset password như sau:

```java
String link = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path("/reset")
        .queryParam("token", token)
        .toUriString();
```

Đây là snippet phụ thuộc `token`. Khi forwarded host có thể do attacker kiểm soát, URL trong email có thể trở thành:

```text
https://attacker.example.test/reset?token=...
```

Người dùng nhận email tưởng hợp lệ nhưng truy cập domain khác và có thể làm lộ token. **Cách phòng mạnh hơn:** tạo URL nhạy cảm từ **canonical public base URL đã cấu hình và được allowlist**; proxy cũng phải scrub forwarded headers và validate Host.

### 9.2. Open redirect và Location poisoning

Chuyển host của request thành host public không tự sinh open redirect, nhưng nhiều redirect helper có thể dùng host/scheme từ request. Nếu host này không tin cậy, attacker có thể ảnh hưởng `Location`. Với redirect đến URL tùy chọn như `next=...` vẫn cần kiểm tra relative path hoặc allowlist đích độc lập; ForwardedHeaderFilter **không** bảo vệ redirect parameter.

### 9.3. CORS: forwarded headers không quyết định Origin hợp lệ

`Origin: https://frontend.example.test` là request header được browser dùng trong cơ chế CORS. `X-Forwarded-Host: app.example.test` mô tả URL public theo proxy; hai khái niệm không đồng nghĩa.

Không viết CORS policy kiểu:

```java
// ANTI-PATTERN: lấy host/proxy metadata không tin cậy làm allowlist.
String origin = request.getHeader("Origin");
response.setHeader("Access-Control-Allow-Origin", origin);
response.setHeader("Access-Control-Allow-Credentials", "true");
```

Phản chiếu tùy ý Origin (đặc biệt cùng credentials) là nguy hiểm. Dùng danh sách origin đáng tin và credential policy riêng; forwarded headers không thay kiểm tra CORS. CORS cũng **không phải authentication hoặc CSRF protection**.

### 9.4. Cookie Secure và HTTPS

Nếu TLS kết thúc ở proxy, backend có thể thấy `http`/`isSecure=false` nếu không có handling tương ứng. Điều này có thể ảnh hưởng code tùy chỉnh hoặc framework dựa trên secure-request signal khi đặt cookies/redirects. Tuy nhiên không lấy `X-Forwarded-Proto=https` từ client làm “chứng minh đã có TLS”; proxy phải đảm bảo kết nối public thật được mã hóa.

Đối với session cookies: cấu hình `Secure`, `HttpOnly`, `SameSite`, TLS và security requirements theo mô hình của ứng dụng. Đừng dựa duy nhất vào `isSecure()` nếu architecture/cấu hình proxy không được kiểm soát.

### 9.5. OAuth2/OIDC callback và redirect_uri

OAuth2 client applications có thể tạo `redirect_uri` từ request và registered client settings. Forwarded host/scheme sai có thể khiến redirect URI không khớp hoặc bị tạo sai domain. Dùng registered redirect URI exact match, canonical public URLs, trusted proxy configuration và integration tests trên topology thực. Không để forwarded host tùy ý quyết định nơi gửi authorization code/token.

### 9.6. IP-based rate limit/audit

Nếu code dùng `X-Forwarded-For` phần tử đầu cho “IP thật”, kẻ gọi có thể spoof IP để né quota hoặc vu khống địa chỉ khác. Chỉ tin địa chỉ từ trusted chain do edge xác minh. Với security-critical identity, vẫn dùng authenticated principal/session/token và risk policy; IP là tín hiệu bổ sung, không phải chứng thư danh tính.

## 10. Checklist triển khai cùng Spring Security

| Trường hợp | Thực hành |
| --- | --- |
| HTTP ingress | Xác thực TLS/certificate, giới hạn public hosts |
| Backend port | Chỉ cho proxy/ingress truy cập; không expose NodePort/backend Internet |
| Forwarded headers | Scrub và tái tạo cả RFC `Forwarded` lẫn `X-Forwarded-*` ở edge |
| App strategy | Chọn `NONE/NATIVE/FRAMEWORK` có chủ đích, tránh xử lý kép |
| Client IP | Trusted proxy CIDRs/chain; không parse first XFF raw |
| CORS | Allowlist origins riêng; không tin Origin hay forwarded host tùy ý |
| CSRF | Bảo vệ state-changing requests khi dùng cookie/session authentication |
| Session cookie | Secure/HttpOnly/SameSite và security configuration phù hợp |
| Authentication | Spring Security identity; không dùng forwarded headers làm user ID |
| URL generation | Canonical public base URL cho link nhạy cảm |
| Logs/metrics | Không log raw Authorization/cookies/reset tokens; phân biệt remote proxy và verified client IP |
| Host validation | Allowlist public domains ở ingress/app; từ chối unknown Host |
| Redirect/callback | Test Location, OAuth redirect_uri, absolute links sau TLS termination |
| Security tests | Thử spoof header từ Internet/direct backend/bên trong network |

**Filter order quan trọng nhưng không đủ:** nếu một middleware khác đã đọc forwarded headers **trước** `ForwardedHeaderFilter` hoặc container đã biến request metadata ở tầng native, một filter chạy sau không thể sửa lại mọi quyết định trước đó. Vì vậy phải đặt trust boundary ngay ở mạng/proxy đầu vào.

## 11. Các test cần có trước khi cho traffic thật

### 11.1. Test qua proxy thật, không chỉ curl trực tiếp backend

| Test | Request từ phía public | Kỳ vọng |
| --- | --- | --- |
| A | HTTPS + Host công khai hợp lệ | URI/link trả về sử dụng `https://app.example.test` |
| B | Client thêm `X-Forwarded-Host: attacker.test` | Edge xóa header giả; link vẫn theo public host hợp lệ |
| C | Client thêm `Forwarded: host=attacker.test;proto=http` | Edge xóa header RFC giả; không có host/scheme poisoning |
| D | Client thêm `X-Forwarded-For: 1.2.3.4` | Hệ thống xác định IP theo trusted chain, không tin phần tử giả |
| E | Client đổi `Host` sang hostname không được cấp | Edge trả reject/redirect canonical đúng policy |
| F | Client thử truy cập trực tiếp backend:8080 | Bị chặn bởi network policy/firewall |
| G | Request qua route có `X-Forwarded-Prefix` | Link không thiếu/trùng prefix |
| H | Redirect 302/303 và OAuth callbacks | `Location`/redirect_uri khớp canonical host, scheme, path |
| I | App đổi strategy NONE/NATIVE/FRAMEWORK | Behavior khớp cấu hình và không có double normalization |

**Quan trọng:** test B/C/D phải gửi header spoof **từ ngoài trust boundary**, không chỉ gửi trực tiếp đến localhost app đang chạy trong lab. Nếu chỉ test backend, bạn đang chứng minh tính năng của filter, **không** chứng minh proxy đã scrub.

### 11.2. MockMvc test cho filter (unit/integration-in-app)

Thêm dependency `spring-boot-starter-test` rồi tạo test:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "server.forward-headers-strategy=framework")
@AutoConfigureMockMvc
class ForwardedMetadataTest {

    @Autowired
    MockMvc mvc;

    @Test
    void canReadForwardedMetadataInControlledTest() throws Exception {
        mvc.perform(get("/api/inspect")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "app.example.test")
                        .header("X-Forwarded-Port", "443"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheme").value("https"))
                .andExpect(jsonPath("$.host").value("app.example.test"))
                .andExpect(jsonPath("$.secure").value(true));
    }
}
```

Đây là test **trên app**, không phải test reverse proxy; nó xác minh request wrapper/framework behavior khi header đã có. Tùy Boot version, filter registration trong MockMvc test context có thể phụ thuộc cách auto-configure filters. Nếu test không thấy behavior, hãy xác minh filter được đăng ký thay vì chuyển ngay sang parse raw headers.

### 11.3. Test host poisoning từ phía ingress

Một security regression test có giá trị là gửi yêu cầu vào **public URL thật** với cả hai loại forwarded headers giả:

```bash
curl -i 'https://app.example.test/api/inspect' \
  -H 'Forwarded: proto=http;host=attacker.example.test' \
  -H 'X-Forwarded-Host: attacker.example.test' \
  -H 'X-Forwarded-Proto: http'
```

Kỳ vọng hệ thống **không** tạo absolute link tới `attacker.example.test`, không tự nhận request public là HTTP chỉ vì header giả, và không bypass quyền. Status/body chính xác có thể phụ thuộc chính sách chặn header lạ của proxy; điều quan trọng là **không tin giá trị giả**.

**Không áp lệnh trên để kết luận ở một hệ thống bạn không sở hữu/không được phép test.** Trong môi trường lab hoặc staging được cấp quyền, đây là test cấu hình thông thường.

## 12. Troubleshooting: sai HTTPS/Host/prefix/redirect

| Triệu chứng | Nguyên nhân khả dĩ | Điều cần kiểm tra |
| --- | --- | --- |
| Link từ ứng dụng là `http://...:8080` | App chưa dùng metadata proxy | Strategy; proxy set `X-Forwarded-Proto/Host/Port` |
| `https` đúng nhưng host vẫn internal | Proxy chưa set hoặc app ưu tiên header khác | Cả `Forwarded` và `X-Forwarded-Host` |
| Link có `/portal/portal/...` | Prefix được thêm hai lần | Proxy path rewrite, context path và prefix |
| Redirect 302 quay lại HTTP | TLS termination/redirect trước forwarded handling | Tomcat redirect context root, filter order và `Location` |
| OAuth redirect_uri mismatch | App sinh URI khác registered value | Canonical public URL/forwarded policy |
| `isSecure()` false sau HTTPS proxy | Strategy none/native chưa đúng hoặc proto header thiếu | Container config, trusted proxy, X-Forwarded-Proto |
| Filter không chạy | Framework strategy chưa bật, servlet filter order/dispatcher types | Filter registration, REQUEST/ASYNC/ERROR |
| Filter chạy hai lần | Vừa Boot FRAMEWORK vừa tự đăng ký filter | Chọn một cơ chế |
| IP audit bị giả | App lấy phần tử đầu của XFF do client gửi | Scrub tại edge + trusted chain algorithm |
| Internal health URL đổi host bất ngờ | Gửi headers forwarded vào internal request | Dùng NONE/removeOnly cho traffic không qua trusted proxy |
| Direct backend request giả mạo scheme/host | Port backend exposed + FRAMEWORK | Network isolation và edge scrub |
| CORS origin lạ được phản chiếu | Policy CORS viết từ request input | Allowed origins tường minh |
| Password reset email trỏ domain khác | Host poisoning | Base URL cấu hình, host allowlist, proxy header scrub |
| `X-Forwarded-Prefix` không có tác dụng | Native không hỗ trợ hoặc framework/version config | Kiểm tra chiến lược và API version |
| Chặn header bằng removeOnly nhưng app vẫn “https” | Container đã xử lý NATIVE trước filter | Tắt native, scrub tại edge và test request metadata |
| Không xem được forwarded headers ở controller | ForwardedHeaderFilter cố ý ẩn chúng | Dùng normalized request fields; debug tại proxy an toàn |

### 12.1. Cách debug có hệ thống

1. Xác định topology thật: client → CDN? → WAF? → ingress? → backend. Ai là **edge of trust**?
2. Chụp cấu hình hiệu lực ở edge: đã xóa và set **cả hai họ** forwarded headers chưa?
3. Kiểm tra backend có nhận trực tiếp request từ Internet/VPC untrusted không.
4. Kiểm tra `server.forward-headers-strategy` effective trên đúng environment/profile và Servlet container.
5. Tại app, so sánh `request.getScheme()`, `getServerName()`, `getServerPort()` và link builder output với public URL mong muốn.
6. Nếu native/container, kiểm tra trusted/internal proxies và header names.
7. Nếu Framework, kiểm tra filter registration, order và prefix support.
8. Test từ phía public **có header spoof**, không chỉ request không có header.
9. Test redirect, secure cookies, OAuth callback, CORS và link nhạy cảm.
10. Giới hạn debug logs: không đưa token, cookie, thông tin khách hàng vào output chẩn đoán.

## 13. Kiến thức dễ nhầm

**Câu 1.** Có `ForwardedHeaderFilter` thì ứng dụng tự phân biệt header của proxy với header của client được không?

**Đáp án:** Không. Filter không biết nguồn gốc đáng tin; edge proxy phải scrub rồi set headers, backend phải bị hạn chế direct access.

**Câu 2.** `X-Forwarded-For` có chứng minh địa chỉ IP thực của người dùng không?

**Đáp án:** Không. Nó chỉ là dữ liệu HTTP; phải biết trusted proxy chain và loại bỏ header giả từ client trước khi sử dụng.

**Câu 3.** `server.forward-headers-strategy=framework` khác `native` như thế nào?

**Đáp án:** FRAMEWORK dùng Spring ForwardedHeaderFilter, NATIVE dùng container hỗ trợ forwarded headers. Khả năng đọc prefix và thuật toán trusted proxies có thể khác; không bật cả hai tùy tiện.

**Câu 4.** Khi nào dùng `removeOnly=true`?

**Đáp án:** Khi cần xóa forwarded headers khỏi downstream mà **không sử dụng thông tin trong chúng**. Nó không khôi phục public URL và không thay edge/proxy trust boundary.

**Câu 5.** Server đằng sau proxy nhìn thấy HTTP, user dùng HTTPS. Có thể tin client gửi `X-Forwarded-Proto=https` để xác định HTTPS không?

**Đáp án:** Chỉ sau khi giá trị ấy do proxy đáng tin tạo ra sau khi xóa client input. Nếu backend mở trực tiếp cho client, câu trả lời là không.

**Câu 6.** Forwarded headers có thay CORS allowlist, CSRF protection hoặc authentication không?

**Đáp án:** Không. Chúng mô tả metadata của request qua proxy; Origin policy, CSRF token, identity và authorization là các cơ chế độc lập.

**Câu 7.** Nếu proxy chỉ xóa `X-Forwarded-Host` nhưng không xóa `Forwarded` thì đã đủ an toàn chưa?

**Đáp án:** Chưa. Một thành phần có thể ưu tiên header RFC `Forwarded` vẫn chứa host giả.

**Câu 8.** Nếu public URL có `/portal` nhưng backend không có prefix, phải làm gì?

**Đáp án:** Cấu hình path rewrite/prefix ở gateway và Spring strategy/version phù hợp; kiểm tra URL builder không thiếu hoặc thêm lặp `/portal`.

## 14. Tài liệu chính thức

- [Spring Framework — Servlet Filters và Forwarded Headers](https://docs.spring.io/spring-framework/reference/web/webmvc/filters.html): RFC 7239, X-Forwarded headers, ForwardedHeaderFilter, trust boundary và dispatcher types.
- [Spring Framework 6.2 — ForwardedHeaderFilter Javadoc](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/web/filter/ForwardedHeaderFilter.html): wrapper semantics, `setRemoveOnly`, `setRelativeRedirects`.
- [Spring Boot 3.5 — Running Behind a Front-end Proxy Server](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html#howto.webserver.use-behind-a-proxy-server): chiến lược NATIVE/FRAMEWORK, redirect/Tomcat.
- [Spring Boot 3.5 — ForwardHeadersStrategy Javadoc](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/autoconfigure/web/ServerProperties.ForwardHeadersStrategy.html): `NONE`, `NATIVE`, `FRAMEWORK`.
- [RFC 7239 — Forwarded HTTP Extension](https://www.rfc-editor.org/rfc/rfc7239): cú pháp và chuỗi proxy hops.
- [Spring Framework — ServletUriComponentsBuilder Javadoc](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/web/servlet/support/ServletUriComponentsBuilder.html): tạo URI dựa vào Servlet request.
- [Spring Security — Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html): filter chain, security context và trust architecture.
- [OWASP — Host Header Injection](https://owasp.org/www-project-web-security-testing-guide/latest/4-Web_Application_Security_Testing/07-Input_Validation_Testing/17-Testing_for_Host_Header_Injection): rủi ro và kiểm thử Host header poisoning.
- [Nginx — proxy_set_header](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_set_header): chính sách header chuyển tiếp tới upstream.

**Ghi nhớ cuối cùng:** ForwardedHeaderFilter giải quyết **độ chính xác của scheme/host/port/prefix khi ứng dụng đứng sau proxy**; nó **không chứng thực** giá trị forwarded headers. Một kiến trúc an toàn phải có proxy edge **loại bỏ header không tin cậy**, set metadata theo route/TLS thực sự, giới hạn đường truy cập backend, dùng đúng strategy `NONE/NATIVE/FRAMEWORK` và kiểm thử host/proto/IP spoofing. Với đường dẫn tuyệt đối nhạy cảm như reset-password hoặc OAuth callbacks, cân nhắc **canonical configured public URL** thay vì tin request metadata.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cors-web-security-008">Kiểm soát chuyển hướng mở, nội dung tải lên và thông tin nhạy cảm trong log</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm soát chuyển hướng mở, nội dung tải lên và thông tin nhạy cảm trong log (Spring MVC)

## 1. Mục tiêu: ba đầu vào không đáng tin, ba lớp bảo vệ bổ sung

Một ứng dụng Spring MVC thường nhận **URL đích chuyển hướng**, **tệp tải lên** và **nội dung yêu cầu** được ghi vào nhật ký. Cả ba đều có thể chứa dữ liệu do người dùng bên ngoài kiểm soát. Nếu sử dụng thiếu kiểm soát, chúng dẫn đến những vấn đề khác nhau:

- **Open redirect**: endpoint đưa người dùng đến domain do kẻ tấn công chọn, lợi dụng độ tin cậy của domain chính để lừa đảo, chuyển hướng OAuth sai đích hoặc làm lộ thông tin.
- **Unrestricted file upload / unsafe file handling**: tệp không tin cậy được lưu dưới tên hoặc thư mục nguy hiểm, phục vụ như mã thực thi/HTML, gây path traversal, chứa mã độc, tiêu hao CPU/bộ nhớ/ổ đĩa hoặc vượt quyền truy cập.
- **Sensitive-data logging và log injection**: token, session ID, password, thông tin cá nhân, dữ liệu form/multipart lọt vào log; nội dung chứa xuống dòng/format giả làm sai lệch hoặc giả mạo các bản ghi.

Ba vấn đề này có bề mặt tấn công và biện pháp riêng. Không thể sửa cả ba bằng một `@Valid`, một cấu hình CORS hoặc chỉ bật Spring Security. Các nguyên tắc chung:

1. Xác định **ranh giới tin cậy** trước khi dùng input: URL từ query/form, file metadata, request headers và raw body đều không đáng tin chỉ vì controller nhận được chúng.
2. **Allowlist theo nghiệp vụ** thay vì chỉ chặn vài chuỗi nguy hiểm.
3. Chuẩn hóa cách xử lý và hạn chế quyền/tác động: chuyển hướng đến đích được kiểm duyệt; tệp có tên server sinh, định dạng được kiểm tra, lưu ở vị trí riêng; log chỉ các trường cần vận hành.
4. Áp dụng giới hạn **độ lớn/thời gian/số lần**, authentication/authorization, CSRF, HTTP security headers và lớp bảo vệ hạ tầng khi phù hợp.
5. Kiểm thử cả trường hợp hợp lệ và các đầu vào đối kháng, xác nhận hành vi ở **HTTP response**, file system và observability pipeline.

Bài nằm trong module **Spring Framework - web**, chủ đề cha **CORS và an toàn web**. Baseline code mẫu dùng **Java 17+, Spring Boot 3.5.x / Spring Framework 6.2.x, Spring MVC Servlet**. Ví dụ không tự bật Spring Security hoặc cung cấp kho lưu production; phần production chỉ rõ những lớp cần bổ sung.

## 2. Mô hình luồng dữ liệu và điểm quyết định tin cậy

```text
Client / Browser
     |
     +--- query/form "destination" ----> MVC controller
     |                                    |
     |                           ALLOWLIST mã đích
     |                                    |
     |                       Response 303 Location: /portal/...
     |                       không phản chiếu raw URL
     |
     +--- multipart "file" -----------> multipart parser
     |                                    |
     |                        giới hạn bytes, loại/decoder,
     |                        dimensions, normalize ảnh
     |                                    |
     |                          server-generated ID + name
     |                                    |
     |                      private storage ngoài webroot
     |                                    |
     |                         download handler có auth
     |
     +--- request headers/body -------> servlet filters / MVC / app
                                          |
                                chỉ chọn safe event fields
                                          |
                           structured log + newline escaping
                                          |
                            log sink có quyền/retention
```

Những điểm cần phân biệt:

- **Redirect** dùng HTTP status 3xx và header `Location`. Nó **không** chứng minh rằng đích được tin cậy hay người dùng đã xác thực.
- **CORS** kiểm soát JavaScript trong browser được đọc response cross-origin theo chính sách, **không ngăn browser theo HTTP redirect** hoặc ngăn kẻ tấn công đăng URL phishing cho người dùng.
- **Content Security Policy (CSP)**, `X-Content-Type-Options: nosniff`, `Content-Disposition` và cookie flags là lớp bảo vệ hữu ích, **không thay thế** việc kiểm tra file hoặc redirect.
- **MIME Content-Type** do client công bố chỉ là metadata input. Nó có thể sai hoặc cố ý giả mạo.
- **Log framework parameterized logging** giúp định dạng tham số và tránh phép nối chuỗi, **không tự loại bỏ PII, token hoặc ký tự CR/LF**.

## 3. Open redirect là gì? Vì sao nguy hiểm?

Giả sử ứng dụng có endpoint:

```http
GET /login/continue?next=https://outside.example/collect HTTP/1.1
Host: trusted.example
```

Nếu server sao chép nguyên `next` vào `Location`:

```http
HTTP/1.1 302 Found
Location: https://outside.example/collect
```

Người dùng click một liên kết bắt đầu bằng domain đáng tin nhưng sau đó được đưa tới website ngoài. Đây là **unvalidated redirect**. Kẻ tấn công có thể:

- Dùng domain tin cậy như bước trung gian trong chiến dịch phishing.
- Vượt qua bộ lọc URL chỉ kiểm tra **domain của liên kết đầu tiên**.
- Thao túng luồng **login/logout/return URL**.
- Kết hợp sơ hở trong OAuth/OIDC redirect registration, `state` hoặc token/code handling để làm lộ thông tin, tùy mô hình.
- Chuyển người dùng tới site phân phối mã độc hoặc giả danh biểu mẫu.

**Không phải mọi redirect sang domain khác đều là lỗ hổng.** Đôi khi thanh toán, SSO hoặc partner integrations cần external redirect. Lỗ hổng nằm ở việc **dữ liệu không tin cậy được phép quyết định đích mà không có chính sách kiểm soát đủ chặt**.

### 3.1. Anti-pattern trong Spring MVC

```java
// KHÔNG DÙNG: request input quyết định trực tiếp URL đích.
@GetMapping("/go")
String go(@RequestParam String next) {
    return "redirect:" + next;
}
```

`redirect:` trong MVC là **chỉ dẫn tạo redirect view**, không phải validator URL. Spring không thể biết business allowlist domain của bạn.

```java
// Cũng KHÔNG DÙNG: sao chép raw target vào Location.
@GetMapping("/go")
ResponseEntity<Void> go(@RequestParam String next) {
    return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(next))
            .build();
}
```

Các fragment trên cho thấy lỗi bảo mật, không phải API được khuyến nghị. `URI.create` chỉ parse URI theo cú pháp nhất định; nó **không quyết định** domain hay đường dẫn có được phép hay không.

### 3.2. Vì sao chặn mỗi chuỗi "http" không đủ?

Những dạng đích đáng xem xét khi input là URL/path:

| Dạng do client đề xuất | Điểm nguy hiểm |
| --- | --- |
| `https://attacker.example/path` | URL tuyệt đối tới domain ngoài |
| `//attacker.example/path` | Network-path reference: browser dùng scheme hiện tại nhưng host bên ngoài |
| `\\\\attacker.example` hoặc dấu slash/backslash hỗn hợp | Một số tầng client/proxy/parser xử lý dấu phân cách khác nhau |
| `https://trusted.example.attacker.example/` | Host có prefix giống domain tin cậy nhưng thực ra khác |
| `https://trusted.example@attacker.example/` | Phần trước `@` là userinfo; host thực nằm sau `@` |
| `https://TRUSTED.EXAMPLE./` | Domain case/trailing dot; cần chuẩn hóa theo chính sách |
| URL chứa `%2f`, `%5c`, `%252f` | URL encoding và double-decoding gây mơ hồ |
| URL chứa CR/LF hoặc encoded CR/LF | Header/log injection tùy downstream và validation |
| URL có scheme `javascript:` / `data:` | Không phải đích HTTP(S) an toàn để tự chuyển hướng |
| Open-redirect endpoint trên chính domain tin cậy | Có thể thành **redirect chain** đến attacker |

Không dùng kiểm tra `next.startsWith("https://trusted.example")` hoặc `next.contains("trusted.example")`. Chúng có thể nhận nhầm `trusted.example.attacker.example`, userinfo hoặc path chứa chuỗi domain.

## 4. Cách an toàn nhất: không nhận URL mà nhận mã đích

Nếu chức năng chỉ cần chuyển về một vài trang nội bộ, nhận **mã đích được allowlist**, không nhận URL/path:

```text
Client gửi: destination=dashboard
Server map: "dashboard" -> "/portal/dashboard"

Client gửi: destination=help
Server map: "help" -> "/portal/help"

Client gửi: destination=https://outside.example
Server: 400 Bad Request (không Location)
```

Ưu điểm: không có vấn đề URL canonicalization, protocol-relative URL, userinfo, IDN hoặc double encoding cho những đích không được hỗ trợ. Mapping cố định do server sở hữu cũng giúp audit dễ hơn.

Trong code mẫu ở phần 9:

```java
String path = switch (destination) {
    case "dashboard" -> "/portal/dashboard";
    case "help" -> "/portal/help";
    default -> throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Unsupported destination");
};

return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(URI.create(path))
        .build();
```

Đây là **fragment trong handler**. Status `303 See Other` phù hợp một form POST hoàn tất và muốn browser thực hiện GET đích; với navigation GET thuần, redirect 302/303 tùy contract. **Không cần gửi lại raw destination vào HTML/log/error message**.

### 4.1. Khi phải hỗ trợ redirect ngoài domain

Chỉ xét cho flow có business requirement rõ ràng (đối tác thanh toán, SSO). Hướng thiết kế:

1. Ưu tiên **partner ID** → lấy URL từ config tin cậy, thay vì để client đưa URL tuyệt đối.
2. Nếu URL thực sự được client truyền, parse bằng URI parser và yêu cầu scheme `https` (hoặc policy riêng) cùng **host chính xác** sau canonicalization.
3. Dùng **danh sách exact hosts / origins**, không dựa vào substring hay wildcard `*.example.com` quá rộng.
4. Cân nhắc **port, userinfo, path, query, trailing dot, IDN/punycode, fragment, percent encoding và normalize**; từ chối inputs mơ hồ thay vì cố đoán.
5. Nếu cần external consent, cho người dùng thấy destination host rõ ràng; không bao giờ gắn access tokens, session IDs hoặc password vào URL.
6. Với OAuth2/OIDC, **redirect_uri phải khớp quy tắc đăng ký của provider** và `state`/PKCE được kiểm chứng theo flow; việc app có một “allowlist gần đúng” không thay thế bảo vệ giao thức.
7. Kiểm soát redirect chains: URL internal hợp lệ không được biến thành lối vòng để đi tiếp tới attacker.
8. Review sự khác nhau giữa public base URL, reverse proxy, trusted forwarded headers và path prefix khi deploy.

**Điểm dễ nhầm trong Spring:** `RedirectView#setHosts(...)` hoặc `UrlBasedViewResolver#setRedirectHosts(...)` dùng thông tin hosts trong **cơ chế URL encoding/session handling**, **không phải một cơ chế allowlist bảo mật tuyệt đối để cấm open redirect**. Không dùng cấu hình đó làm thay cho validation/route mapping nghiệp vụ.

## 5. Redirect trong Spring MVC: status, URI, scope và data leakage

### 5.1. `redirect:` và `RedirectView`

`@Controller` có thể trả:

```java
@PostMapping("/save")
String save() {
    // Đã lưu nghiệp vụ thành công.
    return "redirect:/portal/dashboard";
}
```

Fragment này dùng **đích cố định từ server**, khác với `"redirect:" + untrustedValue`. Spring tạo redirect view và thông thường sinh status 302 theo mặc định Servlet MVC; có thể cấu hình 303/redirect view status theo version/thiết kế.

Với `ResponseEntity`:

```java
return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(URI.create("/portal/dashboard"))
        .build();
```

Mẫu này trả HTTP `303` trực tiếp và tường minh. Đích root-relative `/portal/dashboard` giả định app nằm ở origin root. Nếu ứng dụng triển khai dưới `/shop` hoặc gateway prefix, cần dựng URL có prefix **public** đúng; `RedirectView#setContextRelative(true)` có thể hỗ trợ context-relative view redirects. Tránh lấy host ngẫu nhiên từ client rồi tạo external URL.

### 5.2. Chọn status phù hợp

| Status | Semantics cơ bản | Lưu ý |
| --- | --- | --- |
| `302 Found` | Redirect tạm thời; user agents lịch sử có thể đổi POST thành GET | Dùng nhiều cho web navigation |
| `303 See Other` | Hướng client lấy representation ở URI khác, thông thường qua GET | Phù hợp Post-Redirect-Get |
| `307 Temporary Redirect` | Giữ HTTP method và body qua redirect | Có thể vô tình forward POST body nhạy cảm nếu destination không tin cậy |
| `308 Permanent Redirect` | Redirect vĩnh viễn và giữ method | Không dùng vô tình cho thay đổi tạm hoặc user-controlled target |
| `301 Moved Permanently` | Chuyển thường trực; client/cache có thể lưu mapping | Cẩn thận với link chưa chuẩn và auth/session flows |

Nếu bạn redirect một POST chứa password hoặc payload bí mật bằng `307/308` sang host không tin cậy, client có thể gửi lại method/body tới host đó; đây là lý do nghiêm trọng để không cho client quyết định redirect target. **303 không tự khiến target an toàn**; nó chỉ thay đổi follow-up method semantics.

### 5.3. Không đưa bí mật vào URL, model redirect hay header

Các nơi có thể lộ query parameters: browser history, reverse-proxy access logs, analytics, monitoring, crash reports, referrers hoặc screenshots.

Không redirect kiểu:

```text
Location: https://partner.example/landing?access_token=<secret>
Location: /portal/dashboard?password=<secret>
```

`RedirectAttributes.addAttribute(...)` có thể thêm giá trị vào redirect URL; `addFlashAttribute(...)` lưu giá trị tạm cho request sau (thường dùng session/FlashMap), phù hợp thông báo không nhạy cảm. **Flash attributes vẫn thuộc server state và không phải nơi nên cất token bí mật một cách tùy tiện**.

Với SPA dùng OAuth redirect, theo đúng flow OAuth/OIDC, không tự phát token từ server vào URL chỉ vì dễ làm.

## 6. File upload: các lớp input không đáng tin

Một request file upload thường là `multipart/form-data`, ví dụ:

```http
POST /api/images HTTP/1.1
Content-Type: multipart/form-data; boundary=...

--...
Content-Disposition: form-data; name="file"; filename="avatar.png"
Content-Type: image/png

<binary content>
--...--
```

Cả `filename`, `Content-Type` lẫn bytes bên trong đều do client kiểm soát. Những kiểu tấn công đáng lưu ý:

- **Path traversal:** filename như `../../config.yml` nếu code ghép trực tiếp vào `Path`.
- **Extension confusion:** `photo.png.exe`, tên chấm/ký tự Unicode đặc biệt, case differences, double extension, trailing dots/spaces; phục vụ có thể bị nhầm content type hoặc thực thi.
- **MIME spoofing:** header khai `image/png` nhưng body là HTML/script hoặc định dạng khác.
- **Polyglot files:** một file có thể có signature hợp lệ của một định dạng và nội dung phụ nguy hiểm; magic bytes đơn lẻ không đủ đảm bảo vô hại.
- **Malware và active content:** Office macro, SVG script, HTML, PDF active features, archive executables.
- **Archive extraction attacks:** ZIP slip (`../` trong entry name), zip bomb, symbolic links, nested archives.
- **Image decompression bombs:** file compressed nhỏ nhưng ảnh khi decode có kích thước/pixel cực lớn.
- **Excessive resource usage:** upload lớn, multipart nhiều parts, requests liên tục, storage quota bị đầy.
- **Unauthorized access:** tài liệu riêng được lưu dưới URL dễ đoán hoặc static public folder; không kiểm tra owner khi download.
- **File overwrite/race:** dùng original filename làm target khiến ghi đè file khác.
- **Log leakage:** ghi raw original filename hoặc nội dung file vào logs, trace/APM.

Các biện pháp phải áp dụng **theo loại file, người dùng, mức độ tin cậy và use case**, không bật một kiểm tra chung rồi coi mọi uploads là an toàn.

## 7. Chính sách upload chặt: allowlist + kiểm chứng nội dung + storage isolation

### 7.1. Allowlist loại file

Chọn danh sách **ngắn, cần cho nghiệp vụ**. Ví dụ profile ảnh PNG, không mở `image/*` rồi chấp nhận mọi ảnh/script-bearing SVG. Cần phân biệt:

1. **Extension**: chỉ metadata gợi ý cho UX, không phải bằng chứng.
2. **Declared MIME**: `MultipartFile#getContentType()`, do client tự khai.
3. **Signature/magic**: vài bytes đầu; kiểm tra sơ bộ, vẫn chưa đủ với polyglot/định dạng phức tạp.
4. **Decoder/parser validation**: cấu trúc file được library phù hợp đọc được, có giới hạn resources.
5. **Canonicalization/CDR/transcoding**: xây lại representation đã kiểm tra và loại bớt metadata/phần nguy hiểm nếu định dạng cho phép.
6. **AV/sandbox scanning**: kiểm tra mã độc/đặc tính thực thi theo policy; không thay thế validation và authorization.

**Không** lấy tên gốc `file.getOriginalFilename()` rồi lưu tại `static/uploads/`; đó là mẫu dễ gây traversal, overwrite, public exposure và unsafe type handling.

### 7.2. Giới hạn kích thước ở nhiều tầng

Với Spring Boot 3.5 Servlet multipart:

```properties
# Chỉ ví dụ cho demo chỉ nhận PNG cỡ nhỏ:
spring.servlet.multipart.max-file-size=256KB
spring.servlet.multipart.max-request-size=320KB
```

Đây là **giới hạn do multipart parser/container** áp dụng. Vẫn nên xác minh bytes của file tại application layer và áp quota/rate limit ở gateway/server. Upload có thể bị từ chối **trước khi controller chạy**; các lỗi thường liên quan `MaxUploadSizeExceededException` và HTTP 413 trong error handling được cấu hình.

`max-file-size` khác `max-request-size`: request có thể có nhiều parts/headers overhead, nên giới hạn tổng có thể lớn hơn giới hạn từng file. Không tin `Content-Length` là đo chính xác trong mọi request; dữ liệu chunked/streamed có thể khác. Với stream lớn, ưu tiên streaming và hạn mức đếm bytes để tránh đọc hết vào memory.

### 7.3. Store ngoài webroot, tên do server sinh

Mô hình đáng dùng:

```text
Upload request
  -> xác thực + phân quyền + quota
  -> validate type/size/structure
  -> AV/CDR/normalization nếu cần
  -> quarantine area
  -> random generated storage key, không dùng client filename
  -> private storage / object store
  -> lưu metadata record (owner, size, MIME, hash, status)
  -> download endpoint kiểm tra quyền
  -> Content-Disposition và Content-Type an toàn
```

Trong demo phần 9, lưu **PNG đã decode rồi re-encode** vào thư mục tạm ngẫu nhiên do server tạo, dùng filename hệ thống tạo, không phục vụ trực tiếp qua Boot `static/**`. Đây là **môi trường học tập**, không phải giải pháp lưu file production bền vững; thư mục tạm có thể bị xóa, không phân quyền giữa người dùng và không có virus scanner.

### 7.4. Phòng path traversal và symlink

Anti-pattern:

```java
// NGUY HIỂM: filename do client kiểm soát quyết định path.
Path output = uploadRoot.resolve(file.getOriginalFilename());
file.transferTo(output);
```

Nếu original filename chứa `../` hoặc đường dẫn tuyệt đối, việc resolve/normalize không được kiểm tra đúng có thể thoát khỏi upload root. Ngoài ra có race nếu attacker có quyền thao túng symlink/path trong storage directory.

Ưu tiên:

- Tạo opaque identifier bằng `UUID.randomUUID()` hoặc random storage key đáng tin.
- Tạo file ở private folder do server kiểm soát, ví dụ `Files.createTempFile(root, "img-", ".png")`; không chèn filename client vào path.
- Không cho request cung cấp **arbitrary relative path**.
- Nếu phải dùng path từ client, cần normalize/canonicalize, resolve real path theo policy, chống traversal/symlink và kiểm tra ownership, kể cả race conditions.
- Dùng storage API có key semantics riêng khi chạy trên S3/GCS; đừng nhầm object-store keys với filesystem path security.

**Không lưu upload vào** `src/main/resources/static` / `public` / thư mục executable WAR/JAR rồi tưởng mỗi file có auth. Các locations này thường dùng để public static content.

## 8. Giao tiếp HTTP khi upload/download

Một API upload hợp lệ có thể trả `201 Created` với ID server cấp và `Location` chỉ tới resource mới:

```http
HTTP/1.1 201 Created
Location: /api/images/3a72ad90-f5cb-47ed-b97b-23278331fb9d
Content-Type: application/json

{"id":"3a72ad90-f5cb-47ed-b97b-23278331fb9d",
 "mediaType":"image/png"}
```

ID ở đây là ví dụ format; response thực tế do server cấp ngẫu nhiên. Nếu xử lý upload cần **quét mã độc bất đồng bộ** trước khi coi file sẵn sàng, một thiết kế hợp lý là `202 Accepted` với trạng thái processing/quarantine; **không công khai file trước khi đạt policy**.

Download phải được quyết định ở **server side**:

- Lấy record dựa trên opaque ID, xác minh authenticated user có quyền.
- Không ghép trực tiếp input ID thành đường dẫn tùy ý.
- Chọn `Content-Type` dựa trên **loại đã kiểm chứng**, không lặp raw upload content type.
- Với nội dung có nguy cơ được browser render như HTML/SVG/PDF, cân nhắc `Content-Disposition: attachment` và content scanning/sandbox, CSP hoặc serving từ origin cô lập.
- Đặt `X-Content-Type-Options: nosniff` khi phù hợp.
- Dùng `Cache-Control: private` hoặc `no-store` với tài liệu nhạy cảm; không giả định CDN công khai có thể cache mọi ảnh tải lên.
- Không phản hồi filesystem path, storage key nội bộ hoặc raw exception message.

**Không bao giờ coi UUID là cơ chế phân quyền.** UUID khó đoán giúp giảm dự đoán nhưng GET/download vẫn phải kiểm tra quyền của người dùng đối với resource.

## 9. Ví dụ Spring Boot 3.5 / Java 17 có thể chạy

Demo gồm:

- `POST /navigation/continue?destination=dashboard|help` trả `303 Location` từ **mã allowlist**.
- `GET /portal/dashboard` và `GET /portal/help` để quan sát đích chuyển hướng.
- `POST /api/images` chỉ nhận PNG nhỏ qua multipart; kiểm tra kích thước trước/sau đọc, dùng Java ImageIO xác định định dạng, giới hạn chiều rộng/cao, **re-encode PNG**, lưu dưới tên random ở private temp directory.
- `GET /api/images/{uuid}` trả PNG đã chuẩn hóa dưới dạng **attachment** với `nosniff` và `Cache-Control: no-store`.
- Logging chỉ ghi ID do server cấp, kích thước output; không ghi request body, original filename, cookie/token.
- Test MockMvc cho allowlist, invalid redirect, upload hợp lệ/không hợp lệ và download.

**Giới hạn chủ ý của demo:** không có authentication/authorization, AV scan, user quota, shared persistent storage, key rotation hoặc moderation. **Không triển khai công khai một endpoint lưu/truy xuất upload y hệt demo**; phải bổ sung các lớp này trước production.

### 9.1. Gradle và multipart properties

Trong project Boot 3.5 đã có Boot plugin/repositories/Java toolchain:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đây là fragment `build.gradle`; thêm vào project Boot chuẩn, không coi là toàn bộ file Gradle độc lập. Java `ImageIO` thuộc JDK `java.desktop` module (có ở Java 17 thông thường; với custom jlink image phải giữ module phù hợp).

Tạo `src/main/resources/application.properties`:

```properties
spring.servlet.multipart.max-file-size=256KB
spring.servlet.multipart.max-request-size=320KB

# Không chủ động đưa request params/headers có thể nhạy cảm
# vào Spring MVC debug log:
spring.mvc.log-request-details=false
```

`spring.mvc.log-request-details=false` là mặc định thông dụng của Boot 3.5, nhưng ghi tường minh giúp thấy intent. Setting này **không xóa những log do code, reverse proxy, servlet access log, security filters hoặc APM khác ghi**.

### 9.2. Java application/controller/upload logic

Tạo **`src/main/java/demo/safeweb/SafeWebApplication.java`**:

```java
package demo.safeweb;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class SafeWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(SafeWebApplication.class, args);
    }
}

@RestController
class NavigationController {
    @PostMapping("/navigation/continue")
    ResponseEntity<Void> continueTo(
            @RequestParam String destination) {

        String path = switch (destination) {
            case "dashboard" -> "/portal/dashboard";
            case "help" -> "/portal/help";
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported destination");
        };

        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create(path))
                .build();
    }

    @GetMapping("/portal/dashboard")
    String dashboard() {
        return "dashboard";
    }

    @GetMapping("/portal/help")
    String help() {
        return "help";
    }
}

record ImageResponse(String id, String mediaType) {}

@RestController
@RequestMapping("/api/images")
class ImageController {
    private static final Logger log =
            LoggerFactory.getLogger(ImageController.class);

    private static final int MAX_INPUT_BYTES = 256 * 1024;
    private static final int MAX_DIMENSION = 1024;
    private static final int MAX_PIXELS = 1024 * 1024;
    private static final int MAX_OUTPUT_BYTES = 4 * 1024 * 1024;

    private final Path privateRoot;
    private final ConcurrentMap<UUID, Path> saved =
            new ConcurrentHashMap<>();

    // DEMO ONLY: random temporary directory, not public/static.
    // Production: dedicated private storage, access control and lifecycle.
    ImageController() throws IOException {
        privateRoot = Files.createTempDirectory("safe-image-demo-");
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ImageResponse> upload(
            @RequestPart("file") MultipartFile file)
            throws IOException {

        if (file.isEmpty() || file.getSize() > MAX_INPUT_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid image size");
        }

        // Verify actual bytes, not originalFilename or declared MIME.
        byte[] input = file.getBytes();
        if (input.length == 0 || input.length > MAX_INPUT_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid image size");
        }

        BufferedImage decoded = decodePng(input);

        // Re-encode the decoded pixels; do not serve the original bytes.
        ByteArrayOutputStream normalized = new ByteArrayOutputStream();
        if (!ImageIO.write(decoded, "png", normalized)) {
            throw new IOException("PNG encoder is unavailable");
        }
        byte[] canonicalPng = normalized.toByteArray();
        if (canonicalPng.length > MAX_OUTPUT_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Image output too large");
        }

        UUID id = UUID.randomUUID();
        Path generated = Files.createTempFile(
                privateRoot, "image-", ".png");

        try {
            Files.write(generated, canonicalPng);
            saved.put(id, generated);
        } catch (IOException ex) {
            Files.deleteIfExists(generated);
            throw ex;
        }

        // Log server-generated, bounded metadata only.
        log.info("image_upload_saved imageId={} bytes={}",
                id, canonicalPng.length);

        URI location = URI.create("/api/images/" + id);
        return ResponseEntity.created(location)
                .body(new ImageResponse(id.toString(), "image/png"));
    }

    @GetMapping(path = "/{id}",
            produces = MediaType.IMAGE_PNG_VALUE)
    ResponseEntity<byte[]> download(@PathVariable UUID id)
            throws IOException {

        Path stored = saved.get(id);
        if (stored == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Image not found");
        }

        // DEMO ONLY: real application must verify owner/permission first.
        byte[] content = Files.readAllBytes(stored);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"image.png\"")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(content);
    }

    private static BufferedImage decodePng(byte[] bytes)
            throws IOException {

        try (ImageInputStream input =
                     ImageIO.createImageInputStream(
                             new ByteArrayInputStream(bytes))) {

            if (input == null) {
                throw invalidImage();
            }

            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidImage();
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!reader.getFormatName()
                        .equalsIgnoreCase("png")) {
                    throw invalidImage();
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width < 1 || height < 1
                        || width > MAX_DIMENSION
                        || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw invalidImage();
                }

                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalidImage();
                }
                return image;
            } catch (IOException ex) {
                throw invalidImage();
            } finally {
                reader.dispose();
            }
        }
    }

    private static ResponseStatusException invalidImage() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid PNG image");
    }
}
```

**Một file Java 17 hợp lệ theo class structure:** chỉ `SafeWebApplication` là public; các controller và record còn lại package-private, cùng package `demo.safeweb`. Java 17 cung cấp switch expression, `ImageIO` và `UUID`. Với dependency Boot web và JDK đầy đủ, Spring component scan sẽ phát hiện hai controllers.

**Quan trọng về mức bảo đảm:** ImageIO được giới hạn size/dimensions rồi re-encode, giúp chống nhiều kiểu file giả dạng và loại bớt metadata/raw payload không mong muốn, nhưng vẫn **không phải sandbox hoặc antivirus**. Image decoder có thể có CVE; compressed input nhỏ vẫn có thể gây áp lực decode; với loại file có nguy cơ cao, dùng process isolation, patched codec, sandbox/AV/CDR và resource quotas mạnh hơn. `MAX_INPUT_BYTES` giới hạn file raw; `MAX_DIMENSION` và `MAX_PIXELS` giới hạn pixel count; không thay thế OS-level memory/CPU limits.

**Chi tiết lưu trữ:** `Files.createTempDirectory` sinh thư mục riêng ngoài static resources; `Files.createTempFile` sinh tên server-controlled, không bao giờ dùng `MultipartFile.getOriginalFilename()`. `saved` chỉ là in-memory map — restart sẽ mất mapping, file tạm có thể không được dọn và không có retention management. Khi triển khai thật phải thay bằng metadata DB/storage service và cleanup/quota theo chính sách.

**Chi tiết tải xuống:** response là `attachment` với tên cố định, MIME được chuẩn hóa và `nosniff`. Không cho browser tự quyết định render original HTML/SVG đã upload. Ở production bắt buộc có auth/authorization trên **cả upload lẫn download**, kiểm tra owner/tenant và trạng thái scan.

### 9.3. Chạy thử bằng curl

```bash
./gradlew bootRun
```

Chuyển tới trang được phép (dùng `-i` để xem 303 thay vì tự theo redirect):

```bash
curl -i -X POST \
  'http://localhost:8080/navigation/continue' \
  --data 'destination=dashboard'
```

Kỳ vọng status `303 See Other` và `Location: /portal/dashboard`.

Thử external redirect do client đề nghị:

```bash
curl -i -X POST \
  'http://localhost:8080/navigation/continue' \
  --data-urlencode 'destination=https://outside.example/collect'
```

Kỳ vọng `400 Bad Request`, **không có `Location` do attacker kiểm soát**.

Upload ảnh PNG hợp lệ (file phải thực sự là PNG):

```bash
curl -i -X POST \
  'http://localhost:8080/api/images' \
  -F 'file=@avatar.png;type=image/png'
```

Nếu ảnh dưới 256 KiB, kích thước không vượt 1024×1024 và decoder đọc được, server dự kiến trả `201 Created`, `Location: /api/images/<uuid-thực>` và body JSON chứa ID. Dấu `<uuid-thực>` là placeholder hướng dẫn, không phải ID được dự đoán trước.

Theo URL `Location` nhận được:

```bash
curl -i 'http://localhost:8080/api/images/<uuid-thực>'
```

Kết quả dự kiến: `200 OK`, `Content-Type: image/png`, `Content-Disposition: attachment; filename="image.png"`, `X-Content-Type-Options: nosniff` và PNG đã chuẩn hóa. Với `curl -o downloaded.png`, dùng đúng URL lấy từ 201.

Thử đổi tên một file text thành `avatar.png`: endpoint sẽ từ chối nếu ImageIO không giải mã nó thành PNG hợp lệ. Nếu gửi ảnh quá lớn, lớp multipart có thể chặn từ trước controller; error status/body cụ thể phụ thuộc exception/error handling và container. Dữ liệu demo không được coi là đã trải qua quét mã độc.

## 10. Integration tests với MockMvc

Tạo **`src/test/java/demo/safeweb/SafeWebApplicationTests.java`**:

```java
package demo.safeweb;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SafeWebApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void knownDestinationRedirectsToInternalRoute()
            throws Exception {

        mvc.perform(post("/navigation/continue")
                .param("destination", "dashboard"))
            .andExpect(status().isSeeOther())
            .andExpect(header().string(
                    HttpHeaders.LOCATION, "/portal/dashboard"));
    }

    @Test
    void externalDestinationIsRejectedWithoutLocation()
            throws Exception {

        MvcResult result = mvc.perform(post("/navigation/continue")
                .param("destination",
                        "https://outside.example/collect"))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertNull(result.getResponse()
                .getHeader(HttpHeaders.LOCATION));
    }

    @Test
    void protocolRelativeDestinationIsRejected()
            throws Exception {

        mvc.perform(post("/navigation/continue")
                .param("destination", "//outside.example/path"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void actualPngIsNormalizedAndDownloaded()
            throws Exception {

        BufferedImage small =
                new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(small, "png", output));

        // Client-controlled filename is deliberately suspicious.
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../private-config.yml",
                "application/octet-stream", output.toByteArray());

        MvcResult uploaded = mvc.perform(
                multipart("/api/images").file(file))
            .andExpect(status().isCreated())
            .andReturn();

        String location = uploaded.getResponse()
                .getHeader(HttpHeaders.LOCATION);

        assertNotNull(location);
        assertTrue(location.matches(
                "/api/images/[0-9a-fA-F-]{36}"));

        mvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(content()
                    .contentTypeCompatibleWith(MediaType.IMAGE_PNG))
            .andExpect(header().string(
                    "X-Content-Type-Options", "nosniff"))
            .andExpect(header().string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"image.png\""));
    }

    @Test
    void fakePngBytesAreRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png",
                "not a png".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/images").file(file))
            .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedFileIsRejectedAtApplicationLayer()
            throws Exception {

        MockMultipartFile file = new MockMultipartFile(
                "file", "large.png", "image/png",
                new byte[256 * 1024 + 1]);

        mvc.perform(multipart("/api/images").file(file))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unknownImageIdReturns404() throws Exception {
        mvc.perform(get("/api/images/"
                + "00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isNotFound());
    }
}
```

Chạy:

```bash
./gradlew test
```

Với code Boot 3.5/JDK ImageIO như trên, bảy bài test được **thiết kế để pass**. Đây là kết quả kỳ vọng sau rà soát, **chưa phải output của một lần chạy thực tế**. `MockMvc` không kiểm tra permission của OS filesystem, proxy max body size, nhiều nodes, upload quotas, antivirus/CDR hay log pipeline; cần thêm integration/security/penetration tests tương ứng.

Mục đích test:

- Redirect không nhận full URL hoặc `//host`.
- Upload body phải là PNG đọc được, không chỉ tự khai `Content-Type: image/png`.
- Upload dùng filename bất thường vẫn lưu dưới tên generated và không ghi đè file đích do người dùng chỉ định.
- Download trả MIME/cách xử lý attachment đã được server chọn.
- Trường hợp input size vượt giới hạn trong MockMvc bị controller từ chối.
- Không có bằng chứng nào cho phép kết luận demo đã có **user authorization**, malware scanning hay storage hardening production.

## 11. Những yêu cầu chưa thể bỏ qua ở production upload

### 11.1. Authentication, authorization và ownership

- Chỉ người dùng đủ quyền mới upload.
- Mỗi file record cần owner/tenant, trạng thái scan/quarantine, MIME chuẩn hóa, size, storage key, creation time và retention policy.
- Download kiểm tra quyền **mỗi lần**, kể cả khi biết UUID.
- Tránh IDOR/BOLA: client thay `/api/images/{id}` sang ID người khác không được nhận file.
- Với file public, dùng public object storage/CDN có content rules; với file riêng, dùng signed URL ngắn hạn hoặc download proxy đã xác thực.
- Signed URLs có thời hạn và scope cũng là **credentials**; không ghi nguyên chúng vào logs hoặc referrers.

### 11.2. Antivirus/sandbox và content disarm

Một PNG re-encode không đảm bảo decoder không có lỗi bảo mật hoặc image không mang payload nguy hiểm. Với PDF, DOCX, ZIP, SVG hoặc các định dạng hỗ trợ active content, cần xử lý chuyên biệt:

| Loại file | Ví dụ kiểm soát bổ sung |
| --- | --- |
| PNG/JPEG | Safe decoder, giới hạn pixels/dimensions, strip metadata, re-encode, image-library patching |
| SVG | Không coi là ảnh vô hại; script/links có thể tồn tại; cấm hoặc sanitize nghiêm ngặt và serve isolated |
| PDF | Type/content scanner, sandbox/CDR nếu cần, attachment hoặc isolated viewer |
| DOCX/XLSX | CDR/macro policy, AV scan, không preview trực tiếp mà thiếu sandbox |
| ZIP/TAR | Giới hạn file count, nested depth, decompressed size, chống zip-slip/symlink |
| HTML/JS | Không lưu/serve ở cùng authenticated origin nếu không có isolation phù hợp |
| Executable | Mặc định không cho upload hoặc chỉ trong workflow có kiểm soát chuyên biệt |

Không “chỉ kiểm tra magic bytes” rồi mở PDF/Office/ZIP không giới hạn; parser và preview engine cũng là bề mặt tấn công.

### 11.3. Transaction, consistency và cleanup

- Nếu scan async, đừng công khai file khi trạng thái còn `PENDING`; trả job/upload status theo contract.
- Nếu DB record được tạo nhưng object upload thất bại (hoặc ngược lại), cần rollback/compensation/cleanup.
- Cần quotas theo user/tenant, max object count, max daily bytes, lifecycle TTL, retention/deletion compliance.
- Dùng key do server sinh, avoid collisions/overwrites; với object store chọn write semantics và bucket permissions phù hợp.
- Quản lý tmp files do multipart parser và application tạo; không để file tạm tích tụ vô hạn.
- Cấu hình antivirus/decoder timeouts, memory/CPU/file descriptor limits và separate processing workers.
- Re-encoding ảnh có thể thay đổi màu/alpha/metadata; xác nhận output phù hợp hợp đồng sản phẩm.

## 12. Thông tin nhạy cảm trong log: những gì không nên ghi

OWASP Logging Cheat Sheet liệt kê các loại thường không được log trực tiếp hoặc phải mask/sanitize/hide:

| Dữ liệu | Rủi ro khi xuất hiện trong log | Cách xử lý |
| --- | --- | --- |
| `Authorization: Bearer <token>` | Token có thể bị tái sử dụng | Không ghi token; nếu cần, log kết quả auth và event ID |
| `Cookie` / `Set-Cookie` / `JSESSIONID` | Session hijacking | Loại bỏ hoàn toàn session ID khỏi log |
| Password, OTP, reset token, private key | Lộ credentials | Không log và không phản hồi raw |
| OAuth code, signed URL, API key | Chiếm quyền hoặc truy cập file | Redact token và query parameters |
| Raw JSON/form body | Có thể chứa PII/password, dữ liệu tài chính | Tránh log body; log validated metadata theo allowlist |
| Multipart filename, bytes, document text | Có thể chứa PII, newline injection, malware payload | Không log nguyên bản; server-generated ID thay thế |
| Email, số điện thoại, địa chỉ, ID giấy tờ | Privacy/compliance impact | Chỉ log khi thật cần và theo pháp lý/policy, dùng pseudonymization phù hợp |
| Full exception message/stack trace | Có thể chứa SQL, paths, input, secrets | Sanitize, phân loại lỗi; log stack có kiểm soát và retention |
| Full URL/query/referrer | Query có thể mang token/password | Log route template, bỏ hoặc whitelist query keys |
| Raw IP, User-Agent | PII hoặc untrusted data | Tùy threat model, tuân thủ policy và sanitize |

**Không có quy tắc “mọi log đều phải trống”**. Security event logs vẫn hữu ích để phát hiện tấn công, nhưng chỉ nên ghi fields có mục đích, được phân loại theo độ nhạy cảm, có quyền truy cập và thời hạn lưu rõ.

### 12.1. Parameterized logging không phải redaction

Không nên:

```java
// Anti-pattern: log raw password và token.
log.info("Login user={} password={} token={}",
        request.getParameter("user"),
        request.getParameter("password"),
        request.getHeader("Authorization"));
```

Dạng an toàn hơn:

```java
// Fragment: dữ liệu đã được chọn theo chính sách.
log.info("login_attempt result={} authMethod={} requestId={}",
        "rejected", "password", trustedRequestId);
```

Ở đây `trustedRequestId` phải do server tạo hoặc validate nghiêm ngặt từ hạ tầng tin cậy. Không đổi sang `log.info("userInput={}", rawInput)` rồi cho rằng placeholder tự loại bỏ CR/LF; nó **không phải bộ lọc nội dung**.

### 12.2. Log injection / log forging

Một trường request có thể chứa ký tự xuống dòng nhằm tạo bản ghi giả:

```text
Input malicious (mô phỏng):
  queryName = "Alice\r\n2026-01-01 ERROR admin-login-success"

Nếu log nối trực tiếp:
  INFO queryName=Alice
  2026-01-01 ERROR admin-login-success
```

Log reader, SIEM hoặc parser theo dòng có thể hiểu nhầm dòng thứ hai là event độc lập. Các dấu phân cách `,`, `|`, tabs, JSON quotes/control characters cũng gây vấn đề nếu format không encode đúng.

Biện pháp:

- Ưu tiên **structured JSON logging** bằng serializer đúng, không tự nối text thành JSON.
- Nếu bắt buộc log dữ liệu client ở field cho phép, **sanitize CR/LF/control characters, escape theo sink**, giới hạn độ dài và giá trị.
- Đối với User-Agent/referer/filename, cân nhắc log **classification** hoặc hash/tokenization phù hợp thay vì raw text.
- Không tắt log security events cho IP/users “đã tin cậy”; tránh bỏ qua attacker đã đăng nhập.
- Log record có timestamps/IDs từ hệ thống tin cậy, không lấy nguyên timestamp do client đưa.
- Giữ logs trong destination có quyền truy cập hạn chế và cơ chế chống sửa/xóa/tamper; SIEM có retention/alert thích hợp.

Một helper tối thiểu nếu thực sự cần log chuỗi không tin cậy:

```java
static String safeLogField(String value) {
    if (value == null) {
        return "missing";
    }

    StringBuilder out = new StringBuilder();
    int limit = Math.min(value.length(), 120);

    for (int i = 0; i < limit; i++) {
        char c = value.charAt(i);
        if (Character.isISOControl(c)) {
            out.append('_');
        } else {
            out.append(c);
        }
    }
    return out.toString();
}
```

Đây là **method fragment** chỉ minh họa chuẩn hóa control characters và giới hạn ký tự. **Nó không tự phát hiện secrets/PII và không thay thế escaping theo JSON/XML/log sink**. Không dùng helper này để “hợp pháp hóa” việc log password/token — những dữ liệu đó phải **không được ghi**.

## 13. Log theo route template, không lặp lại raw request URL

Ví dụ với endpoint:

```text
POST /api/images?access_token=SECRET
Authorization: Bearer SECRET
Cookie: JSESSIONID=SECRET
```

Không ghi full URI, query string, raw headers. Một bản ghi hữu ích và ít nhạy cảm hơn:

```text
event=image_upload_saved requestId=<server-generated-id>
resourceId=<uuid> result=created outputBytes=1132
```

**Các giá trị ở trên là ví dụ**, không phải log của chương trình đã chạy. Nếu cần phân tích hiệu suất, dùng route template `/api/images`, HTTP status, duration bucket, bytes count; không đưa file bytes hay filename gốc vào tracing spans.

### 13.1. Tạo correlation ID đúng cách

Nếu cần request correlation ID, phương án đơn giản là server tạo UUID khi request vào, đặt trong MDC rồi **dọn `finally`** khi filter kết thúc:

```java
@Component
class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {

        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        try {
            response.setHeader("X-Request-Id", requestId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }
}
```

Đây là **class fragment độc lập**, cần imports Jakarta Servlet, `@Component`, `OncePerRequestFilter`, `MDC`, `UUID`. Không tự thêm vào project demo nếu không cần; nếu thêm, cấu hình registration/order sao cho server-generated request ID có mặt ở các nơi mong muốn.

Với async/multi-thread request, MDC **không tự động đi theo thread**. Cần context propagation strategy và cleanup theo từng thread/dispatch khi có nghiệp vụ thực sự cần. Không giữ `HttpServletRequest`/token dưới dạng ThreadLocal toàn thời gian.

Nếu chấp nhận request ID từ proxy, chỉ tin proxy đã cấu hình, giới hạn charset/length và chống CRLF injection. Nếu không chắc nguồn tin cậy, sinh ID mới trong application.

## 14. Log levels, filter, Spring Boot và phạm vi cấu hình

Trong Boot 3.5 có:

```properties
spring.mvc.log-request-details=false
logging.level.org.springframework.web=INFO
```

Điều này giúp hạn chế một số debug/trace request details của MVC, nhưng **không bảo đảm log hoàn toàn không có dữ liệu nhạy cảm**.

Cần kiểm tra thêm:

- Application logger do developer viết (`log.info/debug/error`).
- Spring Security authentication/authorization logs.
- Servlet container access logs; pattern có thể chứa URL/query, cookies hoặc headers.
- API gateway/reverse proxy/WAF request logs.
- Distributed tracing/APM auto instrumentation (span tags/header capture).
- Exception reporting/Sentry/crash dumps.
- CI/build deployment logs có env vars/secrets.
- Message queue/integration adapters có log request/response bodies.
- Central logging platform: who can access it, encryption at rest/in transit, retention, deletion/incident response.

**Không bật `TRACE` của toàn framework trong production** khi không có chính sách masking phù hợp. Với sự cố khẩn cấp cần debug, sử dụng flag/tài khoản có thời hạn, mục tiêu tối thiểu, rồi tắt và xử lý log đã thu theo retention/privacy requirements.

### 14.1. Chỉ log lỗi mà không lộ exception input

Không nên:

```java
// Anti-pattern nếu ex.getMessage() chứa raw request/token/SQL.
log.error("Upload failed, filename={}, cause={}",
        file.getOriginalFilename(), ex.getMessage(), ex);
```

Nên:

```java
log.warn("image_upload_rejected reason={} requestId={}",
        "invalid_format", trustedRequestId);
```

Chỉ log exception stack khi có lý do vận hành và đã đánh giá nó không chứa secrets; nếu cần stack trong sink nội bộ, bảo vệ sink đó kỹ hơn public logs và không đưa thẳng message vào HTTP error body.

## 15. Spring Security và CORS liên quan thế nào?

### 15.1. CORS không chống open redirects hoặc file upload độc hại

CORS là một cơ chế browser để quyết định web page **cross-origin** có được đọc response hay không. Nếu browser được người dùng yêu cầu điều hướng tới `https://outside.example` qua Location, CORS policy trên JSON API không phải “bức tường” ngăn redirect.

Tương tự, một client ngoài browser có thể gọi multipart endpoint nếu mạng và auth cho phép, **dù `Access-Control-Allow-Origin` không cho phép domain nào**. CORS không phải authentication.

### 15.2. CSRF vẫn quan trọng

Nếu upload endpoint và navigation endpoint dùng **cookie session authentication** và làm thay đổi server state, cần CSRF protection tùy kiến trúc. JavaScript/browser từ site khác có thể gửi một số dạng form requests cross-origin dù không đọc được response. Khi có Spring Security, cấu hình `SecurityFilterChain` theo endpoint và thêm CSRF token cho HTML form hoặc giải pháp phù hợp cho SPA.

Không tắt CSRF toàn ứng dụng chỉ để multipart example test pass. Demo không có Spring Security để giữ scope rõ; production cần xác thực, phân quyền, CSRF, rate limit, tenant isolation và content-type policies.

### 15.3. Response security headers

Những headers thường có ích khi phục vụ dữ liệu do người dùng tải lên:

| Header | Mục đích |
| --- | --- |
| `Content-Type` đúng loại đã kiểm chứng | Browser biết media type thật |
| `X-Content-Type-Options: nosniff` | Giảm một số kiểu MIME sniffing |
| `Content-Disposition: attachment` | Giảm khả năng browser render active content inline |
| `Cache-Control: no-store` / private | Giới hạn cache dữ liệu riêng |
| `Content-Security-Policy` | Hạn chế nguồn script/resource ở origin/view liên quan |
| `Referrer-Policy` | Giảm tiết lộ đường dẫn/query tới bên ngoài |
| `Strict-Transport-Security` trên HTTPS | Buộc HTTPS cho domain khi được triển khai đúng |

**Không có header đơn lẻ nào cho phép bỏ content validation.** Ví dụ serve raw SVG/HTML từ origin đang có session cookies có thể mang rủi ro rất khác serve PNG đã chuẩn hóa dưới dạng attachment.

## 16. Nếu cần download filename do người dùng đặt

Tình huống thường gặp: sản phẩm muốn trình duyệt tải file theo tên hiển thị do user chọn. Tránh ghép trực tiếp raw filename vào HTTP header:

```java
// Anti-pattern: CR/LF, quote hoặc ký tự lạ trong name gây rủi ro.
response.setHeader(
        "Content-Disposition",
        "attachment; filename=\"" + originalFilename + "\"");
```

Tốt hơn:

1. Lưu tên hiển thị riêng trong metadata DB, có validation về Unicode, length, control chars và policy.
2. Sinh storage filename độc lập, tuyệt đối không dùng display name làm path.
3. Khi trả header, dùng **Spring `ContentDisposition` builder** có xử lý encoding/quoting phù hợp, không tự nối chuỗi header thô.
4. Với loại nguy hiểm, ép download filename/extension an toàn từ media type đã xác minh, không tin ext gốc.
5. Nếu không cần tên gốc, luôn dùng một tên cố định như demo `image.png`.

Fragment:

```java
ContentDisposition disposition =
        ContentDisposition.attachment()
                .filename(safeDisplayName, StandardCharsets.UTF_8)
                .build();

return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
                disposition.toString())
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(fileBytes);
```

Đây là fragment trong handler, cần import `ContentDisposition`, `StandardCharsets`, `HttpHeaders` và khai báo `safeDisplayName`/`fileBytes`. **Builder không thay thế business allowlist nội dung**; `safeDisplayName` phải được kiểm tra và giới hạn trước đó. `Content-Type: application/octet-stream` không phải lựa chọn tối ưu cho mọi định dạng; tùy trường hợp và mục tiêu download.

## 17. Error handling và không tạo thông tin rò rỉ

Một lỗi invalid upload nên trả error body ngắn, ổn định:

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Invalid image",
  "status": 400,
  "detail": "The uploaded file is not an accepted PNG image"
}
```

Đây là **schema minh họa**, không mặc định mọi Boot app tự trả đúng body trên. Với Spring Framework 6+, `ProblemDetail`/`@RestControllerAdvice` có thể thống nhất lỗi; tránh đưa exception message nội bộ như `/tmp/...`, raw filename, parser stack trace vào response.

Nên phân loại:

| Tình huống | Status thường phù hợp |
| --- | --- |
| Redirect token không được hỗ trợ | `400 Bad Request` |
| Uploaded content không phải PNG hợp lệ | `400 Bad Request` hoặc `415 Unsupported Media Type` theo API contract |
| File/request vượt giới hạn | `413 Content Too Large` (tùy version dùng tên `Payload Too Large`) |
| Chưa xác thực upload/download | `401 Unauthorized` |
| Có xác thực nhưng không đủ quyền | `403 Forbidden` |
| File ID không có/không được tiết lộ | `404 Not Found` khi phù hợp |
| Scanner/storage không sẵn sàng | `503 Service Unavailable` hoặc status theo tình huống |
| Upload đã chấp nhận nhưng đang scan async | `202 Accepted` |
| Upload đã xử lý tạo thành công | `201 Created` |

Không hardcode mọi lỗi thành `200 OK` với `{"success":false}`. HTTP status giúp client và monitoring phân biệt lỗi. Không đặt raw user text làm `ProblemDetail.detail` nếu nó có thể lộ token hoặc nội dung nhạy cảm.

## 18. Triển khai sau reverse proxy, gateway, CDN

Một hệ thống thực tế có nhiều nơi diễn giải URL, body và log:

```text
Browser
   |
CDN/WAF
   |
Reverse Proxy/API Gateway
   |
Servlet container (Tomcat...)
   |
Spring Security FilterChain
   |
Spring MVC controller
   |
Private object storage / scanner
   |
Central log/metrics/tracing
```

Các kiểm tra phải nhất quán:

- **Redirect:** Gateway/proxy có thể chuẩn hóa path, scheme, host; đừng tin `Host`/`X-Forwarded-Host` do client tự gửi khi dựng external URL. Chỉ tin proxy headers từ nguồn đáng tin.
- **Upload limits:** Proxy, connector, multipart parser, app validator và object-store limits đều phải có policy nhất quán; request quá lớn có thể bị chặn trước khi vào controller.
- **Storage:** Shared object storage có bucket ACL, encryption, retention, quarantine, signed URL policy; không expose trực tiếp private key.
- **Logs:** Proxy/CDN có thể log full URL/query/cookie trước khi Spring thấy request; phải cấu hình masking ngay tại tầng đó.
- **Cache:** Tránh public CDN caching cho private download. Signed URLs là credentials, cần short TTL và log redaction.
- **Incident response:** Theo dõi số lượng rejected redirects/uploads, scan failures, anomalous rates và log injections, nhưng không lưu raw malicious payload hoặc secrets trong event feed.

### Có nên tự gọi URL mà client đưa để “xác nhận an toàn”?

Không nên. Nếu server tự thực hiện HTTP GET tới `next` để kiểm tra đích, có thể phát sinh **SSRF**: attacker trỏ tới internal addresses, cloud metadata hoặc DNS rebinding targets. Chống open redirect bằng validation/policy trên cấu trúc URL và mapping, **không fetch URL tùy ý**.

## 19. Security testing: thử cả đường hợp lệ và đường bị chặn

### Redirect

- `destination=dashboard` → 303, `Location` đúng local path.
- `destination=help` → 303, local path khác.
- URL tuyệt đối HTTP(S) → 400, không Location external.
- `//outside.example` → 400.
- URL chứa userinfo, backslash, percent-encoded slashes, CRLF → 400.
- Destination thiếu/unknown → 400 theo API contract.
- Nếu có external partner redirect thực, test exact origin/scheme/port/host/path, URL normalize và redirect chain.
- Kiểm tra status 302/303/307/308 theo body/method forwarding semantics.

### Upload

- PNG hợp lệ, kích thước trong giới hạn → 201, ID server sinh, download đúng PNG.
- JPEG/GIF/SVG/HTML/TXT ngụy trang bằng header `image/png` → reject nếu policy PNG-only.
- PNG file với filename `../secrets.txt` → không tạo path do user điều khiển; storage name generated.
- PNG decompression bomb (metadata dimensions lớn) → reject trước full decode khi có thể.
- File rỗng/too large, quá nhiều parts, tên quá dài, multipart boundary malformed → reject/bounded.
- Multipart POST từ user không có quyền → 401/403 khi Security bật.
- Biết UUID của người khác → 403/404 theo disclosure policy.
- Uploaded file chưa scan → không truy cập được bản public.
- Test storage permission, cleanup, quota/rate limit, scanning timeout và concurrent writes.

### Logging

- Đưa CRLF vào User-Agent/filename/query → không tạo log event giả.
- Đưa token/email/password vào body/query → không xuất hiện trong application logs, access logs và APM spans trừ policy được kiểm duyệt.
- Lỗi validation không log rejected sensitive values.
- Error stack trace không xuất hiện trong HTTP response.
- Tracing correlation ID luôn server-generated hoặc validated, không chứa ký tự bất thường.
- Chỉ role vận hành được phép đọc logs, retention và deletion policy được thi hành.

**Không chỉ test rằng API status là 400 hoặc 201**; phải kiểm tra chính các hiệu ứng phụ: header Location, file bytes, file placement và log records.

## 20. Lỗi thiết kế thường gặp

| Triệu chứng | Nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| Domain đáng tin redirect sang site lạ | Dùng `redirect:` + untrusted URL | Chọn route mã đích allowlist |
| App kiểm tra domain bằng `startsWith` nhưng bị bypass | Userinfo, subdomain suffix, IDN, encoded URL | Exact canonical URI policy hoặc partner ID |
| `setRedirectHosts` đã dùng nhưng vẫn redirect ngoài | Hiểu nhầm API encodeURL thành security validator | Validate business destination trước khi redirect |
| Avatar PNG thực ra là HTML | Tin `getContentType()` hoặc extension | Decoder/format validation, normalized output |
| Upload overwrite file server | Tin original filename/path | Random storage name, private directory |
| Ảnh nhỏ khiến OOM khi decode | Compressed dimensions cực lớn | Pixel limits, size limits, sandbox |
| User đoán UUID và tải tài liệu người khác | Chỉ kiểm tra file ID, không owner | Authorization/tenant checks |
| PDF/SVG tải lên gây script execution | Serve inline từ authenticated origin | Cấm/sanitize/CDR, attachment/isolated origin |
| Disk đầy sau thời gian | Không quota/cleanup/retention | Background lifecycle/metrics, storage policy |
| Log chứa JWT sau bật DEBUG | Raw request/headers logged | Redaction policy mọi tầng, tắt request detail |
| Log xuất hiện dòng ERROR giả | CRLF/log injection | Structured encoding, bounded allowlist fields |
| APM vẫn giữ query token dù app logger sạch | Instrumentation capture URL/query | Sanitize ở tracing/gateway config |
| Redirect sau POST bị mất/forward body | Nhầm 303 với 307/308 | Chọn status theo nghiệp vụ, cấm external target |
| Test MockMvc pass nhưng proxy vẫn nhận payload lớn | Chỉ test MVC layer | HTTP integration từ gateway/container |
| Download có `nosniff` nhưng vẫn lộ PII | Không xác thực hoặc public cache | Auth/authorization + private/no-store |
| Files được scan nhưng vẫn chứa mã độc | Scanner có giới hạn/signature thiếu | Defense in depth, sandbox/CDR, updates |

## 21. Khác biệt phiên bản Spring Boot / Spring Framework

| Nền tảng | Điều cần kiểm tra |
| --- | --- |
| Spring Framework 5.x / Boot 2.x | Servlet API thường `javax.servlet.*`; MultipartFile/RedirectView vẫn tồn tại; error response khác 6.x |
| Spring Framework 6.x / Boot 3.x | Java 17+, `jakarta.servlet.*`, `ProblemDetail`; properties `spring.servlet.multipart.*` và `spring.mvc.log-request-details` như ví dụ |
| Spring Framework 7.x / Boot 4.x | API web/security còn nhưng một số modules/starter/JSON/test packages đổi; kiểm tra migration guide, properties metadata và component registrations |

**Không bê code `javax.servlet.*` từ Boot 2 vào Boot 3/4**. ImageIO là Java SE và hoạt động với JDK17+, nhưng cần đảm bảo thư viện ảnh/JDK modules trong custom runtime. Khi dùng native image, phản xạ/codec/resource support phải được test riêng.

Spring Security 6/7 xử lý authentication, authorization, CSRF, headers và session theo SecurityFilterChain; các endpoint trong demo **không tự có chúng** chỉ vì dependency `spring-boot-starter-web`. Sử dụng Security starter + cấu hình policy riêng nếu muốn chạy production.

## 22. Checklist trước khi đưa ứng dụng web lên production

- [ ] Không có redirect target xây từ raw `next`/`returnUrl`. Internal routes dùng mã allowlist; external partners do config kiểm soát.
- [ ] Destination policy kiểm soát scheme/host/port/path/canonicalization nếu thực sự cần external redirect.
- [ ] OAuth/OIDC callback URI/state/PKCE được validate theo protocol, không nhầm với generic redirect.
- [ ] Upload type allowlist có business owner, giới hạn file/request size và max concurrency.
- [ ] Không tin `originalFilename` và `Content-Type`; xác minh bytes/parser, kiểm soát giải nén và re-encode/CDR khi phù hợp.
- [ ] Có AV/sandbox/quarantine policy cho loại dữ liệu cần thiết; không public trước scan completion.
- [ ] Storage ngoài public webroot, opaque generated key, không traversal/symlink/overwrite.
- [ ] Upload/download đều có authentication/authorization, quota, CSRF nếu cookie sessions.
- [ ] Download headers `Content-Type`/`Content-Disposition`/`nosniff`/cache được chọn bởi server.
- [ ] Không lưu raw password/token/cookie/signed URL/PII hay multipart body vào logs.
- [ ] Log events có allowlisted fields, escaping control chars, size limits, structured format.
- [ ] Gateway/container/HTTP client/APM/SIEM cũng có log redaction và retention policy.
- [ ] HTTP error body không chứa stack trace, filesystem paths, SQL hay secrets.
- [ ] Có tests cho malicious redirect, upload disguise/size/metadata, unauthorized download, log injection.
- [ ] Đã xác định lifecycle/cleanup/retention của uploaded objects và incident-response process.
- [ ] Có quan sát metrics bảo mật theo event count/status mà không thu dữ liệu nhạy cảm.

## 23. Bài tập và câu hỏi tự đánh giá

1. Vì sao `return "redirect:" + next` trong Spring MVC là open redirect nếu `next` đến từ request? `RedirectView#setHosts` có thay thế validator domain hay không?
2. Một URL `https://portal.example.attacker.example` qua được kiểm tra `startsWith("https://portal.example")`. Viết thiết kế lại bằng partner ID hoặc exact URI policy.
3. So sánh 302, 303, 307, 308 khi client gửi POST chứa body nhạy cảm. Khi nào 307/308 nguy hiểm nếu đích do attacker chọn?
4. Nếu file `avatar.png` chứa bytes HTML nhưng header `image/png`, điều gì sẽ xảy ra khi API chỉ kiểm tra extension/MIME? Bổ sung decoder validation.
5. Vì sao `Files.createTempFile(privateRoot,"image-",".png")` an toàn hơn `privateRoot.resolve(file.getOriginalFilename())`? Nó chưa giải quyết các rủi ro gì?
6. Một compressed PNG nhỏ chứa kích thước 80.000×80.000 pixel. Vì sao giới hạn upload 256 KiB chưa đủ? Cần giới hạn trước/bên trong decoder như thế nào?
7. Người dùng không biết UUID của người khác nhưng vẫn có thể đoán/brute force hoặc có link leak. Vì sao download vẫn cần authorization?
8. Ứng dụng cho upload SVG rồi serve inline cùng cookie-authenticated origin. Những rủi ro nào xuất hiện và nên đổi cách serve/allowlist ra sao?
9. Input User-Agent chứa ký tự CR/LF khiến log có dòng WARNING giả. Đề xuất structured logging, escaping, max length và kiểm thử.
10. Nếu có `spring.mvc.log-request-details=false` nhưng proxy ghi full query chứa token, vấn đề còn không? Ai chịu trách nhiệm redaction?
11. Thêm một test mock multipart với original filename `../../passwords.txt` và body PNG hợp lệ. Vì sao test không nên assert file được lưu dưới tên đó?
12. Thiết kế scan bất đồng bộ với `202 Accepted`, trạng thái `PENDING/SAFE/REJECTED` và quyền download; giải thích khi nào file được phép xuất bản.
13. Khi Spring Security dùng cookie authentication, CORS có ngăn cross-origin form POST gây CSRF không? Cần những lớp nào?
14. Nêu lý do không log raw `Exception#getMessage()` cho mọi lỗi parse multipart/body, và cách phân loại lỗi an toàn.
15. Viết checklist kiểm tra uploads và logs trên 3 môi trường: local Boot app, staging sau gateway và production dùng object storage.

## 24. Tài liệu tham khảo chính thức

- [OWASP — Unvalidated Redirects and Forwards Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Unvalidated_Redirects_and_Forwards_Cheat_Sheet.html): allowlist destinations, tránh raw return URL.
- [OWASP — File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html): extension/MIME validation, storage isolation, AV/CDR, permissions và CSRF.
- [OWASP — Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html): dữ liệu không nên ghi, log injection, event collection, integrity.
- [OWASP — Cross-Site Request Forgery Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html): CSRF khi dùng cookie sessions và state-changing requests.
- [Spring Framework 6.2 — RedirectView API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/view/RedirectView.html): redirect URLs, contextRelative và redirect hosts semantics.
- [Spring Framework 6.2 — Multipart Support](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/multipart.html): multipart parsing, MultipartFile và MVC Servlet.
- [Spring Boot 3.5 — Common Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html): `spring.servlet.multipart.max-file-size`, `max-request-size`, `spring.mvc.log-request-details`.
- [Spring Security — CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html): request protection, cookie/session flows.
- [Spring Framework 6.2 — ResponseEntity](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/ResponseEntity.html): explicit redirect/create status and headers.
- [Java 17 — ImageIO](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/javax/imageio/ImageIO.html): image format decoder/encoder primitives; security resource limits remain application responsibility.


</details>

- [Quay lại đầu trang](#back-to-top)
