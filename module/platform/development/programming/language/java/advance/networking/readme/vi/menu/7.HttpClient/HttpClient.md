<a id="back-to-top"></a>

# HTTP Client của JDK

## Menu
- [Mô hình HttpClient, HttpRequest và HttpResponse](#http-client-model)
- [Cấu hình và tái sử dụng HttpClient](#http-client-configuration)
- [Xây dựng request, header và body](#http-request-building)
- [BodyHandler, BodySubscriber và xử lý response](#http-response-handling)
- [send, sendAsync và mô hình hoàn tất](#http-sync-async)
- [Flow-based request/response body streaming](#http-flow-streaming)
- [Version, redirect, timeout, proxy, authentication và cookie](#http-client-policies)
- [Tái sử dụng kết nối và chia sẻ tài nguyên](#http-client-resource-sharing)
- [Shutdown, close và vòng đời response body](#http-client-lifecycle)
- [Ranh giới giữa JDK HTTP Client và HTTP integration](#http-integration-boundary)

## <a id="http-client-model">Mô hình HttpClient, HttpRequest và HttpResponse</a>

<details>
<summary>Click for details</summary>

`java.net.http.HttpClient` là API HTTP client mức cao của JDK. Thay vì tự mở socket, framing HTTP, parse status line/header và quản lý việc tái sử dụng kết nối, ứng dụng làm việc với ba abstraction chính:

```text
HttpClient
   │  cấu hình + tài nguyên dùng chung
   │
   ├── send/sendAsync(HttpRequest, BodyHandler)
   │                         │
   │                         └── quyết định cách xử lý body của response
   ↓
HttpResponse<T>
   ├── statusCode
   ├── headers
   └── body : T
```

`HttpRequest` mô tả một request đã build: URI, method, headers, timeout, phiên bản HTTP ưu tiên và body publisher tùy chọn. `HttpClient` thực hiện trao đổi HTTP. `HttpResponse<T>` trả metadata của response cùng kiểu body `T` do `BodyHandler<T>` quyết định.

Ví dụ cơ bản:

```java
HttpClient client = HttpClient.newHttpClient();

HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://example.com/"))
        .GET()
        .build();

HttpResponse<String> response =
        client.send(request, HttpResponse.BodyHandlers.ofString());

System.out.println(response.statusCode());
System.out.println(response.body());
```

Kiểu generic của `HttpResponse<T>` không mô tả HTTP payload trên đường truyền; nó mô tả **cách biểu diễn Java sau khi body handler xử lý**. Cùng một response có thể được biểu diễn thành `String`, `byte[]`, `Path`, `InputStream`, `Flow.Publisher<...>` hoặc kiểu khác thông qua handler/subscriber phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-client-configuration">Cấu hình và tái sử dụng HttpClient</a>

<details>
<summary>Click for details</summary>

`HttpClient` được tạo qua `HttpClient.newBuilder()` hoặc `newHttpClient()`. Sau khi build, client là immutable và có thể dùng cho nhiều request. Đây cũng là đơn vị chia sẻ tài nguyên/cấu hình của HTTP API.

```java
HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(5))
        .build();
```

Các thiết lập cấp client như phiên bản protocol ưu tiên, redirect policy, connect timeout, proxy, authenticator, cookie handler và executor được dùng chung cho các request gửi qua client đó. Một số thiết lập có thể được tinh chỉnh ở ranh giới request, ví dụ request timeout hoặc phiên bản ưu tiên.

Oracle API mô tả mỗi `HttpClient` thường quản lý connection pool riêng và tái sử dụng kết nối khi phù hợp. Vì vậy tạo client mới cho mỗi thao tác thường làm mất cơ hội tái sử dụng đó:

```text
Mô hình sở hữu phù hợp
một HttpClient
   ├── request A
   ├── request B
   └── request C

thường lãng phí
new HttpClient -> one request -> close/drop
new HttpClient -> one request -> close/drop
```

"Tái sử dụng client" không có nghĩa giữ một request builder mutable dùng chung giữa nhiều thread. `HttpClient` sau build là đối tượng cấu hình/tài nguyên dùng chung; mỗi request logic thường build một `HttpRequest` immutable riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-request-building">Xây dựng request, header và body</a>

<details>
<summary>Click for details</summary>

`HttpRequest.Builder` tách **metadata của request** khỏi **việc tạo request body**. GET đơn giản có thể không có body; POST/PUT thường nhận một `BodyPublisher`.

```java
HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://example.com/items"))
        .timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(
                "{\"name\":\"book\"}", StandardCharsets.UTF_8))
        .build();
```

`BodyPublishers` cung cấp các nguồn phổ biến như:

- `noBody()` cho request không có body;
- `ofString(...)`, `ofByteArray(...)` cho body đã có trong bộ nhớ;
- `ofFile(...)` cho dữ liệu từ file;
- `ofInputStream(...)` cho stream do supplier cung cấp;
- `fromPublisher(...)` khi body đến từ một `Flow.Publisher<ByteBuffer>`.

`header(name, value)` thêm một giá trị header; `setHeader(name, value)` đặt lại header cùng tên theo contract của builder. `HttpHeaders` ở phía request/response là view chỉ đọc sau khi đối tượng tương ứng đã được build/nhận.

Body publisher chỉ chịu trách nhiệm cung cấp các byte của body. Content type, domain serialization, authentication scheme và idempotency/retry policy không tự xuất hiện chỉ vì chọn một publisher; tầng ứng dụng/integration vẫn phải định nghĩa chúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-response-handling">BodyHandler, BodySubscriber và xử lý response</a>

<details>
<summary>Click for details</summary>

`BodyHandler<T>` quyết định **response body sẽ được tiêu thụ thành kiểu Java nào**. Handler được gọi khi status code và headers đã có, trước khi các byte body thực tế được đọc, và nó trả về một `BodySubscriber<T>` để nhận body.

```text
status + headers đến
        ↓
BodyHandler<T>.apply(ResponseInfo)
        ↓
BodySubscriber<T>
        ↓ tiêu thụ các byte response
CompletionStage<T>
        ↓
HttpResponse<T>.body()
```

Các handler dựng sẵn bao phủ phần lớn trường hợp sử dụng:

```java
BodyHandlers.ofString();       // HttpResponse<String>
BodyHandlers.ofByteArray();    // HttpResponse<byte[]>
BodyHandlers.ofFile(path);     // HttpResponse<Path>
BodyHandlers.discarding();     // body được tiêu thụ rồi bỏ
BodyHandlers.ofInputStream();  // HttpResponse<InputStream>, body còn ở dạng stream
```

Handler có thể chọn subscriber dựa trên metadata của response. Ví dụ chỉ materialize body dạng text khi status là 200, còn status khác thì tiêu thụ body rồi thay bằng chuỗi rỗng:

```java
HttpResponse.BodyHandler<String> handler = info ->
        info.statusCode() == 200
                ? HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8)
                : HttpResponse.BodySubscribers.replacing("");
```

`BodySubscriber<T>` extends `Flow.Subscriber<List<ByteBuffer>>` và chuyển các byte body có thứ tự thành `T`. Nếu chỉ cần mapping phổ biến, dùng `BodyHandlers`/`BodySubscribers` có sẵn trước khi tự viết subscriber; custom subscriber phải tuân thủ Flow demand/cancellation và vòng đời tài nguyên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-sync-async">send, sendAsync và mô hình hoàn tất</a>

<details>
<summary>Click for details</summary>

`send` và `sendAsync` dùng cùng mô hình request/handler nhưng khác cách bên gọi chờ completion.

`send(...)` block thread gọi cho tới khi response khả dụng theo semantics của body handler:

```java
HttpResponse<String> response =
        client.send(request, BodyHandlers.ofString());
```

`sendAsync(...)` trả ngay một `CompletableFuture<HttpResponse<T>>`:

```java
CompletableFuture<Integer> statusFuture = client
        .sendAsync(request, BodyHandlers.ofString())
        .thenApply(HttpResponse::statusCode);
```

Async ở đây là **completion API**. Nó không biến mọi xử lý phía sau thành non-blocking và không yêu cầu bên gọi tự dùng `Selector`. `CompletableFuture` chỉ biểu diễn việc trao đổi HTTP/xử lý response sẽ hoàn tất sau.

Thời điểm response/future trở nên khả dụng còn phụ thuộc body handler. `ofString()` materialize toàn body thành `String`, trong khi `ofInputStream()`, `ofLines()` hoặc `ofPublisher()` có thể trả response sau khi headers có và để bên gọi tiếp tục tiêu thụ body sau đó.

Với client mặc định của JDK, future trả từ `sendAsync` có thể cancel; cancellation cố gắng hủy trao đổi nhưng chỉ theo kiểu best effort vì request có thể đã bắt đầu được xử lý. Tương tự, interrupt `send` khiến cách triển khai mặc định cố hủy trao đổi rồi ném `InterruptedException`. Mã không nên suy luận "cancel thành công ở phía Java" đồng nghĩa server từ xa chắc chắn chưa thấy request.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-flow-streaming">Flow-based request/response body streaming</a>

<details>
<summary>Click for details</summary>

Request và response body của JDK HTTP Client có ranh giới trực tiếp với `java.util.concurrent.Flow`.

Ở chiều gửi, `HttpRequest.BodyPublisher` extends `Flow.Publisher<ByteBuffer>`. Khi gửi request có body, client subscribe publisher để nhận các `ByteBuffer` đầu ra theo thứ tự. `BodyPublishers.fromPublisher(...)` là adapter khi ứng dụng đã có một publisher:

```java
Flow.Publisher<ByteBuffer> source = createBodyPublisher();

HttpRequest request = HttpRequest.newBuilder(uri)
        .POST(HttpRequest.BodyPublishers.fromPublisher(source))
        .build();
```

Ở chiều nhận, `BodySubscriber<T>` extends `Flow.Subscriber<List<ByteBuffer>>`. Nếu muốn đưa body ra dưới dạng publisher thay vì materialize ngay:

```java
HttpResponse<Flow.Publisher<List<ByteBuffer>>> response =
        client.send(request, HttpResponse.BodyHandlers.ofPublisher());

Flow.Publisher<List<ByteBuffer>> body = response.body();
body.subscribe(mySubscriber);
```

Publisher do `ofPublisher()` trả về chỉ cho phép subscribe **một lần**. Subscriber phải request dữ liệu cho tới completion/error, hoặc cancel nếu không tiếp tục. Nếu không subscribe, không request hoặc không cancel, body có thể không hoàn tất và giữ trao đổi HTTP/tài nguyên còn sống.

Flow đưa demand/cancellation vào ranh giới transport của body; nó không tự định nghĩa chiến lược backpressure nghiệp vụ của toàn ứng dụng. Các mẫu composition concurrency/reactive sâu hơn thuộc module chuyên trách.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-client-policies">Version, redirect, timeout, proxy, authentication và cookie</a>

<details>
<summary>Click for details</summary>

Không phải mọi thiết lập HTTP nằm cùng một phạm vi. Đặt policy đúng ranh giới giúp tránh cấu hình tưởng là theo từng request nhưng thực tế lại dùng chung toàn client.

| Mối quan tâm | Ranh giới chính trong JDK API | Ý nghĩa |
|---|---|---|
| Preferred HTTP version | `HttpClient.Builder.version(...)`, có thể đặt preferred version trên request | Chọn preference HTTP/1.1 hoặc HTTP/2; server/mạng vẫn tham gia negotiation thực tế |
| Redirect | `HttpClient.Builder.followRedirects(...)` | Policy `NEVER`, `NORMAL`, `ALWAYS` cho client |
| Connect timeout | `HttpClient.Builder.connectTimeout(...)` | Giới hạn thời gian thiết lập kết nối |
| Request timeout | `HttpRequest.Builder.timeout(...)` | Timeout gắn với request cụ thể |
| Proxy | `HttpClient.Builder.proxy(...)` | `ProxySelector` cấp client |
| Authentication | `HttpClient.Builder.authenticator(...)` | `Authenticator` cấp client cho HTTP authentication challenge |
| Cookies | `HttpClient.Builder.cookieHandler(...)` | `CookieHandler` dùng chung cho các request của client |
| Async executor | `HttpClient.Builder.executor(...)` | Executor cho công việc bất đồng bộ/phụ thuộc của client; nếu không đặt, cách triển khai vẫn có executor mặc định nội bộ |

Request còn sở hữu URI, method, headers và body. Policy của client là nền cấu hình dùng chung; request là một trao đổi cụ thể.

Các thiết lập này mô tả **cơ chế HTTP của JDK**. Quyết định cấp integration như retry policy theo method/status, circuit breaker, service discovery, OAuth flow, domain error mapping hoặc chọn HTTP client framework thuộc module integration/security tương ứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-client-resource-sharing">Tái sử dụng kết nối và chia sẻ tài nguyên</a>

<details>
<summary>Click for details</summary>

Một `HttpClient` không chỉ là đối tượng chứa method `send`. Oracle API mô tả client cung cấp **cấu hình và khả năng chia sẻ tài nguyên** cho mọi request đi qua nó, và cách triển khai mặc định thường quản lý connection pool có thể tái sử dụng kết nối khi cần.

Điều này dẫn tới hai nguyên tắc về trách nhiệm sở hữu:

1. Tái sử dụng client ở phạm vi phù hợp với nhóm request cùng cấu hình thay vì tạo một client cho từng lời gọi.
2. Nếu nhiều thành phần dùng chung client, chúng cũng đang dùng chung vòng đời của tài nguyên bên dưới; một thành phần không nên tự đóng client nếu nó không sở hữu client đó.

Việc tái sử dụng kết nối là hành vi của cách triển khai/tài nguyên, không phải bảo đảm của API rằng request B chắc chắn dùng đúng socket của request A. Phiên bản protocol, đích đến, thời điểm response body hoàn tất, hành vi của server và các điều kiện transport khác đều ảnh hưởng khả năng tái sử dụng.

Đặc biệt, response body chưa được tiêu thụ có thể giữ trao đổi/kết nối ở trạng thái bận. Handler materialize như `ofString()` thường hoàn tất body trước khi trả giá trị body, còn streaming handler chuyển trách nhiệm tiếp tục tiêu thụ/đóng sang bên gọi.

```text
Dùng chung HttpClient
   ├── cấu hình dùng chung
   ├── kết nối/tài nguyên được quản lý
   └── nhiều trao đổi HttpRequest
          ↓
   vòng đời body phải được hoàn tất
   để tài nguyên có thể được giải phóng/tái sử dụng phù hợp
```

Không dựa vào chi tiết triển khai của connection pool để xây dựng tính đúng đắn của protocol ứng dụng. Tái sử dụng là tối ưu hóa/quản lý tài nguyên của client; tính đúng đắn phải dựa trên HTTP semantics và contract của ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-client-lifecycle">Shutdown, close và vòng đời response body</a>

<details>
<summary>Click for details</summary>

Java 21 bổ sung API vòng đời rõ ràng cho `HttpClient`; client implements `AutoCloseable` và có `shutdown()`, `shutdownNow()`, `awaitTermination(...)`, `isTerminated()` và `close()`.

Cần phân biệt rõ contract của API với hành vi của cách triển khai. **JDK built-in client** được tạo bởi `newHttpClient()` / `newBuilder().build()` override các method này để cung cấp shutdown theo kiểu best effort. Base class `HttpClient` cũng có cách triển khai mặc định, nhưng chỉ việc method tồn tại không có nghĩa một subclass tùy chỉnh bất kỳ sẽ sở hữu cùng cơ chế shutdown thực tế. Các hành vi dưới đây mô tả cách triển khai built-in mà ứng dụng thông thường nhận từ factory của JDK.

- `shutdown()` bắt đầu orderly shutdown: request đã submit được phép chạy tới completion, request mới không còn được nhận; method không chờ client kết thúc.
- `awaitTermination(duration)` chờ sau shutdown tới khi các thao tác hoàn tất, hết duration hoặc thread bị interrupt.
- `shutdownNow()` cố bắt đầu immediate shutdown, nhưng việc dừng các thao tác đang chạy chỉ theo kiểu best effort.
- `close()` bắt đầu orderly shutdown **và chờ** tới khi các thao tác hoàn tất và client kết thúc.

```java
HttpClient client = HttpClient.newHttpClient();
try (client) {
    HttpResponse<String> response =
            client.send(request, BodyHandlers.ofString());
}
```

Vòng đời của **response body** quyết định vòng đời request. Với `BodyHandlers.ofInputStream()`, response có thể được trả ngay sau headers; bên gọi phải đọc tới EOF hoặc đóng stream:

```java
HttpResponse<InputStream> response =
        client.send(request, BodyHandlers.ofInputStream());

try (InputStream body = response.body()) {
    consume(body);
}
```

Đóng stream trước khi đọc hết có thể khiến kết nối HTTP bên dưới bị đóng và không thể tái sử dụng, nhưng vẫn giải phóng tài nguyên của trao đổi. `ofLines()` tương tự yêu cầu đọc hết hoặc đóng `Stream`; `ofPublisher()` yêu cầu subscribe rồi request tới completion/error hoặc cancel subscription.

Java 21 API cảnh báo rằng nếu các stream trả về không được đọc/đóng, hoặc custom subscriber không request/cancel, orderly shutdown có thể bị đình trệ. Vì `close()` chờ quá trình kết thúc có trật tự, một body bị bỏ quên có thể khiến `close()` phải chờ rất lâu. Do đó **trách nhiệm sở hữu body là một phần của tính đúng đắn khi shutdown client**, không chỉ là chuyện dọn bộ nhớ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-integration-boundary">Ranh giới giữa JDK HTTP Client và HTTP integration</a>

<details>
<summary>Click for details</summary>

Module Networking sở hữu cách **JDK HTTP Client API hoạt động**: abstraction client/request/response, body streaming, sync/async completion, cấu hình policy, vòng đời kết nối/tài nguyên và Java 21 shutdown semantics.

Nó không sở hữu toàn bộ bài toán HTTP integration của ứng dụng. Khi câu hỏi chuyển thành các quyết định như:

- service A nên retry request nào và theo backoff nào;
- chọn JDK `HttpClient`, Spring `RestClient`/`WebClient`, Feign hay framework khác;
- ánh xạ HTTP status/body thành domain error ra sao;
- circuit breaker, bulkhead, rate limit hoặc observability policy đặt ở đâu;
- OAuth/token refresh/service credentials tổ chức thế nào;
- API contract/versioning và cách điều phối request giữa các service thiết kế ra sao;

thì lộ trình học phải chuyển sang module integration, security hoặc architecture sở hữu nội dung tương ứng.

Ranh giới này giúp dùng JDK API đúng mà không nhầm một cơ chế transport/client với toàn bộ kiến trúc integration:

```text
Networking
    cơ chế JDK HttpClient
          ↓ được sử dụng bởi
HTTP Integration
    mẫu ứng dụng/client + resilience + policy miền nghiệp vụ
```

Hiểu ranh giới cũng giúp tránh abstraction leak theo chiều ngược lại: một framework HTTP client cuối cùng vẫn dựa trên cơ chế mạng/tài nguyên nào đó, nhưng người học không cần học mọi integration pattern để hiểu `HttpClient` của JDK.

Chương kế tiếp vẫn ở cùng họ `java.net.http` nhưng đổi communication model: thay vì các request/response exchange độc lập, WebSocket client của JDK quản lý một kết nối bidirectional message-oriented lâu dài với listener demand, fragmentation, send completion và close/error lifecycle.

</details>

- [Quay lại đầu trang](#back-to-top)
