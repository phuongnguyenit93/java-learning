<a id="back-to-top"></a>

# Filter, Interceptor, CORS và hạ tầng request

## Menu
- [Servlet Filter, HandlerInterceptor và vị trí của chúng trong request pipeline](#filters-interceptors-and-placement)
- [CORS processing trong Spring MVC](#cors-processing)
- [Forwarded Headers](#forwarded-headers)
- [Cross-cutting component qua Async Dispatch](#async-dispatch-cross-cutting)
- [Ranh giới Spring Security](#security-boundary)

## <a id="filters-interceptors-and-placement">Servlet Filter, HandlerInterceptor và vị trí của chúng trong request pipeline</a>

<details>
<summary>Xem chi tiết</summary>

Servlet `Filter` và Spring MVC `HandlerInterceptor` đều bao quanh request processing nhưng ở layer khác nhau.

Filter thuộc Servlet chain, có thể chạy trước `DispatcherServlet`, wrap request/response và tham gia dispatch không phải MVC controller.

Interceptor thuộc MVC sau khi handler đã được map; nó có thể thấy handler được chọn và chạy trước/sau handler execution.

```text
Filter
→ concern ở Servlet level

HandlerInterceptor
→ concern ở MVC handler level
```

Chọn layer thấp nhất nhưng vẫn có context cần thiết. Request encoding/generic servlet wrapping hợp với filter; handler-specific timing hay locale change có thể hợp với interceptor.

Interceptor không phải replacement cho security framework vì path matching và lifecycle coverage khác dedicated security infrastructure.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cors-processing">CORS processing trong Spring MVC</a>

<details>
<summary>Xem chi tiết</summary>

CORS quyết định browser code từ một origin có thể gọi cross-origin request tới origin khác hay không. Browser có thể gửi preflight `OPTIONS` trước actual request nếu operation không phải simple CORS request.

Spring MVC hỗ trợ `@CrossOrigin` cục bộ và global CORS mapping. Handler mapping kết hợp CORS configuration phù hợp rồi tham gia xử lý preflight/actual request.

CORS policy nên định nghĩa rõ allowed origin, method, header, exposed header, credential và cache duration khi cần. Wildcard rộng không thay thế việc xác định browser origin nào thực sự được gọi API.

CORS là browser security interaction, không phải authentication. Request được CORS cho phép vẫn có thể unauthorized; non-browser client cũng không bị browser CORS enforcement ràng buộc.

Khi dùng Spring Security, CORS processing cần tích hợp đúng với security filter chain thay vì được viết lại bằng MVC code.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="forwarded-headers">Forwarded Headers</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng sau reverse proxy thường nhìn thấy scheme/host/port nội bộ khác với địa chỉ public mà client dùng. Header chuẩn `Forwarded` và các `X-Forwarded-*` phổ biến có thể mô tả client-facing request.

`ForwardedHeaderFilter` có thể adapt Servlet request để downstream code nhìn thấy scheme, host, port đã forwarded hoặc có thể loại bỏ các header đó.

Đây là **trust boundary**. Internet client có thể giả mạo forwarding header nếu edge proxy không xóa giá trị không tin cậy rồi ghi giá trị trusted. Application không nên tin header chỉ vì nó tồn tại.

Forwarded-header handling ảnh hưởng redirect, absolute URI generation, security decision dựa trên scheme và link trả cho client.

Nên có một strategy rõ ràng do deployment sở hữu thay vì trộn container-level/application-level rewriting mà không hiểu precedence.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-dispatch-cross-cutting">Cross-cutting component qua Async Dispatch</a>

<details>
<summary>Xem chi tiết</summary>

Async MVC làm một logical request có thể đi qua nhiều Servlet dispatch. Vì vậy filter và interceptor cần hiểu lifecycle.

Filter có thể được map cho `REQUEST`, `ASYNC`, `ERROR` và dispatcher type khác. `OncePerRequestFilter` có hook quyết định có chạy ở async/error redispatch hay không.

MVC interceptor có thể tham gia lại khi async request redispatch. `AsyncHandlerInterceptor` có callback lúc concurrent handling bắt đầu, trước khi original request thread rời đi.

Hạ tầng request-context của Spring là cơ chế thread-bound nằm trên lifecycle này. `RequestContextHolder` expose `RequestAttributes` hiện tại; với Servlet request đó thường là `ServletRequestAttributes`. `DispatcherServlet` expose context này cho các request mà nó xử lý. `RequestContextFilter` hoặc `RequestContextListener` có thể cung cấp binding tương tự cho code cần Spring request context ngoài đường đi của `DispatcherServlet`, nhưng không nên đăng ký dư thừa khi servlet đã sở hữu việc expose context cần thiết.

Binding thuộc về thread đang xử lý dispatch hiện tại, không gắn vĩnh viễn với một asynchronous task bất kỳ. Vì vậy async/error redispatch có thể cần infrastructure bind context lại trên thread tham gia. Không capture state từ `RequestContextHolder` rồi giả định application-created executor task tự động dùng an toàn; chỉ truyền dữ liệu cụ thể mà task sau thực sự cần, hoặc dùng một cơ chế context propagation có chủ đích.

Code đo timing, mở resource hoặc giữ thread-local state phải tính tới lifecycle bị chia. "Original handler thread đã return" chưa chắc nghĩa HTTP request đã hoàn tất.

Với correlation/logging context, chỉ propagate dữ liệu thread sau thật sự cần và cleanup trên mọi completion/error path.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-boundary">Ranh giới Spring Security</a>

<details>
<summary>Xem chi tiết</summary>

Authentication, authorization, CSRF protection, security-context persistence và request firewalling thuộc Spring Security, chủ yếu tích hợp qua Servlet filter chain.

MVC interceptor thấy mapped handler và có thể hữu ích cho metadata ứng dụng không phải security, nhưng nó không phải security boundary hoàn chỉnh. Static resource, error dispatch, path normalization và handler-mapping difference có thể tạo gap nếu authorization được tự viết trong interceptor.

CORS và security cũng khác nhau. CORS nói browser origin nào được phép gửi request; Spring Security quyết định request có authenticated/authorized hay không và áp dụng protection như CSRF khi phù hợp.

MVC có thể sử dụng authenticated principal, nhưng security policy nên ở Spring Security thay vì bị nhân đôi trong controller/interceptor.

</details>

- [Quay lại đầu trang](#back-to-top)
