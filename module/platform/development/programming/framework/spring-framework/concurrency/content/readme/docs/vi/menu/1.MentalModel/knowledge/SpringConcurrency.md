<a id="back-to-top"></a>

# Mô hình concurrency trong Spring

## Menu
- [Vì sao Spring cần hạ tầng task concurrency?](#spring-concurrency-purpose)
- [Thực thi tác vụ, gọi bất đồng bộ và lập lịch](#execution-async-scheduling)
- [Trách nhiệm của Spring và Java Concurrency](#spring-vs-jdk-concurrency)
- [Executor và scheduler do container quản lý](#container-managed-task-infrastructure)

## <a id="spring-concurrency-purpose">Vì sao Spring cần hạ tầng task concurrency?</a>

<details>
<summary>Xem chi tiết</summary>

Java đã có thread, `Executor`, `ExecutorService`, `Future`, scheduled executor và Virtual Thread. Spring task concurrency tồn tại vì ứng dụng thường cần các cơ chế đó tham gia cùng mô hình cấu hình, dependency injection, vòng đời (lifecycle), proxy và môi trường triển khai của ứng dụng Spring.

Vì vậy Spring bổ sung **khả năng tích hợp và chính sách thực thi (execution policy)**, không tạo ra một mô hình đồng thời thay thế Java. `TaskExecutor` trừu tượng hóa việc gửi tác vụ để thực thi, `TaskScheduler` trừu tượng hóa việc thực thi theo thời gian, `@Async` biến lời gọi phương thức của Spring bean thành công việc được giao cho executor, còn `@Scheduled` giúp container đăng ký phương thức chạy định kỳ hoặc chạy một lần.

Mô hình tư duy:

```text
Java concurrency primitives
→ cung cấp ngữ nghĩa thread/executor/future thực tế

Hạ tầng task của Spring
→ chọn/cấu hình các cơ chế đó dưới dạng bean
→ nối chúng với proxy, annotation, lifecycle và ApplicationContext
```

Module này bắt đầu có giá trị khi câu hỏi chuyển từ "thread pool hoạt động thế nào?" sang "ứng dụng Spring đang dùng chính sách thực thi nào, ai sở hữu nó, và điều gì xảy ra tại lời gọi, khi shutdown, khi có lỗi hoặc khi vượt qua ranh giới context?"

### Lộ trình học

Nên học module theo thứ tự:

```text
Mô hình concurrency của Spring (đang học)
→ phân biệt thực thi tác vụ, gọi bất đồng bộ, lập lịch và ranh giới trách nhiệm

TaskExecutor và @Async
→ hiểu công việc đã sẵn sàng được thực thi thế nào và lời gọi phương thức vượt qua ranh giới bất đồng bộ ra sao

Truyền context
→ hiểu dữ liệu/context nào bị mất khi việc thực thi chuyển sang thread khác

TaskScheduler
→ nắm mô hình thời gian bằng API trước khi dùng annotation

@Scheduled
→ áp dụng mô hình thời gian đó qua cơ chế đăng ký khai báo

Tích hợp Virtual Thread
→ so sánh mô hình một thread cho mỗi task với chính sách dùng pool platform thread

Vòng đời, lỗi và tư duy production
→ ghép chính sách tiếp nhận task, context, scheduling, quan sát lỗi, chẩn đoán và shutdown thành một mô hình hoàn chỉnh
```

Sau khi hoàn thành module, người học phải có thể lần theo công việc từ lúc submit hoặc đăng ký lịch chạy tới lúc thực thi thật, xác định abstraction Spring/JDK nào sở hữu từng hành vi, và chọn executor/scheduler với kỳ vọng rõ ràng về capacity, context, xử lý lỗi và vòng đời.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-async-scheduling">Thực thi tác vụ, gọi bất đồng bộ và lập lịch</a>

<details>
<summary>Xem chi tiết</summary>

Ba nhu cầu này liên quan nhau nhưng không đồng nghĩa.

**Thực thi tác vụ (task execution)** nghĩa là công việc đã sẵn sàng và cần được giao cho một chiến lược thực thi ngay bây giờ. `TaskExecutor` quyết định task chạy trên luồng bên gọi, thread mới hay thread pool.

**Gọi phương thức bất đồng bộ** là hành vi tại điểm gọi. Với proxy mode mặc định của Spring, lời gọi `@Async` được proxy chặn, đóng gói thành task, gửi vào executor rồi bên gọi nhận lại quyền điều khiển mà không chờ thân phương thức chạy xong.

**Scheduling** đưa thời gian vào bài toán. `TaskScheduler` hoặc `@Scheduled` quyết định khi nào task đủ điều kiện chạy: một lần tại một thời điểm, định kỳ fixed rate/fixed delay, theo cron hoặc theo `Trigger` tùy biến.

```text
chạy ngay           → TaskExecutor
gọi method async    → proxy + @Async + TaskExecutor
chạy theo thời gian → TaskScheduler / @Scheduled
```

Phân biệt này giúp tránh tinh chỉnh sai chỗ. Tăng async executor pool không thay đổi cron expression; tăng scheduler pool không sửa được self-invocation của `@Async`; và cả hai cũng không tự động truyền `ThreadLocal` của bên gọi sang thread thực thi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-vs-jdk-concurrency">Trách nhiệm của Spring và Java Concurrency</a>

<details>
<summary>Xem chi tiết</summary>

Spring xây trên Java Concurrency chứ không che đi các quy tắc bảo đảm tính đúng đắn của Java.

**JDK sở hữu** hành vi thread, Java Memory Model, synchronization/atomicity, ngữ nghĩa `Executor`/`Future`, cơ chế tiếp nhận tác vụ của `ThreadPoolExecutor`, `ScheduledExecutorService`, interruption và ngữ nghĩa JDK 21 Virtual Thread. Đây là nền tảng của module Java Concurrency.

Spring sở hữu **lớp tích hợp ở cấp ứng dụng**: abstraction executor/scheduler, cấu hình theo bean, adapter cho môi trường triển khai, `@Async` dựa trên proxy, scheduling dựa trên annotation, task decoration, phối hợp vòng đời và contract/exception riêng của Framework tại các ranh giới đó.

Hai hệ quả thực tế:

- Spring không thể làm shared mutable state tự nhiên an toàn chỉ vì code chạy qua `@Async`.
- Spring có thể làm executor/scheduler dễ cấu hình, thay thế, inject, quan sát và dừng nhất quán với ứng dụng.

Nếu vấn đề là visibility, race, lock, interruption hay ngữ nghĩa runtime của Virtual Thread, hãy quay về Java Concurrency. Nếu vấn đề là bean nào xử lý task, proxy interception, đăng ký lịch chạy, task decoration hoặc hành vi khi context đóng, nó thuộc module này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="container-managed-task-infrastructure">Executor và scheduler do container quản lý</a>

<details>
<summary>Xem chi tiết</summary>

Đưa executor/scheduler vào Spring management biến chính sách thực thi thành hạ tầng của ứng dụng. Nó có thể được đặt tên, inject, cấu hình tập trung, decorate và phối hợp với vòng đời của `ApplicationContext` thay vì được tạo tùy ý trong business code.

Trong ứng dụng Spring thông thường, bean như `ThreadPoolTaskExecutor` hoặc `ThreadPoolTaskScheduler` sở hữu JDK executor cục bộ bên dưới. Trong môi trường Jakarta EE có quản lý thread, Spring có thể delegate qua `DefaultManagedTaskExecutor` hoặc `DefaultManagedTaskScheduler` để dùng managed executor service của môi trường thay vì ứng dụng tự tạo thread.

Cần phân biệt: **Spring-managed bean** và **Jakarta EE managed thread** không phải cùng một khái niệm. Spring có thể quản lý vòng đời của bean trong cả hai trường hợp, nhưng quyền sở hữu thread bên dưới khác nhau.

Việc tập trung chính sách cũng tạo một nơi rõ ràng để trả lời câu hỏi vận hành: dùng pool hay thread-per-task? Chính sách queue/admission là gì? Thread name nào xuất hiện trong log? Context được decorate ra sao? Shutdown xử lý thế nào? Phương thức `@Async`/`@Scheduled` nào dùng hạ tầng này?

Tránh tạo raw executor rải rác trong service method nếu vòng đời của chúng không thực sự cục bộ và được quản lý đầy đủ. Executor sống lâu là tài nguyên hạ tầng và cần chủ sở hữu rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)
