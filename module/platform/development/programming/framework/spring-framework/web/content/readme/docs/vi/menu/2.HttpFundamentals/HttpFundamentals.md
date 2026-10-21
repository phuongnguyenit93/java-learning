<a id="back-to-top"></a>

# Nền tảng HTTP

## Menu
- [Chu trình yêu cầu–phản hồi và cấu trúc một thông điệp HTTP](#http-fundamentals-001)
- [URI, đường dẫn, tham số truy vấn và quy tắc mã hóa](#http-fundamentals-002)
- [HTTP method, tính an toàn và tính lặp lại không đổi kết quả](#http-fundamentals-003)
- [Mã trạng thái và ý nghĩa của các nhóm 2xx, 3xx, 4xx, 5xx](#http-fundamentals-004)
- [Header, Content-Type, Accept và thương lượng nội dung](#http-fundamentals-005)
- [Cookie, phiên làm việc và giới hạn của trạng thái qua HTTP](#http-fundamentals-006)
- [HTTPS, reverse proxy, chuyển hướng và địa chỉ công khai](#http-fundamentals-007)
- [Phân biệt REST, trang HTML, HTTP streaming và WebSocket](#http-fundamentals-008)

## <a id="http-fundamentals-001">Chu trình yêu cầu–phản hồi và cấu trúc một thông điệp HTTP</a>

<details>
<summary>Xem chi tiết</summary>

# Chu trình yêu cầu–phản hồi và cấu trúc một thông điệp HTTP

HTTP (Hypertext Transfer Protocol) là giao thức ứng dụng mà trình duyệt, ứng dụng di động, API client và máy chủ sử dụng để trao đổi yêu cầu và phản hồi. Trong lập trình Spring Web, hiểu HTTP là điều kiện để đọc đúng một request, chọn method, trả status code, xử lý header/body và xác định lỗi nằm ở client, mạng, bộ trung gian hay ứng dụng.

## 1. Mục tiêu học tập và mô hình tổng quan

Sau bài này, bạn cần có thể:
- Mô tả một vòng request–response từ lúc client gửi yêu cầu đến khi nhận được kết quả.
- Tách thông điệp HTTP/1.1 thành **start-line, headers, dòng trống phân cách và body (nếu có)**.
- Phân biệt request-target, method, status code, representation, media type, header và message body.
- Đọc một trao đổi HTTP thực tế, giải thích những gì Spring MVC nhận vào/trả ra.
- Hiểu ranh giới giữa cú pháp HTTP/1.1 dạng văn bản và cách mã hóa khác của HTTP/2, HTTP/3.

HTTP đi theo mô hình client khởi tạo yêu cầu; phía server gửi phản hồi tương ứng. Một kết nối có thể phục vụ nhiều trao đổi, nên **một request không đồng nghĩa một kết nối TCP mới**; HTTP/2 và HTTP/3 còn có thể xử lý nhiều stream đồng thời. HTTP thường được xem là **stateless về ngữ nghĩa**: mỗi request được xử lý theo thông tin đi kèm và trạng thái phía hệ thống, giao thức không tự buộc server nhớ một hội thoại ứng dụng giữa các lần gọi. Cookie, session hoặc token là cơ chế được ứng dụng dùng để duy trì ngữ cảnh.

Ví dụ một lần tra cứu sách:

~~~text
Client                       Server / Spring Web
  |   GET /books/42             |
  |----------------------------->|
  |                              | Đọc URI, method, headers
  |                              | Định tuyến và xử lý
  |   200 OK + JSON              |
  |<-----------------------------|
  | Hiển thị/sử dụng dữ liệu     |
~~~

Sơ đồ này mô tả trao đổi logic. Trên đường đi có thể có DNS, TLS, proxy, load balancer và servlet container; những tầng đó không phải các thành phần của một thông điệp HTTP.

## 2. Chu trình request–response từng bước

1. **Client xây request:** chọn method (ví dụ `GET`), đích (URI), query, các header cần thiết và body khi thích hợp. Chẳng hạn client có thể gửi `Accept: application/json` để yêu cầu một dạng biểu diễn JSON mà server hỗ trợ.
2. **Thiết lập kênh truyền:** client xác định endpoint, thường qua DNS nếu dùng hostname. Với HTTP/1.1 hoặc HTTP/2 thông dụng, kết nối dùng TCP; HTTPS còn có TLS. HTTP/3 truyền qua QUIC. Kết nối có thể được tái sử dụng.
3. **Truyền request:** client gửi thông tin theo phiên bản giao thức đã đàm phán. Một reverse proxy có thể nhận request rồi chuyển tiếp tới server ứng dụng.
4. **Server tiếp nhận và xử lý:** web server/servlet container nhận request; Spring MVC có thể chạy Filter, tìm controller phù hợp, chuyển đổi dữ liệu, gọi logic nghiệp vụ và tạo phản hồi.
5. **Server gửi response:** status code cho biết kết quả ở mức HTTP, headers cung cấp metadata và body chứa dữ liệu nếu loại response cho phép.
6. **Client diễn giải response:** xem status, headers, parse body theo media type, quyết định hiển thị dữ liệu, cache, điều hướng, đăng nhập lại hay báo lỗi.

Luồng request–response **không bảo đảm** request luôn tới ứng dụng hoặc response luôn thành công: DNS, TLS, timeout, proxy hay connection reset có thể làm trao đổi thất bại trước khi nhận được một HTTP status code.

## 3. Cấu trúc thông điệp HTTP/1.1

Khi quan sát cú pháp HTTP/1.1 truyền thống, một thông điệp có dạng:

~~~text
START-LINE\r\n
Header-Name: value\r\n
Another-Header: value\r\n
\r\n
[message body, nếu có]
~~~

Trong mẫu trên, ký hiệu `\r\n` biểu thị hai byte **CRLF** kết thúc mỗi dòng. Hai CRLF liên tiếp tạo dòng trống ngăn phần header và body. Dấu ngoặc vuông ở dòng body chỉ để diễn giải, không phải byte bắt buộc trong thông điệp. Header và body không bị giới hạn là dữ liệu văn bản thuần; body có thể là JSON, form, ảnh hoặc dữ liệu nhị phân. Header rỗng nghĩa là sau start-line có ngay một CRLF kết thúc vùng header (dù request HTTP/1.1 hợp lệ thông thường cần thông tin Host).

**Cảnh báo về phiên bản:** đây là cách biểu diễn *HTTP/1.x*. HTTP/2 và HTTP/3 giữ phần lớn ngữ nghĩa method, status, headers và body, nhưng dùng frame/binary encoding thay cho request/status start-line dạng text trên dây. Vì vậy không được khẳng định “mọi gói HTTP đều chứa dòng `GET /... HTTP/1.1`”. Trong HTTP/2/3, thông tin tương đương được biểu diễn qua pseudo-header như `:method`, `:path`, `:scheme`, `:authority`, và phản hồi qua `:status`; body nằm trong các data frame.

### 3.1. Request start-line

Một HTTP/1.1 request-line có hình thức:

~~~text
METHOD SP request-target SP HTTP-version CRLF
~~~

Ví dụ:

~~~http
GET /books/42?view=summary HTTP/1.1
Host: api.example.test
Accept: application/json

~~~

- `GET` là **method**, diễn tả ý định lấy biểu diễn của tài nguyên.
- `/books/42?view=summary` là **request-target** ở dạng origin-form: path `/books/42` cộng query string `view=summary`. Fragment như `#intro` không được gửi trong request-target HTTP.
- `HTTP/1.1` là phiên bản trong request-line.
- `Host: api.example.test` xác định authority/virtual host cho request HTTP/1.1. Host header bắt buộc trong HTTP/1.1 và phải phù hợp authority của target URI.
- `Accept: application/json` cho biết client ưu tiên nhận nội dung theo media type JSON.

Request-target có các dạng khác cho một số tình huống, như absolute-form khi gửi qua forward proxy hoặc authority-form cho `CONNECT`. Đa số HTTP API thông thường dùng origin-form như ví dụ.

Phân biệt ba phần dễ nhầm:
- **Path** `/books/42` giúp định tuyến tới tài nguyên hoặc endpoint.
- **Query** `?view=summary` truyền tham số lựa chọn/bộ lọc; nó là một phần của URI, không phải body.
- **Body** là dữ liệu nằm sau dòng trống kết thúc headers; ví dụ JSON dùng để tạo đơn hàng.

### 3.2. Response status-line

HTTP/1.1 response bắt đầu bằng status-line:

~~~text
HTTP-version SP status-code SP [reason-phrase] CRLF
~~~

Ví dụ, một phản hồi minh họa:

~~~http
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 15

{"status":"ok"}
~~~

- `HTTP/1.1` là phiên bản phản hồi.
- `200` là **status code** máy có thể diễn giải; `OK` là reason phrase để con người đọc, không nên dùng reason phrase làm cơ sở xử lý nghiệp vụ.
- `Content-Type: application/json` mô tả media type của representation trong body.
- `Content-Length: 15` cho biết body dài **15 byte**, không phải 15 chữ cái tiếng Việt hay số ký tự của JSON nói chung. Body ví dụ toàn ASCII nên 15 byte.
- Dòng trống đánh dấu kết thúc headers; `{"status":"ok"}` là body.

HTTP/2/3 không dùng reason phrase kiểu HTTP/1.1. API client nên dựa vào status code và nội dung/thỏa thuận ứng dụng, không cố khớp chuỗi `OK` hoặc `Not Found`.

### 3.3. Headers

Header gồm tên trường và giá trị, ví dụ `Content-Type: application/json`. Tên header **không phân biệt chữ hoa/chữ thường** ở mức ngữ nghĩa; `content-type` và `Content-Type` chỉ cùng một trường. HTTP/2 yêu cầu tên field được mã hóa chữ thường trong header block. Giá trị có cú pháp/ý nghĩa riêng theo từng trường, không nên tự động gộp mọi header trùng tên.

| Header | Vai trò thông dụng |
| --- | --- |
| `Host` | Chỉ định host/authority trong request HTTP/1.1. |
| `Accept` | Kiểu nội dung phản hồi client có thể tiếp nhận. |
| `Content-Type` | Kiểu media của phần nội dung đi kèm (request hoặc response). |
| `Content-Length` | Độ dài phần nội dung theo **byte**, trong các trường hợp được phép dùng. |
| `Authorization` | Thông tin chứng thực, ví dụ `Bearer` token. |
| `Cache-Control` | Chỉ dẫn hành vi cache. |
| `Location` | URI được thông báo, thường dùng khi tạo tài nguyên hoặc redirect. |
| `Set-Cookie` / `Cookie` | Server đặt cookie; client gửi cookie ở những request sau khi phù hợp. |

**`Accept` không đồng nghĩa `Content-Type`:** request có `Content-Type: application/json` nghĩa là body request là JSON; `Accept: application/json` nghĩa là client muốn response JSON. Cả hai có thể xuất hiện cùng lúc và mang vai trò khác nhau.

Header có thể tác động mạnh đến hành vi. Không ghi lộ giá trị `Authorization`, cookie phiên hay dữ liệu cá nhân vào log. Không ghép trực tiếp dữ liệu người dùng có chứa CR/LF vào header vì có thể tạo lỗ hổng HTTP response splitting.

### 3.4. Message body và cách xác định ranh giới

Body không phải lúc nào cũng có:
- `GET` thông thường chỉ lấy dữ liệu và không gửi body; không nên thiết kế API dựa vào body của GET vì ngữ nghĩa chung chưa được định nghĩa và các bên trung gian có thể không hỗ trợ.
- `POST` hoặc `PUT` thường gửi JSON/form/file trong body, nhưng method không tự buộc body phải có mặt.
- Response `204 No Content` và `304 Not Modified` không được chứa message content; response cho `HEAD` cũng không mang content body dù một số header mô tả response tương ứng của GET.
- Một lỗi mạng có thể khiến body nhận chưa đủ dù đã có status-line và headers.

**Phân định body HTTP/1.1** phải theo quy tắc framing của giao thức, không đoán bằng “đọc cho tới dòng trống thứ hai”. Tùy trường hợp, có `Content-Length`, `Transfer-Encoding: chunked`, hoặc cơ chế kết thúc kết nối; một số loại message không được phép có body. `chunked` là cơ chế mã hóa truyền trên HTTP/1.1, không phải một kiểu JSON hay một kiểu body ứng dụng. Không dùng đồng thời `Content-Length` và `Transfer-Encoding` để tự xây message: sự mơ hồ framing có nguy cơ gây request smuggling. Để HTTP library/server xử lý framing.

Với UTF-8, số byte có thể lớn hơn số ký tự: `"Lan"` trong JSON ASCII có số byte bằng số ký tự, nhưng `"Chào"` chứa ký tự đa byte. Nếu tự thao tác raw socket, độ dài phải tính trên byte đã mã hóa; trong Spring MVC bình thường, framework/web server xử lý phần này.

## 4. Ví dụ hoàn chỉnh: yêu cầu tạo tài nguyên

Đây là *trao đổi HTTP/1.1 minh họa*, giả định cả hai bên gửi đúng các header và body thể hiện bên dưới.

Request:

~~~http
POST /api/orders HTTP/1.1
Host: api.example.test
Content-Type: application/json
Accept: application/json
Content-Length: 14

{"name":"Lan"}
~~~

Response:

~~~http
HTTP/1.1 201 Created
Location: /api/orders/42
Content-Type: application/json
Content-Length: 15

{"status":"ok"}
~~~

Diễn giải lần lượt:
1. `POST /api/orders` yêu cầu server xử lý dữ liệu và tạo một đơn hàng theo thiết kế API.
2. `Content-Type` nói với server cách parse body `{"name":"Lan"}`. Nội dung mẫu có 14 byte ASCII.
3. Server hoàn thành việc tạo tài nguyên và trả `201 Created`; đây là status phù hợp cho trường hợp tạo mới thành công.
4. `Location: /api/orders/42` cho client biết URI của tài nguyên vừa tạo (ở đây là URI tương đối).
5. Body response `{"status":"ok"}` dài 15 byte và có media type JSON.

Các chuỗi trên là **mẫu trao đổi cố định để đọc giao thức**, không khẳng định Spring tự tạo đúng JSON/URI/header này. Trong dự án thật, status, serialization, header và chính sách xử lý lỗi phụ thuộc controller, framework, cấu hình server và proxy.

## 5. Method, status code và ngữ nghĩa của phản hồi

Phân biệt **cú pháp message** với **ngữ nghĩa method/status**. Hai request có cấu trúc giống nhau nhưng method khác sẽ mang ý định khác.

| Method | Ý nghĩa thường gặp | Đặc điểm ngữ nghĩa |
| --- | --- | --- |
| `GET` | Lấy representation | Safe và idempotent theo định nghĩa HTTP; không nên gây thay đổi trạng thái do client yêu cầu. |
| `HEAD` | Lấy metadata tương ứng GET, không lấy body response | Safe, idempotent. |
| `POST` | Gửi dữ liệu để tài nguyên đích xử lý | Thông thường không idempotent. |
| `PUT` | Tạo/thay thế trạng thái tài nguyên tại URI đích theo representation gửi lên | Idempotent theo ngữ nghĩa chuẩn. |
| `PATCH` | Áp dụng thay đổi từng phần | Không mặc nhiên idempotent; phụ thuộc patch cụ thể. |
| `DELETE` | Yêu cầu xóa liên kết/trạng thái tài nguyên tại đích | Idempotent theo ngữ nghĩa HTTP, dù phản hồi những lần gọi có thể khác. |
| `OPTIONS` | Hỏi các tùy chọn giao tiếp | Safe, idempotent. |

**Safe** nói về ngữ nghĩa không yêu cầu thay đổi trạng thái có chủ đích (logging/metrics phụ trợ vẫn có thể xảy ra). **Idempotent** nghĩa là tác động *dự kiến trên server* của một hay nhiều request giống nhau là như nhau; không có nghĩa response byte-for-byte giống nhau hoặc mạng bảo đảm không gửi trùng.

Các nhóm status:
- **1xx – Informational:** phản hồi trung gian, ví dụ `100 Continue`.
- **2xx – Success:** `200 OK` có nội dung thành công tùy method; `201 Created` khi tạo; `204 No Content` khi thành công và không có content.
- **3xx – Redirection:** client cần dùng thông tin điều hướng/cache, ví dụ `301`, `302`, `304`; 304 phục vụ tái sử dụng representation đã cache, không phải body mới.
- **4xx – Client error:** `400 Bad Request` cho request không hợp lệ; `401 Unauthorized` liên quan yêu cầu xác thực; `403 Forbidden` bị từ chối truy cập; `404 Not Found` không tìm thấy representation/tài nguyên theo ngữ cảnh.
- **5xx – Server error:** `500 Internal Server Error` cho lỗi xử lý phía server; `502 Bad Gateway` thường liên quan gateway/proxy nhận upstream không hợp lệ; `503 Service Unavailable` báo tạm không sẵn sàng.

Một status 2xx chỉ xác nhận cách xử lý **theo giao thức và hợp đồng API**. Ví dụ endpoint có thể trả 200 nhưng JSON `{"success":false}`; đó là quyết định thiết kế ứng dụng, không khiến 200 tự động có nghĩa “mọi nghiệp vụ đã thành công”. Hãy xem đồng thời HTTP status và schema phản hồi của API.

## 6. HTTP đi vào Spring Web như thế nào?

Ví dụ bên dưới là **một controller Spring MVC minh họa**, sử dụng Java 17+, Spring Framework 6/Spring Boot 3, module `spring-webmvc` và JSON converter như Jackson. Đặt vào tệp `GreetingController.java` bên trong source set của ứng dụng Spring có cấu hình tự động quét component. Đoạn này *không phải chương trình Java độc lập để chạy bằng lệnh `javac` thuần*, vì cần dependency và ứng dụng web đang hoạt động.

~~~java
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/greetings")
public class GreetingController {

    @GetMapping("/{name}")
    public ResponseEntity<GreetingResponse> greet(@PathVariable String name) {
        return ResponseEntity.ok(new GreetingResponse("Xin chào, " + name + "!"));
    }

    public record GreetingResponse(String message) {}
}
~~~

Khi ứng dụng Spring MVC chạy trên cổng 8080, thử:

~~~bash
curl --http1.1 -i -H "Accept: application/json" http://localhost:8080/api/greetings/Lan
~~~

Trong PowerShell, nếu `curl` là alias của `Invoke-WebRequest`, hãy gọi `curl.exe` để dùng cú pháp curl CLI.

Ví dụ phần body JSON kỳ vọng (format header và cách hiện ký tự Unicode có thể khác theo môi trường):

~~~json
{"message":"Xin chào, Lan!"}
~~~

Giải thích đường đi trong Spring MVC:

1. Client gửi `GET /api/greetings/Lan`. Servlet container nhận request HTTP và tạo thông tin request/response cho tầng servlet.
2. Các `Filter` được cấu hình có thể kiểm tra request hoặc response (ví dụ logging, bảo mật), rồi luồng xử lý đi đến `DispatcherServlet`.
3. `DispatcherServlet` nhờ `HandlerMapping` tìm handler phù hợp với `GET` và path; `HandlerAdapter` thực thi phương thức controller.
4. `@PathVariable` ràng buộc đoạn `Lan` của path vào biến Java `name`. `ResponseEntity.ok(...)` đặt HTTP status là 200.
5. Vì controller là `@RestController`, giá trị `GreetingResponse` được xử lý như response body; `HttpMessageConverter` phù hợp (thường là Jackson JSON converter) serialize record thành JSON.
6. Servlet container gửi status, headers và body tới client. Spring/server có thể bổ sung header khác, dùng chunked transfer hoặc một cơ chế framing hợp lệ; không tự suy luận `Content-Length` nếu response thực tế không có.

Ở chiều **request body**, một API kiểu `@PostMapping` với `@RequestBody` thường dùng converter để deserialize JSON request vào đối tượng Java. Nếu media type không được hỗ trợ, request sai cấu trúc hoặc validation thất bại, Spring có thể trả lỗi 4xx theo cấu hình xử lý lỗi. Một JSON body hợp lệ không đảm bảo nghiệp vụ hợp lệ.

Đây là pipeline **Spring MVC (Servlet)**. Với Spring WebFlux, lớp điều phối và mô hình xử lý là reactive (ví dụ `DispatcherHandler`); không nên áp nguyên sơ đồ `DispatcherServlet` cho WebFlux.

## 7. Tự quan sát request và response khi gỡ lỗi

Dùng `curl -i` để xem **status-line và response headers cùng body** ở đầu ra. Thêm `-v` để xem thêm thông tin kết nối và request/response từ phía client; `--http1.1` ép HTTP/1.1 cho thí nghiệm về cú pháp. Trong DevTools của trình duyệt, tab Network giúp xem URL, method, status, headers, payload, timing; công cụ có thể hiển thị dữ liệu theo giao diện đã giải mã chứ không phải raw bytes thực sự truyền trên mạng.

Thứ tự kiểm tra khi lỗi:
1. **Không có status code?** Kiểm tra DNS, kết nối, TLS, timeout, proxy và server availability trước khi đổ lỗi cho controller.
2. **Có 4xx?** Kiểm tra method, path/query, `Content-Type`, `Accept`, xác thực, phân quyền, JSON body, giới hạn kích thước và hợp đồng API.
3. **Có 5xx?** Đối chiếu log/tracing phía server và upstream; tránh tiết lộ stack trace hay credential ra response công khai.
4. **Status đúng nhưng client parse lỗi?** Kiểm tra `Content-Type`, charset/encoding, body rỗng (đặc biệt 204/HEAD), dạng JSON và dữ liệu thực nhận.
5. **Chỉ hỏng qua proxy?** Kiểm tra rewrite path/host, forwarding headers, kích thước header/body, timeout và sự nhất quán HTTP framing.

HTTP status thuộc response thực tế của một request. **Timeout không phải HTTP 408 tự động**: 408 là một status server có thể trả trong điều kiện nhất định; nếu mất kết nối hoặc hết thời gian ở client mà không có response thì client chỉ thấy lỗi truyền thông.

## 8. Những nhầm lẫn cần tránh

| Nhầm lẫn | Cách hiểu chính xác |
| --- | --- |
| “HTTP chỉ là JSON API.” | HTTP mang nhiều media type; body có thể vắng mặt hoặc là dữ liệu nhị phân. |
| “Request nào cũng có body.” | Không; sự hiện diện và ngữ nghĩa body phụ thuộc loại message và method/status. |
| “Query string chính là body.” | Query thuộc request-target/URI; body nằm sau headers. |
| “Content-Length đếm ký tự.” | Đếm byte; UTF-8 có ký tự nhiều byte. |
| “Có HTTPS nghĩa là method/header/body khác đi.” | HTTPS bảo vệ HTTP khi truyền; ngữ nghĩa HTTP vẫn tương ứng, dù tầng transport/phiên bản có thể khác. |
| “HTTP/2 trên dây cũng có status-line 200 OK.” | HTTP/2 dùng frames và `:status`; start-line là cú pháp HTTP/1.x. |
| “200 luôn nghĩa là nghiệp vụ hoàn tất.” | Cần hiểu cả hợp đồng API và body của phản hồi. |
| “PUT/DELETE idempotent nghĩa là không có tác dụng phụ.” | Idempotent nói về hiệu ứng dự kiến khi lặp lại; logging, audit hoặc response vẫn có thể thay đổi. |
| “Backend luôn nhận đúng request-line client gửi.” | Proxy có thể chỉnh path, host, forwarding headers và phiên bản giao thức. |

## 9. Tự kiểm tra kiến thức

**Bài 1.** Xét request: `GET /products?sort=price HTTP/1.1`, có `Host: shop.example.test` và `Accept: application/json`. Hãy xác định method, path, query, hai header và body có bắt buộc không.

**Bài 2.** Client gửi JSON UTF-8 có dấu tiếng Việt nhưng tự lấy `String.length()` làm `Content-Length`. Rủi ro là gì?

**Bài 3.** Server trả `HTTP/1.1 204 No Content`. Client có nên cố parse JSON từ body không?

**Bài 4.** Controller `@GetMapping("/api/greetings/{name}")` trả `ResponseEntity.ok(...)`. Những phần nào quyết định path mapping, status và JSON serialization?

**Đáp án và giải thích**

1. Method là `GET`; path là `/products`; query là `sort=price`; headers là `Host` và `Accept`; GET không bắt buộc có body và API không nên phụ thuộc GET body.
2. `String.length()` trong Java đếm UTF-16 code units; `Content-Length` đếm bytes trong nội dung HTTP/1.1. Với UTF-8, số này có thể lệch, làm sai framing và dẫn đến lỗi truyền thông/bảo mật; để HTTP library tính theo bytes.
3. Không. Response 204 không có content; client cần xử lý body rỗng theo status.
4. `@GetMapping`/`HandlerMapping` quyết định route và method; `ResponseEntity.ok` đặt status 200; `@RestController` cùng `HttpMessageConverter` thích hợp quyết định serialize response body sang JSON.

## 10. Ghi nhớ khi xây API Spring Web

Đầu tiên xác định **resource và hành vi HTTP** (URI/method/status). Sau đó đặt đúng dữ liệu vào **path, query, headers hay body**, công bố định dạng qua `Content-Type` và xử lý thương lượng nội dung khi cần. Khi trả response, đảm bảo status phản ánh kết quả xử lý, body tuân theo status, và giao thức/header framing được thư viện HTTP đảm nhiệm. Nhờ mô hình này, bạn có thể giải thích một request và response cả ở mức raw HTTP/1.1 lẫn ở controller Spring MVC mà không nhầm lẫn vai trò của từng tầng.

**Tài liệu chuẩn để đối chiếu sâu:** RFC 9110 (HTTP Semantics), RFC 9112 (HTTP/1.1 message syntax và framing), RFC 9113 (HTTP/2) và RFC 9114 (HTTP/3).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-fundamentals-002">URI, đường dẫn, tham số truy vấn và quy tắc mã hóa</a>

<details>
<summary>Xem chi tiết</summary>

# URI, đường dẫn, tham số truy vấn và quy tắc mã hóa trong Spring Web

## Mục tiêu học tập

Sau bài này, bạn có thể:

- Phân biệt URI, URL, đường dẫn (path), tham số truy vấn (query parameter) và fragment.
- Đọc, thiết kế và ghép URI đúng ranh giới giữa các thành phần.
- Giải thích percent-encoding theo UTF-8, sự khác nhau giữa `%20` và `+`, cũng như ký tự có ý nghĩa cú pháp.
- Nhận dữ liệu path/query bằng Spring MVC và tạo URI an toàn bằng `UriComponentsBuilder`.
- Nhận diện lỗi mã hóa hai lần, giải mã quá sớm, dữ liệu nhập không hợp lệ và sự khác biệt giữa proxy, servlet container, Spring MVC.

## 1. URI là gì? Những thành phần nào tạo nên một địa chỉ web?

**URI (Uniform Resource Identifier)** là chuỗi dùng để định danh một tài nguyên theo một scheme. **URL (Uniform Resource Locator)** là cách gọi thường dùng cho URI cung cấp vị trí/cách truy cập, ví dụ `https://...`. Không phải URI nào cũng là địa chỉ HTTP: `urn:isbn:...` là ví dụ dạng URN.

Cú pháp tổng quát của URI phân cấp theo RFC 3986:

```text
scheme ":" hier-part [ "?" query ] [ "#" fragment ]

https://api.example.com:8443/products/42?q=c%C3%A0%20ph%C3%AA&tag=java#reviews
└─┬─┘   └────────┬────────┘ └─────┬────┘ └───────────┬───────────┘ └──┬──┘
scheme       authority           path                query           fragment
              host + port
```

| Thành phần | Ví dụ | Ý nghĩa |
|---|---|---|
| Scheme | `https` | Quy ước truy cập/định danh; scheme HTTP(S) quyết định ngữ cảnh trao đổi web. |
| Authority | `api.example.com:8443` | Phần quyền hạn; với URL HTTP thường chứa host và port tùy chọn. Có thể có userinfo theo cú pháp URI nhưng không nên đưa thông tin đăng nhập vào URL. |
| Path | `/products/42` | Đường dẫn phân cấp, thường ánh xạ đến tài nguyên hoặc route. |
| Query | `q=c%C3%A0%20ph%C3%AA&tag=java` | Phần sau dấu `?`, dùng truyền tiêu chí/tuỳ chọn theo quy ước ứng dụng. |
| Fragment | `reviews` | Phần sau `#`, chỉ vị trí/tham chiếu bên trong representation phía client. Fragment **không được gửi** trong HTTP request-target tới server. |

Lưu ý: `?` mở đầu query và `#` mở đầu fragment là dấu phân tách URI; `&` và `=` thường phân tách cặp key/value trong *cách diễn giải query của ứng dụng*, không phải cấu trúc bắt buộc cho mọi URI theo RFC 3986. Ví dụ `?filter=status:open` hoàn toàn có thể là một query hợp lệ.

### URI tuyệt đối và tham chiếu tương đối

- URI tuyệt đối: `https://example.com/docs/setup?lang=vi`, có scheme.
- Tham chiếu tương đối: `../setup?lang=vi` hoặc `/docs/setup`, cần base URI để xác định đích.
- Path bắt đầu bằng `/` thường là đường dẫn tuyệt đối trong authority hiện tại; path không bắt đầu bằng `/` thường tương đối với thư mục của URI cơ sở.
- `.` và `..` là dot-segment trong path; chuẩn có quy tắc chuẩn hóa. Đừng tự loại bỏ hoặc giải mã chúng tùy tiện khi xử lý dữ liệu URL chưa tin cậy.

Ví dụ: từ base `https://example.com/docs/java/intro`, tham chiếu `../spring?lang=vi` có thể resolve thành `https://example.com/docs/spring?lang=vi`. Cần phân biệt **resolve relative URI** với thao tác cộng chuỗi; phép cộng chuỗi dễ làm sai dấu `/`, `?` và `#`.

## 2. Path: cách định vị và ánh xạ endpoint

Path là một chuỗi các *segment* được phân tách bằng `/`. Với `/api/v1/products/42`, các segment chính là `api`, `v1`, `products`, `42`. Slash làm dấu phân cấp; chuỗi `42` có thể là route variable, trong khi `products` là segment tĩnh.

```text
GET /api/v1/products/42 HTTP/1.1
Host: shop.example.com
```

Trong Spring MVC, route thường được mô tả bằng `@RequestMapping`, `@GetMapping`, `@PostMapping`,… và các placeholder trong path.

```java
@RestController
@RequestMapping("/api/v1/products")
class ProductController {

    @GetMapping("/{id}")
    Map<String, Object> getOne(@PathVariable long id) {
        return Map.of("id", id, "status", "ok");
    }
}
```

Đây là **đoạn mã trong ứng dụng Spring Web có dependency Spring MVC**, không phải file Java độc lập. Khi client gọi `GET /api/v1/products/42`, Spring ánh xạ `id = 42` (kiểu `long`) và có thể trả JSON chứa `"id": 42`, `"status": "ok"` nếu ứng dụng có message converter JSON. Thứ tự thuộc tính JSON không phải một phần hợp đồng của ví dụ.

Những điểm cần phân biệt:

1. `/products` và `/products/` là hai path khác nhau về mặt cú pháp; redirect/route matching với trailing slash phụ thuộc cấu hình framework và server, không nên mặc định chúng tương đương.
2. Chữ hoa/chữ thường, separator và cách xử lý dấu `;` có thể ảnh hưởng route. Thiết kế endpoint nhất quán, xác định rõ quy ước.
3. `@PathVariable` dành cho giá trị nằm **trong path template**; `@RequestParam` dành cho tham số được Spring trích từ query (và có thể cả dữ liệu form tùy Servlet API).
4. Path segment chứa dấu `/` như dữ liệu, chẳng hạn mã sản phẩm `cup/blue`, là trường hợp khó: nếu mã hóa thành `cup%2Fblue` thì reverse proxy hoặc servlet container có thể từ chối/giải mã trước khi Spring route. Không giả định mã hóa slash sẽ hoạt động giống nhau trên mọi tầng. Cân nhắc ID không chứa slash hoặc truyền chuỗi đặc biệt qua query/body.
5. Path là một phần URI, không tự động là đường dẫn file trong hệ điều hành. Nếu sau đó map sang filesystem, phải kiểm tra việc giải mã, dot-segment và chặn path traversal.

### Thiết kế path và query theo vai trò

| Nhu cầu | Ví dụ | Lý do |
|---|---|---|
| Lấy một tài nguyên theo ID | `GET /products/42` | ID là thành phần định danh tài nguyên. |
| Lọc danh sách | `GET /products?category=book` | Tiêu chí truy vấn quyết định tập kết quả. |
| Phân trang | `GET /products?page=2&size=20` | Tùy chọn xem danh sách. |
| Tìm kiếm | `GET /search?q=spring%20mvc` | Chuỗi tìm kiếm là giá trị query. |
| Chọn tài nguyên con | `GET /products/42/reviews/9` | Cấu trúc lồng nhau thể hiện quan hệ tài nguyên. |

Đây là quy ước REST thường dùng, không phải quy tắc bắt buộc của chuẩn URI. Các tham số query **có thể làm thay đổi tài nguyên được truy xuất hoặc cache key**; không nên coi query chỉ là dữ liệu trang trí.

## 3. Query string: tên, giá trị và tham số lặp

Xem ví dụ:

```text
GET /products?q=c%C3%A0%20ph%C3%AA&tag=java&tag=web&sort=price%3Aasc&page=2 HTTP/1.1
```

Theo cách phân tích phổ biến cho ứng dụng web:

- `q` có giá trị giải mã `cà phê`.
- `tag` xuất hiện **hai lần**: `java` và `web`. Server có thể gom chúng thành danh sách, chọn một, hoặc giữ nhiều giá trị; định nghĩa hành vi trong API contract.
- `sort` có giá trị `price:asc`; ký tự `:` được mã hóa ở vị trí giá trị như `%3A`.
- `page` là văn bản `"2"` trên wire; framework có thể chuyển sang số nguyên nếu controller khai báo kiểu `int`.
- `?flag`, `?flag=` và không có `flag` là những hình thức có thể khác nhau về ngữ nghĩa; ứng dụng cần quyết định rõ.
- Thứ tự query param không nên có ý nghĩa trừ khi giao thức/API định nghĩa; thứ tự của **các giá trị lặp cùng key** có thể quan trọng đối với một số API.

Spring MVC ví dụ đọc path và query cùng lúc:

```java
@RestController
@RequestMapping("/api")
class SearchController {

    @GetMapping("/products/{category}")
    Map<String, Object> search(
            @PathVariable String category,
            @RequestParam(name = "q", defaultValue = "") String keyword,
            @RequestParam(name = "tag", required = false) List<String> tags,
            @RequestParam(defaultValue = "1") int page) {

        return Map.of(
                "category", category,
                "keyword", keyword,
                "tags", tags == null ? List.of() : tags,
                "page", page);
    }
}
```

Với `GET /api/products/books?q=c%C3%A0%20ph%C3%AA&tag=java&tag=web&page=2`, Spring có thể bind `category = "books"`, `keyword = "cà phê"`, `tags = ["java", "web"]`, `page = 2`. Ví dụ là đoạn controller dùng trong Spring MVC; cần import các annotation/class tương ứng. Binding kiểu số sai hoặc thiếu giá trị bắt buộc thường dẫn đến lỗi phía client (ví dụ HTTP 400, tùy cấu hình exception handling).

Dữ liệu query có thể xuất hiện trong lịch sử trình duyệt, log của server, proxy hoặc công cụ analytics. **Không đặt mật khẩu, access token hay dữ liệu nhạy cảm vào query string** chỉ vì kết nối đang dùng HTTPS.

## 4. Percent-encoding: mã hóa byte bên trong từng thành phần

URI theo RFC 3986 dùng một tập ký tự cho cú pháp. Khi cần biểu diễn byte không phù hợp hoặc một delimiter đóng vai trò *dữ liệu*, dùng percent-encoding: `%` theo sau **hai chữ số hệ 16**. Ví dụ dấu cách có byte ASCII `0x20` nên là `%20`.

### Nhóm ký tự quan trọng

- **Unreserved**: `A-Z a-z 0-9 - . _ ~`. Thông thường giữ nguyên.
- **Reserved**: `:/?#[]@!$&'()*+,;=`. Một ký tự có thể là dấu cú pháp ở thành phần này nhưng được phép là dữ liệu ở thành phần khác.
- **Non-ASCII**: với nội dung Unicode trên web, thường chuyển chuỗi sang byte **UTF-8**, rồi percent-encode những byte cần mã hóa. Ví dụ `à` là byte UTF-8 `C3 A0` nên `%C3%A0`.
- **Dấu `%` là dữ liệu thật**: phải biểu diễn bằng `%25`; chuỗi `%20` là ba ký tự mã hóa cho byte space, không phải nội dung giống chữ `%20` ban đầu.

| Dữ liệu muốn biểu diễn | Biểu diễn thường dùng khi là dữ liệu trong component | Vì sao |
|---|---|---|
| Dấu cách | `%20` | Dấu cách không thuộc tập ký tự URI cho phép. |
| Dấu cộng `+` | `%2B` | Tránh nhầm với quy ước form, nơi `+` biểu diễn space. |
| Dấu `/` trong **một segment** | `%2F` | Không muốn slash trở thành ký tự tách segment. |
| Dấu `?` trong giá trị | `%3F` | Không muốn tạo dấu mở query ngoài ý muốn. |
| Dấu `#` trong giá trị | `%23` | Không muốn tạo fragment ngoài ý muốn. |
| Dấu `&` trong giá trị query kiểu key/value | `%26` | Không muốn chia thêm tham số. |
| Dấu `=` trong giá trị query kiểu key/value | `%3D` | Không muốn bị hiểu là ký tự phân tách key/value. |
| Dấu `%` | `%25` | Không tạo escape sequence sai. |
| Chuỗi `cà phê` trong query | `c%C3%A0%20ph%C3%AA` | Mã hóa hai ký tự có dấu theo UTF-8 và dấu cách. |

**Quy tắc quan trọng: chia thành phần trước, mã hóa dữ liệu của từng thành phần sau.** Không mã hóa nguyên URI bằng một hàm dành cho một chuỗi dữ liệu; làm vậy sẽ biến cả `:`, `/`, `?`, `&` cấu trúc thành dữ liệu. Ngược lại, đừng ghép dữ liệu chưa mã hóa bằng `+` với query hoặc path rồi hy vọng server hiểu đúng.

### Ví dụ từng bước với UTF-8

Chuỗi tìm kiếm `cà phê & trà` được chuyển sang byte UTF-8. Khi là **một giá trị query**, dạng percent-encoding được chọn ở đây là:

```text
Dữ liệu:      cà phê & trà
Query value:  c%C3%A0%20ph%C3%AA%20%26%20tr%C3%A0
URI:          /search?q=c%C3%A0%20ph%C3%AA%20%26%20tr%C3%A0
Server nhận:  q = "cà phê & trà"
```

Nếu ghi thẳng `/search?q=cà phê & trà` thì dấu `&` có nguy cơ bị tách thành một query param mới trong parser key/value. Dấu cách và ký tự Unicode chưa được biểu diễn theo URI ASCII chuẩn; browser có thể tự sửa nhưng hành vi của các thư viện không nhất thiết giống nhau.

### Vì sao đôi lúc space là `+` chứ không phải `%20`?

`application/x-www-form-urlencoded` là **quy ước mã hóa dữ liệu biểu mẫu**: dấu cách biến thành `+`; dấu `+` thật phải thành `%2B`. Trong quy ước này:

```text
Dữ liệu:                  cà phê + C++
Form-encoded value:       c%C3%A0+ph%C3%AA+%2B+C%2B%2B
Percent-encoded spaces:   c%C3%A0%20ph%C3%AA%20%2B%20C%2B%2B
```

Hai kết quả biểu diễn cùng dữ liệu khi được giải mã bằng **đúng cơ chế tương ứng**. Trong URI syntax thuần túy, `+` không mặc nhiên có nghĩa là space. Các bộ giải mã query theo quy ước form thường diễn giải `+` là dấu cách; vì vậy ký tự cộng thật trong search term như `C++` cần được encode thành `C%2B%2B`.

## 5. Java 17+: phân tích URI và phân biệt bộ mã hóa form

Java chuẩn có `java.net.URI` để phân tích URI và `java.net.URLEncoder` cho quy ước `application/x-www-form-urlencoded`. Chúng phục vụ hai nhu cầu khác nhau.

Tệp độc lập `UriBasics.java`:

```java
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class UriBasics {
    public static void main(String[] args) {
        URI uri = URI.create(
            "https://api.example.com:8443/items/42?q=c%C3%A0%20ph%C3%AA&tag=C%2B%2B#info");

        System.out.println(uri.getScheme());
        System.out.println(uri.getAuthority());
        System.out.println(uri.getRawPath());
        System.out.println(uri.getRawQuery());
        System.out.println(uri.getQuery());
        System.out.println(uri.getFragment());

        String formValue = URLEncoder.encode(
            "cà phê + C++", StandardCharsets.UTF_8);
        System.out.println(formValue);
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 UriBasics.java
java UriBasics
```

Stdout chính xác:

```text
https
api.example.com:8443
/items/42
q=c%C3%A0%20ph%C3%AA&tag=C%2B%2B
q=cà phê&tag=C++
info
c%C3%A0+ph%C3%AA+%2B+C%2B%2B
```

Giải thích:

- `getRawQuery()` trả phần query còn escape sequence; `getQuery()` trả dạng percent-decoded.
- `getRawPath()` giúp xem chuỗi path chưa percent-decode; `getPath()` trả path đã percent-decode. Sự khác biệt quan trọng nếu có `%2F`, `%2E` hoặc dữ liệu đòi hỏi kiểm tra an toàn.
- `URI` không phải HTTP client; tạo `URI` không đồng nghĩa gửi request.
- `URLEncoder` **không phải bộ mã hóa đường dẫn URI tổng quát**. Nó phục vụ nội dung form: dấu cách thành `+`; slash có thể bị mã hóa thành `%2F` kể cả khi bạn muốn nó đóng vai trò ngăn cách segment.
- `URLDecoder` cũng theo quy ước form; không áp nó lên toàn URI/path rồi coi kết quả là URI hợp lệ.

Khi cần ghép đường dẫn và query phức tạp trong ứng dụng Spring, ưu tiên builder theo từng thành phần thay vì `String.format()`/nối chuỗi/`URLEncoder` cho cả URI.

## 6. Tạo URI trong Spring Web với `UriComponentsBuilder`

`UriComponentsBuilder` thuộc `spring-web`, giúp ghép path/query và mã hóa theo ngữ cảnh.

Ví dụ Java trong project có dependency Spring Web (đây là đoạn sử dụng thư viện Spring, không biên dịch bằng JDK đơn thuần):

```java
import java.net.URI;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;

public class SpringUriExample {
    public static void main(String[] args) {
        URI uri = UriComponentsBuilder
            .fromUriString("https://api.example.com")
            .pathSegment("products", "{sku}")
            .queryParam("q", "{keyword}")
            .queryParam("tag", "{tag}")
            .encode()
            .buildAndExpand(Map.of(
                "sku", "cup/blue",
                "keyword", "cà phê & trà",
                "tag", "C++"))
            .toUri();

        System.out.println(uri);
    }
}
```

Kết quả URI mong đợi khi dùng quy tắc **strictly encode URI variables** của builder:

```text
https://api.example.com/products/cup%2Fblue?q=c%C3%A0%20ph%C3%AA%20%26%20tr%C3%A0&tag=C%2B%2B
```

Ở đây `.pathSegment(...)` biểu thị các segment, `.queryParam(...)` biểu thị key/value. `{sku}`, `{keyword}` và `{tag}` là *URI template variables*. Gọi `.encode()` trên **builder trước khi expand** giúp mã hóa cả ký tự reserved trong biến, như `/`, `&`, `+`, khi chúng đóng vai trò dữ liệu. Việc tạo ra URI với `%2F` vẫn chưa bảo đảm phía server chấp nhận encoded slash như một path variable; đó là ràng buộc xử lý request riêng.

### Hai kiểu encode trong Spring không tương đương

| Phương thức | Cơ chế | Khi nào dùng |
|---|---|---|
| `UriComponentsBuilder.encode()` rồi expand biến | Encode template, sau đó **strict encode** giá trị biến, kể cả reserved có ý nghĩa cú pháp. | Mặc định nên chọn khi biến là dữ liệu opaque của người dùng. |
| `UriComponents.encode()` sau expand | Encode ký tự không hợp lệ trong từng component nhưng có thể giữ reserved hợp lệ trong component. | Khi bạn cố ý muốn reserved character của biến mang ngữ nghĩa bên trong component, và hiểu hệ quả. |

Đối với mã hóa path và query, không có **một** hàm encode chuỗi tùy ý dùng đúng cho mọi vị trí. Hãy giữ dấu phân tách do builder quản lý, đưa dữ liệu vào path segment hoặc query value, và chỉ mã hóa một lần.

## 7. Luồng request thực tế: client, server, Spring

```text
Giá trị người dùng: "cà phê & trà"
        |
        v
Client dựng URI theo component và UTF-8 percent-encoding
        |
        v
GET /search?q=c%C3%A0%20ph%C3%AA%20%26%20tr%C3%A0 HTTP/1.1
        |
        v
Reverse proxy / HTTP server / servlet container xử lý request-target
        |
        v
Spring MVC chọn handler, bind @RequestParam q = "cà phê & trà"
        |
        v
Controller kiểm tra dữ liệu theo hợp đồng nghiệp vụ -> response
```

Đây là luồng khái niệm; các tầng có thể có quy tắc riêng đối với `%2F`, `%25`, chuỗi escape sai, dấu chấm kép, dấu chấm phẩy, ký tự điều khiển và path normalization. Vì vậy:

- **Không tự percent-decode lại** chuỗi `@RequestParam`/`@PathVariable` đã được framework bind nếu không có lý do chính xác. Decode thêm có thể làm biến đổi giá trị hoặc tạo lỗ hổng.
- Kiểm tra đầu vào **sau khi biết rõ API/framework trả dữ liệu dạng raw hay decoded**; không đoán bằng mắt nhìn request trên trình duyệt.
- Nếu proxy và ứng dụng diễn giải URI khác nhau, routing, authentication hoặc cache có thể bị bypass; cần test end-to-end cùng cấu hình triển khai thực tế.
- Percent-encoding là **cơ chế biểu diễn dữ liệu**, không có chức năng mã hóa bảo mật, xác thực, phân quyền hay chống SQL injection/XSS.

### Khi dùng log, chữ ký request và cache

URI có thể là một phần của cache key, canonical request hoặc chữ ký HMAC. Nếu những hệ thống liên quan chuẩn hóa khác nhau (thứ tự query, viết hoa/chữ thường hex, `%2F` vs `/`, double-encoding, thông số lặp), chữ ký hay cache có thể sai. Phải thống nhất **một quy tắc canonicalization được công bố rõ**, thay vì tự decode/re-encode một URI tùy ý. Những quy tắc cụ thể phụ thuộc giao thức/API.

## 8. Những lỗi thường gặp và cách sửa

| Lỗi | Hậu quả | Cách xử lý |
|---|---|---|
| `"/search?q=" + keyword` với `keyword = "a&b"` | Dấu `&` có thể bị phân tích thành tham số mới. | Dùng builder `.queryParam("q", "{q}").encode()` và expand dữ liệu. |
| Dùng `URLEncoder` để mã hóa toàn URI | Dấu `:`, `/`, `?` trở thành dữ liệu; URI sai. | Tách scheme, path, query rồi encode đúng component. |
| Cho `C++` vào query nhưng quên encode dấu `+` | Bộ giải mã kiểu form có thể đọc thành `C  `. | Mã hóa cộng thật thành `%2B`. |
| Mã hóa chuỗi `%20` lần nữa | Thành `%2520`; server có thể nhận literal `%20` thay vì space. | Xác định dữ liệu đang raw hay đã encoded; một nguồn chịu trách nhiệm encode. |
| Decode toàn URI trước khi tách component | `%26`, `%2F`, `%3F` có thể trở thành delimiter. | Parse cấu trúc trước, decode theo component đúng một lần. |
| Coi `#fragment` là tham số server | Backend không nhận fragment qua HTTP request-target. | Nếu backend cần dữ liệu, gửi qua path/query/body có thiết kế rõ. |
| Tin rằng `%2F` luôn là ký tự bên trong path variable | Proxy/container có thể từ chối hoặc giải mã slash. | Dùng ID không chứa slash; kiểm thử route qua toàn bộ hạ tầng. |
| Ghép URL tùy ý từ input người dùng cho HTTP outbound | Có thể truy cập host không mong muốn (SSRF) hoặc redirect sai. | Kiểm tra scheme/host/port theo allowlist, chỉ ghép các component được phép. |

### Checklist khi phát triển một endpoint mới

1. Xác định rõ tài nguyên (path segment) và tùy chọn tìm/lọc (query); mô tả tham số lặp, rỗng, mặc định và kiểu dữ liệu.
2. Khi sinh URI, truyền dữ liệu vào **component đúng** qua builder; dùng UTF-8 và strict encoding cho template variables từ input.
3. Khi nhận request, kiểm tra giá trị đã bind trong Spring; validate format, kích thước, miền giá trị và quyền truy cập tài nguyên.
4. Thử với space, `+`, `&`, `#`, `%`, Unicode, dấu `/` trong ID, query lặp, query trống và input escape sai.
5. Kiểm tra trên cùng proxy/container và thiết lập routing của môi trường chạy thật, đặc biệt với encoded slash và normalization.

## 9. Câu hỏi tự kiểm tra

**1. `/items/12?lang=vi#section-2`: server nhận những phần nào?** HTTP request tới origin có path `/items/12` và query `lang=vi`; fragment `section-2` được client giữ và không chuyển trong request-target.

**2. Khi người dùng tìm kiếm `C++ & Java`, có thể nối nguyên văn vào `?q=...` không?** Không. Trong giá trị query kiểu key/value, cần encode `+` và `&` thành dữ liệu thay vì dấu cú pháp, ví dụ `q=C%2B%2B%20%26%20Java` theo percent-encoding.

**3. `/files/a%2Fb` có chắc tương đương một route variable là `a/b` không?** Không. Dù `%2F` biểu diễn slash ở mức byte, tầng proxy/container/framework có thể chặn hoặc diễn giải khác. Chọn ID phù hợp, và kiểm tra cấu hình thực tế.

**4. Nên dùng `URLEncoder` hay `UriComponentsBuilder` để tạo URL của Spring API?** Với URI nhiều thành phần, ưu tiên `UriComponentsBuilder` và mã hóa biến đúng vị trí. `URLEncoder` dùng cho dữ liệu theo quy ước `application/x-www-form-urlencoded`, nhất là form value; không mã hóa nguyên một URI.

## Nguồn tham khảo

- RFC 3986 — *Uniform Resource Identifier (URI): Generic Syntax*: https://www.rfc-editor.org/rfc/rfc3986
- Spring Framework Reference — *URI Links / URI Encoding*: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-uri-building.html
- Java SE — `java.net.URI` và `java.net.URLEncoder`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URI.html và https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLEncoder.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-fundamentals-003">HTTP method, tính an toàn và tính lặp lại không đổi kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# HTTP method, tính an toàn và tính lặp lại không đổi kết quả

## 1. HTTP method là gì và vì sao cần hiểu đúng?

HTTP là giao thức request/response giữa client và server. Một **HTTP request method** (còn gọi là HTTP verb) diễn đạt *hành động mà client muốn thực hiện đối với tài nguyên đích* (target resource). Trong request sau, method là `GET`, đường dẫn là `/api/books/42`:

```http
GET /api/books/42 HTTP/1.1
Host: example.com
Accept: application/json
```

Trong ứng dụng Spring Web, server có thể ánh xạ method và đường dẫn tới một phương thức controller. Tuy nhiên, ý nghĩa chuẩn của method là một **hợp đồng HTTP** giữa client, server, browser, proxy, cache và các công cụ mạng, không chỉ là lựa chọn annotation cho controller.

Ba câu hỏi cần tách biệt:

1. **Method này định yêu cầu làm gì?** Ví dụ `GET` đọc một biểu diễn của tài nguyên, `PUT` tạo/thay thế trạng thái của tài nguyên đích, `DELETE` yêu cầu loại bỏ các biểu diễn hiện tại của tài nguyên.
2. **Method này có an toàn (safe) không?** Client có đang *yêu cầu* thay đổi trạng thái hay không?
3. **Method này có tính idempotent không?** Lặp lại nhiều request giống hệt nhau có cùng *tác động dự kiến* như gửi một request hay không?

Những câu hỏi này ảnh hưởng trực tiếp đến thiết kế API, retry khi lỗi mạng, cache, crawler, bảo mật và trải nghiệm người dùng.

## 2. Các HTTP method thông dụng

| Method | Ý nghĩa chính | Ví dụ | Safe theo ngữ nghĩa chuẩn? | Idempotent theo ngữ nghĩa chuẩn? |
|---|---|---|---|---|
| `GET` | Lấy biểu diễn hiện tại của tài nguyên | `GET /products/42` | Có | Có |
| `HEAD` | Giống yêu cầu lấy metadata tương ứng với GET nhưng response không chứa body | `HEAD /products/42` | Có | Có |
| `OPTIONS` | Hỏi các tùy chọn giao tiếp được hỗ trợ | `OPTIONS /products/42` | Có | Có |
| `TRACE` | Kiểm tra vòng lặp phản chiếu request dọc đường truyền | `TRACE /diagnostic` | Có | Có |
| `POST` | Yêu cầu tài nguyên đích xử lý dữ liệu theo ngữ nghĩa của chính nó | `POST /orders` | Không | Không được bảo đảm |
| `PUT` | Tạo hoặc thay thế trạng thái tài nguyên ở URI đích bằng biểu diễn gửi lên | `PUT /products/42` | Không | Có |
| `PATCH` | Áp dụng một tập thay đổi lên tài nguyên | `PATCH /products/42` | Không | Không được bảo đảm |
| `DELETE` | Yêu cầu loại bỏ các biểu diễn hiện tại của tài nguyên đích | `DELETE /products/42` | Không | Có |
| `CONNECT` | Thiết lập tunnel tới server đích qua proxy | `CONNECT example.com:443` | Không | Không |

**Lưu ý về phạm vi:** `GET`, `HEAD`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `TRACE`, `CONNECT` được định nghĩa trong RFC 9110; `PATCH` được định nghĩa riêng trong RFC 5789. Phần lớn lập trình ứng dụng Spring Web tập trung vào `GET`, `POST`, `PUT`, `PATCH`, `DELETE`. `OPTIONS` thường xuất hiện khi tìm hiểu CORS. `HEAD` hữu ích để kiểm tra metadata. `TRACE` và `CONNECT` không phải các thao tác CRUD thông thường; nhiều server hạn chế hoặc vô hiệu hóa TRACE vì lý do bảo mật.

Method là **case-sensitive** ở tầng HTTP: `GET` là dạng chuẩn; không giả định `get` tương đương `GET`. Các method cũng không tự quyết định định dạng phản hồi: cùng `GET` có thể nhận HTML, JSON, hình ảnh hoặc một kiểu biểu diễn khác tùy tài nguyên và content negotiation.

## 3. Tính an toàn (safe): client không yêu cầu thay đổi trạng thái

### 3.1. Định nghĩa

Một HTTP method là **safe** khi ngữ nghĩa được định nghĩa cho method đó về bản chất là chỉ đọc: client không yêu cầu và không mong đợi việc áp dụng request sẽ làm thay đổi trạng thái trên origin server. Các method safe được chuẩn HTTP chỉ ra gồm **GET, HEAD, OPTIONS, TRACE**.

Ví dụ đúng với tính safe:

```http
GET /api/products/42 HTTP/1.1
Host: shop.example
```

Client yêu cầu đọc sản phẩm số 42. Tải lại trang, mở liên kết, crawler truy cập hoặc browser prefetch URL này không nên trở thành thao tác mua hàng, trừ tiền, xóa sản phẩm hay gửi email.

Ví dụ **sai về ngữ nghĩa**:

```http
GET /api/orders/42/cancel HTTP/1.1
Host: shop.example
```

Nếu việc đọc URL thực sự hủy đơn, request này đang thực hiện hành động thay đổi nghiệp vụ dù mang method `GET`. Thiết kế đúng hơn là một thao tác không an toàn có xác thực/phân quyền thích hợp, chẳng hạn `POST /api/orders/42/cancellation` hoặc một cập nhật trạng thái được mô hình hóa rõ ràng bằng method phù hợp.

### 3.2. “Safe” không có nghĩa tuyệt đối không phát sinh bất kỳ side effect nào

Server có thể ghi access log, tăng chỉ số giám sát, lưu telemetry hoặc ghi thống kê kỹ thuật khi xử lý `GET`. Điều đó không tự động khiến method trở thành unsafe theo định nghĩa: client **không hề yêu cầu** các side effect nội bộ đó và chúng không nên được coi như một giao dịch nghiệp vụ do người dùng chủ động kích hoạt.

Phân biệt:

- `GET /products/42` ghi log truy cập: thường vẫn hợp với ngữ nghĩa safe.
- `GET /products/42` tự động trừ số lượng tồn kho, thu phí hoặc xóa sản phẩm: vi phạm kỳ vọng safe.
- `GET /products/42?operation=delete` thực sự xóa sản phẩm: tham số query không “biến GET thành POST”; phía server phải ngăn hành động unsafe qua safe method.

**Safe không đồng nghĩa với không cần bảo mật.** `GET` vẫn có thể đọc dữ liệu cá nhân, bí mật hoặc tài nguyên chỉ người có quyền mới được xem. Server phải kiểm tra authentication/authorization tương ứng. URL và query string có thể bị lưu trong lịch sử trình duyệt, log và hệ thống quan sát; không nên đưa token hay mật khẩu vào URL.

### 3.3. Tại sao safe lại quan trọng?

Trình duyệt, công cụ tìm kiếm, proxy và bộ kiểm tra liên kết có thể truy cập `GET` mà không diễn tả ý định làm phát sinh giao dịch. Safe method tạo kỳ vọng để các hệ thống đó truy xuất tài nguyên mà không vô tình gây hại.

Vì vậy, một liên kết HTML như `<a href="/delete-account">...</a>` không được để yêu cầu GET của nó thực sự xóa tài khoản. Thao tác thay đổi trạng thái cần được kích hoạt có chủ đích bằng method phù hợp và cơ chế bảo vệ, đặc biệt khi liên quan đến phiên đăng nhập bằng cookie.

## 4. Tính idempotent: lặp lại cùng request vẫn có cùng tác động dự kiến

### 4.1. Định nghĩa và công thức trực quan

Một method có tính **idempotent** khi *tác động dự kiến trên server* của việc gửi nhiều request giống hệt nhau là như tác động của việc gửi một request. Có thể hình dung phép biến đổi trạng thái là `f`:

```text
f(f(state)) = f(state)
```

Công thức là mô hình khái niệm về tác động dự kiến, **không** có nghĩa mọi byte HTTP response hoặc mọi dòng log sẽ giống nhau, và không bảo đảm các request đồng thời với client khác được cách ly.

Ví dụ một sản phẩm có trạng thái `published=false`:

```http
PUT /api/products/42 HTTP/1.1
Host: shop.example
Content-Type: application/json

{"name":"Java","published":true}
```

Nếu API định nghĩa đây là biểu diễn thay thế đầy đủ và server áp dụng đúng ngữ nghĩa PUT, lặp lại cùng request hai hoặc ba lần đều có kết quả nghiệp vụ sau cùng là sản phẩm 42 có `name="Java"` và `published=true`. Request có thể cập nhật dữ liệu một lần hoặc nhiều lần ở hạ tầng; **tác động người dùng yêu cầu** là giống nhau.

Ngược lại, thao tác tăng số lượng:

```http
POST /api/counters/visits/increment HTTP/1.1
Host: shop.example
```

Nếu mỗi request làm giá trị tăng một đơn vị, thực hiện hai lần sẽ tăng hai đơn vị. Đây là hành động **không idempotent**.

### 4.2. Idempotent không phải “response giống hệt”

Giả sử lần DELETE đầu tiên xóa thành công:

```http
DELETE /api/products/42 HTTP/1.1
Host: shop.example
```

Server có thể trả `204 No Content` cho lần đầu và `404 Not Found` cho lần thứ hai, hoặc trả `204` ở cả hai lần tùy hợp đồng API. Trạng thái đích vẫn là “sản phẩm không còn tồn tại”. Do đó, **DELETE vẫn được chuẩn HTTP định nghĩa là idempotent** dù status code qua mỗi lần gửi có thể khác.

Tương tự, `PUT` có thể trả `201 Created` lần đầu khi tạo tài nguyên và `200 OK` hoặc `204 No Content` ở lần thay thế sau. Không cần ép tất cả response giống nhau để có tính idempotent.

Giống như safe, tính idempotent nói về **tác động dự kiến mà client yêu cầu**. Log, revision history, request counter của hạ tầng hoặc việc theo dõi retry vẫn có thể thay đổi mỗi lần nhận request. Tuy nhiên, nếu endpoint `PUT` bị thiết kế để *mỗi lần gửi đều cộng thêm tiền vào tài khoản* thì nó không đáp ứng ngữ nghĩa idempotent của PUT dù controller vẫn dùng `@PutMapping`.

### 4.3. Safe và idempotent liên hệ thế nào?

- Safe method theo ngữ nghĩa chuẩn cũng idempotent: lặp lại một hành động chỉ đọc không yêu cầu thay đổi trạng thái nghiệp vụ.
- Idempotent **không có nghĩa** safe: `PUT` và `DELETE` làm thay đổi tài nguyên, nhưng lặp lại cùng yêu cầu vẫn đạt cùng tác động dự kiến.
- Unsafe và không được bảo đảm idempotent: `POST`, `PATCH`. Một API cụ thể vẫn **có thể** thiết kế một thao tác POST hoặc PATCH mang tính idempotent với điều kiện/logic rõ ràng, nhưng client không được tự giả định chỉ dựa vào method.
- Idempotency không đồng nghĩa với transaction, exactly-once delivery, atomicity, tránh race condition, hoặc tự động chống ghi đè cập nhật của người khác.

## 5. Phân tích riêng các method quan trọng

### 5.1. GET và HEAD

`GET` lấy biểu diễn tài nguyên hiện tại, không phải lệnh tạo hay thay đổi tài nguyên. Khi API trả JSON, response thành công thường là `200 OK`; tài nguyên không tìm thấy thường là `404 Not Found`. `GET` và `HEAD` có thể tham gia cơ chế HTTP caching theo điều kiện của response và cache policy.

`HEAD` yêu cầu metadata tương ứng với GET nhưng **không có response body**. Header như `Content-Type`, `ETag`, `Last-Modified` có thể giúp client kiểm tra tình trạng tài nguyên mà không tải nội dung. Không nên kỳ vọng mọi header tạo ra bằng GET nhất thiết đều xuất hiện giống hệt trong HEAD; HTTP có ngoại lệ cho một số trường mà giá trị chỉ xác định khi sinh body.

```bash
curl -i http://localhost:8080/api/counters/demo
curl -I http://localhost:8080/api/counters/demo
```

Trong Spring MVC, một mapping `@GetMapping` thông thường cũng hỗ trợ xử lý HEAD thích hợp; khả năng chính xác còn phụ thuộc cấu hình và handler của ứng dụng.

### 5.2. POST

`POST` gửi nội dung để tài nguyên đích xử lý theo ngữ nghĩa của riêng nó. Thường dùng để tạo tài nguyên có ID do server cấp, gửi form, kích hoạt nghiệp vụ, hoặc thêm một sự kiện mới:

```http
POST /api/orders HTTP/1.1
Host: shop.example
Content-Type: application/json

{"productId":42,"quantity":1}
```

Nếu mỗi lần gửi tạo một đơn hàng mới, gửi hai lần có thể tạo **hai đơn**. Khi tạo thành công, server thường phản hồi `201 Created` và `Location: /api/orders/<id>`. Việc một API POST có hỗ trợ chống trùng lặp không phải thuộc tính mặc định của POST.

POST cũng có thể được thiết kế idempotent trong một nghiệp vụ cụ thể, ví dụ bằng mã yêu cầu duy nhất hoặc `Idempotency-Key`; đó là **cam kết triển khai riêng** cần ghi rõ trong tài liệu API.

### 5.3. PUT

`PUT` yêu cầu tạo hoặc **thay thế trạng thái tài nguyên ở URI đích** bằng biểu diễn mà client gửi:

```http
PUT /api/users/42 HTTP/1.1
Host: shop.example
Content-Type: application/json

{"name":"Lan","active":true}
```

Một ứng dụng có thể chỉ cho phép PUT lên tài nguyên đã tồn tại và trả `404` nếu chưa tồn tại; ứng dụng khác có thể cho phép tạo mới ở URI đó theo quy tắc của nó. Nếu đã tạo mới thì `201` là mã thành công thích hợp; nếu thay thế, có thể là `200` hoặc `204`.

Tránh nhầm PUT với phép tăng/lũy kế (`balance += amount`, `stock--`). Gửi lại payload giống hệt phải giữ đúng tác động dự kiến. Nếu API dùng PUT nhưng chỉ cập nhật một vài field, phải tài liệu hóa hợp đồng rõ ràng; về mặt ngữ nghĩa chuẩn PUT là tạo/thay thế biểu diễn đích, còn cập nhật từng phần thường phù hợp với PATCH hơn.

### 5.4. PATCH

`PATCH` gửi **tập chỉ dẫn thay đổi một phần** của tài nguyên. Nội dung thay đổi phụ thuộc media type và hợp đồng API, ví dụ JSON Patch (`application/json-patch+json`) hoặc JSON Merge Patch (`application/merge-patch+json`); một DTO JSON đơn giản dùng trong API riêng không tự động tuân theo hai định dạng chuẩn đó.

Hai kiểu PATCH có tác động khác nhau:

```text
A. Gán giá trị cố định: set active = true
   Gửi hai lần thường vẫn là active = true → có thể idempotent.

B. Tăng số dư: balance = balance + 100
   Gửi hai lần cộng thêm 200 → không idempotent.
```

PATCH **không được bảo đảm idempotent theo định nghĩa chung**; muốn an toàn khi retry phải biết cụ thể thao tác có idempotent không và cần cân nhắc điều kiện phiên bản để tránh xung đột.

### 5.5. DELETE

`DELETE` yêu cầu loại bỏ các biểu diễn hiện tại của tài nguyên đích; ví dụ:

```http
DELETE /api/users/42 HTTP/1.1
Host: shop.example
```

Xóa cùng một tài nguyên nhiều lần vẫn có cùng tác động mong muốn: tài nguyên không còn xuất hiện theo hợp đồng của ứng dụng. DELETE idempotent **không buộc hệ thống phải xóa vật lý ngay lập tức**; soft delete, xử lý bất đồng bộ, audit hoặc ràng buộc nghiệp vụ đều cần được mô tả rõ.

Nếu server chỉ nhận công việc để xử lý về sau, response `202 Accepted` có thể phù hợp. Nếu xóa hoàn tất và không trả body, `204 No Content` thường được dùng.

### 5.6. OPTIONS, TRACE và CONNECT

`OPTIONS` cho biết các lựa chọn giao tiếp được hỗ trợ. Một ứng dụng có thể trả header `Allow` hoặc các header khác. **CORS preflight** là một trường hợp đặc biệt dùng OPTIONS, thường có `Origin` và `Access-Control-Request-Method`, nhưng không phải mọi OPTIONS đều là CORS preflight.

`TRACE` dùng kiểm tra đường truyền theo cơ chế loop-back; nhiều server không bật nó vì rủi ro lộ thông tin request. `CONNECT` chủ yếu dành cho proxy tạo tunnel, không dùng làm phương thức CRUD của controller bình thường.

## 6. Thiết kế API theo resource và method

Một API quản lý sản phẩm có thể có thiết kế sau:

| Nhu cầu nghiệp vụ | Method và URI | Tác động dự kiến |
|---|---|---|
| Xem danh sách sản phẩm | `GET /api/products` | Đọc danh sách |
| Xem sản phẩm cụ thể | `GET /api/products/42` | Đọc sản phẩm 42 |
| Tạo sản phẩm để server cấp ID | `POST /api/products` | Có thể tạo một sản phẩm mới mỗi lần |
| Tạo/thay thế sản phẩm theo ID xác định | `PUT /api/products/42` | Trạng thái đích tương ứng payload |
| Sửa một phần thông tin | `PATCH /api/products/42` | Áp dụng tập thay đổi được mô tả |
| Xóa sản phẩm | `DELETE /api/products/42` | Không còn biểu diễn hiện tại của sản phẩm theo API |

Đừng đánh đồng HTTP verb với CRUD một cách cứng nhắc: `POST` có thể kích hoạt xử lý nghiệp vụ; `GET` có thể trả biểu diễn được tổng hợp từ nhiều nguồn; `PATCH` có thể là một tập thao tác phức tạp. Method nói lên **ý định giao tiếp**, còn URI và tài liệu API nói lên **tài nguyên, quyền, payload và kết quả cụ thể**.

Các quy ước hữu ích:

- URI nên mô tả tài nguyên bằng danh từ khi phù hợp (`/orders/42`), còn method mô tả hành động theo giao ước HTTP.
- Đừng dùng `GET /delete?id=42` hay `GET /pay?id=42` để kích hoạt thay đổi nghiệp vụ.
- Chọn mã trạng thái phản ánh thực tế: `200 OK`, `201 Created`, `202 Accepted`, `204 No Content`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`, `412 Precondition Failed`, `405 Method Not Allowed`.
- Khi method được server nhận biết nhưng không được cho phép trên tài nguyên, `405` thường đi kèm `Allow`; khi server không nhận biết/không triển khai method, `501 Not Implemented` là tình huống khác.
- Authentication, authorization, validation và bảo vệ chống CSRF (khi ứng dụng dùng cơ chế cookie/session và có nguy cơ CSRF) cần dựa trên bối cảnh; lựa chọn method đúng không tự động giải quyết vấn đề bảo mật.

## 7. Thực hành với Spring Web MVC

Ví dụ dưới đây là một **ứng dụng Spring Boot MVC tối giản** để quan sát rõ sự khác nhau giữa `GET`, `PUT`, `POST` và `DELETE`. Nó sử dụng Java record (Java 17+) và bộ nhớ RAM, phù hợp cho học tập. Khi chạy cần dự án Spring Boot có dependency web MVC và bộ chuyển JSON, ví dụ một dự án tạo bằng Spring Initializr với dependency **Spring Web**, cùng JDK tương thích với phiên bản Boot được chọn.

Tạo tệp `src/main/java/com/example/http/HttpMethodsApplication.java`:

```java
package com.example.http;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class HttpMethodsApplication {
    public static void main(String[] args) {
        SpringApplication.run(HttpMethodsApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/counters")
class CounterController {
    private final ConcurrentHashMap<String, Integer> counters =
            new ConcurrentHashMap<>();

    public record CounterValue(int value) {}

    @GetMapping("/{key}")
    public ResponseEntity<CounterValue> get(@PathVariable String key) {
        Integer value = counters.get(key);
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new CounterValue(value));
    }

    @PutMapping("/{key}")
    public ResponseEntity<CounterValue> replace(
            @PathVariable String key,
            @RequestBody CounterValue body) {
        Integer previous = counters.put(key, body.value());
        HttpStatus status = previous == null
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(body);
    }

    @PostMapping("/{key}/increment")
    public ResponseEntity<CounterValue> increment(
            @PathVariable String key) {
        Integer value = counters.compute(
                key, (ignored, oldValue) ->
                        oldValue == null ? 1 : oldValue + 1);
        return ResponseEntity.ok(new CounterValue(value));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        counters.remove(key);
        return ResponseEntity.noContent().build();
    }
}
```

**Tại sao code minh họa đúng trọng tâm?**

- `GET` chỉ đọc trạng thái; giá trị `null` dẫn đến `404`.
- `PUT` gán một giá trị cụ thể bằng `counters.put(key, body.value())`. Gửi `{"value":5}` bao nhiêu lần thì trạng thái cuối cùng vẫn là 5, nếu không có thao tác khác chen vào.
- `POST /increment` dùng `compute` để tăng giá trị hiện tại mỗi lần nhận request; `ConcurrentHashMap.compute` làm phép cập nhật tại một key theo cách atomic trong map. Hai POST làm tăng hai lần.
- `DELETE` dùng `remove`; gọi lại vẫn giữ key ở trạng thái không tồn tại.
- `ResponseEntity` thể hiện được status khác nhau (`201` khi PUT tạo key mới, `200` khi thay thế) mà không làm mất idempotency.

Đây là dữ liệu **in-memory**, không lưu sau khi khởi động lại và không thể hiện xử lý giao dịch, bảo mật, validation hoặc persistence của hệ thống thật. `Integer` cũng có nguy cơ overflow nếu liên tục tăng đến giới hạn; tình huống production cần lựa chọn miền dữ liệu và quy tắc kiểm tra phù hợp.

### Chạy và gửi request

Với dự án Gradle có Spring Boot plugin, chạy `./gradlew bootRun` (hoặc `gradlew.bat bootRun` trên Windows). Nếu dùng Maven Wrapper, chạy `./mvnw spring-boot:run` (hoặc `mvnw.cmd spring-boot:run` trên Windows). Không thể chạy riêng source này bằng lệnh `javac` thuần vì nó cần thư viện Spring Boot và Spring MVC.

Giả sử server đang chạy tại `http://localhost:8080`, lần lượt thực hiện:

**Bước 1 — Đọc trước khi tạo**

```bash
curl -i http://localhost:8080/api/counters/demo
```

Status dự kiến: `404 Not Found`.

**Bước 2 — Gửi PUT lần thứ nhất**

```bash
curl -i -X PUT "http://localhost:8080/api/counters/demo" -H "Content-Type: application/json" -d '{"value":5}'
```

Status dự kiến: `201 Created`; body JSON có giá trị `{"value":5}`.

**Bước 3 — Lặp lại cùng PUT**

```bash
curl -i -X PUT "http://localhost:8080/api/counters/demo" -H "Content-Type: application/json" -d '{"value":5}'
```

Status dự kiến lần này: `200 OK`, nhưng body và giá trị trong map vẫn tương ứng với `{"value":5}`. Status khác nhau **không phủ nhận tính idempotent**.

**Bước 4 — Gửi POST tăng giá trị hai lần**

```bash
curl -i -X POST http://localhost:8080/api/counters/demo/increment
curl -i -X POST http://localhost:8080/api/counters/demo/increment
```

Body JSON dự kiến lần lượt chứa `{"value":6}` và `{"value":7}`. Cùng một request POST làm thay đổi trạng thái thêm mỗi lần: không idempotent.

**Bước 5 — Gửi DELETE hai lần**

```bash
curl -i -X DELETE http://localhost:8080/api/counters/demo
curl -i -X DELETE http://localhost:8080/api/counters/demo
```

Hai response đều là `204 No Content`, không có body. Sau đó GET trả `404 Not Found`.

**Lưu ý về shell:** cú pháp dùng dấu nháy đơn cho JSON trong ví dụ `curl` phù hợp với Bash, zsh hoặc PowerShell hiện đại tùy phiên bản. Trong Windows PowerShell 5.1, `curl` thường là alias của `Invoke-WebRequest`; để chạy cú pháp kiểu curl, gọi `curl.exe` và điều chỉnh cách quote JSON của PowerShell, hoặc dùng `Invoke-RestMethod`.

### Liên hệ với annotation Spring MVC

| Annotation Spring MVC | Method HTTP | Điều cần giữ đúng |
|---|---|---|
| `@GetMapping` | GET | Không dùng để gây ra thay đổi nghiệp vụ do client yêu cầu |
| `@PostMapping` | POST | Xử lý tác vụ có side effect; retry phải có chiến lược |
| `@PutMapping` | PUT | Gán/thay thế tài nguyên theo hợp đồng; cùng request giữ tác động dự kiến |
| `@PatchMapping` | PATCH | Xác định rõ semantics của patch và khả năng retry |
| `@DeleteMapping` | DELETE | Lặp lại cùng yêu cầu giữ cùng trạng thái đã xóa |
| `@RequestMapping(method = RequestMethod.OPTIONS)` | OPTIONS | Chỉ tùy biến khi cần, vì framework/server có thể tự xử lý |

Annotation chỉ đăng ký handler; nó **không thể tự biến một thao tác tăng số dư thành idempotent** hoặc biến hành động xóa thành safe nếu logic bên trong vi phạm ngữ nghĩa method.

## 8. Retry: vì sao idempotency có giá trị trong hệ phân tán?

Giả sử client gửi request đến server. Server **đã xử lý thành công**, nhưng kết nối đứt trước khi client nhận response:

```text
Client                          Server
  | -------- PUT --------------> |
  |                              | cập nhật tài nguyên xong
  |       X response mất         |
  |                              |
  | -------- PUT (retry) ------> |
  |                              | áp dụng lại cùng trạng thái
  | <------- response ---------  |
```

Client không biết lần đầu server đã xử lý hay chưa. Nếu PUT tuân thủ idempotency, retry cùng request có thể đạt cùng tác động mong muốn. Nếu request là POST tạo đơn và cứ gửi lại mà không có cơ chế chống trùng, client có nguy cơ tạo **hai đơn hàng**.

Các nguyên tắc thực hành:

1. Chỉ retry tự động khi thực sự hiểu ngữ nghĩa thao tác. HTTP coi các method idempotent là ứng viên phù hợp để retry khi lỗi giao tiếp; còn với method không idempotent, không tự retry chỉ vì chưa thấy response.
2. Retry cần timeout, giới hạn số lần thử, backoff và xử lý mã lỗi phù hợp. Idempotency không có nghĩa retry vô hạn; retry cũng không tự khắc phục dữ liệu sai hoặc lỗi validation.
3. Với giao dịch có nguy cơ trùng lặp (đặt đơn, thanh toán, gửi lệnh), cân nhắc **idempotency key**: client tạo một mã duy nhất cho *một ý định nghiệp vụ*, gửi lại cùng key khi retry; server lưu trạng thái xử lý/kết quả theo key và trả lại hoặc xử lý nhất quán. Đây là hợp đồng ứng dụng cần thiết kế rõ: phạm vi key, thời hạn lưu, cùng key nhưng payload khác, lỗi giữa các bước, và đồng thời nhiều request. Không nên chỉ dựa vào một map RAM cho nghiệp vụ tài chính.
4. Idempotency key giúp chống thực hiện lặp của **cùng một yêu cầu có định danh**, nhưng không tự bảo đảm mọi hành vi trong hệ thống phân tán là “exactly once”.
5. GET/HEAD và các phương thức an toàn còn có thể chịu tác động của cache. **Safe, idempotent và cacheable là ba thuộc tính khác nhau**: ví dụ PUT idempotent nhưng response của PUT không mặc nhiên cacheable; GET có thể cache theo các quy tắc HTTP; cache không tự thay thế cơ chế chống request trùng.

Ví dụ minh họa một hợp đồng tùy ứng dụng, không phải lời cam kết tất cả API đều hỗ trợ header này:

```http
POST /api/orders HTTP/1.1
Host: shop.example
Idempotency-Key: order-request-abc123
Content-Type: application/json

{"productId":42,"quantity":1}
```

Một server có hỗ trợ có thể phát hiện retry cùng key và cùng nội dung theo quy tắc đã công bố, tránh tạo đơn thứ hai. Nếu đổi payload nhưng giữ key, server phải có quy tắc xử lý xung đột rõ ràng.

## 9. Idempotency không giải quyết việc ghi đè cập nhật đồng thời

Giả sử Lan và Minh cùng đọc một sản phẩm ở phiên bản 7. Lan đổi tên sản phẩm; sau đó Minh dùng dữ liệu cũ để PUT trạng thái đầy đủ. Dù cả hai PUT đều idempotent, update của Minh vẫn có thể **ghi đè** thay đổi của Lan.

HTTP hỗ trợ conditional request bằng `ETag` và `If-Match`. Một quy trình minh họa:

```http
GET /api/products/42 HTTP/1.1
Host: shop.example
```

Server trả một ETag, chẳng hạn `"v7"`. Client gửi cập nhật:

```http
PUT /api/products/42 HTTP/1.1
Host: shop.example
If-Match: "v7"
Content-Type: application/json

{"name":"Tên mới","active":true}
```

Nếu biểu diễn hiện tại đã thay đổi và điều kiện không còn đúng, server nên trả `412 Precondition Failed` (theo semantics của If-Match) thay vì âm thầm ghi đè. Ứng dụng phải thực sự triển khai và kiểm tra precondition; thêm header ở phía client không làm server tự động xử lý đúng trong mọi thiết kế controller. Conditional request bổ sung kiểm soát concurrency, còn idempotency giải quyết hành vi lặp lại.

## 10. Những nhầm lẫn thường gặp

**Nhầm 1 — “Idempotent nghĩa là chỉ đọc.”** Không đúng. PUT và DELETE đều có thể làm thay đổi trạng thái nhưng vẫn idempotent.

**Nhầm 2 — “Idempotent nghĩa là mỗi lần đều có response 200 và body giống nhau.”** Không đúng. PUT tạo mới có thể trả 201, lần thay thế tiếp theo trả 200; DELETE lần sau có thể trả 404 thay vì 204.

**Nhầm 3 — “POST luôn tạo mới, PUT luôn cập nhật, PATCH chắc chắn idempotent.”** Không đúng. POST yêu cầu tài nguyên xử lý theo semantics của nó; PUT có thể tạo hoặc thay thế; PATCH không được bảo đảm idempotent.

**Nhầm 4 — “GET chỉ gọi server một lần nên có thể dùng để trừ tiền.”** Không đúng. Trình duyệt, crawler, cache validator hoặc cơ chế prefetch có thể tạo thêm GET. Nghiệp vụ trừ tiền cần một hành động unsafe được xác thực và bảo vệ thích hợp.

**Nhầm 5 — “Dùng `@PutMapping` là logic Java tự động idempotent.”** Không đúng. Nếu controller tăng biến đếm hoặc tạo bản ghi mới mỗi lần gọi, behavior không tuân thủ kỳ vọng PUT.

**Nhầm 6 — “Idempotency giúp ngăn mọi lỗi do concurrent requests.”** Không đúng. Thao tác cạnh tranh giữa nhiều người dùng cần optimistic locking, conditional request hoặc đồng bộ phù hợp. Idempotency của một request đơn lẻ không đảm bảo tính tuần tự cho toàn hệ thống.

**Nhầm 7 — “Method GET an toàn nên API công khai cho mọi người được.”** Không đúng. Safety là semantics về thay đổi trạng thái; quyền truy cập dữ liệu là trách nhiệm riêng.

**Nhầm 8 — “Retry POST chỉ cần đặt cùng ID trong body.”** Chưa đủ nếu backend không có cơ chế nhận dạng, lưu trữ và đảm bảo xử lý request trùng theo hợp đồng. Việc phân biệt một retry với một hành động mới phải được thiết kế rõ ràng.

## 11. Bài tập tự kiểm tra

**Câu 1.** Endpoint `PUT /api/settings/theme` luôn đặt theme là `dark`. Gửi hai lần có idempotent không?  
**Giải thích:** Có, nếu server thực hiện đúng phép gán trạng thái theme=dark; gửi thêm cùng yêu cầu không làm thay đổi tác động nghiệp vụ mong muốn.

**Câu 2.** Endpoint `PATCH /api/users/42` luôn đặt `active=true` có thể idempotent không?  
**Giải thích:** Có thể, theo semantics cụ thể của phép patch. Tuy vậy PATCH không được chuẩn bảo đảm idempotent cho mọi loại thay đổi.

**Câu 3.** DELETE lần đầu trả 204, lần hai trả 404: có vi phạm idempotency không?  
**Giải thích:** Không. Mục tiêu sau cả hai lần vẫn là tài nguyên đã bị xóa/không còn tồn tại.

**Câu 4.** Một GET trả 200 nhưng cũng gửi email xác nhận mua hàng. GET này có tuân thủ safety không?  
**Giải thích:** Không, nếu email xác nhận là hành động nghiệp vụ phát sinh do việc gọi GET và client không chỉ yêu cầu đọc.

**Câu 5.** Có thể tự động retry một POST thanh toán ngay khi timeout không?  
**Giải thích:** Không nên nếu chưa biết request trước đã được xử lý hay chưa và chưa có hợp đồng chống trùng; có thể gây giao dịch lặp. Cần cơ chế idempotency, truy vấn trạng thái hoặc cách phục hồi nghiệp vụ do hệ thống hỗ trợ.

**Câu 6.** Hai PUT cùng idempotent có chắc không ghi đè dữ liệu của nhau không?  
**Giải thích:** Không. Xét dùng ETag/If-Match, optimistic locking hoặc chiến lược concurrency tương ứng.

## 12. Tài liệu chuẩn để tra cứu

- [RFC 9110 – HTTP Semantics, Section 9: Methods](https://www.rfc-editor.org/rfc/rfc9110.html#section-9): đặc biệt Section 9.2.1 (Safe Methods), 9.2.2 (Idempotent Methods), 9.3 (Method Definitions).
- [RFC 5789 – PATCH Method for HTTP](https://www.rfc-editor.org/rfc/rfc5789.html): ngữ nghĩa PATCH và lý do PATCH không mặc định idempotent.
- [Spring Framework Reference – Spring Web MVC](https://docs.spring.io/spring-framework/reference/web/webmvc.html): controller, request mapping và cách Spring MVC xử lý HTTP.

**Ghi nhớ:** *Safe* nói về việc client có yêu cầu thay đổi trạng thái hay không; *idempotent* nói về tác động dự kiến khi lặp lại cùng request. GET/HEAD/OPTIONS/TRACE safe và idempotent; PUT/DELETE không safe nhưng idempotent; POST/PATCH không được bảo đảm idempotent. Thiết kế controller, retry và xử lý đồng thời phải tôn trọng những ngữ nghĩa này.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-fundamentals-004">Mã trạng thái và ý nghĩa của các nhóm 2xx, 3xx, 4xx, 5xx</a>

<details>
<summary>Xem chi tiết</summary>

# Mã trạng thái HTTP và ý nghĩa các nhóm 2xx, 3xx, 4xx, 5xx

Mỗi phản hồi HTTP có một **mã trạng thái (status code)** gồm ba chữ số. Mã này cho client biết kết quả xử lý request ở tầng HTTP: thành công, cần hành động tiếp, có vấn đề với request, hay có lỗi phía server. Trong Spring Web, chọn mã trạng thái đúng giúp browser, mobile app, API client, proxy và hệ thống giám sát xử lý phản hồi nhất quán.

## 1. Mục tiêu và cách đọc mã trạng thái

Sau bài học, bạn cần:
- Nhận diện ý nghĩa tổng quát của các nhóm **2xx**, **3xx**, **4xx**, **5xx** và biết nhóm **1xx** là thông tin trung gian.
- Phân biệt những mã thường dùng như `200`, `201`, `202`, `204`, `301`, `302`, `303`, `304`, `307`, `308`, `400`, `401`, `403`, `404`, `405`, `409`, `415`, `422`, `429`, `500`, `502`, `503` và `504`.
- Chọn status code dựa trên **ý nghĩa của tình huống**, phương thức HTTP, trạng thái tài nguyên và hợp đồng API.
- Trả mã trạng thái bằng `ResponseEntity`, `@ResponseStatus` hoặc xử lý ngoại lệ trong Spring MVC.
- Chẩn đoán lỗi bằng cách đọc **status + headers + body** thay vì chỉ nhìn một chữ số.

Một status code có dạng `xyz`, trong đó chữ số đầu xác định nhóm:

| Nhóm | Tên theo HTTP | Ý nghĩa tổng quát |
| --- | --- | --- |
| 1xx | Informational | Phản hồi trung gian trong lúc trao đổi; chỉ nhắc để có bức tranh đầy đủ. |
| **2xx** | Successful | Request đã được nhận, hiểu và xử lý thành công theo ngữ nghĩa của method/status. |
| **3xx** | Redirection | Client cần thêm bước xử lý, lựa chọn biểu diễn hoặc dùng dữ liệu cache/địa chỉ khác. |
| **4xx** | Client Error | Server cho biết request không thể được đáp ứng trong điều kiện hiện tại; thường liên quan dữ liệu, xác thực, quyền, tài nguyên hay giới hạn phía client. |
| **5xx** | Server Error | Server/gateway không thể hoàn thành một request về nguyên tắc hợp lệ do lỗi hoặc tình trạng phía hạ tầng. |

**Quan trọng:** Status code là **ngữ nghĩa HTTP**, không phải ngoại lệ Java, số dòng lỗi hay một trường tùy ý trong JSON. Ví dụ `HTTP/1.1 404 Not Found` là phản hồi HTTP; chuỗi `"status":404` trong JSON chỉ là dữ liệu ứng dụng nếu server tự định nghĩa. Trong HTTP/2 và HTTP/3, status nằm ở pseudo-header `:status`, không có status-line văn bản kiểu HTTP/1.1.

### Status code, reason phrase, headers và body

Ví dụ phản hồi HTTP/1.1:

~~~http
HTTP/1.1 201 Created
Location: /api/books/42
Content-Type: application/json

{"id":42,"title":"HTTP"}
~~~

Trong ví dụ:
- `201` là status code để chương trình xử lý.
- `Created` là reason phrase tùy chọn ở HTTP/1.1 để con người đọc; không nên so chuỗi này khi lập trình.
- `Location` cung cấp URI của tài nguyên vừa tạo.
- `Content-Type` cho biết kiểu biểu diễn response body.
- JSON là body ứng dụng; **không phải mã trạng thái thứ hai**.

Status code không cho biết toàn bộ chi tiết lỗi. Cùng một `409 Conflict` có thể có nhiều nguyên nhân nghiệp vụ; client cần đọc body lỗi theo schema API, kèm header liên quan.

## 2. Nhóm 2xx — yêu cầu được xử lý thành công

Nhóm 2xx biểu đạt thành công nhưng **không đồng nghĩa lúc nào cũng đã hoàn tất một công việc nền hoặc có body**.

| Mã | Tên | Khi nào dùng |
| --- | --- | --- |
| **200 OK** | OK | Xử lý thành công; ví dụ GET trả representation, POST trả kết quả xử lý đã hoàn thành. |
| **201 Created** | Created | Request đã hoàn tất và dẫn đến tạo một hay nhiều tài nguyên; thường trả `Location` tới tài nguyên chính mới tạo. |
| **202 Accepted** | Accepted | Request được nhận để xử lý nhưng việc xử lý **chưa hoàn thành**; có thể thành công hoặc thất bại về sau. |
| **204 No Content** | No Content | Xử lý thành công và server không gửi message content; hữu ích sau DELETE hoặc cập nhật không cần body. |
| **206 Partial Content** | Partial Content | Server đáp ứng thành công request lấy một phần representation, thường liên quan `Range`/`Content-Range`. |

### 2.1. 200 OK: thành công và nội dung tùy method

Ví dụ:

~~~http
GET /api/books/42 HTTP/1.1
Host: api.example.test
Accept: application/json

~~~

Phản hồi minh họa:

~~~http
HTTP/1.1 200 OK
Content-Type: application/json

{"id":42,"title":"HTTP"}
~~~

Khi GET tài nguyên thành công, 200 thường mang representation của tài nguyên. Với POST, 200 cũng có thể hợp lệ nếu server trả kết quả xử lý mà không có ý nghĩa tạo tài nguyên mới. Với HEAD, 200 không đi kèm response content dù có thể có header mô tả representation tương ứng. Vì vậy, không suy luận “200 luôn chứa JSON”.

### 2.2. 201 Created và Location

Nếu POST thực sự **tạo tài nguyên đã tồn tại sau request**, 201 là một lựa chọn phù hợp:

~~~http
HTTP/1.1 201 Created
Location: /api/books/42
Content-Type: application/json

{"id":42,"title":"HTTP"}
~~~

`Location` cho client biết URI của tài nguyên chính vừa tạo. Đây có thể là URI tương đối, được giải quyết theo URI của request. Body là tùy chọn theo hợp đồng API; nó có thể mô tả tài nguyên mới. Đừng trả 201 chỉ vì method là POST: nếu POST thực hiện tìm kiếm hoặc chạy một hành động không tạo tài nguyên, hãy chọn status phản ánh kết quả thật.

### 2.3. 202 Accepted: bất đồng bộ chưa xong

Ví dụ API nhận yêu cầu xuất báo cáo vào hàng đợi:

~~~http
HTTP/1.1 202 Accepted
Content-Type: application/json
Location: /api/report-jobs/abc

{"jobId":"abc","state":"queued"}
~~~

Client có thể gọi URL theo hợp đồng API để hỏi tiến độ. `202` **không cam kết** job sẽ thành công; phản hồi cuối của job phải được truyền đạt qua cơ chế được thiết kế riêng (truy vấn trạng thái, callback, sự kiện...). Đừng dùng 200 với nội dung “đã hoàn thành” khi thực tế hệ thống mới ghi nhận yêu cầu.

### 2.4. 204 No Content: không có body

~~~http
HTTP/1.1 204 No Content

~~~

204 biểu thị xử lý thành công nhưng **không có message content**, nên client không gọi parser JSON trên một body rỗng. Không tự gắn JSON `{}` vào response 204. Một số framework/container loại bỏ body nếu bạn cố gửi; API phải chủ động thiết kế đúng.

### 2.5. 206 Partial Content

Nếu client gửi `Range: bytes=0-99` và server hỗ trợ range request, response có thể trả 206 với `Content-Range` chỉ rõ đoạn dữ liệu thực gửi. 206 **không có nghĩa “thành công một phần về nghiệp vụ”**. Nếu xử lý ba thao tác mà chỉ hai thành công, không tự dùng 206 làm mã chung cho “partial business success”; hãy định nghĩa hợp đồng API thích hợp.

## 3. Nhóm 3xx — điều hướng, lựa chọn hoặc tái sử dụng cache

Các mã 3xx thường bị gọi chung là redirect, nhưng **304 Not Modified không phải lệnh chuyển tới URL mới**.

| Mã | Ý nghĩa chủ yếu | Method sau khi client làm theo `Location` |
| --- | --- | --- |
| **301 Moved Permanently** | Tài nguyên có URI mới lâu dài | Client hiện đại có thể đổi POST thành GET theo lịch sử tương thích; không dựa vào 301 khi bắt buộc giữ method. |
| **302 Found** | Tạm thời có URI khác | Tương tự 301, client có thể đổi POST thành GET theo lịch sử tương thích. |
| **303 See Other** | Xem kết quả ở URI khác, thường sau POST | Với chuyển hướng thông thường, dùng GET hoặc HEAD để truy xuất tài nguyên đích. |
| **304 Not Modified** | Representation đã cache vẫn hợp lệ sau yêu cầu có điều kiện | Không tải response body mới; tái sử dụng bản cache theo quy tắc cache. |
| **307 Temporary Redirect** | Chuyển hướng tạm thời | **Không đổi method** khi tự động chuyển hướng. |
| **308 Permanent Redirect** | Chuyển hướng lâu dài | **Không đổi method** khi tự động chuyển hướng. |

### 3.1. Redirect và Location

Ví dụ di chuyển endpoint trong thời gian chuyển tiếp:

~~~http
HTTP/1.1 308 Permanent Redirect
Location: /api/v2/books/42

~~~

Client có thể gửi lại request tới URI mới, vẫn giữ method và ngữ nghĩa body theo quy tắc của 308. Với POST có side effect, hãy lưu ý chuyện **gửi lại request có thể thực thi lần nữa**; redirect không thay thế cơ chế idempotency trong thiết kế API.

Ví dụ **Post/Redirect/Get** khi ứng dụng có form trình duyệt: POST lưu dữ liệu thành công, server trả 303 cùng `Location: /orders/42`, browser sau đó GET trang kết quả. Cách này giúp tránh chuyện browser refresh trực tiếp gửi lại POST ở trang kết quả.

Không dùng 301/308 một cách tùy tiện cho URL tạm thời vì client/cache có thể ghi nhớ chuyển hướng lâu dài.

### 3.2. 304 Not Modified và cache có điều kiện

Một ví dụ chuẩn với ETag:

~~~http
GET /api/books/42 HTTP/1.1
Host: api.example.test
If-None-Match: "book-v7"

~~~

Nếu server xác định representation hiện tại khớp ETag:

~~~http
HTTP/1.1 304 Not Modified
ETag: "book-v7"

~~~

Client/cache sử dụng representation đã lưu phù hợp thay vì nhận body mới. **304 không chứa message content**, và cũng không nói “tài nguyên chưa từng thay đổi”. Nó chỉ phản ánh kết quả đánh giá điều kiện của request tại thời điểm đó. Nếu cache không có representation tương ứng, 304 riêng lẻ không đủ để dựng dữ liệu.

### 3.3. Redirect API: ba câu hỏi trước khi chọn mã

1. URI mới có **lâu dài** hay chỉ tạm thời? Lâu dài cân nhắc 301/308; tạm thời cân nhắc 302/307.
2. Có cần **giữ nguyên method/body** khi client đi tiếp không? Nếu có, 307/308 diễn đạt rõ hơn.
3. Muốn client **GET một trang/kết quả sau POST**? 303 thường thể hiện chủ đích rõ nhất.

Đừng lẫn chuyển hướng HTTP 3xx với việc Spring MVC trả tên view hay internal forward. Browser chỉ thực hiện request mới khi nhận redirect thật với status và Location thích hợp.

## 4. Nhóm 4xx — request không được đáp ứng

4xx thường hướng client tới việc **thay đổi request, cung cấp quyền phù hợp hoặc chờ điều kiện cho phép**. Từ “Client Error” không có nghĩa mọi lỗi 4xx chắc chắn do lập trình viên frontend gây ra: quy tắc bảo mật, tồn tại tài nguyên và hợp đồng API đều ảnh hưởng.

| Mã | Tên | Tình huống điển hình |
| --- | --- | --- |
| **400 Bad Request** | Bad Request | Request sai cú pháp hoặc không hợp lệ theo cách server không thể/không muốn xử lý. |
| **401 Unauthorized** | Unauthorized | Thiếu hoặc không hợp lệ thông tin xác thực đối với tài nguyên được bảo vệ; phản hồi 401 phù hợp phải có `WWW-Authenticate`. |
| **403 Forbidden** | Forbidden | Server hiểu request nhưng từ chối thực hiện, thường do không đủ quyền. |
| **404 Not Found** | Not Found | Không tìm thấy representation/tài nguyên đích theo cách server sẵn sàng tiết lộ; cũng có thể cố ý che giấu tài nguyên bị cấm. |
| **405 Method Not Allowed** | Method Not Allowed | URI có handler/tài nguyên nhưng method không được hỗ trợ tại đích; response phải có `Allow` liệt kê method hiện được hỗ trợ. |
| **406 Not Acceptable** | Not Acceptable | Server không tìm được representation phù hợp với điều kiện thương lượng nội dung client gửi và từ chối trả loại khác. |
| **408 Request Timeout** | Request Timeout | Server không nhận được request đầy đủ trong thời gian sẵn sàng chờ. |
| **409 Conflict** | Conflict | Request xung đột với trạng thái hiện tại của tài nguyên/hệ thống. |
| **410 Gone** | Gone | Tài nguyên đã không còn và tình trạng mất được biết là có khả năng lâu dài. |
| **412 Precondition Failed** | Precondition Failed | Điều kiện như `If-Match` không thỏa; hay dùng để tránh ghi đè bản cập nhật đồng thời. |
| **413 Content Too Large** | Content Too Large | Nội dung request quá lớn so với mức server chấp nhận. |
| **415 Unsupported Media Type** | Unsupported Media Type | Media type/encoding của request content không được endpoint hỗ trợ. |
| **422 Unprocessable Content** | Unprocessable Content | Content có loại và cú pháp hiểu được nhưng không thể xử lý theo chỉ dẫn/ngữ nghĩa nội dung. |
| **429 Too Many Requests** | Too Many Requests | Client đã gửi quá nhiều request trong khoảng thời gian theo chính sách giới hạn. |

### 4.1. Phân biệt 400, 415 và 422

Một `POST /api/books` nhận JSON:
- Body JSON **sai cú pháp** như `{"title":`: thường trả **400**, vì không deserialize được.
- Client gửi `Content-Type: application/xml` nhưng endpoint chỉ chấp nhận JSON: thường trả **415**.
- JSON hợp lệ và có cấu trúc server hiểu, nhưng vi phạm một quy tắc ngữ nghĩa (ví dụ một giá trị không thể xử lý): **422** có thể phù hợp tùy hợp đồng API. Nhiều API dùng **400** cho validation và vẫn hoàn toàn hợp lý khi quy định nhất quán. Spring MVC không tự động dùng 422 cho mọi lỗi validation.

### 4.2. Phân biệt 401 và 403

- **401:** client chưa cung cấp hoặc cung cấp chứng thực không hợp lệ. Với cơ chế Bearer token, server bảo vệ endpoint có thể trả `WWW-Authenticate: Bearer`, kèm tham số khi thích hợp. HTTP gọi mã này “Unauthorized” nhưng thực tế liên quan **authentication**.
- **403:** server biết request nhưng từ chối thực hiện, ví dụ tài khoản đã xác thực nhưng thiếu quyền quản trị. Việc thêm token cũng không nhất thiết khắc phục.
- **404:** hệ thống bảo mật có thể chủ động dùng 404 thay 403 để tránh tiết lộ tài nguyên được bảo vệ có tồn tại hay không.

Không suy luận chính sách xác thực chỉ từ mã trạng thái của một endpoint chưa biết.

### 4.3. Phân biệt 404, 405 và 410

- `GET /api/books/999` khi không có sách 999: thường là **404**.
- `POST /api/books/42` khi endpoint này chỉ cho GET: thường là **405** (nếu cấu hình routing có route tương ứng và framework nhận diện khác biệt method).
- Tài liệu đã bị xóa vĩnh viễn và server biết điều đó: **410** có thể diễn đạt rõ hơn; không bắt buộc mọi tài nguyên bị xóa phải trả 410.

Một số API dùng 404 cả khi method/path không có route; hãy đối chiếu cấu hình router và `Allow` thay vì đoán.

### 4.4. Phân biệt 409 và 412 khi cập nhật đồng thời

**409 Conflict:** ví dụ người dùng cố tạo tài khoản với username đã tồn tại, hoặc chuyển trạng thái đơn hàng trái với trạng thái hiện tại.

**412 Precondition Failed:** ví dụ client lấy tài nguyên kèm ETag rồi gửi:

~~~http
PUT /api/books/42 HTTP/1.1
Host: api.example.test
If-Match: "book-v7"
Content-Type: application/json

{"title":"Revised"}
~~~

Nếu representation hiện tại có ETag khác, server có thể trả 412, ngăn cập nhật đè lên thay đổi của người khác. 409 diễn tả xung đột tổng quát; 412 nhấn vào **HTTP precondition** đã thất bại.

### 4.5. 429 và Retry-After

429 phù hợp khi client vượt chính sách rate limit; response **có thể** có `Retry-After` hướng dẫn thời điểm thử lại:

~~~http
HTTP/1.1 429 Too Many Requests
Retry-After: 60
Content-Type: application/problem+json

{"type":"about:blank","title":"Too Many Requests","status":429}
~~~

`Retry-After: 60` có nghĩa đợi khoảng 60 giây trước khi retry, theo hướng dẫn của server; không cam kết lần sau chắc chắn thành công. Client cần backoff, giới hạn tốc độ và cân nhắc idempotency trước khi gửi lại request có side effect.

## 5. Nhóm 5xx — server hoặc hạ tầng không hoàn thành request

Các mã 5xx thường báo vấn đề ở phía xử lý request, server hoặc thành phần trung gian. Phía client có thể retry một số trường hợp, nhưng retry mù quáng dễ tạo tác dụng phụ hoặc làm server đang lỗi quá tải hơn.

| Mã | Tên | Ý nghĩa |
| --- | --- | --- |
| **500 Internal Server Error** | Internal Server Error | Server gặp điều kiện bất ngờ và không hoàn thành request; đây là mã tổng quát khi không có status cụ thể thích hợp hơn. |
| **501 Not Implemented** | Not Implemented | Server không hỗ trợ chức năng cần để thực hiện request, ví dụ không nhận biết hoặc không hỗ trợ method ở cấp server. |
| **502 Bad Gateway** | Bad Gateway | Gateway/proxy nhận phản hồi không hợp lệ từ upstream mà nó cần để hoàn thành request. |
| **503 Service Unavailable** | Service Unavailable | Dịch vụ tạm không sẵn sàng, ví dụ quá tải hoặc bảo trì; có thể dùng `Retry-After`. |
| **504 Gateway Timeout** | Gateway Timeout | Gateway/proxy không nhận phản hồi kịp thời từ upstream. |

### 5.1. Phân biệt 500, 502, 503 và 504

Giả sử luồng:

~~~text
Mobile App → API Gateway → Spring Web → Database / Service khác
~~~

- **500:** một lỗi không được xử lý thích hợp trong service Spring có thể trở thành 500. Ví dụ NullPointerException bất ngờ hoặc mã xử lý lỗi bị hỏng; thực tế do bộ xử lý ngoại lệ quyết định.
- **502:** gateway nhận upstream response sai hoặc không hợp lệ và trả Bad Gateway.
- **503:** backend tạm ngừng phục vụ do bảo trì/quá tải, hoặc gateway không có backend khả dụng và sử dụng 503 theo cấu hình.
- **504:** gateway chờ upstream vượt thời gian cho phép.

**Không tự động gọi mọi timeout là 504.** Nếu client kết nối bị ngắt mà không nhận được HTTP response, client thấy lỗi mạng/timeout chứ không có status code. Nếu chính Spring ứng dụng trả lời timeout theo hợp đồng khác, mã còn phụ thuộc cách triển khai.

**501 không phải 405:** nếu method được server hiểu nhưng tài nguyên đích không hỗ trợ method đó, 405 thích hợp hơn; nếu server không hỗ trợ chức năng/method ở mức rộng hơn, 501 có thể thích hợp.

### 5.2. Retry thông minh

Khi 5xx:
1. Ghi nhận status, request ID/tracing ID (nếu có), khoảng thời gian và endpoint.
2. Nếu có `Retry-After`, tôn trọng tín hiệu đó khi phù hợp.
3. Dùng exponential backoff kèm jitter, giới hạn số lần thử và timeout rõ ràng.
4. Chỉ tự động retry những request mà việc gửi trùng **an toàn theo hợp đồng**. GET thường phù hợp; POST tạo giao dịch cần idempotency key hoặc cơ chế chống xử lý trùng trước khi retry.
5. Trên backend, không trả stack trace, access token, SQL chi tiết hay bí mật nội bộ cho client; dùng log/tracing nội bộ để điều tra.

**Status 5xx không chứng minh tác vụ nghiệp vụ chưa xảy ra.** Server có thể đã ghi dữ liệu thành công rồi lỗi khi tạo response; client retry POST mà không kiểm soát trùng có thể tạo hai bản ghi.

## 6. Chọn status cho một API: từ ngữ nghĩa đến quyết định

Bảng dưới đây là các **mẫu thường gặp**, không phải quy tắc bắt buộc theo method. Điều kiện quan trọng nhất vẫn là kết quả thực của request.

| Tình huống API | Mã nên cân nhắc | Lý do |
| --- | --- | --- |
| GET có representation của tài nguyên | 200 | Trả dữ liệu thành công. |
| POST tạo mới và tài nguyên đã được tạo | 201 | Thành công và tạo tài nguyên; gửi Location khi phù hợp. |
| POST nhận job và sẽ xử lý sau | 202 | Mới tiếp nhận, chưa kết luận kết quả cuối. |
| DELETE thành công, không trả body | 204 | Không có nội dung phản hồi. |
| GET có điều kiện, ETag còn phù hợp | 304 | Dùng lại cache; không gửi representation body. |
| GET id không tồn tại | 404 | Không tìm thấy tài nguyên. |
| Request JSON sai cú pháp | 400 | Dữ liệu request không parse được. |
| Request thiếu xác thực cho tài nguyên bảo vệ | 401 | Yêu cầu xác thực với challenge thích hợp. |
| Người dùng thiếu quyền | 403 | Từ chối hành động. |
| POST tạo dữ liệu trùng khóa nghiệp vụ | 409 | Xung đột trạng thái hiện có. |
| If-Match không đúng với bản hiện tại | 412 | HTTP precondition thất bại. |
| Endpoint quá tải tạm thời | 503 | Service chưa sẵn sàng. |

Các phân biệt hữu ích khi thiết kế:
- **200 hay 201?** Chọn 201 nếu request tạo ra tài nguyên và hoàn tất; POST không phải lúc nào cũng dẫn tới 201.
- **200 hay 204?** Chọn dựa trên việc có representation trong body hay không, không dựa vào cảm tính “body ít thông tin”.
- **201 hay 202?** Tài nguyên đã được tạo và xử lý hoàn tất khác với job mới được xếp hàng chờ.
- **400 hay 422?** Cần hợp đồng API rõ ràng cho phân loại lỗi cú pháp và lỗi ngữ nghĩa.
- **409 hay 500?** Xung đột nghiệp vụ dự kiến là 409; lỗi nội bộ bất ngờ có thể là 500.
- **404 hay 204 khi DELETE lần hai?** Cả hai có thể xuất hiện trong các API khác nhau; DELETE idempotent nói về **tác động dự kiến trên tài nguyên**, không bắt buộc mọi lần trả cùng status.

## 7. Trả status trong Spring MVC: ví dụ chạy được

Ví dụ dùng **Java 17+ và Spring Boot 3 với dependency `spring-boot-starter-web`** (bao gồm Spring MVC và JSON serialization thông dụng). Đây là ứng dụng tối giản có thể chạy trong dự án Spring Boot đã cấu hình build; không phải tệp Java độc lập chỉ cần `javac` mà không có thư viện Spring. Đặt toàn bộ mã vào tệp `src/main/java/demo/http/HttpStatusDemoApplication.java`:

~~~java
package demo.http;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class HttpStatusDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(HttpStatusDemoApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/books")
class BookController {
    private final Map<Long, Book> books = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();

    @GetMapping("/{id}")
    public ResponseEntity<Book> find(@PathVariable long id) {
        Book book = books.get(id);
        if (book == null) {
            return ResponseEntity.notFound().build(); // 404
        }
        return ResponseEntity.ok(book); // 200 + JSON
    }

    @PostMapping
    public ResponseEntity<Book> create(@RequestBody CreateBook request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "title must not be blank"); // 400
        }

        long id = nextId.incrementAndGet();
        Book book = new Book(id, request.title());
        books.put(id, book);

        URI location = URI.create("/api/books/" + id);
        return ResponseEntity.created(location).body(book); // 201 + Location
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (books.remove(id) == null) {
            return ResponseEntity.notFound().build(); // 404
        }
        return ResponseEntity.noContent().build(); // 204
    }

    record CreateBook(String title) {}
    record Book(long id, String title) {}
}
~~~

**Cách chạy:** với dự án Spring Boot Gradle đã thêm `implementation 'org.springframework.boot:spring-boot-starter-web'`, chạy ứng dụng bằng `./gradlew bootRun` trên Linux/macOS hoặc `.\gradlew.bat bootRun` trên Windows. App mặc định thường phục vụ ở `http://localhost:8080` nếu không đổi cấu hình cổng. Ứng dụng mẫu lưu dữ liệu **trong bộ nhớ**, khởi động lại sẽ mất dữ liệu; id tăng từ 1 trong một phiên khởi động mới và phụ thuộc thứ tự request thực tế.

Thử lần lượt bằng `curl` trong terminal có cú pháp shell POSIX (hoặc điều chỉnh quote cho PowerShell):

~~~bash
curl -i -X POST http://localhost:8080/api/books \
  -H 'Content-Type: application/json' \
  -d '{"title":"HTTP Essentials"}'

curl -i http://localhost:8080/api/books/1

curl -i -X DELETE http://localhost:8080/api/books/1

curl -i http://localhost:8080/api/books/1

curl -i -X POST http://localhost:8080/api/books \
  -H 'Content-Type: application/json' \
  -d '{"title":""}'
~~~

Với ứng dụng **mới khởi động**, không có request khác và lần POST đầu tiên thành công, kết quả được mong đợi:

| Request | Status | Ý nghĩa/body kỳ vọng |
| --- | --- | --- |
| POST tạo sách có title hợp lệ | **201 Created** | `Location: /api/books/1` và JSON `{"id":1,"title":"HTTP Essentials"}`. |
| GET `/api/books/1` | **200 OK** | JSON mô tả sách vừa tạo. |
| DELETE `/api/books/1` | **204 No Content** | Không có response body. |
| GET sách vừa xóa | **404 Not Found** | Body rỗng do cách dựng `ResponseEntity.notFound().build()` trong ví dụ. |
| POST với title rỗng | **400 Bad Request** | Lỗi `ResponseStatusException`; định dạng body lỗi tùy cấu hình Spring Boot. |

Framework/container có thể thêm các header khác, và serialization có thể chịu ảnh hưởng cấu hình. Đây là ví dụ phục vụ **HTTP status semantics**: dùng `ResponseEntity` để chủ động trả status và body. Đừng xây giả định rằng mọi response đều có cùng `Content-Length`, thời gian hay tập header giữa môi trường khác nhau.

### 7.1. Ba cách Spring MVC đặt status

**Cách 1 — `ResponseEntity`:** khi controller cần điều khiển đồng thời status, headers và body:

~~~java
return ResponseEntity.created(URI.create("/api/books/" + id))
                     .body(book);
~~~

**Cách 2 — `@ResponseStatus`:** khi handler có status tương đối cố định:

~~~java
@PostMapping("/ping")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void ping() {
    // Endpoint minh họa: thành công, không có body.
}
~~~

Đoạn này là ví dụ **phương thức controller riêng lẻ**, chỉ hoạt động khi đặt trong một controller Spring MVC có import thích hợp (`@PostMapping`, `@ResponseStatus`, `HttpStatus`); không phải một chương trình Java chạy độc lập. `@ResponseStatus` cũng dùng được trên một số loại exception tùy thiết kế.

**Cách 3 — exception và xử lý tập trung:** ném `ResponseStatusException` để gắn mã tương ứng, hoặc dùng `@RestControllerAdvice` kết hợp `@ExceptionHandler` để biến exception miền nghiệp vụ thành response chuẩn. Chẳng hạn một lỗi `BookConflictException` trong logic nghiệp vụ có thể được map thành 409. Chỉ map những lỗi **có ý nghĩa được định nghĩa**; tránh biến mọi exception thành 200 hoặc 400.

Ở Spring MVC, các resolver và cấu hình exception handling quyết định status thực tế. Spring Security, filter, proxy/gateway và cấu hình error response cũng có thể ảnh hưởng phản hồi. Do đó log trạng thái HTTP thực tế ngoài ranh giới ứng dụng khi cần điều tra.

### 7.2. Body lỗi chuẩn hóa: Problem Details

Trong hệ sinh thái HTTP, định dạng **Problem Details** (RFC 9457, cập nhật RFC 7807) thường dùng media type `application/problem+json` và những trường chuẩn như `type`, `title`, `status`, `detail`, `instance`.

Ví dụ 409 theo hợp đồng minh họa:

~~~http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "https://api.example.test/problems/book-conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Book identifier already exists",
  "instance": "/api/books/42"
}
~~~

**Phản hồi trên là ví dụ do API lựa chọn**, không phải body mặc định bảo đảm từ Spring. Spring Framework 6 có `ProblemDetail` và hỗ trợ xử lý các lỗi theo hướng này; mức tự động xuất body và các trường phụ thuộc cấu hình Spring MVC/Spring Boot cũng như exception resolver. Status ở HTTP response mới là status ở tầng giao thức; trường `status` trong body nhằm cung cấp dữ liệu mô tả lỗi đồng bộ với response.

Giữ message lỗi đủ hữu ích cho client nhưng không tiết lộ stack trace, mật khẩu, query SQL hay token. Với lỗi validation, trả chi tiết lỗi theo schema ổn định để người dùng sửa đúng trường.

## 8. Debug theo mã trạng thái và đường đi request

Khi chẩn đoán API, nên quan sát:

~~~bash
curl -i http://localhost:8080/api/books/42
~~~

- `-i` hiển thị response headers kèm body, trong đó có status-line khi dùng HTTP/1.x.
- `-v` cho biết thêm thông tin kết nối và request do curl tạo.
- DevTools Network, HTTP client hoặc tracing có thể cho biết redirect chain, request/response headers, payload và thời gian thực hiện.

Một trình tự kiểm tra có ích:

1. **Có status code không?** Nếu không, điều tra DNS, TCP/QUIC, TLS, timeout kết nối, proxy và lỗi gửi/nhận; đừng tự gán 500.
2. **2xx nhưng UI vẫn lỗi?** Kiểm tra body có đúng schema, nội dung có thực sự hợp lệ, 204 có bị parse JSON nhầm, 202 có bị coi là “job đã xong” hay không.
3. **3xx?** Đọc `Location` và `Cache-Control`/ETag khi liên quan; phân biệt redirect chain với 304 cache. Kiểm tra method sau chuyển hướng, tránh chuyển POST thành GET ngoài ý muốn.
4. **4xx?** Đọc method, path, query, `Content-Type`, `Accept`, auth, permission, body, ETag/precondition và chính sách rate limit. 400/401/403/404/409 cần hướng xử lý khác nhau.
5. **5xx?** Tra log/tracing server và gateway/upstream. So sánh 500/502/503/504 để xác định nơi xảy ra sự cố; thiết kế retry có giới hạn, idempotency phù hợp.

Đối với Spring Web, một lỗi do routing/argument conversion xảy ra **trước khi vào business method**, nên breakpoint chỉ trong controller có thể không bắt được. Hãy xem exception handler, filter, security chain, servlet container và proxy khi status bất ngờ.

## 9. Các nhầm lẫn phổ biến

| Quan niệm sai | Cách hiểu đúng |
| --- | --- |
| “2xx nào cũng có response JSON.” | 204 không có content; 202 mới nhận việc; 206 là một phần representation. |
| “POST lúc nào cũng trả 201.” | 201 khi tạo tài nguyên hoàn tất; POST có thể trả 200, 202, 204 hoặc mã lỗi theo ngữ nghĩa. |
| “3xx đều chuyển sang URL khác.” | 304 thông báo dùng lại cache, không phải redirect tới URI khác. |
| “301, 302, 307, 308 chỉ khác số.” | Tính lâu dài và quy tắc giữ/đổi method là khác biệt đáng kể. |
| “401 là đã đăng nhập nhưng thiếu quyền.” | 401 liên quan authentication/challenge; 403 thường là từ chối quyền. |
| “404 chắc chắn tài nguyên không tồn tại.” | Server có thể dùng 404 để không tiết lộ sự tồn tại của tài nguyên bị cấm. |
| “400 và 422 hoàn toàn hoán đổi cho nhau.” | Chúng mang ngữ nghĩa khác; dùng nhất quán với hợp đồng API. |
| “409 là lỗi hệ thống như 500.” | 409 thường biểu đạt xung đột trạng thái mà client có thể xử lý. |
| “5xx nghĩa là thao tác chắc chắn chưa được thực hiện.” | Server có thể đã lưu thay đổi rồi lỗi khi phản hồi; retry cần kiểm soát trùng. |
| “Có timeout thì server đã trả 504.” | Timeout không nhận response HTTP không có status; 504 là phản hồi cụ thể của gateway. |
| “Spring luôn tự trả đúng body lỗi theo tôi nghĩ.” | Status/body phụ thuộc exception resolver, Boot configuration, Security và proxy. |

## 10. Bài tập tự kiểm tra và giải thích

**Bài 1.** API `POST /payments` nhận yêu cầu, gửi xử lý vào hàng đợi nhưng chưa biết kết quả thanh toán. Nên trả 201 hay 202?

**Bài 2.** Client GET một tài liệu với `If-None-Match` và server thấy ETag không thay đổi. Status phù hợp là gì? Có body JSON tài liệu mới không?

**Bài 3.** Client có token hợp lệ nhưng thiếu quyền xóa sách. Nên trả 401 hay 403? Nếu token không hợp lệ thì sao?

**Bài 4.** Gateway không nhận phản hồi upstream kịp thời. Phân biệt 500, 502, 503, 504: mã nào mô tả đúng nhất?

**Bài 5.** Client POST JSON có cú pháp hợp lệ nhưng trùng khóa unique, API muốn client xử lý xung đột. Nên trả 409 hay 500?

**Bài 6.** DELETE một bản ghi thành công và server không cần trả representation. Status hợp lý là gì? Nếu DELETE cùng URI lần nữa trả 404 thì có phá vỡ idempotency của DELETE không?

### Đáp án

1. **202 Accepted**: xử lý chưa kết thúc; phải có cơ chế theo dõi kết quả cuối ngoài status này.
2. **304 Not Modified** khi đáp ứng điều kiện cache phù hợp; không gửi body representation mới.
3. **403 Forbidden** khi có xác thực hợp lệ nhưng thiếu quyền; **401 Unauthorized** cùng challenge thích hợp khi thiếu/sai xác thực. Chính sách bảo mật có thể cố ý dùng 404 để che giấu sự tồn tại.
4. **504 Gateway Timeout**; 500 là lỗi server chung, 502 là upstream phản hồi không hợp lệ, 503 là dịch vụ tạm không sẵn sàng.
5. **409 Conflict** diễn đạt xung đột trạng thái dự kiến; tránh báo 500 cho lỗi nghiệp vụ đã biết.
6. **204 No Content** phù hợp. Lần sau có thể trả 404: tính idempotent mô tả tác động dự kiến của việc gọi lặp, không bắt buộc status hai lần phải giống hệt.

## 11. Tổng kết kiến thức cốt lõi

Khi thiết kế API Spring Web, hãy đi từ **kết quả thật của request** đến status: **2xx** thể hiện thành công (bao gồm đã tạo, mới chấp nhận, không có body), **3xx** thể hiện điều hướng/cache, **4xx** thể hiện request không thể đáp ứng theo điều kiện hiện tại, và **5xx** biểu thị lỗi server hoặc trung gian. Sau đó chọn header và body khớp ngữ nghĩa của status, đặc biệt `Location` với 201/3xx, `WWW-Authenticate` với 401, `Allow` với 405 và `Retry-After` với 429/503 khi áp dụng. Trong Spring MVC, dùng `ResponseEntity`, status annotation và xử lý ngoại lệ để phản hồi nhất quán; luôn xác minh status/headers/body thực tế từ HTTP client.

**Tài liệu đối chiếu:** RFC 9110 (HTTP Semantics), RFC 9111 (HTTP Caching), RFC 9112 (HTTP/1.1), RFC 9457 (Problem Details) và tài liệu Spring Framework Web MVC về `ResponseEntity`, `@ResponseStatus`, `ProblemDetail`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-fundamentals-005">Header, Content-Type, Accept và thương lượng nội dung</a>

<details>
<summary>Xem chi tiết</summary>

# Header, Content-Type, Accept và thương lượng nội dung trong HTTP và Spring Web

Một HTTP request hay response gồm **start-line/status-line, tập header và phần body (nếu có)**. Header cung cấp thông tin để hai phía hiểu cách trao đổi dữ liệu: định dạng body, loại response mong muốn, ngôn ngữ, mã hóa truyền tải, xác thực, cache và nhiều điều kiện khác.

Trong Spring MVC hoặc Spring WebFlux, hai header dễ bị nhầm nhất là **Content-Type** và **Accept**:

- **Content-Type** mô tả **body đang được gửi có dạng gì**. Trong request, client khai báo body gửi tới server. Trong response, server khai báo body trả về cho client.
- **Accept** trong HTTP request mô tả **client chấp nhận kiểu dữ liệu nào cho response**. Nó không khai báo kiểu body client đã gửi.
- **Content negotiation (thương lượng nội dung)** là quá trình chọn một **representation** phù hợp cho cùng một tài nguyên dựa trên khả năng của server và sở thích được client nêu bằng header như Accept, Accept-Language và Accept-Encoding.

Ví dụ:

~~~http
POST /api/messages HTTP/1.1
Host: example.test
Content-Type: application/json
Accept: text/plain

{"text":"Xin chào"}
~~~

Client gửi **JSON** và muốn nhận **plain text**. Việc gửi JSON không buộc server phải trả JSON. Nếu endpoint thực sự có khả năng đọc JSON và trả text/plain, trao đổi này là hợp lệ; nếu endpoint không hỗ trợ kết hợp ấy, server có thể trả lỗi tương ứng.

## 1. Cấu trúc thông điệp và vai trò của header

Một HTTP/1.1 request minh họa:

~~~http
GET /api/books/7 HTTP/1.1
Host: example.test
Accept: application/json
Accept-Language: vi
Authorization: Bearer TOKEN_EXAMPLE
~~~

Request GET trên không có body. Server có thể trả một response như:

~~~http
HTTP/1.1 200 OK
Content-Type: application/json
Content-Language: vi
Cache-Control: private, max-age=60

{"id":7,"title":"HTTP căn bản"}
~~~

Các dòng này là **ví dụ về giao thức**, không phải kết quả tự động bảo đảm từ mọi ứng dụng Spring. Response thực tế có thể có thêm Content-Length, Date, Transfer-Encoding, Vary, headers do proxy thêm và thứ tự khác.

**Phân biệt ba vị trí:**

1. **Start-line/status-line:** phương thức, đường dẫn và phiên bản HTTP (request); hoặc status code (response), theo biểu diễn HTTP/1.x.
2. **Header field:** cặp tên–giá trị có vai trò xác định theo tiêu chuẩn, ví dụ **Content-Type: application/json**.
3. **Body/content:** bytes của representation, có thể là JSON, văn bản, ảnh, multipart... hoặc rỗng.

Trong HTTP/2 và HTTP/3, cách mã hóa trên dây không phải các dòng text HTTP/1.1 y nguyên: có pseudo-headers và framing nhị phân, nhưng **ý nghĩa ngữ nghĩa** của phần lớn field như Content-Type và Accept vẫn được duy trì.

### Quy tắc thực tế về header

- Tên field về ngữ nghĩa **không phân biệt chữ hoa/thường**: Content-Type và content-type chỉ cùng một tên. Khi thao tác qua framework, dùng API header thay vì tự viết parser chuỗi.
- Một số field cho phép **danh sách giá trị** phân tách dấu phẩy, nhưng **không được tự động gộp mọi header** bằng dấu phẩy. Điển hình Set-Cookie có quy tắc riêng.
- Thứ tự đa số header không quan trọng, nhưng thứ tự giữa các giá trị lặp lại của một số field có thể có ý nghĩa; không coi mọi header là danh sách không thứ tự.
- Header mô tả metadata, **không phải bằng chứng bảo mật** về tính đúng đắn của body. Server phải kiểm tra dữ liệu, giới hạn kích thước, xác thực quyền và không tin mù quáng giá trị do client cung cấp.
- Proxy/gateway/load balancer có thể thêm hoặc thay header. Khi debug cần xác định đang quan sát request từ client, sau reverse proxy hay trong ứng dụng.

## 2. Những header thường gặp và cách phân nhóm

| Header | Hướng phổ biến | Ý nghĩa / khi nào dùng |
|---|---|---|
| Content-Type | Request và response có body | Media type của representation hiện tại |
| Accept | Request | Những media type client nhận được trong response |
| Accept-Language | Request | Các ngôn ngữ ưu tiên, ví dụ vi, en |
| Content-Language | Response | Ngôn ngữ của representation trả về |
| Accept-Encoding | Request | Các content coding như gzip, br mà client hiểu |
| Content-Encoding | Response hoặc request có mã hóa | Body đã được áp dụng content coding nào |
| Vary | Response | Các request header làm thay đổi representation, giúp cache chọn đúng phiên bản |
| Authorization | Request | Thông tin xác thực theo scheme, ví dụ Bearer |
| WWW-Authenticate | Response | Challenge cho cơ chế xác thực, thường xuất hiện cùng 401 |
| Cookie / Set-Cookie | Request / response | Truyền cookie về server / thiết lập cookie trên client |
| Cache-Control | Cả hai | Chỉ thị cache, tùy ngữ cảnh request/response |
| ETag / If-None-Match | Response / request | Validator và conditional request hỗ trợ cache |
| Origin | Request | Nguồn (origin) của trình duyệt, quan trọng trong CORS |
| Access-Control-Allow-Origin | Response | Chính sách cho browser chia sẻ response khác origin |
| Location | Response | URI đích cho redirect hoặc resource được tạo |
| Content-Disposition | Chủ yếu response; cũng dùng trong multipart part | Cách hiển thị/tải xuống hoặc mô tả từng multipart part |
| Content-Length | Request/response phù hợp | Kích thước content tính bằng byte khi được xác định, không bằng số ký tự Unicode |

**Không nên dùng dấu hiệu header để suy ra mọi hành vi ứng dụng.** Authorization không bảo đảm request được phép truy cập; Accept-Language không bảo đảm server có bản dịch; Accept-Encoding không bảo đảm server đã bật nén; Content-Type không chứng minh body parse được.

### Content-Type không phải Content-Encoding

~~~http
HTTP/1.1 200 OK
Content-Type: application/json
Content-Encoding: gzip
Vary: Accept-Encoding
~~~

Nội dung có ý nghĩa logic là **JSON**, nhưng bytes được **nén gzip** trên đường truyền. Client giải mã gzip trước khi parse JSON. Content-Type chỉ loại representation sau khi bỏ các content coding; Content-Encoding chỉ cách giải mã bytes đã áp dụng content coding. MIME type **không thay thế** thuật toán nén.

## 3. Content-Type: khai báo kiểu dữ liệu đang gửi

Media type có dạng **type/subtype**, đôi khi kèm tham số:

~~~http
Content-Type: application/json
Content-Type: text/plain; charset=UTF-8
Content-Type: text/html; charset=utf-8
Content-Type: application/problem+json
Content-Type: application/octet-stream
Content-Type: application/x-www-form-urlencoded
Content-Type: multipart/form-data; boundary=BOUNDARY_123
~~~

**type/subtype** nhận diện cách diễn giải body, ví dụ JSON khác XML dù biểu diễn cùng một đối tượng sách. Với media type có hậu tố **+json**, ví dụ application/problem+json, hậu tố thể hiện cấu trúc JSON nhưng vẫn là **media type cụ thể**; client và server cần thống nhất hỗ trợ media type ấy, không suy rằng mọi endpoint chỉ hỗ trợ application/json sẽ chấp nhận tất cả biến thể +json trong mọi điều kiện.

**charset** cho biết character encoding khi media type có tham số tương ứng, điển hình văn bản. Trong ứng dụng hiện đại, JSON trao đổi giữa các hệ thống thường dùng UTF-8; không nên dựa vào việc gắn charset tùy ý trong Content-Type để xử lý mọi vấn đề encoding.

**multipart boundary** ngăn cách từng part trong body. Các part có thể có Content-Disposition, Content-Type riêng. Khi dùng browser FormData hay công cụ HTTP client tạo multipart tự động, **để client tự gắn Content-Type kèm boundary**; tự đặt thủ công **multipart/form-data** mà không có boundary thường làm server không parse được.

### Content-Type trong request

~~~http
POST /api/books HTTP/1.1
Host: example.test
Content-Type: application/json
Accept: application/json

{"title":"HTTP căn bản"}
~~~

Ở đây server phải **giải mã request body theo JSON** nếu endpoint chấp nhận JSON. Khi client gửi request body là chuỗi văn bản thô nhưng tự nhận Content-Type: application/json, phép parse JSON có thể thất bại (thường 400 cho dữ liệu không hợp lệ). Không được coi Content-Type là công cụ tự chuyển đổi nội dung.

### Content-Type trong response

~~~http
HTTP/1.1 200 OK
Content-Type: text/plain; charset=UTF-8

OK
~~~

Server nói body hiện tại là văn bản. Nếu trả JSON nhưng gắn text/plain, client hoặc browser có thể diễn giải sai; chính sách MIME sniffing của browser cũng có thể ảnh hưởng. Với web content, cấu hình Content-Type chính xác và cân nhắc **X-Content-Type-Options: nosniff** để ngăn browser đoán type trong những trường hợp liên quan.

**Khi GET không gửi body**, thông thường **không cần Content-Type trên request**; nếu muốn nhận JSON, hãy gửi **Accept: application/json**. Header Content-Type không phải “loại API đang gọi” và cũng không đồng nghĩa với kết quả mong muốn.

## 4. Accept: mô tả những response client có thể nhận

Cú pháp điển hình:

~~~http
Accept: application/json
Accept: text/plain
Accept: text/html, application/json
Accept: application/json, text/plain;q=0.5
Accept: application/*;q=0.8, text/plain;q=0.6
Accept: */*
~~~

**Ý nghĩa:**

- **application/json:** chấp nhận JSON.
- **text/plain:** chấp nhận văn bản thuần.
- **application/\***: chấp nhận các subtype trong type application.
- **\*/\***: chấp nhận mọi media type.
- **q:** hệ số mức độ ưu tiên (quality weight), mặc định **1** khi không khai báo, từ **0** đến **1**. **q=0** nghĩa là không chấp nhận loại được biểu đạt ở media range tương ứng.
- **Mức độ cụ thể:** media range khớp cụ thể và các tham số có thể ảnh hưởng cách xác định ứng viên phù hợp; không đơn giản chọn “phần tử đầu tiên của header”.

Ví dụ request:

~~~http
Accept: application/json;q=0.9, text/plain;q=0.5
~~~

Client ưu tiên JSON so với plain text. Nếu server có thể trả cả hai, logic thương lượng có thể chọn JSON theo độ ưu tiên, sau khi xét media type khả dụng và các quy tắc phù hợp. Nếu server **chỉ hỗ trợ text/plain**, trả plain text vẫn hợp lệ vì client cho phép q=0.5.

**Không phải server buộc trả media type đầu tiên trong danh sách.** Accept mô tả giới hạn và sở thích của client; server còn phải có khả năng tạo representation. Các thuật toán chi tiết về xếp hạng có thể khác giữa framework/server, vì vậy không nên tự phát biểu một công thức sắp xếp tuyệt đối áp dụng mọi trường hợp.

### Khi không có Accept

Thiếu Accept thường có ý nghĩa rằng client **không chỉ rõ ràng buộc định dạng response**; server có thể chọn loại mặc định. Công cụ dòng lệnh **curl** thường gửi **Accept: */*** theo mặc định (có thể xem bằng curl -v).

Ví dụ browser khi điều hướng trang HTML thường đề xuất text/html cùng vài media type khác; một lời gọi API từ mã nguồn có thể chủ động đặt Accept: application/json. Vì thế **đừng dựa vào header mặc định của trình duyệt để chứng minh một API hỗ trợ định dạng nào**.

### Accept không đổi request body

~~~http
POST /api/books HTTP/1.1
Content-Type: text/plain
Accept: application/json

sach 1
~~~

Client yêu cầu response JSON nhưng gửi **plain text**. Nếu endpoint chỉ đọc JSON, có thể trả **415 Unsupported Media Type**; việc Accept=application/json không làm body text/plain trở thành JSON. Đây là lỗi cấu hình HTTP client rất thường gặp.

## 5. Content negotiation: một tài nguyên, nhiều representation

Một tài nguyên có thể có các cách biểu diễn:

~~~text
GET /api/status
           |
           | request Accept?
           +--> application/json --> {"status":"ok"}
           +--> text/plain       --> status=ok
           +--> text/html        --> <p>status=ok</p>
           +--> không khớp       --> 406 (nếu server không cung cấp fallback)
~~~

Đây là **mô hình khái niệm**. Để hoạt động, server cần thật sự cài đặt các representation; không phải Spring tự động biết biến mọi object thành JSON, XML, HTML và plain text chỉ vì client yêu cầu.

**Các bước giải thích phía server:**

1. Phát hiện resource theo URL, method và các điều kiện routing khác.
2. Xem request body có tồn tại không, Content-Type của request có được endpoint hỗ trợ không và có converter/decoder đọc được không.
3. Đọc Accept (và các chiến lược cấu hình khác) để xác định media type response mà client mong muốn.
4. Giao giữa **những gì client chấp nhận** với **những gì endpoint và message converter/encoder có thể tạo ra**.
5. Chọn handler/representation phù hợp, serialize body và trả Content-Type của response tương ứng.
6. Nếu không thể chọn, có thể trả **406**; nếu không thể tiêu thụ request body do media type không hỗ trợ, có thể trả **415**. Các loại lỗi khác có thể phát sinh trước hoặc sau quá trình thương lượng.

### Content negotiation chủ động và thương lượng thay thế

**Server-driven / proactive negotiation:** client gửi Accept, Accept-Language, Accept-Encoding; server chọn representation. Đây là cách sử dụng phổ biến của Spring REST controller.

**Agent-driven / reactive negotiation theo nghĩa HTTP:** server có thể cung cấp thông tin lựa chọn hoặc phản hồi như 300/406 để client tự chọn. Chữ **reactive** ở đây là thuật ngữ trong HTTP content negotiation, **không liên quan trực tiếp** đến Spring WebFlux/Reactive Streams.

### Content-Type, Accept và kết quả minh họa

| Request gửi | Server hỗ trợ | Kết quả |
|---|---|---|
| Content-Type JSON, Accept JSON | POST đọc JSON và trả JSON | Thành công nếu body hợp lệ |
| Content-Type JSON, Accept text/plain | POST đọc JSON, trả được text/plain | Thành công nếu có nhánh text/plain |
| Content-Type text/plain, Accept JSON | POST chỉ đọc JSON | Thường **415** |
| Content-Type JSON, Accept XML | POST đọc JSON nhưng chỉ trả JSON | Thường **406** |
| Content-Type JSON, Accept JSON | POST đọc JSON, nhưng body JSON hỏng | Thường **400**, không phải 415 |
| GET không body, Accept JSON | GET trả JSON | Thành công; không cần request Content-Type |
| GET không body, Accept XML | GET chỉ có JSON | Thường **406** |
| GET Accept */* | GET có nhiều representation | Server lựa chọn một representation theo cấu hình; đừng đoán loại nào nếu chưa kiểm tra |

Bảng giả định URL, method, quyền truy cập, handler và cấu hình còn lại đều hợp lệ. HTTP có trường hợp fallback, error handling hoặc media type matching khác; **không nên xem mã lỗi ở bảng là lời hứa tuyệt đối cho mọi web server**.

## 6. Spring MVC: consumes, produces và HttpMessageConverter

Trong **Spring MVC**, controller định tuyến request bằng @RequestMapping và các shortcut như @GetMapping, @PostMapping. Hai thuộc tính quan trọng:

- **consumes:** điều kiện ánh xạ dựa trên **Content-Type của request**. Ví dụ consumes = application/json nghĩa là handler chỉ nhận request representation phù hợp application/json.
- **produces:** điều kiện ánh xạ dựa trên **Accept của request** và khả năng sinh response. Ví dụ produces = application/json biểu thị handler dành cho client chấp nhận JSON.
- **headers:** các điều kiện khớp header khác, ví dụ headers = "X-Client-Version=1", nhưng **nên dùng consumes và produces cho Content-Type/Accept** thay vì xử lý bằng điều kiện headers thủ công.
- **@RequestBody:** yêu cầu Spring đọc/deserialize body thành tham số Java, phụ thuộc converter và media type.
- **@ResponseBody** hoặc **@RestController:** yêu cầu Spring viết giá trị Java thành response body.
- **ResponseEntity:** điều khiển status code, headers và body; Content-Type có thể được chỉ rõ trong response khi cần.

**Luồng đơn giản của Spring MVC:**

~~~text
Client
  -> Servlet container
  -> DispatcherServlet
  -> HandlerMapping lọc path/method/consumes/produces
  -> HandlerAdapter + HttpMessageConverter đọc @RequestBody
  -> Controller gọi business service
  -> ReturnValueHandler + HttpMessageConverter ghi response
  -> Content-Type + HTTP response
~~~

Các converter quen thuộc: **StringHttpMessageConverter** xử lý String/text, **MappingJackson2HttpMessageConverter** xử lý JSON khi Jackson/config tương ứng được khai báo (tên class/converter cụ thể có thể thay đổi theo thế hệ Spring). XML cần dependency và converter phù hợp; không nên chỉ thêm produces="application/xml" rồi kỳ vọng object tự động có XML hợp lệ.

Spring MVC mặc định ưu tiên **Accept header** để suy ra representation được yêu cầu trong các bản Spring hiện đại. Có thể cấu hình thêm chiến lược query parameter nếu cần; **phụ thuộc đuôi .json/.xml của path làm nguồn quyết định media type không còn là mặc định và thường không được khuyến khích** vì mơ hồ routing và rủi ro bảo mật.

## 7. Thực hành Spring Boot + Spring MVC: ví dụ chạy được

Tạo ứng dụng Spring Boot từ **https://start.spring.io/**, chọn **Java**, **Maven**, **Spring Web**, JDK tương thích với phiên bản Boot đã chọn (Java 17+ là mức nền phù hợp cho Boot 3.x/4.x). Chọn package name **example.media** hoặc thay toàn bộ package tương ứng trong hai file sau. Starter MVC được Spring Initializr chọn theo thế hệ Boot (ví dụ Boot 3.x dùng spring-boot-starter-web, còn Boot 4.x dùng spring-boot-starter-webmvc).

**File src/main/java/example/media/MediaDemoApplication.java:**

~~~java
package example.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MediaDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(MediaDemoApplication.class, args);
    }
}
~~~

**File src/main/java/example/media/MediaController.java:**

~~~java
package example.media;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MediaController {

    // Cùng URI, nhưng representation JSON cho client nhận JSON.
    @GetMapping(path = "/greeting",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Message greetingJson() {
        return new Message("Hello Lan");
    }

    // Cùng URI, representation plain text cho client nhận text/plain.
    @GetMapping(path = "/greeting",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String greetingText() {
        return "Hello Lan";
    }

    // Đọc JSON request body và viết JSON response body.
    @PostMapping(path = "/echo",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Message echo(@RequestBody Message input) {
        return new Message(input.text());
    }

    public record Message(String text) {
    }
}
~~~

**Giải thích mã:**

- Hai handler GET có **cùng path và method** nhưng điều kiện produces khác nhau, cho phép chọn representation theo Accept phù hợp.
- Với GET JSON, Spring và Jackson serialize **Message** thành JSON; với GET plain text, Spring xử lý String như văn bản.
- POST /echo dùng consumes để nhận JSON và produces để trả JSON; @RequestBody chuyển JSON thành record Message.
- Không có database, network client hoặc dependency ngoài Spring Web; ví dụ tập trung đúng vào header/media type.
- Controller ở package **example.media**, cùng package/nhánh con của lớp @SpringBootApplication, giúp component scan tìm thấy.

### Khởi động

Trong thư mục dự án Maven đã giải nén:

~~~bash
# macOS / Linux
./mvnw spring-boot:run
~~~

Trên Windows PowerShell hoặc Command Prompt, chạy lệnh tương đương:

~~~powershell
.\mvnw.cmd spring-boot:run
~~~

Chạy các lệnh curl sau từ **một terminal thứ hai**. Mỗi ví dụ yêu cầu ứng dụng đang lắng nghe tại http://localhost:8080 và chưa có ứng dụng khác chiếm cổng.

### Trường hợp A — GET yêu cầu JSON

~~~bash
curl -i -H "Accept: application/json" "http://localhost:8080/api/greeting"
~~~

Status và response media type dự kiến:

~~~http
HTTP/1.1 200
Content-Type: application/json
~~~

Body JSON dự kiến:

~~~json
{"text":"Hello Lan"}
~~~

Các header phụ, charset và dòng status chi tiết tùy phiên bản/server; nội dung JSON trên mới là phần cần kiểm tra.

### Trường hợp B — GET yêu cầu plain text

~~~bash
curl -i -H "Accept: text/plain" "http://localhost:8080/api/greeting"
~~~

Response body dự kiến:

~~~text
Hello Lan
~~~

Response Content-Type thuộc **text/plain** (có thể kèm tham số charset). Cùng URI nhưng hai representation khác nhau; đây là ví dụ rõ nhất của **content negotiation theo media type**.

### Trường hợp C — GET yêu cầu XML không được hỗ trợ

~~~bash
curl -i -H "Accept: application/xml" "http://localhost:8080/api/greeting"
~~~

Status dự kiến:

~~~http
HTTP/1.1 406
~~~

Ứng dụng mẫu không có GET handler phù hợp produces=application/xml nên Spring MVC trả **406 Not Acceptable** theo mặc định. Body lỗi và các response header của lỗi có thể do phiên bản Boot/Spring tùy biến; **không khẳng định JSON lỗi hay nội dung lỗi cố định**.

### Trường hợp D — POST gửi JSON, nhận JSON

~~~bash
curl -i -X POST "http://localhost:8080/api/echo" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  --data '{"text":"Hello Spring"}'
~~~

Body JSON dự kiến:

~~~json
{"text":"Hello Spring"}
~~~

Chú ý hai header có hai nhiệm vụ riêng: **Content-Type** giúp chọn/đọc body request; **Accept** giúp chọn/ghi body response.

**Windows PowerShell:** có thể chạy cùng cú pháp với **curl.exe** và đặt lệnh trên một dòng để tránh khác biệt dấu xuống dòng hoặc alias curl trong Windows PowerShell 5.1:

~~~powershell
curl.exe -i -X POST "http://localhost:8080/api/echo" -H "Content-Type: application/json" -H "Accept: application/json" --data '{"text":"Hello Spring"}'
~~~

Nếu môi trường PowerShell cũ thay đổi quoting khi gọi native command, dùng file body JSON hoặc cú pháp escape thích hợp của phiên bản shell; điều quan trọng là **bytes gửi đi phải là JSON hợp lệ**. Cũng có thể dùng Postman/HTTPie.

### Trường hợp E — POST gửi sai Content-Type

~~~bash
curl -i -X POST "http://localhost:8080/api/echo" \
  -H "Content-Type: text/plain" \
  -H "Accept: application/json" \
  --data '{"text":"Hello Spring"}'
~~~

Status dự kiến:

~~~http
HTTP/1.1 415
~~~

Dù body trông như JSON, client khai báo text/plain trong khi mapping chỉ consumes application/json. Spring MVC thường dừng ở điều kiện media type, trả **415 Unsupported Media Type**; không cần dựa vào nội dung chuỗi để chọn handler.

### Trường hợp F — POST gửi JSON đúng, yêu cầu response XML

~~~bash
curl -i -X POST "http://localhost:8080/api/echo" \
  -H "Content-Type: application/json" \
  -H "Accept: application/xml" \
  --data '{"text":"Hello Spring"}'
~~~

Status dự kiến:

~~~http
HTTP/1.1 406
~~~

Client gửi body JSON hợp lệ và mapping có thể đọc, nhưng không có representation XML phù hợp với produces. **406** liên quan response được yêu cầu, **415** liên quan media type của request content.

### Trường hợp G — Header đúng nhưng JSON hỏng

~~~bash
curl -i -X POST "http://localhost:8080/api/echo" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  --data '{"text":'
~~~

Status dự kiến:

~~~http
HTTP/1.1 400
~~~

Định dạng được khai báo **đúng** nhưng payload JSON **sai cú pháp**. HttpMessageConverter không parse được, thường trả **400 Bad Request**. Như vậy không thể suy mọi lỗi body thành 415.

### Trường hợp H — Thiếu Accept hoặc Accept: */*

~~~bash
curl -i "http://localhost:8080/api/greeting"
~~~

Với **hai mapping produces khác nhau**, client không chỉ rõ định dạng mong muốn. Framework có thể chọn theo quy tắc mapping/converter thực tế; **không dự đoán cứng JSON hay text/plain** trong tình huống mở này. Khi client yêu cầu một representation cụ thể, hãy đặt Accept rõ ràng để kiểm thử có tính xác định.

## 8. Cách Spring xử lý @RequestBody, @ResponseBody và converter

Có bốn khái niệm phối hợp nhưng độc lập:

**(1) Handler mapping:** xác định method controller nào đủ điều kiện path, HTTP method, consumes, produces và các ràng buộc khác.

**(2) Request decoding:** @RequestBody nhờ một reader/converter tương thích media type cùng kiểu Java đích. Nếu header đúng nhưng payload sai cấu trúc (JSON hỏng), lỗi phát sinh **trong quá trình đọc**, không nhất thiết là lỗi mapping.

**(3) Return value / response encoding:** @RestController đưa return value vào body. Framework tìm converter hỗ trợ kiểu Java và media type được chọn; ví dụ Jackson với JSON, String converter với text/plain.

**(4) Exception resolution:** lỗi routing, media type, deserialize và nghiệp vụ có thể được ánh xạ sang HTTP status. @RestControllerAdvice/@ExceptionHandler có thể **tùy biến status/body lỗi**, nhưng cần giữ đúng ý nghĩa giao thức.

**Không suy ra JSON chỉ vì trả Java object.** Nếu không có converter tương thích hoặc dependency bị loại, endpoint có thể không tạo được representation như mong đợi. Ngoài ra **ResponseEntity.ok(...).contentType(...)** đặt explicit response Content-Type; cần bảo đảm **body thật sự trùng loại được tuyên bố** và không mâu thuẫn với điều kiện produces.

### consumes và produces khác @RequestHeader

~~~java
@GetMapping(path = "/example",
        headers = "X-Channel=mobile",
        produces = MediaType.APPLICATION_JSON_VALUE)
public Message example() {
    return new Message("Hello");
}
~~~

Đây là **đoạn method minh họa đặt trong MediaController**, không phải file Java độc lập. Method chỉ phù hợp khi client gửi **X-Channel: mobile** và Accept phù hợp JSON. Trong trường hợp không có header tùy chỉnh, lỗi mapping không nhất thiết là 406/415; cần kiểm tra điều kiện nào không khớp. Dùng **@RequestHeader("X-Channel")** để nhận giá trị header làm tham số khi cần, với cơ chế required/defaultValue rõ ràng.

## 9. WebFlux cũng thương lượng nội dung nhưng dùng reactive codecs

Spring WebFlux dùng những annotation tương tự trong **annotated controllers**:

~~~java
@PostMapping(path = "/echo",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
public Mono<Message> echo(@RequestBody Mono<Message> input) {
    return input.map(message -> new Message(message.text()));
}
~~~

**Chỉ là method minh họa**; đoạn này cần đặt trong WebFlux @RestController có import Reactor Mono, MediaType, PostMapping, RequestBody và record Message thích hợp. Trong WebFlux, **HttpMessageReader/HttpMessageWriter (codecs)** đọc/ghi nội dung reactive thay vì chuỗi converter đồng bộ đặc trưng của MVC.

Điểm cần nhớ:

- **Content-Type/Accept, consumes/produces, HTTP 406/415** giữ nguyên ý nghĩa giao thức ở cả MVC và WebFlux.
- WebFlux có thể phản hồi với **Mono/Flux**, streaming và non-blocking I/O; điều đó không thay đổi vai trò hai header.
- **Content-Type: text/event-stream** thường gắn với Server-Sent Events (SSE); client thường dùng **Accept: text/event-stream** khi muốn stream. Streaming còn cần cấu hình flush, serialization, timeout, proxy phù hợp.
- **Flux** nhiều phần tử không đồng nghĩa với một JSON document hợp lệ nếu gửi từng fragment tùy ý. Chọn media type serialization thích hợp: application/json (ví dụ một JSON array khi writer hỗ trợ), application/x-ndjson cho các JSON record phân cách dòng hoặc text/event-stream cho SSE, theo contract API cụ thể.

**Lưu ý:** phép thương lượng không biến blocking API thành reactive. Nếu service gọi JDBC/JPA blocking ngay trên event-loop, dùng WebFlux có thể gây nghẽn bất kể headers đã cấu hình đúng.

## 10. Accept-Language, Accept-Encoding và Vary

Content negotiation không chỉ xoay quanh JSON/XML. Có thể một resource có nhiều phiên bản ngôn ngữ hoặc content coding:

~~~http
GET /guide HTTP/1.1
Accept: text/html
Accept-Language: vi, en;q=0.7
Accept-Encoding: br, gzip;q=0.8
~~~

Server có thể chọn HTML tiếng Việt, nén Brotli nếu hỗ trợ, và trả:

~~~http
HTTP/1.1 200 OK
Content-Type: text/html; charset=UTF-8
Content-Language: vi
Content-Encoding: br
Vary: Accept-Language, Accept-Encoding

[body đã nén, không thể đọc trực tiếp như text]
~~~

Đây là **ví dụ thiết kế**, không phải header Spring Boot mặc định tự thêm tất cả. Việc trả nội dung theo ngôn ngữ và nén HTTP cần cấu hình thích hợp tại ứng dụng hoặc proxy.

### Vary cần cho cache đúng phiên bản

Giả sử **GET /guide** trả tiếng Việt nếu Accept-Language: vi và trả tiếng Anh nếu Accept-Language: en. Nếu shared cache chỉ khóa theo URL, có nguy cơ trả **bản tiếng Việt cho client muốn tiếng Anh**. Header **Vary: Accept-Language** yêu cầu cache tính đến header ấy khi chọn bản lưu. Nếu body còn được thay đổi bởi Accept, có thể cần **Vary: Accept**. Nếu nén thay đổi representation trên dây, có thể cần **Vary: Accept-Encoding**.

**Vary là khai báo cache key**, không có tác dụng tự tạo bản dịch, nén, hay tự định tuyến controller. Chỉ khai báo những header thực sự có ảnh hưởng đến representation; khai báo không chính xác có thể làm giảm hit rate hoặc gây cache trả sai. Khi có dữ liệu riêng theo người dùng, cần thêm chính sách cache phù hợp (Cache-Control: private/no-store, v.v.), không dựa riêng vào Vary.

### Accept-Language là sở thích, không phải lệnh tuyệt đối

Sở thích ngôn ngữ của browser có thể không đại diện cho lựa chọn thực tế. Nếu người dùng đã chọn ngôn ngữ rõ ràng trong UI/profile, thường ưu tiên lựa chọn ấy; nếu dùng Accept-Language, phải có fallback hợp lý. Trong Spring, locale resolution và việc dịch message thông qua MessageSource là cơ chế cần cấu hình hoặc tích hợp; **chỉ gửi Accept-Language không tự biến mọi String thành bản dịch**.

### Accept-Encoding khác Transfer-Encoding

Content-Encoding gzip/ br mô tả **nội dung được mã hóa/nén**. **Transfer-Encoding: chunked** trong HTTP/1.1 nói về cách framing thông điệp trên kết nối; nó không phải media type và không phải thuật toán nén dữ liệu. HTTP/2/3 có framing riêng. Khi debug, phân biệt **content length theo byte**, nén nội dung và cách vận chuyển.

## 11. Form, upload file, JSON và cấu hình client

**JSON API:**

~~~http
POST /api/orders HTTP/1.1
Content-Type: application/json
Accept: application/json

{"productId":5,"quantity":2}
~~~

Trong Spring, dùng @RequestBody DTO để đọc JSON; **@RequestParam** phù hợp với query/form parameters, không có nghĩa sẽ deserialize nguyên JSON object.

**HTML form truyền key/value:**

~~~http
POST /login HTTP/1.1
Content-Type: application/x-www-form-urlencoded

username=lan&remember=true
~~~

Đây là URL-encoded form body, **không phải JSON**, dù cả hai đều chứa thông tin có cấu trúc. Spring MVC có binding phù hợp với form parameters.

**Upload file:**

~~~http
POST /upload HTTP/1.1
Content-Type: multipart/form-data; boundary=ExampleBoundary

--ExampleBoundary
Content-Disposition: form-data; name="title"

Mau
--ExampleBoundary
Content-Disposition: form-data; name="file"; filename="note.txt"
Content-Type: text/plain

Example file content
--ExampleBoundary--
~~~

Đây là body multipart dạng **minh họa giao thức**. Trong chương trình thực tế, dùng **MultipartFile/@RequestPart** tương ứng với cấu hình Spring MVC, đặt giới hạn kích thước upload, validate media type/nội dung và tên file. Không tin content type do người dùng cung cấp để kết luận file vô hại.

### Fetch API trên browser

Khi gửi JSON:

~~~javascript
const response = await fetch("/api/echo", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "Accept": "application/json"
  },
  body: JSON.stringify({ text: "Hello Spring" })
});

if (!response.ok) {
  throw new Error("HTTP " + response.status);
}

const data = await response.json();
console.log(data.text);
~~~

Đoạn JavaScript trên giả định cùng origin và endpoint JSON mẫu đã triển khai; kết quả console khi request thành công là:

~~~text
Hello Spring
~~~

**Lỗi dễ gặp:** gọi response.json() khi server trả HTML/text hoặc status 204 No Content; việc client đặt Accept: application/json **không bảo đảm server luôn trả JSON**, nhất là ở các response lỗi/proxy. Trước khi parse, có thể kiểm tra status và response Content-Type theo hợp đồng API.

Khi gửi **FormData** chứa file, thường **không tự đặt Content-Type**:

~~~javascript
const form = new FormData();
form.append("title", "Mau");
form.append("file", selectedFile);

await fetch("/upload", {
  method: "POST",
  body: form
});
~~~

Đây là đoạn JavaScript minh họa giả định có biến **selectedFile** là File hợp lệ và server có endpoint upload; browser tự sinh multipart boundary.

### Postman, curl và Spring WebClient

Trong Postman chọn **Body → raw → JSON** thường đồng bộ loại body với Content-Type thích hợp, nhưng vẫn phải kiểm tra tab Headers.

Với curl:

- **-H "Content-Type: application/json"** khai báo request body.
- **-H "Accept: application/json"** yêu cầu response JSON.
- **--data** gửi nội dung; kiểm tra Content-Type thực tế vì lệnh curl có mặc định liên quan application/x-www-form-urlencoded khi dùng --data mà không ghi đè.
- **-i** in response headers cùng body; **-v** quan sát request và response, nhưng tránh đăng công khai Authorization/cookie khi chia sẻ log.
- **--compressed** yêu cầu/giải mã nén theo hỗ trợ của curl khi cần quan sát Accept-Encoding.

Với Spring WebClient, có thể dùng **contentType(MediaType.APPLICATION_JSON)** để khai báo body gửi và **accept(MediaType.APPLICATION_JSON)** để yêu cầu response. Hai phương thức vẫn độc lập như HTTP. Mỗi client có thể tự điền header tùy request/body inserter; hãy kiểm tra request thực tế nếu kết quả không như kỳ vọng.

## 12. Những lỗi thường gặp: 400, 404, 405, 406, 415, 500

| Status | Tình huống tiêu biểu | Kiểm tra đầu tiên |
|---|---|---|
| **400 Bad Request** | JSON body hỏng, thiếu tham số bắt buộc, binding sai | Cú pháp body, trường DTO, required parameters |
| **404 Not Found** | Không có route/resource phù hợp | URL, context path, controller registration |
| **405 Method Not Allowed** | URL đúng kiểu nhưng method không được phép | GET/POST/PUT và @GetMapping/@PostMapping |
| **406 Not Acceptable** | Không tạo được representation phù hợp Accept | Accept, produces, message converter, available response types |
| **415 Unsupported Media Type** | Không nhận/đọc được request content theo media type | Content-Type request, consumes, converter/decoder |
| **500 Internal Server Error** | Lỗi server trong xử lý/serialize hoặc lỗi không được handle | Logs, stack trace, serialization, lỗi code |

Đây là **nguyên nhân thường gặp**, không phải duy nhất. Cấu hình @ControllerAdvice, security layer, proxy, middleware và cơ chế riêng của server có thể trả các status khác; không sửa header một cách máy móc trước khi biết lỗi xảy ra ở tầng nào.

### Ví dụ debug 406

Giả sử:

~~~java
@GetMapping(path = "/report",
        produces = MediaType.APPLICATION_JSON_VALUE)
public Report report() {
    return new Report(1);
}
~~~

Đây là **method minh họa** phụ thuộc một kiểu **Report** hợp lệ và controller bao quanh. Client gọi:

~~~http
GET /report HTTP/1.1
Accept: application/xml
~~~

Nếu server chỉ có representation JSON, không có fallback và mapping theo produces, lỗi **406**. Cách xử lý: sửa **Accept** client nếu khách thật sự muốn JSON; hoặc cung cấp representation XML với message writer/converter và hợp đồng API tương ứng nếu cần XML. **Không đổi Content-Type của GET request** để chữa lỗi này.

### Ví dụ debug 415

~~~java
@PostMapping(path = "/report",
        consumes = MediaType.APPLICATION_JSON_VALUE)
public void submit(@RequestBody Report report) {
    // xử lý báo cáo
}
~~~

Đây cũng là **method minh họa**, không độc lập. Client gửi **Content-Type: text/plain**. Nếu muốn gửi JSON thì thay body thành JSON hợp lệ và khai báo đúng **Content-Type: application/json**. Nếu nghiệp vụ phải chấp nhận text/plain, thiết kế handler/reader khác cho loại ấy. **Không đổi Accept** để chữa 415.

### Checklist khi dùng Spring Boot

1. **Request thực gửi là gì?** Dùng curl -v/Postman console/browser DevTools Network để xem **đường dẫn, method, Content-Type, Accept và body bytes**.
2. **Controller ánh xạ thế nào?** Xem @GetMapping/@PostMapping, consumes/produces, headers, param conditions và tiền tố @RequestMapping ở class.
3. **Dependency/converter có đủ không?** Với JSON kiểm tra Jackson/config tương ứng; với XML kiểm tra converter XML đúng phiên bản.
4. **Body thực sự hợp lệ không?** Content-Type: application/json không làm một chuỗi bất kỳ thành JSON.
5. **Lỗi đến từ đâu?** Controller, Spring framework, security filter, gateway hay reverse proxy? Trace request/correlation ID nếu có, đọc logs đáng tin.
6. **Response lỗi có thể cũng phải thương lượng:** nếu @ExceptionHandler chỉ trả một representation nhất định, client có thể gặp kết quả khác dự kiến. Thiết kế error format nhất quán, ví dụ application/problem+json nếu API chọn chuẩn problem details.
7. **Đã test với Accept cụ thể chưa?** Test cả application/json, text/plain, application/xml và */* đúng theo contract; chỉ dựa một request từ browser là chưa đủ.
8. **Cache có tạo nhầm bản không?** Nếu nhiều representation cùng URI, kiểm tra Vary và Cache-Control ở ứng dụng lẫn CDN/proxy.

## 13. Thiết kế API có thương lượng nội dung tốt

**Khai báo hợp đồng rõ ràng:** endpoint nhận media type nào (request Content-Type) và trả media type nào (response Content-Type/Accept). Trong tài liệu OpenAPI, media type nằm ở requestBody.content và responses.<status>.content; đây là một cách giữ thiết kế và kiểm thử nhất quán.

**Đặt produces/consumes khi có giá trị:** nếu endpoint chỉ nhận JSON, khai báo rõ consumes=application/json cho POST/PUT/PATCH có body; nếu endpoint chỉ trả JSON, khai báo produces=application/json. Điều này giúp phân loại lỗi sớm, tài liệu hóa hợp đồng và lựa chọn handler phù hợp. Không gắn consumes cho GET không body chỉ để “API là JSON”.

**Tách DTO khỏi định dạng đường truyền:** business service làm việc trên domain object; controller/serializer chịu trách nhiệm đọc–ghi representation. Khi thêm định dạng XML/text, tránh nhân đôi logic nghiệp vụ.

**Không ép một URL có mọi định dạng:** nhiều representation cùng URI là khả năng của HTTP, nhưng khi hệ thống không có nhu cầu, API chỉ trả JSON là hoàn toàn hợp lý. Thêm XML/HTML vô cớ làm tăng chi phí kiểm thử, bảo trì và bề mặt rủi ro.

**Tài liệu hóa status code thực tế:** client phải xử lý 406/415/400 và response lỗi. Không hứa response JSON ở mọi lỗi nếu proxy hoặc security layer có thể trả HTML/text.

**Đặt cache đúng:** nếu dùng Accept hoặc Accept-Language để chọn nội dung có thể cache, đánh giá Vary và quy tắc cache ngay từ thiết kế, đặc biệt với CDN.

**Cẩn thận header bảo mật và CORS:** Content-Type và Accept không phải cơ chế phân quyền; CORS do browser thực thi dựa trên Origin và response Access-Control-*; CORS không thay cho authentication/authorization. Dùng Content-Type đúng với nosniff thích hợp cho tài nguyên trình duyệt xử lý.

## 14. Câu hỏi tự kiểm tra

**1. Client POST Content-Type: application/json và Accept: text/plain, nghĩa là gì?** Client gửi JSON, muốn response text/plain; server phải hỗ trợ đọc JSON và tạo plain text nếu muốn thành công.

**2. GET /api/books không có body nhưng muốn JSON, đặt header nào?** **Accept: application/json**. Không cần tự thêm request Content-Type để biểu thị loại response.

**3. Vì sao body JSON hợp lệ vẫn nhận 415?** Vì request có thể khai báo Content-Type khác với consumes, không có reader phù hợp, hoặc kiểu nội dung gửi không được endpoint chấp nhận.

**4. Vì sao Accept: application/xml có thể trả 406?** Client yêu cầu XML nhưng server không tạo được representation XML cho route đó, và không chọn fallback.

**5. q=0.5 trong Accept nói điều gì?** Đó là trọng số độ ưu tiên/chất lượng tương đối cho media range; không phải xác suất 50% server trả loại ấy.

**6. Một endpoint Spring trả record Java có luôn sinh XML khi Accept: application/xml không?** **Không**. Cần handler và converter/encoder tương ứng; chỉ có Java object chưa đủ.

**7. Vary: Accept-Language phục vụ điều gì?** Giúp cache phân biệt response biến đổi theo ngôn ngữ được yêu cầu; không tạo bản dịch.

**8. Cùng URL trả JSON và plain text có vi phạm HTTP không?** **Không**. Đây là nhiều representation của một resource, có thể được chọn bằng content negotiation nếu API triển khai.

**9. Content-Encoding: gzip có nghĩa body là loại gzip thay vì JSON không?** **Không**. Content-Type vẫn có thể là application/json; gzip mô tả cách bytes được nén.

**10. Spring MVC và WebFlux có xử lý Accept/Content-Type khác nghĩa không?** **Không**. Cùng chuẩn HTTP; cơ chế nội bộ đọc/ghi có thể khác (converter vs reactive codecs).

## 15. Tài liệu chuẩn để đối chiếu

- [MDN — HTTP headers](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers): phân loại header và ý nghĩa.
- [MDN — Content-Type](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Type): media type, charset, multipart boundary.
- [MDN — Accept](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Accept): media ranges, wildcard và q weighting.
- [MDN — HTTP content negotiation](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Content_negotiation): representation, language/encoding negotiation, Vary.
- [MDN — 406 Not Acceptable](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/406) và [415 Unsupported Media Type](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/415).
- [Spring Framework — Spring MVC Request Mapping](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html): consumes, produces, header conditions.
- [Spring Framework — Spring MVC Content Types](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/content-negotiation.html): cách Spring MVC xác định requested media types.
- [Spring Framework — WebFlux Request Mapping](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-requestmapping.html): semantics tương ứng của reactive web stack.

**Ghi nhớ:** **Content-Type = body này là gì**; **Accept = response tôi nhận được dạng nào**; **consumes = server nhận loại request body nào**; **produces = server có thể trả response media type nào**; **406 = không đáp ứng được định dạng response mong muốn**; **415 = không hỗ trợ media type của request content**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-fundamentals-006">Cookie, phiên làm việc và giới hạn của trạng thái qua HTTP</a>

<details>
<summary>Xem chi tiết</summary>

# Cookie, phiên làm việc và giới hạn của trạng thái qua HTTP

HTTP là giao thức trao đổi **request–response**. Một request thường được xử lý dựa trên method, URL, header, body và những dữ liệu mà server có thể tra cứu. Bản thân HTTP **không tự nhớ** request trước thuộc về ai hoặc điều gì đã diễn ra ở request đó. Muốn có giỏ hàng, đăng nhập, tùy chọn ngôn ngữ hay tiến trình nhiều bước, ứng dụng phải thiết kế cơ chế liên kết các request.

Ba khái niệm cần phân biệt ngay từ đầu:

- **Cookie** là một cặp tên–giá trị cùng các thuộc tính, được server yêu cầu user agent lưu qua `Set-Cookie`, rồi user agent có thể gửi lại bằng `Cookie` khi điều kiện phù hợp. Cookie nằm phía client.
- **Session (phiên làm việc)** là cách ứng dụng liên kết nhiều request trong một khoảng thời gian. Trong mô hình server-side session phổ biến, cookie chỉ mang **session ID**, còn trạng thái thật nằm ở server hoặc session store.
- **Authentication state (trạng thái xác thực)** là việc ứng dụng biết ai đang được xác thực, thường gắn với session hoặc token. Có cookie/session **không đồng nghĩa** người dùng đã đăng nhập.

Bài này tập trung vào cơ chế HTTP, cách trình duyệt vận hành cookie, trạng thái phiên phía server, các giới hạn và hệ quả bảo mật, rồi nối với Spring MVC.

## 1. Vì sao HTTP được gọi là stateless?

Xét hai request độc lập:

```http
GET /catalog HTTP/1.1
Host: shop.example
```

```http
GET /checkout HTTP/1.1
Host: shop.example
```

Chỉ nhìn hai thông điệp này, server không biết chắc cùng một người dùng đã gửi chúng. Kết nối TCP/TLS có thể được tái sử dụng nhưng **kết nối không phải danh tính người dùng**. Client có thể mở nhiều kết nối; proxy, load balancer và HTTP/2/HTTP/3 thay đổi cách các request được truyền mà không làm HTTP tự tạo session nghiệp vụ.

Tính stateless không có nghĩa **server không thể lưu dữ liệu**. Server có thể lưu tài khoản, đơn hàng, giỏ hàng hoặc session vào cơ sở dữ liệu. Điểm mấu chốt là giao thức không tự quy định một bộ nhớ hội thoại giữa các request. Ứng dụng cần cung cấp một cách xác định request hiện tại liên quan đến trạng thái nào.

```text
Request 1: client ── HTTP ──> server  → tạo trạng thái
Request 2: client ── HTTP ──> server  → cần khóa tra cứu trạng thái cũ
                                    ↑
                        Cookie / token / dữ liệu request
```

Có ứng dụng thiết kế **stateless ở tầng ứng dụng**: mỗi request mang token hoặc dữ liệu cần kiểm tra, server không cần lưu session đăng nhập tương ứng. Có ứng dụng dùng **stateful server session**: request mang session ID để server tìm lại dữ liệu phiên. Cả hai vẫn dùng HTTP.

## 2. Cookie đi qua HTTP như thế nào?

### 2.1. Set-Cookie và Cookie là hai hướng khác nhau

Một response ví dụ:

```http
HTTP/1.1 200 OK
Content-Type: text/plain
Set-Cookie: sid=opaqueRandomIdentifier; Path=/; Secure; HttpOnly; SameSite=Lax

Đã tạo phiên.
```

Sau khi nhận response, trình duyệt **có thể lưu** cookie nếu phù hợp với chính sách cookie và ngữ cảnh hiện tại. Ở request sau đến host/path phù hợp:

```http
GET /profile HTTP/1.1
Host: shop.example
Cookie: sid=opaqueRandomIdentifier
```

Lưu ý:

1. `Set-Cookie` là **response header**; `Cookie` là **request header**. Trong `Cookie`, client gửi chủ yếu các cặp `name=value`, **không gửi lại** `HttpOnly`, `Secure`, `SameSite` hay `Path` như thuộc tính.
2. Một response có thể có nhiều header `Set-Cookie`; một request có thể gửi nhiều cặp cookie.
3. Giá trị `sid` ở đây chỉ là chuỗi giả minh họa. Session ID thật cần có tính ngẫu nhiên/khó đoán phù hợp; **không tự dùng username hay số thứ tự làm session ID**.
4. Trình duyệt tự xét có gửi cookie hay không theo host/domain, path, HTTPS, thời hạn, ngữ cảnh site và chính sách riêng. Server không thể giả định mọi client luôn giữ hoặc gửi cookie.
5. JavaScript `fetch` chịu quy tắc credentials/CORS riêng; việc biết URL hợp lệ không bảo đảm request cross-origin tự mang cookie.

Cookie là dữ liệu phía client: bất cứ giá trị nào do client gửi đều phải được coi là **đầu vào không đáng tin cậy**. Cookie thường không được mã hóa hay ký theo mặc định; người dùng có thể sửa cookie của mình, dù `HttpOnly` hạn chế khả năng truy cập qua JavaScript trong trang.

### 2.2. Các thuộc tính cốt lõi

| Thuộc tính | Ý nghĩa thực tế | Cần nhớ |
| --- | --- | --- |
| `Domain` | Giới hạn phạm vi host/domain nhận cookie | Nếu không đặt `Domain`, cookie thường là **host-only**; đặt `Domain=example.com` có thể cho phép các subdomain phù hợp nhận cookie. Không thể dùng để đặt cookie cho domain không được phép. |
| `Path` | Giới hạn path URL được đính kèm cookie | Ví dụ `Path=/account`; **không phải cơ chế phân quyền/bảo mật cô lập** các khu vực ứng dụng. |
| `Secure` | Chỉ gửi cookie trên các kết nối được coi là an toàn theo quy tắc user agent | Trong môi trường triển khai thông thường, dùng HTTPS; một số hành vi dành cho localhost tùy browser. |
| `HttpOnly` | Ngăn JavaScript phía trang đọc/ghi trực tiếp cookie qua các API như `document.cookie` | Trình duyệt **vẫn tự gửi** cookie trong request phù hợp, kể cả request do JavaScript tạo. Không tự chống CSRF hoặc XSS hoàn toàn. |
| `SameSite=Strict` | Giới hạn chặt việc gửi cookie trong ngữ cảnh cross-site | Tăng bảo vệ trước một số CSRF, nhưng có thể ảnh hưởng luồng người dùng đi từ site khác sang. |
| `SameSite=Lax` | Hạn chế đa số request cross-site, vẫn cho một số top-level navigation đủ điều kiện | Phù hợp nhiều session thông thường; **không thay thế CSRF defense đầy đủ**. |
| `SameSite=None` | Cho phép gửi trong các ngữ cảnh cross-site nếu chính sách browser cho phép | Browser hiện đại thường yêu cầu kèm `Secure`; còn chịu hạn chế third-party cookie/partitioning. |
| `Max-Age` | Thời gian lưu cookie tính bằng giây | Nếu cả `Max-Age` và `Expires` có hiệu lực, `Max-Age` được ưu tiên theo chuẩn. |
| `Expires` | Thời điểm hết hạn theo ngày giờ HTTP | Đồng hồ và chính sách browser có thể ảnh hưởng hành vi lưu/xóa. |

Ví dụ cookie tùy chọn ngôn ngữ tồn tại một giờ:

```http
Set-Cookie: lang=vi; Max-Age=3600; Path=/; Secure; SameSite=Lax
```

Ví dụ cookie session mà JavaScript trang không đọc trực tiếp:

```http
Set-Cookie: sid=unpredictableValue; Path=/; Secure; HttpOnly; SameSite=Lax
```

Trong phát triển local qua HTTP, cookie gắn `Secure` có thể không hoạt động như trong môi trường HTTPS thực. Cần thử trong môi trường tương ứng và quan sát header/cookie store thật của trình duyệt.

### 2.3. Session cookie và persistent cookie

- **Session cookie** thường không có `Max-Age` hoặc `Expires`. User agent có thể xóa nó khi kết thúc phiên trình duyệt; chức năng khôi phục phiên có thể khiến cookie còn tồn tại khi mở lại trình duyệt.
- **Persistent cookie** có thời hạn lưu rõ hơn qua `Max-Age`/`Expires`, nhưng vẫn có thể bị người dùng xóa, trình duyệt chặn hoặc bị giới hạn bởi chính sách riêng.
- **Cookie lifetime** và **server session lifetime** là hai đồng hồ khác nhau. Cookie còn ở client không có nghĩa session ID đó còn tồn tại hoặc còn được server chấp nhận.

Xóa cookie thường thực hiện bằng `Set-Cookie` với **đúng tên và phạm vi Path/Domain tương ứng**, đồng thời `Max-Age=0` (hoặc hạn đã qua):

```http
Set-Cookie: sid=; Max-Age=0; Path=/; Secure; HttpOnly; SameSite=Lax
```

Đặt `Max-Age=0` với `Path` hoặc `Domain` khác cookie ban đầu có thể không xóa đúng cookie mong muốn. Với cookie đăng nhập, **chỉ xóa cookie phía client là chưa đủ**: server cũng cần hủy session hoặc thu hồi token theo thiết kế.

### 2.4. “Origin” khác “site”: nguồn gốc nhầm lẫn phổ biến

**Origin** thường được xác định bằng *scheme + host + port*: `https://app.example.com:443` và `https://app.example.com:8443` khác origin. Khái niệm **site** dùng cho SameSite được xây dựng từ scheme và registrable domain theo các quy tắc browser: `https://app.example.com` và `https://api.example.com` có thể cùng site, dù khác origin.

Các hệ quả:

- Cookie **không tách biệt theo port** giống origin. Hai ứng dụng ở các port khác nhau trên cùng host cần rất cẩn thận với tên/phạm vi cookie.
- CORS chủ yếu quyết định JavaScript ở một origin có được đọc response cross-origin hay không, và một số request có được trình duyệt gửi hay không; CORS **không phải hệ thống phân quyền tài nguyên**.
- `SameSite` xem xét quan hệ site, không chỉ quan hệ origin. Hai subdomain cùng site vẫn có thể tạo rủi ro CSRF nếu tin tưởng quá rộng.
- Cấu hình `Domain`, `Path` chỉ ảnh hưởng chọn gửi cookie, không bảo đảm quyền đọc dữ liệu server hay quyền truy cập endpoint.

Nên ưu tiên cookie host-only cho session khi không có nhu cầu chia sẻ giữa subdomain. Với browser hỗ trợ cookie prefix, tên bắt đầu bằng `__Host-` yêu cầu `Secure`, `Path=/` và **không có `Domain`**; `__Secure-` yêu cầu `Secure`. Prefix tăng tính kiểm tra cấu hình nhưng không thay cơ chế xác thực/phân quyền.

## 3. Server-side session hoạt động ra sao?

Ví dụ mô hình session phổ biến:

```text
Lần đầu:
Browser -- POST /preferences (chưa có sid) --> Spring MVC
                                     │
                          session store tạo ID ngẫu nhiên
                                     │
Browser <-- Set-Cookie: JSESSIONID=abc... -----┘

Lần sau:
Browser -- Cookie: JSESSIONID=abc... --> Spring MVC
                                      │
                         tìm ID abc... trong session store
                                      │
                         đọc / ghi các session attributes
```

**Session ID là khóa tham chiếu**, không phải nơi phải chứa toàn bộ thông tin của người dùng. Với Servlet `HttpSession` thông thường, state được giữ phía server dưới dạng attribute như `language=vi` hoặc `cartId=...`. Cách lưu bên trong tùy container, cấu hình và hệ thống lưu session. Spring MVC dựa trên Servlet API; Spring Session là một lựa chọn bổ sung để quản lý/lưu trữ session theo hệ thống khác.

Session thường có ba thời điểm quan trọng:

1. **Tạo**: khi ứng dụng thực sự cần lưu state; trong Servlet, `request.getSession(true)` có thể tạo mới nếu chưa có.
2. **Sử dụng/làm mới**: request có ID hợp lệ được liên kết với session; quy tắc inactivity timeout do container/session infrastructure quyết định.
3. **Hết hạn hoặc hủy**: inactivity timeout, logout, invalidate hoặc xóa dữ liệu ở session store làm ID cũ không còn trỏ đến session hợp lệ.

Không nên coi `JSESSIONID` là “người dùng đã đăng nhập”. Một session của khách chưa xác thực vẫn có thể tồn tại; sau login, ứng dụng cần liên kết session đúng principal và vòng đời xác thực.

### 3.1. Vì sao cần session ID khó đoán?

Nếu session ID bị đoán, đánh cắp hoặc bị ép sử dụng, kẻ tấn công có thể dùng nó để truy cập cùng session khi server chỉ dựa vào ID. Do đó:

- Sinh ID bằng cơ chế an toàn của framework/container; không tự chế từ timestamp, user ID hay counter.
- Luôn ưu tiên HTTPS/TLS; cookie xác thực nên có `Secure`, `HttpOnly` và `SameSite` phù hợp.
- Khi đăng nhập hoặc tăng đặc quyền, **đổi/rotate session ID** để phòng session fixation. Servlet có `HttpServletRequest.changeSessionId()`; Spring Security cũng cung cấp quản lý bảo vệ session fixation khi cấu hình đúng.
- Hết hạn và hủy session phía server khi logout. Đặt inactivity timeout phù hợp, cân nhắc giới hạn thời gian tuyệt đối khi nghiệp vụ cần.
- Không ghi session ID vào URL, log truy cập công khai, analytics hoặc thông điệp lỗi. URL có thể lộ qua lịch sử, referrer, bookmark và log.
- Bảo vệ chống XSS: `HttpOnly` hạn chế đọc cookie qua script, nhưng XSS vẫn có thể thực hiện các hành động dưới ngữ cảnh người dùng hoặc đọc dữ liệu trang.

Nếu cần giữ người dùng đăng nhập lâu dài (“remember me”), đó thường là cơ chế quản lý token/vòng đời riêng, không nên chỉ tăng vô hạn tuổi thọ session.

### 3.2. Cookie do server ký khác với session server-side

Không phải mọi cookie có chữ “session” đều chứa ID tra cứu. Có ba cách thiết kế thường gặp:

| Thiết kế | Client giữ gì? | Server cần lưu trạng thái tương ứng? | Cần kiểm tra gì? |
| --- | --- | --- | --- |
| Server-side session | ID ngẫu nhiên | Có, trong bộ nhớ hoặc session store | ID còn hợp lệ, phiên hết hạn/chưa bị thu hồi, quyền hiện tại |
| Cookie chứa dữ liệu có chữ ký/MAC | Dữ liệu và chữ ký | Không nhất thiết cho từng phiên | Chữ ký, thời hạn, audience/ý nghĩa, xử lý thu hồi nếu cần |
| Token như JWT trong cookie | Token chứa claims | Không nhất thiết cho mỗi request | Chữ ký/thuật toán, issuer, audience, expiry, quyền và chính sách thu hồi |

**Chữ ký/MAC bảo vệ tính toàn vẹn, không tự bảo mật nội dung**: người cầm cookie vẫn có thể nhìn dữ liệu không được mã hóa. Mã hóa và chữ ký là những nhu cầu khác nhau. Token chứa claims không khiến giao thức HTTP trở thành stateful; đây là cách mang thông tin trạng thái/xác thực qua từng request.

Quyết định đặt token trong cookie hay trong `Authorization: Bearer ...` ảnh hưởng cách browser đính kèm credential và mô hình đe dọa. Cookie được gửi tự động theo quy tắc phù hợp nên cần đánh giá CSRF; token JavaScript tự đính kèm thường thay đổi cơ chế CSRF nhưng lại cần bảo vệ nơi lưu token khỏi XSS.

## 4. Giới hạn thực tế của trạng thái qua HTTP

### 4.1. Cookie có giới hạn dung lượng và vòng đời

Cookie không phù hợp làm kho dữ liệu lớn. Browser áp dụng giới hạn về kích thước từng cookie, số cookie trên domain/site và tổng dung lượng, nhưng mức chính xác **khác nhau theo browser và phiên bản**. Quy tắc kinh nghiệm là giữ từng cookie nhỏ (thường quanh vài kilobyte hoặc ít hơn), tránh dồn giỏ hàng hoặc hồ sơ người dùng đầy đủ vào đó.

Cookie phù hợp sẽ được gửi trong **nhiều request**, kể cả request không cần chính dữ liệu ấy, làm tăng kích thước header và chi phí truyền tải. Session ID ngắn thường tốt hơn nhồi dữ liệu dài vào cookie.

Cookie có thể bị xóa hoặc từ chối, user dùng private mode, mở nhiều thiết bị, đổi trình duyệt hoặc chặn third-party cookie. **Cookie không phải bộ nhớ vĩnh viễn**, cũng không xác định duy nhất một con người.

### 4.2. Session phía server tạo bài toán mở rộng

Với một server giữ session trong RAM:

```text
Browser -- sid=abc --> Load Balancer
                          ├── Node A: có session abc
                          └── Node B: không có session abc
```

Nếu request sau chuyển sang Node B, state trong RAM của A có thể không được tìm thấy. Một số lựa chọn:

- **Sticky session**: load balancer cố đưa các request của client về cùng instance; dễ bắt đầu, nhưng có rủi ro khi instance lỗi/thay thế và khó điều phối tải.
- **Shared/distributed session store**: nhiều instance truy cập cùng một kho session phù hợp (ví dụ Redis qua cơ chế như Spring Session), cần tính đến TTL, độ trễ, serialization, tính nhất quán và bảo mật.
- **Stateless authentication**: mỗi request mang token xác minh được; giảm nhu cầu lưu session đăng nhập, nhưng logout/thu hồi trước hạn, thay đổi quyền hoặc token bị lộ lại có khó khăn khác.
- **Lưu dữ liệu nghiệp vụ vào database**: giỏ hàng, đơn hàng và các sự kiện quan trọng thường nên có định danh/dữ liệu bền vững riêng, thay vì chỉ nằm trong session dễ hết hạn.

Một session store dùng chung không tự bảo đảm mọi thao tác **atomic**. Hai request đồng thời có thể cùng đọc rồi ghi một attribute, gây lost update. Khi dữ liệu cần tính nhất quán, dùng transaction, atomic update, versioning hoặc cơ chế đồng bộ tương ứng.

### 4.3. Session không thay database và cơ chế phân quyền

Session được dùng cho state ngắn hạn, nhưng không nên biến thành nơi lưu mọi dữ liệu nghiệp vụ:

- Đơn hàng phải tồn tại sau khi session hết hạn; session có thể chỉ giữ khóa `cartId`.
- Giá, vai trò, quyền hạn hoặc trạng thái thanh toán cần được xác minh phía server ở thời điểm thao tác; không được tin một chuỗi client tự gửi.
- Session không bảo đảm “mỗi người dùng chỉ có một tab”: nhiều tab trong cùng browser/profile thường chia sẻ cookie/session; thay đổi ở tab này có thể ảnh hưởng tab khác.
- Session cũng không bảo đảm “mỗi người dùng chỉ có một thiết bị”: các trình duyệt hoặc thiết bị khác có session ID riêng.
- Session timeout thường dựa trên khoảng không hoạt động; có thể cần thêm absolute expiry, giới hạn số phiên hoặc cơ chế logout trên mọi thiết bị theo yêu cầu.

## 5. Cookie, CSRF, XSS và ranh giới tin cậy

### 5.1. CSRF phát sinh khi credential được gửi tự động

Nếu trình duyệt tự đính kèm session cookie cho một request, một trang độc hại có thể cố khiến browser của người dùng gửi request đến ứng dụng đang đăng nhập. Đó là một nguồn rủi ro **cross-site request forgery (CSRF)** khi endpoint chấp nhận thao tác nhạy cảm mà thiếu bảo vệ thích hợp.

Ví dụ xấu: `/transfer?amount=1000` thực hiện chuyển tiền bằng GET chỉ vì trình duyệt đang có cookie phiên. GET được kỳ vọng **safe** theo ngữ nghĩa HTTP; thay đổi nghiệp vụ có ý nghĩa không nên đặt ở GET.

Biện pháp thường phối hợp:

1. Dùng HTTP method đúng ý nghĩa: GET/HEAD chỉ đọc; POST/PUT/PATCH/DELETE cho thay đổi.
2. Dùng **CSRF token** hoặc biện pháp tương đương đã được framework hỗ trợ cho các thao tác có nguy cơ; không coi `SameSite` là lớp bảo vệ duy nhất.
3. Cấu hình `SameSite` phù hợp và xét thêm `Origin`/`Referer` nơi thích hợp.
4. Tránh bật `Access-Control-Allow-Credentials: true` cùng origin tùy ý; chỉ cho phép các origin thực sự cần và kiểm tra phía server. CORS không thay xác thực/ủy quyền.
5. Không tin rằng mọi request `Content-Type: application/json` đều tự chống CSRF; hành vi browser, preflight, endpoint hỗ trợ content type nào và các kênh khác cần được đánh giá.

**CSRF khác XSS**: CSRF lợi dụng browser tự gửi credential; XSS chạy script độc hại trong ngữ cảnh trang. Một ứng dụng có thể cần phòng cả hai.

### 5.2. `HttpOnly`, `Secure`, `SameSite` giải quyết các nguy cơ khác nhau

- `HttpOnly` hạn chế JavaScript đọc cookie; **không** ngăn browser gửi cookie trên request.
- `Secure` hạn chế truyền cookie qua kết nối không an toàn; **không** khiến giá trị cookie được mã hóa hoặc đáng tin nếu client tự sửa.
- `SameSite` hạn chế một số tình huống cookie được gửi cross-site; **không** thay CSRF token/kiểm tra quyền, đặc biệt với các trường hợp cùng site hoặc lỗ hổng khác.
- Mã hóa TLS bảo vệ dữ liệu **trên đường truyền**; không giải quyết việc lộ cookie qua log, XSS khai thác phiên, malware, sai cấu hình hoặc server bị xâm nhập.
- **Authentication** xác định ai đang truy cập; **authorization** xác định tài khoản đó có được thực hiện thao tác cụ thể hay không. Một session hợp lệ không cho phép bỏ qua kiểm tra quyền ở từng tài nguyên.

Trong production nên thiết lập chính sách cookie, timeout, CSRF, CORS và logout cùng với cấu hình Spring Security phù hợp luồng đăng nhập. Không dùng riêng một annotation controller để tuyên bố endpoint đã an toàn.

## 6. Ví dụ Spring MVC: lưu tùy chọn bằng HttpSession

Ví dụ này thể hiện đúng ranh giới: request đầu tiên tạo session và lưu `language`; những request sau dùng cookie chứa session ID để đọc giá trị đã lưu. Đây là bài thực hành cơ chế phiên, **không triển khai đăng nhập hoặc phân quyền**.

Yêu cầu: **JDK 17+**, **Spring Boot 3.x** với dependency `spring-boot-starter-web` (Spring MVC trên Servlet API `jakarta.servlet`). Đặt file sau tại `src/main/java/demo/session/SessionDemoApplication.java` trong dự án Spring Boot đã cấu hình build. Đây là **một file ứng dụng đầy đủ khi có dependency Spring Boot**, không phải chương trình có thể biên dịch bằng `javac` thuần không có thư viện.

```java
package demo.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class SessionDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(SessionDemoApplication.class, args);
    }
}

@RestController
@RequestMapping("/api")
class PreferenceController {
    record PreferenceInput(String language) {}
    record PreferenceOutput(String language) {}

    @PostMapping("/preference")
    ResponseEntity<Void> save(
            @RequestBody PreferenceInput input,
            HttpServletRequest request) {
        if (input == null ||
                (!"vi".equals(input.language()) &&
                 !"en".equals(input.language()))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "language must be vi or en");
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("language", input.language());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preference")
    PreferenceOutput read(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String language = session == null
                ? null
                : (String) session.getAttribute("language");
        return new PreferenceOutput(language == null ? "vi" : language);
    }

    @DeleteMapping("/session")
    ResponseEntity<Void> signOutOfDemoSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }
}
```

**Giải thích các điểm quan trọng:**

- `getSession(true)` lấy session hiện có hoặc tạo session mới. Container thường phản hồi với `Set-Cookie: JSESSIONID=...` khi cần gửi ID để client dùng lại.
- `getSession(false)` chỉ trả session hiện có, hoặc `null` nếu không có. GET không tạo session ngoài ý muốn.
- `setAttribute("language", ...)` lưu dữ liệu vào session phía server; cookie thông thường chỉ chứa session ID, **không chứa `language=en`**.
- `invalidate()` hủy session phía server. Browser có thể vẫn giữ cookie chứa ID cũ, nhưng ID đó không còn truy cập được session cũ.
- POST trả `204 No Content`, GET trả JSON và status 200, DELETE trả 204. Các trạng thái này do controller chỉ định hoặc được Spring MVC chọn theo quy tắc mapping.
- Hai request đồng thời ghi chung attribute trên cùng session có thể **ghi đè nhau**; ví dụ chưa xử lý xung đột cập nhật. Ở tình huống nghiệp vụ phức tạp, cần thiết kế đồng thời và nhất quán dữ liệu.
- Endpoint DELETE này minh họa hủy session; nếu dùng cho ứng dụng thật có cookie đăng nhập thì phải tích hợp Spring Security, CSRF defense, và quy trình logout phù hợp.

### 6.1. Tự kiểm tra bằng curl và cookie jar

Giả sử ứng dụng chạy tại `http://localhost:8080`. Ví dụ lệnh sau theo cú pháp shell Bash; trên Windows PowerShell nên gọi `curl.exe` và điều chỉnh cách đặt dấu nháy JSON nếu cần.

**A. GET chưa gửi cookie** — chưa có session nên nhận mặc định:

```bash
curl -i http://localhost:8080/api/preference
```

Response body tương đương:

```json
{"language":"vi"}
```

**B. POST tạo session**, lưu cookie server gửi về vào `cookies.txt`:

```bash
curl -i -c cookies.txt -X POST \
  http://localhost:8080/api/preference \
  -H 'Content-Type: application/json' \
  -d '{"language":"en"}'
```

Status mong đợi: `204 No Content`. Trong một thiết lập Servlet thông thường, response khi tạo session có header gần giống:

```http
Set-Cookie: JSESSIONID=<ID-do-server-sinh>; Path=/; HttpOnly
```

Tên cookie, cờ bảo vệ và các thuộc tính khác **phụ thuộc cấu hình/HTTPS/container**. Dòng trên chỉ là ví dụ minh họa, không phải output bất biến của mọi môi trường.

**C. GET có cookie jar** — curl gửi cookie đã lưu:

```bash
curl -i -b cookies.txt http://localhost:8080/api/preference
```

Body tương đương:

```json
{"language":"en"}
```

**D. GET không gửi cookie** vẫn cho:

```bash
curl -i http://localhost:8080/api/preference
```

```json
{"language":"vi"}
```

**E. Hủy session rồi thử cookie cũ:**

```bash
curl -i -b cookies.txt -X DELETE http://localhost:8080/api/session
curl -i -b cookies.txt http://localhost:8080/api/preference
```

Lệnh DELETE trả 204; GET sau đó trả mặc định `{"language":"vi"}` dù curl có thể vẫn gửi ID cũ. Đó là minh chứng **server-side invalidation**.

Nếu dùng HTTP client khác, nhớ phân biệt các chế độ “giữ cookie tự động” và “không giữ cookie”; không dùng kết quả lẫn nhau để kết luận sai.

### 6.2. Cấu hình cookie và timeout trong Spring Boot

Ví dụ **chỉ áp dụng khi triển khai HTTPS** với cấu hình Spring Boot Servlet session phù hợp, trong `application.yml`:

```yaml
server:
  servlet:
    session:
      timeout: 30m
      cookie:
        http-only: true
        secure: true
        same-site: lax
```

Ý nghĩa:

- `timeout: 30m` đặt mục tiêu thời gian không hoạt động của session theo cơ chế/container đang dùng, **không bảo đảm** một giới hạn tồn tại tuyệt đối 30 phút cho mọi phiên.
- `http-only` giúp ngăn script trực tiếp đọc cookie phiên.
- `secure: true` yêu cầu cookie được gửi qua kết nối an toàn; trên local HTTP cần cân nhắc cấu hình phát triển riêng, không âm thầm tắt bảo vệ trên production.
- `same-site: lax` giới hạn một số trường hợp cross-site; tùy luồng đăng nhập/cross-site legitimate mà chính sách cần thay đổi.

Khi dùng reverse proxy TLS termination, phải cấu hình nhận diện scheme/forwarded headers đáng tin cậy cho ứng dụng. Khi dùng Spring Session hoặc custom cookie serializer, cần kiểm tra cấu hình thực tế của thành phần quản lý cookie/session đó; không suy luận đơn thuần từ một đoạn YAML.

## 7. Các tình huống lỗi cần chẩn đoán

| Triệu chứng | Nguyên nhân khả dĩ | Hướng kiểm tra |
| --- | --- | --- |
| Mỗi request có phiên mới | Client không lưu/gửi cookie, `Secure` trên HTTP, sai Domain/Path, cookie bị chặn | Xem `Set-Cookie` response, `Cookie` request, URL và cookie jar trong DevTools |
| Cookie có nhưng session như mất | Session hết hạn, server restart, load balancer chuyển instance, session store lỗi, ID không hợp lệ | Kiểm tra session store/TTL, routing, logs có che ID |
| CORS lỗi với fetch dùng cookie | Origin chưa được phép, credentials/cookie policy không phù hợp | Cấu hình origin cụ thể, credentials, SameSite/Secure; kiểm tra preflight |
| Logout rồi vẫn thấy dữ liệu | Server chưa invalidate, token khác còn hiệu lực, trình duyệt cache dữ liệu | Kiểm tra invalidate/thu hồi credential, cache policy và dữ liệu client |
| Hai tab thấy cùng tùy chọn | Hai tab đang dùng cùng cookie/session | Kiểm tra thiết kế dữ liệu theo tab nếu thực sự cần tách |
| Session ID bị lộ | Log, URL, XSS hoặc kênh không an toàn | Loại ID khỏi log/URL, TLS, cookie flags, rotation, revoke |
| GET vô tình tạo nhiều session | Gọi `getSession()` thay vì `getSession(false)` trong luồng chỉ đọc | Rà lại chỗ tạo session và response `Set-Cookie` |
| Session store tăng dung lượng | Lưu object quá lớn, timeout quá dài, cleanup không hiệu quả | Chỉ lưu thuộc tính cần thiết, theo dõi TTL/capacity |
| Request cross-site có lúc gửi cookie, có lúc không | Khác origin/site, `SameSite`, browser policy, HTTPS hoặc điều kiện navigation | Phân tích request context và chính sách của từng browser |

Không dùng việc “thấy cookie trong DevTools” để chứng minh server nhận đúng nó: cần xem **request header thực tế** và đối chiếu thời hạn, scope, session store.

## 8. Lỗi hiểu sai thường gặp

1. **“HTTP stateless nên Spring không thể có session.”** Sai: stateful session là tầng ứng dụng ghép các request bằng ID.
2. **“Cookie là session.”** Sai: cookie là cơ chế lưu/gửi dữ liệu phía client; session là cách quản lý trạng thái và vòng đời, có thể dùng hoặc không dùng cookie.
3. **“Cookie có `HttpOnly` thì không thể bị lợi dụng.”** Sai: browser vẫn gửi cookie tự động; XSS còn có cách thực hiện hành động và CSRF vẫn cần xem xét.
4. **“Đặt `Secure` là cookie được mã hóa.”** Sai: `Secure` giới hạn kênh gửi, không mã hóa giá trị được lưu trên client.
5. **“CORS chặn mọi yêu cầu đến API từ bên ngoài.”** Sai: CORS là chính sách trình duyệt, không thay authorization của server; client không phải browser có thể gọi API theo điều kiện riêng.
6. **“Tất cả cookie sẽ sống đúng số giây cấu hình.”** Sai: browser có thể xóa/chặn; session phía server cũng có hạn riêng.
7. **“Mỗi tab có một session độc lập.”** Sai trong mô hình cookie thông thường của cùng browser profile/site; nhiều tab hay dùng chung session.
8. **“Sticky session đảm bảo state không mất.”** Sai: instance chết/redeploy vẫn có thể mất dữ liệu session trong RAM.
9. **“JWT luôn tốt hơn server session.”** Không có kết luận chung: token giảm truy vấn phiên trong một số thiết kế nhưng làm thu hồi tức thì/phân quyền thay đổi phức tạp hơn.
10. **“Xóa cookie là đủ logout.”** Sai với session server-side; cần vô hiệu ID cũ trên server, kết thúc trạng thái xác thực và xử lý token liên quan.
11. **“Chỉ có một session cho cả một người dùng.”** Không nhất thiết; mỗi thiết bị/browser có thể có session riêng, và một browser có thể có nhiều phiên theo domain hoặc cách tổ chức ứng dụng.
12. **“SameSite và CORS là cùng một thứ.”** Sai: SameSite quyết định một số hoàn cảnh gửi cookie; CORS điều khiển một số tương tác cross-origin của browser.

## 9. Câu hỏi tự kiểm tra và cách áp dụng

1. **Không có cookie:** Vì sao hai request từ cùng laptop vẫn chưa đủ để server xác định cùng một phiên? Hãy nêu một thiết kế dùng `Authorization` thay cookie.
2. **Có cookie, không có session:** Browser gửi `Cookie: JSESSIONID=abc` nhưng session store không còn `abc`. Server có thể suy ra người dùng vẫn đăng nhập không? Vì sao?
3. **Cookie scope:** `Path=/admin` có ngăn JavaScript ở `/` truy cập tất cả endpoint quản trị hay thay thế xác thực không? Phân biệt cookie scope với authorization.
4. **Hết hạn:** Vì sao `Max-Age=3600` không đồng nghĩa server giữ session hợp lệ đúng một giờ? Nếu muốn logout ngay trên mọi thiết bị cần quản lý trạng thái nào?
5. **Security:** Với cookie đăng nhập, giải thích tác dụng riêng của `Secure`, `HttpOnly`, `SameSite` và CSRF token. Điểm nào không thay cho kiểm tra quyền?
6. **Mở rộng:** Nếu session lưu trong RAM của Node A nhưng request tới Node B, điều gì xảy ra? So sánh sticky session với shared session store.
7. **Thực hành:** Chạy ví dụ ở mục 6, kiểm tra `Set-Cookie` và `Cookie`; sau đó DELETE session và thử GET cùng cookie cũ. Hãy chỉ rõ dữ liệu nào nằm ở client và dữ liệu nào ở server.
8. **Thiết kế:** Với ngôn ngữ giao diện, giỏ hàng và đơn hàng đã thanh toán, bạn sẽ chọn cookie nhỏ, session attribute hay database bền vững? Giải thích khi browser xóa cookie hoặc session hết hạn.

**Điểm cần nắm:** HTTP không tự duy trì phiên; cookie là phương tiện client lưu/gửi dữ liệu có phạm vi và thời hạn; session là cơ chế ứng dụng gắn nhiều request với trạng thái. Một ứng dụng web tốt phải giải quyết đồng thời **danh tính, quyền truy cập, vòng đời phiên, rủi ro CSRF/XSS, giới hạn browser và tính nhất quán khi mở rộng nhiều server**. Hiểu những ranh giới đó trước khi triển khai đăng nhập, session management hoặc Spring Security.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-fundamentals-007">HTTPS, reverse proxy, chuyển hướng và địa chỉ công khai</a>

<details>
<summary>Xem chi tiết</summary>

# HTTPS, reverse proxy, chuyển hướng và địa chỉ công khai

## 1. Vấn đề cốt lõi: URL người dùng thấy khác URL backend nhìn thấy

Trong ứng dụng Spring MVC, một request đi thẳng vào Tomcat nhúng có thể mang thông tin `http://localhost:8080`. Nhưng khi chạy thật, người dùng truy cập `https://app.example.test`, TLS được xử lý tại reverse proxy, và proxy chuyển tiếp request tới backend qua HTTP nội bộ.

```text
Trình duyệt
   |
   | GET https://app.example.test/api/orders/42
   | HTTPS (TLS tới cổng công khai 443)
   v
Reverse proxy / load balancer
   |
   | GET http://127.0.0.1:8080/api/orders/42
   | HTTP nội bộ; thêm thông tin host/scheme gốc qua forwarded headers
   v
Spring MVC / DispatcherServlet / Controller
   |
   | phản hồi JSON, Location, Set-Cookie...
   v
Proxy -> HTTPS -> Trình duyệt
```

Nếu Spring không biết scheme, host, port hoặc tiền tố đường dẫn công khai, ứng dụng có thể:

- Sinh link trong email, JSON, trang HTML hoặc tài liệu API thành `http://127.0.0.1:8080/...` hay `http://app.example.test/...` thay vì `https://app.example.test/...`.
- Chuyển hướng `Location` tới host/port nội bộ, sai HTTP/HTTPS hoặc thiếu tiền tố `/portal`.
- Vòng lặp redirect giữa proxy ép HTTPS và ứng dụng hiểu request là HTTP.
- Thiết lập sai một số tính chất phụ thuộc HTTPS (chẳng hạn đánh giá request có an toàn hay không), hoặc xử lý sai callback URL.

Bài này tập trung vào mối quan hệ giữa **HTTP/TLS, reverse proxy, forwarded headers, redirect và external/public URL** trong Spring Web MVC. Mô hình WebFlux có các cơ chế riêng, như `ForwardedHeaderTransformer`.

## 2. Từng khái niệm và ranh giới trách nhiệm

### HTTPS và TLS termination

HTTPS là HTTP truyền qua TLS. TLS bảo vệ tính bí mật, toàn vẹn và xác thực danh tính máy chủ trên đoạn kết nối được thiết lập TLS. Khi **TLS termination tại reverse proxy**, trình duyệt kiểm tra chứng chỉ của proxy; backend có thể nhận HTTP nội bộ.

Điều đó không tự động bảo đảm đoạn từ proxy tới backend được mã hóa. Chọn HTTP nội bộ chỉ trong mạng tin cậy có kiểm soát, hoặc dùng HTTPS/mTLS giữa các tầng nếu mô hình bảo mật yêu cầu. Một chứng chỉ HTTPS hợp lệ ở phía client không cho biết backend đã dùng TLS.

### Reverse proxy và load balancer

Reverse proxy đứng trước ứng dụng, nhận request ở địa chỉ công khai rồi chuyển tới một hoặc nhiều backend. Nó có thể terminate TLS, cân bằng tải, thêm header, cache, rewrite đường dẫn hoặc chọn upstream. Load balancer có thể hoạt động ở tầng TCP (L4) hoặc HTTP (L7); chỉ những hop hiểu HTTP mới thêm/sửa HTTP headers.

### Địa chỉ công khai (public origin/base URL)

Một **origin** là bộ `scheme + host + port`; ví dụ `https://app.example.test` có port mặc định 443. **Base URL** có thể thêm tiền tố ứng dụng như `https://app.example.test/portal`. Cần phân biệt:

| Thành phần | Ví dụ | Tác dụng |
| --- | --- | --- |
| Scheme | `https` | Protocol mà client sử dụng. |
| Host | `app.example.test` | Host công khai, khác `localhost` hoặc tên máy nội bộ. |
| Port | `443` | Cổng phía client; thường được lược khỏi URL HTTPS nếu mặc định. |
| Path prefix | `/portal` | Đường dẫn ngoài proxy có thể thêm hoặc bỏ khi forward. |
| Request URI | `/portal/api/orders/42` | Đường dẫn request mà người dùng gọi từ bên ngoài. |

Một địa chỉ công khai đúng không thể suy ra chỉ từ cổng `8080` mà Spring dùng để lắng nghe nội bộ.

## 3. Những header giúp backend biết request gốc

Có hai họ thông tin thường gặp:

- `Forwarded` theo RFC 7239, ví dụ `Forwarded: for=192.0.2.10;proto=https;host=app.example.test`. Một chuỗi proxy có thể tạo nhiều phần tử trong header.
- Các header thực tế phổ biến: `X-Forwarded-Proto: https`, `X-Forwarded-Host: app.example.test`, `X-Forwarded-Port: 443`, `X-Forwarded-For: 192.0.2.10` và `X-Forwarded-Prefix: /portal`.

| Header | Mang ý nghĩa gì? | Nhầm lẫn thường gặp |
| --- | --- | --- |
| `Host` | Host của request mà **hop hiện tại** nhận | Không nhất thiết là host ngoài cùng nếu proxy thay đổi Host. |
| `Forwarded` | Thông tin client/host/protocol do các proxy cung cấp theo chuẩn | Không phải dữ liệu xác thực tự thân; có thể bị client giả mạo nếu proxy không lọc. |
| `X-Forwarded-Proto` | Scheme của request phía ngoài, thường là `https` | Không phải bằng chứng backend ↔ proxy có TLS. |
| `X-Forwarded-Host` | Host công khai ban đầu | Không an toàn nếu cho người dùng tự đặt tùy ý. |
| `X-Forwarded-Port` | Port công khai | Không phải `server.port` nội bộ. |
| `X-Forwarded-For` | Thông tin địa chỉ client qua các hop | Là chuỗi do các proxy cung cấp, phải phân tích theo trust chain; không mặc định tin phần tử đầu. |
| `X-Forwarded-Prefix` | Tiền tố public path bị proxy thay đổi | Phải phối hợp đúng với rewrite path và servlet context path. |

**Cảnh báo bảo mật bắt buộc:** client có thể gửi header `Forwarded` hoặc `X-Forwarded-*` giả. **Reverse proxy ở ranh giới tin cậy phải loại bỏ header forwarded từ request bên ngoài rồi gán những giá trị chính xác của chính nó**, kể cả khi hệ thống chỉ định dùng một trong hai họ header. Ngoài ra, cần ngăn người dùng truy cập trực tiếp backend tin tưởng forwarded headers. Nếu không, attacker có thể làm ứng dụng sinh password-reset URL tới tên miền của họ, redirect sai host hoặc ghi sai IP. Spring không tự phân biệt header được proxy đáng tin cậy thêm hay client giả mạo.

## 4. Spring Boot xử lý forwarded headers như thế nào?

Spring Boot hỗ trợ cấu hình `server.forward-headers-strategy`:

| Giá trị | Cách xử lý | Chọn khi nào |
| --- | --- | --- |
| `none` | Không diễn giải forwarded headers để thay thông tin request public | Chạy trực tiếp hoặc không có trust boundary bảo đảm. |
| `native` | Dùng hỗ trợ của container, chẳng hạn cấu hình RemoteIpValve của Tomcat | Container xử lý đủ các loại header mà ứng dụng cần. |
| `framework` | Dùng cơ chế Spring Framework, với Spring MVC là `ForwardedHeaderFilter` | Cần ứng dụng nhận đúng scheme/host/port hoặc behavior redirect qua forwarded headers, nhất là khi container native chưa đáp ứng. |

Spring Boot có thể chọn mặc định khác nhau trên một số môi trường cloud; **không nên suy đoán từ môi trường**. Hãy chỉ rõ lựa chọn trong cấu hình triển khai và kiểm tra request thực tế. Với Tomcat đằng sau proxy terminate TLS, tài liệu Spring Boot khuyên xem `server.tomcat.redirect-context-root=false` để forwarded scheme được tính đến trước khi container tự redirect context root.

Ví dụ Spring Boot 3.5, trong `src/main/resources/application.yml`:

```yaml
server:
  port: 8080
  forward-headers-strategy: framework
  tomcat:
    redirect-context-root: false
```

Khi có header forwarded hợp lệ từ proxy tin cậy, `ForwardedHeaderFilter` bọc request để các handler và thành phần downstream nhìn thấy scheme, host, port, và tùy cấu hình/version là prefix công khai. Filter cũng xử lý tác động của forwarded headers lên một số redirect. Nếu có các filter tự đăng ký, thứ tự áp dụng rất quan trọng: thành phần cần thông tin URL public phải chạy sau khi request đã được xử lý forwarded headers.

**Không đăng ký filter thủ công lần thứ hai** nếu Boot đã tự cấu hình theo `server.forward-headers-strategy: framework`. Tránh cùng lúc bật các cách xử lý forwarding chồng lên nhau mà chưa hiểu và kiểm tra thứ tự.

### Nếu không dùng Spring Boot

Ứng dụng Spring MVC cấu hình Servlet filter thủ công có thể dùng `org.springframework.web.filter.ForwardedHeaderFilter`. Khi tự đăng ký filter, đặt nó đủ sớm và xét các dispatcher type `REQUEST`, `ASYNC`, `ERROR`; các downstream filter/servlet phải thấy request đã được bọc. Filter còn có chế độ `removeOnly` để loại header forwarded mà **không** dùng chúng. Các constructor và option riêng như cách chọn `Forwarded` so với `X-Forwarded-*` hoặc việc xử lý prefix có thể thay đổi theo dòng Spring Framework: đọc Javadoc của đúng version trước khi tự cấu hình.

## 5. Ví dụ Spring MVC chạy được: sinh link và chuyển hướng đúng public URL

Ví dụ dưới đây dùng Java 17+, Spring Boot **3.5.16**, Maven và Tomcat nhúng. Endpoint `/api/public-link` trả về URL tuyệt đối tới `/api/items/42`; endpoint `/go` thực hiện HTTP redirect đến endpoint item. Nếu forwarded headers hợp lệ được xử lý, URL tuyệt đối sẽ phản ánh host/scheme công khai.

**Cấu trúc:**

```text
proxy-demo/
├── pom.xml
└── src/main/
    ├── java/demo/
    │   ├── ProxyDemoApplication.java
    │   ├── LinkController.java
    │   └── NavigationController.java
    └── resources/application.yml
```

**`pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>proxy-demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
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

**`src/main/resources/application.yml`:**

```yaml
server:
  port: 8080
  forward-headers-strategy: framework
  tomcat:
    redirect-context-root: false
```

**`src/main/java/demo/ProxyDemoApplication.java`:**

```java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProxyDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProxyDemoApplication.class, args);
    }
}
```

**`src/main/java/demo/LinkController.java`:**

```java
package demo;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class LinkController {
    @GetMapping("/api/public-link")
    public Map<String, String> link() {
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/items/{id}")
                .buildAndExpand(42)
                .toUriString();
        return Map.of("url", url);
    }

    @GetMapping("/api/items/{id}")
    public Map<String, Object> item(@PathVariable long id) {
        return Map.of("id", id, "status", "ok");
    }
}
```

**`src/main/java/demo/NavigationController.java`:**

```java
package demo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class NavigationController {
    @GetMapping("/go")
    public String go() {
        return "redirect:/api/items/42";
    }
}
```

`@Controller` trả tên view `redirect:/...` nên Spring MVC tạo HTTP redirect. **Nếu đổi lớp này thành `@RestController`, chuỗi `redirect:/api/items/42` sẽ được gửi dưới dạng body**, không tự chuyển hướng; phải trả `ResponseEntity` có header `Location` khi muốn redirect từ REST controller.

**Chạy:**

```bash
mvn spring-boot:run
```

Ở terminal khác, khi chưa có forwarded headers:

```bash
curl -s http://localhost:8080/api/public-link
```

JSON body dự kiến (một trường, không có sự khác biệt thứ tự khóa):

```json
{"url":"http://localhost:8080/api/items/42"}
```

Thử mô phỏng proxy **trên môi trường phát triển cô lập**:

```bash
curl -s -H "X-Forwarded-Proto: https" -H "X-Forwarded-Host: app.example.test" -H "X-Forwarded-Port: 443" http://localhost:8080/api/public-link
```

JSON body dự kiến:

```json
{"url":"https://app.example.test/api/items/42"}
```

Quan sát redirect (đừng thêm `-L` vì `-L` sẽ theo tiếp Location):

```bash
curl -i -H "X-Forwarded-Proto: https" -H "X-Forwarded-Host: app.example.test" -H "X-Forwarded-Port: 443" http://localhost:8080/go
```

Kỳ vọng nhận status 3xx (mặc định redirect view thường là **302**) và `Location` dẫn về đường dẫn `/api/items/42` trên origin công khai. Đối chiếu **giá trị Location thực tế**: tùy cấu hình redirect và phiên bản Framework, trình duyệt có thể nhận URL tuyệt đối hoặc đường dẫn tương đối; điều bất biến cần kiểm là browser điều hướng được tới **đúng public origin/path**, không phải backend nội bộ.

**Lưu ý kiểm thử:** gửi trực tiếp `X-Forwarded-*` từ `curl` ở trên chỉ nhằm chứng minh cơ chế biến đổi request trong máy phát triển. Production chỉ chấp nhận những header này từ proxy tin cậy đã làm sạch chúng.

## 6. Cấu hình reverse proxy: TLS, Host và forwarded headers

Ví dụ sau là **khối `location` Nginx minh họa**, đặt bên trong một `server` đã được cấu hình HTTPS hợp lệ, chứng chỉ và `server_name app.example.test`. Nginx là reverse proxy ở **ranh giới công khai cuối cùng** nhận request từ Internet, và ứng dụng chỉ nhận kết nối nội bộ từ Nginx.

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;

    proxy_set_header Host app.example.test;
    proxy_set_header X-Forwarded-Host app.example.test;
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header X-Forwarded-Port 443;
    proxy_set_header X-Forwarded-For $remote_addr;

    proxy_set_header Forwarded "";
    proxy_set_header X-Forwarded-Prefix "";
    proxy_set_header X-Forwarded-Ssl "";
}
```

Giải thích các quyết định trong ví dụ:

- `proxy_pass` chỉ tới backend nội bộ. Trong ví dụ này Nginx và app chạy cùng máy; tại môi trường container, địa chỉ upstream thường khác.
- `Host` và `X-Forwarded-Host` gán **một host công khai đã xác định**, tránh phản chiếu Host không hợp lệ từ phía client. Nếu có nhiều tên miền hợp lệ, cần routing/allowlist host tương ứng chứ không ghi cứng một host cho mọi site.
- `X-Forwarded-Proto` và `X-Forwarded-Port` phản ánh listener HTTPS công khai. Giá trị cố định này chỉ đúng khi khối Nginx thực sự phục vụ HTTPS ở 443. Với kiến trúc nhiều tầng, giá trị được tính ở đúng trust boundary.
- `X-Forwarded-For` gán địa chỉ peer mà edge proxy quan sát; ở đây không nối tiếp dữ liệu `X-Forwarded-For` do client tự gửi. Nếu phía trước còn một proxy đáng tin cậy, phải thiết kế chuỗi địa chỉ thật với cấu hình trusted proxies tương ứng.
- Các header còn lại được xóa khỏi request upstream để client không lén đưa thông tin giả vào; Nginx không chuyển tiếp header có giá trị rỗng từ chỉ thị `proxy_set_header`.
- Firewall/network policy phải chặn kết nối công khai thẳng vào cổng backend 8080 nếu backend sẽ tin forwarded headers.

**Kiểm soát trust là trách nhiệm của hạ tầng.** Không có một cấu hình `server.forward-headers-strategy` nào tự xác minh `X-Forwarded-Host` tới từ proxy đã xác thực. Với nhiều load balancer, CDN hoặc ingress, xác định nơi các header được xóa, tạo lại và chấp nhận ở từng hop; không lấy header client tự khai làm nguồn chân lý.

### Khi phía ngoài có tiền tố `/portal`

Có hai trường hợp khác nhau:

**Trường hợp 1: proxy giữ nguyên path**: Browser gọi `/portal/api/items/42` và backend cũng nhận path đó. Có thể cấu hình ứng dụng có context path `/portal` hoặc cấu trúc mapping phù hợp; không thêm `X-Forwarded-Prefix` vô cớ. Lưu ý `server.servlet.context-path=/portal` là cấu hình cấp Servlet container, khác với chỉ viết thêm một đoạn `@RequestMapping`.

**Trường hợp 2: proxy bỏ tiền tố path**: Browser gọi `/portal/api/items/42`, backend nhận `/api/items/42`. Proxy tin cậy có thể gửi `X-Forwarded-Prefix: /portal` để framework dựng link public kèm tiền tố, khi phiên bản/cách cấu hình forwarding hỗ trợ. Cần test cả link và `Location` thực tế; không suy từ header rằng mọi loại redirect, static asset và context path tự động hoạt động hoàn hảo.

Ví dụ khi Nginx đã có HTTPS server như mục trên, có thể thay khối `location /` bằng mẫu routing có prefix:

```nginx
location /portal/ {
    proxy_pass http://127.0.0.1:8080/;

    proxy_set_header Host app.example.test;
    proxy_set_header X-Forwarded-Host app.example.test;
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header X-Forwarded-Port 443;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Prefix /portal;

    proxy_set_header Forwarded "";
    proxy_set_header X-Forwarded-Ssl "";
}
```

Dấu `/` ở cuối `proxy_pass` có ý nghĩa đối với quy tắc thay thế location prefix của Nginx: trong cấu hình ví dụ, `/portal/api/items/42` tới backend thành `/api/items/42`. Nếu mục tiêu muốn giữ prefix, cấu hình proxy khác đi và phải đồng bộ context path. Khi sử dụng `X-Forwarded-Prefix`, xác minh hỗ trợ thực tế của đúng phiên bản Spring; không đặt đồng thời context path `/portal` và áp thêm prefix `/portal` một cách tùy tiện vì có thể sinh `/portal/portal/...`.

## 7. HTTP redirect: status code, Location và bảo toàn method

Redirect là phản hồi HTTP 3xx chứa `Location`; browser/client có thể gửi request mới tới đích. Trong Spring MVC, `redirect:` tạo redirect view; với REST API có thể trả `ResponseEntity.status(...).location(uri).build()`.

| Status | Ý nghĩa chính | Cách client xử lý method/body |
| --- | --- | --- |
| **301 Moved Permanently** | URL đã chuyển lâu dài | Client có lịch sử thay POST thành GET; không nên mặc định dựa vào bảo toàn method. |
| **302 Found** | Chuyển tạm thời | Với browser và nhiều client, POST có thể bị đổi thành GET. |
| **303 See Other** | Đi tới tài nguyên khác để xem kết quả | Thường dùng sau POST, request tiếp theo là GET (trừ HEAD). |
| **307 Temporary Redirect** | Redirect tạm, **bảo toàn method và body** | Hữu ích khi phải giữ nguyên method. |
| **308 Permanent Redirect** | Redirect vĩnh viễn, **bảo toàn method và body** | Dùng cẩn trọng vì có thể được cache. |

**Ví dụ Post/Redirect/Get (PRG)**: Sau POST tạo bản ghi thành công, trả **303 See Other** tới trang `GET /orders/42`, tránh browser tải lại biểu mẫu gây gửi POST lần nữa. Với redirect 307/308, client tiếp tục gửi cùng method/body tới đích, có thể gây thay đổi dữ liệu tại nơi khác nếu cấu hình sai.

```java
// Đoạn minh họa đặt bên trong một @RestController.
@PostMapping("/api/orders")
public ResponseEntity<Void> create() {
    URI location = URI.create("/api/orders/42");
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
            .location(location)
            .build();
}
```

Đoạn này **không phải file Java độc lập**; cần `import java.net.URI;`, `ResponseEntity` và `HttpStatus` của Spring, cùng `@PostMapping`. Đây là ví dụ trả `Location` tương đối; browser sẽ giải quyết nó theo origin của request hiện tại. Trong ứng dụng thực, ID phải đến từ bản ghi vừa tạo, và thiết kế REST API có thể chọn **201 Created + Location** thay vì redirect tùy hợp đồng của endpoint.

**Không chọn redirect chỉ dựa vào mặc định Spring.** Xác định rõ đây là đổi URL tạm/vĩnh viễn, sau POST hay GET, có cần giữ method/body hay không, và client có thể cache redirect không.

## 8. HTTP → HTTPS: nên chuyển hướng tại đâu?

Mẫu phổ biến là proxy có listener HTTP trên 80 trả redirect sang HTTPS trên 443, rồi dùng HTTPS cho request ứng dụng:

```text
GET http://app.example.test/api/items/42
  -> proxy: 301/308 Location: https://app.example.test/api/items/42
  -> browser: GET https://app.example.test/api/items/42
  -> proxy: chuyển tới Spring backend
```

Nếu muốn dùng **308** cho redirect toàn site, phải chắc chắn mọi client chấp nhận method preservation khi request không phải GET. Với browser tương tác thông thường, 301/302 thường gặp; với HTTP API, đánh giá cẩn thận cách redirect ảnh hưởng POST/PUT/PATCH.

**Tránh hai tầng cùng ép HTTPS mà không thống nhất trust headers**. Ví dụ proxy đã nhận HTTPS nhưng gửi HTTP nội bộ, Spring nhìn thấy `request.isSecure() == false` vì forwarded headers bị thiếu; Spring lại redirect tới HTTPS; browser quay lại proxy và lặp mãi. Một giải pháp là xử lý HTTPS ở edge và cấu hình forwarding đúng để Spring thấy public scheme là `https`. Có thể bỏ redirect lặp ở tầng application nếu proxy đã chịu trách nhiệm.

### HSTS là gì?

**HTTP Strict Transport Security** gửi header `Strict-Transport-Security` trong phản hồi **HTTPS** để thông báo trình duyệt hỗ trợ rằng trong một khoảng thời gian nó chỉ nên dùng HTTPS cho host đó. Ví dụ:

```http
Strict-Transport-Security: max-age=31536000
```

HSTS **không thay thế** việc triển khai HTTPS và redirect HTTP khi cần, và browser bỏ qua HSTS được gửi qua HTTP. `includeSubDomains` và `preload` có tác động diện rộng, khó đảo ngược nhanh; chỉ bật khi mọi subdomain liên quan thực sự đáp ứng HTTPS và có kế hoạch vận hành. Spring Security có hỗ trợ HSTS khi ứng dụng dùng Spring Security; một số proxy cũng có thể phát header này. Hãy xác định **một nơi chịu trách nhiệm** và kiểm tra đầu ra thực tế, tránh cấu hình mâu thuẫn.

### Cookie và OAuth callback

- Cookie chứa session ID/auth token thường cần thuộc tính `Secure` để browser chỉ gửi qua HTTPS; `HttpOnly` giảm khả năng JavaScript truy cập cookie, `SameSite` liên quan việc gửi cookie trong ngữ cảnh cross-site. Chúng có vai trò khác nhau.
- Các callback URL (OAuth/OIDC), password reset URL, link email, Swagger UI redirect hoặc webhook response cần đúng **host/scheme/path công khai**, thường phải trùng allowlist của nhà cung cấp. Nên cấu hình external base URL cố định cho nghiệp vụ cần ổn định tuyệt đối, thay vì lấy trực tiếp Host/Forwarded header từ request không tin cậy.
- HSTS, cookie `Secure` và tính bảo mật TLS không thể xác nhận chỉ bằng việc `curl -k` nhận status 200. Cần test từ một client nhìn thấy chứng chỉ, proxy và redirect như người dùng thật.

## 9. Chọn giữa URL tương đối và tuyệt đối

**URL tương đối** như `/api/items/42` không cần biết host hoặc scheme và thường là lựa chọn đơn giản nhất cho liên kết điều hướng **trong cùng public origin**. Tuy nhiên nếu ứng dụng được xuất bản dưới prefix `/portal`, đường dẫn bắt đầu bằng `/` sẽ tham chiếu gốc host và có thể thiếu `/portal`. Khi đó cần ghép path dựa trên public context đúng hoặc dùng URL tương đối theo vị trí hiện tại đã kiểm thử.

**URL tuyệt đối** như `https://app.example.test/portal/api/items/42` cần thiết khi giá trị sẽ rời khỏi ngữ cảnh request hiện tại (email, callback, QR, redirect cross-origin hợp lệ, tài liệu cho client ngoài hệ thống).

Bốn nguồn sinh URL cần phân biệt:

1. **URL dựa trên request hiện tại**: `ServletUriComponentsBuilder` hoặc các công cụ của Spring MVC nhận dữ liệu request sau khi forwarding đã được xử lý; hữu ích cho link đại diện request, phụ thuộc trust boundary.
2. **External base URL cấu hình tĩnh**: hostname/cổng/prefix chính thức đã kiểm duyệt, tốt cho email và callback; tránh Host header injection và không bị lệ thuộc request nội bộ.
3. **Route tương đối trong browser**: browser tự dùng origin hiện tại, phù hợp link nội bộ; phải lưu ý prefix.
4. **URI/Location do client cung cấp**: không bao giờ redirect tùy ý đến giá trị query `?next=https://...` nếu chưa validate. Đây là lỗi *open redirect*.

Đừng sử dụng `request.getRequestURL()` trước khi kiểm tra proxy config để xây những link nhạy cảm: nếu forwarded headers chưa xử lý, nó có thể là địa chỉ backend; nếu headers chưa được làm sạch, nó có thể mang host do attacker chọn.

## 10. Lỗi mở chuyển hướng và cách xây allowlist

Ví dụ endpoint nhận `/login?next=https://evil.example` rồi trả `Location` bằng nguyên chuỗi next là **open redirect**. Kẻ tấn công có thể gửi link trông như thuộc website thật nhưng chuyển sang tên miền lừa đảo sau khi người dùng click.

Hướng xử lý an toàn tùy mục đích:

- Với redirect trong site, chỉ chấp nhận một tập đường dẫn nội bộ đã định nghĩa (ví dụ `/dashboard`, `/account`), kiểm tra path, encoding, fragment và các cách biểu diễn `//evil.example` hoặc backslash. Thay vì nhận bất kỳ URL nào, có thể map một mã điều hướng như `next=dashboard` sang một route cố định.
- Với redirect ngoài site thực sự cần thiết, chỉ chấp nhận scheme `https` và các **origin** cụ thể trong allowlist được quản trị. So sánh bằng URI parser/host chuẩn hóa, không chỉ dùng `startsWith("https://example.com")` (có thể bị đánh lừa bằng `example.com.evil.test`).
- Không xây redirect password reset/OAuth từ `X-Forwarded-Host` nếu header có thể bị client giả mạo; dùng base URL cấu hình sẵn hoặc whitelist host từ trust boundary.

Ngay cả khi backend không trực tiếp lộ port, sai cấu hình ở proxy cũng khiến `Location` và link tuyệt đối sai hoặc nguy hiểm.

## 11. Kịch bản lỗi và cách tìm nguyên nhân

| Hiện tượng | Tín hiệu cần quan sát | Nguyên nhân / hướng sửa |
| --- | --- | --- |
| Link trong JSON thành `http://localhost:8080/...` | Response body, `request.getScheme()`, `getServerName()` | Chưa xử lý forwarded headers, hoặc proxy không gửi host/scheme công khai. |
| `Location: http://app.example.test/...` khi site dùng HTTPS | HTTP 3xx `Location` | Mất `X-Forwarded-Proto`/`Forwarded: proto=https` hoặc ứng dụng tạo URL dựa trên HTTP nội bộ. |
| Redirect loop liên tục | Chuỗi `Location` và mã 301/302/307/308 qua `curl -I -L -v` | Tầng proxy và Spring đánh giá khác nhau về HTTPS, context path hoặc trailing slash. |
| Link thiếu `/portal` | Public URL đối chiếu backend request URI | Proxy đã rewrite prefix nhưng framework không nhận prefix; hoặc URL được ghép thủ công. |
| Link lặp `/portal/portal` | Public base URL / context path / forwarded prefix | Prefix được thêm ở nhiều tầng; cần chọn một mapping rõ. |
| Link dẫn tới host lạ | `Host`, `Forwarded`, `X-Forwarded-Host` do client gửi | Trust boundary không làm sạch header; cần cố định/allowlist host và chặn truy cập trực tiếp backend. |
| Request HTTPS bị browser chặn mixed content | Browser console/network | HTML/JS tạo tài nguyên `http://`; sửa nguồn sinh URL, CDN config và forwarding. |
| Cookie không xuất hiện sau redirect/login | `Set-Cookie`, domain/path/`Secure`/`SameSite` | Cookie scope sai, proxy/TLS làm request đánh giá khác public scheme, hoặc cross-site flow bị giới hạn. |
| OAuth báo `redirect_uri_mismatch` | Redirect URI gửi tới provider | Scheme/host/port/prefix không trùng URI đăng ký; cấu hình external base URL chính xác. |
| IP trong log luôn là IP proxy | Request remote address/forward chain | Chưa xử lý `X-Forwarded-For` hoặc nguồn client IP được xác định sai trust chain. |

### Kiểm tra bằng công cụ HTTP

```bash
# Xem status, Location, response headers. Không tự động follow redirect.
curl -i https://app.example.test/go

# Xem cả chuỗi redirect, request method và URL ở từng hop.
curl -v -L --max-redirs 5 https://app.example.test/go

# Tại máy có backend nội bộ, thử so sánh trước/sau chuyển tiếp.
curl -i http://localhost:8080/api/public-link
curl -i -H "X-Forwarded-Proto: https" -H "X-Forwarded-Host: app.example.test" http://localhost:8080/api/public-link
```

Đoạn trên minh họa lệnh; `app.example.test` là tên miền mẫu, muốn chạy request tới public site cần có DNS/TLS và proxy thực sự của môi trường đó. Khi test bằng `curl -L`, giới hạn redirect để tránh vòng lặp vô tận. Với endpoint nhạy cảm, không đăng log thô các header chứa thông tin cá nhân hoặc token.

Các câu hỏi chẩn đoán quan trọng: “Client gọi URL nào?”, “Proxy chuyển request URI nào?”, “Header nào được proxy tin cậy gán?”, “Spring xử lý những header đó theo cách nào?”, “Response Location/absolute link thực tế là gì?”.

## 12. Checklist triển khai

1. **Xác định public origin và path** chính thức: scheme HTTPS, host, port, prefix. Ghi rõ proxy có giữ hay xóa prefix.
2. **TLS hợp lệ** tại listener công khai; quyết định backend hop dùng HTTP nội bộ hay HTTPS/mTLS theo phân vùng mạng.
3. **Cấu hình trust boundary**: loại `Forwarded`/`X-Forwarded-*` không tin cậy, đặt lại các header cần dùng, kiểm tra quyền truy cập backend và trusted proxy chain.
4. **Chọn forwarding strategy** đúng: `none`, `native` hoặc `framework`; nếu native không xử lý đủ prefix/redirect, cân nhắc framework và test thực tế.
5. **Kiểm thử liên kết và redirect**: URL tuyệt đối, URL tương đối, `Location`, redirect sau POST, prefix, query string và trường hợp HTTP→HTTPS.
6. **Kiểm tra security/cookie**: HSTS trên HTTPS, `Secure`/`HttpOnly`/`SameSite` phù hợp, callback URLs, open redirect, host injection.
7. **Kiểm tra qua proxy thật** từ một client ngoài mạng backend; thử browser, `curl -i` và các flow liên quan; không chỉ test `localhost:8080`.

## 13. Câu hỏi tự đánh giá

**Hỏi:** Client đã dùng HTTPS nhưng `request.isSecure()` trong Spring vẫn là `false`. Có nhất thiết TLS sai không?  
**Đáp:** Không. Proxy có thể terminate TLS và chuyển HTTP nội bộ. Cần kiểm tra TLS ở client→proxy và cách Spring nhận forwarded scheme; bản thân `request.isSecure()` chỉ phản ánh request mà container/filter biểu diễn.

**Hỏi:** `X-Forwarded-Proto: https` có đủ để tin request đến từ HTTPS không?  
**Đáp:** Không. Header do client cũng có thể gửi; chỉ tin sau khi ranh giới proxy đã xóa/gán lại và bảo vệ backend khỏi truy cập ngoài luồng.

**Hỏi:** Vì sao cùng redirect path `/login` chạy đúng ở root nhưng sai khi public URL là `/portal`?  
**Đáp:** Đường dẫn bắt đầu bằng `/` là absolute-path theo gốc host; proxy có thể strip prefix. Cần xây URL theo external path đúng và xác minh hỗ trợ forwarded prefix.

**Hỏi:** Khi nào nên trả 303 thay vì 307?  
**Đáp:** Sau POST và muốn client tải trang kết quả bằng GET, chọn 303; khi redirect mà phải **giữ** request method/body, chọn 307 (tạm thời) hoặc 308 (vĩnh viễn) khi phù hợp.

**Hỏi:** Đã bật `server.forward-headers-strategy=framework`, có thể công khai cổng backend 8080 cho mọi người không?  
**Đáp:** Không nên; nếu backend nhận trực tiếp forwarded headers giả, attacker có thể gây link/redirect tới host sai. Cần firewall/routing và trust boundary.

## 14. Tài liệu đối chiếu

- [Spring Boot 3.5 — Embedded Web Servers / Running Behind a Front-end Proxy](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html).
- [Spring Framework — ForwardedHeaderFilter và bảo mật forwarded headers](https://docs.spring.io/spring-framework/reference/web/webmvc/filters.html).
- [Spring Framework — Redirect Attributes](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/redirecting-passing-data.html).
- [RFC 9110 — HTTP Semantics, 3xx redirection](https://www.rfc-editor.org/rfc/rfc9110).
- [RFC 7239 — Forwarded HTTP Extension](https://www.rfc-editor.org/rfc/rfc7239).
- [MDN — HTTP Strict Transport Security](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Strict-Transport-Security).

**Điểm cần nhớ:** Spring chỉ sinh được đúng public URL khi đã thống nhất giữa **đường đi request ngoài đời thực**, **TLS/redirect của reverse proxy**, **forwarded headers từ nguồn đáng tin**, và **quy tắc tạo URL trong ứng dụng**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-fundamentals-008">Phân biệt REST, trang HTML, HTTP streaming và WebSocket</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt REST, trang HTML, HTTP streaming và WebSocket

## 1. Bốn khái niệm nằm ở những tầng khác nhau

Trong một ứng dụng Spring, cùng một máy chủ có thể trả JSON cho ứng dụng di động, trả trang HTML cho trình duyệt, phát sự kiện liên tục qua HTTP và mở kết nối WebSocket. Những thứ này **không phải bốn framework cạnh tranh trực tiếp**:

- **REST** là *phong cách kiến trúc* cho API hướng tài nguyên: nhận diện tài nguyên bằng URI, thao tác qua ngữ nghĩa HTTP, trao đổi representation, giao tiếp stateless cùng những ràng buộc REST khác. JSON phổ biến nhưng **không bắt buộc**. Một API trả JSON chưa chắc tuân thủ đầy đủ REST.
- **Trang HTML** là *representation/giao diện tài liệu* mà browser nhận và render. Trang có thể tạo tại server (server-side rendering), phục vụ dưới dạng file tĩnh hoặc có JavaScript tải dữ liệu để cập nhật UI.
- **HTTP streaming** là *cách truyền response dần theo thời gian*: server gửi từng phần trong cùng một HTTP response khi dữ liệu xuất hiện. **Server-Sent Events (SSE)** và **NDJSON** là hai cách tổ chức dữ liệu streaming phổ biến.
- **WebSocket** là *giao thức giao tiếp hai chiều, liên tục* sau quá trình thiết lập kết nối. Client và server có thể chủ động gửi message tới nhau; dữ liệu không còn là từng HTTP response độc lập theo request.

Hai câu hỏi giúp phân biệt: **(1) API/document đang biểu diễn cái gì?** và **(2) dữ liệu đi qua kết nối theo mô hình nào?** Một endpoint REST hoàn toàn có thể cung cấp một representation HTML hoặc stream; ứng dụng HTML có thể gọi REST API và mở WebSocket. Vì vậy các khái niệm **có thể kết hợp**.

## 2. So sánh nhanh theo hành vi quan sát được

| Đặc điểm | REST API thông thường | Trang HTML | HTTP streaming (SSE/NDJSON) | WebSocket |
| --- | --- | --- | --- | --- |
| Bản chất | Kiến trúc API/resource | Định dạng tài liệu/UI | Phản hồi HTTP phát dần | Giao thức trao đổi message song công |
| Cách mở | HTTP request (GET/POST/PUT/DELETE...) | Thường HTTP GET | Thường HTTP GET cho stream; tùy use case | HTTP handshake; thường HTTP/1.1 Upgrade hoặc cách thiết lập tương ứng trên HTTP/2 |
| Sau khi mở | Một response cho mỗi request | Một response HTML, tải thêm CSS/JS/API nếu cần | Một HTTP response tồn tại lâu, nhiều chunk/event | Kết nối giữ mở; nhiều message theo hai chiều |
| Hướng dữ liệu điển hình | Client yêu cầu → server trả | Server trả HTML cho browser | Thường server → client trong response; client có thể gửi request riêng | Client ↔ server chủ động |
| Dạng nội dung | JSON, XML, HTML, binary... | `text/html` | `text/event-stream` (SSE), `application/x-ndjson` hoặc bytes | Text/binary WebSocket frames; schema do ứng dụng quyết định |
| Browser client | `fetch()` | Navigation/browser renderer | `EventSource` (SSE), `fetch()` stream | `WebSocket` API |
| HTTP status & caching | Có, theo ngữ nghĩa endpoint | Có, hỗ trợ cache tài liệu/asset | Status/header có lúc bắt đầu; stream thường không cache như tài liệu hữu hạn | Status HTTP liên quan handshake; message sau đó không mang status HTTP riêng |
| Trường hợp tiêu biểu | CRUD, resource API, nghiệp vụ | Trang giới thiệu, dashboard, form | Tiến độ, log, ticker, live feed | Chat, game, collaborative editing, tương tác hai chiều tức thời |

**Chú ý:** “REST” không đồng nghĩa “GET + JSON”, “streaming” không đồng nghĩa “WebSocket”, và “WebSocket” không có nghĩa mọi request HTTP trong ứng dụng phải được thay thế.

## 3. Minh họa timeline: điểm khác biệt ở vòng đời kết nối

### A. REST API: yêu cầu và phản hồi hữu hạn

~~~text
Browser / Mobile            Server
    |  GET /api/items/7        |
    |------------------------->|
    |  200 application/json    |
    |<-------------------------|
    |  {"id":7,"name":"Bàn phím"} |
    |  response hoàn tất       |
~~~

Request tiếp theo có thể dùng lại TCP connection qua HTTP keep-alive, nhưng vẫn là **HTTP request/response mới**. Không đánh đồng việc kết nối TCP được tái sử dụng với việc một HTTP response còn đang stream.

### B. Trang HTML: trình duyệt render tài liệu

~~~text
Browser                       Server
    | GET /page                   |
    |---------------------------->|
    | 200 text/html               |
    |<----------------------------|
    | parse HTML, render DOM      |
    | tải CSS / JS / ảnh nếu có   |
    | JS có thể gọi REST hoặc WS  |
~~~

HTML có thể tĩnh hoặc sinh bởi template engine. Ở ứng dụng hiện đại, một lần tải HTML có thể tạo ra hàng chục request tải asset/API; đó vẫn là hoạt động HTTP bình thường.

### C. HTTP streaming: một response chứa nhiều phần đến dần

~~~text
Client                        Server
    | GET /events                |
    |--------------------------->|
    | 200 text/event-stream      |
    |<---------------------------|
    | event #1                  <|
    | event #2 (sau một lúc)    <|
    | event #3 (sau một lúc)    <|
    | response kết thúc/ngắt     |
~~~

Server gửi một số header từ đầu, rồi tiếp tục flush body khi có dữ liệu. Dữ liệu streaming có thể tạo ra một hoặc nhiều đoạn ở transport; **ranh giới chunk mạng không mặc nhiên là ranh giới message ứng dụng**.

### D. WebSocket: một session trao đổi message hai chiều

~~~text
Browser / App                Server
    | request handshake         |
    |--------------------------->|
    | handshake thành công      |
    |<---------------------------|
    |--- "hello" -------------->|
    |<------------ "welcome" ---|
    |<--------------- "update" -|
    |--- "ack" ---------------->|
    |        ...                |
    | close / kết nối ngắt       |
~~~

Sau handshake, message WebSocket **không phải các HTTP response JSON riêng lẻ**. Muốn mô tả nghiệp vụ như “đăng ký room”, “gửi tin”, “ACK”, “thông báo lỗi”, ứng dụng phải định nghĩa message schema hoặc sử dụng một subprotocol phù hợp.

## 4. REST: hiểu tài nguyên, method và representation

REST nhấn mạnh tài nguyên có URI ổn định và cách thao tác tuân thủ hợp đồng HTTP. Ví dụ:

| Request | Ý nghĩa ví dụ | Response minh họa |
| --- | --- | --- |
| `GET /api/orders/42` | Lấy representation của đơn 42 | `200 OK` + JSON |
| `POST /api/orders` | Yêu cầu tạo một đơn theo quy ước API | `201 Created` + `Location` khi tạo thành công |
| `PUT /api/orders/42` | Thay thế trạng thái tài nguyên theo representation hợp đồng | `200 OK` hoặc `204 No Content` |
| `PATCH /api/orders/42` | Áp dụng cập nhật từng phần theo patch document/hợp đồng | `200 OK` hoặc `204 No Content` |
| `DELETE /api/orders/42` | Yêu cầu xóa tài nguyên | `204 No Content` thường gặp |

Ngữ nghĩa cần nhớ:

- `GET` là **safe** theo nghĩa không nhằm tạo thay đổi trạng thái do client yêu cầu; `GET` cũng idempotent. Ghi log, metrics vẫn có thể phát sinh ở server.
- `PUT` và `DELETE` có ngữ nghĩa **idempotent**: lặp yêu cầu cùng ý định phải có hiệu ứng mong muốn giống một lần, nhưng **không bắt buộc mỗi response/status giống nhau**.
- `POST` không idempotent theo mặc định. Các giao dịch tạo đơn/thanh toán có thể cần **idempotency key** do ứng dụng thiết kế để retry an toàn.
- `PATCH` không mặc nhiên idempotent; tính chất tùy patch semantics.
- **Stateless** trong REST nghĩa là mỗi request mang đủ ngữ cảnh cần thiết để server xử lý, không phụ thuộc vào trạng thái phiên ứng dụng riêng lưu bên server để hiểu request. Điều này không cấm dữ liệu được lưu lâu dài trong database.
- `Accept` thể hiện loại representation client muốn nhận; `Content-Type` biểu thị loại dữ liệu body đang gửi. `Accept: application/json` và response `Content-Type: application/json` là ví dụ, không phải định nghĩa REST.

Ví dụ biểu diễn JSON:

~~~http
GET /api/orders/42 HTTP/1.1
Host: localhost:8080
Accept: application/json
~~~

~~~http
HTTP/1.1 200 OK
Content-Type: application/json

{"id":42,"status":"PAID"}
~~~

REST không quy định bắt buộc JSON hay “mỗi URI chỉ trả đúng một format”. Một tài nguyên có thể có nhiều representation; HTML cũng có thể là một representation của tài nguyên.

## 5. HTML: khi server trả tài liệu dành cho browser

Một endpoint trả HTML:

~~~http
GET /page HTTP/1.1
Host: localhost:8080
Accept: text/html
~~~

~~~http
HTTP/1.1 200 OK
Content-Type: text/html; charset=UTF-8

<!doctype html>
<html lang="vi">
  <head><meta charset="UTF-8"><title>Trang mẫu</title></head>
  <body><h1>Chào mừng</h1></body>
</html>
~~~

Có ba kiểu thường gặp:

1. **Static HTML:** server phân phối file `.html` và các asset; phù hợp trang tĩnh, tài liệu hoặc SPA entry point.
2. **Server-side rendered HTML (SSR):** controller đưa model sang template engine như Thymeleaf; server render HTML và trả cho browser. Phù hợp nội dung cần HTML hoàn chỉnh ngay lần tải đầu, form truyền thống, SEO trong những trường hợp liên quan.
3. **Client-rendered UI (SPA):** server trả một HTML shell; JavaScript ứng dụng dùng `fetch()` để lấy REST/HTTP API data và render giao diện.

Trong Spring MVC, một method `@Controller` trả tên view, ví dụ `"product-detail"`, có thể được template resolver xử lý; method `@RestController` hoặc `@ResponseBody` thường trả **body** trực tiếp. Trả chuỗi HTML từ `@RestController` chỉ là demo; ứng dụng thực nên dùng view/template hoặc static resource phù hợp và escape nội dung do người dùng cung cấp.

**HTML không phải đối thủ của REST:** cùng một website có thể trả HTML ở `/products` và JSON ở `/api/products`. Dùng CSS/JS hoặc tải dữ liệu async không biến một trang HTML thành WebSocket.

## 6. HTTP streaming: phát dữ liệu dần trong một response

Trong HTTP response thông thường, server thường đã biết dữ liệu hữu hạn và client xử lý sau khi nhận đủ body hoặc đọc dần body khi về. Với streaming, server cố ý giữ response mở một thời gian và gửi nội dung mới theo từng đợt.

Các dạng phổ biến:

- **Streaming bytes/file:** download file lớn, proxy stream, video/file chunks; client đọc dần bytes, có thể không có message-level framing.
- **SSE:** text theo chuẩn `text/event-stream`, mỗi event có trường như `data:`, `event:`, `id:`; browser có API `EventSource` thuận tiện.
- **NDJSON (Newline-Delimited JSON):** mỗi dòng là một JSON value độc lập, ví dụ telemetry/log/items; client dùng line separator để parse từng record.
- **Long polling:** client giữ một request chờ sự kiện rồi response kết thúc và client gửi request mới. Đây là cơ chế khác với response stream nhiều sự kiện và khác WebSocket.

Với **HTTP/1.1**, một response stream không có sẵn Content-Length có thể sử dụng `Transfer-Encoding: chunked` tùy server và trường hợp. Với **HTTP/2**, dữ liệu truyền qua DATA frames, **không dùng HTTP/1.1 chunked transfer encoding**. Client không nên lấy ranh giới TCP/HTTP frame làm ranh giới dữ liệu nghiệp vụ.

### 6.1 SSE: event server gửi về browser

Response header quan trọng:

~~~http
Content-Type: text/event-stream
Cache-Control: no-cache
~~~

Body SSE có thể trông như:

~~~text
id: 101
event: status
data: {"orderId":42,"state":"PACKING"}

id: 102
event: status
data: {"orderId":42,"state":"SHIPPING"}

~~~

Một **dòng trống** kết thúc một event. `data:` có thể xuất hiện nhiều lần trong cùng event và được ghép theo quy tắc SSE. `id:` cho phép browser lưu last event ID; khi reconnect, client có thể gửi `Last-Event-ID` để server biết mốc cuối, **nhưng khả năng phát lại event bị lỡ là trách nhiệm của ứng dụng/server**, không tự có chỉ vì dùng SSE.

Client browser:

~~~javascript
const source = new EventSource("/events");
source.addEventListener("status", (event) => {
  console.log(event.lastEventId, JSON.parse(event.data));
});
source.onerror = () => console.log("SSE mất kết nối hoặc lỗi");
~~~

`EventSource` thường dùng GET và tự reconnect khi cần; ứng dụng nên có cách đóng bằng `source.close()`. Native `EventSource` không hỗ trợ truyền tùy ý custom request headers như `fetch()`; khi cần auth phải thiết kế cookies/credentials hoặc client streaming khác phù hợp, đồng thời xét CORS.

**Nên chọn SSE khi:** client cần nhận cập nhật trạng thái đơn hàng, dashboard, báo tiến độ job, monitoring hoặc feed liên tục; thao tác gửi lệnh từ client vẫn dùng HTTP POST/PUT riêng. Nếu cần hai chiều liên tục, xét WebSocket.

### 6.2 NDJSON: nhiều JSON document trên cùng một HTTP response

Ví dụ body có ba dòng độc lập:

~~~ndjson
{"id":1,"name":"Bút"}
{"id":2,"name":"Sổ"}
{"id":3,"name":"Thước"}
~~~

Content type thường dùng: `application/x-ndjson`. Client **không** parse cả body như một JSON array, mà đọc từng dòng, ghép các mảnh byte khi cần rồi parse từng JSON object. Trong JavaScript, `fetch()` cung cấp `Response.body` là `ReadableStream` trên browser hỗ trợ; khi decode stream UTF-8 bằng `TextDecoder`, phải xử lý ký tự/dòng bị cắt giữa các network chunk.

Phân biệt: SSE quy định cú pháp event và có `EventSource` tự reconnect; NDJSON chỉ là quy ước mỗi dòng JSON, không mặc định có `id`, replay hay tự reconnect.

### 6.3 Những giới hạn vận hành của HTTP streaming

- **Status và header được gửi từ đầu.** Sau khi response đã commit, không thể đổi status thành `500` một cách bình thường nếu stream lỗi; cần cơ chế thông báo lỗi trong stream hoặc đóng kết nối.
- **Proxy/CDN/load balancer** có thể buffer, idle timeout hoặc đóng kết nối. Đo thời gian từ server phát đến client nhận; kiểm tra buffering/flush thực tế.
- **Slow consumer/backpressure:** client đọc chậm gây áp lực buffer/memory; reactive backpressure có thể giúp trong các publisher hỗ trợ nhưng không tự hạn chế toàn bộ traffic hay upstream ngoài.
- **Reconnect, retry, duplicate:** stream có thể ngắt giữa chừng. Muốn độ tin cậy cần cursor/event ID, replay store, deduplication và quy tắc resume.
- **Timeout/heartbeat:** kết nối dài có thể cần keep-alive event hoặc heartbeat và timeout phù hợp. SSE có thể gửi dòng comment bắt đầu bằng `:`.
- **Bảo mật:** stream vẫn cần xác thực/phân quyền, giới hạn kết nối và kiểm soát lượng dữ liệu phát cho từng subscriber.

## 7. WebSocket: phiên hai chiều cho các message độc lập

WebSocket phù hợp khi **server và client đều phải chủ động gửi dữ liệu với độ trễ thấp**. Ví dụ chat: client gửi `SEND_MESSAGE`, server phản hồi `ACK`, server đẩy `NEW_MESSAGE` cho nhiều client khi có tin mới. Một connection có thể chuyên chở nhiều message mà không tạo request HTTP mới cho mỗi message.

Trên HTTP/1.1, browser thường khởi tạo bằng handshake có header:

~~~http
GET /ws/echo HTTP/1.1
Host: localhost:8080
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: [giá trị do client tạo]
Sec-WebSocket-Version: 13
~~~

Khi thành công, server phản hồi `101 Switching Protocols` cùng các header WebSocket cần thiết. Trên HTTP/2, tiêu chuẩn cũng cho phép cơ chế thiết lập khác (extended CONNECT) nếu client/server hỗ trợ; không giả định mọi WebSocket đều dùng chính xác handshake HTTP/1.1. Với TLS, browser dùng scheme `wss://` thay vì `ws://`; trên trang HTTPS, nên dùng WSS.

**Sau handshake:**

- Kênh truyền là WebSocket frames/messages (text hoặc binary), không phải JSON HTTP response với status/header cho từng message.
- WebSocket **không quy định** message nghiệp vụ là JSON, protobuf, tên topic hay cấu trúc event; đây là hợp đồng ứng dụng.
- **STOMP over WebSocket** là một lựa chọn subprotocol/messaging khi ứng dụng cần destination/subscription; STOMP không bắt buộc đối với WebSocket.
- WebSocket có ping/pong cấp giao thức, nhưng chiến lược heartbeat application, timeout và reconnect vẫn cần được đánh giá.
- WebSocket **không tự đảm bảo** message đã được lưu, đúng một lần, replay sau reconnect hay phân phối xuyên nhiều instance.

### Khi nào WebSocket có lợi thế?

- Chat hai chiều, presence và typing indicator.
- Collaborative editing với nhiều thay đổi client ↔ server.
- Multiplayer game / điều khiển tương tác hoặc các luồng command và push dày.
- Telemetry hai chiều cần kênh message liên tục.

Nếu client chỉ cần nhận một luồng sự kiện đơn hướng, SSE có thể đơn giản hơn. Với cập nhật thưa và không yêu cầu thời gian thực, periodic polling hoặc REST API có thể đủ.

### Những điều phải thiết kế cho production

- Xác thực lúc kết nối và phân quyền **cho từng loại message/destination**; không coi kết nối thành công là toàn quyền.
- Kiểm tra `Origin` cho browser, chính sách cookie/session, CSRF ở nơi liên quan, và không tin payload từ client.
- Giới hạn kích cỡ message, rate, số connection, outbound queue và thời gian nhàn rỗi.
- Phát hiện disconnect, reconnect theo backoff và đồng bộ trạng thái/cursor sau reconnect.
- Khi chạy nhiều instance, xác định cách broadcast tới đúng subscriber (broker/pub-sub/shared state) và cấu hình load balancer hỗ trợ WebSocket; sticky session một mình không thay thế hệ thống phân phối message.
- Theo dõi active connections, send queue, ping/pong, lỗi handshake, event-loop latency và downstream saturation.

## 8. Spring MVC và Spring WebFlux hỗ trợ những cách nào?

| Nhu cầu | Spring MVC / Servlet stack | Spring WebFlux / reactive stack |
| --- | --- | --- |
| JSON REST API | `@RestController` + `@GetMapping`, `ResponseEntity` | `@RestController` tương tự, có thể trả `Mono<T>` / `Flux<T>` |
| HTML trang | `@Controller` + view resolver/Thymeleaf, static resources | Có view rendering/static resources và annotated hoặc functional endpoints |
| SSE | `SseEmitter`, `ResponseBodyEmitter`, xử lý async Servlet | `Flux<ServerSentEvent<T>>` hoặc `Flux<T>` với `text/event-stream` |
| Stream raw/file | `StreamingResponseBody`, async support tùy trường hợp | `Flux<DataBuffer>` và reactive codecs |
| NDJSON stream | Có thể dùng emitter/custom streaming | `Flux<T>` với `application/x-ndjson` |
| WebSocket | Spring WebSocket Servlet API, `WebSocketHandler`, tùy chọn STOMP broker | Reactive `org.springframework.web.reactive.socket.WebSocketHandler` |
| Điểm đánh giá | Thư viện sync/JPA, Servlet async/virtual threads khi phù hợp | Chuỗi non-blocking xuyên downstream, Reactor semantics và flow control |

Không chọn WebFlux **chỉ** vì muốn SSE/WebSocket. Spring MVC cũng hỗ trợ streaming và WebSocket. Lựa chọn MVC/WebFlux là quyết định kiến trúc execution model/I/O chain rộng hơn nội dung bài học này.

## 9. Ví dụ chạy được: một ứng dụng WebFlux có cả bốn cách trao đổi

Ví dụ dùng **Java 17+**, **Spring Boot 3.3.x / Spring Framework 6.1+**, một ứng dụng độc lập gồm:

- `GET /api/items/7` → một JSON response thông thường;
- `GET /page` → một HTML document;
- `GET /events` → ba SSE event được phát theo thời gian;
- `GET /stream/items` → ba NDJSON record được phát theo thời gian;
- `/ws/echo` → endpoint WebSocket echo, client có thể gửi message.

WebFlux được chọn **để biểu diễn các kiểu response trong một ví dụ thống nhất**; không có nghĩa MVC không làm được những kiểu này.

### 9.1 Cấu hình Gradle

Tạo thư mục project mới với `settings.gradle`:

~~~groovy
rootProject.name = 'protocol-demo'
~~~

Tạo `build.gradle`:

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'example'
version = '1.0.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
}
~~~

Cần JDK 17+ và Gradle tương thích với phiên bản Spring Boot được sử dụng (ví dụ Gradle 8.x). Nếu có Gradle Wrapper, dùng wrapper; nếu chưa có, cần Gradle đã cài để chạy `gradle bootRun` hoặc tạo wrapper.

### 9.2 Java source

Tạo `src/main/java/example/protocols/ProtocolDemoApplication.java`:

~~~java
package example.protocols;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class ProtocolDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProtocolDemoApplication.class, args);
    }

    public record Item(long id, String name) {}

    @RestController
    static class HttpController {

        @GetMapping(value = "/api/items/{id}",
                    produces = MediaType.APPLICATION_JSON_VALUE)
        public Item item(@PathVariable long id) {
            return new Item(id, "Bàn phím");
        }

        @GetMapping(value = "/page",
                    produces = MediaType.TEXT_HTML_VALUE)
        public String page() {
            return "<!doctype html><html lang=\"vi\">"
                    + "<head><meta charset=\"UTF-8\">"
                    + "<title>HTML demo</title></head>"
                    + "<body><h1>Trang HTML từ Spring</h1></body></html>";
        }

        @GetMapping(value = "/events",
                    produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public Flux<ServerSentEvent<String>> events() {
            return Flux.interval(Duration.ZERO, Duration.ofSeconds(1))
                    .take(3)
                    .map(index -> ServerSentEvent.<String>builder()
                            .id(Long.toString(index))
                            .event("tick")
                            .data("Thông báo " + index)
                            .build());
        }

        @GetMapping(value = "/stream/items",
                    produces = MediaType.APPLICATION_NDJSON_VALUE)
        public Flux<Item> ndjson() {
            return Flux.interval(Duration.ZERO, Duration.ofSeconds(1))
                    .take(3)
                    .map(index -> new Item(index + 1,
                            "Mặt hàng " + (index + 1)));
        }
    }

    @Component
    static class EchoSocket implements WebSocketHandler {

        @Override
        public Mono<Void> handle(WebSocketSession session) {
            return session.send(session.receive()
                    .map(message -> session.textMessage(
                            "echo: " + message.getPayloadAsText())));
        }
    }

    @Bean
    public HandlerMapping socketMapping(EchoSocket echoSocket) {
        Map<String, WebSocketHandler> endpoints =
                Map.of("/ws/echo", echoSocket);
        return new SimpleUrlHandlerMapping(endpoints, -1);
    }
}
~~~

Trong ứng dụng Spring Boot WebFlux cấu hình theo chuẩn Boot, WebSocket handler adapter được framework thiết lập. `SimpleUrlHandlerMapping` đăng ký `/ws/echo` với thứ tự ưu tiên `-1` để request handshake được đưa tới WebSocket handler. Khi client đóng kết nối, luồng receive hoàn tất và phiên được giải phóng. Đây là demo echo chữ, không phải giao thức chat hoàn chỉnh.

### 9.3 Chạy và tự kiểm chứng HTTP

Từ thư mục project, chạy:

~~~bash
./gradlew bootRun
~~~

Trên Windows:

~~~powershell
.\gradlew.bat bootRun
~~~

Nếu không có wrapper, dùng `gradle bootRun` với Gradle tương thích đã cài. Khi server chạy mặc định trên cổng 8080:

**A. JSON REST-style GET**

~~~bash
curl -i http://localhost:8080/api/items/7
~~~

Status mong đợi: `200 OK`; `Content-Type` thuộc `application/json`. Body:

~~~json
{"id":7,"name":"Bàn phím"}
~~~

**B. HTML document**

~~~bash
curl -i http://localhost:8080/page
~~~

Status mong đợi: `200 OK`; `Content-Type` thuộc `text/html`. Body có `<h1>Trang HTML từ Spring</h1>`, trình duyệt sẽ render thành tiêu đề trang.

**C. SSE streaming**

~~~bash
curl -N -H "Accept: text/event-stream" http://localhost:8080/events
~~~

Khi kết nối được phát dữ liệu theo lịch của ví dụ, body có ba event tương đương về nội dung:

~~~text
id:0
event:tick
data:Thông báo 0

id:1
event:tick
data:Thông báo 1

id:2
event:tick
data:Thông báo 2

~~~

Khoảng trắng sau dấu hai chấm tùy cách serialize SSE hợp lệ; **không phụ thuộc chính xác spacing**. `-N` yêu cầu curl không buffer stdout; mỗi event đến cách nhau xấp xỉ một giây, còn thời gian thực tế phụ thuộc scheduler và tải.

**D. NDJSON streaming**

~~~bash
curl -N -H "Accept: application/x-ndjson" http://localhost:8080/stream/items
~~~

Các dòng body dự kiến, mỗi dòng là JSON độc lập:

~~~ndjson
{"id":1,"name":"Mặt hàng 1"}
{"id":2,"name":"Mặt hàng 2"}
{"id":3,"name":"Mặt hàng 3"}
~~~

Các dòng đến dần theo thời gian; không có dấu ngoặc vuông `[ ]` bao quanh toàn bộ dòng như JSON array.

**E. WebSocket client từ DevTools của browser**

Mở trang trong browser và chạy:

~~~javascript
const ws = new WebSocket("ws://localhost:8080/ws/echo");
ws.onopen = () => ws.send("xin chào");
ws.onmessage = (event) => console.log(event.data);
ws.onerror = (event) => console.error("WebSocket error", event);
ws.onclose = (event) => console.log("closed", event.code);
~~~

Kết quả console mong đợi khi kết nối thành công:

~~~text
echo: xin chào
~~~

Để đóng kết nối khi kiểm thử xong, gọi `ws.close()`. Nếu trang được truy cập qua HTTPS, URL WebSocket phải sử dụng `wss://` trong môi trường được triển khai với TLS phù hợp.

**F. SSE client từ browser**

Nếu đang mở cùng origin với ứng dụng demo:

~~~javascript
const es = new EventSource("/events");
let received = 0;

es.addEventListener("tick", (event) => {
  console.log("SSE", event.lastEventId, event.data);
  received++;
  if (received === 3) es.close();
});
~~~

Ví dụ đóng sau ba event, vì `EventSource` mặc định có thể kết nối lại khi stream đóng; `Flux.take(3)` ở server chỉ kết thúc response của **một lần kết nối**, không vô hiệu hóa khả năng reconnect của client.

### 9.4 Điều gì giúp ví dụ này hoạt động?

- `@RestController` và `produces` phân biệt content type cho JSON, HTML, SSE và NDJSON.
- `Flux.interval(...).take(3)` là publisher mô phỏng dữ liệu sinh dần. Thực tế có thể thay bằng nguồn events từ application service.
- `ServerSentEvent<T>` tạo cú pháp SSE có event name và ID.
- `Flux<Item>` với `application/x-ndjson` cho phép encode từng item thành dòng JSON; không gom thành một JSON array nếu giữ đúng streaming media type.
- `WebSocketHandler` tiêu thụ các message đến và chuyển thành message trả ra trên cùng session.
- Một ứng dụng dùng được cả năm endpoint chứng minh **cùng web stack có thể triển khai nhiều kiểu giao tiếp**.

**Giới hạn demo:** chương trình chưa có xác thực/phân quyền, rate limit, persistence, resume cursor hay xử lý lỗi/retry cho môi trường phân tán. Trong thực tế không đặt một broadcaster vô hạn hoặc echo tùy ý cho client không được tin cậy.

## 10. Cách chọn cho tình huống thực tế

### Tình huống 1 — Quản lý sản phẩm / đơn hàng

Người dùng nhấn “Tạo đơn” và xem lịch sử mua hàng. Client cần request có kết quả rõ ràng, HTTP status phù hợp và dễ cache dữ liệu GET khi được phép. **Dùng HTTP API theo resource/REST-style**, trả JSON cho ứng dụng hoặc HTML SSR nếu client là trang truyền thống. Không cần WebSocket để thay thế thao tác POST.

### Tình huống 2 — Giao diện website giới thiệu / biểu mẫu

Trang có nội dung đầy đủ ngay khi tải, template render dữ liệu và form post. **Trả HTML**; một số hành động vẫn có thể gọi JSON API. WebSocket không mang lại lợi ích chỉ vì browser đang mở trang.

### Tình huống 3 — Theo dõi tiến độ xử lý file trong 2 phút

Client gửi `POST /jobs` để tạo job, nhận `202 Accepted` và một `jobId`, sau đó theo dõi `GET /jobs/{id}/events` bằng SSE hoặc polling. **SSE** hợp lý khi cần server đẩy nhiều cập nhật một chiều và client giữ một request mở; nếu yêu cầu đơn giản, polling định kỳ cũng được. Cần có cơ chế reconnect/replay nếu không được mất tiến độ.

### Tình huống 4 — Dashboard log/telemetry chảy liên tục

Các record liên tiếp đến dưới dạng JSON. **NDJSON streaming** hợp với client có thể parse từng dòng, cần dữ liệu máy đọc được theo một chiều; **SSE** hợp nếu mục tiêu là browser dùng `EventSource` và muốn event ID/reconnect semantics thuận tiện.

### Tình huống 5 — Chat trực tiếp nhiều người

Client vừa gửi tin, vừa nhận tin mới và typing indicator. **WebSocket** hợp lý vì nhiều message hai chiều và kết nối giữ mở. Vẫn có thể dùng REST API cho đăng nhập, tải lịch sử, upload file, quản lý room; WebSocket đảm nhiệm luồng tương tác thời gian thực.

### Tình huống 6 — Giá cập nhật một lần mỗi 10 phút

GET API với cache/periodic refresh thường đơn giản hơn kết nối streaming/WebSocket luôn mở. “Realtime” là yêu cầu kinh doanh cần lượng hóa, không phải mục tiêu tự thân.

## 11. Khác biệt quan trọng khi thiết kế, debug và deploy

### 11.1 Độ trễ và buffering

Ứng dụng có thể `flush()` nhưng reverse proxy vẫn buffer body. Để kiểm tra stream, dùng `curl -N`, quan sát Network timing trong browser, thử qua đúng ingress/CDN production. Tách thời gian xử lý nghiệp vụ khỏi thời gian server write và client nhận. Streaming không bảo đảm một event lập tức được render khi proxy/client trì hoãn.

### 11.2 Caching và CDN

GET JSON/HTML có thể cache theo `Cache-Control`, `ETag`/`Last-Modified` khi hợp đồng cho phép. SSE và stream dài thường phải xem xét disable cache và proxy buffering. WebSocket không dùng HTTP response caching cho từng message. Tránh gắn `Cache-Control: public` cho dữ liệu cá nhân.

### 11.3 Backpressure, fan-out và tải

Nhiều người nhận cùng một broadcast có tốc độ đọc khác nhau; server phải có chính sách buffer, drop/coalesce, disconnect slow clients hoặc queue giới hạn. WebFlux hỗ trợ Reactive Streams, nhưng upstream/broker không nhất thiết có backpressure đến tận nguồn. Đo memory, số connection, queue length và tốc độ gửi.

### 11.4 Error semantics

HTTP API thông thường thể hiện lỗi qua HTTP status và error body. Với SSE/NDJSON, nếu headers đã commit rồi mới phát sinh lỗi, không thể thay status như response chưa bắt đầu; cần error event/record theo giao thức hoặc đóng stream và cho client resume. WebSocket cần định nghĩa error message hoặc close code, không thể mong HTTP 500 cho từng frame.

### 11.5 Mất kết nối và thứ tự message

SSE có hỗ trợ last event ID ở browser nhưng server phải lưu lịch sử để replay. NDJSON cần tự định nghĩa cursor/reconnect nếu cần. WebSocket trên **một kết nối** bảo toàn thứ tự message theo giao thức, nhưng khi reconnect, failover hoặc xử lý qua nhiều service, cần thiết kế ACK, offset, idempotency và deduplication ở tầng ứng dụng.

### 11.6 Xác thực và bảo mật

HTTP, SSE và WebSocket đều cần auth phù hợp dữ liệu. `EventSource` có giới hạn headers trong browser. Với WebSocket browser, phải xét Origin, cookie, handshake auth và **quyền của từng message**. Dùng TLS (`https://`, `wss://`), validate payload, giới hạn kích thước/tốc độ và kiểm tra phân quyền trước khi gửi dữ liệu vào room/topic.

### 11.7 Quan sát và test

- REST: status, response schema, cache headers, idempotent retry, p95/p99.
- HTML: render đúng, escaping/XSS, asset tải được, SEO/SSR nếu yêu cầu.
- SSE: nhiều event đến theo thời gian, reconnect, Last-Event-ID/replay, proxy idle timeout.
- NDJSON: mỗi dòng parse được, chunk có thể cắt giữa dòng/ký tự UTF-8, stream cancel và slow consumer.
- WebSocket: handshake, message schema, ping/pong, reconnect, auth per-message, nhiều client/instance và close code.

## 12. Lỗi hiểu nhầm phổ biến

| Hiểu nhầm | Sửa cách hiểu |
| --- | --- |
| “REST là JSON” | REST là kiến trúc; JSON chỉ là một kiểu representation; JSON API có thể không RESTful |
| “HTML là backend, REST là frontend” | Cả HTML và REST API có thể được server cung cấp và browser/mobile tiêu thụ |
| “Streaming phải dùng WebSocket” | HTTP streaming (SSE, NDJSON, file) hoạt động trong HTTP response |
| “SSE gửi hai chiều như WebSocket” | SSE stream gốc là server → client; muốn gửi lệnh, client dùng HTTP request khác |
| “`Transfer-Encoding: chunked` là chuẩn SSE” | SSE là cú pháp event; chunked chỉ là một cơ chế HTTP/1.1 truyền body |
| “Một TCP chunk tương ứng một JSON record” | Mạng có thể cắt/gộp bytes tùy ý; parse theo framing của SSE/NDJSON |
| “WebSocket tự có topic, room, ACK, lưu trữ” | WebSocket chỉ cung cấp transport message; ứng dụng hoặc subprotocol thiết kế các chức năng này |
| “HTTP status code cho mỗi WebSocket frame” | Status HTTP xử lý handshake; message sau đó cần schema lỗi riêng |
| “SSE có `id` thì tự replay mọi dữ liệu” | Server phải lưu và phát lại theo ID khi client reconnect |
| “Đổi sang WebFlux mới dùng được WebSocket/SSE” | Spring MVC cũng hỗ trợ SSE, WebSocket và async HTTP |
| “Có streaming là ngay lập tức không latency” | Proxy buffering, scheduler, backpressure và client read speed vẫn có tác động |
| “Giữ connection lâu là luôn tốt hơn polling” | Connection, memory, broker fan-out và vận hành đều có chi phí; chọn theo tần suất cập nhật |

## 13. Bài tập vận dụng

Chọn cơ chế giao tiếp thích hợp và nêu lý do:

1. Frontend cần lấy thông tin một sản phẩm theo ID, tải xong là đủ.
2. Server cần render trang danh sách bài viết cho trình duyệt, có liên kết và form.
3. Browser cần nhận tình trạng đồng bộ dữ liệu mỗi giây trong một phút; không gửi message tương tác qua cùng kênh.
4. CLI cần đọc hàng nghìn record JSON lớn dần, xử lý ngay từng dòng.
5. Hai người đang cùng sửa một tài liệu và gửi các thay đổi qua lại liên tục.
6. Dịch vụ chỉ cập nhật giá sản phẩm khoảng một lần mỗi giờ.

**Gợi ý đáp án:** (1) HTTP GET REST-style/JSON. (2) HTML từ server hoặc static/SSR. (3) SSE và có thể chọn polling nếu yêu cầu đơn giản. (4) NDJSON HTTP streaming. (5) WebSocket, cộng message schema và đồng bộ trạng thái. (6) GET có caching hoặc polling theo lịch, không nhất thiết giữ kết nối dài.

## 14. Checklist thiết kế một endpoint

- [ ] Xác định rõ mục tiêu: resource API, tài liệu HTML, feed một chiều hay tương tác hai chiều.
- [ ] Biết HTTP method, URI, status, `Content-Type`, `Accept` và cache policy.
- [ ] Với REST API: nắm ngữ nghĩa safe/idempotent, transaction và retry.
- [ ] Với HTML: biết render ở server hay client, escape dữ liệu động và quản lý assets.
- [ ] Với SSE/NDJSON: định nghĩa framing, giới hạn stream, timeout, heartbeat, backpressure và lỗi sau khi response commit.
- [ ] Với SSE: quyết định có cần event ID, replay và auth tương thích browser không.
- [ ] Với WebSocket: định nghĩa message schema, auth từng message, close/reconnect, capacity và broadcast nhiều instance.
- [ ] Kiểm chứng streaming qua proxy/load balancer thật; đo client nhận được lúc nào.
- [ ] Test khi client đọc chậm, ngắt giữa chừng hoặc reconnect.
- [ ] Chỉ chọn MVC/WebFlux sau khi xét **toàn bộ chuỗi I/O** và thư viện đang sử dụng.

**Kết luận ứng dụng:** REST giúp tổ chức API tài nguyên; HTML cung cấp tài liệu/giao diện; HTTP streaming cung cấp phản hồi dần trong HTTP; WebSocket cung cấp kênh message hai chiều dài hạn. Một hệ thống Spring khỏe mạnh có thể phối hợp cả bốn theo từng luồng nghiệp vụ.

### Tài liệu tham khảo

- [Spring Framework — Spring Web MVC](https://docs.spring.io/spring-framework/reference/web/webmvc.html)
- [Spring Framework — Spring WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Spring Framework — WebFlux WebSockets](https://docs.spring.io/spring-framework/reference/web/webflux-websocket.html)
- [Spring Framework — Asynchronous Requests / SSE trong MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)
- [MDN — Server-sent events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)
- [MDN — WebSocket API](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API)
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110)
- [RFC 6455 — The WebSocket Protocol](https://www.rfc-editor.org/rfc/rfc6455)


</details>

- [Quay lại đầu trang](#back-to-top)
