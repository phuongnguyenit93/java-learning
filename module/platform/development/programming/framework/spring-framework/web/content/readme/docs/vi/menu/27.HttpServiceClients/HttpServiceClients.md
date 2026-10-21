<a id="back-to-top"></a>

# HTTP Service Client

## Menu
- [@HttpExchange và khai báo hợp đồng HTTP bằng interface](#http-service-clients-001)
- [@GetExchange, @PostExchange và ánh xạ tham số phương thức](#http-service-clients-002)
- [HttpServiceProxyFactory và adapter cho RestClient hoặc WebClient](#http-service-clients-003)
- [Kiểu trả về đồng bộ, reactive và phản hồi ResponseEntity](#http-service-clients-004)
- [Cấu hình header, URI, chuyển đổi dữ liệu và xử lý lỗi](#http-service-clients-005)
- [Quản lý nhiều dịch vụ, khả năng tương thích phiên bản và kiểm thử proxy](#http-service-clients-006)

## <a id="http-service-clients-001">@HttpExchange và khai báo hợp đồng HTTP bằng interface</a>

<details>
<summary>Xem chi tiết</summary>

# @HttpExchange và khai báo hợp đồng HTTP bằng interface

## 1. Mục tiêu và bối cảnh

Khi ứng dụng Java gọi một REST API khác, mã gọi thường phải lặp lại URL, HTTP method, path variable, query parameter, header, request body và kiểu response. **HTTP Service Client** của Spring Framework cho phép diễn tả các thông tin đó thành **một interface Java có annotation**, sau đó Spring tạo proxy thực hiện HTTP request qua client được cấu hình. Cơ chế này có từ Spring Framework 6.0; trong Spring Framework 6.1+ có thể dùng **RestClient**, còn **WebClient** hỗ trợ hướng reactive.

Sau bài này, bạn có thể:
- Định nghĩa hợp đồng HTTP rõ ràng bằng @HttpExchange, @GetExchange, @PostExchange, @PutExchange, @PatchExchange và @DeleteExchange.
- Truyền đúng path variable, query parameter, header và JSON body; chọn return type phù hợp.
- Dùng HttpServiceProxyFactory tạo proxy dựa trên RestClientAdapter hoặc WebClientAdapter.
- Hiểu luồng gọi HTTP, xử lý status/error, timeout, auth và các giới hạn của hợp đồng dạng interface.
- Chạy được ví dụ Java 17 + Spring Boot 3.3.x gọi một HTTP server cục bộ, không cần server bên ngoài.

**Ý tưởng:** interface định nghĩa “ứng dụng này cần gọi endpoint nào và trao đổi dữ liệu gì”; RestClient/WebClient chịu trách nhiệm **thực thi request thật**. Interface và proxy không tự triển khai business logic ở remote server.

## 2. Từ lời gọi thủ công đến hợp đồng interface

Lời gọi RestClient trực tiếp (ví dụ tích hợp, giả định BookDto tồn tại):

~~~java
BookDto book = restClient.get()
    .uri("/api/books/{id}", 42L)
    .header("Accept-Language", "vi")
    .retrieve()
    .body(BookDto.class);
~~~

Khi các lời gọi tăng lên, việc lặp lại chuỗi đường dẫn, tên header và cách giải mã dễ gây sai lệch. Một interface tập trung contract:

~~~java
@HttpExchange(url = "/api/books", accept = MediaType.APPLICATION_JSON_VALUE)
public interface BookHttpClient {

    @GetExchange("/{id}")
    BookDto findById(@PathVariable("id") long id,
                     @RequestHeader("Accept-Language") String language);

    @GetExchange
    List<BookDto> search(@RequestParam("q") String keyword);

    @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    BookDto create(@RequestBody CreateBookRequest body);
}
~~~

Các kiểu BookDto và CreateBookRequest thuộc ứng dụng. Đây là **interface contract**, chưa phải client có thể gọi: phải tạo RestClient/WebClient, adapter và proxy ở phần sau. So với mã thủ công, caller chỉ cần gọi:

~~~java
BookDto book = bookHttpClient.findById(42L, "vi");
~~~

Proxy đọc annotation và đối số của method để dựng HTTP request. Hợp đồng interface mang lại type checking tại thời điểm biên dịch cho chữ ký Java, nhưng **không đảm bảo remote endpoint thật sự tồn tại hoặc response luôn khớp DTO**. Những điều đó chỉ được xác minh bằng kiểm thử tích hợp hoặc khi chạy.

### Phân biệt các khái niệm

| Thành phần | Trách nhiệm |
| --- | --- |
| @HttpExchange / @GetExchange / @PostExchange | Khai báo HTTP method, URL, media type và mapping tham số |
| Interface Java | API phía client nhìn thấy, kiểu input/output và tên method |
| HttpServiceProxyFactory | Tạo đối tượng proxy triển khai interface ở runtime |
| RestClientAdapter | Nối proxy với **RestClient** đồng bộ |
| WebClientAdapter | Nối proxy với **WebClient** hỗ trợ reactive |
| RestClient / WebClient | Gửi request, encode/decode dữ liệu, xử lý transport, status và filters/interceptors |
| Remote server | Thực thi logic, kiểm tra quyền và trả response |

Không nhầm @HttpExchange với HTTP server do chính interface này tự khởi chạy. Spring MVC/WebFlux cũng có thể dùng interface @HttpExchange trên một @Controller triển khai contract, nhưng đó là **một cơ chế ánh xạ server khác**, không biến client proxy thành controller.

## 3. Annotation và quy tắc tạo URL

### 3.1. @HttpExchange và shortcut annotation

@HttpExchange dùng trên **type** để đặt phần cấu hình dùng chung; dùng trên **method** để khai báo một endpoint cụ thể. Thuộc tính tiêu biểu:
- **url** hoặc alias **value**: URI template hoặc path, ví dụ "/api/books/{id}".
- **method**: HTTP method (GET, POST, PUT, PATCH, DELETE, ...).
- **accept**: giá trị Accept mà client mong nhận.
- **contentType**: Content-Type của request body được gửi đi.

Các annotation chuyên biệt giúp dễ đọc hơn:

| Annotation | Method gửi đi | Ví dụ mục đích |
| --- | --- | --- |
| @GetExchange | GET | Tìm một tài nguyên, truy vấn danh sách |
| @PostExchange | POST | Tạo tài nguyên hoặc thực hiện command |
| @PutExchange | PUT | Thay thế trạng thái tài nguyên |
| @PatchExchange | PATCH | Cập nhật từng phần theo contract |
| @DeleteExchange | DELETE | Xóa tài nguyên |

Ví dụ sau minh họa vài dạng chữ ký; các kiểu request/response do ứng dụng định nghĩa:

~~~java
@HttpExchange(url = "/api/books", accept = MediaType.APPLICATION_JSON_VALUE)
interface BookClient {
    @GetExchange("/{id}")
    BookDto get(@PathVariable("id") long id);

    @PutExchange(url = "/{id}", contentType = MediaType.APPLICATION_JSON_VALUE)
    BookDto replace(@PathVariable("id") long id,
                    @RequestBody ReplaceBookRequest body);

    @PatchExchange(url = "/{id}", contentType = MediaType.APPLICATION_JSON_VALUE)
    BookDto patch(@PathVariable("id") long id,
                  @RequestBody PatchBookRequest body);

    @DeleteExchange("/{id}")
    void delete(@PathVariable("id") long id);
}
~~~

Các path trên tương đối với baseUrl của HTTP client. Chẳng hạn RestClient có baseUrl là "https://catalog.example" và interface khai báo "/api/books": GET "/{id}" với id=42 gửi GET tới "https://catalog.example/api/books/42". **Không tự cộng URL bằng string trong mã nghiệp vụ** khi đã có URI template và resolver.

Một method cũng có thể dùng @HttpExchange(method = "OPTIONS", url = "/api/books") nếu cần method không có shortcut. Các giá trị accept/contentType trên method có thể tinh chỉnh hoặc ghi đè cấu hình chung. Với request không có body, đừng đặt Content-Type chỉ vì response là JSON: Content-Type mô tả nội dung **request**, còn Accept mô tả response muốn nhận.

### 3.2. Truyền đối số

| Kiểu tham số | Tác dụng | Ví dụ |
| --- | --- | --- |
| @PathVariable("id") | Thay "{id}" trong URI template | /books/42 |
| @RequestParam("page") | Query parameter hoặc form parameter tùy Content-Type | ?page=2 |
| @RequestHeader("Authorization") | Thêm header | Bearer token |
| @RequestBody | Encode đối tượng thành body | JSON khi converter/encoder hỗ trợ |
| @CookieValue | Thêm cookie | locale=vi |
| @RequestPart | Một phần của multipart request | File hoặc metadata |
| URI | Cho phép truyền URI động, thay annotation URL | Endpoint tính ở runtime |
| HttpMethod | Cho phép chọn HTTP method ở runtime | GET/HEAD theo caller |

Ví dụ:

~~~java
@GetExchange("/search")
List<BookDto> search(
    @RequestParam("keyword") String keyword,
    @RequestParam("page") int page,
    @RequestHeader("X-Correlation-Id") String correlationId);
~~~

Một lời gọi search("spring web", 2, "trace-9") tạo request có query được encode thành tham số keyword, page và header tương ứng. **Không tự encode string query trước khi đưa vào @RequestParam** nếu URI builder/client đã làm công việc đó; encode hai lần gây sai dữ liệu.

Tham số tùy chọn phải được khai báo phù hợp với cơ chế Spring hỗ trợ, ví dụ @RequestParam(required = false) hoặc Optional<T> khi resolver chấp nhận. Đối với giá trị null, đừng mặc định nghĩ Spring sẽ tự loại bỏ tùy ý: resolver có quy tắc về required/optional, và một tham số bắt buộc null có thể gây lỗi.

**@RequestBody trên client** hướng dẫn serialize body của HTTP request; nó không tự kiểm tra ràng buộc nghiệp vụ ở phía server. Việc validation, Content-Type và JSON mapping phải thống nhất với remote API.

## 4. Tạo proxy từ RestClient hoặc WebClient

### 4.1. RestClient: lời gọi đồng bộ

Spring Framework 6.1+:

~~~java
RestClient restClient = RestClient.builder()
    .baseUrl("https://catalog.example")
    .defaultHeader("Accept", "application/json")
    .build();

RestClientAdapter adapter = RestClientAdapter.create(restClient);

HttpServiceProxyFactory factory = HttpServiceProxyFactory
    .builderFor(adapter)
    .build();

BookHttpClient books = factory.createClient(BookHttpClient.class);
BookDto book = books.findById(42L, "vi");
~~~

Đây là đoạn tích hợp: BookHttpClient, BookDto phải có định nghĩa tương ứng. **Gọi books.findById(...) sẽ thực hiện HTTP request đồng bộ trên thread gọi**. RestClient nên được tạo/cấu hình một lần và sử dụng lại qua proxy, ví dụ đăng ký dưới dạng Spring bean. Không tạo client/factory mới cho từng request nghiệp vụ nếu không có lý do rõ ràng.

Ví dụ cấu hình Spring @Configuration sử dụng interface BookHttpClient của ứng dụng:

~~~java
@Configuration
class CatalogClientConfig {

    @Bean
    BookHttpClient bookHttpClient(RestClient.Builder builder) {
        RestClient client = builder
            .baseUrl("https://catalog.example")
            .build();

        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(client))
            .build()
            .createClient(BookHttpClient.class);
    }
}
~~~

RestClient.Builder có thể được Spring Boot cung cấp khi có cấu hình/phụ thuộc phù hợp. Nếu ứng dụng không dùng Spring Boot, có thể gọi RestClient.builder() trực tiếp. Trong Spring Boot 3.3, **khai báo interface có annotation không mặc nhiên biến nó thành bean**: bạn vẫn cần tạo proxy hoặc một cơ chế đăng ký được cấu hình riêng.

### 4.2. WebClient: kiểu reactive

Nếu ứng dụng dùng Reactor và cần non-blocking I/O, tạo WebClientAdapter:

~~~java
WebClient webClient = WebClient.builder()
    .baseUrl("https://catalog.example")
    .build();

HttpServiceProxyFactory factory = HttpServiceProxyFactory
    .builderFor(WebClientAdapter.create(webClient))
    .build();

ReactiveBookClient books = factory.createClient(ReactiveBookClient.class);
Mono<BookDto> response = books.findById(42L);
~~~

Interface tương ứng:

~~~java
@HttpExchange("/api/books")
interface ReactiveBookClient {
    @GetExchange("/{id}")
    Mono<BookDto> findById(@PathVariable("id") long id);

    @GetExchange
    Flux<BookDto> streamBooks();
}
~~~

Đây là các đoạn tích hợp minh họa: BookDto và các biến thuộc ứng dụng; import cần thiết nằm trong org.springframework.web.reactive.function.client.* và reactor.core.publisher.*.

Với WebClient, Mono/Flux biểu diễn pipeline reactive; HTTP request thường được thực hiện **khi có subscriber**. Trong controller WebFlux, nên trả Mono/Flux cho framework thay vì gọi block() trên event-loop. Flux chỉ phù hợp khi API/protocol và decoder thực sự hỗ trợ luồng phần tử (ví dụ NDJSON hoặc SSE phù hợp); không mặc định rằng mọi endpoint trả JSON đều streaming.

**Chọn return type phải phù hợp với adapter.** RestClientAdapter dành cho kết quả synchronous như BookDto, ResponseEntity<BookDto>, HttpHeaders hoặc void; không dùng return type Mono/Flux với adapter chỉ hỗ trợ synchronous. WebClientAdapter hỗ trợ các kiểu reactive như Mono<T>, Flux<T> và các biến thể ResponseEntity, đồng thời có thể xử lý một số kiểu đồng bộ theo adapter, nhưng gọi đồng bộ trên reactive stack có thể gây blocking không mong muốn.

## 5. Chọn kiểu trả về theo contract

| Return type | Khi sử dụng |
| --- | --- |
| BookDto | Chỉ cần body đã decode; thích hợp client đồng bộ |
| List<BookDto> | Endpoint trả một JSON array và decoder hỗ trợ generic |
| ResponseEntity<BookDto> | Cần status, headers và body |
| ResponseEntity<Void> | Cần status/headers của response không có body |
| HttpHeaders | Chỉ cần header |
| void | Không dùng response body |
| Mono<BookDto> | Một kết quả bất đồng bộ trên WebClientAdapter |
| Flux<BookDto> | Luồng phần tử khi server và codec hỗ trợ |
| Mono<ResponseEntity<BookDto>> | Phản hồi reactive có status, headers và body |

Ví dụ chấp nhận 204 No Content từ DELETE:

~~~java
@DeleteExchange("/{id}")
ResponseEntity<Void> delete(@PathVariable("id") long id);
~~~

Caller có thể kiểm tra status. Tuy nhiên với status 4xx/5xx, **client mặc định phát exception**, vì vậy đừng nghĩ ResponseEntity sẽ tự động trả về mọi mã lỗi. Muốn xử lý error status theo contract phải cấu hình underlying RestClient/WebClient.

Với collection hoặc generic kiểu List<BookDto>, proxy lấy được generic return type từ chữ ký interface. Nếu gọi thủ công bằng client fluent, bạn có thể cần ParameterizedTypeReference ở những chỗ generic không thể suy ra từ Class. Một DTO record/class cần Jackson hoặc codec tương thích để mapping JSON. Không thể đảm bảo mọi trường hợp dữ liệu lạ, thiếu field hoặc sai kiểu đều được deserialize thành công chỉ vì interface biên dịch.

## 6. Demo Java 17 chạy được: GET, POST, path, query, header và JSON

Ví dụ tự dựng HTTP server nhỏ ngay trong JVM, nên có thể chạy offline đối với API đích sau khi Maven đã tải dependency. Dùng **Java 17+**, **Maven** và **Spring Boot 3.3.13** (bao gồm Spring Framework 6.1.x). Với dự án đang phát triển mới, chọn phiên bản Spring Boot còn được hỗ trợ theo chính sách của đội ngũ; version này được ghim chỉ để minh họa API Spring 6.1.

Cấu trúc:

~~~text
http-exchange-demo/
├── pom.xml
└── src/main/java/demo/HttpExchangeDemo.java
~~~

**pom.xml**:

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
    <artifactId>http-exchange-demo</artifactId>
    <version>1.0.0</version>
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
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/demo/HttpExchangeDemo.java**:

~~~java
package demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

public class HttpExchangeDemo {

    public record Item(long id, String name) {}
    public record NewItem(String name) {}

    @HttpExchange(url = "/api/items", accept = MediaType.APPLICATION_JSON_VALUE)
    public interface ItemHttpClient {

        @GetExchange("/{id}")
        Item findById(@PathVariable("id") long id,
                      @RequestParam("lang") String lang);

        @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
        Item create(@RequestBody NewItem body,
                    @RequestHeader("X-Request-Id") String requestId);
    }

    public static void main(String[] args) throws Exception {
        AtomicInteger received = new AtomicInteger();
        HttpServer server = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/api/items", exchange -> {
            received.incrementAndGet();
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getRawQuery();
            String json;
            int status;

            if ("GET".equals(method)
                    && "/api/items/42".equals(path)
                    && "lang=vi".equals(query)) {
                status = 200;
                json = "{\"id\":42,\"name\":\"Notebook\"}";
            } else if ("POST".equals(method)
                    && "/api/items".equals(path)
                    && "op-001".equals(
                        exchange.getRequestHeaders().getFirst("X-Request-Id"))
                    && !new String(exchange.getRequestBody().readAllBytes(),
                                  StandardCharsets.UTF_8).isBlank()) {
                status = 201;
                json = "{\"id\":99,\"name\":\"Notebook\"}";
            } else {
                status = 404;
                json = "{\"error\":\"Not found\"}";
            }

            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();

        try {
            RestClient restClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .build();

            ItemHttpClient api = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(ItemHttpClient.class);

            Item found = api.findById(42L, "vi");
            Item created = api.create(new NewItem("Notebook"), "op-001");

            System.out.println("GET: " + found);
            System.out.println("POST: " + created);
            System.out.println("HTTP requests: " + received.get());
        } finally {
            server.stop(0);
        }
    }
}
~~~

Chạy từ thư mục chứa pom.xml:

~~~bash
mvn -q compile
mvn -q exec:java -Dexec.mainClass=demo.HttpExchangeDemo
~~~

**Các dòng stdout do chương trình in:**

~~~text
GET: Item[id=42, name=Notebook]
POST: Item[id=99, name=Notebook]
HTTP requests: 2
~~~

Maven hoặc thư viện có thể xuất thêm log tùy môi trường. Bài demo đã mô tả tường minh hai response của HTTP server cục bộ để kết quả nghiệp vụ xác định. Cổng được hệ điều hành cấp động để tránh xung đột cổng. Không có Spring Boot application context trong ví dụ: đây là **Spring HTTP client chạy từ main Java**. Request đầu dùng URI template /api/items/{id}, query param lang=vi; request sau gửi POST JSON body và X-Request-Id. Proxy dùng RestClientAdapter và Jackson (qua starter-web) để encode/decode record.

Một điểm cần lưu ý: trong demo, header X-Request-Id **chỉ là dấu hiệu cho phép server demo nhận request**; nó chưa tạo khả năng chống trùng POST ở server thực. Muốn chống trùng tác dụng phụ phải triển khai idempotency contract với lưu trạng thái và kiểm soát đồng thời, không chỉ gửi header.

### Quan sát luồng thực thi

~~~mermaid
sequenceDiagram
    participant App as Java caller
    participant Proxy as ItemHttpClient proxy
    participant Client as RestClientAdapter + RestClient
    participant Server as HTTP Server
    App->>Proxy: findById(42, "vi")
    Proxy->>Client: GET /api/items/42?lang=vi
    Client->>Server: HTTP GET
    Server-->>Client: 200 + JSON Item
    Client-->>Proxy: decode Item
    Proxy-->>App: Item(42, "Notebook")
    App->>Proxy: create(NewItem, "op-001")
    Proxy->>Client: POST /api/items + JSON + header
    Client->>Server: HTTP POST
    Server-->>Client: 201 + JSON Item
    Client-->>App: Item(99, "Notebook")
~~~

Phần **Proxy → Client** ở sơ đồ là lời gọi nội bộ cùng JVM; chỉ mũi tên **Client → Server** đi qua HTTP. Annotation không phải code chạy ở server; server chỉ nhìn thấy HTTP request thông thường.

## 7. Lỗi, status, timeout và bảo mật

### 7.1. Không che mất status lỗi

Khi dùng RestClientAdapter, xử lý status tuân theo **RestClient**. Mặc định 4xx/5xx thường dẫn đến RestClientResponseException hoặc subclass tương ứng (chẳng hạn HttpClientErrorException). Với WebClientAdapter, WebClient có WebClientResponseException trên lỗi HTTP (theo cơ chế mặc định). Các lỗi mạng/timeout và lỗi JSON mapping có nhóm exception khác.

Chủ động cấu hình handler cho RestClient nếu muốn ánh xạ status thành lỗi nghiệp vụ. Ví dụ **snippet tích hợp**:

~~~java
RestClient client = RestClient.builder()
    .baseUrl("https://catalog.example")
    .defaultStatusHandler(
        status -> status.value() == 404,
        (request, response) -> {
            throw new CatalogItemNotFoundException(request.getURI().toString());
        })
    .build();
~~~

CatalogItemNotFoundException là custom runtime exception của ứng dụng. Đoạn này không phải chương trình độc lập. Với WebClient, có thể dùng WebClient.Builder.defaultStatusHandler(...). Quan trọng là cấu hình ở underlying client được proxy sử dụng; interface @HttpExchange **không tự động có cơ chế retry, fallback hoặc status mapping riêng**.

### 7.2. Những trách nhiệm vận hành cần cấu hình

- **Timeout:** dùng timeout của transport/HTTP client phù hợp, đồng thời cân đối thời gian tối đa của nghiệp vụ. Tránh request vô hạn trên đường gọi đồng bộ.
- **Retry:** chỉ retry lỗi tạm thời và thao tác an toàn để lặp. GET thường là ứng viên, nhưng POST tạo đơn/thanh toán có thể gây tác dụng phụ trùng nếu response mất.
- **Idempotency:** nếu phải retry POST, server cần cơ chế xử lý trùng như Idempotency-Key + unique constraint/transaction/deduplication; header tự nó không đảm bảo exactly-once.
- **Authentication:** cung cấp token/API key qua filter/interceptor, @RequestHeader hoặc cơ chế bảo mật được cấu hình. Không hardcode secret vào interface, source code hay log.
- **Observability:** record HTTP method, host/service, status, độ trễ, correlation ID và số lần retry; che thông tin nhạy cảm.
- **URI/baseUrl:** kiểm soát target host. Tham số URI động lấy từ input không tin cậy phải kiểm tra để tránh gọi sang địa chỉ nội bộ không được phép.
- **Connection pool và giới hạn:** client dùng lại kết nối, giới hạn số request đồng thời, timeout và retry budget theo tải thực tế.
- **Serialization:** phải có converter/codec phù hợp (Jackson cho JSON thường gặp), cấu hình naming/date formats thống nhất với server.

Không đưa retry vào mọi lời gọi của interface chỉ vì nhìn thấy cùng một annotation: retry policy phụ thuộc phương thức và hợp đồng nghiệp vụ.

## 8. Thiết kế interface theo API thực tế

Một HTTP interface tốt thường đại diện cho một **remote service hoặc nhóm endpoint liên quan** với tên method thể hiện kết quả rõ ràng. Đặt baseUrl/host trong cấu hình, giữ path và contract trong interface; DTO thể hiện wire format, tách khỏi entity JPA và model nội bộ khi vòng đời thay đổi độc lập.

Ví dụ tổ chức:

~~~text
client/catalog/
├── CatalogHttpClient.java   # @HttpExchange contract
├── CatalogClientConfig.java # proxy, baseUrl, auth, timeout
├── dto/
│   ├── BookDto.java
│   └── CreateBookRequest.java
└── CatalogFacade.java       # nghiệp vụ gọi remote, error mapping
~~~

Mã gọi trong CatalogFacade phụ thuộc **CatalogHttpClient** nên có thể test bằng fake/mock interface hoặc kiểm thử proxy với mock HTTP server. Chỉ mock interface đơn thuần **không chứng minh URL, HTTP method, query, header và JSON thực tế đúng**; cần integration test kiểm tra request mà client sinh ra.

Các lựa chọn thiết kế:
1. **RestClient + synchronous return type**: phù hợp ứng dụng MVC, batch, service đồng bộ với luồng xử lý blocking đã được cân nhắc.
2. **WebClient + Mono/Flux**: phù hợp luồng WebFlux, tác vụ concurrent hoặc streaming với end-to-end reactive.
3. **RestClient/WebClient fluent trực tiếp**: phù hợp khi cần request động không tự nhiên dưới một interface (method/URI/body quá linh hoạt), hoặc số call ít.
4. **@HttpExchange interface**: phù hợp remote API có contract tương đối ổn định, nhiều endpoint và cần API Java rõ ràng, thống nhất.

Không nên biến mọi URL thành một method generic nhận Object rồi đánh mất type safety. Ngược lại, interface cũng không nên chứa retry logic, cache policy, xử lý database và điều phối nghiệp vụ; để những phần đó ở client configuration/service layer.

## 9. Lỗi phổ biến và cách tự kiểm tra

| Sai sót | Hệ quả | Cách sửa |
| --- | --- | --- |
| Chỉ khai báo interface mà không tạo proxy | Không có bean để inject hoặc không thể gọi thực | Tạo adapter + factory + createClient hoặc cấu hình đăng ký chính thức |
| Dùng Mono<T> với RestClientAdapter | Return type không phù hợp adapter đồng bộ | Chuyển sang WebClientAdapter hoặc dùng T/ResponseEntity<T> |
| Thiếu @PathVariable cho "{id}" | URI template không được mở rộng đúng | Khai báo @PathVariable("id") rõ ràng |
| Trộn Accept với Content-Type | Server có thể giải mã sai body | Accept cho response mong muốn; Content-Type cho request body |
| Dùng @RequestParam cho JSON object body | Body không theo contract | Dùng @RequestBody và converter JSON |
| Tưởng ResponseEntity<T> nhận mọi 404/500 | Client ném exception khi gặp lỗi | Cấu hình status handling theo hợp đồng |
| Bật retry tự động cho POST ghi | Có thể tạo nhiều đơn hoặc giao dịch | Áp dụng idempotency được server thực thi |
| Gọi block() trong reactive request handler | Chặn event loop, gây suy giảm throughput | Trả Mono/Flux |
| Dùng chung DTO entity DB với wire model | Thay schema nội bộ làm vỡ remote contract | Tạo DTO theo phiên bản HTTP API |
| Chỉ viết unit test mock interface | Không phát hiện URI/header/body mapping sai | Test proxy thật với mock HTTP server |
| Coi @HttpExchange là tự tạo remote endpoint | Server không thực sự tồn tại | Phân biệt client proxy với @Controller implementation |

**Checklist kiểm thử tối thiểu:** xác nhận HTTP method và URL; path/query chứa ký tự đặc biệt; headers/auth; serialization và mapping response; 2xx/204; 400/401/404/429/5xx; transport timeout; trường hợp body lỗi schema; request retry khi có/không có idempotency; tạo proxy/bean khi ứng dụng khởi động. Với WebClient, kiểm thử subscriber/cancellation và không block event loop khi thích hợp.

## 10. Kết luận kiến thức

@HttpExchange biến một remote HTTP contract thành **Java interface dễ đọc và dễ dùng**; HttpServiceProxyFactory sinh proxy để thực thi contract trên **RestClient** hoặc **WebClient**. Annotation mô tả request, adapter quyết định cơ chế gọi, DTO mô tả dữ liệu, còn server và client configuration mới quyết định xác thực, xử lý lỗi, timeout, retry và tính an toàn của tác dụng phụ. Vì thế, type-safe interface làm mã gọi gọn và nhất quán nhưng vẫn cần test HTTP thực và hợp đồng nghiệp vụ đáng tin cậy.

## Tài liệu chính thức

- Spring Framework Reference, HTTP Service Clients: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-http-interface
- Spring Framework 6.1 API, @HttpExchange: https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/service/annotation/HttpExchange.html
- Spring Framework 6.1 API, HttpServiceProxyFactory: https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/service/invoker/HttpServiceProxyFactory.html
- Spring Framework 6.1 API, RestClientAdapter: https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/client/support/RestClientAdapter.html
- Spring Framework Reference, WebClient: https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-service-clients-002">@GetExchange, @PostExchange và ánh xạ tham số phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# @GetExchange, @PostExchange và ánh xạ tham số phương thức trong Spring HTTP Service Client

## 1. Bài học giải quyết vấn đề gì?

Khi một service Java thường xuyên gọi HTTP API, cách viết thủ công với `RestClient.get().uri(...).retrieve()` hoặc `WebClient.post().bodyValue(...).retrieve()` có thể lặp lại ở nhiều nơi. **HTTP Service Client** của Spring Framework cho phép mô tả các endpoint bên ngoài bằng **Java interface có annotation**, sau đó Spring tạo proxy tự xây và thực hiện HTTP request mỗi khi bạn gọi phương thức tương ứng.

Trọng tâm bài này:

- `@GetExchange` biểu diễn request HTTP **GET**; `@PostExchange` biểu diễn HTTP **POST**.
- `@PathVariable`, `@RequestParam`, `@RequestHeader`, `@CookieValue`, `@RequestBody` và `@RequestPart` quyết định tham số Java được đặt ở **đâu** trong HTTP request.
- `@HttpExchange` định nghĩa cấu hình chung, `HttpServiceProxyFactory` biến interface thành client chạy được, thông qua `RestClientAdapter` hoặc `WebClientAdapter`.
- Chọn giá trị trả về đồng bộ/reactive, xử lý lỗi, và tránh những hiểu lầm khi dùng các annotation giống annotation của Spring MVC controller.

Phạm vi ví dụ chính: **Java 17+, Spring Framework 6.1.x (Spring Boot 3.3.x)**. HTTP Service Client có từ Spring Framework 6.0; `RestClient` và cách khởi tạo `HttpServiceProxyFactory.builderFor(...)` được dùng dưới đây phù hợp với Spring 6.1+. Những tiện ích mới hơn của Spring 7 có thể khác; không cần chúng cho bài này.

## 2. Luồng hoạt động: interface → proxy → HTTP request

```text
Mã nghiệp vụ: notesApi.find(42, "vi", "req-123")
   │
   ▼
Proxy tạo bởi HttpServiceProxyFactory
   │  Đọc @GetExchange và annotation từng tham số
   ├─ GET + URL "/notes/{id}" → "/notes/42"
   ├─ @RequestParam("lang")   → "?lang=vi"
   └─ @RequestHeader("X-Trace-Id") → HTTP header
   │
   ▼
RestClientAdapter hoặc WebClientAdapter
   │
   ▼
RestClient / WebClient → HTTP transport → máy chủ
   │
   ▼
Response → decode → kiểu trả về khai báo trên interface
```

**Cần phân biệt:**

1. Annotation trên interface **mô tả yêu cầu HTTP**. Nó không tự mở socket hay chạy server.
2. `HttpServiceProxyFactory.createClient(YourInterface.class)` tạo đối tượng proxy; khi gọi phương thức, proxy sẽ tạo request từ metadata của phương thức và argument thực tế.
3. `RestClientAdapter` dùng API đồng bộ `RestClient`; `WebClientAdapter` dùng client reactive `WebClient`. Interface không bị khóa vào một thư viện HTTP cụ thể, nhưng **kiểu trả về** phải phù hợp adapter.
4. Các annotation `@GetExchange`, `@PostExchange` đều là dạng chuyên biệt của `@HttpExchange`. Đây là **client-side contract**; mặc dù Spring cho phép một số interface dùng chung cho controller, bài này tập trung vào phía **gọi API**.

## 3. Khai báo endpoint với @HttpExchange, @GetExchange và @PostExchange

Ví dụ một dịch vụ ghi chú:

```java
package demo;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(url = "/api/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface NotesHttpClient {

    @GetExchange("/notes/{id}")
    NoteDto find(
            @PathVariable("id") long id,
            @RequestParam("lang") String language,
            @RequestHeader("X-Trace-Id") String traceId);

    @PostExchange(
            url = "/notes",
            contentType = MediaType.APPLICATION_JSON_VALUE)
    NoteDto create(
            @RequestBody CreateNoteRequest request,
            @RequestHeader("X-Trace-Id") String traceId);
}

record NoteDto(long id, String text) {}
record CreateNoteRequest(String text) {}
```

Đây là **minh họa contract**: DTO phải được đặt ở vị trí Java truy cập được; cần JSON codec/message converter (ví dụ Jackson) ở runtime. Chương trình chạy độc lập đầy đủ nằm tại mục 8.

Đọc declaration theo thứ tự:

- `@HttpExchange(url="/api/v1", accept="application/json")` ở **cấp interface** áp dụng cấu hình chung cho các phương thức: tiền tố đường dẫn và định dạng response mong đợi qua header `Accept`.
- `@GetExchange("/notes/{id}")` chọn HTTP method GET và nối đường dẫn với tiền tố. `{id}` là URI template variable cần điền từ `@PathVariable("id")`.
- `@PostExchange(url="/notes", contentType="application/json")` chọn POST, chỉ định `Content-Type` của request có body.
- `accept` diễn đạt định dạng **response muốn nhận**; `contentType` diễn đạt định dạng **request body gửi đi**. Hai thuộc tính không đồng nghĩa. Nếu bên nhận hỗ trợ nhiều dạng, `accept` có thể chứa nhiều media types.
- `@GetExchange(value="/notes/{id}")` và `@GetExchange(url="/notes/{id}")` tương đương về đường dẫn; `value` là alias của `url`. Tương tự cho `@PostExchange`.

Các HTTP shortcut khác gồm `@PutExchange`, `@PatchExchange` và `@DeleteExchange`. Khi cần HTTP method cụ thể, dùng shortcut tương ứng giúp interface tự mô tả hơn so với `@HttpExchange(method="GET")`.

**HTTP semantics:** GET dùng để lấy thông tin, thường phải an toàn và idempotent; POST thường tạo tài nguyên hoặc kích hoạt thao tác thay đổi trạng thái. Việc gắn annotation **không khiến backend tự tuân thủ semantics**: chính API server phải thực hiện đúng. Không dùng GET để thực hiện giao dịch có side effect chỉ vì dễ gọi.

## 4. Bảng ánh xạ tham số Java sang vị trí của HTTP request

| Khai báo trong interface | Thành phần HTTP | Minh họa kết quả |
| --- | --- | --- |
| `@PathVariable("id") long id` | Thay `{id}` trong URL path | `/notes/42` |
| `@RequestParam("lang") String lang` | Query string (thông thường) | `?lang=vi` |
| `@RequestParam("tag") List<String> tags` | Nhiều query/form values cùng tên | `?tag=java&tag=spring` |
| `@RequestHeader("X-Trace-Id") String id` | Request header | `X-Trace-Id: req-123` |
| `@CookieValue("session") String token` | Request cookie | `Cookie: session=...` |
| `@RequestBody CreateNoteRequest body` | Toàn bộ request body | JSON khi cấu hình JSON |
| `@RequestPart("file") Resource file` | Một phần trong multipart body | `multipart/form-data` |
| `@RequestAttribute("key") Object attr` | Request attribute nội bộ client | Không tự biến thành header/query |
| `URI uri` (không annotation) | URL động, override URL annotation | URL runtime |
| `UriBuilderFactory factory` (không annotation) | Factory tạo URI cho invocation | Điều khiển base URL/encoding |
| `HttpMethod method` (không annotation) | Override HTTP method | Chỉ dùng khi thực sự cần động |

Các giá trị `String`, số, enum có thể được convert theo cơ chế Spring. `@RequestParam` và `@RequestHeader` có thể nhận `Collection`, `Map`, `MultiValueMap` với quy tắc phù hợp; hãy kiểm tra server có chấp nhận nhiều giá trị cùng tên hay không. `@PathVariable` cũng hỗ trợ map chứa nhiều biến đường dẫn.

**Cực kỳ quan trọng:** `@RequestParam` trong client không có nghĩa “tham số form” trong mọi tình huống. Với `Content-Type: application/x-www-form-urlencoded`, tham số được gửi ở **form body**; các request khác dùng query string. Không dựa vào tên annotation mà đoán vị trí vật lý.

### 4.1 @PathVariable: điền URI template

```java
@GetExchange("/users/{userId}/orders/{orderId}")
String getOrder(
        @PathVariable("userId") long userId,
        @PathVariable("orderId") String orderId);
```

Gọi `getOrder(15, "A-9")` tạo đường dẫn logic `/users/15/orders/A-9` (cộng tiền tố ở interface và base URL của client). Tên tham số `"userId"` và `"orderId"` phải khớp placeholder. Nếu mismatch, request có thể thất bại khi resolve URI; kiểm tra ngay trong test.

Nên **ghi rõ tên annotation** như `@PathVariable("userId")` thay vì `@PathVariable long userId` để không lệ thuộc cấu hình trình biên dịch giữ tên parameter (`-parameters`). Giá trị dynamic được URI template xử lý encoding; không tự nối chuỗi URL từ input tùy ý.

### 4.2 @RequestParam: query và form-urlencoded

GET thông dụng:

```java
@GetExchange("/books")
List<BookDto> search(
        @RequestParam("q") String keyword,
        @RequestParam("page") int page,
        @RequestParam(value = "lang", required = false) String language);
```

Gọi `search("spring web", 2, null)` có ý nghĩa query chứa `q=spring%20web&page=2` (biểu diễn percent-encoding thực tế tùy URI builder). `lang` không có khi giá trị được phép bỏ qua. Không khẳng định rằng mọi space nhất định được encode thành `%20`: một số chiến lược form/query có thể dùng `+`. Dùng `UriComponentsBuilder`/URI template thay cho nối `?q=` thủ công.

POST form:

```java
@PostExchange(
        url = "/oauth/token",
        contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
String exchangeToken(
        @RequestParam("grant_type") String grantType,
        @RequestParam("code") String code);
```

Ở đây `grant_type` và `code` thuộc **body dạng form** (ví dụ `grant_type=authorization_code&code=abc`), **không phải JSON**. Với JSON body, dùng `@RequestBody` thay vì lạm dụng `@RequestParam`.

`@RequestParam("tag") List<String> tags` và `MultiValueMap<String, String>` hữu ích cho filter có nhiều giá trị; khả năng parse ở server phải được kiểm thử bằng request thật.

### 4.3 @RequestHeader và @CookieValue

```java
@GetExchange("/profile")
String profile(
        @RequestHeader("Authorization") String bearerToken,
        @RequestHeader("X-Request-Id") String requestId,
        @CookieValue("locale") String locale);
```

Header này **chỉ được gửi** theo argument khi gọi method; không có tự động đăng nhập. Ví dụ production thường thích cấu hình authorization trong `RestClient.Builder` interceptor hoặc `WebClient.Builder` filter / security integration, vì gửi raw token qua mọi method signature dễ lặp và dễ lộ trong log.

`@RequestHeader` trong HTTP interface có thể **thêm** giá trị vào header đã tồn tại ở client; đừng mặc định nó ghi đè header mặc định. Header lặp như `Authorization` có thể gây lỗi hoặc rủi ro; kiểm tra cấu hình thực tế. `@CookieValue` thêm Cookie phía client, không đọc cookie từ incoming request của server trừ khi ứng dụng chủ động chuyển tiếp.

### 4.4 @RequestBody: gửi toàn bộ entity

```java
record CreateUser(String name, String email) {}
record UserView(long id, String name) {}

@PostExchange(
        url = "/users",
        contentType = MediaType.APPLICATION_JSON_VALUE,
        accept = MediaType.APPLICATION_JSON_VALUE)
UserView createUser(@RequestBody CreateUser payload);
```

JSON serialized (với Jackson):

```http
POST /users HTTP/1.1
Content-Type: application/json
Accept: application/json

{"name":"Lan","email":"lan@example.org"}
```

`@RequestBody` xác định toàn bộ HTTP payload, không rải từng thuộc tính object thành query parameter. Payload `record` được encode bởi HTTP message converters (RestClient) hoặc codecs (WebClient). Nếu thiếu JSON codec, sai `Content-Type` hoặc server trả JSON không tương thích DTO, lỗi encode/decode có thể xuất hiện.

Với WebClient, `@RequestBody Mono<CreateUser>` cũng có thể gửi dữ liệu reactive; nhưng không dùng publisher/reactive return types với `RestClientAdapter` rồi kỳ vọng nó tự chuyển thành reactive.

### 4.5 @RequestPart: multipart upload

```java
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.service.annotation.PostExchange;

// Fragment bên trong HTTP service interface
@PostExchange(
        url = "/attachments",
        contentType = MediaType.MULTIPART_FORM_DATA_VALUE)
String upload(
        @RequestPart("description") String description,
        @RequestPart("file") Resource file);
```

`@RequestPart` ánh xạ *một phần* của multipart body, mỗi part có tên và có thể có Content-Type riêng. `Resource` thường đại diện file; `HttpEntity<?>` giúp đặt header riêng cho part; client hỗ trợ cũng có thể nhận `MultipartFile` hay Spring `Part` tùy adapter và phiên bản. Không dùng `@RequestBody` JSON thuần đồng thời với nhiều `@RequestPart` rồi kỳ vọng Spring tự ghép thành multipart. Cần kiểm tra giới hạn file size, streaming, bộ đệm và bảo vệ filename thực tế.

### 4.6 Tham số tùy chọn và giá trị null

Tham số không được tự do truyền `null`: framework cần biết annotation nào cho phép `required=false` hoặc tham số được xác định là optional (ví dụ `Optional<T>`). Nếu một field là tùy chọn, thể hiện rõ contract:

```java
@GetExchange("/books")
String list(@RequestParam(value = "category", required = false)
            String category);
```

Khi người dùng không chọn category, phương thức có thể nhận `null` và **bỏ qua** parameter này. Với dữ liệu bắt buộc, validate ở business boundary trước khi gọi HTTP client để phát hiện sớm hơn thay vì trông đợi proxy giải quyết ràng buộc nghiệp vụ.

## 5. Từ interface thành HTTP client thật với HttpServiceProxyFactory

HTTP interface **không được triển khai bằng `new NotesHttpClient()`**; cần proxy. Với Spring Framework 6.1 và `RestClient`:

```java
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

RestClient restClient = RestClient.builder()
        .baseUrl("https://notes.example.org")
        .defaultHeader("X-App", "learning")
        .build();

RestClientAdapter adapter = RestClientAdapter.create(restClient);
HttpServiceProxyFactory factory =
        HttpServiceProxyFactory.builderFor(adapter).build();

NotesHttpClient notes = factory.createClient(NotesHttpClient.class);
NoteDto note = notes.find(42L, "vi", "req-123");
```

Đây là fragment dành cho project đã có `NotesHttpClient` và `NoteDto` ở mục 3; `notes.example.org` chỉ là host minh họa, không chạy thật. **Lưu ý `builderFor(adapter)`:** đây là cách khởi tạo phù hợp Spring 6.1+, thay cho `HttpServiceProxyFactory.builder(adapter)` đã deprecated ở 6.1.

Trong Spring Boot, có thể đăng ký client proxy làm bean:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
class RemoteNotesConfiguration {

    @Bean
    NotesHttpClient notesHttpClient(RestClient.Builder builder) {
        RestClient client = builder
                .baseUrl("https://notes.example.org")
                .build();

        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(client))
                .build()
                .createClient(NotesHttpClient.class);
    }
}
```

Đây là **Spring configuration fragment**. Inject `RestClient.Builder` do Boot quản lý để tận dụng các tùy chỉnh chung nếu có. Tái sử dụng bean proxy và underlying client; không dựng lại factory cho mỗi lần gọi. Chỉ định rõ base URL theo environment/configuration thực tế, timeout, TLS, authentication, log/metrics và retry phù hợp.

### Chọn WebClient khi cần reactive

```java
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;

interface ReactiveNotesClient {
    @GetExchange("/notes/{id}")
    Mono<String> find(@org.springframework.web.bind.annotation.PathVariable("id")
                      long id);
}

WebClient webClient = WebClient.builder()
        .baseUrl("https://notes.example.org")
        .build();

ReactiveNotesClient reactive =
        HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(webClient))
                .build()
                .createClient(ReactiveNotesClient.class);

Mono<String> publisher = reactive.find(42L);
```

Fragment trên dùng trong phương thức Java (không thể đặt các lệnh tạo biến rời rạc trực tiếp tại cấp file). Cần `spring-webflux` và HTTP connector phù hợp. `Mono<String>` là publisher: request thực tế chạy khi subscribe. Trong WebFlux handler, trả publisher; không tự `block()` trên event-loop. Với `RestClientAdapter`, signature kiểu `String`, `List<T>`, `ResponseEntity<T>` hoặc `void` là tự nhiên; với `WebClientAdapter`, hỗ trợ cả `Mono<T>` và `Flux<T>`.

## 6. Ánh xạ tham số: các tình huống dễ hiểu nhầm

| Tình huống | Code ý định | HTTP được sinh | Cách xử lý đúng |
| --- | --- | --- | --- |
| GET item theo id | `@GetExchange("/items/{id}")` + `@PathVariable("id")` | GET `/items/7` | Khớp chính xác tên biến |
| GET filter | `@RequestParam("sort")` | Query `?sort=newest` | Encode user input, không nối raw URL |
| POST JSON | `@PostExchange(contentType="application/json")` + `@RequestBody` | JSON ở body | Có JSON codec |
| POST HTML form | `@PostExchange(contentType="application/x-www-form-urlencoded")` + `@RequestParam` | Các cặp key=value ở body | Server phải parse form |
| POST upload | `@PostExchange(contentType="multipart/form-data")` + `@RequestPart` | Multipart body | Chỉ định part names đúng |
| Header động | `@RequestHeader("X-Trace-Id")` | HTTP header | Tránh duplicate / log secret |
| URL động | `URI uri` | URI runtime thay url tĩnh | Validate target host trước |

**Không nên suy luận các annotation giống hệt cơ chế xử lý argument của Spring MVC controller.** Đây là chiều **gửi request**, nên `@RequestBody` serialize để gửi (không deserialize request từ client); `@RequestHeader` thêm request header gửi đi (không đọc incoming header). Ngoài ra, không phải mọi annotation server-side như `@ModelAttribute` hay `@RequestMapping` có cùng ý nghĩa trên HTTP Service Client.

### Dynamic URI và method (trường hợp nâng cao)

```java
import java.net.URI;
import org.springframework.http.HttpMethod;
import org.springframework.web.service.annotation.HttpExchange;

interface DynamicClient {
    @HttpExchange(method = "GET", url = "/fallback")
    String exchange(URI uri, HttpMethod method);
}
```

Spring nhận `URI` và `HttpMethod` như hai parameter đặc biệt cho phép override URL/method đã khai báo. **Đừng đưa URI tùy ý từ client cuối vào thẳng method**: nguy cơ SSRF, gửi credential đến host lạ hoặc bỏ qua base URL dự kiến. Chỉ dùng khi endpoint cần chọn linh hoạt và có allowlist/validation.

### Khi cần custom argument mapping

Một tham số nghiệp vụ dạng `SearchCriteria` không mặc nhiên được Spring biến thành nhiều query fields. Có thể khai rõ từng `@RequestParam`, hoặc cài `HttpServiceArgumentResolver` trong `HttpServiceProxyFactory.Builder` khi pattern thực sự lặp lại. Một custom resolver phải nhận diện kiểu của tham số, chuyển thành dữ liệu request trên `HttpRequestValues.Builder` và trả `true` nếu đã xử lý; nếu không thì trả `false`. Đó là extension của **proxy**, không phải thay thế controller argument resolver.

## 7. Return type, lỗi HTTP và lifecycle

### 7.1 Return type theo adapter

| Loại backend | Return type HTTP Service method | Ý nghĩa |
| --- | --- | --- |
| `RestClientAdapter` | `String`, `BookDto`, `List<BookDto>` | Gọi đồng bộ và giải mã body |
| `RestClientAdapter` | `ResponseEntity<BookDto>` | Có status, headers, body |
| `RestClientAdapter` | `ResponseEntity<Void>` hoặc `void` | Chỉ quan tâm status/không cần body |
| `WebClientAdapter` | `Mono<BookDto>` | Một giá trị reactive |
| `WebClientAdapter` | `Flux<BookDto>` | Nhiều phần tử/stream reactive |
| `WebClientAdapter` | `Mono<ResponseEntity<BookDto>>` | Status/headers/body reactive |

Không khai báo `Flux<T>` với synchronous RestClient rồi trông chờ tự stream; không khai báo signature đồng bộ rồi gọi trên thread event-loop và kỳ vọng sẽ không block.

### 7.2 HTTP status và network failure

Mặc định `RestClient`/`WebClient` thường chuyển **4xx/5xx** thành lỗi khi HTTP Service method xử lý response. Với RestClient, có thể đặt `defaultStatusHandler(...)`; với WebClient, cấu hình `defaultStatusHandler(...)` hoặc filter trên client nền. Các lỗi DNS, connect, timeout, TLS là **transport failure**, không phải response 404/500 và cần chiến lược xử lý khác.

Hãy chú ý:

- 404 đôi khi là “không tìm thấy” bình thường; dùng `ResponseEntity` hoặc status handler khi contract cần trả empty/null thích hợp thay vì lan mọi lỗi.
- Không retry mù quáng POST tạo tài nguyên khi chưa có idempotency key; có thể tạo trùng nếu request đầu tiên đã thành công nhưng response bị mất.
- Timeout, connection pool, proxy, TLS, logging và observability nằm ở cấu hình underlying RestClient/WebClient/HTTP engine, **không phải** thuộc tính của `@GetExchange`.
- `@RequestHeader("Authorization")` và `@CookieValue` có thể chứa secret. Không ghi toàn bộ method arguments/URI vào log.
- Nếu API bên ngoài thay đổi field JSON hay status, proxy không tự “sửa” schema; cần contract/integration test.

## 8. Ví dụ Java 17+ chạy độc lập: chứng minh GET, POST và parameter mapping

Ví dụ này dùng JDK `HttpServer` làm HTTP server chạy trên localhost, `RestClient` làm client và HTTP interface `@GetExchange`/`@PostExchange`. **Không cần tài khoản hay kết nối Internet để gọi API**; Gradle tải dependency Maven lần đầu nếu chưa cache.

Cấu trúc thư mục:

```text
http-service-demo/
├── build.gradle
└── src/main/java/demo/HttpInterfaceDemo.java
```

`build.gradle` (bản chạy tham chiếu Spring Boot 3.3.13 → Spring Framework 6.1.21):

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
    implementation 'org.springframework.boot:spring-boot-starter-web:3.3.13'
}

application {
    mainClass = 'demo.HttpInterfaceDemo'
}
```

`src/main/java/demo/HttpInterfaceDemo.java`:

```java
package demo;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

public class HttpInterfaceDemo {

    @HttpExchange("/v1")
    public interface NotesApi {
        @GetExchange("/notes/{id}")
        String get(
                @PathVariable("id") long id,
                @RequestParam("lang") String language,
                @RequestHeader("X-Trace-Id") String traceId);

        @PostExchange(
                url = "/notes",
                contentType = MediaType.TEXT_PLAIN_VALUE,
                accept = MediaType.TEXT_PLAIN_VALUE)
        String post(
                @RequestBody String text,
                @RequestHeader("X-Trace-Id") String traceId);
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/notes", HttpInterfaceDemo::handle);
        server.start();

        try {
            String baseUrl =
                    "http://127.0.0.1:" + server.getAddress().getPort();

            RestClient restClient = RestClient.builder()
                    .baseUrl(baseUrl)
                    .build();

            NotesApi notes = HttpServiceProxyFactory
                    .builderFor(RestClientAdapter.create(restClient))
                    .build()
                    .createClient(NotesApi.class);

            System.out.println(notes.get(42, "vi", "T-01"));
            System.out.println(notes.post("Spring Client", "T-02"));
        } finally {
            server.stop(0);
        }
    }

    private static void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String trace =
                    exchange.getRequestHeaders().getFirst("X-Trace-Id");
            String result;

            if ("GET".equals(method)) {
                result = "GET " + exchange.getRequestURI().getRawPath()
                        + "?" + exchange.getRequestURI().getRawQuery()
                        + " trace=" + trace;
            } else if ("POST".equals(method)) {
                String body = new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8);
                result = "POST body=" + body + " trace=" + trace;
            } else {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } finally {
            exchange.close();
        }
    }
}
```

Chạy:

```bash
gradle run
```

Nếu project đã có Gradle Wrapper: macOS/Linux `./gradlew run`; Windows PowerShell `.\gradlew.bat run`. Với Java 17+ và dependency đúng, hai dòng chương trình in ra là:

```text
GET /v1/notes/42?lang=vi trace=T-01
POST body=Spring Client trace=T-02
```

Đó là **stdout do `System.out.println` sinh ra**; Gradle có thể in thêm log build. Server dùng cổng loopback ngẫu nhiên, do đó không hard-code port. Đường dẫn `/v1` từ `@HttpExchange` cấp interface, `/notes/{id}` từ `@GetExchange`, giá trị 42 thay `{id}`, `lang=vi` đến từ `@RequestParam`, `X-Trace-Id` thành header. POST gửi đúng chuỗi `Spring Client` làm body text/plain.

Lưu ý chương trình không khởi động `SpringApplication` và không tạo MVC controller. Điều này chứng minh HTTP interface hoạt động thông qua proxy và một HTTP client thật, không cần Spring IoC container để chạy.

### Đổi POST từ text/plain sang JSON

Trong ứng dụng thực tế, có thể thay khai báo bằng:

```java
record CreateNoteRequest(String text) {}
record NoteDto(long id, String text) {}

@PostExchange(
        url = "/notes",
        contentType = MediaType.APPLICATION_JSON_VALUE,
        accept = MediaType.APPLICATION_JSON_VALUE)
NoteDto create(@RequestBody CreateNoteRequest request);
```

Đây là **fragment**; server tương ứng phải hiểu JSON, trả JSON và project phải có codec/message converters JSON. Khác với demo, bạn sẽ cần một handler tạo note và response DTO thật.

## 9. Những lỗi phổ biến và cách tránh

1. **Không tạo proxy:** annotation interface không tự thành bean. Tạo bằng `HttpServiceProxyFactory` hoặc cấu hình cơ chế auto-registration tương thích phiên bản.
2. **Trộn annotation server/client:** `@RequestParam` trên HTTP service *đang tạo outgoing request*; không tự đọc query từ request đi vào server Spring MVC.
3. **Quên @PathVariable tên rõ ràng:** `{id}` và `@PathVariable("userId")` không khớp sẽ lỗi. Giữ tên consistent.
4. **Đẩy JSON qua @RequestParam:** với POST JSON, dùng `@RequestBody` và đúng `contentType`; form-urlencoded là tình huống khác.
5. **Gửi body với @GetExchange:** API GET không nên phụ thuộc request body; nhiều proxy/cache/server không hỗ trợ hoặc không có semantics ổn định.
6. **Thử khai báo @RequestPart nhưng Content-Type là application/json:** multipart cần `multipart/form-data` và format từng part được server hỗ trợ.
7. **Không biết tham số null có hợp lệ hay không:** khai rõ `required=false`/`Optional` với tham số optional; validate đầu vào bắt buộc.
8. **Gửi trùng Authorization do defaultHeader + @RequestHeader:** kiểm tra cơ chế cộng header và tránh biến token thành header list ngoài ý muốn.
9. **Dùng @PostExchange cho thao tác non-idempotent rồi auto-retry:** có thể tạo dữ liệu trùng. Xác định idempotency key và chính sách retry.
10. **Đổi RestClient ↔ WebClient nhưng giữ return type bất hợp lý:** chọn kiểu sync/reactive phù hợp adapter; không block trong WebFlux event loop.
11. **Bỏ qua mã lỗi và timeout:** status 400/500 khác lỗi mạng; cấu hình handler, logging có kiểm soát và timeout ở underlying client.
12. **Cho phép dynamic URI không kiểm soát:** có thể gửi request tới host ngoài phạm vi hoặc SSRF. Validate scheme, host, port và mạng đích.
13. **Cho rằng Accept là Content-Type:** `Accept` mô tả response mong muốn; `Content-Type` mô tả payload request.
14. **Viết test chỉ assert interface method return mock:** nên có test thực gửi request tới server giả/local để xác minh URL, headers, query/body và HTTP status.

## 10. Tự luyện và kiểm tra hiểu bài

**Bài 1 – Đọc request.**

```java
@HttpExchange("/api")
interface ProductApi {
    @GetExchange("/products/{id}")
    String find(
            @PathVariable("id") int id,
            @RequestParam("region") String region,
            @RequestHeader("X-Client") String client);
}
```

Giả sử base URL là `https://shop.example.org`, gọi `find(9, "vn", "portal")`. Kết quả về mặt cấu trúc là:

```http
GET /api/products/9?region=vn HTTP/1.1
Host: shop.example.org
X-Client: portal
```

Không có JSON request body. Dòng HTTP trên là biểu diễn request logic; HTTP transport thực tế có thể dùng HTTP/2 hoặc thêm các header khác.

**Bài 2 – Sửa ánh xạ.** Code sau gửi POST JSON đúng chưa?

```java
@PostExchange(url = "/customers", contentType = "application/json")
String create(@RequestParam("name") String name);
```

**Đáp án:** Chưa đúng nếu server đòi JSON body. Dùng một DTO/record và `@RequestBody`. `@RequestParam` ở đây thêm query parameter do content type không phải form-urlencoded.

**Bài 3 – Optional.** Nếu HTTP service interface có tham số `@RequestParam(value="sort", required=false) String sort`, có thể gọi với `null` không? **Đáp án:** Có; Spring được phép bỏ qua tham số không bắt buộc. Với param required, cần truyền giá trị hoặc cơ chế optional phù hợp.

**Bài 4 – Chọn adapter.** Giao diện trả `Mono<BookDto>` nên dùng gì? **Đáp án:** `WebClientAdapter` / reactive backend; còn các return type sync kiểu `BookDto`/`ResponseEntity<BookDto>` là tự nhiên với `RestClientAdapter`.

**Bài 5 – Phân biệt header.** `@PostExchange(contentType="application/json", accept="application/json")` tạo những header nào? **Đáp án:** `Content-Type: application/json` để mô tả body gửi đi và `Accept: application/json` để cho biết client muốn nhận JSON.

## Tài liệu tham khảo chính thức

- [Spring Framework — REST Clients: HTTP Service Clients, Method Parameters, Return Values](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-http-interface)
- [Spring Framework 6.1 — @HttpExchange Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/service/annotation/HttpExchange.html)
- [Spring Framework 6.1 — @GetExchange Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/service/annotation/GetExchange.html)
- [Spring Framework 6.1 — @PostExchange Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/service/annotation/PostExchange.html)
- [Spring Framework 6.1 — HttpServiceProxyFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/service/invoker/HttpServiceProxyFactory.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-service-clients-003">HttpServiceProxyFactory và adapter cho RestClient hoặc WebClient</a>

<details>
<summary>Xem chi tiết</summary>

# HttpServiceProxyFactory và adapter cho RestClient hoặc WebClient

## 1. Tổng quan: HTTP Service Client là gì?

Trong Spring Framework, **HTTP Service Client** là cách khai báo API HTTP từ xa bằng một **Java interface**. Mỗi phương thức interface được gắn annotation mô tả HTTP method, URL, headers, request body và kiểu response. **HttpServiceProxyFactory** tạo một proxy triển khai interface; khi ứng dụng gọi phương thức, proxy chuyển thành request thật qua một HTTP client đã được **adapter** bọc lại.

Ví dụ một endpoint GET /api/books/7 trả JSON:

~~~json
{"id":7,"title":"Spring in Action"}
~~~

Có thể viết interface:

~~~java
@HttpExchange(url = "/api/books", accept = MediaType.APPLICATION_JSON_VALUE)
public interface BookApi {
    @GetExchange("/{id}")
    Book findById(@PathVariable("id") long id);
}
~~~

Đến lúc gọi **bookApi.findById(7L)**, Spring tạo GET request phù hợp, decode JSON thành **Book** và trả cho caller. **Book** có thể là Java record:

~~~java
public record Book(long id, String title) {}
~~~

Đây là HTTP client **phía người gọi**. Nó không tự sinh endpoint cho server, không thay cho authentication, timeout, retry hay service discovery. Remote server vẫn phải có API đúng contract.

~~~text
Mã nghiệp vụ: bookApi.findById(7)
                  |
                  v
        Interface @HttpExchange
                  |
          HttpServiceProxyFactory
                  |
       Proxy đọc method + argument
                  |
           HttpExchangeAdapter
            /             \
 RestClientAdapter   WebClientAdapter
       |                    |
   RestClient            WebClient
    sync                 reactive
       \                    /
         HTTP request đến server
                  |
         JSON response -> Book
~~~

Ba khái niệm phải phân biệt:

- **HTTP service interface:** mô tả hợp đồng HTTP, cung cấp API typed cho code Java gọi.
- **HttpServiceProxyFactory:** phân tích annotation/chữ ký method, tạo proxy ở runtime; không phải HTTP transport.
- **Adapter:** cầu nối từ proxy xuống client cụ thể. **RestClientAdapter** dùng RestClient đồng bộ; **WebClientAdapter** dùng WebClient reactive.

Cách tiếp cận này giảm lặp URI, serialization, boilerplate và tập trung khai báo contract. Khi cần xử lý HTTP chi tiết bất thường, vẫn có thể gọi RestClient/WebClient fluent API trực tiếp.

### Phiên bản API

Bài dùng **Java 17+**, tập trung vào **Spring Framework 6.2.x**; ý tưởng và các lời gọi phổ biến còn dùng trong Spring Framework 7.x. Có thể chạy ví dụ với **Spring Boot 3.5.x** (nền Spring 6.2).

- HttpServiceProxyFactory và WebClientAdapter xuất hiện từ Spring 6.0.
- RestClient và RestClientAdapter có từ Spring 6.1.
- Với Spring 6.1+, ưu tiên **HttpServiceProxyFactory.builderFor(adapter)** và **WebClientAdapter.create(webClient)**.
- Ví dụ cũ dùng **HttpServiceProxyFactory.builder(adapter)** hoặc **WebClientAdapter.forClient(webClient)** thuộc API đã deprecate; mã mới tránh dùng.

## 2. Khai báo HTTP interface chính xác

Giả sử hệ thống catalog có:

| HTTP request | Body/response | Nghiệp vụ |
| --- | --- | --- |
| GET /api/books/{id} | JSON Book | Tra một sách |
| GET /api/books?keyword=java | JSON array Book | Tìm sách |
| POST /api/books | JSON CreateBook, trả HTTP 201 và Book | Tạo sách |
| DELETE /api/books/{id} | Không cần body response | Xóa sách |

Các kiểu dữ liệu:

~~~java
public record Book(long id, String title) {}
public record CreateBook(String title) {}
~~~

Interface đồng bộ:

~~~java
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(url = "/api/books",
              accept = MediaType.APPLICATION_JSON_VALUE)
public interface BookService {
    @GetExchange("/{id}")
    Book findById(@PathVariable("id") long id);

    @GetExchange
    List<Book> search(@RequestParam("keyword") String keyword);

    @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Book> create(
        @RequestHeader("X-Request-Id") String requestId,
        @RequestBody CreateBook request);

    @DeleteExchange("/{id}")
    void delete(@PathVariable("id") long id);
}
~~~

Đây là mẫu minh họa hợp đồng. Các top-level **public** type phải được đặt trong các file Java cùng tên, hoặc chuyển thành nested type như ví dụ hoàn chỉnh ở mục 8. API server thực tế quyết định đường dẫn, status và JSON.

Chú ý:
- **@HttpExchange** trên interface khai báo URL prefix dùng chung; annotation trên method như **@GetExchange("/{id}")** bổ sung path và xác định method.
- **@PathVariable("id")** lấy đối số Java chèn vào URI template.
- **@RequestParam("keyword")** thông thường thành query parameter. Với request **application/x-www-form-urlencoded**, request parameters được đưa vào form body.
- **@RequestBody** là dữ liệu Java cần serialize ra request (ví dụ JSON).
- **@RequestHeader** thêm header theo từng lời gọi, thích hợp cho correlation ID hoặc token được truyền theo request.
- **ResponseEntity<Book>** cho phép đọc cả status, headers và body. Chỉ khai báo **Book** khi thực sự chỉ cần body.
- **void** thực hiện request nhưng không trả body. Nếu API trả nội dung cần dùng, đừng bỏ qua bằng kiểu void.
- Các shortcut khác gồm **@PutExchange**, **@PatchExchange**, **@DeleteExchange**.
- **@GetExchange** trong HTTP interface thuộc **org.springframework.web.service.annotation**, khác annotation controller **@GetMapping**.

**Accept** là định dạng phản hồi client mong nhận; **Content-Type** là định dạng request body được gửi đi. Đặt **accept = application/json** không có nghĩa mọi request cũng phải có Content-Type JSON.

Có thể dùng **@HttpExchange** ngay trên method (kèm method/url) nếu cần cách khai báo tổng quát hơn, nhưng annotation theo method thường gọn và dễ hiểu.

### Tên tham số và kiểu generic

Dùng **@PathVariable("id")**, **@RequestParam("keyword")** với tên tường minh giúp tránh phụ thuộc hoàn toàn vào metadata parameter name của compiler. Interface method khai báo **List<Book>** giữ được thông tin kiểu phần tử qua chữ ký phản chiếu; message converter biết giải mã list của Book, khác với dùng raw **List**.

**Đừng xem HTTP interface như REST controller**. Cùng một annotation family có thể được dùng ở phía server trong một số cấu hình Spring, nhưng tạo proxy bằng HttpServiceProxyFactory là công việc ở phía client.

## 3. Dùng HttpServiceProxyFactory với RestClientAdapter

**RestClient** là HTTP client đồng bộ của Spring Framework. Cách dùng khi tạo proxy:

~~~java
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

RestClient restClient = RestClient.builder()
    .baseUrl("https://catalog.example.org")
    .build();

RestClientAdapter adapter = RestClientAdapter.create(restClient);

HttpServiceProxyFactory factory =
    HttpServiceProxyFactory.builderFor(adapter).build();

BookService books = factory.createClient(BookService.class);

Book book = books.findById(7L);
~~~

**catalog.example.org** chỉ là hostname minh họa. Giả sử server thật có endpoint tương ứng, **findById(7L)** phát GET đến **https://catalog.example.org/api/books/7**, chờ response, decode JSON rồi trả Book. Nếu server lỗi/không tồn tại, lời gọi có thể ném exception.

RestClientAdapter thực thi hợp đồng **HttpExchangeAdapter**, phù hợp với return type đồng bộ:
- **void**.
- **HttpHeaders**, **Book** hoặc một **T** cụ thể.
- **List<Book>** hoặc **ResponseEntity<Book>**, **ResponseEntity<List<Book>>**.
- **ResponseEntity<Void>** nếu cần status và headers nhưng không cần response body.

Một trường hợp thực tế:

~~~java
Book found = books.findById(7);
List<Book> matches = books.search("java");
ResponseEntity<Book> created =
    books.create("req-123", new CreateBook("Spring Cookbook"));

System.out.println(found.title());
System.out.println(created.getStatusCode());
~~~

Đây chỉ là đoạn sử dụng khi đã có API catalog; **stdout phụ thuộc server** nên không khẳng định một chuỗi kết quả cụ thể.

### Chọn RestClientAdapter khi nào?

RestClientAdapter phù hợp với tầng nghiệp vụ xử lý đồng bộ, ứng dụng Spring MVC truyền thống, job/batch tuần tự, command line và tích hợp có số lượng HTTP call kiểm soát được. Thread caller chờ I/O; vì vậy phải cấu hình connection pool, concurrency và timeout phù hợp.

Không khai báo **Mono<Book>** hay **Flux<Book>** làm kiểu trả về rồi bọc interface bằng RestClientAdapter. Nó không cung cấp reactive exchange tương đương WebClient. Nếu cần luồng bất đồng bộ, hãy dùng WebClientAdapter và khai báo interface phản ánh semantics đó.

## 4. Dùng HttpServiceProxyFactory với WebClientAdapter

**WebClient** hỗ trợ I/O non-blocking/reactive. WebClientAdapter thực hiện **ReactorHttpExchangeAdapter**, cho phép HTTP service interface trả về Reactor **Mono** hoặc **Flux**.

Interface reactive tương ứng:

~~~java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(url = "/api/books",
              accept = MediaType.APPLICATION_JSON_VALUE)
public interface ReactiveBookService {
    @GetExchange("/{id}")
    Mono<Book> findById(@PathVariable("id") long id);

    @GetExchange
    Flux<Book> list();

    @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<Book>> create(@RequestBody CreateBook request);
}
~~~

Tạo client, adapter, factory, proxy:

~~~java
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

WebClient webClient = WebClient.builder()
    .baseUrl("https://catalog.example.org")
    .build();

WebClientAdapter adapter = WebClientAdapter.create(webClient);

HttpServiceProxyFactory factory =
    HttpServiceProxyFactory.builderFor(adapter).build();

ReactiveBookService books =
    factory.createClient(ReactiveBookService.class);

Mono<Book> result = books.findById(7L);
~~~

**result** ở đây là publisher mô tả thao tác HTTP. Request được thực thi khi có subscription. Trong Spring WebFlux controller, **return result** để framework subscribe/cancel/quản lý lỗi; không cần **subscribe()** tùy tiện. Chỉ gọi **result.block()** ở ranh giới đồng bộ có kiểm soát (ví dụ main/test), không gọi block() bên trong event-loop.

### Flux, streaming và kiểu trả về

Một HTTP service method có thể khai báo:

~~~java
@GetExchange(url = "/events",
             accept = MediaType.APPLICATION_NDJSON_VALUE)
Flux<Book> streamBooks();
~~~

Method này minh họa trường hợp remote server trả **application/x-ndjson**, mỗi dòng là một JSON record. Flux phát các Book khi decoder nhận đủ record. Nếu server thực tế trả **text/event-stream**, hãy dùng Content-Type phù hợp và có thể dùng **Flux<ServerSentEvent<Book>>** nếu cần id/event/retry metadata.

Không suy ra API là realtime chỉ vì kiểu trả về là Flux. Dữ liệu có đến dần không còn phụ thuộc framing, codec, khả năng flush của server/proxy và subscription. Tránh gọi **collectList()** trên stream vô hạn.

### Có thể dùng return type đồng bộ với WebClientAdapter?

Spring hỗ trợ cả các return type đồng bộ như **Book** hoặc **ResponseEntity<Book>** khi dùng WebClientAdapter, nhưng để trả Book trực tiếp proxy phải chờ response. Vì vậy chọn **Mono/Flux** nếu toàn bộ call chain cần non-blocking.

Không cần cố chia sẻ cùng một Java interface giữa client sync và reactive khi API sử dụng khác nhau. Một cặp interface thể hiện rõ hợp đồng:

~~~text
BookService.findById(id)          -> Book
ReactiveBookService.findById(id)  -> Mono<Book>
~~~

Chúng có thể trỏ đến cùng endpoint nhưng kiểu kết quả phản ánh mô hình xử lý khác nhau.

## 5. Bảng so sánh chọn adapter

| Tiêu chí | RestClientAdapter | WebClientAdapter |
| --- | --- | --- |
| Client được bọc | RestClient | WebClient |
| Loại adapter | HttpExchangeAdapter | ReactorHttpExchangeAdapter |
| Cách dùng điển hình | Đồng bộ (blocking caller) | Reactive, non-blocking |
| Kiểu trả về tự nhiên | Book, List<Book>, ResponseEntity<Book>, void | Mono<Book>, Flux<Book>, Mono<ResponseEntity<Book>> |
| Có dùng kiểu đồng bộ được? | Có | Có, nhưng cần chờ response |
| Stream dài hạn | Không phải mục tiêu chính | Phù hợp hơn với Flux |
| Error/status policy | RestClient Builder/request factory | WebClient Builder/connector |
| Tình huống phù hợp | MVC, batch và tầng tích hợp đồng bộ | WebFlux, nhiều I/O đồng thời, stream |

**HttpServiceProxyFactory không tự biến một method đồng bộ thành reactive.** Adapter và return type mới xác định cách request vận hành. Việc đổi adapter mà giữ nguyên toàn bộ interface có thể sai với kiểu phản hồi mong muốn.

## 6. Đăng ký proxy làm bean trong Spring Boot

Đặt cấu hình client/proxy tại **@Configuration**, rồi inject interface vào service. Cách này hỗ trợ dùng lại HTTP client, cấu hình theo môi trường và kiểm thử dễ dàng. Trong ứng dụng Spring Boot có cả **RestClient.Builder** và **WebClient.Builder**:

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class RemoteClientConfig {

    @Bean
    BookService bookService(RestClient.Builder builder) {
        RestClient http = builder
            .baseUrl("https://catalog.example.org")
            .build();
        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(http))
            .build()
            .createClient(BookService.class);
    }

    @Bean
    ReactiveBookService reactiveBookService(WebClient.Builder builder) {
        WebClient http = builder
            .baseUrl("https://catalog.example.org")
            .build();
        return HttpServiceProxyFactory
            .builderFor(WebClientAdapter.create(http))
            .build()
            .createClient(ReactiveBookService.class);
    }
}
~~~

Các interface/record thuộc package tương thích với cấu hình (hoặc có import đúng package). URL trong ví dụ là host minh họa. Cấu hình thực tế nên lấy từ **application.properties** hoặc **@ConfigurationProperties**, ví dụ:

~~~properties
catalog.base-url=https://catalog.example.org
~~~

Với **@Value** hoặc type-safe configuration properties, inject giá trị trên vào bean factory method thay vì hard-code hostname. Nên tạo các client riêng khi host, thông tin xác thực, base URL, timeout hoặc trust policy khác nhau; nếu nhiều interface cùng host và policy, một factory có thể **createClient** nhiều interface.

**Quan trọng:** Spring Boot chỉ tự cung cấp bean WebClient.Builder khi dependency WebFlux phù hợp trên classpath; trong ứng dụng không có WebFlux, cần thêm dependency/client configuration tương ứng. Dùng WebClient để gọi API **không bắt buộc toàn bộ ứng dụng phải là WebFlux server**.

### Những thứ cần cấu hình ở underlying HTTP client

- **baseUrl**, default headers, phương án gắn authorization cho mỗi request.
- Connection pool, TLS, HTTP proxy, timeout kết nối/đọc/response.
- Jackson converters hoặc WebFlux codecs, kích thước buffer và media types.
- Interceptors/filters cho logging có redact dữ liệu nhạy cảm, tracing và metrics.
- HTTP status handler, mapping 404/5xx, retry policy và circuit breaker theo nghiệp vụ.

Những thông số này thuộc **RestClient.Builder**, **WebClient.Builder** hoặc connector/request factory; **HttpServiceProxyFactory** đảm nhiệm việc chuyển interface invocation thành HTTP exchange.

## 7. Return type, xử lý lỗi và một số tùy chỉnh nâng cao

### 7.1 Return type do adapter quyết định

Với RestClientAdapter, các return type thường gặp:

~~~java
void delete(long id);
Book findById(long id);
ResponseEntity<Book> getWithHeaders(long id);
List<Book> findAll();
~~~

Với WebClientAdapter:

~~~java
Mono<Void> deleteReactive(long id);
Mono<Book> findByIdReactive(long id);
Flux<Book> stream();
Mono<ResponseEntity<Book>> getWithHeadersReactive(long id);
~~~

Spring còn hỗ trợ **Mono<ResponseEntity<Flux<T>>>** khi cần status + stream body ở adapter reactive. Khai báo đúng generic type ở phương thức giúp decoder tạo đúng DTO. **Mono<List<Book>>** trả một list khi giải mã xong; **Flux<Book>** phát từng Book. **ResponseEntity** đem lại status và headers, còn DTO thuần chỉ là body.

### 7.2 Mapping lỗi HTTP

Mặc định, RestClient xử lý HTTP status lỗi thành các loại **RestClientException** (ví dụ **RestClientResponseException** với status/body), WebClient thường phát **WebClientResponseException** trong reactive pipeline. Không khẳng định lỗi network, decode JSON và HTTP 4xx/5xx là một loại lỗi duy nhất.

Ví dụ cấu hình handler 404 cho RestClient:

~~~java
RestClient rest = RestClient.builder()
    .baseUrl("https://catalog.example.org")
    .defaultStatusHandler(
        status -> status.value() == 404,
        (request, response) -> {
            throw new IllegalStateException("Không tìm thấy sách");
        })
    .build();
~~~

Ví dụ tương ứng cho WebClient:

~~~java
WebClient web = WebClient.builder()
    .baseUrl("https://catalog.example.org")
    .defaultStatusHandler(
        status -> status.value() == 404,
        response -> Mono.just(
            new IllegalStateException("Không tìm thấy sách")))
    .build();
~~~

Hai đoạn code trên là **fragment cấu hình**, không phải chương trình chạy độc lập; cần import RestClient/WebClient/Mono. RestClient status handler có signature handler đồng bộ nhận request và response; WebClient handler nhận response và trả Publisher chứa Throwable. Không copy nguyên lambda của client này sang client kia.

Lỗi cần thiết kế theo nghiệp vụ: 404 đôi khi được ánh xạ thành Optional/không có dữ liệu, 409 cần xử lý conflict, 429 có thể retry theo Retry-After. Đừng tự động nuốt mọi 4xx để trả DTO null; việc đó có thể che mất lỗi cấu hình hoặc bảo mật.

### 7.3 Timeout, retry và lifecycle

- Đồng bộ: đặt timeout ở HTTP client/request factory. Không để thread chờ không giới hạn khi downstream lỗi.
- Reactive: đặt timeout network trên connector và timeout nghiệp vụ trong Reactor khi cần; lưu ý timeout giữa các phần tử có thể làm hỏng stream hợp lệ.
- **WebClientAdapter** có thể hỗ trợ block timeout cho trường hợp phương thức trả kiểu đồng bộ, nhưng cấu hình ở HTTP client bên dưới thường rõ hơn.
- Retry với POST tạo resource có thể gây tạo trùng; chỉ retry an toàn khi có cơ chế idempotency hoặc hợp đồng rõ.
- Reactive Mono/Flux cần subscription; controller WebFlux nên return publisher. Không gọi **block()** ở event-loop.
- Với stream dài hạn, quản lý cancellation, buffering, error signal, backpressure và khả năng server flush.

### 7.4 Tùy chỉnh resolver cho object tham số

Khi API yêu cầu ba query parameter nhưng Java muốn một object **SearchFilter**, có thể tạo **HttpServiceArgumentResolver**:

~~~java
public record SearchFilter(String owner, String language, String term) {}

public class SearchFilterResolver
        implements HttpServiceArgumentResolver {

    @Override
    public boolean resolve(
            Object argument,
            MethodParameter parameter,
            HttpRequestValues.Builder values) {
        if (!SearchFilter.class.equals(parameter.getParameterType())) {
            return false;
        }

        SearchFilter filter = (SearchFilter) argument;
        values.addRequestParameter("owner", filter.owner());
        values.addRequestParameter("language", filter.language());
        values.addRequestParameter("term", filter.term());
        return true;
    }
}
~~~

Các import còn cần là **org.springframework.core.MethodParameter**, **org.springframework.web.service.invoker.HttpServiceArgumentResolver**, **org.springframework.web.service.invoker.HttpRequestValues**. Ví dụ resolver này là fragment tách từ hệ thống có interface; nó cho thấy cách ánh xạ một object thành query, không phải một main độc lập.

Gắn resolver:

~~~java
HttpServiceProxyFactory factory = HttpServiceProxyFactory
    .builderFor(RestClientAdapter.create(restClient))
    .customArgumentResolver(new SearchFilterResolver())
    .build();
~~~

Spring gọi resolver tùy biến trước resolver mặc định; nếu không hỗ trợ kiểu tham số, phải trả **false** để các resolver khác tiếp tục. Builder còn cho phép **conversionService(...)** để chuyển giá trị thành String và **embeddedValueResolver(...)** cho giá trị nhúng trong metadata của HTTP service.

### 7.5 Header, URI và multipart

Interface còn hỗ trợ **@CookieValue**, **@RequestPart**, tham số **URI** (ghi đè URL được khai báo) và một số argument khác. Ví dụ multipart request:

~~~java
@PostExchange(
    url = "/files",
    contentType = MediaType.MULTIPART_FORM_DATA_VALUE)
ResponseEntity<Void> upload(
    @RequestPart("description") String description,
    @RequestPart("file") Resource file);
~~~

Import **org.springframework.core.io.Resource**. Phía gọi truyền **FileSystemResource** của tệp có thật; tên part và media type phải đúng hợp đồng server. Multipart upload không tự động nghĩa response cũng là multipart: nếu server trả JSON, khai báo DTO response JSON như bình thường.

## 8. Ví dụ hoàn chỉnh Java 17: một API, hai adapter

Demo dưới đây khởi chạy HTTP server tối giản trên loopback bằng JDK, sau đó gọi cùng endpoint thông qua **RestClientAdapter** và **WebClientAdapter**. Không cần server internet; Maven tải dependency Spring cần thiết khi chạy lần đầu.

Cấu trúc:

~~~text
http-interface-demo/
  pom.xml
  src/main/java/demo/HttpInterfaceDemo.java
~~~

**pom.xml** dùng Spring Boot 3.5.0 để quản lý phiên bản Spring 6.2.x:

~~~xml
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
    <artifactId>http-interface-demo</artifactId>
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
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/demo/HttpInterfaceDemo.java**:

~~~java
package demo;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;

public class HttpInterfaceDemo {

    public record Book(long id, String title) {}

    @HttpExchange(url = "/api/books",
                  accept = MediaType.APPLICATION_JSON_VALUE)
    public interface BlockingBooks {
        @GetExchange("/{id}")
        Book find(@PathVariable("id") long id);
    }

    @HttpExchange(url = "/api/books",
                  accept = MediaType.APPLICATION_JSON_VALUE)
    public interface ReactiveBooks {
        @GetExchange("/{id}")
        Mono<Book> find(@PathVariable("id") long id);
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/books/1", HttpInterfaceDemo::respond);
        server.start();

        try {
            String baseUrl =
                "http://127.0.0.1:" + server.getAddress().getPort();

            RestClient rest = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

            BlockingBooks blocking = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(rest))
                .build()
                .createClient(BlockingBooks.class);

            WebClient web = WebClient.builder()
                .baseUrl(baseUrl)
                .build();

            ReactiveBooks reactive = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(web))
                .build()
                .createClient(ReactiveBooks.class);

            System.out.println("RestClient -> " +
                blocking.find(1).title());
            System.out.println("WebClient -> " +
                reactive.find(1).block().title());
        } finally {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange)
            throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        byte[] body = "{\"id\":1,\"title\":\"Spring\"}"
            .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
            "Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
~~~

**Chạy** trong thư mục **http-interface-demo**, yêu cầu JDK 17+ và Maven:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.HttpInterfaceDemo
~~~

**Kết quả mà chương trình in ra stdout** khi chạy thành công:

~~~text
RestClient -> Spring
WebClient -> Spring
~~~

Diễn giải:
1. JDK HttpServer tạo GET /api/books/1 và trả **{"id":1,"title":"Spring"}**. Cổng ngẫu nhiên được chọn khi bind port **0**.
2. RestClient và WebClient có cùng base URL nhưng dùng adapter và interface riêng.
3. Lệnh **blocking.find(1)** phát request đồng bộ, trả Book.
4. **reactive.find(1)** trả Mono; **block()** chỉ được dùng để minh họa ở main đồng bộ, khiến publisher subscribe và đọc JSON.
5. **server.stop(0)** trong finally kết thúc server, kể cả khi lời gọi HTTP thất bại.

Không có đầu ra HTTP cố định nào khác cần đoán. Khi chạy bằng Maven có thể xuất hiện log/cảnh báo của công cụ tùy môi trường; hai dòng trên là các dòng do chính chương trình **System.out.println** phát ra.

## 9. Kiểm thử hợp đồng và lỗi thường gặp

### Hai tầng kiểm thử nên tách

**Kiểm thử proxy + HTTP contract**: dùng server giả như WireMock, MockWebServer hoặc JDK HttpServer để kiểm tra request method, path, query, header, request JSON, status và deserialize response. Ví dụ Java main ở mục 8 kiểm thử một GET thành công của cả hai adapter.

**Kiểm thử business logic**: mock chính Java interface **BookService**/**ReactiveBookService** trong unit test của service, không nhất thiết tạo HTTP proxy khi test chỉ kiểm tra điều phối. Hai loại test bổ sung nhau.

Các tình huống thêm cho contract tests: server trả 404/500, body sai schema, content type khác dự kiến, token thiếu, server chậm, hủy subscription và retry thao tác có side effect.

| Triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Không tìm thấy builder(adapter), forClient(...) | Lẫn ví dụ Spring 6.0 và 6.1+/6.2 | Dùng builderFor và create tương ứng với phiên bản |
| Mono/Flux không hỗ trợ | Interface reactive đi với RestClientAdapter | Dùng WebClientAdapter |
| Treo hoặc lỗi block() trong handler WebFlux | Phương thức trả đồng bộ hoặc block trên event-loop | Giữ pipeline Mono/Flux và return publisher |
| 404 dù host đúng | baseUrl và @HttpExchange/@GetExchange ghép sai path | Kiểm tra đường dẫn thực, path variables |
| Không resolve path variable | Tên tham số không trùng placeholder | Ghi rõ @PathVariable("id") |
| Lỗi JSON / ClassCastException | Kiểu return không khớp JSON, dùng raw types | Khai báo DTO/generic chính xác |
| Flux không nhận dữ liệu | Chưa subscribe, server không flush, sai media type | Xác minh NDJSON/SSE, subscriber và flush |
| 401 hoặc token bị log lộ | Auth header sai cách hoặc logging không redact | Áp dụng auth policy đúng, ẩn token khỏi log |
| POST bị nhân đôi sau retry | Không có idempotency key | Thiết kế retry an toàn |
| HTTP 4xx/5xx khó xử lý | Chưa có status handler | Cấu hình ở RestClient/WebClient |
| Khó giải thích timeout | Timeout chưa đặt ở HTTP connector | Đặt rõ connect/read/response timeout |

**Một số giới hạn cần nhớ:** HTTP interface không tự động xử lý mọi biến thể protocol. Những request đặc biệt có thể cần **customArgumentResolver** hoặc đi trực tiếp vào fluent client API. **RequestEntity** thường không được hỗ trợ như một tham số HTTP service method vì đã mang toàn bộ HTTP request; thay bằng các tham số annotation cụ thể.

## 10. Checklist áp dụng vào dự án

1. Viết contract Java interface với đúng method, URL, tham số, request/response type.
2. Chọn RestClientAdapter nếu caller đồng bộ, WebClientAdapter nếu kết quả cần Mono/Flux và ứng dụng xử lý reactive.
3. Tạo RestClient/WebClient có base URL, auth, timeout, converter/codec và error policy phù hợp.
4. Tạo proxy bằng **HttpServiceProxyFactory.builderFor(adapter).build().createClient(Interface.class)**.
5. Đăng ký interface dưới dạng bean và inject nơi sử dụng.
6. Xác nhận HTTP thực tế qua contract test, không chỉ compile Java.
7. Với reactive, giữ subscription, hủy, retry và backpressure đúng ngữ cảnh. Tránh block trên event-loop.

### Câu hỏi ôn tập

- Vì sao **@HttpExchange** interface vẫn cần **HttpServiceProxyFactory** và **adapter**?
- Có thể đổi RestClientAdapter sang WebClientAdapter mà giữ nguyên interface **Mono<Book>** hay không?
- Khi nào dùng **Book**, **ResponseEntity<Book>**, **Mono<Book>**, **Flux<Book>**?
- Nếu remote API trả JSON array, vì sao **List<Book>** tốt hơn raw **List**?
- Authentication, timeout, status handler nên được đặt ở factory hay underlying client?
- Khi một HTTP service method nhận **SearchFilter** tùy biến, dùng annotation có sẵn hay **HttpServiceArgumentResolver**?

## Tài liệu chính thức

- [Spring Framework 6.2 – REST Clients, HTTP Interface](https://docs.spring.io/spring-framework/reference/6.2/integration/rest-clients.html)
- [Spring Framework – tài liệu REST Clients hiện hành](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [HttpServiceProxyFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/service/invoker/HttpServiceProxyFactory.html)
- [RestClientAdapter Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/client/support/RestClientAdapter.html)
- [WebClientAdapter Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/reactive/function/client/support/WebClientAdapter.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-service-clients-004">Kiểu trả về đồng bộ, reactive và phản hồi ResponseEntity</a>

<details>
<summary>Xem chi tiết</summary>

# HTTP Service Client: Kiểu trả về đồng bộ, reactive và phản hồi ResponseEntity

## Mục tiêu

Spring Framework cho phép khai báo lời gọi HTTP từ xa dưới dạng **Java interface** có `@HttpExchange`, `@GetExchange`, `@PostExchange`… rồi tạo implementation bằng `HttpServiceProxyFactory`. Interface không chứa code tự gọi HTTP; **adapter** sẽ quyết định dùng `RestClient`, `RestTemplate` hoặc `WebClient`.

Sau bài này, bạn có thể:
- Chọn **kiểu trả về đồng bộ** như `Book`, `List<Book>`, `void`, `HttpHeaders`, `ResponseEntity<Book>`.
- Chọn **kiểu trả về reactive** như `Mono<Book>`, `Flux<Book>`, `Mono<Void>`, `Mono<ResponseEntity<Book>>` và `Mono<ResponseEntity<Flux<Book>>>`.
- Hiểu lúc nào thực sự có HTTP I/O, lúc nào body đã được giải mã, và cách xem status/header.
- Thiết kế API client đúng ngữ nghĩa, xử lý lỗi 4xx/5xx, tránh block nhầm event loop và tránh nhầm kiểu return của HTTP client với return của Spring MVC/WebFlux controller.

Phạm vi là **HTTP Interface Client phía gọi dịch vụ** trong Spring Framework 6.1–6.2, Java 17+, không phải một bài tổng quan tất cả kiểu return của controller.

## 1. Tại sao kiểu trả về phụ thuộc HTTP adapter?

Ví dụ cùng endpoint `GET /books/7`, hai interface sau có thể cùng đọc một body JSON, nhưng có cách sử dụng rất khác:

```java
interface SyncBooks {
    @GetExchange("/books/{id}")
    Book getBook(@PathVariable("id") long id);
}

interface ReactiveBooks {
    @GetExchange("/books/{id}")
    Mono<Book> getBook(@PathVariable("id") long id);
}
```

`Book` là DTO, ví dụ `record Book(long id, String title) {}`.

- `SyncBooks.getBook(7)`: lời gọi chỉ trả về `Book` sau khi đã hoàn thành phần HTTP I/O/đọc body, hoặc ném exception. Luồng thực thi của caller **chờ kết quả**.
- `ReactiveBooks.getBook(7)`: lời gọi lập tức cung cấp một `Mono<Book>` mô tả xử lý HTTP. **Subscription** bắt đầu pipeline và sau đó phát body hoặc tín hiệu lỗi. Không được hiểu `Mono<Book>` là `Book` đã có sẵn.
- Nếu cần status/header bên cạnh body, dùng `ResponseEntity<Book>` hoặc `Mono<ResponseEntity<Book>>`, tùy adapter và mô hình thực thi.

`HttpServiceProxyFactory` là thành phần tạo proxy, còn adapter gắn proxy vào client thực. Ký hiệu khái quát:

```text
Interface @GetExchange/@PostExchange
           |
           v
HttpServiceProxyFactory -> dynamic proxy
           |
           +-- RestClientAdapter / RestTemplateAdapter
           |       -> HTTP I/O đồng bộ -> T, HttpHeaders, ResponseEntity<T>, ...
           |
           +-- WebClientAdapter
                   -> HTTP I/O reactive -> Mono<T>, Flux<T>,
                      Mono<ResponseEntity<T>>, ...
```

**Quy tắc cốt lõi:** Kiểu return phải được **adapter bên dưới hỗ trợ**. Không thể khai báo tùy ý `Mono<Book>` rồi dùng `RestClientAdapter` với kỳ vọng Spring tự biến request thành non-blocking. `WebClientAdapter` ngoài reactive còn hỗ trợ kiểu đồng bộ, nhưng đồng bộ trên client reactive vẫn phải chờ kết quả; thường nên dùng dạng reactive khi ứng dụng đang chạy trên WebFlux.

## 2. Khởi tạo HTTP Service Client

### 2.1. Interface chỉ mô tả hợp đồng HTTP

```java
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

record Book(long id, String title) {}

interface BookHttpApi {
    @GetExchange("/books/{id}")
    ResponseEntity<Book> getBook(@PathVariable("id") long id);
}
```

`@GetExchange` đánh dấu HTTP GET và URI template; `@PathVariable` cung cấp biến `id`. Java interface có thể chứa nhiều phương thức cho các endpoint khác nhau. Các annotation khai báo request, không quyết định blocking/reactive một cách độc lập.

### 2.2. Adapter đồng bộ với RestClient

```java
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

RestClient restClient = RestClient.builder()
        .baseUrl("https://catalog.example.com")
        .build();

BookHttpApi api = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(BookHttpApi.class);

ResponseEntity<Book> response = api.getBook(7);
```

Đây là code **minh họa đặt trong method/configuration**, không phải một Java file chạy độc lập. `RestClientAdapter` có từ Spring Framework 6.1, sử dụng `HttpExchangeAdapter` và hỗ trợ kiểu return đồng bộ.

### 2.3. Adapter reactive với WebClient

```java
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

WebClient webClient = WebClient.builder()
        .baseUrl("https://catalog.example.com")
        .build();

ReactiveBookApi api = HttpServiceProxyFactory
        .builderFor(WebClientAdapter.create(webClient))
        .build()
        .createClient(ReactiveBookApi.class);
```

Ở ví dụ trên, `ReactiveBookApi` là interface riêng với phương thức trả về `Mono`/`Flux`; nó được định nghĩa trong phần tiếp theo. `WebClientAdapter` hỗ trợ `ReactorHttpExchangeAdapter`. Nếu project đang dùng Spring 6.0, một số builder factory APIs khác với ví dụ Spring 6.1+; kiểm tra Javadoc của phiên bản thực tế.

Trong Spring Boot, ưu tiên builder do framework cung cấp để nhận cấu hình chung, codecs và customizers. Nếu chỉ sử dụng `RestClient`, dùng dependency Spring Web tương ứng; nếu dùng `WebClient` và Reactor, thường cần `spring-boot-starter-webflux`. Việc dùng WebClient làm HTTP client không bắt buộc controller phía server phải chạy WebFlux.

## 3. Nhóm kiểu trả về đồng bộ

Adapter của `RestClient` hoặc `RestTemplate` hỗ trợ những kiểu return sau:

| Kiểu trả về của interface | Ý nghĩa sau khi method kết thúc | Khi nên dùng |
| --- | --- | --- |
| `void` | Request đã được thực hiện; bỏ qua nội dung response | Lệnh không cần body/status |
| `HttpHeaders` | Có response headers; không giữ body làm kết quả | Chỉ cần ETag, content type hoặc metadata |
| `T` | Response body đã được giải mã thành `T` | Chỉ cần dữ liệu nghiệp vụ |
| `ResponseEntity<Void>` | Có HTTP status + headers, không cần body | DELETE/POST/HEAD cần kiểm tra status |
| `ResponseEntity<T>` | Có status + headers + body `T` | Cần cả payload lẫn metadata HTTP |

Ví dụ:

```java
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.RequestBody;

interface SyncBookApi {
    @GetExchange("/books/{id}")
    Book getBook(@PathVariable("id") long id);

    @GetExchange("/books")
    java.util.List<Book> listBooks();

    @GetExchange("/books/{id}")
    HttpHeaders getBookHeaders(@PathVariable("id") long id);

    @GetExchange("/books/{id}")
    ResponseEntity<Book> getBookResponse(@PathVariable("id") long id);

    @DeleteExchange("/books/{id}")
    ResponseEntity<Void> deleteBook(@PathVariable("id") long id);

    @PostExchange("/books/refresh")
    void requestRefresh();
}
```

Đoạn này là **hợp đồng minh họa**: backend phải thực sự cung cấp các endpoint. `Book` đã là DTO ở phần trước, `List<Book>` diễn đạt một JSON array khi codec hỗ trợ. Chú ý `void` chỉ nói không có giá trị trả về ở Java; HTTP vẫn thực sự có status và có thể báo lỗi.

### 3.1. T (DTO, String, List<T>)

```java
Book book = api.getBook(7);
System.out.println(book.title());
```

Với `T`, code gọi nhận body trực tiếp sau khi request hoàn tất. Nếu server trả HTTP 204 hoặc body rỗng, không nên giả định một DTO hợp lệ luôn tồn tại; kết quả thiếu body phải được xử lý theo hợp đồng endpoint. Tránh dereference `book.title()` khi có khả năng kết quả null.

Đối với collection/generic body, proxy có thể dùng thông tin generic ở chữ ký method để codec giải mã đúng `List<Book>`; đừng dùng `Object`/raw `List` nếu cần kiểm soát cấu trúc dữ liệu.

### 3.2. void và HttpHeaders

`void` phù hợp khi thành công/thất bại của HTTP call là điều duy nhất caller quan tâm, ví dụ kích hoạt thao tác không trả dữ liệu. `HttpHeaders` phù hợp khi chỉ muốn metadata, ví dụ:

```java
HttpHeaders headers = syncApi.getBookHeaders(7);
String etag = headers.getETag();
```

Điều này không có nghĩa bạn luôn biết status cụ thể. Nếu cần phân biệt HTTP 200 và 202 hoặc kiểm tra `Location` kèm status 201, chọn `ResponseEntity<Void>` hoặc `ResponseEntity<T>`.

### 3.3. ResponseEntity<T> và ResponseEntity<Void>

`org.springframework.http.ResponseEntity<T>` là đối tượng chứa ba phần:

```text
ResponseEntity<T>
  - statusCode : HttpStatusCode
  - headers    : HttpHeaders
  - body       : T, có thể null
```

Ví dụ:

```java
ResponseEntity<Book> response = syncApi.getBookResponse(7);

int status = response.getStatusCode().value();
String etag = response.getHeaders().getETag();
Book body = response.getBody();
```

`ResponseEntity<Void>` là lựa chọn rõ ý cho endpoint không cần payload:

```java
ResponseEntity<Void> response = syncApi.deleteBook(7);
boolean noContent = response.getStatusCode().value() == 204;
```

**Lưu ý:** `ResponseEntity` nhận từ HTTP client là **phản hồi của dịch vụ ở xa**. Nó không tự được gửi tiếp cho trình duyệt. Khi trả từ một controller, Spring MVC/WebFlux lại dùng `ResponseEntity` để **xây phản hồi server của ứng dụng mình**. Hai ngữ cảnh có cùng class nhưng trách nhiệm khác nhau.

## 4. Nhóm kiểu trả về reactive

Với `WebClientAdapter`, các kiểu đồng bộ ở phần trước **vẫn có thể dùng**, đồng thời có các kiểu reactive sau:

| Kiểu | Dữ liệu và thời điểm có sẵn | Trường hợp sử dụng |
| --- | --- | --- |
| `Mono<Void>` | Tín hiệu hoàn tất/lỗi, không phát giá trị body | Lệnh không cần dữ liệu |
| `Mono<HttpHeaders>` | Headers xuất hiện bất đồng bộ | Chỉ đọc metadata |
| `Mono<T>` | 0 hoặc 1 body object được giải mã | Một resource |
| `Flux<T>` | Nhiều item theo reactive stream | Danh sách/stream |
| `Mono<ResponseEntity<Void>>` | Status + headers bất đồng bộ, không cần body | Thao tác cần quan sát status |
| `Mono<ResponseEntity<T>>` | Status + headers + body `T` bất đồng bộ | Đọc một body + status |
| `Mono<ResponseEntity<Flux<T>>>` | Status + headers bất đồng bộ, kèm stream body `Flux<T>` | Cần metadata trước/đồng thời đọc stream nhiều item |

Một interface minh họa:

```java
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

interface ReactiveBookApi {
    @GetExchange("/books/{id}")
    Mono<Book> getBook(@PathVariable("id") long id);

    @GetExchange("/books")
    Flux<Book> streamBooks();

    @GetExchange("/books/{id}")
    Mono<HttpHeaders> getBookHeaders(@PathVariable("id") long id);

    @GetExchange("/books/{id}")
    Mono<ResponseEntity<Book>> getBookResponse(@PathVariable("id") long id);

    @GetExchange("/books")
    Mono<ResponseEntity<Flux<Book>>> streamWithResponse();

    @DeleteExchange("/books/{id}")
    Mono<ResponseEntity<Void>> deleteBook(@PathVariable("id") long id);
}
```

Không phải endpoint JSON nào cũng phát item liên tục. `Flux<Book>` là kiểu phản ứng nhiều phần tử; việc truyền dữ liệu tuần tự trong quá trình tải phụ thuộc server, media type và codec. JSON array thường có thể được giải mã thành Flux các phần tử, nhưng nếu cần streaming thực sự trong thời gian dài, cần một giao thức/media type thích hợp như NDJSON hoặc SSE.

### 4.1. Mono<T> và Flux<T>

```java
Mono<Book> one = reactiveApi.getBook(7);
Flux<Book> many = reactiveApi.streamBooks();

Mono<String> name = one.map(Book::title);
Mono<java.util.List<Book>> collected = many.collectList();
```

`one` và `many` chỉ là publisher; không có giá trị ở đây để sử dụng đồng bộ. Khi trong WebFlux controller:

```java
@GetMapping("/my-books/{id}")
public Mono<Book> fetch(@PathVariable long id) {
    return reactiveApi.getBook(id)
            .map(book -> new Book(book.id(), book.title()));
}
```

Đoạn controller này chỉ minh họa cách trả publisher, cần import `org.springframework.web.bind.annotation.GetMapping` và khai báo bên trong controller thực tế. Framework sẽ điều phối subscription; không cần `block()` ở đây.

- `Mono.empty()` biểu diễn hoàn tất không có item, **không giống** `Mono.just(null)` (Reactor không chấp nhận phần tử null).
- `Flux<T>` có thể hoàn thành, báo lỗi hoặc kéo dài vô hạn tùy server.
- `flatMap` dùng khi bước tiếp theo lại trả publisher, trong khi `map` dùng khi phép biến đổi trả giá trị thường.
- Khi gặp lỗi, publisher phát `onError`; kiểu trả về `Mono<T>` không biến HTTP 404 thành “có dữ liệu rỗng” theo mặc định.

### 4.2. Mono<Void> là tín hiệu hoàn tất

```java
interface ReactiveCommandApi {
    @PostExchange("/refresh")
    Mono<Void> refresh();
}
```

`Mono<Void>` không phát DTO; nó cho phép đợi request hoàn tất, phối hợp nhiều thao tác và nhận lỗi:

```java
Mono<Void> next = commandApi.refresh()
        .then(Mono.fromRunnable(() ->
                System.out.println("Refresh request completed")));
```

Trong đoạn trên, dòng in chỉ xảy ra khi publisher được subscribe và request kết thúc thành công. Nếu cần status 202 hay header `Retry-After`, nên dùng `Mono<ResponseEntity<Void>>`.

## 5. ResponseEntity trong reactive: chọn thứ tự bọc cho đúng

Đây là điểm dễ sai nhất vì các kiểu trông gần giống nhau.

### 5.1. Mono<ResponseEntity<T>>

```java
Mono<ResponseEntity<Book>> pending = reactiveApi.getBookResponse(7);

Mono<String> summary = pending.map(entity ->
        "status=" + entity.getStatusCode().value()
                + ", title=" + entity.getBody().title());
```

Phần tử do `Mono` phát ra là **một ResponseEntity đã có status/headers và body Book được giải mã**. `getBody()` vẫn có thể null nếu endpoint không trả body; code ví dụ trên giả định HTTP 200 có JSON hợp lệ. Dùng `Mono<ResponseEntity<T>>` khi muốn đồng thời xử lý body và status.

### 5.2. Mono<ResponseEntity<Flux<T>>>

```java
Mono<ResponseEntity<Flux<Book>>> pending =
        reactiveApi.streamWithResponse();

Mono<Integer> count = pending.flatMap(entity -> {
    int status = entity.getStatusCode().value();
    Flux<Book> body = entity.getBody();
    if (status != 200 || body == null) {
        return Mono.error(new IllegalStateException(
                "Unexpected status or missing body"));
    }
    return body.count().map(Long::intValue);
});
```

**Hai tầng bất đồng bộ:** `Mono` ngoài phát `ResponseEntity` có status/headers; `Flux<Book>` bên trong đại diện phần body có thể được tiêu thụ theo luồng. Đây là kiểu hữu ích khi cần truy cập metadata của response **mà không thu thập toàn bộ stream thành một danh sách trước**. Vì nó có thể stream, tránh `collectList()` trên luồng rất lớn/vô hạn nếu không có giới hạn.

### 5.3. Vì sao không mặc định dùng ResponseEntity<Mono<T>>?

Ở **WebFlux controller** (server), kiểu như `ResponseEntity<Mono<Book>>` hoặc `Mono<ResponseEntity<Book>>` có ý nghĩa khác nhau theo việc status/headers có sẵn trước hay sau publisher. Tuy nhiên, **kiểu return do proxy HTTP Interface Client hỗ trợ phụ thuộc tài liệu của HTTP adapter**. Với `WebClientAdapter`, chọn các kiểu chính thức được hỗ trợ như `Mono<ResponseEntity<T>>` hoặc `Mono<ResponseEntity<Flux<T>>>`; không bê nguyên bảng return của WebFlux controller sang interface client và mặc định mọi kiểu đều được hỗ trợ.

## 6. Ví dụ Java 17+ chạy được: so sánh cùng một server

Demo bên dưới chạy HTTP server nội bộ bằng JDK rồi gọi bằng cả `RestClientAdapter` và `WebClientAdapter`. Server có hai endpoint:

- `GET /books/7`: trả một `Book` cùng header `X-Source`.
- `GET /books`: trả JSON array hai cuốn sách.

Demo kiểm chứng `Book`, `ResponseEntity<Book>`, `Mono<Book>`, `Mono<ResponseEntity<Book>>` và `Flux<Book>`. Chương trình console được phép dùng `block(Duration)` để chờ kết quả cuối cùng; ứng dụng reactive thông thường nên tiếp tục trả publisher.

**Cấu trúc:**

```text
http-service-client-demo/
  build.gradle
  src/main/java/demo/HttpServiceReturnDemo.java
```

**build.gradle**:

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
    mainClass = 'demo.HttpServiceReturnDemo'
}
```

**src/main/java/demo/HttpServiceReturnDemo.java**:

```java
package demo;

import com.sun.net.httpserver.HttpServer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class HttpServiceReturnDemo {
    public record Book(long id, String title) {}

    interface SyncBooks {
        @GetExchange("/books/{id}")
        Book getBook(@PathVariable("id") long id);

        @GetExchange("/books/{id}")
        ResponseEntity<Book> getResponse(@PathVariable("id") long id);
    }

    interface ReactiveBooks {
        @GetExchange("/books/{id}")
        Mono<Book> getBook(@PathVariable("id") long id);

        @GetExchange("/books/{id}")
        Mono<ResponseEntity<Book>> getResponse(
                @PathVariable("id") long id);

        @GetExchange("/books")
        Flux<Book> getAllBooks();
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/books", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                String json;
                int status;

                if (!"GET".equals(exchange.getRequestMethod())) {
                    json = "{\"error\":\"method not allowed\"}";
                    status = 405;
                } else if ("/books/7".equals(path)) {
                    json = "{\"id\":7,\"title\":\"Reactive Java\"}";
                    status = 200;
                } else if ("/books".equals(path)) {
                    json = "[{\"id\":7,\"title\":\"Reactive Java\"},"
                            + "{\"id\":8,\"title\":\"Spring HTTP\"}]";
                    status = 200;
                } else {
                    json = "{\"error\":\"not found\"}";
                    status = 404;
                }

                byte[] data = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders()
                        .set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("X-Source", "local-api");
                exchange.sendResponseHeaders(status, data.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(data);
                }
            } finally {
                exchange.close();
            }
        });

        server.start();
        try {
            String baseUrl = "http://127.0.0.1:"
                    + server.getAddress().getPort();

            RestClient restClient = RestClient.builder()
                    .baseUrl(baseUrl)
                    .build();
            SyncBooks sync = HttpServiceProxyFactory
                    .builderFor(RestClientAdapter.create(restClient))
                    .build()
                    .createClient(SyncBooks.class);

            WebClient webClient = WebClient.builder()
                    .baseUrl(baseUrl)
                    .build();
            ReactiveBooks reactive = HttpServiceProxyFactory
                    .builderFor(WebClientAdapter.create(webClient))
                    .build()
                    .createClient(ReactiveBooks.class);

            Book direct = sync.getBook(7);
            System.out.println("sync=" + direct.id()
                    + "/" + direct.title());

            ResponseEntity<Book> syncResponse = sync.getResponse(7);
            System.out.println("sync status="
                    + syncResponse.getStatusCode().value()
                    + ", source="
                    + syncResponse.getHeaders().getFirst("X-Source"));

            Mono<Book> pending = reactive.getBook(7);
            Book asyncBook = pending.block(Duration.ofSeconds(5));
            System.out.println("reactive=" + asyncBook.id()
                    + "/" + asyncBook.title());

            ResponseEntity<Book> asyncResponse = reactive
                    .getResponse(7)
                    .block(Duration.ofSeconds(5));
            System.out.println("reactive status="
                    + asyncResponse.getStatusCode().value());

            List<Book> allBooks = reactive.getAllBooks()
                    .collectList()
                    .block(Duration.ofSeconds(5));
            System.out.println("books=" + allBooks.size());
        } finally {
            server.stop(0);
        }
    }
}
```

Để chạy: cài JDK 17+ và Gradle; ở thư mục `http-service-client-demo` chạy:

```bash
gradle run
```

Nếu dự án đã có Gradle Wrapper, dùng `./gradlew run` trên Unix/macOS hoặc `gradlew.bat run` trên Windows.

**Các dòng chương trình in ra stdout** (không tính log tải dependency/Gradle):

```text
sync=7/Reactive Java
sync status=200, source=local-api
reactive=7/Reactive Java
reactive status=200
books=2
```

Giải thích:

1. `sync.getBook(7)` dùng `RestClient` và trả về đối tượng `Book` sau lời gọi HTTP đồng bộ.
2. `sync.getResponse(7)` trả thêm status 200 và header `X-Source: local-api`.
3. `reactive.getBook(7)` trả `Mono<Book>`; `block` ở `main` kích hoạt pipeline rồi đợi giá trị.
4. `reactive.getResponse(7)` bọc toàn bộ phản hồi trong `Mono<ResponseEntity<Book>>`.
5. `reactive.getAllBooks()` trả `Flux<Book>`, `collectList` gom thành hai phần tử. Điều này hợp lý cho ví dụ hai item; với stream lớn, giữ cách xử lý từng item.

Demo dùng `HttpServer` của JDK; không cần chạy Spring Boot server riêng. URL được tạo bằng cổng hệ điều hành cấp phát nên không phụ thuộc một cổng cố định.

## 7. Khi nào chọn kiểu return nào?

| Nhu cầu thực tế | Adapter thích hợp | Kiểu return gợi ý |
| --- | --- | --- |
| Ứng dụng xử lý tuần tự, cần một DTO | `RestClientAdapter` | `Book` |
| Gọi API để trigger, bỏ qua body | `RestClientAdapter` | `void` |
| Chỉ cần headers đồng bộ | `RestClientAdapter` | `HttpHeaders` |
| DELETE cần kiểm tra 204 | `RestClientAdapter` | `ResponseEntity<Void>` |
| Đọc body và metadata HTTP đồng bộ | `RestClientAdapter` | `ResponseEntity<Book>` |
| WebFlux service đọc một DTO | `WebClientAdapter` | `Mono<Book>` |
| WebFlux đọc stream nhiều item | `WebClientAdapter` | `Flux<Book>` |
| WebFlux gọi lệnh chỉ quan tâm hoàn tất | `WebClientAdapter` | `Mono<Void>` |
| WebFlux cần status/header kèm một DTO | `WebClientAdapter` | `Mono<ResponseEntity<Book>>` |
| WebFlux cần status/header trước khi tiêu thụ stream body | `WebClientAdapter` | `Mono<ResponseEntity<Flux<Book>>>` |

Cách chọn dựa trên ba câu hỏi: (1) caller đang chạy đồng bộ hay reactive? (2) cần chỉ body hay cả status/header? (3) response gồm 0, 1 hay nhiều phần tử, và có cần streaming thật không?

**Không tự biến reactive thành blocking** chỉ vì muốn return `Book`. Nếu tầng trên đang dùng `Mono`/`Flux`, giữ chuỗi reactive đến biên hệ thống. Nếu ứng dụng cần đồng bộ, `RestClient` thường đơn giản hơn `WebClient` với kiểu return đồng bộ.

## 8. Lỗi HTTP, timeout và phạm vi ResponseEntity

### 8.1. ResponseEntity không đồng nghĩa chấp nhận mọi status

Với cấu hình mặc định, HTTP 4xx/5xx thường được client phát thành exception/tín hiệu lỗi:

- `RestClientAdapter`: thông qua `RestClient`, lỗi HTTP thông thường được phản ánh bằng `RestClientResponseException` và các subtype liên quan.
- `WebClientAdapter`: thông qua `WebClient`, lỗi HTTP thông thường được phản ánh bằng `WebClientResponseException` trên reactive error channel.

Điều này vẫn đúng khi khai báo `ResponseEntity<T>`; đừng kỳ vọng `ResponseEntity<Book>` sẽ tự động chứa body lỗi 404 trong mọi trường hợp. Muốn mapping status khác, hãy cấu hình **error/status handler trên HTTP client gốc** hoặc xử lý lỗi có chủ đích ở tầng service.

Ví dụ xử lý lỗi reactive mà vẫn giữ `Mono<Book>`:

```java
Mono<Book> book = reactiveApi.getBook(7)
        .onErrorResume(
                org.springframework.web.reactive.function.client
                        .WebClientResponseException.NotFound.class,
                notFound -> Mono.empty());
```

Ở đây 404 được chủ động chuyển thành hoàn tất rỗng. Không áp dụng chung cho mọi endpoint: có API dùng 404 để báo lỗi cấu hình hoặc quyền truy cập mà không nên âm thầm bỏ qua.

Ví dụ đồng bộ:

```java
try {
    Book book = syncApi.getBook(7);
} catch (org.springframework.web.client.HttpClientErrorException.NotFound ex) {
    // Xử lý riêng khi server trả HTTP 404.
}
```

Các đoạn xử lý lỗi trên giả định `syncApi`/`reactiveApi` được tạo bằng cấu hình client mặc định. Loại exception thực tế phụ thuộc adapter và status handler.

### 8.2. Timeout và blocking

Với `RestClientAdapter`, lời gọi sync chặn đến khi hoàn tất hoặc xảy ra timeout/lỗi cấu hình. Với `WebClientAdapter`, `Mono`/`Flux` không buộc thread của caller chờ; nhưng **kiểu đồng bộ** được dùng với adapter reactive sẽ phải đợi kết quả. Việc này có thể làm tắc event-loop và đánh mất lợi ích reactive.

Chọn connect/read/response timeout thích hợp ở HTTP client bên dưới. Với `WebClientAdapter`, Spring cũng hỗ trợ cấu hình timeout cho các lời gọi blocking, nhưng thường nên cấu hình timeout thực tế của HTTP connector để kiểm soát việc kết nối/đọc tốt hơn. `Mono.timeout(Duration)` áp dụng tại reactive pipeline nhưng không thay thế tất cả cấu hình mạng.

Đừng gọi `block()` trong WebFlux controller, Netty event loop hoặc code giữ vai trò xử lý reactive liên tục. Nếu cần tương tác với thư viện blocking, cân nhắc tách ranh giới và chuyển công việc blocking sang scheduler chuyên dụng; điều đó **không biến thư viện blocking thành non-blocking**.

### 8.3. Retry và số lần HTTP request

Một publisher reactive lạnh có thể phát lại request khi subscribe lại hoặc khi dùng `retry(...)`. Với GET idempotent, retry giới hạn có thể phù hợp cho một số lỗi mạng/transient; với POST tạo bản ghi/đơn hàng, cần hợp đồng idempotency rõ ràng. Không retry mù quáng 4xx và không đồng nhất tín hiệu `Mono.empty()` với HTTP 404.

## 9. Những nhầm lẫn phổ biến

1. **Khai báo `Mono<T>` nhưng dùng RestClientAdapter.** Adapter sync không hỗ trợ reactive return như WebClientAdapter; chọn adapter/return type nhất quán.
2. **Nghĩ WebClient luôn non-blocking khi return là `T`.** Một method sync vẫn phải chờ giá trị; reactive client hỗ trợ sync return không đồng nghĩa mọi luồng thực thi đều non-blocking.
3. **Nghĩ `Mono<T>` là kết quả đã có.** Nó là publisher, thường chưa có HTTP I/O trước subscription.
4. **Dùng `ResponseEntity<T>` khi chỉ cần body.** Lấy `T` hoặc `Mono<T>` trực tiếp để giảm coupling với chi tiết HTTP.
5. **Bỏ status/header khi workflow cần ETag, Location hoặc 202.** Dùng `ResponseEntity<T>`, `ResponseEntity<Void>` hoặc biến thể reactive tương ứng.
6. **Nhầm `Mono<ResponseEntity<T>>` với `ResponseEntity<Mono<T>>`.** Thứ tự bọc quyết định status/headers xuất hiện lúc nào; bảng return của controller không phải hợp đồng hỗ trợ của HTTP Interface Client.
7. **Dùng `Flux<T>` đồng nghĩa server luôn streaming.** Cần media type/codec và endpoint phù hợp; JSON array nhỏ có thể được gửi như một response hoàn chỉnh.
8. **Bỏ qua response rỗng.** `Mono<Void>` và `ResponseEntity<Void>` có ý nghĩa riêng; `getBody()` có thể null.
9. **Dùng ResponseEntity để “bắt” lỗi 404 trong mọi trường hợp.** Error handler mặc định của underlying client có thể đã biến 404 thành exception.
10. **Block ở event-loop hoặc retry POST không kiểm soát.** Cần tôn trọng mô hình xử lý và tính idempotent.
11. **Đọc quá nhiều dữ liệu vào bộ nhớ bằng `collectList()`.** Hữu ích cho danh sách nhỏ, nhưng không phù hợp với stream vô hạn hoặc payload lớn.
12. **Lẫn server controller với HTTP interface proxy.** `ResponseEntity` bên client mang phản hồi từ dịch vụ khác; bên controller dùng để xây HTTP response từ chính ứng dụng.

## 10. Bài tập ứng dụng

**Bài tập 1 — Chọn kiểu:** Endpoint `DELETE /jobs/{id}` trả 204 và header `X-Audit-Id`. Viết một phương thức đồng bộ và một phương thức reactive vẫn đọc được status/header. Gợi ý: `ResponseEntity<Void>` và `Mono<ResponseEntity<Void>>`.

**Bài tập 2 — So sánh return:** Trong demo, thêm vào `SyncBooks` một phương thức `HttpHeaders getHeaders(long id)` và in `X-Source`. Sau đó thêm `Mono<HttpHeaders>` tương ứng vào `ReactiveBooks`. Giải thích cách truy cập headers khác với giải mã body.

**Bài tập 3 — Status và stream:** Giả sử endpoint `/books` trả rất nhiều sách qua NDJSON và bạn cần đọc `X-Next-Cursor`. Giải thích vì sao `Mono<ResponseEntity<Flux<Book>>>` diễn đạt nhu cầu tốt hơn `Mono<List<Book>>`, và vì sao phải tránh `collectList()` với stream vô hạn.

**Bài tập 4 — Debug lỗi:** Thay `/books/7` bằng `/books/404` trong demo. Server sẽ trả HTTP 404. Quan sát sự khác biệt: method sync ném exception tại lời gọi, trong khi reactive `Mono` phát lỗi khi được subscribe. Vì sao việc return `ResponseEntity<Book>` không tự đảm bảo rằng bạn có thể đọc status 404 như response thành công?

## Tài liệu tham khảo chính thức

- Spring Framework 6.2, REST Clients / HTTP Interface / Return Values: https://docs.spring.io/spring-framework/reference/6.2/integration/rest-clients.html
- Spring WebClient: https://docs.spring.io/spring-framework/reference/6.2/web/webflux-webclient.html
- WebClientAdapter Javadoc: https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/web/reactive/function/client/support/WebClientAdapter.html
- RestClientAdapter Javadoc: https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/web/client/support/RestClientAdapter.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-service-clients-005">Cấu hình header, URI, chuyển đổi dữ liệu và xử lý lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# HTTP Service Client: Cấu hình header, URI, chuyển đổi dữ liệu và xử lý lỗi

## 1. HTTP Service Client là gì và các lớp cấu hình nằm ở đâu?

HTTP Service Client của Spring Framework cho phép định nghĩa hợp đồng gọi HTTP bằng **Java interface**, kết hợp `@HttpExchange`, `@GetExchange`, `@PostExchange`… và các annotation trên tham số. `HttpServiceProxyFactory` tạo proxy triển khai interface và giao phần gửi request/đọc response cho HTTP client thực tế. Cơ chế này xuất hiện từ Spring Framework 6.0; `RestClientAdapter` hỗ trợ `RestClient` từ Spring Framework 6.1. Ví dụ trong bài dùng **Java 17+, Spring Boot 3.3.x (Spring Framework 6.1.x)**.

```text
Code gọi nghiệp vụ
      │
      ▼
CatalogApi interface (@HttpExchange, @GetExchange, @PostExchange)
      │  tham số method → path/query/header/body của request
      ▼
HttpServiceProxyFactory (tạo proxy)
      │
      ▼
RestClientAdapter / WebClientAdapter
      │  baseUrl, defaultHeader, request factory/connector,
      │  converters/codecs, status handlers, timeout...
      ▼
HTTP request → máy chủ → HTTP response → Java object / exception
```

Có **hai tầng cấu hình bổ sung nhau**:

- **Trên interface và method:** URL tương đối, HTTP method, `Accept`/`Content-Type`, đường dẫn, query, header theo từng lần gọi, body và kiểu dữ liệu trả về. Đây là hợp đồng API dễ đọc và kiểm tra.
- **Trên HTTP client nền:** base URL (host/service), header mặc định, xác thực, message converters/codecs, timeout/transport, quy tắc lỗi HTTP dùng chung. Proxy không tự cung cấp một bộ codec hay transport khác với adapter được gắn vào nó.

`RestClient` dùng cách gọi đồng bộ, thích hợp interface trả `T`, `List<T>` hoặc `ResponseEntity<T>`. `WebClient` là client reactive và có thể dùng interface trả `Mono<T>`, `Flux<T>`. Không khai báo `Mono<T>` trên interface dùng `RestClientAdapter` rồi kỳ vọng reactive execution.

## 2. Định nghĩa interface và tạo proxy

Ví dụ hợp đồng quản lý sản phẩm:

```java
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

record Product(long id, String name, String category) {}
record CreateProduct(String name, String category) {}

@HttpExchange(url = "/api/products", accept = MediaType.APPLICATION_JSON_VALUE)
interface CatalogApi {

    @GetExchange("/{id}")
    Product findById(
        @PathVariable("id") long id,
        @RequestHeader("X-Request-Id") String requestId
    );

    @GetExchange("/search")
    List<Product> findByCategory(@RequestParam("category") String category);

    @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    Product create(@RequestBody CreateProduct request);
}
```

Đây là **mã minh họa** (chưa gồm `main`). `@HttpExchange` trên interface đặt URL chung và `Accept: application/json`. `@GetExchange`/`@PostExchange` định nghĩa HTTP method và phần URL còn lại. Header `X-Request-Id` được nhận theo từng lần gọi. Body `CreateProduct` được mã hóa thành JSON nếu có converter phù hợp, và JSON response được giải mã thành `Product`.

Cấu hình đồng bộ bằng `RestClient`:

```java
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

RestClient restClient = RestClient.builder()
    .baseUrl("https://catalog.example.com")
    .defaultHeader("X-Client-App", "java-learning")
    .build();

CatalogApi catalog = HttpServiceProxyFactory
    .builderFor(RestClientAdapter.create(restClient))
    .build()
    .createClient(CatalogApi.class);

Product item = catalog.findById(7, "req-007");
```

Đây là **mã minh họa**; URL cần là service thật. Từ Spring Framework 6.1, `builderFor(RestClientAdapter.create(...))` là API thích hợp. Trong ứng dụng Spring Boot, nên đăng ký proxy một lần dưới dạng bean và inject vào các service, thay vì tạo factory cho mỗi request.

Ví dụ bean:

```java
@Configuration
class CatalogClientConfiguration {

    @Bean
    CatalogApi catalogApi(RestClient.Builder builder) {
        RestClient client = builder
            .baseUrl("https://catalog.example.com")
            .defaultHeader("X-Client-App", "java-learning")
            .build();

        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(client))
            .build()
            .createClient(CatalogApi.class);
    }
}
```

Đoạn trên cần các import tương ứng cho `@Configuration`, `@Bean`, `RestClient`, `RestClientAdapter`, `HttpServiceProxyFactory` và `CatalogApi`. Khi sử dụng `RestClient.Builder` được Spring Boot cung cấp, bạn có thể thừa hưởng những customizer đã được cấu hình trong ứng dụng.

## 3. Header: mặc định, tĩnh theo API và động theo lời gọi

Header điều khiển metadata của request: content negotiation, định dạng body, xác thực, correlation ID, locale, v.v. Chọn nơi khai báo theo **phạm vi trách nhiệm**:

| Nguồn header | Ví dụ | Khi nên dùng |
| --- | --- | --- |
| `RestClient.Builder.defaultHeader(name, value)` | `X-Client-App: java-learning` | Giá trị mặc định áp dụng cho các method của cùng client |
| `WebClient.Builder.defaultHeader(name, value)` | `Accept-Language: vi` | Tương tự nhưng cho WebClient |
| `@HttpExchange(accept=..., contentType=...)` | `Accept: application/json` | Quy định media type trong hợp đồng HTTP interface |
| `@GetExchange(accept=...)` / `@PostExchange(contentType=...)` | `Content-Type: application/json` | Ghi đè media type ở method khi API khác nhau |
| `@RequestHeader("X-Request-Id")` | Giá trị từ tham số Java | Header thay đổi theo từng lời gọi |
| `@RequestHeader HttpHeaders` hoặc map phù hợp | Nhiều header động | Khi số lượng header thay đổi theo request |
| Interceptor/filter của client nền | Bearer token, trace propagation | Chính sách chung, đặc biệt khi token cần làm mới hoặc theo user |

Điểm hay bị hiểu sai: trong Spring Framework 6.1, `@HttpExchange` **không có thuộc tính `headers` tổng quát như `@RequestMapping`**. Sử dụng `accept`, `contentType`, `@RequestHeader` hoặc builder/filter ở client nền. `defaultHeader` chỉ bổ sung giá trị mặc định nếu header đó chưa được chỉ rõ trong request; tránh dựa vào việc chồng nhiều giá trị trùng tên mà không biết client/server diễn giải ra sao.

Ví dụ header động:

```java
@GetExchange("/{id}")
Product findById(
    @PathVariable("id") long id,
    @RequestHeader("X-Request-Id") String requestId,
    @RequestHeader("Authorization") String authorization
);

// Cách gọi:
Product p = catalog.findById(7, "req-007", "Bearer <access-token>");
```

Đoạn này là **minh họa biến thể chữ ký**; muốn chạy, cập nhật interface `CatalogApi` và truyền token hợp lệ. Không ghi access token/cookie nhạy cảm vào log và không nhúng token dài hạn vào annotation hoặc source code. Nếu auth là trách nhiệm chung, interceptor (`RestClient`) hoặc filter (`WebClient`) thường là vị trí thích hợp hơn.

**`Accept` khác `Content-Type`:** `Accept` mô tả các media type client muốn nhận; `Content-Type` mô tả kiểu body **gửi đi**. GET không có request body thường không cần `Content-Type`. Với POST JSON, cả `Content-Type: application/json` của request và `Accept: application/json` cho response đều có ích. Sai media type có thể dẫn tới HTTP 406/415 hoặc lỗi chuyển đổi nội dung.

## 4. URI: base URL, path variable, query và URI động

Với client có `baseUrl("https://catalog.example.com")`, các annotation:

```java
@HttpExchange(url = "/api/products")
interface CatalogApi {

    @GetExchange("/{id}")
    Product findById(@PathVariable("id") long id);

    @GetExchange("/search")
    List<Product> search(
        @RequestParam("category") String category,
        @RequestParam("page") int page
    );
}
```

tạo URI tương ứng (ví dụ):

```text
GET https://catalog.example.com/api/products/7
GET https://catalog.example.com/api/products/search?category=office&page=2
```

`@PathVariable` thay các placeholder trong URL template; `@RequestParam` thêm query parameter cho GET và nhiều tình huống HTTP thông thường. Với request `application/x-www-form-urlencoded`, `@RequestParam` có thể trở thành trường form tương ứng theo hợp đồng HTTP interface. `@PathVariable("id")`/`@RequestParam("page")` khai báo tên rõ ràng, giúp không phụ thuộc metadata tên tham số được compiler giữ lại.

Một số lựa chọn động:

```java
import java.net.URI;

@GetExchange
Product loadFrom(URI absoluteUri);

@GetExchange("/search")
List<Product> searchWithBase(
    UriBuilderFactory uriBuilderFactory,
    @RequestParam("category") String category
);
```

Đây là **các method minh họa**, bổ sung import `org.springframework.web.util.UriBuilderFactory`. Tham số `URI` có thể đặt URL động và thay giá trị URL từ annotation của method. `UriBuilderFactory` cung cấp cách thay base URI/factory trong khi vẫn sử dụng đường dẫn tương đối do annotation mô tả. Chọn tham số URI tuyệt đối khi thực sự cần endpoint khác; **không cho phép URL do người dùng bên ngoài cung cấp tùy ý** nếu không có allowlist host/scheme và kiểm tra SSRF.

**Encoding:** dùng template + variable resolver thay cho nối chuỗi:

```java
// Ưu tiên:
@GetExchange("/customers/{customerId}")
Customer findCustomer(@PathVariable("customerId") String customerId);

// Tránh lắp URL bằng chuỗi chứa input chưa encode:
// "https://api.example.com/customers/" + untrustedCustomerId
```

Với ký tự khoảng trắng, `&`, `?`, `/` hay Unicode, cần hiểu vị trí path/query và cấu hình URI encoding của client. Đặc biệt, ký tự `/` trong một path variable có thể thay đổi ý nghĩa segment nếu không được encode theo chính sách mong muốn. Đừng tự `URLEncoder.encode` toàn bộ URL vì công cụ này dành cho form encoding, không thay thế URI builder. Với query có nhiều giá trị cùng tên, dùng tham số collection hoặc resolver phù hợp; kiểm tra contract thực tế của upstream.

## 5. Chuyển đổi request/response: Java object, JSON, collection

HTTP Service Client ủy nhiệm chuyển đổi cho client nền:

- `RestClient` dùng các `HttpMessageConverter` của Spring Web để viết/đọc body. Trong classpath có Jackson, `MappingJackson2HttpMessageConverter` thường xử lý JSON.
- `WebClient` dùng `HttpMessageReader`/`HttpMessageWriter` và các codec reactive. Cấu hình decoder/buffer của WebClient áp dụng cho proxy dùng `WebClientAdapter`.
- Annotation `@RequestBody` chỉ xác định tham số là request body; để serialize JSON cần converter/codec phù hợp, `Content-Type` phù hợp và DTO hợp lệ.
- Kiểu return trên method xác định đối tượng/collection cần decode. Lỗi JSON/schema có thể xảy ra **ngay cả khi HTTP status là 200**.

Ví dụ DTO Java 17:

```java
public record CreateProduct(String name, String category) {}

public record Product(long id, String name, String category) {}
```

Khi gọi:

```java
Product created = catalog.create(new CreateProduct("Pen", "office"));
```

Converter JSON sẽ gửi payload có dạng:

```json
{"name":"Pen","category":"office"}
```

Giả sử response từ server:

```json
{"id":8,"name":"Pen","category":"office"}
```

thì return type `Product` được deserialize thành `new Product(8, "Pen", "office")`. Với danh sách JSON `[{"id":7,...}]`, return `List<Product>` để Spring giữ thông tin phần tử generic; đừng dùng raw `List` rồi ép thủ công từng `Map` sang DTO. Khi response không có body (204), có thể dùng `void`/`ResponseEntity<Void>`; việc ép một DTO bắt buộc từ body trống thường không phù hợp với hợp đồng endpoint.

Kiểu return hay gặp:

| Return type HTTP interface | Ý nghĩa | Adapter phù hợp |
| --- | --- | --- |
| `Product`, `List<Product>` | Decode body | `RestClientAdapter` / `WebClientAdapter` |
| `ResponseEntity<Product>` | Giữ status, headers và body | Cả hai |
| `ResponseEntity<Void>` | Lấy status/headers, không cần body | Cả hai |
| `Mono<Product>` | Decode bất đồng bộ tối đa một phần tử | `WebClientAdapter` |
| `Flux<Product>` | Decode luồng 0..N phần tử | `WebClientAdapter` |
| `Mono<ResponseEntity<Flux<Product>>>` | Vừa lấy metadata vừa stream | `WebClientAdapter` |

Lưu ý: `ResponseEntity<Product>` không có nghĩa 404 sẽ tự được trả về bình thường; client mặc định có thể ném lỗi status **trước khi** tạo `ResponseEntity`. Nếu nghiệp vụ cần giữ 404 như kết quả dữ liệu, phải cấu hình chính sách status phù hợp và thiết kế DTO/body rõ ràng.

### Tùy biến conversion

`RestClient.Builder` có `messageConverters(...)` để bổ sung/sửa converters. Chỉ bổ sung converter khi có yêu cầu thực tế (format vendor-specific, media type riêng, ObjectMapper policy…). Đảm bảo thứ tự converter không vô tình thay Jackson chuẩn.

```java
RestClient custom = RestClient.builder()
    .baseUrl("https://catalog.example.com")
    .messageConverters(converters -> {
        // Thêm converter riêng tại đây nếu đã xác định rõ media type.
        // Không xóa converters mặc định chỉ để đổi một DTO.
    })
    .build();
```

Với WebClient, tùy biến tại `WebClient.Builder.codecs(configurer -> ...)`. Ví dụ tăng buffer decode hợp lý cho payload JSON lớn: `configurer.defaultCodecs().maxInMemorySize(1024 * 1024)`. Đây là hạn bộ nhớ **trong một thao tác decode cần buffering**, không giới hạn tổng bộ nhớ toàn bộ stream hay mọi collection downstream. Ưu tiên phân trang, stream hoặc giới hạn payload ở server hơn là tăng giới hạn vô tội vạ.

### Các lỗi chuyển đổi thường gặp

- `415 Unsupported Media Type`: server từ chối `Content-Type` của request. Kiểm tra method, converter và body.
- `406 Not Acceptable`: server không thể tạo nội dung khớp `Accept`; kiểm tra API negotiation.
- HTTP 200 nhưng body JSON không hợp lệ/không khớp DTO: có thể gặp `RestClientException` gắn lỗi message conversion hoặc `CodecException`/`DecodingException` với WebClient.
- Thiếu converter Jackson trong classpath hoặc cấu hình thiếu: không serialize/deserialize JSON như kỳ vọng.
- JSON object rất lớn với WebClient: có thể phát `DataBufferLimitException` do codec memory limit, khác với HTTP 413 do server trả về.

## 6. Xử lý lỗi HTTP Service Client ở tầng client nền

**Proxy không có phương thức `onStatus` để gọi nối sau một method của interface.** Định nghĩa chính sách xử lý lỗi ở `RestClient` / `WebClient` được truyền vào `HttpServiceProxyFactory`. Mặc định, cả hai thường phát exception khi nhận HTTP 4xx/5xx (RestClient thuộc họ `RestClientException`, WebClient thường là `WebClientResponseException`).

Với `RestClient` (Spring Framework 6.1):

```java
RestClient restClient = RestClient.builder()
    .baseUrl("https://catalog.example.com")
    .defaultStatusHandler(
        status -> status.value() == 404,
        (request, response) -> {
            throw new ProductNotFoundException(
                "Không tìm thấy sản phẩm: HTTP " + response.getStatusCode().value()
            );
        }
    )
    .build();

CatalogApi catalog = HttpServiceProxyFactory
    .builderFor(RestClientAdapter.create(restClient))
    .build()
    .createClient(CatalogApi.class);
```

`ProductNotFoundException` là class nghiệp vụ do bạn định nghĩa; đoạn này là mã minh họa. `defaultStatusHandler` cho phép map 404 thành exception nhất quán trên **mọi** method proxy dùng client đó. Handler được thử theo thứ tự đăng ký; nên đặt rule chuyên biệt trước rule 4xx/5xx tổng quát. Nếu không có handler tùy chỉnh phù hợp, mapping HTTP 4xx/5xx mặc định vẫn áp dụng.

Với `WebClient` reactive, hình thức handler khác:

```java
WebClient webClient = WebClient.builder()
    .baseUrl("https://catalog.example.com")
    .defaultStatusHandler(
        status -> status.value() == 404,
        response -> response.bodyToMono(String.class)
            .defaultIfEmpty("Not found")
            .map(message -> new ProductNotFoundException(message))
    )
    .build();

CatalogReactiveApi api = HttpServiceProxyFactory
    .builderFor(WebClientAdapter.create(webClient))
    .build()
    .createClient(CatalogReactiveApi.class);
```

Đây cũng là **mã minh họa** (cần khai báo `CatalogReactiveApi` với return reactive và các import). Callback của `WebClient` trả publisher phát `Throwable`; callback của `RestClient` là xử lý đồng bộ, thường `throw` exception. Không trộn kiểu handler của hai API.

### Bốn nhóm lỗi cần tách riêng

1. **HTTP 4xx/5xx:** upstream thực sự trả response. Có status, headers và đôi khi body lỗi. Ví dụ 404 nghiệp vụ, 429 rate limit, 500/503 upstream.
2. **Kết nối/timeout/TLS:** chưa chắc đã nhận HTTP response; xử lý bằng cấu hình transport, retry có giới hạn và quan sát cause. Không thể dùng HTTP status predicate để bắt các lỗi này.
3. **Serialization/deserialization:** JSON sai, DTO không khớp, converter/codecs thiếu, payload quá lớn. HTTP 200 vẫn có thể thất bại ở đây.
4. **Lỗi nghiệp vụ sau decode:** đối tượng hợp lệ về mặt JSON nhưng vi phạm yêu cầu domain. Kiểm tra sau khi nhận DTO, không gộp với lỗi HTTP status.

Với response lỗi mang JSON chi tiết, handler có thể đọc body thành DTO lỗi hoặc String rồi tạo exception nghiệp vụ, nhưng body response là stream chỉ tiêu thụ một lần. Giới hạn nội dung lỗi và tránh log token/sensitive fields. Handler **mặc định** ở client áp dụng cho toàn bộ proxy dùng client; nếu hai API có quy tắc lỗi khác nhau, hãy cấu hình client/adapter riêng hoặc chọn cơ chế handler chi tiết phù hợp.

### Retry, fallback và HTTP status

`onStatus` hoặc `defaultStatusHandler` chỉ quyết định lỗi nào được phát; retry là chính sách **riêng**. Retry có thể hợp lý với lỗi kết nối tạm thời, 502/503 hoặc 429 theo hợp đồng API, nhưng phải có giới hạn, backoff, deadline và xem `Retry-After` nếu thích hợp. Không retry mù thao tác POST gây tác dụng phụ nếu không có idempotency key. Với proxy đồng bộ, dùng resilience layer ngoài client (ví dụ Spring Retry/Resilience4j theo cấu hình đã chọn); với proxy reactive, có thể đặt `retryWhen(...)` trên `Mono`/`Flux`, cùng một error taxonomy rõ ràng.

## 7. Ví dụ độc lập Java 17+: GET, query, header, JSON POST và 404

Bài thực hành dùng `com.sun.net.httpserver.HttpServer` có sẵn trong JDK để chạy HTTP server ngay trên localhost. Kết nối không phụ thuộc internet, nhưng Gradle cần tải dependencies ở lần chạy đầu nếu chưa có cache. Client dùng **`RestClientAdapter`**; mã Java minh họa tất cả nội dung chính: URL động qua server port, header global + header method, path variable, query, body JSON qua Jackson, response thành record và handler cho HTTP 404.

Cấu trúc dự án:

```text
http-service-client-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/HttpServiceClientDemo.java
```

**`settings.gradle`**

```groovy
rootProject.name = 'http-service-client-demo'
```

**`build.gradle`** (Gradle 8.x, JDK 17+)

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web:3.3.13'
}

application {
    mainClass = 'demo.HttpServiceClientDemo'
}
```

**`src/main/java/demo/HttpServiceClientDemo.java`**

```java
package demo;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

public class HttpServiceClientDemo {
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Product(long id, String name, String category) {}
    public record CreateProduct(String name, String category) {}

    @HttpExchange(url = "/api/products", accept = MediaType.APPLICATION_JSON_VALUE)
    public interface CatalogApi {
        @GetExchange("/{id}")
        Product findById(
            @PathVariable("id") long id,
            @RequestHeader("X-Request-Id") String requestId
        );

        @GetExchange("/search")
        List<Product> search(@RequestParam("category") String category);

        @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
        Product create(@RequestBody CreateProduct request);
    }

    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 0), 0
        );
        server.createContext("/api/products", HttpServiceClientDemo::handle);
        server.start();

        try {
            RestClient underlyingClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .defaultHeader("X-Client-App", "java-learning")
                .defaultStatusHandler(
                    status -> status.value() == 404,
                    (request, response) -> {
                        throw new ProductNotFoundException(
                            "HTTP " + response.getStatusCode().value()
                        );
                    }
                )
                .build();

            CatalogApi api = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(underlyingClient))
                .build()
                .createClient(CatalogApi.class);

            Product item = api.findById(7, "request-007");
            System.out.println("GET: " + item);

            List<Product> results = api.search("office");
            System.out.println("SEARCH count: " + results.size());

            Product added = api.create(new CreateProduct("Pen", "office"));
            System.out.println("POST: " + added);

            try {
                api.findById(99, "request-099");
            } catch (ProductNotFoundException error) {
                System.out.println("ERROR: " + error.getMessage());
            }
        } finally {
            server.stop(0);
        }
    }

    private static void handle(HttpExchange request) throws IOException {
        String method = request.getRequestMethod();
        String path = request.getRequestURI().getPath();

        int status = 200;
        String response;

        if (!"java-learning".equals(
                request.getRequestHeaders().getFirst("X-Client-App"))) {
            status = 401;
            response = "{\"error\":\"missing app header\"}";
        } else if (method.equals("GET")
                && path.equals("/api/products/7")
                && "request-007".equals(
                    request.getRequestHeaders().getFirst("X-Request-Id"))) {
            response = "{\"id\":7,\"name\":\"Notebook\",\"category\":\"office\"}";
        } else if (method.equals("GET")
                && path.equals("/api/products/search")
                && "category=office".equals(request.getRequestURI().getRawQuery())) {
            response = "[{\"id\":7,\"name\":\"Notebook\",\"category\":\"office\"}]";
        } else if (method.equals("POST") && path.equals("/api/products")) {
            CreateProduct input = JSON.readValue(
                request.getRequestBody(), CreateProduct.class
            );

            if ("Pen".equals(input.name()) && "office".equals(input.category())) {
                response = "{\"id\":8,\"name\":\"Pen\",\"category\":\"office\"}";
            } else {
                status = 400;
                response = "{\"error\":\"invalid product\"}";
            }
        } else {
            status = 404;
            response = "{\"error\":\"not found\"}";
        }

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", "application/json");
        request.sendResponseHeaders(status, bytes.length);
        try (OutputStream stream = request.getResponseBody()) {
            stream.write(bytes);
        }
    }
}
```

Chạy tại thư mục dự án (sử dụng Gradle đã cài; nếu có wrapper, thay `gradle` bằng `./gradlew` trên Unix/macOS hoặc `gradlew.bat` trên Windows):

```bash
gradle run --quiet
```

**Các dòng chương trình dự kiến in ra:**

```text
GET: Product[id=7, name=Notebook, category=office]
SEARCH count: 1
POST: Product[id=8, name=Pen, category=office]
ERROR: HTTP 404
```

Giải thích luồng thực thi:

1. `HttpServer` bind cổng tự chọn (`0`), client lấy địa chỉ base URL động.
2. `findById(7, "request-007")` tạo GET `/api/products/7` cùng header global `X-Client-App` và header động `X-Request-Id`. Server kiểm tra cả hai, rồi trả JSON object được chuyển thành `Product`.
3. `search("office")` tạo query `category=office`; response JSON array được giải mã thành `List<Product>`.
4. `create(new CreateProduct(...))` tạo POST body JSON, server dùng Jackson đọc thành `CreateProduct`, rồi trả `Product` mới.
5. `findById(99, ...)` nhận 404; `RestClient.defaultStatusHandler` chuyển nó thành `ProductNotFoundException`. Caller bắt exception và in `ERROR: HTTP 404`.

Ở ví dụ này, server là HTTP local phục vụ mục đích học. Với môi trường thật, dùng HTTPS, chính sách credentials/trace, timeout và monitoring tương ứng. Mã ví dụ tập trung vào chức năng cần học; không triển khai authentication, pagination thực tế hay retry production.

## 8. So sánh RestClientAdapter và WebClientAdapter

| Tiêu chí | `RestClientAdapter` | `WebClientAdapter` |
| --- | --- | --- |
| Nền tảng thực thi | `RestClient` đồng bộ | `WebClient` reactive/non-blocking |
| Kiểu return thường dùng | `Product`, `List<Product>`, `ResponseEntity<Product>` | `Mono<Product>`, `Flux<Product>`, `Mono<ResponseEntity<T>>` |
| Header mặc định | `RestClient.Builder.defaultHeader` | `WebClient.Builder.defaultHeader` |
| JSON conversion | Spring `HttpMessageConverter` | Spring reactive codecs |
| Xử lý HTTP 4xx/5xx | `RestClient.Builder.defaultStatusHandler` | `WebClient.Builder.defaultStatusHandler` |
| Cấu hình transport | Client request factory, HTTP engine | Client connector (thường Reactor Netty) |
| Phù hợp | Ứng dụng đồng bộ/MVC, script, API nhỏ | WebFlux pipeline, streaming, nhiều I/O đồng thời |

Interface chia sẻ contract có thể được thiết kế chung nếu return type tương thích; không phải mọi method synchronous trả object đều nên mang nguyên sang luồng reactive. Đặt tên API rõ ràng và tránh gọi `block()` trên event-loop chỉ để tái sử dụng chữ ký đồng bộ.

Cấu hình reactive minh họa:

```java
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;

@HttpExchange(url = "/api/products", accept = "application/json")
interface ReactiveCatalogApi {
    @GetExchange("/{id}")
    Mono<Product> findById(@PathVariable("id") long id);
}

WebClient webClient = WebClient.builder()
    .baseUrl("https://catalog.example.com")
    .defaultHeader("X-Client-App", "java-learning")
    .build();

ReactiveCatalogApi api = HttpServiceProxyFactory
    .builderFor(WebClientAdapter.create(webClient))
    .build()
    .createClient(ReactiveCatalogApi.class);
```

Đây là minh họa (không phải file Java độc lập); cần `spring-boot-starter-webflux`, các import annotation/`Product`/`HttpServiceProxyFactory` và môi trường reactive tương ứng.

## 9. Lỗi thường gặp và cách kiểm tra

1. **Nhầm annotation của controller với HTTP interface.** `@GetMapping`/`@RequestMapping` là lựa chọn phổ biến cho server controller; ở HTTP Service Client dùng `@GetExchange`/`@HttpExchange` để proxy hiểu outbound request.
2. **Đặt header bằng thuộc tính `headers` trên `@HttpExchange`.** Spring 6.1 không định nghĩa thuộc tính này. Sử dụng `accept`/`contentType`, `@RequestHeader` hoặc builder.
3. **Sai vị trí header động:** correlation ID hoặc Authorization cố định tại startup dù chúng thuộc request/user khác nhau. Dùng tham số method, filter hoặc interceptor có scope đúng.
4. **Lắp URL bằng cách nối chuỗi input.** Có nguy cơ encode sai và injection/SSRF. Dùng `@PathVariable`, `@RequestParam` và URI builder; chỉ cho phép host/scheme hợp lệ khi URL động.
5. **Nhầm `Accept` và `Content-Type`.** GET JSON cần yêu cầu server trả JSON, còn POST JSON cần mô tả request body đúng kiểu; header sai có thể gây 406/415.
6. **Tưởng `@RequestBody` tự chuyển JSON khi thiếu Jackson/converter.** Chuyển đổi phụ thuộc classpath và cấu hình client nền.
7. **Dùng `List` raw type thay vì `List<Product>`.** Mất thông tin kiểu phần tử, dễ nhận `Map` rồi gặp ép kiểu sai.
8. **Cho rằng `ResponseEntity<T>` vô hiệu hóa 404 exception.** Status handlers của client có thể chạy trước; phải xử lý theo hợp đồng lỗi.
9. **Gọi `onStatus` ngay trên method proxy.** Proxy trả `Product` hoặc `Mono<Product>`, không phải `ResponseSpec`; cấu hình lỗi tại `RestClient.Builder`/`WebClient.Builder`.
10. **Retry HTTP 400/401/403 hoặc POST mọi trường hợp.** Phân biệt lỗi có thể phục hồi, bảo vệ idempotency và giới hạn retry.
11. **Nuốt mất body lỗi hoặc đọc nó nhiều lần.** Handler chỉ nên tiêu thụ body cần thiết một lần, giới hạn size và giữ status/headers quan trọng trong exception.
12. **Thử request bằng mock có HTTP 200 mà cho rằng production sẽ giống hệt.** Cần integration test riêng cho path encoding, headers, 4xx/5xx, network failures, media types, codec limits và schema biến động.

Để kiểm thử client, có thể dùng `MockRestServiceServer` (với RestClient) hoặc mock HTTP server như WireMock/MockWebServer; kiểm tra cả HTTP method, URI, headers, body và mapping lỗi. Kiểm thử hợp đồng nên thực hiện từ **hành vi HTTP thực tế** (request/response), thay vì chỉ gọi mock implementation của interface.

## 10. Tự kiểm tra

1. **Tại sao khai báo `@RequestHeader("Authorization") String token` hữu ích hơn hard-code bearer token tại interface?** Vì token có thể thay đổi theo user/request và cần được bảo vệ, làm mới.
2. **Khi gọi `search("office")`, `category` nên nằm ở đâu?** Với GET thông thường, `@RequestParam` đưa nó vào query string; `@PathVariable` thì dùng cho một placeholder trong path.
3. **Nếu return `List<Product>` mà HTTP 200 trả JSON object, điều gì xảy ra?** Decoder có thể thất bại do body shape không khớp; HTTP status thành công không bảo đảm Java conversion thành công.
4. **Nếu muốn 404 map sang `ProductNotFoundException` ở tất cả method proxy `RestClient`, cấu hình ở đâu?** Ở `RestClient.Builder.defaultStatusHandler` trước khi tạo `RestClientAdapter`/proxy.
5. **Nếu một method cần streaming response, chọn loại interface/adapter nào?** Dùng `WebClientAdapter` với return type `Flux<T>` và upstream/codec hỗ trợ streaming; không chuyển `List<T>` đồng bộ thành streaming chỉ bằng thay tên kiểu.
6. **Nếu upstream trả JSON lỗi có thêm mã domain, nên xử lý thế nào?** Đọc body lỗi đúng một lần trong status handler, giải mã schema có giới hạn và tạo exception giàu ngữ cảnh, tách khỏi lỗi kết nối/codec.

## 11. Tài liệu tham khảo

- Spring Framework Reference — REST Clients và HTTP Service Clients: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html
- Spring Framework 6.1 Javadoc — `@HttpExchange` (method arguments, URL, media types): https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/service/annotation/HttpExchange.html
- Spring Framework 6.1 Javadoc — `HttpServiceProxyFactory`: https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/service/invoker/HttpServiceProxyFactory.html
- Spring Framework 6.1 Javadoc — `RestClient.Builder`: https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/client/RestClient.Builder.html
- Spring Framework Reference — URI links and encoding: https://docs.spring.io/spring-framework/reference/web/webflux/uri-building.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-service-clients-006">Quản lý nhiều dịch vụ, khả năng tương thích phiên bản và kiểm thử proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Quản lý nhiều dịch vụ, khả năng tương thích phiên bản và kiểm thử proxy HTTP Service Client

## 1. Bài toán và mục tiêu

Trong ứng dụng Spring, một tính năng như xem chi tiết đơn hàng có thể phải gọi **Inventory Service** để kiểm tra tồn kho, **Pricing Service** để đọc giá và **Shipping Service** để dự tính vận chuyển. Với HTTP Service Client, ta có thể khai báo interface bằng @HttpExchange, @GetExchange, @PostExchange rồi tạo proxy thực hiện HTTP thật qua RestClient hoặc WebClient.

Khi chỉ có một interface và một dịch vụ, việc tạo proxy khá ngắn. Khi có nhiều dịch vụ, các khó khăn mới xuất hiện: chọn đúng host, tránh rò token giữa các dịch vụ, cấu hình timeout và retry khác nhau, thay đổi phiên bản API không gây đứt gãy, và kiểm thử liệu proxy có gửi **đúng request thật** hay không.

Mục tiêu của bài:
1. Tổ chức nhiều HTTP Service Client theo từng remote service, với cấu hình base URL, xác thực, timeout và observability tách biệt.
2. Xác định ranh giới phiên bản của HTTP contract, nhận diện thay đổi tương thích và không tương thích ở cả request/response.
3. Tạo và quản lý proxy bằng HttpServiceProxyFactory trong Spring Framework 6.1+, đồng thời hiểu giới hạn phiên bản của cơ chế đăng ký theo group ở Spring Framework 7.
4. Kiểm thử request do proxy tạo ra (URI, method, header, body), mapping JSON, error status và contract evolution; phân biệt unit test với mock HTTP server và integration test.
5. Thiết kế migration có khả năng rollback khi upstream/dependent services được triển khai độc lập.

**Phạm vi:** đây là bài về **HTTP Service Client** của Spring; kiến thức về nhiều dịch vụ, versioning và testing được áp dụng trực tiếp cho proxy được sinh từ interface. Proxy là client gọi ra ngoài, **không phải server triển khai endpoint**, cũng không tự cung cấp API gateway, service discovery, circuit breaker hay registry.

## 2. Mỗi dịch vụ phải có hợp đồng và cấu hình riêng

Ví dụ một tính năng cần gọi hai API khác nhau:

| Dịch vụ | URL nền trong môi trường thử nghiệm | Contract | Dữ liệu |
| --- | --- | --- | --- |
| Inventory | https://inventory.example.test | GET /v1/stock/{sku} | StockResponse |
| Pricing | https://pricing.example.test | GET /v2/prices/{sku} | PriceResponse |

Việc dùng các host khác nhau chỉ là ví dụ; tên miền .test không có API thật. Lựa chọn phiên bản /v1 và /v2 ở đây minh họa hai dịch vụ độc lập có vòng đời contract khác nhau: **không phải mọi service đều phải lên v2 cùng lúc**.

Khai báo hai interface riêng:

~~~java
@HttpExchange(url = "/v1/stock", accept = MediaType.APPLICATION_JSON_VALUE)
public interface InventoryClient {
    @GetExchange("/{sku}")
    StockResponse findStock(@PathVariable("sku") String sku);
}

@HttpExchange(url = "/v2/prices", accept = MediaType.APPLICATION_JSON_VALUE)
public interface PricingClient {
    @GetExchange("/{sku}")
    PriceResponse findPrice(@PathVariable("sku") String sku);
}
~~~

Đây là **mã minh họa để tích hợp**; cần import annotation Spring tương ứng và định nghĩa StockResponse, PriceResponse. Nếu cả hai dùng chung factory và HTTP client với một baseUrl duy nhất, lời gọi có nguy cơ đi sai host. Cần phân biệt rõ:

- **Interface** sở hữu chữ ký request và kiểu response theo hợp đồng của một API.
- **RestClient/WebClient** sở hữu địa chỉ đích, cách kết nối, codec, header mặc định, auth/interceptor/filter và các chính sách truyền tải.
- **HttpServiceProxyFactory** ánh xạ method interface thành request gửi qua adapter.
- **Service/facade bên trong ứng dụng** điều phối kết quả từ nhiều API và chuyển sang model nghiệp vụ riêng.

Cấu trúc gợi ý:

~~~text
src/main/java/com/example/client/
├── inventory/
│   ├── InventoryClient.java
│   └── StockResponse.java
├── pricing/
│   ├── PricingClient.java
│   └── PriceResponse.java
├── configuration/
│   └── PartnerClientsConfiguration.java
└── facade/
    └── ProductAvailabilityService.java
~~~

Không cần ép tất cả HTTP interface vào một lớp giant client. Một nhóm endpoint có cùng host và policy có thể chia thành nhiều interface, nhưng phải rõ factory nào tạo proxy nào.

### 2.1. Tạo proxy một cách tường minh với Spring 6.1+

RestClient là HTTP client **đồng bộ** của Spring Framework 6.1; HttpServiceProxyFactory.builderFor(HttpExchangeAdapter) cũng có từ 6.1. Cách đăng ký bean trong ứng dụng Spring Boot:

~~~java
@Configuration
public class PartnerClientsConfiguration {

    @Bean
    InventoryClient inventoryClient(
            RestClient.Builder baseBuilder,
            PartnerAddresses addresses) {

        RestClient restClient = baseBuilder.clone()
            .baseUrl(addresses.inventory())
            .defaultHeader("X-Consumer", "order-service")
            .build();

        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(InventoryClient.class);
    }

    @Bean
    PricingClient pricingClient(
            RestClient.Builder baseBuilder,
            PartnerAddresses addresses) {

        RestClient restClient = baseBuilder.clone()
            .baseUrl(addresses.pricing())
            .defaultHeader("X-Consumer", "order-service")
            .build();

        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(PricingClient.class);
    }
}
~~~

**Đây là snippet tích hợp**, không phải chương trình độc lập. PartnerAddresses là bean cấu hình do ứng dụng định nghĩa, có các phương thức inventory() và pricing() trả URL; các interface/DTO là ví dụ ở trên. Clone builder trước mỗi nhánh vì RestClient.Builder **mutable**. Điều này tách cấu hình theo host và vẫn cho phép kế thừa customizers của RestClient.Builder do Spring Boot cấp.

**Không khai báo nhầm** hai proxy thành bean dùng chung một RestClient với baseUrl inventory. Một proxy có thể không phát lỗi cho tới khi được gọi; việc khởi tạo Spring context thành công chưa chứng minh routing đúng.

### 2.2. Tách policy theo target service

Một bộ policy tối thiểu cần xem xét cho mỗi host:

| Cấu hình | Inventory | Pricing | Lý do |
| --- | --- | --- | --- |
| Base URL | Config inventory | Config pricing | Tránh gọi sai host/env |
| Auth | Token/audience inventory | Token/audience pricing | Tránh lộ credential giữa các upstream |
| Timeout | Theo SLA tồn kho | Theo SLA báo giá | Dịch vụ có ngân sách độ trễ khác nhau |
| Retry | GET lỗi tạm thời, có giới hạn | GET lỗi tạm thời, có giới hạn | Không nhân request vô hạn |
| Rate limit/concurrency | Theo quota inventory | Theo quota pricing | Giới hạn tải độc lập |
| Error mapping | OutOfStock/Unavailable | PriceNotFound/Unavailable | Bảo vệ tầng nghiệp vụ khỏi lỗi transport |
| Logs/metrics | service=inventory | service=pricing | Định vị sự cố đúng upstream |

Ví dụ cấu hình ngoài code:

~~~yaml
partners:
  inventory:
    base-url: https://inventory.example.test
    connect-timeout: 1s
    read-timeout: 2s
  pricing:
    base-url: https://pricing.example.test
    connect-timeout: 1s
    read-timeout: 1s
~~~

Đây là **cấu hình ứng dụng tự định nghĩa**, không phải property Spring tự hiểu hoặc tự áp timeout. Cần @ConfigurationProperties/binding và truyền các giá trị vào request factory hoặc WebClient connector phù hợp. Dùng một biến cấu hình duy nhất cho cả hai host rồi tự nối path rất dễ làm sai ở lúc đổi môi trường.

Không đưa URL thật, token hay khóa bí mật vào annotation nếu chúng khác nhau theo môi trường; đường dẫn tài nguyên ổn định và contract version có thể nằm trong annotation, còn host/auth nên ở cấu hình. Nếu hai service dùng OAuth với audience khác nhau, cache token cũng phải phân biệt scope/audience.

### 2.3. Phần dùng chung nên là cơ chế tạo proxy, không là policy cứng

Khi có 10 interface, có thể tái sử dụng hàm nhỏ để tạo proxy:

~~~java
public final class HttpClientProxies {
    private HttpClientProxies() {}

    public static <T> T create(RestClient restClient, Class<T> api) {
        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(api);
    }
}
~~~

Bản thân factory có thể tạo nhiều interface cùng host/cấu hình nếu chúng cùng adapter. Nhưng **không gom các dịch vụ độc lập vào một adapter chung** chỉ để giảm số dòng Java. Mỗi remote service có thể cần auth, codec, logging, TLS, proxy HTTP, retry và kiểm thử khác nhau.

Với ứng dụng WebFlux, áp dụng cùng nguyên tắc bằng WebClientAdapter.create(webClient); interface trả Mono<T>/Flux<T> khi contract hỗ trợ reactive. Không dùng Mono<T> với RestClientAdapter rồi kỳ vọng proxy tự trở thành non-blocking.

## 3. Quản lý phiên bản hợp đồng HTTP khi nhiều dịch vụ phát triển độc lập

**Version của API** khác với version của thư viện Java chứa interface và khác với version triển khai của service. Một release server có thể vẫn giữ API /v1; một ứng dụng có thể dùng đồng thời Inventory v1 và Pricing v2.

Các cách thể hiện version do **server contract quy định**, không phải annotation quyết định:
- URL path: /v1/stock/{sku}, /v2/stock/{sku}.
- Media type: Accept: application/vnd.example.inventory.v2+json.
- Header được API định nghĩa, ví dụ X-Api-Version: 2.
- Query parameter nếu đó là quy ước của dịch vụ.

Chỉ chọn **một** quy tắc tương ứng với upstream, không tự thêm nhiều cơ chế version khác nhau vì nghĩ sẽ an toàn hơn. Đổi path/Accept/header phía client **không tự nâng cấp server**.

### 3.1. Thay đổi nào có thể tương thích?

Tương thích phải xét theo hướng dữ liệu và chính sách của **consumer đang chạy**, không chỉ theo cảm nhận của provider.

| Thay đổi phía provider | Rủi ro phía consumer | Cách đánh giá |
| --- | --- | --- |
| Thêm field tùy chọn vào JSON response | Thường tương thích nếu consumer bỏ qua field lạ | Test JSON decoder/DTO hiện tại |
| Thêm endpoint mới | Thường không ảnh hưởng endpoint cũ | Regression các endpoint đang dùng |
| Thêm optional query parameter với default giữ nguyên | Thường tương thích | Test request cũ vẫn có nghĩa như trước |
| Đổi tên/xóa field response đang được dùng | Có thể mất dữ liệu hoặc parse lỗi | Breaking trừ khi có migration/tương thích tạm thời |
| Đổi kiểu amount từ number sang string | DTO mapping có thể sai hoặc parse lỗi | Phải test type và semantics |
| Đổi đơn vị amount từ cents sang dollars | Có thể sai nghiệp vụ **dù JSON parse được** | Breaking về ngữ nghĩa, cần version/field mới |
| Biến field optional thành required trên request | Consumer cũ bị 4xx | Breaking |
| Đổi 404 sang 200 với body rỗng | Error handling thay đổi | Breaking đối với logic dựa status |
| Giảm TTL hoặc đổi timezone/precision | Có thể sai cache và so sánh thời gian | Đánh giá nghĩa vụ contract |
| Thêm giá trị enum mới | Consumer switch cứng có thể lỗi | Kiểm tra khả năng xử lý unknown |
| Thay đổi yêu cầu auth/headers | Consumer cũ có thể 401/403 | Migration config và rollout |
| Bỏ API /v1 | Consumer còn dùng /v1 sẽ lỗi | Chỉ sau khi quan sát đã di chuyển hết |

**Tolerant reader** có thể bỏ qua field JSON mới, nhưng cần ràng buộc các field cốt lõi. Ví dụ response giá:

~~~json
{
  "sku": "A-7",
  "amountInCents": 129900,
  "currency": "VND"
}
~~~

Tương lai upstream thêm field "taxIncluded": true thường không làm hỏng consumer nếu DTO được thiết kế cho phép extra fields. Nhưng nếu provider đổi amountInCents thành "price" và field cũ biến mất, consumer có thể nhận null hoặc 0, thậm chí tính sai giá nếu không validation.

Thiết kế DTO để chặn lỗi âm thầm:

~~~java
@JsonIgnoreProperties(ignoreUnknown = true)
public record PriceResponse(String sku, Long amountInCents, String currency) {
    public PriceResponse {
        Objects.requireNonNull(sku, "sku required");
        Objects.requireNonNull(amountInCents, "amountInCents required");
        Objects.requireNonNull(currency, "currency required");
        if (amountInCents < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }
}
~~~

Đây là snippet độc lập về model nếu thêm các import Jackson/java.util.Objects; không tự là cả chương trình. Dùng **Long** thay vì **long** cho dữ liệu bắt buộc giúp phát hiện field vắng mặt thay vì nhận giá trị mặc định 0. Constructor kiểm tra ngữ nghĩa ngay khi deserialize. @JsonIgnoreProperties(ignoreUnknown = true) phải là **lựa chọn có chủ đích**: hợp đồng nghiêm ngặt có thể yêu cầu fail khi xuất hiện field lạ.

### 3.2. Nên tách DTO wire format khỏi model nghiệp vụ

Interface định nghĩa **dữ liệu thực tế của remote API**; facade chuyển nó thành model nội bộ:

~~~java
public record Money(long amountMinorUnits, String currency) {}

public final class PriceFacade {
    private final PricingClient pricing;

    public PriceFacade(PricingClient pricing) {
        this.pricing = pricing;
    }

    public Money priceOf(String sku) {
        PriceResponse wire = pricing.findPrice(sku);
        return new Money(wire.amountInCents(), wire.currency());
    }
}
~~~

Snippet dùng PricingClient và PriceResponse ở trên; không phải chương trình độc lập. Khi upstream đổi contract, lớp adapter/DTO/facade là điểm chuyển đổi, trong khi phần tính toán đơn hàng không cần phụ thuộc JSON bên ngoài.

Nếu đổi đơn vị tiền, đừng đổi nghĩa của amountInCents trong lớp cũ. Tạo DTO mới (chẳng hạn PriceV3Response) và mapping rõ ràng, kiểm thử mức tiền cả trước và sau migration. Rủi ro **semantic drift** thường lớn hơn lỗi deserialize.

### 3.3. Migration nhiều phiên bản

Khi upstream muốn bỏ /v1 để chuyển /v2, trình tự phổ biến:

1. Provider công bố contract /v2, chính sách tương thích, thời điểm ngừng hỗ trợ /v1, và chuẩn bị test environment.
2. Consumer thêm interface/DTO v2 bên cạnh v1 (nếu cần chạy song song), cùng mapping nội bộ đủ ổn định.
3. Chạy contract/integration tests cho v2; test cả field không biết, null, lỗi status, auth và giới hạn hiệu năng.
4. Rollout từng nhóm/feature flag; log metric theo client version và service để quan sát trước khi chuyển toàn bộ traffic.
5. Có cơ chế rollback rõ ràng (chuyển config/feature flag về v1 còn được provider phục vụ).
6. Chỉ gỡ proxy v1 sau khi không còn call thực tế và hết cửa sổ rollback; cuối cùng provider mới ngừng endpoint cũ.

Dùng một header version có thể đơn giản hơn tạo interface mới khi hợp đồng chỉ thêm field tương thích. Nếu response shape và semantics thay đổi mạnh, hai interface/DTO tách biệt thường dễ review và rollback hơn. **Không đoán version bằng tên deployment**: service version 2.5.1 hoàn toàn có thể tiếp tục phục vụ API v1.

### 3.4. Chú ý giới hạn theo phiên bản Spring

Trong **Spring Framework 6.1**, bài này dùng bean @Configuration cùng RestClientAdapter/WebClientAdapter và HttpServiceProxyFactory.builderFor(...). Đây là cách đơn giản, được hỗ trợ trên baseline Spring Boot 3.3.x.

Spring Framework **7** bổ sung HTTP Service Registry và nhóm HTTP service với @ImportHttpServices, giúp đăng ký nhiều interface/proxy bean theo group. Spring Boot **4** có khả năng cấu hình HTTP service group tương ứng. **Không sử dụng @ImportHttpServices, HttpServiceProxyRegistry hoặc property spring.http.client.service.group.* trong ứng dụng Spring 6.1/Boot 3.3 rồi cho rằng chúng tự hoạt động**. Nếu dự án nâng cấp lên Spring 7/Boot 4, có thể nghiên cứu cơ chế mới và migration độc lập; thiết kế nhóm theo host/policy vẫn giữ nguyên.

## 4. Kiểm thử đúng đối tượng: interface, proxy, HTTP và hợp đồng

Một fake như:

~~~java
InventoryClient fake = sku -> new StockResponse(sku, true);
~~~

chỉ phù hợp **nếu interface thực sự là functional interface**; đoạn này minh họa ý tưởng giả lập phụ thuộc trong unit test tầng nghiệp vụ, không kiểm tra được annotation hay HTTP. Nó cũng không chứng minh proxy gọi đúng host và header.

Có ba lớp kiểm thử khác nhau:

| Lớp test | Chứng minh được | Không chứng minh |
| --- | --- | --- |
| Unit test facade với fake/mock InventoryClient | Điều phối, tính toán nghiệp vụ, fallback | URI/method/header/codec/proxy |
| HTTP proxy test với MockRestServiceServer | @HttpExchange mapping, request headers, query, response JSON và status trên RestClient | DNS/TLS, HTTP transport/network thực |
| Integration test với mock HTTP server chạy qua socket (WireMock/OkHttp MockWebServer) | Đường truyền HTTP thật, request serialization, timeout/kết nối và chính client production | Provider thật có tuân thủ contract không |
| Consumer-driven contract/provider verification | Provider có đáp ứng kỳ vọng consumer đã công bố hay không | Tự động chứng minh mọi nghiệp vụ nếu contract chưa đủ |
| Staging/E2E với provider thật | Tương thích thực tế của các service đang triển khai | Tái tạo hoàn chỉnh mọi tình huống failure |

**MockRestServiceServer** thay thế ClientHttpRequestFactory của RestClient, nên rất phù hợp kiểm thử nhanh proxy đồng bộ. Với WebClient/reactive, nên dùng mock HTTP server tương thích WebClient để kiểm tra request thật hoặc kiểm thử adapter tương ứng. Khi cần mô phỏng TLS, socket reset, timeout thật hoặc proxy HTTP, dùng test qua network/transport, không dựa vào MockRestServiceServer.

Mỗi proxy nên có test khẳng định:
- Đúng host và prefix URL, method, path variable, query encoding.
- Đúng header của service này; không lộ header/credential riêng của service khác.
- Đúng Content-Type/Accept và JSON serialization/deserialization.
- Thành công 2xx, không nội dung 204, 4xx/5xx, timeout/lỗi transport, request bị từ chối.
- DTO đọc được field bổ sung nếu contract cho phép, nhưng thiếu/đổi nghĩa field bắt buộc phải bị phát hiện.
- Bản v1/v2 có bộ dữ liệu hợp đồng riêng; test migration và đường rollback khi dùng song song.

## 5. Ví dụ Java 17 chạy được: hai service và test proxy thật

Ví dụ dưới đây **không gọi một API công khai**. Nó tạo hai RestClient/HTTP Service Client với hai base URL riêng; dùng MockRestServiceServer kiểm tra request thực tế mà proxy định gửi, trả JSON giả lập và xác minh response.

Yêu cầu: **Java 17+**, **Maven**, Spring Boot 3.3.13 (Spring Framework 6.1.x). Phiên bản được ghim để giữ API trong ví dụ nhất quán, không có nghĩa đây là phiên bản production mới nhất hoặc đang được hỗ trợ lâu dài.

### 5.1. Cấu trúc project

~~~text
multi-http-client-demo/
├── pom.xml
├── src/main/java/demo/
│   ├── InventoryClient.java
│   ├── PricingClient.java
│   ├── StockResponse.java
│   ├── PriceResponse.java
│   └── ClientProxies.java
└── src/test/java/demo/
    └── ProxyContractTest.java
~~~

### 5.2. Maven dependencies

**pom.xml**:

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
    <artifactId>multi-http-client-demo</artifactId>
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
</project>
~~~

starter-web cung cấp RestClient/Jackson cho demo; starter-test cấp JUnit 5, spring-test và MockRestServiceServer. Dự án không cần ứng dụng web server chạy hay cấu hình Spring ApplicationContext để kiểm thử proxy.

### 5.3. Năm file mã nguồn

**src/main/java/demo/InventoryClient.java**:

~~~java
package demo;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "/v1/stock", accept = MediaType.APPLICATION_JSON_VALUE)
public interface InventoryClient {
    @GetExchange("/{sku}")
    StockResponse findStock(@PathVariable("sku") String sku);
}
~~~

**src/main/java/demo/PricingClient.java**:

~~~java
package demo;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "/v2/prices", accept = MediaType.APPLICATION_JSON_VALUE)
public interface PricingClient {
    @GetExchange("/{sku}")
    PriceResponse findPrice(@PathVariable("sku") String sku);
}
~~~

**src/main/java/demo/StockResponse.java**:

~~~java
package demo;

public record StockResponse(String sku, boolean available) {}
~~~

**src/main/java/demo/PriceResponse.java**:

~~~java
package demo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PriceResponse(String sku, Long amountInCents, String currency) {
    public PriceResponse {
        Objects.requireNonNull(sku, "sku required");
        Objects.requireNonNull(amountInCents, "amountInCents required");
        Objects.requireNonNull(currency, "currency required");
        if (amountInCents < 0) {
            throw new IllegalArgumentException("amountInCents must be >= 0");
        }
    }
}
~~~

PriceResponse chấp nhận thuộc tính mới không được biết đến (theo contract minh họa), nhưng từ chối thiếu amountInCents. Kiểm tra trong constructor biến lỗi dữ liệu thành lỗi rõ ràng trước khi dùng vào tính giá.

**src/main/java/demo/ClientProxies.java**:

~~~java
package demo;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

public final class ClientProxies {
    private ClientProxies() {}

    public static <T> T create(RestClient restClient, Class<T> api) {
        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(api);
    }
}
~~~

### 5.4. Kiểm thử bằng MockRestServiceServer

**src/test/java/demo/ProxyContractTest.java**:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import org.springframework.http.HttpStatus;

public class ProxyContractTest {

    @Test
    void proxiesRouteToSeparateHostsAndKeepHeadersSeparate() {
        RestClient.Builder inventoryBuilder = RestClient.builder()
            .baseUrl("https://inventory.example.test")
            .defaultHeader("X-Target-Service", "inventory");
        RestClient.Builder pricingBuilder = RestClient.builder()
            .baseUrl("https://pricing.example.test")
            .defaultHeader("X-Target-Service", "pricing");

        // Bind mock servers BEFORE building RestClient instances.
        MockRestServiceServer inventoryServer =
            MockRestServiceServer.bindTo(inventoryBuilder).build();
        MockRestServiceServer pricingServer =
            MockRestServiceServer.bindTo(pricingBuilder).build();

        InventoryClient inventory = ClientProxies.create(
            inventoryBuilder.build(), InventoryClient.class);
        PricingClient pricing = ClientProxies.create(
            pricingBuilder.build(), PricingClient.class);

        inventoryServer.expect(
                requestTo("https://inventory.example.test/v1/stock/A-7"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Target-Service", "inventory"))
            .andRespond(withSuccess(
                "{\"sku\":\"A-7\",\"available\":true}",
                MediaType.APPLICATION_JSON));

        pricingServer.expect(
                requestTo("https://pricing.example.test/v2/prices/A-7"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Target-Service", "pricing"))
            .andRespond(withSuccess(
                "{\"sku\":\"A-7\",\"amountInCents\":129900,\"currency\":\"VND\"}",
                MediaType.APPLICATION_JSON));

        assertEquals(new StockResponse("A-7", true),
            inventory.findStock("A-7"));
        assertEquals(new PriceResponse("A-7", 129900L, "VND"),
            pricing.findPrice("A-7"));

        inventoryServer.verify();
        pricingServer.verify();
    }

    @Test
    void newlyAddedResponseFieldIsAccepted() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://pricing.example.test");
        MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
        PricingClient pricing = ClientProxies.create(
            builder.build(), PricingClient.class);

        server.expect(requestTo(
                "https://pricing.example.test/v2/prices/A-7"))
            .andRespond(withSuccess(
                "{\"sku\":\"A-7\",\"amountInCents\":129900," +
                "\"currency\":\"VND\",\"taxIncluded\":true}",
                MediaType.APPLICATION_JSON));

        assertEquals(129900L,
            pricing.findPrice("A-7").amountInCents());
        server.verify();
    }

    @Test
    void missingRequiredPriceFieldFailsContractValidation() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://pricing.example.test");
        MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
        PricingClient pricing = ClientProxies.create(
            builder.build(), PricingClient.class);

        server.expect(requestTo(
                "https://pricing.example.test/v2/prices/A-7"))
            .andRespond(withSuccess(
                "{\"sku\":\"A-7\",\"currency\":\"VND\"}",
                MediaType.APPLICATION_JSON));

        assertThrows(RestClientException.class,
            () -> pricing.findPrice("A-7"));
        server.verify();
    }

    @Test
    void missingItemReturnsHttp404ErrorByDefault() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://inventory.example.test");
        MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
        InventoryClient inventory = ClientProxies.create(
            builder.build(), InventoryClient.class);

        server.expect(requestTo(
                "https://inventory.example.test/v1/stock/UNKNOWN"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(HttpClientErrorException.NotFound.class,
            () -> inventory.findStock("UNKNOWN"));
        server.verify();
    }
}
~~~

Giải thích bằng chứng từng bài test:
1. **proxiesRoute...:** tạo hai builder và hai mock server độc lập; từng proxy phải đi đúng URL, method và header, sau đó JSON được map thành record tương ứng. Test có một kỳ vọng ở mỗi mock server, vì vậy gọi chéo host hoặc bỏ sót request sẽ thất bại.
2. **newlyAdded...:** JSON response thêm field taxIncluded; @JsonIgnoreProperties(ignoreUnknown = true) cho phép reader cũ tiếp tục đọc field đang dùng. Đây là **một dạng tương thích**, không chứng minh mọi thay đổi đều an toàn.
3. **missingRequired...:** response bỏ amountInCents; constructor yêu cầu non-null nên deserialization phải thất bại và được báo qua exception trên RestClient. Điều này tránh dùng âm thầm giá bằng 0.
4. **missingItem...:** 404 theo chính sách status mặc định của RestClient được phát ra thành HttpClientErrorException.NotFound, không biến thành StockResponse rỗng.

Chạy:

~~~bash
mvn -q test
~~~

JUnit/Surefire chạy **bốn test**. Nếu tất cả thành công, Maven trả **exit code 0**; tuỳ Maven/IDE, chế độ -q có thể không in bảng tóm tắt. Xem báo cáo trong target/surefire-reports/ để xác minh số bài test và trạng thái thực tế. Nếu có lỗi compile, assertion hoặc mapping, Maven trả exit code khác 0; không coi đoạn output dự kiến là bằng chứng đã chạy tại môi trường của người đọc.

**Lưu ý:** ví dụ minh họa API Java 17/Spring 6.1 bằng file và test code đầy đủ; các URL .test không cần DNS thật bởi MockRestServiceServer intercept request. Không nên thay mock bằng RestClient thật trỏ vào URL đó khi chưa có server. Để kiểm thử transport thực, chạy WireMock/MockWebServer ở localhost và cập nhật baseUrl tương ứng.

### 5.5. Từ proxy test đến contract test liên dịch vụ

Proxy test chỉ mô phỏng hợp đồng **consumer nghĩ rằng provider có**. Nếu mock trả trường amountInCents nhưng provider deploy thật trả priceInDollars, test consumer vẫn xanh vì mock sai thực tế. Cần gắn contract vào một nguồn chung hoặc kiểm tra provider thực thi các expectation đó.

Một quy trình consumer-driven contract (dùng Pact hoặc công cụ tương tự nếu tổ chức cần) thường là:
1. Consumer ghi các request thật mình dùng và các response tối thiểu mình yêu cầu.
2. Consumer test dựa trên contract; tạo/công bố artifact contract có version.
3. Provider chạy verification với artifact của consumer để chứng minh endpoint phù hợp.
4. Pipeline deployment đối chiếu provider version và nhóm consumer còn tồn tại; phát hành khi các cặp tương thích.
5. Thêm test tích hợp bằng mock HTTP server hoặc môi trường thực để bắt sai khác transport, auth, TLS, redirect và cấu hình vận hành.

Cẩn thận với “hợp đồng quá chặt”: nếu consumer không dùng field taxIncluded, không cần khóa toàn bộ response JSON chỉ để so khớp byte. Nhưng phải kiểm tra **đúng field và semantics thực sự cần cho nghiệp vụ**.

## 6. Unit test và integration test cho cấu hình nhiều proxy

Một lỗi phổ biến là chỉ test các interface riêng lẻ với builder tự tạo trong test, trong khi cấu hình production lại sử dụng sai baseUrl hoặc chung interceptor cho hai host. Do đó, ngoài test ở phần 5, cần test **wiring thực tế**:

- Tạo Spring context tối thiểu có PartnerClientsConfiguration với property inventory.base-url và pricing.base-url khác nhau.
- Gắn server stub độc lập vào từng cấu hình client, hoặc dùng WireMock trên hai cổng localhost.
- Lấy InventoryClient và PricingClient từ ApplicationContext; xác nhận bean nào gọi host nào.
- Gửi một request có credentials dành cho inventory; xác nhận header đó **không xuất hiện** trên request sang pricing.
- Kiểm tra policy 404/429/503 và timeout qua chính bean đã đăng ký, chứ không thông qua RestClient test tự tạo khác cấu hình.
- Khi bật đổi version hoặc feature flag, test branch cũ/v1 và mới/v2; xóa branch cũ khi hết thời hạn rollback.

Cần phân biệt **assertion của mock** với **thực thi network thật**. Test mock tương đối nhanh, ổn định và xác minh mapping; test socket thực xác minh timeout, DNS/proxy, TLS và wire encoding. Đối với nhiều remote dependencies, kết hợp cả hai ở mức hợp lý thay vì biến mọi unit test thành E2E chậm.

### Khi dùng WebClient/reactive

WebClientAdapter proxy thường trả Mono<T>/Flux<T>. Cần test reactive chain có subscriber, timeout, backpressure/streaming khi áp dụng và retryWhen scope. Với một mock HTTP server qua localhost:

~~~java
WebClient webClient = WebClient.builder()
    .baseUrl("http://localhost:8089")
    .build();

PricingReactiveClient client = HttpServiceProxyFactory
    .builderFor(WebClientAdapter.create(webClient))
    .build()
    .createClient(PricingReactiveClient.class);

StepVerifier.create(client.findPrice("A-7"))
    .assertNext(price -> assertEquals("A-7", price.sku()))
    .verifyComplete();
~~~

Đây là **snippet tích hợp**, không tự chạy nếu thiếu PricingReactiveClient, server test được khởi tạo sẵn và dependency reactor-test. Đừng dùng .block() trong handler WebFlux chỉ để “test nhanh”; dùng StepVerifier trong test, hoặc subscribe/return publisher đúng cách. Thực thi subscriber nhiều lần có thể phát request nhiều lần; kiểm thử retry phải tính cả số request thực tế.

## 7. Lỗi thường gặp khi quản lý nhiều service

1. **Hai proxy chia sẻ sai baseUrl:** method annotation đúng nhưng request đi nhầm upstream. Kiểm thử URL trọn vẹn, clone builder và cấu hình theo host.
2. **Header hoặc token của service A rò sang B:** dùng chung interceptor/defaultHeader không phân biệt target. Tách cấu hình và kiểm thử header âm tính (credential A không có trong request B).
3. **Nhầm code version với API version:** release ứng dụng v3 không có nghĩa endpoint HTTP /v3 đã tồn tại. Version phải từ contract provider.
4. **Đọc JSON quá dễ dãi:** bỏ qua missing required fields, dùng primitive mặc định 0 hoặc null rồi tính toán sai. Validate DTO tại boundary.
5. **Đọc JSON quá nghiêm ngặt:** từ chối field bổ sung vô hại gây lỗi trong rollout provider trước consumer. Quyết định tolerance theo schema contract.
6. **Thêm version vào interface mà không kiểm thử server:** code compile nhưng server có thể không hỗ trợ URL/Accept mới.
7. **Chỉ mock interface ở unit test:** không bắt annotation sai path/query/header, không thấy serialization lỗi. Thêm proxy test với HTTP expectation.
8. **Chỉ test proxy với builder nhân tạo:** production bean có thể cấu hình sai. Thêm integration test cho Spring configuration.
9. **Assume 2xx luôn mang DTO:** 204 No Content có thể tạo body rỗng; contract phải nêu rõ.
10. **Retry toàn bộ POST:** request bị timeout không chứng minh server chưa ghi. Cần idempotency server và retry có giới hạn.
11. **Lặp retry ở nhiều tầng:** client, gateway và service cùng retry làm tăng attempt theo cấp số nhân; chỉ định ownership của retry.
12. **Dùng API Spring 7 trên Spring 6.1:** @ImportHttpServices/group registry không tồn tại trên classpath cũ; version gate và nâng cấp có kế hoạch.
13. **Quên thời hạn ngừng v1:** còn consumer ít traffic tới v1 bị lỗi khi provider xóa API; cần metrics theo caller/version.
14. **Chỉ kiểm kiểu dữ liệu, bỏ semantics:** field tiền tệ đổi đơn vị, timezone thay đổi, default logic khác có thể đúng JSON nhưng sai nghiệp vụ.

## 8. Checklist trước khi phát hành

### Trước khi merge client mới

- Interface @HttpExchange trỏ đúng method, path và API version; DTO, nullability, status semantics được tài liệu hóa.
- Mỗi remote host có cấu hình riêng; secret lấy từ hệ thống quản lý cấu hình/secret, không hardcode.
- Timeout, retry budget và circuit/rate control phù hợp SLA và khả năng chịu tải của từng provider.
- Unit test facade, proxy test URI/header/body/codec/status và integration test wiring đều phù hợp phạm vi thay đổi.
- Có thể xác minh provider contract bằng verification hoặc môi trường tích hợp thật.

### Trước khi migration sang API version mới

- Liệt kê consumer/deployments đang sử dụng version cũ và thời gian provider còn hỗ trợ.
- Viết kiểm thử dữ liệu mẫu cũ/mới, bao gồm thay đổi field/kiểu/semantics và mã lỗi.
- Có cách rollout và rollback trong khi cả hai version còn hoạt động.
- Đo metrics theo service, version, status, latency, số retry; cảnh báo nếu xuất hiện tăng đột biến lỗi mapping/4xx/5xx.
- Gỡ version cũ **sau** khi không còn traffic hoặc consumer phụ thuộc, không chỉ sau khi phát hành version mới.

## 9. Tổng kết

Để quản lý nhiều HTTP Service Client trong Spring, hãy tách **hợp đồng interface**, **client configuration theo dịch vụ**, **factory/proxy** và **facade nghiệp vụ**. Versioning là trách nhiệm phối hợp consumer–provider; tương thích phải xét cả JSON shape, mã lỗi và **ngữ nghĩa dữ liệu**. Kiểm thử proxy phải chạy qua adapter thật để xác minh request HTTP nó tạo ra, sau đó bổ sung provider contract verification và integration test cho transport/wiring.

Trên **Spring Framework 6.1 / Boot 3.3**, tạo RestClient/WebClient và HttpServiceProxyFactory theo từng nhóm cấu hình là cơ chế phù hợp. Khi dùng **Spring Framework 7 / Boot 4**, có thể đánh giá HTTP Service Registry theo group để giảm cấu hình lặp, nhưng vẫn phải giữ ranh giới auth, versioning, rollback và test theo dịch vụ.

## 10. Tài liệu chính thức và đọc thêm

- Spring Framework 6.1 API — HttpServiceProxyFactory, builderFor và createClient: https://docs.spring.io/spring-framework/docs/6.1.7/javadoc-api/org/springframework/web/service/invoker/HttpServiceProxyFactory.html
- Spring Framework 6.1 API — RestClient.Builder (clone/baseUrl/defaultHeader): https://docs.spring.io/spring-framework/docs/6.1.0/javadoc-api/org/springframework/web/client/RestClient.Builder.html
- Spring Framework 6.1 API — MockRestServiceServer.bindTo(RestClient.Builder): https://docs.spring.io/spring-framework/docs/6.1.13/javadoc-api/org/springframework/test/web/client/MockRestServiceServer.html
- Spring Framework Reference — Testing Client Applications: https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-client.html
- Spring Framework Reference — HTTP Service Client: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html
- Spring Engineering Blog — HTTP Service Client Enhancements (Spring Framework 7): https://spring.io/blog/2025/09/23/http-service-client-enhancements/


</details>

- [Quay lại đầu trang](#back-to-top)
