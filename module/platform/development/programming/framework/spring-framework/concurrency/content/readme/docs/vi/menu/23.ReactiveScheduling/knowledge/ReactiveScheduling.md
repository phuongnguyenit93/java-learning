<a id="back-to-top"></a>

# Lập lịch reactive

## Menu
- [Hỗ trợ @Scheduled cho phương thức reactive từ Spring Framework 6.1](#reactive-scheduling-001)
- [Publisher được lấy một lần và được đăng ký nhận nhiều lần theo lịch](#reactive-scheduling-002)
- [Dùng defer để tạo công việc mới ở mỗi lần đăng ký nhận](#reactive-scheduling-003)
- [Giá trị onNext bị bỏ qua trong phương thức lập lịch](#reactive-scheduling-004)
- [Tín hiệu onError được ghi nhận và lịch vẫn có thể tiếp tục](#reactive-scheduling-005)
- [ErrorHandler cho Runnable không xử lý tín hiệu lỗi reactive theo cùng cách](#reactive-scheduling-006)
- [Fixed-delay chờ đăng ký nhận hoàn tất để giữ ngữ nghĩa](#reactive-scheduling-007)
- [Hủy các đăng ký nhận khi bean bị hủy hoặc context đóng](#reactive-scheduling-008)
- [Không coi CompletableFuture là nguồn trì hoãn đăng ký nhận phù hợp trong mọi trường hợp](#reactive-scheduling-009)

## <a id="reactive-scheduling-001">Hỗ trợ @Scheduled cho phương thức reactive từ Spring Framework 6.1</a>

<details>
<summary>Xem chi tiết</summary>

# Hỗ trợ @Scheduled cho phương thức reactive từ Spring Framework 6.1

## 1. Vấn đề cần giải quyết

Trước Spring Framework 6.1, cách quen thuộc để chạy một công việc định kỳ là viết phương thức đồng bộ `@Scheduled` trả `void`. Khi ứng dụng làm việc với Reactor/WebFlux/R2DBC, chúng ta lại thường có pipeline kiểu `Mono<T>` hoặc `Flux<T>`: công việc có thể chỉ thực sự bắt đầu khi **có subscriber**.

Spring Framework **từ phiên bản 6.1** bổ sung hỗ trợ `@Scheduled` trên **phương thức reactive**. Thay vì coi `Publisher` như một giá trị trả về không ai dùng, scheduling infrastructure lấy một `Publisher` từ method và **định kỳ đăng ký subscription** theo `fixedRate`, `fixedDelay` hoặc `cron`.

Ví dụ ý tưởng:

~~~java
// Đoạn mã đặt trong Spring-managed bean; service là dependency ứng dụng.
@Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
public Mono<Void> synchronize() {
    return Mono.defer(() -> service.synchronizeReactive());
}
~~~

Điểm quan trọng không chỉ là cho phép return type `Mono<Void>`. **Cách chạy đã khác:**

1. Spring phát hiện phương thức `@Scheduled` trong bean.
2. Spring gọi method để lấy một `Publisher` **một lần** khi đăng ký lịch.
3. Mỗi lần tới hạn, scheduler chạy một `Runnable` để **subscribe lại** vào `Publisher` đó.
4. Từng subscription tạo các tín hiệu Reactive Streams: `onSubscribe`, có thể có `onNext`, rồi `onComplete` hoặc `onError`.
5. Spring **bỏ qua `onNext`**; ghi WARN và phục hồi khi gặp `onError` để còn lên lịch lần sau.
6. Khi shutdown/hủy bean, Spring hủy lịch và các subscription đang hoạt động theo cơ chế quản lý lifecycle.

Đây là nền tảng của *lập lịch reactive*. Bài học tập trung vào semantics của **Spring 6.1**, điều kiện tương thích Publisher, lỗi lazy/eager, hoàn tất, hủy và một dự án Java 17 chạy được.

**Không đồng nhất:** `@Scheduled` reactive khác `@Async`; khác `Flux.interval`; cũng khác một REST endpoint trả `Mono`. `@Scheduled` là **Spring tự đăng ký lịch**, không có request hoặc caller mong nhận kết quả của mỗi lần kích hoạt.

## 2. Từ @Scheduled đồng bộ sang @Scheduled reactive

### 2.1. Cách đồng bộ truyền thống

~~~java
// Snippet trong @Component, bật @EnableScheduling tại configuration.
@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
public void pollSync() {
    catalogService.refreshBlocking();
}
~~~

Spring gọi `pollSync()` tại các mốc thích hợp. Phương thức trả về sau khi `refreshBlocking()` kết thúc. Với `fixedDelay`, khoảng nghỉ 5 giây được tính từ **thời điểm invocation kết thúc**. Nếu code ném exception, chiến lược `ErrorHandler` của scheduler xử lý; với repeating tasks, Spring thường log và suppress lỗi để lượt sau tiếp tục, tùy cấu hình thực.

### 2.2. Cách reactive từ Spring Framework 6.1

~~~java
// Snippet trong @Component, Reactor có trên classpath.
@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
public Mono<Void> pollReactive() {
    return Mono.defer(() -> catalogService.refreshNonBlocking());
}
~~~

Method `pollReactive()` tạo ra một `Mono` để Spring giữ và subscribe theo lịch. Nếu `catalogService.refreshNonBlocking()` thật sự trả một cold `Mono<Void>` và side effect nằm trong subscription, mỗi tick sẽ chạy một lần mới.

**Hai câu hỏi khác nhau:**

- “Spring gọi **Java method factory** bao nhiêu lần?” — trong cơ chế này, một lần để lấy Publisher.
- “Spring **subscribe Publisher** bao nhiêu lần?” — nhiều lần theo lịch, cho tới khi bị hủy/ứng dụng dừng.

Nếu chỉ đặt tác vụ trong thân method **trước khi return Publisher**, bạn có thể thực thi sai thời điểm: *một lần lúc khởi tạo*, không phải mỗi tick. Nếu dùng một hot/cached Publisher không hỗ trợ nhiều subscription độc lập, từng tick có thể không có công việc mới.

### 2.3. So sánh ngắn

| Thuộc tính | `@Scheduled void` thông thường | `@Scheduled Mono/Flux` từ 6.1 |
| --- | --- | --- |
| Method Java được gọi theo từng tick? | Có, callback được invoke | Không theo cách đó: Spring lấy Publisher một lần rồi subscribe lại |
| Công việc chính chạy lúc nào? | Trong method khi scheduler gọi | Khi Publisher được subscribe, nếu pipeline lazy |
| Giá trị trả về | Không có, hoặc bị ignore nếu return đồng bộ | `onNext` bị ignore/discard |
| Lỗi | Lỗi của Runnable qua ErrorHandler của scheduler | `onError` được log WARN rồi recovered; không qua ErrorHandler |
| `fixedDelay` | Tính từ lúc callback kết thúc | Spring **chờ subscription hoàn tất** để giữ fixed-delay semantics |
| Hủy lúc shutdown | Hủy scheduled task | Hủy scheduled task **và active subscriptions** |
| Đối số method | Không | Vẫn không |
| `@EnableScheduling` | Cần cơ chế đăng ký annotation | Cũng cần |
| `@EnableAsync` | Không bắt buộc | Không bắt buộc |

Trong reactive pipeline, thread xử lý cũng tùy operator và scheduler của Reactor (hoặc thư viện reactive khác), **không mặc định là cùng thread của Spring TaskScheduler** từ đầu đến cuối.

## 3. Spring lấy Publisher một lần, subscribe nhiều lần

Với reactive `@Scheduled` từ Spring 6.1, framework gọi method để lấy **Publisher P khi đăng ký lịch**, rồi subscribe lại chính P mỗi khi đến hạn. Đây là khác biệt với `@Scheduled void`, nơi method Java chạy tại từng tick.

~~~text
Đăng ký: method -> Publisher P
Tick 1: subscribe(P) -> công việc / terminal #1
Tick 2: subscribe(P) -> công việc / terminal #2
Tick 3: subscribe(P) -> công việc / terminal #3
Shutdown: hủy lịch và subscription còn active
~~~

Publisher phải cho phép nhiều subscription. Một lệnh `Mono.just(expensiveCall())` tính argument **ngay lúc lắp ghép**, do đó các tick có thể nhận kết quả cũ. Nguồn `Mono.defer(() -> client.refresh())` hoặc `Mono.fromCallable(...)` dời việc tạo/tính sang mỗi subscription. Dù vậy, `defer` không tự đổi thread, không làm I/O blocking thành nonblocking, và không sửa được nguồn hot/cached/Future dùng chung một lần. Bài tiếp theo phân tích chính xác lifecycle của Publisher được giữ lại.

## 4. Những kiểu phương thức được hỗ trợ

Spring Framework 6.1 không giới hạn ở Reactor `Mono`/`Flux`, nhưng vẫn có ràng buộc chính xác.

### 4.1. Reactive Streams Publisher

Các phương thức có return type triển khai `org.reactivestreams.Publisher` được hỗ trợ khi publisher có thể được subscribe lại:

~~~java
// Snippet trong Spring-managed component; có Reactor dependency.
@Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
public Mono<Void> refresh() {
    return Mono.fromRunnable(this::performRefresh);
}

@Scheduled(cron = "0 0 * * * *")
public Flux<String> report() {
    return Flux.defer(() ->
            Flux.fromIterable(loadCurrentReportRows()));
}
~~~

`performRefresh` và `loadCurrentReportRows` là method do ứng dụng định nghĩa; hai đoạn trên **không phải chương trình độc lập**. Với `Flux<String>`, Spring sẽ **discard toàn bộ onNext**, nên một report quan trọng phải **lưu dữ liệu/metric** theo contract thay vì nghĩ có caller nhận về từng chuỗi. Cần xử lý lỗi, tránh eager I/O khi tạo Flux và kiểm soát lượng dữ liệu.

### 4.2. Kiểu được ReactiveAdapterRegistry chuyển thành Publisher

Spring còn xét một số kiểu reactive do `org.springframework.core.ReactiveAdapterRegistry` hỗ trợ, **nếu adapter tương ứng có deferred subscription**.

Ví dụ Spring docs dùng RxJava `Single<T>`:

~~~java
// Snippet: chỉ chạy khi có RxJava trên classpath.
@Scheduled(fixedDelay = 2, timeUnit = TimeUnit.SECONDS)
public Single<String> rxJavaPoll() {
    return Single.fromCallable(this::fetchStatus);
}
~~~

`fetchStatus` là method của ứng dụng, có thể blocking; kiểu `Single` cần import đúng phiên bản RxJava tương ứng. Một kiểu “convertible to Publisher” **không đủ**: adapter phải báo `getDescriptor().isDeferred() == true` và instance phải chịu được nhiều subscription.

### 4.3. CompletableFuture không phải lựa chọn thay thế trực tiếp

~~~java
// Không dùng mẫu này làm reactive @Scheduled được hỗ trợ:
@Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
public CompletableFuture<String> wrongScheduled() {
    return CompletableFuture.supplyAsync(this::fetch);
}
~~~

Mặc dù `CompletableFuture` có thể được adapter chuyển sang Publisher trong một số API, adapter của nó được đánh dấu **không deferred** trong `ReactiveAdapterRegistry`. Spring reactive `@Scheduled` yêu cầu kiểu có deferred subscription; vì vậy không thể dựa vào một `CompletableFuture` trả trực tiếp để khởi động **công việc mới ở mỗi tick**. Tùy đường xử lý và phiên bản, đăng ký reactive scheduled method với kiểu không hợp lệ có thể gây lỗi khởi tạo. Không đưa snippet sai vào project chạy.

Nếu upstream vốn trả CompletableFuture, hãy **đặt việc gọi tạo Future bên trong một nguồn reactive deferred**, rồi kiểm soát cancellation và thread:

~~~java
// Snippet trong Spring-managed component; client.fetchAsync() tạo Future.
@Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
public Mono<String> adapted() {
    return Mono.defer(() ->
            Mono.fromFuture(client.fetchAsync()));
}
~~~

Ở đây `client` là dependency ứng dụng; Future được tạo **sau khi** Publisher được subscribe, thay vì lúc đăng ký schedule. Lưu ý `Mono.fromFuture` có quy tắc cancellation có thể tác động tới Future; nếu bên ngoài còn dùng cùng Future hoặc side effect không cancellable, hãy thiết kế ownership cẩn thận. Tránh tạo một Future eager duy nhất ngoài `defer` rồi tái sử dụng.

### 4.4. Kotlin: suspend, Flow và Deferred

Spring Framework 6.1 cũng hỗ trợ Kotlin suspending functions và các method trả Kotlin `Flow`/`Deferred` với dependency bridge phù hợp.

~~~kotlin
// Snippet Kotlin, không thuộc project Java 17 ở mục 9.
@Scheduled(fixedDelay = 5000)
suspend fun updateCache() {
    cacheService.refresh()
}

@Scheduled(fixedRate = 5000)
fun auditEvents(): Flow<String> = flow {
    emit(auditService.nextEvent())
}
~~~

Với `suspend fun`, cần có `kotlinx-coroutines-reactor` để framework thích ứng sang Publisher. Đây không phải việc thêm tham số nghiệp vụ mới: **mọi scheduled method vẫn phải không nhận đối số**. Reactor bridge/adapters và version phù hợp phải được cài trong dự án thực, đặc biệt khi kết hợp coroutines với database/reactive APIs.

## 5. Những yêu cầu chung vẫn không đổi

### 5.1. Cần @EnableScheduling

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
~~~

Đặt cấu hình này trong đúng ApplicationContext để Spring phát hiện/register `@Scheduled`. Không cần bật `@EnableAsync` chỉ để chạy reactive scheduled method, vì **việc subscribe Publisher theo lịch** do scheduling infrastructure thực hiện, không phải async proxy.

### 5.2. Phương thức phải không nhận đối số

~~~java
// ĐÚNG: không có argument.
@Scheduled(fixedRate = 5000)
public Mono<Void> reload() {
    return client.reload();
}

// SAI: reactive cũng không có Spring argument resolver.
@Scheduled(fixedRate = 5000)
public Mono<Void> reload(String tenantId) {
    return client.reload(tenantId);
}
~~~

Cả hai là **snippet minh họa**; `client` do ứng dụng định nghĩa. Đối với trường hợp thứ hai, Spring sẽ không tự inject tenant ID mỗi tick. Hãy inject service/repository vào constructor và lấy job ID/tenant danh sách **bên trong Publisher deferred**.

### 5.3. Annotation chọn lịch, không cấp dữ liệu cho từng lần subscribe

~~~java
// Snippet: worker dùng repository được inject.
@Scheduled(cron = "0 */5 * * * *", zone = "Asia/Ho_Chi_Minh")
public Mono<Void> sweep() {
    return Mono.defer(() ->
            repository.findDueItems()      // Flux<WorkItem>
                    .concatMap(service::process)
                    .then());              // Mono<Void>
}
~~~

`repository` và `service` là reactive components của dự án; snippet không độc lập. Mỗi subscription **mới** đọc danh sách mới. `concatMap` xử lý tuần tự trong một subscription; dùng `flatMap(..., concurrency)` khi cần đồng thời có giới hạn. Một cron trigger không tự giới hạn số công việc đang chạy trong các subscriptions chồng lấp.

### 5.4. Không nhầm với controller WebFlux

Một HTTP handler trả `Mono<ResponseEntity<?>>` sẽ có WebFlux server subscriber và đưa dữ liệu tới HTTP response. Trái lại, reactive `@Scheduled` có subscriber do **Spring scheduler** tạo; `onNext` bị discard, không biến thành HTTP response, không có request context hay principal HTTP mặc định.

Nếu một scheduled job phụ thuộc `SecurityContextHolder` hoặc MDC của request cuối cùng trên thread pool, đó là sai kiến trúc và có rủi ro rò rỉ tenant. Hãy truyền job context đã xác thực hoặc dùng service account, reactive context propagation phù hợp và audit rõ.

## 6. fixedRate, cron, fixedDelay: thời điểm subscribe và hoàn tất

Cần phân biệt **thời điểm tới hạn của scheduler**, **thời điểm subscribe**, **thời điểm onComplete/onError** và **thời điểm nghiệp vụ thật sự được xác nhận**.

### 6.1. fixedRate

~~~java
@Scheduled(fixedRate = 2, timeUnit = TimeUnit.SECONDS)
public Mono<Void> poll() {
    return Mono.defer(() -> service.refresh());
}
~~~

Một mốc đến hạn thường làm Spring **subscribe** vào Publisher. Nếu pipeline bất đồng bộ mất 6 giây mới phát `onComplete`, các tick `fixedRate` tiếp theo **có thể tạo subscription khác khi lượt trước vẫn còn hoạt động**, vì fixed-rate reactive không tự bảo đảm đợi completion của mọi subscription. Chi tiết concurrency còn phụ thuộc scheduler, Reactor operators và tải runtime.

~~~text
Cấu hình fixedRate = 2 giây; mỗi operation mất 6 giây

tick t=0 ---> subscribe #1 ----------------------- complete t~6
tick t=2 ---> subscribe #2 ----------------------- complete t~8
tick t=4 ---> subscribe #3 ----------------------- complete t~10

Dễ thấy các business operations có khả năng overlap.
~~~

Nếu nghiệp vụ cấm overlap (ví dụ đối soát cùng một cursor), phải dùng `fixedDelay` với publisher hữu hạn hoặc thêm trạng thái running/lock/lease. Không đơn giản đổi pool size thành 1 rồi tuyên bố mọi reactive job đều không overlap: pipeline có thể offload sang scheduler khác.

### 6.2. fixedDelay

~~~java
@Scheduled(fixedDelay = 2, timeUnit = TimeUnit.SECONDS)
public Mono<Void> poll() {
    return Mono.defer(() -> service.refresh());
}
~~~

Spring Framework 6.1 **chặn luồng scheduling trong lúc đợi subscription kết thúc** để giữ đúng nghĩa fixed delay: sau khi một lần subscription đi tới terminal signal, mới đếm thêm khoảng nghỉ 2 giây cho lần kế tiếp.

~~~text
subscribe #1 t=0 ---- work ~6s ---- onComplete t=6
                                          |
                                          +--- nghỉ 2s
subscribe #2 t~8 --- work ~3s ---- onComplete t~11
                                          |
                                          +--- nghỉ 2s
subscribe #3 t~13 ...
~~~

Đây là **cơ chế blocking ở ranh giới scheduler** để bảo toàn semantics, không có nghĩa pipeline Reactor bên trong nhất thiết blocking I/O. Với publisher không bao giờ phát `onComplete/onError`, các tick tiếp theo theo fixed delay có thể **không bao giờ bắt đầu**, đồng thời chiếm scheduler thread. Hạn chế đó cần được ghi nhận khi chọn pool size.

Cũng không nên kết luận rằng việc `fixedDelay` trong một pod tự ngăn nhiều pod khác thực thi cùng job. Một application instance khác có subscription và scheduler riêng; cần distributed coordination nếu xử lý một tài nguyên toàn cụm.

### 6.3. Cron

~~~java
@Scheduled(
        cron = "0 0/15 9-10 ? * MON-FRI",
        zone = "Asia/Ho_Chi_Minh")
public Mono<Void> poll() {
    return Mono.defer(() -> service.refresh());
}
~~~

Cron quyết định **những mốc lịch** khi Spring có thể subscribe. Nó không phải độ trễ sau completion và không tự hiểu ngày nghỉ lễ. Với pipeline kéo dài, concurrency/missed ticks phải xét chính sách `CronTrigger`, scheduler và trạng thái thực tế; đừng hứa “mọi tick được replay” nếu app downtime hoặc bận.

### 6.4. So sánh tóm tắt

| Tiêu chí | fixedRate | fixedDelay | Cron |
| --- | --- | --- | --- |
| Mốc kế tiếp | Theo chu kỳ từ lịch bắt đầu | Sau terminal của subscription + delay | Theo biểu thức lịch/múi giờ |
| Reactive `onComplete` cần được chờ trước tick sau? | Không mặc định | **Có**, Spring chờ/block subscription | Phụ thuộc trigger và runtime |
| Khả năng overlap business subscriptions | Có thể, nếu pipeline chưa terminal | Một chuỗi fixed-delay hữu hạn thông thường không overlap | Phụ thuộc runtime; nhiều registrations/pod có thể overlap |
| Thích hợp | Heartbeat/polling có concurrency policy | Poll có thời gian nghỉ sau completion | Lịch theo giờ/ngày |
| Bẫy | Chồng lấp và tích backlog | Publisher vô hạn gây treo lượt kế tiếp | Timezone/DST/missed scheduling |

**Lưu ý về thread:** `SimpleAsyncTaskScheduler` trong Spring 6.1 có đặc tính riêng, đặc biệt fixed-delay task chạy trên scheduler thread. Không lấy hành vi của `ThreadPoolTaskScheduler` rồi áp cho mọi TaskScheduler implementation; kiểm tra đúng bean thực tế trong app.

## 7. Lỗi và giá trị của Publisher được Spring xử lý thế nào?

### 7.1. onNext bị bỏ qua

~~~java
// Snippet có Reactor; giá trị không có caller nhận:
@Scheduled(fixedRate = 5000)
public Flux<String> produce() {
    return Flux.just("first", "second");
}
~~~

Mỗi subscription có thể phát hai `onNext` nhưng **Spring bỏ qua/discard các giá trị**. Nếu kết quả quan trọng (số đơn thành công, bản ghi đã cập nhật), phải chủ động lưu vào DB, phát event, ghi metric hoặc trả kết quả qua một service/job state mà ứng dụng sở hữu. Không dùng return value của `@Scheduled` như một API có consumer.

### 7.2. onError không đi vào ErrorHandler của Runnable

~~~java
// Snippet: mỗi subscription tạo ra error signal.
@Scheduled(fixedRate = 5000)
public Mono<Void> fail() {
    return Mono.defer(() ->
            Mono.error(new IllegalStateException("remote unavailable")));
}
~~~

Với Spring reactive `@Scheduled`, error là **tín hiệu `onError` trong Publisher**, không phải Java exception thoát thẳng khỏi `Runnable.run()` như một tác vụ đồng bộ. Spring **ghi WARN và phục hồi**, nên các lần subscription sau vẫn có thể xảy ra. Spring `ErrorHandler` dành cho synchronous scheduled Runnable **không được gọi để xử lý onError reactive**.

Điều này vừa có lợi vừa có rủi ro:

- Một lỗi mạng thoáng qua không khiến lịch định kỳ tự biến mất.
- Nếu mọi subscription đều fail, ứng dụng có thể liên tục chạy một job lỗi, tạo WARN log nhưng **không có dữ liệu được xử lý**.
- Đừng cho rằng đặt `ThreadPoolTaskScheduler.setErrorHandler(...)` sẽ nhận mọi lỗi Reactor.
- Nếu lỗi cần fail-closed hoặc dừng job, hãy thiết kế trạng thái job/circuit breaker/alert và chiến lược dừng/đổi lịch rõ ràng.

### 7.3. Theo dõi lỗi trong pipeline trước khi Spring xử lý

~~~java
// Snippet trong Spring-managed bean; dependencies do ứng dụng cung cấp.
@Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
public Mono<Void> synchronize() {
    return Mono.defer(() -> client.pullChanges())
            .doOnSuccess(ignored -> metrics.success())
            .doOnError(error -> {
                metrics.failure();
                log.error("Scheduled sync failed", error);
            })
            .then();
}
~~~

`client.pullChanges()` phải có return type phù hợp `Mono<T>`/`Mono<Void>`, `metrics`/`log` được inject. `doOnError` là observer, **không biến failure thành success**. Nếu pipeline kết thúc `onError`, Spring tiếp tục log WARN; khi không muốn log lặp, điều chỉnh logging/observability theo thiết kế, không “nuốt mọi lỗi” để che dữ liệu sai.

### 7.4. onErrorResume có thể đổi trạng thái lỗi thành complete

~~~java
// Snippet: fallback hợp lệ nếu nguồn phụ có thể bỏ qua.
return optionalRecommendations()
        .onErrorResume(TransientRecommendationException.class,
                error -> {
                    metrics.optionalDegraded();
                    return Mono.empty();
                })
        .then();
~~~

Đây là quyết định nghiệp vụ: **chỉ** phục hồi lỗi đã phân loại, có metric/degraded state. Không sử dụng `onErrorResume(error -> Mono.empty())` cho thanh toán, kiểm kê bắt buộc hoặc thao tác phải chắc chắn thành công, vì Spring sẽ thấy `onComplete` và không biết dữ liệu bị bỏ qua.

### 7.5. Retry cần giới hạn và tránh chồng lấp

~~~java
// Snippet: tạo một lần xử lý có retry hữu hạn.
return client.pullChanges()
        .retryWhen(
                Retry.backoff(2, Duration.ofMillis(300))
                        .filter(this::isTransientFailure))
        .then();
~~~

`Retry` thuộc `reactor.util.retry.Retry`, `Duration` thuộc `java.time` và `isTransientFailure` do ứng dụng định nghĩa. Retry này nằm **trong một subscription**, không phải scheduler tạo một tick mới. Với `fixedDelay`, scheduler phải chờ cả retry/terminal mới tính delay tiếp. Với `fixedRate`, các subscriptions mới có thể overlap trong khi lượt cũ còn retry. Dùng idempotency để tránh ghi trùng side effect, đồng thời đặt tổng deadline phù hợp.

## 8. Lifecycle: hủy định kỳ và active subscriptions

Spring Framework quản lý việc huỷ lịch khi bean bị destroy hoặc application context đóng. Với reactive `@Scheduled`, điều này bao gồm **next scheduled subscription** và cả **subscription đã được tạo nhưng còn đang hoạt động** (như một `Flux` rất dài hoặc vô hạn).

~~~text
App start:
    @Scheduled method -> Publisher P
    trigger -> subscription A -> running
    trigger -> subscription B -> running (nếu chính sách cho overlap)

App shutdown:
    hủy registration
    cancel/dispose active subscription A
    cancel/dispose active subscription B
~~~

**Cancellation không phải phép màu hủy side effect bên ngoài.** Nếu Publisher đang gửi HTTP request hoặc thao tác DB đã commit, cancel subscription không bảo đảm rollback hay dừng thao tác bất kỳ. Cần I/O timeout, resource cleanup bằng `doFinally`/`usingWhen` hoặc cơ chế tương đương, trạng thái bền vững và idempotency.

### 8.1. Không dùng Publisher vô hạn một cách vô ý

~~~java
// Anti-pattern: không có terminal bình thường.
@Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
public Flux<Long> infinitePublisher() {
    return Flux.interval(Duration.ofSeconds(1));
}
~~~

`Flux.interval` có thể phát mãi tới khi cancel; với fixed delay, scheduler đang đợi terminal của subscription, nên **sẽ không có vòng tiếp theo theo kỳ vọng**. Với fixed rate/cron, nhiều subscriptions vô hạn có thể tích tụ nếu mỗi tick tạo một subscription mới. Chỉ dùng publisher dài/vô hạn khi lifecycle, cancellation và mục tiêu kiến trúc thực sự yêu cầu; thường periodic scheduling nên trả một **Publisher hữu hạn cho mỗi lần xử lý**.

Một thiết kế tốt hơn cho việc kiểm tra trạng thái:

~~~java
@Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
public Mono<Void> checkOnce() {
    return Mono.defer(() -> remoteClient.checkOnce())
            .timeout(Duration.ofSeconds(8))
            .then();
}
~~~

`remoteClient` giả định trả `Mono<T>` hữu hạn. `timeout` tạo error signal khi không có tiến triển tới hạn (theo semantics Reactor); hãy cài timeout thật ở HTTP client/DB song song và xử lý idempotency, vì cancellation có thể không dừng mọi side effect.

### 8.2. Không để hot/cached publisher làm mất lần chạy

~~~java
// Anti-pattern: chạy lần đầu rồi cache completion/value:
@Scheduled(fixedRate = 10000)
public Mono<String> cachedCall() {
    return Mono.fromCallable(this::fetchLatest).cache();
}
~~~

Vì Spring **lấy một Mono một lần**, `cache()` có thể khiến các subscription sau chỉ nhận lại tín hiệu được cache chứ không gọi `fetchLatest` mới. Mẫu cần làm mới:

~~~java
@Scheduled(fixedRate = 10000)
public Mono<String> freshCall() {
    return Mono.defer(() ->
            Mono.fromCallable(this::fetchLatest));
}
~~~

Nếu `fetchLatest` blocking, thêm execution boundary thích hợp như `subscribeOn(Schedulers.boundedElastic())` và bảo vệ quota/bulkhead; không dùng blocking supplier trên non-blocking event loop mặc định.

## 9. Dự án chạy được: Java 17, Spring Boot 3.3.5 và Reactor

Ví dụ này chứng minh bằng **counter và CountDownLatch** (không dựa vào `Thread.sleep`):

- Spring gọi method để tạo Publisher **một lần**.
- Cùng Publisher được **subscribe ít nhất ba lần**.
- Subscription thứ nhất phát lỗi, nhưng Spring vẫn tiếp tục đăng ký các lần sau.
- `Mono.just` tính eager một lần; `Mono.defer` tính lại theo mỗi subscription.

Dự án dùng **Spring Boot 3.3.5** (Spring Framework 6.1.x), **JDK 17**, Maven và Reactor Core. Không dùng web server, DB, HTTP hay file phụ.

### 9.1. Cấu trúc dự án

~~~text
reactive-scheduled-demo/
├── pom.xml
└── src/main/java/com/example/reactiveschedule/
    ├── ReactiveScheduledApplication.java
    └── ReactivePollingJob.java
~~~

### 9.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>reactive-scheduled-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
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

Version Reactor được quản lý bởi BOM của Spring Boot parent. `spring-boot-starter` cung cấp cấu hình nền và logging; `reactor-core` cung cấp `Mono` và `Flux`, không cần `spring-boot-starter-webflux` vì đây là command-line demo, không phục vụ HTTP.

### 9.3. ReactivePollingJob.java

~~~java
package com.example.reactiveschedule;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ReactivePollingJob {
    private final AtomicInteger factoryCalls = new AtomicInteger();
    private final AtomicInteger subscriptions = new AtomicInteger();
    private final AtomicInteger emittedFailures = new AtomicInteger();
    private final AtomicInteger completedNormally = new AtomicInteger();
    private final CountDownLatch firstThree = new CountDownLatch(3);

    @Scheduled(
            initialDelay = 100,
            fixedDelay = 100,
            timeUnit = TimeUnit.MILLISECONDS)
    public Mono<Void> poll() {
        // Spring gọi method factory một lần khi đăng ký lịch.
        factoryCalls.incrementAndGet();

        // Factory bên trong Mono.defer được gọi mỗi subscription.
        return Mono.defer(() -> {
            int attempt = subscriptions.incrementAndGet();

            if (attempt == 1) {
                emittedFailures.incrementAndGet();
                firstThree.countDown();
                return Mono.<Void>error(
                        new IllegalStateException("first attempt fails"));
            }

            completedNormally.incrementAndGet();
            firstThree.countDown();
            return Mono.<Void>empty();
        });
    }

    public boolean awaitThree(long timeout, TimeUnit unit)
            throws InterruptedException {
        return firstThree.await(timeout, unit);
    }

    public int factoryCalls() {
        return factoryCalls.get();
    }

    public int subscriptions() {
        return subscriptions.get();
    }

    public int emittedFailures() {
        return emittedFailures.get();
    }

    public int completedNormally() {
        return completedNormally.get();
    }
}
~~~

**Cơ chế thực tế:**

- `factoryCalls.incrementAndGet()` nằm **ngoài `Mono.defer`**, chạy khi Spring gọi `poll()` để lấy Publisher.
- `subscriptions.incrementAndGet()` nằm trong deferred supplier, chạy theo **mỗi subscription**.
- Lượt đầu trả `Mono.error`, Spring log WARN cho `onError` rồi vẫn cho phép subscription tiếp theo sau fixed delay.
- Các lượt tiếp theo trả `Mono.empty()`, hoàn tất bình thường.
- Latch xác nhận đã bắt đầu ít nhất ba lần subscription trước khi runner in kết quả.
- Các `AtomicInteger` cho phép quan sát an toàn giữa Spring scheduling thread và runner thread.

**Lưu ý:** `completedNormally` được tăng ngay khi chọn nhánh trả `Mono.empty()`; đây là counter mô phỏng “lượt dự kiến hoàn tất bình thường” trong ví dụ đơn giản, không phải metric tổng quát đã xác nhận mọi I/O bên ngoài commit. Với tác vụ thật, dùng `doOnSuccess`/`doOnError`/`doFinally` hoặc job ledger để ghi đúng lifecycle.

### 9.4. ReactiveScheduledApplication.java

~~~java
package com.example.reactiveschedule;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import reactor.core.publisher.Mono;

@SpringBootApplication
@EnableScheduling
public class ReactiveScheduledApplication {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                     SpringApplication.run(
                             ReactiveScheduledApplication.class, args)) {
            // Đóng context sau khi runner đã hoàn tất việc kiểm chứng.
        }
    }

    @Bean("taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("reactive-schedule-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }

    @Bean
    ApplicationRunner verify(ReactivePollingJob job) {
        return args -> {
            if (!job.awaitThree(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Did not receive three subscriptions");
            }

            System.out.println("FACTORY_CALLED_ONCE="
                    + (job.factoryCalls() == 1));
            System.out.println("SUBSCRIPTIONS_GE_3="
                    + (job.subscriptions() >= 3));
            System.out.println("FIRST_FAILURE_EMITTED="
                    + (job.emittedFailures() == 1));
            System.out.println("SUCCESS_BRANCHES_GE_2="
                    + (job.completedNormally() >= 2));

            AtomicInteger counter = new AtomicInteger();

            // calculate eager khi tạo Mono:
            Mono<Integer> eager =
                    Mono.just(counter.incrementAndGet());

            // calculate lazy khi từng subscription:
            Mono<Integer> deferred = Mono.defer(() ->
                    Mono.just(counter.incrementAndGet()));

            System.out.println("EAGER_VALUES="
                    + eager.block() + "," + eager.block());
            System.out.println("DEFERRED_VALUES="
                    + deferred.block() + "," + deferred.block());
        };
    }
}
~~~

`ThreadPoolTaskScheduler` dùng một worker để demo dễ theo dõi. `@EnableScheduling` cho Spring đăng ký `@Scheduled`. Không cần `@EnableAsync` vì phương thức reactive được **schedule và subscribe**, không phải lời gọi bị chặn bởi async proxy.

Runner gọi `awaitThree` có timeout chỉ để **thử nghiệm và in stdout có tính quyết định**; không phải mẫu tối ưu cho API production. `Mono.block()` hai lần ở cuối runner cũng chỉ minh họa `just` vs `defer` trên một command-line thread, không đặt trong Reactor event-loop và không khuyến nghị dùng trong reactive request pipeline.

Khi Spring gặp `onError` đầu tiên, có thể xuất hiện một dòng log mức WARN kèm stack trace; đó là **log framework**, không phải một dòng `System.out` thuộc bộ output kiểm thử.

### 9.5. Chạy và stdout dự kiến

Mở terminal tại thư mục chứa `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

Bỏ qua log Maven, Spring và WARN của subscription thất bại đầu tiên, **sáu dòng do chương trình tự in** có nội dung:

~~~text
FACTORY_CALLED_ONCE=true
SUBSCRIPTIONS_GE_3=true
FIRST_FAILURE_EMITTED=true
SUCCESS_BRANCHES_GE_2=true
EAGER_VALUES=1,1
DEFERRED_VALUES=2,3
~~~

Giải thích lý do ổn định:

1. `poll()` được Spring gọi khi đăng ký và trả đúng một deferred Publisher: `factoryCalls==1`.
2. `CountDownLatch(3)` bảo đảm ít nhất ba lần subscription đã vào deferred supplier, nên `subscriptions>=3`.
3. Chỉ `attempt==1` phát failure; khi counter ≥3, đã có ít nhất hai lần đi nhánh success.
4. `Mono.just(counter.incrementAndGet())` làm counter thành 1 ngay khi tạo Mono và phát lại 1 ở hai lần `block()`.
5. `Mono.defer(() -> Mono.just(counter.incrementAndGet()))` chưa tăng counter ở lúc định nghĩa; hai lần `block()` tiếp theo tạo 2 và 3.

Test này **không cần `Thread.sleep`**, không đòi hỏi đúng số tick tại thời điểm in và không đo thời gian chính xác tới mili-giây. Nếu môi trường không có tài nguyên để scheduler chạy trong 10 giây, runner fail rõ ràng bằng timeout thay vì treo mãi.

## 10. Thiết kế reactive scheduled job cho nghiệp vụ thực

Ví dụ production: cứ 30 giây poll một API và ghi vào R2DBC theo batch. Một cấu trúc rõ ràng (snippet, phụ thuộc vào service của dự án):

~~~java
// Snippet bên trong Spring-managed component.
@Scheduled(
        fixedDelayString = "${inventory.poll.delay:PT30S}")
public Mono<Void> pollInventory() {
    return Mono.defer(() ->
            partnerClient.fetchUpdates())
            .flatMapIterable(batch -> batch.items())
            .concatMap(item -> inventoryStore.upsert(item))
            .then()
            .timeout(Duration.ofSeconds(20))
            .doOnSuccess(v -> metrics.pollSucceeded())
            .doOnError(e -> {
                metrics.pollFailed();
                log.error("Inventory polling failed", e);
            });
}
~~~

Giả định `partnerClient.fetchUpdates()` trả `Mono<Batch>`; `batch.items()` là danh sách; `inventoryStore.upsert(item)` trả Publisher/Mono thích hợp; `metrics`/`log` được inject. Đây là snippet có model/dịch vụ giả định **không phải class đầy đủ**. Phần `${...}` được Spring resolve thành property khi khởi tạo.

Tại sao thiết kế như vậy?

- `Mono.defer` đảm bảo mỗi subscription lấy **một batch mới**.
- `concatMap` giúp tuần tự hóa ghi dữ liệu trong **một subscription** khi cần giữ thứ tự, không đồng nghĩa làm cho cả cluster chỉ có một job.
- `then()` chuyển thành `Mono<Void>` vì `onNext` không có caller nhận và việc quan trọng là terminal signal.
- `timeout` giới hạn thời gian xử lý của subscription, cho fixed delay cơ hội nhận terminal thay vì chờ vô hạn.
- `doOnSuccess`/`doOnError` tạo đường quan sát riêng trước khi Spring nhận terminal, tránh bỏ sót thất bại của nghiệp vụ.
- DB/service cần hỗ trợ idempotency, xử lý dữ liệu trùng và transaction phù hợp. Không dựa vào việc job cứ chạy định kỳ là mọi lần upsert đều có tính exactly-once.

Nếu pipeline rất lớn, không đưa toàn bộ batch vào RAM không giới hạn; áp dụng pagination, streaming, `flatMap(..., concurrency)` có giới hạn và backpressure. Khi partner rate-limited, dùng retry/backoff phù hợp thay vì đơn giản giảm fixedDelay rồi tạo thêm nhiều yêu cầu.

### 10.1. Blocking I/O trong reactive pipeline

~~~java
// Snippet cho API blocking: có boundary riêng.
return Mono.fromCallable(() -> legacyJdbcClient.load())
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(this::convertAndSaveReactive)
        .then();
~~~

`Schedulers.boundedElastic()` cung cấp scheduler phù hợp cho nhiều tác vụ blocking ngắn với giới hạn; không biến JDBC thành I/O non-blocking thật. `legacyJdbcClient` và `convertAndSaveReactive` do ứng dụng định nghĩa. Không chạy `block()` trong WebFlux event-loop hoặc giữ transaction thread-bound một cách sai lệch giữa các scheduler.

Nếu ứng dụng hoàn toàn dùng JDBC, có thể phù hợp hơn khi dùng một `@Scheduled void` đồng bộ với pool phù hợp, tránh dùng Reactor chỉ để bao một lời gọi blocking.

### 10.2. Không dùng Reactor Context như HTTP request context mặc định

Một job scheduled thường không có HTTP request, session hoặc người dùng đang đăng nhập. `ThreadLocal`/MDC từ request của người khác không được xem là thông tin xác thực. Nếu cần correlation ID cho mỗi subscription, sinh/đặt job context tại **subscription time** và truyền qua `contextWrite`/Micrometer context propagation phù hợp:

~~~java
// Snippet: correlationId do code sinh riêng cho mỗi subscription.
return Mono.defer(() -> {
    String correlationId = newJobCorrelationId();
    return service.execute()
            .contextWrite(ctx -> ctx.put("jobCorrelationId", correlationId));
});
~~~

`newJobCorrelationId`/`service.execute` là thành phần dự án. Reactor Context không tự thay thế business authorization hoặc security principal. Truyền tenantId/actor đã kiểm tra qua DTO rõ ràng; dùng service identity hoặc chính sách job phù hợp.

## 11. Observability, retry, concurrency và nhiều instance

### 11.1. Những metric nên có

| Metric / log | Đo tại đâu? | Lý do |
| --- | --- | --- |
| `scheduled_subscriptions_total` | Trong `Mono.defer` hoặc `doOnSubscribe` | Có bao nhiêu lượt thực sự subscribe |
| `scheduled_completions_total` | `doOnSuccess` hoặc terminal `doFinally` được phân loại | Số lượt thành công/terminal |
| `scheduled_errors_total` | `doOnError` | Spring WARN không đủ cho cảnh báo nghiệp vụ |
| `active_subscriptions` | Tăng lúc subscribe, giảm trong `doFinally` | Phát hiện overlap/leak |
| `poll_duration` | Dùng monotonic clock theo scope mỗi subscription | Phân biệt chậm upstream và chậm scheduler |
| `last_success_timestamp` | Sau xác nhận side effect thành công | Phát hiện job vẫn tick nhưng luôn lỗi |
| `timeout/retry/rejection` | Tại điểm phát sinh | Cảnh báo bão hòa hoặc partner lỗi |
| `job_id/tenant_id` | Log có kiểm soát, không lưu bí mật | Truy vết một subscription cụ thể |

**Không tăng “success” chỉ vì Spring đăng ký được lịch hoặc phương thức tạo Publisher đã return.** Với reactive, điều đó có thể xảy ra trước khi bất kỳ side effect nào thực hiện.

### 11.2. Khi cần tránh overlap

- Một schedule `fixedDelay` với Publisher hữu hạn có thể giúp không có lượt mới trước khi lượt cũ terminal, nhưng pipeline vô hạn sẽ chặn.
- Với `fixedRate` hoặc nhiều `@Scheduled` annotations, phải xem các subscriptions có thể overlap.
- Với một app chạy ba pod, mỗi pod có thể subscribe vào job của mình: **không có distributed lock tự động**.
- Khi cần singleton job toàn cluster, dùng leader election, DB lease/claim, broker hoặc scheduler tập trung. Idempotency vẫn cần cho retry và restart.
- Một flag trong bộ nhớ `AtomicBoolean running` chỉ ngăn overlap **trong JVM** và phải reset trong `doFinally`; nó không thay thế coordination giữa nhiều instance.

### 11.3. Không chờ vô hạn hoặc giữ dữ liệu vô hạn

- Dùng timeout HTTP/DB, deadline tổng và cancellation contract.
- Publisher kiểu `Flux.never()` hoặc `Flux.interval` không giới hạn không phù hợp với một lần xử lý hữu hạn.
- Với `fixedDelay`, Spring chặn để đợi terminal; pool nhỏ có thể bị chiếm lâu.
- Với `fixedRate`/cron, một publisher quá chậm có thể dẫn tới nhiều active subscriptions.
- Retry phải có giới hạn/backoff và idempotency; `retryWhen(Retry.backoff(...))` có thể kéo dài thời gian một subscription.
- Khi shutdown, cancellation có thể xảy ra giữa transaction/side effect; cần cơ chế recover/checkpoint bền vững.

## 12. Những nhầm lẫn điển hình

| Hiện tượng / suy nghĩ sai | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| “`@Scheduled Mono` chạy method ở mỗi tick” | Spring tạo Publisher một lần rồi subscribe nhiều lần | Đưa công việc vào cold/deferred source |
| Job chỉ gọi API một lần | Dùng `Mono.just(apiCall())` hoặc cached/hot publisher | `Mono.defer`/`fromCallable` phù hợp |
| `Flux<String>` phát dữ liệu nhưng không ai đọc | Spring discard `onNext` | Ghi kết quả tại service/DB/metric có chủ đích |
| `@Async` không bật nên nghĩ reactive scheduling không chạy | Hai infrastructure khác nhau | Bật `@EnableScheduling`, có Reactor/adapters |
| Mỗi lần lỗi tưởng lịch sẽ dừng | Spring log WARN và recover `onError` | Metric/alert/error policy và job state |
| ErrorHandler của scheduler không nhận Reactor error | `onError` là reactive signal | Xử lý trong pipeline bằng `doOnError`/`onErrorResume` có chọn lọc |
| `fixedDelay` không có tick sau | Publisher không terminal hoặc blocking I/O treo | Timeout và nguồn hữu hạn |
| Fixed-rate job bị overlap | Subscriptions còn active lúc tick mới | Hạn chế concurrency/lease hoặc fixedDelay |
| Trả `CompletableFuture` làm @Scheduled reactive | Adapter không deferred | Mono deferred tạo Future mới mỗi subscription |
| Dùng `Mono.cache()` | Kết quả/terminal bị cache | Không cache pipeline cần thực thi mới mỗi tick |
| Lỗi startup với method có tham số | `@Scheduled` vẫn yêu cầu zero arguments | Inject dependency, lấy input bên trong pipeline |
| Cancel nhưng DB vẫn ghi | Hủy subscription không rollback side effect đã commit | I/O cancellation, idempotency, checkpoint |
| Một cron chạy ba lần trên ba pod | Mỗi pod có lịch riêng | Distributed coordination |
| Callback dùng thread-local request cũ | Scheduled job không nằm trong request scope | Job identity rõ, propagation đúng, không lấy stale tenant |

### Checklist trước khi merge

1. Phiên bản Spring thực sự **≥6.1** và dependency Reactor/RxJava/Kotlin bridge phù hợp?
2. Có `@EnableScheduling` trong context cần thiết; phương thức `@Scheduled` có **zero arguments**?
3. Return type có phải Publisher nhiều-subscriptions hoặc kiểu adapted có descriptor `isDeferred() == true`?
4. **Java method chỉ tạo Publisher một lần**: mọi side effect cần lặp đã được dời tới subscription bằng `defer`/`fromCallable`/`fromRunnable`?
5. Không dùng `Mono.just(expensiveCall())` hoặc `Mono.cache()` vô tình khiến chỉ chạy lần đầu?
6. Có hiểu rõ sự khác biệt `onNext` bị bỏ qua, `onError` được WARN/recover, `onComplete` là kết thúc một lượt?
7. Với `fixedDelay`, Publisher có terminal signal và timeout; chấp nhận scheduler thread chờ completion?
8. Với `fixedRate`/cron, có chính sách overlap, concurrency, rate limits và backpressure?
9. Có metrics/structured logging, job correlation ID, retry có giới hạn, phân loại lỗi business/infra?
10. Có cleanup/hủy tài nguyên và trạng thái durable khi app shutdown/restart?
11. Với multi-pod, có distributed lock/lease/idempotency nếu yêu cầu xử lý một lần?
12. Có test method factory một lần nhưng subscribe nhiều lần, lỗi một lượt không chặn lượt sau, timeout/hủy và output chính xác?

## 13. Câu hỏi tự kiểm tra

1. Điểm mới của Spring Framework 6.1 đối với `@Scheduled` có return type reactive là gì?
2. Tại sao một method `@Scheduled public Mono<Void> poll()` thường không được Spring gọi lại ở mọi tick dù subscription vẫn lặp?
3. `Mono.just(remoteCall())` và `Mono.defer(() -> Mono.fromCallable(this::remoteCall))` khác nhau lúc nào thực hiện công việc?
4. Vì sao `CompletableFuture` có thể chuyển thành Publisher mà vẫn không đủ điều kiện deferred subscription cho reactive scheduled method?
5. Nếu một `Flux` phát 500 bản ghi qua `onNext`, Spring `@Scheduled` đưa các bản ghi đó về đâu?
6. Khi Publisher phát `onError`, Spring scheduling `ErrorHandler` của Runnable có được gọi không? Lịch sau đó có tiếp tục không?
7. Vì sao fixed delay có thể bị treo nếu Publisher không phát terminal signal?
8. Một method `@Scheduled(fixedRate=...)` trả Mono công việc mất 30 giây trong khi period là 5 giây. Có thể có subscriptions overlap không?
9. Reactor `Mono.cache()` ảnh hưởng những lần subscribe kế tiếp của cùng Publisher ra sao?
10. Vì sao hủy scheduled subscription không tự rollback DB hoặc đảm bảo không có duplicate side effect?
11. Spring Boot app có bốn pods: một `@Scheduled` có tự chỉ chạy trên một pod không?
12. Khi nào tốt hơn nên dùng `@Scheduled void` thông thường thay vì reactive method?
13. Kotlin `suspend fun` có cần coroutine/reactor bridge và có được nhận tham số business trong scheduled callback không?

**Ghi nhớ:** Từ **Spring Framework 6.1**, `@Scheduled` có thể dùng với phương thức reactive, nhưng điều được lập lịch là **việc subscribe vào Publisher đã được tạo một lần**. Hãy thiết kế Publisher **lazy, deferred, re-subscribable và hữu hạn khi dùng fixed delay**; hiểu rằng `onNext` bị ignore, `onError` được log/recover, và shutdown hủy subscriptions. Quản lý deadline, concurrency, logging, idempotency và business state độc lập với cơ chế trigger.

## Tài liệu tham khảo chính thức

- [Spring Framework — Task Execution and Scheduling: reactive @Scheduled](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html#scheduling-annotation-support-reactive)
- [Spring Framework 6.1.13 — @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.1.13/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework 6.2 — @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework — ReactiveAdapterRegistry](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/ReactiveAdapterRegistry.html)
- [Project Reactor — Mono.defer](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#defer-java.util.function.Supplier-)
- [Project Reactor — Retry](https://projectreactor.io/docs/core/release/api/reactor/util/retry/Retry.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-scheduling-002">Publisher được lấy một lần và được đăng ký nhận nhiều lần theo lịch</a>

<details>
<summary>Xem chi tiết</summary>

# Publisher được lấy một lần và được đăng ký nhận nhiều lần theo lịch

## 1. Bản chất của reactive @Scheduled trong Spring Framework

Kể từ **Spring Framework 6.1**, một phương thức đánh dấu `@Scheduled` có thể trả `org.reactivestreams.Publisher` (chẳng hạn `Mono` hoặc `Flux` của Project Reactor), không chỉ `void`. Tuy nhiên, cách Spring thực hiện **khác với việc gọi lại phương thức để tạo một Publisher mới theo mỗi nhịp lịch**.

**Spring lấy Publisher một lần khi đăng ký scheduled method, rồi theo các mốc fixed-rate, fixed-delay hoặc cron sẽ đăng ký những subscription mới vào chính Publisher ấy.** Nói cách khác, nếu một phương thức reactive được xử lý thành một registration, nó thường được gọi **một lần để tạo Publisher**, nhưng Publisher có thể được **subscribe nhiều lần**.

Đây là lý do một đoạn code trông hoàn toàn hợp lệ vẫn có thể chỉ tải dữ liệu đúng một lần lúc startup, hoặc phát lại dữ liệu cũ vào tất cả các kỳ chạy sau. Hành vi mỗi tick được quyết định bởi **ngữ nghĩa của Publisher khi được subscribe**, không chỉ bởi nội dung code của method.

### Mục tiêu

- Phân biệt **method invocation**, **assembly**, **subscription** và **thực thi nguồn** trong reactive scheduling.
- Giải thích chính xác vì sao `Mono.just(expensiveCall())` có thể chỉ gọi `expensiveCall()` một lần, còn `Mono.fromSupplier` hoặc `Mono.defer` cho phép làm việc theo từng subscription.
- Hiểu Publisher phải hỗ trợ nhiều lần subscribe, và những kiểu được `ReactiveAdapterRegistry` thích ứng phải hỗ trợ **deferred subscription**.
- Biết `onNext` bị bỏ qua, `onError` được Spring log WARN rồi tiếp tục lịch, `ErrorHandler` của task đồng bộ không nhận trực tiếp reactive error.
- Hiểu vì sao với `fixedDelay`, Spring chờ subscription hoàn tất, còn fixed-rate và cron có nguy cơ chồng lấn những subscription còn active.
- Xây dựng một project Maven **Java 17+**, **Spring Framework 6.1.3** và **Reactor Core 3.6.2** để chứng minh số lần gọi method khác số lần subscribe.
- Áp dụng đúng các ranh giới I/O, cancellation, context, retry, backpressure và idempotency.

**Chủ đề chính:** Lifecycle của **một Publisher được giữ lại và subscribe lặp theo lịch**. Đây không phải bài tổng quan về tất cả API Reactive Streams hoặc cách tạo thread pool.

## 2. Mô hình Spring đăng ký một lần, subscribe theo nhiều nhịp

Xem một scheduled method đơn giản:

~~~java
// Fragment nằm trong bean do Spring quản lý.
@Scheduled(fixedDelay = 5_000)
public Mono<Void> refreshCatalog() {
    return Mono.defer(() -> catalogService.refresh());
}
~~~

Luồng thực thi khái niệm:

~~~text
Spring ApplicationContext khởi tạo
          |
          +--> nhận diện @Scheduled trên method
          |
          +--> gọi refreshCatalog() để lấy Publisher P
          |         |
          |         +-- P được giữ cho registration này
          |
          +--> đăng ký Runnable theo fixedDelay
                    |
                 tới hạn T1
                    |
                    +--> P.subscribe() lần 1
                    |         |
                    |         +--> defer factory chạy
                    |         +--> catalogService.refresh()
                    |         +--> onComplete
                    |
                 chờ fixedDelay
                    |
                 tới hạn T2
                    |
                    +--> P.subscribe() lần 2
                              |
                              +--> defer factory chạy lại
                              +--> catalogService.refresh() lần mới
~~~

**Quan trọng:** Không có bước mặc định “Spring gọi lại `refreshCatalog()` để nhận P2” tại T2. Nếu muốn có một object Publisher con mới cho từng lần, chính pipeline cần trì hoãn tạo object đó đến subscription, ví dụ bằng `Mono.defer(...)`.

Bốn giai đoạn khác nhau:

| Giai đoạn | Xảy ra lúc nào? | Điều gì cần kiểm soát? |
| --- | --- | --- |
| Method được gọi | Khi registration được tạo | Tránh I/O hoặc chụp dữ liệu request động ở đây |
| Publisher được lắp (assembly) | Thường trong method | Chọn operator eager hay lazy cho đúng |
| Subscription bắt đầu | Theo mốc schedule | Work có thực hiện lại không, có lẫn state không? |
| Publisher phát terminal signal | Khi nguồn hoàn tất hoặc lỗi | Fixed-delay, retry, timeout, cleanup và lịch tiếp |

`@EnableScheduling` điều khiển việc phát hiện các scheduled methods; không phải `Mono` tự bật scheduling. Trong một app chỉ khai báo bean trả Mono mà thiếu scheduling infrastructure, không có những subscription theo lịch từ Spring.

### 2.1. Một registration không phải một lần làm việc duy nhất

Publisher là một abstraction mô tả cách phát các tín hiệu `onSubscribe`, `onNext`, `onError` và `onComplete`. Một Publisher lạnh (cold) có thể tạo công việc mới cho **mỗi subscriber**. Một Publisher nóng (hot) có thể phát độc lập với subscribers, hoặc có tính chia sẻ/replay.

Vì vậy không nên rút gọn thành “cùng Publisher nghĩa là cùng giá trị” hay “có hai subscriptions nghĩa là đã làm lại I/O”: cả hai đều phải kiểm tra implementation.

### 2.2. Spring không trả các onNext values cho caller

Scheduled method không có caller đang chờ kết quả nghiệp vụ như HTTP endpoint. Khi Publisher phát `onNext(value)`, Spring **bỏ qua giá trị ấy**. Muốn lưu dữ liệu, cập nhật projection hoặc xử lý batch, bạn phải làm trong pipeline.

~~~java
// Fragment với repository và projectionService đã được inject.
@Scheduled(fixedDelay = 10_000)
public Mono<Void> refreshProjection() {
    return Flux.defer(() -> repository.findUpdates())
            .concatMap(projectionService::apply)
            .then();
}
~~~

`concatMap` phối hợp từng phần tử, `then()` tạo `Mono<Void>` hoàn tất khi upstream hoàn tất. Đây chỉ là fragment: `repository`/`projectionService` cần có trong ứng dụng. Nó minh họa rằng giá trị không được Spring giữ hộ, nhưng các operator bên trong pipeline vẫn chạy theo subscription.

## 3. Hợp đồng của @Scheduled reactive ở Spring 6.1+

Các điểm trong Javadoc của `org.springframework.scheduling.annotation.Scheduled` và Spring Reference:

1. Có thể trả `Publisher`, hoặc kiểu reactive được thích ứng bằng `ReactiveAdapterRegistry` **nếu hỗ trợ deferred subscription**.
2. Publisher trả về phải hỗ trợ **nhiều subscription tiếp theo**.
3. Publisher được **lấy một lần** và framework subscribe lại theo `fixedRate`, `fixedDelay`, `cron` tương ứng.
4. Các tín hiệu `onNext` bị bỏ qua, tương tự việc bỏ qua giá trị trả về của method `@Scheduled` đồng bộ.
5. `onError` được ghi log ở mức **WARN**, sau đó scheduling infrastructure tiếp tục những subscription định kỳ; nó không mặc nhiên hủy lịch.
6. `ErrorHandler` của scheduled Runnable đồng bộ **không phải** callback xử lý trực tiếp của reactive `onError`.
7. Với `fixedDelay`, subscription được **chờ/chặn** để bảo đảm delay tính từ lúc Publisher hoàn tất.
8. Khi bean bị destroy hoặc ApplicationContext đóng, Spring hủy lịch tương lai và các subscription trước đó vẫn active do nó quản lý.

### 3.1. Kiểu reactive và điều kiện deferred

| Kiểu | Phù hợp làm reactive @Scheduled? | Lưu ý |
| --- | --- | --- |
| `Mono<T>` và `Mono<Void>` | Có | Hỗ trợ subscribe lặp; quan tâm cold/hot |
| `Flux<T>` | Có | Có thể phát nhiều phần tử hoặc vô hạn |
| `Publisher<T>` | Có | Implementation cần hỗ trợ nhiều subscriptions |
| RxJava `Single<T>` | Có nếu adapter tương thích | Registry phải báo kiểu hỗ trợ deferred |
| `CompletableFuture<T>` | Không phù hợp làm return trực tiếp | Adapter của nó báo `isDeferred() == false` |
| Kotlin suspending function | Có trong Spring 6.1+ | Cần `kotlinx-coroutines-reactor` để bridge |
| `void` | Có theo `@Scheduled` truyền thống | Method được gọi theo từng tick |

Đừng nhầm “kiểu có thể biến thành Publisher” với “kiểu hợp lệ cho deferred repeated scheduling”. `CompletableFuture` là ví dụ Spring nêu rõ: một future đại diện computation cụ thể, không tự tạo computation mới khi có subscription khác.

### 3.2. Nếu method bị khai báo nhiều @Scheduled?

`@Scheduled` là repeatable annotation. Các khai báo đồng vị trí trên một method được xử lý **độc lập**, với lịch của từng registration. Khi đọc quy tắc “Publisher lấy một lần”, hãy hiểu trong phạm vi đường xử lý **một registration**, không tuyên bố mọi cấu hình nhiều annotations chắc chắn chỉ có một lần method invocation toàn bean.

Nếu hai schedules được cố ý khai báo, hãy kiểm tra số registrations, khả năng overlap và tránh duplicate side effects. Một method không đồng nghĩa một lịch độc quyền.

## 4. Eager vs lazy: vì sao chọn Mono.just hay Mono.defer rất quan trọng?

### 4.1. Mono.just(value) — giá trị đã được tính lúc tạo

~~~java
// Phản ví dụ khi mục tiêu là đọc dữ liệu tươi mỗi tick.
@Scheduled(fixedRate = 5_000)
public Mono<String> eagerRead() {
    String value = database.readCurrentValue();
    return Mono.just(value);
}
~~~

`database.readCurrentValue()` được gọi ngay khi Spring nhận Publisher từ method. Lời gọi này có thể còn **chặn quá trình khởi tạo bean/scheduler** nếu là I/O đồng bộ. Các subscription sau nhận cùng `value` đã capture; chúng không tự gọi lại database.

Nếu dữ liệu thực sự bất biến, `Mono.just(constant)` không sai. Sai ở đây là sự bất đồng giữa mục tiêu “mỗi tick tính mới” và toán tử eager.

### 4.2. Mono.fromSupplier — tính mới khi subscribe

~~~java
@Scheduled(fixedRate = 5_000)
public Mono<String> supplierRead() {
    return Mono.fromSupplier(database::readCurrentValue);
}
~~~

`Mono.fromSupplier` giữ `Supplier` và gọi nó ở subscription time. Cùng một Mono object có thể gọi `database.readCurrentValue()` trong những lần subscribe khác nhau. Nhưng **lazy không đồng nghĩa non-blocking**: nếu database API chặn, nó có thể giữ thread subscribe.

Với nguồn blocking, có thể cân nhắc:

~~~java
@Scheduled(fixedRate = 5_000)
public Mono<String> supplierOnBoundedElastic() {
    return Mono.fromCallable(database::readCurrentValue)
            .subscribeOn(Schedulers.boundedElastic());
}
~~~

Reactor `boundedElastic` giúp cách ly blocking operations khỏi worker không được phép block, nhưng vẫn có giới hạn queue/thread và phải đặt timeout cho database thật. `subscribeOn` không phải config cho Spring `TaskScheduler`: đây là scheduling boundary **bên trong reactive pipeline**.

### 4.3. Mono.fromCallable — phát lỗi qua onError

`Mono.fromCallable(...)` gần với `fromSupplier` nhưng `Callable.call()` có thể ném checked exception. Lỗi khi gọi `call()` được biến thành `onError`, phù hợp với reactive error handling. Nếu nó là lỗi mạng cần retry, chọn retry policy riêng, không cho rằng Spring sẽ retry ngay chính lần subscription ấy.

~~~java
@Scheduled(fixedDelay = 5_000)
public Mono<Void> cleanupFiles() {
    return Mono.fromCallable(() -> fileService.cleanup())
            .subscribeOn(Schedulers.boundedElastic())
            .then();
}
~~~

Đây là fragment, `fileService` cần được định nghĩa. `then()` trả Mono không có giá trị; Spring chỉ cần biết callback hoàn tất hay báo lỗi để điều phối những nhịp sau.

### 4.4. Mono.defer — tạo nguồn Publisher con mới mỗi tick

~~~java
@Scheduled(fixedDelay = 5_000)
public Mono<Void> deferRead() {
    return Mono.defer(() ->
            service.createNewReactiveOperation());
}
~~~

`Mono.defer` gọi factory ở **mỗi lần subscribe** và nhận một Publisher con. Điều này khác với:

~~~java
@Scheduled(fixedDelay = 5_000)
public Mono<Void> capturedRead() {
    Mono<Void> operation = service.createNewReactiveOperation();
    return operation;
}
~~~

Với `capturedRead()`, `createNewReactiveOperation()` chỉ được gọi ở method invocation/assembly; nguồn của nó *có thể* vẫn cold và chạy mới khi subscribe, nhưng việc tạo object Publisher không lặp lại. Với `deferRead()`, factory chắc chắn chạy theo từng subscription (trong trường hợp subscription thực sự xảy ra).

**Không bắt buộc luôn bọc mọi Mono bằng defer.** Nếu `service.createNewReactiveOperation()` đã trả Publisher cold có semantics làm mới mỗi subscribe, giữ nó có thể đúng. Dùng defer khi cần tái tạo request ID, DTO, pipeline hoặc snapshot tài nguyên mỗi tick.

### 4.5. Mono.just(future) hay Mono.fromFuture(existing) không tạo lại Future

~~~java
// Phản ví dụ: CompletableFuture tạo một lần khi scheduled method được gọi.
@Scheduled(fixedRate = 5_000)
public Mono<String> staleFuture() {
    CompletableFuture<String> future =
            CompletableFuture.supplyAsync(
                    () -> blockingRemoteCall(), ioExecutor);
    return Mono.fromFuture(future);
}
~~~

Method trả `Mono` hợp lệ, nhưng future bên trong đã được tạo một lần. Các subscribers nhận lại kết quả của cùng Future; Spring không tự nộp một task mới cho mỗi tick.

Nếu thực sự cần Future mới trên từng subscription:

~~~java
@Scheduled(fixedRate = 5_000)
public Mono<String> freshFuture() {
    return Mono.defer(() -> {
        CompletableFuture<String> future =
                CompletableFuture.supplyAsync(
                        () -> blockingRemoteCall(), ioExecutor);
        return Mono.fromFuture(future);
    });
}
~~~

Đây là fragment với `ioExecutor`, `blockingRemoteCall()` chưa định nghĩa; không khuyến nghị dùng Future không cần thiết khi đã có API Reactor non-blocking. Điểm quan trọng là **lời gọi tạo Future nằm trong factory được hoãn**.

## 5. Project Maven đầy đủ: một lần tạo Publisher, nhiều subscriptions

Project này chứng minh bằng dữ liệu xác định:

- Bộ đếm `methodInvocations` tăng **trong method** `@Scheduled`, đại diện số lần tạo Publisher.
- Bộ đếm `subscriptions` tăng **bên trong `Mono.defer`**, tức mỗi khi Publisher được subscribe.
- `CountDownLatch(2)` đợi **ít nhất hai subscriptions** thay vì đoán số tick dựa trên `Thread.sleep`.
- Một ví dụ Reactor đơn giản trong `main` chỉ ra `Mono.just` khác `Mono.fromSupplier`.

### 5.1. Cấu trúc

~~~text
spring-reactive-schedule-demo/
├── pom.xml
└── src/main/java/demo/
    ├── SchedulerConfig.java
    ├── ReactiveJob.java
    └── ReactiveScheduleDemo.java
~~~

**JDK:** 17+. **Spring Framework:** 6.1.3. **Reactor Core:** 3.6.2. Không cần Spring Boot hay web server cho bài thử này.

### 5.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-reactive-schedule-demo</artifactId>
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
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
            <version>3.6.2</version>
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
                    <mainClass>demo.ReactiveScheduleDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Dependency `spring-context` cấp Spring scheduling infrastructure; `reactor-core` cấp `Mono`/`Flux`. Với ứng dụng thực tế có Spring platform/BOM, nên để dependency management kiểm soát phiên bản Reactor thay vì hardcode nhiều nơi.

### 5.3. SchedulerConfig.java

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulerConfig {

    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("reactive-sched-");
        return scheduler;
    }

    @Bean
    public ReactiveJob reactiveJob() {
        return new ReactiveJob();
    }
}
~~~

`@EnableScheduling` bật phát hiện và đăng ký method `@Scheduled`. `ThreadPoolTaskScheduler` được container quản lý lifecycle. Pool size một thread được dùng cho **demo**, không phải khuyến nghị cố định cho production.

### 5.4. ReactiveJob.java

~~~java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;

public final class ReactiveJob {

    private final AtomicInteger methodInvocations =
            new AtomicInteger();

    private final AtomicInteger subscriptions =
            new AtomicInteger();

    private final CountDownLatch atLeastTwoSubscriptions =
            new CountDownLatch(2);

    @Scheduled(initialDelay = 0L, fixedDelay = 100L)
    public Mono<Void> tick() {
        // Đếm lúc method được Spring gọi để tạo Publisher.
        methodInvocations.incrementAndGet();

        return Mono.defer(() -> {
            // Đếm lúc một subscription thực sự bắt đầu.
            subscriptions.incrementAndGet();
            atLeastTwoSubscriptions.countDown();
            return Mono.<Void>empty();
        });
    }

    public boolean awaitAtLeastTwo(
            long timeout, TimeUnit unit)
            throws InterruptedException {
        return atLeastTwoSubscriptions.await(timeout, unit);
    }

    public int methodInvocationCount() {
        return methodInvocations.get();
    }

    public int subscriptionCount() {
        return subscriptions.get();
    }
}
~~~

Nếu Spring gọi method theo mỗi tick, `methodInvocations` sẽ tăng liên tục; còn nếu nó chỉ subscribe lại vào cùng `Mono.defer`, `subscriptions` tăng mà method count giữ nguyên. Các counter dùng `AtomicInteger` vì method processing và scheduler callback có thể chạy trên thread khác nhau.

### 5.5. ReactiveScheduleDemo.java

~~~java
package demo;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import reactor.core.publisher.Mono;

public final class ReactiveScheduleDemo {

    private static void compareEagerAndLazy() {
        AtomicInteger count = new AtomicInteger();

        Mono<Integer> eager = Mono.just(count.incrementAndGet());
        System.out.println("eagerCreated=" + count.get());
        System.out.println("eagerFirst=" + eager.block());
        System.out.println("eagerSecond=" + eager.block());

        count.set(0);

        Mono<Integer> lazy = Mono.fromSupplier(count::incrementAndGet);
        System.out.println("lazyCreated=" + count.get());
        System.out.println("lazyFirst=" + lazy.block());
        System.out.println("lazySecond=" + lazy.block());
    }

    public static void main(String[] args) throws Exception {
        compareEagerAndLazy();

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             SchedulerConfig.class)) {

            ReactiveJob job = context.getBean(ReactiveJob.class);

            if (!job.awaitAtLeastTwo(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Fewer than two scheduled subscriptions");
            }

            System.out.println("methodCalledOnce="
                    + (job.methodInvocationCount() == 1));
            System.out.println("subscribedAtLeastTwice="
                    + (job.subscriptionCount() >= 2));
        }
    }
}
~~~

### 5.6. Chạy và kiểm tra

Trong thư mục `spring-reactive-schedule-demo`:

~~~bash
mvn -q compile exec:java
~~~

**Stdout từ chương trình Java trong điều kiện scheduler hoạt động bình thường** (không tính log bên ngoài):

~~~text
eagerCreated=1
eagerFirst=1
eagerSecond=1
lazyCreated=0
lazyFirst=1
lazySecond=2
methodCalledOnce=true
subscribedAtLeastTwice=true
~~~

Diễn giải:

1. `Mono.just(count.incrementAndGet())` tăng count ở assembly, nên `eagerCreated=1`. Hai lần `block()` là hai subscription nhưng đều nhận 1.
2. `Mono.fromSupplier(count::incrementAndGet)` chưa gọi supplier tại assembly, nên `lazyCreated=0`, sau đó cho 1 và 2 khi subscribe hai lần.
3. Spring xử lý `@Scheduled` trên `ReactiveJob.tick` và gọi method để tạo Mono tại registration time.
4. Mỗi lần schedule tới hạn, Spring subscribe lại vào Mono; lambda trong `Mono.defer` tăng `subscriptions`.
5. Main chỉ tiếp tục sau khi latch xác nhận ít nhất hai subscriptions. Số subscription thực tế **có thể đã vượt 2**, nên in boolean thay vì đếm chính xác.
6. `methodCalledOnce=true` xác nhận Publisher được tạo một lần trong ví dụ có một registration.
7. Đóng `AnnotationConfigApplicationContext` làm Spring dọn scheduler và active subscriptions do nó quản lý.

Chương trình không in tên thread, thời điểm từng tick hay một số đếm total không ổn định. Nếu hệ thống nghẽn đến mức chưa có hai subscriptions trong 5 giây, chương trình ném exception rõ ràng; không nên đổi timeout thành `Thread.sleep(...)` rồi đoán theo thời gian tường.

**Cẩn thận:** `Mono.block()` trong `compareEagerAndLazy()` dùng ở `main` của test độc lập để quan sát subscriptions. Không gọi `block()` bừa bãi trên Reactor event-loop/non-blocking thread của ứng dụng production.

## 6. Một Publisher không đồng nghĩa mọi lần đều tạo dữ liệu mới

### 6.1. Cold Publisher

Một nguồn cold có thể làm lại computation cho từng subscription:

~~~java
AtomicInteger count = new AtomicInteger();
Mono<Integer> cold = Mono.fromSupplier(count::incrementAndGet);

// Fragment chạy trong một test main:
// cold.block() lần đầu -> 1
// cold.block() lần hai -> 2
~~~

Cùng object `cold`, nhưng mỗi subscription chạy supplier một lần. Đây là lựa chọn thường phù hợp cho job định kỳ cần cập nhật số liệu.

### 6.2. Mono.cache giữ lại kết quả

~~~java
// Phản ví dụ khi mục tiêu là đọc mới mỗi kỳ.
@Scheduled(fixedDelay = 60_000)
public Mono<String> cached() {
    return Mono.fromCallable(this::loadFreshData).cache();
}
~~~

Spring giữ Mono object được tạo ở registration time. Sau subscription đầu tiên, `cache()` có thể giữ terminal signal; những subscription sau có thể nhận dữ liệu cũ mà không chạy lại `loadFreshData()`. Điều này không phải bug của `@Scheduled`, mà là **ngữ nghĩa cache**.

Nếu cần tạo cache chỉ nằm trong một lượt subscription, có thể tạo pipeline con trong `Mono.defer`, nhưng chỉ nên thêm cache khi thật sự có nhiều subscribers bên trong mỗi tick. Với một publisher có một subscriber trên mỗi tick, cache thường không đem lại lợi ích.

### 6.3. Flux.just và dữ liệu capture

~~~java
@Scheduled(fixedRate = 10_000)
public Flux<String> frozenEntries() {
    return Flux.just(loadA(), loadB());
}
~~~

`loadA()` và `loadB()` chạy lúc tạo Flux; những subscription sau phát lại các phần tử đã capture. Để tải lại theo từng tick, đặt các lời gọi trong deferred factory:

~~~java
@Scheduled(fixedRate = 10_000)
public Flux<String> freshEntries() {
    return Flux.defer(() ->
            Flux.just(loadA(), loadB()));
}
~~~

Đây là fragment: `loadA()`/`loadB()` là hàm ứng dụng. Nếu hai hàm này là blocking I/O, cần chuyển sang nguồn thích hợp với executor/`boundedElastic` và timeout thật.

### 6.4. Hot Publisher và nguồn vô hạn

Một hot source như một `Sinks.Many` không hứa tái phát một batch mới mỗi subscription. Subscriber mới có thể bỏ lỡ sự kiện trước đó hoặc chỉ bắt đầu nghe các sự kiện mới, tùy loại sink/replay policy.

~~~java
// Phản ví dụ cho fixedDelay nếu không có điều kiện kết thúc.
@Scheduled(fixedDelay = 5_000)
public Flux<Long> infinite() {
    return Flux.interval(Duration.ofSeconds(1));
}
~~~

Với `fixedDelay`, subscription vô hạn làm callback không đến completion, vì vậy Spring có thể **không khởi động kỳ tiếp theo**. Với fixed-rate, những subscriptions dài tồn tại có thể chồng nhau và tiêu thụ tài nguyên. Không chọn hot/infinite Publisher cho polling định kỳ chỉ vì API trả Flux.

## 7. fixedRate, fixedDelay và cron tác động đến subscription thế nào?

### 7.1. fixedRate: nhịp subscribe theo period

~~~java
@Scheduled(fixedRate = 5_000)
public Mono<Void> poll() {
    return Mono.defer(() -> remoteApi.poll());
}
~~~

Spring lên lịch những subscription theo period. Nếu một subscription hoàn tất chậm hơn period, nhất là khi đã chuyển công việc sang Reactor Scheduler khác, những subscriptions sau **có thể còn overlap** với công việc trước. Fixed rate không phải mutex đảm bảo chỉ có một remote request tại mọi thời điểm.

Muốn không overlap, cân nhắc `fixedDelay` hoặc concurrency guard/queue ở đúng ranh giới việc thật, và xác định nên **skip, chờ hay lưu backlog** khi kỳ trước chưa hoàn tất.

### 7.2. fixedDelay: chờ terminal signal

~~~java
@Scheduled(fixedDelay = 5_000)
public Mono<Void> reconcile() {
    return Mono.defer(() -> reconcileService.reconcile());
}
~~~

Theo Javadoc Spring 6.1, với reactive fixed-delay Spring **chặn subscription callback cho tới khi subscription hoàn tất** để giữ nghĩa delay sau completion. Hệ quả:

- Một publisher chạy chậm có thể giữ scheduler worker chờ lâu.
- Một `Mono.never()` hoặc `Flux.never()` có thể làm lịch không tiến.
- Nếu scheduler một worker, những scheduled callbacks khác có thể bị ảnh hưởng.
- Cần xử lý timeout và cancellation ở tầng Publisher/HTTP/DB thực.
- Dù nguồn chạy qua `Schedulers.boundedElastic()`, phần scheduling fixed-delay vẫn chờ sự hoàn tất của subscription.

Điều này không đồng nghĩa mọi mã trong reactive pipeline bị ép chạy đồng bộ; phần I/O có thể ở threads khác, nhưng callback theo fixedDelay vẫn phải quan sát terminal signal để biết khi nào bắt đầu chờ delay.

### 7.3. cron: lịch theo thời gian tường

~~~java
@Scheduled(
        cron = "0 0 2 * * *",
        zone = "Asia/Ho_Chi_Minh")
public Mono<Void> daily() {
    return Mono.defer(() -> reportService.buildDaily());
}
~~~

Nó nhắm 02:00 hằng ngày theo timezone đã khai báo. Publisher cho method này vẫn được tạo một lần, rồi được subscribe mỗi khi cron trigger được kích hoạt. Cron không phải cơ chế đảm bảo mỗi business period chỉ xử lý đúng một lần trên cluster; ba replicas có thể tạo ba subscriptions song song.

### 7.4. Scheduled method không nhận tham số

Không thể viết:

~~~java
// Sai contract: @Scheduled không tự inject tham số mỗi tick.
@Scheduled(fixedRate = 5_000)
public Mono<Void> wrong(String requestId) {
    return Mono.empty();
}
~~~

Nếu cần request ID hoặc tenant ID, xác định source hợp lệ từ job state/config/service ở subscription time, không dựa vào request ThreadLocal của caller HTTP lúc bean được tạo.

## 8. onError: Spring log WARN và tiếp tục lịch

~~~java
// Fragment: counter là AtomicInteger của bean.
@Scheduled(fixedRate = 5_000)
public Mono<Void> flaky() {
    return Mono.defer(() -> {
        int attempt = counter.incrementAndGet();

        if (attempt == 2) {
            return Mono.error(
                    new IllegalStateException("temporary-error"));
        }
        return Mono.<Void>empty();
    });
}
~~~

Lần thứ hai Publisher phát `onError`. Spring xử lý lỗi reactive đó trong subscription, log WARN và cho phép lịch tiếp tục. Nó không mặc định gọi `ErrorHandler` của Spring `TaskScheduler` cho lỗi này: `onError` là **tín hiệu reactive**, không phải exception chạy xuyên khỏi scheduled Runnable đồng bộ.

Nếu nghiệp vụ phải có retry trong từng tick, viết rõ reactive policy:

~~~java
@Scheduled(fixedRate = 5_000)
public Mono<Void> pollWithRetry() {
    return Mono.defer(() -> service.poll())
            .retryWhen(
                    Retry.backoff(2, Duration.ofMillis(200)))
            .doOnError(error -> logFailure(error))
            .onErrorResume(error -> Mono.empty());
}
~~~

Đây là fragment cần import `reactor.util.retry.Retry`, `java.time.Duration` và dependencies của ứng dụng. `onErrorResume` chuyển lỗi thành completion thành công của một subscription **khi chính sách cho phép**; đừng nuốt lỗi nghiêm trọng mà không có logging/alert và side-effect protection.

**Giới hạn:** `retryWhen` có thể giữ một tick kéo dài hơn period. Nếu operation có side effects, phải có idempotency key, retry budget và backoff; không tự retry vô hạn.

## 9. Cancellation, timeout và shutdown

Spring Reference ghi rõ khi bean bị destroy hoặc ApplicationContext đóng, hạ tầng sẽ hủy các lịch subscription tiếp theo và các subscription trước đó vẫn active.

Tuy nhiên, **cancel subscription không phải rollback tự động của HTTP request, SQL query hoặc thanh toán remote**. Với I/O đồng bộ hoặc SDK không hỗ trợ cancellation, operation thật có thể tiếp tục sau khi Reactor nhận cancellation signal.

~~~java
// Fragment: callRemote() trả Mono phù hợp.
@Scheduled(fixedRate = 10_000)
public Mono<Void> boundedWork() {
    return Mono.defer(() -> callRemote())
            .timeout(Duration.ofSeconds(3))
            .doFinally(signal -> recordTerminalSignal(signal));
}
~~~

`Mono.timeout` đặt deadline trên reactive subscription. `doFinally` quan sát terminal/cancel signal; không chứng minh remote service đã rollback side effect. Cần timeout tầng HTTP/JDBC/DB và cleanup resources thực.

### 9.1. Dùng Mono.fromCallable với blocking I/O cần bounded scheduler

~~~java
@Scheduled(fixedDelay = 10_000)
public Mono<Void> blockingDbWork() {
    return Mono.fromCallable(() -> blockingDbCall())
            .subscribeOn(Schedulers.boundedElastic())
            .then();
}
~~~

Cách này giúp tránh thực hiện blocking DB call trực tiếp trên một Reactor non-blocking worker; nhưng vẫn cần giới hạn database connections, statement timeout và xác định liệu fixedDelay đang giữ scheduler thread chờ tới terminal signal hay không.

### 9.2. Không phát sinh resource vô hạn do nhiều subscriptions

Nếu Publisher không hoàn tất và `fixedRate` tiếp tục phát subscription mới, hệ thống có thể giữ nhiều HTTP calls/subscriptions active cùng lúc. Hãy đo **in-flight count**, đặt concurrency limit, timeout, cancellation policy và đảm bảo `onError` được phân loại đúng.

Nếu business cần “mỗi kỳ tạo một bản ghi rồi trả ACK”, nên dùng một finite Publisher rõ ràng thay vì trả một `Flux.interval(...)` vô hạn.

## 10. Context propagation: đừng capture dữ liệu request một lần ở startup

~~~java
// Phản ví dụ: tenant ID có thể được đọc khi bean được đăng ký lịch.
@Scheduled(fixedDelay = 60_000)
public Mono<Void> wrongTenant() {
    String tenant = tenantHolder.currentTenant();
    return Mono.fromRunnable(() -> processForTenant(tenant));
}
~~~

`tenantHolder.currentTenant()` được đọc khi method được gọi để tạo Publisher, không phải mỗi subscription. Nếu tenant ở request ThreadLocal thì ở lúc startup có thể là `null`, hoặc giá trị không phù hợp. Kể cả với `Mono.defer`, scheduled thread không tự có request principal của HTTP caller.

Thiết kế an toàn hơn là lấy job specification/tenant list từ nguồn sự thật **ở mỗi subscription**, rồi xác định identity/authorization và transaction boundary thích hợp cho background processing:

~~~java
@Scheduled(fixedDelay = 60_000)
public Mono<Void> processTenants() {
    return Mono.defer(() ->
            tenantRepository.listActive()
                    .concatMap(tenantService::process)
                    .then());
}
~~~

Các dependencies ở đây chỉ minh họa. Không chuyển nguyên `HttpServletRequest`, JPA `EntityManager`, Spring transaction context hoặc mutable `SecurityContext` qua worker bằng ThreadLocal; những đối tượng ấy có vòng đời và ràng buộc thread riêng.

## 11. Thread safety: các subscriptions có thể dùng chung state

Một Mono object được giữ lâu có thể capture một mutable object ngay lúc assembly. Nếu hai subscriptions chồng lấn, chúng có thể thao tác cùng object mà không đồng bộ:

~~~java
// Phản ví dụ: counter không thread-safe khi subscriptions overlap.
private int counter;

@Scheduled(fixedRate = 1_000)
public Mono<Void> unsafeCount() {
    return Mono.fromRunnable(() -> counter++);
}
~~~

`AtomicInteger` có thể sửa race của bộ đếm, nhưng **không** chống một invoice bị xử lý hai lần. Nếu muốn mỗi subscription có trạng thái tạm riêng, khởi tạo state bên trong `Mono.defer`:

~~~java
// Fragment: BatchBuffer và repository là abstraction chưa định nghĩa.
@Scheduled(fixedRate = 10_000)
public Mono<Void> batch() {
    return Mono.defer(() -> {
        BatchBuffer buffer = new BatchBuffer();
        return repository.fetchPending()
                .concatMap(buffer::add)
                .then();
    });
}
~~~

Cần thiết kế backpressure/cancellation/cleanup khi bên dưới là I/O. Nếu có nhiều replicas, dùng business-period key và atomic claim/idempotency ở tầng dữ liệu; singleton bean/AtomicBoolean chỉ bảo vệ trong phạm vi một JVM.

## 12. Theo dõi đúng số lần: method, subscription và nguồn thực

| Metric | Đo cái gì? | Vì sao cần? |
| --- | --- | --- |
| Method invocation count | Lúc Spring tạo Publisher khi đăng ký | Xác nhận không kỳ vọng method gọi lại mỗi tick |
| Subscription count | Lúc Publisher nhận subscribe theo lịch | Thấy nhịp lịch thật |
| Source execution count | Lúc Supplier/defer/Callable thực sự chạy | Phát hiện cache/hot source |
| Terminal count | onComplete, onError, cancel | Thấy lifecycle của subscription |
| In-flight subscriptions | Số subscription còn active | Phát hiện overlap hoặc leak |
| Underlying operation duration | Thời gian HTTP/DB thực chạy | Không bị che bởi timeout/cancel |
| Missed/duplicate business periods | Những kỳ bỏ lỡ hoặc xử lý trùng | Đo correctness, không chỉ throughput |

Ví dụ instrument:

~~~java
// Fragment: metrics là dependency đã có.
return Mono.defer(() -> {
    metrics.increment("subscribe-started");
    return service.doWork()
            .doOnError(ex -> metrics.increment("subscribe-errors"))
            .doFinally(signal -> metrics.increment("subscribe-finished"));
});
~~~

`doFinally` báo terminal signal của pipeline, không chứng minh một remote side effect đã hoàn tất/rollback nếu pipeline kết thúc do timeout hoặc cancel. Đừng log token/credentials hoặc nguyên request object để debug.

### 12.1. Test bằng latch, tránh đoán lịch

Bài demo phần 5 dùng hai counter và `CountDownLatch(2)` để đợi hai subscriptions. Không dùng `Thread.sleep(200)` rồi assert “chính xác hai subscriptions”, vì executor scheduling có thể bị chậm hoặc tick thứ ba đã tới trước khi test đọc biến.

### 12.2. Kiểm thử các trường hợp đáng chú ý

| Test | Kỳ vọng |
| --- | --- |
| `Mono.just(calculate())` được subscribe hai lần | `calculate()` chỉ được gọi khi assembly |
| `Mono.fromSupplier(calculate)` được subscribe hai lần | Supplier chạy hai lần |
| Spring reactive method có một `@Scheduled` | Method được gọi để tạo Publisher một lần |
| Scheduler tiếp tục ít nhất hai tick | Publisher nhận ít nhất hai subscriptions |
| Một tick phát `onError` | Lịch tiếp tục, WARN log, ErrorHandler đồng bộ không trực tiếp nhận |
| Publisher infinite với fixedDelay | Không có lần subscribe sau cho đến khi subscription trước kết thúc |
| Context shutdown trong khi Publisher active | Subscription được cancel theo contract Spring |
| Mono.cache sau tick đầu | Có thể trả lại cached terminal signal cho tick tiếp |
| Hai schedules trên một method | Hai registrations độc lập, kiểm tra số invocation/subscribe đúng phạm vi |
| Ba replicas cùng cron | Có thể có ba subscriptions, cần distributed coordination |
| JDBC blocking trong lazy source | Đặt trên worker phù hợp, timeout và connection limit |
| Retry trên side-effect operation | Có idempotency và hạn mức retry |

## 13. Sai lầm thường gặp

1. **“Spring gọi lại method Mono mỗi 5 giây.”** Không đúng contract reactive `@Scheduled`: Publisher lấy một lần, subscribe nhiều lần.
2. **“Cùng một Mono nghĩa là tác vụ chạy duy nhất một lần.”** Không nhất thiết; cold Mono có thể làm lại theo mỗi subscription.
3. **“Mono.just(service.fetch()) sẽ fetch mới ở mỗi tick.”** Sai; `fetch` được gọi khi tạo Mono.
4. **“Mono.fromSupplier luôn chạy background.”** Sai; nó chỉ hoãn computation, không tự chuyển executor.
5. **“Mono.defer là operator đồng bộ eager.”** Sai; factory chạy ở subscription time.
6. **“Mọi Publisher được Spring subscribe nhiều lần đều tự phát dữ liệu mới.”** Sai với cached/hot/được tạo từ Future sẵn có.
7. **“CompletableFuture là kiểu trả trực tiếp phù hợp cho reactive @Scheduled.”** Không theo deferred adapter requirement của Spring 6.1.
8. **“Spring dùng onNext của Flux làm kết quả cuối cùng.”** Sai, các onNext bị bỏ qua.
9. **“onError làm tất cả những lần sau dừng.”** Spring reactive scheduling log WARN và tiếp tục lịch.
10. **“ErrorHandler của TaskScheduler bắt được mọi onError.”** Sai; reactive error nằm trong subscription protocol.
11. **“fixedDelay không đợi reactive Publisher hoàn tất.”** Sai trong Spring 6.1; subscription bị chờ để giữ nghĩa fixed delay.
12. **“Mono.never dùng fixedDelay vẫn sẽ chạy tiếp sau 5 giây.”** Sai; chưa có completion để tính delay.
13. **“fixedRate không bao giờ overlap.”** Không đúng đối với những pipeline async subscriptions còn active.
14. **“subscribeOn là cách cấu hình ThreadPoolTaskScheduler.”** Sai; đó là Reactor scheduling boundary.
15. **“Dùng Mono là JDBC/HTTP SDK tự non-blocking.”** Sai; lazy/blocking vẫn giữ thread khi chạy.
16. **“Đóng context chắc chắn remote transaction rollback.”** Sai; cancellation có giới hạn.
17. **“Một Spring bean scheduled đồng nghĩa chỉ một lịch trong toàn cluster.”** Sai khi có nhiều instances.
18. **“Tạo state trước defer thì state sẽ luôn mới.”** Sai; state tạo tại assembly được capture lặp lại.
19. **“Có onComplete là dữ liệu chắc chắn mới.”** Sai; một cached Mono có thể complete thành công với dữ liệu cũ.
20. **“Không có onNext tức là method không làm gì.”** Sai; `Mono<Void>` có thể làm side effect và chỉ phát terminal signal.

## 14. Checklist ứng dụng

- [ ] Đúng Spring 6.1+ và dependencies Reactor/reactive adapter cần thiết.
- [ ] Xác nhận một registration giữ một Publisher và subscribe theo lịch.
- [ ] Method không nhận tham số, Publisher hỗ trợ nhiều subscriptions.
- [ ] Mọi thao tác phải diễn ra theo từng tick đều nằm trong lazy/deferred source.
- [ ] Không dùng `Mono.just(expensiveCall())` cho dữ liệu cần cập nhật theo tick.
- [ ] Rà soát `cache`, hot source, existing Future và shared mutable state.
- [ ] Không kỳ vọng `onNext` được Spring trả về; xử lý trong pipeline/metrics.
- [ ] Có chính sách `onError`, retry và alert rõ; không tin ErrorHandler đồng bộ.
- [ ] Nếu `fixedDelay`, nguồn hoàn tất hữu hạn và timeout được cân nhắc.
- [ ] Nếu `fixedRate`/cron, đo in-flight subscriptions và ngăn overlap sai nghiệp vụ.
- [ ] I/O blocking được đặt trên executor phù hợp, có timeout thật.
- [ ] Tenant/security/transaction có scope phù hợp cho background job, không chụp ThreadLocal request khi init.
- [ ] Business side effects có idempotency key, atomic claim, retry budget.
- [ ] Có monitoring method count, subscribe count, source count và terminal count.
- [ ] Dùng latch thay `Thread.sleep` cho test số subscriptions.
- [ ] Cleanup khi bean/context destroy; hiểu cancellation không phải remote rollback.
- [ ] Đánh giá nhiều replicas, cron time zone và missed runs.
- [ ] Nếu nhiều `@Scheduled` trên cùng method, phân tích từng registration riêng.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** Reactive method `@Scheduled` được Spring gọi mỗi tick hay chỉ để lấy Publisher?  
**Đáp án:** Với mỗi registration, Spring lấy Publisher một lần và subscribe lại theo lịch.

**Câu 2.** `Mono.just(counter.incrementAndGet())` có tăng counter mỗi subscription không?  
**Đáp án:** Không. Counter tăng khi assembly do argument được tính trước khi gọi `just`.

**Câu 3.** `Mono.fromSupplier(counter::incrementAndGet)` thay đổi gì?  
**Đáp án:** Supplier được gọi mỗi subscription, nên counter có thể tăng ở mỗi tick.

**Câu 4.** Vì sao `Mono.defer(() -> service.createPublisher())` hữu ích?  
**Đáp án:** Nó dời việc tạo Publisher con đến subscription time, giúp tạo operation/state mới.

**Câu 5.** Một Publisher object được subscribe hai lần có nhất thiết cùng output không?  
**Đáp án:** Không; phụ thuộc cold/hot/cache và logic nguồn. `fromSupplier` có thể phát hai giá trị khác nhau.

**Câu 6.** `CompletableFuture` được Spring chấp nhận trực tiếp như kiểu reactive scheduled return không?  
**Đáp án:** Không với adapter không deferred của nó; phải dùng Publisher phù hợp nếu muốn re-subscribe theo lịch.

**Câu 7.** `Flux<T>` trong scheduled method phát `onNext` thì ai nhận giá trị?  
**Đáp án:** Spring bỏ qua những values; ứng dụng cần xử lý chúng trong pipeline.

**Câu 8.** Một subscription `onError` có chấm dứt chuỗi lịch không?  
**Đáp án:** Spring reactive infrastructure log WARN rồi tiếp tục các subscriptions sau theo lịch.

**Câu 9.** Vì sao `ErrorHandler` của scheduler không đủ?  
**Đáp án:** `onError` là tín hiệu của Publisher, không phải exception ném khỏi synchronous Runnable.

**Câu 10.** `fixedDelay` với `Mono.never()` có tiếp tục tick không?  
**Đáp án:** Không trong điều kiện subscription chưa hoàn tất; Spring chờ completion để áp delay.

**Câu 11.** Vì sao fixedRate có thể gây overlap dù scheduler pool size 1?  
**Đáp án:** Subscription có thể kích hoạt I/O async chạy ở threads khác và còn active khi tới tick sau.

**Câu 12.** Đóng context có hủy được HTTP/SQL remote side effect đã thực hiện không?  
**Đáp án:** Không bảo đảm; subscription cancellation phải được kết hợp với cancellation/timeout của client và idempotency.

**Câu 13.** Test đáng tin để so sánh method calls và subscriptions là gì?  
**Đáp án:** Hai counter đặt ở hai giai đoạn, `CountDownLatch` đợi hai subscriptions, rồi so sánh method count=1 và subscription count>=2.

**Câu 14.** Một reactive scheduled job chạy trên ba replicas có tự chạy đúng một lần trên toàn cụm không?  
**Đáp án:** Không. Mỗi context đăng ký lịch; cần distributed coordination và dedup.

## 16. Tài liệu chính thức

- [Spring Framework 6.1.3 — Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): Publisher được lấy một lần, nhiều subscriptions, onNext/onError, fixedDelay và lifecycle.
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): Reactive `@Scheduled` từ Spring 6.1, `ReactiveAdapterRegistry`, deferred requirement, WARN và hủy subscription.
- [Spring Framework 6.1.3 — EnableScheduling Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html): cơ chế đăng ký lịch qua ApplicationContext.
- [Spring Framework 6.1.3 — ReactiveAdapterRegistry Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/core/ReactiveAdapterRegistry.html): chuyển wrapper reactive và kiểm tra adapter.
- [Project Reactor Core 3.6.2 — Mono API](https://projectreactor.io/docs/core/3.6.2/api/reactor/core/publisher/Mono.html): `just`, `fromSupplier`, `fromCallable`, `defer`, `cache`, `timeout`.
- [Project Reactor Reference — Hot Versus Cold](https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html): thời điểm assembly và subscription, cold/hot publishers.
- [Project Reactor Reference — Schedulers](https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html): `boundedElastic` và `subscribeOn`.
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): Future computation và khác biệt với deferred re-execution.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-scheduling-003">Dùng defer để tạo công việc mới ở mỗi lần đăng ký nhận</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng defer để tạo công việc mới ở mỗi lần đăng ký nhận

**Trong Spring Framework 6.1+, một phương thức có @Scheduled trả về Publisher được gọi một lần để lấy Publisher; ở mỗi lượt lịch, Spring đăng ký nhận (subscribe) lại trên Publisher đó.** Đây là điểm khác rất lớn so với cách người học thường hình dung một phương thức @Scheduled thông thường được gọi lại tại mỗi tick.

Vì vậy, nếu method xây Publisher theo cách **thực hiện công việc ngay khi lắp ghép**, ví dụ **Mono.just(loadLatestData())**, thao tác loadLatestData có thể chạy chỉ một lần khi Spring khởi tạo lịch. Những lần subscribe sau chủ yếu nhìn lại giá trị đã được chụp. Để mỗi subscription thực sự khởi động **một công việc mới**, hãy dùng các nguồn lazy đúng nghĩa, đặc biệt **Mono.defer(() -> ...)** hoặc **Flux.defer(() -> ...)** khi cần tạo Publisher mới theo từng lượt.

Bài Knowledge thuộc nhóm **Lập lịch reactive** của **Spring Framework - concurrency**, với chủ đề **Dùng defer để tạo công việc mới ở mỗi lần đăng ký nhận**. Nội dung trình bày cơ chế assembly/subscription, sự khác nhau giữa Mono.just và Mono.defer, ví dụ Maven Java 17 hoàn chỉnh, ảnh hưởng của lịch fixedDelay/fixedRate/cron, xử lý blocking I/O, context, lỗi, cancellation, kiểm thử và các bẫy vận hành.

## 1. Mô hình thật của @Scheduled reactive

Spring Framework 6.1+ **lấy một Publisher lúc đăng ký** rồi subscribe lại nó theo lịch. Công việc của mỗi lần subscription có thể độc lập nếu nguồn cold/deferred; Publisher hot hoặc cached có thể phát lại kết quả cũ. Một lỗi `onError` chỉ kết thúc subscription hiện tại, không tự hủy toàn bộ lịch.

~~~text
Khởi tạo: @Scheduled method -> Publisher P
Tick #1: subscribe(P) -> defer factory -> operation #1
Tick #2: subscribe(P) -> defer factory -> operation #2
~~~

Bài này tập trung vào **đặt factory trì hoãn ở đâu** để một tick xây pipeline, jobId, input và resource mới, thay vì giải thích lại mọi loại schedule. `Mono.fromCallable` hoặc `fromSupplier` đã lazy cho một hành động đơn lẻ; dùng `defer` khi thật sự cần tạo hoặc chọn cả Publisher con ở mỗi lần subscribe.

## 2. Phân biệt assembly, subscription, execution và completion

Trong Reactor, một chuỗi operator có thể được tạo mà chưa chạy tác vụ nguồn. Cần xác định bốn thời điểm:

| Sự kiện | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| **Assembly** | Tạo đối tượng Publisher và mô tả pipeline | Gọi Mono.defer(...) |
| **Subscription** | Một subscriber mới đăng ký nhận, nguồn được khởi động theo contract | Spring gọi subscribe() ở một tick |
| **Execution** | Logic nguồn/callback chạy, có thể qua scheduler Reactor | Callable, Runnable, HTTP request |
| **Termination** | Subscription kết thúc bình thường, lỗi hoặc bị cancel | onComplete/onError/cancel |

Một API có chữ **Mono** trong tên chưa đủ để kết luận phần công việc thực sự lazy:

~~~java
Mono<String> eager = Mono.just(loadValue());

Mono<String> lazy = Mono.fromCallable(() -> loadValue());

Mono<String> deferred = Mono.defer(
        () -> Mono.fromCallable(() -> loadValue()));
~~~

Đây là **đoạn minh họa**, giả sử có method loadValue() trả String.

- Ở dòng **eager**, Java phải tính đối số **loadValue()** trước khi gọi Mono.just. Do đó, việc tải dữ liệu đã xảy ra trong pha assembly.
- Với **fromCallable**, loadValue chỉ được gọi khi có subscription; bản thân nó đã là nguồn lazy, không nhất thiết cần bọc thêm defer.
- Với **defer**, supplier tạo **Publisher mới** cho mỗi subscription. Cách này đặc biệt phù hợp khi mỗi lượt cần tạo chuỗi thao tác mới, chọn nhánh, chụp thời điểm hiện tại hoặc khởi tạo một nguồn khác.

**Mono.defer không làm mọi dòng Java trong method bao ngoài trở thành lazy.** Nếu method @Scheduled vẫn gọi một API trước khi return Mono.defer, lời gọi đó vẫn xảy ra ở pha assembly.

## 3. Anti-pattern: Mono.just chụp kết quả quá sớm

Xét scheduled method:

~~~java
@Scheduled(fixedRate = 5_000)
public Mono<String> report() {
    String result = repository.loadLatestSnapshot();
    return Mono.just(result);
}
~~~

Đây là **đoạn minh họa** trong bean Spring đã có repository.

Với @Scheduled reactive, Spring gọi report() một lần để lấy Publisher. Ngay khi đó:

1. repository.loadLatestSnapshot() được gọi.
2. result chứa dữ liệu tại thời điểm đăng ký lịch.
3. Mono.just(result) giữ lại giá trị này.
4. Những tick sau subscribe vào cùng Mono và nhận cùng giá trị, không tự chạy lại repository.loadLatestSnapshot().

Nếu repository trả dữ liệu của ngày hôm trước, báo cáo định kỳ có thể tiếp tục dùng giá trị cũ trong mọi tick. Vấn đề không phải “Reactor không hoạt động” mà là **side effect đã bị đặt sai pha**.

### Sửa bằng Mono.defer

~~~java
@Scheduled(fixedRate = 5_000)
public Mono<String> report() {
    return Mono.defer(
            () -> Mono.fromCallable(
                    () -> repository.loadLatestSnapshot()));
}
~~~

Đoạn trên chỉ là **minh họa**, chưa xử lý sự khác nhau giữa API DB blocking và nonblocking. Nhưng sự thay đổi có ý nghĩa rõ ràng: mỗi lần Spring subscribe, defer tạo một Publisher mới, và Callable được thực thi theo mỗi subscription.

**Quan trọng:** nếu repository.loadLatestSnapshot() là API **blocking**, code trên có thể chạy blocking ngay trên thread thực hiện subscribe. Trong production phải cân nhắc **Schedulers.boundedElastic()** cho blocking I/O hoặc dùng reactive driver có nguồn nonblocking đúng nghĩa. Phần sau giải thích cách thực hiện.

### Khi nào Mono.just không sai?

Mono.just rất hợp lý khi muốn phát ra **một giá trị bất biến cố định** hoặc đã được tính sẵn một cách có chủ đích. Ví dụ lịch chỉ cần gửi một mã cấu hình không đổi:

~~~java
Mono<String> constant = Mono.just("ACTIVE");
~~~

Không phải mọi Mono.just đều lỗi. **Nó sai khi yêu cầu nghiệp vụ là tái thực hiện việc tính/đọc dữ liệu vào mỗi tick**, nhưng code lại tính giá trị chỉ lúc tạo Publisher.

## 4. Mono.defer thực sự làm gì?

Reactor định nghĩa ý tưởng:

~~~java
Mono<T> deferred = Mono.defer(
        () -> createPublisherForThisSubscription());
~~~

Đây là **pseudocode Java** với T và createPublisherForThisSubscription được định nghĩa ở ứng dụng.

Khi có một subscriber mới:

1. Reactor gọi Supplier được truyền vào Mono.defer.
2. Supplier tạo và trả về một **Mono mới**.
3. Subscriber được kết nối đến Mono mới đó.
4. Các tín hiệu onNext/onComplete/onError của Mono mới được truyền theo chuỗi.
5. Một lần subscribe khác chạy lại từ bước 1.

Vì supplier được gọi ở mỗi lần subscribe, bạn có thể:

- Lấy **thời gian hiện tại**.
- Tạo một **command/jobId** mới.
- Đọc cấu hình mới nhất theo chính sách nghiệp vụ.
- Chọn nhánh dựa trên trạng thái mới.
- Khởi tạo **client request Publisher** mới.
- Tạo state riêng cho lần xử lý.
- Đếm và gắn nhãn mỗi subscription phục vụ tracing/test.

Ví dụ:

~~~java
Mono<String> fresh = Mono.defer(() -> {
    String jobId = UUID.randomUUID().toString();
    return Mono.just(jobId);
});
~~~

Đây là **đoạn minh họa** với java.util.UUID được import. Hai lần subscribe sẽ tạo hai UUID riêng (khả năng trùng là cực nhỏ), khác với việc tạo UUID **bên ngoài defer** rồi đưa nó vào Mono.just.

### defer không mặc định chọn thread mới

**Mono.defer là thao tác trì hoãn việc tạo Publisher, không phải một executor/scheduler.** Supplier được gọi trong quá trình subscription theo thread model đang áp dụng. Nếu Spring scheduler thread subscribe vào một Mono có source đồng bộ, logic bên trong có thể chạy ngay trên **scheduler thread**.

~~~java
Mono<Void> blocking = Mono.defer(
        () -> Mono.<Void>fromRunnable(this::blockingWork));
~~~

Đây là **đoạn minh họa**. Nếu blockingWork giữ thread 10 giây mà không có Scheduler khác, chỉ thêm defer không làm blockingWork chạy trên thread riêng. Cần hiểu ranh giới **lazy vs async** trước khi chọn cấu hình.

## 5. Mono.defer, Mono.fromSupplier, Mono.fromCallable và Mono.fromRunnable

Những công cụ này liên quan nhưng không đồng nhất:

| API | Được tạo ở assembly | Chạy tại subscription | Dùng khi |
| --- | --- | --- | --- |
| **Mono.just(value)** | Giữ ngay value đã được tính | Phát lại value đó | Giá trị đã có, bất biến hoặc cố định |
| **Mono.fromSupplier(fn)** | Giữ Supplier | Gọi fn để tạo giá trị | Tính giá trị bằng hàm đồng bộ mỗi subscription |
| **Mono.fromCallable(fn)** | Giữ Callable | Gọi fn, có thể throw checked exception | Gọi hàm đồng bộ có thể lỗi |
| **Mono.fromRunnable(fn)** | Giữ Runnable | Chạy fn rồi complete rỗng | Side effect không cần trả dữ liệu |
| **Mono.defer(factory)** | Giữ factory của một Publisher | Gọi factory để lấy **Mono mới** | Cần khởi tạo/chọn pipeline mới theo từng subscription |
| **Flux.defer(factory)** | Giữ factory của Flux/Publisher | Tạo Flux mới theo từng subscription | Công việc phát nhiều phần tử |
| **Mono.deferContextual(fn)** | Giữ factory cần Reactor Context | Đọc ContextView hiện có tại subscription | Khi data của pipeline nằm trong Reactor Context |

**Nếu tác vụ chỉ là một hàm đồng bộ có đầu ra T**, fromSupplier/fromCallable có thể là lời giải ngắn nhất. Không nên bọc thêm defer chỉ để tăng số operator khi source đã lazy và re-subscribe được.

**Nếu mỗi lượt cần xây cả một pipeline mới**, đặc biệt pipeline gồm nhiều calls/conditions, defer giúp đưa công việc **tạo pipeline** vào ranh giới subscription.

### Đoạn mẫu cho từng nhu cầu

**Tính toán giá trị mới mỗi lượt:**

~~~java
Mono<Long> value =
        Mono.fromSupplier(System::nanoTime);
~~~

**Gọi hàm có checked exception:**

~~~java
Mono<String> fromFile =
        Mono.fromCallable(() -> Files.readString(path));
~~~

Đây là **đoạn minh họa** có path và java.nio.file.Files. Files.readString là blocking I/O; trong một pipeline nonblocking thực tế phải chuyển sang nguồn thích hợp hoặc boundedElastic.

**Chạy một việc không phát giá trị:**

~~~java
Mono<Void> task =
        Mono.<Void>fromRunnable(() -> recordHeartbeat());
~~~

**Chọn nguồn mới ở mỗi subscription:**

~~~java
Mono<String> selected = Mono.defer(() ->
        featureEnabled()
                ? fetchFromPrimary()
                : fetchFromFallback());
~~~

Các method domain trong snippet cuối trả Mono<String>; không phải file Java hoàn chỉnh. Điều quan trọng là **featureEnabled** được đánh giá theo từng subscription, không chỉ khi tạo selected.

## 6. Cold, hot, cached và per-subscription work

**Một Publisher được dùng lại** chưa chắc gây lỗi. Reactor phân biệt các nguồn **cold** và **hot**:

- **Cold Publisher** thường tạo quá trình phát tín hiệu riêng cho từng subscriber. Một Publisher cold được giữ lâu vẫn có thể thực hiện công việc mới mỗi subscription.
- **Hot Publisher** có thể phát dữ liệu độc lập với subscription; subscriber đến muộn có thể bỏ lỡ sự kiện hoặc nhìn thấy trạng thái dùng chung.
- **Cached/Replay Publisher** có thể giữ kết quả/lỗi để tái sử dụng, thay vì thực thi nguồn lại.

Ví dụ:

~~~java
Mono<String> cold = Mono.fromCallable(this::loadNow);

Mono<String> cached = Mono.fromCallable(this::loadNow)
        .cache();
~~~

Cả hai đoạn là **minh họa** với loadNow đã định nghĩa. Mono cold gọi loadNow mỗi subscription, còn Mono.cache có thể trả lại tín hiệu đã lưu cho những subscription sau theo chính sách cache.

### cache ở đâu quyết định cái gì được tạo lại

~~~java
Mono<String> cachedAcrossTicks =
        Mono.defer(() -> loadFreshMono()).cache();

Mono<String> cacheOnlyWithinTick =
        Mono.defer(() -> loadFreshMono().cache());
~~~

Đây là **đoạn minh họa**, giả sử loadFreshMono() trả Mono<String>.

- **cachedAcrossTicks**: cache đặt **ngoài defer** áp dụng cho **Publisher dùng chung**; sau lần đầu hoàn tất, các tick khác có thể nhận kết quả đã cache.
- **cacheOnlyWithinTick**: mỗi subscription của outer defer tạo **một Mono mới kèm cache riêng**; cache đó chỉ thuộc Publisher mới của lượt ấy, trừ khi ứng dụng cố ý chia sẻ lại object được tạo bên trong.
- Nếu loadFreshMono() bản thân trả **một nguồn hot/shared singleton**, bọc defer **không tự biến nguồn đó thành cold**. Cần sửa ownership của nguồn.

**Bài học:** defer đảm bảo **factory được gọi mỗi subscription**. Nó **không bảo đảm** rằng tài nguyên hoặc Publisher do factory trả về thực sự mới, không chia sẻ, hay không cache. Bạn phải kiểm tra logic bên trong factory.

## 7. Ví dụ chạy được: per-subscription khác eager, rồi thử @Scheduled thật

Ví dụ dưới đây gồm **một dự án Maven Java 17** với **Spring Context 6.2.12** và **Reactor Core 3.7.12**.

Chương trình thực hiện hai phần:

1. Gọi cùng Publisher bằng **block() hai lần** để thấy Mono.just giữ giá trị sớm còn Mono.defer tạo giá trị mới.
2. Khởi tạo Spring ApplicationContext có một bean với **@Scheduled trả Mono<Void>**. Bean method tăng counter assembly một lần; mỗi subscription do Spring kích hoạt tăng một counter khác. **CountDownLatch** đợi ba lượt được quan sát, rồi in các assert dưới dạng boolean xác định, tránh dựa vào tốc độ CPU/đếm exact số tick khi context còn sống.

Cấu trúc:

~~~text
reactive-scheduled-defer/
├── pom.xml
└── src/main/java/example/ReactiveScheduledDeferDemo.java
~~~

**pom.xml:**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>reactive-scheduled-defer</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.12</version>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
            <version>3.7.12</version>
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
                <version>3.5.1</version>
                <configuration>
                    <mainClass>example.ReactiveScheduledDeferDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/example/ReactiveScheduledDeferDemo.java:**

~~~java
package example;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import reactor.core.publisher.Mono;

public class ReactiveScheduledDeferDemo {

    private static void showAssemblyVersusSubscription() {
        AtomicInteger eagerCalls = new AtomicInteger();
        AtomicInteger deferredCalls = new AtomicInteger();

        Mono<String> eager =
                Mono.just("value-" + eagerCalls.incrementAndGet());

        Mono<String> deferred =
                Mono.defer(() -> Mono.just(
                        "value-" + deferredCalls.incrementAndGet()));

        System.out.println("Sau assembly: eager="
                + eagerCalls.get() + ", defer=" + deferredCalls.get());

        System.out.println("Eager lần 1: " + eager.block());
        System.out.println("Eager lần 2: " + eager.block());

        System.out.println("Defer lần 1: " + deferred.block());
        System.out.println("Defer lần 2: " + deferred.block());

        System.out.println("Sau subscribe: eager="
                + eagerCalls.get() + ", defer=" + deferredCalls.get());
    }

    public static class ReactiveJob {
        private final AtomicInteger assemblyCalls = new AtomicInteger();
        private final AtomicInteger subscriptionCalls = new AtomicInteger();
        private final CountDownLatch firstThree = new CountDownLatch(3);
        private final Set<Integer> firstThreeIds =
                new ConcurrentSkipListSet<>();

        @Scheduled(initialDelay = 0, fixedDelay = 50)
        public Mono<Void> tick() {
            // Spring gọi method này một lần để lấy Publisher.
            assemblyCalls.incrementAndGet();

            // Supplier và Runnable chỉ chạy khi subscribe.
            return Mono.defer(() -> Mono.<Void>fromRunnable(() -> {
                int id = subscriptionCalls.incrementAndGet();
                if (id <= 3) {
                    firstThreeIds.add(id);
                    firstThree.countDown();
                }
            }));
        }
    }

    @Configuration
    @EnableScheduling
    public static class Config {

        @Bean("taskScheduler")
        public ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("scheduled-");
            return scheduler;
        }

        @Bean
        public ReactiveJob reactiveJob() {
            return new ReactiveJob();
        }
    }

    public static void main(String[] args) throws Exception {
        showAssemblyVersusSubscription();

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            ReactiveJob job = context.getBean(ReactiveJob.class);

            if (!job.firstThree.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Chưa có đủ 3 scheduled subscriptions");
            }

            System.out.println("Spring tạo Publisher đúng một lần: "
                    + (job.assemblyCalls.get() == 1));

            System.out.println("Có ít nhất 3 lần subscribe: "
                    + (job.subscriptionCalls.get() >= 3));

            System.out.println("Từng subscription có công việc mới: "
                    + job.firstThreeIds.containsAll(
                            Set.of(1, 2, 3)));
        }
    }
}
~~~

Chạy:

~~~bash
mvn -q compile exec:java
~~~

**Stdout dự kiến**, không tính log Maven/Spring và với điều kiện CI có đủ tài nguyên để có ba lượt scheduled trong timeout 10 giây:

~~~text
Sau assembly: eager=1, defer=0
Eager lần 1: value-1
Eager lần 2: value-1
Defer lần 1: value-1
Defer lần 2: value-2
Sau subscribe: eager=1, defer=2
Spring tạo Publisher đúng một lần: true
Có ít nhất 3 lần subscribe: true
Từng subscription có công việc mới: true
~~~

**Phân tích chính xác:**

1. **Mono.just** nhận kết quả của eagerCalls.incrementAndGet() ngay khi tạo Mono; sau đó cả hai lần block đều đọc value-1.
2. Supplier của **Mono.defer** chỉ gọi khi deferred.block() tạo subscription, nên hai lần cho value-1 và value-2.
3. @EnableScheduling đăng ký hạ tầng scheduling; bean ReactiveJob được tạo đúng một lần bởi Config.
4. Spring gọi **ReactiveJob.tick()** trong quá trình chuẩn bị scheduled Publisher; vì thế assemblyCalls chỉ bằng 1 cho một registration.
5. Spring cứ sau khoảng fixedDelay lại tạo subscription mới trên Publisher đó. Supplier trong defer tạo một Mono.fromRunnable mới.
6. Mỗi Runnable tăng subscriptionCalls và ghi nhận ID đầu tiên 1, 2, 3 trước khi countDown. Latch chỉ xuống 0 sau khi ba công việc riêng được chạy.
7. Main **không in chính xác subscriptionCalls == 3** vì scheduler có thể kịp bắt đầu tick thứ tư trước khi ApplicationContext đóng. Điều được assert là **ít nhất ba** và cả ba ID đầu tiên có mặt.
8. **ThreadPoolTaskScheduler** chỉ có một thread để ví dụ đơn giản. Không thể suy từ ví dụ rằng defer luôn tự tạo một thread mới; Runnable đồng bộ có thể chạy ngay trên scheduler thread.
9. Đóng ApplicationContext dừng các scheduled registrations theo lifecycle Spring. Ví dụ không có business I/O nên các subscriptions hoàn thành nhanh.

**Lưu ý về hướng dẫn biên dịch:** cần dùng Maven vì Spring/Reactor không nằm trong JDK thuần; hai dependencies được khai báo rõ. Với Java 17 và các phiên bản minh họa, mã được thiết kế để biên dịch/chạy được mà không cần web server, DB hoặc tài khoản người dùng.

## 8. Chỉ đặt defer ở đúng ranh giới thôi chưa đủ

Xét ba cách:

**Sai khi một tác vụ được thực hiện sớm:**

~~~java
@Scheduled(fixedRate = 10_000)
public Mono<Void> cleanup() {
    cleanupRepository.deleteExpiredNow();
    return Mono.empty();
}
~~~

Nếu deleteExpiredNow là lời gọi đồng bộ, nó chạy trong quá trình Spring tạo Publisher, không phải mỗi tick.

**Đúng về thời điểm nhưng còn thiếu cấu hình nếu blocking:**

~~~java
@Scheduled(fixedRate = 10_000)
public Mono<Void> cleanup() {
    return Mono.defer(() ->
            Mono.<Void>fromRunnable(
                    () -> cleanupRepository.deleteExpiredNow()));
}
~~~

Ở đây công việc được đưa vào Runnable của mỗi subscription, nhưng một lời gọi blocking vẫn có thể giữ scheduler thread.

**Một phiên bản có chủ đích cho blocking I/O:**

~~~java
@Scheduled(fixedRate = 10_000)
public Mono<Void> cleanup() {
    return Mono.defer(() ->
            Mono.fromCallable(() ->
                    cleanupRepository.deleteExpiredNow()))
        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
        .then();
}
~~~

Các đoạn trên là **method minh họa trong Spring bean**, chưa bao gồm repository và dependency injection. Phần cuối dùng boundedElastic để chuyển nguồn blocking sang scheduler Reactor phù hợp hơn. Đây là lựa chọn thực dụng cho một API đồng bộ; không phải sự thay thế cho timeout DB, cancellation hợp tác, giới hạn connection pool hoặc năng lực downstream.

### Vì sao dùng then()?

Nếu deleteExpiredNow trả về số bản ghi đã xóa, **Mono.fromCallable** có thể phát ra Integer. Nhưng @Scheduled reactive **bỏ qua các onNext values**. Dùng **then()** làm rõ ý định: chỉ cần đợi nguồn hoàn tất/lỗi, không cần phát kết quả cho scheduler.

Nếu muốn lưu số bản ghi để monitoring, đặt **doOnNext(n -> metrics.record(n))** hoặc cập nhật job status trong pipeline **trước then()**, chứ không hy vọng scheduler tự lưu giá trị được phát.

## 9. Mono.defer không biến một Publisher dùng chung thành độc lập

Ví dụ tạo một nguồn **hot/cached/shared** bên ngoài:

~~~java
Mono<String> shared =
        remoteClient.loadOnce().cache();

@Scheduled(fixedRate = 5_000)
public Mono<String> poll() {
    return Mono.defer(() -> shared);
}
~~~

Đây là **đoạn minh họa**. Mặc dù defer supplier được gọi mỗi lần, supplier luôn trả **cùng object shared**. Nếu shared đã cache dữ liệu lần đầu, các tick sau có thể lấy kết quả cached, không chạy remote call mới.

Sửa bằng cách đưa factory đúng vị trí:

~~~java
@Scheduled(fixedRate = 5_000)
public Mono<String> poll() {
    return Mono.defer(() -> remoteClient.loadOnce());
}
~~~

Giả sử **remoteClient.loadOnce()** thực sự trả một cold Publisher cho mỗi invocation hoặc một nguồn được tạo lại hợp lệ. Nếu client method bản thân trả về một singleton cached publisher thì cần chỉnh client.

**Không suy ra rằng mọi WebClient Mono đều phải bọc defer**: nhiều Reactor HTTP client publishers vốn đã **cold và re-subscribe được**; mỗi subscription có thể kích hoạt request mới. Defer hữu ích khi phải **xây lại request hoặc lấy input động theo từng tick**. Cần test client thực tế thay vì thêm operator một cách máy móc.

## 10. fixedRate, fixedDelay và cron với Publisher được subscribe lặp lại

Dù bạn dùng Mono.defer, **cơ chế thời gian của @Scheduled vẫn thuộc về TaskScheduler**.

| Lịch | Cách hoạt động ở tầng reactive |
| --- | --- |
| **fixedRate** | Scheduler cố kích hoạt những lượt subscribe theo chu kỳ; nếu subscription trước chưa hoàn tất, các subscription có thể chồng lấn tùy dạng publisher/scheduler |
| **fixedDelay** | Spring **chặn trong scheduled Runnable đến khi subscription hoàn tất** để tôn trọng delay sau completion; cần tránh Publisher không bao giờ complete |
| **cron** | Scheduler chọn mốc theo cron/TriggerContext; mỗi firing lại subscribe trên Publisher đã được giữ |
| **initialDelay** | Điều chỉnh thời điểm kích hoạt ban đầu, không làm Spring method assembly được gọi lại mỗi tick |

**Điểm đặc thù về fixedDelay được Spring tài liệu hóa:** với reactive scheduled method, framework **block subscription** để giữ đúng semantics fixed delay. Không nên hiểu “phương thức trả Mono” đồng nghĩa scheduler thread chắc chắn không bị block ở mọi lịch.

### fixedDelay với Publisher không hoàn tất

~~~java
@Scheduled(fixedDelay = 1_000)
public Flux<Long> infinite() {
    return Flux.interval(Duration.ofSeconds(1));
}
~~~

Đây là **đoạn minh họa** và không nên đưa nguyên vào lịch production. Flux.interval là stream phát nhiều giá trị và có thể **không bao giờ onComplete**. Vì fixedDelay chờ completion của subscription, lượt tiếp theo có thể **không được tính/kích hoạt như người lập trình mong đợi**.

**Giải pháp đúng:** tạo **một công việc hữu hạn** mỗi tick, ví dụ Flux.take(5) hoặc một Mono<Void> báo hoàn tất khi xử lý xong. Nếu cần chạy stream dài hạn liên tục, quản lý nó như **một subscription dài sống** với lifecycle riêng thay vì dùng @Scheduled để tái subscribe.

### fixedRate với publisher async

Nếu mỗi tick subscribe vào một publisher có I/O dài 30 giây nhưng fixedRate chỉ 5 giây, có thể có nhiều subscription in-flight. **Mono.defer không ngăn trùng lặp**; nó thậm chí bảo đảm mỗi subscription khởi động nguồn mới, nên workload có thể tăng mạnh nếu không kiểm soát concurrency.

**Cần phân biệt:** các lượt Runnable định kỳ trong ScheduledThreadPoolExecutor có thể không overlap theo hợp đồng của cùng periodic task, nhưng **subscription async** do Runnable tạo ra có thể còn hoạt động sau khi Runnable return. Do đó nhiều subscriptions/job instances vẫn có thể đồng thời chạy.

## 11. Thread của defer supplier là thread nào?

Hãy tách hai thứ:

- **TaskScheduler của Spring** kích hoạt subscription.
- **Scheduler của Reactor** (ví dụ Schedulers.boundedElastic(), parallel(), immediate()) điều khiển nơi các phần pipeline được thực hiện khi có subscribeOn/publishOn.

Một nguồn:

~~~java
Mono<String> work = Mono.defer(() -> {
    System.out.println(Thread.currentThread().getName());
    return Mono.just("ok");
});
~~~

Nếu không có operator đổi scheduler, supplier có thể được gọi trên thread tạo subscription, ví dụ thread của ThreadPoolTaskScheduler.

Nếu cần chạy công việc đồng bộ có blocking:

~~~java
Mono<String> work = Mono.defer(() ->
        Mono.fromCallable(this::readBlockingFile))
    .subscribeOn(Schedulers.boundedElastic());
~~~

Đây là **đoạn minh họa** với imports Reactor và method readBlockingFile. subscribeOn chủ yếu điều phối **subscription và nguồn upstream** trên scheduler được chọn. boundedElastic sử dụng tài nguyên hữu hạn/bounded theo cấu hình của Reactor, không có nghĩa tạo thread vô hạn hay bảo đảm một thread riêng cho mỗi tick.

### subscribeOn khác publishOn

| Operator | Tác động chính |
| --- | --- |
| **subscribeOn(scheduler)** | Ảnh hưởng nơi diễn ra subscription và upstream source execution, thường phù hợp để chuyển blocking source |
| **publishOn(scheduler)** | Đổi execution context cho **tín hiệu downstream sau điểm operator** |
| **Mono.defer** | Hoãn tạo Publisher đến subscription; **không tự chọn scheduler** |

Một anti-pattern:

~~~java
Mono.fromCallable(this::blockingCall)
    .publishOn(Schedulers.boundedElastic());
~~~

**publishOn đặt sau nguồn** không phải cách đáng tin để dời chính blockingCall khỏi thread chạy nguồn. Nếu mục tiêu là đổi thread của blocking source, thường cần **subscribeOn(boundedElastic)**.

**Không coi boundedElastic là thuốc chữa mọi blocking.** Vẫn cần connection pool, timeout, bulkhead/concurrency limit và số việc tối đa đồng thời. Lập lịch tạo 100 jobs/giây mà downstream chỉ phục vụ 2 jobs/giây sẽ gây backlog, bất kể Reactor có Scheduler nào.

## 12. Một use case thực tế: gọi HTTP reactive mỗi tick

Một service dùng WebClient có thể được thiết kế:

~~~java
@Scheduled(fixedRate = 15_000)
public Mono<Void> refreshRemoteCache() {
    return Mono.defer(() -> {
        String requestId = UUID.randomUUID().toString();

        return webClient.get()
                .uri("/v1/status")
                .header("X-Request-ID", requestId)
                .retrieve()
                .bodyToMono(StatusResponse.class)
                .doOnNext(this::updateLocalCache)
                .then();
    });
}
~~~

Đây là **một đoạn method minh họa**, giả sử webClient, StatusResponse và updateLocalCache được định nghĩa, cùng imports UUID. Nó minh họa xây **request mới và requestId mới** mỗi subscription.

Nhưng thực tế cần cân nhắc:

- **responseTimeout/connectTimeout** phù hợp và timeout ở layer HTTP client, không chỉ timeout Reactor ở cuối.
- **updateLocalCache** phải thread-safe, idempotent hoặc có version check nếu các ticks có thể overlap.
- Nếu một response cũ đến sau response mới, không ghi đè trạng thái mới bằng dữ liệu cũ.
- Tránh log secrets/tokens trong requestId hoặc correlation headers.
- Nếu cache dùng dữ liệu đa tenant, phải truyền tenant context/job identity đã xác minh.
- Nếu endpoint trả lỗi 401/403 hoặc invalid payload, không nuốt lỗi thành một trạng thái “thành công”.

**Không phải cứ bọc WebClient trong defer là mọi request luôn mới.** API của WebClient thường đã tạo cold Publisher, nhưng defer giúp rõ ràng khi cần lấy requestId/config mới **ở thời điểm subscribe**.

## 13. Dữ liệu động nhưng tránh đọc shared mutable field tùy tiện

Một lợi ích của defer là đọc dữ liệu mới tại từng subscription. Nhưng từ đó không được suy ra mọi shared mutable state đều an toàn.

Ví dụ dễ race:

~~~java
private String tenantId;

@Scheduled(fixedRate = 1_000)
public Mono<Void> sync() {
    return Mono.defer(() ->
            syncTenant(tenantId));
}
~~~

Đây là **đoạn minh họa**. Trong singleton bean, một thread khác có thể thay tenantId bất kỳ lúc nào. Nếu các subscriptions overlap, chúng có thể đọc ID khác nhau hoặc gặp state không nhất quán.

Thay bằng một nguồn snapshot hợp lệ:

~~~java
@Scheduled(fixedRate = 1_000)
public Mono<Void> sync() {
    return Mono.defer(() -> {
        JobConfig snapshot = configService.currentImmutableConfig();

        return syncTenant(snapshot.tenantId(), snapshot.version());
    });
}
~~~

Đây là **đoạn minh họa**. Hàm currentImmutableConfig phải trả snapshot nhất quán và JobConfig phải có immutable semantics theo thiết kế. Việc dùng record hoặc final field là hữu ích nhưng **không tự deep-copy** các phần tử mutable bên trong.

Chọn ngữ nghĩa rõ:

- **Snapshot lúc assembly**: dữ liệu cố định cho toàn vòng đời registration; có thể đúng nếu lịch gắn với một tenant bất biến.
- **Snapshot lúc subscription**: cập nhật theo mỗi tick; defer phù hợp.
- **Snapshot lúc thực thi thực tế sau queue wait**: có thể muộn hơn thời điểm subscription nếu dùng Scheduler khác; quyết định có chấp nhận đọc muộn không.
- **Ghi job config/version vào command**: hữu ích khi cần idempotency/retry/reconstruction.

**Không lấy dữ liệu cá nhân từ một request-scoped object** khi job scheduled chạy nền; HTTP request có thể đã kết thúc và SecurityContext của request thread không được tự truyền tới scheduler.

## 14. Lỗi trong mỗi subscription và việc lịch còn tiếp tục

Với reactive @Scheduled, một source có thể phát **onError**. Theo Spring Framework, lỗi reactive được **ghi log mức WARN và recovered** ở tầng scheduling; các subscription theo lịch **vẫn được tiếp tục**, khác với việc unchecked exception thoát trực tiếp từ scheduled Runnable.

**ErrorHandler của TaskScheduler không phải nơi mặc định xử lý lỗi onError này**, do reactive callback chuyển lỗi qua tín hiệu stream thay vì ném lên stack của Runnable.

Ví dụ:

~~~java
private final AtomicInteger attempt = new AtomicInteger();

@Scheduled(fixedRate = 5_000)
public Mono<Void> send() {
    return Mono.defer(() -> {
        int number = attempt.incrementAndGet();

        if (number <= 2) {
            return Mono.error(
                    new IllegalStateException("Remote not ready"));
        }

        return Mono.<Void>fromRunnable(this::deliver);
    });
}
~~~

Đây là **đoạn minh họa trong Spring bean** có deliver. Nhờ defer, biến attempt **được tăng ở mỗi subscription**, không chỉ một lần khi Spring khởi tạo Publisher. Hai lượt đầu phát onError; những lượt sau có thể tiếp tục.

**Đừng dựa duy nhất vào WARN log** để biết một tác vụ kinh doanh quan trọng đã thất bại. Có thể ghi metric, trạng thái job hoặc event lỗi đúng domain.

### Không nuốt mọi lỗi thành thành công

~~~java
return Mono.defer(this::sendActual)
        .onErrorResume(error -> Mono.empty());
~~~

Đây là **đoạn minh họa**. Nó biến mọi lỗi thành complete bình thường. Cách này có thể chấp nhận với một job best-effort không quan trọng, nhưng có thể che mất dữ liệu và làm hệ thống báo sai số liệu với job phải thực hiện.

Một thiết kế cần phân loại lỗi: lỗi parse không retry, lỗi 429/503 có thể retry với backoff, lỗi authorization thường phải ngừng/đưa ra alert, lỗi dữ liệu không nhất thiết nên bỏ qua.

## 15. retryWhen và repeat không thay thế các lần subscribe do @Scheduled

Một ví dụ:

~~~java
@Scheduled(fixedRate = 60_000)
public Mono<Void> job() {
    return Mono.defer(this::oneAttempt)
        .retryWhen(
            Retry.backoff(2, Duration.ofSeconds(1)))
        .then();
}
~~~

Đây là **đoạn minh họa** với reactor.util.retry.Retry và method oneAttempt trả Mono, không phải file độc lập.

- **@Scheduled**: tạo **subscription mới cho mỗi tick**.
- **retryWhen**: khi gặp lỗi trong **một subscription logic**, nó có thể **resubscribe** vào upstream để thử lại.
- **Mono.defer** nằm ở upstream nên retry cũng có thể gọi lại factory để làm **một attempt mới** trong **cùng tick**.
- Nếu fixedRate có tick mới trong lúc retry cũ chưa xong, có thể có **overlap**.
- Nếu fixedDelay và retry kéo dài, lần kế tiếp có thể phải đợi cả chuỗi retry hoàn tất.
- **repeat** tạo lần subscribe mới sau hoàn tất thành công; nếu dùng repeat vô hạn trên một @Scheduled publisher, một tick có thể biến thành stream kéo dài vô hạn, làm hỏng semantics mong đợi.

**Quan trọng:** “mỗi subscription tạo job mới” và “mỗi tick chỉ có một business attempt” không tự tương đương khi có retry/repeat/auto-resubscription. Cần jobId/tickId và attemptNo riêng.

### Tránh retry không giới hạn

Một source lỗi liên tục kết hợp retry vô hạn có thể giữ subscription không bao giờ kết thúc, tăng tải downstream, khiến fixedDelay kẹt hoặc fixedRate tích tụ jobs. Dùng retry hữu hạn, exponential backoff, timeout/deadline, circuit breaker và metrics khi cần.

## 16. Mono<Void> là lựa chọn rõ ràng cho side-effect scheduled jobs

Phương thức @Scheduled reactive thường chạy side effects và không cần trả dữ liệu ra một caller HTTP. Spring **bỏ qua các onNext signals** do Publisher phát ra.

Vì thế:

~~~java
@Scheduled(fixedDelay = 60_000)
public Mono<Void> archive() {
    return Mono.defer(() ->
            archiveService.archiveExpiredRecords()
                    .doOnNext(count -> metrics.recordArchived(count))
                    .then());
}
~~~

Đây là **minh họa** với archiveExpiredRecords trả Mono<Integer> và metrics có method phù hợp.

- **doOnNext** xử lý metric trong pipeline.
- **then()** chỉ còn quan tâm completion/error.
- Framework không lấy giá trị Integer làm một kết quả report.
- Mỗi subscription tạo **một Publisher mới** từ archiveService.

**Không nên tự gọi subscribe trong method:**

~~~java
@Scheduled(fixedRate = 60_000)
public Mono<Void> wrong() {
    archiveService.archiveExpiredRecords().subscribe();
    return Mono.empty();
}
~~~

Đây là **anti-pattern**: lời gọi subscribe chạy ngay trong pha assembly (method chỉ được Spring gọi một lần), bạn mất quyền kiểm soát lifecycle/job error của một subscription do tự tạo; còn Publisher trả về là Mono.empty và những tick sau không còn gọi archiveService. Nếu muốn để Spring quản lý scheduled subscription, **trả về pipeline**, không chủ động subscribe để “ép chạy”.

## 17. Cancellation, dispose và application shutdown

Một reactive Publisher có thể chưa hoàn tất khi ứng dụng ngừng:

- HTTP request đang đợi response.
- DB query vẫn còn in-flight.
- Flux.interval/takeUntil đang phát dữ liệu.
- Một source được chuyển sang boundedElastic đang thực thi blocking operation.
- Một subscription retry có delay.

Theo Spring Framework, khi bean @Scheduled reactive bị hủy hoặc ApplicationContext đóng, Spring sẽ **hủy các scheduled tasks và các subscriptions đang hoạt động** thuộc cơ chế đó.

Tuy nhiên:

1. Cancellation là một **tín hiệu tới Publisher/Subscriber**, không phải lệnh đảo ngược side effect đã commit.
2. Một blocking JDBC/native call có thể không dừng ngay chỉ vì cancel, phụ thuộc API/driver.
3. Một remote server có thể đã nhận request trước cancellation.
4. Một file/transaction có thể cần cleanup, rollback hoặc compensation riêng.
5. Nếu task đã được tự subscribe ngoài cơ chế Spring, Spring có thể **không sở hữu** subscription đó để hủy đúng.
6. Một CompletableFuture được dùng bên dưới có contract cancel riêng; không suy ra cancel Publisher luôn interrupt worker.

### Cleanup trong pipeline

Các operator như **doFinally** giúp quan sát ba dạng termination: complete, error, cancel. Với resource có lifecycle rõ, cân nhắc **using / usingWhen** hoặc API của driver bảo đảm close/release đúng, thay vì chỉ log trong doFinally.

~~~java
return Mono.defer(this::openWork)
    .doFinally(signal -> metrics.recordTermination(signal));
~~~

Đây là **đoạn minh họa**. doFinally không tự làm thao tác resource cleanup an toàn nếu openWork không cài đặt quyền sở hữu tài nguyên. Đặc biệt với các giao dịch/stream async, dùng primitive lifecycle phù hợp của thư viện.

## 18. Thời hạn xử lý và tình huống trùng việc

Giả sử một cron cứ 5 giây subscribe, nhưng mỗi công việc thực tế mất 20 giây. Với publisher asynchronous, có thể có nhiều jobs cùng lúc:

~~~text
t=0s    subscribe #1 --> job A chạy
t=5s    subscribe #2 --> job B chạy
t=10s   subscribe #3 --> job C chạy
t=15s   subscribe #4 --> job D chạy
t=20s   job A hoàn tất
~~~

**Dùng defer đảm bảo tạo mới**, nhưng không cung cấp **backpressure giữa các lần kích hoạt cron** theo một business contract.

Chọn chiến lược:

| Yêu cầu | Hướng xử lý |
| --- | --- |
| Luôn cần dữ liệu mới nhất, không cần mọi tick | Skip/coalesce nếu job trước còn chạy |
| Mỗi mốc là một job có ý nghĩa riêng | Durable queue, bounded concurrency, job key và retry |
| Một job tại một thời điểm trong một JVM | Single-flight guard có cleanup |
| Một job tại một thời điểm toàn cluster | Distributed lease/lock với idempotency/fencing khi cần |
| Không nhận job đã hết hạn | Deadline trong immutable job command; check cả trước và sau queue wait |
| Không gọi quá tải downstream | Rate limiter/bulkhead/connection pool và monitoring |
| Chạy rất lâu/không bao giờ hoàn tất | Quản lý một long-lived subscription riêng, thay vì repeatedly schedule vô hạn |

### Ví dụ single-flight trong một process

~~~java
private final AtomicBoolean inFlight =
        new AtomicBoolean(false);

@Scheduled(fixedRate = 5_000)
public Mono<Void> singleFlight() {
    return Mono.defer(() -> {
        if (!inFlight.compareAndSet(false, true)) {
            return Mono.empty();
        }

        return actualJob()
                .doFinally(signal -> inFlight.set(false));
    });
}
~~~

Đây là **đoạn minh họa**; actualJob() phải trả Mono<Void> hoặc publisher phù hợp, và imports AtomicBoolean/Reactor đã có. Guard được giành ở mỗi subscription, và doFinally được đăng ký để release sau complete/error/cancel.

**Cần suy nghĩ về race:** nếu actualJob() **ném lỗi đồng bộ ngay khi tạo Publisher** trước khi doFinally được gắn, flag có thể bị kẹt true. Với code thực tế, phải bảo đảm actualJob() không ném bên ngoài reactive signal, hoặc dùng defer bên trong + xử lý cleanup chắc chắn để không giữ cờ. Ví dụ được thiết kế cho API actualJob trả cold Mono và báo lỗi qua onError.

**Ngoài ra:** AtomicBoolean local không điều phối nhiều pods và không bảo vệ phần việc tạo ngoài pipeline. Nếu trong actualJob tự subscribe một nguồn khác rồi trả Mono.empty, guard sẽ reset sớm và không bao được side effect ấy.

## 19. Ngữ cảnh Reactor, ThreadLocal và bảo mật

Spring scheduling thường chạy không có HTTP request đang hoạt động. Một job @Scheduled không nên giả định:

- SecurityContextHolder có Authentication của user vừa gửi HTTP request.
- RequestContextHolder còn ServletRequestAttributes.
- MDC trace của request trước là trace của tick hiện tại.
- Transaction ở một thread tự theo sang worker khác.
- Một giá trị từ ThreadLocal được tự chuyển khi subscribeOn/publishOn đổi scheduler.

**Reactor Context** là cơ chế gắn dữ liệu với một subscriber chain, khác với ThreadLocal của servlet. Nếu cần một giá trị mới ở từng subscription:

~~~java
Mono<String> withContext =
        Mono.deferContextual(context ->
                Mono.just(
                        context.getOrDefault("jobId", "<missing>")));
~~~

Đây là **đoạn minh họa**. deferContextual đọc ContextView của **subscription hiện tại**. Tuy nhiên, Spring @Scheduled **không mặc nhiên chèn một HTTP user context** cho mỗi tick. Nếu muốn có jobId, tenantId hoặc service identity, phải thiết lập nó theo hợp đồng ứng dụng.

Có thể viết:

~~~java
@Scheduled(fixedDelay = 30_000)
public Mono<Void> job() {
    return Mono.defer(() -> {
        String jobId = UUID.randomUUID().toString();

        return performJob()
                .contextWrite(ctx -> ctx.put("jobId", jobId));
    });
}
~~~

Đây là **đoạn minh họa** với performJob trả Mono<Void>. Mỗi tick tạo jobId mới. Để đọc được jobId bên trong performJob, những operator cần Reactor Context nên được viết bằng **deferContextual** hoặc integration tương ứng. Việc đặt một String trong Reactor Context **không tự cung cấp quyền truy cập dữ liệu**, không thay thế authentication/authorization.

**Đừng dùng field singleton mutable** để lưu jobId của “tick hiện tại” nếu có thể có subscription overlap; tạo dữ liệu bất biến theo subscription và truyền qua method/Context.

## 20. Transaction và reactive data access

Reactive SQL/R2DBC thường dựa trên **Reactor Context** để liên kết transaction với subscription, khác với transaction gắn ThreadLocal ở các đường JDBC đồng bộ.

Ví dụ mô hình:

~~~java
@Scheduled(fixedDelay = 60_000)
public Mono<Void> reconcile() {
    return Mono.defer(() ->
            repository.findPending()
                    .flatMap(this::applyTransition)
                    .then());
}
~~~

Đây là **đoạn minh họa** với repository trả reactive Publisher. Mỗi subscription có thể tạo chuỗi query và updates mới, nhưng **không đồng nghĩa tự có transaction**. Nếu nhiều thao tác cần atomicity, đặt transaction boundary bằng **ReactiveTransactionManager/TransactionalOperator** hoặc cơ chế @Transactional reactive phù hợp và test đường đi qua proxy/integration thực tế.

### Bẫy khi kết hợp @Transactional với @Scheduled reactive

Vì Spring có thể chỉ **gọi method để tạo Publisher một lần**, không được dựa vào một field mutable hoặc một transaction được mở ngay trong Java method assembly để phục vụ nhiều tick. Transaction của reactive flow cần gắn với **subscription** đúng ranh giới; việc đặt annotation, proxy và reactive transaction operator phải được kiểm chứng ở application thực.

**Không dùng EntityManager/JDBC connection mở từ request thread** rồi giữ nó vào Publisher tái sử dụng cho các ticks. Nó có thể không thread-safe hoặc đã hết lifecycle. Với JDBC blocking, dùng nguồn lazy và scheduler phù hợp, quản lý transaction/connection ngay trong công việc của mỗi subscription.

## 21. Vì sao CompletableFuture không phù hợp làm return type cho @Scheduled reactive?

Spring Framework cho phép một số loại reactive qua **ReactiveAdapterRegistry**, nhưng yêu cầu loại đó hỗ trợ **deferred subscription**.

**CompletableFuture** có thể được adapt thành Publisher ở một số API, nhưng adapter thường khai báo **isDeferred() == false**, vì phần computation có thể đã bắt đầu và Future không có semantics lazy/replay giống publisher cold.

Vì thế, không nên viết:

~~~java
@Scheduled(fixedRate = 10_000)
public CompletableFuture<Result> unsupported() {
    return remoteAsync();
}
~~~

Đây là **đoạn minh họa sai về lựa chọn return type** cho reactive @Scheduled trong Spring 6.1/6.2. Khi hạ tầng xác định đây là kiểu không deferred, nó có thể từ chối đăng ký và dẫn đến lỗi cấu hình.

Thay bằng kiểu reactive:

~~~java
@Scheduled(fixedRate = 10_000)
public Mono<Result> supported() {
    return Mono.defer(() ->
            Mono.fromFuture(() -> remoteAsync()));
}
~~~

Đây là **đoạn minh họa** với remoteAsync trả CompletableFuture<Result>, dùng overload **Mono.fromFuture(Supplier<CompletableFuture>)** để khởi tạo Future khi subscription bắt đầu. Cần xem cancellation behavior của Mono.fromFuture và API remoteAsync để tránh hủy sai một Future chia sẻ hoặc bỏ dở side effects.

Nếu công việc bản chất là một phương thức @Async trả Future được submit theo lịch, có thể dùng @Scheduled void và chủ động submit/track job qua TaskExecutor, nhưng đó là **mô hình khác** với @Scheduled reactive. Đừng chọn CompletableFuture return rồi cho rằng framework sẽ tạo lại Future ở từng tick.

## 22. Kiểm thử defer theo hai tầng

### Tầng A — publisher/unit test

Không cần đợi scheduler thật:

1. Tạo Mono.defer với AtomicInteger.
2. Assert ngay sau assembly counter = 0.
3. Subscribe lần thứ nhất; assert counter = 1.
4. Subscribe lần thứ hai; assert counter = 2.
5. Đối chiếu cùng thử nghiệm với Mono.just(...) để thấy eager = 1 ngay lúc assembly và không tăng tiếp.

Ví dụ Maven đầy đủ ở trên thực hiện đúng ý tưởng này qua block() và stdout.

Nếu dùng **reactor-test**, có thể viết StepVerifier cho từng subscription. Đảm bảo mỗi verifier **subscribe** một lần; đừng chỉ kiểm tra Publisher đã được tạo.

### Tầng B — Spring integration test

1. Bật @EnableScheduling, đăng ký đúng bean @Scheduled và một TaskScheduler có cấu hình.
2. Đặt counter tăng **trong method** và counter tăng **trong Mono.defer**.
3. Đợi nhiều ticks bằng CountDownLatch với timeout.
4. Assert method assembly một lần và supplier được gọi nhiều lần.
5. Đóng context; bảo đảm scheduled subscriptions không tồn tại ngoài lifecycle.
6. Kiểm tra lỗi onError trong tick trước không khiến toàn bộ lịch mặc nhiên dừng.
7. Nếu source dùng Scheduler Reactor, đo thread identity/names ở từng operator cần kiểm chứng.
8. Nếu fixedDelay, dùng một Publisher kết thúc theo latch để chứng minh lượt sau đợi completion.
9. Nếu fixedRate, tạo nhiều nguồn async có thể overlap rồi test chính sách single-flight/backpressure.
10. Nếu có nhiều pods, integration test một JVM không đủ để xác minh global uniqueness.

**Đừng dùng Thread.sleep rồi kết luận chính xác bao nhiêu ticks đã chạy**: scheduler chịu độ trễ của hệ điều hành/GC/CI. Đặt điều kiện tối thiểu “ít nhất N lượt” hoặc sử dụng virtual time cho phần Reactor có lịch thích hợp.

## 23. Lỗi thường gặp khi sử dụng defer trong lịch reactive

**Lỗi 1 — Công việc đã chạy ngoài Publisher.**

~~~java
@Scheduled(fixedRate = 5_000)
public Mono<Void> bad() {
    sideEffect();
    return Mono.empty();
}
~~~

SideEffect chạy một lần lúc framework tạo Publisher, không phải mỗi tick. Đưa side effect vào nguồn lazy.

**Lỗi 2 — Mono.just với lời gọi có side effect.**

~~~java
return Mono.just(repository.queryNow());
~~~

queryNow bị gọi ngay khi assembly. Dùng fromCallable/fromSupplier hoặc defer theo đúng kiểu nguồn.

**Lỗi 3 — Bọc defer nhưng luôn trả một Mono đã cache.**

~~~java
Mono<String> shared = Mono.fromCallable(this::load).cache();
return Mono.defer(() -> shared);
~~~

Factory chạy lại nhưng source cached vẫn là một instance. Tạo nguồn mới bên trong factory nếu cần.

**Lỗi 4 — Nghĩ defer tự chuyển blocking work ra thread khác.**

Sai. defer chỉ đổi **thời điểm tạo nguồn**. Dùng subscribeOn với scheduler phù hợp nếu có blocking.

**Lỗi 5 — Tự subscribe trong method @Scheduled rồi trả Mono.empty.**

Sai nếu muốn Spring quản lý công việc theo lịch: Spring chỉ lấy Publisher một lần, tự subscribe bên ngoài làm mất quyền theo dõi cancellation/error của scheduled subscription.

**Lỗi 6 — Tạo một Flux vô hạn cho fixedDelay.**

Lượt fixed-delay tiếp theo có thể không đến vì subscription không bao giờ complete. Dùng công việc hữu hạn hoặc quản lý stream dài sống riêng.

**Lỗi 7 — Dùng retry vô hạn cùng tick định kỳ.**

Có thể gây backlog hoặc kẹt fixedDelay; giới hạn attempts/deadline.

**Lỗi 8 — Dùng onErrorResume(error -> Mono.empty()) cho mọi lỗi.**

Có thể che mất failure và làm job quan trọng bị bỏ lặng.

**Lỗi 9 — Dựa vào onNext của @Scheduled để lấy output.**

Spring bỏ qua onNext; lưu trạng thái/metrics trong pipeline.

**Lỗi 10 — Cho rằng mỗi subscription tương đương một business attempt.**

retryWhen/repeat hoặc source tự resubscribe có thể tạo nhiều attempts cho một tick. Dùng tickId + attemptNo.

**Lỗi 11 — Tạo jobId trên field singleton và overwrite ở tick sau.**

Với overlap, job A và job B có thể sử dụng ID lẫn nhau. Tạo ID trong defer, truyền bất biến.

**Lỗi 12 — Cho rằng @Scheduled reactive có SecurityContext của HTTP request mới nhất.**

Scheduled job không thuộc một request cụ thể; định danh và authorization phải được thiết kế riêng.

**Lỗi 13 — Nghĩ CompletableFuture return tự có lazy semantics.**

Spring ReactiveAdapterRegistry có thể đánh dấu nó nondeferred và không chấp nhận dùng trực tiếp làm reactive @Scheduled return.

**Lỗi 14 — Nghĩ fixedDelay luôn không block vì trả Mono.**

Spring reactive fixedDelay block subscription để giữ completion-based delay.

**Lỗi 15 — Dùng Publisher đã bị dispose/không thể resubscribe.**

Một @Scheduled reactive Publisher cần hỗ trợ **nhiều subscriptions** theo lifecycle của framework.

**Lỗi 16 — Xem cancel là rollback tác động nghiệp vụ.**

Cancellation không hoàn tác email đã gửi, tiền đã chuyển hay DB đã commit.

## 24. Câu hỏi ôn tập kèm đáp án

**Câu 1.** Trong Spring 6.1+, phương thức @Scheduled trả Mono được gọi lại vào mỗi tick không?

**Đáp án:** Spring lấy Publisher từ method một lần khi đăng ký, rồi mỗi tick tạo subscription trên Publisher ấy. Do đó logic nằm trong body method có thể chỉ chạy một lần.

**Câu 2.** Vì sao Mono.just(loadValue()) có thể trả giá trị cũ ở nhiều ticks?

**Đáp án:** loadValue() được Java gọi trước khi Mono.just được tạo, tại assembly. Một Mono.just giữ giá trị đã tính và phát lại nó.

**Câu 3.** Mono.defer(() -> Mono.just(loadValue())) thay đổi điều gì?

**Đáp án:** Supplier của defer chỉ được gọi khi subscribe, vì thế loadValue được tính lại mỗi subscription. Nếu loadValue blocking, vẫn cần xử lý thread và timeout.

**Câu 4.** Khi chỉ có một Callable đồng bộ và cần chạy mỗi subscribe, có bắt buộc dùng defer không?

**Đáp án:** Không. Mono.fromCallable(callable) tự lazy theo subscription; defer hữu ích hơn khi phải tạo/chọn một Publisher mới.

**Câu 5.** defer có bảo đảm chạy trên thread mới không?

**Đáp án:** Không. Nó không phải scheduler; operator subscribeOn/publishOn và nguồn thực tế mới quyết định execution context.

**Câu 6.** Publisher được tạo một lần có chắc chỉ chạy công việc một lần không?

**Đáp án:** Không. Cold Publisher có thể thực hiện lại source cho mỗi subscribe. Phải kiểm tra semantics của source/cache/hot operators.

**Câu 7.** Nếu defer luôn trả một Mono đã cache, tick sau có chắc gọi lại remote không?

**Đáp án:** Không. Factory được gọi lại nhưng Mono đã cache có thể phát lại tín hiệu cũ.

**Câu 8.** Khi @Scheduled reactive fixedDelay chạy một Mono không bao giờ complete, điều gì có thể xảy ra?

**Đáp án:** Spring block để tôn trọng fixedDelay; lượt tiếp theo có thể bị kẹt vì subscription chưa kết thúc.

**Câu 9.** Nếu fixedRate là 5 giây nhưng mỗi job asynchronous mất 20 giây, defer có ngăn overlap không?

**Đáp án:** Không. Nhiều subscriptions có thể in-flight cùng lúc; cần bulkhead/single-flight/queue/idempotency.

**Câu 10.** Spring dùng giá trị onNext của Mono<String> từ @Scheduled để làm gì?

**Đáp án:** Các onNext values bị bỏ qua ở scheduled reactive method; phần việc nên được theo dõi bằng side effects/metrics/job status trong pipeline.

**Câu 11.** Lỗi onError trong một subscription có tự động ngăn mọi tick sau không?

**Đáp án:** Theo cơ chế reactive @Scheduled, Spring log WARN và tiếp tục schedule subsequent subscriptions; không mặc định dùng ErrorHandler của scheduled Runnable.

**Câu 12.** subscribeOn(boundedElastic) được dùng để giải quyết vấn đề nào?

**Đáp án:** Đưa nguồn blocking đồng bộ sang Scheduler Reactor phù hợp hơn, nhưng vẫn cần giới hạn tài nguyên, timeout và giám sát tải.

**Câu 13.** Vì sao không nên đặt sideEffect().subscribe() ngay trong method @Scheduled reactive?

**Đáp án:** Method chỉ được gọi một lần; subscription tự tạo không thuộc lifecycle quản lý tick của framework, dễ mất error/cancellation handling.

**Câu 14.** Có thể dùng CompletableFuture trực tiếp làm kiểu trả về của reactive @Scheduled không?

**Đáp án:** Không nên. Adapter của CompletableFuture không hỗ trợ deferred subscription theo contract; dùng Publisher deferred như Mono được tạo đúng theo mỗi subscription.

**Câu 15.** Nếu muốn mỗi tick có jobId khác nhau, nên đặt UUID.randomUUID() ở đâu?

**Đáp án:** Trong Mono.defer hoặc một nguồn lazy được thực thi theo mỗi subscription, không nằm trước khi trả Mono dùng chung tại assembly.

**Câu 16.** Khi ứng dụng có ba pods, Mono.defer có bảo đảm chỉ một job toàn cluster không?

**Đáp án:** Không. Mỗi pod có thể tạo subscription riêng; cần distributed coordination/idempotency.

## 25. Checklist triển khai

- [ ] Xác nhận dùng Spring Framework 6.1+ và kiểu trả về @Scheduled reactive có **deferred/re-subscribe semantics**.
- [ ] Hiểu **method được gọi một lần, Publisher được subscribe nhiều lần**.
- [ ] Không đặt side effect trực tiếp trong method assembly nếu mong nó chạy mỗi tick.
- [ ] Không dùng Mono.just(expensiveCall()) khi cần dữ liệu mới theo subscription.
- [ ] Dùng **Mono.defer/Flux.defer** khi cần tạo nguồn/pipeline mới cho mỗi lượt.
- [ ] Khi chỉ cần lời gọi đồng bộ mới, cân nhắc **Mono.fromCallable/fromSupplier/fromRunnable**.
- [ ] Kiểm tra cold/hot/cache/share và vị trí cache tương đối với defer.
- [ ] Không tự subscribe rồi trả Mono.empty nếu muốn framework sở hữu lịch và subscription.
- [ ] Đối với blocking I/O, dùng Scheduler thích hợp hoặc reactive driver, có timeout/downstream limits.
- [ ] Phân biệt **subscribeOn** và **publishOn**, không cho rằng defer tự chọn thread.
- [ ] Dùng **Mono<Void> / then()** khi chỉ cần completion/error.
- [ ] Hiểu **fixedDelay chờ completion của reactive subscription**, tránh publisher vô hạn.
- [ ] Với fixedRate/cron, xem khả năng có nhiều jobs in-flight.
- [ ] Kiểm soát retryWhen/repeat để không tạo attempt vô hạn trong một tick.
- [ ] Đặt immutable jobId/config snapshot/deadline theo đúng thời điểm subscription.
- [ ] Không dùng ThreadLocal/request-scoped context như thể scheduled job thuộc một request HTTP.
- [ ] Với DB/transaction, xác định reactive transaction boundary ở subscription.
- [ ] Xử lý onError bằng metric/job status, không che mọi lỗi thành complete.
- [ ] Dọn tài nguyên theo complete/error/cancel và test context shutdown.
- [ ] Đặt idempotency/lock phù hợp nếu có nhiều subscriptions hoặc nhiều pods.
- [ ] Kiểm thử assembly counter và subscription counter độc lập bằng latch/StepVerifier.
- [ ] Đo số subscription, job starts, completions, failures, concurrency và queue wait.
- [ ] Chỉ dùng Publisher có thể **subscribe nhiều lần** một cách hợp lệ.

## Kết luận

**Mono.defer là một ranh giới trì hoãn việc tạo Publisher đến thời điểm subscription.** Với **@Scheduled reactive**, ranh giới này đặc biệt quan trọng vì Spring **gọi method để lấy Publisher một lần nhưng subscribe lại mỗi lượt lịch**. Đưa việc tạo request/command/pipeline vào defer giúp mỗi tick thực sự khởi động một công việc mới, tránh lỗi dữ liệu cũ do tính giá trị ngay tại assembly.

Tuy nhiên, **defer không tự cung cấp concurrency control, thread mới, transaction, security context hoặc durable job state**. Mỗi subscription được tạo mới có thể chạy chồng lấn, lỗi, timeout hoặc tiếp tục tác động tới tài nguyên downstream. Vì vậy, thiết kế đúng là: tạo nguồn lazy theo mỗi subscription, chọn scheduler khi cần, giới hạn overlap và overload, quản lý lỗi/cancel/cleanup, đồng thời kiểm thử bằng counter hoặc latch để xác minh sự khác nhau giữa **method invocation** và **actual subscription work**.

**Tài liệu tham khảo chính thức:**

- Spring Framework: [Task Execution and Scheduling — Reactive @Scheduled](https://docs.spring.io/spring-framework/reference/integration/scheduling.html), phần reactive methods.
- Spring Framework: [@Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html) — Publisher được tạo một lần, re-subscribe, fixedDelay blocking, WARN khi onError.
- Spring Framework: [ScheduledAnnotationReactiveSupport](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/annotation/ScheduledAnnotationReactiveSupport.html) — điều phối subscription theo lịch.
- Project Reactor: [Mono Javadoc](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html), các API defer, just, fromCallable, fromSupplier, fromRunnable và cache.
- Project Reactor: [Schedulers](https://projectreactor.io/docs/core/release/api/reactor/core/scheduler/Schedulers.html) và [Reactor Reference Guide](https://projectreactor.io/docs/core/release/reference/).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-scheduling-004">Giá trị onNext bị bỏ qua trong phương thức lập lịch</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị onNext bị bỏ qua trong phương thức lập lịch

## 1. Câu hỏi cốt lõi: @Scheduled reactive có “trả kết quả” đi đâu?

Kể từ **Spring Framework 6.1**, phương thức `@Scheduled` có thể trả về một Reactive Streams `Publisher` như Reactor `Mono<T>` hoặc `Flux<T>`, hoặc một kiểu có thể được `ReactiveAdapterRegistry` chuyển sang Publisher **và hỗ trợ thực thi trì hoãn**. Cách này cho phép khai báo tác vụ theo lịch dưới dạng một pipeline reactive thay vì một phương thức `void` chạy đồng bộ.

Tuy nhiên, khác với HTTP handler của Spring WebFlux, **Spring scheduling không có downstream consumer nào nhận các giá trị kết quả của phương thức lập lịch**. Hạ tầng scheduler chỉ **đăng ký subscription** vào Publisher theo chu kỳ, để pipeline được thực thi. Khi pipeline phát:

- `onNext(value)`: giá trị **bị bỏ qua/loại khỏi kết quả scheduling**, không được gom thành danh sách, ghi DB, truyền vào phương thức khác hay trả lại cho caller.
- `onComplete()`: đánh dấu subscription hiện tại đã hoàn tất, có vai trò trong lifecycle và đặc biệt quan trọng với `fixedDelay`.
- `onError(error)`: lỗi subscription được Spring ghi log ở mức `WARN` và được xử lý để các lượt lập lịch sau có thể tiếp tục; không tự biến thành một kết quả business hay báo lỗi trực tiếp cho caller.

**Điểm dễ nhầm nhất:** “bỏ qua `onNext`” **không có nghĩa Publisher không được subscribe hoặc chuỗi operator không chạy**. Một `doOnNext` ở **upstream** vẫn được gọi khi nguồn phát giá trị; `map`/`flatMap` vẫn thực thi khi có subscription và demand phù hợp. Chỉ có điều **giá trị cuối cùng không được scheduler sử dụng**.

Vì vậy cần thiết kế theo mục đích:

1. **Tác vụ mang side effect và không cần trả dữ liệu:** ưu tiên `Mono<Void>` hoàn tất khi side effect thực sự hoàn tất.
2. **Tác vụ tạo dữ liệu cần lưu/đẩy tiếp:** phải **tiêu thụ dữ liệu bên trong pipeline** bằng `flatMap`/`flatMapMany` và API xử lý tương ứng, trước khi `then()`.
3. **Muốn người gọi nhận kết quả:** dùng một API trả `Mono<T>`/`Flux<T>` cho consumer thật, hoặc lưu kết quả/đẩy event qua kênh khác; **không** dựa vào `@Scheduled` như cơ chế trả giá trị.

Bài này giải thích cơ chế subscribe định kỳ, cách nhận diện dữ liệu bị bỏ qua, ví dụ Java 17+ và Spring 6.2.9 chạy được, lỗi phổ biến về hot/cold publisher, `@Scheduled` và `@Async`, error/retry, kiểm thử, và cách biến tác vụ scheduled thành một luồng công việc quan sát được.

## 2. Reactive Streams có những tín hiệu nào?

Một Publisher tuân theo Reactive Streams phát tín hiệu theo hợp đồng subscription:

```text
Subscriber -- subscribe --> Publisher
           <-- onSubscribe(subscription)
           -- request(n) ---------------->

Publisher  -- onNext(A) ----------------> Subscriber
           -- onNext(B) ----------------> Subscriber
           -- onComplete() -------------> Subscriber
                hoặc
           -- onError(error) -----------> Subscriber
```

Một subscription hợp lệ có thể phát **0 hoặc nhiều `onNext`** và sau đó tối đa một tín hiệu terminal (`onComplete` hoặc `onError`). Sau terminal, subscription kết thúc; một Publisher có thể có các lần subscription độc lập về sau nếu nó hỗ trợ.

Khi `@Scheduled` subscribe một Flux theo lịch, framework đóng vai subscriber ở ranh giới scheduling:

```text
Spring @Scheduled -> subscribe -> Flux.just("A","B")
                                      |
                                      +-- onNext("A") ---> scheduler không dùng A
                                      +-- onNext("B") ---> scheduler không dùng B
                                      +-- onComplete() --> lượt subscription kết thúc
```

Đây **không** phải là một `Flux` tự phát giá trị mà không có ai subscribe. Framework chủ động subscribe, vì thế tác vụ vẫn chạy. Phần bị bỏ qua là **các payload onNext** mà scheduler nhận, giống như giá trị return của một method đồng bộ `@Scheduled` cũng không được sử dụng.

Bảng phân biệt:

| Thành phần | Scheduler làm gì? | Người viết tác vụ nên làm gì? |
| --- | --- | --- |
| `onSubscribe` | Bắt đầu/duy trì subscription | Không tự gọi `subscribe()` bên ngoài pipeline |
| `onNext` | Không thu thập và không phát lại giá trị | Xử lý kết quả trong chuỗi nếu nghiệp vụ cần |
| `onComplete` | Nhận biết subscription hoàn tất | Kết thúc pipeline khi công việc đã hoàn tất |
| `onError` | Log `WARN`; một lỗi không chấm dứt lịch về sau | Đo lỗi, đặt retry/timeout có giới hạn nếu cần |
| `cancel`/disposal | Hủy lịch và các subscription còn hoạt động khi shutdown | Thiết kế tác vụ tôn trọng cancellation, release resource |

Đừng xem `onComplete` như bằng chứng một giao dịch nghiệp vụ đã commit thành công: chỉ đúng nếu toàn bộ side effect cần thiết được **nối vào cùng pipeline** và API trả kết quả hoàn tất sau khi commit/ack phù hợp.

## 3. Spring gọi annotated method một lần, rồi subscribe lại nhiều lần

Một chi tiết rất quan trọng của Spring Framework 6.1+:

> Hạ tầng scheduling **gọi phương thức @Scheduled để lấy Publisher một lần**, sau đó lập lịch những `Runnable` subscribe lại **chính Publisher đó** theo `cron`, `fixedRate` hoặc `fixedDelay`.

Điều này khác cách hiểu “mỗi lần tick thì gọi lại method để tạo Publisher mới”.

```text
Khi đăng ký lịch:
     Spring gọi job.reactiveJob()
                    |
                    v
            Nhận một Publisher
                    |
                    +-------------------+--------------------+
                                        |                    |
Lịch tick #1                   Lịch tick #2           Lịch tick #3
   subscribe                      subscribe              subscribe
      |                              |                      |
   pipeline chạy #1             pipeline chạy #2       pipeline chạy #3
      |
   onNext -> bị bỏ qua
   terminal -> kết thúc lượt #1
```

Từ đó xuất hiện hai quy tắc quan trọng:

1. **Publisher phải hỗ trợ nhiều subscription**; nguồn chỉ dùng được một lần có thể thất bại ở tick sau.
2. **Tác dụng phụ phải được thực hiện lúc subscribe**, không phải lúc Java gọi method để **lắp ráp Publisher** lần đầu.

Ví dụ lỗi:

```java
@Scheduled(fixedRate = 5_000)
public Mono<String> badClock() {
    String value = Instant.now().toString(); // Chỉ tính khi method được gọi.
    return Mono.just(value);                  // Lặp lại cùng dữ liệu.
}
```

Đây là **mảnh code minh họa** cần import `Instant`, `Mono` và `@Scheduled`. Spring tạo Publisher một lần; các lần subscribe sau có thể vẫn phát chuỗi thời gian **đã chụp lúc khởi tạo**, không phải đồng hồ mới của mỗi tick.

Cách phù hợp nếu muốn tính khi subscription bắt đầu:

```java
@Scheduled(fixedRate = 5_000)
public Mono<String> deferredClock() {
    return Mono.fromSupplier(() -> Instant.now().toString());
}
```

Nhưng ở cả hai trường hợp, **giá trị String vẫn không được Spring scheduling tiêu thụ**. `defer`/`fromSupplier` sửa thời điểm tính dữ liệu, **không tự biến `onNext` thành một kết quả được lưu**.

Một phương thức chỉ gọi `System.out.println` trong thân method (bên ngoài `defer`) có thể chỉ in một lần lúc đăng ký lịch, không phải mỗi tick. Muốn một hành động xảy ra mỗi lần subscribe, đặt hành động **bên trong Publisher deferred** hoặc một operator upstream được chạy khi subscribe.

## 4. Ví dụ đối chiếu: Flux có onNext nhưng kết quả không đi đâu

Spring cho phép:

```java
@Scheduled(fixedRate = 5_000)
public Flux<String> produceLabels() {
    return Flux.just("A", "B");
}
```

Đây là **mảnh Java của một Spring-managed bean**, cần `@EnableScheduling` và Reactor. Khi đến lượt scheduler, `Flux` phát hai `onNext`, rồi `onComplete`. Không có service nào tự nhận hai chuỗi "A" và "B"; framework không lưu chúng.

Nếu thêm:

```java
return Flux.just("A", "B")
        .map(String::toLowerCase)
        .doOnNext(value -> System.out.println("upstream=" + value));
```

thì console có thể in `upstream=a` và `upstream=b` mỗi subscription vì `map` và `doOnNext` nằm **trước điểm mà scheduler bỏ qua payload**. Điều này **không mâu thuẫn** với quy tắc `onNext` bị bỏ qua: operator upstream đã chạy và `doOnNext` tự gây side effect; chỉ không có downstream business consumer từ framework để dùng hai giá trị.

Đừng kết luận “không thấy giá trị trong controller” tức là “publisher không chạy”. Muốn xác nhận, đo `doOnSubscribe`, `doOnNext`, `doOnComplete`, `doOnError`, số lượt scheduler và state công việc, thay vì đợi Spring trả một `List<String>` không tồn tại.

## 5. Ví dụ chạy được: Java 17+, Spring 6.2.9 và Reactor 3.7.8

Bài thực hành sau cố ý cho mỗi subscription phát hai giá trị. Ta đo:

- **Số lần annotated method thực sự được gọi** để lắp ráp Publisher.
- **Số lần subscribe vào Publisher** do Spring scheduling kích hoạt.
- **Số lần operator upstream nhận onNext**.
- **Số subscription kết thúc thành công**.

Chúng ta đặt `CountDownLatch` để đợi đúng hai lượt thành công có thời hạn. `ThreadPoolTaskScheduler` được khai báo riêng nên không phụ thuộc fallback scheduler mặc định. Phần in kết quả lấy dữ liệu từ `Probe`, **không dựa vào việc Spring trả `onNext` về main**.

### Cấu trúc Maven

```text
scheduled-onnext-demo/
├── pom.xml
└── src/main/java/demo/ScheduledOnNextDemo.java
```

File `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>scheduled-onnext-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.9</version>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
            <version>3.7.8</version>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-observation</artifactId>
            <version>1.14.9</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.0</version>
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

File `src/main/java/demo/ScheduledOnNextDemo.java`:

```java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import reactor.core.publisher.Flux;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ScheduledOnNextDemo {

    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("schedule-");
        return scheduler;
    }

    public static final class Probe {
        final AtomicInteger methodCalls = new AtomicInteger();
        final AtomicInteger subscriptions = new AtomicInteger();
        final AtomicInteger upstreamOnNext = new AtomicInteger();
        final AtomicInteger completions = new AtomicInteger();
        final CountDownLatch twoCompletions = new CountDownLatch(2);
    }

    @Bean
    public Probe probe() {
        return new Probe();
    }

    @Bean
    public Job job(Probe probe) {
        return new Job(probe);
    }

    public static final class Job {
        private final Probe probe;

        public Job(Probe probe) {
            this.probe = probe;
        }

        @Scheduled(initialDelay = 100, fixedDelay = 300)
        public Flux<String> tick() {
            probe.methodCalls.incrementAndGet();

            // Flux.defer: đoạn bên trong thực thi lại cho mỗi subscription.
            return Flux.defer(() -> {
                int n = probe.subscriptions.incrementAndGet();

                return Flux.just("value-" + n + "-A",
                                 "value-" + n + "-B")
                        .doOnNext(value ->
                                probe.upstreamOnNext.incrementAndGet())
                        .doOnComplete(() -> {
                            probe.completions.incrementAndGet();
                            probe.twoCompletions.countDown();
                        });
            });
        }
    }

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(
                             ScheduledOnNextDemo.class)) {

            Probe probe = context.getBean(Probe.class);

            if (!probe.twoCompletions.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out");
            }

            System.out.println("method invocations = "
                    + probe.methodCalls.get());
            System.out.println("subscriptions = "
                    + probe.subscriptions.get());
            System.out.println("upstream onNext = "
                    + probe.upstreamOnNext.get());
            System.out.println("completed subscriptions = "
                    + probe.completions.get());
        }
    }
}
```

Chạy trong thư mục dự án với JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.ScheduledOnNextDemo
```

Stdout của chương trình:

```text
method invocations = 1
subscriptions = 2
upstream onNext = 4
completed subscriptions = 2
```

**Đã xác minh thực thi** logic Java này qua JShell với Spring Framework 6.2.9, Reactor 3.7.8, Reactive Streams 1.0.4 và Micrometer tương ứng. Cả bốn dòng trên khớp. Mã Maven được cung cấp để tái tạo ví dụ ngoài JShell; số phiên bản nêu rõ để tránh ngầm dựa vào một Spring/Project Reactor khác.

Giải thích tuần tự:

1. Spring tạo ApplicationContext và xử lý `@Scheduled` trên bean `Job`. `tick()` được gọi **một lần** để trả về Publisher. `probe.methodCalls` tăng từ 0 lên 1.
2. Sau initial delay, scheduler subscribe `Flux.defer`. Đoạn bên trong `defer` tạo hai giá trị `value-1-A` và `value-1-B`; operator `doOnNext` tăng counter hai lần.
3. Hai giá trị đi tới ranh giới subscriber của Spring nhưng **không được dùng**. Flux phát `onComplete`, và `doOnComplete` ghi nhận lượt đầu.
4. Sau `fixedDelay` tính từ lúc subscription hoàn tất, scheduler subscribe lần nữa **cùng Publisher đã tạo từ bước 1**. `Flux.defer` cho một lần thực thi mới: `value-2-A` và `value-2-B`.
5. Counter tổng: `methodCalls=1`, `subscriptions=2`, `upstreamOnNext=4`, `completions=2`. `CountDownLatch` đánh thức main sau hai lượt. Ví dụ có khoảng cách fixed delay đủ lớn để đọc counter rồi đóng context trước lượt thứ ba.

**Kết luận quan trọng nhất:** `upstreamOnNext=4` chứng minh 4 tín hiệu đã được tạo và operator upstream đã thực thi; **không có giá trị nào được Spring scheduling lưu hoặc chuyển thành kết quả cho main**. Các số đếm hiển thị là do chính code demo chủ động lưu vào `Probe`; đó không phải collection `onNext` tự động của scheduler.

Trong demo dùng `@Scheduled(fixedDelay=300)` để các subscription không chồng lên nhau; trong dự án thật không nên dùng chu kỳ vài trăm mili giây chỉ vì ví dụ.

## 6. Làm sao thực sự sử dụng dữ liệu onNext?

Nếu tác vụ cần đọc thông tin và lưu lại, **phải nối công việc tiêu thụ dữ liệu vào pipeline**. Ví dụ một service có các API reactive:

```java
// Mảnh tích hợp: remote.fetch() trả Mono<Data>;
// store.persist(Data) trả Mono<Void> hoặc Mono<?>.
@Scheduled(fixedDelay = 5_000)
public Mono<Void> refresh() {
    return Mono.defer(() ->
            remote.fetch()
                    .flatMap(data -> store.persist(data))
                    .then());
}
```

Đoạn này **không phải chương trình độc lập**: cần các bean `remote`, `store`, kiểu `Data` và imports của Spring/Reactor. Nhưng nó thể hiện luồng xử lý:

```text
Scheduler subscribe
      |
      v
remote.fetch() -- onNext(data) --> flatMap(data -> store.persist(data))
                                         |
                                         +--> hoàn tất lưu dữ liệu
                                                   |
                                                   v
                                                 then()
                                                   |
                                                   v
                                              onComplete()
```

Ở đây `flatMap` chính là **consumer nghiệp vụ** của data. `then()` chỉ bỏ **giá trị kết quả cuối**, sau khi đã đợi publisher của việc persist hoàn tất. Nếu `persist` thất bại, pipeline kết thúc với `onError` trừ khi ứng dụng chủ động xử lý lỗi.

Với nhiều phần tử:

```java
@Scheduled(fixedDelay = 30_000)
public Mono<Void> batchRefresh() {
    return Flux.defer(() -> remote.streamChanges())
            .flatMap(change -> store.apply(change), 8)
            .then();
}
```

Đây cũng là **mảnh tích hợp**; `remote.streamChanges()` cần trả `Publisher<Change>` hữu hạn và `store.apply` trả một Publisher thể hiện việc áp dụng thay đổi. Tham số `8` giới hạn mức concurrency của `flatMap` ở mức minh họa; phải sizing theo DB/downstream thực tế, và có chính sách retry/idempotency.

**Điểm cần chú ý:** `then()` không làm upstream bị bỏ qua. Nó **đợi chuỗi đã nối hoàn tất**, đồng thời chỉ không chuyển các payload onNext tới downstream. Đúng mục đích của `@Scheduled` khi kết quả là hoàn tất/lỗi chứ không phải một `List<T>` để trả cho caller.

## 7. Không dùng fire-and-forget subscribe() bên trong job

Một phản ví dụ phổ biến:

```java
@Scheduled(fixedDelay = 5_000)
public Mono<Void> wrong() {
    return Mono.defer(() -> {
        service.process().subscribe(); // Subscription ngoài luồng quản lý.
        return Mono.empty();           // Kết thúc ngay.
    });
}
```

Đây là **mảnh phản ví dụ**, giả sử `service.process()` trả `Mono<Void>`. Khi scheduler subscribe:

- `service.process().subscribe()` tạo **một subscription tách rời**.
- Publisher được trả cho scheduler là `Mono.empty()`, báo `onComplete` gần như ngay lập tức.
- Với `fixedDelay`, scheduler đo “đã hoàn tất” theo `Mono.empty()`, **không theo xử lý thật của `service.process()`**.
- Lỗi hoặc cancellation của tác vụ tách rời không còn được phản ánh nhất quán ở subscription mà Spring quản lý; có thể tạo overlap hoặc thất thoát tác vụ khi shutdown.

Cách đúng:

```java
@Scheduled(fixedDelay = 5_000)
public Mono<Void> correct() {
    return Mono.defer(() -> service.process());
}
```

Không cần `subscribe()` thủ công ở đây: **Spring sẽ subscribe** vào Publisher được trả. `onComplete` lúc này đại diện cho kết thúc của `service.process()`, miễn chính API ấy không tự detach tác vụ vào một worker rồi trả trước completion.

Nếu service cần xử lý dữ liệu, hãy dùng `flatMap`/`concatMap`/`then` theo semantics, không nhét một `subscribe()` vào `doOnNext` rồi bỏ mặc kết quả.

## 8. Vì sao Mono<Void> thường phù hợp hơn Mono<Result>?

Với một tác vụ scheduled chỉ cần thực hiện side effect, `Mono<Void>` truyền tải rõ ý định:

- Có một subscription cho mỗi lượt.
- Khi hoàn tất công việc, pipeline phát `onComplete` mà không cần giá trị.
- Nếu lỗi, pipeline phát `onError` để log/đo và xử lý theo chính sách.
- Không tạo kỳ vọng giả rằng Spring sẽ sử dụng một `Result` trả về.

Ví dụ:

```java
@Scheduled(fixedRate = 10_000)
public Mono<Void> synchronize() {
    return Mono.defer(() ->
            remote.fetchCurrentSnapshot()
                    .flatMap(snapshot -> repository.saveSnapshot(snapshot))
                    .then());
}
```

Đây là **mảnh tích hợp** giả định API reactive thật, không phải JDBC blocking bọc vào `Mono.just`. Nếu `repository.saveSnapshot` trả `Mono<Snapshot>` sau khi lưu thành công, `then()` bỏ giá trị snapshot **sau khi đã hoàn tất việc lưu**.

Không phải `Mono<Result>` bị Spring cấm: nó được phép nếu Publisher deferred/reusable phù hợp. Nhưng **Result không được scheduler đọc**, vì thế dùng `Mono<Result>` chỉ để “trả dữ liệu ra ngoài” sẽ gây hiểu nhầm. Một trường hợp hợp lý giữ `Mono<Result>` là nếu cùng pipeline còn được một consumer thật dùng ở nơi khác; tuy nhiên phương thức `@Scheduled` nên được thiết kế rõ vai trò và không dựa vào side effects ngầm của nhiều subscriptions.

## 9. Cold, hot và tính “subscribe lại được”

Spring yêu cầu reactive Publisher do `@Scheduled` trả về **hỗ trợ nhiều subscription liên tiếp**. Một số lựa chọn:

| Publisher | Đặc điểm | Ý nghĩa với scheduled subscription |
| --- | --- | --- |
| `Mono.defer(...)` | Tạo nguồn mới cho từng subscribe | Phù hợp để công việc chạy mới mỗi tick |
| `Flux.defer(...)` | Tạo Flux mới cho từng subscribe | Phù hợp cho batch/stream hữu hạn được đọc lại |
| `Mono.fromSupplier(...)` | Gọi supplier lúc subscribe | Mỗi tick có giá trị mới, nhưng output vẫn bị bỏ qua nếu không consume |
| `Flux.just("A","B")` | Replay hai giá trị cố định cho từng subscriber | Hợp để minh họa; không truy vấn lại nguồn |
| `Mono.just(service.call())` | `service.call()` bị gọi **trước**, khi lắp ráp Publisher | Sai nếu mong service được gọi mỗi tick |
| `Mono.cache()` | Có thể cache tín hiệu đầu tiên cho các subscriber sau | Có thể không chạy lại side effect mỗi tick |
| Hot `Flux` đang phát trước khi subscriber tới | Có thể phát giá trị không phụ thuộc từng subscription | Mỗi tick có thể chỉ nghe được một phần hoặc không nhận được gì |
| Publisher tiêu thụ một lần | Không có hợp đồng replay | Tick sau có thể thất bại, trống hoặc lệch lifecycle |

Hai điều khác nhau:

**(1) Re-subscribe được** không nhất thiết có nghĩa mỗi lần nhận **dữ liệu mới**. `Flux.just("A","B")` hỗ trợ mọi subscribe nhưng luôn phát A/B cố định.

**(2) Source hot** không nhất thiết tạo một subscription độc lập cho một chu kỳ công việc. Nó có thể là một dòng dữ liệu dài hạn chung với phát giá trị theo thời gian riêng, và không có điểm hoàn tất cho `fixedDelay`.

Muốn nhiệm vụ “mỗi 10 giây kiểm tra trạng thái mới”, hãy để **động tác đọc trạng thái** xảy ra trong `defer`/`fromCallable`/API async khi subscribe, không tính sẵn giá trị lúc Spring đăng ký bean.

Không tự suy diễn mọi kiểu có thể adapt thành Publisher đều dùng được. `ReactiveAdapterRegistry` có thông tin `isDeferred()`; ví dụ `CompletableFuture` thường **không phải loại deferred phù hợp** để trả trực tiếp từ một phương thức reactive `@Scheduled`. Nếu cần Futures riêng, viết service scheduling/worker rõ ràng hoặc chuyển sang Publisher deferred có semantics tương ứng.

## 10. onError sẽ thế nào khi scheduler không có consumer dữ liệu?

Theo Spring Framework reference, khi Publisher của reactive `@Scheduled` phát `onError`:

- Spring **ghi log ở mức `WARN`** cho lỗi subscription.
- Lỗi được xử lý để **những lần subscription theo lịch sau vẫn diễn ra**.
- Vì lỗi thuộc luồng reactive bất đồng bộ/lazy, nó **không được ném từ `Runnable` của scheduler** theo cách synchronous callback exception; vì vậy cơ chế `ErrorHandler` của scheduler **không phải nơi xử lý các lỗi này**.

Đây là khác biệt có thể làm mất giám sát: người vận hành đã đăng ký `ErrorHandler` cho scheduler rồi tưởng mọi exception được đếm, nhưng reactive errors vẫn có thể không đi qua handler đó.

Mẫu bổ sung metric/log theo từng lượt:

```java
@Scheduled(fixedDelay = 10_000)
public Mono<Void> refresh() {
    return Mono.defer(() ->
            service.refresh()
                    .doOnError(error ->
                            metrics.recordFailure("refresh", error))
                    .doOnSuccess(ignored ->
                            metrics.recordSuccess("refresh")));
}
```

Đây là **mảnh tích hợp** cần `service`, `metrics` và imports do ứng dụng cung cấp. Với `Mono<Void>`, `doOnSuccess` có thể được gọi với giá trị `null` khi hoàn tất rỗng; không nên dựa vào tham số như một business result.

Nếu dùng `onErrorResume` để chọn fallback, phải xác định fallback nào thực sự bảo đảm nghiệp vụ. Đừng nuốt lỗi thành `Mono.empty()` rồi coi job đã thực thi đầy đủ. Ngược lại, chỉ cần một lần lỗi không có nghĩa lịch ngừng hoạt động; Spring đã có cơ chế log và tiếp tục subscription về sau.

### Retry phải hữu hạn và không nhân đôi side effect

```java
return Mono.defer(() -> service.refresh())
        .retryWhen(
                Retry.backoff(2, Duration.ofMillis(200))
                     .filter(this::isTransientError)
        )
        .timeout(Duration.ofSeconds(5))
        .then();
```

Đây là **mảnh tích hợp**, cần `reactor.util.retry.Retry`, `java.time.Duration`, `isTransientError` và service thực. Nó minh họa retry có giới hạn và một timeout cho subscription; việc placement của `timeout` và `retryWhen` cần chọn theo mục tiêu deadline tổng thể hay deadline mỗi attempt. Với code ở trên, `timeout` đặt **sau retry**, giới hạn thời gian của toàn bộ chuỗi bao gồm retry.

Ngay cả khi reactive chain kết thúc exceptional/cancel, một external API/DB side effect đã hoàn thành trước đó **không tự rollback**. Công việc cần idempotency, transaction boundary phù hợp, retry policy và quan sát nhất quán.

## 11. fixedRate, cron và fixedDelay ảnh hưởng subscription khác nhau

Các thuộc tính `@Scheduled` quy định **khi nào Spring bắt đầu một lượt subscription**, không biến `Flux` thành event stream tự có kết quả trả về.

| Lịch | Ý nghĩa khi method trả Publisher | Nguy cơ |
| --- | --- | --- |
| `fixedRate` | Cố gắng kích hoạt định kỳ theo khoảng cách mốc bắt đầu | Nếu pipeline chậm, có thể có nhiều subscription cùng active |
| `cron` | Kích hoạt theo những mốc cron được tính theo múi giờ | Nếu chạy lâu/có nhiều trigger, có thể overlap hoặc miss theo cấu hình |
| `fixedDelay` | Spring chặn scheduled Runnable đến khi subscription kết thúc để tôn trọng delay sau completion | Publisher không terminal có thể giữ scheduler thread rất lâu |

Ví dụ:

```java
@Scheduled(fixedDelay = 5_000)
public Mono<Void> slowJob() {
    return Mono.defer(() -> service.runOnce());
}
```

Nếu `service.runOnce()` hoàn tất sau 20 giây, lượt sau của **lịch này** được tính sau khoảng fixed delay từ lúc subscription trước kết thúc (thời điểm thực có thể còn trễ do tải). Điều đó **không** đồng nghĩa tất cả async work đã hoàn tất nếu `runOnce` detach tác vụ rồi trả sớm.

### Infinite Flux và fixedDelay

Một Publisher như `Flux.interval(...)` vô hạn thường **không phát `onComplete`**:

```java
@Scheduled(fixedDelay = 5_000)
public Flux<Long> dangerous() {
    return Flux.interval(Duration.ofSeconds(1));
}
```

Đây là **phản ví dụ**, cần Reactor/Duration. Với `fixedDelay`, Spring chặn tới khi subscription terminal, nên một Flux vô hạn có thể làm **không có lượt fixedDelay kế tiếp** và giữ tài nguyên scheduler. `onNext` từ `Flux.interval` vẫn bị bỏ qua; nó không tạo ra một workflow có giá trị được tiêu thụ.

Nếu thực sự cần stream dài hạn, hãy định nghĩa lifecycle và subscription management riêng. Với job định kỳ hữu hạn, dùng `take(n)`/`timeout(...)` có chủ đích và xử lý thất bại rõ ràng; không chỉ áp dụng `take(1)` để che một Publisher vô hạn mà không xem business contract.

### fixedRate/cron có thể chồng subscription

Nếu mỗi subscription mất 10 giây nhưng `fixedRate=1_000`, scheduler có thể khởi tạo một lượt mới trong khi lượt trước chưa kết thúc. Khi đó tải đồng thời lên DB/API có thể tăng theo thời gian; cần giới hạn concurrency, single-flight, semaphore/claim hoặc chọn fixedDelay nếu nghiệp vụ cần hoàn tất lượt trước.

Tách scheduler thread và reactive execution threads có thể giúp callback ngắn, **nhưng không tạo thêm công suất vô hạn**. `flatMap` có concurrency limit, `timeout`, rate limiter hoặc bounded resource pool phải dựa trên sức chịu tải downstream.

## 12. Trạng thái, completion, cancellation và shutdown

Spring sẽ hủy scheduled tasks khi bean bị hủy hoặc ApplicationContext đóng; với reactive `@Scheduled`, hạ tầng cũng xử lý các subscription còn active (đặc biệt stream dài hạn/vô hạn). Nhưng cancellation **không chứng minh** mọi side effect bên ngoài đã dừng hoặc được rollback.

Các ranh giới thực tế:

1. **Đã lập lịch**: chưa chắc đã bắt đầu subscription.
2. **Đã subscribe**: nguồn có thể đang chờ API; chưa chắc có `onNext`.
3. **Có onNext**: giá trị không được scheduler tiêu thụ, chưa chắc side effect nghiệp vụ đã commit.
4. **Có onComplete**: chuỗi Publisher được Spring theo dõi đã terminal; ý nghĩa business do cách bạn nối chuỗi quyết định.
5. **Có onError**: subscription hiện tại lỗi, nhưng lịch kế tiếp có thể tiếp tục.
6. **Đã cancel**: Reactor có thể ngừng một số async work, nhưng API ngoài/DB đã nhận request có thể vẫn xử lý.
7. **JVM chết**: lịch và subscription trong RAM không được tự động replay; cần job ledger/broker/outbox nếu phải khôi phục.

Không nên dùng `@Scheduled Flux<T>` để thực hiện giao dịch quan trọng rồi hy vọng các giá trị bị bỏ qua sẽ được Spring lưu để retry. Nếu cần durable job intent, lưu trạng thái bền vững và tách source of truth khỏi cơ chế timer.

## 13. Transaction, thread boundary và context propagation

Reactive pipeline có thể chuyển thread khi dùng `publishOn`/`subscribeOn`, hoặc khi network client hoàn tất trên event-loop thread. Vì vậy:

- Reactive transaction ở Spring (ví dụ `TransactionalOperator` hoặc reactive transaction manager) thường gắn với **Reactor Context** và subscription, không phải cùng cách `ThreadLocal` của imperative transaction.
- `@Transactional` imperative trong lời gọi khởi tạo Publisher không tự bao một subscription xảy ra **về sau trên thread khác**. Cần lựa chọn transaction manager và vị trí transaction boundary đúng loại dữ liệu/driver.
- Không chuyển JPA `EntityManager`/Hibernate session hay JDBC `Connection` bằng cách nhét vào `ThreadLocal` rồi nghĩ nó tự tồn tại trên reactive scheduler.
- MDC/trace/security context cần propagation tương thích Reactor và Spring; không giả định giá trị caller thread tự đi theo mọi signal.
- `doOnNext` có thể chạy trên thread khác `@Scheduled` method; mọi state chung phải thread-safe.

Với database blocking JDBC, đừng gọi trực tiếp trong Reactor event-loop. Nếu buộc tích hợp API blocking, có thể dùng:

```java
return Mono.fromCallable(() -> blockingRepository.load())
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(data -> reactiveService.process(data))
        .then();
```

Đây là **mảnh tích hợp**, cần Reactor `Schedulers` và các service được định nghĩa. `boundedElastic` cô lập tác vụ chặn khỏi những event-loop threads thường nhạy độ trễ; nhưng nó vẫn có giới hạn tài nguyên, queue và memory. Đây **không** phải bằng chứng đã có nonblocking I/O, cũng không thay thế transaction và backpressure.

**Lưu ý:** `subscribeOn` ảnh hưởng nơi nguồn thực thi theo quy tắc Reactor, không tự làm `@Scheduled` values được sử dụng. Nếu cần một chuỗi business result, vẫn phải kết nối bằng `flatMap` trước terminal.

## 14. Kiểm thử: phân biệt việc tạo Publisher, subscription và tiêu thụ giá trị

Một số test chỉ gọi `job.tick()` rồi thấy method trả Flux; như vậy **chưa chắc đã chạy side effect**, vì reactive Publisher lazy.

Cần hai lớp kiểm tra:

### Unit test pipeline

Khi muốn kiểm tra chính các giá trị Flux phát, một consumer test có thể subscribe trực tiếp qua Reactor Test `StepVerifier`:

```java
StepVerifier.create(Flux.just("A", "B"))
        .expectNext("A", "B")
        .verifyComplete();
```

Đây là **mảnh test code**, yêu cầu dependency `reactor-test` và import `reactor.test.StepVerifier`; nó chứng minh một consumer **cụ thể** có thể nhận giá trị. Nó **không chứng minh `@Scheduled` sẽ gửi A/B cho consumer**, vì subscriber của Spring scheduling vẫn bỏ qua onNext.

### Integration test @Scheduled

Dùng một ApplicationContext thật, executor/scheduler test, latch/virtual clock phù hợp để xác nhận:

1. Annotated method được gọi đúng số lần theo Spring version và lifecycle đăng ký.
2. Publisher được subscribe định kỳ, không chỉ constructed một lần.
3. Operator `doOnNext` và side effect upstream có thực thi.
4. Scheduler không có collection output tự động.
5. `onError` của một subscription không ngăn lần sau, đồng thời metric/log nhận lỗi.
6. `fixedDelay` đợi terminal của subscription, không đợi một fire-and-forget child đã tách rời.
7. Stream không terminal được cancel/cleanup khi context đóng.
8. Khi chạy nhiều replicas, có cơ chế ngăn duplicate business side effects nếu cần.

Ví dụ hoàn chỉnh mục 5 là một integration test tối giản: `method invocations=1`, `subscriptions=2` và `upstream onNext=4`. Những counter này giúp xác nhận **điểm bị bỏ qua nằm ở downstream của Spring**, không nằm ở `Flux` upstream.

### Đừng test thời gian bằng Thread.sleep mơ hồ

Với scheduled job, `CountDownLatch`/`Awaitility` và timeouts giúp giảm flaky. Phép đo chính xác trigger timing cần scheduler/clock injectable, hoặc tách logic tính lịch ra khỏi logic reactive để unit-test. Không viết test đợi vô hạn một Flux không terminal.

## 15. Quan sát trong production: đừng đo chỉ “scheduler fired”

Một pipeline reactive theo lịch có thể phát nhiều `onNext` nhưng không có kết quả được lưu. Cần thiết lập các metric rõ:

| Metric/Log | Ý nghĩa | Gợi ý vị trí |
| --- | --- | --- |
| `scheduled_subscription_started_total` | Có bao nhiêu lượt thực sự subscribe | `doOnSubscribe` |
| `upstream_items_total` | Bao nhiêu item được nguồn phát | `doOnNext` trước điểm xử lý |
| `items_applied_total` | Bao nhiêu item đã được nghiệp vụ áp dụng thành công | Sau `flatMap(save)` và confirm |
| `scheduled_subscription_completed_total` | Số lượt hoàn tất bình thường | `doOnComplete` / `doOnSuccess` |
| `scheduled_subscription_error_total` | Số lượt kết thúc lỗi | `doOnError` |
| `scheduled_subscription_cancel_total` | Số lượt bị cancel | `doFinally(signalType)` |
| `processing_duration_seconds` | Thời gian từ subscribe đến terminal | `elapsed`/timer |
| `in_flight_subscriptions` | Số lượt đang hoạt động | Counter tăng ở subscribe, giảm ở finally |

Với `Flux`, `doFinally` nhận `SignalType` như `ON_COMPLETE`/`ON_ERROR`/`CANCEL`; sử dụng chúng để phân biệt kết thúc và hủy. Metrics thêm ở một vị trí không nên thay đổi ngữ nghĩa của pipeline; không dùng metric counter như kho trạng thái bền vững.

**Không suy ra “items applied” từ “items emitted”.** Một `onNext` được tạo ra không đồng nghĩa DB write thành công, và một subscription có `onComplete` không chứng minh API ngoài đã ack nếu bạn tách side effect bằng `subscribe()` thủ công.

Nếu job quan trọng, phải lưu completion/idempotency state riêng; metrics là công cụ quan sát chứ không phải nguồn sự thật.

## 16. Những hiểu nhầm phổ biến

1. **“`onNext` bị bỏ qua nghĩa là Flux không chạy.”** Sai; framework subscribe, operator upstream vẫn có thể chạy.
2. **“Spring thu thập giá trị của `Flux<T>` rồi chuyển cho caller.”** Không; không có consumer trả kết quả trong scheduling.
3. **“Method reactive `@Scheduled` chạy lại mỗi tick.”** Spring tạo Publisher một lần, rồi subscribe nhiều lần; side effect trong thân method có thể chỉ chạy lúc đăng ký.
4. **“`Mono.just(remote.call())` gọi remote mỗi tick.”** Không; lời gọi Java có thể chạy trước khi Mono được tạo.
5. **“`Flux.defer` khiến Spring tự lưu onNext.”** Không; nó chỉ trì hoãn tạo nguồn cho mỗi subscribe.
6. **“Dùng `doOnNext` để ghi dữ liệu async bằng subscribe lồng là đủ.”** Side effect có thể detach và không được chờ/hủy/quan sát.
7. **“Gọi `subscribe()` trong @Scheduled rồi trả `Mono.empty()` giúp xử lý nhanh hơn.”** Chỉ báo complete sớm và làm mất liên kết lifecycle.
8. **“Một source hot không terminal phù hợp fixedDelay.”** Nó có thể giữ scheduler Runnable vô thời hạn.
9. **“`onError` chắc chắn dừng mọi lần lịch sau.”** Spring log `WARN` và tiếp tục scheduled subscriptions.
10. **“Scheduler `ErrorHandler` sẽ bắt mọi lỗi reactive.”** Lỗi tín hiệu Publisher không đi theo đường synchronous Runnable exception.
11. **“`fixedRate` đợi một subscription hoàn tất.”** Nó có thể tạo subscriptions chồng lên nhau nếu tác vụ dài.
12. **“`Mono<Void>` là điều kiện bắt buộc của Spring.”** Không; đây là cách thể hiện ý định tốt khi kết quả không được dùng.
13. **“`CompletableFuture` thích hợp vì adapt được sang Publisher.”** Spring yêu cầu type reactive deferred; CompletableFuture thường không thỏa điều kiện đó.
14. **“`then()` luôn bỏ qua toàn bộ công việc trước.”** Không; nó đợi upstream terminal nhưng không phát onNext cuối.
15. **“Một tick đã subscribe nghĩa là side effect đã hoàn tất.”** Không; cần terminal/completion và trạng thái nghiệp vụ.
16. **“Retry vô hạn luôn an toàn vì onError sẽ được Spring log.”** Có thể làm một lượt không terminal, trùng side effects và tăng tải.
17. **“Subscription bị cancel đồng nghĩa external API rollback.”** Không có bảo đảm như vậy.
18. **“Reactive @Scheduled tự lưu hàng đợi qua JVM crash.”** Không; phải có job store/outbox/broker nếu cần recovery.

## 17. Câu hỏi tự kiểm tra

**Câu 1.** Tại sao `Flux.just("A", "B")` trong `@Scheduled` vẫn chạy nhưng không có service nào nhận A/B?  
**Đáp án:** Spring subscribe để thực thi job và bỏ qua các payload `onNext` ở ranh giới subscriber scheduling. `onComplete`/`onError` vẫn có ý nghĩa lifecycle.

**Câu 2.** Tại sao `doOnNext` vẫn có thể tăng metric dù onNext bị bỏ qua?  
**Đáp án:** `doOnNext` là operator upstream, chạy khi Publisher phát giá trị trước khi tới subscriber cuối của Spring.

**Câu 3.** Trong demo, method gọi 1 lần, subscriptions 2 lần, onNext 4 lần. Có phải Spring đã đọc và lưu 4 giá trị không?  
**Đáp án:** Không. `Probe` do ứng dụng chủ động ghi counter; Spring không giữ một collection giá trị onNext.

**Câu 4.** Muốn thật sự lưu `Data` do `remote.fetch()` phát, chọn `flatMap(store::persist).then()` hay chỉ `return remote.fetch()`?  
**Đáp án:** Dùng `flatMap` nối thao tác persist trước `then()`; chỉ trả `fetch()` khiến giá trị kết quả bị scheduler bỏ qua.

**Câu 5.** Phương thức `@Scheduled` có gọi lại để tạo Mono mới trên mỗi tick không?  
**Đáp án:** Không theo cơ chế Spring 6.1+ này: framework tạo Publisher một lần rồi subscribe lặp. Dùng `defer`/`fromSupplier` cho công việc cần tính lại mỗi subscription.

**Câu 6.** Vì sao `@Scheduled(fixedDelay=...)` với `Flux.interval(...)` vô hạn có vấn đề?  
**Đáp án:** Với fixedDelay, Spring chờ subscription terminal; Flux vô hạn không terminal nên có thể không có lượt kế tiếp.

**Câu 7.** Một reactive job phát `onError`. `ErrorHandler` của task scheduler có phải nơi nhận lỗi đó không?  
**Đáp án:** Không theo Spring reference; lỗi reactive được Spring log `WARN` và recovered để lần sau vẫn có thể subscribe.

**Câu 8.** Có nên gọi `service.process().subscribe()` rồi trả `Mono.empty()` trong method scheduled không?  
**Đáp án:** Không; nó tạo subscription tách rời mà scheduler không chờ hoàn tất, không quản lý cùng lifecycle và có thể chồng lấp việc.

**Câu 9.** Nếu muốn công việc tiếp diễn sau crash, chỉ dùng `Mono<Void>` có đủ không?  
**Đáp án:** Không. Reactive completion không phải durable storage; cần job ledger, recovery/retry và idempotency.

**Câu 10.** Một phương thức scheduled có trả `Flux<T>` bị cấm không?  
**Đáp án:** Không. Miễn Publisher hỗ trợ nhiều subscription và type thỏa điều kiện Spring, nhưng `T` phát qua `onNext` không được Spring scheduling sử dụng.

**Ghi nhớ:** **Trong reactive @Scheduled, Spring subscribe để thực thi chứ không subscribe để lấy dữ liệu.** `onNext` có thể được phát và operator upstream vẫn chạy, nhưng payload bị bỏ qua ở ranh giới scheduling. Nếu dữ liệu cần lưu hoặc xử lý, hãy **consume bằng operator trong cùng pipeline**, rồi trả một Publisher biểu thị completion/lỗi thực sự, thường là `Mono<Void>`.

### Tài liệu tham khảo

- [Spring Framework Reference — @Scheduled on reactive methods and Kotlin suspending functions](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework 6.2.9 — @Scheduled JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework — ScheduledAnnotationReactiveSupport JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/annotation/ScheduledAnnotationReactiveSupport.html)
- [Project Reactor Reference — Operators and error handling](https://projectreactor.io/docs/core/release/reference/)
- [Reactive Streams specification](https://www.reactive-streams.org/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-scheduling-005">Tín hiệu onError được ghi nhận và lịch vẫn có thể tiếp tục</a>

<details>
<summary>Xem chi tiết</summary>

# Tín hiệu onError được ghi nhận và lịch vẫn có thể tiếp tục trong @Scheduled reactive

Trong **Spring Framework 6.1+**, một phương thức được đánh dấu `@Scheduled` có thể trả về `Publisher` của Reactive Streams, chẳng hạn `Mono<Void>` hoặc `Flux<T>` của Project Reactor. Điều cần hiểu nhất không phải chỉ là “lỗi xảy ra ở đâu”, mà là **lỗi có kết thúc toàn bộ lịch không**. Khi một **subscription** phát ra tín hiệu kết thúc `onError`, Spring **ghi nhận lỗi ở mức WARN** và xử lý nó ở ranh giới của subscription. **Các lần subscription theo lịch tiếp theo vẫn có thể xảy ra** theo `cron`/`fixedRate`/`fixedDelay` đã cấu hình.

Khác với một exception được ném trực tiếp từ thân `Runnable` đồng bộ, `onError` là **tín hiệu bất đồng bộ, terminal của một reactive subscription**. Do vậy, đường `ErrorHandler` vốn dùng cho scheduled `Runnable` thông thường **không tham gia** xử lý tín hiệu này. Điều này không có nghĩa mọi thao tác thất bại sẽ tự được retry trong cùng một lần chạy, hay mọi job tiếp theo chắc chắn thành công: nó chỉ có nghĩa một `onError` của một subscription **không tự làm tắt toàn bộ lịch đăng ký**.

Bài thuộc nhóm **Lập lịch reactive** của module **Spring Framework - concurrency**. Trọng tâm là cơ chế `onError`, cách Spring giữ lịch, sự khác nhau giữa **một lần subscribe mới** và **retry trong cùng một lần subscribe**, các trường hợp `Mono.defer`/publisher nóng–lạnh, các operator xử lý lỗi, kiểm thử và quyết định độ tin cậy trong production.

## 1. Mục tiêu học tập

Sau bài học, bạn có thể:

- Trình bày ba tín hiệu chính `onNext`, `onComplete`, `onError` và quy tắc một subscription chỉ có một terminal signal.
- Giải thích cách `@Scheduled` reactive của Spring 6.1+ lấy publisher **một lần** rồi subscribe lặp theo lịch.
- Phân biệt lần subscription phát `onError` với việc method `@Scheduled` ném exception khi Spring **khởi tạo publisher**.
- Biết Spring ghi `onError` ở mức `WARN` và không sử dụng `ErrorHandler` đồng bộ cho lỗi dạng tín hiệu.
- Hiểu vì sao một subscription lỗi vẫn có thể được theo sau bởi subscription mới, nhưng không có cam kết xử lý công việc bị lỗi thành công.
- Viết `Mono.defer` hoặc `Mono.fromCallable` để thao tác thực sự được khởi động khi **subscribe**, không phải chỉ lúc bean được quét.
- Phân biệt `doOnError`, `onErrorResume`, `onErrorReturn` và `retryWhen` theo mục tiêu quan sát, fallback hoặc retry.
- Hiểu ý nghĩa `fixedDelay` (Spring chờ subscription hoàn thành để giữ delay) so với `fixedRate` và `cron` (có thể xuất hiện subscription đồng thời).
- Kiểm thử bằng publisher tái subscribe, `StepVerifier`, latch và integration test Spring thay vì phỏng đoán bằng `Thread.sleep`.
- Thiết kế metrics, timeout, backoff, idempotency và durable job khi “các lượt sau vẫn tiếp tục” là chưa đủ cho yêu cầu nghiệp vụ.

## 2. Reactive Streams: lỗi là tín hiệu terminal, không phải return value

Một `Publisher<T>` có thể phát các tín hiệu theo trình tự hợp lệ. Subscriber trước hết nhận `onSubscribe(Subscription)`, từ đó có thể `request(n)` để yêu cầu dữ liệu và `cancel()` để dừng. Sau đó có thể có `onNext(T)`, rồi một trong hai tín hiệu terminal:

~~~text
Publisher<T>
    |
    +-- onSubscribe
    |
    +-- onNext(item 1) --- onNext(item 2) --- onComplete
    |
    +-- onNext(item 1) ---------------------- onError(exception)
    |
    +-- onError(exception)  (có thể lỗi trước onNext)
~~~

Trong cùng **một subscription**:

- `onComplete` báo luồng đã hoàn thành bình thường.
- `onError` báo luồng kết thúc thất bại; nó **terminal**.
- Sau `onError` không có `onNext` hoặc `onComplete` tiếp tục hợp lệ cho **subscription đó**.
- Nếu một publisher hỗ trợ subscribe lại, một **subscription mới** có vòng đời signal độc lập; nó không phải sự tiếp tục của subscription cũ đã kết thúc.

Điều này giúp hiểu rõ câu **“onError được ghi nhận và lịch vẫn có thể tiếp tục”**: Spring có thể **tạo subscription mới ở tick kế tiếp**, không phải làm sống lại subscription đã phát `onError`.

### Exception ném ra và onError không đồng nhất

Ví dụ Reactor:

~~~java
Mono<String> failed = Mono.error(
        new IllegalStateException("remote service unavailable"));
~~~

Đây là **fragment Java Reactor**, cần dependency `reactor-core` và import `reactor.core.publisher.Mono`. Nó tạo một `Mono` mà khi được subscribe sẽ phát `onError`. Trái lại:

~~~java
String value = service.callRemote(); // Có thể ném ngay tại đây
Mono<String> publisher = Mono.just(value);
~~~

Đây là **mẫu minh họa**: nếu `callRemote()` ném exception **trước khi `Mono` được tạo**, exception là một lỗi đồng bộ tại thời điểm gọi method. Trong `@Scheduled` reactive, method tạo publisher có thể bị Spring gọi lúc xử lý bean, nên lỗi tại đây khác với tín hiệu `onError` khi subscription chạy.

## 3. Cơ chế @Scheduled reactive của Spring Framework 6.1+

Tài liệu Spring Framework mô tả hành vi cốt lõi:

1. Phương thức `@Scheduled` reactive **không được gọi lại từ đầu ở mỗi tick** theo cách thường nghĩ về scheduled void method.
2. Spring **lấy một Publisher** từ method được annotate trong giai đoạn đăng ký.
3. Spring đăng ký một `Runnable` nội bộ để **subscribe vào publisher đó** mỗi khi lịch đến hạn.
4. Giá trị `onNext` do publisher phát ra bị Spring **bỏ qua**, tương tự giá trị trả về của scheduled method đồng bộ không được dùng.
5. `onComplete` kết thúc subscription hiện tại bình thường; scheduler có thể chờ hoặc tính lượt tiếp theo tùy kiểu lịch.
6. `onError` kết thúc subscription hiện tại ở đường lỗi; Spring ghi `WARN`, **không chuyển lỗi đó qua `ErrorHandler` của `Runnable`**, và tiếp tục cho phép các lần subscription đã được cấu hình sau đó.

~~~text
Spring context / bean post-processor
          |
          +-- invoke reactive @Scheduled method ONCE
          |            |
          |            +-- obtain Publisher P
          |
          +-- register scheduled subscription Runnable
                         |
                 scheduled tick #1
                         |
                         +-- P.subscribe() --> onError
                         |                       |
                         |                  Spring logs WARN
                         |                       |
                         |                 this subscription ends
                         |
                 scheduled tick #2
                         |
                         +-- P.subscribe() --> onComplete
                         |
                 scheduled tick #3
                         |
                         +-- P.subscribe() --> onError
                                               |
                                          Spring logs WARN
~~~

Mỗi tick là **một subscription mới**, không phải một **instance Publisher mới**. Vì vậy bản thân publisher phải chấp nhận các lần subscribe tiếp theo; nếu bạn tạo một đối tượng không hỗ trợ subscribe lặp, hợp đồng `@Scheduled` reactive không được đáp ứng.

**Điểm khác biệt rất quan trọng:** “lịch vẫn chạy” chỉ nói về khả năng **subscribe trong tương lai**. Nó không có nghĩa Spring tự gửi lại một request HTTP thất bại, tự bắt đầu transaction bù, tự retry với backoff hoặc lưu task vào database.

## 4. Vì sao onError được ghi WARN và lịch không dừng?

Đối với phương thức `@Scheduled` đồng bộ:

~~~java
@Scheduled(fixedRate = 5000)
public void regularTask() {
    service.process(); // Có thể ném RuntimeException ra ngoài Runnable
}
~~~

Đây là **mẫu Spring minh họa**, cần `service` do ứng dụng định nghĩa. Scheduler gọi method từ một task `Runnable`; exception của method có thể đi qua đường error handling của scheduler. Cách scheduler có tiếp tục chạy sau lỗi còn phụ thuộc loại trigger, executor và `ErrorHandler` được cấu hình.

Đối với reactive method:

~~~java
@Scheduled(fixedRate = 5000)
public Mono<Void> reactiveTask() {
    return Mono.defer(() -> service.processReactive());
}
~~~

Đây cũng là **Spring method fragment**, với `service.processReactive()` trả `Mono<Void>`. Spring lên lịch hành động **subscribe vào Mono**, còn `Mono` báo lỗi qua `onError`. Lỗi không phải exception được ném trực tiếp ra khỏi `Runnable` scheduling cùng cách như method đồng bộ.

Do đó:

- Spring **ghi log `WARN`** khi subscription phát `onError`.
- Đường contract `org.springframework.util.ErrorHandler` của synchronous scheduled `Runnable` **không được dùng cho reactive `onError`**.
- Spring không phải dừng toàn bộ periodic registration chỉ vì terminal error của **một** subscription.
- Lượt subscription sau vẫn có cơ hội chạy khi scheduled task còn active và ứng dụng còn sống.

Đây là hành vi được nêu trong **Spring Framework reference và Javadoc `@Scheduled`** (hỗ trợ từ Spring 6.1), không phải suy luận từ một Reactor operator.

### Có phải Spring “recover” lỗi nghiệp vụ?

**Không.** Từ “recovered” trong tài liệu Spring nói về việc subscriber/scheduling adapter xử lý tín hiệu để **không làm hỏng lịch tiếp theo**. Không phải mọi lỗi đã được giải quyết. Ví dụ một API gửi hóa đơn trả 503 ở tick #1:

- Tick #1 kết thúc với `onError`; không có bằng chứng hóa đơn đã gửi.
- Spring ghi cảnh báo và vẫn có thể subscribe tại tick #2.
- Tick #2 có thể xử lý một batch khác và thành công.
- Hóa đơn bị lỗi ở tick #1 **có thể vẫn chưa được gửi** nếu không có queue/job state/retry thích hợp.

Vì thế phải phân biệt **schedule health** với **business delivery health**.

## 5. Tạo Publisher đúng: Mono.defer để xử lý mỗi subscription

Một bẫy phổ biến:

~~~java
private final AtomicInteger counter = new AtomicInteger();

@Scheduled(fixedRate = 5000)
public Mono<Void> run() {
    int attempt = counter.incrementAndGet(); // CÓ THỂ CHỈ TĂNG MỘT LẦN
    return Mono.fromRunnable(() ->
            System.out.println("attempt=" + attempt));
}
~~~

Đây là **mã Spring Reactor minh họa**. Vì Spring lấy `Publisher` một lần khi đăng ký, `counter.incrementAndGet()` nằm **ngoài publisher** và có thể chỉ được thực hiện **một lần**. Mỗi tick subscribe lại sẽ in **cùng attempt** nếu publisher chứa captured value.

Cách đúng khi muốn cập nhật state cho từng subscription:

~~~java
private final AtomicInteger counter = new AtomicInteger();

@Scheduled(fixedRate = 5000)
public Mono<Void> run() {
    return Mono.defer(() -> {
        int attempt = counter.incrementAndGet();
        return Mono.fromRunnable(() ->
                System.out.println("attempt=" + attempt));
    });
}
~~~

Ở đây `Mono.defer` nhận một supplier được gọi tại **thời điểm subscribe**, nên tick #1, #2, #3 có thể quan sát state mới. `Mono.fromRunnable` cũng là publisher mang tính deferred cho phần thân Runnable.

### Phân biệt Mono.error và throw trong lúc tạo publisher

Hai tình huống:

~~~java
@Scheduled(fixedRate = 5000)
public Mono<Void> wrongBoundary() {
    throw new IllegalStateException("cannot create publisher");
}
~~~

Đây là **mã Spring minh họa thất bại tại giai đoạn tạo publisher**, không phải một subscription phát `onError`. Có thể làm đăng ký bean/lịch thất bại thay vì được Spring log WARN và tiếp tục.

~~~java
@Scheduled(fixedRate = 5000)
public Mono<Void> correctSignalBoundary() {
    return Mono.defer(() ->
            Mono.error(new IllegalStateException("request failed")));
}
~~~

Đây là **mẫu phân biệt semantics**, không phải nghiệp vụ mong muốn. Mỗi subscription phát `onError`, nên Spring xử lý ở đường reactive `WARN`. Nếu lỗi là tạm thời, subscription tiếp theo vẫn có thể xảy ra. Nếu nguồn luôn thất bại, **mỗi lượt tiếp tục lỗi**, dẫn tới cảnh báo lặp và không có tiến triển nghiệp vụ.

**Quy tắc:** logic phát sinh I/O, đọc dữ liệu thay đổi hoặc có thể ném lỗi theo từng lượt cần được **defer vào reactive pipeline**. Việc trả publisher không đồng nghĩa mọi biểu thức Java ở trước lệnh `return` đều lazy.

## 6. Ví dụ Spring: hai lần lỗi, các lượt sau thành công

Tệp **ReactivePollingJob.java**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ReactivePollingJob {
    private final AtomicInteger attempt = new AtomicInteger();

    @Scheduled(
            initialDelay = 0,
            fixedRateString = "${app.jobs.poll.rate:PT5S}")
    public Mono<Void> poll() {
        return Mono.defer(() -> {
            int current = attempt.incrementAndGet();

            if (current <= 2) {
                return Mono.<Void>error(
                        new IllegalStateException(
                                "temporary failure #" + current));
            }

            return Mono.<Void>fromRunnable(() ->
                    System.out.println("poll succeeded #" + current));
        });
    }
}
~~~

Đây là **Spring component hoàn chỉnh ở mức class**, cần Spring Framework 6.1+ và Reactor `reactor-core`. Ứng dụng cần bật `@EnableScheduling` ở cấu hình hoặc ứng dụng. `PT5S` là 5 giây. Khi Spring bắt đầu scheduling:

- Subscription đầu: `attempt=1`, Mono phát `onError`. Spring ghi WARN.
- Subscription thứ hai: `attempt=2`, Mono tiếp tục phát `onError`. Spring ghi WARN.
- Subscription thứ ba: `attempt=3`, Mono hoàn tất và có thể in `poll succeeded #3`.
- Các subscription sau có thể thành công với số thứ tự tăng dần.

Đây là **một kịch bản có thể kiểm thử**, không phải lời hứa timestamp in stdout chính xác trong Spring vì tick thật phụ thuộc scheduler, lifecycle, shutdown và concurrency. AtomicInteger hỗ trợ truy cập an toàn nếu có subscription overlap.

### Cấu hình bật scheduling

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
~~~

Đây là **Spring configuration class**. `@EnableScheduling` kích hoạt việc xử lý `@Scheduled` trên Spring-managed beans. Cần tránh tạo hai ApplicationContexts cùng đăng ký một job hoặc đăng ký một phương thức nhiều lần nếu không có chủ đích.

### Điều gì xảy ra nếu không dùng Mono.defer?

Nếu bạn viết:

~~~java
@Scheduled(fixedRate = 5000)
public Mono<Void> poll() {
    int current = attempt.incrementAndGet();
    return current <= 2
            ? Mono.error(new IllegalStateException("bad #" + current))
            : Mono.empty();
}
~~~

Spring có thể nhận **một Mono lỗi cố định** khi đăng ký và subscribe lại vào Mono đó mãi, không tái gọi method để tăng `attempt`. Hậu quả là mọi tick đều thấy cùng lỗi `bad #1`. Để thay đổi hành vi theo từng lượt, dùng `Mono.defer`/`fromCallable` hoặc reactive source thực sự hỗ trợ repeated subscription và tái thực thi công việc ở mỗi lần subscribe.

## 7. Ví dụ Java 17+ chạy độc lập: onError của subscription cũ không cấm subscribe mới

Java SE 17 có `java.util.concurrent.Flow.Publisher`, `Flow.Subscriber` và `Flow.Subscription`. API này **khác** kiểu `org.reactivestreams.Publisher` mà Spring/Reactor dùng, nên chương trình sau **không phải implementation của Spring `@Scheduled`**. Nó chỉ minh họa vòng đời nhiều subscription một cách xác định, với cùng một publisher: hai subscription báo `onError`, hai subscription tiếp theo hoàn tất.

Tệp **RepeatedSubscriptionDemo.java**:

~~~java
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

public final class RepeatedSubscriptionDemo {
    static final class ColdAttemptPublisher
            implements Flow.Publisher<String> {
        private final AtomicInteger attempts = new AtomicInteger();

        ColdAttemptPublisher() {
            System.out.println("publisherCreated=1");
        }

        @Override
        public void subscribe(Flow.Subscriber<? super String> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                private boolean requested;

                @Override
                public void request(long n) {
                    if (requested) {
                        return;
                    }
                    requested = true;

                    if (n <= 0) {
                        subscriber.onError(new IllegalArgumentException(
                                "request must be positive"));
                        return;
                    }

                    int attempt = attempts.incrementAndGet();
                    if (attempt <= 2) {
                        subscriber.onError(new IllegalStateException(
                                "temporary failure"));
                    } else {
                        subscriber.onNext("OK-" + attempt);
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    requested = true;
                }
            });
        }

        int attempts() {
            return attempts.get();
        }
    }

    public static void main(String[] args) {
        ColdAttemptPublisher publisher = new ColdAttemptPublisher();
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();

        for (int i = 1; i <= 4; i++) {
            int tick = i;

            publisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(1);
                }

                @Override
                public void onNext(String item) {
                    System.out.println("tick" + tick + ":onNext=" + item);
                }

                @Override
                public void onError(Throwable throwable) {
                    errors.incrementAndGet();
                    System.out.println("tick" + tick + ":onError="
                            + throwable.getClass().getSimpleName());
                }

                @Override
                public void onComplete() {
                    completed.incrementAndGet();
                    System.out.println("tick" + tick + ":onComplete");
                }
            });
        }

        System.out.println("subscriptions=" + publisher.attempts());
        System.out.println("errors=" + errors.get());
        System.out.println("completions=" + completed.get());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac RepeatedSubscriptionDemo.java
java RepeatedSubscriptionDemo
~~~

Stdout được xác định:

~~~text
publisherCreated=1
tick1:onError=IllegalStateException
tick2:onError=IllegalStateException
tick3:onNext=OK-3
tick3:onComplete
tick4:onNext=OK-4
tick4:onComplete
subscriptions=4
errors=2
completions=2
~~~

**Cách đọc:** đối tượng publisher chỉ tạo một lần, nhưng mỗi lời gọi `subscribe` tạo một subscription riêng và tăng số lần xử lý khi `request(1)`. `onError` ở tick 1 không làm publisher bị “cấm subscribe”; tick 3 vẫn có thể hoàn thành thành công. Nếu publisher thực tế là hot/single-use, giả định này không còn đúng — đó là lý do Spring yêu cầu **publisher hỗ trợ repeated subscription**.

**Giới hạn ví dụ:** chương trình gọi subscribe trong vòng lặp đồng bộ; nó không triển khai cron, fixed delay, scheduler thread, Reactor `Mono` hay WARN logging của Spring. Không dùng demo Flow này làm bằng chứng end-to-end cho một Spring Reactive ApplicationContext.

## 8. Spring ghi nhận onError và ErrorHandler không được gọi: phân biệt rõ

Một nhầm lẫn thường thấy là cấu hình `ThreadPoolTaskScheduler.setErrorHandler(...)` rồi chờ nó nhận exception do `Mono.error(...)` phát ra. Với phương thức `@Scheduled` **reactive**, tài liệu Spring nêu rõ:

> onError được log ở WARN và các lần subscription tiếp theo vẫn có thể diễn ra; ErrorHandler của Runnable không xử lý lỗi tín hiệu này.

Đây là **tóm lược**, không phải sao chép nguyên văn tài liệu. Thực thi trên môi trường thật cần kiểm tra Spring version và logging configuration. Vì thế:

~~~java
@Bean
ThreadPoolTaskScheduler scheduler(ErrorHandler handler) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setErrorHandler(handler);
    return scheduler;
}
~~~

Đây là **fragment Spring** với các import cần thiết. Nó có thể dùng cho lỗi của scheduled Runnable đồng bộ, nhưng **không phải nơi bắt buộc xuất hiện một `onError` phát ra từ Reactor publisher của `@Scheduled` reactive**.

Đường đúng để nhìn thấy lỗi nghiệp vụ là **trong reactive pipeline**:

~~~java
@Scheduled(fixedRate = 5000)
public Mono<Void> poll() {
    return Mono.defer(() -> remoteClient.fetchChanges()
            .then())
            .doOnError(error -> monitoring.recordFailure(error));
}
~~~

Đây là **Spring/Reactor fragment** với `remoteClient` và `monitoring` do ứng dụng định nghĩa. `doOnError` quan sát và có thể ghi metric/log riêng; nếu callback không thay thế tín hiệu, `onError` vẫn đi xuống subscriber Spring, nên Spring có thể tiếp tục log WARN.

### Có thể ghi log hai lần

Nếu bạn vừa `doOnError(log::warn)` vừa để Spring nhận `onError`, cùng một lỗi có thể có **hai dòng log**. Nếu mục tiêu là metrics, hãy dùng event/metric chuyên biệt và có correlation ID; nếu mục tiêu là log nguyên nhân, kiểm soát duplicate, stack trace và payload nhạy cảm.

### Nếu ErrorHandler có custom policy dừng lịch?

Policy đó không quyết định một `onError` reactive như nó quyết định một exception ném từ scheduled Runnable. Vì vậy **đừng dùng ErrorHandler duy nhất** làm cơ chế stop/retry/circuit breaker cho scheduled reactive method. Thiết kế operator-level policy và quản trị scheduled registration riêng.

## 9. Các operator Reactor để quan sát và phục hồi

Bốn operator dễ nhầm:

| Operator | Khi có lỗi | Tác động lên subscriber Spring |
| --- | --- | --- |
| `doOnError(callback)` | Thực hiện side effect để log/metric; **không thay thế lỗi** | Spring vẫn nhận `onError` nếu không có recovery phía sau |
| `onErrorResume(fn)` | Chuyển sang publisher fallback | Spring có thể chỉ nhận `onComplete` nếu fallback thành công |
| `onErrorReturn(value)` | Trả một giá trị fallback rồi hoàn thành bình thường | Spring bỏ qua `onNext` trả về; phù hợp hay không tùy business semantics |
| `retryWhen(Retry...)` | Resubscribe upstream trong **một scheduled subscription** theo retry policy | Nếu retry thành công, Spring không thấy lỗi cuối; nếu hết retry, `onError` vẫn lan ra |
| Không gắn operator | `onError` đi thẳng đến subscriber Spring | Spring ghi WARN; lịch không tự dừng chỉ vì tín hiệu này |

### 9.1 doOnError chỉ quan sát

~~~java
public Mono<Void> runOnce() {
    return remoteClient.sendPending()
            .doOnError(error -> metrics.incrementFailure())
            .then();
}
~~~

Đây là **mã Reactor ứng dụng**; giả sử `remoteClient.sendPending()` trả publisher hợp lệ. Nếu nguồn `sendPending` lỗi, `doOnError` chạy và terminal error vẫn lan ra. Trong `@Scheduled` reactive, Spring nhận lỗi để ghi WARN, trong khi metric đã được ứng dụng ghi.

`doOnError` không tự catch rồi chuyển exception thành success. Không giả định nó tương đương `try/catch` có return giá trị dự phòng.

### 9.2 onErrorResume phục hồi trong cùng subscription

~~~java
@Scheduled(fixedRate = 5000)
public Mono<Void> run() {
    return Mono.defer(() -> remoteClient.sendPending())
            .onErrorResume(RemoteServiceUnavailableException.class,
                    error -> useLocalFallback())
            .then();
}
~~~

Đây là **fragment Spring/Reactor**, các class/service do ứng dụng định nghĩa. Nếu lỗi khớp type và fallback thành công, subscription hoàn thành bình thường; Spring **không nhận `onError` cuối** của lượt đó. Điều này có thể đúng với cache stale chấp nhận được, nhưng có thể nguy hiểm nếu gửi notification thất bại mà fallback chỉ làm rỗng.

### 9.3 onErrorResume(... -> Mono.empty()) có thể che mất lỗi

~~~java
@Scheduled(fixedRate = 5000)
public Mono<Void> run() {
    return Mono.defer(() -> paymentService.chargePending())
            .onErrorResume(error -> Mono.empty());
}
~~~

Đây là **ví dụ anti-pattern** khi `paymentService.chargePending()` có side effect quan trọng. Tất cả lỗi đều bị biến thành hoàn thành bình thường; Spring không ghi WARN cho tín hiệu lỗi đã bị nuốt. Nếu không có metric/job-state, người vận hành có thể nghĩ task luôn thành công.

**Không tự động áp fallback rỗng cho thanh toán, đồng bộ dữ liệu bắt buộc hoặc quy trình có SLA.** Hãy quyết định lỗi nào được bỏ qua, lỗi nào cần retry, lỗi nào phải lưu lại để reconciliation.

### 9.4 retryWhen: retry trong một subscription, không phải tick kế

~~~java
@Scheduled(fixedDelayString = "${jobs.delivery.delay:PT30S}")
public Mono<Void> deliver() {
    return Mono.defer(() -> deliveryService.deliverBatch())
            .retryWhen(
                    reactor.util.retry.Retry.backoff(
                            2, java.time.Duration.ofMillis(200)))
            .then();
}
~~~

Đây là **fragment Spring/Reactor**, trong đó `deliverBatch()` là API trả publisher của ứng dụng. Trong một lần scheduler subscribe:

- Reactor thử upstream lần đầu.
- Nếu lỗi, `retryWhen` có thể tạo tối đa hai lần **retry bổ sung** sau backoff theo policy.
- Nếu một lần retry thành công, subscription có thể hoàn thành bình thường.
- Nếu hết retry mà vẫn lỗi, terminal `onError` đến subscriber Spring; Spring log WARN và có thể subscribe mới ở tick sau.

**Không nhầm 2 retry trong một lượt với 2 tick của @Scheduled.** Một tick có thể thực hiện **3 lần gọi nghiệp vụ** (lần đầu + hai retry), và tick sau lại có thể retry tiếp. Với thao tác không idempotent, đó có thể là **nhiều side effect trùng lặp**.

### 9.5 Retry nên lọc loại lỗi

Không phải mọi lỗi đều transient:

- Kết nối mạng, 503, rate limit: có thể retry với backoff, timeout và jitter hợp lý.
- Validation, dữ liệu không hợp lệ, authorization: thường không nên retry vô hạn.
- Transaction conflict: có thể retry nếu thao tác idempotent và policy phù hợp.
- Hủy/subscription cancellation: không tự retry như lỗi service thông thường.

Có thể dùng `Retry.backoff(...).filter(...)` để giới hạn loại lỗi. Nếu retry vẫn thất bại, nên giữ `onError` cho monitoring hoặc chuyển job sang trạng thái retryable/dead-letter rõ ràng.

## 10. Tại sao “lịch vẫn tiếp tục” khác với “retry”?

Hai cơ chế có trục thời gian khác:

~~~text
A. Không có retryWhen
  Tick 1 subscribe ---> API lỗi ---> onError ---> Spring WARN
  Tick 2 subscribe ---> API thành công ---> onComplete
  Tick 3 subscribe ---> API thành công ---> onComplete

B. Có retryWhen(2, backoff)
  Tick 1 subscribe
      |
      +-- attempt 1 lỗi
      +-- retry #1 lỗi
      +-- retry #2 lỗi
      +-- onError cuối ---> Spring WARN
  Tick 2 subscribe ---> attempts mới theo pipeline
~~~

Khi nói “lịch vẫn có thể tiếp tục”, tài liệu Spring chỉ đảm bảo **cơ chế retry scheduling/subscription qua tick** không dừng do một lỗi tín hiệu. Nó **không** đồng nghĩa Reactor `retryWhen` đã được Spring tự thêm vào publisher.

### Thiết kế bảng so sánh

| Câu hỏi | Subscription kế tiếp từ @Scheduled | Retry trong reactive pipeline |
| --- | --- | --- |
| Ai tạo? | Spring scheduler tạo thêm subscription theo cron/rate/delay | Reactor operator resubscribe upstream |
| Khi nào? | Theo trigger đã đăng ký | Theo policy retry/backoff |
| Có phải giữ job cũ? | Không mặc nhiên | Có cùng ngữ cảnh pipeline, nhưng vẫn cần kiểm soát side effect |
| Có log WARN cho từng lỗi trung gian? | Spring log WARN khi nhận terminal `onError` | Lỗi nội bộ được retry thành công có thể không tới Spring |
| Có tự lưu vào DB? | Không | Không |
| Có ngăn duplicate side effect? | Không | Không |
| Có giới hạn total attempts toàn hệ thống? | Không tự có | Có thể giới hạn mỗi pipeline nhưng nhiều ticks vẫn cộng dồn |

Nếu một event bắt buộc phải được xử lý, job table bền vững với trạng thái mỗi item là cách đáng tin hơn là hy vọng tick kế “sẽ xử lý lại đúng item đó”.

## 11. fixedDelay, fixedRate và cron với reactive Publisher

### 11.1 fixedDelay: phải chờ subscription hoàn thành

Javadoc `@Scheduled` nêu rõ với reactive method và `fixedDelay`, Spring biến subscription trong scheduled Runnable thành **lời gọi chờ hoàn thành** để giữ đúng nghĩa fixed-delay. Vì vậy:

~~~java
@Scheduled(fixedDelay = 5000)
public Mono<Void> refresh() {
    return Mono.defer(() -> remoteClient.refresh());
}
~~~

Đây là **mã minh họa** với `remoteClient.refresh()` trả `Mono<Void>`. Với một registration:

1. Scheduler bắt đầu subscription.
2. Nó đợi subscription báo `onComplete` hoặc `onError`.
3. Sau khi subscription kết thúc, áp fixed-delay 5 giây trước lần kế.
4. Một `onError` vẫn được ghi WARN, rồi có thể có subscription mới theo fixed-delay.

**Hệ quả:** nếu publisher **không bao giờ hoàn tất**, fixed-delay schedule có thể bị chặn và không tới lượt kế. Việc này có thể chiếm thread scheduling trong implementation liên quan. Đừng chạy một `Flux.never()` hoặc Flux vô hạn ở scheduled fixed-delay mà kỳ vọng cứ 5 giây có tick.

Ví dụ nên tránh:

~~~java
@Scheduled(fixedDelay = 5000)
public Flux<Long> neverEnding() {
    return Flux.interval(java.time.Duration.ofSeconds(1));
}
~~~

Đây là **mẫu sai mục đích**: `Flux.interval` tạo luồng không kết thúc bình thường. Để sử dụng như một job định kỳ, hãy giới hạn bằng `take(n)`/`timeout` khi phù hợp hoặc biến thành một `Mono<Void>` hoàn tất sau một đơn vị công việc hữu hạn.

### 11.2 fixedRate: có thể có subscription overlap

~~~java
@Scheduled(fixedRateString = "${jobs.refresh.rate:PT5S}")
public Mono<Void> refresh() {
    return Mono.defer(() -> remoteClient.refresh());
}
~~~

Với fixed-rate, scheduler định kỳ kích hoạt **subscribe**, không nhất thiết chờ publisher trước hoàn thành như fixed-delay. Nếu upstream thực hiện công việc bất đồng bộ kéo dài hơn period, các subscription có thể **đồng thời hoạt động** và truy cập chung tài nguyên. Mức độ overlap cụ thể còn tùy thread pool/scheduler/dispatcher và cách reactive publisher triển khai.

### 11.3 cron: mốc lịch theo giờ

~~~java
@Scheduled(
        cron = "${jobs.cleanup.cron:0 */5 * * * *}",
        zone = "${jobs.cleanup.zone:UTC}")
public Mono<Void> cleanup() {
    return Mono.defer(() -> cleanupClient.cleanup());
}
~~~

Spring cron có **sáu trường**, bao gồm giây. Với cron, lượt chạy theo các mốc do CronTrigger tính, nhưng nếu publisher là async và chưa hoàn tất, subscription có thể overlap tùy đường scheduling. Không mặc định một cron ở mỗi node toàn cluster nghĩa là chỉ một business job.

### Tóm tắt

| Kiểu lịch | Khi nào subscribe tiếp? | Nguy cơ |
| --- | --- | --- |
| `fixedDelay` | Sau khi subscription trước kết thúc và đợi delay | Subscription treo khiến lần kế không đến |
| `fixedRate` | Theo nhịp period của scheduler | Subscription overlap, tăng load |
| `cron` | Theo mốc thời gian cron/trigger | Timezone/DST, overlap, nhiều node |
| Repeated `@Scheduled` annotations | Mỗi khai báo là một registration riêng | Duplicate/overlap ngoài ý muốn |

Lưu ý: `onError` khiến subscription đó kết thúc, nên với fixed-delay, cơ chế chờ có thể giải phóng và tiếp tục chu kỳ sau delay. Điều này **không bảo đảm job thất bại được sửa**.

## 12. Giới hạn năng lực của reactive @Scheduled

Hỗ trợ `@Scheduled` reactive được Spring Framework giới thiệu từ **6.1** cho:

- Method trả `org.reactivestreams.Publisher` hoặc implementation như Reactor `Mono`/`Flux`.
- Type mà `ReactiveAdapterRegistry` chuyển thành Publisher được **và hỗ trợ deferred subscription**, ví dụ một số loại reactive của RxJava.
- Kotlin suspending functions, khi có runtime bridge `kotlinx.coroutines.reactor`.
- Kotlin `Flow`/`Deferred` theo đường hỗ trợ của framework, phụ thuộc bridge/runtime phù hợp.

Các method phải không nhận tham số. Publisher phải có khả năng được subscribe nhiều lần; không phải mọi “async type” đều đủ.

### CompletableFuture không phải lựa chọn tương đương

`CompletableFuture` có thể được adapter chuyển sang Publisher trong một số ngữ cảnh, nhưng adapter của nó **không được mô tả là deferred** trong `ReactiveAdapterRegistry`. Vì thế **`@Scheduled` reactive của Spring không chấp nhận kiểu return này như một deferred reactive type** theo hợp đồng API. Đừng đổi `Mono<Void>` sang `CompletableFuture<Void>` và mong scheduler subscribe lại đúng semantics.

Nếu muốn `@Scheduled` chủ động bắt một `CompletableFuture` mỗi lượt, dùng method `void` thông thường với logic orchestration rõ, hoặc trả `Mono.defer(() -> Mono.fromFuture(...))` với cách tạo Future **tại subscription time**, kiểm tra API Reactor và quản lý cancellation/timeout. Không nhầm `Mono.fromFuture(existingAlreadyStartedFuture)` với deferred creation.

### Publisher nóng và đơn đăng ký

Một publisher “hot” phát dữ liệu không phụ thuộc vào subscriber hoặc chỉ có một dòng sự kiện duy nhất có thể không phù hợp để Spring subscribe lặp. Một `Mono` cold/deferred là lựa chọn dễ hiểu cho **mỗi scheduled tick là một công việc hữu hạn**.

Tuy nhiên, “cold” không tự bảo đảm idempotency hoặc safe concurrency: nếu tick đến trong khi request trước chưa xong, hai subscription vào cùng cold publisher có thể chạy hai lần side effect.

## 13. Exception trong phương thức tạo Publisher khác với onError khi subscribe

Đây là chỗ cần phân biệt trong xử lý lỗi và khởi tạo ứng dụng.

### Trường hợp A: method ném lỗi trước khi trả Publisher

~~~java
@Scheduled(fixedRate = 5000)
public Mono<Void> refresh() {
    throw new IllegalStateException("bad configuration");
}
~~~

Trong quá trình Spring xử lý reactive scheduled method, framework gọi method để lấy publisher. Exception tại đây xảy ra **trước khi có subscription hợp lệ**. Nó không phải một `onError` đã được Spring reactive subscriber ghi WARN và recover theo nhịp. Ứng dụng có thể không khởi tạo/đăng ký lịch thành công.

### Trường hợp B: Mono.defer ném exception khi subscription chạy

~~~java
@Scheduled(fixedRate = 5000)
public Mono<Void> refresh() {
    return Mono.defer(() -> {
        throw new IllegalStateException("transient dependency failure");
    });
}
~~~

Đây là fragment Reactor minh họa. Reactor `Mono.defer` chuyển exception xảy ra trong supplier thành tín hiệu lỗi theo hợp đồng operator. Spring sẽ thấy `onError` khi tick subscribe, ghi WARN và có thể tiếp tục lịch.

### Trường hợp C: lỗi sau một chuỗi bất đồng bộ

~~~java
@Scheduled(fixedRate = 5000)
public Mono<Void> refresh() {
    return Mono.defer(() -> reactiveClient.fetch())
            .publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
            .flatMap(this::writeResult)
            .then();
}
~~~

Đây là **mã tích hợp** với `reactiveClient` và `writeResult` do ứng dụng cung cấp. Lỗi trong một operator async là `onError`; nó có thể được phát trên thread khác với thread scheduler. Việc ghi WARN của Spring vẫn là khi subscriber nhận terminal signal, không cần exception được throw trực tiếp về scheduled Runnable.

**Không nhầm thread:** `publishOn` dịch chuyển các tín hiệu downstream, còn `subscribeOn` ảnh hưởng nơi subscribe upstream. Cách tổ chức scheduler của Reactor không tự đồng nhất với `TaskScheduler` của Spring.

## 14. Quản lý lỗi nghiệp vụ: ghi nhận không đủ để không mất việc

Giả sử có 10 giao dịch cần gửi, tick #1 đọc và gửi 3 giao dịch nhưng giao dịch thứ tư gặp timeout. Publisher phát `onError`. Lịch tick #2 vẫn có thể được subscribe, nhưng sẽ làm gì tùy code:

- Nếu tick #2 đọc một batch mới và bỏ qua phần chưa gửi của tick #1: **có thể mất việc**.
- Nếu tick #2 đọc lại tất cả record trạng thái PENDING: **có thể xử lý lại**.
- Nếu tick #2 đọc lại record đã gửi nhưng chưa đánh dấu DONE: **có thể gửi trùng**.
- Nếu operator có `retryWhen` mà thao tác không idempotent: có thể tạo side effect trùng **trong cùng một tick**.
- Nếu toàn bộ queue là RAM và JVM restart: state có thể mất.

Vì thế một hệ thống đáng tin cần **quản lý mỗi item** độc lập với scheduler:

~~~text
Job table / durable queue
    PENDING
      |
      +--> CLAIMED (lease, owner, attempt, deadline)
                |
                +--> SUCCESS (idempotency key)
                |
                +--> RETRYABLE_FAILURE (next_attempt_at)
                |
                +--> PERMANENT_FAILURE / dead-letter
~~~

`@Scheduled` chỉ là **bộ đánh thức/poller** để tìm việc cần chạy. `onError` được log WARN giúp quan sát lỗi, còn **job state** mới cho biết item nào cần retry/đã hoàn thành.

### Có nên biến mọi lỗi thành Mono.empty?

Không. Nếu nghiệp vụ cho phép bỏ qua một item lỗi không nghiêm trọng, có thể catch/recover ở **mức item** với audit rõ, thay vì nuốt lỗi của **cả batch**. Nếu bỏ qua lỗi không được phép, hãy để `onError` lan ra sau khi cập nhật job state thích hợp.

### Khi nào cần transactional outbox?

Nếu một database transaction tạo dữ liệu rồi kỳ vọng job reactive gửi message/email chắc chắn, một callback/scheduler trong RAM không đảm bảo delivery qua crash. Dùng **transactional outbox** để lưu ý định gửi cùng transaction gốc, rồi scheduled reactive poller đọc outbox có retry/idempotency. `onError` ở tick không làm mất outbox record; tick tiếp có thể đọc lại record chưa được ack.

## 15. Transaction và context trong reactive scheduling

Một `@Scheduled` publisher không tự kế thừa transaction từ HTTP request đã kết thúc. Với reactive data access như R2DBC, transaction context nằm trong **Reactor Context** và cần được thiết lập/propagate trong reactive chain, ví dụ `TransactionalOperator` hoặc `@Transactional` reactive service phù hợp.

Ví dụ mô hình:

~~~java
@Scheduled(fixedDelayString = "${jobs.reconcile.delay:PT30S}")
public Mono<Void> reconcile() {
    return Mono.defer(() -> repository.findPending()
            .concatMap(item -> processor.process(item))
            .then())
            .as(transactionalOperator::transactional);
}
~~~

Đây là **mã tích hợp** với `repository`, `processor` và `transactionalOperator` do ứng dụng cung cấp, không biên dịch độc lập. Đặt transaction bao toàn batch có thể không phù hợp khi batch lớn, có network I/O hoặc long-running; có thể cần transaction ngắn **mỗi item**. Sự an toàn của lock/lease và transaction isolation cần thiết kế dựa trên database thực tế.

### ThreadLocal/MDC và Reactor Context

Các operator có thể chạy trên nhiều threads. Nếu bạn log lỗi trong `doOnError` và muốn có trace/correlation ID, không được mặc định ThreadLocal của request còn tồn tại. Dùng context propagation được cấu hình đúng phiên bản Reactor/Spring, hoặc truyền job ID/correlation ID qua payload và Reactor Context.

Việc Spring ghi `WARN` không có nghĩa log sẽ tự kèm business key đủ để xử lý sự cố. Chủ động gắn log context, tránh lộ token, PII hoặc payload nhạy cảm.

## 16. Timeout và subscription không bao giờ hoàn tất

Nếu nguồn không phát `onComplete` hoặc `onError`, Spring không thể kết thúc subscription đó một cách bình thường. Đặc biệt với `fixedDelay`, điều này có thể chặn tick kế vì Spring chờ terminal signal.

Ví dụ giới hạn thời gian:

~~~java
@Scheduled(fixedDelayString = "${jobs.poll.delay:PT10S}")
public Mono<Void> poll() {
    return Mono.defer(() -> remoteClient.pollOnce())
            .timeout(java.time.Duration.ofSeconds(5))
            .doOnError(error -> metrics.recordFailure(error))
            .then();
}
~~~

Đây là **Spring/Reactor fragment**, `remoteClient.pollOnce` và `metrics` là component ứng dụng. Khi upstream không phản hồi trong 5 giây, Reactor có thể phát `TimeoutException` dưới dạng `onError`. Spring sẽ thấy terminal error và có thể tiếp tục subscription tại tick tiếp theo sau fixed-delay.

Nhưng:

- `timeout` phát tín hiệu cancel upstream khi timeout; nó **không chứng minh** một RPC/DB operation phía ngoài đã dừng hoặc rollback side effect.
- Một client không hỗ trợ cancellation có thể vẫn xử lý request cũ.
- Nếu tick tiếp chạy lại cùng job, cần idempotency.
- Timeout tổng của một tick và timeout từng I/O trong tick có thể khác; nên cấu hình cả hai khi cần.

### Tránh retry vô hạn

~~~java
return remoteClient.pollOnce()
        .retryWhen(reactor.util.retry.Retry.indefinitely());
~~~

Đây là **mẫu không an toàn trong job có thể lỗi liên tục**, vì subscription có thể không kết thúc, nhất là nếu không có delay/backoff. Với `fixedDelay`, scheduler có thể bị giữ vô thời hạn; với các kiểu khác, nguồn lực có thể tăng nhanh. Dùng retry hữu hạn, backoff, deadline và xử lý permanent failure.

## 17. Cancellation và shutdown của Reactive @Scheduled

Theo tài liệu Spring, khi bean có scheduled reactive method bị destroy hoặc ApplicationContext đóng, Spring hủy:

- Lượt subscription **được lên lịch trong tương lai**.
- Các **subscription trước đó vẫn còn active** của publisher, ví dụ nguồn dài hoặc vô hạn.

Đây là cancellation ở mức reactive subscription và scheduled tasks, không phải bảo đảm mọi external side effect đã được rollback. Một thư viện I/O có thể không hủy request ngay lập tức sau cancellation.

Nếu ứng dụng dùng `Flux.interval`/`Flux.never` làm scheduled publisher, phải có shutdown behavior được kiểm thử. Nếu upstream có kết nối/socket, sử dụng `doFinally` hoặc lifecycle hook phù hợp để dọn resources. Ví dụ:

~~~java
return Mono.defer(() -> openReactiveResource())
        .doFinally(signal ->
                metrics.recordTermination(signal.name()));
~~~

Đây là fragment với `openReactiveResource()` trả Mono và `metrics` do ứng dụng định nghĩa. `doFinally` nhận `SignalType` (CANCEL, ON_COMPLETE, ON_ERROR...), nên hữu ích để đo termination thực tế của subscription, nhưng callback phải nhẹ và thread-safe.

### Đừng nhầm @Scheduled và @Async

`@Async` chủ yếu dispatch method sang executor; `@Scheduled` reactive đăng ký periodic **subscriptions**. Chỉ thêm `@Async` không giải quyết reliability của `onError` hoặc làm publisher thành deferred. Reactor `subscribeOn`/`publishOn` có mục đích riêng với reactive pipeline; việc phối hợp ba loại scheduler cần được thiết kế, không gắn annotation bừa để “chạy async hơn”.

## 18. Kiểm thử bằng StepVerifier: lỗi hai lần rồi thành công

Để test chính xác *publisher có thể được subscribe lặp*, không cần chờ lịch thật. Đưa logic làm việc vào một class dùng `Mono.defer` và test với `reactor-test`.

Tệp **RetryablePollOperation.java**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;

public final class RetryablePollOperation {
    private final AtomicInteger attempts = new AtomicInteger();

    public Mono<Void> publisher() {
        return Mono.defer(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt <= 2) {
                return Mono.<Void>error(
                        new IllegalStateException("temporary #" + attempt));
            }
            return Mono.<Void>empty();
        });
    }

    public int attempts() {
        return attempts.get();
    }
}
~~~

Đây là **Java class dùng Reactor**; có thể chạy trong dự án có dependency Reactor. Test JUnit 5:

~~~java
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryablePollOperationTest {

    @Test
    void laterSubscriptionsCanSucceedAfterErrors() {
        RetryablePollOperation operation = new RetryablePollOperation();
        Mono<Void> publisher = operation.publisher();

        StepVerifier.create(publisher)
                .expectError(IllegalStateException.class)
                .verify();

        StepVerifier.create(publisher)
                .expectError(IllegalStateException.class)
                .verify();

        StepVerifier.create(publisher)
                .verifyComplete();

        assertEquals(3, operation.attempts());
    }
}
~~~

Đây là **JUnit 5 test**, cần `reactor-core`, `reactor-test` và `junit-jupiter`. Test dùng **cùng một Mono object** ba lần, chứng minh `Mono.defer` có thể tạo những kết quả khác nhau theo state tại subscription time. Test không phụ thuộc scheduler và không cần `Thread.sleep`.

**Phạm vi được chứng minh:** logic publisher có thể subscribe lại sau `onError`. **Chưa chứng minh** Spring thực sự đăng ký tick, ghi WARN hoặc tiếp tục scheduling — những điều đó phải test bằng một Spring context thật.

## 19. Kiểm thử Spring end-to-end: context, tick, WARN và các lượt sau

Một integration test thực sự cần cấu hình `@EnableScheduling` và có listener/bean được Spring quản lý. Để xác minh lỗi đầu không chặn tick sau, dùng các công cụ:

1. **Bean Spring thật** có `@Scheduled(fixedRateString = "...")` trả `Mono<Void>`.
2. `AtomicInteger` đếm attempts bên trong `Mono.defer` để mỗi subscription tăng đúng một.
3. `CountDownLatch` ghi nhận ba lần terminal signal: hai lần lỗi, một lần thành công.
4. Assertion `await(timeout)` để xác minh các subscription có xảy ra sau onError.
5. Một log appender hoặc test logging capture được cấu hình rõ để kiểm tra WARN, tránh suy luận chỉ từ console.
6. Đóng ApplicationContext sau test, bảo đảm task không tiếp tục chạy và worker không bị treo.

### Component tích hợp minh họa

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ReactiveProbeJob {
    private final AtomicInteger attempt = new AtomicInteger();
    private final CountDownLatch terminated = new CountDownLatch(3);

    @Scheduled(fixedRate = 50)
    public Mono<Void> probe() {
        return Mono.defer(() -> {
            int current = attempt.incrementAndGet();
            if (current <= 2) {
                return Mono.<Void>error(
                        new IllegalStateException("probe #" + current));
            }
            return Mono.<Void>empty();
        }).doFinally(signal -> {
            if (attempt.get() <= 3) {
                terminated.countDown();
            }
        });
    }

    public CountDownLatch terminated() {
        return terminated;
    }

    public int attempts() {
        return attempt.get();
    }
}
~~~

Đây là **Spring/Reactor component fragment**, cần context có scheduling và bean được inject trong test. Nó minh họa cách đếm terminal signals nhưng **có race** nếu nhiều subscription chạy đồng thời: `attempt.get()` trong `doFinally` không phản ánh chắc chắn attempt hiện tại. Với test production-grade, nên capture `current` bên trong `Mono.defer` và gắn `doFinally` ở publisher của **từng attempt**, không dùng global count để xác định lượt terminal.

Phiên bản rõ ràng hơn cho test:

~~~java
@Scheduled(fixedRate = 50)
public Mono<Void> probe() {
    return Mono.defer(() -> {
        int current = attempt.incrementAndGet();

        Mono<Void> result = current <= 2
                ? Mono.<Void>error(
                        new IllegalStateException("probe #" + current))
                : Mono.<Void>empty();

        return result.doFinally(signal -> {
            if (current <= 3) {
                terminated.countDown();
            }
        });
    });
}
~~~

Cả hai đoạn là **mẫu Spring integration**, chỉ sử dụng phiên bản thứ hai nếu muốn test không race do counter toàn cục trong callback. Trong test, đợi `terminated.await(5, TimeUnit.SECONDS)`, assert `attempts() >= 3` và kiểm tra log WARN cho hai lỗi đầu. Vì tick tiếp vẫn có thể xảy ra sau khi latch về 0, không khẳng định attempts bằng **đúng** 3 trong bất kỳ thời điểm ngẫu nhiên nào.

### Ghi log WARN thế nào?

Dùng test appender tương thích với logging backend mà ứng dụng thực tế sử dụng, chẳng hạn Logback `ListAppender` gắn với logger của `org.springframework.scheduling.annotation.ScheduledAnnotationReactiveSupport`. Không dùng `System.out` để kết luận Spring đã WARN. Nếu thay phiên bản Spring, cần kiểm tra lại logger name và message; assertion nên tập trung vào **mức WARN và nguyên nhân lỗi**, không bám exact wording dễ thay đổi.

### Test cần hai tầng

- **Unit test:** `StepVerifier` xác nhận publisher lập lại subscription, operator error/fallback/retry.
- **Integration test:** Spring context + scheduler xác nhận lỗi subscription thứ nhất không chặn các lần sau và log WARN.

Không gộp hai tầng thành một kết luận “đã kiểm thử Spring” khi mới chỉ gọi `publisher.subscribe()` bằng tay.

## 20. Dùng VirtualTimeScheduler và tránh phụ thuộc thời gian thật

`StepVerifier.withVirtualTime(...)` và `VirtualTimeScheduler` của Reactor có ích khi bạn muốn kiểm tra **timeout**, **retry backoff**, `delayElements` hoặc các operator Reactor dựa trên thời gian. Tuy nhiên, virtual time của Reactor **không tự động điều khiển** `TaskScheduler`/`@Scheduled` của Spring, vốn có clock/registration riêng.

Ví dụ test logic retry (fragment):

~~~java
StepVerifier.withVirtualTime(() ->
        operation.publisher()
                .retryWhen(
                        Retry.backoff(2, Duration.ofSeconds(1))))
        .thenAwait(Duration.ofSeconds(3))
        .verifyComplete();
~~~

Đây là **fragment test** cần `operation` có state hỗ trợ retry (ví dụ hai lần lỗi rồi thành công), `StepVerifier`, `Retry` và `Duration`. Nó chỉ test **Reactor operator**. Nếu scheduler dùng fixed-rate ngoài Reactor, test integration vẫn cần Spring context/TaskScheduler hoặc một scheduler giả được inject có kiểm soát.

Không dùng virtual time để hứa rằng cron trigger Spring đã chạy một giờ thật trong thời gian ảo mà không thiết kế test clock/scheduler tương ứng.

## 21. Chẩn đoán khi lịch “không tiếp tục” như mong đợi

Khi có log `WARN` nhưng job không chạy thành công ở các tick sau, cần tìm nguyên nhân thật:

| Triệu chứng | Giả thuyết cần kiểm tra | Cách xác minh |
| --- | --- | --- |
| Chỉ thấy một lần tạo publisher | Đây là behavior chuẩn của Spring reactive `@Scheduled` | Kiểm tra subscription count, không đếm method invocation |
| Tất cả ticks báo cùng một exception | Mono lỗi cố định, không defer logic mới; hoặc remote service thực sự luôn hỏng | Dùng `Mono.defer`, log attempt/tick ID |
| Method trả `CompletableFuture` bị từ chối khi startup | Loại return không deferred theo adapter registry | Đổi sang Publisher deferred phù hợp |
| Có WARN nhưng không có gọi ErrorHandler | `onError` là reactive signal, không phải thrown Runnable exception | Dùng `doOnError`/metrics/log subscriber |
| fixed-delay đứng im sau tick đầu | Publisher không complete/error, I/O treo hoặc Flux vô hạn | Instrument terminal signals, thêm timeout |
| fixed-rate có quá nhiều in-flight requests | Subscription overlap, period ngắn hơn thời gian xử lý | Đo in-flight count, giới hạn concurrency |
| Sau lỗi không có business result ở tick kế | Lịch vẫn chạy nhưng state/queue bị mất hoặc lỗi còn tồn tại | Kiểm tra job store, retry policy và downstream |
| Context đóng rồi không có tick nữa | Spring hủy scheduled tasks và active subscriptions | Xem lifecycle/startup/shutdown logs |
| Job chạy nhiều lần ở nhiều node | Mỗi app context đăng ký lịch độc lập | Gắn instance ID, dùng coordination/idempotency |
| Có log WARN nhưng dashboard vẫn xanh | Chỉ đo scheduler alive, không đo job outcome | Metric per attempt/outcome |
| Tracing ID biến mất giữa operator | Reactor Context/thread propagation thiếu | Kiểm tra context-propagation config |
| Chỉ vài lần đầu chạy thành công rồi lặp lỗi | State/hot publisher/authorization/resource exhausted | Inspect subscription-level metrics và root cause |

### Ba kiểm tra nhanh nhất

1. **Spring version ít nhất 6.1?** Nếu cũ hơn, cơ chế reactive `@Scheduled` này không được hỗ trợ theo tài liệu.
2. **Publisher deferred/repeatable?** Nếu method trả một Mono đã hoàn tất lỗi cố định, mỗi subscription sẽ lặp chính lỗi đó.
3. **Có terminal signal không?** Nếu publisher treo, fixed-delay không có cơ sở để schedule tiếp, bất kể Spring có chính sách recover `onError`.

## 22. Observability: đo cả lịch lẫn kết quả

Gợi ý chỉ số:

| Metric/trường | Ý nghĩa |
| --- | --- |
| `scheduled_subscription_started_total` | Số lượt subscribe do lịch tạo |
| `scheduled_subscription_error_total` | Số lượt kết thúc bằng onError |
| `scheduled_subscription_complete_total` | Số lượt kết thúc bằng onComplete |
| `scheduled_subscription_cancel_total` | Số lượt bị cancel |
| `scheduled_subscription_duration` | Thời gian của mỗi subscription |
| `scheduled_subscription_in_flight` | Số subscription chưa terminal, rất quan trọng với fixed-rate/cron |
| `scheduled_trigger_fire_total` | Số lượt scheduler kích hoạt |
| `reactive_retry_attempt_total` | Số lần retry ở operator, khác số tick |
| `business_items_succeeded_total` | Số item hoàn thành nghiệp vụ thật |
| `business_items_failed_total` | Số item lỗi nghiệp vụ (kể cả khi đã fallback) |
| `last_successful_business_execution` | Thời điểm gần nhất có outcome thành công |
| `persistent_backlog_size` | Số item pending trong job store |
| `scheduler_rejection_total` | Số lần executor không nhận tác vụ |

Vì `onNext` của `@Scheduled` bị bỏ qua, **đừng** coi số giá trị `onNext` là số business result Spring đã “nhận”. Nếu một Flux trả 100 item nhưng caller không lưu/ghi nhận, Spring chỉ discard values; cần side effect hoặc pipeline kết quả rõ ràng trước terminal `onComplete`.

### Dùng doOnSubscribe / doFinally để đo

~~~java
@Scheduled(fixedRateString = "${jobs.reconcile.rate:PT10S}")
public Mono<Void> reconcile() {
    return Mono.defer(() -> reconcileService.reconcileOnce())
            .doOnSubscribe(subscription -> metrics.started())
            .doOnError(error -> metrics.failed(error))
            .doOnSuccess(ignored -> metrics.completed())
            .doFinally(signal -> metrics.terminated(signal.name()));
}
~~~

Đây là **Spring/Reactor fragment**, với `reconcileService`/`metrics` do ứng dụng cung cấp. Trong `Mono<Void>`, `doOnSuccess` có thể nhận `null` khi complete bình thường; không được coi null là thất bại. `doFinally` nhận cả `ON_COMPLETE`, `ON_ERROR` và `CANCEL`, nên dùng nó để giảm in-flight counter, trong khi `doOnError`/`doOnSuccess` tách business outcome. Các callback đo metric phải nhẹ, thread-safe và không được ném lỗi thứ cấp.

## 23. Anti-pattern và lỗi hay gặp

1. **Cho rằng onError làm dừng toàn bộ `@Scheduled` reactive.** Theo Spring 6.1+, lịch có thể tiếp tục subscribe ở tick sau.
2. **Cho rằng Spring tự retry request lỗi trong cùng tick.** Không có operator retry nào được thêm vào nghiệp vụ.
3. **Nhầm ErrorHandler của scheduler với subscriber nhận onError.** Đường lỗi reactive không đi qua Runnable ErrorHandler.
4. **Dùng `Mono.just(remoteCall())` khi remoteCall cần chạy ở mỗi tick.** Code có thể chạy lúc tạo publisher, không phải subscription time.
5. **Tăng counter ngoài `Mono.defer` rồi kỳ vọng mỗi tick tăng.** Method reactive `@Scheduled` thường được gọi một lần để lấy Publisher.
6. **Dùng publisher không thể subscribe lại.** Vi phạm hợp đồng cần repeated subscription.
7. **Nhầm hot publisher với cold deferred work.** Một hot source có thể không làm lại nhiệm vụ ở tick kế.
8. **Dùng `CompletableFuture` thay `Mono` ở return type reactive `@Scheduled`.** Adapter không deferred theo hợp đồng.
9. **Ghi lỗi bằng doOnError rồi nghĩ đã phục hồi.** Tín hiệu vẫn lan ra nếu không có operator recovery.
10. **Dùng onErrorResume trả Mono.empty cho mọi exception.** Che mất lỗi nghiệp vụ và log WARN của Spring.
11. **Dùng retryWhen vô hạn không delay.** Có thể khiến một subscription mắc trong vòng retry, đặc biệt fixed-delay.
12. **Nhầm một subscription mới với retry của subscription cũ.** Context và item được xử lý không tự được bảo toàn.
13. **Dùng fixedDelay với publisher không kết thúc.** Scheduler có thể chờ mãi.
14. **Dùng fixedRate với upstream async dài mà không giới hạn in-flight.** Có nguy cơ overlap và quá tải.
15. **Cho rằng cron/timezone giúp một job chạy đúng một lần toàn cụm.** Mỗi context có thể schedule độc lập.
16. **Tưởng @Async là bắt buộc để publisher chạy async.** Reactor có cơ chế subscription/scheduler riêng.
17. **Dùng Thread.sleep trong reactive pipeline/blocking I/O trên event loop.** Có thể làm nghẽn tài nguyên.
18. **Dùng `Mono.defer` mà vẫn ném exception đồng bộ trước return.** Lỗi trước khi tạo publisher không trở thành onError của subscription.
19. **Không kiểm thử đường publisher lỗi cố định.** Tick kế có thể lặp đúng lỗi cũ vô hạn.
20. **Chỉ kiểm tra Spring WARN nhưng không biết item nào chưa xử lý.** Cần metrics/job state.
21. **Cho rằng next tick luôn cứu được lần trước.** Nếu không có durable pending state, việc có thể bị mất.
22. **Retry tác vụ thanh toán không idempotent.** Có nguy cơ nhân đôi side effect.
23. **Đo onNext nhưng bỏ qua onComplete/onError.** Lifecycle được quyết định bởi terminal signal.
24. **Không đặt timeout khi upstream có thể không phát terminal.** fixedDelay không thể tiến.
25. **Cho rằng cancel subscriber chắc chắn hủy external transaction/HTTP.** Cancellation cần được upstream hỗ trợ.
26. **Đếm cùng lỗi tại doOnError và Spring WARN rồi cho hai lần failed jobs.** Phải phân biệt log event và business attempt.
27. **Dùng một AtomicInteger toàn cục trong integration test rồi assert chính xác attempts=3 khi scheduler vẫn đang chạy.** Tick mới có thể đến trước assertion.
28. **Không dùng latch/StepVerifier để kiểm tra hành vi.** Timing ngẫu nhiên làm CI flaky.
29. **Không kiểm thử đóng context với subscription dài.** Có thể rò tài nguyên nếu upstream không tuân thủ cancel.
30. **Giả định Reactor virtual time điều khiển @Scheduled Spring.** Hai cơ chế clock/scheduler khác nhau.
31. **Cho rằng onError cuối cùng chứng minh lỗi đã được lưu vào nơi bền vững.** WARN log không phải job store.
32. **Không có giới hạn concurrency/bulkhead/circuit breaker ở downstream.** Scheduler tiếp tục có thể làm sự cố nặng thêm.

## 24. Quy trình thiết kế reactive scheduled job an toàn

Một checklist theo thứ tự:

1. Xác nhận **Spring Framework 6.1+** và dependency Reactor/Reactive Streams phù hợp.
2. Method `@Scheduled` không nhận tham số, trả Publisher **repeatable/deferred**.
3. Đặt I/O, state update và phần logic thay đổi theo tick **trong `Mono.defer`/operator lazy**.
4. Chọn fixed-delay nếu muốn chờ subscription trước kết thúc; fixed-rate/cron nếu chấp nhận/kiểm soát overlap.
5. Đặt timeout cho call và toàn pipeline nếu có nguy cơ treo.
6. Quyết định lỗi nào retry được, số retry, backoff, deadline và idempotency.
7. Đặt `doOnError`/metrics để phân biệt failure của mỗi subscription, có thể để `onError` tiếp tục đến Spring để được WARN.
8. Nếu dùng `onErrorResume`, phải có lý do nghiệp vụ và metric riêng cho fallback.
9. Chọn cơ chế lưu việc bền vững nếu bất kỳ item nào không được phép mất.
10. Có backpressure/concurrency limit khi subscriber lặp nhanh hơn tốc độ xử lý.
11. Test bằng `StepVerifier`: publisher được subscribe lại sau lỗi và có kết quả đúng.
12. Test bằng Spring context: lỗi hai tick đầu và tick sau vẫn chạy; xác minh WARN.
13. Test shutdown/cancellation và release resources trong upstream.
14. Đo số in-flight subscriptions, lỗi, thành công, retry, backlog và thời gian thành công cuối.
15. Nếu deployment nhiều replica, dùng coordination/lease/idempotency theo contract nghiệp vụ.
16. Nếu vận hành đổi cron/disable job, hiểu rằng cập nhật config không mặc nhiên đổi registration đang chạy.

### Bảng chọn error policy

| Mục tiêu | Công cụ chính |
| --- | --- |
| Chỉ ghi nhận lỗi và để Spring WARN | `doOnError` + terminal `onError` |
| Fallback hợp lệ trong cùng tick | `onErrorResume` có phân loại |
| Retry tạm thời trong cùng tick | `retryWhen` hữu hạn, backoff, filter |
| Không mất job qua JVM crash | Durable queue / transactional outbox |
| Tránh overlap nhiều subscription | fixed-delay hữu hạn, semaphore, concurrency limit |
| Tạm dừng dispatch khi dependency chết | Feature flag, circuit breaker, rescheduling/stop policy |
| Chẩn đoán lỗi từ mỗi tick | Subscription-level metrics, job ID, trace |
| Kiểm tra lịch vẫn chạy sau lỗi | Spring integration test có latch + WARN capture |
| Phân biệt cùng publisher và nhiều lần subscribe | `Mono.defer` và `StepVerifier` trên cùng Mono |

## 25. Câu hỏi tự kiểm tra

1. Trong Spring 6.1+, `@Scheduled` reactive có gọi method để tạo một Publisher mới ở mỗi tick không?
2. Vì sao `Mono.defer` hữu ích khi có state/I/O cần làm lại mỗi lần subscribe?
3. `onError` có phải exception luôn được throw trực tiếp từ scheduled `Runnable` không?
4. Spring ghi `onError` ở mức log nào? `ErrorHandler` của scheduler có nhận tín hiệu này không?
5. Vì sao subscription tiếp theo vẫn có thể xảy ra sau khi subscription trước phát `onError`?
6. Spring có tự thêm `retryWhen` cho công việc lỗi không? Vì sao?
7. `doOnError` và `onErrorResume` khác nhau ra sao về việc lỗi cuối có tới Spring không?
8. Nếu `onErrorResume(error -> Mono.empty())` được áp cho mọi lỗi, dashboard có thể hiểu sai điều gì?
9. `retryWhen(Retry.backoff(2,...))` có thể dẫn tới bao nhiêu lần gọi upstream trong **một tick**?
10. `fixedDelay` với reactive publisher chờ điều kiện gì để tính lần tiếp theo?
11. Nếu một `Flux` vô hạn không phát terminal signal, điều gì có thể xảy ra với fixed-delay?
12. Với `fixedRate` và nguồn async lâu hơn chu kỳ, có thể có overlap không?
13. Một `Mono.error(...)` được tạo một lần có thể phát cùng lỗi ở mọi lần subscribe không?
14. `CompletableFuture` trả từ `@Scheduled` reactive có đáp ứng deferred adapter requirement không?
15. Lỗi thrown ngay khi method tạo publisher khác `onError` khi subscription thế nào?
16. Làm sao test publisher được subscribe ba lần mà không chờ lịch thật?
17. Vì sao `StepVerifier` đơn lẻ chưa chứng minh Spring vẫn schedule tick sau WARN?
18. Cần những metric nào để phân biệt scheduler vẫn alive nhưng job business luôn failed?
19. Nếu một job thanh toán lỗi và scheduler chạy tiếp, điều gì cần thiết để không mất/không gửi trùng?
20. Vì sao `VirtualTimeScheduler` của Reactor không tự điều khiển `@Scheduled` của Spring?
21. Khi đóng ApplicationContext, Spring xử lý những subscription còn active như thế nào?
22. Vì sao log WARN không phải bằng chứng dữ liệu đã được lưu vào job store?

## 26. Tài liệu chính thức

- [Spring Framework Reference — @Scheduled on Reactive methods and Kotlin suspending functions](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-reactive)
- [Spring Framework 6.2 — @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework 6.2 — ScheduledAnnotationBeanPostProcessor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/ScheduledAnnotationBeanPostProcessor.html)
- [Spring Framework — Task Execution and Scheduling reference](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Project Reactor — Error Handling](https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html)
- [Project Reactor — Mono Javadoc](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html)
- [Project Reactor — Retry Javadoc](https://projectreactor.io/docs/core/release/api/reactor/util/retry/Retry.html)
- [Project Reactor — StepVerifier reference](https://projectreactor.io/docs/test/release/api/reactor/test/StepVerifier.html)
- [Reactive Streams Specification](https://www.reactive-streams.org/)
- [Java SE 17 — Flow](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Flow.html)

**Ghi nhớ:** trong Spring Framework **6.1+**, một reactive `@Scheduled` method cung cấp Publisher **một lần**, còn scheduler **subscribe lại theo từng tick**. `onError` kết thúc **subscription hiện tại**, Spring **log WARN** và không đưa tín hiệu đó qua synchronous `ErrorHandler`, nhờ vậy **những lần subscribe sau vẫn có thể diễn ra**. `Mono.defer` là cách thiết yếu để công việc được thực hiện tại **subscription time**. Muốn retry thật phải dùng operator/policy riêng; muốn không mất việc phải dùng state bền vững và idempotency; muốn bảo vệ scheduler cần timeout, hạn chế overlap và metrics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-scheduling-006">ErrorHandler cho Runnable không xử lý tín hiệu lỗi reactive theo cùng cách</a>

<details>
<summary>Xem chi tiết</summary>

# ErrorHandler cho Runnable không xử lý tín hiệu lỗi reactive theo cùng cách

## 1. Vấn đề: cùng một @Scheduled nhưng lỗi đi qua hai con đường khác nhau

Trong Spring Framework, bạn có thể chạy tác vụ định kỳ kiểu imperative bằng một method `void` hoặc chạy tác vụ reactive bằng một method trả `Publisher`, ví dụ Reactor `Mono<Void>`. Cả hai đều được gắn `@Scheduled`, nhưng **cách lỗi được phát ra và quan sát không giống nhau**.

Xét hai ý tưởng:

```java
@Scheduled(fixedRate = 1000)
public void synchronousJob() {
    throw new IllegalStateException("direct failure");
}

@Scheduled(fixedRate = 1000)
public Mono<Void> reactiveJob() {
    return Mono.error(new IllegalStateException("reactive failure"));
}
```

Đây là **các fragment** trên Spring-managed bean; cần cấu hình `@EnableScheduling` và Reactor trên classpath. Trực giác thường gặp là: “Cấu hình `ThreadPoolTaskScheduler.setErrorHandler(...)` thì mọi lỗi từ cả hai method đều đi vào cùng handler.” Với Spring Framework 6.1+ và reactive scheduling, điều này **không đúng**.

- **Method imperative**: ngoại lệ có thể thoát ra khỏi lời gọi `Runnable.run()` do scheduler điều phối; tầng xử lý lỗi cho Runnable có thể bắt và chuyển tới `org.springframework.util.ErrorHandler`.
- **Method reactive**: scheduler chạy một `Runnable` có nhiệm vụ **subscribe** vào Publisher. Lỗi nghiệp vụ thường là tín hiệu Reactive Streams `onError(Throwable)` gửi cho subscriber, **không phải exception ném thoát khỏi Runnable.run()**. Spring xử lý tín hiệu lỗi reactive bằng subscriber nội bộ, ghi log mức `WARN` và phục hồi để các lượt đăng ký subscription kế tiếp vẫn xảy ra. Theo tài liệu chính thức, hợp đồng `ErrorHandler` **không được sử dụng cho onError của reactive scheduled method**.

**Ghi nhớ:** `Throwable` ở hai nhánh có thể giống class Java, nhưng **đường truyền lỗi** khác nhau: exception ném trực tiếp ở boundary Runnable đối lập với **onError signal** tại boundary Publisher/subscriber.

Bài này tập trung vào Spring Framework **6.2.1**, `@Scheduled`, Reactor và `ThreadPoolTaskScheduler`; không trộn lẫn với cơ chế exception của `@Async` hoặc HTTP/WebFlux request handling.

## 2. Hai hợp đồng xử lý lỗi: Runnable và Reactive Streams

### 2.1. Runnable: lỗi có thể thoát khỏi run()

`Runnable` có chữ ký:

```java
void run();
```

Một `RuntimeException` có thể được ném trực tiếp khi scheduler gọi `run()`. Spring có wrapper xử lý lỗi dành cho Runnable định kỳ; khi cấu hình `ThreadPoolTaskScheduler.setErrorHandler(handler)`, wrapper có thể chuyển exception được ném ra cho handler.

Ví dụ:

```java
ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
scheduler.setPoolSize(2);
scheduler.setErrorHandler(error ->
        System.err.println("Scheduler caught: " + error.getMessage()));
```

Đây là **fragment cấu hình**, phải đặt trong bean factory/lifecycle phù hợp để Spring thực sự sử dụng scheduler ấy. Không nên dùng `System.err` làm hệ thống giám sát production; dùng logger/metrics/alerting. Quan trọng: một custom handler có thể **suppress** hoặc **ném lại** lỗi; việc ném lại trong scheduler định kỳ có thể làm registration bị hủy theo hành vi executor bên dưới. Ví dụ cuối bài dùng một handler không ném lỗi, chủ đích để chứng minh những lần chạy tiếp theo vẫn có thể diễn ra.

### 2.2. Publisher: lỗi được gửi bằng onError(Throwable)

Reactive Streams có các tín hiệu cơ bản:

- `onSubscribe(Subscription)`: subscriber nhận subscription.
- `onNext(T)`: phát một phần tử (nếu Publisher có phần tử).
- `onComplete()`: hoàn thành bình thường.
- `onError(Throwable)`: kết thúc subscription bằng lỗi.

Các tín hiệu này thuộc giao thức **Publisher ↔ Subscriber**, không đồng nhất với việc method Java `run()` ném exception.

Ví dụ:

```java
Mono<Void> publisher = Mono.error(
        new IllegalStateException("external-service-failed"));
```

Việc tạo `Mono.error(...)` tạo **Publisher mô tả một kết quả lỗi**; bản thân dòng Java không nhất thiết ném `IllegalStateException` ra caller. Lỗi được phát **khi có subscription** và được đưa qua `onError` tới subscriber.

Tương tự, một `Mono.defer(...)` có thể tạo công việc riêng **cho từng subscription**, và lỗi bên trong supplier được Reactor biến thành tín hiệu lỗi theo hợp đồng operator; đó không có nghĩa exception tự thoát ra scheduler `Runnable.run()`.

### 2.3. Bảng so sánh ngắn

| Câu hỏi | @Scheduled void/Runnable | @Scheduled Mono/Publisher |
| --- | --- | --- |
| Spring lập lịch gì? | Runnable gọi method đồng bộ | Runnable định kỳ **subscribe** Publisher |
| Công việc diễn ra ở đâu? | Trong lời gọi method của scheduled task | Trong luồng tín hiệu được Publisher/subscriber điều phối; có thể dùng thêm scheduler Reactor |
| Kênh lỗi thông thường | Exception ném ra khỏi run() | `onError(Throwable)` |
| `ThreadPoolTaskScheduler.setErrorHandler` nhận lỗi này? | Có thể nhận exception từ Runnable | **Không nhận onError** của reactive method qua cùng cơ chế |
| Khi publisher onError | Không áp dụng | Spring log WARN, phục hồi ở subscriber bên ngoài; lịch subscription kế tiếp vẫn có thể chạy |
| Một lần thất bại có tự dừng lịch? | Phụ thuộc handler / executor / kind of task | Không dừng các lần scheduled subscription kế tiếp chỉ vì onError thông thường |
| Cần metrics lỗi ở đâu? | ErrorHandler, wrapper, job boundary | Reactor pipeline `doOnError` / `onErrorResume` / subscriber-level instrumentation |

## 3. Spring 6.1+ đăng ký reactive @Scheduled ra sao?

Một method reactive `@Scheduled` không có đối số được Spring gọi **một lần để lấy Publisher**; Runnable theo lịch subscribe lặp vào nguồn đó. Return type có thể là Publisher hỗ trợ nhiều subscriptions hoặc kiểu được `ReactiveAdapterRegistry` chuyển thành Publisher với descriptor deferred. Đặt I/O lặp vào nguồn cold/`defer`, không gọi ngay tại assembly.

~~~text
registration: method -> Publisher P
tick #1: Runnable -> subscribe(P) -> onError
tick #2: Runnable -> subscribe(P) -> onComplete
~~~

Bài này tập trung vào ranh giới lỗi: `onError` được subscriber của Spring nhận **không giống** một `RuntimeException` ném thoát khỏi `Runnable.run()`. Phần thực nghiệm sau so sánh trực tiếp custom `ErrorHandler` của scheduler với tín hiệu reactive.

## 4. Vì sao ErrorHandler không thấy onError?

Để hiểu chính xác, quan sát hai đường:

```text
A. Scheduled synchronous method

Scheduled executor
  -> DelegatingErrorHandlingRunnable.run()
     -> invoke void method
        -> throw IllegalStateException
     -> ErrorHandler.handleError(Throwable)
        -> metric / log / suppress or rethrow

B. Scheduled reactive method

Scheduled executor
  -> scheduled Runnable.run()
     -> Publisher.subscribe(TrackingSubscriber)
        -> onError(IllegalStateException)
           -> Spring subscriber logs WARN and recovers
        -> Runnable itself does not throw business error
     -> Runnable returns normally
  -> ErrorHandler has no escaped exception to handle
```

Như vậy, có một **Runnable thật** ở lớp scheduler reactive, nhưng đó là runnable để **đăng ký subscription**, không phải một wrapper trực tiếp ném lại mọi lỗi của pipeline ra ngoài `run()`.

Ngay cả khi `Mono.error(...)` phát `onError` **ngay trong lúc subscribe**, Spring subscriber vẫn là nơi xử lý error signal theo hợp đồng. Việc phát nhanh hay chậm không tự thay đổi `onError` thành một exception ném ra ErrorHandler.

**Không tuyệt đối hóa:** ErrorHandler của scheduler vẫn có thể thấy một **exception thực sự thoát ra từ infrastructure Runnable** trong trường hợp khác. Điều bị loại khỏi hợp đồng ở đây là **onError của reactive Publisher đã được subscriber nhận**, không phải mọi thứ có chữ “reactive” sẽ không bao giờ gây exception ở bất kỳ tầng nào.

### 4.1. Hậu quả với giám sát

Nếu hệ thống đã có:

```java
scheduler.setErrorHandler(ex ->
        metrics.increment("scheduled.runnable.error"));
```

chỉ số này có thể phản ánh lỗi của scheduled **imperative Runnable** nhưng **bỏ sót lỗi nghiệp vụ của reactive scheduled Publisher**. Nhìn dashboard “không có exception ở ErrorHandler” rồi kết luận reactive job luôn thành công là sai.

Cần instrument **tại Reactor error channel** hoặc nơi downstream completion được xác nhận.

## 5. Lỗi reactive được Spring xử lý thế nào?

Trong Spring Framework 6.2, khi Publisher của reactive scheduled method phát `onError`:

1. Subscriber mà Spring dùng cho subscription nhận error.
2. Spring ghi lại lỗi ở mức log `WARN`.
3. Error signal kết thúc subscription hiện tại.
4. Việc ném lỗi không thoát ra scheduler Runnable theo cách của imperative method.
5. Những **lượt subscription đã được schedule kế tiếp** không bị hủy chỉ vì lỗi này.

Điều (5) không có nghĩa lỗi được “retry ngay” theo một chính sách tự động. Một subscription thất bại là **một lượt**. Lượt sau xảy ra theo cron/fixedRate/fixedDelay và availability của scheduler.

Nếu cần retry ngay bên trong **cùng lượt subscription**, đó là việc của Reactor operator như `retryWhen(...)`; nó không phải trách nhiệm của scheduler ErrorHandler.

### 5.1. WARN log không đồng nghĩa health check hoặc alert

Spring log WARN là một tín hiệu hữu ích để tra cứu, nhưng không thay cho:

- Counter lỗi theo job name/exception type.
- Success/failure outcome cho mỗi subscription.
- SLO, alert và retry budget.
- Correlation ID và structured logging.
- Dead-letter hoặc job status store khi công việc không được phép mất.

Nếu log được lấy mẫu hoặc bị đổi appender/level, lỗi có thể không xuất hiện trên dashboard theo cách mong đợi. Xây dựng metric của reactive pipeline thay vì phụ thuộc vào một loại log.

## 6. Ví dụ thực nghiệm: ErrorHandler bắt lỗi void, không bắt onError của Mono

Dự án này có thể chạy bằng **Java 21, Spring Framework 6.2.1, Reactor Core 3.7.1** và Maven, không cần Spring Boot.

Nó cấu hình một `ThreadPoolTaskScheduler` có custom `ErrorHandler` và hai scheduled methods:

- `synchronousFailure()`: là method `void`, mỗi lần chạy ném `IllegalStateException("sync-failure")`.
- `reactiveFailureThenSuccess()`: trả `Mono<Void>` **chỉ một lần lúc đăng ký**, nhưng bên trong `Mono.defer(...)` đếm từng subscription; hai subscription đầu phát `onError`, từ subscription thứ ba trở đi hoàn thành thành công.

Các probe thread-safe và `CountDownLatch` cho phép test **đợi sự kiện** rồi kiểm tra các bất biến thay vì dùng `Thread.sleep(...)` để dự đoán.

### 6.1. Cấu trúc dự án

```text
scheduled-reactive-errors-demo/
├── pom.xml
└── src/main/java/demo/ScheduledReactiveErrorsDemo.java
```

### 6.2. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>scheduled-reactive-errors-demo</artifactId>
  <version>1.0.0</version>
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
    <dependency>
      <groupId>io.projectreactor</groupId>
      <artifactId>reactor-core</artifactId>
      <version>3.7.1</version>
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
          <mainClass>demo.ScheduledReactiveErrorsDemo</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

### 6.3. Tệp src/main/java/demo/ScheduledReactiveErrorsDemo.java

```java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import reactor.core.publisher.Mono;

public class ScheduledReactiveErrorsDemo {

    public static final class Probe {
        final AtomicInteger syncHandled = new AtomicInteger();
        final AtomicInteger otherHandled = new AtomicInteger();
        final AtomicInteger reactiveCreated = new AtomicInteger();
        final AtomicInteger reactiveSubscriptions = new AtomicInteger();
        final AtomicInteger reactiveSignals = new AtomicInteger();
        final AtomicInteger reactiveSuccess = new AtomicInteger();
        final CountDownLatch handledTwoSync = new CountDownLatch(2);
        final CountDownLatch observedThreeSubscriptions = new CountDownLatch(3);
    }

    public static final class Jobs {
        private final Probe probe;
        public Jobs(Probe probe) {
            this.probe = probe;
        }

        @Scheduled(initialDelay = 0, fixedRate = 100,
                   timeUnit = TimeUnit.MILLISECONDS)
        public void synchronousFailure() {
            throw new IllegalStateException("sync-failure");
        }

        @Scheduled(initialDelay = 0, fixedRate = 100,
                   timeUnit = TimeUnit.MILLISECONDS)
        public Mono<Void> reactiveFailureThenSuccess() {
            probe.reactiveCreated.incrementAndGet();
            return Mono.defer(() -> {
                int attempt = probe.reactiveSubscriptions.incrementAndGet();
                if (attempt <= 2) {
                    return Mono.<Void>error(
                            new IllegalStateException("reactive-failure-" + attempt));
                }
                return Mono.<Void>fromRunnable(
                        () -> probe.reactiveSuccess.incrementAndGet());
            }).doOnError(error -> probe.reactiveSignals.incrementAndGet())
              .doFinally(signal -> probe.observedThreeSubscriptions.countDown());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    public static class Config {
        @Bean
        public Probe probe() {
            return new Probe();
        }

        @Bean
        public Jobs jobs(Probe probe) {
            return new Jobs(probe);
        }

        @Bean("taskScheduler")
        public ThreadPoolTaskScheduler taskScheduler(Probe probe) {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2);
            scheduler.setThreadNamePrefix("schedule-");
            scheduler.setErrorHandler(error -> {
                if ("sync-failure".equals(error.getMessage())) {
                    probe.syncHandled.incrementAndGet();
                    probe.handledTwoSync.countDown();
                } else {
                    probe.otherHandled.incrementAndGet();
                }
            });
            return scheduler;
        }
    }

    public static void main(String[] args) throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            Probe probe = context.getBean(Probe.class);
            if (!probe.handledTwoSync.await(5, TimeUnit.SECONDS) ||
                !probe.observedThreeSubscriptions.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Expected scheduled callbacks never occurred");
            }

            boolean synchronousHandlerCalled = probe.syncHandled.get() >= 2;
            boolean reactiveOnErrorObserved = probe.reactiveSignals.get() == 2;
            boolean reactiveContinued = probe.reactiveSubscriptions.get() >= 3
                    && probe.reactiveSuccess.get() >= 1;
            boolean handlerNotInvokedByReactive = probe.otherHandled.get() == 0;
            boolean reactiveMethodOnce = probe.reactiveCreated.get() == 1;

            if (!synchronousHandlerCalled || !reactiveOnErrorObserved ||
                !reactiveContinued || !handlerNotInvokedByReactive ||
                !reactiveMethodOnce) {
                throw new AssertionError("Unexpected error-handling semantics");
            }

            System.out.println("syncErrorHandlerInvoked=" +
                    synchronousHandlerCalled);
            System.out.println("reactiveOnErrorObserved=" +
                    reactiveOnErrorObserved);
            System.out.println("reactiveSubscriptionsContinue=" +
                    reactiveContinued);
            System.out.println("reactiveErrorBypassedErrorHandler=" +
                    handlerNotInvokedByReactive);
            System.out.println("reactivePublisherCreatedOnce=" +
                    reactiveMethodOnce);
        }
    }
}
```

### 6.4. Chạy chương trình

Yêu cầu JDK 21 và Maven. Tại thư mục có `pom.xml`:

```bash
mvn -q -DskipTests compile exec:java
```

**Các dòng stdout do chương trình in, đã kiểm chứng khi chạy thực tế:**

```text
syncErrorHandlerInvoked=true
reactiveOnErrorObserved=true
reactiveSubscriptionsContinue=true
reactiveErrorBypassedErrorHandler=true
reactivePublisherCreatedOnce=true
```

Ngoài năm dòng trên, Spring có thể phát **hai WARN cùng stack trace** về hai lỗi `reactive-failure-1` và `reactive-failure-2` ra log console, tùy cấu hình logging. Đó là hoạt động được mong đợi của subscriber nội bộ, **không phải ErrorHandler mà chương trình cài trên scheduler**.

Trong test này, custom ErrorHandler đếm exception có message `sync-failure`. Bộ đếm `otherHandled` giữ vai trò phát hiện exception khác **ném ra khỏi Runnable**, nhưng không tăng bởi `onError` của Mono. Reactive pipeline tự tăng `reactiveSignals` bằng `doOnError`, rồi Spring vẫn subscribe những lần kế tiếp.

### 6.5. Những điều đã được chứng minh

- `syncErrorHandlerInvoked=true`: ErrorHandler được cấu hình trên scheduler thực sự nhận lỗi từ method imperative.
- `reactiveOnErrorObserved=true`: Reactor pipeline phát và xử lý tín hiệu `onError` đúng như lập trình.
- `reactiveSubscriptionsContinue=true`: sau hai subscription lỗi, những lượt subscription sau vẫn diễn ra, có ít nhất một lượt thành công.
- `reactiveErrorBypassedErrorHandler=true`: trong bài thử này không có exception reactive được đưa vào nhánh custom ErrorHandler.
- `reactivePublisherCreatedOnce=true`: method trả `Mono` được gọi một lần lúc registration, còn nội dung `Mono.defer` được chạy trên từng subscription.

Các counters có thể tăng thêm vì scheduling vẫn hoạt động trong lúc main đang đọc. Assertions chỉ kiểm tra **bất biến**, không phụ thuộc số lượng callback chính xác trên máy nhiều/ít tải.

**Giới hạn test:** các interval 100ms giúp demo chạy nhanh. Đây không phải giá trị production mặc định hoặc một khuyến nghị lịch thực tế. Trong CI cực kỳ quá tải, nên dùng scheduler/clock test điều khiển được hoặc ngưỡng timeout phù hợp, tránh coi sự trễ CPU là lỗi ngữ nghĩa.

## 7. Đặt quan sát lỗi ở đâu trong reactive pipeline?

Vì `ErrorHandler` dành cho Runnable không nhận onError, lựa chọn đúng là **xử lý ở kênh reactive** và/hoặc lớp job status.

### 7.1. doOnError: quan sát, không khôi phục

```java
@Scheduled(fixedDelay = 10_000)
public Mono<Void> exportSnapshot() {
    return Mono.defer(() -> exportService.export())
            .doOnError(error -> {
                // Ghi metric và log có job ID/correlation ID.
                errorMetrics.record("exportSnapshot", error);
            });
}
```

Đây là fragment cần các bean `exportService` và `errorMetrics`. `doOnError` chỉ là side effect để **quan sát** lỗi, không nuốt lỗi. Nếu Publisher vẫn phát `onError` sau operator này, Spring subscriber ngoài cùng sẽ ghi `WARN` theo cơ chế reactive scheduled.

Đừng để observer ném exception hoặc block lâu, vì điều đó có thể làm thay đổi lỗi ban đầu hoặc làm chậm pipeline. Trong production, logging và metrics cần được thiết kế thread-safe, có giới hạn nhãn để tránh cardinality quá cao.

### 7.2. onErrorResume: phục hồi có chủ đích

Nếu lỗi là trường hợp cho phép bỏ qua lượt hiện tại:

```java
@Scheduled(fixedRate = 30_000)
public Mono<Void> refreshCache() {
    return Mono.defer(() -> refreshService.reload())
            .doOnError(ex -> metrics.incrementRefreshFailure())
            .onErrorResume(ex -> Mono.empty());
}
```

Fragment trên cần các bean nghiệp vụ tương ứng. Sau `onErrorResume`, error upstream được đổi thành một Publisher fallback hoàn tất bình thường. Vì vậy **Spring outer subscriber không còn thấy onError của lỗi ấy** và sẽ không log WARN cho nó theo đường mặc định. Metrics trong `doOnError` vẫn ghi nhận failure đã xảy ra.

**Quan trọng:** đó là một chính sách chủ động **chấp nhận hoàn tất lượt không thành công về mặt nghiệp vụ**. Không dùng `onErrorResume(ex -> Mono.empty())` một cách tràn lan cho thanh toán, đối soát hoặc các công việc cần xử lý chắc chắn, vì có thể che mất lỗi và khiến dashboard hiểu lầm là thành công.

### 7.3. onErrorMap: chuyển loại lỗi nhưng không nuốt

```java
return remoteCall()
        .onErrorMap(TimeoutException.class,
                ex -> new JobRetryableException("remote timed out", ex));
```

Đây là fragment: cần khai báo `remoteCall()`, `JobRetryableException`. `onErrorMap` thay đổi exception được gửi tiếp xuống `onError`; nó **không** chuyển tín hiệu thành exception thoát khỏi `Runnable.run()`. ErrorHandler vẫn không tự nhận lỗi này theo cách nhánh imperative.

### 7.4. doFinally: quan sát trạng thái kết thúc

```java
return Mono.defer(() -> doBusinessWork())
        .doOnError(error -> recordFailure(error))
        .doOnSuccess(value -> recordSuccess())
        .doFinally(signalType -> recordTerminalSignal(signalType));
```

Đây là fragment cần code nghiệp vụ. Với `Mono<Void>`, `doOnSuccess` có thể nhận `null` khi Mono hoàn tất rỗng; đoạn trên không dùng giá trị đó. `doFinally` chạy cho completion/error/cancel của **subscription này**, hữu ích để ghi thời lượng và trạng thái. Cần xác định rõ metrics nào là “attempt”, “success”, “failure” và “cancelled”, tránh double counting khi có `retryWhen`.

## 8. Retry trong một subscription khác các lần schedule kế tiếp

Spring tiếp tục schedule sau một `onError`, nhưng điều đó **không đồng nghĩa tự retry ngay**. Phân biệt:

- **Lần schedule kế tiếp**: scheduler đến một mốc `fixedRate`, `fixedDelay` hoặc `cron` khác và subscribe lại vào Publisher.
- **Retry bên trong cùng subscription**: Reactor `retryWhen(...)` resubscribe upstream ngay/hoặc sau một backoff theo chính sách của pipeline.
- **Retry nghiệp vụ bền vững**: workflow/job store/outbox có attempt number, trạng thái và khả năng sống qua restart.

Ví dụ có giới hạn retry:

```java
@Scheduled(fixedDelay = 60_000)
public Mono<Void> syncCatalog() {
    return Mono.defer(() -> catalogClient.sync())
            .retryWhen(
                    Retry.backoff(2, Duration.ofSeconds(1))
                         .maxBackoff(Duration.ofSeconds(5))
                         .filter(this::isTransientError)
            )
            .doOnError(ex -> metrics.recordFinalFailure(ex));
}
```

Đây là fragment với `reactor.util.retry.Retry`, `java.time.Duration` và các thành phần nghiệp vụ `catalogClient`, `metrics`, `isTransientError`. `Retry.backoff(2, ...)` ở đây cho phép **tối đa hai retry** sau lần attempt đầu; sau khi hết retry, pipeline có thể phát `onError` cuối cùng ra Spring subscriber. Đừng dùng retry vô hạn khi công việc có side effect; cần idempotency và retry budget.

### 8.1. Vị trí của doOnError so với retryWhen

```java
return remote()
        .doOnError(ex -> metrics.recordAttemptFailure(ex))
        .retryWhen(Retry.max(2))
        .doOnError(ex -> metrics.recordTerminalFailure(ex));
```

Đây là fragment. `doOnError` **trước** `retryWhen` có thể được gọi mỗi attempt thất bại; `doOnError` **sau** `retryWhen` nhận lỗi cuối cùng nếu tất cả retries hết. Nếu không đặt tên metrics rõ, người vận hành dễ đếm một job fail ba lần dù chỉ có một scheduled subscription với ba attempts.

### 8.2. Retry khác skip

Nếu lỗi hạ nguồn là dữ liệu không hợp lệ và sẽ không tự khỏi, retry chỉ gây tốn tài nguyên. Xem xét phân loại transient/permanent errors; áp dụng `onErrorResume` theo trường hợp có thể bỏ qua và ghi status thất bại cho phần không thể bỏ qua.

## 9. fixedRate, fixedDelay và cron trong reactive scheduling

Cùng một Publisher nhưng thời điểm subscribe tiếp theo phụ thuộc lịch được khai báo.

### 9.1. fixedDelay: chờ subscription để giữ đúng semantics

Tài liệu Spring nêu rõ: nếu cấu hình `fixedDelay` trên reactive `@Scheduled`, Spring **block subscription trong Runnable** để tôn trọng ngữ nghĩa “đợi sau khi lượt trước kết thúc”.

```java
@Scheduled(fixedDelay = 10_000)
public Mono<Void> flush() {
    return Mono.defer(() -> flushService.flush());
}
```

Fragment trên giả sử `flushService.flush()` trả `Mono<Void>`. Một lượt kéo dài do I/O hoặc retry sẽ kéo theo thời điểm fixed-delay kế tiếp. **Không hiểu sai rằng block chỉ dành cho imperative method**; riêng fixed-delay reactive phải có cách biết subscription hoàn tất trước khi tính khoảng delay tiếp theo.

Vì vậy, một Publisher **không bao giờ hoàn tất** như `Flux.never()` có thể làm lịch fixed-delay bị kẹt; cần timeout, cancellation hoặc thiết kế publisher hoàn tất phù hợp.

### 9.2. fixedRate: chú ý chồng lấn subscriptions

Với `fixedRate`:

```java
@Scheduled(fixedRate = 5_000)
public Mono<Void> poll() {
    return Mono.defer(() -> remoteService.poll());
}
```

Scheduler kích hoạt các **lượt subscribe** theo nhịp fixed rate. Một subscription có thể còn đang chạy khi lượt subscribe khác bắt đầu, tùy thời gian hoàn tất, loại Publisher và scheduler; không mặc định business operation được serialize chỉ vì cùng một annotated method.

Điều này khác với một `ScheduledThreadPoolExecutor` periodic Runnable chạy đồng bộ: **Runnable chỉ mở subscription có thể kết thúc nhanh**, còn reactive work tiếp tục sau đó. Nếu tính nhất quán đòi hỏi một lượt chạy tại một thời điểm, phải dùng serializing/locking/admission policy phù hợp.

### 9.3. cron: khớp mốc lịch, không phải retry policy

```java
@Scheduled(cron = "0 */5 * * * *", zone = "Asia/Ho_Chi_Minh")
public Mono<Void> everyFiveMinutes() {
    return Mono.defer(() -> refreshService.refresh());
}
```

Cron mô tả **mốc phút theo lịch**, không tự quyết định số lần retry khi error, không đảm bảo chạy bù mọi missed firing. Lịch có thể bị trễ vì scheduler pool, runtime hoặc restart. Error trong một subscription không “tắt” biểu thức cron; Spring reactive scheduling vẫn đăng ký những lượt sau theo chính sách của scheduler.

## 10. Lỗi lúc tạo Publisher khác lỗi onError lúc subscribe

Một khác biệt quan trọng trong việc debug:

### 10.1. Sai: method ném trực tiếp khi Spring lấy Publisher một lần

```java
@Scheduled(fixedRate = 5000)
public Mono<Void> badAtRegistration() {
    validateConfiguration(); // Nếu ném exception, Spring chưa có Publisher hợp lệ.
    return Mono.empty();
}
```

Đây là fragment. Vì Spring gọi method để **lấy Publisher lúc đăng ký**, ngoại lệ ở `validateConfiguration()` có thể gây lỗi khởi tạo/đăng ký schedule (tùy lifecycle), chứ **không phải** một `onError` từ mỗi scheduled subscription. Đừng kiểm thử loại lỗi này rồi kết luận rằng reactive scheduled onError đã được ErrorHandler bắt.

### 10.2. Đúng nếu validation/work phải chạy mỗi subscription

```java
@Scheduled(fixedRate = 5000)
public Mono<Void> deferredValidation() {
    return Mono.defer(() -> {
        validateConfiguration();
        return service.perform();
    });
}
```

Đây là fragment; nếu `validateConfiguration()` ném lỗi trong deferred supplier, Reactor chuyển nó thành lỗi tại subscription. Spring subscriber ghi `WARN` nếu pipeline không recover, và lần subscription kế tiếp vẫn có thể được schedule.

### 10.3. Không dùng hot, one-shot Publisher một cách vô thức

Spring kỳ vọng Publisher từ reactive `@Scheduled` hỗ trợ **nhiều subscription kế tiếp**. Nếu trả một hot source, `Mono.cache()`, hoặc Publisher chỉ có thể đọc stream một lần, những lần schedule sau có thể không thực thi lại công việc như tưởng tượng; ví dụ cache có thể lặp lại kết quả/lỗi đầu thay vì làm mới dữ liệu.

Đừng viết `return Mono.just(expensiveCall())` nếu `expensiveCall()` phải chạy lại từng lượt. Dùng `Mono.defer` hoặc `Mono.fromCallable` cho nguồn cold; với I/O blocking, chọn executor Reactor phù hợp.

## 11. Khác biệt với @Async và WebFlux

Lỗi của reactive scheduled method không đồng nghĩa với:

- **@Async method trả Future:** lỗi trong worker có thể được caller quan sát qua `Future.get()`/`CompletableFuture` completion.
- **@Async void:** `AsyncUncaughtExceptionHandler` xử lý exception uncaught trong `@Async void`, không phải mặc định xử lý `Mono.onError` của `@Scheduled` reactive.
- **WebFlux HTTP handler:** reactive chain của một request có thể được web exception handling, filters và status mapping xử lý; không có HTTP response cho một background scheduled subscription chỉ vì nó phát onError.
- **Reactor Hooks.onErrorDropped:** đây là đường xử lý errors không còn subscriber có thể nhận trong một số tình huống; không phải cơ chế chuẩn để thay `doOnError` của một lỗi terminal được deliver hợp lệ.

Mỗi loại có **error boundary** riêng. Trộn chung chúng vào một handler toàn cục mà không xác định nguồn lỗi dễ gây mất quan sát hoặc đếm trùng.

## 12. Đo lỗi và kết quả trong production

Một hệ thống có nhiều scheduled reactive jobs nên quan sát ít nhất:

| Chỉ số | Ý nghĩa |
| --- | --- |
| `scheduled.subscription.started` | Số lượt subscription đã bắt đầu |
| `scheduled.subscription.completed` | Lượt hoàn tất bình thường |
| `scheduled.subscription.failed` | Lượt kết thúc bằng onError cuối cùng |
| `scheduled.subscription.cancelled` | Lượt bị cancel/shutdown |
| `scheduled.retry.attempts` | Số lần retry bên trong lượt |
| `scheduled.duration` | Thời gian từ subscribe đến terminal event |
| `scheduled.scheduler.lateness` | Trễ do scheduler so với thời điểm đến hạn |
| `scheduled.inflight` | Số lượt subscription đang xử lý đồng thời |
| `scheduled.outcome.status` | Business succeeded/failed/skipped, không chỉ tín hiệu terminal |

Một fragment instrument:

```java
return Mono.defer(() -> job.run())
        .doOnSubscribe(subscription -> metrics.incrementStarted())
        .doOnError(error -> metrics.incrementFailed(error))
        .doOnSuccess(value -> metrics.incrementCompleted())
        .doFinally(signal -> metrics.recordTerminal(signal));
```

Đây là fragment, có thể cần thay đổi metrics với `Mono<Void>` và status nghiệp vụ của bạn. `doOnSuccess` chạy khi Mono hoàn tất thành công kể cả rỗng; không đo các signals `onNext` của Spring scheduler để suy success vì Spring **bỏ qua emitted items**.

Quan trọng: phân biệt **lỗi business đã được onErrorResume “khôi phục”** với thành công thực sự. Nếu pipeline recover bằng `Mono.empty()`, downstream nhìn thấy completion bình thường; metrics business cần ghi lỗi **trước khi recover** và lưu thông tin rằng lượt này chỉ được skip/fallback.

### 12.1. Mã định danh để tránh logs vô nghĩa

Không gắn raw exception message, request ID ngẫu nhiên hoặc stack trace làm label metrics vì cardinality sẽ phình to. Dùng mã job, loại lỗi, outcome category; log riêng correlation ID/attempt ID để tra cứu chi tiết.

### 12.2. Quan sát cả executor lẫn Publisher

Scheduler metrics vẫn hữu ích: worker threads, active jobs, queue delays, task rejections. Nhưng chúng **không bao trùm outcome của Publisher**. Bảng điều khiển tốt phải kết hợp **scheduling layer** và **reactive subscription layer**.

## 13. Cancellation và shutdown khi job reactive còn chạy

Theo tài liệu Spring, khi hủy bean annotated hoặc đóng ApplicationContext, Spring hủy các scheduled tasks, bao gồm subscription tương lai và những subscription trước đó còn active.

Nhưng:

- Cancel subscription không đồng nghĩa mọi HTTP/JDBC call hoặc side effect đã rollback.
- Một Publisher không kết thúc có thể giữ tài nguyên; phải thiết kế timeout/resource cleanup.
- Retry/backoff dài có thể làm một subscription tồn tại lâu; cần xem chính sách shutdown.
- Trong fixed-rate, một số subscriptions có thể đang in-flight cùng lúc, cần theo dõi concurrency.
- Nếu job bắt buộc hoàn tất qua restart, in-memory `@Scheduled` không cung cấp durability; cần broker, outbox hoặc job store.

Đừng dùng một ErrorHandler của Runnable để mong nó quản lý cancellation/cleanup bên trong reactor chain. Những việc đó thuộc reactive lifecycle và contract của I/O APIs.

## 14. Nên xử lý lỗi ở lớp nào?

Có thể phân công trách nhiệm rõ ràng:

1. **Spring scheduler ErrorHandler**: quan sát exception thật sự ném ra khỏi scheduled Runnable imperative/infrastructure.
2. **Reactor doOnError**: ghi metric/log lỗi từ Publisher; không tự khôi phục.
3. **Reactor onErrorResume / onErrorMap / retryWhen**: quyết định chính sách phục hồi, chuyển lỗi và retry bên trong subscription.
4. **Job/application service**: lưu trạng thái hoàn tất, idempotency, audit, timeout, dead-letter hoặc compensation.
5. **Vận hành**: alert khi lỗi liên tiếp, độ trễ cao, số jobs không hoàn tất, tăng retry rate.

Với job tạo side effect quan trọng như thanh toán, không nuốt lỗi bằng một `onErrorResume(Mono.empty())` mà thiếu trạng thái durable. Với job cache refresh có thể bỏ qua một lượt, recover và ghi failure metric có thể là lựa chọn hợp lý.

Nếu nhiều team chia sẻ scheduler, tài liệu hóa policy: ai chịu trách nhiệm xử lý business onError, ai quản lý scheduler exception, và ở đâu metrics được phát. Đừng để cả hai lớp cùng nghĩ lớp còn lại “đã bắt mọi lỗi”.

## 15. Những lỗi phổ biến

| Hiểu sai / anti-pattern | Hậu quả | Cách sửa |
| --- | --- | --- |
| Cấu hình `setErrorHandler` rồi nghĩ bắt mọi `Mono.onError` | Reactive failures không đi vào handler | Instrument Publisher bằng `doOnError`/terminal status |
| Trộn exception Java với Reactive Streams onError | Sai error boundary | Phân biệt thrown exception và error signal |
| Tưởng method trả Mono được Spring gọi mỗi kỳ | Side effect ngoài Publisher chỉ chạy một lần | Dùng nguồn cold/deferred và đặt logic trong subscription |
| `Mono.just(expensiveCall())` | Eager execution và cache snapshot | Dùng `Mono.defer` hoặc `fromCallable` |
| `Mono.fromCallable(blockingJdbc)` rồi coi là nonblocking | Vẫn có thể block scheduler thread | Offload/đổi sang nonblocking client, quản lý concurrency |
| Bắt lỗi bằng `doOnError` rồi nghĩ đã recover | Error vẫn đi đến Spring subscriber | Dùng `onErrorResume` nếu có fallback hợp lệ |
| `onErrorResume(Mono.empty())` cho mọi lỗi | Che business failure, metrics sai | Recover có phân loại và ghi failure status |
| `retryWhen` vô hạn bên trong lịch dày | Một lượt chạy quá lâu/tạo tải và side effects trùng | Giới hạn retries, backoff, idempotency |
| Dùng `fixedRate` và nghĩ chỉ có một subscription đang chạy | Có thể overlap reactive subscriptions | Kiểm soát in-flight/serializing |
| Dùng `fixedDelay` với Publisher không bao giờ hoàn tất | Scheduler chờ lâu/kẹt lịch | Chọn finite Publisher, timeout, cancellation |
| Nghĩ lỗi onError dừng mọi subscription tương lai | Spring log WARN rồi schedule tiếp | Chính sách disable/circuit breaker phải thiết kế riêng |
| Thấy ErrorHandler counter=0 rồi công bố “0 failures” | Mất lỗi reactive | Metrics ở reactive signal boundary |
| So sánh output không tính WARN logs | Test nhầm stderr với stdout | Tách stdout assertions và logging |
| Tăng poolSize để “bắt” onError | Số thread không đổi hợp đồng lỗi | Sửa nơi quan sát error |
| Dùng `AsyncUncaughtExceptionHandler` cho `@Scheduled Mono` | Sai cơ chế của @Async void | Dùng đúng reactive operators |
| Publisher hot/one-shot được subscribe lại | Lượt sau không thực hiện công việc như mong muốn | Cold/repeatable Publisher |
| Không xử lý cancellation/shutdown | Rò tài nguyên hoặc tác vụ không hoàn tất | Timeout, disposal, resource cleanup |
| Chỉ retry vì scheduler vẫn tiếp tục | Không có retry durable hay đảm bảo xử lý từng item | Job state/outbox/reconciliation nếu cần |

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Một `@Scheduled` method `void` ném `RuntimeException`; một method khác trả `Mono.error(...)`. Cùng `ErrorHandler` của scheduler có nhất thiết nhận cả hai lỗi không?

Không. Lỗi imperative ném từ Runnable có thể được handler nhận. `Mono.onError` của reactive scheduled method được Spring subscriber xử lý riêng; theo Spring 6.2, nó được log WARN và không đi qua ErrorHandler contract theo cùng cách.

**Câu 2.** Spring gọi method `@Scheduled public Mono<Void> job()` mấy lần khi đăng ký lịch?

Spring lấy Publisher một lần lúc registration; sau đó scheduler **subscribe lại nhiều lần** theo cron/fixedRate/fixedDelay. Do đó logic cần lặp phải nằm trong Publisher deferred, không đặt như side effect ngoài nó.

**Câu 3.** `doOnError(...)` có ngăn Spring log WARN không?

Không. Nó chỉ quan sát tín hiệu. Nếu lỗi vẫn đi xuống cuối chain, Spring subscriber nhận onError và có thể WARN. Để recover, dùng operator như `onErrorResume(...)` có chủ đích.

**Câu 4.** `onErrorResume(ex -> Mono.empty())` có phải thành công nghiệp vụ không?

Không nhất thiết. Nó hoàn tất chain bình thường nhưng có thể đại diện một lượt **bỏ qua vì lỗi**. Cần ghi business outcome rõ ràng.

**Câu 5.** Nếu Publisher reactive fail hai lượt, Spring có tự thử lại ngay ba lần trong cùng lượt không?

Không. Các lượt schedule tiếp theo là các **subscription riêng**. Muốn retry trong cùng subscription, dùng `retryWhen(...)` và đặt giới hạn/backoff phù hợp.

**Câu 6.** Tại sao lỗi `throw` trước `return Mono...` có thể có hậu quả khác lỗi từ `Mono.defer(...)`?

Vì đoạn trước có thể chạy khi Spring gọi method để lấy Publisher lúc registration; đoạn trong defer chạy khi có subscriber. Một bên là lỗi method/registration, bên kia là reactive onError.

**Câu 7.** Vì sao dùng `fixedDelay` trên reactive Publisher không hoàn tất có thể làm lịch bị kẹt?

Spring block scheduled Runnable để giữ fixed-delay semantics dựa vào kết thúc subscription. Publisher không complete thì lần delay sau không đến theo điều kiện thông thường.

**Câu 8.** Có phải mọi giá trị `onNext` do scheduled Publisher phát ra đều được đưa vào một nơi lưu trữ của Spring?

Không. Spring bỏ qua/discard onNext của reactive scheduled method. Muốn xử lý kết quả, pipeline phải tự thực hiện side effect/lưu trạng thái theo nghiệp vụ.

## 17. Checklist trước khi đưa reactive @Scheduled vào production

- Đã xác nhận Spring Framework 6.1+ và return type hỗ trợ Publisher **deferred/repeatable subscription**?
- Có tránh `CompletableFuture` là một scheduled reactive return type không hỗ trợ deferred subscription?
- Method annotated không có tham số và chỉ khởi tạo Publisher một lần?
- Logic phải lặp được đặt trong `Mono.defer`/`fromCallable`/nguồn cold thích hợp?
- Có `@EnableScheduling` và scheduler bean phù hợp trong context?
- Có phân biệt lỗi ném từ Runnable với lỗi `onError` của Publisher?
- Có hiểu `setErrorHandler` không thay cho `doOnError`/reactive terminal metrics?
- Có policy lỗi: log, recover, retry, disable, escalate, dead-letter hoặc durable retry?
- Có phân biệt success, skipped/recovered, failure, cancellation và lỗi từng retry attempt?
- Có xác định `fixedDelay` phải chờ subscription, còn `fixedRate` có thể overlap subscriptions?
- Có giới hạn thời gian của network/DB calls và không để Publisher treo mãi?
- Có thread-safe/structured metrics và tránh high-cardinality labels?
- Khi shutdown, subscription in-flight có cleanup/cancel đúng ý đồ?
- Nếu ứng dụng chạy nhiều instances, có idempotency/distributed coordination nếu job không được phép trùng?
- Nếu việc không được mất, đã có durable job record/outbox thay vì chỉ in-memory @Scheduled?
- Integration test có xác nhận **reactive onError xảy ra** nhưng ErrorHandler không được gọi cho nó, và những subscription kế tiếp vẫn chạy không?

## 18. Kết luận

Trong Spring Framework 6.2, **`ErrorHandler` dành cho scheduled `Runnable`** xử lý ngoại lệ Java được ném ra ở boundary thực thi của Runnable. Còn với **reactive `@Scheduled`**, Spring lập lịch một Runnable để **subscribe Publisher**, rồi error nghiệp vụ được gửi qua **Reactive Streams `onError`** đến subscriber. Spring ghi lỗi ở mức **WARN**, phục hồi ở lớp subscriber và tiếp tục những scheduled subscriptions sau; **onError không đi vào ErrorHandler theo cùng cách với lỗi của Runnable imperative**.

Vì thế, cấu hình ErrorHandler là chưa đủ nếu muốn đo độ tin cậy của các reactive scheduled jobs. Hãy đặt `doOnError`/`onErrorResume`/`retryWhen` đúng vị trí trong pipeline, tách metrics của một subscription và từng retry attempt, thiết kế idempotency/cancellation/durability theo nghiệp vụ, và kiểm thử cả hai error channels. Đừng dùng mỗi số đếm ErrorHandler để suy kết quả của reactive schedule.

### Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling, reactive @Scheduled](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework 6.2.1 — @Scheduled API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework 6.2.1 — ThreadPoolTaskScheduler API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html)
- [Spring Framework 6.2.1 — ScheduledAnnotationReactiveSupport API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/annotation/ScheduledAnnotationReactiveSupport.html)
- [Project Reactor — Handling Errors](https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html)
- [Reactive Streams — Specification](https://www.reactive-streams.org/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-scheduling-007">Fixed-delay chờ đăng ký nhận hoàn tất để giữ ngữ nghĩa</a>

<details>
<summary>Xem chi tiết</summary>

# Fixed-delay chờ đăng ký nhận hoàn tất để giữ ngữ nghĩa

Trong Spring Framework 6.1 trở lên, `@Scheduled` hỗ trợ các phương thức reactive trả về `Publisher`, chẳng hạn Reactor `Mono<Void>` hoặc `Flux<T>`. Khác với phương thức Java `void` thông thường, method reactive có thể **trả về ngay một Publisher chưa thực thi**. Phần công việc thật chỉ bắt đầu khi có **subscription**, và subscription có thể tiếp tục bất đồng bộ sau khi `subscribe()` trả về.

Đây là nguyên nhân Spring phải có xử lý đặc biệt cho `@Scheduled(fixedDelay = ...)` reactive: **Spring tạo subscription vào mỗi lượt và chặn Runnable của scheduler cho đến khi subscription phát tín hiệu kết thúc**. Chỉ sau đó, scheduler mới bắt đầu đếm khoảng `fixedDelay` để kích hoạt lượt kế tiếp. Nếu chỉ đợi lời gọi `subscribe()` trả về, lịch sẽ thực chất tính thời gian từ **lúc gửi việc** thay vì **lúc lần xử lý trước kết thúc**, phá vỡ ý nghĩa fixed-delay.

Bài Knowledge thuộc phần **Lập lịch reactive** trong module **Spring Framework - concurrency**. Nội dung làm rõ “đăng ký nhận” (*subscription*) là gì, tại sao cần chờ terminal signal, Spring dùng cơ chế nào, so sánh với fixed-rate và cron, cách viết Publisher đúng, lỗi/timeout/cancellation, tác động tới scheduler thread, ví dụ Java 17 độc lập và kiểm thử tích hợp Spring.

## 1. Phân biệt bốn thời điểm: gọi method, subscribe, terminal và lượt kế tiếp

Mỗi lượt reactive được điều khiển bởi nhiều mốc khác nhau:

1. **Method cung cấp Publisher:** Spring gọi method có `@Scheduled` để lấy một đối tượng `Publisher`. Theo hợp đồng reactive scheduling, Spring **lấy Publisher một lần**, rồi đăng ký nhận từ nó nhiều lần theo lịch.
2. **Bắt đầu subscription:** Runnable của scheduler gọi `publisher.subscribe(subscriber)` cho từng lượt. Publisher dạng lazy/cold thường bắt đầu công việc tại đây.
3. **Subscription kết thúc:** subscriber nhận `onComplete()` hoặc `onError(Throwable)`. Đây là **terminal signal** cho lần subscription đó. `onNext(...)` không phải tín hiệu hoàn tất.
4. **Chờ fixed delay rồi subscribe tiếp:** với `fixedDelay=D`, lượt subscribe tiếp chỉ được lập lịch sau khoảng D tính từ khi **Runnable chờ subscription kết thúc và thoát**.

Timeline minh họa, với một subscription kéo dài 8 giây và `fixedDelay=5s`:

~~~text
Spring startup:
  gọi method @Scheduled reactive một lần  ---> nhận Publisher lạnh

Scheduler thread                       Publisher / reactive worker
    |                                              |
t=0 |--- subscribe #1 ---------------------------->|
    |       subscribe() có thể trả sớm              |
    |       nhưng scheduler Runnable CHỜ            |
    |                                              | I/O / async work
t=8 |<--- onComplete / onError ---------------------|
    |       Runnable hết chờ và return              |
    |--- đếm fixedDelay 5 giây ---------------------|
    |                                              |
t=13|--- subscribe #2 ---------------------------->|
    |       lại chờ terminal của subscription #2    |
    |                                              |
    v                                              v
~~~

Thời điểm trên chỉ là **ví dụ tính toán**, không phải cam kết thời gian chính xác tới mili giây. Scheduler có thể bị trễ vì CPU, thread pool, I/O, shutdown hoặc áp lực hệ thống. Điều quan trọng là khoảng nghỉ được lấy từ **mốc hoàn tất của lượt trước**, không từ mốc gọi method hoặc `subscribe()`.

### 1.1. Fixed-delay không hứa hoàn tất nghiệp vụ ngoài Publisher

Spring chỉ có thể chờ **Publisher mà method trả về**. Nếu Publisher đó hoàn tất ngay sau khi submit một task khác, Spring sẽ coi lượt subscription đã kết thúc, dù task con đang queue hoặc chạy.

Do đó cần phân biệt:

| Mốc | Spring fixed-delay reactive có thể dùng làm căn cứ? | Lưu ý |
| --- | --- | --- |
| Method `@Scheduled` return `Mono` | **Không** | Publisher thường được tạo một lần trước các lượt đăng ký nhận |
| Lời gọi `publisher.subscribe(...)` return | **Không đủ** | Có thể trả ngay khi work vẫn đang chạy |
| `onNext(value)` | **Không** | Giá trị được Spring bỏ qua; stream có thể tiếp tục |
| `onComplete()` | **Có** | Lượt Publisher hoàn tất bình thường |
| `onError(error)` | **Có** | Lượt Publisher kết thúc thất bại; Spring log và xử lý tiếp lịch |
| Một Future/worker bên ngoài được submit | **Chỉ khi được nối vào Publisher** | Nếu không compose, terminal của Publisher không đại diện cho worker đó |
| Remote service nhận event | **Không mặc nhiên** | Phải có acknowledgment phù hợp nếu yêu cầu kết quả cuối ở downstream |

“Completion” luôn phải gắn với **đúng ranh giới trách nhiệm**. Nếu nghiệp vụ chỉ cần xác nhận request gửi tới broker, terminal sau khi broker ACK có thể đủ. Nếu cần consumer xử lý xong, phải đưa tín hiệu hoàn tất của consumer vào workflow hoặc job status riêng.

## 2. Vì sao Java fixed-delay thông thường chưa đủ cho reactive?

Với một `Runnable` đồng bộ, `ScheduledExecutorService.scheduleWithFixedDelay(...)` tính delay từ thời điểm `Runnable.run()` kết thúc đến lần bắt đầu tiếp theo:

~~~java
scheduler.scheduleWithFixedDelay(
        () -> doSynchronousWork(),
        0, 5, TimeUnit.SECONDS);
~~~

Đây là **snippet Java minh họa**: cần `ScheduledExecutorService scheduler`, method `doSynchronousWork()` và imports. Nếu `doSynchronousWork()` thực hiện toàn bộ logic trên cùng thread trước khi return, fixed-delay tự nhiên có nghĩa “công việc xong, đợi thêm 5 giây”.

Nhưng một callback reactive kiểu sau **không giữ ngữ nghĩa nếu dùng trần với scheduled executor**:

~~~java
scheduler.scheduleWithFixedDelay(
        () -> asyncClient.refresh().subscribe(),
        0, 5, TimeUnit.SECONDS);
~~~

Đây là **anti-pattern minh họa**, `asyncClient.refresh()` trả `Mono<Void>`. Lời `subscribe()` thường trả rất nhanh, trong khi network call còn đang diễn ra. ScheduledExecutorService sẽ tính 5 giây từ khi callback `run()` return, không đợi `Mono` phát `onComplete`. Các lượt có thể overlap dù tưởng đã chọn fixed-delay.

**Spring reactive `@Scheduled` chủ động giải quyết sự khác biệt này:** với cấu hình fixed-delay, Runnable mà Spring đăng ký không chỉ `subscribe` rồi return. Nó **chờ subscription phát terminal signal**, để độ trễ được tính từ lúc subscription hoàn tất.

### 2.1. Mốc “đăng ký nhận” là gì?

Trong Reactive Streams, một `Publisher<T>` nhận lời gọi:

~~~java
void subscribe(Subscriber<? super T> subscriber);
~~~

Đây là chữ ký API rút gọn, không phải code cần tự triển khai trong bài. Một subscriber có lifecycle:

~~~text
onSubscribe(Subscription)  -->  onNext(value)*
                                    |
                                    +--> onComplete()
                                    |
                                    +--> onError(Throwable)
~~~

`onComplete` và `onError` là các tín hiệu terminal loại trừ nhau trong một subscription hợp lệ. `onNext` có thể xảy ra không lần nào, một lần hoặc nhiều lần, **không có nghĩa đã kết thúc**. Subscription cũng có thể bị `cancel()` và không gửi terminal như trường hợp hoàn tất thông thường.

Ví dụ `Mono<Void>` có thể không phát `onNext` nào mà vẫn hoàn tất bình thường bằng `onComplete`. Vì vậy không thể dùng “đã nhận được một giá trị” để làm điểm mốc fixed-delay. Và cũng không thể chỉ đếm số lần method Java được gọi vì method có thể chỉ được gọi một lần để tạo Publisher.

## 3. Spring thực hiện chờ subscription như thế nào?

Trong Spring Framework 6.1+, cơ chế xử lý reactive `@Scheduled` tạo một `Runnable` chịu trách nhiệm đăng ký nhận từ Publisher. Với cấu hình `fixedDelay` hoặc `fixedDelayString`, **Runnable này được chuyển thành lệnh subscribe-và-chờ**, thay vì chỉ subscribe.

Theo hợp đồng Javadoc của `@Scheduled`: *“If a fixed delay is configured, the subscription is blocked in order to respect the fixed delay semantics.”* Nghĩa là chờ **subscription kết thúc**, không phải chặn bất kỳ luồng reactive event-loop nào mà người viết tùy tiện gọi `.block()`.

Mô hình khái niệm:

~~~text
Runnable do Spring đăng ký với TaskScheduler:
    |
    +-- tạo subscriber cho lượt hiện tại
    +-- publisher.subscribe(subscriber)
    +-- với fixed-delay: chờ subscriber kết thúc
             |
             +-- onComplete(): giải phóng chờ
             +-- onError(): log/cập nhật lỗi, giải phóng chờ
             +-- cancel khi shutdown: cleanup theo lifecycle
    +-- Runnable return
    +-- TaskScheduler đợi tiếp fixedDelay
    +-- Runnable được kích hoạt lần tiếp theo
~~~

Đây là **sơ đồ giải thích hợp đồng**, không phải API/lệnh Java copy nguyên xi vào ứng dụng. Trong implementation Spring 6.x, code nội bộ tương ứng là `ScheduledAnnotationReactiveSupport` với một subscribing runnable và tín hiệu cho biết terminal đã tới. Ứng dụng **không cần tự viết latch hoặc gọi `Mono.block()`** chỉ để duy trì fixed-delay khi dùng reactive `@Scheduled` đúng cách.

### 3.1. Chờ ở scheduler thread, không có nghĩa toàn bộ xử lý đều blocking

Đây là điểm dễ hiểu nhầm:

- **Scheduler Runnable bị giữ/chặn** trong lúc đợi Publisher kết thúc để giữ thứ tự fixed-delay.
- **Publisher vẫn có thể thực thi bất đồng bộ/non-blocking** trên event-loop hoặc scheduler phù hợp của Reactor và reactive client.
- Một scheduler thread đang chờ terminal **không nhất thiết làm I/O của Publisher chạy đồng bộ**.
- Tuy nhiên, thread đó là **tài nguyên bị chiếm trong thời gian chờ**, vì vậy có tác động đến throughput và khả năng kích hoạt những lịch khác.

Vì lý do này, Spring không hứa rằng reactive fixed-delay là “không chiếm thread”. Nó đúng ngữ nghĩa fixed-delay nhưng phải tính đến **scheduler pool size, số lượng lịch và thời gian terminal**.

### 3.2. Nếu Publisher không bao giờ kết thúc?

Với `Mono.never()`, `Flux.never()` hoặc một stream vô hạn không có terminal signal, fixed-delay có thể **chờ vô thời hạn**:

~~~java
@Scheduled(fixedDelay = 5000)
public Flux<Long> badInfiniteSchedule() {
    return Flux.interval(Duration.ofSeconds(1));
}
~~~

Đây là **method Spring/Reactor minh họa**, cần bean managed, `@EnableScheduling` và imports. `Flux.interval(...)` là stream kéo dài không giới hạn; nếu không có `take(...)`/timeout/cancellation, nó không phát `onComplete`. Spring không có căn cứ để đếm “5 giây sau khi lần chạy trước kết thúc”, nên lượt fixed-delay kế tiếp có thể không tới cho đến khi có cancellation/stop.

Nếu mục đích là stream chạy vĩnh viễn, hãy thiết kế **một subscription dài hạn có lifecycle quản lý riêng**, không giả làm công việc định kỳ với fixed-delay. Nếu từng lượt cần hữu hạn, dùng `take(n)`, `then()` sau một thao tác hữu hạn, `timeout(...)` hoặc giới hạn deadline thích hợp; không dùng một Publisher có thể đứng mãi mà không có chính sách dừng.

## 4. Fixed-delay, fixed-rate và cron khác nhau với reactive @Scheduled

Cả ba đều có thể tạo **một Publisher** và subscribe lặp lại; điều khác nhau là **khi nào mỗi lượt được subscribe**.

| Lịch | Khi nào subscribe tiếp? | Có chờ terminal trước khi Runnable return? | Rủi ro cần xem |
| --- | --- | --- | --- |
| `@Scheduled(fixedDelay=5000)` | Sau khi subscription trước kết thúc, chờ thêm khoảng 5 giây | **Có**, theo hợp đồng reactive fixed-delay | Chiếm scheduler thread, stream không terminal sẽ kẹt |
| `@Scheduled(fixedRate=5000)` | Theo chu kỳ thời gian tính từ các mốc dự kiến của scheduler | **Không theo cơ chế blocking fixed-delay** | Subscription trước có thể còn chạy khi subscription tiếp được mở |
| `@Scheduled(cron="...")` | Theo các mốc được cron/trigger tính | **Không theo cơ chế blocking fixed-delay** | Trigger tiếp có thể xuất hiện khi việc trước chưa xong |
| `@Scheduled` đồng bộ trả `void` với fixed-delay | Sau khi method Java thực thi xong, chờ delay | Method hoàn tất khi return | Nếu method chỉ dispatch async, nó return trước khi job xong |
| `@Scheduled` reactive với Publisher kết thúc ngay | Theo fixed delay sau terminal ngay | Có, nhưng terminal ngay | Nếu side effect thực tế ở task tách rời, delay không chờ task đó |

Không suy ra rằng `fixedRate` bắt buộc chạy song song mọi lượt; scheduler pool, độ dài callback, thread scheduling và các nguồn tài nguyên có thể tạo trễ. Điểm khác biệt là **fixed-rate không có quy tắc chờ subscription terminal rồi mới bắt đầu tính delay**, nên có **khả năng overlap subscription**.

Nếu một báo cáo cần xử lý tuần tự tuyệt đối và không cho lần mới bắt đầu trước khi lần trước kết thúc, fixed-delay với Publisher **trọn vẹn và hữu hạn** là lựa chọn dễ lý giải. Nếu cần throughput, chạy theo tần suất độc lập hoặc xử lý backlog có kiểm soát, fixed-rate/cron có thể phù hợp hơn nhưng phải quản lý concurrency/retry/overload.

### 4.1. Lưu ý khi dùng SimpleAsyncTaskScheduler

Spring `SimpleAsyncTaskScheduler` có mô hình một scheduler thread điều phối và có thể dùng thread mới hoặc virtual thread cho các execution theo cấu hình JDK. Nhưng **fixed-delay** là trường hợp đặc biệt: các fixed-delay task chạy trên **một scheduler thread** trong implementation này, khác cách fixed-rate/cron được dispatch.

Khi reactive fixed-delay chờ terminal lâu, cần đặc biệt cân nhắc khả năng scheduler thread bị chiếm, gây trễ các lịch khác. Theo Javadoc Spring, khi dùng virtual threads với SimpleAsyncTaskScheduler, **fixed-rate hoặc cron** thường được khuyến nghị hơn fixed-delay. Đó là lựa chọn hiệu năng, không làm mất nhu cầu đồng bộ nghiệp vụ: nếu đổi lịch, phải bổ sung điều khiển overlap/flow phù hợp.

Virtual threads ổn định từ Java 21; ví dụ Java độc lập bên dưới tương thích Java 17 và không cần virtual thread.

## 5. Java 17 độc lập: chứng minh điểm sai khi callback chỉ dispatch

Chương trình này dùng **Java concurrency chuẩn**, không yêu cầu Spring hoặc Reactor. Nó mô phỏng hai cách xử lý cùng một `scheduleWithFixedDelay`:

1. **Cách sai:** callback của scheduler chỉ gửi task tới worker rồi return ngay. Lần scheduler tiếp theo có thể đến trong khi worker trước chưa xong.
2. **Cách đúng để mô phỏng Spring reactive fixed-delay:** callback scheduler gửi một công việc bất đồng bộ nhưng **đợi `CompletableFuture` của công việc đó hoàn tất** trước khi return. Lần scheduler tiếp theo chỉ được chạy sau terminal + fixed delay.

Chương trình **không phải implementation của Reactor Publisher**. `CompletableFuture.join()` ở đây chỉ là cách mô phỏng việc callback chờ completion của một nguồn bất đồng bộ. Khi dùng `@Scheduled` reactive thật, Spring tự chờ subscription; đừng dùng `join()` hoặc `block()` bừa bãi trong event-loop để bắt chước.

Tạo tệp **`ReactiveFixedDelayContractDemo.java`**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ReactiveFixedDelayContractDemo {
    private static void await(CountDownLatch latch, String name) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out: " + name);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted: " + name, ex);
        }
    }

    public static void main(String[] args) {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        ExecutorService worker = Executors.newFixedThreadPool(2);

        CountDownLatch naiveStarted = new CountDownLatch(1);
        CountDownLatch releaseNaive = new CountDownLatch(1);
        CountDownLatch naiveFinished = new CountDownLatch(1);
        CountDownLatch naiveSecondTick = new CountDownLatch(1);

        CountDownLatch reactiveStarted = new CountDownLatch(1);
        CountDownLatch releaseReactive = new CountDownLatch(1);
        CountDownLatch reactiveFinished = new CountDownLatch(1);
        CountDownLatch reactiveSecondTick = new CountDownLatch(1);

        try {
            AtomicInteger naiveCalls = new AtomicInteger();
            ScheduledFuture<?> naive = timer.scheduleWithFixedDelay(() -> {
                int tick = naiveCalls.incrementAndGet();
                if (tick == 1) {
                    worker.execute(() -> {
                        naiveStarted.countDown();
                        try {
                            await(releaseNaive, "release naive work");
                        } finally {
                            naiveFinished.countDown();
                        }
                    });
                } else if (tick == 2) {
                    naiveSecondTick.countDown();
                }
            }, 0, 40, TimeUnit.MILLISECONDS);

            await(naiveStarted, "naive first worker start");
            await(naiveSecondTick, "naive second tick");
            System.out.println("1. Naive callback ended while child still active: "
                    + (naiveFinished.getCount() == 1));
            naive.cancel(false);
            releaseNaive.countDown();
            await(naiveFinished, "naive work cleanup");

            AtomicInteger reactiveCalls = new AtomicInteger();
            AtomicLong firstTerminalNs = new AtomicLong();
            AtomicLong secondTickNs = new AtomicLong();
            ScheduledFuture<?> blocked = timer.scheduleWithFixedDelay(() -> {
                int tick = reactiveCalls.incrementAndGet();
                if (tick == 1) {
                    CompletableFuture<Void> subscription = CompletableFuture.runAsync(() -> {
                        reactiveStarted.countDown();
                        await(releaseReactive, "release reactive work");
                    }, worker);
                    subscription.join();
                    firstTerminalNs.set(System.nanoTime());
                    reactiveFinished.countDown();
                } else if (tick == 2) {
                    secondTickNs.set(System.nanoTime());
                    reactiveSecondTick.countDown();
                }
            }, 0, 40, TimeUnit.MILLISECONDS);

            await(reactiveStarted, "reactive first subscription start");
            boolean nextTickWhilePending =
                    reactiveSecondTick.await(150, TimeUnit.MILLISECONDS);
            System.out.println("2. Next tick while first subscription active: "
                    + nextTickWhilePending);
            if (nextTickWhilePending) {
                throw new IllegalStateException("Fixed delay violated");
            }
            releaseReactive.countDown();
            await(reactiveFinished, "first subscription complete");
            await(reactiveSecondTick, "second subscription");
            System.out.println("3. First subscription completed: "
                    + (reactiveFinished.getCount() == 0));
            System.out.println("4. Next tick started after terminal signal: "
                    + (secondTickNs.get() >= firstTerminalNs.get()));
            System.out.println("5. Delay from terminal was at least 40ms: "
                    + (secondTickNs.get() - firstTerminalNs.get()
                            >= TimeUnit.MILLISECONDS.toNanos(40)));
            System.out.println("6. Periodic schedule cancellation: "
                    + blocked.cancel(false));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", ex);
        } finally {
            releaseNaive.countDown();
            releaseReactive.countDown();
            timer.shutdownNow();
            worker.shutdownNow();
        }
    }
}
~~~

Lệnh chạy với JDK 17+:

~~~bash
javac --release 17 ReactiveFixedDelayContractDemo.java
java ReactiveFixedDelayContractDemo
~~~

**Stdout đã xác minh** sau khi biên dịch với `--release 17`:

~~~text
1. Naive callback ended while child still active: true
2. Next tick while first subscription active: false
3. First subscription completed: true
4. Next tick started after terminal signal: true
5. Delay from terminal was at least 40ms: true
6. Periodic schedule cancellation: true
~~~

**Tại sao kết quả có ý nghĩa?**

- Trong phần đầu, scheduler gọi `worker.execute(...)` rồi return, không đợi worker. Worker bị giữ bởi `releaseNaive`; dù vậy tick tiếp theo đến, chứng tỏ **fixed-delay của callback** không phải fixed-delay của job con.
- Trong phần sau, callback chờ `subscription.join()`; `releaseReactive` chưa mở nên Future không thể hoàn tất, và tick tiếp theo **chưa xuất hiện**.
- Sau khi mở `releaseReactive`, `join()` trả về, callback return; chỉ lúc đó scheduler mới bắt đầu tính khoảng 40 ms. Chương trình ghi timestamp bằng `System.nanoTime()` để kiểm tra thứ tự và khoảng cách, chứ không in thời gian tùy máy.
- `CountDownLatch` và timeout hữu hạn giữ thứ tự kiểm thử và tránh phụ thuộc `Thread.sleep(100)` may rủi.
- `cancel(false)` và `shutdownNow()` nhằm cleanup, **không có nghĩa rollback side effect** của task đã chạy.

Chương trình chỉ mô phỏng hợp đồng completion; đường triển khai Spring thật chờ **tín hiệu terminal từ Subscriber**, không nhất thiết dùng `CompletableFuture.join()`. Phân biệt này quan trọng khi đọc ví dụ hoặc áp dụng trong code reactive.

## 6. Reactive Publisher được tạo một lần, subscribe nhiều lần

Spring giữ một Publisher từ reactive method và subscribe lại nó từng tick. Với fixed-delay, **Publisher được theo dõi phải hoàn tất công việc thực của lượt đó**; nếu pipeline tách một child `subscribe()` riêng rồi `Mono.empty()`, scheduler tính delay từ terminal của outer Mono, không chờ child.

~~~java
// Ví dụ trong Spring bean có reactiveClient:
@Scheduled(fixedDelay = 5000)
public Mono<Void> refresh() {
    return Mono.defer(reactiveClient::refresh).then();
}
~~~

Nguồn cold/deferred cho phép tạo công việc mới mỗi tick; `Mono.just(expensiveReadNow())` có thể chỉ gọi hàm một lần khi assembly, còn `fromCallable` lazy nhưng không tự offload blocking. Một Publisher vô hạn không terminal có thể làm scheduler thread chờ vô thời hạn. Các bài khác phân tích sâu defer; ở đây trọng tâm là **điểm terminal xác định fixed-delay**.

## 7. Ví dụ Spring Framework: fixed-delay chờ terminal Mono<Void>

Dưới đây là mã **tích hợp Spring Framework 6.1+/6.2, Java 17+, Reactor Core và một reactive client thật**. Các tệp này không biên dịch với `javac` thuần không có Spring/Reactor dependencies; phần runnable không phụ thuộc thư viện đã được xác minh ở mục 5.

### 7.1. Cấu hình scheduler

Tệp `ReactiveScheduleConfig.java`:

~~~java
package example.reactivejobs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ReactiveScheduleConfig {

    @Bean("reactiveJobScheduler")
    public ThreadPoolTaskScheduler reactiveJobScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("reactive-job-schedule-");
        return scheduler;
    }
}
~~~

Với Spring-managed bean, framework quản lý lifecycle khởi tạo và đóng scheduler theo cấu hình. `setPoolSize(2)` chỉ là số minh họa. Với fixed-delay reactive, scheduler Runnable có thể bị giữ trong lúc đợi terminal, nên cấu hình một thread cho quá nhiều lịch dài có thể gây trễ.

### 7.2. Hợp đồng reactive client

Tệp `ReactiveReportPort.java`:

~~~java
package example.reactivejobs;

import reactor.core.publisher.Mono;

public interface ReactiveReportPort {
    Mono<Void> reconcilePending();
}
~~~

Interface này thể hiện một thao tác trả `Mono<Void>`. **Hợp đồng quan trọng:** Mono chỉ `onComplete` khi phần nghiệp vụ mà caller cần theo dõi đã thực sự hoàn tất. Nếu implementation chỉ gửi lệnh sang một queue khác và trả `Mono.empty()` ngay, terminal đó mới chỉ xác nhận **đã gửi**, không phải job được queue xử lý xong.

Cần một bean implementation thật của `ReactiveReportPort`, ví dụ dùng Reactive WebClient/R2DBC và lỗi nghiệp vụ phù hợp. Không dùng placeholder “mọi việc xong” khi thao tác vẫn đang thực thi ngoài stream.

### 7.3. Scheduled reactive method với timeout và hook quan sát

Tệp `ReactiveReportSchedule.java`:

~~~java
package example.reactivejobs;

import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ReactiveReportSchedule {

    private final ReactiveReportPort port;

    public ReactiveReportSchedule(ReactiveReportPort port) {
        this.port = port;
    }

    @Scheduled(
            fixedDelayString = "${jobs.reconcile.delay:PT5S}",
            initialDelayString = "${jobs.reconcile.initial-delay:PT1S}",
            scheduler = "reactiveJobScheduler")
    public Mono<Void> reconcile() {
        return Mono.defer(port::reconcilePending)
                .timeout(Duration.ofSeconds(20))
                .doOnSubscribe(ignored ->
                        System.out.println("Reactive run subscribed"))
                .doOnSuccess(ignored ->
                        System.out.println("Reactive run completed"))
                .doOnError(error ->
                        System.err.println("Reactive run failed: "
                                + error.getClass().getSimpleName()))
                .then();
    }
}
~~~

Từ mã này:

1. Spring lấy `Mono<Void>` từ `reconcile()` **một lần**; nhờ `Mono.defer(port::reconcilePending)` mà `port.reconcilePending()` được gọi **lại cho từng subscription**.
2. `@Scheduled(fixedDelayString=...)` kích hoạt một lượt subscription theo khoảng chờ; mặc định placeholder `PT5S` là thời lượng 5 giây theo ISO-8601.
3. `timeout(Duration.ofSeconds(20))` giới hạn thời gian **chờ terminal của Publisher** theo cơ chế Reactor. Khi timeout, nó phát lỗi `TimeoutException` xuống stream và có thể phát cancellation upstream.
4. `doOnSuccess(...)` xảy ra khi Mono hoàn tất bình thường; với `Mono<Void>` đối số có thể là `null`. `doOnError(...)` quan sát lỗi; không phải một cơ chế nuốt lỗi hoặc retry.
5. Spring reactive scheduler chờ `onComplete` hoặc `onError` của lần subscription. Sau khi kết thúc và Runnable return, delay mới bắt đầu.
6. Đoạn `System.out` chỉ phục vụ minh họa. Production dùng logger/metrics có correlation id, tránh log thông tin nhạy cảm; đừng in hoặc xử lý blocking lâu trong hook trên event-loop.
7. Spring 6.1+ hỗ trợ thuộc tính `scheduler` của `@Scheduled` để chọn bean theo qualifier/tên. Các phiên bản cũ hơn không có thuộc tính này.

**Giới hạn của timeout:** một Reactor timeout có thể gửi `cancel()` tới upstream, nhưng không có bảo đảm mọi socket, HTTP request, database transaction hoặc side effect remote lập tức dừng/rollback. Cần timeout I/O thực tế và idempotency/compensation theo yêu cầu.

### 7.4. Application properties

Ví dụ cho Spring Boot 3.x:

~~~yaml
jobs:
  reconcile:
    delay: PT5S
    initial-delay: PT1S
~~~

Đây là một phần cấu hình `application.yml` dùng trong **ứng dụng Spring Boot**, không phải cấu hình tự được đọc trong mọi ApplicationContext Spring thuần. Nếu ứng dụng muốn điều chỉnh delay theo môi trường, có thể override bằng properties/env được Spring resolve. Không giả định thay file cấu hình trong lúc ứng dụng đang chạy làm `@Scheduled` tự re-register lịch; thay đổi lịch động cần cơ chế riêng.

### 7.5. Chú ý “blocking scheduler” không phải lý do gọi block() trong Mono

Một anti-pattern:

~~~java
@Scheduled(fixedDelay = 5000)
public Mono<Void> wrongManualBlock() {
    return Mono.fromRunnable(() ->
            blockingReactiveCall().block());
}
~~~

Đây là **method minh họa**, `blockingReactiveCall()` là một method giả định trả Mono và có thể dẫn tới blocking không an toàn. Nếu được gọi trong Reactor non-blocking thread, `block()` có thể gây lỗi hoặc làm nghẽn event-loop. Spring đã có cơ chế **chờ subscriber trong scheduled Runnable**; logic nghiệp vụ nên giữ reactive end-to-end hoặc chuyển nguồn blocking qua executor phù hợp. Đừng thêm một lớp block tùy tiện chỉ để thấy fixed-delay “thật”.

## 8. Anti-pattern quan trọng: tự subscribe rồi trả Mono rỗng

Một lỗi phổ biến làm hỏng chính chủ đề này:

~~~java
@Scheduled(fixedDelay = 5000)
public Mono<Void> wrongNestedSubscription() {
    return Mono.fromRunnable(() -> {
        reactiveClient.refresh().subscribe(); // Fire-and-forget
    });
}
~~~

Đây là **method Spring/Reactor minh họa**; `reactiveClient.refresh()` trả Mono, code không có đủ bean/import để chạy độc lập. Mỗi lượt Spring subscribe vào Mono ngoài thì callback thực hiện `reactiveClient.refresh().subscribe()`, nhưng **Mono ngoài hoàn tất ngay khi việc subscribe lồng nhau trả về**. Scheduler fixed-delay đếm từ lúc **subscription ngoài** kết thúc, không đợi **subscription của client** thực sự thành công hay thất bại.

Hậu quả:

- Lần xử lý tiếp có thể bắt đầu khi request lần trước còn chạy, dẫn tới overlap.
- Lỗi của inner subscription không nhất thiết đi qua kênh lỗi của Mono ngoài/Spring scheduled reactive.
- Shutdown/cancel subscription ngoài không mặc nhiên cancel child subscription được tạo thủ công.
- Metrics “completed” của outer Mono có thể tăng khi business thực tế chưa xong.

Cách đúng hơn:

~~~java
@Scheduled(fixedDelay = 5000)
public Mono<Void> correctComposedSubscription() {
    return Mono.defer(() -> reactiveClient.refresh())
               .then();
}
~~~

Khi đó **Publisher được Spring subscribe chính là pipeline theo dõi công việc**. `onComplete`/`onError` của pipeline đại diện cho terminal của `reactiveClient.refresh()` theo hợp đồng nó cung cấp. Reactor chịu trách nhiệm nối các operator và truyền cancellation/error trong cùng chain.

Không cần tự gọi `subscribe()` trong method `@Scheduled` reactive; mục đích của Spring chính là quản lý các lần đăng ký nhận.

### 8.1. Khác biệt với Mono.just và Mono.defer

So sánh:

~~~java
return Mono.just(expensiveReadNow());              // Eager: có thể chỉ tính một lần
return Mono.fromCallable(() -> expensiveReadNow()); // Deferred per subscribe
return Mono.defer(() -> reactiveClient.refresh()); // Deferred pipeline per subscribe
~~~

Đây là các biểu thức Reactor minh họa, phải đặt trong method có kiểu trả về phù hợp. `Mono.just(...)` đánh giá tham số Java **ngay khi biểu thức được tạo**. Vì Spring lấy Publisher một lần, `expensiveReadNow()` có thể chỉ chạy lúc đăng ký lịch. `Mono.fromCallable` và `Mono.defer` cho phép công việc xảy ra khi **mỗi subscriber** tới.

Cần chú ý cả trường hợp Publisher hot/sharing: nếu dùng một hot source chỉ cho subscriber đến sau nghe những sự kiện còn lại, mỗi lượt fixed-delay không nhất thiết tương ứng một operation độc lập. Trong định kỳ, thường dùng **cold, repeatably subscribable Publisher**.

## 9. onNext, onError, onComplete: Spring sẽ quan sát gì?

Spring reactive `@Scheduled` bỏ qua các giá trị `onNext` do Publisher phát ra. Với `Flux<String>`:

~~~java
@Scheduled(fixedDelay = 5000)
public Flux<String> messages() {
    return Flux.just("first", "second");
}
~~~

Đây là snippet Spring/Reactor minh họa. Mỗi lần subscription phát hai `onNext` rồi `onComplete`. Scheduler chỉ chờ terminal để xác định lúc bắt đầu delay; nó **không sử dụng** hai giá trị này làm return value hay phương tiện giao tiếp nghiệp vụ.

### 9.1. Lỗi không làm lịch tự dừng

Theo Spring reference cho reactive scheduling:

- Khi Publisher phát `onError(error)`, Spring ghi log ở mức **WARN** và phục hồi vòng lặp lịch.
- Lỗi nằm trong luồng asynchronous Publisher, **không ném qua Scheduled Runnable** theo cách xử lý error thông thường.
- Vì vậy `ErrorHandler` dùng cho scheduled `Runnable` đồng bộ **không phải kênh xử lý** các `onError` từ reactive method.
- Với fixed-delay, `onError` là **terminal signal** cho lượt đó; Runnable thoát chờ và lịch tiếp tục sau delay.
- Lượt sau subscribe lại Publisher; nếu nguồn vẫn lỗi, lịch vẫn có thể tiếp tục thất bại nhiều lần và gây bão log hoặc downstream load.

Nếu muốn ghi metric lỗi chính xác, đặt `doOnError(...)` hoặc instrumentation trong Publisher. Nếu ứng dụng cần policy dừng lịch sau N lỗi, phải thiết kế cơ chế quản lý schedule/job rõ ràng; đừng kỳ vọng một `Mono.error(...)` tự hủy lịch.

### 9.2. Retry và timeout có thể kéo dài khoảng thời gian giữa lượt

Ví dụ:

~~~java
return Mono.defer(() -> reactiveClient.refresh())
        .retryWhen(Retry.backoff(2, Duration.ofMillis(200)))
        .timeout(Duration.ofSeconds(20))
        .then();
~~~

Đây là snippet Reactor, cần `reactor.util.retry.Retry` và `Duration`, giả định client trả Publisher hữu hạn. Retry ở **trong cùng subscription** sẽ trì hoãn terminal; Spring fixed-delay chỉ bắt đầu tính delay khi **toàn bộ chain retry/timeout** kết thúc.

Điều này phù hợp nếu mục tiêu là “hoàn thành (hoặc thất bại) lượt xử lý trước khi lên lượt mới”. Nhưng không nên dùng retry vô hạn hoặc retry mọi loại lỗi. Giới hạn số lần, backoff/jitter, deadline, idempotency và phân loại lỗi không thể sửa. Nếu remote side effect đã xảy ra trước lỗi response, retry có thể tạo side effect trùng.

Thứ tự operator cũng quan trọng: đặt `timeout` **bên ngoài retry chain** giới hạn tương đối tổng thời gian; đặt timeout bên trong có thể giới hạn từng attempt riêng. Khi triển khai thật cần kiểm tra version Reactor, scheduler time và response của client khi bị cancel.

### 9.3. Nếu muốn giữ lịch chạy đều nhưng không cho overlap?

Fixed-rate + concurrency guard là một phương án khác nếu bạn chấp nhận những tick bị bỏ qua hoặc hoãn, ví dụ một flag/lock để chỉ nhận một job tại một thời điểm. Nhưng một flag đơn giản trong một JVM không ngăn overlap giữa nhiều application replicas; khi cần tránh chạy trùng trên cluster, dùng distributed lock/job store/idempotency.

Fixed-delay có thể đảm bảo thứ tự **subscription của một scheduled declaration** được nối đúng trong Publisher, nhưng **không** bảo đảm toàn cụm chỉ có một subscription, cũng không biết công việc đang chạy ngoài Publisher.

## 10. Hủy, shutdown và sự khác biệt giữa cancel Future với cancel subscription

Khi bean có `@Scheduled` reactive bị hủy hoặc ApplicationContext đóng, Spring hủy các scheduled tasks và cả những subscription trước đó còn active, theo tài liệu scheduling. Tuy nhiên:

- Việc **cancel subscription** gửi tín hiệu ngừng tới Publisher/Subscriber theo cơ chế reactive. Nó không tương đương “mọi I/O đã dừng và mọi side effect được rollback”.
- Một upstream có thể không hỗ trợ hủy vật lý request đã gửi tới remote server.
- Nếu Publisher bị kẹt tại một API blocking không quan sát cancellation, quá trình shutdown có thể không sạch như kỳ vọng; nên có timeout I/O và lifecycle cleanup.
- `CompletableFuture.cancel(true)` không phải cơ chế điều khiển thay thế cho reactive subscription: trong implementation CompletableFuture chuẩn, tham số `mayInterruptIfRunning` không có tác dụng ngắt worker.
- Với `@Scheduled` reactive fixed-delay, nếu subscription vô hạn thì scheduler có thể chờ lâu; shutdown/cancellation là một nhánh lifecycle riêng, cần kiểm thử để không giữ thread quá thời hạn cho phép.

Không thiết kế “Cancel job” bằng cách chỉ đổi một trạng thái Future rồi trả thông báo *“tác vụ đã dừng hoàn toàn”*. Nên phân biệt `CANCEL_REQUESTED`, `CANCELLING` và `CANCELLED` khi ứng dụng cần xác nhận tiến trình dừng thật.

## 11. Tác động vận hành: một lịch reactive có thể chiếm scheduler thread

Với fixed-delay reactive, Spring **giữ scheduler Runnable đang chờ terminal**. Nếu số lịch fixed-delay dài lớn hơn năng lực scheduler threads, các lịch khác có thể bị chậm, ngay cả khi I/O reactive bên ngoài vẫn non-blocking.

Bảng tình huống:

| Tình huống | Hệ quả |
| --- | --- |
| Publisher hoàn tất sau 50 ms, delay 5 s | Thường có nhiều thời gian scheduler thread rảnh sau completion |
| Publisher hoàn tất sau 20 s, delay 5 s | Chu kỳ giữa hai lần bắt đầu có thể khoảng 25 s cộng overhead |
| Publisher bị `Mono.never()` hoặc chờ I/O vô hạn | Runnable có thể chờ vô hạn; không có trigger tiếp |
| Có 10 scheduled methods reactive fixed-delay, pool scheduler 1 thread | Một subscription chờ lâu có thể trì hoãn những lịch khác |
| Callback tự `subscribe()` một child rồi return `Mono.empty()` | Lịch có vẻ chạy đúng delay nhưng không theo dõi completion child |
| Chọn fixed-rate để tránh giữ scheduler thread | Có thể overlap subscription; phải bổ sung giới hạn concurrent jobs |
| Nhiều app replicas cùng có `@Scheduled` | Mỗi instance có thể chạy cùng lịch; cần điều phối nếu yêu cầu singleton |

Với `ThreadPoolTaskScheduler`, đặt pool size theo số lịch có thể cùng chờ terminal và yêu cầu latency; không chọn số ngẫu nhiên. Nhưng tăng pool không sửa được Publisher không terminal, không thay thế timeout I/O, không làm DB connection pool tự tăng, và không khắc phục duplicate processing trên cluster.

### 11.1. Metrics nên đo ở ba cấp

| Mốc | Tên gợi ý | Ý nghĩa |
| --- | --- | --- |
| Khi scheduler kích hoạt subscription | `subscription_started_total` | Đã có một lượt subscribe, chưa có success |
| Khi Publisher phát onComplete | `subscription_completed_total` | Lượt stream kết thúc bình thường |
| Khi Publisher phát onError | `subscription_failed_total` | Lượt stream thất bại và đã terminal |
| Khi bị cancel | `subscription_cancelled_total` | Bị hủy, chưa chứng minh side effect rollback |
| Từ subscribe đến terminal | `subscription_duration` | Thời gian giữ scheduler Runnable trong fixed-delay |
| Từ terminal tới lần subscribe tiếp | `fixed_delay_observed` | Kiểm tra khoảng chờ thực tế |
| Active scheduled work | `scheduler_active_threads` | Áp lực thread scheduler |
| Child job queue/run | `business_queue_wait`, `business_run_duration` | Nếu Publisher dispatch việc đến executor khác |
| Lần chạy theo job id | `run_status` | Sự thật nghiệp vụ độc lập với tín hiệu của scheduler |

Tên metric trên là **gợi ý thiết kế**, không phải tên metric mặc định của Spring. Có thể dùng Spring Observation/Micrometer hoặc doOn* Reactor hooks; cần phân biệt success của stream với success của workflow ngoài stream, và tránh đưa job id có cardinality quá cao làm metric tag.

Khi xem dashboard, một lịch `fixedDelay=5s` nhưng cách nhau 30 giây không nhất thiết sai: có thể 25 giây dành để chờ subscription hoàn tất, sau đó mới delay 5 giây. Cần đo hai khoảng riêng.

## 12. Kiểm thử fixed-delay reactive đúng hợp đồng

Một test chất lượng cần xác minh **không subscribe lần hai khi lần đầu chưa terminal**, và **subscribe lần hai sau terminal cộng delay**. Đừng chỉ đo số lần annotated method Java được gọi: method có thể được gọi **một lần để tạo Publisher**, nhưng có nhiều subscription.

### 12.1. Test kế hoạch với một Publisher controllable

Có thể dùng Reactor `Mono.create(...)`, `Sinks` hoặc test publisher để giữ lần subscription đầu chưa hoàn tất:

1. Bật Spring test ApplicationContext với `@EnableScheduling`, bean `@Scheduled(fixedDelay=...)` và scheduler dành riêng cho test.
2. Publisher của method được tạo theo cách deferred, tăng counter **mỗi lần subscribe**.
3. Khi subscription đầu bắt đầu, giữ nó pending bằng test-controlled sink và báo latch `firstSubscribed`.
4. Chờ một khoảng **lớn hơn fixedDelay** mà không phát terminal; khẳng định counter chưa tăng lên hai. Không suy luận từ `Thread.sleep(100)` đơn lẻ rằng subscription đầu chắc đã start; dùng latch xác định trước.
5. Phát `sink.success()` hoặc terminal failure, đợi marker của subscription thứ hai và khẳng định subscription thứ hai chỉ xuất hiện sau terminal.
6. Kiểm tra `onError` cũng giải phóng lượt trước và cho lịch tiếp tục; chứng minh `ErrorHandler` đồng bộ không được gọi vì một `Mono.error` bên trong.
7. Cleanup ApplicationContext trong `finally` để cancel active subscription và scheduler; không để publisher vô hạn khiến test treo.

Nếu test đòi hỏi thời gian chính xác, dùng test scheduler/clock hoặc đo bằng monotonic clock với khoảng chờ có biên độ an toàn. `StepVerifier.withVirtualTime` chỉ điều khiển các scheduler Reactor mà test cấu hình; **không tự động điều khiển thời gian của Spring TaskScheduler**. Cần tránh viết test time-virtual cho Reactor rồi tuyên bố đã kiểm chứng annotation scheduling Spring.

### 12.2. Test các lỗi dễ bị bỏ sót

- **Outer Mono hoàn tất sớm:** tạo inner task chờ latch nhưng outer Mono chỉ submit rồi complete; xác minh fixed-delay đếm từ outer terminal, không từ inner finish.
- **Mono.defer:** xác minh `port.reconcilePending()` được gọi một lần **mỗi subscription**, không chỉ một lần lúc tạo bean.
- **onNext:** Flux phát các giá trị nhưng chưa hoàn tất; lịch fixed-delay không bắt đầu lần hai.
- **onError:** Spring log WARN rồi thử lần tiếp theo theo delay; kiểm tra error counter và không dựa vào `ErrorHandler` synchronous.
- **Never-ending Publisher:** `Mono.never()` không có lượt thứ hai khi không có cancel/timeout; test cleanup an toàn.
- **Timeout:** dùng `timeout(...)` để tạo terminal error, rồi kiểm tra lần sau vẫn được subscribe, nhưng remote action có thể cần cleanup riêng.
- **Multiple scheduled declarations:** cùng method có thể có nhiều lịch độc lập; khi chúng chạy đồng thời, cần định nghĩa concurrency semantics riêng.
- **Multiple instances:** test idempotency và điều phối nếu một job không được phép chạy trùng trên cụm.
- **Shutdown:** dừng context khi subscription còn active, kiểm tra cancellation/cleanup, không hứa remote side effect đã được undo.

### 12.3. Tín hiệu cần assert trong integration test

Nên ghi một `runId` hoặc counter per subscription, cùng ba timestamp:

~~~text
subscriptionStartedAt
subscriptionTerminatedAt
nextSubscriptionStartedAt
~~~

Sau đó kiểm tra:

~~~text
nextSubscriptionStartedAt >= previousSubscriptionTerminatedAt + fixedDelay
~~~

Đây là **quan hệ kỳ vọng về thứ tự**, không phải cú pháp Java. Đo bằng `System.nanoTime()`/monotonic clock để tránh thay đổi giờ hệ thống và có tolerance hợp lý khi scheduler bị trễ. Không dùng `lastCompletion` của một callback chỉ submit child để thay kết quả nghiệp vụ.

## 13. Những hiểu nhầm phổ biến

**“Reactive fixed-delay chỉ đợi subscribe() trả về.”** Sai. Spring cố ý chặn Runnable cho đến khi subscription phát terminal signal để fixed-delay tính từ completion.

**“Phương thức @Scheduled trả Mono mỗi chu kỳ.”** Không phải theo hợp đồng reactive scheduling chuẩn Spring 6.1+. Framework lấy Publisher một lần và subscribe lại nhiều lần; dùng deferred Publisher để mỗi lần thực hiện công việc mới.

**“Mono.just(expensiveReadNow()) sẽ gọi expensiveReadNow mỗi 5 giây.”** Sai trong trường hợp giá trị eager được tính lúc tạo Publisher. Dùng `Mono.fromCallable` hoặc `Mono.defer` phù hợp.

**“Nhận onNext nghĩa là xong một chu kỳ.”** Sai. Spring bỏ qua onNext; cần `onComplete` hoặc `onError`.

**“@Scheduled fixedDelay + .subscribe() thủ công bảo đảm chờ child.”** Sai. Child có thể còn chạy khi outer Publisher terminal.

**“Một publisher không terminal vẫn chạy fixed-delay đều đều.”** Sai. Vì không có completion, lần tiếp theo không được tính từ một mốc hoàn tất.

**“Reactive onError tự dừng lịch và đi vào TaskScheduler ErrorHandler.”** Sai. Spring log WARN, khôi phục để lịch tiếp tục; ErrorHandler synchronous không dùng cho onError.

**“fixedRate và fixedDelay chỉ khác tên.”** Sai. Fixed-rate dựa trên mốc chu kỳ, có thể overlap các subscription; fixed-delay đếm từ terminal của subscription trước.

**“Spring fixed-delay chờ Publisher nên mọi I/O chạy blocking.”** Sai. Scheduler Runnable chờ terminal; reactive I/O vẫn có thể non-blocking ở những thread phù hợp.

**“Tăng scheduler pool size sửa được Mono.never().”** Không. Nguồn không terminal vẫn kẹt; cần chính sách completion/timeout/cancel.

**“CompletableFuture là kiểu trả về @Scheduled reactive hợp lệ vì convert sang Publisher được.”** Không mặc định. Adapter của CompletableFuture không có tính deferred, nên không đạt điều kiện của reactive scheduling.

**“Cancel subscription đã dừng mọi giao dịch remote.”** Không có bảo đảm như vậy. Cần timeout/cancellation protocol của client và bảo vệ side effect bằng idempotency/compensation.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao Spring không chỉ gọi `subscribe()` rồi return ngay với reactive `fixedDelay`?

**Đáp án:** vì `subscribe()` thường trả sớm, trong khi Publisher còn chạy. Nếu return ngay, delay tính từ dispatch chứ không phải completion. Spring phải đợi terminal signal trước khi tính delay.

**Câu 2.** Với `fixedDelay=5s` và subscription trước chạy 8 giây, lượt tiếp thường sớm nhất khoảng thời điểm nào?

**Đáp án:** khoảng 13 giây sau lần trước bắt đầu (8 giây xử lý + 5 giây delay), chưa tính overhead và scheduler lateness.

**Câu 3.** `onNext("done")` có khiến Spring fixed-delay bắt đầu đếm delay không?

**Đáp án:** không. `onNext` được bỏ qua; phải chờ `onComplete` hoặc `onError` của lượt subscription.

**Câu 4.** `@Scheduled(fixedDelay=5000) public Mono<Void> work()` được Spring gọi mấy lần để lấy Publisher?

**Đáp án:** theo reactive scheduling, lấy một Publisher một lần, sau đó mỗi lần trigger đến sẽ subscribe lại Publisher đó. Cần cold/deferred source để tạo công việc mới mỗi lượt.

**Câu 5.** Vì sao `Mono.just(expensiveReadNow())` thường sai cho lịch lặp?

**Đáp án:** tham số tính ngay khi tạo Mono, có thể chỉ chạy ở lần Spring lấy Publisher. Dùng `Mono.defer`/`fromCallable` để thực thi trên từng subscription.

**Câu 6.** `Mono.never()` có phù hợp một reactive fixed-delay task hữu hạn không?

**Đáp án:** không. Nó không terminal nên scheduler chờ, không có căn cứ tính delay; cần thiết kế completion/timeout/cancel.

**Câu 7.** `onError` trong Publisher có làm các lần lịch sau mặc nhiên dừng không?

**Đáp án:** không. Spring log WARN và recover để subscribe các lần sau; synchronous ErrorHandler không xử lý các onError này.

**Câu 8.** Nếu code gọi `client.refresh().subscribe()` trong `Mono.fromRunnable` rồi return, fixed-delay đang đợi cái gì?

**Đáp án:** đợi Mono ngoài hoàn tất, thường là lúc lời subscribe lồng nhau trả về; không đợi client.refresh() thực sự kết thúc.

**Câu 9.** Dùng `timeout(Duration)` trên Publisher có bảo đảm rollback request ngoài JVM khi hết hạn không?

**Đáp án:** không. Reactor timeout tạo terminal error/cancel upstream, nhưng việc ngừng I/O và side effect phụ thuộc client/remote; cần timeout và idempotency cụ thể.

**Câu 10.** Vì sao `StepVerifier.withVirtualTime` chưa đủ để chứng minh `@Scheduled` fixed-delay đúng?

**Đáp án:** nó điều khiển virtual time của Reactor scheduler được cấu hình, không tự thay thời gian của Spring TaskScheduler. Cần integration test Spring scheduling riêng.

**Câu 11.** Nếu muốn có báo cáo hoàn tất của worker con, nên nối kết quả thế nào?

**Đáp án:** compose worker/client vào Publisher chính (ví dụ `Mono.defer(() -> client.refresh()).then()`) hoặc dùng job status/ACK riêng; không tự tạo inner subscription bị tách khỏi outer.

## 15. Ghi nhớ và nguồn chính thức

- **Mốc fixed-delay reactive là terminal của subscription, không phải return của subscribe().**
- **Spring lấy Publisher một lần, subscribe lại theo lịch; Publisher phải repeatably subscribable và nên deferred.**
- **Với fixed-delay, Spring chờ onComplete/onError, chiếm scheduler thread trong lúc chờ.**
- **onNext bị bỏ qua; onError được ghi WARN và lịch tiếp tục.**
- **Không dùng nested subscribe() làm fire-and-forget nếu cần fixed-delay theo completion thật.**
- **Publisher có thể không terminal: cần timeout/deadline và lifecycle hợp lý.**
- **Fixed-rate/cron không có quy tắc chờ terminal như fixed-delay; có thể overlap.**
- **Kết quả bên ngoài Publisher và side effect remote cần contract/ACK/job status riêng.**

Tài liệu kiểm chứng:

1. Spring Framework Reference — Task Execution and Scheduling, mục “The `@Scheduled` annotation on Reactive methods or Kotlin suspending functions”: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
2. Spring Framework 6.2 Javadoc — `@Scheduled` (đặc biệt ghi chú reactive fixed-delay chờ subscription): https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html
3. Spring Framework source — `ScheduledAnnotationReactiveSupport`, `SubscribingRunnable` và subscriber terminal handling: https://github.com/spring-projects/spring-framework/blob/main/spring-context/src/main/java/org/springframework/scheduling/annotation/ScheduledAnnotationReactiveSupport.java
4. Spring Framework 6.2 API — `ThreadPoolTaskScheduler`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html
5. Reactive Streams specification: https://www.reactive-streams.org/
6. Reactor reference guide — `Mono.defer`, `timeout`, retry và testing: https://projectreactor.io/docs/core/release/reference/
7. Java SE 17 — `ScheduledExecutorService.scheduleWithFixedDelay`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-scheduling-008">Hủy các đăng ký nhận khi bean bị hủy hoặc context đóng</a>

<details>
<summary>Xem chi tiết</summary>

# Hủy các đăng ký nhận khi bean bị hủy hoặc context đóng

Với **Spring Framework 6.1+**, phương thức `@Scheduled` không chỉ hỗ trợ xử lý đồng bộ `void` mà còn có thể trả về một **reactive `Publisher`** (ví dụ Reactor `Mono`/`Flux`) hoặc một kiểu được adapter hỗ trợ theo hợp đồng deferred subscription. Spring lấy `Publisher` từ phương thức annotated **một lần**, sau đó tạo các **subscription mới theo từng mốc `fixedRate`, `fixedDelay` hoặc `cron`**. Một subscription có thể kết thúc nhanh, kéo dài hàng phút hoặc thậm chí không bao giờ hoàn tất.

Điểm cần hiểu trong bài **“Hủy các đăng ký nhận khi bean bị hủy hoặc context đóng”** thuộc nhóm **Lập lịch reactive** của module **Spring Framework – concurrency** là: **Spring quản lý cả lịch và những subscription đang sống**. Khi bean được hủy hoặc ApplicationContext đóng, hạ tầng Spring hủy các lần đăng ký theo lịch **trong tương lai**, đồng thời **cancel những subscription đã được khởi tạo mà vẫn đang hoạt động**. Đây là cam kết của Spring về quản lý vòng đời reactive scheduling; **không phải cam kết rằng mọi side effect, I/O, giao dịch hay worker bên ngoài lập tức bị dừng/rollback**.

Bài học phân biệt ba lớp dễ bị nhầm:

1. **Lịch đã được hủy:** scheduler không còn tạo những subscription mới từ đăng ký đó.
2. **Subscription đã nhận cancellation:** hệ thống reactive gửi tín hiệu `cancel()`/dispose tới subscription hiện hữu.
3. **Công việc thực đã kết thúc an toàn:** nguồn reactive và những thao tác bên dưới hợp tác dừng, giải phóng tài nguyên và không tiếp tục side effect không mong muốn.

Ba lớp liên quan với nhau nhưng không đồng nhất. Muốn dùng reactive scheduling trong production, cần hiểu cơ chế cancel, viết publisher phù hợp với nhiều lần subscribe và kiểm thử cả shutdown lẫn tác động nghiệp vụ.

## 1. Sơ đồ vòng đời: một Publisher, nhiều subscription

Giả sử bean có method:

```java
@Scheduled(fixedRate = 5_000)
public Flux<String> poll() {
    return Flux.defer(() -> findNewEvents());
}
```

Đây là fragment có dependencies Reactor/Spring; chưa phải chương trình độc lập. Khi framework đăng ký lịch cho bean, Spring lấy publisher **một lần**, rồi mỗi lần tick sẽ subscribe publisher đó. Với một publisher **cold/deferred**, công việc thực xảy ra trong mỗi subscription, không phải lúc tạo method.

```text
ApplicationContext refresh
        |
        v
Bean đã được tạo; @EnableScheduling xử lý @Scheduled
        |
        v
Gọi phương thức poll() để lấy Publisher P   [một lần]
        |
        v
Scheduler đăng ký periodic Runnable
        |
        +---- tick 1: P.subscribe() --> Subscription S1 --> đang chạy
        |
        +---- tick 2: P.subscribe() --> Subscription S2 --> đang chạy
        |
        +---- tick 3: P.subscribe() --> Subscription S3 --> hoàn tất
        |
        ...
        |
        +---- Bean destroy / ApplicationContext.close()
                        |
                        +--> cancel periodic schedule
                        |      (không tick mới sau khi shutdown)
                        |
                        +--> cancel subscription S1 nếu còn active
                        +--> cancel subscription S2 nếu còn active
                        +--> S3 đã terminal: không cần cancel lại
```

**Không phải Spring gọi lại method `poll()` mỗi tick rồi lấy một publisher mới.** Nó lấy publisher **một lần** trong quá trình xử lý annotation, rồi subscribe lặp lại publisher đó. Do vậy, code phải tạo một publisher **có khả năng nhiều lần subscribe và thực hiện lại công việc tại thời điểm subscribe**.

### 1.1. Ba thực thể cần phân biệt

| Thực thể | Vai trò | Vòng đời |
| --- | --- | --- |
| Spring bean chứa `@Scheduled` | Sở hữu phương thức được đăng ký | Tạo/hủy theo ApplicationContext/bean scope |
| Scheduled task / `ScheduledFuture` phía hạ tầng | Quyết định **khi nào** thực hiện subscribe | Đăng ký theo cron/fixed-rate/fixed-delay, hủy khi scheduler/bean bị hủy |
| Reactive `Subscription` | Liên kết một subscriber với publisher, truyền `request(n)`/`cancel()` | Có thể hoàn tất, lỗi hoặc bị hủy; một lịch có thể từng tạo nhiều subscription |

Một số triển khai dùng đối tượng `Disposable` của Reactor để biểu diễn quyền dispose. Về mục đích, `dispose()` thường hủy subscription ở tầng reactive; không được hiểu là lệnh cưỡng bức đóng mọi tài nguyên bên ngoài.

### 1.2. Hủy lịch và hủy subscription khác nhau

Một scheduler có thể **không tạo thêm tick** nhưng vẫn còn subscription cũ đang xử lý. Ví dụ `Mono.delay(Duration.ofMinutes(2))` được subscribe lúc 10:00, context đóng lúc 10:01; không đủ nếu chỉ bỏ tick 10:05, bởi timer của subscription 10:00 vẫn đang đợi. Spring 6.1+ xử lý cả **active subscription**, theo tài liệu chính thức.

Ngược lại, một subscription có thể đã hoàn tất bình thường trong khi lịch vẫn được đăng ký và sẽ subscribe lại lần sau. Kết quả “đã hoàn thành một lượt” không đồng nghĩa “đã hủy lịch”.

## 2. Phiên bản nào hỗ trợ reactive @Scheduled?

Từ **Spring Framework 6.1**, `@Scheduled` hỗ trợ:

- Phương thức trả về `org.reactivestreams.Publisher` hoặc implementation như Reactor `Mono<T>` và `Flux<T>`.
- Một số kiểu có thể chuyển sang `Publisher` bằng `ReactiveAdapterRegistry` **nếu adapter có deferred subscription**.
- Kotlin suspending functions khi có bridge `kotlinx.coroutines.reactor`, cùng một số kiểu Kotlin reactive liên quan.

Tất cả method dạng này **không nhận tham số**, giống contract thông thường của `@Scheduled`.

### 2.1. Không phải Future nào cũng phù hợp

`CompletableFuture` có thể được adapter chuyển sang `Publisher` trong vài ngữ cảnh, nhưng descriptor của adapter dành cho `CompletableFuture` không có thuộc tính `isDeferred() == true`. Do đó, đừng lấy một method:

```java
@Scheduled(fixedRate = 5000)
public CompletableFuture<Void> run() {
    return CompletableFuture.runAsync(this::work);
}
```

làm mẫu reactive scheduling hợp lệ và suy luận Spring sẽ tạo work mới cho từng tick. Đây là **phản ví dụ**: API `CompletableFuture` không cung cấp deferred repeated subscription theo contract nói trên. Hãy dùng kiểu reactive được hỗ trợ, ví dụ `Mono.defer(...)`, hoặc dùng `@Scheduled void`/`TaskScheduler` và quản lý Future theo thiết kế rõ ràng.

### 2.2. Cần bean và @EnableScheduling thật sự

`@Scheduled` trên một object tạo bằng `new` ngoài Spring sẽ không được scheduling processor phát hiện. Một ứng dụng Boot cần:

```java
@SpringBootApplication
@EnableScheduling
public class Application { /* main và bean configuration */ }
```

và method scheduled phải nằm trong **bean do context đó quản lý**, được tạo và xử lý. `@EnableAsync` không thay `@EnableScheduling`. Khi bean không được scan, bị `@Profile`/conditional loại hoặc chưa khởi tạo vì lazy, không có lịch reactive tương ứng để hủy.

**Phạm vi quản lý:** Spring hủy các scheduled tasks **do infrastructure/container của nó đăng ký**, không tự hủy những `subscribe()` hay `ScheduledExecutorService` do code tự tạo ở ngoài quyền quản lý.

## 3. Một Publisher, nhiều subscription cần owner rõ ràng

Spring reactive `@Scheduled` giữ Publisher của method khi đăng ký và subscribe lại nó theo mỗi tick. Nguồn cold `Mono.defer` cho phép tạo một operation riêng cho mỗi subscription; không gọi `subscribe()` thêm nguồn khác nếu không có component sở hữu handle để dispose.

~~~text
Spring bean -> Publisher P (tạo một lần)
        tick 1 -> Subscription S1 (active)
        tick 2 -> Subscription S2 (active)
        tick 3 -> Subscription S3 (completed)
context.close() -> cancel schedule + S1/S2 còn active
~~~

Bài này tập trung vào **hủy lịch tương lai và active subscriptions khi bean/context đóng**. Cancellation không tự rollback side effect remote hoặc đảm bảo JDBC/HTTP worker đã dừng; phải kiểm tra theo từng resource và triển khai cleanup/recovery riêng.

## 4. Cancellation ở tầng Reactive Streams nghĩa là gì?

Reactive Streams dùng `Subscription` để điều khiển demand và cancellation:

```java
public interface Subscription {
    void request(long n);
    void cancel();
}
```

Đây là chữ ký đơn giản hóa để giải thích, không phải file interface cần tạo trong ứng dụng. `cancel()` báo rằng subscriber **không còn muốn nhận thêm signals/data** từ subscription. Nguồn publisher cần hợp tác dừng việc phát và giải phóng tài nguyên phù hợp; cancellation có thể đồng bộ hoặc bất đồng bộ tùy operator/source.

Ba điều **không** được đảm bảo chỉ bởi lời gọi cancel:

1. Một HTTP request đã gửi đi không chắc bị rút lại khỏi server bên kia.
2. Một database transaction đã commit không tự rollback.
3. Một tác vụ blocking/CPU-bound đang chạy không chắc nhận interrupt hoặc dừng tức thì.

Ngay cả khi Reactor `doFinally(SignalType.CANCEL)` ghi lại sự kiện cancel, điều đó chủ yếu chứng minh **subscription đã nhận đường kết thúc bằng cancel** ở điểm operator đang quan sát. Nó **không** chứng minh một side effect ngoài JVM được đảo ngược.

### 4.1. Phân biệt complete, error và cancel

| Terminal path | Ý nghĩa ở subscription | Có phải business success? |
| --- | --- | --- |
| `onComplete` | Publisher hoàn tất bình thường | Chỉ nếu logic và commit nghiệp vụ đã thực sự thành công |
| `onError` | Subscription nhận lỗi | Có thể đã tạo side effect trước khi lỗi |
| `cancel` | Subscriber không muốn tiếp tục nhận signals; chain được dispose/cancel | Không; cancellation không đồng nghĩa rollback |
| Chưa terminal | Subscription còn sống | Không; có thể đang chờ timer, I/O, backpressure hoặc đang treo |

Không nên đánh đồng `Mono<Void>` hoàn tất với **một bản ghi đã durable** nếu code không thực sự await/verify thao tác lưu.

### 4.2. doOnCancel và doFinally dùng khi nào?

Trong Reactor:

```java
Mono<Void> work = Mono.delay(Duration.ofSeconds(30))
        .then()
        .doOnCancel(() -> log.info("Subscription cancelled"))
        .doFinally(signal ->
                log.info("Subscription terminated: {}", signal));
```

Đây là fragment, cần Reactor, `Duration` và logger. `doOnCancel` đặc biệt nhận đường cancel; `doFinally` được gọi khi sequence hoàn thành, lỗi hoặc cancel với `SignalType` như `ON_COMPLETE`, `ON_ERROR`, `CANCEL`. Chúng hữu ích để log/metric/cleanup scope.

**Cần chú ý:** các callback cuối cũng có thể chạy trên thread thực hiện cancellation hoặc thread của nguồn, không nên gán nghiệp vụ lớn/chặn I/O vào đó. Nếu cleanup là bất đồng bộ và cần kết thúc chắc chắn, hãy thiết kế resource lifecycle/`usingWhen` phù hợp thay vì “fire-and-forget” trong `doFinally`.

## 5. Đúng thời điểm hủy: bean destroy và ApplicationContext.close()

Spring Framework Reference mô tả rõ: khi **bean annotated bị destroy** hoặc **ApplicationContext đóng**, Spring hủy các scheduled tasks, bao gồm **subscription kế tiếp chưa xảy ra** và **những subscription đã tồn tại nhưng vẫn active**.

Hai loại cần xử lý độc lập:

```text
T0: bean được tạo, Spring lấy Publisher và đăng ký schedule
T1: tick 1 -> subscribe S1 --------------> chạy lâu
T2: tick 2 -> subscribe S2 ------> complete
T3: tick 3 -> subscribe S3 --------------------> chạy lâu
T4: context.close()
    |
    +-- không còn tick 4/tick 5 ... từ lịch này
    +-- cancel S1 còn active
    +-- S2 đã complete: không cần cancel lại
    +-- cancel S3 còn active
    +-- cleanup các scheduler/resources managed theo lifecycle
```

**Không nên thiết kế dựa trên thứ tự tuyệt đối** giữa `@PreDestroy` của từng bean, sự kiện context close, việc cancel subscription và shutdown của một executor khác. Những callback nội bộ có trình tự phụ thuộc bean dependencies, phase/lifecycle và implementation. Điều an toàn là gắn cleanup vào resource scope/`doFinally` hoặc một owner rõ ràng, rồi kiểm thử stop sequence thực tế.

### 5.1. Bean destroy không giống request timeout

- Client HTTP disconnect có thể không ảnh hưởng scheduled bean đang sống.
- Một request timeout không đồng nghĩa Spring tự destroy bean chứa `@Scheduled`.
- Context close/destroy là sự kiện quản lý **vòng đời bean/scheduling**.
- Một subscription hoàn tất trước destroy không bị “hủy lại”.
- Nếu một code khác tự tạo subscription ngoài scheduling processor, nó phải tự quản lý handle; Spring không tự suy ra cần hủy subscription lạ.

### 5.2. Hủy bean scope đặc biệt

Nếu scheduled bean là singleton, destroy thường diễn ra lúc context shutdown. Nếu dùng scope đặc biệt hoặc tạo nhiều bean instances được processor xử lý, mỗi instance có thể có những registration riêng.

Đừng tạo nhiều instance scheduled bean vô ý rồi kỳ vọng chỉ một schedule. `@Scheduled` là repeatable annotation; cùng một method có nhiều trigger cũng có thể tạo nhiều subscription flows độc lập. Từng registration phải được theo dõi theo owner/lifecycle.

### 5.3. @PreDestroy có cần gọi cancel thủ công?

Với **reactive `@Scheduled` đã được Spring quản lý**, thường **không cần** tự giữ `Disposable` của những subscription Spring tạo chỉ để hủy lúc shutdown; framework có trách nhiệm hủy theo hợp đồng. Việc thêm `@PreDestroy` có thể dùng để dọn **tài nguyên riêng của bean** nhưng không nên làm một subscription thứ hai theo kiểu thủ công mà không sở hữu handle.

Nếu code có:

```java
Disposable manual = publisher.subscribe();
```

tự gọi trong `@PostConstruct`, subscription `manual` **không mặc nhiên trở thành subscription do `@Scheduled` quản lý**. Bean của bạn cần hủy `manual.dispose()` trong lifecycle của nó (hoặc chuyển quyền sở hữu cho một component có lifecycle). Tránh tạo subscription bằng tay bên trong scheduled method chỉ để “kích hoạt nó”; Spring đã subscribe publisher được return.

## 6. Fixed rate, fixed delay và active subscriptions

Trong reactive `@Scheduled`, các mốc tick là thời điểm **subscribe**, không phải lời gọi lại method tạo publisher.

### 6.1. fixedRate/cron có thể có nhiều subscription còn active

Ví dụ:

```java
@Scheduled(fixedRate = 1, timeUnit = TimeUnit.SECONDS)
public Mono<Void> slowJob() {
    return Mono.defer(() -> Mono.delay(Duration.ofSeconds(8)).then());
}
```

Đây là fragment. Mỗi giây, Spring có thể subscribe vào publisher cold. Vì mỗi subscription cần khoảng 8 giây để hoàn tất, trong trạng thái ổn định có thể có **nhiều subscription cùng đang hoạt động**. Khi bean bị destroy/context đóng, Spring phải hủy **những subscription còn active**, không chỉ hủy tick mới.

Không suy luận “scheduler pool size = 1 thì chỉ có một subscription”: scheduler worker có thể **gửi lệnh subscribe rất nhanh**, còn công việc reactive được thực thi trên event-loop/timer/worker khác. Với publisher không blocking, một thread scheduler vẫn có thể kích hoạt nhiều operations đang in flight.

### 6.2. fixedDelay phải tôn trọng thời điểm hoàn tất subscription

Javadoc `@Scheduled` nêu riêng: khi cấu hình **fixed delay** cho reactive method, hạ tầng sẽ **block để chờ subscription hoàn tất**, nhằm giữ ngữ nghĩa “đợi sau khi lượt trước kết thúc mới tính delay”.

```java
@Scheduled(fixedDelay = 2, timeUnit = TimeUnit.SECONDS)
public Mono<Void> slowJob() {
    return Mono.defer(() ->
            Mono.delay(Duration.ofSeconds(5)).then());
}
```

Một lượt hoàn tất sau khoảng 5 giây, rồi mới bắt đầu khoảng chờ 2 giây trước lượt tiếp theo; không phải cứ 2 giây lại tạo subscription mới bất kể lượt cũ. Việc chờ hoàn tất có thể **giữ scheduler thread**, nên với reactive publisher không bao giờ complete, fixed-delay scheduling có nguy cơ giữ thread rất lâu.

**Giới hạn của cancel:** khi context đóng, Spring hủy những active subscriptions; nhưng nếu publisher có blocking call **không hợp tác với cancellation** thì thread/resource thực vẫn có thể mắc kẹt. Vì vậy không coi `fixedDelay + Mono.never()` là giải pháp chờ không giới hạn cho production.

### 6.3. Chọn theo nghiệp vụ, không chỉ tối ưu latency

| Cấu hình | Đặc điểm | Rủi ro cần kiểm tra |
| --- | --- | --- |
| `fixedRate` | Subscribe theo cadence dự kiến | Multiple in-flight subscriptions; trùng side effect |
| `cron` | Subscribe theo lịch calendar/zone | DST, lag, multiple active tasks, cluster duplication |
| `fixedDelay` | Chờ subscription trước terminal rồi mới đếm delay | Scheduler thread có thể bị giữ; publisher không complete |
| TaskScheduler API chủ động | Tự quản lý Runnable/trigger | Phải quản lý thêm subscription ownership nếu tự subscribe |
| External durable job system | Lưu và xử lý job qua store/broker | Idempotency/retry/lease/ownership riêng |

Khi production cần **không được chồng lấp nghiệp vụ**, chưa đủ để chọn `fixedDelay` nếu code thực tế chỉ trả `Mono.empty()` ngay sau khi gửi một async request khác hoặc tự tạo detached subscription. Publisher được return phải **đại diện đúng completion của nghiệp vụ**, không phải chỉ “đã giao việc”.

## 7. Java 17 runnable: hủy tất cả subscription còn active

Chương trình sau là **mô hình học tập thuần JDK 17**, không dùng Spring/Reactor. Nó dùng `java.util.concurrent.Flow` — API Reactive Streams-like trong JDK — để mô phỏng một owner giữ danh sách subscription và hủy khi owner đóng. Spring thực tế dùng giao thức `org.reactivestreams.Publisher` và hạ tầng khác; demo chỉ minh họa **nguyên tắc ownership/cancel**, **không phải code mô phỏng nội bộ Spring**.

Lưu file **SubscriptionOwnerDemo.java**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SubscriptionOwnerDemo {
    static final class NeverPublisher implements Flow.Publisher<String> {
        final AtomicInteger cancels = new AtomicInteger();

        @Override
        public void subscribe(Flow.Subscriber<? super String> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                final AtomicBoolean cancelled = new AtomicBoolean();

                @Override
                public void request(long n) {
                    // Demo không phát dữ liệu hay hoàn tất.
                }

                @Override
                public void cancel() {
                    if (cancelled.compareAndSet(false, true)) {
                        cancels.incrementAndGet();
                    }
                }
            });
        }
    }

    static final class Owner implements AutoCloseable {
        private final List<Flow.Subscription> active = new ArrayList<>();
        private boolean closed;

        Flow.Subscriber<String> newSubscriber() {
            return new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    if (closed) {
                        subscription.cancel();
                    } else {
                        active.add(subscription);
                        subscription.request(1);
                    }
                }

                @Override public void onNext(String value) {}
                @Override public void onError(Throwable error) {}
                @Override public void onComplete() {}
            };
        }

        int activeCount() {
            return active.size();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (Flow.Subscription subscription : active) {
                subscription.cancel();
            }
            active.clear();
        }
    }

    public static void main(String[] args) {
        NeverPublisher source = new NeverPublisher();
        Owner owner = new Owner();

        source.subscribe(owner.newSubscriber());
        source.subscribe(owner.newSubscriber());

        System.out.println("beforeClose: active=" + owner.activeCount()
                + ", cancels=" + source.cancels.get());

        owner.close();

        System.out.println("afterClose: active=" + owner.activeCount()
                + ", cancels=" + source.cancels.get());

        owner.close(); // Idempotent: không hủy lặp lại.

        System.out.println("closeAgain: active=" + owner.activeCount()
                + ", cancels=" + source.cancels.get());
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac SubscriptionOwnerDemo.java
java SubscriptionOwnerDemo
```

**Stdout xác định:**

```text
beforeClose: active=2, cancels=0
afterClose: active=0, cancels=2
closeAgain: active=0, cancels=2
```

Vì tất cả thao tác `subscribe`/`close` ở ví dụ diễn ra **đồng bộ trên main thread**, output có thứ tự xác định mà không cần `Thread.sleep`.

Những điểm học được:

- Cùng một publisher được subscribe **hai lần** nên có hai subscription độc lập.
- Khi owner đóng, nó phải hủy **cả hai** active subscriptions, không chỉ ngăn subscriber thứ ba.
- Một owner tốt nên đóng idempotent; `close()` gọi lần hai không làm tăng `cancels`.
- `cancel()` là yêu cầu kết thúc quan hệ subscription; demo không có I/O nên **không chứng minh việc hủy DB/HTTP/worker thật**.
- API `Flow.Publisher` của JDK **không phải** `org.reactivestreams.Publisher` mà Spring reactive `@Scheduled` yêu cầu; ví dụ chỉ là phép so sánh cơ chế.

Trong production, khi subscription có thể complete/error bất đồng bộ, registry phải **xóa những subscription đã terminal, có concurrency control và không giữ tham chiếu vô hạn**. Demo đơn luồng cố ý tối giản để tập trung vào signal cancellation.

## 8. Spring Boot 3.x + Reactor: quan sát subscription mới và cancellation lúc shutdown

Ví dụ này dùng **Spring Boot 3.x chạy Spring Framework 6.1+**, **Java 17+**, dependency `spring-boot-starter-webflux` và `reactor-core` từ starter. Ứng dụng có một scheduled bean tạo công việc mô phỏng kéo dài 8 giây bằng `Mono.delay`, trong khi `fixedRate` là 2 giây.

Mục tiêu:

- Method `@Scheduled` **trả một `Mono.defer`** có thể subscribe nhiều lần.
- Mỗi subscription tăng bộ đếm riêng.
- Subscription hoàn tất → tăng `completed`.
- Subscription bị cancel → tăng `cancelled`.
- Khi context đóng, Spring hủy periodic registration và các subscription vẫn active; `doFinally(SignalType.CANCEL)` có thể ghi log quan sát.
- Endpoint `GET /reactive/status` hiển thị snapshot trong lúc app đang chạy.

### 8.1. File Spring Boot hoàn chỉnh

Đặt code vào **src/main/java/com/example/demo/ReactiveShutdownApplication.java**:

```java
package com.example.demo;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

@SpringBootApplication
@EnableScheduling
public class ReactiveShutdownApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReactiveShutdownApplication.class, args);
    }

    @Bean("taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler pool = new ThreadPoolTaskScheduler();
        pool.setPoolSize(1);
        pool.setThreadNamePrefix("scheduled-");
        return pool;
    }
}

@Component
class ReactivePollingJob {
    private static final Logger log =
            LoggerFactory.getLogger(ReactivePollingJob.class);

    private final AtomicInteger subscribed = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger cancelled = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    @Scheduled(fixedRate = 2, timeUnit = TimeUnit.SECONDS)
    public Mono<Void> poll() {
        // Spring gọi method để lấy publisher MỘT LẦN.
        return Mono.defer(() -> {
            int attempt = subscribed.incrementAndGet();
            log.info("Subscribed job attempt {}", attempt);

            // Mô phỏng work asynchronous, không block scheduler.
            return Mono.delay(Duration.ofSeconds(8))
                    .then()
                    .doFinally(signal -> {
                        if (signal == SignalType.ON_COMPLETE) {
                            completed.incrementAndGet();
                        } else if (signal == SignalType.ON_ERROR) {
                            failed.incrementAndGet();
                        } else if (signal == SignalType.CANCEL) {
                            cancelled.incrementAndGet();
                        }
                        log.info("Job attempt {} finished: {}",
                                attempt, signal);
                    });
        });
    }

    Map<String, Integer> snapshot() {
        return Map.of(
                "subscribed", subscribed.get(),
                "completed", completed.get(),
                "cancelled", cancelled.get(),
                "failed", failed.get());
    }
}

@RestController
class ReactiveStatusController {
    private final ReactivePollingJob job;

    ReactiveStatusController(ReactivePollingJob job) {
        this.job = job;
    }

    @GetMapping("/reactive/status")
    public Map<String, Integer> status() {
        return job.snapshot();
    }
}
```

Đây là **một file Java hợp lệ**: một public class và các class package-private cùng package. Cần dependencies Spring Boot WebFlux/Reactive Streams/Reactor qua build tool, **không thể `javac` trực tiếp chỉ với JDK**.

Với project Gradle Boot có wrapper:

```bash
./gradlew bootRun
curl -s http://localhost:8080/reactive/status
```

Trên Windows dùng `gradlew.bat bootRun` và `curl.exe` nếu phù hợp.

Một snapshot minh họa khi các lần subscribe đầu tiên đã bắt đầu nhưng chưa hết 8 giây:

```json
{
  "subscribed": 3,
  "completed": 0,
  "cancelled": 0,
  "failed": 0
}
```

**Các con số không cố định**: tùy thời điểm endpoint được gọi, thread scheduling và tải. Nếu ứng dụng chạy hơn 8 giây, một số subscriptions có thể đã `ON_COMPLETE`. Vì fixed rate 2 giây và delay 8 giây, thường có **nhiều active subscriptions** cùng thời điểm dù scheduler pool chỉ có một worker.

### 8.2. Đóng ứng dụng và quan sát cancel

Khi ứng dụng nhận tín hiệu shutdown thông thường, ApplicationContext được đóng. Spring hủy scheduled task và các subscription đang hoạt động. Khi cancellation được chuyển qua chain, các log như sau **có thể xuất hiện**:

```text
INFO ... Subscribed job attempt 4
INFO ... Job attempt 2 finished: onComplete
INFO ... Job attempt 3 finished: cancel
INFO ... Job attempt 4 finished: cancel
```

Log trên là **ví dụ minh họa, không phải thứ tự/dữ liệu cố định**. Số active subscriptions tại thời điểm shutdown thay đổi; không bắt buộc có hai dòng cancel. Các tín hiệu `doFinally` có thể được gọi trên những thread khác nhau tùy cách shutdown/cancellation được chuyển vào reactor pipeline.

**Mục tiêu QA:** tìm bằng chứng **active subscription bị cancel**, không chỉ nhìn `ThreadPoolTaskScheduler` đã ngừng. Một API đọc counter sau khi server đã tắt không còn truy cập được; dùng log/test in-process thay vì cố gọi endpoint đã đóng.

### 8.3. Vì sao dùng Mono.delay thay cho Thread.sleep?

`Mono.delay(Duration.ofSeconds(8))` lập timer trong Reactor; nó **không giữ một scheduler worker bằng `Thread.sleep(8s)`** ở mỗi tick. Khi cancel, timer subscription có thể được dispose. Đây là trường hợp thích hợp để thấy cancellation hợp tác.

Nếu thay bằng:

```java
return Mono.fromCallable(() -> {
    Thread.sleep(8_000);
    callExternalService();
    return "ok";
}).then();
```

thì `fromCallable` chạy khi subscribe và **có thể block chính thread gọi subscribe** nếu chưa được offload. Thêm `subscribeOn(Schedulers.boundedElastic())` có thể di chuyển blocking code sang pool thích hợp, nhưng vẫn phải đặt timeout và xử lý interrupt/cancellation cho thao tác thật. **Cancel reactive chain không đảm bảo `callExternalService()` đã bị ngăn trước khi side effect được gửi.**

### 8.4. Trường hợp logic nghiệp vụ được giao ra ngoài

Nếu scheduled method trả:

```java
@Scheduled(fixedRate = 5000)
public Mono<Void> kickOff() {
    return Mono.fromRunnable(() -> {
        taskExecutor.execute(this::doBusinessWork);
    });
}
```

đây là fragment minh họa. Mono này hoàn tất ngay sau khi **submit task**; subscription có thể complete trước khi worker bắt đầu. Spring hủy subscription đã complete **không thể tự hủy** task detached đang chạy trong `taskExecutor`. Muốn shutdown quản lý đúng, cần sở hữu handle của task đó, quản lý executor lifecycle và/hoặc để publisher phản ánh **thời điểm hoàn thành thật** của nghiệp vụ.

**Không đánh đồng “publisher complete” với “job hoàn thành”** nếu pipeline chỉ giao việc sang thành phần khác.

## 9. Integration test: đóng context hủy active reactive subscription

Kiểm thử tốt nhất cho chủ đề này phải xác nhận **đã subscribe trước** và **cancel xuất hiện sau context.close()**. Dùng `CountDownLatch` thay vì ngủ ngẫu nhiên.

Ví dụ sau dùng JUnit Jupiter, Spring Test/Spring Context và Reactor (có thể lấy qua `spring-boot-starter-test` cùng `spring-boot-starter-webflux`), áp dụng Spring Framework 6.1+.

Đặt file **src/test/java/com/example/demo/ReactiveScheduledShutdownTest.java**:

```java
package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import reactor.core.publisher.Mono;

class ReactiveScheduledShutdownTest {
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class TestConfig {
        @Bean
        ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            return scheduler;
        }

        @Bean
        ProbeJob probeJob() {
            return new ProbeJob();
        }
    }

    static class ProbeJob {
        final CountDownLatch subscribed = new CountDownLatch(1);
        final CountDownLatch cancelled = new CountDownLatch(1);

        @Scheduled(fixedRate = 100,
                   timeUnit = TimeUnit.MILLISECONDS)
        public Mono<Void> tick() {
            // Never completes => khi context đóng nó còn active.
            return Mono.<Void>never()
                    .doOnSubscribe(subscription ->
                            subscribed.countDown())
                    .doOnCancel(cancelled::countDown);
        }
    }

    @Test
    void closingContextCancelsActiveReactiveSubscription()
            throws Exception {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(
                        TestConfig.class);
        ProbeJob job = context.getBean(ProbeJob.class);

        try {
            assertTrue(job.subscribed.await(
                    5, TimeUnit.SECONDS),
                    "Expected at least one scheduled subscription");
        } finally {
            context.close();
        }

        assertTrue(job.cancelled.await(
                5, TimeUnit.SECONDS),
                "Expected cancellation on context close");
    }
}
```

### 9.1. Vì sao test này tốt hơn Thread.sleep?

- `Mono.never()` tạo subscription **không tự complete**, nên Spring có active subscription để hủy.
- `subscribed` chỉ mở sau khi thật sự có một subscriber; test không suy từ “đã qua 100 ms chắc chạy”.
- `context.close()` thực hiện lifecycle shutdown của Spring, không phải `Disposable.dispose()` thủ công trong test.
- `cancelled` chỉ mở khi cancellation được truyền tới subscription.
- Nếu startup hoặc scheduling chậm quá ngưỡng 5 giây, test thất bại có thông báo; ngưỡng cần điều chỉnh trên CI rất chậm chứ không thay bằng vô hạn.
- `finally` bảo đảm context được đóng kể cả assertion đầu thất bại, tránh để scheduler thread sống sau test.
- Test khẳng định **ít nhất một active subscription nhận cancel**; không chứng minh mọi nguồn I/O khác trong production có thể hủy theo cùng cách.

**Không nên viết test** chỉ khẳng định “sau close không thấy log tick mới” — có thể do không có log, task chưa đến giờ hoặc process vừa thoát; nó không chứng minh active subscription được cancel.

### 9.2. Mở rộng kiểm thử nhiều active subscriptions

Với `fixedRate` nhanh và `Mono.never()`, có thể quan sát nhiều subscription đồng thời. Một test chuyên sâu có thể dùng `AtomicInteger` cho total subscriptions và cancels, chờ `CountDownLatch` đếm ít nhất N subscriptions, đóng context rồi assert **tất cả N đã nhận cancellation**. Phải kiểm soát race giữa tick và close, không chỉ đọc số tăng trong một thời điểm không đồng bộ rồi so sánh ngay mà không chờ hạ tầng cleanup.

Nếu yêu cầu chứng minh **không có subscription mới sau shutdown**, ghi lại count sau shutdown và chờ một cửa sổ thời gian có giới hạn để kiểm tra không tăng; hiểu rằng test thời gian là kiểm tra best effort và không thay thế API/lifecycle contract.

## 10. Nếu code tự gọi subscribe(), ai là chủ sở hữu?

Một quy tắc thiết kế nên áp dụng trong Reactor:

**Code nào tạo subscription bằng `subscribe()` thường phải biết ai sở hữu `Disposable`/`Subscription` để hủy khi không còn cần.**

Ví dụ ngoài cơ chế `@Scheduled`:

```java
Disposable disposable = source
        .doOnCancel(() -> log.info("cancelled"))
        .subscribe();

// Khi owner không còn sống:
disposable.dispose();
```

Đây là fragment. Nếu viết trong Spring bean singleton, có thể lưu handle và dispose trong `@PreDestroy`, với lưu ý thread safety nếu có nhiều subscription. Nếu tự tạo từng subscription khi scheduled tick, cần registry cho những subscription đó; **đừng nhầm chúng với các subscription Spring tự tạo từ publisher được return**.

### 10.1. Chống “double subscription”

Sai lầm thường gặp:

```java
@Scheduled(fixedRate = 5000)
public Mono<Void> work() {
    Mono<Void> pipeline = buildPipeline();
    pipeline.subscribe(); // Subscription ngoài sự quản lý của Spring.
    return pipeline;      // Spring còn subscribe thêm mỗi tick.
}
```

Đây là **phản ví dụ**. Method được gọi một lần lúc registration; subscription thủ công có thể chạy ngay và nằm ngoài vòng đời scheduled subscriptions, sau đó Spring subscribe lại cùng pipeline theo lịch. Hệ quả có thể là **double execution** và ownership khó theo dõi.

Đúng hơn:

```java
@Scheduled(fixedRate = 5000)
public Mono<Void> work() {
    return Mono.defer(this::buildPipeline);
}
```

Spring sẽ subscribe publisher theo lịch; không cần gọi `subscribe()` trong method.

### 10.2. Disposable.Composite và giới hạn áp dụng

Khi một component thực sự tự quản lý nhiều subscriptions, Reactor cung cấp các giải pháp như `Disposables.composite()` để chứa handle và dispose nhiều nguồn theo owner. Đây là **một lựa chọn cho manual subscriptions**, không phải bằng chứng rằng Spring reactive `@Scheduled` cần bạn làm thêm điều này để hủy active scheduled subscriptions.

Nếu dùng manual composite, phải thiết kế việc add/close đồng thời, cleanup sau terminal và không giữ reference vô hạn. Không giả định một `ConcurrentHashMap<UUID, Disposable>` có thể được bỏ mặc sau khi job complete.

## 11. Cancellation có tác động gì tới HTTP, database, message broker?

Một ví dụ reactive HTTP:

```java
Mono<String> response = webClient.get()
        .uri("https://api.example.test/status")
        .retrieve()
        .bodyToMono(String.class);
```

Đây là fragment. Khi subscription bị cancel, một reactive HTTP client có thể giải phóng/đóng kết nối, ngừng nhận response hoặc cố hủy request theo khả năng transport/library. Tuy nhiên, **server đích có thể đã nhận và xử lý request**. Không hứa rằng huỷ phía client khiến server undo cập nhật.

### 11.1. JDBC và blocking code

`Mono.fromCallable(() -> jdbcTemplate.update(...))` vẫn dùng JDBC blocking. Nếu chạy trên scheduler thread và không offload, nó có thể chặn. Nếu offload sang bounded elastic, cancellation có thể truyền tín hiệu cho task, nhưng **không bảo đảm driver dừng ngay** hoặc rollback các câu lệnh đã commit.

Giải pháp:
- Cấu hình timeout thật ở JDBC/DB/HTTP client.
- Thiết kế transaction boundary rõ.
- Sử dụng idempotency key cho thao tác có thể retry/cancel.
- Chỉ đánh dấu `CANCELLED` khi biết ranh giới nào đã được thực hiện.
- Có reconciliation/compensation nếu trạng thái ngoài hệ thống không chắc chắn.

### 11.2. R2DBC và reactive transactions

R2DBC có thể hỗ trợ cancellation ở một số operation, nhưng kết quả cụ thể phụ thuộc driver, transaction manager và lúc tín hiệu đến. Không được suy rằng hủy `Mono` luôn làm query và transaction lập tức rollback trong mọi race. Kiểm tra semantics của driver/provider và test trong môi trường tương tự production.

### 11.3. Broker publish và thao tác không idempotent

Nếu scheduled subscription đã gửi message vào Kafka/RabbitMQ hoặc thanh toán sang provider, `cancel()` sau đó không tự xóa message hay thu hồi payment. Có thể:
- Message đã được broker xác nhận.
- Caller chưa nhận được ack.
- Subscription bị hủy giữa chừng.
- Client retry gây publish lần thứ hai.

Vì vậy, trạng thái **subscription cancelled** và **business not executed** cần được biểu diễn riêng. Với việc không được trùng, dùng idempotency key/job state/outbox hoặc reconciliation phù hợp.

## 12. Khi Spring đóng context: shutdown an toàn cần những lớp nào?

Reactive scheduling cleanup là **một phần** của graceful shutdown, không phải toàn bộ chiến lược.

```text
Nhận yêu cầu shutdown
    |
    +--> Ngừng nhận công việc mới ở ingress / trigger
    |
    +--> Spring lifecycle: hủy scheduled registrations
    |       +--> không tạo thêm subscription mới theo lịch đó
    |       +--> cancel active reactive subscriptions
    |
    +--> Tài nguyên reactive cleanup nếu chain hợp tác
    |
    +--> Worker/executor/client DB/HTTP/broker dừng theo lifecycle riêng
    |
    +--> Ghi trạng thái job còn dang dở nếu có durable store
    |
    +--> Process kết thúc sau grace period theo nền tảng deploy
```

Một context đóng **không làm mọi thứ trong Kubernetes tự chờ vô hạn**. Container/pod thường có grace period; nếu nguồn blocking không hợp tác và vượt quá giới hạn, process có thể bị kill. Nếu job có tính chất quan trọng, cần checkpoint/progress bền vững và cơ chế phục hồi.

### 12.1. Đừng gửi side effect trong doFinally mà không có thiết kế

`doFinally(SignalType.CANCEL)` hữu ích để giải phóng resource in-memory hay ghi metric, nhưng nếu nó gửi một remote request “đánh dấu hủy” fire-and-forget, remote call đó cũng có thể bị cắt khi context shutdown. Một hành động bù/ghi trạng thái cần độ bền vững nên được xử lý bằng transaction/job store/shutdown coordination thích hợp.

### 12.2. Scheduled subscription không phải durable job

Một cron/fixedRate reactive `@Scheduled` chạy trong process là **lịch in-memory**; khi process crash đột ngột:
- Spring có thể **không có cơ hội** thực hiện bean destruction sạch.
- Không chắc có cancel signal/callback cleanup.
- Các tick bị bỏ lỡ không tự được replay.
- In-flight business operation có thể ở trạng thái chưa biết.

Nếu cần đảm bảo cuối cùng sẽ xử lý xong, dùng durable work queue/job store, idempotency, retry và reconciliation. Reactive cancellation chỉ quản lý vòng đời trong **shutdown đúng quy trình**, không thay thế recovery sau crash.

### 12.3. Đừng nhầm đóng HTTP request với đóng ApplicationContext

Một HTTP request có `timeout` hoặc client disconnect không làm `@Scheduled` bean tự bị destroy. Ngược lại, khi context close thì bean scheduled được hủy dù không có HTTP request nào. Phải phân biệt scope theo từng resource để tránh hủy nhầm job dùng chung vì một client không chờ nữa.

## 13. Exception reactive không đi theo ErrorHandler đồng bộ

Spring Reference nêu: nếu Publisher reactive được scheduled phát `onError`, hạ tầng **log ở WARN** và có thể tiếp tục lên lịch subscription những lần sau. `ErrorHandler` dùng cho exception của `Runnable` thường **không tham gia trực tiếp**, vì lỗi đến từ tín hiệu reactive bất đồng bộ chứ không phải exception ném ra khỏi Runnable.

Ví dụ:

```java
@Scheduled(fixedRate = 5000)
public Mono<Void> work() {
    return Mono.defer(() ->
            Mono.error(new IllegalStateException("Transient failure")));
}
```

Mỗi subscription có thể báo lỗi, nhưng lịch vẫn có thể tiếp tục subscribe theo period. Không khẳng định một `onError` tự dừng toàn bộ lịch.

### 13.1. Retry reactive phải có giới hạn

```java
return remoteCall()
        .retryWhen(Retry.backoff(3, Duration.ofMillis(200)))
        .then();
```

Đây là fragment. Cần `reactor.util.retry.Retry` và một `remoteCall()` có thể được subscribe lại đúng semantics. Khi cancel, một operator retry hợp tác sẽ không tiếp tục retry sau khi subscription bị dispose. Tuy vậy, **một remote call đã gửi có thể đã tạo side effect**; retry phải idempotent.

Không dùng `retry()` vô hạn cho payment/notification nếu không có idempotency. Một subscription được giữ sống bằng retry không giới hạn có thể tạo backlog và gây khó khăn shutdown.

### 13.2. onErrorResume không phải ghi nhận job thành công

`onErrorResume(ex -> Mono.empty())` chuyển failure thành completion ở tầng reactive. Điều đó có thể hợp lệ nếu lỗi đã được chấp nhận theo domain, nhưng không tự biến một lần ghi DB thất bại thành thành công. Metric nên tách **attempt failed** và **subscription terminal completed**, tránh báo kết quả sai.

## 14. Kiểm thử race: complete trước cancel, cancel trong lúc đang chạy

Cancellation và terminal event có thể đua nhau:

```text
Thread A: subscription đang xử lý
Thread B: context.close() bắt đầu cancel
Thread A: publisher vừa onComplete
Thread B: cancel sau terminal
```

Trong tình huống đó, `doFinally` có thể thấy `ON_COMPLETE` thay vì `CANCEL` vì subscription đã terminal trước khi cancellation có hiệu lực. Điều này không mâu thuẫn với việc Spring đã thực hiện quy trình shutdown.

Test nên phân biệt:

- **Trước close còn active chắc chắn:** dùng `Mono.never()` và `CountDownLatch` để chứng minh cancellation.
- **Đã complete trước close:** không kỳ vọng bắt buộc `doOnCancel`.
- **Nhiều subscriptions:** quan sát từng instance, không suy từ một cờ tổng hợp.
- **Lỗi trong lúc close:** kiểm tra cleanup/idempotent state transitions.
- **Bean bị destroy riêng:** nếu test bean scope thủ công, đảm bảo đó là Spring-managed lifecycle thực sự.

### 14.1. Test không nên dựa vào Thread.sleep

Một test ngủ 100 ms rồi gọi `context.close()` có thể chưa có subscriber nào do scheduler lag; assertion về cancellation sẽ sai ngẫu nhiên. Dùng latch `subscribed.await(timeout)` rồi mới close như ví dụ integration test.

### 14.2. Test resource cleanup riêng

Nếu scheduled pipeline mở file/connection/timer, test nên chứng minh `doFinally` hoặc `usingWhen` đóng resource. Nhưng hãy phân biệt “resource local đã cleanup” với “remote provider đã dừng xử lý”. Với dịch vụ ngoài, cần integration test/mock server có thể quan sát request/cancel/result, không đoán bằng log Reactor.

### 14.3. Không kiểm tra tính đúng đắn chỉ bằng số thread

`Mono.delay`, Netty, bounded elastic và scheduler worker có thể chạy trên các thread khác nhau. Name/quantity của thread không phải hợp đồng cancellation. Dùng counters, latches, terminal signals và trạng thái tài nguyên được quan sát trực tiếp.

## 15. Quan sát production: cần log/metric nào?

| Chỉ số | Ý nghĩa | Không được kết luận |
| --- | --- | --- |
| Scheduled ticks | Bao nhiêu lượt đăng ký subscription theo lịch | Không đồng nghĩa số nghiệp vụ xong |
| Subscription started | Bao nhiêu lần publisher được subscribe | Không bảo đảm side effect bắt đầu |
| Active subscriptions | Bao nhiêu subscription chưa terminal | Không phải số thread đang chạy |
| Completed | Số reactive subscriptions hoàn tất bình thường | Không mặc nhiên là business success |
| Error signals | Số lần phát `onError` | Không bảo đảm không có side effect |
| Cancel signals | Số subscriptions nhận cancellation | Không chứng minh remote service rollback |
| Cleanup duration | Thời gian giải phóng resource sau terminal/cancel | Không đo business latency |
| Executor saturation | Thread/queue/rejection | Không đo toàn bộ trạng thái HTTP/DB |
| In-flight job state | Trạng thái business work thực | Phải dựa vào job store/operation semantics |

Nên log `jobId`, `attemptId`/`subscriptionId` và `correlationId` như dữ liệu riêng, không phụ thuộc MDC từ request thread. Một scheduled task thường **không nằm trong scope của HTTP request**. Nếu dùng MDC trong Reactor callbacks, cần context propagation rõ ràng và cleanup theo thread/Subscriber Context; `ThreadLocal` không tự di chuyển qua scheduler.

### 15.1. Tách signal cancellation và status nghiệp vụ

Một state machine đơn giản có thể có:

```text
SCHEDULED -> SUBSCRIBED -> RUNNING
                        |      |
                        |      +--> SUCCEEDED
                        |      +--> FAILED
                        |      +--> CANCEL_REQUESTED
                        |                  |
                        |                  +--> CANCELLED_CONFIRMED
                        |                  +--> FINISHED_AFTER_CANCEL_REQUEST
                        |
                        +--> SUBSCRIPTION_CANCELLED
```

Đây là **mô hình khái niệm**, không phải state machine Spring cung cấp. `SUBSCRIPTION_CANCELLED` mô tả tầng reactive, `CANCELLED_CONFIRMED` mô tả nghiệp vụ đã dừng an toàn theo tiêu chí domain. Có thể một subscription bị cancel mà một operation remote vẫn đang xử lý.

## 16. Sự khác nhau giữa các kiểu shutdown/cancel

| Tình huống | Ai phát tín hiệu? | Điều chắc chắn ở tầng nào? |
| --- | --- | --- |
| Bean `@Scheduled` bị destroy theo Spring lifecycle | Scheduling infrastructure | Hủy lịch và active scheduled subscriptions được framework quản lý |
| `ApplicationContext.close()` bình thường | Spring container lifecycle | Trigger cleanup các managed beans/tasks |
| JVM bị kill không graceful | Hệ điều hành/container | Không đảm bảo Spring có callback destroy/cancel |
| Client HTTP disconnect | Client/network | Không tự destroy scheduled bean |
| `Future.get(timeout)` của một caller | Caller thread | Chỉ hết chờ, không tự cancel reactive job |
| `Disposable.dispose()` thủ công | Code sở hữu handle | Hủy subscription cụ thể theo Reactor semantics |
| `ScheduledFuture.cancel(true)` trực tiếp | Code có handle scheduling | Hủy/cố interrupt task scheduling; không tự bảo đảm mọi detached publisher đã được hủy nếu không có registry |
| `Mono.timeout(...)` | Reactive operator | Hết hạn subscription theo chính sách operator, có thể cancel upstream, nhưng external side effect không tự rollback |

Đặc biệt, **không gọi `System.exit(...)` hoặc `Runtime.halt(...)` trong ví dụ/test để “chứng minh” bean destroy**. `context.close()` có thể kích hoạt Spring lifecycle có kiểm soát trong cùng JVM, kiểm chứng được bằng latch/metrics.

## 17. Lỗi thường gặp

**Lỗi 1 — Tin reactive @Scheduled tạo Publisher mới mỗi tick.** Spring 6.1+ lấy publisher một lần, subscribe lặp lại. Cần publisher cold/deferred và multiple-subscription-safe.

**Lỗi 2 — Dùng CompletableFuture như một reactive deferred Publisher.** Adapter của CompletableFuture không báo deferred subscription nên không hợp với con đường reactive scheduled method này.

**Lỗi 3 — Gọi subscribe() thủ công trong method @Scheduled rồi return cùng publisher.** Có thể phát sinh detached/double subscription không thuộc ownership của Spring.

**Lỗi 4 — Nghĩ context.close chỉ hủy lịch tương lai.** Spring còn hủy active scheduled subscriptions trong vòng đời reactive scheduling.

**Lỗi 5 — Nghĩ cancel subscription làm rollback transaction đã commit.** Không có rollback tự động của external side effects chỉ vì `cancel`.

**Lỗi 6 — Không hiểu fixedDelay reactive phải chờ terminal signal.** Infinite publisher có thể giữ scheduler thread; cần cancellation và timeouts.

**Lỗi 7 — Nghĩ poolSize=1 bảo đảm chỉ một reactive operation active.** Với fixedRate nonblocking, nhiều subscriptions có thể đồng thời active.

**Lỗi 8 — Mono.just() chụp kết quả eager và kỳ vọng mỗi lần subscribe gọi lại công việc.** Dùng `Mono.defer`/`fromCallable` đúng ranh giới và không block scheduler thread.

**Lỗi 9 — Bọc blocking JDBC bằng Mono.fromCallable nhưng không offload.** Publisher deferred không đồng nghĩa I/O không blocking.

**Lỗi 10 — Tin doOnCancel chứng minh provider chưa xử lý request.** Side effect có thể đã xảy ra hoặc vẫn đang diễn ra.

**Lỗi 11 — Chỉ đếm subscription complete rồi báo business success.** Cần xác nhận durable result/transaction boundary.

**Lỗi 12 — Kỳ vọng ErrorHandler của scheduler nhận mọi lỗi onError.** Error signal reactive được Spring log/recover theo đường riêng.

**Lỗi 13 — Dùng retry vô hạn cho side effect không idempotent.** Có thể thực hiện lặp nghiệp vụ nhiều lần.

**Lỗi 14 — Mong Spring hủy subscription do app tự subscribe bên ngoài @Scheduled.** Manual subscriptions cần owner/dispose riêng.

**Lỗi 15 — Chạy nhiều replicas mà nghĩ chỉ một scheduled reactive job toàn cluster.** Mỗi context có schedule riêng; cần distributed coordination.

**Lỗi 16 — Tin @PreDestroy callback có thứ tự tuyệt đối trước/sau mọi doFinally.** Lifecycle và scheduling có nhiều thành phần; test contract cần thiết.

**Lỗi 17 — Không kiểm thử shutdown khi publisher còn active.** Test chỉ có publisher complete ngay không chứng minh cancellation.

**Lỗi 18 — Dùng Thread.sleep rồi assert cancel signal.** Scheduler lag khiến test flaky; dùng latch xác nhận đã subscribe.

**Lỗi 19 — Dùng @Scheduled reactive trên Spring Framework 5.x như thể API đã hỗ trợ.** Reactive scheduled methods được Spring Framework 6.1 giới thiệu.

**Lỗi 20 — Hiểu cancellation như crash recovery.** JVM chết đột ngột có thể không có callback; cần lưu trạng thái và retry/idempotency ở tầng nghiệp vụ.

## 18. Checklist triển khai Spring reactive scheduling

- [ ] Xác nhận Spring Framework **6.1+** và có Reactor/Reactive Streams dependencies phù hợp.
- [ ] `@EnableScheduling` bật trong đúng ApplicationContext, bean có method `@Scheduled` thực sự được Spring quản lý.
- [ ] Reactive return type là `Publisher`/adapter **deferred** và chịu được **nhiều lần subscribe**.
- [ ] Công việc của mỗi lượt nằm **trong publisher cold/deferred**, không chạy eager lúc method được gọi lần đầu.
- [ ] Không tự `subscribe()` trong scheduled method nếu định return publisher cho Spring subscribe.
- [ ] Với fixedRate/cron, giới hạn số active subscriptions và tránh trùng side effect.
- [ ] Với fixedDelay, bảo đảm publisher terminal hợp lý hoặc có timeout/cancellation an toàn.
- [ ] Gắn `doFinally`/resource management để theo dõi và dọn tài nguyên, nhưng không nhầm với rollback external operation.
- [ ] Nếu có manually subscribed streams, sở hữu `Disposable` riêng và dọn trong lifecycle phù hợp.
- [ ] Đặt timeout ở HTTP/DB/broker, thiết kế idempotency cho side effect/retry.
- [ ] Kiểm thử bean/context shutdown bằng `Mono.never()` + latches để chứng minh active subscription nhận cancel.
- [ ] Kiểm thử race complete/error/cancel, nhiều subscribers, executor/thread blocking, graceful shutdown.
- [ ] Đo `subscribed/completed/errored/cancelled/active` và kết quả nghiệp vụ **riêng biệt**.
- [ ] Với multi-replica, dùng coordination/job store khi cần chỉ một processor xử lý.
- [ ] Với crash recovery, lưu progress durable và reconciliation; không phụ thuộc callback shutdown luôn được gọi.

## 19. Câu hỏi tự kiểm tra

**Câu 1.** `@Scheduled` reactive trả `Mono<Void>`: Spring gọi method tạo Mono mỗi tick hay lấy publisher một lần?

**Đáp án:** Spring Framework 6.1+ lấy publisher từ method một lần, sau đó định kỳ **subscribe** publisher đó theo lịch.

**Câu 2.** Vì sao `Mono.defer` quan trọng trong một reactive scheduled method?

**Đáp án:** Nó trì hoãn việc tạo/thực thi pipeline tới từng subscription, giúp mỗi tick thực hiện work mới khi publisher được subscribe lặp lại.

**Câu 3.** Khi bean bị destroy, Spring chỉ ngừng tick tương lai hay còn hủy subscriptions hiện tại?

**Đáp án:** Cả hai: Spring hủy scheduled tasks và active subscriptions thuộc lịch reactive của bean.

**Câu 4.** Một `Mono.delay(Duration.ofMinutes(10))` đã được subscribe, context đóng sau 1 phút. Tín hiệu nào cần quan sát?

**Đáp án:** Subscription đang active được cancel; có thể dùng `doOnCancel`/`doFinally(CANCEL)` hoặc test latch để xác minh.

**Câu 5.** `@Scheduled(fixedRate=1000)` trả `Mono<Void>` mất 5 giây. Có thể có nhiều active subscriptions không?

**Đáp án:** Có, fixed rate scheduling có thể tạo subscription mới khi subscription trước chưa hoàn tất. Không suy từ scheduler poolSize=1 rằng chỉ một operation in-flight.

**Câu 6.** Vì sao fixedDelay reactive có thể giữ scheduler thread?

**Đáp án:** Hạ tầng block tới khi subscription terminal để giữ ngữ nghĩa fixed delay. Infinite publisher không complete có thể khiến thread đợi lâu, trừ khi được cancel/giới hạn hợp lệ.

**Câu 7.** `doFinally(SignalType.CANCEL)` đã chạy. Có chắc remote payment đã rollback không?

**Đáp án:** Không. Cancellation là trạng thái reactive subscription; provider bên ngoài có thể đã nhận/commit giao dịch.

**Câu 8.** Method `@Scheduled` tự gọi `subscribe()` rồi trả publisher. Có an toàn không?

**Đáp án:** Thường không: có thể tạo subscription ngoài ownership của Spring và Spring còn subscribe publisher theo lịch. Chỉ return publisher cold/deferred để framework quản lý.

**Câu 9.** Nếu một `onError` xuất hiện, Spring `ErrorHandler` cho task đồng bộ có luôn bắt được không?

**Đáp án:** Không. Reactive error đi theo signal, Spring log WARN và tiếp tục scheduled subscriptions theo quy tắc riêng; ErrorHandler của Runnable không trực tiếp quản lý signal đó.

**Câu 10.** Test nào chứng minh shutdown cancellation tốt hơn ngủ 100 ms rồi đóng context?

**Đáp án:** Test dùng `CountDownLatch` xác nhận đã subscribe `Mono.never()`, sau đó gọi `context.close()` và đợi latch `doOnCancel` mở.

**Câu 11.** CompletableFuture có thể tự được dùng như deferred reactive scheduled return type không?

**Đáp án:** Không theo adapter contract này: `CompletableFuture` không hỗ trợ deferred subscription như Spring yêu cầu cho return type chuyển qua ReactiveAdapterRegistry.

**Câu 12.** Nếu JVM bị kill đột ngột thì `doFinally(CANCEL)` có bắt buộc chạy không?

**Đáp án:** Không. Crash/kill có thể bỏ qua lifecycle/cancel; cần durable state, retry/idempotency/reconciliation nếu công việc quan trọng.

## Kết luận

**Spring Framework 6.1+ chủ động quản lý vòng đời reactive `@Scheduled`:** framework lấy `Publisher` một lần, định kỳ tạo subscriptions và khi bean bị hủy hoặc ApplicationContext đóng, nó **hủy những lần subscribe trong tương lai và cả subscriptions còn active**.

Để hành vi này hữu ích và an toàn:

1. Return **cold/deferred, multi-subscription-safe publisher**, không eager work hoặc manual `subscribe()` không kiểm soát.
2. Phân biệt **schedule cancellation**, **reactive subscription cancellation** và **business stop/rollback**.
3. Thiết kế timeout, resource cleanup, idempotency và recovery cho I/O/transaction bên ngoài.
4. Kiểm thử context shutdown khi đã có active subscription bằng latches; đừng chỉ kiểm tra tick ngừng.
5. Giám sát active/completed/errored/cancelled subscriptions tách khỏi kết quả nghiệp vụ.

**Cancellation là cam kết quản lý subscription trong lifecycle Spring, không phải phép màu dừng mọi side effect ngoài JVM.**

## Tài liệu tham khảo

1. Spring Framework Reference — `@Scheduled` trên reactive methods, cách subscribe và hủy khi bean destroy/context close: https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-reactive
2. Spring Framework 6.2 Javadoc — `@Scheduled`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html
3. Spring Framework 6.2 Javadoc — `ScheduledAnnotationBeanPostProcessor`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/ScheduledAnnotationBeanPostProcessor.html
4. Reactive Streams — specification `Subscription.request/cancel`: https://www.reactive-streams.org/
5. Reactor Core Reference — cancel, Disposable, resource cleanup and debugging: https://projectreactor.io/docs/core/release/reference/
6. Project Reactor Javadoc — `Mono.defer`, `doOnCancel`, `doFinally`: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html
7. Spring Boot Reference — Task Execution and Scheduling: https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html
8. Java SE 17 — `Flow.Publisher` and `Flow.Subscription` in standalone demo: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Flow.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-scheduling-009">Không coi CompletableFuture là nguồn trì hoãn đăng ký nhận phù hợp trong mọi trường hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Không coi `CompletableFuture` là nguồn trì hoãn đăng ký nhận phù hợp trong mọi trường hợp

## Mục tiêu

Spring Framework hỗ trợ nhiều mô hình bất đồng bộ: `@Async` trả `CompletableFuture<T>`, `CompletionStage`, `TaskExecutor`, và lập lịch reactive với `@Scheduled` trả về `Publisher` như Reactor `Mono` hoặc `Flux`. Các kiểu này **không có cùng hợp đồng về thời điểm công việc bắt đầu**.

Từ **Spring Framework 6.1**, `@Scheduled` hỗ trợ các phương thức trả về reactive `Publisher` và những kiểu có thể chuyển thành `Publisher` bởi `ReactiveAdapterRegistry`, **với điều kiện kiểu đó hỗ trợ deferred subscription**. `CompletableFuture` là một ví dụ quan trọng của kiểu **có thể thích nghi sang Publisher nhưng không đáp ứng điều kiện deferred subscription**: adapter mặc định của nó báo `getDescriptor().isDeferred() == false`.

Vì vậy, một phương thức reactive scheduled như sau **không phải mẫu hợp lệ** trong môi trường Spring 6.1+/6.2 có adapter cho `CompletableFuture`:

```java
@Scheduled(fixedRate = 1000)
public CompletableFuture<Void> invalidScheduledReturn() {
    return CompletableFuture.runAsync(this::doWork);
}
```

Đây là **đoạn minh họa sai về hợp đồng reactive `@Scheduled`**, không phải chương trình có thể chạy; `doWork` do ứng dụng định nghĩa. Trên cấu hình có Spring reactive adapter hỗ trợ CompletableFuture, Spring kiểm tra loại return ngay lúc đăng ký lịch và từ chối vì nó **không deferred**, thay vì tự lặp một `CompletableFuture` như một Publisher có thể tái đăng ký nhận.

Bài Knowledge này thuộc **Lập lịch reactive** của module **Spring Framework - concurrency**. Sau khi học, bạn có thể:

1. Phân biệt **khởi tạo một tác vụ bất đồng bộ** với **đăng ký nhận một nguồn reactive**.
2. Giải thích `isDeferred` và vì sao **adaptable** không có nghĩa **eligible for reactive `@Scheduled`**.
3. Hiểu Spring lấy **một Publisher** khi xử lý annotation rồi **subscribe nhiều lần** theo lịch.
4. Viết `Mono.defer(...)`, `Mono.fromRunnable(...)`, `Mono.fromCallable(...)` và `Mono.defer(() -> Mono.fromFuture(...))` đúng chỗ.
5. Chạy ví dụ **Java 17+ + Spring Framework 6.2.12 + Reactor** kiểm chứng `CompletableFuture` adapter không deferred, `Mono` adapter deferred, method tạo Publisher được gọi một lần nhưng nguồn được subscribe nhiều lần.
6. Phân biệt lỗi, cancellation, fixed delay, fixed rate, `@Async`, shutdown và những rủi ro của hot Publisher.
7. Quyết định khi nào nên dùng `@Scheduled` reactive và khi nào nên dùng `@Scheduled void` gọi một async service có trả Future.

**Kết luận chính:** với reactive scheduling, điều Spring cần là một **nguồn có thể đăng ký nhận lại để khởi tạo mỗi lượt xử lý khi đến hạn**. Một `CompletableFuture` được khởi động theo mô hình eager không đáp ứng vai trò đó chỉ vì có adapter chuyển đổi sang `Publisher`.

## 1. Từ JDK Future tới Reactive Streams: hai thời điểm khác nhau

`CompletableFuture<T>` đại diện một computation đã có trạng thái và có thể hoàn thành một lần. Nếu nó được tạo bởi `CompletableFuture.supplyAsync(...)`, công việc thường được submit **ngay khi gọi `supplyAsync`**:

```java
CompletableFuture<String> future =
    CompletableFuture.supplyAsync(() -> loadData());
```

Đây là snippet không độc lập, cần `loadData` và executor policy rõ. Sau khi `future` được tạo, tác vụ đã được gửi đi, không chờ tới khi ai đó gọi `future.get()` hoặc một adapter subscribe vào future.

Ngược lại, một **cold, deferred `Mono`** có thể **chỉ tạo việc tại thời điểm subscribe**:

```java
Mono<String> source = Mono.defer(
    () -> Mono.fromCallable(() -> loadData())
);
```

Đây là snippet Reactor. Chỉ dựng `source` chưa gọi `loadData()`. Mỗi subscription có thể chạy `loadData()` mới. Nhưng hãy chú ý: `Mono.fromCallable` thực thi callable trên **thread subscribe** nếu không có cơ chế chuyển luồng; nếu `loadData()` là blocking I/O, nó có thể chặn scheduler worker. Với I/O blocking cần policy executor và timeout thích hợp.

Mô hình:

```text
CompletableFuture.supplyAsync(...)
    |
    +-- submit công việc NGAY
    |
    +-- Future thành công/thất bại một lần
    |
    +-- nhiều observer / subscriber chỉ đọc cùng kết quả đã có

Mono.defer(...)
    |
    +-- tạo một cấu trúc Publisher chưa chạy
    |
    +-- subscribe #1 -> tạo công việc cho lần 1
    |
    +-- subscribe #2 -> tạo công việc cho lần 2
    |
    +-- subscribe #3 -> tạo công việc cho lần 3
```

Không nên tuyệt đối hóa rằng “mọi `Mono` đều lazy và tự chạy lại”: `Mono.just(value)` giữ một giá trị đã tính, `Mono.cache()` cố ý chia sẻ kết quả, hot publisher có semantics khác, và callable/bất đồng bộ bên trong cũng có thể đã được khởi động sớm. **Kiểu Mono cho phép** một pipeline deferred, nhưng cách cấu tạo cụ thể quyết định có công việc mới cho từng subscription hay không.

## 2. Spring 6.1+ kiểm tra điều gì ở reactive `@Scheduled`?

Theo Spring Framework Reference, phương thức `@Scheduled` reactive có thể trả về:

- Một `Publisher` (hoặc concrete Publisher như Reactor `Mono`, `Flux`) **có thể chịu nhiều subscriptions**.
- Một kiểu khác được `ReactiveAdapterRegistry` thích nghi thành `Publisher`, nhưng phải có adapter với descriptor **`isDeferred() == true`**.
- Kotlin `suspend` function hoặc một số loại Kotlin `Flow`/`Deferred` với reactor bridge cần thiết, theo cấu hình tương ứng.

Trong trường hợp kiểu được thích nghi, chỉ biết registry có adapter **chưa đủ**. Kiểm tra:

```java
ReactiveAdapter adapter =
    ReactiveAdapterRegistry.getSharedInstance()
        .getAdapter(CompletableFuture.class);

boolean deferred = adapter.getDescriptor().isDeferred();
```

Đây là snippet dùng Spring Core trong môi trường có adapter đăng ký cho `CompletableFuture`. Khi có Reactor trên classpath như trong demo ở phần 6, kết quả `deferred` của adapter mặc định là **`false`**.

### `ReactiveTypeDescriptor.isDeferred()` nghĩa gì?

`isDeferred()` phân biệt:

- **Deferred:** việc nền tảng chưa bắt đầu, cần hành động của consumer như subscribe để kích hoạt.
- **Non-deferred:** việc có thể đã được kích hoạt mà consumer không kiểm soát thời điểm bắt đầu.

`CompletableFuture` là một container cho kết quả computation có thể đã được khởi chạy. `CompletableFuture.completedFuture(value)` thậm chí đã hoàn tất từ trước khi ai subscribe; `CompletableFuture.supplyAsync(...)` có thể đang xử lý hoặc đã hoàn tất.

Một adapter có thể biến **cùng future** thành một `Mono`, nhưng sự chuyển đổi không có quyền tự động **tái khởi động computation ban đầu** cho lần subscribe sau.

### Vì sao đây là kiểm tra quan trọng?

Spring reactive `@Scheduled` **lấy Publisher một lần**, rồi lên lịch một Runnable để **subscribe cùng Publisher đó nhiều lần**. Nếu nguồn chỉ có một computation eager, việc subscribe lại chỉ thấy **cùng completion**, không đại diện cho một tick nghiệp vụ mới.

Cơ chế có thể tóm tắt:

```text
Application context đăng ký @Scheduled
          |
          v
      gọi method reactive MỘT LẦN
          |
          v
  nhận một Publisher cần multi-subscribe
          |
          +-- tick 1: subscribe -> lượt công việc 1
          |
          +-- tick 2: subscribe -> lượt công việc 2
          |
          +-- tick 3: subscribe -> lượt công việc 3
```

Với một `Mono.defer(...)`, việc subscribe sau có thể tạo công việc mới. Với một future eager tạo ngay ở method, không có phép thần kỳ biến future một lần thành ba computation.

## 3. Vì sao không chỉ “convert CompletableFuture sang Mono” một lần?

Giả sử có API:

```java
CompletableFuture<String> fetchAsync();
```

Và một method scheduled:

```java
@Scheduled(fixedRate = 1000)
public Mono<String> stillWrongSemantics() {
    CompletableFuture<String> future = fetchAsync();
    return Mono.fromFuture(future);
}
```

Đây là snippet minh họa **sai về tính lặp của business work**; `fetchAsync` được giả định có sẵn. Method return `Mono`, vì vậy Spring không từ chối vì *return type* của nó. Nhưng `fetchAsync()` được gọi khi Spring **lấy Publisher một lần**, khiến một future duy nhất được tạo và bắt đầu từ rất sớm. Các subscription sau đó quan sát **chính future ấy**, không gọi lại `fetchAsync()`.

Đây là khác biệt giữa **đúng kiểu trả về** và **đúng cơ chế lặp theo lịch**.

Hãy viết lại thành nguồn deferred:

```java
@Scheduled(fixedRate = 1000)
public Mono<String> correctPerSubscription() {
    return Mono.defer(
        () -> Mono.fromFuture(fetchAsync())
    );
}
```

Ở đây, method scheduled tạo một `Mono` duy nhất, nhưng lambda bên trong `Mono.defer` **chỉ gọi `fetchAsync()` khi một subscription mới xảy ra**. Mỗi tick có cơ hội tạo future riêng. Kiểu trả về trực tiếp của method vẫn là `Mono<String>` — một reactive Publisher được Spring chấp nhận.

Một biến thể Reactor hỗ trợ lazy future supplier cũng có thể dùng `Mono.fromFuture(() -> fetchAsync())`, khi API Reactor của phiên bản dự án cung cấp overload ấy. Trong bài này dùng `Mono.defer` để diễn đạt rành mạch ranh giới khởi tạo task và tránh nhầm giữa overload nhận Future **đã có** với overload nhận **Supplier**.

### Một đường code chạy được nhưng vẫn sai nghiệp vụ

```java
@Scheduled(fixedRate = 1000)
public Mono<String> wrongSnapshot() {
    String current = fetchSynchronously(); // chạy khi tạo Publisher
    return Mono.just(current);
}
```

Đây là snippet minh họa, cần method `fetchSynchronously`. `Mono.just(current)` cho phép subscribe nhiều lần nhưng tất cả subscriber thấy **giá trị current đã chụp khi method được gọi một lần**. Nếu muốn đọc dữ liệu mới ở mỗi tick, dùng `Mono.fromSupplier(this::fetchSynchronously)` hay `Mono.fromCallable(...)`, cộng cơ chế tách thread nếu lời gọi blocking.

Cũng cần thận trọng với:

```java
@Scheduled(fixedRate = 1000)
public Mono<String> cached() {
    return Mono.defer(this::fetchPublisher).cache();
}
```

Snippet này có `fetchPublisher` trả Mono thích hợp. `cache()` giữ kết quả sau lần subscribe đầu và phát lại ở những subscription tiếp theo; đây có thể là **chủ ý** để chia sẻ dữ liệu, nhưng không đúng nếu mục tiêu là refresh backend tại mỗi tick.

## 4. Điều kiện deferred là thuộc về adapter, không thuộc giá trị Future cụ thể

`ReactiveAdapterRegistry` dùng `ReactiveTypeDescriptor` để mô tả các đặc tính của một *loại* reactive/async. Spring xác định tính eligible theo **return type method** và adapter đã đăng ký, không thử “xem future lần này có lười hay không” rồi quyết định cho phép.

Một method:

```java
@Scheduled(fixedRate = 1000)
public CompletableFuture<Void> invalid() {
    return CompletableFuture.completedFuture(null);
}
```

Dù Future đã hoàn tất rất nhanh, kiểu trả về vẫn là `CompletableFuture<Void>` và descriptor vẫn không deferred trong adapter mặc định. **Future completed không tương đương Publisher deferred**.

Ngược lại, một method:

```java
@Scheduled(fixedRate = 1000)
public Mono<Void> valid() {
    return Mono.defer(() -> {
        // tạo tác vụ mới tại subscription
        return Mono.empty();
    });
}
```

được Spring chấp nhận trong môi trường có Reactor và scheduling annotations phù hợp. Cấu trúc `Mono` cho phép subscriptions liên tiếp; code trong supplier chỉ chạy khi có subscription.

### Lỗi thường xuất hiện ở giai đoạn nào?

Trong Spring Framework 6.1+/6.2, nếu return type method có adapter non-deferred, `ScheduledAnnotationReactiveSupport` kiểm tra `candidateAdapter.getDescriptor().isDeferred()` trong **giai đoạn xử lý annotation/đăng ký lịch**. Với cấu hình Reactor có adapter cho `CompletableFuture`, method reactive `@Scheduled` trả Future trực tiếp có thể gây **lỗi khởi tạo Spring context** vì type không đủ điều kiện, thay vì chỉ báo lỗi trong một lần chạy sau này.

Không dựa vào thông điệp exception chính xác như một API ổn định; điểm đáng tin là **type không phù hợp với hợp đồng deferred subscription**.

**Phạm vi của nhận định:** Nếu classpath/registry không có adapter cho một return type, Spring có thể xử lý phương thức như method scheduled đồng bộ có return value bị bỏ qua, tùy hợp đồng/version. Khi đó method có thể được gọi theo lịch nhưng **Future trả về không được Spring theo dõi như một reactive Publisher**. Vì vậy không tuyên bố “mọi `CompletableFuture` trong mọi Spring context luôn bị reject”; hãy kiểm tra Spring version, reactive adapter classpath và đường gọi thật.

## 5. `@Scheduled` reactive khác `@Async` trả CompletableFuture

Đây là hai hợp đồng độc lập:

| Tiêu chí | `@Scheduled` reactive | `@Async` với `CompletableFuture<T>` |
| --- | --- | --- |
| Ai kích hoạt? | Scheduler theo cron/fixed rate/fixed delay. | Caller gọi method qua Spring async proxy. |
| Thời điểm method trả Publisher | Thường **một lần khi đăng ký schedule**. | Mỗi lời gọi từ caller có một future handle. |
| Cách tạo mỗi lượt xử lý | Subscribe lại vào Publisher đã có. | Mỗi lần caller gọi có thể submit một task mới. |
| Kiểu trả về phù hợp | Publisher, hoặc adaptable + deferred type. | `void`, `Future`/`CompletableFuture` theo hợp đồng `@Async`. |
| Có cần `@EnableScheduling`? | Có cho annotation. | Không; `@EnableAsync` dùng cho async proxy. |
| Có bắt buộc `@EnableAsync`? | Không. | Có trong proxy mode thông thường. |
| Phân phối lỗi | `onError` của Publisher được Spring log và xử lý theo reactive scheduling. | Exception ghi vào future cho caller hoặc handler riêng với void. |
| Kết quả `onNext` | Spring **bỏ qua** dữ liệu Publisher phát ra. | Caller có thể lấy `T` từ future. |

Điều này làm rõ một câu thường gây tranh luận: **`CompletableFuture` vẫn là lựa chọn tốt cho nhiều API bất đồng bộ Java**, kể cả `@Async`. Nó chỉ **không mặc nhiên** là nguồn deferred multi-subscribe phù hợp để làm return type của reactive `@Scheduled`.

Nếu module đã có service trả `CompletableFuture`, bạn **không bắt buộc** phải đổi toàn bộ service sang Reactor. Có thể dùng `Mono.defer(() -> Mono.fromFuture(service.callAsync()))` ở ranh giới scheduled method, hoặc dùng `@Scheduled void` gọi service rồi theo dõi future rõ ràng như ở phần 9.

## 6. Ví dụ đầy đủ Java 17+ với Spring 6.2.12 và Reactor

Ta tạo một project Maven nhỏ có hai quan sát:

- `ReactiveAdapterRegistry` báo adapter `CompletableFuture` là **non-deferred** và adapter `Mono` là **deferred**.
- Một `@Scheduled` method trả `Mono<Void>` **được Spring gọi một lần để tạo Publisher**, nhưng lambda của `Mono.defer` chạy **ít nhất ba lần** nhờ ba scheduled subscriptions. Có dùng `CountDownLatch` thay vì ngủ và đoán.

Ví dụ cần **JDK 17+**, Maven, **Spring Framework 6.2.12** và **Reactor Core 3.7.18**. Không cần Spring Boot, Netty, WebFlux hoặc server HTTP: chỉ có Spring Context với scheduling.

### Cấu trúc thư mục

```text
reactive-scheduled-deferred-demo/
├── pom.xml
└── src/main/java/example/ReactiveScheduledDeferredDemo.java
```

Tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>example</groupId>
    <artifactId>reactive-scheduled-deferred-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.12</version>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
            <version>3.7.18</version>
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
                    <mainClass>example.ReactiveScheduledDeferredDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tệp `src/main/java/example/ReactiveScheduledDeferredDemo.java`:

```java
package example;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import reactor.core.publisher.Mono;

public class ReactiveScheduledDeferredDemo {

    public static class TickJob {
        private final AtomicInteger publisherMethodCalls = new AtomicInteger();
        private final AtomicInteger subscriptions = new AtomicInteger();
        private final CountDownLatch firstThreeSubscriptions =
            new CountDownLatch(3);

        @Scheduled(fixedDelay = 50, scheduler = "taskScheduler")
        public Mono<Void> tick() {
            // Spring gọi method để nhận Publisher một lần.
            publisherMethodCalls.incrementAndGet();

            // Supplier này chỉ chạy tại mỗi subscription.
            return Mono.defer(() -> {
                subscriptions.incrementAndGet();
                firstThreeSubscriptions.countDown();
                return Mono.<Void>empty();
            });
        }

        public boolean awaitThree() throws InterruptedException {
            return firstThreeSubscriptions.await(5, TimeUnit.SECONDS);
        }

        public int publisherCalls() {
            return publisherMethodCalls.get();
        }

        public int subscriptionCount() {
            return subscriptions.get();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    public static class Config {
        @Bean
        public TickJob tickJob() {
            return new TickJob();
        }

        @Bean("taskScheduler")
        public ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("reactive-tick-");
            return scheduler;
        }
    }

    public static void main(String[] args) throws Exception {
        ReactiveAdapterRegistry registry =
            ReactiveAdapterRegistry.getSharedInstance();

        ReactiveAdapter futureAdapter =
            registry.getAdapter(CompletableFuture.class);
        ReactiveAdapter monoAdapter =
            registry.getAdapter(Mono.class);

        if (futureAdapter == null || monoAdapter == null) {
            throw new IllegalStateException(
                "Expected Future and Mono reactive adapters not found"
            );
        }

        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(Config.class)) {

            TickJob job = context.getBean(TickJob.class);
            if (!job.awaitThree()) {
                throw new IllegalStateException(
                    "Fewer than three subscriptions within five seconds"
                );
            }

            System.out.println("futureAdapterDeferred="
                + futureAdapter.getDescriptor().isDeferred());
            System.out.println("monoAdapterDeferred="
                + monoAdapter.getDescriptor().isDeferred());
            System.out.println("publisherCreatedOnce="
                + (job.publisherCalls() == 1));
            System.out.println("subscribedAtLeastThreeTimes="
                + (job.subscriptionCount() >= 3));
        }
    }
}
```

Chạy trong thư mục `reactive-scheduled-deferred-demo`:

```bash
mvn -q compile exec:java
```

Bốn dòng **do chương trình in ra** khi chạy thành công:

```text
futureAdapterDeferred=false
monoAdapterDeferred=true
publisherCreatedOnce=true
subscribedAtLeastThreeTimes=true
```

### Giải thích từng dòng và điều được chứng minh

1. `futureAdapterDeferred=false`: `ReactiveAdapterRegistry` nhìn thấy adapter mặc định cho JDK `CompletableFuture` (vì demo có Reactor) nhưng descriptor cho biết nó **không hỗ trợ deferred subscription**.
2. `monoAdapterDeferred=true`: Reactor `Mono` có adapter deferred, phù hợp để Spring tổ chức nhiều lần subscribe.
3. `publisherCreatedOnce=true`: Spring gọi method `TickJob.tick()` một lần trong bước tạo Publisher cho registration. Biến `publisherMethodCalls` không nằm trong `Mono.defer`, nên không tăng khi mỗi tick subscribe lại.
4. `subscribedAtLeastThreeTimes=true`: lambda trong `Mono.defer` chạy với từng scheduled subscription, tăng biến đếm. `CountDownLatch(3)` đảm bảo ít nhất ba lượt đã tới trước khi main in.
5. `fixedDelay=50` trong demo tính bằng mili giây mặc định. Vì `Mono` hoàn tất ngay, mỗi lượt chỉ tốn rất ít thời gian và Spring đợi completion của subscription trước khi tính fixed delay cho lượt kế tiếp.
6. Chương trình in từ main sau latch, không print từ scheduler thread. Số subscription cuối có thể lớn hơn ba vì scheduler có thể chạy thêm trước khi main được CPU; demo không hứa `subscriptionCount == 3`.

**Lưu ý:** Bản demo chỉ đọc adapter metadata và dùng một method hợp lệ. Nó **không đăng ký method `@Scheduled` trả `CompletableFuture` sai**, vì việc đó có thể làm Spring context thất bại khi startup. Ví dụ sai đã được mô tả có chủ đích ở phần 1/4, không được đưa vào bean chạy thật.

Nếu dependency không tải được hoặc scheduler không tạo đủ ba subscriptions trong năm giây, chương trình ném exception thay vì in bốn dòng. Các expected outputs dựa trên Spring 6.2.12 và Reactor 3.7.18; đây là kiểm tra tĩnh của mã/hợp đồng API, không phải lời khẳng định đã thực thi Maven trong môi trường bài học.

## 7. `Mono.defer` và các cách tạo tác vụ mới theo mỗi tick

### Cách 1 — Không blocking, service đã có CompletableFuture

Một service thực hiện I/O async trả `CompletableFuture<Result>`:

```java
public CompletableFuture<Result> fetchAsync() {
    return client.fetchAsync();
}
```

Đây là snippet thuộc một service cần `Result`, `client` và imports tương ứng.

Tại scheduled method, trả `Mono` và trì hoãn **việc tạo Future**:

```java
@Scheduled(fixedRate = 1000)
public Mono<Void> pollEachTick() {
    return Mono.defer(
        () -> Mono.fromFuture(service.fetchAsync()).then()
    );
}
```

Đây là snippet trong bean được Spring quản lý; `service` được inject, `@EnableScheduling` đã bật. Mỗi subscription thực thi supplier của `Mono.defer`, rồi gọi `service.fetchAsync()` để tạo Future mới. `.then()` chuyển kết quả thành `Mono<Void>`, phù hợp khi scheduling chỉ quan tâm completion và không sử dụng value.

**Cảnh báo:** Nếu `service.fetchAsync()` nội bộ dùng một Future đã cache hoặc dùng một shared ongoing computation, `Mono.defer` không tự biến nó thành computation mới. Cần hiểu chính implementation của service.

### Cách 2 — Logic nhỏ không có giá trị trả về

```java
@Scheduled(fixedDelay = 1000)
public Mono<Void> cleanup() {
    return Mono.fromRunnable(() -> cleanupCache());
}
```

Đây là snippet có method `cleanupCache`. `Mono.fromRunnable` tạo nguồn thực thi `Runnable` tại subscription, thường đúng cho một side effect ngắn; không cần lồng `Mono.defer` nếu bản thân `fromRunnable` đã đủ lười.

Nếu `cleanupCache` blocking, invocation xảy ra theo thread subscription trừ khi đã cấu hình scheduling operator khác. Đừng block scheduler thread trong một source reactive rồi gọi đó là “non-blocking”.

### Cách 3 — Callable có kết quả

```java
@Scheduled(fixedDelay = 1000)
public Mono<Summary> snapshot() {
    return Mono.fromCallable(() -> readSnapshot());
}
```

Đây là snippet cần `Summary` và `readSnapshot`. `Mono.fromCallable` thực thi callable theo subscription và phát kết quả; Spring bỏ qua `onNext` của reactive scheduled method. Nếu kết quả có ý nghĩa nghiệp vụ, tự ghi metric/state hoặc gửi nó tới consumer phù hợp, không kỳ vọng scheduler sẽ đưa `Summary` tới HTTP caller.

Nếu callable chặn I/O, có thể chủ động chuyển sang Scheduler dành cho blocking work:

```java
return Mono.fromCallable(() -> readSnapshot())
    .subscribeOn(Schedulers.boundedElastic());
```

Đây là snippet Reactor, cần import `Schedulers` và quản lý timeout/overload. `boundedElastic` không phải hàng đợi vô hạn miễn phí; có giới hạn, có thể backlog và phải phù hợp với I/O client thực tế. Với Reactor Netty/WebClient nonblocking, thường tốt hơn sử dụng API reactive nguyên bản thay vì bọc một blocking call.

### Cách 4 — `Mono.defer` cùng `Mono.fromFuture`

```java
@Scheduled(fixedDelay = 1000)
public Mono<String> request() {
    return Mono.defer(() ->
        Mono.fromFuture(service.requestAsync())
    );
}
```

Khác với:

```java
@Scheduled(fixedDelay = 1000)
public Mono<String> requestEager() {
    CompletableFuture<String> single = service.requestAsync();
    return Mono.fromFuture(single);
}
```

Snippet thứ nhất gọi `service.requestAsync()` **mỗi lần subscribe**; snippet thứ hai chỉ tạo Future **một lần khi Spring gọi method để lấy Publisher**. Dù cả hai return `Mono<String>`, chỉ cái đầu bảo đảm điểm gọi service được trì hoãn tới subscription.

## 8. Tại sao `Publisher` phải hỗ trợ nhiều subscriptions?

`@Scheduled` reactive dùng cùng một instance Publisher cho các lần subscribe. Nguồn phải chịu được việc đăng ký nhận **nhiều lần liên tiếp**.

Cold Publisher do `Mono.defer`, `Mono.fromSupplier`, `Mono.fromCallable` hoặc `Flux.defer` xây dựng có thể chạy lại logic cho mỗi subscription. Ngược lại, một hot source có state chia sẻ hoặc chỉ có thể phát một lần không phù hợp nếu mục tiêu là **một lượt công việc mới ở mỗi tick**.

Ví dụ sai về ý định:

```java
@Scheduled(fixedRate = 1000)
public Mono<String> staleValue() {
    String value = readOnce();
    return Mono.just(value);
}
```

Đây là snippet có method `readOnce`. Có thể subscribe nhiều lần nhưng chỉ phát lại cùng giá trị đã chụp. Nó không lỗi kiểu, nhưng **không tạo fresh work**.

Một `Flux` vô hạn cũng rất dễ gây nhầm:

```java
@Scheduled(fixedRate = 1000)
public Flux<Long> longLivingStream() {
    return Flux.interval(Duration.ofSeconds(1));
}
```

Đây là snippet minh họa, không khuyến nghị. Mỗi tick có thể tạo một subscription vô hạn mới, khiến số subscriptions còn sống tăng cho tới khi context hủy chúng hoặc ứng dụng kiểm soát. Với fixed delay, một nguồn không bao giờ complete có thể khiến scheduling Runnable chặn rất lâu. Hãy dùng Publisher **hữu hạn** cho mỗi lần scheduled tick trừ khi có thiết kế subscription lifetime rõ.

**Một subscriber mới không luôn có nghĩa một side effect mới:** `Mono.just` và `Mono.cache` có thể phát lại; hot/multicast nguồn có thể chia sẻ dữ liệu; `Mono.defer` thực thi supplier mỗi lần subscribe nhưng nội dung supplier vẫn quyết định nó có dùng cache/shared Future hay không.

## 9. Lỗi `onError`, `ErrorHandler` và những lượt sau

Theo Spring 6.1+ reactive scheduling:

- Nếu Publisher phát `onNext`, giá trị này **bị bỏ qua** bởi cơ chế scheduled subscription.
- Nếu Publisher phát `onError`, Spring **log ở mức WARN** và xử lý lỗi trong subscription, không ném exception từ `Runnable` scheduling theo cách giống code đồng bộ.
- Vì vậy `ErrorHandler` của scheduler dành cho `Runnable` **không phải nơi thông thường bắt các tín hiệu `onError` reactive**.
- Các lần scheduled subscriptions **vẫn tiếp tục** sau một `onError`, trừ khi app shutdown/hủy hoặc chính scheduling bị thay đổi.

Ví dụ:

```java
@Scheduled(fixedRate = 1000)
public Mono<Void> maybeFail() {
    return Mono.defer(() -> {
        if (remoteUnavailable()) {
            return Mono.error(new IllegalStateException("Backend unavailable"));
        }
        return Mono.empty();
    });
}
```

Đây là snippet cần `remoteUnavailable` và Spring scheduling setup. Khi một tick phát lỗi, Spring sẽ log, nhưng không tự áp dụng retry backoff. Ở tick sau, `Mono.defer` có thể kiểm tra lại trạng thái backend.

Nếu nghiệp vụ cần retry ngay trong mỗi tick, bạn có thể dùng Reactor retry operators như `retryWhen`, nhưng phải giới hạn số lần, delay, timeout, idempotency. Nếu `retryWhen` kéo dài, nó có thể ảnh hưởng fixed delay và số subscription/in-flight. Cần phân biệt **retry bên trong một scheduled subscription** với **lần subscribe do tick kế tiếp tạo ra**.

Thay vì chỉ tin log scheduler, nên chủ động đo `tick_attempt_total`, `tick_failure_total`, `tick_success_total`, duration, last_success_time, in-flight count và số lần fallback. `doOnError`, `doOnSuccess`, `doFinally` có thể giúp ghi metric phù hợp, nhưng callbacks quan sát không nên ném lỗi ngoài ý muốn hoặc thực hiện blocking I/O dài.

## 10. Fixed delay, fixed rate, cron khi kết quả là Publisher

Spring có một khác biệt vận hành đáng nhớ:

**Fixed delay:** đối với reactive scheduled methods, Spring **block subscription trong Runnable scheduling** cho tới khi Publisher hoàn tất để bảo toàn semantics “nghỉ một khoảng từ sau completion”. Vì vậy một `Mono.never()` hoặc một source I/O lâu có thể giữ scheduler thread và làm các job khác đến hạn phải chờ. **Reactive return type không tự bảo đảm scheduler thread hoàn toàn non-blocking.**

**Fixed rate:** scheduler kích hoạt các subscriptions theo chu kỳ mục tiêu; nếu subscription trước vẫn đang chạy, một subscription mới có thể xảy ra khi lượt cũ chưa hoàn tất. Khi Mono/Flux tạo fresh I/O tại mỗi subscription, nghiệp vụ có thể có **nhiều operations in flight**. Cần bảo vệ downstream khỏi overload.

**Cron:** thời điểm subscription theo lịch calendar và cấu hình timezone. Nếu subscriber cũ còn hoạt động, hãy xem xét overlap giống fixed rate; không coi mỗi tick là đã kết thúc operation cũ.

Tóm tắt:

| Loại lịch | Tương quan với Publisher | Rủi ro chính |
| --- | --- | --- |
| `fixedDelay` | Chờ completion của subscription rồi mới áp dụng delay. | Block scheduler worker nếu Publisher không complete hoặc quá chậm. |
| `fixedRate` | Nhắm nhịp các scheduled subscriptions. | Các subscriptions và I/O có thể overlap khi xử lý lâu. |
| `cron` | Kích hoạt theo mốc lịch. | Overlap, missed triggers, timezone và nhiều replicas. |
| `initialDelay` | Trì hoãn lần đăng ký theo lịch; với các loại lịch lặp, chỉ điều chỉnh lượt đầu. | Nhầm lẫn với business readiness hoặc đúng một lần toàn hệ thống. |

Nếu bắt buộc không overlap, có thể dùng fixed delay với timeout thật ở pipeline để giới hạn worker chờ, hoặc quản lý một in-flight flag/job state cho fixed rate; không nên chỉ hy vọng remote system luôn trả nhanh.

## 11. Một phương án thuần CompletableFuture nhưng không dùng reactive return

Nếu ứng dụng đã dùng `@Async` hoặc async client trả `CompletableFuture`, có thể dùng **scheduled method đồng bộ trả `void`** để **khởi tạo Future mới ở mỗi tick**, rồi tự xử lý completion:

```java
@Component
public class FutureScheduleTick {
    private final RemoteClient client;

    public FutureScheduleTick(RemoteClient client) {
        this.client = client;
    }

    @Scheduled(fixedRate = 1000)
    public void tick() {
        CompletableFuture<String> request = client.fetchAsync();
        request.whenComplete((value, error) -> {
            if (error != null) {
                logger.warn("Fetch failed", error);
            } else {
                metrics.record(value);
            }
        });
    }
}
```

Đây là **mã kiến trúc**, không phải project độc lập: `RemoteClient`, logger, metrics, imports, executor/timeout và Spring setup cần được định nghĩa. Trong mô hình này, mỗi lần scheduler gọi `tick()` là một method invocation bình thường, nên `client.fetchAsync()` được gọi lại. Spring **không** coi Future được tạo là return value reactive scheduled method, vì method trả `void`.

Nhưng chính bạn phải quản lý:

- Nếu tick chỉ submit Future rồi return, fixed delay/fixed rate của scheduled method phản ánh **thời gian submit**, không phản ánh **thời gian Future hoàn thành**.
- Các requests có thể overlap và làm bão hòa network/DB/client pool.
- `whenComplete` là observer, không tự retry/rollback hoặc biến request thành idempotent.
- Exception có thể xảy ra ngay khi gọi `fetchAsync()` trước khi Future được trả; cần xử lý synchronous submit failure.
- Khi context shutdown, không mặc định Future đã gửi được hủy hoặc drain đúng cách nếu không quản lý lifecycle.

Đây là phương án hợp lệ khi team muốn ở lại JDK Future API; không bắt buộc mọi scheduled job đều return Reactor Mono.

## 12. `Mono.fromFuture(...)` và cancellation

`Mono.fromFuture` chuyển completion signal của Future thành `onNext`/`onComplete` hoặc `onError` của một Mono. Khi cùng một Future được nhiều subscriptions quan sát, chúng có chung computation; mỗi subscription không tái khởi động Future.

Với mẫu deferred:

```java
@Scheduled(fixedRate = 1000)
public Mono<String> fetch() {
    return Mono.defer(() ->
        Mono.fromFuture(service.callAsync())
    );
}
```

mỗi subscription tạo một Future mới **nếu service method thực sự tạo task mới**. Nếu subscription bị cancel do shutdown hay timeout, Reactor có cơ chế cancellation liên quan tới Future theo overload và cấu hình; nhưng **việc cancel Future không nhất thiết dừng computation nền**. Ví dụ `CompletableFuture.cancel(true)` không có nghĩa luôn interrupt mọi thread đang xử lý. HTTP request, JDBC transaction hoặc side effect có thể tiếp tục.

Với các operation có tác động dữ liệu, cần:

1. Timeout thực ở HTTP client/DB, không chỉ ở subscriber.
2. Idempotency key và thông tin trạng thái để tránh duplicate nếu retry hoặc tick overlap.
3. Giới hạn concurrent in-flight operations.
4. Cleanup resource trong operator `doFinally`/resource management thích hợp.
5. Chính sách graceful shutdown, xử lý các subscriptions đang chạy.

Một `Mono.timeout(Duration)` có thể ngừng chờ một reactive result nhưng không tự đảm bảo remote operation đã rollback. Tách bạch **subscription lifetime** với **business side effect lifetime**.

## 13. Threading: lazy subscription không đồng nghĩa không blocking

`Mono.defer` trì hoãn **khi nào** công việc được tạo, không tự tạo worker khác để chạy một đoạn blocking.

Ví dụ:

```java
Mono<String> source =
    Mono.fromCallable(() -> slowJdbcQuery());
```

Đây là snippet giả định `slowJdbcQuery` blocking. Nếu subscribe trên scheduler thread, JDBC call có thể chiếm thread đó; với fixed delay, Spring đã phải chờ completion.

Để tách blocking operation sang executor chuyên cho Reactor:

```java
Mono<String> source =
    Mono.fromCallable(() -> slowJdbcQuery())
        .subscribeOn(Schedulers.boundedElastic());
```

Đây là snippet Reactor cần import `Schedulers`; `boundedElastic` không phải công cụ tự động chữa overload. Phải giới hạn concurrency, connection pool, queue, timeout và hiểu bối cảnh Java 17 so với virtual thread options trên JDK mới hơn.

Với reactive HTTP client nonblocking, nên dùng `Mono`/`Flux` từ client trực tiếp, không gọi `.block()` để biến reactive trở lại blocking. Tránh `CompletableFuture.join()` trong `Mono.defer` vì có thể chặn scheduler worker, làm tăng latency hoặc deadlock nếu downstream cần cùng pool.

ThreadLocal của caller không tự theo giữa scheduler worker, Reactor event loop và worker blocking. Dùng Reactor Context/observability propagation có chủ đích, không copy servlet request, transaction resource hoặc security state mutable sang async chain.

## 14. Reactive transaction và công việc theo lịch

`@Scheduled` reactive không tự biến nhiều side effects thành một transaction. Nếu dùng R2DBC/reactive database, transaction boundaries thường gắn với **Reactor Context** và phải được áp dụng vào Publisher mới cho từng subscription, ví dụ `TransactionalOperator` hoặc service reactive có transaction advice phù hợp.

Nếu dùng JDBC/JPA imperative, transaction thường gắn thread và không được tự copy qua Reactor scheduler. Một `Mono.fromCallable` chạy JDBC trên boundedElastic có thể dùng service `@Transactional` được gọi **qua Spring proxy** đúng thread, hoặc `TransactionTemplate` quanh block dữ liệu, nhưng cần tránh gọi lại method nội bộ `this.write()` và hiểu lock/connection lifetime.

Đối với nghiệp vụ quan trọng, một cron/fixed-rate schedule trên nhiều instances không bảo đảm exactly-once. Mỗi replica có thể subscribe riêng; worker có thể retry, crash hoặc nhận kết quả muộn. Cần job identity, trạng thái bền vững, idempotency, unique constraint hoặc cơ chế distributed coordination nếu chỉ một actor được phép xử lý.

## 15. Các lỗi phổ biến khi chọn kiểu trả về

| Sai lầm | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| “CompletableFuture đã có adapter nên chắc dùng được cho reactive @Scheduled.” | Adapter còn phải `isDeferred() == true`; mặc định với CompletableFuture là false. | Return `Mono`/Publisher deferred hoặc dùng scheduled `void` tự quản lý Future. |
| “Spring sẽ gọi lại method trả Publisher ở mỗi tick.” | Spring lấy Publisher một lần rồi subscribe lại. | Đặt per-tick work trong `Mono.defer`/`fromRunnable`/`fromCallable`. |
| “Mono.fromFuture(future) luôn tạo task mới khi subscribe.” | Nếu future đã tạo, mọi subscription quan sát cùng future. | Trì hoãn **việc tạo future** bằng `Mono.defer(() -> Mono.fromFuture(service.callAsync()))`. |
| “Mono.just(service.call()) là lazy.” | `service.call()` chạy lúc tạo Mono. | Dùng `Mono.fromSupplier`/`defer` cho công việc mới. |
| “Mono.cache sẽ refresh ở mỗi tick.” | Nó có thể replay completion đã cache. | Không cache nếu cần công việc fresh. |
| “Mono.defer tự bảo đảm code blocking chạy thread riêng.” | Defer chỉ trì hoãn việc gọi supplier. | Dùng nonblocking client hoặc `subscribeOn`/executor phù hợp. |
| “Fixed delay reactive không bao giờ chặn scheduler thread.” | Spring chờ subscription completion để giữ fixed-delay semantics. | Đặt deadline, kiểm soát duration và pool. |
| “Reactive scheduled onError sẽ đi qua ErrorHandler của Runnable.” | onError được xử lý theo reactive path, không phải Throwable ném trực tiếp từ Runnable. | Giám sát `doOnError` và log/metric phù hợp. |
| “onNext của Mono sẽ đến một caller.” | Spring bỏ qua emitted values của reactive scheduled method. | Lưu kết quả qua service/state/metric. |
| “Future cancel nghĩa network/DB đã rollback.” | Cancellation thường best effort và tách khỏi side effects. | Idempotency, timeout I/O thật, trạng thái job. |
| “Publisher hot/vô hạn luôn phù hợp cho mỗi tick.” | Subscriptions có thể tích lũy hoặc quan sát cùng hot source. | Dùng finite cold Publisher khi cần một việc trên mỗi tick. |
| “@Async CompletableFuture cũng bị cấm vì reactive @Scheduled cấm.” | Hai hợp đồng khác nhau. | `@Async` có thể trả Future; reactive `@Scheduled` cần Publisher deferred. |
| “Một job per pod = một job trên cả hệ thống.” | Mỗi context scheduling riêng. | Durable job identity và coordination/idempotency. |

## 16. Checklist khi review reactive scheduled method

1. Spring Framework có phải **6.1+** và Reactor/reactive adapter dependencies được khai báo đúng không?
2. Method có **không nhận tham số** và kiểu trả về eligible không?
3. Nếu không trả trực tiếp Publisher, `ReactiveAdapter.getDescriptor().isDeferred()` có thật sự true không?
4. Spring có lấy Publisher **một lần** không? Per-tick work có nằm **bên trong** source tạo mới tại subscription?
5. Có vô tình tạo Future eager trước khi `Mono.defer` không?
6. Nguồn có chịu được **nhiều subscriptions**, không phải one-shot/uncached hot source ngoài ý muốn?
7. `fixedDelay` có thể block tới completion bao lâu? Có timeout thật?
8. Fixed rate/cron có tạo nhiều subscriptions in flight không? Cần guard hoặc concurrency limit?
9. `onNext` không được Spring sử dụng; kết quả nghiệp vụ đã được ghi nhận đúng nơi chưa?
10. `onError` được log WARN; có metrics/alert và retry/backoff độc lập không?
11. `@EnableScheduling` có trong đúng context, scheduler có worker đủ và lifecycle/shutdown đúng không?
12. Nếu dùng `@Async`, đây là invocation từ caller hay reactive scheduled return? Không trộn hai hợp đồng.
13. Có xử lý rejection, cancellation, race, idempotency và duplicate job qua nhiều replicas không?
14. Có context/transaction được thiết kế theo thread và Reactor Context thực tế không?
15. Nếu task cần sống qua restart, có job store hoặc event queue bền vững không?
16. Unit test có xác minh **method gọi một lần** và **subscribe nhiều lần**, thay vì chỉ dựa vào số log không gắn với thời điểm?

## 17. Câu hỏi tự kiểm tra

**Câu 1.** `CompletableFuture` có thể được adapt sang `Publisher` thì có tự động đủ điều kiện làm return type reactive `@Scheduled` không?

**Đáp án:** Không. Spring còn yêu cầu adapter deferred. Adapter mặc định của CompletableFuture có `isDeferred() == false`.

**Câu 2.** Spring reactive `@Scheduled` gọi method trả Publisher bao nhiêu lần theo một registration lặp?

**Đáp án:** Spring lấy Publisher một lần khi đăng ký, sau đó mỗi tick subscribe lại vào Publisher đã có.

**Câu 3.** Vì sao `@Scheduled Mono.fromFuture(fetchAsync())` có thể chỉ gọi backend một lần?

**Đáp án:** `fetchAsync()` chạy khi method được gọi tạo Publisher, trong khi Spring không gọi lại method ở mỗi tick; Mono chỉ quan sát cùng Future.

**Câu 4.** Nếu thay bằng `Mono.defer(() -> Mono.fromFuture(fetchAsync()))`, điều gì khác?

**Đáp án:** `fetchAsync()` được gọi khi subscribe, vì vậy mỗi scheduled subscription có thể tạo Future và operation riêng.

**Câu 5.** `Mono.just(load())` có tương đương `Mono.fromCallable(() -> load())` không?

**Đáp án:** Không. `load()` chạy ngay khi dựng `Mono.just` nhưng ở `fromCallable` được gọi khi subscribe. Với callable blocking cần kiểm soát scheduler/thread.

**Câu 6.** `isDeferred` của một instance CompletableFuture có thay đổi khi Future đã hoàn tất không?

**Đáp án:** Không. Đây là thuộc tính `ReactiveTypeDescriptor` của adapter/loại, không phải cờ trạng thái của một instance.

**Câu 7.** Khi `Mono` của scheduled method phát `onNext`, Spring tự gửi giá trị tới một HTTP caller không?

**Đáp án:** Không. Reactive scheduled subscriptions bỏ qua các emitted values.

**Câu 8.** `onError` của reactive scheduled method có được `TaskScheduler.ErrorHandler` xử lý như lỗi Runnable đồng bộ không?

**Đáp án:** Không theo reactive path thông thường. Spring log WARN và tiếp tục các scheduled subscriptions sau lỗi.

**Câu 9.** Với fixed delay, Spring có thể chờ Publisher hoàn tất bằng cách block subscription để giữ semantics không?

**Đáp án:** Có. Reactive return type không đảm bảo scheduler thread luôn nonblocking.

**Câu 10.** Với fixed rate, nếu subscription cũ chưa xong mà tick mới đến hạn, có thể có overlap không?

**Đáp án:** Có thể có nhiều subscriptions và I/O in flight; phải có chính sách concurrency, timeout và idempotency.

**Câu 11.** `@Async CompletableFuture<T>` có bị cùng hạn chế như reactive `@Scheduled CompletableFuture<T>` không?

**Đáp án:** Không. `@Async` có hợp đồng per-invocation Future hợp lệ; vấn đề deferred return type thuộc reactive scheduling.

**Câu 12.** Chương trình ví dụ chứng minh phương thức trả Publisher gọi một lần như thế nào?

**Đáp án:** Đếm method invocations ngoài Mono.defer và đếm subscriptions bên trong; method calls = 1, subscriptions ít nhất 3.

**Câu 13.** Nếu Spring đã tạo Publisher một lần và Publisher ấy là `Flux.interval` vô hạn, mỗi tick có thể gây gì?

**Đáp án:** Tạo subscriptions sống lâu và có thể chồng lấn/tích lũy; với fixed delay còn có nguy cơ giữ scheduler thread chờ completion mãi.

**Câu 14.** Vì sao vẫn cần idempotency và status store dù dùng Mono.defer đúng?

**Đáp án:** Deferred subscription chỉ điều khiển thời điểm tạo việc, không giải quyết crash, retry, multi-replica duplicates hoặc transaction side effects.

### Ghi nhớ

**Đừng đồng nhất “có thể chuyển sang `Publisher`” với “hỗ trợ deferred subscription”.** Với Spring Framework 6.1+, reactive `@Scheduled` đăng ký **một Publisher** và subscribe lại theo mỗi tick. `CompletableFuture` có adapter nhưng descriptor **không deferred**; nó phù hợp với nhiều API Java async như `@Async` nhưng không nên được dùng làm return type reactive scheduled chỉ vì có thể chuyển đổi. Muốn tạo một Future mới ở mỗi tick, return một `Mono` thực sự deferred như `Mono.defer(() -> Mono.fromFuture(service.callAsync()))`, hoặc chọn `@Scheduled void` tự khởi động Future và theo dõi completion/overload. Luôn xem xét `fixedDelay`, lỗi, hủy, tài nguyên, context, transaction và tác dụng phụ bền vững.

### Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): reactive `@Scheduled`, điều kiện `isDeferred`, ví dụ CompletableFuture không deferred, phương thức tạo Publisher một lần và subscribe lại, handling errors.
- [Spring Framework 6.2 — `@Scheduled` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): Publisher phải hỗ trợ nhiều subscriptions, `onNext` bị bỏ qua và `onError` log WARN.
- [Spring Framework — `ReactiveAdapterRegistry`](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/ReactiveAdapterRegistry.html): adapter lookup và adaptation giữa Future/Publisher.
- [Spring Framework — `ReactiveTypeDescriptor`](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/ReactiveTypeDescriptor.html): định nghĩa `isDeferred()`.
- [Spring Framework — `ScheduledAnnotationReactiveSupport` source](https://github.com/spring-projects/spring-framework/blob/v6.2.12/spring-context/src/main/java/org/springframework/scheduling/annotation/ScheduledAnnotationReactiveSupport.java): kiểm tra `isDeferred` và tái subscription.
- [Project Reactor — Mono API](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html): `defer`, `fromFuture`, `fromCallable`, `fromRunnable`, `cache`, `timeout`.


</details>

- [Quay lại đầu trang](#back-to-top)
