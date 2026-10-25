<a id="back-to-top"></a>

# Bất đồng bộ MVC

## Menu
- [Servlet async và khác biệt giữa giải phóng luồng yêu cầu với I/O không chặn](#mvc-async-processing-001)
- [Callable, DeferredResult, WebAsyncTask và lựa chọn mô hình hoàn thành](#mvc-async-processing-002)
- [Cấu hình TaskExecutor, timeout và giới hạn tài nguyên](#mvc-async-processing-003)
- [Luồng dispatch ASYNC và xử lý kết quả hoặc ngoại lệ](#mvc-async-processing-004)
- [ResponseBodyEmitter, SseEmitter và StreamingResponseBody](#mvc-async-processing-005)
- [Trả về kiểu reactive trong MVC và giới hạn khi ghi phản hồi](#mvc-async-processing-006)
- [Phát hiện ngắt kết nối, dọn tài nguyên và xử lý lỗi sau khi đã gửi header](#mvc-async-processing-007)
- [Truyền ngữ cảnh, an toàn luồng và tránh sử dụng executor không giới hạn](#mvc-async-processing-008)

## <a id="mvc-async-processing-001">Servlet async và khác biệt giữa giải phóng luồng yêu cầu với I/O không chặn</a>

<details>
<summary>Xem chi tiết</summary>

# Servlet async và khác biệt giữa giải phóng luồng yêu cầu với I/O không chặn trong Spring MVC

## 1. Vấn đề thực tế: request chờ lâu có nhất thiết phải chiếm Servlet thread?

Hãy hình dung `GET /api/reports/42` cần 3 giây để chờ kết quả từ một dịch vụ khác. Trong MVC đồng bộ truyền thống, thread của Servlet container nhận request, vào controller, gọi dịch vụ và **đứng chờ** trước khi ghi HTTP response. Nếu cùng lúc có nhiều request chờ I/O, số lượng request threads bận sẽ tăng, mặc dù CPU không thật sự tính toán nhiều.

Spring MVC hỗ trợ **Servlet asynchronous request processing** (Servlet 3.0+) để trả lại **thread ban đầu** cho container trong khi HTTP response **vẫn đang mở**. Kết quả sẽ được cung cấp sau qua `Callable`, `DeferredResult`, `WebAsyncTask` hoặc dạng return value tương thích. Sau đó Spring thực hiện **ASYNC dispatch** và hoàn tất response.

Tuy nhiên, có một nhầm lẫn rất phổ biến:

> **Giải phóng Servlet request thread không đồng nghĩa với việc tất cả I/O trong ứng dụng đều non-blocking.**

Nếu controller trả `Callable` mà bên trong vẫn gọi JDBC đồng bộ, thread của **executor** sẽ bị chặn trong thời gian truy vấn. Nếu ghi tệp bằng `StreamingResponseBody` với `OutputStream.write(...)`, công việc đã rời initial container thread nhưng lời gọi ghi vẫn có thể **blocking**. Cơ chế I/O không chặn ở Servlet 3.1 là tầng khác, dựa trên `ServletInputStream.isReady()`/`ReadListener` và `ServletOutputStream.isReady()`/`WriteListener`.

**Phạm vi bài:** Spring **Web MVC trên Servlet stack**, Java 17, Spring Boot 3.3.5 / Spring Framework 6.1.x; phân biệt **Servlet async**, **worker executors**, **non-blocking Servlet I/O** và **Spring WebFlux**. Bài giải thích vòng đời request, các loại return values, timeout/cancellation, cấu hình thread pool, kiểm thử MockMvc và những giới hạn về tải hệ thống.

### Kết quả cần đạt

Sau bài này, bạn có thể:

1. Mô tả request đồng bộ và Servlet async theo từng thread/dispatch.
2. Phân biệt "waiting không giữ Servlet thread" với "operation không giữ bất kỳ thread nào khi chờ".
3. Giải thích `Callable`, `DeferredResult`, `WebAsyncTask` và sự liên hệ với `CompletableFuture`.
4. Biết khi nào `ResponseBodyEmitter`, `SseEmitter`, `StreamingResponseBody` giải phóng request thread nhưng vẫn thực hiện ghi blocking.
5. Phân biệt Servlet 3.0 asynchronous request lifecycle và Servlet 3.1 non-blocking I/O callbacks.
6. Cấu hình bounded `AsyncTaskExecutor` và timeout trên Spring MVC; xử lý overload và context propagation.
7. Viết integration-style controller và MockMvc async tests, quan sát khác biệt giữa `REQUEST` và `ASYNC dispatch`.
8. Quyết định khi nào giữ Spring MVC và khi nào xem xét WebFlux hoặc driver/client thực sự non-blocking.

## 2. Bốn khái niệm cần tách riêng

| Khái niệm | Nó giải quyết điều gì? | Có làm JDBC/HTTP client blocking thành non-blocking không? |
| --- | --- | --- |
| **Servlet request thread** | Thread container đang xử lý filter/servlet/controller cho một dispatch | Không |
| **Servlet async lifecycle** | Cho phép thread initial request thoát trong khi response còn mở, sau đó dispatch lại | Không |
| **Async executor / worker pool** | Thực thi công việc ở thread khác, có thể là các blocking calls | Không |
| **Non-blocking I/O** | I/O readiness/callback/async client; thread không phải đợi socket sẵn sàng bằng cách block | Chỉ khi cả API/driver/pipeline thực sự hỗ trợ |

Ví dụ đồng bộ:

```text
Servlet thread (http-nio-8080-exec-1)
  nhận request -> filter -> controller
  -> blocking HTTP call / JDBC (chờ 3 giây)
  -> serialize JSON -> ghi response -> kết thúc
  [thread bị chiếm suốt thời gian chờ]
```

Ví dụ `Callable`:

```text
Servlet thread (http-nio-8080-exec-1)
  nhận request -> controller trả Callable
  -> request.startAsync()
  -> thoát filter/servlet chain, trả thread về pool

MVC worker thread (mvc-work-1)
  chạy Callable -> blocking JDBC/HTTP call (chờ 3 giây)
  -> tạo Java result -> báo kết quả

Servlet container thread (có thể là thread khác)
  xử lý ASYNC dispatch
  -> MVC serialize result -> ghi HTTP response -> kết thúc
```

Trong ví dụ thứ hai, **container thread ban đầu được giải phóng**, nhưng vẫn có **mvc-work-1** bị block. Vì vậy lợi ích chủ yếu là **tách tài nguyên thread container khỏi công việc lâu** và kiểm soát pool/chính sách riêng, không phải tạo phép màu biến ứng dụng thành I/O non-blocking.

Ví dụ `DeferredResult` đợi một external event/callback thực sự non-blocking:

```text
Servlet thread -> trả DeferredResult -> startAsync -> thoát
                        |
                        | request đang pending, chưa có result
                        | không bắt buộc có worker thread ngủ chờ
                        v
External callback/event loop nhận kết quả sau đó
  -> deferredResult.setResult(value)
                        |
                        v
Servlet ASYNC dispatch -> ghi HTTP response -> complete
```

Nếu bạn triển khai DeferredResult bằng `new Thread(() -> Thread.sleep(...))` cho mỗi request, bạn vẫn tiêu thụ một thread, chỉ là thread khác. **DeferredResult tự nó không làm producer non-blocking.**

## 3. Từ Servlet 3.0: startAsync(), AsyncContext và ASYNC dispatch

Servlet 3.0 bổ sung asynchronous request processing. Ở tầng Servlet API, ý tưởng là:

```java
AsyncContext asyncContext = request.startAsync();
// Có thể đăng ký listener, timeout hoặc trigger công việc ngoài thread này.
// Khi kết quả sẵn sàng: asyncContext.dispatch() hoặc asyncContext.complete().
```

Đây là **snippet Servlet-level**; `request` là `HttpServletRequest` trong Servlet hỗ trợ async, import `jakarta.servlet.AsyncContext`. Không đặt trực tiếp đoạn này vào method MVC vốn đã trả `Callable`/`DeferredResult` rồi cố điều khiển lifecycle song song: **Spring MVC quản lý startAsync/dispatch/complete cho bạn**.

### 3.1. Vòng đời asynchronous request trong Spring MVC

Giả sử endpoint trả `DeferredResult`:

1. Container nhận HTTP request và cấp một Servlet thread.
2. DispatcherServlet chạy handler mapping, binding, interceptors và controller.
3. Controller tạo `DeferredResult` rồi **return sớm**, chưa có body kết quả.
4. MVC gọi cơ chế Servlet `request.startAsync()` và thoát filter/servlet chain.
5. Container thread initial request trở lại pool; response **chưa bị commit/close**.
6. Một thread/callback khác hoàn thành `DeferredResult.setResult(value)` hoặc `setErrorResult(...)`.
7. MVC yêu cầu **ASYNC dispatch** tới Servlet container.
8. DispatcherServlet được gọi lại trong dispatch mới; nó dùng **concurrent result** đã có, **không gọi lại business controller method từ đầu** để tạo result lần hai.
9. MVC áp dụng return-value handling/message converter và ghi response; async processing được complete.
10. Timeout, client disconnect, exceptions và async listeners có thể can thiệp vào flow trên.

```text
HTTP Request
    |
    v
REQUEST dispatch (Servlet thread #1)
    |
    v
Spring MVC controller -> DeferredResult / Callable
    |
    v
Servlet startAsync(); request thread #1 released
    |
    +--------------------- waiting --------------------+
    |                                                  |
    |           producer event / worker result          |
    |                      arrives                     |
    +--------------------------+-----------------------+
                               |
                               v
                  DeferredResult.setResult(...)
                               |
                               v
ASYNC dispatch (Servlet thread #2, may equal #1)
                               |
                               v
           Spring MVC return-value handling + response
                               |
                               v
                       Request complete
```

**Không giả định dispatch thứ hai dùng cùng thread**: container có quyền dùng một thread khác. Tương tự, `@RequestScope`, MDC, SecurityContext, transaction-bound state và những `ThreadLocal` custom **không tự lan truyền** qua mọi worker/dispatch; xem mục 12.

### 3.2. Servlet async phải được bật ở đâu?

Để cơ chế hoạt động, Servlet/filter chain tương ứng phải hỗ trợ asynchronous processing:

- Servlet cần `asyncSupported=true`.
- Các filters trên đường xử lý không được chặn async và cần dispatcher mappings thích hợp (`jakarta.servlet.DispatcherType.ASYNC`) khi phải hoạt động trên async redispatch.
- Với Spring Boot embedded Servlet container và Spring MVC auto-configuration phổ biến, nhiều khai báo được cấu hình sẵn phù hợp.
- Nếu đăng ký filters/servlets thủ công, sử dụng `FilterRegistrationBean.setAsyncSupported(true)` hoặc Servlet 3.x registration tương ứng theo kiến trúc.
- Khi cần filter chạy lại ở dispatch `ASYNC`, đảm bảo mapping bao gồm dispatch type này; nhưng đừng đơn giản chạy mọi filter logic hai lần mà không xét semantics.

Một lỗi thường thấy:

```text
java.lang.IllegalStateException:
Async support must be enabled on a servlet and for all filters involved...
```

Thông báo cụ thể tùy container/framework. Nó thường liên quan filter hoặc servlet không hỗ trợ async, không phải do return type `Callable` sai.

### 3.3. Vì sao ASYNC dispatch quan trọng?

Spring MVC không chỉ nhận giá trị từ thread ngoài rồi viết trực tiếp như thể vẫn ở request ban đầu. Redispatch giúp tái sử dụng pipeline MVC để xử lý body, status, converters và error handling theo lifecycle thống nhất.

Các lifecycle callbacks trong `HandlerInterceptor` cần hiểu async: `afterConcurrentHandlingStarted(...)` của `AsyncHandlerInterceptor` chạy khi request chuyển sang async; `postHandle`/`afterCompletion` trong initial dispatch không luôn xảy ra theo thứ tự như request sync. Tại subsequent dispatch, phần xử lý MVC tiếp tục và completion được gọi theo lifecycle. Nếu dùng timing/trace interceptors, cần chống log đôi và cleanup chưa đúng.

## 4. Spring MVC có những kiểu return value async nào?

### 4.1. Callable<T>: chuyển tác vụ sang MVC async executor

```java
@GetMapping("/price")
public Callable<ResponseEntity<PriceDto>> price() {
    return () -> {
        PriceDto dto = blockingPriceService.loadPrice();
        return ResponseEntity.ok(dto);
    };
}
```

Đây là **snippet** với `PriceDto` và `blockingPriceService` giả định đã tồn tại; không phải controller runnable độc lập. Khi controller trả Callable, MVC bắt đầu Servlet async, rồi gửi Callable tới `AsyncTaskExecutor`. Trong Callable, các thao tác blocking vẫn **chiếm worker thread**. Khi Callable trả kết quả, Spring redispatch và hoàn tất.

**Phù hợp khi:**
- Bạn có business logic lâu hoặc blocking mà cần tách khỏi Servlet threads.
- Muốn dùng executor riêng với queue/pool size đã cấu hình.
- Kết quả trả một lần, không phải stream vô hạn.

**Không phù hợp để tuyên bố "không có thread chờ"** nếu Callable gọi `Thread.sleep`, JDBC blocking, file I/O blocking hoặc sync REST client.

### 4.2. DeferredResult<T>: hoàn tất từ sự kiện bên ngoài

```java
@GetMapping("/status")
public DeferredResult<ResponseEntity<StatusDto>> status() {
    DeferredResult<ResponseEntity<StatusDto>> result =
            new DeferredResult<>(5000L);

    queue.registerListener(event -> {
        result.setResult(ResponseEntity.ok(new StatusDto(event.state())));
    });

    result.onTimeout(() ->
            result.setErrorResult(
                ResponseEntity.status(503).build()));

    result.onCompletion(() -> queue.unregisterListener(result));
    return result;
}
```

Đây là **pseudocode Java dạng snippet** vì `queue`, `StatusDto` và event API không được khai báo, đồng thời `unregisterListener` ở hệ thống thật phải dùng chính registration token/listener phù hợp. Ý nghĩa là kết quả có thể đến từ message broker callback, non-blocking HTTP client callback hoặc event stream, **không cần tạo một thread ngủ chờ cho mỗi request**.

- `setResult(T)`: cung cấp kết quả để MVC redispatch.
- `setErrorResult(Object)`: cung cấp exception hoặc error result để MVC xử lý.
- `onTimeout(Runnable)`: xử lý khi container báo thời gian chờ async đã hết.
- `onError(Consumer<Throwable>)`: phản ứng với lỗi async.
- `onCompletion(Runnable)`: giải phóng resources/registrations sau khi request kết thúc.

**Lưu ý vòng đời:** `setResult` có thể thất bại/không còn tác dụng khi result đã được set bởi timeout/error; không đợi đến khi HTTP response đóng rồi tiếp tục gửi. Khi dùng queue/listener, cleanup trên completion/cancel/timeout để tránh memory leak.

### 4.3. WebAsyncTask<T>: Callable với cấu hình riêng

`WebAsyncTask` là wrapper của Callable, có thể chỉ định timeout, executor và callbacks cho một endpoint:

```java
@GetMapping("/report")
public WebAsyncTask<ResponseEntity<String>> report() {
    WebAsyncTask<ResponseEntity<String>> task =
        new WebAsyncTask<>(3000L,
            () -> ResponseEntity.ok("ready"));

    task.onTimeout(() ->
        ResponseEntity.status(503).body("timed out"));
    return task;
}
```

Snippet này có thể đặt trong `@RestController` với imports `GetMapping`, `ResponseEntity`, `WebAsyncTask`. Timeout `3000L` tính theo milliseconds. `WebAsyncTask` **không tự biến Callable thành non-blocking I/O**; công việc vẫn chạy trên executor.

### 4.4. CompletionStage/CompletableFuture

Spring MVC có thể nhận các async single-value return types như `CompletionStage`/`CompletableFuture` qua return-value adaptation:

```java
@GetMapping("/remote")
public CompletableFuture<ResponseEntity<String>> remote() {
    return service.fetchAsync()
            .thenApply(ResponseEntity::ok);
}
```

Đây là snippet vì `service.fetchAsync()` chưa khai báo. Lợi ích phụ thuộc implementation của `fetchAsync()`:

- Nếu nó dùng HTTP client async thật sự, thời gian chờ mạng có thể không chiếm một worker thread riêng.
- Nếu nó dùng `CompletableFuture.supplyAsync(() -> blockingService.call())`, có worker thread bị block trong `call()`.
- Nếu dùng `supplyAsync` mà không chỉ định executor, mặc định có thể dùng common ForkJoinPool — **không nên mặc định đó là thread pool phù hợp cho blocking công việc production**.

Không đánh giá non-blocking chỉ dựa vào signature `CompletableFuture`.

### 4.5. StreamingResponseBody, ResponseBodyEmitter và SseEmitter

| Loại | Dùng để làm gì | Có chắc non-blocking socket I/O không? |
| --- | --- | --- |
| `StreamingResponseBody` | Ghi raw bytes dần ra `OutputStream`, ví dụ large download | **Không.** MVC dùng thread thực thi task; `write` có thể blocking |
| `ResponseBodyEmitter` | Gửi nhiều đối tượng/phần response theo thời gian thông qua converters | **Không bảo đảm.** Việc ghi Servlet response có thể blocking |
| `SseEmitter` | Server-Sent Events (`text/event-stream`) trên HTTP kết nối lâu | Không biến transport/write pipeline thành fully non-blocking |
| `Callable` | Một kết quả được tạo trong executor | Worker có thể block |
| `DeferredResult` | Một kết quả do producer/event hoàn tất | Phụ thuộc producer, có thể không cần thread chờ |

Với SSE, cần heartbeat, timeout, client disconnect, cleanup subscriptions và policy giới hạn concurrent connections. Kết nối còn mở vẫn tiêu thụ socket descriptors, buffers, server state và tài nguyên khác dù initial Servlet thread đã được giải phóng.

## 5. Servlet 3.1: I/O không chặn thật sự là tầng khác

Servlet **3.0 asynchronous request lifecycle** giải phóng request thread trong lúc chờ result. Servlet **3.1 non-blocking I/O** thêm APIs để đọc/ghi khi stream sẵn sàng, thay vì block thread ở socket I/O:

- `ServletInputStream.isReady()` / `isFinished()` / `setReadListener(ReadListener)`.
- `ServletOutputStream.isReady()` / `setWriteListener(WriteListener)`.
- `ReadListener.onDataAvailable()`, `onAllDataRead()`, `onError(...)`.
- `WriteListener.onWritePossible()` và `onError(...)`.

### 5.1. Blocking I/O

Với OutputStream thông thường:

```java
// Snippet trong một Servlet/synchronous code path.
OutputStream out = response.getOutputStream();
out.write(bigChunk); // Có thể BLOCK đến khi đủ buffer/network capacity.
```

Khi client đọc rất chậm hoặc network buffer đầy, `write` có thể block một thread đang gọi. Chuyển đoạn này vào một `StreamingResponseBody` worker **không thay đổi bản chất blocking** của `write`.

### 5.2. Non-blocking output readiness

Với API Servlet 3.1, mô hình đúng là:

```text
request vào Servlet có async support
  -> startAsync()
  -> getOutputStream().setWriteListener(...)
  -> callback onWritePossible()
       while (output.isReady() && còn bytes):
           write phần có thể ghi
       nếu output không ready: THOÁT callback, không busy-spin
  -> container gọi lại onWritePossible() khi sẵn sàng
  -> khi ghi xong: asyncContext.complete()
```

Snippet minh họa các lời gọi:

```java
ServletOutputStream output = response.getOutputStream();

output.setWriteListener(new WriteListener() {
    @Override
    public void onWritePossible() throws IOException {
        while (output.isReady() && hasPendingBytes()) {
            writeNextChunk(output);
        }
        if (allBytesWritten()) {
            asyncContext.complete();
        }
    }

    @Override
    public void onError(Throwable error) {
        handleFailure(error);
        asyncContext.complete();
    }
});
```

Đây là **pseudocode định hướng**: `hasPendingBytes`, `writeNextChunk`, `allBytesWritten`, `handleFailure` và `asyncContext` phải được tự triển khai và đồng bộ hóa, không phải class runnable sẵn. Cần tránh gọi `complete()` nhiều lần, xử lý lỗi/partial writes và tránh block trong callbacks. Đầu vào tương tự dùng `ReadListener` và vòng lặp `while(input.isReady() && !input.isFinished())`.

**Điều kiện quan trọng:** callbacks phải tôn trọng readiness, không gọi I/O trái contract, không cố spin khi `isReady()==false`. I/O non-blocking ở đây giải quyết việc thread bị block bởi **network stream readiness**, nhưng application callbacks vẫn có thể block nếu bạn gọi JDBC blocking ở trong chúng.

### 5.3. So sánh hai cơ chế bằng câu ngắn

- `request.startAsync()`: "Tôi rời Servlet thread hiện tại, giữ HTTP exchange sống và sẽ tiếp tục sau."
- `ServletOutputStream.setWriteListener(...)`: "Hãy gọi tôi khi socket output có thể ghi; tôi không cần thread đứng chờ buffer trống."
- `Callable`: "Hãy chạy logic này trên worker, thường vẫn synchronous/blocking bên trong."
- `DeferredResult`: "Tôi sẽ cung cấp kết quả khi producer bên ngoài báo xong; không cần worker riêng đứng chờ nếu producer là event-driven."
- `WebFlux + non-blocking client/driver`: "Cả pipeline có thể theo readiness/signals và backpressure thay vì dùng một thread chờ trên mỗi blocking operation", **với điều kiện không nhúng blocking calls vào event-loop**.

Không đồng nhất **asynchronous API** với **non-blocking implementation**.

## 6. So sánh các mô hình theo thread consumption

Giả sử có 100 HTTP requests cùng lúc, mỗi request phải chờ 2 giây một remote service. Đây là **ví dụ tư duy**, không phải benchmark hay số liệu throughput.

| Cách viết | Servlet thread initial request | Trong lúc chờ remote |
| --- | --- | --- |
| Controller sync + blocking client | Giữ thread đến lúc hoàn tất | **Servlet thread** chờ |
| Controller trả Callable + blocking client | Giải phóng sớm | **Executor worker thread** chờ |
| DeferredResult + executor chạy blocking client | Giải phóng sớm | **App worker thread** chờ |
| DeferredResult + HTTP client event-driven non-blocking | Giải phóng sớm | Không cần một worker đứng chờ từng request; vẫn có event-loop/runtime threads |
| Servlet 3.1 WriteListener | Giải phóng khi socket output chưa sẵn sàng | Container callbacks khi có thể ghi, nếu code tuân contract |
| Spring WebFlux + end-to-end reactive I/O | Mô hình khác, thường event-driven | Ít thread cho nhiều kết nối chờ, nếu các dependencies không block |

**Không suy ra** "100 requests async = 0 threads". Dù waiting không có thread riêng, server vẫn cần thread xử lý callback, CPU, serialization, dispatch, GC, network stack; còn cần memory cho mỗi pending request, sockets và timeout state.

Nếu JDBC driver chỉ cung cấp blocking API, dùng WebFlux hoặc CompletableFuture cũng không tự biến JDBC thành non-blocking. Có thể offload blocking call lên bounded scheduler/executor, nhưng đó vẫn là **thread chuyển nơi block**, không phải non-blocking driver.

## 7. Demo hoàn chỉnh: Java 17 + Spring Boot 3.3.5 + Spring MVC async

Demo có bốn endpoint:

- `GET /api/async/sync`: trả kết quả đồng bộ, thread là Servlet container thread.
- `GET /api/async/callable`: trả `Callable` và ghi nhận tên worker thread `mvc-work-*`.
- `GET /api/async/deferred`: trả `DeferredResult`; producer chạy trong thread pool `producer-*`; dùng callback hoàn thành.
- `GET /api/async/web-task`: dùng `WebAsyncTask` để gắn timeout cho một Callable.

**Lưu ý:** Demo không chờ remote service thật và không cố tình `Thread.sleep`. Mục tiêu là kiểm chứng **lifecycle/executor**, không chứng minh latency hoặc throughput khi hệ thống có tải. DeferredResult của demo vẫn có worker producer để tạo dữ liệu; nó **không phải** ví dụ producer non-blocking end-to-end.

### 7.1. Cấu trúc project

```text
servlet-async-demo/
├── pom.xml
└── src/
    ├── main/java/example/asyncmvc/
    │   ├── AsyncMvcApplication.java
    │   ├── AsyncMvcConfig.java
    │   └── AsyncDemoController.java
    └── test/java/example/asyncmvc/
        └── AsyncDemoControllerTest.java
```

**`pom.xml`:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>
    <groupId>example</groupId>
    <artifactId>servlet-async-demo</artifactId>
    <version>1.0.0</version>
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
```

Dự án chỉ cần web starter cho MVC, Jackson và embedded Servlet container; thêm starter-test để chạy MockMvc/JUnit. Không cần WebFlux, không cần Reactor và không cần thư viện reactive để dùng Servlet async.

**`src/main/java/example/asyncmvc/AsyncMvcApplication.java`:**

```java
package example.asyncmvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AsyncMvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(AsyncMvcApplication.class, args);
    }
}
```

### 7.2. Đăng ký bounded executor và timeout

Đối với `Callable`, Spring MVC có một `AsyncTaskExecutor`. Nếu không cấu hình pool phù hợp, default executor của MVC không phải lựa chọn production dưới tải. Cấu hình:

**`src/main/java/example/asyncmvc/AsyncMvcConfig.java`:**

```java
package example.asyncmvc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AsyncMvcConfig implements WebMvcConfigurer {

    @Bean("mvcAsyncExecutor")
    public ThreadPoolTaskExecutor mvcAsyncExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(4);
        pool.setMaxPoolSize(8);
        pool.setQueueCapacity(32);
        pool.setThreadNamePrefix("mvc-work-");
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(5);
        return pool;
    }

    @Bean("producerExecutor")
    public ThreadPoolTaskExecutor producerExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(4);
        pool.setQueueCapacity(16);
        pool.setThreadNamePrefix("producer-");
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(5);
        return pool;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncExecutor());
        configurer.setDefaultTimeout(5000L);
    }
}
```

Giải thích:

- `mvcAsyncExecutor` chạy `Callable`/`WebAsyncTask` và là MVC async executor. `setDefaultTimeout(5000L)` áp dụng mặc định khi return type không override.
- `producerExecutor` minh họa **executor do ứng dụng quản lý**, dùng để hoàn tất `DeferredResult`. `DeferredResult` không nhất thiết chạy trên pool `mvcAsyncExecutor` — producer nằm ở đâu là quyết định của app.
- `corePoolSize`, `maxPoolSize`, `queueCapacity` đặt giới hạn có ý nghĩa. Khi pool/queue đều đầy, task có thể bị reject. **Không tạo pool không giới hạn hoặc queue vô hạn chỉ vì muốn requests "async".**
- `threadNamePrefix` giúp nhận diện thread trong logs và tests. Tên thread không phải API contract của production clients, chỉ là công cụ minh họa.
- Spring quản lý lifecycle của `ThreadPoolTaskExecutor` beans, bao gồm khởi tạo và shutdown.
- Gọi `mvcAsyncExecutor()` từ một `@Configuration` được proxy bởi Spring theo default `proxyBeanMethods=true` sẽ lấy cùng singleton bean; không phải tự tạo thread pool mới mỗi request.

**Quan trọng về capacity:** Khi pool có bounded queue, `maxPoolSize` thường chỉ được dùng sau khi queue đã đạt giới hạn (tùy executor behavior). Nếu job chạy lâu, queue có thể tăng request latency; cần đo **active threads, queued tasks, rejected tasks, request timeout, p95/p99 latency** thay vì chỉ tăng pool size.

### 7.3. Controller: sync, Callable, DeferredResult, WebAsyncTask

**`src/main/java/example/asyncmvc/AsyncDemoController.java`:**

```java
package example.asyncmvc;

import java.util.concurrent.Callable;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.WebAsyncTask;

@RestController
@RequestMapping("/api/async")
public class AsyncDemoController {

    public record Reply(String mode, String thread) {}

    private final ThreadPoolTaskExecutor producerExecutor;

    public AsyncDemoController(
            @Qualifier("producerExecutor")
            ThreadPoolTaskExecutor producerExecutor) {
        this.producerExecutor = producerExecutor;
    }

    @GetMapping("/sync")
    public ResponseEntity<Reply> synchronous() {
        return ResponseEntity.ok(
                new Reply("sync", Thread.currentThread().getName()));
    }

    @GetMapping("/callable")
    public Callable<ResponseEntity<Reply>> callable() {
        return () -> ResponseEntity.ok(
                new Reply("callable", Thread.currentThread().getName()));
    }

    @GetMapping("/deferred")
    public DeferredResult<ResponseEntity<Reply>> deferred() {
        DeferredResult<ResponseEntity<Reply>> result =
                new DeferredResult<>(4000L);

        result.onTimeout(() -> result.setErrorResult(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new Reply("timeout", "none"))));

        result.onError(error -> result.setErrorResult(
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new Reply("error", "none"))));

        try {
            producerExecutor.execute(() -> {
                Reply reply = new Reply(
                        "deferred", Thread.currentThread().getName());
                result.setResult(ResponseEntity.ok(reply));
            });
        } catch (RuntimeException rejected) {
            // Demo: khi pool không nhận công việc, trả lỗi có kiểm soát.
            result.setErrorResult(
                    ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(new Reply("overloaded", "none")));
        }

        return result;
    }

    @GetMapping("/web-task")
    public WebAsyncTask<ResponseEntity<Reply>> webTask() {
        WebAsyncTask<ResponseEntity<Reply>> task =
                new WebAsyncTask<>(3000L, () ->
                        ResponseEntity.ok(new Reply(
                                "web-task",
                                Thread.currentThread().getName())));

        task.onTimeout(() ->
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new Reply("timeout", "none")));

        return task;
    }
}
```

**Tại sao code này hợp lý để học Servlet async?**

1. `synchronous()` thực thi trên Servlet request thread; return body cùng dispatch.
2. `callable()` trả closure; Spring bắt đầu async và thực thi closure trên pool `mvc-work-*`; response được hoàn tất trong một ASYNC dispatch.
3. `deferred()` trả **một promise do ứng dụng điều khiển**. Work được gửi tới `producerExecutor`; khi hoàn tất gọi `setResult` để MVC redispatch. Nếu producer này thay bằng callback thật của non-blocking I/O, có thể không cần thread đứng chờ cho mỗi HTTP call.
4. `webTask()` minh họa per-request timeout 3s với Callable. `WebAsyncTask` phù hợp khi endpoint cần callback timeout riêng.
5. `onTimeout` và `onError` trả response status cụ thể qua `setErrorResult`. Chúng không bảo đảm sửa được response nếu đã commit một phần hoặc socket client đã đóng.
6. `ThreadPoolTaskExecutor.execute(...)` có thể từ chối task khi overload; demo bắt RuntimeException khi submit để tránh pending forever. Trong hệ thống thật, nên bắt **đúng exception rejection policy** và tránh che lỗi lập trình khác; ví dụ có chủ ý tối giản.
7. Tên `producer-*` trong JSON cho biết **producer thread**, không phải Servlet thread ghi cuối cùng.
8. Không có `Thread.sleep`, network request hay database, nên ví dụ chỉ xác nhận lifecycle chứ không chứng minh hiệu năng.
9. Một `DeferredResult` đã timeout có thể từ chối result đến muộn; code production phải kiểm tra return value của `setResult` nếu cần cleanup/metrics.
10. Không gọi `request.startAsync()` trực tiếp: Spring MVC tự quản lý lifecycle khi return kiểu async.

**Lưu ý về timeout:** `DeferredResult(4000L)` và `WebAsyncTask(3000L)` dùng timeout theo milliseconds; trong Servlet async, timeout tính sau khi thread initial request thoát và request chuyển vào trạng thái chờ. Runtime thực có thể có proxy/ingress timeout ngắn hơn; không coi server-side timeout là một deadline tuyệt đối đến từng millisecond trên toàn mạng.

### 7.4. Chạy demo

Từ thư mục `servlet-async-demo` có `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

Đây là **hai lệnh chạy tuần tự**. Cài JDK 17+ và Maven. Với Boot default, ứng dụng chạy tại `http://localhost:8080`.

Các yêu cầu:

```bash
curl -i 'http://localhost:8080/api/async/sync'
curl -i 'http://localhost:8080/api/async/callable'
curl -i 'http://localhost:8080/api/async/deferred'
curl -i 'http://localhost:8080/api/async/web-task'
```

Ví dụ response bodies có dạng:

```json
{"mode":"sync","thread":"http-nio-8080-exec-1"}
```

```json
{"mode":"callable","thread":"mvc-work-1"}
```

```json
{"mode":"deferred","thread":"producer-1"}
```

```json
{"mode":"web-task","thread":"mvc-work-2"}
```

**Đây là kết quả minh họa**, không cam kết tên/số cuối thread. Số thread, thứ tự thread creation, và trường hợp pool reuse phụ thuộc runtime/độ đồng thời. Với các request thành công bình thường, status dự kiến là `200 OK`. Response body `thread` chỉ là bằng chứng công việc đã chạy ở loại pool khác, không phải bằng chứng toàn bộ socket/network operations non-blocking.

## 8. MockMvc: kiểm tra asyncStarted và asyncDispatch

Nếu test chỉ gọi `mvc.perform(get("/api/async/callable")).andExpect(status().isOk())` ngay ở lần dispatch đầu, kết quả có thể chưa sẵn sàng vì request đang async. MockMvc cung cấp:

- `request().asyncStarted()`: xác nhận controller đã chuyển sang asynchronous handling.
- `MvcResult` từ initial request: giữ request/response để tiếp tục.
- `asyncDispatch(mvcResult)`: mô phỏng subsequent Servlet ASYNC dispatch khi kết quả sẵn sàng.
- `jsonPath`/`status`: kiểm tra response **sau** dispatch.

**`src/test/java/example/asyncmvc/AsyncDemoControllerTest.java`:**

```java
package example.asyncmvc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AsyncDemoControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void synchronousResponseIsImmediate() throws Exception {
        mvc.perform(get("/api/async/sync"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.mode").value("sync"));
    }

    @Test
    void callableStartsAsyncAndRunsOnMvcExecutor()
            throws Exception {
        MvcResult pending = mvc.perform(get("/api/async/callable"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(pending))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.mode").value("callable"))
           .andExpect(jsonPath("$.thread").value(
                   startsWith("mvc-work-")));
    }

    @Test
    void deferredResultUsesProducerExecutor()
            throws Exception {
        MvcResult pending = mvc.perform(get("/api/async/deferred"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(pending))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.mode").value("deferred"))
           .andExpect(jsonPath("$.thread").value(
                   startsWith("producer-")));
    }

    @Test
    void webAsyncTaskUsesMvcExecutor()
            throws Exception {
        MvcResult pending = mvc.perform(get("/api/async/web-task"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(pending))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.mode").value("web-task"))
           .andExpect(jsonPath("$.thread").value(
                   startsWith("mvc-work-")));
    }
}
```

Chạy:

```bash
mvn test
```

Bộ test có **4 test methods**. Nếu pass, Maven kết thúc exit code `0` và thường in summary `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`. Ví dụ này kiểm tra return-value lifecycle bằng MockMvc; không phải một phép đo độ trễ hoặc I/O readiness của real socket. Không khẳng định đã chạy Maven trong quá trình tạo tài liệu này.

### Vì sao không assert thread đầu tiên == thread sau redispatch?

Spring/container có thể dùng các thread khác nhau cho initial request, task executor và redispatch. Thread names từ response ở demo ghi nhận lúc business reply được tạo, **không chứng minh** thread ghi HTTP response. Vì vậy test chỉ kiểm tra prefix phù hợp với producer/Callable executor, không bắt các thread IDs phải bằng nhau.

### Test timeout như thế nào?

Để test timeout callback thật, MockMvc đơn thuần có thể không kích hoạt giống hệt container network timeouts. Có thể dùng `MockAsyncContext` hoặc integration test qua embedded Tomcat và một producer được điều khiển bởi `CountDownLatch`/fake service, nhưng không nên dùng `Thread.sleep(10000)` trong unit tests rồi cho rằng timeout chắc chắn ổn định.

Một test đáng tin nên chủ động điều khiển thời điểm producer hoàn thành và timeout, kiểm chứng status/error body theo chính sách ứng dụng. Cần kiểm tra chuyện công việc tiếp tục chạy sau HTTP timeout và có được cancel hay không. `DeferredResult.onTimeout` không tự gọi `Future.cancel(true)` trên task bạn submit.

## 9. Timeout, lỗi và client disconnect

Async request để response pending nên có **timeout policy**, cả ở MVC lẫn proxy/network. Nếu không, kết nối mở quá lâu có thể tích tụ state và sockets.

### 9.1. Timeout không đồng nghĩa cancellation

Khi request timeout:

1. Container có thể báo async timeout.
2. Spring MVC/interceptor/`DeferredResult.onTimeout` có cơ hội tạo error result.
3. MVC cố redispatch để ghi response lỗi, nếu HTTP response chưa commit và client còn kết nối.
4. **Blocking service/worker task đang chạy không nhất thiết bị dừng**. Bạn phải có deadline và cancellation policy ở producer/driver.

Nếu chạy `Callable` gọi sync REST client timeout 60 giây nhưng MVC timeout 3 giây, request phía client có thể nhận 503 sau 3 giây trong khi worker vẫn chạy/đợi tới timeout 60 giây. Khi đủ nhiều tasks, pool vẫn cạn dù container threads rảnh.

**Quy tắc thực hành:** request deadline phải được truyền tới downstream I/O: connection/read timeout, database statement timeout, client cancellation, messaging subscription cleanup, queue cancellation. Không chỉ đặt `configureAsyncSupport.setDefaultTimeout(...)` rồi coi việc quản lý tải đã xong.

### 9.2. Exception propagation

- Với `Callable` ném exception, Spring MVC có thể xử lý kết quả lỗi qua exception resolvers/`@ControllerAdvice` trong redispatch.
- Với `DeferredResult`, dùng `setErrorResult(exception)` hoặc một error result phù hợp; Spring MVC redispatch và xử lý theo return type/exception.
- Nếu response đã ghi một phần (streaming) hoặc client ngắt kết nối, không thể chắc chắn thay HTTP status/body nữa. Log/metrics phải phân biệt lỗi nghiệp vụ với broken pipe/timeout/client abort.
- Không expose stack trace, database error hay credentials trong error response. Dùng stable error codes và request correlation ID.

### 9.3. Error callbacks và cleanup

`DeferredResult` có:
- `onTimeout(...)` để trả fallback/error.
- `onError(...)` để phản ứng exception.
- `onCompletion(...)` để dọn subscription, pending listener registration, slot trong long-polling map, timer/correlation context.

Cần tránh race giữa producer completion và timeout. `setResult(...)` trả boolean cho biết kết quả có được set hay không; chỉ callback đầu tiên thành công được dùng. Dùng `AtomicBoolean`/state machine khi cleanup nhiều nguồn có thể chạy đồng thời.

Một `DeferredResult` lưu trong `ConcurrentHashMap` để đợi sự kiện cần bị xóa khi complete/timeout, tránh memory leak nếu client bỏ đi hoặc producer không bao giờ có event.

## 10. Tại sao @Async không đồng nghĩa HTTP Servlet async?

Spring `@Async` thường đánh dấu method bean chạy ở task executor khác, và trả `Future`/`CompletableFuture` hoặc `void`. Nó là **method invocation async**, không phải bản thân Servlet request lifecycle:

```java
@Async
public CompletableFuture<String> loadReport() {
    return CompletableFuture.completedFuture(
            blockingRepository.loadReport());
}
```

Đây là snippet có `blockingRepository` giả định; dù trả CompletableFuture, gọi `blockingRepository.loadReport()` vẫn block thread mà method `@Async` chạy trên đó. Muốn controller release initial request thread, return type MVC phải được xử lý như async return value, hoặc controller chọn cơ chế `DeferredResult`/`Callable`. Một method tự gọi trên cùng bean (**self-invocation**) có thể bypass `@Async` proxy, nên không mặc định annotation sẽ đổi thread.

**Không nên** tạo mới một raw thread trên mỗi HTTP request:

```java
// PHẢN VÍ DỤ: không nên sinh unbounded threads theo request.
new Thread(() -> doBlockingWork()).start();
```

Với load lớn, việc không bound concurrency gây OOM, context switching và khó shutdown. Dùng executor bounded, queue policy, service-level timeouts và capacity planning.

## 11. StreamingResponseBody: stream không đồng nghĩa non-blocking

Ví dụ trả bytes qua `StreamingResponseBody`:

```java
@GetMapping("/download")
public StreamingResponseBody download() {
    return outputStream -> {
        try (InputStream input = fileService.openInputStream()) {
            input.transferTo(outputStream);
        }
    };
}
```

Đây là **snippet**: `fileService` chưa khai báo, controller/response headers chưa hoàn chỉnh và cần import `StreamingResponseBody`, `InputStream`. Nó cho phép ghi response **từ task executor sau khi initial Servlet thread được trả lại**. Nhưng `InputStream.transferTo(OutputStream)` dùng vòng đọc/ghi thường **blocking**; nếu client chậm, worker có thể đứng chờ output. Spring MVC khuyến nghị cấu hình async executor phù hợp khi dùng streaming.

### ResponseBodyEmitter và SSE

`ResponseBodyEmitter` có thể gửi nhiều object với `send(...)`/`complete()`. `SseEmitter` hỗ trợ SSE events. Mỗi request có thể tồn tại lâu mà không giữ initial Servlet thread, nhưng:

- Mỗi client vẫn chiếm connection, memory/state và có thể sử dụng worker khi gửi.
- Có thể xảy ra blocking writes hoặc queue tích tụ nếu client chậm.
- SSE thường đi một chiều server → client; muốn hai chiều, cân nhắc WebSocket.
- Đặt timeouts, heartbeat, limits, cleanup và client reconnect policy.
- Nếu đăng ký subscription từ broker, hủy khi emitter complete/error/timeout.

**Backpressure:** Servlet MVC emitters không tự tạo một giải pháp backpressure end-to-end giữa broker, application queue và browser. Nếu producer nhanh hơn consumer, cần buffer bounds, drop policy, producer pacing hoặc protocol phù hợp; đừng cứ gửi vô hạn vì initial thread đã rời request.

## 12. ThreadLocal, SecurityContext, MDC và transaction trong async

Một trong những lỗi khó phát hiện là suy ra "vẫn cùng HTTP request" nên mọi context của thread ban đầu vẫn tồn tại ở worker.

### 12.1. MDC/trace

MDC của logging thường dùng `ThreadLocal`. `Callable` chạy trên `mvc-work-*`, không nhất thiết mang `traceId`/`tenantId` mà Servlet filter gắn cho initial container thread. Nếu không có context propagation, logs từ producer mất correlation hoặc ghi nhầm giá trị tồn dư của task trước.

Một `TaskDecorator` có thể capture/apply MDC khi submit task, nhưng phải **restore/clear** trong finally. Khi kết hợp tracing/OpenTelemetry, dùng integration/Context propagation chính thức thay vì tự copy bừa mọi ThreadLocal.

### 12.2. SecurityContext

Spring Security cung cấp cơ chế integration cho Servlet async trong một số đường `Callable`, nhưng **không được giả định mọi executor/CompletableFuture/DeferredResult callback tự kế thừa principal**. Với producer executor tùy biến, cân nhắc `DelegatingSecurityContextAsyncTaskExecutor` hoặc explicit immutable user context; truyền ít dữ liệu cần thiết.

Đừng lưu nguyên request hoặc mutable security context trong long-lived queue rồi xử lý sau khi session đã hết hạn mà không xác minh policy. Phân quyền phải đặt tại endpoint và downstream service phù hợp.

### 12.3. Transaction

`@Transactional` thường gắn transaction với thread đang chạy method. Transaction trên controller initial thread **không tự tiếp tục** trong callable worker, deferred producer hoặc asynchronous dispatch. Nếu cần một DB transaction cho work, mở transaction **bên trong service method** được gọi trên worker. Không cố kéo dài một JDBC transaction suốt thời gian chờ event vài phút.

### 12.4. Locale và request-scoped data

`LocaleContextHolder`/`RequestContextHolder` có lifecycle riêng; Spring có một số integration cho Callable nhưng không bảo đảm mọi callback của riêng app nhận request context. Khi async producer cần locale, timezone hoặc tenant, nên truyền các giá trị **explicit** (bất biến) vào job/callback. Đừng giả định request-scoped bean còn hợp lệ sau khi request đã complete.

## 13. Throughput, thread pool và hiệu năng thực sự

### 13.1. Giải phóng Servlet thread có thể giúp điều gì?

Nếu 200 request đồng thời đang chờ remote events, `DeferredResult` cho phép initial Servlet threads trở về pool sớm, để container nhận các requests khác. Nhưng đồng thời vẫn có:

- 200 pending HTTP responses/connections.
- 200 async state objects.
- Timeout registrations, callbacks và possible buffered data.
- Event producer/subscriptions hoặc background tasks.
- Một Servlet thread ngắn khi result ready để redispatch và serialize response.

Nếu dùng `Callable` với blocking work, 200 requests có thể tràn một worker pool 8 threads + queue 32; 160 requests còn lại có thể bị reject/delay. Đó là **backpressure/overload cần thiết**, không phải lỗi phải “sửa” bằng cách spawn 200 unbounded threads.

### 13.2. Little's Law và giới hạn chờ

Một trực giác hiệu năng: số request đang chờ xấp xỉ `arrival rate × average time in system` khi đo trên một hệ thống tương đối ổn định. Nếu 100 request/giây mất 2 giây, có thể có khoảng 200 requests in flight; async không làm mất 200 pending connections, nó chỉ thay cách giữ threads.

Khi vẽ capacity plan, đo:
- Active/queued/rejected tasks ở executor.
- Current async requests, completion/timeout/cancel.
- Container threads busy và idle.
- HTTP client connection pool limits, DB pool saturation.
- CPU/GC, heap bytes cho response buffers và upload/download state.
- p50/p95/p99 latency và error rates.
- Client disconnects và ingress/reverse proxy timeouts.

Không nói "async luôn nhanh hơn sync". Với tác vụ CPU-bound rất ngắn, chuyển executor + redispatch có overhead và có thể chậm hơn. Với blocking operations, giải phóng Servlet threads có lợi cho isolation/availability nhưng không làm tổng wait-time thấp đi nếu downstream vẫn chậm.

### 13.3. Request deadline và circuit breaker

Khi gọi downstream chậm:
1. Đặt connection/read/request timeout của HTTP client/DB.
2. Giới hạn concurrent calls và queue.
3. Dùng cancellation/deadline propagation khi supported.
4. Fallback/circuit breaker/rate limit theo yêu cầu nghiệp vụ.
5. Không retry vô hạn trong worker; retry storms có thể làm downstream càng nghẽn.
6. Trả HTTP status đúng (`503` khi tạm quá tải, `504` ở gateway nếu phù hợp) với correlation ID và log an toàn.

Timeout chỉ ở Servlet MVC mà không đặt timeout cho client/DB là **thiết kế không đầy đủ**.

## 14. Spring MVC async so với Spring WebFlux

| Khía cạnh | Spring MVC Servlet async | Spring WebFlux |
| --- | --- | --- |
| Abstraction chính | MVC controllers, Servlet request/response | Reactive streams/publishers và reactive HTTP processing |
| Giải phóng initial thread | Có với Callable/DeferredResult/etc. | Reactive event-driven request lifecycle |
| Return types | Callable, DeferredResult, WebAsyncTask, CompletionStage, emitters | Mono, Flux và reactive types |
| I/O server | Servlet container, có Servlet 3.1 non-blocking APIs nhưng MVC response write thường blocking | Designed cho non-blocking streaming I/O qua supported runtime/adapters |
| JDBC/JPA blocking | Vẫn blocking nếu dùng | Vẫn blocking nếu gọi từ event loop; phải offload hoặc đổi driver |
| Backpressure | Cần thiết kế riêng cho emitters/producer queues | Reactive Streams hỗ trợ demand/signals, nhưng phải xem từng boundary |
| Testing | MockMvc + asyncDispatch | WebTestClient + reactive test utilities |
| Migration | Thêm async trên MVC hiện có | Thay programming model/stack và có thể client/driver |
| Phù hợp | Tận dụng Spring MVC, blocking libraries, traffic cần tách Servlet threads | End-to-end async pipelines, streaming/large concurrency với non-blocking dependencies |

Không có một lựa chọn thắng mọi trường hợp. Với MVC đang dùng Hibernate ORM/JPA/JDBC blocking, `Callable` hoặc `DeferredResult` + bounded worker có thể là giải pháp incremental. Nếu bottleneck chính là số lượng kết nối đồng thời và toàn bộ data path có non-blocking network/DB clients, WebFlux có thể giảm thread consumption. Nhưng nếu đưa `block()`/JDBC blocking vào event loop, lợi ích sẽ mất và throughput có thể tệ.

**WebFlux không đồng nghĩa với Netty trong mọi deployment**; nó có thể chạy trên runtime adapters khác. Phân biệt **programming model** (reactive), **server runtime** (Servlet/Netty) và **các thư viện I/O thực tế**.

## 15. Các lỗi phổ biến khi áp dụng Servlet async

| Triệu chứng | Nguyên nhân khả dĩ | Cách xử lý |
| --- | --- | --- |
| `IllegalStateException` báo async unsupported | Servlet/filter chain không `asyncSupported` hoặc dispatcher mapping thiếu | Kiểm tra đăng ký Servlet/filters và ASYNC dispatch |
| Dùng Callable mà threads vẫn cạn | Blocking work chuyển từ container sang worker pool | Bound executor, downstream timeout, đo worker utilization |
| DeferredResult bị timeout dù producer xong | Race, queue quá dài, result set sau timeout | Kiểm tra `setResult` boolean, queue latency và deadline |
| HTTP response kết thúc nhưng task vẫn chạy | MVC timeout không tự cancel downstream work | Truyền cancellation/deadline, cleanup producer/connection |
| `AsyncTaskExecutor` default không chịu tải | Không cấu hình pool/queue phù hợp | `WebMvcConfigurer.configureAsyncSupport` với bounded executor |
| Request không async dù có `@Async` method | `@Async` chỉ đổi nơi gọi method, controller return value không async | Trả Callable/DeferredResult/CompletionStage phù hợp |
| MDC/traceId biến mất ở worker | ThreadLocal không tự propagate | TaskDecorator/Context propagation, clear finally |
| SecurityContext không có trong producer | Tự dùng executor bên ngoài security integration | Explicit context hoặc delegating executor, xác minh ACL |
| Lazy-loading/transaction exception | Transaction chỉ mở ở initial thread | Service transaction bên trong worker, DTO boundaries |
| `StreamingResponseBody` chậm với slow client | `OutputStream.write` blocking trong worker | Pool thích hợp, flow control, native nonblocking APIs nếu cần |
| SSE làm memory tăng dần | Emitter/subscription không cleanup, client chậm | Bounds, callbacks, heartbeat, cancellation |
| MockMvc test fail vì response chưa có | Chưa gọi `asyncDispatch` | `request().asyncStarted()` rồi `asyncDispatch(result)` |
| Controller được gọi "hai lần" trong logs | Confuse ASYNC redispatch/filters/interceptors với controller return value | Log DispatcherType, lifecycle; không duplicate business action |
| Timeout ở proxy trước Spring MVC | Ingress idle timeout thấp hơn MVC timeout | Align end-to-end timeouts, keepalive theo policy |
| CPU tăng sau khi đổi async | Executor dispatch overhead, oversubscription, retry storms | Benchmark tải thật, tune bounded concurrency |
| Non-blocking WriteListener luôn quay vòng | Không thoát khi `isReady()==false` | Chỉ write lúc ready; chờ callback kế tiếp |
| Cố đọc file/DB đồng bộ trên event loop | Blocking I/O vẫn block thread phản ứng | Dùng non-blocking driver hoặc offload bounded scheduler |

Checklist debug cụ thể:

1. Quan sát `DispatcherType.REQUEST` và `DispatcherType.ASYNC` cùng correlation ID.
2. Kiểm tra Controller return type; đang dùng Callable/DeferredResult hay sync DTO?
3. Xem cấu hình `asyncSupported`/ASYNC dispatcher mappings cho Servlet và filters.
4. Đo thread names của producer/worker và container, không chỉ đo total thread count.
5. Đo thời gian chờ downstream, MVC timeout, proxy timeout và queue wait riêng.
6. Nếu có `DeferredResult`, kiểm tra hoàn thành bằng `setResult` hay error/timeout, cleanup listeners.
7. Với streaming, đo time spent trong `write` và tốc độ consumer.
8. Kiểm tra MDC/SecurityContext/LocaleContext/transaction boundaries trên từng executor.
9. Dùng MockMvc `asyncDispatch`, rồi bổ sung live HTTP server test khi cần verify disconnect/network/proxy.
10. Nếu mục tiêu là non-blocking end-to-end, kiểm tra **từng API I/O** (HTTP, DB, filesystem, response write) chứ không chỉ kiểu trả về.

## 16. Chọn kỹ thuật theo loại công việc

| Tình huống | Hướng phù hợp |
| --- | --- |
| Controller gọi DB JDBC blocking lâu nhưng muốn bảo vệ Servlet thread pool | Callable/WebAsyncTask + bounded executor, DB timeout |
| Phải chờ event/message broker callback mà không có thread riêng đứng đợi | DeferredResult + event subscription/callback và cleanup |
| HTTP client trả CompletableFuture từ I/O async thật sự | Controller return CompletableFuture/CompletionStage hoặc adapt qua DeferredResult |
| Tạo báo cáo CPU-bound 20 ms | Thường sync hoặc xử lý worker phù hợp theo benchmark; async không tự nhanh hơn |
| Tạo báo cáo lâu nhiều phút | Job queue + `202 Accepted` + polling/webhook, không giữ HTTP connection vô hạn |
| Trả tệp lớn qua StreamingResponseBody | MVC async executor và kiểm soát blocking writes |
| SSE hàng nghìn clients cập nhật liên tục | Cân nhắc WebFlux/non-blocking pipelines, bounds và heartbeat |
| Cần non-blocking Servlet stream ở mức thấp | Servlet 3.1 `ReadListener`/`WriteListener` với readiness contract |
| End-to-end network + data-store reactive | WebFlux và non-blocking clients/drivers, tránh blocking trên event loop |
| Cần hỗ trợ existing Servlet filters, JSP, blocking Spring MVC ecosystem | Tiếp tục MVC, chỉ dùng async tại nơi thực sự có lợi |

Một endpoint thời gian chờ quá dài có thể nên **không** giữ HTTP connection. Với tác vụ báo cáo mất nhiều phút, API `POST /reports` trả `202 Accepted` cùng job ID, client `GET /reports/{id}/status` hoặc nhận webhook là một lựa chọn giúp xử lý retries, scaling và timeouts ổn định hơn. Servlet async phù hợp các request chờ không quá lâu và có policy rõ, không thay message queue/job orchestration.

## 17. Bài tập tự kiểm tra

1. Vì sao `Callable` có thể giải phóng Servlet request thread nhưng vẫn **block** một worker thread?
2. `DeferredResult` có tự sinh thread và tự gọi I/O không?
3. Trong MVC async lifecycle, request hoàn thành ở initial `REQUEST` dispatch hay có một `ASYNC` redispatch?
4. `request.startAsync()` (Servlet 3.0) và `ServletOutputStream.setWriteListener()` (Servlet 3.1) khác trách nhiệm nào?
5. Đặt `MVC timeout=3s` có tự dừng một JDBC query 60s không?
6. `StreamingResponseBody` có bảo đảm socket write không bao giờ block thread không?
7. Vì sao Spring MVC cần cấu hình `AsyncTaskExecutor` thay vì unbounded threads cho Callable?
8. Nếu response tới user đã timeout, `DeferredResult.setResult(value)` gọi muộn có thể xảy ra chuyện gì?
9. Test MockMvc cho Callable phải có những bước nào?
10. `@Async` annotation trên service có tự khiến HTTP response non-blocking không?
11. Vì sao `ThreadLocal` transaction/MDC/SecurityContext không thể mặc định được giữ qua worker executor?
12. Nếu đổi sang WebFlux mà vẫn gọi JDBC blocking trực tiếp trên event loop, có đạt end-to-end non-blocking không?
13. Khi nào nên dùng `DeferredResult` thay Callable?
14. Async có làm mất chi phí socket/buffer của mỗi pending HTTP response không?
15. Với SSE client chậm, vì sao vẫn phải cần backpressure/buffer limits dù request thread đã được giải phóng?

**Gợi ý:** (1) Callable chạy trên executor, blocking call chặn executor thread. (2) Không; producer/event thread do ứng dụng quyết định. (3) MVC tiếp tục bằng ASYNC dispatch. (4) Lifecycle giải phóng/tiếp tục request so với I/O readiness. (5) Không; cần cancellation/downstream timeout. (6) Không; OutputStream.write có thể block. (7) Bảo vệ concurrency/queue/memory. (8) Result có thể bị từ chối vì đã complete. (9) Request asyncStarted → MvcResult → asyncDispatch → assert. (10) Không; method execution và MVC return-type handling là hai lớp khác. (11) Thread-bound context. (12) Không. (13) Khi kết quả đến từ callback/event không cần worker đứng chờ. (14) Không. (15) Producer có thể nhanh hơn tốc độ network/consumer.

## 18. Tài liệu tham khảo chính thức

- [Spring Framework — Asynchronous Requests in Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)
- [Spring Framework 6.1 — AsyncSupportConfigurer Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/servlet/config/annotation/AsyncSupportConfigurer.html)
- [Spring Framework 6.1 — DeferredResult Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/context/request/async/DeferredResult.html)
- [Spring Framework 6.1 — WebAsyncTask Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/context/request/async/WebAsyncTask.html)
- [Spring Framework — MVC Streaming](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html#webmvc-ann-async-http-streaming)
- [Spring Framework — MVC Compared to WebFlux](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html#webmvc-ann-async-vs-webflux)
- [Spring Framework — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Jakarta Servlet 6.0 — AsyncContext](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/asynccontext)
- [Jakarta Servlet 6.0 — ReadListener](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/readlistener)
- [Jakarta Servlet 6.0 — WriteListener](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/writelistener)
- [Spring MVC Testing — Asynchronous Requests](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-framework/async-requests.html)

**Tổng kết:** Servlet async giải phóng **initial Servlet request thread** khi HTTP response còn pending và dùng **ASYNC dispatch** để hoàn tất sau. `Callable` thường chuyển blocking work sang executor; `DeferredResult` có thể chờ một external callback mà không cần thread đứng đợi riêng; `WebAsyncTask` thêm timeout/callbacks. Đây không phải bằng chứng I/O non-blocking. **Servlet 3.1 ReadListener/WriteListener** giải quyết socket readiness ở tầng thấp, còn WebFlux cung cấp mô hình reactive hơn khi các dependencies hỗ trợ non-blocking end-to-end. Khi triển khai, hãy đo thread/queue/downstream timeout, đảm bảo security/trace context, cleanup và giới hạn tải — không chỉ đổi return type rồi coi ứng dụng đã "non-blocking".


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-async-processing-002">Callable, DeferredResult, WebAsyncTask và lựa chọn mô hình hoàn thành</a>

<details>
<summary>Xem chi tiết</summary>

# Callable, DeferredResult, WebAsyncTask và lựa chọn mô hình hoàn thành trong Spring MVC

## 1. Mục tiêu: trả HTTP response sau khi công việc hoàn tất

Ở Spring MVC đồng bộ, một Servlet container thread nhận request, chạy controller, chờ truy vấn DB/dịch vụ ngoài, tạo kết quả, rồi ghi response. Nếu việc tính kết quả phải chờ lâu, thread xử lý request có thể bị giữ trong lúc chờ.

Spring MVC có hỗ trợ **asynchronous Servlet request processing**. Controller có thể trả một **giá trị đại diện cho kết quả sẽ xuất hiện trong tương lai**. Spring bắt đầu async mode, giải phóng Servlet request thread ban đầu và để response mở. Khi kết quả sẵn sàng, MVC thực hiện **ASYNC dispatch** để tiếp tục xử lý kết quả, áp dụng handler return-value processing (JSON/view/error) và hoàn tất response.

Ba lựa chọn rất hay gặp:

- `Callable<T>` — controller giao **một tác vụ tính kết quả** để Spring MVC thực thi bằng `AsyncTaskExecutor`.
- `DeferredResult<T>` — controller giao **một điểm nhận kết quả**; ứng dụng hoặc event/callback ở bên ngoài sẽ tự gọi `setResult(...)` hoặc `setErrorResult(...)`.
- `WebAsyncTask<T>` — bọc `Callable<T>` và cho phép cấu hình **timeout, executor, onTimeout/onError/onCompletion** cho riêng request.

Đây là bài học về **cách chọn mô hình hoàn thành**, không chỉ ba kiểu Java được phép return. Cần hiểu ai sở hữu tác vụ, ai kết thúc response, timeout tính thế nào, quản lý tài nguyên chờ ra sao, và khi nào nên chọn reactive/streaming thay vì các kiểu một-kết-quả này.

### Kết quả học tập

Sau bài, bạn có thể:

1. Vẽ đúng luồng `REQUEST → startAsync → giải phóng container thread → ASYNC redispatch → response`.
2. Phân biệt `Callable`, `DeferredResult` và `WebAsyncTask` theo **producer, thread, completion trigger, timeout và cleanup**.
3. Cấu hình executor MVC bằng `WebMvcConfigurer#configureAsyncSupport`; hiểu lý do executor mặc định không nên mặc nhiên dùng dưới tải cao.
4. Xử lý `setErrorResult`, exceptions, `@ExceptionHandler`, timeout, lỗi network/client disconnect và bất đồng bộ kết thúc đua nhau.
5. Thiết kế endpoint không leak DeferredResult trong map, không giữ request thread bằng `Future#get()`, không dựa mù quáng vào thread-local.
6. Chạy ví dụ Spring Boot 3 + Java 17 và kiểm tra `request().asyncStarted()` cùng `asyncDispatch(...)` bằng MockMvc.

**Phạm vi:** Spring Framework 6.x/Spring Boot 3.x, Jakarta Servlet API và Spring MVC (Servlet stack). Spring WebFlux có mô hình reactive riêng. Trả `Callable`/`DeferredResult` không tự biến toàn bộ Spring MVC, JDBC hay HttpServletResponse thành non-blocking I/O.

## 2. Servlet async: giải phóng request thread nhưng chưa trả xong response

Synchronous request:

```text
Servlet thread T1
┌────────────────────────────────────────────────────┐
│ GET /report                                        │
│   → filters → DispatcherServlet → controller       │
│   → đọc DB/chờ remote service → tạo body           │
│   → Jackson/View render → HTTP 200 → thread rảnh   │
└────────────────────────────────────────────────────┘
         giữ Servlet thread trong toàn bộ thời gian chờ
```

Spring MVC async:

```text
T1: Servlet container thread                  T2: worker/event thread
──────────────────────────                    ─────────────────────────
GET /report
  → filters → DispatcherServlet
  → controller trả Callable hoặc DeferredResult
  → ServletRequest.startAsync()
  → T1 rời filter/Servlet chain
      response VẪN mở                       Callable chạy / event chờ...
                                            └── tạo kết quả
                                                hoặc setResult(...)
                           │
                           ▼
T3: Servlet container ASYNC redispatch
  → DispatcherServlet tiếp tục với kết quả đã có
  → ResponseEntity/JSON/View/error handler
  → HTTP 200/4xx/5xx
  → hoàn tất async request
```

Những sự thật quan trọng:

- Khi thread ban đầu rời request processing, **HTTP response chưa nhất thiết được gửi xong**. Browser vẫn đợi.
- Trong `ASYNC` redispatch, MVC có thể map cùng URL nhưng **không gọi lại controller method ban đầu như một request mới**; nó tiếp tục xử lý giá trị async đã lưu.
- `ASYNC` dispatch khác `forward:`, khác `redirect:` và khác việc người dùng mở một HTTP connection mới.
- Chuyển sang async **không có nghĩa công việc CPU/DB biến mất**. `Callable` chỉ chuyển blocking work khỏi container thread sang executor thread.
- DeferredResult có thể hiệu quả với producer thật sự callback/event-based: thread Servlet rảnh **và** không cần thêm thread ngồi đợi kết quả. Nhưng nếu tự `new Thread(...).sleep(...)` cho mỗi DeferredResult, bạn chỉ chuyển vấn đề sang chỗ khác.
- Response cuối cùng vẫn đi qua xử lý của Spring MVC, nên exceptions và message conversion còn hoạt động theo quy tắc của Framework.

### 2.1. Servlet prerequisites

Servlet async phải được enable cho `DispatcherServlet` và các Servlet filters có liên quan, với `asyncSupported=true`; filter nào cần tham gia ASYNC redispatch phải được map cho `DispatcherType.ASYNC`. Trong Spring Boot Servlet MVC cấu hình thông thường, Boot quản lý nhiều phần này. Với `web.xml`, filter registration tùy chỉnh hoặc triển khai WAR riêng, phải xác nhận bằng kiểm thử container thực tế.

Ví dụ mapping cho custom filter:

```java
registration.setAsyncSupported(true);
registration.setDispatcherTypes(
    DispatcherType.REQUEST, DispatcherType.ASYNC);
```

*Đây là đoạn minh họa của FilterRegistrationBean/Servlet registration, không phải file Java độc lập.*

`OncePerRequestFilter` có chính sách tham gia redispatch riêng qua `shouldNotFilterAsyncDispatch()`. Một Filter chỉ có flag `asyncSupported` chưa chắc được gọi ở ASYNC dispatch; mapping, filter behavior và container đều quan trọng.

## 3. Bảng quyết định nhanh: ai hoàn thành kết quả?

| Tiêu chí | `Callable<T>` | `DeferredResult<T>` | `WebAsyncTask<T>` |
| --- | --- | --- | --- |
| Ai thực thi? | Spring MVC submit callable vào AsyncTaskExecutor | Producer do ứng dụng quản lý: message/event/webhook/callback | Như Callable nhưng executor có thể tùy chỉnh |
| Kích hoạt completion | Callable return/throw | Ứng dụng gọi `setResult` / `setErrorResult` | Callable return/throw hoặc timeout/error callback |
| Có cần giữ pending handle? | Không, MVC quản lý Callable | Thường có, nếu callback ở bên ngoài cần tìm đúng handle | Không, MVC quản lý callable |
| Timeout riêng request | Không trực tiếp trên Java Callable | `new DeferredResult<>(timeoutMillis)` | `new WebAsyncTask<>(timeoutMillis, callable)` |
| Hook timeout | Qua MVC global CallableProcessingInterceptor | `onTimeout(Runnable)` | `onTimeout(Callable<T>)` |
| Hook error | Qua MVC global interceptor / MVC exception handling | `onError(Consumer<Throwable>)`, `setErrorResult` | `onError(Callable<T>)` |
| Hook completion | Qua MVC global interceptor | `onCompletion(Runnable)` | `onCompletion(Runnable)` |
| Executor riêng request | Cấu hình MVC global hoặc tự quản task bên ngoài | Không bắt buộc có executor để tự hoàn thành | Có thể chỉ định một AsyncTaskExecutor hoặc bean name |
| Điển hình | Blocking service cần chạy ngoài Servlet thread | Chờ kết quả bất ngờ/đến từ event bus | Blocking task có SLA/executor khác mặc định |
| Rủi ro chính | Executor pool/queue đầy, blocking vẫn còn | Pending map leak, timeout/callback race | Như Callable + hiểu sai callback/cancellation |

**Câu hỏi chọn mô hình tốt nhất:** “Kết quả sẽ xuất hiện **bằng cách nào**?” Nếu bạn đã có một callable phải thực thi, chọn Callable/WebAsyncTask. Nếu một sự kiện độc lập sẽ quyết định khi nào có kết quả, chọn DeferredResult.

## 4. Callable<T>: giao tác vụ để Spring thực thi

Controller có thể trả:

```java
@GetMapping("/api/report")
public Callable<ResponseEntity<String>> report() {
    return () -> {
        String result = slowBlockingService.buildReport();
        return ResponseEntity.ok(result);
    };
}
```

*Đây là đoạn minh họa trong @RestController có slowBlockingService được inject; ví dụ chạy độc lập ở mục 10.*

Luồng:

```text
Controller thread T1:
  tạo Callable → return → Spring startAsync → T1 rảnh

MVC AsyncTaskExecutor thread T2:
  call() chạy
    ├── success → result được lưu
    └── exception → exception được lưu

Container thread T3:
  ASYNC dispatch → Spring xử lý ResponseEntity, View hoặc exception
```

### 4.1. Callable không đồng nghĩa non-blocking service

Nếu `slowBlockingService.buildReport()` vẫn dùng JDBC blocking, thread T2 vẫn bị giữ trong lúc DB xử lý. Lợi ích là **không giữ một Servlet container thread** cho tác vụ, giúp thread container sẵn sàng tiếp nhận request mới. Nhưng tổng thread và tài nguyên DB vẫn phải đủ; nếu queue executor tăng vô hạn, latency và memory có thể tệ hơn synchronous request.

Không làm thế này trong controller:

```java
@GetMapping("/wrong")
public String wrong() throws Exception {
    Future<String> future = executor.submit(this::slowCall);
    return future.get(); // Servlet thread T1 vẫn chờ!
}
```

Code trên là minh họa **anti-pattern**, không compile độc lập nếu thiếu field/helper. Việc gọi `get()` trực tiếp **đánh mất** mục đích Servlet async. Hãy return Callable hoặc biểu diễn kết quả tương lai được MVC hỗ trợ.

### 4.2. Chọn Callable khi nào?

- Tác vụ có một điểm bắt đầu và kết thúc rõ, không phải chờ message/event bên ngoài.
- Muốn MVC tự submit và quản lý giá trị trả về/exception.
- Không cần timeout hoặc executor riêng từng endpoint, có thể dùng cấu hình global.
- Service đã blocking nhưng bạn có đủ thread/connection pool và muốn trả Servlet thread sớm.

Không chọn chỉ vì “controller phải nhanh”: đo throughput, queue, DB connections và tail latency trước/sau.

## 5. WebAsyncTask<T>: Callable với SLA và executor riêng

`WebAsyncTask<T>` chứa một `Callable<T>` giống mô hình trên, đồng thời mang cấu hình timeout và executor tùy chọn, cùng callback quản lý lifecycle.

```java
@GetMapping("/api/report-with-deadline")
public WebAsyncTask<ResponseEntity<String>> report() {
    WebAsyncTask<ResponseEntity<String>> task =
        new WebAsyncTask<>(5_000L,
            () -> ResponseEntity.ok("report-ready"));

    task.onTimeout(() ->
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                      .body("report-timeout"));

    task.onError(() ->
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                      .body("report-error"));

    task.onCompletion(() ->
        logger.debug("HTTP async processing finished"));

    return task;
}
```

*Đoạn minh họa của controller có logger; project runnable dùng tên endpoint khác và import đầy đủ.*

### 5.1. Các callback có cùng kiểu không?

Không:

- `onTimeout(Callable<T>)` nhận một callback **trả giá trị T** (hoặc exception) làm kết quả thay thế khi Servlet async timeout.
- `onError(Callable<T>)` nhận callback có thể tạo kết quả thay thế khi có async container error.
- `onCompletion(Runnable)` dùng cho **dọn tài nguyên/quan sát** khi async request hoàn thành, bao gồm timeout/error.

Callback timeout/error chạy từ container thread, nên **phải trả nhanh, không chặn**. Không gọi HTTP remote service rất chậm hoặc đợi `Future#get()` trong callback chỉ để tạo fallback.

### 5.2. Timeout không đồng nghĩa hủy business task

Khi client chờ quá timeout:

- Spring/container có thể tạo kết quả timeout/error rồi hoàn tất response.
- Callable đã được submit có thể **vẫn đang chạy**; timeout response **không tự đảm bảo** JDBC/HTTP downstream bị hủy ngay.
- Cần deadlines trong chính client DB/HTTP, cancellation design, interruption handling, idempotency và tracing phù hợp.
- Không nhầm “đã trả 503/504 cho browser” với “mọi công việc backend đều đã dừng”.

Timeout trong `WebAsyncTask<>(5_000L, callable)` tính theo request async lifecycle của Servlet/container, không phải một CPU quota riêng của callable.

### 5.3. Executor theo request

Có thể chọn một executor khác qua các constructors của `WebAsyncTask`, như overload nhận `AsyncTaskExecutor` hoặc bean name executor. Khi endpoint báo cáo cần pool chuyên biệt còn tác vụ ngắn dùng pool mặc định, việc tách có thể giúp bulkhead nhưng phải cấp giới hạn rõ. Không tự tạo `new ThreadPoolTaskExecutor()` **bên trong từng request**.

Nếu cả ứng dụng có một policy tương đối đồng đều, cấu hình `WebMvcConfigurer#configureAsyncSupport` là đơn giản hơn; chỉ dùng executor riêng khi thực sự có workload/priority khác.

## 6. DeferredResult<T>: chờ một producer tự đến, không cần giao Callable

`DeferredResult<T>` là **điểm kết thúc response trong tương lai**. Controller tạo nó, trả ngay, và giữ một reference để nơi khác (event bus, JMS consumer, callback, một request khác) có thể complete.

```java
@GetMapping("/api/jobs/{id}/wait")
public DeferredResult<ResponseEntity<String>> waitForJob(
        @PathVariable String id) {

    DeferredResult<ResponseEntity<String>> result =
        new DeferredResult<>(10_000L);

    pending.put(id, result);

    result.onTimeout(() ->
        result.setResult(ResponseEntity.status(504)
                                       .body("job-timeout")));

    result.onCompletion(() -> pending.remove(id, result));

    return result;
}
```

*Đây là đoạn minh họa trong controller có concurrent pending map; project đầy đủ bên dưới kiểm soát ID trùng và cleanup.*

Một producer khác:

```java
DeferredResult<ResponseEntity<String>> result = pending.get(jobId);
if (result != null) {
    boolean accepted =
        result.setResult(ResponseEntity.ok("job-completed"));
    // false: đã có kết quả, timeout hoặc không còn usable
}
```

Ưu điểm:

- Controller **không cần đợi trong Servlet thread**.
- Producer không nhất thiết là MVC executor: có thể là listener của message broker, callback một client async, event từ domain service.
- Mô hình rất hợp cho **long polling một kết quả**.

Nhưng phải tự quản ownership, correlation ID, timeout, cancellation/disconnect, queue size, cleanup và concurrency.

### 6.1. setResult/setErrorResult là một cuộc đua có thứ tự

`DeferredResult#setResult(value)` trả boolean:

- `true` nếu result mới được set thành công.
- `false` nếu đã có result hoặc request/result đã hết hiệu lực.

Một response chỉ được quyết định một lần: có thể xảy ra cạnh tranh giữa **event đến**, **timeout**, **async error** và **request bị hoàn tất**. Không giả định callback sẽ thắng timeout.

`setErrorResult(exception)` đưa lỗi về MVC exception handling pipeline, có thể được `@ExceptionHandler` hoặc `@ControllerAdvice` xử lý. Nếu truyền một đối tượng response thay vì Throwable qua error-result API, cần hiểu cách Framework diễn giải loại return value; đơn giản hơn khi timeout muốn HTTP 504 thì dùng `setResult(ResponseEntity.status(504).body(...))`.

### 6.2. Cleanup đúng thời điểm

Đối với pending map, điều cần bảo vệ:

- Khi timeout hoặc connection đóng: loại reference khỏi map.
- Khi complete: loại reference khi request thực sự hoàn tất.
- Khi cùng ID có hai waiter: không im lặng ghi đè waiter đầu.
- Khi event tới sau timeout: không dùng object stale; phản ánh `setResult` thất bại.
- Khi producer không gửi gì mãi: timeout/TTL, kiểm soát pending count và backpressure.
- Khi ứng dụng dừng: đóng/wake pending requests bằng policy rõ.

`onCompletion(...)` được gọi khi Servlet async request hoàn thành vì bất kỳ lý do gì, có giá trị để cleanup. `onTimeout(...)` và `onError(...)` là các điểm làm fallback/result, không thay thế toàn bộ cleanup.

### 6.3. Không dùng DeferredResult để giữ thread chờ

Sai:

```java
@GetMapping("/wrong-deferred")
public DeferredResult<String> wrong() {
    DeferredResult<String> dr = new DeferredResult<>();
    executor.execute(() -> {
        try {
            Thread.sleep(10_000); // giữ một thread chỉ để chờ
            dr.setResult("done");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dr.setErrorResult(e);
        }
    });
    return dr;
}
```

Đây là đoạn minh họa anti-pattern. Nếu công việc đã là một `Callable` blocking, để MVC quản lý thông qua `Callable/WebAsyncTask` thường dễ hơn. Nếu API đã callback-based và không cần thread ngủ chờ, `DeferredResult` mới thể hiện lợi ích đúng.

## 7. Ai sở hữu completion? Ma trận theo kiến trúc

| Nguồn kết quả | Chọn mặc định | Vì sao |
| --- | --- | --- |
| Service blocking tính toán rồi return một giá trị | `Callable<T>` | MVC submit task và quản lý kết quả |
| Service blocking với timeout/SLA riêng | `WebAsyncTask<T>` | Callable + timeout, callbacks, executor riêng |
| JMS/Kafka/message consumer nhận event hoàn thành | `DeferredResult<T>` | Sự kiện tự đến, không cần thread chờ |
| Webhook bên ngoài đánh dấu job xong | `DeferredResult<T>` | Callback complete đang pending |
| Already have `CompletableFuture<T>`/`CompletionStage<T>` | Trả async type được Spring MVC hỗ trợ hoặc adapt phù hợp | Không cần tạo thread chờ/Blocking get |
| Long polling một response sau một event | `DeferredResult<T>` | Một kết quả, timeout và cleanup rõ |
| SSE stream nhiều sự kiện | `SseEmitter` hoặc reactive stream adaptation | Không phù hợp kiểu một giá trị đơn |
| Video/files streaming raw bytes | `StreamingResponseBody` | Cần stream/chunk và quản lý writing thread |
| Luồng non-blocking end-to-end và backpressure | Cân nhắc WebFlux/reactive clients | MVC async không làm JDBC/Servlet writes non-blocking |

**Không quyết định chỉ theo tên class.** Quan trọng là producer thật sự, số lượng responses, khả năng block, data ownership, số concurrent requests, deadlines và expected client behavior.

## 8. Cơ chế timeout, errors và completion

### 8.1. Timeout đến từ đâu?

Các nguồn có thể gồm:

1. Servlet container async timeout (có default khác nhau theo container).
2. MVC global async timeout từ `AsyncSupportConfigurer#setDefaultTimeout(...)`.
3. Per-request timeout ở `DeferredResult<>(milliseconds)` hoặc `WebAsyncTask<>(milliseconds, callable)`.
4. Timeout của DB client, HTTP client, messaging layer.
5. Reverse proxy/API gateway/client timeout — có thể đóng connection **trước** server.
6. Thread pool saturation khiến Callable chưa chạy khi request hết hạn.

Vì vậy “đặt 5 giây cho WebAsyncTask” chưa đủ nếu downstream HTTP client có read timeout 60 giây. Nên có **deadline budget** xuyên các layer: service/read timeout nhỏ hơn request budget và có fallback/cleanup phù hợp.

### 8.2. Giá trị mặc định khi timeout

Nếu bạn không xử lý timeout và response chưa commit, các Spring MVC async timeout interceptors thông thường có thể dẫn tới `AsyncRequestTimeoutException` và HTTP **503 Service Unavailable**. Tuy nhiên response/status cụ thể phụ thuộc framework version, exception handling, container và custom interceptors; không dựa vào điều này thay cho policy explicit.

Trong demo sẽ cho:

- WebAsyncTask timeout → `503` body `webtask-timeout`.
- DeferredResult timeout → `504` body `deferred-timeout`.

Đó là **hợp đồng do ví dụ chủ động lựa chọn**; 503/504 không phải định nghĩa duy nhất của hai loại async.

### 8.3. Exceptions đi qua @ExceptionHandler

Nếu Callable throws, Spring MVC lưu concurrent result dạng exception rồi redispatch. `@ExceptionHandler` hoặc `@RestControllerAdvice` có thể xử lý như lỗi synchronous controller phù hợp.

Với DeferredResult:

```java
result.setErrorResult(
    new IllegalStateException("remote event failed"));
```

Framework có thể đi qua cơ chế exception resolution thông thường. Không trả chuỗi lỗi kỹ thuật nhạy cảm ra client; API error schema nên ổn định và không expose nội dung exception từ hạ tầng.

### 8.4. Completion là kết thúc HTTP, không phải bảo đảm công việc đã hủy

`onCompletion` dùng cho release pending references, dọn context và metrics. Không giả định mọi database operation đều dừng khi HTTP connection ngắt hoặc timeout xảy ra. Nếu side effect nghiệp vụ tiếp tục sau timeout, hãy thiết kế idempotency key, trạng thái job, retry semantics và duplicate suppression.

### 8.5. Callback phải ngắn

Các callbacks timeout/error thường chạy trên Servlet container thread. Thực thi một thao tác chậm trong callback có thể gây nghẽn container pool, trái với mục tiêu async. Callback thường chỉ nên **đặt fallback result, ghi metric nhẹ, đánh dấu hủy, giải phóng reference** rồi thoát.

## 9. Executor, backpressure và context propagation

### 9.1. Cấu hình MVC AsyncTaskExecutor

Spring Framework MVC cho phép:

```java
@Configuration
class MvcAsyncConfig implements WebMvcConfigurer {
    private final AsyncTaskExecutor executor;

    MvcAsyncConfig(
            @Qualifier("mvcExecutor") AsyncTaskExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(executor);
        configurer.setDefaultTimeout(10_000L);
    }
}
```

*Đoạn minh họa class configuration; project mục 10 có file và imports đầy đủ.*

Executor mặc định có thể dùng được cho dev nhưng tài liệu Spring cảnh báo **không thích hợp cho production dưới load cao nếu không cấu hình**. Cần đặt các mức core/max pool size, queue capacity và rejection behavior, gắn metric queue length/active threads.

### 9.2. Một executor pool không làm nguồn lực vô hạn

Nếu pool có 4 worker threads và mỗi Callable blocking 10 giây, một lúc chỉ có khoảng 4 tasks chạy blocking trong pool đó. Request khác có thể xếp hàng rồi timeout trước khi có worker.

Vì vậy thiết kế:

- Bound queue và concurrent requests, không để pending map unbounded.
- Đặt timeout/deadline rõ ở app và downstream.
- Chính sách saturation: queue, reject, circuit breaker, retry có backoff.
- DB connection pool và HTTP client pool phải tương ứng.
- Dùng bulkheads để tác vụ chậm không chiếm mọi worker.
- Load test p95/p99, chứ không dựa vào output `asyncStarted=true` để khẳng định throughput tăng.

### 9.3. ThreadLocal, MDC và Spring Security

`LocaleContextHolder`, `RequestContextHolder`, logging MDC, transaction context và một số SecurityContext storage dựa trên thread-local. Khi MVC chuyển từ thread ban đầu sang executor thread, **không giả định mọi context tự di chuyển**.

- Truyền ID/locale/user data cần thiết **một cách tường minh** vào callable/event callback là cách dễ kiểm soát.
- Với logging MDC, sử dụng task decorator/context propagation cho executor.
- Với Spring Security, dùng các integration/context propagation phù hợp (như security context delegation) thay vì tự sao chép mutable SecurityContext sang thread dùng chung mà quên cleanup.
- Transaction của Servlet thread không tự kéo dài sang Callable thread; đặt transaction boundary trong service cần thực thi.
- Với DeferredResult producer chạy ở message listener, request thread context thường **không tồn tại**, nên cần correlation context rõ.

Đừng giữ `HttpServletRequest` hoặc `HttpServletResponse` lâu sau khi HTTP async request hoàn thành; điều đó gây stale references/leaks.

### 9.4. AsyncHandlerInterceptor khác HandlerInterceptor truyền thống

Trong MVC async, callback `preHandle` có thể chạy trong initial dispatch và lại ở redispatch. Khi request bắt đầu concurrent handling, MVC có thể gọi `AsyncHandlerInterceptor#afterConcurrentHandlingStarted` thay vì ngay lập tức gọi `postHandle`/`afterCompletion` cho lượt đầu. Khi ASYNC dispatch xử lý giá trị kết quả, interceptor chain lại tham gia theo quy tắc Framework.

Nếu tính metric “một HTTP request” ở interceptor, không tăng request count mỗi lần preHandle; cần dùng request attribute/correlation ID và completion semantics phù hợp.

## 10. Ví dụ runnable: Spring Boot 3 + Java 17

Ứng dụng có một nhóm endpoint hoàn toàn cụ thể:

- `GET /work/callable` → `Callable<ResponseEntity<String>>`, Spring thực thi qua MVC executor.
- `GET /work/callable/fail` → Callable throw exception, `@RestControllerAdvice` chuyển thành 500 JSON có mã ổn định.
- `GET /work/webtask` → `WebAsyncTask<ResponseEntity<String>>` có deadline và fallback callbacks.
- `GET /work/deferred/{id}` → chờ **POST từ request khác** để nhận kết quả.
- `POST /work/deferred/{id}/complete` → producer minh họa, gọi `setResult`.
- `DeferredResult` timeout được server hoàn thành bằng HTTP 504; `onCompletion` dọn pending map.

Ví dụ sử dụng in-memory map để dễ học; **không dùng endpoint complete công khai như production** nếu không có authentication/authorization và giới hạn pending requests.

### 10.1. Cây thư mục

```text
spring-mvc-async-models/
├── pom.xml
└── src/
    ├── main/
    │   └── java/example/
    │       ├── AsyncModelsApplication.java
    │       ├── MvcAsyncConfig.java
    │       └── WorkController.java
    └── test/
        └── java/example/WorkControllerTest.java
```

### 10.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-mvc-async-models</artifactId>
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
```

### 10.3. AsyncModelsApplication.java

```java
package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AsyncModelsApplication {
    public static void main(String[] args) {
        SpringApplication.run(AsyncModelsApplication.class, args);
    }
}
```

### 10.4. MvcAsyncConfig.java

```java
package example;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcAsyncConfig implements WebMvcConfigurer {

    private final AsyncTaskExecutor mvcExecutor;

    public MvcAsyncConfig(
            @Qualifier("mvcExecutor") AsyncTaskExecutor mvcExecutor) {
        this.mvcExecutor = mvcExecutor;
    }

    @Bean(name = "mvcExecutor")
    public static ThreadPoolTaskExecutor mvcExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("mvc-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcExecutor);
        configurer.setDefaultTimeout(10_000L);
    }
}
```

Thread pool được tạo **một lần** dưới dạng Spring bean. Đây là kích thước mẫu cho demo, không phải sizing rule cho production. Nếu ứng dụng thực có task dài, cần connection pool, bounded capacity, queue metric, rejection policy và thời gian shutdown.

### 10.5. WorkController.java

```java
package example;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class WorkController {

    private final Map<String, DeferredResult<ResponseEntity<String>>> pending =
            new ConcurrentHashMap<>();

    @GetMapping("/work/callable")
    public Callable<ResponseEntity<String>> callable() {
        return () -> ResponseEntity.ok("callable-ok");
    }

    @GetMapping("/work/callable/fail")
    public Callable<ResponseEntity<String>> failedCallable() {
        return () -> {
            throw new IllegalStateException("simulated failure");
        };
    }

    @GetMapping("/work/webtask")
    public WebAsyncTask<ResponseEntity<String>> webTask() {
        WebAsyncTask<ResponseEntity<String>> task =
                new WebAsyncTask<>(5_000L,
                    () -> ResponseEntity.ok("webtask-ok"));

        task.onTimeout(() ->
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                          .body("webtask-timeout"));

        task.onError(() ->
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                          .body("webtask-error"));

        task.onCompletion(() -> {
            // Chỉ ghi metric nhẹ / cleanup; không đợi tác vụ blocking.
        });

        return task;
    }

    @GetMapping("/work/deferred/{id}")
    public DeferredResult<ResponseEntity<String>> waitForWork(
            @PathVariable String id) {

        DeferredResult<ResponseEntity<String>> result =
                new DeferredResult<>(5_000L);

        DeferredResult<ResponseEntity<String>> existing =
                pending.putIfAbsent(id, result);

        if (existing != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A waiter already exists");
        }

        result.onTimeout(() ->
            result.setResult(
                ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                              .body("deferred-timeout")));

        result.onError(result::setErrorResult);

        result.onCompletion(() -> pending.remove(id, result));

        return result;
    }

    @PostMapping("/work/deferred/{id}/complete")
    public ResponseEntity<String> complete(
            @PathVariable String id) {

        DeferredResult<ResponseEntity<String>> result = pending.get(id);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        boolean completed =
                result.setResult(ResponseEntity.ok("deferred-ok-" + id));

        return completed
                ? ResponseEntity.accepted().body("event-accepted")
                : ResponseEntity.status(HttpStatus.CONFLICT)
                                .body("already-completed");
    }
}

@RestControllerAdvice
class WorkErrors {
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> onIllegalState(
            IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", "WORK_FAILED"));
    }
}
```

**Điểm cần giải thích của mã trên:**

- `Callable` và `WebAsyncTask` được MVC đưa vào `AsyncTaskExecutor`; controller chỉ tạo task và return ngay.
- `DeferredResult` không tự submit task nào. Nó đợi **POST /complete** như một sự kiện bên ngoài. Trong hệ thống thực, producer thường là message consumer hoặc callback của service.
- `pending.putIfAbsent` tránh âm thầm thay waiter có cùng ID. Không có authentication/limit trong demo: điều này **phải** được bổ sung trong production.
- Timeout set HTTP 504 theo chính sách mẫu. `setResult` có thể thất bại nếu producer đã thắng hoặc request đã hết hiệu lực.
- `onCompletion` xóa đúng pair `id/result` để không vô tình xóa một waiter mới có cùng ID.
- `result.onError(result::setErrorResult)` chuyển exception đến MVC. Callback chạy nhanh, không chặn container thread.
- `@RestControllerAdvice` xử lý exception từ Callable bị throw. Đối với lỗi network/client disconnect, không phải exception nào cũng có thể gửi response sau khi connection hỏng.

### 10.6. Chạy bằng Maven

Ở thư mục có `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

GET Callable:

```bash
curl -i http://localhost:8080/work/callable
```

Expected body khi HTTP 200:

```text
callable-ok
```

GET WebAsyncTask:

```bash
curl -i http://localhost:8080/work/webtask
```

Expected 200 body:

```text
webtask-ok
```

GET failed Callable:

```bash
curl -i http://localhost:8080/work/callable/fail
```

Expected status 500, JSON body:

```json
{"code":"WORK_FAILED"}
```

Server-side async handling không có nghĩa client nhận response “ngay”; client vẫn chờ công việc hoàn tất rồi mới có body.

### 10.7. Mô phỏng external event cho DeferredResult

Mở terminal A trước:

```bash
curl -i http://localhost:8080/work/deferred/job-42
```

Trong vòng timeout 5 giây, mở terminal B:

```bash
curl -i -X POST \
  http://localhost:8080/work/deferred/job-42/complete
```

Terminal B nhận body:

```text
event-accepted
```

Terminal A nhận body:

```text
deferred-ok-job-42
```

Nếu không có producer gửi POST, sau timeout application policy trả 504 và:

```text
deferred-timeout
```

Trên Windows PowerShell có thể dùng `curl.exe` và đưa lệnh trên một dòng. Thứ tự response headers có thể khác, không viết test assert byte-for-byte toàn bộ cURL output. Đây là demo long polling một response; production cần authentication, ownership, giới hạn concurrent waiters và giải pháp lưu trạng thái công việc nếu có nhiều instances.

## 11. Test MVC async đúng cách: hai giai đoạn

`MockMvc` không tự giả lập việc browser chờ qua network. Một async MVC test có hai lượt:

1. Gửi request bằng `mvc.perform(...)` và assert `request().asyncStarted()`.
2. Dùng `asyncDispatch(mvcResult)` để MVC xử lý concurrent result thành response, rồi assert status/body.

Mục tiêu không chỉ kiểm tra controller trả wrapper, mà kiểm tra cả **giá trị cuối cùng và exception handling**.

### 11.1. WorkControllerTest.java

File `src/test/java/example/WorkControllerTest.java`:

```java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void callableUsesAsyncProcessing() throws Exception {
        MvcResult pending = mvc.perform(get("/work/callable"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(pending))
            .andExpect(status().isOk())
            .andExpect(content().string("callable-ok"));
    }

    @Test
    void webAsyncTaskUsesAsyncProcessing() throws Exception {
        MvcResult pending = mvc.perform(get("/work/webtask"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(pending))
            .andExpect(status().isOk())
            .andExpect(content().string("webtask-ok"));
    }

    @Test
    void callableExceptionIsHandledOnRedispatch() throws Exception {
        MvcResult pending = mvc.perform(get("/work/callable/fail"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(pending))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("WORK_FAILED"));
    }

    @Test
    void deferredResultIsCompletedByAnotherRequest()
            throws Exception {
        MvcResult waiting = mvc.perform(
                get("/work/deferred/job-test-1"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(post("/work/deferred/job-test-1/complete"))
            .andExpect(status().isAccepted())
            .andExpect(content().string("event-accepted"));

        mvc.perform(asyncDispatch(waiting))
            .andExpect(status().isOk())
            .andExpect(content().string("deferred-ok-job-test-1"));
    }

    @Test
    void unknownJobCannotBeCompleted() throws Exception {
        mvc.perform(post("/work/deferred/not-present/complete"))
            .andExpect(status().isNotFound());
    }

    @Test
    void duplicateWaiterIsRejectedWithoutReplacingOriginal()
            throws Exception {
        MvcResult waiting = mvc.perform(
                get("/work/deferred/job-test-2"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(get("/work/deferred/job-test-2"))
            .andExpect(status().isConflict());

        mvc.perform(post("/work/deferred/job-test-2/complete"))
            .andExpect(status().isAccepted());

        mvc.perform(asyncDispatch(waiting))
            .andExpect(status().isOk())
            .andExpect(content().string("deferred-ok-job-test-2"));
    }

    @Test
    void newWaiterWithAnotherIdIsIndependent()
            throws Exception {
        MvcResult a = mvc.perform(
                get("/work/deferred/job-test-3"))
            .andExpect(request().asyncStarted())
            .andReturn();

        MvcResult b = mvc.perform(
                get("/work/deferred/job-test-4"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(post("/work/deferred/job-test-4/complete"))
            .andExpect(status().isAccepted());
        mvc.perform(post("/work/deferred/job-test-3/complete"))
            .andExpect(status().isAccepted());

        mvc.perform(asyncDispatch(a))
            .andExpect(status().isOk())
            .andExpect(content().string("deferred-ok-job-test-3"));
        mvc.perform(asyncDispatch(b))
            .andExpect(status().isOk())
            .andExpect(content().string("deferred-ok-job-test-4"));
    }
}
```

Các test sử dụng ID khác nhau để không xung đột khi Spring test context dùng chung controller instance. Trong thực tế, cũng nên bảo đảm cleanup sau test và không dùng state global unbounded.

### 11.2. Vì sao chưa assert timeout bằng cách ngủ 5 giây?

Spring MVC timeout có tương tác Servlet container, async request manager và test scheduling. `Thread.sleep(5_001)` rồi hy vọng trạng thái HTTP luôn chính xác có thể tạo flaky test. Để kiểm thử timeout một cách đáng tin:

- Với unit test, tách logic timeout fallback thành hàm được test độc lập, hoặc kiểm thử interceptor/callback bằng hạ tầng mock phù hợp.
- Với integration test, chạy embedded server trên random port và giới hạn timeout nhỏ dành riêng cho test, kiểm tra phản hồi thật cùng thời gian rõ ràng.
- Trên production, quan sát tỷ lệ timeouts, cancellations và kết quả đã hoàn tất sau khi client ngắt.

Bảy test trên chủ đích xác nhận **completion model và redispatch**, không giả vờ đã kiểm chứng mọi tình huống container timeout/error. Khi cần, thêm tests riêng cho client disconnect và proxy timeouts trên môi trường triển khai thực.

## 12. Spring MVC async so với @Async, CompletableFuture và WebFlux

### 12.1. @Async không có cùng nhiệm vụ

`@Async` thuộc Spring task execution, làm một **method call** được thực thi bất đồng bộ qua proxy/executor khi áp dụng đúng. `Callable/DeferredResult/WebAsyncTask` thuộc **hợp đồng xử lý HTTP response** của Spring MVC.

Một service có `@Async` có thể được gọi từ controller, nhưng nếu controller gọi `future.get()` trước khi return, Servlet thread vẫn bị chặn. Một hướng đúng là return async type được Spring MVC hỗ trợ hoặc adapt kết quả vào `DeferredResult` mà không block.

Cần nhớ `@Async` thường dùng Spring proxy: self-invocation trong cùng object có thể không kích hoạt async advice. Không tự kết luận “method gắn @Async thì mọi call đều được tách thread”.

### 12.2. CompletableFuture / CompletionStage

Spring MVC hỗ trợ nhiều async return types thông qua async adapters, kể cả `CompletionStage`/`CompletableFuture` theo tài liệu Framework. Nếu bạn đã có một future thật từ async API, có thể tận dụng thay vì tạo extra blocking worker.

Không thực hiện:

```java
return () -> future.get();
```

nếu mục tiêu là không giữ thread để chờ một future. Cách này vẫn block worker executor; với producer đã callback-based, `DeferredResult` hoặc MVC async adapters cho `CompletionStage` phù hợp hơn.

### 12.3. MVC async không đồng nghĩa WebFlux

Spring MVC async:

- Servlet processing bắt đầu/redispatch trên container.
- Blocking JDBC/HTTP calls vẫn block worker khi chạy trong Callable.
- Khi response body được ghi trên Servlet stack, việc write có thể vẫn blocking.
- MVC có thể hỗ trợ streaming/reactive return types qua integration riêng, nhưng không biến toàn bộ HTTP layer thành WebFlux.

Spring WebFlux:

- Dựa trên reactive contracts và xử lý non-blocking I/O khi stack và dependencies tương thích.
- Thường cần rethink repository/client, backpressure và cách xử lý CPU-blocking work.
- Không phải “mọi app MVC đổi sang WebFlux sẽ nhanh hơn”; nên đo workload và khả năng sử dụng thư viện.

## 13. Những lỗi thiết kế thường gặp

| Hiểu nhầm | Hậu quả | Hướng xử lý |
| --- | --- | --- |
| “Return Callable làm DB query non-blocking” | DB call vẫn block worker | Hiểu rõ thread và backend client |
| “Async response trả về ngay cho browser” | Client vẫn chờ, timeout UX không đổi | Phân biệt release Servlet thread và response completed |
| “DeferredResult tự chạy task” | Result không ai complete, timeout | Có producer/callback rõ ràng |
| “WebAsyncTask khác hoàn toàn Callable” | Thiết kế thừa lớp | WebAsyncTask chỉ bổ sung timeout/executor/callback |
| Gọi `Future#get()` trong controller | Servlet thread vẫn bị chặn | Return async type thay vì block |
| Dùng `Thread.sleep()` cho mỗi DeferredResult | Thread pool bị giữ vô ích | Chờ event thật hoặc dùng Callable hợp lý |
| Pending map không dọn | Memory leak, stale correlation IDs | `onCompletion` + timeout policy |
| Dùng `put` ghi đè waiter | Mất response của request cũ | `putIfAbsent`, duplicate policy |
| Không kiểm tra boolean `setResult` | Event đến sau timeout bị coi là thành công | Check atomic success/failure |
| Timeout callback làm việc quá lâu | Container thread bị giữ | Set fallback thật nhanh |
| Nghĩ timeout tự cancel JDBC/HTTP | Background task tiếp tục gây side effect | Downstream timeout/cancellation/idempotency |
| Không cấu hình AsyncTaskExecutor | Saturation/queue không kiểm soát | Bounded executor và metrics |
| Tạo executor mới theo request | Thread leak và overhead | Managed Spring bean |
| Dùng thread-local từ servlet trong Callable | Mất/nhầm context | Explicit propagation/task decorator |
| Nghĩ transaction của HTTP thread tự đi theo worker | Transaction không tồn tại như mong muốn | Đặt boundary ở service worker |
| Quên asyncSupported ở custom Servlet Filter | Async có thể thất bại | Đăng ký Servlet/filters tương thích |
| Quên map `DispatcherType.ASYNC` | Filter không chạy ở redispatch | Kiểm tra filter mapping/flags |
| Đếm metric mỗi interceptor preHandle | Một HTTP request bị đếm nhiều | Async-aware lifecycle/marker |
| Mặc định retry POST hoàn thành nhiều lần | Duplicate side effects | Idempotency keys, first-result-wins |
| Để public endpoint complete bất kỳ job | Người lạ hoàn tất job của user khác | Authentication, authorization, ownership |
| Hết timeout nhưng vẫn giữ request reference | Memory/resource leak | Completion cleanup, TTL, cancellation |
| Dùng MockMvc mà không `asyncDispatch` | Chỉ test stage 1, không test final response | Gọi asyncDispatch và assert status/body |
| Test timeout bằng sleep tùy ý | Flaky theo CI/container | Controlled integration testing |
| Coi Servlet MVC async = reactive non-blocking | Sai capacity plan | Chọn stack dựa trên blocking I/O thực |

## 14. Checklist lựa chọn mô hình trong production

Khi nhận một yêu cầu “endpoint phải bất đồng bộ”, nên trả lời:

1. **Kết quả do ai tạo?** Một hàm cần chạy, hay event từ producer khác?
2. **Có blocking không?** JDBC, external HTTP client, encryption/compression, file I/O?
3. **Ai sở hữu executor?** MVC executor, riêng cho workload, messaging consumer hay HTTP client event loop?
4. **Giới hạn concurrency ra sao?** Pool, queue, pending request count, per-user quota, backpressure?
5. **Timeout tổng** có phù hợp gateway, browser, HTTP client và downstream không?
6. **Response type** chỉ có một giá trị, hay nhiều events liên tục cần SSE/WebSocket?
7. **Lỗi** được đưa về `@ExceptionHandler`/fallback thế nào?
8. **Timeout** có hủy tác vụ, hay tác vụ tiếp tục? Side effects có idempotent không?
9. **Client disconnect** được quan sát và cleanup như thế nào?
10. **Context** như locale, MDC, user/tenant được truyền qua thread ra sao?
11. **Pending result** lưu ở đâu khi có nhiều replicas? In-memory map có phù hợp hay cần broker/state store?
12. **Test** đã assert `asyncStarted`, `asyncDispatch`, duplicate completion và lỗi chưa?
13. **Metrics** gồm active waiters, executor active/queue/rejections, timeout rate và p95/p99 client latency chưa?
14. **Security** đã chặn user A complete/wait job của user B chưa?
15. Có cần trả **202 Accepted + job ID** để client poll độc lập thay vì giữ HTTP long-poll request lâu không?

### 14.1. Khi nào nên trả 202 Accepted thay vì giữ request?

Nếu công việc mất nhiều phút hoặc giờ, proxy/client timeout thấp, ứng dụng có nhiều instances hoặc cần retry sau restart, endpoint thường tốt hơn khi:

```text
POST /jobs           → 202 Accepted, Location: /jobs/123
GET  /jobs/123       → trạng thái pending/running/done
GET  /jobs/123/data  → kết quả khi hoàn tất
```

Đây là mô hình **job lifecycle** bền vững; không yêu cầu giữ một DeferredResult sống nhiều phút trong memory. `DeferredResult` phù hợp hơn cho long poll vừa phải; không phải queue/job store thay thế durable messaging.

## 15. Bài tập tự kiểm tra

**1.** `Callable<T>` được ai submit vào executor?  
**Đáp án:** Spring MVC submit qua `AsyncTaskExecutor` được cấu hình.

**2.** `DeferredResult<T>` được ai complete?  
**Đáp án:** Ứng dụng/producer gọi `setResult` hoặc `setErrorResult`.

**3.** `WebAsyncTask<T>` bổ sung gì so với Callable?  
**Đáp án:** Timeout, executor lựa chọn và lifecycle callbacks.

**4.** Spring MVC async có làm browser nhận response trước khi công việc xong?  
**Đáp án:** Không; chỉ giải phóng Servlet request thread ban đầu.

**5.** Sau `Callable` return, MVC xử lý kết quả cuối cùng thế nào?  
**Đáp án:** ASYNC redispatch qua Servlet container rồi xử lý return value như MVC thông thường.

**6.** Có nên dùng DeferredResult và tạo một thread ngồi sleep chờ callback không?  
**Đáp án:** Không; mất lợi ích event-driven và tốn worker.

**7.** Khi hai producer cùng `setResult` vào một DeferredResult?  
**Đáp án:** Chỉ một winner; kiểm tra giá trị boolean và xử lý loser.

**8.** Nếu timeout trả HTTP 504, Callable có chắc ngừng chạy ngay không?  
**Đáp án:** Không; cancellation và downstream deadlines phải được thiết kế riêng.

**9.** Timeout callback của WebAsyncTask trả kiểu gì?  
**Đáp án:** `Callable<T>` tạo một giá trị thay thế có kiểu T.

**10.** Dùng `WebMvcConfigurer#configureAsyncSupport` để cấu hình gì?  
**Đáp án:** Executor, default timeout và async processing interceptors.

**11.** Khi MockMvc perform request Callable, chỉ assert 200 ngay lượt đầu có đúng không?  
**Đáp án:** Không; cần `asyncStarted()` rồi `asyncDispatch(...)`.

**12.** Nếu message broker gửi sự kiện sau timeout, có nên tiếp tục ghi response không?  
**Đáp án:** Không; `setResult` thất bại nếu result đã hết hiệu lực, cần cleanup/ignore.

**13.** `ThreadLocal` MDC có chắc xuất hiện trong Callable worker không?  
**Đáp án:** Không mặc định; cần propagation.

**14.** Spring MVC Callable có tự chuyển JDBC thành non-blocking không?  
**Đáp án:** Không.

**15.** Một request dùng ASYNC redispatch có làm controller chạy lại từ đầu không?  
**Đáp án:** MVC thường tiếp tục xử lý concurrent result, không gọi lại controller method ban đầu như request mới.

**16.** Lỗi thrown trong Callable có thể được `@ExceptionHandler` xử lý không?  
**Đáp án:** Có, qua async result exception handling.

**17.** Pending map của DeferredResult phải được dọn khi nào?  
**Đáp án:** Khi request hoàn tất vì success, timeout hoặc error; dùng `onCompletion` phù hợp.

**18.** Có nên dùng DeferredResult cho stream nhiều events?  
**Đáp án:** Không phù hợp; dùng `SseEmitter`, streaming hoặc reactive types tùy yêu cầu.

**19.** WebAsyncTask timeout được đo bắt đầu lúc nào?  
**Đáp án:** Theo async request lifecycle của Servlet/container và MVC configuration, không phải chỉ “thời gian Callable chạy trên CPU”.

**20.** Công việc nhiều phút cần survive restart nên dùng gì?  
**Đáp án:** Thường dùng durable job system + 202 Accepted/job status thay vì giữ HTTP pending vô thời hạn.

## 16. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Asynchronous Requests](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html): `Callable`, `DeferredResult`, `WebAsyncTask`, ASYNC redispatch, exceptions, interceptors, executor và context propagation.
- [Spring Framework 6.2 — DeferredResult Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/context/request/async/DeferredResult.html): `setResult`, `setErrorResult`, callbacks, hasResult và lifecycle.
- [Spring Framework 6.2 — WebAsyncTask Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/context/request/async/WebAsyncTask.html): timeout, executor constructors, `onTimeout`, `onError`, `onCompletion`.
- [Spring Framework 6.2 — MockMvc Async Testing](https://docs.spring.io/spring-framework/reference/6.2/testing/mockmvc/hamcrest/async-requests.html): hai bước `asyncStarted` và `asyncDispatch`.
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): executor, `@Async` và cấu hình task execution.
- [Spring Framework 6.2 — Java WebMvcConfigurer](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/config/annotation/WebMvcConfigurer.html): `configureAsyncSupport`.
- [Jakarta Servlet Specification](https://jakarta.ee/specifications/servlet/6.0/): `AsyncContext`, `asyncSupported` và Servlet dispatcher types.

**Quy tắc ghi nhớ:** nếu **Spring phải chạy một Callable để tạo kết quả**, return `Callable`; nếu muốn **timeout/executor/callback riêng** cho Callable, dùng `WebAsyncTask`; nếu **một event ngoài request tự mang kết quả đến**, dùng `DeferredResult`. Cả ba đều giải phóng Servlet thread ban đầu nhưng vẫn phải có tài nguyên xử lý, giới hạn concurrency, timeout, cleanup và bảo mật đúng cách.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-async-processing-003">Cấu hình TaskExecutor, timeout và giới hạn tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Cấu hình TaskExecutor, timeout và giới hạn tài nguyên trong Spring MVC bất đồng bộ

## 1. Mục tiêu bài học

Spring MVC trên Servlet stack có thể xử lý HTTP theo mô hình bất đồng bộ: controller trả `Callable<T>`, `WebAsyncTask<T>` hoặc `DeferredResult<T>`; Servlet request thread ban đầu rời đi trong khi kết quả được tạo sau đó. Điều này giúp tránh giữ một luồng Servlet chỉ để chờ tác vụ, nhưng không khiến CPU, worker threads, connection, bộ nhớ, hàng đợi hay hệ thống phụ thuộc trở nên vô hạn.

Sau bài học, bạn sẽ:

- Hiểu vòng đời Servlet `REQUEST → startAsync → worker/callback → ASYNC dispatch → response`.
- Phân biệt `Callable`, `WebAsyncTask`, `DeferredResult`, `@Async` và `CompletableFuture`.
- Cấu hình `WebMvcConfigurer.configureAsyncSupport` và `AsyncSupportConfigurer`.
- Đặt `ThreadPoolTaskExecutor` có `corePoolSize`, `maxPoolSize`, `queueCapacity`, chính sách rejection và shutdown.
- Phân biệt Servlet async timeout, timeout riêng mỗi request, client/gateway timeout, downstream HTTP/DB timeout.
- Biết timeout **không tự hủy** mọi tác vụ nền hoặc rollback tác dụng phụ.
- Thiết kế giới hạn tài nguyên: queue, admission control, connection pool, pending DeferredResult, SSE, backpressure và quotas.
- Kiểm tra response cuối của MVC async bằng `MockMvc.asyncDispatch` và thử HTTP thực qua `curl`.
- Nhận diện overload, timeout, disconnect, context propagation và đo queue wait/throughput/latency.

Ví dụ dùng **Java 17+, Spring Boot 3.x, Spring Framework 6.x, Jakarta Servlet**. Đây là Spring MVC Servlet stack, không phải WebFlux. Mã `Thread.sleep` trong demo chỉ mô phỏng I/O; production cần timeout/cancellation của downstream thực tế.

## 2. Servlet async hoạt động như thế nào?

Luồng thông thường khi một controller trả `Callable`:

```text
Client
  |
  | GET /async/callable
  v
Servlet container -------------- DispatcherServlet
  |                                  |
  |  REQUEST thread                   | controller trả Callable
  |                                  | request.startAsync()
  |                                  | submit Callable -> AsyncTaskExecutor
  |  REQUEST thread quay về pool       |
  |                                  |
  |                              Worker thread
  |                                  | chạy Callable, I/O/CPU
  |                                  | trả value/throw error
  |                                  v
  |                     Servlet ASYNC dispatch
  |                                  |
  |                     DispatcherServlet xử lý result
  |                     HttpMessageConverter ghi JSON
  v
Client nhận response
```

Một số điểm dễ nhầm:

1. **Chỉ luồng Servlet ban đầu được trả về sớm.** Nếu `Callable` gọi JDBC synchronous, ngủ, chờ outbound HTTP hoặc đọc tệp, **worker vẫn bị block**.
2. **HTTP response vẫn mở** trong khi chờ; TCP connection, request attributes và buffer tương ứng có chi phí.
3. **Callable result cần async dispatch** trở lại Servlet container để Spring MVC hoàn tất xử lý response.
4. Filter và interceptor có lifecycle khác sau khi bắt đầu async; logging/cleanup cần xét cả REQUEST/ASYNC dispatch.
5. Nếu worker không tạo kết quả đúng hạn, Servlet async timeout có thể kích hoạt; không thể mặc định Java task bị hủy ở đúng mốc.
6. Async không tự tăng sức chứa database hoặc outbound HTTP connection pool; tăng workers có thể chỉ làm nhiều request chờ ở tầng thấp hơn.

Spring Framework mô tả rõ quá trình này trong tài liệu chính thức về asynchronous requests.

## 3. Các kiểu xử lý async của Spring MVC

| Return type | Nơi tạo kết quả | Tình huống phù hợp |
| --- | --- | --- |
| `ResponseEntity<T>` đồng bộ | Servlet request thread | Xử lý ngắn, giản đơn |
| `Callable<T>` | MVC `AsyncTaskExecutor` | Giải phóng request thread khi callable chạy việc khác |
| `WebAsyncTask<T>` | MVC executor hoặc executor riêng | Callable cần timeout và callbacks theo request |
| `DeferredResult<T>` | Producer của ứng dụng/event listener | Kết quả đến từ callback/message/job bên ngoài |
| `CompletableFuture<T>` | Executor tạo future do ứng dụng chọn | Pipeline async có timeout/cancellation riêng |
| `@Async` trên bean method | Spring async infrastructure/executor riêng | Công việc bất đồng bộ tại tầng service |
| `SseEmitter`, `ResponseBodyEmitter` | Cơ chế streaming của MVC | Gửi nhiều events/chunks, cần connection budget |

**`DeferredResult` không tự submit business logic vào MVC executor.** Controller đăng ký một placeholder; thread/event source của ứng dụng phải gọi `setResult` hoặc `setErrorResult` khi có kết quả.

**`@Async` không đồng nghĩa MVC async.** `@EnableAsync` và executor của `@Async` có cơ chế cấu hình khác; `configureAsyncSupport` là điểm chủ yếu để chỉ rõ executor cho `Callable` MVC. Không bật `@EnableAsync` chỉ để controller có thể return `Callable`.

Nếu tác vụ đã rất nhanh, mô hình đồng bộ có thể tối ưu hơn. Không thêm async bằng thói quen khi chưa đo thread usage, I/O wait, p95/p99 và nhu cầu mở connection lâu.

## 4. configureAsyncSupport là điểm cấu hình MVC

Spring MVC cho phép override callback của `WebMvcConfigurer`:

```java
@Override
public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    configurer.setTaskExecutor(mvcExecutor());
    configurer.setDefaultTimeout(2500L);
}
```

Đây là **đoạn method minh họa** trong class cấu hình đã có `mvcExecutor()`. Mã runnable đầy đủ ở phần sau.

- `setTaskExecutor(AsyncTaskExecutor)` chỉ định executor MVC cho `Callable` và một số trường hợp blocking writes khi MVC thích ứng streaming/reactive return types.
- `setDefaultTimeout(long)` dùng **milli giây** làm timeout mặc định cho async requests khi không override theo return type.
- Khi không đặt default timeout, giá trị phụ thuộc Servlet container/framework configuration, không nên hardcode một con số được ghi nhớ từ phiên bản khác.
- Với `WebAsyncTask`, có thể chỉ định timeout và executor riêng.
- Với `DeferredResult`, `ResponseBodyEmitter`, `SseEmitter`, có thể cấu hình timeout theo từng object.
- `registerCallableInterceptors(...)` và `registerDeferredResultInterceptors(...)` hỗ trợ timeout/error/completion handling chuyên sâu.

**Phạm vi:** Async timeout không phải deadline toàn HTTP: TCP connect, proxy, servlet filters, argument binding và downstream có lifecycle của chúng. Timeout Servlet async thường bắt đầu sau khi request thread ban đầu hoàn tất xử lý dispatch và kết thúc khi request được dispatch lại.

## 5. Chọn ThreadPoolTaskExecutor thay vì executor không được giới hạn

`ThreadPoolTaskExecutor` là Spring-managed wrapper quanh Java `ThreadPoolExecutor`, cung cấp cách đặt kích thước core/max, hàng đợi, thread prefix, shutdown và metrics.

Ví dụ ban đầu để học:

```text
corePoolSize = 2
maxPoolSize = 4
queueCapacity = 8
keepAliveSeconds = 30
rejection = AbortPolicy
```

Hành vi quan trọng của queue dương:

1. Khi worker count thấp hơn core, executor tạo core worker.
2. Khi core workers đang bận, task mới thường **vào queue trước**.
3. Khi queue đầy, executor mới mở rộng từ core tới max.
4. Khi pool đạt max **và** queue đầy, task mới bị reject.

Với core=2, max=4, queue=8, năng lực lý thuyết tại thời điểm bão hòa là **4 task đang chạy + 8 task chờ**. Con số này không phải giới hạn 12 HTTP connections vì mỗi async request có thể đã được nhận, còn pending DeferredResult hoặc các nguồn async khác không nằm trong MVC callable queue.

**Bẫy thường gặp:** đặt max=100 mà queueCapacity vô hạn khiến pool hiếm khi tăng trên core vì hàng đợi không đầy. Queue dài làm latency đuôi tăng, dẫn tới hàng loạt timeout dù CPU còn trống hoặc downstream đã bão hòa.

`setQueueCapacity(0)` thường tạo `SynchronousQueue` (handoff trực tiếp), giúp mở threads nhanh hơn, nhưng nếu max không đủ sẽ reject nhanh; phải benchmark thay vì coi queue=0 luôn tốt.

## 6. Rejection policy và backpressure

Các chiến lược từ Java `ThreadPoolExecutor`:

| Policy | Hành vi | Hệ quả cho HTTP async |
| --- | --- | --- |
| `AbortPolicy` | Ném exception khi quá tải | Quan sát được, có thể map thành 503 khi phù hợp |
| `CallerRunsPolicy` | Thread submit tự chạy task | Có thể chặn Servlet request thread, trái mục tiêu giải phóng |
| `DiscardPolicy` | Bỏ task | Request có thể chờ đến timeout mà không ai tạo kết quả |
| `DiscardOldestPolicy` | Bỏ task cũ trong queue | Request trước bị mất công việc khó chẩn đoán |
| Custom rejection | Reject theo quota/bulkhead | Cần bao phủ tất cả race và errors |

Với HTTP request-response, tránh silently discard công việc. `AbortPolicy` thường giúp định hình overload rõ, nhưng không mặc định mọi `TaskRejectedException` sẽ được chuyển thành một HTTP status đẹp: nó còn phụ thuộc vị trí xảy ra rejection, MVC exception resolving và response đã commit chưa.

Một phương án production có thể **từ chối request trước khi submit** dựa trên quyền, rate/quota, số pending requests và capacity. Khi reject:
- `429 Too Many Requests` thường phù hợp với quota/rate theo identity.
- `503 Service Unavailable` có thể phù hợp với capacity server/downstream tạm thời.
- `Retry-After` chỉ đặt nếu có chính sách retry đáng tin.
- Retry phải backoff/jitter và có idempotency nếu request có side effects.

Không dùng queue vô hạn chỉ để “tránh thấy 503”. Nó biến overload nhìn thấy được thành thời gian chờ dài, timeout, memory pressure và retry storm.

## 7. Timeout nhiều tầng: thời gian ai kiểm soát?

Giả sử ứng dụng có các mốc ví dụ:

```text
Client timeout       5 s
Gateway timeout      4 s
Servlet async        3 s
WebAsyncTask         2 s
Outbound HTTP read   1 s
DB statement         800 ms
```

Đây chỉ là **ví dụ policy**, không phải các giá trị mặc định.

Các tầng cần phân biệt:

- **Client timeout:** người dùng/chương trình gọi đã ngừng đợi; request ở server có thể vẫn đang chạy.
- **Reverse proxy/gateway timeout:** ingress không đợi upstream nữa, có thể gửi 504 hoặc đóng kết nối; Spring handler có thể không hề biết ngay.
- **Servlet async timeout:** lifecycle request bên server hết hạn, callbacks/interceptors có cơ hội tạo response/error.
- **WebAsyncTask timeout:** override timeout cho callable cụ thể.
- **DeferredResult timeout:** thời gian chờ callback tạo kết quả, không giới hạn producer job bên ngoài.
- **HTTP client connect/read/response timeout:** thuộc chính outbound client.
- **Database query/transaction timeout:** do driver, pool và transaction manager xử lý theo khả năng của chúng.
- **Queue wait:** task chưa bắt đầu nhưng thời gian yêu cầu bất đồng bộ vẫn đang trôi.

**Quy tắc:** Timeout HTTP không tự động cancel mọi I/O, ngắt tất cả worker, rollback transaction hoặc xóa side effect. Ngay cả khi Java task bị interrupt, driver/network call có thể không dừng được ngay. Tác vụ ghi dữ liệu quan trọng cần idempotency key, transaction/uniqueness constraint và mô hình retry rõ ràng.

## 8. WebAsyncTask: timeout và callback riêng từng request

Ví dụ:

```java
WebAsyncTask<ResponseEntity<ResultDto>> task =
        new WebAsyncTask<>(400L, () -> {
            ResultDto result = slowService.compute();
            return ResponseEntity.ok(result);
        });

task.onTimeout(() -> ResponseEntity.status(503)
        .body(new ResultDto("timeout")));

task.onCompletion(() -> recordMetrics());
return task;
```

Đoạn trên là **snippet** cần có bean/service và imports tương ứng; mã hoàn chỉnh ở phần ứng dụng demo.

- `onTimeout(Callable<V>)` trả một kết quả thay thế, có thể là 503 với JSON đã xác định.
- Callback timeout chạy trên container thread và **phải rất nhanh**; không thực hiện query DB dài hoặc gọi network chặn trong callback.
- `onError` xử lý sự cố async tùy policy; không nuốt lỗi business thành 200.
- `onCompletion` có thể dùng cleanup/metrics, cần idempotent và nhẹ.
- Nếu Callable hoàn tất trước deadline, Spring MVC sử dụng kết quả của Callable để tiếp tục ASYNC dispatch.
- Nếu timeout trước, response có thể được hoàn tất bằng fallback; Callable vẫn có thể tiếp tục.
- Nếu response đã commit, không đảm bảo callback có thể đổi status/body thành một JSON lỗi chuẩn.

Đặt timeout riêng 400ms không có nghĩa tác vụ `Thread.sleep(3000)` sẽ bị ngắt chắc chắn lúc 400ms. Deadline request và vòng đời worker là hai khái niệm khác nhau.

## 9. DeferredResult và pending request không sử dụng MVC worker

Ví dụ long polling:

```java
@GetMapping("/events/{id}")
public DeferredResult<ResponseEntity<EventDto>> waitForEvent(
        @PathVariable String id) {

    DeferredResult<ResponseEntity<EventDto>> result =
            new DeferredResult<>(1500L);

    registry.add(id, result);

    result.onTimeout(() -> {
        registry.remove(id, result);
        result.setResult(ResponseEntity.noContent().build());
    });
    result.onCompletion(() -> registry.remove(id, result));

    return result;
}
```

Đây là **đoạn minh họa**, chưa phải tệp runnable: `EventDto` và `registry` phải được ứng dụng cung cấp. Khi event xuất hiện, publisher có thể gọi `result.setResult(ResponseEntity.ok(event))`, kích hoạt async dispatch.

Lưu ý:

- `setResult` trả boolean; sau timeout/completion việc đặt kết quả có thể thất bại.
- `onTimeout` chạy trong lifecycle Servlet async, không thay việc cancel producer job.
- `registry` phải giới hạn số pending entries, dọn trong `onCompletion`/timeout/error; không bao giờ giữ đối tượng `DeferredResult` vô thời hạn.
- 204 trong ví dụ chỉ là **long-poll timeout không có event** theo một API contract giả định; khi đây là lỗi availability, 503 có thể hợp lý hơn.
- Nhiều pending requests vẫn giữ HTTP connections, request/response state và bộ nhớ, dù worker pool rảnh.
- Nếu bạn submit producer vào một executor riêng, executor đó cần queue/timeout/rejection policy riêng; MVC `setTaskExecutor` không tự giới hạn nó.

## 10. Streaming, SSE và giới hạn resource

`SseEmitter`, `ResponseBodyEmitter` và `StreamingResponseBody` cần quan tâm riêng:

- SSE/long polling giữ một HTTP connection lâu; tổng pending sessions phải được giới hạn per user/tenant.
- Khi client chậm, buffers tăng hoặc write bị block; cần backpressure, buffer cap và drop/disconnect policy.
- Spring MVC có thể dùng worker cho blocking writes, nên executor cần được cấu hình và monitor.
- HTTP response có thể đã commit sau khi gửi chunk đầu; nếu phát sinh lỗi giữa stream, không thể đơn giản thay status thành 503 và gửi JSON thông thường.
- Client disconnect có thể gây `IOException`; cần unregister subscriptions, hủy producer phù hợp và cleanup.
- Nếu có heartbeat, đặt tần suất và timeout hợp lý để không biến keep-alive thành overhead quá lớn.
- Không dùng một executor chung quá nhỏ cho tác vụ CPU nặng, stream dài và REST requests ngắn nếu có nguy cơ starvation.
- Với job chạy vài phút, thường phù hợp hơn là `202 Accepted + jobId` rồi client poll trạng thái, thay vì giữ HTTP response mở vô hạn.

Streaming không phải là miễn phí khi hệ thống có hàng nghìn kết nối đồng thời.

## 11. Mã runnable: Spring Boot 3 + Java 17

Tạo Maven project Spring Initializr Java 17+, dependencies:
- `spring-boot-starter-web`
- `spring-boot-starter-test` (test scope)

Ứng dụng demo không cần DB. Nó sử dụng một executor riêng với core=2, max=4, queue=8; MVC default async timeout 2500ms. Một endpoint dùng `Callable`, endpoint còn lại dùng `WebAsyncTask` 400ms để quan sát timeout riêng.

Cấu trúc:

```text
src/main/java/com/example/demo/
  DemoApplication.java
  MvcAsyncConfig.java
  AsyncDemoController.java
src/test/java/com/example/demo/
  AsyncDemoControllerTest.java
```

### 11.1. DemoApplication.java

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

### 11.2. MvcAsyncConfig.java

```java
package com.example.demo;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcAsyncConfig implements WebMvcConfigurer {

    @Bean(name = "mvcAsyncExecutor")
    public ThreadPoolTaskExecutor mvcAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("mvc-async-");

        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncExecutor());
        configurer.setDefaultTimeout(2500L);
    }
}
```

`@Configuration` mặc định proxy bean methods, nên gọi `mvcAsyncExecutor()` từ method trên cùng configuration class sẽ nhận **bean singleton Spring quản lý**, không tạo một executor mới mỗi request. Nếu đổi sang `@Configuration(proxyBeanMethods = false)`, không nên dựa vào lời gọi method trực tiếp này để lấy singleton; cần tiêm bean đã tạo từ cấu hình khác hoặc wiring tường minh.

Pool này là lựa chọn **mang tính bài tập**, không được coi là default phù hợp cho mọi production. `AbortPolicy` giúp rejection có thể quan sát được, nhưng bạn cần admission/error policy phù hợp nếu muốn trả 503 theo hợp đồng.

### 11.3. AsyncDemoController.java

```java
package com.example.demo;

import java.util.concurrent.Callable;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/async")
public class AsyncDemoController {

    @GetMapping("/callable")
    public Callable<WorkResponse> callable(
            @RequestParam(defaultValue = "20") long delayMs) {

        long duration = validateDelay(delayMs);

        return () -> {
            Thread.sleep(duration); // Mô phỏng blocking work.
            return new WorkResponse(
                "done", Thread.currentThread().getName());
        };
    }

    @GetMapping("/timed")
    public WebAsyncTask<ResponseEntity<WorkResponse>> timed(
            @RequestParam(defaultValue = "20") long delayMs) {

        long duration = validateDelay(delayMs);

        WebAsyncTask<ResponseEntity<WorkResponse>> task =
                new WebAsyncTask<>(400L, () -> {
                    Thread.sleep(duration);
                    return ResponseEntity.ok(new WorkResponse(
                        "done", Thread.currentThread().getName()));
                });

        task.onTimeout(() ->
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new WorkResponse("timeout", "container")));

        task.onCompletion(() -> {
            // Có thể ghi metrics/cleanup nhẹ và idempotent.
        });
        return task;
    }

    private long validateDelay(long delayMs) {
        if (delayMs < 0 || delayMs > 5000) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "delayMs must be between 0 and 5000");
        }
        return delayMs;
    }

    public record WorkResponse(String status, String worker) {}
}
```

Phân tích:

- `/async/callable` trả `Callable` nên công việc được chuyển sang MVC executor; timeout mặc định là 2500ms.
- `/async/timed` trả `WebAsyncTask` với timeout riêng 400ms; callback có thể tạo HTTP 503 khi thời hạn hết.
- `delayMs` bị giới hạn từ 0 đến 5000ms để demo không biến thành endpoint sleep vô hạn. Trong production không đưa endpoint này cho client công khai.
- `worker` ghi tên thread thực thi. Prefix `mvc-async-` do executor tạo, phần số không xác định trước.
- `Thread.sleep` làm nghẽn worker trong demo; ứng dụng thật cần giới hạn/tối ưu I/O và service concurrency.
- Timeout fallback **không bảo đảm** đã dừng sleep hoặc hủy tác dụng phụ; nếu task tiếp tục chạy, kết quả muộn có thể không được dùng làm HTTP body.
- Response không có authentication/authorization trong demo; production phải bảo vệ route và không cho user tùy ý chọn độ dài công việc.

### 11.4. Khởi động

macOS/Linux:

```bash
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Nếu `server.port` chưa đổi, truy cập `localhost:8080`. Các file trên cần Spring Boot classpath; `javac AsyncDemoController.java` riêng lẻ không thể biên dịch khi không có Spring dependencies.

## 12. Curl thử async response và timeout

### Thành công với Callable

```bash
curl -i 'http://localhost:8080/async/callable?delayMs=20'
```

Kỳ vọng HTTP 200 và JSON đại diện:

```json
{
  "status": "done",
  "worker": "mvc-async-1"
}
```

`mvc-async-1` **chỉ là ví dụ**: không hardcode thread number trong test. Quan trọng là `status="done"`.

### WebAsyncTask hoàn tất trước deadline

```bash
curl -i 'http://localhost:8080/async/timed?delayMs=20'
```

Khi hệ thống không bị bão hòa, kỳ vọng HTTP 200 với `status="done"`. Tổng thời gian còn có queue wait và thread scheduling; 20ms sleep không đảm bảo mọi HTTP request sẽ trả trong 400ms trên máy quá tải.

### Timeout riêng

```bash
curl -i 'http://localhost:8080/async/timed?delayMs=3000'
```

Với embedded Servlet container bình thường, HTTP thường trở thành **503 Service Unavailable** kèm JSON `status="timeout"` từ callback. Thời điểm response không phải cam kết thời gian thực tuyệt đối, và worker có thể vẫn chạy sau timeout. Nếu gateway ngắt trước, client có thể không thấy JSON fallback của ứng dụng.

### Input sai

```bash
curl -i 'http://localhost:8080/async/timed?delayMs=-1'
```

Kỳ vọng HTTP 400. Nếu `delayMs=abc`, Spring argument conversion có thể tạo 400 trước khi controller trả WebAsyncTask.

## 13. MockMvc phải có asyncDispatch

Với controller trả `Callable`/`WebAsyncTask`, lần `perform` đầu thường chỉ xác nhận request đã bắt đầu Servlet async. Kết quả HTTP thực được xử lý qua dispatch tiếp theo, nên cần gọi `asyncDispatch(...)`.

Tệp `src/test/java/com/example/demo/AsyncDemoControllerTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AsyncDemoControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void callableReturnsOnAsyncDispatch() throws Exception {
        MvcResult first = mvc.perform(get("/async/callable")
                    .param("delayMs", "0"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(first))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("done"));
    }

    @Test
    void webAsyncTaskReturnsOnAsyncDispatch() throws Exception {
        MvcResult first = mvc.perform(get("/async/timed")
                    .param("delayMs", "0"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(first))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("done"));
    }

    @Test
    void badInputFailsBeforeAsyncStarted() throws Exception {
        mvc.perform(get("/async/timed").param("delayMs", "-1"))
            .andExpect(status().isBadRequest());
    }
}
```

Chạy tests:

```bash
./mvnw test
```

PowerShell:

```powershell
.\mvnw.cmd test
```

Đây là ví dụ test có thể chạy với dependencies đã nêu, **không tuyên bố đã chạy trong repository của người dùng**. Nếu bật Spring Security, cần setup authentication/authorization phù hợp; việc request bị chặn trước MVC không chứng minh async controller sai.

**Giới hạn của MockMvc:** nó không mô phỏng đầy đủ timeout schedule/error dispatch/network behavior của Servlet container thực, cũng không kiểm tra proxy hoặc disconnect. Muốn xác nhận timeout 400ms và fallback HTTP 503, dùng HTTP integration test với embedded container chạy cổng thật hoặc `curl`; kết quả có thể khác trong môi trường tải cao.

## 14. Java 17 độc lập: bounded queue và reject deterministic

Một ví dụ Java thuần thể hiện vì sao bounded queue tạo rejection rõ. Tệp `BoundedPoolDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BoundedPoolDemo {

    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            new ThreadPoolExecutor.AbortPolicy());

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<?> running = pool.submit(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });

            started.await(); // Worker chắc chắn đã bận.
            Future<?> queued = pool.submit(() -> {});

            boolean rejected = false;
            try {
                pool.submit(() -> {}); // 1 running + 1 queued
            } catch (RejectedExecutionException ex) {
                rejected = true;
            } finally {
                release.countDown();
            }

            running.get();
            queued.get();
            System.out.println("rejected=" + rejected);
        } finally {
            release.countDown();
            pool.shutdown();
        }

        System.out.println("terminated=" +
                pool.awaitTermination(1, TimeUnit.SECONDS));
    }
}
```

Biên dịch và chạy Java 17:

```bash
javac BoundedPoolDemo.java
java BoundedPoolDemo
```

Stdout bình thường:

```text
rejected=true
terminated=true
```

`CountDownLatch` khiến worker chắc chắn đang bận trước khi task thứ hai được queue và task thứ ba bị reject, không phụ thuộc vào phỏng đoán của `Thread.sleep(10)`. `terminated=true` xảy ra khi task đã xong và shutdown hoàn thành trong 1 giây; trên máy bị đình trệ bất thường `awaitTermination` có thể trả false nên đây không phải một deadline real-time chắc chắn.

Trong Spring, `ThreadPoolTaskExecutor` thường chuyển saturation sang `TaskRejectedException` của Spring, cần xử lý đúng tại HTTP/application layer. Không lấy `Executors.newFixedThreadPool` mặc định làm bounded queue mà không xem implementation: queue của factory này có thể tăng không giới hạn.

## 15. Timeout không hủy side effect và retry phải có idempotency

Ví dụ tình huống:

```text
T=0ms     HTTP request bắt đầu
T=50ms    Worker bắt đầu gọi database/payment gateway
T=400ms   Servlet async timeout, client nhận 503
T=800ms   Downstream call còn xử lý
T=1200ms  Transaction hoặc payment có thể commit
T=1400ms  Worker cố trả result nhưng HTTP request đã hoàn tất
```

Đây là một **tình huống khả dĩ**; không phải timeline mặc định của Spring.

Hậu quả: client thấy 503 có thể retry, trong khi thao tác cũ thực sự thành công. Với đơn hàng/thanh toán/ghi dữ liệu, cần:
- Idempotency key do client tạo đúng contract, lưu kết quả ở backend.
- DB unique constraint/transaction bảo đảm không tạo trùng.
- Propagate deadline/cancellation vào outbound client khi được hỗ trợ.
- Không dùng HTTP timeout làm dấu hiệu chắc chắn transaction đã rollback.
- Nếu công việc dài, chuyển sang **job processing có trạng thái**, trả `202 Accepted` cùng job ID; client dùng GET tra status.
- Không cho background job dựa vào `HttpServletRequest` còn tồn tại sau response.

## 16. Admission control, semaphore và request pending

Bounded pool giới hạn **workers + queue**, không tự giới hạn mọi pending HTTP requests, SSE connections hoặc `DeferredResult` registrations. Một bảo vệ hoàn chỉnh cần:

1. Gateway/container giới hạn tổng connections, in-flight requests, request timeouts.
2. Application rate/quota limit theo tenant/user và endpoint.
3. Executor bounded core/max/queue, rejection policy được quan sát.
4. DB connection pool và HTTP client connection pool không bị vượt budget.
5. Giới hạn `DeferredResult`/SSE pending sessions và cleanup.
6. CPU/memory/time budget cho parsing/streaming/response bodies.
7. Bulkhead riêng cho long-running export, báo cáo, CPU-heavy jobs và REST latency thấp.
8. Retry/backoff/circuit breaker để không dồn tải lên downstream đang chậm.

Một `Semaphore` có thể giới hạn số hoạt động, nhưng phải định nghĩa permit đo **HTTP in-flight** hay **worker đang chạy**. Nếu release khi HTTP timeout mà worker vẫn chạy, bạn đã giảm số request pending chứ không thật sự giảm worker occupancy. Nếu chỉ release trong `Callable.finally` mà task bị reject trước khi worker bắt đầu, permit có thể không được trả. Vì vậy semaphore logic cần xét success, rejection, timeout, cancellation, disconnect và cleanup idempotent; không coi một đoạn `tryAcquire/finally release` ngắn là đủ cho mọi trường hợp.

## 17. Servlet Filter, ASYNC dispatcher và interceptor

Để Servlet async hoạt động:

- Servlet và filters liên quan cần `asyncSupported=true` trong registration.
- Filter mapping cần bao gồm `DispatcherType.ASYNC` nếu logic filter cần chạy trên async dispatch.
- `OncePerRequestFilter` có hooks `shouldNotFilterAsyncDispatch()`/`shouldNotFilterErrorDispatch()`; không giả định mọi filter chạy đúng một lần trong toàn request.
- `AsyncHandlerInterceptor.afterConcurrentHandlingStarted(...)` có thể được gọi khi controller khởi tạo async thay vì `postHandle`/`afterCompletion` thông thường của initial dispatch.
- Khi async result được dispatch lại, Spring MVC/filters có thể đi qua một lifecycle bổ sung.
- Logs, metrics, tracing/correlation ID cần tránh đếm hai lần và phải dọn state đúng thời điểm **request hoàn tất**, không chỉ khi initial `chain.doFilter` trở về.

Nếu một Filter bất kỳ trên request chain không hỗ trợ async, `request.startAsync()` có thể thất bại dù controller đã return `Callable`. Đặc biệt cần test WAR deployment trên Servlet container bên ngoài: registration và `DispatcherType` có thể khác embedded server.

## 18. ThreadLocal, SecurityContext, Locale và MDC

Async worker là thread khác. Những context sau **không tự động được propagate đầy đủ** qua mọi executor:

- `RequestContextHolder`/Servlet request attributes.
- `LocaleContextHolder`/locale.
- SLF4J `MDC`.
- Spring Security `SecurityContextHolder`.
- Transaction/JPA persistence context.

Một task nên nhận snapshot immutable của thông tin cần thiết qua method parameters, chẳng hạn `Locale`, correlation ID hoặc user ID đã xác thực. Nếu cần propagation cho Spring Security/tracing/MDC, dùng cơ chế chính thống có lifecycle/cleanup rõ ràng; không tự copy toàn bộ `HttpServletRequest` vào thread pool, và không bật `InheritableThreadLocal` vô điều kiện vì pooled threads tái sử dụng giữa requests.

Ví dụ ý tưởng:

```java
Locale selectedLocale = locale;
String correlationId = safeGeneratedCorrelationId;

return () -> reportService.prepare(
        selectedLocale, correlationId);
```

Đây là snippet minh họa trong controller đã có bean/variables tương ứng. Tránh lấy identity tin cậy từ header client tự khai báo. Worker phải được kiểm tra quyền khi truy cập dữ liệu nhạy cảm theo chính sách phù hợp.

## 19. Kế hoạch capacity và Little's Law

Số request đồng thời không trực tiếp bằng số Java worker threads. Trong trạng thái ổn định:

```text
L = λ × W

L: số request trung bình trong hệ thống
λ: throughput trung bình (request/giây)
W: thời gian lưu trong hệ thống (giây)
```

Nếu app xử lý 100 request/giây với mean latency 0,25 giây thì L xấp xỉ 25 requests đang hiện diện, **không phải cấu hình chính xác 25 worker threads**. Các request có thể chờ async, chờ queue, connection, DB hoặc network; workload peak/p99 khác mean.

Một worker pool có 4 threads, mỗi blocking task chiếm trung bình 300ms, có trần xử lý thô khoảng 4/0,3 ≈ 13 task/giây trước overhead. Nếu arrival liên tục vượt sustainable throughput, queue sẽ đầy; tăng queue 8 thành 800 chỉ trì hoãn rejection, đồng thời làm tail latency/timeouts lớn hơn.

CPU-bound tasks không nên dùng số threads lớn tùy ý; I/O-bound tasks có thể cần concurrency nhiều hơn nhưng phải cân bằng với connection pool, memory và downstream rate limits. Trên nhiều replicas, mỗi pod có executor riêng, tổng concurrency có thể tăng theo số pods; downstream shared DB vẫn có limit chung.

## 20. Metrics và cách debug overload/timeout

| Metric | Điều quan sát |
| --- | --- |
| `poolSize`/`activeCount` | Worker đang dùng, pool có chạm max? |
| `queueSize`/`queueCapacity` | Backlog và độ gần saturation |
| submitted/completed/rejected tasks | Rejection rate và throughput |
| queue wait time | Task phải đợi bao lâu trước khi chạy |
| execution time | Work thực sự mất bao lâu |
| HTTP total latency p50/p95/p99 | Trải nghiệm client gồm queue/dispatch/network |
| Async timeout rate | Deadline có phù hợp không |
| Pending DeferredResult/SSE | Request registrations và connections đang mở |
| DB/HTTP client pool occupancy | Bottleneck downstream |
| CPU, heap, GC, file descriptors | Tài nguyên toàn process |

`ThreadPoolTaskExecutor` có các getters cho pool/active/queue state; có thể expose qua metrics/observability framework phù hợp. Không công khai endpoint trả thông tin nội bộ mà không có authorization.

Quy trình debug:

1. Xác định 503 do `WebAsyncTask.onTimeout`, executor rejection, gateway hay service downstream.
2. Kiểm tra queue wait so với task execution time: độ trễ đến từ hàng đợi hay I/O?
3. Xem `activeCount`, `queueSize`, `maxPoolSize` và rejection.
4. Kiểm tra DB/HTTP connection pools, CPU throttling và memory.
5. Xem các tác vụ tiếp tục sau khi client đã timeout/disconnect.
6. Kiểm tra retry storm từ clients; có idempotency và backoff?
7. Kiểm tra Servlet REQUEST/ASYNC dispatch, filter/interceptor lifecycles, correlation ID.
8. So sánh môi trường local, HTTP integration và gateway thực; MockMvc không bao phủ hết container.
9. Quyết định sửa policy: fail fast, tăng phù hợp worker, giảm queue, tối ưu downstream hoặc chuyển công việc dài sang background jobs.

## 21. Sai lầm phổ biến

1. **Không cấu hình MVC executor** rồi chạy `Callable` tải cao mà không giới hạn concurrency.
2. **Đặt maxPoolSize lớn và để queue không giới hạn**; max không giúp tăng nhanh như nghĩ.
3. **Dùng `CallerRunsPolicy`** và khẳng định servlet thread đã được giải phóng.
4. **Dùng `DiscardPolicy`** khiến request không bao giờ nhận kết quả.
5. **Cho rằng `@EnableAsync` quyết định tất cả MVC Callable processing**.
6. **Coi `DeferredResult` tự tạo worker**; nó chỉ giữ kết quả chờ producer.
7. **Coi MVC timeout tự hủy mọi @Async/background job**.
8. **Coi 503 là bằng chứng DB transaction chưa commit**.
9. **Không đặt timeout outbound HTTP/DB**; worker vẫn chờ rất lâu sau HTTP timeout.
10. **Không đếm queue wait trong end-to-end latency budget**.
11. **Giữ pending DeferredResult hoặc SSE vô hạn**, leak request references.
12. **Không xử lý race khi setResult sau timeout**.
13. **Dùng streaming mà không kiểm soát buffers/backpressure**.
14. **Chạy blocking IO trong onTimeout/onCompletion** trên container thread.
15. **Không cấu hình filter asyncSupported và DispatcherType.ASYNC** đúng.
16. **Đếm metrics hai lần** do REQUEST và ASYNC dispatch.
17. **Mang ThreadLocal request context nguyên trạng qua thread pool**.
18. **Dùng queue lớn để che overload** làm latency và retry storm tăng.
19. **Bắt TaskRejectedException thành 200** hoặc nuốt exception.
20. **Không test bằng asyncDispatch** và tưởng test đã xác minh body.
21. **Dựa MockMvc để xác nhận gateway/Servlet timeout**.
22. **Không tách long jobs khỏi endpoint tương tác ngắn**.
23. **Không giới hạn rate/concurrency theo user/tenant**.
24. **Scale workers vượt DB connection pool** và làm hệ thống chậm hơn.
25. **Không có load test có giới hạn** trong staging để kiểm timeout/rejection.
26. **Vội bật virtual threads** rồi nghĩ DB/socket/memory limits biến mất.
27. **Không dọn producer khi client disconnect**, gây tài nguyên tồn dư.
28. **Không thiết kế graceful shutdown** khi pod đang giữ async requests.

## 22. Checklist triển khai production

### Executor

- [ ] Spring MVC `configureAsyncSupport` dùng đúng `AsyncTaskExecutor` có capacity hữu hạn?
- [ ] Core/max/queue phản ánh loại workload và downstream bottleneck?
- [ ] Đã hiểu queue thường đầy trước khi mở workers vượt core?
- [ ] Rejection có status/error contract đúng, không silently drop?
- [ ] Không vô tình dùng CallerRuns làm block Servlet request threads?
- [ ] Executor cho streams/exports/jobs dài đã được tách nếu cần?
- [ ] Có metrics active/queue/rejected và alerting?
- [ ] Shutdown/draining/termination budget được đặt có chủ đích?

### Timeout

- [ ] MVC default async timeout được xác định rõ và test qua container thật?
- [ ] Request đặc thù dùng `WebAsyncTask`/`DeferredResult` timeout đúng?
- [ ] Gateway/client/downstream timeouts cùng phù hợp latency budget?
- [ ] Timeout/cancellation và transaction side effects được thiết kế riêng?
- [ ] Có idempotency/retry policy cho unsafe writes?
- [ ] Pending DeferredResult/SSE có capacity và cleanup?
- [ ] Callback không block và response đã commit được xử lý an toàn?

### Kiểm thử/vận hành

- [ ] MockMvc kiểm tra `asyncStarted` và `asyncDispatch`?
- [ ] Integration test có trường hợp timeout, reject, disconnect?
- [ ] Có load test giới hạn để đo queue wait/p95/p99 và recovery?
- [ ] Filter/Servlet `ASYNC` registration đúng?
- [ ] SecurityContext, Locale, MDC, tracing không rò giữa threads?
- [ ] Có cách chuyển công việc dài sang `202 Accepted + job status` thay vì request mở lâu?

## 23. Câu hỏi tự kiểm tra

**1.** MVC `Callable` chạy trên thread nào?

**Đáp án:** Thường trên MVC `AsyncTaskExecutor` sau khi servlet request thread ban đầu đã rời dispatch. Servlet async request vẫn giữ response mở.

**2.** `setTaskExecutor` có tự chọn executor cho mọi `@Async` method không?

**Đáp án:** Không. `@Async` là cơ chế riêng; muốn dùng chung phải cấu hình có chủ đích.

**3.** Vì sao maxPoolSize=50 mà pool thực tế chỉ có hai workers?

**Đáp án:** Nếu core=2 và queue chưa đầy, tasks thường tiếp tục vào queue; pool chưa cần tăng lên max.

**4.** Timeout callback của WebAsyncTask có bảo đảm worker đã dừng không?

**Đáp án:** Không. Nó xử lý response lifecycle, không nhất thiết hủy I/O hoặc rollback side effects.

**5.** DeferredResult có tự chạy business work trên MVC executor không?

**Đáp án:** Không. Result được cung cấp từ publisher/event/job của ứng dụng.

**6.** Vì sao không dùng DiscardPolicy mặc định cho HTTP Callable?

**Đáp án:** Task có thể mất âm thầm khiến HTTP request treo đến timeout mà không tạo response.

**7.** CallerRunsPolicy có thể làm gì dưới overload?

**Đáp án:** Chạy task trên thread submit, có thể chính là Servlet thread, làm nghẽn request handling.

**8.** Cần gì trong test để xác minh body cuối của Callable?

**Đáp án:** `request().asyncStarted()`, lấy `MvcResult`, rồi `mvc.perform(asyncDispatch(result))`.

**9.** Có thể dùng MockMvc để khẳng định Servlet container timeout 400ms không?

**Đáp án:** Không đầy đủ. Cần HTTP integration test qua server thật, xét gateway và scheduler.

**10.** Vì sao 503 sau payment request cần idempotency?

**Đáp án:** Work cũ có thể vẫn commit sau timeout; client retry không có idempotency có thể tạo giao dịch trùng.

**11.** Nếu pool có 4 workers và queue 8, số HTTP connections tối đa là 12?

**Đáp án:** Không. Đây chỉ là năng lực task của pool; pending async requests, DeferredResult/SSE và connections vẫn có limits riêng.

**12.** Khi việc chạy nhiều phút, nên giữ WebAsyncTask 5 phút hay chuyển sang job?

**Đáp án:** Thường chuyển sang job queue với `202 Accepted` và endpoint hỏi trạng thái để tách lifetime công việc khỏi HTTP connection.

## 24. Tài liệu tham khảo

- Spring Framework 6.2 — Asynchronous Requests và `configureAsyncSupport`: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html
- Spring Framework — `AsyncSupportConfigurer` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/web/servlet/config/annotation/AsyncSupportConfigurer.html
- Spring Framework — `WebAsyncTask` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/web/context/request/async/WebAsyncTask.html
- Spring Framework — `DeferredResult` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/web/context/request/async/DeferredResult.html
- Spring Framework — `ThreadPoolTaskExecutor` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Spring Framework — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html
- Spring Framework — MockMvc Async Requests: https://docs.spring.io/spring-framework/reference/6.2/testing/spring-mvc-test-framework/async-requests.html
- Spring Security — MVC Async Integration: https://docs.spring.io/spring-security/reference/servlet/integrations/mvc.html
- Java 17 — ThreadPoolExecutor: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
- Java 17 — ArrayBlockingQueue: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html
- Jakarta Servlet specification: https://jakarta.ee/specifications/servlet/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-async-processing-004">Luồng dispatch ASYNC và xử lý kết quả hoặc ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Luồng dispatch ASYNC và xử lý kết quả hoặc ngoại lệ trong Spring MVC

## 1. Mục tiêu: hiểu đúng hai lượt xử lý một HTTP request

Trong ứng dụng Servlet truyền thống, một HTTP request thường chiếm một servlet container thread cho tới khi controller trả kết quả và response hoàn tất. Nhưng một số công việc phải đợi I/O, event từ hệ thống khác hoặc tác vụ lâu hơn bình thường. Spring MVC có **Servlet asynchronous request processing**: trả lại servlet thread trước khi có kết quả, giữ HTTP response mở, rồi tiếp tục xử lý khi kết quả sẵn sàng.

Ví dụ endpoint dùng `Callable`:

~~~java
@GetMapping("/report")
public Callable<ResponseEntity<String>> report() {
    return () -> ResponseEntity.ok("ready");
}
~~~

Khi controller **trả về đối tượng `Callable`**, chuỗi `"ready"` **chưa phải** HTTP response cuối tại thời điểm đó. Spring MVC:
1. Đưa request vào asynchronous mode qua Servlet async API.
2. Chạy `Callable` trên `AsyncTaskExecutor`.
3. Cho servlet container thread đang phục vụ request trở lại pool trong lúc chờ.
4. Khi task xong, **dispatch request lần nữa với `DispatcherType.ASYNC`**.
5. `DispatcherServlet` xử lý **kết quả đã được lưu**, không gọi lại method controller để chạy lại task.
6. Resolve return value hoặc exception, ghi response và kết thúc async request.

**Quan trọng:** một HTTP request có thể có nhiều pha/dispatch nội bộ, nhưng client vẫn thấy **một HTTP response**. `ASYNC` là loại Servlet dispatch; nó **không** có nghĩa client gửi một HTTP method `ASYNC`.

### Sau bài này, bạn sẽ làm được

- Vẽ thứ tự `REQUEST` → `startAsync` → worker/event → `ASYNC` → response.
- Phân biệt Servlet container thread với thread xử lý `Callable` hoặc nơi gọi `DeferredResult.setResult`.
- Dùng đúng `Callable<T>`, `DeferredResult<T>` và `WebAsyncTask<T>` cho một kết quả bất đồng bộ.
- Hiểu kết quả thành công và lỗi đi qua `HandlerMethodReturnValueHandler`, `HttpMessageConverter`, `HandlerExceptionResolver` / `@ExceptionHandler`.
- Phân biệt exception từ worker với timeout, client disconnect và exception sau khi response đã commit.
- Hiểu `AsyncHandlerInterceptor`, filter dispatcher types, Spring Security context, cleanup và cấu hình executor/timeout.
- Viết ví dụ Java 17+ Spring Boot 3.3.5 và MockMvc với `request().asyncStarted()`, `asyncDispatch(...)` và assertions HTTP cuối.
- Biết điểm khác giữa MVC async, Java `@Async` và Spring WebFlux/reactive processing.

Bài tập trung **Spring MVC Servlet stack**; không diễn giải cơ chế này như WebFlux. Các tên API minh họa phù hợp Spring Framework 6.1.x trong Spring Boot 3.3.5.

## 2. Vì sao trả `Callable` không đồng nghĩa trả response ngay?

Nhìn vào hai controller:

~~~java
@GetMapping("/sync")
public String sync() {
    return slowService.load();
}

@GetMapping("/async")
public Callable<String> async() {
    return () -> slowService.load();
}
~~~

Đây là hai fragments minh họa (chưa có class và bean `slowService`). `sync` chờ `load()` hoàn thành trên servlet request thread. `async` giao việc cho executor; servlet thread có thể quay lại phục vụ request khác khi tác vụ đang chạy.

Tuy nhiên, **tác vụ I/O bên trong Callable vẫn có thể blocking** và chiếm một **worker thread**. MVC async không làm JDBC/HTTP blocking call tự trở thành non-blocking. Lợi ích là tránh chiếm container threads trong thời gian đợi; chi phí là thread pool, queue, context propagation và một lượt dispatch nữa.

Nếu gọi `Thread.sleep` hoặc REST client đồng bộ 10 giây trong Callable, bạn vẫn dùng một thread ở executor trong 10 giây. Chỉ thay nơi chờ, không “loại bỏ” thời gian chờ. Với hiệu năng, cần đo số threads, hàng đợi và latency trước/sau.

## 3. Chu trình Servlet: `REQUEST`, async mode và `ASYNC`

### 3.1. Sơ đồ theo các giai đoạn

~~~text
Client           Servlet container          Spring MVC             Async executor/event
  |                      |                       |                          |
  | GET /api/async/...   |                       |                          |
  |--------------------->|  DispatcherType.REQUEST                         |
  |                      |-- Filters ---------->|                          |
  |                      |                       | HandlerMapping            |
  |                      |                       | Controller returns        |
  |                      |                       | Callable/DeferredResult  |
  |                      |                       | startAsync()             |
  |                      |                       |------------------------->| Callable worker
  |                      |<-- Filter/Servlet exit|                          |
  |                      |  request thread free  |                          |
  |                      |  response remains open|                          |
  |                      |                       |                 compute result
  |                      |<-----------------------------------------------  |
  |                      |  async dispatch       |                          |
  |                      |  DispatcherType.ASYNC |                          |
  |                      |-- Filters* ---------->| DispatcherServlet again |
  |                      |                       | consume saved result     |
  |                      |                       | serialize OR resolve err |
  |<---------------------| response completes    |                          |
~~~

Dấu `Filters*` nhắc rằng **chỉ những filters được map cho dispatcher type `ASYNC`** và phù hợp quy tắc dispatch mới tham gia lượt sau. Một `OncePerRequestFilter` còn có logic nên bỏ qua async dispatch hay không. Đừng giả định mọi filter sẽ chạy hai lần, hoặc không filter nào chạy lần thứ hai.

### 3.2. Servlet API bên dưới

Ở mức Servlet, `HttpServletRequest.startAsync()` trả về `AsyncContext` và đặt request ở chế độ async (nếu servlet/filter chain cho phép). Tác dụng chính:
- Servlet có thể kết thúc lần dispatch ban đầu.
- Response vẫn còn mở cho kết quả sau.
- Ứng dụng/container có thể dispatch lại hoặc complete.
- Async request có thể timeout và nhận notifications `AsyncListener`.

Spring MVC quản lý những thao tác này thông qua tầng hạ tầng như `WebAsyncManager`, `AsyncWebRequest` và argument/return-value processing. **Không tự gọi `request.startAsync()` trong controller đã trả `Callable`/`DeferredResult`**: nếu trộn hai cách quản lý độc lập, dễ phát sinh double-dispatch, race condition hoặc response chưa hoàn tất.

### 3.3. Một request hay hai request?

Có **một** request HTTP từ client. Trong server, có hai lần Servlet dispatch:
- `REQUEST`: nhận request, chọn handler, controller trả async handle.
- `ASYNC`: tiếp tục sau khi có kết quả hoặc lỗi.

Khi dispatch ASYNC, Spring MVC vẫn tìm được handler mapping tương ứng để tiếp tục pipeline, nhưng **không gọi lại controller method**; kết quả đã được `WebAsyncManager` lưu để return-value/exception resolvers xử lý. Vì vậy không ghi nghiệp vụ vào một MVC interceptor `preHandle` với giả định nó chỉ chạy đúng một lần mỗi request: cần xét async redispatch.

### 3.4. Phân biệt Servlet dispatch và HTTP redirect/forward

| Cơ chế | Client có gửi request mới? | Ý nghĩa |
| --- | --- | --- |
| `DispatcherType.REQUEST` | Client gửi request ban đầu | Xử lý HTTP request bình thường |
| `DispatcherType.ASYNC` | Không | Container tiếp tục cùng logical HTTP request sau async completion |
| `DispatcherType.FORWARD` | Không phải request HTTP mới | Servlet forward nội bộ |
| HTTP `302 Location` | Browser/client thường gửi request mới khi follow redirect | Một HTTP round-trip khác |
| `DispatcherType.ERROR` | Không nhất thiết | Error dispatch của container theo cấu hình |

`ASYNC` dispatch không phải một redirect, không phải server push và không phải message queue acknowledgement. Nếu muốn client quay lại hỏi trạng thái, đó là thiết kế **202 Accepted + status endpoint** khác, không phải nội bộ MVC async dispatch.

## 4. `Callable<T>`: Spring quản lý worker và resume kết quả

### 4.1. Luồng xử lý chính

Khi handler trả `Callable<T>`, Spring:
1. Chấp nhận return value là một asynchronous computation.
2. Đưa request vào async mode.
3. Submit `Callable` vào `AsyncTaskExecutor` do MVC cấu hình.
4. Cho `DispatcherServlet` và những filters trong lượt ban đầu thoát.
5. Worker chạy `Callable.call()` và thu kết quả hoặc `Throwable`.
6. Lưu kết quả trong async manager, yêu cầu container dispatch ASYNC.
7. Với result, xử lý return value giống controller trả đối tượng đó trực tiếp.
8. Với exception, đi qua Spring MVC exception handling.

Ví dụ:

~~~java
@GetMapping("/callable")
public Callable<ResponseEntity<String>> callable() {
    return () -> ResponseEntity.ok("worker complete");
}
~~~

Spring MVC có thể serialize `"worker complete"` thành response sau dispatch. **ResponseEntity không được ghi hoàn tất ngay khi controller trả Callable**.

### 4.2. Kết quả không phụ thuộc định danh thread

Có thể thấy ba thread khác nhau:
- `T1`: container thread xử lý REQUEST.
- `T2`: executor worker chạy Callable.
- `T3`: container thread xử lý ASYNC.

Không yêu cầu T1 ≠ T3 trong mọi runtime: thread pool có thể tái sử dụng cùng thread. Bản chất là **ngữ cảnh dispatch được chia theo pha**, không phải nhất thiết phải có ba tên thread khác nhau. Không dùng thread name/order làm API contract hoặc assertion cứng.

### 4.3. Callable ném exception ở thread khác có bị mất không?

Không, nếu exception xảy ra **bên trong Callable do Spring MVC quản lý**, Spring giữ nó như kết quả async và dispatch lần nữa. Ở lượt ASYNC, nó đi qua `HandlerExceptionResolver` và `@ExceptionHandler` tương tự exception trong synchronous controller.

~~~java
@GetMapping("/callable/fail")
public Callable<String> fail() {
    return () -> {
        throw new IllegalStateException("simulated");
    };
}
~~~

Đây là fragment chỉ để diễn giải; code production không nên để `IllegalStateException` nội bộ lộ nguyên văn tới client. Một `@RestControllerAdvice` map exception public đã biết sang status/body an toàn; lỗi không đoán trước trở thành 500 và được log bảo vệ phía server.

### 4.4. Khác với tạo thread thủ công

Không viết:

~~~java
@GetMapping("/bad")
public String bad() {
    new Thread(() -> expensiveWork()).start();
    return "done";
}
~~~

Code trên **trả HTTP response trước khi biết công việc thành công hay thất bại**, không phải MVC async response. Nó còn tạo thread không kiểm soát và làm exception rơi vào một nơi khác. Nếu mục tiêu là job dài thực sự, dùng task queue/job table và endpoint trả 202 + job ID, không giữ HTTP connection mở vô hạn.

## 5. `DeferredResult<T>`: một kết quả đến từ nguồn bên ngoài

Với `Callable`, Spring MVC biết computation để submit. Với `DeferredResult`, controller trả một **holder** cho kết quả sẽ được thiết lập sau bởi ứng dụng: message listener, callback từ service, completion của workflow, scheduler hoặc một worker do bạn điều phối.

~~~java
@GetMapping("/deferred")
public DeferredResult<String> deferred() {
    DeferredResult<String> result = new DeferredResult<>(5_000L);

    // Nguồn ngoài hoặc executor sẽ gọi sau:
    scheduleCallback(() -> result.setResult("event arrived"));

    return result;
}
~~~

`scheduleCallback` là tên hàm minh họa, không nằm trong Servlet/Spring API. Ví dụ đầy đủ có executor thật ở mục 10.

### 5.1. Khi gọi `setResult(...)`

Nếu gọi:

~~~java
boolean accepted = result.setResult(ResponseEntity.ok(body));
~~~

Spring lưu giá trị nếu kết quả chưa được quyết định, sau đó thực hiện async dispatch để xử lý nó như return value bình thường của handler. Phương thức trả boolean để biểu thị có chấp nhận thiết lập kết quả đó hay không; **không suy ra response đã gửi thành công cho browser**. Kết quả có thể bị từ chối nếu request đã timeout/completed hoặc một result khác thắng race.

### 5.2. Khi gọi `setErrorResult(...)`

~~~java
result.setErrorResult(new ResourceNotFoundException("record missing"));
~~~

Nếu object là exception phù hợp, lượt ASYNC xử lý nó qua exception resolvers. `setErrorResult` cũng có thể nhận non-exception Object làm alternate result theo contract của Spring, nhưng nên dùng exception/domain result rõ ràng, tránh nhầm lẫn với response thành công.

**Một exception ném trong `Runnable` riêng nhưng không gọi `setErrorResult` sẽ không tự động trở thành lỗi của request.** Ví dụ:

~~~java
executor.execute(() -> {
    throw new RuntimeException("lost to request");
});
~~~

Nếu handler đã trả `DeferredResult`, exception của runnable này không được MVC liên kết tự động với holder. Cần bắt exception trong callback rồi gọi `setErrorResult`:

~~~java
executor.execute(() -> {
    try {
        String data = externalService.load();
        result.setResult(data);
    } catch (Exception ex) {
        result.setErrorResult(ex);
    }
});
~~~

Các dòng trên là fragment có biến executor/service/result; demo đầy đủ sử dụng nó ở dưới. Phân biệt này là **nguyên nhân phổ biến khiến request DeferredResult treo đến timeout trong khi worker logs đã báo lỗi**.

### 5.3. Timeout và completion callbacks

`DeferredResult` cung cấp:
- `onTimeout(Runnable)`: callback khi async request timeout.
- `onError(Consumer<Throwable>)`: callback khi có lỗi ở request/async processing.
- `onCompletion(Runnable)`: callback khi async lifecycle kết thúc, dù thành công/thất bại.

Một timeout callback có thể cố `setErrorResult(...)` hoặc chọn alternate result; nhưng callbacks có race với producer. Sau completion, nên dọn registry/pending entries, hủy đăng ký listener và tránh memory leaks. **Không dùng `onCompletion` như bằng chứng business transaction thành công**.

## 6. `WebAsyncTask<T>`: Callable với timeout/executor tùy biến

`WebAsyncTask<T>` bọc `Callable<T>` và cho phép timeout, executor riêng, xử lý timeout/error/completion.

~~~java
@GetMapping("/web-task")
public WebAsyncTask<ResponseEntity<String>> webTask() {
    WebAsyncTask<ResponseEntity<String>> task =
            new WebAsyncTask<>(3_000L,
                    () -> ResponseEntity.ok("task done"));

    task.onTimeout(() -> ResponseEntity.status(503).body("timed out"));
    task.onCompletion(() -> {
        // Tài nguyên gắn với request có thể được dọn tại đây.
    });
    return task;
}
~~~

Đoạn fragment minh họa, cần các imports Spring. `3_000L` trong constructor là **milliseconds**. Callback timeout chạy trong container thread và phải xử lý rất nhanh, **không ngủ/chờ network** trong callback.

`WebAsyncTask.onError(Callable<T>)` cũng có thể cung cấp alternate result cho lỗi trong async request processing. Không nhầm `onError` này với mọi exception ném **bên trong worker Callable**: exception từ worker thường được Spring đưa thẳng qua MVC exception handling; thiết kế handler theo loại lỗi thực tế.

Timeout trên `WebAsyncTask` không phải guarantee cho việc chủ động dừng một JDBC query, HTTP request hay thread đang block. Cần per-dependency timeouts, cancellation/cooperative interrupt và bounded resources khi sử dụng thực.

## 7. Lượt `ASYNC` dispatch xử lý kết quả như thế nào?

### 7.1. Trường hợp thành công

~~~text
T1: DispatcherServlet --> Controller returns Callable<Payload>
T1: Spring starts async; servlet/filter chain exits
T2: Callable returns Payload
T2: WebAsyncManager stores result; asks AsyncContext.dispatch()
T3: DispatcherServlet on ASYNC
T3: stored Payload --> return value handling --> HttpMessageConverter
T3: response status/headers/body; complete
~~~

Nếu `Payload` là Java record và controller `@RestController`, JSON converter (thường Jackson) serialize nó tại lượt xử lý result. Với `ResponseEntity<Payload>`, status và headers bên trong object được MVC dùng ở pha hoàn tất.

### 7.2. Trường hợp exception

~~~text
T1: Controller returns Callable
T2: Callable throws ResourceNotFoundException
T2: Spring saves exception as async result
T3: ASYNC dispatch
T3: HandlerExceptionResolver / @RestControllerAdvice
T3: HTTP 404 + safe ProblemDetail JSON
~~~

Đối với `DeferredResult`:

~~~text
T1: Controller returns DeferredResult
E1: External callback calls setErrorResult(exception)
T3: ASYNC dispatch
T3: @ExceptionHandler can map the exception
~~~

Điểm mấu chốt là **exception phải được đưa vào quy trình Spring MVC**. Nếu bạn tạo một thread tùy ý rồi exception bị uncaught, MVC không thể biết nên gửi 404/500 cho request cụ thể nào. Hãy kết nối qua `setErrorResult` hoặc một loại return value mà Spring hiểu.

### 7.3. Sự tham gia của `@RestControllerAdvice`

`@ExceptionHandler` trong controller hoặc `@RestControllerAdvice` vẫn có thể áp dụng sau ASYNC dispatch. Nếu handler chỉ trả 500 default cho exception chưa biết, đừng đưa stack trace, raw SQL message, token hay path server vào response. Với một exception nghiệp vụ public, đặt `code` máy đọc được và HTTP status phù hợp.

Spring MVC 6.x còn có `ProblemDetail`/`ErrorResponse` và `ResponseEntityExceptionHandler`. Khi kế thừa `ResponseEntityExceptionHandler`, hãy override hooks dành cho built-in exceptions thay vì thêm @ExceptionHandler trùng loại với superclass, tránh ambiguous mapping.

### 7.4. Sau khi response đã commit

Nếu đã stream bytes xuống client, server **không còn tự do đổi HTTP status/body** như trước. `ResponseBodyEmitter`/`SseEmitter` có cơ chế complete/error riêng; lỗi I/O do client disconnect không phải lúc nào cũng gửi được một body 500 đẹp. Bài này tập trung **một kết quả async** qua Callable/DeferredResult/WebAsyncTask, không tự hứa advice xử lý được mọi lỗi xảy ra sau khi body đã gửi.

## 8. Timeout, lỗi, client disconnect và race conditions

### 8.1. Timeout mặc định

Không tự đoán timeout là “mặc định 30 giây” trong mọi deployment. Default Servlet async timeout phụ thuộc container/configuration và có thể được Spring MVC tùy chỉnh. Với `WebMvcConfigurer.configureAsyncSupport`:

~~~java
@Override
public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    configurer.setDefaultTimeout(10_000L);
}
~~~

Đơn vị là milliseconds. `DeferredResult<>(5_000L)` hoặc `WebAsyncTask<>(3_000L, callable)` có thể đặt timeout riêng cho request; một số load balancer/gateway/client timeout còn ngắn hơn. Hiệu lực timeout của API phụ thuộc **nhiều tầng**, không chỉ Spring.

### 8.2. Nếu timeout mà không có handler tùy chỉnh

Spring có `AsyncRequestTimeoutException`, mặc định thường dẫn tới **503 Service Unavailable** qua MVC exception infrastructure trong Spring 6.1. Một callback timeout tùy biến có thể quyết định result/status khác, ví dụ 504 cho gateway-like use case nếu đúng semantics. Đừng khẳng định mọi timeout chắc chắn 504.

Dựa vào `@RestControllerAdvice` và `onTimeout` để tạo error schema ổn định; phải test thực tế và tránh trả 200 với body mô tả lỗi nếu client cần status hợp lệ.

### 8.3. Producer thắng hay timeout thắng?

Giả sử executor vừa hoàn thành khi timeout xảy ra:

~~~text
T2: result.setResult(payload)        ↘
                                      một kết quả được chấp nhận
T3: timeout callback setErrorResult ↗
~~~

`DeferredResult` giúp điều phối một terminal result, nhưng callback ngoài vẫn có thể đang chạy hoặc chuẩn bị làm side effect. Kiểm giá trị boolean trả về của `setResult`/`setErrorResult` khi cần instrumentation, dọn pending work, không ghi business event “gửi thành công” chỉ dựa vào điều đó.

### 8.4. Client đã đóng kết nối

Servlet async nói chung không có một sự kiện “client đóng tab” luôn được gửi ngay và chính xác theo mọi trường hợp. Với streaming, write có thể ném `IOException` khi có lỗi kết nối; đối với single result, server đôi lúc chỉ phát hiện khi serialize/write cuối. Đừng giữ work vô hạn dựa trên hy vọng client luôn kết nối.

Nên có bounded timeout ở service, HTTP client/database và queue; cancellation khi có thể; cleanup trong completion callback; idempotency cho side effects. Không trả thông tin nhạy cảm trong error log lúc client disconnect.

### 8.5. Executor bị đầy

Async MVC giải phóng container thread nhưng **không miễn phí năng lực thực thi**. Nếu pool có 8 threads nhưng liên tục nhận hàng nghìn tasks blocking, queue sẽ lớn, latency tăng và request timeout. Có thể dẫn đến task rejection khi vượt ngưỡng.

Dùng bounded executor, overload policy, metrics, per-dependency timeout và backpressure/admission control ở app/gateway. Không dùng pool vô hạn trong production; không tạo một thread mới cho mỗi request.

## 9. `AsyncHandlerInterceptor` và các callback của MVC

`HandlerInterceptor` bình thường có `preHandle`, `postHandle` và `afterCompletion`. Khi controller bắt đầu async:
- Ở **REQUEST ban đầu**, `preHandle` thường chạy.
- Khi concurrent handling bắt đầu, một `AsyncHandlerInterceptor` nhận `afterConcurrentHandlingStarted` **thay cho** `postHandle` và `afterCompletion` trong lượt đầu.
- Ở **ASYNC dispatch**, interceptor có thể đi qua lifecycle thêm lần nữa; `preHandle` có thể được gọi lại rồi `postHandle/afterCompletion` chạy khi hoàn tất tùy đường xử lý.

Ví dụ fragment:

~~~java
public final class TraceAsyncInterceptor implements AsyncHandlerInterceptor {

    @Override
    public void afterConcurrentHandlingStarted(
            HttpServletRequest request, HttpServletResponse response,
            Object handler) {
        // Dọn ThreadLocal của REQUEST thread nếu cần.
    }
}
~~~

Fragment này cần imports `AsyncHandlerInterceptor` và Servlet types; không đăng ký vào demo runnable vì không cần thay request ở ví dụ chính.

**Lỗi thường gặp:** code bắt đầu timer trong `preHandle` và tăng pageview trong đó, sau ASYNC redispatch lại tăng thêm lần nữa. Nếu business side effect chỉ được phép chạy một lần, cần đánh dấu trên request scope/kiểm dispatcher type hoặc đặt side effect tại nơi phù hợp, không tin interceptor luôn gọi đúng một lần.

### 9.1. Filters và `DispatcherType.ASYNC`

Servlet filters có mapping dispatcher types; trong Boot, khi đăng ký `FilterRegistrationBean` có thể chỉ rõ:

~~~java
registration.setDispatcherTypes(
        DispatcherType.REQUEST, DispatcherType.ASYNC);
registration.setAsyncSupported(true);
~~~

Đây là fragment cần `registration` và import `jakarta.servlet.DispatcherType`. Nếu servlet/filter không hỗ trợ async, runtime có thể gặp `IllegalStateException` khi bắt đầu asynchronous processing.

Với `OncePerRequestFilter`, xem `shouldNotFilterAsyncDispatch()` và `isAsyncDispatch(request)`; nó có cơ chế tránh chạy nhiều lần tùy dispatcher type và override. Không mặc định tự gọi filter custom trên mọi ASYNC dispatch chỉ vì class có tên “Once”.

### 9.2. Filter chain và Spring Security

Spring Security có tích hợp `WebAsyncManagerIntegrationFilter` giúp xử lý `SecurityContext` khi dùng Spring MVC `Callable`. Tuy vậy, **custom threads**, `DeferredResult` được hoàn thành từ message listener và nhiều `CompletableFuture` callbacks **không tự luôn có cùng `SecurityContextHolder`**, MDC hoặc transaction context như container request thread.

Nếu worker cần principal/tenant, nên capture **immutable, đã xác thực** data từ controller/service và truyền qua parameter an toàn, hoặc dùng `DelegatingSecurityContextExecutor`/context propagation theo framework/config cụ thể. Không giữ nguyên đối tượng mutable `HttpServletRequest` trong worker để đọc user/session ở thời điểm tùy ý. Không tự assume `@Transactional` context sẽ đi theo thread khác; transaction thường thread-bound và cần ranh giới service được gọi đúng trong worker.

### 9.3. MDC, tracing và correlation

`ThreadLocal`/SLF4J MDC thường không tự lan qua thread pools. Nếu log theo trace/request ID, dùng tracing context propagation hoặc task decorator phù hợp, đảm bảo clear context cuối task để tránh dữ liệu request này còn lại trên thread tái sử dụng ở request khác.

Không đưa raw cookies/Authorization header/token vào logs để “sửa async”. Một trace ID vô hại và task ID riêng giúp liên kết T1/T2/T3 tốt hơn log tên thread.

## 10. Ví dụ chạy được: Spring Boot 3.3.5, Callable, DeferredResult, WebAsyncTask

Demo minh họa một kết quả async có thể là **thành công**, **không tìm thấy** hoặc **timeout**:
- `GET /api/async/callable/{id}`: `Callable<ResponseEntity<Payload>>` được chạy trên MVC executor.
- `GET /api/async/deferred/{id}`: `DeferredResult<ResponseEntity<Payload>>` được kết thúc bằng executor callback; khi lỗi gọi `setErrorResult`.
- `GET /api/async/web-task/{id}`: `WebAsyncTask<ResponseEntity<Payload>>` có timeout riêng.
- `@RestControllerAdvice`: map `MissingItemException` sang 404 và `WorkTimedOutException` sang 503 bằng `ProblemDetail`.
- MockMvc: assert `asyncStarted`, gọi `asyncDispatch` rồi kiểm response cuối.

Không có database, Spring Security hoặc network client thực; kết quả là **fixture deterministic**, không tuyên bố worker thật sự làm I/O lâu. Mục tiêu là mô tả lifecycle và exception propagation. Test không cố gây timeout bằng `Thread.sleep` vì sẽ khiến CI flaky.

~~~text
mvc-async-dispatch-demo/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/mvcasync/
    │   ├── AsyncApplication.java
    │   ├── config/AsyncMvcConfig.java
    │   └── web/
    │       ├── AsyncController.java
    │       ├── AsyncErrorAdvice.java
    │       ├── MissingItemException.java
    │       └── WorkTimedOutException.java
    └── test/java/com/example/mvcasync/web/
        └── AsyncControllerTest.java
~~~

### 10.1. Gradle build

`settings.gradle`:

~~~groovy
rootProject.name = 'mvc-async-dispatch-demo'
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

`spring-boot-starter-web` cung cấp Spring MVC, Servlet container (Tomcat mặc định với Boot starter Web) và JSON converter Jackson. `spring-boot-starter-test` cung cấp JUnit 5, MockMvc. Không cần `spring-boot-starter-webflux` chỉ để dùng Callable/DeferredResult của MVC.

### 10.2. Main class

`src/main/java/com/example/mvcasync/AsyncApplication.java`:

~~~java
package com.example.mvcasync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AsyncApplication {
    public static void main(String[] args) {
        SpringApplication.run(AsyncApplication.class, args);
    }
}
~~~

### 10.3. Cấu hình executor có giới hạn và MVC timeout

`src/main/java/com/example/mvcasync/config/AsyncMvcConfig.java`:

~~~java
package com.example.mvcasync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AsyncMvcConfig implements WebMvcConfigurer {

    @Bean("mvcAsyncExecutor")
    public ThreadPoolTaskExecutor mvcAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mvc-worker-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(64);
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // @Configuration sử dụng Spring-managed @Bean method.
        configurer.setTaskExecutor(mvcAsyncExecutor());
        configurer.setDefaultTimeout(10_000L);
    }
}
~~~

Ở `@Configuration` tiêu chuẩn (mặc định proxy bean methods được bật), gọi `mvcAsyncExecutor()` trong class nhận managed singleton bean; Spring quản lý initialization/shutdown. `ThreadPoolTaskExecutor` được dùng cả cho `Callable` của MVC và được injection vào controller cho nguồn hoàn thành `DeferredResult`. `setDefaultTimeout` quy định timeout async mặc định **10.000 milliseconds**; trong code dưới `DeferredResult`/`WebAsyncTask` có timeout riêng.

**Production:** sizing 4/8/64 chỉ là demo. Chọn pool/queue theo tải thực, workload blocking hay CPU-bound, thread memory, queue-wait timeout, overload behavior, metrics và shutdown. Timeout đợi queue cũng được tính vào thời gian chờ của request trong thực tế; không đo mỗi thời gian `call()`.

### 10.4. Hai exception public

`src/main/java/com/example/mvcasync/web/MissingItemException.java`:

~~~java
package com.example.mvcasync.web;

public class MissingItemException extends RuntimeException {

    public MissingItemException(long id) {
        super("Item " + id + " does not exist");
    }
}
~~~

`src/main/java/com/example/mvcasync/web/WorkTimedOutException.java`:

~~~java
package com.example.mvcasync.web;

public class WorkTimedOutException extends RuntimeException {

    public WorkTimedOutException() {
        super("Async operation timed out");
    }
}
~~~

Ở ứng dụng thật, exception messages nội bộ không nên phản chiếu trực tiếp nếu chứa identifiers hoặc dữ liệu nhạy cảm. Advice sẽ trả safe detail thay vì `ex.getMessage()`.

### 10.5. Controller đủ ba kiểu kết quả

`src/main/java/com/example/mvcasync/web/AsyncController.java`:

~~~java
package com.example.mvcasync.web;

import java.util.concurrent.Callable;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.WebAsyncTask;

@RestController
@RequestMapping("/api/async")
public class AsyncController {

    public record Payload(long id, String state) {}

    private final AsyncTaskExecutor executor;

    public AsyncController(AsyncTaskExecutor mvcAsyncExecutor) {
        this.executor = mvcAsyncExecutor;
    }

    private ResponseEntity<Payload> find(long id) {
        if (id <= 0) {
            throw new MissingItemException(id);
        }
        return ResponseEntity.ok(new Payload(id, "ready"));
    }

    @GetMapping("/callable/{id}")
    public Callable<ResponseEntity<Payload>> callable(
            @PathVariable("id") long id) {
        return () -> find(id);
    }

    @GetMapping("/deferred/{id}")
    public DeferredResult<ResponseEntity<Payload>> deferred(
            @PathVariable("id") long id) {

        DeferredResult<ResponseEntity<Payload>> result =
                new DeferredResult<>(5_000L);

        result.onTimeout(() ->
                result.setErrorResult(new WorkTimedOutException()));

        result.onCompletion(() -> {
            // Không giữ kết quả trong queue/registry sau completion.
        });

        try {
            executor.execute(() -> {
                try {
                    result.setResult(find(id));
                } catch (Exception ex) {
                    result.setErrorResult(ex);
                }
            });
        } catch (RuntimeException rejected) {
            result.setErrorResult(rejected);
        }

        return result;
    }

    @GetMapping("/web-task/{id}")
    public WebAsyncTask<ResponseEntity<Payload>> webTask(
            @PathVariable("id") long id) {

        WebAsyncTask<ResponseEntity<Payload>> task =
                new WebAsyncTask<>(3_000L, () -> find(id));

        task.onTimeout(() -> {
            throw new WorkTimedOutException();
        });

        return task;
    }
}
~~~

**Giải thích và những điểm cần nhớ:**
- `find(id)` chỉ dựng fixture hoặc ném exception khi `id <= 0`; không thực hiện DB/network. Tất cả các ví dụ cuối cùng trả `Payload` giống nhau.
- `callable`: method controller **chạy trên REQUEST thread**, nhưng lambda `() -> find(id)` chạy trên executor do MVC submit. Exception trong lambda được Spring thu và đưa qua ASYNC dispatch.
- `deferred`: MVC **không tự submit công việc** của `DeferredResult`; controller gửi task qua injected executor. Khi thành công gọi `setResult`; khi lỗi trong task, **phải gọi `setErrorResult`**, nếu không MVC không biết exception của thread tùy biến.
- `deferred` timeout sau 5 giây theo cấu hình holder, callback đẩy `WorkTimedOutException` nếu chưa có result.
- `webTask` có timeout 3 giây, callback trả/đẩy alternate error. Exception ném từ callback timeout được Spring's async interception xử lý theo contract; trong deployment thực cần kiểm mapping/error branch riêng, không giả định callback có thể làm I/O dài.
- Catch quanh `executor.execute` chỉ nhằm minh họa việc task submission có thể bị từ chối; ở hệ thống thực, map task rejection thành error chính sách 503 với an toàn thông tin, không trả raw exception.

**Một tinh chỉnh trước khi dùng production:** `onTimeout` của `WebAsyncTask` có thể dùng `return` một alternate `ResponseEntity` phù hợp thay vì ném exception; điều này giảm sự phụ thuộc vào exception path trong callback. Nếu cần trả lỗi theo ProblemDetail như các nhánh khác, có thể đăng ký callback trả exception object khi dùng loại kết quả thích hợp hoặc viết resolver có kiểm thử đầy đủ. Bài demo chỉ test success/error từ `Callable`/`DeferredResult`, không mô phỏng timer thật.

### 10.6. Advice chuyển exception thành error response chuẩn

`src/main/java/com/example/mvcasync/web/AsyncErrorAdvice.java`:

~~~java
package com.example.mvcasync.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AsyncErrorAdvice {

    @ExceptionHandler(MissingItemException.class)
    public ResponseEntity<ProblemDetail> missing(
            MissingItemException ex) {

        ProblemDetail problem =
                ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Item not found");
        problem.setDetail("The requested item does not exist.");
        problem.setProperty("code", "ITEM_NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(WorkTimedOutException.class)
    public ResponseEntity<ProblemDetail> timeout(
            WorkTimedOutException ex) {

        ProblemDetail problem =
                ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setTitle("Operation timed out");
        problem.setDetail("The operation could not finish in time.");
        problem.setProperty("code", "ASYNC_TIMEOUT");
        return ResponseEntity.status(
                HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }
}
~~~

`ProblemDetail` là Spring 6 HTTP API dựa trên RFC 9457. Với Jackson support trong Spring MVC, `setProperty("code", ...)` thường trở thành extension field `code` ở top-level JSON.

HTTP 404/503 chỉ là demo's contract: không nên dùng 503 cho **mọi** failure. Network gateway timeout 504, upstream failure 502 hoặc overload 503 có semantics khác nhau; xác định dựa trên loại lỗi và nơi nó xảy ra. Không trả exception raw message ra JSON.

## 11. MockMvc: cần hai pha test, không chỉ `asyncStarted`

`MockMvc` không tự động xử lý container async dispatch cho bạn như một server đang chạy. Với handler bất đồng bộ, thứ tự kiểm thử chính xác là:
1. `mvc.perform(get(...))` lần đầu.
2. Assert `request().asyncStarted()` và lấy `MvcResult`.
3. `mvc.perform(asyncDispatch(first))` để hoàn tất xử lý kết quả trong test.
4. Assert **HTTP status/body cuối**.

### 11.1. Bộ kiểm thử runnable

`src/test/java/com/example/mvcasync/web/AsyncControllerTest.java`:

~~~java
package com.example.mvcasync.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AsyncControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void callableSuccessNeedsAsyncDispatch() throws Exception {
        MvcResult started = mvc.perform(
                        get("/api/async/callable/7"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.state").value("ready"));
    }

    @Test
    void callableExceptionIsHandledOnResume() throws Exception {
        MvcResult started = mvc.perform(
                        get("/api/async/callable/0"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deferredSuccessIsSerializedAfterResume() throws Exception {
        MvcResult started = mvc.perform(
                        get("/api/async/deferred/9"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.state").value("ready"));
    }

    @Test
    void deferredSetErrorResultGoesThroughAdvice() throws Exception {
        MvcResult started = mvc.perform(
                        get("/api/async/deferred/0"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void webAsyncTaskSuccessNeedsDispatch() throws Exception {
        MvcResult started = mvc.perform(
                        get("/api/async/web-task/11"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11));
    }
}
~~~

Chạy trong thư mục project có Gradle wrapper:

~~~bash
./gradlew test
~~~

Windows PowerShell/CMD:

~~~powershell
.\gradlew.bat test
~~~

**Đọc đúng mục đích test:** Spring MVC MockMvc support đợi async result khi dispatch, nhưng test không chứng minh network disconnect, Servlet container timeout, filter ASYNC registrations hoặc thread pool behavior dưới tải thật. Không assert tên thread cố định, không cần `Thread.sleep` để chứng minh dispatch. Cấu hình executor hiện tại bảo đảm thực thi non-blocking ở servlet REQUEST thread khi MVC trao việc cho worker; việc worker hoàn thành rất nhanh không thay nguyên tắc lifecycle.

### 11.2. Test sai điển hình

~~~java
mvc.perform(get("/api/async/callable/7"))
        .andExpect(status().isOk());
~~~

Đoạn test có thể chỉ nhìn **status ban đầu** trước khi JSON serialization hoàn tất, không xác minh body cuối hay error handling. Kể cả `status().isOk()` pass trước dispatch, nó **không chứng minh task thành công**. Luôn assert `request().asyncStarted()` và làm `asyncDispatch(...)` cho các dạng return value bất đồng bộ.

### 11.3. Khi dùng WebTestClient thay MockMvc

Spring MVC có cách dùng WebTestClient gắn với MockMvc; những abstraction test cao hơn có thể tự động xử lý async, nên không copy cơ học hai pha MockMvc sang mọi client. Nếu dùng HTTP client thực tới embedded Tomcat, client thường chỉ thấy response cuối, không có “API call asyncDispatch” phía client.

## 12. Quan sát HTTP trên server thực

Chạy:

~~~bash
./gradlew bootRun
~~~

Thành công:

~~~bash
curl -i 'http://localhost:8080/api/async/callable/7'
curl -i 'http://localhost:8080/api/async/deferred/9'
curl -i 'http://localhost:8080/api/async/web-task/11'
~~~

Dự kiến mỗi response có HTTP **200** và JSON như:

~~~json
{"id":7,"state":"ready"}
~~~

Ở deferred/9 thì `id=9`, web-task/11 thì `id=11`.

Thử exception:

~~~bash
curl -i 'http://localhost:8080/api/async/callable/0'
curl -i 'http://localhost:8080/api/async/deferred/0'
~~~

Dự kiến HTTP **404** với `ProblemDetail` và extension:

~~~json
{
  "type":"about:blank",
  "title":"Item not found",
  "status":404,
  "detail":"The requested item does not exist.",
  "code":"ITEM_NOT_FOUND"
}
~~~

`instance` có thể được thêm tự động theo request path tùy Spring/Jackson handling. Snippet JSON trên chỉ nêu fields chủ yếu. **curl chỉ thấy một HTTP response cuối**; không thể suy ra số Servlet dispatch chỉ từ `curl -i`. Dùng application tracing/debug logs hoặc một interceptor đã được instrument nếu bạn cần quan sát pha nội bộ.

## 13. Lỗi xảy ra ở đâu thì xử lý ở đó

| Nơi lỗi xảy ra | Ví dụ | MVC có nhận được tự động? | Cách xử lý |
| --- | --- | --- | --- |
| Controller trước khi trả async handle | Validate parameter thất bại | Có, như synchronous MVC | `@ExceptionHandler`, validation/error response |
| Bên trong `Callable.call()` | Remote service exception | Có, Spring thu exception thành async result | Advice/error resolver ở ASYNC dispatch |
| Bên trong `WebAsyncTask` Callable | Worker throw | Có, giống Callable | Advice/error resolver |
| Runnable riêng của `DeferredResult` | Throw không catch | **Không**, MVC chỉ biết holder chưa hoàn thành | Catch và `setErrorResult(exception)` |
| Callback `setErrorResult(exception)` | Event báo không tồn tại | Có, khi được chấp nhận | ASYNC dispatch rồi advice |
| DeferredResult timeout callback | Holder chưa có result | Theo callback/result/interceptor | Set error/alternate result, log và cleanup |
| Servlet async timeout mặc định | Request đợi quá lâu | Thường thành `AsyncRequestTimeoutException` | 503 mặc định hoặc policy riêng |
| Response streaming đã commit | Network disconnect/write IOException | Không đảm bảo đổi body/status được | Cleanup/log an toàn, emitter/completion API |
| Executor reject khi submit | Pool/queue quá tải | Tùy điểm throw/cấu hình | Bounded overload policy, 503/metrics/test |
| Worker background không gắn request | Scheduled job ném exception | Không | Job error handling/retry/dead-letter độc lập |

Dùng `@RestControllerAdvice` không có nghĩa bắt được exception trong mọi thread ở JVM. Điều kiện là lỗi có đi qua **cơ chế result/exception handling của MVC**. Spring MVC async giúp bạn làm điều đó với Callable/WebAsyncTask/DeferredResult khi dùng đúng cách.

## 14. Observability: đừng nhân đôi request count vì dispatch thứ hai

Vì cùng HTTP request có thể đi qua filters/interceptors hai lần ở hai dispatcher types, analytics không nên đếm mỗi `preHandle` hoặc `doFilter` như một business request nếu chúng chạy cho cả REQUEST và ASYNC.

Khuyến nghị:
- Dùng metric HTTP server được framework đo đúng vòng đời response nếu có.
- Khi tự instrument, gắn một request ID/attribute và xác định bắt đầu/kết thúc logical request một lần.
- Log dispatcher type, phase, trace ID và duration, **không log tokens/cookies/body nhạy cảm**.
- Với worker, truyền tracing/MDC context có kiểm soát và clear khi dùng pooled threads.
- Đo executor active count, pool/queue size, task rejection, timeout rate, p95/p99 latency, downstream wait time.
- Tách timeout của proxy/client/service/container; đừng nhìn một metric “async timeout” rồi kết luận DB có vấn đề.

Giả sử worker bắt đầu sau 9 giây chờ queue và Spring MVC timeout là 10 giây. Dù business computation chỉ mất 2 giây, request có thể timeout trước khi worker trả. Monitoring phải phân biệt queue time với execution time.

## 15. MVC async so với Java `@Async`, CompletableFuture và WebFlux

### 15.1. `@Async` của Spring

`@Async` là cơ chế Spring chạy method trên executor thông qua AOP proxy, khi được enable và gọi đúng proxy boundary. Nó **không tự làm HTTP response dùng Servlet ASYNC dispatch** chỉ vì service được annotate. Nếu controller chờ `future.get()`, servlet request thread vẫn block.

Để làm MVC async, controller phải trả một loại return value mà MVC nhận diện như `Callable`, `DeferredResult`, `CompletionStage/CompletableFuture` phù hợp, hoặc reactive single-result adapter. Kiểm phiên bản/adapter khi sử dụng và xử lý cancellation/exception rõ.

### 15.2. `CompletableFuture` và context propagation

`CompletableFuture.supplyAsync(...)` nếu không chỉ executor có thể sử dụng common ForkJoinPool. Điều đó dễ gây khó dự đoán về throughput, trace context, transaction/security context và workload isolation. Với request-scoped workloads, ưu tiên dedicated bounded executor hoặc async HTTP client phù hợp. MVC biết xử lý một số async return types qua adapters, nhưng việc một future được chạy **ở đâu** vẫn là trách nhiệm ứng dụng.

### 15.3. Spring MVC async không phải WebFlux

| Đặc điểm | Spring MVC async | Spring WebFlux |
| --- | --- | --- |
| Nền HTTP server | Servlet API | Reactive/non-blocking stack |
| Lượt dispatch `ASYNC` Servlet | Có khi dùng async support | Không cùng cơ chế Servlet MVC |
| Controller return `Callable`/`DeferredResult` | Hỗ trợ | Không phải programming model chủ đạo |
| Blocking JDBC trong worker | Vẫn chiếm worker thread | Cũng phải offload nếu gọi blocking code |
| HTTP response stream writes | Có thể blocking qua executor | Dựa trên non-blocking reactive I/O |
| Bối cảnh sử dụng | Mở rộng MVC hiện hữu | End-to-end reactive/streaming khi phù hợp |

Không chuyển sang WebFlux chỉ vì thấy chữ “async”; cần đánh giá library chain, team familiarity, thread model, tracing, backpressure và kiểm thử. Nếu chỉ đợi một số external calls trong ứng dụng MVC, Servlet async thường đủ.

## 16. Những lỗi triển khai thường gặp

| Triệu chứng | Nguyên nhân thường gặp | Cách giải quyết |
| --- | --- | --- |
| Controller trả Callable nhưng thread vẫn đông | Callable thực hiện I/O blocking trong worker | Đo pool/queue; cân nhắc async I/O hoặc tăng năng lực phù hợp |
| `IllegalStateException` khi start async | Servlet/filter không có `asyncSupported` | Cấu hình Servlet/Filter và dispatcher types |
| Client chờ mãi với DeferredResult | Không gọi setResult/setErrorResult | Đảm bảo mọi nhánh thành công/lỗi/timeout hoàn tất |
| Worker logs lỗi nhưng client nhận 503 timeout | Runnable throw không gọi setErrorResult | Catch và đưa exception vào DeferredResult |
| `@ExceptionHandler` không chạy | Lỗi xảy ra ngoài MVC async flow hoặc response đã commit | Đưa qua holder, dùng appropriate error flow |
| MockMvc assert 200 nhưng JSON rỗng | Chỉ kiểm initial perform | Gọi `asyncDispatch(MvcResult)` |
| Interceptor double-count metric | preHandle chạy lại ở ASYNC | Phân biệt phase/dispatcher type |
| MDC mất hoặc user hiện sai | ThreadLocal không tự propagate hoặc bị leak | Context propagation và cleanup có kiểm soát |
| `SecurityContextHolder` rỗng trong worker custom | Callback không qua tích hợp Callable Security | Truyền trusted principal/context qua executor wrapper |
| `@Transactional` không còn trên worker | Transaction context thread-bound | Mở transaction ở service đúng thread |
| Timeout luôn sau 30s dù MVC đặt 120s | Proxy/client/LB timeout ngắn hơn | Cấu hình tổng thể và kiểm network |
| Async timeout sau khi công việc hoàn tất | Race/queue backlog/task result đến muộn | Kiểm setResult boolean, metrics và cancellation |
| Hàng nghìn worker threads khi tải lớn | Executor không giới hạn | Dùng bounded pool/queue và overload handling |
| JSON 500 bị lộ raw exception | Catch-all advice trả ex.getMessage() | Safe ProblemDetail + internal logs |
| Background task hoàn thành sau client disconnect | Không có cancel/cleanup | Kiểm idempotency, timeout và lifecycle callbacks |
| `ResponseBodyEmitter` đã commit rồi vẫn muốn đổi 404 | HTTP status/headers đã gửi | Xử lý streaming protocol/error riêng |
| Gọi controller method hai lần do nghĩ ASYNC là request mới | Hiểu nhầm mapping trong redispatch | MVC dùng stored result, không gọi lại method |
| `Thread.sleep` trong timeout callback | Blocking container thread | Callback nhanh; schedule cleanup ngoài nếu cần |

## 17. Checklist production

1. Xác nhận container, DispatcherServlet và filter mapping hỗ trợ Servlet async.
2. Chọn đúng kiểu: Callable cho computation MVC-managed; DeferredResult cho external event/completion; WebAsyncTask cho cấu hình Callable per-request.
3. Đặt MVC async timeout và per-dependency timeout; xác nhận LB/client timeout không ngắn hơn ngoài ý muốn.
4. Dùng executor có giới hạn với metrics/rejection policy rõ ràng; không tạo unbounded threads.
5. Đảm bảo mọi callback DeferredResult có nhánh `setResult` hoặc `setErrorResult`.
6. Dùng `@ExceptionHandler`/`ProblemDetail` cho known errors, giữ status/body an toàn.
7. Xét timeout/result race, cancellation, cleanup và pending-registration leaks.
8. Không dựa vào ThreadLocal/MDC/SecurityContext/transaction context tự lan qua threads.
9. Phân biệt `REQUEST` và `ASYNC` để không đếm/dùng business side effects hai lần.
10. Kiểm AsyncHandlerInterceptor và OncePerRequestFilter nếu cần áp dụng logic ở redispatch.
11. Test MockMvc theo hai pha và thêm integration HTTP/container tests cho timeout/disconnect/security.
12. Nếu streaming, kiểm hành vi khi response đã commit; không giả định có thể thay status.
13. Tách các job thực sự dài sang queue/job status endpoint thay vì giữ HTTP connection vô hạn.
14. Kiểm sensitive data trong log, trace, exception và security context propagation.
15. Đo thread pool, queue, response latency và errors sau mỗi thay đổi executor/config.

### Câu hỏi tự kiểm tra

**1.** Trong Callable, method controller có được gọi lại ở lần ASYNC dispatch không?

**Trả lời:** Không. Spring MVC map handler để tiếp tục, nhưng dùng **concurrent result đã lưu**, không chạy lại business code trong controller method.

**2.** HTTP client gửi mấy request nếu server xử lý REQUEST và ASYNC?

**Trả lời:** Một HTTP request. REQUEST/ASYNC là hai Servlet dispatch nội bộ, client thấy một response hoàn tất.

**3.** `Callable` ném exception trên worker có vào `@RestControllerAdvice` được không?

**Trả lời:** Có, khi Spring MVC đang quản lý Callable, exception được giữ làm result và xử lý ở lượt ASYNC dispatch.

**4.** Tại sao exception trong executor runnable của DeferredResult không tự tới advice?

**Trả lời:** Runnable độc lập không được Spring tự liên kết với DeferredResult; ứng dụng phải catch và gọi `setErrorResult(exception)`.

**5.** `DeferredResult.setResult(...)` trả true có nghĩa client đã nhận response chưa?

**Trả lời:** Chưa. Chỉ phản ánh holder chấp nhận thiết lập result; sau đó còn async dispatch, conversion và network write.

**6.** Vì sao MockMvc cần `asyncDispatch`?

**Trả lời:** MockMvc mô phỏng Servlet processing nhưng không tự hoàn tất async request như embedded container; phải dispatch tiếp để test status/body cuối.

**7.** `WebAsyncTask.onTimeout` có nên gọi HTTP client 10 giây để retry không?

**Trả lời:** Không. Timeout callback chạy trong container thread và phải nhanh; xử lý retry/background việc nặng bên ngoài, có bounded resources.

**8.** Spring MVC async có giải phóng mọi thread trong khi JDBC đang chạy không?

**Trả lời:** Không. Servlet request thread được trả lại pool, nhưng JDBC blocking vẫn chiếm executor worker.

**9.** Sau khi `ResponseBodyEmitter` đã gửi một phần body, có thể đổi status thành 404 không?

**Trả lời:** Thường không vì response có thể đã commit; phải xử lý lỗi streaming theo protocol tương ứng.

## 18. Tài liệu chính thức

- [Spring Framework 6.2 — Asynchronous Requests](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html): Callable, DeferredResult, WebAsyncTask, processing, error handling, interceptor, streaming và Servlet configuration.
- [Spring Framework 6.1 — WebAsyncManager Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/web/context/request/async/WebAsyncManager.html): start, concurrent result, dispatch lifecycle.
- [Spring Framework 6.1 — DeferredResult Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/web/context/request/async/DeferredResult.html): `setResult`, `setErrorResult`, timeout/error/completion callbacks.
- [Spring Framework 6.1 — WebAsyncTask Javadoc](https://docs.spring.io/spring-framework/docs/6.1.8/javadoc-api/org/springframework/web/context/request/async/WebAsyncTask.html): per-task timeout, executor, timeout/error/completion.
- [Spring Framework 6.1 — AsyncHandlerInterceptor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.8/javadoc-api/org/springframework/web/servlet/AsyncHandlerInterceptor.html): `afterConcurrentHandlingStarted` và lifecycle interceptor.
- [Spring Framework — MockMvc Async Requests](https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/async-requests.html): `request().asyncStarted()`, `request().asyncResult(...)` và `asyncDispatch`.
- [Spring Framework — MVC Configuration](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/async-support.html): `configureAsyncSupport`, task executor và timeout.
- [Jakarta Servlet 6.0 — AsyncContext](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/asynccontext): `dispatch`/`complete`, timeout/listeners.
- [Spring Security — Servlet Integration](https://docs.spring.io/spring-security/reference/servlet/integrations/servlet-api.html): integration points và security context.
- [Spring Framework — Problem Details](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html): error response API và exception handling.

**Ghi nhớ:** Spring MVC async không trả response ngay và cũng không tạo “request thứ hai” ở browser. Controller trả **Callable/DeferredResult/WebAsyncTask**, Servlet giải phóng request thread rồi **ASYNC dispatch** khi có kết quả/lỗi. Thành công đi qua return-value processing; lỗi đúng nguồn đi qua exception resolvers; timeout/cleanup/context propagation cần được cấu hình và kiểm thử. Trong MockMvc, đừng dừng ở `asyncStarted`: phải gọi `asyncDispatch` và kiểm tra HTTP response cuối.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-async-processing-005">ResponseBodyEmitter, SseEmitter và StreamingResponseBody</a>

<details>
<summary>Xem chi tiết</summary>

# ResponseBodyEmitter, SseEmitter và StreamingResponseBody trong Spring MVC

## 1. Khi nào một HTTP response nên được gửi theo dòng thời gian?

Phản hồi thông thường của Spring MVC thường được hoàn thành trong một lần:

```text
Client                Spring MVC
  | GET /api/report        |
  |----------------------->|
  |                        | Tính toàn bộ kết quả
  |<-----------------------| HTTP 200 + JSON hoàn chỉnh
  |     kết thúc           |
```

Nhưng một số yêu cầu cần **truyền kết quả thành nhiều phần** khi dữ liệu sẵn sàng:

- Một công việc dài gửi các bước tiến độ (queued, running, completed).
- Browser nhận thông báo một chiều từ server qua **Server-Sent Events (SSE)**.
- API sinh báo cáo CSV hoặc nội dung nhị phân lớn mà không giữ toàn bộ output trong RAM.
- Một truy vấn trả nhiều record tuần tự dưới dạng **NDJSON** (mỗi dòng là một JSON value).
- Hệ thống cần giảm **time to first byte** và tránh tạo một mảng/list lớn trước khi trả response.

Spring MVC Servlet cung cấp ba kiểu trả về tiêu biểu:

| API | Đơn vị được ghi | Định dạng | Trường hợp phù hợp |
| --- | --- | --- | --- |
| **`ResponseBodyEmitter`** | Nhiều object; mỗi object qua `HttpMessageConverter` | Do ứng dụng quyết định, ví dụ text/NDJSON | Tiến độ dạng nhiều fragment, streaming các object |
| **`SseEmitter`** | Nhiều SSE events, là subclass của `ResponseBodyEmitter` | `text/event-stream`, có `event/id/data/retry` | EventSource của browser, thông báo một chiều |
| **`StreamingResponseBody`** | Bytes trực tiếp trên `OutputStream` | CSV, binary, text tùy code | Download/ghi stream không cần message conversion mỗi object |

Cả ba thuộc Spring MVC **bất đồng bộ**, nhưng **không biến Servlet output I/O thành non-blocking I/O**. Khi ghi response, một thread vẫn có thể bị block bởi network hoặc downstream client. Đây là điểm khác biệt quan trọng so với Spring WebFlux.

**Phạm vi:** Spring Framework 6.2.x / Spring Boot 3.5.x, Java 17+, **Servlet MVC** (ví dụ embedded Tomcat). Không import nhầm API reactive WebFlux khi dùng ba kiểu này.

## 2. Cơ sở Servlet async: request thread kết thúc nhưng response còn mở

Trong xử lý đồng bộ, container thread đi qua filters, `DispatcherServlet`, controller, ghi response rồi thoát. Với MVC async, Spring gọi cơ chế `request.startAsync()` của Servlet API (thông qua framework), cho phép **request thread trả về container** trong khi HTTP response chưa hoàn thành.

```text
HTTP GET /demo/events
       |
       v
Servlet container REQUEST dispatch (Thread A)
       |
Filters -> DispatcherServlet -> Controller
       |
Controller trả SseEmitter/ResponseBodyEmitter
       |
Spring MVC kích hoạt Servlet async
       |
Thread A trả về pool (response vẫn mở)
       |
Producer thread / message source (Thread B)
       |
  emitter.send(...) nhiều lần
       |
  emitter.complete() hoặc timeout/error/disconnect
       |
Servlet ASYNC dispatch để hoàn tất vòng đời
       |
Response kết thúc, callbacks cleanup
```

Đối với `StreamingResponseBody`, callback `writeTo(OutputStream)` được thực thi bất đồng bộ bởi MVC task executor, thay vì bắt container request thread chờ toàn bộ download. Vì vậy cần cấu hình **`AsyncTaskExecutor` có giới hạn và phù hợp tải thực tế**.

### “Async” không đồng nghĩa “không sử dụng thread”

- Servlet request thread có thể được giải phóng khi controller trả emitter hoặc streaming callback.
- Producer vẫn cần thread/worker hoặc một nguồn sự kiện có cơ chế phù hợp.
- Ghi `OutputStream` trong Spring MVC vẫn là thao tác **blocking** theo mô hình Servlet output truyền thống.
- Nếu client tải cực chậm, thread đang gọi `send` hoặc `writeTo` có thể bị chặn.
- Việc trả `Flux` trong Spring MVC có thể hỗ trợ reactive backpressure ở giao diện publisher, nhưng việc ghi HTTP cuối cùng vẫn có thể diễn ra trên thread và block.
- Với hệ thống có số lượng lớn kết nối kéo dài và mục tiêu I/O non-blocking end-to-end, cần cân nhắc **Spring WebFlux, WebSocket hoặc hạ tầng event streaming khác** theo đặc điểm ứng dụng.

## 3. So sánh trực tiếp ba API và các kiểu async khác

| Đặc tính | `ResponseBodyEmitter` | `SseEmitter` | `StreamingResponseBody` |
| --- | --- | --- | --- |
| Trả nhiều giá trị | Có | Có | Có, dưới dạng nhiều lần ghi bytes |
| Conversion object | Qua `HttpMessageConverter` | Qua converter cho phần data | **Không** cho bytes được viết trực tiếp |
| Tự có SSE event formatting | Không | **Có** | Không |
| Đọc từ JavaScript `EventSource` | Chỉ khi tự tạo đúng wire format | **Phù hợp nhất** | Có thể tự viết SSE bytes nhưng ít tiện |
| Dành cho file/CSV lớn | Có thể, nhưng ít trực tiếp | Không phải mục tiêu chính | **Phù hợp nhất** |
| Kiểm soát response status/headers | Trả trong `ResponseEntity<ResponseBodyEmitter>` | `ResponseEntity<SseEmitter>` | `ResponseEntity<StreamingResponseBody>` |
| Timeout callback tích hợp emitter | `onTimeout` | Kế thừa `onTimeout` | Không có emitter callback; xử lý qua MVC/Servlet async infrastructure |
| Hoàn tất bình thường | `complete()` | `complete()` | Callback `writeTo` trả về |
| Error application-level | `completeWithError(ex)` khi phù hợp | Kế thừa `completeWithError` | `writeTo` ném IOException/exception |
| Khi client ngắt | Ghi tiếp có thể gây IOException | Ghi heartbeat/event có thể phát hiện disconnect | `writeTo` có thể ném IOException |
| Đơn vị hiển thị phía client | Phụ thuộc content framing do bạn chọn | Event theo chuẩn SSE | Bytes do code xác định |

`DeferredResult<T>` và `Callable<T>` giải quyết **một kết quả bất đồng bộ**; chúng không tự tạo nhiều message trong một HTTP response. `WebAsyncTask` là cách cấu hình cho `Callable`. Nếu một job trả một JSON object sau khi hoàn tất, `DeferredResult` có thể đúng hơn. Nếu cần nhiều thông báo trên một response, hãy chọn trong ba API trên.

### HTTP chunked transfer không phải wire protocol riêng của API

Khi không biết Content-Length, HTTP/1.1 server có thể dùng `Transfer-Encoding: chunked`; HTTP/2 có frame riêng. **Đừng viết hoặc parse chunk markers** trong code ứng dụng. Bạn viết logical data/fragments, container và HTTP stack quyết định cách biểu diễn transport.

Ngay cả khi gọi `flush()`, proxy, CDN, compression, browser buffering hoặc TCP vẫn có thể làm dữ liệu đến theo các nhóm khác nhau. Không dựa vào “mỗi `send` tạo chính xác một network chunk”.

## 4. `ResponseBodyEmitter`: gửi lần lượt nhiều object

Class:

```java
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
```

Controller có thể trả emitter ngay:

```java
@GetMapping("/progress")
ResponseBodyEmitter progress() {
    ResponseBodyEmitter emitter = new ResponseBodyEmitter(30_000L);
    // Producer ở thành phần/thread khác sẽ gọi send và complete.
    return emitter;
}
```

**Đây là method fragment**, không phải flow hoàn chỉnh; nếu không có producer hoặc không gọi complete, kết nối có thể treo tới timeout.

### 4.1. API thiết yếu

- `new ResponseBodyEmitter()`: timeout từ cấu hình MVC/server nếu không đặt riêng.
- `new ResponseBodyEmitter(Long timeout)`: timeout cho emitter; đơn vị **milliseconds**.
- `send(Object object)`: chuyển object qua một `HttpMessageConverter` tương thích.
- `send(Object object, MediaType hint)`: đưa media type gợi ý để chọn converter.
- `complete()`: thông báo producer hoàn tất bình thường.
- `completeWithError(Throwable)`: hoàn tất với lỗi application-level phù hợp, kích hoạt async dispatch.
- `onTimeout(Runnable)`: callback khi request async bị timeout.
- `onError(Consumer<Throwable>)`: callback khi xử lý async gặp lỗi.
- `onCompletion(Runnable)`: callback khi request async kết thúc, gồm success, timeout và network error.

Spring 6.2 cho phép đăng ký nhiều callbacks cho các lifecycle events nói trên. Các callbacks có thể chạy trên **container threads**, không mặc định cùng producer thread. Vì vậy mọi shared state phải thread-safe.

### 4.2. Một emitter không tự tạo NDJSON hợp lệ

Nếu muốn nhiều records dưới dạng NDJSON:

```text
{"seq":1,"state":"queued"}
{"seq":2,"state":"running"}
{"seq":3,"state":"done"}
```

Mỗi dòng phải là JSON value hợp lệ và có delimiter **newline**. Việc gọi `emitter.send(object, MediaType.APPLICATION_JSON)` liên tiếp không tự bảo đảm các object được phân tách thành từng dòng: nếu chỉ ghép JSON object sát nhau, output `{}{}` không phải một JSON document hoặc NDJSON hợp lệ.

Một producer có thể gửi **object JSON rồi ký tự newline** theo đúng contract:

```java
emitter.send(new Progress(1, "queued"), MediaType.APPLICATION_JSON);
emitter.send("\n", MediaType.TEXT_PLAIN);

emitter.send(new Progress(2, "done"), MediaType.APPLICATION_JSON);
emitter.send("\n", MediaType.TEXT_PLAIN);

emitter.complete();
```

Đây là **fragment**, giả định record `Progress` và emitter đã được khai báo. Trong ví dụ runnable phần 9, server dùng `application/x-ndjson` ở response, dùng converter JSON cho từng record và text/plain cho delimiter.

Nếu cần đảm bảo framing/flush đặc thù ở tốc độ rất cao, `StreamingResponseBody` có thể phù hợp hơn hoặc chọn framework chuyên biệt cho publisher/batching.

### 4.3. Error lifecycle: đặc biệt với IOException

Theo hướng dẫn Spring MVC:

- Nếu `emitter.send(...)` ném **`IOException`** vì client đã ngắt hoặc network write thất bại, **không tự gọi `complete()` hoặc `completeWithError()`** từ nhánh đó. Servlet container sẽ thông báo async error; Spring hoàn tất request qua xử lý tương ứng.
- Với lỗi **do ứng dụng phát sinh** trong producer (business failure, serialization bug trước khi response committed), `completeWithError(ex)` có thể được sử dụng, nếu emitter chưa hoàn tất.
- Khi response đã commit (đã ghi bytes/headers), handler lỗi **không chắc có thể thay HTTP status thành 500 hoặc thay body bằng JSON error**. Client có thể nhận phần dữ liệu đầu rồi stream đột ngột kết thúc.
- `onCompletion` là nơi phù hợp để gỡ reference emitter khỏi registry và giải phóng tài nguyên; đừng coi nó chỉ xảy ra trên happy path.

## 5. `SseEmitter`: Server-Sent Events chuẩn cho browser

Class:

```java
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
```

`SseEmitter` kế thừa `ResponseBodyEmitter` nhưng bổ sung cách xây **SSE event**:

```java
SseEmitter emitter = new SseEmitter(30_000L);

emitter.send(SseEmitter.event()
        .id("event-42")
        .name("progress")
        .reconnectTime(2_000L)
        .data("Đã hoàn thành 42%"));
```

Đây là **fragment**, chưa có controller/executor và exception handling. `SseEmitter.event()` trả `SseEventBuilder` để đặt fields; `send(builder)` ghi event theo định dạng `text/event-stream`.

### 5.1. Định dạng wire của SSE

Một stream SSE là **UTF-8 text** với các events tách nhau bởi **dòng trống**:

```text
id: 42
event: progress
retry: 2000
data: {"percent":42}

id: 43
event: finished
data: {"percent":100}

```

Các trường quan trọng:

| Field | Ý nghĩa |
| --- | --- |
| `data:` | Payload. Nhiều dòng `data:` liên tiếp được nối bằng newline ở client |
| `event:` | Loại event để JavaScript dùng `addEventListener("progress",...)` |
| `id:` | Event ID, dùng cho `lastEventId` và cơ chế reconnect |
| `retry:` | Gợi ý milliseconds cho client khi reconnect; phải là số nguyên |
| Dòng bắt đầu bằng `:` | Comment/heartbeat, client không coi là application event |
| Dòng trống | Kết thúc một event frame |

**Tên field không phải HTTP headers.** `id:` ở đây nằm trong response body `text/event-stream`, không phải `ETag` hay HTTP `Last-Modified`. Mỗi event cần có frame delimiter đúng; `SseEmitter` giúp giảm sai sót khi tự nối các chuỗi.

### 5.2. JavaScript `EventSource`

Browser có API EventSource:

```javascript
const source = new EventSource("/demo/sse");

source.addEventListener("progress", event => {
  console.log("Progress:", event.lastEventId, event.data);
});

source.addEventListener("finished", event => {
  console.log("Finished:", event.data);
  source.close(); // Demo stream hữu hạn, tránh tự reconnect.
});

source.onerror = event => {
  console.warn("SSE interrupted", event);
};
```

Lưu ý:

- `EventSource` dùng **HTTP GET** để đăng ký stream.
- Một kết nối bị đóng có thể **tự reconnect** theo chính sách browser; với stream cố ý kết thúc sau vài events, client nên gọi `source.close()` hoặc có protocol stop thích hợp.
- `EventSource` hỗ trợ thông điệp **server → client**; muốn client gửi commands thường dùng POST/PUT qua `fetch` hoặc WebSocket nếu cần hai chiều.
- EventSource trong browser không có API chuẩn để tự gắn mọi custom request header; với auth thường dùng cookie phù hợp hoặc kiến trúc gateway/token có thiết kế riêng. Không đưa access token thô vào URL query.
- Cross-origin EventSource cần cấu hình CORS chính xác. Nếu dùng credentials/cookies, cần `withCredentials: true` và CORS policy cho phép origin cụ thể; không dùng wildcard origin khi cho credentials.
- SSE được hỗ trợ trên nhiều browser hiện đại; proxy/CDN buffering và HTTP/1.1 connection limits có thể gây khó khi mở nhiều tab.

### 5.3. `Last-Event-ID` và reconnect

Nếu server gửi `id: 42`, browser có thể gửi **`Last-Event-ID: 42`** ở lần kết nối lại. Điều này **không tự tạo replay log trong Spring**. Nếu app chỉ giữ emitter trong RAM, khi reconnect sang node khác hoặc sau restart nó không biết phải gửi tiếp từ event nào.

Một hệ thống SSE production nếu cần delivery continuity phải:

1. Gán IDs ổn định, có thứ tự theo stream/resource.
2. Lưu events trong broker/database/log theo retention policy.
3. Khi client gửi Last-Event-ID, kiểm tra ID theo người dùng/tenant/stream, sau đó replay events phù hợp.
4. Xử lý duplicate events theo semantics **at-least-once** nếu cần; client cần idempotency.
5. Giải quyết gap nếu event cũ đã hết retention.
6. Khi scale nhiều nodes, dùng shared event source hoặc pub/sub, không chỉ local map of emitters.

**Đừng hứa exactly-once** chỉ vì SSE có `id`.

### 5.4. Heartbeat và phát hiện disconnect

Servlet API không thông báo client disconnect ngay lập tức trong mọi tình huống. Với stream chạy lâu, cần **gửi dữ liệu định kỳ** để giữ kết nối và phát hiện write failure. SSE có thể dùng event comment:

```java
emitter.send(SseEmitter.event().comment("heartbeat"));
```

Đây là fragment. Comment `:heartbeat` không tạo application event ở browser. Interval cần nhỏ hơn idle timeout của proxy/container theo kiến trúc, có jitter/backoff nếu thích hợp. Heartbeat không thay thế authorization, connection limits hoặc subscriber cleanup.

Không thiết kế **mỗi client một Thread.sleep vô hạn**; cần scheduler/producer event loop có giới hạn, đo số subscribers, tối ưu broadcaster và xử lý slow clients.

## 6. `StreamingResponseBody`: ghi trực tiếp tới OutputStream

Class là functional interface:

```java
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
```

Method cốt lõi:

```java
void writeTo(OutputStream outputStream) throws IOException;
```

Spring MVC thực thi callback `writeTo` bất đồng bộ, cho ứng dụng **ghi bytes trực tiếp**. Không có message conversion cho từng object như `ResponseBodyEmitter`.

Ví dụ một CSV response:

```java
@GetMapping(path = "/export.csv", produces = "text/csv")
ResponseEntity<StreamingResponseBody> export() {
    StreamingResponseBody body = output -> {
        output.write("id,name\n".getBytes(StandardCharsets.UTF_8));
        output.write("1,Ada\n".getBytes(StandardCharsets.UTF_8));
        output.flush();
    };

    return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(
                    "text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"export.csv\"")
            .body(body);
}
```

Đây là **method fragment** trong controller; cần imports `StandardCharsets`, `ResponseEntity`, `HttpHeaders`, `MediaType`. Production cần escape CSV đúng và giới hạn output.

### 6.1. Tránh giữ toàn bộ file vào RAM

Anti-pattern cho file lớn:

```java
byte[] all = Files.readAllBytes(path);
return ResponseEntity.ok(all);
```

Khi nhiều clients tải file lớn, bộ nhớ có thể tăng theo tổng file sizes. Dùng `StreamingResponseBody` với buffer:

```java
StreamingResponseBody body = output -> {
    try (InputStream source = Files.newInputStream(path)) {
        source.transferTo(output);
        output.flush();
    }
};
```

Đây là **fragment**, cần kiểm tra `path` do server sở hữu và quyền truy cập trước đó. `InputStream#transferTo` stream qua buffer nội bộ thay vì materialize toàn bộ file, nhưng network write vẫn blocking, tốc độ download phụ thuộc downstream.

Với file rất lớn có sẵn trên disk, Spring MVC `Resource` / `ResourceRegion` có thể thích hợp hơn khi cần HTTP Range, conditional GET, Content-Length hoặc sendfile. `StreamingResponseBody` **không tự cung cấp** resumable download/`206 Partial Content`.

### 6.2. CSV/NDJSON phải tự kiểm soát framing

- Với CSV, escape dấu phẩy, quote và newline đúng. Khi export từ input người dùng vào spreadsheet, cân nhắc **CSV formula injection** (giá trị bắt đầu bằng `=`, `+`, `-`, `@` có thể được phần mềm bảng tính diễn giải như công thức).
- Với NDJSON, mỗi line là **một JSON value hoàn chỉnh**, có newline delimiter; tự nối field strings có thể tạo JSON invalid.
- Với binary, không dùng `Writer` trộn charset không kiểm soát vào `OutputStream`.
- Ghi theo batches hợp lý, không flush từng byte gây overhead lớn.
- Không set Content-Length nếu không biết chính xác bytes được gửi và không thể đảm bảo độ dài.
- Không truy cập request-scoped bean hoặc `HttpServletRequest` từ callback async nếu không có context propagation rõ ràng; truyền immutable metadata, path/ID sau khi đã validate.

## 7. Lifecycle, timeout, errors và connection cleanup

### 7.1. Các trạng thái hữu ích cho emitter

```text
CREATED
   |
   v
ACTIVE --send()--> ACTIVE --send()--> ACTIVE
   |                  |                 |
   | complete()       | timeout         | IOException/disconnect
   v                  v                 v
COMPLETED           TIMED_OUT          ERROR
   \                  |                 /
    \_________________|________________/
                       |
                onCompletion()
                giải phóng registry
```

Đây là **sơ đồ khái niệm**, không phải enum trạng thái công khai của `ResponseBodyEmitter`. Lifecycle có thể xảy ra gần nhau trên các threads khác nhau; dùng `AtomicBoolean` hoặc synchronization thích hợp khi cần bảo vệ thao tác cleanup khỏi race.

### 7.2. onTimeout khác với completeWithError

`emitter.onTimeout(...)` chỉ đăng ký callback; nó không tự hủy mọi producer trong một external message broker. Application cần cancel task/unsubscribe, giải phóng resources theo policy.

`onCompletion` chạy khi async request hoàn tất vì success/error/timeout; thường là nơi tốt nhất để remove emitter khỏi registry. Với disconnect, không chờ callback ở một thời điểm cụ thể nếu không có write/heartbeat phát hiện lỗi.

### 7.3. Sai lầm với IOException

Nếu `send` ném `IOException` vì container write failure, ứng dụng **không được tự gọi `completeWithError` hoặc `complete` cho chính sự kiện container này**. Container sẽ cung cấp error notification, Spring xử lý async completion. Lời khuyên này đến từ Spring MVC async streaming reference.

Nếu lỗi business xảy ra trước/sau khi gửi một số events, `completeWithError(ex)` có thể khiến Spring xử lý exception resolver khi async dispatch. Nhưng nếu **headers/response đã committed**, status/body không còn được thay an toàn; server nên phát một event lỗi thuộc protocol trước khi complete (nếu connection còn usable), hoặc để client xử lý disconnect/retry.

### 7.4. Timeout ba tầng

1. **Emitter timeout**: `new SseEmitter(30_000L)` hoặc `new ResponseBodyEmitter(30_000L)`.
2. **MVC default async timeout**: `AsyncSupportConfigurer#setDefaultTimeout`; áp dụng khi không có override.
3. **Servlet container/proxy/load balancer timeout**: kết nối có thể bị đóng độc lập với hai giá trị trên.

Đừng đồng nhất “timeout emitter 30 s” với “mọi proxy chắc chắn giữ connection ít nhất 30 s”. Reverse proxy có idle/read timeout riêng, browser có chính sách reconnect riêng.

## 8. Cấu hình TaskExecutor và Servlet async

### 8.1. Spring Boot 3.5 có executor tự cấu hình — nhưng vẫn cần sizing

Spring Boot 3.5 có cơ chế auto-configure `AsyncTaskExecutor` khi điều kiện phù hợp. Trong phiên bản này, bean auto-configured dùng tên **`applicationTaskExecutor`**; Spring MVC cần bean có tên đó và kiểu `AsyncTaskExecutor` khi dùng Boot auto-configuration.

Tài liệu Spring Framework khuyến nghị cấu hình executor phù hợp tải thay vì phụ thuộc vào fallback mặc định khi streaming cần blocking writes. Boot thường tạo `ThreadPoolTaskExecutor` trừ khi Java 21+ virtual threads được bật. Sự có mặt của một custom `Executor` bean có thể làm Boot auto-configuration back off, nên cần kiểm tra executor thực tế.

Một cấu hình manual rõ ràng trong demo phần 9:

```java
@Configuration
class StreamingAsyncConfig implements WebMvcConfigurer {

    @Bean(name = "applicationTaskExecutor")
    ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(4);
        pool.setMaxPoolSize(8);
        pool.setQueueCapacity(100);
        pool.setThreadNamePrefix("mvc-stream-");
        pool.initialize();
        return pool;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(applicationTaskExecutor());
        configurer.setDefaultTimeout(30_000L);
    }
}
```

Đây là **class fragment**, cần imports Spring Config/AsyncTaskExecutor; xem full file runnable phần 9.

- Pool có tối đa 8 workers và queue 100 requests/tasks trong ví dụ. Đây là **con số để học**, không phải sizing production cho mọi hệ thống.
- Nếu task bị block do slow clients, pool có thể cạn workers; khi queue đầy cần fail-fast/rejection/backpressure policy.
- `StreamingResponseBody` và producer tasks trong demo dùng chung pool để đơn giản; trong production, nên **tách các executors** nếu load profile khác nhau, để streaming writes không làm nghẽn các producers.
- Có thể dùng Boot properties `spring.task.execution.pool.*` nếu để Boot auto-configure executor; tránh vừa tạo custom executor vừa kỳ vọng mọi Boot properties tự thay đổi nó.
- Với Java 21+ virtual threads, việc sử dụng threads nhẹ hơn không biến network I/O thành backpressure tự động. Cần giới hạn clients và lượng dữ liệu in-flight.

### 8.2. Servlet/Filter registrations phải hỗ trợ async

Nếu dùng Servlet 3+ và Java configuration đúng của Spring Boot, `DispatcherServlet` thường được đăng ký hỗ trợ async. Nhưng một custom filter thiếu `asyncSupported=true` có thể khiến request không thể chuyển sang async.

Với `FilterRegistrationBean` tùy chỉnh:

```java
registration.setAsyncSupported(true);
registration.setDispatcherTypes(
        DispatcherType.REQUEST, DispatcherType.ASYNC);
```

Đây là **fragment**, cần đúng filter registration và imports `jakarta.servlet.DispatcherType`. `ASYNC` dispatcher mapping dùng khi cần tham gia redispatch; không tự bảo đảm filter cũng chạy lại logic nếu base class `OncePerRequestFilter` mặc định skip async/error.

Nếu khai báo `web.xml`/WAR ngoài, cần cấu hình `<async-supported>true</async-supported>` cho servlet và relevant filters. Test trong embedded container hoặc target deployment thay vì chỉ unit test controller.

## 9. Ứng dụng Spring Boot 3.5 + Java 17 có thể chạy

Ba endpoints:

1. `GET /demo/objects` — `ResponseBodyEmitter` xuất **hai NDJSON records** dùng Jackson converter và dấu xuống dòng.
2. `GET /demo/sse` — `SseEmitter` xuất một event `progress` và một event `finished`, mỗi event có ID.
3. `GET /demo/report.csv` — `StreamingResponseBody` xuất một file CSV không materialize toàn bộ response trước.

Để test hữu hạn và không treo người dùng, mỗi stream cố ý **kết thúc bình thường**. Chúng không mô phỏng endless live feed, shared pub/sub hoặc lưu trạng thái qua nodes. Full source nằm trong một file Java.

### 9.1. Gradle dependencies

Trong project Spring Boot 3.5 có Boot plugin, repositories, dependency management và Java 17 toolchain:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đây là **mảnh cấu hình `build.gradle`**, không phải Gradle file độc lập. Starter web có Servlet MVC/Jackson dùng cho JSON object conversions. Không cần dependency WebFlux để chạy ba kiểu streaming này.

### 9.2. Toàn bộ Java source

Tạo file **`src/main/java/demo/streaming/StreamingDemoApplication.java`**:

```java
package demo.streaming;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@SpringBootApplication
public class StreamingDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamingDemoApplication.class, args);
    }
}

@Configuration
class StreamingAsyncConfig implements WebMvcConfigurer {

    @Bean(name = "applicationTaskExecutor")
    ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(4);
        pool.setMaxPoolSize(8);
        pool.setQueueCapacity(100);
        pool.setThreadNamePrefix("mvc-stream-");
        pool.initialize();
        return pool;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(applicationTaskExecutor());
        configurer.setDefaultTimeout(30_000L);
    }
}

record Progress(int seq, String state) {}

@RestController
class StreamingController {

    private static final MediaType NDJSON =
            MediaType.parseMediaType("application/x-ndjson");

    private final AsyncTaskExecutor executor;

    StreamingController(
            @Qualifier("applicationTaskExecutor")
            AsyncTaskExecutor executor) {
        this.executor = executor;
    }

    @GetMapping(path = "/demo/objects",
            produces = "application/x-ndjson")
    ResponseEntity<ResponseBodyEmitter> objects() {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(15_000L);
        AtomicBoolean stopped = registerLifecycle(emitter);

        executor.execute(() -> {
            try {
                if (stopped.get()) {
                    return;
                }

                emitter.send(new Progress(1, "queued"),
                        MediaType.APPLICATION_JSON);
                emitter.send("\n", MediaType.TEXT_PLAIN);

                emitter.send(new Progress(2, "done"),
                        MediaType.APPLICATION_JSON);
                emitter.send("\n", MediaType.TEXT_PLAIN);

                if (!stopped.get()) {
                    emitter.complete();
                }
            } catch (IOException disconnected) {
                // Servlet container handles IOException/async error.
                stopped.set(true);
            } catch (RuntimeException failure) {
                if (!stopped.get()) {
                    emitter.completeWithError(failure);
                }
            }
        });

        return ResponseEntity.ok()
                .contentType(NDJSON)
                .cacheControl(CacheControl.noStore())
                .body(emitter);
    }

    @GetMapping(path = "/demo/sse",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<SseEmitter> sse() {
        SseEmitter emitter = new SseEmitter(15_000L);
        AtomicBoolean stopped = registerLifecycle(emitter);

        executor.execute(() -> {
            try {
                if (stopped.get()) {
                    return;
                }

                emitter.send(SseEmitter.event()
                        .id("1")
                        .name("progress")
                        .reconnectTime(2_000L)
                        .data("queued"));

                emitter.send(SseEmitter.event()
                        .id("2")
                        .name("finished")
                        .data("done"));

                if (!stopped.get()) {
                    emitter.complete();
                }
            } catch (IOException disconnected) {
                // Do NOT call completeWithError after an I/O failure.
                stopped.set(true);
            } catch (RuntimeException failure) {
                if (!stopped.get()) {
                    emitter.completeWithError(failure);
                }
            }
        });

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @GetMapping(path = "/demo/report.csv",
            produces = "text/csv")
    ResponseEntity<StreamingResponseBody> csv() {

        StreamingResponseBody body = output -> {
            // Deterministic demo, no user-provided spreadsheet cells.
            output.write("id,name\n".getBytes(StandardCharsets.UTF_8));
            output.write("1,Ada\n".getBytes(StandardCharsets.UTF_8));
            output.write("2,Lin\n".getBytes(StandardCharsets.UTF_8));
            output.flush();
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"report.csv\"")
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private static AtomicBoolean registerLifecycle(
            ResponseBodyEmitter emitter) {
        AtomicBoolean stopped = new AtomicBoolean(false);
        emitter.onTimeout(() -> stopped.set(true));
        emitter.onError(error -> stopped.set(true));
        emitter.onCompletion(() -> stopped.set(true));
        return stopped;
    }
}
```

**Đây là file Java 17 hợp lệ trong Spring Boot 3.5:** chỉ `StreamingDemoApplication` là public; `StreamingAsyncConfig`, `StreamingController` và record `Progress` cùng package nên được Spring component scan. `ThreadPoolTaskExecutor` là Spring `AsyncTaskExecutor`, được dùng cho cả producer và MVC async trong demo.

Chú thích:

- `objects()` gửi JSON objects và `"\n"` riêng để tạo **NDJSON**. Cần message converters của `spring-boot-starter-web`. Một producer duy nhất đảm bảo thứ tự messages theo `seq`.
- `sse()` định dạng đúng các SSE frames, có `id` và `event`; không phải JSON. Header `X-Accel-Buffering: no` mang tính **cấu hình riêng cho NGINX** nếu reverse proxy tôn trọng, không phải HTTP standard header bắt buộc.
- `csv()` được Spring MVC schedule callback lên async executor; không tự tạo thread. Ví dụ text CSV cố định, không có yêu cầu escape input người dùng.
- Timeout 15 giây được đặt **trên từng emitter**, còn MVC default timeout 30 giây là fallback cho request kiểu async không override.
- `onTimeout`, `onError` và `onCompletion` chỉ đặt flag; trong hệ thống thật cần dừng producer/subscription, huỷ scheduled tasks và xóa emitter khỏi registry.
- Nếu executor quá tải, lệnh `execute` có thể ném rejection exception ngay trong handler; production cần admission control, fallback status/metrics, không retry vô hạn.
- Việc gửi trước khi Spring hoàn tất khởi tạo emitter có thể được framework queue để flush sau khi handler được gắn; không dựa vào thứ tự thread để suy ra bytes chắc chắn được gửi trước lúc controller trả.
- Không tự thiết kế endless `while(true)` trong mỗi HTTP request, nhất là với thread pool cố định.
- Response media types rất quan trọng: client cần biết `application/x-ndjson`, `text/event-stream` hoặc `text/csv` đúng chức năng.

## 10. Gọi bằng curl và đọc kết quả mong đợi

Chạy application:

```bash
./gradlew bootRun
```

### 10.1. Object streaming/NDJSON

```bash
curl -N -i 'http://localhost:8080/demo/objects'
```

`-N` (no-buffer) giúp curl không tự buffer output; không ép mọi proxy flush ngay.

Body dự kiến (hai dòng, mỗi dòng là một JSON object):

```json
{"seq":1,"state":"queued"}
{"seq":2,"state":"done"}
```

Thứ tự keys bên trong JSON object phụ thuộc Jackson; ví dụ này dùng record với hai fields theo thứ tự khai báo. Client NDJSON nên parse **từng dòng** thay vì parse toàn body như một JSON array.

### 10.2. SSE

```bash
curl -N -i \
  -H 'Accept: text/event-stream' \
  'http://localhost:8080/demo/sse'
```

Phần body có dạng:

```text
id:1
event:progress
retry:2000
data:queued

id:2
event:finished
data:done

```

Định dạng có thể có dấu cách sau dấu hai chấm hoặc thứ tự field hơi khác theo serializer; **contract quan trọng** là các field và event delimiter. `curl` chỉ hiển thị bytes, không tự parse SSE như browser `EventSource`.

### 10.3. Download CSV

```bash
curl -i 'http://localhost:8080/demo/report.csv'
```

Status dự kiến `200 OK`; headers gồm `Content-Type: text/csv;charset=UTF-8` và `Content-Disposition: attachment; filename="report.csv"`. Body:

```csv
id,name
1,Ada
2,Lin
```

Tải file ra disk bằng `curl -o report.csv 'http://localhost:8080/demo/report.csv'`; không dùng `-i` khi lưu CSV thực để tránh đưa HTTP headers vào file.

## 11. Kiểm thử MockMvc cho async và streaming hữu hạn

`MockMvc` không chạy một HTTP socket real như Tomcat, nên nó phù hợp để kiểm tra **MVC contract, media type và body sau khi producer hoàn tất**. Nó không đo được flush latency trên đường truyền, proxy buffering, TCP disconnect hay behavior của browser EventSource thực.

Tạo **`src/test/java/demo/streaming/StreamingDemoApplicationTests.java`**:

```java
package demo.streaming;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StreamingDemoApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void emitsTwoNdjsonRecordsAndCompletes() throws Exception {
        MvcResult result = mvc.perform(get("/demo/objects"))
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(5_000L); // Chờ signal hoàn tất, không sleep.

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/x-ndjson"))
                .andExpect(content().string(
                        containsString("\"state\":\"queued\"")))
                .andExpect(content().string(
                        containsString("\"state\":\"done\"")));
    }

    @Test
    void writesSseFieldsAndCompletes() throws Exception {
        MvcResult result = mvc.perform(get("/demo/sse")
                        .accept("text/event-stream"))
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(5_000L);

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        "text/event-stream"))
                .andExpect(content().string(
                        containsString("event:progress")))
                .andExpect(content().string(
                        containsString("data:queued")))
                .andExpect(content().string(
                        containsString("event:finished")));
    }

    @Test
    void writesCsvBytesInAsyncCallback() throws Exception {
        MvcResult result = mvc.perform(get("/demo/report.csv"))
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(5_000L);

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"report.csv\""))
                .andExpect(content().string(
                        "id,name\n1,Ada\n2,Lin\n"));
    }
}
```

Chạy:

```bash
./gradlew test
```

Ba tests được **thiết kế để pass** với project Spring Boot 3.5 và full source trên; đây là **kỳ vọng đã rà soát**, **không phải kết quả Gradle đã chạy**. Test dùng `asyncDispatch` sau khi async result signal có mặt, đúng cách test MVC async được Spring document.

**Giới hạn MockMvc:** nếu một test cần chứng minh *streaming một phần trước khi hoàn tất*, đừng chỉ assert body sau `asyncDispatch`. Hãy khởi chạy `@SpringBootTest(webEnvironment=RANDOM_PORT)` và dùng client đọc response **incrementally**, ví dụ Java `HttpClient` với `BodyHandlers.ofInputStream()` hoặc HTTP/SSE client phù hợp.

## 12. Test trực tiếp trên embedded server và tránh false positive

Với Servlet streaming, một test chỉ đọc toàn bộ body sau khi kết thúc **không chứng minh** dữ liệu từng được flush sớm. Để đo thực sự:

1. Khởi chạy Boot trên port thật (RANDOM_PORT).
2. Tạo producer gửi các events cách nhau một khoảng có kiểm soát.
3. HTTP client lấy `InputStream`/SSE frames khi response còn mở.
4. Đo thời điểm nhận frame đầu tiên, không chờ toàn bộ stream.
5. Kiểm tra status/Content-Type; đóng client giữa stream.
6. Xác minh producer/registry dọn kết nối và không còn tăng memory.
7. Lặp qua reverse proxy/ingress/CDN staging.
8. Test network chậm, timeout và retry behavior.

Mẫu client Java 17 để đọc response (fragment trong test HTTP thực):

```java
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(
                "http://localhost:" + port + "/demo/sse"))
        .header("Accept", "text/event-stream")
        .GET()
        .build();

HttpResponse<InputStream> response =
        client.send(request, HttpResponse.BodyHandlers.ofInputStream());

try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(
                response.body(), StandardCharsets.UTF_8))) {
    String line;
    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
}
```

Đây là **fragment**, cần imports `java.net.http.*`, `java.net.URI`, `java.io.*`, `StandardCharsets` và biến `port` do `@LocalServerPort` test cung cấp. Ví dụ stream hữu hạn, nên vòng lặp sẽ kết thúc; stream production vô hạn cần read deadline/cancellation.

Trong test thật nên dùng timeouts/awaitility hoặc logic có bound, không dùng `Thread.sleep` vô hạn hoặc assert network timing quá chặt gây flaky CI.

## 13. Reverse proxy, browser và HTTP buffering

Một stream đúng ở controller vẫn có thể bị gom ở proxy:

```text
Producer send event 1 -----+
Producer send event 2 -----+--> Servlet output
Producer send event 3 -----+
                                |
                        NGINX / CDN buffer
                                |
                   Client nhận cả 3 lúc cuối
```

Nguyên nhân có thể là:

- Proxy `proxy_buffering`/compression làm response không flush theo events.
- CDN cache/transform, gateway timeout hoặc WAF policy không thích hợp cho long-lived connections.
- Response bị nén/buffer thành một khối trước khi gửi.
- Browser/devtools/curl output buffering.
- TCP segmentation/HTTP/2 framing không tương đương `send()` boundaries.
- Producer thực ra chỉ gửi sau khi hoàn tất công việc.
- Cấu hình executor thiếu worker, tasks bị queued quá lâu.

Các hướng kiểm tra:

- Với SSE, gửi `Content-Type: text/event-stream` và cache policy như `no-cache` thích hợp.
- Kiểm tra NGINX buffering. Header `X-Accel-Buffering: no` chỉ có tác dụng nếu proxy đó tôn trọng.
- Sử dụng `curl -N` hoặc HTTP client incremental.
- Theo dõi timestamps ở producer, Servlet write và client event receiver để xác định nơi bị chậm.
- Không tắt buffering/caching toàn hệ thống vô điều kiện; cấu hình theo streaming routes.
- Xác định proxy idle timeout và heartbeat interval hợp lý.

**Không hứa mỗi `flush()` tương ứng một network packet**. Hãy định nghĩa SLA theo thời điểm event xuất hiện ở client, và test qua đúng topology production.

## 14. Backpressure, slow consumers và giới hạn tài nguyên

### 14.1. `ResponseBodyEmitter`/`SseEmitter` không phải message broker

Nếu producer tạo 10.000 events/s nhưng client chỉ đọc được 100 events/s, ứng dụng có thể:

- Block worker trên socket writes.
- Tích lũy queue trong emitter registry/broadcaster.
- Dùng hết executor threads/heap.
- Timeout/disconnect các clients khác.

Giải pháp production:

- Giới hạn số subscribers trên user/tenant, tổng kết nối và event rate.
- Dùng queues có **capacity** thay vì unbounded lists; áp **drop/coalesce/disconnect policy** rõ ràng.
- Có metrics: active connections, queue depth, dropped events, average write latency, reconnect count.
- Với trạng thái tiến độ thường xuyên, **coalesce** thành bản cập nhật mới nhất thay vì giữ mọi event.
- Với event đòi hỏi durability, dùng DB/event log/message broker; SSE chỉ là lớp chuyển tới client.
- Kiểm tra quyền nhận từng stream và quyền truy cập sau khi reconnect.
- Tránh tạo thread riêng/scheduler mới **cho mỗi client** mà không giới hạn.
- Gỡ emitter khỏi registry ở `onCompletion`, `onTimeout`, `onError`; dừng heartbeat tương ứng.

### 14.2. `StreamingResponseBody` chịu tốc độ đọc của client

Nếu xuất file từ database hoặc object storage, mỗi download có thể giữ một worker trong suốt quá trình stream. Đừng mở `ResultSet`/DB transaction rất lâu chỉ để giữ kết nối HTTP chậm; cân nhắc:

- Tạo file tạm có kiểm soát rồi stream qua object storage/CDN.
- Export job bất đồng bộ: `POST /exports` trả `202 Accepted` và `GET /exports/{id}` trả status.
- Streaming query/cursor với timeout/batch size và transaction boundary đã thiết kế.
- Bounded connection pools/CPU/memory/IO quotas.
- Cơ chế Range/206 nếu người dùng cần resumable file download.

## 15. Bảo mật SSE và streaming

### 15.1. Authentication/authorization

Kết nối SSE có thể tồn tại lâu. Không chỉ kiểm tra lúc subscribe rồi mặc định user luôn có quyền vô hạn:

- Xác thực request ban đầu bằng Spring Security theo kiến trúc.
- Authorization theo resource/tenant cho event stream.
- Nếu quyền bị revoke trong lúc kết nối, cân nhắc disconnect hoặc giới hạn TTL và buộc reconnect/re-auth.
- Không phát events của tenant A tới subscribers của tenant B.
- Không nhét access token vào `?token=...` vì URL query có thể lọt vào browser history, logs, proxy/APM.
- Nếu cross-origin có cookie session, dùng CORS allowlist chính xác, CSRF cho các **state-changing request khác**, và đánh giá chính sách credentials.
- Tránh các response cache công khai cho streams có dữ liệu riêng.

### 15.2. Event data và frontend injection

`EventSource` chuyển `event.data` thành chuỗi. Nếu nội dung do user kiểm soát, không đưa thẳng vào `innerHTML`:

```javascript
source.addEventListener("progress", event => {
  const node = document.getElementById("progress");
  node.textContent = event.data; // Text, không parse HTML.
});
```

Đây là fragment, giả định DOM có element id `progress`. Nếu dùng JSON, parse bằng `JSON.parse` và validate schema/fields cần thiết. SSE framing không biến dữ liệu bên trong thành HTML an toàn.

### 15.3. File download headers

Với `StreamingResponseBody`:

- Đặt `Content-Type` đúng dữ liệu: `text/csv;charset=UTF-8`, `application/pdf`, `application/octet-stream`...
- Dùng `Content-Disposition: attachment` nếu muốn ép download, và encode filename bằng API thích hợp thay vì nối raw client filename vào header.
- `X-Content-Type-Options: nosniff` hữu ích khi file có thể bị browser sniff.
- Với tài liệu riêng, dùng `Cache-Control: private` hoặc `no-store`.
- Authorization phải xảy ra **trước khi mở stream**, không dựa vào file ID khó đoán là đủ.
- Khi export CSV từ dữ liệu không tin cậy, kiểm tra **formula injection** ở phần mềm bảng tính.
- Đừng log download bytes, access tokens hoặc signed URLs.

## 16. Khi nào nên chọn WebSocket hoặc WebFlux thay vì SSE/MVC streaming?

| Nhu cầu | Giải pháp nên cân nhắc |
| --- | --- |
| Browser chỉ nhận cập nhật server → client, event text | **SseEmitter** |
| Server gửi nhiều object fragments/NDJSON theo HTTP response | **ResponseBodyEmitter** |
| Gửi file/CSV/binary trực tiếp với bộ nhớ thấp | **StreamingResponseBody** |
| Một kết quả bất đồng bộ, không cần stream | `DeferredResult`/`Callable` |
| Hai chiều liên tục, client gửi events liên tục | WebSocket / STOMP tùy use case |
| Số kết nối/streams cực lớn cần reactive I/O non-blocking | Spring WebFlux hoặc gateway hỗ trợ reactive |
| Durable events/replay độc lập ứng dụng | Event broker/log + API SSE/WebSocket frontend |
| Batch export kéo dài hàng phút/giờ | Background job + object storage + download |
| Client dùng SSE nhưng cần gửi lệnh | EventSource GET + HTTP POST/PUT riêng |

SSE phù hợp nhiều dashboard/notification use cases vì đơn giản và browser hỗ trợ reconnect. Nhưng HTTP GET streaming không đáp ứng mọi giao tiếp hai chiều và không làm giảm nhu cầu broker, event retention, auth hoặc rate limits.

## 17. Lỗi thường gặp và troubleshooting

| Triệu chứng | Nguyên nhân khả dĩ | Cách kiểm tra |
| --- | --- | --- |
| Controller trả emitter nhưng browser mãi không thấy gì | Producer chưa `send`, proxy buffer, media type sai | Debug producer, `curl -N`, response headers |
| Request timeout sau vài giây | Emitter/MVC/Servlet/proxy timeout khác nhau | Log callbacks và kiểm tra proxy idle timeout |
| SSE browser reconnect liên tục sau 2 events | Stream hữu hạn hoàn tất nhưng EventSource tự retry | Client `source.close()` khi event finished |
| SSE có events nhưng `onmessage` không gọi | Có `event: progress` nên phải dùng `addEventListener("progress")` | Inspect wire frame |
| Client nhận JSON parse error | Ghép `{}{}` không newline/array delimiters | Dùng NDJSON framing rõ ràng |
| `send` ném IOException khi đóng tab | Client disconnect trong khi server write | Để container xử lý async error, cleanup registry |
| App gọi `completeWithError` sau IOException | Sai lifecycle theo Spring docs | Không gọi complete/error trong nhánh write IOException |
| Error sau khi gửi bytes vẫn trả HTTP 200 | Response committed, status không thể đổi | Thiết kế protocol error event/abort handling |
| Download chiếm nhiều RAM | `readAllBytes`/materialize output | Stream buffer qua `OutputStream` |
| CSV bị hỏng encoding | Charset mismatch hoặc writer không rõ charset | Sử dụng UTF-8 nhất quán |
| CSV mở Excel chạy công thức lạ | Formula injection từ user fields | Escape/sanitize export theo policy |
| Thread pool cạn khi nhiều SSE clients | Blocking writes/producer loops, unbounded connections | Executor metrics, connection caps, queue policy |
| Registry giữ emitter cũ mãi | Không cleanup onCompletion/error/timeout | Remove emitter, cancel heartbeat |
| MVC async không khởi động | Servlet/filter `asyncSupported=false` | Kiểm tra registrations |
| URL qua gateway trả kết quả cuối mới thấy | Proxy buffering/compression hoặc không flush | HTTP incremental test sau proxy |
| `Last-Event-ID` có nhưng bị mất events | Không có server-side replay store | Shared event log và resume policy |
| CORS lỗi trên cross-origin EventSource | Origins/credentials không khớp | Cấu hình CORS cho SSE endpoint |
| Authorization chỉ kiểm tra khi kết nối | Quyền người dùng thay đổi giữa stream | TTL/re-auth/disconnect policy |
| Test MockMvc fail vì async chưa hoàn tất | Test chưa đợi async result/dispatch | `getAsyncResult(...)` rồi `asyncDispatch(...)` |
| Stream trên WebFlux không dùng class MVC | Lẫn Servlet với reactive stack | Dùng API `Flux`/ServerSentEvent của WebFlux |

## 18. Các khác biệt theo version Spring Boot/Spring Framework

| Phiên bản | Lưu ý |
| --- | --- |
| Spring Framework 5.x + Boot 2.x | Các class `ResponseBodyEmitter`, `SseEmitter`, `StreamingResponseBody` đã tồn tại; Servlet API phần lớn dùng `javax.servlet.*` |
| Spring Framework 6.x + Boot 3.x | Java 17+, Servlet API `jakarta.servlet.*`; mã demo dùng thế hệ này |
| Spring Framework 7.x + Boot 4.x | MVC streaming concepts tiếp tục có, nhưng starter/modules/test APIs và executor auto-configuration có thể thay đổi; đọc docs/BOM đúng version |

Riêng **Spring Boot 3.5** thay đổi tên auto-configured TaskExecutor: bean tên `applicationTaskExecutor`; không còn mặc định alias `taskExecutor` như các version trước. Các ví dụ custom executor cần kiểm tra tên bean và điều kiện MVC yêu cầu kiểu `AsyncTaskExecutor`.

Spring Framework 6.2 bổ sung khả năng đăng ký nhiều callbacks trong emitter lifecycle. Nhưng sự hiện diện của callbacks không thay thế việc cleanup producer tasks nếu streams bị timeout hoặc disconnect.

Đừng trộn Java source WebFlux (`org.springframework.web.reactive.*`) với Servlet MVC (`org.springframework.web.servlet.mvc.method.annotation.*`). API tên gần nhau có thể có các hợp đồng thực thi khác nhau.

## 19. Checklist triển khai production

- [ ] Chọn đúng representation: NDJSON, SSE hay raw bytes.
- [ ] `Content-Type` và client parser tương thích với wire format.
- [ ] Producer không chạy vòng lặp vô hạn trên request/worker không được quản lý.
- [ ] Executor có giới hạn/metrics/rejection strategy; streaming writes không làm chết các workloads khác.
- [ ] Servlet và filters có `asyncSupported=true`; mapping ASYNC khi cần.
- [ ] Timeout emitter/MVC/container/proxy nhất quán với heartbeat/reconnect.
- [ ] `onCompletion`/`onError`/`onTimeout` cleanup subscribers, queues, scheduled tasks.
- [ ] Sau IOException khi gửi emitter không tự gọi `complete`/`completeWithError`.
- [ ] Khi response committed, error handling theo streaming protocol, không giả định trả status mới.
- [ ] SSE events có `id/event/data` hợp đồng rõ, Last-Event-ID/replay nếu business cần.
- [ ] EventSource client đóng stream chủ đích; dùng CORS/credentials đúng.
- [ ] Private streams authz theo user/tenant và có revocation strategy.
- [ ] Rate limit/connection cap/backpressure/slow-client policy.
- [ ] Downloads có Content-Disposition/MIME/cache/authorization thích hợp.
- [ ] Không log raw token, query secret hoặc payload nhạy cảm trong stream.
- [ ] Test MockMvc async semantics và test incremental streaming qua container thật.
- [ ] Test trên proxy/CDN cuối cùng, không chỉ localhost.
- [ ] Scale multi-node dùng shared event source khi cần replay/ordering.
- [ ] Theo dõi active streams, disconnections, errors, timeout rate, queue depth và memory.

## 20. Bài tập thực hành

1. Thay `ResponseBodyEmitter` bằng `DeferredResult<List<Progress>>`. Khác biệt về lúc client nhận từng record và memory là gì?
2. Bỏ các lệnh gửi `"\n"` trong endpoint NDJSON. Body nhận được có còn là NDJSON hợp lệ không?
3. Thay `SseEmitter.event().name("progress")` bằng không có `name`. Browser dùng `onmessage` hay `addEventListener("progress")`?
4. Giải thích khác nhau giữa `id:42` và `Last-Event-ID:42`. Spring SseEmitter có tự lưu replay events không?
5. Viết client EventSource đóng stream khi nhận event `finished`, rồi thử mở lại stream.
6. Nếu container proxy idle timeout 60 giây nhưng server chỉ gửi event mỗi 5 phút, cần heartbeat thế nào?
7. Khi `emitter.send` ném IOException, có nên gọi `completeWithError`? Nêu thứ tự xử lý lỗi do container quản lý.
8. Tại sao một server có 500 SSE connections có thể tốn threads nếu producers hoặc writes block? Đề xuất giới hạn và metrics.
9. Dùng `StreamingResponseBody` thay `Files.readAllBytes` để trả một file 2 GB. Cần xử lý Content-Length/Range/download cancellation thế nào?
10. Chỉnh CSV export để chứa giá trị bắt đầu bằng `=` từ người dùng. Rủi ro gì khi mở bằng spreadsheet? Cần escape theo policy nào?
11. Thêm test MockMvc cho cả ba endpoints dùng `asyncDispatch`; tại sao chỉ assert status trước async completion chưa đủ?
12. Tạo test HTTP thật với `HttpClient.BodyHandlers.ofInputStream` đo thời điểm nhận SSE frame đầu, so với thời điểm producer complete.
13. Khi bật Spring Security cookie authentication, cross-origin EventSource cần CORS/credentials như thế nào?
14. Trong rolling deployment, client reconnect với Last-Event-ID đến node khác; cần lưu state ở đâu?
15. So sánh dùng SSE, WebSocket, WebFlux khi dashboard cần cập nhật 1 lần/giây và khi game cần 2 chiều 60 lần/giây.
16. Nếu `@Async` method muốn lấy `RequestContextHolder` hoặc MDC, vì sao không tự có? Nêu cách truyền immutable context an toàn.
17. Khi ứng dụng trả ResponseEntity<SseEmitter>, header nào nên cấu hình trước first send và vì sao response committed làm thay đổi header thất bại?
18. Viết test fail-fast khi executor hết queue capacity, và chọn HTTP status/retry policy phù hợp cho clients.

## 21. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Asynchronous Requests and HTTP Streaming](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html): Servlet async lifecycle, ResponseBodyEmitter, SseEmitter, StreamingResponseBody, disconnects và cấu hình.
- [Spring Framework 6.2 — ResponseBodyEmitter Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseBodyEmitter.html): send, timeout, complete, error, lifecycle callbacks.
- [Spring Framework 6.2 — SseEmitter Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.html): SSE event builder, id/event/data/retry.
- [Spring Framework 6.2 — StreamingResponseBody Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/StreamingResponseBody.html): writeTo(OutputStream) và async execution.
- [Spring Framework 6.2 — AsyncSupportConfigurer Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/config/annotation/AsyncSupportConfigurer.html): MVC executor và default timeout.
- [Spring Boot 3.5 — Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html): applicationTaskExecutor, ThreadPoolTaskExecutor, Boot defaults.
- [Spring Framework — MockMvc Async Requests](https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/async-requests.html): asyncResult, asyncDispatch.
- [MDN — Using Server-Sent Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events): EventSource, SSE format, retry, Last-Event-ID và comments.
- [Spring MVC — Controller Return Values](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html): các kiểu async return value.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-async-processing-006">Trả về kiểu reactive trong MVC và giới hạn khi ghi phản hồi</a>

<details>
<summary>Xem chi tiết</summary>

# Trả về kiểu reactive trong Spring MVC và giới hạn khi ghi phản hồi

> **Phạm vi:** Spring Web MVC trên Servlet stack, Java 17+ và Spring Boot 3.x/Spring Framework 6.x; nội dung vẫn có giá trị nền tảng với Spring Framework 7.x nhưng cấu hình dependency của Boot 4 có thể khác. Bài giải thích cách MVC chấp nhận `Mono<T>`, `Flux<T>`, `Publisher<T>` hoặc reactive types được `ReactiveAdapterRegistry` hỗ trợ; phân biệt **giá trị đơn**, **streaming SSE/NDJSON** và **tập hợp JSON thông thường**; làm rõ điều dễ nhầm nhất: **nguồn reactive không làm cho Servlet response write của Spring MVC trở thành non-blocking I/O**. Có một demo Java 17 Spring Boot 3.x chạy được, cách cấu hình `AsyncTaskExecutor`, cURL, MockMvc, giới hạn bộ nhớ và xử lý lỗi/ngắt kết nối.

## 1. Vấn đề: muốn dùng nguồn reactive trong ứng dụng MVC

Một dịch vụ REST dùng Spring MVC nhưng lấy dữ liệu từ Reactor `Mono`, `Flux`, một reactive repository hoặc `WebClient`. Câu hỏi thường gặp:

- `@RestController` có thể trả trực tiếp `Mono<Book>` thay cho `Book` không?
- Trả `Flux<Book>` có đồng nghĩa với gửi từng phần tử ngay đến browser không?
- Khi response là `application/json`, Spring có giữ toàn bộ `Flux` trong bộ nhớ trước khi trả không?
- Nếu client chậm hoặc ngắt kết nối, backpressure hoạt động đến mức nào?
- Cần bao nhiêu thread khi chạy 1.000 kết nối SSE?
- Giữa Spring MVC và WebFlux, điểm khác nhau của cách viết response là gì?

**Trả lời ngắn:** Spring MVC hỗ trợ reactive return types. Nó điều phối thông qua Servlet asynchronous request processing; `Mono` thường được thích ứng như `DeferredResult`, `Flux` chọn giữa **streaming** hoặc **collection** tùy media type. Khi ghi response thực tế, Spring MVC vẫn sử dụng **blocking writes trên một thread/executor dành riêng**, không chuyển sang mô hình WebFlux non-blocking HTTP I/O. Nếu chưa cấu hình executor, buffer, timeout và giới hạn nguồn, app vẫn có thể nghẽn hoặc hết RAM.

## 2. Sơ đồ luồng dữ liệu

```text
HTTP client
     |
Servlet container (Tomcat)
     |
DispatcherServlet + HandlerAdapter
     |
@GetMapping trả Mono<T> / Flux<T>
     |
ReactiveAdapterRegistry
     |
     +--> Mono<T> / single value
     |      -> DeferredResult-style async processing
     |      -> một giá trị / ResponseEntity / JSON / error
     |
     +--> Flux<T> + application/json
     |      -> collect values thành List-like result
     |      -> Jackson serializes JSON array sau khi hoàn tất
     |
     +--> Flux<T> + text/event-stream hoặc application/x-ndjson
            -> emitter-style streaming
            -> encode và ghi từng phần/chunk
            -> blocking ServletResponse write qua AsyncTaskExecutor
     |
HTTP response / Servlet output stream
```

**Điểm then chốt:** không được đánh đồng **Reactive Streams backpressure ở publisher/subscriber** với **non-blocking socket write**. Spring MVC có thể giới hạn tốc độ yêu cầu tiếp từ publisher để phù hợp quá trình write, nhưng mỗi lần viết xuống Servlet response vẫn có thể block một worker thread. <https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html>

## 3. Reactive Streams và Reactor types

### 3.1. Mono và Flux

| Kiểu | Cardinality | Ví dụ ý nghĩa |
| --- | --- | --- |
| `Mono<T>` | 0 hoặc 1 giá trị, hoặc error | Tải thông tin một tài khoản |
| `Flux<T>` | 0 đến N giá trị, có thể hữu hạn hoặc vô hạn | Danh sách, thay đổi theo thời gian, sự kiện |
| `Publisher<T>` | Giao diện Reactive Streams | Abstraction của nguồn phát |
| `CompletionStage<T>` | Một kết quả bất đồng bộ JDK | Future-like result |
| `Single<T>` (RxJava) | Một giá trị | Reactive adapter khác khi thư viện có mặt |

`Mono.just(value)` không tự làm I/O non-blocking. `Mono.fromCallable(() -> blockingRepository.find(...))` vẫn chạy đoạn code blocking trên thread đăng ký nếu không chuyển scheduler phù hợp. Reactor cung cấp `subscribeOn(Schedulers.boundedElastic())` cho những nguồn blocking có kiểm soát, nhưng chuyển thread chỉ giúp tránh block event-loop; nó không làm bản thân JDBC hay Servlet I/O non-blocking.

### 3.2. ReactiveAdapterRegistry

Spring `org.springframework.core.ReactiveAdapterRegistry` kết nối reactive libraries với hạ tầng MVC. Nó nhận biết semantics một giá trị/nhiều giá trị và thích ứng thành chuỗi dữ liệu. Reactor là lựa chọn phổ biến; RxJava cũng có thể được hỗ trợ nếu thư viện và adapter phù hợp có mặt.

Đừng cố viết:

```java
// Anti-pattern: subscribe trong controller và trả null.
@GetMapping("/bad")
public Object bad() {
    service.getBook().subscribe(book -> System.out.println(book));
    return null;
}
```

Đoạn code minh họa này tách subscription khỏi response lifecycle; kết quả có thể đến sau khi handler đã kết thúc, lỗi/cancellation khó gắn với HTTP response. **Trả thẳng publisher** cho MVC:

```java
@GetMapping("/good")
public Mono<BookView> good() {
    return service.getBook();
}
```

Đây là snippet; cần service/DTO được định nghĩa riêng. Khi framework quản lý subscription, nó có thể tích hợp timeout, exception dispatch, message conversion và hủy subscription trong các trường hợp phù hợp.

## 4. Một giá trị: Mono<T> hoạt động như kết quả bất đồng bộ

Ví dụ:

```java
@RestController
class BookController {
    @GetMapping("/api/book")
    Mono<BookView> book() {
        return Mono.just(new BookView(1L, "Spring MVC"));
    }
}
```

Đây là fragment, cần định nghĩa `BookView` và imports. `@RestController` khiến giá trị được đưa ra HTTP response body; `Mono<BookView>` không bị serialize thành JSON mô tả Mono, mà MVC **đợi giá trị thực** do publisher phát.

Về mặt hạ tầng, single-value reactive return được thích ứng tương tự `DeferredResult<T>`:

1. Controller trả publisher, Spring MVC đăng ký xử lý bất đồng bộ.
2. Servlet request chuyển vào async mode; request thread ban đầu có thể được trả về pool.
3. Mono phát `onNext(book)` rồi `onComplete` hoặc `onError`.
4. Spring MVC tiếp tục xử lý kết quả qua async dispatch và ghi body bằng `HttpMessageConverter`.
5. Sau khi response hoàn thành, async lifecycle kết thúc.

**Lưu ý:** `Mono.just(...)` có thể phát giá trị ngay tại lúc subscribe; asynchronous return type không đảm bảo công việc thực sự chạy trên thread khác hoặc thao tác bên trong không blocking. Nếu muốn minh họa giá trị đến muộn, dùng `Mono.delay(...)` hoặc một async service thật.

### 4.1. Mono.empty()

`Mono.empty()` không phát một `T`. Trạng thái HTTP mặc định có thể phụ thuộc method signature, Spring MVC return handler và annotation/status do ứng dụng chọn. Đừng coi `Mono.empty()` luôn là HTTP 404. Nếu business contract là “không tìm thấy”, phải ánh xạ thành lỗi hoặc `ResponseEntity.notFound()` rõ ràng.

```java
@GetMapping("/api/books/{id}")
Mono<ResponseEntity<BookView>> find(@PathVariable long id) {
    return repository.findById(id)
            .map(book -> ResponseEntity.ok(toView(book)))
            .defaultIfEmpty(ResponseEntity.notFound().build());
}
```

Snippet này yêu cầu reactive repository/mapper; mục đích là cho thấy HTTP status được chọn sau khi biết kết quả.

### 4.2. Khi nào Status/Header được quyết định?

Hai cấu trúc khác nhau:

```java
// Status và headers được xác định ngay.
ResponseEntity<Mono<BookView>> immediateHeaders() {
    return ResponseEntity.accepted()
            .header("X-Demo", "value")
            .body(Mono.just(new BookView(1L, "Example")));
}
```

```java
// Toàn ResponseEntity được quyết định bất đồng bộ.
Mono<ResponseEntity<BookView>> deferredHeaders() {
    return Mono.just(
            ResponseEntity.ok(new BookView(1L, "Example")));
}
```

Hai method trên là snippets độc lập, không có `@GetMapping`. Với `ResponseEntity<Mono<T>>`, server biết status/headers sớm, body đến sau. Với `Mono<ResponseEntity<T>>`, status, headers và body đều được quyết định khi Mono hoàn thành. Trong thực tế, nếu cần trả 404/409 tùy dữ liệu bất đồng bộ, `Mono<ResponseEntity<T>>` biểu đạt tốt hơn vì status chưa bị khóa ở thời điểm tạo controller return value.

## 5. Nhiều giá trị: Flux<T> KHÔNG luôn streaming

Cùng một `Flux<BookView>` có thể có hai hành vi khác nhau theo **media type phản hồi**.

### 5.1. Flux + application/json: thu thập rồi serialize một JSON array

```java
@GetMapping(path = "/api/books",
            produces = MediaType.APPLICATION_JSON_VALUE)
Flux<BookView> books() {
    return Flux.just(
            new BookView(1, "A"),
            new BookView(2, "B"));
}
```

Spring MVC thích ứng multi-value reactive type với non-streaming media type như `application/json` thành kết quả tương tự `DeferredResult<List<?>>`. Khi publisher hoàn tất, MVC serialize danh sách thành một JSON array:

```json
[{"id":1,"title":"A"},{"id":2,"title":"B"}]
```

**Không phát từng object ngay khi có phần tử** theo nghĩa SSE/NDJSON. Với nguồn rất lớn, `collectList`-style buffering có thể tăng memory theo số phần tử. Với nguồn vô hạn, response dạng JSON array **không có điểm hoàn tất**; client có thể phải đợi mãi hoặc timeout, trong khi bộ nhớ tiếp tục tăng.

**Lỗi hay gặp:** trả `Flux.interval(...)` vô hạn với `produces="application/json"` và tưởng browser sẽ thấy sự kiện liên tục. Chọn streaming media type hoặc giới hạn/tổng hợp dữ liệu phù hợp.

### 5.2. Flux + text/event-stream: SSE

```java
@GetMapping(path = "/api/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
Flux<ServerSentEvent<String>> events() {
    return Flux.just(
            ServerSentEvent.builder("first")
                    .id("1").event("tick").build(),
            ServerSentEvent.builder("second")
                    .id("2").event("tick").build());
}
```

Đây là snippet; cần Reactor và `org.springframework.http.codec.ServerSentEvent`. SSE có framing dạng text như:

```text
id:1
event:tick
data:first

id:2
event:tick
data:second

```

Thứ tự/cách ghi các fields và line endings tùy serializer; mẫu thể hiện ý nghĩa sự kiện, không phải output byte-for-byte của mọi Spring version.

Với SSE, Spring MVC thích ứng reactive stream thành emitter-style response. Khi có phần tử, MVC có thể serialize và flush để client nhận dần, nếu reverse proxy/network không buffer. Cần dùng client như `curl -N` để tránh buffer stdout khi kiểm thử.

### 5.3. Flux + application/x-ndjson: mỗi phần tử là một dòng JSON

```java
@GetMapping(path = "/api/records",
            produces = "application/x-ndjson")
Flux<BookView> records() {
    return Flux.just(
            new BookView(1, "A"),
            new BookView(2, "B"));
}
```

**Snippet minh họa định dạng**: để output thực sự là NDJSON, cần `HttpMessageConverter`/Jackson configuration hỗ trợ media type trên phiên bản đang dùng. Khi streaming được chọn, response theo định dạng NDJSON thường là:

```text
{"id":1,"title":"A"}
{"id":2,"title":"B"}
```

Mỗi dòng là một JSON value hoàn chỉnh; khác với mảng JSON truyền thống. Với bản Spring/container/converter cụ thể, cần test serialization và flush thực tế, không chỉ tin `produces`. Một hệ thống có thể cần cấu hình thêm media types cho converter.

### 5.4. Bảng chọn output shape

| Return type | Media type / mapping | Hành vi phổ biến trong MVC | Phù hợp |
| --- | --- | --- | --- |
| `Mono<Book>` | `application/json` | Một body JSON sau khi Mono ra kết quả | Tra cứu một item |
| `Flux<Book>` | `application/json` | Gom toàn bộ thành List/JSON array | Danh sách **hữu hạn, có giới hạn** |
| `Flux<Book>` | `application/x-ndjson` | Từng JSON object theo dòng | Stream nhiều bản ghi |
| `Flux<ServerSentEvent<T>>` | `text/event-stream` | Phát SSE events dần | Notification/telemetry |
| `Flux<T>` | Streaming type khác có converter tương thích | Phụ thuộc converter & media configuration | Streaming chuyên biệt |
| `ResponseEntity<Flux<T>>` | Streaming type | Status/headers xác định trước, body reactive | Stream có metadata tĩnh |
| `Mono<ResponseEntity<T>>` | Non-streaming | Response status/headers/body bất đồng bộ | HTTP status phụ thuộc kết quả |

**Ghi nhớ:** `Flux<T>` là cấu trúc dữ liệu reactive; **media type** mới quyết định MVC có xử lý nó như stream từng phần hay gom lại trước khi render.

## 6. Backpressure trong MVC và giới hạn chỗ ghi response

Reactive Streams cho phép downstream yêu cầu các phần tử từ upstream qua `Subscription.request(n)`. Trong Spring MVC streaming bridge:

```text
Reactor Publisher (có backpressure)
           |
           v
ReactiveAdapter / Subscriber
           |
           v
Spring MVC emitter/queue
           |
           v
AsyncTaskExecutor worker
           |
           v
HttpMessageConverter
           |
           v
ServletOutputStream.write(...)     <-- BLOCKING khi client/network chậm
           |
HTTP client
```

Spring MVC hỗ trợ backpressure khi streaming reactive values để không vô tội vạ lấy hàng triệu phần tử trong lúc network chưa xử lý xong. Tuy nhiên **blocking write không biến mất**: một worker thread có thể mắc kẹt chờ socket/container/network xử lý. Nếu có rất nhiều clients chậm hoặc kết nối SSE lâu dài, số lượng worker cần thiết và mức đệm có thể trở thành bottleneck.

### 6.1. Backpressure không đảm bảo mọi Publisher an toàn

Ví dụ `Flux.interval(Duration.ofMillis(5))` phát sự kiện theo đồng hồ. Nếu downstream không theo kịp, một số source có thể báo overflow/cancellation khi không có demand; hot source như topic/message broker có thể có drop/buffer policy khác. Cần quy định:

- `limitRate` hoặc demand policy khi phù hợp;
- `onBackpressureBuffer(maxSize, overflowStrategy)` có **giới hạn rõ**, không buffer vô hạn;
- `onBackpressureDrop`/`onBackpressureLatest` khi **nghiệp vụ cho phép mất sự kiện**;
- timeout, cancellation và giới hạn tổng số events;
- client reconnection/resume semantics với SSE event IDs nếu cần.

**Không đưa cách drop dữ liệu vào luồng thanh toán hoặc audit** chỉ để giảm RAM; các luồng này cần delivery semantics khác.

### 6.2. Reactive không đồng nghĩa non-blocking khi gọi service

```java
// Anti-pattern: hàm JDBC blocking chạy trên thread phát subscription.
return Mono.fromCallable(() -> jdbcRepository.findById(id));
```

Nếu source chạy trên event loop, có thể block event loop. Với đoạn code blocking tạm thời:

```java
return Mono.fromCallable(() -> jdbcRepository.findById(id))
        .subscribeOn(Schedulers.boundedElastic());
```

Snippet này chuyển công việc sang scheduler giới hạn tương đối; **không biến JDBC thành async driver**. Với tải cao, có thể cần reactive driver, connection pool tuning hoặc giữ service MVC/blocking thông thường. Việc chọn MVC hay WebFlux phải dựa trên toàn pipeline thực tế, không chỉ return type.

## 7. Vì sao Spring MVC không trở thành WebFlux?

| Tiêu chí | Spring MVC + Mono/Flux return | Spring WebFlux |
| --- | --- | --- |
| Foundation | Servlet API + DispatcherServlet | Reactive stack + WebHandler |
| Controller return | Mono/Flux được adapter hỗ trợ | Native reactive pipeline |
| Request body parsing | MVC `HttpMessageConverter` và Servlet request | Reactive `HttpMessageReader` / `Flux<DataBuffer>` |
| Streaming writes | **Blocking Servlet output writes** trên async executor | Non-blocking I/O theo runtime hỗ trợ |
| Backpressure | Có tại reactive publisher bridge | Xuyên nhiều phần của reactive pipeline |
| Servlet thread | Được trả về khi async request bắt đầu, thread khác có thể ghi | Event-loop/non-blocking approach |
| MVC form binding | `@ModelAttribute`/Servlet binding | Reactive-aware APIs khác |
| Streaming CPU/thread cost | Thread bị giữ khi blocking write | Mô hình resource khác, nhưng vẫn phải giới hạn nguồn |
| Chuyển từ MVC sang WebFlux | Không xảy ra chỉ vì trả Flux | Cần chọn Spring reactive web runtime/application stack |

Spring MVC không hỗ trợ `Mono<@RequestBody T>` như một tham số handler reactive được stream ở mức framework contract, và không có explicit reactive model-attribute support tương đương WebFlux. **Return reactive** không đồng nghĩa toàn bộ request path non-blocking.

### 7.1. Có thể thêm Reactor mà vẫn chạy MVC không?

Có. Với Spring Boot 3.x, dependency chính cho server MVC là `spring-boot-starter-web`; thêm `io.projectreactor:reactor-core` nếu muốn dùng Mono/Flux. Bạn **không** cần thêm `spring-boot-starter-webflux` chỉ để trả Reactor types trong MVC. Spring Boot nếu có cả MVC và WebFlux starters thường ưu tiên servlet MVC auto-configuration, nhưng tránh thêm stack không cần thiết để giảm nhiễu.

Nếu cần `WebClient` thực tế, thư viện `spring-webflux` và Reactor Netty/HTTP client tương ứng có thể được thêm như client dependencies; việc có WebClient không ép controller server chuyển sang WebFlux.

### 7.2. Khi nào nên chuyển WebFlux?

Cân nhắc WebFlux khi:
- Có rất nhiều kết nối streaming chậm/đồng thời mà blocking write threads là vấn đề thực tế.
- Backend/call chain dùng non-blocking clients/drivers, tránh block ở mỗi bước.
- Đội ngũ có khả năng thiết kế cancellation, backpressure, errors, context propagation và testing reactive end-to-end.

Giữ MVC khi:
- Ứng dụng chủ yếu dùng blocking JDBC/JPA/Servlet integrations.
- Nhu cầu async vài kết quả đơn hoặc SSE quy mô vừa.
- Muốn tận dụng hệ MVC hiện hữu, dễ gỡ lỗi, và executor/capacity phù hợp.

Không nên chuyển framework chỉ vì trả `Flux` thấy “hiện đại hơn”; hãy đo tải, memory, threads và latency thực tế.

## 8. Cấu hình AsyncTaskExecutor cho response write

Tài liệu Spring MVC nhấn mạnh: khi streaming reactive, framework thực hiện blocking writes bằng `AsyncTaskExecutor` riêng để tránh block upstream source. Executor mặc định dành cho async support **không phù hợp tải production**; cần cấu hình có giới hạn và tính toán capacity.

Ví dụ `WebMvcConfigurer#configureAsyncSupport`:

```java
@Configuration
class MvcAsyncConfig implements WebMvcConfigurer {

    @Bean
    ThreadPoolTaskExecutor mvcAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mvc-async-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(64);
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncExecutor());
        configurer.setDefaultTimeout(15_000L);
    }
}
```

Đây là snippet class, cần import `@Configuration`, `@Bean`, `WebMvcConfigurer`, `AsyncSupportConfigurer`, `ThreadPoolTaskExecutor`. Phần ví dụ hoàn chỉnh ở mục 9 có đầy đủ imports và method.

**Quan trọng:**
- `@Configuration` bean methods mặc định được Spring proxy để cùng một bean được tái sử dụng khi gọi `mvcAsyncExecutor()` từ config instance; nếu thay đổi `proxyBeanMethods=false`, cần inject instance thay vì tự gọi method.
- `corePoolSize/maxPoolSize/queueCapacity` là thông số ví dụ, không phải chuẩn universal. Khi quá tải, task có thể bị từ chối; cần metrics, capacity planning, fallback và quy tắc trả lỗi trước khi response committed.
- `setDefaultTimeout(15_000L)` áp một default request async timeout; đối với SSE lâu hơn 15 giây cần cấu hình timeout phù hợp. Không sao chép timeout demo vào production long-lived stream một cách máy móc.
- Một thread blocking ghi cho client chậm có thể bị giữ lâu. Executor pool 8 threads không có nghĩa server xử lý vô hạn 10.000 SSE streams mà không có latency/queue issues.
- Nếu dùng Spring Security/Micrometer context, phải xem thêm context propagation qua async threads.

### 8.1. Async Servlet dispatch/filter config

Spring MVC async dựa trên Servlet 3.0+ async capability. Các Filters cần áp dụng đúng dispatcher types và `asyncSupported`. Trong ứng dụng Boot thông thường, embedded Servlet container/DispatcherServlet được cấu hình thích hợp; với deployment truyền thống/custom filter registration, phải tự xác minh:

```xml
<!-- Snippet web.xml cho ứng dụng Servlet truyền thống -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>
        org.springframework.web.servlet.DispatcherServlet
    </servlet-class>
    <async-supported>true</async-supported>
</servlet>
```

Với filter cần tham gia async dispatch, đặt `<async-supported>true</async-supported>` và mapping có `<dispatcher>ASYNC</dispatcher>` theo yêu cầu. Nếu một filter không hỗ trợ async nằm trong chain, `request.startAsync()` có thể không chạy đúng. Async dispatch không giống một request mới từ client; các `HandlerInterceptor` callbacks cũng có lifecycle đặc biệt.

## 9. Ví dụ hoàn chỉnh, chạy được: Spring Boot 3.x / Java 17

Ví dụ có ba endpoint:
- `GET /api/mono`: trả một JSON object trễ bằng `Mono<Message>`.
- `GET /api/array`: trả `Flux<Message>` với `application/json`, tạo một JSON array sau khi publisher hoàn thành.
- `GET /api/events`: `Flux<ServerSentEvent<String>>` + `text/event-stream`, gửi ba SSE events theo thời gian.

Demo chỉ dùng dữ liệu hữu hạn, không có database hay authentication. Executor được cấu hình có giới hạn; timeout đủ cho 3 events. Không thêm WebFlux starter để server vẫn là MVC.

### 9.1. Maven dependencies

Tạo Maven Spring Boot **3.5.x** project từ [Spring Initializr](https://start.spring.io/), Java 17, chọn **Spring Web** rồi thêm Reactor Core theo Spring Boot BOM:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-core</artifactId>
    </dependency>
</dependencies>
```

Đây là fragment của `pom.xml` đã dùng Spring Boot parent/BOM; không đặt Reactor version bằng một số tùy ý trái BOM. `spring-boot-starter-web` bao gồm Spring MVC, Jackson và embedded Tomcat mặc định. Reactor Core cung cấp `Mono`, `Flux` và schedulers.

### 9.2. Cấu trúc source

```text
src/main/
└── java/
    └── com/example/demo/
        └── DemoApplication.java
```

Đặt toàn bộ Java source dưới đây vào file duy nhất `src/main/java/com/example/demo/DemoApplication.java`.

### 9.3. Java hoàn chỉnh

```java
package com.example.demo;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

record Message(int id, String text) {
}

@Configuration
class MvcAsyncConfiguration implements WebMvcConfigurer {

    @Bean
    public ThreadPoolTaskExecutor mvcAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mvc-write-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(64);
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(
            AsyncSupportConfigurer configurer) {

        configurer.setTaskExecutor(mvcAsyncExecutor());
        configurer.setDefaultTimeout(15_000L);
    }
}

@RestController
@RequestMapping("/api")
class ReactiveReturnController {

    @GetMapping(
            path = "/mono",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Message> single() {
        return Mono.delay(Duration.ofMillis(100))
                .map(tick -> new Message(1, "ready"));
    }

    @GetMapping(
            path = "/array",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<Message> array() {
        return Flux.fromIterable(List.of(
                new Message(1, "alpha"),
                new Message(2, "beta"),
                new Message(3, "gamma")))
                .delayElements(Duration.ofMillis(100));
    }

    @GetMapping(
            path = "/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> events() {
        return Flux.interval(Duration.ofMillis(200))
                .take(3)
                .map(i -> ServerSentEvent.<String>builder()
                        .id(Long.toString(i))
                        .event("tick")
                        .data("event-" + i)
                        .build());
    }
}
```

### 9.4. Vì sao ví dụ là MVC, không phải WebFlux?

- `spring-boot-starter-web` kích hoạt Servlet MVC và embedded Tomcat; không có WebFlux server starter.
- Reactor `Flux/Mono` chỉ là loại return values được MVC adapt.
- `MvcAsyncConfiguration` đăng ký `AsyncTaskExecutor` cho Servlet async and writes.
- `ServerSentEvent<String>` giúp MVC tạo SSE formatting; class nằm trong `org.springframework.http.codec` do Spring Web cung cấp.
- `Mono.delay` và `Flux.interval` mô phỏng async publisher, không block handler thread bằng `Thread.sleep()`.
- `/array` có ba phần tử hữu hạn, nên gom thành JSON array an toàn trong demo.
- `/events` có `take(3)` nên sẽ kết thúc; production long-lived SSE cần timeout, heartbeat, reconnection và limit phù hợp.
- Không có lệnh `.block()` hoặc `.subscribe()` thủ công trong controller; MVC quản lý subscriptions và response lifecycle.

### 9.5. Chạy

```bash
# Linux/macOS
./mvnw spring-boot:run

# Windows PowerShell / Command Prompt
mvnw.cmd spring-boot:run
```

Nếu không cấu hình port, API chạy ở `http://localhost:8080`.

### 9.6. Kết quả Mono

```bash
curl -i 'http://localhost:8080/api/mono'
```

Phần body dự kiến:

```json
{"id":1,"text":"ready"}
```

HTTP status `200 OK`, content type JSON. Giá trị được Mono phát sau một khoảng thời gian nhỏ, nhưng độ trễ thực tế tùy scheduler/server. Không lấy 100 ms làm SLA.

### 9.7. Kết quả Flux JSON array

```bash
curl -i 'http://localhost:8080/api/array'
```

Body sau khi tất cả phần tử được thu thập:

```json
[
  {"id":1,"text":"alpha"},
  {"id":2,"text":"beta"},
  {"id":3,"text":"gamma"}
]
```

JSON thực tế thường trên một dòng và không có whitespace như trên. Quan trọng là **một JSON array hoàn chỉnh**, không phải ba response riêng biệt. Vì MVC gom Flux cho media type `application/json`, client thường không nhận phần tử ngay từ 100 ms đầu.

### 9.8. Kết quả SSE

```bash
curl -N -i \
  -H 'Accept: text/event-stream' \
  'http://localhost:8080/api/events'
```

Một response có 3 events, với các trường tương đương:

```text
id:0
event:tick
data:event-0

id:1
event:tick
data:event-1

id:2
event:tick
data:event-2

```

Thông thường chúng được phát ra theo thời gian, không cần chờ đủ 3. `curl -N` tắt output buffering của curl; nếu có Nginx/CDN hoặc compression buffering, event có thể đến từng cụm thay vì từng mốc. Để kiểm tra streaming thực, cần đo thời điểm nhận từng event ở client, không chỉ xem body cuối.

### 9.9. Windows PowerShell

PowerShell có thể map `curl` thành alias; dùng `curl.exe`:

```powershell
curl.exe -i "http://localhost:8080/api/mono"
curl.exe -i "http://localhost:8080/api/array"
curl.exe -N -i -H "Accept: text/event-stream" "http://localhost:8080/api/events"
```

### 9.10. Không giữ kết nối SSE vĩnh viễn bằng demo

Demo `take(3)` kết thúc nhanh để có kết quả kiểm chứng. Nếu đổi thành `Flux.interval(...)` không giới hạn:

- `async timeout` 15 giây của demo có thể kết thúc request;
- reverse proxy/ingress/load balancer có idle timeout riêng;
- mỗi slow writer có thể chiếm async executor thread;
- browser EventSource thường tự reconnect khi connection đóng;
- event IDs, resume logic, replay bounds và per-client resource limits cần được thiết kế.

Không thay `take(3)` bằng `retry()` vô hạn để “tự sửa” mọi lỗi: có thể tạo infinite loop, duplicate events hoặc quá tải source.

## 10. Kiểm thử async với MockMvc

Với Spring Boot **3.5.x**, thêm test dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

Đặt trong `src/test/java/com/example/demo/ReactiveMvcTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReactiveMvcTest {

    @Autowired
    MockMvc mvc;

    @Test
    void monoReturnsJsonAfterAsyncDispatch() throws Exception {
        MvcResult pending = mvc.perform(get("/api/mono"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.text").value("ready"));
    }

    @Test
    void fluxWithNormalJsonIsCollectedAsArray() throws Exception {
        MvcResult pending = mvc.perform(get("/api/array"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("alpha"))
                .andExpect(jsonPath("$[2].text").value("gamma"));
    }
}
```

**Giải thích test:**
- Reactive return types được xử lý theo Spring MVC async infrastructure.
- Với MockMvc, cần xác nhận `request().asyncStarted()` và gọi `asyncDispatch(...)` để nhận response hoàn chỉnh trong test; assert trực tiếp trên request ban đầu có thể sai vì kết quả chưa có.
- Test JSON array quan sát việc Flux được gom trước khi serialize.
- SSE cần kiểm tra riêng media type/event framing và streaming timing ở môi trường servlet/container thật; chỉ một MockMvc response cuối **không chứng minh network streaming**.

Chạy:

```bash
# macOS / Linux
./mvnw test

# Windows
mvnw.cmd test
```

Test `Mono.delay`/`Flux.delayElements` dựa vào real scheduler; nếu CI bị quá chậm hoặc timeout, tăng giới hạn test một cách có lý do hoặc dùng virtual scheduler trong unit test Reactor, không lạm dụng `Thread.sleep(...)` trong test.

## 11. Streaming errors: khi đã commit, không thể quay lại đổi HTTP status

Với Mono single value, nếu lỗi xảy ra **trước khi** MVC ghi headers/body, `@ExceptionHandler` hoặc `@RestControllerAdvice` có thể tạo 4xx/5xx response. Ví dụ:

```java
@GetMapping("/api/error")
Mono<Message> failEarly() {
    return Mono.error(new IllegalStateException("upstream unavailable"));
}
```

Đây là snippet controller; cần error policy. Nếu error được xử lý trước khi response committed, Spring MVC có thể dispatch lỗi qua exception resolvers.

**Nhưng với streaming Flux**, server có thể đã gửi các events đầu:

```text
event:tick
data:first

event:tick
data:second

... publisher errors ...
```

Lúc đó HTTP headers/status có thể đã committed. Server **không thể đảm bảo** chuyển status 200 thành 500, đổi Content-Type hay thay cả body bằng JSON lỗi chuẩn. Hành vi có thể là đóng connection, hủy stream, log exception hoặc báo lỗi bằng **event/application protocol** nếu response còn ghi được và thiết kế cho phép.

### 11.1. Giao thức báo lỗi trong stream

Với SSE, có thể định nghĩa event riêng:

```text
event:app-error
data:{"code":"UPSTREAM_LOST","message":"Retry later"}

```

Đây là **giao thức ứng dụng** trong body SSE, không phải chuyển HTTP status đã gửi thành 500. Nếu client cần biết lỗi đáng tin, nó phải hiểu event `app-error` và có chiến lược retry/backoff. Không đưa stacktrace, tokens hay thông tin hệ thống vào error events.

### 11.2. Khi response chưa committed

Với `Mono<ResponseEntity<T>>`, response metadata được quyết định cùng async result; vì vậy dễ chọn status theo dữ liệu hơn `ResponseEntity<Mono<T>>`. Với `Flux` đã stream, status thường cố định trước khi tất cả phần tử có mặt. Nên validate/auth trước khi bắt đầu stream và kiểm tra nguồn availability/permissions phù hợp để giảm nguy cơ lỗi muộn.

### 11.3. Client disconnect

Nếu client đóng tab/ngắt kết nối, writer có thể ném `IOException` hoặc loại exception đặc thù container; publisher subscription/cancellation cần được quản lý để không giữ task/connection sau khi response đã chết. Reactor cung cấp `doOnCancel`/`doFinally` để quan sát cleanup, ví dụ:

```java
Flux<Long> events = Flux.interval(Duration.ofSeconds(1))
        .doFinally(signal -> {
            // Cleanup subscription-scoped resources nếu có.
            // Không log token/session/cookie thô.
        });
```

Snippet này không thay một chính sách cleanup đầy đủ nếu upstream là hot stream/shared subscription hoặc có database cursor. Cần test disconnect qua network thật.

### 11.4. Timeout và response đã ghi dở

Timeout của Servlet async request, gateway read/write timeout và upstream publisher timeout **khác nhau**. Khi timeout xảy ra sau khi đã stream bytes, error handler thường không còn thay status/body được. Với Flux vô hạn, hãy xác định chính sách long-lived connection, heartbeat, idle timeout, maximum duration, retry và quota thay vì tin một default timeout.

## 12. Threading, context propagation và bảo mật

### 12.1. ThreadLocal không tự xuyên qua mọi async boundary

Trong Servlet MVC đồng bộ, `RequestContextHolder`, MDC và một số security context có thể gắn với request thread. Khi reactive pipeline chuyển sang scheduler hoặc Servlet async write thread, **không nên mặc định** ThreadLocal còn nguyên.

Spring có tích hợp Micrometer Context Propagation khi library tương ứng hiện diện và `ThreadLocalAccessor` được đăng ký; các accessor như Locale/Request attributes có thể cần đăng ký có chủ đích. Reactive `Context` và ThreadLocal là hai cơ chế khác nhau.

**Không làm:**

```java
return Mono.delay(Duration.ofMillis(50))
        .map(tick -> SecurityContextHolder.getContext()
                .getAuthentication()
                .getName());
```

Nếu không có context propagation đúng, bạn có thể thấy null hoặc security context không mong muốn. Về bảo mật, hãy đưa ID/tenant đã xác thực qua service API có kiểm soát hoặc dùng context propagation chuẩn, không truyền toàn bộ token bí mật vào global static state.

### 12.2. Authorization cần diễn ra trước stream

- Xác thực request bằng Spring Security filter chain.
- Kiểm tra user có quyền subscribe đúng channel/resource.
- Khi stream phát dữ liệu đa tenant, mỗi event phải thuộc quyền phù hợp hoặc stream phải được tạo từ scope đã được kiểm tra.
- Nếu quyền bị revoke trong một kết nối dài, quyết định cách cập nhật quyền/đóng stream; initial auth không mặc định bảo vệ vô thời hạn.
- Không tạo `Flux.share()` toàn cục phát dữ liệu của mọi tenant rồi lọc muộn theo request.
- Với SSE cross-origin, CORS allowlist, cookie/security/session policy riêng vẫn áp dụng. SSE không tự bảo vệ CSRF cho các endpoint cập nhật khác.

### 12.3. Không block Reactor event loop

Nếu bạn dùng WebClient/Reactor Netty trong một MVC controller, tránh gọi `.block()` trên Reactor event loop hoặc triển khai business chain chứa `Thread.sleep()` trong operators chạy trên event-loop scheduler. MVC blocking response write sẽ được chuyển sang async executor, nhưng upstream code blocking do bạn viết **vẫn có thể gây nghẽn upstream scheduler**.

## 13. Memory, latency và slow clients

### 13.1. Flux JSON thường gom danh sách

Một Publisher trả 100.000 records với `application/json` thường bị gom theo hướng `List<?>` trước khi Jackson serialize. Memory gồm:
- Java objects trong collection;
- deserialized rows nếu source repository lưu cache;
- JSON serialization buffers;
- servlet response/container output buffers;
- concurrency của các requests khác.

Với volume lớn, nên dùng pagination/limit hoặc streaming media type phù hợp. Streaming NDJSON giảm nhu cầu giữ toàn bộ collection nhưng không miễn phí memory; mỗi record vẫn được convert, writer/coder vẫn có buffers, và slow clients có thể giữ task/thread.

### 13.2. Flux.interval với tần suất cao

`Flux.interval(Duration.ofMillis(1))` có thể phát nhanh hơn client nhận. Nếu không có backpressure policy phù hợp, có thể lỗi overflow/drop/buffer tăng. Giảm phát event, batch/dedupe, giới hạn queue, có deadline và cân nhắc sampling. SSE thích hợp cho thông báo nhẹ; không phù hợp để truyền ảnh/video hoặc file nhiều GiB chỉ vì hỗ trợ streaming.

### 13.3. Executor saturation

Nếu mỗi blocking write giữ thread trong một khoảng dài, một pool nhỏ có thể đầy. Với pool `core=4 max=8 queue=64` của demo:
- Có tối đa 8 threads do executor quản lý theo cấu hình.
- Queue có giới hạn và request/task có thể bị từ chối khi quá tải.
- Một queue lớn thường trì hoãn response và tăng latency tail thay vì tăng throughput thực.

Không tuyên bố “8 threads là đủ cho 8, 80 hay 800 SSE users” khi chưa đo; mức thread occupancy phụ thuộc tốc độ network, flush, container, streaming cadence và cách framework lập lịch viết. Cần tải thử với slow consumers thực.

### 13.4. Proxy buffering và headers

Một SSE app chạy trực tiếp trên localhost thấy events từng 200 ms, nhưng qua reverse proxy có thể bị gom cho đến khi buffer đầy. Tùy infrastructure:
- cấu hình tắt buffering cho route SSE được xác thực;
- thiết lập idle/read timeouts phù hợp;
- gửi heartbeat khi cần và có giá trị;
- test `curl -N` qua URL public, không chỉ localhost;
- xác nhận CDN/load balancer hỗ trợ streaming.

Không tự khuyên đặt `X-Accel-Buffering: no` ở mọi môi trường như một chuẩn HTTP phổ quát; đây là chỉ dẫn dành cho một số proxy như Nginx và còn phụ thuộc cấu hình upstream. Cân nhắc nó khi topology tương ứng có mặt.

## 14. Lựa chọn công cụ theo yêu cầu

| Yêu cầu | Phương án phù hợp trong MVC | Cảnh báo |
| --- | --- | --- |
| Một kết quả có thể có sau vài trăm ms | `Mono<T>`, `CompletableFuture<T>`, `DeferredResult<T>` | Không block upstream scheduler |
| Status phụ thuộc kết quả async | `Mono<ResponseEntity<T>>` | Xử lý error trước commit |
| Status/headers tĩnh, body đến sau | `ResponseEntity<Mono<T>>` | Đừng mong thay status lúc body lỗi |
| JSON collection nhỏ, hữu hạn | `Flux<T>` + `application/json` | Buffer toàn collection |
| Thông báo một chiều liên tục | `Flux<ServerSentEvent<T>>` + SSE, hoặc `SseEmitter` | Timeout, connection, worker threads |
| Nhiều JSON records phát dần | `Flux<T>` + NDJSON converter | Media type, flush, interoperability |
| Tải file/binary stream | `StreamingResponseBody`/`Resource` | Blocking write, bandwidth, errors after commit |
| End-to-end non-blocking HTTP writes | Spring WebFlux | Cần chọn reactive server stack đúng |
| JDBC/JPA blocking business logic | MVC hoặc boundedElastic bridge có cân nhắc | Reactor type không biến JDBC non-blocking |

Không chọn reactive return type chỉ để né thread pool quan trọng: hãy xác định **bottleneck ở request worker, upstream client, DB hay response writer** rồi dùng async/web framework đúng nhu cầu.

## 15. Lỗi thường gặp và hướng xử lý

| Triệu chứng | Nguyên nhân khả dĩ | Hướng khắc phục |
| --- | --- | --- |
| `Mono<T>` bị trả như một object wrapper | MVC không nhận biết adapter/dependency hoặc method không trả body | Kiểm tra Reactor classpath, `@RestController`, converter |
| Flux JSON không tới từng phần tử ngay | `application/json` được gom thành List | SSE/NDJSON khi cần stream |
| Flux vô hạn treo request, tăng RAM | Non-streaming collection không kết thúc | Chuyển streaming media, limit/timeouts |
| SSE chạy local nhưng browser nhận một lượt | Proxy/client output buffering | Test `curl -N`, kiểm tra ingress |
| SSE ngắt sau 15 giây | Async timeout demo hoặc proxy timeout | Cấu hình timeout/heartbeat phù hợp |
| CPU/thread cao dù return Flux | MVC writes vẫn blocking | AsyncTaskExecutor bounded, đo slow clients/WebFlux |
| `Async support must be enabled` | Servlet/filter registration thiếu asyncSupported | Sửa Servlet/filter config & dispatcher types |
| `subscribe()` thủ công làm response rỗng | Subscription nằm ngoài MVC lifecycle | Return Mono/Flux trực tiếp |
| `.block()` làm chết throughput | Blocking upstream/event loop | Dùng nguồn nonblocking hoặc boundedElastic |
| Timeout không ra JSON error chuẩn | Response SSE đã committed | Event protocol/close/cancel/reconnect |
| Error handler thay status thất bại | Headers/body đã gửi | Xử lý errors trước streaming khi có thể |
| MDC/SecurityContext mất | Async thread boundaries | Context propagation hoặc explicit context |
| Backpressure overflow | Hot producer nhanh hơn slow subscriber | Bounded buffer/drop policy có chủ đích |
| MockMvc test trả body trống | Quên `asyncDispatch` | Assert asyncStarted + asyncDispatch |
| SSE nhận dữ liệu sai tenant | Subscription không scope authorization | Kiểm tra quyền mỗi channel/data source |
| Flux/Mono return nhưng không có Reactor | Thiếu dependency `reactor-core` | Thêm version do BOM quản lý |
| 5xx khi executor quá tải | Bounded executor/queue đã saturation | Rate limit, shed load, tăng capacity có số liệu |

## 16. Checklist production

1. Server đang dùng đúng **Spring MVC Servlet stack**, không nhầm với WebFlux khi chỉ thêm Reactor Core.
2. Return type có semantics phù hợp: 0..1 `Mono` hay 0..N `Flux`.
3. Endpoint định nghĩa media type rõ: JSON array hay SSE/NDJSON streaming.
4. Publisher là **hữu hạn** nếu output non-streaming; có pagination, timeout và cap.
5. Async Servlet support, filter dispatch types và MVC `AsyncTaskExecutor` được cấu hình/test.
6. Có đo số async requests, executor queue/active threads, slow clients và time-to-first-byte.
7. Có policy khi producer nhanh hơn consumer, buffer/drop strategy được nghiệp vụ chấp thuận.
8. HTTP response status/headers được quyết định trước khi write; lỗi sau commit dùng stream protocol/cancel.
9. Long-lived connections có timeout, heartbeat, reconnection IDs và quotas phù hợp.
10. Khi client ngắt kết nối, upstream subscriptions và resources được cleanup.
11. Security/auth/tenant scope, CSRF/CORS và permission changes được xử lý đúng.
12. ThreadLocal/MDC/locale/request context chỉ được dùng qua propagation tin cậy.
13. Integration test qua reverse proxy xác nhận streaming/flush thật, không chỉ test body cuối MockMvc.
14. Với Boot 4/Spring 7, kiểm tra starter/test package thay đổi trước khi copy code Boot 3.

## 17. Câu hỏi tự kiểm tra

**Câu 1.** Trả `Mono<BookView>` trong `@RestController` MVC có buộc phải dùng WebFlux server không?

**Đáp án:** Không. MVC thông qua `ReactiveAdapterRegistry` thích ứng reactive return type với Servlet async processing. Chỉ cần thư viện Reactor tương thích.

**Câu 2.** `Flux<BookView>` với `produces="application/json"` có gửi ngay từng object không?

**Đáp án:** Không mặc định; MVC có xu hướng gom multi-value stream thành List rồi serialize một JSON array.

**Câu 3.** Muốn browser nhận từng notification, nên dùng media type nào?

**Đáp án:** `text/event-stream` với `Flux<ServerSentEvent<T>>` hoặc `SseEmitter`; nhớ proxy buffering và timeout.

**Câu 4.** Vì sao vẫn phải cấu hình `AsyncTaskExecutor` dù source là Flux non-blocking?

**Đáp án:** Vì Servlet MVC vẫn ghi response bằng **blocking write** trên worker thread; không nên block upstream Reactor event loop.

**Câu 5.** `Mono<ResponseEntity<BookView>>` khác `ResponseEntity<Mono<BookView>>`?

**Đáp án:** Cái đầu quyết định cả status/headers/body bất đồng bộ; cái sau biết status/headers ngay, chỉ body phát sau.

**Câu 6.** Khi SSE đã gửi event đầu, có thể đổi status 200 thành 500 rồi trả ProblemDetail JSON không?

**Đáp án:** Không bảo đảm vì response có thể đã committed. Thiết kế error event/connection close và retry policy riêng.

**Câu 7.** `Flux.interval(1ms)` phát liên tục có tự bảo đảm không tràn bộ nhớ khi client chậm không?

**Đáp án:** Không. Cần backpressure behavior/source policy, bounded buffers, quotas và cancellation.

**Câu 8.** MockMvc test `Mono.delay` nên kiểm tra thế nào?

**Đáp án:** `request().asyncStarted()` rồi `asyncDispatch(result)` để assert status/JSON sau async completion.

**Câu 9.** Có nên dùng `.subscribe()` ở controller rồi tự trả null?

**Đáp án:** Không; làm mất integration giữa publisher và response lifecycle. Return publisher.

**Câu 10.** Khi nào nên chọn WebFlux thay MVC?

**Đáp án:** Khi cần non-blocking HTTP I/O và reactive pipeline toàn diện, đặc biệt high-concurrency streaming/slow clients; lựa chọn dựa trên benchmark và độ phức tạp code, không chỉ dựa vào tên Mono/Flux.

## 18. Tài liệu chính thức

- [Spring Framework 6.2 — Asynchronous Requests](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html): Servlet async, Reactive Types, write blocking, backpressure, context propagation và executor.
- [Spring Framework — Controller Return Values](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html): Mono/Flux adapter, streaming vs JSON collection, DeferredResult và emitters.
- [Spring Framework — ResponseEntity](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html): `ResponseEntity<Mono<T>>` và `Mono<ResponseEntity<T>>`.
- [Spring Framework 6.2 — HTTP Streaming](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html#_http_streaming): SSE, ResponseBodyEmitter và StreamingResponseBody.
- [Spring Framework 6.2 — MVC Async Config](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html#_configuration): `WebMvcConfigurer#configureAsyncSupport`, task executor và timeout.
- [Spring Framework — ReactiveAdapterRegistry Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/core/ReactiveAdapterRegistry.html): Reactive Streams adapters.
- [Spring Framework 6.2 — ServerSentEvent Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/http/codec/ServerSentEvent.html): SSE event representation.
- [Project Reactor — Reference](https://projectreactor.io/docs/core/release/reference/): Mono/Flux, schedulers, backpressure, cancellation, testing.
- [Spring Boot 3.5 — Web Servlet Applications](https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html): Servlet MVC stack, embedded containers.
- [Spring Boot 4 — Build Systems/Starters](https://docs.spring.io/spring-boot/4.0/reference/using/build-systems.html): thay đổi starter khi nâng cấp Boot 4.

**Kết luận:** trong Spring MVC, **Mono = kết quả bất đồng bộ đơn**, **Flux + JSON thường = gom thành mảng**, **Flux + SSE/NDJSON = streaming**. MVC có thể quản lý backpressure và Servlet async request lifecycle, nhưng **response writes vẫn blocking** và dùng `AsyncTaskExecutor`. Để stream production an toàn phải kiểm soát executor, bộ nhớ, timeout, producer speed, cancellation, proxy buffering và lỗi sau khi response committed; chỉ trả về kiểu reactive không biến ứng dụng MVC thành WebFlux.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-async-processing-007">Phát hiện ngắt kết nối, dọn tài nguyên và xử lý lỗi sau khi đã gửi header</a>

<details>
<summary>Xem chi tiết</summary>

# Spring MVC — Phát hiện ngắt kết nối, dọn tài nguyên và xử lý lỗi sau khi đã gửi header

## 1. Vấn đề thực tế và mục tiêu

Spring MVC hỗ trợ xử lý bất đồng bộ bằng `Callable`, `DeferredResult`, `ResponseBodyEmitter`, `SseEmitter`, `StreamingResponseBody` và một số reactive return types. Controller có thể kết thúc method từ lâu nhưng HTTP response vẫn mở. Nếu client đóng tab, ngắt mạng, hủy download hoặc reverse proxy chủ động đóng kết nối, server có thể vẫn giữ scheduled task, subscription, buffer hoặc stream đang phục vụ client đó.

Ví dụ endpoint SSE thông báo tiến độ:

~~~http
GET /api/events HTTP/1.1
Accept: text/event-stream
~~~

Server phát:

~~~http
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-store

event: progress
data: {"job":"A123","percent":30}

: heartbeat

event: progress
data: {"job":"A123","percent":50}
~~~

Một browser đóng tab khi đã nhận 50% không làm Servlet thread tự động được gọi một callback "remote disconnected" ngay tại thời điểm đó. Nếu kết nối không còn hoạt động nhưng không có lần ghi mới, backend có thể chưa biết. Trong lúc chờ, những đối tượng còn sống có thể gây **memory leak, task leak, subscriber leak, thread exhaustion và lãng phí tài nguyên**.

Sau bài học, bạn cần giải thích được:

- **Phát hiện disconnect**: giới hạn Servlet API, write/flush failure, heartbeat SSE, proxy idle timeout, `IOException` và `AsyncRequestNotUsableException`.
- **Quản lý lifecycle**: `SseEmitter.onCompletion`, `onTimeout`, `onError`, `DeferredResult` và container ASYNC dispatch.
- **Cleanup idempotent**: dọn client registry, ScheduledFuture, broker subscription, file/DB stream, queue, MDC; chống nhiều callback chạy cạnh tranh.
- **HTTP response committed**: khi status/headers đã gửi, không thể tùy tiện đổi status 200 thành 500 hay trả JSON lỗi mới.
- **Lỗi streaming**: in-band SSE error event nếu connection còn usable; dừng ghi và cleanup nếu I/O failure.
- **Kiểm thử**: Java 17 / Spring Boot 3.5, Maven, MockMvc async và kiểm thử socket disconnect thật.

Đây là bài về **Spring MVC trên Servlet stack**. WebFlux và WebSocket có lifecycle, API và backpressure khác; không sao chép giả định của một stack sang stack kia.

## 2. Vòng đời của Servlet async processing

### 2.1. Request đồng bộ

~~~text
Client -> Filter -> DispatcherServlet -> Controller
       -> View/Converter -> Response write -> Request complete
~~~

Controller return thường gắn gần với thời điểm server kết thúc xử lý response.

### 2.2. Callable và DeferredResult

~~~text
REQUEST dispatch
  |
  +-> Controller returns Callable/DeferredResult
  |
  +-> Servlet request enters async mode
  |
  +-> Initial servlet thread released
  |      HTTP response remains pending
  |
  +-> Worker or callback provides result
           |
           v
       ASYNC dispatch
           |
           v
       Serialize/complete response
~~~

- `Callable<T>`: Spring MVC submit Callable vào `AsyncTaskExecutor`; khi trả giá trị/exception, request được async-dispatch để hoàn tất.
- `DeferredResult<T>`: ứng dụng giữ handle và tự gọi `setResult` hoặc `setErrorResult` từ callback/thread khác; MVC xử lý kết quả cuối.
- `SseEmitter` và `ResponseBodyEmitter`: một request có thể phát **nhiều** dữ liệu/events cho đến complete, timeout hoặc error.
- `StreamingResponseBody`: callback viết trực tiếp vào OutputStream; Spring MVC có executor cho phần ghi, nhưng Servlet I/O vẫn thường blocking.

**Controller return không phải kết thúc đời sống tài nguyên của SSE.** Nếu tạo ScheduledFuture trong controller rồi gọi cancel trong `finally` của controller method, timer có thể bị hủy ngay trước khi stream thật sự chạy. Cần gắn cleanup vào **async lifecycle callbacks**.

### 2.3. Filter và interceptor cũng thay đổi

Một `OncePerRequestFilter` có thể thoát khỏi initial REQUEST dispatch trong khi SSE stream còn mở. Vì vậy duration đo bởi `try/finally` quanh `chain.doFilter` ban đầu là **dispatch duration**, không phải toàn bộ stream lifetime.

`AsyncHandlerInterceptor.afterConcurrentHandlingStarted(...)` được gọi khi MVC bắt đầu xử lý concurrent, thay vì `postHandle` và `afterCompletion` ngay ở lượt đầu. Khi result hoàn thành, có thể có ASYNC redispatch. Timeout/disconnect cần callback tương ứng; không giả định interceptor `afterCompletion` luôn chạy cùng lúc với controller method return.

Servlet configuration cũng cần `asyncSupported=true` cho DispatcherServlet và filters liên quan; filter mappings phải xét `DispatcherType.ASYNC` nếu một filter cần hoạt động trong redispatch. Spring Boot thường thiết lập tích hợp MVC căn bản, nhưng custom registration vẫn cần kiểm tra.

## 3. Client disconnect được phát hiện lúc nào?

**Servlet API không cung cấp một notification tức thời đáng tin rằng remote client vừa biến mất.** Đây là giới hạn được tài liệu Spring MVC nêu rõ. Với HTTP streaming, cách phổ biến để phát hiện client ngắt là **thử ghi**; khi TCP connection/proxy không còn usable, write/flush có thể ném exception.

### 3.1. Một dòng thời gian

~~~text
12:00:00  Browser mở EventSource
12:00:01  Server gửi event #1
12:00:05  Browser mất mạng / đóng tab
12:00:06  Không có event nào để gửi -> server có thể chưa biết
12:00:15  Heartbeat write/flush -> có thể phát hiện lỗi
12:00:15+ AsyncListener/container error notification
           Spring MVC completes async lifecycle
~~~

Thời gian chỉ minh họa. OS socket buffers, reverse proxy buffering, TCP timeouts, kernel/network stack và cách container flush có thể làm **lần ghi đầu tiên sau disconnect chưa thất bại ngay**. Heartbeat giúp giảm khoảng im lặng nhưng không bảo đảm phát hiện trong đúng một số giây.

### 3.2. Những tín hiệu cần phân biệt

| Sự kiện | Ý nghĩa hợp lý | Không được suy ra |
| --- | --- | --- |
| `IOException: Broken pipe` | Ghi lên connection đã đóng hoặc bị reset | Mọi IOException đều do người dùng chủ động hủy |
| `Connection reset by peer` | Peer/proxy TCP reset | HTTP status 500 đã được trả tới client |
| `AsyncRequestNotUsableException` | Async response không còn usable do lỗi ghi, container error, hoặc lifecycle đã hoàn tất | Có thể tiếp tục viết thêm JSON |
| `SseEmitter.onError(Throwable)` | Container báo lỗi async | Chắc chắn do client đóng tab |
| `SseEmitter.onTimeout()` | Servlet async timeout | Người dùng chủ động ngắt |
| `SseEmitter.onCompletion()` | Async request kết thúc vì bất kỳ nguyên nhân nào | Luôn là lỗi |
| `emitter.send(...)` không ném exception | Write API hiện tại không báo lỗi | Client đã render/nhận trọn payload |

Class cụ thể:

~~~java
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
~~~

`AsyncRequestNotUsableException` kế thừa `IOException` và biểu thị response async không còn usable theo các cơ chế Spring/container. Không cần catch nó riêng cho mọi endpoint; cần hiểu nó khi đọc stacktrace và thiết kế cleanup.

### 3.3. Client-side close và server-side completion khác nhau

Browser `EventSource.close()` ngừng mở/reconnect ở phía browser. Server có thể chỉ biết sau write failure hoặc container error/completion. Trong khi đó, `emitter.complete()` là hành động **server-side** đánh dấu request hoàn tất; nó không có nghĩa "remote browser đã chủ động disconnect".

Tương tự, nếu proxy idle timeout đóng downstream nhưng vẫn giữ upstream một lúc, quá trình nhận biết có thể khác nhau giữa gateway và ứng dụng.

## 4. Heartbeat là một phần của thiết kế SSE

SSE hỗ trợ comment-only event:

~~~text
: heartbeat

~~~

Dòng bắt đầu bằng dấu `:` là comment trong SSE. Browser thường bỏ qua comment này thay vì tạo business message event. Spring cung cấp:

~~~java
emitter.send(SseEmitter.event().comment("heartbeat"));
~~~

Ý nghĩa:

- Tạo một lần write/flush định kỳ để có cơ hội phát hiện connection đã đóng.
- Giảm nguy cơ một số ingress/proxy cho rằng upstream response bị idle.
- Giúp đo tỷ lệ write failures và tuổi thọ stream.

Nhưng heartbeat không chứng minh mọi byte đã được browser xử lý; TCP/proxy buffers có thể khiến write thành công tạm thời dù downstream không còn hoạt động.

### 4.1. Ba thời gian cần chọn riêng

| Timeout | Ở đâu | Ghi chú |
| --- | --- | --- |
| Servlet async timeout | `new SseEmitter(timeoutMillis)` hoặc MVC config | Giới hạn async processing theo container/Spring |
| Proxy read/idle timeout | Nginx, ingress, CDN | Có thể đóng response nếu lâu không có dữ liệu |
| Client idle/reconnect | Browser, HTTP client, mobile app | Có thể tự hủy/reconnect tùy protocol |

Ví dụ `new SseEmitter(60_000L)` đặt timeout async 60 giây. Heartbeat mỗi 10 giây **không nhất thiết reset thời hạn async request**. Nếu stream phải sống lâu, cần chọn chính sách timeout/reconnection phù hợp thay vì chỉ gửi comment heartbeat.

### 4.2. Proxy buffering

Nginx có thể giữ dữ liệu ở buffer khiến browser không nhận heartbeat kịp thời dù Spring đã write. Với một số cấu hình, response có thể đặt:

~~~http
Content-Type: text/event-stream
Cache-Control: no-store
X-Accel-Buffering: no
~~~

`X-Accel-Buffering: no` là hint liên quan Nginx, **không phải chuẩn SSE của browser** và có thể bị upstream cấu hình bỏ qua. Cần test qua proxy thật và dùng cấu hình streaming/timeout phù hợp ở từng hop.

## 5. ResponseBodyEmitter, SseEmitter và callbacks

### 5.1. Đăng ký lifecycle trước khi publish emitter

~~~java
SseEmitter emitter = new SseEmitter(60_000L);
emitter.onTimeout(() -> cleanupOnce());
emitter.onError(error -> cleanupOnce());
emitter.onCompletion(() -> cleanupOnce());
return emitter;
~~~

Đây là fragment; `cleanupOnce()` phải thuộc connection state thực tế. Callback có thể xảy ra trên **container thread**, không phải thread controller. Multiple callbacks có thể xảy ra cho cùng một request.

Javadoc Spring 6.2:

- `onTimeout(Runnable)`: container báo async timeout.
- `onError(Consumer<Throwable>)`: container báo error async processing.
- `onCompletion(Runnable)`: container hoàn tất async request **vì bất kỳ lý do nào**, gồm network error và timeout.

**onCompletion là terminal cleanup, không phải detector riêng cho disconnect.**

### 5.2. Quy tắc then chốt: IOException từ emitter.send()

Tài liệu Spring MVC ghi rõ: khi `ResponseBodyEmitter.send(...)` ném `IOException` (ví dụ remote client đã rời đi), **ứng dụng không phải và không nên gọi `emitter.complete()` hay `emitter.completeWithError(exception)` chỉ vì lỗi ghi đó**.

Servlet container tự phát async error notification; Spring MVC tiếp nhận lỗi và thực hiện xử lý, có thể gồm ASYNC dispatch tới exception resolution. Ứng dụng cần **dừng nguồn phát và dọn tài nguyên do mình sở hữu**.

Đúng về nguyên tắc:

~~~java
try {
    emitter.send(SseEmitter.event().name("update").data(payload));
} catch (IOException writeFailure) {
    cleanupMySubscription();
    // KHÔNG gọi emitter.complete() / completeWithError(writeFailure).
}
~~~

Đây là fragment với `payload` và `cleanupMySubscription` từ ứng dụng. Điều này **khác** lỗi business tự phát hiện khi channel vẫn usable: trường hợp đó có thể dùng error SSE event hoặc `completeWithError` theo trạng thái response.

### 5.3. Không cố gửi lỗi thứ hai trên socket đã lỗi

Sai:

~~~java
try {
    emitter.send(event);
} catch (IOException ex) {
    emitter.send(SseEmitter.event()
            .name("error")
            .data("Connection lost"));
}
~~~

Connection có thể không còn usable; lần ghi thứ hai thường chỉ gây thêm exception. Không cố trả HTTP 500 JSON sau khi SSE đã bắt đầu, bởi status/headers có thể đã committed.

## 6. HTTP response committed là gì?

Servlet `HttpServletResponse` hỗ trợ:

~~~java
boolean committed = response.isCommitted();
~~~

Response **committed** khi HTTP status và headers đã được gửi khỏi phần response buffering để tới tầng output. Sau commit:

- Không thể rút lại status 200 đã phát và đổi thành 500/409/404 trong cùng response.
- Không thể gửi một response hoàn toàn mới với headers khác.
- `reset()` hoặc `resetBuffer()` sau commit có thể ném `IllegalStateException`.
- `sendError(...)` sau commit không thể sửa response hợp lệ; có thể thất bại hoặc tạo lỗi phụ tùy container.
- `@RestControllerAdvice`/`@ExceptionHandler` không thể tự biến các bytes SSE đã gửi thành body `application/problem+json`.

### 6.1. Timeline

~~~text
Controller returns SseEmitter
   |
Response headers prepared
   |
First event sent / flush occurs
   |
HTTP 200 + Content-Type are committed
   |
Later producer fails
   |
Cannot replace with HTTP 500 JSON on the same connection
~~~

Lưu ý `setStatus(200)` chưa chắc đã commit ngay; buffering và flush quyết định thời điểm. Đừng giả định controller return đồng nghĩa response committed; khi debug có thể kiểm `isCommitted()` nhưng vẫn cần tuân framework lifecycle.

### 6.2. Lỗi trước commit

Nếu controller đã biết request không hợp lệ/không được cấp quyền/job không tồn tại, hãy kiểm **trước khi tạo hoặc phát stream**:

~~~java
@GetMapping(path = "/events/{jobId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
SseEmitter events(@PathVariable String jobId) {
    jobService.verifyAccess(jobId);     // 401/403 nếu cần
    jobService.verifyExists(jobId);     // 404
    jobService.verifyStreamingState(jobId); // 409
    return streamService.subscribe(jobId);
}
~~~

Fragment này cần service methods được triển khai. Khi lỗi xảy ra trước commit, Spring `@ExceptionHandler` có thể trả status và JSON/ProblemDetail an toàn. Đây là lý do validation, auth và preconditions nên được giải quyết sớm.

### 6.3. Lỗi sau commit nhưng còn gửi được

Một business producer gặp lỗi nhưng SSE channel vẫn usable có thể phát **in-band error event**:

~~~text
event: app-error
data: {"code":"JOB_FAILED","message":"Không thể tiếp tục"}

~~~

Trong Java (fragment):

~~~java
emitter.send(SseEmitter.event()
        .name("app-error")
        .data(Map.of(
                "code", "JOB_FAILED",
                "message", "Không thể tiếp tục")));
emitter.complete();
~~~

HTTP status vẫn là 200 trước đó; frontend phải phân biệt **transport status** với **application-level event code**. Chỉ làm điều này khi có thể gửi; không dùng để chữa failed write hoặc đã unusable.

### 6.4. Lỗi I/O sau commit

Nếu write bị `IOException`:

1. Hủy scheduled tasks, unsub broker và giải phóng business resources.
2. Không cố phát JSON hoặc error SSE event.
3. Không gọi `complete()/completeWithError()` chỉ vì IOException từ `ResponseBodyEmitter.send`.
4. Để Servlet container/Spring hoàn tất request error lifecycle.
5. Ghi metric/log phù hợp, tránh một stacktrace ERROR cho từng browser đóng tab bình thường.

## 7. Cleanup idempotent và race conditions

Một session có thể bị timeout, onError, write failure, manual cancel, shutdown hoặc complete trong khoảng rất gần nhau. Những callbacks có thể chạy trên nhiều threads.

Sai:

~~~java
void cleanup() {
    registry.remove(id);
    future.cancel(false);
    subscription.close();
    activeCount.decrementAndGet();
}
~~~

Nếu chạy hai lần, activeCount bị trừ hai lần; có thể double-close hoặc xóa nhầm session khác cùng id.

Cách thiết kế:

~~~java
final AtomicBoolean closed = new AtomicBoolean(false);

void cleanup() {
    if (!closed.compareAndSet(false, true)) {
        return;
    }
    registry.remove(id, thisSession);
    if (future != null) future.cancel(false);
    subscription.close();
    activeCount.decrementAndGet();
}
~~~

Đây là fragment minh họa. Dùng **connection ID duy nhất**, `ConcurrentMap.remove(id, instance)` và exactly-once cleanup. Cần xử lý race `onCompletion` diễn ra **trước khi ScheduledFuture được gán**: setter lưu Future rồi kiểm `closed.get()` và cancel nếu đã đóng.

### 7.1. Những tài nguyên cần dọn

| Tài nguyên | Hành động |
| --- | --- |
| Map giữ SseEmitter/session | Remove đúng object/value |
| ScheduledFuture heartbeat | Cancel future của connection |
| Broker subscription | Unsubscribe theo SDK contract |
| Stream/file InputStream | Close bằng try-with-resources |
| DB cursor/transaction resources | Close tại ownership boundary; không giữ lâu không cần |
| Per-client queue/buffer | Clear references và có capacity giới hạn |
| Future background job | Cancel/cooperative interrupt nếu client không còn cần |
| MDC/ThreadLocal | Restore/clear trên worker thread tương ứng |
| Metrics active connections | Decrement một lần |
| Application scheduler | Chỉ shutdown khi application lifecycle kết thúc, không shutdown per client |

**Không lấy một shared executor rồi shutdown trong `onCompletion` của từng request**, vì như vậy sẽ ảnh hưởng các client khác.

### 7.2. Các race cần kiểm thử

- Timeout callback xảy ra ngay khi timer task đang `send`.
- Write failure kích hoạt container `onError` và cuối cùng `onCompletion`.
- Future vừa schedule nhưng callback cleanup đã chạy.
- Client reconnect tạo session mới cùng logical user; cleanup session cũ xóa nhầm session mới.
- App shutdown đồng thời với client disconnect.
- Broker callback vào queue trước khi session closed nhưng chạy sau closed.

Kiểm `closed` trước mỗi send và dùng per-connection state; không lock toàn cục qua network write. Các operations đang chạy có thể cần cancellation/cooperative stopping riêng.

## 8. SSE reconnect và Last-Event-ID

Browser `EventSource` thường tự reconnect sau một số lỗi connection. Khi có SSE event id:

~~~text
id: 102
event: update
data: {"percent":70}

~~~

browser có thể gửi `Last-Event-ID: 102` khi reconnect. Server **chỉ replay events** nếu có storage/sequence history thực sự; một map emitter in-memory không đảm bảo durability.

Nếu yêu cầu không mất sự kiện, cần:

- Event IDs tăng/độc nhất theo stream contract.
- Durable event store hoặc message broker retention.
- Reconnect với cursor đã được xác thực.
- Replay từ sequence tiếp theo, xử lý duplicate/idempotency.
- Retention/window và behavior khi cursor quá cũ.

Nếu chỉ gửi progress giá trị mới nhất, có thể chọn **latest-state snapshot sau reconnect** thay vì replay toàn bộ.

Phía frontend phải gọi `eventSource.close()` khi unmount/không cần stream để tránh reconnect mãi. Server vẫn cần cleanup khi phát hiện disconnect, không chỉ dựa vào frontend tuân thủ.

## 9. Dự án chạy được: SSE + DeferredResult

Dự án dùng Java 17, Spring Boot 3.5.0. Có:

- `GET /api/stream`: mở SSE, gửi heartbeat mỗi 10 giây.
- `GET /api/once`: DeferredResult được hoàn tất sau 50 ms.
- `GET /api/stream-count`: đếm active subscriptions để học/dễ test.
- Lifecycle cleanup onCompletion/onTimeout/onError, AtomicBoolean exactly-once.
- Khi `send()` ném IOException, chỉ dọn resources; không gọi emitter.complete/error.
- Một `ScheduledExecutorService` dùng chung làm Spring bean, shutdown lúc app tắt.

### 9.1. Cấu trúc

~~~text
async-disconnect-demo/
├── pom.xml
└── src/
    ├── main/java/demo/AsyncDisconnectApplication.java
    └── test/java/demo/AsyncDisconnectTest.java
~~~

### 9.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>async-disconnect-demo</artifactId>
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

### 9.3. AsyncDisconnectApplication.java

Đặt toàn bộ code vào **một file** `src/main/java/demo/AsyncDisconnectApplication.java`. Một top-level public class; các class còn lại package-private. Demo không có database, không có login/security và không có business broker.

~~~java
package demo;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@SpringBootApplication
public class AsyncDisconnectApplication {
    public static void main(String[] args) {
        SpringApplication.run(AsyncDisconnectApplication.class, args);
    }
}

@Configuration
class SchedulerConfig {
    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService scheduler() {
        return Executors.newScheduledThreadPool(2);
    }
}

@Service
class StreamService {
    private static final Logger LOG =
            LoggerFactory.getLogger(StreamService.class);

    private final ScheduledExecutorService scheduler;
    private final ConcurrentMap<String, Connection> connections =
            new ConcurrentHashMap<>();

    StreamService(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    SseEmitter subscribe() {
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(60_000L);
        Connection connection = new Connection(id, emitter);
        connections.put(id, connection);

        emitter.onCompletion(() -> close(connection, "completion"));
        emitter.onTimeout(() -> close(connection, "timeout"));
        emitter.onError(ex -> {
            LOG.info("stream_error id={} kind={}",
                    id, ex.getClass().getSimpleName());
            close(connection, "container-error");
        });

        try {
            ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                    () -> heartbeat(connection),
                    1, 10, TimeUnit.SECONDS);
            connection.setFuture(future);
        } catch (RuntimeException ex) {
            close(connection, "scheduler-failed");
            throw ex; // Chưa trả emitter, HTTP error handling còn cơ hội.
        }
        return emitter;
    }

    int activeCount() {
        return connections.size();
    }

    private void heartbeat(Connection connection) {
        if (connection.closed.get()) {
            return;
        }
        try {
            // SSE comment, browser không dispatch business message event.
            connection.emitter.send(
                    SseEmitter.event().comment("heartbeat"));
        } catch (IOException ioFailure) {
            // Không complete()/completeWithError() sau write IOException.
            LOG.info("stream_write_failed id={} kind={}",
                    connection.id,
                    ioFailure.getClass().getSimpleName());
            close(connection, "write-failure");
        } catch (IllegalStateException noLongerUsable) {
            close(connection, "not-usable");
        } catch (RuntimeException unexpected) {
            LOG.warn("stream_producer_failed id={} kind={}",
                    connection.id,
                    unexpected.getClass().getSimpleName());
            close(connection, "producer-failure");
        }
    }

    private void close(Connection connection, String reason) {
        if (!connection.closed.compareAndSet(false, true)) {
            return;
        }
        connections.remove(connection.id, connection);
        ScheduledFuture<?> f = connection.future;
        if (f != null) {
            f.cancel(false);
        }
        LOG.info("stream_closed id={} reason={}", connection.id, reason);
    }

    @PreDestroy
    void cleanupOnShutdown() {
        for (Connection connection : connections.values()) {
            close(connection, "shutdown");
        }
        // Spring hủy scheduler bean; không shutdown cho mỗi client.
    }

    private static final class Connection {
        final String id;
        final SseEmitter emitter;
        final AtomicBoolean closed = new AtomicBoolean();
        volatile ScheduledFuture<?> future;

        Connection(String id, SseEmitter emitter) {
            this.id = id;
            this.emitter = emitter;
        }

        void setFuture(ScheduledFuture<?> scheduled) {
            this.future = scheduled;
            if (closed.get()) {
                scheduled.cancel(false);
            }
        }
    }
}

@RestController
class AsyncController {
    private final StreamService streams;
    private final ScheduledExecutorService scheduler;

    AsyncController(StreamService streams,
                    ScheduledExecutorService scheduler) {
        this.streams = streams;
        this.scheduler = scheduler;
    }

    @GetMapping(path = "/api/stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<SseEmitter> stream() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .body(streams.subscribe());
    }

    @GetMapping(path = "/api/once",
                produces = MediaType.APPLICATION_JSON_VALUE)
    DeferredResult<ResponseEntity<Map<String, String>>> once() {
        DeferredResult<ResponseEntity<Map<String, String>>> result =
                new DeferredResult<>(2_000L);

        ScheduledFuture<?> future = scheduler.schedule(
                () -> result.setResult(
                        ResponseEntity.ok(Map.of("state", "done"))),
                50, TimeUnit.MILLISECONDS);

        result.onCompletion(() -> future.cancel(false));
        result.onError(error -> future.cancel(false));
        result.onTimeout(() -> {
            future.cancel(false);
            result.setErrorResult(
                    ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(Map.of("code", "WAIT_TIMEOUT")));
        });
        return result;
    }

    @GetMapping("/api/stream-count")
    Map<String, Integer> count() {
        return Map.of("activeConnections", streams.activeCount());
    }
}
~~~

### 9.4. Vì sao implementation xử lý được race?

- `ConcurrentMap` dùng id riêng cho mỗi connection.
- `AtomicBoolean.compareAndSet(false,true)` bảo đảm cleanup **exactly once**.
- `connections.remove(id, connection)` không xóa nhầm object khác cùng key.
- `setFuture` kiểm lại closed vì timeout có thể đến trước khi Future được gán.
- Các callbacks chỉ đóng **owned scheduled task**, không shutdown global scheduler.
- Khi send ném IOException, không chủ động gọi `complete()` hay `completeWithError()`; Spring/container quản lý async error.
- Timeout và error callbacks có thể ở thread khác; state/registry được thiết kế thread-safe.
- `SseEmitter(60_000L)` có timeout 60 giây; heartbeat không nhất thiết kéo dài timeout này.

**Giới hạn demo:** Một ScheduledFuture cho mỗi connection tốn scheduling overhead nếu rất nhiều clients; production có thể dùng shared heartbeat loops hoặc chiến lược khác. Registry in-memory mất khi restart, không đảm bảo replay. Mọi endpoint demo đều public, cần authentication/authorization và quota nếu dùng dữ liệu thật. `stream-count` nên là metric/admin endpoint được bảo vệ.

### 9.5. Chạy và quan sát

Từ thư mục Maven:

~~~bash
mvn spring-boot:run
~~~

Terminal thứ hai:

~~~bash
curl -N -i http://localhost:8080/api/stream
~~~

`-N` làm curl không buffer output, thuận tiện quan sát stream. Response headers có thể gồm:

~~~http
HTTP/1.1 200
Content-Type: text/event-stream
Cache-Control: no-store
X-Accel-Buffering: no
~~~

Sau khoảng một giây rồi mỗi mười giây:

~~~text
:heartbeat

:heartbeat

~~~

Mở terminal khác:

~~~bash
curl -s http://localhost:8080/api/stream-count
~~~

Ví dụ khi đang có một subscription:

~~~json
{"activeConnections":1}
~~~

Nhấn Ctrl+C ở client. Server **có thể chưa ngay lập tức biết**; sau write/timeout callbacks, registry sẽ được dọn và count có thể về 0:

~~~json
{"activeConnections":0}
~~~

Đây là kết quả ví dụ, không phải cam kết timing chính xác. Trong production cần quan sát proxy behavior, TCP buffering, heartbeat interval, timeouts và số active subscriptions thật.

### 9.6. Browser EventSource

~~~javascript
const source = new EventSource("/api/stream");

source.onopen = () => {
  console.log("SSE opened");
};
source.onerror = () => {
  console.log("Disconnected or reconnecting");
};

function stop() {
  source.close();
}
~~~

Demo chỉ gửi SSE **comments** nên browser không nhận business message events. Nếu ứng dụng cần event nghiệp vụ, gửi `event: update` và data phù hợp, đăng ký listener tương ứng. Client phải gọi `close()` khi không còn cần stream để không reconnect mãi.

### 9.7. Một DeferredResult có kết quả một lần

~~~bash
curl -i http://localhost:8080/api/once
~~~

Kết quả tương đương:

~~~json
{"state":"done"}
~~~

Sau khoảng 50ms cộng overhead, MVC redispatch kết quả. Nếu request timeout trước khi task chạy, timeout callback cancel Future và có thể set HTTP 503 theo chính sách demo; thực tế race/Servlet container default handling cần được kiểm thử. `future.cancel(false)` sau khi Future đã hoàn tất không gây hại.

## 10. MockMvc tests

File `src/test/java/demo/AsyncDisconnectTest.java`:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AsyncDisconnectTest {
    @Autowired MockMvc mvc;

    @Test
    void deferredResultCompletesViaAsyncDispatch() throws Exception {
        MvcResult initial = mvc.perform(get("/api/once"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(initial))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("done"));
    }

    @Test
    void sseStartsAsyncWithStreamingHeaders() throws Exception {
        MvcResult result = mvc.perform(get("/api/stream"))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andExpect(header().string(
                    HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().string(
                    "X-Accel-Buffering", "no"))
            .andExpect(header().string(
                    HttpHeaders.CONTENT_TYPE,
                    containsString("text/event-stream")))
            .andReturn();

        assertTrue(result.getRequest().isAsyncStarted());
    }

    @Test
    void countEndpointExposesNumericValue() throws Exception {
        mvc.perform(get("/api/stream-count"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeConnections").isNumber());
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

MockMvc kiểm MVC asyncStarted, asyncDispatch và HTTP headers. Nó **không giả lập TCP disconnect một cách đầy đủ**. Với `SseEmitter`, test `andReturn()` khi asyncStarted không đồng nghĩa stream đã complete; phải thiết kế timeout/lifecycle cleanup và nếu cần dùng integration tests với embedded Tomcat và HTTP socket thật.

## 11. StreamingResponseBody: xử lý lỗi giữa download

Với file download lớn, Spring MVC có `StreamingResponseBody`:

~~~java
@GetMapping("/download")
public StreamingResponseBody download() {
    return output -> {
        try (InputStream input = storage.open("report.csv")) {
            input.transferTo(output);
            output.flush();
        }
    };
}
~~~

Đây là fragment, cần `InputStream`, `StreamingResponseBody` imports và `storage` service thực. Nếu OutputStream ghi thất bại do client disconnect:

- `try-with-resources` giúp đóng InputStream.
- Không tự viết JSON `{"error":"..."}` lên output đã lỗi.
- Log/metric ngắn với classification phù hợp.
- Stop producer và hủy resources/cursor còn phụ thuộc download.

Nếu server đã gửi 5MB của file 10MB rồi lỗi, HTTP headers/status có thể đã committed. Client chỉ nhận file không đầy đủ, không nhận được một HTTP 500 mới. Nếu giao thức yêu cầu resume/integrity, cần `Content-Length` đúng, range requests, ETag/checksum và các semantics riêng.

**Blocking I/O:** StreamingResponseBody không làm JDBC/file/socket writes tự trở thành non-blocking. Cần bounded task executor, timeouts, quotas và xử lý slow clients.

## 12. DeferredResult: timeout và cancellation

`DeferredResult<T>` thể hiện **một** async result. Trong callback:

~~~java
boolean accepted = result.setResult(value);
if (!accepted) {
    // Request đã timeout/complete hoặc result đã được set.
    // Dừng công việc không còn cần thiết.
}
~~~

Đây là fragment với result/value xác định trong ứng dụng. Không phát kết quả nhiều lần; không tin timeout callback luôn thắng task đã sắp kết thúc.

Ví dụ lifecycle:

~~~java
DeferredResult<String> response = new DeferredResult<>(5_000L);
response.onCompletion(() -> cancelJob());
response.onError(error -> cancelJob());
response.onTimeout(() -> {
    cancelJob();
    response.setErrorResult(new TimeoutException("Expired"));
});
~~~

Fragment này cần `cancelJob()` và import `TimeoutException`. `Future.cancel(true)` chỉ gửi tín hiệu interrupt nếu task hỗ trợ; không bảo đảm thread dừng. Với job dài hơn timeout HTTP, cân nhắc trả `202 Accepted + jobId` rồi client polling/SSE, thay vì giữ kết nối đến khi business job hoàn tất.

## 13. Thiết kế lỗi trước và sau commit

| Thời điểm | Ví dụ lỗi | Hướng xử lý |
| --- | --- | --- |
| Trước khi tạo stream | Invalid input, 401/403, 404, business 409 | Trả HTTP status + JSON/ProblemDetail an toàn |
| Sau khi stream bắt đầu nhưng channel usable | Job failed, business task stopped | Gửi SSE `app-error` event hoặc kết thúc theo contract |
| Sau committed + write IOException | Broken pipe, connection reset | Không viết thêm, dọn producer, để container/Spring xử lý |
| Sau committed + internal producer failure | Serialization/business exception | In-band error nếu usable; ngược lại log/cleanup/close |
| DeferredResult chưa committed | Worker exception | `setErrorResult`, MVC exception resolver |
| Stream hoàn thành bình thường | Business completed | `emitter.complete()` và onCompletion cleanup |
| Async timeout | Deadline exceeded | onTimeout cleanup, framework timeout behavior hoặc result policy |
| Reverse proxy closes connection | Idle timeout/network | Có thể phát hiện ở heartbeat/send tiếp theo |

Phân biệt:

- **HTTP transport status** được chốt khi response committed.
- **Event-level status** có thể thay đổi trong payload SSE vì đó là dữ liệu ứng dụng.
- **Business job state** có thể tiếp tục sau khi HTTP stream kết thúc, tùy nghiệp vụ; hủy SSE không nhất thiết hủy durable job.

Không coi `@ExceptionHandler` là “bộ sửa 500 cho mọi tình huống”, vì nó không thể đảo ngược bytes đã phát.

## 14. Metrics và logging cho streaming

Nên ghi khi mở và đóng:

~~~text
stream_open connectionId=<id> endpoint=/api/stream
stream_close connectionId=<id> reason=timeout lifetimeMs=60000
stream_write_failed connectionId=<id> kind=AsyncRequestNotUsableException
~~~

Đây là log mẫu, không phải output cố định.

Các metrics hữu ích:

- Active SSE connections (gauge).
- Opened/closed total theo **reason nhóm hữu hạn**.
- Stream lifetime histogram.
- IO write failures theo endpoint/reason nhóm nhỏ.
- Timeout rate, reconnect rate và heartbeat latency.
- Scheduler queue/executor saturation.
- Cleanup failures và broker subscription backlog.

Không gắn userId/requestId/connectionId vào Prometheus low-cardinality labels. Có thể dùng correlation ID trong log/trace với privacy review. Đừng log token, cookie, email hoặc raw request body để debug disconnect.

Một số gateways như Nginx log **499** cho client closed request; đây là ký hiệu **nonstandard, riêng proxy**, không phải HTTP status Spring MVC cần trả cho client đã disconnect.

Lỗi "Broken pipe" do client ngắt thông thường thường không đáng log stacktrace ERROR mỗi lần; theo dõi bằng counter và log ở mức phù hợp. Nếu failures tăng bất thường theo deployment mới, cần điều tra proxy/network/timeout và code.

## 15. Slow client, backpressure và production capacity

Kênh SSE có thể giữ hàng nghìn kết nối. Nếu producer sinh events nhanh hơn tốc độ mạng client nhận, queue tăng, RAM tăng và threads có thể bị block.

Các khuyến nghị:

1. Giới hạn connections trên instance/tenant/user theo trust và authentication policy.
2. Chọn bounded buffers/queues, tránh tích tụ vô hạn per-client.
3. Với progress/latest state, ưu tiên **coalescing**: giữ giá trị mới nhất thay vì mọi event cũ.
4. Với dữ liệu bắt buộc không mất, dùng durable log/replay cursor.
5. Chọn scheduler/thread pool có metrics và shutdown.
6. Đặt heartbeat hợp lý với proxy idle read timeout.
7. Dọn broker subscriptions và Future ngay lúc lifecycle kết thúc.
8. Không giữ database transaction, file handle hoặc ServletRequest suốt stream nếu không cần.
9. Chạy soak tests với nhiều connections, slow readers và disconnect storm.
10. Tách HTTP stream lifetime khỏi business job lifetime; không hủy durable job chỉ vì browser đóng tab trừ khi nghiệp vụ yêu cầu.

### 15.1. Nginx minh họa

~~~nginx
location /api/stream {
    proxy_pass http://backend:8080;
    proxy_buffering off;
    proxy_read_timeout 120s;
}
~~~

Đây là fragment, cần config TLS, host validation, auth và routing khác. `proxy_read_timeout` thường đo khoảng idle giữa reads từ upstream, không phải tổng stream lifetime. Với SSE hãy test qua proxy public thật, không chỉ backend.

Nếu upstream đã gửi HTTP headers mà sau đó lỗi, gateway cũng không thể phát thêm một HTTP 502 response mới trong cùng stream đã bắt đầu; thường phải đóng connection. Client có thể reconnect theo SSE semantics.

## 16. Câu hỏi luyện tập

**Bài 1:** Browser đóng SSE connection khi stream không có event trong 2 phút. Tại sao Servlet API không đảm bảo phát hiện ngay? Nên thiết kế heartbeat, timeout và proxy như thế nào?

**Bài 2:** `SseEmitter.send()` ném `IOException: Broken pipe`. Theo Spring, có nên gọi `completeWithError` ngay trong catch không? Tại sao cần dọn scheduled tasks nhưng để container hoàn tất lifecycle?

**Bài 3:** Có onError, onTimeout và onCompletion cùng gọi cleanup. Viết code sử dụng `AtomicBoolean.compareAndSet` để tránh giảm active counter nhiều lần.

**Bài 4:** Response SSE đã gửi status 200 cùng hai events. Một business exception xuất hiện. Tại sao `@RestControllerAdvice` không thể biến thành HTTP 500? Khi nào gửi `event: app-error`?

**Bài 5:** Một stream giữ broker subscription và scheduled heartbeat. Hãy vẽ lifecycle của hai resources khi client disconnect, timeout, shutdown và normal complete.

**Bài 6:** Một `DeferredResult` bị timeout nhưng worker gọi `setResult` sau đó. Đọc boolean return và thiết kế cancellation phù hợp.

**Bài 7:** Download file 100MB bị ngắt ở byte thứ 1 triệu. Làm sao đóng InputStream? Vì sao không thể gửi JSON lỗi sau khi đã truyền file bytes?

**Bài 8:** Heartbeat SSE comment khác với event business như thế nào? Browser có tạo message event từ comment không?

**Bài 9:** Proxy buffer SSE khiến client không thấy dữ liệu. Nêu cách kiểm `X-Accel-Buffering`, `proxy_buffering`, timeouts và TCP socket behavior.

**Bài 10:** Thiết kế metrics stream.active, stream.closed.total{reason} và stream.lifetime, tránh dùng connectionId làm metric label.

**Bài 11:** EventSource reconnect nhưng client cần không mất message. Hãy thiết kế event IDs và replay history; nếu không có durable history thì contract cần nói gì?

**Bài 12:** Viết MockMvc test cho DeferredResult asyncStarted/asyncDispatch và đề xuất một test integration socket disconnect mà MockMvc không thể mô phỏng đầy đủ.

## 17. Checklist trước khi triển khai

- [ ] Input, authentication, authorization và business preconditions được kiểm tra trước khi stream commit.
- [ ] Hiểu và định nghĩa được async timeout, proxy idle timeout, client reconnect policy.
- [ ] Có heartbeat định kỳ nhưng không giả định phát hiện disconnect tức thời.
- [ ] Đăng ký onCompletion/onTimeout/onError khi tạo emitter.
- [ ] Cleanup once dùng thread-safe state, cancel futures, unsubscribe broker và gỡ registry.
- [ ] IOException từ ResponseBodyEmitter.send không bị application double-complete.
- [ ] Exception sau committed không cố đổi HTTP status hoặc gửi JSON response mới.
- [ ] Event-level error protocol rõ ràng nếu muốn báo lỗi trong SSE vẫn usable.
- [ ] Có limits per-user/per-instance, bounded buffers và slow-client policy.
- [ ] Không shutdown shared executor theo request.
- [ ] Có resource cleanup khi application shutdown.
- [ ] Có metrics active/lifetime/disconnect/timeout và alert leak.
- [ ] Có tests streaming headers, MVC async lifecycle, concurrent callbacks, socket disconnect và proxy behavior.
- [ ] Frontend EventSource đóng khi không cần và xử lý reconnect/event replay có chủ đích.

## 18. Ghi nhớ

- **Servlet không cung cấp callback ngay lập tức, đáng tin cậy cho remote disconnect.** Write failure hoặc container async error notification thường là cách phát hiện.
- **Heartbeat SSE** vừa giữ stream có hoạt động vừa tạo cơ hội phát hiện kết nối lỗi.
- **onCompletion** xảy ra vì nhiều lý do, nên dùng làm terminal cleanup; **onTimeout/onError** cung cấp thêm dấu hiệu chứ không phải bằng chứng chắc chắn của client cancellation.
- Khi **SseEmitter.send ném IOException**, dừng producer và cleanup owned resources; **không tự gọi complete hoặc completeWithError** chỉ vì lỗi ghi đó.
- Khi **HTTP response committed**, không thể thay status/headers hay trả JSON error mới trong cùng response.
- Lỗi business khi SSE còn usable có thể truyền qua **SSE app-error event**; lỗi transport thì dừng ghi.
- **Cleanup phải idempotent** vì callbacks có thể cạnh tranh; mọi Future, subscription và buffer có owner rõ.
- `DeferredResult`, `StreamingResponseBody` và MVC async filters/interceptors đều cần timeout/cancellation đúng ranh giới.
- MockMvc kiểm MVC lifecycle, nhưng **disconnect cần integration test với socket/proxy thật**.

## 19. Tài liệu chính thức

- [Spring Framework 6.2 — Asynchronous Requests](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html): Callable, DeferredResult, ResponseBodyEmitter, SseEmitter, StreamingResponseBody, disconnects.
- [Spring Framework 6.2 — ResponseBodyEmitter](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseBodyEmitter.html): send, complete, completeWithError và lifecycle callbacks.
- [Spring Framework 6.2 — SseEmitter](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.html).
- [Spring Framework 6.2 — AsyncRequestNotUsableException](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/context/request/async/AsyncRequestNotUsableException.html).
- [Spring Framework 6.2 — DeferredResult](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/context/request/async/DeferredResult.html).
- [Jakarta Servlet 6.0 — ServletResponse](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/servletresponse): isCommitted, reset và resetBuffer.
- [HTML Living Standard — Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html): EventSource, SSE framing, reconnect.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-async-processing-008">Truyền ngữ cảnh, an toàn luồng và tránh sử dụng executor không giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Truyền ngữ cảnh, an toàn luồng và tránh sử dụng executor không giới hạn trong Spring MVC bất đồng bộ

## 1. Bối cảnh và mục tiêu

Spring MVC hỗ trợ **xử lý request bất đồng bộ trên Servlet stack** bằng `Callable<T>`, `WebAsyncTask<T>`, `DeferredResult<T>`, `ResponseBodyEmitter`, `SseEmitter`, `StreamingResponseBody` và một số reactive return types. Khi handler trả một giá trị bất đồng bộ, thread Servlet ban đầu có thể rời khỏi request sớm trong khi response vẫn mở. Công việc khác tiếp tục trên thread từ executor, thread của callback sự kiện hoặc một hệ thống I/O khác.

Chuyển từ một luồng sang nhiều luồng dẫn đến ba nhóm rủi ro:

1. **Mất ngữ cảnh:** `MDC`/trace ID, `SecurityContextHolder`, `LocaleContextHolder`, `RequestContextHolder`, transaction hay `ThreadLocal` tự định nghĩa không tự đồng bộ giữa mọi thread.
2. **Data race và rò rỉ:** controller/service singleton chia sẻ mutable state không đồng bộ; thread pool tái sử dụng thread giữ lại `ThreadLocal` của request trước; request kết thúc nhưng callback vẫn giữ request/session/stream không còn hợp lệ.
3. **Quá tải tài nguyên:** executor sinh thread không có giới hạn hoặc có queue không giới hạn, blocking task kéo dài và streaming client chậm khiến CPU, heap, connection, disk, database pool và timeout tăng không kiểm soát.

Học xong, bạn có thể giải thích chính xác luồng `Callable` so với `DeferredResult`, cấu hình **`AsyncTaskExecutor` có giới hạn**, truyền MDC/tracing và security context theo cơ chế phù hợp, viết code an toàn luồng, xử lý timeout/rejection/cancellation, và kiểm thử request bất đồng bộ bằng `MockMvc`.

**Phạm vi:** **Java 17+, Spring Framework 6.x, Spring Boot 3.x, Spring MVC Servlet stack**. Đây không phải hướng dẫn WebFlux non-blocking hoặc tutorial riêng cho Java virtual threads. Khả năng chạy trên virtual threads không tự thay thế giới hạn tải đồng thời, kiểm soát tài nguyên hay bảo mật dữ liệu.

## 2. Luồng request ban đầu và luồng async khác gì?

Với `Callable`:

~~~text
Client                         Servlet container             MVC executor
  | GET /api/async/work               |                          |
  |---------------------------------->|                          |
  |                          DispatcherServlet                    |
  |                          gọi controller -> Callable          |
  |                          request.startAsync()                |
  |                          submit Callable ------------------->|
  |                          giải phóng thread Servlet           |
  |                                                             |
  |                                              Callable chạy ở worker
  |                                              tạo DTO / throw error
  |                          <------ kết quả/async dispatch ------|
  |                          DispatcherServlet xử lý lại          |
  |<------------------------- response HTTP ----------------------|
~~~

Trình tự quan trọng:

- Controller được gọi trong **thread phục vụ request ban đầu**.
- Controller trả một đối tượng `Callable<T>`, chứ **không** chạy luôn `call()`.
- MVC dùng `AsyncTaskExecutor` để chạy `Callable` trên worker thread.
- Khi worker hoàn thành, framework **async dispatch** request trở lại Servlet container để hoàn tất response hoặc xử lý exception.
- Mã của ứng dụng không nên giả định mọi đoạn `before/after` ở MVC interceptor/filter chỉ chạy đúng một lần trên cùng một thread; với async có thể có nhiều dispatch phase.

Đối với `DeferredResult<T>`, một hệ thống khác (event listener, message broker callback, scheduler, bounded executor riêng) có thể gọi `setResult` hoặc `setErrorResult`. **MVC không tự chạy công việc producer trong executor của nó** chỉ vì handler trả `DeferredResult`. Đây là khác biệt rất quan trọng cho context propagation.

### Các kiểu trả về thường gặp

| Kiểu trả về | Ai làm công việc tiếp? | Điều cần chú ý |
| --- | --- | --- |
| `Callable<T>` | MVC `AsyncTaskExecutor` | Thread-local context; queue; timeout; result/error |
| `WebAsyncTask<T>` | Executor mặc định hoặc executor riêng nếu cấu hình | Có timeout và callback theo từng tác vụ |
| `DeferredResult<T>` | Producer/callback do ứng dụng hoặc thư viện quản lý | Tự quản lý producer, completion, timeout, cleanup |
| `CompletableFuture<T>` | Executor mà future thực sự được tạo/chạy trên đó | `supplyAsync` không chỉ định executor thường dùng common pool |
| `ResponseBodyEmitter`, `SseEmitter` | Nguồn sự kiện/producer của ứng dụng, hệ thống ghi HTTP | Client chậm; heartbeat; cleanup; bộ nhớ buffer |
| `StreamingResponseBody` | MVC async executor chạy callback ghi `OutputStream` | Blocking writes; client disconnect; executor capacity |
| Reactive return type trên Spring MVC | Bridge qua MVC async mechanism | Ghi Servlet response vẫn blocking ở các điểm tương ứng |

**Async Servlet không đồng nghĩa CPU không còn làm việc.** Nó giúp không giữ thread Servlet ban đầu xuyên suốt thời gian chờ result, nhưng downstream database query, HTTP client, file I/O và response writes có thể vẫn blocking ở nơi khác.

## 3. ThreadLocal: ngữ cảnh gắn với luồng, không gắn với request mãi mãi

Một `ThreadLocal<T>` giữ giá trị theo **thread hiện tại**:

~~~java
private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

TRACE_ID.set("req-42");
String here = TRACE_ID.get(); // "req-42" trên thread vừa set
~~~

Ví dụ này là đoạn Java minh họa trong class/method, không phải toàn bộ chương trình. Nếu executor chạy task trên **thread khác**, `TRACE_ID.get()` trên worker thường là `null`, hoặc tệ hơn là **giá trị cũ bị rò rỉ** do một tác vụ trước không cleanup.

Các holder liên quan trong hệ sinh thái Spring:

| Context | Điểm truy cập | Bản chất |
| --- | --- | --- |
| Correlation/log fields | SLF4J `MDC` | Thường dùng thread-local per logging implementation |
| Security authentication | `SecurityContextHolder` | Strategy mặc định thường thread-local |
| Locale/time zone | `LocaleContextHolder` | Context gắn với thread |
| Request attributes | `RequestContextHolder` | Thường gắn với thread xử lý request |
| Transaction | `TransactionSynchronizationManager` | Thread-bound transaction/resource state |
| Tracing observation | Micrometer Observation/trace context | Có cơ chế scope và context propagation riêng |
| Custom tenant/user metadata | Custom `ThreadLocal` | Phải tự quyết định lifecycle, confidentiality, cleanup |

**Không thể lấy một cơ chế propagate rồi mặc nhiên khẳng định đã mang hết các context còn lại.** Ví dụ MDC có trace ID nhưng worker vẫn có thể không có `SecurityContext` nếu không dùng integration tương ứng; ngược lại một `DelegatingSecurityContextAsyncTaskExecutor` không tự thêm MDC/LocaleContext tùy ý.

### Quy tắc lifecycle

1. **Capture** đúng context tại **điểm giao việc** (submission), không khi class được khởi tạo.
2. **Restore** trong worker ngay trước callback.
3. **Cleanup/restore previous value** trong `finally`, kể cả khi task throw.
4. Không giữ reference sống tới `HttpServletRequest`, `HttpSession`, JPA `EntityManager` hoặc security object mutable quá thời hạn cần thiết.
5. Với tác vụ nền kéo dài sau khi HTTP request hoàn tất, tốt hơn truyền **dữ liệu tối thiểu bất biến** (ID, tenant, principal identifier, traceparent/correlation ID) thay vì copy toàn bộ request scope.

`InheritableThreadLocal` không phải giải pháp an toàn mặc định cho thread pool: thread được tạo/tái sử dụng vào thời điểm khác và có thể giữ ngữ cảnh của công việc không liên quan. Đặc biệt không bật `RequestContextHolder.setRequestAttributes(..., true)` hoặc `SecurityContextHolder.MODE_INHERITABLETHREADLOCAL` với mong đợi nó xử lý đúng mọi pooled worker.

## 4. Truyền MDC qua `TaskDecorator` có cleanup

Khi một request có `X-Request-Id`, bạn thường muốn log trong handler, business service và worker đều chứa cùng request ID.

Một `TaskDecorator` có thể capture snapshot MDC lúc task được đưa vào executor, rồi đặt và khôi phục nó trong worker:

~~~java
package com.example.demo;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

public final class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> captured = MDC.getCopyOfContextMap();

        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (captured == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(captured);
                }
                runnable.run();
            } finally {
                if (previous == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        };
    }
}
~~~

Đây là **file Java hoàn chỉnh** dùng được trong project Spring Boot có SLF4J. Điểm đáng lưu ý:

- `getCopyOfContextMap()` tạo **snapshot**, không chia sẻ `Map` mutable giữa các request.
- `decorate` được gọi ở phía **submission**, giúp lấy context **trước** khi tác vụ chạy ở worker.
- Task đọc context chỉ trong lúc đang thực thi; finally khôi phục context cũ, không để correlation ID của request A tràn sang request B.
- Nếu task bị exception, `finally` vẫn cleanup. Một số executor đưa cho decorator wrapper khác với user task, nên **đừng dựa vào decorator để bắt mọi exception kinh doanh**; theo dõi future/callback/error handlers và logs.
- Chỉ nên propagate các trường log cần thiết. Không đưa password, cookie, bearer token hoặc PII nhạy cảm vào MDC.

### TaskDecorator không thay controller filter/interceptor

Để MDC có giá trị khi MVC submit `Callable`, request thread cần thiết lập correlation ID **trước khi task được submit**. Filter `OncePerRequestFilter` phù hợp trong ví dụ. Nếu controller tự `MDC.put` rồi `MDC.remove` ngay trong `finally` của handler, MVC có thể submit `Callable` **sau khi handler đã trả về**, khiến decorator chụp một context rỗng. Vì vậy đừng nhầm lifecycle của **controller method** với lifecycle của **Servlet filter chain**.

Một filter cũng cần xử lý dispatch types có chủ đích. `OncePerRequestFilter` có các quy tắc mặc định về async/error dispatch; nếu cần MDC trong cả pha async redispatch, hãy xác minh `shouldNotFilterAsyncDispatch`, filter registration và cách tái lập context trong từng dispatch. Bài demo tập trung MDC khi **worker chạy Callable**, không hứa trace context ở mọi filter/async dispatch.

## 5. Spring Security: context của Callable khác context của executor tùy ý

Với Spring Security 6.x, `SecurityContextHolder` mặc định dùng thread-local strategy. Spring Security có tích hợp với **Spring MVC `Callable`** qua `WebAsyncManagerIntegrationFilter` và `SecurityContextCallableProcessingInterceptor`. Khi filter chain Security hoạt động đúng, integration này có thể đưa `SecurityContext` của request sang thread chạy MVC Callable và dọn theo lifecycle.

**Nhưng điều này không tự bao phủ mọi loại async:**

- `DeferredResult` được một message listener hoàn thành trên thread khác: worker/listener đó không tự có context gốc chỉ vì MVC đã trả `DeferredResult`.
- `CompletableFuture.supplyAsync(...)` gọi `ForkJoinPool.commonPool()` mặc định khi không cấp executor và không tự kế thừa request SecurityContext.
- `@Async` sử dụng executor do Spring async infrastructure chọn, có thể khác hoàn toàn executor dành cho MVC.
- `StreamingResponseBody`, callbacks và producer tự quản lý cần được kiểm tra riêng, không suy luận từ ví dụ `Callable`.
- Async task sau khi user logout hoặc quyền thay đổi không thể xem bản SecurityContext chụp trước đó như một authorization vĩnh viễn.

Để chạy **ứng dụng tự submit task** với Spring Security, có thể dùng `DelegatingSecurityContextAsyncTaskExecutor`:

~~~java
@Bean("securityAwareExecutor")
public AsyncTaskExecutor securityAwareExecutor(
        @Qualifier("boundedWorkerExecutor")
        AsyncTaskExecutor delegate) {

    return new DelegatingSecurityContextAsyncTaskExecutor(delegate);
}
~~~

Đây là **method bean minh họa**, cần dependency Spring Security và bean `boundedWorkerExecutor` đã cấu hình, cũng như import `@Bean`, `@Qualifier`, `AsyncTaskExecutor` và `DelegatingSecurityContextAsyncTaskExecutor`. Với constructor chỉ truyền delegate, executor có thể chụp `SecurityContext` của người submit ở mỗi lần giao task, chạy task trong context đó rồi dọn sau. Nếu đưa một **SecurityContext cố định** vào constructor khác, tất cả task có thể cùng chạy dưới identity đó; đây là quyết định bảo mật riêng, không dùng nhầm trong multi-user web requests.

**Không đăng ký cùng một security context vào nhiều lớp wrapper một cách tùy tiện.** Cần hiểu Spring Security tự tích hợp `Callable` như thế nào, còn custom executor áp dụng cho task nào; kiểm thử với authentication giả lập thay vì chỉ thấy method trả 200 rồi kết luận không rò quyền.

### Authorization phải ở đúng ranh giới

- **Kiểm tra quyền request** trước khi giao việc và trước khi truy cập resource nhạy cảm.
- Nếu task tiếp tục sau một khoảng chờ dài, cân nhắc **kiểm tra quyền lại** khi lấy dữ liệu nhạy cảm, nhất là job nền.
- Chỉ truyền principal ID/tenant ID cần thiết vào task; tránh giữ reference tới mutable Authentication hoặc toàn bộ session dài hơn request.
- Không coi việc có `SecurityContext` trong một worker là bằng chứng nhiệm vụ được phép thực hiện mãi, vì quyền có thể thay đổi.

## 6. Locale, request attributes, tracing và Micrometer

### 6.1. LocaleContextHolder

Trong request MVC, `LocaleContextHolder.getLocale()` có thể dựa vào `LocaleResolver` hoặc cấu hình locale request. Worker tự tạo thông qua `new Thread(...)` không tự có locale ban đầu. Nếu thực thi công việc nền, hãy chụp **`Locale`** như một value object rồi truyền vào service, thay vì buộc service đọc thread-local:

~~~java
Locale locale = LocaleContextHolder.getLocale();
Callable<String> task = () -> renderMessage(locale, "welcome");
~~~

Đây là snippet minh họa trong method có `renderMessage(Locale,String)`. Muốn truyền timezone, chụp thêm `ZoneId` hoặc kiểu domain cần thiết. Cách truyền tham số tường minh giúp test dễ hơn và tránh giữ request attributes đã hết vòng đời.

### 6.2. RequestContextHolder

`RequestContextHolder.currentRequestAttributes()` có thể ném `IllegalStateException` khi không còn request bound với thread. Đừng “vá lỗi” bằng cách set lại nguyên `ServletRequestAttributes` vào một task có thể chạy sau khi HTTP response đã hoàn tất. Request, input stream, output stream và session có lifecycle riêng.

Tốt hơn:

~~~java
String tenantId = validatedTenantIdFromRequest;
String correlationId = validatedCorrelationId;
executor.execute(() -> work(tenantId, correlationId));
~~~

Đây là snippet minh họa khi `executor`, `work` và giá trị đầu vào đã được khai báo/validate. Chọn dữ liệu được sao chép như `String`, `UUID`, immutable record để không giữ servlet request object.

### 6.3. Micrometer Context Propagation

Spring Framework 6.1+ có `ContextPropagatingTaskDecorator` trong `org.springframework.core.task.support` cho những trường hợp dùng Micrometer Context Propagation. Nó hỗ trợ capture/restore logging/observation context trên các thread khác khi được cấu hình đúng:

~~~java
pool.setTaskDecorator(new ContextPropagatingTaskDecorator());
~~~

**Đây là đoạn minh họa bổ sung**, yêu cầu class/dependency Micrometer context propagation thích hợp, phiên bản Spring tương thích, cùng các context accessors đã đăng ký. Không nên khai báo đồng thời decorator tùy biến MDC và decorator Micrometer một cách không kiểm soát; chọn hoặc compose chúng theo nhu cầu thực và kiểm chứng thứ tự restore.

Theo tài liệu Spring MVC, các accessors như `LocaleContextThreadLocalAccessor` và `RequestAttributesThreadLocalAccessor` **cần đăng ký với `ContextRegistry` khi muốn sử dụng**, không tự xuất hiện trong mọi project. Với `ContextSnapshot`:

~~~java
ContextSnapshot snapshot = ContextSnapshot.captureAll();
executor.execute(() -> {
    try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
        doWork();
    }
});
~~~

Đây là snippet khái niệm cho ứng dụng đã cấu hình Micrometer và các imports phù hợp; `executor`/`doWork` là dependencies bên ngoài. Snapshot được chụp ở thread caller; scope khôi phục context trong worker và trả lại trạng thái cũ khi đóng.

**Cần thận trọng:** tự copy request scope qua worker lâu dài có thể giữ đối tượng và dữ liệu quá vòng đời. Context propagation là một chính sách cần thiết kế, không phải cơ chế “clone toàn bộ web request”.

### 6.4. Transaction không mặc nhiên đi cùng thread

`@Transactional` của Spring quản lý transaction resources theo thread. Một `Callable` chạy ở worker **không tự tiếp tục transaction** đã bắt đầu trong controller request thread. Tách transaction ở service chạy **bên trong worker**:

~~~java
return () -> reportService.loadReport(id);
~~~

Ở đây `reportService.loadReport(id)` có thể là public method `@Transactional(readOnly = true)` trên Spring-managed service proxy. Nếu chính method controller được annotate `@Transactional` rồi chỉ return `Callable`, transaction của controller có thể đã kết thúc **trước khi callable được thực thi**.

Không share một JPA `EntityManager` hoặc `Session` thread-bound cho nhiều async tasks. Kiểm tra lazy-loaded entities ở ranh giới async; có thể load DTO cần thiết trong transaction worker.

## 7. An toàn luồng: controller và service là singleton theo mặc định

Spring `@RestController`, `@Service`, `@Component` thường là **singleton beans**. Nhiều request đồng thời gọi cùng một instance. Nếu controller có field mutable và không đồng bộ:

~~~java
@RestController
public class UnsafeCounterController {
    private int count = 0;

    @GetMapping("/counter")
    public int counter() {
        return ++count;
    }
}
~~~

Đây là class minh họa anti-pattern, cần import `@RestController`/`@GetMapping`. Toán tử `++` gồm đọc–tăng–ghi, không atomic. Hai request có thể làm mất increment. Khi async task cũng truy cập field, tình huống race càng rõ.

Nếu thật sự cần counter trong memory (chỉ để demo/metrics đơn giản), dùng:

~~~java
private final AtomicInteger count = new AtomicInteger();

int current = count.incrementAndGet();
~~~

`AtomicInteger` bảo vệ atomic update trong **một process**, nhưng không tự đồng bộ nhiều application instances. Nếu là số dư ngân hàng, business sequence, quota hoặc inventory, cần đồng bộ/transaction/constraint ở storage hoặc service chuyên dụng, không chỉ một AtomicInteger.

### Các kiểu dữ liệu và cơ chế phù hợp

| Loại state | Cách xử lý | Lỗi cần tránh |
| --- | --- | --- |
| Request DTO | Immutable record/local variable | Mutate object chung giữa requests |
| Request ID, tenant ID | Immutable snapshot / explicit parameter | `ThreadLocal` không cleanup |
| Counter đơn giản | `AtomicInteger`, metrics backend | `int++` trên singleton |
| Map chia sẻ in-process | `ConcurrentHashMap` khi hợp đồng phù hợp | `HashMap` đồng thời không khóa |
| Compound update nhiều bước | Lock / atomic compute / DB transaction | Nghĩ ConcurrentHashMap làm mọi chuỗi thao tác atomic |
| File/stream | Một thread sở hữu hoặc synchronization có hợp đồng | Nhiều thread ghi chung `OutputStream` tùy ý |
| Database entity manager | Scope transaction/thread hợp lệ | Share `EntityManager` giữa async tasks |
| Global configuration | Immutable `record`/`final` fields | Ghi mutable config khi xử lý request |

**Không truyền mutable `HashMap`, `ArrayList` hoặc một `StringBuilder` dùng chung vào nhiều callbacks** nếu chúng không có synchronization. Mỗi async request nên sở hữu dữ liệu riêng, hoặc cần concurrency control rõ ràng.

### Atomic không đồng nghĩa an toàn nghiệp vụ

~~~java
int current = counter.get();
if (current < limit) {
    counter.incrementAndGet();
}
~~~

Đoạn trên vẫn có race: hai thread cùng đọc `current < limit`, đều increment vượt quota. Sử dụng thao tác CAS/atomic update hoặc storage transactional tương ứng với business constraint. Đừng rút kết luận “dùng biến atomic nên mọi thứ thread-safe”.

### `volatile` có giới hạn

`volatile` hỗ trợ visibility và ordering nhất định, nhưng `volatile int count` **không làm** `count++` atomic. `synchronized`, `ReentrantLock`, atomic classes hoặc bất biến (immutability) phải được chọn theo thao tác thực tế; không dùng một `synchronized` toàn controller để vô tình serialize mọi request nếu throughput cần cao.

## 8. Tránh executor không giới hạn: ba nguồn rủi ro

Một ví dụ không phù hợp cho tải production:

~~~java
ExecutorService executor = Executors.newCachedThreadPool();
~~~

Một cached thread pool có thể tạo thêm thread khi nhiều tác vụ đang bận mà không có giới hạn thread cố định thích hợp; dưới tải cao, memory và scheduler pressure tăng mạnh. `newFixedThreadPool(8)` giới hạn worker threads nhưng dùng **unbounded queue** mặc định — vẫn có thể gom rất nhiều tác vụ pending và gây out-of-memory/latency lớn. Vì vậy “thread pool có 8 thread” **không** đồng nghĩa “tổng công việc đã được giới hạn”.

Tương tự:

~~~java
CompletableFuture.supplyAsync(() -> slowCall());
~~~

Khi không truyền executor, nó có thể dùng **ForkJoin common pool** thay vì executor MVC mà bạn định cấu hình; thread context/observability/capacity không tự theo rule của MVC. Common pool thích hợp một số công việc CPU-bound có kiểm soát, nhưng đừng dùng như một lối tắt tổng quát cho I/O blocking của request.

### Sức chứa thực tế của ThreadPoolTaskExecutor

Các tham số chính:

- `corePoolSize`: số worker mục tiêu tối thiểu theo policy thread pool.
- `maxPoolSize`: số worker tối đa khi queue đầy và pool cần tăng.
- `queueCapacity`: số task pending cho phép trước khi từ chối hoặc thử tăng worker.
- `keepAliveSeconds`: thời gian idle trước khi non-core threads được thu hồi.
- `RejectedExecutionHandler`: phản ứng khi đã tới capacity; **AbortPolicy** thường an toàn hơn silently dropping task.
- Timeout/cancellation: giới hạn thời gian xử lý, tránh giữ một task vô hạn.
- Monitoring: active threads, pool size, queue depth, rejection count, latency, timeout, disconnect rate.

Ví dụ `core=2, max=4, queue=16`: khi hai worker bận, phần lớn task mới trước tiên vào queue; pool thường chỉ tăng từ 2 lên 4 **sau khi queue đầy**. Khi đã có 4 worker và queue 16 tác vụ, task đến tiếp có thể bị reject. Đây là đặc điểm của `ThreadPoolExecutor` mà `ThreadPoolTaskExecutor` sử dụng theo cấu hình phổ biến; không coi `maxPoolSize=4` là “luôn chạy ngay 4 thread song song” khi queue chưa đầy.

### Đặt queue lớn có thể xấu hơn

Nếu mỗi task mất 5 giây và chỉ 4 worker chạy, một queue 10.000 request có thể giữ rất nhiều object/request snapshots, đồng thời khách hàng chờ vô nghĩa và timeout. Queue giới hạn buộc hệ thống **thừa nhận quá tải** sớm hơn; cần xử lý rejection ở HTTP boundary, capacity planning và đặt giới hạn concurrency tổng thể.

Ngưỡng phù hợp phụ thuộc tài nguyên downstream:
- DB connection pool có 10 connections: 100 worker I/O đồng thời chưa chắc làm nhanh hơn.
- API upstream có rate limit 20 QPS: cần rate limit/circuit breaker/timeout, không tăng thread vô hạn.
- File streaming tới client chậm: một callback blocking có thể giữ worker rất lâu.
- CPU-bound job: tăng thread vượt số CPU cores có thể tăng context switching.

**Little's Law** là trực giác hữu ích: số việc đang xử lý/chờ xấp xỉ arrival rate × thời gian ở hệ thống khi ổn định. Nếu request đến nhanh hơn tốc độ hoàn thành lâu dài, bất kỳ queue hữu hạn nào cũng đầy — giải pháp là giảm tải hoặc tăng năng lực thật, không phải đổi sang queue vô hạn.

## 9. Ví dụ chạy được: bounded MVC executor + MDC propagation

Ví dụ dưới đây có:

- `ThreadPoolTaskExecutor` giới hạn **core=2**, **max=4**, **queue=16**, tên luồng `mvc-worker-`, timeout MVC **5 giây**.
- `MdcTaskDecorator` có snapshot/restore trong finally.
- Một `OncePerRequestFilter` tạo/kiểm tra `X-Request-Id` và đặt `MDC["requestId"]` trong phạm vi filter.
- Controller trả `Callable<WorkResponse>`; worker đọc MDC đã được propagate và trả immutable record.
- MockMvc test dùng `asyncDispatch`, kiểm chứng request/worker trace giống nhau, thread worker khác request handling.
- Chỉ dùng Spring Boot 3.x web/test; **không yêu cầu Spring Security hoặc Micrometer** trong runnable example.

Dự án Maven dùng Java 17+, Spring Boot 3.x, dependencies `spring-boot-starter-web`, `spring-boot-starter-test`, package `com.example.demo`.

Cấu trúc:

~~~text
src/main/java/com/example/demo/DemoApplication.java
src/main/java/com/example/demo/MdcTaskDecorator.java
src/main/java/com/example/demo/AsyncMvcConfig.java
src/main/java/com/example/demo/RequestIdMdcFilter.java
src/main/java/com/example/demo/AsyncWorkController.java
src/test/java/com/example/demo/AsyncWorkControllerTest.java
~~~

### 9.1. Ứng dụng chính

**`DemoApplication.java`**

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

### 9.2. MdcTaskDecorator.java

Sử dụng toàn bộ nội dung file đã giới thiệu ở mục 4, dưới package `com.example.demo`. Cần **đặt đúng file/class riêng**; không khai báo hai `MdcTaskDecorator` với cùng full-qualified class name.

### 9.3. AsyncMvcConfig.java

~~~java
package com.example.demo;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class AsyncMvcConfig implements WebMvcConfigurer {

    private final AsyncTaskExecutor executor;

    public AsyncMvcConfig(
            @Qualifier("mvcAsyncExecutor") AsyncTaskExecutor executor) {
        this.executor = executor;
    }

    // Static bean method tránh vòng tham chiếu với chính configuration.
    @Bean("mvcAsyncExecutor")
    public static ThreadPoolTaskExecutor mvcAsyncExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(4);
        pool.setQueueCapacity(16);
        pool.setKeepAliveSeconds(30);
        pool.setThreadNamePrefix("mvc-worker-");
        pool.setTaskDecorator(new MdcTaskDecorator());
        pool.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return pool;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(executor);
        configurer.setDefaultTimeout(5_000L); // milliseconds
    }
}
~~~

Các tham số kích thước chỉ là **demo**. Trong production, cần thiết kế queue/rejection theo SLO, throughput, loại công việc, database pool và số pod/server. Spring Framework docs nhấn mạnh executor mặc định phục vụ MVC async **không phù hợp cho production dưới tải**; cấu hình explicit là khuyến nghị quan trọng.

**Không cần** thêm `@EnableAsync` chỉ để dùng `Callable` của Spring MVC. `@EnableAsync` phục vụ hạ tầng `@Async` riêng, có thể dùng một executor khác.

### 9.4. RequestIdMdcFilter.java

~~~java
package com.example.demo;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String candidate = request.getHeader("X-Request-Id");
        String requestId = candidate != null &&
                candidate.matches("[A-Za-z0-9_-]{1,64}")
                ? candidate : UUID.randomUUID().toString();

        String previous = MDC.get("requestId");
        try {
            MDC.put("requestId", requestId);
            chain.doFilter(request, response);
        } finally {
            if (previous == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previous);
            }
        }
    }
}
~~~

**Vì sao filter này không có memory leak đơn giản?**

- Giá trị `requestId` giới hạn tối đa 64 ký tự và chỉ chứa ký tự allowlist, giảm nguy cơ log injection qua header.
- Context được đặt **trước** `DispatcherServlet`, nên lúc MVC submit `Callable`, decorator có thể capture MDC đang active.
- `finally` restore context của thread ban đầu, kể cả exception.
- Worker thread cũng restore context cũ nhờ `MdcTaskDecorator`.
- Filter mặc định có hành vi cụ thể với ASYNC dispatch; demo **chỉ chứng minh propagation ở worker**. Nếu muốn log MDC trong toàn bộ redispatch/filter/controller advice, cần đăng ký/kiểm thử chiến lược phù hợp thay vì mặc định nó vẫn tồn tại.

Trong hệ thống thực, correlation ID có thể được ingress/gateway sinh ra. Không tin mù quáng `X-Request-Id` của mọi client, nhất là khi dùng để liên hệ logs liên tổ chức; áp dụng chính sách đổi tên, validate, sinh ID mới hoặc ánh xạ trace ID tùy trust boundary.

### 9.5. AsyncWorkController.java

~~~java
package com.example.demo;

import java.util.concurrent.Callable;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/async")
public class AsyncWorkController {

    @GetMapping("/work")
    public Callable<WorkResponse> work(
            @RequestParam(defaultValue = "demo") String job) {

        if (job.length() > 40) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "job too long");
        }

        // Snapshot dữ liệu request trước khi rời thread Servlet.
        String requestTrace = MDC.get("requestId");

        return () -> {
            // Chạy trên bounded MVC AsyncTaskExecutor.
            String workerTrace = MDC.get("requestId");
            String workerThread = Thread.currentThread().getName();

            return new WorkResponse(
                    job, requestTrace, workerTrace, workerThread);
        };
    }

    public record WorkResponse(
            String job,
            String requestTrace,
            String workerTrace,
            String workerThread) {}
}
~~~

**Ở đây `Callable` không chứa `Thread.sleep`** vì mục tiêu là chứng minh thread context và bounded executor một cách ổn định khi test. Công việc thực sự có thể là gọi service hoặc đọc file, nhưng phải chọn executor phù hợp với mức blocking, giới hạn thời gian và số tài nguyên downstream.

`WorkResponse` là Java record bất biến; không để một mutable shared field trong singleton controller. Cùng `job` và `requestTrace` là snapshots của request, không cần truy cập `HttpServletRequest` trên worker.

### 9.6. Kiểm thử MockMvc bằng asyncDispatch

**`src/test/java/com/example/demo/AsyncWorkControllerTest.java`**

~~~java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .status;

@SpringBootTest
@AutoConfigureMockMvc
class AsyncWorkControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void callableCarriesMdcAndUsesBoundedExecutor() throws Exception {
        MvcResult pending = mockMvc.perform(
                        get("/api/async/work")
                                .param("job", "report")
                                .header("X-Request-Id", "req-123"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completed = mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job").value("report"))
                .andExpect(jsonPath("$.requestTrace").value("req-123"))
                .andExpect(jsonPath("$.workerTrace").value("req-123"))
                .andReturn();

        String json = completed.getResponse().getContentAsString();
        assertTrue(json.contains("mvc-worker-"));
    }

    @Test
    void rejectsJobNameThatIsTooLong() throws Exception {
        mockMvc.perform(get("/api/async/work")
                        .param("job", "a".repeat(41)))
                .andExpect(status().isBadRequest());
    }
}
~~~

**Đọc đúng giá trị của test:**
- `request().asyncStarted()` xác nhận request đã vào async handling.
- `asyncDispatch(pending)` đưa kết quả đã hoàn thành qua Servlet/MVC dispatch để hoàn tất response; chỉ lúc đó mới assert JSON.
- `requestTrace=workerTrace` chứng minh MDC propagation trong đường `Callable` demo này.
- `workerThread` chứa prefix `mvc-worker-` chứng minh tác vụ đã chạy trên pool đặt tên.
- Test **không chứng minh pool thật sự chống quá tải** dưới hàng nghìn clients. Cần thêm load/integration tests để đo active threads, queue depth, rejection, timeouts, memory, DB pool và 503.
- Test không thêm Spring Security; nếu ứng dụng thực có Security filters, cấu hình/mock authentication phải tương thích.

Chạy test:

~~~bash
./mvnw test
~~~

Trên Windows dùng `mvnw.cmd test`; nếu thiếu Maven wrapper, dùng `mvn test` khi Maven đã được cài.

### 9.7. Gọi curl

Chạy ứng dụng:

~~~bash
./mvnw spring-boot:run
~~~

Sau đó:

~~~bash
curl -i   -H "X-Request-Id: req-123"   "http://localhost:8080/api/async/work?job=report"
~~~

Kết quả HTTP 200 có JSON **cùng trace ID**:

~~~json
{
  "job": "report",
  "requestTrace": "req-123",
  "workerTrace": "req-123",
  "workerThread": "mvc-worker-1"
}
~~~

**Lưu ý:** tên thread `mvc-worker-1` chỉ minh họa; worker thực tế có thể là `mvc-worker-2` hoặc tên khác theo thứ tự scheduling. Không assert chính xác tên/ID thread. Điều cần kiểm chứng là prefix và trace ID.

Các lệnh xuống dòng dùng dấu `\` của Linux/macOS/Git Bash. Trên Windows PowerShell, dùng `curl.exe` trên một dòng hoặc cú pháp nối dòng của PowerShell.

## 10. DeferredResult: producer chạy đâu và cleanup thế nào?

`DeferredResult` cho phép trả response từ một sự kiện/tác vụ hoàn thành sau này:

~~~java
@GetMapping("/wait-for-message")
public DeferredResult<String> waitForMessage() {
    DeferredResult<String> result =
            new DeferredResult<>(5_000L, "timeout");

    messageBroker.registerListener(payload -> {
        result.setResult(payload);
    });

    result.onCompletion(() ->
            messageBroker.unregisterListener(result));

    return result;
}
~~~

Đây là **minh họa khái niệm**, chưa chạy độc lập vì `messageBroker` và API register/unregister giả định theo hệ thống. Callback producer có thể chạy trên thread broker. Ở những implementation có listener handle, nên lưu đúng subscription/token để unsubscribe trong `onCompletion`, `onTimeout` và `onError` khi thích hợp.

Điểm cần áp dụng:

- **Không tạo một thread riêng trên mỗi request** chỉ để chờ event; dùng infrastructure/event-loop/message broker đã có hoặc bounded executor chuyên trách.
- Đừng giữ `DeferredResult` trong một map vô hạn mà không giới hạn số subscribers hoặc cleanup sau completion/timeout/disconnect.
- Sử dụng `setResult`/`setErrorResult` có tính cạnh tranh (request có thể timeout đồng thời); giá trị trả về của operation nếu available nên được quan sát.
- Context từ request ban đầu **không tự xuất hiện** trên broker listener. Chụp các immutable fields cần thiết, hoặc dùng snapshot/propagation mechanism của producer.
- Nếu producer exception sau khi request timeout, không có guarantee có thể trả body lỗi như khi response chưa commit.

### WebAsyncTask cho một task có timeout riêng

~~~java
@GetMapping("/slow-task")
public WebAsyncTask<String> slowTask() {
    WebAsyncTask<String> task = new WebAsyncTask<>(
            2_000L, () -> slowService.call());

    task.onTimeout(() -> "timeout");
    task.onError(() -> "error");
    return task;
}
~~~

Đây là handler minh họa, cần import `WebAsyncTask`, `@GetMapping` và `slowService` đã được inject. `WebAsyncTask` gắn timeout/callback cho tác vụ MVC `Callable`. **HTTP request timeout không phải cơ chế dừng chắc chắn công việc đã chạy ở worker**; nếu downstream call không hỗ trợ cancel/timeout, nó có thể tiếp tục tốn tài nguyên sau khi client đã nhận response timeout.

Nếu chọn fallback `"timeout"`, phải cân nhắc mã HTTP và contract API; không mặc định một chuỗi fallback với status 200 là lựa chọn đúng cho mọi endpoint.

## 11. Timeout, cancellation, rejection và backpressure

### 11.1. Timeout nhiều lớp

| Tầng | Ví dụ | Tác dụng |
| --- | --- | --- |
| Spring MVC async timeout | `configurer.setDefaultTimeout(5_000)` | Giới hạn thời gian chờ async result theo lifecycle Servlet |
| HTTP client timeout | Connection/read/response timeout khi gọi upstream | Giới hạn thời gian worker chờ bên ngoài |
| DB query timeout | Transaction/query timeout | Giới hạn công việc ở database |
| Queue wait timeout | Deadline trước khi task chạy | Không để request nằm chờ vô nghĩa |
| Reverse proxy idle timeout | Nginx/ingress/CDN | Có thể đóng kết nối trước khi Spring xong |
| Browser/client abort | Người dùng đóng trang, AbortController | Client không cần result nữa; server phải xử lý cleanup |
| Business deadline | Deadline tổng hợp với context/cancellation | Tránh thao tác tiếp tục sau khi hết giá trị |

**Đừng chỉ đặt `setDefaultTimeout` và nghĩ task blocking sẽ bị stop.** Spring/Servlet có thể kết thúc request và giải phóng response, nhưng task đang gọi dịch vụ chậm vẫn cần timeout/cancellation ở I/O tương ứng. Java interruption không phải lúc nào cũng phá được mọi blocking API.

### 11.2. Rejection khi pool đầy

Với `AbortPolicy`, task bị từ chối khi capacity đã hết. Trong Spring Task execution, có thể thấy `TaskRejectedException` hoặc exception được wrapping/tuyến xử lý tương ứng, tùy API/phiên bản.

Một kiến trúc phù hợp:
1. Không nuốt rejection rồi để client treo không có response.
2. Chuyển rejection được nhận diện tại HTTP boundary thành **503 Service Unavailable** hoặc status theo contract, có thể kèm `Retry-After` khi có chiến lược retry thích hợp.
3. **Không tự retry ngay trong request với vòng lặp vô hạn**, vì làm tải nặng hơn.
4. Đặt queue/rejection metrics, alert, rate limiting, admission control.
5. Kiểm thử thật với tác vụ blocking có kiểm soát trong môi trường isolated để chứng minh pool chạm giới hạn và response được xử lý đúng.

Ví dụ đoạn handler ở một tầng đã xác định exception đến được MVC:

~~~java
@RestControllerAdvice
class CapacityErrorAdvice {

    @ExceptionHandler(TaskRejectedException.class)
    ResponseEntity<Map<String, String>> overloaded() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "SERVER_BUSY"));
    }
}
~~~

Đây là **class minh họa**, không phải phần cần bổ sung để runnable demo mục 9. Cần import `@RestControllerAdvice`, `@ExceptionHandler`, `TaskRejectedException`, `ResponseEntity`, `HttpStatus`, `Map`. **Không bảo đảm** mọi exception executor đều đi qua `@RestControllerAdvice`: có lỗi phát sinh trong async submission, filter, executor scheduler hoặc sau response commit. Test end-to-end đúng đường thực tế, bổ sung filter/container error handling khi cần.

### 11.3. Chính sách CallerRuns không luôn tốt cho MVC

`CallerRunsPolicy` dùng thread caller thực thi task khi pool đầy. Nó có thể tạo backpressure ở một số worker pipeline nhưng trong MVC request path, thread caller thường là **Servlet request thread**; nếu tác vụ blocking dài thì bạn có thể chiếm thread Servlet và mất lợi ích của async. Không chọn CallerRuns chỉ vì “không muốn mất tác vụ”, mà phải đánh giá tác động tới toàn server.

**DiscardPolicy** bỏ tác vụ mà không bảo đảm response được hoàn tất, đặc biệt nguy hiểm nếu task tương ứng một `Callable` đang chờ; tránh dùng cho request/response workloads trừ khi thiết kế có completion/error handling rõ ràng.

### 11.4. Semaphore/admission control bổ sung

Một bounded queue hạn chế tổng pending tasks trong một instance, nhưng không giới hạn tuyệt đối số kết nối HTTP/SSE/file streams hay số concurrent DB queries nếu các executor khác cũng hoạt động. Có thể đặt semaphore per downstream hoặc per-tenant quota trước khi submit task, trả 429/503 khi hết slot, và release trong finally.

~~~java
if (!semaphore.tryAcquire()) {
    throw new ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS, "too many concurrent jobs");
}
try {
    return compute();
} finally {
    semaphore.release();
}
~~~

Đây là **đoạn minh họa trong service/method chạy công việc**, nơi `semaphore`, `compute` và imports đã tồn tại. Phải đặt `release` đúng lifecycle **khi công việc thực sự kết thúc**, không chỉ khi controller trả `Callable`. Một semaphore in-process không tự giới hạn tổng tải của tất cả application replicas.

## 12. CompletableFuture, @Async và những executor khác nhau

### `CompletableFuture`

~~~java
CompletableFuture<Report> future =
        CompletableFuture.supplyAsync(
                () -> reportService.generate(id),
                boundedExecutor);
~~~

Đây là snippet khi `reportService`, `id`, `boundedExecutor` đã có. Chỉ định **executor có giới hạn** thay vì thả `supplyAsync` vào common pool theo mặc định. Nếu callback cần request context, capture immutable IDs và propagation ở executor. Đừng giữ `HttpServletRequest`/JPA entity sống trên async boundary.

Khi ghép future, phải xét các stages:
- `thenApply` có thể chạy trên thread hoàn thành previous stage.
- `thenApplyAsync` không nhận executor có thể dùng default async facility, thường là common pool.
- `thenApplyAsync(..., boundedExecutor)` rõ nơi chạy tiếp, nhưng vẫn cần thiết kế timeout và propagation.
- `exceptionally`, `handle`, `whenComplete` cần dọn tài nguyên và lỗi đúng contract.

### `@Async`

~~~java
@Service
class ReportService {

    @Async("reportExecutor")
    public CompletableFuture<String> generate() {
        return CompletableFuture.completedFuture("done");
    }
}
~~~

Đây là **class minh họa**, cần imports và bean `reportExecutor` cùng `@EnableAsync` trong cấu hình. Spring `@Async` theo proxy AOP thường chỉ intercept **lời gọi qua Spring proxy**; self-invocation như `this.generate()` trong cùng bean sẽ không tự chuyển thread.

MVC `configureAsyncSupport(...).setTaskExecutor(mvcExecutor)` và executor dùng bởi `@Async` **là hai cơ chế cấu hình khác nhau**. Nếu muốn chia một pool, phải chủ động khai báo/đăng ký cùng bean và đánh giá việc jobs dài làm nghẽn request async. Nhiều hệ thống nên **tách pool theo workload** (quick MVC callbacks, report jobs, streaming), với quota và shutdown policies riêng.

## 13. ResponseBodyEmitter, SSE và StreamingResponseBody: bài toán thread khác nữa

### 13.1. StreamingResponseBody

~~~java
@GetMapping("/download")
public StreamingResponseBody download() {
    return outputStream -> {
        try (InputStream input = fileService.openAuthorizedFile()) {
            input.transferTo(outputStream);
        }
    };
}
~~~

Đây là handler minh họa, cần `fileService` và imports `StreamingResponseBody`, `InputStream`, `@GetMapping`. Chỉ mở source stream **bên trong callback**, không mở trong controller rồi đóng trước khi task async chạy. `transferTo` sao chép theo stream, không yêu cầu đưa toàn file vào heap; nhưng ghi response vẫn có thể **blocking** do client chậm.

Nếu dùng cùng executor cho `Callable` trả JSON và downloads kéo dài, 4 file downloads chậm có thể chiếm hết worker, khiến các request bình thường bị queue và timeout. Cân nhắc tách workload và đặt rate/concurrency limits khi có nhiều stream.

### 13.2. SseEmitter

~~~java
@GetMapping(path = "/events",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter events() {
    SseEmitter emitter = new SseEmitter(30_000L);
    emitter.onCompletion(() -> subscriptions.remove(emitter));
    emitter.onTimeout(() -> subscriptions.remove(emitter));
    subscriptions.add(emitter);
    return emitter;
}
~~~

Đây là snippet minh họa giả định `subscriptions` là collection được thiết kế thread-safe (ví dụ concurrent set) và được quản lý tại service. Chỉ phần controller đăng ký emitter **không gửi sự kiện**; producer của ứng dụng phải gửi sự kiện và gọi `complete`/`completeWithError` khi thích hợp.

Trách nhiệm quản lý:

- Giới hạn số SSE clients, thời gian kết nối, buffered outbound events và memory per subscriber.
- Không gửi từ mọi producer thread một cách hỗn loạn vào cùng `SseEmitter` nếu code không điều phối concurrent writes.
- Dùng heartbeat/periodic events để phát hiện client disconnect trong môi trường Servlet; client mất kết nối có thể chỉ được phát hiện khi write xảy ra.
- Cleanup onCompletion/onTimeout/onError, kể cả khi lỗi xảy ra lúc producer đã push.
- Đừng giữ `SecurityContext`, `HttpServletRequest` hoặc các `ThreadLocal` của subscriber suốt một kết nối SSE dài.

Tương tự `ResponseBodyEmitter`: dữ liệu được producer gửi bất đồng bộ nhưng lifecycle callbacks, serialization/blocking writes và resource ownership phải được quản lý riêng.

## 14. Testing đúng: MockMvc, đồng thời và quan sát sản xuất

### 14.1. MockMvc cho correctness

Các assertions cốt lõi với `Callable` là:
- Request ban đầu `asyncStarted`.
- Async result được **dispatch**.
- Kết quả JSON đúng và context cần thiết đã propagate.
- Error/timeout nếu được xử lý theo contract.

Không dùng `Thread.sleep(...)` để đoán async worker đã hoàn thành trong test. `MockMvc` cung cấp async result handling và `asyncDispatch`; test phải chờ đúng lifecycle.

### 14.2. Kiểm thử tải/rejection bằng HTTP thật

Để kiểm tra pool capacity thực sự, chạy server trên HTTP socket thật và tạo nhiều request cùng lúc tới endpoint có tác vụ được chặn bằng synchronization có kiểm soát trong **môi trường test**. Đo:
- Số active worker không vượt configured max.
- Queue không vượt bound.
- Request tiếp theo được reject/timeout đúng policy.
- Không mất `MDC`, không rò context giữa request IDs khác nhau.
- Response lỗi hợp lệ khi pool không còn capacity.
- Recovery sau overload: queue giảm, thread count ổn, memory không tăng mãi.

**Không chạy load test cực lớn trên production** để “kiểm tra unbounded executor” vì có thể làm dịch vụ gián đoạn. Chọn test environment, cấp traffic budget và quan sát metrics.

### 14.3. Spring Boot Actuator / Micrometer metrics

Nếu dùng Spring Boot Actuator và metrics registry tương ứng, bạn có thể giám sát:
- Active thread/pool/queue metrics của executor được bind đúng.
- HTTP server requests duration và status 2xx/4xx/5xx.
- JVM heap, threads, GC pauses, CPU.
- Database connection pool saturation.
- Rate của 503/429/rejection, async timeout, client disconnect.
- Trace/log correlation across workers và downstream calls.

Đừng chỉ dựa vào tổng số HTTP 200. Một hệ thống có queue 10.000 jobs có thể vẫn trả nhiều 200 nhưng p99 latency và bộ nhớ tăng xấu.

### 14.4. Kiểm tra cross-request leakage

Gửi request A có `X-Request-Id: req-A` và B có `X-Request-Id: req-B`. Khi worker thread được tái dùng, A không được nhìn thấy `req-B` và ngược lại. Test tuần tự có thể chưa đủ để bắt race; cần concurrent stress với nhiều request IDs và kiểm tra trace fields trên kết quả/logs.

MDC cleanup trong `finally` là lớp bảo vệ quan trọng, nhưng nếu ứng dụng tự tạo `ThreadLocal` khác, phải kiểm thử và cleanup chính context đó.

## 15. Sai lầm thường gặp

| Sai lầm | Vì sao sai | Cách làm tốt |
| --- | --- | --- |
| Tin `@Async` tự dùng executor MVC | Hai cơ chế có thể khác nhau | Khai báo rõ executor của từng workload |
| Tin MVC async biến I/O blocking thành non-blocking | Worker vẫn bị block bởi DB/network/stream | Giới hạn concurrency, timeouts và chọn I/O stack phù hợp |
| Dùng cached pool không giới hạn | Thread/memory/scheduler tăng khi tải dồn | Pool/queue bound, admission control |
| Dùng fixed pool với queue mặc định vô hạn | Pending tasks có thể tăng không giới hạn | `ThreadPoolTaskExecutor` queueCapacity rõ |
| Chỉ truyền MDC không truyền SecurityContext | Hai holder/semantics khác nhau | Spring Security integration và wrappers đúng |
| Dùng InheritableThreadLocal trong pool | Thread reuse giữ context không đúng request | Capture/restore/cleanup theo task |
| Tắt MDC trong controller finally trước submit | Async submission có thể sau controller return | Thiết lập ở request filter hoặc capture ID tường minh |
| Giữ `HttpServletRequest` trong job nền | Request có thể hoàn tất/không còn hợp lệ | Immutable snapshots trước submit |
| Chia sẻ `EntityManager` giữa threads | Thread-bound transaction semantics | Service transaction trong worker |
| `volatile int count++` | Increment vẫn không atomic | Atomic/lock/transaction phù hợp |
| Có `ConcurrentHashMap` là mọi logic atomic | Chuỗi read-check-write còn race | Atomic map methods/transactions |
| Nuốt rejection để request “chờ” | Request có thể timeout mà không ai hoàn thành | Báo 503/429 hoặc retry có giới hạn |
| Chỉ có MVC request timeout | Downstream blocking task vẫn chạy | Deadline và cancellation ở từng I/O |
| `CallerRunsPolicy` cho MVC request | Có thể block Servlet request thread | Đánh giá overload semantics riêng |
| Giữ hàng nghìn SSE emitters không cleanup | Memory và connection resource leak | Bound subscriber count, timeout/cleanup |
| Dùng `MockMvc` để chứng minh không thể quá tải | MockMvc không thay HTTP load/production metrics | Integration/load tests đúng workload |
| Log session token trong MDC | Rò credential qua logs | Chỉ propagate correlation metadata cần thiết |

## 16. Câu hỏi ôn tập

**1. Vì sao `Callable<T>` có thể cần propagation nhưng method controller không?**

Controller chạy trên Servlet request thread với context ban đầu; `Callable.call()` chạy sau trên worker thread khác. Các giá trị thread-local không tự có trên worker theo mọi trường hợp.

**2. `TaskDecorator` phải làm gì ngoài `MDC.setContextMap`?**

Capture snapshot khi submit và **restore/clear trong finally** sau worker, để không rò dữ liệu giữa những request khi thread được tái sử dụng.

**3. Spring Security 6.x đã tích hợp MVC Callable thì DeferredResult producer cũng tự có SecurityContext?**

Không. Tích hợp `WebAsyncManagerIntegrationFilter` áp dụng theo lifecycle Callable; event/broker producer hoặc custom executor cần cơ chế propagate riêng.

**4. `newFixedThreadPool(8)` có thực sự giới hạn số tác vụ đang pending không?**

Không. Nó giới hạn thread, nhưng hàng đợi mặc định của factory thường không giới hạn, nên pending tasks/memory vẫn có thể tăng cao.

**5. Với `core=2,max=4,queue=16`, khi nào pool tăng lên worker thứ ba?**

Trong ThreadPoolExecutor phổ biến, sau khi đã có core threads, task mới được queue trước. Khi queue đầy, pool mới tìm cách tăng worker tới max; sau đó reject nếu toàn capacity đã đạt.

**6. `setDefaultTimeout(5000)` có tự interrupt mọi DB query đang block?**

Không chắc. Nó quy định MVC async timeout theo Servlet lifecycle; cần query/socket/call timeout và cancellation phù hợp từng downstream.

**7. `ThreadLocal` của transaction có nên copy sang async worker không?**

Không nên copy thô. Mở transaction riêng trong service thực thi ở worker để thread-bound resources được quản lý đúng.

**8. Vì sao Stream/SSE cần concurrency limit dù đã dùng async?**

Nhiều stream vẫn giữ connections, buffers và có thể giữ threads khi ghi blocking tới client chậm. Async request không tạo tài nguyên vô hạn.

**9. Có thể dùng `CompletableFuture.supplyAsync(...)` không truyền executor cho nhiều blocking HTTP calls?**

Không nên mặc định, vì common pool và context handling/capacity không nhất thiết phù hợp; chỉ định bounded executor cho workload tương ứng.

**10. Nếu thấy log của request A hiện trong request B, kiểm tra gì?**

Kiểm tra MDC/ThreadLocal cleanup trong finally, worker reuse, TaskDecorator chụp context theo thời điểm submit, async dispatch, các executor khác và context nested.

## 17. Checklist triển khai production

1. **Lập sơ đồ async boundaries**: Servlet request, MVC Callable/WebAsyncTask, DeferredResult producer, `@Async`, CompletableFuture, message broker, streaming callbacks.
2. Với mỗi boundary, xác định **ai sở hữu executor**, thread, timeout, exception handling và cleanup.
3. Dùng bounded thread pool/queue, giới hạn concurrent tasks; tránh executor/queue không giới hạn cho công việc blocking.
4. Đặt admission control và overload behavior; trả 503/429 đúng contract thay vì silently dropping.
5. Chụp **immutable request/tenant/correlation context** trước submit; không giữ toàn servlet request hoặc JPA session.
6. Truyền MDC/log/tracing bằng TaskDecorator hoặc Micrometer Context Propagation; cleanup/restore trong finally.
7. Xử lý SecurityContext qua Spring Security integration/wrappers phù hợp, không dùng `InheritableThreadLocal` làm giải pháp chung.
8. Mở transaction trong worker service khi cần; giữ controller/service singleton stateless hoặc synchronize đúng những shared mutable state.
9. Đặt MVC timeout, downstream I/O timeout, deadline/cancellation; đo client disconnect và async errors.
10. Không trộn các stream/downloads lâu với quick-response Callable pool nếu việc chia tài nguyên khiến chúng chèn ép nhau.
11. Test `MockMvc asyncDispatch`, context propagation và response; test HTTP thật dưới tải cho queue/rejection/timeouts.
12. Theo dõi active threads, queue depth, latency p95/p99, DB pool, rejections, memory, trace correlation và leak; điều chỉnh capacity dựa trên đo đạc thay vì đặt max threads rất cao.

## Tài liệu chính thức

- [Spring Framework 6.2 — Asynchronous Requests (MVC)](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html)
- [Spring Framework 6.2 — AsyncSupportConfigurer](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/config/annotation/AsyncSupportConfigurer.html)
- [Spring Framework 6.2 — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework 6.2 — TaskDecorator](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/task/TaskDecorator.html)
- [Spring Framework 6.2 — ContextPropagatingTaskDecorator](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html)
- [Spring Security 6.5 — Concurrency Support](https://docs.spring.io/spring-security/reference/6.5/features/integrations/concurrency.html)
- [Spring Security 6.5 — Servlet MVC Async Security](https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/concurrency.html)
- [Micrometer — Context Propagation](https://docs.micrometer.io/context-propagation/reference/)
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)


</details>

- [Quay lại đầu trang](#back-to-top)
