<a id="back-to-top"></a>

# Thực thi tác vụ và @Async

## Menu
- [Hợp đồng TaskExecutor và AsyncTaskExecutor](#task-executor)
- [Các chiến lược và cách triển khai thực thi tác vụ](#task-executor-strategies)
- [Cấu hình, hàng đợi và saturation của ThreadPoolTaskExecutor](#spring-executor-config)
- [@EnableAsync, @Async và proxy dispatch](#async-annotation)
- [AsyncConfigurer, executor mặc định và qualification](#async-executor-selection)
- [Kiểu trả về và cách quan sát lỗi của @Async](#async-return-type)
- [Executable evidence cho Task Execution và @Async](#spring-executor-experiments)

Spring không thay thế Java concurrency primitives. Nó cung cấp abstraction và tích hợp vòng đời để ứng dụng sử dụng các cơ chế đó nhất quán hơn.

```text
Java Executor concepts
        ↓
Spring TaskExecutor / ThreadPoolTaskExecutor
        ↓
@Async proxy dispatch
```

## <a id="task-executor">Hợp đồng TaskExecutor và AsyncTaskExecutor</a>

<details>
<summary>Xem chi tiết</summary>

`TaskExecutor` là contract cốt lõi của Spring cho ý nghĩa "nhận `Runnable` này để thực thi". Phương thức `execute(Runnable)` cố ý gần như trùng với `java.util.concurrent.Executor`; giá trị Spring bổ sung là chiến lược thực thi có thể được cấu hình, inject, thích nghi với môi trường triển khai và được quản lý như hạ tầng của ứng dụng.

`AsyncTaskExecutor` mở rộng mô hình đó với các thao tác phục vụ việc gửi tác vụ bất đồng bộ, bao gồm `Callable` và các handle theo dõi hoàn tất dạng future. Đoạn mã chỉ cần gửi tác vụ rồi tiếp tục có thể phụ thuộc vào `TaskExecutor`; đoạn mã cần quan sát lúc hoàn tất có thể cần contract giàu hơn hoặc API trả future.

`ThreadPoolTaskExecutor` là cách triển khai dạng pool phổ biến trong ứng dụng Spring cục bộ. Bên dưới nó dùng JDK `ThreadPoolExecutor` nhưng cung cấp cấu hình kiểu bean cho core/max pool size, queue capacity, keep-alive, đặt tên thread, task decoration, xử lý rejection và vòng đời.

Mô hình admission vẫn là của JDK:

```text
chưa đủ core worker
→ tạo worker

đã đạt core
→ đưa task vào queue

queue đầy nhưng chưa đạt max
→ tăng worker tới max

queue đầy và đã đạt max
→ reject
```

Spring không thay đổi thuật toán này. Khác biệt tại ranh giới abstraction của Spring là cách component được cấu hình và rejection được biểu lộ ra ngoài. Mã ứng dụng gọi qua `TaskExecutor` nên chuẩn bị cho contract `TaskRejectedException` của Spring thay vì phụ thuộc vào việc `RejectedExecutionException` thô của JDK luôn thoát ra nguyên dạng.

Đường exception đó chỉ xuất hiện khi executor/rejection handler bên dưới thực sự từ chối bằng cách ném exception. Nếu cấu hình JDK `RejectedExecutionHandler` tùy biến thì chính handler quyết định hành vi khi quá tải: khi executor vẫn đang hoạt động, `CallerRunsPolicy` chạy task bị từ chối ngay trên thread submit, còn chính sách như `DiscardPolicy` có thể bỏ task mà không ném exception. Vì vậy rejection handler làm thay đổi ngữ nghĩa thực thi quan sát được, không chỉ đổi loại exception. Với `@Async`, `CallerRunsPolicy` có thể làm target method chạy ngay trên thread bên gọi trước khi proxy return, còn chính sách discard im lặng có thể làm mất async work mà không có exception báo ra.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-executor-strategies">Các chiến lược và cách triển khai thực thi tác vụ</a>

<details>
<summary>Xem chi tiết</summary>

Interface nhỏ giúp nhiều cách triển khai biểu diễn các chiến lược thực thi rất khác nhau:

- `SyncTaskExecutor` chạy task ngay trên caller thread. Nó hữu ích khi không muốn hành vi async, ví dụ một số test, nhưng không tạo concurrency.
- `SimpleAsyncTaskExecutor` tạo thread mới cho mỗi task và không tái sử dụng thread. Spring 6.1 cho phép dùng JDK 21 Virtual Thread; class này còn hỗ trợ giới hạn mức đồng thời và task decoration.
- `ThreadPoolTaskExecutor` dùng `ThreadPoolExecutor` có cấu hình và phù hợp khi ứng dụng cần worker-pool/queue/rejection model rõ ràng.
- `ConcurrentTaskExecutor` thích nghi một JDK `Executor` đã tồn tại để Spring delegate vào hạ tầng đó.
- `DefaultManagedTaskExecutor` delegate tới managed executor service của môi trường Jakarta EE/JSR-236.
- `VirtualThreadTaskExecutor` là lựa chọn virtual-thread-per-task tối giản từ Spring 6.1 và được học sâu ở chương Virtual Thread.

Không chọn cách triển khai chỉ vì tên class quen thuộc. Hãy chọn theo hành vi cần thiết: đồng bộ hay bất đồng bộ, pool hay thread-per-task, thread do ứng dụng hay môi trường quản lý, admission có giới hạn hay không, có cần context decoration và phối hợp vòng đời hay không.

Một cách kiểm tra tốt là thay class name bằng mô tả chính sách. Nếu yêu cầu thật là "pooled execution, queue capacity 500, abort khi overload" thì chính sách đó phải nhìn thấy rõ trong tài liệu cấu hình/vận hành, không nên biến mất sau một biến kiểu `Executor` quá chung.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-executor-config">Cấu hình, hàng đợi và saturation của ThreadPoolTaskExecutor</a>

<details>
<summary>Xem chi tiết</summary>

Module dùng bean riêng tên `threadLearningTaskExecutor` để các thử nghiệm có chính sách thực thi nhỏ, dễ dự đoán:

```java
executor.setCorePoolSize(2);
executor.setMaxPoolSize(4);
executor.setQueueCapacity(8);
executor.setKeepAliveSeconds(30);
executor.setThreadNamePrefix("thread-learning-executor-");
executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
```

Điểm quan trọng không phải các số `2/4/8` mà là quan hệ giữa chúng. Với queue capacity dương, khi core worker đang bận, task mới được đưa vào queue trước. Pool chỉ tăng vượt core khi queue đã đầy. Khi queue đầy và worker đã chạm max, chính sách rejection mới áp dụng.

Điều này dễ gây bất ngờ nếu cấu hình queue rất lớn nhưng lại kỳ vọng `maxPoolSize` thường xuyên được dùng. Queue lớn có thể hấp thụ công việc trong thời gian dài, khiến pool ở gần core size trong khi độ trễ âm thầm tích tụ trong queue.

Chiều ngược lại cũng quan trọng: `queueCapacity = 0` dùng direct hand-off thay vì queue đệm, nên khi core worker đã bận, executor có thể tăng ngay về phía `maxPoolSize`. Hình dạng quá tải thay đổi rất mạnh và cần được chọn có chủ đích.

Sizing cho production phải dựa trên workload và ràng buộc của downstream: arrival rate, thời lượng task, CPU, capacity của DB/HTTP connection, latency SLO, mức queueing chấp nhận được và termination window. Demo cố tình dùng số nhỏ để quan sát semantics, không phải để sao chép sang production.

Việc đưa các **giá trị tinh chỉnh vận hành** ra ngoài code thường hợp lý, nhưng cơ chế binding không thuộc phạm vi sở hữu của module này. Nội dung cũ trước đây dùng Spring Boot `@ConfigurationProperties` làm ví dụ; nguyên tắc tách tuning khỏi business code được giữ lại, còn property binding đặc thù của Boot thuộc curriculum Spring Boot.

Không nên đưa mọi chính sách thành property không kiểm soát. Rejection strategy, hard safety limit và hành vi context/lifecycle có thể thay đổi ngữ nghĩa của hệ thống nên cần invariant và validation ở cấp ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-annotation">@EnableAsync, @Async và proxy dispatch</a>

<details>
<summary>Xem chi tiết</summary>

`@EnableAsync` đăng ký hạ tầng cho việc thực thi phương thức bất đồng bộ của Spring. Với `AdviceMode.PROXY` mặc định, Spring áp dụng async interceptor cho những lời gọi đủ điều kiện và thực sự đi qua proxy. `@Async` đánh dấu phương thức hoặc class có lời gọi cần được gửi vào executor.

```text
caller
→ Spring proxy intercept @Async
→ resolve executor
→ submit method invocation
→ caller lấy lại quyền điều khiển / nhận completion handle
→ worker chạy target method
```

Annotation này không phải tính năng của ngôn ngữ Java và không tự biến lời gọi trực tiếp thành bất đồng bộ. Đây là lý do self-invocation là pitfall quan trọng: `this.otherAsyncMethod()` vẫn chạy bên trong target object và thông thường bỏ qua proxy, nên proxy-mode async interception không xảy ra.

Thiết kế rõ ràng thường đặt ranh giới async giữa hai Spring bean cộng tác. AspectJ advice mode có thể xử lý lời gọi nội bộ khác đi, nhưng cơ chế proxy/weaving sâu thuộc module Spring AOP.

Cũng không nên dùng `@Async` như marker chung có nghĩa "làm nhanh hơn". Nó thay đổi luồng điều khiển, cách quan sát lỗi, ranh giới thread/context, giả định về transaction và hành vi shutdown. Bên gọi phải được thiết kế để hiểu các hệ quả đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-executor-selection">AsyncConfigurer, executor mặc định và qualification</a>

<details>
<summary>Xem chi tiết</summary>

Thực thi async cần quy tắc chọn executor. Hai cách chính ở cấp ứng dụng:

- cung cấp executor mặc định thông qua `AsyncConfigurer#getAsyncExecutor()` hoặc dùng cơ chế phân giải executor mặc định của Spring;
- chỉ định executor cho một phương thức bằng `@Async("beanNameOrQualifier")` khi workload đó cần chính sách riêng.

Nếu không có executor do `AsyncConfigurer` chỉ định, hạ tầng async của Spring tìm executor mặc định phù hợp trong context, thông thường ưu tiên một `TaskExecutor` duy nhất và sau đó là `Executor` bean tên `taskExecutor`. Nếu cả hai đều không phân giải được, Spring fallback sang `SimpleAsyncTaskExecutor`. Ứng dụng production vẫn nên cấu hình chính sách thực thi mong muốn một cách tường minh thay vì coi fallback đó là quyết định sizing.

Module hiện chọn executor trực tiếp trên phương thức dùng cho bài học:

```java
@Async("threadLearningTaskExecutor")
public CompletableFuture<Map<String, Object>> runAsync(...) { ... }
```

`TaskExecutorConfig` triển khai `AsyncConfigurer` để cung cấp `AsyncUncaughtExceptionHandler`, nhưng không override `getAsyncExecutor()`. Vì vậy phương thức demo dùng qualifier của `@Async` để chọn `threadLearningTaskExecutor` một cách tường minh.

Chỉ tách nhiều executor khi workload thật sự cần chính sách khác nhau, ví dụ task nhỏ nhạy với độ trễ và task tích hợp block lâu. Mỗi executor mới đều tạo thêm trách nhiệm về capacity, vòng đời, metrics và tuning; không nên tạo chỉ để "gắn nhãn" code.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-return-type">Kiểu trả về và cách quan sát lỗi của @Async</a>

<details>
<summary>Xem chi tiết</summary>

Contract của Spring yêu cầu phương thức `@Async` khai báo return type là `void` hoặc kiểu tương thích với Future như `Future<T>`/`CompletableFuture<T>`. Lựa chọn này quyết định bên gọi quan sát việc hoàn tất và lỗi bằng cách nào.

Với kiểu trả về dạng future, proxy trả cho bên gọi handle bất đồng bộ dùng để theo dõi việc hoàn tất. Target method vẫn phải tuân thủ Java signature nên bên trong nó cũng trả một Future của riêng mình — thường là Future đã hoàn tất chứa kết quả của method — nhưng Future của target không phải chính handle được trả trực tiếp cho bên gọi. Interceptor gửi lời gọi method vào executor đã chọn rồi trả handle bất đồng bộ do executor quản lý. Bên gọi vẫn phải **thực sự quan sát** handle đó bằng `get`, `join`, composition, callback hoặc tiếp tục trả nó qua một ranh giới async khác. Bỏ qua future có thể khiến lỗi thật trở nên vô hình ở cấp vận hành.

Với `void`, không có handle theo dõi việc hoàn tất để trả. Nếu thân phương thức ném exception sau khi đã dispatch, Spring chuyển uncaught exception tới `AsyncUncaughtExceptionHandler`. Module dùng `AsyncExceptionProbe` làm minh chứng:

```java
@Async("threadLearningTaskExecutor")
public void failWithoutFuture(String correlationId) {
    throw new IllegalStateException("async void failure: " + correlationId);
}
```

Endpoint `/spring-executor/void-exception` đăng ký một future quan sát theo correlation id trước khi gọi proxy. Hai nhánh được tách rõ:

```text
submit thành công
→ worker chạy method
→ method throw
→ AsyncUncaughtExceptionHandler quan sát failure

submit bị reject
→ failure xảy ra đồng bộ tại proxy/executor boundary
→ method body chưa từng chạy
```

`AsyncUncaughtExceptionHandler` xử lý uncaught exception của `void @Async` sau dispatch; nó không phải rejection handler. Nếu luồng nghiệp vụ cần biết thành công/thất bại, kiểu trả về theo dõi completion thường rõ ràng hơn `void` kiểu fire-and-forget.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-executor-experiments">Executable evidence cho Task Execution và @Async</a>

<details>
<summary>Xem chi tiết</summary>

Phần TaskExecutor giữ ba endpoint nhỏ như **minh chứng có thể chạy được**, không phải khuyến nghị cho production hay load benchmark:

| Trọng tâm Knowledge | Controller method | Endpoint | Điều chứng minh |
| --- | --- | --- | --- |
| Context + executor dispatch | `TaskExecutorController#asyncWithContext()` | `GET /spring-executor/async-context` | bên gọi và worker khác nhau; custom executor và decorator thực sự tham gia |
| Lỗi của `void @Async` | `TaskExecutorController#asyncVoidException()` | `GET /spring-executor/void-exception` | lỗi trong thân phương thức đi tới handler; rejection khi gửi tác vụ là nhánh khác |
| Admission và saturation của pool | `TaskExecutorController#saturation()` | `GET /spring-executor/saturation` | quan sát được chuỗi cô lập `core → queue → tăng tới max → reject` mà không gây saturation cho executor của ứng dụng |

Thử nghiệm đầu thuộc chủ yếu về chương Context Propagation vì minh chứng quan trọng là dữ liệu nào vượt qua ranh giới thread. Thử nghiệm thứ hai thuộc `async-return-type` vì nó làm cách quan sát lỗi trở nên nhìn thấy được. Thử nghiệm thứ ba map tới `spring-executor-config` vì nó làm chuỗi admission theo cấu hình có thể quan sát trực tiếp.

Các endpoint này không trả lời câu hỏi sizing cho production. Chúng dùng cấu hình nhỏ, dễ dự đoán để semantics dễ quan sát. Việc tuning pool vẫn cần workload test, metrics và phân tích capacity của downstream.

</details>

- [Quay lại đầu trang](#back-to-top)
