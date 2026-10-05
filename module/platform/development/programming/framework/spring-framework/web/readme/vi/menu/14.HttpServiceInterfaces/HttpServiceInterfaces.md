<a id="back-to-top"></a>

# HTTP Service Interface và Client Adapter

## Menu
- [Contract HTTP Interface và @HttpExchange](#http-exchange-contract)
- [HttpServiceProxyFactory](#http-service-proxy-factory)
- [Mô hình HttpExchangeAdapter](#http-exchange-adapter-model)
- [RestClientAdapter và RestTemplateAdapter](#synchronous-http-client-adapters)
- [Argument và return value của service method](#service-method-arguments-and-return-values)
- [Ranh giới Reactive Adapter và WebClient](#reactive-adapter-boundary)

## <a id="http-exchange-contract">Contract HTTP Interface và @HttpExchange</a>

<details>
<summary>Xem chi tiết</summary>

HTTP Service Interface cho phép mô tả HTTP contract bằng Java interface. `@HttpExchange` là annotation cốt lõi; có thể đặt ở interface và method, trong đó metadata cụ thể hơn ở method tinh chỉnh contract dùng chung. Cùng interface contract này cũng có thể được một MVC `@Controller` implement cho server handling; chapter này tập trung vào **client proxy** vì đó là ownership của roadmap milestone hiện tại.

```java
@HttpExchange("/users")
interface UserService {
    @GetExchange("/{id}")
    User find(@PathVariable long id);
}
```

Trong client-proxy use case, Spring đọc metadata của method, resolve request value từ argument, tạo HTTP request, delegate execution sang adapter rồi convert response về return type đã khai báo.

Việc dùng interface như client proxy không tự cung cấp service discovery, retry hay RPC transparency. Latency và HTTP failure semantics vẫn còn. Interface nên phản ánh remote HTTP contract thay vì cố che nó thành một repository nội bộ như thể lời gọi là local.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-service-proxy-factory">HttpServiceProxyFactory</a>

<details>
<summary>Xem chi tiết</summary>

`HttpServiceProxyFactory` tạo runtime proxy implement HTTP Service Interface. Trong Spring Framework 6.1, cấu hình factory bằng `HttpExchangeAdapter`, ví dụ qua `HttpServiceProxyFactory.builderFor(adapter)` hoặc method `exchangeAdapter(...)` trên builder, rồi gọi `createClient(MyService.class)` để tạo proxy.

Đường `HttpExchangeAdapter` là hướng chính trong 6.1; builder method cũ dựa trên `HttpClientAdapter` đã deprecated từ 6.1. Factory delegate việc exchange thay vì bị gắn với một HTTP client cụ thể, nhờ đó interface model độc lập với `RestClient`, `RestTemplate` hay reactive client.

Factory cũng quản lý concern ở mức method như custom argument resolver và conversion support. Thông thường nên tạo nó một lần như infrastructure và dùng để tạo các client proxy ổn định.

Không cần tạo proxy factory mới cho mỗi request. Proxy là metadata-driven infrastructure; dữ liệu thay đổi theo từng call thuộc service method argument.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-exchange-adapter-model">Mô hình HttpExchangeAdapter</a>

<details>
<summary>Xem chi tiết</summary>

`HttpExchangeAdapter` là execution abstraction nằm giữa declarative proxy và HTTP client cụ thể. Proxy chuyển method invocation đã annotate thành `HttpRequestValues`; adapter biết cách thực hiện request và trả về header, body hoặc dạng `ResponseEntity`.

```text
@HttpExchange interface method
→ HttpServiceProxyFactory
→ HttpRequestValues
→ HttpExchangeAdapter
→ HTTP client cụ thể
```

Nhờ boundary này, cùng một interface model có thể dùng với nhiều client implementation khác nhau.

Application code thông thường không cần thao tác adapter trực tiếp. Nó tồn tại để proxy infrastructure phụ thuộc vào exchange contract ổn định thay vì phụ thuộc fluent API của một client cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronous-http-client-adapters">RestClientAdapter và RestTemplateAdapter</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework 6.1 có `HttpExchangeAdapter` đồng bộ cho cả hai client chính:

- `RestClientAdapter` delegate tới `RestClient` đã cấu hình;
- `RestTemplateAdapter` delegate tới `RestTemplate` đã cấu hình.

Adapter được tạo từ client hiện có, nên proxy thừa hưởng các hành vi quan trọng như request factory, converter, interceptor, base URI strategy và error policy từ client đó.

Composition này hữu ích khi migration: ứng dụng có thể áp dụng HTTP Service Interface nhưng vẫn giữ một `RestTemplate` setup đã trưởng thành, hoặc dùng `RestClient` với cùng interface model.

Không nên cấu hình proxy và underlying client bằng các policy mâu thuẫn. Adapter là cầu nối chứ không phải nơi thứ hai để nhân đôi mọi transport option.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-method-arguments-and-return-values">Argument và return value của service method</a>

<details>
<summary>Xem chi tiết</summary>

Argument của HTTP Service method được resolve thành các phần của request. Những contract phổ biến gồm path variable, request parameter, header, cookie, request body, URI và metadata liên quan HTTP method. Tập argument thực tế phụ thuộc các service argument resolver đã đăng ký.

Return type cho proxy biết caller cần phần nào của response. Method có thể chỉ cần decoded body hoặc cần cả status/header theo dạng `ResponseEntity`. Generic body type cần giữ đủ type information để converter decode chính xác.

Nên giữ service method nhỏ và gần với transport contract. Một method có quá nhiều optional parameter không liên quan thường là dấu hiệu remote HTTP API khó hiểu.

Java signature thuận tiện không loại bỏ HTTP semantics. Method trả `User` vẫn có thể thất bại vì network error, HTTP error response hoặc conversion error trước khi có bất kỳ `User` nào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-adapter-boundary">Ranh giới Reactive Adapter và WebClient</a>

<details>
<summary>Xem chi tiết</summary>

HTTP Service Interface không bị giới hạn về mặt ý tưởng ở blocking execution. `ReactorHttpExchangeAdapter` là specialization reactive của `HttpExchangeAdapter`; adapter dựa trên `WebClient` vì vậy có thể hỗ trợ reactive return type ngoài các synchronous return shape.

Module này dừng ở boundary: declarative interface độc lập với client cụ thể, còn adapter quyết định execution behavior. Deep mechanics của `WebClient`, Reactor, backpressure, event loop và reactive error composition thuộc Spring Reactive.

Không khai báo reactive return type trên synchronous adapter rồi giả định proxy sẽ tự trở thành non-blocking. Capability execution đến từ adapter và client được chọn.

```text
HTTP Service Interface
→ declaration model dùng chung

synchronous adapter
→ module này

WebClient/reactive execution
→ Spring Reactive
```

</details>

- [Quay lại đầu trang](#back-to-top)
