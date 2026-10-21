<a id="back-to-top"></a>

# WebClient

## Menu
- [WebClient.Builder, ClientHttpConnector và thư viện HTTP nền](#web-client-001)
- [Gửi yêu cầu reactive và thiết lập URI, header, cookie, body](#web-client-002)
- [retrieve, onStatus, exchangeToMono và exchangeToFlux](#web-client-003)
- [Đọc JSON, kiểu generic, dữ liệu theo luồng và multipart](#web-client-004)
- [ExchangeFilterFunction và cấu hình dùng chung](#web-client-005)
- [Timeout, connection pool, TLS và giới hạn bộ nhớ codec](#web-client-006)
- [Xử lý lỗi, retry có giới hạn và tránh lặp thao tác có tác dụng phụ](#web-client-007)
- [Tiêu thụ hoặc giải phóng response body để tái sử dụng kết nối](#web-client-008)
- [Dùng WebClient trong MVC; chọn vị trí block và tránh chặn event loop](#web-client-009)
- [Reactor Context, truyền mã đối chiếu và hủy yêu cầu đang chạy](#web-client-010)

## <a id="web-client-001">WebClient.Builder, ClientHttpConnector và thư viện HTTP nền</a>

<details>
<summary>Xem chi tiết</summary>

# WebClient.Builder, ClientHttpConnector và thư viện HTTP nền

## 1. Mục tiêu và bức tranh tổng thể

`WebClient` là HTTP client trong Spring WebFlux: ứng dụng dùng nó để gọi API bên ngoài theo mô hình reactive, với kết quả thường là `Mono<T>` (0–1 phần tử) hoặc `Flux<T>` (nhiều phần tử). Ba lớp trách nhiệm quan trọng cần tách biệt:

- **`WebClient.Builder`** cấu hình API client ở mức Spring: base URL, header/cookie mặc định, filter, codec, connector và quan sát (observability); `build()` tạo một `WebClient` để tái sử dụng.
- **`ClientHttpConnector`** là hợp đồng kết nối của Spring (`org.springframework.http.client.reactive.ClientHttpConnector`). Nó nối WebClient với thư viện HTTP thực tế và biến thao tác gửi/nhận thành các `ClientHttpRequest`/`ClientHttpResponse` reactive.
- **Thư viện HTTP nền (transport/engine)** thực hiện I/O thật: TCP/TLS, proxy, connection pool, event loop hoặc executor, timeout, HTTP/1.1–HTTP/2 tùy khả năng và cấu hình của engine.

Luồng đơn giản:

```text
Code ứng dụng
  → WebClient (URI, header, filter, encoder/decoder)
    → ExchangeFunction
      → ClientHttpConnector
        → Reactor Netty / JDK HttpClient / Jetty / Apache HttpComponents
          → mạng HTTP(S) và máy chủ đích
```

**Điểm mấu chốt:** `WebClient.Builder` quyết định *cách client được cấu hình*; `ClientHttpConnector` quyết định *transport nào được dùng*; engine quyết định *cách kết nối và I/O hoạt động*. Chọn connector không thay thế codec JSON, còn tăng giới hạn codec không thay đổi connect timeout.

## 2. Khi nào dùng và cần dependency gì?

WebClient phù hợp với các cuộc gọi HTTP bất đồng bộ, chuỗi xử lý reactive, gọi nhiều dịch vụ, hoặc tải dữ liệu dạng stream. Có thể dùng nó trong ứng dụng Spring MVC nhưng tuyệt đối không gọi `block()` trên luồng event loop của WebFlux/Reactor Netty. Với ứng dụng chỉ cần API đồng bộ, `RestClient` có thể đơn giản hơn.

Tối thiểu cần Spring WebFlux (`org.springframework:spring-webflux`), Project Reactor và một transport HTTP tương thích. Trong ứng dụng Spring Boot, `spring-boot-starter-webflux` là cách thông dụng để có WebClient cùng Reactor Netty. Nếu dự án đã có bộ quản lý phiên bản (Spring Boot BOM), hãy để BOM chọn các phiên bản tương thích. Không trộn ngẫu nhiên Spring, Reactor, Netty, Jetty hoặc HttpComponents khác thế hệ.

| Engine | Connector Spring | Trường hợp phù hợp | Lưu ý |
| --- | --- | --- | --- |
| Reactor Netty | `ReactorClientHttpConnector` | Lựa chọn phổ biến cho ứng dụng WebFlux; event loop và pooling mạnh | Cần `reactor-netty-http`; cấu hình bằng `reactor.netty.http.client.HttpClient` |
| Java JDK HttpClient | `JdkClientHttpConnector` | Muốn dùng `java.net.http.HttpClient` có sẵn trong JDK hiện đại | Không đồng nghĩa với bỏ Reactor ở tầng API WebClient |
| Jetty | `JettyClientHttpConnector` | Hệ thống đã dùng Jetty hoặc cần tùy chỉnh Jetty client | Cần thư viện Jetty Reactive HTTP client thích hợp, quản lý lifecycle |
| Apache HttpComponents | `HttpComponentsClientHttpConnector` | Đã chuẩn hóa Apache HttpClient 5 async | Dùng **async** client và phụ thuộc Apache tương ứng |

Spring Boot có thể tự nhận biết connector dựa vào classpath; ở các phiên bản Boot hỗ trợ các lựa chọn trên, ưu tiên phổ biến là Reactor Netty → Jetty → Apache → JDK. **Đó là hành vi auto-configuration của Boot**, không nên coi là hợp đồng cố định của mọi phiên bản Spring Framework hay của `WebClient.builder()` thuần. Khi yêu cầu bắt buộc một transport, hãy cấu hình tường minh.

## 3. WebClient.Builder: cấu hình ứng dụng

Ví dụ trong một Spring application đã có WebFlux:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class CatalogClientConfig {

    @Bean
    WebClient catalogClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://catalog.example.org")
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Client", "spring-learning")
                .codecs(config -> config.defaultCodecs()
                        .maxInMemorySize(1024 * 1024))
                .build();
    }
}
```

Đoạn trên là cấu hình trong ứng dụng Spring có bean `WebClient.Builder`; host `catalog.example.org` chỉ là địa chỉ minh họa, không phải endpoint chạy thử. Các thành phần quan trọng:

- `baseUrl(...)` làm gốc cho URI tương đối, ví dụ `.uri("/books/{id}", 7)`. URI tuyệt đối có thể ghi đè base URL. Quản lý slash/path có chủ đích, đặc biệt khi base URL chứa path.
- `defaultHeader(...)` và `defaultCookie(...)` cung cấp giá trị mặc định; header của request cụ thể có thể được cấu hình riêng.
- `defaultUriVariables(...)` và `uriBuilderFactory(...)` giúp định nghĩa quy tắc mở rộng URI, encoding và biến URI; không nối thẳng đầu vào chưa kiểm soát vào URL.
- `defaultRequest(...)` chỉnh mỗi request; `filter(...)` thêm `ExchangeFilterFunction` xử lý xuyên suốt, ví dụ thêm correlation ID, authentication, đo thời gian và lỗi.
- `codecs(...)` / `exchangeStrategies(...)` điều chỉnh bộ mã hóa/giải mã body; `maxInMemorySize` liên quan kích thước *buffer nội dung*, không phải dung lượng pool kết nối.
- `clientConnector(...)` gắn trực tiếp transport được cấu hình; `observationRegistry(...)` phục vụ metrics/tracing nếu cần.

**Tính thay đổi:** Builder là đối tượng **mutable/stateful**; gọi thêm `defaultHeader`/`filter` lên cùng builder ảnh hưởng các client được `build()` sau đó. WebClient đã build được thiết kế bất biến để chia sẻ giữa các thread; dùng `webClient.mutate().filter(...).build()` tạo một client **mới**, không sửa client cũ. Nếu cần tạo nhiều cấu hình từ một builder dùng lại, cân nhắc `builder.clone()`. Đừng tạo WebClient mới cho từng request.

**Trong Spring Boot:** nên inject `WebClient.Builder` được Boot auto-configure để nhận codec, tài nguyên HTTP, customizer và observability đã được cấu hình. Boot thường cung cấp builder scope prototype, nhưng bản thân từng builder vẫn mutable. Tạo bằng `WebClient.create()` hay `WebClient.builder()` trực tiếp sẽ không tự nhận toàn bộ tùy chỉnh Boot.

Ví dụ thay đổi client mà giữ nguyên bản gốc:

```java
WebClient publicApi = WebClient.builder()
        .baseUrl("https://example.org")
        .build();

WebClient privateApi = publicApi.mutate()
        .defaultHeader("X-Api-Version", "2")
        .build();
// publicApi giữ cấu hình ban đầu; privateApi có thêm header mặc định.
```

## 4. ClientHttpConnector hoạt động như thế nào?

Interface `ClientHttpConnector` định nghĩa thao tác cấp thấp mang dạng:

```java
Mono<ClientHttpResponse> connect(
        HttpMethod method,
        URI uri,
        Function<? super ClientHttpRequest, Mono<Void>> requestCallback);
```

Chữ ký trên là **trích đoạn API để hiểu hợp đồng**, không phải chương trình chạy độc lập. WebClient gọi connector qua tầng exchange khi một request được subscribe. Connector chuẩn bị native HTTP request, để callback ghi header/body, gửi qua engine rồi chuyển response trở lại dạng Spring reactive.

Connector **không** quyết định cách serialize DTO JSON, business error mapping, hay retry policy. Việc đó nằm ở WebClient codecs, filter và pipeline Reactor. Các lỗi có thể phát sinh ở lớp khác nhau:

- Lỗi tạo URI hoặc codec: thường do mã ứng dụng/dữ liệu.
- `WebClientResponseException`: HTTP server đã trả status lỗi 4xx/5xx khi dùng `retrieve()` mặc định.
- `WebClientRequestException`: lỗi I/O/transport, DNS, kết nối, TLS… trước khi có HTTP response hợp lệ.
- `DataBufferLimitException`: body cần buffer vượt giới hạn decoder; tăng `maxInMemorySize` chỉ khi có lý do, cân nhắc streaming cho dữ liệu lớn.

## 5. Cấu hình Reactor Netty: pooling, connect timeout và response timeout

`ReactorClientHttpConnector` là lựa chọn quen thuộc. Với Reactor Netty, có thể cấu hình `HttpClient` trước rồi truyền vào Builder:

```java
import java.time.Duration;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
class PaymentsHttpConfig {

    @Bean
    WebClient paymentsClient(WebClient.Builder builder) {
        ConnectionProvider pool = ConnectionProvider.builder("payments-pool")
                .maxConnections(100)
                .pendingAcquireTimeout(Duration.ofSeconds(2))
                .build();

        HttpClient httpClient = HttpClient.create(pool)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)
                .responseTimeout(Duration.ofSeconds(5));

        return builder
                .baseUrl("https://payments.example.org")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
```

Đây là **ví dụ cấu hình Spring bean**, không gửi request tới host ví dụ. Trong hệ thống thật, phải **quản lý vòng đời `ConnectionProvider` tùy chỉnh** (dispose lúc application shutdown), tránh tạo pool riêng vô hạn theo số request/bean. Với nhiều ứng dụng Spring Boot, cho client dùng tài nguyên Reactor Netty được chia sẻ và quản lý sẵn còn phù hợp hơn; chỉ tách pool khi cần giới hạn độc lập giữa các upstream. Nếu dùng `ReactorResourceFactory` với tài nguyên riêng, kết nối lifecycle vào `ApplicationContext`.

Phân biệt timeout:

| Thiết lập | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `CONNECT_TIMEOUT_MILLIS` (Netty) | Giới hạn thời gian thiết lập kết nối TCP | 3 giây |
| `pendingAcquireTimeout` (pool) | Chờ cấp kết nối khi pool bận | 2 giây |
| `responseTimeout` (Reactor Netty) | Timeout xử lý/đọc response ở tầng transport theo định nghĩa engine | 5 giây |
| `Mono.timeout(Duration)` | Giới hạn thời gian của **publisher** theo đồng hồ Reactor; không thay thế cấu hình socket/pool | `.timeout(Duration.ofSeconds(8))` |

Các timeout không bảo đảm tổng thời gian từ lúc gọi tới lúc có kết quả là phép cộng cố định. DNS, TLS, streaming và retry có đặc tính riêng. Hãy cấu hình ngân sách timeout rõ ràng theo SLA, đo đạc và xử lý thất bại. Với stream kéo dài (SSE), timeout cố định quá ngắn có thể cắt kết nối hợp lệ.

**Không nhầm `reactor.netty.http.client.HttpClient` với `java.net.http.HttpClient`**: chúng là hai kiểu khác nhau, dành cho hai connector khác nhau.

## 6. Đổi engine sang JDK HttpClient

Khi cần JDK HTTP engine, không phải viết lại code gọi API; thay connector:

```java
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

HttpClient jdk = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

WebClient client = WebClient.builder()
        .baseUrl("https://example.org")
        .clientConnector(new JdkClientHttpConnector(jdk))
        .build();
```

Đây là đoạn cấu hình Java 17+ trong project có Spring WebFlux; client chỉ thực hiện I/O khi gọi và subscribe request. JDK client có quy tắc cấu hình timeout, proxy, SSL, redirect riêng; không thể bê `ChannelOption` hay `ConnectionProvider` của Netty sang JDK. Chọn connector theo nhu cầu vận hành, môi trường, tính tương thích và benchmark thực tế thay vì suy đoán connector này luôn nhanh hơn connector kia.

## 7. Ví dụ chạy trọn vẹn: HTTP server cục bộ → WebClient → Reactor Netty

Ví dụ độc lập sau chứng minh Builder gắn connector và thực sự nhận dữ liệu HTTP, **không phụ thuộc Internet**. Dùng Gradle với Java 17+, thư mục dự án:

```text
build.gradle
src/main/java/demo/WebClientDemo.java
```

`build.gradle` (Groovy DSL; dùng BOM để đồng bộ phiên bản):

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation platform('org.springframework.boot:spring-boot-dependencies:3.3.13')
    implementation 'org.springframework:spring-webflux'
    implementation 'io.projectreactor.netty:reactor-netty-http'
}

application {
    mainClass = 'demo.WebClientDemo'
}
```

`src/main/java/demo/WebClientDemo.java`:

```java
package demo;

import com.sun.net.httpserver.HttpServer;
import io.netty.channel.ChannelOption;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

public final class WebClientDemo {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hello", exchange -> {
            byte[] data = "Hello WebClient".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(data);
            }
        });
        server.start();

        try {
            HttpClient transport = HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)
                    .responseTimeout(Duration.ofSeconds(3));

            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:"
                            + server.getAddress().getPort())
                    .clientConnector(
                            new ReactorClientHttpConnector(transport))
                    .defaultHeader("X-Lesson", "webclient")
                    .build();

            String result = webClient.get()
                    .uri("/hello")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(5)); // chỉ tại main demo

            System.out.println(result);
        } finally {
            server.stop(0);
        }
    }
}
```

Chạy bằng Gradle trong thư mục ví dụ:

```bash
./gradlew run
```

Trên Windows PowerShell dùng `.\gradlew.bat run`; cần Gradle wrapper đã được tạo sẵn, hoặc dùng lệnh `gradle run` nếu đã cài Gradle. Phần stdout do chương trình tự in là **chính xác**:

```text
Hello WebClient
```

Gradle và logging thư viện có thể in thêm dòng riêng. JDK HTTP server mở cổng ngẫu nhiên trên loopback, tránh phụ thuộc API công cộng và đóng khi hoàn tất. Kết quả được nhận khi `block(...)` subscribe publisher; **không dùng `block()` trong request handler WebFlux đang chạy trên event loop**.

Nếu dựng dự án Java theo module path thay vì classpath truyền thống, có thể cần khai báo thêm module `jdk.httpserver`; ví dụ Gradle trên dùng classpath thông thường.

## 8. Cách sử dụng client đã cấu hình

Giả sử có `WebClient catalogClient` trỏ tới API trả JSON. DTO Java record:

```java
record Book(long id, String title) {}
```

Ví dụ truy vấn và xử lý status:

```java
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

// Đặt trong một phương thức/service đã nhận catalogClient từ Spring.
Mono<Book> request = catalogClient.get()
        .uri("/books/{id}", 42)
        .retrieve()
        .onStatus(status -> status.value() == 404,
                response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new IllegalArgumentException(
                                "Không tìm thấy book 42: " + body)))
        .bodyToMono(Book.class);

return request;
```

Đây là **fragment bên trong phương thức trả `Mono<Book>`**, không phải file Java độc lập. Khi cần return trong Java service, đặt record ở package/scope thích hợp. `retrieve()` mặc định chuyển HTTP 4xx/5xx thành `WebClientResponseException`; `onStatus(...)` chỉ tùy biến trường hợp xác định. Khi cần xem cả status/header/body, xem `exchangeToMono` / `exchangeToFlux` và tiêu thụ hoặc giải phóng response body đúng cách.

Gọi `get().uri(...).retrieve().bodyToMono(...)` chỉ **tạo publisher**; cần subscriber do WebFlux quản lý, `subscribe()` ở ranh giới ứng dụng phù hợp, hoặc `block()` trong chương trình đồng bộ/demo mới thực thi I/O. Trong controller WebFlux, trả thẳng `Mono` để runtime subscribe; không tự `subscribe()` cho mỗi request rồi bỏ qua tín hiệu lỗi.

### Filters: logic chung cho nhiều request

```java
WebClient client = WebClient.builder()
        .filter((request, next) -> {
            // Không log Authorization, Cookie hoặc body nhạy cảm.
            System.out.println("HTTP " + request.method()
                    + " " + request.url().getPath());
            return next.exchange(request);
        })
        .build();
```

Filter chạy theo chuỗi; thứ tự đăng ký có thể ảnh hưởng authentication, retry, logging. Nếu filter tự đọc response, phải xử lý/release body đúng cách; nếu chỉ truyền `ClientResponse` đi tiếp thì để WebClient tiêu thụ. Không cài filter log URL đầy đủ khi query string có secret hoặc dữ liệu cá nhân.

## 9. Vận hành và lỗi hay gặp

1. **Chỉ thêm `spring-webflux` nhưng thiếu HTTP engine tương thích:** khi chạy có thể không tìm được connector mong muốn hoặc không có tính năng của engine đã giả định. Kiểm tra dependency tree và connector thực tế, đặc biệt sau khi exclude Netty.
2. **Tưởng `build()` gửi request:** `build()` chỉ tạo client; pipeline Reactive được thực thi khi subscribe.
3. **Thay đổi lại một builder dùng chung:** cấu hình “rò” sang client khác được build sau; clone builder hoặc tạo bean client ổn định.
4. **Tạo `HttpClient`/pool theo từng request:** gây churn kết nối/thread/tài nguyên. Tái sử dụng WebClient và quản lý vòng đời pool/event loop.
5. **Chặn event loop bằng `block()` hoặc I/O blocking trong `map`:** mất throughput, có thể gặp lỗi `IllegalStateException`; giữ pipeline non-blocking hoặc chuyển công việc blocking sang scheduler phù hợp khi thực sự cần.
6. **Nhầm HTTP 500 với lỗi connect:** 500 là response từ server, còn connect/DNS/TLS là transport error; xử lý chính sách retry khác nhau và thận trọng với POST không idempotent.
7. **Chỉ đặt `Mono.timeout`:** điều đó không thay cấu hình connect/pool/response timeout của engine, và không tự thiết lập chính sách retry.
8. **Tăng `maxInMemorySize` vô hạn khi gặp `DataBufferLimitException`:** có nguy cơ tăng memory; với response lớn, ưu tiên streaming `Flux<DataBuffer>`/`Flux<T>` và release buffer khi tự xử lý.
9. **Tắt xác minh TLS để “sửa” chứng chỉ:** không an toàn. Cấu hình trust store/chứng chỉ đúng và tuân thủ chính sách proxy.
10. **Dùng input người dùng làm URL tùy ý:** mở nguy cơ SSRF; giới hạn host/scheme/IP theo chính sách trước khi gửi. Không ghi token hoặc dữ liệu nhạy cảm vào log.
11. **Tự cấu hình connector và quên auto-configuration:** những thay đổi về resource/SSL/metrics hoặc customizer có thể không còn đi qua cấu hình Boot như kỳ vọng. Kiểm tra bằng integration test và cấu hình thực tế.

### Checklist trước khi đưa vào production

- Xác định engine/connector thực tế trên classpath và cách Boot khởi tạo nó.
- Dùng `WebClient.Builder` do Spring Boot cấp nếu muốn thừa hưởng cấu hình chung; giữ client dưới dạng bean tái sử dụng.
- Định nghĩa connect, pending acquire, response và end-to-end timeout tương ứng với SLA.
- Định cỡ pool theo số upstream và tải; quản lý shutdown của tài nguyên tự tạo.
- Giới hạn codec/buffering, kiểm tra behavior với response lớn và streaming.
- Xử lý 4xx/5xx, transport error, retry có điều kiện; tránh retry request gây hiệu ứng phụ khi thiếu idempotency.
- Kiểm tra HTTPS, proxy, certificate, header bí mật, log, metrics/tracing, và SSRF.

## 10. Tự kiểm tra kiến thức

**Câu 1.** Tăng `maxInMemorySize` có giải quyết lỗi connect timeout không?  
**Đáp án:** Không. Đây là giới hạn bộ đệm body ở tầng codec; connect timeout thuộc transport.

**Câu 2.** Tại sao hai WebClient dùng cùng `WebClient.Builder` có thể có cấu hình khác nhau?  
**Đáp án:** Builder mutable; mỗi lần `build()` chụp cấu hình hiện thời. Dùng `clone()` hoặc tạo builder riêng nếu muốn nhánh cấu hình độc lập.

**Câu 3.** Muốn chuyển từ Reactor Netty sang JDK HttpClient, phải sửa tất cả code `get()/retrieve()/bodyToMono()` không?  
**Đáp án:** Không. Thay `ClientHttpConnector` (và cấu hình engine). Bề mặt WebClient phía ứng dụng hầu như giữ nguyên.

**Câu 4.** Vì sao không nên `block()` trong WebFlux controller?  
**Đáp án:** Chặn luồng event loop đang xử lý nhiều request, làm mất lợi thế non-blocking và có thể bị Reactor từ chối. Trả `Mono`/`Flux` cho runtime.

## Tài liệu chính thức

- [Spring Framework — WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)
- [Spring Framework — WebClient Configuration](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-builder.html)
- [Spring Framework — WebClient Filters](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-filter.html)
- [Spring Framework — retrieve() và xử lý lỗi](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-retrieve.html)
- [Spring Boot — Calling REST Services / WebClient](https://docs.spring.io/spring-boot/reference/io/rest-client.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-client-002">Gửi yêu cầu reactive và thiết lập URI, header, cookie, body</a>

<details>
<summary>Xem chi tiết</summary>

# WebClient: Gửi yêu cầu reactive và thiết lập URI, header, cookie, body

## Mục tiêu

Bài học này hướng dẫn sử dụng Spring WebFlux `WebClient` để **gửi HTTP request theo phong cách reactive**, cấu hình **URI, header, cookie, body**, đọc response và xử lý lỗi đúng cách. Sau bài học, bạn có thể tự xây GET/POST có tham số động, gửi JSON/form/multipart, ghép pipeline `Mono`/`Flux` và tránh các lỗi hay gặp khi kết hợp HTTP với Reactive Streams. Ví dụ dùng Java 17+, Spring Framework 6.x và Spring Boot 3.x.

## 1. WebClient hoạt động như thế nào?

`WebClient` là HTTP client có fluent API, hỗ trợ non-blocking I/O và streaming. Nó thuộc `org.springframework.web.reactive.function.client` và dùng Reactor để mô tả kết quả bất đồng bộ. Một pipeline thông thường:

```text
WebClient
  -> GET / POST / PUT / PATCH / DELETE
  -> URI (path, path variable, query parameter)
  -> request headers + cookies + body nếu có
  -> retrieve() hoặc exchangeToMono(...)
  -> bodyToMono(...) / bodyToFlux(...) / toEntity(...)
  -> subscription -> HTTP I/O -> decode response
```

Ví dụ:

```java
WebClient client = WebClient.create("https://api.example.com");

Mono<String> pending = client.get()
        .uri("/health")
        .retrieve()
        .bodyToMono(String.class);
```

`Mono<T>` biểu diễn tối đa một phần tử; `Flux<T>` biểu diễn nhiều phần tử. **Tạo `Mono` chưa có nghĩa request đã được gửi**: publisher của WebClient là *cold*, HTTP I/O được kích hoạt khi có subscriber. Trong ứng dụng WebFlux, trả `Mono`/`Flux` để framework quản lý subscription. Trong console demo, có thể dùng `block` tại ranh giới đồng bộ có chủ đích.

Không gọi `subscribe()` tùy tiện trong service chỉ để “cho request chạy”: việc tách pipeline làm mất đường truyền lỗi, cancellation và ngữ cảnh xử lý. Mỗi lần subscribe một publisher lạnh có thể tạo request mới; điều đó đặc biệt quan trọng với POST có tác dụng phụ.

## 2. Khởi tạo client và cấu hình mặc định

Khi dự án Spring Boot đã quản lý phiên bản thư viện, dependency thông dụng:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
}
```

Có thể sử dụng WebClient trong ứng dụng Spring MVC để gọi dịch vụ HTTP; việc thêm client không bắt buộc chuyển controller sang WebFlux. Spring Boot cung cấp `WebClient.Builder` đã được cấu hình theo môi trường ứng dụng:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class ClientConfig {
    @Bean
    WebClient catalogClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://catalog.example.com/api")
                .defaultHeader("X-Application", "web-learning")
                .build();
    }
}
```

Có thể khởi tạo nhanh bằng `WebClient.create()`, `WebClient.create(baseUrl)` hoặc `WebClient.builder()`. Dùng client cấu hình chung và tái sử dụng thay vì tạo HTTP client/connection pool mới trong mỗi request.

`baseUrl` là tiền tố URI chung; `defaultHeader`, `defaultCookie` đặt giá trị mặc định; `filter` dùng cho xử lý chung như logging an toàn, correlation hoặc auth; `clientConnector` cấu hình tầng HTTP. `mutate()` tạo builder từ cấu hình có sẵn. Không đặt token hay cookie **riêng của từng người dùng** trong singleton client dùng chung.

## 3. Thiết lập URI an toàn

### 3.1. URI template với path variable

```java
Mono<String> response = client.get()
        .uri("/orders/{id}", 42)
        .retrieve()
        .bodyToMono(String.class);
```

Với base URL `https://api.example.com`, request hướng đến `https://api.example.com/orders/42`. `{id}` là vị trí thay biến; sử dụng URI template dễ đọc và an toàn về encoding hơn nối chuỗi thủ công.

### 3.2. URI builder với query parameter

```java
Mono<String> response = client.get()
        .uri(uriBuilder -> uriBuilder
                .path("/books")
                .queryParam("keyword", "spring reactive")
                .queryParam("page", 1)
                .queryParam("size", 20)
                .build())
        .retrieve()
        .bodyToMono(String.class);
```

Request mang các tham số `keyword`, `page`, `size` trong query string. Dấu cách trong `keyword` cần được encode; tùy cấu hình URI builder, ký tự khoảng trắng có thể được biểu diễn bằng `%20`. Không tự nối `?`/`&` hoặc encode lại giá trị đã mã hóa.

Kết hợp path variable và query:

```java
Mono<String> response = client.get()
        .uri(builder -> builder
                .path("/customers/{customerId}/orders")
                .queryParam("status", "OPEN")
                .queryParam("page", 0)
                .build(7))
        .retrieve()
        .bodyToMono(String.class);
```

Đường dẫn đại diện resource, ví dụ `/customers/7/orders`; query cho bộ lọc và phân trang, ví dụ `?status=OPEN&page=0`. `pathSegment(...)` hữu ích khi thêm từng segment độc lập; `queryParamIfPresent(...)` hữu ích khi chỉ muốn gửi tham số nếu `Optional` có giá trị.

### 3.3. Địa chỉ tương đối và tuyệt đối

`baseUrl(...)` áp dụng cho URI tương đối. Một absolute URI được truyền trực tiếp có thể thay thế base URL. Nếu URI/host đến từ input bên ngoài, cần kiểm soát scheme và danh sách host được phép để tránh SSRF; mã hóa URI không phải là kiểm tra quyền gọi đích.

## 4. HTTP method và header

Các HTTP method thường gặp:

| Method | Mục đích phổ biến | WebClient |
| --- | --- | --- |
| GET | Đọc một resource hoặc danh sách | `client.get()` |
| POST | Tạo resource, gửi lệnh hoặc dữ liệu | `client.post()` |
| PUT | Thay thế resource | `client.put()` |
| PATCH | Cập nhật một phần | `client.patch()` |
| DELETE | Xóa resource | `client.delete()` |

Hợp đồng API quyết định ngữ nghĩa chính xác. GET thường truyền điều kiện đọc bằng URI/query chứ không có body.

### 4.1. Gắn các header

```java
Mono<String> product = client.get()
        .uri("/products/{id}", 42)
        .accept(MediaType.APPLICATION_JSON)
        .header("X-Request-Id", "req-42")
        .headers(h -> h.setBearerAuth(accessToken))
        .retrieve()
        .bodyToMono(String.class);
```

Các API quan trọng:

- `header("X-Request-Id", "req-42")` thêm giá trị header cho request.
- `headers(h -> h.set("X-Request-Id", "req-42"))` thao tác trực tiếp trên `HttpHeaders`; `set` thay thế giá trị trước đó của header cùng tên.
- `accept(MediaType.APPLICATION_JSON)` khai báo định dạng phản hồi mong muốn: `Accept: application/json`.
- `contentType(MediaType.APPLICATION_JSON)` khai báo định dạng payload **gửi lên**: `Content-Type: application/json`.
- `headers(h -> h.setBearerAuth(token))` tạo `Authorization: Bearer ...`.
- `headers(h -> h.setBasicAuth(username, password))` tạo Basic auth; chỉ dùng với HTTPS.

**Dễ nhầm:** `Accept` mô tả loại dữ liệu muốn *nhận*; `Content-Type` mô tả loại dữ liệu đang *gửi*. Đặt `Content-Type` không tự tạo body. GET không có body thông thường chỉ cần `Accept` nếu quan tâm định dạng phản hồi.

HTTP header names không phân biệt chữ hoa/chữ thường. `header(...)` có thể thêm nhiều giá trị; khi muốn một giá trị duy nhất, dùng `headers(h -> h.set(...))`. Không log Authorization, cookie hay secret, và không hardcode mật khẩu/token vào source.

## 5. Cookie trong request

Cookie được gửi qua header `Cookie`, thường mang định danh phiên hoặc giá trị theo hợp đồng API:

```java
Mono<String> profile = client.get()
        .uri("/me")
        .cookie("session", sessionToken)
        .cookies(c -> c.add("locale", "vi-VN"))
        .retrieve()
        .bodyToMono(String.class);
```

Dạng HTTP tương đương thường là `Cookie: session=...; locale=vi-VN`. Nếu có cookie chung cho mọi request của một client:

```java
WebClient client = WebClient.builder()
        .baseUrl("https://api.example.com")
        .defaultCookie("locale", "vi-VN")
        .build();
```

Phân biệt rõ: `Cookie` là request header; `Set-Cookie` là response header. WebClient không tự duy trì cookie jar giống trình duyệt. Nếu cần lưu `Set-Cookie` và gửi lại ở request sau, ứng dụng phải quản lý rõ ràng, tránh rò rỉ giữa người dùng.

Không đưa session token của từng user vào `defaultCookie` trên client singleton; gắn cookie theo request và chỉ truyền dữ liệu nhạy cảm qua HTTPS tới đích tin cậy.

## 6. Gửi request body

HTTP body chứa payload, phổ biến với POST, PUT và PATCH. WebClient dùng Spring HTTP message writers/codecs để mã hóa đối tượng Java sang JSON, text, form hoặc multipart.

### 6.1. JSON DTO có sẵn: bodyValue

```java
record CreateBookRequest(String title, int quantity) {}

Mono<String> created = client.post()
        .uri("/books")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue(new CreateBookRequest("Java", 2))
        .retrieve()
        .bodyToMono(String.class);
```

Khi có Jackson JSON codec, record sẽ được mã hóa thành object JSON gồm `title` và `quantity`. `bodyValue` dùng khi bạn có một giá trị bình thường như DTO, record hoặc chuỗi.

### 6.2. Đầu vào là Mono/Flux: body(Publisher, Class)

```java
Mono<CreateBookRequest> requestBody =
        Mono.just(new CreateBookRequest("Java", 2));

Mono<String> created = client.post()
        .uri("/books")
        .contentType(MediaType.APPLICATION_JSON)
        .body(requestBody, CreateBookRequest.class)
        .retrieve()
        .bodyToMono(String.class);
```

`body(publisher, elementClass)` dùng khi dữ liệu đầu vào đã là publisher reactive. **Đừng truyền `Mono` vào `bodyValue`**; publisher không phải DTO cần serialize. Với `Flux<T>`, server phải hỗ trợ media type/định dạng streaming phù hợp; một endpoint nhận JSON object đơn lẻ không tự nhiên trở thành endpoint stream.

### 6.3. Form URL encoded

```java
Mono<String> response = client.post()
        .uri("/login")
        .body(BodyInserters.fromFormData("username", "demo")
                .with("locale", "vi"))
        .retrieve()
        .bodyToMono(String.class);
```

Import `org.springframework.web.reactive.function.BodyInserters`. Data được writer biểu diễn theo `application/x-www-form-urlencoded`. Form body và query parameter đều có key/value nhưng nằm tại vị trí khác trong HTTP request.

### 6.4. Multipart với tệp

```java
MultipartBodyBuilder parts = new MultipartBodyBuilder();
parts.part("description", "Tập tin minh họa");
parts.part("file", new FileSystemResource(Path.of("report.txt")));

Mono<String> response = client.post()
        .uri("/upload")
        .bodyValue(parts.build())
        .retrieve()
        .bodyToMono(String.class);
```

Import `org.springframework.http.client.MultipartBodyBuilder`, `org.springframework.core.io.FileSystemResource`, `java.nio.file.Path`. Đoạn minh họa giả định file `report.txt` tồn tại và API nhận `description`/`file`. Để writer thiết lập multipart boundary; không tự thêm `Content-Type: multipart/form-data` thủ công mà quên boundary.

### 6.5. Phân biệt bốn nhóm thông tin

| Phần request | Ví dụ | Vai trò |
| --- | --- | --- |
| URI/path/query | `/books/42?lang=vi` | Resource đích và tham số URL |
| Header | `Accept: application/json` | Metadata và thương lượng định dạng |
| Cookie | `Cookie: session=abc` | Thông tin trạng thái/phiên |
| Body | `{"title":"Java","quantity":2}` | Dữ liệu gửi tới server |

Không đặt password hoặc access token vào query khi có thể dùng header phù hợp: URL dễ bị ghi trong log, cache hoặc lịch sử truy cập.

## 7. Demo Java 17+ có thể chạy: gửi URI, header, cookie, JSON body

Ví dụ sau tự chạy một HTTP server cục bộ bằng JDK `HttpServer`, rồi dùng WebClient POST tới server đó. Server kiểm tra bốn yếu tố của request; nếu đúng, trả HTTP 201 và JSON, nếu sai trả 400. Mục đích là kiểm chứng pipeline đầy đủ mà không cần API bên ngoài.

**Cấu trúc dự án:**

```text
webclient-demo/
  build.gradle
  src/main/java/demo/WebClientRequestDemo.java
```

**build.gradle** (Gradle và JDK 17+, BOM Spring Boot 3.3.x cố định phiên bản tương thích):

```groovy
plugins {
    id 'application'
}
repositories {
    mavenCentral()
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
dependencies {
    implementation platform('org.springframework.boot:spring-boot-dependencies:3.3.6')
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
}
application {
    mainClass = 'demo.WebClientRequestDemo'
}
```

**src/main/java/demo/WebClientRequestDemo.java:**

```java
package demo;

import com.sun.net.httpserver.HttpServer;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class WebClientRequestDemo {
    public record CreateOrderRequest(String product, int quantity) {}
    public record OrderResult(long id, boolean accepted) {}

    public static void main(String[] args) throws Exception {
        // Cổng 0 để hệ điều hành chọn cổng local còn trống.
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/api/orders/", exchange -> {
            try {
                String uri = exchange.getRequestURI().toString();
                String requestId = exchange.getRequestHeaders()
                        .getFirst("X-Request-Id");
                String cookie = exchange.getRequestHeaders()
                        .getFirst("Cookie");
                String contentType = exchange.getRequestHeaders()
                        .getFirst("Content-Type");
                String json = new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8);

                boolean valid = "POST".equals(exchange.getRequestMethod())
                        && "/api/orders/42?source=guide".equals(uri)
                        && "req-42".equals(requestId)
                        && "session=demo-token".equals(cookie)
                        && "application/json".equals(contentType)
                        && json.contains("\"product\":\"book\"")
                        && json.contains("\"quantity\":2");

                int status = valid ? 201 : 400;
                String data = valid
                        ? "{\"id\":42,\"accepted\":true}"
                        : "{\"error\":\"invalid request\"}";
                byte[] responseBytes = data.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders()
                        .set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, responseBytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(responseBytes);
                }
            } finally {
                exchange.close();
            }
        });

        server.start();
        try {
            int port = server.getAddress().getPort();
            WebClient client = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + port)
                    .build();

            Mono<OrderResult> pending = client.post()
                    .uri(uri -> uri.path("/api/orders/{id}")
                            .queryParam("source", "guide")
                            .build(42))
                    .header("X-Request-Id", "req-42")
                    .cookie("session", "demo-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(new CreateOrderRequest("book", 2))
                    .retrieve()
                    .bodyToMono(OrderResult.class);

            // block chỉ ở biên console demo; không block trong event loop.
            OrderResult result = pending.block(Duration.ofSeconds(5));
            System.out.println("id=" + result.id()
                    + ", accepted=" + result.accepted());
        } finally {
            server.stop(0);
        }
    }
}
```

Chạy trong thư mục demo bằng `gradle run` nếu có Gradle; hoặc `./gradlew run` khi dự án đã tạo Gradle Wrapper.

**Dòng do chương trình in ra stdout:**

```text
id=42, accepted=true
```

Giải thích lần lượt:

1. `uri(...)` dựng `/api/orders/42?source=guide` từ template/path/query.
2. `header(...)` tạo `X-Request-Id: req-42` để định danh request.
3. `cookie(...)` gửi `session=demo-token`.
4. `contentType(...)` và `bodyValue(...)` gửi record dưới dạng JSON.
5. `retrieve().bodyToMono(OrderResult.class)` giải mã response JSON; mã 400 sẽ phát lỗi HTTP.
6. `block` tại `main` cho console chờ kết quả rồi in; WebFlux service thực tế nên trả lại `Mono<OrderResult>`.

Ứng dụng mẫu không phải server sản xuất. Nó chỉ giúp quan sát rằng thiếu/sai bất cứ thành phần nào sẽ làm HTTP request không đạt hợp đồng đã đặt.

## 8. Nhận response, status và lỗi

Các phương thức thường dùng:

```java
Mono<String> text = client.get()
        .uri("/status")
        .retrieve()
        .bodyToMono(String.class);

Mono<ResponseEntity<String>> full = client.get()
        .uri("/status")
        .retrieve()
        .toEntity(String.class);

Flux<EventDto> events = client.get()
        .uri("/events")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .retrieve()
        .bodyToFlux(EventDto.class);
```

Ở đoạn này `EventDto` chỉ là kiểu DTO minh họa do ứng dụng định nghĩa; server phải thực sự trả luồng tương ứng mới dùng `bodyToFlux` theo kiểu đó. `toEntity` đọc cả status và headers; `bodyToMono` đọc tối đa một body object; với response không có body (ví dụ HTTP 204), dùng `toBodilessEntity()` hoặc `bodyToMono(Void.class)` khi phù hợp.

### 8.1. Lỗi HTTP mặc định và onStatus

`retrieve()` mặc định chuyển status 4xx và 5xx thành lỗi `WebClientResponseException` và các subtype. Xử lý 404 có ngữ nghĩa riêng bằng `onStatus`:

```java
Mono<Book> book = client.get()
        .uri("/books/{id}", 42)
        .retrieve()
        .onStatus(
                status -> status.value() == 404,
                response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new IllegalStateException(
                                "Book not found: " + body)))
        .bodyToMono(Book.class)
        .timeout(Duration.ofSeconds(3));
```

`Book` là DTO giả định đã khai báo. `onStatus` xử lý một status xác định; lỗi khác vẫn dùng xử lý mặc định. `timeout` giới hạn thời gian ở tầng reactive; khi cần, cấu hình thêm connect/read timeout tại HTTP connector.

Nếu cần tự quyết định theo status và xử lý response ở cấp thấp:

```java
Mono<String> result = client.get()
        .uri("/status")
        .exchangeToMono(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(String.class);
            }
            return response.createException().flatMap(Mono::error);
        });
```

Với `exchangeToMono`, phải xử lý/tiêu thụ body đúng nhánh để không giữ tài nguyên kết nối. Không dùng API thấp hơn chỉ để đọc body khi `retrieve` đã đáp ứng.

### 8.2. Retry và timeout

Retry sẽ đăng ký lại pipeline và **có thể gửi lại HTTP request**. Chỉ retry khi hành vi được coi là an toàn/idempotent hoặc API hỗ trợ idempotency key, có giới hạn lần thử và phân loại lỗi cần retry. POST tạo đơn hàng không nên retry mù quáng. Timeout ở client giúp tránh chờ vô hạn nhưng không đảm bảo server chưa thực hiện hành động.

## 9. Sai lầm thường gặp

1. **Tạo Mono nhưng không có subscriber:** request chưa được gửi. Trong WebFlux, trả publisher; đừng chèn `subscribe()` tùy tiện rồi bỏ lỗi.
2. **Gọi `block()` trong event-loop:** phá mô hình non-blocking, giảm thông lượng hoặc bị Reactor từ chối. `block` trong demo console có chủ đích, không phải mẫu code dùng trong controller reactive.
3. **Ghép URI bằng chuỗi từ input:** dễ lỗi encoding, thiếu `?`/`&` hoặc bị đổi host ngoài ý muốn. Dùng URI template/builder và kiểm soát đích.
4. **Nhầm `Accept` với `Content-Type`:** cái đầu mô tả response mong muốn, cái sau mô tả request payload.
5. **Đưa `Mono<Dto>` vào `bodyValue`:** dùng `body(mono, Dto.class)` cho publisher; `bodyValue(dto)` cho DTO đã có.
6. **Cho rằng WebClient tự lưu cookie từ `Set-Cookie`:** phải có cơ chế quản lý/chuyển tiếp riêng.
7. **Để token hoặc session của người dùng trong default headers/cookies toàn cục:** nguy cơ lẫn dữ liệu giữa users. Gắn credential cho từng request.
8. **Bỏ qua status HTTP:** 4xx/5xx thường tạo tín hiệu lỗi, không tự giải mã thành DTO thành công.
9. **Retry mọi lỗi POST:** có thể tạo dữ liệu trùng. Cần tiêu chuẩn idempotent và số lần retry hữu hạn.
10. **Kỳ vọng `Flux` luôn nhận stream từng phần:** media type và server phải hỗ trợ streaming thật; client reactive không tự biến một response JSON đơn thành nguồn sự kiện vô hạn.
11. **Tạo WebClient mới ở mọi lời gọi:** làm cấu hình phân tán và có thể lãng phí tài nguyên kết nối.
12. **Log body/header nhạy cảm:** password, access token, cookie và dữ liệu riêng tư cần được che trong tracing và logs.

## 10. Tự kiểm tra

Trước khi gửi request, rà soát: HTTP method đúng chưa? URI path và query đã encode? Host có được phép? `Accept`/`Content-Type` đúng không? Header/cookie có rò bí mật? Body là DTO hay publisher? Server nhận JSON, form hay multipart? Pipeline được subscribe ở đâu? 4xx/5xx, timeout và retry được xử lý thế nào?

**Bài tập A:** Với `baseUrl("https://api.example.com")`, viết GET `/books/12?lang=vi&page=1`, gắn `Accept: application/json` và `X-Request-Id: learning-1`, kết quả là `Mono<String>`. Dùng `uri(builder -> ...)`, `accept(...)`, `header(...)`, `retrieve()`.

**Bài tập B:** Viết POST `/books` gửi record `BookCreate(String title, int quantity)` bằng `bodyValue`. Khi body đầu vào chuyển thành `Mono<BookCreate>`, đổi sang `body(mono, BookCreate.class)`. Giải thích lý do.

**Bài tập C:** Chạy ví dụ server cục bộ; thử bỏ header `X-Request-Id` hoặc đổi tên cookie rồi dự đoán lỗi. Khôi phục request và kiểm chứng output thành công.

## Tài liệu chính thức

- WebClient tổng quan: https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html
- WebClient builder/configuration: https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-builder.html
- Request body, form, multipart: https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-body.html
- retrieve / response / onStatus: https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-retrieve.html
- URI building: https://docs.spring.io/spring-framework/reference/web/webflux/uri-building.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-client-003">retrieve, onStatus, exchangeToMono và exchangeToFlux</a>

<details>
<summary>Xem chi tiết</summary>

# WebClient: `retrieve()`, `onStatus()`, `exchangeToMono()` và `exchangeToFlux()`

## 1. Mục tiêu và bối cảnh

`WebClient` là HTTP client thuộc Spring WebFlux, xây trên Reactor (`Mono`/`Flux`). Nó có thể dùng trong ứng dụng Spring WebFlux lẫn Spring MVC. Bốn phương thức trong bài giải quyết hai câu hỏi khác nhau:

1. **Ai quyết định cách xử lý HTTP status?** `retrieve()` cung cấp cách xử lý thông thường (mặc định biến HTTP 4xx/5xx thành lỗi), `onStatus()` tùy biến quy tắc đó; `exchangeToMono()` và `exchangeToFlux()` đưa toàn bộ `ClientResponse` vào hàm của bạn để bạn tự rẽ nhánh.
2. **Kết quả có bao nhiêu phần tử?** `Mono<T>` có tối đa một phần tử (hoặc rỗng/lỗi); `Flux<T>` có 0..N phần tử (hoặc lỗi). Chọn theo giao thức dữ liệu và nhu cầu xử lý, không chọn theo việc endpoint là GET hay POST.

Luồng xử lý điển hình:

```text
WebClient.get()/post() → uri()/headers() → gửi HTTP khi subscribe
                                           ├─ retrieve()
                                           │    ├─ onStatus(...) tùy chọn
                                           │    └─ bodyToMono / bodyToFlux / toEntity...
                                           ├─ exchangeToMono(response -> Mono<T>)
                                           └─ exchangeToFlux(response -> Flux<T>)
```

Điểm nền tảng: `Mono`/`Flux` là pipeline *lazy*: chỉ xây pipeline thường chưa gửi request; request được thực hiện khi có subscriber (ví dụ do framework subscribe hoặc trong ví dụ CLI dùng `block()`). Không gọi `block()` trên luồng xử lý non-blocking/event-loop của WebFlux.

## 2. `retrieve()`: đường đi chuẩn cho phản hồi thường gặp

`retrieve()` trả về `WebClient.ResponseSpec` để chọn cách lấy nội dung. Dùng khi muốn decode response bình thường, đồng thời chấp nhận quy tắc status mặc định.

```java
record Product(long id, String name) {}

WebClient client = WebClient.create("https://api.example.com");

Mono<Product> one = client.get()
    .uri("/products/{id}", 42)
    .accept(MediaType.APPLICATION_JSON)
    .retrieve()
    .bodyToMono(Product.class);

Flux<Product> many = client.get()
    .uri("/products")
    .retrieve()
    .bodyToFlux(Product.class);

Mono<ResponseEntity<Product>> withMetadata = client.get()
    .uri("/products/{id}", 42)
    .retrieve()
    .toEntity(Product.class);
```

Các đoạn trên là **minh họa**: cần import WebClient, MediaType, Reactor và ResponseEntity, cần server thực sự ở URL ví dụ. `bodyToMono(Product.class)` đọc một đối tượng; `bodyToFlux(Product.class)` đọc nhiều đối tượng, chẳng hạn JSON array hoặc một luồng định dạng có decoder thích hợp. `toEntity(Product.class)` giữ HTTP status/headers cùng body; đây là cách lấy metadata mà vẫn dùng `retrieve()`.

**Mặc định lỗi HTTP:** nếu không có handler khớp, status từ 400 trở lên được ánh xạ thành `WebClientResponseException` (bao gồm các subclass phù hợp, ví dụ NotFound). Exception chứa status, headers và có thể chứa body lỗi. Thành công HTTP không có nghĩa JSON luôn decode thành công: body không hợp lệ hoặc content type không phù hợp vẫn có thể phát sinh lỗi codec. Lỗi DNS, kết nối hoặc timeout là một nhóm lỗi khác (`WebClientRequestException` hoặc nguyên nhân tương ứng); `onStatus()` chỉ dành cho phản hồi đã nhận được HTTP status.

`retrieve()` không trả trực tiếp `ClientResponse` để bạn tùy ý đọc body hai lần. Khi cần chọn decoder theo status hoặc content-type theo logic riêng, xem `exchangeToMono()` / `exchangeToFlux()`.

## 3. `onStatus()`: ánh xạ HTTP status thành lỗi nghiệp vụ

`onStatus(Predicate<HttpStatusCode>, Function<ClientResponse, Mono<? extends Throwable>>)` được gọi **sau `retrieve()`, trước thao tác đọc body**. Predicate chọn status; hàm thứ hai trả về publisher tạo ra exception cần phát downstream.

```java
Mono<Product> product = client.get()
    .uri("/products/{id}", 42)
    .retrieve()
    .onStatus(
        status -> status.value() == 404,
        response -> response.bodyToMono(String.class)
            .defaultIfEmpty("Không có mô tả lỗi")
            .map(message -> new ProductNotFoundException(message))
    )
    .onStatus(
        HttpStatusCode::is5xxServerError,
        response -> response.createException()
    )
    .bodyToMono(Product.class);
```

Trong đó `ProductNotFoundException` là class exception nghiệp vụ do ứng dụng định nghĩa (đoạn trên không phải chương trình độc lập). Cần import `org.springframework.http.HttpStatusCode`. Handler đọc body 404 thành chuỗi rồi tạo exception; khi không có body, `defaultIfEmpty` đảm bảo vẫn phát lỗi. `response.createException()` tạo `Mono<WebClientResponseException>` theo status/headers/body. Ví dụ 5xx minh họa việc giữ thông tin HTTP gốc; nếu không cần đổi quy tắc này, có thể bỏ handler 5xx vì mặc định đã xử lý.

Ba lựa chọn dễ nhầm:

- Trả `Mono.just(new MyException(...))` hoặc một `Mono` phát ra `Throwable` → `retrieve()` biến thành **tín hiệu lỗi**.
- Trả `Mono.empty()` từ **hàm `onStatus`** → *bỏ xử lý status đó như lỗi*, để response đi tiếp qua bộ giải mã body. Đây **không phải** là bỏ qua response, cũng không biến 404 thành body rỗng.
- Gọi `onErrorResume(...)` **sau** `bodyToMono(...)` → quyết định cách khôi phục từ lỗi đã phát, ví dụ fallback một giá trị mặc định; nó có thể bắt cả lỗi status, kết nối hoặc decode tùy loại exception được lọc.

```java
Mono<String> displayName = client.get()
    .uri("/profiles/{id}", 42)
    .retrieve()
    .onStatus(
        status -> status.value() == 404,
        response -> response.bodyToMono(String.class)
            .map(message -> new IllegalArgumentException("Profile absent: " + message))
    )
    .bodyToMono(String.class)
    .onErrorResume(IllegalArgumentException.class, error -> Mono.just("Khách ẩn danh"));
```

**Thứ tự handler có ý nghĩa:** khi nhiều predicate khớp, handler khớp đầu tiên được áp dụng. Đặt quy tắc chuyên biệt (ví dụ 404) trước quy tắc tổng quát (4xx). Chú ý API có `onRawStatus(IntPredicate, ...)` nếu cần xử lý mã số tùy biến.

## 4. `exchangeToMono()`: quyết định từ `ClientResponse`, trả một kết quả

`exchangeToMono(response -> Mono<T>)` cho truy cập trực tiếp status và headers, rồi chọn cách decode hoặc lỗi. Đây là lựa chọn khi *mỗi status có thể mang body schema khác nhau*, hoặc ứng dụng muốn coi 404 là một kết quả hợp lệ.

```java
Mono<Optional<Product>> result = client.get()
    .uri("/products/{id}", 42)
    .exchangeToMono(response -> {
        int status = response.statusCode().value();
        if (status == 404) {
            return Mono.just(Optional.<Product>empty());
        }
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(Product.class).map(Optional::of);
        }
        return response.createException()
            .flatMap(error -> Mono.error(error));
    });
```

Đây là **đoạn minh họa** cần import `Optional`; body 404 cố ý không đọc vì không cần nó. Loại kết quả của mọi nhánh phải nhất quán thành `Mono<Optional<Product>>`. Với 2xx có body trống, nhánh minh họa phát `Mono.empty()` (không có Optional); nếu nghiệp vụ đòi kết quả `Optional.empty()`, bổ sung `.defaultIfEmpty(Optional.empty())` sau nhánh body.

Sự khác biệt quan trọng:

- **Không có cơ chế `onStatus()` ở đây**: status 404/500 **không tự bị chuyển thành lỗi** như khi dùng `retrieve()`. Hàm callback chịu trách nhiệm đầy đủ cho mọi status mà ứng dụng quan tâm.
- Để giữ lỗi HTTP gốc, dùng `response.createException()` hoặc `response.createError()` thay vì lặng lẽ decode body lỗi thành đối tượng thành công.
- Phải **decode/consume body bên trong callback** nếu cần. Sau khi `Mono` do callback trả về kết thúc, phần body chưa dùng được Spring kiểm tra và giải phóng; không thể cất `ClientResponse` để decode body sau ở phía downstream. Không cố đọc cùng body hai lần.
- Có thể truy cập `response.headers().contentType()` rồi rẽ nhánh decoder theo content type nếu endpoint có nhiều dạng body; phải xử lý cả trường hợp header vắng mặt.

## 5. `exchangeToFlux()`: quyết định từ `ClientResponse`, trả luồng nhiều phần tử

`exchangeToFlux(response -> Flux<T>)` tương tự `exchangeToMono` nhưng callback trả `Flux<T>`. Chọn khi cần chuỗi kết quả nhiều phần tử, ví dụ danh sách sản phẩm hoặc stream sự kiện. Đây không nhất thiết là streaming qua mạng: JSON array cũng có thể được decode thành `Flux<Product>`; luồng nhận dần từng chunk cần server cung cấp định dạng/đường truyền phù hợp (SSE, NDJSON, v.v.).

```java
Flux<Product> products = client.get()
    .uri("/products")
    .accept(MediaType.APPLICATION_JSON)
    .exchangeToFlux(response -> {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToFlux(Product.class);
        }
        if (response.statusCode().value() == 404) {
            return Flux.empty();
        }
        return response.createException()
            .flatMapMany(error -> Flux.error(error));
    });
```

`Flux.empty()` ở nhánh 404 diễn đạt rõ quyết định nghiệp vụ: danh sách không tồn tại được coi như không có phần tử. Trong nhiều API, 404 phải được xem là lỗi; đừng tự động chuyển mọi 404 thành danh sách trống.

Nếu decode trực tiếp qua `retrieve().bodyToFlux(Product.class)` đã đủ, **không cần** `exchangeToFlux()`. Tương tự `exchangeToMono`, body chỉ còn khả dụng trong lifecycle của callback.

## 6. Ví dụ chạy độc lập, phản hồi giả lập không cần server HTTP

Ví dụ dưới dùng `ExchangeFunction` giả lập bốn HTTP response. Vì vậy có thể chạy và kiểm chứng kết quả mà không gọi internet. Mẫu dùng Java 17+, Gradle và Spring Boot Starter WebFlux 3.3.13; dữ liệu JSON được Jackson decode thành Java `record`. Cấu trúc:

```text
webclient-status-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/WebClientStatusDemo.java
```

**`settings.gradle`**

```groovy
rootProject.name = 'webclient-status-demo'
```

**`build.gradle`**

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux:3.3.13'
}

application {
    mainClass = 'demo.WebClientStatusDemo'
}
```

**`src/main/java/demo/WebClientStatusDemo.java`**

```java
package demo;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class WebClientStatusDemo {
    public record Book(int id, String name) {}

    private static ClientResponse json(String body) {
        return ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .build();
    }

    public static void main(String[] args) {
        ExchangeFunction fakeHttp = request -> {
            ClientResponse response = switch (request.url().getPath()) {
                case "/book" -> json("{\"id\":7,\"name\":\"Lan\"}");
                case "/books" -> json(
                    "[{\"id\":7,\"name\":\"Lan\"},{\"id\":8,\"name\":\"Minh\"}]");
                case "/missing" -> ClientResponse.create(HttpStatus.NOT_FOUND)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .body("missing book")
                    .build();
                default -> ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("unexpected route")
                    .build();
            };
            return Mono.just(response);
        };

        WebClient client = WebClient.builder()
            .exchangeFunction(fakeHttp)
            .build();

        Book book = client.get().uri("/book")
            .retrieve()
            .bodyToMono(Book.class)
            .block();
        System.out.println("retrieve: " + book);

        String statusResult = client.get().uri("/missing")
            .retrieve()
            .onStatus(
                status -> status.value() == 404,
                response -> response.bodyToMono(String.class)
                    .map(msg -> new IllegalStateException("HTTP 404: " + msg))
            )
            .bodyToMono(Book.class)
            .map(Book::name)
            .onErrorResume(IllegalStateException.class,
                error -> Mono.just(error.getMessage()))
            .block();
        System.out.println("onStatus: " + statusResult);

        String notFound = client.get().uri("/missing")
            .exchangeToMono(response -> {
                if (response.statusCode().value() == 404) {
                    return Mono.just("Không tìm thấy");
                }
                if (response.statusCode().is2xxSuccessful()) {
                    return response.bodyToMono(String.class);
                }
                return response.createException()
                    .flatMap(error -> Mono.error(error));
            })
            .block();
        System.out.println("exchangeToMono: " + notFound);

        List<Book> books = client.get().uri("/books")
            .exchangeToFlux(response -> {
                if (response.statusCode().is2xxSuccessful()) {
                    return response.bodyToFlux(Book.class);
                }
                return response.createException()
                    .flatMapMany(error -> Flux.error(error));
            })
            .collectList()
            .block();
        System.out.println("exchangeToFlux: " + books);
    }
}
```

Chạy từ thư mục dự án với JDK 17+ và Gradle đã cài (hoặc thay `gradle` bằng `./gradlew` nếu có Gradle Wrapper):

```bash
gradle run --quiet
```

**Các dòng do chương trình in** (thư viện có thể in thêm log trong một số môi trường):

```text
retrieve: Book[id=7, name=Lan]
onStatus: HTTP 404: missing book
exchangeToMono: Không tìm thấy
exchangeToFlux: [Book[id=7, name=Lan], Book[id=8, name=Minh]]
```

Giải thích: phản hồi `/book` có JSON object nên `bodyToMono` sinh một `Book`. `/missing` ở đường `retrieve()` được `onStatus` chuyển thành `IllegalStateException` và `onErrorResume` đổi lỗi thành thông báo; cùng mã 404 được `exchangeToMono` chủ động coi là kết quả "Không tìm thấy". `/books` chứa JSON array, `bodyToFlux` decode hai phần tử rồi `collectList()` gom thành `List<Book>`. `block()` chỉ dùng để dễ quan sát trong hàm `main` kiểu CLI.

## 7. Bảng chọn nhanh

| Nhu cầu | API phù hợp | Ai xử lý status? | Kiểu kết quả điển hình |
| --- | --- | --- | --- |
| Đọc object JSON, nhận mặc định 4xx/5xx là lỗi | `retrieve().bodyToMono(...)` | Spring mặc định | `Mono<T>` |
| Đọc danh sách/stream, status mặc định | `retrieve().bodyToFlux(...)` | Spring mặc định | `Flux<T>` |
| Map 404/422/5xx thành exception nghiệp vụ | `retrieve().onStatus(...).bodyToMono/Flux(...)` | Spring + handler tùy biến | `Mono<T>` hoặc `Flux<T>` |
| 404 thành Optional rỗng; 200 giải mã theo schema riêng | `exchangeToMono(...)` | Chính callback | `Mono<T>` |
| Trả 0..N phần tử theo status/content type | `exchangeToFlux(...)` | Chính callback | `Flux<T>` |

Quy tắc thực hành: **bắt đầu với `retrieve()`**, thêm `onStatus()` khi chỉ cần tùy biến lỗi. Chọn `exchangeToMono()`/`exchangeToFlux()` khi nghiệp vụ phải điều khiển toàn bộ cách diễn giải response.

## 8. Lỗi phổ biến và cách tránh

1. **Tưởng `onStatus` dùng được sau `exchangeToMono`.** `onStatus` là phương thức của `ResponseSpec` sinh từ `retrieve`; ở nhánh `exchangeTo*` hãy rẽ nhánh theo `response.statusCode()` trong callback.
2. **Chỉ kiểm tra 2xx trong `exchangeTo*` và bỏ qua nhánh còn lại.** Không có mapping 4xx/5xx mặc định như `retrieve`; hãy dùng `createException`/`createError` hoặc một lựa chọn nghiệp vụ tường minh.
3. **Trả `Mono.empty()` từ `onStatus` rồi cho rằng request 404 đã bị bỏ qua.** Nó chỉ bỏ trạng thái lỗi; decode body vẫn tiếp tục và có thể thất bại nếu response là HTML/text, còn DTO mong JSON.
4. **Đọc body lỗi hai lần.** Body response là luồng tiêu thụ một lần; nếu cần chi tiết lỗi, decode ngay trong `onStatus` hay `exchangeTo*`, sau đó tạo exception hoặc một giá trị kết quả.
5. **Nhầm `bodyToFlux` với bảo đảm truyền dữ liệu real-time.** Flux biểu diễn 0..N phần tử, nhưng streaming thực sự còn phụ thuộc server, media type, codec và proxy/buffering.
6. **`onErrorResume` bắt mọi lỗi và trả success giả.** Nên lọc exception cụ thể; phân biệt HTTP status, kết nối, timeout, giải mã, và chỉ fallback khi hợp đồng nghiệp vụ cho phép.
7. **Gọi `block()` trong request handler WebFlux.** Việc chặn event loop có thể làm giảm khả năng phục vụ hoặc gây lỗi; trả thẳng `Mono`/`Flux`, phối hợp bằng `map`, `flatMap`, `zip`.
8. **Không xử lý body rỗng.** `bodyToMono` có thể hoàn thành không phát giá trị (ví dụ 204); cân nhắc `switchIfEmpty`, `defaultIfEmpty`, hoặc `Mono<Void>` theo hợp đồng endpoint.
9. **Trộn retry với mọi 4xx.** Retry chỉ nên áp dụng lỗi tạm thời có thể phục hồi, có giới hạn số lần và backoff; tránh retry mù request gây tác dụng phụ (POST không idempotent).
10. **Dựa vào `retrieve()` để nhận headers nhưng bỏ mất chúng.** Dùng `toEntity(...)`/`toEntityList(...)` nếu muốn status/headers và body nhưng không cần control flow của `exchangeTo*`.

## 9. Kiểm tra hiểu bài

- Endpoint GET `/orders/9` trả 404 kèm JSON lỗi. Nếu muốn phát `OrderNotFoundException` thì dùng gì? → `retrieve().onStatus(...)`, parse body lỗi và tạo exception; nếu 404 là một kết quả bình thường với schema khác, `exchangeToMono(...)` có thể phù hợp hơn.
- Endpoint trả JSON array 200 và 500 HTML. Nếu muốn danh sách hoặc lỗi HTTP chuẩn? → `retrieve().bodyToFlux(Order.class)` đã đủ. Nếu HTTP 404 phải trở thành luồng trống, có thể viết callback `exchangeToFlux(...)` rõ ràng.
- Với `onStatus(status -> status.value() == 404, response -> Mono.empty())` thì điều gì xảy ra? → Response 404 đi tiếp vào decoder; không tự động phát `Mono.empty()` ở downstream.
- Vì sao không giữ `ClientResponse` rồi decode ở `flatMap` bên ngoài `exchangeToMono`? → Callback phải quyết định và tiêu thụ body trong phạm vi của nó; phần body chưa dùng sẽ được giải phóng sau khi publisher callback kết thúc.

## 10. Tài liệu tham khảo

- Spring Framework — WebClient: https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html
- Spring Framework — `retrieve()` và `onStatus()`: https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-retrieve.html
- Spring Framework — `exchangeToMono()` / `exchangeToFlux()`: https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-exchange.html
- Spring Framework API — `WebClient.ResponseSpec`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/reactive/function/client/WebClient.ResponseSpec.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-client-004">Đọc JSON, kiểu generic, dữ liệu theo luồng và multipart</a>

<details>
<summary>Xem chi tiết</summary>

# WebClient: đọc JSON, kiểu generic, dữ liệu theo luồng và multipart

## 1. Mục tiêu và mô hình tư duy

**WebClient** là HTTP client trong Spring WebFlux. Nó tạo yêu cầu HTTP, gửi và nhận dữ liệu qua các codec, sau đó trả về **Mono<T>** (0–1 giá trị) hoặc **Flux<T>** (0–n giá trị). WebClient dùng được cả trong ứng dụng Spring MVC lẫn WebFlux; việc gọi WebClient không tự biến toàn bộ ứng dụng thành reactive.

Bài này tập trung vào **phần thân (body)** của HTTP request/response:
- Đọc JSON của một đối tượng và danh sách đối tượng.
- Giữ đúng kiểu generic, kể cả cấu trúc lồng nhau như API envelope chứa một page.
- Đọc dữ liệu liên tục qua NDJSON hoặc Server-Sent Events (SSE).
- Gửi biểu mẫu multipart gồm trường văn bản, JSON và tệp; khi máy chủ trả multipart, đọc các part tương ứng.
- Nhận biết ranh giới giữa dữ liệu dạng dòng, dữ liệu cần gom toàn bộ vào bộ nhớ và vòng đời tài nguyên.

Các đoạn mã dựa trên **Java 17+, Spring Framework 6.x / Spring Boot 3.x và Project Reactor**. Những đường dẫn HTTP bắt đầu bằng /api trong bài là **hợp đồng API minh họa**: chúng chỉ thực sự hoạt động nếu hệ thống đích triển khai các endpoint cùng Content-Type và cấu trúc body đã mô tả.

### Chọn cách đọc theo hợp đồng phản hồi

| Response Content-Type và dạng body | Mục tiêu | Cách đọc thông dụng |
| --- | --- | --- |
| application/json, một object | Lấy DTO | retrieve().bodyToMono(User.class) |
| application/json, một mảng | Lấy List<User> | bodyToMono(ParameterizedTypeReference<List<User>>) |
| application/json, object bọc generic | Lấy ApiEnvelope<Page<User>> | bodyToMono(ParameterizedTypeReference<...>) |
| application/x-ndjson, mỗi dòng một JSON | Nhận nhiều phần tử theo thời gian | retrieve().bodyToFlux(User.class) |
| text/event-stream | Nhận SSE và metadata event | bodyToFlux(ParameterizedTypeReference<ServerSentEvent<User>>) |
| multipart/form-data, **request** | Gửi text, JSON và file | MultipartBodyBuilder + BodyInserters |
| multipart/mixed, **response** | Đọc tên/headers/content của từng part | Reader cho MultiValueMap<String, Part> |

**Accept** diễn đạt kiểu phản hồi client mong muốn; **Content-Type** mô tả kiểu body đang gửi. Đừng nhầm hai header. WebClient chọn codec dựa trên **kiểu Java đích** và **media type thực tế**.

## 2. Chuẩn bị dependencies và WebClient

Trong dự án Spring Boot có quản lý phiên bản dependency, thêm starter sau vào **pom.xml**:

~~~xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
~~~

Starter cung cấp WebClient, Reactor và HTTP client mặc định phù hợp; Spring Boot thường tự cấu hình các codec Jackson khi Jackson có trên classpath. Nếu không dùng Spring Boot, phải tự quản lý các dependency Spring WebFlux, Reactor, HTTP connector và JSON codec tương ứng.

Tạo một client với địa chỉ gốc và giới hạn giải mã có chủ đích:

~~~java
WebClient client = WebClient.builder()
    .baseUrl("http://localhost:8080")
    .codecs(config -> config.defaultCodecs()
        .maxInMemorySize(2 * 1024 * 1024))
    .build();
~~~

**maxInMemorySize** là giới hạn bộ đệm của một số thao tác giải mã (ví dụ khi gom một giá trị JSON lớn); đây không phải giới hạn tổng kích thước mọi tệp tải lên hay dung lượng toàn stream. Trước khi tăng giới hạn, cần xác định vì sao response bị gom vào RAM.

WebClient thường được đăng ký thành bean dùng lại:

~~~java
@Configuration
class HttpClientConfig {
    @Bean
    WebClient externalClient(WebClient.Builder builder) {
        return builder.baseUrl("https://api.example.org").build();
    }
}
~~~

Hai import ở ví dụ bean là **org.springframework.context.annotation.Bean** và **org.springframework.context.annotation.Configuration**. URL example.org là địa chỉ minh họa, cần đổi theo hệ thống thật. Không tạo mới client cho từng bản ghi hay từng sự kiện.

## 3. Đọc JSON: một đối tượng, metadata HTTP và danh sách

Giả sử máy chủ có:

~~~http
GET /api/users/7
Accept: application/json

HTTP/1.1 200 OK
Content-Type: application/json

{"id":7,"name":"An"}
~~~

DTO Java 17:

~~~java
public record User(long id, String name) {}
~~~

Đọc JSON thành một đối tượng:

~~~java
Mono<User> user = client.get()
    .uri("/api/users/{id}", 7)
    .accept(MediaType.APPLICATION_JSON)
    .retrieve()
    .bodyToMono(User.class);
~~~

**bodyToMono(User.class)** giải mã body theo cấu trúc DTO. **Mono<User>** chưa có giá trị ngay tại lúc khai báo; yêu cầu HTTP sẽ được thực thi khi có subscription. Với phản hồi không có body, Mono có thể hoàn thành rỗng; một JSON object và một JSON array không thể tùy tiện dùng chung cùng kiểu đích.

Nếu cần mã trạng thái và headers:

~~~java
Mono<ResponseEntity<User>> result = client.get()
    .uri("/api/users/{id}", 7)
    .retrieve()
    .toEntity(User.class);
~~~

Phải lấy status từ **ResponseEntity**, không cố đưa status thành field của User. Với các trường JSON đặt tên khác field Java, dùng cấu hình Jackson hoặc annotation **@JsonProperty** thích hợp. Nếu response là JSON động, có thể giải mã thành **JsonNode** để duyệt mà chưa cần DTO cố định, nhưng nên ưu tiên DTO khi có hợp đồng rõ ràng.

### JSON array và hai cách biểu diễn

Ví dụ body:

~~~json
[
  {"id":7,"name":"An"},
  {"id":8,"name":"Bình"}
]
~~~

Nếu muốn một collection hoàn chỉnh: dùng tham chiếu generic ở mục 4. Nếu muốn xử lý từng phần tử có khả năng được decoder phát ra trong quá trình đọc, có thể dùng:

~~~java
Flux<User> userFlux = client.get()
    .uri("/api/users")
    .accept(MediaType.APPLICATION_JSON)
    .retrieve()
    .bodyToFlux(User.class);

Mono<List<User>> allUsers = userFlux.collectList();
~~~

**collectList()** gom toàn bộ phần tử trước khi phát kết quả và có chi phí bộ nhớ tương ứng. Việc **bodyToFlux** phát một mảng JSON theo phần tử không tự chứng minh server đang truyền realtime: mạng, JSON framing và cách server flush response đều ảnh hưởng thời điểm nhận.

## 4. Đọc JSON generic đúng kiểu

Trong Java, **List.class** chỉ giữ được kiểu thô; nó không biểu diễn **List<User>**. Vì type erasure, làm như sau dễ gây ra các phần tử được giải mã thành Map thay vì User:

~~~java
// Sai kiểu đích khi muốn List<User>: mất thông tin User.
Mono<List> raw = client.get()
    .uri("/api/users")
    .retrieve()
    .bodyToMono(List.class);
~~~

Hãy giữ thông tin kiểu bằng **ParameterizedTypeReference<T>**:

~~~java
ParameterizedTypeReference<List<User>> listType =
    new ParameterizedTypeReference<List<User>>() {};

Mono<List<User>> users = client.get()
    .uri("/api/users")
    .accept(MediaType.APPLICATION_JSON)
    .retrieve()
    .bodyToMono(listType);
~~~

Dạng anonymous subclass lưu lại thông tin generic để codec Jackson biết mỗi phần tử phải thành User. Dùng một kiểu **List<User>** ở bên trái không đủ nếu tham số đưa cho bodyToMono chỉ là **List.class**.

### Generic lồng nhau: API envelope và trang dữ liệu

Giả sử API trả về:

~~~json
{
  "status": "ok",
  "data": {
    "content": [
      {"id":7,"name":"An"},
      {"id":8,"name":"Bình"}
    ],
    "totalElements": 2
  }
}
~~~

Định nghĩa các record:

~~~java
public record Page<T>(List<T> content, long totalElements) {}
public record ApiEnvelope<T>(String status, T data) {}
~~~

Giải mã đầy đủ cấu trúc:

~~~java
ParameterizedTypeReference<ApiEnvelope<Page<User>>> type =
    new ParameterizedTypeReference<ApiEnvelope<Page<User>>>() {};

Mono<ApiEnvelope<Page<User>>> page = client.get()
    .uri("/api/users/page")
    .retrieve()
    .bodyToMono(type);
~~~

Kết quả của **data().content()** là **List<User>**, không phải danh sách Map. **ParameterizedTypeReference** còn dùng được với **bodyToFlux** nếu mỗi phần tử của luồng cũng là một kiểu generic (chẳng hạn **Event<User>**).

**Phân biệt hai trường hợp:** một HTTP response có JSON array nên đọc thành List<User> hoặc Flux<User>; một response có object bao **data.content** phải đọc bằng cấu trúc envelope tương ứng. **bodyToFlux(User.class)** không tự bóc trường data.content của envelope.

## 5. Dữ liệu theo luồng: NDJSON và SSE

**Streaming** nghĩa là xử lý dần các sự kiện/phần tử khi chúng đến thay vì chờ toàn bộ response hoàn tất. Có hai định dạng phổ biến:

1. **NDJSON** (application/x-ndjson): mỗi dòng chứa một giá trị JSON độc lập. Dòng mới kết thúc một bản ghi.
2. **SSE** (text/event-stream): mỗi event được đóng khung bằng các dòng **data:**, có thể thêm **id:**, **event:** và **retry:**. Hai định dạng không tương đương.

### 5.1 Đọc NDJSON

Body minh họa, được server trả với **Content-Type: application/x-ndjson**:

~~~text
{"id":7,"name":"An"}
{"id":8,"name":"Bình"}
{"id":9,"name":"Chi"}
~~~

Client:

~~~java
Flux<User> liveUsers = client.get()
    .uri("/api/users/live")
    .accept(MediaType.APPLICATION_NDJSON)
    .retrieve()
    .bodyToFlux(User.class);

Mono<Void> firstThree = liveUsers
    .take(3)
    .doOnNext(user -> System.out.println(user.name()))
    .then();
~~~

**take(3)** chủ động ngừng sau ba phần tử và hủy subscription phía client. Đây là kỹ thuật hữu ích với luồng kéo dài; khi nguồn kết thúc sớm, có thể nhận ít hơn ba phần tử. **then()** chỉ báo hoàn tất/lỗi, không giữ lại danh sách. Trong ví dụ này chưa chạy HTTP cho đến khi một bên subscribe hoặc block ở ranh giới không-reactive.

Để xem các bản ghi trên terminal khi server đã triển khai endpoint:

~~~bash
curl -N -H "Accept: application/x-ndjson" http://localhost:8080/api/users/live
~~~

Tùy proxy/server, Content-Type đúng vẫn chưa đủ cho trải nghiệm realtime nếu tầng gửi dữ liệu giữ buffer lâu không flush.

### 5.2 Đọc SSE với payload JSON

Ví dụ raw response:

~~~text
id: 101
event: user
data: {"id":7,"name":"An"}

id: 102
event: user
data: {"id":8,"name":"Bình"}

~~~

Đọc kèm metadata:

~~~java
ParameterizedTypeReference<ServerSentEvent<User>> eventType =
    new ParameterizedTypeReference<ServerSentEvent<User>>() {};

Flux<ServerSentEvent<User>> events = client.get()
    .uri("/api/users/events")
    .accept(MediaType.TEXT_EVENT_STREAM)
    .retrieve()
    .bodyToFlux(eventType);

Flux<User> payloads = events
    .filter(event -> event.data() != null)
    .map(ServerSentEvent::data);
~~~

Nếu không cần id/event/retry, hệ thống có thể đọc từng DTO trực tiếp khi codec và payload SSE phù hợp; dùng **ServerSentEvent<User>** để không mất thông tin protocol. Đừng ép stream SSE vào **Mono<List<User>>** nếu kết nối được thiết kế không kết thúc.

Kiểm tra trực tiếp:

~~~bash
curl -N -H "Accept: text/event-stream" http://localhost:8080/api/users/events
~~~

### 5.3 Vòng đời, backpressure và lỗi ở stream

- Một **Flux** là mô tả pipeline; subscription kích hoạt network I/O. Ở controller reactive, nên **return Flux/Mono** cho framework quản lý, tránh gọi **subscribe()** tùy tiện rồi đánh mất quản lý lỗi và hủy luồng.
- **Backpressure** giúp điều tiết nhu cầu phần tử trong Reactive Streams, nhưng không bảo đảm mọi API, proxy hay HTTP peer đều áp dụng cùng tốc độ phát. Tránh downstream chậm vô hạn và các toán tử gom dữ liệu không giới hạn.
- **timeout(Duration.ofSeconds(30))** có thể dùng khi thật sự yêu cầu dữ liệu xuất hiện trong thời hạn đó, nhưng với stream nhiều khoảng nghỉ, timeout có thể coi một khoảng nghỉ hợp lệ là lỗi. Phân biệt timeout kết nối, response và timeout giữa các phần tử.
- **take(n)**, hủy subscription và chính sách retry có thể ngắt hoặc tái kết nối stream; tự xác định cách tránh xử lý trùng event khi reconnect, ví dụ dùng id SSE.
- Không gọi **collectList()** trên stream vô hạn. Không giả định response **application/json** chứa một mảng khổng lồ tự nhiên sẽ được flush theo từng phần tử.

## 6. Multipart request: gửi trường text + JSON + file

Multipart không phải một JSON object lớn. Một body **multipart/form-data** được chia thành các **part**, mỗi part có tên, headers riêng (có thể có filename/Content-Type) và dữ liệu tương ứng. Tình huống điển hình: upload file cùng metadata có cấu trúc.

Hợp đồng ví dụ:
- **POST /api/uploads**
- Part **description**: một chuỗi.
- Part **metadata**: JSON của **UploadMetadata**.
- Part **file**: binary file.
- Response thành công: **application/json**, ví dụ **{"id":"f-123"}**.

Mã gửi multipart:

~~~java
public record UploadMetadata(String source, String category) {}
public record UploadReceipt(String id) {}

Path path = Path.of("./avatar.png");

MultipartBodyBuilder parts = new MultipartBodyBuilder();
parts.part("description", "Ảnh đại diện");
parts.part("metadata", new UploadMetadata("mobile", "avatar"),
           MediaType.APPLICATION_JSON);
parts.part("file", new FileSystemResource(path), MediaType.IMAGE_PNG);

Mono<UploadReceipt> uploaded = client.post()
    .uri("/api/uploads")
    .contentType(MediaType.MULTIPART_FORM_DATA)
    .body(BodyInserters.fromMultipartData(parts.build()))
    .retrieve()
    .bodyToMono(UploadReceipt.class);
~~~

Giải thích:
- **MultipartBodyBuilder** tạo một **MultiValueMap<String, HttpEntity<?>>** có thông tin part và headers. Không tự ghép boundary hay chuỗi multipart bằng tay; framework gán boundary và mã hóa body.
- **FileSystemResource** cho codec đọc file và lấy filename. Truyền **Path**/đường dẫn như một chuỗi thông thường sẽ chỉ gửi văn bản, không gửi bytes của file.
- Metadata có **Content-Type: application/json** riêng; **Content-Type: multipart/form-data** là header của request tổng thể.
- Server có thể trả một **JSON response** cho upload multipart; khi đó **bodyToMono(UploadReceipt.class)** là đúng. Không nhầm kiểu request với kiểu response.
- File phải tồn tại và client cần quyền đọc file. Nếu server kỳ vọng tên part khác, Spring không tự đổi tên part cho đúng.

Bản cực ngắn khi chỉ gửi text và một Resource:

~~~java
Mono<String> answer = client.post()
    .uri("/api/uploads/simple")
    .body(BodyInserters.fromMultipartData("description", "Tài liệu")
        .with("file", new FileSystemResource(Path.of("./document.pdf"))))
    .retrieve()
    .bodyToMono(String.class);
~~~

Với file rất lớn hoặc part có nguồn reactive, **MultipartBodyBuilder.asyncPart(...)** cho phép đưa **Publisher** cùng kiểu phần tử vào part. Khi cần thực sự truyền multipart tuần tự, Spring còn hỗ trợ **FormPartEvent** và **FilePartEvent**. Chỉ dùng kỹ thuật nâng cao đó khi hiểu việc cấp và giải phóng **DataBuffer**; không biến toàn bộ file thành **byte[]** lớn chỉ để tiện gửi.

### Góc nhìn server để đối chiếu hợp đồng

Ví dụ controller WebFlux nhận đúng các part trên:

~~~java
@PostMapping(path = "/api/uploads",
             consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Mono<UploadReceipt> upload(
        @RequestPart("metadata") Mono<UploadMetadata> metadata,
        @RequestPart("file") FilePart file) {
    return metadata.flatMap(info ->
        file.transferTo(Path.of("./uploads", file.filename()))
            .thenReturn(new UploadReceipt("f-123")));
}
~~~

Đây chỉ là ví dụ minh họa cách ghép API client–server; ứng dụng thực phải **kiểm tra tên tệp và đường dẫn đích**, tạo thư mục, xử lý lỗi file trùng và không dùng trực tiếp tên người dùng cung cấp để ghép đường dẫn lưu trữ. Trường **description** trong request có thể được server nhận thêm bằng **@RequestPart("description") String description** nếu cần.

## 7. Khi *response* là multipart: đọc từng part

Khác với mục 6, có API trả **Content-Type: multipart/mixed** (hoặc multipart/form-data) cho một response gồm nhiều part. Đọc body kiểu **List<User>** hay **byte[]** không giúp nhận metadata và ranh giới giữa các part. Một hướng xử lý là giải mã thành **MultiValueMap<String, Part>** qua **MultipartHttpMessageReader**. Với WebClient cần kiểm tra client codec đã có reader này chưa; có thể đăng ký reader tường minh như sau:

~~~java
WebClient multipartClient = client.mutate()
    .codecs(config -> config.customCodecs()
        .register(new MultipartHttpMessageReader(
            new DefaultPartHttpMessageReader())))
    .build();

ParameterizedTypeReference<MultiValueMap<String, Part>> multipartType =
    new ParameterizedTypeReference<MultiValueMap<String, Part>>() {};

Mono<MultiValueMap<String, Part>> response = multipartClient.get()
    .uri("/api/bundle")
    .accept(MediaType.MULTIPART_MIXED)
    .retrieve()
    .bodyToMono(multipartType);
~~~

Giả sử response có file part mang tên **file**:

~~~java
Mono<Void> saved = response.flatMap(map -> {
    Part part = map.getFirst("file");
    if (part instanceof FilePart filePart) {
        return filePart.transferTo(Path.of("./downloaded.bin"));
    }
    return Mono.error(new IllegalStateException(
        "Response không chứa file part tên 'file'"));
});
~~~

**FormFieldPart** cung cấp giá trị văn bản của form field; **FilePart** có **filename()**, **headers()** và **transferTo(Path)**. Trường hợp một tên có nhiều part, dùng **get(name)** để lấy danh sách, thay vì **getFirst(name)**. **FilePart** chứa nội dung có vòng đời tài nguyên; phải đọc, chuyển tiếp hoặc ghi file đúng cách, không giữ tham chiếu Part rồi bỏ dở luồng. Cấu hình reader ở đây giúp trình bày rõ codec cần dùng, vì mặc định các codec client giữa phiên bản/cấu hình có thể khác nhau.

Đối với phản hồi multipart rất lớn cần xử lý từng khối theo stream, có thể chọn **PartEventHttpMessageReader** / **PartEvent** phù hợp và xử lý các **DataBuffer** có kiểm soát; đọc map toàn bộ part không phải giải pháp mặc định tốt cho mọi khối lượng dữ liệu. Khác với **multipart/form-data**, **application/x-www-form-urlencoded** chỉ là cặp khóa–giá trị đơn giản, không phải file part.

## 8. Một lớp client hoàn chỉnh, nhất quán về kiểu

Mã sau tổng hợp các trường hợp chính vào một lớp. File **ReadingWithWebClient.java** thuộc package **demo**. Nó **biên dịch với dependency WebFlux được cấu hình như mục 2**; khi gọi các phương thức, hệ thống HTTP phía server phải hiện thực đúng những endpoint ví dụ. Vì kết quả phụ thuộc dữ liệu từ server nên không có stdout cố định để khẳng định trước.

~~~java
package demo;

import java.nio.file.Path;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.DefaultPartHttpMessageReader;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.MultipartHttpMessageReader;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class ReadingWithWebClient {
    public record User(long id, String name) {}
    public record Page<T>(List<T> content, long totalElements) {}
    public record ApiEnvelope<T>(String status, T data) {}
    public record UploadMetadata(String source, String category) {}
    public record UploadReceipt(String id) {}

    private final WebClient client;

    public ReadingWithWebClient(String baseUrl) {
        this.client = WebClient.builder()
            .baseUrl(baseUrl)
            .build();
    }

    public Mono<User> readUser(long id) {
        return client.get()
            .uri("/api/users/{id}", id)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(User.class);
    }

    public Mono<List<User>> readUserList() {
        ParameterizedTypeReference<List<User>> type =
            new ParameterizedTypeReference<List<User>>() {};
        return client.get()
            .uri("/api/users")
            .retrieve()
            .bodyToMono(type);
    }

    public Mono<ApiEnvelope<Page<User>>> readPage() {
        ParameterizedTypeReference<ApiEnvelope<Page<User>>> type =
            new ParameterizedTypeReference<ApiEnvelope<Page<User>>>() {};
        return client.get()
            .uri("/api/users/page")
            .retrieve()
            .bodyToMono(type);
    }

    public Flux<User> readNdjson() {
        return client.get()
            .uri("/api/users/live")
            .accept(MediaType.APPLICATION_NDJSON)
            .retrieve()
            .bodyToFlux(User.class);
    }

    public Flux<ServerSentEvent<User>> readSse() {
        ParameterizedTypeReference<ServerSentEvent<User>> type =
            new ParameterizedTypeReference<ServerSentEvent<User>>() {};
        return client.get()
            .uri("/api/users/events")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(type);
    }

    public Mono<UploadReceipt> upload(Path sourceFile) {
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("description", "Ảnh đại diện");
        parts.part("metadata",
            new UploadMetadata("mobile", "avatar"),
            MediaType.APPLICATION_JSON);
        parts.part("file", new FileSystemResource(sourceFile));

        return client.post()
            .uri("/api/uploads")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(parts.build()))
            .retrieve()
            .bodyToMono(UploadReceipt.class);
    }

    public Mono<Void> downloadMultipartFile(Path destination) {
        WebClient readerClient = client.mutate()
            .codecs(config -> config.customCodecs()
                .register(new MultipartHttpMessageReader(
                    new DefaultPartHttpMessageReader())))
            .build();

        ParameterizedTypeReference<MultiValueMap<String, Part>> type =
            new ParameterizedTypeReference<MultiValueMap<String, Part>>() {};

        return readerClient.get()
            .uri("/api/bundle")
            .accept(MediaType.MULTIPART_MIXED)
            .retrieve()
            .bodyToMono(type)
            .flatMap(parts -> {
                Part part = parts.getFirst("file");
                if (part instanceof FilePart filePart) {
                    return filePart.transferTo(destination);
                }
                return Mono.error(new IllegalStateException(
                    "Thiếu file part trong response"));
            });
    }
}
~~~

**Cách sử dụng:** tạo **new ReadingWithWebClient("http://localhost:8080")**, rồi ở một thành phần reactive trả trực tiếp **readUser(7)** hoặc **readNdjson()** cho caller. Ở một điểm vào dòng lệnh hoặc test không chạy trên event-loop có thể dùng **readUser(7).block()**, nhưng không nên dùng **block()** trong handler WebFlux/reactive pipeline. Không gọi **subscribe()** rồi lập tức trả HTTP 200 cho một upload quan trọng: như vậy caller không biết upload đã hoàn tất hay lỗi.

### Theo dõi lỗi HTTP có ngữ cảnh

**retrieve()** mặc định chuyển HTTP 4xx/5xx thành **WebClientResponseException**; với nghiệp vụ cần thông báo riêng có thể đặt **onStatus** trước bodyToMono:

~~~java
Mono<User> user = client.get()
    .uri("/api/users/{id}", 7)
    .retrieve()
    .onStatus(
        status -> status.value() == 404,
        response -> Mono.just(
            new IllegalArgumentException("Không tìm thấy user 7")))
    .bodyToMono(User.class);
~~~

Khi dùng **exchangeToMono/Flux** để tự chọn cách đọc dựa trên status, phải xử lý response body ngay trong callback phù hợp; đừng để body chưa đọc rồi cố giải mã sau callback. Đặt timeout/retry theo tính chất nghiệp vụ; retry upload POST không có idempotency key có thể tạo bản ghi trùng.

## 9. Các lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| Nhận LinkedHashMap thay vì User trong danh sách | Dùng List.class, mất generic | Dùng ParameterizedTypeReference<List<User>> |
| Decode báo mismatch hoặc không lấy được phần tử | JSON root object/array khác DTO | Xem body và schema thực; DTO phải khớp JSON |
| Dữ liệu NDJSON không hiện dần | Server/proxy không flush hoặc response không phải NDJSON | Kiểm tra Content-Type, framing, curl -N và hành vi flush |
| SSE không đọc được bằng JSON codec thường | Nhầm text/event-stream với application/json | Dùng accept(TEXT_EVENT_STREAM) và ServerSentEvent<T> |
| Tăng RAM khi stream chạy lâu | collectList/cache/buffer không giới hạn | Xử lý mỗi phần tử, giới hạn/hủy rõ ràng |
| Multipart chỉ gửi đường dẫn tệp | Đưa String path thay cho file Resource | Dùng FileSystemResource hoặc dữ liệu file được encode đúng |
| HTTP 415 / 400 khi upload | Content-Type sai hoặc tên part khác hợp đồng | So request headers, boundary, part names, per-part media type |
| Cannot decode multipart response | Client chưa có multipart reader thích hợp | Đăng ký MultipartHttpMessageReader và kiểm tra kiểu Part |
| DataBufferLimitException | Decoder phải gom một giá trị quá giới hạn | Kiểm tra payload, streaming strategy và giới hạn codec |
| RuntimeException sau khi block() | 4xx/5xx, timeout hoặc lỗi giải mã | Bắt đúng loại lỗi; đọc status + body an toàn khi cần |
| Treo tài nguyên/kết nối khi xử lý part | Bỏ qua nội dung part hoặc hủy sai vòng đời | Consume/transfer/release các part và DataBuffer đúng API |

Không log tràn lan toàn bộ JSON chứa dữ liệu cá nhân hoặc binary multipart. Đối với file tải về, dùng đường dẫn đã kiểm soát và kiểm tra kích thước, định dạng, tên file theo yêu cầu ứng dụng.

## 10. Kiểm tra kiến thức qua tình huống

1. **GET /users** trả **application/json** với một mảng 100 user: muốn List<User> đầy đủ → **bodyToMono(ParameterizedTypeReference<List<User>>)**; nếu muốn xử lý từng phần tử, cân nhắc **bodyToFlux(User.class)**.
2. **GET /users/page** trả object **{"data":{"content":[...]}}**: phải có DTO envelope/page đúng schema; **bodyToFlux(User.class)** không tự đi vào field content.
3. **GET /users/live** gửi từng JSON object trên từng dòng: dùng **application/x-ndjson + bodyToFlux(User.class)**, kiểm tra server flush và tránh collectList vô hạn.
4. **GET /events** trả **text/event-stream** có event id và JSON data: dùng **ServerSentEvent<User>** để có cả metadata.
5. **POST /uploads** đòi part **file** và part **metadata** dạng JSON: dùng MultipartBodyBuilder; response thành công là JSON thì đọc **UploadReceipt**, không đọc như multipart chỉ vì request là multipart.
6. **GET /bundle** trả **multipart/mixed** gồm text và file: client cần multipart reader phù hợp; đọc **Part**, chuyển nội dung file bằng API dành cho FilePart hoặc dữ liệu reactive tương ứng.

### Checklist khi tự triển khai

- Xác nhận status, Content-Type và schema body của **response thực tế** trước khi chọn kiểu Java.
- Với generic, truyền **ParameterizedTypeReference** đến tận lời gọi bodyToMono/bodyToFlux.
- Với streaming, xác định định dạng NDJSON/SSE, ranh giới bản ghi, flush, hủy, timeout và backpressure.
- Với multipart, phân biệt chiều **gửi request** và **đọc response**; kiểm tra tên part, filename, headers, quản lý file/DataBuffer.
- Đưa Mono/Flux vào chuỗi xử lý cuối cùng và quản lý lỗi; chỉ block tại ranh giới đồng bộ có kiểm soát.

## Tài liệu tham khảo

- [Spring Framework – WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)
- [Spring Framework – WebClient retrieve() và giải mã response](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-retrieve.html)
- [Spring Framework – WebClient request body và multipart](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-body.html)
- [Spring Framework – Reactive Core, codecs và multipart](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html)
- [Spring Framework – WebClient exchangeToMono/Flux](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-exchange.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-client-005">ExchangeFilterFunction và cấu hình dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# ExchangeFilterFunction và cấu hình dùng chung trong WebClient

## 1. Bối cảnh: vì sao WebClient cần bộ lọc?

`WebClient` là HTTP client theo lập trình phản ứng của Spring WebFlux. Một ứng dụng thường gọi nhiều API bên ngoài và các lần gọi đó có những quy tắc lặp lại: đặt địa chỉ gốc, thêm header, gửi thông tin xác thực, ghi log, gắn mã theo dõi hoặc quan sát trạng thái trả về.

Nếu từng phương thức tự lặp lại các việc này, logic nghiệp vụ bị trộn với cách thực hiện HTTP; khi thay đổi chính sách, ta phải sửa nhiều chỗ. `WebClient.Builder` giải quyết phần **cấu hình mặc định dùng chung**, còn `ExchangeFilterFunction` xử lý **logic cắt ngang từng lần trao đổi HTTP**. Hai cơ chế phối hợp với nhau nhưng có nhiệm vụ khác nhau.

Ví dụ: dịch vụ đơn hàng cần gọi API tồn kho. Phần nghiệp vụ chỉ quan tâm “lấy tồn kho của sản phẩm”; cấu hình dùng chung có thể giữ `baseUrl` và `Accept`, còn filter có thể thêm mã truy vết và ghi nhận phương thức, đường dẫn, mã trạng thái. Một request đặc biệt vẫn có thể được tùy chỉnh riêng.

Mục tiêu của bài:

- Hiểu filter chạy ở đâu trong chuỗi gọi `WebClient` và ý nghĩa của `ClientRequest`, `ExchangeFunction`, `ClientResponse`.
- Biết tạo, đăng ký, sắp xếp và kết hợp các `ExchangeFilterFunction`.
- Phân biệt `defaultHeader`, `defaultRequest`, `filter` và `mutate()`; tổ chức cấu hình cho nhiều HTTP client.
- Biết cách đưa dữ liệu riêng từng request vào filter, kiểm thử và tránh các lỗi liên quan đến phản ứng bất đồng bộ, bảo mật, response body.

## 2. Mô hình hoạt động của ExchangeFilterFunction

Một filter có hợp đồng cốt lõi:

```java
Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next);
```

Trong đó:

| Thành phần | Ý nghĩa |
| --- | --- |
| `ClientRequest` | Request đã được biểu diễn thành phương thức, URL, headers, cookies, body và attributes; về nguyên tắc là đối tượng bất biến. |
| `ExchangeFunction next` | Bước tiếp theo trong chuỗi: có thể là filter kế tiếp hoặc thành phần thực sự gửi HTTP. |
| `ClientResponse` | Phản hồi HTTP gồm trạng thái, headers và body dạng luồng để xử lý tiếp. |
| `Mono<ClientResponse>` | Kết quả bất đồng bộ của một lần trao đổi; filter có thể biến đổi, quan sát hoặc trả về lỗi theo ngữ nghĩa Reactor. |

Một filter tối giản:

```java
ExchangeFilterFunction addClientHeader = (request, next) -> {
    ClientRequest updated = ClientRequest.from(request)
            .headers(headers -> headers.set("X-Client-Name", "inventory-service"))
            .build();

    return next.exchange(updated);
};

WebClient client = WebClient.builder()
        .baseUrl("https://inventory.example")
        .filter(addClientHeader)
        .build();
```

`ClientRequest.from(request)` sao chép dữ liệu request hiện có vào builder để thay đổi phần cần thiết. `headers(...set(...))` đặt một giá trị header; dùng `header("Tên", "giá trị")` để thêm giá trị nếu đó là chủ đích. Trong filter trên, `next.exchange(updated)` giữ chuỗi xử lý tiếp tục. Không gọi `next.exchange` nghĩa là filter chặn đường đi mặc định và phải tự trả một `Mono<ClientResponse>` phù hợp hoặc một tín hiệu lỗi.

**Thời điểm chạy:** lời gọi `client.get().uri(...).retrieve().bodyToMono(...)` tạo mô tả công việc phản ứng. Filter thực thi khi luồng được đăng ký (subscribe), chẳng hạn bởi một tầng trên hoặc `block()` trong chương trình minh họa. Không được mặc định rằng chỉ dựng `Mono` đã phát đi request.

### 2.1 Thứ tự filter: đi và về

Giả sử builder đăng ký `filterA` rồi `filterB`:

```text
Đăng ký luồng
    |
    v
A: trước next.exchange(...)
    |
    v
B: trước next.exchange(...)
    |
    v
Trao đổi HTTP
    |
    v
B: quan sát ClientResponse
    |
    v
A: quan sát ClientResponse
    |
    v
Mã gọi WebClient nhận kết quả
```

Thứ tự phía request là **A → B → HTTP**; khi response quay về qua các bước `flatMap`/`doOnNext` tương ứng, thứ tự là **HTTP → B → A**. Nếu lỗi xảy ra trước khi có `ClientResponse`, các nhánh `doOnError`/`onErrorResume` được đăng ký dọc chuỗi sẽ quan sát hoặc xử lý tín hiệu đó. Mỗi lần đăng ký luồng có thể thực hiện lại trao đổi; lưu ý này đặc biệt quan trọng khi kết hợp `retry`.

Một filter ghi nhận thông tin đầu vào/đầu ra nhưng không tiêu thụ body:

```java
ExchangeFilterFunction logMetadata = (request, next) -> {
    long started = System.nanoTime();
    System.out.println("Request: " + request.method() + " " + request.url().getPath());

    return next.exchange(request)
            .doOnNext(response -> {
                long elapsedMs = (System.nanoTime() - started) / 1_000_000;
                System.out.println("Status: " + response.statusCode().value()
                        + ", elapsedMs=" + elapsedMs);
            })
            .doOnError(error ->
                    System.err.println("Transport/filter failure: "
                            + error.getClass().getSimpleName()));
};
```

Đây là ví dụ học tập; trong ứng dụng thực, dùng logger có cấu hình và thư viện quan sát được tích hợp. Không ghi `Authorization`, `Cookie`, body chứa dữ liệu cá nhân hoặc toàn bộ URL có tham số bí mật. Nếu cần đo thời gian cho từng lần đăng ký hoặc thử lại, đặt thời điểm bắt đầu trong `Mono.defer(...)` hoặc cơ chế đo chuyên dụng để tránh dùng lại mốc thời gian không phù hợp.

## 3. Tạo filter theo từng mục đích

### 3.1 Dạng đầy đủ và dạng bộ chuyển đổi

Dạng lambda hai tham số `(request, next) -> ...` hữu ích khi phải xem cả request và response. Với thao tác một phía, có thể dùng:

```java
ExchangeFilterFunction requestOnly =
        ExchangeFilterFunction.ofRequestProcessor(request ->
                Mono.just(ClientRequest.from(request)
                        .headers(headers -> headers.set("X-Source", "web"))
                        .build()));

ExchangeFilterFunction responseOnly =
        ExchangeFilterFunction.ofResponseProcessor(response -> {
            System.out.println("HTTP " + response.statusCode().value());
            return Mono.just(response);
        });
```

Cần import `reactor.core.publisher.Mono` và các kiểu `org.springframework.web.reactive.function.client.*`. Hai factory ở trên thích hợp khi chỉ biến đổi một phía mà không cần tự quản lý `next.exchange(...)`. Phải trả lại request/response hợp lệ để chuỗi tiếp tục.

Có thể kết hợp hai filter theo thứ tự với `first.andThen(second)`; khi dùng `WebClient.Builder`, gọi `filter(...)` nhiều lần thường trực quan hơn.

### 3.2 Authentication, token và chính sách header

`ExchangeFilterFunctions.basicAuthentication(username, password)` là filter có sẵn để gửi HTTP Basic Authentication, phù hợp khi API thực sự yêu cầu Basic Auth và bí mật được cung cấp an toàn từ cấu hình ngoài mã nguồn.

Nếu cần Bearer token thay đổi theo request, có thể đặt header trực tiếp trên request hoặc xây filter nhận nguồn token tương ứng. Ví dụ cấu trúc cho token đã có sẵn, không truy vấn token qua mạng:

```java
ExchangeFilterFunction bearerToken(String token) {
    return (request, next) -> {
        ClientRequest authorized = ClientRequest.from(request)
                .headers(headers -> headers.setBearerAuth(token))
                .build();
        return next.exchange(authorized);
    };
}
```

Đoạn này là **phương thức minh họa**, được đặt trong một lớp Java và cần token hợp lệ của đúng API. Không hardcode secret thực trong source, không dùng một token cố định cho tất cả người dùng nếu token đại diện cho danh tính từng người. Khi token cần nạp/làm mới bất đồng bộ, trả chuỗi `Mono` từ cơ chế cấp token; không dùng I/O chặn luồng sự kiện (event loop). Chỉ gửi credential tới host tin cậy đã cấu hình; không tự chuyển tiếp header xác thực sang host khác.

### 3.3 Chặn yêu cầu và xử lý phản hồi

Filter có thể kiểm tra chính sách trước khi gửi, ví dụ chặn yêu cầu thiếu một attribute bắt buộc:

```java
ExchangeFilterFunction requireTenant = (request, next) -> {
    if (request.attribute("tenantId").isEmpty()) {
        return Mono.error(new IllegalArgumentException("Missing tenantId"));
    }
    return next.exchange(request);
};
```

Đây là lỗi cục bộ ở client, **không phải** HTTP response có mã 4xx. Nếu muốn tạo một `ClientResponse` giả lập để chặn đường gửi thì phải xác định rõ trạng thái/body của response và ý nghĩa với mã gọi.

**Đặc biệt chú ý body:** filter nhìn thấy `ClientResponse` nhưng body là nội dung cần được tiêu thụ hoặc giải phóng đúng cách. Filter chỉ quan sát metadata rồi `return Mono.just(response)` thường an toàn vì tầng gọi còn nhận response để đọc body. Trái lại, nếu filter tự đọc/loại bỏ response (ví dụ đổi luồng sau HTTP 401), phải giải phóng nội dung cũ, chẳng hạn thông qua `response.releaseBody()`, trước khi thực hiện yêu cầu khác. Không gọi `response.bodyToMono(String.class)` để “log thử” rồi chuyển nguyên response đã bị tiêu thụ sang bên dưới; dễ gây lỗi đọc body hoặc rò tài nguyên.

`retrieve()` thường chuyển HTTP 4xx/5xx thành lỗi theo chính sách mặc định ở tầng xử lý response; bản thân `next.exchange(...)` có thể trả thành công một `ClientResponse` mang trạng thái 404/500. Vì vậy `doOnError` của filter **không thay thế** `doOnNext(response -> kiểm tra status)` để quan sát các HTTP status. Định nghĩa ánh xạ mã lỗi nghiệp vụ nên đặt gần API cụ thể bằng `retrieve().onStatus(...)` khi phù hợp.

## 4. Cấu hình dùng chung với WebClient.Builder

`WebClient.Builder` gom các cấu hình thường dùng trước khi tạo client:

| API trên builder | Dùng khi nào | Điểm cần nhớ |
| --- | --- | --- |
| `baseUrl(String)` | Nhiều request gọi chung một API host/prefix. | Request dùng URL tuyệt đối có thể chỉ định đích riêng; cần kiểm soát để không làm lộ credential. |
| `defaultHeader(name, values...)` | Header mặc định như `Accept` hay định danh ứng dụng. | Chỉ thêm giá trị mặc định nếu request chưa cung cấp header cùng tên. |
| `defaultHeaders(Consumer<HttpHeaders>)` | Thêm, thay hoặc xóa các default header đã khai báo. | Khác với việc thay đổi headers của một request đang đi qua filter. |
| `defaultCookie(...)`, `defaultCookies(...)` | Cookie mặc định cho các request thích hợp. | Không sử dụng cookie nhạy cảm cho client gọi nhiều miền. |
| `defaultRequest(Consumer<RequestHeadersSpec<?>>)` | Tùy biến cấu trúc request mỗi lần tạo, có thể đặt attribute. | Không xử lý response, không dùng thay filter cần quan sát response. |
| `filter(ExchangeFilterFunction)` | Thêm một filter vào cuối chuỗi. | Thứ tự đăng ký quan trọng. |
| `filters(Consumer<List<ExchangeFilterFunction>>)` | Xem/sắp xếp/xóa danh sách filter. | Dùng có chủ đích, tránh thêm trùng. |
| `clientConnector(...)` | Chọn và cấu hình HTTP transport, timeout/kết nối ở lớp thích hợp. | Không nhầm với timeout nghiệp vụ của luồng. |
| `codecs(...)` | Cấu hình bộ mã hóa/giải mã, giới hạn bộ nhớ. | Không thuộc trách nhiệm của filter. |

Ví dụ khai báo một client dùng chung bằng cấu hình Spring; có thể dùng với Spring Framework/Spring Boot có `spring-webflux`:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class RemoteClientConfig {

    @Bean
    WebClient inventoryClient(WebClient.Builder sharedBuilder) {
        ExchangeFilterFunction clientTag = (request, next) -> {
            ClientRequest updated = ClientRequest.from(request)
                    .headers(headers -> {
                        if (!headers.containsKey("X-Client-Tag")) {
                            headers.set("X-Client-Tag", "inventory");
                        }
                    })
                    .build();
            return next.exchange(updated);
        };

        return sharedBuilder.clone()
                .baseUrl("https://inventory.example")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(clientTag)
                .build();
    }
}
```

`WebClient.Builder` được inject sẵn trong một ứng dụng Spring Boot đã bật tính năng tương ứng; trong ứng dụng Spring Framework thuần, phải tự khai báo builder bean hoặc tạo qua `WebClient.builder()`. `clone()` cô lập các thay đổi cấu hình trên builder khỏi cấu hình của nơi khác sử dụng cùng builder. Không truyền đối tượng `WebClient.Builder` có thể bị thay đổi giữa nhiều luồng để cấu hình động mỗi request.

Ví dụ mã gọi gọn, tái sử dụng bean đã cấu hình:

```java
Mono<String> stockResponse = inventoryClient.get()
        .uri("/api/stocks/{sku}", "SKU-123")
        .retrieve()
        .bodyToMono(String.class);
```

Đây là một **đoạn minh họa bên trong component/service** có quyền truy cập `inventoryClient`; không phải chương trình Java độc lập. Để chạy thực tế cần API, DTO, cấu hình xác thực và xử lý lỗi phù hợp hệ thống.

### 4.1 Chia sẻ lõi cấu hình, tách client theo API

Tổ chức điển hình:

```text
Cấu hình chung của ứng dụng (log an toàn, quan sát, codec/connector)
                  |
                  v
           WebClient.Builder
              /       \
             v         v
     inventoryClient  paymentClient
     baseUrl A         baseUrl B
     headers A         auth/filter B
```

Không nên dùng một `baseUrl`, credential hoặc filter riêng của API A cho API B. Nếu inject cùng một builder có khả năng thay đổi, dùng `clone()` trước khi cấu hình riêng. Tạo một `WebClient` mỗi lần gọi cũng làm phân tán cấu hình; thông thường tạo các instance dùng chung khi khởi động hoặc tạo bản sao có chủ đích.

### 4.2 WebClient bất biến và mutate()

Sau khi `build()`, `WebClient` không có API để sửa trực tiếp cấu hình đã xây. `mutate()` tạo builder mới mang cấu hình hiện tại; gọi `build()` tạo **client mới**, không đổi client cũ:

```java
WebClient original = WebClient.builder()
        .baseUrl("https://inventory.example")
        .filter(requestOnly)
        .build();

WebClient extended = original.mutate()
        .filter(responseOnly)
        .build();
```

Trong ví dụ này, `original` có `requestOnly`; `extended` có cả `requestOnly` và `responseOnly`. Biến `requestOnly` và `responseOnly` là các filter đã định nghĩa ở phần trước. Khi muốn đưa filter lên đầu hoặc loại bỏ filter cũ, có thể dùng `mutate().filters(list -> ...)`. Cần hiểu thứ tự và nhận diện filter trước khi sửa danh sách; thêm một filter mỗi lần một phương thức gọi `mutate()` có thể gây lặp filter không mong muốn.

## 5. Đưa dữ liệu riêng từng request vào filter bằng attributes

`ClientRequest` có một map attributes dùng **nội bộ cho chuỗi filter**, không được tự động gửi qua mạng như HTTP headers. Một request có thể gắn attribute để đổi hành vi filter:

```java
ExchangeFilterFunction selectiveAudit = (request, next) -> {
    boolean skip = Boolean.TRUE.equals(
            request.attribute("skipAudit").orElse(false));

    if (!skip) {
        System.out.println("Audit " + request.method() + " "
                + request.url().getPath());
    }
    return next.exchange(request);
};

WebClient client = WebClient.builder()
        .filter(selectiveAudit)
        .build();

Mono<String> value = client.get()
        .uri("https://service.example/resource")
        .attribute("skipAudit", true)
        .retrieve()
        .bodyToMono(String.class);
```

Trong ví dụ, `skipAudit` chỉ là cờ của client/filter, không phải header HTTP. Muốn truyền dữ liệu cho server, phải chủ động chuyển thành header/body/query parameter theo hợp đồng API.

Với yêu cầu cần dữ liệu theo từng subscriber trong chuỗi phản ứng, phân biệt attributes với Reactor `Context`: attribute được gắn trên một `ClientRequest`, còn `Context` được truyền theo luồng đăng ký. Tránh phụ thuộc tùy tiện vào `ThreadLocal` trong xử lý reactive vì một luồng có thể chuyển thread. `defaultRequest(...)` có thể đặt attribute mặc định, nhưng chính sách lấy thông tin định danh/correlation cần được thiết kế nhất quán.

## 6. Ví dụ chạy độc lập với mạng: xác minh filter và header

Đoạn dưới đây có phương thức `main` hoàn chỉnh; nó dùng một `ExchangeFunction` giả để tạo response nên **không thực hiện HTTP thật**. Cần Java 17+ và các thư viện Spring WebFlux + Project Reactor trên classpath (ví dụ dự án Spring Boot đã khai báo dependency `org.springframework.boot:spring-boot-starter-webflux` và quản lý phiên bản tương thích).

**Tệp `FilterDemo.java`:**

```java
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class FilterDemo {

    public static void main(String[] args) {
        ExchangeFunction fakeTransport = request -> {
            System.out.println(
                    "transport: " + request.headers().getFirst("X-Client-Id"));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .body("pong")
                    .build());
        };

        ExchangeFilterFunction audit = (request, next) -> {
            System.out.println(
                    "before: " + request.method() + " " + request.url().getPath());
            return next.exchange(request)
                    .doOnNext(response ->
                            System.out.println(
                                    "after: " + response.statusCode().value()));
        };

        WebClient client = WebClient.builder()
                .baseUrl("https://example.invalid")
                .defaultHeader("X-Client-Id", "learning-module")
                .exchangeFunction(fakeTransport)
                .filter(audit)
                .build();

        String body = client.get()
                .uri("/ping")
                .retrieve()
                .bodyToMono(String.class)
                .block(); // Chỉ dùng tại ranh giới đồng bộ của demo.

        System.out.println("body: " + body);
    }
}
```

**Stdout dự kiến, chính xác theo ví dụ (không tính thông báo môi trường/build tool):**

```text
before: GET /ping
transport: learning-module
after: 200
body: pong
```

Lý do:

1. Lúc `block()` đăng ký luồng, filter `audit` chạy và in dòng `before`.
2. `next.exchange(request)` gọi `fakeTransport`. Transport nhận header mặc định `X-Client-Id` và trả `ClientResponse` 200 với body `pong`.
3. `doOnNext` quan sát response rồi in dòng `after`.
4. `retrieve().bodyToMono(String.class)` giải mã body thành chuỗi, cuối cùng `main` in `body`.

Với dự án Spring Boot/Gradle, thêm `implementation 'org.springframework.boot:spring-boot-starter-webflux'` vào dependencies nếu chưa có; đặt `FilterDemo.java` trong source set và chạy `FilterDemo.main` bằng IDE hoặc Java launcher có classpath thư viện từ Gradle. Lệnh `javac FilterDemo.java` đơn lẻ **không đủ** vì cần các JAR Spring/Reactor. `block()` ở đây là ngoại lệ dành cho `main` kiểm chứng; trong luồng xử lý WebFlux server, nên trả `Mono`/`Flux` thay vì chặn event loop.

### 6.1 Thử thay đổi để tự kiểm tra

- Chuyển `.filter(audit)` thành `.filter(audit).filter(secondFilter)` với `secondFilter` in `B-before` trước `next.exchange` và `B-after` qua `doOnNext`. Quan sát thứ tự `A-before → B-before → transport → B-after → A-after`.
- Thêm `.header("X-Client-Id", "override")` ngay trước `.retrieve()`. Transport sẽ thấy `override` vì header của request ưu tiên hơn `defaultHeader`.
- Bỏ `.block()` và không subscribe; phần trao đổi không được thực hiện.
- Trong một client thực, đặt `attribute("skipAudit", true)` và quan sát filter dùng attribute quyết định có log metadata hay không.

## 7. Những nhầm lẫn và rủi ro phổ biến

| Sai lầm | Hệ quả | Cách xử lý |
| --- | --- | --- |
| Nhầm `ExchangeFilterFunction` với server-side `WebFilter` | Can thiệp sai phía của HTTP. | `ExchangeFilterFunction` dành cho request **đi ra** của `WebClient`; `WebFilter` nằm trong pipeline xử lý server WebFlux. |
| Gọi `next.exchange(request)` hai lần hoặc lồng retry tùy tiện | Có thể gửi cùng yêu cầu nhiều lần, nguy hiểm với POST/PUT không idempotent. | Chủ động định nghĩa retry theo HTTP method, mã lỗi, giới hạn và tính idempotent; không gửi lặp vô điều kiện. |
| `doOnError` được dùng để đếm mọi HTTP 4xx/5xx | Bỏ sót status khi `exchange` trả `ClientResponse` bình thường. | Quan sát `response.statusCode()`; xử lý trạng thái nghiệp vụ ở `retrieve().onStatus(...)` khi hợp lý. |
| Đọc body trong filter rồi trả lại nguyên response | Downstream không còn body như kỳ vọng hoặc rò tài nguyên. | Không tiêu thụ body chỉ để log; nếu xử lý và bỏ response, giải phóng body đúng cách. |
| In toàn bộ headers/body | Lộ token, cookie, PII, nội dung thanh toán. | Chỉ ghi method, mẫu đường dẫn, status, thời gian và ID đã kiểm soát. |
| Dùng `ThreadLocal` để lấy token trong luồng reactive | Mất/sai ngữ cảnh khi đổi thread. | Truyền dữ liệu minh bạch qua request attributes hoặc Reactor Context, tùy bài toán. |
| Dùng `block()` trong filter hay controller phản ứng | Chặn event loop, tăng độ trễ và có thể gây lỗi. | Kết hợp bất đồng bộ bằng `flatMap`, `Mono.defer` hoặc publisher phù hợp. |
| Chỉnh lại builder chia sẻ sau khi dùng | Cấu hình giữa các client không nhất quán. | Hoàn tất cấu hình trước `build()`; `clone()`/`mutate()` khi tạo biến thể. |
| Dùng filter để viết toàn bộ logic nghiệp vụ | Khó kiểm thử và khó phân biệt lỗi từng endpoint. | Filter giải quyết vấn đề chung; xử lý response/DTO/trạng thái nghiệp vụ tại tầng client service. |
| Coi filter là cơ chế timeout/connection pool | Không xử lý đúng lỗi cấu hình transport. | Cấu hình `clientConnector`, timeout, pooling và quản lý vòng đời tài nguyên ở lớp phù hợp. |

## 8. Gợi ý tổ chức trong ứng dụng

Trong ứng dụng có nhiều HTTP client, nên tách phần cấu hình toàn cục (quan sát, connector, policy log an toàn) khỏi client của từng dịch vụ (base URL, xác thực, headers riêng). Định nghĩa filter tái sử dụng thành hàm/bean nếu có giá trị kiểm thử độc lập; có thể bổ sung bộ lọc theo thứ tự bằng `filter(...)` và kiểm soát danh sách bằng `filters(...)`. Đặt tham số cấu hình như endpoint, thời gian chờ, tên ứng dụng trong nguồn cấu hình môi trường phù hợp.

Một cách rà soát thiết kế cho mỗi filter:

1. **Nó sửa request, chỉ quan sát response hay kiểm soát chuỗi trao đổi?** Chọn API đơn giản nhất đủ nhiệm vụ.
2. **Nó có cần dữ liệu riêng từng request không?** Dùng header khi server cần biết, attribute khi chỉ client/filter cần, Reactor Context khi cần ngữ cảnh theo subscriber.
3. **Nó có thể tiêu thụ hoặc bỏ response không?** Nếu có, định nghĩa rõ việc đọc/giải phóng body và chính sách lỗi.
4. **Nó có an toàn khi chạy đồng thời và subscribe nhiều lần không?** Tránh state mutable dùng chung thiếu đồng bộ và tác dụng phụ không idempotent.
5. **Có kiểm thử được mà không gọi HTTP thật không?** Dùng `ExchangeFunction` giả như ví dụ, hoặc mock HTTP server khi muốn thử mã hóa, header thật và kết nối.

## 9. Tổng kết kiến thức

- `WebClient.Builder` giữ cấu hình chung; `ExchangeFilterFunction` can thiệp vào một lần trao đổi HTTP qua `ClientRequest → ExchangeFunction → ClientResponse`.
- Filter được chạy khi publisher được subscribe; thứ tự filter quyết định lớp xử lý request và response.
- `ClientRequest.from(request)` tạo request mới khi sửa header/attribute; `next.exchange(...)` chuyển tiếp trao đổi.
- `defaultHeader` đưa giá trị mặc định, `defaultRequest` tùy biến mỗi request, `filter` xử lý cắt ngang; `WebClient.mutate()` tạo client biến thể mà không sửa bản gốc.
- Khi xử lý response trong filter, luôn bảo toàn hoặc giải phóng body đúng cách; không log thông tin nhạy cảm và không chặn luồng phản ứng.

**Tài liệu tham khảo chính thức (Spring Framework):**

- [WebClient — Filters](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-filter.html)
- [WebClient — Configuration](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-builder.html)
- [WebClient — Attributes](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-attributes.html)
- [Java API — ExchangeFilterFunction](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/reactive/function/client/ExchangeFilterFunction.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-client-006">Timeout, connection pool, TLS và giới hạn bộ nhớ codec</a>

<details>
<summary>Xem chi tiết</summary>

# WebClient: Timeout, Connection Pool, TLS và giới hạn bộ nhớ codec

## 1. Bức tranh tổng thể

`WebClient` của Spring WebFlux là HTTP client bất đồng bộ, non-blocking. Khi sử dụng **Reactor Netty** làm HTTP connector (mặc định phổ biến khi có `spring-boot-starter-webflux`), một request chịu tác động từ nhiều tầng:

```text
WebClient (Spring)
    │  mã hóa request / giải mã response bằng HTTP message codecs
    ▼
ReactorClientHttpConnector
    ▼
Reactor Netty HttpClient
    ├── ConnectionProvider: kết nối tái sử dụng, số lượng tối đa, hàng đợi chờ
    ├── DNS, TCP connect, proxy (nếu có)
    ├── TLS handshake + xác thực chứng chỉ/hostname (HTTPS)
    └── Gửi HTTP request → chờ/đọc HTTP response
    ▼
Spring codecs: đọc/chuyển body → Mono<T> / Flux<T>
```

Bốn nhóm cấu hình trong bài bảo vệ **bốn loại tài nguyên/trạng thái khác nhau**:

- **Timeout:** tránh request treo khi chờ kết nối, TLS, byte phản hồi hoặc vượt hạn tổng thể.
- **Connection pool:** giới hạn số kết nối và số request đợi, giảm chi phí thiết lập TCP/TLS nhiều lần.
- **TLS:** mã hóa đường truyền, xác thực server bằng certificate chain/hostname; khi cần có thể thêm chứng chỉ client cho mTLS.
- **`maxInMemorySize` của codec:** hạn chế lượng dữ liệu được gom trong bộ nhớ trong lúc decode; tránh tiêu tốn bộ nhớ vô hạn vì response lớn.

Các thông số mẫu dưới đây là **giá trị minh họa để học**. Chọn cấu hình production từ SLO, đặc điểm upstream, kích thước payload và kết quả load test, không lấy con số mẫu làm mặc định cho mọi hệ thống.

## 2. Các loại timeout: hiểu đúng phạm vi trước khi cấu hình

| Loại | Bắt đầu/kết thúc, ý nghĩa | Cấu hình tiêu biểu |
| --- | --- | --- |
| Chờ lấy kết nối từ pool | Đợi connection rảnh/có thể tạo connection mới; quá hạn thì acquire thất bại | `ConnectionProvider.pendingAcquireTimeout(Duration)` |
| TCP connect | Đợi bắt tay TCP tới địa chỉ remote; không bao gồm toàn bộ HTTP response | `HttpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, ms)` |
| TLS handshake | Đợi bắt tay TLS khi dùng HTTPS; độc lập với timeout kết nối TCP | `HttpClient.secure(ssl -> ssl.handshakeTimeout(...))` |
| Đọc HTTP response | Giới hạn khoảng lặng **giữa các lần đọc dữ liệu từ network** trong một response, không phải deadline cho toàn bộ giao dịch | `HttpClient.responseTimeout(Duration)` |
| Read/Write I/O tùy chọn | Giám sát khoảng thời gian không đọc/ghi theo Netty handler; phạm vi có thể khác `responseTimeout` | `ReadTimeoutHandler` / `WriteTimeoutHandler` |
| Tổng pipeline reactive | Một `Mono`/`Flux` không phát tín hiệu tiếp theo đúng hạn thì bị hủy, phụ thuộc chỗ đặt operator | `mono.timeout(Duration)`, hoặc một chiến lược deadline xuyên nhiều bước |

**Quan trọng:** `responseTimeout(Duration)` trong Reactor Netty mô tả khoảng thời gian tối đa giữa các lần *network read* khi đọc response. Một stream liên tục nhận chunk nhỏ có thể chạy lâu hơn giá trị này mà không timeout. Nếu nghiệp vụ yêu cầu “toàn bộ lời gọi hoàn thành trong 3 giây”, cần timeout tổng trên publisher hoàn tất toàn bộ request (và cân nhắc nhiều lần retry cùng chia sẻ một ngân sách thời gian). `CONNECT_TIMEOUT_MILLIS` không giới hạn thời gian DNS, pool acquisition hoặc đọc response; tùy môi trường, DNS/proxy cũng có timeout riêng.

Ví dụ tạo `HttpClient` dùng chung:

```java
import java.time.Duration;
import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

HttpClient httpClient = HttpClient.create()
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)
    .responseTimeout(Duration.ofSeconds(5));
```

Sau đó gắn qua Spring:

```java
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

WebClient webClient = WebClient.builder()
    .baseUrl("https://api.example.com")
    .clientConnector(new ReactorClientHttpConnector(httpClient))
    .build();

Mono<String> result = webClient.get()
    .uri("/health")
    .retrieve()
    .bodyToMono(String.class)
    .timeout(Duration.ofSeconds(8)); // Deadline cho publisher này
```

Đây là đoạn minh họa; thêm import `reactor.core.publisher.Mono` nếu dùng độc lập và thay server bằng endpoint thật. `timeout()` phát lỗi timeout reactive khi hết hạn và hủy subscription, **không thay** cấu hình connect/pool/TLS/read ở tầng transport. Khi cùng đặt nhiều timeout, giới hạn nào đạt trước sẽ quyết định biểu hiện thực tế. Không phân biệt nguyên nhân bằng cách chỉ nhìn tên một exception; cần xem *cause chain* và metrics.

### Per-request timeout và nguy cơ dùng nhầm ReadTimeoutHandler

`HttpClient.responseTimeout(...)` đặt mức mặc định cho các request của `HttpClient`. Reactor Netty cũng hỗ trợ ghi đè ở mức `HttpClientRequest` (trong callback `doOnRequest`/`httpRequest`, khi API/connector cho phép). Điều này phù hợp khi phần lớn endpoint nhanh nhưng một endpoint streaming cần read interval lớn hơn.

Một số ví dụ trên mạng dùng:

```java
HttpClient client = HttpClient.create()
    .doOnConnected(connection -> connection
        .addHandlerLast(new io.netty.handler.timeout.ReadTimeoutHandler(10))
        .addHandlerLast(new io.netty.handler.timeout.WriteTimeoutHandler(10)));
```

Hai handler giám sát tình trạng I/O của **channel** (tham số trên là giây), có thể ảnh hưởng tới kết nối tái sử dụng và luồng streaming. Nếu mục tiêu chỉ là chờ/đọc HTTP response, ưu tiên `responseTimeout` của Reactor Netty; thêm I/O handler khi có yêu cầu riêng và đã hiểu lifecycle của chúng.

## 3. Connection pool: chia sẻ, giới hạn và tái sử dụng

Mỗi lần mở TCP/TLS mới có chi phí thời gian, CPU và socket. Connection pool giữ connection đủ điều kiện sau một response để request khác có thể sử dụng lại (tuân thủ keep-alive và HTTP protocol). Tái sử dụng giúp giảm kết nối mới, nhưng pool không tự làm upstream xử lý nhanh hơn.

Cấu hình mẫu:

```java
import java.time.Duration;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.http.client.HttpClient;

ConnectionProvider provider = ConnectionProvider.builder("partner-api-pool")
    .maxConnections(100)
    .pendingAcquireMaxCount(200)
    .pendingAcquireTimeout(Duration.ofSeconds(2))
    .maxIdleTime(Duration.ofSeconds(20))
    .maxLifeTime(Duration.ofMinutes(5))
    .evictInBackground(Duration.ofSeconds(30))
    .build();

HttpClient httpClient = HttpClient.create(provider)
    .responseTimeout(Duration.ofSeconds(5));
```

Giải thích:

- `maxConnections(100)`: tối đa 100 kết nối hoạt động trong **mỗi pool key** (ví dụ remote authority/proxy tùy cấu hình); không hiểu là đúng 100 kết nối tổng toàn bộ JVM cho mọi host.
- `pendingAcquireMaxCount(200)`: số yêu cầu chờ lấy kết nối tối đa trong queue khi pool đã bận. Đây là hàng đợi admission, không phải 200 kết nối bổ sung.
- `pendingAcquireTimeout(2s)`: sau 2 giây mà chưa lấy được connection, yêu cầu thất bại; timeout này khác TCP connect.
- `maxIdleTime(20s)`: connection rảnh quá lâu đủ điều kiện loại bỏ. Chọn phù hợp, thường không dài hơn idle timeout của server/load balancer.
- `maxLifeTime(5m)`: tuổi thọ tối đa của connection trước khi cần loại khỏi pool. Không đồng nghĩa một request đang chạy sẽ bị ngắt đúng khi chạm mốc.
- `evictInBackground(30s)`: dọn các connection đủ điều kiện loại bỏ theo chu kỳ; nếu không bật, việc kiểm tra còn diễn ra tại các thời điểm acquire/release theo triển khai.

**Pool dùng cho ứng dụng:** thường tạo `WebClient`/`HttpClient`/`ConnectionProvider` dưới dạng bean chia sẻ, tái sử dụng giữa các request thay vì tạo pool mới cho mỗi HTTP call.

```java
@Configuration
class PartnerClientConfig {
    @Bean(destroyMethod = "dispose")
    ConnectionProvider partnerPool() {
        return ConnectionProvider.builder("partner-api-pool")
            .maxConnections(100)
            .pendingAcquireMaxCount(200)
            .pendingAcquireTimeout(Duration.ofSeconds(2))
            .maxIdleTime(Duration.ofSeconds(20))
            .build();
    }

    @Bean
    WebClient partnerWebClient(ConnectionProvider partnerPool) {
        HttpClient http = HttpClient.create(partnerPool)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)
            .responseTimeout(Duration.ofSeconds(5));
        return WebClient.builder()
            .baseUrl("https://api.example.com")
            .clientConnector(new ReactorClientHttpConnector(http))
            .build();
    }
}
```

Đây là cấu hình minh họa, cần imports `org.springframework.context.annotation.*`, `WebClient`, `HttpClient`, `ConnectionProvider`, `ChannelOption`, `ReactorClientHttpConnector`, `Duration`. Khi dùng Spring Boot, có thể inject builder được Boot auto-configure để giữ các customizer của ứng dụng; nhớ gắn connector trước khi `build()`.

**Lifecycle:** chỉ dispose pool **do chính mình tạo và sở hữu** khi dừng ứng dụng; không dispose sau từng request. Spring WebClient với Reactor Netty mặc định dùng tài nguyên toàn cục được chia sẻ; không nên vô tình tắt global resources của thành phần khác. Khi tạo cấu hình resources riêng và đặt ứng dụng vào container có thể restart tại chỗ, quản lý tài nguyên bằng Spring bean/`ReactorResourceFactory` để tránh leak threads/connections.

### Quá tải pool và chẩn đoán

Nếu có 100 request dùng đồng thời mà `maxConnections=10`, chỉ 10 connection có thể đang phục vụ cùng lúc trong pool (giản lược HTTP/1.1); các request còn lại chờ, rồi có thể gặp acquire timeout hoặc queue limit. Tăng pool quá lớn có thể gây áp lực lên upstream, socket, file descriptors hoặc ephemeral ports. Nếu response body không được đọc/hủy đúng cách, connection cũng có thể bị giữ lâu.

Theo dõi ít nhất: số connection active/idle/pending theo pool, acquire latency, acquire timeout, connect/TLS timeout, response latency và error rate; dùng Reactor Netty metrics với Micrometer/Actuator nếu đã tích hợp. Điều chỉnh **cùng** concurrency giới hạn phía caller, rate limit và năng lực upstream. Không bật pending queue vô hạn để che một bottleneck; nó có thể tăng độ trễ và làm người dùng gặp timeout hàng loạt.

## 4. TLS: kết nối HTTPS an toàn và các timeout liên quan

Với URL `https://...`, TLS mang lại mã hóa và xác thực server. Cấu hình bình thường nên dùng trust anchors tin cậy, xác thực chain và hostname; không tắt kiểm tra chứng chỉ để “chạy được”. Đối với endpoint cần mutual TLS (mTLS), client còn phải xuất trình certificate/private key do hệ thống cấp.

Mẫu `HttpClient` cho HTTPS với handshake timeout:

```java
import java.time.Duration;
import reactor.netty.http.Http11SslContextSpec;
import reactor.netty.http.client.HttpClient;

HttpClient httpsHttpClient = HttpClient.create()
    .secure(ssl -> ssl
        .sslContext(Http11SslContextSpec.forClient())
        .handshakeTimeout(Duration.ofSeconds(8)))
    .responseTimeout(Duration.ofSeconds(5));

WebClient httpsWebClient = WebClient.builder()
    .baseUrl("https://api.example.com")
    .clientConnector(new ReactorClientHttpConnector(httpsHttpClient))
    .build();
```

Phần `WebClient` cần imports như ở phần 2. `Http11SslContextSpec` ở đây chủ động cấu hình TLS cho HTTP/1.1; các trường hợp HTTP/2/ALPN dùng cấu hình tương thích protocol tương ứng. Cấu hình TLS chỉ nên dùng cho endpoint HTTPS. Một số connector/version có cấu hình mặc định khác; ví dụ trong bài dựa trên **Spring Framework 6.x + Reactor Netty 1.1.x**.

### Trust store, custom CA, mTLS

- **Public CA:** trong hầu hết tình huống HTTPS chuẩn, ưu tiên trust configuration mặc định do client/JDK/Netty cung cấp, tránh tự quản lý cert không cần thiết.
- **Internal CA/self-signed test CA:** cài CA tin cậy vào trust store phù hợp runtime, hoặc truyền custom trust manager một cách có kiểm soát. Cert phải đúng hostname/SAN của URL. Không nhầm lỗi “unknown CA” với timeout.
- **mTLS:** cấu hình thêm client certificate và private key hợp lệ bằng SSL context, đồng thời vẫn kiểm tra server certificate. Bảo vệ secret/password/key; tránh đưa private key vào mã nguồn, log hoặc ảnh Docker công khai.
- **TLS handshake timeout:** chỉ giới hạn giai đoạn handshake, không thay connect timeout hay response timeout. Nếu server từ chối TLS version/cipher hoặc cert sai, tăng timeout thường không giải quyết được.
- **Không dùng** `InsecureTrustManagerFactory.INSTANCE` hoặc tắt hostname verification trong môi trường thật; cách này biến HTTPS thành đường truyền dễ bị giả mạo đích.

Khi gặp `SSLHandshakeException`/`SSLException`, kiểm tra nguyên nhân là expired/unknown CA, thiếu intermediate certificate, SAN/hostname không khớp, protocol/cipher incompatibility, server yêu cầu client certificate, hoặc thời hạn handshake. Nên bật debug TLS có kiểm soát trong môi trường kiểm thử; không bật wiretap/body logging chứa secrets ở production.

## 5. Giới hạn bộ nhớ codec: `maxInMemorySize` là gì?

`WebClient` dùng HTTP message codecs để chuyển JSON, text, XML… giữa byte stream và object. Một số decoder cần gom dữ liệu vào bộ nhớ để xử lý một value/segment. Mặc định theo tài liệu Spring, giới hạn thường là **256 KiB (262.144 bytes)**. Khi vượt ngưỡng liên quan, có thể gặp:

```text
org.springframework.core.io.buffer.DataBufferLimitException:
Exceeded limit on max bytes to buffer
```

Tăng giới hạn cho **WebClient cụ thể**:

```java
WebClient client = WebClient.builder()
    .codecs(configurer ->
        configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
    .build();
```

Con số trên tương ứng **2 MiB**, minh họa cho endpoint trả về object JSON cần gom hơn mức mặc định. Đừng tăng tất cả client lên hàng trăm MiB khi chỉ một endpoint có payload lớn; giới hạn cao nhân với số request song song có thể gây áp lực heap/off-heap và GC.

**Điều dễ nhầm:** `maxInMemorySize` là giới hạn buffering của **decoder/message reader áp dụng**, không phải quota dung lượng toàn ứng dụng, không giới hạn tổng HTTP bytes truyền qua mạng và không phải biện pháp phòng mọi dạng memory exhaustion. Với stream NDJSON/SSE được decode từng item, giới hạn thường áp dụng trên phần cần đệm cho *một item/frame*; `collectList()` vẫn có thể giữ rất nhiều item trong RAM. Với `bodyToMono(String.class)` hoặc JSON object lớn, thường cần gom nội dung và chạm giới hạn.

Nếu nhận response lớn:

1. Kiểm tra **định dạng response** và schema trước: JSON object khổng lồ có thực sự cần tải trọn? Có thể đổi API sang pagination, NDJSON hoặc streaming.
2. Đặt giới hạn theo yêu cầu nghiệp vụ và kích thước payload thực tế, gồm cả response lỗi do `onStatus` đọc thành String; đừng chỉ đổi max size để “hết lỗi”.
3. Khi tải file lớn, cân nhắc truyền `Flux<DataBuffer>` vào file sink thích hợp (ví dụ `DataBufferUtils.write`), tránh `bodyToMono(byte[].class)` hoặc `collectList` mọi buffer. Cẩn thận ownership/release nếu xử lý `DataBuffer` thủ công; decoder framework thường quản lý việc này.
4. Dùng pagination/backpressure/concurrency bound để giới hạn lượng đối tượng tồn tại cùng lúc. Codec limit không thay thế size cap tại reverse proxy/gateway hoặc giới hạn tổng request/response theo hợp đồng API.

Cấu hình ở **client** không tự thay đổi codec limit của **server**. Spring MVC khác Spring WebFlux ở nhiều tầng body parsing; cần xác định chính xác phía phát `DataBufferLimitException`.

## 6. Ví dụ Java 17+ chạy tại localhost: pool, timeout và codec limit

Ví dụ tạo Reactor Netty HTTP server ngay trong `main`, nên **không cần internet hoặc upstream ngoài** khi chạy. Client được cấu hình pool, TCP connect timeout, response read timeout và giới hạn codec 1 KiB. Endpoint `/ok` trả ngắn; endpoint `/large` trả 4 KiB text và được mong đợi gây `DataBufferLimitException` khi decode thành `String`.

Cấu trúc:

```text
webclient-resources-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/WebClientResourcesDemo.java
```

**`settings.gradle`**

```groovy
rootProject.name = 'webclient-resources-demo'
```

**`build.gradle`** (Java 17+, Gradle 8.x)

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux:3.3.13'
}

application {
    mainClass = 'demo.WebClientResourcesDemo'
}
```

**`src/main/java/demo/WebClientResourcesDemo.java`**

```java
package demo;

import java.time.Duration;

import io.netty.channel.ChannelOption;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

public class WebClientResourcesDemo {
    public static void main(String[] args) {
        DisposableServer server = HttpServer.create()
            .host("127.0.0.1")
            .port(0) // Hệ điều hành chọn port còn trống
            .route(routes -> routes
                .get("/ok", (request, response) ->
                    response.header("Content-Type", "text/plain; charset=UTF-8")
                        .sendString(Mono.just("OK")))
                .get("/large", (request, response) ->
                    response.header("Content-Type", "text/plain; charset=UTF-8")
                        .sendString(Mono.just("x".repeat(4096)))))
            .bindNow();

        ConnectionProvider pool = ConnectionProvider.builder("lesson-pool")
            .maxConnections(4)
            .pendingAcquireMaxCount(8)
            .pendingAcquireTimeout(Duration.ofSeconds(2))
            .maxIdleTime(Duration.ofSeconds(15))
            .maxLifeTime(Duration.ofMinutes(1))
            .build();

        try {
            HttpClient http = HttpClient.create(pool)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1_000)
                .responseTimeout(Duration.ofSeconds(2));

            WebClient client = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + server.port())
                .clientConnector(new ReactorClientHttpConnector(http))
                .codecs(config -> config.defaultCodecs().maxInMemorySize(1024))
                .build();

            String result = client.get().uri("/ok")
                .retrieve()
                .bodyToMono(String.class)
                .block();
            System.out.println("ok: " + result);

            try {
                client.get().uri("/large")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
                System.out.println("large: unexpected success");
            } catch (RuntimeException error) {
                if (!hasCause(error, DataBufferLimitException.class)) {
                    throw error;
                }
                System.out.println("large: DataBufferLimitException");
            }
        } finally {
            pool.disposeLater().block();
            server.disposeNow();
        }
    }

    private static boolean hasCause(Throwable error, Class<?> expectedType) {
        for (Throwable current = error; current != null;
             current = current.getCause()) {
            if (expectedType.isInstance(current)) {
                return true;
            }
        }
        return false;
    }
}
```

Trong thư mục dự án, chạy bằng Gradle cài sẵn hoặc Gradle Wrapper nếu đã tạo:

```bash
gradle run --quiet
```

**Các dòng do chương trình in ở môi trường bình thường:**

```text
ok: OK
large: DataBufferLimitException
```

Có thể xuất hiện thêm log từ thư viện tùy cấu hình logger. `port(0)` để tránh xung đột cổng. `disposeNow()` dừng server; `disposeLater().block()` giải phóng pool do ví dụ tự tạo. `block()` chỉ có trong CLI `main` để quan sát kết quả; trong WebFlux request handler, trả `Mono`/`Flux` trực tiếp.

Ví dụ minh họa cách thiết lập timeout; vì localhost trả lời nhanh, **nó không cố gây TCP connect timeout hay response timeout**. Để kiểm chứng timeout độc lập, dùng upstream test có thể điều khiển độ trễ, đo thời gian và phân biệt loại lỗi. Với endpoint `/slow` phát body sau thời gian chờ lớn hơn `responseTimeout`, có thể kiểm tra response timeout trong integration test mà không phụ thuộc internet. TLS không được bật trên server `http://127.0.0.1` này; cấu hình HTTPS đã trình bày ở phần 4.

## 7. Chọn giới hạn theo tải và nhu cầu nghiệp vụ

Giả sử có 200 request đồng thời tới một dịch vụ ngoài, mỗi request tối đa 2 MiB response body:

- Nếu pool chỉ có 20 kết nối, phần còn lại có thể đợi. `pendingAcquireTimeout` cần đồng bộ với deadline toàn request; nếu deadline chỉ 2 giây mà acquisition cho phép 10 giây, yêu cầu có thể bị hủy trước khi tới giai đoạn HTTP.
- Nếu decoder có thể giữ 2 MiB cho mỗi response và nhiều response cùng giải mã, mức sử dụng RAM có thể tăng theo concurrency. Một con số đơn giản để nhắc thiết kế: 50 request đang buffer tối đa 2 MiB có thể tạo *khoảng* 100 MiB buffer theo trường hợp xấu; thực tế còn overhead object, networking và các buffer khác. Đây **không** phải ước lượng RAM toàn JVM.
- Nếu client stream nội dung liên tục, `responseTimeout=5s` là read idle timeout; thời gian hoàn thành có thể dài hơn 5 giây. Hạn tổng thể nên kiểm soát bằng publisher timeout/deadline nghiệp vụ.
- Tăng pool gấp 10 không làm upstream mạnh hơn. Cần đo độ trễ, throughput, tỉ lệ 429/503 và kiểm soát request concurrency/retry.

Một bộ giá trị thử nghiệm chỉ có nghĩa khi ghi rõ traffic profile và upstream SLO. Ví dụ cho API nội bộ có response thường dưới 500 ms, có thể **thử** connect 2 s, acquire 1 s, read 3 s, total 5 s và codec 1 MiB, sau đó load test để điều chỉnh; không dùng làm cấu hình khuyến nghị cho streaming, tải file hay liên vùng có latency cao.

## 8. Lỗi phổ biến và chẩn đoán theo tín hiệu

| Hiện tượng | Điều cần kiểm tra trước | Cách xử lý phù hợp |
| --- | --- | --- |
| Pool acquire timeout | Active/pending connections, queue length, response latency, số client/pool bị tạo | Giảm concurrency, tái sử dụng client, cân chỉnh pool, xử lý upstream chậm |
| TCP connect timeout | DNS, route, firewall, địa chỉ/port, proxy, sức tải server | Kiểm tra network path, đặt timeout đúng, retry có giới hạn nếu an toàn |
| TLS handshake error/timeout | CA, SAN hostname, cert hết hạn, mTLS, TLS versions, latency | Sửa trust/cert/protocol; chỉ tăng handshake timeout khi thực sự chậm |
| `ReadTimeoutException` hoặc lỗi response read timeout | Server có ngừng gửi byte? Streaming có heartbeat? Proxy buffering? | Điều chỉnh read timeout và heartbeat, điều tra upstream |
| `TimeoutException` từ Reactor `timeout()` | Pipeline nào đang chờ? `timeout` đặt trước hay sau retry? | Thiết kế deadline tổng, log stage và correlation ID |
| `DataBufferLimitException` | Codec nào decode, body một object lớn hay stream nhiều item, kích thước payload | Giảm payload, phân trang/stream, tăng `maxInMemorySize` có mục tiêu |
| OutOfMemoryError dù tăng codec limit | Nhiều request song song, `collectList`, byte[] lớn, off-heap Netty, memory leak | Giới hạn concurrency/tổng byte, streaming, profiling heap/direct memory |
| Lỗi ngẫu nhiên với keep-alive | Pool idle lâu hơn server/LB idle timeout, stale sockets, network reset | Cấu hình max idle, eviction, kiểm tra connection reuse |

Tối thiểu trong log/trace nên có endpoint logic (tránh lộ thông tin nhạy cảm), correlation ID, thời gian chờ pool/connect/response, loại lỗi, HTTP status khi có response, và kích thước/kiểu body nếu có số đo an toàn. Không ghi credentials, access token, dữ liệu cá nhân hoặc toàn bộ TLS payload.

**Các anti-pattern cần tránh:**

1. Dùng `Mono.timeout` rồi cho rằng đã cấu hình TCP connect/pool timeout chuẩn ở transport.
2. Tăng `maxConnections` và `pendingAcquireMaxCount` vô hạn để chữa latency; có thể làm quá tải upstream.
3. Khởi tạo `ConnectionProvider` mới mỗi lần gọi HTTP, làm mất reuse và rò rỉ tài nguyên.
4. Dùng `block()` trên Netty event loop trong WebFlux; pipeline non-blocking phải dùng operator `map`/`flatMap` và trả publisher.
5. Coi read timeout là thời gian tối đa download toàn file; traffic streaming cần deadline/idle policy riêng.
6. Tắt certificate validation để qua lỗi TLS; đây là lỗ hổng bảo mật, không phải xử lý timeout.
7. Coi limit codec là giới hạn tổng response/download hoặc tổng RAM; vẫn có các buffer/collection ngoài phạm vi này.
8. `onErrorResume(error -> Mono.empty())` trên tất cả lỗi khiến monitoring không phân biệt timeout, certificate failure, HTTP 500 hay payload vượt limit.
9. Retry mặc định tất cả thao tác POST không idempotent; cần giới hạn retry, backoff, idempotency key khi thích hợp và deadline chung.
10. Dùng số timeout rất ngắn trong môi trường có cold start, TLS handshake, DNS hoặc kết nối xuyên vùng mà chưa đo thực tế.

## 9. Câu hỏi tự kiểm tra

1. **Pool max 10, 30 request đến cùng lúc:** 20 request còn lại có nhất thiết là TCP connect timeout? → Không; có thể đang chờ connection trong pool, chịu giới hạn `pendingAcquireMaxCount`/`pendingAcquireTimeout`, không đi vào TCP connect.
2. **Một server trả về một chunk mỗi 2 giây trong 10 phút, `responseTimeout=5s`:** Có timeout vì tổng 10 phút? → Không nhất thiết; read interval đều dưới 5 giây. Muốn hạn tổng giao dịch cần deadline khác.
3. **HTTPS báo “unable to find valid certification path”:** Nên tăng handshake timeout? → Không; kiểm tra CA chain/trust store và hostname/khả năng mTLS trước.
4. **`bodyToMono(MyHugeDto.class)` gặp `DataBufferLimitException` nhưng `bodyToFlux(DataBuffer.class)` đọc được:** Có nghĩa RAM luôn an toàn? → Không; thao tác gom các buffer hoặc request concurrency cao vẫn có thể tiêu tốn bộ nhớ.
5. **SSE chạy liên tục:** Chọn 2 MiB codec limit có bảo vệ khỏi 1 triệu sự kiện bị `collectList()` gom lại? → Không; cần xử lý từng item và kiểm soát lưu trữ downstream.
6. **Vì sao cần dispose pool tự tạo khi dừng app?** → Pool quản lý connection/tài nguyên; giải phóng khi đóng lifecycle, không dispose cho từng request.

## 10. Tài liệu tham khảo

- Spring Framework, WebClient configuration (Reactor Netty, timeouts, codecs): https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-builder.html
- Spring Framework, Reactive Core — Limits/streaming/DataBuffer: https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html
- Reactor Netty, HTTP Client (connection pool, TLS, timeout): https://projectreactor.io/docs/netty/release/reference/http-client.html
- Reactor Netty Javadoc, `HttpClient.responseTimeout`: https://projectreactor.io/docs/netty/release/api/reactor/netty/http/client/HttpClient.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-client-007">Xử lý lỗi, retry có giới hạn và tránh lặp thao tác có tác dụng phụ</a>

<details>
<summary>Xem chi tiết</summary>

# WebClient: Xử lý lỗi, retry có giới hạn và tránh lặp thao tác có tác dụng phụ

## Mục tiêu học tập

Sau bài này, bạn có thể phân biệt lỗi HTTP với lỗi kết nối khi gọi dịch vụ bằng Spring WebClient; quyết định lỗi nào được thử lại; cấu hình số lần retry, backoff, jitter và timeout có giới hạn; đồng thời tránh tạo đơn hàng, thu tiền hoặc ghi dữ liệu hai lần khi một request được gửi lại. Trọng tâm là **độ an toàn của hành vi nghiệp vụ**, không chỉ là làm cho một HTTP call cuối cùng trả về thành công.

WebClient là HTTP client theo mô hình reactive, trả về Mono hoặc Flux. Pipeline chỉ thực sự chạy khi có subscriber (hoặc khi block trong ứng dụng console). **retryWhen đăng ký lại (resubscribe) upstream khi có tín hiệu lỗi**: nếu upstream chứa thao tác HTTP, request có thể được gửi lại. Vì vậy, retry là một quyết định nghiệp vụ có tác động đến hệ thống bên ngoài.

## 1. Hiểu đúng các nhóm lỗi

Với cách gọi phổ biến **retrieve().bodyToMono(...)**, Spring mặc định biến HTTP 4xx/5xx thành WebClientResponseException, có thông tin status, headers và response body. Lỗi khi chưa nhận được một HTTP response đầy đủ, chẳng hạn từ kết nối, DNS hoặc TLS, thường được biểu diễn qua WebClientRequestException. Lỗi parse JSON, xử lý dữ liệu hoặc lỗi do code ứng dụng ném ra là những nhóm lỗi khác; không nên tự động retry chỉ vì chúng cũng đi theo nhánh onError.

| Trường hợp | Ý nghĩa điển hình | Cách xử lý đề xuất |
| --- | --- | --- |
| 200/201/204 | Thành công; 204 thường không có body | Đọc body đúng kiểu; xử lý Mono.empty nếu phù hợp |
| 400/422 | Dữ liệu request sai hoặc không hợp lệ | Sửa input, trả lỗi nghiệp vụ; thường không retry |
| 401/403 | Thiếu xác thực hoặc không có quyền | Xử lý auth/quyền theo hợp đồng; không retry vô điều kiện |
| 404 | Không tìm thấy tài nguyên | Chuyển thành lỗi nghiệp vụ hoặc giá trị rỗng theo yêu cầu |
| 409/412 | Xung đột phiên bản/trạng thái | Giải quyết xung đột, kiểm tra điều kiện ghi; không retry mù |
| 408/429 | Request timeout tại server hoặc bị giới hạn tốc độ | Có thể retry khi request an toàn; tôn trọng Retry-After nếu server cung cấp |
| 500/502/503/504 | Lỗi phía server/gateway; một số lỗi tạm thời | Chỉ retry những mã đã cho phép và khi thao tác có thể lặp an toàn |
| Mất kết nối, timeout phía client | Không biết chắc server có xử lý xong hay chưa | Đặc biệt nguy hiểm với thao tác ghi; cần hợp đồng chống trùng |

Không đánh đồng “HTTP trả 5xx” với “chắc chắn server chưa ghi gì”. Ví dụ server đã lưu đơn hàng, nhưng lỗi xảy ra lúc tạo response; client nhìn thấy 500 hoặc timeout dù tác dụng phụ đã xảy ra.

## 2. Bắt và chuyển đổi lỗi tại WebClient

**retrieve()** tiện khi chủ yếu cần response body. Nếu không tùy biến, 4xx/5xx được phát thành WebClientResponseException. Dùng **onStatus** khi cần tạo lỗi nghiệp vụ cụ thể; handler nhận ClientResponse và phải trả về Mono chứa Throwable hoặc Mono phát lỗi. Ví dụ sau là **đoạn mã tích hợp vào service**, không phải một chương trình độc lập:

~~~java
Mono<Product> findProduct(String id) {
    return webClient.get()
        .uri("/products/{id}", id)
        .retrieve()
        .onStatus(
            status -> status.value() == 404,
            response -> Mono.just(new ProductNotFoundException(id))
        )
        .bodyToMono(Product.class);
}
~~~

Trong đoạn trên, Product và ProductNotFoundException là kiểu của ứng dụng; webClient là WebClient đã cấu hình baseUrl. Khi status là 404, lỗi được chuyển thành loại riêng để caller xử lý có chủ đích. Không nên chuyển mọi lỗi thành Mono.empty vì sẽ xóa mất khác biệt giữa “không có dữ liệu” và “dịch vụ bị lỗi”.

Nếu cần đọc status/headers và lựa chọn cách giải mã body theo nhiều nhánh, dùng **exchangeToMono** hoặc **exchangeToFlux**; ví dụ:

~~~java
Mono<String> result = webClient.get()
    .uri("/health/details")
    .exchangeToMono(response -> {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(String.class);
        }
        return response.createException()
            .flatMap(Mono::error);
    });
~~~

Ở đây response lỗi được chuyển thành exception mang status/body. Việc tiêu thụ body cần được thực hiện trong hàm xử lý ClientResponse; không giữ ClientResponse để dùng sau khi publisher từ exchangeToMono hoàn tất.

Phân biệt các operator sau:
- **doOnError**: quan sát/ghi log; không nuốt hoặc phục hồi lỗi.
- **onErrorMap**: chuyển đổi lỗi; giữ nguyên nguyên nhân (cause) khi bọc exception.
- **onErrorResume**: chủ động phục hồi bằng publisher khác, chỉ nên dùng khi có fallback hợp lệ về nghiệp vụ.
- **retryWhen**: đăng ký lại upstream, có thể gửi HTTP request thêm lần nữa.
- **onErrorReturn**: thay lỗi bằng một giá trị; nguy hiểm nếu trả dữ liệu giả để che mất sự cố.

Vị trí operator cũng có ý nghĩa: doOnError đặt **trước** retryWhen thấy từng lần thử thất bại; đặt **sau** retryWhen thường chỉ thấy lỗi cuối cùng khi chính sách retry không cứu được request.

## 3. Retry có giới hạn: điều kiện, số lần và khoảng chờ

Một retry policy đủ an toàn phải trả lời bốn câu hỏi: **lỗi nào**, **phương thức/nghiệp vụ nào**, **tối đa bao nhiêu lần**, và **đợi bao lâu**. Không đặt retryWhen(Retry.indefinitely()) cho outbound HTTP trong luồng xử lý request người dùng.

Project Reactor cung cấp **Retry.backoff(maxRetries, minBackoff)**. Ví dụ maxRetries = 2 nghĩa là **một lần gọi đầu tiên + tối đa hai lần thử lại = tối đa ba HTTP request**. Backoff tăng dần giảm áp lực khi dịch vụ đang lỗi; maxBackoff chặn khoảng chờ quá lớn; jitter làm nhiều client không đồng loạt retry đúng cùng thời điểm. Trong ví dụ bên dưới jitter = 0 chỉ để kết quả dễ lặp lại; trong production thường nên dùng jitter khác 0.

Ví dụ logic quyết định lỗi có thể retry cho một request **GET chỉ đọc**:

~~~java
static boolean isTransientHttpFailure(Throwable error) {
    if (error instanceof WebClientResponseException response) {
        int status = response.getStatusCode().value();
        return status == 408 || status == 429 ||
               status == 502 || status == 503 || status == 504;
    }
    return error instanceof WebClientRequestException;
}
~~~

Đây là **chính sách minh họa**, không phải danh sách retry mặc định cho mọi API. Có API trả 500 cho lỗi tạm thời, API khác có 500 sau khi ghi thành công; việc có đưa 500 vào filter phải dựa vào hợp đồng của dịch vụ. Với 429/503 kèm Retry-After, retry backoff cơ bản **không tự động làm đúng theo header này**; cần logic đọc header và điều chỉnh lịch thử lại theo chính sách server nếu API yêu cầu.

Mẫu áp dụng:

~~~java
Retry policy = Retry.backoff(2, Duration.ofMillis(200))
    .maxBackoff(Duration.ofSeconds(2))
    .jitter(0.5)
    .filter(MyClient::isTransientHttpFailure)
    .doBeforeRetry(signal ->
        logger.warn("Retry #{} do {}",
            signal.totalRetries() + 1,
            signal.failure().toString()))
    .onRetryExhaustedThrow((spec, signal) -> signal.failure());

Mono<String> body = webClient.get()
    .uri("/catalog/{id}", id)
    .retrieve()
    .bodyToMono(String.class)
    .retryWhen(policy);
~~~

Đây cũng là đoạn tích hợp: MyClient, logger, webClient và id thuộc lớp ứng dụng. **onRetryExhaustedThrow** ở đây trả lỗi HTTP/cuối cùng thay vì lớp lỗi “retry exhausted”, giúp tầng trên phân loại lỗi gốc. Chỉ log số lần thử, loại lỗi, request/correlation ID và thời gian; tránh log token, mật khẩu, dữ liệu cá nhân hoặc toàn bộ body nhạy cảm. Retry.backoff không giới hạn tổng thời gian nghiệp vụ nếu không kết hợp timeout/budget ở tầng phù hợp.

### Timeout và tổng ngân sách

- **Timeout từng lần thử**: dùng timeout ở HTTP connector (ví dụ Reactor Netty HttpClient.responseTimeout) hoặc một timeout operator được đặt trước retryWhen, có đánh giá rõ về loại lỗi phát ra.
- **Timeout toàn chuỗi**: đặt timeout sau retryWhen để giới hạn tổng thời gian bao gồm các lần thử và backoff.
- **TimeoutException** từ Mono.timeout không tự động khớp filter chỉ nhận WebClientRequestException/WebClientResponseException; muốn retry nó phải quyết định bổ sung một cách tường minh.
- Dù client timeout, request trước đó **có thể vẫn chạy ở server**. Timeout không phải lệnh rollback.
- Cấu hình phải thỏa ngân sách của caller: một API cần phản hồi trong 2 giây không nên có ba lần timeout 2 giây cộng nhiều giây backoff.

## 4. Ví dụ đầy đủ: GET lỗi 503 hai lần rồi thành công

Ví dụ Java 17+ sau khởi động một HTTP server nhỏ trong cùng JVM để chủ động trả 503 cho hai request đầu tiên, rồi trả 200. WebClient gọi GET và dùng retry có giới hạn. Server JDK này chỉ phục vụ demo; không có thao tác ghi. Ví dụ dùng Spring WebFlux và Reactor Netty do dependency spring-boot-starter-webflux cung cấp.

Đặt các tệp trong thư mục:

~~~text
webclient-retry-demo/
├── pom.xml
└── src/main/java/demo/WebClientRetryDemo.java
~~~

Tệp **pom.xml** (bản Spring Boot 3.3.13 được ghim để có thể tái hiện ví dụ; khi làm dự án thật hãy chọn nhánh còn được hỗ trợ):

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.13</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>webclient-retry-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Tệp **src/main/java/demo/WebClientRetryDemo.java**:

~~~java
package demo;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

public final class WebClientRetryDemo {
    private WebClientRetryDemo() {}

    public static void main(String[] args) throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/items/42", exchange -> {
            int attempt = hits.incrementAndGet();
            int status = attempt <= 2 ? 503 : 200;
            String response = status == 503 ? "temporarily unavailable" : "item-42";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                "Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(bytes);
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            WebClient client = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .build();

            Retry boundedRetry = Retry.backoff(2, Duration.ofMillis(100))
                .maxBackoff(Duration.ofMillis(300))
                .jitter(0.0) // Chỉ để demo có thời gian chờ xác định.
                .filter(WebClientRetryDemo::canRetryGet)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());

            String item = client.get()
                .uri("/items/42")
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(boundedRetry)
                .block(Duration.ofSeconds(5)); // Chỉ dùng ở demo console.

            System.out.println("Kết quả: " + item);
            System.out.println("Số request: " + hits.get());
        } finally {
            server.stop(0);
        }
    }

    private static boolean canRetryGet(Throwable error) {
        if (error instanceof WebClientResponseException response) {
            int code = response.getStatusCode().value();
            return code == 429 || code == 502 ||
                   code == 503 || code == 504;
        }
        return error instanceof WebClientRequestException;
    }
}
~~~

Cài JDK 17+ và Maven, sau đó đứng trong thư mục webclient-retry-demo chạy:

~~~bash
mvn -q compile
mvn -q exec:java -Dexec.mainClass=demo.WebClientRetryDemo
~~~

Hai dòng do chương trình chủ động in ra là:

~~~text
Kết quả: item-42
Số request: 3
~~~

Maven hoặc thư viện có thể in thêm log tùy môi trường. Server trả 503 ở lượt 1 và 2, hai lỗi này nằm trong filter nên Reactor resubscribe hai lần; request thứ 3 nhận 200. **Không có lượt thứ 4** vì maxRetries = 2. Đổi điều kiện server thành attempt <= 3 để thử nhánh hết retry: lỗi 503 cuối cùng sẽ đi ra ngoài, code không in hai dòng thành công. Đổi status thành 400 để thấy filter từ chối retry ngay lần đầu.

Trong ứng dụng Spring WebFlux, **không gọi block() trên luồng xử lý reactive/event loop**. Service/controller nên trả Mono/Flux để framework subscribe. block() ở đây chỉ nhằm quan sát kết quả đồng bộ từ hàm main.

## 5. Tránh lặp thao tác có tác dụng phụ

### 5.1. HTTP method và tính idempotent

**Idempotent** có nghĩa là gửi cùng một thao tác nhiều lần có **tác động cuối cùng mong muốn lên trạng thái server tương đương gửi một lần**. Nó không đồng nghĩa “chỉ có một HTTP request”, “không ghi log nhiều lần”, hoặc “response lần nào cũng giống nhau”.

| Method | Tính chất theo ý nghĩa HTTP | Quyết định retry |
| --- | --- | --- |
| GET/HEAD | An toàn (read-only) và idempotent theo hợp đồng HTTP | Thường phù hợp retry lỗi tạm thời nếu hệ thống thực hiện đúng |
| PUT | Idempotent nếu cùng URI và cùng trạng thái đích | Có thể retry khi API thực sự giữ hợp đồng đó |
| DELETE | Idempotent về hiệu ứng xóa; lượt sau có thể trả 404 | Cần coi 404/204 theo hợp đồng để tránh báo lỗi giả |
| POST | Không mặc nhiên idempotent | Không tự động retry thao tác ghi nếu chưa có cơ chế chống trùng |
| PATCH | Không mặc nhiên idempotent | Phụ thuộc ý nghĩa patch và điều kiện cập nhật |

Ngay cả GET cũng không “an toàn” trong một hệ thống viết sai hợp đồng (ví dụ GET lại thu tiền); hãy xác nhận contract thực tế. PUT như “đặt số lượng về 5” khác POST như “tăng số lượng thêm 5”. Cùng một request POST gửi ba lần có thể tạo ba đơn hàng.

### 5.2. Cửa sổ lỗi nguy hiểm

Ví dụ client gửi **POST /orders** tạo đơn:
1. Server nhận POST, lưu đơn #123 và commit transaction.
2. Mạng đứt trước khi response 201 về tới client.
3. WebClient nhìn thấy request exception/timeout, không biết server đã commit.
4. retryWhen gửi lại POST. Nếu server thiếu cơ chế chống trùng, có thể tạo thêm đơn #124.

Việc bắt WebClientRequestException và retry không giải quyết được tính không chắc chắn ở bước 3. **Không thể kết luận “request chưa tới server” chỉ từ lỗi client**.

### 5.3. Idempotency-Key phải được server thực thi

Một cách thường dùng cho POST tạo tài nguyên/thanh toán là sinh **một key duy nhất cho một thao tác nghiệp vụ** và tái sử dụng **chính key đó qua mọi lần retry**. Ví dụ phần client (mã minh họa tích hợp):

~~~java
String operationKey = /* ID ổn định của một yêu cầu tạo đơn */;
Mono<OrderResponse> request = webClient.post()
    .uri("/orders")
    .header("Idempotency-Key", operationKey)
    .bodyValue(command)
    .retrieve()
    .bodyToMono(OrderResponse.class);

// Chỉ cấu hình retry khi server đã cam kết xử lý idempotency key
// và các lỗi được phân loại là tạm thời.
~~~

OrderResponse, command và operationKey là các giá trị thuộc ứng dụng; snippet không phải chương trình độc lập. Không sinh UUID mới **bên trong** Mono.defer hoặc ngay tại từng lần retry; làm thế các lần gửi có key khác nhau, phá hỏng cơ chế chống trùng. Ngược lại, key cũng không nên dùng lại cho **hai thao tác nghiệp vụ độc lập**.

Một phía server thực thi idempotency đúng cần đảm bảo:
- Lưu khóa theo phạm vi phù hợp, thường là tenant/user + endpoint/operation + key, cùng dấu vân tay (fingerprint) của request; cùng key nhưng khác nội dung phải báo xung đột, không âm thầm thực hiện thao tác mới.
- **Kiểm tra/giành quyền xử lý key và ghi kết quả nghiệp vụ một cách atomic** (ví dụ transaction/unique constraint hoặc cơ chế tương đương). Chỉ kiểm tra “key chưa tồn tại” rồi mới insert mà không khóa có thể race giữa hai request đồng thời.
- Nếu cùng key và cùng payload được gửi lại sau khi thao tác hoàn tất, trả về kết quả đã lưu hoặc trạng thái phù hợp theo contract, thay vì chạy tác dụng phụ lần nữa.
- Xác định xử lý request đang chạy, kết quả thất bại, TTL/lưu trữ, quyền truy cập và hồi phục sau crash. TTL hết hạn có thể cho phép thực hiện lại, nên vòng đời key phải khớp yêu cầu nghiệp vụ.
- Với hệ thống phát sự kiện hoặc gọi dịch vụ khác, transaction database một mình không đủ để ngăn gửi message hai lần; cân nhắc transactional outbox, unique operation ID và consumer idempotent.

Idempotency-Key **không tự có hiệu lực chỉ vì client gắn header**; server phải hỗ trợ và tài liệu hóa hợp đồng. Nếu server không hỗ trợ, cách an toàn có thể là **không retry thao tác ghi**; thay vào đó hỏi trạng thái bằng operation ID hoặc dùng API truy vấn kết quả trước khi quyết định gửi tiếp. Với thanh toán, xử lý unknown outcome theo chính sách của nhà cung cấp.

## 6. Retry ở đúng vị trí và kiểm soát tải

**Một publisher có thể được subscribe nhiều lần.** retryWhen sẽ đăng ký lại toàn bộ đoạn upstream bên trái nó, bao gồm các thao tác phát sinh khi subscribe. Tránh đặt thao tác phụ như gửi email, ghi DB hoặc phát event bên trong phần sẽ retry nếu thao tác đó không có khóa chống trùng.

~~~java
// Nguy hiểm nếu savePayment có tác dụng phụ và thiếu chống trùng:
savePayment(command)
    .then(webClient.get().uri("/receipt").retrieve().bodyToMono(String.class))
    .retryWhen(retryPolicy); // Có thể gọi lại savePayment.

// Chỉ retry phần đọc HTTP khi phù hợp:
savePayment(command)
    .then(
        webClient.get().uri("/receipt")
            .retrieve()
            .bodyToMono(String.class)
            .retryWhen(retryPolicy)
    );
~~~

Snippet chỉ diễn giải **phạm vi resubscribe**; savePayment, command, webClient và retryPolicy là thành phần giả định của ứng dụng. Việc tách phạm vi không bảo vệ thao tác savePayment nếu caller bên ngoài retry toàn bộ flow; hệ thống vẫn cần đánh giá idempotency ở mọi tầng.

Trong hệ thống thật, nhiều lớp (HTTP client, service, message consumer, gateway, job) cùng retry có thể nhân số request: ba tầng, mỗi tầng tối đa ba lượt, có thể tạo đến **27 lượt cố gắng** cho một thao tác. Thiết kế retry ở số ít tầng có chủ đích và đặt **retry budget**, giới hạn đồng thời, rate limit/circuit breaker khi cần. Backoff có jitter giúp giảm “retry storm”, nhưng không thay thế giới hạn công suất. Retry chỉ nên áp dụng lỗi **có khả năng hồi phục trong thời gian ngắn**; lỗi input hoặc bug logic cần sửa nguyên nhân.

Về quan sát vận hành, đo số attempt thực tế, tỉ lệ thành công sau retry, mã status, timeout, latency toàn chuỗi, thời gian chờ backoff và số request bị từ chối do budget. Phân biệt “người dùng thực hiện một thao tác” với “client phát ba HTTP request”. Cảnh báo nếu retry làm tải tăng trong khi tỷ lệ thành công không cải thiện.

## 7. Các lỗi triển khai thường gặp

1. **Retry tất cả exception**: WebClientResponseException 400/401/404 và lỗi parse dữ liệu tiếp tục được thử lại dù request không thay đổi. Hãy dùng filter theo status và nhóm lỗi.
2. **Nhầm số lần**: Retry.backoff(3, ...) cho tối đa ba lần retry **sau** lần đầu, tức tối đa bốn attempt, chứ không phải tổng cộng ba.
3. **Retry mọi POST**: response mất không chứng minh server chưa ghi. Cần Idempotency-Key có hợp đồng server, điều kiện ghi, hoặc cơ chế truy vấn trạng thái.
4. **Tạo key mới mỗi attempt**: mỗi retry bị xem là một nghiệp vụ mới. Key phải ổn định theo operation.
5. **Không có timeout/budget**: nhiều lần timeout + backoff vượt SLA của caller, tăng số request đang treo.
6. **Bỏ qua Retry-After**: client ồ ạt gọi lại API đang rate limit; cần điều chỉnh chiến lược theo giao thức dịch vụ.
7. **Nuốt lỗi bằng fallback giả**: onErrorResume trả “thành công rỗng” cho thao tác thanh toán khiến downstream hiểu sai. Fallback phải có ý nghĩa nghiệp vụ rõ ràng.
8. **Đặt retry quá rộng**: retry cả chuỗi có DB write/event emit gây lặp thao tác; thu hẹp vùng được resubscribe.
9. **Lẫn lộn request timeout với rollback**: client dừng chờ nhưng server có thể vẫn đang xử lý.
10. **Dùng block() trong reactive handler**: chặn event-loop, làm giảm thông lượng và có thể gây lỗi runtime; trả Mono/Flux thay vì block.
11. **Chỉ test đường thành công**: thiếu test status 400, 404, 429, 503, request exception, hết retry, key trùng, key khác payload và request đồng thời.

## 8. Quy trình quyết định trước khi bật retry

Hãy lần lượt xác định: (1) đây là GET chỉ đọc hay thao tác có thể đổi trạng thái; (2) nếu server xử lý thành công nhưng response mất, lần gửi thứ hai có an toàn không; (3) có idempotency key/conditional write hay contract server hỗ trợ không; (4) lỗi được phân loại cụ thể và có khả năng phục hồi không; (5) tổng số attempt, backoff, jitter và timeout có nằm trong ngân sách không; (6) có metric/log và phép thử đối với request trùng hay chưa.

**Nguyên tắc cần nhớ:** retry giúp tăng khả năng hoàn thành request trước sự cố tạm thời, nhưng **không tự tạo ra tính exactly-once cho tác dụng phụ**. Với thao tác ghi, độ an toàn phải đến từ giao thức và trạng thái server (idempotency, unique constraint, transaction/outbox/consumer deduplication), sau đó client mới quyết định retry.

## Tài liệu tham khảo

- Spring Framework Reference — WebClient retrieve và onStatus: https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-retrieve.html
- Spring Framework Reference — exchangeToMono/exchangeToFlux: https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-exchange.html
- Project Reactor API — RetryBackoffSpec, filter, jitter, maxBackoff và onRetryExhaustedThrow: https://projectreactor.io/docs/core/release/api/reactor/util/retry/RetryBackoffSpec.html
- HTTP Semantics — safe/idempotent methods và Retry-After: https://www.rfc-editor.org/rfc/rfc9110


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-client-008">Tiêu thụ hoặc giải phóng response body để tái sử dụng kết nối</a>

<details>
<summary>Xem chi tiết</summary>

# WebClient: Tiêu thụ hoặc giải phóng response body để tái sử dụng kết nối

## 1. Vì sao response body quyết định vòng đời kết nối?

Khi dùng Spring `WebClient` để gọi HTTP, việc nhận được status và headers **chưa có nghĩa là đã đọc xong phản hồi**. Response body là một luồng dữ liệu có thể chưa được nhận, giải mã hoặc bỏ đi. Nếu ứng dụng chỉ nhìn status rồi bỏ qua body, một kết nối HTTP/1.1 có thể vẫn còn dữ liệu phản hồi chưa xử lý; không thể an toàn dùng kết nối đó cho request kế tiếp.

Với connector dùng connection pool (ví dụ Reactor Netty), vòng đời thông thường của một phản hồi hữu hạn là:

1. Lấy một kết nối từ pool hoặc mở kết nối mới; gửi request.
2. Nhận response status, headers và luồng body.
3. **Đọc hết body** nếu cần nội dung, hoặc **giải phóng/discard body bằng API thích hợp** nếu không cần.
4. Hoàn tất hoặc hủy response theo quy tắc của HTTP client; nếu giao thức và server cho phép, kết nối có thể được đưa về pool để tái sử dụng.

Phải phân biệt ba khái niệm:

- **Consume:** subscribe và xử lý luồng body đến khi hoàn tất, chẳng hạn `bodyToMono(String.class)` hoặc `bodyToFlux(Item.class)`.
- **Release/discard:** xử lý/giải phóng phần body không cần dùng qua API như `ClientResponse.releaseBody()` hoặc `toBodilessEntity()`. Đây là cách đóng vòng đời phản hồi khi ứng dụng chỉ cần status/headers.
- **Reuse:** kết nối vật lý thực sự được dùng lại. Consume/release đúng là điều kiện quan trọng, **không phải cam kết** reuse: server có thể gửi `Connection: close`, kết nối có thể lỗi/hết hạn, request có thể bị cancel giữa chừng, pool có thể loại bỏ connection; HTTP/2 lại có đặc điểm multiplexing khác HTTP/1.1.

`Mono` và `Flux` là mô tả xử lý bất đồng bộ: thông thường request chưa chạy chỉ vì bạn gọi `retrieve()` hoặc tạo pipeline. Phải có subscription (ví dụ framework WebFlux subscribe khi trả publisher từ handler, hoặc `block()` trong demo ở ngoài event-loop).

## 2. Chọn API đúng để quản lý body

| Nhu cầu | Cách viết khuyến nghị | Điều cần hiểu |
| --- | --- | --- |
| Cần đối tượng hoặc văn bản của body | `retrieve().bodyToMono(T.class)` | Đọc và decode body để tạo giá trị; `Mono` hoàn tất sau quá trình nhận/giải mã theo codec. |
| Cần toàn bộ các phần tử của response dạng luồng hữu hạn | `retrieve().bodyToFlux(T.class)` và tiêu thụ đến hết | Dùng stream đúng mục đích; tránh cắt ngang tùy tiện nếu ưu tiên tái sử dụng HTTP/1.1 connection. |
| Chỉ cần status/headers, bỏ body | `retrieve().toBodilessEntity()` | Trả `ResponseEntity<Void>`; phù hợp khi không cần payload. Nhánh mặc định `retrieve()` coi 4xx/5xx là lỗi. |
| Cần tự phân nhánh theo HTTP status | `exchangeToMono(response -> ...)` hoặc `exchangeToFlux(response -> ...)` | Quyết định xử lý body bên trong callback. Spring kiểm tra và giải phóng body chưa dùng sau khi publisher callback trả về hoàn tất. |
| Trong callback đã có `ClientResponse` và chủ động bỏ body | `response.releaseBody()` | Trả `Mono<Void>`; phải **chain** vào publisher trả về, không chỉ gọi hàm rồi bỏ qua. |
| Cần status/headers và body dạng `Flux` | `retrieve().toEntityFlux(T.class)` | `ResponseEntity` chứa `Flux`: **phải subscribe/tiêu thụ Flux bên trong**, nếu không tài nguyên liên quan không được giải phóng đúng. |

API cũ `exchange()` bị deprecated từ Spring 5.3 do dễ bỏ sót bước consume/release response. Với code hiện đại, ưu tiên `retrieve()` hoặc `exchangeToMono`/`exchangeToFlux`.

Lưu ý: `bodyToMono(Void.class)` có thể dùng khi thực sự **không kỳ vọng body**. Nếu server bất ngờ gửi bytes, dùng `Void` để “bỏ qua” không đồng nghĩa với chủ động đọc hết dữ liệu để tái sử dụng HTTP/1.1 connection; connector có thể đóng kết nối. Khi response **có thể có body nhưng bạn không cần body**, `toBodilessEntity()` hoặc `releaseBody()` diễn đạt ý định rõ hơn.

## 3. Cách viết khi cần và không cần nội dung

### 3.1. Cần dữ liệu: decode body như bình thường

~~~java
WebClient client = WebClient.create("https://api.example.com");

// Mono<String> sẽ đọc/giải mã nội dung khi được subscribe.
Mono<String> payload = client.get()
        .uri("/orders/42")
        .retrieve()
        .bodyToMono(String.class);

// Ở ứng dụng WebFlux, trả payload qua reactive chain cho framework xử lý.
// Tránh gọi subscribe() thủ công trong service chỉ để "kích hoạt" request.
~~~

`retrieve()` mặc định chuyển trạng thái 4xx/5xx thành `WebClientResponseException` khi subscriber yêu cầu đọc response. Nếu cần mã lỗi và body lỗi, có thể dùng các API lỗi được cung cấp, thay vì đọc response hai lần.

### 3.2. Không cần payload: trả về response không có body

~~~java
Mono<ResponseEntity<Void>> response = client.delete()
        .uri("/orders/42")
        .retrieve()
        .toBodilessEntity();
~~~

Dù caller chỉ quan tâm mã HTTP, publisher vẫn phải được subscribe để request và cleanup thực sự diễn ra. Ở Spring WebFlux, nên trả publisher lên controller thay vì gọi `.block()` trên event-loop.

### 3.3. Muốn tự xử lý status: consume hoặc release trong callback

~~~java
Mono<String> result = client.get()
        .uri("/orders/42")
        .exchangeToMono(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(String.class);
            }
            return response.createError();
        });
~~~

Trong ví dụ, nhánh thành công đọc body; nhánh lỗi tạo reactive error từ response. Với `exchangeToMono`/`exchangeToFlux`, nếu callback trả về publisher hoàn tất mà body chưa tiêu thụ, Spring giải phóng phần chưa dùng để tránh giữ tài nguyên. **Không đưa chính `ClientResponse` ra khỏi callback** rồi mới cố đọc body ở downstream: body không còn bảo đảm truy cập được sau khi publisher callback kết thúc.

Nếu chỉ lấy status và không cần body:

~~~java
Mono<Integer> status = client.get()
        .uri("/health")
        .exchangeToMono(response ->
                response.releaseBody()
                        .thenReturn(response.statusCode().value()));
~~~

`releaseBody()` chỉ trả về `Mono<Void>`, vì vậy mẫu sai là gọi `response.releaseBody();` rồi ngay sau đó `return Mono.just(status);`: publisher cleanup vừa tạo chưa được subscribe/chain. `thenReturn(...)` buộc công việc release trở thành một phần của chuỗi kết quả.

Nếu muốn nhánh khác nhau theo lỗi, phải chú ý khác biệt: `retrieve()` tự báo lỗi cho 4xx/5xx; `exchangeToMono()` đưa quyền quyết định cho callback. Chọn rõ policy từng nhánh.

### 3.4. Filter tự chặn response phải giải phóng nó

~~~java
ExchangeFilterFunction rejectBusy = (request, next) ->
        next.exchange(request).flatMap(response -> {
            if (response.statusCode().value() == 429) {
                return response.releaseBody()
                        .then(Mono.error(
                                new IllegalStateException("Upstream too busy")));
            }
            return Mono.just(response);
        });
~~~

Đây là đoạn mã minh họa cho filter: filter **thay response bằng lỗi** nên phải cleanup body trước khi phát lỗi. Ở nhánh `Mono.just(response)`, filter chuyển response cho các tầng phía sau tiếp tục xử lý body; không release sớm vì downstream vẫn cần nó.

## 4. Demo chạy được với Java 17 và Spring WebFlux

Ví dụ dưới tạo HTTP server nội bộ trả body có độ dài xác định. Nó minh họa ba cách khép vòng đời phản hồi, không tuyên bố đo được việc tái sử dụng kết nối.

**Điều kiện:** JDK 17+, một dự án Maven/Gradle có dependency `spring-boot-starter-webflux` (Spring Boot 3.x) hoặc đầy đủ `spring-webflux` cùng HTTP connector, rồi chạy lớp `ResponseBodyLifecycleDemo` từ IDE. Đây là một chương trình Java hoàn chỉnh trong ngữ cảnh project đã khai báo dependency; `javac` trần không đủ classpath cho Spring/Reactor.

Lưu tệp `src/main/java/ResponseBodyLifecycleDemo.java`:

~~~java
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.web.reactive.function.client.WebClient;

public class ResponseBodyLifecycleDemo {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/data", exchange -> {
            byte[] body = "payload-ready".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.start();
        try {
            String baseUrl = "http://127.0.0.1:"
                    + server.getAddress().getPort();
            WebClient client = WebClient.create(baseUrl);

            String body = client.get()
                    .uri("/data")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(5));
            System.out.println("body = " + body);

            Integer bodilessStatus = client.get()
                    .uri("/data")
                    .retrieve()
                    .toBodilessEntity()
                    .map(entity -> entity.getStatusCode().value())
                    .block(Duration.ofSeconds(5));
            System.out.println("bodiless status = " + bodilessStatus);

            Integer releasedStatus = client.get()
                    .uri("/data")
                    .exchangeToMono(response ->
                            response.releaseBody().thenReturn(
                                    response.statusCode().value()))
                    .block(Duration.ofSeconds(5));
            System.out.println("released status = " + releasedStatus);
        } finally {
            server.stop(0);
        }
    }
}
~~~

Output kỳ vọng khi ba request đều thành công:

~~~text
body = payload-ready
bodiless status = 200
released status = 200
~~~

Giải thích:

- Request 1 cần body nên `bodyToMono(String.class)` đọc và decode `payload-ready`.
- Request 2 không cần body nên `toBodilessEntity()` trả status và headers mà không cấp payload cho caller.
- Request 3 dùng `exchangeToMono` để lấy status và chain `releaseBody()`.
- `block(Duration...)` chỉ phục vụ `main` đồng bộ trong demo; không dùng `block()` trong controller/service đang chạy trên Reactor event-loop.
- Server đặt cổng `0` để hệ điều hành chọn cổng trống. Kết quả kiểm tra **cách xử lý response**, chưa chứng minh một TCP socket được reuse; muốn khẳng định reuse phải quan sát pool và connection/channel identifiers.

## 5. Tình huống dễ sai và hậu quả

### Chỉ đọc headers/status rồi bỏ response

Code cũ có thể gọi `exchange()`, kiểm tra status và return một giá trị khác mà không động tới body. Khi đó stream phản hồi chưa được xử lý có thể giữ tài nguyên và gây pool exhaustion dưới tải. Đổi sang `exchangeToMono(...)`, dùng `retrieve().toBodilessEntity()` hoặc chain `releaseBody()` trong nhánh chủ động bỏ payload.

### Trả `Flux` nhưng không ai subscribe

Với `toEntityFlux(...)`, response entity chứa một `Flux` chưa chắc đã được đọc. Nếu chỉ lấy status từ `ResponseEntity` rồi lãng quên Flux, tài nguyên liên quan có thể chưa được giải phóng. Hãy tiêu thụ Flux hoặc chọn `toBodilessEntity()` nếu thực sự không cần nội dung.

### Dừng giữa stream bằng `take(1)`, `next()`, timeout hoặc cancellation

Một chuỗi body bị cancel trước khi đọc hết không giống trường hợp consume thành công đến EOF. Framework/connector cần cleanup tài nguyên; với HTTP/1.1, nó có thể phải hủy/đóng connection thay vì đưa connection còn bytes về pool. **Cancel không đồng nghĩa memory leak**, nhưng cũng không nên kỳ vọng socket được reuse. Với stream vô hạn (SSE), kết nối có thể bị giữ cho đến khi người dùng ngắt và cleanup theo đường hủy.

### Chạm tới `DataBuffer` thấp tầng mà không release

`bodyToFlux(DataBuffer.class)` trao buffer cho code của bạn. Với pooled buffer, nếu giữ hoặc loại bỏ buffer sai cách sẽ có nguy cơ leak bộ nhớ native, **khác** với khái niệm kết nối TCP chưa trở về pool. Khi tự xử lý buffer, hãy đọc xong rồi `DataBufferUtils.release(buffer)` trong `finally`, và xem xét `doOnDiscard(DataBuffer.class, DataBufferUtils::release)` nếu operator có thể loại bỏ buffer do filter, error hoặc cancel. Với codec chuẩn chuyển thẳng sang POJO/String, framework thường quản lý buffer giúp bạn; đừng release lại buffer đã được framework sở hữu/giải phóng.

### Nhầm lỗi 4xx/5xx với body không cần xử lý

`retrieve()` có xử lý lỗi mặc định, còn `exchangeToMono()` yêu cầu bạn tự phân nhánh. Khi dùng `onStatus` hay filter để đọc thông báo lỗi, không được lấy body hai lần; muốn đưa thông tin lỗi vào exception thì tiêu thụ body trong đường xử lý lỗi. Không tạo một publisher release rồi bỏ quên nó.

## 6. Quan sát thực tế khi connection pool có vấn đề

Khi thấy nhiều request chờ acquire connection, timeout bất thường hoặc số kết nối active tăng mà không giảm:

1. Kiểm tra mọi code path: thành công, 4xx/5xx, retry, timeout, cancel, filter, nhánh chỉ đọc headers. Hỏi rõ: body đã được tiêu thụ đến hết hoặc được release theo API chưa?
2. Phân biệt stream dài hợp lệ với connection bị mắc kẹt. SSE và response streaming có thể chiếm connection trong thời gian dài.
3. Đọc metrics của Reactor Netty connection provider (active/idle/pending, nếu đã bật metrics), log thời điểm request bắt đầu/kết thúc và log debug có kiểm soát. Dựa vào **channel/connection id** thực tế nếu cần xác nhận reuse.
4. Dùng một server test HTTP/1.1 giữ kết nối sống để thử tải tuần tự và đồng thời; so sánh phản hồi có body hữu hạn được đọc hết với trường hợp hủy sớm. Không suy ra kết luận chỉ từ ba status 200.
5. Rà soát nguyên nhân khác: pool quá nhỏ, timeout, server `Connection: close`, proxy, lỗi mạng hoặc server chậm. Tăng pool size không chữa được luồng body bị bỏ quên.

**Quy tắc ra quyết định:** cần payload thì decode và tiêu thụ; không cần payload thì dùng `toBodilessEntity()` hoặc chain `releaseBody()`; khi tùy biến status, giữ toàn bộ xử lý body trong callback `exchangeToMono`/`exchangeToFlux`. Hoàn tất vòng đời response giúp pool hoạt động đúng, còn reuse thực tế tùy transport và server.

## 7. Tài liệu gốc

- Spring Framework, WebClient — Exchange: https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-exchange.html
- Spring Framework, WebClient — retrieve(): https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-retrieve.html
- Spring Framework, Data Buffers and Codecs: https://docs.spring.io/spring-framework/reference/core/databuffer-codec.html
- Spring Framework, ClientResponse JavaDoc (releaseBody, toBodilessEntity): https://docs.spring.io/spring-framework/docs/6.2.2/javadoc-api/org/springframework/web/reactive/function/client/support/ClientResponseWrapper.html
- Spring Framework, ResponseSpec JavaDoc (toEntityFlux): https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/web/reactive/function/client/WebClient.ResponseSpec.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-client-009">Dùng WebClient trong MVC; chọn vị trí block và tránh chặn event loop</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng WebClient trong Spring MVC: chọn vị trí block và tránh chặn event loop

## 1. Bối cảnh: WebClient là reactive, Spring MVC là servlet-based

`WebClient` là HTTP client sử dụng API reactive (`Mono`/`Flux`), thực hiện HTTP I/O theo hướng bất đồng bộ và hỗ trợ non-blocking khi connector phù hợp (thường là Reactor Netty). `Spring MVC` chạy trên Servlet stack; controller đồng bộ thường được gọi bởi thread từ pool của Servlet container (chẳng hạn `http-nio-8080-exec-*` trên Tomcat). Servlet stack cho phép controller gọi API blocking, nhưng thread bị giữ trong suốt lúc chờ.

Vì vậy, dùng WebClient trong MVC **hoàn toàn hợp lệ**. Quyết định quan trọng là: endpoint hiện tại cần trả về một giá trị Java đồng bộ hay có thể trả một publisher cho Spring MVC xử lý bất đồng bộ?

- **Đồng bộ:** nhận `Mono<T>` rồi gọi `block()` **một lần ở ranh giới imperative**, ví dụ một phương thức service đồng bộ được controller MVC gọi trên servlet worker. Cách này dễ phối hợp với code đồng bộ sẵn có nhưng dùng một servlet thread cho mỗi request đang đợi upstream.
- **Bất đồng bộ:** controller MVC trả trực tiếp `Mono<T>`/`Mono<ResponseEntity<T>>`. Spring MVC thích ứng publisher thành cơ chế Servlet async (tương tự `DeferredResult`); request không giữ servlet worker liên tục trong lúc WebClient chờ upstream.
- **Streaming:** MVC có thể thích ứng `Flux` sang kiểu ghi response bất đồng bộ với media type thích hợp, nhưng việc **ghi ra HTTP response ở Servlet stack vẫn là blocking** và cần một `AsyncTaskExecutor` phù hợp. Chỉ thêm `WebClient` không biến toàn bộ ứng dụng MVC thành WebFlux server.

Điều cần tránh nhất là gọi `block()` trên Reactor event-loop (`reactor-http-nio-*`) hoặc scheduler non-blocking. Khi đó bạn có thể gặp `IllegalStateException` hoặc làm nghẽn luồng xử lý I/O. **HTTP client non-blocking không tự bảo đảm mọi phần của pipeline đều non-blocking**: đoạn code của bạn ở `map`, `flatMap` hay callback vẫn có thể chạy trên event-loop.

### Sơ đồ hai mô hình

~~~text
A. MVC đồng bộ
client HTTP --> Servlet worker --> service --> WebClient: Mono<T>
                     |                           |
                     |-- block() chờ ------------|--> Reactor Netty event-loop
                     |                                (đọc/ghi socket, không block)
                     <-- giá trị T ----------------|
                     --> ghi response MVC

B. MVC bất đồng bộ
client HTTP --> Servlet worker --> controller trả Mono<T>
                     |                 |
                     |            MVC đăng ký Servlet async
                 worker rảnh           |
                                      WebClient chạy I/O trên event-loop
                                            |
                                         kết quả T
                                            |
                                 MVC dispatch/ghi response
~~~

`block()` ở luồng servlet **không khiến Reactor Netty thực hiện I/O blocking**; nó chỉ khiến chính servlet thread đứng chờ tín hiệu từ publisher. Đây là khác biệt giữa **blocking caller** và **blocking event-loop**.

## 2. Khi nào được block, khi nào nên trả Mono?

| Tình huống | Khuyến nghị | Lý do |
| --- | --- | --- |
| MVC controller/service có hợp đồng đồng bộ `T get(...)`, dùng JDBC/JPA hoặc code imperative | Có thể `block()` tại điểm kết thúc lời gọi từ servlet worker | Hợp đồng synchronous; ranh giới rõ ràng, dễ xử lý lỗi. |
| MVC controller có thể trả `Mono<T>` | Ưu tiên trả publisher trực tiếp | MVC hỗ trợ Servlet async, tiết kiệm servlet threads đang chờ I/O. |
| Controller WebFlux hoặc callback đang chạy trên Reactor event-loop | **Không gọi `block()`** | Chặn event-loop, Reactor có thể báo `IllegalStateException`. |
| Trong `map`/`doOnNext`/`flatMap` của chuỗi WebClient | Không block trực tiếp | Callback có thể chạy trên non-blocking thread; dùng tổ hợp publisher thay vì nested blocking. |
| Gọi đồng thời nhiều upstream HTTP endpoints | Tạo các `Mono`, dùng `zip`/`flatMap` rồi mới block **nếu thật sự cần** | Tránh đợi tuần tự; tối đa một lần block ở cuối ở caller đồng bộ. |
| Chuỗi reactive bắt buộc gọi thư viện blocking như JDBC cũ | Offload **phần blocking đó** bằng `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` | Giữ event-loop thông thoáng; giới hạn công việc chặn qua scheduler. |
| Ứng dụng MVC thuần synchronous không cần reactive/streaming | Cân nhắc `RestClient` | API imperative rõ ràng, không cần tạo pipeline để block. |

Không có yêu cầu bắt buộc phải chọn cách thứ hai cho mọi ứng dụng: với tải vừa, nhiều thao tác đồng bộ khác và độ phức tạp thấp, việc block trên servlet worker có thể là quyết định thực tế. Nhưng khi upstream có latency cao và concurrency lớn, số servlet threads đang chờ có thể là nút thắt; nên đo throughput, active threads, queue và timeout trước khi chọn kiến trúc.

## 3. Cách dùng WebClient ở ranh giới đồng bộ của MVC

Trong service synchronous, giữ publisher chưa subscribe cho đến lúc cuối, sau đó block một lần:

~~~java
@Service
public class CustomerService {
    private final WebClient client;

    public CustomerService(WebClient.Builder builder) {
        this.client = builder.baseUrl("https://api.example.com").build();
    }

    public CustomerDto find(long id) {
        return client.get()
                .uri("/customers/{id}", id)
                .retrieve()
                .bodyToMono(CustomerDto.class)
                .block(Duration.ofSeconds(3));
    }
}
~~~

Đây là **đoạn minh họa tích hợp**, giả sử `CustomerDto` đã được định nghĩa và upstream tồn tại. `block(Duration)` chờ tối đa khoảng thời gian chỉ định; nếu publisher rỗng, kết quả có thể là `null`. Thường cần phân biệt “không có body” với tài nguyên không tồn tại, ví dụ dùng `switchIfEmpty(Mono.error(...))` và chính sách 404 rõ ràng. `retrieve()` mặc định biến HTTP 4xx/5xx thành `WebClientResponseException` khi publisher được subscribe.

Vị trí hợp lý là **một điểm cuối trong service đồng bộ**, được gọi từ servlet worker. Không gọi `block()` trong lambda của `map`/`flatMap` rồi lại gọi tiếp `block()` ở ngoài; đó là nested blocking.

Ví dụ controller MVC đồng bộ gọi service:

~~~java
@RestController
class CustomerController {
    private final CustomerService customers;

    CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping("/customers/{id}")
    CustomerDto get(@PathVariable long id) {
        return customers.find(id);
    }
}
~~~

Khi request đến `/customers/42`, servlet thread đang xử lý controller đứng đợi upstream; khi hoàn tất, MVC serialize `CustomerDto` ra JSON. Đặt timeout bảo vệ đường chờ và xử lý lỗi nhất quán; nếu upstream chậm hơn số servlet threads đáp ứng được, ứng dụng có thể bị giảm throughput dù WebClient dùng kết nối bất đồng bộ.

### Nhiều HTTP call: dựng các Mono trước, chờ một lần

~~~java
Mono<CustomerDto> customer = client.get()
        .uri("/customers/42")
        .retrieve()
        .bodyToMono(CustomerDto.class);

Mono<OrderDto[]> orders = client.get()
        .uri("/customers/42/orders")
        .retrieve()
        .bodyToMono(OrderDto[].class);

CustomerPage result = Mono.zip(
        customer, orders,
        (c, o) -> new CustomerPage(c, List.of(o))
).block(Duration.ofSeconds(5));
~~~

Giả sử `OrderDto` và `CustomerPage` được định nghĩa. Khi `zip` được subscribe, hai request có thể cùng tiến triển; trong khi đó hai lời gọi `customer.block()` rồi `orders.block()` sẽ chờ tuần tự. Nếu một nguồn lỗi/rỗng, `zip` tuân theo semantics Reactor (có thể lỗi hoặc hoàn thành rỗng và hủy nguồn khác); cần chính sách lỗi/rỗng rõ ràng.

**Không block lặp lại trên cùng một `Mono` nguồn nếu chỉ muốn lấy một response**: nhiều lần `block()` thường tạo nhiều subscription và có thể phát sinh nhiều HTTP requests, vì WebClient publisher mặc định là cold.

## 4. Cách dùng không block: MVC trả Mono trực tiếp

MVC có thể trả một `Mono` từ controller; framework đăng ký xử lý bất đồng bộ và hoàn tất HTTP response khi publisher phát kết quả:

~~~java
@RestController
class AsyncCustomerController {
    private final WebClient client;

    AsyncCustomerController(WebClient.Builder builder) {
        this.client = builder.baseUrl("https://api.example.com").build();
    }

    @GetMapping("/async-customers/{id}")
    Mono<CustomerDto> get(@PathVariable long id) {
        return client.get()
                .uri("/customers/{id}", id)
                .retrieve()
                .bodyToMono(CustomerDto.class)
                .timeout(Duration.ofSeconds(3));
    }
}
~~~

Đây vẫn là **Spring MVC controller**, không phải WebFlux handler. Caller không tự gọi `subscribe()`: Spring MVC subscribe thông qua adapter để quản lý lifecycle của response. Phương thức controller trả nhanh, còn servlet request ở trạng thái async đến khi response kết thúc hoặc timeout/hủy.

Spring MVC cũng hỗ trợ reactive trả về nhiều phần tử, nhưng cần chọn media type và cách ghi response phù hợp; chẳng hạn `text/event-stream` để stream hoặc `application/json` thường gom nhiều phần tử thành một collection để serialize. Đừng mặc định `Flux` trên MVC server đồng nghĩa ghi socket non-blocking.

### Ghép request mà không block

~~~java
@GetMapping("/overview/{id}")
Mono<CustomerPage> overview(@PathVariable long id) {
    Mono<CustomerDto> profile = client.get()
            .uri("/customers/{id}", id)
            .retrieve()
            .bodyToMono(CustomerDto.class);

    Mono<OrderDto[]> orders = client.get()
            .uri("/customers/{id}/orders", id)
            .retrieve()
            .bodyToMono(OrderDto[].class);

    return Mono.zip(profile, orders,
            (customer, items) -> new CustomerPage(
                    customer, List.of(items)))
            .timeout(Duration.ofSeconds(5));
}
~~~

`Mono.zip` mô tả song song về mặt subscribe. Với các API phụ thuộc nhau, dùng `flatMap`:

~~~java
Mono<OrderDto[]> result = client.get()
        .uri("/customers/42")
        .retrieve()
        .bodyToMono(CustomerDto.class)
        .flatMap(customer -> client.get()
                .uri("/customers/{id}/orders", customer.id())
                .retrieve()
                .bodyToMono(OrderDto[].class));
~~~

Hai ví dụ này giả sử các DTO và bean `client` tồn tại; không phải file Java độc lập. Đặc biệt, `flatMap` nhận publisher; không cần `block()` để lấy `customer` bên trong lambda.

## 5. Demo hoàn chỉnh Java 17+: một app MVC, hai endpoint

Demo chạy local mà không phụ thuộc dịch vụ Internet. Ứng dụng Spring Boot 3.x sử dụng **Spring MVC với Tomcat** và WebClient gọi một HTTP server giả lập độc lập chạy trên port ngẫu nhiên của JDK. Có hai API:

- `GET /sync`: Spring MVC controller gọi `block(Duration)` trên servlet thread.
- `GET /async`: controller trả `Mono<Quote>` để Spring MVC xử lý bằng Servlet async.

Mục tiêu là quan sát hai **ranh giới thực thi**; kết quả JSON giống nhau không có nghĩa hai endpoint giữ servlet worker như nhau.

### `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>mvc-webclient</artifactId>
    <version>0.0.1-SNAPSHOT</version>
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
            <artifactId>spring-boot-starter-webflux</artifactId>
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

Khi cả `spring-boot-starter-web` và `spring-boot-starter-webflux` có mặt, Spring Boot 3.x mặc định cấu hình **Spring MVC web application**; starter WebFlux cung cấp WebClient/Reactor Netty client và những thành phần reactive cần thiết. Điều này **không tự thay server MVC bằng Netty/WebFlux**.

### `src/main/java/demo/MvcWebClientApplication.java`

~~~java
package demo;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class MvcWebClientApplication {

    public static void main(String[] args) throws Exception {
        HttpServer upstream = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);

        upstream.createContext("/quote", exchange -> {
            byte[] payload = "{\"text\":\"hello\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders()
                    .set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        upstream.start();

        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> upstream.stop(0)));

        SpringApplication app = new SpringApplication(
                MvcWebClientApplication.class);
        app.setDefaultProperties(Map.of(
                "demo.upstream-url",
                "http://127.0.0.1:" + upstream.getAddress().getPort()));
        app.run(args);
    }

    @Bean
    WebClient upstreamClient(
            WebClient.Builder builder,
            @Value("${demo.upstream-url}") String upstreamUrl) {
        return builder.baseUrl(upstreamUrl).build();
    }

    public record Quote(String text) {}

    @RestController
    static class GatewayController {
        private final WebClient client;

        GatewayController(WebClient upstreamClient) {
            this.client = upstreamClient;
        }

        @GetMapping("/sync")
        Quote sync() {
            return client.get()
                    .uri("/quote")
                    .retrieve()
                    .bodyToMono(Quote.class)
                    .block(Duration.ofSeconds(3));
        }

        @GetMapping("/async")
        Mono<Quote> async() {
            return client.get()
                    .uri("/quote")
                    .retrieve()
                    .bodyToMono(Quote.class)
                    .timeout(Duration.ofSeconds(3));
        }
    }
}
~~~

Chạy ở thư mục chứa `pom.xml` (JDK 17+, Maven):

~~~bash
mvn spring-boot:run
~~~

Mở terminal khác:

~~~bash
curl -s http://127.0.0.1:8080/sync
curl -s http://127.0.0.1:8080/async
~~~

Cả hai lệnh đều có body dự kiến (mỗi lệnh một dòng):

~~~json
{"text":"hello"}
~~~

Đối với ứng dụng Boot dùng cấu hình cổng mặc định, server MVC lắng nghe ở `8080`; port upstream do OS cấp động. `HttpServer` giả lập upstream xử lý request độc lập nên không tạo vòng lặp tự gọi vào cùng một MVC server. Dừng `mvn spring-boot:run` sẽ đóng ứng dụng, shutdown hook dừng upstream.

Kết quả giống nhau về JSON, nhưng khác cách tiêu thụ servlet worker: `sync` chờ trên thread nhận request; `async` trả `Mono` để MVC đăng ký async processing. Đây là demo hành vi, **không phải benchmark**; cần công cụ đo luồng và tải để đánh giá khả năng mở rộng.

## 6. Vì sao block trong Reactor event-loop nguy hiểm?

Reactor Netty thường dùng một số event-loop threads để xử lý sự kiện socket cho rất nhiều request. Nếu một trong các threads này đang chờ I/O đồng bộ hoặc `Thread.sleep` lâu, các kết nối khác do nó phụ trách cũng bị chậm. Reactor đánh dấu nhiều thread xử lý non-blocking bằng marker `NonBlocking`; gọi `block()`/`blockFirst()`/`blockLast()` ở đó thường bị ngăn bằng exception kiểu:

~~~text
java.lang.IllegalStateException:
block()/blockFirst()/blockLast() are blocking,
which is not supported in thread reactor-http-nio-...
~~~

Tên thread có thể khác theo môi trường. Không nên dựa **chỉ** vào tên thread để quyết định an toàn; `Schedulers.isInNonBlockingThread()` cho biết thread hiện tại có được Reactor đánh dấu không được block.

### Sai: nested block bên trong operator

~~~java
Mono<CustomerPage> bad = client.get()
        .uri("/customers/42")
        .retrieve()
        .bodyToMono(CustomerDto.class)
        .map(customer -> {
            // Sai: map có thể chạy trên Reactor Netty event-loop.
            OrderDto[] orders = client.get()
                    .uri("/customers/{id}/orders", customer.id())
                    .retrieve()
                    .bodyToMono(OrderDto[].class)
                    .block();
            return new CustomerPage(customer, List.of(orders));
        });
~~~

Khi chạy dưới một subscriber, lambda `map` có thể được gọi trên event-loop của HTTP response, nên `block()` lồng bên trong vi phạm threading model. **Đúng:** dùng `flatMap` để diễn tả HTTP call thứ hai bằng publisher:

~~~java
Mono<CustomerPage> good = client.get()
        .uri("/customers/42")
        .retrieve()
        .bodyToMono(CustomerDto.class)
        .flatMap(customer -> client.get()
                .uri("/customers/{id}/orders", customer.id())
                .retrieve()
                .bodyToMono(OrderDto[].class)
                .map(orders -> new CustomerPage(
                        customer, List.of(orders))));
~~~

### Sai: gọi thư viện blocking thẳng trong pipeline

~~~java
Mono<String> wrong = client.get()
        .uri("/quote")
        .retrieve()
        .bodyToMono(String.class)
        .map(body -> legacyFileApi.readAndStore(body)); // Chặn thread đang chạy map!
~~~

Nếu code thực sự phải gọi API blocking mà bạn không thể thay thế, bọc **chỉ đoạn blocking** bằng scheduler phù hợp:

~~~java
Mono<String> right = client.get()
        .uri("/quote")
        .retrieve()
        .bodyToMono(String.class)
        .flatMap(body -> Mono.fromCallable(
                    () -> legacyFileApi.readAndStore(body))
                .subscribeOn(Schedulers.boundedElastic()));
~~~

Hai đoạn này là code minh họa; cần import `reactor.core.scheduler.Schedulers` và có `legacyFileApi` thực tế. `boundedElastic` dành cho tác vụ blocking có giới hạn; nó không làm API legacy thành non-blocking hay tạo throughput vô hạn. Nếu toàn bộ pipeline chỉ là WebClient reactive, **không cần** `subscribeOn(boundedElastic())` để “biến WebClient thành async”: WebClient đã có cơ chế I/O bất đồng bộ.

Mẹo sai thường gặp: gắn `publishOn(Schedulers.boundedElastic())` vào Mono rồi gọi `block()` ngay bên ngoài **không chuyển chính thread gọi `block()` sang boundedElastic**. Thread caller vẫn bị chặn. Chỉ dùng offload khi tác vụ blocking thực sự nằm **bên trong** publisher mà scheduler điều khiển.

## 7. Timeout, lỗi, hủy request và vòng đời body

### Phân biệt timeout

- `block(Duration.ofSeconds(3))`: timeout thời gian thread caller chờ tín hiệu từ publisher; chỉ thích hợp ở ranh giới synchronous đã cho phép block. Khi timeout, subscription bị hủy.
- `mono.timeout(Duration.ofSeconds(3))`: thiết lập giới hạn ở cấp pipeline reactive; phù hợp cho MVC controller trả `Mono`. Timeout sinh tín hiệu lỗi và hủy upstream.
- Timeout kết nối TCP, nhận response hoặc acquire connection là các cấu hình HTTP client/connection pool; chúng không hoàn toàn đồng nghĩa hai loại timeout trên.
- Spring MVC Servlet async cũng có timeout ở tầng web MVC; cấu hình và ánh xạ lỗi sao cho không có tầng chờ vô hạn, đồng thời cân nhắc timeout tổng thể theo SLA.

Không nên chỉ tăng mọi timeout để xử lý cạn servlet threads. Quan sát thời gian chờ upstream, connection pool, max threads và số request đồng thời. Timeout không phải bảo đảm request được upstream dừng xử lý hoàn toàn: cancel client không nhất thiết rollback hoạt động ở server.

### Xử lý lỗi có chủ đích

`retrieve()` mặc định phát lỗi khi HTTP 4xx hoặc 5xx; kết nối lỗi/không kết nối thường phát `WebClientRequestException`, thời gian chờ có thể tạo loại lỗi khác theo API chọn. Với MVC synchronous, lỗi từ `block()` xuất hiện tại call stack đồng bộ; với MVC reactive return, lỗi là tín hiệu trên publisher và do pipeline/MVC exception infrastructure xử lý. Thiết kế `@ExceptionHandler`, response status và log phù hợp, không bắt toàn bộ lỗi rồi trả 200 với dữ liệu giả.

Ví dụ xử lý upstream 404 theo nghiệp vụ mà không block trong callback:

~~~java
Mono<CustomerDto> customer = client.get()
        .uri("/customers/{id}", 42)
        .retrieve()
        .onStatus(status -> status.value() == 404,
                response -> Mono.error(
                        new CustomerNotFoundException(42)))
        .bodyToMono(CustomerDto.class);
~~~

Đây là code minh họa giả định exception tùy biến tồn tại. `onStatus` phải trả `Mono<? extends Throwable>` phù hợp. Với các nhánh tự xử lý `ClientResponse` bằng `exchangeToMono`, luôn đọc hoặc giải phóng response body đúng cách; tránh giữ lại `ClientResponse` để sử dụng sau khi callback kết thúc. Nếu luồng đã bị cancel giữa chừng, connection pool có thể phải loại socket HTTP/1.1 khỏi pool; đừng khẳng định mọi request bị hủy đều tái sử dụng được kết nối.

### Context và transaction qua thread boundary

Ở MVC synchronous, `ThreadLocal` như MDC, `RequestContextHolder` hoặc transaction có thể tồn tại trên servlet worker. Nhưng callback WebClient chạy ở luồng khác, vì thế **không mặc định** mang đủ servlet context/transaction sang reactive callback. Nếu cần tracing/security context propagation, dùng cơ chế context propagation được Spring/Micrometer hỗ trợ và kiểm thử thực tế. Không giữ JDBC transaction mở chỉ để đợi remote HTTP lâu nếu có thể tách lại ranh giới transaction.

## 8. Lỗi phổ biến và cách sửa

1. **Gọi `block()` trong WebFlux controller hoặc callback WebClient:** chuyển sang trả `Mono`/`Flux` và dùng `flatMap`/`zip`; nếu có thư viện blocking không tránh được, offload riêng phần đó.
2. **Gọi `subscribe()` thủ công trong MVC controller rồi trả kết quả trống:** bỏ subscription thủ công; trả publisher để MVC quản lý request và lỗi. `subscribe()` có thể làm request chạy ngoài lifecycle HTTP và lỗi bị bỏ sót.
3. **Gọi `block()` hai lần trên một publisher HTTP:** tạo hai subscriptions có thể gửi request hai lần. Dựng pipeline rồi subscribe/block ở đúng ranh giới một lần; cache chỉ khi có chủ đích và hiểu trade-off.
4. **Gọi hai upstream lần lượt bằng `block()`:** dựng hai `Mono` trước, kết hợp `zip` nếu độc lập, rồi mới block một lần khi cần đồng bộ.
5. **Cho rằng có WebClient thì MVC thành hoàn toàn non-blocking:** MVC vẫn dựa trên Servlet container; render/write response có thể dùng blocking executor, tùy kiểu trả về và media type.
6. **Không giới hạn timeout/concurrency trong endpoint synchronous:** servlet workers có thể bị chiếm hết. Thiết lập thời hạn, capacity phù hợp; cân nhắc trả `Mono` và theo dõi metrics.
7. **Dùng `boundedElastic` tràn lan:** chỉ offload tác vụ legacy blocking; nó có giới hạn và có thể tạo queue.
8. **Đọc status nhưng quên response body:** `retrieve().bodyToMono(...)` hoặc `toBodilessEntity()`; với `exchangeToMono` hãy hoàn thiện xử lý/release body để connection pool không giữ tài nguyên lâu.
9. **Thử gọi HTTP trở lại chính cùng ứng dụng trong demo/tải nặng:** nếu servlet workers đều chờ các request nội bộ cần servlet worker khác, bạn có thể tự gây starvation. Tách upstream thực sự và giới hạn concurrency.

## 9. Checklist chọn ranh giới

Trước khi gọi WebClient từ Spring MVC, tự trả lời:

- **Ai đang gọi?** Servlet worker có thể block, Reactor event-loop thì không.
- **Hợp đồng trả về là gì?** Nếu có thể trả `Mono<T>`, hãy để MVC quản lý async; nếu hợp đồng `T` đồng bộ là bắt buộc, block một lần ở cuối trên thread được phép.
- **Có mấy HTTP calls?** Gộp các publishers độc lập bằng `zip` để giảm chờ tuần tự; dùng `flatMap` cho chuỗi có phụ thuộc.
- **Có mã blocking trong callback không?** Thay bằng API reactive hoặc offload phần legacy bằng `boundedElastic`.
- **Có timeout, xử lý lỗi, cancel, body cleanup rõ ràng không?** Đảm bảo request kết thúc và không giữ servlet thread/connection vô hạn.
- **Đo được bottleneck không?** Quan sát servlet busy threads, async requests, Reactor Netty connection pool, response latency và lỗi timeout dưới tải.

**Nguyên tắc:** Với MVC đồng bộ, `block()` được phép ở ranh giới imperative trên servlet worker nhưng có chi phí. Với MVC bất đồng bộ, trả `Mono` là lựa chọn tự nhiên. Bên trong chuỗi WebClient/reactive hoặc trên event-loop, giữ tất cả xử lý non-blocking và kết hợp publishers thay vì gọi `block()`.

## 10. Tài liệu tham khảo

- Spring Framework — WebClient Synchronous Use: https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-synchronous.html
- Spring Framework — Spring MVC Asynchronous Requests, Reactive Types: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- Spring Framework — Concurrency Model in Spring MVC and WebFlux: https://docs.spring.io/spring-framework/reference/web/webflux/new-framework.html
- Spring Boot — Calling REST Services / WebClient: https://docs.spring.io/spring-boot/reference/io/rest-client.html
- Project Reactor — Threading and Schedulers: https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html
- Spring Framework — WebClient retrieve(): https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-retrieve.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-client-010">Reactor Context, truyền mã đối chiếu và hủy yêu cầu đang chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Reactor Context, truyền mã đối chiếu và hủy yêu cầu đang chạy trong WebClient

## 1. Bài toán và mô hình hoạt động

Ứng dụng Spring WebFlux thường nhận một HTTP request rồi dùng `WebClient` gọi một hay nhiều dịch vụ khác. Khi tìm nguyên nhân lỗi, ta cần nối những log và lời gọi HTTP thuộc cùng yêu cầu gốc bằng **mã đối chiếu (correlation ID)**. Nếu người gọi ngừng cần kết quả, ta cũng muốn dừng các công việc còn đang chạy để tránh tiêu tốn tài nguyên vô ích.

Hai cơ chế giải quyết hai vấn đề khác nhau:

- **Reactor Context:** truyền dữ liệu nhỏ, không phụ thuộc thread, gắn với một subscription của `Mono`/`Flux`.
- **Cancellation:** subscriber phát tín hiệu `cancel` đến nguồn upstream; HTTP client có thể dừng nhận/gửi trao đổi đang diễn ra tùy connector và thời điểm.
- **HTTP header:** muốn dịch vụ phía xa nhìn thấy correlation ID, phải chủ động đưa ID từ Context vào header của outbound request. Reactor Context không tự vượt qua mạng.

Ví dụ kiến trúc:

```text
HTTP client
   |
   v
WebFlux server A: WebFilter nhận/tạo correlation ID
   |
   +-- chain.filter(exchange).contextWrite(c -> c.put(KEY, id))
   |
   v
A: service -> Mono/Flux của nghiệp vụ
   |                         |
   +--> WebClient -> B       +--> flatMap -> WebClient -> C
         |                                 |
         v                                 v
   filter đọc Context                 filter đọc Context
         |                                 |
         v                                 v
   Header X-Correlation-ID           Header X-Correlation-ID
   |
   v
Kết quả / lỗi / cancellation trở về subscriber của A
```

**Cần phân biệt:** correlation ID giúp nhóm những sự kiện của một công việc; trace ID/span ID từ một hệ thống distributed tracing còn mô tả quan hệ giữa các bước xử lý. Correlation ID không xác thực danh tính, không bảo đảm tất cả hành động đều nguyên tử và không tự kiểm soát vòng đời của request ở server từ xa.

Mục tiêu bài học: hiểu chiều truyền của Context, cách đọc/ghi Context trong WebClient, lan truyền qua `flatMap`, tạo header một cách an toàn, xem xét ngữ nghĩa cancel và timeout, kiểm thử hành vi mà không cần mạng thật.

## 2. Reactor Context là gì?

`Context` của Project Reactor là một tập key/value bất biến theo ngữ nghĩa API: `put` tạo Context mới, không sửa trực tiếp Context cũ. `ContextView` là giao diện chỉ đọc. Dữ liệu được gắn với **subscriber** và truyền qua các toán tử reactive hỗ trợ Context; nó không mặc định gắn với thread cụ thể.

| Cơ chế | Nơi dữ liệu được lưu | Phạm vi |
| --- | --- | --- |
| `Reactor Context` | Ngữ cảnh của subscription `Mono`/`Flux` | Những toán tử/publisher cùng chuỗi reactive, kể cả publisher lồng nhau được kết hợp đúng cách |
| `ClientRequest.attribute(...)` | Thuộc tính nội bộ của một request WebClient | Filter của **request đó**, không tự chuyển sang request khác |
| HTTP header | Dữ liệu gửi qua mạng | Dịch vụ nhận request có thể đọc được |
| `ThreadLocal` | Biến gắn với thread hiện tại | Không đáng tin để đại diện một request reactive có thể chuyển thread |
| MDC của logger | Thông thường là trạng thái theo thread | Muốn kết nối với Reactor Context cần tích hợp propagation đúng phiên bản |

Một subscription có thể nhận Context của riêng nó. Chẳng hạn, cùng một `Mono` đọc được hai giá trị khác nhau khi subscribe hai lần:

**Tệp `ContextBasics.java` (chạy với Java 17+ và Reactor trên classpath):**

```java
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class ContextBasics {
    static final String KEY = "correlationId";

    public static void main(String[] args) {
        Mono<String> work = Mono.deferContextual(view -> {
            String id = view.getOrDefault(KEY, "missing");
            return Mono.just("id=" + id);
        });

        System.out.println(work
                .contextWrite(Context.of(KEY, "request-A"))
                .block());

        System.out.println(work
                .contextWrite(Context.of(KEY, "request-B"))
                .block());
    }
}
```

**Stdout:**

```text
id=request-A
id=request-B
```

`Mono.deferContextual` đọc Context **lúc có subscription**, không đọc một biến mutable toàn cục lúc dựng pipeline. `Context.of(KEY, value)` là cách cấp Context cho một subscription; `contextWrite(c -> c.put(KEY, value))` là cách cập nhật dựa trên Context đã có.

### 2.1 Vì sao contextWrite đặt ở phía cuối?

Dữ liệu thông thường đi từ nguồn tới subscriber; Context đi theo phía subscriber **ngược lên upstream** trong quá trình subscription.

```java
Mono<String> example =
        Mono.deferContextual(view ->
                Mono.just(view.getOrDefault("label", "absent")))
            .map(String::toUpperCase)
            .contextWrite(ctx -> ctx.put("label", "visible"));

System.out.println(example.block()); // VISIBLE
```

Mặc dù `contextWrite` được viết sau `deferContextual`, phần upstream vẫn thấy `label`. Một `contextWrite` đặt giữa chuỗi chỉ ảnh hưởng những đoạn upstream trong phạm vi đó; nó không tạo “biến toàn cục mới” cho mọi publisher và không tự phát HTTP.

Phân biệt ba thời điểm:

1. **Assembly:** tạo đối tượng mô tả các thao tác, ví dụ `client.get().retrieve().bodyToMono(...)`.
2. **Subscription:** WebFlux runtime, `subscribe()` hoặc `block()` đăng ký publisher; lúc này Context được chuyển, filter có thể chạy và HTTP được thực hiện.
3. **Signals:** dữ liệu, lỗi, hoàn thành hoặc `cancel` đi qua pipeline.

`block()` trong ví dụ `main` chỉ giúp minh họa đồng bộ. Không dùng `block()` trên event loop của WebFlux.

### 2.2 Key, kiểu và giá trị Context

Có thể dùng chuỗi khóa cố định (`"correlationId"`) hoặc khóa riêng theo class/module để tránh xung đột. Một filter có thể đọc `view.get(KEY)` nếu chắc chắn có khóa, hoặc `view.getOrDefault(KEY, fallback)` khi cần hoạt động an toàn lúc thiếu dữ liệu. Kiểu trả về phải phù hợp kiểu giá trị đã lưu.

Nên lưu chuỗi ID ngắn, đối tượng bất biến hoặc metadata nhỏ; tránh bỏ body, stream, mật khẩu, mutable request/session object hay dữ liệu lớn vào Context. Không dùng correlation ID làm bằng chứng đăng nhập hoặc phân quyền.

## 3. Truyền Context sang WebClient bằng ExchangeFilterFunction

Để gửi mã ra ngoài, filter của `WebClient` phải đọc Context và **tạo request mới** chứa HTTP header.

**Tệp `CorrelationFilter.java`:**

```java
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

public final class CorrelationFilter {
    public static final String KEY = "correlationId";
    public static final String HEADER = "X-Correlation-ID";

    private CorrelationFilter() {}

    public static ExchangeFilterFunction create() {
        return (request, next) -> Mono.deferContextual(view -> {
            String id = view.getOrDefault(KEY, "");

            if (id.isBlank()) {
                return next.exchange(request);
            }

            ClientRequest updated = ClientRequest.from(request)
                    .headers(headers -> headers.set(HEADER, id))
                    .build();

            return next.exchange(updated);
        });
    }
}
```

Filter trả `Mono<ClientResponse>`; `next.exchange(request)` tiếp tục tới filter sau hoặc HTTP transport. `ClientRequest.from(request)` giữ các thuộc tính khác và cho phép chỉnh header trên bản request mới. `headers.set` thay thế giá trị cũ của header. Trong ví dụ này **ID lấy từ Context được ưu tiên** hơn header do request đặt trước đó; nếu muốn header cụ thể thắng thì phải thay đổi chính sách một cách tường minh.

**Không đọc Context một lần khi xây dựng client:** `WebClient` thường được dùng lại cho nhiều request chạy đồng thời. Đặt `Mono.deferContextual` trong filter bảo đảm mỗi subscription lấy dữ liệu tương ứng.

### 3.1 Đăng ký filter vào builder

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class RemoteClientConfiguration {
    @Bean
    WebClient inventoryClient(WebClient.Builder sharedBuilder) {
        return sharedBuilder.clone()
                .baseUrl("https://inventory.example")
                .filter(CorrelationFilter.create())
                .build();
    }
}
```

Đây là cấu hình mẫu trong Spring Boot với `WebClient.Builder` được cung cấp; Spring Framework thuần có thể tự tạo `WebClient.builder()` hoặc tự khai báo bean. URL minh họa phải được thay bằng cấu hình của dịch vụ thật. `clone()` tránh việc thêm filter hoặc đổi `baseUrl` vô tình ảnh hưởng những client khác sử dụng cùng builder.

Trong service, chỉ cần trả `Mono`/`Flux` cho caller:

```java
Mono<String> response = inventoryClient.get()
        .uri("/stock/{sku}", "SKU-7")
        .retrieve()
        .bodyToMono(String.class)
        .contextWrite(ctx ->
                ctx.put(CorrelationFilter.KEY, "request-A"));
```

Đoạn này giả định `inventoryClient` đã được inject và API trả body String; nó không phải chương trình Java hoàn chỉnh. Khi có subscription, filter sẽ thêm `X-Correlation-ID: request-A` lên outbound HTTP request.

### 3.2 Vì sao request lồng nhau vẫn nhận cùng mã?

```java
Mono<String> workflow =
        inventoryClient.get()
                .uri("/items/{sku}", "SKU-7")
                .retrieve().bodyToMono(String.class)
                .flatMap(item ->
                        inventoryClient.get()
                                .uri("/stock/{sku}", "SKU-7")
                                .retrieve().bodyToMono(String.class))
                .contextWrite(ctx ->
                        ctx.put(CorrelationFilter.KEY, "request-A"));
```

Hai HTTP request trên cùng chuỗi đều có thể thấy `request-A`, vì `flatMap` subscribe publisher bên trong bằng context của chuỗi reactive phù hợp. Đây là trường hợp tiêu biểu nên dùng Reactor Context hơn request attribute. `ClientRequest.attribute` chỉ thuộc về một request và không tự chuyển sang request sau.

Không làm như sau trong controller/service:

```java
// Anti-pattern minh họa trong một phương thức:
inventoryClient.get().uri("/items/SKU-7")
        .retrieve().bodyToMono(String.class)
        .subscribe(); // Bắt đầu subscription tách khỏi pipeline caller.
return Mono.just("started");
```

`subscribe()` thủ công có thể làm mất quyền kiểm soát lỗi, Context, thời hạn và cancellation của caller. Thường nên **trả về publisher** hoặc kết hợp với `flatMap`/`then` thay vì phát tác vụ rời rạc.

### 3.3 Truyền ID an toàn

Correlation ID là metadata vận hành, không phải token xác thực. Chỉ nhận những ký tự có thể đưa vào HTTP header an toàn, giới hạn độ dài (ví dụ cho phép chữ, số, dấu chấm, gạch dưới và gạch ngang, tối đa 64 ký tự) và không cho CR/LF.

Một ID do bên ngoài gửi có thể bị giả mạo. Trong hệ thống có biên tin cậy, có thể luôn tạo ID mới ở cổng vào hoặc chỉ tin gateway đã xác thực; **regex chỉ kiểm tra định dạng**. Không chuyển header chứa ID hoặc credential sang host ngoài danh sách tin cậy mà không có chính sách rõ ràng. Với dự án đã dùng OpenTelemetry hoặc Micrometer Tracing, nên ưu tiên trace propagation tiêu chuẩn như `traceparent` để có đầy đủ quan hệ span; correlation ID tùy chỉnh chỉ là một lớp bổ sung.

## 4. Đưa ID inbound vào Context của WebFlux server

`WebFilter` chạy phía **server inbound**; `ExchangeFilterFunction` chạy phía **client outbound**. Một `WebFilter` có thể đọc/tạo ID rồi đưa vào Reactor Context để những lời gọi WebClient trong downstream dùng được:

**Tệp `IncomingCorrelationWebFilter.java`:**

```java
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class IncomingCorrelationWebFilter implements WebFilter {
    private static final String HEADER = "X-Correlation-ID";
    private static final String KEY = "correlationId";
    private static final Pattern SAFE =
            Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             WebFilterChain chain) {
        String incoming = exchange.getRequest()
                .getHeaders().getFirst(HEADER);

        String id = incoming != null && SAFE.matcher(incoming).matches()
                ? incoming : UUID.randomUUID().toString();

        exchange.getResponse().getHeaders().set(HEADER, id);

        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(KEY, id));
    }
}
```

Ứng dụng cần bật Spring WebFlux và component scanning cho class trên. `contextWrite` bọc chuỗi do `chain.filter(exchange)` trả về nên những thành phần ở upstream trong chain có thể đọc `id`. Response header ở đây được đặt trước khi response được commit; caller nhìn thấy ID của lần xử lý hiện tại nếu response được trả qua filter.

Nếu không tin tưởng ID inbound, hãy **thay nhánh chọn incoming bằng tạo ID mới** theo chính sách hệ thống. Nên quy định điểm khởi tạo, truyền tiếp và điểm thay đổi ID trong kiến trúc đa dịch vụ để tránh log từ cùng công việc bị rời rạc.

Ví dụ đọc ngữ cảnh ở tầng nghiệp vụ:

```java
Mono<Void> auditedWork = Mono.deferContextual(view -> {
    String id = view.getOrDefault("correlationId", "missing");
    System.out.println("processing id=" + id);
    return Mono.empty();
});
```

Đây là đoạn minh họa; trong production dùng logger được cấu hình, chỉ log ID/method/status/đường dẫn đã làm sạch. `MDC` của SLF4J thường dựa vào thread và không tự đồng bộ với Reactor Context ở mọi toán tử/scheduler. Muốn đưa ID vào MDC, dùng tích hợp context propagation/observability phù hợp Spring, Reactor và Micrometer phiên bản thực tế, đồng thời kiểm tra hành vi khi chuyển thread.

## 5. Ví dụ hoàn chỉnh: hai lời gọi WebClient cùng Context

Chương trình dưới dùng `ExchangeFunction` giả tạo HTTP response trong bộ nhớ; **không kết nối mạng**. Một chuỗi `flatMap` gửi hai request mô phỏng, đọc cùng mã; cuối chương trình hủy một `Mono` không hoàn tất.

**Tệp `ContextCancelDemo.java` (Java 17+, Spring WebFlux và Reactor):**

```java
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class ContextCancelDemo {
    private static final String KEY = "correlationId";
    private static final String HEADER = "X-Correlation-ID";

    public static void main(String[] args) {
        ExchangeFunction fakeTransport = request -> {
            System.out.println("fake HTTP " + request.method()
                    + " " + request.url().getPath()
                    + " id=" + request.headers().getFirst(HEADER));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE,
                            MediaType.TEXT_PLAIN_VALUE)
                    .body("ok")
                    .build());
        };

        ExchangeFilterFunction correlation =
                (request, next) -> Mono.deferContextual(view -> {
                    String id = view.getOrDefault(KEY, "missing");
                    ClientRequest newRequest = ClientRequest.from(request)
                            .headers(h -> h.set(HEADER, id))
                            .build();
                    return next.exchange(newRequest);
                });

        WebClient client = WebClient.builder()
                .baseUrl("https://example.invalid")
                .exchangeFunction(fakeTransport)
                .filter(correlation)
                .build();

        String result = client.get()
                .uri("/first")
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(first -> client.get()
                        .uri("/second")
                        .retrieve()
                        .bodyToMono(String.class))
                .contextWrite(Context.of(KEY, "request-42"))
                .block();

        System.out.println("response=" + result);

        AtomicBoolean cancelled = new AtomicBoolean();
        Disposable subscription = Mono.<String>never()
                .doOnCancel(() -> {
                    cancelled.set(true);
                    System.out.println("upstream cancel observed");
                })
                .subscribe();

        subscription.dispose();
        System.out.println("cancelled=" + cancelled.get());
    }
}
```

**Stdout:**

```text
fake HTTP GET /first id=request-42
fake HTTP GET /second id=request-42
response=ok
upstream cancel observed
cancelled=true
```

Mỗi request giả lập chỉ in header và trả body `ok`. Sau khi `block()` subscribe chuỗi, filter đọc `request-42` của subscription; `flatMap` giữ context cho lần gọi thứ hai. `Mono.never()` không phát dữ liệu và không hoàn thành; `dispose()` thông báo hủy, làm `doOnCancel` được gọi. Đó là minh chứng về **tín hiệu hủy trong Reactor**, không phải minh chứng một server HTTP từ xa ngừng xử lý.

Để chạy, đưa class vào project Spring Boot 3.x với Java 17+ và dependency (ví dụ Gradle):

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
}
```

Chạy phương thức `main` từ IDE dùng Gradle classpath, hoặc cung cấp đầy đủ JAR Spring/Reactor vào classpath cho `javac`/`java`. Không thể dùng `javac ContextCancelDemo.java` trong thư mục trống vì `org.springframework.*` và `reactor.*` không thuộc JDK. `block()` trong chương trình `main` dành cho thực nghiệm đồng bộ, không đặt trong code xử lý request trên event loop WebFlux.

## 6. Hủy một yêu cầu đang chạy thực sự nghĩa là gì?

Reactive Streams cho phép subscriber gửi `cancel` qua `Subscription.cancel()`. Ở Reactor, `subscribe()` thường trả một `Disposable`; gọi `dispose()` yêu cầu dừng subscription.

```text
Caller / WebFlux runtime
         |
         | cancel
         v
bodyToMono() hoặc bodyToFlux()
         |
         v
retrieve() / trao đổi WebClient
         |
         v
Client HTTP connector / network
         |
         v
Remote server (có thể đã nhận và xử lý request)
```

Ví dụ chủ thể sở hữu một subscription và muốn hủy nó:

```java
Disposable running = client.get()
        .uri("/long-running")
        .retrieve()
        .bodyToMono(String.class)
        .doFinally(kind -> System.out.println("finished=" + kind))
        .subscribe(
                body -> System.out.println("body=" + body),
                error -> System.err.println(error.getMessage())
        );

// Khi người gọi hủy tác vụ:
running.dispose();
```

Đây là đoạn minh họa trong thành phần có quyền giữ `running`. Với WebFlux controller, thường **trả `Mono` cho framework** để runtime sở hữu subscription và quản lý nó; không tự subscribe rồi tách công việc khỏi yêu cầu.

Các giới hạn quan trọng:

1. Tín hiệu cancel chỉ là **yêu cầu hủy theo giao thức reactive**. Nếu HTTP request đã rời client, remote server vẫn có thể đang chạy hoặc đã ghi dữ liệu.
2. HTTP connector có thể dừng nhận body, hủy trao đổi/đóng kết nối hoặc giải phóng tài nguyên tùy thời điểm, nhưng không bảo đảm hoàn tác tác dụng phụ ở server.
3. Một API thanh toán, tạo đơn hoặc ghi dữ liệu cần khóa idempotency/truy vấn trạng thái để xử lý kết quả mơ hồ, đặc biệt khi caller timeout rồi retry.
4. Thao tác blocking hoặc thư viện ngoài không hỗ trợ cancel có thể tiếp tục làm việc sau khi reactive subscriber hủy.
5. Một publisher hot/chia sẻ (`cache()`, `share()` hoặc nguồn quản lý độc lập) có vòng đời riêng; hủy một subscriber không nhất thiết dừng nguồn chung.

### 6.1 Quan sát complete, error và cancel

| Tình huống | Signal | API theo dõi |
| --- | --- | --- |
| Nguồn hoàn thành bình thường | `onComplete` | `doOnSuccess` (Mono), `doOnComplete` (Flux), `doFinally(ON_COMPLETE)` |
| Nguồn phát lỗi | `onError` | `doOnError`, `doFinally(ON_ERROR)` |
| Subscriber yêu cầu ngừng | `cancel` | `doOnCancel`, `doFinally(CANCEL)` |

`doFinally` được gọi khi chuỗi kết thúc bằng một trong ba cách. `doOnTerminate` phản ứng với complete/error, không đại diện đầy đủ cho cancellation.

```java
Mono<String> operation = client.get()
        .uri("/report")
        .retrieve()
        .bodyToMono(String.class)
        .doFinally(signalType -> {
            switch (signalType) {
                case CANCEL -> System.out.println("cancelled");
                case ON_ERROR -> System.out.println("failed");
                case ON_COMPLETE -> System.out.println("completed");
                default -> { }
            }
        });
```

Tạo biến `operation` chưa kích hoạt HTTP; phải có subscription. Những callback trên phục vụ quan sát/tài nguyên, không dùng để thay thế `onErrorResume`, `flatMap` hay cơ chế chuyển đổi dữ liệu của luồng.

### 6.2 Cancel khác timeout và status 4xx/5xx

- **Cancel:** một subscriber hoặc toán tử ở downstream yêu cầu ngừng. Subscriber đã cancel thường không được gửi thêm `onError` chỉ để báo “đã cancel”.
- **Reactor timeout:** `timeout(Duration)` làm hết thời gian chờ tín hiệu, **cancel upstream**, và phát `TimeoutException` xuống downstream khi không có fallback.
- **HTTP 404/500:** là trạng thái response HTTP; `retrieve()` có logic chuyển một số response status thành lỗi. Đây không đồng nghĩa cancel.
- **Timeout ở connector:** connect timeout, response timeout hoặc read timeout kiểm soát từng giai đoạn của transport; không thay thế deadline nghiệp vụ tổng thể.

```java
Mono<String> timed = client.get()
        .uri("/slow")
        .retrieve()
        .bodyToMono(String.class)
        .doFinally(kind -> System.out.println("beforeTimeout=" + kind))
        .timeout(java.time.Duration.ofSeconds(2))
        .doFinally(kind -> System.out.println("afterTimeout=" + kind));
```

Khi quá hạn trước khi có kết quả, `doFinally` **phía nguồn** có thể thấy `CANCEL`, còn `doFinally` phía dưới `timeout` thấy `ON_ERROR`. Đó là hai góc nhìn của cùng một sự kiện timeout, không phải kết quả mâu thuẫn. Thứ tự thời gian in log có thể tùy scheduler; không viết test dựa vào thứ tự callback nếu không được API bảo đảm.

Một `Flux.take(1)` có thể cancel upstream sau khi nhận phần tử đầu; `switchMap` có thể hủy tác vụ cũ khi có giá trị mới. Với streaming HTTP, cần hiểu đóng/giải phóng body sau khi dừng sớm để không giữ connection ngoài ý muốn.

## 7. Quản lý vòng đời trong controller và service

Một WebFlux controller nên mô tả công việc bằng publisher, ví dụ:

```java
@GetMapping("/product/{sku}")
public Mono<String> product(@PathVariable String sku) {
    return inventoryClient.get()
            .uri("/products/{sku}", sku)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(3));
}
```

Đoạn này nằm bên trong class `@RestController` đã inject `inventoryClient`; cần import `@GetMapping`, `@PathVariable`, `Mono` và `Duration`. WebFlux runtime subscribe publisher và có thể phát tín hiệu cancel khi kết nối với caller đóng, tùy giai đoạn xử lý. Vì vậy service không cần `subscribe()` thủ công để “chạy ngay” request.

Với công việc thực sự cần chạy độc lập vòng đời HTTP (ví dụ xử lý job dài), phải tạo cơ chế job riêng có danh tính, trạng thái, deadline và quyền hủy rõ ràng. Không giả định một `subscribe()` rời rạc là hệ thống quản lý tác vụ nền đáng tin cậy.

### 7.1 Cancellation và correlation ID liên hệ ra sao?

Mã đối chiếu giúp giải thích **request nào** đã hoàn thành, lỗi hoặc bị hủy. Filter có thể log metadata an toàn và ghi nhận loại kết thúc bằng `doFinally`, nhưng không nên đọc response body chỉ để log hoặc tạo thêm request phụ chỉ để ghi log.

Nếu dùng `doFinally` trong filter, cần lưu ý vị trí toán tử: đo thời gian cho một HTTP attempt khác với đo thời gian cho cả pipeline có `retry`, `timeout`, decode body. Khi cần observability xuyên nhiều tầng, sử dụng Micrometer Observation/Tracing hoặc instrument thích hợp để có số đo nhất quán, rồi gắn correlation ID làm thuộc tính bổ sung.

Không ghi token, cookie, authorization header, dữ liệu thanh toán hoặc raw query string có secret. Không coi cancel là lỗi nghiệp vụ nếu người dùng thực sự đã ngừng quan tâm kết quả.

## 8. Kiểm thử không cần kết nối mạng

Dùng `ExchangeFunction` giả, như ở phần 5, để ghi lại outbound header. Với JUnit 5 + `reactor-test`, test có thể kiểm tra ID thuộc đúng subscription:

```java
AtomicReference<String> seen = new AtomicReference<>();

ExchangeFunction fake = request -> {
    seen.set(request.headers().getFirst("X-Correlation-ID"));
    return Mono.just(ClientResponse.create(HttpStatus.OK)
            .body("done")
            .build());
};

WebClient client = WebClient.builder()
        .baseUrl("https://example.invalid")
        .exchangeFunction(fake)
        .filter(CorrelationFilter.create())
        .build();

StepVerifier.create(client.get()
                .uri("/test")
                .retrieve()
                .bodyToMono(String.class)
                .contextWrite(Context.of("correlationId", "test-123")))
        .expectNext("done")
        .verifyComplete();

assertEquals("test-123", seen.get());
```

Đây là **đoạn trong phương thức `@Test`**, cần import `AtomicReference`, `ExchangeFunction`, `ClientResponse`, `HttpStatus`, `WebClient`, `Mono`, `Context`, `StepVerifier` và `assertEquals`. Test dùng filter `CorrelationFilter` từ phần 3. Với Gradle, khai báo `testImplementation 'io.projectreactor:reactor-test'` và JUnit 5 tương thích.

Kiểm thử cancel thuần Reactor:

```java
AtomicBoolean cancelled = new AtomicBoolean();

StepVerifier.create(Mono.<String>never()
                .doOnCancel(() -> cancelled.set(true)))
        .thenCancel()
        .verify();

assertTrue(cancelled.get());
```

Test này xác minh giao thức Reactor, **không xác minh HTTP server thật dừng xử lý**. Muốn xác minh transport, dùng server giả điều khiển được thời điểm response/connection và theo dõi việc đóng kết nối, giải phóng body và các request thực tế.

Các trường hợp nên có test: không có Context, hai subscription có ID khác nhau, hai request lồng nhau, header không hợp lệ ở inbound, cancel trước và sau khi bắt đầu nhận response, timeout với lỗi downstream, retry sau một tác vụ gây tác dụng phụ, và tác vụ shared/hot không dừng như nguồn cold thông thường.

## 9. Lỗi thường gặp và cách khắc phục

| Nhầm lẫn | Hậu quả | Cách xử lý |
| --- | --- | --- |
| “Reactor Context là ThreadLocal mới” | Trộn ID giữa những request khi scheduler đổi thread | Dùng `deferContextual`, `contextWrite`; tích hợp MDC rõ ràng |
| Gọi `ctx.put(KEY, id)` nhưng bỏ kết quả mới | Không thay đổi Context đang dùng | Trả về Context được `put` tạo, ví dụ `contextWrite(c -> c.put(...))` |
| Đặt `contextWrite` sai phía của đoạn đọc | Filter có thể không thấy giá trị | Kiểm tra vị trí upstream/downstream và subscription boundary |
| Tạo filter đọc ID khi build client | ID của user/request bị giữ cố định hoặc mất | Đọc Context trong `Mono.deferContextual` khi request được subscribe |
| Dùng `ClientRequest.attribute` rồi kỳ vọng tự sang API kế tiếp | Request thứ hai thiếu ID | Dùng Context và filter thêm HTTP header |
| Dùng `subscribe()` thủ công trong controller | Mất ràng buộc cancel/error/context của caller | Kết hợp publisher bằng `flatMap`/`then` và trả về runtime |
| Coi correlation ID là token tin cậy | Dễ bị giả mạo, dẫn đến quyết định phân quyền sai | Xác thực độc lập; chỉ dùng ID cho tương quan log |
| Cho rằng cancel dừng giao dịch ở server | Có thể lặp thanh toán/ghi dữ liệu khi retry | Idempotency key, deadline, kiểm tra trạng thái cuối |
| Chỉ dùng `doOnError` để đếm cancel | Thiếu nhiều lần hủy không phát lỗi | Quan sát `doOnCancel` / `doFinally(CANCEL)` |
| Nhầm Reactor `timeout` với mọi network timeout | Độ trễ kết nối/đọc không có chính sách rõ ràng | Kết hợp deadline nghiệp vụ và timeout connector |
| Trả lại `ClientResponse` sau khi filter đọc body | Downstream hết body/rò tài nguyên | Không tiêu thụ body để log; xử lý body đúng vòng đời nếu can thiệp |
| Ghi mọi header/body vào log | Lộ secret/PII | Chỉ ghi những trường đã cho phép, dùng tracing chuẩn |

## 10. Tự kiểm tra mức độ hiểu

1. Cùng một `Mono` đọc `Context` nhưng có hai subscriber mang ID khác nhau: filter nhìn thấy ID nào? **ID thuộc subscriber hiện tại.**
2. Vì sao `contextWrite` thường nằm gần cuối pipeline? **Context truyền từ subscriber lên upstream, nơi `deferContextual` và filter đọc nó.**
3. Đã gắn ID vào Reactor Context thì dịch vụ phía sau có tự nhận ID? **Không; phải ghi vào header hoặc dùng cơ chế truyền trace được cấu hình.**
4. `flatMap` gọi thêm WebClient có giữ Context? **Có khi publisher con được kết hợp đúng trong cùng chuỗi reactive; tự `subscribe()` rời rạc làm đứt mối liên hệ này.**
5. `timeout` thường tạo tín hiệu gì? **Hủy upstream và phát lỗi `TimeoutException` downstream nếu không có fallback.**
6. Hủy WebClient có đảo ngược việc server đã xử lý? **Không; cần quy tắc nghiệp vụ cho request có tác dụng phụ và kết quả mơ hồ.**
7. Dùng `ThreadLocal` thay Context cho mọi async request có an toàn? **Không; một request có thể chuyển thread, và thread có thể xử lý nhiều request khác nhau.**
8. Một correlation ID có đủ thay thế distributed tracing không? **Không; tracing còn cần span, quan hệ và ngữ cảnh truyền chuẩn.**

Trong triển khai thực, hãy thống nhất nơi tạo ID, cách kiểm tra ID inbound, cách mang ID trong Reactor Context, filter gắn outbound header, cách log/trace, deadline và điều kiện idempotency trước khi bật retry tự động.

## 11. Tài liệu tham khảo

- [Spring Framework — WebClient Context](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-context.html): hướng dẫn `Mono.deferContextual` và `contextWrite` trong filter.
- [Spring Framework — WebClient Attributes](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-attributes.html): phạm vi thuộc tính của một request.
- [Spring Framework — WebClient Filters](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-filter.html): `ExchangeFilterFunction` và cấu hình filter.
- [Project Reactor — Context](https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html): chiều truyền Context, ContextView, tính bất biến.
- [Project Reactor — Error Handling / doFinally](https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html): phân biệt kết thúc và hủy.
- [Project Reactor — Mono JavaDoc](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html): `doOnCancel`, `doFinally` và `timeout`.


</details>

- [Quay lại đầu trang](#back-to-top)
