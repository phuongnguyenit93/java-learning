<a id="back-to-top"></a>

# Spring TaskExecutor, ThreadPoolTaskExecutor và @Async

## Menu
- [1. TaskExecutor là gì?](#task-executor)
- [2. @Async hoạt động qua Spring proxy](#async-annotation)
- [3. Return type của @Async](#async-return-type)
- [4. Cấu hình ThreadPoolTaskExecutor](#spring-executor-config)
- [5. TaskDecorator và context propagation](#task-decorator)
- [6. Demo @Async + custom executor + context](#spring-async-demo)
- [7. Spring Framework và Spring Boot cần phân biệt](#spring-boot-auto-config)
- [8. Lifecycle và shutdown](#executor-lifecycle)
- [9. Experiment của Spring Task Executor](#spring-executor-experiments)

Spring không thay thế Java concurrency primitives. Nó cung cấp abstraction và lifecycle integration để sử dụng chúng thuận tiện hơn trong application.

```text
Java Executor concepts
        ↓
Spring TaskExecutor / ThreadPoolTaskExecutor
        ↓
@Async proxy dispatch
```

## <a id="task-executor">1. TaskExecutor là gì?</a>

<details>
<summary>Click for details</summary>

`org.springframework.core.task.TaskExecutor` là abstraction của Spring cho việc thực thi task.

Nó gần với `Executor` của Java nhưng được tích hợp vào Spring container.

`ThreadPoolTaskExecutor` là implementation phổ biến, bên dưới sử dụng `ThreadPoolExecutor` và expose các cấu hình như:

- core pool size;
- max pool size;
- queue capacity;
- keep-alive;
- thread name prefix;
- rejection handler;
- TaskDecorator;
- shutdown behavior.

Mental model vẫn là phần Thread Pool đã học:

```text
core → queue → max → rejection
```

Spring không thay đổi quy tắc cơ bản này.

Một Spring-specific nuance là exception type ở abstraction boundary. `ThreadPoolTaskExecutor` thực hiện Spring `TaskExecutor` contract; khi task bị từ chối, caller nên xử lý theo Spring rejection semantics như `TaskRejectedException` thay vì viết code phụ thuộc rằng raw `RejectedExecutionException` của JDK luôn đi xuyên qua không đổi.

Nếu cần custom overload policy ở tầng JDK, underlying `ThreadPoolExecutor` vẫn dùng `RejectedExecutionHandler`; nhưng public contract mà application gọi qua `TaskExecutor` là contract của Spring.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-annotation">2. @Async hoạt động qua Spring proxy</a>

<details>
<summary>Click for details</summary>

Trước hết application phải bật async method execution infrastructure. Trong module:

```java
@Configuration
@EnableAsync
class TaskExecutorConfig {
}
```

`@EnableAsync` yêu cầu Spring đăng ký infrastructure cần thiết để detect/intercept method có `@Async` theo async configuration hiện tại.

Mental model:

```text
@EnableAsync
→ enable async method execution infrastructure

@Async
→ đánh dấu method invocation cần được async interceptor xử lý

TaskExecutor
→ execution strategy thực tế
```

`@Async` đứng một mình không phải Java language feature và không tự biến method call thành asynchronous nếu application context không bật/cấu hình async support tương ứng.

Khi method được gọi qua Spring proxy:

```text
caller
→ proxy intercept @Async method
→ submit invocation vào TaskExecutor
→ caller nhận control/result handle
→ worker thực thi method
```

Vì vậy `@Async` không phải keyword của Java và không tự tạo "phép màu" bên trong method.

### Self-invocation

Nếu một method trong bean gọi trực tiếp một `@Async` method khác trên chính `this`, lời gọi có thể không đi qua proxy, vì vậy async interception không xảy ra theo proxy mode mặc định.

Design thường rõ ràng hơn khi async boundary nằm giữa hai Spring bean.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-return-type">3. Return type của @Async</a>

<details>
<summary>Click for details</summary>

Các kiểu thường gặp:

- `void` cho fire-and-forget;
- `Future<T>`;
- `CompletableFuture<T>`.

Với `void`, caller không có completion handle trực tiếp; exception handling cũng cần được thiết kế riêng.

Trong proxy-based `@Async`, exception của `void` method không thể được caller nhận qua `Future`. Nếu application dùng fire-and-forget thật sự, cần có `AsyncUncaughtExceptionHandler`/logging/metric strategy tương ứng thay vì giả định exception sẽ tự quay lại HTTP caller.

Với `CompletableFuture`, caller có thể compose result theo các concept đã học ở phần Async.

Code hiện dùng result-bearing method:

```text
TaskExecutorService#runAsync(...)
```

để caller có completion handle rõ ràng và experiment có thể quan sát deterministic.

### Demo exception của @Async void

Với `void`, Spring proxy không thể trả exception cho caller thông qua `Future` vì caller không nhận completion handle nào cả.

Cấu hình một `AsyncUncaughtExceptionHandler` riêng:

```text
AsyncExceptionProbe
```

và một async method cố tình fail:

```java
@Async("threadLearningTaskExecutor")
public void failWithoutFuture(String correlationId) {
    throw new IllegalStateException(...);
}
```

Tham khảo:

```text
TaskExecutorController#asyncVoidException()
GET /spring-executor/void-exception
```

Controller dùng một correlation id chỉ để learning experiment có thể quan sát deterministic rằng handler đã nhận đúng failure. Response kỳ vọng có dạng:

```text
handlerInvoked = true
method = failWithoutFuture
exceptionType = IllegalStateException
correlationId = ...
```

Probe cũng cleanup registration khi observation future hoàn thành exceptional hoặc timeout. Controller gắn timeout **trước khi** gọi async proxy và còn bắt submission failure đồng bộ. Điểm này quan trọng vì task có thể bị reject ngay tại submission boundary, trước khi `@Async` method thật sự chạy và trước khi `AsyncUncaughtExceptionHandler` có cơ hội nhận exception từ method body.

Flow của experiment là:

```text
register observation future
→ attach timeout
→ gọi @Async proxy
   ├─ submit thành công → worker chạy → handler complete future
   └─ submit bị reject  → caller nhận submission failure đồng bộ
                       → complete future exceptionally
→ mọi terminal path đều cleanup pending registration
```

Vì vậy cần phân biệt **failure của task sau khi đã dispatch** với **failure ngay tại submission/admission**. `AsyncUncaughtExceptionHandler` giải quyết exception thoát khỏi `void @Async` method body; nó không phải replacement cho rejection handling ở submission boundary.

**Kết luận:** `@Async void` là fire-and-forget đối với caller; error reporting phải đi qua logging/metrics/`AsyncUncaughtExceptionHandler` hoặc một channel riêng. Nếu business flow cần biết success/failure, ưu tiên return `CompletableFuture`/result handle thay vì `void`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-executor-config">4. Cấu hình ThreadPoolTaskExecutor</a>

<details>
<summary>Click for details</summary>

Config dùng bean riêng:

```text
threadLearningTaskExecutor
```

để các experiment dùng một executor có cấu hình rõ ràng và dễ quan sát.

Các giá trị demo nhỏ vì mục tiêu là học semantics, không phải production sizing.

Sizing production vẫn phải dựa vào workload/downstream/SLO như phần Thread Pool.

Tham khảo code:

```text
TaskExecutorConfig#threadLearningTaskExecutor()
```

### Demo config và production config là hai mục tiêu khác nhau

Demo cố ý hard-code giá trị nhỏ:

```java
executor.setCorePoolSize(2);
executor.setMaxPoolSize(4);
executor.setQueueCapacity(8);
```

để experiment dễ quan sát và không phụ thuộc environment.

Production thường nên externalize những giá trị vận hành cần tuning, ví dụ:

```yaml
application:
  async:
    core-pool-size: 8
    max-pool-size: 32
    queue-capacity: 500
    keep-alive-seconds: 60
    await-termination-seconds: 30
```

rồi bind bằng một configuration object:

```java
@ConfigurationProperties(prefix = "application.async")
public class AsyncExecutorProperties {
    private int corePoolSize;
    private int maxPoolSize;
    private int queueCapacity;
    private int keepAliveSeconds;
    private int awaitTerminationSeconds;
}
```

Sau đó config executor lấy giá trị từ properties thay vì hard-code.

Lợi ích:

- dev/staging/production có thể dùng sizing khác nhau mà không sửa code;
- deployment platform có thể override bằng environment/config;
- thay đổi operational tuning không bị trộn với business logic;
- dễ review rõ đâu là semantics của executor, đâu là con số phù hợp với workload hiện tại.

Nhưng externalize không có nghĩa mọi property đều nên cho phép chỉnh tùy ý. Rejection policy, context propagation hoặc safety limit quan trọng vẫn cần default/validation rõ ràng để tránh một config sai làm biến đổi semantics hệ thống.

Mental model:

```text
learning demo
→ hard-code nhỏ, deterministic

production
→ typed configuration + validation
→ environment-specific values
→ metrics/load test để tuning
```

Không copy các con số demo `2/4/8` sang production và cũng không dùng công thức CPU-bound/I/O-bound như một giá trị tuyệt đối.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-decorator">5. TaskDecorator và context propagation</a>

<details>
<summary>Click for details</summary>

Đây là cầu nối trực tiếp với phần ThreadLocal.

Pattern:

```text
request/caller thread
    context = REQUEST-123
        ↓ submit
TaskDecorator capture REQUEST-123
        ↓
worker thread
        ↓ restore REQUEST-123
async method chạy
        ↓ finally
restore/remove worker context
```

Demo giữ `DemoContext` để nhìn rõ cơ chế capture/restore/cleanup, đồng thời propagate thêm hai context thực tế thường gặp trong Spring application:

- SLF4J MDC, ví dụ `requestId` dùng cho log correlation;
- Spring `RequestAttributes`, là context gắn với HTTP request hiện tại.

Tham khảo:

```text
DemoTaskDecorator
DemoContext
```

`DemoTaskDecorator` capture cả ba context trên caller thread, restore chúng trên worker, rồi restore/clear state cũ trong `finally`.

Rule quan trọng nhất của decorator không phải chỉ `set`, mà là **cleanup/restore trong finally** vì worker sẽ được reuse cho task khác. Nếu chỉ set MDC/RequestAttributes mà không cleanup, task sau chạy trên cùng worker có thể nhìn thấy context của request trước.

`TaskDecorator` cũng không nên được xem như universal async exception handler. Decorator nhận một `Runnable` execution callback; callback đó có thể là wrapper do framework/executor tạo ra thay vì business lambda gốc. Với execution theo `Future`/`FutureTask`, failure có thể được capture vào completion handle thay vì luôn thoát trực tiếp khỏi `Runnable.run()` để decorator bắt được. Vì vậy:

```text
TaskDecorator
→ context capture / restore / cleanup

Future / async handler / rejection handling
→ failure observation theo contract tương ứng
```

Không trộn hai trách nhiệm này chỉ vì chúng cùng nằm quanh execution boundary.

### Lifecycle của RequestAttributes

Propagation không kéo dài lifecycle của HTTP request. Một async task có thể sống lâu hơn request gốc; khi đó không nên giả định mọi object/request-scoped state lấy từ `RequestAttributes` vẫn còn hợp lệ để sử dụng tùy ý.

Nếu async work chỉ cần vài giá trị như `requestId`, tenant id hoặc principal id, design thường an toàn và rõ ràng hơn khi capture **giá trị cần thiết** sang một immutable context riêng thay vì giữ dependency dài hạn vào request object.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-async-demo">6. Demo @Async + custom executor + context</a>

<details>
<summary>Click for details</summary>

Tham khảo controller:

```text
TaskExecutorController#asyncWithContext()
GET /spring-executor/async-context
```

Controller đặt:

```text
DemoContext = REQUEST-123
MDC[requestId] = REQUEST-123
RequestAttributes = request hiện tại do Spring Web quản lý
```

rồi gọi bean khác có:

```java
@Async("threadLearningTaskExecutor")
```

Response `CompletableFuture` cho biết:

- caller thread;
- worker thread;
- DemoContext worker quan sát được;
- MDC request id worker quan sát được;
- worker có nhận được Spring RequestAttributes hay không.

Kỳ vọng:

```text
callerThread != workerThread
workerContext = REQUEST-123
workerMdcRequestId = REQUEST-123
workerHasRequestAttributes = true
```

Controller cũng capture context/MDC trước khi đặt giá trị demo và restore chúng trong `finally`. Vì vậy experiment không làm mất context đã tồn tại sẵn trên caller thread.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-boot-auto-config">7. Spring Framework và Spring Boot cần phân biệt</a>

<details>
<summary>Click for details</summary>

Không nên nói chung rằng:

> Không cấu hình executor thì `@Async` luôn dùng `SimpleAsyncTaskExecutor`.

Đó có thể là cách mô tả fallback ở Spring Framework trong một số trường hợp, nhưng application đang dùng **Spring Boot 3.3.x**, nơi Boot có task execution auto-configuration.

Trong cấu hình mặc định không dùng virtual threads, Boot thường cung cấp `ThreadPoolTaskExecutor` cho task execution nếu application chưa tự định nghĩa executor phù hợp.

Khi virtual threads được bật bằng cấu hình Boot tương ứng, auto-configured task executor có thể chuyển sang implementation sử dụng virtual threads.

Vì custom bean hoặc `AsyncConfigurer` có thể thay đổi lựa chọn cuối cùng, khi behavior quan trọng hãy kiểm tra executor thực tế của application thay vì dựa trên một câu fallback chung.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-lifecycle">8. Lifecycle và shutdown</a>

<details>
<summary>Click for details</summary>

Một ưu điểm lớn của Spring-managed executor là container có thể quản lý lifecycle.

`ThreadPoolTaskExecutor` có các tùy chọn liên quan shutdown như:

- chờ task hoàn thành;
- timeout chờ termination.

Nhưng "Spring quản lý" không có nghĩa mọi task chắc chắn hoàn thành. Shutdown policy và timeout vẫn phải khớp SLA/deployment termination window.

Tham khảo cấu hình của module:

```text
TaskExecutorConfig#threadLearningTaskExecutor()
```

Method này hiện cấu hình cả:

```text
setWaitForTasksToCompleteOnShutdown(true)
setAwaitTerminationSeconds(2)
```

Hai giá trị demo nhỏ để demo lifecycle; production phải khớp termination window của process/container và thời gian task thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-executor-experiments">9. Experiment của Spring Task Executor</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#spring-async-demo` | `TaskExecutorController#asyncWithContext()` | `GET /spring-executor/async-context` |
| `#async-return-type` | `TaskExecutorController#asyncVoidException()` | `GET /spring-executor/void-exception` |

</details>

- [Quay lại đầu trang](#back-to-top)
