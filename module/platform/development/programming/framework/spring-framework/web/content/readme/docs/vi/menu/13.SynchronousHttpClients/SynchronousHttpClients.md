<a id="back-to-top"></a>

# RestClient, RestTemplate và hạ tầng client dùng chung

## Menu
- [Mô hình HTTP client đồng bộ của Spring](#synchronous-client-model)
- [RestClient trong Spring Framework 6.1](#rest-client-6-1)
- [RestTemplate](#rest-template)
- [ClientHttpRequestFactory và HTTP library bên dưới](#client-http-request-factory)
- [Tùy biến client request: interceptor, initializer và xử lý URI](#client-request-customization)
- [Message conversion và body handling phía client](#client-message-conversion-and-bodies)
- [Xử lý status và error](#status-and-error-handling)
- [Ranh giới chọn client của Integration HTTP](#integration-http-selection-boundary)

## <a id="synchronous-client-model">Mô hình HTTP client đồng bộ của Spring</a>

<details>
<summary>Xem chi tiết</summary>

HTTP client đồng bộ của Spring cung cấp mô hình request/response blocking trên một HTTP library bên dưới. Thread của bên gọi chuẩn bị request, thực hiện I/O, chờ response, decode response rồi tiếp tục với giá trị hoặc exception. Spring Framework thêm một lớp thống nhất cho URI expansion, header, message conversion, interceptor, request factory và error handling.

Hai API đồng bộ chính trong Spring Framework 6.1 là `RestClient` và `RestTemplate`. Chúng dùng chung nhiều hạ tầng thấp hơn nhưng có programming style khác nhau:

```text
RestClient
→ fluent request specification

RestTemplate
→ template-method operations
```

Không abstraction nào biến remote call thành local call. DNS, TLS, connection pool, timeout, latency và partial failure vẫn tồn tại bên dưới. Vì vậy synchronous client chỉ là API thuận tiện, không phải resilience strategy.

Chapter này sở hữu Framework mechanics của các client đó. Việc chọn blocking hay reactive client cho toàn hệ thống, retry/circuit breaker và kiến trúc service-to-service thuộc Integration HTTP.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rest-client-6-1">RestClient trong Spring Framework 6.1</a>

<details>
<summary>Xem chi tiết</summary>

`RestClient` được giới thiệu trong Spring Framework 6.1 như một HTTP client đồng bộ có fluent API. Code xây request từng bước rồi chọn cách đọc response.

```java
User user = restClient.get()
    .uri("/users/{id}", 42)
    .accept(MediaType.APPLICATION_JSON)
    .retrieve()
    .body(User.class);
```

`retrieve()` là high-level path phổ biến. Nó áp dụng status handling rồi decode body hoặc tạo `ResponseEntity`. Mặc định, 4xx và 5xx được chuyển thành exception của Spring web client. Có thể tùy biến bằng `onStatus` cho một response hoặc default status handler ở builder.

`exchange(...)` là escape hatch mức thấp hơn khi code cần tự quyết định dựa trên status, header và body cùng lúc. Các status handler đăng ký cho `retrieve()` không được áp dụng khi dùng `exchange()`; callback của `exchange` tự sở hữu quyết định status/error. Vì vậy không nên xem nó chỉ là một cách viết khác của `retrieve()`.

`RestClient` có thể được tạo trực tiếp hoặc build từ cấu hình của một `RestTemplate` hiện có, giúp chia sẻ/migrate cấu hình mà không giả định hai API có call shape giống nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rest-template">RestTemplate</a>

<details>
<summary>Xem chi tiết</summary>

`RestTemplate` là synchronous client theo template style đã tồn tại lâu trong Spring Framework. API gom các thao tác HTTP phổ biến vào những method như `getForObject`, `postForEntity`, `exchange` và `execute`.

Template centralize các bước lặp lại:

```text
chuẩn bị request
→ áp dụng callback/interceptor
→ thực thi qua ClientHttpRequestFactory
→ kiểm tra status
→ decode qua HttpMessageConverter
```

`RestTemplate` **không bị deprecated trong Spring Framework 6.1**. `RestClient` cung cấp fluent synchronous API hiện đại hơn và là lựa chọn tự nhiên cho code mới, nhưng ứng dụng hiện có vẫn có thể dùng `RestTemplate` khi semantics và integration của nó đã ổn định.

Điểm vận hành quan trọng là cấu hình mutable dùng chung. Nên cấu hình `RestTemplate` trước khi dùng concurrent, thay vì thay request factory, converter, interceptor hay error handler khi request đang chạy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="client-http-request-factory">ClientHttpRequestFactory và HTTP library bên dưới</a>

<details>
<summary>Xem chi tiết</summary>

`ClientHttpRequestFactory` là cầu nối giữa Spring client API và implementation HTTP thực tế. Nó tạo `ClientHttpRequest` thực hiện network operation. Các factory khác nhau có thể dựa trên JDK HTTP stack hoặc client library được hỗ trợ khác.

Boundary này quan trọng vì transport behavior không hoàn toàn do `RestClient` hay `RestTemplate` quyết định. Connection pooling, proxy, TLS, low-level timeout, HTTP protocol version và native request option phụ thuộc factory cùng library bên dưới.

Chọn request factory dựa trên yêu cầu vận hành chứ không chỉ vì một class có mặt trên classpath. Service throughput cao thường cần pool và timeout được cấu hình có chủ đích; command-line tool đơn giản có thể không cần mức phức tạp đó.

```text
RestClient / RestTemplate
→ Spring request/response semantics

ClientHttpRequestFactory
→ transport adapter

HTTP library bên dưới
→ socket, pooling, TLS, protocol implementation
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="client-request-customization">Tùy biến client request: interceptor, initializer và xử lý URI</a>

<details>
<summary>Xem chi tiết</summary>

Client request thường cần cross-cutting customization như correlation header, authentication material, logging metadata hoặc quy tắc URI dùng chung. Spring cho phép áp dụng các concern đó trước khi request đi xuống transport.

`ClientHttpRequestInterceptor` bọc execution và có thể đọc/sửa request trước khi delegate. Interceptor tạo thành chain nên thứ tự có ý nghĩa. Ví dụ interceptor đọc response body để log có thể làm thay đổi khả năng đọc body của code sau nếu không chủ động dùng buffering phù hợp.

Request initializer tùy biến request cụ thể trước execution; `UriBuilderFactory` centralize base URL, URI template và encoding. `RestClient.Builder` cũng cung cấp default header, request customization và interceptor.

Không đưa business logic của từng endpoint vào interceptor. Interceptor phù hợp với concern trực giao với operation. Cũng không tự retry non-idempotent request trong generic interceptor nếu ứng dụng chưa xác định rõ retry có an toàn hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="client-message-conversion-and-bodies">Message conversion và body handling phía client</a>

<details>
<summary>Xem chi tiết</summary>

Synchronous client dùng `HttpMessageConverter` để serialize và deserialize body, tương tự cách Spring MVC dùng converter phía server. Converter được chọn dựa trên Java type cùng media type của request/response.

```text
Java value phía client
→ HttpMessageConverter
→ HTTP body

HTTP body
→ HttpMessageConverter
→ Java value phía client
```

`Content-Type` mô tả body gửi đi, còn `Accept` mô tả representation client muốn nhận. Converter chỉ xử lý được khi Java type và media type nằm trong khả năng của nó.

Với generic response type, nên dùng API giữ lại type information như `ParameterizedTypeReference<List<User>>` thay vì mong runtime khôi phục generic parameter đã bị type erasure từ `List.class`.

Custom converter nên hẹp. Thay toàn bộ converter list chỉ để thêm một format có thể vô tình làm mất hỗ trợ JSON, string, form, resource hoặc byte array ở nơi khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="status-and-error-handling">Xử lý status và error</a>

<details>
<summary>Xem chi tiết</summary>

Nhận được byte từ server chưa có nghĩa remote call thành công. Client cần policy rõ cho HTTP status, transport failure, decoding failure và error body.

Với `RestClient.retrieve()`, response 4xx thường tạo `HttpClientErrorException`, 5xx tạo `HttpServerErrorException`. `onStatus` định nghĩa xử lý riêng cho response; default status handler ở builder định nghĩa policy dùng chung. `RestTemplate` dùng `ResponseErrorHandler` cho vai trò tương đương.

Cần phân biệt HTTP error response với I/O failure. HTTP 500 là một HTTP response hợp lệ có error status; connect timeout có thể xảy ra trước khi nhận được response nào. Sự khác biệt này quan trọng cho log, retry decision và observability.

Không nên gom mọi failure thành `RuntimeException("call failed")`. Hãy giữ status, header và error detail hữu ích khi an toàn. Ngược lại, không log toàn bộ error body một cách mù quáng vì nó có thể chứa credential hoặc dữ liệu cá nhân.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integration-http-selection-boundary">Ranh giới chọn client của Integration HTTP</a>

<details>
<summary>Xem chi tiết</summary>

Module này dạy **Spring Framework thực hiện synchronous HTTP exchange như thế nào**. Nó không sở hữu quyết định kiến trúc rộng hơn về client nào nên được chuẩn hóa cho toàn hệ thống hay resilience nên thiết kế ra sao.

Các quyết định đó phụ thuộc:

- blocking thread-per-request hay reactive execution;
- service discovery/load balancing;
- propagation của authentication;
- retry và circuit breaker;
- observability convention;
- idempotency và timeout budget.

Framework client API là building block cho các thiết kế trên. Integration HTTP sở hữu cross-client comparison và service-to-service policy; Spring Reactive sở hữu deep mechanics của `WebClient`.

Quy tắc thực tế là hiểu mechanics ở chapter này trước, sau đó ra quyết định kiến trúc ở layer nhìn thấy toàn bộ distributed interaction.

</details>

- [Quay lại đầu trang](#back-to-top)
