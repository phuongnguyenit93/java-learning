<a id="back-to-top"></a>

# Tích hợp Virtual Thread trong Spring

## Menu
- [Ranh giới trách nhiệm giữa Spring và JDK Virtual Thread](#virtual-thread-boundary)
- [VirtualThreadTaskExecutor](#virtual-thread-task-executor)
- [SimpleAsyncTaskExecutor với Virtual Thread](#simple-async-task-executor-virtual)
- [SimpleAsyncTaskScheduler](#simple-async-task-scheduler)
- [Ngữ nghĩa fixed-delay trong SimpleAsyncTaskScheduler](#virtual-thread-fixed-delay)
- [Các cơ chế điều khiển của SimpleAsyncTaskExecutor](#virtual-thread-concurrency-limits)
- [Chọn Virtual Thread hay pooled platform thread](#virtual-thread-decision)

## <a id="virtual-thread-boundary">Ranh giới trách nhiệm giữa Spring và JDK Virtual Thread</a>

<details>
<summary>Xem chi tiết</summary>

Virtual Thread là cơ chế concurrency của JDK 21; Spring không định nghĩa lại cách virtual thread được schedule, mount, block hay pin. Vai trò của Spring hẹp hơn: cung cấp các cách triển khai abstraction thực thi tác vụ và lập lịch để có thể tạo virtual thread, nhờ đó ứng dụng vẫn dùng `TaskExecutor`, `@Async` hoặc `TaskScheduler` thay vì gắn mọi điểm gọi trực tiếp với API tạo thread của JDK.

Có thể giữ ranh giới tư duy như sau:

```text
Ngữ nghĩa Virtual Thread của JDK 21
→ Java Concurrency sở hữu

Chọn/cấu hình Spring executor hoặc scheduler dùng Virtual Thread
→ module này sở hữu
```

Virtual Thread thay đổi chi phí của concurrency có nhiều blocking, nhưng không thay đổi quy tắc bảo đảm tính đúng đắn của shared state. Task chạy trên virtual thread vẫn cần safe publication, synchronization khi cần, giới hạn hợp lý đối với database/remote service và truyền context rõ ràng khi trạng thái gắn với thread phải đi qua ranh giới executor.

Không nên hiểu "virtual" thành "không giới hạn". Virtual Thread giúp việc có rất nhiều blocking task rẻ hơn mô hình một platform thread cho mỗi task, nhưng connection pool, rate limit, CPU, memory, file descriptor và quota của hệ thống downstream vẫn hữu hạn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-thread-task-executor">VirtualThreadTaskExecutor</a>

<details>
<summary>Xem chi tiết</summary>

`VirtualThreadTaskExecutor` là `AsyncTaskExecutor` tối giản của Spring Framework 6.1 dựa trên JDK 21 Virtual Thread. Mỗi task được gửi sẽ chạy trên một virtual thread mới. Nó không có worker pool, vì vậy cũng không có mô hình tinh chỉnh `poolSize` hay `queueCapacity`.

Bề mặt cấu hình nhỏ là chủ ý thiết kế: tùy chọn trực tiếp đáng chú ý là tiền tố tên thread. Nó phù hợp khi yêu cầu đơn giản là "mỗi task chạy trên một virtual thread" và ứng dụng không cần phía Spring cung cấp throttling hay task decoration.

```java
@Bean
VirtualThreadTaskExecutor virtualThreadExecutor() {
    return new VirtualThreadTaskExecutor("orders-vt-");
}
```

Class này không cung cấp các cơ chế điều khiển phong phú của `SimpleAsyncTaskExecutor`. Nếu cần giới hạn mức đồng thời, `TaskDecorator` hoặc theo dõi task khi đóng executor, nên dùng `SimpleAsyncTaskExecutor` với Virtual Thread thay vì cố biến `VirtualThreadTaskExecutor` thành một thread pool.

Tên thread chỉ là metadata để chẩn đoán, không phải ranh giới cô lập. Tính đúng đắn của nghiệp vụ không được phụ thuộc vào tên virtual thread hay carrier thread cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="simple-async-task-executor-virtual">SimpleAsyncTaskExecutor với Virtual Thread</a>

<details>
<summary>Xem chi tiết</summary>

`SimpleAsyncTaskExecutor` dùng mô hình một thread cho mỗi task và không tái sử dụng thread. Từ Spring Framework 6.1, nó có thể chuyển việc tạo thread cho từng task sang JDK 21 Virtual Thread:

```java
@Bean
SimpleAsyncTaskExecutor applicationVirtualThreads() {
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("app-vt-");
    executor.setVirtualThreads(true);
    executor.setConcurrencyLimit(200);
    executor.setTaskDecorator(new MyTaskDecorator());
    return executor;
}
```

Mô hình này khác `ThreadPoolTaskExecutor`. Thread pool tái sử dụng một tập platform thread hữu hạn và thường kết hợp admission với queue. `SimpleAsyncTaskExecutor` tạo thread cho từng task được nhận; khi dùng Virtual Thread, mô hình này rẻ hơn đáng kể cho workload có nhiều blocking, nhưng nó vẫn không phải thread pool.

Đây là lý do Javadoc của `VirtualThreadTaskExecutor` hướng sang `SimpleAsyncTaskExecutor` khi ứng dụng cần cơ chế như giới hạn mức đồng thời hoặc task decoration. Trong baseline Spring 6.1, concurrency limit dương hoạt động như một cơ chế **throttle**: khi số task đang hoạt động đã chạm giới hạn, thread submit sẽ chờ cho tới khi một task khác hoàn tất và trả lại một slot. Đây không phải queue capacity và cũng không tự reject chỉ vì limit đang đầy. Với bên gọi `@Async`, điều này có nghĩa chính lời gọi qua proxy có thể bị block ở bước submit khi throttle đã bão hòa.

Giới hạn mức đồng thời bảo vệ capacity của downstream; decorator truyền context; theo dõi termination cho phép `close()` chờ các task đang chạy khi đã cấu hình task-termination timeout. Khác với cơ chế phối hợp vòng đời của executor như `ThreadPoolTaskExecutor`, việc theo dõi lúc `close()` này không có nghĩa `SimpleAsyncTaskExecutor` tham gia một coordinated `SmartLifecycle` stop.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="simple-async-task-scheduler">SimpleAsyncTaskScheduler</a>

<details>
<summary>Xem chi tiết</summary>

`SimpleAsyncTaskScheduler` là `TaskScheduler` được Spring Framework 6.1 thiết kế theo mô hình một thread thực thi cho mỗi lần task được kích hoạt. Nó có một scheduler thread để quyết định *khi nào* task chạy, rồi thông thường tạo thread thực thi riêng cho mỗi lần chạy. Trên JDK 21, khi bật Virtual Thread, các thread thực thi đó có thể là virtual thread.

```text
ThreadPoolTaskScheduler
→ scheduler pool cố định
→ thân task chạy trên scheduler thread

SimpleAsyncTaskScheduler
→ một scheduler thread quyết định thời điểm
→ thông thường mỗi lần chạy có thread thực thi riêng
```

Vì kế thừa `SimpleAsyncTaskExecutor`, **đường thực thi có bước bàn giao** thông thường của scheduler này có thể dùng giới hạn mức đồng thời, task decoration và cơ chế theo dõi task khi kết thúc. Nó cũng có thể chuyển phần thực thi đã bàn giao sang target executor khác, hữu ích khi muốn giữ một scheduling clock nhưng dùng chính sách thực thi khác. Fixed-delay là ngoại lệ quan trọng được giải thích ở phần kế tiếp.

Mô hình tư duy quan trọng là tách *scheduling* khỏi *execution*. Một scheduler thread không đồng nghĩa toàn ứng dụng chỉ chạy được một task cùng lúc, vì phần lớn lần kích hoạt được hand-off sang thread thực thi riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-thread-fixed-delay">Ngữ nghĩa fixed-delay trong SimpleAsyncTaskScheduler</a>

<details>
<summary>Xem chi tiết</summary>

Fixed-delay là ngoại lệ có chủ ý trong mô hình hand-off của `SimpleAsyncTaskScheduler`. Để giữ đúng ngữ nghĩa fixed-delay truyền thống, fixed-delay task chạy ngay trên scheduler thread. Khoảng delay được tính sau khi một lần thực thi kết thúc rồi mới tính tới lần kế tiếp.

Vì vậy một fixed-delay task chạy chậm có thể giữ scheduler thread duy nhất và làm chậm công việc lập lịch khác. Đây là hành vi riêng của `SimpleAsyncTaskScheduler`, không phải hạn chế chung của Virtual Thread.

Vì đường fixed-delay chạy trực tiếp user task trên scheduler thread thay vì đi qua bước bàn giao `execute(...)` thông thường, `TaskDecorator`, cơ chế giới hạn mức đồng thời và target task executor kế thừa của scheduler không chi phối thân fixed-delay task đó. Khi cấu hình fixed-delay workload, không nên giả định mọi lần kích hoạt của `SimpleAsyncTaskScheduler` đều đi qua cùng một tập cơ chế điều khiển thực thi.

Với scheduler này, Spring khuyến nghị fixed-rate hoặc cron khi muốn tận dụng mô hình thread-per-execution/Virtual Thread. Nếu bắt buộc cần fixed-delay và task có thể block lâu, nên xem lại cách triển khai scheduler có phù hợp với workload hay không.

```text
SimpleAsyncTaskScheduler + fixed rate/cron
→ scheduler quyết định thời điểm rồi hand-off execution

SimpleAsyncTaskScheduler + fixed delay
→ task chạy trên scheduler thread
→ một lần chạy chậm có thể giữ toàn scheduling lane
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-thread-concurrency-limits">Các cơ chế điều khiển của SimpleAsyncTaskExecutor</a>

<details>
<summary>Xem chi tiết</summary>

Khi `SimpleAsyncTaskExecutor` dùng Virtual Thread, các cơ chế điều khiển xung quanh vẫn có ý nghĩa riêng:

- **Concurrency limit** giới hạn việc tiếp nhận để không quá số task đã cấu hình cùng hoạt động. Trong Spring 6.1, với limit **dương**, thread submit sẽ chờ một slot khi đã chạm limit; đây không phải pool size hay ngưỡng rejection. Đặt limit bằng `0` nghĩa là không cho phép thực thi đồng thời và việc thực thi thất bại với `IllegalStateException` thay vì chờ.
- **Task decoration** bao quanh callback thực thi để capture/restore logging, observation hoặc context của ứng dụng. Quy tắc cleanup của chương Context Propagation vẫn giữ nguyên.
- **Termination tracking** cho phép `close()` chờ các task đang chạy khi cấu hình task-termination timeout. Đây là cơ chế theo dõi lúc đóng executor, không phải contract dừng vòng đời có phối hợp. Việc theo dõi này có overhead runtime.

Các khả năng này cho thấy vì sao `SimpleAsyncTaskExecutor` là lựa chọn Virtual Thread linh hoạt hơn. Đồng thời chúng bác bỏ mô hình tư duy "dùng Virtual Thread thì không cần cấu hình concurrency": Virtual Thread giảm áp lực do thread khan hiếm, nhưng không loại bỏ kiểm soát admission, context, vòng đời hay capacity của downstream.

Ngữ nghĩa giới hạn này cũng quan trọng trên các đường của `SimpleAsyncTaskScheduler` sử dụng cơ chế bàn giao thông thường, chẳng hạn fixed-rate và trigger-driven execution. Nếu concurrency limit dương đã bão hòa, scheduler thread có thể phải chờ slot trong lúc dispatch, từ đó làm chậm các lần kích hoạt tiếp theo. Thân fixed-delay task bỏ qua throttle này vì chạy trực tiếp trên scheduler thread. Vì vậy concurrency limit là một quyết định backpressure có chủ đích chứ không phải safety switch miễn phí.

Không trộn vai trò của các tham số cấu hình. Giới hạn mức đồng thời bảo vệ capacity; task decoration truyền context; termination timeout kiểm soát cách `close()` chờ các task được theo dõi. Tinh chỉnh một cái không giải quyết thay hai cái còn lại.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-thread-decision">Chọn Virtual Thread hay pooled platform thread</a>

<details>
<summary>Xem chi tiết</summary>

Nên cân nhắc Virtual Thread khi workload gồm nhiều task độc lập, dành phần lớn thời gian chờ I/O và ứng dụng muốn giữ mô hình lập trình thread-per-task đơn giản. Nó đặc biệt hữu ích khi việc sizing platform-thread pool trước đây chủ yếu bị chi phối bởi thời gian chờ thay vì CPU.

Nên ưu tiên platform-thread pool có giới hạn, hoặc ít nhất giữ concurrency limit rõ ràng, khi chính mức concurrency cần bị giới hạn: công việc nặng CPU, database connection pool nhỏ, downstream service dễ quá tải hoặc process có ngân sách tài nguyên chặt là các ví dụ điển hình.

| Câu hỏi | Executor hướng Virtual Thread | `ThreadPoolTaskExecutor` |
| --- | --- | --- |
| Tái sử dụng thread | Thread per task | Tái sử dụng platform thread |
| Workload phù hợp | Nhiều blocking task độc lập | Muốn ranh giới worker/queue hữu hạn rõ ràng |
| Admission/backpressure | Phải thiết kế riêng hoặc dùng concurrency limit | Pool + queue + rejection tạo ranh giới tự nhiên |
| Context propagation | Vẫn phải làm rõ | Vẫn phải làm rõ |
| Tính đúng đắn của shared state | Không thay đổi | Không thay đổi |

Không chọn chỉ dựa trên số lượng thread. Hãy bắt đầu từ workload, mục tiêu độ trễ, capacity của downstream, hành vi shutdown và nhu cầu chẩn đoán. Spring cung cấp nhiều chính sách thực thi để ứng dụng lựa chọn; không có một chính sách đúng cho mọi trường hợp.

</details>

- [Quay lại đầu trang](#back-to-top)
