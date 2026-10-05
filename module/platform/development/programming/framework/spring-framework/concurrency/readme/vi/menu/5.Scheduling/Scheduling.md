<a id="back-to-top"></a>

# Lập lịch khai báo với @Scheduled

## Menu
- [@EnableScheduling và cơ chế đăng ký scheduled method](#scheduled-infrastructure)
- [Lập lịch định kỳ và one-time: fixed delay, fixed rate, cron và initial delay](#scheduled-trigger-modes)
- [Cron expression, time zone và time unit](#scheduled-cron-zone-timeunit)
- [Chọn scheduler cho @Scheduled](#scheduled-scheduler-qualifier)
- [Repeatable schedule và khả năng overlap](#scheduled-overlap)
- [Hành vi khi scheduled task thất bại](#scheduled-failure)
- [Reactive @Scheduled method](#reactive-scheduled-methods)
- [SchedulingConfigurer và đăng ký nâng cao](#scheduling-configurer)

## <a id="scheduled-infrastructure">@EnableScheduling và cơ chế đăng ký scheduled method</a>

<details>
<summary>Xem chi tiết</summary>

`@EnableScheduling` kích hoạt hạ tầng lập lịch dựa trên annotation của Spring. Bên dưới, `ScheduledAnnotationBeanPostProcessor` phát hiện các phương thức có `@Scheduled` và đăng ký task với scheduler.

Mô hình tư duy này khác `@Async`. Không cần một bên gọi gọi phương thức qua proxy ở mỗi lần kích hoạt. Container phát hiện metadata lập lịch khi xử lý bean rồi tạo đăng ký lịch chạy.

Phương thức `@Scheduled` đồng bộ thông thường không nhận tham số. Giá trị trả về nếu có sẽ bị bỏ qua; contract hữu ích là side effect thực hiện ở mỗi lần chạy. Phương thức reactive là ngoại lệ từ Spring 6.1 vì Spring lập lịch các lần subscribe lặp lại vào Publisher mà phương thức trả về.

Nếu không cấu hình scheduler tường minh, hạ tầng lập lịch tìm scheduler phù hợp trong context, ưu tiên một `TaskScheduler` duy nhất hoặc bean name theo quy ước `taskScheduler`, đồng thời có thể dùng `ScheduledExecutorService`. Nếu không có bean phù hợp, annotation processor có thể tạo scheduler mặc định cục bộ chỉ có một thread.

Fallback đó tiện cho việc khởi động, không phải khuyến nghị sizing cho production. Khi công việc đã lên lịch có thể block hoặc overlap, hãy định nghĩa scheduler rõ ràng để chính sách thực thi/vòng đời nhìn thấy được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-trigger-modes">Lập lịch định kỳ và one-time: fixed delay, fixed rate, cron và initial delay</a>

<details>
<summary>Xem chi tiết</summary>

`@Scheduled` hỗ trợ cả đăng ký định kỳ và đăng ký chạy một lần.

Với công việc định kỳ, chọn một mô hình trigger chính:

- `fixedDelay` — khoảng delay của lần sau được tính sau khi lần gọi trước hoàn thành.
- `fixedRate` — các lần chạy được lập lịch theo khoảng thời gian cố định giữa các thời điểm bắt đầu dự kiến.
- `cron` — chạy theo Spring cron expression và time zone tùy chọn.

`initialDelay` trì hoãn lần kích hoạt đầu. Từ Spring Framework 6.1, `initialDelay` còn có thể đứng **một mình**, không cần fixed delay/fixed rate/cron, để tạo scheduled task chạy một lần.

```java
@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
void pollAfterCompletion() { ... }

@Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
void sampleEveryMinute() { ... }

@Scheduled(initialDelay = 10, timeUnit = TimeUnit.SECONDS)
void runOnceAfterStartupDelay() { ... }
```

Không suy ra overlap hay không overlap chỉ từ `fixedRate`. Với `ThreadPoolTaskScheduler` truyền thống, một đăng ký định kỳ kế thừa ngữ nghĩa của `ScheduledThreadPoolExecutor`, nên các lần fixed-rate kế tiếp của chính đăng ký đó không chạy chồng lên nhau; nếu một lần chạy quá lâu thì các lần sau bị trễ. Với `SimpleAsyncTaskScheduler`, các lần fixed-rate thường được hand-off sang những execution thread riêng nên có thể overlap. Các đăng ký riêng biệt — bao gồm nhiều `@Scheduled` lặp trên cùng phương thức — độc lập với nhau và vẫn có thể overlap.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-cron-zone-timeunit">Cron expression, time zone và time unit</a>

<details>
<summary>Xem chi tiết</summary>

Spring cron expression có sáu trường, bao gồm **seconds**:

```text
second minute hour day-of-month month day-of-week
```

Ví dụ `0 0 9 * * MON-FRI` nghĩa là 09:00 các ngày trong tuần theo scheduler clock/time zone tương ứng. Attribute `zone` cho phép khai báo cron dùng time zone tường minh thay vì zone mặc định của scheduler.

Các giá trị số của `fixedDelay`, `fixedRate` và `initialDelay` dùng attribute `timeUnit`; mặc định là milliseconds:

```java
@Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
```

Các biến thể dạng chuỗi có thể được đưa ra cấu hình bên ngoài và, nơi được hỗ trợ, có thể dùng giá trị kiểu duration. Attribute `timeUnit` bị bỏ qua đối với cron expression và đối với duration string đã mang unit riêng. Đơn vị nên đủ tường minh để giá trị như "30" không bị người vận hành hiểu là seconds trong khi thực tế là milliseconds.

Cron còn có quy ước hữu ích: `Scheduled.CRON_DISABLED` có giá trị `"-"`. Placeholder có thể resolve thành marker đó để vô hiệu hóa cron trigger mà không xóa annotation. Đây là tiện ích cho việc bật/tắt bằng cấu hình, nhưng trạng thái vận hành vẫn phải cho thấy job đang bị vô hiệu hóa.

Time zone, chuyển đổi daylight-saving và lịch nghiệp vụ là yêu cầu thực, không phải chi tiết định dạng. Job có ý nghĩa pháp lý hoặc thời gian nghiệp vụ cần kiểm thử rõ các chuyển đổi lịch này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-scheduler-qualifier">Chọn scheduler cho @Scheduled</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework 6.1 bổ sung attribute `scheduler` cho `@Scheduled`. Nó cho phép một phương thức đã lên lịch chọn scheduler cụ thể bằng qualifier hoặc bean name thay vì dùng cơ chế chọn scheduler mặc định.

```java
@Scheduled(
    fixedRate = 10,
    timeUnit = TimeUnit.SECONDS,
    scheduler = "latencySensitiveScheduler"
)
void refreshFastPath() { ... }
```

Qualifier được đối chiếu với một bean `TaskScheduler` hoặc `ScheduledExecutorService` cụ thể theo qualifier value hoặc bean name. Khả năng này hữu ích khi workload thật sự cần chính sách khác nhau, ví dụ job nhỏ nhạy với độ trễ không nên chia scheduler thread với maintenance job block lâu.

Giữ lý do ở mức kiến trúc. Nhiều scheduler đồng nghĩa nhiều tài nguyên thread, chính sách vòng đời, bề mặt quan sát và cấu hình capacity. Tạo một scheduler cho mỗi phương thức chỉ để có thread name khác thường làm vận hành phức tạp hơn mà không tạo isolation có giá trị.

`scheduler` rỗng nghĩa là dùng cơ chế chọn mặc định thông thường. Nếu phương thức cần chính sách riêng vì tính đúng đắn hoặc độ trễ, hãy làm dependency đó tường minh và đặt tên scheduler theo chính sách thay vì chi tiết cách triển khai ngẫu nhiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-overlap">Repeatable schedule và khả năng overlap</a>

<details>
<summary>Xem chi tiết</summary>

`@Scheduled` là repeatable annotation. Nhiều `@Scheduled` trên cùng một phương thức được xử lý độc lập, và Spring cho phép các lịch đó overlap hoặc chạy sát nhau.

Overlap có thể xuất hiện khi:

- `SimpleAsyncTaskScheduler` dispatch lần fixed-rate/cron tiếp theo trong khi lần thực thi trước vẫn đang chạy;
- nhiều bean instance cùng đăng ký scheduled method;
- nhiều instance của ứng dụng cùng chạy in-process schedule;
- phương thức có nhiều khai báo cron/rate độc lập.

Ngược lại, tăng pool size của `ThreadPoolTaskScheduler` **không** làm các lần kế tiếp của cùng một đăng ký fixed-rate/fixed-delay chồng lên nhau; contract periodic task của JDK bảo đảm các lần chạy đó diễn ra tuần tự. Pool scheduler lớn hơn cho phép các đăng ký scheduled khác nhau chạy đồng thời.

Vì vậy `@Scheduled` **không** phải distributed lock hay single-flight guarantee.

Nếu overlap không an toàn, phải chọn cơ chế bảo vệ tường minh: làm job idempotent, serialize bằng single-thread scheduler khi đủ, dùng lock ở cấp ứng dụng, hoặc dùng cơ chế distributed scheduling/locking khi nhiều process cùng tham gia.

Cũng cần cẩn thận với stateful singleton bean. Hai lần gọi đồng thời của cùng scheduled method có thể truy cập chung field mutable. Thread safety vẫn là trách nhiệm Java Concurrency; Spring scheduling không tự làm mutable state an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-failure">Hành vi khi scheduled task thất bại</a>

<details>
<summary>Xem chi tiết</summary>

Lỗi của scheduled task cần chính sách vừa giữ các lần lập lịch sau vừa không che mất vấn đề vận hành.

Với `Runnable` chạy lặp, chiến lược xử lý lỗi mặc định của Spring ghi log rồi suppress lỗi để các lần chạy sau vẫn tiếp tục. Task chạy một lần có thể truyền lỗi qua future hoặc đường thực thi bên dưới. `ThreadPoolTaskScheduler` còn cho phép cấu hình `ErrorHandler` tùy biến khi ứng dụng cần chính sách khác.

Hành vi mặc định này không đồng nghĩa "retry nghiệp vụ vừa thất bại ngay lập tức". Lần kích hoạt theo lịch tiếp theo không nhất thiết là retry của cùng một công việc logic. Nếu job thay đổi trạng thái bên ngoài, ngữ nghĩa retry cần idempotency, phát hiện trùng lặp, backoff và quyết định nghiệp vụ riêng.

```text
scheduled callback ném lỗi
→ ErrorHandler / chính sách lỗi của scheduler quan sát
→ đăng ký lặp thông thường vẫn đủ điều kiện cho lần sau
```

Không nên catch `Exception` trong mọi job rồi im lặng tiếp tục chỉ để lịch chạy sống. Hãy xử lý những lỗi mà job có đường phục hồi rõ ràng và để lỗi bất ngờ đi vào kênh xử lý lỗi của scheduler.

Reactive `@Scheduled` có đường xử lý lỗi khác: Publisher `onError` được ghi log rồi phục hồi để các subscription tương lai tiếp tục; scheduler `ErrorHandler` thông thường không phải kênh lỗi reactive.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-scheduled-methods">Reactive @Scheduled method</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework 6.1 cho phép phương thức `@Scheduled` trả reactive `Publisher` hoặc kiểu mà Spring có thể chuyển sang Publisher **với ngữ nghĩa subscription trì hoãn (deferred subscription)**.

Vòng đời này rất dễ bị hiểu sai. Spring lấy Publisher từ phương thức một lần, sau đó lập lịch việc **subscribe lại** Publisher đó theo trigger:

```text
quá trình xử lý bean
→ gọi scheduled method để lấy Publisher
→ đăng ký lịch
      ↓ mỗi lần kích hoạt
   subscribe lại
```

Vì vậy Publisher phải an toàn cho việc subscribe lặp lại và nên trì hoãn công việc thật tới thời điểm subscribe. Kiểu bất đồng bộ có adapter không hỗ trợ deferred subscription — điển hình `CompletableFuture` — không phù hợp với contract reactive scheduled này.

Giá trị `onNext` bị bỏ qua vì scheduling dùng Publisher để kích hoạt công việc chứ không tiêu thụ result stream. Nếu subscription kết thúc bằng `onError`, Spring ghi log WARN rồi phục hồi để các scheduled subscription sau vẫn diễn ra; scheduler `ErrorHandler` thông thường không tham gia đường xử lý lỗi reactive.

Với fixed-delay reactive scheduling, Spring block subscription để giữ ngữ nghĩa "delay sau khi hoàn tất". Khi context shutdown, Spring hủy scheduled task và các reactive subscription đang hoạt động liên quan.

Phần này chỉ sở hữu ranh giới lập lịch của Spring. Deferred execution, backpressure, Reactor Context và thiết kế Publisher thuộc Reactive Programming/Spring Reactive.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduling-configurer">SchedulingConfigurer và đăng ký nâng cao</a>

<details>
<summary>Xem chi tiết</summary>

Triển khai `SchedulingConfigurer` khi cấu hình annotation mặc định không đủ và ứng dụng cần truy cập trực tiếp `ScheduledTaskRegistrar` dùng cho việc đăng ký.

Lớp cấu hình có thể dùng registrar để:

- chọn/cấu hình scheduler;
- thêm fixed-rate, fixed-delay, cron hoặc `Trigger` tùy biến bằng code;
- đăng ký lịch có thời điểm tính động;
- cấu hình `ObservationRegistry` cho scheduled-task observation của Spring 6.1.

```java
@Configuration
@EnableScheduling
class SchedulingConfig implements SchedulingConfigurer {
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setScheduler(taskScheduler());
        registrar.addTriggerTask(this::runJob, triggerContext -> nextTime(triggerContext));
    }
}
```

Đây là abstraction phù hợp khi chính việc đăng ký lịch là logic của ứng dụng. Nó cũng là cầu nối tự nhiên cho việc lập lịch lại ở runtime vì ứng dụng có thể giữ handle `ScheduledTask`/`ScheduledFuture` thay vì giả định attribute của annotation có thể thay đổi tùy ý.

Tránh đăng ký cùng một job logic bằng cả `@Scheduled` và registrar dùng bằng code nếu việc lập lịch trùng không phải chủ ý. Registrar là hạ tầng; logic nghiệp vụ vẫn nên nằm trong service thông thường mà scheduled callback gọi tới.

</details>

- [Quay lại đầu trang](#back-to-top)
