<a id="back-to-top"></a>

# Filter và interceptor

## Menu
- [Phân biệt Servlet Filter, HandlerInterceptor, AOP và ControllerAdvice](#filters-interceptors-001)
- [Filter chain, thứ tự đăng ký và các loại dispatch được áp dụng](#filters-interceptors-002)
- [OncePerRequestFilter và hành vi với async/error dispatch](#filters-interceptors-003)
- [preHandle, postHandle, afterCompletion và điều kiện gọi lại](#filters-interceptors-004)
- [Đăng ký interceptor, phạm vi đường dẫn và AsyncHandlerInterceptor](#filters-interceptors-005)
- [CharacterEncodingFilter, FormContentFilter và HiddenHttpMethodFilter](#filters-interceptors-006)
- [Bọc request/response để đọc nội dung và giới hạn bộ nhớ](#filters-interceptors-007)
- [Chọn đúng điểm mở rộng cho ghi log, đo thời gian và truyền mã đối chiếu](#filters-interceptors-008)

## <a id="filters-interceptors-001">Phân biệt Servlet Filter, HandlerInterceptor, AOP và ControllerAdvice</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt Servlet Filter, HandlerInterceptor, AOP và ControllerAdvice trong Spring MVC

> **Phạm vi:** Spring Web MVC trên Servlet stack, Java 17+ và Spring Boot 3.x / Spring Framework 6.x. Bốn cơ chế này đều có vẻ “chạy trước hoặc sau” một công việc, nhưng chúng can thiệp ở **các tầng và thời điểm khác nhau**: `Servlet Filter` bao quanh request/response trong chuỗi Servlet; `HandlerInterceptor` quan sát chuỗi thực thi handler đã được Spring MVC ánh xạ; **Spring AOP** can thiệp vào lời gọi **phương thức của Spring bean thông qua proxy**; `@ControllerAdvice` cung cấp các chức năng dùng chung cho **MVC controller**, đặc biệt xử lý lỗi và tùy biến binding/model. Không nên coi bốn loại là tương đương hoặc thay thế được cho nhau.

## 1. Bức tranh tổng thể: mỗi cơ chế đứng ở đâu?

Một request đi vào Spring Boot Servlet app theo mô hình đơn giản sau:

```text
HTTP client
    |
Servlet container (Tomcat / Jetty / ...)
    |
    v
[ Servlet Filter 1 ] --+
    |                  | before/after FilterChain.doFilter(...)
[ Servlet Filter 2 ] --+
    |
    v
DispatcherServlet
    |
    v
HandlerMapping chọn handler / HandlerExecutionChain
    |
    v
[ HandlerInterceptor.preHandle ]
    |
    v
HandlerAdapter gọi @Controller method
    |                 \
    |                  \-> @ControllerAdvice @InitBinder / @ModelAttribute
    |                                   (khi giải quyết model/binding)
    v
Controller gọi service Spring bean (proxy nếu có)
    |
    v
[ Spring AOP @Around / @Before / @After ... ]
    |
    v
service / repository / nghiệp vụ
    |
    +------ nếu exception trong MVC -------------------+
    |                                                |
    v                                                v
HTTP body / view                              MVC exception resolver
    |                                           |
    |                                  @ExceptionHandler local,
    |                                  rồi @ControllerAdvice phù hợp
    |
[ HandlerInterceptor.postHandle ] (luồng đồng bộ thành công)
    |
View rendering nếu có
    |
[ HandlerInterceptor.afterCompletion ]
    |
Quay lại filter chain (finally / logic sau doFilter)
    |
HTTP response
```

Sơ đồ cố ý lược bỏ nhiều chi tiết: validation có thể xảy ra **trước khi controller method bắt đầu**; Spring Security thường là filter chain riêng; nội dung JSON của `@ResponseBody` thường đã được ghi trong `HandlerAdapter` **trước khi** `postHandle` được gọi; async request có thêm dispatch; exception xử lý thành công có thể khiến `afterCompletion` nhận `ex = null`. Những chi tiết đó làm nên khác biệt thực tế.

### 1.1. Bảng so sánh nhanh

| Khía cạnh | Servlet Filter | HandlerInterceptor | Spring AOP | ControllerAdvice |
| --- | --- | --- | --- | --- |
| Thuộc lớp nào? | Servlet API | Spring Web MVC | Spring Core AOP/proxy | Spring Web MVC controller infrastructure |
| Điểm móc | Servlet request/response dispatch | `HandlerExecutionChain` sau khi Spring MVC chọn handler | Lời gọi method được Spring proxy intercept | Resolver cho exception, binder/model và advice đặc biệt |
| Phạm vi điển hình | HTTP path và dispatcher types đã đăng ký, bao gồm cả request không vào MVC | HandlerMapping / path patterns được đăng ký | Method của Spring-managed beans khớp pointcut | Controller được scope advice áp dụng |
| Vào trước handler? | Có, từ trước `DispatcherServlet` | `preHandle` có | Có nếu AOP pointcut bao method đang gọi | Không phải “preHandle” chung; một số hooks áp dụng khi chuẩn bị model/binder |
| Hậu xử lý | Code sau `chain.doFilter` / `finally` | `postHandle`, `afterCompletion` | `@After`, `@AfterReturning`, `@AfterThrowing`, `@Around` | `@ExceptionHandler`, `ResponseBodyAdvice` và những chức năng chuyên biệt |
| HTTP request trực tiếp? | Có, `ServletRequest`/`HttpServletRequest` | Có, `HttpServletRequest` và handler | Không mặc định cần HTTP; có thể chạy trong batch/scheduler | Có ngữ cảnh MVC tùy method/hook |
| Có thể chặn request? | Có, không gọi `chain.doFilter` | Có, `preHandle` trả `false` | Có thể thay/không gọi `proceed()` nhưng chỉ cho method có proxy | Có thể biến một exception thành HTTP response; không phải filter |
| Phù hợp nhất | HTTP policy sớm, logging/correlation, encoding, security filter | Đo/đính metadata liên quan handler MVC, locale và xử lý quanh routing | Transactions, metrics/service logging, policy xuyên layer | REST error contract, shared `@InitBinder`, shared `@ModelAttribute`, response body advice |
| Giới hạn quan trọng | Không tự biết controller method đã chọn | Không nên dùng làm lớp bảo mật chính | Self-invocation không đi qua proxy | Không tự bắt mọi lỗi từ Servlet Filter |
| Đăng ký thường gặp | `@Component`/`FilterRegistrationBean` | `WebMvcConfigurer#addInterceptors` | `@Aspect` + AOP auto-proxy config/starter | `@ControllerAdvice` / `@RestControllerAdvice` bean |

**Nguyên tắc chọn đúng:** nếu chính sách liên quan **request vào ứng dụng**, nghĩ Filter; nếu cần **handler MVC đã được map**, nghĩ Interceptor; nếu liên quan **method nghiệp vụ**, nghĩ AOP; nếu muốn **thống nhất lỗi/binding/model controller**, nghĩ ControllerAdvice.

## 2. Servlet Filter: can thiệp ở ranh giới HTTP/Servlet

`jakarta.servlet.Filter` thuộc Servlet API, không phải annotation riêng của Spring MVC. Spring Boot 3.x dùng namespace **`jakarta.servlet.*`**; mã Spring Boot 2.x cũ thường dùng **`javax.servlet.*`**, không thể trộn import tùy ý.

Cấu trúc interface:

```java
public interface Filter {
    void init(FilterConfig filterConfig) throws ServletException;
    void doFilter(ServletRequest request,
                  ServletResponse response,
                  FilterChain chain)
            throws IOException, ServletException;
    void destroy();
}
```

Đây là chữ ký mô tả Servlet API (các method init/destroy có thể có default implementation tùy API version). Điểm quan trọng là `chain.doFilter(request,response)`: filter giao quyền cho phần tiếp theo. Nếu filter **không** gọi `chain.doFilter`, request có thể dừng tại filter đó (nếu tự trả response thích hợp).

### 2.1. Filter bao quanh request như thế nào?

```java
@Override
public void doFilter(ServletRequest request,
                     ServletResponse response,
                     FilterChain chain)
        throws IOException, ServletException {

    long started = System.nanoTime();
    try {
        chain.doFilter(request, response);
    } finally {
        long duration = System.nanoTime() - started;
        System.out.println("request nanos=" + duration);
    }
}
```

Đây là **method minh họa**, cần đặt trong một lớp triển khai `Filter` và import Servlet classes. Code trước `doFilter` chạy trước chuỗi tiếp theo; code trong `finally` chạy khi chuỗi trả điều khiển hoặc ném exception. Nếu response đã committed, sửa status/header ở `finally` có thể quá muộn.

**Những request Filter có thể thấy:** route MVC, static resources, servlet endpoint khác và một số error/async dispatch tùy filter registration. Không khẳng định mọi Filter chắc chắn chạy cho **mọi dispatcher type**; việc đăng ký URL patterns và các `DispatcherType.REQUEST`, `ASYNC`, `ERROR`, `FORWARD`, `INCLUDE` quyết định phạm vi.

### 2.2. Dùng `OncePerRequestFilter` của Spring

Trong app Spring MVC, thường dùng:

```java
@Component
class CorrelationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        request.setAttribute("traceId", "demo-trace");
        response.setHeader("X-Trace-Id", "demo-trace");
        chain.doFilter(request, response);
    }
}
```

Đây là snippet class; cần import `org.springframework.web.filter.OncePerRequestFilter`, `org.springframework.stereotype.Component` và Jakarta HTTP/Servlet API. `OncePerRequestFilter` giúp tránh chạy lặp ngoài ý muốn trong một **dispatch** theo cơ chế đánh dấu request; đừng diễn giải thành “chính xác một lần duy nhất trong toàn bộ mọi thread và async/error dispatch”. Với async/error dispatch, xem các hook `shouldNotFilterAsyncDispatch()` và `shouldNotFilterErrorDispatch()` và dispatcher registration.

### 2.3. Đăng ký filter và thứ tự

Spring Boot có thể đăng ký bean `Filter` (ví dụ `@Component`) với servlet container. Khi cần giới hạn đường dẫn/độ ưu tiên rõ ràng, dùng `FilterRegistrationBean`:

```java
@Bean
FilterRegistrationBean<MyFilter> myFilterRegistration(MyFilter filter) {
    FilterRegistrationBean<MyFilter> bean = new FilterRegistrationBean<>();
    bean.setFilter(filter);
    bean.addUrlPatterns("/api/*");
    bean.setOrder(10);
    return bean;
}
```

Đây là snippet cấu hình, cần lớp `MyFilter` và import `FilterRegistrationBean`, `@Bean`; **không** đặt đồng thời nhiều registration độc lập cho cùng filter nếu không muốn lặp. `setOrder` biểu diễn thứ tự đăng ký tương đối giữa filter của Boot trong những điều kiện áp dụng. Thứ tự tuyệt đối còn phụ thuộc Security filter chain, Servlet registration và dispatcher types: dùng integration test thay vì đoán chỉ từ con số.

### 2.4. Khi nào chọn Filter?

Phù hợp với:
- Tạo trace/correlation ID trước cả MVC; ghi log request, latency tổng.
- Chuẩn hóa encoding/request wrappers và response headers cần đặt sớm.
- Chính sách áp dụng cho cả static resources hoặc non-MVC servlets trong servlet application.
- Rate limiting và bảo vệ ranh giới HTTP khi được thiết kế tại lớp filter/gateway thích hợp.
- Spring Security sử dụng `FilterChainProxy` trong Servlet filter chain, vì authentication/authorization cần diễn ra đủ sớm.

**Không phù hợp:** truy cập trực tiếp `HandlerMethod` đã chọn để biết annotation controller, trừ khi tự tích hợp sâu hoặc để Interceptor làm việc ấy. Filter chạy **trước** khi DispatcherServlet chọn MVC handler, nên không mặc định biết controller method nào sẽ được gọi.

## 3. HandlerInterceptor: can thiệp vào chuỗi thực thi MVC

`org.springframework.web.servlet.HandlerInterceptor` chạy trong Spring MVC khi `HandlerMapping` xác định handler và tạo `HandlerExecutionChain`. API chủ yếu:

```java
public interface HandlerInterceptor {
    boolean preHandle(HttpServletRequest request,
                      HttpServletResponse response,
                      Object handler) throws Exception;

    void postHandle(HttpServletRequest request,
                    HttpServletResponse response,
                    Object handler,
                    ModelAndView modelAndView) throws Exception;

    void afterCompletion(HttpServletRequest request,
                         HttpServletResponse response,
                         Object handler,
                         Exception ex) throws Exception;
}
```

Đây là chữ ký tóm tắt (interface có default method ở Spring MVC hiện đại). Ý nghĩa:

- `preHandle`: gọi **trước khi handler được invoke**. Trả `true` để tiếp tục; trả `false` để dừng phần còn lại của chain và bỏ qua handler. Nếu dừng, interceptor tự chịu trách nhiệm cung cấp response thích hợp.
- `postHandle`: gọi sau khi handler xử lý **thành công trong luồng đồng bộ**, trước khi render view truyền thống. Với `@ResponseBody` / `ResponseEntity`, body có thể đã được converter ghi vào response tại thời điểm này.
- `afterCompletion`: gọi khi xử lý request hoàn tất cho những interceptor đã chạy `preHandle` thành công, kể cả nhiều trường hợp exception. Hữu ích để dọn thread-local/metrics; tham số `ex` **không nhất thiết** phản ánh mọi exception đã được resolver xử lý.

### 3.1. Đăng ký interceptor trong Spring Boot MVC

```java
@Configuration
class WebConfiguration implements WebMvcConfigurer {

    private final TraceInterceptor traceInterceptor;

    WebConfiguration(TraceInterceptor traceInterceptor) {
        this.traceInterceptor = traceInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(traceInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/public/**");
    }
}
```

Đây là snippet cần `TraceInterceptor` là Spring bean và import `Configuration`, `WebMvcConfigurer`, `InterceptorRegistry`. Path patterns ở đây áp cho MVC handler mappings thuộc cấu hình MVC; không biến nó thành Filter cho toàn container. Controller nào match theo MVC handler mapping tương ứng thì interceptor được áp dụng.

Có thể khai báo `MappedInterceptor` bean khi cần nhiều `HandlerMapping` sử dụng chung. Nếu có các handler mappings tùy biến hoặc framework phụ, cần xác minh phạm vi đăng ký Java config. Một `HandlerInterceptor` có thể chạy cả với resource handler nếu pattern match; nó **không nhất thiết chỉ chạy cho method gắn `@RequestMapping`**.

### 3.2. HandlerMethod và thông tin controller

```java
@Override
public boolean preHandle(HttpServletRequest request,
                         HttpServletResponse response,
                         Object handler) {

    if (handler instanceof HandlerMethod hm) {
        String methodName = hm.getMethod().getName();
        request.setAttribute("controllerMethod", methodName);
    }
    return true;
}
```

Snippet trên chỉ là method. `HandlerMethod` cho phép biết method/controller đã được Spring MVC chọn. Nhưng `handler` được khai báo `Object` vì có những handler không phải `HandlerMethod` (chẳng hạn resource handler). Luôn kiểm tra `instanceof`.

### 3.3. Nhiều interceptor: thứ tự callback

Nếu interceptor A đăng ký trước B, một request đồng bộ không lỗi và cả hai `preHandle` trả `true` thường có trình tự:

```text
A.preHandle
B.preHandle
@Controller handler
B.postHandle
A.postHandle
View render (nếu có)
B.afterCompletion
A.afterCompletion
```

Điều này giống cấu trúc “bao quanh”: pre theo thứ tự đăng ký; post/after theo thứ tự ngược. Nếu B trả `false`, handler không chạy, callback `afterCompletion` chỉ áp cho những interceptor trước đó đã hoàn tất `preHandle=true`; không được mặc định `afterCompletion` của B sẽ chạy khi chính B từ chối.

### 3.4. Tại sao không thêm header trong `postHandle` của REST JSON?

Với `@ResponseBody`, `HandlerAdapter` có thể đã ghi/commit response trước khi `postHandle` chạy. Do đó:

```java
// Không đáng tin cậy cho REST response đã ghi:
@Override
public void postHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        ModelAndView modelAndView) {
    response.setHeader("X-Late", "may-not-be-sent");
}
```

Đây là anti-pattern minh họa. Nếu cần header trước khi body được ghi, đặt trong `preHandle`, Filter trước `chain.doFilter` hoặc `ResponseEntity`. Nếu mục tiêu **chỉnh/bao kết cấu body JSON** hoặc thêm logic trước converter write, xem `ResponseBodyAdvice` thay vì nghĩ `postHandle` luôn phù hợp.

### 3.5. Lựa chọn bảo mật

Tài liệu chính thức Spring MVC **không khuyến nghị interceptor làm lớp bảo mật chính** vì có thể xảy ra mismatch giữa path matching của interceptor và annotated controller mappings. Hãy dùng **Spring Security** hoặc lớp tương tự hoạt động sớm trong Servlet filter chain. Interceptor hợp lý để bổ sung logging/metrics liên quan handler, localization hoặc kiểm tra điều kiện phụ không được dùng thay authorization chính.

## 4. Spring AOP: cắt ngang lời gọi method của Spring bean

Spring AOP thuộc mô hình **aspect-oriented programming**: áp dụng logic xuyên suốt lên nhiều lời gọi method mà không sao chép cùng đoạn code vào từng service.

Các thuật ngữ:

| Khái niệm | Ví dụ | Nghĩa |
| --- | --- | --- |
| **Aspect** | Class `@Aspect` | Nơi khai báo logic xuyên suốt |
| **Advice** (AOP) | `@Around` / `@Before` | Code chạy tại join point phù hợp |
| **Join point** | Lời gọi method của proxied bean | Điểm có thể can thiệp |
| **Pointcut** | `execution(* com.example.demo..service..*(..))` | Điều kiện chọn method |
| **Proxy** | JDK dynamic proxy hoặc CGLIB | Object trung gian thực thi advice |
| **ProceedingJoinPoint** | `pjp.proceed()` | Cho phép method gốc tiếp tục từ `@Around` |

**Đừng nhầm thuật ngữ “Advice” trong AOP** với `@ControllerAdvice`: một bên là aspect/proxy method interception, bên kia là controller infrastructure của Web MVC.

### 4.1. Một aspect quanh service method

```java
@Aspect
@Component
class ServiceTimingAspect {

    @Around("execution(* com.example.demo.GreetingService.greet(..))")
    Object time(ProceedingJoinPoint joinPoint) throws Throwable {
        long started = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsed = System.nanoTime() - started;
            System.out.println("service nanos=" + elapsed);
        }
    }
}
```

Snippet này cần class `GreetingService` và import AspectJ annotation, Spring component và `ProceedingJoinPoint`. Với Spring Boot 3.x thêm starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

Spring Boot tự cấu hình AOP khi có dependency phù hợp; không luôn cần `@EnableAspectJAutoProxy` thủ công. Aspect chỉ chạy khi call **đi qua proxy** và match pointcut; không phải middleware HTTP. Method chạy từ scheduled task hoặc message consumer cũng có thể được AOP intercept nếu qua proxied bean.

### 4.2. Thế nào là self-invocation?

```java
@Service
class OrderService {

    public void outer() {
        inner(); // lời gọi nội bộ trên 'this', không đi qua proxy
    }

    public void inner() {
        // Pointcut chỉ match inner() thì có thể không chạy ở đây.
    }
}
```

Đây là snippet minh họa. Nếu một method của bean gọi method khác trên **cùng instance** qua `this`, nó không đi qua Spring AOP proxy, nên advice áp lên `inner()` có thể bị bỏ qua. Đây là giới hạn nổi bật của proxy-based Spring AOP. Giải pháp thường phù hợp là tách trách nhiệm sang Spring bean khác rồi inject, thay vì tự gọi qua proxy/hack `AopContext.currentProxy()`.

Tùy proxy mode:
- **JDK dynamic proxy** thường áp dụng theo interface.
- **CGLIB proxy** kế thừa class, bị giới hạn với final class/final method và visibility.
- Spring Boot có defaults riêng có thể chọn class-based proxies; đừng suy luận mặc định chỉ từ quy tắc Java có hay không có interface.

### 4.3. Spring AOP không phải HTTP middleware

AOP thấy **method invocation**, không thấy **tự động mọi URL**. Nó có thể không chạy nếu:
- Bean không thuộc Spring container.
- Method call không đi qua proxy.
- Pointcut không match hoặc method không thể được advised do proxy constraints.
- Đối tượng được tạo trực tiếp bằng `new` thay vì bean được Spring quản lý.

AOP phù hợp cho metrics/service policy, transaction boundaries, auditing lời gọi nghiệp vụ (khi tuân thủ hợp đồng bảo mật) hoặc các behavior xuyên layer. Spring `@Transactional` dựa trên proxy/AOP infrastructure trong cấu hình thường gặp, nên self-invocation cũng là một nguy cơ với transaction method.

**Không dùng AOP để tự triển khai mọi HTTP security check.** Với authentication/authorization trên web, dùng Spring Security filter/method security; đừng chỉ dựa vào một aspect logics tự viết mà bỏ qua đường đi không qua proxy.

## 5. @ControllerAdvice: dùng chung chức năng MVC controller

`@ControllerAdvice` là annotation trên một Spring bean, giúp khai báo những method có vai trò dùng chung cho controller trong phạm vi áp dụng. `@RestControllerAdvice` tương đương `@ControllerAdvice` kèm behavior `@ResponseBody` cho các response method liên quan.

Ba nhóm hook quan trọng nhất:

1. **`@ExceptionHandler`:** chuyển exception khi xử lý MVC thành HTTP response/view lỗi.
2. **`@InitBinder`:** tùy biến `WebDataBinder` cho model binding/formatting/validation.
3. **`@ModelAttribute` methods:** đóng góp các model attributes dùng chung cho controller.

Ngoài ra, một `@ControllerAdvice` bean có thể triển khai `ResponseBodyAdvice<T>` để can thiệp lúc response body chuẩn bị được ghi bởi converter. Đây là extension interface riêng, **không đồng nghĩa mọi class `@ControllerAdvice` tự có một pre/post callback quanh mọi request**.

### 5.1. Exception handler dùng chung

```java
@RestControllerAdvice
class ApiErrorAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badArgument(
            IllegalArgumentException ex) {

        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid request"));
    }
}
```

Đây là snippet class; import `RestControllerAdvice`, `ExceptionHandler`, `ResponseEntity` và `java.util.Map`. Nếu controller/service (trong vòng đời request mà MVC resolver thấy) ném `IllegalArgumentException`, Spring chọn handler này khi không có local handler ưu tiên tương ứng.

**Quy tắc ưu tiên:** local `@ExceptionHandler` trong controller được xem trước global handlers từ `@ControllerAdvice`. Khi nhiều advice beans cùng khớp, thứ tự `@Order` và exception matching root/cause cũng quan trọng. Không nên đặt một generic `@ExceptionHandler(Exception.class)` rồi trả HTTP 400 cho mọi lỗi hệ thống.

### 5.2. InitBinder để kiểm soát form binding

```java
@ControllerAdvice(annotations = Controller.class)
class FormBinderAdvice {

    @InitBinder("profileForm")
    void onlyPublicFields(WebDataBinder binder) {
        binder.setAllowedFields("displayName", "bio");
    }
}
```

Snippet minh họa: `@InitBinder` áp dụng cho model attribute `profileForm` ở các controller thuộc phạm vi advice. Nó không tự tạo một HTTP filter; cũng không áp dụng như JSON allowlist cho Jackson `@RequestBody`. Hãy bảo vệ data binding bằng DTO riêng và kiểm tra quyền cập nhật ở service.

### 5.3. Shared model attributes

```java
@ControllerAdvice
class PageModelAdvice {

    @ModelAttribute("siteName")
    String siteName() {
        return "Demo MVC";
    }
}
```

Method này giúp các controller tạo view có attribute `siteName` nếu thuộc phạm vi advice. Không dùng nó để load các dữ liệu đắt tiền cho mọi request mà không cần; nếu chỉ REST APIs thì việc bổ sung model có thể không hữu ích.

### 5.4. ResponseBodyAdvice cho JSON

Nếu muốn chỉnh **response body** trước converter write:

```java
@RestControllerAdvice
class VersionAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return true; // Demo: áp dụng rộng; production phải chọn lọc.
    }

    @Override
    public Object beforeBodyWrite(Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> converterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        response.getHeaders().add("X-API-Version", "1");
        return body;
    }
}
```

Đây là **snippet riêng, không thêm vào code ví dụ chạy ở mục 8**. Cần import `ResponseBodyAdvice`, `MethodParameter`, `HttpMessageConverter`, `MediaType`, `ServerHttpRequest`, `ServerHttpResponse` và `RestControllerAdvice`. Chức năng chạy gần thời điểm message converter ghi response, khác với `HandlerInterceptor.postHandle`. Với `supports()=true`, cần đặc biệt kiểm tra text, binary, streaming và lỗi để tránh can thiệp vào response không thích hợp. Nếu chỉ thêm header tĩnh, Filter hoặc `ResponseEntity` có thể đơn giản hơn.

### 5.5. Điều ControllerAdvice không làm

- Không bao trùm mọi Servlet request như Filter.
- Không tự được gọi một lần trước và sau **mọi controller handler**; callback phụ thuộc chức năng (error/binder/model/body).
- Không tự bắt exception xuất phát hoàn toàn từ một Servlet filter **trước khi** `DispatcherServlet` được gọi, vì lỗi có thể chưa đi vào MVC exception resolver.
- Không phải một AOP aspect: `@ControllerAdvice` **không** xử lý arbitrary method calls trong service/batch.
- Không mặc định vượt qua local `@ExceptionHandler` khi cả hai cùng match.

### 5.6. Giới hạn phạm vi của global advice

```java
@RestControllerAdvice(basePackages = "com.example.demo.api")
class ApiOnlyErrors {
    // ...
}
```

Annotation cũng hỗ trợ `basePackageClasses`, `assignableTypes` hoặc `annotations` để áp dụng cho nhóm controller. Không phải cứ tên “global” là không thể giới hạn. Dùng selector có mục đích, tránh xử lý các lỗi HTML view và lỗi REST bằng một body contract không phù hợp.

## 6. So sánh thứ tự callback trong những tình huống thật

### 6.1. Request JSON thành công đồng bộ

Ví dụ client gọi `GET /api/greet?name=Lan`:

```text
TraceFilter trước chain.doFilter
  -> TraceInterceptor.preHandle
     -> Controller method bắt đầu
        -> ServiceTimingAspect.before/proceed
           -> GreetingService.greet
        <- ServiceTimingAspect.after
     <- Controller trả DTO
     -> HttpMessageConverter ghi JSON
  <- TraceInterceptor.postHandle (JSON có thể đã ghi)
  <- TraceInterceptor.afterCompletion
TraceFilter finally / sau chain.doFilter
```

Không phải mọi callback đều có cấu trúc before/after giống nhau, và async handling có thể làm chuỗi khác đi. Một `@RestControllerAdvice` chỉ khai báo `@ExceptionHandler` sẽ **không chạy** trong success path trên.

### 6.2. Service ném exception, được ControllerAdvice xử lý

```text
Filter before
  -> Interceptor.preHandle
     -> Controller
        -> AOP around service
           -> service throws IllegalArgumentException
        <- AOP finally
     -> MVC catches exception
     -> @RestControllerAdvice @ExceptionHandler
     -> HTTP 400 + JSON error
  -> Interceptor.afterCompletion
Filter finally
```

`postHandle` thông thường **không chạy** trên đường handler ném exception; exception resolver có thể tạo response riêng. `afterCompletion` có thể được gọi sau khi MVC xử lý xong, nhưng tham số `ex` có thể `null` nếu exception đã được resolver xử lý. Đừng dùng `afterCompletion(ex != null)` làm nguồn duy nhất để đếm mọi exception HTTP.

### 6.3. Filter chặn request trước MVC

```text
Filter detects invalid early request
  -> response.setStatus(400)
  -> write safe body
  -> return without chain.doFilter
     X DispatcherServlet
     X HandlerInterceptor
     X @Controller
     X @ControllerAdvice @ExceptionHandler
```

Vì request không vào MVC, local/global MVC exception handlers không có cơ hội xử lý như bình thường. Nếu muốn error contract thống nhất từ Filter/Spring Security, sử dụng handler/writer phù hợp với filter stack hoặc chủ động chuyển giao qua một interface được thiết kế; đừng giả định `@RestControllerAdvice` tự bắt lỗi trước MVC.

### 6.4. Request đến URL không có MVC handler

Một Filter đã đăng ký thích hợp **có thể** chạy và thấy request. Interceptor chỉ chạy nếu MVC tìm được một handler mapping phù hợp (lưu ý framework có thể có `ResourceHttpRequestHandler` cho static resources). Spring Boot error page hoặc fallback handler có thể xử lý request theo cấu hình; đừng nghĩ mọi 404 đều làm `HandlerInterceptor.preHandle` chạy cho một controller method.

## 7. Ví dụ đầy đủ chạy được: quan sát cả bốn cơ chế

Đây là ứng dụng **Spring Boot 3.x, Java 17+**, dùng **Spring Web** và **Spring AOP**. Không thêm Spring Security, database, Thymeleaf hay WebFlux. Đặt mã bên dưới vào duy nhất file `src/main/java/com/example/demo/DemoApplication.java`; một public class, các class khác package-private hợp lệ.

### 7.1. Dependency

Tạo Maven project tại [Spring Initializr](https://start.spring.io/) với Java 17+ và hai dependencies **Spring Web** và **Spring AOP**, tương đương:

```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
  </dependency>
</dependencies>
```

Đây là fragment trong `pom.xml`; Initializr cung cấp Spring Boot parent/BOM, version, plugins và phần project metadata còn lại. AOP starter cung cấp các thư viện cần cho `@Aspect` và Boot tự cấu hình proxy trong điều kiện bình thường.

### 7.2. Toàn bộ Java source

```java
package com.example.demo;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

// TẦNG 1: Servlet Filter. Boot đăng ký Filter bean.
@Component
class TraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        response.setHeader("X-Filter", "seen");
        System.out.println("FILTER before");
        try {
            chain.doFilter(request, response);
        } finally {
            System.out.println("FILTER after");
        }
    }
}

// TẦNG 2: MVC HandlerInterceptor.
@Component
class TraceInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        response.setHeader("X-Interceptor", "seen");
        System.out.println("INTERCEPTOR preHandle");
        return true;
    }

    @Override
    public void postHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            org.springframework.web.servlet.ModelAndView modelAndView) {

        System.out.println("INTERCEPTOR postHandle");
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        System.out.println("INTERCEPTOR afterCompletion");
    }
}

@Configuration
class MvcConfig implements WebMvcConfigurer {

    private final TraceInterceptor interceptor;

    MvcConfig(TraceInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/**");
    }
}

// TẦNG 3: Spring-managed service qua proxy AOP.
@Service
class GreetingService {

    public String greet(String name) {
        System.out.println("SERVICE greet");

        if ("bad".equalsIgnoreCase(name)) {
            throw new IllegalArgumentException("invalid name");
        }
        return "Hello, " + name;
    }
}

@Aspect
@Component
class ServiceTraceAspect {

    @Around("execution(* com.example.demo.GreetingService.greet(..))")
    public Object around(ProceedingJoinPoint joinPoint)
            throws Throwable {

        System.out.println("AOP before");
        try {
            return joinPoint.proceed();
        } finally {
            System.out.println("AOP after");
        }
    }
}

// Controller chỉ chứa HTTP mapping và gọi service.
@RestController
@RequestMapping("/api")
class GreetingController {

    private final GreetingService service;

    GreetingController(GreetingService service) {
        this.service = service;
    }

    @GetMapping("/greet")
    public Map<String, String> greet(
            @RequestParam(defaultValue = "Lan") String name) {

        return Map.of("message", service.greet(name));
    }
}

// TẦNG 4: MVC error policy chung.
@RestControllerAdvice
class ApiErrors {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidName(
            IllegalArgumentException ex) {

        System.out.println("ADVICE exceptionHandler");
        return ResponseEntity.badRequest()
                .body(Map.of("error", "name invalid"));
    }
}
```

**Những điểm cần đọc đúng:**
- `TraceFilter` dùng `OncePerRequestFilter`, chạy ở Servlet filter chain và đặt `X-Filter` trước khi request vào MVC.
- `TraceInterceptor` đăng ký cho `/api/**`, đặt `X-Interceptor` trong `preHandle` để chắc header có thể được ghi trước body.
- `GreetingService` là Spring `@Service`. Controller inject bean do Spring cung cấp; cuộc gọi tới `greet(...)` đi qua AOP proxy, do đó `ServiceTraceAspect` hoạt động.
- `ServiceTraceAspect` log `AOP before` và `AOP after` kể cả khi `greet(...)` ném exception, vì đoạn sau đặt trong `finally`.
- `ApiErrors` là `@RestControllerAdvice`, chỉ chọn `@ExceptionHandler(IllegalArgumentException.class)` khi exception phù hợp đi tới MVC exception resolver.
- Bài demo dùng `System.out.println` chỉ để quan sát callback; production nên dùng structured logging/tracing/metrics thích hợp. Các request đồng thời có thể khiến dòng log xen kẽ.
- Không có `@EnableAspectJAutoProxy` vì Spring Boot 3.x với starter AOP mặc định đã kích hoạt hạ tầng proxy phù hợp.

### 7.3. Chạy

Từ thư mục Maven project:

```bash
# Linux hoặc macOS
./mvnw spring-boot:run

# Windows PowerShell / Command Prompt
mvnw.cmd spring-boot:run
```

Server mặc định chạy tại `http://localhost:8080` nếu chưa đổi `server.port` hoặc context path. Chạy request trong môi trường không có Security/filter ngoài ví dụ để hiểu chuỗi callback.

### 7.4. Request thành công: Filter + Interceptor + AOP, không có error advice

```bash
curl -i 'http://localhost:8080/api/greet?name=Lan'
```

Response chứa:
- HTTP status **200 OK**.
- Header `X-Filter: seen`.
- Header `X-Interceptor: seen`.
- Body JSON (giá trị đúng, thứ tự header không cố định):

```json
{"message":"Hello, Lan"}
```

Với một request đồng bộ duy nhất, log trong console theo chuỗi tiêu biểu:

```text
FILTER before
INTERCEPTOR preHandle
AOP before
SERVICE greet
AOP after
INTERCEPTOR postHandle
INTERCEPTOR afterCompletion
FILTER after
```

Không có `ADVICE exceptionHandler` vì không có exception. Nếu phía ngoài còn filter/container logging, output có thể có thêm dòng không nằm trong snippet.

### 7.5. Request lỗi: ControllerAdvice chuyển exception thành 400

```bash
curl -i 'http://localhost:8080/api/greet?name=bad'
```

Response chứa HTTP status **400 Bad Request**, hai header `X-Filter: seen` và `X-Interceptor: seen`, body:

```json
{"error":"name invalid"}
```

Log chính tiêu biểu:

```text
FILTER before
INTERCEPTOR preHandle
AOP before
SERVICE greet
AOP after
ADVICE exceptionHandler
INTERCEPTOR afterCompletion
FILTER after
```

`INTERCEPTOR postHandle` thường **không xuất hiện** do controller ném exception trước khi hoàn tất handler thành công, sau đó MVC error resolver mới tạo response. `afterCompletion` vẫn là nơi cleanup phù hợp cho interceptor đã thành công ở `preHandle`.

### 7.6. Request ngoài /api/**

```bash
curl -i http://localhost:8080/unknown
```

Filter được đăng ký trên phạm vi URL mặc định của application nên có thể chạy. Interceptor chỉ được map `/api/**`, vì vậy không thể dùng `/unknown` để chứng minh Interceptor đã chạy. Kết quả 404 cụ thể phụ thuộc Spring MVC static resources/error handling hiện hành; `@RestControllerAdvice` ví dụ chỉ xử lý `IllegalArgumentException` và không có nghĩa nó sẽ tạo body cho mọi 404.

## 8. Kiểm thử bằng MockMvc

Để tự động xác minh rằng Filter và Interceptor chèn header, ControllerAdvice chuyển lỗi thành 400, còn service/AOP vẫn được dùng trong quá trình xử lý, thêm dependency test `spring-boot-starter-test` và file `src/test/java/com/example/demo/PipelineTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PipelineTest {

    @Autowired
    MockMvc mvc;

    @Test
    void successfulRequestTraversesFilterAndInterceptor()
            throws Exception {

        mvc.perform(get("/api/greet")
                        .param("name", "Lan"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Filter", "seen"))
                .andExpect(header().string("X-Interceptor", "seen"))
                .andExpect(jsonPath("$.message")
                        .value("Hello, Lan"));
    }

    @Test
    void controllerAdviceMapsServiceErrorTo400()
            throws Exception {

        mvc.perform(get("/api/greet")
                        .param("name", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Filter", "seen"))
                .andExpect(header().string("X-Interceptor", "seen"))
                .andExpect(jsonPath("$.error")
                        .value("name invalid"));
    }
}
```

Chạy:

```bash
# macOS/Linux
./mvnw test

# Windows
mvnw.cmd test
```

**Giới hạn của test trên:** các assertions này xác minh các headers và responses nhưng **chưa trực tiếp chứng minh AOP advice đã chạy**. Để xác minh AOP độc lập, có thể inject bean service vào test, dùng `AopUtils.isAopProxy(service)` và một mock/spy hoặc metric counter có kiểm soát; đừng kết luận từ response body rằng aspect đã được invoke. Cũng có thể kiểm tra logs trong môi trường thử nghiệm nếu cần, nhưng assertions với metric/test double ít mong manh hơn.

`@SpringBootTest` tạo application context; `@AutoConfigureMockMvc` thiết lập MockMvc và filters khi cấu hình phù hợp. Nếu thêm Spring Security, cần test auth/CSRF theo policy; ví dụ này không bật Security.

## 9. Tình huống nào nên dùng cái nào?

| Mục tiêu | Cơ chế ưu tiên | Vì sao |
| --- | --- | --- |
| Đặt trace ID trước mọi HTTP endpoint | **Servlet Filter** | Có mặt trước DispatcherServlet; dễ dùng request/response wrapper |
| Chuẩn hóa header bảo mật áp toàn ứng dụng | **Security Filter/Servlet Filter** | Chính sách sớm trước khi body/handler chạy |
| Authentication/authorization HTTP | **Spring Security filter chain** (và method security khi cần) | Chính sách bảo mật nhất quán và sớm |
| Biết controller method và annotation nào được match | **HandlerInterceptor** | Có `handler instanceof HandlerMethod` |
| Đo thời gian xử lý MVC handler | **HandlerInterceptor** | Có preHandle/afterCompletion; không đo toàn bộ filter chain |
| Đo thời gian service method kể cả lời gọi ngoài HTTP | **Spring AOP** | Quan sát service bean invocation |
| Transaction trên service method | **Spring transaction abstraction / proxy AOP** | Đây là vấn đề nghiệp vụ, không phải HTTP request |
| Chuyển exception domain thành JSON lỗi thống nhất | **@RestControllerAdvice + @ExceptionHandler** | Đúng tầng HTTP error representation |
| Thêm chung model attribute cho các HTML controllers | **@ControllerAdvice @ModelAttribute** | Tập trung model setup |
| Ràng buộc binding/formatting cho form | **@ControllerAdvice @InitBinder** | Tác động MVC binding |
| Can thiệp vào response body trước message converter | **ResponseBodyAdvice** | Đúng thời điểm serialize |
| Chặn request không phù hợp theo handler-specific metadata | **HandlerInterceptor** cho metadata bổ sung, nhưng **không thay security** | Chạy sau HandlerMapping |
| Xử lý lỗi do Filter xảy ra ngoài MVC | **Filter/security-specific error strategy** | MVC advice không mặc định thấy exception đó |

**Không bắt buộc chọn duy nhất một:** một app production thường đồng thời có Spring Security filters, custom trace filter, MVC interceptors cho correlation metadata, AOP cho service transactions và `@RestControllerAdvice` cho errors. Vấn đề là trách nhiệm phải tách bạch, tránh ghi log/ghi body/validate cùng một thứ ở nhiều lớp.

## 10. Bảo mật: tại sao interceptor không thay Spring Security?

Spring Framework cảnh báo `HandlerInterceptor` có thể không thích hợp làm security layer do sự khác biệt giữa pattern áp cho interceptor và mapping annotation/controller. Ví dụ interceptor match `/admin/**` nhưng những MVC mappings, forwards, dispatcher types và controller method selection có thể phức tạp hơn. Authorization là tính chất cần áp **chính xác và nhất quán** cho mọi đường vào, không thể dựa vào giả định đường dẫn string match đơn giản.

Cách làm đúng:
- Dùng Spring Security `SecurityFilterChain`, `authorizeHttpRequests(...)`, authentication provider, CSRF/session policy khi cần.
- Áp **method security** cho business methods nhạy cảm khi nghiệp vụ yêu cầu (vd. `@PreAuthorize` với cấu hình phù hợp).
- Kiểm tra ownership/tenant ở service bằng identity đã xác thực.
- Chỉ dùng HandlerInterceptor cho bổ trợ không phải hàng rào bảo mật duy nhất.

**Lưu ý:** Servlet Filter tự viết cũng không tự động an toàn như Spring Security. Spring Security cung cấp cả một mô hình authentication, authorization, error handling, exception translation và lifecycle. Nếu chỉ kiểm tra `request.getHeader("X-Role")` do client tự gửi, việc đặt logic trong Filter vẫn **không biến nó thành xác thực tin cậy**.

## 11. Async và ERROR dispatch: các nhánh nâng cao không được bỏ qua

### 11.1. Servlet Filter và dispatcher types

Filter registration có thể được cấu hình cho dispatcher types `REQUEST`, `ASYNC`, `ERROR`, `FORWARD` và `INCLUDE`. Filter không nhất thiết chạy trong mọi dispatcher; behavior phụ thuộc container/Boot registration.

`OncePerRequestFilter` cung cấp các hook:
- `shouldNotFilter(HttpServletRequest)` để bỏ qua request không áp dụng.
- `shouldNotFilterAsyncDispatch()` để chọn tham gia hoặc tránh dispatch async.
- `shouldNotFilterErrorDispatch()` để chọn logic quanh dispatch lỗi.

Nếu dùng ThreadLocal hoặc MDC:
- Đặt context trước `chain.doFilter`.
- Dọn trong `finally` tại dispatch/thread hiện tại.
- Với async, không giả định ThreadLocal tự chuyển sang thread xử lý khác; phải tích hợp propagation rõ ràng.
- Không log token/cookie/password hay toàn request body thô nếu chưa có chính sách bảo vệ dữ liệu.

### 11.2. HandlerInterceptor và async MVC

Nếu handler khởi động async (ví dụ `Callable`, `DeferredResult`), ban đầu Spring MVC có thể **không** gọi `postHandle` / `afterCompletion` ngay khi handler chuyển sang async. Thay vào đó `AsyncHandlerInterceptor.afterConcurrentHandlingStarted(...)` cung cấp callback khi request chuyển sang concurrent handling; sau async dispatch để hoàn tất, interceptor chain có thể chạy lại theo vòng dispatch.

Vì vậy:
- Không tính tổng thời gian async chỉ từ một lần `preHandle`/`postHandle` mà không hiểu lifecycle.
- Cleanup ThreadLocal đúng theo các callback và thread, dùng async-aware tracing khi cần.
- Đừng xác nhận “Filter chạy chính xác một lần toàn request, Interceptor chạy chính xác một lần” với mọi async scenario.

### 11.3. Exception ở filter trước DispatcherServlet

Nếu filter ném exception trước `chain.doFilter`, `@RestControllerAdvice` thường không bắt được vì MVC chưa nắm quyền. Nếu exception phát sinh **bên trong** controller/service và vào MVC exception resolver, advice có thể xử lý. Tránh đặt error body ở Filter sau khi response đã committed; cần chính sách exception handling phù hợp tầng.

### 11.4. Response đã committed

Mọi cơ chế ở các thời điểm khác nhau cần cân nhắc `HttpServletResponse.isCommitted()`. Cụ thể:
- Filter ở đầu chuỗi thường có thể đặt headers trước.
- Interceptor `preHandle` thường còn kịp đặt headers.
- Interceptor `postHandle` có thể quá muộn với `@ResponseBody` JSON.
- AOP ở service không nên tự ghi Servlet response trừ khi thiết kế thực sự yêu cầu; service nên trả kết quả hoặc ném domain exception.
- `ResponseBodyAdvice` chạy trong đường xử lý body của Spring MVC và phù hợp hơn với việc can thiệp converter output.

## 12. Hiệu năng và đo thời gian ở đúng tầng

Một app có ba loại latency khác nhau:

1. **HTTP end-to-end trên server**: đo ở Filter, thường bao gồm các servlet filters/DispatcherServlet nằm **phía sau nó**, cộng thời gian chờ/sync trong phạm vi dispatch. Với async muốn end-to-end đầy đủ cần cơ chế async-aware, không chỉ đo một lượt `doFilter`.
2. **MVC handler processing**: đo Interceptor, gồm handler/controller logic sau preHandle và các bước MVC render/cleanup theo callback.
3. **Service method latency**: đo AOP, chỉ method call match pointcut thông qua proxy.

Nếu `GET /api/orders` mất 250 ms nhưng service chỉ mất 30 ms, chênh lệch có thể nằm ở security, parameter conversion, network I/O, JSON serialization, view rendering hoặc scheduling. Không dùng metric của AOP service để kết luận toàn bộ HTTP response nhanh/chậm.

Với app lớn, cân nhắc Micrometer Observation/tracing tích hợp Spring Boot thay vì tự viết vô số Filter/Aspect thủ công. Tránh tạo nhãn metric chứa ID user, raw URL với giá trị động, exception message hay token vì cardinality và dữ liệu nhạy cảm.

## 13. Lỗi hay gặp và phương án xử lý

| Vấn đề | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Filter không chạy ở endpoint mong muốn | URL pattern hoặc dispatcher type registration sai | Kiểm tra `FilterRegistrationBean`/container mapping |
| Filter chạy hai lần | Trùng đăng ký bean + registration, dispatch khác hoặc nesting | Kiểm tra registrations và `OncePerRequestFilter` logic |
| Interceptor không chạy | Chưa đăng ký qua MVC config, path pattern không khớp hoặc không có HandlerMapping phù hợp | Kiểm tra `addInterceptors`/handler mapping |
| `preHandle=false` nhưng client nhận response rỗng | Chặn chain mà không set status/body | Trả HTTP response thích hợp trước khi return false |
| `postHandle` không chạy khi lỗi | Handler ném exception, đi sang resolver | Cleanup trong `afterCompletion`, xử lý body lỗi ở advice |
| Header đặt trong `postHandle` không thấy | REST converter đã ghi/commit body | Đặt ở Filter/preHandle/ResponseEntity/ResponseBodyAdvice |
| `afterCompletion(ex)` null dù client nhận 4xx | Exception đã được resolver xử lý | Dựa vào status/metric/error handler phù hợp |
| AOP `@Around` không chạy | Chưa có AOP starter, pointcut sai, call không qua proxy | Kiểm tra bean/proxy/pointcut |
| `@Transactional` trên internal method không chạy | Self-invocation `this.inner()` bỏ qua proxy | Tách service hoặc thay thiết kế proxy boundary |
| Advice không bắt lỗi filter | Lỗi xảy ra ngoài MVC | Xử lý tại filter/security layer |
| App startup báo ambiguous exception handlers | Hai `@ExceptionHandler` trùng mapping | Loại/đổi mapping có chủ đích |
| Exception bị đổi thành 200 | Error handler trả body nhưng không đặt status | Dùng `ResponseEntity.status(...)` hoặc `ProblemDetail` |
| Log cùng request thiếu correlation | Trace context không được giữ khi async/thread switch | Dùng tracing/MDC propagation và cleanup đúng |
| Dùng Interceptor để security nhưng route bypass | Handler mapping/path pattern mismatch | Dùng Spring Security FilterChain |
| ControllerAdvice ảnh hưởng endpoint không mong đợi | Không giới hạn scope hoặc generic catch-all | Dùng selectors và error type hẹp |
| Filter che response body của controller | Tự ghi/flush trước hoặc sau `chain.doFilter` không đúng | Kiểm soát response wrappers và commit state |

## 14. Checklist code review

Khi chuẩn bị thêm cross-cutting logic, đặt các câu hỏi:

1. Cần biết HTTP request/response từ **trước MVC** hay chỉ cần handler method đã match?
2. Nếu request không vào MVC (404, static, security reject), logic vẫn phải chạy không?
3. Cần logic chạy khi service được gọi từ scheduled task/message consumer, hay chỉ khi có HTTP?
4. Có tự quản lý exception/status/body hoặc cần chính sách lỗi thống nhất cho REST controllers?
5. Với interceptor, `preHandle`, `postHandle`, `afterCompletion` có hoạt động đúng khi handler lỗi, trả `false` và chạy async?
6. Với AOP, bean/method có đi qua proxy không? Có self-invocation, final/private methods hoặc pointcut quá rộng?
7. Có chạm vào authentication/authorization không? Nếu có, đã dùng Spring Security và kiểm tra quyền ở service chưa?
8. Có ai khác cũng đang thêm header, log trace ID, xử lý cùng exception hoặc thay body không? Có bị **double logging/double write**?
9. Các tests đã kiểm tra cả request thành công, request lỗi, URL không match và, nếu dùng async, callback lifecycle chưa?
10. Các header có được đặt **trước commit**? Các object trong request/ThreadLocal được cleanup đầy đủ chưa?

## 15. Tự kiểm tra và đáp án

**Câu 1.** Muốn đặt correlation ID trước khi request đi vào DispatcherServlet, kể cả khi request bị security filter chặn, cơ chế nào phù hợp?

**Đáp án:** Servlet Filter được đăng ký đúng thứ tự URL/dispatcher. Security-specific policy nên dùng Spring Security infrastructure.

**Câu 2.** Muốn biết `@GetMapping` method được Spring MVC chọn tên gì, dùng gì?

**Đáp án:** HandlerInterceptor, trong `preHandle` kiểm tra `handler instanceof HandlerMethod`.

**Câu 3.** Muốn log mọi lần Spring service method được gọi, kể cả từ scheduler, dùng gì?

**Đáp án:** Spring AOP quanh method phù hợp của managed bean, lưu ý lời gọi phải đi qua proxy và pointcut chính xác.

**Câu 4.** Muốn mọi REST controller trả cấu trúc JSON lỗi thống nhất cho `ProductNotFoundException`, dùng gì?

**Đáp án:** `@RestControllerAdvice` với `@ExceptionHandler(ProductNotFoundException.class)` và status HTTP phù hợp.

**Câu 5.** `HandlerInterceptor.postHandle` có chắc thêm được header khi handler trả `ResponseEntity<Dto>` không?

**Đáp án:** Không. Message converter có thể đã ghi/commit body trước khi `postHandle`. Dùng `preHandle`, `ResponseEntity`, Filter hoặc `ResponseBodyAdvice` theo mục tiêu.

**Câu 6.** Vì sao `@Around` không chạy khi service gọi `this.innerMethod()`?

**Đáp án:** Spring AOP proxy chỉ can thiệp lời gọi đi qua proxy; self-invocation trên `this` đi thẳng vào method của instance.

**Câu 7.** ControllerAdvice bắt được mọi exception từ Servlet Filter trước DispatcherServlet không?

**Đáp án:** Không mặc định. Exception nằm ngoài MVC cần error strategy ở filter/security/servlet layer phù hợp.

**Câu 8.** Dùng HandlerInterceptor để phân quyền thay Spring Security có an toàn không?

**Đáp án:** Không nên. Spring Framework cảnh báo có thể lệch cách khớp path/controller annotations; authentication/authorization nên nằm trong security infrastructure và business authorization ở service.

**Câu 9.** Filter và Interceptor đều có thể đo request time. Kết quả có giống nhau không?

**Đáp án:** Không bắt buộc. Chúng đo phạm vi khác nhau, còn async, serialization, view rendering, filter chain và thread scheduling làm thay đổi số đo.

## 16. Tài liệu chuẩn

- [Spring Framework 6.2 — Handler Interception](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/handlermapping-interceptor.html): `preHandle`, `postHandle`, `afterCompletion`, response committed và khuyến nghị bảo mật.
- [Spring Framework 6.2 — Configure Interceptors](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/interceptors.html): `WebMvcConfigurer#addInterceptors`, mapped patterns và `MappedInterceptor`.
- [Spring Framework 6.2 — Controller Advice](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-advice.html): `@ExceptionHandler`, `@InitBinder`, `@ModelAttribute`, phạm vi.
- [Spring Framework 6.2 — Exception Handler](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-exceptionhandler.html): error handling và exception matching.
- [Spring Framework 6.2 — @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html): `@Aspect`, advice, pointcut và auto-proxy.
- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB proxies, self-invocation và method constraints.
- [Spring Framework — OncePerRequestFilter Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/filter/OncePerRequestFilter.html): dispatch handling và filtering hooks.
- [Spring Boot — Servlet Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html): servlet filters, MVC configuration và embedded container integration.
- [Spring Security — Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html): `FilterChainProxy`, `SecurityFilterChain` và web security.

**Kết luận:** bốn công cụ không cạnh tranh nhau về một “thứ tự trước/sau” duy nhất. **Servlet Filter** ở ranh giới HTTP, **HandlerInterceptor** quanh MVC handler chain, **Spring AOP** quanh lời gọi method của bean qua proxy, còn **ControllerAdvice** gom logic MVC dùng chung như exception responses và binding/model customizations. Đặt cross-cutting logic ở tầng có đủ thông tin và đúng phạm vi, kiểm thử success/error/async và không thay Spring Security bằng một interceptor tùy biến.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="filters-interceptors-002">Filter chain, thứ tự đăng ký và các loại dispatch được áp dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Filter chain, thứ tự đăng ký và các loại dispatch trong Spring MVC

## 1. Vì sao một filter có thể chạy sai thứ tự hoặc chạy nhiều lần?

Trong ứng dụng Spring MVC chạy trên **Servlet container**, HTTP request đi qua **chuỗi Servlet Filter** trước khi tới `DispatcherServlet`. Filter có thể làm việc trước và sau thành phần kế tiếp, chẳng hạn chuẩn hóa encoding, đọc forwarded headers đã được tin cậy, xác thực, ghi correlation ID hoặc quan sát thời gian xử lý.

Những sự cố phổ biến:

- Filter giải mã body chạy trước filter thiết lập character encoding khiến dữ liệu Unicode bị đọc sai.
- Filter CORS hoặc proxy headers đặt sau Spring Security khiến preflight request bị từ chối hoặc URL được tạo sai.
- Một filter vô tình đăng ký **hai lần**, một lần với Servlet container và một lần trong `SecurityFilterChain`.
- Filter hoạt động với request ban đầu nhưng không chạy khi `forward`, khi Servlet container xử lý lỗi hoặc khi async dispatch hoàn tất.
- Developer nghĩ `OncePerRequestFilter` có nghĩa “chỉ một lần từ lúc request bắt đầu đến mọi dispatch về sau”, trong khi nó bảo đảm theo **mỗi request dispatch trong một request thread**, tùy cấu hình async/error và quy tắc already-filtered.
- Dùng `@Order` cho interceptor rồi mong nó thay đổi thứ tự Servlet Filter.

Bài này giải thích **hai chiều cấu hình độc lập**:

1. **Filter được đăng ký ở đâu, áp dụng trên URL/Servlet nào và ở vị trí nào?**
2. **Filter có tham gia loại Servlet dispatch hiện tại không?**

Sau đó là code Spring Boot 3.x/Java 17+ thực thi được và kiểm thử bằng HTTP qua embedded Servlet container để nhìn thấy thứ tự thật, bao gồm request thông thường và `FORWARD`.

## 2. Servlet Filter chain: mô hình trước/sau

Mỗi Servlet Filter thực hiện `doFilter(request, response, chain)`. Gọi `chain.doFilter(...)` là chuyển tiếp tới filter tiếp theo hoặc đích servlet. Khi lời gọi đó kết thúc, phần code sau nó trong filter hiện tại được thực thi theo chiều **ngược lại**.

```text
Browser
  |
  v
[Filter A] trước chain.doFilter         A-before
  |
  v
[Filter B] trước chain.doFilter         B-before
  |
  v
[Filter C] trước chain.doFilter         C-before
  |
  v
DispatcherServlet -> Spring MVC controller
  |
  v
[Filter C] sau chain.doFilter           C-after
  ^
  |
[Filter B] sau chain.doFilter           B-after
  ^
  |
[Filter A] sau chain.doFilter           A-after
  |
  v
HTTP response
```

Dạng source:

```java
@Override
public void doFilter(
        ServletRequest request,
        ServletResponse response,
        FilterChain chain)
        throws IOException, ServletException {

    // Chuẩn bị request, context hoặc wrapper.
    before(request);

    try {
        chain.doFilter(request, response);
    } finally {
        // Dọn tài nguyên, đo duration, clear ThreadLocal...
        cleanup();
    }
}
```

Đây là **fragment của class implements `jakarta.servlet.Filter`**, chưa kèm định nghĩa `before` và `cleanup`; không phải một file Java tự chạy. Ý nghĩa quan trọng của `finally`: cleanup vẫn được thực hiện nếu downstream ném exception. Không phải lúc nào sau `chain.doFilter` cũng còn khả năng **đổi status/headers/body**, vì response có thể đã được commit.

Nếu filter **không gọi `chain.doFilter`**, request dừng tại filter. Điều này hợp lệ khi filter chủ động gửi 401/403/429 hoặc xử lý response hoàn chỉnh, nhưng sai nếu vô tình bỏ qua chain và khiến controller không bao giờ được gọi.

### 2.1. Phân biệt Servlet Filter và MVC interceptor

| Tiêu chí | Servlet `Filter` | Spring MVC `HandlerInterceptor` |
| --- | --- | --- |
| Cấp thực thi | Servlet container/filter chain, trước servlet được nhắm tới | Bên trong `DispatcherServlet` sau khi chọn handler |
| Phạm vi | Có thể áp dụng cho servlet khác, resource, dispatch types theo mapping | Áp dụng cho handler MVC phù hợp |
| Lựa chọn đối tượng | URL patterns, servlet registrations, `DispatcherType` | `InterceptorRegistry`, handler và include/exclude path |
| Thứ tự | Filter registration/order trong container | Thứ tự interceptor được thêm vào chain MVC |
| Bao request/response | Có thể wrap ServletRequest/ServletResponse | Không là Servlet Filter; dùng `preHandle`/`postHandle`/`afterCompletion` |
| Dữ liệu đầu vào | Xử lý ở biên Servlet, phù hợp encoding/security/logging | Phù hợp metadata gắn với handler, đo request MVC |
| Thay thế Security filter? | Spring Security có chain riêng dựa trên Servlet filter | Không đủ để bảo vệ mọi servlet path |

`HandlerInterceptor` không có cơ chế `DispatcherType` mapping tương đương một Servlet Filter đăng ký. Vì vậy đặt logic bảo mật bắt buộc chỉ trong interceptor rồi cho rằng nó bảo vệ toàn bộ application thường không đúng.

## 3. Hai ý nghĩa của từ “chain” khi dùng Spring Security

Trong Spring MVC có thể gặp:

**A. Servlet container filter chain** — Servlet specification sắp xếp các registrations phù hợp với URL, servlet name và dispatcher type.

```text
Request
  |
  v
EncodingFilter
  |
  v
springSecurityFilterChain (DelegatingFilterProxy)
  |
  v
TraceFilter
  |
  v
DispatcherServlet
```

**B. Spring Security `FilterChainProxy` và `SecurityFilterChain`** — nằm **bên trong** filter có tên thông dụng `springSecurityFilterChain`. `FilterChainProxy` chọn một `SecurityFilterChain` khớp request; các security filters trong chain đó có thứ tự riêng.

```text
DelegatingFilterProxy (Servlet filter)
  |
  v
FilterChainProxy
  |
  +-- matching SecurityFilterChain
        |
        +-- SecurityContext...
        +-- Authentication...
        +-- Authorization...
  |
  v
trả về Servlet chain bên ngoài
```

Do đó:

- `FilterRegistrationBean#setOrder(...)` điều chỉnh **vị trí đăng ký Servlet filter**.
- `HttpSecurity#addFilterBefore` / `addFilterAfter` / `addFilterAt` điều chỉnh **vị trí filter bên trong Spring Security filter chain**.
- Hai loại order **không thay thế nhau**. Một filter custom cần tham gia authentication/authorization phải được cấu hình đúng nơi; không đơn giản đặt order số nhỏ để giả làm security filter.
- Nếu filter custom đồng thời là một bean `Filter` mà Boot auto-register với container **và** được thêm vào `HttpSecurity`, nó có thể chạy hai lần. Để tránh double registration, cân nhắc disable Servlet registration cho bean chỉ dành cho Security (xem phần 11).

Bài này không tái triển khai Spring Security; chỉ làm rõ nơi đặt filter và cách xác minh hành vi.

## 4. Các `DispatcherType` của Servlet

`jakarta.servlet.DispatcherType` là enum cho **loại dispatch hiện tại**, không phải HTTP method. Một HTTP request có thể liên quan **hơn một dispatch**.

| `DispatcherType` | Khi nào xuất hiện | Lưu ý quan trọng |
| --- | --- | --- |
| **`REQUEST`** | Request ban đầu được container chuyển tới servlet/đích | Loại phổ biến nhất |
| **`FORWARD`** | Server gọi `RequestDispatcher.forward(...)` hoặc Spring view `forward:...` | Cùng request phía server; URL/servlet target có thể khác |
| **`INCLUDE`** | Server thực hiện `RequestDispatcher.include(...)` | Gộp nội dung resource khác vào response; quy tắc response khác forward |
| **`ASYNC`** | Một async request được redispatch từ async context vào servlet chain | Có thể chạy trên thread khác; không giống một request HTTP client mới |
| **`ERROR`** | Container phân phối tới error page/error handler | Thường đi qua error mapping; có thể có nested error dispatch |

Một filter chỉ chạy trong dispatch nếu **cả hai điều kiện** đúng:

1. Filter registration **map tới resource mục tiêu**: URL pattern/servlet name tương ứng.
2. Registration **bao gồm dispatcher type** hiện tại.

Sau đó, filter code vẫn có thể chủ động bỏ qua request theo đường dẫn, request attributes hoặc logic của `OncePerRequestFilter`. Filter được map `REQUEST` sẽ không tự nhận `ERROR` chỉ vì nó đã thấy request ban đầu.

### 4.1. Ví dụ: request có thể đi qua filter lần thứ hai khi forward

```text
GET /api/demo/forward           dispatcher=REQUEST
       |
       +-- Filter A [REQUEST]    chạy
       +-- Filter B [REQUEST]    chạy
       +-- MVC handler trả "forward:/api/demo/echo"
                         |
                         v
  server FORWARD tới /api/demo/echo      dispatcher=FORWARD
       |
       +-- Filter A [FORWARD]    chạy nếu registration có FORWARD
       +-- Filter B [REQUEST]    KHÔNG chạy ở FORWARD
       +-- MVC handler echo
```

Đây là **server-side forward**, không phải `302/303` redirect. Browser không nhận request URL mới, vì container tự dispatch bên trong. Nếu handler trả `"redirect:/api/demo/echo"` thì client tạo HTTP request mới, dispatcher của request đó thường là **`REQUEST`**, không phải `FORWARD`.

### 4.2. `ERROR` và đường dẫn `/error`

Một sự cố có thể tạo luồng:

```text
GET /api/orders/1 (REQUEST)
   |
  lỗi chưa được xử lý / sendError
   |
Servlet container ERROR dispatch -> /error
   |
BasicErrorController / error view (tùy Spring Boot)
```

**Filter muốn thấy `ERROR` phải đăng ký `DispatcherType.ERROR` và mapping phải khớp đích error dispatch.** Ví dụ filter map chỉ `/api/orders/*` có thể **không** chạy ở error target `/error` dù registration có `ERROR`. Filter map `/*` có thể thấy cả hai, nhưng còn bị `OncePerRequestFilter` skip nếu cấu hình mặc định của class đó bỏ qua ERROR.

Không phải mọi response lỗi 4xx/5xx đều tạo ERROR dispatch: `@ControllerAdvice` hoặc handler có thể trả `ResponseEntity`/`ProblemDetail` trực tiếp trong **cùng REQUEST dispatch**. Ngược lại, filter/security/container có thể gây error handling trước khi MVC chạy. Hãy kiểm tra thực tế thay vì suy đoán từ status code.

### 4.3. `ASYNC` dispatch

Trong Spring MVC, một handler trả `Callable<T>`, `DeferredResult<T>` hoặc cơ chế bất đồng bộ khác có thể bắt đầu async processing. Thread phục vụ request ban đầu có thể thoát; kết quả async được redispatch về Servlet container để hoàn thiện HTTP response.

```text
HTTP request (REQUEST)
   |
Filter A -> DispatcherServlet
                 |
                 +-- controller trả Callable
                 +-- request startAsync
   |
REQUEST thread có thể kết thúc
   |
worker thread tính toán kết quả
   |
Servlet ASYNC dispatch
   |
Filter A [ASYNC] nếu đã map và không tự skip
   |
DispatcherServlet render/ghi response
```

Filter có dùng `ThreadLocal` phải quản lý set/clear **cho từng thread/dispatch phù hợp**, không tin context tự truyền sang worker thread. Khi Servlet Filter cần tham gia request bất đồng bộ, phải kiểm tra **`setAsyncSupported(true)`**, dispatcher mapping, Servlet/filters trong toàn chain, và logic filter class. “Có `ASYNC` trong mapping” không tự cho phép mọi filter bất đồng bộ nếu async support của toàn chain không hợp lệ.

### 4.4. `INCLUDE`

`RequestDispatcher.include(...)` khiến một resource đóng góp output cho response của request đang chạy. Servlet Filter có thể tham gia **INCLUDE** khi registration map loại đó. Các thay đổi status/headers trong included resource bị giới hạn bởi Servlet semantics; không dùng filter INCLUDE như cơ chế thay thế cho response controller chính. Các template engine hiện đại thường dùng cơ chế fragment của riêng chúng, **không đồng nghĩa Servlet INCLUDE dispatch**.

## 5. Các cách đăng ký Servlet Filter trong Spring Boot

### 5.1. Khai báo bean `Filter` để Boot tự đăng ký

Trong ứng dụng Boot có embedded Servlet container, một bean `Filter` được phát hiện và thường được Boot tự đăng ký vào ServletContext:

```java
@Bean
Filter simpleFilter() {
    return (request, response, chain) -> {
        // Trước downstream...
        chain.doFilter(request, response);
        // Sau downstream...
    };
}
```

Đây là **bean factory method fragment** trong class `@Configuration`, cần imports `jakarta.servlet.Filter` và `org.springframework.context.annotation.Bean`. Boot mặc định map filter bean theo quy tắc filter registration, thường `/*`.

Đường này tiện khi không cần tùy chỉnh URL pattern, dispatchers hoặc order sâu. Nhưng nếu ứng dụng cần **chính xác** thứ tự/dispatcher/URL, nên dùng `FilterRegistrationBean` hoặc API registration thích hợp.

### 5.2. `FilterRegistrationBean<T>`: cấu hình rõ ràng

`org.springframework.boot.web.servlet.FilterRegistrationBean<T>` đăng ký một filter với embedded Servlet container và cho phép:

- `setFilter(filter)` hoặc constructor nhận filter.
- `setOrder(int)` chỉ định thứ tự registration.
- `addUrlPatterns(...)` / `setUrlPatterns(...)` giới hạn URL.
- `setDispatcherTypes(DispatcherType...)` hoặc `setDispatcherTypes(EnumSet<DispatcherType>)`.
- `setAsyncSupported(boolean)`.
- `setName(...)`, init parameters, servlet names và một số chi tiết mapping khác.

Ví dụ:

```java
@Bean
FilterRegistrationBean<AuditFilter> auditRegistration() {
    FilterRegistrationBean<AuditFilter> registration =
            new FilterRegistrationBean<>(new AuditFilter());

    registration.setName("auditFilter");
    registration.addUrlPatterns("/api/*");
    registration.setDispatcherTypes(
            DispatcherType.REQUEST, DispatcherType.ERROR);
    registration.setOrder(100);
    registration.setAsyncSupported(true);
    return registration;
}
```

Đây là **fragment**: `AuditFilter` là filter class do ứng dụng phải định nghĩa. Ý tưởng là đăng ký **một filter instance thông qua một registration bean**. Không tự gắn đồng thời `@Component`/`@WebFilter` vào chính filter rồi lại đăng ký nó riêng theo cách gây double registration.

### 5.3. `@WebFilter` và `@ServletComponentScan`

Servlet API có `@WebFilter` để khai báo filter và URL/dispatcher mappings bằng annotation. Trong Spring Boot embedded container, muốn tự động quét Servlet components theo cách tương ứng thường dùng `@ServletComponentScan` (với Boot 3 dùng `jakarta.servlet.annotation.WebFilter`).

```java
@WebFilter(
    filterName = "legacyAudit",
    urlPatterns = "/legacy/*",
    dispatcherTypes = {
        DispatcherType.REQUEST, DispatcherType.FORWARD
    }
)
public class LegacyAuditFilter implements Filter {
    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(request, response);
    }
}
```

Đây là **class fragment** với imports cần thêm, phải đặt trong file `LegacyAuditFilter.java` và cấu hình Servlet component scan nếu dùng Boot executable JAR. Ở Spring Boot mới, có thêm những lựa chọn annotation registration do Boot cung cấp; luôn đối chiếu version thực tế trước khi sao chép.

Không coi `@WebFilter` tự có đầy đủ Spring dependency injection như `@Component` trong mọi kiểu deployment. Cần phân biệt ai quản lý lifecycle/filter instance.

### 5.4. `web.xml` / `ServletContext#addFilter`

Trong WAR hoặc Servlet application truyền thống, bạn có thể đăng ký filter trong `web.xml` hoặc programmatically thông qua `ServletContext`. Chúng tuân theo Servlet specification và hoạt động ngoài cơ chế `FilterRegistrationBean` của Boot. Khi migrate WAR sang embedded Boot, tránh để filter được đăng ký ở **cả hai đường** mà không có chủ đích.

## 6. Thứ tự FilterRegistrationBean, @Order và số ưu tiên

Khi dùng Boot registration, **số order nhỏ hơn chạy trước** trong nhóm các filters có order được Spring Boot quản lý. Ví dụ:

| Filter | Order | Trên đường vào | Trên đường ra |
| --- | ---: | --- | --- |
| Filter A | `10` | Trước B | Sau B |
| Filter B | `20` | Sau A | Trước A |
| Filter C | `100` | Sau B | Trước B và A |

Nếu hai filters cùng order, **đừng dựa vào thứ tự không được quy định rõ** giữa chúng. Tạo giá trị order rõ và kiểm thử khi thứ tự ảnh hưởng semantics. Các filter do framework thêm (encoding, form content, Security, forwarded headers...) cũng có order, vì vậy số `10` của ứng dụng không khẳng định “chạy đầu tiên trong tất cả filters”.

Theo tài liệu Boot 3.5, có ba cách cần phân biệt:

- Đặt `@Order` trên **class Filter** hoặc implement `Ordered` đối với filter bean có auto-registration.
- Đặt `setOrder(int)` trên **`FilterRegistrationBean`** khi muốn chắc chắn cho một registration.
- **Không trông cậy `@Order` chỉ trên `@Bean method` trả Filter** để thiết lập thứ tự của Servlet filter registration; Boot khuyến nghị dùng `FilterRegistrationBean` hoặc order trên chính Filter class/instance.

Một filter wrap request (để sửa headers, đọc cached body, forwarded data...) có thể cần chạy rất sớm. Nhưng không tùy tiện đặt filter đọc body tại `Ordered.HIGHEST_PRECEDENCE`: theo Spring Boot, nó có thể chạy **trước cả character encoding config**. Hãy cân đối thứ tự các filter đã có, đặc biệt Security, CORS, body wrappers và encoding.

### 6.1. Thứ tự registration khác với thứ tự `addFilterBefore` trong Spring Security

Nếu định nghĩa:

```java
http.addFilterBefore(myFilter, UsernamePasswordAuthenticationFilter.class);
```

Đây là **fragment cấu hình Spring Security**, không phải thứ tự của tất cả Servlet filters. Nó xác định vị trí **bên trong** một `SecurityFilterChain`; Servlet filter registration của Spring Security nằm ở tầng bên ngoài. Không dùng `@Order(1)` trên custom security filter và mặc định rằng nó đứng trước Spring Security authentication filter.

## 7. Thứ tự filter không giải quyết việc filter có chạy hay không

Một filter có thể **không chạy** dù order sớm vì:

1. URL request/dispatch **không khớp URL pattern** hoặc servlet name mapping.
2. Registration **không chứa dispatcher type** hiện tại.
3. Filter class **chủ động skip** bằng điều kiện riêng, ví dụ `OncePerRequestFilter#shouldNotFilter(...)`.
4. Với async/error, `OncePerRequestFilter` có `shouldNotFilterAsyncDispatch()` và `shouldNotFilterErrorDispatch()`.
5. `OncePerRequestFilter` phát hiện **already-filtered attribute** trong cùng dispatch/context và tránh thực thi `doFilterInternal` lần nữa.
6. Một filter trước đó không gọi `chain.doFilter(...)`.
7. Spring Security `FilterChainProxy` chọn một security chain khác, hoặc filter custom không được thêm vào chain khớp request.
8. Filter không đăng ký thành công, registration bị disable hoặc application dùng Servlet context/route khác.

Một filter có thể **chạy hai lần** dù order “đúng” khi:

- Một instance (hoặc hai instances cùng chức năng) được đăng ký độc lập ở cả Servlet và Security chain.
- Filter thường `implements Filter` được mapped tới nhiều dispatch types; mỗi dispatch là một lần đi qua chain.
- `REQUEST` dẫn tới `FORWARD`; filter map cả hai chạy ở cả hai.
- Error handling tạo `ERROR` dispatch mới; filter map ERROR chạy thêm.
- Async request redispatch nhiều lần; nếu filter map ASYNC và logic cho phép, có thêm execution.

**“Thứ tự” và “số lần chạy” là hai câu hỏi riêng**: muốn tìm nguyên nhân cần log filter name, dispatcher type, request URI, thread và request correlation ID.

## 8. `OncePerRequestFilter`: “once” nghĩa là gì?

`org.springframework.web.filter.OncePerRequestFilter` là base class Spring triển khai `Filter`, chủ yếu cung cấp `doFilterInternal(HttpServletRequest,HttpServletResponse,FilterChain)`. Nó dùng một **already-filtered request attribute** để tránh xử lý lặp trong những tình huống request dispatch lồng nhau.

```java
class RequestTagFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        request.setAttribute("serverTag", "from-filter");
        try {
            chain.doFilter(request, response);
        } finally {
            request.removeAttribute("serverTag");
        }
    }
}
```

Đây là **class fragment**, cần imports Jakarta Servlet và `OncePerRequestFilter`, cùng cấu hình registration. Khi filter chạy, downstream nhận `serverTag`; sau khi downstream trả về, attribute được dọn.

Các điểm dễ sai:

- `OncePerRequestFilter` **không tự đăng ký** với Servlet container. Vẫn cần Filter bean/FilterRegistrationBean/registration khác.
- Base class mặc định **bỏ qua async dispatch** bằng `shouldNotFilterAsyncDispatch() == true` và **bỏ qua error dispatch** bằng `shouldNotFilterErrorDispatch() == true`.
- Override `shouldNotFilterAsyncDispatch()` trả `false` nếu muốn `doFilterInternal` được tham gia trong `ASYNC` dispatch phù hợp, **nhưng** registration của Servlet Filter cũng phải bao gồm `DispatcherType.ASYNC`.
- Override `shouldNotFilterErrorDispatch()` trả `false` nếu muốn xử lý `ERROR`; registration cũng cần `DispatcherType.ERROR` và URL/Servlet mapping khớp target error.
- Với **nested error dispatch**, một số Servlet container có thể xử lý `ERROR` lồng trong `REQUEST` trước khi filter chain gốc hoàn toàn thoát. Spring có hook `doFilterNestedErrorDispatch(...)` để xử lý trường hợp này; hành vi không được tóm gọn thành “always once forever”.
- `FORWARD` có thể dùng lại cùng request và đụng already-filtered marker. Vì vậy một filter `OncePerRequestFilter` đăng ký cả REQUEST/FORWARD **không nhất thiết gọi `doFilterInternal` thêm lần nữa** khi forward nằm lồng trong `chain.doFilter` ban đầu.
- Base class dùng request attribute đánh dấu đã chạy cho **filter instance/name**; tránh cố sửa tên/key marker thủ công mà không hiểu tác động.

Ví dụ khi cần quan sát async và error:

```java
class AsyncErrorContextFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {
        try {
            // Set per-thread context nếu cần.
            chain.doFilter(request, response);
        } finally {
            // Clear ThreadLocal/MDC nếu đã set.
        }
    }
}
```

Đây là **class minh họa**, chưa có logic ThreadLocal cụ thể. Nó vẫn cần một registration kiểu:

```java
registration.setDispatcherTypes(
        DispatcherType.REQUEST,
        DispatcherType.ASYNC,
        DispatcherType.ERROR);
registration.setAsyncSupported(true);
```

Chỉ có override method nhưng quên registration → không thấy async/error. Chỉ có registration nhưng giữ default skip behavior → có thể vào `doFilter(...)` nhưng không chạy `doFilterInternal(...)` ở những dispatch đó.

## 9. Ví dụ Java 17+ Spring Boot 3.x chạy qua embedded Servlet container

Ví dụ này cố ý dùng **plain `Filter`** thay vì `OncePerRequestFilter` để thấy rõ hai lần thực thi trên `REQUEST` và `FORWARD`. Hai filters được tạo trực tiếp trong `FilterRegistrationBean`, **không** đánh dấu `@Component` để tránh vô tình đăng ký trùng.

Trong project Spring Boot 3.x (Servlet MVC) đã có Boot plugin, repositories và dependency management, thêm dependency:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đây là **fragment `build.gradle`**, không phải một build file hoàn chỉnh. `spring-boot-starter-web` ở Boot 3 có Servlet stack embedded container. Các imports dưới dùng `jakarta.servlet.*`, không dùng `javax.servlet.*` cũ của Boot 2.

Tạo **`src/main/java/demo/filterchain/FilterChainApplication.java`**:

```java
package demo.filterchain;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class FilterChainApplication {
    public static void main(String[] args) {
        SpringApplication.run(FilterChainApplication.class, args);
    }

    @Bean
    FilterRegistrationBean<RecordingFilter> firstRegistration() {
        FilterRegistrationBean<RecordingFilter> registration =
                new FilterRegistrationBean<>(new RecordingFilter("A"));

        registration.setName("demoFilterA");
        registration.addUrlPatterns("/api/filters/*");
        registration.setOrder(10);
        registration.setDispatcherTypes(EnumSet.of(
                DispatcherType.REQUEST,
                DispatcherType.FORWARD,
                DispatcherType.INCLUDE,
                DispatcherType.ASYNC,
                DispatcherType.ERROR));
        registration.setAsyncSupported(true);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RecordingFilter> secondRegistration() {
        FilterRegistrationBean<RecordingFilter> registration =
                new FilterRegistrationBean<>(new RecordingFilter("B"));

        registration.setName("demoFilterB");
        registration.addUrlPatterns("/api/filters/*");
        registration.setOrder(20);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setAsyncSupported(true);
        return registration;
    }
}

class RecordingFilter implements Filter {
    static final String EVENTS_KEY = "demo.filter.events";

    private final String label;

    RecordingFilter(String label) {
        this.label = label;
    }

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest http = (HttpServletRequest) request;

        @SuppressWarnings("unchecked")
        List<String> previous =
                (List<String>) http.getAttribute(EVENTS_KEY);

        List<String> events = previous;
        if (events == null) {
            events = new CopyOnWriteArrayList<>();
            http.setAttribute(EVENTS_KEY, events);
        }

        // Ghi nhận ở chiều đi vào; "before" chạy theo order tăng dần.
        events.add(label + ":" + http.getDispatcherType().name());

        // Luôn chuyển tiếp tới filter/servlet tiếp theo trong ví dụ.
        chain.doFilter(request, response);
    }

    static List<String> events(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        List<String> result =
                (List<String>) request.getAttribute(EVENTS_KEY);

        if (result == null) {
            return List.of();
        }
        return List.copyOf(result);
    }
}

@RestController
class EchoController {

    @GetMapping(
            path = "/api/filters/echo",
            produces = MediaType.TEXT_PLAIN_VALUE)
    String echo(HttpServletRequest request) {
        List<String> events = new java.util.ArrayList<>(
                RecordingFilter.events(request));

        events.add("handler:" + request.getDispatcherType().name());
        return String.join(" | ", events);
    }

    @GetMapping(
            path = "/api/plain",
            produces = MediaType.TEXT_PLAIN_VALUE)
    String plain() {
        return "plain";
    }
}

@Controller
class ForwardController {
    @GetMapping("/api/filters/forward")
    String forwardToEcho() {
        return "forward:/api/filters/echo";
    }
}
```

**Tính biên dịch:** đây là một file Java 17 hợp lệ khi build bằng Spring Boot 3.x + Servlet dependencies, vì chỉ `FilterChainApplication` là `public`. `RecordingFilter` và hai controllers là package-private; Spring Boot component scanning từ `demo.filterchain` vẫn tìm controllers. Không dùng `javac FilterChainApplication.java` mà thiếu Spring/Servlet classpath; dùng build tool của project.

### 9.1. Tại sao filter A và B chạy theo thứ tự đó?

- Hai registration cùng map URL `/api/filters/*`, cùng tham gia **`REQUEST`**.
- A có `order=10`, B có `order=20`; ở chiều vào: **A trước B**.
- B **chỉ map `REQUEST`**. A map `REQUEST` và `FORWARD` cùng những dispatcher types khác.
- Method `EchoController#echo` đọc event list **trước khi response body được ghi**; nó phản ánh các bước vào của filter đã xảy ra. Nếu sau `chain.doFilter` filter còn cleanup/log, các sự kiện sau đó **không tự xuất hiện trong body** đã render.
- `CopyOnWriteArrayList` được dùng để tránh race trong việc đọc danh sách sự kiện nếu có xử lý async; đây là demo observability, không phải thiết kế high-throughput logging tối ưu.

### 9.2. Request bình thường

```bash
./gradlew bootRun
curl -i 'http://localhost:8080/api/filters/echo'
```

Với registration như trên và Servlet container embedded thông thường, status **200 OK**, `Content-Type` tương thích `text/plain`, body:

```text
A:REQUEST | B:REQUEST | handler:REQUEST
```

`/api/plain` không khớp URL pattern `/api/filters/*` nên body chỉ:

```text
plain
```

Spring Boot vẫn có thể có các **framework filters khác** chạy trên cả hai endpoint; ví dụ chỉ ghi lại hai custom filters A/B. Không suy ra filter chain toàn ứng dụng chỉ có đúng A và B.

### 9.3. Server-side forward

```bash
curl -i 'http://localhost:8080/api/filters/forward'
```

Trong Servlet container hỗ trợ MVC forward như ví dụ, luồng được ghi:

```text
A:REQUEST | B:REQUEST | A:FORWARD | handler:FORWARD
```

Lý do:

1. Request đầu `/api/filters/forward` đi qua A và B (cùng map REQUEST).
2. MVC `ForwardController` trả `forward:/api/filters/echo`; server thực hiện `FORWARD` trong cùng HTTP request.
3. Ở forward dispatch, A vẫn map `FORWARD` và đích `/api/filters/echo`; B **không** map `FORWARD`.
4. `EchoController` được gọi với dispatcher type `FORWARD` và đọc request attribute list đã tích lũy.

Ví dụ dùng **plain Filter**, nên không có already-filtered marker của `OncePerRequestFilter` tự ngăn A lần thứ hai. Hành vi thực tế còn phụ thuộc URL mappings và container. Trong phần test tiếp theo, dùng server thật của `@SpringBootTest(RANDOM_PORT)` để kiểm tra thay vì chỉ mô phỏng với MockMvc.

### 9.4. Những giới hạn chủ ý của ví dụ

- Chỉ quan sát trực tiếp REQUEST/FORWARD. Đăng ký `ASYNC`/`ERROR`/`INCLUDE` cho A **không tự tạo** những dispatch này; muốn kiểm thử phải có async handler, error mapping hoặc Servlet include phù hợp.
- `ERROR` thường target `/error` ở Boot. Vì A chỉ map `/api/filters/*`, nó **có thể không thấy ERROR dispatch tới `/error`** dù đã đăng ký ERROR. Muốn theo dõi error dispatch, cần mapping target error thích hợp (thường `/*` hoặc `/error`).
- `FilterRegistrationBean#setAsyncSupported(true)` cho phép registration tham gia async, nhưng chỉ dùng async thực sự khi Servlet và các filters liên quan đều hỗ trợ.
- App dùng embedded container và không có Spring Security tùy chỉnh; nếu thêm security, có thêm filters trong chuỗi.
- Tránh dùng `CopyOnWriteArrayList` để tích lũy lượng lớn log request trong production; thay bằng structured logs/tracing, cleanup và giới hạn dữ liệu.
- Ví dụ không tự log các sự kiện “sau chain” để response body luôn phản ánh chính xác trạng thái tại thời điểm handler chạy.

## 10. Integration test với embedded container, không chỉ MockMvc

Một số chi tiết filter registration như dispatcher types, URL patterns và forward/error mapping được **Servlet container thực thi**. `MockMvc` rất hữu ích để test controller nhưng có thể không tái hiện đầy đủ dynamic filter registration/container forward/error dispatch như runtime thật.

Với `spring-boot-starter-test` của Boot 3, tạo **`src/test/java/demo/filterchain/FilterChainApplicationTests.java`**:

```java
package demo.filterchain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FilterChainApplicationTests {

    @Autowired
    TestRestTemplate http;

    @Test
    void requestDispatchRunsAThenBThenController() {
        ResponseEntity<String> response = http.getForEntity(
                "/api/filters/echo", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(
                "A:REQUEST | B:REQUEST | handler:REQUEST",
                response.getBody());
    }

    @Test
    void servletForwardDispatchRunsOnlyAAgain() {
        ResponseEntity<String> response = http.getForEntity(
                "/api/filters/forward", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(
                "A:REQUEST | B:REQUEST | A:FORWARD | handler:FORWARD",
                response.getBody());
    }

    @Test
    void customFiltersDoNotMatchOtherUrlPattern() {
        ResponseEntity<String> response = http.getForEntity(
                "/api/plain", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("plain", response.getBody());
    }
}
```

Chạy:

```bash
./gradlew test
```

**Kỳ vọng ba test pass** với cấu hình Spring Boot 3.x Servlet embedded như ví dụ, không có security/filter phụ thay đổi route/response. Đây là kết quả mong đợi từ logic source; **không phải log thực thi đã thu được**. Nếu môi trường sử dụng servlet container khác hoặc có custom filters, kiểm tra dispatching/registration trên server thực tế thay vì ép test output để che sai khác.

Ưu thế của `RANDOM_PORT`: ứng dụng khởi chạy Servlet container và nhận HTTP qua port thật, nên đo được filter registration. Nó chậm hơn `MockMvc` nhưng phù hợp kiểm tra URL mapping/dispatcher types. Với Spring Boot 4, test infrastructure/module và API TestRestTemplate có thể đổi; ví dụ này có baseline Boot 3.

### 10.1. Test ASYNC và ERROR cần tình huống riêng

Để xác minh `ASYNC`:

- Tạo handler MVC trả `Callable<String>` hoặc `DeferredResult<String>` và bảo đảm async được enabled ở toàn servlet/filter chain.
- Đăng ký filter theo `DispatcherType.REQUEST` và `ASYNC`.
- Nếu dùng `OncePerRequestFilter`, quyết định rõ có override `shouldNotFilterAsyncDispatch()` hay không.
- Log riêng dispatcher type, thread ID, request correlation ID trong từng phase; assert chính xác số lần theo contract của filter.
- Test bằng embedded server thực và đảm bảo không dựa vào timing ngẫu nhiên.

Để xác minh `ERROR`:

- Tạo tình huống lỗi đi qua **container error dispatch**, ví dụ Servlet có error mapping phù hợp.
- Đảm bảo filter map `DispatcherType.ERROR` và **URL target error** phù hợp.
- Nếu filter là `OncePerRequestFilter`, cấu hình `shouldNotFilterErrorDispatch()`; kiểm tra nested ERROR behavior của container.
- Không dùng mỗi một `@RestControllerAdvice` trả 400/500 trực tiếp rồi kết luận container đã chạy ERROR dispatch.

Để xác minh `INCLUDE`, dùng `RequestDispatcher.include(...)` theo Servlet API và xác nhận filter registration/URL mapping, không dùng Thymeleaf fragment inclusion rồi mong `DispatcherType.INCLUDE` xuất hiện.

## 11. Spring Security filter chain và nguy cơ đăng ký trùng

Trong ứng dụng có Spring Security, container thường thấy một Servlet filter proxy (`DelegatingFilterProxy`) điều phối vào bean `springSecurityFilterChain`. Bên trong `FilterChainProxy`, có một hoặc nhiều `SecurityFilterChain` khớp URL theo rule của Spring Security.

Một cấu hình ví dụ (fragment Java dành cho Spring Security 6.x):

```java
@Bean
SecurityFilterChain apiSecurity(
        HttpSecurity http,
        ApiAuditFilter audit) throws Exception {

    http.securityMatcher("/api/**")
        .authorizeHttpRequests(registry ->
                registry.anyRequest().authenticated())
        .addFilterBefore(
                audit, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```

Đây là **fragment**, cần dependency Spring Security, các imports tương ứng và bean `ApiAuditFilter`. Method `addFilterBefore` thao tác trong Spring Security chain, **không** dùng `FilterRegistrationBean#setOrder`.

Nếu `ApiAuditFilter` là Spring bean và implements `Filter`, Spring Boot có thể **auto-register** nó thành Servlet filter. Lúc đó cùng logic audit có thể chạy:

1. Một lần trong container chain do Boot tự đăng ký.
2. Một lần bên trong Spring Security chain do `addFilterBefore`.

Một kỹ thuật để ngăn Servlet auto registration khi filter được sử dụng **chỉ ở Spring Security**:

```java
@Bean
FilterRegistrationBean<ApiAuditFilter> disableServletRegistration(
        ApiAuditFilter filter) {

    FilterRegistrationBean<ApiAuditFilter> registration =
            new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
}
```

Đây là fragment trong `@Configuration`, cần `ApiAuditFilter` do ứng dụng định nghĩa. Khi có filter đăng ký riêng hoặc nhiều context, hãy kiểm tra thực tế auto-registration và đảm bảo không làm mất đăng ký mong muốn của chính mình.

**Vấn đề CORS:** preflight `OPTIONS` có thể không mang session cookie/credentials, nên CORS phải được xử lý **trước lớp security authorization phù hợp**. Trong app có Spring Security, nên dùng cơ chế CORS tích hợp của Security và MVC (hoặc CORS filter sắp xếp đúng), tránh có nhiều nguồn CORS headers mâu thuẫn.

**Vấn đề CSRF:** Servlet filter chain và security chain ảnh hưởng các HTTP methods thay đổi state; không bỏ CSRF protection tùy tiện chỉ vì filter custom gặp 403.

## 12. Request/response wrappers và filter order

Filter có thể bọc request/response:

```java
HttpServletRequest wrapped =
        new HttpServletRequestWrapper(originalRequest) {
            // Override method cần thiết theo mục đích.
        };

chain.doFilter(wrapped, response);
```

Đây là **fragment**, cần `HttpServletRequestWrapper` và biến `originalRequest`. Wrapper có thể thêm headers, cache body hoặc thay đổi cách downstream nhìn thấy request. Nếu đặt wrapper quá muộn, một filter trước nó đã đọc dữ liệu cũ; nếu đặt quá sớm không phù hợp encoding, có thể phá decoding.

Những mối quan tâm:

- **Character encoding:** xác định charset **trước khi ai đó đọc parameters/body**; việc set encoding sau khi body đã đọc không sửa được dữ liệu.
- **HTTP body stream:** request body thông thường chỉ đọc được một lần nếu không dùng caching wrapper đúng; không log dữ liệu nhạy cảm, không đọc toàn body vô điều kiện.
- **Forwarded headers:** `ForwardedHeaderFilter` có thể wrap request để downstream nhìn scheme/host/prefix do reverse proxy truyền vào. Filter này phải chạy trước các thành phần cần dùng forwarded view; chỉ tin headers từ proxy đã cấu hình an toàn.
- **ContentCachingRequestWrapper`:** có thể lưu bytes **đã được downstream đọc**; không mặc định rằng chỉ wrap là có sẵn toàn bộ body. Lưu ý memory và max payload.
- **Async & ERROR:** wrapper có thể bị thay thế hoặc không còn như cũ khi dispatch xảy ra ở thread khác hoặc nested ERROR; kiểm tra base filter hỗ trợ `doFilterNestedErrorDispatch` nếu cần giữ wrapper semantics.
- **Response wrapper:** response có thể đã committed; không cố đọc/thay bytes sau `chain.doFilter` mà không có thiết kế buffer đúng và giới hạn dung lượng.

Một custom filter chỉ cần correlation ID thường không nên wrap toàn body; đặt header/request attribute hoặc logging MDC ở đúng scope là đủ.

## 13. Logging và chẩn đoán filter chain

Để tìm filter chạy sai thứ tự, nên log tối thiểu:

- Filter name và registration order (tại startup).
- Dispatcher type: `request.getDispatcherType()`.
- HTTP method, request URI, servlet path và context path.
- Correlation ID do server hoặc hạ tầng tin cậy cấp.
- Thread name/ID nếu đang điều tra ASYNC.
- Sự kiện `before`/`after` và thời gian đo.
- Thông tin lỗi đã được phân loại, không lộ token/session ID/body nhạy cảm.

Trong Boot 3 có thể bật:

```properties
logging.level.web=debug
```

Theo tài liệu Boot, điều này hữu ích để thấy filter registrations tại startup, bao gồm order/URL mapping. Có thể cần logger package cụ thể cho Servlet container và Spring Security để xem chi tiết runtime chain; đừng mở DEBUG/TRACE toàn cục trong production vì vừa tốn chi phí vừa có nguy cơ log dữ liệu nhạy cảm.

### 13.1. Dấu hiệu và nguyên nhân

| Triệu chứng | Nguyên nhân thường gặp | Cách xác minh |
| --- | --- | --- |
| Filter không bao giờ chạy | Không đăng ký, registration disabled hoặc URL pattern sai | Startup logs và request tới URL khớp |
| Filter chỉ chạy với GET thường, không chạy khi forward | Registration không map FORWARD hoặc `OncePerRequestFilter` skip nested dispatch | Log dispatcher type và target URL |
| Filter không chạy ở `/error` | Chỉ map REQUEST hoặc URL không khớp `/error` | Đăng ký ERROR + mapping đích, test container |
| Filter không chạy ở async completion | Thiếu ASYNC mapping, asyncSupported false, OncePerRequestFilter skip | Kiểm tra cả registration và override |
| Filter chạy hai lần trên mỗi request | Auto-registration + Security chain hoặc multiple registrations | Liệt kê registration, security chain, filter IDs |
| URL ghi vào Location sai phía sau proxy | Forwarded header filter chạy sai order/trust | Kiểm tra external request vs nội bộ |
| 401/403 trước khi controller chạy | Security filter chain từ chối | Debug security matcher và authentication |
| 400 do body parse bất ngờ | Body bị đọc trước converter hoặc encoding sai | Kiểm tra filter body wrapper/order |
| Request không đến controller, client “treo” | Filter quên `chain.doFilter` hoặc không tự trả response | Trace before/after và breakpoints |
| ThreadLocal chứa data từ user trước | Không clear trong `finally` hoặc bị reuse thread | Audit MDC context lifetime |
| Chỉ lỗi ở cluster, không tái hiện local | Filter state singleton/local, session store khác | Kiểm tra node ID, sticky sessions, shared state |

### 13.2. Cách cô lập lỗi

1. Xác nhận request **có đi vào Servlet container** hay bị gateway/WAF chặn trước.
2. In danh sách filter registrations và URL patterns.
3. Log `DispatcherType` ở filter đầu tiên, không suy từ HTTP method.
4. Nếu dùng Spring Security, log matching `SecurityFilterChain`.
5. Nếu lỗi xảy ra khi forward/error/async, tạo một integration test với dispatch tương ứng.
6. Kiểm tra filter đã gọi `chain.doFilter` đúng và cleanup `ThreadLocal` trong `finally`.
7. Đọc response status/headers/body **trước khi quyết định tầng nào xử lý lỗi**.
8. So sánh embedded container ở dev/test với container/proxy production nếu hai môi trường khác nhau.

## 14. Khi nào dùng Filter, Interceptor hay ControllerAdvice?

| Bài toán | Lựa chọn thường hợp lý | Giải thích |
| --- | --- | --- |
| Character encoding, HTTP wrapper, request correlation tại ingress | Servlet Filter | Cần trước MVC handler |
| Authentication/authorization | Spring Security filter chain | Cơ chế security chuyên dụng |
| CORS | Spring MVC/Spring Security CORS tích hợp hoặc CorsFilter đúng order | Cần xử lý OPTIONS trước auth phù hợp |
| Bổ sung model attribute cho view | `@ModelAttribute` method | MVC model, không cần filter |
| Quyết định theo handler được chọn, audit tên handler | `HandlerInterceptor` | Có context handler MVC |
| Kiểm tra DTO/request body | `@Valid`/`@Validated` + controller/service validation | Không nên deserialize JSON tùy tiện trong filter |
| Chuyển exception MVC/domain thành HTTP error | `@RestControllerAdvice` / `ResponseEntityExceptionHandler` | Tập trung error contract |
| Xử lý lỗi authentication từ Security | `AuthenticationEntryPoint` / `AccessDeniedHandler` | Nằm ở filter layer phù hợp |
| Truyền context nội bộ giữa methods | Tham số rõ ràng hoặc request attributes | Tránh lạm dụng ThreadLocal |
| Theo dõi async/error across dispatch | Servlet Filter có dispatcher mapping + lifecycle phù hợp | Không chỉ interceptor REQUEST đơn giản |

**Không đặt nghiệp vụ chính hoặc truy vấn DB nặng vào mọi Servlet Filter** nếu chỉ một endpoint cần. Filter nên tập trung vào cross-cutting concern liên quan HTTP; giới hạn phạm vi để tránh ảnh hưởng cả health checks, static files và trang lỗi.

## 15. Phân biệt Spring Framework / Boot theo phiên bản

| Nền tảng | Filter registration / dispatch notes |
| --- | --- |
| Spring Framework 5.x + Boot 2.x | Servlet Filter API thường là `javax.servlet.*`, `OncePerRequestFilter` và `FilterRegistrationBean` đã có |
| Spring Framework 6.x + Boot 3.x | Java 17+, Jakarta Servlet `jakarta.servlet.*`; `FilterRegistrationBean`/`OncePerRequestFilter` dùng như trong ví dụ |
| Spring Framework 7.x + Boot 4.x | Servlet filter semantics và `OncePerRequestFilter` tiếp tục có; Boot 4 có điều chỉnh module, starter, registration annotation và test infrastructure; đọc đúng Javadoc/BOM của version đang dùng |

Ở mọi version, **Servlet spec dispatcher types khác nhau** không biến `REQUEST` thành `ASYNC` chỉ vì code có CompletableFuture. Ứng dụng cần thực sự kích hoạt Servlet asynchronous processing thì mới có redispatch ASYNC.

Một thay đổi khi migrate Boot 2 → Boot 3 thường gặp: imports `javax.servlet.Filter`, `javax.servlet.DispatcherType` phải đổi sang `jakarta.servlet.*`. Thay đổi dependency Jackson, validation hay Spring Security có thể ảnh hưởng các filters khác, nên sau nâng cấp phải kiểm tra filter registration, order, URL mapping và dispatcher types bằng test thật.

## 16. Checklist đưa filter vào production

- [ ] Phạm vi URL/Servlet registration được giới hạn đúng nhu cầu; không mặc định mọi filter cần `/*`.
- [ ] Order rõ ràng khi có phụ thuộc, không rely vào filter order bằng nhau.
- [ ] Biết filter thuộc Servlet chain hay Spring Security chain; không đăng ký hai lần.
- [ ] `DispatcherType.REQUEST`/`FORWARD`/`INCLUDE`/`ASYNC`/`ERROR` được chọn có chủ đích.
- [ ] Nếu cần ASYNC, thiết lập async support và policy `OncePerRequestFilter` cho từng thread/dispatch.
- [ ] Nếu cần ERROR, map cả target error dispatch; kiểm tra nested error case.
- [ ] Filter luôn gọi `chain.doFilter` nếu không chủ động hoàn tất response.
- [ ] ThreadLocals/MDC/resource cleanup dùng `try/finally`, không leak giữa requests.
- [ ] Không sửa response headers sau khi đã committed.
- [ ] Encoding/body wrapper đặt đúng chỗ, có giới hạn memory và tránh log bí mật.
- [ ] Forwarded headers chỉ được tin từ proxy cấu hình; auth/CORS/Security order được kiểm tra.
- [ ] Có test request bình thường, forward, async, error khi những dispatch này nằm trong contract.
- [ ] Thứ tự/registration được kiểm thử với container thật ít nhất ở integration tests quan trọng.
- [ ] Có logging/tracing giúp truy nguyên loại dispatcher, filter name và lỗi production.

## 17. Bài tập thực hành

1. Trong app phần 9, đổi `registration.setOrder(10)` của A thành `30`. Điều gì thay đổi trong body `GET /api/filters/echo`? Vì sao chiều return sau chain (nếu có) lại theo hướng ngược?
2. Bỏ `DispatcherType.FORWARD` khỏi A rồi gọi `/api/filters/forward`. Chuỗi sự kiện dự kiến còn gì? Test server thật.
3. Thay A bằng subclass `OncePerRequestFilter`. Ở nested forward trong `chain.doFilter`, tại sao `doFilterInternal` có thể **không** chạy lần thứ hai ngay cả khi registration có FORWARD?
4. Đăng ký A chỉ map `/api/filters/*` và map ERROR. Một exception dẫn tới error page `/error`. Vì sao A có thể không chạy ở error dispatch?
5. Thêm một `Callable<String>` controller và filter async-aware. Phân biệt `setAsyncSupported`, dispatcher mapping ASYNC và `shouldNotFilterAsyncDispatch()`.
6. Một filter implements `Filter` là bean Spring và được thêm vào `HttpSecurity.addFilterBefore`. Tại sao có nguy cơ xử lý hai lần? Hãy disable container registration nhưng giữ security chain.
7. Đặt filter đọc body ở order cực nhỏ trước character encoding filter. Những lỗi nào có thể xuất hiện với form data UTF-8?
8. Dùng `MockMvc` và `@SpringBootTest(RANDOM_PORT)` để test. Loại nào phù hợp hơn để chứng minh Servlet container đăng ký FORWARD/ERROR? Vì sao?
9. Bổ sung `RequestDispatcher.include(...)` vào một Servlet. Filter có map INCLUDE sẽ chạy khi nào, và sự khác biệt với Thymeleaf template fragment là gì?
10. Nếu filter không gọi `chain.doFilter`, cần làm gì để không gây response dang dở? Nêu một ví dụ chặn request hợp lệ và một trường hợp bug.
11. Phân biệt `HandlerInterceptor#preHandle`, Servlet Filter `doFilter` và Spring Security `SecurityFilterChain` trong một app có cả ba.
12. Thiết kế correlation ID được ghi trong REQUEST và cả ERROR dispatch nhưng không leak sang thread/client khác; giải thích cleanup theo `finally`.

## 18. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Servlet Filters](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/filters.html): filter chain, built-in filters, wrappers, forwarded headers và dispatch.
- [Spring Framework 6.2 — OncePerRequestFilter Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/filter/OncePerRequestFilter.html): already-filtered marker, async/error skipping và nested ERROR dispatch.
- [Spring Boot 3.5 — Servlet Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html): Servlet filter beans, order, registration và startup debug logging.
- [Spring Boot 3.5 — FilterRegistrationBean Javadoc](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/web/servlet/FilterRegistrationBean.html): URL patterns, order, Servlet registrations, dispatcher types.
- [Spring Boot 3.5 — Embedded Web Servers](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html): dispatcher types defaults và filter registration.
- [Spring Security — Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html): FilterChainProxy, SecurityFilterChain và order.
- [Spring Security — Servlet CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html): thứ tự CORS và Spring Security.
- [Jakarta Servlet Specification](https://jakarta.ee/specifications/servlet/6.0/): Servlet Filter, dispatcher types, request dispatch và async handling.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="filters-interceptors-003">OncePerRequestFilter và hành vi với async/error dispatch</a>

<details>
<summary>Xem chi tiết</summary>

# OncePerRequestFilter và hành vi với async/error dispatch trong Spring MVC

## 1. Tại sao “once per request” có thể gây hiểu nhầm?

Trong một ứng dụng Spring MVC, ta thường muốn ghi trace ID, đo thời gian xử lý, thêm HTTP headers hoặc tạo context phục vụ logging. Đặt logic tại Servlet Filter là hợp lý vì Filter hoạt động trước/sau servlet trong chuỗi xử lý.

Nhưng một HTTP request có thể được container **dispatch nhiều lần**:

- Lần đầu: `DispatcherType.REQUEST`.
- Ứng dụng bắt đầu xử lý bất đồng bộ và kết quả được dispatch trở lại: `DispatcherType.ASYNC`.
- Servlet container chuyển tới trang/endpoint lỗi: `DispatcherType.ERROR`.

Nếu một filter chạy lại trong cùng request, việc tạo trace mới, đếm request, gửi sự kiện nghiệp vụ hoặc bọc response nhiều lần có thể gây sai lệch. Spring cung cấp `org.springframework.web.filter.OncePerRequestFilter` để quản lý việc filter **chỉ thực hiện một lần trong một request dispatch trên một thread**, với quy tắc rõ về xử lý lại ở `ASYNC` hoặc `ERROR`.

**Không hiểu tên class thành “doFilterInternal chạy đúng một lần duy nhất trong toàn bộ vòng đời một HTTP request bất kể dispatch/thread”.** Nếu bạn chủ động bật tham gia `ASYNC`/`ERROR`, `doFilterInternal` có thể chạy ở những dispatch riêng sau đó. Còn một `ERROR` dispatch lồng bên trong chuỗi `REQUEST` có thể đi qua `doFilterNestedErrorDispatch` thay vì `doFilterInternal`.

### Mục tiêu

Sau bài học bạn có thể:

1. Phân biệt Servlet `Filter`, Spring MVC `HandlerInterceptor`, `OncePerRequestFilter` và các kiểu dispatch.
2. Giải thích chính xác “already filtered” request attribute, cách set/remove bằng `try/finally` và hệ quả cho nhiều dispatch.
3. Phân tích `shouldNotFilterAsyncDispatch()`, `shouldNotFilterErrorDispatch()`, `shouldNotFilter(HttpServletRequest)`, `isAsyncDispatch()`, `isAsyncStarted()`, `doFilterNestedErrorDispatch()`.
4. Kết hợp **hai lớp cấu hình**: filter được Servlet container **đăng ký cho dispatcher types nào** và filter **có chủ đích skip dispatch nào**.
5. Thiết kế logic tracing, logging, wrapping, thread-local, security và cleanup không bị chạy lặp sai.
6. Viết demo Spring Boot 3/Java 17 và kiểm thử bằng các Servlet mocks; hiểu giới hạn của `MockMvc` khi mô phỏng `ERROR` dispatch thực.

**Bối cảnh:** Spring MVC Servlet stack, Spring Framework 6.2.x, Spring Boot 3.x, Java 17+, Jakarta Servlet API. Trong Spring Boot 3/Spring Framework 6 các import là `jakarta.servlet.*`, không phải `javax.servlet.*` của hệ thống đời cũ.

## 2. Filter nằm ở đâu trong luồng HTTP?

Luồng synchronous điển hình:

```text
Client
   │ HTTP request
   ▼
Servlet container (Tomcat/Jetty/Undertow)
   │
   ├─ Filter A.doFilter(...)
   │    ├─ mã trước filterChain
   │    ▼
   │    Filter B.doFilter(...)
   │         └─ DispatcherServlet
   │              └─ Spring MVC HandlerInterceptor
   │                   └─ Controller
   │                   └─ View/HTTP message converter
   │
   ├─ mã sau filterChain của Filter B
   └─ mã sau filterChain của Filter A
   ▼
HTTP response
```

Servlet Filter thuộc **Servlet container filter chain**, nên có thể thấy request trước khi Spring MVC chọn handler, kể cả đường đi không tới controller (tùy mapping). `HandlerInterceptor` thuộc **Spring MVC HandlerExecutionChain** và hoạt động sau handler mapping; interceptor không thay thế hoàn toàn Servlet Filter.

Khi một controller dùng `Callable<T>`, `DeferredResult<T>` hoặc cơ chế Servlet async, thread ban đầu có thể trả quyền điều khiển về container **trước khi response hoàn tất**. Filter chain có thể kết thúc một lượt `REQUEST` rồi có lượt `ASYNC` mới. Điều này ảnh hưởng tới việc đo thời gian, kết thúc trace, set header và dọn dẹp thread-local.

### 2.1. Bảng DispatcherType

| DispatcherType | Khi nào có thể xuất hiện | Điểm cần lưu ý |
| --- | --- | --- |
| `REQUEST` | Client gửi yêu cầu HTTP ban đầu | Đây là dispatch đầu tiên thường gặp |
| `ASYNC` | Servlet async hoàn tất một giai đoạn và dispatch lại | Có thể chạy trên thread khác; không tự động được mọi filter tham gia |
| `ERROR` | Container thực hiện error-page dispatch | Có thể tách thành filter chain mới hoặc lồng trong REQUEST |
| `FORWARD` | Một servlet forward nội bộ | Một request có thể có nhiều lượt dispatch |
| `INCLUDE` | Một servlet/include resource khác | Tùy cách đăng ký filter có được gọi lại |

`OncePerRequestFilter` đặc biệt có API skip cho `ASYNC` và `ERROR`. Với `FORWARD`/`INCLUDE`, cơ chế “already filtered” và dispatcher mapping vẫn quan trọng; không được suy diễn rằng class chỉ bao giờ liên quan tới ba kiểu đầu. Trong Spring MVC, `forward:...` có thể tạo `FORWARD` dispatch nhưng chỉ khi Servlet container thực hiện dispatch tương ứng.

**Không đồng nhất HTTP request với Servlet dispatch:** một HTTP request có thể đi qua nhiều dispatch, và một dispatch chỉ là một chặng của quá trình xử lý.

## 3. OncePerRequestFilter cung cấp gì?

Class trừu tượng:

```java
public abstract class OncePerRequestFilter extends GenericFilterBean {

    @Override
    public final void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        // Spring quản lý việc skip, marker và gọi doFilterInternal.
    }

    protected abstract void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException;
}
```

*Đây là trích yếu API mang tính giáo dục, không phải bản sao đầy đủ mã nguồn Spring. Nên override `doFilterInternal`, không override phương thức `doFilter` của base class.*

Sơ đồ quyết định đơn giản hóa từ implementation Spring 6.2:

```text
doFilter(request,response,chain)
       │
       ├─ Request/response không phải HTTP?
       │      └─ lỗi ServletException
       │
       ├─ Dispatch bị bỏ qua theo shouldNotFilterAsyncDispatch /
       │  shouldNotFilterErrorDispatch hoặc shouldNotFilter?
       │      └─ chain.doFilter, KHÔNG gọi doFilterInternal
       │
       ├─ Đã có marker "already filtered" trên request?
       │      ├─ DispatcherType.ERROR
       │      │     └─ doFilterNestedErrorDispatch(...)
       │      └─ Kiểu khác
       │            └─ chain.doFilter, không chạy doFilterInternal
       │
       └─ Chưa có marker
             ├─ request.setAttribute(marker, true)
             ├─ doFilterInternal(...)
             │     └─ thường gọi chain.doFilter(...)
             └─ finally request.removeAttribute(marker)
```

Đây không phải một khóa toàn cục hoặc bộ nhớ “request ID đã xử lý trong 24 giờ”. Marker là **request attribute**, tồn tại trong phạm vi lượt filter đang thực thi và bị dọn ở `finally` của lượt đó.

### 3.1. Already-filtered attribute

Base class mặc định xây tên attribute từ tên filter cụ thể cộng hậu tố `.FILTERED`; khi chưa có tên đầy đủ có thể dùng class name. API:

- `getAlreadyFilteredAttributeName()`: xác định tên marker, có thể override khi thật sự cần.
- `ALREADY_FILTERED_SUFFIX`: hằng hậu tố dùng cho marker.
- Marker thường được đặt trước khi gọi `doFilterInternal`, xóa trong `finally` sau khi nó hoàn thành, kể cả khi chain ném exception.

Ví dụ minh họa:

```text
request.getAttribute("auditFilter.FILTERED") == null
    → set TRUE
    → chạy doFilterInternal
        → nếu trong đó chain khiến cùng filter được gọi lồng:
           marker vẫn TRUE → không chạy doFilterInternal lần nữa
    → finally remove attribute
```

Tên chính xác phụ thuộc filter registration/bean name. **Đừng cài logic ứng dụng phụ thuộc vào chuỗi marker nội bộ**, và đừng tự gán/chỉnh marker của framework nếu không có lý do được kiểm chứng.

**Hệ quả:** nếu `REQUEST` đã kết thúc lượt filter và request sau đó đi vào `ASYNC` dispatch, marker trước đã bị gỡ; nếu filter được đăng ký và không skip `ASYNC`, `doFilterInternal` có thể chạy tiếp một lượt mới. Đây là hành vi có chủ đích.

### 3.2. Filter không tự gọi chain thì sao?

Một filter chặn request có thể không gọi `filterChain.doFilter(...)` (ví dụ từ chối request do xác thực); controller không chạy. Nếu đã gọi chain, logic sau chain có thể chạy lại khi Servlet đã async-started nhưng **response chưa hoàn tất**. Để phân biệt, xem `isAsyncStarted(request)`.

Mọi đoạn code phải giữ nguyên contract Servlet: không ghi response hai lần, không sử dụng response sau khi đã commit, và không bỏ qua exception/cleanup một cách tình cờ.

## 4. ASYNC dispatch: cùng HTTP request nhưng nhiều lượt xử lý

Một controller minh họa:

```java
@GetMapping("/slow")
Callable<String> slow() {
    return () -> "finished";
}
```

*Đây là handler body minh họa; đặt trong `@RestController` với import `Callable`, `GetMapping`. Chạy thực tế còn phụ thuộc Servlet async support.*

Trình tự khái quát:

```text
Thread T1                                      Thread T2 / T3
─────────                                      ──────────────
HTTP REQUEST
  → Filter.dispatch=REQUEST
  → DispatcherServlet/controller
  → MVC bắt đầu async
  → REQUEST filter chain return
  → marker finally removed
                                               Callable chạy trong executor
                                               result được lưu
                                               Servlet ASYNC dispatch
                                                 → Filter.dispatch=ASYNC (*)
                                                 → DispatcherServlet hoàn tất response
                                                 → filter chain return
Response hoàn tất
```

(*) Chỉ khi filter **được map cho ASYNC** và không skip ASYNC.

### 4.1. shouldNotFilterAsyncDispatch()

Mặc định method trả **true**: `OncePerRequestFilter` bỏ qua các lượt `ASYNC` trong logic `doFilterInternal`.

```java
@Override
protected boolean shouldNotFilterAsyncDispatch() {
    return false; // muốn doFilterInternal tham gia ASYNC dispatch
}
```

Trả `false` **không đủ nếu Servlet container chưa đăng ký filter cho DispatcherType.ASYNC**. Cần cấu hình cả filter registration, ví dụ `FilterRegistrationBean#setDispatcherTypes(REQUEST, ASYNC)` và `setAsyncSupported(true)` nếu ứng dụng dùng async.

Khi trả false, filter có thể chạy **một lần trong từng thread/dispatch tham gia**. Điều đó hữu ích cho:

- Thiết lập thread-local MDC/trace context cho thread xử lý lượt ASYNC.
- Áp dụng context phục vụ response trong giai đoạn sau.
- Dọn tài nguyên theo lifecycle của từng dispatch.
- Thêm header sau khi response thật sự hoàn tất (nếu chưa commit).

Nhưng **không** dùng nó để mặc định gửi email, ghi hóa đơn hoặc tăng số “số lượng HTTP requests” ở mỗi lần `doFilterInternal`: một HTTP request có thể làm các side effect ấy xảy ra nhiều lần.

### 4.2. isAsyncDispatch() khác isAsyncStarted()

| API | Câu hỏi trả lời | Ví dụ |
| --- | --- | --- |
| `isAsyncDispatch(request)` | “Lượt hiện tại có là ASYNC dispatch không?” | Trong lượt xử lý tiếp theo sau async |
| `isAsyncStarted(request)` | “Request đang ở chế độ async, lượt hiện tại chưa phải lượt cuối?” | Sau controller tạo Callable nhưng trước khi hoàn tất request |

Dùng trong filter:

```java
boolean redispatch = isAsyncDispatch(request);
try {
    filterChain.doFilter(request, response);
} finally {
    boolean stillAsync = isAsyncStarted(request);
    if (!stillAsync) {
        // Trong nhiều luồng thông thường, đây là lúc
        // có thể thực hiện tác vụ hoàn tất của lượt xử lý.
    }
}
```

Đây chỉ là mẫu quan sát trạng thái. Nếu filter **không tham gia ASYNC dispatch**, nó không có cơ hội chạy `finally` ở lượt cuối; khi đó cần `AsyncListener`, callback MVC hoặc cơ chế phù hợp khác để phát sự kiện “HTTP request thực sự hoàn tất”.

### 4.3. AsyncListener khi cần theo dõi toàn bộ vòng đời

Để đo thời gian end-to-end từ request đầu tiên tới khi async hoàn tất, các lựa chọn gồm:

- Đặt start timestamp vào request attribute ngay lượt `REQUEST`, không reset trên redispatch.
- Nếu filter tham gia mọi ASYNC dispatch, kết thúc đo khi response thật sự hoàn tất và lifecycle đã rõ; không đo lặp trên mỗi lần dispatch.
- Với workflow đặc biệt, đăng ký `AsyncListener#onComplete` / `onError` / `onTimeout` và xử lý đăng ký lại ở `onStartAsync` khi async cycle mới bắt đầu; cần hiểu rằng việc gắn listener có thể phụ thuộc thời điểm gọi `request.startAsync()`.
- Spring MVC còn có `CallableProcessingInterceptor` và `DeferredResultProcessingInterceptor` cho những điểm hook MVC-specific.
- Tránh dùng một thread-local duy nhất để ghi trạng thái toàn HTTP request khi request có thể chuyển thread.

**Lưu ý về context propagation:** request attributes đi theo object Servlet request, nhưng `ThreadLocal`/MDC **không tự di chuyển sang executor thread**. Filter tham gia `ASYNC` dispatch cũng không nhất thiết chạy trên thread mà `Callable` được execute. Muốn truyền context vào worker thread, hãy cấu hình executor decorator/context propagation phù hợp.

## 5. ERROR dispatch: tách chain và nested dispatch

Servlet container có thể tiến hành xử lý lỗi theo một trong hai hình thức cần phân biệt.

### 5.1. ERROR dispatch riêng sau REQUEST

Lược đồ thường gặp:

```text
REQUEST dispatch
  → filter.doFilterInternal(REQUEST)
  → downstream báo lỗi / sendError
  → filter cleanup/finally, marker bị remove

ERROR dispatch mới (có thể thread khác)
  → filter.doFilterInternal(ERROR), NẾU:
       • filter registration có DispatcherType.ERROR
       • shouldNotFilterErrorDispatch() == false
  → /error hoặc error page
```

Theo mặc định, `shouldNotFilterErrorDispatch()` trả **true**, tức là không đưa `ERROR` dispatch vào `doFilterInternal`.

Để tham gia:

```java
@Override
protected boolean shouldNotFilterErrorDispatch() {
    return false;
}
```

Dù vậy, không có registration `DispatcherType.ERROR` thì container không gọi filter ở error chain. Và “doFilterInternal chạy trong ERROR” không có nghĩa filter tự nhận được mọi Java exception: exceptions có thể đã được xử lý ở controller bằng `@ExceptionHandler` mà không có Servlet ERROR dispatch.

### 5.2. Nested ERROR dispatch trong cùng REQUEST chain

Trên một số Servlet container, sau `response.sendError(...)` hoặc tình huống tương tự, ERROR dispatch có thể xảy ra **lồng ngay trong REQUEST chain cùng thread**, khi marker still present.

```text
Filter.doFilter(REQUEST)
  └── set already-filtered marker = TRUE
      └── doFilterInternal(REQUEST)
          └── chain.doFilter(...)
              └── container nested ERROR dispatch
                  └── Filter.doFilter(ERROR)
                      ├── marker vẫn TRUE
                      └── doFilterNestedErrorDispatch(...)
  └── finally remove marker
```

Trong trường hợp nested, Spring gọi `doFilterNestedErrorDispatch(...)`, **không tự gọi lại `doFilterInternal(...)`**, nếu filter không skip ERROR. Mặc định nested-error hook chỉ chuyển tiếp `filterChain.doFilter(...)`.

Một số container có thể đưa request/response về wrapper gốc ở nested ERROR. Nếu filter đã bọc request/response để sửa input/body/header, subclass có thể cần **bọc lại trong `doFilterNestedErrorDispatch`** để giữ hành vi mong muốn.

### 5.3. Override doFilterNestedErrorDispatch

```java
@Override
protected void doFilterNestedErrorDispatch(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {

    // Nếu filter đang dùng custom wrappers, có thể bọc lại
    // request/response tại đây sau khi đánh giá đúng container.
    filterChain.doFilter(request, response);
}
```

Hook này được Spring Framework thêm từ 5.1.9. **Đừng gọi `doFilterInternal` một lần nữa chỉ để “có log”** trừ khi bạn thật sự muốn lặp đúng toàn bộ logic và đã chứng minh an toàn. Nếu dùng wrapper, có thể cần riêng một hàm nhỏ “wrap only” cho nested-error.

### 5.4. Bảng đối chiếu

| Trạng thái | Marker còn? | Mặc định skip? | Nếu không skip và filter đã được map |
| --- | --- | --- | --- |
| REQUEST đầu tiên | Không | Không | `doFilterInternal(REQUEST)` |
| REQUEST lồng, marker còn | Có | Không | Chỉ `chain.doFilter` |
| ASYNC dispatch riêng sau REQUEST | Thường đã remove | **Có** | `doFilterInternal(ASYNC)` |
| ERROR dispatch riêng sau REQUEST | Thường đã remove | **Có** | `doFilterInternal(ERROR)` |
| ERROR lồng khi REQUEST marker còn | Có | **Có** | `doFilterNestedErrorDispatch(ERROR)` |
| FORWARD/INCLUDE lồng khi marker còn | Có | Không có flag async/error áp dụng | Thường chỉ `chain.doFilter` |

Không suy đoán số lần chạy tuyệt đối chỉ từ bảng: filter registration, container, async cycles và cách MVC trả lỗi quyết định các dispatch có thật. Có nhiều lượt ASYNC trong một request nếu async lifecycle tiếp tục; mỗi lượt phải được xét theo semantics của container và marker.

## 6. Hai lớp quyết định: FilterRegistrationBean và shouldNotFilter*

Một nhầm lẫn thường gặp là nghĩ override `shouldNotFilterAsyncDispatch() = false` **tự đăng ký** filter với servlet container. Thực tế phải xét đồng thời:

1. **Registration layer**: filter được map vào URL patterns và `DispatcherType.REQUEST/ASYNC/ERROR/...` nào; có cho phép async không?
2. **OncePerRequestFilter layer**: nếu filter đã được gọi, `shouldNotFilterAsyncDispatch()`, `shouldNotFilterErrorDispatch()` và `shouldNotFilter(request)` có bỏ qua logic hay không?

Ví dụ đăng ký tường minh trong Spring Boot 3:

```java
@Bean
FilterRegistrationBean<DispatchTraceFilter> traceRegistration() {
    FilterRegistrationBean<DispatchTraceFilter> reg =
            new FilterRegistrationBean<>(new DispatchTraceFilter());

    reg.setName("dispatchTraceFilter");
    reg.setUrlPatterns(List.of("/*"));
    reg.setDispatcherTypes(EnumSet.of(
            DispatcherType.REQUEST,
            DispatcherType.ASYNC,
            DispatcherType.ERROR));
    reg.setAsyncSupported(true);
    reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    return reg;
}
```

*Đoạn minh họa của project đầy đủ ở mục 9; cần import `FilterRegistrationBean`, `DispatcherType`, `EnumSet`, `List`, `Ordered` và `@Bean`.*

Nếu không cần async/error, chỉ map REQUEST để giảm side effects và overhead. Nếu cần, đặt mapping và flags nhất quán rồi kiểm thử trên Servlet container sẽ triển khai thực tế. `setAsyncSupported(true)` không có nghĩa mọi handler tự chạy async; nó chỉ cho phép request chuyển sang async khi các phần còn lại hỗ trợ.

**Tránh đăng ký filter hai lần**: một class gắn `@Component` đồng thời lại tự tạo `FilterRegistrationBean<>(filter)` có thể dẫn đến mapping trùng hoặc hành vi khó đoán tùy auto registration. Chọn một chiến lược đăng ký rõ ràng. Demo tạo `new DispatchTraceFilter()` chỉ bên trong bean registration, **không gắn @Component cho filter**.

### 6.1. shouldNotFilter(HttpServletRequest)

Override khi một số URL hoặc yêu cầu không cần filter:

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/assets/");
}
```

Nếu ứng dụng chạy với context path, kiểm tra cách tạo path. Không dựa vào `getRequestURI()` và prefix một cách thiếu kiểm thử khi mapping có forwarded paths, percent-encoding hoặc context path. Với bảo mật, ưu tiên framework matchers đúng semantics; không tự tạo lỗ hổng vì skip endpoint nhạy cảm.

**Thứ tự thực thi:** `shouldNotFilter(request)` được xét khi filter đã nhận request. Nó không thay thế Servlet URL pattern mapping hay Spring Security authorization.

## 7. Dùng filter cho từng mục đích mà không bị lặp sai

### 7.1. Correlation/trace ID

Một request có thể đi qua REQUEST, ASYNC, ERROR. Tạo ID **một lần** và lưu trong request attribute:

```java
static final String TRACE_KEY = "example.traceId";

String trace = (String) request.getAttribute(TRACE_KEY);
if (trace == null) {
    trace = UUID.randomUUID().toString();
    request.setAttribute(TRACE_KEY, trace);
}

try {
    MDC.put("traceId", trace);
    filterChain.doFilter(request, response);
} finally {
    MDC.remove("traceId");
}
```

*Đây là đoạn minh họa bên trong một filter, cần import UUID và MDC/logging API. Nó không bao trùm executor thread của Callable; phần truyền context cho worker cần cấu hình riêng.*

Với nhiều dispatch, request attribute lưu trace ID xuyên qua các lượt, còn MDC là **per-thread** và phải set/remove khi vào/ra từng thread. Filter tham gia ASYNC redispatch chỉ gắn MDC cho chính thread redispatch. Dùng executor decoration, task wrapper hoặc Spring context-propagation phù hợp nếu muốn trace trong thread thực thi `Callable`.

### 7.2. Đo thời gian toàn request

Không đo “tổng request time” bằng một biến local bắt đầu lại trong mỗi `doFilterInternal`:

```java
long start = System.nanoTime();
filterChain.doFilter(request, response);
long elapsed = System.nanoTime() - start;
```

Với async, elapsed trên lượt REQUEST có thể chỉ phản ánh giai đoạn khởi động async, không phải thời gian tới khi response xong.

Một thiết kế tốt hơn:

1. Tạo start timestamp **một lần** trong request attribute.
2. Chọn một điểm “request completed” rõ ràng: listener `AsyncListener#onComplete`, callback async MVC hoặc giai đoạn cuối nếu lifecycle đã được kiểm chứng.
3. Dùng marker khác để đảm bảo metric summary được phát đúng một lần, ngay cả ERROR dispatch.
4. Dùng monotonic clock (`System.nanoTime()`) để tính duration; dùng wall clock nếu cần timestamp hiển thị.
5. Tách metric **dispatcher count** (có thể > 1) khỏi metric **HTTP request count** (mục tiêu 1).

### 7.3. Security/filter chain

Spring Security cũng dùng Servlet Filters và nhiều implementation có cơ chế kiểu once-per-request, async/error handling riêng. Không áp chính sách “bỏ qua ASYNC/ERROR” một cách ngẫu nhiên vì có thể ảnh hưởng tới context, response hoặc error pages. Trình tự filter và dispatcher mapping thuộc thiết kế security.

Không nên tự làm authentication bằng kiểm tra token đơn giản trong filter demo rồi coi như tương đương Spring Security. Ưu tiên cấu hình Spring Security đầy đủ và kiểm thử cả trạng thái anonymous/authenticated, ASYNC và ERROR khi có yêu cầu.

### 7.4. Request/response wrappers

Một filter có thể bọc request/response để ghi payload hoặc điều chỉnh header. Các rủi ro:

- Đọc request body trong filter trước controller mà không hỗ trợ cached/re-readable body khiến controller không đọc được nữa.
- Bọc response rồi quên copy buffer ra response gốc.
- Với ERROR nested dispatch, container có thể thay wrapper về bản gốc. Override `doFilterNestedErrorDispatch` để bọc lại khi thật sự cần.
- Async cần wrapper có lifecycle phù hợp; không đọc response đã commit.
- Logging body có thể lộ mật khẩu, token, thông tin cá nhân; giới hạn kích thước và mask dữ liệu nhạy cảm.

Nếu dùng `ContentCachingRequestWrapper` hoặc `ContentCachingResponseWrapper`, hãy đọc Javadoc: request wrapper không tự đọc body trước nếu downstream chưa đọc; response wrapper cần thao tác copy body phù hợp. Đừng giả định chỉ việc tạo wrapper là có thể đọc lại response ở mọi dispatch.

## 8. Ví dụ thông thường vs async vs error

### 8.1. REQUEST đồng bộ

```text
Client GET /sync
  → REQUEST filter doFilterInternal: count=1
  → Controller "sync"
  → Filter complete
  → HTTP 200
```

### 8.2. REQUEST bắt đầu async và ASYNC dispatch

```text
Client GET /async
  → REQUEST filter doFilterInternal: count=1
  → Callable trả về / MVC bắt đầu async
  → filter REQUEST kết thúc, marker bị remove
  → Servlet ASYNC dispatch
  → ASYNC filter doFilterInternal: count=2
  → MVC hoàn tất response
  → HTTP 200
```

Mô hình trên chỉ xảy ra khi filter được map với ASYNC và `shouldNotFilterAsyncDispatch() == false`. Nếu dùng mặc định skip async, số lần `doFilterInternal` trong mô hình sẽ chỉ là 1.

### 8.3. Exception được MVC xử lý khác ERROR dispatch

Một controller có:

```java
@GetMapping("/problem")
String fail() {
    throw new IllegalArgumentException("invalid");
}

@ExceptionHandler(IllegalArgumentException.class)
ResponseEntity<String> handle(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body("bad");
}
```

*Đoạn minh họa, cần đặt vào controller/advice có imports.*

Nếu exception resolver của Spring MVC xử lý và ghi response 400, Servlet container **không nhất thiết thực hiện ERROR dispatch**. Vì vậy `shouldNotFilterErrorDispatch() == false` không có nghĩa filter nhận thêm lượt xử lý lỗi cho mọi exception. Khi cần ghi metric lỗi, có thể dùng exception handling hoặc quan sát HTTP status/final outcome theo điểm phù hợp.

### 8.4. ERROR dispatch riêng

```text
REQUEST filter bắt đầu
  → Servlet/application báo lỗi
  → REQUEST filter kết thúc, marker xóa
ERROR dispatch do container khởi tạo
  → ERROR filter nhận lượt mới, nếu đã map và không skip
  → endpoint/error page render
```

### 8.5. Nested ERROR dispatch

```text
REQUEST filter đang active; marker TRUE
  → downstream sendError
  → container nested ERROR (trong cùng chain/thread)
  → filter được gọi tiếp với dispatcherType=ERROR
  → marker TRUE → doFilterNestedErrorDispatch
  → không tự tăng số lần doFilterInternal
```

**Không viết unit test mà chỉ gọi REQUEST rồi gọi ERROR sau khi REQUEST hoàn tất để khẳng định đã kiểm thử nested behavior.** Hai tình huống khác nhau và có nhánh xử lý khác nhau.

## 9. Ứng dụng Spring Boot 3 + Java 17 chạy được

Dùng Spring Boot **3.3.13**, Java 17+, Maven. Demo minh họa filter ghi số lần logic chạy trên **cùng request object**, endpoint sync/async và error handler đơn giản. Test Servlet mock kiểm tra các nhánh ASYNC/ERROR đáng tin hơn việc kỳ vọng MockMvc tự dựng một error-page dispatch của container.

Cấu trúc:

```text
once-filter-dispatch-demo/
├── pom.xml
└── src/
    ├── main/java/example/
    │   ├── DispatchDemoApplication.java
    │   ├── DispatchTraceFilter.java
    │   └── DispatchController.java
    └── test/java/example/
        ├── DispatchTraceFilterTest.java
        └── DispatchControllerTest.java
```

### 9.1. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>once-filter-dispatch-demo</artifactId>
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

### 9.2. DispatchTraceFilter.java

```java
package example;

import java.io.IOException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

public class DispatchTraceFilter extends OncePerRequestFilter {

    public static final String INTERNAL_COUNT =
            "example.trace.internalCount";

    public static final String NESTED_COUNT =
            "example.trace.nestedErrorCount";

    public static final String FIRST_DISPATCH =
            "example.trace.firstDispatcherType";

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false; // filter tham gia lượt ASYNC nếu container có map
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false; // filter tham gia lượt ERROR nếu container có map
    }

    private static int next(HttpServletRequest request, String name) {
        Integer current = (Integer) request.getAttribute(name);
        int updated = current == null ? 1 : current + 1;
        request.setAttribute(name, updated);
        return updated;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        int count = next(request, INTERNAL_COUNT);

        if (request.getAttribute(FIRST_DISPATCH) == null) {
            request.setAttribute(
                    FIRST_DISPATCH, request.getDispatcherType().name());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Khi REQUEST đã async-started, response thường chưa hoàn tất.
            // Không set một header "đã hoàn thành" quá sớm.
            if (!isAsyncStarted(request) && !response.isCommitted()) {
                response.setHeader("X-Trace-Dispatches",
                        Integer.toString(count));
            }
        }
    }

    @Override
    protected void doFilterNestedErrorDispatch(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        next(request, NESTED_COUNT);
        filterChain.doFilter(request, response);
    }
}
```

Đây là **instrumentation để học**, không phải metrics production:

- Counter lưu trên request attribute nên được dùng lại qua dispatch của **cùng request**.
- `doFilterInternal` tăng `INTERNAL_COUNT` từng lượt mà filter thực sự xử lý.
- `doFilterNestedErrorDispatch` tăng counter **riêng**; nested error không tự làm tăng internalCount.
- `response.isCommitted()` giúp tránh sửa header khi HTTP response đã commit; demo chỉ quan sát được header trong tình huống phù hợp.
- Nếu ERROR dispatch riêng bắt đầu trên cùng request sau khi response bị commit, set header có thể không còn khả thi.
- Counter không phải số HTTP requests. Một HTTP request có thể có internalCount = 2 khi có ASYNC redispatch.

### 9.3. DispatchDemoApplication.java

```java
package example;

import java.util.EnumSet;
import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@SpringBootApplication
public class DispatchDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchDemoApplication.class, args);
    }

    @Bean
    FilterRegistrationBean<DispatchTraceFilter> traceFilterRegistration() {
        FilterRegistrationBean<DispatchTraceFilter> registration =
                new FilterRegistrationBean<>(new DispatchTraceFilter());

        registration.setName("dispatchTraceFilter");
        registration.setUrlPatterns(List.of("/*"));
        registration.setDispatcherTypes(EnumSet.of(
                DispatcherType.REQUEST,
                DispatcherType.ASYNC,
                DispatcherType.ERROR));
        registration.setAsyncSupported(true);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);

        return registration;
    }
}
```

Class `DispatchTraceFilter` **không** gắn `@Component`. Chỉ `FilterRegistrationBean` đăng ký nó với Servlet container. Kể cả khi Spring Boot tự đăng ký các Servlet filters khác, cấu hình này chỉ kiểm soát dispatch types của chính filter demo.

### 9.4. DispatchController.java

```java
package example;

import java.util.concurrent.Callable;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class DispatchController {

    @GetMapping("/sync")
    public ResponseEntity<String> sync(HttpServletRequest request) {
        Integer n = (Integer) request.getAttribute(
                DispatchTraceFilter.INTERNAL_COUNT);
        return ResponseEntity.ok("sync-" + n);
    }

    @GetMapping("/async")
    public Callable<String> async() {
        return () -> "async-ok";
    }

    @GetMapping("/handled-error")
    public ResponseEntity<String> handledError() {
        // Đây là lỗi do controller tự trả trực tiếp, không yêu cầu
        // Servlet container chạy ERROR dispatch.
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("bad");
    }

    @GetMapping("/unhandled-error")
    public String unhandledError() {
        throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "demo failure");
    }
}
```

`/sync` giúp quan sát request attribute: controller thấy counter đã được filter tăng lên 1 trước khi method chạy. `/async` dùng MVC async support với `Callable<String>`; kiểu return ở `@RestController` sẽ được viết như body khi hoàn tất. `/handled-error` trả 400 theo business logic mà không mặc nhiên tạo ERROR dispatch. `/unhandled-error` minh họa lỗi server, nhưng **không hứa rằng mọi container sẽ dispatch ERROR theo một chuỗi chính xác giống nhau**; điều này phải được integration test trên container thực.

### 9.5. Lệnh chạy và kết quả

Từ thư mục chứa `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

Dùng terminal khác:

```bash
curl -i http://localhost:8080/sync
curl -i http://localhost:8080/async
curl -i http://localhost:8080/handled-error
```

Kết quả cơ bản dự kiến:

- `GET /sync` → HTTP 200; body `sync-1`; header `X-Trace-Dispatches: 1` khi response chưa commit lúc filter kết thúc.
- `GET /async` → HTTP 200; body `async-ok`. Nếu container thực sự thực hiện ASYNC redispatch qua filter và response còn sửa header được, `X-Trace-Dispatches` có thể phản ánh 2 lượt. Điều kiện/điểm commit cần kiểm chứng bằng log hoặc integration test; đừng coi số header này là giá trị contract bất biến.
- `GET /handled-error` → HTTP 400 và body `bad`; status 400 **không tự chứng minh có ERROR dispatch**.

Thứ tự headers và các headers khác phụ thuộc Servlet container; không coi cURL output gồm toàn bộ headers là chuỗi stdout cố định.

## 10. Unit test filter theo từng dispatcher type

Để kiểm tra logic filter độc lập với container, dùng `MockHttpServletRequest` và `MockHttpServletResponse` từ spring-test. Những test này **mô phỏng dispatcherType**, không tuyên bố tái hiện đầy đủ cách container khởi tạo async/error dispatch.

File `src/test/java/example/DispatchTraceFilterTest.java`:

```java
package example;

import java.io.IOException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DispatchTraceFilterTest {

    @Test
    void firstRequestDispatchExecutesInternalOnce() throws Exception {
        DispatchTraceFilter filter = new DispatchTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain end = (req, res) -> { };
        filter.doFilter(request, response, end);

        assertEquals(1, request.getAttribute(
                DispatchTraceFilter.INTERNAL_COUNT));
        assertEquals("REQUEST", request.getAttribute(
                DispatchTraceFilter.FIRST_DISPATCH));
        assertNull(request.getAttribute(
                filterAlreadyFilteredName(filter)));
    }

    @Test
    void asyncRedispatchCanRunInternalAgain() throws Exception {
        DispatchTraceFilter filter = new DispatchTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain end = (req, res) -> { };

        // Lượt REQUEST ban đầu đã kết thúc, marker của base class
        // đã bị remove trong finally.
        filter.doFilter(request, response, end);

        request.setDispatcherType(DispatcherType.ASYNC);
        filter.doFilter(request, response, end);

        assertEquals(2, request.getAttribute(
                DispatchTraceFilter.INTERNAL_COUNT));
    }

    @Test
    void separateErrorDispatchCanRunInternalAgain() throws Exception {
        DispatchTraceFilter filter = new DispatchTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain end = (req, res) -> { };

        filter.doFilter(request, response, end);
        request.setDispatcherType(DispatcherType.ERROR);
        filter.doFilter(request, response, end);

        assertEquals(2, request.getAttribute(
                DispatchTraceFilter.INTERNAL_COUNT));
        assertNull(request.getAttribute(
                DispatchTraceFilter.NESTED_COUNT));
    }

    @Test
    void nestedErrorUsesDedicatedHookNotInternal() throws Exception {
        DispatchTraceFilter filter = new DispatchTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain end = (req, res) -> { };
        FilterChain requestChain = (req, res) -> {
            // Mô phỏng ERROR dispatch lồng ngay trong REQUEST chain
            // khi already-filtered marker vẫn còn.
            request.setDispatcherType(DispatcherType.ERROR);
            filter.doFilter(request, response, end);
        };

        filter.doFilter(request, response, requestChain);

        assertEquals(1, request.getAttribute(
                DispatchTraceFilter.INTERNAL_COUNT));
        assertEquals(1, request.getAttribute(
                DispatchTraceFilter.NESTED_COUNT));
    }

    @Test
    void baseDefaultsSkipAsyncAndError() throws Exception {
        class DefaultFilter extends OncePerRequestFilter {
            int calls;

            @Override
            protected void doFilterInternal(
                    jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response,
                    FilterChain chain)
                    throws ServletException, IOException {
                calls++;
                chain.doFilter(request, response);
            }
        }

        DefaultFilter filter = new DefaultFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain end = (req, res) -> { };

        filter.doFilter(request, response, end);
        request.setDispatcherType(DispatcherType.ASYNC);
        filter.doFilter(request, response, end);
        request.setDispatcherType(DispatcherType.ERROR);
        filter.doFilter(request, response, end);

        assertEquals(1, filter.calls);
    }

    private static String filterAlreadyFilteredName(
            DispatchTraceFilter filter) {
        // Trong unit test chưa có tên filter đầy đủ do container set.
        // Base class fallback sang class name + ALREADY_FILTERED_SUFFIX.
        return filter.getClass().getName()
                + OncePerRequestFilter.ALREADY_FILTERED_SUFFIX;
    }
}
```

Chạy `mvn test`. Test `nestedErrorUsesDedicatedHookNotInternal` là điểm quan trọng: nó bắt lượt ERROR **khi REQUEST vẫn đang ở trong chain**. Trong khi đó, test `separateErrorDispatchCanRunInternalAgain` gọi ERROR **sau khi REQUEST filter đã thoát**, vì marker đã bị remove.

Test `baseDefaultsSkipAsyncAndError` cho thấy mặc định của base class. `DispatcherType` trong mocks được set thủ công, vì unit test không chứng minh filter đã được Servlet container map đúng types. Phần mapping sẽ được kiểm chứng riêng trên ứng dụng chạy thực.

### 10.1. Kiểm thử Spring MVC endpoint async

File `src/test/java/example/DispatchControllerTest.java`:

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DispatchControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void synchronousControllerReturnsBody() throws Exception {
        mvc.perform(get("/sync"))
            .andExpect(status().isOk())
            .andExpect(content().string("sync-1"));
    }

    @Test
    void asyncControllerRequiresAsyncDispatchInMockMvc() throws Exception {
        MvcResult pending = mvc.perform(get("/async"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(pending))
            .andExpect(status().isOk())
            .andExpect(content().string("async-ok"));
    }

    @Test
    void handledErrorDoesNotRequireServletErrorDispatch()
            throws Exception {
        mvc.perform(get("/handled-error"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("bad"));
    }
}
```

Ở MockMvc, phương thức `asyncDispatch(pending)` mô phỏng bước MVC redispatch để lấy kết quả `Callable`; **không dùng kết quả test này để khẳng định mọi cấu hình Servlet container/FilterRegistrationBean sẽ gọi filter đúng hai lần**. Để kiểm chứng DispatcherType.ASYNC/ERROR mapping trong deploy thật, dùng bài kiểm thử HTTP tới server được khởi động trên random port hoặc quan sát log trong Tomcat/Jetty tương ứng.

### 10.2. Khi nào cần @SpringBootTest(webEnvironment = RANDOM_PORT)?

Các unit tests giả lập được trạng thái của `OncePerRequestFilter`; `MockMvc` kiểm tra MVC async pipeline; nhưng **chỉ embedded Servlet container thực** mới cho bạn độ tin cậy cao về:

- Filter registration có đưa vào REQUEST/ASYNC/ERROR không.
- Container có chuyển `sendError` sang ERROR dispatch riêng/lồng không.
- Request/response wrappers có bị thay lại trong nested ERROR không.
- Header có còn ghi được ở điểm filter hoàn tất hay response đã commit.
- Cách Spring Security/Servlet filters khác ảnh hưởng filter chain.

Với production, hãy thêm integration test phù hợp môi trường Tomcat/Jetty/Undertow đang dùng. Không viết assertion rằng mọi container sẽ có thứ tự lỗi giống nhau; số ERROR dispatch và thời điểm lỗi có thể khác tùy cách exception được xử lý.

## 11. Checklist cấu hình đúng

Trước khi triển khai một OncePerRequestFilter, nên trả lời:

1. **Mục tiêu của filter là gì?** Tracing/logging, security, header, wrapping, metrics hay kiểm tra request?
2. Có cần tham gia `ASYNC` và `ERROR` không, hay chỉ `REQUEST`?
3. FilterRegistrationBean hoặc `web.xml` đã map đúng `DispatcherType` chưa?
4. Nếu muốn async, filter/mapping có `asyncSupported` và container hỗ trợ async không?
5. `shouldNotFilterAsyncDispatch` và `shouldNotFilterErrorDispatch` đã khớp mục tiêu chưa?
6. Nếu skip theo URL, `shouldNotFilter` có bỏ nhầm endpoint quan trọng không?
7. Logic có dùng request attribute cho thông tin xuyên dispatch và MDC/thread-local đúng thread không?
8. Nếu error nested, cần override `doFilterNestedErrorDispatch` để bọc request/response lại không?
9. Khi async started, có vô tình set header, ghi response, tính duration hoặc close resource quá sớm không?
10. Có tránh side effect nghiệp vụ phát lặp khi REQUEST + ASYNC + ERROR đi qua không?
11. Test đã phân biệt separate ERROR và nested ERROR chưa?
12. Đã kiểm tra cả MockMvc/Servlet mocks lẫn embedded container với dispatcher mapping thật chưa?

## 12. Những lỗi phổ biến

| Hiểu nhầm/lỗi | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| “OncePerRequestFilter chỉ chạy đúng 1 lần cho mọi HTTP request” | Sai khi bật async/error redispatch | Hiểu guarantee theo dispatch/thread |
| Override `shouldNotFilterAsyncDispatch=false` nhưng không map ASYNC | Không bao giờ thấy ASYNC tại filter | Cấu hình dispatcher types **và** flag |
| Override `shouldNotFilterErrorDispatch=false` nhưng không map ERROR | Không nhận error dispatch | Map ERROR ở Servlet registration |
| Quên `setAsyncSupported(true)` khi filter nằm trong async chain | Async có thể bị từ chối hoặc cấu hình không đầy đủ | Đăng ký async-supported hợp lệ |
| Tăng metric HTTP request ở mọi `doFilterInternal` | Một HTTP request bị đếm nhiều lần | Phân biệt dispatch counter vs request counter |
| Đo duration ngay khi REQUEST chain return | Bỏ mất giai đoạn bất đồng bộ | Dùng completion hook/lifecycle đúng |
| Đặt MDC mà không `remove()` trong finally | Trộn trace giữa các request trên thread pool | Dọn thread-local trong finally |
| Nghĩ tham gia ASYNC redispatch là MDC sẽ có trong Callable thread | Log Callable thiếu trace | Propagate context vào executor |
| Chỉ nhìn 500 và nghĩ đã có ERROR dispatch | Kết luận sai về behavior | Phân biệt MVC exception handler và container error |
| Unit test gọi ERROR sau REQUEST mà gọi là nested ERROR | Không test nhánh nested | Trigger nested trước khi outer filter return |
| Wrapper mất tác dụng ở nested ERROR | Container có thể đưa wrapper về bản gốc | Override nested error hook và re-wrap khi cần |
| Gắn filter `@Component` và tạo FilterRegistrationBean thêm | Khó kiểm soát registration/trùng execution | Chọn một cách đăng ký |
| Dùng `@Order` nhưng bỏ dispatcher type mapping | Filter không nhận lượt dispatch mong muốn | Thứ tự và mapping là hai lớp khác nhau |
| Đọc request body sớm không dùng buffering đúng | Controller không còn body | Thiết kế cached request wrapper phù hợp |
| Response đã commit vẫn chỉnh header | Header không xuất hiện hoặc exception | Kiểm tra lifecycle/commit; đặt header đúng thời điểm |
| Bọc response nhưng không copy body ra output thực | Response rỗng | Copy body đúng lúc, xử lý async/error |
| Dùng `ThreadLocal` thay request attribute để giữ dữ liệu xuyên async | Mất dữ liệu qua thread | Request attribute + explicit propagation |
| Tắt filter cho mọi ERROR vì “không quan trọng” | Bỏ qua log/security/error handling mong muốn | Quyết định chính sách theo loại response |
| Tưởng servlet Filter giống MVC interceptor | Hook không được gọi khi không có handler hoặc error dispatch | Chọn layer phù hợp |

## 13. Câu hỏi tự kiểm tra

**1.** Vì sao `OncePerRequestFilter` có thể gọi `doFilterInternal` hai lần trong một HTTP request?  
**Đáp án:** request có thể có REQUEST và ASYNC (hoặc ERROR) dispatch riêng; marker đã remove sau lượt trước; nếu filter được map và không skip, lượt sau có thể gọi lại.

**2.** Mặc định `shouldNotFilterAsyncDispatch()` trả gì?  
**Đáp án:** `true`, tức bỏ qua async dispatch.

**3.** Mặc định `shouldNotFilterErrorDispatch()` trả gì?  
**Đáp án:** `true`, tức bỏ qua error dispatch.

**4.** Nếu return `false` ở cả hai method trên thì filter tự tham gia ASYNC/ERROR chưa?  
**Đáp án:** chưa chắc; Servlet registration còn phải map đúng DispatcherType.

**5.** Ý nghĩa của already-filtered marker?  
**Đáp án:** tránh `doFilterInternal` chạy lồng thêm khi request attribute báo filter đang xử lý một lượt; marker được gỡ trong finally.

**6.** Marker có tồn tại vĩnh viễn tới khi HTTP response xong không?  
**Đáp án:** không; nó bị remove khi lời gọi filter tương ứng kết thúc.

**7.** `isAsyncDispatch()` và `isAsyncStarted()` khác gì?  
**Đáp án:** một cái nhận diện kiểu dispatch hiện tại, một cái cho biết request đang ở async mode nên chưa hoàn tất trên lượt hiện tại.

**8.** ERROR dispatch riêng khác nested ERROR thế nào?  
**Đáp án:** separate ERROR thường sau khi REQUEST filter chain kết thúc và marker đã remove; nested ERROR vào khi marker còn, nên gọi `doFilterNestedErrorDispatch`.

**9.** Khi nào cần override `doFilterNestedErrorDispatch`?  
**Đáp án:** khi cần xử lý tình huống container thay request/response wrappers hoặc công việc đặc thù trong nested ERROR; tránh chạy lại logic tổng thể không cần thiết.

**10.** Khi một `@ExceptionHandler` xử lý lỗi 400, có nhất thiết nhận Servlet ERROR dispatch không?  
**Đáp án:** không, MVC có thể xử lý hoàn toàn mà không có ERROR dispatch.

**11.** MDC có tự xuất hiện trong thread thực thi `Callable` không?  
**Đáp án:** không được bảo đảm; cần context propagation vào executor.

**12.** Nếu đo request latency trong REQUEST filter nhưng controller trả Callable thì sai ở đâu?  
**Đáp án:** filter có thể kết thúc trước khi async xử lý xong; cần lifecycle completion hook.

**13.** `MockMvc` có thể kiểm tra chắc việc Servlet container tái dispatch ERROR không?  
**Đáp án:** không đầy đủ; dùng Servlet mocks cho logic class và integration test với embedded container cho registration/lifecycle.

**14.** Tại sao không đếm HTTP requests bằng số lần doFilterInternal?  
**Đáp án:** một HTTP request có thể có nhiều dispatcher invocations.

**15.** `FilterRegistrationBean#setOrder` có thay thế `setDispatcherTypes` không?  
**Đáp án:** không; một cái quyết định thứ tự filter, một cái quyết định dispatch types.

## 14. Nguồn chính thức

1. [Spring Framework 6.2 — OncePerRequestFilter Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/filter/OncePerRequestFilter.html): base class, marker, flags mặc định, async/error/nested dispatch.
2. [Spring Framework 6.2 — Web Async](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html): mô hình async MVC, Callable, DeferredResult, async dispatch.
3. [Spring Boot 3.3 — Servlet Filters](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html): registration Servlet components/FilterRegistrationBean.
4. [Jakarta Servlet API — DispatcherType](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/dispatchertype): REQUEST, ASYNC, ERROR, FORWARD, INCLUDE.
5. [Jakarta Servlet API — AsyncListener](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/asynclistener): lifecycle callbacks.
6. [Spring Test — MockMvc](https://docs.spring.io/spring-framework/reference/6.2/testing/spring-mvc-test-framework.html): kiểm thử controller, asyncDispatch và request/response.
7. [Spring Security — Servlet Architecture](https://docs.spring.io/spring-security/reference/6.3/servlet/architecture.html): filter chain trong bảo mật Servlet và vấn đề thứ tự/dispatch.

**Tóm lại:** `OncePerRequestFilter` đảm bảo logic filter **không chạy trùng trong cùng lượt request dispatch**, chứ không đảm bảo chỉ một lượt xử lý bất kể ASYNC/ERROR. Muốn tham gia ASYNC/ERROR phải thiết lập **cả dispatcher mapping lẫn cờ skip**; với nested ERROR hãy dùng đúng hook riêng. Thiết kế trace/metrics/cleanup theo **toàn HTTP lifecycle** và phân biệt request count với dispatch count.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="filters-interceptors-004">preHandle, postHandle, afterCompletion và điều kiện gọi lại</a>

<details>
<summary>Xem chi tiết</summary>

# preHandle, postHandle, afterCompletion và điều kiện gọi lại trong Spring MVC

## 1. Mục tiêu và tình huống sử dụng

Một request đi qua Spring MVC có thể được nhiều `HandlerInterceptor` quan sát hoặc can thiệp trước/sau khi controller thực thi. Ba callback thường gặp là:

- `preHandle(request, response, handler)`: trước khi HandlerAdapter gọi handler; trả `true` để cho đi tiếp hoặc `false` để dừng chain.
- `postHandle(request, response, handler, modelAndView)`: sau khi handler thực thi **thành công theo đường xử lý đồng bộ**, trước khi view được render; không phải nơi tin cậy để sửa response JSON đã ghi.
- `afterCompletion(request, response, handler, exception)`: ở giai đoạn hoàn tất dispatch để dọn tài nguyên/ghi telemetry, nhưng **chỉ đối với interceptor có `preHandle` đã hoàn thành và trả `true`**, với ngoại lệ quan trọng của xử lý bất đồng bộ.

Sau bài này, bạn sẽ biết:

1. Vị trí của `HandlerInterceptor` trong `DispatcherServlet` và sự khác biệt với Servlet Filter.
2. Thứ tự callback khi có **nhiều interceptor**, khi trả `false`, controller ném exception, view render lỗi và request bất đồng bộ.
3. Ý nghĩa chính xác của `postHandle`, `ModelAndView` có thể null, `afterCompletion` và tham số `Exception ex`.
4. Khi nào `AsyncHandlerInterceptor.afterConcurrentHandlingStarted` được gọi thay cho hai callback thông thường và khi nào có dispatch lần hai.
5. Viết, đăng ký và kiểm thử hai interceptor bằng **Java 17+, Spring Framework 6.x / Spring Boot 3.x, MockMvc**, cùng các trường hợp response body và error.
6. Tránh sai lầm phổ biến: ngầm dựa vào interceptor để bảo mật, set header quá muộn, dọn `ThreadLocal` không đúng lúc hoặc ghi hai response.

**Phạm vi:** Spring MVC trên Servlet stack, không phải Spring WebFlux. Tên giao diện và ý nghĩa callback áp dụng cho Spring MVC 6.x; với Spring 7/Boot 4, đối chiếu API theo phiên bản đang dùng. Trong ví dụ, hai interceptor được gắn riêng vào đường dẫn `/trace/**`; không mặc định áp dụng lên mọi servlet request.

## 2. Chúng nằm ở đâu trong luồng xử lý?

```text
Client
  |
  v
Servlet container
  |
  v
Servlet Filters (CORS, compression, security, tracing ...)
  |
  v
DispatcherServlet
  |
  +-- HandlerMapping chọn handler
  |      -> HandlerExecutionChain(handler, [Interceptor A, Interceptor B])
  |
  +-- A.preHandle(request, response, handler)
  |      -> true
  +-- B.preHandle(request, response, handler)
  |      -> true
  |
  +-- HandlerAdapter gọi controller/handler
  |
  +-- B.postHandle(...)             \
  +-- A.postHandle(...)              } đường đồng bộ thành công
  |
  +-- render ModelAndView nếu có view
  |
  +-- B.afterCompletion(...)         \
  +-- A.afterCompletion(...)          } cleanup sau dispatch
  |
  v
Servlet Filters sau chain.doFilter -> container -> client
```

**Không đồng nhất Filter với HandlerInterceptor:**

| Vấn đề | Servlet Filter | Spring MVC HandlerInterceptor |
| --- | --- | --- |
| Nằm ở đâu? | Trước/bên ngoài DispatcherServlet theo Servlet filter chain | Sau khi HandlerMapping tìm được handler, bên trong MVC |
| Đối tượng API | `Filter#doFilter`, `FilterChain` | `preHandle`, `postHandle`, `afterCompletion` |
| Có biết handler MVC? | Không mặc định | Nhận `handler`, có thể là `HandlerMethod` |
| Có can thiệp request không được mapping đến controller? | Thường có, tùy URL/dispatcher registration | Chỉ khi request đi tới một HandlerExecutionChain có interceptor |
| Bảo mật sớm cho mọi request? | Spring Security nằm trên filter chain và thích hợp hơn | Không nên làm lớp bảo mật chính |
| Can thiệp sau khi response body JSON được ghi? | Filter có thể bao response bằng wrapper phù hợp, còn phải chú ý commit | `postHandle` thường quá muộn cho `@ResponseBody`/`ResponseEntity` |

Spring Framework khuyến nghị **Spring Security hoặc filter-chain security** hơn là dùng interceptor như rào bảo mật chính, vì mapping của interceptor và security boundary có thể không tương đương. Interceptor phù hợp cho cross-cutting logic **gắn với MVC handler**, như quan sát thời gian, thêm model attributes cho view, kiểm tra điều kiện nghiệp vụ bổ trợ hoặc tracking.

## 3. Ba callback: chữ ký và ý nghĩa

Giao diện `org.springframework.web.servlet.HandlerInterceptor` có các phương thức mặc định:

```java
boolean preHandle(
    HttpServletRequest request,
    HttpServletResponse response,
    Object handler) throws Exception;

void postHandle(
    HttpServletRequest request,
    HttpServletResponse response,
    Object handler,
    ModelAndView modelAndView) throws Exception;

void afterCompletion(
    HttpServletRequest request,
    HttpServletResponse response,
    Object handler,
    Exception ex) throws Exception;
```

Đây là **trích yếu chữ ký API**, cần imports Jakarta Servlet, `ModelAndView` và `HandlerInterceptor` trong source thật. Từ Spring Framework hiện đại, các phương thức có default implementations nên chỉ override cái cần dùng. `preHandle` mặc định trả `true`.

### 3.1. preHandle: quyết định có đi tiếp không

`preHandle` chạy sau HandlerMapping chọn `handler`, trước HandlerAdapter gọi nó. `true` nghĩa là MVC được tiếp tục đến interceptor kế hoặc handler. `false` nghĩa là interceptor đã xử lý/chủ động kết thúc response và Spring **không gọi handler**.

```java
@Override
public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler) throws Exception {
    if (request.getHeader("X-Feature") == null) {
        response.sendError(400, "Missing X-Feature");
        return false;
    }
    return true;
}
```

Đây là snippet trong lớp triển khai `HandlerInterceptor`. Khi trả `false`, bạn phải đảm bảo response được giải quyết phù hợp: đặt status, body/redirect, `sendError` hoặc một cơ chế khác. **Trả `false` mà không set gì** có thể tạo response rỗng với status mặc định ngoài ý muốn.

Điểm tinh tế: `false` không phải Java exception. `@RestControllerAdvice` không tự nhận một `Exception` chỉ vì interceptor trả false. Với `sendError`, container error handling có thể tiếp tục theo cấu hình, khác với viết trực tiếp body.

### 3.2. postHandle: sau handler thành công, trước view render

`postHandle` nhận `ModelAndView`, có thể thêm metadata cho view nếu model/view còn được xử lý:

```java
@Override
public void postHandle(HttpServletRequest request,
        HttpServletResponse response, Object handler,
        ModelAndView mav) {
    if (mav != null) {
        mav.addObject("renderedBy", "spring-mvc");
    }
}
```

**Với `@ResponseBody`/`ResponseEntity`, HTTP response thường đã được ghi và có thể commit trong HandlerAdapter trước `postHandle`.** Vì thế không nên dùng `postHandle` để “bảo đảm” thêm JSON property, đổi status/header hay ghi error body. Với REST JSON output, dùng `ResponseBodyAdvice` ở đúng giai đoạn serialize, controller/`ResponseEntity` để set status/header, hoặc filter/response wrapper khi có yêu cầu đặc thù.

`mav == null` là trường hợp bình thường khi handler trả response body, tự ghi trực tiếp, return null, hoặc một số loại handler khác; không phải tự động là lỗi.

### 3.3. afterCompletion: cleanup sau toàn bộ xử lý

`afterCompletion` thường chạy sau khi view đã được render xong (nếu có). Mục đích tốt gồm đóng các tài nguyên của chính interceptor, đo latency tổng thể, đánh dấu hoàn thành, dọn thread-local state mà interceptor đã tạo và log thành công/thất bại bằng thông tin đã kiểm soát.

```java
@Override
public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex) {

    // Chỉ dọn dữ liệu do interceptor sở hữu.
    // Không cố thay status/body khi response có thể đã commit.
}
```

**Điều kiện chính xác:** chỉ gọi cho interceptor mà `preHandle` **đã hoàn tất thành công và trả `true`** trong chain đó. Interceptor trả `false` không được gọi `afterCompletion` của chính nó; nếu `preHandle` ném trước khi trả true, cũng không được bảo đảm callback của chính nó.

Tham số `ex` không phải “mọi lỗi từng xảy ra”. Exception đã được `HandlerExceptionResolver` xử lý có thể **không xuất hiện** trong `afterCompletion(ex)`. Nếu controller đã trả response 409 một cách bình thường, `ex` có thể null. Đừng dùng `ex == null` để kết luận HTTP status luôn là 2xx; nên xem `response.getStatus()` và tín hiệu ứng dụng.

## 4. Thứ tự khi đăng ký nhiều interceptor

Giả sử đăng ký hai interceptor A rồi B, cùng match URL. Dòng thời gian đồng bộ thành công:

```text
A.pre
B.pre
controller
B.post
A.post
view rendering nếu có
B.after
A.after
```

Quy luật:

- **Trước handler**: `preHandle` theo thứ tự đăng ký A → B.
- **Sau handler**: `postHandle` theo thứ tự đảo B → A.
- **Hoàn tất**: `afterCompletion` cũng theo thứ tự đảo B → A.

Thứ tự này tương tự cấu trúc lồng nhau:

```text
A {
    B {
        handler
    }
}
```

Thứ tự thực tế phụ thuộc vào `HandlerExecutionChain` tạo bởi HandlerMapping, không chỉ danh sách `@Component` trong application context. Spring Boot có thể có interceptors do framework hoặc thư viện tự thêm; khi debug hãy quan sát **interceptor chain thực tế** cho handler.

### 4.1. B trả false

Nếu A trả true, B trả false:

```text
A.pre -> true
B.pre -> false, tự ghi response
A.after
(stop: không chạy controller, B.post, A.post, B.after)
```

Spring gọi `afterCompletion` của các interceptor **đã qua preHandle thành công trước B** theo thứ tự ngược. B không nhận `afterCompletion` của chính nó. Do đó nếu B cấp tài nguyên/đặt ThreadLocal **trước khi quyết định trả false**, B phải tự dọn trong đường `false` hoặc sắp xếp lại thiết kế; không “chờ afterCompletion” để dọn.

### 4.2. A trả false ngay

```text
A.pre -> false
(stop: không chạy B.pre, controller, bất kỳ post/after nào của A/B)
```

A có trách nhiệm gửi response. Vì chưa interceptor nào hoàn thành `preHandle=true`, không có `afterCompletion` từ chain này.

### 4.3. B ném exception trong preHandle

```text
A.pre -> true
B.pre -> throws exception
(thường không có B.after)
A.after có thể chạy trong luồng exception handling của MVC
```

Những interceptor trước đó đã trả true có thể được cleanup khi DispatcherServlet xử lý error; đừng suy rộng rằng callback của B luôn chạy. Nếu B cấp tài nguyên rồi ném trong preHandle, hãy tự đặt cleanup trong `catch/finally` của B hoặc chuyển việc cấp tài nguyên sang một vị trí có cleanup chắc chắn.

### 4.4. B đã trả true, nhưng controller ném exception

```text
A.pre -> true
B.pre -> true
controller -> throws
(no A.post or B.post for failed handler execution)
exception resolvers / @ControllerAdvice
B.after
A.after
```

Thường `postHandle` không được gọi sau khi handler ném exception. `afterCompletion` có thể vẫn chạy vì cả A/B đã trả true. Sau đó nếu MVC advice đã xử lý exception thì `ex` quan sát tại completion có thể null; đừng dựa ex để đo số lỗi nghiệp vụ.

## 5. Bảng tình huống gọi lại: kết luận thực dụng

| Tình huống | preHandle | postHandle | afterCompletion |
| --- | --- | --- | --- |
| Handler đồng bộ thành công, A/B đều true | A → B | B → A | B → A |
| A true, B false | A → B (B chặn) | Không | Chỉ A |
| A false | Chỉ A | Không | Không |
| Handler ném exception, advice xử lý | A → B | Thường không | B → A; `ex` có thể null |
| Handler trả JSON `@ResponseBody` thành công | A → B | B → A, `mav` thường null; body có thể đã ghi | B → A |
| Handler view render thất bại | A → B | Có thể đã chạy trước render | Completion của interceptor đã active; lỗi tùy resolver |
| MVC handler bắt đầu async | A → B lần dispatch đầu | Không ở dispatch đầu | Không ở dispatch đầu; dùng async callback |
| Async hoàn tất và dispatch lại | Thường A → B ở ASYNC dispatch | Khi kết quả được xử lý thích hợp | Khi ASYNC dispatch kết thúc thích hợp |
| Filter từ chối trước DispatcherServlet | Không có chain MVC | Không | Không |
| Không tìm được HandlerExecutionChain áp dụng | Không có interceptor MVC trong chain | Không | Không |

Đừng diễn giải bảng thành luật “mọi 404 đều không chạy interceptor”: trong một số cấu hình, request 404 có thể được một handler khác (chẳng hạn resource handler) match trước khi quyết định không tìm thấy resource, và handler đó có interceptor riêng. Tương tự, exceptions trong mapping trước khi chain được tạo có đường xử lý khác exceptions sau preHandle.

## 6. ModelAndView, view render và REST response

Controller trả view:

```java
@Controller
class PageController {
    @GetMapping("/pages/home")
    String home(Model model) {
        model.addAttribute("title", "Trang chủ");
        return "home";
    }
}
```

Mã trên là **trích đoạn class trong dự án Spring MVC** và chỉ render được khi có template `home` tương ứng. `postHandle` có thể thấy `ModelAndView` và thêm attribute trước khi view render. `afterCompletion` chạy sau khi view được xử lý xong.

Controller REST:

```java
@RestController
class ApiController {
    @GetMapping("/api/health")
    Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
```

Đây là đoạn minh họa, cần imports Java/Spring và project Spring Web. JSON thường được ghi trong HandlerAdapter bởi `HttpMessageConverter`. `postHandle` có thể vẫn chạy, nhưng `ModelAndView` thường null và sửa HTTP body/header lúc đó có thể đã quá muộn.

**Nếu yêu cầu đặt một response header quan trọng**, ưu tiên:

- `ResponseEntity.ok().header("X-...", "...").body(dto)` khi controller sở hữu header.
- `ResponseBodyAdvice` khi cần sửa response body trong quá trình serialize theo policy MVC.
- Servlet Filter khi yêu cầu áp lên cả requests không tới MVC hoặc cần response wrapper ở mức Servlet.
- Không dùng `postHandle` làm biện pháp bảo đảm security header cho mọi trường hợp: view/REST/error và response commit có đường xử lý khác nhau.

## 7. AsyncHandlerInterceptor: khi nào ba callback không đủ?

Khi controller trả `Callable`, `DeferredResult` hoặc kích hoạt xử lý Servlet async do Spring MVC quản lý, **dispatch đầu có thể kết thúc mà kết quả handler chưa sẵn sàng**. Khi ấy DispatcherServlet không gọi `postHandle` và `afterCompletion` trong dispatch đầu như bình thường.

`AsyncHandlerInterceptor` mở rộng `HandlerInterceptor` thêm:

```java
@Override
public void afterConcurrentHandlingStarted(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler) throws Exception {
    // Dọn thread-bound state của dispatch đầu.
}
```

Callback này được gọi **thay cho** `postHandle`/`afterCompletion` trên dispatch đã khởi động async. Nó hữu ích nếu interceptor đã đặt MDC hoặc ThreadLocal trên request thread và phải dọn trước khi trả thread về Servlet container.

Luồng điển hình của một request async (A/B đều true):

```text
REQUEST dispatch:
    A.pre
    B.pre
    controller trả Callable/DeferredResult -> MVC bắt đầu async
    B.afterConcurrentHandlingStarted
    A.afterConcurrentHandlingStarted
    (chưa B.post/A.post, chưa B.after/A.after ở dispatch đầu)

Khi async result hoàn tất:
ASYNC dispatch:
    A.pre
    B.pre
    MVC xử lý giá trị/ngoại lệ async đã có
    B.post
    A.post
    B.after
    A.after
```

Một số chi tiết phụ thuộc adapter, return type, async timeout và thiết lập filter. Với `Callable` ném exception được MVC quản lý, exception có thể được xử lý trên dispatch tiếp theo qua resolvers. **Không đồng nhất một `CompletableFuture` tự spawn ngoài MVC với vòng đời servlet async của controller.**

### 7.1. Timeout hoặc network error có thể không có dispatch hoàn tất

Nếu async request timeout hoặc client disconnect, container có thể không dispatch lại theo con đường bạn trông đợi; khi đó `postHandle`/`afterCompletion` có thể **không được gọi**. Muốn lifecycle callbacks mạnh hơn, Spring cung cấp `WebAsyncManager.registerCallableInterceptor`, `registerDeferredResultInterceptor` và các callbacks của `DeferredResult` như `onTimeout`/`onCompletion`. Dùng đúng cơ chế, kiểm tra version/framework và luôn đảm bảo cleanup thread-specific.

### 7.2. Không dùng ThreadLocal như request-wide storage

Một `ThreadLocal` chỉ gắn với thread hiện tại. Nếu A.pre lưu `System.nanoTime()` vào ThreadLocal trên request thread, đến ASYNC dispatch có thể là thread khác; giá trị không tự xuất hiện. Với thời điểm bắt đầu cần đo qua nhiều dispatch, dùng request attribute hoặc một context được truyền rõ ràng, đồng thời lưu ý request có thể hoàn tất bất thường.

Ví dụ an toàn cho thời điểm:

```java
request.setAttribute("startNanos", System.nanoTime());

// Trong callback cùng request, đọc attribute bằng tên có namespace.
Long started = (Long) request.getAttribute("startNanos");
```

Đây là đoạn minh họa, không phải nguyên chương trình; không dùng tên attribute chung chung trong ứng dụng lớn để tránh va chạm. Nếu dùng MDC, nhớ remove/restore MDC trên từng thread và có callback riêng khi async bắt đầu.

## 8. Ví dụ Java 17+ hoàn chỉnh: hai interceptor A và B

Ví dụ Spring Boot 3.x:

- A và B chỉ được đăng ký cho `/trace/**`.
- Request `/trace/ok`: A.pre, B.pre, controller, B.post, A.post, B.after, A.after.
- Request `/trace/blocked`: B chặn bằng 403, A.after vẫn chạy, B.after không chạy.
- Request `/trace/fail`: controller ném `DemoFailure`, advice trả 409, không gọi postHandle nhưng gọi completion cho cả A/B.
- Request `/trace/async`: trả `Callable<String>`; dispatch đầu gọi `afterConcurrentHandlingStarted`, sau async dispatch chạy callbacks phù hợp.

Dùng Spring Initializr Maven, Java 17+, dependencies `spring-boot-starter-web` và `spring-boot-starter-test`. Dùng package `com.example.demo` cho tất cả lớp. Không cần database.

### 8.1. DemoApplication.java

Tệp `src/main/java/com/example/demo/DemoApplication.java`:

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

### 8.2. TraceEvents.java

Tệp `src/main/java/com/example/demo/TraceEvents.java`:

```java
package com.example.demo;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

public final class TraceEvents {
    private static final String KEY =
            TraceEvents.class.getName() + ".events";

    private TraceEvents() {}

    @SuppressWarnings("unchecked")
    public static List<String> of(HttpServletRequest request) {
        Object value = request.getAttribute(KEY);
        if (value instanceof List<?>) {
            return (List<String>) value;
        }
        List<String> events = new ArrayList<>();
        request.setAttribute(KEY, events);
        return events;
    }

    public static void add(HttpServletRequest request, String event) {
        of(request).add(event);
    }
}
```

`TraceEvents` dùng **request attribute** thay vì singleton global list. Nhờ đó mỗi request có trace độc lập, không trộn dữ liệu người dùng. Chỉ phục vụ demo/test với callbacks trên luồng điều phối; trong production, async concurrency/telemetry có thể cần collection thread-safe và framework tracing chính thức. Không ghi các event này vào response như API contract.

### 8.3. TraceInterceptor.java

Tệp `src/main/java/com/example/demo/TraceInterceptor.java`:

```java
package com.example.demo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

public class TraceInterceptor implements AsyncHandlerInterceptor {
    private final String name;

    public TraceInterceptor(String name) {
        this.name = name;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
            HttpServletResponse response, Object handler)
            throws Exception {

        TraceEvents.add(request, name + ".pre");

        if ("B".equals(name)
                && request.getRequestURI().endsWith("/trace/blocked")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("BLOCKED");
            return false;
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request,
            HttpServletResponse response, Object handler,
            ModelAndView mav) {
        TraceEvents.add(request, name + ".post");
        // REST response body đã có thể ghi: không sửa body/header ở đây.
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
            HttpServletResponse response, Object handler,
            Exception ex) {
        TraceEvents.add(request, name + ".after");
    }

    @Override
    public void afterConcurrentHandlingStarted(
            HttpServletRequest request,
            HttpServletResponse response, Object handler) {
        TraceEvents.add(request, name + ".concurrent");
    }
}
```

B tự chặn route `/trace/blocked`. Vì B trả false, B **không nhận** `afterCompletion`; demo không cấp tài nguyên riêng nên không cần cleanup khác. Nếu interceptor dùng ThreadLocal trong `preHandle`, nhớ xử lý đường false/throw ngay tại interceptor, không phụ thuộc `afterCompletion` sau đó.

### 8.4. InterceptorConfig.java

Tệp `src/main/java/com/example/demo/InterceptorConfig.java`:

```java
package com.example.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterceptorConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TraceInterceptor("A"))
                .addPathPatterns("/trace/**");
        registry.addInterceptor(new TraceInterceptor("B"))
                .addPathPatterns("/trace/**");
    }
}
```

A được đăng ký trước B trong cùng `InterceptorRegistry` và cùng match các route demo. Có thể dùng `excludePathPatterns` cho ngoại lệ. Đừng gắn Interceptor global quá rộng mà không kiểm tra các request static resources/error endpoints hoặc framework khác có thể match.

### 8.5. DemoFailure.java

Tệp `src/main/java/com/example/demo/DemoFailure.java`:

```java
package com.example.demo;

public class DemoFailure extends RuntimeException {
    public DemoFailure() {
        super("Simulated controller failure");
    }
}
```

### 8.6. TraceController.java

Tệp `src/main/java/com/example/demo/TraceController.java`:

```java
package com.example.demo;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.Callable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TraceController {

    @GetMapping("/trace/ok")
    public String ok(HttpServletRequest request) {
        TraceEvents.add(request, "handler");
        return "OK";
    }

    @GetMapping("/trace/blocked")
    public String blocked() {
        return "SHOULD_NOT_RUN";
    }

    @GetMapping("/trace/fail")
    public String fail(HttpServletRequest request) {
        TraceEvents.add(request, "handler");
        throw new DemoFailure();
    }

    @GetMapping("/trace/async")
    public Callable<String> async() {
        return () -> "ASYNC";
    }
}
```

Controller chỉ xử lý những request B cho đi tiếp. `blocked()` cố ý tồn tại để chứng minh handler **được mapping nhưng bị preHandle chặn trước khi gọi**; đây không phải lỗi 404.

### 8.7. TraceAdvice.java

Tệp `src/main/java/com/example/demo/TraceAdvice.java`:

```java
package com.example.demo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TraceAdvice {

    @ExceptionHandler(DemoFailure.class)
    public ResponseEntity<String> handleDemo(
            DemoFailure ex, HttpServletRequest request) {
        TraceEvents.add(request, "advice");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("HANDLED");
    }
}
```

Lỗi `DemoFailure` được MVC advice xử lý thành 409. `afterCompletion` vẫn chạy cho interceptor đã trả true, nhưng `postHandle` không được gọi trên đường handler ném exception. Trường `ex` của `afterCompletion` **có thể null** nếu exception đã được xử lý; vì thế không viết test kỳ vọng ex bắt buộc là `DemoFailure`.

### 8.8. Chạy chương trình

Trong dự án có Maven Wrapper từ Initializr:

```bash
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Dự án mặc định dùng port 8080 trừ khi cấu hình `server.port` khác. Các file cần Spring MVC và Servlet/Jakarta dependencies; không thể `javac TraceInterceptor.java` độc lập ngoài classpath của Maven project.

## 9. curl: status và body

### 9.1. Luồng thành công

```bash
curl -i http://localhost:8080/trace/ok
```

Dự kiến **200 OK**, body:

```text
OK
```

Mặc dù `TraceEvents` thu thập A.pre → B.pre → handler → B.post → A.post → B.after → A.after, client chỉ thấy body `OK`; trace được giữ trong request attribute để test. Spring không tự thêm event list vào HTTP body.

### 9.2. B chặn

```bash
curl -i http://localhost:8080/trace/blocked
```

Dự kiến **403 Forbidden**, body:

```text
BLOCKED
```

Handler `TraceController.blocked` không chạy; B chủ động viết response rồi trả false. Không tự suy ra rằng hệ thống đã kiểm tra quyền thật: đây chỉ là demo short-circuit, không phải kiến trúc security production.

### 9.3. Controller lỗi

```bash
curl -i http://localhost:8080/trace/fail
```

Dự kiến **409 Conflict**, body:

```text
HANDLED
```

`TraceAdvice` đã map exception nghiệp vụ mô phỏng. `postHandle` không chạy cho handler ném; completion của interceptor đã active vẫn được trigger sau MVC error resolution.

### 9.4. Async

```bash
curl -i http://localhost:8080/trace/async
```

Dự kiến **200 OK**, body:

```text
ASYNC
```

Một request HTTP có thể trải qua hai servlet dispatches bên trong, nên log callback/trace có nhiều lần preHandle. Không suy ra từ một lần `curl` rằng callback chỉ chạy một lần.

## 10. MockMvc: kiểm thử thứ tự chính xác

Tạo `src/test/java/com/example/demo/TraceInterceptorTest.java`, dependency `spring-boot-starter-test`:

```java
package com.example.demo;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TraceInterceptorTest {

    @Autowired
    MockMvc mvc;

    private static List<String> events(MvcResult result) {
        return TraceEvents.of(result.getRequest());
    }

    @Test
    void successfulHandlerHasReversePostAndCompletion() throws Exception {
        MvcResult result = mvc.perform(get("/trace/ok"))
            .andExpect(status().isOk())
            .andExpect(content().string("OK"))
            .andReturn();

        assertEquals(List.of(
            "A.pre", "B.pre", "handler",
            "B.post", "A.post",
            "B.after", "A.after"
        ), events(result));
    }

    @Test
    void falseShortCircuitsOnlyPreviouslyCompletedInterceptors()
            throws Exception {
        MvcResult result = mvc.perform(get("/trace/blocked"))
            .andExpect(status().isForbidden())
            .andExpect(content().string("BLOCKED"))
            .andReturn();

        assertEquals(List.of("A.pre", "B.pre", "A.after"),
                events(result));
    }

    @Test
    void resolvedControllerExceptionDoesNotCallPostHandle()
            throws Exception {
        MvcResult result = mvc.perform(get("/trace/fail"))
            .andExpect(status().isConflict())
            .andExpect(content().string("HANDLED"))
            .andReturn();

        assertEquals(List.of(
            "A.pre", "B.pre", "handler", "advice",
            "B.after", "A.after"
        ), events(result));
    }

    @Test
    void asyncUsesConcurrentCallbackBeforeRedispatch()
            throws Exception {
        MvcResult first = mvc.perform(get("/trace/async"))
            .andExpect(request().asyncStarted())
            .andReturn();

        List<String> before = List.copyOf(events(first));
        assertTrue(before.contains("A.concurrent"));
        assertTrue(before.contains("B.concurrent"));
        assertFalse(before.contains("A.post"));
        assertFalse(before.contains("B.post"));

        MvcResult finalResult = mvc.perform(asyncDispatch(first))
            .andExpect(status().isOk())
            .andExpect(content().string("ASYNC"))
            .andReturn();

        List<String> after = events(finalResult);
        assertTrue(after.contains("A.post"));
        assertTrue(after.contains("B.post"));
        assertTrue(after.contains("A.after"));
        assertTrue(after.contains("B.after"));
    }
}
```

Chạy:

```bash
./mvnw test
```

Trên PowerShell:

```powershell
.\mvnw.cmd test
```

Các test này là **source code mẫu**, không phải tuyên bố đã chạy trong repository cụ thể. Nếu project bật Spring Security, custom interceptors, filters hay exception resolvers khác, cần điều chỉnh setup/expected results. `MockMvc` giả lập servlet request/async MVC, không thay toàn bộ hành vi network/container error page.

### Vì sao trace đặt trên HttpServletRequest?

Nếu sử dụng singleton `List<String>` để ghi event từ nhiều request, các request song song sẽ trộn nhau. Request attribute giữ trace trong phạm vi request và cho MockMvc đọc sau khi dispatch. Trong production, sử dụng metrics/tracing framework có correlation ID và cleanup phù hợp; tránh thêm event list để người dùng truy xuất vô ý.

### Assertion cho controller lỗi nói lên điều gì?

Nó xác nhận:

- Handler đã chạy và ném exception.
- `@RestControllerAdvice` xử lý thành HTTP 409.
- `postHandle` của cả A/B không gọi.
- `afterCompletion` của A/B vẫn gọi vì các `preHandle` đều trả true.

Nếu bỏ `TraceAdvice`, exception có thể thoát qua error resolver/container theo một đường khác, nhưng không tự khiến `postHandle` xuất hiện trở lại.

## 11. Chọn nơi đặt tracing, cleanup và các thông tin response

### 11.1. Metrics/latency

Một interceptor có thể đo thời gian giữa `preHandle` và `afterCompletion` bằng `System.nanoTime()`. Dùng request attribute có namespace:

```java
@Override
public boolean preHandle(HttpServletRequest request,
        HttpServletResponse response, Object handler) {
    request.setAttribute("metrics.startedNanos", System.nanoTime());
    return true;
}

@Override
public void afterCompletion(HttpServletRequest request,
        HttpServletResponse response, Object handler, Exception ex) {
    Long began = (Long) request.getAttribute("metrics.startedNanos");
    if (began != null) {
        long elapsed = System.nanoTime() - began;
        // Gửi elapsed tới metric registry phù hợp.
    }
}
```

Đây là **hai method trích đoạn** trong một interceptor. Với async, `afterCompletion` ở dispatch cuối và request attribute thường tiếp tục tồn tại, nhưng khi timeout/network error có thể không có completion như kỳ vọng; dùng callbacks async riêng. Nếu nhiều interceptor cùng ghi tên attribute, cần key có namespace hoặc dùng đối tượng riêng.

### 11.2. ThreadLocal và MDC

Một interceptor đôi khi `MDC.put("requestId", id)` ở `preHandle` và `MDC.remove` ở `afterCompletion`. Cách này có vấn đề khi:

- `preHandle` trả false → completion của chính interceptor không chạy.
- `preHandle` ném exception trước khi trả true → completion của chính nó không được bảo đảm.
- Async bắt đầu → `afterCompletion` không chạy tại dispatch đầu; phải dọn thread-bound state trong `afterConcurrentHandlingStarted`.
- Luồng xử lý async có thể khác thread ban đầu. Không suy rằng MDC tự truyền qua mọi executor.
- Filter cũng có thể sở hữu MDC; interceptor chỉ nên restore giá trị cũ do mình đã thay, không xóa vô điều kiện context của tầng khác.

Thiết kế an toàn: không mở scope cho đến khi biết được quản lý cleanup; nếu đã mở, phải đóng ở mọi nhánh false/throw và async callback. Với tracing distributed, ưu tiên framework observability dùng propagation chuẩn thay vì tự viết ThreadLocal quá phức tạp.

### 11.3. Response headers

Nếu chỉ cần thêm header trong một `@ResponseBody` handler, set trong controller/`ResponseEntity` hoặc các hooks trước response commit. `postHandle` chạy sau HandlerAdapter nên không chắc có thể sửa header. Spring documentation nhấn mạnh dùng `ResponseBodyAdvice` cho thay đổi body REST ở đúng giai đoạn.

Nếu áp header bảo mật chung như HSTS/CSP, filter hoặc Spring Security header configuration có phạm vi đúng hơn interceptor chỉ match MVC handlers. Nếu muốn set header trước handler, có thể ở preHandle nhưng vẫn phải đảm bảo error branches và non-MVC requests theo policy; interceptor không thay thế filter.

## 12. Exception ở các thời điểm khác nhau

### Handler ném trước khi trả response

`preHandle` đã true → handler ném → `postHandle` không chạy; MVC `HandlerExceptionResolver` cố xử lý; `afterCompletion` thường chạy cho interceptor đã active. Nếu exception được resolver xử lý thì `ex` của completion có thể null. Dùng `response.getStatus()` để quan sát status cuối, không suy theo exception argument duy nhất.

### postHandle tự ném exception

Một interceptor có thể ném ngay ở `postHandle`. MVC có thể đi vào exception handling, và completion vẫn được trigger cho các interceptors đã qua `preHandle=true`. Nhưng response JSON có thể đã commit; exception handler không nhất thiết sửa được body/status. Tránh logic có side effect nguy hiểm trong postHandle.

### View render thất bại

Với template MVC, `postHandle` có thể đã chạy trước render; nếu view render lỗi sau đó, `afterCompletion` có thể được gọi trong luồng cleanup/exception. Không khẳng định `postHandle` chỉ chạy khi toàn bộ response HTML đã thành công: nó chạy sau handler, trước render.

### preHandle false không phải exception

Nếu interceptor trả false mà không ném exception, đó là đường ngắt chủ động. Nó không kích hoạt `@ExceptionHandler` chỉ vì HTTP status được đặt 403. Đừng đồng nhất `setStatus(403)` với `sendError(403)`: cái sau có thể kích hoạt error handling của container khi response chưa committed.

### Filter exception trước DispatcherServlet

Nếu một Filter ném exception trước khi gọi `chain.doFilter`, interceptor MVC chưa chạy; không có `preHandle`/`postHandle`/`afterCompletion` cho request gốc. Container hoặc filter khác quyết định response. `@RestControllerAdvice` cũng không tự xử lý exception ban đầu ngoài vòng đời DispatcherServlet.

## 13. Các bẫy cấu hình và kỹ thuật

1. **Tin rằng `postHandle` luôn chỉnh được JSON:** handler REST đã có thể ghi body trước callback.
2. **Nghĩ `postHandle` luôn nhận ModelAndView:** REST thường null.
3. **Nghĩ interceptor trả false vẫn nhận afterCompletion của chính nó:** sai.
4. **Không tự tạo response khi trả false:** dẫn tới status/body mơ hồ.
5. **Dọn ThreadLocal chỉ trong afterCompletion:** thiếu nhánh false/throw và async start.
6. **Cho rằng `ex != null` là thước đo duy nhất của lỗi:** resolved exceptions có thể cho ex null; status vẫn 4xx/5xx.
7. **Quên thứ tự ngược:** post/after chạy theo thứ tự B → A.
8. **Tự gọi controller sau khi B trả false:** phá short-circuit.
9. **Dùng interceptor làm security chính:** không bao phủ đầy đủ Filter/Servlet/gateway, cần Spring Security.
10. **Gắn interceptors cho `/**` nhưng không kiểm tra handler resource/error:** behavior có thể khó hiểu.
11. **Đồng nhất một HTTP request với một MVC dispatch:** async có thể dispatch lần hai.
12. **Nghĩ async request vẫn chạy post/after ở dispatch đầu:** callback đúng là `afterConcurrentHandlingStarted`.
13. **Không kiểm soát timeout async:** có thể không có dispatch hoàn tất; cần callbacks async chuyên biệt.
14. **Đặt trace trong singleton field:** request song song trộn dữ liệu.
15. **Ghi response cả ở preHandle false và trong controller:** controller không chạy, nhưng code gọi chain lại/hook sai có thể double write.
16. **Không test nhánh error:** happy-path không đủ chứng minh cleanup.
17. **Dùng `@ControllerAdvice` để bắt mọi filter exception:** advice thuộc phạm vi MVC.
18. **Nhầm HTTP status do servlet filter thành exception handler:** một response 403 có thể không chứa Java exception.
19. **Đưa rejected values/headers nhạy cảm vào interceptor log:** nguy cơ lộ PII/token.
20. **Bỏ qua context path trong URL matcher thủ công:** hãy dùng `addPathPatterns` thay vì kiểm tra chuỗi URI tùy tiện khi có thể.
21. **Không xem response committed:** set header/status trong afterCompletion thường quá muộn.
22. **Coi HandlerInterceptor là request lifecycle hook duy nhất:** filter và Servlet async lifecycle cần cơ chế riêng.
23. **Bất biến giả định mọi handler đều là HandlerMethod:** resource handlers cũng có thể match; dùng `instanceof HandlerMethod` nếu cần inspect annotations.
24. **Dùng ThreadLocal cross-thread cho metrics async:** truyền request attribute hoặc context phù hợp.

## 14. Debug callback không chạy như mong đợi

Khi một breakpoint trong `postHandle`/`afterCompletion` không được hit:

1. **Request có vào DispatcherServlet chưa?** Kiểm tra gateway, Servlet Filter, Spring Security, request log.
2. **HandlerMapping có tìm đúng handler không?** Có thể 404 hoặc tài nguyên static được handler khác xử lý.
3. **Interceptor có match pattern không?** Xem `addPathPatterns`/`excludePathPatterns`, context path, matching strategy.
4. **Có interceptor trước nó trả false?** Khi đó interceptor phía sau chưa được gọi.
5. **Chính interceptor đó trả false?** Không mong `afterCompletion` của chính nó.
6. **preHandle có ném exception?** Cleanup của chính interceptor cần nằm ở nhánh lỗi.
7. **Controller có ném exception?** Không kỳ vọng `postHandle` sau handler ném.
8. **Đã bắt đầu MVC async?** Kỳ vọng `afterConcurrentHandlingStarted` ở dispatch đầu, còn post/after trên dispatch cuối nếu có.
9. **Response là @ResponseBody hay view?** `mav` null và response commit thay đổi khả năng sửa dữ liệu.
10. **Exception đã được advice xử lý chưa?** `ex` có thể null dù HTTP status 409/500.
11. **Đã render xong view chưa?** `postHandle` trước rendering; `afterCompletion` sau xử lý.
12. **Có filter/container error handling khác?** Không phải lỗi nào cũng vào MVC interceptor chain.
13. **Có custom HandlerAdapter/HandlerMapping?** Một số ứng dụng có chain khác.
14. **Có virtual thread/async wrapper không?** Không coi callback thread giống nhau; quản lý state an toàn.
15. **Test MockMvc hay HTTP thật?** MockMvc kiểm tra MVC lifecycle, nhưng container async error dispatch/connection reset cần integration test thực.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** Đăng ký A trước B, đồng bộ thành công thì thứ tự gọi?

**Đáp án:** A.pre, B.pre, handler, B.post, A.post, B.after, A.after.

**Câu 2.** A.pre trả true nhưng B.pre trả false thì những callback nào tiếp tục?

**Đáp án:** Spring bỏ qua handler và mọi post; gọi A.after, không gọi B.after. B phải tự tạo response hợp lệ và tự dọn tài nguyên nó sở hữu.

**Câu 3.** Nếu A.pre trả false ngay?

**Đáp án:** Không chạy B hay handler; A.after không chạy vì A chưa trả true.

**Câu 4.** Controller ném exception rồi `@RestControllerAdvice` xử lý?

**Đáp án:** postHandle không chạy theo đường handler ném; afterCompletion của các interceptor đã trả true vẫn thường chạy. `ex` có thể null do exception đã được resolver giải quyết.

**Câu 5.** Vì sao `postHandle` không phù hợp để sửa body JSON?

**Đáp án:** `@ResponseBody`/`ResponseEntity` thường đã được serializer/HandlerAdapter ghi trước postHandle, response có thể đã commit. Dùng `ResponseBodyAdvice` hoặc response contract ở controller.

**Câu 6.** Nếu controller trả `Callable`, callback nào thay cho post/after trên dispatch đầu?

**Đáp án:** `AsyncHandlerInterceptor.afterConcurrentHandlingStarted`.

**Câu 7.** Sau async processing thành công có thể xảy ra gì?

**Đáp án:** Container tạo ASYNC dispatch, MVC lại gọi preHandle, postHandle và afterCompletion trong lifecycle xử lý kết quả. Timeout/network error có thể khiến không có dispatch cuối thông thường.

**Câu 8.** `afterCompletion(ex == null)` có bảo đảm response là 200?

**Đáp án:** Không. Có thể là 403 do short-circuit khác hoặc exception đã xử lý thành 409; phải xem response status và ngữ cảnh.

**Câu 9.** Filter từ chối trước DispatcherServlet có làm chạy preHandle không?

**Đáp án:** Không. Request chưa đến MVC HandlerExecutionChain.

**Câu 10.** Một `ThreadLocal` được set trong preHandle nên dọn ở đâu?

**Đáp án:** Cần quản lý mọi nhánh (false/throw, normal completion, async start), và restore giá trị cũ khi cần; chỉ dựa vào afterCompletion là thiếu.

## 16. Checklist thiết kế

- [ ] Interceptor chỉ match những route phù hợp và có purpose rõ.
- [ ] `preHandle=false` có status/body/redirect được tạo có chủ đích.
- [ ] Mọi interceptor sở hữu tài nguyên dọn đúng cả nhánh false/throw.
- [ ] ModelAndView được kiểm tra null trước khi thêm attributes.
- [ ] Không cố sửa JSON body/header ở postHandle sau khi committed.
- [ ] Không coi ex==null là request thành công tuyệt đối.
- [ ] Không dùng interceptor làm security boundary chính.
- [ ] Đã kiểm tra thứ tự đăng ký và thứ tự đảo của post/after.
- [ ] Đã test request bình thường, block, controller exception và async.
- [ ] Với async, có `afterConcurrentHandlingStarted` và kế hoạch timeout cleanup.
- [ ] State theo request không dùng mutable singleton dùng chung.
- [ ] Tracing/logging không lộ token, cookie, PII.
- [ ] Chú ý Servlet Filter và error dispatch không nằm trọn trong interceptor lifecycle.
- [ ] Kiểm thử behavior trên bản Spring MVC thực tế đang dùng.

## 17. Tài liệu tham khảo

- Spring Framework 6.2 — Interception: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/handlermapping-interceptor.html
- Spring Framework 6.2 — MVC Interceptors config: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/interceptors.html
- Spring Framework 6.2 — HandlerInterceptor Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/HandlerInterceptor.html
- Spring Framework 6.2 — AsyncHandlerInterceptor Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/AsyncHandlerInterceptor.html
- Spring Framework 6.2 — Asynchronous Requests: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html
- Spring Framework 6.2 — ResponseBodyAdvice: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseBodyAdvice.html
- Spring Framework — @ControllerAdvice and error responses: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html
- Jakarta Servlet Specification — request and async lifecycle: https://jakarta.ee/specifications/servlet/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="filters-interceptors-005">Đăng ký interceptor, phạm vi đường dẫn và AsyncHandlerInterceptor</a>

<details>
<summary>Xem chi tiết</summary>

# Đăng ký interceptor, phạm vi đường dẫn và AsyncHandlerInterceptor trong Spring MVC

## 1. Mục tiêu và tình huống sử dụng

Trong Spring MVC, một ứng dụng có thể muốn ghi thời gian xử lý theo controller, gắn trace/correlation metadata, bổ sung thông tin trước khi handler chạy hoặc quan sát đường hoàn tất request. `HandlerInterceptor` là extension point được gắn vào một `HandlerExecutionChain` của Spring MVC. Nó khác `Servlet Filter` vì interceptor được chọn sau khi Spring tìm handler, còn filter nằm trong Servlet filter chain bên ngoài `DispatcherServlet`.

Giả sử ứng dụng có bốn đường dẫn:

~~~text
GET /api/orders/7         => áp dụng interceptor
GET /api/reports/async    => áp dụng interceptor và xử lý async
GET /api/public/ping      => cố tình loại trừ
GET /health               => nằm ngoài phạm vi /api/**
~~~

Bài học giải thích cách đăng ký với `WebMvcConfigurer`, `InterceptorRegistry`, `addPathPatterns`/`excludePathPatterns`, thứ tự và lifecycle các callback, khác biệt khi controller dùng `Callable`/`DeferredResult`, cleanup `ThreadLocal` khi chuyển thread, các giới hạn timeout/network error và kiểm thử chính xác bằng MockMvc.

**Phạm vi:** Spring MVC Servlet stack, Java 17+, Spring Boot 3.x và Spring Framework 6.x với namespace `jakarta.servlet`. Spring WebFlux có lập trình request khác. `@Async` trên service method không tự đồng nghĩa với Servlet async request processing.

## 2. Vị trí interceptor trong HTTP pipeline

`HandlerInterceptor` thuộc `HandlerExecutionChain` của Spring MVC: Servlet Filters đã chạy ở tầng container; `DispatcherServlet` và `HandlerMapping` xác định handler trước khi interceptor được gọi. Vì vậy, interceptor có thể biết `HandlerMethod` đã chọn, nhưng không tự bao phủ những request bị Security/Servlet Filter chặn sớm.

~~~text
Servlet Filter chain
    → DispatcherServlet / HandlerMapping
    → A.preHandle → B.preHandle
    → MVC handler + xử lý return value
    → B.postHandle → A.postHandle   (đường đồng bộ thành công)
    → view rendering nếu có
    → B.afterCompletion → A.afterCompletion
~~~

Ba callback có vai trò khác nhau: `preHandle` trả `true` để đi tiếp hoặc `false` để dừng (phải tự hoàn tất response và cleanup phần việc của chính nó); `postHandle` nhận `ModelAndView` có thể null và ở REST có thể quá muộn để sửa body/header; `afterCompletion` chỉ dành cho interceptor đã hoàn tất `preHandle=true`, và tham số `ex` có thể null dù lỗi đã được MVC resolver xử lý. `handler` không nhất thiết là `HandlerMethod`, ví dụ static resource handler.

Với A rồi B, chiều trước handler là A→B và chiều sau là B→A. Nếu B trả `false`, A có thể nhận `afterCompletion` nhưng B không; handler và `postHandle` không chạy. Nếu handler ném exception, `postHandle` thường không chạy còn cleanup áp dụng cho những interceptor đã active. Đó là kiến thức nền cho **registration/path matching** ở mục 3–4 và **ASYNC redispatch** ở mục 5; ví dụ chính về từng nhánh callback, thứ tự và lỗi nằm ở bài chuyên đề `preHandle`/`postHandle`/`afterCompletion`.

## 3. Đăng ký qua WebMvcConfigurer trong Spring Boot

Cách điển hình:

~~~java
@Configuration(proxyBeanMethods = false)
public class MvcConfiguration implements WebMvcConfigurer {

    private final AuditInterceptor audit;

    public MvcConfiguration(AuditInterceptor audit) {
        this.audit = audit;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(audit)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/public/**")
                .order(0);
    }
}
~~~

Đoạn này là **fragment**, cần `AuditInterceptor` đã định nghĩa và imports tương ứng. Chi tiết:
- `addInterceptor(audit)` đăng ký instance interceptor thuộc chain MVC.
- `addPathPatterns("/api/**")` chọn các route thuộc phạm vi API.
- `excludePathPatterns("/api/public/**")` bỏ interceptor ở phần URL đã công bố.
- `order(0)` xếp thứ tự tương đối với registration khác.
- Không khai báo include patterns thường có nghĩa áp dụng cho các path do MVC handler mapping tương ứng xử lý, chứ không có nghĩa mọi request tới toàn bộ server.

Đặt class config trong package mà `@SpringBootApplication` component scan được. Nên để interceptor là Spring bean khi nó cần inject dependencies, thay vì `new` nhiều instance ở nhiều cấu hình khác nhau.

**Không thêm `@EnableWebMvc` chỉ để đăng ký interceptor.** Trong Spring Boot, annotation này thường làm bạn chủ động sở hữu toàn bộ MVC configuration và có thể khiến nhiều Boot MVC auto-config không còn hiệu lực. `WebMvcConfigurer` cho phép bổ sung cấu hình mà vẫn giữ hành vi Boot mặc định.

### 3.1. Thứ tự registration

~~~java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(first)
            .addPathPatterns("/api/**")
            .order(0);

    registry.addInterceptor(second)
            .addPathPatterns("/api/admin/**")
            .order(10);
}
~~~

Fragment cần bean `first` và `second`. Với `/api/admin/report`, cả hai có thể khớp; preHandle từ first tới second, callback hoàn tất ngược chiều. Số thứ tự nhỏ hơn có mức ưu tiên sớm hơn trong registry. Thứ tự các bộ lọc Servlet không bị thay đổi bởi `order` ở đây.

### 3.2. MappedInterceptor bean

Ngoài Java config, Spring MVC hỗ trợ `MappedInterceptor`: một bean chứa include/exclude patterns và interceptor delegate. Ví dụ:

~~~java
@Bean
MappedInterceptor monitoringMapping(AuditInterceptor delegate) {
    return new MappedInterceptor(
            new String[]{"/api/**"},
            new String[]{"/api/public/**"},
            delegate);
}
~~~

Đây là fragment, cần import `@Bean` và `MappedInterceptor`, không đồng thời copy vào demo mục 8. Tài liệu Spring phân biệt: Java `addInterceptors` thường đăng ký vào các `HandlerMapping` do MVC Java configuration quản lý, trong khi `MappedInterceptor` bean có thể được phát hiện bởi các `HandlerMapping` khác, kể cả integration của framework khác.

**Không đăng ký cùng một interceptor hai lần** qua cả bean `MappedInterceptor` và `registry.addInterceptor` cho cùng scope: callback có thể lặp và metrics có thể đếm sai. Chọn một chiến lược, rồi integration-test trên mapping thực tế của ứng dụng.

## 4. Phạm vi đường dẫn và pattern matching

Với cấu hình:

~~~java
registry.addInterceptor(audit)
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/public/**");
~~~

Các kỳ vọng trong một ứng dụng MVC thông thường:

| Path | Áp dụng? | Giải thích |
| --- | --- | --- |
| `/api/orders/7` | Có | Khớp `/api/**` |
| `/api/reports/async` | Có | Khớp include |
| `/api/public/ping` | Không | Bị exclude |
| `/health` | Không | Ngoài include |
| `/api/deep/nested/path` | Có | Wildcard nhiều path segments |
| `/app/api/orders/7` với context path `/app` | Thường vẫn xét application-relative path `/api/orders/7` | Đừng nhầm context path với route |

`/api/*` thường là một cấp path segment, còn `/api/**` biểu đạt nhiều cấp theo path matcher được cấu hình. Pattern matching được thực hiện trong ngữ cảnh `HandlerMapping`, không nên thay bằng `request.getRequestURI().startsWith(...)` vì context path, URL encoding và servlet mapping.

### 4.1. PathPatternParser và AntPathMatcher

Spring MVC 6 phổ biến sử dụng `PathPatternParser`, khác một số cấu hình cũ dùng `AntPathMatcher`. Khi nâng cấp:
- Không mặc định `/api/items` và `/api/items/` luôn khớp giống nhau; hành vi trailing slash đã thay đổi ở các phiên bản mới.
- Một số wildcard pattern, encoding, matrix parameters và servlet mapping có ngữ nghĩa khác nhau giữa hai hệ matching.
- `**` trong parsed patterns có những giới hạn vị trí nhất định; đừng chuyển các Ant patterns phức tạp sang PathPattern mà không test.
- Nếu chạy dưới context path hoặc reverse proxy, xác minh route mà `HandlerMapping` thực sự xét.

Test path include, exclude và slash variants bằng MockMvc/integration test phù hợp version. Không tự xây logic bảo mật dựa trên so sánh string path thô.

### 4.2. Loại trừ đường dẫn không phải authorization

`excludePathPatterns("/api/public/**")` chỉ bỏ **interceptor đang xét**, không tự tạo chính sách “permit all” hoặc “deny all”. Đặc biệt, Spring chính thức cảnh báo interceptor không phải vị trí lý tưởng để triển khai lớp security chính vì có thể lệch giữa controller path matching và các luật bảo vệ.

Đối với xác thực/phân quyền, dùng Spring Security filter chain/authorization rules. Đối với request metrics hay intercepting controller metadata, interceptor phù hợp hơn. Một request bị security filter từ chối có thể không bao giờ đến interceptor.

### 4.3. Các request không đi qua handler mapping dự kiến

Interceptor có thể không thấy:
- Request bị filter/Security chặn sớm.
- Static resource do Servlet/container hoặc HandlerMapping khác xử lý.
- CORS preflight được MVC xử lý ở nhánh đặc biệt.
- `ERROR`/`FORWARD` dispatch theo cấu hình riêng.
- Actuator endpoints trên management port/context khác.

Nếu cần quan sát mọi request HTTP bất kể có controller hay không, hãy cân nhắc Filter hoặc observability tích hợp ở tầng phù hợp. Đừng suy từ việc interceptor không chạy rằng request chưa hề đến server.

## 5. AsyncHandlerInterceptor khác gì HandlerInterceptor?

`AsyncHandlerInterceptor` kế thừa `HandlerInterceptor` và thêm:

~~~java
void afterConcurrentHandlingStarted(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler) throws Exception;
~~~

Khi controller return `Callable<T>`, `WebAsyncTask<T>` hoặc `DeferredResult<T>` và MVC bắt đầu Servlet async processing, thread Servlet ban đầu được giải phóng trong khi HTTP response vẫn có thể chưa hoàn tất. Vì vậy lifecycle bình thường bị tách thành nhiều lượt dispatch.

### 5.1. Request đồng bộ

~~~text
REQUEST dispatch
  interceptor.preHandle()
  controller runs and returns result
  MVC processes return value / view
  interceptor.postHandle()
  interceptor.afterCompletion()
  request handling completes
~~~

Đây là đường thành công thông thường khi `preHandle` trả true và không có error.

### 5.2. Request bất đồng bộ thành công

~~~text
INITIAL REQUEST DISPATCH — servlet thread:
  preHandle()                             DispatcherType.REQUEST
  controller returns Callable / DeferredResult
  Spring MVC starts async handling
  afterConcurrentHandlingStarted()
  servlet request thread is released

ASYNC COMPUTATION:
  Callable computes result on executor thread,
  or DeferredResult completed by another producer

ASYNC DISPATCH — servlet thread (may be a different thread):
  preHandle()                             DispatcherType.ASYNC
  Spring MVC resumes using concurrent result
  postHandle()                            normal successful completion
  afterCompletion()                       final MVC processing
~~~

**Điểm quan trọng:**
1. Ở lượt ban đầu bắt đầu async, `postHandle` và `afterCompletion` **không gọi ngay**; `afterConcurrentHandlingStarted` được gọi thay thế.
2. Khi kết quả sẵn sàng, container dispatch lại tới `DispatcherServlet`; `preHandle` thường chạy **một lần nữa** trong `ASYNC` dispatch.
3. Controller method **không nhất thiết được gọi lại** trong lượt redispatch; Spring xử lý kết quả đã được worker/event tạo ra.
4. Không mặc định “một HTTP request chỉ gọi preHandle một lần”. Nếu đếm request, tạo trace hoặc kiểm TTL theo số lần preHandle, async redispatch có thể khiến thống kê sai.
5. Sơ đồ không yêu cầu chính xác ba thread vật lý khác nhau; thread pool có thể tái sử dụng threads.

Dùng `request.getDispatcherType()` để phân biệt `REQUEST` và `ASYNC` trong những trường hợp cần. Servlet còn có dispatcher types khác (FORWARD, INCLUDE, ERROR); chúng phụ thuộc container/filter mappings.

### 5.3. Vì sao cần cleanup ở afterConcurrentHandlingStarted?

Một interceptor có thể tạo dữ liệu thread-bound như MDC/logging context/`ThreadLocal`. Khi bắt đầu async, servlet thread đầu chuẩn bị quay về pool, nên **phải dọn dữ liệu mà interceptor đã gắn vào thread đó**. Nếu chỉ dọn tại `afterCompletion`, callback này không chạy trên lượt request ban đầu; thread sẽ có nguy cơ giữ context của client A và rò qua request B.

Ví dụ ý tưởng:

~~~java
@Override
public void afterConcurrentHandlingStarted(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler) {
    MDC.remove("traceId");
}
~~~

Đoạn trên là fragment, chỉ phù hợp nếu chính interceptor **sở hữu và đã set** key `traceId`; cần import `org.slf4j.MDC`. Không xóa ngẫu nhiên context do tracing library khác quản lý. Sau khi đã dọn thread ban đầu, **không có nghĩa context tự xuất hiện trong Callable worker**: cần truyền metadata rõ ràng hoặc dùng Micrometer Context Propagation/`TaskDecorator` và capture/restore thích hợp.

Nếu còn cần state xuyên dispatch, có thể lưu dữ liệu nhỏ trong request attribute. Đừng xóa attribute ngay ở `afterConcurrentHandlingStarted` rồi mong redispatch vẫn đọc được.

### 5.4. Timeout và network errors

Với async requests, Servlet container có thể timeout hoặc bị ngắt kết nối. Một số đường lỗi **không có successful redispatch**, do đó `postHandle`/`afterCompletion` không được đảm bảo chạy như nhánh thành công.

Để bắt các sự kiện này, dùng extension phù hợp:
- `DeferredResult.onTimeout(Runnable)`, `DeferredResult.onCompletion(Runnable)`.
- `WebAsyncTask` callbacks và timeout cho `Callable`.
- `WebAsyncManager.registerCallableInterceptor(...)` hoặc `registerDeferredResultInterceptor(...)` để quan sát sâu hơn.
- Filter/container async lifecycle nếu trách nhiệm phải nằm ở tầng Servlet, với dispatch mapping chính xác.

Không đặt cleanup của tài nguyên quan trọng **duy nhất** trong `afterCompletion` rồi kết luận mọi kết nối/khóa luôn được giải phóng. Với request hoàn tất muộn, callback completion thường chỉ để dọn/quan sát, không phải lúc nào còn được phép tạo HTTP result mới.

### 5.5. Async MVC không đồng nghĩa reactive non-blocking

`Callable` chạy trên executor; JDBC/blocking HTTP calls vẫn block worker thread. `DeferredResult` cho phép event bên ngoài quyết định lúc hoàn tất mà không cần giữ Servlet request thread trong lúc chờ. Spring WebFlux là framework khác, không dùng nguyên cách hook `AsyncHandlerInterceptor` của Servlet MVC.

Production nên cấu hình bounded `AsyncTaskExecutor`, default timeout, queue policy và error handling. Không dùng executor không giới hạn trong ứng dụng có nhiều request blocking chậm chỉ vì muốn tăng concurrency.

## 6. Những lỗi dễ gặp trước khi thiết kế

| Hiện tượng | Nguyên nhân | Hướng giải quyết |
| --- | --- | --- |
| Header/body không đổi khi viết ở `postHandle` | Response body đã được converter ghi | Dùng `ResponseBodyAdvice` nếu thật sự cần đổi body |
| Metrics request tăng gấp đôi ở API async | `preHandle` chạy ở REQUEST rồi ASYNC dispatch | Đếm một logical request, kiểm dispatcher type |
| MDC bị rò sang user khác | Không dọn thread đầu khi async start | `afterConcurrentHandlingStarted` và context ownership rõ |
| Thread async không có trace context | Không có propagation | Capture/restore explicit hoặc Micrometer/TaskDecorator |
| Timeout không chạy cleanup | Chỉ dựa `afterCompletion` | Async timeout/completion hooks |
| Interceptor không có tác dụng ở static resource | Resource đi qua mapping khác | Đặt logic trong Filter nếu cần toàn Servlet |
| Security bị bypass do exclude path | Dùng interceptor như ACL | Spring Security filter chain |
| Callback lặp bất thường sync | Đăng ký cùng bean hai lần | Chọn registry hoặc MappedInterceptor |
| Boot MVC mất converters | Thêm `@EnableWebMvc` tùy tiện | Dùng `WebMvcConfigurer` |
| Cast `handler` ném `ClassCastException` | Handler không phải `HandlerMethod` | Dùng `instanceof` |
| Handler bị skip nhưng response rỗng | preHandle false mà không set response | Gửi status/body rõ ràng |

## 7. Ứng dụng Java 17 + Spring Boot minh họa

Ứng dụng có bốn route để kiểm scope và async lifecycle:
- `GET /api/items/{id}`: sync JSON, có interceptor.
- `GET /api/async/report`: `Callable<Report>`, có interceptor qua hai lượt dispatch bình thường.
- `GET /api/public/ping`: bị exclude, không có interceptor demo.
- `GET /health`: ngoài include, không có interceptor demo.

Code dùng Spring Boot 3.3.5, Spring MVC và MockMvc, **không cần database**. Header `X-Mvc-Interceptor` và `X-Mvc-Pre-Visits` chỉ để quan sát kỹ thuật; không dùng chúng làm token/authorization state.

### 7.1. Cấu trúc thư mục

~~~text
interceptor-demo/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/interceptordemo/
    │   ├── InterceptorDemoApplication.java
    │   └── web/
    │       ├── RequestTraceInterceptor.java
    │       ├── MvcConfiguration.java
    │       └── DemoController.java
    └── test/java/com/example/interceptordemo/web/
        └── DemoControllerTest.java
~~~

### 7.2. Gradle

`settings.gradle`:

~~~groovy
rootProject.name = 'interceptor-demo'
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

Sử dụng Gradle wrapper tương thích Java 17/Spring Boot 3.3. Trên Linux/macOS chạy `./gradlew bootRun`, `./gradlew test`; trên Windows chạy `.\gradlew.bat bootRun`, `.\gradlew.bat test`. Code phụ thuộc Spring nên không chạy `javac` trực tiếp khi thiếu classpath.

### 7.3. Application entry point

`src/main/java/com/example/interceptordemo/InterceptorDemoApplication.java`:

~~~java
package com.example.interceptordemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InterceptorDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(InterceptorDemoApplication.class, args);
    }
}
~~~

### 7.4. RequestTraceInterceptor

`src/main/java/com/example/interceptordemo/web/RequestTraceInterceptor.java`:

~~~java
package com.example.interceptordemo.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class RequestTraceInterceptor implements AsyncHandlerInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(RequestTraceInterceptor.class);
    private static final String STATE_KEY =
            RequestTraceInterceptor.class.getName() + ".state";

    private static final class State {
        private final long startNanos = System.nanoTime();
        private int preVisits;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        State state = (State) request.getAttribute(STATE_KEY);
        if (state == null) {
            state = new State();
            request.setAttribute(STATE_KEY, state);
        }
        state.preVisits++;

        response.setHeader("X-Mvc-Interceptor", "active");
        response.setHeader(
                "X-Mvc-Pre-Visits", Integer.toString(state.preVisits));
        log.debug("pre path={} dispatch={} visits={}",
                request.getRequestURI(), request.getDispatcherType(),
                state.preVisits);
        return true;
    }

    @Override
    public void postHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            ModelAndView modelAndView) {
        log.debug("post path={} dispatch={}",
                request.getRequestURI(), request.getDispatcherType());
    }

    @Override
    public void afterConcurrentHandlingStarted(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        log.debug("async-start path={} dispatch={}",
                request.getRequestURI(), request.getDispatcherType());
        // Nếu tự đặt MDC/ThreadLocal trong preHandle,
        // dọn context thuộc Servlet thread ban đầu ở đây.
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        State state = (State) request.getAttribute(STATE_KEY);
        if (state != null) {
            long elapsedMs =
                    (System.nanoTime() - state.startNanos) / 1_000_000L;
            log.debug("complete status={} dispatch={} elapsedMs={}",
                    response.getStatus(), request.getDispatcherType(),
                    elapsedMs);
        }
    }
}
~~~

Phân tích:
- `State` gắn vào **request attribute**, không lưu trong bean singleton field chia sẻ mọi user.
- `preVisits` tăng ở mỗi lượt preHandle; sync thường là 1, async thành công sau redispatch thường là 2. Không dùng counter đó làm metric tổng số HTTP requests.
- `postHandle` và `afterCompletion` chỉ log, không cố sửa JSON sau khi converter có thể đã ghi.
- `afterConcurrentHandlingStarted` ghi nhận bước bắt đầu async, và là điểm hợp lý để dọn thread-bound state **do interceptor này tạo**.
- Không log cookie, Authorization header, raw request body hoặc session ID. Với production metrics, ưu tiên route template từ MVC thay vì raw URI chứa ID.

### 7.5. MvcConfiguration

`src/main/java/com/example/interceptordemo/web/MvcConfiguration.java`:

~~~java
package com.example.interceptordemo.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class MvcConfiguration implements WebMvcConfigurer {

    private final RequestTraceInterceptor trace;

    public MvcConfiguration(RequestTraceInterceptor trace) {
        this.trace = trace;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(trace)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/public/**")
                .order(0);
    }
}
~~~

Config này chọn cả route đồng bộ và async trong `/api/**`, trừ nhánh public. Không đăng ký interceptor qua MappedInterceptor lần nữa.

### 7.6. Controller

`src/main/java/com/example/interceptordemo/web/DemoController.java`:

~~~java
package com.example.interceptordemo.web;

import java.util.Map;
import java.util.concurrent.Callable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    public record Item(long id, String status) {}
    public record Report(String status) {}

    @GetMapping("/api/items/{id}")
    public Item item(@PathVariable("id") long id) {
        return new Item(id, "ok");
    }

    @GetMapping("/api/async/report")
    public Callable<Report> asyncReport() {
        return () -> new Report("ready");
    }

    @GetMapping("/api/public/ping")
    public String publicPing() {
        return "pong";
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "up");
    }
}
~~~

`asyncReport` trả đúng `Callable<Report>` cho Spring MVC nên kích hoạt Servlet async processing, sau đó kết quả `Report` được serialize ở lượt completion. Không nhầm với việc gọi một `@Async` service method rồi chặn đợi kết quả.

### 7.7. Test với MockMvc

`src/test/java/com/example/interceptordemo/web/DemoControllerTest.java`:

~~~java
package com.example.interceptordemo.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void includedApiRouteRunsInterceptor() throws Exception {
        mvc.perform(get("/api/items/7"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Mvc-Interceptor", "active"))
                .andExpect(header().string("X-Mvc-Pre-Visits", "1"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void excludedPublicRouteHasNoInterceptorHeader() throws Exception {
        mvc.perform(get("/api/public/ping"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Mvc-Interceptor"))
                .andExpect(content().string("pong"));
    }

    @Test
    void routeOutsideIncludeHasNoInterceptorHeader() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Mvc-Interceptor"))
                .andExpect(jsonPath("$.status").value("up"));
    }

    @Test
    void asyncDispatchRunsASecondPreHandle() throws Exception {
        MvcResult first = mvc.perform(get("/api/async/report"))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("X-Mvc-Interceptor", "active"))
                .andReturn();

        mvc.perform(asyncDispatch(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(header().string("X-Mvc-Interceptor", "active"))
                .andExpect(header().string("X-Mvc-Pre-Visits", "2"));
    }
}
~~~

Các test phân biệt:
- Header có ở include route, không có ở exclude và outside route.
- `request().asyncStarted()` xác minh MVC đã bắt đầu async.
- `asyncDispatch(first)` là lượt tiếp tục **cùng logical HTTP request**, không phải gửi một GET mới.
- Hai lượt `preHandle` thể hiện initial REQUEST và ASYNC completion trong đường mẫu. Với error/timeout hoặc một dispatch tùy biến, không coi con số hai là quy luật bất biến.

Chạy `./gradlew test`. MockMvc không thay thế integration tests với Servlet container/load balancer khi cần nghiên cứu thread pools, timeouts và network disconnect.

### 7.8. Quan sát bằng curl

~~~bash
curl -i 'http://localhost:8080/api/items/7'
curl -i 'http://localhost:8080/api/async/report'
curl -i 'http://localhost:8080/api/public/ping'
curl -i 'http://localhost:8080/health'
~~~

Kết quả dự kiến:
- `/api/items/7`: HTTP 200, `{"id":7,"status":"ok"}`, có `X-Mvc-Interceptor: active` và `X-Mvc-Pre-Visits: 1`.
- `/api/async/report`: HTTP 200, `{"status":"ready"}`, thông thường header visits cuối là 2 sau successful async dispatch.
- `/api/public/ping`: HTTP 200, body text `pong`, không có header của interceptor.
- `/health`: HTTP 200, JSON `{"status":"up"}`, không có header của interceptor.

Thread names và elapsedMs trong debug logs không cố định. Endpoint demo không triển khai security và header chỉ phục vụ kiểm tra cơ chế MVC.

## 8. Cấu hình executor và timeout cho production

Spring MVC cho phép mở rộng `WebMvcConfigurer#configureAsyncSupport`. Ví dụ **fragment** (không nằm sẵn trong source app ở mục 7):

~~~java
@Override
public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    configurer.setTaskExecutor(mvcAsyncExecutor);
    configurer.setDefaultTimeout(5_000L);
}
~~~

`mvcAsyncExecutor` là `AsyncTaskExecutor` đã được inject từ một bean khác; cần import `AsyncSupportConfigurer`. Bean thread pool minh họa:

~~~java
@Bean
public ThreadPoolTaskExecutor mvcAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("mvc-async-");
    executor.initialize();
    return executor;
}
~~~

Đây cũng là fragment, cần imports `@Bean` và `ThreadPoolTaskExecutor`. Khi tích hợp vào app thực tế, đảm bảo bean được quản lý một lần, có shutdown/rejection policy phù hợp, executor được inject đúng và các con số được điều chỉnh theo tải/thời gian I/O. Spring MVC default executor cho một số tác vụ async không thích hợp production khi nhiều công việc blocking.

Nếu dùng `DeferredResult`, cũng phải xác định producer/event source hoàn tất response và dọn state khi timeout/cancel.

### 8.1. DeferredResult và callback

~~~java
@GetMapping("/api/deferred")
public DeferredResult<String> deferred() {
    DeferredResult<String> result = new DeferredResult<>(5_000L);
    result.onTimeout(() -> result.setErrorResult(
            new IllegalStateException("Deferred response timed out")));
    result.onCompletion(() -> {
        // Cleanup resource gắn với deferred operation.
    });
    return result;
}
~~~

Fragment không độc lập: cần import `DeferredResult` và một event/worker chủ động gọi `setResult` trước timeout khi có kết quả. Nếu không ai hoàn tất, nó đi vào timeout. `onCompletion` có thể được gọi khi response đã hoàn tất, không nên kỳ vọng có thể ghi thêm body.

## 9. ThreadLocal, MDC và context propagation

Một `ThreadLocal` có vẻ hoạt động với handler sync vì toàn request chạy trên một thread. Với async, cần quan tâm ít nhất ba giai đoạn: Servlet thread ban đầu, worker thread và Servlet thread của redispatch. Chúng **không đảm bảo cùng một thread**.

Nếu interceptor đặt `MDC.put("traceId", ...)` trong `preHandle`:
1. Dọn trên Servlet thread đầu khi bắt đầu async (thường là `afterConcurrentHandlingStarted`).
2. Dùng context propagation có chủ đích nếu Callable cần trace ID.
3. Khi redispatch, có thể dựng lại MDC từ request attribute/tracing context.
4. Dọn đúng key ở cuối lifecycle tương ứng; không xóa context thuộc component khác.

Micrometer Context Propagation, `TaskDecorator` hoặc các cơ chế Spring Security context propagation có thể hỗ trợ; việc chọn API phụ thuộc phiên bản và hệ thống tracing. Đừng tự cho rằng ThreadLocal được Java tự sao chép qua thread pool.

Một request attribute nhỏ có thể giữ request ID/timestamp xuyên async dispatch, nhưng không thay thế cleanup của thread-bound state và không nên chứa credentials hoặc object mutable lớn. Nếu code có nhiều async callbacks cùng đụng một object, cần đồng bộ hoặc immutable state phù hợp.

## 10. Không dùng interceptor như tầng security chính

Spring Framework lưu ý rõ interceptor không lý tưởng để triển khai security bởi đường mapping MVC có thể khác đường request matching của các lớp bảo vệ. Một request có thể bị filter chặn trước interceptor; tương tự, static resources hoặc alternate HandlerMappings có thể bypass interceptor đăng ký chỉ cho MVC.

Dùng:
- **Spring Security** cho authentication, authorization và CSRF policy.
- **Servlet Filter** cho xử lý nằm ngoài MVC handler và request/response wrapping ở tầng Servlet.
- **HandlerInterceptor** cho metadata/quan sát hoặc hook gắn với MVC handler cụ thể.
- **ResponseBodyAdvice** khi cần biến đổi response body theo message conversion, thay vì chỉnh JSON muộn ở postHandle.
- **ControllerAdvice/HandlerExceptionResolver** cho hợp đồng lỗi HTTP.

Nếu kiểm tra annotation trên handler, dùng `handler instanceof HandlerMethod`, không cast mù:

~~~java
if (handler instanceof HandlerMethod method) {
    String name = method.getMethod().getName();
    // Chỉ dùng tên method như metadata nội bộ có chủ đích.
}
~~~

Đoạn trên là fragment, cần import `org.springframework.web.method.HandlerMethod`.

## 11. Các tình huống kiểm thử bổ sung

Với ứng dụng lớn, cần thêm regression tests:
- Hai interceptors có thứ tự; xác minh chiều thuận của preHandle và chiều ngược của postHandle/afterCompletion.
- Interceptor B trả false; handler không chạy, A được dọn, B tự dọn resource của mình.
- Handler ném exception và exception được resolver xử lý; không mặc định `ex` của afterCompletion chứa original exception.
- Path khác context path, servlet mapping và trailing slash; có/không encoded characters.
- Request `OPTIONS`/CORS preflight, static resource và Actuator mapping được cấu hình riêng.
- Async return lỗi và timeout: completion/interceptor callbacks được thực thi theo policy thực tế.
- Worker `Callable` sử dụng logging/security context đúng; thread Servlet được dọn trước khi reuse.
- Production bounded executor, circuit breaker/downstream timeout và response khi queue quá tải.

Không chỉ kiểm tra HTTP 200. Một interceptor có thể “được gọi” nhưng chỉ ở một nhánh và không thực thi cleanup mong muốn. Dùng test double hoặc recorder theo request nếu cần khẳng định thứ tự callback đầy đủ.

## 12. Lỗi phổ biến và cách chẩn đoán

| Triệu chứng | Nguyên nhân có thể | Giải pháp |
| --- | --- | --- |
| Không thấy interceptor chạy | Config không được component scan hoặc path không match | Xem bean, mapping và test include |
| Exclude không có tác dụng | Pattern sai, đăng ký trùng hoặc matcher khác | Kiểm paths và registrations |
| Mỗi async request bị đếm hai lần | Initial REQUEST + ASYNC redispatch | Phân biệt dispatcher type, dùng request-scoped marker |
| `postHandle` không chạy ngay khi trả `Callable` | Đã bắt đầu async | Dùng `afterConcurrentHandlingStarted` |
| `afterCompletion` không chạy ở timeout | Không có successful final dispatch | Dùng async-specific callbacks |
| Response header đặt ở postHandle bị mất | Response đã commit | Đặt sớm khi phù hợp hoặc đúng response hook |
| MDC của user A xuất hiện ở user B | ThreadLocal không được dọn | Dọn thread context khi async start/complete |
| Async worker thiếu locale/security context | Không truyền context | Explicit propagation/TaskDecorator |
| Kết quả `Callable` không hoàn tất | Executor/timeouts/mapping async support | Kiểm queue, thread pool, timeout, Servlet async config |
| @EnableWebMvc làm biến mất MVC converter | Boot auto-configuration thay đổi | Dùng WebMvcConfigurer khi chỉ thêm interceptor |
| Filter thấy request nhưng interceptor không | Request bị reject sớm hoặc thuộc mapping khác | Chọn đúng tầng hook |
| Có thể bypass authorization | Đặt rule trong interceptor không đủ bao phủ | Spring Security |
| Runtime lỗi khi cast handler | Handler không phải HandlerMethod | Dùng instanceof |

### Trình tự debug

1. Xác nhận URL và HTTP method match handler trong Spring MVC.
2. Kiểm config class/bean interceptor trong application context.
3. Đối chiếu include/exclude patterns với route mà HandlerMapping nhận diện.
4. Log có kiểm soát `DispatcherType` ở preHandle để phân biệt REQUEST và ASYNC.
5. Với async, kiểm `request().asyncStarted()`, executor và việc gọi `asyncDispatch` trong test.
6. Với timeout, dùng callbacks ngoài afterCompletion.
7. Xác minh thread-bound cleanup/propagation không leak.
8. Nếu mục tiêu là security hoặc toàn HTTP, chuyển hook sang đúng filter/Security layer.

## 13. Câu hỏi tự kiểm tra

**1.** `WebMvcConfigurer#addInterceptors` có cần `@EnableWebMvc` trong Spring Boot không?

**Trả lời:** Thường không. Chỉ triển khai WebMvcConfigurer để bổ sung interceptors và giữ Boot MVC auto-configuration.

**2.** `excludePathPatterns("/api/public/**")` có thay Spring Security authorization rules không?

**Trả lời:** Không. Nó chỉ điều chỉnh phạm vi của interceptor đó.

**3.** Với A rồi B, thứ tự callback sync thông thường là gì?

**Trả lời:** A.pre → B.pre → handler → B.post → A.post → B.complete → A.complete.

**4.** Vì sao request trả Callable có thể gọi preHandle hai lần?

**Trả lời:** MVC xử lý lượt REQUEST đầu, bắt đầu async, rồi có lượt ASYNC redispatch sau khi kết quả sẵn sàng.

**5.** Ở lượt đầu bắt đầu async, callback nào được gọi thay postHandle/afterCompletion?

**Trả lời:** `AsyncHandlerInterceptor.afterConcurrentHandlingStarted()`.

**6.** Timeout và client disconnect có bảo đảm postHandle/afterCompletion không?

**Trả lời:** Không. Phải thiết kế callbacks chuyên biệt và kiểm container/error path.

**7.** Dọn ThreadLocal trên Servlet thread có tự chuyển state sang Callable worker không?

**Trả lời:** Không. Cần propagation được xác định rõ.

**8.** Muốn can thiệp body JSON REST sau serialization, có nên mặc định dùng postHandle?

**Trả lời:** Không. Có thể quá muộn; `ResponseBodyAdvice` hoặc message-converter layer phù hợp hơn.

## 14. Tài liệu chính thức

- [Spring Framework — MVC Interceptors](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/interceptors.html): `WebMvcConfigurer`, registration, patterns và cảnh báo security.
- [Spring Framework 6.1 — AsyncHandlerInterceptor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.8/javadoc-api/org/springframework/web/servlet/AsyncHandlerInterceptor.html): callback lúc async start, ASYNC dispatch và timeout caveats.
- [Spring Framework 6.1 — HandlerInterceptor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.8/javadoc-api/org/springframework/web/servlet/HandlerInterceptor.html): `preHandle`, `postHandle` và `afterCompletion`.
- [Spring Framework — Asynchronous Requests](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html): `Callable`, `DeferredResult`, executor, timeout, context propagation.
- [Spring Framework — MVC Java Configuration](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/enable.html): `WebMvcConfigurer` và `@EnableWebMvc`.
- [Spring Framework 6.1 — MappedInterceptor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/web/servlet/handler/MappedInterceptor.html): dùng bean với include/exclude.
- [Jakarta Servlet 6 — DispatcherType](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/dispatchertype): REQUEST, ASYNC và các dispatch types.
- [Spring Security — Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html): Filter chain và security boundary.

**Ghi nhớ:** interceptor làm việc quanh Spring MVC handler, có thể giới hạn bằng include/exclude, nhưng không thay Filter/Security. Khi handler bắt đầu async, `afterConcurrentHandlingStarted` được gọi thay cho cặp callback hoàn tất của lượt đầu; `preHandle` có thể chạy lại ở ASYNC redispatch, và timeout/thread context cleanup cần cơ chế riêng. Viết test cho cả path scope lẫn async lifecycle trước khi đưa interceptor vào production.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="filters-interceptors-006">CharacterEncodingFilter, FormContentFilter và HiddenHttpMethodFilter</a>

<details>
<summary>Xem chi tiết</summary>

# CharacterEncodingFilter, FormContentFilter và HiddenHttpMethodFilter trong Spring MVC

## 1. Ba Servlet Filter xử lý ba vấn đề khác nhau

Trong Spring MVC **Servlet stack**, dữ liệu từ client không đi thẳng tới controller. Trước `DispatcherServlet`, một chuỗi `jakarta.servlet.Filter` có thể điều chỉnh encoding, bọc request, thêm thông tin và thậm chí thay đổi HTTP method mà code phía sau nhìn thấy.

Ba filter thuộc package `org.springframework.web.filter` giải quyết ba vấn đề riêng:

| Filter | Vấn đề nó giải quyết | Tác động cốt lõi |
| --- | --- | --- |
| **`CharacterEncodingFilter`** | Dữ liệu text/form bị giải mã sai vì request/response charset chưa được thiết lập phù hợp | Gọi `setCharacterEncoding` cho request và có thể cho response; cấu hình `forceRequestEncoding`/`forceResponseEncoding` |
| **`FormContentFilter`** | Servlet API chỉ yêu cầu cung cấp form parameters theo `getParameter*` đối với POST; còn PUT/PATCH/DELETE với body form-urlencoded có thể không bind được | Đọc body `application/x-www-form-urlencoded` của PUT/PATCH/DELETE rồi bọc request để lộ form fields qua `getParameter*` |
| **`HiddenHttpMethodFilter`** | HTML form trình duyệt gửi GET/POST, trong khi handler Spring muốn `@PutMapping`, `@PatchMapping` hoặc `@DeleteMapping` | Với POST chứa form parameter `_method=PUT/PATCH/DELETE`, bọc request để `getMethod()` trả method được yêu cầu |

**Một filter không thay thế hai filter còn lại.** Sửa encoding không tự làm PUT body thành Servlet parameters; đọc PUT form body không tự đổi method; đổi POST thành DELETE không làm việc kiểm tra authorization/CSRF trở nên không cần thiết.

Ví dụ ba request:

```http
POST /demo/form HTTP/1.1
Content-Type: application/x-www-form-urlencoded; charset=UTF-8

text=Ph%E1%BB%9F
```

```http
PUT /demo/form HTTP/1.1
Content-Type: application/x-www-form-urlencoded; charset=UTF-8

text=Ph%E1%BB%9F
```

```http
POST /demo/items/7 HTTP/1.1
Content-Type: application/x-www-form-urlencoded; charset=UTF-8

_method=DELETE
```

Trong ứng dụng demo ở phần 8:

- Request thứ nhất đi tới `@PostMapping` với `text="Phở"`. Charset request phải được xác định **trước khi Servlet container đọc form parameters**.
- Request thứ hai đi tới `@PutMapping` với `text="Phở"` nhờ `FormContentFilter` hỗ trợ Servlet-style `@RequestParam` từ PUT body.
- Request thứ ba **vẫn là POST trên đường truyền HTTP**, nhưng `HiddenHttpMethodFilter` bọc request và phần MVC phía sau có thể thấy method DELETE để chọn `@DeleteMapping`.

Bài tập trung vào **Spring Framework 6.x/Spring Boot 3.x, Java 17+, Servlet MVC**; có giải thích các khác biệt với Boot 2 và 4. Với Spring WebFlux/reactive stack, không dùng các Servlet filters trên như một phần của `WebFilter` chain.

## 2. Chúng nằm ở đâu trong quá trình xử lý HTTP?

```text
Client gửi bytes HTTP
        |
        v
Servlet container: request, response, FilterChain
        |
        +--> CharacterEncodingFilter
        |      đặt request/response charset theo policy
        |
        +--> FormContentFilter
        |      PUT/PATCH/DELETE + application/x-www-form-urlencoded?
        |      parse body rồi wrap HttpServletRequest.getParameter*()
        |
        +--> HiddenHttpMethodFilter
        |      POST có _method=PUT/PATCH/DELETE?
        |      wrap HttpServletRequest.getMethod()
        |
        +--> các filter khác tùy app (logging, Security, CORS...)
        |
        v
DispatcherServlet
        |
        v
HandlerMapping chọn @PostMapping / @PutMapping / @DeleteMapping ...
        |
        v
HandlerAdapter:
  @RequestParam / @ModelAttribute -> đọc Servlet request parameters
  @RequestBody                -> dùng HttpMessageConverter khi phù hợp
        |
        v
Controller / View / ResponseEntity
        |
        v
Response đi ngược qua FilterChain
```

Đây là **sơ đồ chức năng**, không khẳng định mọi ứng dụng có chính xác ba filter theo cùng thứ tự và không có filter khác. Container chọn registrations, URL patterns, dispatcher types và order; Boot auto-configuration cung cấp mặc định tùy version/property. Với `HiddenHttpMethodFilter`, thứ tự tương đối với Spring Security và multipart processing có tác động lớn.

Điều không nên nhầm:

- `HttpServletRequest#getMethod()` là method Servlet API được các wrapper nhìn thấy. Hidden filter **không sửa gói HTTP đã gửi** và không biến một POST thành một DELETE trên đường truyền giữa browser và proxy.
- `request.getParameter("text")` lấy query/form parameters theo Servlet semantics; không đồng nghĩa đọc JSON body.
- `Content-Type` cho biết body đang dùng media type gì. `Accept` chỉ mô tả response representations mà client chấp nhận; không kích hoạt các filter nói trên.
- `@Valid` / `@Validated` thực hiện Bean Validation sau khi dữ liệu được bind; không phải chức năng của filter.
- `Filter` hoạt động ở lớp Servlet **trước** MVC; `HandlerInterceptor` chỉ được gọi trong phần chọn và thực thi handler MVC.

## 3. `CharacterEncodingFilter` hoạt động như thế nào?

Class: `org.springframework.web.filter.CharacterEncodingFilter`; kế thừa `OncePerRequestFilter`.

Servlet request parameters có thể là chuỗi đã được giải mã từ bytes HTTP. Nếu charset của input không được xác định đúng lúc, các ký tự như “Đắk Lắk”, “Phở”, “Hà Nội” có thể bị biến thành dấu hỏi hoặc ký tự mojibake.

Filter này cho phép định cấu hình:

- **`encoding`**: charset như `UTF-8`.
- **`forceRequestEncoding`**: áp đặt charset cho request, kể cả khi request đã chỉ định encoding khác.
- **`forceResponseEncoding`**: áp đặt charset cho response theo policy.
- **`forceEncoding`**: shortcut áp dụng việc force cho cả request và response trong API liên quan.

Ví dụ fragment Java cấu hình thủ công:

```java
import org.springframework.web.filter.CharacterEncodingFilter;

CharacterEncodingFilter filter = new CharacterEncodingFilter();
filter.setEncoding("UTF-8");
filter.setForceRequestEncoding(true);
filter.setForceResponseEncoding(false);
```

Fragment trên **chỉ tạo và cấu hình object**, chưa đăng ký vào Servlet container. Trong ứng dụng Spring Boot thông thường, ưu tiên properties do Boot quản lý để tránh đăng ký trùng.

### 3.1. Đặt encoding trước khi đọc parameter/body

Một lỗi thường gặp:

```java
String text = request.getParameter("text");
request.setCharacterEncoding("UTF-8"); // Có thể đã quá muộn.
```

Nếu container đã giải mã form parameter theo encoding sai, gọi `setCharacterEncoding` sau đó **không tự xây lại** chuỗi Java đã sai. Do vậy, `CharacterEncodingFilter` phải chạy trước một custom filter, controller hoặc Servlet component nào đó đọc request parameters/body.

Trong Spring Boot 3, filter quản lý encoding thường có order sớm. Nếu thêm custom filter đọc body và cố đặt ở `Ordered.HIGHEST_PRECEDENCE`, có nguy cơ chạy **trước filter encoding**. Hãy kiểm tra thứ tự filter thực tế bằng logs và integration test.

### 3.2. Force request không luôn là lựa chọn tương đương “chấp nhận mọi charset”

Ví dụ client ghi rõ:

```http
Content-Type: application/x-www-form-urlencoded; charset=ISO-8859-1
```

Nếu bạn cấu hình **force-request UTF-8**, request có thể bị đọc theo UTF-8 thay vì charset client khai báo. Điều này đúng khi API contract **bắt buộc UTF-8**, nhưng sai khi hệ thống cần hỗ trợ nhiều charset thật sự. Hãy công bố charset được chấp nhận thay vì bật “force” tùy tiện rồi gây biến dạng dữ liệu.

### 3.3. Request encoding và response encoding là hai quyết định

- Với form-urlencoded input, trọng tâm là **request charset trước khi parse**.
- Với HTML/text response, `response.setCharacterEncoding(...)` chỉ có ý nghĩa khi được áp dụng trước khi response body/headers bị commit và trước khi writer được sử dụng.
- Với JSON, Spring MVC sử dụng `HttpMessageConverter` (thường Jackson) và media type rules. `CharacterEncodingFilter` không thay thế JSON decoder hoặc việc chọn converter.
- URI path/query decoding, connector configuration, DB collation/charset, Java source file encoding và HTML meta charset là **những lớp khác**; filter này không tự sửa tất cả.

Khi debug tiếng Việt lỗi, kiểm tra cả bytes client thực gửi, `Content-Type`/charset, Servlet request decoding, filter order, converter, response charset và trình hiển thị của client.

## 4. `FormContentFilter` — giúp PUT/PATCH/DELETE đọc form parameters

Class: `org.springframework.web.filter.FormContentFilter`; kế thừa `OncePerRequestFilter`.

Theo Servlet specification, `getParameter*()` bắt buộc hỗ trợ parse `application/x-www-form-urlencoded` **cho POST** theo điều kiện hợp lệ. Không thể dựa vào mọi Servlet container để có cùng hành vi đối với PUT, PATCH, DELETE. `FormContentFilter` xử lý khoảng trống đó:

1. Nhận Servlet request trong filter chain.
2. Kiểm tra method là **PUT/PATCH/DELETE** và body có media type form-urlencoded phù hợp.
3. Dùng form HTTP message converter để đọc các key-value pairs.
4. Bọc `HttpServletRequest` để `getParameter`/`getParameterMap`/`getParameterNames`/`getParameterValues` thấy dữ liệu body như request parameters.
5. Chuyển request đã bọc đến MVC hoặc downstream filter.

Ví dụ:

```http
PATCH /api/products/7 HTTP/1.1
Content-Type: application/x-www-form-urlencoded; charset=UTF-8

title=S%E1%BB%95+tay&quantity=2
```

Handler có thể đọc:

```java
@PatchMapping(
        path = "/api/products/{id}",
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
String patch(
        @PathVariable long id,
        @RequestParam String title,
        @RequestParam int quantity) {

    return id + ":" + title + ":" + quantity;
}
```

Đây là fragment method trong controller với import `MediaType`/các MVC annotations. Nếu FormContentFilter được bật và request hợp lệ, các tham số dự kiến là `id=7`, `title="Sổ tay"`, `quantity=2`.

### 4.1. Filter này không làm gì?

- **Không đổi HTTP method.** PUT vẫn là PUT, PATCH vẫn là PATCH.
- **Không tự parse JSON thành form parameters.** Body `application/json` cần `@RequestBody`/JSON message converter.
- **Không bảo đảm mọi form type đều được hỗ trợ.** `multipart/form-data` cần multipart resolution riêng; không coi nó như `application/x-www-form-urlencoded`.
- **Không tự validate business logic.** `quantity=0` có thể bind thành int 0 nếu không có validation/rule khác.
- **Không bắt buộc nếu controller trực tiếp đọc body bằng cơ chế khác đã phù hợp.** Ví dụ `@RequestBody MultiValueMap<...>` có thể dùng message converter form phù hợp, nhưng đây là code path khác so với `@RequestParam`.

### 4.2. Query parameters và body parameters cùng tên

Ví dụ:

```http
PUT /api/products/7?title=from-query HTTP/1.1
Content-Type: application/x-www-form-urlencoded

title=from-body
```

Khi Servlet request wrapper kết hợp các nguồn parameters, đừng thiết kế hợp đồng phụ thuộc mơ hồ vào “giá trị nào thắng” hoặc “thứ tự values” cho cùng tên. Tốt hơn tránh trùng key hoặc quy định rõ vị trí: path ID trên URI, form fields chỉ trong body, pagination qua query.

### 4.3. InputStream có thể bị tiêu thụ

`FormContentFilter` phải đọc body để parse key-value data. Nếu filter khác hoặc controller về sau muốn đọc lại raw `ServletInputStream`, không nên mặc định body vẫn nguyên vẹn để đọc lần hai. Nếu ứng dụng bắt buộc inspect raw body cùng lúc với form binding, cần thiết kế wrapper/caching có kiểm soát và test trên container thật; **không log password, token hay dữ liệu nhạy cảm** chỉ để debug.

Đặt filter đọc body quá sớm hoặc chồng nhiều body-caching filters dễ làm parsing, request size và performance khác kỳ vọng.

## 5. `HiddenHttpMethodFilter` — giả lập PUT/PATCH/DELETE từ HTML POST

Class: `org.springframework.web.filter.HiddenHttpMethodFilter`; kế thừa `OncePerRequestFilter`.

HTML form tiêu chuẩn hỗ trợ gửi theo **GET hoặc POST**. Một RESTful MVC controller lại có thể muốn:

```java
@PutMapping("/products/{id}")
@PatchMapping("/products/{id}")
@DeleteMapping("/products/{id}")
```

Để nối hai cơ chế đó, HTML form có thể thực sự gửi POST, chứa một trường hidden:

```html
<form action="/demo/items/7" method="post">
  <input type="hidden" name="_method" value="DELETE">
  <button type="submit">Xóa</button>
</form>
```

Browser gửi:

```http
POST /demo/items/7 HTTP/1.1
Content-Type: application/x-www-form-urlencoded

_method=DELETE
```

`HiddenHttpMethodFilter` đọc request parameter `_method`, kiểm tra method được phép và bọc request. Các component **sau filter** (như `DispatcherServlet`) thấy `request.getMethod() == "DELETE"`, từ đó match `@DeleteMapping`.

**Chỉ ba method được phép override là PUT, DELETE và PATCH.** Đây không phải cơ chế cho client tùy ý biến POST thành GET/HEAD/OPTIONS/TRACE hoặc một method bất kỳ.

### 5.1. Cấu hình field tên khác `_method`

Mặc định field name là `_method` (`HiddenHttpMethodFilter.DEFAULT_METHOD_PARAM`). Có thể tùy chỉnh:

```java
HiddenHttpMethodFilter filter = new HiddenHttpMethodFilter();
filter.setMethodParam("_httpMethod");
```

Fragment này chỉ tạo object. Cần đăng ký filter đúng một lần; nếu đổi tên field, HTML phải gửi đúng tên mới. Trên Boot, nếu chỉ cần mặc định `_method` thì bật property tương ứng thường đơn giản hơn.

### 5.2. Vì sao chỉ xử lý POST?

Vì mục đích là hỗ trợ HTML form gửi **POST trên mạng** trong khi handler MVC muốn một method update/delete. Với một request native PUT/PATCH/DELETE, `HiddenHttpMethodFilter` **không cần thay method**.

Các filter downstream có thể thấy DELETE dù packet ban đầu là POST. Vì vậy, khi phân tích access logs ở reverse proxy và application logs, cần phân biệt **wire method** với **method đã được Servlet request wrapper trình bày**.

### 5.3. Hidden method không phải permission hoặc authorization

Client có thể tự gửi `_method=DELETE`, không cần nhấn vào nút của HTML view. Do đó:

- Phải có authentication, authorization, ownership checks riêng.
- Nếu dùng cookie authentication, phải bảo vệ request thay đổi state bằng CSRF policy phù hợp.
- Spring Security có thể dùng method-based matcher. Filter cần được sắp xếp và tích hợp sao cho lớp authorization nhìn thấy method đúng theo thiết kế.
- Không coi việc nút DELETE chỉ xuất hiện với admin trong template là bảo vệ server-side.
- Không ghi hoặc thực thi xóa chỉ vì key `_method` tồn tại; request phải đi qua toàn bộ MVC/security/business validation pipeline.

## 6. Cấu hình mặc định trong Spring Boot 3.x

**Cần phân biệt class của Spring Framework với auto-configuration của Spring Boot.** Việc một filter tồn tại trong `spring-web` không có nghĩa Boot đã bật filter đó trong mọi app.

Với **Spring Boot 3.5** trên Servlet MVC, tài liệu properties/autoconfiguration cho thấy:

| Thành phần | Boot 3.5 thường làm gì? | Property |
| --- | --- | --- |
| `CharacterEncodingFilter` | Boot autoconfigure khi Servlet web app phù hợp và encoding support bật | `server.servlet.encoding.enabled`, `server.servlet.encoding.charset`, `server.servlet.encoding.force-*` |
| `FormContentFilter` | **Bật theo mặc định** nếu điều kiện auto-configuration và classpath phù hợp | `spring.mvc.formcontent.filter.enabled=true` |
| `HiddenHttpMethodFilter` | **Tắt theo mặc định**, phải bật nếu app dùng HTML `_method` | `spring.mvc.hiddenmethod.filter.enabled=false` |

Một cấu hình minh họa cho project học tập Boot 3:

```properties
# src/main/resources/application.properties
server.servlet.encoding.enabled=true
server.servlet.encoding.charset=UTF-8
server.servlet.encoding.force-request=true

spring.mvc.formcontent.filter.enabled=true
spring.mvc.hiddenmethod.filter.enabled=true
```

- `CharacterEncodingFilter` nhận charset UTF-8 trước khi form parameter parsing (với registration order phù hợp).
- `FormContentFilter` được Boot cấu hình cho việc đọc form-urlencoded body trên PUT/PATCH/DELETE.
- `HiddenHttpMethodFilter` bật để HTML form POST `_method=...` vào các endpoints method tương ứng.
- Đây là **cấu hình chọn cho bài demo**, không phải lời khuyên mọi API đều cần bật hidden-method override. Nếu API chỉ nhận JSON với native HTTP PUT/DELETE từ mobile/SPA, thường không cần `HiddenHttpMethodFilter`.
- `force-request=true` thể hiện contract ví dụ chỉ dùng UTF-8. Với một dịch vụ chấp nhận nhiều charset khác nhau, cần thiết kế tương ứng.

**Nếu bạn muốn encoding response cũng được force**, Boot 3 có `server.servlet.encoding.force-response=true`; hãy cân nhắc loại nội dung được gửi, server/container và media type. Không thiết lập “force all” mặc định nếu client hợp lệ có charset khác.

### 6.1. Không đăng ký thủ công thêm lần nữa

Khi Boot đã tạo filter tương ứng, **đừng đồng thời tạo `@Bean CharacterEncodingFilter` / `FilterRegistrationBean` khác với cùng tác dụng** chỉ vì đọc được ví dụ trên Internet. Kết quả có thể là duplicate execution, request wrapping hai tầng, order khó dự đoán hoặc cấu hình một bean làm Boot auto-configuration back off không như bạn mong muốn.

Cách ưu tiên trong ứng dụng thông thường:

1. Chọn properties Boot phù hợp.
2. Kiểm tra registrations ở startup logs.
3. Dùng integration test xác nhận form binding và hidden override.
4. Chỉ tạo `FilterRegistrationBean` tùy chỉnh khi thực sự cần URL pattern/dispatcher types/order/constructor khác default.

### 6.2. Cách kiểm tra filter registrations

```properties
logging.level.web=debug
```

Trong Boot 3, log ở web group thường cung cấp thông tin filter registrations, URL patterns và order khi startup. Với nhiều filters đã được auto-configure (encoding, request wrappers, Spring Security, observation...), thứ tự thực tế **không chỉ gồm ba filter** của bài. Kiểm tra trong môi trường test riêng để không bật DEBUG gây lộ request metadata nhạy cảm ở production.

## 7. Một ví dụ chạy được: Java 17 + Spring Boot 3.5

Ứng dụng demo minh họa đồng thời:

- POST form-urlencoded với text tiếng Việt.
- PUT form-urlencoded mà `@RequestParam` vẫn nhận field từ body.
- PATCH form-urlencoded tương tự.
- HTML POST với `_method=DELETE` được handler `@DeleteMapping` nhận.

**Baseline:** Java 17+, Spring Boot **3.5.x**, Spring Framework 6.2.x, embedded Servlet MVC (Tomcat mặc định trong starter web). Ví dụ không dùng Spring Security, database hoặc Thymeleaf để tránh tác động ngoài phạm vi; HTML demo là form thuần.

### 7.1. Gradle dependencies

Trong project đã có Spring Boot Gradle plugin và repositories/dependency management:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đây là **fragment `build.gradle`**. Project cần được tạo/cấu hình qua Spring Initializr hoặc build file Spring Boot chuẩn. Mẫu không có dependency Spring Security; trong app thật, nếu đã có Spring Security cần cấu hình auth/CSRF/test tương ứng.

### 7.2. Properties

Tạo **`src/main/resources/application.properties`**:

```properties
server.servlet.encoding.charset=UTF-8
server.servlet.encoding.force-request=true
spring.mvc.formcontent.filter.enabled=true
spring.mvc.hiddenmethod.filter.enabled=true
```

Không thêm các bean `CharacterEncodingFilter`, `FormContentFilter` hay `HiddenHttpMethodFilter` thủ công trong demo này. Để Boot thực hiện auto-registration theo config.

### 7.3. Controller Java

Tạo **`src/main/java/demo/builtinfilters/BuiltinFiltersApplication.java`**:

```java
package demo.builtinfilters;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class BuiltinFiltersApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuiltinFiltersApplication.class, args);
    }
}

@RestController
@RequestMapping("/demo")
class FormDemoController {

    @PostMapping(
            path = "/form",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    String post(@RequestParam("text") String text) {
        return "POST:" + text;
    }

    @PutMapping(
            path = "/form",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    String put(@RequestParam("text") String text) {
        return "PUT:" + text;
    }

    @PatchMapping(
            path = "/form",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    String patch(@RequestParam("text") String text) {
        return "PATCH:" + text;
    }

    @DeleteMapping(
            path = "/items/{id}",
            produces = MediaType.TEXT_PLAIN_VALUE)
    String delete(@PathVariable long id) {
        // Chỉ mô phỏng; ứng dụng thật phải kiểm tra auth và xóa DB.
        return "DELETE:" + id;
    }
}
```

**Mã có thể biên dịch với baseline đã ghi**: file chỉ có một `public` class là `BuiltinFiltersApplication`; `FormDemoController` không public nên đặt cùng file hợp lệ. Controller được component scan từ package `demo.builtinfilters`.

Các endpoints chỉ trả text để tập trung quan sát Servlet filtering, không dùng Jackson, persistence hoặc Bean Validation. Response body `POST:Phở` chứng minh decoding của form; `PUT:Phở` chứng minh GET/POST không phải method duy nhất có thể lấy `@RequestParam` từ form body sau khi có FormContentFilter; `DELETE:7` chứng minh method override.

### 7.4. Chạy và gửi curl

Khởi chạy qua Gradle wrapper:

```bash
./gradlew bootRun
```

**POST form-urlencoded với UTF-8:**

```bash
curl -i -X POST 'http://localhost:8080/demo/form' \
  -H 'Content-Type: application/x-www-form-urlencoded; charset=UTF-8' \
  --data-urlencode 'text=Phở'
```

Response body dự kiến (status 200):

```text
POST:Phở
```

**PUT form-urlencoded:**

```bash
curl -i -X PUT 'http://localhost:8080/demo/form' \
  -H 'Content-Type: application/x-www-form-urlencoded; charset=UTF-8' \
  --data-urlencode 'text=Phở'
```

Response body dự kiến:

```text
PUT:Phở
```

**PATCH form-urlencoded:**

```bash
curl -i -X PATCH 'http://localhost:8080/demo/form' \
  -H 'Content-Type: application/x-www-form-urlencoded; charset=UTF-8' \
  --data-urlencode 'text=Phở'
```

Response body dự kiến:

```text
PATCH:Phở
```

**POST giả lập DELETE:**

```bash
curl -i -X POST 'http://localhost:8080/demo/items/7' \
  -H 'Content-Type: application/x-www-form-urlencoded; charset=UTF-8' \
  --data '_method=DELETE'
```

Response body dự kiến:

```text
DELETE:7
```

Với ví dụ này, controller chỉ có `@DeleteMapping("/items/{id}")`, **không** có `@PostMapping` cho route đó. Nếu tắt `HiddenHttpMethodFilter`, POST `_method=DELETE` **không tự match** `@DeleteMapping` và thường nhận 405 Method Not Allowed, tùy app/error handling. Nếu tắt `FormContentFilter`, PUT/PATCH form parameters từ body có thể không được Servlet container expose như mong muốn, làm `@RequestParam("text")` không resolve và thường dẫn tới HTTP 400. Không phụ thuộc vào “tình cờ container có hỗ trợ” vì Servlet spec không bắt buộc trường hợp này.

Các ví dụ curl giả định server ở cổng 8080, không có filter/proxy/security custom. Header thật (date, length, charset...) có thể khác; điều đáng test là status, method được controller chọn và Unicode text đúng.

### 7.5. HTML form dùng `_method`

Mẫu HTML có thể đặt vào template hay trang tĩnh (đường URL cần phù hợp context path của app):

```html
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <title>Xóa tài nguyên demo</title>
</head>
<body>
  <form method="post" action="/demo/items/7"
        accept-charset="UTF-8">
    <input type="hidden" name="_method" value="DELETE">
    <button type="submit">Xóa mục 7</button>
  </form>
</body>
</html>
```

Browser thực sự gửi POST. Với `HiddenHttpMethodFilter` đã bật, MVC có thể gọi method `@DeleteMapping`. Trong ứng dụng thực tế có cookie-based login, **phải thêm CSRF token theo cơ chế Spring Security đang dùng**, xác nhận quyền được phép xóa mục 7 và không dùng đường dẫn này như endpoint demo public.

## 8. Integration test: kiểm tra filter thật qua embedded server

Dùng `@SpringBootTest(webEnvironment=RANDOM_PORT)` thay vì chỉ unit test controller để xác minh Servlet container và Boot auto-registered filters thật sự tham gia. `MockMvc` có thể test MVC bindings và filters được gắn trong test context, nhưng không nhất thiết phản ánh tất cả registration/dispatcher ordering của embedded container.

Tạo **`src/test/java/demo/builtinfilters/BuiltinFiltersApplicationTests.java`**:

```java
package demo.builtinfilters;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BuiltinFiltersApplicationTests {

    @Autowired
    TestRestTemplate http;

    private HttpHeaders formHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    @Test
    void postFormDecodesUtf8() {
        String value = URLEncoder.encode(
                "Phở", StandardCharsets.UTF_8);
        HttpEntity<String> request = new HttpEntity<>(
                "text=" + value, formHeaders());

        ResponseEntity<String> response = http.postForEntity(
                "/demo/form", request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("POST:Phở", response.getBody());
    }

    @Test
    void putFormBodyBecomesRequestParameter() {
        HttpEntity<String> request = new HttpEntity<>(
                "text=Notebook", formHeaders());

        ResponseEntity<String> response = http.exchange(
                "/demo/form", HttpMethod.PUT, request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("PUT:Notebook", response.getBody());
    }

    @Test
    void patchFormBodyBecomesRequestParameter() {
        HttpEntity<String> request = new HttpEntity<>(
                "text=Notebook", formHeaders());

        ResponseEntity<String> response = http.exchange(
                "/demo/form", HttpMethod.PATCH, request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("PATCH:Notebook", response.getBody());
    }

    @Test
    void hiddenMethodChangesPostToDeleteForMvc() {
        HttpEntity<String> request = new HttpEntity<>(
                "_method=DELETE", formHeaders());

        ResponseEntity<String> response = http.postForEntity(
                "/demo/items/7", request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("DELETE:7", response.getBody());
    }

    @Test
    void postWithoutHiddenOverrideRemainsPost() {
        HttpEntity<String> request = new HttpEntity<>(
                "text=Notebook", formHeaders());

        ResponseEntity<String> response = http.postForEntity(
                "/demo/form", request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("POST:Notebook", response.getBody());
    }
}
```

Chạy:

```bash
./gradlew test
```

**Kỳ vọng các test này phản ánh hành vi nêu trên ở baseline Boot 3.5 với filter config được bật.** Tuy nhiên `PATCH` là điểm cần lưu ý: `TestRestTemplate` dùng request factory của HTTP client hiện có; một số cấu hình `RestTemplate`/`HttpURLConnection` **không hỗ trợ HTTP PATCH**. Nếu test PATCH thất bại với lỗi `Invalid HTTP method: PATCH` **trước khi request tới server**, đó là giới hạn **HTTP client của test**, không chứng minh FormContentFilter hoặc controller sai. Để kiểm thử PATCH bằng HTTP thật, cấu hình `TestRestTemplate` dùng request factory hỗ trợ PATCH (ví dụ Apache HttpComponents có dependency phù hợp), hoặc dùng `curl -X PATCH` như trên. Đừng ghi nhận test PATCH “PASS” nếu chưa kiểm tra client có gửi request thật.

**Lưu ý quan trọng để bộ test runnable:** nếu môi trường test không có HTTP client hỗ trợ PATCH, **không coi năm test phía trên chắc chắn pass nguyên xi**. Có thể thay phương thức test PATCH bằng request qua Java `java.net.http.HttpClient` (Java 17) như phần tiếp theo, vì client này hỗ trợ method PATCH tùy ý.

### 8.1. PATCH qua Java 17 HttpClient (không cần HttpComponents)

Trong test class nói trên, nếu muốn loại bỏ phụ thuộc vào PATCH support của `TestRestTemplate`, thêm field port và thay **duy nhất test method `patchFormBodyBecomesRequestParameter`** bằng:

```java
@org.springframework.boot.test.web.server.LocalServerPort
int port;

@Test
void patchFormBodyBecomesRequestParameter() throws Exception {
    java.net.http.HttpClient client =
            java.net.http.HttpClient.newHttpClient();

    java.net.http.HttpRequest request =
            java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(
                        "http://localhost:" + port + "/demo/form"))
                .header("Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8")
                .method(
                        "PATCH",
                        java.net.http.HttpRequest.BodyPublishers
                                .ofString("text=Notebook"))
                .build();

    java.net.http.HttpResponse<String> response =
            client.send(
                    request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertEquals("PATCH:Notebook", response.body());
}
```

Đoạn này là **fragment thay thế một method trong test class**, dùng Java 17 HttpClient và `LocalServerPort`, không bổ sung như method trùng tên trong cùng class. Cách thay thế này làm test PATCH gửi request tới **embedded server thật**, không lệ thuộc vào `RestTemplate` request factory. Nếu muốn áp dụng ngay, có thể dùng giải pháp `HttpClient` cho cả năm test và chỉ dùng `TestRestTemplate` cho các method khác.

**Các test trong bài chỉ mô tả kết quả dự kiến và chưa được thực thi**; muốn khẳng định pass, chạy `./gradlew test` trong project Spring Boot đã thiết lập.

## 9. Cấu hình filter thủ công khi thực sự cần kiểm soát thứ tự

Nếu ứng dụng không muốn dùng auto-registration mà cần order và dispatcher mapping rõ, có thể tạo `FilterRegistrationBean`. **Đây là phương án thay thế cho Boot property-based auto-configuration ở phần 7, không được bật đồng thời nguyên xi**.

Ví dụ fragment (đặt bên trong một class `@Configuration` trong Boot 3):

```java
@Bean
FilterRegistrationBean<CharacterEncodingFilter> encodingRegistration() {
    CharacterEncodingFilter filter = new CharacterEncodingFilter(
            "UTF-8", true);
    FilterRegistrationBean<CharacterEncodingFilter> bean =
            new FilterRegistrationBean<>(filter);
    bean.setName("myEncodingFilter");
    bean.setOrder(10);
    bean.addUrlPatterns("/*");
    bean.setDispatcherTypes(DispatcherType.REQUEST);
    return bean;
}

@Bean
FilterRegistrationBean<FormContentFilter> formRegistration() {
    FilterRegistrationBean<FormContentFilter> bean =
            new FilterRegistrationBean<>(new FormContentFilter());
    bean.setName("myFormContentFilter");
    bean.setOrder(20);
    bean.addUrlPatterns("/*");
    bean.setDispatcherTypes(DispatcherType.REQUEST);
    return bean;
}

@Bean
FilterRegistrationBean<HiddenHttpMethodFilter> hiddenMethodRegistration() {
    FilterRegistrationBean<HiddenHttpMethodFilter> bean =
            new FilterRegistrationBean<>(new HiddenHttpMethodFilter());
    bean.setName("myHiddenMethodFilter");
    bean.setOrder(30);
    bean.addUrlPatterns("/*");
    bean.setDispatcherTypes(DispatcherType.REQUEST);
    return bean;
}
```

Đây là **fragment**, cần imports `org.springframework.boot.web.servlet.FilterRegistrationBean`, `org.springframework.web.filter.*`, `jakarta.servlet.DispatcherType` và `@Bean`. Nếu thực sự dùng ba registration này, cần **tắt hoặc loại bỏ auto-registration tương ứng** của Boot và kiểm tra rằng không có một servlet filter bean khác cùng chức năng:

```properties
# Chỉ là minh họa chế độ TỰ đăng ký, khác project demo phần 7.
server.servlet.encoding.enabled=false
spring.mvc.formcontent.filter.enabled=false
spring.mvc.hiddenmethod.filter.enabled=false
```

Thứ tự `10/20/30` chỉ nói lên **thứ tự tương đối giữa các registrations được Boot quản lý**, không đảm bảo encoding luôn chạy trước Spring Security hoặc mọi filter hạ tầng khác. Nếu cần filter đọc body, phải xem order của encoding filter thực tế trước đó. Với đăng ký thông qua `web.xml`/`@WebFilter` hoặc WAR ngoài container, cơ chế ordering còn theo Servlet specification/config khác; không dựa mù quáng vào số `setOrder` của Boot cho registrations không thuộc nó.

### 9.1. Tại sao phải xem Servlet DispatcherType?

`FilterRegistrationBean#setDispatcherTypes(DispatcherType.REQUEST)` ở ví dụ trên nghĩa là filter chạy cho **request dispatch ban đầu**; nó không tự tham gia `FORWARD`, `ERROR`, `ASYNC`, `INCLUDE`. Nếu filter cần hỗ trợ async/error, chọn dispatcher types có chủ đích và hiểu rằng cả ba filter kế thừa `OncePerRequestFilter`, mặc định có chính sách skip async/error. Không bật tất cả dispatcher types vô điều kiện chỉ vì có enum.

Với `HiddenHttpMethodFilter`, việc thay method một lần trong REQUEST trước khi MVC chọn handler thường là mục tiêu đủ rõ; không cần tự lặp lại method override trong ERROR dispatch. Với form data, tránh việc đọc body lần hai khi request đã bị wrapper xử lý và Servlet dispatch tiếp.

### 9.2. Multipart form và HiddenHttpMethodFilter

`HiddenHttpMethodFilter` phải đọc `_method` như request parameter, nên với POST `multipart/form-data` cần **multipart processing diễn ra trước nó** để field có thể được lấy theo cách filter dự kiến. Javadoc khuyến nghị `MultipartFilter` trước HiddenHttpMethodFilter khi dùng cách filter-based multipart resolution.

**Không kết luận rằng luôn phải thêm MultipartFilter trong Boot.** Trong các ứng dụng Spring MVC, multipart có thể được cấu hình để `DispatcherServlet` xử lý; khi HiddenHttpMethodFilter chạy trước DispatcherServlet, khả năng đọc multipart parameters tùy Servlet container và multipart config. Do đó cần integration test với **multipart POST + _method=PATCH/DELETE** trên đúng container, hoặc chọn form-urlencoded đơn giản cho hidden method.

Tránh kết hợp `MultipartFilter` với Servlet-specific multipart resolution không tương thích. Nếu custom order cần multipart trước hidden, xác định chính xác filter nào resolve multipart trong kiến trúc của bạn.

## 10. Spring Security, CSRF và HTTP method override

Khi bật `HiddenHttpMethodFilter`, security cần xem method đã được biến đổi trong Servlet request wrapper **nếu authorization policy dựa trên HTTP method**.

Một luồng mong muốn:

```text
Browser gửi POST /items/7 + _method=DELETE
      |
      +-- character encoding
      +-- parse form params khi cần
      +-- hidden method wrapper -> getMethod() = DELETE
      +-- Spring Security authentication/authorization/CSRF
      +-- DispatcherServlet -> @DeleteMapping
```

Đây là **sơ đồ yêu cầu thiết kế**, không phải thứ tự auto-reg chắc chắn trong mọi Boot/Security version. `Spring Security FilterChainProxy` nằm trong một Servlet filter registration riêng; nếu order khác, Security có thể xét POST trong khi MVC sau đó xét DELETE. Phải kiểm tra startup filter list hoặc security debug và test authorized/unauthorized requests.

**CSRF:** với các request sử dụng cookie session và thay đổi state, bảo vệ CSRF là yêu cầu quan trọng. HTML form method override không né CSRF. Spring Security có thể xử lý request parameter `_csrf`; bảo đảm hidden method filter, encoding/multipart và CSRF token processing cùng hoạt động trên request phù hợp. Nếu POST ban đầu có `_method=DELETE`, CSRF vẫn phải được xác minh theo policy của ứng dụng.

**Authorization:** đừng dựa vào việc UI chỉ render nút xóa cho admin. Client có thể tự gửi POST form hoặc DELETE native; server phải kiểm tra tài nguyên, identity và quyền thao tác.

### 10.1. Native DELETE và hidden DELETE khác gì?

| Khía cạnh | Native DELETE | POST + `_method=DELETE` |
| --- | --- | --- |
| HTTP method trên mạng | DELETE | POST |
| Servlet method sau hidden filter | DELETE | DELETE (nếu filter bật/mapping đúng) |
| Proxy/WAF/access logs phía trước | Thấy DELETE | Thấy POST |
| MVC handler | `@DeleteMapping` | `@DeleteMapping` |
| Browser HTML form không dùng JS | Không hỗ trợ method DELETE trực tiếp | Có thể dùng |
| Security/filter order | Cần áp dụng đúng | Đặc biệt cần đồng nhất method sau override |
| Caching, idempotency và retries | Theo semantics method HTTP gửi thật | Proxy/client thấy POST; không suy ra tính idempotent ở network |

Nếu API phục vụ SPA/mobile đã gửi native HTTP methods được, **thường không cần hidden method override**. Chỉ bật filter khi thực sự có HTML forms hoặc legacy clients cần pattern này.

## 11. Lỗi encoding: phân biệt form body, URL và response

### 11.1. Form-urlencoded có quy tắc percent-encoding

Với UTF-8, chuỗi `Phở` được mã hóa vào form-urlencoded body theo percent-encoded bytes. Ví dụ:

```text
Java String:       Phở
UTF-8 bytes:       50 68 E1 BB 9F
Form encoded:      Ph%E1%BB%9F
```

Khi nhận request đúng charset, Servlet/container và FormContentFilter (tùy method) khôi phục Java `String "Phở"`. Nếu bytes bị giải mã theo sai charset, text sẽ sai dù controller vẫn được gọi.

### 11.2. URL query/path không hoàn toàn do CharacterEncodingFilter quyết định

`/products?title=...` và `/products/{slug}` chịu ảnh hưởng URL percent-encoding, container connector settings và URI decoding policy. Ví dụ Tomcat có `server.tomcat.uri-encoding=UTF-8` trong cấu hình phù hợp. **Không** xem việc bật `CharacterEncodingFilter` là đã kiểm soát toàn bộ URL path/query decoding trong mọi Servlet container.

### 11.3. JSON không cần FormContentFilter

Client:

```http
PUT /api/products/7 HTTP/1.1
Content-Type: application/json

{"title":"Phở"}
```

Controller:

```java
@PutMapping("/api/products/{id}")
ResponseEntity<Void> update(
        @PathVariable long id,
        @RequestBody ProductUpdateRequest request) {

    // ...
}
```

Đây là fragment; `ProductUpdateRequest` phải định nghĩa trong project. Spring MVC đọc JSON bằng `HttpMessageConverter`, thường Jackson, **không** thông qua `FormContentFilter` hay `_method`.

Nếu endpoint chỉ nhận JSON, không bật form parsing/hidden override chỉ để “đảm bảo request được xử lý”. Cần chọn converter, validation và API contract đúng với JSON.

### 11.4. Response text mất dấu

Nếu input đúng nhưng output sai, kiểm tra response `Content-Type` charset, converter viết body (String converter, Thymeleaf, JSP, Jackson), Servlet response encoding và HTML/browser interpretation. `force-response=true` chỉ có tác dụng theo context và trước khi response committed; nó không sửa những byte đã ghi sai bởi code khác.

## 12. Các lỗi thường gặp và cách tìm nguyên nhân

| Triệu chứng | Khả năng cao | Hướng xác minh |
| --- | --- | --- |
| `Phở` thành ký tự lỗi | CharacterEncodingFilter chạy muộn, force policy sai hoặc request header/bytes không khớp | Log charset trước `getParameter` và kiểm tra body bytes bằng client thực |
| Form POST đọc được, PUT form-urlencoded lại thiếu `@RequestParam` | Chưa có FormContentFilter hoặc media type không phù hợp | Kiểm tra `spring.mvc.formcontent.filter.enabled` và content type |
| POST `_method=DELETE` trả 405 | Hidden filter chưa bật, field thiếu/sai tên, filter không map URL, hoặc method không nằm trong bộ cho phép | Bật property đúng Boot version, test request thật |
| POST hidden DELETE vào nhầm `@PostMapping` | Method wrapper chưa chạy trước MVC | Kiểm tra registration order và chain |
| POST `_method=PATCH` trả 415 | Handler PATCH `consumes` không khớp request body media type | Kiểm tra `Content-Type` là form-urlencoded và route |
| Multipart hidden method không hoạt động | Param `_method` chưa có khi filter chạy | Kiểm tra multipart resolution trước hidden filter và container |
| Cùng filter chạy hai lần | Auto-registration và manual registration đồng thời | Kiểm tra registrations và bean names |
| Security chặn request khác kỳ vọng | Security thấy method ở phase khác MVC hoặc thiếu auth/CSRF | Kiểm tra order, method-based matchers và CSRF |
| Body bị mất sau log filter | Một filter đọc `ServletInputStream` trước người xử lý chính | Thiết kế caching wrapper có kiểm soát |
| Dữ liệu form sai dù charset UTF-8 | HTML/client thực gửi encoding khác hoặc đã decode quá sớm | Xem raw bytes/headers và request charset |
| Lỗi chỉ khi deploy WAR | Filter ordering/config khác embedded container | Kiểm tra `web.xml`/Servlet registration thực tế |
| Tests xanh với MockMvc nhưng production sai | Test không tái hiện filter registration/container | Thêm RANDOM_PORT integration test |
| Hidden GET/HEAD không thay đổi method | HiddenHttpMethodFilter chỉ xét POST | Dùng native method hoặc thiết kế API khác |
| Có `_method=TRACE` mà không chọn TRACE handler | Filter chỉ hỗ trợ PUT/DELETE/PATCH | Không phụ thuộc override method bất kỳ |

**Chẩn đoán nên đi theo thứ tự:** raw HTTP request → filter registrations/URL/dispatcher mapping → encoding/filter order → wrapped method/params → Spring MVC handler mapping → binding/validation → response body/media type.

Với thông tin nhạy cảm như password, token, session ID và personal data, không bật log raw form body chỉ để debug.

## 13. Lựa chọn scope của filter và chi phí vận hành

Ba filter này kế thừa `OncePerRequestFilter`, nhưng “once” không đồng nghĩa chạy đúng một lần trong toàn bộ vòng đời ứng dụng hoặc mọi async/error dispatch. Mỗi Servlet request/dispatch có cách xử lý riêng. Khi filter map nhiều dispatcher types, cần xét cơ chế skip async/error của base class, tránh tự giả định mọi dispatch sẽ tiếp tục parse body hay override method.

Khi xử lý đồng thời:

- Servlet filters thường là **singleton instance** trong container và phục vụ nhiều requests trên nhiều threads.
- Không lưu mutable request-specific data ở fields của filter instance.
- Nếu dùng request attribute/ThreadLocal/MDC để quan sát method trước/sau override, cleanup trong `finally` để không leak data sang request tiếp.
- Không chỉnh lại encoding giữa request ở runtime bằng shared mutable filter config.
- Hạn chế đọc/lưu form body nhiều lần; áp giới hạn kích thước request qua Servlet/container, bảo vệ file upload riêng.

Về performance: encoding/method override thường nhẹ, còn form body parsing cần đọc và xử lý dữ liệu. Với app nhận payload lớn, phân biệt form body và raw JSON, thiết lập size limits và đo tác động thay vì bật toàn bộ custom wrappers.

## 14. Boot 2, Boot 3 và Boot 4: đừng dùng nhầm package/property

| Nền tảng | Servlet imports | Các filter Spring | Cấu hình cần xem |
| --- | --- | --- | --- |
| Spring Framework 5.x + Boot 2.x | `javax.servlet.*` | `CharacterEncodingFilter`, `FormContentFilter`, `HiddenHttpMethodFilter` trong `org.springframework.web.filter` | Properties theo đúng bản Boot 2, nhiều version dùng `server.servlet.encoding.*` và `spring.mvc.*.filter.enabled` |
| Spring Framework 6.x + Boot 3.x | **`jakarta.servlet.*`** | Các class filter cơ bản cùng package Spring; Boot 3.5 auto-configure như phần 6 | `server.servlet.encoding.*`, `spring.mvc.formcontent.filter.enabled`, `spring.mvc.hiddenmethod.filter.enabled` |
| Spring Framework 7.x + Boot 4.x | **`jakarta.servlet.*`** | Servlet Filter vẫn là layer web đúng, nhưng auto-config module/registration của Boot 4 thay đổi | Encoding được chuyển sang **`spring.servlet.encoding.*`**; kiểm tra metadata của webmvc auto-config khi tìm properties form/hidden |

**Chú ý thay đổi thực tế ở Boot 4:** `server.servlet.encoding.mapping.*` có thể vẫn dùng cho locale-to-charset mapping của **embedded Servlet container**, nhưng các properties cấu hình `CharacterEncodingFilter` được chuyển sang `spring.servlet.encoding.charset` / `spring.servlet.encoding.force-*`. Vì vậy hãy đọc đúng phần “Common Application Properties” của **Boot 4**; không copy nguyên `server.servlet.encoding.charset` từ Boot 3 và kết luận encoding mới đã được áp dụng.

Ví dụ **Boot 4** chỉ minh họa namespace encoding:

```properties
spring.servlet.encoding.charset=UTF-8
spring.servlet.encoding.force-request=true
```

Đây **không** phải đầy đủ cấu hình Boot 4 của bài demo; tập webmvc starters, tests và auto-config khác phải theo Spring Boot 4 BOM. Đừng trộn imports `javax.servlet.*` từ tutorial cũ vào Boot 3/4.

### Khi chạy trên Servlet container bên ngoài

Với WAR triển khai ngoài, filter có thể được đăng ký qua `web.xml`, Servlet annotations, `WebApplicationInitializer` hoặc container-specific configuration. Thứ tự đăng ký và dispatcher mappings có thể khác embedded Boot. Hãy xác minh thật thay vì coi property Boot hoặc `FilterRegistrationBean#setOrder` luôn là nguồn duy nhất kiểm soát mọi Servlet filter.

## 15. Checklist thực hành

- [ ] Đã xác định request là form-urlencoded, multipart hay JSON; không dùng một filter sai loại dữ liệu.
- [ ] `CharacterEncodingFilter` chạy trước thành phần đầu tiên đọc/parse form parameters.
- [ ] Charset client/server nhất quán; không force UTF-8 nếu contract thực sự cho phép encoding khác.
- [ ] `FormContentFilter` bật nếu cần nhận `@RequestParam`/`@ModelAttribute` từ PUT/PATCH/DELETE form body.
- [ ] `HiddenHttpMethodFilter` chỉ bật khi client dùng HTML POST + `_method`; bảo đảm giá trị chỉ PUT/PATCH/DELETE.
- [ ] Với multipart POST, `_method` có thể đọc được trước MVC handler; test trên container đích.
- [ ] Không đăng ký trùng filter qua Boot auto-config và `FilterRegistrationBean`/`@Component`/`web.xml`.
- [ ] Filter registration order, URL patterns và dispatcher types được xác minh bằng startup logs.
- [ ] Spring Security authorization/CSRF nhìn thấy method phù hợp và vẫn bảo vệ state-changing requests.
- [ ] Không sử dụng HiddenHttpMethodFilter để lách giới hạn reverse proxy hoặc policy HTTP methods một cách thiếu kiểm soát.
- [ ] Không đọc body hai lần khi filter, logger và controller cùng cần stream.
- [ ] Có HTTP integration tests trên container thật cho Unicode, PUT/PATCH form-urlencoded và hidden DELETE.
- [ ] Có negative tests khi tắt hidden filter, khi media type sai, khi field bắt buộc thiếu, và với request không được phép.
- [ ] Không log body/hidden fields chứa bí mật; kiểm soát request size.
- [ ] Khi nâng Boot version, xem lại namespace properties, imports và filter registration, không dựa vào ví dụ của major version cũ.

## 16. Bài tập tự kiểm tra

1. Một POST form-urlencoded chứa `name=Ph%E1%BB%9F` bị decode thành text sai, nhưng JSON cùng dữ liệu lại đúng. Hãy phân tích sự khác nhau giữa Servlet form parameters và JSON message converter, và vị trí CharacterEncodingFilter.
2. Vì sao `@RequestParam String name` trong `@PutMapping` có thể không có giá trị trên một Servlet container nếu tắt FormContentFilter, dù raw body có `name=An`?
3. Khi HTML form gửi POST với `_method=DELETE`, HTTP method nào được reverse proxy nhìn thấy? MVC downstream nhìn method nào khi HiddenHttpMethodFilter đã bật?
4. Cho ba filters Encoding, FormContent và Hidden, vì sao không đủ để chỉ chỉ định order mà còn phải xem URL mapping và dispatcher types?
5. Bật `spring.mvc.hiddenmethod.filter.enabled=true` trong Boot 3.5 nhưng POST `_method=DELETE` vẫn trả 405. Nêu ít nhất năm nguyên nhân ngoài việc property gõ sai.
6. Nếu custom security matcher bảo vệ mọi DELETE nhưng cho phép POST, và hidden-method filter chạy **sau** Security, rủi ro là gì? Hãy kiểm tra filter order và thiết kế lại.
7. Browser upload file bằng `multipart/form-data` kèm field `_method=PATCH`. Vì sao HiddenHttpMethodFilter có thể không đọc được field nếu multipart parsing chưa chạy? Nên test ở layer nào?
8. Gửi `PUT Content-Type: application/json` tới endpoint có `@RequestBody`. Vì sao FormContentFilter không phải giải pháp cho mọi lỗi parse JSON?
9. Trên Spring Boot 4, property `server.servlet.encoding.charset` từ Boot 3 không còn là namespace cấu hình chính của CharacterEncodingFilter. Tìm property tương ứng và kiểm chứng bằng test tiếng Việt.
10. Chạy unit test controller với MockMvc thấy `DELETE:7`, nhưng integration test với embedded container không pass. Những khác biệt filter registration/URL mapping/dispatcher type nào cần kiểm tra?
11. Thiết kế một test PATCH dùng Java 17 `HttpClient` thay cho request factory không hỗ trợ PATCH; chứng minh request thật đến controller với body form-urlencoded.
12. Trong app chỉ có SPA gửi JSON qua native PUT/DELETE, những filter nào thực sự cần? Giải thích vì sao nên giảm cấu hình không sử dụng.

## 17. Tài liệu chính thức

- [Spring Framework 6.2 — Servlet Filters](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/filters.html): vai trò filters; phần Form Data giải thích `FormContentFilter` trên PUT/PATCH/DELETE.
- [Spring Framework 6.2 — CharacterEncodingFilter API](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/web/filter/CharacterEncodingFilter.html): request/response encoding, force options.
- [Spring Framework 6.2 — FormContentFilter API](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/web/filter/FormContentFilter.html): parse form-urlencoded body và wrapper `getParameter*`.
- [Spring Framework 6.2 — HiddenHttpMethodFilter API](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/web/filter/HiddenHttpMethodFilter.html): `_method`, PUT/PATCH/DELETE và lưu ý multipart.
- [Spring Framework 6.2 — OncePerRequestFilter API](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/web/filter/OncePerRequestFilter.html): giới hạn “once” trong async/error/nested dispatch.
- [Spring Boot 3.5 — Common Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html): mặc định của `spring.mvc.formcontent.filter.enabled` và `spring.mvc.hiddenmethod.filter.enabled`, encoding properties.
- [Spring Boot 3.5 — Servlet Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html): auto-registration/order và character encoding.
- [Spring Boot 4.0 — Common Application Properties](https://docs.spring.io/spring-boot/4.0/appendix/application-properties/index.html): namespace `spring.servlet.encoding.*` và các thuộc tính cho Boot 4.
- [Spring Framework — MultipartFilter API](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/web/multipart/support/MultipartFilter.html): filter-based multipart resolution và tương tác với HiddenHttpMethodFilter.
- [Jakarta Servlet Specification 6.0](https://jakarta.ee/specifications/servlet/6.0/): Servlet request parameters, filters, dispatcher types và form processing.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="filters-interceptors-007">Bọc request/response để đọc nội dung và giới hạn bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# Bọc request/response để đọc nội dung và giới hạn bộ nhớ trong Spring MVC

> **Bối cảnh:** Spring Web MVC dùng Servlet API (`jakarta.servlet.*`) trên Java 17+, ví dụ Spring Boot 3.x / Spring Framework 6.x. Chủ đề tập trung vào **`HttpServletRequestWrapper`, `HttpServletResponseWrapper`, `ContentCachingRequestWrapper` và `ContentCachingResponseWrapper`**, việc đọc HTTP body mà không phá luồng xử lý, cũng như **kiểm soát lượng bộ nhớ được giữ để ghi log/quan sát**. Phải phân biệt rõ ba giới hạn: **kích thước cache**, **kích thước payload được chấp nhận**, và **bộ nhớ của toàn pipeline (converter, file upload, service)**.

## 1. Vì sao cần bọc request và response?

Trong Servlet stack, HTTP body được đọc/ghi dưới dạng stream. Một Filter có thể muốn đo hoặc quan sát:

- Request method, URL, status, thời gian thực thi.
- Một bản xem trước nhỏ (preview) của request body khi người dùng gọi JSON/text API.
- Một bản xem trước nhỏ của response body để chẩn đoán lỗi trong môi trường thử nghiệm.
- Số byte response được ghi, giúp phân tích dữ liệu lớn mà không giữ toàn bộ trong RAM.
- Việc từ chối upload hoặc JSON body quá lớn bằng chính sách giới hạn độc lập.

Cách làm nguy hiểm:

```java
// KHÔNG dùng khi body có thể rất lớn.
byte[] all = request.getInputStream().readAllBytes();
System.out.println(new String(all, StandardCharsets.UTF_8));
chain.doFilter(request, response);
```

Đoạn code minh họa này có hai vấn đề:

1. `readAllBytes()` có thể giữ **toàn bộ request body** trong heap. Request vài trăm MiB có thể gây áp lực GC hoặc `OutOfMemoryError`.
2. Nó **tiêu thụ request input stream trước** khi controller và `HttpMessageConverter` đọc; nếu không có cơ chế replay hoàn chỉnh, `@RequestBody` sau đó sẽ không còn body và có thể nhận lỗi.

Response cũng có vấn đề tương tự: gom body vào `ByteArrayOutputStream` không giới hạn để ghi log có thể khiến server giữ nhiều bản sao của một file hoặc một JSON response lớn.

**Mục tiêu đúng:** cho phần còn lại của ứng dụng tiếp tục đọc/ghi body bình thường, chỉ giữ tối đa một lượng byte đã định (ví dụ **1024 byte mỗi chiều/request**) để quan sát, và **đừng nhầm con số ấy là hard limit cho toàn bộ HTTP body**.

## 2. Kiến trúc Servlet wrapper

`HttpServletRequestWrapper` và `HttpServletResponseWrapper` thuộc package `jakarta.servlet.http` trong Spring Boot 3.x. Chúng thực thi Decorator pattern: chứa request/response thật, mặc định delegate các phương thức xuống đối tượng gốc và cho phép override một số method.

```text
Client HTTP request
   |
Servlet Filter
   | new RequestWrapper(originalRequest)
   | new ResponseWrapper(originalResponse)
   |
   v
chain.doFilter(requestWrapper, responseWrapper)
   |
   v
DispatcherServlet
   |
Controller @RequestBody -> HttpMessageConverter đọc wrapper.getInputStream()
   |
Controller trả DTO / String / Resource
   |
HttpMessageConverter -> wrapper.getOutputStream() / wrapper.getWriter()
   |
   v
Servlet container truyền bytes đến client
   |
Filter ghi thống kê bounded preview, status, thời gian (nếu sync)
```

Ví dụ hình dạng tối giản:

```java
class MyRequestWrapper extends HttpServletRequestWrapper {
    MyRequestWrapper(HttpServletRequest request) {
        super(request);
    }
}

class MyResponseWrapper extends HttpServletResponseWrapper {
    MyResponseWrapper(HttpServletResponse response) {
        super(response);
    }
}
```

Đây là fragment mô tả các lớp base, không phải demo hoàn chỉnh. Một wrapper chỉ delegate sẽ không tự cache được body. Muốn đọc hoặc quan sát body, phải dùng wrapper Spring có sẵn hoặc override cách đọc/ghi stream một cách đúng chuẩn Servlet.

**Không tự ý sửa byte body:** nếu wrapper thay body nhưng vẫn giữ `Content-Length`, charset, `Content-Encoding` hoặc chữ ký HMAC cũ, downstream sẽ đọc sai hoặc signature không hợp lệ. Bài này chủ yếu **quan sát**, không biến đổi nội dung.

## 3. Tại sao request body thường chỉ đọc được một lần?

`HttpServletRequest.getInputStream()` cho phép đọc HTTP entity body dưới dạng byte; `getReader()` cho phép đọc qua `Reader` với character encoding. Chúng là **hai cách truy cập thay thế nhau**, không được trộn tùy tiện trên cùng request theo hợp đồng Servlet.

Trong Spring MVC:

```java
@PostMapping(path = "/echo", consumes = MediaType.TEXT_PLAIN_VALUE)
@ResponseBody
String echo(@RequestBody String body) {
    return "echo:" + body;
}
```

Đây là method minh họa: message converter cần đọc body từ request stream để tạo `String`. Nếu Filter đã đọc hết stream, converter không thể tự tua lại như đọc file thông thường.

### 3.1. Các cách tiếp cận

| Cách | Có tiêu thụ stream trước handler? | Bộ nhớ | Dùng khi nào? |
| --- | --- | --- | --- |
| Filter đọc `readAllBytes()` trước `doFilter` | **Có** | Theo toàn body, không an toàn nếu không chặn | **Tránh** |
| `ContentCachingRequestWrapper(request, limit)` rồi downstream tự đọc | **Không** chủ động đọc trước | Cache tối đa `limit` byte được đọc | Logging/observability request đã tiêu thụ |
| Tự dựng replayable request từ một bounded byte array | **Có**, nhưng wrapper trả lại stream từ bản đã lưu | Tối đa hard cap đặt trước, thêm copy nếu không cẩn thận | Chỉ khi thật sự cần đọc trước và replay body nhỏ |
| Tee request stream, giữ prefix hữu hạn trong quá trình downstream đọc | **Không** chủ động đọc trước | Theo giới hạn preview | Đếm/ghi sample an toàn, nâng cao |
| Parse JSON/DTO trong controller/service | Framework đọc đúng một lần | Theo converter/object graph | Xử lý nghiệp vụ, không thay audit filter |

Điểm quan trọng: **khả năng xem lại những byte đã đi qua** và **khả năng cho downstream đọc lại cùng body** là hai tính năng hoàn toàn khác nhau.

## 4. ContentCachingRequestWrapper: cache chỉ những gì đã được đọc

Spring cung cấp:

```java
ContentCachingRequestWrapper wrapped =
        new ContentCachingRequestWrapper(request, 1024);
chain.doFilter(wrapped, response);
byte[] preview = wrapped.getContentAsByteArray();
```

Đây là fragment trong Filter, không phải chương trình độc lập. Converter/controller đọc body qua `wrapped`; khi đó wrapper sao lưu **tối đa 1024 byte** đã được tiêu thụ. Nếu handler **không đọc body**, cache có thể **rỗng**. `getContentAsByteArray()` trả nội dung **đã được đọc tới thời điểm gọi**, không tự đọc toàn request.

Theo Javadoc Spring Framework 6.2, constructor nhận `contentCacheLimit` để giới hạn cache; với các nhánh bản vá mới của Spring 6.2, constructor không truyền limit còn bị deprecate để khuyến khích đặt ngưỡng cụ thể. **Luôn dùng constructor có limit dương** cho logging.

### 4.1. Giới hạn cache KHÔNG phải giới hạn tải lên

Giả sử HTTP body dài **10 MiB**, còn limit là **1024 byte**. Sau khi `@RequestBody` đọc đủ, converter vẫn có thể nhận và deserialize toàn 10 MiB; `ContentCachingRequestWrapper` chỉ lưu tối đa 1024 byte trong **cache của wrapper**.

| Đại lượng | Trong ví dụ |
| --- | --- |
| HTTP request body thực | 10 MiB |
| `contentCacheLimit` | 1024 byte |
| `getContentAsByteArray().length` sau khi downstream đọc | Tối đa 1024 byte |
| Số byte converter được phép đọc | **Không bị chặn** bởi cấu hình cache này |
| Giới hạn heap của JSON tree/DTO | **Không được bảo đảm** bởi cấu hình cache |

Default của `handleContentOverflow(int limit)` ở wrapper là không làm gì. Nó **không mặc định trả 413 Payload Too Large** khi vượt cache limit. Có thể override hook, nhưng exception xảy ra trong lúc đọc stream và cần xử lý an toàn về filter/response commitment; không nên cho rằng override một hook là chính sách giới hạn upload đầy đủ.

### 4.2. Có phải ContentCachingRequestWrapper cho phép đọc body lần thứ hai?

Không. Nó cung cấp API đọc **bản sao cache** sau khi body được tiêu thụ, chứ không mặc định “rewind” toàn input stream cho `@RequestBody` đọc lại một lần nữa. Đặc biệt khi body lớn hơn `contentCacheLimit`, cache còn **không chứa toàn body**.

Nếu thật sự cần request được đọc lại:

1. Chọn một **hard cap hữu hạn** cho toàn body.
2. Đọc tối đa `cap + 1` byte (không dùng `readAllBytes()`) trước khi quyết định nhận/từ chối.
3. Từ chối với **413 Content Too Large** nếu vượt giới hạn, bao gồm trường hợp client không gửi `Content-Length`.
4. Với body hợp lệ, dựng custom `HttpServletRequestWrapper` trả `ServletInputStream` mới từ mảng đã lưu; xử lý cả `getReader()`, encoding, `isFinished`/`isReady`/`setReadListener` đúng Servlet API.
5. Với nonblocking/async/multipart, phải xét các yêu cầu tương thích bổ sung; tốt nhất dùng cơ chế chuyên dụng của container hoặc codec.

Cách đọc trước/replay luôn phải cân nhắc memory amplification do nhiều request đồng thời. Nếu chỉ cần logging, **không replay**; dùng cache-on-read có limit là đơn giản hơn.

## 5. ContentCachingResponseWrapper: dễ dùng nhưng có thể giữ toàn bộ response

Spring có `ContentCachingResponseWrapper`:

```java
ContentCachingResponseWrapper wrapped =
        new ContentCachingResponseWrapper(response);
try {
    chain.doFilter(request, wrapped);
    byte[] body = wrapped.getContentAsByteArray();
    // Chỉ dùng khi chắc response nhỏ hoặc đã có kiểm soát.
} finally {
    wrapped.copyBodyToResponse();
}
```

Đây là ví dụ **pattern sync được giản lược**, không nên copy vào production không giới hạn kích thước, async/error dispatch hoặc các luồng đã committed. Điểm cực kỳ quan trọng:

- `ContentCachingResponseWrapper` giữ những byte response được ghi qua nó trong buffer nội bộ.
- `getContentAsByteArray()` trả bản sao của **nội dung response được cache**.
- Phải gọi `copyBodyToResponse()` trong mô hình sử dụng sync thông thường để **chuyển nội dung cache sang response gốc**. Quên lệnh này có thể khiến client nhận body rỗng/bị thiếu.
- `flushBuffer()` trên wrapper **không đồng nghĩa** đẩy body qua response gốc trước khi copy; cần hiểu behavior của wrapper.
- API thông dụng của lớp này **không có constructor `(response, maxBytes)`** để giới hạn toàn cache như request wrapper.

Ví dụ response 100 MiB từ `@ResponseBody` có thể khiến wrapper giữ cỡ 100 MiB (chưa kể serializer/DTO/array copy), trước khi copy sang client. Nếu 100 requests như vậy chạy đồng thời, chỉ phần cache lý thuyết đã lên tới **~10 GiB**, không tính overhead. **Tránh bọc toàn bộ response bằng ContentCachingResponseWrapper nếu endpoint có body lớn hoặc không dự đoán được.**

### 5.1. `copyBodyToResponse()` không phải bảo đảm an toàn bộ nhớ

Nó giải quyết việc **phát body ra client** sau khi đã cache, chứ không làm quá trình cache trở thành streaming bounded-memory. Cách dùng này phù hợp với những trường hợp có chủ đích cần toàn response để tính ETag hoặc kiểm thử body nhỏ. Ví dụ `ShallowEtagHeaderFilter` sử dụng kỹ thuật buffering để tính ETag; điều đó có trade-off memory/latency.

### 5.2. Vì sao response streaming cần cách khác?

Một endpoint `Resource`/file lớn, `StreamingResponseBody`, SSE, chunked transfer hoặc async rendering có thể cần đẩy bytes liên tục. Giữ toàn body trong heap vừa không phù hợp mục tiêu streaming, vừa có thể làm sai latency hoặc quên flush/copy.

Muốn **chỉ quan sát một prefix nhỏ của response**, nên dùng **tee wrapper**: byte nào được ghi thì vừa chuyển tiếp ngay tới Servlet output stream gốc, vừa sao lưu **tối đa N byte đầu**, và đếm tổng số byte đã đi qua wrapper.

## 6. Bounded preview: tách việc quan sát khỏi body size

Một implementation hợp lý cho logging:

```text
Controller writes byte[] / JSON / String
             |
             v
Response tee ServletOutputStream
     |
     +-----> output stream gốc -> client (ghi toàn bộ)
     |
     +-----> preview buffer (tối đa 1024 byte)
     |
     +-----> counter (số byte đi qua)
```

Với response **100 KiB**, preview 1024 byte:

- Client vẫn nhận **đầy đủ 100 KiB**.
- Tee wrapper chỉ giữ **tối đa 1024 byte**.
- Counter ghi nhận số byte đi qua wrapper; không cần tạo `byte[100KiB]` để làm log.
- Nếu controller đã sinh một `String` 100 KiB hoặc một DTO rất lớn, bộ nhớ **của controller/serializer** vẫn phải được quản lý riêng. Bounded tee chỉ giới hạn **bộ nhớ phụ của wrapper**.

### 6.1. Một lưu ý quan trọng về `getWriter()`

Servlet response có hai cách ghi:

- `getOutputStream()` cho binary và converter output.
- `getWriter()` cho character text theo encoding.

Một wrapper phải xử lý **cả hai**. Với cùng response, ứng dụng không được gọi lẫn `getWriter()` và `getOutputStream()` như hai output mode độc lập; chúng loại trừ nhau theo hợp đồng Servlet. Nếu override một method và bỏ qua method kia, bạn có thể log được binary nhưng bỏ sót response HTML/text, hoặc ngược lại.

### 6.2. Giữ nguyên trạng thái `isReady()` và write listener

`ServletOutputStream` có `isReady()` và `setWriteListener(...)` cho nonblocking I/O. Nếu custom wrapper chỉ implement `write(int)` rồi trả `isReady()=true` bất kể stream gốc, code async có thể ghi vào stream chưa sẵn sàng hoặc phá contract. Delegate các API readiness/listener xuống stream gốc. Điều này **không tự biến wrapper thành async-aware hoàn chỉnh**: vòng đời async vẫn phải được thiết kế/kiểm thử riêng.

### 6.3. Dữ liệu nào nên giữ lại?

Mặc định tốt nhất là **không ghi raw body**. Ngay cả 1024 byte đầu cũng có thể chứa password, email, bearer token, thông tin y tế hoặc bí mật. Thường chỉ nên log metadata:

```text
method=POST path=/api/echo status=200
request_cached_bytes=5
response_observed_bytes=10
response_preview_bytes=10
duration_ms=...
```

Nếu thực sự cần body sample khi debug có kiểm soát: allowlist endpoint/media type, redaction trước khi phát ra log, ẩn fields nhạy cảm, tránh multipart/binary, giới hạn retention và phân quyền truy cập log. Hex/Base64 **không phải** che giấu dữ liệu; chỉ đổi cách biểu diễn.

## 7. Ví dụ chạy được: request cache giới hạn + response tee giới hạn

Ví dụ sau là **Spring Boot 3.x + Spring Web, Java 17+**, đồng bộ (synchronous request handlers). Mỗi request chỉ giữ **tối đa 1024 byte bản sao phía request** và **1024 byte bản sao phía response** ở lớp quan sát. Response được forward **trực tiếp** xuống container thay vì gom toàn bộ trước khi gửi. Không cần database, security hay AOP.

Đây là minh họa cho **giới hạn bộ nhớ của observability wrapper**, **không phải hard limit HTTP payload**, và **không tối ưu cho async/SSE**. Các endpoint demo cố tình dùng body nhỏ hoặc String lớn để quan sát khác biệt.

### 7.1. Tạo project

Từ [Spring Initializr](https://start.spring.io/), chọn Maven, Java 17+, Spring Boot 3.x và dependency **Spring Web**. Phần dependency tương ứng:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

Đây là fragment của `pom.xml`; project Initializr cung cấp parent/BOM, Spring Boot plugin và metadata. Đặt **tất cả source** trong một file `src/main/java/com/example/demo/DemoApplication.java`. File chỉ có một public top-level class; những lớp còn lại package-private là hợp lệ trong Java 17.

### 7.2. Mã Java đầy đủ

```java
package com.example.demo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@Component
class BoundedBodyObservationFilter extends OncePerRequestFilter {

    private static final int PREVIEW_LIMIT = 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI()
                .substring(request.getContextPath().length());
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        // Chỉ cache phần request body mà downstream thật sự đọc.
        ContentCachingRequestWrapper req =
                new ContentCachingRequestWrapper(request, PREVIEW_LIMIT);

        // Không dùng ContentCachingResponseWrapper vì nó có thể
        // giữ toàn bộ response body trong RAM.
        BoundedResponseWrapper resp =
                new BoundedResponseWrapper(response, PREVIEW_LIMIT);

        long start = System.nanoTime();
        try {
            chain.doFilter(req, resp);
        } finally {
            // Demo chỉ công bố kết quả hoàn chỉnh cho sync request.
            if (!req.isAsyncStarted()) {
                resp.flushObservedWriter();
                long elapsedNanos = System.nanoTime() - start;

                // Không log raw request/response body.
                System.out.println(
                        "OBS " + req.getMethod() + " "
                        + req.getRequestURI()
                        + " status=" + resp.getStatus()
                        + " reqPreviewBytes="
                        + req.getContentAsByteArray().length
                        + " respPreviewBytes=" + resp.previewSize()
                        + " respObservedBytes=" + resp.observedBytes()
                        + " elapsedMs=" + elapsedNanos / 1_000_000L);
            } else {
                // Với async: dữ liệu có thể tiếp tục được ghi về sau.
                // Demo không tuyên bố số byte ở đây là tổng cuối.
                System.out.println("OBS async: completion not observed");
            }
        }
    }
}

class BoundedResponseWrapper extends HttpServletResponseWrapper {

    private final int limit;
    private final ByteArrayOutputStream preview;
    private long observed;

    private ServletOutputStream output;
    private PrintWriter writer;
    private boolean outputMode;
    private boolean writerMode;

    BoundedResponseWrapper(HttpServletResponse response, int limit) {
        super(response);
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        this.limit = limit;
        this.preview = new ByteArrayOutputStream(limit);
    }

    private void remember(int oneByte) {
        observed++;
        if (preview.size() < limit) {
            preview.write(oneByte);
        }
    }

    private void remember(byte[] bytes, int off, int len) {
        observed += len;
        int canKeep = Math.min(limit - preview.size(), len);
        if (canKeep > 0) {
            preview.write(bytes, off, canKeep);
        }
    }

    // Một stream được chia sẻ cho output binary và writer text.
    private ServletOutputStream outputDelegate() throws IOException {
        if (output == null) {
            ServletOutputStream target = super.getOutputStream();

            output = new ServletOutputStream() {

                @Override
                public boolean isReady() {
                    return target.isReady();
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    target.setWriteListener(listener);
                }

                @Override
                public void write(int b) throws IOException {
                    target.write(b);
                    remember(b);
                }

                @Override
                public void write(byte[] b, int off, int len)
                        throws IOException {

                    target.write(b, off, len);
                    remember(b, off, len);
                }

                @Override
                public void flush() throws IOException {
                    target.flush();
                }

                @Override
                public void close() throws IOException {
                    target.close();
                }
            };
        }
        return output;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writerMode) {
            throw new IllegalStateException(
                    "getWriter() already selected");
        }
        outputMode = true;
        return outputDelegate();
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (outputMode) {
            throw new IllegalStateException(
                    "getOutputStream() already selected");
        }
        writerMode = true;

        if (writer == null) {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null
                    ? StandardCharsets.ISO_8859_1
                    : Charset.forName(encoding);

            writer = new PrintWriter(
                    new OutputStreamWriter(outputDelegate(), charset));
        }
        return writer;
    }

    // Không đóng output stream; chỉ flush writer của demo sync.
    void flushObservedWriter() {
        if (writer != null) {
            writer.flush();
        }
    }

    int previewSize() {
        return preview.size();
    }

    long observedBytes() {
        return observed;
    }
}

@RestController
class DemoController {

    @PostMapping(
            path = "/api/echo",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> echo(@RequestBody String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "text/plain;charset=UTF-8"))
                .body("echo:" + body);
    }

    @GetMapping(
            path = "/api/large",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String large() {
        // Đây là demo String 10_000 byte ASCII.
        // Không dùng cách tạo String lớn này cho file streaming.
        return "x".repeat(10_000);
    }

    @GetMapping("/api/writer")
    public void writer(HttpServletResponse response)
            throws IOException {

        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("hello writer");
    }
}
```

**QA mã nguồn theo thiết kế:**

- `ContentCachingRequestWrapper(request, 1024)` đặt **cache request tối đa 1024 byte**; wrapper không tự đọc trước body.
- `BoundedResponseWrapper` chỉ giữ **prefix tối đa 1024 byte**, đồng thời có `observed` để đếm số byte được ghi qua wrapper. `ByteArrayOutputStream(limit)` có dung lượng tối đa theo logic `remember(...)`, không tăng theo kích thước response.
- `outputDelegate()` delegate `write(...)` xuống stream gốc **trước** khi ghi vào preview. Response không chờ lưu đủ toàn bộ body vào một array.
- `getWriter()` dùng cùng delegate output và encoding đã xác định; `getOutputStream()` / `getWriter()` loại trừ nhau.
- `writer.flush()` ở finally trong **đường xử lý sync demo** đảm bảo body do writer chuẩn bị được chuyển xuống stream gốc. Với app thực tế phải tuân thủ lifecycle flush/commit/reset, async callbacks, error dispatch và wrapper nesting đầy đủ.
- `setWriteListener`/`isReady` delegate xuống stream gốc, nhưng code **chưa được thiết kế để quan sát async stream hoàn chỉnh**; khi request chuyển async, filter log rõ rằng completion chưa được quan sát.
- Response do `sendError()`/`sendRedirect()` hoặc servlet container tự sinh có thể **đi vòng qua những write() được wrapper quan sát**; counter không phải số byte “trên dây” sau HTTP compression/chunking/TLS.
- `@RequestBody String` vẫn có thể cấp phát bộ nhớ theo kích thước body trong MVC converter. **Giới hạn 1024 byte ở Filter không hạn chế kích thước `String` này.**
- Ví dụ chỉ in metadata, không in raw payload. Trong production tránh log đường dẫn chứa PII/token; với query string và headers phải có redaction.

### 7.3. Chạy ứng dụng

Tại thư mục gốc Spring Initializr:

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows PowerShell hoặc Command Prompt
mvnw.cmd spring-boot:run
```

Spring Boot mặc định dùng `http://localhost:8080` nếu không đổi `server.port`/context path.

### 7.4. HTTP test: request text nhỏ

```bash
curl -i -X POST 'http://localhost:8080/api/echo' \
  -H 'Content-Type: text/plain' \
  --data-binary 'hello'
```

Response quan trọng:

```http
HTTP/1.1 200
Content-Type: text/plain;charset=UTF-8

echo:hello
```

Console có dòng tương tự:

```text
OBS POST /api/echo status=200 reqPreviewBytes=5 respPreviewBytes=10 respObservedBytes=10 elapsedMs=...
```

Giải thích:
- Input `hello` chứa 5 byte ASCII.
- Output `echo:hello` chứa 10 byte ASCII.
- Limit preview 1024 không bị chạm, nên số byte cache/observed bằng kích thước thực của demo này.
- `elapsedMs` phụ thuộc máy/server/GC, không có con số cố định.

### 7.5. HTTP test: response lớn hơn giới hạn preview

```bash
curl -sS -o large.txt http://localhost:8080/api/large
wc -c large.txt
```

Với Unix có `wc`, stdout:

```text
10000 large.txt
```

Console có dòng dạng:

```text
OBS GET /api/large status=200 reqPreviewBytes=0 respPreviewBytes=1024 respObservedBytes=10000 elapsedMs=...
```

Đây là khác biệt mấu chốt: client nhận đủ **10.000 byte**, nhưng wrapper chỉ lưu **1024 byte đầu** trong buffer quan sát. `reqPreviewBytes=0` vì endpoint GET không đọc request body. Nếu OS/tool hiển thị metadata khác, chỉ giá trị số byte của file cần kiểm tra.

### 7.6. HTTP test: ghi qua getWriter()

```bash
curl -i http://localhost:8080/api/writer
```

Body:

```text
hello writer
```

Bản ghi server dạng:

```text
OBS GET /api/writer status=200 reqPreviewBytes=0 respPreviewBytes=12 respObservedBytes=12 elapsedMs=...
```

Chuỗi `hello writer` dài **12 byte ASCII**. Test này giúp phát hiện wrapper chỉ override `getOutputStream()` mà bỏ quên `getWriter()`.

### 7.7. Lệnh cURL trên Windows PowerShell

Bash dùng ký tự `\` để xuống dòng; PowerShell không dùng cùng cú pháp. Hãy chạy một dòng bằng `curl.exe`:

```powershell
curl.exe -i -X POST "http://localhost:8080/api/echo" -H "Content-Type: text/plain" --data-binary "hello"
```

Để kiểm tra file lớn trên Windows, có thể dùng PowerShell:

```powershell
curl.exe -sS -o large.txt http://localhost:8080/api/large
(Get-Item large.txt).Length
```

Giá trị hiển thị là `10000`.

## 8. Giới hạn bộ nhớ quan sát ≠ giới hạn kích thước HTTP request

Có ít nhất bốn lớp tài nguyên có thể tăng theo dữ liệu người dùng:

```text
Client payload
   |
Reverse proxy / ingress (body size / timeout / rate limits)
   |
Servlet container (multipart/form limits, buffers, connection policy)
   |
Filters (bounded preview vs full caching)
   |
MVC converters (JSON object graph, String @RequestBody, byte[] payload)
   |
Controller / service (lists, files, temp objects, database transactions)
```

**Ví dụ:** filter giữ tối đa 1024 byte, nhưng controller nhận `@RequestBody byte[]` với body 200 MiB. Converter vẫn có thể tạo một byte array ~200 MiB, chưa kể copy/overhead. Vì vậy hard payload policy phải được thực thi **trước hoặc trong quá trình tiêu thụ body**, không chỉ trong logging filter.

### 8.1. Content-Length chỉ là kiểm tra nhanh, không đủ

```java
long length = request.getContentLengthLong();
if (length > MAX_ALLOWED_BYTES) {
    response.sendError(413, "Content Too Large");
    return;
}
```

Đây là snippet kiểm tra sớm trong một Filter, **không phải cơ chế hard cap hoàn chỉnh**:
- `Content-Length` có thể không xuất hiện hoặc là `-1`, đặc biệt với chunked encoding.
- Body thực tế sau transfer decoding/compression có thể phức tạp hơn con số header.
- Proxy/container có thể có các quy tắc riêng trước filter.

Để áp **hard cap** cho generic JSON/binary request, phải đếm **byte thực sự được đọc từ input stream** bằng một bounded/counting decorator hoặc chính sách proxy/container tương đương. Khi vượt giới hạn, ngừng đọc và trả 413 ở thời điểm còn có thể ghi response; kiểm soát exception propagation, HTTP connection và trạng thái response đã committed. Test cả requests có/không có `Content-Length`, upload chậm, chunked và concurrent requests. Nếu tự implement ServletInputStream decorator, phải bảo toàn non-blocking semantics.

### 8.2. Ví dụ pattern bounded read trước khi tạo replay buffer

Trong môi trường **đồng bộ** và có hard cap nhỏ, nếu thực sự phải đọc trước rồi replay, tránh:

```java
// Không dùng với dữ liệu không bị giới hạn!
byte[] body = request.getInputStream().readAllBytes();
```

Dùng pattern đọc có giới hạn:

```java
// Pseudocode minh họa quyết định, KHÔNG phải filter đầy đủ.
int limitBytes = 1024 * 1024;
byte[] data = request.getInputStream()
        .readNBytes(limitBytes + 1);

if (data.length > limitBytes) {
    // Trả 413 trước khi cho xử lý tiếp.
    // Không gọi chain.doFilter với stream đã bị tiêu thụ.
} else {
    // Chỉ bọc request bằng replayable wrapper thực sự nếu cần.
    // Wrapper phải triển khai đầy đủ getInputStream/getReader.
}
```

`readNBytes(limit+1)` giữ lượng dữ liệu hữu hạn, giúp phát hiện body vượt giới hạn mà không nạp toàn bộ. Nhưng nó **đã tiêu thụ stream**, nên phải có replay wrapper đúng để downstream đọc các byte hợp lệ. Với body dài hơn limit, có thể cần chính sách đóng/tiếp tục tiêu thụ kết nối của container; không tự giả định mỗi request sau trên cùng connection luôn hoạt động bình thường sau khi từ chối giữa chừng.

### 8.3. Cấu hình upload multipart và HTTP form

Spring Boot 3.x hỗ trợ các property khác nhau cho các đường đọc body:

```properties
# Multipart (file upload); ví dụ chính sách của ứng dụng:
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=6MB
spring.servlet.multipart.file-size-threshold=0B

# Giới hạn form POST do embedded Tomcat xử lý:
server.tomcat.max-http-form-post-size=2MB
```

Ý nghĩa:
- `max-file-size` giới hạn một file trong multipart request.
- `max-request-size` giới hạn tổng multipart request.
- `file-size-threshold=0B` nghĩa là cấu hình multipart có thể spill file contents xuống disk theo cơ chế Servlet multipart; điều này **không** loại trừ disk exhaustion hoặc cần kiểm soát temp files/cleanup.
- `server.tomcat.max-http-form-post-size` liên quan form POST do Tomcat parse, **không** là một universal hard limit cho mọi `application/json`/binary request.

Các property này khác nhau theo Servlet container (Tomcat/Jetty/Undertow), bản Spring Boot và framework parser. Kiểm tra [Spring Boot Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/) và thực hiện integration test cho loại payload thật của ứng dụng.

### 8.4. Upload lớn: stream thay vì giữ byte[]

Với `MultipartFile`, tránh:

```java
byte[] all = file.getBytes(); // có thể tạo mảng lớn trong RAM
```

Nếu thiết kế cần lưu file, dùng `file.getInputStream()` và copy **theo buffer nhỏ** vào storage được kiểm soát, hoặc `file.transferTo(...)` khi phù hợp. Vẫn cần:
- hard file/request size limits;
- quota về disk, số lượng uploads, số parts và thời gian xử lý;
- MIME sniffing/định dạng hợp lệ, không tin tuyệt đối tên file/Content-Type từ client;
- scanning/sandbox thích hợp nếu xử lý nội dung không tin cậy;
- permission checks, path traversal protection và cleanup temporary files.

`MultipartFile` có thể đã được Servlet multipart resolver/container xử lý trước khi controller chạy; đặt một filter sau parser không tự khiến quá trình đó trở thành bounded-memory. Kiểm tra filter order và multipart infrastructure nếu muốn đo phần nào của body.

## 9. So sánh ContentCaching wrappers và tee wrappers

| Tình huống | ContentCachingRequestWrapper | ContentCachingResponseWrapper | Bounded tee wrapper |
| --- | --- | --- | --- |
| Quan sát request body mà handler tự đọc | Rất phù hợp với cache limit dương | Không liên quan | Custom request tee cũng được |
| Cho phép đọc lại request cho converter | **Không tự động** | Không liên quan | Chỉ khi làm replay wrapper riêng |
| Lưu toàn bộ response để tính hash/ETag | Không | Có, với trade-off memory | Không nếu chỉ giữ prefix |
| Quan sát response rất lớn | Không liên quan | Nguy cơ cache toàn bộ | **Ưu tiên bounded tee**, hoặc bỏ body logging |
| Giới hạn request body được phép tải | Không, chỉ giới hạn cache | Không | Preview tee cũng **không**; cần counting hard limit |
| Có thể thay đổi body khi đã đọc? | Không dễ dàng, cần replay/custom | Có thể can thiệp full buffer khi còn sync, nhưng phức tạp | Không chủ đích thay đổi body |
| Phải copy body trở lại response? | Không có `copyBodyToResponse()` | **Có** với pattern wrapper buffering | Không, tee chuyển tiếp ngay |
| Async/SSE/nonblocking | Cần hiểu dispatch/stream lifecycle | Dễ ảnh hưởng streaming | Phải thiết kế async-aware chuyên biệt |

**Không có wrapper chung “đọc lại mọi body, không tốn RAM, vừa streaming, vừa chỉnh nội dung, vừa auto 413”**. Mỗi yêu cầu là một trade-off riêng; xây wrapper ít quyền nhất và đơn giản nhất đáp ứng nhu cầu.

## 10. Đọc body để log có thể gây lỗi nội dung và lỗi bảo mật

### 10.1. Không decode binary thành text tùy ý

Một `application/octet-stream`, ảnh JPEG, zip, PDF hoặc response compressed không thể mặc định đọc thành `new String(bytes, UTF_8)` rồi mong được text có ý nghĩa. Byte prefix còn có thể cắt giữa ký tự UTF-8 multi-byte, khiến preview string có ký tự thay thế `�`.

Để chẩn đoán:
- Ưu tiên log byte count, media type, status và đường dẫn đã làm sạch.
- Chỉ render text preview nếu media type thuộc allowlist và charset đã xác minh.
- Với JSON, cần schema-based redaction; regex loại field nhạy cảm trên substring JSON **không đáng tin** khi chuỗi bị cắt giữa field/escape.
- Hex hoặc Base64 chỉ chuyển encoding; **không** tự che dữ liệu nhạy cảm.

### 10.2. Authorization và dữ liệu nhạy cảm

Filter có thể chạy trước khi authentication hoàn tất, nên log raw request headers/body có thể chứa cookie, bearer token, password, API keys và dữ liệu nhạy cảm. Với lỗi 401/403, càng cần chính sách redaction. Không lưu body cho endpoint login/payment/health records chỉ vì debug dễ hơn.

Nếu dùng Spring Security:
- Xem thứ tự filter và `SecurityFilterChain`.
- Tránh log principal/header raw khi chưa biết nguồn tin cậy.
- Dùng correlation ID không chứa bí mật thay vì dump payload.
- Kiểm tra log retention, encryption at rest, access control và môi trường bật sample.

### 10.3. Nén, transform và bytes-on-wire

Response wrapper đếm những byte được ghi qua chính wrapper. Nếu một filter/container phía ngoài nén gzip/Brotli, HTTP chunked encoding, TLS encryption hoặc proxy transform, số byte trên mạng có thể **khác**. Không dùng counter của tee wrapper làm số byte wire-level chính xác nếu chưa biết vị trí trong filter chain và transfer encoding.

Ngoài ra, `HEAD`/`304 Not Modified` không có response body theo semantics HTTP. Một wrapper ghi header/body theo cách không đúng có thể phá các điều kiện này. Với HTTP caching/ETag/streaming, cân nhắc dùng các khả năng sẵn có của framework.

## 11. Async, error dispatch và response lifecycle

### 11.1. OncePerRequestFilter không bảo đảm một lần cho toàn vòng đời async

`OncePerRequestFilter` hỗ trợ lọc trong dispatch của Servlet request, có hook như:
- `shouldNotFilter(HttpServletRequest)`.
- `shouldNotFilterAsyncDispatch()`.
- `shouldNotFilterErrorDispatch()`.

Với `Callable`, `DeferredResult`, `StreamingResponseBody` hoặc SSE, request có thể chuyển sang async và response tiếp tục được ghi **sau khi `chain.doFilter()` đầu tiên trả về**. Bởi vậy đoạn:

```java
chain.doFilter(requestWrapper, responseWrapper);
log(responseWrapper.getContentAsByteArray());
```

có thể thấy **body chưa hoàn chỉnh** hoặc xung đột async lifecycle. Demo ở mục 7 chủ động chỉ công bố log cuối cho synchronous handlers.

Nếu cần production support async:
1. Đăng ký wrapper/filters với dispatcher types phù hợp.
2. Xem xét `AsyncListener` (`onComplete`, `onError`, `onTimeout`, `onStartAsync`), hoặc tầng tracing tương ứng, để đo completion thật.
3. Không flush/close Servlet stream sai thread/thời điểm.
4. Giữ preview bounded và thread-safety nếu callback đồng thời.
5. Test async, lỗi, cancel, timeout và nested wrappers.
6. Không dùng ThreadLocal/MDC qua async thread mà thiếu propagation/cleanup.

### 11.2. Error dispatch và sendError

Exception xảy ra trong Filter **trước DispatcherServlet** không mặc định được `@RestControllerAdvice` bắt. Servlet container có thể mở ERROR dispatch tới error controller; filter có thể chạy lại nếu đăng ký `DispatcherType.ERROR`. `sendError(...)`/`sendRedirect(...)` do container xử lý cũng không nhất thiết đi qua `ServletOutputStream.write(...)` của wrapper.

Do đó preview/count là **quan sát trong phạm vi wrapper**, không phải toàn bộ raw HTTP response sinh bởi container, error pages và network stack.

### 11.3. Flush, committed status và Content-Length

Nếu response đã committed, không được tùy tiện reset body hay thay status/header. Với wrapper ghi trực tiếp xuống container (tee), bạn không thể quay lại sửa bytes đã gửi. Nếu muốn chỉnh JSON output, dùng `ResponseBodyAdvice` hoặc custom `HttpMessageConverter` trong phạm vi kiểm soát; nếu muốn rewrite HTML/file body toàn phần, phải chấp nhận buffering có giới hạn hoặc streaming transformation phức tạp.

Không tự set `Content-Length` bằng số **ký tự** Java của một String: HTTP length tính bằng **byte** theo encoding. Với response text UTF-8 có tiếng Việt, `String.length()` thường khác số bytes thực.

## 12. Tài nguyên và bộ nhớ trong server thật

Giả sử mỗi request giữ preview 1 KiB mỗi chiều (chưa tính wrapper object/metadata):
- 1 request: tối đa **2 KiB dữ liệu xem trước**.
- 1.000 requests đồng thời đang sống: tối đa khoảng **2 MiB nội dung preview**.
- Nếu giữ toàn response 10 MiB/request trong `ContentCachingResponseWrapper`, 1.000 requests có thể cần khoảng **10 GiB** chỉ cho cached body, chưa tính copies, serializer, object graph, container buffer và JVM overhead.

Đây là phép tính **dung lượng dữ liệu thô**, không phải đo heap chính xác. `ByteArrayOutputStream` có initial capacity, overhead, array allocation; `getContentAsByteArray()` có thể tạo copy; logging formatter/queue có thể giữ thêm dữ liệu. Với high concurrency, giảm preview, sampling và các buffer khác sẽ giúp hơn chỉ tối ưu một class.

Các giới hạn cần tính chung:
- **Request concurrency:** thread pool, queue và kết nối mở.
- **Request body:** proxy/container/codec/parser hard cap và thời gian đọc.
- **Output generation:** tránh xây một String/byte[] quá lớn; sử dụng streaming/resource khi thích hợp.
- **Uploads:** multipart thresholds, disk quota, cleanup và antivirus scanning.
- **Logs:** batch/async appender queue, message truncation, sampling và retention.
- **Heap:** giữ đủ dư địa cho GC, converter object graph, caches, HTTP session và service data.

## 13. Những lỗi thường gặp

| Triệu chứng | Lý do | Cách sửa |
| --- | --- | --- |
| `@RequestBody` rỗng sau Filter logging | Filter đọc stream trước | Chỉ cache-on-read hoặc replay wrapper có hard cap |
| Request cache rỗng dù body có gửi | Downstream không tiêu thụ body hoặc wrapper tạo sai vị trí | Cache phản ánh **bytes đã đọc**, không đọc trước |
| Request quá lớn vẫn được controller nhận | `contentCacheLimit` chỉ giới hạn cache | Enforce hard body size ở ingress/container/counting layer |
| Response client trống khi dùng caching wrapper | Quên `copyBodyToResponse()` | Copy body trong đúng sync lifecycle hoặc chọn tee |
| Response lớn gây OOM | `ContentCachingResponseWrapper` giữ toàn body | Bounded tee/không log body, streaming output |
| Ghi được binary nhưng không bắt text | Wrapper chỉ override `getOutputStream()` | Hỗ trợ cả `getWriter()` và đúng charset |
| Servlet báo không thể gọi `getWriter/getOutputStream` | Chuyển qua lại hai mode | Tôn trọng mode exclusivity |
| UTF-8 preview có ký tự lỗi | Cắt byte giữa code point | Dùng metadata hoặc decode phần đủ ký tự có kiểm tra |
| PostHandle không đọc được response body | Body có thể đã gửi bởi converter | Dùng response wrapper ở Filter hoặc `ResponseBodyAdvice` |
| Response bị sai Content-Length | Ghi thêm/bớt bytes sau khi set length | Để container/converter quản lý, hoặc tính lại theo byte chính xác |
| Async response log thiếu cuối | Filter return trước async completion | AsyncListener/appropriate async-aware design |
| Body log làm lộ token/password | Không whitelist/redact | Metadata-only, allowlist, bảo vệ log |
| Log 1 MiB/request gây tăng heap | Preview limit quá rộng, concurrency cao | Giới hạn vài KiB, sampling |
| Filter chạy hai lần | Double registration/ERROR dispatch | Kiểm tra mappings/order và OncePerRequestFilter |
| 413 không xảy ra khi chunked | Chỉ tin Content-Length | Count actual bytes, enforce proxy/container |
| Log bytes không khớp Content-Length | Compression/transfer coding/partial content | Hiểu vị trí wrapper và wire encoding |

## 14. Checklist review cho request/response wrapping

1. Mục tiêu là **quan sát**, **thay đổi body**, hay **enforce payload limit**? Có tách thành các trách nhiệm riêng chưa?
2. Có giữ **tối đa N byte** cho mỗi request/response không, hay đang `readAllBytes()` / buffer toàn response?
3. Có bỏ quên `getReader()` hoặc `getWriter()` không?
4. Nếu dùng `ContentCachingRequestWrapper`, đã hiểu cache chỉ có bytes được downstream đọc và **không cho replay toàn body** chưa?
5. Nếu dùng `ContentCachingResponseWrapper`, đã đảm bảo `copyBodyToResponse()` và kiểm soát full buffering chưa?
6. Nếu dùng custom tee, đã delegate mọi write/flush/ready/listener operation quan trọng và xử lý writer encoding đúng chưa?
7. Với async/error dispatch, wrapper có lifecycle riêng và có test timeout/error/complete không?
8. Có data redaction, audit access, sampling, sampling limits, retention và log queue limits chưa?
9. Hard body limit có dựa vào **actual bytes** và xử lý khi thiếu Content-Length không?
10. Multipart uploads/file streaming có cách xử lý riêng không?
11. Memory model đã tính đồng thời converter body, object graph, temp files, copies và concurrent requests chưa?
12. Các test đã kiểm chứng client nhận **đầy đủ body** dù chỉ cache một preview ngắn chưa?

## 15. Tự kiểm tra

**Câu 1.** `ContentCachingRequestWrapper(req, 1024)` có tự trả HTTP 413 nếu request body 5 MiB?

**Đáp án:** Không. Limit này giới hạn **cache** chứ không chặn downstream đọc body. Cần hard body limit độc lập.

**Câu 2.** Vì sao không gọi `request.getInputStream().readAllBytes()` trong Filter để log rồi tiếp tục `chain.doFilter`?

**Đáp án:** Nó vừa có nguy cơ gom toàn body trong heap, vừa tiêu thụ stream mà `@RequestBody` cần. Nếu phải đọc trước thì cần hard cap và replay wrapper thực sự; nếu chỉ quan sát thì dùng cache-on-read/tee.

**Câu 3.** Với `ContentCachingResponseWrapper`, tại sao phải gọi `copyBodyToResponse()`?

**Đáp án:** Nó giữ response body trong buffer; cần copy các bytes đã cache xuống response gốc trong sync lifecycle thông thường để client nhận được nội dung.

**Câu 4.** Nếu ứng dụng trả file 50 MiB và chỉ muốn đếm bytes + lưu 1 KiB đầu, có nên dùng ContentCachingResponseWrapper không?

**Đáp án:** Không lý tưởng; nó có thể giữ toàn file trong RAM. Dùng bounded tee hoặc không ghi body, và chọn streaming/resource phù hợp.

**Câu 5.** Request gửi `Transfer-Encoding: chunked`, không có Content-Length. Kiểm tra `getContentLengthLong()` có tạo hard limit không?

**Đáp án:** Không. Phải có chính sách ở proxy/container hoặc đếm bytes thực đọc, kể cả trường hợp không có length header.

**Câu 6.** Vì sao body preview có thể khiến log lộ bí mật dù chỉ dài 1024 byte?

**Đáp án:** Password, token, email hoặc thông tin riêng tư có thể xuất hiện ngay đầu body. Mặc định chỉ log metadata; body sample cần allowlist/redaction/retention/access control.

**Câu 7.** Khi response async còn ghi sau lúc `chain.doFilter()` return, log byte count trong Filter có đầy đủ không?

**Đáp án:** Không bảo đảm. Cần async-aware lifecycle và callback completion để chốt metrics.

## 16. Tài liệu chính thức

- [Spring Framework 6.2 — ContentCachingRequestWrapper Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/util/ContentCachingRequestWrapper.html): giới hạn cache, chỉ cache bytes đã đọc và `handleContentOverflow`.
- [Spring Framework 6.2 — ContentCachingResponseWrapper Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/util/ContentCachingResponseWrapper.html): cache response body, `getContentAsByteArray()`, `copyBodyToResponse()`.
- [Spring Framework — Servlet Filters](https://docs.spring.io/spring-framework/reference/web/webmvc/filters.html): Filter, OncePerRequestFilter, async/error dispatch.
- [Spring Boot 3.5 — Common Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/): `spring.servlet.multipart.*`, Tomcat form limits và các giới hạn container.
- [Spring Boot 3.5 — MultipartProperties Javadoc](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/autoconfigure/web/servlet/MultipartProperties.html): file/request size và threshold.
- [Jakarta Servlet API — HttpServletRequestWrapper](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/http/httpservletrequestwrapper): delegate/override request behavior.
- [Jakarta Servlet API — HttpServletResponseWrapper](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/http/httpservletresponsewrapper): delegate/override response behavior.
- [Spring Framework — HTTP Message Conversion](https://docs.spring.io/spring-framework/reference/web/webmvc/message-converters.html): cách body đi từ Servlet input stream thành controller argument và từ return value ra output stream.

**Kết luận:** để quan sát request, `ContentCachingRequestWrapper` có limit dương là lựa chọn tốt nếu chỉ cần các bytes mà downstream đã đọc. Để quan sát response lớn, tránh dùng `ContentCachingResponseWrapper` như một logger mặc định vì có thể buffer toàn body; dùng **bounded streaming tee** hoặc chỉ log metadata. Bất kỳ preview limit nào cũng **không thay thế hard HTTP payload limit**, memory policy của converter/service, kiểm soát uploads, async lifecycle hoặc bảo mật log.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="filters-interceptors-008">Chọn đúng điểm mở rộng cho ghi log, đo thời gian và truyền mã đối chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Spring MVC — Chọn đúng điểm mở rộng cho ghi log, đo thời gian và truyền mã đối chiếu

## 1. Mục tiêu

Một ứng dụng Spring MVC cần quan sát request: ai gọi endpoint nào, có lỗi không, mất bao lâu, và log nào thuộc cùng một request. Nhưng **ghi log**, **đo thời gian** và **truyền mã đối chiếu** không nhất thiết nằm cùng một điểm mở rộng.

Nếu đặt mọi logic vào `HandlerInterceptor`, bạn có thể bỏ sót request bị từ chối trước khi tới controller. Nếu đặt mọi logic vào Servlet `Filter`, bạn chưa chắc biết **route template** như `/api/orders/{id}`, hoặc đo nhầm thời gian của một request bất đồng bộ. Nếu tự ghi timer và trace cho mọi request trong khi Spring Boot/Micrometer đã có instrumentation, bạn có thể tạo metrics trùng và log khó đối chiếu.

Sau bài học, bạn có thể:

- Phân biệt **Servlet Filter / OncePerRequestFilter**, **HandlerInterceptor / AsyncHandlerInterceptor**, **@ControllerAdvice / ResponseBodyAdvice**, và **Micrometer Observation/Tracing**.
- Chọn đúng tầng cho **request ID**, **MDC**, **access log**, **handler timing**, **HTTP metrics**, **tracing**, bảo mật và response customization.
- Giải thích thứ tự gọi filter → DispatcherServlet → HandlerMapping/interceptor → controller → render/serialize → filter, và vòng đời khi có async/error dispatch.
- Tạo mã đối chiếu `X-Request-ID` an toàn, dùng chung giữa response header, request attributes, MDC và application logs; không tin vô điều kiện ID do client cung cấp.
- Đo thời gian bằng `System.nanoTime()`, phân biệt thời gian **filter dispatch**, **handler**, **HTTP request tổng thể**, và thời gian service/downstream.
- Tránh rò dữ liệu khi log headers/query/body; hiểu caching wrappers và giới hạn logging request/response payload.
- Viết ví dụ runnable bằng **Java 17+, Spring Boot 3.5.0 / Spring Framework 6.2**, kèm MockMvc tests.

Bối cảnh là **Spring Web MVC trên Servlet stack**, không phải WebFlux. Ví dụ có minh họa các công cụ quan sát phổ biến và một ứng dụng nhỏ có thể chạy; các fragment riêng lẻ được ghi chú khi chưa phải class hoàn chỉnh.

## 2. Bảng chọn điểm mở rộng theo mục tiêu

| Việc cần làm | Điểm mở rộng nên cân nhắc | Vì sao | Lưu ý |
| --- | --- | --- | --- |
| Sinh/kiểm tra mã đối chiếu từ đầu request, gắn header response | Servlet `Filter` / `OncePerRequestFilter`, hoặc trusted gateway | Chạy trước DispatcherServlet/handler; bao phủ cả request chưa tới controller | Phải xử lý filter ordering, ASYNC/ERROR dispatch, cleanup MDC |
| Thêm request ID vào log hiện tại | Servlet Filter + SLF4J `MDC` | Cần đặt sớm và xóa chắc chắn trong `finally` | MDC thường dựa vào ThreadLocal, không tự qua worker mới |
| Log method/status/thời gian ở ranh giới Servlet | Filter hoặc container access log | Biết request/response, kể cả lỗi trước MVC | Với async, `chain.doFilter` kết thúc sớm, không đồng nghĩa HTTP response đã xong |
| Log tên handler và URI **template** | `HandlerInterceptor` | Được chạy sau handler mapping, biết route/handler | Không bao phủ mọi thứ trước mapping/filter |
| Đo thời gian riêng một handler MVC | `HandlerInterceptor` + `System.nanoTime()` | Có `preHandle`, `afterCompletion`, route pattern | Cần xét `AsyncHandlerInterceptor` và async lifecycle |
| Đo latency HTTP, số lần gọi, phân nhóm status, traces | **Spring Boot Actuator + Micrometer Observation/Tracing** | Có instrumentation và conventions sẵn | Không tự thêm timer trùng; kiểm soát tag cardinality |
| Đo một bước nghiệp vụ/service | `ObservationRegistry`, `Timer` hoặc instrumentation cho service | Phản ánh thời gian DB/business, không phải toàn HTTP | Chọn tên/tag ít biến thể và scope phù hợp |
| Sửa body JSON kết quả controller | `ResponseBodyAdvice` hoặc HTTP message converter | Có thể can thiệp trước serialize response của MVC | Không áp dụng cho mọi lỗi từ filter, file/stream/proxy |
| Chuẩn hóa HTTP exception response | `@RestControllerAdvice` / `@ExceptionHandler` / `ResponseEntityExceptionHandler` | Phân loại lỗi ở web MVC | Không thay thế security filter exception handling |
| Authentication/authorization, CORS, một số security headers | **Spring Security filter chain** và cấu hình security | Bảo vệ sớm, bao phủ trước controller | Không dùng interceptor làm lớp security chính |
| Access log do reverse proxy / servlet container tạo | Proxy access log / container valve | Quan sát kể cả khi Spring app không xử lý được | Dữ liệu header/query phải được redact phù hợp |

**Quy tắc thực hành:** dùng ít công cụ nhất nhưng bao phủ **đúng ranh giới**. Đặt ID ở filter/gateway, handler-aware logs ở interceptor nếu thực sự cần, HTTP metrics/traces ở Micrometer, nghiệp vụ tại service, lỗi ở advice. Không dùng `@ControllerAdvice` để đo request end-to-end hoặc MVC interceptor như một giải pháp security toàn diện.

## 3. Thứ tự các giai đoạn của request

### 3.1. Request đồng bộ

~~~text
Client → Servlet container → Filter A (Request-ID/MDC)
       → Spring Security filter chain (nếu có)
       → DispatcherServlet / HandlerMapping
       → HandlerInterceptor.preHandle
       → HandlerAdapter gọi Controller method
       → HandlerAdapter xử lý return value
          ├─ REST @ResponseBody: HttpMessageConverter ghi body,
          │  response CÓ THỂ đã commit
          └─ View: tạo ModelAndView, CHƯA render
       → HandlerInterceptor.postHandle (khi handler thành công)
       → Render view nếu có
       → HandlerInterceptor.afterCompletion
       → Filter A finally (đo dispatch, dọn MDC)
       → HTTP response
~~~

Đây là **luồng đồng bộ giản lược**, không mô tả mọi exception/forward/async case. Thứ tự filter phụ thuộc registration. Điểm chính cần nhớ: khi `@ResponseBody` hoặc `ResponseEntity` được return-value handler xử lý, JSON có thể **đã được serialize/commit trước `postHandle`**; còn với MVC view thông thường, `postHandle` chạy **trước** view rendering. Vì vậy không đặt header quan trọng ở `postHandle` và kỳ vọng luôn được gửi; chọn Filter ở thời điểm sớm, controller/`ResponseEntity` hoặc `ResponseBodyAdvice` tùy phạm vi.

### 3.2. Filter nằm ngoài MVC

Servlet filter là API của Jakarta Servlet:

~~~java
void doFilter(
        ServletRequest request,
        ServletResponse response,
        FilterChain chain) throws IOException, ServletException;
~~~

Vì filter đứng trong container chain, nó có thể thực hiện công việc trước/sau `chain.doFilter(...)` mà không cần biết controller. Nó vẫn chạy với những request không được MVC map thành handler, tùy URL patterns và dispatcher types đã đăng ký.

**Điểm mạnh:** logging bắt đầu sớm, request ID, header, wrapper/body policy, security integration. **Giới hạn:** có thể chưa biết chính xác route template; trạng thái HTTP và body có thể chưa hoàn tất ở thời điểm filter kết thúc trong async requests.

### 3.3. Interceptor nằm trong MVC

`HandlerInterceptor` được `HandlerMapping` áp dụng cho một handler phù hợp:

~~~java
public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler) throws Exception;

public void postHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        ModelAndView modelAndView) throws Exception;

public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex) throws Exception;
~~~

- `preHandle` trước handler, trả `false` để ngừng handler chain.
- `postHandle` sau xử lý handler đồng bộ, nhưng có thể đã quá muộn để sửa body/header (đặc biệt response JSON).
- `afterCompletion` sau khi MVC hoàn thành request trong trường hợp thông thường, phù hợp cleanup/timing/logging; `ex` không phải lúc nào cũng chứa lỗi thực tế nếu exception đã được resolver xử lý.
- `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE` có thể cung cấp route template như `/api/orders/{id}` sau mapping, hữu ích cho log/metrics ít cardinality.

Interceptor không phải thay thế cho Spring Security: một số request hoặc URL matching/filter paths không qua cùng interceptor chain. Tránh dùng nó để tạo lớp bảo vệ authentication/authorization duy nhất.

## 4. Mã đối chiếu: requestId, traceId và error code

| Tên | Ví dụ | Vai trò |
| --- | --- | --- |
| `X-Request-ID` / `requestId` | `2f7c7b2b-f418-4297-b042-c86f86e76053` | Đối chiếu một request giữa response và log |
| Distributed `traceId` | ID theo W3C trace context | Liên kết nhiều spans qua các service |
| `spanId` | Một span trong trace | Phân biệt từng operation |
| Public error code | `ORDER_NOT_FOUND` | Cho client biết loại lỗi, ổn định qua nhiều requests |
| HTTP status | 400, 404, 409, 500 | Diễn tả kết quả theo HTTP semantics |

**Request ID không phải error code.** Cùng một `ORDER_NOT_FOUND` xuất hiện ở nhiều request có các request IDs khác nhau. Nếu hệ thống đã dùng Micrometer Tracing/OpenTelemetry, không tự suy ra `traceId == requestId`; một trace có thể chứa nhiều requests qua nhiều services.

### 4.1. Sinh ID an toàn

Ví dụ cho ứng dụng độc lập:

~~~java
String requestId = UUID.randomUUID().toString();
request.setAttribute("requestId", requestId);
response.setHeader("X-Request-ID", requestId);
~~~

Lưu ID trong request attribute giúp các thành phần ở **cùng HTTP request** truy cập nó mà không chỉ phụ thuộc vào MDC. Đặt header trước khi response có thể bị commit; đừng đợi `afterCompletion` mới thêm header.

Không trực tiếp tin `X-Request-ID` từ bất kỳ client nào. Header do client gửi có thể:

- Quá dài, chứa ký tự không phù hợp/log-injection.
- Trùng ID của người khác, gây spoofing và khó đối chiếu.
- Mang dữ liệu nhạy cảm trong chính giá trị header.

**Chính sách đơn giản:** luôn sinh ID ở server, không sử dụng giá trị client. Trong hệ thống có **trusted gateway**, có thể cho phép gateway cấp ID và backend kiểm tra độ dài, charset, nguồn trust/forwarded headers. Tránh tự tạo thêm một request ID mỗi tầng nếu bạn đang chủ ý theo dõi cùng một edge request.

### 4.2. MDC cho log

SLF4J MDC lưu context theo thread của logging backend (thường ThreadLocal):

~~~java
String previous = MDC.get("requestId");
MDC.put("requestId", requestId);
try {
    chain.doFilter(request, response);
} finally {
    if (previous == null) MDC.remove("requestId");
    else MDC.put("requestId", previous);
}
~~~

Restore giá trị cũ giúp an toàn khi có scope lồng nhau. Không đặt token, password, cookie, email nhạy cảm vào MDC vì phần lớn log appenders có thể in mọi MDC keys. MDC trong servlet thread **không tự propagate** sang `@Async`, `CompletableFuture`, scheduled job hay thread pool khác.

### 4.3. Async context propagation

Nếu cần request ID trong `@Async`, bạn có thể:

- Truyền `requestId` **tường minh** vào service/job, rồi đặt MDC trong scope cần log.
- Dùng `TaskDecorator` trên đúng executor: capture MDC lúc **submit**, install trên worker, restore trong `finally`.
- Dùng Micrometer Context Propagation/`ContextPropagatingTaskDecorator` nếu hệ thống có observation/tracing context và accessor được đăng ký phù hợp.

Ví dụ fragment về scope đơn giản:

~~~java
String requestId = (String) request.getAttribute("requestId");

executor.execute(() -> {
    String previous = MDC.get("requestId");
    try {
        MDC.put("requestId", requestId);
        service.execute();
    } finally {
        if (previous == null) MDC.remove("requestId");
        else MDC.put("requestId", previous);
    }
});
~~~

Đây là fragment; `executor` và `service` phải được cấu hình thực tế. Với background job kéo dài sau request, nên gửi **immutable business context** cho job, không giữ tham chiếu `HttpServletRequest` hay `ServletRequestAttributes` qua thời gian dài.

## 5. Chọn nơi ghi log

### 5.1. Những thông tin đáng log

Nên dùng log có cấu trúc hoặc key-value:

~~~text
event=http_request_completed
requestId=2f7c7b2b-f418-4297-b042-c86f86e76053
method=GET route=/api/orders/{id} status=200 durationMs=12.4
~~~

`route` nên là template, không phải path có ID thực, query raw hay token. Nếu MVC chưa chọn handler, filter không biết template, có thể log method + status + duration + requestId mà không cần raw URL. Nếu cần tuyến đường từ reverse proxy, áp dụng allowlist/redaction.

**Không log mặc định:** `Authorization`, `Cookie`, `Set-Cookie`, password, access token, raw request/response body, `?resetToken=...`, SQL credentials hay PII. Với một số nghiệp vụ có yêu cầu audit, tách audit log có chính sách bảo vệ/retention và quyền truy cập riêng.

### 5.2. Chọn filter cho dispatch log

~~~java
long started = System.nanoTime();
try {
    chain.doFilter(request, response);
} finally {
    long elapsed = System.nanoTime() - started;
    log.info("dispatch finished status={} elapsedMs={}",
             response.getStatus(), elapsed / 1_000_000.0);
}
~~~

Dùng `System.nanoTime()` (monotonic clock) để đo khoảng thời gian; không trừ `System.currentTimeMillis()` nếu cần elapsed chính xác, vì wall clock có thể thay đổi.

**Ranh giới quan trọng:** với Servlet async, `chain.doFilter` trên initial REQUEST dispatch có thể trả về ngay sau khi MVC **bắt đầu** async, trước khi kết quả sẵn sàng. Vì vậy phép đo trên là **dispatch duration**, không chắc là end-to-end HTTP latency.

### 5.3. Chọn interceptor khi cần route template

Sau khi handler mapping thành công:

~~~java
Object route =
        request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
String routePattern = route instanceof String s ? s : "unmapped";
~~~

Nếu dùng method handler names hoặc business route metadata thì interceptor có vị trí thuận lợi hơn. Đối với metric labels, dùng `routePattern` có số lượng hữu hạn, không dùng từng URL cụ thể (ví dụ `/users/1234`, `/users/1235` gây cardinality cao).

**Cẩn thận:** URI template attribute có thể thay đổi/tồn tại khác nhau theo handler mapping; luôn có fallback. Interceptor không xử lý mọi request bị từ chối trước mapping; đừng dùng một interceptor đơn lẻ làm access log toàn diện.

## 6. Đo thời gian: đo cái gì?

Một request có thể gồm các thành phần:

~~~text
Client
  -> proxy / network
  -> Servlet Filter + Spring Security
  -> MVC mapping + interceptor
  -> controller
  -> service + database/downstream
  -> JSON serialization / view render
  -> response output
  -> client
~~~

Các phép đo khác nhau có ý nghĩa khác nhau:

| Phép đo | Thường bắt đầu/kết thúc ở đâu | Có tính network bên ngoài app? |
| --- | --- | --- |
| Gateway latency | Proxy nhận đến lúc proxy hoàn tất response | Có thể tính nhiều phần mạng/proxy |
| Server HTTP latency | HTTP server/framework observation lifecycle | Không phải toàn network client |
| Filter dispatch duration | Trước/sau `chain.doFilter` của **một dispatch** | Không; có thể bỏ phần async chờ |
| MVC handler elapsed | `preHandle` đến completion theo lifecycle | Không tính giai đoạn trước handler mapping |
| Service duration | Bắt đầu/kết thúc service operation | Chỉ business/service |
| Database/client span duration | Tới lúc dependency call kết thúc | Tùy instrumentation, không phải toàn request |

Không gọi "filter time = controller time" hay "service time = HTTP time". Đừng tính elapsed bằng cách cộng nhiều timers lồng nhau, vì chúng chồng lấp; ví dụ HTTP span đã bao gồm service span.

### 6.1. Đo MVC bằng interceptor

Ví dụ:

~~~java
public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response,
        Object handler) {
    if (request.getAttribute(START_KEY) == null) {
        request.setAttribute(START_KEY, System.nanoTime());
    }
    return true;
}

public void afterCompletion(
        HttpServletRequest request, HttpServletResponse response,
        Object handler, Exception ex) {
    Long start = (Long) request.getAttribute(START_KEY);
    if (start != null) {
        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
        log.info("mvc_handler_complete status={} elapsedMs={}",
                 response.getStatus(), elapsedMs);
    }
}
~~~

Đây là fragment — `START_KEY`, `log` và class bao quanh phải được định nghĩa. Điểm cần chú ý ở request async: `preHandle` có thể gọi lần nữa khi ASYNC dispatch, vì vậy chỉ set start khi chưa tồn tại để không reset đồng hồ.

### 6.2. AsyncHandlerInterceptor và ASYNC dispatch

Spring MVC hỗ trợ:

~~~java
public interface AsyncHandlerInterceptor extends HandlerInterceptor {
    default void afterConcurrentHandlingStarted(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {
    }
}
~~~

Trong request bắt đầu async, Servlet thread ban đầu quay lại pool và MVC gọi **`afterConcurrentHandlingStarted` thay cho `postHandle` và `afterCompletion`** ở lượt dispatch ban đầu. Khi kết quả sẵn sàng, container làm ASYNC dispatch và interceptor thường được gọi lại, cuối cùng `afterCompletion` mới chạy.

~~~text
REQUEST dispatch:
   interceptor.preHandle  -> controller returns Callable/DeferredResult
   interceptor.afterConcurrentHandlingStarted
   Servlet thread released; response chưa nhất thiết hoàn tất

Worker / callback:
   Tạo async result

ASYNC dispatch:
   interceptor.preHandle (lần nữa)
   MVC xử lý result
   interceptor.postHandle / afterCompletion
~~~

Một số trường hợp timeout, lỗi mạng hoặc async completion không qua redispatch theo dự kiến có thể khiến `afterCompletion` **không xảy ra**; nếu cần tổng thời gian chính xác và callback cleanup, dùng `CallableProcessingInterceptor`, `DeferredResultProcessingInterceptor`, `DeferredResult.onTimeout/onCompletion` hoặc Servlet `AsyncListener` theo trường hợp.

**Không đặt cleanup ThreadLocal duy nhất vào `afterCompletion` khi async đã bắt đầu:** Servlet thread ban đầu có thể được tái sử dụng trước đó. Lúc `afterConcurrentHandlingStarted` có thể cần cleanup những thread-bound attributes do interceptor tự thiết lập.

### 6.3. Filter và dispatcher types

Servlet dispatch có các loại như `REQUEST`, `ASYNC` và `ERROR`. `OncePerRequestFilter` có các hook:

~~~java
protected boolean shouldNotFilterAsyncDispatch();
protected boolean shouldNotFilterErrorDispatch();
~~~

Ý nghĩa:

- "Once" là một lần theo **dispatch** và chính sách lọc tương ứng, không phải cam kết chạy đúng một lần cho toàn bộ vòng đời HTTP request bất kể async/error.
- Nếu muốn filter góp mặt trong ASYNC/ERROR dispatch, cần cả cấu hình đăng ký filter cho dispatcher type tương ứng **và** chính sách shouldNotFilter phù hợp.
- Khi filter chạy lại trong async redispatch, phải **tái sử dụng** request ID lưu ở request attribute, không sinh ID mới cho cùng HTTP request.
- Nếu filter chỉ chạy ở REQUEST dispatch, MDC trong async redispatch có thể vắng; đó không phải bằng chứng ID đã biến mất khỏi request attribute.

Đừng gộp log hai dispatch thành hai HTTP requests; một HTTP request async có thể có nhiều lần dispatch. Nếu bạn muốn một observation cho toàn bộ HTTP lifecycle, ưu tiên Micrometer instrumentation được framework hỗ trợ.

## 7. Micrometer Observation và Tracing: khi không cần tự viết timer

Spring Boot Actuator tích hợp Micrometer Observation cho **metrics, logs correlation và tracing** theo các dependency/instrumentation đã cấu hình. Với Boot 3.5 có thể bật cơ chế HTTP server observation/metrics bằng actuator và những registry/exporters phù hợp.

Thêm dependency:

~~~xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
~~~

Khi thích hợp, framework có thể tạo observations cho HTTP requests. Sử dụng conventions/tags có sẵn như method, status, URI template để phân tích thay vì thêm interceptor bấm giờ nếu nhu cầu chỉ là **HTTP latency dashboard**.

**Không lạm dụng metric tags**:

- Tags **low-cardinality** có số biến thể hữu hạn (HTTP method, route template, status, outcome).
- Không gắn user ID, email, request ID, raw URL hay token vào metric tags; những giá trị này tạo cardinality rất cao, tăng chi phí và có thể lộ PII.
- Request ID phù hợp với log/tracing context, **không** phù hợp làm dimension của timer/counter.

### 7.1. ObservationRegistry cho đoạn business

Khi muốn đo một bước nghiệp vụ độc lập:

~~~java
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

public final class PricingService {
    private final ObservationRegistry registry;

    public PricingService(ObservationRegistry registry) {
        this.registry = registry;
    }

    public int computePrice(int quantity) {
        return Observation.createNotStarted("pricing.calculate", registry)
                .lowCardinalityKeyValue("kind", "standard")
                .observe(() -> quantity * 100);
    }
}
~~~

Đây là class minh họa tính toán in-memory, không phải endpoint trong dự án mục 9. `Observation` chỉ meaningful khi có observation handlers/metrics/tracing bridge phù hợp trong ứng dụng. Với Spring Boot auto-instrumented HTTP handlers, tránh gắn thêm `@Observed` cho controller mà không hiểu việc nó có thể tạo observation **trùng**.

### 7.2. Tracing qua các service

Một `traceId` được truyền qua HTTP headers chuẩn (thường W3C `traceparent`) nếu client instrumentation/injection đúng. Khi Service A gọi Service B, custom `MDC.put("requestId",...)` trên A **không tự truyền sang B**. Cần HTTP client instrumentation hoặc propagate một header đã được trusted/validated nếu dùng custom ID.

Với `@Async`/thread pool, tracing context cũng có thể mất nếu không propagate. Spring Boot 3.5 hướng dẫn `ContextPropagatingTaskDecorator` trên executor khi cần Micrometer context propagation.

### 7.3. Không nhầm request ID do mình tạo với tracing chính thức

Bạn có thể giữ `X-Request-ID` cho support đồng thời dùng Micrometer trace/span IDs. Trong log, hai loại có thể cùng xuất hiện. Hãy định nghĩa ownership rõ để tránh sinh thêm nhiều ID cạnh tranh, ghi đè context đã do tracing framework quản lý hoặc phát tán headers không đáng tin cậy.

## 8. Logging request/response body: rủi ro và kỹ thuật

Nhu cầu thường gặp: "Log toàn bộ JSON request/response để debug". Với API public, đây là một trong những chỗ dễ lộ password, token, số điện thoại, thông tin thanh toán, personal data và dữ liệu vận hành.

**Không log body mặc định.** Nếu cần kỹ thuật chẩn đoán, chọn field allowlist, sampling, giới hạn kích thước, redaction, hạn thời gian lưu và kiểm soát truy cập. Một phần log bị rò có thể nguy hiểm tương đương dữ liệu DB bị rò.

### 8.1. ContentCachingRequestWrapper

Spring có:

~~~java
ContentCachingRequestWrapper wrapped =
        new ContentCachingRequestWrapper(request, 16 * 1024);
chain.doFilter(wrapped, response);
byte[] cached = wrapped.getContentAsByteArray();
~~~

Cần import `org.springframework.web.util.ContentCachingRequestWrapper`. Wrapper **chỉ cache những byte đã được downstream đọc**; nó không tự hút toàn bộ body trước khi controller chạy. Nếu body chưa được đọc, cache có thể rỗng. Giới hạn cache và overflow handling cần được đánh giá; không log bytes chưa giải mã đúng charset và không coi cache là bản sao hợp lệ của mọi streaming request.

### 8.2. ContentCachingResponseWrapper

Nếu bọc response:

~~~java
ContentCachingResponseWrapper wrapped =
        new ContentCachingResponseWrapper(response);
chain.doFilter(request, wrapped);
// Chỉ minh họa: nếu thật sự cần inspect bytes, phải có policy redaction.
wrapped.copyBodyToResponse();
~~~

Cần import `org.springframework.web.util.ContentCachingResponseWrapper`. **Phải gọi `copyBodyToResponse()`** để truyền body đã buffer xuống response thật, nếu không client có thể không nhận body. Trong async/streaming, việc copy quá sớm hoặc quá muộn có thể làm hỏng response, tăng RAM, phá SSE/file downloads. Chỉ dùng sau khi hiểu lifecycle; với endpoint JSON bình thường cần điều chỉnh cách dùng khi có async dispatch.

### 8.3. Lựa chọn thay thế

- Log metadata method/status/route/elapsed/request ID thay vì body.
- Bật sampling/structured logging trong tầng observability có redaction.
- Dùng `RequestBodyAdvice`/`ResponseBodyAdvice` chỉ khi thật sự cần kiểm tra kiểu DTO cụ thể; nó không bao phủ toàn bộ filter/security/proxy errors hoặc mọi body types.
- Không đặt raw exception stacktrace và request payload vào public error JSON.

## 9. Dự án chạy được: Filter + Interceptor + safe error response

Ví dụ Spring Boot 3.5.0, Java 17, Maven:

1. Filter sinh request ID, đặt header sớm, lưu request attribute và MDC, ghi **filter dispatch time** rồi restore MDC.
2. Interceptor ghi **handler elapsed** và route template, biết HTTP status sau xử lý MVC.
3. Controller chứng minh handler nhìn thấy cùng ID; advice trả JSON an toàn trong một business conflict.
4. Tests xác nhận ID nhất quán, không chấp nhận giá trị spoof từ client, lỗi 409 và hành vi 405/404 của HTTP boundary.
5. Đã thêm Actuator để minh họa hướng production cho HTTP observations; không bắt buộc bạn phải tự viết timer trùng.

### 9.1. Cấu trúc dự án

~~~text
logging-extension-demo/
├── pom.xml
├── src/main/
│   ├── java/demo/LoggingExtensionApplication.java
│   └── resources/application.properties
└── src/test/java/demo/LoggingExtensionTest.java
~~~

Mọi class Java chính trong ví dụ đặt chung một file để dễ copy/run. Trong dự án thật nên tách từng class ra file riêng.

### 9.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>logging-extension-demo</artifactId>
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
            <artifactId>spring-boot-starter-actuator</artifactId>
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

### 9.3. application.properties

~~~properties
# Chỉ demo hiện log theo request ID; có thể dùng structured logging ở production.
logging.pattern.level=%5p [rid:%X{requestId}]

# Không tự phát stacktrace/exception details từ /error ra client.
server.error.include-stacktrace=never
server.error.include-message=never
server.error.include-binding-errors=never
server.error.include-exception=false

# Actuator mặc định chỉ expose các endpoint an toàn hơn; không mở * bừa.
management.endpoints.web.exposure.include=health,info
~~~

Không cần bật `management.endpoints.web.exposure.include=*` chỉ để xem metrics; quyết định Actuator exposure/authentication theo môi trường. Instrumentation và registry exporter thực tế có thể cần thêm dependencies/cấu hình.

### 9.4. LoggingExtensionApplication.java

Đặt toàn bộ code dưới đây trong `src/main/java/demo/LoggingExtensionApplication.java`. Chỉ có **một** top-level public class trùng tên file; các class còn lại package-private.

~~~java
package demo;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class LoggingExtensionApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoggingExtensionApplication.class, args);
    }
}

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestCorrelationFilter extends OncePerRequestFilter {
    static final String REQUEST_ID_ATTR =
            RequestCorrelationFilter.class.getName() + ".requestId";
    static final String HEADER = "X-Request-ID";

    private static final Logger LOG =
            LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        // Demo: không dùng giá trị X-Request-ID do client tự cung cấp.
        Object existing = request.getAttribute(REQUEST_ID_ATTR);
        String id = existing instanceof String value
                ? value
                : UUID.randomUUID().toString();

        request.setAttribute(REQUEST_ID_ATTR, id);
        response.setHeader(HEADER, id);

        String previousId = MDC.get("requestId");
        long started = System.nanoTime();

        MDC.put("requestId", id);
        try {
            chain.doFilter(request, response);
        } finally {
            double dispatchMs =
                    (System.nanoTime() - started) / 1_000_000.0;

            // Không log query/body/Authorization/raw path.
            LOG.info("filter_dispatch_done method={} status={} dispatchMs={}",
                    request.getMethod(), response.getStatus(), dispatchMs);

            // Restore vì Servlet thread có thể được tái sử dụng.
            if (previousId == null) MDC.remove("requestId");
            else MDC.put("requestId", previousId);
        }
    }
}

@Component
class HandlerTimingInterceptor implements AsyncHandlerInterceptor {
    private static final Logger LOG =
            LoggerFactory.getLogger(HandlerTimingInterceptor.class);

    private static final String START =
            HandlerTimingInterceptor.class.getName() + ".start";

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        // Khi ASYNC dispatch chạy lại preHandle, không reset mốc bắt đầu.
        if (request.getAttribute(START) == null) {
            request.setAttribute(START, System.nanoTime());
        }
        return true;
    }

    @Override
    public void afterConcurrentHandlingStarted(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        // Lượt REQUEST đã khởi động async; response chưa hoàn tất.
        // Không kết luận handler đã xong tại đây.
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {
        Object start = request.getAttribute(START);
        if (!(start instanceof Long started)) return;

        double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;

        Object route = request.getAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String pattern = route instanceof String s ? s : "unmapped";

        // Không log exception.getMessage() hay request body.
        LOG.info("mvc_handler_done route={} status={} handlerMs={}",
                pattern, response.getStatus(), elapsedMs);

        request.removeAttribute(START);
    }
}

@Configuration
class MvcLoggingConfig implements WebMvcConfigurer {
    private final HandlerTimingInterceptor interceptor;

    MvcLoggingConfig(HandlerTimingInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/**");
    }
}

class OrderConflictException extends RuntimeException {
    OrderConflictException() {
        super("Synthetic internal condition: do not expose raw exception");
    }
}

record ErrorDto(
        String code,
        String message,
        String requestId) {
}

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(OrderConflictException.class)
    ResponseEntity<ErrorDto> onConflict(
            OrderConflictException ex,
            HttpServletRequest request) {

        String requestId =
                (String) request.getAttribute(
                        RequestCorrelationFilter.REQUEST_ID_ATTR);

        // Không trả ex.getMessage() ra client.
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorDto(
                        "ORDER_CONFLICT",
                        "Thao tác không hợp lệ trong trạng thái hiện tại.",
                        requestId));
    }
}

@RestController
@RequestMapping("/api")
class OrderController {
    @GetMapping("/orders/{id}")
    Map<String, Object> find(@PathVariable long id) {
        String requestId = MDC.get("requestId");
        // Demo synchronous: MDC được filter thiết lập trên Servlet thread.
        return Map.of("id", id,
                "status", "ok",
                "requestId", requestId);
    }

    @GetMapping("/orders/conflict-demo")
    Map<String, Object> conflict() {
        throw new OrderConflictException();
    }
}
~~~

**Chú ý về thứ tự mapping:** `GET /api/orders/conflict-demo` có mapping literal nên Spring MVC ưu tiên đường dẫn tĩnh đó trước mapping `/api/orders/{id}`. Route template trong interceptor cho endpoint chi tiết sẽ là `/api/orders/{id}`, tránh metric mỗi order id một nhãn.

**Chú ý về thời gian:** trong code, `dispatchMs` là thời gian của filter chain trên current dispatch. Với synchronous request, nó gần với thời gian xử lý HTTP bên trong ứng dụng nhưng không tính network/client rendering. `handlerMs` bắt đầu sau mapping, kết thúc ở MVC afterCompletion; chúng **không được cộng với nhau**. Với async, phần đo filter ban đầu chưa phản ánh toàn bộ chờ async; interceptor dùng START attribute cho đến final ASYNC dispatch, nhưng nếu async timeout/error không redispatch, cần callback chuyên biệt để tránh thiếu số liệu.

### 9.5. Chạy và quan sát logs

Trong thư mục `logging-extension-demo`:

~~~bash
mvn spring-boot:run
~~~

Thử GET:

~~~bash
curl -i 'http://localhost:8080/api/orders/42'
~~~

Ví dụ body:

~~~json
{"id":42,"status":"ok","requestId":"2f7c7b2b-f418-4297-b042-c86f86e76053"}
~~~

Header `X-Request-ID` cùng HTTP response phải **khớp chính xác** body.requestId; UUID khác ở mỗi request. Console logs có dạng gần như:

~~~text
INFO [rid:2f7c7b2b-f418-4297-b042-c86f86e76053]
mvc_handler_done route=/api/orders/{id} status=200 handlerMs=3.12

INFO [rid:2f7c7b2b-f418-4297-b042-c86f86e76053]
filter_dispatch_done method=GET status=200 dispatchMs=8.45
~~~

Số đo/time/id trong ví dụ **chỉ minh họa** và không phải output cố định; environment, JIT, templates và JVM warm-up làm timing thay đổi theo lượt gọi.

### 9.6. Thử lỗi, spoofed header và 405

Giả sử một client tự gửi X-Request-ID:

~~~bash
curl -i 'http://localhost:8080/api/orders/42' \
  -H 'X-Request-ID: attacker-chosen'
~~~

Server sẽ tạo UUID mới thay vì dùng `attacker-chosen`. Điều đó giảm nguy cơ log spoofing/collision từ HTTP client.

Thử domain conflict:

~~~bash
curl -i 'http://localhost:8080/api/orders/conflict-demo'
~~~

HTTP 409, response body tương đương:

~~~json
{
  "code": "ORDER_CONFLICT",
  "message": "Thao tác không hợp lệ trong trạng thái hiện tại.",
  "requestId": "2f7c7b2b-f418-4297-b042-c86f86e76053"
}
~~~

ID cụ thể sẽ khác và bằng response header `X-Request-ID`. Test cũng cần xác nhận không có chuỗi `"Synthetic internal condition"` trong body.

Thử không có POST mapping:

~~~bash
curl -i -X POST 'http://localhost:8080/api/orders/42'
~~~

Framework thường trả **405 Method Not Allowed**. Filter vẫn có thể thêm X-Request-ID cho request này dù không có handler method thực sự được gọi. Vì interceptor không đảm bảo thấy mọi lỗi trước mapping, nên không dùng nó làm access log duy nhất.

### 9.7. MockMvc tests: request ID, status và an toàn phản hồi

Tạo file `src/test/java/demo/LoggingExtensionTest.java`:

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoggingExtensionTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void idMatchesHeaderAndJsonBody() throws Exception {
        MvcResult result = mvc.perform(get("/api/orders/42"))
            .andExpect(status().isOk())
            .andReturn();

        String id = result.getResponse().getHeader("X-Request-ID");
        assertNotNull(id);
        UUID.fromString(id);

        JsonNode body = mapper.readTree(
                result.getResponse().getContentAsString());
        assertEquals(id, body.path("requestId").asText());
        assertEquals(42, body.path("id").asInt());
    }

    @Test
    void ignoresUntrustedClientRequestId() throws Exception {
        MvcResult result = mvc.perform(get("/api/orders/42")
                .header("X-Request-ID", "attacker-chosen"))
            .andExpect(status().isOk())
            .andReturn();

        String id = result.getResponse().getHeader("X-Request-ID");
        assertNotNull(id);
        UUID.fromString(id);
        assertFalse("attacker-chosen".equals(id));
    }

    @Test
    void conflictKeepsHttpStatusAndHidesInternalMessage()
            throws Exception {
        MvcResult result = mvc.perform(
                get("/api/orders/conflict-demo"))
            .andExpect(status().isConflict())
            .andReturn();

        String id = result.getResponse().getHeader("X-Request-ID");
        assertNotNull(id);

        String serialized = result.getResponse().getContentAsString();
        JsonNode body = mapper.readTree(serialized);

        assertEquals("ORDER_CONFLICT", body.path("code").asText());
        assertEquals(id, body.path("requestId").asText());
        assertFalse(serialized.contains("Synthetic internal condition"));
    }

    @Test
    void filterCoversMethodNotAllowedBeforeHandler()
            throws Exception {
        MvcResult result = mvc.perform(post("/api/orders/42"))
            .andExpect(status().isMethodNotAllowed())
            .andReturn();

        assertNotNull(result.getResponse().getHeader("X-Request-ID"));
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

MockMvc tests minh họa phản hồi HTTP và filter registration. Chúng không chứng minh latency thực tế hoặc độ chính xác của metrics khi chạy tải cao. Để kiểm thử logs, dùng in-memory test appender hoặc structured log capture được cách ly khỏi thông tin nhạy cảm; để kiểm thử async, thêm controller có `Callable`/`DeferredResult` và test `asyncDispatch` + timeout callbacks.

## 10. Async và lỗi trước MVC: thiết kế bổ sung

Nếu hệ thống có nhiều async endpoints hoặc streaming, **không chỉ dựa trên ví dụ synchronous**. Một số tình huống:

### 10.1. @Async và CompletableFuture

Method `@Async` chạy trên executor khác; MDC requestId từ filter không tự sang worker. Có thể dùng TaskDecorator:

~~~java
public final class RequestIdTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable task) {
        String requestId = MDC.get("requestId"); // Capture lúc submit.
        return () -> {
            String previous = MDC.get("requestId");
            try {
                if (requestId == null) MDC.remove("requestId");
                else MDC.put("requestId", requestId);
                task.run();
            } finally {
                if (previous == null) MDC.remove("requestId");
                else MDC.put("requestId", previous);
            }
        };
    }
}
~~~

Đây là class Java cần import `org.springframework.core.task.TaskDecorator` và `org.slf4j.MDC`. Đăng ký bằng `ThreadPoolTaskExecutor#setTaskDecorator(...)` trên **đúng executor** chạy task. Không truyền toàn bộ `ServletRequestAttributes` vào job nền sau response.

### 10.2. @ControllerAdvice không bao phủ mọi lỗi

Một `@RestControllerAdvice` phù hợp để map domain exceptions ở controller/service sang HTTP body. Nhưng:

- Spring Security authentication errors có thể được xử lý **trong filter chain**, trước DispatcherServlet. Cấu hình `AuthenticationEntryPoint`/`AccessDeniedHandler` trong Security.
- Một JSON serialization failure có thể xảy ra khi response đã commit; không còn chắc thay body lỗi được.
- Gateway/proxy có thể tự phát 502/503/504 khi upstream unreachable; cấu hình lỗi Boot không kiểm soát được chúng.

Đây là lý do request ID nên được gắn sớm ở filter hoặc gateway, không chỉ trong advice. Nếu proxy cũng gắn ID, cần có hợp đồng về nguồn tin cậy/format/propagation.

### 10.3. Exception trong filter

Nếu filter gọi dependency rồi throw exception trước `DispatcherServlet`, `@RestControllerAdvice` thường không bắt được vì không qua MVC handler invocation. Đừng đặt business logic phức tạp trong filter. Nếu filter cần xử lý lỗi, hãy để container/security error handler hoặc một policy lỗi ở filter boundary xử lý đúng và tránh lộ exception internals.

### 10.4. Thứ tự filter và Security

Khi có `DelegatingFilterProxy`/Spring Security, request-id filter có thể chạy trước hoặc sau security chain tùy order. Nếu muốn ID trên cả 401/403, bạn phải bảo đảm filter bao phủ đường từ chối security **ở ngoài/đủ sớm**, hoặc để trusted gateway tạo ID; kiểm chứng bằng integration tests. Đừng suy ra từ một `@Order` duy nhất rằng mọi registration giữa Boot, servlet container và security đã đúng trong mọi deployment.

### 10.5. Response header và thời điểm ghi

- Đặt `X-Request-ID` ở **đầu filter chain** khi response chưa commit.
- Đặt "X-Elapsed-Ms" trong `afterCompletion` có thể quá muộn: JSON đã được ghi hoặc response đã commit. Nếu buộc expose duration header, cần hook sớm hơn/response wrapper phù hợp và kiến trúc cho async/streaming; cách đơn giản hơn là **không công bố latency header** và dùng metrics/logs.
- Với streaming, SSE, file download, đo "request complete" cần xét lúc stream/connection kết thúc; không dùng thời gian controller method trả về làm duration tổng.

## 11. Những lỗi phổ biến

1. **Dùng interceptor để cấp quyền duy nhất:** request có thể bị xử lý/truy cập qua đường không match interceptor; dùng Spring Security/filter chain.
2. **Dùng filter để log route template khi handler chưa map:** lúc đó filter thường chỉ có raw URL, dễ dính PII/cardinality. Nếu cần handler route, log ở interceptor hoặc instrumentation của MVC.
3. **Đo `chain.doFilter` rồi gọi đó là latency async end-to-end:** Servlet async initial dispatch có thể kết thúc trước request.
4. **Tạo request ID ngay trong controller:** lỗi trước controller có thể không có ID.
5. **Tin `X-Request-ID` từ client không kiểm tra:** dễ collision, injection, spoofing.
6. **Quên `MDC.remove`/restore trong finally:** pooled thread có thể giữ ID request trước.
7. **Cho rằng MDC tự đi vào `@Async`:** cần explicit context hoặc decorator/snapshot.
8. **Đưa request ID vào metric label:** mỗi request một label, cardinality bùng nổ.
9. **Log toàn bộ query/body/headers:** dễ lộ tokens/PII; mặc định dùng allowlist metadata.
10. **Dùng `ContentCachingResponseWrapper` nhưng quên `copyBodyToResponse()`:** client có thể nhận body trống.
11. **Dùng RequestCachingWrapper rồi tưởng nó đọc body sẵn:** cache chỉ chứa bytes downstream đã đọc.
12. **Đặt header ở `postHandle` hoặc `afterCompletion`:** với `@ResponseBody` body có thể đã commit.
13. **Tạo timer interceptor trùng với Micrometer instrumentation:** metrics double-count, tăng chi phí.
14. **Làm timer bằng `System.currentTimeMillis()`:** wall-clock có thể nhảy; dùng `System.nanoTime()`.
15. **Không quan tâm ASYNC/ERROR dispatch và filter registration:** log thành hai requests hoặc mất correlation ID.
16. **Log `exception.getMessage()` từ DB và gửi lại client:** rò hạ tầng; dùng safe error DTO và secure logs.
17. **Để business logic vào filter:** khó test, ảnh hưởng bảo mật/lifecycle và bypass transaction/service boundary.
18. **Không theo dõi exception/timeouts trong async processing:** `afterCompletion` có thể không chạy ở mọi exceptional async path.
19. **Nhầm requestId với traceId:** custom ID không tự tạo distributed spans và không tự đi qua các HTTP service.
20. **Tin rằng một test MockMvc chứng minh production logging đầy đủ:** còn container/gateway/security/CORS/streaming/lifecycle cần integration tests.

## 12. Checklist thực hành triển khai

- **Mục đích rõ:** cần request ID, access logs, handler logs, business timing hay distributed tracing?
- **Ownership:** gateway hay app sinh request ID? Header nào được tin cậy? Ai quản lý MDC?
- **Ranh giới:** chọn Filter trước MVC, Interceptor sau mapping, Advice cho exception, Micrometer cho observations.
- **Timing:** định nghĩa đúng start/end cho request đồng bộ và async; không tự cộng timers lồng nhau.
- **Privacy:** chỉ log field cho phép; không in Authorization, Cookie, raw URL/query/body, secret.
- **Cardinality:** metric tags dùng route template, HTTP method/status/outcome; không dùng user ID/request ID.
- **Lifecycle:** restore MDC cuối dispatch; khi đổi thread dùng context propagation; khi ERROR/ASYNC dispatch kiểm tra registration.
- **Security:** bảo đảm 401/403 có request ID nếu policy yêu cầu; interceptor không là auth layer.
- **Error:** advice không trả stacktrace/SQL; exception filter/serializer/proxy có policy riêng.
- **Overhead:** sampling, log levels, executor queue, instrumentation cost, retention và quyền xem logs.
- **Tests:** GET thành công, 400/404/405, security denial, exception 500, async completion/timeout, nhiều request concurrent; so header ID ↔ body/log và kiểm tra không lộ PII.
- **Operation:** dashboard latency theo p50/p95/p99, tỷ lệ 4xx/5xx, theo route/status, traces cho yêu cầu chậm; biết cách tra log qua request ID.

## 13. Bài tập

**Bài 1 — Chọn điểm mở rộng:** Với từng yêu cầu, chọn Filter, Interceptor, ControllerAdvice, ResponseBodyAdvice hay Micrometer: (a) sinh request ID cho cả 401/403, (b) ghi template `/orders/{id}`, (c) chuẩn hóa lỗi 409, (d) đo HTTP p95 và (e) thêm field tùy chỉnh vào DTO JSON.

**Bài 2 — Timing:** Controller trả `Callable<ResponseEntity<?>>` và mất 1 giây trong worker, initial filter dispatch chỉ chạy 10 ms. Vì sao timer ở finally filter có thể log 10 ms thay vì ~1 giây? Cần dùng callback/instrumentation nào để đo request HTTP hoàn chỉnh?

**Bài 3 — MDC:** Hai requests liên tiếp được xử lý bằng cùng thread. Nếu quên `finally` restore MDC, log request thứ hai có thể xuất hiện ID nào? Viết đoạn capture/restore cẩn thận khi task ném exception.

**Bài 4 — Tracing:** Service A trả HTTP request ID cho client và gọi Service B. Vì sao B không tự thấy cùng traceId? Nêu cơ chế trace context propagation qua HTTP client/gateway và phân biệt custom request ID với distributed trace.

**Bài 5 — Log redaction:** Một request có `Authorization: Bearer ...`, `?resetToken=...`, JSON password. Thiết kế một log line an toàn gồm method/status/route template/elapsed/requestId nhưng không có các giá trị nhạy cảm.

**Bài 6 — Interceptor:** Nếu Controller trả JSON, `postHandle` có phải vị trí an toàn để set response header không? Giải thích khi nào response đã commit và chọn filter/ResponseBodyAdvice nếu cần.

**Bài 7 — Unit/MockMvc:** Mở rộng `LoggingExtensionTest` để xác nhận UUID giữa header và body luôn khớp, và không bao giờ là `attacker-chosen` khi client tự gửi ID.

**Bài 8 — Metrics:** Nếu bạn đặt label `userId` cho mỗi http.server.requests timer thì chuyện gì xảy ra với số lượng time series? Đề xuất bộ tag ít cardinality hơn.

**Bài 9 — Async dispatch:** Viết một endpoint `Callable<String>` và AsyncHandlerInterceptor có stopwatch gắn request attribute. Quan sát callbacks REQUEST/ASYNC dispatch và test bằng `asyncDispatch`.

**Bài 10 — Architecture:** Một ứng dụng có Spring Security, MVC, Actuator, 10 service downstream và một gateway. Vẽ nơi tạo request ID, nơi inject/extract tracing headers, nơi đo HTTP, nơi đo service span, nơi redaction. Tránh instrument một việc hai lần.

## 14. Ghi nhớ

- **Servlet Filter** là ranh giới phù hợp cho request ID, MDC và ghi log ở mức request dispatch; nó hoạt động trước MVC nhưng không nhất thiết biết handler template.
- **HandlerInterceptor** phù hợp cho route/handler-aware logging và đo phạm vi MVC; nó không thay thế security filter.
- **AsyncHandlerInterceptor** cần xét callback `afterConcurrentHandlingStarted` và redispatch; filter `chain.doFilter` ban đầu không chắc đo được toàn bộ HTTP async latency.
- **Micrometer Observation/Tracing** là hướng mặc định tốt cho HTTP metrics và distributed traces; không tự thêm timers/tags trùng, tránh cardinality cao.
- **MDC** dùng cho log correlation, phải dọn; muốn propagate sang thread khác cần TaskDecorator/snapshot hoặc explicit parameters.
- **Request ID** nên được tạo/kiểm soát bởi server/trusted gateway, xuất header sớm, dùng nhất quán trong logs và safe error response.
- **Đo thời gian đúng ranh giới, log đúng dữ liệu, dọn đúng vòng đời** quan trọng hơn số lượng interceptor/filter được thêm vào.

## 15. Tài liệu tham khảo

- [Spring Framework 6.2 — Servlet Filters](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/filters.html): filter và các đặc điểm lifecycle.
- [Spring Framework 6.2 — Interceptors](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/interceptors.html): WebMvcConfigurer và interceptor registration.
- [Spring Framework 6.2 — Handler Interception](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/handlermapping-interceptor.html): preHandle, postHandle, afterCompletion, response handling.
- [Spring Framework 6.2 — Asynchronous Requests](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html): Callable, DeferredResult, AsyncHandlerInterceptor, ASYNC dispatch.
- [Spring Framework 6.2 — OncePerRequestFilter](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/filter/OncePerRequestFilter.html): REQUEST/ASYNC/ERROR dispatcher behavior.
- [Spring Boot 3.5 — Observability](https://docs.spring.io/spring-boot/3.5/reference/actuator/observability.html): Micrometer Observation, metrics, tracing, context propagation.
- [Spring Boot 3.5 — HTTP Tracing](https://docs.spring.io/spring-boot/3.5/reference/actuator/tracing.html): distributed tracing và correlation.
- [W3C Trace Context](https://www.w3.org/TR/trace-context/): traceparent/tracestate.
- [SLF4J Manual — MDC](https://www.slf4j.org/manual.html): log correlation context.


</details>

- [Quay lại đầu trang](#back-to-top)
