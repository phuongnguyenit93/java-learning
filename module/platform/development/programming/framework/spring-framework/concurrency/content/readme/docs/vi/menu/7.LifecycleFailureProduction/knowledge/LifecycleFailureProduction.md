<a id="back-to-top"></a>

# Vòng đời, lỗi và tư duy production

## Menu
- [Vòng đời phối hợp, shutdown và termination](#executor-lifecycle)
- [Các ranh giới lỗi: rejection, async failure và scheduled failure](#failure-boundaries)
- [Chẩn đoán runtime và tín hiệu quan sát](#runtime-diagnostics)
- [Ranh giới Spring Framework và Spring Boot task configuration](#boot-auto-config-boundary)
- [Quyết định executor/scheduler end-to-end](#production-synthesis)

## <a id="executor-lifecycle">Vòng đời phối hợp, shutdown và termination</a>

<details>
<summary>Xem chi tiết</summary>

Một lợi ích lớn của executor do Spring quản lý là nó có thể tham gia vòng đời của `ApplicationContext` thay vì tồn tại như hạ tầng thread không được quản lý. Container có điểm để phối hợp dừng/shutdown, nhưng điều đó **không** đảm bảo mọi task trong queue chắc chắn hoàn thành.

Với `ThreadPoolTaskExecutor`, chính sách shutdown phải được chọn rõ ràng. Module hiện cấu hình:

```java
executor.setWaitForTasksToCompleteOnShutdown(true);
executor.setAwaitTerminationSeconds(2);
```

`setWaitForTasksToCompleteOnShutdown(true)` yêu cầu executor cho phép task đang chạy và task trong queue tiếp tục hoàn tất thay vì lập tức interrupt/clear chúng. Trong Spring Framework 6.1, bật flag này còn có nghĩa executor không đi qua coordinated lifecycle-stop phase thông thường; soft shutdown của nó được thực hiện muộn hơn ở bước destroy executor. `setAwaitTerminationSeconds(2)` còn yêu cầu container chờ termination tối đa hai giây trước khi tiếp tục shutdown. Hai setting giải quyết hai câu hỏi khác nhau: **chọn đường shutdown/hoàn tất công việc còn lại như thế nào**, và **container sẽ chờ bao lâu**. Hết hai giây chờ không đồng nghĩa Spring sẽ tự kill các task còn chạy; nếu process vẫn sống, executor có thể tiếp tục hoàn tất chúng trong khi phần còn lại của shutdown tiến lên.

Timeout ở production phải khớp termination window của nền tảng triển khai và vòng đời của các tài nguyên task còn cần. Chờ mười giây không giúp gì nếu nền tảng hard-kill process sau năm giây. Ngược lại, để task tiếp tục chạy khi datasource hoặc network client đã bị destroy cũng có thể làm graceful shutdown thất bại.

Cần xem cả việc gửi task trong lúc shutdown như một phần của vòng đời. Khi shutdown bắt đầu, công việc mới có thể bị reject tùy trạng thái/cấu hình của executor. Luồng nghiệp vụ không nên giả định có thể đưa công việc nền vào queue cho tới sát thời điểm process chết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="failure-boundaries">Các ranh giới lỗi: rejection, async failure và scheduled failure</a>

<details>
<summary>Xem chi tiết</summary>

Câu "async task bị lỗi" chưa đủ để chẩn đoán production. Có nhiều ranh giới lỗi khác nhau:

1. **Lỗi khi gửi/reject task** xảy ra trước khi business code bắt đầu. Executor đang saturated hoặc shutdown có thể reject task ngay tại điểm gọi.
2. **Lỗi `@Async` với kiểu trả về dạng future** được giữ trong `Future`/`CompletableFuture`; bên gọi quan sát khi `get`, `join` hoặc compose future.
3. **Lỗi của `@Async void`** không có future để trả về, nên Spring chuyển uncaught exception tới `AsyncUncaughtExceptionHandler`.
4. **Lỗi của scheduled task** thuộc ngữ nghĩa scheduler/error-handler. Không nên trộn nó với executor rejection; job chạy lặp cần chính sách retry/idempotency riêng.

Thử nghiệm `/spring-executor/void-exception` trong module chứng minh một điểm phân biệt hữu ích: rejection có thể xảy ra đồng bộ khi proxy đang gửi task, còn exception phát sinh sau đó trong phương thức `void` thật được quan sát qua `AsyncUncaughtExceptionHandler`.

Vì vậy xử lý vận hành cần ghi rõ lỗi xảy ra **ở ranh giới nào**. Retry một lần gửi task chưa chạy, retry logic nghiệp vụ có thể đã chạy dở và cảnh báo một job chạy lặp bị hỏng là ba chính sách khác nhau với rủi ro thực thi trùng khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-diagnostics">Chẩn đoán runtime và tín hiệu quan sát</a>

<details>
<summary>Xem chi tiết</summary>

Sự cố concurrency dễ chẩn đoán hơn khi chính sách thực thi có thể quan sát. Nên dùng tiền tố tên thread có ý nghĩa, ghi rõ cấu hình executor/scheduler, và quan sát các tín hiệu mà cách triển khai cung cấp như active thread, pool size hiện tại, queue depth, số công việc đã hoàn tất, rejection, thời lượng task và độ trễ shutdown.

Spring Framework 6.1 còn hỗ trợ khả năng quan sát (observability) cho việc thực thi phương thức `@Scheduled`. Khi `ScheduledTaskRegistrar` được cấu hình với `ObservationRegistry`, Spring tạo observation cho mỗi lần thực thi đã lên lịch với thông tin như phương thức, kết quả và lỗi. Đây là instrumentation cho scheduled method, **không** phải tự động instrument mọi task tùy ý gửi vào mọi `TaskExecutor`.

Có thể phân lớp minh chứng:

```text
Tên thread / trạng thái pool
→ công việc đang chạy ở đâu và theo chính sách nào

Correlation id của task/ứng dụng
→ request/thao tác nghiệp vụ nào tạo task

Observation của scheduled task
→ thời lượng/kết quả/lỗi của lần thực thi @Scheduled

Metrics/traces/logs của ứng dụng
→ tác động tới downstream và nghiệp vụ
```

Khả năng quan sát không thay thế thiết kế có giới hạn. Dashboard có thể cho thấy queue đang tăng, nhưng không quyết định queue capacity hay chính sách rejection đúng là bao nhiêu; đó vẫn là quyết định về chính sách thực thi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boot-auto-config-boundary">Ranh giới Spring Framework và Spring Boot task configuration</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework sở hữu các abstraction và cơ chế của module này: `TaskExecutor`, `ThreadPoolTaskExecutor`, `TaskScheduler`, `@Async`, `@Scheduled`, task decoration, hook vòng đời và hạ tầng liên quan.

Spring Boot có thể tự động tạo/cấu hình executor hoặc scheduler bean từ quy ước và các thuộc tính cấu hình (configuration property) của Boot. Tiện ích đó thuộc curriculum Spring Boot. Không nên dùng Boot auto-configuration làm lời giải thích cho ngữ nghĩa của Framework, vì cùng API Spring Framework có thể hoạt động hoàn toàn không cần Boot.

Một trình tự chẩn đoán tốt là:

```text
1. Ứng dụng đang dùng abstraction nào của Spring Framework?
2. Executor/scheduler bean cụ thể nào thực sự xử lý công việc?
3. Bean đó do ứng dụng tự tạo hay Spring Boot auto-configure?
4. Các setting pool/queue/virtual-thread/scheduling/lifecycle đang có hiệu lực là gì?
```

Ranh giới này tránh hai lỗi phổ biến: coi mặc định của Boot là mặc định chung của Spring, hoặc học tên property trước khi hiểu hành vi executor/scheduler mà property đó cuối cùng cấu hình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="production-synthesis">Quyết định executor/scheduler end-to-end</a>

<details>
<summary>Xem chi tiết</summary>

Thiết kế production nên bắt đầu từ ngữ nghĩa của workload, không bắt đầu từ annotation:

```text
thực thi nền thông thường
→ chọn chính sách của TaskExecutor

gọi phương thức kiểu fire-and-continue
→ @Async + executor + cách quan sát lỗi rõ ràng

thực thi theo thời gian
→ TaskScheduler/@Scheduled + ngữ nghĩa trigger
```

Sau đó làm rõ chính sách theo từng chiều:

- **Capacity:** Giới hạn concurrency an toàn thực sự nằm ở CPU, DB connection, remote quota, memory hay độ trễ?
- **Admission:** Công việc được đưa vào queue, throttle, reject hay cấp một virtual thread mới?
- **Context:** Giá trị nào phải đi qua task boundary và abstraction nào sở hữu chúng?
- **Failure:** Rejection, lỗi trong future, lỗi async `void` và lỗi scheduled task được quan sát thế nào?
- **Lifecycle:** Công việc đang chạy/trong queue sẽ ra sao khi context đóng và process termination?
- **Scheduling:** Các lần thực thi có thể overlap không? Fixed delay, fixed rate, cron hay one-time có đúng quy tắc nghiệp vụ?
- **Diagnostics:** Người vận hành có xác định được executor/scheduler, nguồn task, thời lượng và kết quả không?

Lựa chọn cuối thường là tổ hợp chứ không phải một tên class. Service blocking I/O có thể dùng Virtual Thread nhưng vẫn đặt concurrency limit theo capacity của downstream; batch nặng CPU có thể dùng `ThreadPoolTaskExecutor` có giới hạn; maintenance job chạy lặp có thể dùng `ThreadPoolTaskScheduler` với pool nhỏ có chủ ý và logic job idempotent.

Mục tiêu của module không phải "dùng nhiều thread hơn". Mục tiêu là làm cách thực thi, lập lịch, context, xử lý lỗi và hành vi shutdown trở thành những quyết định có chủ ý và quan sát được.

</details>

- [Quay lại đầu trang](#back-to-top)
