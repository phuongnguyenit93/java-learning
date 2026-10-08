<a id="back-to-top"></a>

# Auto-configuration cho task execution và scheduling

## Menu
- [Vì sao Boot auto-configure hạ tầng task execution?](#boot-task-execution-purpose)
- [Khi nào Boot cung cấp và sử dụng `AsyncTaskExecutor`?](#application-task-executor)
- [Các property `spring.task.execution.*` tinh chỉnh execution bằng platform thread như thế nào?](#task-execution-properties)
- [Khi nào Boot cung cấp task scheduler?](#boot-task-scheduler)
- [Các property `spring.task.scheduling.*` tinh chỉnh scheduling như thế nào?](#task-scheduling-properties)
- [Điều gì thay đổi khi ứng dụng tự cung cấp executor hoặc scheduler?](#custom-executor-scheduler-boundary)
- [Boot auto-configuration bàn giao sang ngữ nghĩa concurrency của Spring ở đâu?](#framework-concurrency-handoff)

## <a id="boot-task-execution-purpose">Vì sao Boot auto-configure hạ tầng task execution?</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng thường cần executor ngay cả khi mục tiêu của nó không phải học concurrency. Các tích hợp framework có thể cần chạy công việc bất đồng bộ, còn công việc theo lịch cần scheduler. Vai trò của Spring Boot là cung cấp hạ tầng hợp lý khi ứng dụng chưa tự cung cấp, đồng thời cung cấp property cho những nhu cầu tinh chỉnh phổ biến.

Trong Spring Boot 3.3, nếu context không có `Executor` bean, Boot auto-configure một `AsyncTaskExecutor`. Với platform thread, cách triển khai là `ThreadPoolTaskExecutor`; khi bật virtual threads, Boot dùng `SimpleAsyncTaskExecutor` chạy virtual thread. Scheduler cũng được auto-configure theo cùng tư tưởng khi ứng dụng cần thực thi task theo lịch.

Chương này tập trung vào giá trị mặc định, tích hợp và back-off của Boot. Ngữ nghĩa của `@Async`, `@Scheduled`, rejection, locking và tính đúng đắn khi chạy đồng thời thuộc Spring Framework hoặc Java concurrency.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-task-executor">Khi nào Boot cung cấp và sử dụng `AsyncTaskExecutor`?</a>

<details>
<summary>Xem chi tiết</summary>

`AsyncTaskExecutor` được Boot auto-configure không chỉ là bean tiện dụng cho code ứng dụng. Trong Boot 3.3, nó được dùng cho nhiều tích hợp cần thực thi bất đồng bộ hoặc công việc blocking như `@EnableAsync`, Spring for GraphQL `Callable`, xử lý request bất đồng bộ của Spring MVC và hỗ trợ blocking execution của WebFlux.

Vì nhiều thành phần cùng dùng chung, thay executor là một quyết định runtime có phạm vi rộng hơn một method `@Async`. Nếu ứng dụng tự định nghĩa `Executor`, một số tích hợp sẽ back off; riêng MVC/WebFlux cần `AsyncTaskExecutor` mang tên `applicationTaskExecutor` cho hợp đồng cụ thể của chúng.

Trước khi tùy biến, hãy liệt kê thành phần nào đang dùng executor. Một bean tùy biến chạy đúng cho một trường hợp sử dụng vẫn có thể làm tích hợp khác mất hợp đồng về kiểu hoặc tên bean.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-execution-properties">Các property `spring.task.execution.*` tinh chỉnh execution bằng platform thread như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Với `ThreadPoolTaskExecutor` dùng platform thread, Boot cung cấp namespace `spring.task.execution.*` để điều chỉnh pool. Spring Boot 3.3 bắt đầu với tám core threads và cho phép cấu hình kích thước tối đa, dung lượng queue, keep-alive, hành vi shutdown, tiền tố tên thread cùng các tùy chọn liên quan.

```yaml
spring:
  task:
    execution:
      pool:
        max-size: 16
        queue-capacity: 100
        keep-alive: 10s
```

Đây là mô hình năng lực của executor chứ không phải bộ "nút tăng hiệu năng" áp dụng cho mọi workload. Queue có giới hạn thay đổi thời điểm pool được phép tăng; throughput và latency của workload quyết định con số nào hợp lý. Hãy đo và hiểu ngữ nghĩa executor trước khi tăng các giá trị.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.3/reference/features/task-execution-and-scheduling.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boot-task-scheduler">Khi nào Boot cung cấp task scheduler?</a>

<details>
<summary>Xem chi tiết</summary>

Boot có thể auto-configure task scheduler khi cần thực thi task theo lịch, ví dụ khi scheduling được bật. Trên đường platform thread, Boot dùng `ThreadPoolTaskScheduler`; trong Boot 3.3 pool mặc định có một thread.

Scheduler là hạ tầng runtime chứ không phải nơi định nghĩa ngữ nghĩa scheduling. Spring Framework quyết định cách method `@Scheduled` được phát hiện và gọi; Boot chỉ cung cấp/cấu hình cách triển khai để cơ chế framework sử dụng.

Khi scheduled job chạy trễ, hãy phân loại: scheduler có thiếu năng lực hoặc bị chặn, biểu thức scheduling có sai, task có chạy quá lâu, hay chính sách thực thi đồng thời có vấn đề. Chỉ phần cung cấp/cấu hình scheduler là trách nhiệm chính của Boot.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-scheduling-properties">Các property `spring.task.scheduling.*` tinh chỉnh scheduling như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Với `ThreadPoolTaskScheduler` dùng platform thread, `spring.task.scheduling.*` cấu hình các giá trị mặc định của Boot. Các thiết lập thường gặp gồm kích thước pool, tiền tố tên thread và cách chờ công việc theo lịch khi shutdown.

```yaml
spring:
  task:
    scheduling:
      thread-name-prefix: scheduling-
      pool:
        size: 2
```

Pool một thread mặc định là lựa chọn thận trọng. Tăng pool cho phép những scheduled task độc lập có thể chạy chồng thời gian, nhưng đồng thời tăng áp lực concurrency lên database, API hoặc tài nguyên phía dưới. Pool lớn hơn không biến một task chậm hoặc không thread-safe thành đúng.

Khi bật virtual threads, Boot dùng chiến lược scheduler khác và các property liên quan pooling không còn mô tả cùng mô hình runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-executor-scheduler-boundary">Điều gì thay đổi khi ứng dụng tự cung cấp executor hoặc scheduler?</a>

<details>
<summary>Xem chi tiết</summary>

Boot auto-configuration được thiết kế để back off khi ứng dụng chủ động cung cấp hạ tầng riêng. Vì vậy executor/scheduler tùy biến có thể thay thế một phần bố trí mặc định chứ không chỉ thêm bean mới.

Điều phải kiểm tra là tính tương thích với thành phần sử dụng. Nhiều executor có thể cần quy ước tên bean như `taskExecutor` hoặc `applicationTaskExecutor`; tích hợp web có thể yêu cầu chính kiểu `AsyncTaskExecutor`. Boot cũng cung cấp builder beans để ứng dụng tạo cách triển khai tùy biến dựa trên các quy ước/giá trị mặc định đã được Boot chuẩn bị.

Chỉ tùy biến khi workload cần mức cô lập, năng lực, cách đặt tên hoặc chính sách lifecycle khác. Nếu tùy biến chỉ để tái tạo mặc định bằng nhiều code hơn, ứng dụng đang nhận thêm gánh nặng bảo trì mà không có lợi ích runtime rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="framework-concurrency-handoff">Boot auto-configuration bàn giao sang ngữ nghĩa concurrency của Spring ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Module này dừng ở ranh giới cung cấp hạ tầng của Boot: Boot tạo executor/scheduler nào, namespace nào cấu hình chúng, thành phần nào sử dụng và khi nào auto-configuration back off.

Spring Framework sở hữu ngữ nghĩa của `@Async`, `@EnableAsync`, `@Scheduled`, `@EnableScheduling`, task decorators và các abstraction concurrency của framework. Java concurrency sở hữu thread safety, memory visibility, synchronization, interruption, cơ chế executor nói chung và ngữ nghĩa sâu của virtual threads.

Khi chẩn đoán, hãy phân loại trước. "Vì sao Boot tạo executor này?" thuộc chương này. "Vì sao scheduled method chạy chồng nhau?" chủ yếu là Spring scheduling. "Vì sao trạng thái dùng chung có thể thay đổi bị hỏng?" là Java concurrency. Phân ranh giới đúng giúp tránh sửa cấu hình để che lỗi về tính đúng đắn.

</details>

- [Quay lại đầu trang](#back-to-top)
