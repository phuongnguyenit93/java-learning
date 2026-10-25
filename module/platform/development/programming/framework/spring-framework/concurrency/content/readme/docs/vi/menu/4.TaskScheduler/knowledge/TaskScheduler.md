<a id="back-to-top"></a>

# TaskScheduler và lập lịch bằng API

## Menu
- [Vì sao Spring cung cấp TaskScheduler?](#task-scheduler-purpose)
- [One-time, fixed-rate, fixed-delay và trigger scheduling](#scheduling-time-model)
- [Trigger và TriggerContext](#trigger-context)
- [Các cách triển khai TaskScheduler và môi trường triển khai](#task-scheduler-implementations)
- [Mô hình scheduler thread và execution thread](#scheduler-execution-models)
- [Lập lịch lại và hủy task tại runtime](#runtime-rescheduling-cancellation)
- [Pause, resume, shutdown và termination](#scheduler-lifecycle)
- [Khi TaskScheduler là đủ và khi cần Quartz](#quartz-boundary)

## <a id="task-scheduler-purpose">Vì sao Spring cung cấp TaskScheduler?</a>

<details>
<summary>Xem chi tiết</summary>

`TaskScheduler` là abstraction của Spring cho công việc mà thời điểm đủ điều kiện chạy phụ thuộc vào thời gian. Nó bổ sung cho `TaskExecutor`: executor trả lời "công việc đã sẵn sàng sẽ được thực thi thế nào?", còn scheduler thêm câu hỏi "khi nào công việc đó được phép chạy?".

Hợp đồng của Spring 6.1 dùng `Instant` và `Duration` thay vì buộc ứng dụng thao tác trực tiếp với số mili giây:

```java
ScheduledFuture<?> schedule(Runnable task, Instant startTime);
@Nullable
ScheduledFuture<?> schedule(Runnable task, Trigger trigger);
ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period);
ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay);
```

`TaskScheduler#getClock()` cung cấp clock dùng cho tính toán lập lịch. Điều này quan trọng với logic của trigger và khả năng kiểm thử vì khái niệm "bây giờ" là một phần của mô hình lập lịch.

Tương tự `TaskExecutor`, abstraction này tách ý định lập lịch khỏi cơ chế triển khai. Ứng dụng độc lập có thể dùng local thread-pool scheduler; ứng dụng Jakarta EE có thể delegate sang managed scheduled executor; code chỉ phụ thuộc `TaskScheduler` vẫn giữ nguyên ý định lập lịch.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduling-time-model">One-time, fixed-rate, fixed-delay và trigger scheduling</a>

<details>
<summary>Xem chi tiết</summary>

Các chính sách lập lịch khác nhau ở **quan hệ thời gian mà chúng cố giữ**:

- **One-time** chạy một lần tại hoặc sau một `Instant`.
- **Fixed rate** nhắm tới nhịp đều giữa các thời điểm bắt đầu đã được lên lịch.
- **Fixed delay** chờ một lần thực thi hoàn thành rồi mới đợi thêm khoảng delay trước lần tiếp theo.
- **Trigger-based** yêu cầu `Trigger` tính `Instant` kế tiếp dựa trên lịch sử thực thi.

```text
fixed rate
scheduled start ── period ── scheduled start

fixed delay
start → run → complete ── delay ── next start
```

Chọn dựa trên ngữ nghĩa nghiệp vụ, không dựa trên tên phương thức ngắn hơn. Polling cần nghỉ sau mỗi lần hoàn thành phù hợp fixed-delay. Yêu cầu "chạy theo lịch trên đồng hồ" thường hợp cron/trigger. Lấy mẫu telemetry mỗi N giây có thể hợp fixed-rate, nhưng khi một lần chạy kéo dài thì phải xét cùng mô hình thực thi của scheduler cụ thể.

Chính sách thời gian và capacity thực thi là hai chuyện riêng. Với `ThreadPoolTaskScheduler`, `ScheduledThreadPoolExecutor` bên dưới không cho các lần chạy kế tiếp của **cùng một** đăng ký fixed-rate hoặc fixed-delay chồng lấn nhau; một lần chạy chậm sẽ làm các lần sau bị trễ. `SimpleAsyncTaskScheduler` khác với fixed-rate/cron vì mỗi lần kích hoạt thường được hand-off sang execution thread riêng, nên lần trước vẫn có thể đang chạy khi lần sau được dispatch. Các đăng ký độc lập cũng có thể overlap với nhau. Vì vậy phải phân tích timing cùng scheduler được chọn, không coi fixed rate tự thân là bảo đảm về parallelism.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="trigger-context">Trigger và TriggerContext</a>

<details>
<summary>Xem chi tiết</summary>

`Trigger` là extension point cho lịch chạy không thể diễn tả bằng rate hoặc delay cố định. Trong Spring 6.1, nó tính lần chạy tiếp theo dưới dạng `Instant`:

```java
@Nullable
Instant nextExecution(TriggerContext context);
```

`TriggerContext` cung cấp lịch sử lập lịch:

- `lastScheduledExecution()` — lần trước đáng lẽ được schedule lúc nào;
- `lastActualExecution()` — thực tế bắt đầu lúc nào;
- `lastCompletion()` — hoàn thành lúc nào;
- `getClock()` — clock dùng để tính thời gian.

Lịch sử này cho phép trigger chọn ngữ nghĩa rõ ràng. Backoff trigger có thể tính từ thời điểm hoàn thành; wall-clock trigger có thể bỏ qua thời lượng task và tính theo quy tắc lịch. Trả `null` từ `nextExecution` nghĩa là không còn lần kích hoạt tiếp theo.

"Hoàn thành" ở đây phụ thuộc vào góc nhìn của scheduler. Với `ThreadPoolTaskScheduler`, thân task chạy trên scheduler thread nên `lastCompletion()` phản ánh lúc thân task đó hoàn tất. Với công việc dùng `Trigger` trên `SimpleAsyncTaskScheduler`, callback đã lên lịch thông thường chỉ bàn giao (hand off) user task sang thread thực thi riêng; vì vậy trigger context có thể ghi nhận callback bàn giao đã hoàn thành trong khi công việc nghiệp vụ vẫn còn chạy. Không nên xây chính sách backoff phụ thuộc vào thời điểm hoàn tất từ `lastCompletion()` trước khi xác định rõ mô hình thực thi của scheduler cụ thể.

Spring cung cấp `CronTrigger` cho cron expression và `PeriodicTrigger` khi API cần `Trigger` tổng quát. Nếu code đã biết chắc cần fixed rate hay fixed delay, gọi trực tiếp phương thức của `TaskScheduler` thường rõ hơn.

Trigger tùy biến nên tập trung vào *tính thời điểm*. Công việc nghiệp vụ thuộc scheduled task, không thuộc `nextExecution(...)`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-scheduler-implementations">Các cách triển khai TaskScheduler và môi trường triển khai</a>

<details>
<summary>Xem chi tiết</summary>

Các cách triển khai `TaskScheduler` chính khác nhau về quyền sở hữu thread và mô hình thực thi:

- `ThreadPoolTaskScheduler` bao một `ScheduledThreadPoolExecutor` cục bộ và cung cấp cấu hình kiểu bean. Đây là local scheduler truyền thống của Spring.
- `ConcurrentTaskScheduler` thích nghi một `ScheduledExecutorService` đã tồn tại.
- `DefaultManagedTaskScheduler` delegate tới `ManagedScheduledExecutorService` của môi trường Jakarta EE/JSR-236.
- `SimpleAsyncTaskScheduler`, có từ Spring 6.1, dùng một scheduler thread và thông thường một thread thực thi riêng cho mỗi lần kích hoạt; nó phù hợp với JDK 21 Virtual Thread.

Vì vậy lựa chọn cách triển khai một phần là quyết định theo môi trường triển khai. Tạo thread cục bộ là bình thường trong ứng dụng Spring độc lập, còn môi trường application server có quản lý thread có thể yêu cầu delegate sang managed scheduler.

Đây cũng là quyết định về chính sách thực thi. Scheduler dùng pool cố định và scheduler dùng thread-per-firing có khả năng overlap, saturation và đặc điểm vòng đời khác nhau. API chung `TaskScheduler` giảm coupling tại điểm gọi nhưng không xóa các khác biệt vận hành đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduler-execution-models">Mô hình scheduler thread và execution thread</a>

<details>
<summary>Xem chi tiết</summary>

Scheduler có thể tách "ai theo dõi thời gian?" và "ai chạy thân task?" theo nhiều cách.

`ThreadPoolTaskScheduler` bám sát ngữ nghĩa của `ScheduledExecutorService`: thân task chạy trên chính scheduler thread. Vì vậy pool size vừa là capacity lập lịch vừa là capacity thực thi; task chạy lâu có thể chiếm một scheduler thread.

Điều này cũng có nghĩa `ScheduledFuture` do `ThreadPoolTaskScheduler` trả về đại diện cho việc thực thi/hoàn tất thực tế của scheduled task hoặc chuỗi chạy lặp, không chỉ một bước hand-off sang worker pool khác.

`SimpleAsyncTaskScheduler` có cấu trúc khác:

```text
một scheduler thread
      ↓ quyết định firing
execution thread riêng cho mỗi firing
```

ngoại trừ fixed-delay task chạy trên scheduler thread để giữ đúng fixed-delay semantics.

`ScheduledFuture` mà scheduler này trả về đại diện cho bước **hand-off** sang thread thực thi, không đại diện cho thời điểm task nghiệp vụ thực sự hoàn thành. Nếu cần quan sát việc hoàn tất của thân task, ứng dụng phải có tín hiệu hoàn tất riêng thay vì coi handle lập lịch là kết quả của worker task.

Khi chẩn đoán overlap hoặc độ trễ, hãy xác định scheduler cụ thể trước. Cần biết thân task chạy trên scheduler thread, được hand-off sang thread riêng cho mỗi lần thực thi hay delegate sang target executor khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-rescheduling-cancellation">Lập lịch lại và hủy task tại runtime</a>

<details>
<summary>Xem chi tiết</summary>

Lập lịch bằng code thông thường trả về handle runtime. Các phương thức theo thời điểm và định kỳ của `TaskScheduler` trả `ScheduledFuture<?>`; riêng `schedule(Runnable, Trigger)` là nullable và trả `null` khi `Trigger` không tạo ra lần chạy kế tiếp. Khi có handle, giữ nó giúp ứng dụng hủy các lần chạy tương lai bằng `cancel(...)` theo ngữ nghĩa của scheduler bên dưới.

Lập lịch lại động thường là thao tác **hủy rồi đăng ký lại**:

```text
giữ handle hiện tại
→ cancel registration cũ
→ xây/tính chính sách mới
→ register lại
→ lưu handle mới
```

Trong hạ tầng đăng ký cấp cao hơn của Spring, `ScheduledTask` cũng là biểu diễn runtime có thể hủy. Bài học thiết kế vẫn giống nhau: nếu yêu cầu nghiệp vụ có nhu cầu hủy ở runtime thì phải giữ handle đăng ký.

Không coi attribute của `@Scheduled` là trạng thái runtime có thể thay đổi tùy ý. Lịch chạy dựa trên annotation được phát hiện từ bean metadata rồi container đăng ký. Nếu lịch phải đổi theo database/config event, dùng `TaskScheduler` trực tiếp hoặc đăng ký bằng code qua `ScheduledTaskRegistrar`/`SchedulingConfigurer`.

Hủy lịch không phải rollback. Hủy các lần kích hoạt tương lai không hoàn tác side effect của lần thực thi đã bắt đầu; logic của job vẫn cần idempotency và xử lý interruption phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduler-lifecycle">Pause, resume, shutdown và termination</a>

<details>
<summary>Xem chi tiết</summary>

Scheduler là hạ tầng sống lâu nên cần mô hình shutdown. Scheduler do Spring quản lý tham gia vòng đời container để ứng dụng có thể dừng việc lập lịch mới và giải phóng tài nguyên thực thi khi context đóng.

Spring Framework 6.1 bổ sung hành vi pause/resume cho `ThreadPoolTaskScheduler` thông qua tích hợp `SmartLifecycle`, cùng graceful shutdown theo vòng đời. Trong mô hình này, `stop()` tạm dừng việc thực thi task và `start()` cho phép tiếp tục trong khi scheduler vẫn có thể tái sử dụng; đây là hành vi vòng đời chứ không phải hai API công khai `pause()`/`resume()`. Shutdown thì khác: nó đóng vĩnh viễn tài nguyên thực thi của `ApplicationContext` đó.

Chính sách vận hành vẫn cần trả lời rõ:

- Delayed task có được chạy sau khi shutdown bắt đầu không?
- Task định kỳ đã đăng ký có tiếp tục không?
- Container chờ công việc đang chạy bao lâu?
- Cancellation có được phép interrupt running task không?
- Task đang block I/O sẽ ra sao?

Scheduled executor bên dưới có chính sách cho một số câu hỏi này và Spring cung cấp hook cấu hình tương ứng. Không được hiểu "Spring quản lý" thành "mọi công việc đã lên lịch chắc chắn hoàn thành trước khi tiến trình thoát"; termination window của nền tảng triển khai vẫn là ranh giới ngoài cùng.

Hãy test shutdown với công việc chạy lâu/blocking giống production, không chỉ callback chạy tức thì.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="quartz-boundary">Khi TaskScheduler là đủ và khi cần Quartz</a>

<details>
<summary>Xem chi tiết</summary>

`TaskScheduler` phù hợp với in-process scheduling khi lifecycle của ứng dụng cũng là lifecycle của schedule: maintenance, cache refresh, polling, metrics collection hoặc work có thể đăng ký lại khi process start.

Quartz có ý nghĩa khi bản thân bài toán lập lịch cần khả năng mạnh hơn như persisted job/trigger state, durable job identity, calendar và misfire handling phong phú hoặc coordination giữa nhiều instance của ứng dụng. Những yêu cầu đó không được giải quyết chỉ bằng tăng pool size của `ThreadPoolTaskScheduler`.

Spring Framework cung cấp Quartz integration để Quartz job/trigger tham gia Spring configuration và dependency injection. Module này chỉ thiết lập **decision boundary**; job store, clustering, trigger semantics và operations của Quartz thuộc Quartz-specific knowledge.

Một phép thử hữu ích là persistence: nếu process chết và in-memory registration mất theo là điều không chấp nhận được, chỉ `TaskScheduler` trong process có lẽ chưa đủ cho scheduling architecture.

</details>

- [Quay lại đầu trang](#back-to-top)
