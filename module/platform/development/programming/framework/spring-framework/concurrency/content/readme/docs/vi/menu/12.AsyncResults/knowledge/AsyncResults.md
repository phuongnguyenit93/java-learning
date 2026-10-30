<a id="back-to-top"></a>

# Kết quả bất đồng bộ

## Menu
- [void phù hợp khi bên gọi không cần nhận kết quả trực tiếp](#async-results-001)
- [Future cho phép chờ kết quả hoặc nhận lỗi khi công việc kết thúc](#async-results-002)
- [CompletableFuture cho phép kết hợp các bước xử lý](#async-results-003)
- [Vai trò giá trị Future tạm từ phương thức đích với Future do proxy trả về](#async-results-004)
- [Dùng completedFuture() cho giá trị đã tính trong luồng xử lý](#async-results-005)
- [Không dùng kiểu trả về đồng bộ thông thường để giả định vẫn nhận kết quả](#async-results-006)
- [Trả về dữ liệu độc lập với tài nguyên đã đóng](#async-results-007)
- [Không gọi get() hoặc join() ngay lập tức nếu mục tiêu là chồng lấp công việc](#async-results-008)

## <a id="async-results-001">void phù hợp khi bên gọi không cần nhận kết quả trực tiếp</a>

<details>
<summary>Xem chi tiết</summary>

# void phù hợp khi bên gọi không cần nhận kết quả trực tiếp

Trong lập trình bất đồng bộ với Spring Framework, có những công việc mà bên gọi chỉ cần **yêu cầu thực hiện**, không cần nhận trực tiếp giá trị kết quả từ lời gọi đó. Ví dụ: chủ động làm nóng một cache có thể tái tạo, thu thập một thống kê phụ trợ không ảnh hưởng giao dịch chính, hoặc cập nhật một thành phần phục vụ tối ưu hiệu năng. Với loại công việc phù hợp, một phương thức `@Async` có kiểu trả về `void` là lựa chọn đơn giản và rõ ý định.

Tuy nhiên, **`void` chỉ nói rằng phương thức không trả về giá trị**. Nó không có nghĩa phương thức tự chạy bất đồng bộ, đã hoàn thành thành công, có bảo đảm xử lý bền vững hay không thể phát sinh lỗi. Để thực thi bất đồng bộ bằng Spring, lời gọi phải đi qua cơ chế `@Async`/`TaskExecutor` đúng cách.

Bài học giải thích khi nào chọn `void`, nó khác `Future`/`CompletableFuture` ra sao, cách xử lý lỗi của `@Async void`, giới hạn vận hành và một ví dụ Java 17 chạy độc lập.

## 1. Hiểu đúng ý nghĩa của void

Phương thức Java thông thường:

~~~java
public void updateLocalState() {
    System.out.println("Updated");
}
~~~

không trả về một object kết quả. Nhưng **nó vẫn chạy đồng bộ** trên thread gọi cho đến khi method trở về hoặc ném exception. Chỉ đổi kiểu trả về sang `void` không tạo ra worker mới và không giảm thời gian chờ của caller.

Phương thức Spring:

~~~java
@Async("backgroundExecutor")
public void warmCache(String key) {
    // Công việc chạy trong executor nếu lời gọi được Spring intercept đúng cách.
}
~~~

là **đoạn minh họa đặt trong một Spring-managed bean**. Khi được gọi từ bên ngoài bean qua Spring proxy trong chế độ `@EnableAsync` phù hợp, interceptor đóng gói lời gọi thành task rồi gửi đến executor được chọn. Caller không nhận được `Future` từ method `void`, vì vậy không có handle trả về để `get()`, `join()`, `cancel()` hoặc đọc exception.

Có ba điều cần tách:

- **Không cần dữ liệu trả về:** caller không sử dụng giá trị tính toán của task. Đây là lý do hợp lệ để cân nhắc `void`.
- **Không cần biết khi nào kết thúc:** caller không cần chờ kết quả ngay trong luồng điều khiển hiện tại. Đây có thể là lý do thứ hai, nhưng hệ thống vẫn cần cách giám sát ở cấp vận hành.
- **Không cần đảm bảo xử lý:** là một yêu cầu **khác hẳn**. `@Async void` không tự quyết định độ bền, retry hay mức độ quan trọng của nhiệm vụ. Nghiệp vụ vẫn có thể cần cơ chế theo dõi, lưu trạng thái hoặc hàng đợi bền vững.

Từ “fire-and-forget” thường được dùng cho mô hình gọi rồi không chờ. Nó diễn tả hành vi của **caller**, không phải cam kết rằng task sẽ hoàn tất, lỗi được tự sửa hoặc công việc không thể mất.

## 2. Khi nào void là một lựa chọn phù hợp?

| Trường hợp | Mức độ phù hợp | Lý do |
| --- | --- | --- |
| Làm nóng cache có thể tạo lại khi cần | **Thường phù hợp** | Caller không cần giá trị trực tiếp, task có thể thử lại khi cần |
| Tính toán metadata phụ trợ, có thể bỏ qua khi quá tải | **Có thể phù hợp** | Không quyết định kết quả giao dịch chính, nhưng vẫn cần đo lỗi |
| Gửi thông báo không quan trọng, chấp nhận best-effort | **Có thể phù hợp** | API không cần đợi gửi xong; vẫn phải có chính sách lỗi, tải và retry |
| Ghi nhận một sự kiện kinh doanh bắt buộc lưu đầy đủ | **Không nên chỉ dựa vào @Async void in-memory** | Mất task khi crash/shutdown có thể gây sai nghiệp vụ |
| Thanh toán, trừ tiền, cập nhật tồn kho bắt buộc thành công | **Không phù hợp nếu chỉ gọi rồi bỏ qua** | Cần trạng thái, idempotency, retry có kiểm soát và khả năng xác nhận |
| Caller cần trả kết quả sau khi xử lý | **Không phù hợp** | Cần handle hoặc cơ chế trả trạng thái/kết quả khác |
| Caller cần nhận biết task lỗi hoặc hoàn tất | **Thường dùng CompletableFuture<Void> hay job id** | Không có dữ liệu nghiệp vụ trả về nhưng vẫn cần trạng thái hoàn thành |

Một nguyên tắc hữu ích: **“không cần giá trị trả về” không đồng nghĩa “không cần trách nhiệm đối với kết quả.”** Với việc quan trọng, việc caller không đợi trực tiếp vẫn có thể kết hợp hàng đợi/job store và endpoint tra cứu trạng thái. Chọn `void` dựa vào hợp đồng cần có giữa caller và nhiệm vụ, không chỉ vì muốn API trả response nhanh.

## 3. So sánh void, Future và CompletableFuture

Trong Spring, phương thức có `@Async` thường khai báo kiểu trả về `void` hoặc một dạng `Future` như `CompletableFuture<T>`.

| Kiểu trả về của phương thức @Async | Caller nhận được gì? | Theo dõi hoàn tất/lỗi từ caller? | Khi thích hợp |
| --- | --- | --- | --- |
| `void` | Không có Future/result handle | **Không** qua return value; cần logging, handler hoặc trạng thái ngoài | Best-effort, caller không cần phản hồi trực tiếp |
| `Future<T>` | Future đại diện kết quả bất đồng bộ | `get()`, `isDone()`, `cancel()` | Cần biết hoàn tất/nhận kết quả, chấp nhận API Future |
| `CompletableFuture<T>` | Future có khả năng gắn callback và composition | `whenComplete`, `exceptionally`, `thenApply`… | Cần ghép các bước, xử lý lỗi bất đồng bộ |
| `CompletableFuture<Void>` | Không có dữ liệu nghiệp vụ nhưng có handle hoàn tất | **Có**, thông qua completion/exception của Future | Không cần payload nhưng vẫn cần biết thành công/thất bại |

Điểm dễ nhầm là `CompletableFuture<Void>` **không giống** `void`. Cả hai không trả dữ liệu nghiệp vụ, nhưng phương thức thứ nhất trả một **đối tượng biểu diễn trạng thái** cho caller.

Ví dụ Spring tích hợp:

~~~java
@Async("backgroundExecutor")
public CompletableFuture<Void> warmWithCompletion(String key) {
    // Thực hiện công việc trong worker.
    System.out.println("Warming: " + key);
    return CompletableFuture.completedFuture(null);
}
~~~

Đây là **method minh họa**, không phải chương trình `javac` độc lập; phải đặt trong Spring-managed bean cùng cấu hình async. Với cách này, Spring proxy trả một `CompletableFuture<Void>` để caller theo dõi. Bên trong target method, `completedFuture(null)` là kết quả tạm thời sau khi phần công việc của method đã thực hiện; Spring proxy mới cung cấp future bất đồng bộ cho caller.

Nếu method chạy lỗi, caller có thể đăng ký:

~~~java
CompletableFuture<Void> result = worker.warmWithCompletion("catalog");
result.whenComplete((ignored, error) -> {
    if (error != null) {
        System.err.println("Background work failed: " + error);
    }
});
~~~

Đây cũng là **đoạn caller minh họa**, giả định `worker` là bean đã inject từ Spring. Vì yêu cầu là biết completion/error, dùng `CompletableFuture<Void>` hợp lý hơn `void` dù không có dữ liệu trả về.

### 3.1. TaskExecutor.execute(...) và ExecutorService.submit(...) không giống nhau

API Java nền tảng:

~~~java
executor.execute(runnable);             // Không trả handle: void
Future<?> future = service.submit(task); // Trả handle Future
~~~

Đây là **hai lời gọi minh họa**, cần có biến `executor` và `service` tương ứng. `execute(Runnable)` trả `void` theo hợp đồng của `Executor`. `submit(...)` của `ExecutorService` trả `Future`. Trong Spring, `TaskExecutor` tập trung vào `execute(...)`, còn một số extension như `AsyncTaskExecutor` hỗ trợ submit để caller nhận Future.

Với `execute`, task exception được quan sát theo cơ chế của executor/caller thread, không tự được đóng gói thành Future trả về. Với `submit`, exception thường được giữ trong `Future` và được bộc lộ khi caller gọi `get()`. Đừng suy ra hai API có cùng hành vi lỗi chỉ vì cả hai chạy trên thread pool.

## 4. Timeline: void không có nghĩa công việc đã xong

~~~text
HTTP thread                  Spring proxy / executor           Worker thread
    |                                  |                             |
    |-- cacheService.warm("A") ------->|                             |
    |                                  |-- nhận task / queue --------|
    |<-- lời gọi void trả về ----------|                             |
    |                                  |       chờ worker            |
    |-- trả HTTP response              |                             |
    |                                  |-- giao task ---------------->|
    |                                  |                         bắt đầu warm
    |                                  |                         thực thi
    |                                  |                         xong hoặc lỗi
~~~

Kịch bản trên giả định pool có queue và worker bận. Task cũng có thể bắt đầu **trước khi lời gọi `void` trên caller trả về** nếu executor có worker rảnh. Ngoài ra, với `SyncTaskExecutor`, task chạy đồng bộ; với chính sách `CallerRunsPolicy` khi quá tải, task có thể chạy trên caller.

Vì vậy câu chính xác là: **`@Async void` cho phép caller không cần chờ và không nhận trực tiếp kết quả hoàn tất nếu việc submit được xử lý bất đồng bộ**; không có bảo đảm “task chắc chắn bắt đầu sau khi caller trả về”.

Các mốc cần đo độc lập là `t_submit`, `t_start`, `t_finish`. Thời gian từ submit đến start có thể nằm trong queue. Nếu API trả nhanh mà queue chờ nhiều giây, điều đó vẫn ảnh hưởng tới tính kịp thời của xử lý, dù caller không nhận Future.

## 5. Java 17 chạy độc lập: execute trả void, worker vẫn chạy sau đó

Ví dụ dùng Java chuẩn để quan sát sự khác biệt, không cần Spring. Hai latch cho phép xác nhận task đã bắt đầu và giữ nó chưa thể kết thúc trước khi caller kiểm tra.

Tạo tệp **`VoidExecutorDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class VoidExecutorDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        try {
            pool.execute(() -> {
                started.countDown();
                try {
                    release.await();
                    System.out.println("3. Background task finished");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Task did not start");
            }
            System.out.println("1. execute() returned without a Future");
            System.out.println("2. Task finished? " + (finished.getCount() == 0));
            release.countDown();
            if (!finished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Task did not finish");
            }
            System.out.println("4. Main observed completion with CountDownLatch");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
~~~

Lệnh chạy:

~~~bash
javac --release 17 VoidExecutorDemo.java
java VoidExecutorDemo
~~~

Stdout **đã xác minh** khi biên dịch với `--release 17`:

~~~text
1. execute() returned without a Future
2. Task finished? false
3. Background task finished
4. Main observed completion with CountDownLatch
~~~

Giải thích:

1. `execute(...)` không trả `Future`; callback có thể đang thực thi hoặc chờ. Chương trình gọi `started.await(...)` để chắc chắn worker đã bắt đầu.
2. Worker đợi `release`; do đó tại thời điểm main in dòng thứ hai, task chắc chắn **chưa hoàn tất**.
3. Main mở latch; worker in thông báo và giảm `finished`.
4. Chỉ nhờ cơ chế đồng bộ được bổ sung có chủ đích (`finished.await(...)`), main mới xác minh được task đã kết thúc. **Đây không phải một kết quả mà `execute(void)` trả về.**

Ví dụ trình diễn một trường hợp caller chủ động **thêm cách theo dõi** dù API execute tự nó không có Future. Nếu caller không cần biết completion, nó có thể không chờ latch; nhưng trong bài test, latch giúp tạo kết quả xác định. `shutdownNow()` chỉ để giải phóng worker khi chương trình kết thúc, không phải cơ chế bảo đảm mọi tác vụ trong ứng dụng thật đã xử lý thành công.

## 6. Spring Framework: ví dụ @Async void với executor có giới hạn

Các tệp sau là **mã tích hợp** cho Java 17+, Spring Framework 6.x (hoặc ứng dụng Spring Boot tương thích) có `spring-context`, xử lý annotation và component scanning. Chúng **không biên dịch chỉ bằng một câu lệnh `javac` thiếu Spring dependencies**. Mỗi class ở một tệp có cùng tên trong package `example.concurrency`.

### 6.1. Cấu hình bật @Async và chọn executor

Tệp `AsyncConfiguration.java`:

~~~java
package example.concurrency;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfiguration implements AsyncConfigurer {

    @Bean("bestEffortExecutor")
    public ThreadPoolTaskExecutor bestEffortExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("best-effort-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (exception, method, args) ->
                System.err.println("Async void failed in "
                        + method.getName() + ": "
                        + exception.getClass().getSimpleName());
    }
}
~~~

Giải thích:

- `@EnableAsync` bật cơ chế interceptor cho `@Async` trong Spring ApplicationContext.
- `@Bean("bestEffortExecutor")` đặt tên rõ ràng. `@Async("bestEffortExecutor")` chọn bean này thay vì phụ thuộc vào default executor. Spring quản lý initialization/lifecycle của bean executor.
- `core=2`, `max=4`, `queueCapacity=20` là số minh họa. Sau core, executor thường ưu tiên enqueue. Pool bão hòa có thể từ chối task, không phải mọi lời gọi đều sẽ được nhận.
- `AbortPolicy` làm lỗi từ chối rõ ràng. Trong `ThreadPoolTaskExecutor`, ngoại lệ được bộc lộ theo hợp đồng Spring `TaskExecutor`, thường là `TaskRejectedException`.
- `getAsyncUncaughtExceptionHandler()` đăng ký **handler cho exception không được bắt trong target `@Async void`**. Ví dụ chỉ in tên method và loại lỗi; production nên dùng structured logging/metrics, phân loại mức độ và giới hạn dữ liệu nhạy cảm.

`AsyncConfigurer` được xử lý sớm trong bootstrap; ví dụ không inject các bean phức tạp vào handler. Nếu ứng dụng cần logger hoặc hệ thống cảnh báo, triển khai nhất quán với lifecycle của Spring. Chỉ nên có cấu hình async toàn cục có chủ đích, tránh nhiều cấu hình cùng tranh quyền quyết định handler.

### 6.2. Service không cần trả kết quả trực tiếp

Tệp `CacheWarmService.java`:

~~~java
package example.concurrency;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CacheWarmService {
    private final ConcurrentMap<String, Boolean> warmed =
            new ConcurrentHashMap<>();

    @Async("bestEffortExecutor")
    public void warm(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Cache key must not be blank");
        }
        // Mô phỏng công việc làm nóng cache có thể tạo lại.
        warmed.put(key, Boolean.TRUE);
    }

    public boolean isWarm(String key) {
        return Boolean.TRUE.equals(warmed.get(key));
    }
}
~~~

Đây là ví dụ đơn giản có thể biên dịch **trong ứng dụng Spring đã khai báo dependencies**, nhưng không đại diện cho một hệ thống cache nhiều node. Map chỉ nằm trong bộ nhớ **một application instance**. Nghiệp vụ thực tế có thể gọi repository hoặc cache backend, dùng timeout khi có I/O và tuân theo chính sách retry/overload của hệ thống.

Khi bean khác gọi `cacheWarmService.warm("catalog")` qua Spring proxy:

1. Spring submit lời gọi vào `bestEffortExecutor`.
2. Caller không nhận một `Future` vì chữ ký là `void`.
3. Worker có thể thực thi ngay hoặc phải chờ queue.
4. Khi chạy, `warm(...)` cập nhật map. Caller không được thông báo trực tiếp khi nó xong.
5. Nếu `key` không hợp lệ khiến method ném exception **trong worker**, exception được chuyển đến `AsyncUncaughtExceptionHandler` thay vì quay về stack của caller sau khi đã gửi task thành công.

Method `isWarm(...)` chỉ nhằm minh họa một **kênh kiểm tra trạng thái riêng**, không phải một acknowledgment tự động của phương thức `void`. Trong hệ thống thật, cần phân biệt rõ trạng thái chưa nhận, đang đợi, đang chạy, thành công và thất bại nếu trạng thái đó là một yêu cầu nghiệp vụ.

### 6.3. Caller từ controller và ý nghĩa của HTTP 202

Tệp `CacheWarmController.java` (cần Spring Web MVC):

~~~java
package example.concurrency;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cache")
public class CacheWarmController {
    private final CacheWarmService cacheWarmService;

    public CacheWarmController(CacheWarmService cacheWarmService) {
        this.cacheWarmService = cacheWarmService;
    }

    @PostMapping("/warm/{key}")
    public ResponseEntity<Void> requestWarm(@PathVariable String key) {
        if (key.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            cacheWarmService.warm(key);
            return ResponseEntity.accepted().build();
        } catch (TaskRejectedException ex) {
            return ResponseEntity.status(
                    HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
~~~

Điều quan trọng là **controller gọi một bean khác**; lời gọi đi qua Spring proxy của `CacheWarmService`. `202 Accepted` ở đây chỉ mô tả việc **yêu cầu đã được submit thành công theo cơ chế executor trong bộ nhớ**. Không hứa “cache đã được làm nóng” hoặc “công việc chắc chắn sẽ sống qua restart”.

Cần ghi nhớ ranh giới của ví dụ:

- Bắt `TaskRejectedException` ở caller nhằm xử lý lỗi **trước/khi nhận task** do executor không còn khả năng tiếp nhận. Tùy cấu hình executor/rejection policy, một số cơ chế có thể chạy task trên caller hoặc bỏ task mà không ném lỗi; phải chọn policy có chủ đích.
- Nếu worker được nhận nhưng sau đó ném exception, HTTP response **đã gửi không tự đổi thành 500**. Trường hợp đó được quan sát bởi handler hoặc kênh trạng thái độc lập.
- Việc `warm(...)` hoàn tất rất nhanh có thể xảy ra **trước khi controller trả 202**, nhưng lời gọi `void` không cung cấp bảo đảm đó.
- Nếu nghiệp vụ đòi hỏi chỉ trả 202 sau khi công việc đã được lưu bền vững, controller phải ghi vào queue/job store bền vững trước, rồi mới xác nhận theo hợp đồng API.

Đối với bài toán cache ấm có thể tái tạo, cách thực hiện best-effort này hợp lý về mặt minh họa. Không dùng nó nguyên xi cho thanh toán, audit bắt buộc hoặc tác vụ không được phép mất.

## 7. Lỗi ở @Async void đi đâu?

Đây là khác biệt kỹ thuật quan trọng nhất giữa `void` và `Future` khi thiết kế hợp đồng trả về.

### 7.1. Exception bên trong worker không tự quay lại caller

Ví dụ:

~~~java
@Async("bestEffortExecutor")
public void sendBestEffort(String id) {
    throw new IllegalStateException("Simulated background failure");
}
~~~

Đây là một **method minh họa** đặt trong bean Spring, không phải class hoàn chỉnh độc lập. Nếu được gọi qua proxy và executor nhận task, exception xảy ra **trên worker**. Caller đã đi tiếp nên không thể dùng `try/catch` quanh lời gọi để đón exception thực thi đó như lỗi đồng bộ.

Với phương thức `@Async` trả `Future`, exception thực thi có thể được biểu diễn qua Future để caller quan sát bằng `get()` hoặc callback. Với `void`, Spring hỗ trợ `AsyncUncaughtExceptionHandler`. Nếu không cấu hình riêng, handler mặc định của Spring ghi log exception; nó không biến lời gọi thành Future.

### 7.2. Hai loại lỗi phải có kênh xử lý riêng

| Lỗi | Xảy ra ở đâu? | Caller có thể xử lý như thế nào? |
| --- | --- | --- |
| Dữ liệu đầu vào không hợp lệ trước khi submit | Thread caller, nếu validate ở biên | Trả 400/validation error đồng bộ |
| Executor từ chối task lúc submit | Lời gọi qua Spring proxy, trước khi công việc được nhận | Catch `TaskRejectedException` nếu policy ném lỗi; trả 503/throttle |
| Lỗi nghiệp vụ trong phương thức `@Async void` | Worker sau khi đã nhận task | Handler/log/metrics, retry hoặc trạng thái độc lập; không thể đổi response đã trả |
| JVM chết hoặc instance bị terminate sau khi nhận task | Ngoài đường exception bình thường của method | Khôi phục công việc từ job store/queue bền vững nếu nghiệp vụ cần |
| Task bị gián đoạn do shutdown/cancellation | Trong quá trình chạy/đóng pool tùy cấu hình | Xử lý interruption và quyết định task cần được retry/khôi phục không |

Cần phân biệt lỗi từ task **đã được nhận** và lỗi **không được nhận**. `AsyncUncaughtExceptionHandler` xử lý exception chưa được bắt từ phương thức `@Async void`; không phải hệ thống thay thế cho mọi trường hợp pool bị reject, crash hoặc task chưa bao giờ được chạy.

### 7.3. Handler không phải cơ chế retry tự động

Handler nhận thông tin exception, `Method` và arguments. Nó có thể log, phát tín hiệu metric hoặc gửi cảnh báo qua hệ thống riêng. **Nó không tự chạy lại method** và không bảo đảm công việc được xử lý thành công.

Nếu tự implement retry, phải xem xét idempotency, số lần thử, backoff/jitter, timeout và nguy cơ tạo bão retry khi downstream lỗi. Nếu handler gửi tiếp việc sang cùng một executor đang bão hòa, nó có thể làm quá tải nặng hơn.

Không in nguyên arguments trong log/alert một cách mặc định: chúng có thể chứa email, token, dữ liệu cá nhân hoặc payload lớn. Trong ví dụ ở mục 6, handler chỉ ghi tên method và loại exception để giữ minh họa gọn.

## 8. Những trường hợp void không đáp ứng đủ yêu cầu

### 8.1. Cần biết thành công/thất bại dù không cần payload

Dùng `CompletableFuture<Void>`. Caller có thể:

~~~java
CompletableFuture<Void> future = worker.warmWithCompletion("catalog");
future.whenComplete((unused, error) -> {
    if (error != null) {
        System.err.println("Warm failed: " + error);
    } else {
        System.out.println("Warm completed");
    }
});
~~~

Đoạn này giả định method và bean `worker` ở mục 3. Nếu `future.join()` hoặc `get()` ngay, caller bị chặn tới khi xong. Không cần chặn nếu chỉ muốn gắn callback; đó là lợi ích quan trọng của completion stage.

### 8.2. Cần hủy công việc

`@Async void` không trả handle để caller gọi `cancel(...)` trên chính invocation đó. Nếu hợp đồng cần hủy, có thể dùng `Future`/`CompletableFuture`, hoặc một job id và cơ chế hủy riêng. Ngay cả khi có Future, `cancel(true)` chỉ yêu cầu ngắt khi task và thư viện hỗ trợ; không tự hoàn tác side effect đã xảy ra.

### 8.3. Cần trạng thái liên tục hoặc kết quả sau này

Không nhất thiết phải chờ trong HTTP request, nhưng có thể trả job id và để khách hàng gọi endpoint kiểm tra trạng thái. Khi đó hệ thống cần lưu ít nhất `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED` (và trạng thái hủy nếu có), cùng thời điểm, lỗi và quy tắc giữ thông tin. Job id và job store là hợp đồng nghiệp vụ độc lập với return type của method worker.

### 8.4. Công việc không được phép mất

Một executor in-memory chỉ nhận task vào worker/queue của process. Nếu JVM crash hoặc app bị kill trước khi task chạy xong, lời gọi `void` không có cơ chế khôi phục tự động. Với công việc bắt buộc thực hiện, cần cân nhắc queue bền vững, transactional outbox, job store, retry/idempotency và cơ chế phục hồi rõ ràng.

Không nên gọi `void` là “không an toàn” theo bản thân kiểu dữ liệu: một worker `void` vẫn có thể đọc từ queue bền vững do thành phần khác điều phối. Vấn đề là **người gọi không có return handle**, còn độ tin cậy phụ thuộc toàn bộ kiến trúc.

## 9. Sai lầm về proxy, transaction và ngữ cảnh thread

### 9.1. Self-invocation khiến @Async không được áp dụng

Trong cấu hình proxy mặc định:

~~~java
@Service
public class ExampleService {
    public void outer() {
        inner(); // Lời gọi nội bộ, thường KHÔNG đi qua @Async proxy.
    }

    @Async("bestEffortExecutor")
    public void inner() {
        System.out.println("Inside worker?");
    }
}
~~~

Đây là **đoạn mã minh họa trong Spring**, với imports tương ứng. Việc `outer()` gọi trực tiếp `inner()` trên cùng instance thường bỏ qua proxy. `inner()` có thể chạy đồng bộ trên caller dù được đánh dấu `@Async`. Cách thông thường là tách method async sang một bean khác và gọi thông qua bean đã được Spring quản lý, như `CacheWarmController → CacheWarmService` ở trên.

Không dùng `@Async` trên lifecycle callback như `@PostConstruct` với kỳ vọng Spring sẽ tự chạy nền theo cách thông thường. Nếu cần kích hoạt công việc sau startup, bố trí một bean điều phối khác và xem xét lifecycle/readiness phù hợp.

### 9.2. Context của thread không tự được chuyển sang worker

Một số thông tin trong Spring ứng dụng gắn với thread hiện tại, chẳng hạn transaction context, request context, MDC hoặc security context. Khi `@Async` chuyển sang worker khác, **không được mặc định** coi chúng tự động được chuyển đầy đủ và an toàn.

Ví dụ caller đang trong transaction không có nghĩa transaction thread-bound của caller tự động tiếp tục ở worker. Nếu công việc async cần transaction riêng, thiết kế ranh giới transaction **trong chính luồng xử lý async** bằng cấu hình Spring phù hợp. Với trace/MDC, có thể dùng task decorator hoặc cơ chế context propagation đã được thư viện xác định.

### 9.3. Kiểu void không bảo đảm chạy ngoài caller

`TaskExecutor` có các implementation khác nhau. `SyncTaskExecutor` chạy đồng bộ trên thread gọi. `CallerRunsPolicy` của `ThreadPoolExecutor` có thể chạy task trên caller khi pool bão hòa và chưa shutdown, thay vì từ chối.

Vì thế “dùng `void` để giảm latency” chỉ đúng nếu đường thực thi được cấu hình thực sự phù hợp và không chặn caller. Đo latency request, thời gian submit, active workers và queue wait để kiểm chứng.

## 10. Theo dõi công việc void trong môi trường vận hành

Dù caller không cần nhận kết quả, hệ thống vẫn nên biết task đã thực sự được xử lý hay chưa. Một tập quan sát cơ bản:

| Chỉ số/thông tin | Nơi đo | Ý nghĩa |
| --- | --- | --- |
| Số lời gọi submit | Biên caller | Có bao nhiêu yêu cầu xử lý nền |
| Số lần submit bị từ chối | Biên proxy/executor | Phát hiện quá tải hoặc shutdown |
| Thời gian chờ queue | Từ lúc nhận đến khi worker bắt đầu | Đo độ trễ trước khi xử lý thật |
| Số task đang chạy, queue depth, pool size | Executor | Giới hạn concurrency và backlog |
| Thời gian thực thi | Worker bắt đầu → kết thúc | Phát hiện I/O chậm hoặc contention |
| Số task thành công/thất bại | Trong method, handler hoặc hệ thống job | Phân biệt “được nhận” và “xử lý xong” |
| Correlation/job id | Caller và worker | Ghép log/trace của một tác vụ |
| Shutdown/timeout/retry | Lifecycle và downstream | Phát hiện việc bị cắt ngang hoặc lặp lại |

Với `ThreadPoolTaskExecutor`, có thể theo dõi `getActiveCount()`, `getPoolSize()`, `getQueueSize()` (ở phiên bản Spring hỗ trợ) và các metric được instrument tương ứng. Chỉ nhìn số task submit không thể kết luận số task đã hoàn tất.

Khi thiết lập metrics, nên chọn tên rõ: `submitted_total`, `accepted_total`, `rejected_total`, `started_total`, `completed_total`, `failed_total`. Đây là **tên metric gợi ý cho ứng dụng**, không phải danh sách API metric mặc định do Spring tự xuất ra.

### 10.1. Shutdown và triển khai lại

Khi application context đóng, executor có lifecycle shutdown theo cấu hình của phiên bản Spring. Thời gian chờ task, hành vi nhận task mới và việc ngắt tác vụ cần được chọn phù hợp với yêu cầu. Không kết luận “đã submit thì task chắc chắn hoàn tất trước pod restart”.

Nếu task chỉ là cache warm có thể làm lại, mất một lượt khi shutdown có thể chấp nhận được. Nếu task bắt buộc, dùng cơ chế bền vững hơn thay vì chỉ kéo dài thời gian chờ đóng pool.

## 11. Kiểm thử @Async void đúng cách

Vì caller không có Future, test kiểm tra được completion cần một **tín hiệu đồng bộ hoặc spy/collaborator có chủ đích**:

1. Dùng test ApplicationContext thật có `@EnableAsync` và executor được chọn; gọi method thông qua bean proxy, không gọi `new Service()`.
2. Inject một cộng tác viên kiểm thử (hoặc latch) để xác nhận target method đã chạy; dùng timeout hữu hạn khi chờ để test không treo vô hạn.
3. Kiểm tra nhánh thành công và nhánh exception của method `void`; với exception, xác minh handler được gọi hoặc metric lỗi tăng. Không chờ `try/catch` của caller thấy lỗi phát sinh trên worker.
4. Mô phỏng executor không nhận thêm task để kiểm tra chính sách rejection ở caller. Không cần tạo tải lớn tùy tiện; một executor có giới hạn nhỏ với latch đủ tái hiện.
5. Kiểm tra shutdown/restart nếu hệ thống có yêu cầu không mất việc; test đơn vị của service không chứng minh được độ bền của queue.

Nếu test dùng `SyncTaskExecutor`, code có thể chạy ngay trên thread test. Điều đó hữu ích để kiểm tra logic đơn giản nhưng **không kiểm chứng được hành vi queue và timing của async thật**. Nên có integration test riêng cho đường đi qua proxy và executor.

## 12. Những hiểu nhầm cần tránh

**“Method trả void nên chạy nền.”** Sai. `void` chỉ là kiểu trả về; cần cấu hình executor và interception đúng nếu muốn bất đồng bộ.

**“Không có Future nghĩa là task chắc chắn không lỗi.”** Sai. Exception trong worker vẫn xảy ra; với `@Async void` cần handler/log/metrics hoặc cơ chế nghiệp vụ riêng.

**“Caller bắt `Exception` quanh method @Async void là bắt hết lỗi.”** Sai. Caller thường chỉ bắt được lỗi xảy ra ở bước gửi, ví dụ rejection; lỗi thực thi sau khi task được nhận đi theo đường xử lý async.

**“Trả HTTP 202 nghĩa là task đã xử lý thành công.”** Sai. Cần phân biệt đã nhận với hoàn tất, và in-memory submit với durable acceptance.

**“Không có dữ liệu cần trả thì phải dùng void.”** Không bắt buộc. `CompletableFuture<Void>` tốt hơn nếu caller cần thông tin hoàn tất, exception hoặc cơ chế kết hợp thêm tác vụ.

**“@Async trong cùng bean vẫn chạy async khi gọi this.method().”** Sai trong proxy mode mặc định; lời gọi nội bộ không đi qua proxy.

**“void fire-and-forget không cần logging hoặc retry.”** Sai. Yêu cầu xử lý lỗi phụ thuộc nghiệp vụ; không nhận kết quả trực tiếp vẫn cần khả năng quan sát và chính sách quá tải.

**“Có thể hủy một invocation @Async void bằng Future.”** Không có Future trả về từ method `void`; cần thiết kế handle riêng hoặc chuyển sang Future nếu hợp đồng yêu cầu hủy.

**“Mọi task gửi vào executor đều chạy ngay.”** Sai. Queue, core/max, CPU/I/O và tài nguyên downstream quyết định thời điểm thực sự bắt đầu.

## 13. Tình huống lựa chọn theo yêu cầu nghiệp vụ

Một sản phẩm có chức năng tạo báo cáo và muốn gọi thêm tác vụ phụ:

- **Làm nóng cache dữ liệu báo cáo đã có thể sinh lại:** caller không cần biết kết quả ngay, thất bại có thể được ghi metric và thử lại ở lượt sau. `@Async void` là phương án hợp lý nếu đã kiểm soát executor và hiểu rủi ro mất task.
- **Tạo file báo cáo người dùng sẽ tải xuống:** caller cần biết report id, trạng thái và link khi hoàn thành. Chỉ `@Async void` không đủ hợp đồng. Có thể dùng job id/status store, hoặc `CompletableFuture<Report>` cho workflow trong cùng process.
- **Gửi biên lai giao dịch bắt buộc:** caller có thể không cần chờ email gửi xong, nhưng hệ thống phải biết biên lai nào chưa gửi và phải retry. Queue bền vững/outbox với trạng thái và idempotency phù hợp hơn một lời gọi `@Async void` thuần trong bộ nhớ.
- **Cập nhật counter phụ trợ với sai số nhỏ được chấp nhận:** `void` có thể đáp ứng nếu metric thất bại được theo dõi và tốc độ gửi task không vượt năng lực của pool.

Quy tắc ra quyết định:

~~~text
Caller có cần kết quả nghiệp vụ ngay?
    |
    +-- Có --> Future<T> / CompletableFuture<T> hoặc xử lý đồng bộ phù hợp
    |
    +-- Không --> Có cần biết completion/error/cancel qua return handle?
                     |
                     +-- Có --> CompletableFuture<Void> / job id + trạng thái
                     |
                     +-- Không --> Công việc có được phép best-effort/làm lại?
                                      |
                                      +-- Có --> @Async void + handler + metrics
                                      |
                                      +-- Không --> Thiết kế tiếp nhận bền vững,
                                                   retry/idempotency và trạng thái
~~~

Đây là sơ đồ chọn hợp đồng, không phải luật của một framework. Cùng một nghiệp vụ có thể chọn mô hình khác tùy SLA, mức độ quan trọng và cách hệ thống đã lưu task.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Một phương thức Java trả `void` có mặc nhiên bất đồng bộ không?

**Đáp án:** không. Kiểu `void` không tạo thread và không tách lời gọi. Trong Spring cần cơ chế `@Async` và executor được cấu hình, caller phải đi qua interceptor/proxy.

**Câu 2.** Caller gọi `@Async void warm()` và lời gọi trả về. Có biết việc làm nóng đã thành công chưa?

**Đáp án:** không. Chỉ biết lời gọi đã trở về; nếu task được submit, nó có thể đang queue, chạy hoặc đã xong. Muốn biết thành công cần kênh quan sát khác hoặc Future.

**Câu 3.** Tại sao `CompletableFuture<Void>` đôi khi tốt hơn `void` dù không trả payload?

**Đáp án:** Future cung cấp handle để theo dõi hoàn tất, lỗi, gắn callback và yêu cầu hủy khi hỗ trợ.

**Câu 4.** Exception ném từ target `@Async void` được truyền đến controller qua `try/catch` không?

**Đáp án:** không theo đường đồng bộ sau khi task đã được submit thành công. Spring dùng `AsyncUncaughtExceptionHandler` cho exception không bắt từ method `void`; rejection ở bước submit là chuyện riêng.

**Câu 5.** Nếu pool bão hòa, phương thức `@Async void` luôn trả bình thường không?

**Đáp án:** không. Với `AbortPolicy`, submit có thể thất bại và Spring có thể ném `TaskRejectedException` tại caller.

**Câu 6.** Vì sao không dùng `@Async void` in-memory đơn thuần để bảo đảm thanh toán được xử lý?

**Đáp án:** vì không có hợp đồng kết quả trực tiếp hoặc bảo đảm bền vững; crash có thể làm mất task. Cần trạng thái, idempotency, retry và tiếp nhận bền vững phù hợp.

**Câu 7.** Trong ví dụ Java 17, nhờ đâu main biết task đã hoàn tất dù `execute` trả `void`?

**Đáp án:** nhờ `CountDownLatch finished` được bổ sung riêng và `await(...)`. Đó không phải giá trị trả về của `execute`.

**Câu 8.** Có nên đo số task thành công bằng số lời gọi `@Async void` ở caller không?

**Đáp án:** không. Cần tách submitted, rejected, started, completed và failed; caller chỉ đo được một phần đường đi.

## 15. Tài liệu tham khảo

- Spring Framework — Task Execution and Scheduling, `@Async` và xử lý exception: https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html
- Spring Framework 6.2 API — `@Async` và các kiểu trả về được hỗ trợ: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/annotation/Async.html
- Spring Framework 6.2 API — `@EnableAsync`, `AsyncConfigurer`, xử lý lỗi `void` và proxy: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html
- Spring Framework 6.2 API — `ThreadPoolTaskExecutor`: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Java SE 17 — `Executor`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html
- Java SE 17 — `ExecutorService` và `Future`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-results-002">Future cho phép chờ kết quả hoặc nhận lỗi khi công việc kết thúc</a>

<details>
<summary>Xem chi tiết</summary>

# Future cho phép chờ kết quả hoặc nhận lỗi khi công việc kết thúc

## 1. Tại sao cần Future?

Trong ứng dụng Java và Spring, công việc có thể được giao cho một worker thread để caller tiếp tục xử lý việc khác. Tuy nhiên, caller vẫn cần biết **task đã kết thúc chưa, kết quả là gì và có lỗi xảy ra không**. Giao diện `java.util.concurrent.Future<V>` cung cấp một **handle đại diện cho kết quả của một phép tính có thể chưa hoàn tất**.

Ví dụ nghiệp vụ: request gửi công việc tạo báo cáo vào một thread pool. Sau đó hệ thống muốn nhận đường dẫn báo cáo, phát hiện thất bại, hoặc không chờ lâu hơn một thời hạn nhất định. Thay vì xem việc gửi task là đã thành công, code giữ lại `Future<Report>` và đọc nó tại điểm thực sự cần kết quả.

Sau bài này, bạn sẽ:

- Hiểu `Future<V>` trong mô hình thread pool và phân biệt **nhận task**, **hoàn tất task**, **thành công**, **thất bại** và **hủy**.
- Sử dụng `get()`, `get(timeout, unit)`, `isDone()`, `isCancelled()` và `cancel(...)` chính xác.
- Bắt `ExecutionException` để lấy **cause là lỗi gốc của task**, phân biệt với `InterruptedException`, `TimeoutException` và `CancellationException`.
- Tạo ví dụ chạy độc lập bằng **Java 17+**, có đầu ra ổn định và kiểm tra timeout/cancellation mà không suy đoán lịch chạy.
- Sử dụng `Future` qua `ExecutorService.submit(...)` của JDK và `AsyncTaskExecutor.submit(...)`/`@Async` trong Spring.
- Biết khi nào `Future` là đủ, khi nào nên dùng `CompletableFuture` để ghép các bước bất đồng bộ, và các rủi ro khi đợi Future bên trong cùng một pool.

**Phiên bản:** Các ví dụ JDK sử dụng Java 17+, ví dụ Spring độc lập dùng Spring Framework 6.1.3 và Java 17+. Chúng minh họa những API ổn định, không phụ thuộc Java 21 virtual threads.

## 2. Mô hình: task chạy ở nơi khác, Future là tay cầm

`Future<V>` không phải một worker thread và cũng không phải bản thân công việc. Nó là đối tượng cho phép caller theo dõi trạng thái của một phép tính được nộp vào executor.

~~~text
Caller thread                            Executor / worker
      |                                          |
      | submit(Callable<V>)                      |
      |----------------------------------------->|
      |<------ Future<V> -----------------------|
      |                                          |
      | làm công việc khác                       | chạy call()
      |                                          |
      | future.get()                             |
      | ------ đợi nếu chưa hoàn thành --------> |
      |                                          |
      |<------- giá trị V / exception ------------|
~~~

Từ lúc nhận được `Future` đến khi gọi `get()`, caller có thể tiếp tục xử lý độc lập. Nếu gọi `get()` **ngay lập tức**, caller có thể bị chặn cho đến khi worker hoàn tất; lợi ích concurrency vẫn có thể có, nhưng không nhất thiết cải thiện độ trễ theo cách bạn mong muốn.

### Bốn tình huống cần phân biệt

| Tình huống | `isDone()` | `isCancelled()` | `get()` |
| --- | --- | --- | --- |
| Task còn đang đợi hoặc chạy | `false` | `false` | Có thể **chặn** |
| Task hoàn thành thành công | `true` | `false` | Trả về `V`; `Runnable` trả `null` |
| Task hoàn thành với exception | `true` | `false` | Ném `ExecutionException`, cause là lỗi thực thi |
| Task bị hủy thành công | `true` | `true` | Ném `CancellationException` |

**Quan trọng:** `isDone() == true` chỉ có nghĩa là task đã **kết thúc theo một cách nào đó**, có thể là thành công, exception hoặc cancellation. Không dùng `isDone()` làm bằng chứng nghiệp vụ thành công.

Một task có thể bị **từ chối ngay ở bước submit** khi pool quá tải hoặc shutdown. Khi đó lời gọi submit có thể ném exception **trước khi trả Future**: không có `Future` để đợi kết quả. Đây là lỗi **admission**, không phải lỗi bên trong task đã được chấp nhận.

## 3. API cốt lõi của Future trong Java 17

`Future<V>` thuộc package `java.util.concurrent`; `V` là kiểu kết quả mong đợi. Những method quan trọng trong API Java 17 là:

| Method | Hành vi | Lưu ý |
| --- | --- | --- |
| `V get()` | Chờ nếu cần, rồi lấy kết quả | Có thể chặn vô thời hạn nếu task không bao giờ hoàn tất |
| `V get(long timeout, TimeUnit unit)` | Chờ tối đa thời gian đã cho rồi lấy kết quả | Ném `TimeoutException` nếu chưa hoàn tất trong thời hạn |
| `boolean isDone()` | Cho biết Future đã kết thúc, gồm cả lỗi/hủy | **Không xác nhận thành công** |
| `boolean isCancelled()` | Cho biết đã bị hủy | `false` không đồng nghĩa task thành công |
| `boolean cancel(boolean mayInterruptIfRunning)` | Thử hủy phép tính | Cần xét implementation và khả năng task hợp tác với interrupt |

Ví dụ chữ ký:

~~~java
// Các chữ ký API dùng để giải thích; không phải class độc lập.
V get() throws InterruptedException, ExecutionException;

V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;

boolean isDone();
boolean isCancelled();
boolean cancel(boolean mayInterruptIfRunning);
~~~

**Lưu ý phiên bản:** Đây là bộ method tương thích Java 17. Các phiên bản JDK mới hơn có thêm tiện ích truy cập trạng thái như `state()`/`resultNow()`/`exceptionNow()`, nhưng không viết code Java 17 dựa trên các method mới đó.

### 3.1. `get()` trả kết quả khi thành công

~~~java
// Fragment: executor là ExecutorService được cấu hình.
Future<Integer> answer = executor.submit(() -> 21 * 2);
Integer result = answer.get(); // 42 khi thành công
~~~

`submit(Callable<Integer>)` nhận một lambda có trả giá trị; `Future<Integer>` cho phép lấy kết quả. `get()` **chặn thread gọi** nếu chưa hoàn thành. Nếu task đã hoàn thành, method trở về ngay. `get()` không thực thi task thay cho worker.

### 3.2. `submit(Runnable)` vẫn trả Future nhưng không có payload

~~~java
// Fragment: Runnable không trả dữ liệu.
Future<?> saved = executor.submit(() -> saveRecord());
Object result = saved.get(); // null nếu chạy thành công
~~~

Ở đây `saveRecord()` là hàm ứng dụng chưa được định nghĩa, nên đoạn code không độc lập. Với `Runnable`, future theo dõi hoàn tất và lỗi; kết quả thành công thường là `null`. Nếu cần trả một giá trị nghiệp vụ, dùng `Callable<T>` hoặc overload `submit(Runnable, result)` của `ExecutorService`.

Không nhầm với `execute(Runnable)`: method `execute` nhận công việc nhưng có kiểu trả về `void`, nên không trực tiếp cung cấp Future để theo dõi.

### 3.3. `get(timeout, unit)` giới hạn thời gian đợi của caller

~~~java
// Fragment: lấy kết quả trong tối đa 2 giây chờ.
String data = future.get(2, TimeUnit.SECONDS);
~~~

Nếu hết 2 giây mà Future chưa hoàn tất, caller nhận `TimeoutException` và có thể chuyển sang fallback hoặc phản hồi timeout. **Task không tự bị hủy chỉ vì `get` timeout**; nó vẫn có thể đang chạy và gây side effect. Muốn hủy, ứng dụng phải ra quyết định riêng (ví dụ `future.cancel(true)` khi phù hợp) và biết rằng cancellation không đảm bảo chặn toàn bộ I/O ở mọi implementation.

### 3.4. Quan hệ happens-before và kết quả chia sẻ

Javadoc `Future` của JDK 17 quy định: những hành động trong tính toán bất đồng bộ **happen-before** những hành động của thread khác **sau một lời gọi `Future.get()` tương ứng**. Vì vậy, sau `get()` thành công, caller có quan hệ đồng bộ để quan sát kết quả được công bố qua việc hoàn tất task. Tuy vậy, nếu task còn tiếp tục hoặc bạn chia sẻ đối tượng mutable cho nhiều thread khác, các vấn đề data race khác vẫn cần giải quyết bằng thiết kế thread-safe, không coi `Future` là khóa bảo vệ mọi biến.

## 4. Những exception của Future.get và cách xử lý

Mỗi exception báo một câu chuyện khác nhau:

| Exception | Xuất hiện khi | Hành động thường cần |
| --- | --- | --- |
| `ExecutionException` (checked) | Task đã chạy và ném exception | Đọc `getCause()`, log/phân loại lỗi nghiệp vụ |
| `InterruptedException` (checked) | Thread đang đợi bị interrupt | Giữ lại tín hiệu `Thread.currentThread().interrupt()` nếu không truyền tiếp |
| `TimeoutException` (checked, chỉ với timed get) | Caller chờ quá hạn, Future chưa hoàn tất | Fallback/return timeout; quyết định riêng có hủy task không |
| `CancellationException` (unchecked) | Future bị hủy | Đường xử lý cancellation, không lấy giá trị như thành công |

### `ExecutionException` chứa lỗi gốc, không thay nó

Giả sử task ném `IllegalStateException("invalid-state")`. Nếu task đã được chấp nhận thông qua `submit(Callable)`, ngoại lệ được lưu ở Future. Caller gọi `get()` và nhận `ExecutionException`. Hãy dùng `ex.getCause()` để biết lỗi gốc:

~~~java
try {
    Integer value = future.get();
    use(value);
} catch (ExecutionException ex) {
    Throwable rootCause = ex.getCause();
    reportFailure(rootCause);
}
~~~

Đây là **fragment**, cần biến/hàm của ứng dụng, imports và xử lý/khai báo `InterruptedException`. Đừng log duy nhất dòng `ExecutionException` rồi bỏ qua stack trace/cause. Nhưng cũng không luôn “ném cause” trực tiếp mà không phân loại: lỗi kết nối, validation và lỗi lập trình có thể cần chính sách khác nhau.

### `InterruptedException` không có nghĩa worker đã thất bại

`get()` ném `InterruptedException` khi **thread caller đang đợi** bị interrupt. Điều đó **không tự chứng minh** task ở worker đã thất bại hoặc dừng. Nếu không có trách nhiệm xử lý interrupt ở tầng hiện tại, bạn có thể truyền tiếp exception hoặc phục hồi interrupt flag rồi thoát đường xử lý:

~~~java
try {
    return future.get();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Waiting thread interrupted", ex);
}
~~~

Đây là fragment bên trong method có kiểu trả về phù hợp và cần xử lý `ExecutionException`; không phải class độc lập. Việc phục hồi interrupt là quan trọng vì nhiều blocking APIs xóa cờ interrupt khi ném `InterruptedException`. Không tự động tiếp tục chờ vô hạn và nuốt tín hiệu đó.

### `TimeoutException` không phải `ExecutionException`

Khi `future.get(1, TimeUnit.SECONDS)` hết hạn, caller có thể nhận `TimeoutException` ngay cả nếu worker hoàn tất **sau đó một phần nghìn giây**. Không đọc `isDone()` ở thời điểm khác rồi suy ngược rằng timeout trước đó là không thể: hai hành động xảy ra ở hai mốc thời gian. Timeout là kết quả của việc **đợi quá lâu**, không phải kết luận task chắc chắn sai.

## 5. Ví dụ Java 17+ hoàn chỉnh: kết quả và lỗi

Lưu chương trình dưới đây thành `FutureBasicsDemo.java`. Nó nộp một task thành công, một task thất bại và một `Runnable`, rồi lần lượt đọc Future. Không phụ thuộc Spring để bạn hiểu hợp đồng nền tảng trước.

~~~java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class FutureBasicsDemo {

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> ok = pool.submit(() -> 21 * 2);

            Future<Integer> failed = pool.submit(
                    (Callable<Integer>) () -> {
                        throw new IllegalStateException("invalid-state");
                    });

            Future<?> sideEffect = pool.submit((Runnable) () -> { });

            System.out.println("value=" + ok.get());

            try {
                failed.get();
                System.out.println("unexpected-success");
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                System.out.println("error="
                        + cause.getClass().getSimpleName()
                        + ":" + cause.getMessage());
            }

            System.out.println("runnableValue=" + sideEffect.get());
            System.out.println("okDone=" + ok.isDone());
            System.out.println("failedDone=" + failed.isDone());

        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac FutureBasicsDemo.java
java FutureBasicsDemo
~~~

**Đầu ra do chương trình in:**

~~~text
value=42
error=IllegalStateException:invalid-state
runnableValue=null
okDone=true
failedDone=true
~~~

**Giải thích:**

1. `ok` đại diện cho `Callable<Integer>` trả `42`, nên `ok.get()` là `42`.
2. `failed` là task đã **được chấp nhận** nhưng ném `IllegalStateException`; `failed.get()` ném `ExecutionException` với cause chứa `invalid-state`.
3. `sideEffect` là `Runnable` không trả payload; `get()` thành công trả `null`.
4. Cả `ok.isDone()` và `failed.isDone()` đều là `true`. Đây là minh chứng rõ rằng **done không có nghĩa success**.
5. `shutdown()` và `awaitTermination()` dọn pool; không để lại non-daemon worker. Nếu hết thời gian dọn, ví dụ gọi `shutdownNow()` để yêu cầu dừng.

Code chỉ in từ **main** sau khi đợi các Future, không in từ nhiều worker đồng thời nên thứ tự dòng đầu ra ổn định dù thứ tự **thực thi** của ba task không được đảm bảo.

## 6. Ví dụ Java 17+ hoàn chỉnh: timeout không tự hủy task

Ví dụ thứ hai sử dụng hai `CountDownLatch`: `started` xác nhận task đã bắt đầu; `release` giữ task lại cho đến khi main chủ động cho phép hoàn thành. Nhờ vậy có thể xác minh timeout **mà không dựa vào `Thread.sleep()` hoặc tốc độ máy**.

Lưu thành `FutureTimeoutDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class FutureTimeoutDemo {

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<String> pending = pool.submit(() -> {
                started.countDown();
                release.await();
                return "ready";
            });

            started.await(); // task đã bắt đầu

            try {
                pending.get(100, TimeUnit.MILLISECONDS);
                System.out.println("unexpected-value");
            } catch (TimeoutException ex) {
                System.out.println("timed-out=true");
            }

            System.out.println("doneBeforeRelease=" + pending.isDone());

            release.countDown();
            System.out.println("value=" + pending.get(5, TimeUnit.SECONDS));

        } finally {
            release.countDown(); // tránh giữ worker nếu caller lỗi
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac FutureTimeoutDemo.java
java FutureTimeoutDemo
~~~

Đầu ra:

~~~text
timed-out=true
doneBeforeRelease=false
value=ready
~~~

Lần `get(100, MILLISECONDS)` đầu tiên timeout vì task vẫn đang chờ `release`. Khi đó `isDone()` là `false`, chứng tỏ **timeout của caller không kết thúc task**. Sau khi `release.countDown()`, worker tiếp tục, và `get(5, SECONDS)` trả `"ready"`.

Trong hệ thống thực, nếu task đang gọi HTTP hoặc JDBC thay vì `CountDownLatch.await()`, timeout của `Future.get` vẫn không tự động làm HTTP request hay DB query dừng. Hãy đặt timeout riêng cho downstream I/O, giới hạn concurrency, và cân nhắc hủy tác vụ theo đúng hợp đồng nghiệp vụ.

## 7. Cancellation: Future có thể hủy, nhưng không phải công tắc dừng tuyệt đối

`future.cancel(boolean mayInterruptIfRunning)` yêu cầu hủy tác vụ; kết quả phụ thuộc trạng thái và implementation:

- Nếu Future đã hoàn tất, `cancel(...)` thường trả `false` và không thay đổi kết quả.
- Nếu tác vụ chưa bắt đầu và hủy thành công, tác vụ không nên chạy.
- Nếu tác vụ đang chạy, `cancel(true)` cho phép implementation thử **interrupt worker thread** khi biết worker. Task phải hợp tác với interrupt và khả năng hủy của I/O bên dưới.
- `cancel(false)` không yêu cầu interrupt worker đang chạy; điều đó không chứng minh mọi tác dụng phụ sẽ ngừng xảy ra. Đừng hiểu `cancel` là một giao dịch rollback.
- Sau khi Future hủy thành công, `isCancelled()` và `isDone()` có thể đều là `true`; `get()` ném `CancellationException`.

Để quan sát rõ, dùng `FutureTask` của JDK thông qua executor. Lưu thành `FutureCancelDemo.java`:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class FutureCancelDemo {

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<String> task = pool.submit(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return "interrupted";
                }
                return "finished";
            });

            started.await(); // task đang chạy, chưa có kết quả
            boolean requested = task.cancel(true);

            System.out.println("cancelRequested=" + requested);
            System.out.println("isCancelled=" + task.isCancelled());
            System.out.println("isDone=" + task.isDone());

            try {
                task.get();
                System.out.println("unexpected-value");
            } catch (CancellationException ex) {
                System.out.println("get=CancellationException");
            }

        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac FutureCancelDemo.java
java FutureCancelDemo
~~~

Đầu ra kỳ vọng ổn định với `ExecutorService.newSingleThreadExecutor()` và task được giữ bởi latch:

~~~text
cancelRequested=true
isCancelled=true
isDone=true
get=CancellationException
~~~

Chú ý: dòng `cancelRequested=true` thể hiện **yêu cầu hủy được Future chấp nhận**, không bảo đảm mọi side effect đã hoặc sẽ xảy ra trong task đều được hoàn tác. Trong ví dụ, task chờ ở điểm hỗ trợ interrupt; một tác vụ thực thi mã không kiểm tra interrupt hoặc một thư viện I/O không đáp ứng interrupt có thể không dừng ngay. Khi task không được phép làm dở giao dịch nghiệp vụ, cần thiết kế cancellation và transaction rõ ràng.

### Sự khác nhau của CompletableFuture.cancel

`CompletableFuture` **implements Future**, nhưng JDK quy định `CompletableFuture.cancel(boolean)` xem việc hủy như **exceptional completion**; cờ `mayInterruptIfRunning` không có tác dụng kiểm soát interrupt trực tiếp trên tác vụ như ở một `FutureTask` thông thường. Đừng chuyển từ `FutureTask` sang `CompletableFuture` rồi mặc định cơ chế hủy thực thi bên dưới giống hệt.

## 8. Tích hợp Spring: AsyncTaskExecutor trả Future

Trong Spring, `org.springframework.core.task.TaskExecutor` cơ bản có `execute(Runnable)` và trả `void`. Interface `AsyncTaskExecutor` mở rộng nó bằng các overload `submit`:

~~~java
// Chữ ký API, không phải file chạy độc lập:
Future<?> submit(Runnable task);
<T> Future<T> submit(Callable<T> task);
~~~

Spring Framework 6.1.3 `AsyncTaskExecutor` hỗ trợ hai phương thức trên. `ThreadPoolTaskExecutor` là implementation phổ biến để quản lý nhóm worker threads và queue. Nếu khai báo biến kiểu `TaskExecutor`, bạn **không thể gọi `submit` qua kiểu tĩnh đó**; hãy inject `AsyncTaskExecutor` hoặc một implementation phù hợp.

### 8.1. Ví dụ Spring Framework 6.1.3 độc lập (Maven)

Ví dụ sau chạy bằng `main`, **không cần Spring Boot server hay `@EnableAsync`**, bởi vì gọi `submit` trực tiếp trên executor đã khởi tạo. Cấu trúc:

~~~text
spring-future-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── SpringFutureDemo.java
~~~

Tệp `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-future-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.3</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <mainClass>demo.SpringFutureDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Tệp `src/main/java/demo/SpringFutureDemo.java`:

~~~java
package demo;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public final class SpringFutureDemo {

    public static void main(String[] args) throws Exception {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(8);
        pool.setThreadNamePrefix("spring-future-");
        pool.initialize();

        try {
            AsyncTaskExecutor executor = pool;

            Future<String> ok = executor.submit(() -> "report-ready");
            Future<Integer> bad = executor.submit(
                    (Callable<Integer>) () -> {
                        throw new IllegalArgumentException("invalid-id");
                    });

            System.out.println("springValue=" + ok.get(5, TimeUnit.SECONDS));

            try {
                bad.get(5, TimeUnit.SECONDS);
                System.out.println("unexpected-value");
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                System.out.println("springCause="
                        + cause.getClass().getSimpleName()
                        + ":" + cause.getMessage());
            }

        } finally {
            pool.shutdown();
        }
    }
}
~~~

Trong thư mục `spring-future-demo` chạy:

~~~bash
mvn -q compile exec:java
~~~

Đầu ra do chương trình Java in (không tính log riêng từ Maven):

~~~text
springValue=report-ready
springCause=IllegalArgumentException:invalid-id
~~~

Ở đây Spring `ThreadPoolTaskExecutor` nhận cả task thành công và task thất bại. Cả hai Future đều là handle của công việc được nộp. `get` chờ hoàn tất và cung cấp giá trị hoặc `ExecutionException` bao quanh nguyên nhân của lỗi. `pool.shutdown()` dọn executor được tạo thủ công; trong ứng dụng Spring thật, hãy đăng ký pool làm bean singleton để container quản lý lifecycle.

### 8.2. Rejection xảy ra trước khi có Future

`AsyncTaskExecutor.submit(...)` có thể ném `TaskRejectedException` **ngay tại điểm gửi** nếu pool bị shutdown, queue đầy và không thể mở rộng worker. Khi đó lệnh `Future<T> future = executor.submit(...)` **không hoàn thành việc gán biến**. Không gọi `future.get()` để tìm exception nếu Future chưa từng được trả về.

~~~java
// Fragment trong service có biến executor kiểu AsyncTaskExecutor.
try {
    Future<String> future = executor.submit(() -> "ok");
    return future;
} catch (org.springframework.core.task.TaskRejectedException ex) {
    // Ghi metric/log cho quá tải hoặc lifecycle shutdown.
    throw ex;
}
~~~

Đoạn này không độc lập; giả sử method bao quanh có kiểu trả `Future<String>`. `TaskRejectedException` của Spring kế thừa `RejectedExecutionException` của JDK. Nó khác `ExecutionException` của task **đã được chấp nhận**.

## 9. Spring @Async: trả Future từ phương thức nghiệp vụ

Spring `@Async` cho phép một method được gọi bất đồng bộ thông qua interceptor/proxy khi `@EnableAsync` đã bật và bean/caller đúng điều kiện. Method có thể khai báo kiểu trả về `Future<T>` hoặc `CompletableFuture<T>` thay vì `void`. Caller sẽ nhận một Future đại diện cho lời gọi phương thức bất đồng bộ.

Ví dụ cấu hình trong một ứng dụng Spring đã có component scanning (các class là **fragment kiến trúc**, không phải project `main` độc lập):

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class ReportAsyncConfig {
    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("report-");
        return executor;
    }
}
~~~

Method nghiệp vụ nằm trong một bean khác:

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    @Async("reportExecutor")
    public CompletableFuture<String> generate(String id) {
        String result = "report:" + id;
        return CompletableFuture.completedFuture(result);
    }
}
~~~

Ví dụ caller **được Spring quản lý** và đã inject `ReportService`:

~~~java
CompletableFuture<String> result = reportService.generate("A42");
String value = result.get(5, TimeUnit.SECONDS); // "report:A42"
~~~

Đoạn caller là fragment; cần khai báo/tiêm `reportService`, import `TimeUnit`, khai báo hoặc xử lý checked exceptions. Spring proxy nộp lời gọi sang executor và trả Future cho caller. Khi worker chạy target method, method có thể trả `CompletableFuture.completedFuture(...)` như phương tiện cung cấp giá trị; **Future mà caller nhận từ proxy** mới đại diện cho công việc bất đồng bộ.

**Điều kiện thường bị quên:**

- Gắn `@EnableAsync` trong configuration và đảm bảo service là bean do Spring quản lý.
- Gọi từ bean khác **qua Spring proxy**. Self-invocation (`this.generate(...)` trong chính service) thường **bỏ qua proxy** ở chế độ mặc định `AdviceMode.PROXY`.
- `@Async("reportExecutor")` chọn executor đúng tên; nếu dùng sai tên hoặc caller tạo service bằng `new`, hành vi không như kỳ vọng.
- Đừng gắn `@Async` lên method khai báo **trong lớp `@Configuration`**: Javadoc Spring ghi rõ không hỗ trợ trường hợp đó. Để configuration khai báo executor và business method nằm trong service.
- Với `@Async void`, caller **không có Future** để đọc exception; Spring hỗ trợ `AsyncUncaughtExceptionHandler` hoặc logging theo cấu hình. Nếu cần kiểm soát lỗi, ưu tiên API trả Future/CompletableFuture khi phù hợp.

## 10. So sánh Future và CompletableFuture

`CompletableFuture<T>` **là một implementation của `Future<T>`** nhưng còn implements `CompletionStage<T>`. Nó có các API ghép xử lý bất đồng bộ, ví dụ `thenApply`, `thenCombine`, `thenCompose` và `exceptionally`.

| Nhu cầu | `Future<T>` thuần | `CompletableFuture<T>` |
| --- | --- | --- |
| Chờ task và lấy kết quả | `get()` | `get()` hoặc `join()` |
| Kiểm tra done / cancel | Có | Có |
| Chờ có timeout | `get(timeout, unit)` | `get(timeout, unit)`; ngoài ra có API timeout riêng |
| Ghép nhiều task mà không đợi từng Future bằng cách blocking | Không có API chaining trong interface | Có `thenCombine`, `thenCompose`, `allOf` |
| Khôi phục giá trị khi task lỗi | Chủ động `try/catch` quanh `get` | Có `exceptionally`/`handle` |
| Cách exception xuất hiện ở `join()` | Không có `join()` | `CompletionException` (unchecked) khi lỗi |
| Hành vi `cancel(true)` | Phụ thuộc implementation `Future` | Không dùng cờ `mayInterruptIfRunning` để ngắt tác vụ theo hợp đồng `CompletableFuture` |

Nếu bài toán chỉ cần “nộp công việc, thỉnh thoảng chờ kết quả”, `Future` rất phù hợp. Nếu cần **ghép nhiều bước không chặn**, `CompletableFuture` thường phù hợp hơn.

Trong Spring Framework 6.0+, `AsyncTaskExecutor` còn có:

~~~java
// Chữ ký minh họa (Spring 6.0+):
CompletableFuture<Void> submitCompletable(Runnable task);
<T> CompletableFuture<T> submitCompletable(Callable<T> task);
~~~

Điểm quan trọng: các overload `submitCompletable` **chỉ có từ Spring Framework 6.0**. Với Spring 5.x, có thể dùng `CompletableFuture.supplyAsync(..., taskExecutor)` hoặc `runAsync(..., taskExecutor)`, nhưng cần hiểu `Supplier` không khai báo checked exception như `Callable`. Không lẫn API Java 17, Spring version và kiểu interface khi nâng cấp dự án.

### Ví dụ ghép hai Future khác CompletableFuture thế nào?

Với hai `Future<Integer>` thuần, thường bạn phải `get()` để lấy từng kết quả:

~~~java
Future<Integer> a = executor.submit(() -> 10);
Future<Integer> b = executor.submit(() -> 20);
int sum = a.get() + b.get(); // 30
~~~

Đây là fragment với `executor` kiểu `ExecutorService` và method bao quanh xử lý checked exceptions. Hai task được nộp **trước khi chờ**, nên có thể chạy song song, nhưng `get()` vẫn chặn caller.

Với `CompletableFuture`, có thể mô tả bước ghép để chạy khi cả hai hoàn tất:

~~~java
CompletableFuture<Integer> a =
        CompletableFuture.supplyAsync(() -> 10, executor);
CompletableFuture<Integer> b =
        CompletableFuture.supplyAsync(() -> 20, executor);

CompletableFuture<Integer> sum =
        a.thenCombine(b, Integer::sum);
~~~

Đây cũng là fragment, yêu cầu `executor` là `Executor` và các import `CompletableFuture`. Nếu cần giữ concurrency trong Spring, truyền **executor tường minh** thay vì vô tình dùng `ForkJoinPool.commonPool()`. `sum` có thể được trả lên tầng khác mà không cần chặn tại vị trí tạo nó.

## 11. Tránh tự gây nghẽn thread pool khi đợi Future

Một lỗi phổ biến là **task A đang chạy trên pool có một worker**, rồi A gửi task B vào cùng pool và gọi `B.get()`. A giữ worker duy nhất để đợi B; B không có worker để chạy. Đây là dạng **thread-starvation deadlock**.

~~~text
Pool có đúng 1 worker:
  worker chạy task A
      |
      +-- submit(task B) vào cùng pool
      |
      +-- B.get() --> A bị chặn
                      |
                      +-- B đang ở queue,
                          nhưng worker vẫn bị A giữ
~~~

Không giải quyết bằng việc đổi `get()` thành `get(timeout)` rồi xem như hết vấn đề: timeout chỉ giúp A cuối cùng ngừng đợi, nhưng kiến trúc vẫn thiếu worker nếu cùng cách làm. Cân nhắc tách các bước phụ thuộc thành composition bất đồng bộ, sử dụng executor phù hợp, không chặn worker vào task con nằm trong cùng pool nhỏ hoặc phân rã luồng xử lý.

Ngoài ra, `isDone()` liên tục trong một vòng `while(!future.isDone())` mà không ngủ/đợi là **busy-wait**, gây lãng phí CPU. Dùng `get` ở ranh giới cần dữ liệu, callback của `CompletableFuture` hoặc các API đồng bộ chuyên dụng theo mục tiêu.

## 12. Checklist áp dụng Future trong ứng dụng thật

1. **Chọn API có kết quả:** `ExecutorService.submit(Callable)` hoặc `AsyncTaskExecutor.submit(Callable)` trả `Future<T>`; `execute` không cho handle.
2. **Nộp các task độc lập trước khi chờ:** tránh serial hóa do gọi `get()` ngay sau từng `submit` nếu không cần.
3. **Đặt giới hạn chờ ở ranh giới cần SLA:** dùng `get(timeout, unit)` thay vì đợi vô thời hạn cho remote I/O không đáng tin; có timeout riêng tại HTTP/JDBC.
4. **Tách đường lỗi:** `TaskRejectedException` tại **submit** khác `ExecutionException` tại **get**. Đừng để một catch khái quát che hai nguyên nhân.
5. **Kiểm tra cause:** log context có ích và `getCause()` thay vì chỉ message của `ExecutionException`.
6. **Xử lý interrupt:** nếu catch `InterruptedException` mà không truyền tiếp, phục hồi interrupt flag.
7. **Không coi done là thành công:** `isDone` có thể đúng khi task failed hoặc cancelled.
8. **Cân nhắc cancellation hợp tác:** `cancel(true)` chỉ thử interrupt khi implementation hỗ trợ; side effects, transaction và I/O không tự rollback.
9. **Không bỏ Future của task quan trọng:** nếu caller không bao giờ gọi `get` hay đăng ký cơ chế quan sát, exception trong task có thể không được xử lý đúng trách nhiệm.
10. **Đo pool và queue:** backlog, số worker, task duration, timeout và rejection rate giúp phát hiện quá tải; đừng tăng queue vô hạn chỉ để che lỗi.
11. **Tránh chờ trong cùng pool nhỏ:** giữ worker để đợi Future phụ thuộc có thể làm pool không tiến triển.
12. **Trong Spring, đảm bảo async thực sự chạy:** service do context quản lý, lời gọi qua proxy, `@EnableAsync` và executor name đúng.

### Câu hỏi tự kiểm tra

**Câu 1.** `future.isDone()` bằng `true` có chứng minh task thành công không?  
**Đáp án:** Không. `isDone` đúng khi hoàn tất bình thường, ném lỗi hoặc bị hủy.

**Câu 2.** Khi một `Callable` ném `IllegalArgumentException` trong worker sau khi task được nhận, `future.get()` báo gì?  
**Đáp án:** `ExecutionException`; lỗi gốc nằm trong `getCause()`.

**Câu 3.** `future.get(100, MILLISECONDS)` ném `TimeoutException`. Task có bị dừng ngay không?  
**Đáp án:** Không. Chỉ việc **chờ của caller** hết thời gian; task vẫn có thể chạy tiếp.

**Câu 4.** Nếu caller bị interrupt trong lúc đợi, điều đó cho biết worker đã thất bại chưa?  
**Đáp án:** Chưa. `InterruptedException` thuộc thread đang chờ; cần xử lý interrupt, không tự suy luận tình trạng worker.

**Câu 5.** Spring `TaskExecutor` chỉ khai báo `execute`. Làm thế nào để lấy `Future<String>` từ một tác vụ trả String?  
**Đáp án:** Dùng `AsyncTaskExecutor` hoặc implementation phù hợp và gọi `submit(Callable<String>)`. Với `@Async` có thể khai báo return type `Future<String>` hoặc `CompletableFuture<String>` trên method của bean được Spring xử lý.

**Câu 6.** Tại sao `future.get()` không bắt được việc executor từ chối task lúc submit?  
**Đáp án:** Nếu bị từ chối, `submit` đã ném exception **trước khi trả về Future**; phải catch tại điểm nộp.

**Câu 7.** Vì sao hai `Future` được nộp vào pool một worker rồi task đầu chờ `get` của task thứ hai có thể deadlock?  
**Đáp án:** Task đầu giữ worker duy nhất; task thứ hai nằm trong queue mà không có worker để thực thi, nên lời đợi không được đáp ứng.

**Câu 8.** `CompletableFuture` khác `Future` thuần ở điểm nào hữu ích cho quy trình nhiều bước?  
**Đáp án:** Có `CompletionStage` với các phương thức composition/callback/khôi phục lỗi, tránh phải chặn caller tại từng bước.

## 13. Tài liệu tham khảo chính thức

- [Java SE 17 — Future Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): định nghĩa `get`, timeout, exception, cancellation, `isDone` và quan hệ happens-before.
- [Java SE 17 — ExecutorService Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): `submit`, `shutdown` và vòng đời executor.
- [Java SE 17 — FutureTask Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/FutureTask.html): một implementation cho phép dùng Future với Runnable.
- [Java SE 17 — CompletableFuture Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): `CompletionStage`, `join`, chaining, lỗi và hủy.
- [Spring Framework 6.1.3 — AsyncTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/core/task/AsyncTaskExecutor.html): `submit` trả `Future` và `submitCompletable` (từ Spring 6.0).
- [Spring Framework 6.1.3 — ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): triển khai pool, queue, submit và TaskRejectedException.
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): `@Async`, Future return types và xử lý exception.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-results-003">CompletableFuture cho phép kết hợp các bước xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# `CompletableFuture` cho phép kết hợp các bước xử lý

**Module:** Spring Framework - concurrency  
**Nhóm:** Kết quả bất đồng bộ  
**Mục tiêu:** Biết mô hình hóa một quy trình gồm nhiều bước không đồng bộ bằng `CompletableFuture<T>`/`CompletionStage<T>`, chọn đúng `thenApply`, `thenCompose`, `thenCombine`, `allOf` và các nhánh xử lý lỗi, đồng thời hiểu ranh giới executor, timeout và cách tích hợp với Spring `@Async`.

## 1. Từ một Future đến một luồng công việc

Trong ứng dụng Spring, việc tạo báo giá có thể cần:

1. Lấy giá sản phẩm.
2. Tính thuế từ giá — **phụ thuộc vào bước 1**.
3. Lấy phí vận chuyển — **độc lập với việc tính thuế**.
4. Ghép giá có thuế và phí vận chuyển thành tổng tiền.
5. Dựa trên tổng tiền, gọi dịch vụ giữ chỗ hàng — **phụ thuộc vào bước 4 và cũng trả về future**.
6. Lấy loại khách hàng độc lập; chỉ khi cả phần giữ chỗ và loại khách hàng hoàn thành thì tạo kết quả.
7. Nếu một bước lỗi hoặc quá hạn, chuyển lỗi, dùng fallback đúng phạm vi hoặc từ chối theo hợp đồng nghiệp vụ.

Viết kiểu blocking truyền thống:

```java
int price = priceFuture.get();          // Chờ giá.
int shipping = shippingFuture.get();    // Chờ phí vận chuyển.
String reservation = reserve(total).get(); // Chờ tác vụ tiếp.
```

Đây là đoạn **minh họa**, chưa đủ biến `priceFuture`, `shippingFuture`, `total` và `reserve` để biên dịch riêng. Mỗi `get()` có thể chặn thread hiện tại, và việc lấy kết quả từng bước dễ làm người viết đặt các điểm chờ không cần thiết.

Với `CompletableFuture`, ta mô tả **quan hệ phụ thuộc** giữa các kết quả:

```text
getPrice() ------- thenApply(tính thuế) ---+
                                           +-- thenCombine(tổng) -- thenCompose(giữ chỗ) --+
getShipping() -----------------------------+                                             |
                                                                                         +-- allOf --> kết quả
getCustomerTier() ----------------------------------------------------------------------+
```

Mỗi mũi tên là một **stage** (bước phụ thuộc). Stage phía sau được kích hoạt theo điều kiện hoàn thành của stage phía trước, thay vì buộc thread đang xây pipeline chờ ở từng bước. **Kết hợp bất đồng bộ không đồng nghĩa mọi đoạn code đều chạy trên thread khác**; thread thực thi còn tùy executor và cách dùng các phương thức có hậu tố `Async`.

## 2. Bản chất của `CompletableFuture`

`java.util.concurrent.CompletableFuture<T>` (có từ Java 8; dùng tốt trong Java 17+) vừa:

- Triển khai `Future<T>`: biểu diễn một kết quả có thể lấy bằng `get()`, theo dõi `isDone()`, `cancel()`.
- Triển khai `CompletionStage<T>`: mô tả các thao tác **tiếp nối, ghép, rẽ nhánh và xử lý lỗi** khi kết quả sẵn sàng.
- Cho phép hoàn thành thủ công bằng `complete(value)` hoặc `completeExceptionally(error)`; thường được dùng khi thích nghi API callback sang mô hình Future.

Một future có thể ở trạng thái **chưa hoàn thành**, **hoàn thành bình thường** với giá trị `T`, hoặc **hoàn thành ngoại lệ**. Một trong các thao tác hoàn thành thắng cuộc; kết quả đã hoàn thành thông thường không được ghi đè bằng `complete` lần nữa.

```java
CompletableFuture<Integer> result = new CompletableFuture<>();

boolean first = result.complete(42);   // true: lần hoàn thành đầu tiên.
boolean second = result.complete(99);  // false: đã hoàn thành.
System.out.println(result.join());     // 42.
```

Đây là đoạn Java 17 hợp lệ khi đặt vào một method và import `java.util.concurrent.CompletableFuture`. `join()` **cũng chờ** nếu future chưa xong; tên `join` không làm việc chờ trở thành non-blocking.

### `completedFuture`, `failedFuture`, `supplyAsync` và `runAsync`

| Cách tạo | Kết quả |
| --- | --- |
| `CompletableFuture.completedFuture(42)` | Đã hoàn thành thành công với `42`; không tạo task mới |
| `CompletableFuture.failedFuture(error)` | Đã hoàn thành ngoại lệ; hữu ích trong kiểm thử/đường lỗi |
| `CompletableFuture.supplyAsync(supplier, executor)` | Giao một phép tính **có giá trị trả về** cho executor chỉ định |
| `CompletableFuture.runAsync(runnable, executor)` | Giao tác vụ `void`, trả `CompletableFuture<Void>` |
| `new CompletableFuture<>()` | Future chưa hoàn thành; bên khác phải gọi `complete`/`completeExceptionally` hoặc tương đương |

Chỉ tạo `new CompletableFuture<>()` không làm một tác vụ tự chạy. Chỉ gọi `completedFuture` không làm một method đang chạy trở thành asynchronous.

## 3. Chọn toán tử kết hợp: một nguồn, nguồn phụ thuộc, hai nguồn độc lập

| Tình huống | API | Đầu vào → đầu ra | Chờ điều kiện nào? |
| --- | --- | --- | --- |
| Biến đổi một kết quả đã có | `thenApply(fn)` | `T -> U` | Future nguồn hoàn thành bình thường |
| Làm việc phụ không trả dữ liệu | `thenAccept(consumer)` | `T -> Void` | Future nguồn hoàn thành bình thường |
| Chạy bước tiếp theo không dùng giá trị | `thenRun(runnable)` | `T -> Void` | Future nguồn hoàn thành bình thường |
| Bước tiếp theo **tự trả CompletionStage** | `thenCompose(fn)` | `T -> CompletionStage<U>`, kết quả ghép `U` | Nguồn xong rồi stage được trả về cũng xong |
| Kết hợp kết quả **hai future độc lập** | `thenCombine(other, fn)` | `T + U -> V` | **Cả hai** hoàn thành bình thường |
| Hành động dùng hai kết quả | `thenAcceptBoth(other, action)` | `T + U -> Void` | Cả hai hoàn thành bình thường |
| Đợi **tất cả** một danh sách future | `CompletableFuture.allOf(...)` | `... -> Void` | Tất cả hoàn thành |
| Lấy future hoàn thành **đầu tiên** | `CompletableFuture.anyOf(...)` | `... -> Object` | Một future hoàn thành, **kể cả ngoại lệ** |
| Xử lý thành công/lỗi để đổi kết quả | `handle((value, error) -> ...)` | `T hoặc lỗi -> U` | Nguồn hoàn thành theo bất kỳ cách nào |
| Chỉ phục hồi khi lỗi | `exceptionally(error -> fallback)` | Lỗi `T -> T` | Nguồn hoàn thành ngoại lệ |
| Quan sát thành công/lỗi | `whenComplete((value, error) -> ...)` | Thường giữ kết quả gốc | Nguồn hoàn thành theo bất kỳ cách nào |

**Ghi nhớ:** `thenApply` biến đổi **giá trị**; `thenCompose` nối một **công việc bất đồng bộ khác**; `thenCombine` ghép **hai việc độc lập**. Ba phương thức này giải quyết ba dạng phụ thuộc khác nhau.

## 4. `thenApply`: biến đổi kết quả của một bước

Giả sử giá gốc là 200, thuế được tính bằng 10%:

```java
CompletableFuture<Integer> price =
        CompletableFuture.completedFuture(200);

CompletableFuture<Integer> priceWithTax =
        price.thenApply(p -> p + p / 10);

System.out.println(priceWithTax.join()); // 220
```

`thenApply` nhận một hàm `T -> U`; kết quả được đặt trong **future mới**. Nó **không** thay đổi giá trị của `price`. Nếu nguồn hoàn thành ngoại lệ thì lambda `thenApply` **không được chạy theo đường thành công**; stage tiếp theo trở thành exceptional, trừ khi có cơ chế phục hồi phù hợp.

Dùng `thenApply` cho phép biến đổi đồng bộ **nhẹ và nhanh**, chẳng hạn mapping DTO, chuẩn hóa dữ liệu nhỏ, tính toán ít CPU. Nếu lambda phải chờ DB/HTTP hoặc tính CPU nặng, không nên vô tình chạy nó trên completion thread của một phần việc khác. Khi cần chủ động chọn pool cho phép biến đổi, dùng `thenApplyAsync(fn, executor)` và một executor có giới hạn phù hợp.

## 5. `thenCompose`: nối một tác vụ mới và loại bỏ future lồng nhau

Đây là khác biệt quan trọng nhất khi học kết hợp nhiều bước.

Giả sử sau khi biết tổng tiền, ta phải gọi một dịch vụ khác trả về `CompletableFuture<String>` chứa mã giữ chỗ:

```java
CompletableFuture<Integer> total =
        CompletableFuture.completedFuture(245);

CompletableFuture<String> reservation =
        total.thenCompose(amount ->
                CompletableFuture.supplyAsync(
                        () -> "RES-" + amount,
                        ioExecutor));
```

Ví dụ trên là **đoạn minh họa**, cần có biến `ioExecutor` kiểu `Executor` và import `CompletableFuture`. Khi total có giá trị, hàm trả về một stage khác. `thenCompose` trả về `CompletableFuture<String>` được hoàn thành theo stage bên trong.

Nếu viết bằng `thenApply`, kiểu kết quả trở thành **future lồng future**:

```java
CompletableFuture<CompletableFuture<String>> nested =
        total.thenApply(amount ->
                CompletableFuture.supplyAsync(
                        () -> "RES-" + amount,
                        ioExecutor));
```

Cần chờ/ghép thêm để có giá trị thật. Vì vậy, khi lambda **trả một future**, mặc định nên nghĩ tới `thenCompose` thay cho `thenApply`.

So sánh tương tự:

```text
thenApply(fn):    Future<T> + (T -> U)            -> Future<U>
thenCompose(fn):  Future<T> + (T -> Future<U>)     -> Future<U>
```

Không nhét `join()` bên trong lambda để “làm phẳng” thủ công:

```java
// Tránh: blocking trong continuation, dễ gây nghẽn/deadlock.
total.thenApply(amount -> reserveAsync(amount).join());
```

Đoạn minh họa trên giả sử `reserveAsync(amount)` có tồn tại. Nếu nó đặt task vào **chính pool có số worker nhỏ** và continuation đang chiếm một worker để chờ, có thể xảy ra starvation hoặc deadlock. Dùng `thenCompose(amount -> reserveAsync(amount))` để mô tả phụ thuộc mà **không giữ thread chờ**.

### `thenComposeAsync` khác `thenCompose` như thế nào?

`thenCompose` chạy **hàm tạo stage tiếp theo** theo quy tắc completion không-async; `thenComposeAsync(fn, executor)` chủ động giao việc chạy hàm đó sang executor. Nhưng **executor này không tự quyết định nơi chạy mọi task trong future mà `fn` trả về**: future bên trong có nguồn phát, network client, executor và quy tắc riêng. Phân biệt “chạy callback nối stage” với “chạy tác vụ nghiệp vụ bên trong”.

## 6. `thenCombine`: kết hợp hai kết quả độc lập

Giá có thuế và phí vận chuyển có thể được tìm đồng thời, rồi ghép khi cả hai xong:

```java
CompletableFuture<Integer> priceWithTax =
        CompletableFuture.supplyAsync(() -> 220, executor);

CompletableFuture<Integer> shipping =
        CompletableFuture.supplyAsync(() -> 25, executor);

CompletableFuture<Integer> grandTotal =
        priceWithTax.thenCombine(shipping, Integer::sum);

// Không có stage nào gọi get()/join() để chờ stage kia.
System.out.println(grandTotal.join()); // 245
```

Đoạn trên cần biến `executor` kiểu `Executor` trong chương trình thực tế. `thenCombine` chỉ chạy hàm khi **cả hai nguồn hoàn thành bình thường**. Nếu một nguồn lỗi, future kết quả cũng thất bại; không tự dùng giá trị `0` cho nguồn bị lỗi.

Sự khác biệt về cấu trúc:

- **Phụ thuộc tuần tự:** bước B cần giá trị A mới biết làm gì → `A.thenCompose(a -> B(a))`.
- **Độc lập:** có thể gửi A và B từ đầu, chỉ ghép kết quả sau → `A.thenCombine(B, combiner)`.
- **Đừng viết** `A.thenCompose(a -> B())` nếu B thực sự không phụ thuộc A: điều đó làm B khởi động chậm không cần thiết.

Nếu hai phép tính có cùng kết quả nhưng ưu tiên kết quả nào đến trước, `applyToEither` có thể hữu ích; nó kích hoạt khi có một nguồn **hoàn thành bình thường** phù hợp. Đừng nhầm với `anyOf`, vì `anyOf` có thể hoàn thành **ngoại lệ** ngay khi nguồn đầu tiên lỗi.

## 7. `allOf`: đợi nhiều nhánh; `anyOf`: lấy kết quả đầu tiên

### `CompletableFuture.allOf`

```java
CompletableFuture<String> a =
        CompletableFuture.completedFuture("A");
CompletableFuture<String> b =
        CompletableFuture.completedFuture("B");
CompletableFuture<String> c =
        CompletableFuture.completedFuture("C");

CompletableFuture<String> summary =
        CompletableFuture.allOf(a, b, c)
                .thenApply(ignored -> a.join() + b.join() + c.join());

System.out.println(summary.join()); // ABC
```

`allOf` trả `CompletableFuture<Void>`, **không trả một danh sách kết quả**. Chỉ sau khi `allOf` hoàn thành bình thường, việc lấy `a.join()`, `b.join()`, `c.join()` trong continuation trên không phải chờ thêm; chúng đã xong. Nếu một thành phần lỗi, `allOf` hoàn thành ngoại lệ; continuation thành công sẽ không chạy. Muốn tổng hợp cả **thành công lẫn lỗi của từng nguồn**, phải chuyển từng nhánh thành kết quả có trạng thái (`handle`) trước khi `allOf`.

Ví dụ tổng hợp theo **thứ tự input** (không phụ thuộc thứ tự chạy):

```java
List<CompletableFuture<String>> futures = List.of(a, b, c);

CompletableFuture<List<String>> values =
        CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]))
                .thenApply(ignored ->
                        futures.stream()
                                .map(CompletableFuture::join)
                                .toList());
```

Đoạn trên cần import `java.util.List` và `java.util.concurrent.CompletableFuture`; là phần code có thể đặt trong method. Khi một future lỗi, cả `values` sẽ lỗi; nó không mặc định bỏ qua nguồn thất bại.

### `CompletableFuture.anyOf`

```java
CompletableFuture<String> fast =
        CompletableFuture.completedFuture("cache");
CompletableFuture<String> slow =
        new CompletableFuture<>();

CompletableFuture<Object> first =
        CompletableFuture.anyOf(fast, slow);

System.out.println(first.join()); // cache
```

`anyOf` hoàn thành theo **future đầu tiên hoàn thành**, dù thành công hay ngoại lệ, và trả `Object` vì input có thể khác kiểu. Trong ví dụ này `fast` đã hoàn thành từ trước, nên kết quả xác định. Với hai nguồn thực sự tranh đua nhau, không thể dự đoán nguồn thắng nếu không kiểm soát lịch hoàn thành.

**Cảnh báo:** “đã có một kết quả” không có nghĩa các công việc còn lại tự dừng hoặc tự được cancel. Với các lời gọi HTTP tới hai nhà cung cấp, cần chính sách timeout/cancel/resilience thực tế nếu không muốn tiếp tục tiêu thụ tài nguyên.

### Không biến `allOf` thành “chạy tất cả song song” một cách thần kỳ

`allOf` chỉ **đợi** các future đã có. Tính song song phụ thuộc lúc **khởi động từng future** và tài nguyên executor. Nếu bạn tạo tất cả bằng `completedFuture` thì không có I/O nào phát sinh. Nếu từng future gọi API đồng bộ trên cùng pool 1 thread, chúng vẫn có thể chạy tuần tự dù dùng `allOf`.

## 8. `thenApply` và các phương thức hậu tố `Async`: thread nào chạy?

Các phương thức như `thenApply`, `thenCompose`, `thenCombine`, `whenComplete` **không có hậu tố `Async`** có thể chạy callback trên thread hoàn thành future nguồn, hoặc thread khác đang thực hiện thao tác completion. Nếu nguồn đã hoàn thành khi đăng ký callback, phần xử lý có thể chạy ngay trong thread đang đăng ký.

Các biến thể có hậu tố `Async`:

- `thenApplyAsync(fn)`, `thenComposeAsync(fn)` không truyền executor: với `CompletableFuture` thông thường, mặc định dùng `ForkJoinPool.commonPool()` của JDK (có quy tắc fallback khi common pool không hỗ trợ đủ parallelism).
- `thenApplyAsync(fn, executor)` và `thenComposeAsync(fn, executor)`: giao callback cho **executor chỉ định**, giúp kiểm soát thread/queue/rejection.
- `CompletableFuture.supplyAsync(supplier, executor)`: chủ động chạy supplier trong pool được chọn.
- **Khai báo `@Async` trên một Spring method không làm các lời gọi `thenApplyAsync()` không tham số trong code khác tự chuyển sang Spring executor.** Cần truyền executor rõ ràng khi muốn dùng cùng pool.

Ví dụ mapping nhẹ:

```java
CompletableFuture<OrderDto> dto =
        orderFuture.thenApply(OrderDto::from);
```

Ví dụ mapping CPU nặng được tách pool:

```java
CompletableFuture<byte[]> compressed =
        fileFuture.thenApplyAsync(
                bytes -> compress(bytes),
                cpuExecutor);
```

Hai đoạn trên là **minh họa trong ứng dụng**: cần các kiểu `OrderDto`, `orderFuture`, `fileFuture`, `compress` và `cpuExecutor` do dự án định nghĩa.

**Không mặc định coi `thenApplyAsync` nhanh hơn**: nó còn thêm một lần schedule/queue và có thể gây rejection. Chọn async boundary khi thật sự cần tách công việc khỏi completion thread, bảo vệ một tài nguyên hoặc phân loại loại tải.

## 9. Các kiểu lỗi và cách phục hồi có kiểm soát

Khi một stage hoàn thành ngoại lệ, các stage “thành công” phụ thuộc nó (ví dụ `thenApply`, `thenCompose`, `thenCombine`) thông thường cũng hoàn thành ngoại lệ; hàm success callback không chạy. Phải xử lý lỗi như một phần **của pipeline**, không trông đợi caller nhận trực tiếp exception từ worker.

| API | Khi được gọi? | Dùng để làm gì? | Giá trị stage sau |
| --- | --- | --- | --- |
| `exceptionally(error -> fallback)` | Chỉ khi lỗi | Phục hồi bằng giá trị cùng loại `T` | Bình thường nếu fallback không lỗi |
| `handle((value, error) -> newValue)` | Cả thành công và lỗi | Biến đổi cả hai nhánh thành một kết quả `U` | Tùy hàm xử lý |
| `whenComplete((value, error) -> log(...))` | Cả thành công và lỗi | Quan sát log/metric/cleanup | Thường giữ nguyên thành công/lỗi; callback cũng có thể ném |
| `exceptionallyCompose(error -> otherFuture)` (Java 12+) | Khi lỗi | Phục hồi bằng một **stage bất đồng bộ mới** | Theo stage fallback |
| `completeExceptionally(error)` | Được gọi chủ động | Đánh dấu **future hiện tại** lỗi nếu chưa hoàn thành | Boolean cho biết có thắng race hay không |

Ví dụ fallback cho một lỗi có thể chấp nhận:

```java
CompletableFuture<String> displayName =
        lookupNameAsync(userId)
                .exceptionally(error -> "Khách");

```

Đoạn này chưa độc lập: `lookupNameAsync(userId)` và `userId` thuộc ứng dụng. **Chỉ dùng fallback khi không phá hỏng bất biến nghiệp vụ.** Có thể dùng tên hiển thị mặc định, nhưng không được tự dùng `0` cho số tiền thanh toán nếu lỗi đang là không lấy được giá thật.

Ví dụ cần fallback bất đồng bộ:

```java
CompletableFuture<Profile> profile =
        fetchFromPrimary(userId)
                .exceptionallyCompose(error ->
                        fetchFromReplica(userId));
```

Cần định nghĩa `Profile`, `fetchFromPrimary`, `fetchFromReplica` trong ứng dụng; ví dụ chỉ mô tả flow. `exceptionallyCompose` tránh việc fallback stage bị lồng nhau và không cần chặn bằng `join()`.

### `handle` có thể vô tình nuốt lỗi

```java
CompletableFuture<Integer> parsed =
        CompletableFuture.<Integer>failedFuture(
                new IllegalArgumentException("invalid"))
                .handle((value, error) ->
                        error == null ? value : -1);
```

`parsed` hoàn thành bình thường với `-1`: lỗi gốc đã được **chuyển thành dữ liệu**. Đây là một quyết định rõ ràng của hợp đồng, không phải hiệu ứng phụ vô hại. Nếu không có fallback hợp lệ, ghi nhận lỗi rồi **duy trì** trạng thái lỗi.

### `whenComplete` dùng để quan sát, không phải phương thức thay giá trị

```java
CompletableFuture<String> result =
        workFuture.whenComplete((value, error) -> {
            if (error != null) {
                metrics.recordFailure(error);
            } else {
                metrics.recordSuccess();
            }
        });
```

Đây là đoạn minh họa (`workFuture`, `metrics` do ứng dụng cung cấp). Callback có thể được gọi trên thread không dự đoán trước khi dùng phiên bản không-Async. Callback cũng phải xử lý được exception của chính nó; nếu callback ném lỗi khi nguồn thành công, stage hậu quả có thể thành exceptional. Không đưa một công việc blocking kéo dài vào callback quan sát dùng chung.

## 10. `get()` và `join()`: điểm chặn thật sự nằm ở đâu?

Cả `get()` và `join()` **có thể block thread gọi** tới lúc future hoàn thành.

- `get()` có `InterruptedException`, `ExecutionException`; `get(timeout, unit)` còn có `TimeoutException` khi thread chờ quá thời gian. Phù hợp ở ranh giới cần xử lý checked exception/interrupt có chủ đích.
- `join()` trả giá trị hoặc ném unchecked `CompletionException` (hoặc trường hợp cancel là `CancellationException`); tiện khi các stage đã hoàn thành hoặc khi xử lý ở ranh giới ngoài cùng.
- `getNow(fallback)` không chờ, nhưng trả fallback nếu chưa xong; nếu đã hoàn thành ngoại lệ vẫn có thể ném.
- `isDone()` không có nghĩa thành công: có thể complete bình thường, lỗi hoặc cancel.
- `get(timeout)` **giới hạn thời gian chờ của caller**, không nhất thiết hoàn thành exceptionally hoặc dừng future gốc.

Đừng gọi `join` trong controller ngay sau khi tạo pipeline rồi tuyên bố endpoint đã non-blocking:

```java
// Có thể block Servlet thread tới khi xử lý xong.
String value = someFuture.join();
```

Trong Spring MVC, có thể trả `CompletableFuture<T>` hoặc `CompletionStage<T>` theo cơ chế async return type được hỗ trợ, hoặc dùng `DeferredResult`/`Callable` phù hợp, thay vì chặn request thread. Tuy nhiên endpoint trả future có thể vẫn **giữ HTTP response mở** chờ kết quả. Nếu cần trả `202 Accepted` ngay và xử lý sau, phải tạo hợp đồng job ID/trạng thái và khả năng lưu bền vững riêng.

## 11. Timeout, cancellation và nghĩa vụ dừng công việc

Java 9+ cung cấp:

```java
CompletableFuture<String> timed =
        remoteCallAsync()
                .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS);
```

Đây là **đoạn minh họa**; cần có `remoteCallAsync()` trả `CompletableFuture<String>`. `orTimeout` làm **chính CompletableFuture nhận lời gọi** hoàn thành ngoại lệ với `TimeoutException` nếu quá hạn. Nó **không tự hủy** network call, query DB hoặc task đang chạy. Các continuation đã xếp hàng cũng có thể còn hoạt động tùy thiết kế. Nếu muốn trả giá trị mặc định:

```java
CompletableFuture<String> withFallback =
        remoteCallAsync()
                .completeOnTimeout(
                        "tạm-không-có",
                        2, java.util.concurrent.TimeUnit.SECONDS);
```

`completeOnTimeout` cũng tác động lên **chính future gọi phương thức**, không phải “tạo một bản clone độc lập để timeout”. Nếu future được chia sẻ cho nhiều người tiêu thụ, việc đặt timeout có thể thay đổi kết quả mà những người tiêu thụ khác nhìn thấy. Cân nhắc tạo một dependent stage độc lập rồi đặt timeout lên stage đó khi cần cô lập chính sách timeout (và hiểu rõ cách lan truyền lỗi).

`cancel(true)` đối với `CompletableFuture` được xem như **hoàn thành ngoại lệ bằng `CancellationException`**; tham số `mayInterruptIfRunning` không bảo đảm ngắt thread thực thi supplier. Muốn hủy tác vụ thực, phải tích hợp đúng cancel API của HTTP/JDBC client, cooperative cancellation/interrupt hoặc token, cùng quy tắc đóng resource.

Timeout và cancel nên đi từ **deadline tổng** của yêu cầu, không phải mỗi bước tự nhận một timeout lớn rồi cộng dồn. Ví dụ một request có 2 giây end-to-end không thể cho ba lời gọi tuần tự mỗi lời gọi 2 giây mà vẫn đảm bảo SLA.

## 12. Chương trình Java 17 độc lập: pipeline tuần tự + song song + lỗi

Ví dụ sau có thể biên dịch bằng **JDK 17 trở lên**, không cần Spring. Nó minh họa:

- `supplyAsync` với `ExecutorService` được truyền tường minh.
- `thenApply` tính thuế sau khi có giá.
- `thenCombine` ghép giá với phí vận chuyển **độc lập**.
- `thenCompose` gọi bước giữ chỗ trả future mà không tạo future lồng nhau.
- `allOf` chờ kết quả giữ chỗ và hạng khách hàng.
- `exceptionally`, `handle`, `whenComplete` theo các hợp đồng khác nhau.
- `shutdown` executor có trách nhiệm, không đoán thứ tự worker.

Lưu nguyên văn **`CompletableFutureCompositionDemo.java`**:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CompletableFutureCompositionDemo {

    private static CompletableFuture<Integer> fetchBasePrice(Executor executor) {
        return CompletableFuture.supplyAsync(() -> 200, executor);
    }

    private static CompletableFuture<Integer> fetchShipping(Executor executor) {
        return CompletableFuture.supplyAsync(() -> 25, executor);
    }

    private static CompletableFuture<String> fetchCustomerTier(Executor executor) {
        return CompletableFuture.supplyAsync(() -> "GOLD", executor);
    }

    private static CompletableFuture<String> reserveInventory(
            int total, Executor executor) {
        return CompletableFuture.supplyAsync(
                () -> "RES-" + total, executor);
    }

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            CompletableFuture<Integer> withTax =
                    fetchBasePrice(executor)
                            .thenApply(price -> price + price / 10);

            // Được khởi tạo độc lập với withTax.
            CompletableFuture<Integer> shipping =
                    fetchShipping(executor);

            CompletableFuture<Integer> total =
                    withTax.thenCombine(shipping, Integer::sum);

            // Callback tạo ra một future khác: dùng thenCompose.
            CompletableFuture<String> reservation =
                    total.thenCompose(amount ->
                            reserveInventory(amount, executor));

            CompletableFuture<String> tier =
                    fetchCustomerTier(executor);

            CompletableFuture<String> report =
                    CompletableFuture.allOf(reservation, tier)
                            .thenApply(ignored ->
                                    reservation.join() + "/" + tier.join());

            System.out.println("total=" + total.join());
            System.out.println("reservation=" + reservation.join());
            System.out.println("report=" + report.join());

            CompletableFuture<String> recovered =
                    CompletableFuture.<String>failedFuture(
                            new IllegalStateException("service down"))
                            .exceptionally(error -> "fallback");

            System.out.println("recovered=" + recovered.join());

            CompletableFuture<String> handled =
                    CompletableFuture.<String>failedFuture(
                            new IllegalArgumentException("invalid"))
                            .handle((value, error) ->
                                    error == null ? value : "invalid-input");

            System.out.println("handled=" + handled.join());

            AtomicInteger observedCount = new AtomicInteger();
            CompletableFuture<String> observed =
                    CompletableFuture.completedFuture("ok")
                            .whenComplete((value, error) ->
                                    observedCount.incrementAndGet())
                            .thenApply(String::toUpperCase);

            System.out.println("observed=" + observed.join());
            System.out.println("observedCount=" + observedCount.get());
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
```

Chạy:

```bash
javac CompletableFutureCompositionDemo.java
java CompletableFutureCompositionDemo
```

**Stdout do chương trình in khi kết thúc bình thường:**

```text
total=245
reservation=RES-245
report=RES-245/GOLD
recovered=fallback
handled=invalid-input
observed=OK
observedCount=1
```

Giải thích:

1. `fetchBasePrice` trả `200`, `thenApply` thêm thuế 10% thành `220`.
2. Phí vận chuyển độc lập là `25`, `thenCombine` cho `245`.
3. `thenCompose` gửi bước giữ chỗ mới với tổng `245`, trả `RES-245` không lồng future.
4. `allOf` yêu cầu cả giữ chỗ và hạng `GOLD` hoàn thành; `report` ghép chúng theo thứ tự trong lambda, bất kể worker nào chạy xong trước.
5. Hai future thất bại được `exceptionally`/`handle` chuyển thành giá trị fallback minh họa. Trong nghiệp vụ tính tiền, việc fallback có hợp lệ hay không phải được đánh giá riêng.
6. `whenComplete` tăng bộ đếm đúng một lần rồi để giá trị `"ok"` đi qua `thenApply` thành `"OK"`.

Vì chương trình chỉ in ở thread `main` **sau khi chờ các future tương ứng** nên thứ tự và nội dung các dòng là xác định; thứ tự chạy thật của 4 worker không xác định. `Executors.newFixedThreadPool` có queue không giới hạn theo lượng bộ nhớ và chỉ được dùng để đơn giản hóa ví dụ; dịch vụ thật nên dùng executor giới hạn queue/admission phù hợp.

**Điểm học quan trọng:** pipeline thể hiện đúng *phụ thuộc* của công việc, nhưng tại cuối chương trình CLI vẫn cần `join()` để in kết quả sau khi hoàn tất. Điều này hoàn toàn khác việc `join()` ngay bên trong continuation hoặc trong request thread đang cần duy trì tính responsive.

## 13. Ghép với Spring `@Async` đúng ranh giới

Một Spring service có thể trả `CompletableFuture<T>` từ method `@Async`. Spring giao invocation qua executor đã cấu hình, sau đó caller kết hợp các future trả về bằng JDK API.

Trong một dự án **Spring Boot 3.x, Java 17+** có `spring-boot-starter-web` hoặc Spring Context phù hợp, ví dụ ba file sau minh họa cách nối các thành phần. Đây là **các class thuộc dự án Spring**, không phải một chương trình `javac` thuần độc lập; chương trình JDK chạy được nằm ở phần 12.

### 13.1. Executor có giới hạn và bật `@Async`

File `src/main/java/com/example/quote/AsyncConfig.java`:

```java
package com.example.quote;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("quoteIoExecutor")
    public ThreadPoolTaskExecutor quoteIoExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("quote-io-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
```

Đây là cấu hình **minh họa**, các giá trị 4/8/32 phải được đo với tải và giới hạn kết nối DB/HTTP thật. Executor là Spring bean nên container quản lý lifecycle của `ThreadPoolTaskExecutor`. Chỉ gắn `@EnableAsync` không tự bảo đảm các method được gọi đúng qua Spring proxy.

### 13.2. Hai nguồn kết quả độc lập

File `src/main/java/com/example/quote/QuoteDataService.java`:

```java
package com.example.quote;

import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class QuoteDataService {

    @Async("quoteIoExecutor")
    public CompletableFuture<Integer> fetchPrice(long productId) {
        // Mô phỏng đọc dữ liệu; ứng dụng thật dùng DB/API client có timeout.
        return CompletableFuture.completedFuture(200);
    }

    @Async("quoteIoExecutor")
    public CompletableFuture<Integer> fetchShipping(long addressId) {
        return CompletableFuture.completedFuture(25);
    }
}
```

Khi Spring proxy intercept lời gọi, method body chạy theo executor được chọn và trả một future **đã có kết quả từ bên trong worker**; caller nhận `CompletableFuture` đại diện lời gọi async. Không cần tạo `CompletableFuture.supplyAsync` thêm bên trong mỗi method chỉ để có một future, vì điều đó có thể tạo thêm một tầng scheduling và dùng pool khác ngoài dự tính.

### 13.3. Service orchestrator ghép future mà không chặn

File `src/main/java/com/example/quote/QuoteService.java`:

```java
package com.example.quote;

import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

@Service
public class QuoteService {

    private final QuoteDataService data;

    public QuoteService(QuoteDataService data) {
        this.data = data;
    }

    public CompletableFuture<Integer> calculateQuote(
            long productId, long addressId) {

        CompletableFuture<Integer> priceWithTax =
                data.fetchPrice(productId)
                        .thenApply(p -> p + p / 10);

        CompletableFuture<Integer> shipping =
                data.fetchShipping(addressId);

        return priceWithTax.thenCombine(shipping, Integer::sum);
    }
}
```

`QuoteService` **không cần** annotation `@Async` cho method orchestration này: nó chỉ xây pipeline từ các future mà `QuoteDataService` đã khởi chạy; không dùng `get()`/`join()` để chặn. Hai nguồn được khởi chạy độc lập, và `thenCombine` đợi khi chúng đều thành công.

Ví dụ này hoạt động nếu `QuoteService` và `QuoteDataService` là **hai Spring beans** được gọi đúng qua proxy. Nếu `fetchPrice()` gọi `this.fetchShipping()` bên trong cùng `QuoteDataService`, lời gọi nội bộ có thể bỏ qua async proxy.

Khi đưa ra HTTP endpoint, cần quyết định rõ:

- Trả kết quả cuối trong cùng request? Có thể cho framework xử lý response bất đồng bộ qua return type phù hợp, thay vì `.join()` trên Servlet thread.
- Trả `202` để xử lý lâu và theo dõi sau? Cần job ID, trạng thái, persistence/retry; `CompletableFuture` **không phải durable job queue**.
- Nếu `fetchPrice()` lỗi? Kết quả `calculateQuote()` thất bại; controller/global handler cần ánh xạ thành error đúng hợp đồng, không trả tổng tiền với giá `0` âm thầm.

## 14. Ranh giới executor và bẫy `commonPool`

`CompletableFuture` trong Spring có hai cách tạo công việc thường bị trộn lẫn:

1. **Spring `@Async("quoteIoExecutor")`:** proxy gửi invocation vào **Spring bean executor** đã chỉ định.
2. **JDK `CompletableFuture.supplyAsync(...)` hoặc `thenApplyAsync(...)` không truyền executor:** mặc định dùng cơ chế executor bất đồng bộ của JDK (thường `ForkJoinPool.commonPool()`), **không tự động dùng Spring bean**.

Nếu team muốn mọi I/O blocking qua một pool Spring có queue hữu hạn, dùng:

```java
CompletableFuture<Result> future =
        CompletableFuture.supplyAsync(
                () -> blockingCall(),
                quoteIoExecutor);
```

Đây là đoạn minh họa cần `Result`, `blockingCall()` và tham chiếu bean `quoteIoExecutor`. Không đưa JDBC blocking liên tục vào `ForkJoinPool.commonPool()` mà chưa hiểu áp lực của phần khác trong ứng dụng.

Một vài cảnh báo:

- `thenApply` không đảm bảo chạy trên caller thread hay trên cùng worker của nguồn; đừng phụ thuộc vào thread name để bảo vệ trạng thái.
- `thenApplyAsync` không đảm bảo “sẽ luôn có thêm một thread rảnh”: executor có thể từ chối hoặc queue đầy.
- `@Async` trên method không có nghĩa callback `thenApplyAsync` ở phần khác tự thừa hưởng `@Async` executor.
- Nếu một callback muốn dùng `ThreadLocal` request/trace/security, cần **propagation và cleanup rõ ràng**. `@Transactional` trong caller không tự lan sang worker; mỗi worker cần ranh giới transaction đúng.
- Executor có 2 thread nhưng trong callback có `join()` đợi tác vụ xếp trong **cùng executor** có thể bị nghẽn vĩnh viễn nếu cả 2 worker đều đang chờ.
- `CompletableFuture` không cung cấp backpressure ở cấp hệ thống. Gửi 10.000 tác vụ tới một executor/HTTP client có giới hạn mà không có admission control vẫn có thể làm queue lớn, timeout hoặc reject.

## 15. Những lỗi thực tế khi ghép pipeline

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Dùng `thenApply` cho hàm trả future | `CompletableFuture<CompletableFuture<T>>`, khó xử lý lỗi/chờ | `thenCompose` |
| Dùng `thenCompose` khi B độc lập A | Tạo phụ thuộc giả, B khởi chạy chậm | Khởi chạy A và B sớm, `thenCombine` |
| Gọi `.join()` trong callback để chờ tác vụ cùng pool | Giữ worker, starvation/deadlock | Ghép qua `thenCompose`/`thenCombine` |
| Gọi `.join()` ngay trong HTTP controller | Chặn request thread | Trả async result hoặc thiết kế job async theo contract |
| Nghĩ `allOf` trả kết quả các future | `allOf` thực ra trả `Void` | Lấy kết quả từ từng future sau `allOf`; kiểm tra lỗi |
| Nghĩ `anyOf` luôn trả future thành công nhanh nhất | Nó có thể hoàn thành ngoại lệ trước | Chọn điều kiện “first normal”/fallback phù hợp |
| Dùng `exceptionally(e -> null)` ở mọi nơi | Nuốt lỗi và chuyển sang dữ liệu sai | Phân loại lỗi, dùng fallback chỉ khi hợp lệ |
| Dùng `whenComplete` như `handle` để đổi giá trị | Không có ý nghĩa thay giá trị theo hợp đồng | Dùng `handle`/`thenApply` cho biến đổi |
| Dùng `thenApplyAsync()` mặc định và tưởng dùng pool Spring | Tác vụ có thể chạy ở `commonPool` | Truyền executor tường minh |
| Bỏ qua future hoàn thành ngoại lệ | Lỗi nền không được ghi nhận đúng | `whenComplete`/`handle` ở ranh giới quan sát hoặc job status |
| Dùng `orTimeout` rồi nghĩ đã hủy HTTP call | I/O có thể vẫn chạy và chiếm connection | Timeout client thực, cancellation/cooperation và resource cleanup |
| `cancel(true)` rồi nghĩ supplier đã bị interrupt | Với `CompletableFuture`, cancel chủ yếu là exceptional completion | Thiết kế cơ chế hủy riêng |
| Trong `@Async` tạo thêm `supplyAsync()` không kiểm soát | Double scheduling, pool sai, khó trace | Để proxy chạy method và trả result future phù hợp |
| Một tenant fan-out hàng nghìn nhánh không giới hạn | Quá tải connection pool, RAM, queue, downstream | Semaphore/bulkhead, batching, giới hạn concurrency |
| Trả `202` vì đã có `CompletableFuture` trong RAM | Crash làm mất công việc đã hứa xử lý | Durable job/queue, trạng thái và idempotency |
| Dùng `join()` để kiểm tra `isDone` | `isDone` có thể là lỗi/cancel | Quan sát `isCompletedExceptionally` và xử lý `CompletionException` |

## 16. Kiểm thử pipeline như thế nào?

Để test **logic kết hợp** mà không phụ thuộc timing/sleep:

1. Dùng `new CompletableFuture<>()` tạo nguồn chưa hoàn thành.
2. Kết nối `thenApply`, `thenCompose` hoặc `thenCombine`.
3. Chủ động `complete(value)` hoặc `completeExceptionally(error)` theo thứ tự bạn muốn.
4. Dùng `join()`/assertion **ở test thread cuối cùng**, không bên trong callback.
5. Kiểm tra kết quả, lỗi, thứ tự phụ thuộc và callback không chạy khi nguồn chưa đủ.

Ví dụ JUnit 5:

```java
@Test
void thenCombineWaitsForBoth() {
    CompletableFuture<Integer> left = new CompletableFuture<>();
    CompletableFuture<Integer> right = new CompletableFuture<>();

    CompletableFuture<Integer> combined =
            left.thenCombine(right, Integer::sum);

    left.complete(10);
    assertFalse(combined.isDone());

    right.complete(7);
    assertEquals(17, combined.join());
}
```

Đây là **đoạn test minh họa**, cần đặt trong test class có import `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.Assertions.*` và `java.util.concurrent.CompletableFuture`, cùng dependency JUnit 5. Không cần `Thread.sleep` hay đoán lịch scheduling.

Test thêm các nhánh:

- Nguồn A lỗi, B thành công: `thenCombine` phải hoàn thành ngoại lệ, không tự tính với `null`.
- `thenCompose`: sau khi A xong nhưng future do hàm tạo chưa xong, kết quả ghép cũng chưa xong.
- `allOf`: một nhánh chưa xong thì toàn bộ chưa hoàn thành; khi tất cả xong, nguồn lỗi làm `allOf` lỗi.
- `anyOf`: hoàn thành trước bằng **ngoại lệ** để xác minh đây không phải “đầu tiên thành công”.
- `handle` đổi lỗi thành giá trị: assertion phải kiểm tra đó là **quyết định fallback**, không phải thành công nghiệp vụ bất ngờ.
- Đặt executor queue nhỏ, giữ worker bằng latch và xác minh rejection/timeout đúng như thiết kế.
- Với `@Async`, gọi qua Spring proxy/bean khác; dùng `@SpringBootTest`/slice tương ứng để kiểm tra executor, propagation và exceptional completion.

Ở môi trường production, cần kiểm thử integration với DB, HTTP client thực, timeout, retry storm và shutdown. Unit test logic future **không** chứng minh tính bền vững của job sau restart.

## 17. Câu hỏi tự kiểm tra

**1.** `CompletableFuture<Integer>` cần nhân 2 kết quả sau khi có dữ liệu. Dùng gì?  
**Đáp:** `thenApply(x -> x * 2)`; callback nhận một **giá trị**, trả giá trị khác.

**2.** Sau khi lấy `userId`, cần gọi `fetchOrdersAsync(userId)` trả `CompletableFuture<List<Order>>`. Dùng gì?  
**Đáp:** `thenCompose(userId -> fetchOrdersAsync(userId))`, vì callback trả một stage.

**3.** Lấy tỷ giá và phí vận chuyển từ hai nguồn độc lập, rồi cộng/ghép. Dùng gì?  
**Đáp:** Khởi động cả hai, rồi `thenCombine` khi cả hai sẵn sàng.

**4.** `allOf(f1, f2)` trả `CompletableFuture<List<T>>` đúng hay sai?  
**Đáp:** Sai. Nó trả `CompletableFuture<Void>`; lấy kết quả từng future sau khi cả nhóm hoàn thành bình thường.

**5.** `anyOf(f1, f2)` có bảo đảm bỏ qua future lỗi và lấy kết quả thành công không?  
**Đáp:** Không. Future đầu tiên hoàn thành ngoại lệ có thể làm `anyOf` hoàn thành ngoại lệ.

**6.** Khác nhau giữa `thenApply` và `thenApplyAsync` không truyền executor?  
**Đáp:** `thenApply` dùng quy tắc completion không-async; `thenApplyAsync` mặc định dùng executor bất đồng bộ của `CompletableFuture` (thường common pool), không tự động dùng Spring pool.

**7.** `orTimeout(1, SECONDS)` có dừng JDBC query đang chạy không?  
**Đáp:** Không. Nó hoàn thành future ngoại lệ khi quá hạn; cần timeout/cancel ở client/driver/resource nếu muốn chặn công việc thật.

**8.** `@Async` service trả `CompletableFuture`, nhưng caller gọi `join` ngay trong controller. Controller có hoàn toàn non-blocking không?  
**Đáp:** Không. `join` vẫn có thể chặn thread caller.

**9.** `handle` và `whenComplete` đều nhận `(value, error)`; có dùng thay thế nhau hoàn toàn được không?  
**Đáp:** Không. `handle` **biến đổi** kết quả/lỗi thành stage mới; `whenComplete` dùng để **quan sát** và thường giữ kết quả/lỗi gốc.

**10.** Callback gọi `join()` đợi task nằm trong chính executor có một worker; rủi ro gì?  
**Đáp:** Starvation/deadlock vì worker duy nhất đang chờ thay vì chạy task được xếp hàng. Dùng `thenCompose` và tránh blocking trong continuation.

## 18. Kết luận và tài liệu chính thức

**`CompletableFuture` hữu ích vì mô tả được đồ thị phụ thuộc giữa các bước:**

- **Tuần tự phụ thuộc:** `thenApply` cho biến đổi đồng bộ, `thenCompose` khi bước kế tiếp trả một future.
- **Ghép song song:** `thenCombine` với hai nguồn độc lập; `allOf` cho nhiều nguồn.
- **Rẽ nhánh:** `anyOf` khi muốn kết quả hoàn thành sớm nhất và chấp nhận cả khả năng lỗi; chọn API khác nếu muốn “thành công đầu tiên”.
- **Lỗi:** `exceptionally`, `handle`, `whenComplete`, cùng ranh giới lỗi rõ ràng.
- **Executor:** callback không-Async và Async có hành vi scheduling khác nhau; truyền executor tường minh khi cần kiểm soát tài nguyên.
- **Vận hành:** tránh `join` trong worker, dùng timeout/cancel thực, bounded concurrency, quan sát future lỗi; `CompletableFuture` không thay thế durable job queue.

Tài liệu tham khảo:

- [Java SE 17 — `CompletableFuture` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): mọi combinator, lỗi, timeout, cancellation và quy tắc executor.
- [Java SE 17 — `CompletionStage` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionStage.html): khái niệm stage và phụ thuộc.
- [Java SE 17 — `ExecutorService` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): quản lý thực thi và shutdown.
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@Async`, future, executor và exception handling.
- [Spring Framework — AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): vì sao self-invocation không đi qua async proxy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-results-004">Vai trò giá trị Future tạm từ phương thức đích với Future do proxy trả về</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò giá trị Future tạm từ phương thức đích với Future do proxy trả về trong Spring @Async

## 1. Vấn đề: tại sao một phương thức @Async phải tự trả về Future nếu proxy đã trả Future?

Trong Spring Framework, một phương thức gắn `@Async` có thể khai báo kiểu trả về `Future<T>` hoặc `CompletableFuture<T>`. Khi gọi qua Spring AOP proxy, **caller nhận một Future đại diện cho lần thực thi bất đồng bộ**; Future này do hạ tầng Spring/executor tạo để theo dõi tác vụ, không nhất thiết là chính đối tượng Future được tạo trong phần thân phương thức đích.

Đọc đoạn mã sau:

```java
@Async("workExecutor")
public CompletableFuture<String> calculate() {
    String value = expensiveCalculation();
    return CompletableFuture.completedFuture(value);
}
```

Đây là fragment, cần một Spring-managed bean, cấu hình `@EnableAsync`, executor tên `workExecutor` và hàm `expensiveCalculation()` có thật. Hai câu hỏi thường xuất hiện:

1. Nếu method đã trả `CompletableFuture.completedFuture(value)` — một future **đã hoàn tất** — tại sao caller vẫn có thể thấy Future **đang pending**?
2. Nếu đã có `CompletableFuture` do target tạo ra, Spring còn phải tạo Future của proxy để làm gì?

Câu trả lời: có **hai vai trò** và thường có **hai đối tượng**.

- **Future tạm của target (target-side temporary Future):** object trả về từ phần thân method sau khi nó đã tính toán, dùng để chuyển giá trị hoặc lỗi cho cơ chế advice đang xử lý task trên worker.
- **Future thực tế của proxy (caller-facing async Future):** object Spring trả ngay cho caller khi chuyển lời gọi thành task trên executor; nó đại diện cho quá trình thực thi bao gồm cả việc method đích chưa bắt đầu hoặc vẫn đang chạy.

Đây là điểm được giải thích ngay trong Javadoc `org.springframework.scheduling.annotation.Async` của Spring 6.2: method đích vẫn cần thỏa chữ ký `Future`, nên có thể trả một handle tạm chỉ mang giá trị sau khi tính toán; proxy cung cấp handle bất đồng bộ thật cho client.

**Nguyên tắc nhớ nhanh:** *Future tạm giúp method giữ đúng hợp đồng trả về và chuyển kết quả; Future proxy giúp caller quan sát thời gian hoàn tất của lần gọi bất đồng bộ.*

## 2. Hai tầng xử lý: call boundary và result handoff

Để hiểu đúng, cần chia lời gọi `@Async` thành hai phần.

### 2.1. Lời gọi đi qua proxy

Một service khác hoặc caller nhận bean từ Spring context:

```java
Future<String> clientFuture = workService.calculate();
```

Nếu `workService` là proxy của Spring và method đủ điều kiện được advice, async interceptor chọn executor rồi **submit** phần gọi method đích. Nó trả về một `Future` tương ứng với task đã gửi. Điều này không phải khẳng định caller đã có kết quả; task có thể còn nằm trong queue hoặc đang chạy.

### 2.2. Phần thân method thực thi ở worker

Trên worker, Spring gọi target method. Method phải tuân thủ kiểu trả về đã khai báo:

```java
@Async("workExecutor")
public Future<String> calculate() {
    String computed = doWork();
    return CompletableFuture.completedFuture(computed);
}
```

Đây là fragment: `doWork()` là logic nghiệp vụ, không phải lời gọi tự sinh của Spring. Sau khi chạy `doWork()`, target trả một `Future<String>` đã hoàn tất, mang `computed`.

### 2.3. Async interceptor chuyển kết quả ra Future của caller

Trong mô hình Spring Framework 6.2, sau khi target method trả về:

1. Advice lấy object kết quả từ invocation.
2. Nếu đó là `Future<?>`, advice có thể gọi `get()` trên Future target để lấy giá trị hoặc lỗi.
3. Kết quả/exception này được dùng để hoàn tất task trong executor.
4. `Future` mà caller đã nhận từ proxy trở thành completed/failed tương ứng.

Hình dung:

```text
Caller thread                        Spring proxy                Worker thread
    |                                     |                            |
    | call @Async method                  |                            |
    |------------------------------------>|                            |
    |                                     | submit invocation task     |
    |                                     |--------------------------->|
    |<------------------------------------|                            |
    | caller-facing Future<T>             |                   invoke target method
    | (often still pending)               |                            |
    |                                     |                   compute business value
    |                                     |                            |
    |                                     |                 return temporary Future<T>
    |                                     |                            |
    |                                     |                   unwrap temporary future
    |                                     |                     (get value or error)
    |                                     |                            |
    | caller Future completes             |<-------- completion -------|
    |                                     |                            |
    | callerFuture.get() -> T             |                            |
```

**Không hiểu sai:** target không trực tiếp “gửi” object Future tạm cho caller bằng Java return theo đường gọi thông thường. Trong proxy mode, Spring intercept lời gọi và cung cấp **một kết quả proxy-level**. Giá trị cuối cùng có thể giống nhau, nhưng object identity, thời điểm hoàn tất và trách nhiệm theo dõi khác nhau.

## 3. Future tạm có bắt buộc đã hoàn thành không?

Trong pattern phổ biến, **nên trả Future đã hoàn tất** sau khi tính toán:

```java
return CompletableFuture.completedFuture(value);
```

Nó không tạo task mới và không tự chuyển công việc sang thread khác. Bản thân method đã được chuyển sang worker **nhờ @Async proxy**, vì vậy tạo một `CompletableFuture.completedFuture(...)` ở cuối method là cách hợp lý để thỏa chữ ký và bàn giao kết quả.

Tuy nhiên một target method có thể trả một `Future` chưa hoàn thành. Trong cơ chế interceptor của Spring 6.2, worker có thể phải chờ `targetFuture.get()`, khiến worker bị chiếm cho đến khi Future tạm hoàn tất.

Ví dụ sai về hiệu quả sử dụng pool (đây là **minh họa anti-pattern**, không phải chương trình độc lập):

```java
@Async("workExecutor")
public CompletableFuture<String> load() {
    return CompletableFuture.supplyAsync(
            () -> slowBlockingCall(),
            anotherExecutor);
}
```

Ở đây lời gọi bên ngoài đã được đưa vào `workExecutor`, còn `supplyAsync` lại gửi công việc sang `anotherExecutor`. Với Spring proxy/execution bridge, worker đầu tiên có thể phải chờ kết quả của Future bên trong. Đó là **hai lần scheduling**, không phải tự động tối ưu hay nonblocking.

Nếu `anotherExecutor` là chính một pool nhỏ đang bị task cha chiếm hết, việc chờ Future con có thể gây **thread starvation deadlock**. Nếu thực sự cần pipeline bất đồng bộ nhiều bước, hãy thiết kế rõ tầng orchestration, chọn executor phù hợp, hoặc dùng `CompletableFuture` composition/async APIs ngoài cách bọc một `@Async` method không cần thiết.

**Ngoại lệ hợp lý:** có những tích hợp trả Future từ API hiện có. Khi đó vẫn phải hiểu worker sẽ xử lý kết quả đó ra sao và kiểm tra tải; không mặc nhiên kỳ vọng mọi tầng là nonblocking.

## 4. Chữ ký trả về: void, Future và CompletableFuture

Spring `@Async` trong proxy mode thông thường chỉ hỗ trợ các nhóm kiểu trả về phù hợp:

| Kiểu method khai báo | Future do target cần trả | Caller quan sát | Điểm lưu ý |
| --- | --- | --- | --- |
| `void` | Không có | Không có result handle từ method | Exception không thể truyền bằng Future; dùng error handler/monitoring |
| `Future<T>` | Một `Future<T>` tạm, thường đã hoàn tất | `Future<T>` thực tế của proxy | Caller dùng `get()`, `isDone()`, `cancel()` |
| `CompletableFuture<T>` | Một `CompletableFuture<T>` tạm, thường `completedFuture(value)` | `CompletableFuture<T>` thực tế của proxy | Có thể chain `thenApply`, `thenCompose`, `exceptionally` |

`CompletableFuture<T>` là một subtype của `Future<T>` nên được hỗ trợ. Ngược lại, một method khai báo riêng `CompletionStage<T>` (không phải subtype của `Future`), `String`, `Mono<T>` hay đối tượng nghiệp vụ không được mặc định coi là kiểu trả về hợp lệ cho Spring `@Async` thông thường. Reactor có cơ chế concurrency riêng, không nên nhầm API.

### 4.1. Khi nào chọn Future<T>?

Dùng khi caller chỉ cần đợi và lấy kết quả bằng `get()`:

```java
@Async("workExecutor")
public Future<Integer> count() {
    int count = calculateCount();
    return CompletableFuture.completedFuture(count);
}
```

Đây là fragment cần `calculateCount()`. Dù target dùng `CompletableFuture` tạm, caller chỉ có thể dựa vào contract `Future<Integer>` nếu method được khai báo như vậy. Không ép kiểu Future proxy thành `CompletableFuture` rồi mặc định nó tương thích — hãy khai báo `CompletableFuture<Integer>` từ đầu nếu cần composition.

### 4.2. Khi nào chọn CompletableFuture<T>?

Dùng nếu caller muốn kết hợp kết quả mà không gọi `get()` ngay:

```java
@Async("workExecutor")
public CompletableFuture<Integer> countAsync() {
    int count = calculateCount();
    return CompletableFuture.completedFuture(count);
}
```

Caller có thể:

```java
CompletableFuture<String> displayed = service.countAsync()
        .thenApply(number -> "count=" + number)
        .exceptionally(ex -> "unavailable");
```

Các fragment yêu cầu `calculateCount()` và biến `service` thích hợp. `thenApply` không nhất thiết chạy trên worker khác; callback có thể chạy trên thread hoàn tất stage nếu dùng biến thể không có hậu tố `Async`. Vì vậy chọn `CompletableFuture` để **kết hợp kết quả**, không nên suy ra mỗi stage sẽ tạo thread mới.

### 4.3. Không yêu cầu tự tạo thread trong target

Khi đã dùng `@Async` qua proxy, target không cần `new Thread(...)` hay `CompletableFuture.supplyAsync(...)` chỉ để “làm cho nó async”. Phần chuyển sang executor đã xảy ra trước khi target bắt đầu chạy.

## 5. Ví dụ thực nghiệm chạy được: so sánh Future tạm và Future proxy

Ví dụ sau dùng **Java 21, Maven và Spring Framework 6.2.1**, không cần Spring Boot. Nó kiểm chứng bốn tình huống:

1. Method khai báo `Future<String>` trả Future tạm hoàn tất, trong khi Future proxy vẫn pending vì worker còn chờ một latch.
2. Method khai báo `CompletableFuture<String>` cũng có hai object và hai thời điểm hoàn tất có thể khác nhau.
3. Method đích trả một CompletableFuture **chưa hoàn tất**; Future proxy chỉ hoàn tất khi Future đích được giải phóng.
4. Future tạm hoàn tất lỗi; caller nhìn thấy lỗi tương ứng qua Future proxy.

### 5.1. Cấu trúc Maven

```text
spring-async-future-bridge-demo/
├── pom.xml
└── src/main/java/demo/FutureBridgeDemo.java
```

**Tệp `pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-async-future-bridge-demo</artifactId>
  <version>1.0</version>
  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-context</artifactId>
      <version>6.2.1</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.5.0</version>
        <configuration>
          <mainClass>demo.FutureBridgeDemo</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

### 5.2. Tệp Java hoàn chỉnh

**Tệp `src/main/java/demo/FutureBridgeDemo.java`:**

```java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class FutureBridgeDemo {
    public static class Signals {
        final AtomicReference<Future<String>> targetFuture = new AtomicReference<>();
        final AtomicReference<CompletableFuture<String>> targetCompletable =
                new AtomicReference<>();
        final AtomicReference<CompletableFuture<String>> targetPending =
                new AtomicReference<>();
        final CountDownLatch futureReady = new CountDownLatch(1);
        final CountDownLatch futureRelease = new CountDownLatch(1);
        final CountDownLatch completableReady = new CountDownLatch(1);
        final CountDownLatch completableRelease = new CountDownLatch(1);
        final CountDownLatch pendingReady = new CountDownLatch(1);
    }

    public static class WorkService {
        private final Signals signals;

        public WorkService(Signals signals) {
            this.signals = signals;
        }

        @Async("workExecutor")
        public Future<String> loadFuture() throws InterruptedException {
            Future<String> temporary = CompletableFuture.completedFuture("F:42");
            signals.targetFuture.set(temporary);
            signals.futureReady.countDown();
            signals.futureRelease.await();
            return temporary;
        }

        @Async("workExecutor")
        public CompletableFuture<String> loadCompletable()
                throws InterruptedException {
            CompletableFuture<String> temporary =
                    CompletableFuture.completedFuture("CF:ok");
            signals.targetCompletable.set(temporary);
            signals.completableReady.countDown();
            signals.completableRelease.await();
            return temporary;
        }

        @Async("workExecutor")
        public CompletableFuture<String> pendingTarget() {
            CompletableFuture<String> temporary = new CompletableFuture<>();
            signals.targetPending.set(temporary);
            signals.pendingReady.countDown();
            return temporary;
        }

        @Async("workExecutor")
        public CompletableFuture<String> failedTarget() {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("bad-input"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class AppConfig {
        @Bean("workExecutor")
        public ThreadPoolTaskExecutor workExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(8);
            executor.setThreadNamePrefix("async-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(5);
            return executor;
        }

        @Bean
        public Signals signals() {
            return new Signals();
        }

        @Bean
        public WorkService workService(Signals signals) {
            return new WorkService(signals);
        }
    }

    private static void await(CountDownLatch latch, String label)
            throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timeout waiting for " + label);
        }
    }

    public static void main(String[] args) throws Exception {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            WorkService service = context.getBean(WorkService.class);
            Signals signals = context.getBean(Signals.class);
            try {
                Future<String> callerFuture = service.loadFuture();
                await(signals.futureReady, "futureReady");
                System.out.println("futureTargetDone=" +
                        signals.targetFuture.get().isDone());
                System.out.println("futureProxyPending=" + !callerFuture.isDone());
                System.out.println("futureDistinct=" +
                        (callerFuture != signals.targetFuture.get()));
                signals.futureRelease.countDown();
                System.out.println("futureValue=" +
                        callerFuture.get(5, TimeUnit.SECONDS));

                CompletableFuture<String> callerCompletable =
                        service.loadCompletable();
                await(signals.completableReady, "completableReady");
                System.out.println("completableTargetDone=" +
                        signals.targetCompletable.get().isDone());
                System.out.println("completableProxyPending=" +
                        !callerCompletable.isDone());
                System.out.println("completableDistinct=" +
                        (callerCompletable != signals.targetCompletable.get()));
                signals.completableRelease.countDown();
                System.out.println("completableValue=" +
                        callerCompletable.get(5, TimeUnit.SECONDS));

                CompletableFuture<String> callerPending = service.pendingTarget();
                await(signals.pendingReady, "pendingReady");
                System.out.println("pendingProxyBefore=" +
                        !callerPending.isDone());
                signals.targetPending.get().complete("P:ready");
                System.out.println("pendingValue=" +
                        callerPending.get(5, TimeUnit.SECONDS));

                CompletableFuture<String> failure = service.failedTarget();
                try {
                    failure.get(5, TimeUnit.SECONDS);
                    throw new AssertionError("Expected failure");
                } catch (ExecutionException ex) {
                    System.out.println("failedCause=" +
                            ex.getCause().getClass().getSimpleName());
                }
            } finally {
                signals.futureRelease.countDown();
                signals.completableRelease.countDown();
                CompletableFuture<String> pending = signals.targetPending.get();
                if (pending != null && !pending.isDone()) {
                    pending.completeExceptionally(
                            new IllegalStateException("cleanup"));
                }
            }
        }
    }
}
```

**Vì sao có bean `Signals` riêng?** Đây là harness kiểm chứng hai phía của lời gọi. `WorkService` bị Spring proxy; việc đọc trực tiếp các field gắn với object proxy không nhất thiết đọc cùng state của target instance. Do đó test đặt `CountDownLatch` và `AtomicReference` vào một **bean Signals riêng**, được inject vào target và cũng lấy từ context ở caller. Đây là kỹ thuật test/điều phối minh họa, **không phải lời khuyên lưu toàn bộ state tác vụ production trong một singleton**.

### 5.3. Lệnh chạy

Trong thư mục dự án có `pom.xml`:

```bash
mvn -q -DskipTests compile exec:java
```

Yêu cầu Maven và JDK 21 trở lên. Thuộc tính `maven.compiler.release=21` khiến mã Java được biên dịch theo mức ngôn ngữ Java 21.

**Output đã kiểm chứng với Spring Framework 6.2.1:**

```text
futureTargetDone=true
futureProxyPending=true
futureDistinct=true
futureValue=F:42
completableTargetDone=true
completableProxyPending=true
completableDistinct=true
completableValue=CF:ok
pendingProxyBefore=true
pendingValue=P:ready
failedCause=IllegalArgumentException
```

Các dòng in từ chương trình, không bao gồm log phụ nếu môi trường Maven/JVM tự xuất thông báo.

## 6. Giải thích từng nhóm kết quả

### 6.1. Future<T>: target hoàn tất nhưng proxy chưa

Lúc worker vào `loadFuture()`:

```java
Future<String> temporary = CompletableFuture.completedFuture("F:42");
signals.targetFuture.set(temporary);
signals.futureReady.countDown();
signals.futureRelease.await();
return temporary;
```

`temporary.isDone()` là `true` ngay sau `completedFuture(...)`. Nhưng worker vẫn chờ `futureRelease`, nên **phương thức đích chưa trả** và task của interceptor chưa hoàn tất. Vì thế:

```text
futureTargetDone=true
futureProxyPending=true
futureDistinct=true
```

Hai đối tượng độc lập có trạng thái khác nhau tại cùng thời điểm. Sau khi caller mở latch, target trả Future tạm, interceptor lấy giá trị và đưa nó ra Future proxy. Caller nhận `F:42`.

### 6.2. CompletableFuture<T>: vẫn có hai vai trò

`loadCompletable()` lặp lại ý tưởng, chỉ đổi chữ ký sang `CompletableFuture<String>`:

```text
completableTargetDone=true
completableProxyPending=true
completableDistinct=true
completableValue=CF:ok
```

Dù cùng một kiểu `CompletableFuture<String>`, object mà target tạo **không phải** object được caller nhận từ proxy trong ví dụ. Cả hai đều là Future, nhưng có trách nhiệm khác nhau.

Điều này giải thích vì sao code `return CompletableFuture.completedFuture(value)` ở cuối `@Async` method **không vô hiệu hóa tính bất đồng bộ** của lời gọi: việc chạy trên executor được quyết định ở proxy boundary; target chỉ đóng gói giá trị sau khi xử lý.

### 6.3. Future tạm còn pending

Method `pendingTarget()` tạo một `CompletableFuture<>` chưa hoàn tất rồi trả nó. Lúc đó:

```text
pendingProxyBefore=true
```

Khi caller hoàn tất temporary bằng:

```java
signals.targetPending.get().complete("P:ready");
```

interceptor có thể lấy giá trị và hoàn tất proxy Future; caller đọc được:

```text
pendingValue=P:ready
```

Phép thử cho thấy **proxy Future phụ thuộc vào trạng thái hoàn tất của target Future** trong cơ chế bridge của Spring 6.2. Với Future tạm chưa xong, worker có thể đang phải chờ `get()`. Đây là lý do nên cảnh giác với method trả Future nền khác rồi chờ lồng nhau trên pool nhỏ.

### 6.4. Future tạm hoàn tất lỗi

`failedTarget()` trả:

```java
return CompletableFuture.failedFuture(
        new IllegalArgumentException("bad-input"));
```

Caller gọi `failure.get(...)` trên Future proxy và nhận `ExecutionException` với nguyên nhân `IllegalArgumentException`, nên chương trình in:

```text
failedCause=IllegalArgumentException
```

Không hiểu rằng target và proxy có cùng một exception wrapper object. Điểm cần quan sát là **nguyên nhân nghiệp vụ** có thể được chuyển qua ranh giới proxy/worker và xuất hiện khi caller đọc kết quả.

## 7. Các thời điểm hoàn tất khác nhau và vì sao test dễ sai

Giả sử caller gọi `service.calculate()` khi pool chưa có worker rảnh. Lời gọi thường trả về proxy Future sau khi task được chấp nhận, nhưng việc chạy trên worker có thể chưa bắt đầu.

Có thể xảy ra chuỗi:

1. `t0`: caller nhận Future proxy trạng thái pending.
2. `t1`: worker bắt đầu chạy target method.
3. `t2`: target tính xong và tạo temporary `completedFuture(value)`.
4. `t3`: target trả temporary.
5. `t4`: interceptor unwrap kết quả.
6. `t5`: Future proxy hoàn tất để caller nhận được `value`.

Trong ví dụ có latch, `t2 < t3` được kéo giãn có chủ đích để chứng minh `temporary.isDone() == true` trong khi `proxyFuture.isDone() == false`.

Trong ứng dụng thực, một phép tính rất nhanh có thể khiến proxy Future đã `isDone()==true` ngay khi caller nhìn vào, mà lời gọi **vẫn thật sự đã được chạy qua executor**. Vì thế, không thể dùng duy nhất `isDone()` ngay sau call để kết luận proxy đã hoặc chưa thực thi bất đồng bộ.

Để kiểm chứng concurrency, ưu tiên latch/Phaser kiểm soát mốc hoặc instrumentation đo thời điểm submit/bắt đầu/hoàn tất; đừng chỉ dựa vào `Thread.sleep(...)`.

## 8. Exception từ target Future được chuyển thế nào?

Có hai nguồn lỗi cần phân biệt.

### 8.1. Lỗi phát sinh trong phần thân target

Ví dụ:

```java
@Async("workExecutor")
public CompletableFuture<String> failFast() {
    throw new IllegalStateException("invalid");
}
```

Khi đi qua proxy, method body chạy trên executor. Với kiểu `Future`/`CompletableFuture`, caller thường quan sát lỗi qua handle async, chẳng hạn `future.get()` ném `ExecutionException` hoặc `future.join()` ném `CompletionException`.

### 8.2. Target trả Future đã hoàn tất lỗi

Ví dụ đã kiểm chứng với `failedFuture(...)`. Interceptor lấy kết quả từ temporary Future; nếu Future tạm báo lỗi, lỗi được chuyển vào kết quả async dành cho caller. Đừng để ý quá mức đến lớp exception bao ngoài: hãy xem `getCause()` phù hợp.

### 8.3. Lỗi ngay ở thời điểm submit

Nếu executor/queue quá tải hoặc đã shutdown, Spring có thể từ chối task **trước khi proxy trả một Future hữu ích** bằng `TaskRejectedException`. Đây không phải lỗi tính toán trong Future tạm của method, bởi method chưa chắc đã chạy.

Mẫu kiểm soát:

```java
try {
    CompletableFuture<String> result = service.calculateAsync();
    // Tiếp tục xử lý result khi nó hoàn thành.
} catch (TaskRejectedException ex) {
    // Xử lý trường hợp executor không nhận task.
}
```

Đây là fragment cần service có method `calculateAsync()` và import `TaskRejectedException`. Retry phải có giới hạn; không dùng retry vô hạn khiến quá tải tăng thêm.

## 9. Vì sao không nên trả CompletableFuture.supplyAsync(...) trong @Async tùy tiện?

Khi method `@Async` đã được Spring chuyển lên pool A, lời gọi `CompletableFuture.supplyAsync(..., poolB)` bên trong tạo thêm một tác vụ trên pool B. Nếu target trả Future của pool B mà chưa hoàn tất, bridge ở worker pool A có thể chờ kết quả pool B.

Hệ quả:

- Mất một worker của pool A để chờ công việc ở pool B.
- Nếu pool B cũng có giới hạn chặt hoặc chính là pool A, việc chờ có thể gây starvation/deadlock.
- Khó theo dõi timeout, cancellation, MDC, transaction và exception vì có thêm boundary.
- Không tự tạo ra I/O nonblocking: tác vụ gọi JDBC/HTTP blocking vẫn chiếm một worker nào đó.

**Hướng tốt hơn:** nếu công việc là đồng bộ và muốn đưa nó sang worker, chỉ tính toán trong `@Async` method rồi `return CompletableFuture.completedFuture(value)`. Nếu đã có API tự trả `CompletableFuture` và mục tiêu là composition không chặn worker, cân nhắc bỏ bọc `@Async` dư thừa và trả/combine future theo đúng API composition.

Với pipeline nhiều bước thực sự, thiết kế rõ executor ownership và cách nối stage thay vì vô thức kết hợp cả annotation và `supplyAsync`.

## 10. Proxy boundary: khi không có Future do proxy tạo

Toàn bộ so sánh target Future/proxy Future ở trên **chỉ đúng khi lời gọi thực sự qua Spring async advice**. Nếu caller gọi method trực tiếp trên đối tượng không được Spring quản lý hoặc target tự gọi method nội bộ:

```java
public CompletableFuture<String> selfCall() {
    return this.calculateAsync(); // Bỏ qua proxy trong AdviceMode.PROXY.
}
```

Đây là fragment; `calculateAsync()` phải thuộc cùng class. Ở chế độ `@EnableAsync` mặc định (`AdviceMode.PROXY`), `this.calculateAsync()` không tạo một lượt interceptor mới. Method chạy theo lời gọi Java bình thường và có thể trả **chính Future do method tạo**, vì không có proxy bridge tại điểm gọi.

Không nên kết luận rằng “Future proxy và Future tạm là cùng object” chỉ vì quan sát một ca self-invocation; đó là đường gọi **khác cơ chế**.

Checklist cho proxy:

- Có `@EnableAsync` hoặc XML `<task:annotation-driven>` trong context?
- Bean có được lấy/inject qua Spring container, thay vì `new` thủ công?
- Method có được gọi **từ bên ngoài** qua bean reference/proxy?
- Method/return type có phù hợp proxy mode? Method private/final không phải chỗ đặt `@Async` đáng tin.
- `@Async("workExecutor")` có khớp bean executor?
- Có gọi `Future.get()` ngay trên request thread khiến caller vẫn block không?

Nếu self-invocation là vấn đề, ưu tiên tách async method sang bean khác hoặc gửi task tường minh bằng `AsyncTaskExecutor`; đừng chỉ tăng kích thước pool để chữa lỗi call path.

## 11. Future proxy có bảo đảm cùng object hoặc cùng triển khai với target không?

**Không.** Object Future target có thể là một `CompletableFuture.completedFuture(...)`; Future proxy được tạo theo kiểu trả về khai báo và executor thực tế.

Trong Spring Framework 6.2:

- Nếu method khai báo `Future<T>`, Spring có thể dùng `AsyncTaskExecutor.submit(...)` để tạo Future của task.
- Nếu method khai báo `CompletableFuture<T>`, Spring có thể dùng `AsyncTaskExecutor.submitCompletable(...)` để cung cấp handle phù hợp cho caller.
- Bên trong worker, advice lấy giá trị/exception từ Future target rồi kết thúc task.

Vì vậy, không giả định `targetFuture == proxyFuture`, không làm business logic phụ thuộc class thực tế của Future, không ép kiểu `Future<T>` sang `CompletableFuture<T>` khi method chỉ hứa `Future<T>`. Chỉ dựa vào hợp đồng kiểu public đã khai báo.

**Chú ý khả năng thay đổi phiên bản:** cấu trúc bridge chi tiết và wrapper class là implementation detail của Spring; các bất biến cần dựa trên hợp đồng `@Async` và kết quả thực thi, không phải tên class nội bộ.

## 12. Timeout và cancel ở hai lớp Future

Giả sử caller có:

```java
CompletableFuture<String> callerFuture = service.calculateAsync();
```

Caller có thể dùng `get(2, TimeUnit.SECONDS)`, `orTimeout(...)`, `cancel(...)` hoặc các stage xử lý lỗi. Nhưng những thao tác này chủ yếu tác động lên **handle caller đang cầm**. Không tự suy rằng tất cả Future bên dưới hoặc tác vụ downstream sẽ được hủy/rollback.

Những ranh giới cần phân biệt:

- **Caller timeout:** hết thời gian đợi kết quả, không đồng nghĩa worker đã dừng.
- **Caller Future cancellation:** trạng thái cancel của proxy handle; mức độ interrupt worker phụ thuộc implementation và loại Future.
- **Target temporary Future cancellation:** trạng thái của Future được trả từ target; không phải lúc nào cũng được nối tự động hai chiều với proxy Future.
- **I/O/network cancellation:** có thể cần timeout/cancel ở chính HTTP client, JDBC, socket hoặc API bên ngoài.

Riêng `CompletableFuture.cancel(true)` của JDK không bảo đảm interrupt thread đang chạy chỉ vì tham số `mayInterruptIfRunning=true`; không nên dựa vào đó làm cơ chế dừng chắc chắn. Đối với những công việc có side effect, thiết kế idempotency, cleanup, timeout ở downstream và cách lưu trạng thái cuối cùng.

Đừng cho rằng Future tạm là nguồn sự thật để client hủy công việc, vì client thông thường **không cầm** Future tạm đó trong proxy mode.

## 13. Đoạn code xử lý kết quả cho caller

Caller có thể lựa chọn blocking get có timeout:

```java
try {
    String value = result.get(3, TimeUnit.SECONDS);
    apply(value);
} catch (ExecutionException ex) {
    Throwable original = ex.getCause();
    // Ghi nhận lỗi nghiệp vụ.
} catch (TimeoutException ex) {
    // Có thể task vẫn chạy; quyết định timeout/cancel/retry.
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Cho caller kết thúc theo chính sách interruption.
}
```

Đây là fragment; `result` là `Future<String>`, `apply` là hàm xử lý nghiệp vụ, các import java.util.concurrent thích hợp phải có.

Nếu method khai báo `CompletableFuture<String>`, bạn có thể chọn:

```java
service.calculateAsync()
       .thenApply(String::trim)
       .thenAccept(value -> save(value))
       .exceptionally(ex -> {
           logError(ex);
           return null;
       });
```

Đây là fragment, cần `save(...)` và `logError(...)`. Với CompletableFuture, phương thức `exceptionally` ở cuối nhận `Throwable` và phải trả giá trị khớp kiểu stage tại vị trí đó (ở đây stage từ `thenAccept` là `Void`, nên `return null` phù hợp). Không giả định callback tự sử dụng executor `@Async` ban đầu; các stage không hậu tố `Async` có thể chạy trên thread hoàn tất future.

Nếu muốn caller không bị chặn, ưu tiên composition/callback hơn là `get()` ngay sau lời gọi.

## 14. Quan sát thread, lỗi và tính nhất quán

Sau khi gọi qua proxy, code target chạy trên worker của executor (với cấu hình async pool như bài). Do đó:

- **ThreadLocal, MDC, SecurityContext:** không mặc nhiên được copy từ caller. Cần propagation phù hợp cho correlation/log/security nếu nghiệp vụ yêu cầu.
- **Transaction:** giao dịch gắn với thread của caller không tự chuyển nguyên vẹn vào worker. Nếu task có DB work, cấu hình transaction boundary riêng ở worker.
- **Side effects:** khi caller nhận Future proxy, công việc có thể còn chưa chạy. Không trả lời “đã xử lý thành công” chỉ vì không có exception tại thời điểm gọi method.
- **Rejection:** executor có queue và max workers; quá tải có thể ném lỗi từ chối ở đường submit.
- **Shutdown:** in-memory executor không có durability. Nếu tiến trình chết trước khi Future hoàn tất, không tự có khả năng replay.
- **Error reporting:** với method trả `Future`, caller cần đọc kết quả hoặc có chỗ khác quan sát completion. Với `void` async method, dùng `AsyncUncaughtExceptionHandler` hoặc cơ chế log/metrics phù hợp.

Những vấn đề này thuộc vòng đời của **proxy Future và task thật**, không được giải quyết chỉ bằng cách target trả `completedFuture(value)`.

## 15. Vì sao ví dụ dùng Signals bean riêng thay vì field của proxy?

Spring AOP có thể đưa ra một **proxy object** khác với **target object**. Với class-based proxy, việc đọc trực tiếp field qua reference proxy không tương đương một method getter đang được advice/delegate vào target.

Ví dụ minh họa nguy cơ:

```java
WorkService service = context.getBean(WorkService.class);
// Đừng mặc định service.someField là cùng field instance
// mà method của target đọc/ghi khi gọi qua proxy.
```

Trong phép thử bài này, ta muốn quan sát cả Future target và Future proxy. Vì vậy `Signals` là một **bean riêng**:

- Target nhận `Signals` qua constructor injection.
- Caller lấy **cùng bean Signals** từ ApplicationContext.
- Các `CountDownLatch` và `AtomicReference` nằm trên object Signals được chia sẻ có chủ đích.
- Caller không phải đọc field của proxy để suy trạng thái target.

Đây là một kỹ thuật kiểm thử rõ ràng, cho phép so sánh identity và timing có kiểm soát. Trong business code thực tế, tránh để một singleton Signals chứa state của nhiều request; phải gắn task ID, state riêng cho từng request và bảo đảm thread-safety.

## 16. Những lỗi phổ biến

| Hiểu sai/thiết kế sai | Hậu quả | Cách hiểu hoặc sửa |
| --- | --- | --- |
| Thấy `completedFuture(value)` trong target nên nghĩ lời gọi không async | Nhầm Future tạm với Future proxy | Async được quyết định ở proxy boundary; target chỉ chuyển giá trị |
| Nghĩ target Future và caller Future luôn cùng object | Sai identity và thời điểm hoàn tất | So sánh theo contract/kết quả, không dựa identity |
| Target trả `new CompletableFuture<>()` rồi không ai complete | Worker có thể chờ mãi và caller Future pending | Trả completedFuture sau khi tính xong hoặc hoàn tất future có chiến lược rõ |
| Bọc `supplyAsync(...)` bên trong mọi method `@Async` | Double scheduling, worker chờ Future con | Không tạo tầng async thứ hai nếu không cần |
| Chờ Future con trên cùng pool một worker | Starvation deadlock | Tránh nested blocking, dùng composition đúng |
| Cast proxy `Future<T>` thành `CompletableFuture<T>` khi method chỉ hứa Future | Có thể `ClassCastException` | Khai báo `CompletableFuture<T>` từ API nếu cần chaining |
| Gọi `this.asyncMethod()` và so sánh Future | Không qua advice; Future của target có thể được trả trực tiếp | Gọi qua Spring-managed bean khác |
| Dùng `isDone()` làm phép thử duy nhất | Task nhanh có thể hoàn tất trước khi kiểm tra | Dùng latch/instrumentation chứng minh trình tự |
| Nghĩ `get(timeout)` tự hủy task | Worker có thể tiếp tục chạy | Quyết định cancel/cleanup riêng |
| Nghĩ `cancel(true)` chắc chắn dừng mọi work | Nhiều tác vụ/CF không hỗ trợ interrupt như mong đợi | Thiết kế cooperative cancellation và timeout downstream |
| Bỏ qua `ExecutionException.getCause()` | Không thấy lỗi thực sự từ target | Truy nguyên exception cause, log đúng chỗ |
| Coi kết quả submit thành công là đã xử lý xong | Có thể task còn trong queue | Theo dõi Future proxy, metrics và trạng thái nghiệp vụ |
| Đọc field của Spring proxy như field target | Test sai/hang vì state không trùng | Sử dụng state bean riêng hoặc API getter đúng ý đồ |
| Dùng in-memory Future thay cho durable job tracking | Không có replay qua restart | Dùng outbox/broker/status store khi cần |

## 17. Câu hỏi tự kiểm tra

**Câu 1.** Method `@Async` trả `CompletableFuture.completedFuture("done")`. Caller có thể nhận một Future `isDone() == false` không?

Có. Future tạm của target đã hoàn tất, nhưng task proxy còn có thể chưa chạy đến đoạn đó hoặc chưa kết thúc. Hai Future có vai trò và thời điểm hoàn tất khác nhau.

**Câu 2.** Nếu method khai báo `Future<Integer>`, có nên cast Future proxy sang `CompletableFuture<Integer>` để dùng `thenApply` không?

Không nên. Hợp đồng public chỉ hứa `Future<Integer>`; object thực tế do executor/proxy quyết định. Hãy khai báo kiểu `CompletableFuture<Integer>` ngay từ đầu nếu cần composition.

**Câu 3.** Vì sao target vẫn phải trả Future dù có async interceptor?

Vì Java method phải tuân thủ chữ ký trả về. Future tạm là cách target bàn giao giá trị/lỗi để hạ tầng Spring chuyển sang Future do proxy cung cấp.

**Câu 4.** Nếu target trả một `CompletableFuture` chưa hoàn tất từ executor khác, chuyện gì có thể xảy ra?

Bridge trên worker Spring có thể phải chờ Future target hoàn tất. Điều này tiêu tốn worker, đặc biệt dễ gây starvation khi các task phụ thuộc nhau trong pool nhỏ.

**Câu 5.** Timeout trên Future proxy có đảm bảo Future tạm được hủy không?

Không. Timeout khi đọc `get(timeout)` chỉ áp dụng cho thời gian chờ của caller. Cancellation và propagate đến target/downstream phụ thuộc thiết kế và implementation; không mặc định là hai chiều.

**Câu 6.** Nếu thấy proxy bean tồn tại mà `this.asyncMethod()` chạy đồng bộ, có mâu thuẫn với kiến thức hai Future không?

Không. Self-invocation bypass proxy, nên không có caller-facing proxy Future mới cho lời gọi đó. Đó là một call path khác.

**Câu 7.** Test cần chứng minh hai Future khác trạng thái thì dùng gì?

Tạo Future tạm hoàn tất trong worker, dùng latch giữ target trước khi return, lấy Future proxy từ caller, sau đó so sánh trạng thái/identity và giải phóng latch. Không dùng sleep với thời lượng ngẫu nhiên.

## 18. Checklist khi thiết kế @Async có kết quả

- **Chữ ký:** `Future<T>` hay `CompletableFuture<T>`? Có thực sự cần nhận/chains kết quả không?
- **Target method:** tính toán công việc trên worker rồi trả một Future tạm thích hợp, thường `CompletableFuture.completedFuture(value)`.
- **Caller:** giữ Future của proxy và dùng nó để lấy kết quả/exception; không trông đợi object identity bằng Future tạm.
- **Proxy boundary:** đã bật `@EnableAsync`, bean do Spring quản lý, không phải self-invocation?
- **Không nested blocking:** target có vô tình gửi thêm task vào chính pool rồi đợi Future con không?
- **Timeout/cancel:** có timeout ở caller và ở I/O thực tế, biết ý nghĩa cancellation của từng tầng?
- **Exception:** có phân biệt lỗi trong target/temporary Future với `TaskRejectedException` tại submit?
- **Vận hành:** quan sát queue wait, execution time, active workers, rejection, errors, shutdown?
- **Context:** transaction/MDC/security và dữ liệu input có được xử lý đúng ở thread worker?
- **Độ bền:** nếu tác vụ phải tồn tại qua restart, đã dùng cơ chế lưu trạng thái/durable queue phù hợp?

## 19. Kết luận

Trong Spring `@Async`, **Future tạm do phương thức đích trả về** và **Future do Spring proxy trả về cho caller** không có cùng trách nhiệm:

1. Future tạm giữ đúng chữ ký method và chuyển giá trị hoặc lỗi từ lúc worker hoàn thành phần tính toán.
2. Future proxy là kết quả của lời gọi bất đồng bộ ở ranh giới AOP/executor; caller dùng nó để chờ, kiểm tra trạng thái, xử lý lỗi và composition.
3. Chúng có thể khác object, khác thời điểm hoàn tất; Future tạm đã completed **không có nghĩa** proxy Future cũng đã completed.
4. Trả một Future tạm chưa xong có thể làm worker phải chờ kết quả; tránh double scheduling và nested blocking nếu không có yêu cầu thực sự.
5. Toàn bộ bridge chỉ xảy ra nếu lời gọi được Spring async advice chặn; self-invocation trong chế độ proxy là một trường hợp khác.

### Tài liệu tham khảo

- [Spring Framework 6.2.1 — @Async Javadoc](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/annotation/Async.html)
- [Spring Framework 6.2.1 — @EnableAsync Javadoc](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Spring Framework 6.2.1 — AsyncExecutionAspectSupport](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/aop/interceptor/AsyncExecutionAspectSupport.html)
- [Java 21 — CompletableFuture API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-results-005">Dùng completedFuture() cho giá trị đã tính trong luồng xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng `completedFuture()` cho giá trị đã tính trong luồng xử lý

## Mục tiêu

Trong Spring Framework, một phương thức `@Async` trả `CompletableFuture<T>` thường kết thúc bằng:

```java
return CompletableFuture.completedFuture(result);
```

Vì sao một phương thức **bất đồng bộ** lại trả về future **đã hoàn tất**? Cần phân biệt hai đối tượng: **future tạm** mà phương thức đích tạo trong worker sau khi đã tính `result`, và **future thực tế** Spring proxy trả cho caller để theo dõi toàn bộ công việc được submit. `completedFuture()` không lập lịch chạy; chính `@Async` và executor chuyển việc khỏi caller khi được cấu hình và gọi đúng cách.

Sau khi học, bạn có thể:

1. Giải thích chính xác hợp đồng của `CompletableFuture.completedFuture(value)` trong JDK.
2. Hiểu vì sao target method `@Async` dùng completed future để chuyển giá trị đã tính cho hạ tầng Spring.
3. Phân biệt thời điểm **giá trị đã có trong worker** với thời điểm **future của caller hoàn tất**.
4. Chọn đúng giữa `completedFuture`, `failedFuture`, `supplyAsync`, `new CompletableFuture<>()`, và các toán tử composition.
5. Viết và chạy ví dụ Java 17+ với Spring Framework chứng minh future tạm đã xong trong khi proxy future còn pending.
6. Tránh các sai lầm về self-invocation, blocking, lỗi bất đồng bộ, cancellation, timeout, context và mutable state.

**Kết luận cốt lõi:** `completedFuture(value)` nói “tôi **đã có** value”; nó không nói “hãy tính value trên thread khác”. Trong phương thức `@Async`, việc lựa chọn thread nằm ở **Spring proxy/executor**, không ở static factory của `CompletableFuture`.

## 1. `completedFuture()` trong Java tạo ra gì?

Theo Java SE, `CompletableFuture.completedFuture(U value)` trả về một `CompletableFuture<U>` mới, **đã hoàn tất bình thường** với giá trị truyền vào. Đó là factory method đồng bộ, không submit task.

```java
CompletableFuture<Integer> ready =
    CompletableFuture.completedFuture(42);

System.out.println(ready.isDone()); // true
System.out.println(ready.join());   // 42
```

Ví dụ trên là snippet Java sử dụng `java.util.concurrent.CompletableFuture`, không cần Spring. Nó không mở thread mới, không dùng `ForkJoinPool.commonPool()` và không tạo một computation pending. `join()` ở đây trả về ngay vì kết quả đã sẵn.

Đặc biệt, Java đánh giá **biểu thức tham số trước lời gọi**:

```java
int answer = expensiveCalculation();
return CompletableFuture.completedFuture(answer);
```

`expensiveCalculation()` chạy **trước** khi future được tạo, trên thread đang chạy đoạn code đó. Viết:

```java
return CompletableFuture.completedFuture(expensiveCalculation());
```

không tự làm phép tính thành async: Java vẫn thực thi `expensiveCalculation()` để lấy argument trước khi gọi factory method. Nếu `expensiveCalculation()` chặn ba giây, caller của một method Java thông thường có thể bị chặn ba giây. Muốn đổi nơi chạy, cần cơ chế scheduling riêng như `@Async`, `ExecutorService.submit`, hoặc `CompletableFuture.supplyAsync`.

**Các trạng thái và đặc điểm:**

- Future do `completedFuture(value)` tạo có `isDone() == true`, `isCompletedExceptionally() == false`.
- `get()` và `join()` có kết quả sẵn; không phải chờ một background task.
- Có thể dùng `completedFuture(null)` nếu hợp đồng cho phép giá trị `null`; đây là một **future hợp lệ** mang giá trị null, không đồng nghĩa một method trả chính tham chiếu future là `null`.
- Với value là object mutable, `completedFuture` không biến object ấy thành immutable hay thread-safe.
- `completedFuture(error)` khi `error` là một `Throwable` vẫn có thể là **thành công với giá trị Throwable**, khác với future thất bại.

Ví dụ đồng bộ trả completion handle:

```java
public CompletableFuture<String> greeting(String name) {
    String message = "Xin chào " + name;
    return CompletableFuture.completedFuture(message);
}
```

Nếu không có AOP async hoặc executor bao ngoài, cả việc tạo `message` lẫn tạo future đều xảy ra trên caller. **Kiểu trả về `CompletableFuture` không tự bảo đảm lời gọi là bất đồng bộ.**

## 2. Vì sao `@Async` sử dụng một future tạm?

Spring `@Async` hỗ trợ những phương thức có kiểu trả về `void` hoặc `Future` (bao gồm `CompletableFuture`). Với kiểu trả về có kết quả, Spring proxy submit lời gọi sang executor rồi trao cho caller một handle để chờ hoặc compose kết quả.

Xem method minh họa trong một Spring bean:

```java
@Async("reportExecutor")
public CompletableFuture<String> buildReport(String id) {
    String report = calculateReport(id); // work trong worker
    return CompletableFuture.completedFuture(report);
}
```

Đây chưa phải chương trình độc lập: cần method `calculateReport`, `@EnableAsync`, bean tên `reportExecutor` và lời gọi **qua Spring proxy**. Trong điều kiện đúng:

1. Caller gọi `buildReport("A")` trên Spring-managed proxy.
2. Async interceptor chọn `reportExecutor` và submit method invocation.
3. Caller nhận **future của proxy**, thường khi task còn chưa hoàn tất.
4. Worker chạy `calculateReport("A")` và nhận một `String report`.
5. Vì chữ ký method yêu cầu `CompletableFuture<String>`, worker trả **future tạm đã completed** bằng `completedFuture(report)`.
6. Hạ tầng async xử lý return value của target method và hoàn tất **future mà caller nhận** với `report`.

Spring `@Async` Javadoc diễn đạt rõ sự khác nhau này: future mà proxy trả là asynchronous handle thực tế, còn target method có thể dùng `CompletableFuture.completedFuture` như một handle tạm để chuyển giá trị đã tính trong execution thread.

```text
Caller thread                 Spring proxy                  Worker thread
   |                               |                              |
   +-- service.buildReport("A") -->|                              |
   |                               +--- submit to executor ------>|
   |<-- proxyFuture --------------+                              |
   |                                                      calculateReport
   |                                                            |
   |                                                    String report đã có
   |                                                            |
   |                                           completedFuture(report)
   |                                                    tempFuture = DONE
   |                                                            |
   |                               <---- target returns tempFuture
   |                               |                              |
   |<---- proxyFuture completed ---+                              |
   |
   +-- thenApply/get/join/whenComplete tùy caller
```

**Không thể suy ra** rằng proxy future sẽ luôn pending ngay sau method call: task có thể chạy cực nhanh, hoặc executor được chọn có thể thực thi trên caller. Ví dụ đầy đủ ở phần 4 chủ động dùng chốt đồng bộ để quan sát hai trạng thái mà không phụ thuộc lịch scheduler.

### Ai chịu trách nhiệm gì?

| Thành phần | Trách nhiệm | Không tự làm |
| --- | --- | --- |
| `@EnableAsync` | Đăng ký hạ tầng annotation-driven async trong Spring context. | Không tạo kết quả nghiệp vụ. |
| `@Async("reportExecutor")` | Đánh dấu method và chỉ định executor. | Không tự hoạt động qua mọi kiểu lời gọi Java trực tiếp. |
| Spring proxy/interceptor | Nhận invocation, chọn executor, submit và cung cấp completion handle cho caller. | Không bảo đảm task luôn chạy thread khác trong mọi executor configuration. |
| `Executor`/`TaskExecutor` | Phân phối/chạy task, quản lý hàng đợi và worker theo chính sách. | Không biến dữ liệu dùng chung thành thread-safe. |
| `completedFuture(result)` trong target | Đóng gói kết quả **đã tính trong worker** thành completed temporary future. | Không submit thêm task, không bắt đầu một worker mới. |
| Future trả cho caller | Cho phép chờ có timeout, quan sát lỗi, biến đổi hoặc phối hợp kết quả. | Không tự giải phóng caller nếu caller gọi `join()` ngay. |

## 3. Điều kiện để ví dụ Spring hoạt động đúng

Không phải cứ thêm `@Async` vào mã nguồn là method chạy nền. Cần kiểm tra:

- **Bean do Spring quản lý:** object phải đến từ `ApplicationContext`, không phải instance tự tạo bằng `new`.
- **Async đã bật:** cần `@EnableAsync` hoặc cấu hình hạ tầng tương đương trong đúng application context.
- **Lời gọi qua proxy:** ở proxy mode mặc định, `this.asyncMethod()` trong cùng target là **self-invocation**, bỏ qua proxy và không được interceptor submit như lời gọi bên ngoài.
- **Method có thể intercept:** class-based proxy không override được method `final` hoặc `private`; class `final` không thể subclass. Nếu JDK proxy, caller cần gọi method được expose qua interface phù hợp.
- **Chữ ký hợp lệ:** `@Async` hỗ trợ `void`, `Future` và `CompletableFuture`, không thay tùy ý bằng `String` rồi kỳ vọng interceptor sẽ biến thành future.
- **Executor được chọn có thật:** `@Async("lessonExecutor")` cần bean tên tương ứng. Direct executor kiểu `Runnable::run` hoặc policy `CallerRunsPolicy` khi quá tải có thể chạy task trên caller, dù proxy vẫn có tác dụng.
- **Caller không chặn một cách vô tình:** gọi `get()` hoặc `join()` ngay sẽ chờ kết quả; `@Async` không bảo đảm HTTP request thread tự được giải phóng.

Khi thiếu `@EnableAsync` trong ứng dụng tự cấu hình như ví dụ, `@Async` có thể không được xử lý; `return completedFuture(value)` lúc đó chỉ là dòng Java thông thường. Vì vậy cần phân biệt **completion API** với **scheduling configuration** khi chẩn đoán.

## 4. Thực hành Java 17: factory không tạo task nền

Ví dụ sau kiểm chứng hành vi của `completedFuture` với JDK thuần. Thí nghiệm hai Future target/proxy của Spring đã có trong bài chuyên về async bridge; ở đây chỉ tập trung vào **thời điểm tính giá trị và việc submit**.

Lưu thành `CompletedFutureSemanticsDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CompletedFutureSemanticsDemo {
    static int compute(AtomicInteger calls) {
        calls.incrementAndGet();
        return 21 * 2;
    }

    public static void main(String[] args) {
        AtomicInteger calls = new AtomicInteger();
        // compute chạy trên caller trước khi factory nhận đối số.
        int result = compute(calls);
        CompletableFuture<Integer> ready =
                CompletableFuture.completedFuture(result);
        System.out.println("computedBeforeFactory=" + calls.get());
        System.out.println("readyDone=" + ready.isDone());
        System.out.println("readyValue=" + ready.join());

        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch release = new CountDownLatch(1);
        try {
            CompletableFuture<Integer> scheduled =
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            release.await();
                            return 42;
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                        }
                    }, pool);
            // Chốt chưa mở, nên task không thể hoàn tất.
            System.out.println("scheduledPending=" + !scheduled.isDone());
            release.countDown();
            System.out.println("scheduledValue=" + scheduled.join());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
```

Chạy bằng JDK 17+:

```bash
javac --release 17 CompletedFutureSemanticsDemo.java
java CompletedFutureSemanticsDemo
```

Kết quả:

```text
computedBeforeFactory=1
readyDone=true
readyValue=42
scheduledPending=true
scheduledValue=42
```

`compute()` được gọi trước factory nên bộ đếm đã bằng 1, còn `ready` completed ngay. `supplyAsync` submit supplier lên worker, nhưng latch giữ phép tính chưa xong để lần kiểm tra `scheduledPending` ổn định; sau khi mở chốt mới nhận được 42. Trong method Spring `@Async`, proxy đã làm nhiệm vụ submit; target chỉ cần tính kết quả trên worker rồi trả `completedFuture(result)`.

## 5. Dùng `completedFuture` đúng trong Spring service thực tế

Giả sử một service truy vấn repository đồng bộ:

```java
@Service
public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    @Async("ioExecutor")
    public CompletableFuture<UserDto> findUser(long id) {
        User user = repository.loadRequired(id);
        UserDto dto = new UserDto(user.id(), user.name());
        return CompletableFuture.completedFuture(dto);
    }
}
```

Đây là **mẫu cấu trúc không độc lập**; các kiểu `UserRepository`, `User` và `UserDto` phải được định nghĩa, có các annotation/import tương ứng và bean `ioExecutor` phải tồn tại.

Lời gọi `service.findUser(id)` từ bean khác đi **qua Spring proxy** sẽ gửi toàn bộ phần đọc repository và dựng DTO lên `ioExecutor`. Khi đến `completedFuture(dto)`, phần việc đồng bộ trong worker đã xong. Future này chỉ đóng gói kết quả đã có cho Spring.

Nếu không có `@Async` hoặc gọi trực tiếp target qua self-invocation, `repository.loadRequired(id)` diễn ra trên caller, có thể chặn caller lâu; completed future phía sau không cứu được thời gian đã mất.

### Khi nào nên dùng?

- Target method `@Async` tính được kết quả đồng bộ trong worker và cần trả đúng chữ ký `CompletableFuture<T>`.
- Nhánh cache hit hoặc dữ liệu tĩnh đã có sẵn muốn tuân thủ API Future thống nhất.
- Test stub trả ngay một giá trị có sẵn, không cần scheduler.
- Một adapter muốn cung cấp stage đã hoàn tất mà không tạo thêm worker.

### Khi nào không phù hợp?

- Muốn bắt đầu tính toán ở pool khác: sử dụng `ExecutorService.submit`, Spring `@Async` hoặc `CompletableFuture.supplyAsync(..., executor)`.
- Muốn biểu diễn công việc sẽ hoàn tất khi callback/sự kiện đến: tạo future chưa hoàn tất và gọi `complete` hoặc `completeExceptionally` đúng thời điểm.
- Muốn chuyển tải một Future đang pending thành giá trị: nên compose thay vì `join` một Future khác trong worker rồi bọc kết quả.
- Muốn báo lỗi đã biết: dùng `failedFuture(error)` chứ không coi `completedFuture(error)` là completion thất bại.

## 6. So sánh các API trong một bảng

| API | Kết quả ngay sau lời gọi | Lập lịch task mới? | Mục đích |
| --- | --- | --- | --- |
| `CompletableFuture.completedFuture(value)` | Future đã completed thành công | **Không** | Đưa giá trị đã tính vào completion API. |
| `CompletableFuture.failedFuture(error)` | Future đã completed exceptionally | **Không** | Đưa lỗi đã biết vào completion API. |
| `new CompletableFuture<T>()` | Future chưa completed | **Không** | Callback bên ngoài hoàn tất sau. |
| `CompletableFuture.supplyAsync(supplier)` | Future có thể còn pending hoặc đã xong rất nhanh | **Có** qua default executor | Chạy phép tính bất đồng bộ mà không chỉ định pool. |
| `CompletableFuture.supplyAsync(supplier, executor)` | Tương tự, theo chính sách executor cung cấp | **Có** qua executor được chọn | Chủ động chọn pool. |
| `CompletableFuture.runAsync(runnable, executor)` | Future kiểu `Void` | **Có** | Công việc không có giá trị trả về. |
| `future.thenApply(transform)` | Stage phụ thuộc kết quả future trước | Không bắt buộc tạo worker mới | Biến đổi kết quả. |
| `future.thenApplyAsync(transform, executor)` | Stage phụ thuộc thực thi qua executor | **Có** | Cần chọn thread/pool cho bước biến đổi. |
| `future.thenCompose(function)` | Stage dẹt từ hàm trả future khác | Không tự yêu cầu pool mới | Nối chuỗi các phép tính bất đồng bộ. |

`isDone()` không chứng minh một bước đã thực thi trên worker khác. Task qua `supplyAsync` có thể hoàn tất trước khi caller kiểm tra, hoặc executor tùy chỉnh có thể thực thi trực tiếp trên caller. Phải nhìn cơ chế scheduling, không chỉ trạng thái future.

### Đối chiếu với `supplyAsync`

```java
ExecutorService pool = Executors.newSingleThreadExecutor();
try {
    int number = 7;
    CompletableFuture<Integer> immediate =
        CompletableFuture.completedFuture(number * 2);
    CompletableFuture<Integer> scheduled =
        CompletableFuture.supplyAsync(() -> number * 2, pool);

    System.out.println("immediate=" + immediate.join());
    System.out.println("scheduled=" + scheduled.join());
} finally {
    pool.shutdown();
}
```

Đây là snippet đặt trong method, cần import `ExecutorService`, `Executors`, `CompletableFuture`. Khi chạy thành công, output là:

```text
immediate=14
scheduled=14
```

Output giống nhau nhưng khác ranh giới thực thi: biểu thức `number * 2` của `immediate` được đánh giá trên caller ngay lập tức; biểu thức bên trong supplier của `scheduled` được gửi qua `pool`.

### Tránh “double scheduling” không có chủ đích

```java
@Async("ioExecutor")
public CompletableFuture<String> load(String id) {
    return CompletableFuture.supplyAsync(() -> blockingLookup(id));
}
```

Đây là snippet cần bean/executor và `blockingLookup`, không độc lập. Ở đây `@Async` đã đưa phương thức `load` lên executor được chọn, rồi `supplyAsync` **không truyền executor** lại gửi một task sang default executor của JDK (thường `ForkJoinPool.commonPool()`). Điều này có thể gây hai tầng scheduling, khó quản lý thread/context/metrics và thêm blocking khi Spring cần kết quả của stage con.

Nếu `blockingLookup` là công việc đồng bộ muốn thực hiện trên worker `ioExecutor`, idiom thường đơn giản hơn:

```java
@Async("ioExecutor")
public CompletableFuture<String> load(String id) {
    String value = blockingLookup(id);
    return CompletableFuture.completedFuture(value);
}
```

Nếu logic thật sự đã hoàn tất bất đồng bộ ở một hệ thống khác, có thể có lý do để compose một stage từ API đó; cần quyết định rõ ownership và executor, không tự động chọn `supplyAsync` chỉ vì có `CompletableFuture`.

## 7. Xử lý ngoại lệ ở target và caller

### Target ném lỗi trước khi tạo completed future

```java
@Async("ioExecutor")
public CompletableFuture<UserDto> findUser(long id) {
    User user = repository.loadRequired(id);
    return CompletableFuture.completedFuture(
        new UserDto(user.id(), user.name())
    );
}
```

Đây là snippet dùng các kiểu domain ở phần trước. Nếu repository ném `RuntimeException`, dòng `completedFuture` sẽ không chạy. Khi invocation đi qua Spring async proxy, hạ tầng phản ánh lỗi từ worker sang completion handle của caller.

Caller có thể dùng `get()` (thường nhận `ExecutionException` với `cause`), `join()` (thường `CompletionException`) hoặc callback `whenComplete`/`handle`.

### Lỗi đã được xác định trong code

```java
@Async("validationExecutor")
public CompletableFuture<String> validate(String input) {
    if (input == null || input.isBlank()) {
        return CompletableFuture.failedFuture(
            new IllegalArgumentException("Input is blank")
        );
    }
    return CompletableFuture.completedFuture(input.trim());
}
```

Snippet này giả định bean `validationExecutor`, các import và Spring async context. `failedFuture` tạo completed future **exceptionally**, còn `completedFuture` tạo completed future **successfully**. Chữ “completed” chỉ nói về trạng thái kết thúc; phải phân biệt thành công với thất bại.

Nếu khai báo `@Async void`, caller không có future để nhận exception từ worker; cần `AsyncUncaughtExceptionHandler` hoặc logging/metrics có chủ đích. Với nghiệp vụ quan trọng phải theo dõi kết quả, nên có một return type Future hoặc job status.

### Caller quan sát lỗi

```java
CompletableFuture<String> answer = validationService.validate(input);
answer.whenComplete((value, error) -> {
    if (error != null) {
        System.err.println("Failed: " + error.getMessage());
    } else {
        System.out.println("Valid: " + value);
    }
});
```

Đây là đoạn minh họa có `validationService` và `input`. `whenComplete` là phép **quan sát**, không nhất thiết chữa lỗi. Nếu muốn trả fallback, cân nhắc `exceptionally`/`handle` và chính sách nghiệp vụ. Không bỏ quên future trong lúc task có thể lỗi.

## 8. Composition sau khi nhận kết quả

Caller thường không cần lập tức `get()`/`join()`; có thể ghép các stage:

```java
CompletableFuture<String> display =
    workService.compute("A", 7)
        .thenApply(value -> "Result: " + value.value());
```

Snippet dùng `WorkService` của ví dụ chính nhưng không độc lập. `thenApply` có thể được thực thi bởi thread hoàn tất stage hoặc thread đăng ký callback khi future đã completed. Nó **không bảo đảm** tạo một worker khác.

Nếu muốn chọn rõ executor cho bước tiếp theo:

```java
CompletableFuture<String> display =
    workService.compute("A", 7)
        .thenApplyAsync(value -> "Result: " + value.value(), cpuExecutor);
```

Ở đây cần một `Executor cpuExecutor`. Không mặc định rằng `thenApplyAsync(...)` không truyền executor sẽ dùng `lessonExecutor` của Spring; overload không có executor thường dùng default executor của JDK.

Với hàm trả `CompletionStage<U>`:

```java
CompletableFuture<String> chained =
    workService.compute("A", 7)
        .thenCompose(value -> anotherService.lookup(value.value()));
```

Snippet giả định `anotherService.lookup(...)` trả `CompletionStage<String>` hoặc `CompletableFuture<String>`. `thenCompose` tránh tạo future lồng. Tránh `join()` Future khác trong worker chỉ để `return completedFuture(...)`: nếu future kia cần chính worker bị chặn để chạy tiếp, pool nhỏ có thể nghẽn hoặc deadlock.

## 9. Thread context, dữ liệu mutable và side effects

**Thread context:** `@Async` là ranh giới giữa caller thread và worker. `ThreadLocal`, MDC, request attributes, security context, và transaction gắn với caller không mặc nhiên truyền sang worker. `completedFuture` không thay đổi quy tắc này. Khi cần context, truyền có kiểm soát bằng `TaskDecorator` hoặc cơ chế tương ứng, phải dọn/khôi phục trong `finally` do worker được tái sử dụng. Nếu task async cần transaction, xác định transaction tại worker theo cấu hình Spring phù hợp.

**Object được trả:** Một `CompletableFuture<List<Order>>` completed không tự biến `List<Order>` thành immutable. Nếu nhiều thread còn sửa object, nguy cơ race vẫn có. Ưu tiên DTO bất biến, defensive copy hoặc ownership rõ:

```java
List<String> snapshot = List.copyOf(items);
return CompletableFuture.completedFuture(snapshot);
```

Snippet này cần import `List` và biến `items`. `List.copyOf` ngăn sửa cấu trúc thông qua danh sách kết quả, nhưng không bảo đảm bất biến sâu của các phần tử mutable; việc copy từ nguồn đang sửa đồng thời cần đồng bộ thích hợp.

**Side effects:** Hoàn tất future của một method không tự chứng minh một message đã được consumer xử lý, email đã tới mailbox, hay database transaction của hệ thống khác đã commit. Phải xác định rõ `result` nói về giai đoạn nghiệp vụ nào; khi cần tính bền vững, dùng acknowledgement/job status/transaction phù hợp.

## 10. Timeout và cancellation không được nhầm với completedFuture

```java
Result value = service.compute("A", 7)
    .get(500, TimeUnit.MILLISECONDS);
```

Snippet này dùng service của ví dụ. `get(timeout)` giới hạn **lần caller chờ kết quả**. Nếu timeout, worker có thể vẫn chạy; caller timeout không tự tạo `completedFuture` hay tự dừng task.

`CompletableFuture.orTimeout(...)` có thể khiến future kết thúc ngoại lệ nếu còn pending sau thời hạn; `completeOnTimeout(...)` có thể hoàn tất bằng fallback. Các API này thay đổi trạng thái completion của future được gọi chứ không bảo đảm ngắt worker. Với future do Spring proxy trả về, cancellation và interrupt có ngữ nghĩa phụ thuộc loại future/hạ tầng; không suy ra `cancel(true)` chắc chắn dừng I/O hoặc rollback side effect.

Trong HTTP request, nếu controller gọi `join()` ngay, chính controller thread vẫn chờ. Muốn HTTP async đúng nghĩa phải dùng hợp đồng của Spring MVC/WebFlux phù hợp, độc lập với việc target method có `@Async`.

Đặt timeout thật ở HTTP client, JDBC, connection pool và deadline nghiệp vụ; hạn chế queue/worker để không giữ tài nguyên vô hạn. Nếu task cần tiếp tục sau client disconnect hoặc restart, phải có job status và hàng đợi/lưu trữ bền vững; `completedFuture` không giải quyết độ bền.

## 11. Những sai lầm thường gặp

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “`completedFuture` tạo task chạy nền.” | Nó tạo future đã completed, không submit task. |
| “Trả future completed trong `@Async` nghĩa là toàn bộ lời gọi đồng bộ.” | Target method có thể đang chạy trên worker; proxy future của caller theo dõi toàn bộ invocation. |
| “Future trong target và future caller chắc chắn là cùng object.” | Trong proxy mode Spring, caller thường nhận một handle riêng. |
| “`isDone() == true` chứng minh đã chạy trên thread khác.” | Một future đồng bộ tạo bằng `completedFuture` cũng `isDone() == true`. |
| “Đánh giá tham số `completedFuture(slowQuery())` ở background.” | `slowQuery()` chạy trước lời gọi factory, trên thread hiện tại. |
| “`this.asyncMethod()` cũng đi qua async proxy.” | Self-invocation ở proxy mode bỏ qua advisor. |
| “Nên lồng `supplyAsync` trong mọi `@Async`.” | Có thể thêm một tầng scheduling sang default JDK executor không cần thiết. |
| “`completedFuture(exception)` thể hiện task thất bại.” | Có thể là future thành công với giá trị exception; dùng `failedFuture`. |
| “`get(timeout)` sẽ hủy tác vụ nếu caller hết hạn.” | Timeout chỉ giới hạn chờ của caller, không tự cancel worker. |
| “Future completed thì payload tự bất biến và an toàn luồng.” | Completion không bảo vệ mutable object được share. |
| “Future returned nghĩa là HTTP thread không block.” | Controller gọi `join`/`get` vẫn chặn; cần thiết kế HTTP async thích hợp. |

## 12. Checklist rà soát một `return completedFuture(result)`

1. **Giá trị đã tính ở đâu?** Mọi phép tính/I/O xảy ra **trước** `completedFuture` và có thể blocking.
2. **Có proxy không?** `@EnableAsync` được bật ở đúng context, bean do Spring quản lý và lời gọi qua proxy?
3. **Đúng executor không?** Qualifier `@Async("...")`, thread-name prefix, hàng đợi/rejection và giới hạn pool đã rõ?
4. **Có hai loại future không?** Future tạm ở target với future proxy caller không nên bị đồng nhất về identity và lifecycle.
5. **Có exception không?** Thất bại từ `throw`, `failedFuture` và lỗi submit phải được quan sát, không coi là success.
6. **Có double scheduling không?** `supplyAsync` trong method `@Async` có đưa task sang một executor khác ngoài ý muốn?
7. **Có blocking để compose không?** Tránh `join()` trên future phụ thuộc cùng pool nhỏ.
8. **Payload mutable không?** Kiểm tra thread safety, ownership, snapshot/DTO trước khi expose.
9. **Có context và transaction không?** Worker có nhận đúng context và dọn sạch sau khi dùng?
10. **Timeout/cancel và late result?** Caller trả fallback có thể khiến task cũ tiếp tục cập nhật data; cần idempotency và giới hạn side effect.

## 13. Câu hỏi tự kiểm tra

**1. `CompletableFuture.completedFuture(7)` có dùng một thread mới không?**

Không. Nó tạo future đã completed ngay, không cần executor.

**2. Vì sao target method `@Async` trả completed future mà caller vẫn có thể nhận pending future?**

Vì target tính value bên trong worker rồi trả future tạm; caller nhận future do Spring proxy tạo để theo dõi **toàn bộ invocation**, gồm cả thời gian chờ chạy và tính toán.

**3. Nếu chỉ có `return completedFuture(slowQuery())` mà không có scheduling, `slowQuery` chạy ở đâu?**

Chạy trên caller thread trước khi factory được gọi, có thể block caller.

**4. Ví dụ demo cho `temporaryFutureDone=true` nhưng `proxyFutureDoneBeforeReturn=false` nhờ đâu?**

Nhờ worker đã tạo future tạm và mở latch `temporaryReady`, nhưng vẫn bị giữ ở `allowReturn` trước khi target method return. Proxy future chưa được hoàn tất.

**5. Dùng `supplyAsync` bên trong `@Async("lessonExecutor")` có nhất thiết tốt hơn không?**

Không. Có thể tạo thêm một task trên common pool hoặc executor khác, gây khó quản lý scheduling và context; nếu đã tính đồng bộ trong worker thì `completedFuture(result)` thường phù hợp hơn.

**6. Khi muốn biểu diễn lỗi đã xác định, dùng `completedFuture(error)` hay `failedFuture(error)`?**

Dùng `failedFuture(error)` để completion exceptional. `completedFuture(error)` có thể coi object lỗi là giá trị thành công.

**7. Nếu caller gọi `proxyFuture.get()` ngay, caller có tiếp tục xử lý việc khác không?**

Không trong lúc đợi; chính thread caller bị block cho đến khi future hoàn tất hoặc chờ bị ngắt.

**8. Một future completed với `List<Order>` có tự trở thành deep immutable không?**

Không. Future không thay đổi tính mutable của object, cần chính sách dữ liệu và đồng bộ riêng.

### Ghi nhớ

`CompletableFuture.completedFuture(value)` chỉ nói rằng **value đã sẵn có và future được hoàn tất thành công**. Trong một method Spring `@Async` được gọi qua proxy, **việc bất đồng bộ nằm ở đường proxy/executor**; target có thể dùng completed future tạm như một cách chuẩn để chuyển giá trị đã tính trong worker tới caller. Luôn tách bạch *chỗ tính giá trị*, *đường submit*, *future thực tế của caller*, *ngoại lệ* và *vòng đời của tác vụ*.

### Tài liệu tham khảo chính thức

- [Spring Framework — `@Async` Javadoc (6.2)](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/annotation/Async.html): hợp đồng future của proxy và future tạm của target.
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): `@EnableAsync`, executor, `@Async` và return type.
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): `completedFuture`, `failedFuture`, `supplyAsync`, composition, timeout và cancellation.
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): kết quả, chờ và hủy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-results-006">Không dùng kiểu trả về đồng bộ thông thường để giả định vẫn nhận kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng kiểu trả về đồng bộ thông thường để giả định vẫn nhận kết quả

## 1. Vấn đề: ghi @Async không biến String thành kết quả trả ngay

Trong Java thông thường, một phương thức khai báo `String` và chạy đồng bộ cho người gọi một giá trị ngay khi phương thức kết thúc:

```java
public String calculate() {
    return "42";
}

// Lời gọi đồng bộ:
String value = service.calculate();
System.out.println(value);
```

Đây là một đoạn mã minh họa trong các class có sẵn, chưa phải chương trình độc lập. Người gọi đi tiếp sau khi `calculate()` hoàn thành và nhận chính giá trị trả về.

Khi muốn Spring chạy một phương thức **bất đồng bộ** qua `@Async`, người học dễ viết nhầm:

```java
import org.springframework.scheduling.annotation.Async;

@Async
public String calculate() {
    return "42";
}
```

**Đây là cách khai báo sai đối với cơ chế Spring `@Async` thông thường.** `String` là kiểu trả về đồng bộ: nó không biểu diễn một công việc sẽ hoàn thành sau, cũng không có kênh thông báo lỗi, chờ hoặc hủy. Spring không thể vừa trả ngay cho caller vừa giữ nguyên hợp đồng method `String calculate()` theo cách mà proxy `@Async` hỗ trợ.

Theo JavaDoc `org.springframework.scheduling.annotation.Async`, kiểu trả về của phương thức đích được hỗ trợ là **`void` hoặc một kiểu `java.util.concurrent.Future`**, trong đó có **`CompletableFuture<T>`**. Các kiểu đơn giản như `String`, `int`, `UserDto`, `List<UserDto>`, `Optional<UserDto>` hoặc `ResponseEntity<UserDto>` **không được phép** làm kiểu trả về trực tiếp của phương thức `@Async` theo mô hình này.

Trong một lời gọi thật sự bị Spring async interceptor chặn, kiểu không được hỗ trợ thường gây `IllegalArgumentException` với nội dung tương tự “Invalid return type for async method ...” thay vì trả về `String` như mong đợi. Nếu bạn vẫn quan sát thấy `String` trả về bình thường, đó có thể là vì phương thức **đã chạy đồng bộ**, ví dụ qua lời gọi `this.calculate()` bỏ qua proxy, hoặc chưa bật xử lý `@Async`. Không được lấy biểu hiện “có dữ liệu” làm bằng chứng bất đồng bộ đang hoạt động.

**Nguyên tắc trung tâm:** Khi muốn nhận kết quả từ công việc `@Async`, hãy thiết kế **giao diện trả về đại diện cho kết quả tương lai**, phổ biến nhất là `CompletableFuture<T>`; khi không cần trả kết quả cho caller, có thể dùng `void` và thiết kế cách ghi nhận lỗi phù hợp.

## 2. Vì sao kiểu trả về đồng bộ không phù hợp?

Một phương thức đồng bộ trả `T` biểu đạt:

```text
Caller --------------------> method()
       chờ method chạy xong
       <-------------------- T
       tiếp tục với giá trị T
```

Một phương thức `@Async` được Spring xử lý qua proxy thường biểu đạt:

```text
Caller -> Spring proxy -> chọn Executor -> gửi công việc
Caller <---------------- Future<T> / CompletableFuture<T>
    |                          |
    | làm việc khác            | worker thực thi method thật
    |                          |
    +-- quan sát/chờ ----------+--> T hoặc exception khi hoàn tất
```

Vì kết quả `T` chưa chắc đã có khi caller trở về, đối tượng được trả phải đại diện cho **trạng thái chưa hoàn tất, hoàn tất thành công hoặc hoàn tất thất bại**. `Future<T>` mang hợp đồng cơ bản đó, còn `CompletableFuture<T>` hỗ trợ thêm các thao tác phối hợp và biến đổi kết quả.

Không nên suy luận rằng `@Async` tạo ra một đối tượng `T` đặc biệt để người gọi sử dụng như dữ liệu đã có. Nếu muốn dữ liệu `T` ngay lập tức, đó là **hợp đồng đồng bộ**: gọi một phương thức bình thường, chờ hoàn tất, hoặc quyết định rõ thời điểm chờ qua `Future.get()`/`join()`. Không thể nhận cả “ngay lập tức” và “kết quả thực của việc chưa hoàn tất” mà không có một abstraction biểu diễn trạng thái tương lai.

### 2.1. So sánh các kiểu trả về

| Khai báo method | Dùng với Spring @Async thông thường? | Người gọi có gì? |
|---|---|---|
| `void send()` | Có | Không có handle kết quả; cần kênh lỗi/giám sát riêng |
| `Future<String> load()` | Có | `Future` để chờ, hủy, quan sát trạng thái và lỗi |
| `CompletableFuture<String> load()` | Có | Future và API phối hợp kết quả không chặn |
| `String load()` | **Không** | Kiểu đồng bộ, không phải handle của tác vụ |
| `int count()` / `boolean check()` | **Không** | Giá trị nguyên thủy đồng bộ |
| `UserDto load()` | **Không** | DTO trực tiếp, không biểu diễn việc chưa hoàn tất |
| `List<UserDto> loadAll()` | **Không** | Collection trực tiếp, không phải Future |
| `Optional<UserDto> find()` | **Không** | Có/không có giá trị hiện tại, không phải trạng thái hoàn tất |
| `ResponseEntity<UserDto> response()` | **Không** | Wrapper HTTP không phải Future |
| `CompletionStage<String> load()` | **Không theo khai báo này** | `CompletionStage` không mở rộng `Future`; hãy khai báo `CompletableFuture<String>` nếu dùng Spring @Async |
| `Mono<String> load()` / `Flux<String> load()` | **Không theo @Async này** | Reactive Publisher có mô hình thực thi khác; không phải Future |

**Điểm tinh tế:** `CompletableFuture<T>` vừa triển khai `Future<T>` vừa triển khai `CompletionStage<T>`. Nhưng **kiểu trả về khai báo trong chữ ký** mới là điều Spring kiểm tra. Viết `CompletionStage<T>` không giống viết `CompletableFuture<T>` dù đối tượng thực tế trả về có thể là một `CompletableFuture`.

Các ghi chú này áp dụng cho annotation `@Async` của Spring với interceptor mặc định, không khẳng định mọi hệ thống bất đồng bộ Java chỉ có thể dùng Future. Reactor, Kotlin coroutine, messaging và Spring MVC có những hợp đồng khác; đừng trộn các cơ chế đó một cách vô thức.

## 3. Quy tắc API chính xác của Spring @Async

JavaDoc `@Async` cho biết:

- Method có thể nhận các tham số thông thường.
- Kiểu trả về phải là `void` hoặc thuộc họ `Future` được hỗ trợ.
- Có thể khai báo `CompletableFuture<T>` để phối hợp với bước tiếp theo.
- `Future` caller nhận từ Spring proxy là **handle bất đồng bộ thực** để theo dõi kết quả.
- Method đích vẫn phải trả đúng kiểu khai báo. Vì vậy nó có thể trả một `CompletableFuture.completedFuture(value)` sau khi hoàn tất phép tính **trên worker**; Spring proxy tạo ra một handle khác đại diện cho lần thực thi bất đồng bộ.

Điểm cuối dễ gây nhầm lẫn. Nhìn vào thân method:

```java
@Async("resultExecutor")
public CompletableFuture<Integer> doubleValue(int n) {
    int result = n * 2;
    return CompletableFuture.completedFuture(result);
}
```

Nhiều người thấy `completedFuture(...)` rồi kết luận “method này thực ra không async”. Kết luận ấy **không đúng nếu lời gọi đi qua Spring proxy**. Thân method được worker thực thi và trả một Future đã hoàn tất khi tính xong; proxy sẽ cung cấp cho caller một handle của **toàn bộ công việc được gửi đi**. Điều đó khác với:

```java
public CompletableFuture<Integer> doubleValue(int n) {
    return CompletableFuture.completedFuture(n * 2);
}
```

Nếu thiếu `@Async` và không có cơ chế async khác, phép tính `n * 2` được thực hiện trên luồng gọi; đối tượng CompletableFuture chỉ gói kết quả đã có. **Kiểu trả về CompletableFuture tự nó không bảo đảm có luồng nền.**

Có thể diễn đạt hai tầng Future như sau:

```text
Caller
  |
  | gọi qua @Async proxy
  v
Spring tạo/sử dụng Future ngoài (caller giữ)
  |
  | Executor nhận task
  v
Worker thực thi method đích
  |
  | method trả Future trong, thường completedFuture(value)
  v
Spring lấy/ghép kết quả vào Future ngoài
  |
  v
Caller quan sát Future ngoài -> value hoặc lỗi
```

Với phương thức trả `void`, caller không nhận Future ngoài. Với phương thức khai báo `String`, không có giao diện `Future` phù hợp để proxy trả và Spring từ chối chữ ký đó.

## 4. Ví dụ sai thường gặp và cách sửa đúng

### 4.1. Trả String trực tiếp

**Sai:**

```java
@Async("resultExecutor")
public String generateCode(String prefix) {
    return prefix + "-123";
}
```

**Đúng khi phải nhận giá trị:**

```java
@Async("resultExecutor")
public CompletableFuture<String> generateCode(String prefix) {
    String code = prefix + "-123";
    return CompletableFuture.completedFuture(code);
}
```

Đây là những phương thức minh họa trong một Spring bean; không phải chương trình độc lập. Bên gọi phải thay chữ ký:

```java
CompletableFuture<String> pending = service.generateCode("INV");
// Làm việc khác hoặc gắn pipeline trước khi quan sát kết quả.
String code = pending.join();
```

`join()` có thể chặn cho đến khi kết quả sẵn sàng. Nếu gọi nó ngay và luôn phải chờ, caller vẫn bị chặn dù thân method đã chuyển sang executor khác; xem phần phối hợp không chặn ở dưới.

### 4.2. Trả DTO, List hoặc Optional trực tiếp

**Sai:**

```java
@Async
public UserDto findUser(String id) {
    return repository.load(id);
}
```

**Cách thiết kế trả về hợp lệ:**

```java
@Async("resultExecutor")
public CompletableFuture<UserDto> findUser(String id) {
    UserDto user = repository.load(id);
    return CompletableFuture.completedFuture(user);
}
```

`UserDto` và `repository` là kiểu/thành phần nghiệp vụ do ứng dụng cung cấp; hai đoạn trên **không** phải class Java độc lập. Đoạn sửa dùng Future chứa **UserDto** chứ không trả UserDto trực tiếp.

Nếu nghiệp vụ trả một collection, kiểu tương ứng có thể là `CompletableFuture<List<UserDto>>`. Nếu kết quả có thể vắng mặt, có thể dùng `CompletableFuture<Optional<UserDto>>`. Đó là những Future chứa kết quả nghiệp vụ; không đảo thành `List<CompletableFuture<UserDto>>` nếu nghiệp vụ thực sự chỉ có một task tạo ra cả danh sách, và không nhầm `Optional` với handle hoàn tất.

### 4.3. Trả boolean hoặc int trực tiếp

**Sai:**

```java
@Async
public boolean sendNotification(String recipient) {
    // Trả boolean khi method chạy xong không được Spring @Async hỗ trợ.
    return true;
}
```

**Đúng nếu caller cần biết thành công/thất bại bằng dữ liệu nghiệp vụ:**

```java
@Async("resultExecutor")
public CompletableFuture<Boolean> sendNotification(String recipient) {
    boolean delivered = sendThroughProvider(recipient);
    return CompletableFuture.completedFuture(delivered);
}
```

`sendThroughProvider` là phương thức giả định của service. Nếu thành công được biểu diễn bằng việc method hoàn tất, còn thất bại bằng ngoại lệ, có thể trả `CompletableFuture<Void>` thay vì `Boolean`. Nhưng phải xác định rõ boolean đại diện cho “đã gửi tới provider”, “provider chấp nhận” hay “người nhận đã nhận”; async không tự định nghĩa ý nghĩa thành công.

### 4.4. Không cần trả kết quả: dùng void có chủ đích

```java
@Async("resultExecutor")
public void writeAuditEvent(String eventId) {
    auditRepository.save(eventId);
}
```

Đây là ví dụ phương thức trong Spring bean, giả định có `auditRepository`. `void` hợp lệ nhưng caller không thể gọi `get()` hoặc `join()` để lấy kết quả, cũng không nhận trực tiếp lỗi bên trong task nếu nó chạy trên worker. Nếu audit là dữ liệu quan trọng phải lưu dù JVM crash, chỉ dùng `@Async void` chưa đủ; cần cơ chế lưu công việc bền vững phù hợp.

**Không chọn `void` chỉ để sửa lỗi chữ ký khi caller thực sự cần nhận trạng thái.** Hãy chọn kiểu trả về theo hợp đồng của nghiệp vụ, không chỉ để làm annotation hết báo lỗi.

## 5. Future<T> và CompletableFuture<T> — khác nhau thế nào?

Cả hai có thể biểu diễn một phép tính sẽ hoàn tất sau; nhưng khả năng theo dõi và phối hợp khác nhau:

| Nhu cầu | Future<T> | CompletableFuture<T> |
|---|---|---|
| Chờ lấy giá trị | `get()` | `get()` hoặc `join()` |
| Chờ có thời hạn | `get(timeout, unit)` | `get(timeout, unit)`, và các API timeout đặc thù |
| Kiểm tra hoàn tất | `isDone()` | `isDone()` |
| Hủy theo hợp đồng | `cancel(...)` | `cancel(...)` |
| Biến đổi khi hoàn tất mà không chờ ngay | Không có API composition trong giao diện Future cơ bản | `thenApply(...)`, `thenCompose(...)` |
| Phối hợp hai công việc | Cần orchestration riêng | `thenCombine(...)`, `allOf(...)` |
| Phục hồi lỗi | Thường xử lý quanh `get()` | `exceptionally(...)`, `handle(...)`, `whenComplete(...)` |
| Kiểu được Spring @Async chấp nhận | Có | Có |

Ghi chú: `Future.get()` có thể ném `InterruptedException` và `ExecutionException`; `get(timeout, unit)` thêm `TimeoutException`. `CompletableFuture.join()` thường báo lỗi qua `CompletionException` (unchecked), có thể ném `CancellationException` nếu bị hủy. Những cách báo lỗi khác nhau không làm cho task tự an toàn hơn.

### 5.1. Cách dùng Future cơ bản

```java
@Async("resultExecutor")
public Future<Integer> calculateTax(int amount) {
    int tax = amount / 10;
    return CompletableFuture.completedFuture(tax);
}
```

Đây là đoạn method Spring hợp lệ: chữ ký khai báo `Future<Integer>`; thân method trả một `CompletableFuture<Integer>` vì kiểu này hiện thực `Future<Integer>`.

Caller:

```java
Future<Integer> pending = service.calculateTax(200);
try {
    Integer tax = pending.get();
    System.out.println(tax);
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    // Tầng gọi quyết định dừng hoặc truyền lỗi phù hợp.
} catch (ExecutionException execution) {
    Throwable rootCause = execution.getCause();
    // Ghi nhận/xử lý lỗi gốc.
}
```

Đây là mảnh mã minh họa trong phương thức caller, cần import `java.util.concurrent.Future` và `ExecutionException`. `get()` chặn tới khi task xong. Nếu muốn UI/request không bị chặn, nên thiết kế phản hồi theo tương lai thay vì gọi `get()` ngay.

### 5.2. Cách dùng CompletableFuture

```java
@Async("resultExecutor")
public CompletableFuture<Integer> calculateTax(int amount) {
    int tax = amount / 10;
    return CompletableFuture.completedFuture(tax);
}
```

Caller có thể gắn một phép biến đổi:

```java
CompletableFuture<String> description = service
        .calculateTax(200)
        .thenApply(tax -> "Thuế: " + tax);
```

Phần tạo pipeline ở caller không nhất thiết chặn để chờ phép tính hoàn tất. `thenApply` có thể được chạy ở luồng hoàn tất stage trước hoặc luồng đăng ký continuation nếu kết quả đã sẵn sàng; **không cam kết chạy trên một executor riêng**. Khi cần ấn định executor cho continuation bất đồng bộ, dùng overload `thenApplyAsync(action, specificExecutor)` và đảm bảo executor được quản lý đúng vòng đời.

### 5.3. Khi nhiều bước phụ thuộc

Nếu bước B cũng là async và trả `CompletableFuture<U>`, hãy cân nhắc `thenCompose` để ghép pipeline:

```java
CompletableFuture<String> workflow = service.findUserId("A")
        .thenCompose(userId -> service.loadProfile(userId))
        .thenApply(profile -> "Đã tải hồ sơ: " + profile);
```

Đây là mảnh code kiến trúc, giả định service có hai method trả `CompletableFuture<String>`. `thenCompose` “làm phẳng” future của bước B, khác với `thenApply` có thể tạo `CompletableFuture<CompletableFuture<String>>` nếu lambda trả future.

Đừng bao bọc thêm một lớp `CompletableFuture.supplyAsync(...)` bên ngoài phương thức `@Async` chỉ để “chắc chắn bất đồng bộ”: điều đó có thể tạo hai lượt scheduling, chuyển việc sang common pool nếu không truyền executor, tăng khó khăn về context và làm lỗi khó theo dõi.

## 6. Ví dụ Spring đầy đủ với Java 17: @Async trả Future đúng cách

Ví dụ này cho thấy một method trả `CompletableFuture<Integer>`, một method trả `Future<String>` và executor có thread prefix riêng. Chương trình lấy service từ Spring `ApplicationContext` để lời gọi **đi qua proxy `@Async`**.

Đây là ví dụ Maven tối giản sử dụng **Java 17+** và **Spring Framework 6.2.7**. Spring sẽ được Maven tải; không thể chỉ chạy `javac AsyncReturnTypesDemo.java` khi chưa có dependency.

### 6.1. Cấu trúc dự án

```text
async-return-types-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── lesson/
                └── AsyncReturnTypesDemo.java
```

### 6.2. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>lesson</groupId>
    <artifactId>async-return-types-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.7</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 6.3. Tệp src/main/java/lesson/AsyncReturnTypesDemo.java

```java
package lesson;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class AsyncReturnTypesDemo {

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            ResultService service = context.getBean(ResultService.class);

            CompletableFuture<Integer> number = service.doubleValue(21);
            Future<String> workerName = service.workerName();

            System.out.println("Kết quả: " + number.join());
            System.out.println("Đúng executor: "
                    + workerName.get().startsWith("result-worker-"));
        }
    }

    @Configuration
    @EnableAsync
    public static class AppConfig {

        @Bean("resultExecutor")
        public ThreadPoolTaskExecutor resultExecutor() {
            ThreadPoolTaskExecutor executor =
                    new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(4);
            executor.setThreadNamePrefix("result-worker-");
            return executor;
        }

        @Bean
        public ResultService resultService() {
            return new ResultService();
        }
    }

    public static class ResultService {

        @Async("resultExecutor")
        public CompletableFuture<Integer> doubleValue(int input) {
            int result = input * 2;
            return CompletableFuture.completedFuture(result);
        }

        @Async("resultExecutor")
        public Future<String> workerName() {
            return CompletableFuture.completedFuture(
                    Thread.currentThread().getName());
        }
    }
}
```

### 6.4. Biên dịch/chạy

Trong thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=lesson.AsyncReturnTypesDemo
```

**Hai dòng do chương trình in ra**, không tính thông báo riêng của Maven:

```text
Kết quả: 42
Đúng executor: true
```

Lý do kết quả ổn định:

1. `service` là bean lấy từ `AnnotationConfigApplicationContext`; lời gọi được interceptor `@Async` xử lý.
2. `@EnableAsync` kích hoạt annotation processing trong context có bean service.
3. Cả hai method chỉ rõ `@Async("resultExecutor")`; bean executor đặt thread prefix `result-worker-`.
4. `doubleValue(21)` tính ra 42 ở worker; `CompletableFuture.completedFuture(42)` là handle tạm của method đích; caller giữ CompletableFuture do proxy trả để đọc 42.
5. `workerName()` lấy tên thread của **bên đang chạy thân method**, không lấy tên caller. `startsWith("result-worker-")` kiểm chứng đúng prefix mà không phụ thuộc số thứ tự worker.
6. `join()` và `get()` trong `main` được dùng để **quan sát kết quả demo**. Với request HTTP thật, chờ trực tiếp ngay sau lời gọi có thể phá mục tiêu tránh chặn caller.
7. `ThreadPoolTaskExecutor` là bean do Spring quản lý, nên ứng dụng demo không tự `shutdown()` pool dùng chung trong service; đóng context kết thúc vòng đời bean theo cấu hình.

Ví dụ này cố ý không chứa network hoặc thao tác nặng; nó minh họa **kiểu trả về và đường thực thi**, không phải benchmark. Nếu viết `@Async public Integer doubleValue(...)` để “đơn giản hóa”, chữ ký sẽ không được async interceptor chấp nhận như ví dụ trên.

## 7. Phản ví dụ: vì sao @Async String có thể làm bạn tưởng đã chạy đúng?

Xem class minh họa:

```java
import org.springframework.scheduling.annotation.Async;

public class WrongReturnService {

    @Async("resultExecutor")
    public String getMessage() {
        return "Xin chào";
    }

    public String callFromSameClass() {
        return this.getMessage();
    }
}
```

Đoạn này không độc lập: cần đăng ký service và executor trong Spring mới có thể kiểm chứng proxy behavior. Có **hai đường gọi** cần phân biệt.

### Đường A — gọi từ bean khác qua proxy

```text
Bean ngoài -> proxy WrongReturnService -> @Async interceptor
                                   |
                                   +--> thấy declared return type String
                                   |
                                   +--> báo lỗi kiểu trả về không hợp lệ
```

Đường này không tạo ra `Future<String>` nào vì API mà method công bố là `String`; Spring sẽ từ chối chữ ký không hợp lệ. Đây là lỗi **hợp đồng lập trình**, không phải lỗi mất dữ liệu hay do quá tải pool.

### Đường B — gọi this.getMessage() trong cùng class

```text
Caller -> WrongReturnService.callFromSameClass()
                                 |
                                 +--> this.getMessage()
                                       (bỏ qua proxy)
                                 |
                                 +--> trả "Xin chào" đồng bộ
```

Trong proxy mode mặc định, lời gọi nội bộ thường không đi qua advice. Kết quả `String` có thể xuất hiện, nhưng **đó không phải bằng chứng method đã được Spring chạy bất đồng bộ**. Cách sửa không phải ép kiểu String thành Future tại caller: cần đổi chữ ký của method `@Async` cho phù hợp và gọi qua proxy đúng cách.

Nếu dùng `new WrongReturnService()` thay vì lấy bean từ Spring, annotation cũng không có hiệu lực vì đối tượng không được quản lý. Ngoài ra, lời gọi có thể chạy đồng bộ nếu executor cố ý thực thi trực tiếp; điều quan trọng là xác minh cơ chế và hợp đồng chứ không đoán theo tên annotation.

## 8. CompletableFuture trả về ngay: điều gì đã hoàn tất, điều gì chưa?

`CompletableFuture<T>` là **đối tượng kết quả tương lai**, không phải giá trị `T`. Có ít nhất ba trạng thái cần phân biệt:

- **Chưa hoàn tất:** tác vụ đang xếp hàng hoặc chạy, future chưa chứa kết quả cuối.
- **Hoàn tất bình thường:** có kết quả, có thể lấy bằng `join()/get()` hoặc pipeline.
- **Hoàn tất ngoại lệ/hủy:** future không có kết quả thành công; caller phải xử lý lỗi.

Không được hiểu mọi `CompletableFuture` được trả ra đều nhất thiết đang chờ: một phương thức có thể hoàn tất rất nhanh và caller nhận một future đã hoàn tất. Ngược lại, chỉ vì lời gọi `@Async` trả về ngay cũng không có nghĩa giá trị nghiệp vụ đã sẵn sàng.

Một quy trình an toàn:

```java
CompletableFuture<String> future = service.loadReport(reportId);

future.thenAccept(report -> {
    System.out.println("Có báo cáo: " + report);
}).exceptionally(error -> {
    System.err.println("Không tạo được báo cáo: " + error);
    return null;
});
```

Đây là đoạn code minh họa; `service.loadReport(...)` giả định trả `CompletableFuture<String>`. Caller **không** dùng `String report = service.loadReport(reportId)` vì kiểu không tương thích ngay tại biên dịch. Cũng không nên gọi `future.getNow(null)` rồi xem `null` là thất bại: `null` có thể chỉ có nghĩa công việc chưa xong hoặc kết quả hợp lệ là null, tùy thiết kế.

### 8.1. thenApply, thenCompose, thenCombine

- `thenApply(f)` biến đổi `T` thành `U` khi một stage đã hoàn tất thành công.
- `thenCompose(f)` dùng khi `f` trả một future khác, để không tạo future lồng future.
- `thenCombine(other, f)` chờ hai stage thành công và hợp nhất hai kết quả.
- `whenComplete(action)` quan sát kết quả/lỗi nhưng không phải API phục hồi lỗi có chủ đích.
- `handle(f)` có thể chuyển cả thành công và thất bại thành giá trị mới.
- `exceptionally(f)` cung cấp đường phục hồi một stage thất bại.

Với các pipeline có bước bất đồng bộ, chú ý `thenApply` không đảm bảo chạy ở một worker riêng: có thể chạy trên thread hoàn tất stage trước hoặc caller nếu stage đã hoàn tất. `thenApplyAsync(...)` không truyền executor có thể dùng common pool; nếu cần giữ một chính sách thực thi cụ thể, cung cấp executor được cấu hình rõ.

### 8.2. Không có giá trị đồng bộ để ép kiểu

Phản ví dụ:

```java
// Sai: Future<String> không thể ép thành String.
String result = (String) service.loadReport(reportId);
```

Đây là đoạn phản ví dụ cố tình sai; nếu `loadReport` khai báo trả `CompletableFuture<String>`, ép kiểu trên không hợp lệ và có thể bị lỗi biên dịch kiểu không tương thích. Không nên đổi sang `Object` để cố qua trình biên dịch rồi cast lại; đối tượng runtime vẫn là future, không biến thành `String`.

Nếu bắt buộc chờ tại một ranh giới đồng bộ, hãy **công khai việc chờ**:

```java
String result = service.loadReport(reportId).join();
```

Cách này chỉ hợp lệ nếu kiểu trả về đúng là `CompletableFuture<String>`; nó **chặn** caller cho đến lúc hoàn tất. Nó không phải phương pháp giữ API “vừa đồng bộ vừa không chặn”.

## 9. Java 17 độc lập: chứng minh Future không có kết quả ngay

Ví dụ này dùng `CountDownLatch` để giữ worker chưa hoàn tất cho tới khi caller quyết định mở chốt. Nhờ đó, `isDone()` chắc chắn là `false` ở lần kiểm tra đầu tiên và kết quả sau cùng là 42.

**Tệp `FutureIsNotValueDemo.java` — không cần Spring:**

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FutureIsNotValueDemo {

    public static void main(String[] args)
            throws InterruptedException {

        ExecutorService pool =
                Executors.newSingleThreadExecutor();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch allowFinish = new CountDownLatch(1);

        try {
            CompletableFuture<Integer> future =
                    CompletableFuture.supplyAsync(() -> {
                        started.countDown();
                        try {
                            allowFinish.await();
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(
                                    "Đã bị ngắt", interrupted);
                        }
                        return 21 * 2;
                    }, pool);

            started.await();

            System.out.println("Đã xong trước mở chốt: "
                    + future.isDone());

            allowFinish.countDown();

            System.out.println("Kết quả sau chờ: "
                    + future.join());
        } finally {
            allowFinish.countDown();
            pool.shutdown();
        }
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac FutureIsNotValueDemo.java
java FutureIsNotValueDemo
```

Kết quả:

```text
Đã xong trước mở chốt: false
Kết quả sau chờ: 42
```

Diễn giải:

1. `supplyAsync(..., pool)` trả ngay `CompletableFuture<Integer>` đại diện cho phép tính, không trả `int` 42.
2. Worker bắt đầu, báo `started.countDown()` rồi chờ `allowFinish`. Caller chờ `started.await()` để biết worker đã đứng tại điểm báo hiệu.
3. Lần đọc `future.isDone()` diễn ra khi chốt chưa mở, nên giá trị chính xác là `false`.
4. Sau `allowFinish.countDown()`, worker được phép hoàn tất và trả 42.
5. `join()` lấy 42 sau khi task hoàn tất. Nếu task chưa xong, `join()` sẽ đợi; không biến lời gọi thành “lấy kết quả ngay”.
6. `finally` mở chốt để tránh để worker bị kẹt khi main rời sớm, rồi gọi `shutdown()` cho pool do chương trình tự tạo.

Đây là minh họa bằng Java chuẩn, **không phải triển khai `@Async` của Spring**. Nó giúp người học thấy rõ hai khái niệm: *nhận handle ngay* và *nhận giá trị sau khi công việc xong*. Trong Spring, proxy `@Async` cũng phải cung cấp một hợp đồng kết quả tương lai thay vì biến một `String` đồng bộ thành phép tính bất đồng bộ.

## 10. Lỗi: Future báo lỗi bằng cách nào?

Phân biệt **lỗi khi định tuyến/gửi việc** và **lỗi khi thân task đã chạy**.

### 10.1. Kiểu trả về không được hỗ trợ

`@Async public String get()` là lỗi hợp đồng của phương thức. Trong Spring proxy mặc định, khi interceptor kiểm tra chữ ký không hợp lệ, thường phát sinh `IllegalArgumentException` ngay trên đường gọi. Không có `Future` để lấy lỗi vì method không công bố Future hợp lệ.

Cách sửa: đổi chữ ký và cập nhật tất cả caller liên quan; không chỉ thay annotation hoặc cố bắt một lỗi chung rồi mặc định trả null.

### 10.2. Không tìm thấy executor / executor từ chối

`@Async("missingExecutor")` có thể thất bại khi Spring không tìm được bean phù hợp; pool đã đóng/đầy có thể từ chối công việc. Đây thường là **lỗi đồng bộ tại đường gửi**, chưa chứng minh thân method đã chạy. Cần sửa wiring hoặc xử lý quá tải theo chính sách của API.

### 10.3. Exception trong method trả Future

Một phương thức hợp lệ:

```java
@Async("resultExecutor")
public CompletableFuture<Integer> divide(int a, int b) {
    int value = a / b; // b = 0 gây ArithmeticException.
    return CompletableFuture.completedFuture(value);
}
```

Caller:

```java
CompletableFuture<Integer> pending = service.divide(10, 0);

pending.whenComplete((value, error) -> {
    if (error != null) {
        System.err.println("Không thực hiện được: " + error);
    }
});
```

Đây là các mảnh mã minh họa trong Spring service/caller, không phải chương trình độc lập. Nếu task chạy qua proxy, ngoại lệ trong worker được phản ánh bằng việc Future của caller **hoàn tất theo hướng lỗi**; không thể xem Future đã được tạo là một kết quả thành công.

Nếu dùng `get()`, lỗi thường xuất hiện trong `ExecutionException`, với nguyên nhân tại `getCause()`. Nếu dùng `join()`, lỗi thường được bao trong `CompletionException`; khi future đã bị hủy có thể gặp `CancellationException`. Đừng bỏ qua cause hoặc chỉ log rằng “job failed” mà không có correlation ID.

### 10.4. Exception trong method trả void

```java
@Async("resultExecutor")
public void sendMail(String address) {
    throw new IllegalStateException("Mô phỏng gửi thất bại");
}
```

Caller không có Future trả về để nhận `IllegalStateException` nếu task chạy trên worker. Với Spring `@Async void`, lỗi chưa được xử lý trong method có thể được chuyển cho `AsyncUncaughtExceptionHandler`; Spring mặc định ghi log. Có thể cung cấp handler riêng qua `AsyncConfigurer`.

**Không dùng `void` cho nghiệp vụ mà caller buộc phải biết chắc trạng thái thành công/thất bại.** Có thể sử dụng `CompletableFuture<Void>` nếu không cần dữ liệu nhưng vẫn cần handle hoàn tất.

### 10.5. CompletableFuture<Void> cho thao tác không trả dữ liệu

```java
@Async("resultExecutor")
public CompletableFuture<Void> updateCache(String key) {
    updateCacheInternal(key);
    return CompletableFuture.completedFuture(null);
}
```

Đây là mảnh method minh họa, giả định `updateCacheInternal` tồn tại. Caller nhận được `CompletableFuture<Void>`; `join()` trả `null` khi hoàn tất bình thường, nhưng điểm quan trọng là **có thể chờ/quan sát lỗi**, khác với `void`.

## 11. Không biến controller Spring thành một lời gọi đồng bộ giả

Xét ứng dụng web có service cần thời gian tạo báo cáo. Một anti-pattern là:

```java
// Sai chữ ký cho Spring @Async:
@Async("resultExecutor")
public ReportDto buildReport(String id) {
    return reportRepository.build(id);
}
```

Phương thức trên **không** thể dùng như một phép tính Spring `@Async` trả `ReportDto` trực tiếp. Giả sử đã sửa service thành:

```java
@Async("resultExecutor")
public CompletableFuture<ReportDto> buildReport(String id) {
    ReportDto dto = reportRepository.build(id);
    return CompletableFuture.completedFuture(dto);
}
```

Lúc này controller có nhiều quyết định hợp lệ theo hợp đồng HTTP:

- Nếu API **phải trả nội dung báo cáo khi xử lý xong**, với Spring MVC có thể trả một kiểu kết quả bất đồng bộ được MVC hỗ trợ, chẳng hạn `CompletableFuture<ReportDto>` hoặc `CompletableFuture<ResponseEntity<ReportDto>>` ở tầng controller thích hợp. Spring MVC sẽ quản lý quá trình hoàn tất HTTP theo abstraction được hỗ trợ; điều này khác với việc service trả `ReportDto` đồng bộ.
- Nếu API **chỉ xác nhận đã nhận yêu cầu và trả job ID**, cần lưu trạng thái job bền vững/khả năng tra cứu phù hợp. Trả HTTP 202 không tự chứng minh background job đã hoàn tất.
- Nếu dùng `future.join()` trong controller để trả `ReportDto` ngay, controller **chặn** tới lúc công việc xong. Đây là một lựa chọn đồng bộ có chủ đích chứ không phải lời giải bất đồng bộ hoàn toàn.

Mã ví dụ Spring MVC controller (đặt trong một controller hợp lệ, có service được inject):

```java
@GetMapping("/reports/{id}")
public CompletableFuture<ResponseEntity<ReportDto>> getReport(
        @PathVariable String id) {

    return reportService.buildReport(id)
            .thenApply(ResponseEntity::ok);
}
```

Đây là **mảnh Spring MVC**, giả định `reportService.buildReport` trả `CompletableFuture<ReportDto>` và các kiểu/import Spring MVC đã sẵn sàng. Kiểu trả về của **controller** ở đây được Spring MVC xử lý theo mô hình async web request; không cần gắn `@Async` trực tiếp lên controller method chỉ vì nó trả Future. Hãy để service chuyên xử lý nghiệp vụ, controller quyết định hợp đồng HTTP.

**Phân biệt chiều wrapper:** `CompletableFuture<ResponseEntity<ReportDto>>` có Future ở ngoài và có thể được xử lý bất đồng bộ. `ResponseEntity<CompletableFuture<ReportDto>>` có ResponseEntity ở ngoài; nếu đặt `@Async` trực tiếp lên method trả kiểu này thì declared return type **không phải Future**, nên không đáp ứng hợp đồng Spring `@Async`.

## 12. @Async không bảo đảm mọi caller đều không bị chặn

Có hai lý do lớn:

**Thứ nhất — code caller tự chờ:**

```java
int result = service.doubleValue(21).join();
```

Spring có thể chạy phép tính trên worker, nhưng `join()` vẫn chặn caller cho tới khi có 42. Để có lợi ích không chặn ở biên ứng dụng, cần trả Future hoặc gắn pipeline và chỉ chờ ở điểm thực sự cần kết quả.

**Thứ hai — executor/thiết kế có thể chạy trên caller:** `SyncTaskExecutor` chạy nhiệm vụ ngay trên thread gửi. Một số rejection policy như `CallerRunsPolicy` có thể khiến caller chạy task khi pool quá tải, nếu chưa shutdown. Vì vậy “có `@Async`” không đủ để suy ra “luôn dùng luồng khác” hoặc “caller luôn trả ngay”.

Thêm nữa, `CompletableFuture.completedFuture(value)` **trong một method không có `@Async`** chỉ bọc một kết quả đã có; nếu tính toán nặng xảy ra trước lời gọi `completedFuture` trên caller, trả kiểu CompletableFuture không làm phép tính đó tự chạy nền.

## 13. Các vấn đề Spring proxy có thể che giấu lỗi trả về

Nếu một method `@Async` có kiểu trả về bất hợp lệ nhưng bạn vẫn thấy nó trả một `String` thông thường, hãy kiểm tra hạ tầng trước khi kết luận “Spring tự xử lý được”.

### 13.1. Chưa bật @EnableAsync

Trong cấu hình Java thường cần:

```java
@Configuration
@EnableAsync
public class AsyncConfiguration {
    // Đăng ký Executor/TaskExecutor phù hợp nếu cần.
}
```

Đây là class cấu hình Spring minh họa, cần các import `Configuration` và `EnableAsync` tương ứng. Nếu không bật xử lý `@Async`, annotation có thể không có tác dụng trong context đang dùng; một method thông thường vẫn trả giá trị đồng bộ.

### 13.2. Self-invocation

```java
public String outer() {
    return this.wrongAsyncMethod();
}

@Async
public String wrongAsyncMethod() {
    return "value";
}
```

Đây là **đoạn lỗi cố ý**. Trong proxy mode mặc định, `this.wrongAsyncMethod()` không qua interceptor nên vẫn nhận `String` đồng bộ. Nhưng khi một bean khác gọi method qua proxy, chữ ký `@Async String` không hợp lệ. Không nên thiết kế chương trình dựa vào tình cờ bỏ qua interceptor.

Cách giải quyết: tách method cần chạy async vào một Spring bean khác, inject bean đó và dùng chữ ký `CompletableFuture<String>`. Một lựa chọn nâng cao là AspectJ weaving đúng cấu hình, nhưng không cần cho bài toán cơ bản này.

### 13.3. Class/method không được proxy phù hợp

Method `private`, lời gọi nội bộ, đối tượng tự `new`, method `final` trong cấu hình subclass proxy và cấu trúc nhiều `ApplicationContext` có thể khiến advice không áp dụng như dự tính. Khi học, ưu tiên **public non-final method** trên Spring-managed service, gọi từ bean khác.

Theo JavaDoc `@Async`, không dùng annotation này trên method khai báo trong `@Configuration`. Chỉ đặt cấu hình executor ở `@Configuration`, còn tác vụ ở service riêng.

### 13.4. @Transactional và các advice khác

Nếu method dùng đồng thời `@Transactional` và `@Async`, cần hiểu proxy/advice order và ranh giới transaction. Transaction trong caller **không tự truyền nguyên trạng** sang worker. Nếu nghiệp vụ cần transaction khi chạy async, thiết kế transaction đúng trong đường worker và kiểm tra hành vi thật bằng integration test.

Không giải quyết sai kiểu trả về bằng cách thêm `@Transactional`, `@Retryable` hoặc `@Cacheable`: chúng có trách nhiệm khác nhau và không biến `String` thành kiểu Future hợp lệ cho `@Async`.

## 14. Timeout, hủy và thời điểm kết quả còn giá trị

Với phương thức trả Future, caller có thể đặt ngân sách chờ:

```java
Future<String> pending = service.loadReport(id);
try {
    String report = pending.get(2, TimeUnit.SECONDS);
    consume(report);
} catch (TimeoutException timeout) {
    pending.cancel(true);
    // Ghi nhận timeout và áp dụng hợp đồng nghiệp vụ.
}
```

Đây là mảnh minh họa trong caller, giả định có `service`, `consume` và các import `java.util.concurrent`. Method bao quanh phải xử lý thêm `InterruptedException`/`ExecutionException` hoặc khai báo ném tiếp.

**Hết hạn `get(timeout)` không tự hủy công việc.** `cancel(true)` là yêu cầu hủy/ngắt theo cơ chế của Future/executor; thao tác I/O hoặc task không hợp tác có thể tiếp tục. Ngoài ra, khi caller không đợi nữa, backend có thể đã thực hiện một side effect; hủy Future không phải rollback cho giao dịch hoặc dịch vụ bên ngoài.

Với `CompletableFuture`, có thể dùng các API hoàn tất khi quá hạn theo phiên bản Java, nhưng hãy phân biệt **hành vi của handle** và **việc task bên dưới thực sự dừng**. Đưa deadline vào yêu cầu nghiệp vụ nếu công việc trở nên vô nghĩa sau một thời điểm cụ thể.

Cũng tránh đợi một Future trong chính pool bị giới hạn nếu task được đợi cần tài nguyên/worker của cùng pool. Trường hợp một worker gửi việc phụ rồi chờ trong pool một worker có thể deadlock do starvation, không liên quan tới kiểu trả về `String` hay `CompletableFuture`. Kiểu đúng chỉ mở khả năng chờ/quan sát; thiết kế luồng và tài nguyên vẫn là trách nhiệm riêng.

## 15. Thread safety, visibility và kết quả bên ngoài Future

Một lỗi khác là cố dùng `void` nhưng lưu kết quả vào một field, rồi đọc field ngay sau lời gọi:

```java
private String latestReport;

@Async("resultExecutor")
public void generate() {
    latestReport = "Xong";
}

// Caller khác:
service.generate();
String value = service.getLatestReport();
```

Đây là **mảnh phản ví dụ**, không phải code độc lập. Nó có nhiều vấn đề:

- `generate()` có thể chưa chạy khi caller đọc field.
- Nếu field không được bảo vệ đúng, có vấn đề visibility và data race.
- Nhiều request cùng dùng một field trong singleton bean có thể ghi đè kết quả của nhau.
- Không có ID/handle giúp gắn kết quả với yêu cầu cụ thể.

Dùng `volatile` có thể cải thiện visibility cho một giá trị được ghi/đọc đơn giản nhưng **không** tạo cơ chế chờ hoàn tất, không liên kết kết quả với từng request và không làm một chuỗi cập nhật phức tạp thành nguyên tử.

Nếu caller cần **kết quả của chính lần gọi**, `CompletableFuture<T>` là hợp đồng tự nhiên. Nếu cần **trạng thái job tồn tại lâu** và nhiều client tra cứu, thiết kế kho trạng thái và job ID có vòng đời rõ ràng, không dùng một field singleton làm mailbox toàn ứng dụng.

Một Future hoàn tất thành công cung cấp điểm đồng bộ theo hợp đồng của các API concurrent thích hợp, nhưng dữ liệu mutable bên trong kết quả vẫn cần ownership rõ ràng. Ví dụ trả một `ArrayList` rồi tiếp tục sửa nó từ worker khác có thể gây race; ưu tiên snapshot bất biến hoặc cơ chế đồng bộ đúng.

## 16. Future không làm công việc nền trở thành bền vững

`CompletableFuture<T>` cho caller biết khi nào *một lần thực thi trong tiến trình hiện tại* hoàn tất; nó **không** tự đảm bảo công việc sẽ chạy tới cùng nếu JVM chết, application redeploy, pool bị đóng hoặc worker không được lên lịch.

Nếu nghiệp vụ gồm thanh toán, gửi email quan trọng, tạo chứng từ cần đảm bảo hoặc xử lý có thể kéo dài nhiều phút, phải quyết định rõ:

- Khi nào yêu cầu được xem là **accepted** và khi nào là **succeeded**?
- Có cần lưu lại công việc ở database/broker trước khi trả xác nhận cho caller?
- Retry có an toàn trước thao tác đã thực hiện một phần không? Có idempotency key?
- Làm sao truy vấn trạng thái, báo thất bại hoặc xử lý sự cố sau restart?
- Có giới hạn worker/queue, backpressure và policy từ chối rõ ràng không?

Trả `CompletableFuture<String>` đúng chữ ký `@Async` chỉ giải quyết khả năng **theo dõi kết quả của tác vụ**, không tự giải quyết toàn bộ bài toán reliability.

## 17. Kiểm thử kiểu trả về và hành vi thực thi

Một unit test chỉ thấy method được gắn `@Async` là chưa đủ. Với Spring, ít nhất cần kiểm tra:

1. Context có `@EnableAsync` thực sự hoạt động.
2. Service được lấy từ Spring, không tự `new`.
3. Method trả đúng `void`, `Future<T>` hoặc `CompletableFuture<T>`.
4. Với method cần kết quả, caller nhận Future và kết quả chính xác khi hoàn tất.
5. Ngoại lệ trong thân task làm Future lỗi, không bị coi là thành công.
6. Lỗi kiểu trả về không hợp lệ được phát hiện **khi gọi qua proxy**, không bị che bởi self-invocation.
7. Thread/executor được chọn đúng theo cấu hình; không chỉ suy đoán từ annotation.
8. Test chờ với timeout/latch nếu cần; không dùng `Thread.sleep(...)` để đoán task hoàn tất.
9. Test trường hợp pool quá tải/từ chối và chính sách dừng ứng dụng.
10. Kiểm tra hợp đồng HTTP/job khi caller không muốn đợi kết quả.

Ví dụ assertion minh họa (dùng JUnit và một Spring test context thực tế):

```java
CompletableFuture<Integer> future = service.doubleValue(21);
assertEquals(42, future.join());
```

Đoạn này không độc lập vì cần `service` lấy qua proxy và `assertEquals` từ thư viện kiểm thử. Quan trọng không phải dùng `join()` ở production, mà là test xác nhận kiểu và giá trị của Future.

Nếu cần test một invalid `@Async String`, hãy tạo bean riêng, gọi từ bean test qua proxy và kiểm tra lỗi hợp đồng; đừng “test” bằng cách tạo đối tượng `new` hoặc gọi `this.invalidMethod()` trong cùng bean, vì việc đó kiểm tra lời gọi Java thông thường chứ không kiểm tra Spring async interceptor.

### 17.1. Một số dấu hiệu cần điều tra trên production

- Runtime báo `Invalid return type for async method`: tìm chữ ký `@Async` trả `String`, DTO, primitive, `Optional`, `CompletionStage` hoặc loại không hỗ trợ.
- Người dùng báo kết quả đôi khi null: tìm logic đọc field ngay sau `void @Async` hoặc sử dụng `getNow(...)` không phân biệt trạng thái.
- API chậm dù đã `@Async`: tìm `join()/get()` ngay trên request thread hoặc `CallerRunsPolicy` lúc pool quá tải.
- Method tưởng async nhưng log luôn cùng tên thread caller: kiểm tra proxy, `@EnableAsync`, self-invocation và executor.
- Caller không thấy lỗi task: kiểm tra `void` vs Future, `AsyncUncaughtExceptionHandler` và việc có quan sát exceptionally/completion hay không.
- Lỗi “đã trả 202 nhưng tác vụ mất”: kiểm tra bảo đảm lưu công việc, không coi in-memory executor hoặc Future là message queue bền vững.

## 18. Bài tập thực hành có hướng giải

### Bài 1 — Nhận diện chữ ký sai

Cho các chữ ký:

```java
@Async String a();
@Async void b();
@Async Future<Integer> c();
@Async CompletableFuture<List<String>> d();
@Async Optional<String> e();
@Async CompletionStage<String> f();
```

Hỏi: loại nào phù hợp với Spring `@Async` thông thường?

**Định hướng:** `b`, `c` và `d` phù hợp về kiểu trả về. `a`, `e`, `f` không phù hợp; riêng `CompletionStage` không mở rộng `Future` dù một triển khai như `CompletableFuture` có thể hiện thực cả hai giao diện. Các method còn cần được Spring quản lý/proxy đúng để chạy async.

### Bài 2 — Sửa cách sử dụng DTO

Một service hiện có:

```java
@Async
public CustomerDto load(String id) {
    return repository.find(id);
}
```

Caller viết `CustomerDto dto = service.load(id);`. Hãy sửa cả chữ ký service và caller để caller có thể làm việc khác trong lúc chờ kết quả.

**Định hướng:** Sửa service thành `CompletableFuture<CustomerDto>`, tính DTO bên trong worker rồi trả `CompletableFuture.completedFuture(dto)`. Caller giữ `CompletableFuture<CustomerDto>`, gắn `thenApply/thenAccept` hoặc chuyển future cho tầng có trách nhiệm. Không gọi `join()` ngay nếu mục tiêu là tránh chặn.

### Bài 3 — Vì sao completedFuture vẫn đúng?

Method `@Async` tính một giá trị rồi trả `CompletableFuture.completedFuture(value)`. Có mâu thuẫn với mục tiêu bất đồng bộ không?

**Định hướng:** Không, nếu method được gọi qua proxy. Thân method chạy trong task của Spring executor; `completedFuture` là handle tạm từ method đích. Proxy đưa cho caller Future đại diện cho lần chạy async đó.

### Bài 4 — Tìm self-invocation

`@Async public String get()` trả String bình thường khi được gọi từ `this.get()`. Có thể kết luận Spring hỗ trợ String không?

**Định hướng:** Không. Lời gọi nội bộ không đi qua proxy trong chế độ mặc định. Phải đổi chữ ký và gọi qua Spring bean proxy trước khi đánh giá hành vi.

### Bài 5 — CompletableFuture nhưng vẫn chặn

Caller gọi `service.calculate().join()` ngay sau khi gửi task. Vì sao HTTP request vẫn có thể chậm dù executor chạy trên worker riêng?

**Định hướng:** `join()` chặn luồng HTTP request tới lúc Future hoàn tất. Dùng tích hợp async ở tầng web hoặc thiết kế phản hồi theo job ID/SLO nếu không muốn chờ.

### Bài 6 — Không cần dữ liệu nhưng cần biết đã xong

Bạn đang dùng `@Async void saveAudit()`; caller không cần trả dữ liệu nhưng phải biết đã lưu thành công trước bước tiếp theo. Chọn kiểu nào?

**Định hướng:** `CompletableFuture<Void>` và xử lý completion/failure, hoặc thiết kế transaction/orchestration nghiệp vụ khác phù hợp. `void` không cung cấp handle hoàn tất cho caller.

### Bài 7 — Chạy ví dụ

Chạy project `AsyncReturnTypesDemo` trên Spring, sau đó chạy `FutureIsNotValueDemo` thuần Java 17. Giải thích vì sao ví dụ đầu nhận 42 qua Future từ proxy, còn ví dụ thứ hai nhận được Future trước khi task được phép hoàn tất.

**Định hướng:** Cả hai tách handle khỏi giá trị; Spring giải quyết dispatch qua interceptor và `@Async`, còn Java SE dùng `CompletableFuture.supplyAsync(..., executor)`.

## 19. Checklist khi review một method @Async có kết quả

| Câu hỏi | Điều kiện mong muốn |
|---|---|
| Method định trả dữ liệu gì? | `T` được đặt **bên trong** `Future<T>`/`CompletableFuture<T>` |
| Kiểu trả về có được @Async hỗ trợ? | `void` hoặc kiểu `Future` phù hợp |
| Caller có thật sự cần kết quả? | Nếu có, không chọn `void` rồi đọc shared mutable field |
| @EnableAsync đã bật đúng context? | Có, và method được gọi qua Spring proxy |
| Executor có được cấu hình rõ? | Biết bean được chọn, giới hạn worker/queue và rejection |
| Kết quả được lấy ở đâu? | `get/join` ở ranh giới chấp nhận chặn hoặc pipeline không chặn |
| Exception được quan sát ở đâu? | Future completion/error hoặc handler cho `void` |
| Có timeout/cancel/deadline không? | Có chính sách đúng yêu cầu và hiểu task có thể chưa dừng |
| State/thread context có an toàn? | Không suy diễn transaction hay ThreadLocal tự lan truyền |
| Kết quả cần tồn tại qua restart? | Nếu có, dùng cơ chế lưu công việc/nhật ký bền vững |

## 20. Tóm tắt nguyên tắc

- **Không dùng kiểu trả về đồng bộ như `String`, `int`, DTO hay `List<T>` trực tiếp trên Spring `@Async` rồi giả định caller vẫn nhận được dữ liệu.**
- **`@Async` hỗ trợ `void` hoặc các kiểu `Future` phù hợp**, phổ biến nhất là `CompletableFuture<T>` khi cần kết quả và phối hợp nhiều bước.
- **Spring proxy trả Future đại diện cho lần thực thi bất đồng bộ**; method đích có thể dùng `CompletableFuture.completedFuture(value)` như một handle tạm khi đã tính xong trên worker.
- **`void` hợp lệ nhưng không cho caller handle hoàn tất.** Nếu cần biết xong/lỗi mà không cần dữ liệu, cân nhắc `CompletableFuture<Void>`.
- **`get()`/`join()` là những điểm có thể chặn.** Dùng pipeline hoặc abstraction async của tầng web khi muốn tránh chặn caller.
- **Kiểu đúng chưa đủ:** phải bật `@EnableAsync`, gọi qua Spring bean proxy, chọn executor phù hợp và quan sát lỗi/tải/context theo nhu cầu.
- **Future không phải dữ liệu nghiệp vụ và không phải job queue bền vững.** Nó là hợp đồng theo dõi một lần thực thi; reliability vẫn cần thiết kế riêng.

### Tài liệu chuẩn

- [Spring Framework Javadoc — @Async](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Async.html)
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework Javadoc — @EnableAsync](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Java SE 17 Javadoc — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 17 Javadoc — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java SE 17 Javadoc — CompletionStage](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionStage.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-results-007">Trả về dữ liệu độc lập với tài nguyên đã đóng</a>

<details>
<summary>Xem chi tiết</summary>

# Trả về dữ liệu độc lập với tài nguyên đã đóng

## 1. Vấn đề: Future hoàn tất không có nghĩa tài nguyên còn mở

Trong Spring Framework, một tác vụ chạy qua `@Async`, `AsyncTaskExecutor` hoặc `CompletableFuture` có thể đọc database, tệp, HTTP response hay stream rồi trả kết quả cho caller. Điều dễ sai là **trả chính đối tượng còn phụ thuộc vào tài nguyên đang mở**: `ResultSet`, `InputStream`, JPA entity chứa lazy association, hoặc một `Stream` đọc dữ liệu theo yêu cầu.

Ví dụ: tác vụ đọc database trong transaction, trả một entity, kết thúc transaction; caller sau đó gọi `entity.getItems()`. Nếu `items` chưa được khởi tạo và persistence context không còn khả dụng, thao tác có thể ném `LazyInitializationException`. Tương tự, một `BufferedReader` được trả ra sau `try-with-resources` đã đóng reader không còn đọc tiếp được.

**Nguyên tắc:** tại thời điểm hoàn tất kết quả bất đồng bộ, dữ liệu được trả về phải **có quyền sở hữu và vòng đời độc lập** với những tài nguyên mà task đã đóng, trừ khi API chủ đích chuyển giao quyền sở hữu tài nguyên và định nghĩa rõ ai phải đóng nó.

Bất biến cần đạt với kết quả thông thường:

```text
worker mở tài nguyên
      |
      v
đọc đủ các giá trị cần thiết
      |
      v
chuyển thành dữ liệu sở hữu độc lập (DTO / value object / immutable copy)
      |
      v
đóng tài nguyên trong finally / try-with-resources
      |
      v
Future hoàn tất với DTO
      |
      v
caller dùng DTO: không truy cập lại tài nguyên đã đóng
```

Từ khóa là **độc lập với vòng đời**, không nhất thiết mọi dữ liệu phải là `String` hay `record`. Một DTO có thể chứa danh sách, map, giá trị thời gian và object con, miễn là không lén giữ JDBC cursor, JPA proxy chưa khởi tạo, session, stream hay đối tượng mutable đang bị task khác sửa.

## 2. Phân biệt dữ liệu thuần và handle gắn với tài nguyên

| Thứ muốn trả qua Future | Phụ thuộc vòng đời sau khi worker rời scope? | Cách xử lý phù hợp |
| --- | --- | --- |
| `String`, `long`, `BigDecimal`, `Instant` | Thông thường không | Trả trực tiếp nếu giá trị đã được tính xong |
| `record ReportDto(...)` chỉ chứa giá trị độc lập | Không, nếu các trường cũng độc lập | Trả DTO; sao chép collection/array mutable khi cần |
| `List<ReportDto>` đã materialize | Không phụ thuộc DB nếu phần tử là DTO độc lập | Có thể dùng `List.copyOf` để giữ cấu trúc bất biến |
| JPA entity có `LAZY` relationship | Có thể cần persistence context khi truy cập trường chưa load | Truy xuất và map thành DTO bên trong transaction |
| Hibernate proxy / `PersistentCollection` | Có thể cần session hoặc bị ảnh hưởng bởi detach | Không trả như kết quả dữ liệu đã hoàn tất |
| `java.sql.ResultSet` / `Statement` | Phụ thuộc statement/connection còn mở | Duyệt `ResultSet` và map thành giá trị trước khi đóng |
| `InputStream`, `Reader`, `Stream<T>` gắn file/DB | Phụ thuộc nguồn còn mở và thứ tự đọc | Materialize có giới hạn hoặc thiết kế streaming sở hữu tài nguyên rõ |
| `byte[]` mới được copy độc lập | Không phụ thuộc stream gốc, nhưng mutable | Cân nhắc defensive copy, dung lượng và quyền sửa |
| `Path` hoặc ID file trong object storage | Là locator, không phải handle đang mở | Có thể trả locator; nơi dùng phải tự mở nguồn mới và xử lý file thay đổi/xóa |
| `InputStreamResource` bao một stream đã mở | Có thể single-use, phụ thuộc stream sống | Không trả rồi đóng stream trước khi framework đọc |

**Không đánh đồng “object còn tồn tại trên heap” với “object vẫn sử dụng được”.** Java garbage collector giữ object còn được tham chiếu, nhưng không tự kéo dài transaction, database cursor, file descriptor hoặc HTTP connection mà object cần.

Một JPA entity đã detach **không nhất thiết luôn lỗi**: các thuộc tính đơn giản và association đã được tải có thể vẫn đọc được. Vấn đề là API phụ thuộc ngầm vào việc attribute nào đã fetch, session nào còn mở và người dùng gọi getter ở thời điểm nào. DTO rõ ràng giúp loại bỏ sự phụ thuộc khó kiểm soát đó.

## 3. Ranh giới thời gian và thread trong Spring concurrency

Giả sử controller gọi bean async và nhận `CompletableFuture<ReportDto>`:

```text
HTTP/caller thread                        Thread của reportExecutor
------------------                        -----------------------
request -> async proxy
           |
           +-- submit task ---------------> @Async method bắt đầu
           |                                      |
           |                                mở transaction riêng
           |                                      |
           |                                JPA query, lấy trường cần
           |                                      |
           |                                map ReportEntity -> ReportDto
           |                                      |
           |                                transaction kết thúc
           |                                      |
           |                             Future hoàn tất với ReportDto
           |
caller nhận handle  <------------------ kết quả sẵn sàng để get/join
           |
         get() / compose
           |
     dùng ReportDto độc lập
```

**Điểm quan trọng:** giao dịch imperative của Spring dùng `PlatformTransactionManager` thường được gắn với thread hiện tại. Transaction của HTTP caller **không tự truyền** sang thread worker chỉ vì `@Async` trả Future. Do đó truy vấn và mapping cần nằm trong một transactional boundary **thực sự chạy trên worker** (thông qua một lời gọi được Spring transactional advice áp dụng).

`Future.get()` của JDK cung cấp quan hệ *happens-before* giúp caller nhìn thấy các ghi dữ liệu trước khi task hoàn tất, nhưng **không mở lại transaction hay hồi sinh tài nguyên đã đóng**. Đừng dùng memory visibility để giải quyết lỗi lifetime.

Có hai ranh giới độc lập phải giải quyết:

1. **Concurrency boundary**: nhiều thread có thể truy cập cùng object → cần immutable snapshot hoặc đồng bộ phù hợp.
2. **Resource boundary**: tài nguyên mở trong phạm vi nào, ai đóng, ai còn quyền dùng → cần hoàn tất materialization hoặc chuyển ownership rõ ràng.

## 4. Ví dụ sai: trả JPA entity rồi mới truy cập lazy relationship

Đoạn sau là **mã minh họa trong ứng dụng Spring Data JPA**, không phải Java độc lập:

```java
@Service
public class BadAsyncReportService {
    private final ReportRepository repository;

    public BadAsyncReportService(ReportRepository repository) {
        this.repository = repository;
    }

    @Async("reportExecutor")
    public CompletableFuture<ReportEntity> loadAsync(long id) {
        // Giả sử repository trả entity có collection LAZY.
        ReportEntity report = repository.findById(id).orElseThrow();
        return CompletableFuture.completedFuture(report);
    }
}
```

Caller:

```java
ReportEntity entity = reports.loadAsync(7L).get();
int count = entity.getItems().size(); // Có thể lỗi nếu collection chưa load.
```

Ở đây `findById` có thể chạy với transactional boundary do repository áp dụng; sau khi boundary kết thúc, association LAZY chưa load thường không còn được truy cập an toàn. Có hệ thống bật Open Session in View hoặc có cấu hình session khác làm lỗi **tạm thời không xuất hiện**; đó không phải một hợp đồng tốt cho kết quả bất đồng bộ. Đặc biệt, persistence context của request thread cũng không tự trở thành persistence context của worker.

Lỗi khác là trả `Stream<ReportEntity>` từ repository sau khi transaction truy vấn đã đóng. Stream có thể cần cursor/connection khi caller duyệt. Trả reference tới Stream chưa được tiêu thụ **không giống** trả danh sách dữ liệu đã hoàn tất.

## 5. Mẫu đúng: transactional reader tạo DTO trước khi rời scope

Thay vì đưa entity ra khỏi phạm vi quản lý persistence, hãy chuyển tất cả dữ liệu cần dùng sang một DTO độc lập **trước khi transaction kết thúc**.

Giả sử domain có `ReportEntity`, `ReportItem`, `ReportRepository`; đoạn dưới là **các lớp tích hợp Spring mang tính mẫu**, đặt trong các tệp Java và import phù hợp, không phải chương trình tự chạy bằng JDK thuần.

```java
public record ReportDto(
        long id,
        String ownerName,
        java.util.List<String> itemNames) {

    public ReportDto {
        itemNames = java.util.List.copyOf(itemNames);
    }
}
```

```java
@Service
public class ReportSnapshotReader {
    private final ReportRepository repository;

    public ReportSnapshotReader(ReportRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ReportDto loadSnapshot(long id) {
        ReportEntity entity = repository.findById(id).orElseThrow();

        // Các getter có thể kích hoạt LAZY fetch: thực hiện bên trong
        // transactional boundary trên thread đang đọc.
        String owner = entity.getOwner().getName();
        java.util.List<String> names = entity.getItems().stream()
                .map(ReportItem::getName)
                .toList();

        return new ReportDto(entity.getId(), owner, names);
    }
}
```

```java
@Service
public class AsyncReportService {
    private final ReportSnapshotReader reader;

    public AsyncReportService(ReportSnapshotReader reader) {
        this.reader = reader;
    }

    @Async("reportExecutor")
    public CompletableFuture<ReportDto> loadAsync(long id) {
        // Chạy trên worker; gọi sang bean reader qua Spring proxy.
        ReportDto value = reader.loadSnapshot(id);
        return CompletableFuture.completedFuture(value);
    }
}
```

Yêu cầu để mẫu hoạt động đúng:

- Có `@EnableAsync`, bean executor `reportExecutor`, Spring Data JPA và transaction management được cấu hình phù hợp.
- `ReportSnapshotReader` và `AsyncReportService` **là hai Spring-managed beans khác nhau**, được inject theo constructor. Nhờ vậy, lời gọi `reader.loadSnapshot(id)` từ worker có thể đi qua proxy `@Transactional`. Tránh `this.loadSnapshot(id)` trong cùng bean ở proxy mode vì self-invocation có thể bỏ qua advice.
- Các liên kết lazy cần dùng được truy cập/mapped khi transaction còn hoạt động. Nếu mô hình quan hệ nhiều tầng, chọn fetch plan, entity graph, projection hoặc truy vấn DTO thích hợp để tránh N+1 queries và tải quá nhiều dữ liệu.
- DTO chứa `long`, `String` và một danh sách `String` copy bất biến; nó không mang `EntityManager`, `Session` hoặc entity proxy ra ngoài transaction.
- `CompletableFuture.completedFuture(value)` được tạo **bên trong worker** sau khi reader đã tính xong. Spring async proxy trả handle cho caller; không có nghĩa caller buộc chạy method đồng bộ.

`@Transactional(readOnly = true)` là một chính sách giao dịch/read-only hint; nó **không tự biến entity thành immutable DTO**, cũng không thay thế việc mapping. Nếu mapping bất cẩn chép một `List<ReportEntity>` vào DTO, kết quả vẫn có thể chứa phụ thuộc vào session và state mutable.

## 6. Ví dụ Java 17+ chạy độc lập: kết quả sống lâu hơn reader

Để chứng minh mà không cần database thật, ví dụ dưới đây dùng `BufferedReader` đóng bởi `try-with-resources` và `ExecutorService` trả `Future`. Trường hợp tốt **đọc hết dữ liệu và trả record độc lập**; trường hợp sai **trả một reader đã bị đóng trong worker**.

Tạo tệp `DetachedDataDemo.java`:

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class DetachedDataDemo {

    public record Snapshot(long id, String title, List<String> lines) {
        public Snapshot {
            lines = List.copyOf(lines); // Bản sao cấu trúc bất biến.
        }
    }

    static Snapshot readSnapshot() throws IOException {
        try (BufferedReader reader =
                     new BufferedReader(new StringReader("alpha\nbeta\n"))) {
            List<String> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(line);
            }
            return new Snapshot(7L, "Q4", rows);
        } // reader đã đóng; Snapshot vẫn dùng được.
    }

    static BufferedReader brokenReader() throws IOException {
        BufferedReader reader =
                new BufferedReader(new StringReader("unread"));
        try (reader) {
            return reader; // Sai: Java đóng reader trước khi trả kết quả.
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            Future<Snapshot> good = pool.submit(DetachedDataDemo::readSnapshot);
            Snapshot value = good.get(); // Worker đã hoàn tất và reader đã đóng.
            System.out.println("snapshot = " + value.id() + "/" + value.title());
            System.out.println("lines = " + String.join(",", value.lines()));

            try {
                value.lines().add("gamma");
                System.out.println("immutable = false");
            } catch (UnsupportedOperationException ex) {
                System.out.println("immutable = true");
            }

            Future<BufferedReader> bad =
                    pool.submit(DetachedDataDemo::brokenReader);
            try {
                bad.get().readLine();
                System.out.println("closed reader fails = false");
            } catch (IOException ex) {
                System.out.println("closed reader fails = true");
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac DetachedDataDemo.java
java DetachedDataDemo
```

Stdout xác định:

```text
snapshot = 7/Q4
lines = alpha,beta
immutable = true
closed reader fails = true
```

**Giải thích:**

1. `readSnapshot()` mở reader bên trong worker, duyệt hết các dòng thành `ArrayList<String>`, tạo `Snapshot` với `List.copyOf`, rồi đóng reader tự động khi ra khỏi `try`.
2. Caller nhận `Snapshot` qua `Future.get()`. Lúc này không còn cần reader: dữ liệu nằm trong `String` và cấu trúc danh sách độc lập.
3. `List.copyOf` ngăn các thao tác thay đổi cấu trúc danh sách đã trả; ví dụ `add` ném `UnsupportedOperationException`. Vì phần tử là `String` bất biến, snapshot ở đây không chứa tham chiếu tới dữ liệu mutable bên ngoài.
4. `brokenReader()` trả reference tới reader nằm trong `try-with-resources`, nhưng Java đóng nó **trước khi caller nhận kết quả**. Sau `bad.get()`, `readLine()` ném `IOException`.
5. `Future.get()` đồng bộ hoàn tất phép tính nhưng **không giữ nguồn dữ liệu mở**. Chính điều này khiến hai nhánh có kết quả khác nhau.

Trong ví dụ, `StringReader` chỉ mô phỏng vòng đời của stream; code thật có thể là `Files.newBufferedReader`, `InputStream` từ HTTP hoặc một JDBC cursor. Quy tắc ownership vẫn như nhau. Trong production, shutdown executor nên theo vòng đời của ứng dụng/bean, không dừng pool dùng chung sau mỗi request.

## 7. Một lỗi tương tự với JDBC ResultSet

`java.sql.ResultSet` phụ thuộc vào `Statement` và `Connection`. Đoạn sau **sai về thiết kế**:

```java
public ResultSet loadRows(DataSource ds) throws SQLException {
    try (Connection connection = ds.getConnection();
         PreparedStatement statement =
                 connection.prepareStatement("SELECT id, name FROM report")) {
        return statement.executeQuery(); // ResultSet phụ thuộc statement.
    } // Statement và Connection được đóng.
}
```

Đây là **mảnh Java JDBC**, cần import `javax.sql.DataSource` và các lớp `java.sql`; không phải chương trình độc lập. Việc trả về `ResultSet` không chuyển quyền sở hữu connection cho caller: `try-with-resources` đóng các resource ngay khi thoát scope.

Cách đúng là tạo dữ liệu kết quả ngay bên trong scope:

```java
public List<ReportRow> loadRows(DataSource ds) throws SQLException {
    List<ReportRow> rows = new ArrayList<>();

    try (Connection connection = ds.getConnection();
         PreparedStatement statement =
                 connection.prepareStatement("SELECT id, name FROM report");
         ResultSet rs = statement.executeQuery()) {

        while (rs.next()) {
            rows.add(new ReportRow(rs.getLong("id"), rs.getString("name")));
        }
    }
    return List.copyOf(rows);
}

public record ReportRow(long id, String name) {}
```

Đây là **mảnh minh họa**, cần `ArrayList`, `List`, JDBC imports và các khai báo trong cấu trúc Java hợp lệ. Khi code trả về, connection, statement và result set đã đóng; chỉ còn danh sách các giá trị `long` và `String`. Tránh quên đóng `ResultSet` trong mã dùng JDBC trực tiếp dù statement thường quản lý nó.

Khi bảng có hàng triệu dòng, **không materialize vô hạn**. Cân nhắc phân trang, chunking, ghi tệp có quản lý, hoặc stream dữ liệu trong đúng scope tài nguyên đang mở với cơ chế backpressure.

## 8. Vì sao Stream<T> đặc biệt dễ gây hiểu lầm?

`Stream<T>` trong Java mô tả một *pipeline có thể tính lười*, không phải một collection đã được đọc đầy đủ. Một stream từ `List<String>` thuần có thể không cần I/O, nhưng stream từ JPA repository, JDBC, file hoặc HTTP body có thể dựa trên connection/cursor/reader mở.

```java
// Mảnh minh họa: KHÔNG trả stream DB ra khỏi transactional scope.
@Transactional(readOnly = true)
public Stream<ReportEntity> streamReports() {
    return repository.streamAllReports();
}
```

Nếu caller gọi `stream.forEach(...)` sau khi transaction đã kết thúc, tài nguyên có thể không còn sử dụng được. Ngay cả khi stream còn hoạt động, người dùng phải biết ai có nghĩa vụ `close()`, khi nào transaction kết thúc và có được phép truy cập stream từ thread khác không.

Hai mô hình an toàn hơn:

- **Materialize có giới hạn:** mở nguồn, duyệt, map thành DTO và thu vào `List<DTO>` **trước khi transaction kết thúc**; đóng stream nếu API yêu cầu.
- **Stream thật có kiểm soát:** toàn bộ quá trình mở nguồn → đọc từng phần → ghi tới destination → đóng nguồn diễn ra trong một callback/hàm có ownership rõ ràng, không trả stream đang mở về caller chỉ để caller đọc sau.

Đừng nghĩ `stream.toList()` tự xử lý mọi vấn đề: nó chỉ giải quyết **tính lười và materialization** nếu được gọi khi nguồn còn mở; các phần tử thu được vẫn có thể là entity/proxy còn phụ thuộc session. Cần map phần tử thành dữ liệu độc lập trước khi đưa vào danh sách trả về.

## 9. Trả kết quả qua CompletableFuture: giá trị phải sẵn sàng trước khi complete

Nếu tự dùng JDK `CompletableFuture.supplyAsync`, hãy tạo giá trị **bên trong tác vụ** rồi mới return:

```java
CompletableFuture<ReportDto> future =
        CompletableFuture.supplyAsync(
                () -> reportReader.loadSnapshot(reportId),
                executor
        );
```

Đây là **mảnh mã tích hợp** giả định `reportReader` là bean đã được inject, `reportId` là ID và `executor` là `java.util.concurrent.Executor` đã có. Nếu `loadSnapshot` được Spring quản lý transaction qua proxy, việc gọi nó trong lambda sẽ mở boundary trên worker. Nếu tự truy vấn JDBC thay vì gọi reader bean, lambda nên sở hữu tài nguyên và trả DTO sau khi `try-with-resources` kết thúc.

Ngược lại, không nên tạo Future từ một resource sẽ bị đóng trước khi xử lý tiếp:

```java
InputStream input = openInput();
try (input) {
    return CompletableFuture.supplyAsync(() -> parse(input), executor);
}
// Sai: input có thể bị đóng ngay khi method trên caller trả về,
// trong khi worker vẫn chưa bắt đầu parse.
```

Đây là **mã minh họa sai về ownership**, không phải phương thức độc lập; `openInput`, `parse`, `executor` cần được định nghĩa trong ứng dụng thật. Cách sửa: để `openInput()` xảy ra **bên trong** supplier và đóng ngay trong supplier sau khi parse, hoặc đọc dữ liệu đủ nhỏ thành snapshot độc lập **trước** khi submit.

`CompletableFuture.completedFuture(value)` không làm value được detach tự động: nếu `value` là một entity chứa lazy proxy hoặc một stream đã đóng, Future vẫn hoàn tất với chính object không an toàn đó.

## 10. Chuyển ownership tài nguyên: ngoại lệ có chủ đích

Không phải hệ thống nào cũng nên nạp toàn bộ dữ liệu vào RAM rồi trả DTO. Với file lớn hoặc video, streaming là hợp lý, nhưng phải thiết kế **ai mở, ai đọc, ai đóng**.

Ví dụ Spring MVC `StreamingResponseBody` (mảnh mã controller, cần imports, bean `exports` và cấu hình Spring Web MVC):

```java
@GetMapping("/exports/{id}")
public ResponseEntity<StreamingResponseBody> download(@PathVariable long id) {
    StreamingResponseBody body = output -> {
        // Mở nguồn TRONG callback thực hiện streaming.
        try (InputStream input = exports.open(id)) {
            input.transferTo(output);
        }
        // Không đóng output của framework tại đây.
    };

    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(body);
}
```

Trong Spring MVC, callback viết response body có thể chạy sau khi controller method đã trả về và thường được xử lý qua async infrastructure. Việc mở nguồn **trong callback** bảo đảm stream được mở khi thực sự cần đọc. Cần cấu hình MVC async executor và timeout phù hợp, cùng chính sách khi client ngắt kết nối.

Sai lầm thường gặp là mở `InputStream` trong controller rồi đóng nó khi controller trả, trong khi callback mới bắt đầu đọc sau đó. Cũng không nên cho callback dùng trực tiếp một `EntityManager` hoặc transaction đã tồn tại ở HTTP caller thread: callback cần tự tạo ranh giới tài nguyên/transaction phù hợp.

Đây là **streaming với ownership rõ**, khác với trường hợp trả một DTO đã materialize. Các mẫu khác như trả locator tới object storage, URL có hạn dùng, hoặc ID của kết quả đã lưu bền vững cũng tách được quyền sở hữu nhưng cần xử lý version, TTL và tính sẵn có tại thời điểm caller sử dụng.

## 11. Dữ liệu đầu vào cũng có vòng đời

Bài toán không chỉ xảy ra với **kết quả trả về**; input truyền từ request sang tác vụ async cũng có thể phụ thuộc vào scope đã kết thúc.

Ví dụ không nên mặc định an toàn:

```java
// Mảnh minh họa trong HTTP controller:
executor.execute(() -> parse(request.getInputStream()));
```

`request` và stream request được servlet container quản lý theo lifecycle xử lý HTTP; task có thể chỉ bắt đầu sau khi response đã hoàn tất. Muốn xử lý bất đồng bộ, hãy copy dữ liệu cần thiết (có giới hạn dung lượng) trước khi rời request scope, hoặc lưu payload vào kho bền vững rồi gửi **ID bất biến** cho worker. `MultipartFile` tạm và các stream thuộc request cũng cần quy tắc tương tự.

Ví dụ cách truyền dữ liệu nhỏ có chủ đích:

```java
byte[] payload = request.getInputStream().readAllBytes();
byte[] snapshot = payload.clone();
executor.execute(() -> process(snapshot));
```

Đây là **mảnh tích hợp**, không phải hướng dẫn đọc upload không giới hạn vào RAM. Trong thực tế phải giới hạn kích thước và validate input trước, tránh truyền mảng mutable cho nhiều writer; có thể thay `byte[]` bằng một value object/DTO. Với payload lớn, lưu ra tệp/object storage hoặc hàng đợi và truyền reference có lifecycle kiểm soát.

Một lỗi tương tự: gửi entity đang gắn với persistence context của caller cho worker và mong worker gọi lazy getter được, hoặc mong `@Transactional` ở caller tự truyền transaction sang worker. Với Spring imperative transactions, đó không phải bảo đảm hợp lệ.

## 12. Độ bất biến: List.copyOf chỉ là bước đầu

DTO không chứa resource đã đóng mới giải quyết một nửa vấn đề. Nửa còn lại là tránh trạng thái mutable bị sửa đồng thời sau khi Future trả kết quả.

`List.copyOf(source)` tạo danh sách **không cho sửa cấu trúc**, nhưng là *shallow copy*: nếu phần tử là `MutableReportLine` có trường thay đổi được, caller khác vẫn có thể sửa phần tử và ảnh hưởng người đọc. Tương tự, Java `record` không tự deep copy `List`, `Map`, mảng hoặc custom mutable object.

Ví dụ cần defensive copy đối với `byte[]`:

```java
public final class DocumentBytes {
    private final byte[] content;

    public DocumentBytes(byte[] content) {
        this.content = content.clone();
    }

    public byte[] content() {
        return content.clone();
    }
}
```

Đây là **class độc lập** (cần lưu `DocumentBytes.java` nếu chạy riêng), minh họa rằng cả đầu vào constructor lẫn đầu ra accessor đều phải được copy nếu không muốn caller có quyền sửa mảng nội bộ. Với file rất lớn, copy lặp có thể đắt: ưu tiên mô hình ownership theo phần, buffer có lifetime rõ, hoặc locator tới tệp đã lưu.

Thực hiện đầy đủ:

1. Chỉ chọn các trường mà caller thật sự cần.
2. Map thành giá trị độc lập khi tài nguyên còn sống.
3. Copy collection/map/mảng và các phần tử mutable khi có yêu cầu chia sẻ an toàn.
4. Đóng resource trước hoặc trong quá trình hoàn tất boundary, truyền ra ngoài chỉ dữ liệu kết quả.
5. Kiểm soát kích thước kết quả để tránh OOM hoặc tạo backlog lớn trong executor.

## 13. Kết quả lớn: đừng đổi lỗi resource-closed lấy OutOfMemoryError

Một `List.copyOf` chứa vài trăm item là chuyện khác với truy vấn vài triệu row hoặc đọc tệp nhiều GB. Nếu materialize vô hạn, một tác vụ có thể chiếm heap, gây GC dài và làm giảm throughput của toàn bộ `ThreadPoolTaskExecutor`.

Các chiến lược khi dữ liệu lớn:

| Cần làm gì? | Thiết kế phù hợp | Điều kiện cần |
| --- | --- | --- |
| Trả vài trăm record | DTO/record với danh sách giới hạn | Có pagination/limit rõ |
| Trả báo cáo lớn nhưng có thể download | Worker ghi kết quả ra tệp/object storage, Future trả ID/URI | Có lifecycle/TTL/version/ACL; không trả stream đã đóng |
| Gửi từng phần qua HTTP | Streaming response mở resource trong callback | Timeout, client disconnect, executor và backpressure |
| Xử lý hàng triệu row trong batch | Paging/chunking trong scope transaction ngắn; ghi output theo phần | Checkpoint, retry/idempotency, memory bound |
| Đọc file theo cursor | Một đối tượng quản lý lifetime và quyền đóng cursor có API rõ | Caller nhận trách nhiệm đóng; không nhầm với DTO đã hoàn tất |

Với kết quả lưu bền vững, Future có thể trả `ExportReady(fileId, size, checksum)` thay vì `InputStream`. Khi caller cần đọc file, service mở một stream **mới** dựa trên locator. Độc lập resource không nhất thiết đồng nghĩa dữ liệu luôn tồn tại: file có thể bị xóa hoặc thay phiên bản; do đó cần xử lý not-found, TTL, object version, kiểm tra quyền truy cập và retry.

## 14. Lỗi và hủy tác vụ ảnh hưởng ra sao tới đóng resource?

Một mẫu resource ownership đúng phải làm việc cả khi **task thất bại**, **timeout** hoặc **cancel**:

- **Success:** tạo đủ DTO, đóng resource, complete Future với giá trị.
- **Exception:** `try-with-resources` đóng resource; Future hoàn tất exceptional. Caller lấy nguyên nhân qua `get()`/`ExecutionException.getCause()` hoặc `CompletableFuture.handle/exceptionally`.
- **Caller `get(timeout)` hết thời hạn:** đây chỉ là timeout của **lượt chờ**, task vẫn có thể chạy và giữ resource. Không tự giả định tài nguyên đã đóng.
- **`future.cancel(true)`:** Future thử yêu cầu ngắt worker nếu implementation hỗ trợ; không bảo đảm một JDBC call hoặc thao tác I/O lập tức dừng. Resource nên được đóng ở `finally` khi code thực sự thoát scope.
- **`cancel(false)`:** Future có thể chuyển trạng thái hủy nhưng task đang chạy vẫn có thể tiếp tục; không xem `isDone()` là bằng chứng mọi resource đã được giải phóng.
- **Task bị từ chối ngay khi submit:** resource **không nên được mở trước rồi bỏ mặc** nếu việc gửi thất bại. Tốt nhất mở resource bên trong task sau khi được executor nhận.

Trong JDK, `Future.get()` tạo quan hệ đồng bộ kết quả; còn `CompletableFuture.cancel(...)` theo hợp đồng implementation không sử dụng interruption để điều khiển công việc. Vì vậy đừng đặt đóng tài nguyên phụ thuộc duy nhất vào `isCancelled()`: phải dựa vào `try-with-resources`/`finally` và thiết kế dừng hợp tác.

Lưu ý chi tiết: `close()` cũng có thể ném exception. Nếu task đã tạo DTO nhưng đóng resource thất bại trên đường thoát `try-with-resources`, Future có thể **hoàn tất bằng lỗi** thay vì trả DTO, tùy cách xử lý exception. Tránh nuốt lỗi đóng resource nếu nó có ý nghĩa với tính toàn vẹn công việc.

## 15. Cách kiểm tra kết quả thực sự độc lập

Một kiểm thử tốt cần **đọc dữ liệu sau khi scope tài nguyên đã đóng** chứ không chỉ assert khi transaction đang mở.

Kịch bản gợi ý cho một service `loadAsync(id)`:

1. Tạo dữ liệu thử có association LAZY và collection cần đọc, chạy task qua executor thực (không chỉ gọi target method trực tiếp).
2. `Future.get()` đợi task đã hoàn tất. Nếu dùng Spring JPA, kiểm tra service đã rời method transactional trước lúc đánh giá kết quả.
3. Ở caller **không có persistence context phục vụ việc đọc**, truy cập mọi trường của DTO, duyệt collection, serialize JSON. Không được cần thêm query hoặc mở session mới ngầm.
4. Kiểm tra danh sách, map, array không bị thay đổi khi object gốc hoặc collection gốc bị sửa sau đó.
5. Với JDBC/file, dùng test fake có cờ `closed` hoặc thực sự đóng resource rồi xác nhận caller vẫn đọc được DTO.
6. Thử nhánh exception/cancellation/timeout; kiểm tra `close` chạy đúng và không rò rỉ connection/file descriptor.
7. Thử kích thước gần giới hạn, concurrency nhiều task, heap và thời gian queue để phát hiện phản ứng xấu khi materialize số lượng lớn.

Nếu ứng dụng có Open Session in View, hãy cân nhắc tắt nó trong bài kiểm tra boundary hoặc chủ động xác nhận truy vấn/serialization không xảy ra sau khi service đã tạo DTO; nếu không, session mở ở request có thể che một bug lazy-loading. Đừng khẳng định chỉ cần test `@Transactional` trên test method là đủ: transaction của test có thể làm dữ liệu trông hợp lệ trong khi production đã đóng scope.

## 16. Những lỗi thiết kế thường gặp

1. **Trả `ResultSet` ra khỏi `try-with-resources`.** Statement/connection đã đóng, ResultSet không còn hợp đồng sử dụng hợp lệ.
2. **Trả JPA entity có LAZY association qua Future.** Caller có thể truy cập khi session/transaction đã kết thúc.
3. **Tạo DTO nhưng field vẫn là entity hoặc PersistentCollection.** “Đổi tên kiểu ngoài cùng” không tạo snapshot độc lập.
4. **Trả `Stream<T>` rồi mới định duyệt ở caller.** Stream có thể cần connection/file chưa được duy trì và đóng đúng cách.
5. **Tưởng Future giữ transaction sống tới lúc `get()`.** Future chỉ giữ handle kết quả/trạng thái; không quản lý transaction lifecycle.
6. **Dùng `@Transactional` ở HTTP caller và cho rằng worker dùng chung transaction.** Imperative transaction mặc định gắn với thread; phải tạo boundary đúng trong worker.
7. **Tự gọi `this.readTransactional()` bên trong cùng bean.** Trong proxy mode mặc định có thể bỏ qua transactional advice; tách bean và gọi qua proxy.
8. **Chỉ serialize JPA entity sau khi Future kết thúc.** Serializer có thể đụng getter LAZY trong lúc session đã đóng.
9. **Mở `InputStream` trong caller rồi đóng trước khi worker/callback dùng.** Đưa thao tác mở/đóng về scope chủ sở hữu thực sự.
10. **Tin rằng `List.copyOf` deep-copy mọi phần tử.** Nó không copy nội dung mutable bên trong.
11. **Đọc mọi bản ghi vào RAM để tránh lazy.** Có thể gây OOM; cần limit, pagination, chunking hoặc streaming có kiểm soát.
12. **Đọc request body hoặc `MultipartFile` trên worker sau khi request scope kết thúc.** Không giả định tài nguyên do servlet/container quản lý còn mở.
13. **Đánh đồng `cancel(true)` với đóng connection ngay lập tức.** Hủy có tính hợp tác, cần `finally` và chính sách timeout downstream.
14. **Trả file path rồi coi file không thể thay đổi.** Locator độc lập với stream, nhưng vẫn cần quản lý quyền, version và thời gian tồn tại.
15. **Không kiểm thử sau khi tài nguyên đã đóng.** Test chỉ chạy trong transaction thường bỏ sót lỗi xuất hiện ở caller khác thread.

## 17. Tự kiểm tra kiến thức

**Câu 1.** Task `@Async` trả `Future<ReportEntity>`; caller `get()` thành công nhưng `entity.getItems()` sau đó ném lỗi. Vì sao?  
**Đáp án:** Future đã hoàn tất nhưng association LAZY vẫn phụ thuộc persistence context/session không còn dùng được. Cần map dữ liệu cần thiết thành DTO trong transactional scope của worker.

**Câu 2.** Nếu `ResultSet` được trả từ method trong `try(Connection c=...)`, caller có nên tiếp tục `rs.next()`?  
**Đáp án:** Không. `ResultSet` có vòng đời phụ thuộc Statement/Connection; nên đọc hết hoặc chia trang trong scope rồi trả giá trị độc lập.

**Câu 3.** `Future.get()` có kéo dài vòng đời của `InputStream` mà task đã đóng không?  
**Đáp án:** Không. Nó chỉ đợi/nhận trạng thái kết quả và tạo quan hệ đồng bộ bộ nhớ.

**Câu 4.** `record ReportDto(List<MutableItem> items)` với `List.copyOf(items)` có bất biến sâu không?  
**Đáp án:** Không. Danh sách không thể thay đổi cấu trúc, nhưng từng `MutableItem` vẫn có thể bị sửa nếu chia sẻ tham chiếu.

**Câu 5.** File 4 GB cần được phục vụ qua HTTP. Nên trả một `byte[]` 4 GB từ Future không?  
**Đáp án:** Thường không. Ưu tiên trả locator tới nguồn lưu bền vững hoặc streaming với ownership mở/đóng và giới hạn tài nguyên rõ ràng.

**Câu 6.** Trong Spring MVC streaming, nên mở `InputStream` trước khi controller trả về hay trong callback `StreamingResponseBody`?  
**Đáp án:** Thường mở trong callback thực sự tiêu thụ dữ liệu, rồi đóng khi callback kết thúc; không giả định stream mở trước đó vẫn sống.

**Câu 7.** Task bị `get(timeout)` timeout. Có thể khẳng định JDBC connection đã được đóng không?  
**Đáp án:** Không. Worker có thể còn đang chạy; phải có deadline, chính sách hủy hợp tác và `try-with-resources`/`finally` để giải phóng đúng thời điểm.

**Kết luận:** hãy để tác vụ bất đồng bộ **sở hữu và đóng** các resource mà nó mở, rồi trả về **một giá trị đã hoàn tất, độc lập và an toàn để dùng ở thread khác**. Nếu dữ liệu quá lớn để materialize, hãy chuyển sang thiết kế có **ownership rõ ràng** cho streaming hoặc tệp lưu bền vững; đừng chuyển ngầm một cursor/stream/session đã hết hạn qua `Future`.

### Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling (@Async, Future)](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — Declarative Transaction Management, thread-bound transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html)
- [Spring Data JPA — DTO and projection queries](https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html)
- [Spring Framework — StreamingResponseBody](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/StreamingResponseBody.html)
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 17 — AutoCloseable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html)
- [Java SE 17 — BufferedReader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-results-008">Không gọi get() hoặc join() ngay lập tức nếu mục tiêu là chồng lấp công việc</a>

<details>
<summary>Xem chi tiết</summary>

# Không gọi get() hoặc join() ngay lập tức nếu mục tiêu là chồng lấp công việc

## 1. Vấn đề: có Future nhưng vẫn chạy tuần tự

Trong Spring Framework, phương thức được đánh dấu `@Async` có thể chạy trên `TaskExecutor` và trả về `Future<T>` hoặc `CompletableFuture<T>`. Tuy nhiên, **bất đồng bộ ở phía worker không đồng nghĩa caller đang tận dụng được tính đồng thời**. Nếu sau mỗi lần gọi bạn lập tức chờ kết quả bằng `get()` hoặc `join()`, caller sẽ dừng tại lần chờ đó và **chưa kịp gửi công việc độc lập tiếp theo**.

Ví dụ tưởng là song song:

~~~java
String inventory = inventoryService.loadAsync().join();
String price = pricingService.loadAsync().join();
return inventory + " / " + price;
~~~

Đây là **đoạn code minh họa**, giả định hai service bean thực sự trả `CompletableFuture<String>` từ các method `@Async` hợp lệ. Lệnh đầu tiên gửi yêu cầu lấy tồn kho rồi **đợi nó hoàn thành**; chỉ sau đó mới gọi tới giá. Hai hoạt động độc lập không được khởi động cùng lúc.

Thay bằng:

~~~java
CompletableFuture<String> inventory = inventoryService.loadAsync();
CompletableFuture<String> price = pricingService.loadAsync();

return inventory.thenCombine(price, (stock, amount) ->
        stock + " / " + amount);
~~~

Đoạn thứ hai giả định phương thức chứa code trả `CompletableFuture<String>`. Nó **gửi cả hai công việc trước**, rồi tạo future của kết quả kết hợp. Caller không cần chặn tại mỗi lần gửi. Trong thực tế, hai tác vụ chỉ có thể chạy chồng lấp khi executor, tài nguyên, khóa và downstream cho phép; `CompletableFuture` không tự bảo đảm nhiều worker đang chạy đồng thời.

**Quy tắc cốt lõi:** nếu các công việc độc lập, hãy **khởi động tất cả công việc cần chạy đồng thời trước**, sau đó mới **phối hợp kết quả hoặc chờ tại ranh giới thật sự cần kết quả**. Không bỏ hoàn toàn việc chờ; chỉ đặt điểm chờ đúng vị trí.

## 2. Phân biệt ba thời điểm: submit, execution và await

~~~text
Thời gian ------------------------------------------------------------>

Cách dễ vô tình tuần tự:
Caller:  submit A |====== get(A) ======| submit B |==== join(B) ====|
Worker:      [------- A -------]                  [------ B ------]

Cách cho phép chồng lấp:
Caller:  submit A -> submit B -> làm việc C -> phối hợp -> | chờ nếu cần |
Worker 1:    [------- A --------]
Worker 2:         [---------- B ---------]
Caller C:              [-- C --]
~~~

Sơ đồ trên chỉ minh họa vị trí chặn; độ dài thực tế tùy thời gian task, thread availability và scheduling. Hai thao tác submit liên tiếp **không bắt buộc** dẫn tới hai thread đang chạy cùng lúc. Chúng có thể nằm trong queue nếu pool không đủ năng lực.

### 2.1. Khi nào mỗi hành động xảy ra?

| Hành động | Điều gì xảy ra? | Caller có bị chặn chờ kết quả không? |
| --- | --- | --- |
| `worker.workAsync()` qua Spring proxy | Interceptor gửi phần thực thi tới executor rồi trả một handle kết quả | Thông thường **không chờ** thân method hoàn thành, nhưng submission vẫn có thể bị ảnh hưởng bởi saturation/rejection |
| `CompletableFuture.supplyAsync(task, executor)` | Gửi task tới executor được chỉ định | Không chờ task hoàn thành khi submit thành công |
| `future.get()` | Đợi hoàn thành, trả giá trị hoặc ném exception tương ứng | **Có** nếu chưa xong |
| `future.join()` | Đợi hoàn thành, trả giá trị hoặc ném unchecked completion exception | **Có** nếu chưa xong |
| `future.thenApply(fn)` | Đăng ký bước biến đổi sau khi future trước xong | Không bắt caller chờ, nhưng callback có thể chạy ngay trên caller khi future đã hoàn thành |
| `future.thenCompose(fn)` | Ghép một bước tiếp theo trả future khác | Không bắt caller đợi từng bước; có **phụ thuộc dữ liệu** giữa hai bước |
| `a.thenCombine(b, fn)` | Tạo future kết hợp khi **cả a và b** thành công | Không buộc caller chờ ngay |
| `CompletableFuture.allOf(...)` | Tạo future hoàn thành khi mọi future đầu vào hoàn thành | Không chặn tại lúc đăng ký |
| `get(timeout, unit)` | Chặn nhưng có giới hạn thời gian chờ | Có; có thể ném `TimeoutException` |

**Điều quan trọng:** `get` và `join` không phải các API sai. Chúng phù hợp khi caller **thực sự phải đồng bộ** ở cuối xử lý, trong test/CLI hoặc tại ranh giới trả kết quả. Sai lầm nằm ở việc **chờ quá sớm khi vẫn còn việc độc lập chưa được gửi**.

## 3. Vì sao chờ ngay lập tức làm mất cơ hội overlap?

Giả sử có hai cuộc gọi I/O độc lập: A thường mất khoảng 400 ms, B thường mất khoảng 600 ms; thời gian chỉ dùng để minh họa, không phải cam kết.

Cách tuần tự:

~~~java
Result a = service.fetchAsync("A").join(); // đợi A
Result b = service.fetchAsync("B").join(); // bây giờ mới gửi B
consume(a, b);
~~~

Tổng thời gian chờ có thể gần **400 + 600 ms**, chưa kể overhead. Dù `fetchAsync` thật sự chạy trên worker, caller không cho B cơ hội khởi động trong lúc A chạy.

Cách tốt hơn:

~~~java
CompletableFuture<Result> aFuture = service.fetchAsync("A");
CompletableFuture<Result> bFuture = service.fetchAsync("B");

// Caller có thể tiếp tục xử lý việc khác không phụ thuộc kết quả.
prepareResponseMetadata();

// Chỉ khi cần cả hai mới đồng bộ:
Result a = aFuture.join();
Result b = bFuture.join();
consume(a, b);
~~~

Đoạn này là ví dụ khái niệm, cần `Result`, `service`, `prepareResponseMetadata` và `consume` trong ứng dụng. Nếu executor có đủ khả năng và hai tác vụ không khóa lẫn nhau, thời gian hoàn tất có thể tiến gần `max(400, 600)` thay vì tổng, nhưng **không được coi đây là bảo đảm hiệu năng**.

Nếu không muốn caller block ở cuối, hãy để hàm orchestration **trả `CompletableFuture`**:

~~~java
public CompletableFuture<CombinedResult> getCombined() {
    var a = service.fetchAsync("A");
    var b = service.fetchAsync("B");
    return a.thenCombine(b, CombinedResult::new);
}
~~~

Đây là một method minh họa, cần các kiểu `CombinedResult`, `service`, cấu hình Spring và import. Khi người dùng downstream cũng chấp nhận Future, ta có thể **duy trì pipeline bất đồng bộ** qua nhiều lớp. Nếu một tầng cuối bắt buộc trả giá trị đồng bộ, chỉ chờ ở **tầng đó**, với timeout và xử lý lỗi.

## 4. get() và join(): giống nhau ở việc chặn, khác nhau ở cách báo lỗi

### 4.1. Future.get()

Với `java.util.concurrent.Future<T>`:

~~~java
T result = future.get();
T resultWithTimeout = future.get(3, TimeUnit.SECONDS);
~~~

Hai dòng là những cách dùng **thay thế**, không đặt cùng phạm vi vì trùng tên biến. `get()` có thể chặn không giới hạn; `get(timeout, unit)` giới hạn thời gian chờ. Các exception checked thường gặp gồm:

- `InterruptedException`: thread đang chờ bị interrupt; nếu bắt để làm cleanup, thông thường phục hồi interrupt flag phù hợp.
- `ExecutionException`: task hoàn thành lỗi, có thể kiểm tra `getCause()`.
- `TimeoutException`: thời gian chờ đã vượt giới hạn.
- `CancellationException`: Future đã bị hủy (unchecked).

`get(timeout)` ném `TimeoutException` **không đồng nghĩa task đã dừng**; công việc có thể vẫn chạy và tiếp tục tác động lên hệ thống. Cần xử lý timeout của caller, cancellation, trạng thái job và downstream theo nghiệp vụ.

### 4.2. CompletableFuture.join()

`CompletableFuture<T>.join()` cũng chặn nếu future chưa hoàn tất, nhưng không buộc caller khai báo/bắt checked `InterruptedException` hay `ExecutionException`:

~~~java
String value = future.join();
~~~

Nếu Future thất bại, `join()` thường ném `CompletionException` (unchecked) bao bọc nguyên nhân; nếu bị hủy có thể ném `CancellationException`. `join()` **không có tham số timeout trực tiếp**. Với `CompletableFuture` cần timeout, có thể dùng `get(timeout, unit)`, hoặc xem xét `orTimeout`/`completeOnTimeout` theo hợp đồng của API.

**Đừng kết luận `join()` là “non-blocking” chỉ vì không khai báo checked exception.** `get()` và `join()` đều có thể chặn luồng tại điểm gọi.

### 4.3. Nếu future đã hoàn thành thì sao?

Khi future đã kết thúc, `get()` và `join()` có thể trả ngay hoặc ném lỗi ngay; chúng không nhất thiết tạo độ trễ. Lời khuyên "đừng chờ ngay" chủ yếu nhằm tránh việc caller bị giữ khi **chưa khởi động đủ các tác vụ có thể độc lập** hoặc đang ở thread không nên chặn.

## 5. Ba mẫu điều phối đúng

### Mẫu A — gửi trước, đợi sau (fork-then-join)

~~~java
CompletableFuture<Customer> customer = customerClient.getAsync(id);
CompletableFuture<Orders> orders = orderClient.listAsync(id);

// Công việc không phụ thuộc customer/orders.
AuditMetadata metadata = buildMetadata();

Customer c = customer.join();
Orders o = orders.join();
return assemble(c, o, metadata);
~~~

Đoạn minh họa này **chủ động có điểm join** ở cuối, sau khi đã khởi động cả hai công việc. Nó phù hợp khi hàm phải trả giá trị đồng bộ và thread caller **được phép chờ**. Nếu caller là request thread, cần xem timeout, tải và cách hệ thống web xử lý bất đồng bộ.

### Mẫu B — kết hợp kết quả mà không chặn caller

~~~java
public CompletableFuture<Overview> getOverview(String id) {
    var customer = customerClient.getAsync(id);
    var orders = orderClient.listAsync(id);
    return customer.thenCombine(orders, Overview::new);
}
~~~

Mảnh code minh họa cần các kiểu và bean đã khai báo. `thenCombine` chạy khi **cả hai** hoàn thành thành công, không cần caller gọi `join`. Nó là cách diễn tả “khi đủ hai kết quả, hãy làm bước tiếp”.

**Lưu ý về thread thực thi callback:** `thenCombine` không có hậu tố `Async` thường thực thi callback ở thread hoàn thành một stage hoặc ở thread đang đăng ký nếu các stage đã xong. Nếu bước ghép kết quả làm CPU nặng hoặc I/O blocking, cân nhắc `thenCombineAsync(fn, chosenExecutor)`, đồng thời tránh gây bão hòa chính pool cần để chạy các task còn lại.

### Mẫu C — nhiều future với allOf()

~~~java
List<CompletableFuture<String>> futures = List.of(
        service.runAsync("A"),
        service.runAsync("B"),
        service.runAsync("C"));

CompletableFuture<List<String>> results =
        CompletableFuture.allOf(
                futures.toArray(new CompletableFuture<?>[0]))
                .thenApply(ignored ->
                        futures.stream()
                                .map(CompletableFuture::join)
                                .toList());
~~~

Mảnh minh họa cần `List`, `service` và import phù hợp. Ở đây:

1. Ba method được gọi trước, tạo ba Future.
2. `allOf` tạo Future kết thúc khi **tất cả** các Future đầu vào kết thúc.
3. `thenApply` chỉ chạy bình thường sau khi `allOf` hoàn thành thành công; lúc đó các `join()` để lấy giá trị **không còn phải chờ task**.
4. Nếu bất kỳ Future nào hoàn thành lỗi, `allOf` thường hoàn thành lỗi và callback `thenApply` không chạy. Phải xử lý lỗi ở pipeline nếu cần partial results.

`allOf()` **không tự tạo `List<T>` kết quả**; nó trả về `CompletableFuture<Void>`. Bước lấy kết quả cần thực hiện riêng. Đừng gắn `join` trong vòng lặp **đang tạo danh sách Future** theo kiểu “submit một việc rồi đợi ngay”, vì như vậy lại biến quá trình thành tuần tự.

## 6. Phân biệt công việc độc lập và công việc phụ thuộc

Không phải mọi `join` sớm đều vô lý. Nếu B **cần kết quả A làm đầu vào**, không thể khởi động B thực sự trước khi có A:

~~~java
// A hoàn tất rồi mới biết khóa để lấy B.
CompletableFuture<Account> account = accountService.findAsync(userId);

CompletableFuture<Profile> profile =
        account.thenCompose(a -> profileService.loadAsync(a.profileId()));
~~~

Mảnh code minh họa này dùng `thenCompose` vì callback trả về một Future khác. `thenApply` trong trường hợp này sẽ tạo `CompletableFuture<CompletableFuture<Profile>>` thay vì tự flatten kết quả. **`thenCompose` không tạo parallelism giữa A và B**, vì B thực sự phụ thuộc vào A; nhưng nó giúp orchestration không block thread trong lúc chờ.

Trong một workflow có ba tác vụ A, B, C:

- A và B **độc lập**: có thể submit cả hai ngay.
- C **phụ thuộc kết quả A+B**: dùng `thenCombine` hoặc `allOf` để kích hoạt C sau khi có đủ dữ liệu.
- D **không liên quan**: có thể được submit trước hoặc trong lúc A/B đang chạy.

Tư duy này xây một **đồ thị phụ thuộc (dependency graph)** thay vì chờ từng dòng code một cách máy móc.

~~~text
A ─────────┐
           ├──> C (cần A và B) ───> kết quả
B ─────────┘
D ─────────────────────────────────> công việc độc lập
~~~

Chỉ những cạnh **phụ thuộc thật sự** mới buộc phải chờ; phần độc lập mới có cơ hội chồng lấp.

## 7. Demo độc lập Spring: quan sát cả hai task bắt đầu trước khi caller chờ kết quả

Ví dụ này được thiết kế để **kiểm tra hành vi**, không phán đoán bằng tốc độ. Dùng **Java 17+, Spring Framework 6.1.14 và Gradle 8.5+**, không cần Spring Boot, HTTP server hay database.

Có hai method call `@Async` chạy trên một `ThreadPoolTaskExecutor` có **hai worker**. Cả hai worker báo tín hiệu qua `CountDownLatch` khi đã bắt đầu, rồi cùng chờ một latch khác. Caller khởi động **cả hai** trước, thực hiện một phép tính độc lập, quan sát hai worker đã vào method và mới cho chúng hoàn tất.

### 7.1. Cấu trúc project

~~~text
overlap-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/OverlapDemo.java
~~~

**settings.gradle**

~~~groovy
rootProject.name = 'overlap-demo'
~~~

**build.gradle**

~~~groovy
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
    implementation 'org.springframework:spring-context:6.1.14'
}

application {
    mainClass = 'demo.OverlapDemo'
}
~~~

**src/main/java/demo/OverlapDemo.java**

~~~java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public final class OverlapDemo {

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            JobWorker worker = context.getBean(JobWorker.class);

            CountDownLatch bothStarted = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);

            // Gửi cả hai task trước khi lấy bất kỳ kết quả nào.
            CompletableFuture<String> a =
                    worker.workAsync("A", bothStarted, release);
            CompletableFuture<String> b =
                    worker.workAsync("B", bothStarted, release);

            // Tạo future phối hợp, không block caller ở đây.
            CompletableFuture<String> combined =
                    a.thenCombine(b, (left, right) ->
                            left + "+" + right);

            try {
                // Caller tiếp tục xử lý phần độc lập.
                int callerComputation = 20 + 22;
                System.out.println("caller work: " +
                        (callerComputation == 42));

                // Chờ TÍN HIỆU test: cả hai worker đã vào method.
                if (!bothStarted.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Two tasks did not start in time");
                }
                System.out.println("both tasks started: " +
                        (bothStarted.getCount() == 0));

                // Workers chưa thể hoàn tất vì đang chờ release.
                System.out.println("both futures pending: " +
                        (!a.isDone() && !b.isDone()));

                release.countDown();

                // Điểm đồng bộ cuối cùng: giờ mới cần kết quả.
                System.out.println("combined result: " +
                        combined.get(5, TimeUnit.SECONDS));
                System.out.println("both futures completed: " +
                        (a.isDone() && b.isDone()));
            } finally {
                // Tránh kẹt worker nếu test/quan sát thất bại.
                release.countDown();
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class Config {

        @Bean("workerExecutor")
        public ThreadPoolTaskExecutor workerExecutor() {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(2);
            pool.setMaxPoolSize(2);
            pool.setQueueCapacity(4);
            pool.setThreadNamePrefix("overlap-worker-");
            return pool;
        }

        @Bean
        public JobWorker jobWorker() {
            return new JobWorker();
        }
    }

    public static class JobWorker {
        @Async("workerExecutor")
        public CompletableFuture<String> workAsync(
                String name,
                CountDownLatch started,
                CountDownLatch release) {

            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    return CompletableFuture.failedFuture(
                            new IllegalStateException(
                                    "Release latch timed out"));
                }
                return CompletableFuture.completedFuture(
                        name + "-done");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return CompletableFuture.failedFuture(ex);
            }
        }
    }
}
~~~

**Chạy** trong thư mục `overlap-demo`:

~~~bash
gradle run
~~~

Nếu có Gradle Wrapper, dùng `./gradlew run` (Linux/macOS) hoặc `gradlew.bat run` (Windows).

**Các dòng stdout do chương trình tự in**, khi hai worker khởi động và hoàn tất bình thường trong ngưỡng thời gian năm giây:

~~~text
caller work: true
both tasks started: true
both futures pending: true
combined result: A-done+B-done
both futures completed: true
~~~

Có thể xuất hiện log Spring hoặc Gradle ngoài năm dòng này. Demo **không khẳng định latency** hoặc hậu tố tên thread cụ thể. Nếu môi trường thiếu tài nguyên và worker không bắt đầu trong timeout, chương trình ném exception thay vì xem đó là kết quả thành công.

### 7.2. Vì sao demo xác minh được overlap?

1. `Config` bật `@EnableAsync`; `JobWorker` được container tạo và method `workAsync` gọi từ code bên ngoài bean qua Spring proxy.
2. `workerExecutor` có hai worker và nhận hai task A/B.
3. Mỗi worker giảm `bothStarted` rồi chờ `release`. Việc `bothStarted` về 0 **chỉ có thể xảy ra khi cả hai method đã bắt đầu chạy**, không chỉ nằm trong queue.
4. Khi caller đọc `both futures pending`, chưa worker nào được thả, nên hai CompletableFuture vẫn chưa kết thúc.
5. Caller thả latch, hai worker hoàn thành. Future `combined` tự hoàn thành khi cả A và B có kết quả; caller chỉ gọi `get(timeout)` tại ranh giới cần in kết quả.
6. Nếu đổi code thành `worker.workAsync("A", ...).get()` **trước khi** submit B, caller sẽ chờ A trong lúc A đợi `release`; không bao giờ đến được bước submit B theo trình tự như ví dụ. Trường hợp thử nghiệm này minh họa rõ việc chờ quá sớm đã phá cơ hội overlap.

`CountDownLatch` ở đây phục vụ **kiểm thử có kiểm soát**, không phải yêu cầu phải có latch trong mọi chương trình `@Async`. Trong production, công việc thực thường chờ I/O, xử lý CPU hoặc phối hợp với hệ thống ngoài; `CompletableFuture`/Future thể hiện trạng thái hoàn thành.

## 8. Áp dụng vào service và controller Spring

### 8.1. Tầng service trả future thay vì chờ bên trong

Không nên viết một method đồng bộ chứa hai `join` sớm rồi trả giá trị:

~~~java
public DashboardData loadDashboard() {
    var account = accountService.getAsync().join();
    var orders = orderService.listAsync().join();
    return new DashboardData(account, orders);
}
~~~

Thay vào đó, nếu API có thể bất đồng bộ:

~~~java
public CompletableFuture<DashboardData> loadDashboardAsync() {
    var account = accountService.getAsync();
    var orders = orderService.listAsync();
    return account.thenCombine(orders, DashboardData::new);
}
~~~

Hai method trên là **mảnh code minh họa**, cần các dependency/service/kiểu dữ liệu tương ứng. Tầng gọi quyết định khi nào cần await. Khi áp dụng `@Async` cho method của service, phải nhớ **self-invocation** trong proxy mode mặc định không kích hoạt interception; nên inject một Spring-managed worker bean và gọi qua proxy.

### 8.2. Trong Spring MVC

Controller có thể trả `CompletableFuture` hoặc kiểu kết quả bất đồng bộ mà MVC hỗ trợ, để Spring quản lý ranh giới async request. Cần chọn cơ chế tương thích với kiểu trả về, timeout và cấu hình MVC.

Đoạn minh họa (cần spring-webmvc, import và các bean phù hợp):

~~~java
@RestController
class DashboardController {
    private final DashboardService service;

    DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public CompletableFuture<DashboardData> dashboard() {
        return service.loadDashboardAsync();
    }
}
~~~

Điểm chính là không lấy `service.loadDashboardAsync().join()` ngay trong controller nếu mục tiêu là **giải phóng request thread**. Tuy nhiên, kiểu trả về Future **không tạo ra khả năng thực thi vô hạn**; vẫn cần timeout, giới hạn số async requests, queue/rejection policy và kết nối downstream.

Một request HTTP cuối cùng vẫn cần kết quả để phản hồi. **Không block tại controller** khác với **không bao giờ phải đợi kết quả**: MVC có thể quản lý việc đợi/dispatch thông qua cơ chế asynchronous request processing của nó. Không dùng Future tùy tiện để che một operation blocking bên trong mà không đánh giá resource.

### 8.3. Trong scheduled job hoặc message consumer

Một tác vụ theo lịch có thể submit các tác vụ độc lập và phối hợp kết quả bằng Future, nhưng cần xem vòng đời callback: nếu scheduler callback chờ `join()` lâu, nó có thể giữ thread scheduler, làm các lần kích hoạt khác bị trì hoãn. Nếu callback submit rồi trả về ngay, các lần lịch tiếp theo có thể **chồng lấp công việc nền**; cần cơ chế deduplication hoặc giới hạn số job đang chạy nếu nghiệp vụ không cho phép.

Với message listener, đừng chỉ submit async rồi **acknowledge message ngay** nếu hệ thống yêu cầu xử lý thành công trước ack. Chỗ nào chờ hoặc gửi ack phụ thuộc delivery contract. `@Async` và Future trong bộ nhớ không bảo đảm khôi phục task sau process crash.

## 9. Xử lý lỗi, timeout và cancellation mà không phá tính bất đồng bộ

### 9.1. Xử lý lỗi trong chuỗi Future

~~~java
CompletableFuture<String> safe = worker.workAsync()
        .exceptionally(error -> {
            logFailure(error);
            return "fallback";
        });
~~~

Đây là mảnh minh họa, cần bean `worker`, method `logFailure` và kiểu trả phù hợp. `exceptionally` tạo một stage dùng giá trị thay thế khi stage trước hoàn thành lỗi; **không tự retry** công việc bị lỗi và cũng không bảo đảm rollback side effect.

Để chỉ quan sát lỗi mà vẫn giữ trạng thái exceptional:

~~~java
CompletableFuture<String> observed = worker.workAsync()
        .whenComplete((value, error) -> {
            if (error != null) {
                logFailure(error);
            }
        });
~~~

Callback trong `whenComplete` không tự biến failure thành success. Với các stage ghép, cần xác định rõ việc một nhánh fail thì bỏ cả tác vụ, dùng fallback hay cho kết quả một phần.

### 9.2. Dùng timeout đúng mục đích

~~~java
CompletableFuture<String> task = worker.workAsync();
String value = task.get(3, TimeUnit.SECONDS);
~~~

Ví dụ này **chủ động chờ** tối đa ba giây tại ranh giới cần kết quả. `get(timeout)` timeout **không tự dừng task**. Nếu caller phải trả HTTP trong ngưỡng thời gian, hãy quyết định xử lý task còn chạy, cancellation, retry và idempotency.

Với `CompletableFuture`:

~~~java
CompletableFuture<String> timed = worker.workAsync()
        .orTimeout(3, TimeUnit.SECONDS);
~~~

`orTimeout` có thể **hoàn thành exceptional chính CompletableFuture nhận được** sau timeout; nó không bắt buộc hủy computation thực đang chạy dưới executor. `completeOnTimeout` thay bằng một giá trị mặc định nếu chưa xong. Không dùng timeout như biện pháp thay cho admission control hoặc task cancellation.

### 9.3. Đừng bỏ quên các Future

Tránh tình huống “fire-and-forget vô chủ”:

~~~java
worker.sendAsync(data); // không lưu handle, không xử lý lỗi/ack
~~~

Mảnh minh họa trên **không mặc định sai**, nhưng với tác vụ có giá trị nghiệp vụ, caller nên có cách ghi trạng thái, theo dõi failure hoặc retry. Với `@Async void`, exception từ worker không thể tự ném đồng bộ lại caller; có thể cấu hình `AsyncUncaughtExceptionHandler`, log và metrics. Nếu thao tác phải survive restart, dùng job store, outbox hoặc broker bền vững chứ không chỉ lưu Future trong heap.

## 10. Overlap phụ thuộc tài nguyên, không chỉ phụ thuộc cách gọi

Không gọi `join` ngay **chỉ tạo cơ hội** chạy đồng thời. Còn có các giới hạn:

| Giới hạn | Ảnh hưởng |
| --- | --- |
| Executor chỉ có một worker | A và B thường được chạy lần lượt dù submit cùng lúc |
| Pool queue đầy | Task bị từ chối; handler như DiscardPolicy có thể bỏ task âm thầm |
| Pool dùng CallerRunsPolicy | Khi bão hòa, caller có thể chạy task ngay, làm lời gọi submit bị chặn |
| Hai task tranh cùng một khóa | Chỉ một task vào critical section một lúc |
| DataSource pool có một connection | Hai worker có thể chờ cùng tài nguyên, throughput không tăng |
| Downstream giới hạn rate/connections | Gửi song song có thể tăng lỗi 429/503 hoặc timeouts |
| Workload CPU-bound trên ít core | Tăng worker quá mức có thể tăng context switching và chậm hơn |
| Task gắn transaction/ThreadLocal | Chuyển sang worker có thể mất context cần thiết nếu không truyền đúng |

**Cấu hình executor cần có mục đích.** Với `ThreadPoolTaskExecutor`, corePoolSize, maxPoolSize, queueCapacity và rejection handler tương tác với nhau. Một queue lớn có thể giữ nhiều request nhưng tăng độ trễ và bộ nhớ. Một pool quá ít worker sẽ không giúp A/B chạy chồng lấp dù orchestrator đã submit cả hai.

Thêm thread không biến blocking I/O thành non-blocking và không tự làm class singleton an toàn luồng. Những property `spring.task.execution.*` của Spring Boot chỉ áp dụng theo quy tắc auto-configuration tương ứng; ứng dụng Spring Framework thuần trong demo không đọc các property Boot này.

### 10.1. Nguy cơ thread starvation deadlock khi chờ Future bên trong pool

Ví dụ giả định executor chỉ có **một worker** và một queue có chỗ:

~~~java
executor.execute(() -> {
    Future<String> child = executor.submit(() -> "child result");
    String result = child.get(); // worker duy nhất đang chờ child
    use(result);
});
~~~

Đoạn này chỉ nhằm minh họa (cần bao ngoài xử lý checked exception, executor và `use`). Parent task chiếm worker và đợi child. Nhưng child nằm trong queue, cần worker đó để chạy: **thread starvation deadlock**. Có timeout thì parent có thể thoát khỏi việc đợi; nó không làm thiết kế chờ này trở thành tối ưu.

Để giảm nguy cơ, biểu diễn pipeline bằng continuation, không block worker khi đang đợi một task khác chạy trên chính pool hạn chế. Nếu các bước phụ thuộc hoàn toàn theo trình tự, đôi khi **gọi trực tiếp trên một worker** lại đơn giản hơn submit con rồi đợi. Cần tính pool, queue và dependency graph cùng nhau.

## 11. Transaction, context và singleton khi chồng lấp công việc

Trong Spring, `@Transactional` của caller thường gắn với **thread hiện tại**. Khi `@Async` dùng worker khác, transaction context **không tự động được truyền**. Hai query async chạy đồng thời cũng có thể cần hai database connections/transaction độc lập. Một yêu cầu vừa ghi dữ liệu trong transaction chưa commit, rồi submit worker truy vấn ngay, có thể gặp dữ liệu chưa nhìn thấy.

Không giữ một transaction dài trên request thread chỉ để chờ nhiều Future nếu không có lý do rõ ràng. Với thao tác phải xảy ra sau commit, cân nhắc after-commit event hoặc transactional outbox tùy yêu cầu nhất quán và durability.

Các `ThreadLocal` như MDC, SecurityContext, request attributes cũng không tự lan truyền qua thread pool; dùng cơ chế capture/restore hoặc `TaskDecorator` thích hợp khi cần. Đừng nhúng dữ liệu người dùng nhạy cảm vào tên thread để theo dõi tác vụ; nên sử dụng structured log/tracing.

Một `@Service` Spring mặc định singleton có thể bị nhiều worker gọi đồng thời. Tránh đặt request-specific mutable fields trên bean; truyền immutable DTO/record hoặc snapshot và sử dụng cơ chế đồng bộ thích hợp cho shared mutable state. Thay đổi cách phối hợp Future không tự loại bỏ data races.

## 12. Kiểm tra hiệu quả và viết test ổn định

### 12.1. Đo điều đúng

Đừng chỉ so thời gian một lần chạy, vì JVM warmup, cache, scheduling, network và latency downstream làm số đo dao động. Hãy quan sát:

- **Task submit time** và số task đã gửi.
- **Thời điểm worker bắt đầu/kết thúc** để xác nhận thời gian xử lý thực sự chồng lấp.
- **Queue wait time**, active threads, queue depth và rejection.
- **Caller blocked time** ở `get()`, `join()` hoặc điểm chờ khác.
- **Thời gian đáp ứng end-to-end** và chi phí cho hệ thống ngoài.

Hai Future được tạo gần nhau không chắc worker đã chạy đồng thời. Demo ở mục 7 dùng `CountDownLatch` để xác minh **cả hai method đã vào worker trước khi được thả** — bằng chứng mạnh hơn phép đo thời gian ngẫu nhiên.

### 12.2. Test các trường hợp bất thường

1. Một trong hai tác vụ ném lỗi: future kết hợp có thể hoàn thành lỗi, cần assert cause đúng.
2. Một nhánh rất chậm: áp dụng timeout và quyết định nhánh khác sẽ tiếp tục hay bị hủy.
3. Executor chỉ có một worker: không nên assert song song thực tế nếu không có capacity.
4. Executor từ chối task: phải có failure signal hoặc cơ chế thay thế theo contract.
5. Caller bị interrupt khi đang `get()`: xử lý interrupt phù hợp và không đánh đồng với task fail.
6. Một task phụ thuộc vào task khác: dùng `thenCompose`, không cố chạy nhánh phụ thuộc trước dữ liệu đầu vào.
7. Độ tải cao: xác nhận giới hạn concurrency, response timeout và số tác vụ dồn hàng đợi.
8. Có job trạng thái lâu dài: kiểm tra job store, restart/retry và idempotency.

### 12.3. Kiểm tra @Async thực sự chạy qua Spring

Nếu gọi method được `@Async` đánh dấu mà code chạy đồng bộ, xem lại:

- `@EnableAsync` có được bật trong đúng ApplicationContext chưa?
- Worker có được Spring quản lý và caller có inject proxy từ container không?
- Có đang tự gọi `this.asyncMethod()` trong cùng bean không?
- Method có thể được Spring proxy intercept không (thực tế hay dùng public, non-final)?
- `@Async("executorName")` trỏ đến đúng bean executor không?
- Caller có đang `join()` ngay nên **trông** như toàn bộ chuỗi đồng bộ dù method đã async không?

Phải phân biệt **annotation không hoạt động** với **annotation hoạt động nhưng điểm chờ đặt quá sớm**; cách sửa hoàn toàn khác nhau.

## 13. Anti-pattern và cách sửa nhanh

| Anti-pattern | Hệ quả | Thay bằng |
| --- | --- | --- |
| `aAsync().join(); bAsync().join();` khi A/B độc lập | Không khởi động B trước khi xong A | Tạo Future A và B trước, chờ hoặc combine sau |
| `future.get()` ngay trong HTTP controller | Giữ request thread chờ | Trả Future/kiểu async MVC thích hợp, cấu hình timeout |
| `stream.map(item -> async(item).join())` | Có thể submit rồi chờ từng item, thành gần tuần tự | `map(async)` ra danh sách Future, `allOf` sau |
| `CompletableFuture.supplyAsync(task)` không chỉ định pool | Thường dùng ForkJoin commonPool, không lấy pool Spring tự định nghĩa | `supplyAsync(task, explicitExecutor)` hoặc Spring-managed `@Async` |
| `allOf(...)` nhưng quên lấy từng kết quả | Chỉ có CompletableFuture<Void> | `thenApply` lấy kết quả sau khi allOf hoàn tất |
| `join()` trong worker rồi submit child lên cùng single-thread pool | Dễ starvation/deadlock | Dùng continuation hoặc cấu hình lại dependency và pool |
| Tạo hàng nghìn Future rồi allOf một lần không giới hạn | Quá tải pool, DB, network hoặc bộ nhớ | Bounded concurrency, pagination, rate limit |
| Không quan sát Future sau dispatch | Lỗi nền không ai xử lý | Error handler, status, metrics, tracing |
| `get(timeout)` rồi nghĩ task đã dừng | Task vẫn có thể chạy sau timeout | Quy định cancel/timeout ở tầng công việc và downstream |
| Xem CompletableFuture là bảo đảm task sẽ survive restart | Future chỉ sống trong process | Durable queue, outbox hoặc job store |

## 14. Quy trình refactor từ code chờ sớm sang phối hợp đúng

**Bước 1.** Vẽ đồ thị phụ thuộc: task nào độc lập, task nào cần kết quả task trước? Không ép song song các thao tác có thứ tự bắt buộc.

**Bước 2.** Kiểm tra async infrastructure: `@EnableAsync`, worker managed bean, lời gọi qua proxy, executor và queue capacity. Nếu `@Async` không hoạt động, sửa điều đó trước khi tối ưu `join`.

**Bước 3.** Thay code khởi động rồi chờ ngay bằng **khởi động toàn bộ nhánh độc lập trước**:

~~~java
var a = workerA.calculateAsync();
var b = workerB.calculateAsync();
~~~

Đây là đoạn minh họa, giả định cả hai trả `CompletableFuture` và được gọi đúng từ Spring-managed bean.

**Bước 4.** Nếu caller phải trả kết quả đồng bộ, đợi ở ranh giới muộn nhất hợp lý; nếu có thể trả async, **trả pipeline Future**:

~~~java
return a.thenCombine(b, Aggregate::new);
~~~

**Bước 5.** Chọn timeout, cách handle exception, cancellation và retry. Đặc biệt không lờ lỗi trên worker.

**Bước 6.** Đặt giới hạn concurrency theo CPU, connection pool, downstream rate limit và mục tiêu độ trễ; không dùng async để biến quá tải thành hàng đợi vô hạn.

**Bước 7.** Kiểm thử bằng latch hoặc instrumentation để xác nhận worker thực sự chồng lấp và kết quả cuối đúng. Đo thêm request latency, queue wait và error/rejection.

## 15. Câu hỏi tự kiểm tra

1. **Vì sao gọi `get()` ngay sau `@Async` có thể làm hai công việc A/B độc lập chạy tuần tự?** Vì caller chờ A hoàn tất trước khi có cơ hội gửi B.
2. **`join()` có non-blocking hơn `get()` không?** Không. Cả hai có thể block; khác nhau chủ yếu ở cơ chế exception/API.
3. **Đổi từ `get` sang `join` có tự tạo overlap không?** Không; phải thay vị trí chờ và thực sự submit các nhánh độc lập.
4. **Gửi hai Future trước rồi `join` ở cuối có hợp lệ không?** Có, khi caller được phép đồng bộ ở điểm đó và timeout/error được cân nhắc.
5. **Tại sao `thenCombine` phù hợp với hai tác vụ độc lập?** Nó tạo kết quả khi cả hai hoàn tất mà không bắt caller chờ tại lúc đăng ký.
6. **Khi B phụ thuộc dữ liệu do A trả về, API nào phù hợp?** `thenCompose`, vì callback trả Future và sự phụ thuộc là thật.
7. **`allOf` có trả thẳng danh sách giá trị không?** Không; nó trả `CompletableFuture<Void>`, cần bước tập hợp kết quả.
8. **Có hai Future thì chắc chắn dùng hai worker cùng lúc không?** Không. Phụ thuộc executor, queue, khóa và downstream.
9. **Future `get(timeout)` ném TimeoutException thì task đã dừng chưa?** Chưa chắc; timeout chờ không tự hủy computation.
10. **Vì sao chờ Future trong một worker single-thread pool có thể deadlock?** Nếu worker đang đợi một child task cũng cần chính worker đó để chạy.
11. **Trong Spring MVC, vì sao trả `CompletableFuture` thường hữu ích hơn `join` trong controller?** Nó cho phép framework quản lý async request thay vì giữ request thread chờ đồng bộ, khi cơ chế tương thích.
12. **Tách `@Async` sang executor có tự truyền transaction và ThreadLocal không?** Không; cần thiết kế context/transaction riêng.
13. **Làm sao chứng minh overlap thay vì chỉ xem thời gian?** Khống chế đồng bộ bằng latch hoặc đo chính xác interval của hai worker; demo mục 7 xác minh cả hai đã bắt đầu trước khi thả.
14. **Có nên tránh `join` ở mọi nơi không?** Không. Nó cần thiết trong một số ranh giới đồng bộ; điều cần tránh là **chờ quá sớm** và **chờ trong thread nhạy cảm khi có lựa chọn tốt hơn**.

**Ghi nhớ:** `@Async` giúp **gửi công việc**, còn `CompletableFuture` giúp **phối hợp kết quả**. Muốn các công việc độc lập có cơ hội chạy chồng lấp, hãy **gửi chúng trước**, **không gọi `get()`/`join()` ngay sau mỗi lần gửi**, và chỉ đợi ở nơi thực sự cần kết quả. Đồng thời kiểm soát executor, lỗi, timeout và tài nguyên — async không tự bảo đảm hiệu năng hay độ bền công việc.

### Tài liệu chính thức

- [Java 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — Async Requests in Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)
- [Spring Framework 6.1 — @Async API](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/annotation/Async.html)


</details>

- [Quay lại đầu trang](#back-to-top)
