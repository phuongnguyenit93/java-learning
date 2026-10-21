<a id="back-to-top"></a>

# Vận hành và thực hành

## Menu
- [Thiết kế lớp HTTP mỏng, ranh giới dịch vụ và hợp đồng dữ liệu rõ ràng](#operations-practice-001)
- [Quan sát yêu cầu/phản hồi bằng log, tracing và Micrometer Observation](#operations-practice-002)
- [Đọc lỗi 404, 405, 406, 415, validation và ngoại lệ bất đồng bộ](#operations-practice-003)
- [Chẩn đoán cạn luồng, nghẽn kết nối, thiếu bộ nhớ và tác vụ chặn event loop](#operations-practice-004)
- [Triển khai sau proxy: HTTPS, forwarded headers, streaming và WebSocket](#operations-practice-005)
- [Giới hạn tài nguyên, timeout, ngắt kết nối và dừng ứng dụng an toàn](#operations-practice-006)
- [Thực hành REST API: mapping, DTO, validation, lỗi và kiểm thử](#operations-practice-007)
- [Thực hành trang biểu mẫu: view, bản địa hóa, session và redirect](#operations-practice-008)
- [Thực hành gọi dịch vụ: HTTP client, timeout và xử lý lỗi](#operations-practice-009)
- [Thực hành cập nhật thời gian thực: lựa chọn SSE hoặc WebSocket](#operations-practice-010)
- [Đối chiếu cùng một bài toán trên MVC và WebFlux bằng hành vi quan sát được](#operations-practice-011)

## <a id="operations-practice-001">Thiết kế lớp HTTP mỏng, ranh giới dịch vụ và hợp đồng dữ liệu rõ ràng</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế lớp HTTP mỏng, ranh giới dịch vụ và hợp đồng dữ liệu rõ ràng

## 1. Vì sao thiết kế ranh giới HTTP là một vấn đề vận hành?

Trong ứng dụng Spring Framework, `@RestController` là nơi HTTP request đi vào hệ thống. Viết mọi logic ngay tại controller ban đầu có thể nhanh, nhưng lâu dài dễ gây các vấn đề: endpoint chứa SQL hoặc JPA repository calls, xử lý transaction lẫn với JSON mapping, ngoại lệ nghiệp vụ bị trả thành HTTP 500, thay tên cột database làm thay đổi response JSON, và test nghiệp vụ buộc phải khởi động web server.

Thiết kế **lớp HTTP mỏng** có nghĩa controller chỉ làm những việc thuộc giao thức HTTP:

1. Nhận và diễn giải input từ path, query, headers hoặc JSON body.
2. Thực hiện kiểm tra hình dạng dữ liệu tại ranh giới (ví dụ Bean Validation).
3. Chuyển request DTO thành input của một ca sử dụng/service.
4. Gọi service và chuyển kết quả thành response DTO.
5. Chọn HTTP status, response headers và hợp đồng lỗi ổn định.

**Service boundary** giữ hành vi nghiệp vụ độc lập với HTTP: kiểm tra bất biến nghiệp vụ, điều phối repository/domain, phân định transaction, phát sự kiện theo thiết kế. **Data contract** là sự thỏa thuận rõ ràng về request, response, kiểu dữ liệu, optional fields, quy tắc lỗi, versioning, status codes và ngữ nghĩa; contract có thể tồn tại lâu hơn một bảng database hay một class domain.

Đây không phải lời kêu gọi tạo thật nhiều lớp. Với endpoint rất đơn giản, service có thể nhỏ. Mục tiêu là trách nhiệm có chỗ đứng rõ ràng, các thay đổi có phạm vi ảnh hưởng dự đoán được, và test đúng lớp.

Phạm vi: HTTP APIs trong **Spring MVC**, Java 17+ / Spring Boot 3.3.x / Spring Framework 6.1.x, kèm phân biệt khi dùng WebFlux. Bài tập runnable minh họa API đăng ký và tra cứu sách, với in-memory repository để tập trung vào kiến trúc mà không cần database.

## 2. Tách transport, application, domain và persistence

Một luồng HTTP điển hình:

```text
HTTP client
    │ POST /api/books + JSON
    ▼
[HTTP adapter / @RestController]
  - parse/validate request DTO
  - map DTO → command
  - call application service
  - map result → response DTO + HTTP 201
    │
    ▼
[Application service / use case]
  - áp dụng quy tắc nghiệp vụ
  - chọn transaction boundary nếu cần
  - gọi repository port
    │
    ▼
[Repository abstraction] ← port (hợp đồng truy cập dữ liệu)
    │
    ▼
[Persistence adapter]
  - JPA/JDBC/Mongo/in-memory
    │
    ▼
Database hoặc storage
```

| Lớp/trách nhiệm | Biết điều gì? | Tránh điều gì? |
| --- | --- | --- |
| **Controller / HTTP adapter** | URL, HTTP verb, status, headers, JSON DTO, `@Valid` | SQL, entity manager, chính sách giao dịch, thuật toán nghiệp vụ dài |
| **Application service** | Ca sử dụng, input command, quyền nghiệp vụ, transaction và orchestration | `ResponseEntity`, `HttpServletRequest`, `@RequestBody`, status 404/409 |
| **Domain** | Entity/value object, bất biến, quy tắc nghiệp vụ | Jackson annotations, HTTP query param, tên cột database |
| **Repository port** | Những thao tác storage mà use case cần | Thông tin transport HTTP |
| **Repository adapter** | JDBC/JPA/SQL/index/cache/storage cụ thể | HTTP serialization, response status |
| **Exception translation / advice** | Đổi exception thành response lỗi công khai | Che giấu bug thành 200, tiết lộ stack trace |

Chiều phụ thuộc hợp lý là: controller phụ thuộc service; service phụ thuộc domain và repository interface; implementation storage thực thi interface đó. Có thể dùng dependency injection của Spring bằng constructor. Không cần triển khai Clean Architecture đầy đủ với hàng chục module Java cho mỗi CRUD; tách ít nhất ranh giới HTTP và nghiệp vụ là bước có giá trị lớn.

### Ví dụ dấu hiệu controller đang quá dày

```java
// Ví dụ PHẢN MẪU, không có các class đi kèm để chạy độc lập.
@PostMapping("/orders")
public ResponseEntity<?> place(@RequestBody OrderEntity order) {
    order.setStatus("NEW");
    repository.save(order);
    paymentClient.charge(order.getAmount());
    emailSender.send(order.getCustomerEmail());
    return ResponseEntity.ok(order);
}
```

Đoạn này vừa nhận persistence entity qua HTTP, vừa điều phối thanh toán/email, vừa ghi dữ liệu và chọn response. Các lỗi có thể tạo trạng thái nửa thành công: ghi order trước rồi charge thất bại; retry POST có thể nhân đôi thanh toán. Khó đảm bảo transaction và idempotency.

Thiết kế lại: HTTP adapter nhận `PlaceOrderRequest` (contract), gọi `OrderApplicationService.place(command)`, service đặt quy tắc/transaction/outbox và trả một kết quả `PlacedOrder`; controller ánh xạ thành `201 Created` và DTO. Với thanh toán bên ngoài, giao dịch database không thể rollback được HTTP charge đã thành công: cần idempotency key, saga/compensation hoặc event-driven workflow thích hợp.

## 3. Định nghĩa HTTP contract trước khi viết controller

Bắt đầu từ hợp đồng API ở góc nhìn consumer. Ví dụ “tạo sách” và “xem một sách”:

| Operation | Request | Thành công | Lỗi điển hình |
| --- | --- | --- | --- |
| Tạo sách | `POST /api/books`, JSON `{"title":"Spring","isbn":"9780000000001"}` | `201 Created`, `Location: /api/books/1`, response JSON có id/title/isbn | `400` nếu JSON/field không hợp lệ; `409` nếu ISBN đã tồn tại |
| Tra cứu | `GET /api/books/{id}` | `200 OK`, response JSON | `400` nếu id không hợp lệ; `404` nếu không tìm thấy |

Một request DTO khác response DTO:

```java
// Java 17+ records, fragment; file runnable đầy đủ ở mục 8.
public record CreateBookRequest(
        @NotBlank String title,
        @NotBlank String isbn) {}

public record BookResponse(
        long id,
        String title,
        String isbn) {}
```

Dấu `@NotBlank` thuộc Bean Validation (import `jakarta.validation.constraints.NotBlank`). Nó kiểm tra đầu vào tại HTTP boundary; service vẫn nên bảo vệ các quy tắc cốt lõi vì use case có thể được gọi từ scheduler, message listener, batch job hoặc test mà không đi qua controller.

### Contract không phải database schema

Giả sử persistence entity có thêm `costPrice`, `supplierId`, `internalNotes`, `rowVersion`. API public không nên tự trả tất cả fields đó chỉ vì entity có getter. Việc serialize trực tiếp `@Entity` có thể làm rò dữ liệu, gây vòng tham chiếu JSON ở quan hệ JPA và khiến lazy-loading chạy khi viết response. DTO là một **phép chiếu có chủ ý**.

Đặt tên JSON, kiểu và semantics ổn định. `id` là định danh tài nguyên, `title` là tên sách, `isbn` là định danh sách được dùng để kiểm tra trùng theo chính sách demo. Nếu ISBN bắt buộc có định dạng chuẩn trong production, thêm validation nghiệp vụ tương ứng thay vì dựa duy nhất vào `@NotBlank`.

### Những quyết định contract cần viết rõ

- Field nào bắt buộc và field nào optional? Phân biệt field **thiếu**, giá trị `null`, chuỗi trống và giá trị mặc định.
- POST trả `201 Created` hay `202 Accepted`? Nếu tạo đồng bộ thì 201; nếu chỉ nhận việc vào queue để xử lý sau, 202 có thể đúng hơn.
- Sau POST, `Location` trỏ tới resource nào? Đường dẫn công khai cần nhất quán với GET.
- Trường thời gian dùng ISO 8601/UTC hay có timezone nào? Số tiền dùng kiểu biểu diễn gì (tránh `double` tùy tiện)?
- Pagination/filter/sort dùng query params nào? Có giới hạn số kết quả, chiều dài chuỗi, size payload không?
- Hợp đồng lỗi gồm `status`, `detail`, `type` và correlation ID thế nào? Có tiết lộ dữ liệu nội bộ không?
- Có cần idempotency key cho POST chịu retry, ETag/`If-Match` cho update đồng thời và versioning khi đổi contract không?

Đặt các quyết định trên trong API docs/OpenAPI + contract tests. Không trộn sự phụ thuộc vào property naming ngẫu nhiên của Jackson với quyết định nghiệp vụ.

## 4. Controller mỏng: một HTTP operation được viết ra sao?

Một controller đúng nhiệm vụ có thể chỉ có vài dòng:

```java
@RestController
@RequestMapping(path = "/api/books", produces = MediaType.APPLICATION_JSON_VALUE)
class BookController {
    private final BookService service;

    BookController(BookService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<BookResponse> create(
            @Valid @RequestBody CreateBookRequest request) {
        Book created = service.create(request.title(), request.isbn());
        BookResponse response = toResponse(created);
        return ResponseEntity
                .created(URI.create("/api/books/" + created.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    BookResponse get(@PathVariable long id) {
        return toResponse(service.find(id));
    }

    private static BookResponse toResponse(Book book) {
        return new BookResponse(book.id(), book.title(), book.isbn());
    }
}
```

Đây là **fragment**; Java imports và các class đi kèm có ở mục 8. Điểm cần quan sát:

- `@RestController` tương đương controller có body serialization, sử dụng Spring MVC `HttpMessageConverter` để đọc/ghi JSON.
- `@RequestMapping` định nghĩa path và `produces`; `@PostMapping(consumes=...)` định nghĩa content type request phù hợp. Spring có thể trả `415` khi Content-Type không được hỗ trợ và `406` khi response không thỏa Accept header.
- `@Valid @RequestBody` kiểm tra DTO nếu dự án có Jakarta Bean Validation provider. JSON lỗi, type mismatch và Bean Validation có thể tạo các exception khác nhau.
- Controller xây dựng `Location` và `201 Created` vì đây là quyết định **HTTP**, không phải domain rule.
- `toResponse` đưa domain object sang DTO có kiểm soát; có thể chuyển mapper sang class riêng khi phức tạp hoặc tái sử dụng.
- Service chỉ biết hai giá trị nghiệp vụ và trả `Book`, **không nhận `HttpServletRequest` và không trả `ResponseEntity`**.

Với response kiểu `BookResponse`, Spring mặc định trả 200 khi thành công. Nếu cần kiểm soát header hoặc status, trả `ResponseEntity<BookResponse>`. Đừng bắt mọi handler luôn trả `ResponseEntity<?>` nếu kiểu trả về cố định đã diễn đạt contract tốt.

### Quy tắc thực dụng để đánh giá “mỏng”

Một controller method thường dễ đọc như một pipeline: `bind → validate → map → call → map → respond`. Nó không có một vòng lặp xử lý business dài, không trực tiếp gọi repository/persistence API và không quyết định business transaction. Số dòng không phải metric duy nhất: controller 40 dòng toàn ánh xạ DTO vẫn có thể hợp lý, còn controller 5 dòng chứa một lệnh SQL hoặc business operation không idempotent vẫn sai chỗ.

## 5. Service boundary: nghiệp vụ, transaction và orchestration

Dịch vụ ứng dụng là nơi diễn tả **ca sử dụng**. Với ví dụ sách, quy tắc quan trọng là:

- Tiêu đề và ISBN sau chuẩn hóa cơ bản không được để trống, ngay cả khi gọi service không qua HTTP.
- Không đăng ký hai sách có cùng ISBN theo tiêu chí của ví dụ.
- ID được cấp ở storage boundary, và tra cứu ID không hợp lệ phải thất bại có chủ đích.
- Repository là dependency thông qua interface, nên service được unit test mà không cần web server hoặc database.

```java
@Service
class BookService {
    private final BookRepository repository;

    BookService(BookRepository repository) {
        this.repository = repository;
    }

    Book create(String title, String isbn) {
        String normalizedTitle = normalize(title, "title");
        String normalizedIsbn = normalize(isbn, "isbn");
        return repository.saveUnique(normalizedTitle, normalizedIsbn);
    }

    Book find(long id) {
        if (id <= 0) {
            throw new InvalidBookInputException("id must be positive");
        }
        return repository.find(id)
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    private static String normalize(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidBookInputException(field + " must not be blank");
        }
        return value.strip();
    }
}
```

Đây là fragment trích từ ví dụ hoàn chỉnh, dùng `@Service`, interface `BookRepository` và domain record `Book`. Service không import `org.springframework.http.*`, không định nghĩa HTTP 404/409; nó ném ngoại lệ nghiệp vụ (ví dụ `BookNotFoundException`), tầng HTTP advice sẽ dịch lỗi.

### Vì sao service phải kiểm tra bất biến dù DTO đã có @NotBlank?

Bean Validation tại controller xử lý tính hợp lệ của **incoming HTTP body**, giúp trả 400 sớm. Nhưng service có thể bị gọi từ scheduled task hoặc consumer khác. Để bất biến quan trọng không bị vượt qua, đặt chúng ở service/domain và/hoặc constraint database.

Tính duy nhất của ISBN là quy tắc có cạnh tranh đồng thời. Mẫu code `if (!exists) save()` ở hai thread có race condition: cả hai đều thấy chưa tồn tại. Trong production, **unique index/constraint ở database** mới là một lớp bảo vệ quan trọng; service/adapter phải map duplicate-key violation sang lỗi nghiệp vụ. Ví dụ in-memory ở mục 8 dùng `synchronized` để minh họa thao tác atomic trong một JVM, **không phải** giải pháp lock phân tán nhiều node.

### Transaction boundary nằm đâu?

Thường đặt `@Transactional` vào service method thực hiện một ca sử dụng thay đổi dữ liệu:

```java
@Service
class OrderService {
    @Transactional
    public OrderResult place(PlaceOrderCommand command) {
        // Load aggregate, kiểm tra rule, thay đổi nhiều bảng,
        // ghi outbox event trong cùng transaction nếu cần.
        return ...; // fragment ý tưởng, không compile độc lập
    }
}
```

Dòng `return ...;` là ký hiệu mô tả, **không phải Java runnable**. Với database JPA/JDBC, cần transaction manager phù hợp. Trong Spring proxy mode, `@Transactional` thường được áp dụng khi gọi qua Spring proxy; tự gọi `this.otherTransactionalMethod()` trong cùng object không tạo proxy interception. Transaction chỉ bao phủ resource đã tham gia vào transaction; không biến HTTP gọi payment service thành hoạt động có thể rollback tự động.

Đừng đặt `@Transactional` vào mọi controller để “đảm bảo an toàn”. Điều đó trộn persistence policy vào transport và có thể giữ transaction mở trong lúc serialize response hoặc gọi mạng chậm. Dành transaction cho ranh giới nghiệp vụ cần nhất quán.

## 6. Repository port và persistence adapter

Một repository interface mô tả **những thao tác mà service cần**, thay vì phơi bày toàn bộ mọi query/trình quản lý dữ liệu:

```java
interface BookRepository {
    Book saveUnique(String title, String isbn);
    Optional<Book> find(long id);
}
```

Trong ví dụ runnable, một adapter in-memory thực hiện interface này. Nếu đổi sang JPA, bạn có thể có Spring Data repository cho `BookEntity`, còn adapter `JpaBookRepositoryAdapter` chuyển entity ↔ domain `Book` và thực hiện `BookRepository`.

Sơ đồ phụ thuộc:

```text
BookController ──► BookService ──► BookRepository (interface)
                                          ▲
                                          │ implements
                                  JpaBookRepositoryAdapter
                                          │
                                          ▼
                                Spring Data/JPA/Database
```

Lợi ích là service dễ unit test bằng in-memory/fake implementation và không bị chi phối bởi JPA lazy proxies. **Không nhất thiết phải tạo repository port riêng trong mọi ứng dụng nhỏ**; nếu việc dùng trực tiếp `JpaRepository` trong service đã ổn định, có thể chấp nhận. Khi cần tách persistence model, testing độc lập hoặc nhiều data sources, port/adapter tạo ranh giới hữu ích.

### Những quy tắc không nên đánh đổi

- Không trả JPA `Page<Entity>` trực tiếp chỉ vì tiện: cần quyết định DTO fields, pagination metadata và contract public.
- Đừng để controller cài điều kiện business bằng các chuỗi `if` rải rác; tập trung vào service/domain.
- Không dùng lazy-loading bên ngoài transaction một cách tình cờ khi Jackson serialize entity.
- Khi API nhận các ID liên quan tài nguyên của user khác, service/security phải enforce authorization theo resource, tránh insecure direct object reference (IDOR).
- Kiểu lỗi storage cụ thể như `DataIntegrityViolationException` nên được dịch ở boundary phù hợp thành domain conflict khi đã hiểu nguyên nhân, không tùy tiện chuyển mọi lỗi database thành 409.

## 7. Validation, error contract và HTTP status phải nhất quán

### 7.1 Phân biệt validation shape và business validation

| Loại vấn đề | Nơi phát hiện lý tưởng | HTTP mapping gợi ý |
| --- | --- | --- |
| JSON bị sai syntax hoặc type | Spring `HttpMessageConverter` | `400 Bad Request` |
| Bắt buộc field title/isbn | `@Valid` / Bean Validation | `400 Bad Request` |
| ID path không chuyển được sang `long` | Spring MVC argument binding | `400 Bad Request` |
| ID đã parse nhưng không hợp lệ (<=0) | Service/domain | `400 Bad Request` |
| Không có resource tương ứng | Service/domain | `404 Not Found` |
| ISBN đã tồn tại | Service/repository uniqueness | `409 Conflict` |
| User chưa đăng nhập / không được phép | Security layer | `401 Unauthorized` / `403 Forbidden` |
| Request quá lớn / quá tải | Web/server/gateway policy | `413` hoặc `429` khi phù hợp |
| Lỗi chương trình/hạ tầng không lường trước | Exception monitoring | `500` hoặc `503` theo tình huống; không lộ stack trace |

Những status trên là **mapping policy**, không phải annotation tự suy luận toàn bộ. Spring MVC có hành vi mặc định cho nhiều lỗi binding/validation, nhưng ứng dụng cần định nghĩa payload lỗi thống nhất.

### 7.2 Dùng ProblemDetail để báo lỗi công khai

Spring Framework 6 hỗ trợ `ProblemDetail` theo chuẩn problem details; trong Spring 6.1 tài liệu liên quan RFC 7807, các bản cập nhật tham chiếu RFC 9457. Ví dụ:

```java
@RestControllerAdvice
class ApiErrorHandler {

    @ExceptionHandler(BookNotFoundException.class)
    ProblemDetail notFound(BookNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateIsbnException.class)
    ProblemDetail duplicate(DuplicateIsbnException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidBookInputException.class)
    ProblemDetail invalid(InvalidBookInputException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
```

Ví dụ hoàn chỉnh còn xử lý `MethodArgumentNotValidException` cho `@Valid`. Spring hỗ trợ content type `application/problem+json` khi serialize ProblemDetail. Các trường thường thấy gồm `type`, `title`, `status`, `detail`, `instance`; bạn có thể thêm extension properties đã kiểm soát như `errorCode`, `traceId` và danh sách `violations`.

**Không phản ánh trực tiếp exception message nội bộ ra công khai**: SQL table names, stack traces, bearer token, raw user input nhạy cảm, internal hostnames hoặc vendor errors có thể gây rò thông tin. Ví dụ hiển thị `BookNotFoundException` chứa ID đơn giản không nhạy cảm; production nên dùng error codes và messages đã được chuẩn hóa.

### 7.3 Lỗi nghiệp vụ khác lỗi lập trình

Một `BookNotFoundException` dự kiến khi GET ID không có dữ liệu là business outcome, phù hợp 404. `NullPointerException` trong service không được “cứu” bằng `@ExceptionHandler(Exception.class)` rồi trả 200 hoặc 400. Ghi log correlation ID, trả response 500 an toàn và sửa bug. Nếu external dependency không sẵn sàng, có thể trả 503 theo hợp đồng, nhưng thiết kế retry/backoff và circuit breaker độc lập.

Đối với update, kết hợp `ETag` và `If-Match` hoặc version field giúp phát hiện lost update (thường trả 412 Precondition Failed theo HTTP semantics khi precondition không đạt). Không mặc định mọi `PUT` sẽ idempotent nếu business logic ghi thêm bản ghi phụ hoặc phát event không kiểm soát.

## 8. Ví dụ hoàn chỉnh: API Spring Boot Java 17+

Ví dụ API sách dưới đây chạy **không cần database**: lưu trong một in-memory repository, dùng Spring MVC và Jakarta Validation. Nó minh họa HTTP adapter, service boundary, repository port, DTOs, duplicate detection và error contract. Cơ chế in-memory không lưu dữ liệu sau restart và không dùng cho multi-instance.

### 8.1 Cấu trúc và dependencies

```text
thin-http-demo/
├── build.gradle
└── src/
    ├── main/java/demo/ThinHttpApp.java
    └── test/java/demo/BookServiceTest.java
```

`build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.13'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'demo'
version = '1.0.0'

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}

springBoot {
    mainClass = 'demo.ThinHttpApp'
}
```

Cần Gradle phù hợp phiên bản plugin cùng JDK 17+. Nếu dự án đã có Gradle Wrapper, sử dụng wrapper; ví dụ này không giả định wrapper tự có trong thư mục mới tạo. Spring Boot starter web đem theo Spring MVC + Jackson; starter validation đem theo Hibernate Validator/Jakarta Bean Validation implementation.

### 8.2 Source đầy đủ: ThinHttpApp.java

```java
package demo;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@SpringBootApplication
public class ThinHttpApp {
    public static void main(String[] args) {
        SpringApplication.run(ThinHttpApp.class, args);
    }
}

// ----- Public API contracts -----

record CreateBookRequest(
        @NotBlank String title,
        @NotBlank String isbn) {}

record BookResponse(
        long id,
        String title,
        String isbn) {}

// ----- Domain model và application exceptions -----

record Book(long id, String title, String isbn) {}

class BookNotFoundException extends RuntimeException {
    BookNotFoundException(long id) {
        super("Book " + id + " not found");
    }
}

class DuplicateIsbnException extends RuntimeException {
    DuplicateIsbnException(String isbn) {
        super("ISBN already exists: " + isbn);
    }
}

class InvalidBookInputException extends RuntimeException {
    InvalidBookInputException(String detail) {
        super(detail);
    }
}

// ----- Port của storage -----

interface BookRepository {
    Book saveUnique(String title, String isbn);
    Optional<Book> find(long id);
}

// ----- Persistence adapter (chỉ dành cho demo) -----

@Repository
class InMemoryBookRepository implements BookRepository {
    private final Map<Long, Book> byId = new HashMap<>();
    private final Map<String, Long> idByIsbn = new HashMap<>();
    private long nextId = 1;

    @Override
    public synchronized Book saveUnique(String title, String isbn) {
        if (idByIsbn.containsKey(isbn)) {
            throw new DuplicateIsbnException(isbn);
        }

        Book book = new Book(nextId++, title, isbn);
        byId.put(book.id(), book);
        idByIsbn.put(isbn, book.id());
        return book;
    }

    @Override
    public synchronized Optional<Book> find(long id) {
        return Optional.ofNullable(byId.get(id));
    }
}

// ----- Application service: hoàn toàn độc lập với HTTP -----

@Service
class BookService {
    private final BookRepository repository;

    BookService(BookRepository repository) {
        this.repository = repository;
    }

    Book create(String title, String isbn) {
        String normalizedTitle = normalize(title, "title");
        String normalizedIsbn = normalize(isbn, "isbn");
        return repository.saveUnique(normalizedTitle, normalizedIsbn);
    }

    Book find(long id) {
        if (id <= 0) {
            throw new InvalidBookInputException("id must be positive");
        }
        return repository.find(id)
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    private static String normalize(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidBookInputException(field + " must not be blank");
        }
        return value.strip();
    }
}

// ----- HTTP adapter: bind, validate, map, respond -----

@RestController
@RequestMapping(
        path = "/api/books",
        produces = MediaType.APPLICATION_JSON_VALUE)
class BookController {
    private final BookService service;

    BookController(BookService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<BookResponse> create(
            @Valid @RequestBody CreateBookRequest request) {
        Book result = service.create(request.title(), request.isbn());
        BookResponse body = toResponse(result);
        URI location = URI.create("/api/books/" + result.id());
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{id}")
    BookResponse get(@PathVariable long id) {
        return toResponse(service.find(id));
    }

    private static BookResponse toResponse(Book book) {
        return new BookResponse(book.id(), book.title(), book.isbn());
    }
}

// ----- HTTP exception mapping -----

@RestControllerAdvice
class ApiErrorHandler {
    @ExceptionHandler(BookNotFoundException.class)
    ProblemDetail notFound(BookNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateIsbnException.class)
    ProblemDetail duplicate(DuplicateIsbnException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidBookInputException.class)
    ProblemDetail badInput(InvalidBookInputException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request body validation failed");
    }
}
```

**Tại sao chỉ một Java file mà vẫn thể hiện ranh giới?** Java cho phép chỉ một class public (`ThinHttpApp`), còn các top-level classes/records package-private cùng file. Đây là cách rút ngắn phần setup ví dụ. Trong codebase thật nên tách chúng theo trách nhiệm, ví dụ `api/`, `application/`, `domain/` và `persistence/`, để ownership và visibility rõ hơn. `InMemoryBookRepository` có `synchronized` trên cả ghi và đọc để giữ hai maps nhất quán trong một process; thay bằng storage phù hợp khi triển khai thật.

Controller không biết hai maps đang tồn tại. Service không biết `ResponseEntity` hay `ProblemDetail`. Đổi từ in-memory sang JPA adapter chỉ ảnh hưởng implementation repository và wiring nếu interface giữ nguyên.

### 8.3 Unit test service không cần Spring context

`src/test/java/demo/BookServiceTest.java`:

```java
package demo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookServiceTest {

    @Test
    void createsAndFindsBook() {
        BookService service =
                new BookService(new InMemoryBookRepository());

        Book book = service.create("  Spring HTTP  ", "  123  ");

        assertEquals(1L, book.id());
        assertEquals("Spring HTTP", book.title());
        assertEquals("123", book.isbn());
        assertEquals(book, service.find(1L));
    }

    @Test
    void rejectsDuplicateAndMissingBook() {
        BookService service =
                new BookService(new InMemoryBookRepository());

        service.create("First", "ISBN-1");

        assertThrows(DuplicateIsbnException.class,
                () -> service.create("Other", "ISBN-1"));
        assertThrows(BookNotFoundException.class,
                () -> service.find(42L));
        assertThrows(InvalidBookInputException.class,
                () -> service.find(0L));
    }
}
```

Test gọi service như Java object bình thường. Không có Tomcat, HTTP, Spring `ApplicationContext`, Jackson hay database. Đây là một dấu hiệu tốt: **business logic có ranh giới riêng** và có thể xác minh độc lập.

Chạy:

```bash
gradle test
gradle bootRun
```

Trong thực tế, `gradle test` là lệnh hoàn tất rồi `gradle bootRun` chạy server; khi dùng hai terminal riêng, có thể chạy các lệnh tách biệt. Nếu có wrapper: `./gradlew test` / `./gradlew bootRun`, trên PowerShell `.\gradlew.bat test` / `.\gradlew.bat bootRun`.

Kết quả test được kỳ vọng: **2 tests pass**. Đây là kỳ vọng logic dựa trên mã đã trình bày, không phải báo cáo một lần chạy thực tế trong môi trường này. Không có stdout cố định từ `System.out.println`; Gradle sẽ ghi log riêng.

### 8.4 Dùng curl kiểm tra public contract

Khi Boot server đang ở port 8080, tạo sách:

```bash
curl -i -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -d '{"title":"Spring HTTP","isbn":"9780000000001"}'
```

**Ví dụ response thành công** (chỉ hiển thị status/header/body liên quan, server có thể thêm headers và thay đổi thứ tự):

```http
HTTP/1.1 201 Created
Location: /api/books/1
Content-Type: application/json

{"id":1,"title":"Spring HTTP","isbn":"9780000000001"}
```

GET tiếp:

```bash
curl -i http://localhost:8080/api/books/1
```

**Ví dụ response:**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"id":1,"title":"Spring HTTP","isbn":"9780000000001"}
```

POST lần hai với cùng ISBN (service đã strip whitespace) trả **409 Conflict** và `ProblemDetail` có `status:409` cùng thông điệp duplicate. Gọi `GET /api/books/999` trả **404 Not Found**; gọi `GET /api/books/0` trả **400 Bad Request**. POST body `{"title":"","isbn":"x"}` sẽ bị Bean Validation chặn và trả 400 trước khi service tạo sách. Body JSON sai cú pháp cũng thường nhận HTTP 400 từ Spring MVC, dù không đi qua handler cho `MethodArgumentNotValidException`.

**Không khẳng định chính xác toàn bộ JSON lỗi** từ ví dụ nếu có auto-configuration hay extension khác. Chỉ coi các status và những trường contract được định nghĩa là kỳ vọng; để ổn định tuyệt đối, hãy thêm HTTP integration test kiểm tra response JSON keys, content type và status.

Thử POST hợp lệ khi server vừa restart: in-memory repository reset nên ID bắt đầu lại ở 1. Đó là behavior của demo, không phải requirement cho ứng dụng production.

### 8.5 Vì sao cần thêm HTTP contract test?

Unit test `BookService` không thể phát hiện lỗi `@PostMapping` sai route, Jackson không deserialize được record, thiếu `@Valid`, sai `Location` hoặc status 200 thay vì 201. Vì vậy cần một lớp web test dùng `MockMvc` hoặc chạy server trên random port; với Spring Boot có thể dùng `@WebMvcTest(BookController.class)` + mock service, hoặc `@SpringBootTest` + `@AutoConfigureMockMvc` để kiểm tra wiring. Các test nên assert contract thật, ví dụ:

- `POST /api/books` với JSON hợp lệ trả `201`, header `Location` đúng và JSON có ba field dự kiến.
- POST thiếu title trả `400`, service **không được gọi**.
- Duplicate ISBN trả `409` và error contract `application/problem+json`.
- GET ID không tồn tại trả `404`; GET ID tồn tại trả `200`.
- JSON không hợp lệ trả `400`; request Content-Type sai có thể trả `415`.

Với security được bật, test còn phải xác minh 401/403 đúng và bảo đảm user không truy cập tài nguyên của người khác. Kết hợp unit tests cho luật nghiệp vụ, web tests cho mapping, persistence integration tests cho uniqueness/transactions và end-to-end tests cho các hợp đồng quan trọng.

## 9. Thiết kế request/response mapping không tạo coupling ngầm

### 9.1 DTO mapping nên rõ ràng

Trong demo, mapper là một private method:

```java
private static BookResponse toResponse(Book book) {
    return new BookResponse(
            book.id(), book.title(), book.isbn());
}
```

Nếu có nhiều endpoint, mapping phức tạp hoặc cần reuse, có thể tách `BookDtoMapper`. Một mapper nên biểu diễn chuyển đổi dữ liệu có chủ đích, không chứa transaction, repository lookup hay network calls. MapStruct có thể hữu ích cho nhiều fields nhưng không tự giải quyết design contract; bạn vẫn phải chọn field nào công khai.

### 9.2 Những thay đổi hợp đồng dễ làm client hỏng

| Thay đổi | Tác động | Cách làm an toàn hơn |
| --- | --- | --- |
| Đổi tên JSON `isbn` thành `bookNumber` | Client cũ đọc field sai | Giữ alias tương thích hoặc version API |
| Đổi `id` từ số sang UUID string | Client parsing/storage thay đổi | Thiết kế migration và version |
| Biến optional field thành required | Request cũ trả 400 | Có giai đoạn chuyển tiếp, default hoặc endpoint version |
| Trả thêm field optional mới | Thường backward compatible với client tolerant | Đừng giả định mọi client đều bỏ qua unknown fields |
| Đổi 404 thành 200 + `null` | Consumer error branch sai | Giữ semantics ổn định, thông báo contract change |
| Dùng persistence entity mới làm response | Có nguy cơ lộ internal fields | Map explicit response DTO |
| Đổi thứ tự JSON properties | Thường không quan trọng về ngữ nghĩa JSON | Consumer không nên so sánh raw JSON string |

OpenAPI giúp khám phá hợp đồng nhưng file schema tự sinh không thay thế review về semantics. Một API có OpenAPI hợp lệ vẫn có thể có tên trường khó hiểu hoặc lỗi domain được trả sai status.

### 9.3 Versioning, pagination và idempotency

Versioning có thể nằm trong URL (`/api/v1/books`), header hoặc media type. Quan trọng hơn vị trí là **chính sách tương thích**: thay đổi nào breaking, hỗ trợ phiên bản cũ bao lâu, deprecation và migration diễn ra thế nào.

Endpoint danh sách nên quy định pagination và sort thay vì trả toàn bộ dataset theo kích thước tùy ý. Trả response DTO với `items`, `nextCursor` hoặc `page`/`size` theo yêu cầu, không lộ trực tiếp cấu trúc `Page<Entity>` bên trong. Limit max page size ở HTTP boundary; service phải xác thực query scope và bảo vệ query cost.

Với POST có side effects hoặc thanh toán, cân nhắc `Idempotency-Key` được client gửi qua header. Service/storage cần lưu trạng thái key theo user/operation cùng request fingerprint, xử lý cạnh tranh và TTL; chỉ đọc header trong controller mà không có cơ chế lưu/dedup thì **chưa** đạt idempotency.

## 10. Bảo mật, quan sát và vận hành tại ranh giới

### 10.1 AuthN/AuthZ không phải chuyện controller tự đọc JWT

Authentication xác định danh tính và thường do Spring Security filter chain xử lý. Controller có thể nhận principal hoặc security context cần thiết, sau đó application layer xác minh **quyền trên tài nguyên và use case**. Không tin bất kỳ `userId`/`tenantId` nào do client tự gửi là bằng chứng sở hữu.

Ví dụ `GET /api/books/42` trong demo là public, chưa có auth. Production cần quyết định ai được đọc/tạo/sửa, bảo vệ IDOR và multi-tenancy. Ràng buộc tài nguyên nên được enforce trong service/repository query dựa trên authenticated identity, không chỉ ẩn nút trên frontend.

### 10.2 Logging và tracing theo đúng lớp

- Ở HTTP layer: log HTTP method, route template, status, latency, correlation ID/trace ID; tránh log full Authorization headers, cookies và body nhạy cảm.
- Ở service: log sự kiện nghiệp vụ có ý nghĩa (book registered, order placed), outcome, identifier không nhạy cảm, không lặp dữ liệu request.
- Ở persistence: metric query latency, slow query và constraint violations; không log raw database credentials.
- Ở exception mapping: phân biệt expected client/business errors với unexpected 5xx. Không ghi stack trace 404 dự kiến như lỗi nghiêm trọng.
- Dùng Micrometer/Observation/trace propagation của Spring Boot khi phù hợp, để request ID nối qua HTTP client, service và storage.

Observability không nên làm service biết `HttpServletRequest`; truyền explicit business context/correlation khi thực sự cần, hoặc dựa tracing context được quản lý đúng.

### 10.3 Timeout, tài nguyên và tránh blocking không phù hợp

Với MVC/Servlet, thread request có thể bị giữ trong lúc đồng bộ gọi database/remote API; phải có connection pool, timeout và giới hạn đồng thời. Với WebFlux, controller nên trả reactive pipeline và tránh `block()` trên event-loop. Ranh giới service vẫn phải rõ dù method signature có thể là `Mono<Result>` hoặc `Flux<Result>`.

Khi service gọi API bên ngoài, định nghĩa connect/response timeout, retry có điều kiện, circuit breaker và backpressure phù hợp. Không để controller chứa `Thread.sleep`, infinite retry hay đồng bộ hóa trên một lock toàn cục để chữa nghẽn tải. Khi công việc rất dài, có thể trả `202 Accepted` với operation ID và tách xử lý background/job theo design thay vì giữ một HTTP request mở quá lâu.

## 11. Chọn package structure và cách refactor controller đang phình to

Một cấu trúc đơn giản theo feature thường dễ theo dõi hơn một thư mục chứa hàng trăm controllers/services chung:

```text
com.example.books/
├── api/
│   ├── BookController.java
│   ├── CreateBookRequest.java
│   ├── BookResponse.java
│   └── BookApiErrorHandler.java
├── application/
│   ├── BookService.java
│   └── BookRepository.java
├── domain/
│   ├── Book.java
│   └── DuplicateIsbnException.java
└── persistence/
    ├── JpaBookRepositoryAdapter.java
    └── BookEntity.java
```

Đây là mô hình tổ chức, không bắt buộc project nhỏ phải có đúng bốn package. Với nhiều bounded contexts, mỗi feature có thể quản lý domain và adapter của riêng nó. Đặt service name theo **ca sử dụng** (`RegisterBookService`, `PlaceOrderService`) nếu class quá lớn; không để một `CommonService` phụ trách mọi thứ.

### Các bước refactor an toàn

1. **Chụp public contract hiện tại:** routes, methods, body JSON, status, headers, error cases. Tạo HTTP tests cho behavior mà client đang dùng.
2. **Xác định luật nghiệp vụ** nằm trong controller: kiểm tra trạng thái, quyền resource, uniqueness, transaction, orchestration.
3. **Tách request/response DTO khỏi persistence entity**, đưa mapping vào adapter; tránh đổi JSON contract ngoài ý muốn.
4. **Đưa use case vào service** với input/output không phụ thuộc HTTP. Gắn transaction boundary vào service khi cần.
5. **Đưa storage vào repository/adapter**, dùng database constraints bảo vệ dữ liệu cạnh tranh.
6. **Chuẩn hóa exception translation** bằng `@RestControllerAdvice`/`ProblemDetail`, giữ status codes công khai nếu đã là hợp đồng ổn định.
7. **Chạy unit + HTTP contract + persistence integration tests** và kiểm tra logs/metrics trước khi rollout.

Đừng viết lại tất cả endpoints cùng lúc chỉ để “đẹp kiến trúc”. Với hệ thống legacy, chọn một vertical slice, giữ behavior và refactor dần. Nếu đang tồn tại contract sai nhưng client vẫn dựa vào nó, sửa cần kế hoạch tương thích/versioning thay vì âm thầm đổi 200 sang 404 trên production.

## 12. Những phản mẫu dễ gặp

1. **Controller gọi thẳng repository và chứa mọi rule:** business logic khó reuse/test, transaction boundary phân tán.
2. **Service trả `ResponseEntity` hoặc nhận `HttpServletRequest`:** transport coupling tràn vào application core.
3. **Trả JPA entity trực tiếp:** khó khóa chặt JSON schema, có nguy cơ lazy loading và lộ internal fields.
4. **Tin rằng `@Valid` đã bảo vệ mọi rule:** nó không thay thế business invariants, concurrency constraints hoặc authorization.
5. **Check duplicate bằng exists + save không atomic:** chạy song song sẽ tạo trùng; dùng database unique constraint và map lỗi hợp lý.
6. **Bắt tất cả exceptions rồi trả 200 với `success:false`:** vi phạm HTTP semantics, phá monitoring/retry/caching và hợp đồng client.
7. **Dùng mọi lỗi business là 500:** xóa nhòa expected outcomes và làm hệ thống báo động giả.
8. **Dùng `Map<String,Object>` cho mọi DTO:** dễ typo/thiếu compile-time safety, contract không rõ và thiếu validation.
9. **Biến service thành “God object” với hàng chục dependencies:** mỗi ca sử dụng nên có scope rõ; tách orchestration/aggregate theo domain boundaries.
10. **Dùng DTO class làm domain entity:** cùng một object có cả `@Entity`, `@JsonProperty`, `@RequestBody` và business lifecycle, khiến mọi tầng đổi theo nhau.
11. **Phụ thuộc message wording thay vì error code:** client parse `detail` string để ra quyết định, dẫn tới breaking khi dịch ngôn ngữ.
12. **Không phân biệt create đồng bộ và xử lý async:** trả 201 ngay khi job chưa tạo resource; cân nhắc 202 + operation status endpoint.
13. **Log request/response bodies mặc định:** có thể rò token, dữ liệu cá nhân và tăng log volume.
14. **Phỏng đoán performance từ số lớp:** chi phí network/DB/serialization thường vượt một lời gọi service; benchmark có chứng cứ rồi mới tối ưu.

## 13. Bài tập tự kiểm tra

**Câu 1.** Controller có method gọi JPA repository, chuyển tiền và gửi email trước khi trả response. Vì sao nên tách service?

**Trả lời:** HTTP adapter đang sở hữu cả business orchestration, transaction và integration failure handling. Service cần đặt ranh giới ca sử dụng; các side effects ngoài database có chính sách idempotency/outbox/saga phù hợp.

**Câu 2.** Có `@NotBlank` trên `CreateBookRequest.title`; có cần check null/blank trong service nữa không?

**Trả lời:** Với invariant quan trọng, có. Service có thể được gọi từ job/message listener không qua `@Valid`. Tránh duplicate validation quá phức tạp nhưng đừng đặt domain safety phụ thuộc HTTP.

**Câu 3.** Client gửi POST ISBN đã tồn tại; có nên trả 500?

**Trả lời:** Không, nếu duplicate là business conflict dự kiến, 409 là lựa chọn hợp lý. Mapping phải xuất phát từ rule thực, không biến mọi database exception thành 409.

**Câu 4.** Chỉ có unit test BookService thì đủ chứng minh endpoint POST trả 201 và Location đúng chưa?

**Trả lời:** Chưa. Cần HTTP contract test cho annotation routes, JSON binding, validation, status, headers, errors, security và serialization.

**Câu 5.** Có nên trả `BookEntity` trực tiếp làm JSON vì entity có sẵn getters?

**Trả lời:** Không nên. Public DTO kiểm soát fields và versioning; entity thường chứa nội bộ, relationships hoặc persistence-specific metadata.

**Câu 6.** Tạo thêm ba Spring instances có giúp unique ISBN an toàn nếu mỗi instance tự lock bằng `synchronized` không?

**Trả lời:** Không. Lock chỉ nằm trong JVM. Production cần unique index/constraint ở storage dùng chung và cơ chế xử lý tranh chấp.

**Câu 7.** REST controller trả 202 thay 201 trong trường hợp nào?

**Trả lời:** Khi request chỉ được **chấp nhận để xử lý sau**, chưa hoàn tất việc tạo resource. Contract nên có operation ID/status URL để client kiểm tra trạng thái.

**Câu 8.** Khi service đã tách khỏi HTTP, WebFlux có thể tái sử dụng service đồng bộ cũ trên event-loop một cách tự nhiên không?

**Trả lời:** Không tự động. Blocking I/O phải tránh trên event-loop; có thể cần reactive persistence/client, hoặc chuyển blocking tác vụ sang scheduler thích hợp có kiểm soát. Ý tưởng ranh giới vẫn đúng, nhưng execution model ảnh hưởng signatures và thread usage.

## Tài liệu tham khảo chính thức

- [Spring Framework — Annotated Controllers (Spring MVC)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html)
- [Spring Framework — RequestBody and Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html)
- [Spring Framework — Controller Advice](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html)
- [Spring Framework — Error Responses / Problem Details](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- [Spring Framework — Validation](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html)
- [Spring Framework — Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- [Spring Boot — Validation](https://docs.spring.io/spring-boot/reference/io/validation.html)
- [Spring Boot — Testing](https://docs.spring.io/spring-boot/reference/testing/index.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="operations-practice-002">Quan sát yêu cầu/phản hồi bằng log, tracing và Micrometer Observation</a>

<details>
<summary>Xem chi tiết</summary>

# Quan sát yêu cầu/phản hồi bằng log, tracing và Micrometer Observation trong Spring Web

Một endpoint Spring MVC có thể trả `200 OK` nhưng người dùng vẫn thấy chậm; một request khác có thể kết thúc bằng `500` nhưng log chỉ ghi “Something went wrong”. Muốn tìm nguyên nhân, cần biết **request nào đã vào hệ thống, đi qua thành phần nào, mất bao lâu, trả về status gì, có lỗi nào, và các bản ghi liên quan có thể nối với nhau hay không**.

Quan sát (observability) không chỉ là in ra nhiều log. Trong Spring Framework 6.1/Spring Boot 3.3, ba nguồn tín hiệu thường phối hợp là **logs**, **metrics** và **distributed traces**. Micrometer Observation tạo một lớp ghi nhận sự kiện chung: qua các `ObservationHandler` tương ứng, cùng một observation có thể sinh meter/metric và span, trong khi logging mô tả diễn biến cụ thể của ứng dụng. Bài học tập trung vào request/response của **Spring MVC trên Servlet stack**, có đối chiếu ranh giới HTTP client và truy vết liên dịch vụ khi cần.

## 1. Từ một request đến ba loại bằng chứng

Giả sử ứng dụng có endpoint `GET /api/items/42`. Nhìn vào ba tín hiệu:

| Tín hiệu | Trả lời câu hỏi gì? | Ví dụ nội dung | Phạm vi và điểm yếu |
| --- | --- | --- | --- |
| **Log** | “Điều gì đã xảy ra trong lần xử lý này?” | “start lookup”, “lookup failed”, exception stack trace, `traceId` | Chi tiết, có thứ tự gần đúng; thiếu correlation thì khó nối nhiều dòng/dịch vụ |
| **Metric** | “Tần suất, thời gian, tỷ lệ lỗi của nhiều request là bao nhiêu?” | Số lần, tổng/thống kê độ trễ của `http.server.requests` theo method/status/URI template | Rẻ để tổng hợp/xem xu hướng; không mô tả từng lần xử lý |
| **Trace** | “Một lần xử lý cụ thể đã đi qua các service/span nào, mất thời gian ở đâu?” | Trace A có server span → client span → downstream server span | Cần tracer, truyền context và backend; sampling có thể bỏ qua một số request |

Ví dụ điều tra: cảnh báo p95 latency tăng ở `GET /api/items/{id}` từ metric; lọc trace chậm để thấy span gọi dịch vụ danh mục chiếm đa số thời gian; dùng `traceId` của trace đó để xem log và nguyên nhân cụ thể. **Không dùng một metric để khôi phục nội dung request đơn lẻ, cũng không dùng log rời rạc thay cho tỷ lệ lỗi tổng hợp.**

```text
Client
  └─ HTTP GET /api/items/42
      ├─ Servlet filter / Spring MVC
      │   ├─ HTTP server Observation: http.server.requests
      │   │   ├─ ObservationHandler → metric timer
      │   │   └─ tracing handler → server span (khi có tracer)
      │   ├─ Controller/Service → application logs
      │   └─ HTTP response 200
      └─ HTTP client / network response

Người vận hành
  ├─ metrics → lượng request, duration, status, nhóm URI
  ├─ traces  → quan hệ span và thời gian trên một trace
  └─ logs    → sự kiện, lỗi, dữ kiện phục vụ điều tra
```

Tên `http.server.requests` là tên observation/meter trong cấu hình mặc định Spring Framework 6.1. Backend như Prometheus có thể đổi quy tắc đặt tên meter khi xuất dữ liệu. Không mặc định coi một tên metric trong Prometheus là nguyên văn tên observation.

## 2. Bắt đầu từ log request/response có ý nghĩa

### 2.1. Log ở đúng cấp độ

Log có thể đặt ở controller/service để mô tả sự kiện thực sự có giá trị: bắt đầu xử lý, lựa chọn nhánh quan trọng, kết quả nghiệp vụ, lỗi cần triage. Khi cần tổng quan từng HTTP request, dùng filter hoặc access log của server/proxy thay vì rải câu `log.info` vào mọi controller. Mỗi nguồn quan sát có phạm vi khác nhau:

- **Application log:** controller/service biết nghiệp vụ và nguyên nhân; filter biết HTTP method/status nhưng không tự biết tất cả chi tiết nghiệp vụ.
- **Servlet filter log:** có thể ghi method, route, status và duration; thứ tự filter/dispatch ảnh hưởng điều gì được ghi, nhất là async request.
- **Container access log:** ghi giao dịch HTTP từ góc nhìn server; format và cấu hình phụ thuộc Tomcat/Jetty/Undertow.
- **Proxy/ingress log:** phản ánh những request bị chặn hoặc lỗi ngay ở proxy, có thể không hề vào ứng dụng Java.

Đừng mở log debug toàn bộ body, token, cookie hoặc header `Authorization` ở production. URL có thể chứa email, ID hoặc query nhạy cảm; ưu tiên **route template** `/api/items/{id}` thay cho raw URL khi log phục vụ tổng hợp. Nếu cần log chi tiết để xử lý sự cố, phải xác định rõ các trường cho phép, mức giữ dữ liệu và nơi truy cập.

### 2.2. Controller ví dụ có log nghiệp vụ

Giả sử ứng dụng Spring Boot 3.3/Java 17+ có `spring-boot-starter-web` và `spring-boot-starter-actuator`. Tệp `src/main/java/com/example/observe/ObserveApplication.java`:

```java
package com.example.observe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ObserveApplication {
    public static void main(String[] args) {
        SpringApplication.run(ObserveApplication.class, args);
    }
}
```

Tệp `src/main/java/com/example/observe/ItemController.java`:

```java
package com.example.observe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
public class ItemController {
    private static final Logger log =
            LoggerFactory.getLogger(ItemController.class);

    private final ItemService service;

    public ItemController(ItemService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ItemDto> find(@PathVariable long id) {
        log.debug("Looking up item");
        String label = service.findLabel(id);
        log.info("Item lookup completed");
        return ResponseEntity.ok(new ItemDto(id, label));
    }

    public record ItemDto(long id, String label) {}
}
```

Ví dụ intentionally không in `id` vào log. Khi hệ thống được phân quyền và phân loại dữ liệu, có thể log khóa định danh đã được duyệt, nhưng không nên coi mọi `@PathVariable` đều an toàn để xuất log.

Tệp `src/main/java/com/example/observe/ItemService.java`:

```java
package com.example.observe;

import org.springframework.stereotype.Service;

@Service
public class ItemService {
    public String findLabel(long id) {
        return "Item-" + id;
    }
}
```

Request `GET /api/items/42` dự kiến trả JSON tương đương `{"id":42,"label":"Item-42"}` và status 200. Đó là kết quả logic của mã mẫu; thứ tự trường JSON không phải là hợp đồng bắt buộc khi không cấu hình thêm.

Có thể cấu hình mức log:

```properties
logging.level.root=INFO
logging.level.com.example.observe=DEBUG
```

`DEBUG` cho package riêng làm rõ sự kiện mà không mở log DEBUG toàn bộ Spring. Trong môi trường thật, log ở mức `INFO` trên mỗi request có thể tạo lưu lượng lớn; cân nhắc dựa vào metrics/access logs cho thống kê thường xuyên và giữ log nghiệp vụ ở đúng mức.

### 2.3. Nếu viết filter, phải tôn trọng vòng đời Servlet

Khi cần request summary độc lập với controller, một `OncePerRequestFilter` tối giản có thể ghi method, status và thời gian cho những dispatch thông thường. Ví dụ sau là **đoạn minh họa cách chọn thông tin an toàn**, không thay thế instrumentation chuẩn của Micrometer:

```java
// Bên trong doFilterInternal(...) của OncePerRequestFilter:
long started = System.nanoTime();
try {
    filterChain.doFilter(request, response);
} finally {
    long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS
            .toMillis(System.nanoTime() - started);

    Object matchedPattern = request.getAttribute(
            org.springframework.web.servlet.HandlerMapping
                    .BEST_MATCHING_PATTERN_ATTRIBUTE);

    String route = matchedPattern instanceof String pattern
            ? pattern
            : "UNMATCHED";

    logger.info("method={} route={} status={} durationMs={}",
            request.getMethod(), route, response.getStatus(), elapsedMs);
}
```

Đoạn trên không phải class Java độc lập: nó cần nằm trong một subclass filter đã định nghĩa `logger` và nhận tham số `request`, `response`, `filterChain`. Với Servlet async, thời gian ở `finally` có thể chỉ phản ánh thời điểm dispatch hoàn thành, chưa phải lúc response cuối cùng gửi xong. Nếu cần đo đúng lifecycle HTTP, ưu tiên instrumentation Servlet server tích hợp sẵn và cấu hình dispatch phù hợp.

Không tự tạo thêm `Observation` tên `http.server.requests` quanh mỗi request **khi đã có** `ServerHttpObservationFilter`; có thể nhân đôi metric/span và làm sai số liệu.

## 3. Micrometer Observation: đơn vị ghi nhận chung

Micrometer phân biệt các thành phần:

- `ObservationRegistry`: registry tiếp nhận/điều phối observation; không có handler hữu hiệu, observation có thể không sinh metric hay span.
- `Observation`: khoảng hoạt động được đặt tên, có thời điểm bắt đầu/kết thúc và context.
- `Observation.Context`: lưu ngữ cảnh cho quá trình xử lý observation (bao gồm trạng thái lỗi và key-values).
- `ObservationConvention`: quy định tên/contextual name và nhãn (key-values) phù hợp từng loại observation.
- `ObservationHandler`: nhận sự kiện lifecycle, ví dụ tích hợp ghi metric và tạo trace span.
- `ObservationFilter`: tùy biến key-values/context khi observation hoàn tất; khác với Servlet Filter.

Một observation cho HTTP server thường nằm quanh một lần trao đổi request/response; tên mặc định ở Spring MVC là `http.server.requests`. Ngoài observation tự động, bạn có thể chủ động quan sát một đoạn nghiệp vụ khi cần biết thời gian của đoạn đó.

Tệp `src/main/java/com/example/observe/ObservedCatalog.java`:

```java
package com.example.observe;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Service;

@Service
public class ObservedCatalog {
    private final ObservationRegistry registry;

    public ObservedCatalog(ObservationRegistry registry) {
        this.registry = registry;
    }

    public String loadCategory() {
        return Observation.createNotStarted(
                        "catalog.load", registry)
                .lowCardinalityKeyValue("operation", "read")
                .observe(() -> "general");
    }
}
```

`.observe(Supplier<T>)` bao quanh phần xử lý: khởi động observation, thực hiện supplier và kết thúc observation kể cả khi supplier ném exception (theo lifecycle hỗ trợ của API). Với `spring-boot-starter-actuator`, Spring Boot tự cấu hình hạ tầng registry/handler cho các khả năng có trên classpath. Bài ví dụ dùng tên riêng `catalog.load` nên **không thay** metric HTTP `http.server.requests` do Framework cung cấp.

Với workload có thể lỗi, ưu tiên dùng phương thức `observe(...)` hoặc `try`/`finally` đúng vòng đời thay vì `start()` rồi quên `stop()`. Observation bị bỏ dở sẽ khiến duration/trace không phản ánh đúng thực tế.

### 3.1. Cardinality: điều kiện để metrics sử dụng được

**Low cardinality** là tập giá trị có số lượng hữu hạn, ổn định. Ví dụ tốt: `method=GET`, `status=200`, `uri=/api/items/{id}` hoặc `operation=read`. Các nhãn này phù hợp làm chiều phân loại metric.

**High cardinality** là tập giá trị có thể tăng theo số request, ví dụ `itemId=42`, `userId=abc123`, URL chứa ID, request ID. Đưa chúng vào **metric tag** có thể gây bùng nổ số time series, tăng bộ nhớ, chi phí lưu và rủi ro quá tải. Micrometer Observation phân biệt:

| Loại key-value | Metric | Trace | Ví dụ |
| --- | --- | --- | --- |
| `lowCardinalityKeyValue` | Có thể tham gia tag/dimension | Có thể tham gia span attribute | `operation=read` |
| `highCardinalityKeyValue` | Không tự trở thành meter tag | Có thể tham gia span attribute | `itemId=42` nếu được phép lưu |
| Log field | Không tự trở thành metric tag | Không tự xuất hiện trong trace | Sự kiện nghiệp vụ và stack trace |

Không có nghĩa high-cardinality trace tag luôn an toàn: tracing backend cũng có chi phí và chính sách bảo mật. Giá trị cá nhân/secret có thể bị lộ qua span; phải có nguyên tắc lọc/redact tương tự log.

## 4. Quan sát HTTP server tự động trong Spring MVC

Spring Framework hỗ trợ `ServerHttpObservationFilter` (Servlet filter) để tạo server observation cho request/response. Trong ứng dụng Spring Framework thuần, cần đăng ký filter cùng `ObservationRegistry` hữu hiệu. Spring Boot Actuator thường tự cấu hình các thành phần này khi dependencies và tính năng observability tương ứng hiện diện.

Một số nhãn thường gặp theo convention mặc định Spring Framework 6.1:

| Nhãn | Nội dung | Vì sao hữu ích |
| --- | --- | --- |
| `method` | HTTP method như GET/POST | So sánh traffic và latency theo method |
| `uri` | **URI pattern** của handler nếu xác định được | Tổng hợp nhiều `/api/items/42`, `/api/items/43` thành một tuyến |
| `status` | HTTP status response, ví dụ 200/404/500 | Phân biệt thành công và thất bại ở mức HTTP |
| `outcome` | Nhóm kết quả exchange | Theo dõi các nhóm trạng thái |
| `exception` | Exception quan sát được hoặc `none` | Tìm sự cố chưa được xử lý ở phạm vi observation |

Số/loại nhãn và tên có thể khác giữa phiên bản/convention/backend. Đặc biệt, **status 500 không đồng nghĩa tự động có `exception=SomeException`**. Khi `@ExceptionHandler` xử lý exception và chuyển thành response bình thường, exception có thể không thoát qua Servlet observation filter để được ghi là lỗi. Phân biệt **HTTP response status**, **lỗi nghiệp vụ**, và **throwable được công cụ instrumentation quan sát**.

Request không map được hoặc redirect có thể được gán các giá trị URI dự phòng như `NOT_FOUND`, `REDIRECTION` hoặc `UNKNOWN` tùy convention. Vì vậy truy vấn dựa trên route chỉ chính xác khi routing information có thể được instrumentation xác định.

Spring MVC **không tự bao gồm mọi thứ**: filter phía proxy, browser network, TCP handshake, thời gian browser render và một dependency bên ngoài chưa instrument sẽ không có span đúng nghĩa chỉ vì HTTP server observation tồn tại.

## 5. Dựng phép thử có thể kiểm chứng bằng Actuator

Với Boot 3.3, dependency Gradle điển hình:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

Đoạn này bổ sung vào project đã có plugin Spring Boot và version quản lý dependency. Với môi trường phát triển nội bộ có thể bật endpoint metrics:

```properties
spring.application.name=web-observe-demo
management.endpoints.web.exposure.include=health,info,metrics
```

Theo mặc định, endpoint Actuator được expose qua HTTP rất hạn chế; `metrics` cần cho phép lộ rõ trong cấu hình. URL thông thường:

```bash
curl -i http://localhost:8080/api/items/42
curl -s http://localhost:8080/actuator/metrics
curl -s http://localhost:8080/actuator/metrics/http.server.requests
```

Thao tác thứ nhất tạo request, thao tác thứ hai liệt kê tên meter đã xuất hiện; thao tác thứ ba (khi có dữ liệu/handler phù hợp) trả số đo/tags cho `http.server.requests`, chẳng hạn số lượng `COUNT` và thời gian tổng `TOTAL_TIME`. **Không cố định số đo** vì chúng thay đổi theo lưu lượng và thời điểm chạy. Để lọc một tag:

```bash
curl -G "http://localhost:8080/actuator/metrics/http.server.requests" \
  --data-urlencode "tag=method:GET" \
  --data-urlencode "tag=uri:/api/items/{id}"
```

Các lệnh trên giả định shell hỗ trợ ký tự xuống dòng `\` như Bash. Với Windows PowerShell, chạy lệnh trên một dòng hoặc dùng quy tắc xuống dòng của PowerShell. Response metrics endpoint là dữ liệu JSON của Actuator; tên meter sử dụng trong URI là tên gốc `http.server.requests`, không nhất thiết trùng tên xuất sang Prometheus.

**Giới hạn:** đây mới là phép kiểm tra instrument/metric tại application instance, không phải hệ thống giám sát tập trung. Muốn thu thập dài hạn, cần meter registry/exporter được cấu hình (Prometheus, OTLP, v.v.), backend lưu và dashboard/alert. Endpoint Actuator có thể lộ thông tin vận hành nhạy cảm, vì vậy cần giới hạn network và authorization khi expose ở môi trường thật.

### 5.1. Kiểm tra bằng integration test

Trong ứng dụng đơn giản ở trên, có thể xác minh request HTTP thật tạo HTTP server meter trong `MeterRegistry`. Tệp `src/test/java/com/example/observe/HttpObservationTest.java`:

```java
package com.example.observe;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class HttpObservationTest {
    @Autowired
    TestRestTemplate http;

    @Autowired
    MeterRegistry meters;

    @Test
    void realRequestRecordsHttpServerTimer() {
        var response = http.getForEntity("/api/items/42", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());

        Timer timer = meters.find("http.server.requests")
                .tags("method", "GET", "uri", "/api/items/{id}")
                .timer();

        assertNotNull(timer);
        assertTrue(timer.count() >= 1L);
    }
}
```

Test này giả định cấu hình Actuator/Micrometer HTTP server observation mặc định và ứng dụng chỉ có các endpoint đơn giản như trên. Nó chứng minh **request được gửi qua embedded HTTP server** và có meter ghi nhận ít nhất một lần; không chứng minh trace đã được gửi đến collector, log chứa MDC hoặc downstream propagation hoạt động. Với test chỉ gọi trực tiếp controller method, server observation thường không được tạo; với `MockMvc`, tùy cấu hình instrumentation/filter trong test context mà sự kiện có/không được ghi, vì vậy không được suy diễn tương đương live HTTP.

Kiểm tra chạy bằng:

```bash
./gradlew test --tests com.example.observe.HttpObservationTest
```

Trên Windows dùng `.\gradlew.bat test --tests com.example.observe.HttpObservationTest`. Đây là hướng dẫn thực hiện test cho ví dụ; không phải tuyên bố test đã chạy trong một project cụ thể.

## 6. Distributed tracing: trace/span và correlation ID

**Trace** đại diện một chuỗi hoạt động liên quan đến một request xuyên nhiều component. Mỗi **span** là một đoạn của chuỗi đó; thường có trace ID chung, span ID riêng và parent/child relationship:

```text
Trace ID: abc...
  [HTTP SERVER GET /api/items/{id}]        60 ms
      ├─ [catalog.load]                    15 ms
      └─ [HTTP CLIENT GET /inventory/{id}]  38 ms
            └─ [inventory: HTTP SERVER]    30 ms
```

Số thời gian và ID ở sơ đồ chỉ mang tính minh họa, không phải phép đo. Không cộng tổng các span trong sơ đồ để thay thế request duration; span có thể lồng nhau hoặc chạy song song. Để có trace phân tán, cả phía client và server của lời gọi liên dịch vụ cần instrument và **truyền trace context** đúng cách.

Micrometer Observation chỉ tự thành trace span khi có tracing handler/tracer đúng trên classpath và registry. Thêm `spring-boot-starter-actuator` chưa đủ để xem các trace từ backend. Với Boot 3.3, một cấu hình tracing OpenTelemetry xuất sang Zipkin có thể dùng:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-tracing-bridge-otel'
    runtimeOnly 'io.opentelemetry:opentelemetry-exporter-zipkin'
}
```

Ví dụ này là cấu hình tracer/exporter **bổ sung** cho ứng dụng web phần trước và cần có Zipkin collector chạy sẵn. Có thể chọn bridge Brave hoặc backend khác theo yêu cầu; không trộn nhiều bridge/exporter mà không có chủ đích. Với Boot 3.3, đường cấu hình Zipkin có dạng `management.zipkin.tracing.*`; không suy đoán nó giống các phiên bản Boot mới hơn.

Để mọi request mẫu có cơ hội được ghi khi học tracing tại môi trường local:

```properties
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
```

Spring Boot 3.3 mặc định sampling khoảng 10%; `1.0` tạo nhiều trace hơn, chỉ hợp lý cho môi trường kiểm chứng lưu lượng thấp. Ở production, chọn sampling theo lưu lượng, chi phí và nhu cầu điều tra. **Không thấy một trace trong backend chưa chắc là hệ thống không xử lý request**, vì có thể request không được sample, exporter/collector không hoạt động hoặc context không truyền qua dịch vụ khác.

### 6.1. Correlation ID trong log

Khi Micrometer Tracing được cấu hình, Spring Boot có khả năng đưa `traceId` và `spanId` vào MDC (Mapped Diagnostic Context) cho log trên luồng đang có span. Ví dụ hình thức log:

```text
2026-10-21 INFO [abc123-traceIdLikeValue-def456] Item lookup completed
```

Đây là **minh họa format**, không phải trace ID hợp lệ có thể tra cứu từ request thực. Trong cấu hình thực, hãy dùng trace ID/format do tracer và logging pattern tạo. Cùng một trace có thể gồm nhiều span ID; hai dòng ở các service khác nhau cùng `traceId` không nhất thiết có cùng `spanId`.

Đối với điều tra, cách làm hiệu quả là lấy `traceId` từ trace backend rồi tìm tất cả log cùng ID trong khoảng thời gian tương ứng. Nếu chỉ thêm một random `requestId` độc lập trên từng service, hệ thống phải có cơ chế truyền ID đó; nó **không tự** tạo quan hệ parent/child hay duration như distributed tracing.

### 6.2. Truyền context khi gọi dịch vụ khác

Spring Boot auto-configure observation/tracing cho một số HTTP client builder. Ví dụ khi có một backend khác:

```java
// Đoạn minh họa bên trong một @Service có RestClient được inject.
RestClient client = builder.baseUrl("http://localhost:8081").build();
String response = client.get()
        .uri("/inventory/{id}", 42)
        .retrieve()
        .body(String.class);
```

Ở đây `builder` là `RestClient.Builder` **do Spring Boot cung cấp** qua dependency injection. Tracing infrastructure có thể tự truyền W3C trace context hoặc propagation format được cấu hình. Nếu bạn tự gọi `RestClient.create()` hoặc `new RestTemplate()` mà bỏ qua builder auto-configured và không gắn observation registry/interceptor tương ứng, propagation tự động có thể bị mất. Phải kiểm tra trace phía downstream, không chỉ log service nguồn.

Đối với `@Async`, task executor, thread pool, virtual thread hoặc reactive pipeline, context/thread-local/MDC **không được bảo đảm** tự đi theo mọi bước chuyển luồng. Cần dùng cơ chế context propagation được hỗ trợ cho version/mô hình thực thi đang dùng và test qua ranh giới thread. Việc tự copy MDC mà quên clear/restore còn có thể làm “dính” ID từ request này sang request khác.

## 7. Theo dõi lỗi và response một cách đáng tin

Một server observation có thể ghi status HTTP 4xx/5xx, nhưng trạng thái `error` trong trace/tag còn phụ thuộc exception có đi qua tầng instrument hay đã bị xử lý. Ví dụ controller dùng `@ExceptionHandler` đổi exception thành response 404; công cụ quan sát ở Servlet filter có thể thấy status 404 mà **không thấy exception thoát ra**.

Vì vậy khi tìm lỗi cần phối hợp:

1. **Metric theo HTTP status/outcome:** endpoint nào tăng 5xx hoặc duration tăng?
2. **Application log ở nơi xử lý exception:** có đủ error code và stack trace theo chính sách bảo mật không?
3. **Trace span:** request đi đến dịch vụ nào, có span lỗi, timeout hoặc latency bất thường không?
4. **Client-visible response:** status/body/header thực sự trả cho người dùng có khớp mong đợi không?

Nếu phải đánh dấu exception đã xử lý là lỗi trong observation, Spring Framework cung cấp khả năng tương tác `ServerRequestObservationContext`, nhưng hãy cấu hình nhất quán, tránh tự gán mọi response 4xx thành “unexpected exception”. Đối với hệ thống thực tế, phân biệt lỗi validate do client, lỗi business kỳ vọng và lỗi hạ tầng.

### 7.1. Log request và response body có nên dùng không?

Ghi toàn bộ body có vẻ giúp debug nhưng thường tạo ba nhóm vấn đề:

- **Bảo mật/quyền riêng tư:** password, token, thông tin thanh toán, PII có thể bị lưu trong log hoặc trace.
- **Hiệu năng:** body lớn/streaming/multipart khiến việc đệm nội dung tốn RAM, I/O và độ trễ.
- **Ngữ nghĩa Servlet:** request input stream chỉ đọc được theo lifecycle; wrapping/đọc sai thời điểm có thể làm hỏng xử lý downstream hoặc không chứa toàn bộ response.

Nếu thật sự cần một số field phục vụ điều tra, hãy log structured fields được chọn lọc, giới hạn độ dài và redact trước khi xuất. Với API audit, cần định nghĩa một cơ chế audit riêng có policy, retention và phân quyền, thay vì dùng log HTTP debug làm nguồn dữ liệu chính thức.

## 8. Kiểm thử từng lớp thay vì nhìn một lần chạy

| Mục tiêu | Cách kiểm tra | Bằng chứng thu được | Điều chưa chứng minh |
| --- | --- | --- | --- |
| Business log đúng nhánh | Unit test với appender/test logger hoặc integration test | Một event được ghi khi branch chạy | MDC, tracer hay HTTP transport |
| MVC response đúng | `MockMvc` với context/filter phù hợp | Status, headers, JSON/view và routing | Socket thật hoặc collector |
| Metric HTTP | Live HTTP `RANDOM_PORT` + `MeterRegistry` | Timer/count và tag route/status sau request | Span đã export |
| Tracing | Chạy tracer/collector, gửi request, tra trace | Server span, trace ID và liên kết span | Mọi request đều được sample |
| Propagation | App A gọi app B qua instrumented client | Trace ID chung, quan hệ span xuyên service | Không tự xác thực mọi network hop |
| Vận hành production | Dashboard/alert + kiểm tra policy | Xu hướng, cảnh báo, giới hạn retention/access | Không thay thế test logic chức năng |

Đối với metric latency, đừng dùng một lần gọi nhanh để khẳng định performance của hệ thống. Cần phân biệt cold start/warmup, phân phối thời gian, traffic mix và những dependency bị mock. Histogram/percentile phụ thuộc cấu hình registry và backend; hãy cấu hình rõ trước khi làm SLO dựa trên p95/p99.

## 9. Các lỗi phổ biến và cách chẩn đoán

**Có log nhưng không có trace ID:** kiểm tra đã thêm tracing bridge chưa, request có active span không, logging pattern có giữ MDC không và có chuyển thread ngoài context không. `spring-boot-starter-actuator` riêng lẻ không đảm bảo log correlation dựa trên tracing.

**Có metric nhưng không thấy trace:** có thể chưa có tracer/exporter, sampling loại request, collector sai endpoint hoặc exporter không kết nối. Metrics và tracing có các pipeline khác nhau mặc dù được tạo từ cùng Observation.

**Không thấy `http.server.requests`:** kiểm tra application thực sự có request sau khi khởi động, observation instrumentation/registry có được cấu hình và enable không, endpoint `metrics` đã expose chưa, và tên meter đúng theo version/convention không. Với Spring Framework không dùng Boot, cần đăng ký `ServerHttpObservationFilter`.

**Metric có quá nhiều chuỗi khác nhau:** kiểm tra tag có raw path, user ID, request ID hoặc query value không; chuyển sang route template/nhãn low-cardinality, giữ thông tin request cụ thể trong log/trace có kiểm soát.

**Trace thiếu downstream service:** dùng HTTP client builder được instrument; kiểm tra context propagation header, sampler của từng service và việc context không bị mất khi chuyển thread.

**Status 500 mà exception tag `none`:** có thể lỗi đã được `@ExceptionHandler` xử lý hoặc filter không nhìn thấy throwable. So status và log handler, đừng quy kết instrument hỏng từ một tag duy nhất.

**Timer thấp nhưng người dùng vẫn chậm:** HTTP server timer thường không bao gồm DNS, TLS negotiation ở client, proxy queue, truyền qua mạng và browser rendering. Cần ghép metric ở client, proxy và máy chủ.

**Quan sát gây thêm tải:** bật 100% tracing/DEBUG hoặc log full body dưới tải lớn có thể ảnh hưởng latency và chi phí. Thiết kế sampling, lọc tag, log level và retention phù hợp; đo tác động trước khi áp dụng đại trà.

## 10. Checklist thực hành

Khi triển khai observability cho Spring MVC, thực hiện theo chuỗi:

1. Xác định endpoint/luồng cần điều tra, status và latency kỳ vọng.
2. Bật Actuator/Observation có kiểm soát; tạo một request rồi xác minh meter `http.server.requests` được cập nhật.
3. Tách log nghiệp vụ có giá trị khỏi access log; không log secret/body không cần thiết.
4. Nếu cần trace, bổ sung một tracer bridge và exporter phù hợp; kiểm tra trace ID trong log và server span ở collector.
5. Dùng HTTP client builder đã được instrument để kiểm chứng một call qua service thứ hai với trace ID chung.
6. Giữ nhãn metric low-cardinality, chọn đúng URI template; cấu hình alert dựa trên status, throughput và latency có ý nghĩa.
7. Kiểm tra sampling, context propagation qua async, exception handling và mức truy cập các endpoint quan sát.

Một hệ thống “quan sát được” khi từ triệu chứng ban đầu như “tỷ lệ 5xx tăng” có thể đi từ **metric** đến request/trace liên quan, rồi đến **log hoặc span** có bằng chứng giải thích. Đó là tiêu chí sử dụng, không phải chỉ có ba thư viện trong danh sách dependency.

### Tài liệu tham khảo

- Spring Framework 6.1 — Observability Support: https://docs.spring.io/spring-framework/reference/6.1/integration/observability.html
- Spring Boot 3.3 — Observability: https://docs.spring.io/spring-boot/3.3/reference/actuator/observability.html
- Spring Boot 3.3 — Metrics và Metrics Endpoint: https://docs.spring.io/spring-boot/3.3/reference/actuator/metrics.html
- Spring Boot 3.3 — Tracing và Log Correlation: https://docs.spring.io/spring-boot/3.3/reference/actuator/tracing.html
- Micrometer — Observation Documentation: https://docs.micrometer.io/micrometer/reference/observation/introduction.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="operations-practice-003">Đọc lỗi 404, 405, 406, 415, validation và ngoại lệ bất đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc lỗi 404, 405, 406, 415, validation và ngoại lệ bất đồng bộ trong Spring Web

## 1. Mục tiêu: chẩn đoán từ HTTP request tới nơi Spring xử lý

Khi một API Spring MVC trả về lỗi, **HTTP status chỉ là triệu chứng**. Để tìm đúng nguyên nhân cần xem request đã đi tới bước nào: chọn handler, khớp method, kiểm tra media type, deserialize/bind tham số, validation, gọi controller, xử lý bất đồng bộ, hay ghi response.

Bài này sử dụng **Spring MVC / Servlet stack**, Java 17+, Spring Framework 6.x (ví dụ Spring Boot 3.x). Với Spring WebFlux, khái niệm HTTP tương tự nhưng exception classes và execution model có khác biệt được nêu cuối bài.

Một quy trình điều tra hữu ích:

1. Ghi nhận **method + URI + query string** mà client thật sự gửi; phân biệt endpoint của Spring với URL public sau proxy/context-path.
2. Ghi lại **`Content-Type`** (request body thuộc định dạng nào), **`Accept`** (client muốn nhận định dạng nào), `Origin` nếu có CORS, và chính xác body được gửi.
3. Xem HTTP status, response headers (`Allow`, `Accept`, `Content-Type`), response body/`ProblemDetail` và request/correlation ID.
4. Đối chiếu các điều kiện `@RequestMapping`/`@GetMapping`/`@PostMapping`: path, HTTP method, `params`, `headers`, `consumes` và `produces`.
5. Nếu request đã tới controller, xem lỗi chuyển đổi dữ liệu, Bean Validation, nghiệp vụ và exception resolver; nếu bất đồng bộ, xem cả **async dispatch**, timeout và exception phát sinh ở worker thread hoặc publisher.

Không đoán “404 là sai controller” hay “400 là validation” trước khi xác minh vị trí lỗi. Một vấn đề có thể bị thay đổi biểu hiện bởi Spring Security, filter, servlet container, proxy hoặc một `@ControllerAdvice` custom.

## 2. Sơ đồ đường đi request và bảng phân biệt nhanh

~~~text
HTTP client
  |
  v
Reverse proxy / Servlet filters / Spring Security
  |
  v
DispatcherServlet + HandlerMapping
  |-- không tìm handler/tài nguyên ----------------> 404
  |-- path đúng, HTTP method không phù hợp -------> 405
  |-- @RequestMapping consumes không khớp --------> 415
  |-- @RequestMapping produces không khớp --------> 406
  |
  v
HandlerAdapter + argument resolvers + converters
  |-- body JSON hỏng, thiếu tham số, type mismatch -> thường 400
  |-- @Valid, method validation thất bại ----------> thường 400
  |
  v
Controller / service
  |-- business exception -------------------------> advice/resolver
  |-- Callable / DeferredResult / Mono
  |      |-- deferred exception -------------------> async dispatch -> advice
  |      |-- timeout ------------------------------> timeout handling
  |
  v
ReturnValueHandlers + HttpMessageConverter
  |-- không tìm converter ghi theo Accept --------> 406 / write failure
  v
HTTP response
~~~

Đây là sơ đồ thường gặp, **không phải bảo đảm rằng exception luôn rơi đúng một loại**: thứ tự kiểm tra mapping có thể phụ thuộc các điều kiện khác và MVC configuration. Ví dụ lỗi serialization cuối response có thể gây `HttpMessageNotWritableException`, thường là lỗi server chứ không mặc định là 406.

| Status | Tình huống thường gặp | Exception/đầu mối trong MVC | Điều cần nhìn ngay |
| --- | --- | --- | --- |
| `404 Not Found` | Không có handler cho path; static resource không tồn tại; nghiệp vụ tự trả 404 | `NoHandlerFoundException` hoặc `NoResourceFoundException` khi resolver/handler tương ứng ném, hoặc ứng dụng chủ động trả 404 | URI sau rewrite, context path, mapping, static resource, security, custom advice |
| `405 Method Not Allowed` | Path có mapping nhưng HTTP method không được chấp nhận | `HttpRequestMethodNotSupportedException` | Method thực, `Allow` response header, `@GetMapping`/`@PostMapping` |
| `406 Not Acceptable` | Không thể cung cấp representation phù hợp `Accept`/`produces` | `HttpMediaTypeNotAcceptableException` | `Accept`, `produces`, converters, nội dung response |
| `415 Unsupported Media Type` | Request body `Content-Type` không được mapping/converter chấp nhận | `HttpMediaTypeNotSupportedException` | `Content-Type`, `consumes`, converter, charset |
| `400 Bad Request` | JSON sai cú pháp, thiếu field/param, conversion hoặc Bean Validation thất bại | `HttpMessageNotReadableException`, `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `MethodArgumentTypeMismatchException`... | Body và constraint thực tế, binding/field errors |
| `500/503/other` | Lỗi nghiệp vụ/infra, exception async, timeout | Exception từ `Callable`, `DeferredResult.setErrorResult(...)`, `AsyncRequestTimeoutException`... | Exception root cause, async dispatch, timeout, response đã commit hay chưa |

Không có ánh xạ duy nhất **“async exception = HTTP 500”**. Một `@ExceptionHandler` có thể chuyển thành 422/503; timeout MVC mặc định thường 503 nếu response chưa commit; một lỗi ứng dụng chưa xử lý có thể thành 500. Xem đúng cấu hình và mã đã chạy.

## 3. 404: phân biệt không có handler với resource và business not-found

Một endpoint mẫu:

~~~java
@RestController
@RequestMapping("/api/books")
class BookController {
    @GetMapping("/{id}")
    Book find(@PathVariable long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));
    }
}
~~~

Đoạn trên minh họa, cần cung cấp `repository` và các lớp nghiệp vụ. Có ít nhất ba kiểu 404 khác nhau:

- `GET /api/boks/42` (gõ sai `books`): request có thể không khớp controller nào; Spring trả 404 từ quá trình handler mapping/static-resource handling.
- `GET /api/books/42`: mapping đúng, nhưng service phát `BookNotFoundException` và advice chuyển thành 404.
- `GET /assets/logo.png`: nếu static handler có mapping nhưng file không tồn tại, có thể nhận `NoResourceFoundException` (Spring 6.1+) rồi 404.

`NoHandlerFoundException` **không xuất hiện trong mọi lần 404**. `DispatcherServlet` có thể gửi 404 trực tiếp, và default static resource mapping của Spring Boot có thể nhận path trước rồi báo `NoResourceFoundException`. Không xây `@ExceptionHandler(NoHandlerFoundException.class)` với giả định nó sẽ bắt tất cả 404 của ứng dụng.

Lệnh thử:

~~~bash
curl -i http://localhost:8080/api/boks/42
~~~

Khi kiểm tra 404, hãy xác nhận controller được scan, `@RequestMapping` ở class có prefix nào, proxy có cắt prefix không, trailing slash có được match hay không, API version có đúng không và đường dẫn có bị Spring Security hoặc gateway sửa. **404 từ gateway** không nhất thiết đi qua `DispatcherServlet` của ứng dụng.

Nếu yêu cầu là “không tìm thấy bản ghi” thì trả 404 từ logic nghiệp vụ là hợp lý. Nhưng không nên biến exception database/timeout thành 404 chỉ để client khỏi nhận 500: việc đó giấu nguyên nhân vận hành thực sự.

## 4. 405: đúng URI nhưng sai HTTP method

Ví dụ endpoint chỉ cho POST:

~~~java
@PostMapping(path = "/api/items")
ResponseEntity<Void> create(@RequestBody CreateItem command) {
    return ResponseEntity.noContent().build();
}
~~~

Request này có thể gây 405:

~~~http
GET /api/items HTTP/1.1
Accept: application/json
~~~

Spring có thể ném `HttpRequestMethodNotSupportedException` và ghi header `Allow` để cho biết method được chấp nhận, ví dụ `POST`. `Allow` là đầu mối quan trọng khi đọc lỗi 405.

Điểm cần kiểm tra:

- Frontend gửi `GET` hay `POST`? `fetch` mặc định là GET nếu không chỉ định `method`.
- HTML form hỗ trợ một số method giới hạn; có dùng `HiddenHttpMethodFilter` hoặc endpoint REST thực sự chờ PUT/PATCH/DELETE không?
- Reverse proxy có chuyển method hay preflight `OPTIONS` bị chặn trước khi tới endpoint không?
- `HEAD` thường được hỗ trợ dựa trên GET mapping; `OPTIONS` có thể được Spring xử lý tự động, vì vậy đừng gộp mọi method khác GET vào 405 khi kiểm thử.
- Nếu controller có `params`/`headers` conditions, request có thể thất bại theo điều kiện khác, không chỉ method.

Thử tương phản:

~~~bash
curl -i -X GET http://localhost:8080/api/items
curl -i -X POST http://localhost:8080/api/items -H "Content-Type: application/json" -d '{"name":"Pen"}'
~~~

Đọc cả `Allow`, không chỉ status. Đừng sửa bằng cách thêm `@GetMapping` cho thao tác ghi dữ liệu; sửa client hoặc API contract để method phù hợp.

## 5. 406: Accept yêu cầu dạng server không tạo được

`Accept` diễn tả các loại media type mà client có thể nhận. Controller:

~~~java
@GetMapping(
    path = "/api/items/1",
    produces = MediaType.APPLICATION_JSON_VALUE
)
Item get() {
    return new Item(1L, "Pen");
}
~~~

Nếu client gửi:

~~~http
GET /api/items/1 HTTP/1.1
Accept: application/xml
~~~

Trong khi endpoint chỉ sản xuất JSON, Spring có thể báo `406 Not Acceptable` với `HttpMediaTypeNotAcceptableException`. Đổi `Accept: application/json` thường giải quyết được khi endpoint thực sự trả JSON.

**Đừng nhầm `Accept` với `Content-Type`:**

- `Accept` mô tả **response representation** client mong muốn.
- `Content-Type` mô tả **request body** client đang gửi.
- `produces` ràng buộc khả năng **trả dữ liệu**.
- `consumes` ràng buộc khả năng **nhận body**.

Các tình huống 406 thường gặp:

1. Browser hoặc API client gửi `Accept: application/xml` nhưng server chỉ tạo JSON.
2. Endpoint `produces="application/json"` trong khi client giới hạn loại media không tương thích.
3. Response DTO/converter hoặc content negotiation được cấu hình không phù hợp; cần kiểm tra exception thực tế, vì lỗi converter ghi response có thể biểu hiện 500 thay vì 406.
4. Handler yêu cầu loại media cụ thể bằng `produces`, nhưng browser/WebClient gửi header khác.

Thử:

~~~bash
curl -i http://localhost:8080/api/items/1 -H "Accept: application/xml"
curl -i http://localhost:8080/api/items/1 -H "Accept: application/json"
~~~

Không nên đổi `produces` thành `*/*` chỉ để làm 406 biến mất nếu ứng dụng vẫn chỉ serialize được JSON. Khả năng sản xuất representation phải là contract rõ ràng.

## 6. 415: Content-Type hoặc định dạng body không được hỗ trợ

Endpoint:

~~~java
@PostMapping(
    path = "/api/items",
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
)
Item create(@RequestBody CreateItem input) {
    return service.create(input);
}
~~~

Nếu client gửi dữ liệu dạng text nhưng endpoint chỉ nhận JSON:

~~~http
POST /api/items HTTP/1.1
Content-Type: text/plain
Accept: application/json

{"name":"Pen"}
~~~

thì Spring thường trả `415 Unsupported Media Type` với `HttpMediaTypeNotSupportedException`. **Body nhìn giống JSON không đủ**: request vẫn khai báo `text/plain`.

Với `Content-Type: application/json` nhưng JSON sai cú pháp:

~~~http
POST /api/items HTTP/1.1
Content-Type: application/json

{"name":
~~~

thường là **400** do `HttpMessageNotReadableException`, không phải 415 vì media type đã được chấp nhận và lỗi xảy ra ở bước parse.

Các lưu ý:

- `application/json`, `application/x-www-form-urlencoded` và `multipart/form-data` không thay thế tùy tiện.
- Multipart cần tên part phù hợp, parser, `@RequestPart`/`@RequestParam` và media type cho JSON part. Thiếu part hay parse lỗi có thể tạo lỗi 400; loại media của toàn request không khớp `consumes` thường là 415.
- Một charset hoặc custom vendor media type như `application/vnd.acme+json` có thể cần kiểm tra converter/compatibility; không đoán rằng Spring luôn chấp nhận.
- Nếu HTTP method/path đã sai, Spring có thể thất bại trước khi kiểm tra body media type. Khi debug, giữ các thành phần khác đúng và thay **một header** để định vị vấn đề.

## 7. Validation: nhiều loại lỗi cùng có thể thành 400

Validation thuộc nhiều giai đoạn khác nhau, nên cần phân biệt exception và vị trí xử lý.

### 7.1. @Valid @RequestBody và MethodArgumentNotValidException

~~~java
public record NewItem(
        @NotBlank String name,
        @Size(max = 80) String description) {}

@PostMapping("/api/items")
Item create(@Valid @RequestBody NewItem input) {
    return service.create(input);
}
~~~

Khi request JSON hợp lệ về cú pháp nhưng `name` rỗng hoặc `description` quá dài, Spring MVC thường ném `MethodArgumentNotValidException` nếu tham số command object được validate theo cấu hình hỗ trợ. Với Spring Boot 3.x, cần dependency Bean Validation tương thích (thường `spring-boot-starter-validation`). Import tương ứng là `jakarta.validation.Valid` và `jakarta.validation.constraints.*`, không phải `javax.validation.*` trên Boot 3.

Nếu tham số dùng `BindingResult`/`Errors` ngay sau argument để tự xử lý, controller có thể nhận lỗi thay vì exception tự động; test phải dựa vào controller signature thực tế.

### 7.2. Validation tham số method: HandlerMethodValidationException

Một endpoint kiểm tra trực tiếp query parameter:

~~~java
@GetMapping("/api/items")
List<Item> list(@RequestParam @Min(1) int page) {
    return service.findPage(page);
}
~~~

Với Spring Framework 6.1+, MVC có hỗ trợ method validation tích hợp; nếu signature cần validation trên parameter như `@Min` và request dùng `page=0`, có thể nhận `HandlerMethodValidationException` (thông thường 400 cho lỗi input, 500 cho lỗi validation ở return value). Cơ chế thực tế phụ thuộc cách cấu hình: class-level `@Validated` có thể kích hoạt AOP method validation khác, nên cần kiểm tra exception của app thay vì cứng hóa một class cho mọi phiên bản.

### 7.3. Conversion/parse khác validation

- `GET /api/items/not-a-number` với `@PathVariable long id`: type conversion thất bại, có thể thành `MethodArgumentTypeMismatchException`/`TypeMismatchException` → 400.
- Thiếu `@RequestParam(required=true)`: `MissingServletRequestParameterException` → thường 400.
- JSON sai cú pháp hay body không decode được: `HttpMessageNotReadableException` → thường 400.
- Thiếu `@RequestBody(required=true)`: cũng thường là 400 ở bước đọc body.
- Constraint của entity/database (unique key, foreign key) là vấn đề tầng lưu trữ; không tự động trở thành một lỗi validation HTTP 400 chuẩn nếu không có mapping nghiệp vụ phù hợp.

Muốn trả lỗi có thể dùng chung, hãy chuẩn hóa `ProblemDetail`, mã lỗi và danh sách field violations, nhưng **không trả nội dung nhạy cảm** từ exception nội bộ. Trả field name, code và message được duyệt thay vì stack trace.

Ví dụ payload lỗi ứng dụng tự thiết kế:

~~~json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "code": "INVALID_INPUT",
  "errors": [
    {"field": "name", "message": "must not be blank"}
  ]
}
~~~

Đây là **mẫu contract tùy biến**, không phải cam kết rằng mọi project Spring tự trả đúng các trường này. Spring `ProblemDetail` hỗ trợ thuộc tính mở rộng, nhưng format phụ thuộc cấu hình error handling của ứng dụng.

## 8. Exception handling trong Spring MVC: ai xử lý?

Một exception thường được đưa tới chuỗi `HandlerExceptionResolver`, trong đó có:

1. `ExceptionHandlerExceptionResolver` tìm `@ExceptionHandler` trong controller hoặc `@ControllerAdvice`.
2. `ResponseStatusExceptionResolver` xử lý `@ResponseStatus`/`ResponseStatusException` và một số nguyên nhân tương ứng.
3. `DefaultHandlerExceptionResolver` chuyển các exception tiêu chuẩn Spring MVC thành status thích hợp.

`ResponseEntityExceptionHandler` là class nền hữu ích để tập trung xử lý các exception MVC đã biết; phiên bản Spring mới có hỗ trợ `ProblemDetail` theo RFC 9457 (trước đây thường gọi RFC 7807). Có thể dùng `@RestControllerAdvice` để tạo response JSON cho exception nghiệp vụ.

Ví dụ:

~~~java
@RestControllerAdvice
class ApiExceptionAdvice {
    @ExceptionHandler(DependencyUnavailable.class)
    ProblemDetail dependencyDown(DependencyUnavailable ex) {
        ProblemDetail p = ProblemDetail.forStatus(
                HttpStatus.SERVICE_UNAVAILABLE);
        p.setTitle("Dependency unavailable");
        p.setDetail("Please retry later");
        p.setProperty("code", "UPSTREAM_UNAVAILABLE");
        return p;
    }
}
~~~

Đây là snippet tích hợp: cần định nghĩa `DependencyUnavailable` và import `ProblemDetail`, `HttpStatus`, `ExceptionHandler`. Bản demo đầy đủ ở phần 10.

Trong Spring Boot 3.x, `spring.mvc.problemdetails.enabled=true` có thể bật xử lý `ProblemDetail` cho exception của MVC bằng auto-configuration; một `@ControllerAdvice` riêng phải cân nhắc **thứ tự ưu tiên** và lớp kế thừa `ResponseEntityExceptionHandler` để tránh handler chồng lấp. Không tạo một `@ExceptionHandler(Exception.class)` nuốt mọi exception và luôn trả 200/400: cách đó làm sai giao thức và che lỗi server.

### 404/405/406/415 với advice

Không nhất thiết tự viết handler cho tất cả. Có thể để Spring xử lý built-in exceptions và chỉ override khi cần contract lỗi cụ thể. Nếu override `handleHttpRequestMethodNotSupported` mà bỏ mất header `Allow`, client sẽ mất thông tin cần thiết. Tương tự, các lỗi media type có thể có headers liên quan; hãy giữ headers Spring cung cấp khi customize.

## 9. Ngoại lệ bất đồng bộ: request có thể kết thúc ở dispatch khác

Spring MVC sử dụng Servlet async với `Callable<T>`, `DeferredResult<T>`, `WebAsyncTask<T>` và adapter cho một số reactive return types. Lần đầu vào controller có thể trả về đối tượng đại diện cho công việc chưa hoàn tất; `DispatcherServlet` nhả servlet thread khi request ở trạng thái async. Khi có kết quả/lỗi, Servlet container thực hiện **ASYNC dispatch** để MVC hoàn tất response.

### 9.1. Callable: exception ném ở worker

~~~java
@GetMapping("/api/async/call")
Callable<String> call() {
    return () -> {
        // Thực thi sau trong AsyncTaskExecutor.
        throw new DependencyUnavailable();
    };
}
~~~

Exception ở bên trong `Callable` **không ném trực tiếp trong stack ban đầu của controller**. Spring MVC nhận kết quả exception và đưa qua cơ chế exception handling sau async dispatch. Một `@ExceptionHandler(DependencyUnavailable.class)` có thể tạo response phù hợp.

Tư duy đúng:

~~~text
GET /api/async/call
  -> DispatcherServlet ban đầu
  -> controller trả Callable
  -> startAsync; servlet request thread được giải phóng
  -> AsyncTaskExecutor chạy Callable
  -> Callable ném DependencyUnavailable
  -> asyncDispatch về MVC
  -> @RestControllerAdvice chuyển thành HTTP 503 ProblemDetail
~~~

### 9.2. DeferredResult: setErrorResult để chuyển lỗi vào MVC

~~~java
@GetMapping("/api/async/pending")
DeferredResult<String> pending() {
    DeferredResult<String> result = new DeferredResult<>(10_000L);
    taskRegistry.register(result);
    return result;
}

// Khi công việc bên ngoài thất bại:
result.setErrorResult(new DependencyUnavailable());
~~~

`taskRegistry` là thành phần minh họa. `setErrorResult(exception)` yêu cầu Spring hoàn tất async request bằng một error result và đưa exception qua resolver khi dispatch. **Đừng chỉ log lỗi ở callback rồi bỏ request trong trạng thái chưa hoàn tất**. Đồng thời cần hủy/dọn task/registry trong `onTimeout` và `onCompletion`.

### 9.3. Timeout khác lỗi business

Timeout có thể xảy ra ở nhiều tầng:

- `DeferredResult(timeoutMillis)` hoặc `WebAsyncTask`: giới hạn thời gian xử lý Servlet async request.
- `spring.mvc.async.request-timeout`: cấu hình timeout MVC async mặc định (nếu app dùng Boot có property tương ứng).
- HTTP client (WebClient/RestClient): connect/read/response timeout tới upstream, xảy ra độc lập với timeout MVC.
- Gateway/reverse proxy: có thể trả 504 trước khi Spring MVC kịp hoàn tất response.
- Reactive `Mono.timeout(...)` khi controller trả publisher: phát tín hiệu timeout của Reactor, có thể được map thành response khác.

Trong Spring MVC mặc định, `AsyncRequestTimeoutException` thường được xử lý thành **503 Service Unavailable** nếu response chưa commit. Custom `@ExceptionHandler` hoặc interceptor có thể biến thành response khác. Nếu client đã disconnect, stream đã gửi headers/body hoặc response đã commit, **không thể tùy tiện thay status/body** nữa.

Đừng cho rằng thấy 503 là chắc chắn async timeout: 503 cũng có thể do server tự báo bảo trì, quá tải hoặc app-specific mapping. Xác định exception class và thời gian thực thi.

### 9.4. Một số lỗi xử lý bất đồng bộ phổ biến

- **Không gọi `asyncDispatch` trong test:** `MockMvc` báo initial request đã bắt đầu async nhưng test không quan sát HTTP status cuối cùng.
- **Block ở event-loop:** nếu controller MVC trả `Mono` dùng WebClient, gọi `block()` trong Reactor event-loop có thể gây `IllegalStateException`. Phối hợp publisher bằng `flatMap`/`zip`, chỉ block ở ranh giới imperative trên thread được phép.
- **Đã timeout nhưng worker vẫn chạy:** timeout của HTTP request không nhất thiết hủy công việc bên ngoài hoặc rollback remote operation; cần cancellation/idempotency rõ ràng.
- **Mất MDC/trace ID:** request có thể qua thread/dispatch khác. Dùng tracing/context propagation được hỗ trợ, không mặc định `ThreadLocal` giữ nguyên mọi nơi.
- **Filter chỉ chạy REQUEST:** trong Servlet async, có thể cần mapping cho dispatcher type `ASYNC` để xử lý đúng tracing/security, theo cấu hình cụ thể.

## 10. Demo Spring Boot 3.x và kiểm thử MockMvc

Demo dùng Spring Boot 3.x, Java 17+, Spring MVC, JUnit 5 và Bean Validation. Các endpoint chủ động tạo điều kiện để quan sát từng status và một business exception xảy ra trong cả `Callable` lẫn `DeferredResult`.

Cấu trúc Maven:

~~~text
spring-web-errors/
├── pom.xml
├── src/main/java/demo/ErrorDemoApplication.java
├── src/main/java/demo/DiagnosticController.java
├── src/main/java/demo/ApiErrorAdvice.java
└── src/test/java/demo/DiagnosticControllerTest.java
~~~

### 10.1. pom.xml

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
    <artifactId>spring-web-errors</artifactId>
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
            <artifactId>spring-boot-starter-validation</artifactId>
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

### 10.2. src/main/java/demo/ErrorDemoApplication.java

~~~java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ErrorDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ErrorDemoApplication.class, args);
    }
}
~~~

### 10.3. src/main/java/demo/DiagnosticController.java

~~~java
package demo;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
public class DiagnosticController {
    private final AtomicReference<DeferredResult<Item>> pending =
            new AtomicReference<>();

    public record Item(long id, String name) {}
    public record NewItem(@NotBlank String name) {}

    @GetMapping(
            path = "/api/items/1",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Item getItem() {
        return new Item(1, "Pen");
    }

    @PostMapping(
            path = "/api/items",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Item create(@Valid @RequestBody NewItem input) {
        return new Item(2, input.name());
    }

    @GetMapping("/api/quantity")
    public String quantity(@RequestParam @Min(1) int amount) {
        return "amount=" + amount;
    }

    @GetMapping("/api/async/callable")
    public Callable<Item> fromCallable() {
        return () -> {
            throw new DependencyUnavailable();
        };
    }

    @GetMapping("/api/async/deferred")
    public DeferredResult<Item> fromDeferred() {
        DeferredResult<Item> result = new DeferredResult<>(10_000L);
        pending.set(result);
        result.onCompletion(() -> pending.compareAndSet(result, null));
        return result;
    }

    // Chỉ là hook có phạm vi package để demo test điều khiển sự kiện.
    void failPending() {
        DeferredResult<Item> current = pending.get();
        if (current == null) {
            throw new IllegalStateException("No pending request");
        }
        current.setErrorResult(new DependencyUnavailable());
    }
}

class DependencyUnavailable extends RuntimeException {}
~~~

Trong `fromDeferred()`, ứng dụng thật không nên giữ **duy nhất một pending request trong AtomicReference** nếu có nhiều client đồng thời; cần registry riêng theo request ID, cleanup khi hoàn tất/timeout và giới hạn số task. Cấu trúc demo chỉ để test một request tại một thời điểm.

### 10.4. src/main/java/demo/ApiErrorAdvice.java

~~~java
package demo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrorAdvice {
    @ExceptionHandler(DependencyUnavailable.class)
    public ProblemDetail dependencyUnavailable(DependencyUnavailable error) {
        ProblemDetail detail = ProblemDetail.forStatus(
                HttpStatus.SERVICE_UNAVAILABLE);
        detail.setTitle("Dependency unavailable");
        detail.setDetail("Please retry later");
        detail.setProperty("code", "UPSTREAM_UNAVAILABLE");
        return detail;
    }
}
~~~

Advice này chỉ xử lý **một exception nghiệp vụ**, để Spring MVC tự phát những status built-in như 405, 406, 415 và 400 do validation. Vì return `ProblemDetail` mang status 503, Spring MVC dùng status tương ứng cho response. Không đưa stack trace hoặc thông tin nội bộ vào body.

### 10.5. src/test/java/demo/DiagnosticControllerTest.java

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DiagnosticController.class)
class DiagnosticControllerTest {
    @Autowired MockMvc mvc;
    @Autowired DiagnosticController controller;

    @Test
    void missingRouteIs404() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
            .andExpect(status().isNotFound());
    }

    @Test
    void wrongHttpMethodIs405() throws Exception {
        mvc.perform(post("/api/items/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\"}"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string(
                    HttpHeaders.ALLOW, containsString("GET")));
    }

    @Test
    void unacceptableAcceptIs406() throws Exception {
        mvc.perform(get("/api/items/1")
                .accept(MediaType.APPLICATION_XML))
            .andExpect(status().isNotAcceptable());
    }

    @Test
    void unsupportedContentTypeIs415() throws Exception {
        mvc.perform(post("/api/items")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Pen\"}"))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void validRequestSucceeds() throws Exception {
        mvc.perform(post("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Pen\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(2))
            .andExpect(jsonPath("$.name").value("Pen"));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidBeanValidationIs400() throws Exception {
        mvc.perform(post("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidMethodParameterIs400() throws Exception {
        mvc.perform(get("/api/quantity")
                .param("amount", "0"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void callableErrorIsResolvedAfterAsyncDispatch() throws Exception {
        MvcResult pending = mvc.perform(get("/api/async/callable"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(pending))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"))
            .andExpect(jsonPath("$.detail").value("Please retry later"));
    }

    @Test
    void deferredErrorIsResolvedAfterAsyncDispatch() throws Exception {
        MvcResult pending = mvc.perform(get("/api/async/deferred"))
                .andExpect(request().asyncStarted())
                .andReturn();

        controller.failPending();

        mvc.perform(asyncDispatch(pending))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"));
    }
}
~~~

`@WebMvcTest(DiagnosticController.class)` load MVC controller và các `@ControllerAdvice` trong slice nếu chúng được component scan vào web slice. Nếu project tùy biến component scanning, cần `@Import(ApiErrorAdvice.class)` hoặc cấu hình slice cụ thể để advice có mặt; test có thể xác minh bằng response 503 chứa `code`.

**Điều quan trọng nhất khi kiểm thử async:** `mvc.perform(get(...))` đầu tiên **chưa phải response cuối** khi method trả `Callable`/`DeferredResult`. Dùng `request().asyncStarted()`, lấy `MvcResult`, kích hoạt kết quả giả lập nếu cần, sau đó `asyncDispatch(...)` để assert HTTP status/body cuối cùng. Nếu thiếu bước thứ hai, test dễ cho kết luận sai về việc advice có hoạt động.

### 10.6. Chạy Maven và kiểm tra kết quả

Dùng JDK 17+ và Maven trong thư mục chứa `pom.xml`:

~~~bash
mvn test
~~~

Nếu các lớp nằm đúng package và dependency như trình bày, kỳ vọng Maven kết thúc `BUILD SUCCESS`, 10 bài test pass, không có test failure. Đây là **kỳ vọng từ mã minh họa**, không phải output đã được đo thực tế trong môi trường này. Nếu một test lỗi, đọc `target/surefire-reports` để xem exception và cấu hình thực tế; đừng chỉnh expected status mà không hiểu nguồn lỗi.

Chạy API thật:

~~~bash
mvn spring-boot:run
~~~

Thử nhanh:

~~~bash
curl -i http://localhost:8080/api/does-not-exist
curl -i -X POST http://localhost:8080/api/items/1 -H "Content-Type: application/json" -d '{"name":"X"}'
curl -i http://localhost:8080/api/items/1 -H "Accept: application/xml"
curl -i -X POST http://localhost:8080/api/items -H "Content-Type: text/plain" -d '{"name":"Pen"}'
curl -i -X POST http://localhost:8080/api/items -H "Content-Type: application/json" -d '{"name":""}'
~~~

Trong thứ tự trên, status kỳ vọng là **404, 405, 406, 415, 400**. Các endpoint async ném exception sẽ trả `503` do `ApiErrorAdvice`, nếu request hoàn tất trước timeout và response chưa bị commit.

Lệnh `curl` ví dụ dùng cú pháp CLI thường gặp của Bash; ở Windows PowerShell nên dùng `curl.exe` và quy tắc quote chuỗi JSON của shell đó.

## 11. Quan sát log đúng cách thay vì đoán theo status

Khi xử lý sự cố thực, thu thập các trường sau cho mỗi request:

| Nhóm | Ví dụ | Tại sao cần |
| --- | --- | --- |
| Request | method, URI, query, `Accept`, `Content-Type` | Phân biệt mapping/converter errors |
| Response | status, `Allow`, media type, duration | Chỉ ra 405/406/415 và độ trễ |
| Trace | request ID, trace ID, span ID | Liên kết gateway → MVC → upstream |
| Exception | class, root cause, stack trace ở server log | Phân biệt validation, async timeout, upstream failure |
| Async | dispatcher type `REQUEST`/`ASYNC`, timeout config, completion | Biết request đã chuyển thread/dispatch chưa |
| Backend | database, HTTP client status/latency, thread pool/queue | Biết lỗi thực sự nằm ở tầng nào |

Không log token, mật khẩu, nội dung riêng tư hoặc toàn bộ multipart file chỉ để debug 400/415. Khi gặp `HttpMessageNotReadableException`, log an toàn đủ để tái hiện schema/body mẫu đã ẩn dữ liệu nhạy cảm.

Một luồng điều tra cụ thể:

1. Nếu **404**, xác nhận gateway routing, controller scan, path variables và static mappings; so với đường dẫn từ request logs.
2. Nếu **405**, lấy `Allow` rồi xem method trong request thực, bao gồm request do browser preflight `OPTIONS` hay client gửi.
3. Nếu **406**, in `Accept`, `produces` và danh sách converter được app hỗ trợ.
4. Nếu **415**, in `Content-Type`, `consumes` và body shape; thử lại với header đúng.
5. Nếu **400 validation**, đọc field/type/constraint errors và phân biệt lỗi JSON parser.
6. Nếu **5xx/timeout async**, theo dõi initial dispatch, worker/publisher, async dispatch và advice; kiểm tra response đã commit, proxy timeout và external service.

Trong Spring Boot có thể dùng Actuator HTTP server metrics và tracing nếu đã cấu hình, kết hợp request correlation IDs. Đừng bật DEBUG/TRACE toàn bộ traffic dài hạn trên production khi chứa dữ liệu nhạy cảm.

## 12. Các ngộ nhận thường gặp

### 12.1. “404 nghĩa là @ExceptionHandler của tôi không bắt được business exception”

Có thể endpoint chưa được route đến controller, hoặc static resource handler phát 404. Phân biệt bằng trace, handler mapping và loại exception; không phải mọi 404 đều là business exception.

### 12.2. “405 là controller không tồn tại”

405 thường có nghĩa path được nhận diện nhưng method không phù hợp. Kiểm tra response `Allow` và phương thức request, rồi đối chiếu `@GetMapping`/`@PostMapping`.

### 12.3. “JSON body đúng thì không thể bị 415”

Media type do `Content-Type` khai báo quyết định bước `consumes`/converter. JSON text gắn `text/plain` vẫn có thể bị 415; JSON hỏng nhưng content type đúng thường ra 400.

### 12.4. “Accept: application/xml mà nhận 406 là do không có dữ liệu”

406 nói về response representation, không phải bản ghi bị thiếu. Khi muốn báo bản ghi không tồn tại, nghiệp vụ trả 404 độc lập với việc client chọn media type hợp lệ.

### 12.5. “Mọi 400 đều do @Valid”

Thiếu parameter, sai kiểu path variable, body JSON hỏng cũng là 400 phổ biến. Đọc exception class và test từng biên lỗi.

### 12.6. “Exception xảy ra trong CompletableFuture/Callable thì @ControllerAdvice không chạy”

Với các async return types được Spring MVC hỗ trợ và exception được **đưa vào lifecycle MVC** (ví dụ `Callable` ném hoặc `DeferredResult.setErrorResult(exception)`), request được async-dispatch và xử lý bằng exception resolvers bình thường. Nhưng exception bị nuốt trong một fire-and-forget thread/`@Async void` không tự đi vào response request đã kết thúc.

### 12.7. “Async request timeout nhất định là HTTP 504”

MVC mặc định thường trả 503 cho `AsyncRequestTimeoutException` khi chưa commit; 504 thường xuất hiện ở gateway/proxy hoặc mapping tùy biến. Xem tầng nào đã trả response.

### 12.8. “Test chỉ cần assert exception của controller method”

Với MVC, lỗi có thể xảy ra **trước khi method được gọi** (mappings, converter, binding) hoặc **sau initial dispatch** (async). `MockMvc` kiểm tra cả request pipeline và cần `asyncDispatch` cho kết quả bất đồng bộ.

## 13. Lưu ý với WebFlux và reactive errors

Trong Spring WebFlux, thay vì Servlet `DispatcherServlet` là reactive `DispatcherHandler` và các thành phần `WebFilter`/`WebExceptionHandler`. HTTP semantics vẫn là 404/405/406/415/400 nhưng exception classes thường thuộc package reactive/server khác, ví dụ `ResponseStatusException`, `ServerWebInputException` hoặc `WebExchangeBindException` cho binding/validation. Không áp dụng nguyên xi danh sách exception MVC lên WebFlux.

Với controller trả `Mono`/`Flux`:

~~~java
@GetMapping("/api/reactive/item")
Mono<Item> reactive() {
    return webClient.get()
            .uri("/upstream/item")
            .retrieve()
            .bodyToMono(Item.class)
            .onErrorMap(WebClientRequestException.class,
                    error -> new DependencyUnavailable());
}
~~~

Đây là snippet tích hợp (cần `webClient` và class `Item`/`DependencyUnavailable`). Nếu exception là **reactive error signal** từ publisher được Spring WebFlux subscribe trong request lifecycle, có thể được xử lý bằng exception handler; nếu ứng dụng tự gọi `subscribe()` rồi trả response 200 ngay, lỗi phát sinh ngoài lifecycle không tự đổi status đã gửi. Tương tự, không block Reactor event-loop khi chữa lỗi async; compose publishers bằng operators.

Dùng `WebTestClient` hoặc integration test reactive để khẳng định status/body thực tế, và phân biệt lỗi từ `WebClientResponseException` (upstream có HTTP response lỗi) với `WebClientRequestException` (request/network failure). Nếu muốn truyền nguyên status upstream sang client, phải có quyết định nghiệp vụ rõ ràng thay vì trả mặc định 500.

## 14. Checklist thực hành

- [ ] Ghi đúng method, URL, `Content-Type`, `Accept`, query, payload shape và request ID trước khi sửa code.
- [ ] Với 404: xác minh mapping, resources và business not-found riêng.
- [ ] Với 405: đọc `Allow`; kiểm tra method HTTP và mapping.
- [ ] Với 406: kiểm tra `Accept`, `produces` và message converter phía response.
- [ ] Với 415: kiểm tra `Content-Type`, `consumes` và converter đầu vào.
- [ ] Với 400: phân biệt parse/binding/validation; báo field errors có kiểm soát.
- [ ] Với `Callable`/`DeferredResult`: bảo đảm exception đi qua async result và dùng `asyncDispatch` trong MockMvc.
- [ ] Với timeout: xác minh timeout ở MVC, HTTP client hay gateway; xem response đã commit chưa.
- [ ] Với `@ControllerAdvice`: giữ status/headers chuẩn và kiểm tra conflict/thứ tự handlers, không nuốt mọi lỗi.
- [ ] Có tests âm tính/biên, request correlation, log an toàn và thống kê tỉ lệ lỗi theo nhóm.

**Nguyên tắc quan trọng:** xác định **request thất bại ở bước nào** trước khi đổi controller/advice. 404/405 là đầu mối routing, 406/415 là negotiation/converter, validation thường là 400, còn async exception cần lần dispatch tiếp theo và một chính sách xử lý lỗi minh bạch.

## 15. Tài liệu tham khảo chính thức

- Spring Framework — Mapping Requests: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html
- Spring Framework — REST Error Responses và `ProblemDetail`: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
- Spring Framework — MVC Asynchronous Requests: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- Spring Framework — Method Validation: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html
- Spring Framework — Exception Handling: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html
- Spring Framework — MockMvc: https://docs.spring.io/spring-framework/reference/testing/mockmvc.html
- Spring Framework — MockMvc Async Requests: https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/async-requests.html
- RFC 9110 — HTTP Semantics: https://www.rfc-editor.org/rfc/rfc9110.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="operations-practice-004">Chẩn đoán cạn luồng, nghẽn kết nối, thiếu bộ nhớ và tác vụ chặn event loop</a>

<details>
<summary>Xem chi tiết</summary>

# Chẩn đoán cạn luồng, nghẽn kết nối, thiếu bộ nhớ và tác vụ chặn event loop trong Spring Web

## 1. Tại sao bốn vấn đề này dễ bị nhầm lẫn?

Ứng dụng Spring có thể trả HTTP 503, 500, timeout hoặc phản hồi rất chậm dù CPU chưa cao. Nguyên nhân không nhất thiết ở controller vừa xuất hiện trong log. Request đi qua nhiều tài nguyên: luồng tiếp nhận, scheduler, hàng đợi, kết nối HTTP đến dịch vụ khác, JDBC connection, bộ nhớ heap, direct memory và các bước mã hóa/giải mã dữ liệu.

Bốn nhóm sự cố thường tạo ra triệu chứng tương tự:

1. **Cạn luồng (thread starvation):** các luồng cần để làm việc đã bận hoặc chờ quá lâu; request mới xếp hàng, bị từ chối hoặc timeout.
2. **Nghẽn kết nối (connection-pool exhaustion):** hết kết nối HTTP client, JDBC hoặc giới hạn kết nối server; luồng có thể đang chờ lấy connection chứ không thực sự dùng nhiều CPU.
3. **Thiếu bộ nhớ (memory pressure/OOM):** heap, direct buffer, metaspace, native thread stacks hoặc container RSS tiến sát giới hạn, dẫn đến GC liên tục, OOM hoặc container bị kill.
4. **Chặn event loop:** đoạn code blocking chạy trên các thread nhỏ và quan trọng của Reactor Netty/WebFlux, khiến nhiều request không liên quan đều chậm.

Không thể khẳng định nguyên nhân chỉ từ một thông báo `TimeoutException` hay tên thread. Cách làm đúng là **đo triệu chứng → xác định tài nguyên bị giới hạn → lấy bằng chứng độc lập → sửa một nguyên nhân → so sánh lại dưới tải tương tự**.

### 1.1 Hai mô hình chạy phổ biến

| Đặc điểm | Spring MVC + Servlet (thường Tomcat) | Spring WebFlux + Reactor Netty |
| --- | --- | --- |
| Ngữ nghĩa HTTP | Controller thường chạy trên luồng worker Servlet | Handler phản ứng, event-loop non-blocking |
| Số luồng | Thường có pool tương đối lớn để chịu các thao tác blocking | Ít event-loop threads; không được chặn chúng |
| Khi xử lý JDBC blocking | Thường sử dụng worker Servlet, cần giới hạn pool | Cần offload khỏi event loop hoặc dùng driver reactive |
| Khi gọi HTTP outbound | Có thể dùng RestClient/RestTemplate hoặc WebClient | WebClient non-blocking là lựa chọn tự nhiên |
| Điểm nghẽn điển hình | Worker threads, accept queue, HikariCP, outbound HTTP | Event-loop blocking, connection provider, Scheduler, buffer |
| Tín hiệu lỗi | Busy threads, queue tăng, connection wait | Event-loop latency, pool acquire queue, stalled subscribers |

Một ứng dụng MVC **vẫn có thể dùng WebClient** và vì vậy cũng có Reactor Netty event-loop ở phía HTTP client. Không suy luận rằng thấy tên `reactor-http-nio-*` thì toàn bộ ứng dụng chạy WebFlux. Spring Boot có thể chọn MVC khi có cả hai starter, tùy cấu hình triển khai. Cần xem dependency, runtime thực tế và stack trace.

### 1.2 Phân biệt chậm vì CPU và chậm vì chờ tài nguyên

Nếu CPU cao bền vững kèm thread `RUNNABLE` thực hiện tính toán liên tục, tìm hot path, vòng lặp, serialization hoặc GC. Nếu CPU thấp nhưng độ trễ cao, nghi ngờ socket/DB chờ, queue, mutex, pool hoặc thread đợi điều kiện. Cần đo **thời gian chờ và độ sâu hàng đợi** chứ không mặc định “CPU thấp là khỏe”.

Ví dụ một request cần tổng cộng 300 ms, nhưng 240 ms chờ kết nối database, 30 ms chạy SQL, 20 ms business logic và 10 ms chuyển đổi JSON. Tối ưu code Java 20 ms không giải quyết phần lớn độ trễ. Lập timeline của từng pha giúp ưu tiên đúng nguồn lực.

## 2. Quy trình ứng cứu có bằng chứng

Khi tần suất timeout tăng, áp dụng một thứ tự ổn định:

1. **Xác định phạm vi sự cố:** tất cả endpoint hay một route? có xảy ra ở mọi instance/pod? từ khi deploy cấu hình nào? tỷ lệ lỗi, p50/p95/p99, throughput, số request đang chờ và error rate.
2. **Bảo vệ hệ thống đang chạy:** nếu cần, áp dụng giới hạn đầu vào, backpressure, giảm traffic không quan trọng hoặc rollback thay đổi vừa phát hành theo quy trình sự cố. Không tăng tất cả các giới hạn bừa bãi.
3. **Thu thập đồng thời metrics, logs, tracing, JVM evidence:** thời điểm phải đồng bộ. Một ảnh chụp tại lúc hệ thống rảnh có thể không thể hiện nút nghẽn.
4. **So sánh mô hình JVM/HTTP/database:** worker busy, active/pending connections, connection acquire wait, GC pauses, used heap/direct memory, số thread và trạng thái event loop.
5. **Thu nhiều thread dump/JFR có khoảng cách:** phân biệt một thread vô tình đang ngủ với một cụm thread liên tục nghẽn tại cùng lời gọi.
6. **Kiểm tra phụ thuộc bên ngoài:** DB slow query/lock, API upstream latency, DNS, load balancer, proxy, network RTT, CPU limit/GC.
7. **Xây giả thuyết có thể bác bỏ:** nếu connection pool là nút nghẽn, tăng thời gian SQL hoặc giữ response body lâu sẽ làm active=max và pending tăng; nếu sửa code trả connection sớm, pending phải giảm dưới cùng tải.
8. **Thay đổi nhỏ và xác nhận lại:** xem p95/p99, error rate, memory, số pool waiters, resource headroom. Ghi lại quyết định và hệ quả.

Mỗi giả thuyết phải trả lời ba câu: **bằng chứng nào ủng hộ, bằng chứng nào chống lại, quan sát nào thay đổi sau khi sửa?**

### 2.1 Những chỉ số cần chụp ngay

| Nhóm | Chỉ số/chứng cứ nên quan sát | Đọc như thế nào |
| --- | --- | --- |
| HTTP | RPS, in-flight requests, latency p95/p99, 429/503/5xx, timeout theo route | Phân biệt quá tải đầu vào với một downstream bất thường |
| Servlet | Tomcat busy/current/max threads, request queue, connection counts | Busy sát max cùng queue tăng gợi ý starvation ở worker |
| Thread/JVM | thread count, daemon count, thread dump, CPU theo thread, deadlock | Stack cho biết đang block I/O, lock, wait hay chạy CPU |
| JDBC/Hikari | active, idle, pending, acquire timeout, query duration, DB connections | Active sát max + pending tăng là dấu hiệu thiếu connection hoặc giữ connection quá lâu |
| HTTP outbound | Reactor Netty active/idle/pending connections, acquire timeouts, connect/response timeouts | Phân biệt không lấy được connection với remote trả chậm |
| Bộ nhớ | heap used/committed/max, GC pause/rate, direct buffer, RSS/container limit | Phân biệt heap retention với áp lực native/direct |
| Event loop | event-loop responsiveness, thread dump stack, long/blocking tasks, BlockHound | Một blocking call có thể làm chậm nhiều socket dùng cùng loop |
| Phụ thuộc | DB locks, slow SQL, upstream p95, connection resets, DNS | Đừng sửa pool khi nguyên nhân nằm ở server phụ thuộc |

Metrics cần được thu theo thời gian (đường biểu diễn) và theo nhãn hợp lý. Đừng tạo nhãn động như userId, orderId hoặc URL nguyên văn có hàng triệu biến thể; cardinality cao có thể làm hệ giám sát tốn bộ nhớ.

## 3. Thu thập chứng cứ bằng Spring Boot Actuator và JVM

### 3.1 Actuator: biết metric nào đang tồn tại

Spring Boot Actuator và Micrometer cung cấp nhóm HTTP, JVM, datasource, server và một số driver. **Tên metric cụ thể phụ thuộc phiên bản, thư viện, cấu hình**: trước hết gọi `/actuator/metrics` để xem danh sách, rồi truy vấn từng metric có thật.

Ví dụ cấu hình tối thiểu cho môi trường nội bộ:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized
  metrics:
    tags:
      application: order-api
```

Đây là cấu hình ví dụ cho một ứng dụng Spring Boot có `spring-boot-starter-actuator`; endpoint `prometheus` còn cần registry tương ứng, ví dụ dependency `io.micrometer:micrometer-registry-prometheus`. Mặc định các endpoint metrics không phải lúc nào cũng được expose. Chỉ expose chúng qua mạng quản trị có xác thực/phân quyền và không tiết lộ thông tin runtime cho người dùng công cộng.

Các lệnh kiểm tra ở máy có quyền gọi endpoint quản trị:

```bash
curl -s http://localhost:8080/actuator/metrics
curl -s http://localhost:8080/actuator/metrics/jvm.threads.live
curl -s http://localhost:8080/actuator/metrics/jvm.memory.used
curl -s http://localhost:8080/actuator/metrics/jvm.gc.pause
curl -s http://localhost:8080/actuator/metrics/http.server.requests
```

Không suy diễn kết quả cứng cho các lệnh này: nó phụ thuộc trạng thái runtime. Ví dụ `jvm.memory.used` có thể trả nhiều measurement/tag theo `area` và `id`; muốn phân tách heap, dùng query tag nếu metric hỗ trợ. Với Prometheus, tên metric có thể được đổi sang dấu gạch dưới và hậu tố đơn vị; Actuator `/metrics` dùng **tên meter gốc**.

Các metric hữu ích **nếu được đăng ký**:

- `tomcat.threads.busy`, `tomcat.threads.current`, `tomcat.threads.config.max` cho Tomcat; một số bản/cấu hình cần bật MBean registry.
- `hikaricp.connections.active`, `hikaricp.connections.idle`, `hikaricp.connections.pending` cho HikariCP.
- `jdbc.connections.active` cho một số DataSource được instrument.
- `reactor.netty.connection.provider.active.connections`, `...idle.connections`, `...pending.connections`, `...pending.connections.time` khi metrics của Reactor Netty ConnectionProvider được bật.
- `jvm.memory.used`, `jvm.memory.max`, `jvm.buffer.memory.used`, `jvm.threads.live`, `jvm.gc.pause` và metric HTTP server.

Không nhìn một con số hiện tại rồi kết luận leak: hãy quan sát xu hướng dưới tải, sau GC, khi traffic giảm và so sánh với baseline.

### 3.2 Chụp thread dump bằng jcmd

Dùng JDK 17+/21 có công cụ `jcmd`, chạy bằng tài khoản được phép truy cập process Java tương ứng. Phải biết đúng PID trong VM/container:

```bash
jcmd -l
jcmd <PID> Thread.print -l > thread-1.txt
```

`<PID>` là chỗ điền số process thực; hai dòng trên chỉ là mẫu lệnh, không chạy nguyên placeholder. Tên subcommand được hỗ trợ cần kiểm tra `jcmd <PID> help Thread.print` theo JDK đang sử dụng. Lấy thêm 2–3 bản thread dump cách nhau vài giây **khi sự cố còn xảy ra**; tránh coi stack một thời điểm là bằng chứng nguyên nhân duy nhất.

Đọc thread dump:

- `RUNNABLE` không luôn đồng nghĩa tiêu CPU: thread Java đang trong native socket I/O có thể ở trạng thái RUNNABLE. Kết hợp CPU-per-thread/JFR.
- `WAITING` / `TIMED_WAITING` có thể là chờ bình thường của thread pool; cần xem **đang chờ ở đâu** và có bao nhiêu request bị ảnh hưởng.
- `BLOCKED` thường liên quan monitor/`synchronized`; stack và `locked ownable synchronizers` giúp xem lock.
- Tìm nhiều `http-nio-*-exec-*` mắc ở JDBC, RestTemplate, khóa hoặc gọi remote; tìm `reactor-http-nio-*` mắc ở `Thread.sleep`, `Future.get`, JDBC hoặc blocking client.
- Nếu hàng trăm request chờ một connection pool, thread dump cho thấy nơi **bị chặn**, trong khi metrics pool xác nhận nguyên nhân thiếu tài nguyên.

Trong production, thread dump có thể chứa tên method, đường dẫn tệp và đôi khi dữ liệu nhạy cảm từ stack; lưu bằng quyền hạn chế và dùng quy trình bảo vệ dữ liệu. JFR/thread dump ít xâm lấn hơn heap dump nhưng vẫn có chi phí.

### 3.3 Java Flight Recorder (JFR)

JFR cung cấp timeline về CPU, allocation, GC, locks, socket và nhiều sự kiện Java. Ví dụ ghi ngắn 60 giây trong lúc lỗi tái hiện:

```bash
jcmd <PID> JFR.start name=web-incident settings=profile duration=60s filename=/tmp/web-incident.jfr
jcmd <PID> JFR.check
```

Đường dẫn phải là nơi process có quyền ghi; trên Windows thay `/tmp/...` bằng đường dẫn phù hợp. Với `duration`, bản ghi được hoàn thiện theo lịch cấu hình; có thể dùng `JFR.dump` hoặc `JFR.stop` theo tình huống. Mở kết quả bằng JDK Mission Control (JMC) hoặc công cụ phân tích JFR được phép.

JFR giúp trả lời: thread nào tiêu CPU, socket read nào kéo dài, allocation ở đoạn code nào, GC pause nào trùng p99 spike và có lock contention thực hay không. Dùng `settings=profile` trong thời gian giới hạn vì lượng sự kiện/chi phí lớn hơn `default`; kiểm tra chính sách chẩn đoán của môi trường.

### 3.4 GC, heap và native memory

Những lệnh read-only thường hữu ích (tùy khả năng của JDK và process):

```bash
jcmd <PID> GC.heap_info
jcmd <PID> VM.flags
jcmd <PID> VM.native_memory summary
```

`VM.native_memory summary` chỉ có thông tin đầy đủ nếu **Native Memory Tracking** đã bật khi JVM khởi động, ví dụ `-XX:NativeMemoryTracking=summary`, và có overhead. Không bật tính năng chỉ bằng việc gọi lệnh. Nếu ứng dụng chết bởi OOMKilled từ orchestrator, JVM có thể không kịp ném `OutOfMemoryError` hoặc ghi heap dump; cần xem giới hạn bộ nhớ container và log của nền tảng.

`GC.class_histogram` và `GC.heap_dump` có thể gây tác động đáng kể, dung lượng lớn và chứa dữ liệu nhạy cảm. Chỉ thực hiện theo kế hoạch đã kiểm tra dung lượng, thời điểm và phân quyền. Không kích hoạt heap dump ngay khi máy đang hết đĩa hoặc memory.

## 4. Cạn luồng trên Spring MVC/Servlet

### 4.1 Mô hình cạn luồng

Tomcat giữ một tập worker để chạy HTTP request. Nếu 200 luồng đều chờ DB/HTTP/lock lâu, request mới có thể phải xếp hàng. Độ trễ tăng ngay cả khi CPU thấp.

```text
Incoming requests
        |
        v
Tomcat accept/connection handling
        |
        v
Request workers (busy: 200 / max: 200)
   |      |      |
   v      v      v
 DB wait HTTP wait Lock wait
        |
        v
Pending work accumulates -> high latency/timeouts
```

Một phép ước lượng từ định luật Little cho hệ ổn định:

**Số request đồng thời xấp xỉ = tốc độ đến (request/giây) × thời gian trung bình trong hệ (giây).**

Ví dụ 100 RPS × 0,2 s ≈ 20 request đang xử lý. Nếu dependency chậm lên 2 s nhưng RPS vẫn 100, con số lên xấp xỉ 200. Đây là **ước lượng trung bình**, không phải công thức đặt chính xác thread pool; burst, queue, CPU, xử lý async, nhiều upstream và giới hạn container có thể đổi kết quả.

### 4.2 Bằng chứng cần đối chiếu

- `tomcat.threads.busy` tăng gần giới hạn và request latency/pending queue cùng tăng.
- Thread dumps lặp lại cho thấy nhiều worker đang ở `HikariPool.getConnection`, `SocketInputStream.read`, `HttpURLConnection` hoặc khóa.
- Database metrics có active=max + waiters/pending? Khi đó cạn luồng có thể **là hậu quả của thiếu JDBC connection**.
- CPU gần 100% kèm runnable tính toán liên tục thì không chỉ là I/O starvation.
- Request rate tăng sau deploy/retry storm? Phải đánh giá nguồn tải trước khi tăng thread count.

### 4.3 Cách sửa đúng tầng

**Nếu công việc chỉ chờ HTTP/DB chậm:** tối ưu query, rút ngắn transaction, đặt timeout cho từng dependency, circuit breaker/giới hạn đồng thời, giảm retry đồng loạt. Thêm thread có thể làm số người cùng chờ tăng và đẩy áp lực sang DB.

**Nếu CPU mới là nút nghẽn:** profile hot method, giảm serialize/parse không cần thiết, giới hạn parallelism theo số CPU; tăng worker không làm CPU tạo ra nhiều thời gian tính toán hơn.

**Nếu pool bị queue quá sâu:** đặt giới hạn request và từ chối sớm theo khả năng hệ thống; trả mã phù hợp (ví dụ 429 cho rate limit, 503 cho tạm quá tải) cùng retry-after theo contract nếu có.

**Nếu đang chạy nhiều tác vụ async:** `@Async` và `Callable`/`DeferredResult` cần executor được quản lý, có giới hạn queue, timeout và chính sách rejection rõ ràng. Không dùng pool mặc định vô hạn trong production.

Ví dụ cấu hình `ThreadPoolTaskExecutor` cho công việc bất đồng bộ có kiểm soát:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
class WorkerPoolConfig {
    @Bean
    ThreadPoolTaskExecutor blockingWorkExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("blocking-work-");
        return executor;
    }
}
```

Đây là một class cấu hình Spring (đặt trong package đã được component scan), không phải chương trình `main` độc lập. Các số 8/16/100 chỉ để minh họa; capacity cần được xác định bằng profile và limit tài nguyên. Bean này **không tự động đổi Tomcat worker pool** hoặc WebFlux event loop; chỉ được dùng tại nơi ứng dụng inject và gọi executor, hoặc được cấu hình vào thành phần async cụ thể.

## 5. Nghẽn kết nối: phải xác định pool nào

Ứng dụng có thể đồng thời sử dụng nhiều loại kết nối:

- **Inbound server connection/worker:** Tomcat/Netty chấp nhận request từ client.
- **JDBC/HikariCP:** lấy connection từ pool để chạy SQL/transaction.
- **Outbound HTTP pool của Reactor Netty:** `WebClient` dùng `ConnectionProvider` để tái sử dụng HTTP connections.
- **Broker, Redis hoặc client khác:** mỗi driver có giới hạn và metrics riêng.

Một thông báo “connection timeout” có thể xảy ra ở bước DNS, TCP connect, chờ lấy connection từ pool, đợi response, hoặc cơ sở dữ liệu. Không gộp tất cả thành một timeout duy nhất.

### 5.1 HikariCP bị giữ kết nối quá lâu

Dấu hiệu điển hình: active gần maximum, idle bằng 0, pending threads tăng, `SQLTransientConnectionException` hoặc log “Connection is not available, request timed out”. Nguyên nhân có thể là:

- Mỗi transaction kéo dài vì query chậm, row lock hoặc chờ remote HTTP trong khi vẫn giữ DB connection.
- Code mở connection/ResultSet nhưng không đóng đúng cách, hoặc response streaming gắn với transaction kéo dài.
- DB đã đạt giới hạn connection hoặc không cho thêm session.
- Pool quá nhỏ so với số transaction đồng thời **thực sự có nhu cầu**, hoặc tải vượt năng lực DB.
- Retry storm khiến nhiều transaction mới xuất hiện trước khi transaction cũ kết thúc.

`@Transactional` không nhất thiết chiếm connection từ đầu tới cuối trong mọi driver/transaction, nhưng thường có thể **giữ connection khi transaction đang dùng DB**. Tránh gọi API remote nhiều giây bên trong một transaction DB nếu không có lý do rõ.

### 5.2 Connection pool của Reactor Netty

`WebClient` dùng Reactor Netty `HttpClient` có thể hoạt động với pool giữ kết nối theo remote host. Khi các connection đang hoạt động đạt `maxConnections`, yêu cầu tiếp theo có thể chờ trong hàng `pending acquire`. Nếu hết giới hạn thời gian hoặc pending queue đầy, client báo lỗi acquisition trước khi request đến server.

Cần phân biệt:

- `pendingAcquireTimeout`: thời gian chờ lấy connection từ pool.
- `CONNECT_TIMEOUT_MILLIS`: thời hạn thiết lập kết nối TCP.
- `responseTimeout`: thời gian chờ liên quan response theo cấu hình HTTP client.
- `Mono.timeout(...)`: deadline của publisher ở tầng Reactor; có thể cancel upstream.
- `maxIdleTime` / `maxLifeTime`: chính sách lưu/reuse connection, không phải thời gian chờ server xử lý.

Không dựa vào một con số “mặc định pool bao nhiêu” vì thay đổi theo Reactor Netty version, cấu hình global, CPU và cách tạo `ConnectionProvider`. Luôn xem metrics và version runtime.

### 5.3 Ví dụ cấu hình outbound HTTP pool có giới hạn

Đây là cấu hình Spring Boot có `spring-webflux` và Reactor Netty connector, dùng cho `WebClient` outbound kể cả trong ứng dụng MVC:

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
class OutboundHttpClientConfig {

    @Bean(destroyMethod = "dispose")
    ConnectionProvider inventoryConnectionProvider() {
        return ConnectionProvider.builder("inventory-api")
                .maxConnections(50)
                .pendingAcquireMaxCount(100)
                .pendingAcquireTimeout(Duration.ofSeconds(2))
                .metrics(true)
                .build();
    }

    @Bean
    WebClient inventoryWebClient(WebClient.Builder builder,
                                 ConnectionProvider inventoryConnectionProvider) {
        HttpClient http = HttpClient.create(inventoryConnectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2_000)
                .responseTimeout(Duration.ofSeconds(3));

        return builder.clone()
                .baseUrl("https://inventory.example")
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
    }
}
```

Spring quản lý `ConnectionProvider` bean và gọi `dispose` khi context đóng. Phải thay domain bằng địa chỉ cấu hình thật và bổ sung TLS/authn nếu endpoint yêu cầu. `metrics(true)` cần các dependency Micrometer phù hợp. Không chia sẻ connection provider chỉ để “giải quyết” pool cạn khi nhiều đối tác có đặc tính hoàn toàn khác nhau; quản trị tài nguyên theo nhu cầu và xác định owner của pool.

**Các giới hạn 50/100/2s/3s chỉ là ví dụ.** Nếu upstream không trả response trong 3 giây, timeout có thể xảy ra dù còn connection; khi đó tăng `maxConnections` có thể làm upstream quá tải thêm. Cần đo connection active/pending, duration, error code, TLS/connect time và throughput.

### 5.4 Trả connection về pool đúng vòng đời

Một connection HTTP cần body của response được tiêu thụ hoặc giải phóng phù hợp để tái sử dụng. Với `WebClient.retrieve().bodyToMono(...)`, Spring thường quản lý quá trình giải mã và vòng đời body. Với các API cấp thấp `exchangeToMono` / `exchangeToFlux`, phải xử lý body trong function tương ứng; không trả `ClientResponse` để xử lý ngoài vòng đời chức năng mà không hiểu API. Nếu tự làm việc với `DataBuffer` pooled, cần giải phóng dữ liệu không còn sử dụng theo quy tắc của Spring.

**Dấu hiệu leak/giữ connection:** active sát max dai dẳng ngay khi request đã “xong” ở tầng nghiệp vụ, pending liên tục tăng và thread dumps hoặc leak detector chỉ ra response body không được hoàn tất. Nhưng cũng có thể endpoint stream đang cố ý giữ connection; cần tách lưu lượng streaming lâu dài khỏi request ngắn khi thiết kế capacity.

## 6. Thiếu bộ nhớ: heap, direct memory, metaspace và container

### 6.1 Memory của JVM không chỉ là -Xmx

RSS của process Java có thể bao gồm:

```text
Container memory budget / process RSS
       |
       +-- Java heap (-Xmx) / object graph
       +-- JVM metaspace / class metadata
       +-- Code cache / JIT
       +-- Thread stack của platform threads
       +-- Direct / off-heap buffers (NIO/Netty)
       +-- JNI/native libraries & allocator overhead
       +-- JVM runtime structures và vùng native khác
```

Nếu đặt `-Xmx` quá sát memory limit của container, phần native/direct/thread stack không còn chỗ; container có thể bị OOMKilled ngay cả khi Java heap chưa đầy. `jvm.memory.used` không đo toàn bộ RSS. Ngược lại, heap used tăng đến một mức và ổn định sau GC có thể là bình thường, **không tự chứng minh memory leak**.

### 6.2 Phân biệt các loại lỗi

| Triệu chứng | Giả thuyết cần kiểm chứng | Bằng chứng |
| --- | --- | --- |
| `OutOfMemoryError: Java heap space` | Heap thật thiếu hoặc object bị giữ quá lâu | Heap/GC, allocation rate, histogram và dominator tree khi có heap dump |
| `OutOfMemoryError: Direct buffer memory` | Direct buffer quá nhiều/không giải phóng/giới hạn direct memory | Direct buffer metrics, allocation stack, Netty leak detector, RSS |
| `Metaspace` OOM | Nạp class/classloader quá nhiều hoặc leak classloader | Classloader stats, metaspace metrics, JFR |
| `unable to create native thread` | Quá nhiều thread hoặc giới hạn native memory/PID | Thread count, OS process/thread limit, stack size, RSS |
| Container `OOMKilled` không có Java OOME | RSS/container limit vượt ngưỡng | Orchestrator event, container memory working set, JVM memory breakdown |
| GC pause nhiều, CPU cao | Allocation churn hoặc heap pressure | JFR GC, allocation profile, heap occupancy sau GC |

### 6.3 Spring WebFlux/DataBuffer thường gặp

- `bodyToMono(String.class)` gom toàn body thành một String: nếu response lớn, allocation lớn và heap pressure. Với payload lớn nên parse/stream theo phần, có kích thước tối đa cho từng request.
- `DataBufferLimitException` là ngoại lệ **vượt giới hạn lượng dữ liệu được gom/giải mã**, không nhất thiết có memory leak; đừng tự tăng `maxInMemorySize` lên mức vô hạn để che lỗi dữ liệu quá lớn.
- Với `DataBuffer` kiểu pooled, tự giữ, chuyển, loại bỏ hoặc đọc thủ công mà không release đúng lúc có thể làm tăng direct memory.
- `Flux.cache()`/`collectList()`/`buffer()` trên luồng vô hạn hoặc stream lớn có thể giữ hàng triệu object; `onBackpressureBuffer` không có giới hạn có thể biến nghẽn downstream thành OOM.
- Các kết nối SSE/WebSocket nhiều và tồn tại lâu cần quota, lifetime, giới hạn message size, timeout và cleanup.
- Ghi log body HTTP không giới hạn hoặc giữ exception stack/body lớn cũng tạo áp lực bộ nhớ.

Ví dụ xử lý upload/download phải có giới hạn, chuyển tiếp theo stream khi phù hợp, không gom toàn bộ vào `byte[]` nếu không cần. Chỉ điều chỉnh codec limit sau khi biết payload hợp lệ tối đa và budget memory theo concurrency.

### 6.4 Chẩn đoán leak thật, không nhầm với cache hợp lệ

Một memory leak thường có **live set** tăng dần sau các lần GC và không hồi phục sau khi tải giảm. Điều tra gồm: histogram nhiều lần, JFR allocation, heap dump tại một thời điểm có đủ dung lượng và so sánh dominator graph. Đặt giả thuyết về ai giữ reference (static map, cache không TTL, subscriber/connection chưa dọn, ThreadLocal, classloader, scheduled job).

Với Netty direct memory, heap dump có thể không chứa toàn bộ nội dung native buffer; cần kết hợp direct buffer metrics, Netty leak detection trong môi trường kiểm soát, NMT (nếu bật) và native allocation profile. Không tự động kết luận “không thấy heap leak nên không có leak”.

## 7. Chặn event loop trong Spring WebFlux

### 7.1 Event loop vì sao đặc biệt nhạy cảm?

Reactor Netty thường dùng một số lượng nhỏ thread I/O (ví dụ tên `reactor-http-nio-*`) để xử lý rất nhiều socket. Nếu một callback chạy `Thread.sleep`, gọi JDBC blocking, `Future.get`, `RestTemplate` hoặc I/O file đồng bộ trên event loop, nó giữ thread đó trong lúc chờ. Các kết nối khác cùng event loop không được xử lý kịp, làm p99 tăng mạnh ngay cả khi CPU rất thấp.

```text
Event-loop thread #1:
  socket A -> callback -> Thread.sleep(1000)  [BLOCKED 1 second]
  socket B -> cannot progress
  socket C -> cannot progress

Event-loop thread #2:
  sockets D, E ... continue normally
```

Điểm quan trọng: **“code trả Mono” không tự làm cho mọi thao tác bên trong không chặn**. Nếu gọi JDBC trước khi tạo Mono hoặc gọi blocking method trong `map` đang chạy trên event loop, vẫn có vấn đề.

### 7.2 Ví dụ sai: blocking trước khi tạo publisher

```java
@GetMapping("/customers/{id}")
Mono<Customer> customer(@PathVariable long id) {
    Customer value = legacyJdbcService.loadCustomer(id); // Chặn ngay.
    return Mono.just(value);
}
```

Đây là fragment bên trong WebFlux controller có `legacyJdbcService` (API blocking). Blocking xảy ra ngay khi controller method chạy. `Mono.just` không chuyển công việc sang thread khác; chỉ bọc giá trị đã được tính.

Ngay cả đoạn sau vẫn block nếu subscriber chạy trên event loop:

```java
return Mono.fromCallable(() -> legacyJdbcService.loadCustomer(id));
```

`fromCallable` trì hoãn tính toán đến subscription, nhưng **không tự đổi scheduler**.

### 7.3 Cách bọc một blocking API khi chưa thể chuyển sang reactive driver

```java
@GetMapping("/customers/{id}")
Mono<Customer> customer(@PathVariable long id) {
    return Mono.fromCallable(
                    () -> legacyJdbcService.loadCustomer(id))
            .subscribeOn(Schedulers.boundedElastic());
}
```

Đây là fragment trong một `@RestController` có `legacyJdbcService` và imports `Mono`, `Schedulers`, `GetMapping`, `PathVariable`, `Customer`. `subscribeOn(boundedElastic())` khiến blocking call chạy trên pool dành cho việc có thể chặn, giúp event loop không bị giữ trong lúc đợi.

Đổi scheduler **không làm JDBC trở thành non-blocking**. BoundedElastic vẫn có giới hạn thread/queue; nếu query chậm và lưu lượng quá lớn, queue của boundedElastic lại đầy. Cần giải quyết cả giới hạn DB, query duration, timeout, số việc đồng thời và ngừng phát tác vụ không có nhu cầu.

Nếu dùng `publishOn`, nó chuyển execution downstream từ vị trí tương ứng; `subscribeOn` trên publisher bọc blocking source thường dễ dự đoán hơn cho cách này. Không dùng `Schedulers.parallel()` để chạy I/O blocking lâu vì scheduler đó hướng tới công việc CPU giới hạn thread.

### 7.4 Không chữa bằng block() hoặc subscribe() thủ công

- `.block()` trong WebFlux handler là dấu hiệu sai mô hình; trên Reactor thread được đánh dấu non-blocking, có thể ném `IllegalStateException`. Ở nơi khác nó vẫn có thể làm tắc worker.
- `.subscribe()` thủ công trong controller tách vòng đời lỗi/cancellation khỏi HTTP response; thường nên trả publisher để framework subscribe.
- `CompletableFuture.join()`, `Thread.sleep()`, `LockSupport.parkNanos()`, driver JDBC/Redis đồng bộ hoặc DNS synchronous trong callback cũng đáng kiểm tra.
- `Flux.parallel()` hoặc tăng số scheduler không thay thế limit tài nguyên bên ngoài.

### 7.5 Phát hiện blocking call

Tìm stack như sau trong thread dumps/JFR: `reactor-http-nio-*` đang ở `Thread.sleep`, `SocketInputStream.read`, `java.sql.*`, `Future.get`, `FileInputStream.read` hoặc thư viện đồng bộ. Kết hợp sự gia tăng latency của **nhiều endpoint độc lập** chạy chung event loop.

Trong test, **BlockHound** có thể instrument code để phát hiện blocking call trên thread được đánh dấu non-blocking. Ví dụ ý tưởng:

```java
BlockHound.install();

Mono.delay(Duration.ofMillis(1))
        .doOnNext(ignored -> {
            try {
                Thread.sleep(10); // Chỉ là lỗi chủ động trong test.
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(error);
            }
        })
        .block();
```

Đây là fragment test có `io.projectreactor.tools:blockhound` (phiên bản tương thích) và `reactor-core`, không phải code đưa vào production. Với JDK 13+ và các phiên bản BlockHound nhất định, công cụ có thể cần JVM flag `-XX:+AllowRedefinitionToAddDeleteMethods`; kiểm tra tài liệu và tương thích JDK đang chạy. BlockHound không phát hiện **mọi** blocking operation, và một test không có lỗi chưa chứng minh toàn ứng dụng không block.

Nếu cần kiểm tra chính sách an toàn trong dự án, dùng JFR + test về độ trễ event loop/CPU, kiểm soát thread names và một test BlockHound độc lập ở môi trường phù hợp; đừng cài agent vô điều kiện trong production.

## 8. Bài thực hành Java 17+: hiểu tác động của thời gian chờ lên độ đồng thời

Trước khi tối ưu pool, cần hiểu quan hệ cơ bản giữa thời gian xử lý và lượng công việc đang trong hệ. Ví dụ này dùng **Java thuần**, chạy được mà không có Spring/Gradle, để ước lượng tải trung bình theo định luật Little. Nó **không mô phỏng scheduler thật** và không đề xuất thông số production.

**Tệp `CapacityEstimateDemo.java`:**

```java
public class CapacityEstimateDemo {

    static double averageInFlight(double requestsPerSecond,
                                  double averageSeconds) {
        if (requestsPerSecond < 0 || averageSeconds < 0) {
            throw new IllegalArgumentException("Non-negative only");
        }
        return requestsPerSecond * averageSeconds;
    }

    public static void main(String[] args) {
        double rps = 100;

        System.out.printf("fast dependency: %.0f requests%n",
                averageInFlight(rps, 0.2));

        System.out.printf("slow dependency: %.0f requests%n",
                averageInFlight(rps, 2.0));

        System.out.printf("after timeout cap: %.0f requests%n",
                averageInFlight(rps, 0.8));
    }
}
```

Lệnh:

```bash
javac CapacityEstimateDemo.java
java CapacityEstimateDemo
```

**Stdout chính xác** (trên môi trường dùng định dạng số dấu chấm cho ví dụ):

```text
fast dependency: 20 requests
slow dependency: 200 requests
after timeout cap: 80 requests
```

Giải thích: ở tốc độ 100 RPS ổn định, tăng thời gian trong hệ từ 0,2 giây lên 2 giây làm số lượng trung bình từ 20 lên 200. Giá trị “after timeout cap” là kịch bản minh họa thời gian trung bình giảm còn 0,8 giây; thực tế áp timeout có thể làm tăng lỗi, retry hoặc hàng đợi. Không nên đặt timeout chỉ để ép phép tính.

**Thực hành tiếp:** chọn một API nội bộ có số liệu test. Đo throughput, p95/p99, pool active/pending, thread dump. Làm chậm dependency có kiểm soát trên môi trường test, quan sát loại tài nguyên bị bão hòa, sau đó khôi phục tốc độ dependency hoặc thêm concurrency limit và đo lại. Không tạo tải bất thường lên hệ thống production.

## 9. Chẩn đoán qua bốn ca điển hình

### 9.1 Case A: Tomcat threads 200/200, CPU 20%

**Hiện tượng:** p99 tăng, HTTP 503/timeout, JVM CPU chỉ khoảng 20%, worker busy gần tối đa. Thread dumps cho thấy nhiều `http-nio-*` gọi `HikariPool.getConnection`. Metrics Hikari active=max và pending tăng.

**Giả thuyết hợp lý:** thiếu JDBC connection do query/transaction giữ connection, worker Servlet chờ nối. **Không** kết luận “Tomcat cần 500 threads”: nhiều threads hơn có thể cùng đợi DB lâu hơn.

**Kiểm chứng:** xem slow query/DB lock, transaction duration, đối chiếu active vs max của Hikari, số pending và DB-side connection/lock; tìm SQL chậm theo traceId.

**Khắc phục:** tối ưu SQL/index, rút ngắn transaction, tránh network call giữa transaction; điều chỉnh concurrency/timeout có hạn; chỉ đổi kích thước pool nếu DB có năng lực phục vụ. Sau sửa, pending giảm và p99 phục hồi.

### 9.2 Case B: WebClient pending connections tăng, HTTP upstream vẫn khỏe

**Hiện tượng:** request outbound fail với pool acquire timeout; metrics Reactor Netty active sát max, pending tăng; cùng lúc server phía xa có latency ổn.

**Khả năng:** giữ connection vì response body chưa được tiêu thụ/đóng, stream sống lâu, max pool quá nhỏ so với concurrency hoặc request fan-out tạo nhiều kết nối đồng thời.

**Kiểm chứng:** số active/pending sau khi traffic giảm, loại endpoint streaming, trace span duration, body consumption path và Reactor Netty metrics. Thử với workload cố định có/không có response body release đúng.

**Khắc phục:** tiêu thụ/release body đúng vòng đời, giới hạn fan-out, tách pool cho stream dài, đặt pending timeout và limit hợp lý, áp dụng backpressure; tăng maxConnections nếu server/upstream có headroom thực sự.

### 9.3 Case C: JVM heap ổn nhưng pod OOMKilled

**Hiện tượng:** heap used ~60% Xmx, GC bình thường, nhưng container RSS sát limit và bị OOMKilled; ứng dụng không ghi `Java heap space`.

**Khả năng:** direct/off-heap buffers, nhiều native thread stacks, metaspace, JVM native allocator hoặc giới hạn container chưa tính memory ngoài heap.

**Kiểm chứng:** JVM heap, `jvm.buffer.memory.used`, số thread, `VM.native_memory summary` (nếu NMT bật), RSS và cgroup limit. Nhìn trend trước khi chết; heap dump **có thể không chứa nguyên nhân** nếu leak ở direct memory.

**Khắc phục:** giảm retention và kích thước buffer, release pooled DataBuffer, giới hạn số connection/stream/thread, chọn budget Xmx và native phù hợp với container, giảm cache/queue không giới hạn.

### 9.4 Case D: WebFlux latency spike nhưng CPU vẫn thấp

**Hiện tượng:** đồng loạt nhiều endpoint phản hồi trễ đúng từng đợt ~1 giây; thread dump cho thấy `reactor-http-nio-*` đang `Thread.sleep(1000)` hoặc gọi JDBC blocking.

**Khả năng:** callback trên event loop bị chặn. Khác với một downstream đơn lẻ chậm, ảnh hưởng lan tới nhiều request trên cùng event loop.

**Kiểm chứng:** thread dumps liên tiếp, JFR hoặc BlockHound trong test; tái hiện khi bật/tắt đoạn blocking trong môi trường kiểm soát.

**Khắc phục:** thay bằng I/O non-blocking thực sự; trong giai đoạn chuyển đổi, dùng `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`; áp concurrency limit và timeout cho dependency blocking. Xác minh event-loop latency/p99 phục hồi, đồng thời không đẩy nghẽn sang boundedElastic/DB.

## 10. Phân loại lỗi thường gặp: đừng đoán từ exception

| Lỗi/tín hiệu | Có thể là gì | Cần kiểm chứng thêm |
| --- | --- | --- |
| `TimeoutException` từ `Mono.timeout` | Publisher chưa phát tín hiệu đúng hạn | Nguồn chậm, acquire wait, event-loop block, timeout đặt quá thấp |
| `PoolAcquireTimeoutException` | Chờ connection từ Reactor Netty pool quá hạn | Active/pending/max, response body retention, upstream concurrency |
| `ConnectTimeoutException` | Kết nối TCP chưa thiết lập kịp | DNS, routing, firewall, backlog server, network và remote readiness |
| `ReadTimeoutException` / response timeout | Không nhận response/dữ liệu trong thời hạn | Remote server/stream, network, proxy hoặc timeout config |
| `SQLTransientConnectionException` | JDBC pool không cấp connection kịp | Hikari active/pending, DB locks, transaction duration |
| `DataBufferLimitException` | Vượt ngưỡng aggregate/codec | Payload size/shape, codec limit, streaming design; không đồng nghĩa leak |
| `OutOfMemoryError: Java heap space` | Thiếu heap khả dụng | GC, live set, allocation, cache, heap dominators |
| `OutOfMemoryError: Direct buffer memory` | Direct memory không đủ | Buffers, connection count, release, limit direct memory |
| `IllegalStateException: block() ... non-blocking thread` | Blocking Reactor API trên thread non-blocking | Vị trí `block()`/`blockFirst()` và pipeline |
| Pod `OOMKilled` | Container vượt memory limit | RSS/cgroup/native memory, JVM heap chỉ là một phần |
| 503 tăng cùng thread/queue saturation | Server không đủ năng lực phục vụ tức thời | Pool, backlog, downstream, rate, retries, admission control |

Không dựa hoàn toàn vào tên exception; đọc root cause và toàn stack (loại bỏ dữ liệu nhạy cảm trước chia sẻ). Cùng một HTTP 503 có thể đến từ application, reverse proxy, load balancer hoặc upstream. Cần correlation/trace ID để xác định tầng phát sinh lỗi.

## 11. Những thay đổi dễ làm tình hình xấu hơn

- **Tăng Tomcat maxThreads vô giới hạn:** nhiều request giữ JDBC/socket lâu hơn, native thread stacks tăng, DB chịu tải nặng hơn.
- **Tăng Hikari maximumPoolSize ngay lập tức:** DB có thể hết session, memory và tăng lock contention; pool phải phù hợp giới hạn DB và latency SQL.
- **Tăng Reactor Netty maxConnections rất lớn:** mở nhiều kết nối có thể gây connect timeout hoặc làm upstream quá tải; giải quyết response lifecycle và fan-out trước.
- **Dùng queue không giới hạn:** chuyển overload thành tăng heap, thời gian chờ và OOM thay vì từ chối sớm có kiểm soát.
- **Tắt timeout vô thời hạn:** giữ threads/connections/subscriptions mãi khi dependency chết.
- **Tăng `maxInMemorySize` không có phân tích:** có thể tránh một `DataBufferLimitException` nhưng đổi thành heap OOM khi nhiều request đồng thời.
- **Đưa mọi blocking call vào boundedElastic mà không giới hạn:** không block event loop nhưng vẫn có thể cạn boundedElastic threads/queue hoặc connection pool.
- **Dùng retry đồng loạt không backoff/jitter/idempotency:** khuếch đại traffic trong lúc hệ thống yếu nhất.
- **Tắt logging/metrics toàn bộ để giảm overhead:** mất bằng chứng nguyên nhân; chỉ giảm logging dư thừa, bật observability phù hợp.
- **Chỉ nhìn heap dump khi pod chết:** có thể bỏ qua native memory, socket hoặc thread stack.

Bất kỳ thay đổi pool/timeout/retry nào cũng cần kèm mục tiêu về p95/p99, lỗi, throughput và giới hạn tài nguyên, sau đó đo lại.

## 12. Kiểm thử và diễn tập ở môi trường kiểm soát

### 12.1 Kịch bản 1: DB trì hoãn

Dùng test database hoặc stub repository gây trễ có kiểm soát. Phát một lượng request vừa đủ, ghi lại Hikari active/pending, Tomcat busy và p95. Thử cùng tải với transaction được rút ngắn; xác minh pending giảm. Không tăng traffic vào hệ thống production để tự tạo sự cố.

### 12.2 Kịch bản 2: WebClient pool nhỏ

Tạo một HTTP test server phản hồi trễ (ví dụ WireMock/MockWebServer) và `WebClient` có maxConnections nhỏ, pending queue giới hạn. Gửi request song song, xác nhận metrics pending/acquire timeout khớp lý thuyết; sau đó thực hiện body consumption đúng, giới hạn concurrency và so sánh.

### 12.3 Kịch bản 3: Event-loop blocking

Trong môi trường test, tạo hai endpoint WebFlux, một endpoint có callback cố ý `Thread.sleep`. Gửi đồng thời request vào endpoint nhanh; đo đuôi latency. Sửa endpoint chặn sang `boundedElastic` và kiểm tra request nhanh không còn bị chậm tương tự. Dùng test thời gian có ngưỡng rộng, không assert chính xác từng mili giây.

### 12.4 Kịch bản 4: Memory retention

Mô phỏng cache không TTL hoặc `Flux.cache()` trên luồng tăng mãi với quota test nhỏ; theo dõi heap/live set sau các chu kỳ GC. Loại bỏ giữ tham chiếu, đặt giới hạn/TTL và so sánh. Test direct buffer phải bảo đảm buffer release và cleanup; không tạo leak cố ý trên máy dùng chung/production.

### 12.5 Các assertion nên có

- Khi quá tải, hệ thống từ chối/timeout **trong giới hạn thời gian có chủ đích**, không treo vô thời hạn.
- Khi dependency phục hồi, queue/pending và error rate trở về gần baseline, không còn tích tụ.
- Phát hiện leak bằng dữ liệu qua thời gian, không bằng một snapshot may rủi.
- Tránh tác dụng phụ lặp do retry: idempotency key và trạng thái xử lý nhất quán.
- Không còn blocking call trên event loop trong những luồng đã kiểm soát.
- Sau dừng hoặc hủy stream, connection/allocated buffers được giải phóng.

## 13. Checklist nhanh theo thứ tự kiểm tra

**Khi HTTP response chậm và CPU thấp:**

1. Endpoint nào, instance nào, từ khi nào? Kiểm tra error rate và dependency latency.
2. Tomcat workers busy hay WebFlux event-loop stalls? Spring MVC có đang dùng WebClient phía client không?
3. DB connections hoặc outbound HTTP connections có `active=max`, `pending>0` không?
4. Thread dumps/JFR chứng minh thread chờ cái gì? Có thread đang chặn event loop không?
5. Heap, native RSS và GC có cùng tăng không? Có container OOMKill không?
6. Có một thay đổi cấu hình, retry, fan-out hay cache mới vừa được triển khai?
7. Can thiệp tối thiểu: giới hạn đầu vào, sửa query/response lifecycle/offload blocking, cấu hình timeout thực tế.
8. Đo lại p95/p99, saturation, memory và các chỉ số người dùng quan trọng dưới tải tương đương.

**Khi nghi ngờ memory leak:** xem live set sau GC, heap dump khi đủ an toàn, direct memory, thread count, classloader và object retention. **Khi nghi ngờ event loop:** tìm stack blocking trên `reactor-http-nio-*`, kiểm tra phản hồi của endpoint độc lập và tái hiện có kiểm soát. **Khi nghi ngờ thiếu connection:** chứng minh pending acquire/connection wait và xác định **pool nào** trước khi đổi kích thước.

## 14. Tài liệu chính thức để tra cứu

- [Spring Framework — WebFlux concurrency model](https://docs.spring.io/spring-framework/reference/web/webflux/new-framework.html): Servlet blocking model và event-loop, xử lý thư viện blocking.
- [Spring Boot — Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html): metrics JVM/HTTP/connection và Actuator metrics endpoint.
- [Reactor Netty — HTTP client connection pool và timeouts](https://projectreactor.io/docs/netty/release/reference/http-client.html): `ConnectionProvider`, queue, timeout và Micrometer metrics.
- [Oracle JDK 21 — Diagnostic Tools](https://docs.oracle.com/en/java/javase/21/troubleshoot/diagnostic-tools.html): `jcmd`, thread dumps, JFR, native memory và các công cụ JVM.
- [Oracle JDK 21 — Java Flight Recorder](https://docs.oracle.com/en/java/javase/21/jfapi/flight-recorder.html): thu thập và phân tích sự kiện JFR.
- [Spring Framework — DataBufferUtils](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/io/buffer/DataBufferUtils.html): vòng đời và release của pooled buffers.
- [Spring Framework — DataBufferLimitException](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/io/buffer/DataBufferLimitException.html): nguyên nhân vượt giới hạn aggregate.
- [Project Reactor — FAQ: blocking calls on boundedElastic](https://projectreactor.io/docs/core/release/reference/faq.html#faq.wrap-blocking): bọc nguồn blocking bằng `fromCallable` và `subscribeOn`.
- [Reactor BlockHound](https://github.com/reactor/BlockHound): phát hiện blocking calls trong các thread non-blocking.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="operations-practice-005">Triển khai sau proxy: HTTPS, forwarded headers, streaming và WebSocket</a>

<details>
<summary>Xem chi tiết</summary>

# Triển khai Spring Web sau reverse proxy: HTTPS, forwarded headers, streaming và WebSocket

## 1. Bức tranh triển khai và các lỗi thường bị nhầm

Trong triển khai thực tế, người dùng thường kết nối tới **reverse proxy** (Nginx, Envoy, gateway, ingress, load balancer), rồi proxy chuyển request tới ứng dụng Spring chạy trên mạng nội bộ. Proxy có thể terminate TLS, đổi protocol, địa chỉ IP, hostname, port, path prefix và đặc tính truyền dữ liệu.

```text
Trình duyệt / API client
https://app.example.test       wss://app.example.test/ws
          │ TLS tới proxy
          ▼
┌──────────────────────────────────────────────┐
│ Reverse proxy / Load balancer                 │
│ - HTTPS certificate, TLS policy               │
│ - route, trust boundary, forwarded headers    │
│ - HTTP buffering/timeout, WebSocket Upgrade   │
└──────────────────────────────────────────────┘
          │ HTTP/1.1 nội bộ (ví dụ)
          │ Host/Forwarded/X-Forwarded-* được kiểm soát
          ▼
Spring Boot / Spring MVC hoặc WebFlux
http://127.0.0.1:8080
          │
          ├─ REST/redirect/absolute link
          ├─ SSE/NDJSON/file streaming
          └─ WebSocket handler / STOMP endpoint
```

Ứng dụng ở sau proxy có thể nhìn request nội bộ là `http://127.0.0.1:8080`, dù client thật đã dùng HTTPS. Nếu không truyền và xử lý metadata gốc đúng cách, ứng dụng có thể trả:
- `Location: http://...` thay vì `https://...`, gây mixed content, vòng lặp redirect hoặc sai OAuth callback.
- Cookie thiếu `Secure` hoặc URL/scheme/host suy luận sai từ request.
- SSE “chờ lâu rồi mới nhận một lượt” do buffering/compression ở proxy.
- WebSocket handshake thất bại (`400`/`404`/`426`/`502`) hoặc kết nối tự đóng do thiếu Upgrade, timeout/idle.
- Log IP client sai, hoặc nguy hiểm hơn: tin header IP/host do attacker giả mạo.

Không có một tùy chọn Spring duy nhất sửa tất cả. Phải phối hợp **TLS & trust boundary → forwarded metadata → buffering/timeouts → WebSocket routing → test end-to-end**.

Phần ví dụ sử dụng **Spring Boot 3.3.x, Spring Framework 6.1.x, Java 17+ và Nginx**, với lưu ý riêng cho Servlet MVC/WebFlux. Các hostname, địa chỉ mạng và đường dẫn chứng chỉ được đưa ra để minh họa mô hình; triển khai thật dùng domain, chứng chỉ, mạng nội bộ và chính sách bảo mật của hệ thống.

## 2. HTTPS termination: hai kết nối khác nhau

**TLS termination tại proxy** nghĩa là TLS từ client được xác thực/kết thúc ở proxy, sau đó proxy có thể mở một HTTP connection không mã hóa **trên mạng nội bộ đã kiểm soát** tới Spring.

```text
Browser -- HTTPS/TLS --> Nginx -- HTTP --> Spring :8080
```

Ưu điểm: tập trung certificate, TLS version/cipher, HSTS và lifecycle chứng chỉ; Spring có thể chỉ nghe port nội bộ. Nhưng từ phía Spring, `request.getScheme()` và `isSecure()` ban đầu có thể phản ánh **hop nội bộ** (HTTP, false), không phải giao thức ở browser. Cần metadata được proxy xác thực như `X-Forwarded-Proto: https` hoặc `Forwarded: proto=https;host=...`.

**TLS end-to-end / re-encryption** dùng HTTPS cả proxy → backend:

```text
Browser -- HTTPS/TLS --> Proxy -- HTTPS/TLS --> Spring
```

Ở đây proxy phải verify certificate/hostname của upstream khi cấu hình như vậy; việc bật HTTPS upstream mà không verify đúng danh tính server không đủ chống giả mạo backend. Cân nhắc mTLS khi mạng/kiến trúc yêu cầu. Forwarded headers vẫn có ích vì hostname/port/public prefix bên ngoài có thể khác hostname nội bộ dù cả hai hop là HTTPS.

**Đừng chỉ bật `server.ssl.*` trong Spring và nghĩ proxy đã xong:** nếu proxy terminate TLS nhưng upstream đã chuyển sang HTTPS, `proxy_pass` phải khớp protocol/port backend. Mismatch thường gây `502 Bad Gateway` hoặc lỗi TLS handshake. Không bật HTTPS ở backend chỉ vì client dùng HTTPS nếu topology đã quyết định TLS termination ở trust boundary.

Trách nhiệm cơ bản tại proxy:
1. Redirect HTTP port 80 sang HTTPS port 443 nếu hệ thống yêu cầu.
2. Dùng chứng chỉ hợp lệ cho public hostname; cấu hình TLS hiện đại và tự động gia hạn.
3. Chặn truy cập trực tiếp port ứng dụng từ Internet; backend chỉ nhận traffic từ proxy/ingress đáng tin.
4. Hạn chế request size, kết nối và các policy an ninh thích hợp; giữ thời hạn proxy hợp với workload.
5. Gửi đầy đủ thông tin public scheme/host/port theo chính sách rõ ràng, tuyệt đối không chuyển tiếp giá trị giả mạo từ bên ngoài.

HSTS (`Strict-Transport-Security`) chỉ nên bật sau khi xác minh HTTPS hoạt động ổn định trên domain/subdomain cần áp dụng, đặc biệt nếu dùng `includeSubDomains`. Các cookie chứa session thường cần `Secure`, `HttpOnly`, `SameSite` theo chính sách auth/cross-site; forwarded scheme là một phần thông tin để ứng dụng thiết lập chúng, nhưng không thay việc cấu hình cookie.

## 3. Forwarded headers: phục hồi URL thật và xác định ranh giới tin cậy

Hai họ header phổ biến:

**RFC 7239 `Forwarded`:**

```http
Forwarded: for=203.0.113.10;proto=https;host=app.example.test
```

**`X-Forwarded-*` (de facto):**

```http
X-Forwarded-For: 203.0.113.10
X-Forwarded-Proto: https
X-Forwarded-Host: app.example.test
X-Forwarded-Port: 443
```

Một số proxy còn dùng `X-Forwarded-Prefix: /portal` nếu public path có prefix khác ứng dụng. Các ví dụ địa chỉ `203.0.113.0/24` và domain `example.test` thuộc không gian dành cho tài liệu, không phải endpoint có thể truy cập thực tế.

Ý nghĩa từng trường:
- `For` / `X-Forwarded-For`: IP ở đầu chuỗi qua các proxy (có thể nhiều hop).
- `Proto` / `X-Forwarded-Proto`: giao thức nhìn từ bên ngoài, ví dụ `https`.
- `Host` / `X-Forwarded-Host`: public hostname, đôi khi kèm port.
- `X-Forwarded-Port`: port public nếu proxy dùng.
- `X-Forwarded-Prefix`: base path phía public khi proxy strip/prefix rewrite.

Sau khi được framework tin cậy và xử lý, các thao tác dựa trên request như tạo absolute links, redirects, `getScheme()`, `getServerName()`, `isSecure()` có thể phản ánh URL phía client. Cần phân biệt:
- Header `Host` của HTTP request upstream do proxy đặt.
- Forwarded headers do proxy quản lý.
- Giá trị `request.getRemoteAddr()` ở socket có thể vẫn là địa chỉ proxy, không phải IP gốc; việc nhận dạng IP qua chuỗi proxy phụ thuộc container/adapter và trust model.

### Quy tắc bảo mật quan trọng nhất

**Không tin `Forwarded`/`X-Forwarded-*` do client Internet tự đặt.** Nếu một attacker tự gửi:

```http
X-Forwarded-Host: attacker.example
X-Forwarded-Proto: http
```

mà ứng dụng trực tiếp tin, absolute redirect/password reset URL có thể trỏ sai host hoặc cookie/URL security bị ảnh hưởng. Ở **proxy biên tin cậy**, cần:
1. Xóa/ghi đè cả `Forwarded` **và** toàn bộ `X-Forwarded-*` nhận từ bên ngoài, kể cả các trường không dùng.
2. Ghi lại giá trị mới do proxy kiểm soát từ kết nối đáng tin (hostname được validate, client IP thực tế, TLS state).
3. Không cho bên ngoài bypass proxy để gửi thẳng vào backend.
4. Với nhiều lớp proxy, định nghĩa chính xác trusted hops/networks; không suy IP thật bằng cách lấy phần tử đầu tiên của `X-Forwarded-For` do bất kỳ nguồn nào gửi.

**`X-Forwarded-For` từ Nginx:** `$proxy_add_x_forwarded_for` *nối thêm* địa chỉ client vào header `X-Forwarded-For` có sẵn. Nếu Nginx là **edge proxy trực tiếp Internet** và chưa dọn header đầu vào, phép nối này có thể giữ giá trị giả mạo. Ở ví dụ Nginx phía dưới, dùng `X-Forwarded-For $remote_addr` để **ghi đè** chuỗi từ browser. Nếu Nginx ở sau CDN/LB đáng tin, cấu hình trust chain bằng `real_ip_header`/`set_real_ip_from` hoặc cơ chế của hạ tầng trước khi xây lại IP, không suy đoán rằng `$remote_addr` luôn là IP browser.

## 4. Spring Boot: NATIVE, FRAMEWORK và NONE

Spring Boot 3.3 hỗ trợ:

```yaml
server:
  forward-headers-strategy: framework
```

Các giá trị:

| Chiến lược | Cơ chế | Lưu ý |
| --- | --- | --- |
| `none` | Không xử lý forwarded metadata theo Boot strategy | Dùng khi không có reverse proxy tin cậy hoặc không muốn tin metadata |
| `native` | Dùng chức năng của web server/container nền (Tomcat, Jetty, Reactor Netty tùy stack) | Behavior/hỗ trợ các header phụ thuộc container |
| `framework` | Dùng cơ chế xử lý của Spring Framework | Servlet MVC dùng `ForwardedHeaderFilter`, reactive WebFlux dùng `ForwardedHeaderTransformer` |

Một số cloud platform được Boot nhận biết có thể mặc định `native`; môi trường khác thường là `none`. **Khai báo tường minh** để deployment dễ review. `framework` thường hữu ích khi muốn xử lý public prefix và URL/redirect thống nhất theo Spring; `native` phù hợp nếu hạ tầng đã dùng container-specific remote-IP/trust logic. Tránh để **cả container và filter của framework đều tự diễn giải cùng một tập header** khi chưa kiểm chứng.

**Servlet MVC:** `ForwardedHeaderFilter` đọc/áp dụng metadata cho request và một số thao tác response như `sendRedirect`. Có `setRemoveOnly(true)` khi chỉ muốn loại header giả mạo mà **không sử dụng** metadata.

**WebFlux:** `ForwardedHeaderTransformer` xử lý metadata trước lúc Spring tạo exchange cho handler; có `setRemoveOnly(true)` tương tự. Spring Boot có thể auto-configure theo `server.forward-headers-strategy=framework`; nếu tự đăng ký bean/filter tương ứng, tránh duplicate handling. Những lớp này **không xác thực proxy**: bảo đảm trust boundary ở lớp ingress/network vẫn là điều kiện bắt buộc.

### Kiểm tra path prefix và URL tuyệt đối

Giả sử public path là `https://app.example.test/portal/api/orders` nhưng Nginx strip `/portal` trước khi chuyển thành `/api/orders`. Nếu chỉ set `X-Forwarded-Proto` mà quên public prefix, các link sinh ra từ request có thể thiếu `/portal`. Hãy chọn **một** mô hình nhất quán:
- Proxy giữ nguyên `/portal` để backend biết application context path, hoặc
- Proxy strip prefix và cung cấp `X-Forwarded-Prefix: /portal` theo cấu hình trusted; backend framework hỗ trợ đúng version và application context.

Kiểm tra từng cấu hình bằng endpoint tự sinh URL và test redirect/`Location` thật. Không hard-code public URL trong mọi controller chỉ để chữa một forwarded-header policy sai. Với application phục vụ ở root path như ví dụ dưới, **không cần** `X-Forwarded-Prefix`.

### Tomcat redirect context root

Với Spring MVC trên Tomcat, khi terminate SSL tại proxy và có context path, tài liệu Spring Boot lưu ý `server.tomcat.redirect-context-root=false` để `X-Forwarded-Proto` được xét trước khi container tự chuyển hướng thêm dấu `/`. Chỉ áp dụng khi thực sự dùng Tomcat/context-root redirect, không bắt buộc cho mọi ứng dụng.

## 5. Nginx: HTTPS, sanitize header, streaming và WebSocket Upgrade

Ví dụ Nginx cấu hình cho **một edge reverse proxy** nhận TLS công khai, upstream Spring nghe HTTP ở `127.0.0.1:8080` trên cùng host. Đây là cấu hình tham khảo, cần thay domain `app.example.test` và certificate paths bằng hạ tầng thật. Giá trị `proxy_read_timeout` là **khoảng không nhận byte liên tiếp** từ upstream, không phải deadline tổng response.

```nginx
# Đặt bên trong http { ... }.
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}

server {
    listen 80;
    server_name app.example.test;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name app.example.test;

    ssl_certificate     /etc/nginx/certs/fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;

        # Chỉ tin thông tin do edge proxy tạo.
        proxy_set_header Host $host;
        proxy_set_header Forwarded "";
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Ssl "";
        proxy_set_header X-Forwarded-Prefix "";

        # WebSocket: hop-by-hop headers cần chuyển rõ ràng.
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;

        proxy_connect_timeout 5s;
        proxy_send_timeout 75s;
        proxy_read_timeout 75s;

        # Mặc định có thể buffer response thông thường.
        # Endpoint SSE trong ví dụ trả X-Accel-Buffering: no.
    }
}
```

**Đừng copy nguyên khối mà chưa xem trust/network/TLS:** certificate thực phải tồn tại/khớp host, firewall không cho client bypass backend, Nginx chỉ nhận các host được phép, và cần chính sách thêm nếu deploy sau CDN/ingress khác. Nếu `proxy_set_header Host $host` không đủ cho application dùng public port tùy biến, xác định chính sách port/host phù hợp. Cấu hình Nginx thay đổi theo phiên bản; HTTP/2 phía client không nhất thiết đồng nghĩa WebSocket upstream sẽ dùng HTTP/2. Snippet sử dụng đường **HTTP/1.1 Upgrade** cho backend.

### WebSocket cần Upgrade/Connection

`Upgrade` và `Connection` là hop-by-hop headers: proxy HTTP thường không chuyển chúng lên backend nếu không đặt lại. Để tạo tunnel WebSocket qua reverse proxy, client gửi handshake HTTP/1.1 với `Upgrade: websocket`; upstream trả `101 Switching Protocols`; proxy duy trì kết nối hai chiều lâu dài.

Các dòng quan trọng:

```nginx
proxy_http_version 1.1;
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection $connection_upgrade;
proxy_read_timeout 75s;
```

Trong `map`: request có `Upgrade` sẽ đặt `Connection: upgrade`, request không có sẽ đặt `Connection: close` theo ví dụ chính thức Nginx (không phải tối ưu cho upstream keep-alive mọi loại request, nhưng rõ ràng và dễ học). Với cấu hình HTTP/1.1 khác, có thể tối ưu keep-alive và routing cho request thường, miễn đảm bảo handshake WebSocket vẫn đúng.

**Giữ kết nối:** Nginx mặc định đóng tunnel nếu upstream không gửi gì trong khoảng `proxy_read_timeout` (mặc định tài liệu Nginx thường 60 giây). Tăng read idle timeout hoặc để backend gửi ping/pong/WebSocket heartbeat thường xuyên đủ để tránh proxy đánh giá idle. `proxy_read_timeout=75s` không có nghĩa mọi WebSocket connection chỉ tồn tại 75 giây; có traffic kịp thời thì kết nối có thể kéo dài.

**STOMP trên WebSocket:** ngoài tunnel, broker/app phải có heartbeat/session cleanup và subscription management phù hợp. Thay route raw `/ws` bằng STOMP handshake endpoint không làm quy tắc proxy đổi căn bản; SockJS fallback còn có HTTP polling/streaming paths, nên cần route tất cả đường dẫn tương ứng nếu dùng SockJS.

### SSE/streaming bị buffer

Nginx proxy buffering (thường mặc định bật) có thể nhận dữ liệu upstream trước rồi phát cho browser theo chính sách buffer, khiến `EventSource`/`curl -N` không thấy từng event ngay. Với endpoint cụ thể, có thể:

- Tắt proxy buffering trong `location /events`: `proxy_buffering off;`, hoặc
- Để ứng dụng trả header `X-Accel-Buffering: no` cho response SSE để Nginx không buffer response này (miễn không bật `proxy_ignore_headers` bỏ qua header). Đây là lựa chọn của ví dụ bên dưới.
- Kiểm tra gzip/Brotli hoặc compression ở các tầng proxy/CDN, vì chúng cũng có thể giữ data trước khi flush.
- Đảm bảo controller **flush** sự kiện ra response thực, có content type `text/event-stream`, và gửi comment heartbeat nếu stream dài.

Đừng tắt buffering toàn bộ API mặc định nếu chỉ vài endpoint streaming cần latency thấp; điều đó có thể làm thay đổi throughput và memory behavior của proxy. Với nhiều proxy/CDN layers, phải tắt buffering tại **mọi layer ảnh hưởng**; thay đổi một Nginx chưa bảo đảm CDN phía trước truyền từng chunk.

## 6. Ví dụ Spring Boot chạy được: scheme, redirect, SSE và WebSocket

Ta xây ứng dụng Spring MVC nhỏ, gửi traffic **trực tiếp localhost HTTP** khi develop, sau đó chạy sau Nginx HTTPS theo mô hình phần 5. Các endpoint:

| Endpoint | Mục đích |
| --- | --- |
| `GET /diag` | Cho thấy scheme, host, secure, URL mà Spring nhận sau forwarded processing |
| `GET /go` | Redirect đến absolute URL `/status` để kiểm tra `Location` |
| `GET /status` | Kết quả đơn giản cho redirect |
| `GET /events` | Phát 3 SSE events, flush qua `SseEmitter` và đặt `X-Accel-Buffering: no` |
| `GET /ws` Upgrade | Raw WebSocket echo để kiểm tra `101 Switching Protocols` và đường hai chiều |

Cấu trúc:

```text
reverse-proxy-demo/
├── settings.gradle
├── build.gradle
└── src/main/
    ├── java/demo/ProxyDemoApplication.java
    └── resources/application.yml
```

**`settings.gradle`**

```groovy
rootProject.name = 'reverse-proxy-demo'
```

**`build.gradle`** — Java 17+, Gradle 8.x:

```groovy
plugins {
    id 'org.springframework.boot' version '3.3.13'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'java'
}

group = 'demo'
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
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
}
```

**`src/main/resources/application.yml`**

```yaml
server:
  port: 8080
  forward-headers-strategy: framework
```

Chỉ bật đọc forwarded headers khi thực sự có **trusted proxy boundary**. Nếu server development mở công khai, bất kỳ client nào gọi trực tiếp `localhost:8080` qua port forwarded/NAT đều có thể tự đặt header; đây là lý do phải ràng buộc backend vào mạng nội bộ hoặc firewall chặn truy cập không qua proxy.

**`src/main/java/demo/ProxyDemoApplication.java`**

```java
package demo;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@SpringBootApplication
public class ProxyDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProxyDemoApplication.class, args);
    }

    @Bean("sseTaskExecutor")
    ThreadPoolTaskExecutor sseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("demo-sse-");
        return executor;
    }
}

@RestController
class DemoController {
    private final TaskExecutor taskExecutor;

    DemoController(@Qualifier("sseTaskExecutor") TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    @GetMapping("/diag")
    Map<String, Object> diag(HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scheme", request.getScheme());
        result.put("secure", request.isSecure());
        result.put("host", request.getServerName());
        result.put("port", request.getServerPort());
        result.put("url", request.getRequestURL().toString());
        return result;
    }

    @GetMapping("/status")
    Map<String, String> status() {
        return Map.of("status", "ok");
    }

    @GetMapping("/go")
    ResponseEntity<Void> redirect() {
        URI target = ServletUriComponentsBuilder
            .fromCurrentContextPath()
            .path("/status")
            .build()
            .toUri();
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(target)
            .build();
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(30_000L);

        taskExecutor.execute(() -> {
            try {
                for (int i = 0; i < 3; i++) {
                    emitter.send(SseEmitter.event()
                        .id(Integer.toString(i))
                        .name("tick")
                        .data("event-" + i));
                    Thread.sleep(1_000);
                }
                emitter.complete();
            } catch (IOException disconnected) {
                // Client đã rời đi; container/Spring xử lý I/O error.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                emitter.complete();
            }
        });
        return emitter;
    }
}

@Configuration
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new EchoHandler(), "/ws");
        // Mặc định kiểm tra Origin cho same-origin request.
    }

    static final class EchoHandler extends TextWebSocketHandler {
        @Override
        protected void handleTextMessage(WebSocketSession session,
                                         TextMessage message) throws IOException {
            session.sendMessage(new TextMessage("echo: " + message.getPayload()));
        }
    }
}
```

**Giải thích các điểm cần kiểm chứng:**
- `/diag` đọc request đã qua `ForwardedHeaderFilter` (nếu có header đúng và Boot strategy là `framework`), giúp quan sát public scheme/host, nhưng endpoint này chỉ nên bật ở môi trường kiểm tra/được bảo vệ vì có thể lộ topology.
- `/go` tạo absolute `Location` dựa trên request đã được phục hồi, thay vì ghép hard-coded `http://localhost:8080`.
- `/events` trả `SseEmitter` với `Content-Type: text/event-stream`, flush các event qua Spring MVC, đặt `X-Accel-Buffering: no` để Nginx không buffer response cụ thể. Vòng lặp 3 event hữu hạn giúp kiểm tra dễ; production SSE lâu dài cần cleanup/heartbeat, quản lý listener và threading tốt hơn. Pool executor nhỏ ở đây chỉ phục vụ demo; nếu nhiều stream đồng thời, cần thiết kế non-blocking/scheduled fan-out thích hợp, không giữ một thread chờ cho mỗi client vô hạn.
- `/ws` dùng Spring WebSocket raw echo. Ở môi trường production, cần xác thực, authorization, kiểm soát Origin/CSRF theo giao thức và policy gửi đồng thời; mẫu chỉ minh họa transport Upgrade.
- `@EnableWebSocket` cần `spring-boot-starter-websocket`, còn `SseEmitter` của Spring MVC chạy trên servlet async. Đừng trộn nguyên phần servlet code này vào ứng dụng chỉ dùng WebFlux/Netty rồi kỳ vọng compile.

Chạy local:

```bash
gradle bootRun
```

Kiểm tra trực tiếp (trên máy chạy server):

```bash
curl -i http://localhost:8080/status
curl -i http://localhost:8080/go
curl -N -i http://localhost:8080/events
curl -s http://localhost:8080/diag
```

**Kết quả mong đợi ở local, không gắn forwarded header**:
- `/status`: HTTP 200, JSON có `"status":"ok"`.
- `/go`: HTTP 302, `Location: http://localhost:8080/status` (host có thể tương ứng URL thực tế).
- `/events`: status 200 và các event `id:0`, `event:tick`, `data:event-0`, sau đó các event 1, 2 theo khoảng 1 giây và stream kết thúc.
- `/diag`: trường `scheme` là `http`, `secure` false, host `localhost` hoặc host request thực tế.

Các command kiểm tra trên là hành vi dự kiến; transcript raw HTTP phụ thuộc headers mặc định và phiên bản.

Sau khi có Nginx HTTPS hợp lệ tại domain thực, gọi:

```bash
curl -i https://app.example.test/go
curl -N -i https://app.example.test/events
curl -s https://app.example.test/diag
```

Kỳ vọng `/diag` cho `scheme=https`, `secure=true` và public host. `Location` của `/go` phải bắt đầu bằng `https://app.example.test/`. Với `curl -N`, ba event được nhận lần lượt, không dồn ở cuối khi proxy buffering đã được điều chỉnh đúng. Địa chỉ `app.example.test` là domain minh họa, phải thay bằng tên DNS thực tế có chứng chỉ.

### Kiểm tra WebSocket

Trên trình duyệt đang mở trang cùng public origin:

```javascript
const socket = new WebSocket("wss://app.example.test/ws");
socket.onopen = () => socket.send("hello");
socket.onmessage = (event) => console.log(event.data);
socket.onerror = (error) => console.error(error);
socket.onclose = (event) => console.log("closed:", event.code);
```

Với domain thực được cấu hình, console sẽ nhận `echo: hello`. DevTools Network → WS sẽ hiện handshake `101 Switching Protocols` và WebSocket frames. Trong thử nghiệm local có thể dùng `ws://localhost:8080/ws` từ một trang cùng origin local; với trang HTTPS, phải dùng `wss://` vì browser chặn mixed active content `ws://`.

**Lưu ý về Origin:** browser WebSocket có thể gửi `Origin`; Spring mặc định có same-origin checking. Khi kiểm thử bằng console từ origin khác, handshake có thể bị từ chối dù Nginx Upgrade hoàn toàn đúng. Không chữa bằng `setAllowedOrigins("*")` trong ứng dụng có xác thực/session; kiểm tra đúng public origin, cookie auth và Origin policy.

## 7. Streaming trong Servlet MVC và WebFlux: flush, heartbeat, backpressure

Cả MVC và WebFlux có thể phát SSE/stream, nhưng mô hình I/O khác:

| Spring MVC Servlet | Spring WebFlux |
| --- | --- |
| `SseEmitter` / `ResponseBodyEmitter` / `StreamingResponseBody` | `Flux<ServerSentEvent<T>>` / `Flux<T>` / `DataBuffer` |
| Servlet async; việc ghi response có thể blocking | Mô hình non-blocking trên server reactive phù hợp |
| Cần `AsyncTaskExecutor` production phù hợp | Tránh blocking event loop; dùng scheduler chỉ khi cần |
| Cleanup `onCompletion`/`onTimeout`/`onError` ở `SseEmitter` | Cleanup qua `doFinally(SignalType.CANCEL)` / resource scope |
| Heartbeat giúp phát hiện client disconnect khi còn kết nối HTTP dài hạn | Tương tự; cancel/transport error phản ánh đóng luồng tùy network |

Ví dụ WebFlux (chỉ **minh họa**, không chèn vào ứng dụng MVC ở phần 6):

```java
@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> events() {
    return Flux.interval(Duration.ofSeconds(10))
        .map(i -> ServerSentEvent.<String>builder()
            .id(Long.toString(i))
            .event("heartbeat")
            .data("tick-" + i)
            .build())
        .doFinally(signal -> log.info("SSE ended: {}", signal));
}
```

Cần imports `GetMapping`, `MediaType`, `ServerSentEvent`, `Flux`, `Duration` và logger do app khai báo; đoạn không độc lập. **Không gọi `block()` trên reactive event-loop**. Khi browser/người dùng cancel hoặc proxy đóng stream, publisher có thể nhận cancellation. Nhưng hủy subscription không phải lời hứa mọi network/socket close xảy ra ngay đúng thời điểm nếu có proxy layers.

**Heartbeat/flush:** một stream liên tục nhưng lâu không phát byte có thể bị Nginx, LB hoặc CDN đóng do `read idle timeout`. Gửi SSE comment ping định kỳ (ví dụ `: ping` rồi dòng trắng), hoặc event heartbeat có tên tùy contract; nhịp phải ngắn hơn idle timeout **với biên độ dự phòng cho jitter**. Đừng gửi heartbeat quá nhanh cho hàng trăm nghìn client mà chưa đánh giá CPU và bandwidth.

**Compression:** gzip/Brotli có thể làm buffering tăng do encoder đợi đủ dữ liệu. Với `text/event-stream`, cần kiểm tra setting server/proxy/CDN; nếu cần độ trễ thấp, loại trừ SSE khỏi compression ở các tầng gây delay.

**Chunked và HTTP/2:** streaming không có nghĩa phải hiện header `Transfer-Encoding: chunked` ở mọi client. HTTP/2 không dùng chunked transfer coding như HTTP/1.1; kiểm tra **thời điểm nhận các sự kiện và data framing**, không dựa vào một header truyền tải duy nhất.

**Tài nguyên và backpressure:** Spring có thể flush từng event, nhưng network chậm và nhiều client vẫn tạo memory/backpressure/threads. Giới hạn active streams, tốc độ publish, queue per subscriber, payload size và cleanup resource. Không dùng `collectList` với Flux không kết thúc. Với file download lớn, dùng streaming data, đo direct memory/heap và kiểm soát download concurrency.

## 8. WebSocket/STOMP sau proxy: lifecycle và scale

Một WebSocket bắt đầu bằng HTTP handshake rồi chuyển sang kết nối lâu dài. Những yêu cầu ngoài proxy cơ bản:

- **Forwarded public origin:** browser dùng `wss://` tương ứng HTTPS; ứng dụng kiểm tra Origin/auth/cookie và các redirect URL nếu có.
- **Upgrade:** Nginx phải forward `Upgrade`/`Connection` và dùng HTTP/1.1 cho hop đến backend khi áp dụng protocol Upgrade.
- **Idle timeout:** `proxy_read_timeout` và timeout tại ingress/LB phải đủ hoặc có ping/pong; không nhầm read timeout với tổng tuổi thọ session.
- **Scale nhiều instance:** raw WebSocket sessions gắn với node đang giữ TCP connection. Load balancer phải giữ connection sống; khi reconnect có thể sang node khác. Nếu dùng Spring STOMP simple broker in-memory, subscription không tự chia sẻ qua các node; cân nhắc external broker relay.
- **Authentication/session:** HTTPS termination không thay xác thực WebSocket. Browser WebSocket API không cho tùy ý đặt mọi custom header; cần thiết kế cookie hoặc token/subprotocol/handshake theo chính sách an toàn. Tránh đưa bearer token không kiểm soát vào URL/query/log.
- **SockJS fallback:** có thêm đường dẫn/info, XHR streaming/polling; proxy routing, CORS, buffering và sticky session requirement có thể khác native WebSocket.
- **Đóng kết nối:** gửi code close không phải lúc nào được nhận; khi mất mạng cần heartbeat và cleanup idempotent. Với STOMP, `SessionDisconnectEvent` có thể xuất hiện hơn một lần.

`101 Switching Protocols` chỉ chứng minh handshake/tunnel được thiết lập, **không** chứng minh publisher/subscription/auth nghiệp vụ hoạt động. Cần gửi một message thử, nhận phản hồi, quan sát reconnect/timeout và test node draining khi deploy rolling update.

## 9. Checklist xác minh theo từng tầng

| Kiểm tra | Cách thực hiện | Tiêu chí đạt |
| --- | --- | --- |
| TLS/certificate | `curl -I https://domain`, browser certificate inspector | Certificate hợp lệ, đúng hostname, không phải HTTP downgrade |
| Redirect HTTP → HTTPS | `curl -I http://domain/path` | `Location` public HTTPS đúng path/query, không loop |
| Scheme/host mà Spring thấy | Endpoint `/diag` được bảo vệ | `https`/public host/port đúng sau forwarded processing |
| Host spoofing | Kiểm tra proxy sanitize `Forwarded` và mọi `X-Forwarded-*` | Client ngoài không ép ứng dụng tạo URL đến host giả |
| SSE latency | `curl -N -i https://domain/events` và đo timestamps | Event đến dần, không bị dồn cuối |
| SSE timeout | Để idle vượt/ngắn hơn timeout có heartbeat phù hợp | Connection không chết ngoài dự kiến |
| WebSocket Upgrade | Browser DevTools Network → WS | Handshake `101`, có frame hai chiều |
| WebSocket idle/reconnect | Ngắt mạng, đóng tab, thử lại | Server cleanup, client reconnect đúng chính sách |
| Multi-node | Test 2+ replicas khi load balance | Routing/subscription, rolling restart đúng kỳ vọng |
| Resource use | Metrics, tracing, heap, active connections, queue | Không leak stream/socket, có limit và cleanup |

**Chẩn đoán thường gặp:**

1. `502/504` từ Nginx: upstream sai host/port/protocol, backend không sẵn sàng, TLS backend sai hoặc request vượt thời hạn; kiểm tra Nginx error log và health của Spring.
2. OAuth redirect / `Location` quay về HTTP: forwarded proto không được gửi hoặc Spring không áp dụng, hoặc public prefix/host bị xử lý sai.
3. “Too many redirects”: redirect HTTPS ở nhiều tầng không nhất quán, backend nghĩ request luôn HTTP; xét cả CDN/LB/Nginx/Spring và cấu hình trust.
4. Lộ internal host trong response: app sinh absolute URL từ host nội bộ; kiểm tra forwarded host/port/prefix và reverse-proxy behavior.
5. SSE trả đủ dữ liệu nhưng từng batch: proxy buffering, compression, app chưa flush hoặc CDN giữ chunk.
6. SSE bị đóng đúng 60 giây idle: kiểm tra `proxy_read_timeout` và heartbeat interval; timeout là khoảng không nhận byte, không phải max response length.
7. WebSocket `400`/`426`: header Upgrade hoặc Connection không được forward, sai HTTP version/hop, Origin/protocol mismatch.
8. WebSocket `403`: Origin/CORS/auth/security policy bị từ chối; không tắt policy mù quáng.
9. WebSocket `404`: sai path/ingress strip-prefix hoặc backend không đăng ký handshake endpoint.
10. WebSocket `101` rồi đóng nhanh: proxy idle timeout, heartbeat thiếu, app exception, session/resource limits hoặc node restart.
11. Client IP trong log là proxy: chưa cấu hình trusted chain hoặc web-server access log remote-IP behavior; không lấy IP từ header raw thiếu xác thực.
12. Nhiều streams gây CPU/RAM lớn: một blocking writer/thread mỗi client, queue vô hạn, payload lớn, không cleanup, kết nối đóng nhưng listener còn sống.

Khi debug, ghép **request ID/trace ID** giữa proxy access log, Spring log và client DevTools; ghi status, latency, upstream status, bytes sent, handshake errors, timeouts và connection closes. Tránh log cookies/Authorization/URL có secret, đặc biệt WebSocket handshake query.

## 10. Nguyên tắc triển khai production

- **Chỉ một nguồn gốc public URL đáng tin:** domain/hostname được validate tại ingress; proxy tạo forwarded metadata; Spring xử lý theo strategy nhất quán.
- **Phân quyền mạng:** backend/private service chỉ nhận connection từ proxy trusted; firewall/security groups/network policy là phần quan trọng nhất để ngăn forged forwarded headers.
- **TLS có lifecycle:** tự động gia hạn cert, kiểm tra chain/hostname, key permissions, policy redirect và HTTPS upstream nếu threat model cần.
- **Timeout phân lớp:** connect, send, read-idle, app async, broker/WebSocket heartbeat và request/business deadline là các đồng hồ khác nhau. Không dùng một con số cho tất cả.
- **Streaming có giới hạn:** buffering/compression chỉ tắt tại endpoint cần thấp latency; duy trì heartbeat và cleanup, đo slow consumer.
- **WebSocket vận hành có trạng thái:** quản lý auth, Origin, subscriptions, idle, node draining, reconnect, graceful deployment và session limits.
- **Giám sát sát với hành vi người dùng:** test trực tiếp qua public hostname và proxy thật; unit test/controller test không chứng minh TLS/headers/streaming network/Upgrade đúng.
- **Đừng bật proxy trust vô điều kiện:** ở staging/dev hay khi backend được gọi trực tiếp, forwarded headers có thể do attacker tự gửi nếu không có trust boundary. Có thể dùng `none` hoặc `removeOnly` trong môi trường không được bảo vệ.

## 11. Câu hỏi tự kiểm tra

1. Browser gọi HTTPS nhưng Spring trả `Location: http://localhost:8080/login`. Kiểm tra đầu tiên? → Proxy có gửi trusted `X-Forwarded-Proto/Host` và Spring có bật đúng `server.forward-headers-strategy` chưa; backend có bị truy cập trực tiếp không.
2. Tại sao `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for` có thể nguy hiểm ở edge Internet? → Nó có thể nối tiếp giá trị client đã giả mạo thay vì thay bằng IP đáng tin.
3. `framework` và `native` khác nhau ở đâu? → Một bên dùng cơ chế Spring `ForwardedHeaderFilter`/`Transformer`, một bên dùng container/network engine; không nên xử lý trùng khi chưa kiểm tra.
4. SSE hiển thị ba event cùng lúc sau ba giây dù server gửi từng giây. Cần tìm ở đâu? → Buffering/compression ở Nginx/CDN hoặc app chưa flush, không chỉ chỉnh `SseEmitter`.
5. `proxy_read_timeout 75s` có cấm WebSocket hoạt động hơn 75s không? → Không. Đây là read idle interval, có traffic/heartbeat thì kết nối dài hạn.
6. WebSocket handshake trả `101` nhưng không nhận STOMP MESSAGE. Đã chắc proxy đúng toàn bộ chưa? → Chưa; cần kiểm tra STOMP CONNECT/SUBSCRIBE, auth, broker routing và event semantics.
7. Với deployment nhiều replica, client reconnect tự khôi phục subscriptions của simple broker? → Không; session và subscription in-memory cần tái lập hoặc kiến trúc broker phù hợp.
8. Vì sao không thể tin thẳng `X-Forwarded-Host` từ browser? → Attacker có thể làm lệch absolute URLs/redirect/cookie logic; proxy trusted phải sanitize.
9. Khi test `curl -N` nhận event nhanh nhưng browser không nhận, cần xem thêm gì? → Browser cache/compression/CDN/EventSource parsing/response media type/CORS, cùng mọi layer mạng.
10. Khi proxy terminate TLS, kết nối proxy → backend là HTTP có phải luôn không an toàn? → Phụ thuộc mạng tin cậy và threat model; nếu cần bảo mật hop nội bộ, dùng HTTPS verify upstream hoặc mTLS.

## 12. Tài liệu chính thức

- Spring Boot 3.3 — Running Behind a Front-end Proxy Server: https://docs.spring.io/spring-boot/3.3/how-to/webserver.html#howto.webserver.use-behind-a-proxy-server
- Spring Framework 6.x — Forwarded Headers (Servlet): https://docs.spring.io/spring-framework/reference/web/webmvc/filters.html#filters-forwarded-headers
- Spring Framework 6.x — Forwarded Headers (WebFlux): https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html#webflux-forwarded-headers
- Spring Framework 6.x — Asynchronous Requests, SSE, disconnects: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- Spring Framework 6.x — WebSocket Handshake: https://docs.spring.io/spring-framework/reference/web/websocket/server.html
- Nginx — WebSocket proxying: https://nginx.org/en/docs/http/websocket.html
- Nginx — proxy buffering, `X-Accel-Buffering` và `proxy_read_timeout`: https://nginx.org/en/docs/http/ngx_http_proxy_module.html
- RFC 7239 — Forwarded HTTP Extension: https://www.rfc-editor.org/rfc/rfc7239


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="operations-practice-006">Giới hạn tài nguyên, timeout, ngắt kết nối và dừng ứng dụng an toàn</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn tài nguyên, timeout, ngắt kết nối và dừng ứng dụng an toàn trong Spring Web

## 1. Mục tiêu: hệ thống hoạt động đúng cả khi quá tải và khi kết nối bị cắt

Một HTTP endpoint không chỉ cần trả JSON đúng lúc lưu lượng bình thường. Trong vận hành, hệ thống còn phải biết xử lý các trường hợp:

- Nhiều request đồng thời hơn số thread, connection hoặc phiên làm việc mà máy chủ xử lý được.
- Client gửi một request quá lớn, upload tệp lớn hoặc giữ kết nối chậm.
- HTTP call sang dịch vụ khác bị chậm hoặc mất kết nối; một request tưởng nhỏ giữ worker quá lâu.
- Browser đóng tab, mobile mất mạng, reverse proxy hết hạn idle timeout trong lúc server còn xử lý.
- Pod/process nhận tín hiệu dừng để triển khai phiên bản mới, nhưng vẫn còn request đang chạy.
- Một stream SSE hoặc WebSocket sống lâu, cần kết thúc và giải phóng tài nguyên khi client đi xa.

Bốn nhóm kiến thức cần phối hợp là **resource limits**, **timeouts**, **disconnect/cancellation** và **graceful shutdown**. Giải pháp an toàn không phải chỉ tăng số thread, tăng timeout hay cho phép upload vô hạn: các thay đổi đó có thể chuyển tắc nghẽn từ HTTP layer sang database, memory, thread pool hoặc service phụ thuộc.

Bài dùng chủ yếu **Spring Boot 3.5.x, Spring Framework 6.2.x, Java 17+**. Phân biệt **Spring MVC trên Servlet/Tomcat** và **Spring WebFlux trên Reactor Netty** khi thuộc tính hay lifecycle có khác biệt. Những số cấu hình là ví dụ để học, phải đo tải và điều chỉnh theo hạ tầng thực tế.

### Mô hình đường đi của request

~~~text
Client/browser/mobile
       |
  Load balancer / ingress / reverse proxy
       |     (max request, keep-alive, idle timeout)
       v
  HTTP listener (Tomcat / Reactor Netty)
       |     (connections, backlog, event loop / worker threads)
       v
  DispatcherServlet (MVC) hoặc DispatcherHandler (WebFlux)
       |     (request body, codec, memory, validation)
       v
  Controller + business work
       |     (concurrency, queues, CPU, cancellation)
       +----> DB connection pool
       |
       +----> RestClient / WebClient -> remote API
       |           (connect, read, response, total timeout)
       v
  HTTP response / stream
       |
       v
  Client còn kết nối hoặc đã disconnect
~~~

Chỉ cần một tầng không có giới hạn, hệ thống có thể bị quá tải dù các tầng còn lại được cấu hình hợp lý.

## 2. Resource limits: cần giới hạn những tài nguyên nào?

| Tài nguyên | Rủi ro | Nơi kiểm soát thường gặp |
| --- | --- | --- |
| Số kết nối HTTP | Quá nhiều sockets, file descriptors, giữ kết nối nhàn rỗi | Tomcat/Netty, ingress, OS |
| Số request đang xử lý | Thread hoặc CPU bị giữ đến kiệt | Worker pool, semaphore, bulkhead |
| Queue chờ | Độ trễ không có trần, hết bộ nhớ | Executor queue, connection accept backlog, admission control |
| HTTP headers | Header quá lớn tiêu tốn bộ nhớ hoặc bị lợi dụng | server.max-http-request-header-size, proxy |
| Request body / upload | Disk/memory cạn, phải lưu dữ liệu rất lớn | Multipart limits, codec limits, proxy, file storage |
| JSON body | Decoder gom nhiều dữ liệu trong RAM | WebFlux codec maxInMemorySize, thiết kế stream |
| WebSocket/SSE sessions | Giữ socket và bộ đệm outbound lâu dài | Session limits, timeout/heartbeat, subscriber policy |
| Kết nối DB | Thread xếp hàng chờ DB, cascade failure | Connection pool max size và query timeout |
| HTTP client outbound | Quá nhiều kết nối gọi downstream, retry bão | HTTP pool, pending-acquire, concurrency limit |
| File/temp buffer | Temp disk cạn, rò tài nguyên | Temp directory quota, multipart cleanup, lifecycle |

**Bảo vệ nhiều tầng**: nếu ingress chặn upload lớn, ứng dụng vẫn nên có giới hạn của riêng mình. Nếu application giới hạn 20 business operations đồng thời, HTTP server vẫn cần giới hạn connections/threads và HTTP client outbound cần pool thích hợp.

### 2.1 Tomcat với Spring MVC

Một cấu hình minh họa trong **src/main/resources/application.properties**:

~~~properties
# Spring MVC sử dụng embedded Tomcat trong starter-web.
server.tomcat.threads.max=80
server.tomcat.max-connections=400
server.tomcat.accept-count=100

# HTTP request headers và multipart upload.
server.max-http-request-header-size=16KB
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=6MB

# Thời gian giữ kết nối nhàn rỗi hoặc chờ client gửi request line.
server.tomcat.keep-alive-timeout=20s
server.tomcat.connection-timeout=10s
~~~

Ý nghĩa cụ thể:
- **threads.max** giới hạn các worker thread của Tomcat kiểu truyền thống. Đây không phải giới hạn chính xác số request toàn ứng dụng nếu còn async request, virtual threads, background jobs hoặc side effects.
- **max-connections** giới hạn số connection mà server xử lý ở một thời điểm; HTTP keep-alive có thể giữ connection dù không có request đang thực thi.
- **accept-count** điều tiết backlog khi tài nguyên chấp nhận request đã bão hòa; không phải hàng đợi nghiệp vụ có thể giao tác.
- **max-http-request-header-size** bảo vệ header request, không áp dụng cho toàn bộ file upload; cách tính có khác nhau giữa Tomcat và Netty.
- **max-file-size/max-request-size** thuộc Servlet multipart parsing; không áp dụng trực tiếp cho WebFlux multipart hoặc mọi dạng application/json.
- **connection-timeout** và **keep-alive-timeout** thuộc giai đoạn kết nối/HTTP parser; chúng không tự đặt deadline cho business method chạy lâu.
- Khi dùng **virtual threads** của Java 21+ với cấu hình hỗ trợ, property threads.max của Tomcat có thể không tác động như pool truyền thống. Bài thực hành đang dùng Java 17 và worker threads thường.

Thông số quá thấp có thể từ chối request hợp lệ; quá cao có thể dồn thêm tải xuống DB/CPU, kéo dài tail latency. Tăng cùng lúc các chỉ số mà không đo thường làm khó tìm bottleneck.

### 2.2 WebFlux và codec limits

Ở Spring WebFlux, nhiều HTTP connection có thể được một số event-loop thread phục vụ; **không được block event loop** bằng Thread.sleep, JDBC đồng bộ hoặc xử lý CPU dài. Reactive Streams có backpressure, nhưng nó không tự giới hạn kích thước toàn bộ JSON, số concurrent subscriptions hoặc tổng thời gian sống của mọi stream.

Ví dụ tùy biến giới hạn giải mã trong WebFlux server:

~~~java
@Configuration
public class WebFluxCodecConfig implements WebFluxConfigurer {
    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().maxInMemorySize(256 * 1024);
    }
}
~~~

Imports cần dùng trong fragment:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;
~~~

**maxInMemorySize** giới hạn lượng dữ liệu mà decoder cần buffer cho một object/chunk tùy codec. Với một stream NDJSON, giới hạn có thể áp dụng trên mỗi record cần giải mã; nó **không là giới hạn total bytes mọi response**. Nếu codec vượt giới hạn, có thể nhận **DataBufferLimitException**. Đừng tăng lên hàng trăm MB chỉ để hợp thức hóa kiểu giải mã không phù hợp.

Đối với multipart WebFlux, giới hạn **non-file part**, ngưỡng ghi file part ra disk, dung lượng file trên disk và tổng số part là các khái niệm khác nhau. Ví dụ thuộc tính Spring Boot 3.5:

~~~properties
spring.webflux.multipart.max-in-memory-size=256KB
spring.webflux.multipart.max-disk-usage-per-part=20MB
spring.webflux.multipart.max-parts=20
~~~

Đọc và xác minh theo đúng version của Boot và bộ multipart reader. Nếu ứng dụng sử dụng Servlet multipart thay vì WebFlux, hãy cấu hình **spring.servlet.multipart.***. Đảm bảo thư mục tạm có giới hạn và cleanup; giới hạn codec không thay cho antivirus, file type validation, quota của tài khoản hoặc storage policy.

### 2.3 Chặn tải ở tầng nghiệp vụ bằng semaphore/bulkhead

Số connection HTTP không phản ánh mức tài nguyên nghiệp vụ: một request gọi DB 5 lần có thể đắt hơn hàng trăm request static. Đặt một concurrency limit ở đoạn công việc cần bảo vệ, trả thông báo quá tải ngay khi không còn chỗ:

~~~java
private final Semaphore permits = new Semaphore(8, true);

@GetMapping("/process")
public ResponseEntity<?> process() {
    if (!permits.tryAcquire()) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, "1")
            .build();
    }

    try {
        return ResponseEntity.ok(service.performWork());
    } finally {
        permits.release();
    }
}
~~~

Fragment trên thuộc một controller có **service** đã được inject và imports Spring/Java tương ứng. **tryAcquire()** không chờ, giúp hệ thống từ chối sớm thay vì đẩy request vào queue vô hạn. **finally** bảo đảm trả permit cả khi business method ném exception. Dùng **503 Service Unavailable** hợp lý cho overload toàn hệ thống; **429 Too Many Requests** thường phù hợp giới hạn theo caller/rate policy. Chọn status và Retry-After theo hợp đồng.

**Cảnh báo về phạm vi:** semaphore trong một JVM chỉ giới hạn instance đó. Với nhiều pod, giới hạn toàn hệ thống cần điều phối cùng load balancer/quota hoặc distributed limit. Đừng cho rằng mỗi pod có 8 permits tương đương toàn cluster tối đa 8 operations.

## 3. Timeout là một họ cơ chế, không phải một nút bấm

| Timeout/deadline | Đo từ đâu | Khi hết thời gian | Nơi cấu hình |
| --- | --- | --- | --- |
| TCP connect timeout | Bắt đầu thiết lập kết nối | Không kết nối được | HTTP client connector |
| Connection-pool acquire timeout | Chờ lấy connection từ pool | Không lấy được slot | HTTP client pool |
| Read/response timeout | Chờ nhận response/data theo cơ chế connector | Đóng/hủy request outbound, error signal | RestClient/WebClient connector |
| Tổng thời hạn nghiệp vụ | Bắt đầu operation đến deadline | Hủy hoặc báo timeout toàn chuỗi | Business layer, Reactor timeout, request context |
| Servlet async timeout | Request async bắt đầu | Spring/Servlet dispatch lỗi timeout | spring.mvc.async.request-timeout, DeferredResult |
| Idle/keep-alive timeout | Kết nối rỗi không có request/bytes | Kết nối có thể đóng | Tomcat/Netty, proxy |
| Proxy upstream timeout | Gateway chờ backend | Gateway có thể trả 504 hoặc đóng | Reverse proxy/ingress |
| Graceful shutdown timeout | Application bắt đầu dừng | Giai đoạn chờ hoàn tất kết thúc | spring.lifecycle.timeout-per-shutdown-phase |

**Không đánh đồng các timeout**: **server.tomcat.connection-timeout=10s** không có nghĩa một API có quyền chạy tối đa 10s. **spring.mvc.async.request-timeout=10s** không tự giới hạn tất cả controller method đồng bộ. **WebClient responseTimeout(2s)** là chính sách ở HTTP client, khác deadline tổng của 5 bước nghiệp vụ.

Nếu browser có deadline 3 giây nhưng API gọi ba service mỗi service với read timeout 10 giây tuần tự, response có thể xuất hiện quá muộn. Thiết kế thời gian từ ngoài vào trong:

~~~text
Client deadline (ví dụ 5s)
        |
Gateway deadline (ví dụ 4.5s)
        |
API business budget (ví dụ 4s)
        |
DB + downstream calls + serialization
        |
Phần dự phòng cho trả response/cleanup
~~~

Các số trên chỉ minh họa quan hệ; phải tính cả network latency và thời gian xếp hàng. Phân phối một **deadline tổng** cho các call con tốt hơn cấu hình mỗi call với timeout riêng mà không xét ngân sách còn lại.

## 4. Timeout cho RestClient đồng bộ

Với **RestClient** sử dụng JDK HttpClient (Java 17), cấu hình connect timeout trên HttpClient và read timeout qua **JdkClientHttpRequestFactory**:

~~~java
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

HttpClient jdkClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(2))
    .build();

JdkClientHttpRequestFactory factory =
    new JdkClientHttpRequestFactory(jdkClient);
factory.setReadTimeout(Duration.ofSeconds(4));

RestClient client = RestClient.builder()
    .baseUrl("https://catalog.example.org")
    .requestFactory(factory)
    .build();
~~~

Đây là **configuration fragment**, cần có remote server thật khi gọi. **connectTimeout** liên quan đến TCP establishment; **setReadTimeout** với factory này đặt timeout ở request HTTP mà factory tạo (phù hợp Spring 6.2). HTTP client khác, ví dụ Apache HttpComponents hoặc Jetty, có API cho pool lease, connect và response timeout riêng: không sao chép nhầm thuộc tính giữa chúng.

Trong servlet thread đồng bộ, một lời gọi RestClient bị treo có thể giữ một Tomcat worker. Vì vậy **mọi outbound call đáng kể nên có timeout hữu hạn**, quan sát được và phù hợp SLA. Nếu xảy ra lỗi, xử lý bằng exception mapping; không tự động retry mọi POST có side effect.

### Timeout của một nghiệp vụ nhiều bước

Ví dụ workflow phải hỏi cả profile và inventory. Nếu tổng ngân sách 3 giây, không nên cho mỗi call 3 giây nối tiếp. Một mẫu design là truyền **Instant deadline** hoặc **Duration remainingBudget** xuống các client method và điều chỉnh timeout thực tế cho bước tiếp theo. Với blocking calls, hủy thực thi cần khả năng interrupt/cancel từ client và service; **Future.cancel(true)** là yêu cầu ngắt cooperative, không bảo đảm mọi I/O ngừng ngay lập tức.

**Tránh** tạo new Thread hoặc ExecutorService không giới hạn mỗi request để "ép timeout". Chúng chỉ đẩy số công việc còn sống ra khỏi request thread, khiến shutdown và leak trở nên phức tạp.

## 5. Timeout cho WebClient dùng Reactor Netty

Với WebClient, có thể cấu hình HTTP client connector:

~~~java
import java.time.Duration;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

HttpClient httpClient = HttpClient.create()
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
    .responseTimeout(Duration.ofSeconds(4));

WebClient webClient = WebClient.builder()
    .baseUrl("https://catalog.example.org")
    .clientConnector(new ReactorClientHttpConnector(httpClient))
    .build();
~~~

**CONNECT_TIMEOUT_MILLIS** giới hạn thiết lập kết nối. **responseTimeout** của Reactor Netty gắn với hành vi nhận response, không tự bảo đảm mọi logic trước/sau network và mọi thao tác DB hoàn thành trong một tổng deadline.

Khi cần timeout cho **toàn reactive pipeline**:

~~~java
Mono<Book> result = webClient.get()
    .uri("/api/books/{id}", 7)
    .retrieve()
    .bodyToMono(Book.class)
    .timeout(Duration.ofSeconds(5));
~~~

**Book** là DTO đã định nghĩa ở ứng dụng. Reactor **timeout** phát lỗi nếu nguồn không phát tín hiệu đúng hạn theo semantics của operator; việc hủy subscription có thể truyền xuống HTTP connector và ngừng I/O. Nhưng task blocking do code tự chạy bên ngoài reactive pipeline có thể không tự dừng. Với **Flux** nhiều phần tử, timeout operator thường quan tâm khoảng chờ giữa các signals chứ không tương đương thời hạn tuyệt đối từ đầu tới cuối; nếu cần fixed total deadline, thiết kế deadline rõ ràng.

### Pool và connection saturation

WebClient mặc định có thể dùng các tài nguyên Reactor Netty dùng chung (event loops, connection pool). Khi tải cao, cần quan sát số **active/idle/pending connections**, acquire latency và timeout. Nếu tự tạo **ConnectionProvider** với **maxConnections**, **pendingAcquireMaxCount**, **pendingAcquireTimeout**, phải dùng singleton/shared provider hợp lý và đóng nó theo application lifecycle. Không tạo một connection pool mới cho mỗi request.

Một request outbound fail do pool acquire timeout khác với DNS/connect timeout và khác response timeout. Sự phân biệt này giúp biết cần mở pool, hạn chế lưu lượng, rút ngắn downstream, hay tăng tài nguyên.

## 6. Spring MVC: timeout trong request bất đồng bộ

Spring MVC có Servlet async processing thông qua **Callable<T>**, **DeferredResult<T>**, **WebAsyncTask<T>**, **SseEmitter** và **ResponseBodyEmitter**. Nó giải phóng servlet thread trong lúc chờ kết quả, nhưng một số công việc vẫn có thể chạy trên executor và các lần ghi response streaming là blocking I/O.

Cấu hình mặc định async request timeout:

~~~properties
spring.mvc.async.request-timeout=10s
~~~

Thuộc tính này áp dụng **Servlet MVC asynchronous request processing**, không áp dụng cho một controller đồng bộ bình thường hoặc toàn WebFlux. Nếu không đặt, default timeout phụ thuộc Servlet container/implementation.

Ví dụ một operation với **DeferredResult** và timeout callback:

~~~java
@GetMapping("/async")
public DeferredResult<ResponseEntity<String>> async() {
    DeferredResult<ResponseEntity<String>> result =
        new DeferredResult<>(3_000L);

    result.onTimeout(() -> result.setErrorResult(
        new ResponseStatusException(
            HttpStatus.GATEWAY_TIMEOUT, "Upstream quá chậm")));

    service.startAsyncWork(result);
    return result;
}
~~~

Đoạn trên là **fragment minh họa** với **service** định nghĩa trong ứng dụng. **startAsyncWork** phải hoàn thành bằng **setResult(...)** hoặc **setErrorResult(...)**; không chờ đồng bộ ngay trong controller. Với DeferredResult timeout, setting error result làm Spring chạy async dispatch để xử lý lỗi. Callback **onCompletion** thích hợp để giải phóng dữ liệu/đăng ký liên quan; nhưng nó không đồng nghĩa hủy tức thời mọi background task.

Với **Callable**, có thể dùng **WebAsyncTask<T>** để cấu hình timeout riêng; đồng thời phải cấp **AsyncTaskExecutor** phù hợp. Spring MVC tài liệu cảnh báo default async executor không thích hợp cho production under load; nên cấu hình pool có giới hạn và backpressure rõ ràng nếu dùng async methods nhiều.

### Timeout thực sự hủy được công việc không?

Không mặc định. Một DeferredResult hết hạn chỉ hoàn tất phần HTTP-facing lifecycle; nếu bạn đã khởi chạy blocking DB call hoặc future không gắn cancellation thì nó có thể vẫn chạy. Thiết kế cancellation token, query timeout và task interruption ở các tầng liên quan, bảo đảm resource cleanup trong finally/onCompletion. Đừng để hàng ngàn công việc "mồ côi" tiếp tục sau khi client đã bỏ đi.

## 7. Phát hiện client ngắt kết nối trong Spring MVC

**Servlet API không cung cấp tín hiệu tức thời và đáng tin cậy cho mọi tình huống client ngắt kết nối.** Đặc biệt với response stream/SSE, server thường chỉ nhận biết khi cố ghi bytes lên socket và ghi bị lỗi. Một browser đóng tab không bảo đảm controller đồng bộ đang xử lý sẽ lập tức nhận exception.

Với **SseEmitter**, có thể đăng ký các callback:

~~~java
SseEmitter emitter = new SseEmitter(30_000L);

emitter.onCompletion(() -> {
    // Bỏ subscription, giải phóng tài nguyên của stream.
});

emitter.onTimeout(() -> {
    // Đánh dấu stream đã timeout, dọn dữ liệu liên quan.
});

emitter.onError(error -> {
    // Ghi metric phù hợp, không log stacktrace lặp vô hạn.
});
~~~

Code trên là **fragment** của API **org.springframework.web.servlet.mvc.method.annotation.SseEmitter**. Khi emit theo thời gian, gửi heartbeat SSE comment định kỳ để phát hiện client đã đi xa và tránh một số idle timeout:

~~~java
emitter.send(SseEmitter.event().comment("heartbeat"));
~~~

Lệnh send phải chạy trong logic streaming với lifecycle rõ ràng, xử lý **IOException** theo đúng quy ước. Theo Spring MVC reference, nếu **ResponseBodyEmitter/SseEmitter.send(...)** ném IOException do client rời đi thì servlet container sẽ kích hoạt error notification và Spring xử lý completion; application không cần tự gọi **complete()** hay **completeWithError()** ngay trong catch IOException. Tránh giữ emitter trong collection không có cơ chế remove để không rò memory.

Một mẫu quản lý subscription:

~~~text
Mở SSE: tạo emitter + register onCompletion/onTimeout/onError
        |
Đăng ký vào nguồn sự kiện
        |
Phát data hoặc heartbeat định kỳ
        |
Lỗi ghi / timeout / disconnect / server shutdown
        |
Ngừng subscription, xóa emitter khỏi registry, dọn task
~~~

**Heartbeat** không phải guarantee phát hiện disconnect trong mọi trạng thái mạng; nó giúp phát hiện bằng I/O. Khi nhiều người dùng SSE, tần suất heartbeat phải cân bằng giữa phát hiện nhanh và overhead.

## 8. WebFlux: cancellation, DataBuffer và tài nguyên reactive

Ở WebFlux, publisher trả cho framework đại diện vòng đời response:

~~~java
@GetMapping(value = "/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> events() {
    return eventSource.stream()
        .map(item -> ServerSentEvent.<String>builder()
            .data(item)
            .build())
        .doFinally(signal -> {
            // signal có thể là CANCEL, ON_COMPLETE, ON_ERROR.
            metrics.recordTermination(signal.name());
        });
}
~~~

Ví dụ là **fragment**: cần một **eventSource** và **metrics** đã định nghĩa/inject, import **Flux**, **ServerSentEvent**, **MediaType**, **@GetMapping**. Nếu client ngắt kết nối, hủy subscription có thể tạo **SignalType.CANCEL** ở chuỗi publisher. **doFinally** thích hợp cho metric và cleanup; không tự hiểu một **CANCEL** là lỗi nghiệp vụ hay tự ghi lại HTTP response khi socket không còn.

Các chú ý quan trọng:
- **Publisher cancellation** phải truyền đến upstream/resource owner. Nếu đã gọi **subscribe()** thủ công trong controller, framework khó quản lý subscription đó; nên return Flux/Mono.
- Khi tự sử dụng **DataBuffer**, cần tuân theo lifecycle: tiêu thụ, chuyển tiếp hoặc release buffer đúng API, nhất là với pooled buffers ở Netty. Tránh giữ DataBuffer bên ngoài request.
- **collectList()**, **cache()**, **buffer()** không giới hạn trên stream dài hạn là nguyên nhân gây tăng RAM; dùng xử lý theo phần tử và các toán tử bounded.
- Code **Thread.sleep**, JDBC đồng bộ và các thao tác block lâu trên Netty event-loop làm toàn bộ các connection dùng loop đó bị ảnh hưởng. Nếu buộc tích hợp API blocking, offload có kiểm soát bằng **Schedulers.boundedElastic()** và giới hạn concurrency; không coi offload là giải pháp thay việc chuyển sang API async phù hợp.
- Connection client đã đóng không có nghĩa background work vốn được submit cho executor tự bị hủy; quản lý task lifecycle và deadline ở tầng sở hữu công việc.

### Trường hợp response body lớn

Nếu endpoint trả hàng triệu record, đừng trả **Mono<List<BigRecord>>** chứa tất cả và tăng maxInMemorySize vô hạn. Khi contract cho phép, thiết kế stream NDJSON/SSE hoặc phân trang; giới hạn kích thước mỗi phần tử. Nếu bắt buộc trả một JSON array lớn, phải phân tích buffering, time-to-first-byte và giới hạn memory đúng codec/server/client.

## 9. Graceful shutdown: dừng server khác gì ngắt process ngay?

**Graceful shutdown** cho phép các request đang chạy có một khoảng thời gian hoàn tất trong lúc server ngừng nhận những request mới. Với Spring Boot **3.5**, graceful shutdown được **bật mặc định** cho embedded Tomcat, Jetty, Reactor Netty và Undertow; có thể khai báo tường minh để dễ đọc cấu hình:

~~~properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=20s
~~~

Spring thực hiện shutdown khi **ApplicationContext đóng**, trong một pha sớm của quá trình dừng **SmartLifecycle** beans. Thời gian cấu hình là **grace period cho mỗi pha** lifecycle, không phải một "request timeout = 20s" chung. Khi hết thời gian chờ, framework sẽ tiếp tục shutdown, nên công việc dài hơn chưa chắc hoàn tất.

### Timeline triển khai một pod

~~~text
T0: orchestrator yêu cầu instance dừng (SIGTERM)
      |
      +--> Load balancer/ingress cần ngừng route traffic mới
      |
      +--> Spring ApplicationContext bắt đầu đóng
              |
              +--> web server graceful shutdown:
              |       không nhận request mới theo cách của server
              |       đợi request hiện hữu trong grace window
              |
              +--> SmartLifecycle beans và tài nguyên khác dừng
              |
              +--> close data sources / HTTP client pools / executors
      |
T_deadline: hết shutdown grace hoặc bị hệ thống ngoài cưỡng bức
      |
      v
Container/process kết thúc
~~~

Cách từ chối request mới phụ thuộc server: Tomcat, Jetty, Reactor Netty thường ngừng nhận ở network layer; Undertow có thể trả **503** trong giai đoạn shutdown. Không viết test kỳ vọng **mọi server đều trả HTTP 503** khi graceful shutdown.

**SIGKILL**, process crash, mất điện, JVM bị kill cưỡng bức hay container hết termination grace không cho ứng dụng cơ hội cleanup đầy đủ. Trong IDE, nút Stop có thể kết thúc ngay tùy cách gửi tín hiệu. Do đó graceful shutdown là **best effort với deadline**, không phải cam kết hoàn tất mọi workflow hay đảm bảo exactly-once.

### Phải phối hợp với orchestrator

Trong Kubernetes hoặc hệ thống orchestrator tương tự:
- Đặt readiness false và ngừng định tuyến lưu lượng mới đến instance đang drain.
- Kiểm tra propagation delay của endpoint updates/proxy; đôi lúc request vẫn đang trên đường.
- Chọn **terminationGracePeriodSeconds** lớn hơn hoặc đủ bao quát cửa sổ Spring graceful shutdown, thời gian dọn tài nguyên, quá trình stop container và phần dự phòng.
- Đặt request timeout, downstream budget và shutdown window nhất quán. Nếu request hợp pháp có thể chạy 2 phút nhưng grace chỉ 20 giây, request đó có nguy cơ bị cắt.
- Với SSE/WebSocket stream dài hạn, cần chính sách yêu cầu reconnect hoặc đóng có kiểm soát; chờ stream vô hạn thì graceful window cũng không thể kết thúc một cách trọn vẹn.
- Nếu công việc phải survive restart, ghi vào durable job queue/database để instance khác tiếp tục; không giữ toàn bộ trạng thái quan trọng chỉ trong memory.

Một pod "đã đóng cổng" không đồng nghĩa job nền đã hoàn thành hoặc transaction đã commit. Đưa trạng thái công việc vào kho durable và thiết kế idempotency/transaction boundary thích hợp.

## 10. Dừng executor và tài nguyên do ứng dụng tự quản lý

Spring quản lý vòng đời nhiều bean và embedded server, nhưng nếu ứng dụng tự tạo ExecutorService, scheduled task, client connector, file handle hay message consumer thì cũng cần owner chịu trách nhiệm dọn.

Ví dụ **ThreadPoolTaskExecutor** có số thread/queue hữu hạn:

~~~java
@Configuration
public class WorkerPoolConfig {
    @Bean("workExecutor")
    public ThreadPoolTaskExecutor workExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("work-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        return executor;
    }
}
~~~

Imports: **org.springframework.context.annotation.Bean**, **Configuration**, **org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor**.

- **core/max pool size** hạn chế workers; **queueCapacity** hạn chế pending tasks.
- Khi pool/queue bão hòa, executor có thể từ chối task; caller phải xử lý **TaskRejectedException** hoặc policy tương ứng thay vì âm thầm chấp nhận công việc.
- **waitForTasksToCompleteOnShutdown(true)** yêu cầu executor chờ các task đang chạy/đã queue khi đóng, nhưng không tự bảo đảm đủ thời gian của toàn quá trình shutdown và các beans khác.
- **awaitTerminationSeconds(15)** giới hạn thời gian chờ của executor khi shutdown; cần cân đối với Spring lifecycle shutdown budget và orchestrator deadline.
- Nếu có công việc chạy vô hạn hoặc không tôn trọng interruption, shutdown vẫn có thể bị kéo dài/hết hạn. Thiết kế task có deadline, cancellation và checkpoint; dùng durable queue khi mất task là không chấp nhận được.

Đoạn này là **fragment cấu hình**, có thể được đưa vào ứng dụng Spring Boot. Không tự thêm một executor như trên nếu ứng dụng không thực sự cần xử lý bất đồng bộ. Trong một WebFlux controller thuần non-blocking, không dùng thread pool để bọc mọi lời gọi mà không xét nhu cầu.

### Hủy subscription và consumer

Một WebSocket/SSE subscription hoặc message consumer chạy nền phải có logic **stop requesting more work**, hủy subscription, đóng file/network resources và xử lý in-flight messages trước khi ngừng hoàn toàn. Với Reactive Streams, giữ **Disposable** hoặc quản lý publisher lifecycle tại một component đúng ownership; gọi **dispose()** khi stop và bảo đảm close/rollback khi hủy. Không giữ đối tượng subscription trong static field không được cleanup.

## 11. Demo chạy được: hạn chế công việc đồng thời và graceful shutdown (Spring MVC)

Demo Java 17 dùng Spring Boot 3.5.0, embedded Tomcat và một **Semaphore** nhằm cho tối đa **hai thao tác business đang thực thi** tại một thời điểm trong JVM. Nếu có thêm request trong lúc cả hai permits được giữ, server trả **503 + Retry-After: 1**. Request có thời gian giả lập quá dài bị từ chối bằng 400 trước khi lấy permit. Phần shutdown để Spring Boot thực hiện theo **server.shutdown=graceful**.

Cấu trúc thư mục:

~~~text
resource-limits-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/demo/
    │   │   ├── ResourceLimitsApplication.java
    │   │   └── WorkController.java
    │   └── resources/application.properties
    └── test/java/demo/WorkControllerTest.java
~~~

**pom.xml**:

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
  <artifactId>resource-limits-demo</artifactId>
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

**src/main/java/demo/ResourceLimitsApplication.java**:

~~~java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ResourceLimitsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResourceLimitsApplication.class, args);
    }
}
~~~

**src/main/java/demo/WorkController.java**:

~~~java
package demo;

import java.util.concurrent.Semaphore;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WorkController {

    // Chỉ giới hạn business work trong instance này.
    private final Semaphore slots = new Semaphore(2, true);

    public record WorkResult(String status, long durationMs) {}

    @GetMapping("/work")
    public ResponseEntity<WorkResult> work(
            @RequestParam(name = "ms", defaultValue = "100") long ms) {

        if (ms < 0 || ms > 2_000) {
            return ResponseEntity.badRequest().build();
        }

        if (!slots.tryAcquire()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .build();
        }

        try {
            // Chỉ mô phỏng blocking work để thấy tác động dưới tải.
            Thread.sleep(ms);
            return ResponseEntity.ok(new WorkResult("completed", ms));

        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .build();

        } finally {
            slots.release();
        }
    }
}
~~~

**Thread.sleep** có chủ đích chỉ để học cách giữ servlet worker. Trong production thay bằng logic nghiệp vụ thật, với timeout/transaction/cancellation và hạn chế DB/downstream riêng. Semaphore (fairness=true) áp dụng theo JVM và chỉ bao quanh work method; nó không hạn chế mọi HTTP endpoint hoặc toàn cluster. Đối với một ứng dụng thật, xem xét bulkhead ở service layer thay vì lặp semaphore trong nhiều controller.

**src/main/resources/application.properties**:

~~~properties
server.port=8080

# HTTP/Servlet resource boundaries cho demo.
server.tomcat.threads.max=20
server.tomcat.max-connections=100
server.max-http-request-header-size=16KB
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=6MB

# Chỉ có tác dụng trên MVC async request, nếu ứng dụng dùng.
spring.mvc.async.request-timeout=10s

# Chờ request hiện hữu trong một shutdown phase.
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=10s
~~~

Cấu hình graceful timeout ở đây **10s** vì demo chỉ chạy work tối đa 2s; khi ứng dụng thật có request hợp pháp lâu hơn cần điều chỉnh theo SLA và orchestrator. **server.tomcat.threads.max=20** không giới hạn business work ở hai: limit business work được thực hiện bằng semaphore.

**src/test/java/demo/WorkControllerTest.java**:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkController.class)
class WorkControllerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void successfulWorkReturnsKnownJson() throws Exception {
        mvc.perform(get("/api/work").param("ms", "0"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                org.springframework.http.MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.durationMs").value(0));
    }

    @Test
    void invalidDurationRejectedBeforeWork() throws Exception {
        mvc.perform(get("/api/work").param("ms", "2001"))
            .andExpect(status().isBadRequest());
    }
}
~~~

Hai test này xác minh **contract thành công 200/JSON** và **giới hạn input trả 400**. Chúng không chứng minh hệ thống xử lý đúng concurrent overload, timeout network hoặc graceful shutdown: những thứ đó cần integration/load/lifecycle tests vì MockMvc không mở socket thật.

Chạy tại thư mục gốc:

~~~bash
mvn test
mvn spring-boot:run
~~~

Khi dependencies tải thành công, hai JUnit test dự kiến pass. Trong terminal khác:

~~~bash
curl -i "http://localhost:8080/api/work?ms=0"
~~~

Phần body trả về từ controller, sau khi request thành công:

~~~json
{"status":"completed","durationMs":0}
~~~

Status kỳ vọng là **HTTP 200**, header **Content-Type: application/json**. Lệnh với ms không hợp lệ:

~~~bash
curl -i "http://localhost:8080/api/work?ms=2001"
~~~

Kỳ vọng **HTTP 400 Bad Request**; phần body lỗi cụ thể do Spring Boot error handling định nghĩa, không cam kết JSON body trong bài.

### Kiểm tra overload một cách có kiểm soát

Mở hai terminal gửi request giữ permit khoảng 2 giây:

~~~bash
curl -i "http://localhost:8080/api/work?ms=2000"
~~~

Ngay khi cả hai request đang chạy, gọi request thứ ba. Khi hai permits đều đang bị giữ, request thứ ba sẽ nhận **HTTP 503** và **Retry-After: 1**. Nếu request thứ nhất đã hoàn tất hoặc chưa thực sự chiếm permit khi request thứ ba đến, request thứ ba có thể thành công; thứ tự/đếm chính xác phụ thuộc thời điểm thử, vì vậy không giả định một số cố định trong test dùng terminal thủ công.

Trong môi trường test tự động có thể dùng **barrier/latch** và một server thật để kiểm chứng deterministically thời điểm hai permits đã được lấy. Tránh dùng chỉ một **Thread.sleep(100)** để hy vọng các request luôn song song; cách đó dễ flake trên CI.

### Kiểm tra graceful shutdown

Khởi động server với **mvn spring-boot:run**, phát request **ms=2000**, gửi SIGTERM tới đúng Java process khi request đang thực thi, và quan sát liệu nó hoàn tất trước khi hết cửa sổ 10s. Trong deployment thật kiểm tra qua orchestrator, traffic routing và readiness của pod. **Dùng Ctrl+C hoặc Stop ở IDE không phải phép thử tương đương trong mọi môi trường** vì cách đóng process có thể khác SIGTERM.

**Không cố diễn giải một lần thử bằng curl thành bảo đảm tuyệt đối**: nếu process bị cưỡng bức, chưa kịp tiếp nhận SIGTERM, reverse proxy timeout hoặc request vẫn đang trên đường, kết quả có thể khác. Graceful shutdown cần được test cùng load balancer, thread pools, DB transaction và job background thực tế.

## 12. Logging, metrics, tracing và cảnh báo

Để biết hệ thống bị giới hạn ở đâu, nên đo ít nhất:

| Metric / tín hiệu | Giải thích |
| --- | --- |
| HTTP active requests và latency p50/p95/p99 | Tải và tail latency đang tăng hay không |
| 4xx/5xx, 429, 503, 504 | Phân biệt input/rate limit, overload, gateway timeout |
| Tomcat threads busy/active và max | Server worker pool có bão hòa |
| Open/idle connections và accept backlog | Connection pressure, giữ socket nhàn rỗi |
| JVM heap, GC pause, direct memory | Buffer/JSON/DataBuffer gây memory pressure |
| Executor active threads, queue depth, rejected tasks | Nền xử lý đang quá tải |
| DB pool active/idle/pending và query timeout | Bottleneck downstream DB |
| HTTP outbound pool active/pending, connect/response timeout | Dependency remote gây tắc nghẽn |
| WebSocket/SSE active sessions, drops, termination reasons | Streaming leak hoặc disconnect |
| Shutdown duration và unfinished work | Có vượt graceful window hay không |

Spring Boot Actuator/Micrometer có thể cung cấp metrics theo instrumentation và backend được cấu hình, nhưng không phải mọi metric trên đều tự xuất hiện nếu ứng dụng chưa bật instrumentation tương ứng. Phân biệt **request canceled by client** với **server 500** trong log/metrics; broken pipe sau khi browser rời đi không có cùng ý nghĩa với lỗi business trước lúc trả response.

### Nguyên tắc log khi disconnect/timeout

- Ghi **request/correlation ID**, endpoint, duration, status/result và timeout category.
- Không log nguyên access token, full request body, file upload hoặc dữ liệu cá nhân.
- Một SSE/WebSocket disconnect bình thường không cần stacktrace ERROR lặp lại hàng nghìn lần; thống kê metric hợp lý.
- Đừng nuốt **InterruptedException**; khôi phục interrupt flag khi bắt tại blocking code và dừng sớm.
- Không coi **timeout** là dấu hiệu downstream chắc chắn đã rollback. Một POST từ client có thể đã được server thực thi thành công trước khi response bị mất; retry cần idempotency key.

## 13. Thiết kế kiểm thử cho failure modes

| Failure mode | Cách mô phỏng | Điều phải chứng minh |
| --- | --- | --- |
| Nhiều request đồng thời | Load test có concurrency rõ, test barrier/latch | Từ chối sớm, queue có trần, phục hồi sau overload |
| Request body lớn | Payload vượt multipart/codec limit | Từ chối phù hợp, disk/memory không tăng vô hạn |
| Downstream không nhận connection | Mock server unreachable/DNS/socket setup | Connect timeout hữu hạn, báo lỗi đúng |
| Downstream trả rất chậm | MockWebServer/WireMock với response delay | Response timeout, total deadline và cleanup |
| Pool outbound cạn | Giới hạn connection pool và tạo nhiều requests | Pending acquire bound, không xếp hàng vô hạn |
| Client đóng SSE/browser tab | HTTP client cancel/close socket | Subscription/task được release, metric phù hợp |
| Client hết hạn trước server | Proxy timeout ngắn hơn API | Không tiếp tục tốn tài nguyên vô ích khi có thể hủy |
| Nhận SIGTERM khi có request đang chạy | Integration/deployment test | Không nhận thêm request mới, in-flight trong grace hoàn tất nếu kịp |
| SIGKILL ngay giữa transaction | Chaos/drill có dữ liệu test | Durable state/idempotency giúp phục hồi |
| WebSocket/SSE khi triển khai phiên bản mới | Mở session rồi drain pod | Client reconnect, subscribe lại, state đồng bộ |

**Lưu ý trạng thái HTTP khi hủy:** đôi khi server không thể gửi bất kỳ status nào vì socket đã đóng. Trong log phía proxy có thể có mã vendor-specific cho client closed request, nhưng application không nên tự cố trả một "status ngắt kết nối" lên connection đã mất. Ở Reactor, **CANCEL** là signal publisher; nó không phải HTTP status.

Một bài test graceful shutdown đáng tin phải phân biệt các thời điểm: request thật sự đã được server chấp nhận, SIGTERM được nhận, server ngừng routing mới, request cũ hoàn thành/timeout, tài nguyên tắt. Nếu chỉ gửi SIGTERM rồi kiểm tra process exit code, chưa kiểm chứng đặc tính graceful.

## 14. Các lỗi phổ biến cần tránh

1. **Đặt server.tomcat.connection-timeout rồi tưởng API đã có business deadline.** Đây là timeout kết nối, không thay thế outbound read timeout và tổng ngân sách request.
2. **Tăng threads.max khi DB pool quá nhỏ.** Thêm nhiều servlet worker có thể chỉ tăng số luồng chờ DB và làm p99 xấu hơn.
3. **Để queue executor hoặc pending acquire vô hạn.** Quá tải nhỏ biến thành hàng nghìn request treo lâu thay vì thông báo từ chối sớm.
4. **Chỉ giới hạn upload tại client.** Kẻ gọi khác có thể bypass giao diện; cần kiểm server/proxy/storage.
5. **Tăng WebFlux maxInMemorySize để chữa stream lớn.** Nếu contract cho phép hãy chuyển sang phân trang/stream, giới hạn per-record.
6. **Dùng Thread.sleep/JDBC đồng bộ trên Netty event-loop.** Một tác vụ block ảnh hưởng nhiều kết nối khác.
7. **Gọi subscribe() thủ công ở controller reactive.** Mất cancellation/error propagation từ WebFlux, có thể tạo việc nền mồ côi.
8. **Coi DeferredResult timeout là task nền đã bị cancel.** Cần owner và cancellation/interrupt/query timeout rõ ràng.
9. **Chờ một callback disconnect tức thời trong Spring MVC.** Servlet không có notification đáng tin cho mọi remote close; I/O write/heartbeat giúp phát hiện.
10. **Gọi completeWithError() trong catch IOException của SseEmitter một cách vô điều kiện.** Spring/container có lifecycle error notification riêng.
11. **Cho mọi in-flight stream sống mãi trong graceful shutdown.** Cần policy drain/reconnect và thời hạn hợp lý.
12. **Coi graceful shutdown thay durable queue.** Process vẫn có thể bị kill, cần chống mất/trùng việc.
13. **Retry mọi lỗi timeout.** Timeout có thể xảy ra sau khi server hoàn tất side effect; retry cần idempotency.
14. **Không kiểm thử dưới proxy/load balancer.** Ứng dụng có thể chạy đúng khi localhost nhưng chết do gateway idle timeout hoặc termination grace không khớp.

## 15. Checklist vận hành trước khi triển khai

- [ ] Biết resource bottleneck thật: CPU, JVM memory, Tomcat threads, Reactor Netty pool, DB connections hay downstream.
- [ ] Đã đặt limit cho HTTP connections/headers, request/multipart/codec buffers và business concurrency theo công suất thực.
- [ ] Không có queue hoặc buffer tăng không giới hạn trên những đường xử lý nhiều traffic.
- [ ] Outbound HTTP có connect/read/response timeout hữu hạn và policy retry có kiểm soát.
- [ ] Tổng business deadline tương thích với gateway timeout và SLA; không cho mọi bước downstream dùng toàn bộ deadline.
- [ ] Streaming/SSE/WebSocket có cleanup, heartbeat phù hợp, cancellation và reconnect strategy.
- [ ] Spring MVC async có executor phù hợp và timeout; WebFlux không block event-loop.
- [ ] Graceful shutdown và thời gian dừng của orchestrator đủ cho request hợp pháp có giới hạn thời gian.
- [ ] Background tasks, thread pools, DB pools, network clients có lifecycle owner và dọn khi ApplicationContext đóng.
- [ ] Metrics/logs phân biệt overload, rate limit, timeout, disconnect, shutdown và lỗi nghiệp vụ.
- [ ] Đã kiểm thử tình huống overload, remote timeout, client abort và SIGTERM trong môi trường có proxy.

## Tài liệu chính thức

- [Spring Boot 3.5 – Graceful Shutdown](https://docs.spring.io/spring-boot/3.5/reference/web/graceful-shutdown.html)
- [Spring Boot 3.5 – Common Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/)
- [Spring Framework 6.2 – Spring MVC Asynchronous Requests](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html)
- [Spring Framework – Reactive Core và codec buffer limits](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html)
- [Spring Framework – WebClient Configuration, resources và timeouts](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-builder.html)
- [JdkClientHttpRequestFactory Javadoc (Spring 6.2)](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/client/JdkClientHttpRequestFactory.html)
- [Spring Framework – WebFlux](https://docs.spring.io/spring-framework/reference/6.2/web/webflux.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="operations-practice-007">Thực hành REST API: mapping, DTO, validation, lỗi và kiểm thử</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành REST API: mapping, DTO, validation, lỗi và kiểm thử

## Mục tiêu

Xây dựng REST API quản lý công việc (Task) bằng **Java 17+, Spring Boot 3.3.6 và Spring MVC**. Bài bao gồm HTTP mapping, DTO, Jakarta Bean Validation, xử lý exception, unit/slice test và integration test. Dữ liệu được giữ trong RAM để tập trung vào web layer; không dùng database trong ví dụ.

Sau khi hoàn thành, bạn có thể giải thích đường đi từ HTTP request tới `DispatcherServlet`, controller, DTO, service, error handler; thiết kế HTTP status chính xác và tự viết test với `MockMvc`, `@WebMvcTest`, `@SpringBootTest`.

### Hợp đồng REST API

| HTTP method | URI | Input | Thành công | Lỗi |
| --- | --- | --- | --- | --- |
| GET | `/api/tasks` | Query `completed=true/false` tùy chọn | 200 + JSON array | 400 nếu query sai kiểu |
| GET | `/api/tasks/{id}` | ID là long | 200 + JSON Task | 400 nếu sai kiểu, 404 nếu không tồn tại |
| POST | `/api/tasks` | JSON title, completed | 201 + Task + Location | 400 nếu body sai |
| PUT | `/api/tasks/{id}` | JSON title, completed | 200 + Task | 400 hoặc 404 |
| DELETE | `/api/tasks/{id}` | Không body | 204 No Content | 404 nếu không tồn tại |

PUT trong bài thay thế cả hai trường có thể cập nhật. Nếu muốn cập nhật riêng một field, định nghĩa PATCH và xử lý “không gửi field” khác với “gửi null”. Một Task gồm `id` server cấp, `title` và `completed`. Client không thể tự đặt `id` khi POST.

Ví dụ POST:

~~~http
POST /api/tasks HTTP/1.1
Content-Type: application/json

{"title":"Học REST API","completed":false}
~~~

Với store rỗng và ID bắt đầu từ 1, server trả HTTP 201 Created, header Location trỏ đến task mới và body JSON như:

~~~json
{"id":1,"title":"Học REST API","completed":false}
~~~

Đừng dựa vào thứ tự property trong JSON hoặc host/port cố định trong Location; hai điều đó có thể khác theo môi trường.

## 1. Cấu trúc các tầng

~~~text
HTTP request
  |
DispatcherServlet / HandlerMapping
  |
  +--> Jackson chuyển JSON thành TaskInput
  +--> Bean Validation: @Valid + constraints
  |       |
  |       +--> sai -> MethodArgumentNotValidException
  |                    -> ApiExceptionHandler -> 400 JSON
  |
TaskController
  |
TaskService -> ConcurrentHashMap
  |
TaskResponse
  |
ResponseEntity / Jackson
  |
HTTP response (status + headers + JSON)
~~~

**Controller** chỉ đảm nhận binding và HTTP contract; **request DTO** định nghĩa các trường client có thể gửi; **response DTO** chỉ công khai dữ liệu cho client; **service** thực hiện CRUD và quy tắc nghiệp vụ; **exception handler** chuyển lỗi có chủ đích thành status/schema ổn định. Không nên trả trực tiếp JPA entity trong một API production vì có thể lộ trường nội bộ, kích hoạt lazy loading và ràng buộc schema database với client.

## 2. Cấu trúc dự án

~~~text
tasks-rest-demo/
  build.gradle
  src/main/java/demo/tasks/
    TaskApiApplication.java
    TaskInput.java
    TaskResponse.java
    TaskNotFoundException.java
    TaskService.java
    TaskController.java
    ApiError.java
    ApiExceptionHandler.java
  src/test/java/demo/tasks/
    TaskControllerMvcTest.java
    TaskApiIntegrationTest.java
~~~

**build.gradle**:

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.6'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'demo'
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
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

`spring-boot-starter-web` cung cấp Spring MVC, Jackson và embedded Tomcat. `spring-boot-starter-validation` cung cấp Jakarta Bean Validation implementation. `spring-boot-starter-test` cung cấp JUnit 5, Mockito, MockMvc và Spring Test. Ví dụ cố định Boot **3.3.6**, vì thế `@MockBean` trong test là API hợp lệ; đừng tự áp dụng import của Boot phiên bản mới hơn vào project này.

**TaskApiApplication.java**:

~~~java
package demo.tasks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TaskApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskApiApplication.class, args);
    }
}
~~~

Các file đặt cùng package `demo.tasks` để component scanning nhận được controller, service và advice.

## 3. DTO và validation

**TaskInput.java**:

~~~java
package demo.tasks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskInput(
        @NotBlank(message = "title must not be blank")
        @Size(max = 80, message = "title must contain at most 80 characters")
        String title,

        @NotNull(message = "completed is required")
        Boolean completed
) {}
~~~

`@NotBlank` từ chối null, chuỗi rỗng và chỉ có whitespace. `@Size(max=80)` kiểm tra độ dài. `@NotNull` yêu cầu field `completed` tồn tại. Dùng `Boolean` boxed để phân biệt field thiếu/null với `false`; nếu dùng primitive `boolean`, field thiếu có thể trở thành false mặc định và không thể được phát hiện bằng `@NotNull`.

**TaskResponse.java**:

~~~java
package demo.tasks;

public record TaskResponse(
        long id,
        String title,
        boolean completed
) {}
~~~

Response có ID server tạo; request không có ID. Java 17 record được Jackson trong Boot 3 xử lý như DTO JSON.

| Request JSON | Kết quả |
| --- | --- |
| `{"title":"Learn","completed":false}` | Hợp lệ |
| `{"title":" ","completed":false}` | Vi phạm NotBlank |
| `{"title":"Learn"}` | Vi phạm NotNull của completed |
| `{"title":` | JSON malformed, chưa có DTO để validate |
| `{"title":"Learn","completed":"maybe"}` | Lỗi chuyển kiểu JSON |

Phải đặt `@Valid` trên controller parameter cùng `@RequestBody`. Annotation constraint có mặt trên record nhưng controller quên `@Valid` thì validation không được kích hoạt theo đường xử lý dự kiến.

## 4. Service và exception nghiệp vụ

**TaskNotFoundException.java**:

~~~java
package demo.tasks;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(long id) {
        super("Task " + id + " not found");
    }
}
~~~

Service không tự chọn HTTP status. Advice chuyển lỗi này thành 404.

**TaskService.java**:

~~~java
package demo.tasks;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service
public class TaskService {
    private final Map<Long, TaskResponse> tasks =
            new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public List<TaskResponse> list(Boolean completed) {
        return tasks.values().stream()
                .filter(task -> completed == null
                        || task.completed() == completed)
                .sorted(Comparator.comparingLong(TaskResponse::id))
                .toList();
    }

    public TaskResponse get(long id) {
        TaskResponse found = tasks.get(id);
        if (found == null) {
            throw new TaskNotFoundException(id);
        }
        return found;
    }

    public TaskResponse create(TaskInput input) {
        long id = sequence.incrementAndGet();
        TaskResponse task = new TaskResponse(
                id, input.title().trim(), input.completed());
        tasks.put(id, task);
        return task;
    }

    public TaskResponse replace(long id, TaskInput input) {
        TaskResponse task = tasks.computeIfPresent(id,
                (key, old) -> new TaskResponse(
                        key, input.title().trim(), input.completed()));
        if (task == null) {
            throw new TaskNotFoundException(id);
        }
        return task;
    }

    public void delete(long id) {
        if (tasks.remove(id) == null) {
            throw new TaskNotFoundException(id);
        }
    }
}
~~~

`ConcurrentHashMap` cho phép thao tác map từ nhiều thread; `AtomicLong` tạo ID không trùng trong **một process**. `computeIfPresent` cập nhật value theo key trong một bước thay vì gọi `get` rồi `put` rời rạc. Record TaskResponse immutable tránh sửa data trong object được nhiều thread dùng chung. `list` sắp xếp theo ID để kết quả dễ kiểm chứng.

Giới hạn: đây là RAM store, mất dữ liệu khi restart, không có transaction, không đồng bộ giữa replicas. Khi chuyển sang database, bổ sung repository, migration, transaction và chính sách optimistic locking khi cần.

## 5. HTTP mapping trong controller

**TaskController.java**:

~~~java
package demo.tasks;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaskResponse> list(
            @RequestParam(required = false) Boolean completed) {
        return service.list(completed);
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @Valid @RequestBody TaskInput input) {
        TaskResponse created = service.create(input);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public TaskResponse replace(
            @PathVariable long id,
            @Valid @RequestBody TaskInput input) {
        return service.replace(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
~~~

`@RestController` cho biết return value được ghi ra HTTP body, không phải tên view template. `@RequestMapping` đặt tiền tố; các annotation GET/POST/PUT/DELETE chọn HTTP method tương ứng. `@PathVariable` lấy `id` từ URI, còn `@RequestParam` lấy query `?completed=true`. `@RequestBody` dùng Jackson decode request JSON; `@Valid` kiểm tra constraints trước khi gọi service.

`ResponseEntity.created(location)` trả 201 + header Location và body Task. `ServletUriComponentsBuilder.fromCurrentRequest()` lấy URI theo request hiện tại; nếu deploy sau proxy, cấu hình trusted forwarded headers đúng để Location không trả host/port bên trong. GET/PUT trả DTO và mặc định 200; DELETE trả 204 không body.

HTTP status trong demo:

| Status | Tình huống |
| --- | --- |
| 200 | Đọc hoặc thay Task thành công |
| 201 + Location | Tạo Task mới |
| 204 | Xóa Task đã tồn tại, không body |
| 400 | Input sai constraint, JSON malformed hoặc ID/query không parse được |
| 404 | ID hợp lệ về cú pháp nhưng Task không tồn tại |
| 500 | Lỗi server không dự kiến, cần log nội bộ an toàn |

DELETE lần hai trả 404 theo hợp đồng bài này. Idempotence của HTTP DELETE là về **hiệu ứng cuối** trên trạng thái resource, không bắt buộc mọi lần có cùng response code. PUT lặp cùng payload cũng phải có cùng trạng thái cuối. Để hỗ trợ partial updates, thiết kế PATCH riêng.

## 6. Ánh xạ lỗi nhất quán bằng @RestControllerAdvice

**ApiError.java**:

~~~java
package demo.tasks;

import java.util.Map;

public record ApiError(
        String code,
        String message,
        Map<String, String> fields
) {}
~~~

**ApiExceptionHandler.java**:

~~~java
package demo.tasks;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ApiError> missing(
            TaskNotFoundException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(
                        "TASK_NOT_FOUND",
                        error.getMessage(),
                        Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidDto(
            MethodArgumentNotValidException error) {
        Map<String, String> fields = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors()
                .forEach(fieldError -> fields.putIfAbsent(
                        fieldError.getField(),
                        fieldError.getDefaultMessage() == null
                                ? "Invalid value"
                                : fieldError.getDefaultMessage()));

        return ResponseEntity.badRequest()
                .body(new ApiError(
                        "VALIDATION_FAILED",
                        "Request validation failed",
                        fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(
            HttpMessageNotReadableException error) {
        return ResponseEntity.badRequest()
                .body(new ApiError(
                        "INVALID_JSON",
                        "Request body is missing or malformed",
                        Map.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> invalidParameter(
            MethodArgumentTypeMismatchException error) {
        String name = error.getName() == null
                ? "parameter" : error.getName();
        return ResponseEntity.badRequest()
                .body(new ApiError(
                        "INVALID_PARAMETER",
                        "Request parameter has an invalid type",
                        Map.of(name, "Invalid value")));
    }
}
~~~

Bốn loại lỗi khác nhau:

1. `TaskNotFoundException` → HTTP 404 + `TASK_NOT_FOUND`.
2. `MethodArgumentNotValidException` từ `@Valid` → HTTP 400 + `VALIDATION_FAILED` kèm fields.
3. `HttpMessageNotReadableException` từ converter JSON → HTTP 400 + `INVALID_JSON`.
4. `MethodArgumentTypeMismatchException` từ ID/query không chuyển kiểu được → 400 + `INVALID_PARAMETER`.

Ví dụ lỗi field:

~~~json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "fields": {
    "title": "title must not be blank",
    "completed": "completed is required"
  }
}
~~~

Đây là error schema của **demo**, không phải chuẩn bắt buộc; Spring 6 cũng hỗ trợ `ProblemDetail` (RFC 9457) và `ResponseEntityExceptionHandler`. Nếu thêm constraint trực tiếp trên method parameter, như `@Min(1) @PathVariable long id`, Spring MVC 6.1+ có thể phát `HandlerMethodValidationException` thay vì lỗi DTO; phải mở rộng handler/test tương ứng.

Không gom mọi `Exception` thành HTTP 400. Exception không dự kiến là lỗi server, thường phải được log riêng, trả phản hồi an toàn và không lộ stack trace, token, SQL hay dữ liệu riêng tư.

## 7. Controller slice test bằng @WebMvcTest

`@WebMvcTest(TaskController.class)` dựng MVC components để kiểm tra mapping, JSON conversion, validation và exception mapping. Service được thay bằng Mockito mock. Không cần chạy embedded HTTP server/DB.

**TaskControllerMvcTest.java**:

~~~java
package demo.tasks;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskControllerMvcTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    TaskService service;

    @Test
    void postMapsDtoAndReturns201() throws Exception {
        when(service.create(any(TaskInput.class)))
                .thenReturn(new TaskResponse(42, "Learn MVC", false));

        mvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Learn MVC","completed":false}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location", endsWith("/api/tasks/42")))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.title").value("Learn MVC"));

        verify(service).create(argThat(input ->
                "Learn MVC".equals(input.title())
                        && Boolean.FALSE.equals(input.completed())));
    }

    @Test
    void invalidDtoIsRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.title").exists())
                .andExpect(jsonPath("$.fields.completed").exists());

        verifyNoInteractions(service);
    }

    @Test
    void notFoundBecomes404() throws Exception {
        when(service.get(999))
                .thenThrow(new TaskNotFoundException(999));

        mvc.perform(get("/api/tasks/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("TASK_NOT_FOUND"));
    }

    @Test
    void malformedJsonBecomes400() throws Exception {
        mvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_JSON"));

        verifyNoInteractions(service);
    }

    @Test
    void invalidPathVariableBecomes400() throws Exception {
        mvc.perform(get("/api/tasks/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_PARAMETER"));
    }
}
~~~

Slice test thật sự đi qua MVC request lifecycle, Jackson, `@Valid` và exception handler; chỉ gọi trực tiếp `new TaskController(service).create(dto)` không kiểm tra những thành phần đó. `verifyNoInteractions(service)` chứng minh input sai bị chặn trước khi vào nghiệp vụ.

Trong Boot 3.3, `@WebMvcTest` có khả năng scan `@ControllerAdvice`, vì vậy `ApiExceptionHandler` trong cùng package được nạp. Nếu project có component scan/filter đặc biệt, hãy import advice vào test configuration một cách tường minh.

## 8. Integration test với service thật

`@SpringBootTest` dựng application context đầy đủ, còn `@AutoConfigureMockMvc` dùng mock Servlet request/response. Test này **không mở TCP port**, nhưng gọi qua controller/service/advice thật và xác minh state.

**TaskApiIntegrationTest.java**:

~~~java
package demo.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskApiIntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void createReadUpdateDelete() throws Exception {
        MvcResult created = mvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Learn REST","completed":false}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Learn REST"))
                .andReturn();

        JsonNode json = mapper.readTree(
                created.getResponse().getContentAsString());
        long id = json.path("id").asLong();

        mvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false));

        mvc.perform(put("/api/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Learn REST well","completed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title")
                        .value("Learn REST well"))
                .andExpect(jsonPath("$.completed").value(true));

        mvc.perform(delete("/api/tasks/{id}", id))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("TASK_NOT_FOUND"));
    }

    @Test
    void invalidPutFailsValidationFirst() throws Exception {
        mvc.perform(put("/api/tasks/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","completed":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"));
    }
}
~~~

Không đoán ID bằng 1 trong test: lấy ID thực từ POST response, vì Spring Test có thể reuse application context và thứ tự chạy test không được bảo đảm. PUT invalid trả 400 trước khi service tra ID. Unit test service, DB integration test và real-network test bổ sung sau khi có yêu cầu.

Để test server thật qua TCP, có thể dùng `@SpringBootTest(webEnvironment=RANDOM_PORT)`, `@LocalServerPort` và HTTP client như `RestClient` hoặc `WebTestClient.bindToServer` khi có dependency. Muốn kiểm tra proxy/TLS phải gửi request qua chính tầng proxy/TLS đó; MockMvc không kiểm tra mạng thật.

## 9. Chạy và gửi HTTP request

Cần JDK 17+ và Gradle tương thích. Tại thư mục chứa `build.gradle`:

~~~bash
gradle test
gradle bootRun
~~~

Nếu dự án có Gradle Wrapper, dùng `./gradlew test` và `./gradlew bootRun` trên Unix/macOS, hoặc `gradlew.bat test` và `gradlew.bat bootRun` trên Windows.

**Tạo Task** bằng curl trong shell hỗ trợ single quotes:

~~~bash
curl -i -X POST "http://localhost:8080/api/tasks" \
  -H "Content-Type: application/json" \
  -d '{"title":"Học Spring REST","completed":false}'
~~~

**Đọc, lọc, cập nhật, xóa:**

~~~bash
curl -i "http://localhost:8080/api/tasks"
curl -i "http://localhost:8080/api/tasks?completed=false"
curl -i "http://localhost:8080/api/tasks/1"

curl -i -X PUT "http://localhost:8080/api/tasks/1" \
  -H "Content-Type: application/json" \
  -d '{"title":"Đã thực hành","completed":true}'

curl -i -X DELETE "http://localhost:8080/api/tasks/1"
curl -i "http://localhost:8080/api/tasks/1"
~~~

**Kiểm tra lỗi:**

~~~bash
curl -i -X POST "http://localhost:8080/api/tasks" \
  -H "Content-Type: application/json" \
  -d '{"title":"  ","completed":null}'

curl -i -X POST "http://localhost:8080/api/tasks" \
  -H "Content-Type: application/json" \
  -d '{"title":'

curl -i "http://localhost:8080/api/tasks/abc"
curl -i "http://localhost:8080/api/tasks/99999"
~~~

Bốn trường hợp trả lần lượt: 400 `VALIDATION_FAILED`, 400 `INVALID_JSON`, 400 `INVALID_PARAMETER`, 404 `TASK_NOT_FOUND`. Trên Windows PowerShell 5.1, `curl` có thể là alias của `Invoke-WebRequest`; dùng `curl.exe` hoặc `Invoke-RestMethod` với JSON request body đúng cách. API demo dùng port 8080 mặc định trừ khi bạn đổi cấu hình.

## 10. Những lỗi phổ biến

1. **Nhầm mapping path/query:** `/tasks/7` là path variable, `?completed=true` là query.
2. **Quên `@Valid` hoặc starter-validation:** record annotations không tự đảm bảo web request bị chặn.
3. **Dùng primitive boolean cho input required:** không phân biệt missing với false.
4. **Trả JPA entity trực tiếp:** dễ lộ field nội bộ, circular references, lazy-loading và coupling schema.
5. **POST tạo resource nhưng không trả 201/Location:** làm HTTP contract thiếu thông tin.
6. **DELETE 204 kèm body:** trái ngữ nghĩa No Content.
7. **Coi DTO invalid là server error:** nên trả 400 và field errors.
8. **Coi mọi exception là 400:** che bug server, khó monitoring.
9. **Chỉ test happy path:** bỏ quên missing resource, JSON malformed, invalid query và validation.
10. **Chỉ gọi Java controller method trong test:** không xác minh HTTP binding/Jackson/validation/exception handling.
11. **Phụ thuộc ID cố định hay thứ tự chạy test:** dẫn tới flaky tests.
12. **Dùng ConcurrentHashMap thay database trong production:** không có durability, transaction hay multi-node consistency.
13. **Hardcode host/port vào Location:** sai khi deploy sau reverse proxy.
14. **Tin rằng validation thay authentication/authorization:** mỗi resource cần chính sách quyền riêng.
15. **Không xử lý Content-Type/Accept:** có thể nhận 415 hoặc 406 khi media types không phù hợp.

## 11. Bài tập mở rộng

**Bài 1:** Viết GET `/api/tasks/{id}/status` chỉ trả `id`, `completed` và test cả 200/404.

**Bài 2:** Thêm `description` tùy chọn tối đa 500 ký tự vào DTO. Test input quá dài bị 400 và input hợp lệ xuất hiện trong response.

**Bài 3:** Tạo ba task với completed true/false/true. Test query `completed=true` chỉ trả hai ID đúng. Đảm bảo test không phụ thuộc dữ liệu dư từ test khác.

**Bài 4:** So sánh `GET /api/tasks/abc` và `GET /api/tasks/99999`. Vì sao 400 và 404 khác nhau? Viết MockMvc test.

**Bài 5:** Thêm `@Min(1)` trực tiếp lên `@PathVariable long id`, nghiên cứu `HandlerMethodValidationException` và cập nhật advice/test.

**Bài 6:** Thiết kế PATCH chỉ cập nhật completed; phân biệt field absent và JSON null, quyết định hợp đồng có thể kiểm thử.

**Bài 7:** Thay RAM store bằng database, bổ sung migration, repository, transaction boundaries, versioning và kiểm thử lost update.

**Bài 8:** Thêm authorization theo owner, kiểm tra người chưa đăng nhập (401) và có đăng nhập nhưng không có quyền (403).

## 12. Checklist hoàn thành

- [ ] Mapping và HTTP method đúng hợp đồng.
- [ ] DTO input/output tách biệt, constraint và `@Valid` hoạt động.
- [ ] POST trả 201 + Location; GET/PUT 200; DELETE 204.
- [ ] 400/404 được phân loại và trả JSON lỗi ổn định.
- [ ] Controller chỉ làm HTTP boundary, service giữ logic.
- [ ] Slice test xác minh DTO binding, JSON, status, validation và exception mapping.
- [ ] Integration test dùng service thật, kiểm tra create/read/update/delete.
- [ ] Đã thử curl và xem đầy đủ status/header/body.
- [ ] Nắm rõ giới hạn storage RAM và những việc cần làm khi đưa vào production.

## Tài liệu tham khảo chính thức

- Spring MVC annotated controllers: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller.html
- Request mapping: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-requestmapping.html
- Request body: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/requestbody.html
- Validation: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html
- Exception handling: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-exceptionhandler.html
- MockMvc: https://docs.spring.io/spring-framework/reference/6.2/testing/spring-mvc-test-framework.html
- Spring Boot 3.3 testing: https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="operations-practice-008">Thực hành trang biểu mẫu: view, bản địa hóa, session và redirect</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành trang biểu mẫu: View, bản địa hóa, session và redirect trong Spring MVC

## 1. Bài toán thực hành và kết quả cần đạt

Một trang biểu mẫu web truyền thống có nhiều trách nhiệm hơn việc nhận vài chuỗi rồi gọi service. Spring MVC phải hiển thị view HTML, bind các field từ form vào đối tượng Java, kiểm tra dữ liệu, giữ dữ liệu người dùng đang nhập, chọn ngôn ngữ giao diện, duy trì trạng thái qua nhiều request và điều hướng sau khi lưu thành công.

Trong bài này, ta xây dựng **trang đăng ký có chức năng lưu bản nháp**:

1. Người dùng mở `GET /form`, thấy trang Thymeleaf bằng tiếng Anh.
2. Chọn liên kết `?lang=vi`, nhãn và thông báo chuyển sang tiếng Việt và được nhớ trong HTTP session.
3. Nhập tên, email, ghi chú, chọn **Lưu bản nháp**; server giữ bản nháp trong session rồi redirect về `GET /form`.
4. Chọn **Gửi đăng ký**; nếu tên/email không hợp lệ, server render lại form cùng lỗi mà không redirect.
5. Nếu dữ liệu hợp lệ, server đánh dấu hoàn tất, xóa bản nháp thuộc controller khỏi session, đặt thông báo flash rồi redirect `GET /done`. Làm mới trang kết quả không tự gửi lại POST.

Bài minh họa **Spring MVC Servlet + Thymeleaf + Jakarta Bean Validation**, dùng Java 17 và Spring Boot 3.3.x (Spring Framework 6.1). Demo không ghi database: “hoàn tất” chỉ xác nhận luồng HTTP/view hoạt động. Ứng dụng production cần tầng service, transaction, bảo mật và idempotency riêng.

Mục tiêu kiến thức: hiểu `Model`, logical view name, `th:object`/`th:field`, `MessageSource` và locale, `@SessionAttributes`/`SessionStatus`, `RedirectAttributes.addFlashAttribute`, Post/Redirect/Get (PRG), cùng cách kiểm thử các giai đoạn của biểu mẫu bằng MockMvc.

## 2. Luồng request của một MVC form

~~~mermaid
flowchart TD
    A[GET /form] --> B[DispatcherServlet]
    B --> C[Controller trả logical view 'form']
    C --> D[Thymeleaf render HTML từ model + messages]
    D --> E[Trình duyệt hiển thị form]
    E --> F{POST /form với action}
    F -->|save| G[Bind draft và giữ trong session]
    G --> H[Flash saved=true]
    H --> I[302 Location: /form]
    I --> A
    F -->|finish| J[Bind + Jakarta Validation]
    J -->|Sai| K[BindingResult có field errors]
    K --> C
    J -->|Đúng| L[SessionStatus.setComplete]
    L --> M[Flash completedName]
    M --> N[302 Location: /done]
    N --> O[GET /done render view 'done']
~~~

Đây là luồng HTTP nhiều request. **View** do template engine render thành HTML; **model** chứa các dữ liệu template được phép truy cập. Khi controller trả `"form"`, Spring dùng view resolver tìm template (ví dụ `templates/form.html`). Khi trả `"redirect:/form"`, Spring phát HTTP redirect để browser **gửi request GET mới**; đây không phải hành vi render view `form` ngay trong POST.

| Controller trả về | Spring làm gì? | URL trên trình duyệt |
| --- | --- | --- |
| `"form"` | Render template `form.html` trong request hiện tại | Giữ URL request |
| `"done"` | Render template `done.html` | Giữ URL request |
| `"redirect:/form"` | Gửi redirect với Location `/form` | Browser đi sang URL `/form` |
| `"forward:/form"` | Chuyển request phía server cho handler khác | Browser thường không đổi URL |

Dùng redirect sau **POST thành công**, không redirect mù khi `BindingResult` có lỗi. Nếu redirect khi validation thất bại mà không chủ động giữ `BindingResult` và form values, các lỗi/giá trị nhập sẽ biến mất. Trả trực tiếp view `form` trên lỗi vừa đơn giản vừa giữ được kết quả bind của request.

## 3. View Thymeleaf, binding và kiểm tra dữ liệu

### 3.1. Model và template

Trong Spring Boot với starter Thymeleaf, file `src/main/resources/templates/form.html` là view HTML; controller trả logical view name `"form"`. Template nhận model attributes do controller cung cấp. Ví dụ một đoạn Thymeleaf:

~~~html
<form th:action="@{/form}" th:object="${draft}" method="post">
  <input type="text" th:field="*{name}">
  <span th:if="${#fields.hasErrors('name')}"
        th:errors="*{name}">Lỗi tên</span>
  <button name="action" value="finish" type="submit">Gửi</button>
</form>
~~~

Đây là fragment để hiểu cú pháp, không phải trang hoàn chỉnh. Dấu `th:object` trỏ đến **model attribute** `draft`. `th:field` sinh tên/id/giá trị tương ứng thuộc tính của object; nếu Spring bind lỗi, template hiển thị lại giá trị người dùng nhập và `BindingResult` phù hợp.

Cần phân biệt:
- HTML `name="action"` là một **tham số điều khiển nút submit** (`save` hoặc `finish`) do browser gửi.
- `name`, `email`, `note` là fields thuộc `draft`, được bind qua setter của JavaBean.
- `@Valid` kích hoạt Bean Validation trên object tại handler submit cuối; `BindingResult` phải nằm **ngay sau** argument được validate.
- `th:errors` lấy thông điệp lỗi từ Bean Validation/binding, còn `#{form.title}` đọc thông điệp UI từ message bundle.

### 3.2. Ranh giới client và server

Thuộc tính HTML như `required`, `maxlength` cải thiện UX nhưng có thể bị bỏ qua bởi cURL, bot hoặc client tự viết. Ràng buộc phải được kiểm lại ở server bằng `@NotBlank`, `@Email`, `@Size` và kiểm tra nghiệp vụ.

Các trường `id`, `role`, `price`, `owner` không nên xuất hiện trong một form object nhận tự do nếu client không được quyền sửa. Dùng DTO dành riêng cho input; tuyệt đối không bind trực tiếp toàn bộ entity có field nhạy cảm.

## 4. Bản địa hóa: MessageSource, LocaleResolver, interceptor

Spring MVC chọn `Locale` cho request rồi chuyển cho Spring message resolver và template engine. Ví dụ có `messages.properties` (tiếng Anh mặc định) và `messages_vi.properties` (tiếng Việt):

~~~properties
form.title=Registration form
form.name=Name
form.submit=Submit registration
~~~

~~~properties
form.title=Biểu mẫu đăng ký
form.name=Họ tên
form.submit=Gửi đăng ký
~~~

Trong view Thymeleaf, `<h1 th:text="#{form.title}">` sẽ hiện chuỗi theo locale hiện hành. **MessageSource** quản lý chuỗi giao diện; nó không tự dịch nội dung cơ sở dữ liệu hoặc tự chọn ngôn ngữ của lỗi nghiệp vụ chưa được thiết kế i18n.

Hai chiến lược phổ biến:
- `AcceptHeaderLocaleResolver`: suy ra locale từ header `Accept-Language`; phù hợp nếu muốn người dùng thay ngôn ngữ qua browser/header. Resolver này **không hỗ trợ thay locale bằng interceptor setLocale** theo cách thông thường.
- `SessionLocaleResolver`: đọc/ghi locale tùy chọn trong `HttpSession`; phù hợp ví dụ có session. `LocaleChangeInterceptor` nhìn `?lang=vi` hoặc `?lang=en` và gọi resolver thay đổi ngôn ngữ, duy trì cho các request sau thuộc cùng session.

Trong demo sử dụng `SessionLocaleResolver`. Khi session hết hạn hoặc browser mất cookie, ngôn ngữ trở về fallback (ở đây mặc định tiếng Anh). Cần cung cấp lựa chọn ngôn ngữ rõ ràng, giới hạn danh sách locale được hỗ trợ ở production và kiểm thử mọi message key cần thiết.

**Thông điệp validation** là resource bundle riêng trong ví dụ (`ValidationMessages.properties`, `ValidationMessages_vi.properties`) dùng các key trong annotation constraint. Nhờ đó test được cả nhãn UI lẫn thông báo ràng buộc theo ngôn ngữ thay vì hardcode tiếng Anh trong Java.

## 5. Session, flash attributes và chuyển hướng: ba vòng đời khác nhau

### 5.1. @SessionAttributes và SessionStatus

`@SessionAttributes("draft")` đặt trên **controller class** báo Spring lưu model attribute tên `draft` vào HTTP session giữa các request thuộc workflow đó. Khi `@ModelAttribute("draft")` xuất hiện lần đầu, object được tạo và lưu; các GET/POST tiếp theo lấy lại object từ session.

`SessionStatus.setComplete()` đánh dấu workflow đã hoàn thành để Spring **xóa model attributes do @SessionAttributes quản lý** sau handler. Nó **không gọi `HttpSession.invalidate()`**, vì làm vậy sẽ xóa cả đăng nhập, locale và trạng thái workflow khác của người dùng. Bản nháp là trạng thái tạm thời; ứng dụng vẫn phải xử lý khi session timeout hoặc dữ liệu từ tab khác cùng session ghi đè.

Đừng nhầm:
- `@SessionAttributes` (số nhiều, cấp class): chọn model attributes Spring tự quản lý cho workflow controller.
- `@SessionAttribute` (số ít, method parameter): đọc một session attribute đã tồn tại và thường do thành phần khác quản lý.
- `HttpSession`: API Servlet để đọc/ghi/xóa dữ liệu session thủ công, thích hợp khi sở hữu trạng thái rõ ràng.
- `SessionLocaleResolver`: cũng dùng HTTP session nhưng lưu **locale** riêng, không phải bản nháp `draft`.

Session thường được định danh qua cookie `JSESSIONID`, nhưng không có nghĩa dữ liệu session nhất thiết nằm trong cookie; với Servlet thông thường dữ liệu ở phía server. Khi chạy nhiều instance, cần chính sách session stickiness hoặc shared session storage (ví dụ Spring Session với Redis) và lưu ý serialization/TTL.

### 5.2. RedirectAttributes và flash

Sau khi lưu bản nháp, ta thêm `saved=true` qua `RedirectAttributes.addFlashAttribute`. Spring lưu flash attributes **tạm thời** cho request sau redirect (thường sử dụng session/FlashMap), chuyển chúng vào model của GET kế tiếp rồi loại bỏ. Đây là cơ chế truyền trạng thái UI ngắn hạn như “đã lưu”, không phải kho lưu trữ workflow lâu dài.

Trong demo, `completedName` chỉ hiện trên trang kết quả ngay sau POST hợp lệ. Nó **không nằm trong query string** vì được gửi dưới dạng flash. Nếu user truy cập trực tiếp `GET /done` mà không có flash, controller redirect về `/form`.

Phân biệt:
- `redirectAttributes.addAttribute("id", id)`: thích hợp cho tham số path/query (có thể lộ trong URL, bookmark, log).
- `redirectAttributes.addFlashAttribute("saved", true)`: dữ liệu tạm truyền qua một lần redirect, không đưa vào query.
- `@SessionAttributes("draft")`: giữ dữ liệu nhiều request cho tới khi gọi `setComplete()`.
- Model bình thường: mặc định chỉ tồn tại trong phạm vi một request để render view.

### 5.3. Post/Redirect/Get và giới hạn

Nếu POST trả HTML trực tiếp, người dùng F5 có thể bị browser hỏi gửi lại POST. Với **PRG**, POST thành công trả redirect, browser truy cập GET, nên F5 trên trang GET không tự gửi lại POST. Tuy nhiên PRG **không bảo đảm xử lý giao dịch exactly-once**: double-click hai POST, retry từ client hoặc request song song vẫn có thể tạo tác dụng phụ trùng. Khi thực sự ghi DB/thanh toán, cần transaction, unique key/idempotency, kiểm soát race và validation nghiệp vụ.

Đối với POST thất bại do validation, trả view trực tiếp để giữ `BindingResult`; nếu cần redirect cả lúc lỗi, phải có cơ chế lưu lỗi/giá trị riêng, nhưng thường phức tạp không cần thiết.

## 6. Dự án Java 17/Spring Boot 3.3 có thể chạy

Ta tạo một ứng dụng Servlet MVC nhỏ, một controller cho form session workflow và controller khác cho trang kết quả. Việc tách **DoneController** giúp `GET /done` không tự khởi tạo lại `draft` qua phương thức `@ModelAttribute` của FormController sau khi session workflow được xóa.

### 6.1. Cấu trúc thư mục

~~~text
mvc-form-i18n-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/demo/
    │   │   ├── FormDemoApplication.java
    │   │   ├── WebConfig.java
    │   │   ├── RegistrationDraft.java
    │   │   ├── FormController.java
    │   │   └── DoneController.java
    │   └── resources/
    │       ├── messages.properties
    │       ├── messages_vi.properties
    │       ├── ValidationMessages.properties
    │       ├── ValidationMessages_vi.properties
    │       └── templates/
    │           ├── form.html
    │           └── done.html
    └── test/java/demo/
        └── FormFlowTest.java
~~~

Tất cả classes cùng package `demo` nên Spring Boot component scan được controllers/configuration. Demo không cần repository hay database.

### 6.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.13</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>mvc-form-i18n-demo</artifactId>
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
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
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

Maven dependencies gồm MVC/Tomcat, Thymeleaf view resolver, Jakarta Bean Validation và JUnit/MockMvc. Version `3.3.13` được cố định để minh họa các API Spring 6.1; với dự án production hãy chọn nhánh còn được hỗ trợ theo chính sách cập nhật của tổ chức.

### 6.3. FormDemoApplication.java

~~~java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FormDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(FormDemoApplication.class, args);
    }
}
~~~

Lớp này mở application context và embedded web server. Không cần `@EnableWebMvc` khi dùng cấu hình mặc định của Spring Boot: thêm annotation đó có thể vô tình làm mất một số MVC auto-configuration và view/converter mặc định.

### 6.4. WebConfig.java: chọn locale bằng session

~~~java
package demo;

import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        registry.addInterceptor(interceptor);
    }
}
~~~

Mặc định `lang=en`/không có lựa chọn riêng sẽ lấy English. Request `GET /form?lang=vi` khiến interceptor đổi locale đã lưu trong HTTP session. Các request cùng session sau đó không cần thêm tham số. Trong code này, chỉ các locale mà người dùng có bundle được thiết kế đầy đủ là `en` và `vi`; bản production nên giới hạn đúng danh sách lựa chọn, thay vì chấp nhận tùy ý một locale string.

### 6.5. RegistrationDraft.java: input DTO cho biểu mẫu

~~~java
package demo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationDraft {

    @NotBlank(message = "{form.name.required}")
    @Size(max = 80, message = "{form.name.length}")
    private String name = "";

    @NotBlank(message = "{form.email.required}")
    @Email(message = "{form.email.invalid}")
    private String email = "";

    @Size(max = 200, message = "{form.note.length}")
    private String note = "";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
~~~

Các field được bind bởi Spring thông qua setters. `@NotBlank` từ chối null, chuỗi rỗng hoặc whitespace; `@Email` kiểm một số quy tắc định dạng email nhưng **không xác nhận mailbox tồn tại**. `@Size` giới hạn độ dài khi validate. Nút **Lưu bản nháp** bên dưới không chạy full validation, vì có thể lưu bản nháp đang thiếu dữ liệu; production cần giới hạn kích thước request ở tầng HTTP và kiểm soát dữ liệu session để tránh bị lạm dụng lưu payload quá lớn.

### 6.6. FormController.java: tạo, giữ và kết thúc draft

~~~java
package demo;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/form")
@SessionAttributes("draft")
public class FormController {

    @ModelAttribute("draft")
    public RegistrationDraft draft() {
        return new RegistrationDraft();
    }

    @GetMapping
    public String showForm() {
        return "form";
    }

    @PostMapping(params = "action=save")
    public String saveDraft(
            @ModelAttribute("draft") RegistrationDraft draft,
            RedirectAttributes redirect) {

        // @SessionAttributes tự giữ draft qua các request cùng session.
        // Không validate bắt buộc để cho phép lưu bản nháp chưa hoàn chỉnh.
        redirect.addFlashAttribute("saved", true);
        return "redirect:/form";
    }

    @PostMapping(params = "action=finish")
    public String finish(
            @Valid @ModelAttribute("draft") RegistrationDraft draft,
            BindingResult errors,
            RedirectAttributes redirect,
            SessionStatus sessionStatus) {

        if (errors.hasErrors()) {
            // Cùng request, giữ BindingResult và field values cho Thymeleaf.
            return "form";
        }

        // Demo chỉ xác nhận form, chưa lưu database.
        redirect.addFlashAttribute(
            "completedName", draft.getName().trim());
        sessionStatus.setComplete();
        return "redirect:/done";
    }
}
~~~

Không cần gọi `model.addAttribute("draft", ...)` trong GET vì phương thức `@ModelAttribute("draft")` tạo model object khi thiếu. Khi có `draft` trong session, Spring lấy lại object cũ. **Cùng một trình duyệt/cùng session dùng nhiều tab có thể chia sẻ draft**, nên một workflow production phức tạp có thể cần workflow ID riêng thay cho một tên session attribute cố định.

`BindingResult` đứng ngay sau `@Valid @ModelAttribute`; nếu đặt sai vị trí, Spring có thể không chuyển field errors vào argument như mong đợi. Còn `SessionStatus.setComplete()` chỉ được gọi sau khi form hợp lệ, tránh mất draft khi người dùng cần sửa lỗi.

### 6.7. DoneController.java: tránh kết quả rỗng khi vào thẳng URL

~~~java
package demo;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DoneController {

    @GetMapping("/done")
    public String done(Model model) {
        if (!model.containsAttribute("completedName")) {
            return "redirect:/form";
        }
        return "done";
    }
}
~~~

Flash attribute `completedName` được chuyển sang model của GET ngay sau redirect. Nếu người dùng F5 khi flash đã hết, controller có thể chuyển về form vì không còn marker hoàn tất. Trong ứng dụng có database, nên dùng **ID của kết quả đã lưu và trang GET idempotent**, thay vì phụ thuộc hoàn toàn vào flash để xem lại dữ liệu sau vài phút.

### 6.8. Các resource bundles cho UI và validation

**src/main/resources/messages.properties**:

~~~properties
form.title=Registration form
form.language=Language
form.name=Full name
form.email=Email address
form.note=Notes
form.save=Save draft
form.finish=Submit registration
form.saved=Draft saved in this session.
form.done.title=Registration completed
form.done.message=Registration received for:
form.new=Start another registration
~~~

**src/main/resources/messages_vi.properties**:

~~~properties
form.title=Biểu mẫu đăng ký
form.language=Ngôn ngữ
form.name=Họ và tên
form.email=Địa chỉ email
form.note=Ghi chú
form.save=Lưu bản nháp
form.finish=Gửi đăng ký
form.saved=Đã lưu bản nháp trong phiên hiện tại.
form.done.title=Đăng ký hoàn tất
form.done.message=Đã nhận đăng ký của:
form.new=Tạo đăng ký khác
~~~

**src/main/resources/ValidationMessages.properties**:

~~~properties
form.name.required=Please enter your name.
form.name.length=Name must not exceed {max} characters.
form.email.required=Please enter your email.
form.email.invalid=Email format is invalid.
form.note.length=Notes must not exceed {max} characters.
~~~

**src/main/resources/ValidationMessages_vi.properties**:

~~~properties
form.name.required=Vui lòng nhập tên.
form.name.length=Tên không được vượt quá {max} ký tự.
form.email.required=Vui lòng nhập email.
form.email.invalid=Định dạng email không hợp lệ.
form.note.length=Ghi chú không được vượt quá {max} ký tự.
~~~

Spring Boot tự tìm `messages*.properties` cho UI khi auto-configure `MessageSource`; Jakarta Bean Validation sử dụng những key `{form....}` để resolve thông báo từ resource bundles tương ứng. `{max}` được validator nội suy từ thuộc tính `@Size(max=...)`. Khi kiểm thử i18n, hãy xác minh **cả label và lỗi** ở locale mong muốn. Không nên gộp bí mật, user input hoặc HTML chưa được escape vào message bundles.

### 6.9. form.html: view nhập liệu, switch locale, hiển thị lỗi

**src/main/resources/templates/form.html**:

~~~html
<!doctype html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title th:text="#{form.title}">Registration form</title>
</head>
<body>
  <nav>
    <span th:text="#{form.language}">Language</span>:
    <a th:href="@{/form(lang='en')}">English</a>
    <a th:href="@{/form(lang='vi')}">Tiếng Việt</a>
  </nav>

  <h1 th:text="#{form.title}">Registration form</h1>

  <p th:if="${saved}" th:text="#{form.saved}">
    Draft saved.
  </p>

  <form th:action="@{/form}" th:object="${draft}" method="post">
    <div>
      <label for="name" th:text="#{form.name}">Name</label>
      <input id="name" type="text" th:field="*{name}" maxlength="80">
      <span th:if="${#fields.hasErrors('name')}"
            th:errors="*{name}">Name error</span>
    </div>

    <div>
      <label for="email" th:text="#{form.email}">Email</label>
      <input id="email" type="email" th:field="*{email}">
      <span th:if="${#fields.hasErrors('email')}"
            th:errors="*{email}">Email error</span>
    </div>

    <div>
      <label for="note" th:text="#{form.note}">Notes</label>
      <textarea id="note" th:field="*{note}" maxlength="200"></textarea>
      <span th:if="${#fields.hasErrors('note')}"
            th:errors="*{note}">Notes error</span>
    </div>

    <button type="submit" name="action" value="save"
            th:text="#{form.save}">Save draft</button>
    <button type="submit" name="action" value="finish"
            th:text="#{form.finish}">Submit</button>
  </form>
</body>
</html>
~~~

`th:field` tạo/điền giá trị input từ `RegistrationDraft`; sau lỗi, nó được render lại cùng `BindingResult`. Trên browser thật, `type="email"` có client-side validation mặc định; để **kiểm thử riêng server-side validation**, MockMvc/cURL có thể gửi form data không hợp lệ trực tiếp. Trong production, nên bổ sung `aria-describedby`, liên kết lỗi với input, CSS error state, keyboard focus và hỗ trợ screen reader.

Không dùng `th:utext` với dữ liệu của người dùng. `th:text` thực hiện escaping HTML; trang kết quả dùng cùng nguyên tắc để tránh XSS.

### 6.10. done.html: view GET sau redirect

**src/main/resources/templates/done.html**:

~~~html
<!doctype html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title th:text="#{form.done.title}">Completed</title>
</head>
<body>
  <h1 th:text="#{form.done.title}">Completed</h1>
  <p>
    <span th:text="#{form.done.message}">Received for:</span>
    <strong th:text="${completedName}">Visitor</strong>
  </p>
  <a th:href="@{/form}" th:text="#{form.new}">New registration</a>
</body>
</html>
~~~

Trong demo, `completedName` là flash attribute tồn tại cho GET đích sau redirect. Dòng hiển thị bằng `th:text` giúp escape ký tự như `<`, `>` trong input. Nếu cần hiển thị lâu dài kết quả thực tế, lưu record vào database và thiết kế `GET /registrations/{id}` có authorization thích hợp.

## 7. Kiểm thử workflow bằng Spring Boot + MockMvc

MockMvc chạy HTTP request giả trong MVC stack, dùng thực controllers, view resolver, binder, validator, interceptor và session của ứng dụng. Có thể assert view name, status, Location, flash attributes, field errors và HTML đã render. Test này **không mở socket TCP**, không chứng minh reverse proxy/TLS; muốn test browser thực, dùng Selenium/Playwright hoặc test client HTTP tới RANDOM_PORT bổ sung.

**src/test/java/demo/FormFlowTest.java**:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class FormFlowTest {

    @Autowired
    MockMvc mvc;

    @Test
    void getFormRendersThymeleafView() throws Exception {
        mvc.perform(get("/form"))
            .andExpect(status().isOk())
            .andExpect(view().name("form"))
            .andExpect(model().attributeExists("draft"))
            .andExpect(content().string(containsString("Registration form")));
    }

    @Test
    void languageChoicePersistsWithinOneHttpSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(get("/form").session(session).param("lang", "vi"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                containsString("Biểu mẫu đăng ký")));

        mvc.perform(get("/form").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(
                containsString("Biểu mẫu đăng ký")));
    }

    @Test
    void saveDraftRedirectsAndKeepsFormDataInSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/form").session(session))
            .andExpect(status().isOk());

        mvc.perform(post("/form").session(session)
                .param("action", "save")
                .param("name", "Lan")
                .param("email", "lan@example.org")
                .param("note", "Gọi vào buổi sáng"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/form"))
            .andExpect(flash().attribute("saved", true));

        RegistrationDraft draft =
            (RegistrationDraft) session.getAttribute("draft");
        assertEquals("Lan", draft.getName());
        assertEquals("lan@example.org", draft.getEmail());

        mvc.perform(get("/form").session(session))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("draft"))
            .andExpect(content().string(containsString("Lan")));
    }

    @Test
    void invalidFinalSubmitReturnsSameViewWithFieldErrors()
            throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/form").session(session)
                .param("action", "finish")
                .param("name", " ")
                .param("email", "not-an-email")
                .param("note", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("form"))
            .andExpect(model().attributeHasFieldErrors(
                "draft", "name", "email"));

        // Invalid form chưa gọi SessionStatus.setComplete().
        RegistrationDraft draft =
            (RegistrationDraft) session.getAttribute("draft");
        assertEquals("not-an-email", draft.getEmail());
    }

    @Test
    void validFinalSubmitUsesPrgAndClearsDraft() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(get("/form").session(session))
            .andExpect(status().isOk());

        MvcResult result = mvc.perform(post("/form").session(session)
                .param("action", "finish")
                .param("name", "Lan")
                .param("email", "lan@example.org")
                .param("note", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/done"))
            .andExpect(flash().attribute("completedName", "Lan"))
            .andReturn();

        assertNull(session.getAttribute("draft"));

        mvc.perform(get("/done").session(session)
                .flashAttrs(result.getFlashMap()))
            .andExpect(status().isOk())
            .andExpect(view().name("done"))
            .andExpect(content().string(containsString("Lan")));
    }

    @Test
    void directDoneUrlWithoutFlashRedirectsToForm() throws Exception {
        mvc.perform(get("/done"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/form"));
    }
}
~~~

Có **6 phương thức `@Test`**: render view; đổi locale và duy trì qua session; lưu draft và redirect; validation thất bại; PRG thành công và xóa draft; truy cập trực tiếp trang done bị redirect. Những assertions kiểm tra cả HTML render và lifecycle, không chỉ gọi trực tiếp method controller.

Giải thích hai điểm dễ nhầm khi test:

- Dùng cùng `MockHttpSession` cho nhiều `mvc.perform(...)` để mô phỏng browser mang cùng session cookie. Mỗi `MockHttpSession` mới đại diện một phiên khác; nếu dùng session khác ở GET sau POST thì draft/locale **không còn** là điều đúng.
- `MvcResult.getFlashMap()` lấy flash data của POST và `.flashAttrs(...)` gắn nó vào GET mô phỏng sau redirect. MockMvc không tự chạy browser follow redirect; test phải chủ động thực hiện GET tiếp theo. Trong browser thật, `RedirectAttributes`/FlashMap của Spring xử lý lưu và tiêu thụ flash qua redirect.

Để chạy test:

~~~bash
mvn -q test
~~~

Nếu mọi test pass, Maven trả exit code **0**, báo cáo nằm trong `target/surefire-reports/`. Chế độ `-q` có thể không in dòng tóm tắt; không nhầm “không có output” với đã kiểm chứng thành công nếu chưa xem exit code. Đây là ví dụ hoàn chỉnh để thực hành; kết quả runtime **không được khẳng định là đã chạy trong lúc biên soạn bài**.

## 8. Chạy web app và quan sát các request

Khởi động project từ thư mục có `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

Nếu cổng 8080 trống và không có cấu hình khác, truy cập:

~~~text
http://localhost:8080/form
http://localhost:8080/form?lang=vi
http://localhost:8080/form?lang=en
~~~

Kịch bản thao tác:
1. Mở `/form?lang=vi`, kiểm nhãn **Biểu mẫu đăng ký** và **Lưu bản nháp**.
2. Nhập tên `Lan`, email `lan@example.org`, ghi chú tùy ý, bấm **Lưu bản nháp**. Quan sát DevTools Network: **POST /form → HTTP redirect → GET /form**; dữ liệu còn trên form.
3. Sửa email thành sai định dạng và bấm **Gửi đăng ký**. Trình duyệt có thể chặn submit tại client do input type=email; để chủ động kiểm server, dùng cURL/MockMvc hoặc DevTools thay đổi thuộc tính `type` thành text. Server sẽ trả lại view với lỗi.
4. Sửa email hợp lệ rồi gửi. Trình duyệt nhận redirect sang `/done`, hiện thông báo tên vừa nhập. Refresh ở URL kết quả không gửi lại POST.
5. Quay về `/form`: draft cũ đã được `SessionStatus.setComplete()` xóa nên object mới bắt đầu trống; lựa chọn `lang=vi` vẫn được giữ trong cùng HTTP session.

Nếu dùng cURL để tái hiện qua nhiều request, cần giữ cookie:

~~~bash
curl -c cookies.txt -b cookies.txt -i \
  "http://localhost:8080/form?lang=vi"

curl -c cookies.txt -b cookies.txt -i \
  -X POST "http://localhost:8080/form" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "action=save" \
  --data-urlencode "name=Lan" \
  --data-urlencode "email=lan@example.org" \
  --data-urlencode "note=Ghi chú"

curl -c cookies.txt -b cookies.txt -i \
  "http://localhost:8080/form"
~~~

File `cookies.txt` ở đây là **file do cURL tạo trên máy người học**, không phải file cấu hình Spring. Không commit cookie/token vào Git hoặc đăng tải trong bug report. Nếu bật Spring Security cho app, POST sẽ cần CSRF token hợp lệ và có thể yêu cầu authentication; các lệnh cURL demo lúc đó phải bổ sung quy trình bảo mật thích hợp.

## 9. Các điểm cần hiểu rõ trước khi chuyển demo sang production

### 9.1. Session lifetime và nhiều tab

Trong ứng dụng demo, một phiên browser có đúng một `draft` theo tên attribute. Hai tab cùng browser có thể dùng chung JSESSIONID, sửa cùng draft và gây tình trạng tab A ghi đè tab B. Để hỗ trợ nhiều form song song, thêm workflow ID duy nhất, scope trạng thái theo ID và kiểm soát concurrency.

Session không phải database vĩnh viễn: restart server, timeout, failover hoặc người dùng xóa cookie có thể làm mất draft. Nếu người dùng kỳ vọng có bản nháp lưu nhiều ngày/đa thiết bị, lưu DB với định danh tài khoản, quyền truy cập và lịch sử phiên bản; session chỉ giữ tham chiếu nhẹ.

Trong cluster, session có thể được lưu trong shared backing store như Redis qua Spring Session hoặc routing theo session affinity. Các object được đưa vào distributed session cần xem xét serialization, schema evolution, dung lượng, TTL và dữ liệu nhạy cảm. Không đặt mật khẩu, số thẻ hay dữ liệu nhạy cảm không cần thiết trong session.

### 9.2. CSRF, XSS và phân quyền

`POST /form` là state-changing action. Trong ứng dụng có đăng nhập/cookie, dùng Spring Security CSRF protection theo cấu hình; form Thymeleaf thường tích hợp hidden CSRF field khi Spring Security/RequestDataValueProcessor được cấu hình đúng, nhưng phải test response và submit thực. Không tự tắt CSRF để ví dụ chạy nếu môi trường production cần nó.

`th:text` và `th:field` hỗ trợ escaping HTML, nhưng mọi nội dung từ input vẫn phải được validate/sanitize phù hợp mục đích hiển thị. Không dùng `th:utext` cho nội dung chưa tin cậy. Session cookie nên có `HttpOnly`, `Secure` khi HTTPS, `SameSite` phù hợp, session timeout và session fixation protection theo security policy.

PRG giải quyết UX refresh, không phải bảo vệ khỏi POST trùng hay CSRF. Một action gửi đăng ký thực sự cần server-side uniqueness/idempotency và authorization riêng.

### 9.3. Form binding và mass assignment

Input `RegistrationDraft` chỉ chứa field mà browser được phép gửi. Nếu dùng JPA entity trực tiếp với `id`, `role`, `isAdmin`, `verified`, attacker có thể gửi thêm form parameters để sửa thuộc tính không xuất hiện trên UI. Vì vậy dùng DTO, chuyển sang entity trong service và thêm allowed fields/constructor binding khi cần.

Đối với chức năng **save draft** không validate đầy đủ, vẫn cần giới hạn request size, lengths, số phiên và tần suất để tránh abuse. Trong ứng dụng có nghiệp vụ quan trọng, save draft có thể validate một số invariant tối thiểu khác với validation hoàn tất.

### 9.4. URL redirect và dữ liệu nhạy cảm

`redirect:/done` là server-controlled relative URL. Không lấy tham số `next` do user đưa vào rồi `redirect:` tùy ý: đó có thể tạo **open redirect**. Nếu cần “quay lại trang trước”, dùng allowlist host/path đã kiểm tra.

`RedirectAttributes.addFlashAttribute` không đưa dữ liệu vào URL theo mặc định, nhưng không phải kho bí mật lâu dài: flash thường được giữ trong session và có thể bị request đồng thời tiêu thụ trước dự kiến. Tránh dùng flash để chuyển dữ liệu tài chính/credential, và không coi GET trang hoàn tất là nguồn sự thật nghiệp vụ.

### 9.5. Bản địa hóa thực tế

Trong production cần quyết định:
- Ngôn ngữ nào được hỗ trợ và cách fallback nếu thiếu message key.
- Việc lựa chọn ngôn ngữ được lưu trong session, cookie, tài khoản hay URL.
- Validation messages, ngày/giờ, số, đơn vị tiền tệ, plural forms có tuân locale không.
- Accessibility của language switch; có cần cập nhật `lang` attribute của thẻ `<html>` theo locale hiện tại không.
- Không chỉ kiểm nhãn form; error messages, email, trang thành công cũng cần kiểm vi/en.

Bản ví dụ đặt `<html lang="en">` tĩnh để giữ HTML đơn giản; với trang song ngữ thực tế nên bind thuộc tính lang theo locale (chẳng hạn `th:lang="${#locale.language}"`) để screen reader phát âm đúng. Cần test locale selector và `Accept-Language` trên môi trường thực, nhất là khi proxy/CDN cache HTML.

## 10. Ma trận kiểm thử cần có

| Tình huống | Kỳ vọng | Lớp test |
| --- | --- | --- |
| GET form mới | 200, view form, model draft mới | MockMvc |
| Switch `?lang=vi` | Label tiếng Việt và session ghi locale | MockMvc nhiều request cùng session |
| GET tiếp không có lang | Vẫn giữ tiếng Việt | MockMvc |
| POST save draft thiếu field | Redirect /form; draft vẫn lưu | MockMvc |
| GET sau save | Giá trị name/email/note còn trên form | MockMvc với MockHttpSession |
| POST finish với blank name | View form, field error name, không xóa draft | MockMvc |
| POST finish với email sai | View form, error email | MockMvc |
| POST finish hợp lệ | Redirect /done, flash completedName, draft bị xóa | MockMvc |
| GET /done sau redirect | View done và tên được escape | MockMvc/browser |
| GET /done trực tiếp | Redirect /form khi không có flash | MockMvc |
| Session mới | Locale/draft không rò từ session cũ | MockMvc dùng hai sessions |
| Session timeout | Draft mất theo contract, không có 500 | Integration |
| Two tabs cùng session | Xem rủi ro overwrite, quyết định workflow ID | Browser/integration |
| Security enabled | POST có CSRF hợp lệ; token sai bị từ chối | spring-security-test/integration |
| Reverse proxy | Cookie/redirect Location/scheme đúng qua HTTPS | E2E qua proxy |
| Double POST | Side effects không trùng theo idempotency contract | Service/integration |

Nếu muốn test HTML escaping, gửi tên chứa ký tự `<`/`>` qua luồng hợp lệ rồi kiểm output là escaped text, không chứa HTML/script được thực thi. Với mock HTML, tránh so sánh nguyên cả tài liệu vì whitespace/layout có thể thay đổi; kiểm đoạn text, view name, status và model quan trọng.

## 11. Những lỗi phổ biến và cách khắc phục

1. **Đặt form.html trong `static` thay vì `templates`:** Spring không tìm được logical view `form` bằng Thymeleaf. Đặt đúng `src/main/resources/templates/`.
2. **Dùng `@RestController` cho trang HTML:** trả string `"form"` như response body thay vì view name. Dùng `@Controller`.
3. **Thiếu `th:object` nhưng dùng `th:field="*{...}"`:** binding template lỗi. Đảm bảo model có object đúng tên.
4. **Quên `@Valid`/`spring-boot-starter-validation`:** các annotation DTO không được thực thi như dự tính. Test input sai phải có field errors.
5. **Đặt `BindingResult` không ngay sau form object:** không bắt lỗi binding/validation đúng. Sửa signature của handler.
6. **Redirect sau validation lỗi:** mất field errors hoặc form values nếu không lưu riêng. Return logical view `form` trong request lỗi.
7. **Dùng `AcceptHeaderLocaleResolver` với `LocaleChangeInterceptor`:** resolver này không hỗ trợ thay locale qua setLocale. Chọn SessionLocaleResolver/CookieLocaleResolver hoặc cơ chế thiết kế khác.
8. **Chỉ tạo `messages_vi.properties` mà quên bundle mặc định:** thiếu fallback và các key của ngôn ngữ khác; dùng bundle chuẩn mặc định.
9. **Hardcode UI text trong HTML/Java:** đổi ngôn ngữ chỉ hoạt động một phần. Dùng `#{key}` và resource bundles nhất quán.
10. **Nhầm flash với session state:** flash sống qua một redirect; draft cần session/workflow state qua nhiều request.
11. **Gọi `HttpSession.invalidate()` khi hoàn tất form:** có thể làm mất đăng nhập/locale. Dùng `SessionStatus.setComplete()` để xóa thuộc tính workflow do controller quản lý.
12. **Quên dọn draft sau submit:** lần đăng ký sau thấy dữ liệu cũ; gọi setComplete khi nghiệp vụ đã hoàn tất thật.
13. **Giả định PRG chống duplicate submit:** hai POST vẫn có thể chạy; cần idempotency/unique operation key khi có side effects.
14. **Lưu dữ liệu quá lớn/nhạy cảm trong session:** tốn bộ nhớ, tạo rủi ro bảo mật và vấn đề replication.
15. **Không test cùng session cookie:** GET sau POST mất draft, locale không được giữ, dễ kết luận sai cấu hình. Sử dụng cùng MockHttpSession/cookie.
16. **So sánh raw HTML nguyên trang:** test dễ vỡ theo format; kiểm contract (status, view, text/key elements/redirect/flash).
17. **Redirect từ input không kiểm tra:** có thể tạo open redirect. Chỉ cho phép URL nội bộ/allowlist.
18. **Dựa vào flash để xem lại đăng ký vĩnh viễn:** refresh/direct link không đủ dữ liệu; trang kết quả production phải đọc dữ liệu đã lưu có authorization.
19. **Không xử lý nhiều tab:** một draft theo session có thể bị ghi đè; thiết kế ID hoặc optimistic locking khi cần.
20. **Chỉ thử browser có client-side validation:** không chứng minh Bean Validation phía server. Dùng MockMvc/cURL gửi input vi phạm.

## 12. Checklist triển khai

Với một trang biểu mẫu Spring MVC hoàn chỉnh, hãy kiểm lần lượt:
1. Route GET/POST và logical view names đúng; template Thymeleaf đọc model object đã khai báo.
2. Dữ liệu được bind bằng DTO riêng, ràng buộc `@Valid` và `BindingResult` chạy trên server, field errors render đúng.
3. Labels, buttons, thông báo validation và done page có resource bundles; locale được chọn và duy trì đúng vòng đời.
4. Session state chỉ lưu điều cần thiết, có thời hạn và được xóa bằng `SessionStatus.setComplete()` sau thành công.
5. Redirect sau POST thành công, flash được tiêu thụ đúng; POST lỗi render lại form cùng dữ liệu và thông báo.
6. Security CSRF/XSS/authorization, kích thước request, session cookie và dữ liệu nhạy cảm có chính sách rõ.
7. MockMvc và integration tests xác minh happy path, invalid input, cùng/new session, redirect, flash, localization, timeout và double-submit theo rủi ro.
8. Nếu production nhiều instance, xác minh session storage/affinity, reverse proxy forwarded headers và redirect dưới HTTPS.

## 13. Kết luận

**View** hiển thị form và phản hồi cho người dùng; **binding/validation** chuyển request vào DTO an toàn; **bản địa hóa** chọn message theo locale; **session** lưu trạng thái bản nháp qua nhiều request; **redirect + flash** giúp hoàn tất POST theo PRG mà không mất thông báo nhất thời.

Những phần đó chỉ thật sự gắn kết khi được kiểm thử trong cùng một HTTP workflow. Với Spring MVC, thiết kế tách `FormController` và `DoneController`, `@SessionAttributes`, `SessionLocaleResolver`, `RedirectAttributes` và các test MockMvc cho phép kiểm tra rõ từng trách nhiệm. Khi chuyển sang ứng dụng thật, bổ sung persistence, security, concurrency và idempotency thay vì mở rộng session thành database.

## Tài liệu tham khảo

- Spring Framework — MVC view technologies và Thymeleaf: https://docs.spring.io/spring-framework/reference/web/webmvc-view.html
- Spring Framework — ModelAttribute / Data Binding: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/modelattrib-method-args.html
- Spring Framework — Validation: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html
- Spring Framework — @SessionAttributes: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/sessionattributes.html
- Spring Framework — Locale và SessionLocaleResolver: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/localeresolver.html
- Spring Framework — RedirectAttributes và Flash Attributes: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/redirecting-passing-data.html
- Spring Framework — MockMvc: https://docs.spring.io/spring-framework/reference/testing/mockmvc.html
- Thymeleaf — Spring form binding and validation: https://www.thymeleaf.org/doc/tutorials/3.1/thymeleafspring.html
- Spring Boot 3.3 — Testing: https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="operations-practice-009">Thực hành gọi dịch vụ: HTTP client, timeout và xử lý lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành gọi dịch vụ: HTTP client, timeout và xử lý lỗi

## 1. Mục tiêu: gọi dịch vụ bên ngoài một cách có kiểm soát

Trong ứng dụng Spring Framework, một HTTP endpoint thường phải gọi hệ thống khác: inventory, pricing, payment, identity, shipping hoặc API của đối tác. Việc gọi được URL chỉ là bước đầu. Ở production, kết nối có thể thất bại khi DNS/TCP/TLS, upstream trả 4xx/5xx, server trả dữ liệu không hợp lệ, mạng chậm bất thường hoặc client đã hủy request. Nếu mọi tình huống được bắt bằng `catch (Exception)` rồi trả `null`, người gọi không thể phân biệt hết hàng, upstream tạm thời hỏng hay lỗi lập trình.

Mục tiêu bài thực hành:

1. Biết chọn **`RestClient`**, **`WebClient`**, khi nào gặp `RestTemplate` và HTTP Service Client.
2. Đặt **connect timeout, read/response timeout và end-to-end deadline** theo SLA thay vì tùy tiện.
3. Phân loại **HTTP status error**, **transport/network failure**, **decode error**, **timeout** và **business response**.
4. Quyết định khi nào nên retry và khi nào retry có thể gây trùng nghiệp vụ.
5. Thiết kế một client adapter gọi dịch vụ, mapping lỗi sang hợp đồng của service/API, giữ bí mật và observability.
6. Chạy chương trình Java 17+ tự mở HTTP server cục bộ, gọi bằng Spring `RestClient` và quan sát ba tình huống **200, 404, response chậm gây read timeout**.

**Phiên bản tham chiếu:** Java 17+, Spring Framework 6.1.x, Spring Boot 3.3.x. `RestClient` có từ Spring Framework 6.1. Ví dụ hoàn chỉnh dùng Java SE + module `spring-web` và một local JDK HTTP server; không cần chạy Docker, database hoặc API public.

## 2. Chọn API HTTP client phù hợp với execution model

| Client | Mô hình và API | Dùng khi | Lưu ý |
| --- | --- | --- | --- |
| **`RestClient`** | Fluent, đồng bộ; `get().uri(...).retrieve().body(...)` | Service Spring MVC, batch đồng bộ, ứng dụng imperative | Thực thi I/O khi gọi method, giữ request thread chờ response |
| **`WebClient`** | Reactive; `Mono<T>`/`Flux<T>` | Spring WebFlux, streaming, async composition, số lượng I/O concurrent lớn | Publisher thường chạy khi subscribe; tránh `block()` trên event-loop |
| **`RestTemplate`** | API đồng bộ lâu đời | Dự án đang dùng, tương thích với integration cũ | Spring hiện khuyến khích `RestClient` cho API đồng bộ mới |
| **HTTP Service Client** | Java interface `@GetExchange`/`@PostExchange` + proxy | Muốn mô tả API đối tác bằng contract interface | Bên dưới vẫn cần chọn `RestClientAdapter`/`WebClientAdapter` và cấu hình transport/timeouts |

Chọn theo môi trường thật. Một Spring MVC controller có vài outbound REST calls thường không cần đổi toàn bộ chương trình sang reactive chỉ vì muốn dùng HTTP. Ngược lại, trong WebFlux, chặn luồng Netty event-loop bằng `RestClient` hoặc `WebClient.block()` có thể khiến throughput suy giảm và bị Reactor từ chối.

**Một HTTP client không phải một TCP connection.** `RestClient`/`WebClient` có thể tái sử dụng transport, connection pool và thread resources tùy thư viện nền. Nên khai báo/reuse client như một bean; không xây client, TLS context hoặc pool mới cho từng incoming request.

### Ví dụ hai kiểu gọi

```java
// Fragment synchronous, bên trong method có sẵn RestClient inventoryClient:
String body = inventoryClient.get()
        .uri("/items/{id}", 42)
        .retrieve()
        .body(String.class);
```

```java
// Fragment reactive, bên trong method có sẵn WebClient inventoryWebClient:
Mono<String> bodyPublisher = inventoryWebClient.get()
        .uri("/items/{id}", 42)
        .retrieve()
        .bodyToMono(String.class);
```

Với `RestClient`, thread gọi chờ cho đến khi response/exception. Với `WebClient`, `Mono` mô tả pipeline và request được thực hiện theo subscription. Cả hai mặc định biến nhiều status **4xx/5xx** thành exception khi dùng `retrieve()`, thay vì tự trả `null`. Chúng không bắt buộc dùng cùng loại connector bên dưới.

## 3. Hiểu toàn bộ hành trình một lời gọi HTTP

Một lần gọi không chỉ là khoảng thời gian server xử lý business logic:

```text
Incoming request
      │
      ▼
Application service / outbound adapter
      │
      ├─ URI resolve + DNS
      ├─ Chờ lấy connection từ pool (nếu dùng pool)
      ├─ TCP connect
      ├─ TLS handshake (HTTPS)
      ├─ Gửi request headers và body
      ├─ Chờ response headers
      ├─ Đọc response body (có thể stream lâu)
      └─ Decode JSON / mapping DTO
      │
      ▼
Thành công hoặc một lớp lỗi cụ thể
```

Các lớp lỗi:

| Giai đoạn | Tình huống lỗi | Dấu hiệu |
| --- | --- | --- |
| Tạo URI/input | URL sai, biến URI thiếu, ký tự sai | Có thể lỗi ngay tại phía client |
| DNS / TCP | Không resolve được host, refused, network unreachable | Thường I/O/transport exception, **không có HTTP status** |
| TLS | Chứng chỉ không tin cậy, hostname mismatch, handshake error | SSL/transport exception; không nên tắt xác minh TLS |
| Pool | Hết kết nối, pending acquire timeout | Phụ thuộc engine/pool, chưa nhận response |
| Đọc response | Read/response timeout, connection reset, EOF | Transport exception |
| Server đã trả response | 400, 401, 403, 404, 409, 429, 500, 503… | **HTTP status có thật**, body/headers có thể hữu ích |
| Decode | JSON sai/field sai, content type không tương thích, body quá lớn | Codec/message conversion exception |
| Nghiệp vụ | HTTP 200 nhưng JSON biểu diễn out-of-stock, denied, invalid state | Response hợp lệ về transport, cần hiểu semantics domain |

**Không đánh đồng HTTP 503 với connect timeout:** 503 nghĩa là đã nhận được một response từ một HTTP server/proxy; connect timeout xảy ra trước khi có response hợp lệ. Hai loại cần log và retry khác nhau.

**Không đánh đồng HTTP 404 upstream với HTTP 404 public:** phía inventory trả 404 nghĩa là SKU không có trên inventory; API public có thể trả 404, 422 hoặc một business outcome khác tùy use case. Không tự động “bê nguyên” mọi status của dịch vụ phụ ra ngoài.

## 4. Timeout: định nghĩa từng bộ đếm thời gian

Timeout phải được thiết kế theo ngân sách phục vụ request, không theo một con số “5 giây cho tất cả”.

| Loại timeout | Giới hạn điều gì? | Ví dụ |
| --- | --- | --- |
| **Connect timeout** | Thời gian tạo TCP connection tới endpoint | 300 ms |
| **Pool acquire timeout** | Thời gian chờ được cấp connection trong pool | 200 ms |
| **Read timeout** | Thời gian chờ thao tác đọc socket, thường là thời gian chờ dữ liệu kế tiếp | 900 ms |
| **Response timeout** | Timeout ở tầng HTTP engine khi chờ/nhận response theo semantics engine | 1 s |
| **Write timeout** | Thời gian ghi request body/socket, nếu engine hỗ trợ | 1 s |
| **End-to-end deadline** | Tổng ngân sách ca sử dụng, gồm gọi mạng và xử lý/tries | 2 s |

**Read timeout không đồng nghĩa total request timeout.** Chẳng hạn một stream gửi từng byte trong khoảng thời gian nhỏ hơn read timeout có thể kéo dài nhiều phút. Tương tự, chỉ cấu hình connect timeout không chặn server nhận connection nhưng treo khi gửi body.

Các bộ đếm còn phụ thuộc HTTP engine và version. DNS lookup không phải lúc nào cũng chịu connect timeout; TLS handshake, proxy và pending acquire có thể có bộ giới hạn khác. Muốn có **hạn tổng**, đặt deadline ở application/async pipeline hoặc cơ chế client hỗ trợ toàn bộ lifecycle, rồi truyền **remaining budget** tới các cuộc gọi phía dưới khi có thể.

### Ví dụ ngân sách và số lần thử

Một public endpoint có SLO 2 giây. Giả sử controller/serialization cần khoảng 250 ms, phần khác cần 150 ms, còn ~1,6 giây cho inventory call. Nếu mỗi outbound try cho phép response timeout 1,5 giây và tự retry **hai lần nữa**, bạn có nguy cơ giữ request lâu hơn ngân sách nhiều lần.

Chiến lược đúng hơn:

- Xác định **deadline tổng** cho call chain từ incoming request.
- Cấu hình connect và read/response timeout của transport để resource không treo.
- Giới hạn số lần retry và tổng thời gian retries; dùng remaining-time budget.
- Không retry khi deadline đã hết hoặc client hủy request.
- Đặt fallback chỉ khi business semantics cho phép (ví dụ cached informational data, không dùng fallback “thành công” giả cho tiền hoặc inventory reservation).

**Không có một bộ timeout duy nhất áp dụng được cho tất cả upstream.** Payment service có yêu cầu khác image metadata service; đặt cấu hình theo nhóm dependency, endpoint, kích thước payload và khả năng chống tải.

## 5. RestClient: cấu hình request factory và timeout

Trong Spring Framework 6.1, `RestClient` tạo từ `RestClient.builder()`, `RestClient.create()` hoặc `RestClient.Builder` do Spring Boot cung cấp. Timeout cụ thể nằm ở **ClientHttpRequestFactory** (thư viện HTTP nền), không có một phương thức `RestClient.timeout(...)` chung cho mọi engine.

Ví dụ dễ học: dùng `SimpleClientHttpRequestFactory` dựa trên JDK `HttpURLConnection`:

```java
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

SimpleClientHttpRequestFactory requestFactory =
        new SimpleClientHttpRequestFactory();
requestFactory.setConnectTimeout(Duration.ofMillis(300));
requestFactory.setReadTimeout(Duration.ofMillis(900));

RestClient client = RestClient.builder()
        .baseUrl("https://inventory.example.org")
        .requestFactory(requestFactory)
        .defaultHeader("X-Client-Name", "catalog-api")
        .build();
```

Đây là **fragment cấu hình**, URL ví dụ không dùng để chạy thật. `setConnectTimeout` và `setReadTimeout` đều có overload `Duration` trong Spring 6.1. `0` đối với các URLConnection timeout có thể biểu diễn timeout vô hạn; không đặt 0 vì nghĩ rằng đó là “không cần chờ”.

Tùy stack, client có thể dùng Apache HttpComponents, JDK `HttpClient`, Jetty hoặc Reactor Netty (WebClient), với các API configure timeout, pool, TLS khác nhau. **Không copy nguyên cấu hình của `SimpleClientHttpRequestFactory` sang mọi engine.** Kiểm tra dependency tree và native HTTP engine thực sự được chọn.

### Dùng Spring Boot-managed builder

Nếu là Spring Boot application, inject builder thay vì tùy tiện tạo client khác mà bỏ qua common customization:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class OutboundHttpConfiguration {

    @Bean
    RestClient inventoryRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(300);
        factory.setReadTimeout(900);

        return builder
                .baseUrl("https://inventory.example.org")
                .requestFactory(factory)
                .build();
    }
}
```

Đây là Spring configuration fragment. Lưu ý cấu hình request factory tường minh có thể thay đổi transport do Boot chọn, ảnh hưởng pooling, metrics hoặc TLS mặc định; verify sau khi wiring với Boot customizers. Không dùng một `RestClient.Builder` mutable như singleton để rồi thêm header riêng của nhiều upstream vào cùng builder không kiểm soát; Spring Boot thường cung cấp builder scope prototype.

Đặt base URL ở externalized configuration theo môi trường (ví dụ `@ConfigurationProperties` với validation). Tên host, credentials, connect/read timeouts và pool size không nên là các hằng số cố định bị sao chép khắp service.

## 6. RestClient: phân biệt 4xx/5xx và lỗi I/O

### 6.1 `retrieve()` và status lỗi

Với `RestClient`, `retrieve().body(Foo.class)` mặc định xử lý các HTTP status 4xx/5xx thành `RestClientResponseException` và các subclass cụ thể như `HttpClientErrorException`/`HttpServerErrorException`. Vì đã có response, exception mang **status**, có thể có **headers và response body**. Các lỗi mạng dùng ngoại lệ khác, phổ biến `ResourceAccessException`.

```java
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

String result;

try {
    result = inventoryRestClient.get()
            .uri("/items/{id}", "SKU-42")
            .retrieve()
            .body(String.class);
} catch (RestClientResponseException responseError) {
    int code = responseError.getStatusCode().value();
    // HTTP server/proxy thực sự trả status code này.
    // Mapping 404/429/503 phải theo semantics của dependency.
    throw responseError;
} catch (ResourceAccessException transportError) {
    // DNS, TCP, TLS, read timeout hoặc lỗi I/O khác.
    throw transportError;
}
```

**Fragment:** giả định `inventoryRestClient` có sẵn. Những `catch` trên chỉ minh họa taxonomy; production cần chuyển lỗi thành domain/adapter exceptions và logging an toàn thay vì cứ ném cùng exception qua mọi tầng.

Không bắt `Exception` rồi âm thầm trả `Optional.empty()`: nó biến network outage hoặc lỗi JSON thành “không có item”. `Optional.empty` chỉ phù hợp nếu API contract quy định rõ một HTTP 404 thật sự nghĩa là kết quả không tồn tại.

### 6.2 Xử lý có chủ đích với onStatus

Nếu một dịch vụ dùng 404 như giá trị bình thường nhưng 5xx phải báo unavailable, có thể:

```java
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

// Fragment bên trong một method đã có RestClient client.
String body = client.get()
        .uri("/inventory/{sku}", "ABC-1")
        .retrieve()
        .onStatus(HttpStatusCode::is5xxServerError,
                (request, response) -> {
                    throw new IllegalStateException(
                            "Inventory upstream failed: "
                            + response.getStatusCode());
                })
        .body(String.class);
```

`onStatus` trong ví dụ chỉ thay đổi handling cho 5xx. Với 404, default `retrieve()` vẫn throw; nếu muốn map 404 thành empty hoặc typed “not found”, cần thêm status handling và chọn API phù hợp. Đừng dùng `onStatus(s -> true, ...)` một cách tùy tiện rồi nuốt response body, bởi có thể làm mất xử lý status bình thường.

Một số trường hợp cần đọc cả status, headers và body mà không muốn cơ chế mặc định của `retrieve()`: cân nhắc `exchange((request, response) -> ...)` của RestClient. Với `exchange()`, **status handlers không được áp dụng tự động**; caller phải tự kiểm tra status và tiêu thụ/đóng response đúng hợp đồng. Cẩn thận response stream không bị đọc hai lần hoặc không được giải phóng.

### 6.3 Mapping lỗi sang ngôn ngữ ứng dụng

Khuyến khích có adapter thay vì để controller rải `catch`:

```text
HTTP controller
    → ProductService (use case)
      → InventoryGateway interface
        → HttpInventoryGateway (RestClient)
          → remote inventory API
```

`HttpInventoryGateway` có thể map 404 thành `InventoryItemMissing`, 409 thành `InventoryConflict`, 429/503 thành `InventoryTemporarilyUnavailable`, transport timeout thành `InventoryTimeout`. Những lỗi này là abstraction của dependency, để service biết xử lý use case nhưng không bị buộc dùng kiểu exception của Spring HTTP client.

Khi public API trả 502/503/504 hay 404, quyết định dựa **public contract**. 504 Gateway Timeout thường phù hợp khi service thực sự đóng vai gateway và quá hạn chờ upstream; 503 phù hợp khi không có dependency cần thiết; 502 phù hợp với invalid/bad upstream response. Không có quy tắc phải luôn map timeout thành 504 trong mọi hệ thống.

## 7. Retry: giới hạn số lần, backoff, jitter và tính idempotent

Retry có thể hữu ích cho lỗi **tạm thời**, nhưng cũng dễ tạo thêm tải và tạo bản ghi trùng. Quyết định cần dựa vào loại operation, lỗi, timeout budget và idempotency.

| Tình huống | Retry tự động? | Lý do |
| --- | --- | --- |
| GET idempotent, connect refused tạm thời | Có thể, số lần ít | Request chưa chắc đến server; quan sát lỗi mạng |
| GET 503 hoặc 429 | Có thể, theo `Retry-After` và ngân sách | Upstream báo không sẵn sàng/quá tải |
| GET 404 | Thường không | Không tìm thấy theo hợp đồng, không phải transient error |
| POST tạo order / charge payment | Không retry mù | Có thể server đã xử lý, response bị mất |
| POST có idempotency key được **server hỗ trợ và lưu bền** | Có thể theo policy | Server có cách nhận diện lần gọi trùng |
| JSON decode error / schema mismatch | Thường không | Retry không sửa được contract/schema |
| 401/403 | Thường không | Cần sửa auth/permission; không spam retry |
| Timeout sau khi đã gửi request | Chỉ khi semantics cho phép | Không biết server đã xử lý hay chưa |

### Nguyên tắc thiết kế

- Bắt đầu bằng **0 retry**, thêm retry cho lỗi đã được chứng minh là transient và an toàn.
- Tối đa 1–2 retries tùy SLA; exponential backoff + jitter để tránh mọi client đồng loạt retry.
- Đọc và kiểm tra `Retry-After` khi upstream trả 429/503: header có thể là số giây hoặc HTTP-date; không retry vượt hạn ngân sách.
- Timeout từng attempt nhỏ hơn total deadline; không cộng dồn vô hạn.
- Phân biệt các lỗi **đã có HTTP response** với **không có response**; connect timeout và read timeout có độ chắc chắn khác nhau về việc request đã đến upstream.
- Circuit breaker và bulkhead giảm cascading failures; retry không phải công cụ duy nhất.
- Giữ correlation ID, idempotency key và trace context ổn định qua các attempts, log số lần thử thay vì log secret.

**Ví dụ nghiệp vụ nguy hiểm:** POST `/payments` mất response vì mạng gián đoạn. Client thấy read timeout và gọi lại; nếu payment service không hỗ trợ idempotency, tài khoản có thể bị trừ hai lần. Timeout không chứng minh request đầu tiên đã bị hủy.

### Retry trong WebClient

`WebClient` sử dụng Reactor để composition; fragment:

```java
import java.time.Duration;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

// Fragment trong method: chỉ retry GET idempotent.
// isRetryable(Throwable) phải được implement theo contract riêng.
Mono<String> response = webClient.get()
        .uri("/catalog/{id}", 42)
        .retrieve()
        .bodyToMono(String.class)
        .retryWhen(Retry.backoff(2, Duration.ofMillis(100))
                .maxBackoff(Duration.ofMillis(500))
                .filter(this::isRetryable));
```

Đây là fragment, không phải chương trình độc lập: `webClient` và `isRetryable` chưa định nghĩa. `Retry.backoff(2, ...)` biểu diễn tối đa **hai lần retry sau lần thử đầu tiên**. Đừng lọc bằng `throwable -> true`: sẽ retry cả validation/schema errors. Retry operator trên publisher chỉ có tác dụng khi pipeline được subscribe. Nếu chỉ có tổng deadline, cân nhắc `timeout` ở phạm vi toàn pipeline và cancellation behavior của engine.

## 8. WebClient: thời gian chờ và xử lý lỗi reactive

Với Spring WebFlux, WebClient là lớp HTTP API; timeout vận chuyển nằm ở connector/native engine. Reactor Netty là một lựa chọn phổ biến:

```java
import java.time.Duration;
import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 300)
        .responseTimeout(Duration.ofSeconds(2));

WebClient client = WebClient.builder()
        .baseUrl("https://catalog.example.org")
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
```

Fragment này yêu cầu `spring-webflux` và `reactor-netty-http`. `CONNECT_TIMEOUT_MILLIS` là timeout cho connect TCP ở Netty, còn `responseTimeout` của Reactor Netty đặt timeout phản hồi theo semantics của transport. Với connection pool riêng, cấu hình thêm `pendingAcquireTimeout` và giới hạn kết nối bằng `ConnectionProvider`; không tạo pool theo từng request.

**Có thể đặt timeout ở mức publisher:**

```java
Mono<String> mono = client.get()
        .uri("/catalog/{id}", 42)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(3));
```

`Mono.timeout` theo dõi thời gian của reactive publisher và gây timeout signal nếu không có item/signal phù hợp trong giới hạn. Đây không tự cấu hình DNS/TCP/pool/socket timeouts, và không biến một stream `Flux` dài thành timeout tổng cố định nếu áp dụng sai vị trí. Đặt transport settings và total deadline có chủ đích.

### Xử lý status trong WebClient

```java
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

// Fragment: trả Mono<String>, không gọi block trên event-loop.
Mono<String> content = client.get()
        .uri("/catalog/{id}", 42)
        .retrieve()
        .onStatus(HttpStatusCode::is5xxServerError,
                response -> Mono.error(new IllegalStateException(
                        "Upstream status: "
                        + response.statusCode().value())))
        .bodyToMono(String.class);
```

Theo mặc định, `retrieve()` tạo `WebClientResponseException` cho response 4xx/5xx nếu không override; các lỗi connect/transport thường được bọc thành `WebClientRequestException`; decoder failures và Reactor `TimeoutException` cần được xem xét riêng. Trong `onStatus`, nếu custom handler cần đọc body để tạo lỗi, chỉ đọc một lần và giới hạn nội dung; tránh giữ reference tới response body sau khi stream đã được tiêu thụ.

**Tránh các sai lầm:** `block()` trên WebFlux event-loop, `subscribe()` thủ công từ mỗi controller request rồi không quản lý error, tạo WebClient mới theo mỗi item, dùng infinite `retry()`, tăng `maxInMemorySize` vô hạn thay vì streaming khi body lớn.

## 9. Ví dụ độc lập chạy được: Java 17 + RestClient + HTTP server cục bộ

Ví dụ dưới đây sử dụng JDK `HttpServer` để mô phỏng upstream, rồi dùng Spring `RestClient` gọi:

- `GET /inventory/ok`: trả 200, body `item-42`.
- `GET /inventory/missing`: trả 404, body `missing`.
- `GET /inventory/slow`: cố ý giữ response lâu hơn client read timeout; gây transport timeout.

**Không gọi API ngoài Internet khi chạy demo.** Gradle có thể cần mạng để tải Spring dependencies lần đầu. Demo dùng Spring Framework `spring-web` trực tiếp; không cần chạy `SpringApplication` hay server Spring Boot.

### 9.1 Cấu trúc thư mục

```text
http-client-practice/
├── build.gradle
└── src/main/java/demo/HttpClientPractice.java
```

Nội dung `build.gradle`:

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
    implementation 'org.springframework:spring-web'
}

application {
    mainClass = 'demo.HttpClientPractice'
}
```

Spring Boot BOM giữ bộ phiên bản Spring Framework đồng bộ. Project chỉ cần `spring-web` cho `RestClient` vì demo lấy text/plain chứ không dùng JSON DTO/Jackson. Phần HTTP server là API có sẵn trong JDK (module `jdk.httpserver`); cấu hình ở đây chạy trên classpath thông thường.

### 9.2 Java source đầy đủ

Tệp `src/main/java/demo/HttpClientPractice.java`:

```java
package demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public final class HttpClientPractice {

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/inventory", HttpClientPractice::serve);
        server.start();

        try {
            SimpleClientHttpRequestFactory transport =
                    new SimpleClientHttpRequestFactory();
            transport.setConnectTimeout(Duration.ofMillis(300));
            transport.setReadTimeout(Duration.ofMillis(150));

            RestClient client = RestClient.builder()
                    .baseUrl("http://127.0.0.1:"
                            + server.getAddress().getPort())
                    .requestFactory(transport)
                    .build();

            System.out.println("OK: " + fetch(client, "ok"));

            try {
                fetch(client, "missing");
                throw new AssertionError("Expected HTTP 404");
            } catch (RestClientResponseException error) {
                System.out.println("MISSING: HTTP "
                        + error.getStatusCode().value());
            }

            try {
                fetch(client, "slow");
                throw new AssertionError("Expected timeout");
            } catch (ResourceAccessException error) {
                System.out.println("SLOW: I/O failure (timeout expected)");
            }
        } finally {
            server.stop(0);
        }
    }

    private static String fetch(RestClient client, String item) {
        return client.get()
                .uri("/inventory/{id}", item)
                .retrieve()
                .body(String.class);
    }

    private static void serve(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        boolean slow = path.equals("/inventory/slow");
        int status = path.equals("/inventory/missing") ? 404 : 200;
        String body = path.equals("/inventory/ok")
                ? "item-42"
                : path.equals("/inventory/missing") ? "missing" : "too-late";

        if (slow) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set(
                    "Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } catch (IOException disconnected) {
            // Client đã timeout/ngắt kết nối trước khi server trả kết quả.
        } finally {
            exchange.close();
        }
    }
}
```

**Code này là chương trình đầy đủ.** Nó khởi động server trên địa chỉ loopback và port do hệ điều hành chọn, xây `RestClient` gắn request factory, rồi lần lượt gọi ba endpoint. `try/finally` đảm bảo dừng local server. Ví dụ chỉ làm việc với response text/plain; trong ứng dụng thật có thể thêm JSON converters, authentication, pool và observability.

### 9.3 Lệnh chạy và output

Trong thư mục `http-client-practice`, dùng Gradle đã cài:

```bash
gradle run
```

Nếu có Gradle Wrapper tạo sẵn: macOS/Linux `./gradlew run`; PowerShell Windows `.\gradlew.bat run`. Lưu ý thư mục được mô tả không tự chứa wrapper: hoặc cài Gradle, hoặc tạo wrapper phù hợp trước khi chạy.

**Các dòng stdout của chương trình trong điều kiện bình thường**, không tính log Gradle:

```text
OK: item-42
MISSING: HTTP 404
SLOW: I/O failure (timeout expected)
```

Case slow cố ý đợi 1.500 ms trong khi client read timeout 150 ms. Nếu môi trường chạy thay đổi đáng kể và kết quả slow không gây timeout đúng dự kiến, chương trình sẽ ném `AssertionError("Expected timeout")`; điều này giúp bài thực hành phát hiện tình huống timeout không được áp dụng thay vì âm thầm coi pass. Thông báo `I/O failure` trong `catch` là cách tóm tắt vì `ResourceAccessException` bao gồm nhiều lỗi transport; trong test production nên kiểm tra cause (ví dụ `SocketTimeoutException`) để xác nhận đó đúng là timeout.

Case 404 khác timeout: `retrieve()` đọc được HTTP response nên exception mang status code, còn slow làm request thất bại trước khi có response hợp lệ. Response body `missing` không bị tự chuyển thành `null` hoặc chuỗi rỗng.

### 9.4 Thay đổi thí nghiệm

1. Tăng `setReadTimeout` từ 150 ms lên 3.000 ms: `/inventory/slow` có thể trả `too-late` thành công, và ví dụ đang kỳ vọng timeout sẽ chủ động ném `AssertionError`. Đây là tín hiệu để sửa tiêu chí test nếu bạn chủ động đổi timeout.
2. Đổi `/inventory/missing` thành response 200 + body `not-found`: HTTP client thấy **thành công vận chuyển**; consumer phải diễn giải business response đúng contract, không thể dựa vào HTTP 404.
3. Mô phỏng server trả 503: thay status ở `serve` theo path, bắt `RestClientResponseException` và quan sát code 503, khác với `ResourceAccessException` của timeout.
4. Tắt server trước khi gọi: có thể nhận `ConnectException` được bọc trong `ResourceAccessException`. Đừng coi đó là HTTP 503.
5. Thử URL có hostname cần DNS hoặc HTTPS certificate sai trong môi trường test độc lập để phân biệt DNS/TLS failures; không tắt hostname/certificate verification để “sửa nhanh”.

Ví dụ thực hành không test được pool acquire timeout vì `SimpleClientHttpRequestFactory` không dùng một connection pool kiểu Apache HttpClient/Reactor Netty. Để đánh giá pool, cần chọn engine tương ứng và cấu hình load test riêng.

## 10. Kiểm thử HTTP client thực tế: tránh kiểm thử chỉ một mock method

Mỗi HTTP client adapter nên có **integration/contract tests** gửi request thật tới local stub server, WireMock, MockWebServer hoặc test HTTP server có kiểm soát. Mock mỗi lời gọi Java `restClient.get()...retrieve()` có thể tạo test yếu, không phát hiện serialization, URI encoding, timeout hay status mapping sai.

### Ma trận kiểm thử tối thiểu

| Test | Upstream được stub | Mong đợi ở outbound adapter |
| --- | --- | --- |
| Success | 200 + JSON/text hợp lệ | Parse DTO, trả result đúng |
| Not found | 404 + error body | Map lỗi theo business contract, không gọi retry vô hạn |
| Validation/client error | 400/422 | Bảo toàn lỗi phù hợp, không coi là transient |
| Unauthorized | 401/403 | Không vô tình log credential; auth refresh chỉ theo policy |
| Rate limit | 429 + `Retry-After` | Retry có giới hạn và deadline hoặc báo unavailable |
| Dependency outage | 503 | Chọn retry/circuit breaker theo nhu cầu |
| Read timeout | Server chậm hoặc giữ socket | Fail trong ngân sách, không treo vô hạn |
| Connect failure | Endpoint unreachable/refused | Phân biệt transport và HTTP response |
| Invalid JSON | Response 200 nhưng body sai | Decode error, không trả kết quả giả |
| Payload lớn | Response vượt giới hạn | Chính sách memory/streaming an toàn |
| Retry POST | Server xử lý request rồi mất response | Bảo vệ chống duplicate/idempotency nếu retry |
| Cancellation | Caller hủy request trước khi có response | Reactive cancellation/resource cleanup đúng |

Trong service nhiều dependencies, đo thời gian theo từng call và tổng request budget. Với test timing, dùng stub có kiểm soát và ngưỡng đủ rộng để tránh flaky test; đừng assert độ chính xác từng mili-giây trên CI chịu tải.

### Một unit test phân loại lỗi có ích

Bạn có thể unit test class phân loại exception độc lập:

```java
import java.net.SocketTimeoutException;
import org.springframework.web.client.ResourceAccessException;

class TimeoutClassifier {
    static boolean hasSocketTimeout(Throwable error) {
        for (Throwable current = error;
                current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}

// Ví dụ assertions (bên trong JUnit test):
// assertTrue(TimeoutClassifier.hasSocketTimeout(
//       new ResourceAccessException("read",
//           new SocketTimeoutException("Read timed out"))));
```

Đây là **fragment minh họa**, không có JUnit imports/test class đầy đủ. Lưu ý không phải mọi timeout đều là `SocketTimeoutException`: JDK HttpClient có thể dùng `HttpTimeoutException`; Reactor `TimeoutException` hoặc engine-specific exceptions xuất hiện ở tầng khác. Classifier production cần hỗ trợ các dạng phù hợp đúng thư viện.

## 11. Thiết kế một outbound adapter và hợp đồng lỗi rõ ràng

Đừng để HTTP controller hay service nghiệp vụ trực tiếp xử lý tên exception của Netty/JDK/Apache ở hàng chục nơi. Có thể tách:

```text
CheckoutController (HTTP boundary)
       │
       ▼
CheckoutService (application use case)
       │
       ▼
PriceGateway (interface)
       │
       └────► HttpPriceGateway (RestClient)
                  ├─ parse PriceQuote DTO
                  ├─ upstream 404 → PriceNotFound
                  ├─ upstream 429/503 → PriceUnavailable
                  ├─ timeout → PriceTimeout
                  └─ invalid JSON → PriceProtocolError
```

Ví dụ interface:

```java
record PriceQuote(String sku, String currency, long cents) {}

interface PriceGateway {
    PriceQuote getPrice(String sku);
}
```

Fragment này không tự compile độc lập khi chưa có implementation. Application service nhận một `PriceGateway` qua constructor, không nhận trực tiếp `RestClient`. Trong `HttpPriceGateway`, map lỗi dựa trên **upstream contract**, và đặt timeout, authentication, tracing, retry ở cùng một ranh giới.

Các mapping quan trọng:

- 404 từ product registry có thể là “SKU không tồn tại”; 404 từ một URL sai cấu hình lại là lỗi triển khai, không được tự coi là business outcome.
- 401/403 có thể là credential expired, permission denied của service account hoặc dấu hiệu thay đổi policy; không biến thành “product missing”.
- 429/503 có thể tạm thời; đừng trả HTTP 500 chung cho tất cả nếu public API cần signal outage rõ ràng.
- Decode/contract mismatch nên được quan sát với metric riêng và alert; nếu 200 trả JSON sai, HTTP đã “thành công” nhưng integration đã hỏng.
- Timeout có thể khiến outcome không xác định đối với lệnh thay đổi trạng thái; trạng thái “unknown” cần reconcile hoặc idempotency key.

### Ví dụ exception translation sang ProblemDetail

Trong HTTP service public, `@RestControllerAdvice` có thể map exception từ adapter sang payload chuẩn:

```java
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class DownstreamErrorAdvice {

    @ExceptionHandler(PriceTimeout.class)
    ProblemDetail timeout(PriceTimeout ex) {
        ProblemDetail result = ProblemDetail.forStatus(
                HttpStatus.GATEWAY_TIMEOUT);
        result.setTitle("Upstream timeout");
        result.setDetail("Pricing service did not respond in time");
        return result;
    }

    @ExceptionHandler(PriceUnavailable.class)
    ProblemDetail unavailable(PriceUnavailable ex) {
        ProblemDetail result = ProblemDetail.forStatus(
                HttpStatus.SERVICE_UNAVAILABLE);
        result.setTitle("Pricing unavailable");
        result.setDetail("Please retry later");
        return result;
    }
}
```

Đây là **Spring fragment**, `PriceTimeout` và `PriceUnavailable` cần tự định nghĩa như domain/adapter exceptions; không phải chương trình chạy độc lập. Phần detail chỉ là thông tin đã được kiểm soát, không tiết lộ internal hostnames, raw exception message, stack trace hoặc token. Có thể gắn `errorCode` và trace ID cho client chẩn đoán; giữ payload tương thích với OpenAPI contract.

## 12. Vận hành: an toàn, hiệu năng và quan sát

### 12.1 Đừng biến HTTP client thành SSRF proxy

Nếu một API chấp nhận URL hoặc hostname từ request của người dùng rồi gọi bằng `RestClient`/`WebClient`, có nguy cơ SSRF tới metadata endpoints, localhost, private network hay service nội bộ. Hãy dùng **base URLs cấu hình sẵn**, allowlist schemes/hosts/ports/IP ranges theo policy, kiểm soát redirects và DNS rebinding, tránh log URL chứa credentials. Không dùng token/header của service account cho host động không tin cậy.

Trên HTTPS, kiểm tra certificate chain và hostname. Đừng bật “trust all certificates” hoặc vô hiệu hóa TLS validation để khắc phục production connectivity. Proxy/firewall và service mesh có thể làm thay đổi timeout, headers và retry; biết lớp nào sở hữu retry để tránh retry multiplication.

### 12.2 Connection pooling, thread và tài nguyên

Một production HTTP client thường nên dùng connection pooling. Tối ưu các giới hạn:

- Max connections tổng và per-route/per-host.
- Pending acquire timeout, queue length, connection max idle/lifetime.
- TLS reuse, DNS caching, connection validation/eviction theo engine.
- Lifecycle shutdown pool, event-loop và client resources.
- Tổng số inbound requests đồng thời nhân fan-out outbound calls, tránh mỗi request tạo quá nhiều calls.

**Ví dụ:** nếu 300 concurrent requests mỗi request gọi 4 dịch vụ cùng lúc, hệ thống có thể tạo lượng concurrent outbound operations vượt tổng 300 nhiều lần. Không chỉ tăng pool vô hạn để “chữa timeout”; cần quan sát upstream capacity, concurrency limiter và kiểm soát bulkhead.

Với Reactor Netty, tài nguyên event-loop và connection pool thường được dùng chung; tách `ConnectionProvider` khi một upstream cần isolation, nhưng phải quản lý lifecycle. Với Apache HttpClient, pool và `RequestConfig` có các timeout riêng; tham số và hành vi không nhất thiết trùng JDK `HttpURLConnection`.

### 12.3 Observability và bảo mật dữ liệu

Metric/trace đáng theo dõi:

| Metric | Dùng để trả lời |
| --- | --- |
| Outbound requests/sec theo dependency, route template | Lưu lượng tới upstream nào tăng? |
| Latency p50/p95/p99, connect/read/total nếu có | Nghẽn ở connect hay response? |
| HTTP statuses 2xx/4xx/5xx và decode failures | Upstream trả lỗi hay payload không tương thích? |
| Timeout count, connect errors, pool acquire wait | Có dấu hiệu nghẽn transport/capacity? |
| Retry attempts, success after retry, circuit state | Retry có cứu request hay khuếch đại outage? |
| Concurrent requests, pool active/idle/pending | Có vượt khả năng của pool hoặc upstream không? |
| Tracing/correlation ID xuyên HTTP hops | Một request bị chậm ở hệ thống nào? |

Ghi log **route template**, dependency identifier, status, latency, trace ID, retry count; không ghi raw `Authorization`, session cookies, payload thanh toán, token, URL query chứa secret hay toàn bộ response body lỗi. Khi cần xem body để debug, áp giới hạn bytes, masking và access control. Đừng để interceptor log response body khiến body bị consume mất hoặc DataBuffer không được release.

### 12.4 Bộ quy tắc production thực tế

1. Định nghĩa outbound client theo dependency và tái sử dụng nó như bean.
2. Mỗi dependency có connect/read/response timeouts, concurrency và circuit policy đã đo.
3. Không chờ vô hạn; còn lại bao nhiêu deadline thì truyền xuống bấy nhiêu khi khả thi.
4. Retry chỉ những lỗi transient phù hợp với idempotency, có max attempts, backoff và jitter.
5. Dịch transport/status/decode errors sang các loại lỗi nghiệp vụ/gateway rõ ràng.
6. Kiểm tra JSON contract, payload size, encoding, response content type.
7. Thêm test với 200, 4xx, 5xx, timeout, connect failure, malformed body và duplicate POST.
8. Dùng TLS đúng, protect credentials, chặn SSRF và bảo vệ sensitive log data.
9. Có cơ chế fallback/resync hợp lệ; không phát sinh response “thành công giả” để giấu outage.
10. Quan sát P95/P99 latency, rate limit, pool pressure, retries và alerts theo upstream.

## 13. Lỗi phổ biến và cách sửa

| Sai lầm | Hậu quả | Sửa thế nào |
| --- | --- | --- |
| Không cấu hình timeout | Thread/resource bị treo lâu khi upstream chậm | Đặt timeout transport + deadline tổng |
| Bắt mọi `Exception` rồi trả `null` | “Không có dữ liệu” che khuất outage/bug | Phân loại status, I/O, decode, business error |
| Mặc định HTTP 404 upstream = HTTP 404 public | Lộ topology và sai business semantics | Adapter map theo hợp đồng từng dependency |
| Retry mọi POST timeout | Tạo order/payment trùng | Idempotency key, reconcile, giới hạn retry |
| Retry ngay nhiều lần khi 503 | Khuếch đại sự cố upstream | Backoff+jitter, circuit breaker, budget |
| Chỉ đặt connect timeout | Server nhận kết nối rồi treo vẫn giữ request | Thêm read/response + deadline |
| Coi `ReadTimeout` là tổng request duration | Stream dữ liệu liên tục có thể kéo dài | Dùng deadline hợp lý |
| Recreate client/pool cho mỗi request | Connection churn, thread/resource leak | Inject shared client/managed lifecycle |
| Gọi `block()` trong WebFlux event-loop | Nghẽn event-loop và lỗi Reactor | Compose reactive và trả `Mono/Flux` |
| Log full headers/body | Lộ token, PII và log quá tải | Mask/limit, log metadata cần thiết |
| Tắt TLS certificate validation | Mất khả năng xác minh server | Cấu hình truststore/PKI chính xác |
| Test chỉ mock fluent API chain | Không bắt URI/timeout/status/serialization sai | Gọi local stub HTTP server và assert contract |
| Ngộ nhận `convertAndSend`/HTTP client return là downstream đã xử lý business | Không biết kết quả không xác định | API semantics, confirmations, idempotency |

## 14. Câu hỏi tự kiểm tra và thực hành mở rộng

**Câu 1.** Một RestClient gọi `/inventory` nhận HTTP 503 sau 20 ms. Đây có phải connect timeout không?

**Trả lời:** Không. Đã nhận HTTP response 503, nên đây là lỗi status/protocol/service; connect timeout là lỗi kết nối trước response.

**Câu 2.** Một POST payment bị read timeout sau khi client gửi JSON; có thể retry ngay để chắc chắn không thất bại?

**Trả lời:** Không. Server có thể đã charge thành công và chỉ mất response. Cần idempotency key được upstream hỗ trợ hoặc cơ chế inquiry/reconciliation.

**Câu 3.** Vì sao có connect timeout và read timeout vẫn cần total deadline?

**Trả lời:** Chúng giới hạn các giai đoạn hoặc các thao tác I/O, chưa chắc giới hạn thời gian tổng từ đầu đến cuối, đặc biệt khi có pool wait, DNS, TLS, retries và streaming.

**Câu 4.** Có nên dịch mọi upstream 404 thành public 404?

**Trả lời:** Không. Cần hiểu contract và ý nghĩa thiếu dữ liệu ở dependency. Public 404 chỉ phù hợp nếu resource mà API public hứa cung cấp thật sự không tồn tại.

**Câu 5.** `WebClient.get().retrieve().bodyToMono(String.class)` đã gửi request chưa?

**Trả lời:** Đó là một publisher, thông thường request được kích hoạt khi subscribe. Trong WebFlux controller, trả publisher cho runtime, không tự `block()`.

**Câu 6.** `Retry.backoff(2, ...)` là tổng cộng hai attempts hay hai retries?

**Trả lời:** Tối đa hai **retries**, tức một lần đầu và thêm tối đa hai lần thử lại, nếu filter/policy cho phép.

**Câu 7.** Tại sao `ResourceAccessException` chưa đủ để kết luận “Read timed out”?

**Trả lời:** Lỗi này còn có thể do DNS, connect refused, TLS, reset/EOF và các loại I/O khác. Cần xem cause hoặc mã lỗi của engine để phân loại chính xác.

**Câu 8.** Khi tăng pool, latency vẫn cao và upstream bị 429, hướng giải quyết hợp lý là gì?

**Trả lời:** Giảm concurrency, tôn trọng rate limit/Retry-After, tối ưu fan-out và dùng bulkhead/circuit breaker. Tăng pool đơn thuần có thể làm upstream quá tải hơn.

**Bài tập 1:** Thay local server `/inventory/slow` bằng endpoint trả response text theo nhiều đoạn với khoảng cách giữa các đoạn dưới read timeout; quan sát vì sao read timeout không giới hạn toàn bộ stream. Thử thêm timeout tổng trong một pipeline phù hợp để so sánh.

**Bài tập 2:** Thêm endpoint trả 429 và `Retry-After: 1`; viết retry policy chỉ cho GET, không retry 404 và không vượt deadline. Đo số attempts bằng log có correlation ID.

**Bài tập 3:** Dùng `@RestControllerAdvice` trả ProblemDetail an toàn khi inventory timeout. Phân biệt response 503 từ upstream với trạng thái mất kết nối và decode error; thêm HTTP integration tests cho từng case.

## Tài liệu chính thức

- [Spring Framework — REST Clients, RestClient](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [Spring Framework — WebClient Configuration/Timeouts](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-builder.html)
- [Spring Framework — WebClient Error Handling](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-retrieve.html)
- [Spring Framework 6.1 — SimpleClientHttpRequestFactory](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/http/client/SimpleClientHttpRequestFactory.html)
- [Spring Framework 6.1 — RestClient Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/client/RestClient.html)
- [Spring Boot — REST Clients](https://docs.spring.io/spring-boot/reference/io/rest-client.html)
- [Project Reactor — Error Handling](https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="operations-practice-010">Thực hành cập nhật thời gian thực: lựa chọn SSE hoặc WebSocket</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành cập nhật thời gian thực: lựa chọn SSE hoặc WebSocket trong Spring Web

Một dashboard cần thấy đơn hàng mới mà không phải Refresh; một ứng dụng chat cần người dùng gửi và nhận tin nhắn liên tục. Cả hai đều là “cập nhật thời gian thực”, nhưng đường truyền có yêu cầu khác nhau. **Server-Sent Events (SSE)** dùng một HTTP response giữ mở để server gửi nhiều sự kiện **một chiều đến client**. **WebSocket** nâng cấp kết nối HTTP thành kênh message **hai chiều**.

Bài này tập trung vào Spring MVC (Servlet stack), Java 17+ và Spring Boot 3.3. Ta viết một ứng dụng mini có cả SSE và WebSocket, dùng chung dữ liệu, rồi kiểm tra cách truyền, reconnect và những giới hạn khi đưa lên môi trường thật. Mục tiêu là chọn giao thức từ nhu cầu thay vì mặc định WebSocket luôn “real-time hơn”.

## 1. Chọn giao thức theo hướng truyền và yêu cầu phục hồi

| Tiêu chí | SSE | WebSocket |
| --- | --- | --- |
| Luồng dữ liệu trên một kết nối | Server → client | Server ⇄ client |
| Transport | HTTP streaming, `text/event-stream` | HTTP handshake, `101 Switching Protocols`, sau đó là WebSocket frames |
| JavaScript browser | `EventSource` | `WebSocket` |
| Client gửi lên server | Dùng POST/PUT/HTTP riêng | `socket.send(...)` trên cùng kết nối |
| Tự reconnect | `EventSource` tự thử lại khi đứt; có `retry`/`Last-Event-ID` | Native WebSocket không tự reconnect; ứng dụng quản lý retry/re-subscribe |
| Nội dung message | UTF-8 text events, có thể mang JSON | Text/binary frame, format do ứng dụng/subprotocol quy định |
| Ví dụ phù hợp | Progress tác vụ, thông báo, feed, dashboard chỉ đọc | Chat, chỉnh sửa cộng tác, điều khiển hai chiều, sự kiện client thường xuyên |
| Khả năng replay | Có trường ID hỗ trợ thiết kế resume, nhưng **server phải lưu và phát lại** | Tự thiết kế id/ack/replay theo nghiệp vụ |
| Hạ tầng | Proxy phải cho streaming, tránh buffering và idle timeout quá ngắn | Proxy/load balancer phải xử lý handshake Upgrade, keepalive và WebSocket timeout |
| Chi phí | Kết nối HTTP tồn tại lâu, có thể chịu giới hạn connection/stream | Mỗi phiên WS giữ tài nguyên kết nối, cần xử lý client chậm và fan-out |

Cần server **chỉ đẩy cập nhật** còn các thao tác gửi lên ít và vốn đã có REST API: ưu tiên thử **SSE + POST**. Cần client gửi nhiều loại message thường xuyên trên cùng channel: thử **WebSocket**. Cần subscriptions, topic, routing người dùng và broker: cân nhắc **STOMP trên WebSocket**, khác với raw handler của bài này. Nếu dữ liệu chỉ đổi mỗi vài phút, HTTP polling có thể đáp ứng với độ phức tạp thấp hơn.

**Quan trọng:** SSE hay WebSocket đều không tự bảo đảm tin nhắn không mất, “exactly once”, phân phối thứ tự toàn hệ thống hoặc lịch sử khi reconnect. Đó là những quyết định của lớp ứng dụng, lưu trữ và event broker.

## 2. HTTP stream SSE khác WebSocket như thế nào?

### SSE: một response mang nhiều sự kiện

Trình duyệt mở HTTP request:

```http
GET /api/updates/stream HTTP/1.1
Accept: text/event-stream
```

Server giữ response mở và lần lượt gửi:

```text
retry: 3000
event: ready
data: connected

id: 17
event: update
data: {"id":17,"source":"http","message":"Có đơn mới","time":"2026-10-21T10:00:00Z"}

: keepalive

```

Mỗi event có thể gồm `id:`, `event:`, `data:`, `retry:` và **kết thúc bằng dòng trống**. Dòng mở đầu `:` là comment/heartbeat. JavaScript dùng `addEventListener("update", ...)` cho named event `update`; `onmessage` chỉ nhận unnamed `message` event.

`EventSource` tự kết nối lại và có thể gửi header `Last-Event-ID` khi đã nhận sự kiện có `id`. Nếu server không lưu lịch sử để tra cứu từ ID ấy thì reconnect chỉ giúp nhận các sự kiện **mới**, hoàn toàn không bảo đảm bù sự kiện đã lỡ.

### WebSocket: handshake rồi hai phía gửi frames

```text
Browser -- HTTP Upgrade /ws/updates --> Spring WebSocket
Browser <-- 101 Switching Protocols --  Spring WebSocket
Browser <--- text/binary frames ---> WebSocketHandler
```

Một raw WebSocket handler không có sẵn cơ chế subscribe topic, ack hay message schema. Bài này quy ước client gửi chuỗi ngắn; server phát JSON đến toàn bộ client đang kết nối. Nếu dùng STOMP, message sẽ có frames/destinations theo STOMP và phải cấu hình `@EnableWebSocketMessageBroker` với client STOMP tương ứng.

## 3. Khởi tạo project demo

Project dùng Spring Boot 3.3 và Java 17+ với dependencies Gradle (version được Boot quản lý):

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

`spring-boot-starter-web` hỗ trợ Spring MVC, embedded Servlet server; `spring-boot-starter-websocket` thêm WebSocket. Không cần thay thành WebFlux chỉ để dùng `SseEmitter`.

Cấu trúc tệp:

```text
src/main/java/com/example/realtime/
  RealtimeApplication.java
  LiveHub.java
  UpdatesController.java
  UpdateSocketHandler.java
  WebSocketConfig.java
src/main/resources/static/
  index.html
src/test/java/com/example/realtime/
  UpdatesControllerTest.java
```

Tệp `RealtimeApplication.java`:

```java
package com.example.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RealtimeApplication {
    public static void main(String[] args) {
        SpringApplication.run(RealtimeApplication.class, args);
    }
}
```

`@EnableScheduling` phục vụ heartbeat SSE theo lịch. Sử dụng scheduler được Spring quản lý, không tạo thêm một thread riêng cho mỗi subscriber.

## 4. Một trung tâm phát sự kiện cho cả SSE và WebSocket

Tệp `LiveHub.java` dưới đây lưu subscribers trong memory **của một JVM**. Các cập nhật từ REST hoặc WebSocket được serialize cùng kiểu JSON; có một chuỗi `id` để minh họa nhận diện event.

```java
package com.example.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class LiveHub {
    private final ObjectMapper mapper;
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentMap<String, SseEmitter> emitters =
            new ConcurrentHashMap<>();
    private final Set<WebSocketSession> sockets =
            ConcurrentHashMap.newKeySet();

    public LiveHub(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record UpdateEvent(
            long id, String source, String message, Instant time) {}

    public SseEmitter subscribeSse() {
        SseEmitter emitter =
                new SseEmitter(TimeUnit.MINUTES.toMillis(5));
        String subscriptionId = UUID.randomUUID().toString();

        emitters.put(subscriptionId, emitter);
        emitter.onCompletion(() -> emitters.remove(subscriptionId));
        emitter.onTimeout(() -> emitters.remove(subscriptionId));
        emitter.onError(error -> emitters.remove(subscriptionId));

        try {
            emitter.send(SseEmitter.event()
                    .name("ready")
                    .reconnectTime(3000)
                    .data("connected"));
        } catch (IOException | IllegalStateException ex) {
            emitters.remove(subscriptionId);
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public void addSocket(WebSocketSession session) {
        sockets.add(session);
    }

    public void removeSocket(WebSocketSession session) {
        sockets.remove(session);
    }

    public UpdateEvent publish(String raw, String source) {
        if (raw == null || raw.isBlank() || raw.length() > 200) {
            throw new IllegalArgumentException(
                    "message phải chứa 1..200 ký tự");
        }

        UpdateEvent event = new UpdateEvent(
                sequence.incrementAndGet(), source, raw.strip(), Instant.now());
        String json = toJson(event);

        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event()
                        .id(Long.toString(event.id()))
                        .name("update")
                        .data(json));
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(entry.getKey());
            }
        }

        for (WebSocketSession session : sockets) {
            if (!session.isOpen()) {
                sockets.remove(session);
                continue;
            }
            try {
                // Việc gửi đồng thời trên cùng một session cần được serialize.
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                    }
                }
            } catch (IOException | IllegalStateException ex) {
                sockets.remove(session);
            }
        }

        return event;
    }

    @Scheduled(fixedDelay = 20_000)
    public void heartbeat() {
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(
                        SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(entry.getKey());
            }
        }
    }

    private String toJson(UpdateEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không tạo được JSON", ex);
        }
    }
}
```

Cơ chế: `subscribeSse()` tạo `SseEmitter` giữ HTTP response mở; `onCompletion`, `onTimeout` và `onError` dọn emitter khỏi map. `publish(...)` tạo event rồi gửi cho cả SSE subscribers lẫn WebSocket sessions. `heartbeat()` phát comment định kỳ để hỗ trợ giữ kết nối qua idle timeout. `@Scheduled(fixedDelay=20_000)` không bảo đảm mọi proxy flush stream; phần proxy cần được cấu hình riêng.

Mã demo chủ động giới hạn message 200 ký tự và sử dụng Jackson để xuất JSON hợp lệ. JavaScript parse lại bằng `JSON.parse`. JSON được gửi như **văn bản** khi gọi `.data(json)` cho SSE hoặc `new TextMessage(json)` cho WebSocket. Nếu sau này chuyển sang gửi trực tiếp object và converter JSON, phải kiểm tra cách serialize để tránh double-encoding chuỗi JSON.

**Ba giới hạn đáng nhớ:**

- `AtomicLong` sinh ID khác nhau trong một JVM, không lưu bền, reset khi restart và không tự đảm bảo thứ tự broadcast tuyệt đối khi nhiều publisher đồng thời.
- Vòng lặp fan-out ở `publish` làm việc đồng bộ và có thể chậm khi nhiều client hoặc socket bị nghẽn. Với production, cần broker, worker bất đồng bộ, outbound buffer có giới hạn, timeout/slow-consumer policy; với Spring WebSocket có `ConcurrentWebSocketSessionDecorator` khi phù hợp.
- Map/set là state cục bộ của **một instance**. Khi load balancing qua nhiều instance, không tự động broadcast sang các client đang nối với instance khác.

## 5. Triển khai SSE bằng Spring MVC

Tệp `UpdatesController.java` cung cấp HTTP stream cho browser và POST endpoint để người dùng gửi một cập nhật khi đang ở chế độ SSE:

```java
package com.example.realtime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class UpdatesController {
    private final LiveHub hub;

    public UpdatesController(LiveHub hub) {
        this.hub = hub;
    }

    @GetMapping(
            path = "/api/updates/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return hub.subscribeSse();
    }

    @PostMapping(
            path = "/api/updates",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public LiveHub.UpdateEvent publish(@RequestBody UpdateRequest request) {
        try {
            return hub.publish(request.message(), "http");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    public record UpdateRequest(String message) {}
}
```

Browser chỉ nhận SSE qua `GET /api/updates/stream`. Khi cần thực hiện hành động, browser gửi `POST /api/updates` như một HTTP request **khác**, rồi kết quả được broadcast xuống stream của tất cả subscribers. Vì vậy ứng dụng dùng SSE vẫn tương tác theo hai hướng *ở mức toàn hệ thống*; SSE stream vẫn chỉ truyền một chiều.

Đây là **demo localhost không có xác thực**. Không triển khai endpoint POST broadcast công khai ra Internet. Cần xác thực người gửi, kiểm tra quyền phát/nhận sự kiện theo người dùng/tenant, giới hạn tốc độ, CSRF khi dùng cookie-based session, lọc payload nhạy cảm và giới hạn số kết nối.

## 6. Triển khai raw WebSocket handler

Tệp `UpdateSocketHandler.java` xử lý text từ browser. Khi nhận text hợp lệ, nó gọi cùng `hub.publish` nhưng với nguồn `websocket`:

```java
package com.example.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class UpdateSocketHandler extends TextWebSocketHandler {
    private final LiveHub hub;

    public UpdateSocketHandler(LiveHub hub) {
        this.hub = hub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        hub.addSocket(session);
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session, TextMessage message) throws Exception {
        try {
            hub.publish(message.getPayload(), "websocket");
        } catch (IllegalArgumentException ex) {
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session, CloseStatus status) {
        hub.removeSocket(session);
    }

    @Override
    public void handleTransportError(
            WebSocketSession session, Throwable exception)
            throws Exception {
        hub.removeSocket(session);
        if (session.isOpen()) {
            session.close();
        }
    }
}
```

`afterConnectionEstablished` ghi session vào tập đang hoạt động; `afterConnectionClosed`/`handleTransportError` dọn khi kết nối kết thúc. Việc đóng socket với `CloseStatus.BAD_DATA` cho biết dữ liệu client không hợp lệ. Đoạn này chỉ hỗ trợ text frame; nếu bài toán cần binary hoặc protocol phức tạp, phải thiết kế handler tương ứng.

Tệp `WebSocketConfig.java`:

```java
package com.example.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final UpdateSocketHandler handler;

    public WebSocketConfig(UpdateSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/updates");
    }
}
```

Client dùng `ws://localhost:8080/ws/updates` khi chạy HTTP local; website HTTPS phải dùng `wss://`. Spring kiểm tra Origin của WebSocket handshake theo cấu hình; đừng vô tư thêm `setAllowedOrigins("*")`. Origin chỉ là một kiểm soát phía browser, không thay thế authentication hoặc authorization cho mỗi hành động socket.

Đây là raw handler. Nếu cần nhiều phòng chat, subscription, `@MessageMapping` và `SimpMessagingTemplate`, hãy xây cấu hình STOMP qua `@EnableWebSocketMessageBroker`. STOMP là **protocol ứng dụng chạy trên WebSocket**, nên client STOMP không thể dùng nguyên URL raw handler mà tự nhiên có các destination `/topic/...`.

## 7. Giao diện thử hai cách

Tạo `src/main/resources/static/index.html`. Spring Boot phục vụ tệp này tại `http://localhost:8080/` trong cấu hình web mặc định:

```html
<!doctype html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <title>Spring Realtime: SSE và WebSocket</title>
</head>
<body>
  <h1>Cập nhật thời gian thực</h1>
  <button id="open-sse">Dùng SSE</button>
  <button id="open-ws">Dùng WebSocket</button>
  <button id="stop">Ngắt kết nối</button>
  <p id="status">Chưa kết nối</p>

  <label for="input">Thông điệp:</label>
  <input id="input" maxlength="200" value="Cập nhật mới">
  <button id="publish">Phát cập nhật</button>
  <ol id="updates"></ol>

  <script>
    let sse = null;
    let ws = null;
    let mode = null;
    const status = document.getElementById("status");
    const updates = document.getElementById("updates");

    function write(text) {
      const li = document.createElement("li");
      li.textContent = text;
      updates.prepend(li);
    }

    function showEvent(json) {
      const event = JSON.parse(json);
      write("#" + event.id + " [" + event.source + "] " + event.message);
    }

    function disconnect() {
      if (sse !== null) {
        sse.close();
        sse = null;
      }
      if (ws !== null) {
        ws.close();
        ws = null;
      }
      mode = null;
      status.textContent = "Chưa kết nối";
    }

    document.getElementById("open-sse").onclick = () => {
      disconnect();
      mode = "sse";
      sse = new EventSource("/api/updates/stream");
      status.textContent = "SSE đang kết nối";
      sse.addEventListener("ready", () => {
        status.textContent = "SSE sẵn sàng";
      });
      sse.addEventListener("update", e => showEvent(e.data));
      sse.onerror = () => {
        status.textContent = "SSE gián đoạn, browser sẽ thử nối lại";
      };
    };

    document.getElementById("open-ws").onclick = () => {
      disconnect();
      mode = "ws";
      const scheme = location.protocol === "https:" ? "wss:" : "ws:";
      ws = new WebSocket(scheme + "//" + location.host + "/ws/updates");
      status.textContent = "WebSocket đang kết nối";
      ws.onopen = () => status.textContent = "WebSocket sẵn sàng";
      ws.onmessage = e => showEvent(e.data);
      ws.onclose = () => {
        status.textContent = "WebSocket đã đóng, hãy chọn kết nối lại";
      };
      ws.onerror = () => status.textContent = "WebSocket gặp lỗi";
    };

    document.getElementById("stop").onclick = disconnect;

    document.getElementById("publish").onclick = async () => {
      const message = document.getElementById("input").value.trim();
      if (!message || message.length > 200) {
        write("Thông điệp phải chứa 1..200 ký tự");
        return;
      }

      if (mode === "ws") {
        if (!ws || ws.readyState !== WebSocket.OPEN) {
          write("WebSocket chưa sẵn sàng");
          return;
        }
        ws.send(message);
      } else if (mode === "sse") {
        try {
          const response = await fetch("/api/updates", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({message})
          });
          if (!response.ok) {
            write("POST lỗi HTTP " + response.status);
          }
        } catch (error) {
          write("Không gửi được POST");
        }
      } else {
        write("Hãy chọn SSE hoặc WebSocket");
      }
    };
  </script>
</body>
</html>
```

**So sánh quan sát được:** khi chọn SSE, JavaScript tạo `EventSource`, nhưng nút phát cập nhật thực hiện `fetch(POST)`. Khi chọn WebSocket, cùng nút gọi `ws.send(message)` ngay trên socket đã mở. Cả hai có thể nhận sự kiện của nhau vì server phát đến cùng một `LiveHub`.

HTML dùng `textContent` để không diễn giải message thành HTML/script. Ứng dụng thật vẫn phải kiểm tra quyền đọc/ghi từng channel và có cơ chế bảo vệ khỏi spam, payload độc hại, thông tin nhạy cảm.

## 8. Chạy thử và kiểm tra bằng trình duyệt/curl

Từ project đã cấu hình Boot:

```bash
./gradlew bootRun
```

Windows PowerShell: `.\gradlew.bat bootRun`. Mở `http://localhost:8080/` ở hai tab. Tab A chọn SSE, tab B chọn WebSocket. Từ tab A gửi “Có đơn mới”, hai tab sẽ nhận sự kiện có `source=http`. Từ tab B gửi “Gửi qua WebSocket”, hai tab sẽ nhận sự kiện có `source=websocket`. Đây là bằng chứng cả hai client đều nhận cùng nguồn phát **trong một JVM**.

Mở terminal Bash A để xem SSE thô:

```bash
curl -N -H "Accept: text/event-stream" \
  http://localhost:8080/api/updates/stream
```

`curl -N` tắt buffer đầu ra của curl, cho thấy sự kiện ngay khi server flush. Từ terminal B phát cập nhật:

```bash
curl -i -X POST http://localhost:8080/api/updates \
  -H "Content-Type: application/json" \
  -d '{"message":"Có đơn mới"}'
```

Trên PowerShell có thể dùng lệnh trên **một dòng**:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/updates" -ContentType "application/json" -Body '{"message":"Có đơn mới"}'
```

Bên nhận SSE dự kiến thấy cấu trúc:

```text
id: 1
event: update
data: {"id":1,"source":"http","message":"Có đơn mới","time":"..."}

```

`id` và `time` thực tế thay đổi theo thứ tự phát và thời gian máy chủ. Đây là ví dụ dạng dữ liệu, **không phải stdout được ghi lại sau khi chạy code**. HTTP response `GET /api/updates/stream` vẫn mở và có thể nhận nhiều sự kiện.

Để kiểm tra handshake WebSocket, mở Developer Tools → Network → WS và chọn **Dùng WebSocket**. Handshake thành công thông thường có status `101 Switching Protocols`; danh sách Frames hiển thị text client gửi và JSON server phát. Nếu URL `ws://` được gọi từ website HTTPS, trình duyệt có thể chặn mixed content; dùng `wss://` và reverse proxy TLS phù hợp.

### Bốn thí nghiệm phân biệt hai giao thức

1. **Hai tab:** dùng SSE ở A, WebSocket ở B, gửi từ từng bên; cùng nhìn thấy update để xác nhận fan-out.
2. **Mất mạng:** ngắt kết nối tạm. `EventSource` có thể tự thử lại theo `retry`; WebSocket trong demo chỉ báo đã đóng và đợi người dùng chọn lại. Ứng dụng WS thực cần backoff/jitter và tái xác thực khi reconnect.
3. **Khởi động lại server:** các ID trong `AtomicLong` reset; sự kiện phát lúc browser offline không được replay. Đây là giới hạn demo, không phải lỗi riêng của SSE.
4. **Thử dữ liệu sai:** POST message chỉ có dấu cách phải trả 400; WebSocket gửi text rỗng có thể bị đóng với close code invalid data. Cả hai đường vào đều kiểm tra độ dài 1..200 ký tự.

## 9. Kiểm thử Spring MVC và ranh giới kiểm thử

Tệp `src/test/java/com/example/realtime/UpdatesControllerTest.java`:

```java
package com.example.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UpdatesControllerTest {
    @Autowired
    MockMvc mvc;

    @Test
    void publishesValidPayload() throws Exception {
        mvc.perform(post("/api/updates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello"))
                .andExpect(jsonPath("$.source").value("http"));
    }

    @Test
    void rejectsBlankPayload() throws Exception {
        mvc.perform(post("/api/updates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
```

Chạy:

```bash
./gradlew test --tests com.example.realtime.UpdatesControllerTest
```

Kiểm thử trên nạp context và dùng `MockMvc` để kiểm tra HTTP controller. Nó **không thực hiện TCP streaming hay WebSocket handshake**, nên chưa đủ chứng minh browser thực sự nhận cập nhật. Kiểm thử tích hợp cho transport cần chạy embedded server trên cổng thực, dùng SSE client đọc nhiều chunk và WebSocket client thực sự connect, gửi/nhận/đóng kết nối. Kiểm tra hai subscribers, timeout, disconnect, lỗi, và chậm mạng với timeout hữu hạn; không dùng sleep tùy tiện làm bằng chứng nhận dữ liệu.

## 10. Các vấn đề vận hành cần thiết trước production

### 10.1. Idle timeout, heartbeat, proxy buffering

SSE là HTTP streaming. Reverse proxy có thể giữ dữ liệu trong buffer và chỉ gửi cho browser sau khi đủ kích thước, khiến event đến muộn; cấu hình không buffer stream, timeout đủ lớn và flush thường xuyên. Heartbeat comment `: keepalive` giúp hạn chế kết nối bị coi là idle, nhưng proxy nào cũng có cấu hình riêng. WebSocket cần proxy hỗ trợ handshake Upgrade, connection timeout, ping/pong và termination có kiểm soát. Cần đo trên đúng proxy/load balancer đang dùng thay vì suy từ kết quả chạy localhost.

Browser HTTP/1.1 có thể giới hạn số connection tới cùng origin; nhiều tab mở SSE dùng chung connection budget. HTTP/2 multiplex giúp cải thiện nhưng còn ràng buộc max concurrent streams. WebSocket cũng giữ connection/resource mỗi client, nên cả hai đều có chi phí khi số người dùng lớn.

### 10.2. Security và phân quyền

- Với SSE, native `EventSource` không cho đặt tùy ý header `Authorization`. Cookie-based session có thể dùng; cross-origin cần CORS/credentials đúng cách. Tránh token nhạy cảm trong URL query nếu có giải pháp khác.
- WebSocket bắt đầu bằng HTTP handshake. Kiểm tra origin, principal và quyền vào channel, rồi **tiếp tục kiểm tra quyền từng message** trong handler/protocol. Origin check không thay thế authentication; handshake thành công không có nghĩa mọi message về sau hợp lệ.
- Endpoint POST dùng cookie/session thường cần CSRF token nếu Spring Security bật; thiếu token có thể dẫn tới HTTP 403. Kèm rate limit, giới hạn payload và quyền phát sự kiện.
- Demo broadcast tới tất cả sessions. Hệ thống có nhiều user/tenant phải chia audience, lọc event theo quyền và dọn quyền truy cập khi session không còn hợp lệ.

### 10.3. Reconnect khác replay

Khi stream đứt, client có thể nối lại, nhưng các event xảy ra khi offline không tự xuất hiện. Muốn phục hồi, lưu sự kiện vào event store/broker có offset bền vững; SSE server đọc `Last-Event-ID`, WebSocket app gửi last-seen sequence khi reconnect, rồi phát lại phần thiếu. Client phải xử lý duplicate bằng event ID, vì reconnect/retry có thể khiến một sự kiện được nhận hơn một lần. Chọn delivery semantics và idempotency theo yêu cầu: UI hiển thị trạng thái mới nhất khác với lệnh thanh toán không được xử lý trùng.

### 10.4. Scale, thứ tự và slow consumers

```text
Producer → Event broker / durable log
              ├→ Instance A → SSE + WS clients A
              └→ Instance B → SSE + WS clients B
```

Dùng `ConcurrentHashMap` trong một JVM có nghĩa publisher ở instance A không tự biết clients ở instance B. Sticky session chỉ giúp client tiếp tục đến một node trong lúc còn kết nối, **không giải quyết broadcast đa node**. Dùng Redis pub/sub, Redis Streams, Kafka, RabbitMQ hoặc broker khác tùy nhu cầu fan-out, persistence, ordering, latency và chi phí.

Event ID tăng dần ở một instance không chứng minh thứ tự gửi tuyệt đối khi nhiều threads publish song song. Với dữ liệu quan trọng, định nghĩa ordering theo từng stream/partition và dùng offset/version rõ ràng. Với slow consumers, đặt queue/buffer **có giới hạn**, chính sách drop/terminate/replay, đo độ trễ và memory. Không để một client chậm làm tắc cả luồng phát.

### 10.5. Quan sát hoạt động và dọn tài nguyên

Đo số connection SSE/WS đang mở, thời gian kết nối, reconnect count, tỷ lệ lỗi gửi, fan-out latency, message backlog, slow-client disconnect và số bytes/message. Dọn `SseEmitter` khi complete/timeout/error; dọn `WebSocketSession` khi close/error. Client rời trang nên gọi `EventSource.close()` hoặc `WebSocket.close()`. Với Spring MVC async SSE, thread Servlet có thể được trả lại pool trong lúc connection còn mở, nhưng socket và các tài nguyên khác vẫn tồn tại; số lượng connection lớn phải được capacity plan.

## 11. Lỗi phổ biến và cách xử lý

| Triệu chứng | Nguyên nhân hay gặp | Cách xác minh |
| --- | --- | --- |
| `EventSource` kết nối nhưng JS không nhận event | JS dùng `onmessage` trong khi server phát named `event: update` | Dùng `addEventListener("update", ...)` |
| SSE chỉ hiện dữ liệu sau một lúc | Proxy buffering hoặc không flush | Thử `curl -N` trực tiếp với instance và qua proxy |
| Reconnect rồi mất cập nhật | Server không replay từ event ID | Dùng persistence/offset, tra `Last-Event-ID` |
| WebSocket không handshake | Sai URL, `ws/wss`, Origin, auth, reverse proxy Upgrade | Kiểm tra status và headers ở Network → WS |
| Socket tự đóng | Timeout, payload không hợp lệ, server restart | Kiểm tra close code, server logs, heartbeat |
| POST trả 403 | CSRF/authorization | Gửi CSRF token và xác thực đúng chính sách |
| Một node nhận message, node khác không | Lưu subscribers cục bộ | Kiểm tra routing và broker fan-out |
| Memory tăng theo thời gian | Emitter/socket không cleanup, queue quá lớn | Callback close/error, metrics connections/buffers |
| Trùng/sai thứ tự | Retry và nhiều producer | Event ID/version, ordering và dedup |
| Người dùng thấy dữ liệu người khác | Broadcast không lọc audience | Kiểm tra quyền theo user, tenant, topic |

## 12. Checklist kết thúc bài thực hành

- **SSE:** `GET /api/updates/stream` trả `text/event-stream`, browser nhận `ready` và các event `update`; SSE nhận thông điệp server phát qua POST hoặc WebSocket.
- **WebSocket:** handshake HTTP `101`, client gửi raw text message, nhận JSON text frame và broadcast tới client khác.
- **Độ bền:** reconnect SSE và WebSocket có hành vi khác nhau; demo không replay nên có thể mất sự kiện khi offline.
- **Chọn công nghệ:** SSE cho fan-out một chiều, WebSocket cho nhu cầu hai chiều, STOMP/broker cho topic/subscription phức tạp, polling cho tần suất đổi thấp.
- **Production:** có kế hoạch cho auth, Origin/CSRF, proxy timeout, heartbeat, persistence, scale đa instance, slow consumers và observability.

Một bài thực hành hoàn thành khi có thể **chứng minh bằng browser/Network tab và curl** rằng hai đường truyền hoạt động, giải thích được vì sao mỗi đường phù hợp một nhu cầu và nhận diện những bảo đảm còn phải bổ sung ở cấp ứng dụng.

### Tài liệu tham khảo

- Spring Framework 6.1 — Async MVC và SSE: https://docs.spring.io/spring-framework/reference/6.1/web/webmvc/mvc-ann-async.html
- Spring Framework 6.1 — WebSocket Server: https://docs.spring.io/spring-framework/reference/6.1/web/websocket/server.html
- Spring Framework 6.1 — STOMP over WebSocket: https://docs.spring.io/spring-framework/reference/6.1/web/websocket/stomp.html
- Spring Framework API — SseEmitter: https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.html
- WHATWG — Server-sent events: https://html.spec.whatwg.org/multipage/server-sent-events.html
- RFC 6455 — WebSocket Protocol: https://www.rfc-editor.org/rfc/rfc6455


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="operations-practice-011">Đối chiếu cùng một bài toán trên MVC và WebFlux bằng hành vi quan sát được</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu cùng một bài toán trên Spring MVC và WebFlux bằng hành vi quan sát được

## 1. Mục tiêu: cùng API, cùng upstream, so sánh điều nhìn thấy

Spring MVC và Spring WebFlux đều hỗ trợ xây dựng REST APIs nhưng có mô hình thực thi khác nhau. Nếu chỉ so sánh cú pháp “controller trả object” với “controller trả Mono”, chúng ta chưa chứng minh được khác biệt khi vận hành.

Bài thực hành sử dụng **một bài toán duy nhất**: cổng API truy vấn số lượng hàng từ một HTTP upstream có độ trễ khoảng 300 ms.

- Client gọi `GET /api/inventory/{id}`.
- Gateway Spring gọi `GET /inventory/{id}` ở upstream dùng chung.
- Upstream trả JSON `{"id":1,"available":true}` cho ID hợp lệ, trả HTTP `404` cho ID không tồn tại.
- Cổng MVC và cổng WebFlux cùng trả HTTP `200` + JSON tương đương hoặc HTTP `404`.
- Chạy nhiều request đồng thời và ghi nhận **thread names**, số request đang xử lý, p95 response latency, tổng thời gian và lỗi.
- Đối chiếu theo hành vi được quan sát, không suy ra framework “luôn nhanh hơn” từ một benchmark toy.

Trong bài này:

- **MVC** dùng Servlet/Tomcat + `RestClient` (HTTP client đồng bộ).
- **WebFlux** dùng Reactor Netty + `WebClient` (HTTP client reactive/non-blocking).
- **Upstream** là HTTP server độc lập viết bằng Java SE 17; cả hai cổng gọi cùng một URL. Nhờ vậy phép so sánh không vô tình đo hai backend khác nhau.

Các cấu hình và tên API tập trung vào **Spring Boot 3.x / Spring Framework 6.x, Java 17+**. Đừng lấy một kết luận hiệu năng trong môi trường này áp cho mọi loại workload.

## 2. Điều cần hiểu trước khi chạy

### 2.1. Spring MVC đồng bộ

Một request MVC điển hình:

~~~text
Client
  -> TCP/HTTP -> Tomcat servlet worker
                  |
                  -> @RestController
                  -> RestClient HTTP GET tới upstream (đồng bộ)
                  -> thread servlet CHỜ response
                  -> nhận Inventory record
                  -> Jackson serialize JSON
                  -> HTTP 200 cho client
~~~

Trong lúc `RestClient` đợi response, **servlet worker gọi nó bị chiếm**. Đây không phải lỗi: đó là mô hình lập trình imperative; đơn giản và phù hợp nhiều hệ thống đang dùng Spring MVC, JDBC/JPA, nghiệp vụ đồng bộ.

Nhưng nếu nhiều request phải chờ remote I/O lâu, giới hạn số servlet threads, HTTP connections và queue có thể ảnh hưởng throughput/tail latency. Việc tăng thread pool chỉ chuyển điểm nghẽn nếu upstream hoặc DB đã quá tải.

### 2.2. Spring WebFlux reactive

Một request WebFlux điển hình:

~~~text
Client
  -> Reactor Netty event-loop / WebFlux handler
                  |
                  -> @RestController trả Mono<Inventory>
                  -> WebClient HTTP GET tới upstream
                  -> subscribe bất đồng bộ; event-loop KHÔNG ngồi đợi 300 ms
                  -> nhận tín hiệu HTTP response
                  -> Mono phát Inventory record
                  -> WebFlux/Jackson ghi HTTP 200 JSON
~~~

Publisher `Mono<T>` biểu diễn **một kết quả có thể đến sau**, không phải một thread riêng. Reactor Netty dùng non-blocking I/O cho socket; reactor operators có thể chạy trên event-loop, scheduler khác hoặc thread đặc thù theo chuỗi xử lý. **Không mặc định mọi callback chạy trên một thread duy nhất** và không gọi `block()` trong event-loop.

Đặc biệt, Spring MVC **cũng có hỗ trợ async** bằng `Callable`, `DeferredResult`, `CompletionStage` và reactive return values như `Mono`. Vì vậy “MVC = mọi request phải giữ servlet worker” chỉ đúng với ví dụ **MVC đồng bộ** đang khảo sát, không đúng cho toàn bộ Spring MVC. MVC async dùng Servlet async; WebFlux có asynchronous contracts từ đầu. Ở Servlet stack, các lần ghi HTTP response vẫn có đặc tính blocking tùy kiểu response, khác WebFlux với non-blocking response I/O.

### 2.3. Khác nhau ở đâu, giống nhau ở đâu?

| Khía cạnh | MVC + RestClient | WebFlux + WebClient |
| --- | --- | --- |
| HTTP API bên ngoài | `GET /api/inventory/{id}` | Giống hệt |
| Response 200 | JSON Inventory | JSON Inventory tương đương |
| HTTP upstream | Cùng Java HTTP server | Cùng Java HTTP server |
| Độ trễ upstream | 300 ms mô phỏng | 300 ms mô phỏng |
| Handler return type | `Inventory` | `Mono<Inventory>` |
| HTTP client | `RestClient` synchronous | `WebClient` reactive |
| Khi chờ upstream | Servlet worker bị block | Không cần giữ một event-loop để đợi |
| Khi nhận lỗi 404 | Catch HTTP client exception | Map reactive error signal |
| Áp lực khi nhiều request | Servlet threads, pending tasks, pools | Open connections, event-loops, event queues, subscriber/backpressure |
| Tích hợp với JPA/JDBC blocking | Tự nhiên | Phải cô lập blocking calls hoặc lựa chọn mô hình khác |
| Khả năng chạy async | MVC hỗ trợ nếu thiết kế riêng | Thuộc mô hình nền tảng |

Cả hai có thể thực hiện cùng chức năng và cùng độ đúng nghiệp vụ. Một benchmark duy nhất không chứng minh WebFlux nhanh hơn về CPU, chi phí vận hành hay latency trong mọi tình huống.

## 3. Kiến trúc demo và các giả định đo

~~~text
                        +----------------------------+
  curl / LoadProbe ---> | MVC gateway :8081          |
                        | Tomcat + RestClient         |---+
                        +----------------------------+   |
                                                         | HTTP GET
                        +----------------------------+   | /inventory/{id}
  curl / LoadProbe ---> | WebFlux gateway :8082      |---+
                        | Reactor Netty + WebClient   |   |
                        +----------------------------+   v
                                                   +-------------------+
                                                   | Upstream :9090    |
                                                   | Java HttpServer   |
                                                   | delay ~= 300 ms   |
                                                   | 1..3 -> 200 JSON  |
                                                   | others -> 404     |
                                                   +-------------------+
~~~

Chủ động tránh các lỗi làm kết quả so sánh vô nghĩa:

- Một upstream **không tự gọi ngược chính server MVC**, vì vòng lặp tự gọi có thể gây cạn servlet worker.
- Upstream dùng fixed pool 64 workers để phục vụ các request demo đồng thời; nếu vượt capacity sẽ có queue và tăng latency.
- Hai gateway chạy ở **hai JVM, hai cổng khác nhau**; chỉ chạy một phép đo tải tại một thời điểm để tránh upstream bị hai nhóm đo tranh tài nguyên.
- Đường truyền là localhost. Nó phù hợp để quan sát execution model, **không đại diện cho mạng Internet hoặc production**.
- Cấu hình `server.tomcat.threads.max=8` dưới đây là **thí nghiệm có chủ đích**, làm rõ hiện tượng chờ thread; không phải khuyến nghị mặc định cho production.
- Kết quả latency phụ thuộc máy, hệ điều hành, JIT warmup, connection reuse, logging, memory, số CPU, client và tải khác. Ghi số thật thu được, không thay chúng bằng một con số “chuẩn”.

## 4. Ứng dụng upstream dùng chung: Java 17 thuần

Tạo file `upstream/SlowInventoryServer.java`:

~~~java
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class SlowInventoryServer {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 9090), 0);
        server.createContext("/inventory", SlowInventoryServer::handle);
        server.setExecutor(Executors.newFixedThreadPool(64));
        server.start();
        System.out.println("Upstream listening on 127.0.0.1:9090");
    }

    private static void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            reply(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        String[] parts = exchange.getRequestURI().getPath().split("/");
        String id = parts.length == 3 ? parts[2] : "";

        if (!id.matches("[1-3]")) {
            reply(exchange, 404, "{\"error\":\"not_found\"}");
            return;
        }

        try {
            Thread.sleep(300); // Mô phỏng remote I/O / một backend chậm.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            reply(exchange, 503, "{\"error\":\"interrupted\"}");
            return;
        }

        reply(exchange, 200,
                "{\"id\":" + id + ",\"available\":true}");
    }

    private static void reply(
            HttpExchange exchange, int status, String json)
            throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
~~~

Từ thư mục `upstream/`, biên dịch và chạy:

~~~bash
javac --add-modules jdk.httpserver SlowInventoryServer.java
java --add-modules jdk.httpserver SlowInventoryServer
~~~

Stdout khi server khởi chạy:

~~~text
Upstream listening on 127.0.0.1:9090
~~~

Thử ở một terminal khác:

~~~bash
curl -i http://127.0.0.1:9090/inventory/1
curl -i http://127.0.0.1:9090/inventory/9
~~~

Response thứ nhất có status `200`, body `{"id":1,"available":true}` và độ trễ chủ động khoảng 300 ms; response thứ hai `404` với body `{"error":"not_found"}`. Giá trị `Date`, thời gian thực và thứ tự headers không cố định. Nếu cổng 9090 bị chiếm, phải chỉnh URL ở upstream và cả hai gateway cùng lúc.

**Tại sao upstream dùng `Thread.sleep`?** Chỉ để tạo độ trễ kiểm soát được ở **một server độc lập**. Nó không ngụ ý rằng WebFlux cần hoặc nên sử dụng `Thread.sleep`. Khi ứng dụng WebFlux gọi endpoint, I/O của gateway vẫn do WebClient non-blocking đảm nhận; thread sleep nằm ở máy chủ upstream giả lập.

## 5. Gateway Spring MVC: synchronous RestClient

Tạo thư mục `mvc/` với cấu trúc:

~~~text
mvc/
  pom.xml
  src/main/java/demo/MvcInventoryApplication.java
  src/main/resources/application.properties
~~~

### 5.1. mvc/pom.xml

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
    <artifactId>mvc-inventory</artifactId>
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
~~~

Đây là ứng dụng **Servlet MVC** vì có `spring-boot-starter-web`. Không thêm WebFlux starter vào project MVC chỉ để gọi HTTP đồng bộ; `RestClient` đủ cho bài toán này.

### 5.2. mvc/src/main/resources/application.properties

~~~properties
server.port=8081
server.tomcat.threads.max=8
~~~

Giới hạn 8 servlet threads giúp quan sát khi số request đồng thời vượt capacity. Giá trị này có thể làm 40 request phải đợi nhiều đợt, nhất là khi mỗi request giữ worker khoảng 300 ms.

### 5.3. mvc/src/main/java/demo/MvcInventoryApplication.java

~~~java
package demo;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class MvcInventoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(MvcInventoryApplication.class, args);
    }

    @Bean
    RestClient upstream(RestClient.Builder builder) {
        HttpClient jdkClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(Duration.ofSeconds(2));

        return builder
                .baseUrl("http://127.0.0.1:9090")
                .requestFactory(factory)
                .build();
    }
}

record Inventory(long id, boolean available) {}

@RestController
class MvcInventoryController {
    private static final Logger log =
            LoggerFactory.getLogger(MvcInventoryController.class);

    private final RestClient upstream;
    private final AtomicInteger active = new AtomicInteger();

    MvcInventoryController(RestClient upstream) {
        this.upstream = upstream;
    }

    @GetMapping("/api/inventory/{id}")
    Inventory get(@PathVariable int id) {
        int now = active.incrementAndGet();
        log.info("MVC start id={} active={} thread={}",
                id, now, Thread.currentThread().getName());

        try {
            Inventory result = upstream.get()
                    .uri("/inventory/{id}", id)
                    .retrieve()
                    .body(Inventory.class);

            if (result == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Empty upstream body");
            }

            return result;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Inventory not found", ex);
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Upstream rejected request", ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Upstream unavailable", ex);
        } finally {
            int left = active.decrementAndGet();
            log.info("MVC end id={} active={} thread={}",
                    id, left, Thread.currentThread().getName());
        }
    }
}
~~~

Chạy từ thư mục `mvc/`:

~~~bash
mvn spring-boot:run
~~~

Quan sát log. Với request đơn bình thường, start và end thường có cùng tên thread Servlet worker, ví dụ `http-nio-8081-exec-1`, vì `RestClient` gọi đồng bộ ngay bên trong controller. Tên thread thực tế có thể khác theo Tomcat, phiên bản và môi trường; điều cần quan sát là **một worker bắt đầu request, đợi upstream và hoàn tất request**.

`active` đo số lời gọi còn đang ở method controller, **không phải tổng servlet threads trong container**. Số đo này giúp nhận diện lượng request đang chờ, nhưng để biết thread-pool saturation nên kiểm tra thêm Tomcat metrics hoặc thread dump.

Trong ví dụ trên, một response lỗi HTTP 404 từ upstream được chuyển thành status 404 của gateway. Lỗi HTTP khác hoặc lỗi kết nối được chuyển 502. Cấu hình timeout ở HTTP client là một lớp bảo vệ; timeout có thể biểu hiện như lỗi I/O trong `RestClientException`, nên ví dụ **không tuyên bố mapping 504 cho mọi timeout**.

## 6. Gateway Spring WebFlux: reactive WebClient

Tạo thư mục `webflux/` riêng:

~~~text
webflux/
  pom.xml
  src/main/java/demo/WebFluxInventoryApplication.java
  src/main/resources/application.properties
~~~

Không trộn `spring-boot-starter-web` với `spring-boot-starter-webflux` trong dự án này: nếu cả hai cùng có, Spring Boot thường chọn **MVC web application** làm default. Tách hai app giúp bạn xác thực chắc chắn server stack được dùng.

### 6.1. webflux/pom.xml

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
    <artifactId>webflux-inventory</artifactId>
    <version>0.0.1-SNAPSHOT</version>
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
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 6.2. webflux/src/main/resources/application.properties

~~~properties
server.port=8082
~~~

WebFlux server Reactor Netty không dùng thuộc tính `server.tomcat.threads.max`. Không cố “công bằng hóa” bằng cách dùng cùng tên cấu hình thread: MVC servlet pool và Reactor Netty event-loop pool thực hiện các vai trò khác nhau. Khi benchmark nghiêm túc, cần ghi nhận và điều chỉnh **cả hai hệ thống theo capacity/resource budget**.

### 6.3. webflux/src/main/java/demo/WebFluxInventoryApplication.java

~~~java
package demo;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@SpringBootApplication
public class WebFluxInventoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebFluxInventoryApplication.class, args);
    }

    @Bean
    WebClient upstream(WebClient.Builder builder) {
        return builder.baseUrl("http://127.0.0.1:9090").build();
    }
}

record Inventory(long id, boolean available) {}

@RestController
class WebFluxInventoryController {
    private static final Logger log =
            LoggerFactory.getLogger(WebFluxInventoryController.class);

    private final WebClient upstream;
    private final AtomicInteger active = new AtomicInteger();

    WebFluxInventoryController(WebClient upstream) {
        this.upstream = upstream;
    }

    @GetMapping("/api/inventory/{id}")
    Mono<Inventory> get(@PathVariable int id) {
        return Mono.defer(() -> {
            int now = active.incrementAndGet();
            log.info("WebFlux subscribe id={} active={} thread={}",
                    id, now, Thread.currentThread().getName());

            return upstream.get()
                    .uri("/inventory/{id}", id)
                    .retrieve()
                    .bodyToMono(Inventory.class)
                    .switchIfEmpty(Mono.error(
                            new ResponseStatusException(
                                    HttpStatus.BAD_GATEWAY,
                                    "Empty upstream body")))
                    .onErrorMap(
                            WebClientResponseException.class,
                            ex -> ex.getStatusCode().value() == 404
                                    ? new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Inventory not found", ex)
                                    : new ResponseStatusException(
                                            HttpStatus.BAD_GATEWAY,
                                            "Upstream rejected request", ex))
                    .onErrorMap(
                            WebClientRequestException.class,
                            ex -> new ResponseStatusException(
                                    HttpStatus.BAD_GATEWAY,
                                    "Upstream unavailable", ex))
                    .timeout(Duration.ofSeconds(2))
                    .onErrorMap(
                            TimeoutException.class,
                            ex -> new ResponseStatusException(
                                    HttpStatus.GATEWAY_TIMEOUT,
                                    "Upstream response timed out", ex))
                    .doOnNext(value -> log.info(
                            "WebFlux result id={} thread={}",
                            id, Thread.currentThread().getName()))
                    .doFinally(signal -> {
                        int left = active.decrementAndGet();
                        log.info("WebFlux end id={} active={} signal={} thread={}",
                                id, left, signal,
                                Thread.currentThread().getName());
                    });
        });
    }
}
~~~

Chạy ở thư mục `webflux/`:

~~~bash
mvn spring-boot:run
~~~

Ở đây **controller không gọi `block()` hoặc `subscribe()`**. Nó trả `Mono<Inventory>`; WebFlux tự subscribe trong request lifecycle, serialize dữ liệu khi publisher phát giá trị và xử lý lỗi từ publisher.

`Mono.defer` bảo đảm việc tăng biến `active` và tạo request diễn ra **lúc subscribe**, không ngay lúc method tạo publisher. `doFinally` chạy khi stream kết thúc thành công, lỗi hoặc bị cancel; đếm in-flight vì vậy có cơ chế cleanup kể cả trường hợp request bị hủy.

`timeout(Duration)` là timeout cấp reactive chain, không thay thế đầy đủ connect timeout và các giới hạn connection pool của Reactor Netty. Response status 504 từ timeout này là chính sách minh họa; mapping timeout trong MVC ở trên chưa được chuẩn hóa thành 504, nên **chỉ xem 200/404 và lỗi mạng 502 là contract tương đương trong demo**, không kết luận hai stack có error contract hoàn toàn giống nhau ở mọi failure mode.

### Chú ý về log thread

Ví dụ khi log xuất hiện trên `reactor-http-nio-*`, nó cho thấy callback đang ở event-loop Reactor Netty. Nhưng Reactor có thể chọn thread khác dựa vào operator/scheduler; tên không cố định. Điểm đáng quan sát là **không có lời gọi đợi blocking trên handler thread** trong code này. WebClient thực hiện HTTP I/O theo non-blocking model trong khi upstream chờ khoảng 300 ms.

Nếu chèn `Thread.sleep(300)` vào `map` hoặc `doOnNext` bên WebFlux thì bạn đã phá giả định non-blocking. Nếu gọi JDBC/JPA synchronous hoặc đọc file blocking, phải chuyển phần đó sang cơ chế blocking có kiểm soát (`boundedElastic` hoặc thread pool riêng) hoặc chọn Spring MVC imperative nếu toàn hệ thống chủ yếu blocking.

## 7. Thử nghiệm đối xứng: kiểm tra chức năng và lỗi

Bật ba tiến trình ở ba terminal:

1. `SlowInventoryServer` lắng nghe 9090.
2. `MvcInventoryApplication` lắng nghe 8081.
3. `WebFluxInventoryApplication` lắng nghe 8082.

Gửi cùng request:

~~~bash
curl -i http://localhost:8081/api/inventory/1
curl -i http://localhost:8082/api/inventory/1
~~~

Cả hai có status `200 OK`, `Content-Type` JSON tương ứng và body về mặt dữ liệu:

~~~json
{"id":1,"available":true}
~~~

Thứ tự JSON fields, HTTP headers `Date`/`Content-Length`/`Transfer-Encoding` và server headers có thể khác; so **ý nghĩa dữ liệu, status và contract** thay vì so byte-for-byte toàn bộ response.

Thử resource không tồn tại:

~~~bash
curl -i http://localhost:8081/api/inventory/9
curl -i http://localhost:8082/api/inventory/9
~~~

Cả hai có HTTP status `404` nếu upstream đang chạy đúng như ví dụ. Spring Boot có thể tạo default error body khác giữa các stack/cấu hình, nhất là khi không bật `ProblemDetail` đồng nhất. **Cùng status không đồng nghĩa body lỗi đã có contract giống nhau**; nếu frontend cần body thống nhất, nên chuẩn hóa bằng `@RestControllerAdvice` tương ứng và kiểm thử schema.

Thử backend dừng: dừng `SlowInventoryServer`, sau đó gửi `/api/inventory/1` tới hai gateway. Với cơ chế catch/map trong ví dụ, **lỗi kết nối** thông thường được chuyển `502 Bad Gateway`. Khi backend đang chạy nhưng kéo dài quá timeout, MVC có thể map một I/O timeout thành 502 còn WebFlux có thể trả 504 từ `Mono.timeout`; đó là khác biệt đã được chỉ rõ, không che giấu như “cùng semantics tuyệt đối”.

Dùng `curl -w` để ghi nhận riêng latency:

~~~bash
curl -s -o /dev/null -w "MVC total=%{time_total}s\n" http://localhost:8081/api/inventory/1
curl -s -o /dev/null -w "WebFlux total=%{time_total}s\n" http://localhost:8082/api/inventory/1
~~~

`/dev/null` là đường dẫn trên hệ Unix-like; trên Windows có thể dùng công cụ HTTP CLI/tập lệnh phù hợp hoặc chỉ quan sát `curl -w` mà không dùng `-o /dev/null`. Thời gian một request khi không có queue thường bị chi phối bởi độ trễ upstream và không tự chứng minh stack nào hơn.

## 8. LoadProbe: đo nhiều request đồng thời bằng Java 17

Tạo file độc lập `probe/LoadProbe.java`:

~~~java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class LoadProbe {
    record Result(int status, long elapsedMs) {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println(
                    "Usage: java LoadProbe <baseUrl> [requestCount]");
            System.exit(2);
        }

        String baseUrl = args[0].replaceAll("/+$", "");
        int count = args.length == 2 ? Integer.parseInt(args[1]) : 40;
        if (count < 1 || count > 2000) {
            throw new IllegalArgumentException("requestCount must be 1..2000");
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        HttpRequest request = HttpRequest.newBuilder(
                    URI.create(baseUrl + "/api/inventory/1"))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

        List<CompletableFuture<Result>> pending = new ArrayList<>();
        long globalStart = System.nanoTime();

        for (int i = 0; i < count; i++) {
            long requestStart = System.nanoTime();
            CompletableFuture<Result> one = client.sendAsync(
                    request, HttpResponse.BodyHandlers.discarding())
                .handle((response, error) -> new Result(
                    error == null ? response.statusCode() : -1,
                    TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - requestStart)));
            pending.add(one);
        }

        CompletableFuture.allOf(
                pending.toArray(CompletableFuture[]::new)).join();

        long ok = pending.stream()
                .map(CompletableFuture::join)
                .filter(result -> result.status() == 200)
                .count();

        long[] latencies = pending.stream()
                .map(CompletableFuture::join)
                .mapToLong(Result::elapsedMs)
                .sorted()
                .toArray();

        int p95Index = (int) Math.ceil(count * 0.95) - 1;
        long totalMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - globalStart);

        System.out.printf(
                "requests=%d ok=%d failed=%d totalMs=%d p95Ms=%d%n",
                count, ok, count - ok, totalMs, latencies[p95Index]);
    }
}
~~~

Biên dịch ở `probe/`:

~~~bash
javac LoadProbe.java
~~~

Chạy **riêng từng gateway**, không đồng thời hai phép đo:

~~~bash
java LoadProbe http://localhost:8081 40
java LoadProbe http://localhost:8082 40
~~~

Chương trình in một dòng theo định dạng:

~~~text
requests=<N> ok=<M> failed=<N-M> totalMs=<measured> p95Ms=<measured>
~~~

Các chuỗi trong dấu `<...>` là **tên giá trị đo**, không phải output số cố định. Với demo và các tiến trình hoạt động bình thường, hy vọng phần lớn request trả 200; bất kỳ failed nào cũng phải được điều tra thay vì bỏ qua.

`LoadProbe` cố gắng khởi tạo nhiều HTTP requests gần nhau bằng `HttpClient.sendAsync(...)`. Nó là công cụ học tập, **không phải load generator chuẩn có warm-up, rate control, coordinated-omission correction, latency distribution hoàn chỉnh**. `totalMs` là thời gian từ lúc bắt đầu tạo các requests đến khi tất cả hoàn tất; `p95Ms` là percentile thực nghiệm từ các latencies ghi nhận sau `sendAsync`. Với 40 mẫu, phân vị rất nhiễu và phụ thuộc trễ ở client; cần nhiều vòng/đủ mẫu mới có kết luận.

## 9. Kết quả kỳ vọng về mặt cấu trúc, không dự đoán số đo

Khi 40 requests đều cần chờ một upstream 300 ms, có hai khả năng thường quan sát được (không bảo đảm mọi máy sẽ giống hệt):

**Ở MVC với Tomcat tối đa 8 request workers:**

- Nếu cả 40 requests gần như cùng đến, có thể thấy khoảng 8 lời gọi controller đồng thời, một số request phải chờ worker.
- Log start/end thường cùng servlet thread; trong khoảng chờ upstream, thread đó chưa phục vụ request khác.
- `active` trong controller thường không vượt quá số request threads đang thực thi controller. Nó có thể thấp hơn 8 khi client/network không bão hòa.
- Tail latency có thể tăng qua nhiều “đợt” chờ khi concurrency vượt capacity. Số đợt và total time còn phụ thuộc connection scheduling, upstream pool, JVM warmup.

**Ở WebFlux với Reactor Netty + WebClient:**

- Nhiều subscriptions có thể được mở trong khi chỉ có số event-loop threads tương đối ít; event-loop không bị block trong lúc chờ upstream.
- Log `active` có thể lớn hơn số event-loop threads vì nó đếm **đang chờ publisher hoàn tất**, không phải số threads.
- Thời gian chờ do upstream vẫn tồn tại; WebFlux không làm 300 ms network/backend latency “biến mất”.
- Nếu WebClient connection pool, upstream thread pool, CPU, queue, logging hoặc client cạn capacity, p95/tổng thời gian vẫn có thể tăng rất mạnh.

### Vì sao không lấy một con số để khẳng định WebFlux thắng?

Trong ví dụ này ta **cố ý giới hạn Tomcat chỉ 8 workers** nhưng không giới hạn một resource budget tương ứng của WebFlux. Điều này chứng minh tác động của **mô hình chờ thread** dưới một cấu hình cụ thể, chứ không so sánh công bằng toàn bộ framework.

Muốn đánh giá nghiêm túc:

1. Chạy warm-up để JIT/HTTP connection reuse ổn định.
2. Đo nhiều vòng và báo median/p95/p99, throughput, CPU, memory/GC, số connections, số threads, errors.
3. Đảm bảo upstream không thành điểm nghẽn; nếu thành bottleneck hãy ghi rõ đó là điều bạn đang đo.
4. Thử cùng host/cấu hình, giới hạn CPU/memory/network tương đương; ghi phiên bản Java/Spring/Netty/Tomcat và config.
5. So sánh cả **MVC async dùng WebClient/CompletionStage**, không chỉ MVC blocking, khi bài toán đòi hiệu quả chờ I/O cao.
6. Thử biến thể có nghiệp vụ blocking thật (JDBC/JPA), CPU-bound computation và streaming, vì “reactive = nhanh hơn” là suy luận sai.
7. Kiểm thử timeout, cancellation, backpressure và error rates, không chỉ happy path.

Để phục vụ giám sát thực tế, có thể dùng Micrometer/Actuator (được cấu hình chính xác cho từng app) để quan sát `http.server.requests`, Tomcat busy threads, Reactor Netty connection pool và scheduler metrics; tên meter/tag thực tế có thể phụ thuộc phiên bản và configuration.

## 10. Sự khác nhau giữa blocking servlet thread và non-blocking event-loop

Một thread servlet đồng bộ chờ RestClient:

~~~text
Tomcat-Worker-1: [controller]->[HTTP call--------waiting--------]->[JSON]
Tomcat-Worker-2: [controller]->[HTTP call--------waiting--------]->[JSON]
Tomcat-Worker-3: [controller]->[HTTP call--------waiting--------]->[JSON]
...
       số request đang chạy bị giới hạn bởi worker pool và resources khác
~~~

Nhiều request WebFlux non-blocking:

~~~text
reactor-http-nio-1: [accept][subscribe][socket-event][write][accept]...
reactor-http-nio-2: [accept][subscribe][socket-event][write][accept]...
         |              |               |
         +--- nhiều upstream requests đang chờ HTTP response
              không cần mỗi request một blocking worker
~~~

Đây là sơ đồ logic, không mô tả scheduler internals một cách chính xác tuyệt đối. Netty có worker event-loops và networking tasks; operator chain có thể chạy ở các thread khác.

### Sai: ép WebFlux quay về blocking

~~~java
@GetMapping("/bad/{id}")
Inventory wrong(@PathVariable int id) {
    return webClient.get()
            .uri("/inventory/{id}", id)
            .retrieve()
            .bodyToMono(Inventory.class)
            .block();
}
~~~

Khi đoạn code này được thực thi trên Reactor event-loop, `block()` không được hỗ trợ, có thể ném `IllegalStateException`. Dù một đoạn blocking chạy được trên thread khác, nó vẫn lãng phí khả năng non-blocking mà API WebFlux đang cung cấp.

Đúng:

~~~java
@GetMapping("/good/{id}")
Mono<Inventory> correct(@PathVariable int id) {
    return webClient.get()
            .uri("/inventory/{id}", id)
            .retrieve()
            .bodyToMono(Inventory.class);
}
~~~

Hai snippet trên là minh họa tích hợp, giả định có `webClient` và `Inventory`. Với WebFlux, trả publisher; không gọi thủ công `subscribe()` để rồi controller trả response trước khi kết quả đến. Trả publisher giúp framework quản lý status, error, cancellation, trace và response lifecycle.

### Blocking code không được làm mất

Nếu tầng nghiệp vụ bắt buộc gọi JDBC/JPA synchronous hoặc client legacy blocking trong một reactive pipeline, bọc **riêng** đoạn blocking:

~~~java
Mono<Inventory> result = Mono.fromCallable(
                () -> legacyInventoryRepository.findById(id))
        .subscribeOn(Schedulers.boundedElastic());
~~~

Đây là một giải pháp phối hợp có giới hạn, không chuyển repository thành reactive và không tự làm DB queries có thể chạy vô hạn. Với nhiều thành phần blocking, cấu trúc MVC synchronous thường có tính đơn giản và khả năng kiểm soát tốt hơn; **sử dụng WebFlux không phải yêu cầu bắt buộc cho mọi web API hiện đại**.

## 11. Cùng một hành vi khi có lỗi, timeout và client cancel

### Upstream trả 404

Cả RestClient và WebClient mặc định coi HTTP 4xx/5xx là error path khi gọi `retrieve()` (với những API như `body(...)` và `bodyToMono(...)` trong ví dụ). Hai controller map upstream 404 thành gateway 404. Tuy nhiên default response body và xử lý exception có thể không giống nhau nếu không chuẩn hóa. Test phải kiểm tra status, format, fields cần thiết chứ không dựa vào thông điệp exception.

### Upstream trả 5xx

Ở MVC, `RestClientResponseException` được catch và map gateway 502. Ở WebFlux, `WebClientResponseException` được map trong `onErrorMap` thành gateway 502. Cả hai thể hiện rằng gateway không thể hoàn thành truy vấn do upstream failure, không nhất thiết giữ nguyên status 500 của backend.

### Upstream không mở hoặc network lỗi

Ở MVC, lỗi synchronous ở `RestClientException` được chuyển 502. Ở WebFlux, `WebClientRequestException` là error signal trên `Mono` và được map thành 502. Nếu ứng dụng thực cần retry, phải thêm có điều kiện, backoff và đảm bảo idempotency; không nên retry vô hạn làm tăng tải trên upstream đang lỗi.

### Timeout

Timeout cần phân biệt:

- MVC JDK HTTP request factory cấu hình connect/read timeout. Exception thường xuất hiện như I/O failure và ví dụ map 502.
- WebFlux `Mono.timeout(Duration.ofSeconds(2))` phát timeout của pipeline, được map 504 trong ví dụ. Nếu Netty connection acquisition hay connection setup có chính sách khác, cần cấu hình tương ứng.
- Client hoặc proxy có timeout riêng; chúng có thể trả lỗi hoặc đóng connection trước gateway.
- “Timeout HTTP gateway” không đồng nghĩa upstream đã dừng công việc. Với thao tác ghi, retry phải xét idempotency keys.

Nếu **contract API yêu cầu mọi timeout là 504**, hãy bổ sung mapper timeout nhất quán cho cả hai ứng dụng và tests cho từng failure mode, thay vì giả định mặc định Spring đồng nhất.

### Client rời trang và cancel

Spring WebFlux có khả năng truyền cancel qua reactive subscription; nhưng việc client đóng socket, thời điểm server nhận biết và khả năng huỷ request ở upstream tùy connector/protocol, không phải lúc nào cũng tức thì. Với MVC synchronous, servlet thread đang blocking vào HTTP client cũng không tự hủy remote operation chỉ vì browser đóng tab.

Đừng gắn ý nghĩa “cancellation = rollback” hoặc “WebFlux không bao giờ giữ tài nguyên khi client rời đi”. Quan sát metrics, cleanup của `doFinally`, connection pool và timeout thực.

## 12. Tự động kiểm thử contract hai gateway

Một lựa chọn đơn giản là chạy upstream 9090 và hai gateway, rồi dùng HTTP client thực để thử cùng bộ test cases:

| Test case | MVC :8081 | WebFlux :8082 | Assert bắt buộc |
| --- | --- | --- | --- |
| `GET /api/inventory/1` | 200 | 200 | JSON id=1, available=true |
| `GET /api/inventory/2` | 200 | 200 | JSON id=2, available=true |
| `GET /api/inventory/9` | 404 | 404 | Status 404, không trả 200 với body lỗi |
| Upstream 9090 tắt, gọi id=1 | thường 502 | thường 502 | Lỗi network được xử lý, không leak stack trace |
| Client gửi HTTP POST tới GET-only route | thường 405 | thường 405 | Method contract; `Allow` nếu framework trả |
| Chạy 40 request song song | đa số 200 nếu đủ capacity | đa số 200 nếu đủ capacity | Error count + total/p95 latency |
| Upstream trả chậm quá timeout | xem mapper MVC | xem mapper WebFlux | Không tự tuyên bố same status nếu code mapper khác |

Các test cases này **không yêu cầu body lỗi giống nhau** khi hai dự án chưa cùng cài error response contract. Nếu muốn strict equality, định nghĩa DTO/`ProblemDetail` và `@RestControllerAdvice` ở cả hai stack.

Một integration test đơn giản có thể dùng JUnit 5 + Java `HttpClient` và chạy ứng dụng ở random ports, hoặc Spring MVC `MockMvc` và WebFlux `WebTestClient` cho nhanh; tuy nhiên bài toán “cùng upstream thật” quan sát rõ hơn khi **chạy server trên port và dùng HTTP client thực**. `MockMvc` không đi qua socket Tomcat, vì vậy không dùng số đo từ `MockMvc` để chứng minh servlet thread saturation.

Một test Java thuần chỉ kiểm tra 200/404 có thể được viết như sau, không phụ thuộc vào Spring Test (giả sử hai gateway và upstream đã được bật):

~~~java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ContractProbe {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        for (int port : new int[]{8081, 8082}) {
            check(client, port, 1, 200, "\"available\":true");
            check(client, port, 9, 404, null);
        }
        System.out.println("MVC and WebFlux HTTP contract: PASS");
    }

    static void check(HttpClient client, int port, int id,
                      int expectedStatus, String expectedFragment)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port
                        + "/api/inventory/" + id))
                .GET()
                .build();
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != expectedStatus) {
            throw new AssertionError(
                    "port=" + port + " id=" + id
                    + " status=" + response.statusCode());
        }

        if (expectedFragment != null
                && !response.body().contains(expectedFragment)) {
            throw new AssertionError("Unexpected body: " + response.body());
        }
    }
}
~~~

Lưu file `probe/ContractProbe.java` rồi chạy:

~~~bash
javac ContractProbe.java
java ContractProbe
~~~

Nếu tất cả các assertion pass, stdout chính xác là:

~~~text
MVC and WebFlux HTTP contract: PASS
~~~

Nếu kết quả khác, chương trình ném `AssertionError` hoặc lỗi kết nối thay vì in `PASS`. Đây là chương trình chạy được với Java 17+; không cần sửa code cho từng gateway.

## 13. Khi nào MVC async làm kết quả so sánh thay đổi?

Đây là phép đối chiếu quan trọng để tránh ngộ nhận.

MVC có thể dùng WebClient:

~~~java
@RestController
class MvcAsyncController {
    private final WebClient client;

    MvcAsyncController(WebClient client) {
        this.client = client;
    }

    @GetMapping("/api/async-inventory/{id}")
    Mono<Inventory> inventory(@PathVariable int id) {
        return client.get()
                .uri("/inventory/{id}", id)
                .retrieve()
                .bodyToMono(Inventory.class);
    }
}
~~~

Đoạn trên là snippet tích hợp, cần bean `WebClient`, dependency `spring-webflux` cung cấp client và DTO. **Nó vẫn là Spring MVC nếu app chạy trên servlet stack**, chứ không tự chuyển thành WebFlux.

Khi controller trả `Mono`, Spring MVC chuyển sang Servlet asynchronous request handling tương tự `DeferredResult`. Servlet worker ban đầu có thể được giải phóng trong lúc chờ WebClient; do vậy mô hình này có thể giảm hiện tượng servlet worker bị giữ chờ I/O như MVC RestClient synchronous.

Nhưng MVC async vẫn có Servlet async dispatch và khi stream response dạng reactive, các lần ghi trên Servlet stack có thể blocking/được chuyển tới async executor. WebFlux xử lý asynchronous/non-blocking I/O sâu trong server stack. Độ chênh thực tế phải đo theo workload và implementation, không chỉ dựa tên lớp controller.

### Bốn biến thể đáng benchmark nếu muốn công bằng

1. MVC synchronous + RestClient: đơn giản, baseline imperative.
2. MVC async + WebClient trả `Mono`: tách hiệu ứng của HTTP client blocking khỏi Servlet server model.
3. WebFlux + WebClient: toàn chuỗi HTTP I/O reactive.
4. MVC với Java virtual threads (nếu JVM/framework/config hỗ trợ): cho phép nhiều tác vụ blocking theo mô hình thread-per-request với chi phí khác platform threads, nhưng **không làm HTTP client và database tự thành non-blocking**.

Java 17 của demo **chưa có virtual threads chính thức** (được chuẩn hóa ở Java 21). Vì vậy không biến phép đo Java 17 hiện tại thành nhận định về chi phí thread trên Java 21+.

## 14. Bẫy đo đạc và vận hành thường gặp

### Chỉ đo một request

Một request chờ upstream 300 ms thường cho thời gian tương tự nhau giữa MVC và WebFlux. Khác biệt quan trọng thường nằm ở concurrency, thread/resource cost và tình huống client/network chậm. Hãy đo từ 1 → 8 → 40 → 100 requests, ghi cả latency và số lỗi, nhưng không làm quá capacity upstream nếu không định nghiên cứu nghẽn tải.

### Benchmark trên cùng process hoặc khác workload

Nếu MVC gọi DB synchronously nhưng WebFlux chỉ chờ `Mono.delay` không cần network thì **không tương đương**. Trong demo này cả hai thực hiện HTTP GET tới đúng upstream. Tuy nhiên MVC Tomcat thread limit 8 là ràng buộc cố ý, nên cần xem đó là **thí nghiệm minh họa giới hạn worker**, không phải so sánh default performance.

### Bỏ qua connection pool

WebClient Reactor Netty có pool connection và quy tắc giới hạn; RestClient phụ thuộc request factory/client cụ thể. Nếu số socket/pending acquisitions bị giới hạn, requests phải chờ dù event-loop rảnh. Cần theo dõi active/pending connections và timeout acquire khi scale.

### Dùng blocking API trong WebFlux

Một lần gọi `Thread.sleep`, JDBC/JPA blocking hoặc `block()` sai vị trí trên event-loop có thể làm nhiều connection chậm. Cần thay bằng reactive driver hoặc offload phần blocking có giới hạn; `subscribeOn(boundedElastic)` không thay đổi bản chất I/O đã blocking.

### Không xử lý chuỗi reactive rỗng

`Mono<Inventory>` có thể hoàn thành không phát dữ liệu nếu upstream trả body rỗng. Demo dùng `switchIfEmpty(Mono.error(...))` để biến thành 502, thay vì âm thầm trả empty response gây nhầm với 404 hoặc 200 null. MVC kiểm tra `result == null` tương tự.

### Log sai thông tin thread

Thread name là dấu hiệu chứ không phải metric đầy đủ. Thread có thể đổi sau async boundary, `publishOn`, `subscribeOn` hoặc I/O callbacks. Sử dụng trace IDs/correlation IDs, metrics và profiling khi cần kết luận.

### Không kiểm tra error path

Khác biệt status mapping 502/504, response body, timeout và cancel mới là điểm dễ gây bug khi đổi framework. Trước migration nên viết contract tests (happy path, missing resource, timeout, upstream 5xx, auth, validation và cancel).

### Trộn hai starters và vô tình chạy sai stack

Spring Boot ứng dụng chứa cả `spring-boot-starter-web` và `spring-boot-starter-webflux` thường tự cấu hình **Spring MVC**. Việc thấy WebClient hoặc Mono trong code không đủ để kết luận server đang chạy WebFlux. Kiểm tra dependency tree, startup logs, application context type và embedded server thật.

## 15. Checklist đưa quyết định vào dự án thực tế

| Câu hỏi | Nếu câu trả lời là... | Hướng xử lý |
| --- | --- | --- |
| Đường đi đang chờ nhiều HTTP/database I/O? | HTTP reactive nhưng DB blocking | Tính lợi ích của MVC async/WebClient và chi phí offload; không chọn WebFlux vội. |
| Phần lớn code hiện tại là imperative/JPA? | Có | MVC thường là default hợp lý, giữ contract và monitor threads. |
| Cần hàng chục nghìn socket/stream? | Có, công việc mỗi message non-blocking | WebFlux có thể là lựa chọn tốt, nhưng phải load test fan-out, pools, slow consumers. |
| Tác vụ chủ yếu CPU-bound? | Có | Tối ưu thuật toán/CPU/scheduler; reactive không tự gia tốc xử lý CPU. |
| Có nhu cầu backpressure/streaming? | Có | Đánh giá WebFlux và cách ghi response, buffering, cancellation; MVC cũng có async streaming với khác biệt I/O. |
| Có yêu cầu migration? | Có | Viết HTTP contract tests trước, thử từng endpoint, đo error rate và p95/p99. |
| Có lo dữ liệu mất/duplicate sau timeout? | Có | Thiết kế retry/idempotency, persistence, không dựa vào transport để đảm bảo. |

**Kết luận:** Trong bài toán truy vấn upstream chậm với cùng HTTP contract, MVC + RestClient chờ đồng bộ trên servlet worker; WebFlux + WebClient biểu diễn kết quả bằng `Mono` và dùng non-blocking I/O. Quan sát đồng thời thread names, in-flight requests, p95, latency, resource limits và lỗi thực tế mới cho phép đưa ra quyết định. Nếu nghiệp vụ chủ yếu blocking, MVC hoàn toàn có thể là lựa chọn đúng; nếu yêu cầu nhiều concurrent I/O và reactive end-to-end, WebFlux đáng được kiểm nghiệm.

## 16. Tài liệu tham khảo

- Spring Framework — Web MVC (Servlet stack): https://docs.spring.io/spring-framework/reference/web/webmvc.html
- Spring Framework — MVC Asynchronous Requests: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- Spring Framework — Spring WebFlux: https://docs.spring.io/spring-framework/reference/web/webflux.html
- Spring Framework — REST Clients (RestClient, WebClient): https://docs.spring.io/spring-framework/reference/integration/rest-clients.html
- Spring Framework — WebClient Synchronous Use: https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-synchronous.html
- Spring Framework — WebClient Error Handling: https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-retrieve.html
- Reactor Core — Threading and Schedulers: https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html
- Spring Boot — Web Application Type and Reactive Applications: https://docs.spring.io/spring-boot/reference/web/reactive.html


</details>

- [Quay lại đầu trang](#back-to-top)
