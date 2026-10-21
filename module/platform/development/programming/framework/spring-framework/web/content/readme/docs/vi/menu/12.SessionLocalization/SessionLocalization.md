<a id="back-to-top"></a>

# Trạng thái và ngôn ngữ

## Menu
- [Request scope, session scope và dữ liệu dùng chung giữa các yêu cầu](#session-localization-001)
- [Cookie và cấu hình thời hạn, Secure, HttpOnly, SameSite](#session-localization-002)
- [HttpSession, tạo/hủy phiên và giới hạn khi triển khai nhiều máy chủ](#session-localization-003)
- [@SessionAttributes, SessionStatus và khác biệt với @SessionAttribute](#session-localization-004)
- [LocaleResolver, LocaleContextResolver và lựa chọn ngôn ngữ](#session-localization-005)
- [MessageSource, thông báo validation và bản địa hóa giao diện](#session-localization-006)
- [RequestContextHolder, LocaleContextHolder và giới hạn ThreadLocal](#session-localization-007)
- [Truyền ngữ cảnh khi chuyển sang xử lý bất đồng bộ](#session-localization-008)

## <a id="session-localization-001">Request scope, session scope và dữ liệu dùng chung giữa các yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Request scope, session scope và dữ liệu dùng chung giữa các yêu cầu trong Spring MVC

## 1. Từ bản chất HTTP đến bài toán quản lý trạng thái

HTTP request là một lần trao đổi độc lập: client gửi phương thức, URI, headers và có thể có body; server xử lý rồi trả status, headers và body. HTTP **không tự mang toàn bộ biến Java từ request trước sang request sau**. Trong khi đó ứng dụng web lại thường cần nhớ dữ liệu:

- **Trong một request:** mã theo dõi, tenant đã xác thực, dữ liệu trung gian do filter hoặc interceptor tính toán.
- **Giữa nhiều request của cùng một người dùng/phiên:** lựa chọn ngôn ngữ, bước hiện tại của một wizard, giỏ hàng nhỏ hoặc tùy chọn giao diện.
- **Giữa các request từ mọi người dùng:** cấu hình chỉ đọc, feature catalog, bộ đếm toàn ứng dụng hoặc cache dùng chung.
- **Qua restart, qua nhiều máy chủ hoặc trong thời gian dài:** profile người dùng, đơn hàng, giỏ hàng cần bền vững, bản ghi trong database hoặc kho lưu trữ ngoài.

Spring MVC cung cấp **Servlet request/session** và các **Spring bean scopes** để biểu diễn vòng đời tương ứng. Tuy nhiên, “scope” không có nghĩa “dữ liệu tự động được lưu bền vững”; phải chọn đúng chủ sở hữu, thời hạn và nơi lưu trữ.

Hình dung ba request:

```text
Browser A                           Server
   | GET /api/scope/request  -------> request R1
   |                                  @RequestScope -> Bean R1
   | <------- response --------------- R1 kết thúc
   |
   | GET /api/scope/session  -------> request R2, session S-A
   |                                  @SessionScope -> Bean S-A
   | <------- Set-Cookie session-id -- session S-A còn sống
   |
   | GET /api/scope/session + cookie -> request R3, session S-A
   |                                  @RequestScope -> Bean R3 mới
   |                                  @SessionScope -> Bean S-A cũ
   | <------- response --------------- R3 kết thúc

Browser B
   | GET /api/scope/session  -------> request R4, session S-B
                                      Bean S-B khác S-A

@ApplicationScope / singleton:
cùng một ứng dụng web có thể được dùng bởi R1, R2, R3, R4
(nhưng không mặc định chia sẻ với JVM hoặc node triển khai khác).
```

**Mục tiêu bài học:** phân biệt thời gian sống và phạm vi nhìn thấy của dữ liệu, dùng `@RequestScope`/`@SessionScope`/`@ApplicationScope` đúng cách, tránh nhầm chúng với `HttpSession`/request attributes, hiểu session ID và cookie, kiểm thử bằng Java 17+ Spring MVC và lựa chọn cách lưu state an toàn trong hệ thống nhiều instance.

## 2. Phân biệt scope của Servlet và scope của Spring bean

Có hai lớp khái niệm cùng sử dụng từ “request” và “session”:

1. **Servlet attributes:** key-value nằm trong `HttpServletRequest`, `HttpSession` hoặc `ServletContext`, thường được đặt bằng `setAttribute(name, value)`.
2. **Spring bean scopes:** container quyết định một bean definition sẽ được tạo một instance cho mỗi request/session/application hay dùng một instance chung.

Hai lớp có quan hệ vòng đời nhưng **không phải API đồng nghĩa**. Khi bạn gọi `request.setAttribute("trace", "abc")`, Spring không tự biến chuỗi đó thành một bean `@RequestScope`. Khi đánh dấu `@SessionScope` cho một bean, bạn không cần tự gọi `session.setAttribute` để đăng ký bean; Spring quản lý instance qua scope của container.

| Phạm vi | Giá trị tồn tại trong | Ai thường sử dụng | Có đi qua request mới? | Chia sẻ giữa người dùng? |
| --- | --- | --- | --- | --- |
| Biến cục bộ method | Một lần gọi method | Bất kỳ Java method | Không | Không |
| Servlet **request attribute** | Một Servlet request | Filter, interceptor, controller | Không, trừ trường hợp cùng request được forward/dispatch | Không |
| Spring **request-scoped bean** | Một request trong web-aware context | Controller/service được DI | Không | Không |
| Servlet **session attribute** | HTTP session | Controller, auth/session manager | Có, nếu request mới tìm đúng session còn hiệu lực | Không theo mặc định |
| Spring **session-scoped bean** | HTTP session | Component có state của từng session | Có, khi đúng session và state còn hiệu lực | Không theo mặc định |
| Spring **application-scoped bean** | Một `ServletContext` | Thành phần dữ liệu cấp web app | Có | Có, trong cùng ServletContext |
| Spring **singleton bean** | Một Spring IoC container | Controller, service, repository thông thường | Có | Có, trong cùng container |
| Database/shared cache | Theo khóa, TTL và nghiệp vụ | Repository/service/cache | Có | Tùy khóa và phân quyền |

**Điểm dễ nhầm:** `@ApplicationScope` và Spring singleton có thể có vòng đời gần nhau trong một Spring Boot web app đơn giản, nhưng không đồng nhất về contract. Singleton là **một instance trên một bean definition trong một Spring IoC container**; application scope là **một instance trong ServletContext**. Khi có nhiều Spring ApplicationContext hoặc nhiều web applications, kết quả có thể khác.

Với **Spring WebFlux**, session abstraction là `WebSession`, không phải Servlet `HttpSession`. Ví dụ dưới đây chỉ dành cho Spring MVC Servlet, không copy nguyên imports sang WebFlux.

## 3. Request scope: dữ liệu chỉ cần trong một HTTP request

### 3.1. Servlet request attributes

`HttpServletRequest#setAttribute` cất object dưới một key trong **request hiện tại**:

```java
// Một Servlet Filter hoặc HandlerInterceptor đặt thông tin:
request.setAttribute("traceId", "trace-42");

// Một thành phần downstream đọc lại:
String traceId = (String) request.getAttribute("traceId");

// Có thể xóa:
request.removeAttribute("traceId");
```

Đây là **fragment Java**, giả định `request` là biến `jakarta.servlet.http.HttpServletRequest` đang được xử lý. Các giá trị không tự đi qua HTTP redirect (redirect tạo request mới). Khi Servlet **forward** một request đến resource khác, request có thể được dùng tiếp và attributes của nó vẫn có ý nghĩa; cần phân biệt forward với redirect.

Trong controller, thay vì dùng Servlet API trực tiếp, có thể khai báo:

```java
@GetMapping("/trace")
String trace(@RequestAttribute("traceId") String traceId) {
    return traceId;
}
```

`@RequestAttribute` trên tham số lấy **một attribute đã được đặt trước đó**. Nó không đọc trực tiếp JSON body, query parameter hoặc HTTP header cùng tên. Nếu dữ liệu có thể thiếu, dùng `@RequestAttribute(name="traceId", required=false)` hoặc `Optional<String>`, rồi có logic fallback rõ ràng.

Điều này có ích cho dữ liệu do server tính toán trong một request: correlation ID, kết quả tenant resolution, thông tin đã được xác thực và chuẩn hóa. Nếu attribute liên quan authentication/authorization, phải xác định nguồn tin cậy; một header tùy ý từ client **không tự trở thành danh tính đã xác thực**.

### 3.2. Spring bean `@RequestScope`

Bean có scope request được tạo khi Spring cần nó trong một request web phù hợp và được giữ để dùng lại **trong request đó**. Request khác tạo target bean mới.

```java
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
class RequestMarker {
    private final String id = UUID.randomUUID().toString();

    String getId() {
        return id;
    }
}
```

Đây là class hoàn chỉnh trong project Spring MVC (được sử dụng ở ví dụ ứng dụng phần 8). Khi hai method call đến `getId()` trên cùng scoped bean trong **cùng request**, nhận cùng ID; sang request mới, thường có ID mới. UUID chỉ để quan sát vòng đời, không được coi là token bảo mật hoặc định danh người dùng.

**Không nhầm** `@RequestScope` với `@RequestAttribute`:

| `@RequestScope` | `@RequestAttribute` |
| --- | --- |
| Annotation Spring đặt trên **bean class/factory method** | Annotation Spring MVC đặt trên **tham số handler** |
| Spring IoC quản lý vòng đời **instance bean** | MVC argument resolver đọc object theo key từ Servlet request |
| Có thể inject proxy vào singleton bean | Cần attribute đã được đặt trước khi handler resolve |
| Thích hợp component chứa context của một request | Thích hợp truyền giá trị ngắn, rõ key từ filter/interceptor |

Nếu chỉ cần một chuỗi `traceId`, không bắt buộc phải tạo cả scoped bean. Ngược lại, khi một object request có nhiều phụ thuộc và cần được inject ở vài lớp, request-scoped bean là lựa chọn dễ quản lý.

## 4. Session scope: nhiều request có thể chia sẻ state của cùng phiên

### 4.1. HTTP session và session ID

`HttpSession` là abstraction của Servlet container. Với cách quản lý thường gặp, server tạo một session rồi cấp cho browser một **session identifier** (ví dụ cookie `JSESSIONID`). Những request sau mang đúng session ID hợp lệ để server nhận ra cùng session.

```text
Browser                 Server
POST /preferences
  không cookie           tạo Session S-1
       ----------------->
  <--------------------- Set-Cookie: JSESSIONID=<opaque-id>

GET /preferences
  Cookie: JSESSIONID=...  tìm lại Session S-1
       ----------------->
  <--------------------- đọc language đã lưu

Browser khác/incognito
  không cookie hợp lệ     Session S-2 mới (nếu ứng dụng cần)
       ----------------->
```

Cookie thường lưu **session ID**, không phải toàn bộ Java object trong session. Các thuộc tính trong `HttpSession` thường nằm ở phía server/container hoặc session store bên ngoài. Đây khác với thiết kế cookie chứa dữ liệu client-readable hay JWT self-contained.

Session có thể hết hiệu lực vì timeout, logout, invalidate, session store không còn dữ liệu hoặc ứng dụng triển khai lại. **Không bao giờ coi việc có cookie `JSESSIONID` là bằng chứng session hợp lệ hoặc đã đăng nhập.**

### 4.2. Đọc và ghi Servlet session attributes

```java
@PostMapping("/preferences")
String save(@RequestParam String language, HttpSession session) {
    session.setAttribute("language", language);
    return "saved";
}

@GetMapping("/preferences")
String read(
        @SessionAttribute(name = "language", required = false)
        String language) {

    return language == null ? "unset" : language;
}
```

Các đoạn trên là **method fragment trong controller**, cần imports tương ứng. `@SessionAttribute` đọc attribute đã có trong HTTP session; `required=false` cho phép vắng mặt. Muốn ghi/xóa, thường dùng `HttpSession#setAttribute` / `removeAttribute` hoặc `WebRequest`.

**Cẩn thận với việc tạo session:**

```java
// Có thể tạo session nếu chưa có:
HttpSession session = request.getSession();

// Chỉ đọc session đã có, không tạo mới:
HttpSession existing = request.getSession(false);
if (existing != null) {
    Object value = existing.getAttribute("language");
}
```

`request` ở đây là `HttpServletRequest`. Inject tham số `HttpSession` vào handler hoặc truy cập `@SessionScope` bean có thể khiến session được tạo khi chưa có; khi endpoint cần **chỉ kiểm tra trạng thái cũ**, dùng `getSession(false)` để tránh tạo session không cần thiết.

Khi muốn logout hoặc bỏ mọi dữ liệu liên quan một session:

```java
HttpSession existing = request.getSession(false);
if (existing != null) {
    existing.invalidate();
}
```

`invalidate()` kết thúc session; không chỉ xóa một key. Nếu chỉ bỏ chọn ngôn ngữ, `existing.removeAttribute("language")` thích hợp hơn. Với hệ thống dùng Spring Security, logout/session fixation/concurrent session control cần đi qua cơ chế quản lý security chính thức thay vì coi mỗi lời gọi `invalidate()` tùy ý là luồng đăng xuất hoàn chỉnh.

### 4.3. Spring bean `@SessionScope`

`@SessionScope` là shortcut cho `@Scope("session")` và **mặc định tạo class-based scoped proxy** (TARGET_CLASS). Bean target thực được tạo riêng theo HTTP session và dùng lại trong cùng session.

```java
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

@Component
@SessionScope
class SessionPreferences {
    private String language = "vi";

    synchronized String getLanguage() {
        return language;
    }

    synchronized void setLanguage(String language) {
        this.language = language;
    }
}
```

Đây là class minh họa quản lý preference cho một session. Cơ chế đồng bộ bảo vệ hai method trong **cùng JVM và cùng bean instance**, nhưng không bảo đảm đồng bộ đa node khi session object được serialize/copy qua Redis hoặc backend khác. Với data shared giữa các server, cần thiết kế nhất quán phù hợp kho lưu trữ.

Spring không tự ghi mọi mutable field của session bean ra database. Ở session store ngoài, object có thể cần **serialization** và cách phát hiện thay đổi phù hợp cấu hình `Spring Session`. Một implementation session bean dạng mutable không đương nhiên phù hợp cho cluster.

## 5. Vì sao session-scoped bean có thể inject vào singleton controller?

Spring `@Controller`/`@Service` thường là **singleton**, được tạo một lần cho mỗi Spring IoC container. Nếu inject trực tiếp một object thực có vòng đời request/session ngắn hơn vào singleton, sẽ xảy ra *scope mismatch*: singleton giữ reference đến một instance trong khi request/session sau cần instance khác.

Giải pháp thông dụng là **scoped proxy**:

```text
Singleton controller (một instance)
       |
       +--- injected RequestMarker proxy -------------+
       |                                              |
       |  Request R1: proxy.getId() -> RequestMarker-R1|
       |  Request R2: proxy.getId() -> RequestMarker-R2|
       |                                              |
       +--- injected SessionPreferences proxy --------+
                                                      |
          Session S-A: proxy.getLanguage() -> Bean S-A
          Session S-B: proxy.getLanguage() -> Bean S-B
```

`@RequestScope` và `@SessionScope` mặc định sử dụng `proxyMode=ScopedProxyMode.TARGET_CLASS`. Proxy có thể được inject một lần vào singleton; mỗi method call trong request hợp lệ sẽ chuyển đến **target của scope hiện tại**.

Ví dụ controller constructor injection:

```java
@RestController
class ContextController {
    private final RequestMarker requestMarker;
    private final SessionPreferences preferences;

    ContextController(
            RequestMarker requestMarker,
            SessionPreferences preferences) {
        this.requestMarker = requestMarker;
        this.preferences = preferences;
    }

    @GetMapping("/context")
    String context() {
        return requestMarker.getId() + ":" + preferences.getLanguage();
    }
}
```

Đây là **class minh họa**, chưa chứa toàn bộ imports/config. `ContextController` vẫn là singleton; đối tượng mà constructor nhận là scoped proxy, **không phải cùng target state cho mọi người dùng**.

Cần lưu ý:

1. Class-based proxy thường phải subclass được class target. **Không tạo class scoped bean là `final` hoặc đặt method cần proxy thành `final`** khi dùng TARGET_CLASS, vì CGLIB không thể override.
2. Nếu bean được truy cập ngoài HTTP request phù hợp (ví dụ trong background job), Spring có thể ném lỗi *scope not active*, không thể tìm request/session hiện tại.
3. Cách thay thế là inject `ObjectProvider<ScopedBean>` và chỉ gọi `getObject()` trong request hợp lệ; nó không biến request scope thành global.
4. Đừng giữ lại **target scoped object** hoặc `HttpServletRequest` làm field trong singleton/static/global map sau khi request/session kết thúc.
5. `@Scope("request")` thuần có thể không bật proxy mặc định; khác `@RequestScope` shortcut. Đọc đúng API annotation và proxy mode được cấu hình.

## 6. Application scope, singleton, prototype và dữ liệu dùng chung

### 6.1. `@ApplicationScope`

Bean `@ApplicationScope` có vòng đời theo `ServletContext` của web application. Những request từ các client khác nhau **trong cùng ServletContext** có thể dùng chung instance.

```java
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.ApplicationScope;

@Component
@ApplicationScope
class ApplicationCounter {
    private final AtomicLong total = new AtomicLong();

    long increment() {
        return total.incrementAndGet();
    }
}
```

`AtomicLong` giúp thao tác tăng số nguyên an toàn khi nhiều threads cùng dùng instance trong một JVM. Nó **không** giúp tổng toàn cụm khi hai JVM chạy trên hai nodes độc lập. Để có số đếm toàn cluster, dùng atomic operation trên database/Redis/hệ thống metrics phù hợp.

### 6.2. Singleton không có nghĩa một instance cho toàn internet

`@Service`, `@Component`, `@Controller` thường mặc định singleton:

```java
@Service
class CatalogService {
    // Tốt: phụ thuộc khác được inject và methods không giữ state user.
}
```

Singleton **một instance cho một bean definition trong Spring container**, không phải một instance duy nhất khi có nhiều instances service, pods hoặc JVMs. Đừng đặt trường `String currentUserId` mutable trong singleton controller/service: hai request có thể ghi đè nhau, dẫn tới lộ dữ liệu giữa người dùng.

Thay vào đó, nhận user ID đã xác thực qua tham số/`Principal`, để context theo request/session đúng lifecycle, và lưu state bền vững trong kho phù hợp.

### 6.3. Prototype cũng không đồng nghĩa request scope

`@Scope("prototype")` tạo instance mới **khi Spring container resolve bean**. Nếu một singleton inject prototype trực tiếp vào constructor đúng một lần, singleton sẽ giữ **một prototype instance được inject lúc tạo singleton**. Không có nghĩa mỗi HTTP request hoặc mỗi method call tự có prototype mới.

Nếu cần object mới mỗi lần dùng, gọi `ObjectProvider.getObject()`/factory tại thời điểm thích hợp; nếu cần đúng một object trong mỗi HTTP request, ưu tiên `@RequestScope`.

### 6.4. Dữ liệu ứng dụng chung nên đặt ở đâu?

| Loại dữ liệu | Khuyến nghị |
| --- | --- |
| Config chỉ đọc, object immutable, stateless service | Singleton bean/configuration properties |
| Giá trị mutable dùng chung trong một JVM | Bean application/singleton với concurrency control |
| Cache chung giữa nhiều application instances | Redis/distributed cache hoặc dịch vụ cache phù hợp |
| Dữ liệu nghiệp vụ cần durability và transaction | Database |
| Bộ đếm/metrics hệ thống | Metrics backend, database atomic operation hoặc cơ chế aggregation |
| Giỏ hàng theo user cần tồn tại qua logout/device | Domain storage gắn user ID, không chỉ HttpSession |
| Ngôn ngữ UI chỉ dành cho request hiện tại | LocaleResolver, `Accept-Language` hoặc query/header được xác thực theo contract |
| Tùy chọn ngôn ngữ theo user nhiều request | Session, cookie có chính sách phù hợp hoặc user profile DB |

## 7. Nhiều cơ chế “giữ dữ liệu” khác nhau trong Spring MVC

### 7.1. `@RequestAttribute` vs `@SessionAttribute`

Hai annotation ở **tham số controller** chỉ **đọc thuộc tính**:

```java
@GetMapping("/context")
String context(
        @RequestAttribute(name = "trace", required = false) String trace,
        @SessionAttribute(name = "language", required = false) String language) {

    String left = trace == null ? "none" : trace;
    String right = language == null ? "none" : language;
    return left + ":" + right;
}
```

Đây là method fragment cho controller. `trace` chỉ thuộc request đang xử lý; `language` có thể đến từ session request trước. Annotation không tự khai báo lifecycle của bean và không tự ghi attribute.

### 7.2. `@SessionAttributes` — có chữ “s”, dành cho model workflow

`@SessionAttributes` được đặt **ở class controller**, giúp Spring MVC tự động đẩy một số **model attributes** vào session để dùng cho nhiều bước của form/controller. Nó **khác** `@SessionAttribute` (đọc một key session đã có).

```java
@Controller
@SessionAttributes("draft")
class DraftController {

    @ModelAttribute("draft")
    DraftForm draft() {
        return new DraftForm();
    }

    @GetMapping("/draft")
    String start() {
        return "draft/form";
    }

    @PostMapping("/draft/finish")
    String finish(
            @ModelAttribute("draft") DraftForm draft,
            SessionStatus status) {

        // Lưu draft thành công theo nghiệp vụ trước khi kết thúc.
        status.setComplete();
        return "redirect:/draft/done";
    }
}
```

Đây là **fragment MVC form**, cần kiểu `DraftForm`, template/view và imports do app định nghĩa; không phải chương trình chạy độc lập. `SessionStatus#setComplete()` kết thúc **việc quản lý model attributes được khai báo bởi @SessionAttributes** trong controller, **không invalidate toàn bộ HTTP session** và không xóa mọi attribute khác.

### 7.3. Flash attributes — chỉ dành cho request tiếp theo sau redirect

Trong flow Post-Redirect-Get:

```java
@PostMapping("/preferences")
String save(RedirectAttributes redirects) {
    redirects.addFlashAttribute("notice", "Đã lưu lựa chọn");
    return "redirect:/preferences";
}
```

Flash attribute thường được giữ **tạm thời** trong session bởi `FlashMapManager`, rồi được chuyển vào model của request nhận redirect và bị loại khỏi flash storage. Nó **không phải session attribute sống lâu**. Dùng cho thông báo thành công ngắn, không dùng làm cơ sở dữ liệu.

Nếu dùng `redirects.addAttribute("notice", "...")` thay vì `addFlashAttribute`, dữ liệu có thể được chuyển thành query/path parameter trong URI — phải cân nhắc logging, browser history, URL length và dữ liệu nhạy cảm.

### 7.4. `RequestContextHolder` — context gắn với thread

`RequestContextHolder` có thể cấp `RequestAttributes` của request hiện tại theo cơ chế thread-bound:

```java
RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
Object traceId = attributes.getAttribute("traceId", RequestAttributes.SCOPE_REQUEST);
```

Đoạn này là **fragment**, cần import `org.springframework.web.context.request.RequestAttributes` và `RequestContextHolder`. Nó không phải kho dữ liệu toàn ứng dụng và có thể ném exception nếu thread hiện tại không có request context. `@Async`, custom executor và `CompletableFuture` không tự đảm bảo chuyển context sang thread khác. Tránh thiết kế service phụ thuộc sâu vào thread-local web context; truyền dữ liệu cần thiết qua tham số rõ ràng khi có thể.

## 8. Ứng dụng Spring Boot 3.x + Java 17: quan sát cả ba scope

Ứng dụng dưới đây minh họa đồng thời:

1. `@RequestScope`: cùng request nhận cùng mã ID; request mới có ID khác.
2. `@SessionScope`: lựa chọn ngôn ngữ và lượt xem tồn tại khi reuse cùng HTTP session.
3. `@ApplicationScope`: bộ đếm chung cho những request đến **một ServletContext**.
4. `@RequestAttribute`: giá trị do Servlet Filter cung cấp.
5. `@SessionAttribute`: đọc key đã được lưu trực tiếp vào Servlet session.

Sử dụng **Spring Boot 3.x / Spring Framework 6.x trên Servlet MVC**. `spring-boot-starter-web` mang Tomcat và MVC thông dụng; ứng dụng không cần Redis hay database.

### 8.1. Dependencies

Với dự án Gradle đã có Boot plugin, dependency management, repositories và Java toolchain:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đây là **mảnh cấu hình `build.gradle`**, không phải file build độc lập. Không dùng `javac` trực tiếp không classpath cho project này; chạy thông qua Gradle/Maven của Spring Boot.

### 8.2. Một file Java chứa application, beans và controller

Tạo **`src/main/java/demo/scope/ScopeApplication.java`**:

```java
package demo.scope;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.context.annotation.ApplicationScope;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.context.annotation.SessionScope;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class ScopeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScopeApplication.class, args);
    }

    @Bean
    OncePerRequestFilter serverTagFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {

                // Được đặt bởi phía server, không đọc từ header do client tự khai.
                request.setAttribute("serverTag", "from-filter");
                chain.doFilter(request, response);
            }
        };
    }
}

@Component
@RequestScope
class RequestMarker {
    private final String id = UUID.randomUUID().toString();

    String getId() {
        return id;
    }
}

@Component
@SessionScope
class SessionPreferences {
    private String language = "vi";
    private int visits;

    synchronized String getLanguage() {
        return language;
    }

    synchronized void setLanguage(String language) {
        this.language = language;
    }

    synchronized int nextVisit() {
        return ++visits;
    }
}

@Component
@ApplicationScope
class ApplicationCounter {
    private final AtomicLong total = new AtomicLong();

    long next() {
        return total.incrementAndGet();
    }
}

@RestController
@RequestMapping("/api/scope")
class ScopeController {
    private final RequestMarker requestMarker;
    private final SessionPreferences preferences;
    private final ApplicationCounter counter;

    ScopeController(
            RequestMarker requestMarker,
            SessionPreferences preferences,
            ApplicationCounter counter) {
        this.requestMarker = requestMarker;
        this.preferences = preferences;
        this.counter = counter;
    }

    @GetMapping("/request")
    Map<String, String> requestInfo(
            @RequestAttribute("serverTag") String tag) {

        return Map.of(
                "first", requestMarker.getId(),
                "second", requestMarker.getId(),
                "tag", tag);
    }

    @GetMapping("/session")
    Map<String, Object> sessionInfo() {
        return Map.of(
                "language", preferences.getLanguage(),
                "visits", preferences.nextVisit());
    }

    @PostMapping("/session/language")
    Map<String, String> setLanguage(
            @RequestParam String language,
            HttpSession session) {

        if (!language.equals("vi") && !language.equals("en")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported language");
        }

        preferences.setLanguage(language);
        session.setAttribute("uiLanguage", language);
        return Map.of("language", language);
    }

    @GetMapping("/session/raw")
    Map<String, String> rawSessionAttribute(
            @SessionAttribute(name = "uiLanguage", required = false)
            String language) {

        return Map.of(
                "language", language == null ? "not-set" : language);
    }

    @PostMapping("/session/end")
    ResponseEntity<Void> endSession(HttpServletRequest request) {
        HttpSession current = request.getSession(false);
        if (current != null) {
            current.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/application")
    Map<String, Long> applicationInfo() {
        return Map.of("total", counter.next());
    }
}
```

**Đây là một file Java 17 hợp lệ khi đặt trong dự án Boot 3 nêu trên:** chỉ `ScopeApplication` có modifier `public`; các beans/controller khác không public và cùng package `demo.scope`. Spring Boot component scan từ package này tìm được cả ba `@Component` và `@RestController`.

Giải thích luồng:

- `ScopeController` là singleton mặc định và giữ các **proxy** được inject cho request/session/application scoped beans.
- `GET /api/scope/request` gọi `requestMarker.getId()` hai lần trong cùng request, nên `first` và `second` bằng nhau. Request sau được request-scoped target khác, nên ID khác (UUID không có cam kết tuyệt đối về uniqueness, nhưng trùng ngẫu nhiên thực tế rất khó).
- Servlet Filter đặt `serverTag`; `@RequestAttribute("serverTag")` đọc đúng attribute. Nếu controller chạy qua pipeline không có filter và thuộc tính bắt buộc vắng, request có thể lỗi binding.
- `GET /api/scope/session` dùng `@SessionScope` bean của session hiện tại và tăng số lượt xem. Client mới có language mặc định `vi` và lượt xem ban đầu `1`.
- `POST /api/scope/session/language` ghi cùng lựa chọn vào hai nơi **chỉ để đối chiếu API**: field của `@SessionScope` bean và key `uiLanguage` trong `HttpSession`. Chúng là hai cơ chế khác nhau, không tự đồng bộ nếu một bên đổi độc lập.
- `GET /api/scope/session/raw` dùng `@SessionAttribute(required=false)` để đọc riêng attribute `uiLanguage`. Với session mới chưa có key, trả `not-set`; không dùng endpoint này để buộc tạo session.
- `POST /api/scope/session/end` invalidate session đã tồn tại, trả **204 No Content**. Không sử dụng proxy session bean sau khi invalidate trong cùng method.
- `GET /api/scope/application` tăng bộ đếm thuộc `ServletContext` hiện tại. Các client khác nhau có thể nhìn giá trị tăng chung; không có cơ sở dữ liệu nên restart hoặc node khác có bộ đếm riêng.

**Không áp dụng lựa chọn ngôn ngữ tự động cho `MessageSource` trong ví dụ này.** Field `language` chỉ là chuỗi lưu state demo. Muốn điều khiển localization thật, cần tích hợp `LocaleResolver`/`LocaleChangeInterceptor` hoặc giải pháp i18n phù hợp; xem phần 12.

### 8.3. Quan sát bằng curl và cookie jar

Chạy dự án:

```bash
./gradlew bootRun
```

Hai request không cần session:

```bash
curl -sS 'http://localhost:8080/api/scope/request'
curl -sS 'http://localhost:8080/api/scope/request'
```

Body minh họa (UUID khác nhau theo từng request):

```json
{"first":"<uuid-A>","second":"<uuid-A>","tag":"from-filter"}
```

Dòng JSON trên là **sơ đồ giá trị**, dấu `<uuid-A>` không phải UUID thực tế. Lần tiếp theo `first` và `second` sẽ chứa UUID B khác A. **Không thể công bố stdout cụ thể** cho UUID sinh ngẫu nhiên trước khi chạy.

Dùng cookie jar để giữ cùng session:

```bash
curl -i -c session-a.txt \
  'http://localhost:8080/api/scope/session'

curl -i -b session-a.txt -c session-a.txt \
  -X POST \
  -d 'language=en' \
  'http://localhost:8080/api/scope/session/language'

curl -sS -b session-a.txt \
  'http://localhost:8080/api/scope/session'

curl -sS -b session-a.txt \
  'http://localhost:8080/api/scope/session/raw'
```

Body phản hồi theo thứ tự, nếu server mới và không có client khác chạm vào cùng session:

```json
{"language":"vi","visits":1}
{"language":"en"}
{"language":"en","visits":2}
{"language":"en"}
```

JSON key order có thể khác, không thuộc hợp đồng. `-c session-a.txt` lưu cookie do server trả; `-b session-a.txt` gửi cookie đó trong request tiếp. Không gửi cookie từ session A thì request mới không mặc nhiên dùng session A.

Tạo session của client B qua cookie jar khác:

```bash
curl -i -c session-b.txt \
  'http://localhost:8080/api/scope/session'
```

Giá trị mong đợi (session B chưa từng được dùng): `{"language":"vi","visits":1}`, độc lập với session A. Không commit các file `session-a.txt` / `session-b.txt` vào repository: chúng chứa session identifier nhạy cảm.

Kết thúc session A:

```bash
curl -i -b session-a.txt \
  -X POST \
  'http://localhost:8080/api/scope/session/end'
```

Kỳ vọng status **204 No Content**. Sau đó session ID cũ không còn hợp lệ; request tiếp với cookie cũ có thể tạo session mới nếu endpoint cần session, tùy container và cấu hình cookie. Không dựa vào việc server “tự giữ” lại dữ liệu sau invalidate.

## 9. Test MockMvc để chứng minh request/session/application scope

Trong dự án trên, thêm file **`src/test/java/demo/scope/ScopeApplicationTests.java`**:

```java
package demo.scope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScopeApplicationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper; // Jackson 2 của baseline Spring Boot 3.

    @Test
    void requestTargetReusedWithinOneRequestButNotTheNext()
            throws Exception {

        MvcResult first = mvc.perform(get("/api/scope/request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tag").value("from-filter"))
                .andReturn();

        MvcResult second = mvc.perform(get("/api/scope/request"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode a = mapper.readTree(
                first.getResponse().getContentAsString());
        JsonNode b = mapper.readTree(
                second.getResponse().getContentAsString());

        assertEquals(a.get("first").asText(), a.get("second").asText());
        assertEquals(b.get("first").asText(), b.get("second").asText());
        assertNotEquals(a.get("first").asText(), b.get("first").asText());
    }

    @Test
    void sessionStateSurvivesOnlyWhenSameSessionIsReused()
            throws Exception {

        MvcResult first = mvc.perform(get("/api/scope/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("vi"))
                .andExpect(jsonPath("$.visits").value(1))
                .andReturn();

        MockHttpSession session = (MockHttpSession)
                first.getRequest().getSession(false);
        assertNotNull(session);

        mvc.perform(post("/api/scope/session/language")
                        .session(session)
                        .param("language", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("en"));

        mvc.perform(get("/api/scope/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.visits").value(2));

        mvc.perform(get("/api/scope/session/raw").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("en"));

        // Request không tái sử dụng session cũ -> state riêng.
        mvc.perform(get("/api/scope/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("vi"))
                .andExpect(jsonPath("$.visits").value(1));
    }

    @Test
    void unknownSessionAttributeIsOptional() throws Exception {
        mvc.perform(get("/api/scope/session/raw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("not-set"));
    }

    @Test
    void invalidSessionChoiceReturns400() throws Exception {
        mvc.perform(post("/api/scope/session/language")
                        .param("language", "unknown"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applicationCounterIsSharedInsideTheWebApp()
            throws Exception {
        mvc.perform(get("/api/scope/application"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber());

        mvc.perform(get("/api/scope/application"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    void sessionCanBeInvalidated() throws Exception {
        MvcResult first = mvc.perform(get("/api/scope/session"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession)
                first.getRequest().getSession(false);
        assertNotNull(session);

        mvc.perform(post("/api/scope/session/end").session(session))
                .andExpect(status().isNoContent());
        // Không tái sử dụng MockHttpSession đã bị invalidate.
    }
}
```

Chạy:

```bash
./gradlew test
```

Với **Spring Boot 3.x**, `spring-boot-starter-web`, `spring-boot-starter-test` và code đúng như phần 8, **sáu test được thiết kế để pass**. Đây là kỳ vọng sau rà soát, **không phải kết quả Gradle đã chạy thực tế**.

Giải thích một số assertions:

- Hai lời gọi `getId()` trong **cùng HTTP request** trả cùng một ID; request thứ hai nhận marker mới. UUID khác nhau là hành vi thực tế kỳ vọng trong test mẫu.
- Trong `MockMvc`, `.session(session)` gửi lại cùng `MockHttpSession` giữa nhiều request. **Không** tái sử dụng instance session thì request mới có state độc lập.
- `@SessionScope` tự xác định target bean qua request/session. Test không dựa vào Java static variables hay field của controller để giả lập session.
- `@SessionAttribute(required=false)` trả fallback khi key chưa tồn tại.
- Counter application được test ở mức output là số; một suite nhiều test hoặc có requests song song không nên assert tổng tuyệt đối bắt đầu từ 1.
- Test invalidate tránh dùng lại session đã bị đánh dấu invalid; nếu muốn kiểm tra hậu quả, hãy gửi request với session mới.

**Giới hạn:** `MockMvc` không kiểm tra browser cookie flags, network proxy, Redis, cluster, multiple JVMs hoặc lỗi serialize/deserialization session. Những đặc tính đó cần integration tests ở môi trường phù hợp. Test dùng `ObjectMapper` từ Jackson 2 vì baseline là Boot 3; Boot 4 chuyển đường JSON mặc định sang Jackson 3 `JsonMapper`, nên chính imports/test mapper cần rà soát khi migrate.

## 10. Request, session và application khi có nhiều threads đồng thời

### Request scope thường tách theo request, nhưng đừng nhầm với thread-safety tuyệt đối

Mỗi request có request-scoped bean riêng theo lifecycle. Trong xử lý đồng bộ thông thường, dữ liệu này không bị chia sẻ ngẫu nhiên giữa hai HTTP requests. Tuy nhiên, một request có thể có async dispatch/callback; framework và code cần quản lý context đúng, không tự giả định “request luôn chạy một thread duy nhất” trong mọi mô hình.

Nếu chuyển công việc sang executor, đừng giữ reference `HttpServletRequest` hoặc dựa vào `RequestContextHolder` trên worker thread không có context. Hãy lấy dữ liệu cần thiết trước rồi truyền immutable value hoặc context carrier đã thiết kế rõ ràng.

### Session scope có thể bị nhiều request truy cập cùng lúc

Hai tab hoặc nhiều HTTP calls của cùng browser có thể gửi request **đồng thời** với cùng session ID. Vì vậy:

```text
Session S-A có cartQuantity = 0

Request 1: đọc 0 ------+
                       +---- cùng ghi 1
Request 2: đọc 0 ------+

Kết quả có thể là 1 thay vì 2 nếu dùng read-modify-write không đồng bộ.
```

`HttpSession` **không tự biến object mutable bên trong thành thread-safe**. Các giải pháp:

- Dùng immutable value + cập nhật qua cơ chế atomic thích hợp.
- Với state tạm trong một JVM, khóa phù hợp hoặc atomic counter; đánh giá contention và lifecycle.
- Với cluster/Redis/DB, dùng transaction, optimistic locking, compare-and-set, atomic increments, database constraints hoặc chiến lược consistency phù hợp.
- Không đồng bộ bằng `synchronized(this)` của một scoped bean rồi mặc định nó có hiệu lực **trên nhiều nodes**.
- Không giữ session lock trong lúc gọi API bên ngoài chậm nếu không cần; có thể khiến các request của cùng user bị giữ lại quá lâu.

### Application scope và singleton càng phải chú ý concurrency

Dữ liệu dùng chung cho tất cả requests trong cùng instance chịu cạnh tranh nhiều hơn. Chẳng hạn một `HashMap` mutable đặt vào singleton không an toàn cho concurrent writes; dùng `ConcurrentHashMap` khi cần, và đồng thời nghĩ tới atomicity của cả thao tác nghiệp vụ.

**Scope quyết định “ai nhìn thấy cùng object”**, không quyết định “object tự đồng bộ” hoặc “dữ liệu được lưu bền vững”.

## 11. Session timeout, cookies, security và privacy

### 11.1. Cấu hình session trong Spring Boot Servlet

Một số properties thông dụng của Spring Boot Servlet:

```properties
# Ví dụ: session không hoạt động trong khoảng thời gian này sẽ hết hạn
server.servlet.session.timeout=30m

# Cookie session chỉ truyền trên HTTPS (triển khai phải dùng HTTPS thật)
server.servlet.session.cookie.secure=true

# Hạn chế JavaScript đọc cookie session
server.servlet.session.cookie.http-only=true

# Điều chỉnh cross-site cookie policy theo use case
server.servlet.session.cookie.same-site=lax
```

Đây là **một cấu hình mẫu**, không tự bật mọi setting trong môi trường có HTTP plaintext hoặc cross-site frontend. Cookie `Secure` cần kết nối HTTPS ở phía client; nếu bật trên môi trường local chỉ dùng HTTP, việc browser lưu/gửi cookie có thể khác kỳ vọng. `SameSite=None` thường cần `Secure` và phải đánh giá CSRF/CORS/authentication kỹ.

`server.servlet.session.timeout` là session inactivity timeout cấu hình; session có thể chấm dứt sớm do logout, invalidate, backend store failure hoặc thay đổi policy. Với Spring Session backend bên ngoài, TTL và timeout handling còn phụ thuộc cơ chế save/expiration của repository.

### 11.2. Session ID là bearer credential cần bảo vệ

- Session ID nên khó đoán, được truyền qua HTTPS và được quản lý qua cookie policy phù hợp.
- Tránh đưa session ID vào URL/query string, access logs, analytics, Referer hoặc screenshots.
- Chống **session fixation** khi đăng nhập; với Spring Security, dùng cơ chế session fixation protection do framework cung cấp theo phiên bản/cấu hình.
- Nếu dựa vào cookies cho authenticated form/actions, bảo vệ **CSRF**. `SameSite` có thể hỗ trợ nhưng không thay thế mọi lớp phòng thủ phù hợp.
- Đăng xuất cần xóa authentication state và kết thúc/revoke session theo chính sách; chỉ xóa một key ngôn ngữ không đăng xuất người dùng.
- Không lưu mật khẩu plaintext, refresh token thô, PII thừa hoặc object rất lớn trong session.
- Session bean/attribute có thể chứa object nội bộ không serializable; điều này gây lỗi khi clustering hoặc persist sessions. Kiểm tra thiết kế serialization/schema trước khi dùng store ngoài.

### 11.3. Session state không thay thế phân quyền

Một request mang cookie session hợp lệ chưa chứng minh endpoint được phép thay đổi mọi tài nguyên. Authorization cần dựa trên identity/principal đáng tin và kiểm tra quyền đối với tài nguyên cụ thể. Đừng chấp nhận tham số `userId` rồi truy cập state/session của người dùng khác chỉ theo ID đó.

Một số kiến trúc REST API không dùng server-side session mà dùng token đã xác minh theo từng request. **Stateless authentication** không đồng nghĩa ứng dụng hoàn toàn không có state: domain data vẫn nằm trong DB/cache; token cũng có thời hạn và chính sách revoke tùy yêu cầu.

## 12. Trạng thái ngôn ngữ: khi nào dùng session, locale và user profile?

Lựa chọn language là ví dụ dễ học về state nhưng có nhiều thiết kế hợp lệ.

### 12.1. Chỉ dùng ngôn ngữ client yêu cầu cho mỗi request

Client gửi `Accept-Language`, server dùng một cơ chế locale resolution thích hợp. Trong Spring MVC, `LocaleResolver`, `LocaleContextResolver` và `LocaleContextHolder` có vai trò với i18n. Khi không cần nhớ lựa chọn thủ công, dùng ngôn ngữ theo request/header là phù hợp.

```http
GET /products HTTP/1.1
Accept-Language: vi-VN,vi;q=0.9,en;q=0.7
```

Header do client gửi không bắt buộc được ứng dụng chấp nhận nguyên vẹn: cấu hình locale resolver, supported locales và fallback policy quyết định ngôn ngữ được chọn.

### 12.2. Người dùng chọn ngôn ngữ một lần cho session

Nếu UI có lựa chọn trong session, `@SessionScope`/`HttpSession` có thể nhớ language và dùng để cấu hình locale của các request sau. Nhưng **chỉ lưu chuỗi "en" trong `SessionPreferences` không tự khiến Spring MessageSource render tiếng Anh**. Ứng dụng phải tích hợp với `LocaleResolver`/locale change strategy phù hợp.

Không đặt bất kỳ giá trị ngôn ngữ nào từ request vào session mà không whitelist. Khi lựa chọn chỉ hỗ trợ `vi`/`en`, kiểm tra input như ví dụ phần 8.

### 12.3. Sở thích theo user trên nhiều thiết bị

Khi người dùng đăng nhập trên điện thoại và laptop, hai browsers thường có sessions riêng. Muốn cùng ngôn ngữ trên mọi thiết bị, nên lưu preference theo user ID trong DB và đưa vào locale selection sau authentication. Session có thể cache preference hiện tại nhưng không là nguồn dữ liệu bền vững.

## 13. Hệ thống nhiều instance: tại sao session có thể “mất” khi scale?

Khi triển khai hai pods:

```text
Load balancer
     |
     +---- App A (JVM A) -> HttpSession local A
     |
     +---- App B (JVM B) -> HttpSession local B

Browser gửi JSESSIONID từ App A
request tiếp theo được route sang App B
=> App B không mặc nhiên có session data của App A.
```

Các giải pháp cần cân nhắc:

1. **Sticky sessions** (load balancer giữ cùng client về một node): đơn giản, nhưng khi node lỗi/restart vẫn có thể mất session và mất cân bằng tải.
2. **Shared session store** (ví dụ Spring Session + Redis): thay thế implementation `HttpSession` bằng một backend dùng chung, để A/B có thể đọc cùng session ID.
3. **Database/domain store**: nếu dữ liệu cần bền vững lâu dài, lưu theo user/resource thay vì nhét tất cả vào HTTP session.
4. **Stateless API**: tránh HTTP session cho endpoint thích hợp, truyền authentication/context theo token và truy vấn storage thật.

**Spring Session** cung cấp tích hợp với Servlet `HttpSession` thông qua filter, backend như Redis/JDBC tùy dependency/cấu hình. Khi được thiết lập, code dùng `HttpSession` và nhiều cơ chế session của Spring MVC có thể tiếp tục hoạt động mà ít thay đổi, nhưng phải xử lý:

- Serialization/versioning của session attributes khi rolling deployments.
- TTL, cookie settings, backend availability, memory và chi phí lưu trữ.
- Concurrent writes vào cùng session và chiến lược lưu/flush.
- Thay đổi thiết kế ứng dụng để không chứa graph object quá lớn hoặc không serializable trong session.
- Observability và disaster recovery cho session store.

Ví dụ **mảnh dependency Boot 3** khi thật sự cấu hình Redis-backed sessions (không thêm vào project demo phần 8 nếu không có Redis):

```groovy
dependencies {
    implementation 'org.springframework.session:spring-session-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

Trong project sử dụng Spring Boot BOM và auto-configuration phù hợp, còn cần cấu hình kết nối Redis và các session settings theo version. Đây không phải cấu hình Redis hoàn chỉnh. **Không coi “thêm dependency” là đủ chứng minh state đã chia sẻ an toàn qua nhiều node**; phải có integration tests kiểm tra hai app instances dùng chung Redis/session store.

Spring Session 4.x đi cùng thế hệ Spring Framework 7 / Boot 4, còn Boot 3.x thường sử dụng Spring Session 3.x do BOM quản lý. Không hardcode hai major không tương thích.

## 14. Khi nào nên tránh session?

Session có ích, nhưng không luôn là cách tốt nhất:

- **API stateless cần scale ngang:** request độc lập; server lấy state domain từ DB/cache với khóa user/resource.
- **Các tác vụ dài hoặc queue jobs:** trạng thái job phải có identity và persistence riêng, không biến session thành job repository.
- **Dữ liệu lớn:** lưu identifier và truy vấn DB/cache có thể hợp lý hơn sao chép cả danh sách entity vào session.
- **Thông tin toàn ứng dụng:** dùng configuration/singleton/cache chung, không nhân bản trong mỗi session.
- **Tùy chọn dài hạn:** lưu user preference bền vững; session chỉ là cache tạm.
- **Chuyển trạng thái giữa microservices:** dùng request metadata hoặc explicit context, không giả định hai service chia sẻ cùng Servlet `HttpSession` nếu chưa thiết kế.
- **Yêu cầu bí mật và audit:** không lưu thông tin nhạy cảm trong session tùy tiện, cân nhắc tính tuân thủ và quản lý vòng đời dữ liệu.

Một cách chọn nhanh:

| Câu hỏi | Nếu “Có” | Hướng thiết kế |
| --- | --- | --- |
| Dữ liệu chỉ phục vụ request hiện tại? | Có | Method parameter, request attribute, `@RequestScope` |
| Cần dữ liệu trong request kế sau **cùng session**? | Có | `HttpSession`/`@SessionScope`, nếu được phép stateful |
| Chỉ cần thông báo sau redirect? | Có | Flash attributes |
| Cần giữ model qua các bước của cùng wizard controller? | Có | `@SessionAttributes` + `SessionStatus` |
| Mọi người dùng của cùng web app dùng chung? | Có | Singleton/`@ApplicationScope` và bảo vệ concurrent access |
| Mọi node/pods đều phải thấy? | Có | Shared store hoặc distributed coordination |
| Dữ liệu phải còn sau restart/logout hoặc dùng nhiều thiết bị? | Có | Database/domain storage gắn user/resource |
| Đây là identity hay authorization? | Có | Spring Security/principal/permission check chính thức |

## 15. Những lỗi phổ biến cần nhận diện

### Lỗi A — giữ thông tin user trong singleton field

```java
@RestController
class UnsafeController {
    private String currentUserId; // Sai: singleton chia sẻ giữa nhiều request.

    @PostMapping("/select-user")
    String select(@RequestParam String userId) {
        currentUserId = userId;
        return currentUserId;
    }
}
```

Đây là **anti-pattern**, không dùng production. Hai users gửi request đồng thời có thể nhìn state của nhau. Cần identity đã được authenticate theo mỗi request, hoặc store đúng session/user với authorization.

### Lỗi B — tưởng request attribute sẽ qua redirect

```java
request.setAttribute("notice", "Đã lưu");
return "redirect:/summary";
```

Request mới tại `/summary` sẽ không tự có attribute `notice` từ request cũ. Sử dụng `RedirectAttributes.addFlashAttribute("notice",...)` nếu cần một thông báo ngắn cho request sau redirect.

### Lỗi C — inject trực tiếp request/session bean không có proxy vào singleton

Nếu tạo bean bằng `@Scope("session")` thuần, không có scoped proxy hay provider, singleton có thể gặp inactive scope trong lúc khởi tạo hoặc giữ sai object. Với Spring MVC, dùng `@SessionScope`/`@RequestScope` hoặc khai báo `proxyMode` phù hợp.

### Lỗi D — dùng `@SessionAttribute` để mong tự tạo giá trị

`@SessionAttribute("language")` không ghi language khi key chưa tồn tại. Khi `required=true` (mặc định), thiếu attribute là lỗi resolve argument; với optional cho phép logic fallback.

### Lỗi E — nhầm `@SessionAttributes` với `@SessionScope`

`@SessionAttributes("draft")` là marker trên controller class cho **model conversation**, dùng `SessionStatus#setComplete()` để dọn. `@SessionScope` là marker của **bean lifetime**. Chúng giải quyết hai nhu cầu khác nhau.

### Lỗi F — dùng session bean mutable mà không xử lý concurrency

Các request cùng browser/session có thể chạy song song. `private int cartCount; cartCount++;` không bảo đảm atomicity. Dùng giải pháp đồng bộ đúng phạm vi lưu trữ; đặc biệt chú ý Redis/cluster.

### Lỗi G — session timeout/restart làm mất state

Session không phải database. App mới restart, node khác, Redis key hết TTL hoặc user logout đều có thể khiến state biến mất. Với thông tin bền vững, lưu DB đúng contract.

### Lỗi H — session cookie không được gửi

Nếu browser không lưu cookie, `SameSite`/`Secure` sai môi trường, domain/path lệch hoặc CORS credentials không được thiết lập khi cross-origin, request mới có thể không tìm lại session. Kiểm tra `Set-Cookie`, request `Cookie`, scheme HTTPS, devtools và gateway.

### Lỗi I — truy cập scoped bean từ thread background

`@Async`, scheduler hay executor không có HTTP request context mặc định. Truy cập proxy request/session lúc đó có thể gây `ScopeNotActiveException`/“No thread-bound request”. Truyền immutable context qua tham số hoặc lưu trạng thái bền vững ngoài request.

### Lỗi J — coi application scope là cache toàn cluster

`@ApplicationScope` chỉ nằm trong một `ServletContext`. Khi scale hai pods, mỗi pod có instance riêng. Với global state, cần shared storage/cơ chế coordination.

### Lỗi K — tạo session khi chỉ muốn đọc

Dùng `request.getSession()` hay inject `HttpSession` khi lẽ ra chỉ cần “có session cũ hay không” có thể làm tăng số sessions. Sử dụng `getSession(false)` nếu mục tiêu là kiểm tra mà không tạo.

### Lỗi L — bỏ qua bảo mật session

Session ID có thể bị đánh cắp nếu thiếu HTTPS/cookie controls; CSRF có thể ảnh hưởng state-changing endpoints dựa trên cookie; session fixation phải được xử lý khi login. Đừng cho rằng bean có `@SessionScope` tự bảo đảm tính bảo mật.

## 16. Lưu ý khi nâng cấp Spring

| Nền tảng | Khác biệt thực hành |
| --- | --- |
| Spring Framework 5.x + Spring Boot 2.x | Web scopes request/session/application có sẵn; Servlet API thường ở `javax.servlet.*` |
| Spring Framework 6.x + Spring Boot 3.x | Java 17+, Servlet ở `jakarta.servlet.*`; ví dụ chạy phần 8 dùng baseline này |
| Spring Framework 7.x + Spring Boot 4.x | Cơ chế scope bean/HTTP session vẫn có, nhưng starters, Spring Session major version và JSON/test auto-configuration cần kiểm tra theo BOM mới |

`@RequestScope`, `@SessionScope`, `@ApplicationScope` là các annotation cấp Spring bean, không phụ thuộc JSON mapper. Vì vậy việc chuyển Jackson 2 → Jackson 3 khi lên Boot 4 **không tự thay lifecycle của bean scopes**, nhưng code test đọc JSON bằng `ObjectMapper` sẽ cần đổi nếu dự án dùng mapper mặc định Jackson 3.

Trong môi trường **AOT/native image**, khởi tạo bean/proxy/reflection cần được rà soát theo Spring Boot version; constructor/proxy target không tương thích có thể làm build native lỗi dù chạy JVM bình thường. Đừng bỏ qua smoke tests cho request/session scoping sau migration.

## 17. Checklist thiết kế state theo scope

- [ ] Mỗi giá trị có owner rõ: request, HTTP session, ServletContext/Spring context, database hoặc shared cache.
- [ ] Không chứa user-specific mutable state trong singleton controller/service.
- [ ] Session data chỉ dành cho user/session liên quan; không tin user ID tự khai.
- [ ] `@RequestScope` và `@SessionScope` được inject vào singleton qua scoped proxy hoặc provider đúng cách.
- [ ] Không giữ reference đến request object/servlet request ở async worker khi request kết thúc.
- [ ] Các session-scoped mutable objects được kiểm soát concurrent requests; thiết kế lại khi multi-node.
- [ ] Cookies session được bảo vệ bởi HTTPS, Secure/HttpOnly/SameSite phù hợp; authentication, CSRF và session fixation được xử lý.
- [ ] Đọc session cũ qua `getSession(false)` khi không muốn tạo session mới.
- [ ] Session có timeout, logout/invalidate policy, không lưu secrets/PII không cần thiết.
- [ ] Redirect dùng flash attributes thay cho request attrs để chuyển thông báo một lần.
- [ ] `@SessionAttributes` workflow được kết thúc bằng `SessionStatus#setComplete()`.
- [ ] Dữ liệu cần sau restart hoặc chia sẻ nhiều thiết bị được lưu vào DB.
- [ ] Dữ liệu cần qua nhiều nodes dùng shared store/cơ chế nhất quán.
- [ ] Có tests cho hai HTTP requests khác nhau, hai sessions khác nhau và request trùng session.
- [ ] Không assert counter toàn ứng dụng từ 1 nếu test suite có thể chạy song song hoặc có request khác.

## 18. Bài tập tự đánh giá

1. Một filter tính `traceId`. Hãy chọn giữa `request.setAttribute` + `@RequestAttribute` và `@RequestScope` bean. Cách nào hợp lý khi chỉ có một chuỗi? Cách nào hợp lý nếu cần một request context object có nhiều field?
2. Hai tabs của cùng browser gửi đồng thời `POST /cart/add`. Vì sao `@SessionScope` không bảo đảm `cartCount++` an toàn? Đề xuất giải pháp khi deployment một node và khi có Redis.
3. Thêm endpoint cho xem lựa chọn ngôn ngữ bằng `@SessionAttribute(name="uiLanguage",required=false)`. Khi key không có, có nên gọi `getSession()` để tạo mới không?
4. Trong ứng dụng mẫu, vì sao `requestMarker.getId()` hai lần trên cùng request trả cùng giá trị dù controller là singleton? Điều gì thay đổi khi request tiếp theo đến?
5. Dùng request attribute `notice` rồi redirect sang URL khác. Tại sao mất notice? Hãy sửa bằng `RedirectAttributes.addFlashAttribute` và phân biệt flash với long-lived session attribute.
6. Wizard form ba bước dùng `@SessionAttributes("draft")`: khi hoàn tất, `SessionStatus#setComplete()` có invalidate luôn session dùng để đăng nhập không?
7. Một service singleton có `private String lastUserId` cập nhật sau mỗi request. Dữ liệu có thể leak thế nào? Sửa thiết kế qua identity ở request và domain storage theo user ID.
8. Hai Spring Boot pods dùng local HttpSession, load balancer chuyển request ngẫu nhiên. Điều gì xảy ra với `@SessionScope`? Nêu hai chiến lược xử lý và trade-offs.
9. Người dùng muốn lựa chọn ngôn ngữ xuất hiện trên điện thoại khác sau khi đăng nhập. Vì sao lưu `language` trong session của laptop là chưa đủ? Đưa ra thiết kế DB preference + locale resolution.
10. Mở rộng MockMvc tests: lưu `language=en` ở session A, GET ở session B vẫn `vi`; invalidate session A và xác nhận request mới không thấy `en`.
11. Một `@Async` method gọi `RequestContextHolder.currentRequestAttributes()` và lỗi. Giải thích vì sao không nên “fix” bằng cách chia sẻ request object tùy tiện sang mọi thread.
12. Dữ liệu cấu hình chung cho tất cả users thay đổi mỗi phút. So sánh singleton local cache, `@ApplicationScope` và distributed cache; khi nào cần TTL/invalidation.

## 19. Tài liệu tham khảo chính thức

- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): singleton, prototype, request, session, application, scoped proxies.
- [Spring Framework 6.2 — Web Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html): request/session/application trong nền tảng Spring 6.
- [Spring Framework — `@RequestScope` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/context/annotation/RequestScope.html): shortcut request scope và scoped proxy.
- [Spring Framework — `@SessionScope` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/context/annotation/SessionScope.html): session bean và proxyMode mặc định.
- [Spring MVC — `@RequestAttribute`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestattrib.html): đọc request attributes đã có.
- [Spring MVC — `@SessionAttribute`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/sessionattribute.html): đọc attributes đã có trong session.
- [Spring MVC — `@SessionAttributes`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/sessionattributes.html): giữ model attributes tạm qua nhiều request, `SessionStatus`.
- [Spring MVC — Flash Attributes](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/flash-attributes.html): lưu dữ liệu tạm giữa redirect và request kế tiếp.
- [Spring Boot — Servlet Web Applications](https://docs.spring.io/spring-boot/reference/web/servlet.html): session timeout và cookie settings.
- [Spring Session — HttpSession Integration](https://docs.spring.io/spring-session/reference/http-session.html): session storage dùng chung và cách thay thế Servlet HttpSession.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="session-localization-002">Cookie và cấu hình thời hạn, Secure, HttpOnly, SameSite</a>

<details>
<summary>Xem chi tiết</summary>

# Cookie và cấu hình thời hạn, Secure, HttpOnly, SameSite trong Spring MVC

## 1. Mục tiêu và bối cảnh

HTTP về cơ bản là giao thức **stateless**: server không tự ghi nhớ người dùng giữa hai request chỉ nhờ HTTP. **Cookie** là một cơ chế để server hướng dẫn trình duyệt lưu một cặp tên–giá trị kèm các thuộc tính, rồi trình duyệt tự quyết định khi nào gửi cặp đó trong request tiếp theo. Ứng dụng Spring MVC có thể dùng cookie cho session identifier, tùy chọn giao diện, locale, ghi nhận sự đồng ý của người dùng hoặc những thông tin trạng thái khác.

Bài này tập trung vào **vòng đời và phạm vi cookie**, đặc biệt là `Max-Age`/`Expires`, `Secure`, `HttpOnly`, `SameSite`, và cách cấu hình, đọc, xóa trong **Spring MVC Servlet stack**.

Học xong, bạn có thể:

1. Phân biệt header **`Set-Cookie` của response** và **`Cookie` của request**, cookie session và cookie có thời hạn.
2. Giải thích `Max-Age`, `Expires`, `Domain`, `Path`, `Secure`, `HttpOnly`, `SameSite=Strict/Lax/None` và giới hạn của từng cơ chế.
3. Dùng `ResponseCookie`, `ResponseEntity`, `@CookieValue` để đặt, đọc và xóa cookie trong controller Spring MVC.
4. Cấu hình các thuộc tính của **session cookie** do Servlet container / Spring Boot quản lý mà không nhầm với cookie tùy chỉnh.
5. Hiểu quan hệ giữa cookie, HTTPS, CSRF, XSS, CORS, third-party cookie policy, session timeout, và kiểm thử bằng `MockMvc`.
6. Nhận diện các cấu hình không an toàn: `SameSite=None` thiếu `Secure`, bỏ `HttpOnly` cho session ID, `Domain` quá rộng hoặc giữ cookie nhạy cảm quá lâu.

Ví dụ ứng dụng chạy theo **Java 17+, Spring Framework 6.x, Spring Boot 3.x**. Trình duyệt có thể áp dụng chính sách cookie khác nhau theo phiên bản, thiết lập quyền riêng tư và ngữ cảnh first-party/third-party; server gửi `Set-Cookie` **không có nghĩa** trình duyệt chắc chắn lưu hoặc gửi lại cookie đó.

## 2. Cookie di chuyển qua HTTP như thế nào?

Một response hướng dẫn client tạo cookie:

~~~http
HTTP/1.1 204 No Content
Set-Cookie: view_pref=compact; Path=/api/prefs; Max-Age=604800; Secure; HttpOnly; SameSite=Lax
~~~

Ở các request sau, nếu điều kiện trình duyệt thỏa mãn, client gửi:

~~~http
GET /api/prefs/current HTTP/1.1
Host: example.com
Cookie: view_pref=compact
~~~

Những điều quan trọng:

- `Set-Cookie` là **response header** để tạo, thay đổi hoặc hết hạn cookie. Để gửi nhiều cookie, server thường dùng **nhiều header `Set-Cookie` riêng**, không gộp tùy tiện thành một header cách nhau bằng dấu phẩy.
- `Cookie` là **request header**, chỉ mang các cặp tên–giá trị, ví dụ `Cookie: theme=dark; lang=vi`. Nó **không gửi lại** `Max-Age`, `Secure`, `HttpOnly`, `Path` hoặc `SameSite`.
- Cookie không nằm trong JSON body. Một endpoint có thể gửi body JSON và `Set-Cookie` cùng lúc; cũng có thể trả HTTP 204 không có body mà vẫn đặt cookie qua header.
- `@CookieValue` đọc cookie từ request **hiện tại**, không đọc `Set-Cookie` đang được tạo cho response. Để đọc cookie vừa đặt, cần request tiếp theo của client, hoặc truyền giá trị theo luồng nghiệp vụ ngay tại server.
- Cookie được quản lý bởi client theo các ràng buộc scope, thời hạn, chính sách browser và người dùng. Header `Set-Cookie` xuất hiện trong `curl -i` chỉ chứng minh server đã gửi nó; **chưa chứng minh browser đã chấp nhận**.

### Phân biệt cookie với server-side session

Cookie `JSESSIONID` thường chứa một **định danh session**, còn dữ liệu session chính được lưu ở phía server (memory, Redis hoặc một session repository). Cookie `JSESSIONID` không tự chứa toàn bộ object `HttpSession`. Một session ID có thể hết hiệu lực ở server ngay cả khi trình duyệt còn cookie; ngược lại cookie session có thể biến mất khi browser kết thúc trong khi server chưa hết session timeout.

Không đặt password, dữ liệu bí mật dạng plaintext hoặc toàn bộ object người dùng vào cookie chỉ vì cookie nằm trong trình duyệt. Người dùng và môi trường client có nhiều cách quan sát/sửa cookie; một cookie có `HttpOnly` không phải kho dữ liệu bí mật ở phía server.

## 3. Các thuộc tính trong `Set-Cookie`

| Thuộc tính | Tác dụng | Lưu ý |
| --- | --- | --- |
| `name=value` | Định danh và giá trị cookie | Không tin là dữ liệu server chưa bị sửa; kiểm tra khi nhận |
| `Max-Age=604800` | Thời hạn theo **giây** tính từ lúc nhận; 604800 = 7 ngày | `Max-Age=0` làm cookie hết hạn ngay |
| `Expires=...` | Thời điểm hết hạn theo ngày GMT | Nếu có cả `Max-Age`, client hiện đại ưu tiên `Max-Age` |
| `Path=/api` | Giới hạn request path mà browser gửi cookie | Không phải ranh giới bảo mật cô lập các path |
| `Domain=example.com` | Cho phép cookie áp dụng cho domain và subdomain phù hợp | Bỏ `Domain` thường an toàn hơn: cookie host-only |
| `Secure` | Chỉ gửi qua kết nối bảo mật HTTPS (ngoại lệ localhost tùy browser) | Không tự mã hóa cookie đang lưu trên thiết bị |
| `HttpOnly` | Ngăn JavaScript `document.cookie` đọc/chỉnh cookie | Không chặn browser gửi cookie trong `fetch` hoặc form request |
| `SameSite=Strict` | Hạn chế cookie về các request cùng site | Mạnh hơn nhưng có thể ảnh hưởng luồng mở link từ site khác |
| `SameSite=Lax` | Cho phép cùng site và một số điều hướng top-level cross-site an toàn | Không thay thế CSRF token |
| `SameSite=None` | Cho phép cookie trong ngữ cảnh cross-site đủ điều kiện | **Cần `Secure`**; browser vẫn có thể chặn third-party cookie |
| `Partitioned` | Tùy chọn cookie có storage phân vùng trong browser hỗ trợ | Cần `Secure`; chính sách browser khác nhau |

Không phải trình duyệt nào cũng giữ cookie trong thời gian cam kết nếu người dùng xóa cookie, trình duyệt giới hạn dung lượng/số lượng, private mode kết thúc, hoặc chính sách tracking protection can thiệp. Thời hạn **không phải lời hứa lưu trữ tuyệt đối**.

### Một cookie không có `Domain` là gì?

Ví dụ server `api.example.com` gửi:

~~~http
Set-Cookie: pref=vi; Path=/; Secure; SameSite=Lax
~~~

Vì **không có `Domain`**, đây thường là **host-only cookie**: chỉ host `api.example.com` nhận cookie, không tự mở rộng tới `www.example.com`.

Nếu thêm `Domain=example.com`, cookie có thể được gửi cả tới `example.com` và các subdomain phù hợp. Domain quá rộng có thể làm tăng rủi ro một subdomain bị chiếm quyền hoặc không đáng tin cậy thao túng cookie. Không thể đặt `Domain` tùy ý thành domain khác không liên quan hoặc public suffix như `com`.

`Path` điều khiển việc trình duyệt gửi cookie theo đường dẫn, nhưng **không cô lập quyền đọc dữ liệu giữa các ứng dụng cùng origin** như một biện pháp bảo mật độc lập. Cần quản lý origin, domain, quyền ứng dụng và trust boundary hợp lý.

## 4. Cấu hình thời hạn: session cookie, `Max-Age` và `Expires`

### 4.1. Session cookie

Khi không có `Expires` và `Max-Age`, cookie thường được xem là **session cookie**, theo vòng đời browser session. Không giả định nó chắc chắn biến mất ngay khi đóng một tab: một số browser có chế độ restore session và phục hồi các session cookie. “Session cookie” cũng khác với “server-side session timeout”.

### 4.2. Persistent cookie

~~~http
Set-Cookie: theme=dark; Path=/; Max-Age=2592000; Secure; SameSite=Lax
~~~

`2592000` giây tương ứng 30 ngày (30 × 24 × 60 × 60). `Max-Age` là số giây tương đối, không phải millisecond. Nếu server ghi `Max-Age=30` để biểu diễn 30 ngày, cookie chỉ tồn tại khoảng 30 giây.

Với `Expires`, server chỉ định ngày giờ hết hạn; có thể xuất hiện sai lệch đồng hồ client/server. Nếu có cả hai, `Max-Age` có độ ưu tiên theo semantics cookie phổ biến. Spring `ResponseCookie` cung cấp `maxAge(Duration.ofDays(...))` để tránh nhầm đơn vị.

### 4.3. Xóa cookie

~~~http
Set-Cookie: theme=; Path=/; Max-Age=0; Secure; SameSite=Lax
~~~

`Max-Age=0` yêu cầu cookie hết hạn ngay. **Tên, `Path` và `Domain` scope phải tương thích với cookie cũ**. Nếu cookie cũ tạo `Path=/api/prefs` mà xóa bằng `Path=/`, browser có thể vẫn giữ cookie cũ vì đó là một identity khác.

Để thay/loại bỏ cookie một cách nhất quán, duy trì cùng cấu hình scope giữa lúc tạo và xóa. Cookie xóa không cần giữ nguyên value cũ, nhưng các thuộc tính liên quan bảo mật/scope nên nhất quán cho tính dễ hiểu. Nếu có nhiều cookie trùng tên ở các path hoặc domain khác nhau, cần xem xét từng cookie cụ thể.

### 4.4. Hết hạn cookie không đồng nghĩa logout bảo mật

Xóa cookie giúp browser không gửi session ID nữa, nhưng **logout đáng tin còn phải vô hiệu session/token phía server** khi hệ thống dùng session. Đối với session ID do Spring Security quản lý, nên sử dụng logout/session-management của Spring Security, không chỉ tự gửi `Set-Cookie: JSESSIONID=; Max-Age=0` và tưởng đã thu hồi quyền.

Một opaque session identifier tồn tại tại server có thể bị sử dụng lại nếu đã rò rỉ và chưa bị thu hồi, dù user xóa cookie từ một browser.

## 5. `Secure`: bảo vệ khi truyền cookie

`Secure` hướng dẫn browser chỉ gửi cookie qua `https:` (ngoại lệ thường gặp là `localhost` trong môi trường phát triển). Đây là thuộc tính cần thiết cho session/authentication cookie khi triển khai HTTPS.

~~~http
Set-Cookie: session_ref=abc123; Path=/; Secure; HttpOnly; SameSite=Lax
~~~

Nó giúp tránh browser gửi cookie nhạy cảm qua HTTP thường, nhưng **không**:
- Bảo vệ hoàn toàn trước XSS nếu ứng dụng vẫn có script injection.
- Mã hóa giá trị cookie khi lưu trên ổ đĩa client.
- Xác thực quyền truy cập hoặc ngăn replay của token bị lộ.
- Tự chuyển HTTP site thành HTTPS, hay tự sửa cấu hình reverse proxy.

Dùng HTTPS trên toàn ứng dụng, ưu tiên HSTS ở production khi thích hợp, bảo vệ traffic giữa reverse proxy/backend theo kiến trúc. Nếu app đứng sau load balancer TLS termination, cấu hình proxy và trusted forwarding đúng để framework biết request gốc là HTTPS; không tin header `X-Forwarded-Proto` từ client không đáng tin.

Nếu đang dùng `Secure=true` tại `http://localhost:8080`, kết quả browser có thể phụ thuộc chính sách localhost và browser. Ví dụ thực hành bên dưới kiểm tra **server gửi thuộc tính đúng** qua `curl -i`/MockMvc; để kiểm tra browser gửi cookie tự động, dùng HTTPS trong môi trường phù hợp.

## 6. `HttpOnly`: hạn chế JavaScript chạm cookie

Cookie có `HttpOnly` thường không xuất hiện trong `document.cookie` và không được sửa bằng JavaScript thường trong trang:

~~~http
Set-Cookie: session_ref=abc123; Path=/; Secure; HttpOnly; SameSite=Lax
~~~

Cơ chế này giảm một số rủi ro **đọc/đánh cắp session cookie qua XSS**, nhưng không loại bỏ XSS. Script độc hại chạy trong origin có thể:
- Gửi request `fetch` tới backend mà trình duyệt đính cookie HttpOnly đủ điều kiện.
- Đọc những thông tin mà UI/API cho phép script truy cập.
- Thực hiện thao tác bất hợp pháp trong phiên người dùng nếu server thiếu kiểm soát quyền/CSRF.

Với **session ID**, `HttpOnly=true` thường là lựa chọn mặc định mong muốn. Với **tùy chọn giao diện** mà client JavaScript thật sự phải đọc bằng `document.cookie`, có thể cân nhắc cookie không HttpOnly (vì không chứa bí mật) hoặc truyền giá trị qua HTML/API được phép, sau đó lưu ở cơ chế phù hợp. Không vô hiệu hóa HttpOnly trên authentication cookie chỉ để frontend “lấy token cho tiện”.

`HttpOnly` khác với CORS: CORS quy định JavaScript trang web khác có thể đọc response hay không, nhưng không phải cơ chế tổng quát chặn browser gửi cookie. Cookie và credential policy của browser cần được kiểm tra riêng.

## 7. `SameSite`: Strict, Lax, None

`SameSite` giới hạn khi browser gắn cookie vào request theo **site** của request so với site gây ra request. “Site” thường là **scheme + registrable domain** (schemeful same-site), không đồng nghĩa với **origin** (scheme + host + port). Ví dụ `https://app.example.com` và `https://api.example.com` có thể khác origin nhưng vẫn same-site; `http://example.com` và `https://example.com` có thể khác site theo schemeful rule.

| Giá trị | Request same-site | Điều hướng top-level cross-site GET | Cross-site fetch/form POST thông thường |
| --- | --- | --- | --- |
| `Strict` | Có, nếu scope/other rules khớp | Thường không gửi | Không gửi |
| `Lax` | Có | Thường gửi với safe top-level navigation | Thường không gửi |
| `None` | Cho phép theo scope | Có thể gửi | Có thể gửi nếu `Secure` và các chính sách browser cho phép |

Giải thích:

- **`Strict`**: phù hợp thao tác nhạy cảm khi UX chấp nhận việc cookie không đính vào request điều hướng từ website khác. Ví dụ người dùng mở link được gửi từ site khác có thể phải thực hiện một bước cùng site tiếp theo để phiên được dùng.
- **`Lax`**: cân bằng UX và một phần phòng chống CSRF; cho phép một số top-level navigation an toàn như GET. Không cho phép giả định mọi request GET đều vô hại: API phải bảo đảm **GET không tạo thay đổi trạng thái nguy hiểm**.
- **`None`**: không chặn theo SameSite, dùng cho trường hợp cần cookie trong một số ngữ cảnh cross-site (chẳng hạn embedded widget). Browser hiện đại **yêu cầu `Secure`** đi kèm; third-party cookie blocking/partitioning và privacy settings vẫn có thể ngăn cookie.
- **Bỏ thuộc tính**: nhiều browser áp dụng mặc định kiểu Lax, nhưng có khác biệt/ngoại lệ như grace period cho cookie mới tạo và thay đổi chính sách qua thời gian. Nếu ứng dụng có yêu cầu bảo mật rõ ràng, hãy **đặt SameSite tường minh**.

**Quan trọng:** `SameSite=Lax` không thay thế **CSRF protection**. Server cần CSRF token hoặc biện pháp kiểm soát request giả mạo phù hợp với authentication mechanism; Spring Security cung cấp CSRF protection khi được cấu hình. Cross-site top-level GET vẫn có thể mang cookie Lax, nên GET/HEAD phải được thiết kế theo safe semantics.

### Cross-origin `fetch` không hoàn toàn đồng nghĩa cross-site

Một frontend ở `https://app.example.com` gọi `https://api.example.com` là **cross-origin**, vì host khác. Nhưng trong nhiều cấu hình, hai origin này vẫn **same-site**. Khi browser xử lý cookie:
- `SameSite` liên quan **site**.
- `Domain`, `Path`, `Secure`, expiry liên quan scope cookie.
- `fetch` dùng `credentials` và CORS liên quan cách request gửi credentials và JavaScript được đọc response.
- Chính sách chặn cookie bên thứ ba còn phụ thuộc browser.

Vì vậy không nên đổi cookie sang `SameSite=None` chỉ vì thấy thông báo “CORS error”. Trước tiên cần xác định hai site có thật sự cross-site không và điểm chặn là credentials/CORS/cookie scope hay CSRF.

## 8. API Spring MVC tạo cookie: `ResponseCookie` và `ResponseEntity`

Trong **Spring Framework 6.x**, class `org.springframework.http.ResponseCookie` có builder để khai báo:
- `path(String)`, `domain(String)` khi thực sự cần.
- `maxAge(Duration)` hoặc `maxAge(long seconds)`.
- `secure(boolean)`, `httpOnly(boolean)`, `sameSite(String)`.

Ví dụ khai báo:

~~~java
ResponseCookie cookie = ResponseCookie.from("view_pref", "compact")
        .path("/api/prefs")
        .maxAge(Duration.ofDays(7))
        .secure(true)
        .httpOnly(true)
        .sameSite("Lax")
        .build();

String headerValue = cookie.toString();
~~~

Đây là **đoạn minh họa bên trong method**: cần import `ResponseCookie` và `Duration`. `toString()` tạo **giá trị** thích hợp cho header `Set-Cookie`, không phải một URL hay JSON body.

Để gửi header, dùng:

~~~java
return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .build();
~~~

Với method có kiểu trả về `ResponseEntity<Void>`, kết quả là **HTTP 204 No Content** kèm `Set-Cookie` và **không có body**. `ResponseEntity` là lựa chọn tiện lợi khi cần điều khiển status và headers cùng lúc.

Nếu gửi nhiều cookie, dùng **hai giá trị header** `Set-Cookie`, ví dụ:

~~~java
return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, first.toString())
        .header(HttpHeaders.SET_COOKIE, second.toString())
        .body("ok");
~~~

Đây là đoạn minh họa, cần có hai `ResponseCookie` là `first` và `second`; API `header` bổ sung các giá trị header. Không tự nối `first + "," + second` thành một chuỗi vì cú pháp `Set-Cookie` có các đặc điểm riêng, nhất là khi có `Expires`.

### Đọc cookie trong controller bằng `@CookieValue`

~~~java
@GetMapping("/current")
public String current(
        @CookieValue(name = "view_pref", required = false) String pref) {
    return pref == null ? "missing" : pref;
}
~~~

Đây là handler minh họa trong `@RestController`, cần import. `@CookieValue` tìm cookie theo tên trên **request**:
- Mặc định `required=true`; cookie bắt buộc thiếu có thể dẫn tới lỗi HTTP 400.
- `required=false` cho phép thiếu cookie và tham số `String` nhận `null`.
- `defaultValue="..." ` có thể dùng để đặt fallback, đồng thời ngụ ý không yêu cầu bắt buộc.
- Có thể lấy `jakarta.servlet.http.Cookie` khi cần object Cookie của Servlet, nhưng **request Cookie không cho bạn biết chắc chắn** thuộc tính `SameSite`/`HttpOnly` đã được browser lưu, vì browser không gửi những thuộc tính đó trong `Cookie` request header.

Dữ liệu cookie do client kiểm soát. Nếu cookie chứa lựa chọn chỉ nhận `compact` hoặc `comfortable`, hãy **validate allowlist** ở server; không tin cookie để xác định role, user ID hoặc quyền truy cập.

## 9. Ví dụ hoàn chỉnh Java 17+ / Spring Boot 3.x

Tạo project bằng Spring Initializr: Java 17+, Spring Boot 3.x, Maven, **Spring Web** và **Spring Boot Starter Test**. Package của project là `com.example.demo`. Bài chỉ sử dụng cookie tùy chọn giao diện, **không tạo authentication/session system**.

Cấu trúc:

~~~text
src/main/java/com/example/demo/DemoApplication.java
src/main/java/com/example/demo/PreferenceCookieController.java
src/test/java/com/example/demo/PreferenceCookieControllerTest.java
~~~

### 9.1. Lớp khởi động

**`src/main/java/com/example/demo/DemoApplication.java`**

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

### 9.2. Controller tạo, đọc, xóa cookie

**`src/main/java/com/example/demo/PreferenceCookieController.java`**

~~~java
package com.example.demo;

import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prefs")
public class PreferenceCookieController {

    private static final String COOKIE_NAME = "view_pref";
    private static final String COOKIE_PATH = "/api/prefs";

    @PostMapping("/compact")
    public ResponseEntity<Void> chooseCompact() {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "compact")
                .path(COOKIE_PATH)
                .maxAge(Duration.ofDays(7))
                .secure(true)
                .httpOnly(true)
                .sameSite("Lax")
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @GetMapping(value = "/current",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String current(
            @CookieValue(name = COOKIE_NAME, required = false)
            String value) {

        if (value == null) {
            return "view_pref=missing";
        }

        // Cookie do client điều khiển: không tin giá trị tùy ý.
        if (!value.equals("compact") && !value.equals("comfortable")) {
            return "view_pref=invalid";
        }

        return "view_pref=" + value;
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        ResponseCookie expired = ResponseCookie.from(COOKIE_NAME, "")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .secure(true)
                .httpOnly(true)
                .sameSite("Lax")
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expired.toString())
                .build();
    }
}
~~~

**Cách hoạt động:**

1. `POST /api/prefs/compact` gửi HTTP 204 và `Set-Cookie: view_pref=compact; ... Max-Age=604800; Secure; HttpOnly; SameSite=Lax`. Đây là cookie tồn tại tối đa khoảng 7 ngày theo hướng dẫn `Max-Age`.
2. `Path=/api/prefs` có nghĩa browser gửi cookie cho request hợp lệ thuộc đường dẫn này; nó không tự gửi cho mọi endpoint của website.
3. `Secure(true)` giữ semantics an toàn đối với production HTTPS. Với demo chạy `http://localhost:8080`, browser cụ thể có thể áp dụng exception localhost; test trực tiếp header không phụ thuộc việc browser quyết định lưu.
4. `HttpOnly(true)` cho thấy thuộc tính bảo vệ cookie khỏi các API truy cập cookie bằng JavaScript. Đây là **ví dụ cấu hình**; nếu UI JavaScript thực sự cần đọc một preference cookie không bí mật, thiết kế có thể dùng một cơ chế khác hoặc cân nhắc `HttpOnly=false` **chỉ cho cookie không chứa credential**.
5. `SameSite("Lax")` đặt chính sách gửi cookie tường minh. Nó không khiến cross-site request trở nên an toàn một cách tuyệt đối.
6. `GET /api/prefs/current` đọc `@CookieValue` và validate giá trị. Thiếu cookie trả text `view_pref=missing`; cookie hợp lệ trả `view_pref=compact` hoặc `view_pref=comfortable`.
7. `DELETE /api/prefs` gửi `Max-Age=0` với **cùng tên và Path** để yêu cầu xóa cookie. Xóa phía browser không có nghĩa vô hiệu hóa session server, nhưng ví dụ này chỉ là cookie preference, không dùng session.
8. Cả POST và DELETE dùng 204 không body; header `Set-Cookie` vẫn hợp lệ. Status không nhất thiết phải là 200 để browser xử lý cookie.

Trong hệ thống thực, nếu endpoint thay đổi state và authentication dùng cookie, cần có cơ chế phòng CSRF thích hợp. Ví dụ không thêm Spring Security để tập trung vào cookie mechanics.

### 9.3. Gọi bằng curl

Chạy:

~~~bash
./mvnw spring-boot:run
~~~

Trên Windows: `mvnw.cmd spring-boot:run`. Nếu không có Maven Wrapper, dùng `mvn spring-boot:run` sau khi cài Maven.

Gửi lệnh tạo cookie và quan sát header:

~~~bash
curl -i -X POST "http://localhost:8080/api/prefs/compact"
~~~

Bạn sẽ thấy status **204 No Content**, và một `Set-Cookie` tương đương:

~~~http
Set-Cookie: view_pref=compact; Path=/api/prefs; Max-Age=604800; Expires=...; Secure; HttpOnly; SameSite=Lax
~~~

`Expires` nếu được Spring tự bổ sung sẽ phụ thuộc thời điểm thực thi; dấu `...` ở đây chỉ là **minh họa**, không phải một giá trị date hợp lệ để copy vào response. Thứ tự attribute không phải API contract chính; kiểm tra những thuộc tính cần thiết.

`curl` trong ví dụ không tự bảo đảm cookie Secure được gửi trên HTTP ở các lệnh sau. Để **minh họa chính xác cách controller đọc `Cookie` request**, chủ động gửi header giả lập:

~~~bash
curl -i "http://localhost:8080/api/prefs/current"   -H "Cookie: view_pref=compact"
~~~

Response body kỳ vọng:

~~~text
view_pref=compact
~~~

Không gửi cookie:

~~~bash
curl -i "http://localhost:8080/api/prefs/current"
~~~

Body:

~~~text
view_pref=missing
~~~

Gửi một giá trị ngoài allowlist:

~~~bash
curl -i "http://localhost:8080/api/prefs/current"   -H "Cookie: view_pref=admin"
~~~

Body:

~~~text
view_pref=invalid
~~~

Yêu cầu xóa:

~~~bash
curl -i -X DELETE "http://localhost:8080/api/prefs"
~~~

Status kỳ vọng **204**, header `Set-Cookie` chứa `view_pref=; Path=/api/prefs; Max-Age=0` cùng các flag `Secure`, `HttpOnly`, `SameSite=Lax`. Trên trình duyệt, kiểm tra bằng DevTools **Application/Storage → Cookies** và **Network → Response Headers / Request Cookies**. Với `HttpOnly`, không đợi `document.cookie` đọc được cookie.

Các lệnh multiline dùng dấu `\` của Linux/macOS/Git Bash. Trên Windows PowerShell, dùng `curl.exe` với cú pháp quote/nối dòng đúng; một dòng thường ít gặp lỗi hơn.

## 10. Kiểm thử `Set-Cookie` và `@CookieValue` với MockMvc

Thêm dependency `spring-boot-starter-test` và tệp **`src/test/java/com/example/demo/PreferenceCookieControllerTest.java`**:

~~~java
package com.example.demo;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreferenceCookieController.class)
class PreferenceCookieControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void createCookieWithSevenDayExpiryAndSecurityFlags()
            throws Exception {

        MvcResult result = mockMvc.perform(
                        post("/api/prefs/compact"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn();

        String header = result.getResponse()
                .getHeader(HttpHeaders.SET_COOKIE);

        assertNotNull(header);
        assertTrue(header.contains("view_pref=compact"));
        assertTrue(header.contains("Path=/api/prefs"));
        assertTrue(header.contains("Max-Age=604800"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Lax"));
    }

    @Test
    void readCookieIfPresentAndHandleMissingCookie()
            throws Exception {

        mockMvc.perform(get("/api/prefs/current")
                        .cookie(new Cookie("view_pref", "compact")))
                .andExpect(status().isOk())
                .andExpect(content().string("view_pref=compact"));

        mockMvc.perform(get("/api/prefs/current"))
                .andExpect(status().isOk())
                .andExpect(content().string("view_pref=missing"));
    }

    @Test
    void refuseUnknownCookieValue() throws Exception {
        mockMvc.perform(get("/api/prefs/current")
                        .cookie(new Cookie("view_pref", "admin")))
                .andExpect(status().isOk())
                .andExpect(content().string("view_pref=invalid"));
    }

    @Test
    void clearCookieUsesMatchingPathAndZeroAge()
            throws Exception {

        MvcResult result = mockMvc.perform(delete("/api/prefs"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn();

        String header = result.getResponse()
                .getHeader(HttpHeaders.SET_COOKIE);

        assertNotNull(header);
        assertTrue(header.contains("view_pref="));
        assertTrue(header.contains("Path=/api/prefs"));
        assertTrue(header.contains("Max-Age=0"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Lax"));
    }
}
~~~

Chạy:

~~~bash
./mvnw test
~~~

Các test khẳng định **server phát header đúng** và `@CookieValue` bind giá trị request đúng. `MockMvc` **không mô phỏng toàn bộ trình duyệt**: nó không tự enforce cookie `Secure` trên HTTP, không thử third-party cookie blocking, không đánh giá CSRF policy của browser, cũng không bảo đảm `SameSite` đã được một browser cụ thể chấp nhận. Những đặc tính đó cần browser/integration tests trên HTTPS và ngữ cảnh cross-site thực sự.

Nếu ứng dụng sản phẩm có Spring Security, test POST/DELETE có thể yêu cầu CSRF token và authentication. Bổ sung `spring-security-test` rồi dùng `with(csrf())` theo chính sách của project, không tắt CSRF để qua test.

## 11. Session cookie do Spring Boot quản lý: cấu hình khác cookie tự tạo

Ứng dụng dùng Servlet session hoặc Spring Security có thể thấy cookie `JSESSIONID`. **Đừng thay đổi `JSESSIONID` thủ công trong controller như cách tạo `view_pref`** nếu cookie đó thuộc container/session management của framework.

Với **Spring Boot 3.x**, có thể cấu hình session cookie trong `src/main/resources/application.properties`:

~~~properties
server.servlet.session.cookie.name=JSESSIONID
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.same-site=lax
server.servlet.session.cookie.path=/
server.servlet.session.timeout=30m
~~~

Ý nghĩa:
- `http-only=true`, `secure=true`, `same-site=lax` điều khiển những thuộc tính mà container dùng để tạo session cookie.
- `path=/` mở rộng cookie cho các route trong host, phù hợp khi session được dùng toàn ứng dụng; có thể thu hẹp theo cấu trúc app.
- `server.servlet.session.timeout=30m` là **thời gian timeout session phía server**, không phải thời gian cookie tồn tại phía browser.
- Nếu muốn cookie tồn tại theo `Max-Age` xác định, Boot còn có `server.servlet.session.cookie.max-age` và các thuộc tính tương ứng. Cần xét với chính sách timeout/invalidation để tránh hiểu sai rằng cookie còn tức session còn.
- Khi chạy HTTPS thật, `Secure=true` là thiết lập phù hợp. Ở local HTTP, cookie `Secure` có thể gây khó kiểm thử tùy browser; dùng profile phát triển được kiểm soát nếu cần, không đưa cấu hình kém an toàn vào production.

Nếu ứng dụng không thực sự tạo session, nó không nhất thiết phát `JSESSIONID`. Việc đặt property **không tự tạo một session**.

### Cookie ngoài session: `CookieSameSiteSupplier` của Spring Boot

Spring Boot 3.x hỗ trợ `CookieSameSiteSupplier` đối với một số cookie thêm qua `HttpServletResponse` của servlet stack. Ví dụ minh họa:

~~~java
import org.springframework.boot.web.servlet.server.CookieSameSiteSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CookiePolicyConfig {

    @Bean
    public CookieSameSiteSupplier demoCookies() {
        return CookieSameSiteSupplier.ofLax()
                .whenHasNameMatching("myapp.*");
    }
}
~~~

Class trên là **ví dụ cấu hình bổ sung**, không cần thêm vào project controller ở phần 9. Nó có thể dùng để đặt SameSite cho các cookie tên khớp khi đi qua cơ chế `response.addCookie(...)` được Boot hỗ trợ. Đừng giả định supplier luôn viết lại các giá trị `Set-Cookie` đã được tự tạo trực tiếp bằng `ResponseEntity.header` hoặc mọi response từ proxy; kiểm tra đường xuất cookie cụ thể của hệ thống.

### `jakarta.servlet.http.Cookie` và `HttpServletResponse.addCookie`

Cách Servlet truyền thống:

~~~java
Cookie cookie = new Cookie("view_pref", "compact");
cookie.setPath("/api/prefs");
cookie.setMaxAge(604800);
cookie.setSecure(true);
cookie.setHttpOnly(true);
response.addCookie(cookie);
~~~

Đây là đoạn minh họa trong method có `HttpServletResponse response`, với import `jakarta.servlet.http.Cookie`. Trong Servlet API phù hợp, một số implementation còn hỗ trợ `setAttribute("SameSite", "Lax")`; khả năng serialize `SameSite` phụ thuộc Servlet version/container/Boot policy. Khi cần điều khiển rõ `SameSite` một cách portable trong Spring MVC 6.x, `ResponseCookie` và `Set-Cookie` header thường dễ đọc và dễ test hơn.

**Đừng dùng `javax.servlet.http.Cookie` trong Spring Boot 3.x**, vì nhánh này dựa trên **Jakarta Servlet** (`jakarta.servlet.*`).

## 12. Session và bảo mật: CSRF, XSS, session fixation

### CSRF với cookie authentication

Nếu website dùng session cookie để xác thực, browser sẽ tự gửi cookie ở những request thỏa điều kiện scope và chính sách cookie. Attacker có thể tìm cách khiến browser của nạn nhân gửi một request làm thay đổi dữ liệu. **SameSite=Lax/Strict giảm một số đường tấn công CSRF nhưng không thay thế CSRF protection**.

Các nguyên tắc:
1. HTTP GET/HEAD không nên thực hiện thay đổi trạng thái nguy hiểm.
2. Đối với state-changing POST/PUT/PATCH/DELETE gắn với cookie authentication, dùng CSRF token/kiểm soát phù hợp Spring Security và threat model.
3. Kiểm tra Origin/Referer và Content-Type khi thích hợp là biện pháp bổ sung, không thay token khi ứng dụng thực sự cần.
4. Không coi CORS là “tường chống CSRF”: một request có thể được gửi mà không cần JavaScript đọc response, nhất là qua HTML form/cơ chế điều hướng.

### XSS và `HttpOnly`

`HttpOnly` ngăn script đọc session cookie qua `document.cookie`, nhưng script bị injection vẫn có thể tương tác với website trong quyền của user. Cần encode output, tránh unsafe DOM sinks, có CSP phù hợp và giảm phạm vi cookie.

### Session fixation và rotation

Sau khi người dùng đăng nhập, cần áp dụng cơ chế **session fixation protection** như thay đổi session ID; Spring Security có session management tương ứng. Không tạo session ID dự đoán được hoặc giữ lại một ID do attacker kiểm soát. Khi logout, hủy session/token phía server và xóa cookie liên quan theo cơ chế framework.

### Prefix `__Secure-` và `__Host-`

Một số browser có quy tắc kiểm tra theo tên cookie:

~~~http
Set-Cookie: __Secure-session=abc; Secure; HttpOnly; Path=/; SameSite=Lax
Set-Cookie: __Host-session=abc; Secure; HttpOnly; Path=/; SameSite=Lax
~~~

- `__Secure-`: yêu cầu `Secure` và nguồn thiết lập HTTPS trong browser hỗ trợ.
- `__Host-`: ngoài `Secure`, yêu cầu `Path=/` và **không có `Domain`**, giúp giới hạn cookie ở host.
- Prefix là lớp phòng vệ bổ sung, **không thay authorization/session invalidation**.
- Tên và cookie value ở trên là placeholder mô tả header, không phải session identifier nên dùng thật.

## 13. Cross-origin frontend, CORS và third-party cookie

Ví dụ một ứng dụng frontend tại `https://app.example.com` gọi API `https://api.example.com`:

~~~javascript
const response = await fetch("https://api.example.com/api/prefs/current", {
  method: "GET",
  credentials: "include"
});

if (!response.ok) {
  throw new Error("HTTP " + response.status);
}

console.log(await response.text());
~~~

`credentials: "include"` cho phép trình duyệt gửi credentials với cross-origin request theo điều kiện; **không ép** gửi cookie bị `SameSite`, `Secure`, `Domain`, `Path` hoặc browser privacy policy chặn. Để JavaScript đọc response cross-origin, backend cần CORS thích hợp, ví dụ `Access-Control-Allow-Origin` đúng origin cụ thể và `Access-Control-Allow-Credentials: true` khi cho phép credentials. Không dùng `Access-Control-Allow-Origin: *` kết hợp với credentialed requests.

Nếu frontend và API thuộc **hai site khác nhau**, có thể cần `SameSite=None; Secure` cho cookie cross-site, nhưng **nhiều browser chặn third-party cookies** hoặc yêu cầu phương án khác. Không đổi sang `None` một cách máy móc: xác định trước liệu hai origin có cùng registrable domain/scheme (same-site) hay không, và có thể thiết kế lại luồng với same-site backend hoặc token-based API khi thích hợp.

Một response `Set-Cookie` có thể hiện trong Network panel nhưng bị browser đánh dấu “blocked” do chính sách `SameSite`, `Secure`, domain, browser privacy, prefix, cookie size hoặc CORS/credentials. Hãy đọc lý do block trong DevTools, đừng chỉ kiểm tra status 200.

JavaScript frontend **không đọc trực tiếp header `Set-Cookie` bằng `response.headers.get("set-cookie")`** trong browser như một header thông thường: đây là **forbidden response-header name** đối với Fetch exposure. Browser xử lý Set-Cookie qua cookie store theo policy.

## 14. Nhiều cookie, giới hạn kích thước và chính sách lưu

Cookies được gửi trong các request phù hợp, vì vậy dữ liệu lớn trong cookie gây overhead mạng và có thể bị browser/proxy từ chối. Trình duyệt có giới hạn kích thước cho từng cookie và số cookie theo domain, nhưng chi tiết khác nhau theo browser/version; tránh lưu JSON object lớn, danh sách quyền hoặc file trong cookie.

Cần:
- Chỉ lưu giá trị nhỏ và cần thiết; dùng server-side state nếu dữ liệu phức tạp.
- Đặt tên rõ ràng, tránh nhiều cookie cùng tên ở các path/domain làm server đọc nhầm giá trị.
- Dùng `Path` hẹp khi thích hợp; tránh `Domain` quá rộng.
- Chọn `Max-Age` đủ ngắn với dữ liệu nhạy cảm và không vượt quá thời hạn phiên phía server.
- Không ghi full `Cookie` hoặc `Set-Cookie` chứa token vào application logs, analytics hay tracing không được kiểm soát.
- Với authentication token, cân nhắc rotation/revocation, ký hoặc mã hóa giá trị khi appropriate; cookie flag **không chứng minh tính toàn vẹn hay chống sửa đổi**.
- Không dựa vào thứ tự cookie khi nhiều cookie cùng tên trong request; server nên thiết kế tránh trùng tên và scope mơ hồ.

Các trình duyệt ngày càng có các tính năng như **Partitioned cookies (CHIPS)** trong những bối cảnh nhúng. Spring Framework 6.2 hỗ trợ `ResponseCookie.ResponseCookieBuilder#partitioned(boolean)`; nhưng nếu ứng dụng đang dùng phiên bản 6.0/6.1, cần đối chiếu API trước khi sử dụng. Đối với cookie partitioned, browser hỗ trợ yêu cầu `Secure` và có các quy tắc partitioning riêng. Bài này tập trung `SameSite` cơ bản, không coi CHIPS là thay thế kiểm soát CSRF.

## 15. Các lỗi phổ biến và cách xử lý

| Hiện tượng | Nguyên nhân thường gặp | Hướng xử lý |
| --- | --- | --- |
| Browser không lưu cookie | `Secure` trên HTTP, SameSite None thiếu Secure, Domain không hợp lệ, third-party policy | Kiểm tra DevTools blocked reason và HTTPS |
| `@CookieValue` nhận null | Browser không gửi cookie do Path/Domain/Secure/expiry/SameSite | Kiểm tra `Cookie` request header, không chỉ `Set-Cookie` response |
| Xóa cookie nhưng vẫn thấy lại | Sai Path/Domain hoặc có cookie cùng tên scope khác | So lại identity lúc tạo/xóa; kiểm tra từng cookie |
| Cookie “7 ngày” chỉ sống 7 giây | Truyền nhầm `maxAge` đơn vị hoặc số giây | Dùng `Duration.ofDays(7)` |
| Cookie bị mất sau khi browser restart | Không có Max-Age/Expires, hoặc browser privacy policy | Đặt thời hạn phù hợp và chấp nhận việc user có thể xóa |
| Session ID còn cookie nhưng server báo hết phiên | Session server timeout/invalidation độc lập | Phân biệt session timeout với cookie expiry |
| JS không đọc `document.cookie` | Cookie có `HttpOnly` | Đúng với session cookie; không tắt nếu là credential |
| Cross-site embed không có cookie | SameSite Lax/Strict, third-party cookie policy, credentials/CORS | Xác minh site/origin và thiết kế authentication phù hợp |
| `SameSite=None` không hoạt động | Thiếu Secure, dùng HTTP, browser chặn tracking | Bật HTTPS và xác minh chính sách trình duyệt |
| API dùng cookie nhưng vẫn có CSRF | Chỉ dựa vào SameSite hoặc endpoint GET có side effects | Bảo vệ CSRF và phương thức HTTP đúng semantics |
| Session cookie không đổi theo property | Cookie do thành phần khác tạo, config không thuộc runtime | Kiểm tra `JSESSIONID` thuộc container hay Spring Session, profile/config |
| Có hai header Set-Cookie bị gộp | Proxy/framework tùy chỉnh combine headers không đúng | Gửi từng Set-Cookie header riêng |
| Thông tin nhạy cảm lộ ở log | Logging trực tiếp Cookie/Set-Cookie headers | Redact credentials và áp dụng log policy |

### Debug có hệ thống

1. **Response:** có thực sự gửi `Set-Cookie` không? Tên/giá trị/Path/Domain/Max-Age/flags là gì?
2. **Browser storage:** cookie được chấp nhận hay bị block? Nguyên nhân browser nêu là gì?
3. **Request tiếp theo:** `Cookie` header có cặp tên–giá trị mong đợi không?
4. **Spring controller:** `@CookieValue` có đúng tên, `required`, conversion không?
5. **Session backend:** nếu là session cookie, session ID còn hợp lệ không? Logout/rotation/timeout đã diễn ra chưa?
6. **Cross-origin:** fetch credentials, CORS, SameSite, schemeful site và third-party policy có phù hợp không?
7. **Security:** kiểm tra CSRF/XSS/session management; không tăng phạm vi cookie bừa bãi chỉ để “sửa lỗi không gửi”.

## 16. Tự kiểm tra kiến thức

**Câu 1. `Set-Cookie` và `Cookie` khác nhau thế nào?**

`Set-Cookie` là response header hướng dẫn browser lưu hoặc cập nhật cookie cùng các attributes. `Cookie` là request header chỉ gửi lại cặp tên–giá trị của những cookie đủ điều kiện.

**Câu 2. `HttpOnly=true` có ngăn browser tự gửi cookie trong `fetch` không?**

Không. `HttpOnly` hạn chế JavaScript trực tiếp đọc/chỉnh cookie; browser vẫn có thể đính cookie vào request hợp lệ, tùy `credentials`, SameSite, Secure, scope và browser policy.

**Câu 3. Vì sao `SameSite=None` cần `Secure`?**

Browser hiện đại yêu cầu kết hợp này để cho phép cookie trong các ngữ cảnh cross-site được phép. Dù đủ hai thuộc tính, browser vẫn có thể chặn third-party cookie.

**Câu 4. Cookie được đặt `Max-Age=3600` tồn tại khoảng bao lâu?**

Khoảng một giờ, vì đơn vị là **giây**. Client vẫn có thể xóa sớm. Không nhầm với `server.servlet.session.timeout` của server-side session.

**Câu 5. Vì sao chỉ xóa `JSESSIONID` cookie chưa chắc là logout an toàn?**

Vì session/token còn cần được vô hiệu hóa phía server; session ID đã rò rỉ có thể vẫn có hiệu lực. Sử dụng Spring Security logout/session management.

**Câu 6. `SameSite=Lax` có thay thế CSRF token không?**

Không. Nó hạn chế một số cross-site request nhưng không loại bỏ mọi tình huống giả mạo hoặc sai thiết kế phương thức HTTP.

**Câu 7. Muốn xóa cookie có `Path=/api/prefs` thì chỉ cần `Max-Age=0; Path=/`?**

Không chắc. Xóa cookie cần cùng tên và phạm vi path/domain tương thích với cookie gốc. Nên đặt lại đúng `Path=/api/prefs`.

**Câu 8. `Secure` có phải là mã hóa giá trị cookie ở ổ cứng không?**

Không. Nó giới hạn truyền qua HTTPS trong browser hỗ trợ, không bảo đảm dữ liệu lưu client được mã hóa và không thay server-side authorization.

## 17. Checklist triển khai production

1. Phân loại rõ cookie **session/authentication** và cookie **preference**. Không tự viết session management nếu Spring Security đã sở hữu việc đó.
2. Chọn `Max-Age`/session timeout theo nghiệp vụ; hiểu cookie expiry và server-side session expiry khác nhau.
3. Với cookie nhạy cảm, sử dụng HTTPS và `Secure`, `HttpOnly`, `SameSite=Lax` hoặc `Strict` nếu phù hợp luồng; không chọn `None` nếu không cần cross-site.
4. Bỏ `Domain` nếu không cần subdomain share; đặt `Path` hạn chế theo nhu cầu. Cân nhắc prefix `__Host-`/ `__Secure-` trong môi trường HTTPS.
5. Luôn validate cookie khi nhận; không sử dụng dữ liệu cookie client kiểm soát để cấp quyền.
6. Khi xóa, dùng đúng tên, domain, path và `Max-Age=0`; nếu là authentication session, vô hiệu phiên phía server.
7. Kiểm thử cả HTTP response `Set-Cookie` và request `Cookie`, URL path khác nhau, expiry, CSRF/CORS và browser acceptance trong HTTPS thực tế.
8. Kiểm soát logging, số lượng và dung lượng cookie; không đưa credential vào URL hoặc log.
9. Nếu triển khai cross-site iframe/SPA, phân biệt **same-site** với **same-origin**, kiểm tra policy bên thứ ba và CORS credential, không sửa bằng cách tắt toàn bộ ràng buộc bảo mật.
10. Đối với session cookie, cấu hình qua `server.servlet.session.cookie.*` và Spring Security theo đúng chủ sở hữu cookie, không sao chép code tạo cookie preference để thay `JSESSIONID`.

## Tài liệu chính thức

- [Spring Framework 6.2 — `ResponseCookie.ResponseCookieBuilder` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.17/javadoc-api/org/springframework/http/ResponseCookie.ResponseCookieBuilder.html)
- [Spring Framework — `@CookieValue` trong Spring MVC](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/cookievalue.html)
- [Spring Boot 3.5 — Servlet Web Applications / SameSite Cookies](https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html)
- [Spring Boot 3.5 — Application Properties (session cookie)](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html)
- [MDN — Set-Cookie HTTP header](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie)
- [MDN — Using HTTP cookies](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Cookies)
- [MDN — Secure cookie configuration](https://developer.mozilla.org/en-US/docs/Web/Security/Practical_implementation_guides/Cookies)
- [OWASP — Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [OWASP — Cross-Site Request Forgery Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="session-localization-003">HttpSession, tạo/hủy phiên và giới hạn khi triển khai nhiều máy chủ</a>

<details>
<summary>Xem chi tiết</summary>

# HttpSession: tạo, hủy phiên và giới hạn khi triển khai nhiều máy chủ

## 1. Tại sao một ứng dụng web cần session?

HTTP về bản chất xử lý những request riêng biệt: một request không tự chứa bộ nhớ của request trước. Tuy vậy, ứng dụng đôi khi cần duy trì **trạng thái ngắn hạn của một trình duyệt** qua nhiều lần gọi, ví dụ giỏ hàng, các bước điền biểu mẫu, tùy chọn giao diện hoặc thông tin liên quan phiên đăng nhập. Trong Java Servlet stack, `jakarta.servlet.http.HttpSession` là API để liên kết dữ liệu phía server với một session identifier được client mang theo.

Ví dụ trình tự:

~~~text
Client                         Load balancer               Server
  | GET /catalog                    |                          |
  |-------------------------------->|------------------------->|
  |<------------------------------- HTTP 200, chưa cần session-|
  |
  | POST /api/demo-session/start     |                          |
  |-------------------------------->|------------------------->| getSession(true)
  |                                  |                          | setAttribute(...)
  |<------------------------------- Set-Cookie: JSESSIONID=... |
  |
  | GET /api/demo-session + Cookie   |                          |
  |-------------------------------->|------------------------->| getSession(false)
  |<------------------------------- dữ liệu đã lưu ------------|
  |
  | DELETE /api/demo-session         |                          |
  |-------------------------------->|------------------------->| invalidate()
  |<------------------------------- HTTP 204 ------------------|
~~~

Tên `JSESSIONID` trong sơ đồ là cookie session phổ biến của Servlet container. Nếu sử dụng Spring Session, tên mặc định có thể là `SESSION`; tên cuối cùng có thể được tùy biến. **Cookie thông thường mang ID để tra cứu; session attributes được giữ ở server/store, không tự được nhúng vào cookie.** Đừng nhầm HttpSession với JWT tự chứa claims hoặc cookie lưu toàn bộ dữ liệu.

Bài học giúp bạn:
- Hiểu lifecycle session: chưa tồn tại, tạo, đọc/ghi attributes, timeout, invalidate và tạo phiên mới.
- Phân biệt `getSession()` / `getSession(true)` với `getSession(false)`, và cách tránh tạo phiên ngoài ý muốn.
- Hiểu session ID/cookie, inactivity timeout, logout, session fixation và các chính sách bảo mật.
- Thực hành Java 17+ Spring Boot MVC có controller/test và hai request dùng chung một session.
- Đánh giá việc dùng session cục bộ khi load balancing nhiều node, sticky sessions và shared session store qua Spring Session + Redis.
- Phân biệt session store dùng chung với cơ sở dữ liệu nghiệp vụ, độ bền/nhất quán và các giới hạn khi scale.

**Phạm vi phiên bản:** ví dụ Servlet stack `jakarta.servlet`, Java 17+ và Spring Boot 3.3.x / Spring Framework 6.1.x. Ý nghĩa Servlet API vẫn áp dụng trong các phiên bản hiện đại. Khi dùng Spring Security hoặc Spring Session cần đối chiếu dependency/configuration của phiên bản đang chạy.

## 2. HttpSession là gì và không phải là gì?

`HttpSession` đại diện một phiên logic giữa ứng dụng web với một client trong khoảng thời gian hữu hạn. Server/container tạo session ID, lưu các attribute theo khóa String và có thể sử dụng cookie hoặc URL rewriting để kết nối request tiếp theo với cùng session.

| Thành phần | Lưu ở đâu? | Chức năng |
| --- | --- | --- |
| `HttpSession` và attributes | Servlet container hoặc kho session thay thế | Trạng thái gắn với phiên |
| Session ID | Được server phát sinh, trình duyệt thường gửi qua cookie | Khóa tra cứu phiên |
| `JSESSIONID` | Cookie thường gặp ở container Servlet | Chuyển ID của container session |
| `SESSION` | Cookie thường gặp khi Spring Session sử dụng default cookie resolver | Chuyển ID của Spring Session |
| `HttpServletRequest` | Một request cụ thể | Cho phép tra cứu/tạo phiên và thông tin cookie |
| `SecurityContext` | Ngữ cảnh xác thực do Spring Security quản lý | Không tương đương toàn bộ session, cách lưu tùy authentication/security configuration |

**Session không phải cơ chế xác thực tự động.** Chỉ gọi `getSession()` không làm user được đăng nhập hoặc phân quyền. Một session có thể là anonymous, và các attribute bất kỳ bạn đặt vào session không thay thế quy trình authentication, CSRF protection hay authorization.

Một browser cũng có thể chứa nhiều cookie. Server chọn đúng cookie theo domain, path, secure flags và cấu hình. Không gửi session ID vào URL hay query string trong API bình thường; URL thường đi vào log, lịch sử browser và referrer, nên tăng nguy cơ lộ định danh phiên.

### 2.1. Lifecycle tổng quát

~~~text
                   HTTP request không có session hợp lệ
                                  |
                         getSession(false)?
                          /             \
                       null         getSession(true)
                                       |
                                       v
                            NEW session + generated ID
                                       |
                               setAttribute(...)
                                       |
                                       v
                            ACTIVE, client gửi cookie
                                 /              \
                       Không hoạt động         invalidate()
                       quá timeout                 |
                            |                      v
                            v                    INVALID
                          EXPIRED                không dùng lại
                              \                    /
                               \                  /
                            Request tiếp theo:
                       không tìm được phiên cũ
                    tạo phiên mới chỉ khi có yêu cầu
~~~

Timeout thường dựa trên **khoảng không hoạt động (inactivity)** giữa các truy cập từ client, không phải một thời hạn tuyệt đối tính từ thời điểm login. Server có thể có cleanup bất đồng bộ, vì thế “hết hạn đúng một giây cố định” không phải cam kết để dựa vào giao diện. Với các session store phân tán, thời điểm key bị xóa và thời điểm lần đọc tiếp theo phát hiện expired có thể khác nhau.

## 3. Các phương thức Servlet quan trọng

| Method | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `request.getSession()` | Trả session hiện tại, hoặc **tạo mới** nếu chưa có | Có thể phát sinh state/cookie ngoài ý muốn |
| `request.getSession(true)` | Tương đương hành vi tạo khi thiếu | Dùng khi thật sự muốn bắt đầu session |
| `request.getSession(false)` | Trả session hiện tại nếu tồn tại/hợp lệ, **không tự tạo** | Dùng ở endpoint chỉ đọc hoặc logout |
| `session.setAttribute(key,value)` | Lưu hoặc thay một attribute | Dữ liệu cần nhỏ, ổn định, phù hợp serializer |
| `session.getAttribute(key)` | Đọc attribute hoặc null | Cast/kiểm kiểu và khả năng thiếu |
| `session.removeAttribute(key)` | Xóa riêng một attribute | Không hủy toàn bộ session |
| `session.invalidate()` | Hủy session và unbind attributes | Không sử dụng object session đó sau invalidate |
| `session.getId()` | Đọc session ID | Tránh log/trả client như data thông thường |
| `session.getCreationTime()` | Thời điểm tạo | Không phải authentication timestamp |
| `session.getLastAccessedTime()` | Thời điểm truy cập liên quan request | Không phải thời điểm gọi setAttribute gần nhất |
| `session.setMaxInactiveInterval(seconds)` | Thay timeout không hoạt động cho phiên này | Giá trị `<= 0` thường có nghĩa không timeout; tránh trong production |
| `session.getMaxInactiveInterval()` | Đọc timeout hiện có | Đơn vị là **giây**, khác duration trong application config |
| `request.changeSessionId()` | Đổi ID của session hiện tại, giữ các attributes | Dùng đúng điểm xác thực thành công khi cần chống fixation |
| `request.isRequestedSessionIdValid()` | Kiểm ID từ request có nhận diện session hợp lệ không | Không đồng nghĩa user đã authenticated |

`request.getSession(false)` rất quan trọng. Một endpoint trạng thái “chưa có giỏ hàng/phiên” không nên tự tạo session chỉ vì controller khai báo `HttpSession` rồi gọi một API khác. Khi tiêm trực tiếp `HttpSession` vào tham số handler, Spring MVC/Servlet thường có thể tạo phiên nếu chưa có; muốn tuyệt đối tránh, nhận `HttpServletRequest` và gọi `getSession(false)` rõ ràng.

Sau `invalidate()`, nhiều thao tác trên object `HttpSession` cũ sẽ ném `IllegalStateException`. Không giữ tham chiếu session trong singleton service hoặc thread background; lấy phiên theo context của request khi cần.

### 3.1. Khác nhau giữa attribute và hủy phiên

~~~java
HttpSession session = request.getSession(true);
session.setAttribute("theme", "dark");
session.setAttribute("cartId", 42L);

// Xóa một khóa, session vẫn có thể tồn tại:
session.removeAttribute("theme");

// Hủy mọi attribute/phiên:
session.invalidate();
~~~

Đây là **fragment** cần đặt trong method có biến `HttpServletRequest request`. Không dùng `removeAttribute("user")` làm logout mặc định khi Spring Security còn lưu trạng thái xác thực ở nơi khác; đăng xuất cần dùng security/logout flow đã cấu hình.

### 3.2. Không dùng HttpSession làm bộ nhớ toàn cục

Một `@Service` singleton có thể phục vụ nhiều user; biến instance mutable của service là dữ liệu chia sẻ không đúng với session scope. `HttpSession` là trạng thái của một client, nhưng **cũng không tự đảm bảo thread safety**: hai request đồng thời từ cùng browser có thể đọc/ghi cùng session attributes.

Ví dụ sai:

~~~java
// MINH HỌA SAI: service singleton không lưu cart riêng từng client.
@Service
class WrongCartService {
    private final List<String> items = new ArrayList<>();
    public void add(String item) { items.add(item); }
}
~~~

Fragment trên thiếu imports và không dành để copy chạy riêng. Nó minh họa vấn đề dữ liệu toàn cục, **không phải ví dụ chính của bài**.

Ngay cả khi thay `items` bằng `session.getAttribute("cart")` rồi thêm phần tử vào một `ArrayList` mutable, vẫn có race condition và rủi ro trong distributed session store. Với giỏ hàng thật, cân nhắc lưu trong database theo user/cart ID hoặc cập nhật transactional/atomic ở backend.

## 4. Session ID, cookie và các thuộc tính bảo mật

HTTP response lần đầu tạo session thường có `Set-Cookie`, các request kế tiếp có `Cookie` tương ứng. Browser tự lưu và gửi cookie khi domain/path/security conditions phù hợp.

~~~http
HTTP/1.1 200 OK
Set-Cookie: JSESSIONID=<opaque-id>; Path=/; HttpOnly; Secure; SameSite=Lax
~~~

Đây là **ví dụ về một cookie đã cấu hình bảo mật trên HTTPS**, không phải header mặc định trong mọi môi trường. `<opaque-id>` phải được server tạo ngẫu nhiên, không lấy từ username hoặc timestamp dễ đoán.

- **`HttpOnly`:** JavaScript trong browser không trực tiếp đọc cookie, giảm một số đường đánh cắp qua script; không thay thế phòng chống XSS.
- **`Secure`:** cookie chỉ được gửi qua HTTPS; nếu bật `Secure` khi thử `http://localhost` bằng trình duyệt/curl, tùy client có thể không gửi lại cookie.
- **`SameSite=Lax/Strict/None`:** giúp hạn chế một số ngữ cảnh cross-site; `SameSite=None` yêu cầu `Secure` ở browser hiện đại. Không coi SameSite là thay thế CSRF protection hoàn chỉnh.
- **`Path`, `Domain`:** xác định nơi cookie được gửi; cấu hình sai có thể làm cookie không quay lại endpoint khác.
- **`Max-Age` / `Expires` của cookie:** mô tả thời gian lưu cookie phía client; **khác** `HttpSession` server-side inactivity timeout. Cookie còn tồn tại không đảm bảo session ở server còn hợp lệ.

Không trả session ID trong body debug hoặc access logs. Reverse proxy phải truyền HTTPS/forwarded headers đúng và được tin cậy có kiểm soát, nhất là khi quyết định cookie Secure, URL/redirect và origin validation.

### 4.1. Session fixation: đổi ID sau xác thực

Nếu attacker kiểm soát hoặc biết một ID phiên trước login, rồi nạn nhân login mà hệ thống tiếp tục dùng ID đó, attacker có thể lợi dụng (*session fixation*). Với Servlet 3.1+ có `request.changeSessionId()` để tạo ID mới cho **session hiện tại** mà vẫn giữ attributes cần thiết.

~~~java
// MINH HỌA trong authentication flow riêng do ứng dụng tự sở hữu:
HttpSession existing = request.getSession(false);
if (existing != null && authenticationSucceeded) {
    request.changeSessionId();
}
~~~

Fragment không độc lập: `authenticationSucceeded` là kết quả đã được auth service xác minh, không do client cung cấp. Nếu ứng dụng dùng **Spring Security**, tránh tự xoay ID hoặc tự xóa security context một cách tùy ý: Spring Security thường áp dụng session fixation protection khi login. Với Servlet hiện đại, chiến lược mặc định thường là `changeSessionId`; có `newSession`/`migrateSession` khi có yêu cầu cụ thể. Kiểm tra policy thực tế bằng integration test.

Đổi ID không tự tước quyền trên tất cả những credential khác; logout và thu hồi token, remember-me, refresh token, SSO có logic riêng. Nên dựa vào Spring Security `SecurityFilterChain` và configured logout handlers để làm việc đó.

## 5. Timeout và hủy phiên: điều cần phân biệt

### 5.1. Timeout bất hoạt

`session.setMaxInactiveInterval(1800)` có nghĩa giới hạn không hoạt động khoảng **1800 giây (30 phút)**. Mỗi request liên kết với session được container/store quan sát có thể cập nhật mốc truy cập theo định nghĩa tương ứng. `getLastAccessedTime()` không đơn giản bằng thời điểm gần nhất một lập trình viên gọi `setAttribute()`.

Trong Spring Boot Servlet app có thể dùng:

~~~properties
server.servlet.session.timeout=30m
~~~

Nếu có Spring Session, tham số `spring.session.timeout=30m` có thể kiểm soát timeout của repository; khi không được thiết lập, auto-configuration Servlet của Spring Boot lấy từ `server.servlet.session.timeout`. Đừng đặt cả hai rồi giả định precedence dựa trên tên; xem dependency-managed version và xác minh giá trị runtime qua `getMaxInactiveInterval()`.

**Không dùng negative timeout như một cách “giữ user đăng nhập vĩnh viễn”.** Nó làm tăng rủi ro session bị đánh cắp hoặc dữ liệu bị giữ không giới hạn. Nếu sản phẩm cần “remember me”, hãy dùng cơ chế xác thực thích hợp (token/cookie lifecycle, rotation, revocation), không phải session vô hạn.

### 5.2. Logout và session invalidation

~~~java
HttpSession existing = request.getSession(false);
if (existing != null) {
    existing.invalidate();
}
~~~

Dùng `getSession(false)` tránh tạo phiên mới chỉ để hủy. Request tiếp theo mang cookie cũ sẽ không tìm được phiên cũ nếu việc invalidate được lưu hiệu quả trong kho phiên. Tuy nhiên, với **các request đã bắt đầu chạy song song**, invalidation không bảo đảm thu hồi ngay mọi hành động đang thực hiện; cần kiểm tra authorization ở ranh giới nghiệp vụ và chọn semantics phù hợp.

Trong ứng dụng có Spring Security, hãy dùng logout endpoint/handler do Spring Security quản lý: nó có thể xóa security context, invalidate HttpSession và dọn cookie tùy cấu hình. Demo bên dưới không có Spring Security nên chỉ thể hiện thao tác Servlet, **không được xem là triển khai login/logout an toàn**.

### 5.3. Session events và dọn dẹp

Servlet có `HttpSessionListener`, `HttpSessionAttributeListener` và `HttpSessionIdListener`, dùng khi cần quan sát tạo/hủy session hoặc đổi ID. Nếu dùng Spring Session Redis, event `SessionCreatedEvent`/`SessionDeletedEvent`/`SessionExpiredEvent` phụ thuộc repository/configuration và Redis keyspace notifications; **không hứa callback expiry đúng thời điểm từng giây hoặc chạy chắc chắn trong mọi failover**.

Không gắn logic nghiệp vụ quan trọng như hoàn tiền, hủy đơn hàng hay khóa vé **chỉ** vào session-expired event. Những công việc đó cần transaction, job/reconciliation và nguồn dữ liệu bền vững độc lập với vòng đời phiên.

## 6. Ví dụ chạy được: tạo, đọc và hủy phiên qua Spring MVC

Ví dụ sử dụng **Java 17, Spring Boot 3.3.5** và `spring-boot-starter-web`. Không có Spring Security và không dùng database để tập trung vào `HttpSession`. Giá trị `nickname` chỉ là chuỗi demo, **không được tin như danh tính đã xác thực**.

Cấu trúc:

~~~text
session-demo/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/sessiondemo/
    │   ├── SessionDemoApplication.java
    │   └── web/DemoSessionController.java
    └── test/java/com/example/sessiondemo/web/
        └── DemoSessionControllerTest.java
~~~

### 6.1. Gradle project

`settings.gradle`:

~~~groovy
rootProject.name = 'session-demo'
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

Có thể thêm `src/main/resources/application.properties`:

~~~properties
server.servlet.session.timeout=30m
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=lax
~~~

Trong **production HTTPS**, đặt thêm `server.servlet.session.cookie.secure=true`. Không bật cứng Secure trong demo HTTP thuần nếu client thử nghiệm cần gửi lại cookie. Spring Boot có cơ chế liên quan forwarded headers, session cookie customizer và session store; kiểm thử cookie thực tế thay vì dựa vào config ở code review.

`src/main/java/com/example/sessiondemo/SessionDemoApplication.java`:

~~~java
package com.example.sessiondemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SessionDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(SessionDemoApplication.class, args);
    }
}
~~~

### 6.2. Controller: chủ động tạo session, chỉ đọc khi có, hủy phiên

`src/main/java/com/example/sessiondemo/web/DemoSessionController.java`:

~~~java
package com.example.sessiondemo.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo-session")
public class DemoSessionController {

    public record StartRequest(String nickname) {}
    public record DemoState(String nickname, int timeoutSeconds) {}

    @PostMapping(path = "/start", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DemoState> start(
            @RequestBody StartRequest input,
            HttpServletRequest request) {

        if (input.nickname() == null || input.nickname().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        HttpSession session = request.getSession(true);
        String nickname = input.nickname().trim();
        session.setAttribute("nickname", nickname);

        return ResponseEntity.ok(
                new DemoState(nickname, session.getMaxInactiveInterval()));
    }

    @GetMapping
    public ResponseEntity<DemoState> read(HttpServletRequest request) {
        // Không tự tạo session cho người chỉ xem trạng thái.
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.noContent().build();
        }

        Object value = session.getAttribute("nickname");
        if (!(value instanceof String nickname)) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(
                new DemoState(nickname, session.getMaxInactiveInterval()));
    }

    @DeleteMapping
    public ResponseEntity<Void> end(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }
}
~~~

**Phân tích:**
1. `POST /start` kiểm tra nickname, sau đó `getSession(true)` tạo phiên nếu chưa có; nếu đã có, nó **sử dụng lại phiên hiện tại**. Dữ liệu lưu là chuỗi nhỏ, tránh serialization phức tạp khi sau này chuyển sang kho dùng chung.
2. `GET /api/demo-session` gọi `getSession(false)`, trả `204 No Content` khi chưa có session hoặc chưa có nickname, không vô tình tạo session mới. Đây là **chính sách của demo**, không phải status bắt buộc Servlet.
3. `DELETE /api/demo-session` invalidates session nếu có và luôn trả 204. Với cookie cũ, GET sau logout sẽ không thể đọc attribute của phiên đã hủy.
4. Không trả `session.getId()` trong JSON; ID phải được giữ kín như credential phiên.
5. `timeoutSeconds` đọc từ `getMaxInactiveInterval()` (đơn vị giây), không nhất thiết bằng “số giây còn lại”.
6. Demo không có auth; ai kiểm soát cookie có thể truy cập nickname của session demo. Không dùng endpoint này để cấp quyền truy cập tài nguyên thật.

Nếu controller nhận `HttpSession` trực tiếp thay vì `HttpServletRequest` ở `read`, framework có thể tạo session trên GET. Thiết kế ở đây chủ ý không làm thế.

### 6.3. Chạy và quan sát cookie với curl

Tại project có Gradle wrapper:

~~~bash
./gradlew bootRun
~~~

Trên Windows có thể dùng `.\gradlew.bat bootRun`. Dùng `curl.exe` trong PowerShell khi muốn giữ nguyên cách truyền option của curl.

Đầu tiên, GET chưa có session:

~~~bash
curl -i 'http://localhost:8080/api/demo-session'
~~~

Kết quả mong đợi **204 No Content**, thường không có cookie session mới do controller này tạo. Các filter bên ngoài (nếu thêm sau này) có thể tạo session riêng, nên đừng tuyên bố cả ứng dụng không bao giờ sinh cookie.

Bắt đầu session, lưu cookie vào jar:

~~~bash
curl -i -c cookies.txt -X POST \
  'http://localhost:8080/api/demo-session/start' \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"Lan"}'
~~~

Dự kiến **200 OK**, JSON có nickname `Lan` và timeoutSeconds gần giá trị config (ví dụ 1800). Header `Set-Cookie` thường có tên `JSESSIONID` khi dùng container session. Cookie jar chỉ phục vụ kiểm thử; **không commit/công khai file cookie**.

Đọc lại bằng chính cookie:

~~~bash
curl -i -b cookies.txt 'http://localhost:8080/api/demo-session'
~~~

Dự kiến **200 OK** và `nickname="Lan"`. Không gửi cookie:

~~~bash
curl -i 'http://localhost:8080/api/demo-session'
~~~

Dự kiến **204 No Content** vì request không liên kết session cũ. Đây là bằng chứng trạng thái được nối bởi session ID, không bởi địa chỉ IP.

Hủy:

~~~bash
curl -i -b cookies.txt -X DELETE \
  'http://localhost:8080/api/demo-session'
curl -i -b cookies.txt 'http://localhost:8080/api/demo-session'
~~~

Request DELETE dự kiến **204**; GET với cookie cũ dự kiến **204**. Container có thể gửi cookie expired hoặc không tùy cấu hình, nhưng server đã invalidated session. Sau khi hủy, gửi POST /start với cookie cũ có thể tạo **một phiên mới** và ID mới; không hồi sinh phiên cũ.

### 6.4. Kiểm thử MockMvc vòng đời session

`src/test/java/com/example/sessiondemo/web/DemoSessionControllerTest.java`:

~~~java
package com.example.sessiondemo.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoSessionControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void getWithoutSessionDoesNotCreateOne() throws Exception {
        MvcResult result = mvc.perform(get("/api/demo-session"))
                .andExpect(status().isNoContent())
                .andReturn();
        assertEquals(null, result.getRequest().getSession(false));
    }

    @Test
    void sameSessionKeepsAttributeUntilInvalidation() throws Exception {
        MvcResult created = mvc.perform(post("/api/demo-session/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"Lan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Lan"))
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) created.getRequest().getSession(false);
        assertNotNull(session);
        assertEquals("Lan", session.getAttribute("nickname"));

        mvc.perform(get("/api/demo-session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Lan"));

        mvc.perform(delete("/api/demo-session").session(session))
                .andExpect(status().isNoContent());

        assertTrue(session.isInvalid());

        // Request mới không cung cấp session vừa hủy:
        mvc.perform(get("/api/demo-session"))
                .andExpect(status().isNoContent());
    }

    @Test
    void blankNicknameIsRejectedWithoutSessionCreation() throws Exception {
        MvcResult result = mvc.perform(post("/api/demo-session/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals(null, result.getRequest().getSession(false));
    }
}
~~~

Chạy `./gradlew test`. Kiểm thử trên xác nhận `getSession(false)`, attribute và `invalidate()` ở MockMvc **trong một ứng dụng**. Nó không chứng minh Redis TTL, cookie qua browser thực, cân bằng tải hai node, concurrent session writes hoặc xử lý khi Redis down; các bài kiểm thử đó cần server/container, Redis và load balancer thực tế.

Gợi ý bổ sung kiểm thử cho sản phẩm: thời gian timeout nhỏ và client cookie thật; session bị invalidate khi logout; cookie `HttpOnly/Secure/SameSite`; kiểm thử xác thực/CSRF của Spring Security; gửi hai request đồng thời; kiểm thử qua hai node với session store chung.

## 7. Multi-server: tại sao session mặc định của một node gây sự cố?

Giả sử load balancer chuyển request tới hai ứng dụng độc lập cùng chạy một phiên bản:

~~~text
                  ┌───────────────┐
Browser ──LB─────┤ App node A    │  Local session memory A
                  └───────────────┘
                  ┌───────────────┐
             └────┤ App node B    │  Local session memory B
                  └───────────────┘
~~~

Nếu lần đầu POST /start vào node A, container A tạo session ID và lưu nickname ở bộ nhớ A. Request tiếp theo tới B mang cùng cookie, nhưng B **không có entry session cục bộ** đó. `getSession(false)` trên B có thể trả null; client thấy trạng thái “mất” hoặc phải đăng nhập lại. Khi rolling deployment hay node restart, memory-local sessions cũng có thể biến mất.

Vấn đề này không được giải quyết chỉ bằng cách:
- Deploy cùng một file JAR lên hai node.
- Dùng cùng database nghiệp vụ nhưng vẫn giữ session in-memory.
- Bật HTTPS hoặc cookie chung domain.
- Đưa session ID trong header.
- Giữ một object user static hoặc singleton ở mỗi JVM.

Cần một cơ chế định tuyến hoặc **shared session persistence** phù hợp.

### 7.1. Sticky sessions ở load balancer

LB có thể giữ một browser tiếp tục vào node A (*session affinity/sticky routing*). Đây là cách giảm việc “nhảy node” khi sessions vẫn ở memory từng JVM.

Ưu điểm: đơn giản, ít round trip đến kho session; phù hợp một số hệ thống nhỏ, ngắn hạn.

Hạn chế:
- Nếu A chết/được thay trong deployment, dữ liệu in-memory có thể mất.
- Phân phối tải không đều khi có nhiều session nặng.
- Có thể cần cấu hình cookie affinity riêng của LB; cookie đó khác cookie ứng dụng.
- Mở rộng/giảm node và drain connections cần chiến lược.
- Sticky routing **không tự tạo failover session**; cần container replication hoặc shared store nếu muốn phiên sống sót khi node mất.

Không kết luận sticky luôn sai: nó là lựa chọn có trade-off, nhưng phải kiểm failure và công bố tác động user khi node hỏng.

### 7.2. Chia sẻ HttpSession qua Spring Session

Spring Session thay thế cơ chế lưu session phía container bằng một `SessionRepository`, thường thông qua `springSessionRepositoryFilter` bọc `HttpServletRequest/HttpSession`. Redis, JDBC hoặc store được hỗ trợ sẽ giữ attributes và metadata session dùng chung giữa node.

~~~text
 Browser (cookie SESSION hoặc cấu hình tương đương)
                   |
            Load balancer
             /          \
            v            v
      App node A      App node B
      Spring Session  Spring Session
            \          /
             \        /
           Shared Redis
       session attributes + TTL
~~~

Nhờ dùng chung Redis, request mang ID hợp lệ có thể đọc cùng session ở A hoặc B, miễn hai node dùng cấu hình namespace, serializer, timeout và session cookie tương thích. Spring Session **không làm toàn ứng dụng stateless**; nó chuyển trạng thái session từ mỗi JVM sang kho dùng chung.

Giới hạn:
- Redis/network có thể chậm hoặc down: read/write session thất bại và ảnh hưởng availability.
- Serialization của session attributes phải tương thích giữa các version deploy.
- Cùng session được cập nhật đồng thời từ nhiều node có thể có lost updates; không mặc nhiên có transaction/locking cho mọi attribute.
- Session cache/flush mode, TTL và thư viện khác nhau ảnh hưởng thời điểm thay đổi được lưu.
- Redis replication/failover có trade-off về durability; một lần ACK không đồng nghĩa mọi bản sao ở mọi vùng đã nhận được dữ liệu.
- Shared session store không tự chia sẻ dữ liệu của singleton/local caches, WebSocket state, file tạm hoặc transaction đang chạy.

### 7.3. Cấu hình Spring Boot 3.3 và Spring Session Redis

Giữ source Java Controller ở mục 6. Trong **ứng dụng Boot 3.3.x**, thêm các dependency do Spring Boot quản lý:

~~~groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.session:spring-session-data-redis'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
~~~

Đây là đoạn **thay thế block dependencies** trong `build.gradle` ở mục 6, không phải thêm một block `dependencies` thứ hai vô điều kiện. Với Boot 3.3, `spring-session-data-redis` cộng Redis connector là lựa chọn đúng theo Spring Session 3.x/Boot 3.3 docs. Tài liệu latest Spring Session 4.x có thể chỉ tới starter `spring-boot-starter-session-data-redis` của thế hệ Boot mới hơn; không copy tên starter latest vào Boot 3.3 khi chưa kiểm phiên bản.

Ví dụ cấu hình Redis ở môi trường **phát triển**:

~~~properties
server.servlet.session.timeout=30m
spring.session.timeout=30m
spring.session.redis.namespace=demo:session
spring.session.redis.flush-mode=on_save
spring.data.redis.host=localhost
spring.data.redis.port=6379
~~~

Với production:
- Thay Redis host/port qua environment/secrets/managed Redis, bảo vệ kết nối TLS, network policy, credentials, failover.
- Các node ứng dụng chia sẻ **một session repository phù hợp** và serializer/schema tương thích.
- Tách Redis namespace theo môi trường/ứng dụng; không chia sẻ namespace giữa staging và production.
- Định cấu hình request timeout, connection pool, monitoring, max memory và eviction phù hợp. Nếu Redis xóa key do eviction/TTL, phiên biến mất.
- Cookie name/path/domain và secure flags phải nhất quán qua node và reverse proxy.
- Đo độ trễ đọc/ghi phiên; tránh ghi object đồ sộ hoặc thay đổi liên tục.

Spring Boot auto-configuration có thể tự tạo repository filter khi có đúng dependency. Không cần vừa kích hoạt auto-configuration vừa gắn thêm `@EnableRedisHttpSession` trừ khi có chủ đích override; nếu tự cấu hình, hiểu rõ điều gì được auto-config back off. Sau khi bật, cookie mặc định có thể là `SESSION` thay cho `JSESSIONID`.

**Về serialization:** Redis session repositories cần serialize thuộc tính, nhiều cấu hình mặc định dùng JDK serialization; nếu lưu custom object, có thể cần `Serializable` và version/class compatibility. Dùng immutable primitives/String/ID đơn giản giúp giảm rủi ro; nếu đổi sang JSON serializer, cần quy tắc type metadata và chống deserialization gadget. Không bao giờ coi bất kỳ serialized Java object từ store là dữ liệu hoàn toàn vô hại chỉ vì đến từ Redis.

### 7.4. Thực nghiệm hai node, một Redis

Khởi động Redis phù hợp ở localhost:6379 (hoặc trỏ cả hai node tới kho đã được cấp quyền). Chạy **hai tiến trình ứng dụng** cùng source và config Redis, khác port:

~~~bash
./gradlew bootRun --args='--server.port=8081'
./gradlew bootRun --args='--server.port=8082'
~~~

Chạy mỗi lệnh trong terminal/process riêng. Hai tiến trình cần sử dụng cùng Redis session namespace và cấu hình cookie/serialization. Với nhiều ứng dụng cùng hostname `localhost` nhưng khác port, cookie HTTP **không tách theo port**; điều này cho phép thử chuyển node nhưng có thể gây nhiễu nếu trên cùng hostname còn app khác. Nên dùng cookie jar riêng cho test.

Gửi POST tạo session ở A, sau đó GET ở B bằng cùng cookie jar:

~~~bash
curl -i -c shared-cookies.txt -X POST \
  'http://localhost:8081/api/demo-session/start' \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"Lan"}'

curl -i -b shared-cookies.txt \
  'http://localhost:8082/api/demo-session'
~~~

Nếu Spring Session Redis hoạt động và cả hai node dùng chung store, GET tại B dự kiến **200** với nickname `Lan`. Thử DELETE ở B rồi GET ở A:

~~~bash
curl -i -b shared-cookies.txt -X DELETE \
  'http://localhost:8082/api/demo-session'

curl -i -b shared-cookies.txt \
  'http://localhost:8081/api/demo-session'
~~~

Dự kiến GET cuối là **204**, chứng minh invalidation đã được quan sát qua repository dùng chung (với điều kiện các request thực hiện tuần tự và backend khỏe). Nếu vẫn thấy 204 ngay trước khi chia sẻ Redis, chưa đủ chứng minh gì: hãy chắc chắn request đầu tạo session, response có cookie, tên/path cookie đúng và request sau gửi đúng cookie.

### 7.5. Khi kho session gặp lỗi

Nếu Redis không khả dụng, kết quả phụ thuộc cấu hình và thời điểm:
- Tạo/read/write session có thể thất bại; không được tự động coi user anonymous rồi tiếp tục quyền nhạy cảm.
- LB có thể nhận nhiều request thất bại đồng thời.
- Chuyển node không có tác dụng nếu cả hai đều phụ thuộc cùng store hỏng.
- Thêm Redis HA/replication giúp cải thiện availability nhưng **không cung cấp consistency/durability tuyệt đối**.
- Khi một phiên mất vì eviction/TTL, người dùng có thể cần tạo phiên lại/đăng nhập lại; hệ thống phải làm rõ hành vi trải nghiệm.

Cần monitoring session lookup failures, Redis latency, error rates, key eviction, stale serialization, rolling deploy compatibility. Không log session ID thô để debug; dùng request tracing và các metric tổng hợp.

## 8. Concurrency: shared session store không tự xử lý race condition

Hai request đồng thời có thể cùng đọc một attribute rồi sửa:

~~~text
Ban đầu counter=0
Request A đọc counter=0
Request B đọc counter=0
Request A ghi counter=1
Request B ghi counter=1
Kết quả counter=1 thay vì 2
~~~

Điều này có thể xảy ra với `HttpSession` cục bộ lẫn shared Redis. `setAttribute` không phải atomic “increment counter”, và container/store có thể có copy-on-read/write hoặc flush mode khác nhau. Một `synchronized(session)` trong node A **không đồng bộ với node B**.

Đối với operation quan trọng (thanh toán, kiểm kê, quota, giỏ hàng có tiền), cần nghiệp vụ có transaction, optimistic locking/version, Redis atomic command/Lua hoặc cơ chế hàng đợi/phân tán được thiết kế đúng. HttpSession phù hợp với trạng thái giao diện nhỏ và tạm thời; không dùng nó làm nguồn sự thật cho tài sản/đơn hàng.

Đặc biệt tránh đặt object mutable như `ArrayList` vào session rồi chỉnh nội dung mà không `setAttribute` lại: tùy session repository và flush mode, thay đổi bên trong object có thể không được đánh dấu “dirty” hoặc không được lưu đúng lúc. Với distributed session, ưu tiên immutable values, gán lại attribute sau thay đổi và thiết kế update an toàn trước cạnh tranh.

## 9. HttpSession, Spring MVC model session và Spring Security khác nhau

### 9.1. `@SessionAttribute` và `@SessionAttributes`

| API | Vị trí dùng | Mục đích |
| --- | --- | --- |
| `HttpSession` | Servlet request/controller | Chủ động tạo, đọc, ghi và hủy session |
| `@SessionAttribute("key")` | **Tham số** handler | Đọc một attribute đã tồn tại; có `required` |
| `@SessionAttributes("form")` | **Cấp controller** | Lưu model attribute tạm trong quy trình nhiều bước của controller |
| `SessionStatus#setComplete()` | Trong luồng `@SessionAttributes` | Kết thúc việc quản lý model session attributes của controller |
| `@RequestAttribute` | Tham số handler | Đọc attribute của **một request**, không phải session |

`@SessionAttributes` không phải câu lệnh tạo session tùy ý cho mọi dữ liệu. Nó thích hợp cho flow model qua nhiều request ở cùng controller; `SessionStatus.setComplete()` hoàn thành conversational model và dọn attribute liên quan, **không có nghĩa invalidate toàn bộ HttpSession** hay logout tài khoản.

`@SessionAttribute` phù hợp nếu filter/middleware đã đặt dữ liệu cần đọc; để không vô tình tạo session hoặc tự kiểm soát toàn bộ lifecycle, sử dụng `HttpServletRequest.getSession(false)` có thể dễ hiểu hơn.

### 9.2. Spring Security session management

Spring Security có thể dùng HttpSession để lưu `SecurityContext`/authenticated principal qua các request tùy authentication setup. Nhưng không phải mọi ứng dụng Spring Security đều stateful: bearer token resource server hoặc thiết lập `SessionCreationPolicy.STATELESS` có thể không duy trì session cho auth. Cụm từ “REST API” cũng **không bắt buộc** luôn stateless, dù stateless là lựa chọn phổ biến cho một số kiến trúc.

Đối với ứng dụng web đăng nhập cookie/session:
- Xác thực credentials ở auth provider/filter đáng tin.
- Có bảo vệ session fixation khi đăng nhập; Servlet hiện đại thường đổi session ID.
- Cấu hình CSRF protection cho request state-changing khi trình duyệt tự đính kèm cookie.
- Logout qua Spring Security để dọn security context và invalidated session theo config.
- Có thể áp giới hạn concurrent sessions và chiến lược khi session hết hạn.
- Xem xét remember-me token và SSO khác session; invalidating HttpSession không thu hồi mọi loại token.

Ví dụ `SessionCreationPolicy.STATELESS` nhằm tránh sử dụng HttpSession cho SecurityContext, nhưng **không phải lệnh cấm mọi code ứng dụng gọi `getSession(true)`**. Nếu muốn không có server session, phải thiết kế mọi thành phần đồng bộ với mục tiêu đó.

## 10. Chọn kiến trúc session khi tăng số lượng node

| Tiêu chí | Local container HttpSession | Sticky LB + local session | Spring Session + shared Redis/JDBC |
| --- | --- | --- | --- |
| Đơn giản khi một node | Tốt | Cần LB | Cần kho ngoài |
| Giữ state giữa hai node bất kỳ | Không | Không, trừ khi affinity luôn về node cũ | Có, nếu store và config khỏe |
| Node A mất khi có session | Thường mất session | Thường mất/ảnh hưởng phiên gắn A | Node B có thể tiếp tục với store chung |
| Latency cho attribute access | Thường trong process | Thường trong process | Có chi phí I/O/serialization |
| Vấn đề concurrency | Có | Có | Có, thậm chí cross-node |
| Vấn đề serialization/deploy | Container-specific | Container-specific | Rõ hơn, cần tương thích schema/serializer |
| Kho session là phụ thuộc mới | Không | Không | Có, cần HA/observability |
| Phù hợp | Dev, một node, session mất được | Affinity được chấp nhận, fallback có kế hoạch | Nhiều node cần duy trì session qua chuyển node |

Nếu nghiệp vụ **không cần server-side session**, một API stateless với xác thực token phù hợp có thể dễ scale hơn vì các node không cần dùng chung HttpSession. Nhưng token cũng có expiration, rotation, revocation, replay và security trade-off; không tuyên bố chuyển sang JWT tự động giải quyết mọi vấn đề phân tán.

Có ứng dụng vẫn dùng Redis cho caching/rate limiting nhưng không dùng Spring Session; chỉ thêm Redis dependency không có nghĩa `HttpSession` tự động chuyển sang Redis. Phải có Spring Session integration/repository filter và cấu hình đúng.

## 11. Bảo mật và vận hành thực tế

### 11.1. Rủi ro và biện pháp

| Rủi ro | Hành động |
| --- | --- |
| Session ID bị đọc từ JavaScript | Dùng cookie HttpOnly và phòng chống XSS |
| Cookie gửi qua HTTP | Bật HTTPS và Secure cookie ở production |
| Session fixation sau login | Xoay ID với Spring Security/container strategy |
| CSRF trên cookie-authenticated POST/DELETE | Dùng Spring Security CSRF token và chính sách SameSite phù hợp |
| Session tồn tại quá lâu | Timeout hợp lý, absolute lifetime nếu nghiệp vụ đòi hỏi |
| Lộ session trong logs | Không log ID/cookie; dùng correlation ID riêng |
| Redis outage/eviction | Giám sát availability, memory policy, TTL và recovery |
| Serialization của object lớn | Chỉ lưu dữ liệu nhỏ, ID bất biến, serializer/schema có version |
| Concurrent writes mất cập nhật | Atomic/transactional update ở domain store |
| Cross-environment session namespace | Prefix tách môi trường, credentials và network isolation |
| Logout chỉ xóa cookie client | Phải invalidate/revoke state phía server hoặc auth layer |
| Dùng session giữ tiền/quota | Chuyển nguồn sự thật sang transactional datastore |

### 11.2. Độ bền dữ liệu và session store

Một session **không phải** database giao dịch dài hạn. Nếu Redis được cấu hình làm cache có eviction, key session có thể mất trước expected inactivity timeout khi thiếu bộ nhớ. Redis replication/HA không đồng nghĩa không mất dữ liệu trong mọi partition/failover. JDBC-backed Spring Session có trade-off khác về độ bền, latency, cleanup và locking, nhưng cũng cần migration/schema, index, transaction và security.

Với rolling deployment, hai phiên bản app có thể chạy cùng lúc. Nếu session chứa serialized class của bản mới mà bản cũ không hiểu, người dùng gặp lỗi khi request tới node cũ. Cách giảm rủi ro: dùng record dữ liệu đơn giản, schema rõ, chiến lược serialization tương thích hoặc versioned attributes, và test canary/blue-green thực tế.

### 11.3. Điều cần đo trong production

Theo dõi: số session hoạt động, tỉ lệ tạo mới bất thường, mất session giữa requests, tỉ lệ login thất bại do timeout, Redis/session-store latency và error, dung lượng/key eviction, serialization failures, số cookie sai domain/path, session fixation/auth anomalies, thời gian xử lý logout và số request đồng thời trên cùng session.

Để chẩn đoán, dùng trace ID được tạo tin cậy và route template. Không đưa raw `Cookie`, `Set-Cookie`, session ID, password hoặc private attributes vào dashboard/log của mọi developer.

## 12. Lỗi thường gặp và cách tìm nguyên nhân

| Triệu chứng | Nguyên nhân | Kiểm tra |
| --- | --- | --- |
| GET endpoint tạo `JSESSIONID` bất ngờ | `getSession()`/injected `HttpSession` hoặc filter/security tạo session | Log có kiểm soát nơi gọi `getSession(true)`, kiểm chain |
| Mỗi request có session ID mới | Client không gửi cookie, cookie path/domain/secure sai | Dùng curl cookie jar và inspect Set-Cookie/Cookie |
| Đang dùng HTTPS nhưng cookie không quay lại | Reverse proxy/forwarded headers, SameSite/Path/Domain | Test browser/devtools và ingress config |
| Session mất khi chuyển node | Container-local state trên từng JVM | Test request A→B, xem Spring Session/shared store |
| Sticky routing vẫn mất session khi deploy | Pod/node cũ bị thay, local memory không replica | Lập kế hoạch drain và shared repository hoặc chấp nhận reset |
| Redis có dữ liệu nhưng app không đọc session | Khác namespace/cookie name/serializer, session expired | Đối chiếu config mỗi node, TTL, phiên bản |
| Thay attribute nhưng node khác vẫn thấy cũ | Flush/save behavior hoặc concurrent updates | Chọn immutable value, set lại, xem transaction/last-writer |
| `IllegalStateException` sau logout | Code tiếp tục dùng object session đã invalidate | Dừng mọi truy cập phiên cũ sau `invalidate()` |
| Logout không thực sự logout | Chỉ xóa custom attr/cookie; security context/token còn | Dùng Spring Security logout/revocation flow |
| Tạo session cho tất cả request API | Cấu hình/framework hoặc dùng getSession mặc định | Kiểm sessions policy và các filters |
| Session expiry event không chạy đúng lúc | Repository/event config, Redis keyspace notifications | Không phụ thuộc event cho nghiệp vụ quan trọng |
| Memory leak/session quá lớn | Lưu entity graph/giỏ hàng lớn, timeout vô hạn | Chỉ lưu ID/small data, timeout và instrumentation |
| Counter/cart bị ghi đè | Hai request cùng sửa session | DB transaction, optimistic locking hoặc atomic Redis operation |
| Test MockMvc xanh, multi-node fail | Mock session không chứng minh cookie/network/store | Viết integration test hai server + Redis/LB |

## 13. Quy trình chẩn đoán từng bước

1. **Xem Set-Cookie** của request tạo phiên và **Cookie** của request sau. Xác định tên và Path/Domain/Secure/SameSite; không copy giá trị thật ra ticket/log công khai.
2. Dùng `request.getSession(false)` ở endpoint đọc để thấy session có thực sự tồn tại không; tránh làm kết quả thay đổi chỉ vì debug.
3. Kiểm `getMaxInactiveInterval()` và cấu hình `server.servlet.session.timeout` / `spring.session.timeout` theo version.
4. So sánh request vào node A rồi node B với cùng cookie. Nếu chỉ A đọc được, xác định local memory hay không tương thích Spring Session.
5. Kiểm Redis connectivity, namespace, TTL, serialization, flush mode, memory eviction và độ trễ.
6. Với concurrent updates, kiểm lại ai sửa attribute, có dùng read-modify-write không, có transaction/locking không.
7. Kiểm Spring Security: session creation policy, fixation, login/logout, CSRF, SecurityContextRepository và remember-me nếu có.
8. Thử shutdown một node và kiểm client còn phiên khi dùng store chung; với shared store down, kiểm phương án fail closed, metrics và thông báo người dùng.
9. Kiểm cookie flags và TLS/forwarded-header config trong môi trường thật.
10. Phân biệt dữ liệu user/business không được phép mất với trạng thái session có thể tái tạo; chuyển dữ liệu lâu dài sang persistence phù hợp.

## 14. Câu hỏi tự kiểm tra

**1.** `request.getSession(false)` khác `request.getSession()` như thế nào?

**Trả lời:** `false` không tạo session mới và trả null nếu không có phiên hợp lệ; `getSession()` có thể tạo phiên khi thiếu.

**2.** `session.invalidate()` xóa một attribute hay toàn bộ session?

**Trả lời:** Nó làm session không còn hợp lệ và unbind các attributes; không nên dùng tiếp object đó.

**3.** Browser có cookie `JSESSIONID` còn tồn tại, vậy session server chắc chắn còn không?

**Trả lời:** Không. Server có thể đã invalidate, timeout hoặc mất in-memory state.

**4.** Vì sao sticky LB không bảo đảm session tồn tại sau khi node A chết?

**Trả lời:** Affinity chỉ định tuyến request đến A khi A còn khả dụng; state local A thường mất nếu A bị thay và không replication.

**5.** Spring Session Redis có tự tạo transaction toàn cục cho mọi session attribute không?

**Trả lời:** Không. Nó chia sẻ persistence; vẫn cần thiết kế concurrency, serialization, failure handling và tính nhất quán.

**6.** Khi Spring Security dùng session để lưu auth, chỉ gọi `session.removeAttribute("user")` có phải logout an toàn không?

**Trả lời:** Không. Auth state có thể ở security context hoặc thành phần khác. Dùng logout flow được Security cấu hình.

**7.** Có thể dùng `@SessionAttributes` như alias của `HttpSession.invalidate()` không?

**Trả lời:** Không. `@SessionAttributes` dành cho model attributes trong controller workflow; `SessionStatus.setComplete()` dọn dữ liệu flow, không hủy toàn bộ session hay đảm bảo logout.

## 15. Tài liệu chính thức

- [Jakarta Servlet 6.0 — `HttpSession`](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/http/httpsession): attributes, invalidate, inactivity timeout, access time, isNew.
- [Jakarta Servlet 6.0 — `HttpServletRequest`](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/http/httpservletrequest): `getSession(false)`, `changeSessionId()`, request/session ID methods.
- [Spring Framework — `@SessionAttribute`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/sessionattribute.html): đọc session attribute có sẵn.
- [Spring Framework — `@SessionAttributes`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/sessionattributes.html): lưu model attributes qua các bước form.
- [Spring Security — Session Management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html): authentication persistence, session fixation, concurrency/session policies.
- [Spring Boot 3.3 — Spring Session Integration](https://docs.spring.io/spring-boot/3.3/reference/web/spring-session.html): session stores, auto-configuration, timeout properties.
- [Spring Session 3.3 — Redis Reference](https://docs.spring.io/spring-session/reference/3.3/http-session.html): thay HttpSession container bằng Spring Session, clustered session integration.
- [Spring Session — Redis Boot Guide](https://docs.spring.io/spring-session/reference/guides/boot-redis.html): repository filter, Redis config và cookie behavior (đối chiếu version Boot khi chọn dependency).
- [Spring Session — API và Redis expiration/events](https://docs.spring.io/spring-session/reference/api.html): repository, flush modes, serialization, session events.

**Ghi nhớ:** HttpSession giúp nhiều HTTP requests chia sẻ trạng thái bằng session ID; `getSession(false)` đọc mà không tạo, `invalidate()` hủy phiên, timeout dựa trên inactivity. Khi chuyển từ một server sang nhiều server, cần quyết định rõ liệu giữ affinity và chấp nhận mất state hay dùng Spring Session với shared repository. Kho phiên phân tán giúp chia sẻ state nhưng vẫn cần bảo vệ ID, quản lý lifecycle, serialization, concurrency, availability và dữ liệu nghiệp vụ lâu dài.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="session-localization-004">@SessionAttributes, SessionStatus và khác biệt với @SessionAttribute</a>

<details>
<summary>Xem chi tiết</summary>

# @SessionAttributes, SessionStatus và khác biệt với @SessionAttribute trong Spring MVC

> **Bối cảnh:** Spring Web MVC theo **Servlet stack**, Java 17+ và Spring Boot 3.x/Spring Framework 6.x. Bài học phân biệt ba thành phần dễ nhầm vì tên giống nhau: **`@SessionAttributes`** lưu tạm các **model attributes** qua nhiều HTTP request của một luồng làm việc; **`SessionStatus`** đánh dấu kết thúc luồng ấy và yêu cầu dọn những attribute đã quản lý; **`@SessionAttribute`** đọc một **session attribute đã tồn tại** như tham số handler. Cả ba gắn với HTTP session nhưng **không đồng nghĩa**, cũng không thay thế bảo mật, validation hoặc lưu trữ bền vững.

## 1. Vì sao cần trạng thái giữa các request?

HTTP vốn không tự mang một Java object từ request này sang request tiếp theo. Một biểu mẫu nhiều bước có thể yêu cầu:

1. Người dùng điền **tên** ở bước 1.
2. Người dùng điền **thành phố** ở bước 2.
3. Người dùng xác nhận cả hai và hoàn tất.
4. Server xóa bản nháp tạm, không để dữ liệu cũ xuất hiện khi bắt đầu quy trình mới.

Nếu mỗi request chỉ có các trường của bước hiện tại thì cần một nơi giữ **draft tạm thời**. Một lựa chọn trong Spring MVC là model attribute được đưa vào **HTTP session** nhờ `@SessionAttributes`.

```text
Client (cookie JSESSIONID)
     |
GET /wizard/start
     | Controller tạo model["draft"]
     v
HTTP Session: draft(name="", city="")
     |
POST /wizard/name -> draft.name="Lan"
     |
POST /wizard/city -> draft.city="Da Nang"
     |
POST /wizard/finish
     | hoàn tất nghiệp vụ
     | SessionStatus.setComplete()
     v
HTTP Session: không còn draft thuộc controller workflow
     |
GET /wizard/start -> tạo draft mới
```

Cookie session (thường là `JSESSIONID` với cấu hình Servlet mặc định) giúp server nhận biết nhiều request đến từ cùng một HTTP session. Không gửi cookie tiếp thì server có thể xem request là session mới. Thuộc tính session không phải biến toàn cục giữa mọi người dùng.

**Khái niệm cốt lõi:**
- **Request scope:** dữ liệu chỉ tồn tại trong quá trình xử lý một request.
- **Model:** map dữ liệu controller đưa sang view; bình thường **không tự bền** qua các request.
- **HTTP session:** nơi giữ thuộc tính liên kết với một phiên phía server (tùy hạ tầng triển khai).
- **Controller conversation:** chuỗi request phục vụ một tác vụ tạm thời, ví dụ wizard; `@SessionAttributes` dành cho loại dữ liệu này.

## 2. So sánh ba API ngay từ đầu

| API | Khai báo ở đâu? | Nó làm gì? | Có tự lưu hay tự dọn không? |
| --- | --- | --- | --- |
| `@SessionAttributes("draft")` | **Trên class** `@Controller` / `@RestController` | Khai báo model attribute tên `draft` cần được lưu vào session giữa các request của controller workflow | Spring MVC tự đồng bộ model attribute phù hợp vào session; dọn khi conversation được đánh dấu complete |
| `SessionStatus status` | **Tham số handler method** | Cung cấp `setComplete()` và `isComplete()` cho lifecycle của workflow | `setComplete()` kích hoạt Spring MVC dọn **các attribute được `@SessionAttributes` quản lý** |
| `@SessionAttribute(name="tenantCode")` | **Trên tham số handler method** | Đọc một attribute đã nằm trong HTTP session | **Không tự tạo, lưu hay dọn** attribute; chỉ resolve giá trị để truyền vào tham số |
| `HttpSession` | Tham số handler method / Servlet API | Đọc, ghi, xóa attribute trực tiếp; có thể invalidate toàn bộ session | Chính code hoặc container/session infrastructure quản lý |

**Đừng nhầm số nhiều và số ít:**

- **`@SessionAttributes` (số nhiều):** cơ chế **model → session** theo phạm vi controller và lifecycle tác vụ.
- **`@SessionAttribute` (số ít):** cơ chế **session → tham số method** cho một giá trị đã có.
- **`SessionStatus`:** tín hiệu **hoàn tất** để controller thôi duy trì model attribute tạm trong session.

Chúng không phải cách khai báo “bean scope=session”. Ví dụ `@SessionScope` là cơ chế của Spring container để quản lý bean theo session; bài này tập trung vào **Spring MVC handler model attributes**.

## 3. `@SessionAttributes`: khai báo trên controller và lưu model attribute

Cú pháp tối thiểu:

```java
@Controller
@SessionAttributes("draft")
public class WizardController {

    @GetMapping("/wizard")
    public String start(Model model) {
        model.addAttribute("draft", new DraftForm());
        return "wizard-form";
    }
}
```

Đây là **method minh họa**, cần tự định nghĩa `DraftForm` và view `wizard-form`. Vai trò từng phần:

- `@SessionAttributes("draft")` khai báo controller dùng một **model attribute tên `draft`** xuyên nhiều request.
- `model.addAttribute("draft", new DraftForm())` đưa object vào Model.
- Sau khi method hoàn thành theo pipeline Spring MVC, Spring nhận ra attribute đã khai báo và giữ nó ở HTTP session.
- Ở request sau, Spring khôi phục model attribute từ session để handler cùng workflow tiếp tục sử dụng nó.

`@SessionAttributes` không đồng nghĩa “lấy mọi local variable của controller cho vào session”. Chỉ những **model attributes phù hợp theo tên hoặc kiểu** được khai báo mới tham gia.

### 3.1. Khai báo theo tên và/hoặc kiểu

```java
@SessionAttributes(names = {"draft", "addressForm"})
class WizardController {
    // ...
}
```

Hoặc:

```java
@SessionAttributes(types = DraftForm.class)
class WizardController {
    // ...
}
```

Các snippet trên là **hai phương án riêng**, không đặt trên cùng một class. Annotation hỗ trợ `names`/`value` và `types`:

- Dùng **tên** khi chỉ muốn một số model attributes cụ thể tham gia conversation.
- Dùng **kiểu** khi chủ đích muốn model attributes thuộc type tương ứng được lưu. Cẩn thận nếu controller có nhiều attributes cùng type nhưng không cần persistence: khai báo quá rộng có thể lưu nhiều dữ liệu hơn mong muốn.
- Có thể kết hợp name/type theo hợp đồng Spring; hãy tránh chọn quá rộng vì có thể tạo session không cần thiết, tăng dung lượng hoặc giữ dữ liệu nhạy cảm lâu hơn.

**Phạm vi:** annotation nằm ở class của **handler controller**. Spring chỉ kích hoạt cơ chế conversation khi xử lý controller đó; không phải lệnh “mọi controller đều tự động lưu `draft`”. Tuy nhiên HTTP session là kho chứa dùng chung trong cùng phiên, nên nếu nhiều controller vô tình dùng **cùng session attribute name**, có thể đụng nhau. Dùng tên đủ đặc thù cho workflow.

### 3.2. Lấy lại attribute bằng `@ModelAttribute`

```java
@PostMapping("/wizard/name")
public String saveName(
        @ModelAttribute("draft") DraftForm draft,
        BindingResult bindingResult) {
    // Spring tìm model attribute "draft", gồm giá trị đã khôi phục từ session.
    // Có thể thực hiện binding/validation theo quy tắc MVC.
    return "wizard-next";
}
```

Đây là **method minh họa** và yêu cầu template thích hợp. Không phải mỗi request Spring lại xây object mới nếu đã có session attribute tương ứng. `@ModelAttribute("draft")` là cách truy cập đối tượng của workflow và tham gia cơ chế binding.

**Cảnh báo an toàn:** property binding từ client có thể gán các field của bean mutable nếu method không giới hạn đầu vào. Không lưu entity chứa `isAdmin`, `ownerId`, `price` hoặc quan hệ JPA rộng chỉ vì `@SessionAttributes` tiện. Dùng form DTO chỉ chứa input hợp đồng, kiểm tra quyền và/hoặc cấu hình `@InitBinder#setAllowedFields`.

### 3.3. Tạo model attribute khi chưa có trong session

Để không phải khởi tạo thủ công tại nhiều handler:

```java
@ModelAttribute("draft")
public DraftForm initializeDraft() {
    return new DraftForm();
}
```

Method `@ModelAttribute` kiểu này được gọi để đóng góp giá trị khi model chưa có attribute đó; ở request có `draft` đã được phục hồi từ session, nó không nên ghi đè draft đã tồn tại. Đây là pattern phổ biến cho form wizard; xem ví dụ hoàn chỉnh ở mục 8.

**Cần phân biệt thiếu khởi tạo:** nếu handler khai báo `@ModelAttribute("draft")` và Spring kỳ vọng attribute trong session mà không có, behavior có thể phụ thuộc cách attribute được khai báo, có hay không initializer, constructor binding và luồng method. Đừng dựa vào việc một session rỗng luôn tự sinh đúng trạng thái; thiết kế bước khởi tạo rõ ràng và test request đi tắt/đánh dấu hoàn tất.

## 4. `SessionStatus`: đánh dấu hoàn tất và dọn attribute của workflow

Spring MVC cho phép chèn `SessionStatus` trực tiếp vào handler method:

```java
@PostMapping("/wizard/finish")
public String finish(
        @ModelAttribute("draft") DraftForm draft,
        SessionStatus sessionStatus) {

    // Đã xác thực/ghi dữ liệu nghiệp vụ thành công.
    sessionStatus.setComplete();
    return "redirect:/wizard/done";
}
```

Đây là **snippet minh họa**, không chạy độc lập. `setComplete()` có nghĩa **conversation hiện tại đã hoàn tất**; sau khi xử lý, Spring dọn những attributes do `@SessionAttributes` của controller quản lý. Đừng hiểu nhầm method này là:

- `HttpSession.invalidate()`: hủy cả HTTP session, kéo theo các session attributes khác và session identity.
- `session.removeAttribute("anyKey")`: xóa một key tùy ý trong servlet session.
- `model.clear()`: xóa mọi attribute tạm thời trong Model tại thời điểm method.
- `userService.save(...)`: lưu dữ liệu vĩnh viễn vào database.

`SessionStatus` là interface `org.springframework.web.bind.support.SessionStatus`, có:

| Method | Ý nghĩa |
| --- | --- |
| `void setComplete()` | Đánh dấu conversation đã xong để MVC thực hiện cleanup |
| `boolean isComplete()` | Xem handler hiện tại đã đánh dấu complete chưa |

**Quan trọng về thứ tự:** gọi `setComplete()` **sau khi** kiểm tra validation, quyền và commit/save nghiệp vụ thành công. Nếu validation fail rồi gọi complete, người dùng có thể mất dữ liệu draft và không thể tiếp tục đúng quy trình. Nếu database write thất bại, không đánh dấu complete trước khi kiểm tra kết quả.

### 4.1. `setComplete()` dọn những gì?

Với `@SessionAttributes("draft")`, Spring MVC xóa dữ liệu `draft` do conversation manager theo dõi trong session. Nó **không tự dọn** các session keys như `tenantCode`, giỏ hàng được tạo trực tiếp qua `HttpSession.setAttribute("cart", ...)`, hoặc trạng thái đăng nhập do framework bảo mật quản lý.

Nếu controller còn dùng `@SessionAttributes("shippingForm")`, cùng controller có thể dọn attribute phù hợp với annotation đó khi session status được complete. Đừng đặt nhiều workflow độc lập cần lifecycle khác nhau dưới một cấu hình `@SessionAttributes` quá rộng nếu một lần `setComplete()` có thể làm mất dữ liệu của luồng khác.

### 4.2. `SessionStatus` không phải trạng thái đăng nhập

Câu lệnh:

```java
status.setComplete();
```

**không tương đương logout**. Đăng nhập/đăng xuất do cơ chế security và session management điều khiển. Với Spring Security, logout thường dọn SecurityContext và có thể invalidate session theo cấu hình; không sử dụng `SessionStatus` để thay thế logout.

### 4.3. Sau complete, gọi workflow lần nữa có điều gì xảy ra?

Khi handler controller cần draft trở lại, Spring có thể **tạo một draft mới** nếu đã khai báo `@ModelAttribute("draft")` initializer. Vì vậy sau POST finish và GET start mới, dữ liệu có thể trở về chuỗi rỗng. Đây là cách thông thường để tránh hiển thị nhầm bản nháp cũ. Cần có test xác nhận tạo mới thực sự, nhất là khi đồng thời có một nơi khác ghi cùng key session.

## 5. `@SessionAttribute`: đọc giá trị đã có trong session

Annotation **số ít** được đặt trên **tham số method**, không trên class:

```java
@GetMapping("/current-tenant")
@ResponseBody
public String currentTenant(
        @SessionAttribute(name = "tenantCode", required = false)
        String tenantCode) {

    return tenantCode == null ? "missing" : tenantCode;
}
```

Giả sử một filter, authentication flow hoặc code ứng dụng đã làm:

```java
session.setAttribute("tenantCode", "tenant-a");
```

Khi request mang cùng HTTP session, Spring resolve attribute đó làm đối số `tenantCode`. Đây chỉ là một giá trị đọc được trong session, **không cần** class-level `@SessionAttributes("tenantCode")`.

Các tùy chọn:

- `name` hoặc `value`: tên session key.
- `required = true` (mặc định): thiếu attribute có thể phát sinh exception khi resolve method argument.
- `required = false`: cho phép giá trị không tồn tại; với kiểu reference như `String`, method có thể nhận `null`, nên kiểm tra trước khi dùng.
- Có thể dùng `Optional<T>` nếu muốn biểu diễn có/không theo kiểu mạnh hơn.

Ví dụ:

```java
@GetMapping("/locale-preference")
@ResponseBody
public String preferred(
        @SessionAttribute(name = "preferredLanguage", required = false)
        String language) {

    return language == null ? "default" : language;
}
```

**Khi nào dùng `@SessionAttribute`?** Khi một attribute được quản lý **ngoài controller conversation** và controller chỉ cần tham chiếu nó: thông tin do filter đặt, một setting theo session, hoặc metadata lưu bởi phần khác của ứng dụng. Nếu cần thêm/xóa attribute, có thể lấy `HttpSession` hoặc `WebRequest` theo hợp đồng tương ứng; không cần cố ép `@SessionAttributes` vào trường hợp đó.

### 5.1. Thiếu required session attribute

```java
@GetMapping("/must-have-tenant")
@ResponseBody
public String mustHave(
        @SessionAttribute("tenantCode") String tenantCode) {
    return tenantCode;
}
```

Nếu không có session hoặc không có key `tenantCode`, Spring MVC có thể ném exception do thiếu required session attribute trước khi method được gọi. HTTP status và body cụ thể do resolver/error configuration quyết định; **không khẳng định mọi phiên bản trả một body y hệt nhau**. Với dữ liệu thực sự tùy chọn, viết `required=false` hoặc `Optional` và xử lý nhánh vắng rõ ràng.

### 5.2. `@SessionAttribute` có sửa session không?

Không tự động. Đối số chỉ đưa một giá trị đã tồn tại cho method. Với reference đến một mutable object, code Java có thể **thay đổi chính object** đó; thế nhưng đó là side effect của object/session, **không phải cơ chế model conversation tự động của annotation**. Với dữ liệu persistent cần kiểm soát, ưu tiên immutable value hoặc service có logic ghi rõ ràng.

## 6. So sánh trực tiếp bằng một tình huống

Giả sử cùng session chứa:

```text
draft       = DraftForm("Lan", "Da Nang")
tenantCode  = "demo-tenant"
cart        = [Book-1, Book-2]
```

| Thao tác | Kết quả |
| --- | --- |
| Handler thuộc `@SessionAttributes("draft")` nhận `@ModelAttribute("draft") DraftForm` | Được tham gia lifecycle model/session; có thể đọc và cập nhật draft |
| Handler nhận `@SessionAttribute("tenantCode") String` | Chỉ đọc value `demo-tenant` vào method argument |
| `SessionStatus.setComplete()` của wizard controller | Xóa `draft` đã quản lý, không chủ động xóa `tenantCode` hoặc `cart` |
| `session.removeAttribute("tenantCode")` | Xóa key `tenantCode` mà không cần kết thúc draft |
| `session.invalidate()` | Hủy toàn bộ HTTP session, tất cả attributes trong session đó không còn được dùng |

**Tóm lại:** số nhiều quản lý **conversation model state**; số ít đọc **existing session state**; `SessionStatus` kết thúc conversation đã được quản lý. Không thay chúng cho nhau chỉ vì cùng liên quan `HttpSession`.

## 7. Khác biệt với những cơ chế gần giống

### 7.1. `@ModelAttribute` nhưng không có `@SessionAttributes`

```java
@PostMapping("/one-step")
public String submit(@ModelAttribute SimpleForm form) {
    return "done";
}
```

Thông thường form chỉ tồn tại trong vòng đời xử lý request hiện tại; Spring **không mặc định** giữ nó trong HTTP session cho các request kế tiếp. Muốn wizard cần lưu tạm, thêm cơ chế tương ứng (session conversation hoặc storage khác).

### 7.2. `RedirectAttributes.addFlashAttribute(...)`

```java
@PostMapping("/done")
public String done(RedirectAttributes redirects) {
    redirects.addFlashAttribute("notice", "Đã lưu");
    return "redirect:/summary";
}
```

Flash attribute phục vụ **truyền dữ liệu qua một redirect tới request kế tiếp**, thường cho thông báo thành công, không dành cho cả wizard nhiều bước kéo dài. Nền tảng có thể dùng session để mang flash data, nhưng vòng đời của flash không bằng `@SessionAttributes`.

### 7.3. `HttpSession` trực tiếp

```java
@GetMapping("/manual-session")
@ResponseBody
public String manual(HttpSession session) {
    session.setAttribute("pref", "compact");
    return (String) session.getAttribute("pref");
}
```

Đây là code chủ động thao tác session key. Dùng `HttpSession` nếu cần quản lý rõ key/TTL/invalidating/session metadata, nhưng nhớ kiểm soát kích thước và quyền. `SessionStatus` không xóa mặc định key `pref` ở ví dụ này nếu nó không phải managed session attribute của controller workflow.

### 7.4. `@SessionScope`

```java
@Component
@SessionScope
class ShoppingCart {
    // Bean theo scope session, không phải model attribute của wizard.
}
```

`@SessionScope` là cơ chế bean scope với proxy theo session trong Spring IoC; không phải annotation để bind Model sang session. Nếu ứng dụng chỉ cần shared state giữa controller/services trong một session, session-scoped bean có thể là một lựa chọn riêng, nhưng vẫn có vấn đề concurrency/serialization/session expiry.

## 8. Ví dụ hoàn chỉnh, chạy được: wizard và session attribute độc lập

Ví dụ Spring Boot 3.x, Java 17+ với duy nhất dependency **Spring Web** để giảm độ phức tạp. Không cần template engine hoặc database: các handler trả JSON nhưng vẫn là Spring MVC controller, nên có thể quan sát model/session lifecycle trực tiếp. Ví dụ cố tình không thêm Spring Security, dùng dữ liệu giả lập. Trong ứng dụng thật cần auth/CSRF phù hợp.

### 8.1. Chuẩn bị project

Tạo Maven Spring Boot 3.x project với dependency **Spring Web** tại [Spring Initializr](https://start.spring.io/). File `pom.xml` có starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

Đây là dependency fragment; parent/plugin/metadata do Initializr chuẩn bị. Đặt Java source sau ở `src/main/java/com/example/demo/DemoApplication.java`. Toàn file chỉ có một `public class`; những class khác package-private hợp lệ theo Java 17.

### 8.2. Mã Java đầy đủ

```java
package com.example.demo;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

class DraftForm {
    private String name = "";
    private String city = "";

    public DraftForm() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}

@Controller
@RequestMapping("/wizard")
@SessionAttributes("draft")
class WizardController {

    // Chỉ tạo draft mới khi chưa có attribute khôi phục từ session.
    @ModelAttribute("draft")
    public DraftForm initializeDraft() {
        return new DraftForm();
    }

    @GetMapping("/start")
    @ResponseBody
    public Map<String, String> start(
            @ModelAttribute(value = "draft", binding = false)
            DraftForm draft) {

        return snapshot("started", draft);
    }

    @GetMapping("/state")
    @ResponseBody
    public Map<String, String> state(
            @ModelAttribute(value = "draft", binding = false)
            DraftForm draft) {

        return snapshot("in-progress", draft);
    }

    @PostMapping(path = "/name",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseBody
    public Map<String, String> name(
            @ModelAttribute(value = "draft", binding = false)
            DraftForm draft,
            @RequestParam("name") String name) {

        draft.setName(name);
        return snapshot("name-set", draft);
    }

    @PostMapping(path = "/city",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseBody
    public Map<String, String> city(
            @ModelAttribute(value = "draft", binding = false)
            DraftForm draft,
            @RequestParam("city") String city) {

        draft.setCity(city);
        return snapshot("city-set", draft);
    }

    @PostMapping("/finish")
    @ResponseBody
    public Map<String, String> finish(
            @ModelAttribute(value = "draft", binding = false)
            DraftForm draft,
            SessionStatus status) {

        // Demo không ghi database. Trước khi complete trong hệ thống thật,
        // phải kiểm tra validation/quyền và commit nghiệp vụ thành công.
        Map<String, String> result = snapshot("complete", draft);
        status.setComplete();
        return result;
    }

    @PostMapping("/cancel")
    @ResponseBody
    public ResponseEntity<Map<String, String>> cancel(
            SessionStatus status) {

        status.setComplete();
        return ResponseEntity.ok(Map.of("stage", "cancelled"));
    }

    private static Map<String, String> snapshot(
            String stage, DraftForm draft) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("stage", stage);
        result.put("name", draft.getName());
        result.put("city", draft.getCity());
        return result;
    }
}

@RestController
@RequestMapping("/session/tenant")
class TenantController {

    @PostMapping("/set-demo")
    public Map<String, String> setDemo(HttpSession session) {
        // Chỉ minh họa server-side assignment; không lấy tenant từ client.
        session.setAttribute("tenantCode", "demo-tenant");
        return Map.of("tenant", "demo-tenant");
    }

    @GetMapping
    public Map<String, String> get(
            @SessionAttribute(name = "tenantCode", required = false)
            String tenantCode) {
        return Map.of("tenant",
                tenantCode == null ? "<missing>" : tenantCode);
    }

    @PostMapping("/clear")
    public Map<String, String> clear(HttpSession session) {
        session.removeAttribute("tenantCode");
        return Map.of("tenant", "<missing>");
    }
}
```

**Giải thích thiết kế:**

- `@Controller @SessionAttributes("draft")` đăng ký model attribute `draft` cho workflow. Nhờ `@ModelAttribute("draft")` initializer, request đầu có một `DraftForm` mới; request sau có session attr thì giữ cùng dữ liệu.
- `@ModelAttribute(value = "draft", binding = false)` ở handler tham gia việc lấy draft từ model/session **nhưng không tự bind mọi request parameter vào các property của nó**. Hai endpoint dùng `@RequestParam` và gán có chủ đích một property cho mỗi bước để minh họa tránh over-posting. Trong production còn phải validation và authorization, không chỉ copy chuỗi thô.
- Các method trả JSON nhờ `@ResponseBody`; dù controller không trả view template, MVC vẫn quản lý model/session attributes qua pipeline thông thường.
- `finish` và `cancel` nhận `SessionStatus` và gọi `setComplete()`; Spring dọn attribute `draft` của wizard. Object `DraftForm` trong handler vẫn dùng được khi tạo body result, nhưng sau request không được tiếp tục giữ trong managed session.
- `TenantController` không khai báo `@SessionAttributes`. Nó đưa session key `tenantCode` bằng `HttpSession.setAttribute(...)`, rồi đọc bằng **`@SessionAttribute` số ít**. `SessionStatus.setComplete()` của wizard không đụng vào key này.
- `Map.of(...)` không cam kết thứ tự JSON object fields, nên khi kiểm tra hãy so các field/values, không so thứ tự key. `snapshot` dùng `LinkedHashMap` để output quan sát dễ đọc.
- Spring MVC controllers là singleton theo mặc định; **không** đặt `private DraftForm currentDraft` lên controller để lưu user state, vì như vậy nhiều người dùng có thể nhìn thấy/chỉnh state của nhau. Draft phải đến từ session/model của request.

### 8.3. Chạy ứng dụng

Trong root project Maven:

```bash
# macOS hoặc Linux
./mvnw spring-boot:run

# Windows PowerShell / Command Prompt
mvnw.cmd spring-boot:run
```

Server mặc định nghe `http://localhost:8080` nếu không đổi `server.port` hoặc context path.

### 8.4. Gửi nhiều request với **cùng session cookie**

Dùng `-c cookies.txt -b cookies.txt` ở **mọi request** để lưu và nạp cookie jar; nếu bỏ một trong hai, request mới có thể không thuộc cùng session. Lệnh bên dưới dành cho Bash; PowerShell có thể dùng `curl.exe` và gộp mỗi lệnh trên một dòng.

**Bước 1 — Khởi tạo draft:**

```bash
curl -sS -c cookies.txt -b cookies.txt \
  http://localhost:8080/wizard/start
```

JSON body:

```json
{"stage":"started","name":"","city":""}
```

**Bước 2 — Chỉ ghi tên:**

```bash
curl -sS -c cookies.txt -b cookies.txt \
  -X POST http://localhost:8080/wizard/name \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'name=Lan'
```

JSON:

```json
{"stage":"name-set","name":"Lan","city":""}
```

**Bước 3 — Chỉ ghi thành phố:**

```bash
curl -sS -c cookies.txt -b cookies.txt \
  -X POST http://localhost:8080/wizard/city \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'city=Da Nang'
```

JSON:

```json
{"stage":"city-set","name":"Lan","city":"Da Nang"}
```

**Bước 4 — Đọc bản nháp xuyên request:**

```bash
curl -sS -c cookies.txt -b cookies.txt \
  http://localhost:8080/wizard/state
```

JSON:

```json
{"stage":"in-progress","name":"Lan","city":"Da Nang"}
```

Tất cả dữ liệu đến từ **HTTP session**; request `GET /wizard/state` không cần query/body gửi lại tên/thành phố.

**Bước 5 — Tạo một session attribute ngoài workflow:**

```bash
curl -sS -c cookies.txt -b cookies.txt \
  -X POST http://localhost:8080/session/tenant/set-demo

curl -sS -c cookies.txt -b cookies.txt \
  http://localhost:8080/session/tenant
```

Bước đầu ghi `tenantCode=demo-tenant` vào HttpSession; bước sau dùng `@SessionAttribute` đọc lại. Body có dữ liệu:

```json
{"tenant":"demo-tenant"}
```

**Bước 6 — Hoàn tất wizard:**

```bash
curl -sS -c cookies.txt -b cookies.txt \
  -X POST http://localhost:8080/wizard/finish
```

JSON phản ánh giá trị ngay trước cleanup:

```json
{"stage":"complete","name":"Lan","city":"Da Nang"}
```

Sau response này, `SessionStatus.setComplete()` đã yêu cầu MVC dọn `draft`. Gọi lại:

```bash
curl -sS -c cookies.txt -b cookies.txt \
  http://localhost:8080/wizard/state
```

JSON sẽ là trạng thái **khởi tạo mới** theo initializer:

```json
{"stage":"in-progress","name":"","city":""}
```

Nhưng đọc `tenantCode` vẫn thấy giá trị đã đặt:

```bash
curl -sS -c cookies.txt -b cookies.txt \
  http://localhost:8080/session/tenant
```

```json
{"tenant":"demo-tenant"}
```

Điều này minh họa khác biệt quan trọng: **kết thúc wizard không xóa toàn HTTP session**.

Nếu gọi `POST /wizard/cancel`, body là `{"stage":"cancelled"}` và draft cũng được dọn. Sau đó, gọi `POST /session/tenant/clear` thì riêng `tenantCode` được xóa; `GET /session/tenant` trả `{"tenant":"<missing>"}`.

**Lưu ý:** các chuỗi JSON phía trên mô tả giá trị trường trong ví dụ Spring Boot/Jackson mặc định; các HTTP header hoặc thứ tự field có thể khác theo cấu hình. Nếu request mất cookie session, bạn có thể thấy dữ liệu bị reset sớm hơn vì đã chuyển session. Nếu Spring Security được thêm và CSRF protection bật, POST có thể trả 403 nếu không kèm token; ví dụ này không bật security.

## 9. Kiểm thử lifecycle bằng MockMvc

Có thể kiểm thử việc **cùng session giữ draft qua nhiều request** và **cleanup khi complete** thay vì chỉ nhìn body JSON. Tạo Spring Boot test dependency `spring-boot-starter-test` trong build và file `src/test/java/com/example/demo/WizardSessionTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WizardSessionTest {

    @Autowired
    MockMvc mvc;

    @Test
    void draftSurvivesAcrossRequestsAndIsClearedOnComplete()
            throws Exception {

        var start = mvc.perform(get("/wizard/start"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) start.getRequest().getSession(false);
        assertNotNull(session);
        assertNotNull(session.getAttribute("draft"));

        mvc.perform(post("/wizard/name")
                        .session(session)
                        .param("name", "Lan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lan"));

        mvc.perform(post("/wizard/city")
                        .session(session)
                        .param("city", "Da Nang"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Da Nang"));

        mvc.perform(get("/wizard/state").session(session))
                .andExpect(jsonPath("$.name").value("Lan"))
                .andExpect(jsonPath("$.city").value("Da Nang"));

        mvc.perform(post("/wizard/finish").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("complete"));

        assertNull(session.getAttribute("draft"));

        mvc.perform(get("/wizard/state").session(session))
                .andExpect(jsonPath("$.name").value(""))
                .andExpect(jsonPath("$.city").value(""));
    }
}
```

Đây là file test **riêng**, sử dụng JUnit 5 và MockMvc của Spring Boot 3.x. Nếu test framework đã được tùy biến filter/security, cần cấu hình thích hợp. Chạy:

```bash
# macOS / Linux
./mvnw test

# Windows
mvnw.cmd test
```

Các assertions quan trọng:
- Sau GET start, session có `draft`.
- POST của step1/step2 với **chính `MockHttpSession`** giữ dữ liệu.
- Sau POST finish, session **không còn** `draft`.
- GET state tiếp theo tạo draft rỗng mới.

Một bài test khác hữu ích: đặt `tenantCode` trong cùng session trước khi finish, rồi xác nhận `getAttribute("tenantCode")` vẫn còn sau cleanup. Điều này kiểm tra `SessionStatus` chỉ xử lý managed session attributes.

## 10. Hạn chế và rủi ro trong ứng dụng production

### 10.1. HTTP session không phải database

Session có vòng đời riêng và có thể timeout, bị invalidate lúc logout, bị mất khi container restart nếu không có persistence phù hợp, hoặc được replicate/serialize trong hạ tầng phân tán. Nếu workflow phải tồn tại lâu, cần resume từ thiết bị khác hoặc yêu cầu audit, cân nhắc **server-side persistence** (database/cache với key an toàn) thay vì coi `@SessionAttributes` là nơi lưu bền vững.

Nếu chạy nhiều server instances, load balancer sticky sessions hoặc giải pháp session chia sẻ như Spring Session có thể cần thiết. Không mặc định session attribute nằm trong bộ nhớ JVM này tự xuất hiện ở JVM khác.

### 10.2. Không đặt mutable user state vào singleton controller

**Sai:**

```java
@Controller
class UnsafeController {
    private DraftForm draft = new DraftForm(); // Dùng chung giữa requests/users!
}
```

Controller thông thường là singleton bean. Field mutable như trên có nguy cơ rò dữ liệu giữa nhiều người dùng hoặc gây race condition. Đúng hơn là dùng request/model/session-bound data, với scope và lifecycle rõ ràng.

### 10.3. Nhiều tab và request đồng thời

Một session có thể được dùng bởi nhiều tab trình duyệt. Hai tab cùng mở wizard, cùng key `draft` có thể ghi đè dữ liệu của nhau. Ngoài ra, nhiều request đồng thời cập nhật một mutable object trong session có thể dẫn đến mất cập nhật; HTTP session **không** tự cung cấp transaction lock hoặc optimistic locking cho mọi thao tác.

Giải pháp tùy yêu cầu:
- Thiết kế một workflow/session tại một thời điểm nếu UX cho phép.
- Dùng unique **workflow ID** và storage riêng mỗi workflow (có TTL, quyền truy cập).
- Dùng concurrency control/transaction ở service hoặc persistence layer.
- Không sử dụng một mutable DraftForm session chung nếu sản phẩm hỗ trợ nhiều luồng chỉnh sửa song song.

### 10.4. Session fixation, cookies, CSRF và authorization

Không xem session ID hoặc việc có một attribute như bằng chứng duy nhất của quyền. Với web app dùng cookie session, cần TLS, `HttpOnly`, `Secure`, `SameSite` phù hợp; login/logout/session fixation protection do security configuration kiểm soát. `@SessionAttribute("tenantCode")` chỉ lấy giá trị đã có, không đảm bảo tenant đó được xác thực hay người dùng được phép truy cập mọi tài nguyên trong tenant.

Trong ví dụ `TenantController`, giá trị `tenantCode` được server gán là demo cố định. **Không** tạo một endpoint production cho phép client đổi tenant hay principal tùy ý rồi dùng giá trị đó để phân quyền; luôn xác minh từ identity/authorization service đáng tin cậy.

Các form POST/PUT/DELETE có tác dụng thay đổi trạng thái cần CSRF protection khi dùng cookie-based authentication. `SessionStatus.setComplete()` không tự kiểm tra CSRF hoặc ủy quyền.

### 10.5. Giảm dữ liệu nhạy cảm và giới hạn dung lượng

Tránh lưu password thô, access token tùy tiện, file upload lớn, JPA entity graph khổng lồ hoặc những object không thể serialize khi session replication yêu cầu. Session state nên nhỏ, có TTL phù hợp, chỉ giữ dữ liệu cần cho workflow. Nếu lưu object Java trong session cluster, loại object/cấu trúc có thể yêu cầu khả năng serialization theo cấu hình session store.

### 10.6. Validation và over-posting

Ở handler dùng `@ModelAttribute` có data binding, client có thể tự gửi các tham số ngoài UI, ví dụ `isAdmin=true`. Vì vậy:
- Ưu tiên **DTO riêng** cho wizard thay vì entity.
- Mỗi bước chỉ cho phép những trường tương ứng với hợp đồng.
- Nếu cần property binding mutable form, cân nhắc `@InitBinder("draft")` + `setAllowedFields(...)`, hoặc `@ModelAttribute(binding=false)` và gán field có chủ đích như demo.
- Kiểm tra Bean Validation và các ràng buộc nghiệp vụ trước khi gọi `setComplete()`.
- Authorization và kiểm tra ownership ở service vẫn bắt buộc; session object không tự đảm bảo quyền.

**Không nhầm:** validation kiểm tra giá trị hợp lệ; hạn chế binding kiểm soát field được gửi; authorization quyết định ai được phép thao tác tài nguyên.

## 11. Những lỗi thường gặp

| Triệu chứng | Nguyên nhân khả dĩ | Cách xử lý |
| --- | --- | --- |
| Draft mất sau mỗi request | Client không gửi cùng session cookie, session timeout, session store không chia sẻ | Gửi cùng `JSESSIONID`/cookie jar, kiểm tra session config |
| Draft cũ vẫn xuất hiện sau finish | Quên `SessionStatus.setComplete()` hoặc handler finish không thuộc workflow mong đợi | Gọi `setComplete()` sau khi hoàn tất; test attribute cleanup |
| `setComplete()` không xóa `tenantCode` | Key đó không do `@SessionAttributes` quản lý | Dùng `HttpSession.removeAttribute` hoặc cơ chế owner phù hợp |
| Session bị xóa toàn bộ sau wizard | Code gọi `HttpSession.invalidate()` | Phân biệt invalidate với `SessionStatus.setComplete()` |
| `@SessionAttribute` nhận `null` | `required=false` và key chưa có/session khác | Kiểm tra null/Optional hoặc đảm bảo nơi tạo value |
| Thiếu attribute báo lỗi trước controller method | `@SessionAttribute` mặc định `required=true` | Dùng required=false khi optional, bảo đảm key khi required |
| Hai tab sửa lẫn draft | Cả hai tab cùng HttpSession và same attribute key | Dùng workflow ID riêng và transaction/concurrency policy |
| Request body có field lạ sửa draft | Mutable model attribute bị property-bound ngoài hợp đồng | DTO nhỏ, allowlist hoặc binding=false |
| Trang POST trả 403 | CSRF protection bật nhưng thiếu token | Xem Spring Security và form/cURL token |
| Lưu draft trong field controller | Singleton chia sẻ giữa users | Không lưu mutable state per-user trên controller |
| Logout không xảy ra khi finish | `SessionStatus` không điều khiển authentication | Dùng logout/security session management |
| Browser mới không thấy draft | Session khác nhau theo cookie/client | Đây là giới hạn theo HTTP session, không phải lỗi converter |
| Cùng key session bị dùng ở controller khác | Collision tên attribute | Đặt tên attribute rõ workflow, kiểm thử tích hợp |

### 11.1. Gỡ lỗi có hệ thống

1. Kiểm tra mọi request có cùng session cookie không (tránh log nguyên session ID trong log production).
2. Kiểm tra controller có `@SessionAttributes("draft")` đúng tên model attribute không.
3. Kiểm tra đã thêm `draft` vào Model hoặc có `@ModelAttribute("draft")` initializer chưa.
4. Kiểm tra handler có nhận `@ModelAttribute("draft")` và có data binding bị cấm/ngoài ý muốn không.
5. Kiểm tra `SessionStatus#setComplete()` có được gọi **trước thời điểm mong muốn** hay bị bỏ quên.
6. Nếu chỉ đọc key có sẵn, kiểm tra nơi nào làm `HttpSession.setAttribute(...)` và tên key dùng với `@SessionAttribute`.
7. Với ứng dụng nhiều instance, kiểm tra session persistence/replication, load balancing và serialization.
8. Viết MockMvc test nhiều request cùng session, kết thúc workflow rồi tạo lại draft; thêm test nhiều tab/concurrent updates nếu sản phẩm cần.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Muốn giữ object `draft` thuộc Model giữa bước 1 và bước 2 của cùng controller, dùng gì?

**Đáp án:** Class-level `@SessionAttributes("draft")` và xây dựng/phục hồi Model attribute tương ứng.

**Câu 2.** Muốn kết thúc form wizard và xóa đúng những model attributes do controller workflow quản lý?

**Đáp án:** Nhận `SessionStatus` trong handler và gọi `setComplete()` sau khi hoàn tất thành công.

**Câu 3.** `@SessionAttribute("preferredLanguage") String language` có tự tạo key khi thiếu không?

**Đáp án:** Không. Nó đọc session attribute có sẵn; mặc định `required=true` và thiếu key có thể gây lỗi argument resolution. Nếu optional, dùng `required=false` hoặc `Optional` và xử lý vắng mặt.

**Câu 4.** `SessionStatus.setComplete()` có logout user và xóa mọi session key không?

**Đáp án:** Không. Nó kết thúc conversation do `@SessionAttributes` quản lý, không invalidate toàn HTTP session hoặc logout.

**Câu 5.** Vì sao `private DraftForm draft` là field không an toàn trong `@Controller`?

**Đáp án:** Controller thường là singleton và field bị chia sẻ giữa người dùng/request, tạo race condition và rủi ro lộ dữ liệu.

**Câu 6.** Cùng một người dùng mở wizard ở hai tab có hai bản nháp độc lập không?

**Đáp án:** Không mặc định. Hai tab thường có chung session cookie và key, có thể chỉnh chung object. Nếu cần workflow song song, phải thiết kế key/storage riêng.

**Câu 7.** Sau khi `@SessionAttributes` giữ được `DraftForm`, có thể bỏ validation và authorization vì dữ liệu nằm server-side không?

**Đáp án:** Không. Client có thể gửi tham số để bind và người dùng vẫn phải được kiểm tra quyền theo tài nguyên/động tác; session persistence chỉ là cơ chế lưu trạng thái.

## 13. Tài liệu chính thức

- [Spring Framework — `@SessionAttributes`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/sessionattributes.html): lưu model attributes tạm trong HTTP session và kết thúc bằng `SessionStatus`.
- [Spring Framework — `@SessionAttribute`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/sessionattribute.html): đọc pre-existing HTTP session attributes qua handler method parameter.
- [Spring Framework — `@ModelAttribute` methods](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-modelattrib-methods.html): cung cấp model attribute và phối hợp controller methods.
- [Spring Framework — `@ModelAttribute` method arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/modelattrib-method-args.html): binding, validation, model/session attribute resolution.
- [Spring Framework — MVC Method Arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html): `SessionStatus`, `@SessionAttribute`, `HttpSession` và những loại tham số được hỗ trợ.
- [Spring Framework — `SessionStatus` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/bind/support/SessionStatus.html): hợp đồng `setComplete()`, `isComplete()`.
- [Spring Security — CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html): bảo vệ các state-changing requests dùng session/cookie.

**Kết luận:** dùng **`@SessionAttributes`** cho một tập model attributes tồn tại tạm xuyên request trong cùng controller workflow; dùng **`SessionStatus.setComplete()`** để dọn chúng khi workflow thành công hoặc hủy; dùng **`@SessionAttribute`** khi chỉ cần **đọc** một key đã có trong HTTP session, đặc biệt khi được quản lý ngoài controller. Cả ba không thay thế session security, binding limits, business validation, concurrency control và lưu trữ bền vững.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="session-localization-005">LocaleResolver, LocaleContextResolver và lựa chọn ngôn ngữ</a>

<details>
<summary>Xem chi tiết</summary>

# LocaleResolver, LocaleContextResolver và lựa chọn ngôn ngữ trong Spring MVC

## 1. Bài toán: cùng một trang, nhiều ngôn ngữ

Một người truy cập website với HTTP header:

```http
GET /welcome HTTP/1.1
Accept-Language: vi-VN,vi;q=0.9,en;q=0.7
```

Họ mong thấy lời chào bằng tiếng Việt. Người khác gửi `Accept-Language: en-US,en;q=0.8` thì muốn thấy tiếng Anh. Sau đó người dùng có thể chọn ngôn ngữ thủ công, và lựa chọn đó nên được nhớ trong cookie hoặc session. Từ nhu cầu này có ba câu hỏi khác nhau:

1. **Ngôn ngữ (locale) hiện tại được chọn thế nào?** Spring MVC dùng một `LocaleResolver`.
2. **Nếu ngoài ngôn ngữ còn có múi giờ thì sao?** `LocaleContextResolver` mở rộng mô hình để trả về `LocaleContext`, có thể kèm `TimeZone`.
3. **Chọn locale rồi làm sao hiển thị văn bản được dịch?** `MessageSource`, resource bundles và template engine sử dụng locale đã chọn để tìm thông điệp. Resolver tự nó không dịch text.

Sau bài này, bạn sẽ biết các chiến lược `AcceptHeaderLocaleResolver`, `CookieLocaleResolver`, `SessionLocaleResolver` và `FixedLocaleResolver`; cấu hình `LocaleChangeInterceptor`; phân biệt language/region với time zone; xử lý fallback và supported languages; áp dụng `MessageSource` cho controller và Thymeleaf; tự kiểm thử bằng Spring Boot 3, Java 17 và MockMvc.

**Phạm vi:** Spring MVC **Servlet stack**, Spring Framework 6.x (ví dụ 6.2) và Spring Boot 3.x. Trong Spring WebFlux cũng có giao diện cùng tên `LocaleContextResolver` nhưng ở package `org.springframework.web.server.i18n` và dùng `ServerWebExchange`; **không được nhập nhầm** với Servlet MVC package `org.springframework.web.servlet`.

## 2. Locale là gì? Không nhầm với ngôn ngữ đơn thuần

`java.util.Locale` diễn tả ngôn ngữ và các thông tin văn hóa/địa phương như region, script, variant tùy trường hợp:

| Language tag | Ý nghĩa điển hình | `Locale.forLanguageTag(...)` |
| --- | --- | --- |
| `vi` | Tiếng Việt, chưa chỉ định quốc gia | `Locale.forLanguageTag("vi")` |
| `vi-VN` | Tiếng Việt ở Việt Nam | `Locale.forLanguageTag("vi-VN")` |
| `en` | Tiếng Anh nói chung | `Locale.forLanguageTag("en")` |
| `en-US` | Tiếng Anh ở Mỹ | `Locale.forLanguageTag("en-US")` |
| `en-GB` | Tiếng Anh ở Anh | `Locale.forLanguageTag("en-GB")` |
| `ja-JP` | Tiếng Nhật ở Nhật | `Locale.forLanguageTag("ja-JP")` |
| `zh-Hant-TW` | Tiếng Hoa chữ phồn thể, vùng Đài Loan | Locale gồm language/script/region |

Locale chi phối cách chọn **message**, format số, ngày, tiền tệ; nó **không nhất thiết quyết định múi giờ**. Cùng `en-US`, người dùng có thể đang ở New York, Seattle hoặc Hà Nội. `TimeZone` đại diện một thuộc tính độc lập như `Asia/Ho_Chi_Minh` hay `Europe/London`, đặc biệt quan trọng khi hiển thị `Instant` thành giờ địa phương.

Để parse BCP 47 language tag chuẩn, thường dùng:

```java
Locale vn = Locale.forLanguageTag("vi-VN");
Locale us = Locale.forLanguageTag("en-US");
System.out.println(vn.toLanguageTag());
System.out.println(us.getLanguage());
```

Đoạn minh họa có `import java.util.Locale;`, stdout tương ứng:

```text
vi-VN
en
```

Đây là Java logic minh họa; khi cần chạy độc lập, đặt vào một class có `main`. Trong URL parameter và cookie, dùng language tags nhất quán, ví dụ `vi`, `en` hoặc `vi-VN`, thay vì tùy tiện ghép string theo dấu gạch dưới.

### 2.1. Accept-Language không phải lựa chọn bắt buộc

HTTP `Accept-Language` thể hiện thứ tự **ưu tiên** của user agent; `q` từ 0 đến 1 thể hiện trọng số. Server có thể giới hạn các ngôn ngữ được hỗ trợ và chọn fallback phù hợp; không phải client đòi `fr-CA` thì ứng dụng bắt buộc có bản dịch `fr-CA`. Một số request từ API client không gửi header này. Lựa chọn thủ công trong tài khoản/cookie/session có thể được ưu tiên hơn header nếu đó là chính sách của ứng dụng.

Để API trung thực, hãy phân biệt:

- **Requested locale:** mong muốn được client trình bày.
- **Resolved locale:** kết quả mà chiến lược Spring MVC chọn cho request.
- **Message bundle thực sự dùng:** các bundle như `messages_vi.properties`; một key có thể fallback sang bundle mặc định dù resolved locale là `vi-VN`.
- **Time zone của người dùng:** thuộc tính độc lập, không suy ra từ ngôn ngữ.

## 3. Vai trò của LocaleResolver trong DispatcherServlet

Giao diện Servlet MVC:

```java
package org.springframework.web.servlet;

public interface LocaleResolver {
    Locale resolveLocale(HttpServletRequest request);

    void setLocale(HttpServletRequest request,
                   HttpServletResponse response,
                   Locale locale);
}
```

*Đây là bản trích chữ ký nhằm giải thích ý nghĩa, không phải source đầy đủ (các annotation nullability/docs được lược bỏ).*

`resolveLocale(request)` trả locale hiện tại theo một chiến lược: header, cookie, session hoặc fixed value. `setLocale(...)` yêu cầu đổi locale, nhưng **không phải implementation nào cũng hỗ trợ** thao tác này.

`DispatcherServlet` tìm bean có tên chính xác **`localeResolver`** trong context của nó. Nếu không có, mặc định Framework dùng `AcceptHeaderLocaleResolver`. Cách chọn này có thể được Spring Boot tự cấu hình hoặc bị override bằng bean custom.

Luồng đơn giản:

```text
HTTP request
    │
    ▼
DispatcherServlet
    │
    ├── Chọn HandlerMapping và controller
    │
    ├── LocaleResolver.resolveLocale(request)
    │       ├── Accept-Language
    │       ├── cookie
    │       ├── HttpSession
    │       └── fixed/fallback
    │
    ├── LocaleContextHolder / request context
    │       └── locale hiện tại của request
    │
    ├── Controller, Validator, Formatter, Template View
    │       └── MessageSource.getMessage(code, args, locale)
    │
    ▼
HTML/JSON hoặc thông báo theo ngôn ngữ đã chọn
```

**Không lưu locale người dùng vào một field mutable trên singleton controller.** Controller/service bean có thể phục vụ nhiều requests song song. Lấy locale theo request hoặc context hiện hành, hoặc truyền `Locale` một cách tường minh trong method.

### 3.1. Lấy locale trong code ứng dụng

Các lựa chọn:

```java
@GetMapping("/locale")
@ResponseBody
String locale(Locale locale) {
    return locale.toLanguageTag();
}
```

`Locale` method argument được Spring MVC resolve từ request. Các API liên quan:

- `LocaleContextHolder.getLocale()`: lấy locale gắn với context hiện tại, thường là thread xử lý Servlet request.
- `RequestContextUtils.getLocale(request)`: lấy locale từ request theo resolver MVC đã đăng ký.
- `RequestContextUtils.getTimeZone(request)`: lấy time zone nếu resolver hỗ trợ context thời gian; có thể trả null.

`LocaleContextHolder` dựa trên thread context, nên với `@Async`, executor, tác vụ nền hoặc thread tự tạo, **không mặc định** locale từ HTTP request được truyền qua. Khi dùng xử lý bất đồng bộ, hãy truyền locale rõ ràng hoặc thiết kế context propagation phù hợp; không giữ lại `HttpServletRequest` để dùng sau khi request đã kết thúc.

## 4. LocaleContextResolver khác LocaleResolver thế nào?

Trong **Servlet MVC**, `org.springframework.web.servlet.LocaleContextResolver` là interface **extends `LocaleResolver`** và bổ sung khả năng làm việc với `LocaleContext`:

```java
package org.springframework.web.servlet;

public interface LocaleContextResolver extends LocaleResolver {
    LocaleContext resolveLocaleContext(HttpServletRequest request);

    void setLocaleContext(HttpServletRequest request,
                          HttpServletResponse response,
                          LocaleContext localeContext);
}
```

*Chữ ký đã giản lược annotation và một số default methods để giải thích; `LocaleContext` thuộc `org.springframework.context.i18n`.*

`LocaleContext` có `getLocale()`. Khi thực thể context còn implements `TimeZoneAwareLocaleContext`, nó có thể cung cấp `getTimeZone()`.

Sơ đồ quan hệ:

```text
LocaleResolver
 ├── resolveLocale(request): Locale
 └── setLocale(request, response, locale)
          ▲
          │ extends
LocaleContextResolver
 ├── resolveLocaleContext(request): LocaleContext
 └── setLocaleContext(request, response, context)
       │
       └── LocaleContext
            ├── Locale getLocale()
            └── nếu TimeZoneAwareLocaleContext:
                 TimeZone getTimeZone()
```

Các class `CookieLocaleResolver`, `SessionLocaleResolver` và `FixedLocaleResolver` trong Spring MVC 6.x triển khai giao diện mở rộng này thông qua `AbstractLocaleContextResolver`. `AcceptHeaderLocaleResolver` triển khai `LocaleResolver` cơ bản: nó đọc ngôn ngữ từ header và không tự lưu timezone/selection của người dùng.

### 4.1. Khi nào cần time zone?

Ví dụ email booking lúc `2026-12-24T09:00:00Z`:

- Với time zone `Asia/Ho_Chi_Minh`, thời điểm tương ứng là 16:00 cùng ngày.
- Với `Europe/London` vào tháng 12, thời điểm tương ứng là 09:00 (giờ chuẩn GMT).

Locale chỉ định ngôn ngữ hiển thị như `vi` hoặc `en`; để hiển thị thời gian tuyệt đối, phải biết time zone. Khi chuyển `Instant`, ưu tiên `java.time.ZoneId`/`ZonedDateTime`. `TimeZone` từ LocaleContext có thể đổi sang `ZoneId` bằng `timeZone.toZoneId()`.

Ví dụ đọc time zone từ request:

```java
TimeZone timeZone = RequestContextUtils.getTimeZone(request);
ZoneId zone = timeZone != null
        ? timeZone.toZoneId()
        : ZoneId.of("UTC");

String localTime = instant.atZone(zone).toString();
```

*Đoạn minh họa cần `HttpServletRequest request`, `Instant instant` và import tương ứng. Fallback UTC ở đây là chính sách mẫu; không khẳng định mọi hệ thống nên dùng UTC cho giờ hiển thị.*

**Cần phân biệt:** `LocaleChangeInterceptor` đổi *locale*, không phải một giao diện đầy đủ để chọn *time zone*. Muốn đổi cả hai, ứng dụng có thể dùng `LocaleContextResolver#setLocaleContext(...)` với `SimpleTimeZoneAwareLocaleContext` nếu resolver cho phép, hoặc có endpoint/policy riêng.

## 5. Bốn chiến lược LocaleResolver thường gặp

| Resolver | Nguồn locale | Đổi được bằng `setLocale`? | Thời gian lưu | Có context múi giờ? |
| --- | --- | --- | --- | --- |
| `AcceptHeaderLocaleResolver` | `Accept-Language` trong HTTP request | **Không**; header do client quyết định | Theo từng request | Không triển khai LocaleContextResolver |
| `CookieLocaleResolver` | Cookie lưu lựa chọn, fallback nếu không có | **Có** | Theo cookie; cấu hình max age | Có |
| `SessionLocaleResolver` | `HttpSession` attribute, fallback nếu chưa chọn | **Có** | Trong phiên người dùng | Có |
| `FixedLocaleResolver` | Locale cố định do server cấu hình | **Không** đổi động | Cố định với mọi request | Có thể có default timezone |

Tất cả tồn tại trong package `org.springframework.web.servlet.i18n`. Việc chọn resolver phù hợp phụ thuộc mục tiêu: API nhận ngôn ngữ theo HTTP header, website cho khách chọn ngôn ngữ và lưu cookie, website đã có session, hoặc ứng dụng chỉ phục vụ một ngôn ngữ.

### 5.1. AcceptHeaderLocaleResolver

Đây là mặc định quen thuộc trong Spring MVC khi không đăng ký resolver khác. Nó dựa vào `ServletRequest.getLocale()` và/hoặc danh sách locale được hỗ trợ.

```java
@Bean
LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver =
            new AcceptHeaderLocaleResolver();
    resolver.setSupportedLocales(List.of(
            Locale.forLanguageTag("vi"),
            Locale.forLanguageTag("en")
    ));
    resolver.setDefaultLocale(Locale.forLanguageTag("vi"));
    return resolver;
}
```

*Đây là cấu hình thay thế riêng, không dùng đồng thời với cookie resolver trong project mục 8.*

Với configured `supportedLocales`, Spring tìm locale phù hợp trong các ưu tiên của `Accept-Language`. Điểm dễ bỏ sót: việc so khớp có **ngôn ngữ và quốc gia**; nếu chỉ khai báo `en-US`, client gửi `en-GB` không tự động khớp. Muốn fallback theo ngôn ngữ, nên khai báo thêm `en` trong danh sách được hỗ trợ. Khi không khớp, dùng `defaultLocale` nếu có; nếu không thì fallback theo locale request.

**Quan trọng:** `AcceptHeaderLocaleResolver#setLocale(...)` không được hỗ trợ và có thể ném `UnsupportedOperationException`. Không ghép nó một cách mù quáng với `LocaleChangeInterceptor` nếu interceptor sẽ gọi `setLocale(...)` khi thấy query parameter.

### 5.2. CookieLocaleResolver

Cookie thích hợp cho website cần ghi nhớ lựa chọn ngôn ngữ qua các request mà không bắt buộc có server session:

```java
@Bean
LocaleResolver localeResolver() {
    CookieLocaleResolver resolver =
            new CookieLocaleResolver("SITE_LANG");
    resolver.setDefaultLocale(Locale.forLanguageTag("vi"));
    resolver.setCookieMaxAge(Duration.ofDays(30));
    resolver.setCookiePath("/");
    resolver.setCookieSameSite("Lax");
    resolver.setCookieHttpOnly(true);
    return resolver;
}
```

*Đoạn mã minh họa nằm trong `@Configuration`, cần import `LocaleResolver`, `CookieLocaleResolver`, `Duration` và `Locale`. Ứng dụng chạy đầy đủ ở mục 8.*

Khi người dùng chọn `lang=en`, một interceptor/controller có thể gọi `setLocale(...)` và gửi header `Set-Cookie` để lưu locale đã chọn. Request sau gửi lại cookie; resolver đọc giá trị đó. Cấu hình `maxAge` xác định thời hạn lưu; nếu muốn cookie chỉ sống trong phiên browser, chọn cấu hình phù hợp thay vì cố ép một thời hạn.

Cookie mang lựa chọn người dùng, không phải bằng chứng xác thực hay phân quyền. Không dùng cookie locale để tin cậy các trường bảo mật. Với HTTPS production, xét `Secure`, `SameSite`, `HttpOnly`, cookie path/domain, thời hạn lưu và chính sách privacy. Phản hồi có thể cần `Vary: Cookie` hoặc chính sách cache riêng khi HTML khác theo locale cookie.

Một lựa chọn cookie sau này có thể **ưu tiên hơn Accept-Language**. Nếu đã cố định `defaultLocale` trên resolver, locale fallback có thể dùng giá trị này thay vì header. Nên công bố thứ tự ưu tiên rõ trong sản phẩm.

### 5.3. SessionLocaleResolver

`SessionLocaleResolver` lưu locale/timezone trong `HttpSession`. Một cấu hình minh họa:

```java
@Bean
LocaleResolver localeResolver() {
    SessionLocaleResolver resolver = new SessionLocaleResolver();
    resolver.setDefaultLocale(Locale.forLanguageTag("vi"));
    return resolver;
}
```

Phù hợp với ứng dụng server-side đã có session và muốn giữ lựa chọn tạm trong thời gian session sống. Lựa chọn thường mất khi session hết hạn hoặc bị xóa. Nếu hệ thống không muốn tạo session, cân nhắc cookie hoặc AcceptHeader. Không đồng nhất session locale với profile language bền vững của tài khoản: nếu user đã đăng nhập, có thể cần đồng bộ lựa chọn lên database và load lại cho phiên sau.

### 5.4. FixedLocaleResolver

`FixedLocaleResolver` luôn trả một locale mặc định, có thể có default timezone:

```java
@Bean
LocaleResolver localeResolver() {
    return new FixedLocaleResolver(
            Locale.forLanguageTag("vi"),
            TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    );
}
```

*Đoạn minh họa đòi import `FixedLocaleResolver`, `Locale` và `TimeZone`; không chạy đồng thời với các bean resolver khác.*

Dùng khi ứng dụng có ngôn ngữ xác định, hoặc muốn behavior ổn định trong một môi trường. Không sử dụng `LocaleChangeInterceptor` để đổi động với fixed resolver vì `setLocale` không được hỗ trợ.

## 6. LocaleChangeInterceptor: cho người dùng chọn ngôn ngữ

`org.springframework.web.servlet.i18n.LocaleChangeInterceptor` là một `HandlerInterceptor`. Nó kiểm tra query parameter theo tên cấu hình (mặc định `locale`), parse thành locale rồi gọi `LocaleResolver#setLocale(request,response,locale)`.

Ví dụ URL:

```text
GET /welcome?lang=en
GET /welcome?lang=vi
```

Cấu hình:

```java
@Configuration
class WebLocaleConfig implements WebMvcConfigurer {

    @Bean
    LocaleResolver localeResolver() {
        CookieLocaleResolver resolver =
                new CookieLocaleResolver("SITE_LANG");
        resolver.setDefaultLocale(Locale.forLanguageTag("vi"));
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor interceptor =
                new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        interceptor.setHttpMethods("GET");
        registry.addInterceptor(interceptor);
    }
}
```

*Đoạn cấu hình minh họa; project hoàn chỉnh bên dưới có imports và cookie options.*

### 6.1. Trình tự trong request đổi ngôn ngữ

```text
GET /welcome?lang=en
       │
       ▼
HandlerMapping chọn controller + interceptor chain
       │
       ▼
LocaleChangeInterceptor.preHandle(...)
       ├── phát hiện param lang=en
       ├── parse Locale("en")
       └── resolver.setLocale(request, response, Locale.ENGLISH)
                  ├── CookieLocaleResolver ghi cookie
                  └── resolver lưu locale cho request hiện tại
       │
       ▼
Controller hoặc View dùng locale đã được chọn
       │
       ▼
MessageSource lấy message từ messages_en.properties
```

Trong cùng request, resolver hỗ trợ cập nhật locale để handler/view đọc được lựa chọn mới. Browser giữ cookie và gửi lại sau đó.

### 6.2. Tránh ghép interceptor với resolver không thay đổi được

Bảng:

| Resolver | `?lang=en` với LocaleChangeInterceptor |
| --- | --- |
| `AcceptHeaderLocaleResolver` | Không phù hợp; `setLocale` có thể ném `UnsupportedOperationException` |
| `FixedLocaleResolver` | Không phù hợp; không cho thay locale động |
| `CookieLocaleResolver` | Phù hợp; viết cookie |
| `SessionLocaleResolver` | Phù hợp; lưu vào session |

`setParamName("lang")` thay đổi tên query parameter. Có thể `setHttpMethods("GET")` để giới hạn kiểu request được phép đổi locale; `setIgnoreInvalidLocale(true)` cho phép bỏ qua chuỗi locale parse sai, nhưng **không phải whitelist ngôn ngữ hỗ trợ**.

**Lưu ý bảo mật/UX:** đừng cho vô hạn language tag từ client rồi dùng nó trong file path/SQL hoặc nghiệp vụ. Parse locale không chứng minh ngôn ngữ đó có message bundle. Có thể whitelist các lựa chọn `vi`, `en` tại UI và kiểm tra đầu vào phía server; khi ngôn ngữ không được hỗ trợ, từ chối hoặc fallback có chủ đích. Nếu đổi locale qua GET có tác dụng lưu cookie, hãy cân nhắc cache/crawlers/CSRF/privacy và quyết định hợp đồng phù hợp, không dùng nó cho nghiệp vụ có tác động quan trọng.

## 7. MessageSource và template engine: chọn ngôn ngữ chưa phải dịch

`LocaleResolver` chỉ chọn locale. Câu `"Welcome"`, `"Xin chào"` thuộc **message bundles** được quản lý bởi `MessageSource`, ví dụ:

```properties
# messages.properties - bundle mặc định
greeting=Welcome!
title=Locale demonstration
```

```properties
# messages_vi.properties
greeting=Xin chào!
title=Ví dụ ngôn ngữ
```

```properties
# messages_en.properties
greeting=Hello!
title=Language example
```

Controller có thể gọi:

```java
@GetMapping("/api/message")
Map<String, String> message(HttpServletRequest request) {
    Locale locale = RequestContextUtils.getLocale(request);
    String text = messageSource.getMessage("greeting", null, locale);
    return Map.of(
            "language", locale.getLanguage(),
            "message", text);
}
```

*Đoạn này minh họa một handler trong controller có field `MessageSource messageSource` được inject; project đầy đủ nằm ở mục 8.*

Với Thymeleaf, dùng expression `#{greeting}`:

```html
<h1 th:text="#{greeting}">Default greeting</h1>
```

Đó là expression tra MessageSource theo locale của request. Khác với `${someModelValue}` dùng để tra model variable; dấu `#` và dấu `$` có ý nghĩa khác nhau.

### 7.1. Spring Boot MessageSource auto-configuration

Spring Boot thường tìm default bundle `classpath:/messages.properties`. **Nếu chỉ có `messages_vi.properties` và `messages_en.properties` mà thiếu default `messages.properties`, MessageSource auto-configuration có thể không được tạo như bạn chờ đợi.** Định nghĩa default file để có fallback có chủ đích.

Ví dụ cấu hình:

```properties
spring.messages.basename=messages
spring.messages.encoding=UTF-8
spring.messages.fallback-to-system-locale=false
```

- `spring.messages.basename`: một hoặc nhiều nhóm bundle.
- `encoding=UTF-8`: giữ chữ có dấu Việt.
- `fallback-to-system-locale=false`: tránh kết quả phụ thuộc locale của hệ điều hành trong một số luồng fallback.
- MessageSource tìm theo mức đặc hiệu của locale khi có các file tương ứng như `messages_vi_VN.properties` và `messages_vi.properties`, sau đó fallback theo cấu hình và default bundle.

Người dùng chọn `vi-VN` vẫn có thể lấy key từ `messages_vi.properties` nếu không tồn tại file vùng cụ thể. Tên bundle thường sử dụng dấu underscore theo quy ước Java resource bundle, trong khi HTTP language tag dùng dấu gạch ngang (`vi-VN`). Không ghép đường dẫn file trực tiếp từ query parameter tùy ý.

### 7.2. Locale cũng ảnh hưởng số, ngày và validation messages

- `Formatter`, `@NumberFormat`, `@DateTimeFormat` có thể dùng locale cho parse/print ở web binding khi được cấu hình tương thích. Định dạng ngày/giờ còn cần timezone để ánh xạ đúng thời điểm.
- Constraint messages trong Jakarta Bean Validation có thể được tích hợp với Spring MessageSource hoặc bundle validation riêng, tùy cấu hình validator. Đừng giả định chỉ tạo `messages_vi.properties` là mọi message từ Hibernate Validator đều có bản dịch tiếng Việt tùy ý.
- Một request JSON API có thể chọn ngôn ngữ cho **thông báo lỗi** nhưng nên giữ error `code` ổn định để client logic không phụ thuộc văn bản bản địa hóa.
- Đối với HTML cache/CDN, response thay đổi theo `Accept-Language` nên cân nhắc header `Vary: Accept-Language`; theo cookie thì quan tâm cookie/session. Cache có thể phát nhầm ngôn ngữ giữa users nếu cache key không phản ánh ngôn ngữ.

## 8. Ví dụ hoàn chỉnh: Spring Boot 3 + Java 17 + CookieLocaleResolver

Ứng dụng nhỏ cung cấp:

- `GET /api/greeting`: JSON chứa language hiện tại và bản dịch từ MessageSource.
- `GET /api/greeting?lang=en` hoặc `?lang=vi`: interceptor đổi locale và phát cookie `SITE_LANG`.
- Request sau gửi cookie nhận lại cùng ngôn ngữ, không cần query parameter.
- `GET /api/context`: minh họa LocaleContext và time zone.
- `GET /welcome`: Thymeleaf render text bằng `#{greeting}` thay vì nhét một string dịch sẵn vào model.
- MockMvc chứng minh default/fallback, đổi ngôn ngữ, replay cookie, message rendering và context timezone.

Dùng **Spring Boot 3.3.13**, Java 17+ và Maven. Đây là mô hình **một cookie resolver được cấu hình rõ ràng**, không đồng thời tạo AcceptHeader/Session/Fixed resolvers trong cùng context.

### 8.1. Cấu trúc project

```text
locale-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/example/LocaleDemoApplication.java
    │   └── resources/
    │       ├── application.properties
    │       ├── messages.properties
    │       ├── messages_vi.properties
    │       ├── messages_en.properties
    │       └── templates/welcome.html
    └── test/java/example/LocaleDemoTest.java
```

### 8.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>locale-demo</artifactId>
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
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
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

### 8.3. application.properties và các message bundles

```properties
spring.messages.basename=messages
spring.messages.encoding=UTF-8
spring.messages.fallback-to-system-locale=false

spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html
spring.thymeleaf.cache=false
```

`src/main/resources/messages.properties`:

```properties
greeting=Welcome!
title=Locale demonstration
```

`src/main/resources/messages_vi.properties`:

```properties
greeting=Xin chào!
title=Ví dụ ngôn ngữ
```

`src/main/resources/messages_en.properties`:

```properties
greeting=Hello!
title=Language example
```

Mỗi key có một bản dịch. Với `lang=vi`, response là `"Xin chào!"`. Với `lang=en`, response là `"Hello!"`. Nếu language khác không có bundle, quy tắc fallback có thể dùng `messages.properties`; demo chỉ tập trung vào `vi` và `en`.

### 8.4. LocaleDemoApplication.java

Đặt toàn bộ code sau tại `src/main/java/example/LocaleDemoApplication.java`. Chỉ class main là public top-level class; các class còn lại package-private.

```java
package example;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;

@SpringBootApplication
public class LocaleDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(LocaleDemoApplication.class, args);
    }
}

@Configuration
class LocaleWebConfig implements WebMvcConfigurer {

    // Tên bean "localeResolver" được DispatcherServlet nhận biết.
    @Bean
    LocaleResolver localeResolver() {
        CookieLocaleResolver resolver =
                new CookieLocaleResolver("SITE_LANG");

        // Default cụ thể giúp kết quả không phụ thuộc hệ điều hành.
        resolver.setDefaultLocale(Locale.forLanguageTag("vi"));
        resolver.setDefaultTimeZone(
                TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

        resolver.setCookieMaxAge(Duration.ofDays(30));
        resolver.setCookiePath("/");
        resolver.setCookieSameSite("Lax");
        resolver.setCookieHttpOnly(true);

        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor change = new LocaleChangeInterceptor();
        change.setParamName("lang");
        change.setHttpMethods("GET");
        registry.addInterceptor(change);
    }
}

@RestController
class GreetingApiController {
    private final MessageSource messageSource;

    GreetingApiController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @GetMapping("/api/greeting")
    Map<String, String> greeting(HttpServletRequest request) {
        Locale locale = RequestContextUtils.getLocale(request);

        String message =
                messageSource.getMessage("greeting", null, locale);

        return Map.of(
                "language", locale.getLanguage(),
                "message", message);
    }

    @GetMapping("/api/context")
    Map<String, String> context(HttpServletRequest request) {
        Locale locale = RequestContextUtils.getLocale(request);
        TimeZone zone = RequestContextUtils.getTimeZone(request);

        return Map.of(
                "language", locale.getLanguage(),
                "timeZone", zone != null ? zone.getID() : "unset");
    }
}

@Controller
class WelcomePageController {
    @GetMapping("/welcome")
    String welcome() {
        return "welcome";
    }
}
```

Giải thích:

- Bean method `localeResolver()` có tên mặc định bằng tên method, đúng key mà `DispatcherServlet` tìm.
- `CookieLocaleResolver("SITE_LANG")` dùng constructor không deprecated trong Spring 6; không cần gọi `setCookieName(...)` đã deprecated từ 6.0.
- `setDefaultLocale(vi)` đặt locale fallback cố định. Trong demo **khi chưa có cookie**, giá trị này được ưu tiên; vì vậy `Accept-Language: en` một mình **không bảo đảm** lựa chọn tiếng Anh. Muốn coi header là ưu tiên, đừng cấu hình default cố định theo cách này hoặc chọn `AcceptHeaderLocaleResolver` tùy hợp đồng.
- `setDefaultTimeZone(...)` cho LocaleContext một time zone fallback. Nó không làm ngôn ngữ thay đổi.
- `LocaleChangeInterceptor` đọc `?lang=en`, gọi resolver cập nhật locale và đặt cookie cho các request sau.
- `MessageSource` giải quyết key `greeting` theo locale. Dù JSON body là API, vẫn có thể dùng locale để bản địa hóa thông điệp.
- Demo không mở rộng locale toàn cầu bằng singleton mutable; mỗi handler đọc từ request.

### 8.5. templates/welcome.html

File `src/main/resources/templates/welcome.html`:

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title th:text="#{title}">Demo</title>
</head>
<body>
  <h1 th:text="#{greeting}">Greeting</h1>
  <nav>
    <a th:href="@{/welcome(lang='vi')}">Tiếng Việt</a>
    <a th:href="@{/welcome(lang='en')}">English</a>
  </nav>
</body>
</html>
```

`th:text="#{greeting}"` tra cứu key trong `MessageSource` bằng locale đang hoạt động; `th:href="@{/welcome(lang='en')}"` tạo link chuyển ngôn ngữ. Template gán `lang="vi"` tĩnh để ví dụ dễ đọc, nhưng **không chính xác** khi người dùng đang xem tiếng Anh. Sản phẩm thực nên thêm `th:lang="${#locale.language}"` để HTML lang phản ánh ngôn ngữ được chọn. Nếu cần, thay thẻ `<html>` thành:

```html
<html xmlns:th="http://www.thymeleaf.org"
      th:lang="${#locale.language}">
```

Khi chạy, Thymeleaf tự bind variable `#locale` với locale hiện tại. Không nhầm `#{greeting}` (message expression) với `${#locale.language}` (variable expression).

### 8.6. Chạy và quan sát

Trong thư mục `locale-demo`:

```bash
mvn test
mvn spring-boot:run
```

Kiểm tra API:

```bash
curl -i http://localhost:8080/api/greeting
curl -i "http://localhost:8080/api/greeting?lang=en"
curl -i http://localhost:8080/api/context
```

Các response body JSON kỳ vọng:

- Không có cookie/param: `{"language":"vi","message":"Xin chào!"}` (thứ tự key JSON có thể khác).
- `?lang=en`: `{"language":"en","message":"Hello!"}` và header `Set-Cookie` cho `SITE_LANG`.
- `/api/context` với cấu hình mặc định: `{"language":"vi","timeZone":"Asia/Ho_Chi_Minh"}` nếu request không kèm cookie/param; timezone có thể được mang trong locale context khi resolver hỗ trợ.

Dùng cookie jar để chứng minh lựa chọn được giữ qua request:

```bash
curl -i -c locale-cookies.txt \
  "http://localhost:8080/api/greeting?lang=en"

curl -i -b locale-cookies.txt \
  http://localhost:8080/api/greeting

curl -i -b locale-cookies.txt \
  http://localhost:8080/welcome
```

Kết quả hai GET dùng cookie là ngôn ngữ tiếng Anh. Trên trang Thymeleaf, tiêu đề có `Language example` và `Hello!`. Nếu trực tiếp mở `http://localhost:8080/welcome?lang=vi`, interceptor sẽ cập nhật cookie sang tiếng Việt.

**Lưu ý:** lệnh `curl` ở trên dành cho shell hỗ trợ dấu nối dòng `\`; trên Windows PowerShell có thể dùng `curl.exe` và đưa tham số vào một dòng.

### 8.7. MockMvc: kiểm tra cookie và ngôn ngữ

File `src/test/java/example/LocaleDemoTest.java`:

```java
package example;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class LocaleDemoTest {
    @Autowired
    MockMvc mvc;

    @Test
    void fallbackLocaleIsVietnamese() throws Exception {
        mvc.perform(get("/api/greeting"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.language").value("vi"))
            .andExpect(jsonPath("$.message").value("Xin chào!"));
    }

    @Test
    void queryParameterChangesLocaleOnCurrentRequest()
            throws Exception {
        mvc.perform(get("/api/greeting").param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.language").value("en"))
            .andExpect(jsonPath("$.message").value("Hello!"));
    }

    @Test
    void chosenLocaleIsPersistedInCookie() throws Exception {
        MvcResult changed =
                mvc.perform(get("/api/greeting").param("lang", "en"))
                   .andExpect(status().isOk())
                   .andReturn();

        String header = changed.getResponse().getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.startsWith("SITE_LANG="));

        // Parse duy nhất cặp name=value đầu trong header Set-Cookie;
        // demo cookie chỉ chứa language tag, không có dấu '=' nội bộ.
        String value = header.split(";", 2)[0].split("=", 2)[1];

        mvc.perform(get("/api/greeting")
                    .cookie(new Cookie("SITE_LANG", value)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.language").value("en"))
            .andExpect(jsonPath("$.message").value("Hello!"));
    }

    @Test
    void defaultTimeZoneIsResolvedFromLocaleContext()
            throws Exception {
        mvc.perform(get("/api/context"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.language").value("vi"))
            .andExpect(jsonPath("$.timeZone")
                    .value("Asia/Ho_Chi_Minh"));
    }

    @Test
    void thymeleafRendersLocalizedMessage() throws Exception {
        mvc.perform(get("/welcome").param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(view().name("welcome"))
            .andExpect(content().string(
                    containsString("<h1>Hello!</h1>")));
    }

    @Test
    void apiResponseIsJsonNotAView() throws Exception {
        mvc.perform(get("/api/greeting").param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("Hello!"));
    }
}
```

Chạy `mvn test`. Bộ test chứng minh locale mặc định, thay đổi trên cùng request, cookie được gửi và dùng lại, time zone fallback, Thymeleaf sử dụng message bundle và JSON API không bị nhầm với template. Mỗi request MockMvc mặc định không tự tái sử dụng cookie từ request trước; test cố tình copy giá trị cookie sang request mới để mô phỏng browser.

Nếu deploy với reverse proxy, cookie policy có thể thêm `Secure`/`Domain` hoặc thay cách viết header. Vì vậy trong ứng dụng production, ưu tiên integration test đầy đủ trên HTTP client thực tế cho lifecycle của cookie, không chỉ MockMvc.

## 9. Khi nào nên dùng header, cookie, session hoặc tài khoản?

### 9.1. REST API stateless

Client có thể gửi:

```http
GET /api/products/7 HTTP/1.1
Accept-Language: en
```

Server dùng `AcceptHeaderLocaleResolver` và `MessageSource` để bản địa hóa thông điệp, giữ mã lỗi JSON ổn định. Không cần server session. Với API nhiều clients, phân biệt locale yêu cầu và ngôn ngữ thực hỗ trợ; không nên trả toàn bộ nội dung nghiệp vụ khác đi chỉ vì Accept-Language nếu hợp đồng không nói.

Một số API chọn header riêng hoặc user profile; phải định nghĩa thứ tự ưu tiên, nhất là khi cùng tồn tại cookie và Accept-Language. Dữ liệu quốc tế hóa không nên làm thay đổi ID, enum code hay tiền tệ một cách mơ hồ.

### 9.2. Website server-rendered

`CookieLocaleResolver` tiện cho website không muốn tạo session chỉ để nhớ language. Dùng links/buttons để chọn locale, rồi render Thymeleaf bằng message expressions. Nếu người dùng đăng nhập, có thể lưu ngôn ngữ vào profile; cookie đóng vai trò cache/preference tạm.

### 9.3. Website đã dùng HttpSession

`SessionLocaleResolver` thích hợp khi session là phần của thiết kế. Locale lựa chọn được giữ cho tới khi session hết hạn. Nếu session bị đổi do login/logout hoặc load balancing, xử lý đồng bộ profile/cookie/session một cách nhất quán.

### 9.4. Ứng dụng cố định ngôn ngữ

`FixedLocaleResolver` thích hợp cho dịch vụ chỉ có một ngôn ngữ hoặc môi trường kiểm thử cố định. Không thêm LocaleChangeInterceptor nếu không có nhu cầu đổi locale động.

### 9.5. Chiến lược ưu tiên rõ ràng

Một ví dụ về **chính sách sản phẩm tự định nghĩa** (không phải thứ tự mặc định bắt buộc của Spring):

```text
1. Lựa chọn explicit của user đã lưu trong profile
2. Cookie language do người dùng tự chọn
3. Accept-Language từ HTTP request
4. Locale mặc định của sản phẩm, ví dụ vi
```

Cần cài đặt chính sách trong resolver/custom logic một cách rõ ràng, nhất là khi ngôn ngữ hỗ trợ chỉ có `vi`/`en`. Không mong Spring tự phối hợp cả profile, cookie, header theo thứ tự trên nếu chưa lập trình.

## 10. Lỗi thường gặp và cách chẩn đoán

| Hiện tượng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `?lang=en` gây `UnsupportedOperationException` | Dùng LocaleChangeInterceptor với AcceptHeader/Fixed resolver | Chuyển sang Cookie/Session hoặc không cho đổi locale động |
| Dùng `LocaleContextResolver` nhưng import sai | Import từ `org.springframework.web.server.i18n` của WebFlux | Dùng `org.springframework.web.servlet.LocaleContextResolver` cho Spring MVC |
| Header `en-GB` không chọn ngôn ngữ mong muốn | Supported locale cấu hình chỉ `en-US` | Cấu hình ngôn ngữ-only `en` hoặc vùng tương thích |
| Browser gửi `Accept-Language: en` nhưng vẫn ra `vi` | Cookie cũ hoặc `defaultLocale(vi)` ưu tiên | Kiểm tra resolver, cookie và fallback order |
| `MessageSource` không xuất hiện | Thiếu file `messages.properties` mặc định hoặc sai basename | Thêm bundle mặc định và kiểm tra `spring.messages.*` |
| UI có chữ `??greeting??` hay lỗi missing code | Thiếu key trong bundle, bundle không load | Đối chiếu key, basename và language suffix |
| Chữ tiếng Việt thành ký tự lỗi | File/response encoding khác nhau | UTF-8 từ editor, bundle, HTML meta tới response |
| Time zone luôn null | Chỉ có basic LocaleResolver hoặc chưa cấu hình time zone | Dùng LocaleContextResolver + default/user timezone |
| `en-US` hiển thị ngày không đúng time zone | Locale không phải time zone | Dùng `ZoneId`/`TimeZone` riêng |
| Đổi locale nhưng background thread vẫn theo ngôn ngữ cũ | LocaleContextHolder không tự truyền qua thread | Truyền locale vào tác vụ hoặc context propagation |
| HTML CDN phát nhầm ngôn ngữ giữa người dùng | Cache không vary theo language/cookie | Bổ sung cache key/Vary hoặc tắt shared caching |
| User gửi locale kỳ lạ và UI fallback bất ngờ | Không whitelist locale hỗ trợ | Kiểm tra danh sách hỗ trợ, thông báo/fallback có chủ ý |
| Test thay đổi cookie nhưng request sau vẫn mặc định | MockMvc không giả lập browser cookie jar | Truyền cookie sang request tiếp theo |
| Dùng `Locale.US` cho toàn bộ người Việt | Nhầm language/region với locale UI | Dùng language tag thích hợp và bundle có fallback rõ |

### 10.1. Debug theo từng tầng

1. Xem request có `Accept-Language`, cookie `SITE_LANG` hoặc query `lang` hay không.
2. Xác định **đúng resolver bean** tên `localeResolver` được DispatcherServlet dùng. Kiểm tra kiểu bean và các giá trị mặc định.
3. Nếu có `LocaleChangeInterceptor`, kiểm tra nó được đăng ký trong `WebMvcConfigurer#addInterceptors` và tên parameter khớp.
4. Đọc resolved locale bằng `RequestContextUtils.getLocale(request)`; nếu cần context timezone thì `getTimeZone(request)`.
5. Kiểm tra `messages.properties`, `messages_vi.properties`, `messages_en.properties`, key và encoding.
6. Nếu controller trả đúng message nhưng Thymeleaf sai, kiểm tra template expression `#{...}`, theme, model, view name và template cache.
7. Với API, kiểm tra error `code` độc lập với localized `message` và client cache policy.
8. Viết MockMvc test có cả query param, cookie và request không có lựa chọn.

### 10.2. Hai ví dụ sai và sửa

**Sai:**

```java
@Bean
LocaleResolver localeResolver() {
    return new AcceptHeaderLocaleResolver();
}

@Override
public void addInterceptors(InterceptorRegistry registry) {
    LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
    interceptor.setParamName("lang");
    registry.addInterceptor(interceptor);
}
```

Khi URL chứa `?lang=en`, interceptor gọi `setLocale` trên resolver không hỗ trợ, phát sinh lỗi. Đây là đoạn minh họa để nhận diện lỗi, **không** đặt vào project chạy.

**Đúng khi muốn user đổi ngôn ngữ và lưu lại:**

```java
@Bean
LocaleResolver localeResolver() {
    return new CookieLocaleResolver("SITE_LANG");
}
```

Thêm interceptor như project mục 8 và cấu hình cookie/security nếu cần.

## 11. Bảo mật, hiệu năng và trải nghiệm quốc tế hóa

- **XSS:** bản dịch từ message bundles có thể chứa markup nếu ứng dụng cho phép; không render dữ liệu từ người dùng bằng HTML unescaped. Thymeleaf `th:text` mặc định escape text.
- **Open redirects:** endpoint đổi locale không nên chuyển đến URL tùy ý do request cung cấp. Chỉ redirect về route/host cho phép.
- **Cookie:** chỉ chứa preference ít nhạy cảm, chọn `SameSite`/`Secure`/`HttpOnly`/maxAge/path theo chính sách; không dùng cookie language để quyết định quyền.
- **Cache:** response HTML thay đổi theo locale; cấu hình cache theo cookie/header hoặc dùng URL ngôn ngữ tường minh để dễ cache.
- **SEO:** website công khai đa ngôn ngữ có thể chọn URL ngôn ngữ như `/vi/...` và `/en/...`, kết hợp `hreflang`/canonical theo chiến lược SEO. Cookie locale không nhất thiết là lựa chọn tốt nhất để crawl mọi biến thể.
- **A11y:** thuộc tính HTML `lang` phải phản ánh nội dung đang hiển thị; trong ví dụ ở trên có thể dùng `th:lang` động.
- **I18n message design:** tránh nối câu từ nhiều mảnh literal; dùng placeholders/pluralization phù hợp công cụ và dịch thuật theo ngữ cảnh.
- **Timezone & currency:** tách `Locale` khỏi time zone và currency thực tế. `Locale.US` không có nghĩa người dùng thanh toán bằng USD; nghiệp vụ phải có nguồn dữ liệu tin cậy riêng.
- **Validation:** đừng đổi field name hoặc error code theo ngôn ngữ; chỉ đổi thông điệp thân thiện hiển thị.
- **Operations:** log ngôn ngữ đã chọn ở mức không nhạy cảm để phân tích lỗi phân phối bundle; không log cookie nguyên vẹn kèm token của request.

## 12. Tự kiểm tra

1. `LocaleResolver` có tự dịch text không? **Không.** Nó chọn locale; MessageSource/template làm công việc tra thông điệp.
2. `LocaleContextResolver` kế thừa giao diện nào? **`LocaleResolver`**, thêm phương thức cho `LocaleContext`.
3. Có thể dùng `LocaleContextResolver` của WebFlux trong Servlet MVC không? **Không**, hai API ở package/loại request khác nhau.
4. `AcceptHeaderLocaleResolver` có hỗ trợ `setLocale` từ query parameter không? **Không**, vì header do client quản lý.
5. Nếu muốn user chọn tiếng Anh và nhớ qua nhiều request mà không dùng server session, chọn gì? **CookieLocaleResolver**.
6. Nếu muốn nhớ lựa chọn trong phiên HttpSession, chọn gì? **SessionLocaleResolver**.
7. Khi ứng dụng luôn chỉ hiển thị tiếng Việt, resolver phù hợp? **FixedLocaleResolver** hoặc chính sách locale cố định.
8. Vì sao supportedLocales chỉ có `en-US` có thể không match `en-GB`? **So khớp language+country**, nên bổ sung `en` nếu muốn fallback theo ngôn ngữ.
9. `vi-VN` có đồng nghĩa múi giờ Asia/Ho_Chi_Minh không? **Không**; locale và timezone là dữ liệu riêng.
10. Khi dùng Thymeleaf, `#{greeting}` khác `${name}` thế nào? **`#{...}` tra message code**, còn `${...}` lấy model variable.
11. Vì sao `messages.properties` quan trọng trong Boot? **Bundle mặc định giúp auto-config và fallback hoạt động như dự kiến**.
12. `LocaleChangeInterceptor` đọc param tên gì mặc định? **`locale`**; có thể dùng `setParamName("lang")`.
13. Nếu cookie ngôn ngữ đã lưu là `en` nhưng header là `vi`, cái nào được dùng? **Tùy resolver/chính sách**; với CookieLocaleResolver, cookie thường ưu tiên.
14. `MockMvc` có tự giữ cookie từ request trước như browser không? **Không mặc định**; phải truyền cookie ở request sau.
15. Timezone lấy bằng API nào ở Servlet MVC? **`RequestContextUtils.getTimeZone(request)`**, trả null nếu không có.
16. Vì sao cần `Vary: Accept-Language`/cache theo cookie? **Để tránh trả bản dịch của người này cho người khác**.
17. Dùng `LocaleContextHolder` trong một thread pool có luôn đọc được locale request không? **Không chắc**, cần context propagation/truyền tường minh.
18. Khi client gửi `lang=fr` nhưng chỉ có bundles vi/en, có chắc server ném lỗi không? **Không.** Resolver có thể chấp nhận locale, còn MessageSource fallback nếu không có bundle; phải whitelist nếu muốn từ chối.
19. Nếu dịch tiêu đề nhưng HTML vẫn `<html lang="vi">` cho bản tiếng Anh, có vấn đề gì? **Có**, lang không phản ánh nội dung và ảnh hưởng accessibility/SEO.
20. Làm sao chứng minh i18n hoạt động end-to-end? **Test resolver selection, MessageSource output, cookie replay và template render theo locale**.

## 13. Nguồn tham khảo chính thức

- [Spring Framework 6.2 — Locale Resolution](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/localeresolver.html): các resolver, cookie/session, locale change interceptor.
- [LocaleResolver Javadoc](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/web/servlet/LocaleResolver.html): hai phương thức `resolveLocale`/`setLocale`.
- [LocaleContextResolver (Servlet) Javadoc](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/web/servlet/LocaleContextResolver.html): time zone-aware context và thay đổi context.
- [AcceptHeaderLocaleResolver Javadoc](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/web/servlet/i18n/AcceptHeaderLocaleResolver.html): `supportedLocales`, matching, hạn chế `setLocale`.
- [CookieLocaleResolver Javadoc](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/web/servlet/i18n/CookieLocaleResolver.html): cấu hình cookie, default locale/time zone.
- [LocaleChangeInterceptor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/web/servlet/i18n/LocaleChangeInterceptor.html): parameter name, method filter, invalid-locale handling.
- [Spring Boot — Internationalization](https://docs.spring.io/spring-boot/3.3/reference/features/internationalization.html): tự cấu hình MessageSource từ bundles và properties.
- [Thymeleaf + Spring Tutorial](https://www.thymeleaf.org/doc/tutorials/3.1/thymeleafspring.html): message expressions `#{...}`, variables và Spring MVC integration.
- [MDN Accept-Language](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language): HTTP language preference/q-values.

**Ghi nhớ:** `LocaleResolver` chọn **locale** cho request. `LocaleContextResolver` mở rộng bằng **LocaleContext và tùy chọn time zone**. `LocaleChangeInterceptor` chỉ phù hợp với resolver **cho phép thay đổi locale**, như cookie/session. `MessageSource` và template engine mới thực sự tạo ra văn bản bản địa hóa. Phải định nghĩa rõ thứ tự ưu tiên giữa header, cookie, session và profile, đồng thời kiểm thử thay đổi ngôn ngữ qua nhiều request.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="session-localization-006">MessageSource, thông báo validation và bản địa hóa giao diện</a>

<details>
<summary>Xem chi tiết</summary>

# MessageSource, thông báo validation và bản địa hóa giao diện trong Spring MVC

## 1. Mục tiêu và bối cảnh

Trong ứng dụng Spring MVC có form HTML, một màn hình có thể cần hiển thị cùng nội dung bằng tiếng Việt và tiếng Anh. Nhãn trường nhập, nút bấm, thông báo lỗi khi gửi form và thông báo lưu thành công đều cần bản dịch. Nếu viết cứng tất cả chuỗi trong controller hoặc view, mỗi lần hỗ trợ ngôn ngữ mới phải sửa logic ở nhiều nơi.

Spring cung cấp ba lớp trách nhiệm:

1. **Locale** mô tả ngôn ngữ/địa phương đang được sử dụng (ví dụ `vi`, `vi-VN`, `en`).
2. **LocaleResolver** xác định Locale của mỗi HTTP request; `LocaleChangeInterceptor` có thể hỗ trợ người dùng chọn ngôn ngữ qua query parameter khi resolver cho phép.
3. **MessageSource** tìm câu chữ từ các resource bundles theo message key và Locale; Spring MVC/Thymeleaf còn có cơ chế dùng MessageSource để hiện lỗi trong `BindingResult`.

Bài học tập trung vào **Spring Web MVC (Servlet stack)**, `MessageSource`, lỗi validation của form và i18n giao diện server-rendered bằng Thymeleaf. Ví dụ thực thi dùng Java 17, Spring Boot 3.3.5 và Spring Framework 6.1.x.

Một trải nghiệm mong muốn:

```text
GET /contacts/new?lang=vi
→ Tạo liên hệ / Họ và tên / Lưu liên hệ

GET /contacts/new?lang=en
→ Create contact / Name / Save contact

POST /contacts với name quá ngắn
→ vẫn hiển thị form, nhưng validation error được dịch
```

**i18n** là thiết kế ứng dụng có thể phục vụ nhiều ngôn ngữ; **l10n** là bản địa hóa nội dung/định dạng cho từng Locale. `MessageSource` không phải công cụ tự động dịch văn bản: người phát triển phải cung cấp các bản dịch và quy tắc fallback.

## 2. MessageSource và resource bundles

Interface `org.springframework.context.MessageSource` định nghĩa tra cứu message theo code, tham số và Locale:

```java
String getMessage(
        String code, Object[] args,
        String defaultMessage, Locale locale);

String getMessage(
        String code, Object[] args, Locale locale)
        throws NoSuchMessageException;

String getMessage(
        MessageSourceResolvable resolvable, Locale locale)
        throws NoSuchMessageException;
```

Đây là **trích chữ ký API**, chưa phải class Java độc lập có đủ imports. Giả sử tệp tiếng Việt có:

```properties
contact.heading=Tạo liên hệ
contact.saved=Đã lưu liên hệ {0}.
```

Tệp tiếng Anh:

```properties
contact.heading=Create contact
contact.saved=Saved contact for {0}.
```

Gọi:

```java
String vi = messageSource.getMessage(
    "contact.saved", new Object[]{"Lan"}, Locale.forLanguageTag("vi"));
String en = messageSource.getMessage(
    "contact.saved", new Object[]{"Lan"}, Locale.ENGLISH);
```

Kết quả:

```text
Đã lưu liên hệ Lan.
Saved contact for Lan.
```

`{0}` là tham số theo quy tắc `java.text.MessageFormat`. Lưu ý quy tắc escape dấu nháy đơn: trong pattern MessageFormat, hai dấu `''` có thể biểu thị một dấu nháy đơn; phải kiểm thử các bản dịch có apostrophe hoặc dấu ngoặc phức tạp.

Nếu không tìm được key:

- Overload có `defaultMessage` trả fallback được truyền vào.
- Overload không có default có thể ném `NoSuchMessageException`.
- Cấu hình `useCodeAsDefaultMessage` có thể hiện chính code, nhưng dùng nó bừa bãi sẽ che lỗi thiếu bản dịch.

Đừng dùng nội dung câu dịch làm identifier, ví dụ không đặt key là `Tạo liên hệ`. Nên đặt mã ổn định, mang ngữ nghĩa như `contact.heading`, `contact.submit`, `contact.error.email`.

### Spring Boot cấu hình MessageSource

Spring Boot tự cấu hình MessageSource nếu tìm thấy bundle cơ sở theo basename phù hợp, mặc định là `messages`. Có thể tạo:

```text
src/main/resources/
├── application.properties
├── messages.properties
├── messages_en.properties
└── messages_vi.properties
```

**Cần có `messages.properties`**, ngay cả khi phần lớn bản dịch nằm ở `messages_en.properties` và `messages_vi.properties`. Nếu chỉ cung cấp các file có hậu tố locale, auto-configuration của Boot có thể không được kích hoạt.

`application.properties`:

```properties
spring.messages.basename=messages
spring.messages.encoding=UTF-8
spring.messages.fallback-to-system-locale=false
```

- Basename có thể trỏ tới nhiều bundles, ví dụ `messages,validation`.
- UTF-8 giữ dấu tiếng Việt.
- `fallback-to-system-locale=false` giảm phụ thuộc vào locale của hệ điều hành chạy server; vẫn có thể rơi về bundle cơ sở.
- Resource bundle khác với cơ chế translate online: tải một tệp messages không khiến tất cả chuỗi trong database/UI tự động được dịch.

## 3. LocaleResolver và cách chọn ngôn ngữ cho request

Spring MVC giải quyết locale thông qua `LocaleResolver`:

| Resolver | Nguồn xác định locale | Dùng `?lang=vi` để thay đổi được không? |
| --- | --- | --- |
| `AcceptHeaderLocaleResolver` | HTTP `Accept-Language` của client | **Không** hỗ trợ `setLocale` qua interceptor |
| `SessionLocaleResolver` | Session attribute, sau đó fallback | **Có** |
| `CookieLocaleResolver` | Cookie client, sau đó fallback | **Có**, theo cookie policy |

Với ứng dụng chỉ đọc ngôn ngữ do browser gửi, `AcceptHeaderLocaleResolver` thường đủ; không cần tạo session chỉ để đọc header. Khi muốn có nút “Tiếng Việt / English” trên website truyền thống, `SessionLocaleResolver` đi với `LocaleChangeInterceptor` là cách dễ hiểu.

Cảnh báo: nếu chỉ thêm `LocaleChangeInterceptor` nhưng dùng `AcceptHeaderLocaleResolver`, việc đổi `?lang=vi` có thể gây `UnsupportedOperationException` vì server không thể thay HTTP `Accept-Language` trong request của trình duyệt.

Locale không phải timezone hay quốc tịch. Hai người dùng cùng `vi-VN` có thể ở múi giờ khác nhau. `Accept-Language` cũng không chứng minh danh tính người dùng; nó chỉ là preference của client.

## 4. BindingResult và việc bản địa hóa thông báo validation

Một form controller có thể có chữ ký:

```java
@PostMapping("/contacts")
public String submit(
        @Valid @ModelAttribute("contactForm") ContactForm form,
        BindingResult errors) {
    if (errors.hasErrors()) {
        return "contacts/new";
    }
    return "redirect:/contacts/done";
}
```

Đây là **snippet**; controller đầy đủ ở phần 6. Thứ tự diễn ra:

```text
HTTP POST form fields
   |
   v
@ModelAttribute: binding chuỗi từ request vào ContactForm
   |
   v
@Valid: Jakarta Bean Validation kiểm tra constraints
   |
   v
BindingResult chứa FieldError hoặc ObjectError
   |
   v
Controller trả form view khi có lỗi
   |
   v
Thymeleaf th:errors và Spring MessageSource
   |
   v
Thông báo theo Locale của request
```

Điểm dễ nhầm là có **hai con đường tra message**:

1. **Bean Validation message interpolation** do provider (như Hibernate Validator) thực hiện khi xử lý annotation `@NotBlank(message = "...")`. Nguồn bundle chuẩn của Jakarta Validation thường là `ValidationMessages.properties`, hoặc có thể được nối với Spring MessageSource qua `LocalValidatorFactoryBean`.
2. **Spring MessageSource resolution trên `BindingResult`**: `FieldError` mang một danh sách **error codes** ưu tiên, và Thymeleaf `th:errors` có thể resolve các codes đó từ `messages*.properties` theo locale.

Ví dụ DTO có:

```java
@NotBlank
@Size(min = 2, max = 40)
private String name;
```

Lỗi `@NotBlank` cho `contactForm.name` thường có mã theo thứ tự ưu tiên như:

```text
NotBlank.contactForm.name
NotBlank.name
NotBlank.java.lang.String
NotBlank
```

Khi `messages_vi.properties` chứa:

```properties
NotBlank.contactForm.name=Vui lòng nhập họ và tên.
Size.contactForm.name=Tên phải có từ 2 đến 40 ký tự.
```

thì Thymeleaf có thể hiển thị hai thông báo đó theo loại lỗi tương ứng. Không nhất thiết phải viết `message="Vui lòng nhập..."` trực tiếp trên Java DTO.

**Type conversion errors** cũng nằm trong BindingResult. Nếu form có `age` kiểu `int` mà người dùng nhập `xyz`, lỗi có thể mang code `typeMismatch.contactForm.age`; nó khác lỗi `@Min` hoặc `@NotNull`. Dịch lỗi validation cần hiểu mã nào thực sự được tạo ra.

`BindingResult` phải đứng **ngay sau** tham số `@Valid @ModelAttribute` liên quan nếu controller muốn nhận lỗi tại method. Nếu form sai và vẫn redirect, request mới sẽ không tự giữ BindingResult — cách đơn giản là render lại cùng view, giữ lỗi trong request hiện tại.

## 5. Phân biệt MessageSource arguments và validation placeholders

Thông điệp từ Spring MessageSource dùng `{0}`, `{1}`... theo MessageFormat:

```properties
contact.saved=Saved contact for {0}.
```

Bean Validation templates có thể dùng placeholders theo constraints, ví dụ `{min}`, `{max}`:

```java
@Size(min = 2, max = 40, message = "Length must be {min} to {max}")
private String name;
```

Nếu dùng Spring MessageSource để cấp messages cho Bean Validation, quá trình nội suy của provider và cách xử lý MessageFormat có thể tương tác. Đừng giả định việc đưa `{min}` vào bất kỳ file `messages.properties` nào cũng đồng nghĩa với `MessageSource.getMessage` tự biết giá trị `min`. Trong ví dụ runnable bên dưới, các lỗi hiển thị trên view dùng **Spring FieldError codes**, với thông báo tĩnh đầy đủ, để việc phân giải dễ đoán và kiểm chứng.

## 6. Ứng dụng chạy được: Java 17 + Spring MVC + Thymeleaf

Ứng dụng tạo liên hệ, cho phép đổi hai ngôn ngữ qua `?lang=vi`/`?lang=en`, dịch nhãn form và lỗi. Không dùng database; khi form hợp lệ, ứng dụng chỉ mô phỏng thao tác lưu rồi redirect.

### 6.1. Cấu trúc dự án

```text
mvc-i18n-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/example/i18n/
    │   │   ├── I18nApplication.java
    │   │   ├── LocaleConfig.java
    │   │   ├── ContactForm.java
    │   │   └── ContactController.java
    │   └── resources/
    │       ├── application.properties
    │       ├── messages.properties
    │       ├── messages_en.properties
    │       ├── messages_vi.properties
    │       └── templates/contacts/
    │           ├── new.html
    │           └── done.html
    └── test/java/example/i18n/
        └── I18nWebTest.java
```

**`pom.xml`**

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
    <artifactId>mvc-i18n-demo</artifactId>
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
```

**`src/main/java/example/i18n/I18nApplication.java`**

```java
package example.i18n;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class I18nApplication {
    public static void main(String[] args) {
        SpringApplication.run(I18nApplication.class, args);
    }
}
```

### 6.2. Cấu hình chọn ngôn ngữ qua session

**`src/main/java/example/i18n/LocaleConfig.java`**

```java
package example.i18n;

import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor change = new LocaleChangeInterceptor();
        change.setParamName("lang");
        change.setHttpMethods("GET");
        registry.addInterceptor(change);
    }
}
```

Bean tên `localeResolver` cho phép MVC dùng `SessionLocaleResolver`. Khi người dùng truy cập `GET /contacts/new?lang=vi`, interceptor cập nhật locale của session. Mọi request sau cùng session (có cookie phù hợp) sẽ dùng locale đó. Default English cố định giúp ví dụ không phụ thuộc locale của máy chạy.

Nếu ứng dụng là REST stateless, việc đọc `Accept-Language` trực tiếp thường thích hợp hơn session. Nếu chỉ hỗ trợ `vi`/`en`, nên kiểm tra/giới hạn locale được phép từ input ở tầng thiết kế thật, thay vì cho mọi mã locale bất kỳ.

### 6.3. DTO và controller

**`src/main/java/example/i18n/ContactForm.java`**

```java
package example.i18n;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ContactForm {
    @NotBlank
    @Size(min = 2, max = 40)
    private String name;

    @NotBlank
    @Email
    private String email;

    public ContactForm() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
```

**`src/main/java/example/i18n/ContactController.java`**

```java
package example.i18n;

import java.util.Locale;
import java.util.Map;

import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/contacts")
public class ContactController {
    private final MessageSource messages;

    public ContactController(MessageSource messages) {
        this.messages = messages;
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("contactForm", new ContactForm());
        return "contacts/new";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("contactForm") ContactForm form,
            BindingResult errors,
            RedirectAttributes redirectAttributes) {

        if (errors.hasErrors()) {
            return "contacts/new";
        }

        // Demo: không có repository, mô phỏng lưu thành công.
        redirectAttributes.addFlashAttribute(
                "savedName", form.getName().strip());
        return "redirect:/contacts/done";
    }

    @GetMapping("/done")
    public String done() {
        return "contacts/done";
    }

    @GetMapping("/label")
    @ResponseBody
    public Map<String, String> label(Locale locale) {
        String heading = messages.getMessage(
                "contact.heading", null, locale);
        return Map.of("heading", heading);
    }
}
```

Khi POST invalid, Spring giữ `ContactForm` và `BindingResult` trong model của request; controller render lại form. Khi hợp lệ, redirect tránh việc refresh gửi lại POST, còn flash `savedName` dành cho GET liền sau. Vì ứng dụng không lưu dữ liệu bền vững, endpoint done chỉ là ví dụ màn hình thông báo, không phải hệ thống tạo liên hệ thật.

Endpoint `GET /contacts/label` minh họa gọi `MessageSource` trực tiếp với `Locale` do MVC resolve. `@ResponseBody` là cần thiết vì class là `@Controller` và method này muốn trả JSON, không phải view.

### 6.4. Tất cả resource bundles

**`src/main/resources/application.properties`**

```properties
spring.messages.basename=messages
spring.messages.encoding=UTF-8
spring.messages.fallback-to-system-locale=false
```

**`src/main/resources/messages.properties`** — fallback English:

```properties
contact.heading=Create contact
contact.name=Name
contact.email=Email
contact.submit=Save contact
contact.done=Completed
contact.saved=Saved contact for {0}.
NotBlank.contactForm.name=Name is required.
Size.contactForm.name=Name must have 2 to 40 characters.
NotBlank.contactForm.email=Email is required.
Email.contactForm.email=Enter a valid email address.
```

**`src/main/resources/messages_en.properties`**

```properties
contact.heading=Create contact
contact.name=Name
contact.email=Email
contact.submit=Save contact
contact.done=Completed
contact.saved=Saved contact for {0}.
NotBlank.contactForm.name=Name is required.
Size.contactForm.name=Name must have 2 to 40 characters.
NotBlank.contactForm.email=Email is required.
Email.contactForm.email=Enter a valid email address.
```

**`src/main/resources/messages_vi.properties`**

```properties
contact.heading=Tạo liên hệ
contact.name=Họ và tên
contact.email=Địa chỉ email
contact.submit=Lưu liên hệ
contact.done=Hoàn tất
contact.saved=Đã lưu liên hệ {0}.
NotBlank.contactForm.name=Vui lòng nhập họ và tên.
Size.contactForm.name=Tên phải có từ 2 đến 40 ký tự.
NotBlank.contactForm.email=Vui lòng nhập email.
Email.contactForm.email=Email không đúng định dạng.
```

Tên `contactForm` trong `NotBlank.contactForm.name` **phải trùng** tên model attribute truyền ở controller. Lỗi `@Size` cho name và `@Email` cho email sẽ có keys tương ứng. Một trường nhập sai có thể phát sinh nhiều errors; đừng mặc định lúc nào cũng chỉ có một `FieldError`.

### 6.5. Template Thymeleaf với messages và lỗi validation

**`src/main/resources/templates/contacts/new.html`**

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title th:text="#{contact.heading}">Create contact</title>
</head>
<body>
    <nav>
        <a th:href="@{/contacts/new(lang='vi')}">Tiếng Việt</a>
        <a th:href="@{/contacts/new(lang='en')}">English</a>
    </nav>
    <h1 th:text="#{contact.heading}">Create contact</h1>

    <form th:action="@{/contacts}"
          th:object="${contactForm}" method="post">
        <label for="name" th:text="#{contact.name}">Name</label>
        <input id="name" th:field="*{name}" type="text">
        <p th:if="${#fields.hasErrors('name')}"
           th:errors="*{name}">Name error</p>

        <label for="email" th:text="#{contact.email}">Email</label>
        <input id="email" th:field="*{email}" type="email">
        <p th:if="${#fields.hasErrors('email')}"
           th:errors="*{email}">Email error</p>

        <button type="submit" th:text="#{contact.submit}">
            Save contact
        </button>
    </form>
</body>
</html>
```

Ba kiểu biểu thức của Thymeleaf cần nhớ:

- `#{contact.heading}` tra message key trong bundles, dùng locale hiện tại.
- `${contactForm}` tham chiếu object trong Spring model.
- `*{name}` là một property của object do `th:object` chọn.

`th:field` giúp bind input, giữ giá trị khi form bị trả lại sau lỗi. `#fields.hasErrors('name')` và `th:errors="*{name}"` dựa trên Spring BindingResult, hiển thị thông báo sau khi resolve qua MessageSource.

Đây là trang demo có `html lang="en"` cố định; khi triển khai thực, cần gán lang phản ánh locale đang hiển thị, ví dụ expose `htmlLang` từ controller rồi dùng `th:lang="${htmlLang}"`. Điều này quan trọng với screen reader/accessibility, dù không ngăn message lookup hoạt động trong demo.

**`src/main/resources/templates/contacts/done.html`**

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title th:text="#{contact.done}">Completed</title>
</head>
<body>
    <h1 th:text="#{contact.done}">Completed</h1>
    <p th:if="${savedName}"
       th:text="#{contact.saved(${savedName})}">
       Saved contact
    </p>
    <a th:href="@{/contacts/new}"
       th:text="#{contact.heading}">Create contact</a>
</body>
</html>
```

`#{contact.saved(${savedName})}` đưa biến `savedName` vào placeholder `{0}` của message. Template dùng `th:text` để escape HTML; không dùng `th:utext` cho chuỗi do người dùng nhập. Flash attribute chỉ sống tạm sau redirect: khi refresh trang done, thông báo không bắt buộc xuất hiện lần nữa.

## 7. Chạy thử và đầu ra mong đợi

Ở thư mục chứa `pom.xml`, dùng JDK 17+ và Maven:

```bash
mvn test
mvn spring-boot:run
```

Hai lệnh thực hiện **tuần tự**. Sau khi chạy, mở:

```text
http://localhost:8080/contacts/new?lang=vi
```

Các phần text chính:

```text
Tiếng Việt  English
Tạo liên hệ
Họ và tên
Địa chỉ email
Lưu liên hệ
```

Chọn liên kết English; cùng session, phần giao diện đổi thành:

```text
Tiếng Việt  English
Create contact
Name
Email
Save contact
```

Thử POST với `name=A` và `email=not-an-email`. `A` có một ký tự, vi phạm `@Size(min=2,max=40)`; `not-an-email` vi phạm `@Email`. Với locale tiếng Việt, các errors hiển thị theo bundle:

```text
Tên phải có từ 2 đến 40 ký tự.
Email không đúng định dạng.
```

Nếu form hợp lệ như `name=Lan`, `email=lan@example.com`, server trả redirect tới `/contacts/done`. Trên trang kết quả GET tiếp theo, thông báo dự kiến theo locale tiếng Việt là:

```text
Đã lưu liên hệ Lan.
```

Với locale English, kết quả tương ứng là:

```text
Saved contact for Lan.
```

Lưu ý browser phải giữ session cookie. Nếu đổi `?lang=vi` bằng trình duyệt A rồi dùng curl không có cookie của trình duyệt A, request curl có thể trở lại locale mặc định English.

### Gọi MessageSource trực tiếp qua API JSON minh họa

`GET /contacts/label` trả một JSON object:

```json
{"heading":"Tạo liên hệ"}
```

hoặc:

```json
{"heading":"Create contact"}
```

Tùy session Locale. Dùng curl với cookie jar trên Bash:

```bash
curl -s -c cookies.txt -b cookies.txt \
  'http://localhost:8080/contacts/new?lang=vi' > /dev/null

curl -s -b cookies.txt \
  'http://localhost:8080/contacts/label'
```

Response body thường là `{"heading":"Tạo liên hệ"}`. Trong PowerShell, cách gửi session cookies khác và `/dev/null` không phải đường null output chuẩn; có thể dùng `Invoke-WebRequest` với `-WebSession`.

## 8. MockMvc test: hai ngôn ngữ và validation

**`src/test/java/example/i18n/I18nWebTest.java`**

```java
package example.i18n;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class I18nWebTest {
    @Autowired MockMvc mvc;
    @Autowired MessageSource messages;

    @Test
    void messagesExistInEnglishAndVietnamese() {
        assertEquals("Create contact", messages.getMessage(
                "contact.heading", null, Locale.ENGLISH));
        assertEquals("Tạo liên hệ", messages.getMessage(
                "contact.heading", null, Locale.forLanguageTag("vi")));
    }

    @Test
    void vietnameseFormRendersTranslatedHeading() throws Exception {
        mvc.perform(get("/contacts/new").param("lang", "vi"))
           .andExpect(status().isOk())
           .andExpect(view().name("contacts/new"))
           .andExpect(content().string(containsString("Tạo liên hệ")));
    }

    @Test
    void englishFormRendersTranslatedHeading() throws Exception {
        mvc.perform(get("/contacts/new").param("lang", "en"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Create contact")));
    }

    @Test
    void invalidFormRendersVietnameseMessages() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc
                .perform(get("/contacts/new").param("lang", "vi"))
                .andReturn().getRequest().getSession(false);

        mvc.perform(post("/contacts").session(session)
                        .param("name", "A")
                        .param("email", "not-an-email"))
           .andExpect(status().isOk())
           .andExpect(model().attributeHasFieldErrors(
                   "contactForm", "name", "email"))
           .andExpect(content().string(containsString(
                   "Tên phải có từ 2 đến 40 ký tự.")))
           .andExpect(content().string(containsString(
                   "Email không đúng định dạng.")));
    }

    @Test
    void validFormRedirects() throws Exception {
        mvc.perform(post("/contacts")
                        .param("name", "Lan")
                        .param("email", "lan@example.com"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/contacts/done"));
    }
}
```

Chạy `mvn test`. Class có **5 test methods**; nếu mọi test pass, Maven trả exit code `0`. Không dựa vào thứ tự chạy test, thông báo console hay thời gian. Test validation tạo một session qua GET `?lang=vi`, sau đó gửi POST **cùng session** để kiểm tra bản dịch thật ở view.

Trong dự án thực, nên bổ sung test từng message key được dịch đủ, missing-key fallback, locale không hỗ trợ, apostrophes, UI accessibility, việc đổi ngôn ngữ qua redirect, và các hình thức validation khác như `typeMismatch`.

## 9. Khi cần bản địa hóa thông báo ngay từ Bean Validation provider

Ở ví dụ form, lỗi hiển thị thông qua Spring `BindingResult` codes. Nếu bạn muốn lấy bản dịch **ngay tại `ConstraintViolation.getMessage()`** hoặc muốn annotation dùng custom template:

```java
@NotBlank(message = "{contact.name.required}")
private String name;
```

thì phải hiểu **Jakarta Bean Validation message interpolation** là cơ chế riêng. Theo thông lệ, provider đọc `ValidationMessages.properties` cùng các biến thể locale; Spring có thể kết nối `MessageSource` bằng `LocalValidatorFactoryBean.setValidationMessageSource(...)`.

Snippet cấu hình **tùy chọn**, dành cho dự án có nhu cầu này (không bắt buộc để chạy ví dụ phần 6):

```java
@Bean
public LocalValidatorFactoryBean applicationValidator(
        MessageSource messageSource) {
    LocalValidatorFactoryBean validator =
            new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(messageSource);
    return validator;
}
```

Imports cần thiết:

```java
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
```

Khi dùng cấu hình tùy chọn này, bảo đảm MVC `@Valid` thật sự sử dụng validator đã được cấu hình (tùy ứng dụng có thể cần `WebMvcConfigurer.getValidator()`), không vô ý có nhiều validator beans cạnh tranh; phải chạy integration tests. Không cấu hình đồng thời `setValidationMessageSource` và `setMessageInterpolator` trên cùng factory khi hai lựa chọn không tương thích.

Thêm key vào các bundle:

```properties
contact.name.required=Please enter your name.
```

```properties
contact.name.required=Vui lòng nhập họ và tên.
```

Hai đoạn trên lần lượt thuộc `messages_en.properties` và `messages_vi.properties` khi áp dụng tùy chọn. Spring có thể dùng `LocaleContextHolder` của request cho message interpolation qua provider được tích hợp; nếu bạn dùng custom `ValidatorFactory` ở ngoài Spring hoặc xử lý async, locale phải được truyền/cấu hình đúng.

**Không nên suy luận** rằng mọi chuỗi của `FieldError.getDefaultMessage()` chắc chắn được locale hóa chỉ vì Thymeleaf hiển thị lỗi đúng. Với REST API, muốn một thông điệp cụ thể theo locale, có thể lấy `MessageSource.getMessage(error, locale)` (với `error` là `ObjectError` hoặc `FieldError` — tức `MessageSourceResolvable`), rồi trả một error DTO có `code` ổn định và `message` là bản dịch. Nếu kế thừa `ResponseEntityExceptionHandler`, hãy override handler thích hợp thay vì đăng ký `@ExceptionHandler` trùng với exception mà base class đã xử lý.

## 10. Formatting khác với translating

`MessageSource` phục vụ dịch **văn bản**. Khi hiển thị ngày, giờ, số hoặc tiền, dùng formatter/converter phù hợp. Ví dụ Java 17 hoàn chỉnh, chạy độc lập:

**`LocaleNumberDemo.java`**

```java
import java.text.NumberFormat;
import java.util.Locale;

public class LocaleNumberDemo {
    public static void main(String[] args) {
        double number = 1234.5;
        NumberFormat us = NumberFormat.getNumberInstance(Locale.US);
        NumberFormat germany = NumberFormat.getNumberInstance(Locale.GERMANY);
        us.setMinimumFractionDigits(2);
        germany.setMinimumFractionDigits(2);
        System.out.println(us.format(number));
        System.out.println(germany.format(number));
    }
}
```

Chạy:

```bash
javac LocaleNumberDemo.java
java LocaleNumberDemo
```

Kết quả điển hình trên JDK với CLDR locale data:

```text
1,234.50
1.234,50
```

Ví dụ dùng `double` chỉ để nhìn dấu số. Với tiền thật, nên dùng `BigDecimal` và quy tắc currency/rounding rõ ràng. Đừng đổi ý nghĩa `VND` thành `USD` chỉ bằng cách thay ký hiệu tiền.

Trong Spring MVC, `@DateTimeFormat`, `@NumberFormat`, `Formatter` và `ConversionService` hỗ trợ form binding/formatting; chúng giải quyết việc khác với tra message code.

## 11. Những giới hạn trong ứng dụng thực tế

### 11.1. Chọn ngôn ngữ không thay thế security

`lang` là dữ liệu request, có thể bị sửa tùy ý. Không coi locale là dấu hiệu quyền, quốc tịch hoặc phân vùng tenant. Nếu dùng session/cookie, cần hiểu CSRF, session lifetime, cache policy, và trạng thái người dùng. Nếu website cần ghi nhớ locale qua nhiều thiết bị, hãy cân nhắc preference trên tài khoản.

Với `spring-boot-starter-security` và session login, POST form có thể cần CSRF token; ví dụ không cài Spring Security nên không triển khai token. Đừng tắt CSRF ở ứng dụng thật chỉ để cho ví dụ POST chạy.

### 11.2. HTML escaping và accessibility

Thymeleaf `th:text` escape HTML đầu ra, giúp tránh nhiều lỗi XSS khi chèn dữ liệu người dùng vào câu dịch. Đừng dùng `th:utext` với nội dung chưa được sanitize. Cần thiết kế HTML `lang` theo locale thực, liên kết lỗi bằng `aria-describedby`/`aria-invalid` và kiểm tra với screen readers; một giao diện chỉ đổi text nhưng sai lang metadata vẫn là localization chưa hoàn chỉnh.

### 11.3. Caching và response representation

Nếu HTML/JSON phụ thuộc `Accept-Language`, CDN/reverse proxy có thể cần `Vary: Accept-Language` hoặc cache key theo locale; nếu locale trong session/cookie thì thường cần xử lý caching phù hợp dữ liệu cá nhân hóa. Không dùng một public cache key cho các response khác ngôn ngữ rồi để user A nhìn nội dung của user B.

### 11.4. Background jobs và thread context

`LocaleContextHolder` thường gắn locale vào thread request. Tác vụ gửi email chạy ở worker khác **không mặc nhiên nhận locale của request ban đầu**; cần truyền Locale của người nhận vào hàm xây nội dung. Đừng dùng locale mặc định của máy chủ để dịch email cho tất cả người dùng.

### 11.5. Pluralization và ngữ cảnh

`MessageFormat` hỗ trợ tham số nhưng không tự giải quyết mọi số nhiều, giống và ngữ pháp. Với ngôn ngữ phức tạp, cân nhắc ICU MessageFormat hoặc message variants phù hợp. Không ghép ngẫu nhiên từ đã dịch thành câu, vì thứ tự từ và văn phạm khác nhau giữa các ngôn ngữ.

## 12. Các lỗi thường gặp

| Hiện tượng | Nguyên nhân | Hướng kiểm tra |
| --- | --- | --- |
| UI hiện `contact.heading` | Không có key hoặc resolver không tìm bundle | Kiểm tra basename, classpath, key, fallback |
| Chỉ có `messages_vi.properties`, không tự cấu hình MessageSource | Thiếu bundle mặc định `messages.properties` | Thêm base file và test bean |
| Tiếng Việt bị lỗi dấu | Sai encoding | Lưu UTF-8, đặt `spring.messages.encoding=UTF-8` |
| `?lang=vi` gây exception | Interceptor đổi locale trên `AcceptHeaderLocaleResolver` | Dùng `SessionLocaleResolver` hoặc resolver có setLocale |
| Form dùng locale khác validation message | Message source/validator locale khác, session không được gửi | Kiểm tra `BindingResult.codes`, locale resolver và cookie |
| `th:errors` hiện tiếng Anh dù label tiếng Việt | Thiếu key `Size.contactForm.name` hoặc tên model object sai | Kiểm tra `FieldError.getCodes()` và bundle |
| Không hiện lỗi sau submit | `BindingResult` sai vị trí hoặc controller redirect khi invalid | Đặt kế tiếp `@Valid`, render lại view |
| `@NotBlank(message="{key}")` hiện nguyên key | Jakarta validator không thấy bundle dùng cho interpolation | Dùng `ValidationMessages*.properties`/`LocalValidatorFactoryBean` |
| Error JSON không được dịch | Trả `getDefaultMessage()` trực tiếp | Dùng `MessageSource.getMessage(error, locale)` hoặc cấu hình validator |
| Chỉ đổi một page, page khác không đổi | Hardcode strings còn sót, cache hoặc session khác | Kiểm tra tất cả templates, bundles, HTTP headers |
| MessageFormat làm mất dấu apostrophe | Escape theo cú pháp single quote | Dùng `''` và viết tests |
| Flash message không còn sau refresh | Flash chỉ để chuyển dữ liệu ngắn hạn sang GET kế tiếp | Không dùng flash làm state bền vững |
| Trả nguyên `error.getRejectedValue()` trong lỗi | Echo dữ liệu nhạy cảm | Chỉ trả field/code/message an toàn, log tối thiểu |
| Định dạng số đúng nhưng đơn vị tiền sai | Locale bị dùng thay cho currency nghiệp vụ | Mang currency riêng và định dạng theo business context |

Cách debug có hệ thống:

1. In/kiểm tra Locale do `LocaleResolver` trả về.
2. Xác nhận đủ `messages.properties`, `messages_en.properties`, `messages_vi.properties` trên classpath.
3. Kiểm tra `spring.messages.basename` và encoding.
4. Unit-test `messageSource.getMessage("contact.heading", null, locale)`.
5. Đối với validation, inspect `BindingResult.getFieldError("name").getCodes()` và các key được tìm trong bundle.
6. Phân biệt `FieldError.getDefaultMessage()` và `MessageSource.getMessage(error, locale)`.
7. Render HTML và kiểm tra `th:errors` theo hai locale, không chỉ đọc JSON/Java object.
8. Giữ cùng session khi đổi ngôn ngữ qua `?lang=...`; test cả fallback locale.
9. Kiểm tra chỗ nào còn hardcode, bị cache, hoặc sử dụng locale từ background thread sai ngữ cảnh.
10. Khi có API công khai, định nghĩa error code ổn định; đừng bắt client parse nội dung đã dịch.

## 13. Checklist trước khi triển khai i18n

1. Các message keys được đặt theo domain/action, không phụ thuộc trực tiếp vào câu đã dịch.
2. Bundle cơ sở luôn có; bundles cho các ngôn ngữ được hỗ trợ tương đối đầy đủ.
3. Mọi file được lưu UTF-8; test dấu tiếng Việt/apostrophe.
4. Resolver được chọn đúng theo nhu cầu: browser header, session hoặc cookie.
5. Language switch không xung đột với `AcceptHeaderLocaleResolver`.
6. Validation tests assert đúng field code và message theo locale.
7. Logic POST hợp lệ dùng PRG khi phù hợp, form lỗi giữ `BindingResult`.
8. Error JSON tách machine-readable code khỏi localized text.
9. HTML `lang`, labels và errors hỗ trợ accessibility.
10. Ngày/tiền/đơn vị và timezone được định dạng theo quy tắc riêng.
11. Caching, CSRF, authentication và session được kiểm tra riêng.
12. Background jobs nhận Locale người dùng rõ ràng, không phụ thuộc default server thread.

## 14. Bài tập tự kiểm tra

1. `LocaleResolver` và `MessageSource` khác nhau ra sao?
2. Vì sao Spring Boot cần bundle mặc định `messages.properties`?
3. `#{contact.heading}` và `${contactForm}` khác gì nhau trong Thymeleaf?
4. Vì sao `NotBlank.contactForm.name` dùng cho `BindingResult` không hoàn toàn giống `@NotBlank(message="{contact.name.required}")`?
5. Vì sao `?lang=vi` có thể lỗi nếu dùng `AcceptHeaderLocaleResolver`?
6. Khi validation fail, làm sao giữ các lỗi trên trang HTML?
7. Vì sao nên test locale bằng cùng session nếu dùng `SessionLocaleResolver`?
8. `MessageSource.getMessage(error, locale)` hữu ích thế nào khi tạo JSON error response?
9. Vì sao Locale không tự quyết định timezone và đơn vị tiền?
10. Khi gửi email từ async job, bạn nên lấy Locale từ đâu?

**Gợi ý:** (1) Resolver chọn Locale, MessageSource trả văn bản. (2) Làm fallback và điều kiện auto-configuration. (3) Message key và model attribute. (4) Spring field error codes so với Jakarta Bean Validation interpolation. (5) Header không sửa được từ server. (6) Dùng `BindingResult` ngay sau `@Valid @ModelAttribute` rồi render cùng form. (7) Lựa chọn ngôn ngữ được lưu trong session. (8) Tra thông báo từ error codes theo locale, thay vì dùng defaultMessage tùy provider. (9) Cần giữ dữ liệu nghiệp vụ/currency/timezone riêng. (10) Lưu user preference hoặc truyền Locale tường minh cho worker.

## 15. Tài liệu chính thức

- [Spring Framework — MessageSource interface](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/MessageSource.html)
- [Spring Framework — MVC Locale Resolution](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/localeresolver.html)
- [Spring Framework — Binding and Validation](https://docs.spring.io/spring-framework/reference/core/validation/validator.html)
- [Spring Framework — LocalValidatorFactoryBean](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/validation/beanvalidation/LocalValidatorFactoryBean.html)
- [Spring Framework — SessionLocaleResolver](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/servlet/i18n/SessionLocaleResolver.html)
- [Spring Framework — LocaleChangeInterceptor](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/servlet/i18n/LocaleChangeInterceptor.html)
- [Spring Boot — Internationalization](https://docs.spring.io/spring-boot/reference/features/internationalization.html)
- [Thymeleaf — Using Thymeleaf: Message Expressions](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html)
- [Jakarta Bean Validation 3.0](https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html)

**Tổng kết:** Spring MVC dùng `LocaleResolver` để xác định ngôn ngữ request, `MessageSource` để lấy văn bản theo code, và `BindingResult`/Thymeleaf để hiển thị lỗi form đã bản địa hóa. Cần hiểu sự khác nhau giữa việc resolve `FieldError` và nội suy message của Bean Validation provider; một ứng dụng đa ngôn ngữ hoàn chỉnh phải xử lý cả fallback, encoding, HTTP sessions, accessibility và test thật ở từng locale.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="session-localization-007">RequestContextHolder, LocaleContextHolder và giới hạn ThreadLocal</a>

<details>
<summary>Xem chi tiết</summary>

# RequestContextHolder, LocaleContextHolder và giới hạn ThreadLocal trong Spring MVC

## 1. Mục tiêu và bối cảnh

Trong một HTTP request, nhiều thành phần cần truy cập **ngữ cảnh hiện tại**: request attributes, thông tin locale, múi giờ, ngôn ngữ dùng dịch thông điệp hoặc mã correlation ID. Spring MVC giúp gắn một phần ngữ cảnh này với **luồng Java đang xử lý request**. Hai API thường gặp là:

- `org.springframework.web.context.request.RequestContextHolder`: lấy `RequestAttributes` của request đang gắn với luồng hiện tại.
- `org.springframework.context.i18n.LocaleContextHolder`: lấy `LocaleContext`, `Locale` và đôi khi `TimeZone` đang gắn với luồng hiện tại.

Các holder rất tiện, nhưng **không phải kho lưu trạng thái toàn cục, không phải session, không phải API truyền context qua mọi executor**, và không thay thế dependency rõ ràng trong business logic.

Sau bài này bạn sẽ:

1. Phân biệt request attributes, servlet request, locale, timezone và HTTP session.
2. Sử dụng `getRequestAttributes()`, `currentRequestAttributes()`, `ServletRequestAttributes`, `LocaleContextHolder.getLocale()` đúng chỗ.
3. Hiểu `DispatcherServlet`, `RequestContextFilter` và `RequestContextListener` gắn/thu hồi context thế nào.
4. Phân biệt `ThreadLocal`, `InheritableThreadLocal`, `@Async`, `CompletableFuture`, servlet async `Callable` và request đã kết thúc.
5. Biết khi nào nên **truyền dữ liệu cần thiết một cách tường minh** thay vì cố sao chép `HttpServletRequest`/`RequestAttributes` vào worker thread.
6. Đọc ngôn ngữ từ `Accept-Language`, hiểu `LocaleResolver` và `MessageSource`, không nhầm locale với timezone.
7. Thực hành mã **Java 17+, Spring MVC/Spring Boot 3.x**, `curl` và MockMvc; chẩn đoán lỗi thiếu context và ngăn rò rỉ giữa request.

Bài tập trung **Spring MVC trên Servlet stack**, Spring Framework 6.x. Spring WebFlux dùng Reactor Context, không nên dùng hai ThreadLocal holder này làm nền tảng để truyền context trong reactive pipeline. Một số API thay đổi giữa Spring Boot 3 và 4; kiểm tra tài liệu đúng phiên bản đang chạy.

## 2. Bức tranh HTTP request và context gắn với luồng

```text
Browser/API client
      |
      | GET /context/info
      | Accept-Language: vi-VN
      v
Servlet container
      |
      v
Servlet Filter chain
      |
      v
DispatcherServlet
      |  - gắn RequestAttributes vào RequestContextHolder
      |  - gắn LocaleContext vào LocaleContextHolder
      |  - tìm controller
      v
Controller / Service (trong luồng request đang được gắn)
      |  - RequestContextHolder.getRequestAttributes()
      |  - LocaleContextHolder.getLocale()
      v
MessageSource / response DTO / view
      |
      v
DispatcherServlet hoàn tất dispatch
      |  - khôi phục/thu hồi thread-bound context
      v
Filter chain -> container -> client
```

Đây là mô hình **luồng đồng bộ thông thường**. Khi có servlet async, forward/error dispatch, executor hoặc filter tùy chỉnh, vòng đời gắn/thu hồi context có thể khác. `DispatcherServlet` và các thành phần khung quản lý request context theo vòng đời request/dispatch chứ không cho phép lưu tùy ý qua request khác.

Một ứng dụng Servlet thường dùng **thread pool**: luồng từng xử lý request A có thể xử lý request B sau này. Vì thế framework phải khôi phục/clear thread-bound state đúng lúc. Nếu tự gắn `ThreadLocal` mà quên `remove()` hoặc `finally`, request B có thể quan sát thông tin cũ của request A — đây vừa là bug vừa có thể là sự cố rò rỉ dữ liệu.

## 3. RequestContextHolder là gì?

`RequestContextHolder` là holder tĩnh của Spring Web, lưu một instance `RequestAttributes` theo thread. `RequestAttributes` là abstraction có scope/attribute operations; với servlet web request, implementation phổ biến là `ServletRequestAttributes`.

Phương thức quan trọng:

| API | Hành vi |
| --- | --- |
| `RequestContextHolder.getRequestAttributes()` | Trả `RequestAttributes` đang bind hoặc **null** khi không có |
| `RequestContextHolder.currentRequestAttributes()` | Trả context đang bind; nếu không có có thể fallback JSF context (nếu có), sau đó ném `IllegalStateException` khi thật sự không tìm được |
| `RequestContextHolder.setRequestAttributes(attrs)` | Gắn context vào **thread hiện tại**, không truyền kế thừa sang thread con |
| `setRequestAttributes(attrs, true)` | Gắn theo `InheritableThreadLocal`; có rủi ro lớn với thread pool |
| `resetRequestAttributes()` | Xóa request attributes ở thread hiện tại |

Spring lưu hai holder nội bộ: một `ThreadLocal` thông thường và một `InheritableThreadLocal` cho tùy chọn kế thừa. Gọi `setRequestAttributes(attrs, false)` dùng binding không kế thừa, và xóa binding chế độ còn lại trong thread đó.

### 3.1. Khi dùng trong controller

```java
@GetMapping("/info")
public String info() {
    RequestAttributes attrs =
            RequestContextHolder.currentRequestAttributes();
    return attrs.getClass().getSimpleName();
}
```

Đây là **method minh họa** cần đặt trong `@RestController` cùng các imports. Trong Spring MVC Servlet request thông thường, `attrs` thường là `ServletRequestAttributes`. Tuy nhiên, **đừng cast vô điều kiện trong mã thư viện dùng chung**: trong test, JSF hoặc hạ tầng khác, implementation có thể không phải Servlet.

### 3.2. Truy cập HttpServletRequest khi thực sự cần

```java
RequestAttributes attrs = RequestContextHolder.getRequestAttributes();

if (attrs instanceof ServletRequestAttributes servletAttrs) {
    HttpServletRequest request = servletAttrs.getRequest();
    String uri = request.getRequestURI();
}
```

Đây là đoạn minh họa **bên trong một method** với imports `jakarta.servlet.http.HttpServletRequest`, `RequestAttributes`, `RequestContextHolder` và `ServletRequestAttributes`; không phải tệp Java độc lập.

Nếu code thực sự cần request trong controller, cách dễ test và minh bạch hơn thường là **khai báo method parameter**:

```java
@GetMapping("/path")
public String path(HttpServletRequest request) {
    return request.getRequestURI();
}
```

Spring MVC có thể inject `HttpServletRequest` vào argument. Không cần đi vòng qua một static holder khi dependency có thể được truyền trực tiếp. Tương tự, service chỉ cần request ID hoặc user ID nên nhận **giá trị đó** qua tham số, không nhận toàn bộ servlet request.

### 3.3. RequestAttributes không phải session

`RequestAttributes.SCOPE_REQUEST` biểu diễn thuộc tính của request; `SCOPE_SESSION` liên quan phiên HTTP nếu có. Đừng nhầm một thread-bound holder với dữ liệu session sống lâu hơn request.

```java
attrs.setAttribute(
    "requestId", "R-123", RequestAttributes.SCOPE_REQUEST);
Object id = attrs.getAttribute(
    "requestId", RequestAttributes.SCOPE_REQUEST);
```

Hai dòng này là minh họa hoạt động trong request. Giá trị request-scope thường tồn tại theo **vòng đời request**, không thể an toàn giữ để dùng trong job chạy sau response. Thuộc tính session có vòng đời và quy tắc đồng thời riêng; việc dùng holder không tự tạo session id hay thay thế cơ chế session management.

### 3.4. currentRequestAttributes và getRequestAttributes khác nhau

Nếu helper có thể gọi trong cả request và unit test/scheduled job, ưu tiên:

```java
RequestAttributes attributes =
        RequestContextHolder.getRequestAttributes();

if (attributes == null) {
    // Không có request context. Chọn hành vi fallback có chủ đích.
}
```

`currentRequestAttributes()` phù hợp khi **request context là điều kiện bắt buộc**, và bạn muốn fail fast nếu đang ở sai lifecycle. Nó thường ném `IllegalStateException` với thông tin rằng không có thread-bound request. Bắt `IllegalStateException` rồi giả vờ có request rỗng sẽ che bug kiến trúc.

Không viết `RequestContextHolder.currentRequestAttributes()` trong constructor của singleton bean khởi tạo lúc startup; tại thời điểm đó không có một HTTP request đang xử lý.

## 4. Ai gắn request context: DispatcherServlet, Filter hay Listener?

Trong Spring MVC, `DispatcherServlet`/`FrameworkServlet` đã tự expose request attributes trong quá trình xử lý; **không phải cứ muốn dùng RequestContextHolder là phải đăng ký thêm filter**.

Ba lựa chọn thường gặp:

| Thành phần | Khi nào hữu ích | Ghi chú |
| --- | --- | --- |
| `DispatcherServlet` | Controller/handler được Spring MVC dispatch | Bình thường đã expose context |
| `RequestContextFilter` | Cần context ở các tầng **ngoài DispatcherServlet**, chẳng hạn filter hoặc servlet khác | Cần cấu hình mapping/order/dispatcher types phù hợp |
| `RequestContextListener` | Servlet listener để gắn request thread context ngoài DispatcherServlet | Chỉ dùng khi môi trường cần; xem lifecycle |

Nếu một filter chạy **trước** `DispatcherServlet` và gọi `RequestContextHolder.getRequestAttributes()`, bạn không được mặc định đã có context do DispatcherServlet cung cấp: nó chưa chạy. Nếu đặt `RequestContextFilter` và đăng ký đúng thứ tự, request/locale context có thể được expose ở filter path đó. Nhưng không thêm nhiều cơ chế chỉ vì “cho chắc”: cần xác định thành phần nào thực sự cung cấp context cho đoạn code cần nó.

`RequestContextFilter` có tùy chọn `setThreadContextInheritable(boolean)`, **mặc định false** để tránh rủi ro thread pool. Trong Spring 6.x, filter còn xét async và error dispatch để thiết lập context trên các dispatch tương ứng; behavior còn tùy servlet filter registration.

## 5. LocaleContextHolder: ngôn ngữ và múi giờ theo luồng

`LocaleContextHolder` lưu một `LocaleContext` gắn với thread, có thể gồm `Locale` và, khi context hỗ trợ, `TimeZone`. `DispatcherServlet` tự expose locale đã được MVC resolve.

Các API chính:

| API | Hành vi |
| --- | --- |
| `LocaleContextHolder.getLocaleContext()` | Trả context đang bind hoặc **null** |
| `LocaleContextHolder.getLocale()` | Trả locale của context; nếu không có, fallback tới Spring default/JVM default |
| `LocaleContextHolder.getTimeZone()` | Trả timezone của context; nếu không có, fallback tới Spring default/JVM default |
| `LocaleContextHolder.setLocale(locale)` | Gắn locale vào thread hiện tại, không kế thừa theo mặc định |
| `LocaleContextHolder.setLocaleContext(ctx)` | Gắn context, có thể có timezone; không kế thừa theo mặc định |
| `LocaleContextHolder.resetLocaleContext()` | Xóa context ở thread hiện tại |
| `setLocale(locale, true)` / `setLocaleContext(ctx, true)` | Cho phép kế thừa qua `InheritableThreadLocal` |
| `setDefaultLocale(...)` / `setDefaultTimeZone(...)` | Đặt **default dùng chung** của holder; không phải locale riêng của một user |

### 5.1. getLocale() không null chứng tỏ có request?

**Không.** Ngay cả khi không bind bất kỳ locale context nào, `getLocale()` vẫn thường trả default locale của Spring/JVM; tương tự `getTimeZone()` có default timezone. Muốn biết có context gắn với thread không, dùng `getLocaleContext() != null`.

```java
Locale locale = LocaleContextHolder.getLocale();
LocaleContext ctx = LocaleContextHolder.getLocaleContext();

boolean explicitlyBound = (ctx != null);
```

Đoạn này có imports `java.util.Locale` và `org.springframework.context.i18n.LocaleContext`. `explicitlyBound` cho biết có thread-bound LocaleContext, **không** bảo đảm có HTTP request hoặc có header Accept-Language; context có thể do test hay ứng dụng tự set.

### 5.2. Locale khác timezone

`Locale.forLanguageTag("vi-VN")` mô tả ngôn ngữ/region, không có nghĩa timezone là `Asia/Ho_Chi_Minh`. `Accept-Language: vi-VN` không truyền một timezone hợp lệ. Dùng IANA zone như `Asia/Ho_Chi_Minh` qua profile/user preference hoặc cơ chế riêng và truyền `ZoneId` tường minh trong business logic khi cần quy đổi thời điểm.

Ví dụ:

```java
Locale locale = Locale.forLanguageTag("vi-VN");
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");

String language = locale.toLanguageTag(); // "vi-VN"
String zoneText = zone.getId();            // "Asia/Ho_Chi_Minh"
```

`LocaleContextHolder.getTimeZone()` fallback tới default nếu context không cung cấp timezone; không nên dùng để suy ra vị trí vật lý của user, tài khoản hay request.

## 6. Accept-Language, LocaleResolver và MessageSource

Browser có thể gửi:

```http
GET /context/info HTTP/1.1
Accept-Language: vi-VN, en;q=0.8
```

Một cấu hình Spring MVC phổ biến dùng `AcceptHeaderLocaleResolver` để resolve locale từ header và các supported locales/default nếu cấu hình. `DispatcherServlet` đặt locale đã resolve vào `LocaleContextHolder` trong request. Hành vi chính xác phụ thuộc resolver và options.

**Phân biệt:**

- `Accept-Language` = ưu tiên ngôn ngữ của client.
- `LocaleResolver` / `LocaleContextResolver` = chính sách chọn locale của ứng dụng trên request.
- `LocaleContextHolder` = nơi **đọc context hiện tại theo thread**; không tự ghi cookie hay persist lựa chọn.
- `MessageSource` = tra cứu chuỗi đã bản địa hóa theo một `Locale`.
- `TimeZone` / `ZoneId` = quy tắc múi giờ, tách biệt ngôn ngữ.

Ví dụ:

```java
String title = messageSource.getMessage(
        "greeting",
        null,
        LocaleContextHolder.getLocale());
```

Đây là snippet trong một bean có `MessageSource messageSource` được inject. Nếu resource bundles chứa:

```properties
# messages_vi.properties
greeting=Xin chào
```

```properties
# messages_en.properties
greeting=Hello
```

Khi locale là Vietnamese/English và cấu hình MessageSource tìm được key, chuỗi phù hợp được lấy. Đừng gắn chặt business layer với thread holder nếu đã biết locale trong method signature; truyền `Locale` vào service làm code dễ test hơn.

### Có nên gọi setLocale để thay ngôn ngữ cho toàn phiên?

`LocaleContextHolder.setLocale(locale)` chỉ thay đổi **binding thread hiện tại**; nó không tự thay `Accept-Language` và không ghi session/cookie. Nếu ứng dụng cho phép user chọn ngôn ngữ trên giao diện, cần chiến lược `LocaleResolver` hỗ trợ ghi và có thể dùng `LocaleChangeInterceptor` hoặc endpoint cập nhật preference được thiết kế rõ.

`AcceptHeaderLocaleResolver` không hỗ trợ `setLocale` như một nơi ghi preference tùy ý; nếu cần thay locale bằng query/session/cookie, cân nhắc `SessionLocaleResolver`/`CookieLocaleResolver` và cấu hình đúng. Không đưa `setLocale` vào một request rồi mong request kế tiếp của user chắc chắn có cùng locale.

## 7. Tại sao ThreadLocal có giới hạn?

Java `ThreadLocal<T>` đặt một ô dữ liệu riêng cho **mỗi thread**. Nó không tự đồng bộ hoặc tự sao chép giữa thread. Giá trị được set trong request thread chỉ có mặt trong thread đó, cho đến khi `remove()`/reset.

### 7.1. Ví dụ Java thuần, chạy bằng javac

Tệp `ThreadLocalDemo.java`:

```java
public class ThreadLocalDemo {
    private static final ThreadLocal<String> LANGUAGE =
            new ThreadLocal<>();

    public static void main(String[] args) throws InterruptedException {
        LANGUAGE.set("vi-VN");
        try {
            System.out.println("main=" + LANGUAGE.get());

            Thread child = new Thread(
                    () -> System.out.println(
                            "child=" + LANGUAGE.get()));
            child.start();
            child.join();
        } finally {
            LANGUAGE.remove();
        }

        System.out.println("mainAfter=" + LANGUAGE.get());
    }
}
```

Lệnh:

```bash
javac ThreadLocalDemo.java
java ThreadLocalDemo
```

Stdout chính xác:

```text
main=vi-VN
child=null
mainAfter=null
```

`ThreadLocal` tiêu chuẩn không tự copy `"vi-VN"` sang child thread. Đây là Java thuần, không yêu cầu Spring hay web server.

### 7.2. InheritableThreadLocal không giải quyết thread pool

`InheritableThreadLocal` có thể sao chép tham chiếu từ parent khi một **thread mới được tạo**. Nó không cập nhật giá trị mỗi lần một pooled worker chạy một task. Trong `ExecutorService`/`ThreadPoolExecutor`:

- Worker thread có thể đã được tạo từ trước khi request A bắt đầu.
- Worker tạo khi request A đang active có thể kế thừa context A.
- Worker sau đó được **tái sử dụng** cho request B, trong khi giá trị kế thừa cũ chưa được clear.
- Nếu context chứa `HttpServletRequest`/`ServletRequestAttributes`, nó có thể tham chiếu dữ liệu **đã hết lifecycle** hoặc thông tin user trước.

Spring Javadoc của `RequestContextFilter` cảnh báo không bật inheritance với pool có khả năng tạo thread mới theo nhu cầu. Vì thế **không đặt `threadContextInheritable=true` làm cách sửa tổng quát cho @Async hoặc executor**.

Một thread con chỉ sống đúng trong phạm vi request có thể kế thừa context nếu chủ động thiết kế; tuy nhiên vẫn cần lifecycle/cleanup và nên cân nhắc liệu thật sự cần truyền cả request hay chỉ vài giá trị bất biến.

## 8. @Async, CompletableFuture và ExecutorService

Những tình huống sau **không đảm bảo** nhìn thấy `RequestContextHolder`/`LocaleContextHolder` của request ban đầu:

```java
CompletableFuture.supplyAsync(
        () -> LocaleContextHolder.getLocale());

executorService.submit(
        () -> RequestContextHolder.getRequestAttributes());

@Async
public void doWork() {
    // Chạy trên executor thread khác, không mặc nhiên có request context.
}
```

Đây là **ba snippet độc lập**, không phải một chương trình Java hoàn chỉnh. `getLocale()` vẫn có thể trả JVM default dù không hề nhận locale từ request; do đó “không bị null” không chứng minh propagation thành công. `getRequestAttributes()` trong pooled thread thường là null; `currentRequestAttributes()` có thể ném `IllegalStateException`.

### Cách nên dùng: snapshot dữ liệu cần thiết, rồi truyền tham số

Trong controller:

```java
@GetMapping("/enqueue")
public String enqueue(
        Locale locale,
        @RequestHeader(name = "X-Request-Id", required = false)
        String requestId) {

    String id = (requestId == null) ? "none" : requestId;
    jobService.enqueue(locale.toLanguageTag(), id);
    return "QUEUED";
}
```

Đây là method **minh họa** trong `@RestController` đã có bean `jobService`; không phải file biên dịch độc lập. `jobService.enqueue` có thể tạo job command gồm hai `String` bất biến. Job không cần `HttpServletRequest`, `ServletRequestAttributes` hay một thread-bound context còn sống.

Nếu job cần user identity, chỉ truyền **ID/các quyền cần kiểm tra lại** theo thiết kế bảo mật, không coi thông tin từ request header do client tự gửi là trusted identity. Khi job xử lý sau, hãy re-authorize theo chính sách phù hợp; tránh mang nguyên security/session/request context ra khỏi lifecycle.

### Khi nào TaskDecorator hợp lý?

Spring `TaskDecorator` có thể giúp propagate **một số context được phép** vào task trên executor, với việc khôi phục context cũ trong `finally`. Có thể dùng cho `LocaleContext` hoặc logging correlation ID khi tác vụ ngắn, không chứa tài nguyên request đã đóng. Tuy nhiên propagate request attributes nguyên trạng qua pool là rủi ro.

Ví dụ an toàn hơn cho locale, không mang servlet request:

```java
package com.example.demo;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.task.TaskDecorator;

public class LocaleTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable task) {
        // Chụp locale context tại thời điểm submit/decorate task.
        LocaleContext captured = LocaleContextHolder.getLocaleContext();

        return () -> {
            LocaleContext previous =
                    LocaleContextHolder.getLocaleContext();
            try {
                LocaleContextHolder.setLocaleContext(captured);
                task.run();
            } finally {
                LocaleContextHolder.setLocaleContext(previous);
            }
        };
    }
}
```

Dùng với `ThreadPoolTaskExecutor.setTaskDecorator(new LocaleTaskDecorator())` trong cấu hình executor. Snippet này là **lớp Java độc lập trong dự án Spring có dependencies**, không tự khởi động web server. Nó copy `LocaleContext`, không copy `RequestAttributes`. Khi ngữ cảnh gốc null, `setLocaleContext(null)` reset thread-bound locale trên worker; `finally` khôi phục trạng thái trước đó. Với logging MDC, security context hoặc trace context, có cơ chế propagation và cleanup riêng; không tự cho rằng copy locale đồng nghĩa copy tất cả.

Lưu ý khi dùng `@Async`: framework có thể tạo wrapper task/future; TaskDecorator can thiệp vào runnable được executor chạy, không nhất thiết trực tiếp vào đúng đoạn code người dùng nghĩ. Hãy test thực tế và tránh dùng một static holder làm giao ước dài hạn của job.

## 9. Servlet async do Spring MVC quản lý khác task tự spawn

Spring MVC hỗ trợ controller trả `Callable<T>` hoặc `WebAsyncTask<T>`; framework điều phối Servlet async processing. Spring có cơ chế gắn request/locale context trong những giai đoạn thích hợp của xử lý async do MVC quản lý, và có thể dispatch lại vào Servlet.

**Không được suy từ đó rằng mọi `CompletableFuture.supplyAsync` hoặc background job của bạn sẽ được gắn đúng context.** Thread pool tùy ý chạy bên ngoài MVC async lifecycle là chuyện khác.

Ví dụ controller trả `Callable` an toàn với snapshot dữ liệu:

```java
@GetMapping("/async-safe")
public Callable<LanguageResponse> asyncSafe(Locale locale) {
    final String tag = locale.toLanguageTag();
    return () -> new LanguageResponse(tag);
}
```

Method này **không phụ thuộc vào locale ThreadLocal ở worker**; `tag` đã được lấy khi controller ở request context hợp lệ. `Callable` do MVC quản lý có lifecycle/async dispatch riêng, nhưng code vẫn tường minh và dễ test.

Nếu tự chạy asynchronous streaming hoặc gửi sự kiện `SseEmitter` sau khi response đã commit, request có thể không còn sống. Đừng lưu `ServletRequestAttributes` để dùng trong callback nhiều phút sau. Trong ứng dụng với virtual threads (Java 21+), mỗi task có thể có thread riêng nhưng **ThreadLocal vẫn là cơ chế theo thread**, không tự thay đổi semantics request lifetime hay propagation; Java 17 demo không dùng virtual threads.

## 10. Mã mẫu chạy được: Spring Boot 3 / Spring MVC

Ví dụ này minh họa ba cách đọc context:

- `GET /context/info`: đọc request và locale từ hai holder trong MVC dispatch; đồng thời nhận `Locale` và `HttpServletRequest` trực tiếp để so sánh.
- `GET /context/async-safe`: dùng `Callable` và **snapshot locale tag** từ request trước khi task async bắt đầu.
- `GET /context/messages`: lấy message từ `MessageSource` theo `Locale` đang được MVC resolve.

Dự án Maven từ Spring Initializr với Java 17+, `spring-boot-starter-web`, `spring-boot-starter-test`. Với `Locale`/MessageSource, thêm các tệp messages bên dưới; không cần database và không cần `RequestContextFilter` riêng vì controller chạy trong `DispatcherServlet`.

### 10.1. src/main/java/com/example/demo/DemoApplication.java

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

### 10.2. src/main/java/com/example/demo/ContextController.java

```java
package com.example.demo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;
import java.util.concurrent.Callable;

@RestController
public class ContextController {

    private final MessageSource messages;

    public ContextController(MessageSource messages) {
        this.messages = messages;
    }

    @GetMapping("/context/info")
    public ContextResponse info(
            HttpServletRequest request,
            Locale locale) {

        RequestAttributes attrs =
                RequestContextHolder.getRequestAttributes();
        LocaleContext localeContext =
                LocaleContextHolder.getLocaleContext();

        boolean servletBound =
                attrs instanceof ServletRequestAttributes;
        boolean localeBound = localeContext != null;

        String path = request.getRequestURI();
        String tag = locale.toLanguageTag();
        String holderTag = LocaleContextHolder.getLocale()
                .toLanguageTag();

        return new ContextResponse(
                path, tag, holderTag, servletBound, localeBound);
    }

    @GetMapping("/context/async-safe")
    public Callable<LanguageResponse> asyncSafe(Locale locale) {
        String captured = locale.toLanguageTag();

        // Callable được Spring MVC xử lý như servlet async.
        return () -> new LanguageResponse(captured);
    }

    @GetMapping("/context/messages")
    public MessageResponse messages(Locale locale) {
        String resolved = messages.getMessage(
                "greeting", null, locale);
        return new MessageResponse(
                locale.toLanguageTag(), resolved);
    }

    public record ContextResponse(
            String path,
            String locale,
            String holderLocale,
            boolean requestBound,
            boolean localeBound) {}

    public record LanguageResponse(String locale) {}

    public record MessageResponse(
            String locale, String greeting) {}
}
```

Vì code nằm trong controller `@RestController`, Spring MVC resolve argument `HttpServletRequest`/`Locale` qua method arguments, rồi Jackson serialize DTO record. `ServletRequestAttributes` được kiểm tra bằng `instanceof` thay vì cast mù. Không cần tạo static field chứa request; controller bean thường là singleton, trong khi request là tài nguyên có vòng đời riêng.

**Phần quan trọng của demo:** `info()` nhận `Locale` làm parameter, nhưng đồng thời đọc `LocaleContextHolder.getLocale()`. Hai kết quả thông thường cùng locale trong MVC dispatch. `requestBound` và `localeBound` là boolean có thể kiểm chứng chứ không khẳng định mọi request phải có timezone.

### 10.3. src/main/resources/messages_vi.properties

```properties
greeting=Xin chào
```

### 10.4. src/main/resources/messages_en.properties

```properties
greeting=Hello
```

Có thể thêm `src/main/resources/messages.properties` làm fallback:

```properties
greeting=Hello
```

Đây là ba file riêng; messages bundle được Spring Boot auto-configure khi đặt đúng nguồn và có fallback `messages.properties`. Không cần gọi `LocaleContextHolder.setLocale` để dịch vì handler đã nhận `Locale` của request.

### 10.5. Chạy ứng dụng

```bash
./mvnw spring-boot:run
```

PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Cổng mặc định thường là 8080 nếu không chỉnh `server.port`. Những source này không biên dịch bằng `javac ContextController.java` khi chưa có Spring Boot dependencies; sử dụng dự án Maven đã tạo từ Spring Initializr.

## 11. Kiểm chứng HTTP bằng curl

### Request context và locale

```bash
curl -s -H "Accept-Language: vi-VN" \
  http://localhost:8080/context/info
```

Với cấu hình locale resolver tiêu chuẩn đọc Accept-Language, JSON kỳ vọng có các giá trị:

```json
{
  "path": "/context/info",
  "locale": "vi-VN",
  "holderLocale": "vi-VN",
  "requestBound": true,
  "localeBound": true
}
```

Thứ tự JSON keys có thể khác; không dùng snapshot chuỗi nguyên JSON để kiểm tra. Nếu ứng dụng đã cấu hình `LocaleResolver` riêng, `defaultLocale`, `supportedLocales` hay gateway sửa header, locale có thể khác: cần điều chỉnh test theo contract.

### MessageSource theo ngôn ngữ

```bash
curl -s -H "Accept-Language: vi-VN" \
  http://localhost:8080/context/messages
```

Kỳ vọng `greeting` = `"Xin chào"`, `locale` = `"vi-VN"`. Với:

```bash
curl -s -H "Accept-Language: en-US" \
  http://localhost:8080/context/messages
```

Kỳ vọng `greeting` = `"Hello"` theo bundle English. Bài này chọn key `greeting`, không phụ thuộc database hay request session.

### Servlet async an toàn

```bash
curl -s -H "Accept-Language: vi-VN" \
  http://localhost:8080/context/async-safe
```

Kỳ vọng response sau async dispatch chứa `{"locale":"vi-VN"}`. Locale tag được snapshot **trước** khi Callable chạy; không phụ thuộc worker đọc ThreadLocal.

## 12. MockMvc: kiểm tra bound context, locale và async

Tạo `src/test/java/com/example/demo/ContextControllerTest.java`, phụ thuộc `spring-boot-starter-test`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Locale;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ContextControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void requestAndLocaleContextAreBoundInsideMvc() throws Exception {
        mvc.perform(get("/context/info")
                .locale(Locale.forLanguageTag("vi-VN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path").value("/context/info"))
            .andExpect(jsonPath("$.locale").value("vi-VN"))
            .andExpect(jsonPath("$.holderLocale").value("vi-VN"))
            .andExpect(jsonPath("$.requestBound").value(true))
            .andExpect(jsonPath("$.localeBound").value(true));
    }

    @Test
    void messageSourceUsesResolvedLocale() throws Exception {
        mvc.perform(get("/context/messages")
                .locale(Locale.forLanguageTag("vi-VN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.greeting").value("Xin chào"));
    }

    @Test
    void asyncResponseUsesCapturedValue() throws Exception {
        MvcResult initial = mvc.perform(get("/context/async-safe")
                .locale(Locale.forLanguageTag("vi-VN")))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(initial))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locale").value("vi-VN"));
    }
}
```

Chạy:

```bash
./mvnw test
```

Windows:

```powershell
.\mvnw.cmd test
```

Đây là **mẫu test có thể chạy trong project Boot 3 đã khai báo dependencies**; không phải kết quả đã thực thi trong repo cụ thể. Nếu bật Spring Security, custom locale resolver hoặc filter, test có thể cần mock user, allow-list và cấu hình locale tương ứng.

**Vì sao test async không gọi ThreadLocal trên worker?** Mục tiêu là chứng minh cách truyền snapshot ổn định, không giả định tất cả worker luôn có HTTP request context. Để test cơ chế MVC async binding riêng, viết test có kiểm soát theo Spring Framework version và lifecycle, không lấy behavior ngẫu nhiên của một `ExecutorService` làm contract.

## 13. Unit test thiếu request context

Trong unit test ngoài MVC, `RequestContextHolder.getRequestAttributes()` có thể là null. Có thể kiểm tra rõ mà không cần web server:

```java
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;

import static org.junit.jupiter.api.Assertions.*;

class NoRequestContextTest {

    @Test
    void noRequestContextOnOrdinaryTestThread() {
        RequestContextHolder.resetRequestAttributes();

        assertNull(RequestContextHolder.getRequestAttributes());
        assertThrows(IllegalStateException.class,
                RequestContextHolder::currentRequestAttributes);
    }
}
```

Đây là **lớp test độc lập trong source test của một dự án Spring có JUnit**; file cần package phù hợp nếu project yêu cầu. Với môi trường JSF có `FacesContext` đang active, `currentRequestAttributes()` có thể fallback thay vì throw; test trên giả định unit test Java thông thường không có JSF FacesContext.

Nếu viết test cho `LocaleContextHolder`, phải nhớ `getLocale()` fallback default nên **không assert null**. Dùng `LocaleContextHolder.getLocaleContext()` để biết có context gắn với thread hay không. Unit test dùng `setLocaleContext`/`setRequestAttributes` thủ công phải cleanup trong `finally` hoặc teardown; không tạo state rò rỉ giữa các test cùng test worker.

## 14. Khi thật sự cần gắn context thủ công

Trong code tùy chỉnh, nếu cần tạm gắn một `LocaleContext`, hãy **khôi phục trạng thái trước đó**, không đơn giản luôn reset một context do framework đang sở hữu.

```java
LocaleContext old = LocaleContextHolder.getLocaleContext();
try {
    LocaleContextHolder.setLocale(Locale.forLanguageTag("vi-VN"));
    doSomethingThatNeedsLocale();
} finally {
    LocaleContextHolder.setLocaleContext(old);
}
```

Đoạn này là snippet trong method đã có import `Locale`/`LocaleContext` và method `doSomethingThatNeedsLocale()`. Nó không tự lập trình session/cookie cho user và không nên được dùng trong controller chỉ để đổi locale của ứng dụng qua nhiều request.

Với `RequestContextHolder`, tự tạo `ServletRequestAttributes` và gắn vào custom thread chỉ có thể hợp lý trong phạm vi một request **còn active**, có quản lý cleanup và không làm sai lifecycle. Không nên dùng `new ServletRequestAttributes(mockRequest)` trong production chỉ để dập `IllegalStateException`, và càng không dùng request attributes từ request A cho background job của request B.

Nếu cần xử lý tác vụ có id người gọi/ngôn ngữ, truyền immutable data và authorize đúng. Context propagation framework chỉ nên là bổ trợ cho những cross-cutting concerns đã hiểu vòng đời.

## 15. Cạm bẫy và xử lý lỗi

1. **Đọc context trong constructor singleton:** constructor chạy khi khởi động, không có request, dẫn đến `IllegalStateException`.
2. **Dùng currentRequestAttributes ngoài thread web:** trong `@Scheduled`, `@Async` hoặc pooled worker có thể ném exception.
3. **Cho rằng getLocale() không null nghĩa là request đã propagate:** sai; nó fallback Spring/JVM default.
4. **Bật inheritable=true cho thread pool:** có thể rò rỉ context từ request cũ vào pooled worker.
5. **Lưu ServletRequestAttributes trong static map/cache:** tạo memory leak và tham chiếu request đã hết vòng đời.
6. **Quên cleanup ThreadLocal trong code tự set:** worker giữ dữ liệu cũ khi tái sử dụng.
7. **Cố lấy userId từ request header và coi là trusted identity:** header client tự kiểm soát; xác thực/ủy quyền riêng.
8. **Đổi locale qua LocaleContextHolder.setLocale rồi mong session nhớ:** nó chỉ thay thread-bound context; dùng resolver/persist preference phù hợp.
9. **Nhầm Accept-Language với timezone:** ngôn ngữ/region không xác định múi giờ thực của user.
10. **Đọc RequestContextHolder trong filter chạy trước DispatcherServlet:** holder có thể chưa được framework bind; cần xác định filter/listener và thứ tự.
11. **Copy HttpServletRequest vào CompletableFuture:** request có thể đã kết thúc; chỉ snapshot thông tin cần.
12. **TaskDecorator set locale mà không finally restore:** pooled worker có thể mang locale của user khác.
13. **Mang toàn bộ SecurityContext một cách vô điều kiện:** cần dùng cơ chế security propagation và lifecycle, không tự copy credentials.
14. **Dựa vào ThreadLocal để duy trì business transaction qua thread:** transaction và persistence context có cơ chế riêng, không được tự mang qua worker.
15. **Dùng Locale.getDefault() để suy locale của client:** đó là JVM default, không phải Accept-Language.
16. **Nhận HTTP 500 khi không có request rồi sửa bằng RequestContextFilter ở mọi nơi:** nếu call site là scheduled job, filter không chạy ở đó; sửa dependency của job.
17. **Gắn context mà không phân biệt REQUEST/ERROR/ASYNC dispatch:** thread/dispatch có thể khác; inspect lifecycle thực.
18. **Để message key không tồn tại:** MessageSource có thể ném `NoSuchMessageException` nếu không có default phù hợp, không phải lỗi ThreadLocal.
19. **Dùng holder static làm API business:** khó test, che dependency và giới hạn reuse qua CLI, batch, messaging.
20. **Kết luận propagation từ test chạy “thỉnh thoảng đúng”:** executor thread reuse/timing có thể tạo tình trạng không ổn định; test với giá trị snapshot.
21. **Đọc request sau khi response hoàn tất:** request scope đã hết; dù reference vẫn còn không có nghĩa an toàn.
22. **Không phân biệt SessionLocaleResolver với AcceptHeaderLocaleResolver:** chính sách lưu locale khác nhau; cần thiết kế rõ giao diện đổi ngôn ngữ.
23. **Bỏ qua test cô lập đa luồng:** có thể có cross-request leak không lộ ra trong happy-path.
24. **Trộn thread-context với Reactor Context trong WebFlux:** model lập trình khác, hãy dùng đúng API của reactive stack.

## 16. Quy trình debug khi locale/request context “biến mất”

1. Xác định đoạn code chạy trên **thread nào**; log tên thread/correlation ID có kiểm soát, không log dữ liệu nhạy cảm.
2. Nó chạy trong MVC controller/service đồng bộ, servlet filter, `@Async`, `Callable`, `CompletableFuture` hay scheduled task?
3. Nếu `RequestContextHolder.getRequestAttributes() == null`, xem có vào DispatcherServlet chưa, filter/listener có được đăng ký không.
4. Nếu `currentRequestAttributes()` ném IllegalStateException trong background job, không thêm Filter một cách mù quáng: chuyển sang snapshot input hoặc thiết kế truyền tham số.
5. Nếu `LocaleContextHolder.getLocale()` ra locale mặc định, kiểm tra `getLocaleContext()` để phân biệt context thiếu với locale được resolve là mặc định.
6. Xem `LocaleResolver` và header `Accept-Language` thật; client, gateway hay test có thể override locale.
7. Xem task executor/thread pool và cờ inheritable; tránh patch bằng `setThreadContextInheritable(true)`.
8. Kiểm tra cleanup trong `finally` của mọi đoạn code tự set ThreadLocal, kể cả khi task ném exception.
9. Nếu lỗi xảy ra khi async response, xem Servlet async lifecycle, ERROR/ASYNC dispatch và thời điểm response đã commit.
10. Đo bằng unit test không có request, MockMvc trong request và integration test executor thực; không gộp ba kết quả này thành một tình huống.
11. Nếu kết quả phụ thuộc request ID/timezone, truyền hai giá trị đó dưới kiểu immutable vào worker thay vì request object.
12. Kiểm tra chính sách user/tenant, session/cookie và authorization riêng; thread-bound locale không chứng minh đã xác thực.

## 17. Checklist thiết kế an toàn

- [ ] Controller có thể nhận trực tiếp `HttpServletRequest`, `Locale` hoặc principal qua method arguments khi cần?
- [ ] Service nhận dữ liệu cần thiết dưới dạng tham số rõ ràng thay vì truy cập holder tĩnh?
- [ ] Code dùng `currentRequestAttributes()` chỉ khi đang chắc chắn ở request lifecycle?
- [ ] Code có fallback đúng khi `getRequestAttributes()==null`?
- [ ] Không lưu `ServletRequestAttributes`/`HttpServletRequest` vào background queue, cache hoặc static field?
- [ ] Không bật inheritable context cho pooled executor?
- [ ] Mọi ThreadLocal tự set đều được restore/clear bằng `finally`?
- [ ] Locale resolver phản ánh đúng hợp đồng `Accept-Language`/session/cookie?
- [ ] Locale và timezone được phân biệt; timezone lấy từ nguồn hợp lệ riêng?
- [ ] I18n message được lookup bằng locale explicit hoặc context đã được xác nhận?
- [ ] Servlet async và worker thread được kiểm thử riêng?
- [ ] Có test chống leak giữa những tác vụ/user khác nhau khi dùng custom decorators?
- [ ] Request-specific business data có authorization; không tin header client tự chọn?
- [ ] HTTP/error response không lộ request context hoặc dữ liệu cá nhân?
- [ ] Code có khả năng chạy từ batch/event/CLI mà không phụ thuộc HTTP thread nếu use case cần?

## 18. Câu hỏi tự kiểm tra

**1.** `RequestContextHolder.currentRequestAttributes()` và `getRequestAttributes()` khác nhau ra sao?

**Đáp án:** `getRequestAttributes()` trả null nếu không có thread-bound context. `currentRequestAttributes()` yêu cầu context, có thể fallback JSF, sau đó ném IllegalStateException nếu vẫn không có.

**2.** Vì sao `LocaleContextHolder.getLocale()` không phải cách kiểm tra request đã được bind?

**Đáp án:** Nó fallback Spring/JVM default ngay cả khi không có context. Dùng `getLocaleContext()` để xem có locale context gắn thread hay không.

**3.** `Locale.forLanguageTag("vi-VN")` có nghĩa timezone = Asia/Ho_Chi_Minh?

**Đáp án:** Không. Locale/region và timezone là hai thông tin khác nhau.

**4.** Có phải Spring MVC controller đồng bộ nào cũng cần thêm RequestContextFilter mới đọc được RequestContextHolder?

**Đáp án:** Không. DispatcherServlet đã expose request context trong xử lý MVC thông thường.

**5.** Vì sao không bật `setThreadContextInheritable(true)` cho thread pool?

**Đáp án:** Pooled worker có thể tạo lúc request A active rồi được tái sử dụng cho B, giữ context A hoặc context đã hết vòng đời, gây rò rỉ và sai dữ liệu.

**6.** Có thể lấy request locale tự động trong `CompletableFuture.supplyAsync` không?

**Đáp án:** Không được giả định. Executor thread khác; snapshot locale và truyền tường minh, hoặc dùng decorator chuyên biệt có cleanup nếu thực sự cần.

**7.** `@Async` có làm HttpServletRequest tồn tại lâu hơn response không?

**Đáp án:** Không. Background task có lifecycle riêng; không nắm giữ request attributes sau khi request hoàn tất.

**8.** `LocaleContextHolder.setLocale` có tạo cookie cho request sau không?

**Đáp án:** Không. Nó sửa context của thread hiện tại. Muốn persist lựa chọn ngôn ngữ cần resolver/session/cookie/preference mechanism phù hợp.

**9.** Khi nào `TaskDecorator` phù hợp?

**Đáp án:** Khi cần propagate context nhỏ và an toàn (như locale, trace metadata) vào tác vụ đã quản lý, có `try/finally` khôi phục. Không copy nguyên ServletRequestAttributes vào job dài hạn.

**10.** Cách đơn giản để service không phụ thuộc RequestContextHolder?

**Đáp án:** Controller lấy `Locale`/request id/user id từ framework, tạo input DTO/snapshot immutable rồi truyền vào service qua tham số.

## 19. Tài liệu tham khảo

- Spring Framework — RequestContextHolder Javadoc: https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/web/context/request/RequestContextHolder.html
- Spring Framework — LocaleContextHolder Javadoc: https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/context/i18n/LocaleContextHolder.html
- Spring Framework — RequestContextFilter và inheritable context: https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/web/filter/RequestContextFilter.html
- Spring Framework — RequestContextListener: https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/web/context/request/RequestContextListener.html
- Spring Framework — LocaleResolver / LocaleContextResolver: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/localeresolver.html
- Spring Framework — MVC Method Arguments: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html
- Spring Framework — MVC Async Requests: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- Spring Framework — TaskDecorator API: https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/core/task/TaskDecorator.html
- Java 17 — ThreadLocal Javadoc: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html
- Java 17 — InheritableThreadLocal Javadoc: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/InheritableThreadLocal.html
- Jakarta Servlet specification: https://jakarta.ee/specifications/servlet/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="session-localization-008">Truyền ngữ cảnh khi chuyển sang xử lý bất đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Spring MVC — Truyền ngữ cảnh khi chuyển sang xử lý bất đồng bộ

## 1. Mục tiêu và vấn đề thực tế

Một HTTP request vào Spring MVC có thể mang theo **ngôn ngữ của người dùng**, thông tin request, danh tính đã xác thực, mã đối chiếu (request ID), trace/span ID và trạng thái giao dịch. Khi controller chạy đồng bộ trên một Servlet thread, Spring thường gắn một số dữ liệu này với thread hiện tại. Nếu công việc chuyển sang thread khác bằng `@Async`, `CompletableFuture` hoặc executor, chúng **không tự động xuất hiện đầy đủ** ở thread mới.

Ví dụ:

~~~java
Locale locale = LocaleContextHolder.getLocale();
String requestId = MDC.get("requestId");
~~~

Trong handler đang xử lý request, `locale` có thể là `vi_VN` và `requestId` là một UUID. Tuy nhiên, nếu bạn chạy chính hai dòng đó trong `CompletableFuture.supplyAsync(...)` trên common pool, kết quả có thể trở thành locale mặc định của máy chủ và `requestId == null`. Điều đó gây sai ngôn ngữ, mất khả năng đối chiếu log, hoặc nghiêm trọng hơn là sử dụng sai thông tin bảo mật.

Sau bài học, bạn có thể:

- Phân biệt **business data** được truyền qua tham số tường minh và **execution context** gắn với thread.
- Hiểu `LocaleContextHolder`, `RequestContextHolder`, SLF4J `MDC`, `SecurityContextHolder`, Micrometer tracing/context propagation và phạm vi phù hợp của mỗi loại.
- Giải thích vòng đời Servlet async của `Callable`, `DeferredResult`, `CompletionStage` và `@Async` — chúng không đồng nghĩa với nhau.
- Thiết kế `TaskDecorator` **capture khi submit**, **restore khi chạy**, **cleanup trong finally**; tránh context leak qua thread pool.
- Biết khi nào dùng `ContextPropagatingTaskDecorator`/Micrometer Context Propagation và khi nào dùng Spring Security `DelegatingSecurityContextAsyncTaskExecutor`.
- Xây dựng ví dụ Java 17+ / Spring Boot 3.5.0 với `@Async`, locale, request ID, HTTP response và MockMvc async dispatch.
- Nhận diện các giới hạn về request/session lifecycle, SecurityContext, transaction, tracing, timeout, thread pool, data privacy và propagation qua service khác.

**Phạm vi:** Spring MVC (Servlet stack) của Spring Framework 6.2.x. Không đồng nhất giải pháp này với Spring WebFlux/Reactor Context; các môi trường dùng reactive stack cần thiết kế khác.

## 2. Context là gì? Vì sao đổi thread làm mất context?

**Context** là dữ liệu đi kèm một lần thực thi mà nhiều thành phần cần truy cập nhưng không phải lúc nào cũng được truyền trực tiếp qua tham số phương thức: locale, trace ID, request metadata, authentication, v.v.

Trong Java, `ThreadLocal<T>` lưu giá trị gắn với **một thread**. Thread khác có ThreadLocal riêng:

~~~java
private static final ThreadLocal<String> CURRENT_REQUEST =
        new ThreadLocal<>();
~~~

Với một thread pool, worker thường được tái sử dụng để xử lý nhiều tác vụ của nhiều người dùng. Nếu không dọn ThreadLocal sau khi chạy, ngữ cảnh của request cũ có thể lẫn sang tác vụ mới.

### 2.1. Chương trình Java 17 độc lập: quan sát context mất khi đổi thread

Lưu vào file `ThreadLocalDemo.java`:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ThreadLocalDemo {
    private static final ThreadLocal<String> REQUEST = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            REQUEST.set("req-A");

            Future<String> without = pool.submit(REQUEST::get);
            System.out.println(without.get() == null);

            String captured = REQUEST.get(); // Capture trên caller thread.

            Future<String> with = pool.submit(() -> {
                String previous = REQUEST.get();
                try {
                    REQUEST.set(captured); // Install trên worker.
                    return REQUEST.get();
                } finally {
                    if (previous == null) {
                        REQUEST.remove();
                    } else {
                        REQUEST.set(previous);
                    }
                }
            });
            System.out.println(with.get());
        } finally {
            REQUEST.remove();
            pool.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac ThreadLocalDemo.java
java ThreadLocalDemo
~~~

Stdout:

~~~text
true
req-A
~~~

**Ý nghĩa:** request context không tự chuyển sang worker. Phải capture tại nơi còn ngữ cảnh, install trong thread xử lý và restore/clear sau khi kết thúc. `finally` rất quan trọng khi task ném exception. Không thay thế bằng việc set context một lần lúc tạo thread pool, vì pool phục vụ nhiều request.

### 2.2. Những context thường gặp trong Spring

| Cơ chế | Chứa dữ liệu gì? | Phạm vi mặc định và điều cần nhớ |
| --- | --- | --- |
| `LocaleContextHolder` | `LocaleContext`, có thể kèm timezone | ThreadLocal của Spring; dùng cho localization/formatting |
| `RequestContextHolder` | `RequestAttributes`, thường `ServletRequestAttributes` | Gắn với request/thread; request object có vòng đời hữu hạn |
| `SecurityContextHolder` | `SecurityContext` với `Authentication` | Thường thread-local; propagation phải có cơ chế bảo mật phù hợp |
| SLF4J `MDC` | Map chuỗi phục vụ structured logging, correlation ID | Thường thread-local trong log backend; cần copy/restore |
| Micrometer Observation/Tracing | Observation, trace/span context | Cơ chế riêng được instrumentation và context propagation quản lý |
| `TransactionSynchronizationManager` | Resource/transaction synchronization state | Ràng buộc với thread/transaction; không copy tùy tiện sang worker |
| HTTP session | Trạng thái phiên người dùng, lưu server-side | Không phải ThreadLocal; không biến session thành context của task một cách ngầm định |
| `@SessionAttributes` | Model attributes được giữ qua các request MVC | Là lifecycle của model/session, **khác** async thread context |

Đặc biệt, **ngôn ngữ được resolve từ HTTP request** không tự biến thành thuộc tính của task background. Bạn phải quyết định: một tác vụ cần đúng locale của người dùng lúc submit, hay locale mới được chọn sau đó? Thường nên capture một giá trị bất biến (ví dụ `Locale` và user ID đã được cho phép) ngay khi lên lịch.

## 3. LocaleContextHolder và RequestContextHolder trong MVC

Spring MVC thường xác định locale từ `LocaleResolver`/`LocaleContextResolver`. Ví dụ cấu hình mặc định có thể sử dụng `Accept-Language: vi-VN`. Trong request handler, Spring gắn locale context với thread phục vụ request:

~~~java
@GetMapping("/welcome")
@ResponseBody
String welcome() {
    Locale locale = LocaleContextHolder.getLocale();
    return locale.toLanguageTag();
}
~~~

Trong ngữ cảnh request thích hợp với `Accept-Language: vi-VN`, kết quả là `vi-VN`. Đây là fragment handler; cần class/controller và imports nếu đưa vào ứng dụng.

**Phân biệt:**

- `LocaleContextHolder.getLocale()` trả `Locale` theo context hiện tại hoặc fallback locale. Việc gọi thành công **không chứng minh** locale của request đã được propagate.
- `LocaleContextHolder.getLocaleContext()` cho phép lấy đối tượng `LocaleContext`; nếu ngữ cảnh hỗ trợ timezone, phần đó có thể được bảo toàn khi propagate đúng context.
- `LocaleContextHolder.setLocaleContext(context)` thiết lập context cho thread đang chạy; `resetLocaleContext()` xóa nó khỏi thread.
- `RequestContextHolder.currentRequestAttributes()` có thể ném `IllegalStateException` nếu không có request attributes gắn vào thread. `getRequestAttributes()` trả null khi không có.

### 3.1. Vì sao không copy ServletRequestAttributes bừa bãi?

Request chỉ sống trong vòng đời HTTP do Servlet container điều khiển. Khi `@Async` tạo **background job tiếp tục chạy sau khi response đã hoàn tất**, việc giữ tham chiếu `HttpServletRequest`, `HttpSession` hoặc `ServletRequestAttributes` có thể gây:

1. Truy cập request không còn hợp lệ sau request completion.
2. Giữ reference tới object lớn, session hoặc dữ liệu nhạy cảm lâu hơn dự định.
3. Giả định session/security state vẫn bất biến, trong khi người dùng có thể đăng xuất hoặc đổi quyền.
4. Race condition nếu nhiều threads cùng truy cập request/response mutable.

**Ưu tiên:** chắt lọc dữ liệu cần thiết trên request thread và truyền rõ vào task:

~~~java
record JobContext(
        String requestId,
        Locale locale,
        String authorizedUserId) {
}

// Lấy trên request thread, sau khi đã kiểm tra quyền.
JobContext context = new JobContext(requestId, resolvedLocale, userId);
executor.execute(() -> service.perform(jobId, context));
~~~

Đây là fragment kiến trúc, cần `requestId`, `resolvedLocale`, `userId`, `jobId` và service phù hợp. Không đưa vào immutable context password, bearer token hoặc toàn bộ session. **Một user ID không tự cấp quyền**: background job có thể cần re-check quyền hoặc service identity tùy thời điểm và chính sách.

### 3.2. Chú ý timezone

Locale như `vi-VN` không tự định nghĩa timezone. Nếu yêu cầu hiển thị giờ của người dùng, context có thể cần `ZoneId` độc lập:

~~~java
record DisplayContext(Locale locale, ZoneId zoneId) {
}
~~~

Import `java.util.Locale` và `java.time.ZoneId`. Khi gửi task vào hàng đợi hoặc tiến trình khác, gửi các giá trị bất biến có hợp đồng rõ, không dựa vào JVM default timezone.

## 4. Các loại xử lý bất đồng bộ không giống nhau

### 4.1. Spring MVC Callable

~~~java
@GetMapping("/report")
@ResponseBody
public Callable<String> report() {
    return () -> reportService.generate();
}
~~~

Spring MVC chuyển Servlet request sang async mode, submit `Callable` lên `AsyncTaskExecutor` được cấu hình, rồi sau khi có kết quả sẽ **ASYNC dispatch** để hoàn tất HTTP response.

Không nhầm việc Spring có lifecycle riêng cho MVC `Callable` với việc mọi `ThreadLocal` tùy ý đều tự theo sang thread khác. Framework và các interceptor có thể thực hiện một số context binding nhất định; với MDC/custom ThreadLocal vẫn cần kiểm thử và cấu hình có chủ đích.

### 4.2. DeferredResult

~~~java
@GetMapping("/jobs/{id}/wait")
@ResponseBody
public DeferredResult<String> waitForJob(@PathVariable String id) {
    DeferredResult<String> result = new DeferredResult<>(10_000L);
    registry.register(id, result);
    return result;
}
~~~

Khi một thread khác có kết quả:

~~~java
result.setResult("done");
// Hoặc result.setErrorResult(exception);
~~~

Đây là hai fragment mô tả ý tưởng; `registry` và `result` phải được quản lý trong ứng dụng thực. `DeferredResult` không tự tạo worker task: **ứng dụng chủ động hoàn thành nó** từ thread/callback khác. Khi kết quả đến, MVC dispatch lại để trả response. Cần xử lý `onTimeout`, `onCompletion`, cleanup registry và tránh hoàn tất hai lần.

Không giữ Servlet request hoặc locale holder xuyên suốt vòng đời DeferredResult. Capture rõ context khi đăng ký, dùng khi hoàn tất nếu cần; ngữ cảnh mới ở callback không mặc nhiên là ngữ cảnh ban đầu.

### 4.3. CompletionStage/CompletableFuture

~~~java
@GetMapping("/summary")
@ResponseBody
public CompletableFuture<String> summary() {
    return CompletableFuture.supplyAsync(() -> "ready", executor);
}
~~~

Spring MVC có thể thích ứng `CompletionStage`/`CompletableFuture` thành một kết quả HTTP async. Tuy nhiên **phần task do chính bạn submit** lên executor khác sẽ sử dụng thread của executor đó. Nếu dùng:

~~~java
CompletableFuture.supplyAsync(() -> work());
~~~

mà không chỉ định executor, Java thường dùng `ForkJoinPool.commonPool()`. Không giả định MDC, locale, SecurityContext hoặc transaction đi theo common pool.

Tương tự, các stage kiểu `thenApplyAsync`, `thenComposeAsync` hoặc callback do thư viện bên ngoài gọi có thể chạy trên thread khác. Nếu chỉ propagate **stage đầu tiên** mà stage sau chạy trên executor khác, context lại có thể mất. Thiết kế propagation tại **mọi boundary có đổi thread**.

### 4.4. @Async

~~~java
@Service
class ReportService {
    @Async("contextExecutor")
    public CompletableFuture<String> generate() {
        return CompletableFuture.completedFuture("done");
    }
}
~~~

`@Async` trong Spring thường sử dụng proxy/interceptor để submit method invocation lên executor. Phải bật `@EnableAsync` và gọi method qua Spring-managed bean/proxy (ví dụ controller inject service). **Self-invocation** từ một method của cùng instance gọi method `@Async` khác thường bỏ qua proxy nên chạy đồng bộ.

`@Async` là **cách chạy method ở worker**, không phải cơ chế tự propagate mọi loại context. `ContextPropagatingTaskDecorator` hoặc cơ chế tương đương phải được gắn đúng executor.

### 4.5. Bảng so sánh

| Công cụ | Ai chọn/schedule worker? | HTTP response còn chờ? | Context cần chú ý |
| --- | --- | --- | --- |
| MVC `Callable<T>` | MVC AsyncTaskExecutor | Có, Servlet async | Lifecycle MVC, custom MDC/tracing, executor |
| MVC `DeferredResult<T>` | Ứng dụng/event source tạo kết quả | Có, tới result/timeout | Callback thread và cleanup |
| Controller trả `CompletableFuture<T>` | Bạn hoặc library tạo future | Có, tới future complete/timeout | Executor của tất cả stages |
| Service method `@Async` | Spring AOP async executor | Không tự bảo đảm controller đang chờ; phụ thuộc return type | Proxy, TaskDecorator, SecurityContext |
| Scheduled/background job | Scheduler/queue | Thường không còn HTTP request | Truyền context immutable/re-auth theo chính sách |
| Reactor/WebFlux | Reactive scheduling/context | Theo reactive pipeline | Reactor Context, không tự dựa ThreadLocal |

Khi Servlet request chuyển async, container thread ban đầu được trả về pool nhưng HTTP response có thể vẫn mở. Kết thúc async processing thường gồm một ASYNC dispatch; filter mappings và async-supported phải được cấu hình đúng để middleware hoạt động như mong đợi.

## 5. Quy tắc capture → install → execute → restore

Một cách an toàn để chuyển context qua thread boundary:

~~~text
HTTP request thread                         Pool worker thread
LocaleContextHolder = vi_VN
MDC.requestId = abc-123
       |
       | submit(task)
       | -- CAPTURE snapshot --> locale=vi_VN, mdc={requestId:abc-123}
       |                         |
       |                         | INSTALL vào worker
       |                         | EXECUTE công việc
       |                         | finally RESTORE worker state cũ
       |                         |
HTTP handler được giải phóng                Worker tái sử dụng an toàn
~~~

Quan trọng:

- **Capture ở thời điểm submit**, khi caller thread còn context hợp lệ. Nếu capture lúc worker chạy, bạn có thể đã mất context nguồn.
- Chỉ chuyển các trường **thực sự cần thiết**, không copy toàn bộ request object hoặc ThreadLocal không rõ tác dụng.
- **Restore trạng thái trước đó**, không chỉ gán null tùy tiện; một worker có thể nằm trong một context lồng nhau.
- Dùng `try/finally` kể cả khi task ném exception hay bị hủy.
- Chọn context lifetime đúng: HTTP request đã hoàn thành thì background task không còn quyền phụ thuộc live ServletRequest.
- Bảo mật: không giữ một authentication object mutable từ request quá lâu hoặc truyền nhầm sang task của người khác.

### 5.1. TaskDecorator ở Spring Framework

Spring cung cấp `org.springframework.core.task.TaskDecorator`. Với `ThreadPoolTaskExecutor`:

~~~java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(2);
executor.setMaxPoolSize(4);
executor.setQueueCapacity(50);
executor.setThreadNamePrefix("ctx-");
executor.setTaskDecorator(new LocaleAndMdcTaskDecorator());
executor.initialize();
~~~

Đây là fragment từ lớp cấu hình hoàn chỉnh ở mục 9. `TaskDecorator` bọc callback chuẩn bị chạy. Nó có thể được áp dụng cho wrapper Runnable của task chứ không nhất thiết là đúng Runnable do user gửi; vì vậy đừng dựa vào TaskDecorator để bắt/quan sát mọi exception trong các `Future`. Mục đích ở đây là **context propagation**, không phải toàn bộ error handling.

### 5.2. Dọn context quan trọng như truyền context

Sai:

~~~java
return () -> {
    MDC.put("requestId", capturedId);
    task.run();  // Nếu task ném exception, MDC tồn tại trên pooled thread.
};
~~~

Đúng về nguyên tắc:

~~~java
return () -> {
    String previous = MDC.get("requestId");
    try {
        MDC.put("requestId", capturedId);
        task.run();
    } finally {
        if (previous == null) MDC.remove("requestId");
        else MDC.put("requestId", previous);
    }
};
~~~

Đây là phiên bản đơn giản cho **một key**. Muốn propagate đầy đủ MDC, dùng `MDC.getCopyOfContextMap()` và `MDC.setContextMap(...)` kèm clear/restore (xem dự án). Dù vậy, chỉ có những key đã qua privacy review mới nên được đặt trong MDC; context propagation không thay thế redaction.

## 6. SecurityContext: truyền một cách có kiểm soát

Spring Security thông thường quản lý `SecurityContext` bằng `SecurityContextHolder`, thường dựa trên thread-local. Worker mới không tự có danh tính của request.

Spring Security cung cấp:

- `DelegatingSecurityContextRunnable` / `DelegatingSecurityContextCallable`.
- `DelegatingSecurityContextExecutor`, `DelegatingSecurityContextTaskExecutor` và `DelegatingSecurityContextAsyncTaskExecutor`.
- Tích hợp với các dạng Spring MVC async nhất định, ví dụ Callable; không được suy ra rằng mọi executor tùy biến hay background job được bảo vệ giống nhau.

Một ví dụ minh họa executor wrapper:

~~~java
AsyncTaskExecutor delegate = configuredExecutor;
AsyncTaskExecutor secured =
        new DelegatingSecurityContextAsyncTaskExecutor(delegate);
~~~

Cần import `org.springframework.core.task.AsyncTaskExecutor` và `org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor`, cùng dependency Spring Security. Với constructor nhận delegate mà không cấp explicit context, wrapper capture security context khi task được submit theo hợp đồng của class, chạy dưới context đó và dọn sau khi xong.

**Không đặt `SecurityContextHolder.MODE_INHERITABLETHREADLOCAL` như một "chữa nhanh" cho pooled workers.** Thread trong pool được tạo rồi tái dùng ở những thời điểm khác nhau, giá trị inherited có thể cũ hoặc không tương ứng request. Cũng đừng sao chép một mutable `Authentication` rồi tùy tiện chuyển qua nhiều task không đáng tin cậy.

Quan trọng hơn, background job dài hạn nên nhận **user ID, tenant ID và các business facts cần thiết** rồi xác định cơ chế xác thực/phân quyền thích hợp khi thực thi; việc copy context tại thời điểm enqueue không có nghĩa quyền đó vẫn hợp lệ nhiều giờ sau. Tuân thủ least privilege và không tiết lộ token.

## 7. Micrometer Context Propagation và tracing

Spring Framework 6.2 có thể tích hợp thư viện **Micrometer Context Propagation**. Nó cung cấp cơ chế chụp các `ThreadLocalAccessor` đã đăng ký và phục hồi giá trị qua thread boundaries.

Ví dụ hướng dùng `ContextSnapshot` (minh họa, cần dependency và registry phù hợp):

~~~java
ContextSnapshot snapshot = ContextSnapshot.captureAll();

executor.execute(() -> {
    try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
        process();
    }
});
~~~

Import:

~~~java
import io.micrometer.context.ContextSnapshot;
~~~

**Không tự suy ra `captureAll()` sẽ chứa mọi ThreadLocal.** Chỉ những loại context có `ThreadLocalAccessor` đã được đăng ký mới được capture/restore. Spring cung cấp `LocaleContextThreadLocalAccessor` và `RequestAttributesThreadLocalAccessor`, nhưng trong tài liệu Spring MVC 6.2 các accessor này **không được đăng ký tự động**; cần đăng ký với `ContextRegistry` nếu muốn dùng. Xem xét đặc biệt kỹ request attributes lifetime.

Spring 6.1+ có `ContextPropagatingTaskDecorator`:

~~~java
executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
~~~

Import `org.springframework.core.task.support.ContextPropagatingTaskDecorator`; cần thư viện Micrometer Context Propagation thích hợp trên classpath. Decorator này hỗ trợ logging/observation context khi có accessor đúng; nó gây overhead khi tasks rất nhỏ và không thay thế quản lý executor capacity, timeouts, security authorization hay request lifecycle.

### 7.1. Tracing xuyên thread và xuyên dịch vụ là hai việc khác nhau

Trong cùng process, task decorator/context snapshot có thể restore tracing/observation context cho worker. Khi Service A gọi Service B qua HTTP, cần **inject/extract tracing headers** theo chuẩn như W3C `traceparent` qua HTTP client instrumentation. Việc có MDC.requestId trên A không tự làm B nhận đúng ID.

Nếu ứng dụng đã dùng Micrometer Tracing/OpenTelemetry, ưu tiên cơ chế context propagation/instrumentation có sẵn của hệ đó; không tự tạo thêm hệ span/trace giả mạo. Một trace có thể gồm nhiều span, và custom requestId không bắt buộc bằng traceId.

### 7.2. Tránh double propagation và context cũ

Khi gắn nhiều task decorators, security wrappers, tracing interceptors hoặc custom MDC propagation lên cùng executor, phải xác định thứ tự và quyền sở hữu key. Nếu hai cơ chế ghi cùng key MDC/LocaleContextHolder, giá trị sau cùng có thể không như mong đợi. Hãy test dưới nhiều requests đồng thời, bảo đảm restore đúng scope trước khi worker nhận task mới.

## 8. Hướng sản xuất: chọn mô hình đúng theo nhu cầu

| Mục đích | Hướng khuyến nghị |
| --- | --- |
| Formatter/message theo locale trong task ngắn | Truyền `Locale`/`ZoneId` tường minh, hoặc decorator đã kiểm thử |
| Request ID cho logs `@Async` | Capture/copy MDC tại submission; restore/clear trong worker |
| Spring Security authentication tại task ngắn | Security delegating executor hoặc integration được hỗ trợ |
| Task nền kéo dài sau HTTP response | Truyền immutable business context, không giữ ServletRequest |
| Trace cross-thread/cross-service | Micrometer/OpenTelemetry context propagation và HTTP instrumentation |
| Async trả HTTP response | MVC Callable/DeferredResult/CompletionStage + timeout/interceptor/ASYNC dispatch |
| Chờ external event | DeferredResult hoặc asynchronous messaging với lifecycle cleanup |
| Nhiều công việc rất nhỏ | Đánh giá overhead snapshot/decorator, batch và pool sizing |
| Job qua queue/message broker | Serialize explicit metadata được phép, không gửi Java ThreadLocal/SecurityContext object |

Một số ranh giới quan trọng:

- **Transaction:** transaction-bound resources của `@Transactional` không tự chuyển theo pooled worker. Không copy `TransactionSynchronizationManager` sang thread khác để "chia sẻ" một database transaction. Hãy mở transaction mới theo nghiệp vụ hoặc tổ chức lại boundary.
- **Persistence:** JPA entity lazy proxies có thể không truy cập được ngoài transaction; DTO/projection có dữ liệu cần thiết nên được chuẩn bị trước khi gửi background job.
- **Timeout/cancellation:** nếu HTTP async timeout trước khi task hoàn tất, worker có thể vẫn đang chạy tùy cấu hình; cần xử lý cancel, shutdown, idempotency và tránh ghi response khi đã committed.
- **Thread pool:** queue không giới hạn có thể tích tụ tasks và giữ captured context/PII quá lâu. Chọn pool/queue/rejection policy có giám sát.
- **Error handling:** exception trong `CompletableFuture` cần được xử lý với `exceptionally`, `handle` hoặc MVC async exception path phù hợp; không bỏ qua future lỗi.
- **MDC memory/privacy:** đừng capture toàn bộ MDC nếu ứng dụng chứa token/PII trong đó. Lọc các key như requestId/traceId đã được cho phép.
- **ASYNC dispatch:** Servlet filters có thể cần mapping `DispatcherType.ASYNC` và `asyncSupported=true` nếu logic phải chạy ở lần dispatch sau. `OncePerRequestFilter` có `shouldNotFilterAsyncDispatch()`; default hành vi và cấu hình thực tế cần được test.
- **CompletableFuture chain:** mỗi `thenApplyAsync` trên executor khác là một thread boundary mới; propagation không "đi theo đối tượng future" một cách thần kỳ.

## 9. Dự án Spring Boot chạy được: chuyển Locale + MDC qua @Async

Demo gửi `GET /api/greeting?name=Lan` cùng `Accept-Language: vi-VN`. Controller gọi **Spring bean khác** có method `@Async("contextExecutor")`. Decorator trên executor capture **LocaleContext** và **MDC** tại thời điểm submit, khôi phục trong worker rồi restore trạng thái thread cũ.

HTTP response chứa `greeting` theo ngôn ngữ request, `locale`, `requestId` và tên worker thread để quan sát.

### 9.1. Cấu trúc

~~~text
async-context-demo/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── AsyncContextApplication.java
    │   ├── RequestIdFilter.java
    │   ├── AsyncExecutionConfig.java
    │   ├── GreetingService.java
    │   └── GreetingController.java
    └── test/java/demo/AsyncGreetingTest.java
~~~

### 9.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>async-context-demo</artifactId>
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

Không cần Micrometer Context Propagation cho demo vì `TaskDecorator` tự triển khai copy hai loại context cụ thể. Nếu muốn đổi sang `ContextPropagatingTaskDecorator` cần bổ sung dependency và đăng ký accessor phù hợp với phiên bản framework.

### 9.3. AsyncContextApplication.java

~~~java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AsyncContextApplication {
    public static void main(String[] args) {
        SpringApplication.run(AsyncContextApplication.class, args);
    }
}
~~~

`@EnableAsync` kích hoạt Spring async proxy. Để proxy intercept, controller phải gọi method `@Async` qua bean service được inject, không self-invocation.

### 9.4. RequestIdFilter.java

~~~java
package demo;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        // Sinh ID mới, không tin header X-Request-ID tùy ý từ client.
        String id = UUID.randomUUID().toString();
        String oldId = MDC.get("requestId");

        request.setAttribute("requestId", id);
        response.setHeader("X-Request-ID", id);
        MDC.put("requestId", id);

        try {
            chain.doFilter(request, response);
        } finally {
            if (oldId == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", oldId);
            }
        }
    }
}
~~~

Filter gắn requestId trên thread servlet trước khi controller gọi service. `finally` ngăn dữ liệu cũ tồn tại khi servlet thread được tái sử dụng. Đây là demo request đồng bộ trước lần ASYNC dispatch; response header tồn tại qua lifecycle của cùng HTTP response. Với cấu hình real-world cho nhiều dispatches (ERROR/ASYNC), cần test và điều chỉnh filter registration thay vì mặc định tạo ID mới ở mỗi dispatch.

### 9.5. AsyncExecutionConfig.java

~~~java
package demo;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncExecutionConfig {

    @Bean("contextExecutor")
    public ThreadPoolTaskExecutor contextExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ctx-");
        executor.setTaskDecorator(new LocaleAndMdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    static final class LocaleAndMdcTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable delegate) {
            // Capture tại caller thread (thời điểm submit).
            LocaleContext capturedLocale =
                    LocaleContextHolder.getLocaleContext();
            Map<String, String> capturedMdc =
                    MDC.getCopyOfContextMap();

            return () -> {
                // Worker có thể từng chạy task khác: giữ trạng thái cũ.
                LocaleContext previousLocale =
                        LocaleContextHolder.getLocaleContext();
                Map<String, String> previousMdc =
                        MDC.getCopyOfContextMap();

                try {
                    if (capturedLocale == null) {
                        LocaleContextHolder.resetLocaleContext();
                    } else {
                        LocaleContextHolder.setLocaleContext(capturedLocale);
                    }

                    if (capturedMdc == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(capturedMdc);
                    }

                    delegate.run();
                } finally {
                    // Khôi phục trạng thái trước đó dù task lỗi.
                    if (previousLocale == null) {
                        LocaleContextHolder.resetLocaleContext();
                    } else {
                        LocaleContextHolder.setLocaleContext(previousLocale);
                    }

                    if (previousMdc == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previousMdc);
                    }
                }
            };
        }
    }
}
~~~

Decorator này chỉ làm hai việc có chủ ý: truyền `LocaleContext` và map MDC. Nó **không** propagate `SecurityContext`, `ServletRequestAttributes` hay transaction. Việc copy cả map MDC phù hợp để minh họa; ứng dụng có key nhạy cảm nên lọc allowlist trước khi capture. Với thread pool, cleanup là yêu cầu bảo mật, không chỉ một vấn đề thẩm mỹ log.

Nếu số lượng tác vụ rất lớn/nhỏ, mỗi lần capture/copy map gây overhead; cân nhắc quan sát hiệu năng và dùng một thư viện propagation thống nhất nếu kiến trúc yêu cầu.

### 9.6. GreetingService.java

~~~java
package demo;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.slf4j.MDC;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

record GreetingResponse(
        String greeting,
        String locale,
        String requestId,
        String workerThread) {
}

@Service
public class GreetingService {

    @Async("contextExecutor")
    public CompletableFuture<GreetingResponse> greet(String name) {
        Locale locale = LocaleContextHolder.getLocale();

        String greeting = "vi".equals(locale.getLanguage())
                ? "Xin chào, " + name
                : "Hello, " + name;

        GreetingResponse response = new GreetingResponse(
                greeting,
                locale.toLanguageTag(),
                MDC.get("requestId"),
                Thread.currentThread().getName());

        return CompletableFuture.completedFuture(response);
    }
}
~~~

Mục tiêu ở đây là quan sát Locale và MDC trong worker thread. Bản địa hóa thực tế nên dùng `MessageSource`/resource bundles thay vì toán tử ternary khi số lượng thông điệp nhiều.

Khi `@Async` interceptor điều phối lời gọi, method service chạy trên `contextExecutor`. `CompletableFuture.completedFuture(...)` được tạo **bên trong worker**, và Spring async proxy cung cấp future để controller có thể trả kết quả sau khi task hoàn tất.

### 9.7. GreetingController.java

~~~java
package demo;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {
    private final GreetingService service;

    public GreetingController(GreetingService service) {
        this.service = service;
    }

    @GetMapping(path = "/api/greeting",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<GreetingResponse> greeting(
            @RequestParam(defaultValue = "Lan") String name) {
        return service.greet(name);
    }
}
~~~

Controller nhận request trên Servlet thread, gọi Spring-managed service, nhận future và trả cho MVC. MVC xử lý async response khi future có kết quả. Không cần gọi `future.join()` trong controller: join sẽ block Servlet thread và làm mất phần lớn lợi ích của async return type.

### 9.8. Chạy và kết quả HTTP

Tại thư mục có `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

Gọi request tiếng Việt:

~~~bash
curl -i -G 'http://localhost:8080/api/greeting' \
  -H 'Accept-Language: vi-VN' \
  --data-urlencode 'name=Lan'
~~~

Body tương đương JSON dưới đây (request ID và tên thread có tính động):

~~~json
{
  "greeting": "Xin chào, Lan",
  "locale": "vi-VN",
  "requestId": "f64a2c8b-5e8c-4b02-9053-090122b843c4",
  "workerThread": "ctx-1"
}
~~~

HTTP response có header `X-Request-ID` với **cùng giá trị** body.requestId của request đó. Ví dụ UUID chỉ minh họa định dạng, không phải giá trị cố định. Nếu gọi với `Accept-Language: en-US`, trường `greeting` là `Hello, Lan` và `locale` là `en-US` trong cấu hình locale resolver mặc định được dùng ở demo.

### 9.9. Kiểm thử MockMvc async dispatch

Tạo file `src/test/java/demo/AsyncGreetingTest.java`:

~~~java
package demo;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AsyncGreetingTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void propagatesVietnameseLocaleAndRequestId() throws Exception {
        MvcResult initial = mvc.perform(get("/api/greeting")
                .param("name", "Lan")
                .header("Accept-Language", "vi-VN"))
            .andExpect(request().asyncStarted())
            .andReturn();

        String id = initial.getResponse().getHeader("X-Request-ID");
        assertNotNull(id);
        UUID.fromString(id);

        MvcResult finalResponse = mvc.perform(asyncDispatch(initial))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode body = mapper.readTree(
                finalResponse.getResponse().getContentAsString());

        assertEquals("Xin chào, Lan", body.path("greeting").asText());
        assertEquals("vi-VN", body.path("locale").asText());
        assertEquals(id, body.path("requestId").asText());
        assertTrue(body.path("workerThread").asText().startsWith("ctx-"));
    }

    @Test
    void usesEnglishLocaleForAnotherRequest() throws Exception {
        MvcResult initial = mvc.perform(get("/api/greeting")
                .param("name", "Lan")
                .header("Accept-Language", "en-US"))
            .andExpect(request().asyncStarted())
            .andReturn();

        MvcResult finalResponse = mvc.perform(asyncDispatch(initial))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode body = mapper.readTree(
                finalResponse.getResponse().getContentAsString());
        assertEquals("Hello, Lan", body.path("greeting").asText());
        assertEquals("en-US", body.path("locale").asText());
        assertEquals(
                initial.getResponse().getHeader("X-Request-ID"),
                body.path("requestId").asText());
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

Hai test xác nhận HTTP MVC async lifecycle (`asyncStarted` và `asyncDispatch`), locale của từng request, mã đối chiếu từ header sang worker và tên worker. Việc chạy tuần tự hai test chưa đủ để chứng minh không có data leak trong mọi trường hợp; production cần stress test đồng thời nhiều locales/users và kiểm tra cả exception paths.

**Giới hạn demo:** Không bật Spring Security, không truyền session/ServletRequest/transaction qua worker, không kết nối database, không có distributed tracing hay message queue. Nếu tích hợp thêm các thành phần đó, phải chọn cơ chế propagation thích hợp cho chúng; TaskDecorator thủ công chỉ lo LocaleContext và MDC.

## 10. Ví dụ nguy hiểm và cách sửa

### 10.1. Sai: bắt ThreadLocal ngay trong worker

~~~java
CompletableFuture.supplyAsync(() -> {
    // Worker có thể không có Locale/RequestAttributes của HTTP request.
    return LocaleContextHolder.getLocale().toLanguageTag();
});
~~~

Cách tốt hơn khi chỉ cần ngôn ngữ:

~~~java
Locale locale = LocaleContextHolder.getLocale();
return CompletableFuture.supplyAsync(
        () -> renderMessage(locale),
        boundedExecutor);
~~~

Đây là fragment: `renderMessage` và `boundedExecutor` do ứng dụng cung cấp. Truyền `Locale` là lựa chọn đơn giản, giảm coupling với ThreadLocal. Nếu có nhiều lớp thư viện nội bộ dựa trên LocaleContextHolder, decorator/snapshot mới thực sự tiện.

### 10.2. Sai: luôn set MDC mà không clear

~~~java
executor.execute(() -> {
    MDC.put("requestId", id);
    service.run();
});
~~~

Nếu service lỗi, `requestId` có thể tồn tại trên pooled thread và lẫn sang request khác. Dùng `finally` restore MDC hoặc executor TaskDecorator đã kiểm thử.

### 10.3. Sai: tạo thêm thread thủ công mỗi request

~~~java
new Thread(() -> expensiveWork()).start();
~~~

Cách này bỏ qua pool management, shutdown, queue, context, error visibility và rate limits. Dùng executor được quản lý, backpressure và timeout rõ ràng; với công việc lâu/durable dùng queue/job system.

### 10.4. Sai: giữ HttpServletRequest trong background job dài

~~~java
executor.execute(() ->
        saveAuditRecord(request.getHeader("Authorization")));
~~~

Đoạn này vừa giữ reference request quá vòng đời cần thiết, vừa chuyển bearer token vào job/log. Nếu audit cần danh tính đã được kiểm tra, chuẩn bị một `AuditActorId` hoặc context đã được phép trên request thread rồi đưa vào task.

### 10.5. Sai: copy transaction bằng ThreadLocal

Một transaction gắn với connection và tài nguyên nhất định. Copy `TransactionSynchronizationManager` sang thread khác không phải cách chia sẻ transaction an toàn. Hãy mở transaction trên worker/service với `@Transactional` ở đúng proxy boundary khi nghiệp vụ cần, hoặc đổi thiết kế để chỉ có một transaction boundary.

### 10.6. Sai: coi CompletableFuture luôn non-blocking I/O

`CompletableFuture.supplyAsync(() -> jdbcRepository.findAll(), executor)` giải phóng Servlet thread trong khi worker chờ JDBC, nhưng I/O JDBC đó **vẫn blocking trên worker thread**. Nếu tải cao, worker pool vẫn có thể cạn. Cần chọn pool sizing, query timeout và kiến trúc phù hợp thay vì chỉ thêm `@Async`.

## 11. Checklist debug khi ngữ cảnh biến mất

1. **Xác định thread boundary:** controller → @Async service? common pool? thenApplyAsync? scheduled callback? HTTP client?
2. **Kiểm tra capture ở đâu:** caller thread có locale, MDC, SecurityContext đúng vào lúc submit không?
3. **Kiểm tra executor thật:** @Async có đúng bean `contextExecutor`? Có bị self-invocation bỏ qua proxy không?
4. **Kiểm tra decorator/wrapper:** được gắn lên executor thực thi task chưa? Có accessor đúng nếu dùng Micrometer không?
5. **Kiểm tra cleanup:** có finally restore trạng thái cũ khi lỗi, timeout hoặc cancellation không?
6. **Kiểm tra duration:** task có tiếp tục sau HTTP request/session kết thúc? Có đang giữ ServletRequestAttributes quá lâu?
7. **Kiểm tra security:** có truyền Authentication không phù hợp? Có cần re-authorize vào thời điểm job chạy?
8. **Kiểm tra locale:** header Accept-Language có được locale resolver chấp nhận? default locale có đang che lỗi propagation?
9. **Kiểm tra tracing:** đã có tracing library tự động propagate chưa? Có nhiều wrappers ghi đè context?
10. **Kiểm tra production:** bounded thread pool, queue capacity, rejection, error handler, timeout, data retention và multi-node.
11. **Kiểm tra test:** một request vi-VN, một request en-US, chạy concurrent, assert MDC requestId không bị lẫn; test error path.
12. **Kiểm tra MVC dispatch:** filter/interceptor có chạy trên ASYNC dispatch theo yêu cầu không? Tránh tạo request ID mới ở lần dispatch sau.

Một lỗi phổ biến trong test là **nhìn thấy đúng locale mặc định rồi tưởng propagation hoạt động**. Hãy test hai ngôn ngữ khác nhau, cấu hình locale resolver rõ và xác minh worker thread thực sự khác thread ban đầu.

## 12. Tình huống mở rộng

### 12.1. Async gửi email sau khi HTTP trả 202 Accepted

API nhận yêu cầu và trả 202, job gửi email tiếp tục trong queue. Không nên giữ `LocaleContextHolder` hay HTTP session để dựa vào thời điểm gửi email có thể xảy ra muộn. Thay vào đó tạo job payload:

~~~java
record EmailJob(
        String templateId,
        String recipientId,
        String languageTag,
        String businessCorrelationId) {}
~~~

Worker tái tạo `Locale.forLanguageTag(languageTag)` và kiểm tra business context cần thiết trước khi gửi. Nếu có thay đổi ngôn ngữ người dùng giữa lúc enqueue và lúc thực hiện, chính sách phải xác định dùng snapshot lúc enqueue hay ưu tiên ngôn ngữ mới.

### 12.2. Worker dùng thời gian theo vùng

Cần cả `ZoneId` và locale, ví dụ người dùng ở Việt Nam:

~~~java
Locale locale = Locale.forLanguageTag("vi-VN");
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
ZonedDateTime time = instant.atZone(zone);
~~~

Cần imports từ `java.util` và `java.time`; biến `instant` phải là một `Instant` đã xác định. Locale kiểm soát ngôn ngữ/format, ZoneId kiểm soát quy tắc giờ; truyền nhầm một cái không thay thế cái kia.

### 12.3. Nhiều task phụ thuộc nhau

Với `CompletableFuture`, một callback `thenApply` không hậu tố Async có thể chạy trên thread hoàn thành stage trước, không chắc là request thread. Callback `thenApplyAsync` có thể chạy ở common pool nếu không cấp executor. Mỗi stage sử dụng thông tin context cần contract rõ; không nên dựa vào "thread tình cờ" đã có đúng giá trị.

### 12.4. Logging trên async callback

Nếu chỉ cần request ID để đối chiếu log, việc truyền nó như giá trị:

~~~java
String id = MDC.get("requestId");
executor.execute(() -> {
    try (MDC.MDCCloseable ignored =
            MDC.putCloseable("requestId", id)) {
        service.run();
    }
});
~~~

là một phương án đơn giản khi bạn kiểm soát một callback cụ thể. Chú ý `MDC.putCloseable` sẽ remove key khi scope kết thúc, không đảm bảo restore giá trị trước nếu worker đang có key đó; với nested context hãy dùng snapshot-and-restore đầy đủ như TaskDecorator ở mục 9. Không đưa bí mật vào key ID.

## 13. Bài tập tự kiểm tra

**Bài 1 — ThreadLocal cơ bản:** Đọc lại chương trình ở mục 2 và giải thích vì sao stdout đầu tiên là `true`. Bỏ finally trong wrapped task và mô tả rủi ro khi worker được tái sử dụng.

**Bài 2 — Localization:** Một request `Accept-Language: vi-VN` gọi `CompletableFuture.supplyAsync()` và service in English. Hãy xác định chỗ locale mất và sửa bằng cách truyền `Locale` vào lambda thay vì đọc ThreadLocal trong worker.

**Bài 3 — MDC:** Viết TaskDecorator capture `requestId` khi submit và restore previous value sau task. Tạo 100 requests song song với hai locales khác nhau; kiểm tra id không bị lẫn giữa responses.

**Bài 4 — Security:** So sánh copy SecurityContext vào task ngắn với job nền chạy sau 10 giờ. Vì sao job lâu nên lưu user/tenant ID và kiểm tra quyền theo chính sách riêng?

**Bài 5 — Spring MVC:** Phân biệt `Callable<T>`, `DeferredResult<T>`, controller trả `CompletableFuture<T>` và service `@Async`. Ai tạo worker, ai giữ HTTP response đang chờ, và async dispatch xuất hiện ở đâu?

**Bài 6 — Timezone:** Một locale `en-US` không đủ xác định múi giờ. Thiết kế `DisplayContext(Locale, ZoneId)` dùng để format một `Instant` trên worker.

**Bài 7 — Micrometer:** Nêu điều kiện để `ContextSnapshot.captureAll()` biết cách chụp `LocaleContextHolder`, và vì sao không nên propagate `ServletRequestAttributes` của request đã kết thúc.

**Bài 8 — CompletableFuture:** Có chuỗi `supplyAsync(..., executorA).thenApplyAsync(..., executorB)`. Vì sao context có thể đúng ở A nhưng mất ở B? Đề xuất decorator cho cả hai hoặc truyền explicit context.

**Bài 9 — Kiểm thử:** Viết MockMvc test cho hai requests Accept-Language khác nhau, dùng `asyncDispatch` và assert request ID từ header khớp trường JSON worker đã trả.

**Bài 10 — Vận hành:** Đề xuất metrics/alerting khi pool queue đầy, task timeout, rejection và tỷ lệ lỗi 5xx tăng. Vì sao không nên retry vô hạn POST không idempotent?

## 14. Ghi nhớ

- Context **gắn với ThreadLocal** không tự theo thread mới. Propagation phải có chủ đích ở mỗi boundary.
- `LocaleContextHolder` xử lý ngôn ngữ; `RequestContextHolder` gắn request; `MDC` dành cho logging; `SecurityContextHolder` thuộc bảo mật. Chúng không thể hoán đổi tùy tiện.
- Ưu tiên **truyền immutable business context tường minh** cho background job, nhất là công việc tiếp tục sau HTTP request.
- Với `@Async` và ThreadPoolTaskExecutor, dùng `TaskDecorator` **capture on submit, install on worker, restore in finally**.
- Micrometer Context Propagation và Spring Security executors hỗ trợ propagation có điều kiện; chọn đúng dependency, accessor, executor và lifetime.
- MVC async return types vẫn trải qua Servlet async/ASYNC dispatch. Không tự động coi `@Async` là non-blocking I/O hoặc bảo đảm mọi context được truyền.
- Test nhiều locales/requests đồng thời và exception paths; tránh stale context, rò dữ liệu và lỗi bảo mật.

## 15. Tài liệu chính thức

- [Spring Framework 6.2 — Asynchronous Requests](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html): Callable, DeferredResult, CompletionStage, async processing, context propagation, executor configuration.
- [Spring Framework 6.2 — TaskDecorator](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/task/TaskDecorator.html): interface cho task context wrapping.
- [Spring Framework 6.2 — ContextPropagatingTaskDecorator](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html): Micrometer context propagation trong executors.
- [Spring Framework 6.2 — LocaleContextHolder](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/i18n/LocaleContextHolder.html): locale context theo thread.
- [Spring Framework 6.2 — RequestContextHolder](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/context/request/RequestContextHolder.html): request attributes theo thread.
- [Spring Security 6.5 — Concurrency Support](https://docs.spring.io/spring-security/reference/6.5/features/integrations/concurrency.html): DelegatingSecurityContextRunnable/Executor và Spring task executors.
- [Micrometer — Context Propagation](https://docs.micrometer.io/context-propagation/reference/): ThreadLocalAccessor, ContextRegistry và ContextSnapshot.
- [W3C Trace Context](https://www.w3.org/TR/trace-context/): traceparent/tracestate khi cần propagate distributed trace qua HTTP.


</details>

- [Quay lại đầu trang](#back-to-top)
