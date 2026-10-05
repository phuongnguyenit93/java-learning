<a id="back-to-top"></a>

# Truyền context qua ranh giới tác vụ

## Menu
- [Vì sao thread-bound context bị mất?](#context-propagation-problem)
- [TaskDecorator: capture, restore và cleanup](#task-decorator)
- [ContextPropagatingTaskDecorator trong Spring 6.1](#context-propagating-task-decorator)
- [Ranh giới của security, transaction, request và reactive context](#context-ownership-boundaries)
- [Executable evidence cho context propagation](#spring-async-demo)

## <a id="context-propagation-problem">Vì sao thread-bound context bị mất?</a>

<details>
<summary>Xem chi tiết</summary>

Ranh giới thread cũng là ranh giới context. Trạng thái trong `ThreadLocal` thuộc về thread đang giữ nó; gửi một `Runnable` sang thread khác không tự động sao chép MDC, request attribute, tenant information, trace context hay `ThreadLocal` bất kỳ của ứng dụng.

```text
thread bên gọi
  ThreadLocal = REQUEST-123
      ↓ gửi task
thread worker
  ThreadLocal = trạng thái vốn có của worker
```

Với thread pool, vấn đề còn nguy hiểm hơn vì worker được tái sử dụng. Nếu task gán context rồi quên khôi phục/xóa, task không liên quan chạy sau đó trên cùng worker có thể nhìn thấy dữ liệu cũ.

Vì vậy câu hỏi thiết kế không chỉ là "copy ThreadLocal thế nào?" mà phải là:

1. Task bất đồng bộ thực sự cần context nào?
2. Abstraction nào sở hữu context đó?
3. Có thể truyền một giá trị immutable nhỏ qua tham số phương thức không?
4. Nếu truyền context gắn với thread là hợp lý, việc capture/restore/cleanup được tập trung ở đâu?

Với định danh nghiệp vụ, truyền tường minh qua tham số thường an toàn nhất. Task decoration hữu ích khi context hạ tầng như logging hoặc observation phải đi theo nhiều lần gửi task một cách nhất quán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-decorator">TaskDecorator: capture, restore và cleanup</a>

<details>
<summary>Xem chi tiết</summary>

`TaskDecorator` cho executor một hook tập trung để bao callback thực thi trước khi chạy. Mẫu chuẩn là **capture ở thread gửi task → restore ở thread thực thi → chạy task → restore/clear trạng thái cũ của worker trong `finally`**.

`DemoTaskDecorator` của module cố ý truyền ba loại context để làm minh chứng:

- `DemoContext`, custom `ThreadLocal` chỉ dùng cho học tập;
- SLF4J MDC để liên kết log;
- Spring `RequestAttributes` từ `RequestContextHolder`.

```text
thread bên gọi
  capture context
      ↓
decorate(Runnable)
      ↓
worker
  lưu context cũ của worker
  cài context đã capture
  chạy callback
  finally restore context cũ
```

Phần `finally` là bắt buộc để bảo đảm tính đúng đắn. Chỉ gán context trước khi chạy mà không dọn dẹp sẽ tạo rò rỉ dữ liệu khi worker thread được tái sử dụng.

Decorator cũng **không** phải bộ xử lý exception chung cho mọi luồng async. Spring có thể decorate một callback thực thi đang bao task của người dùng. Với việc gửi tác vụ trả future, exception có thể bị giữ trong `FutureTask`/handle completion thay vì thoát trực tiếp khỏi `Runnable.run()`. Hãy quan sát lỗi bằng cơ chế đúng của từng contract: future completion, `AsyncUncaughtExceptionHandler`, rejection handling hoặc scheduler error handling.

### Lưu ý về vòng đời request

Truyền `RequestAttributes` không kéo dài vòng đời của HTTP request. Task chạy nền có thể sống lâu hơn request và không nên giả định mọi request-scoped object còn dùng được vô thời hạn. Nếu task chỉ cần request id, tenant id hoặc principal id, sao chép các giá trị immutable cần thiết thường an toàn hơn giữ cả request context.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-propagating-task-decorator">ContextPropagatingTaskDecorator trong Spring 6.1</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework 6.1 bổ sung `ContextPropagatingTaskDecorator` cho một nhu cầu hạ tầng phổ biến: capture context từ thread gửi task và restore quanh lúc thực thi bằng `ContextSnapshotFactory` của Micrometer Context Propagation.

Trường hợp dùng điển hình là logging/observation context:

```text
context accessor đã đăng ký
      ↓ capture snapshot
submit task
      ↓
worker thread
      ↓ restore snapshot scope
run task
      ↓ close scope / restore giá trị cũ
```

Cách này có hệ thống hơn việc tự viết sao chép/dọn dẹp cho từng executor, nhưng nó không phải phép màu sao chép mọi `ThreadLocal`. Context nào capture được phụ thuộc vào các context accessor đã đăng ký với hạ tầng Micrometer Context Propagation.

Để dùng decorator này, Micrometer Context Propagation cũng phải có trên runtime classpath của ứng dụng. Class nằm trong Spring Core nhưng cơ chế snapshot mà nó delegate tới là hạ tầng của Micrometer, không phải một kho context riêng do Spring tự tạo.

Việc truyền context cũng có chi phí: mỗi task phải capture và restore context. Javadoc của Spring lưu ý decorator này không phù hợp nếu ứng dụng chạy số lượng rất lớn task cực nhỏ và overhead propagation trở nên đáng kể.

Nên dùng khi ứng dụng đã có mô hình context mà Micrometer Context Propagation hiểu và việc giữ context xuyên qua ranh giới thread có giá trị. Với giá trị nghiệp vụ vốn tự nhiên là tham số phương thức, truyền trực tiếp vẫn có thể rõ ràng hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-ownership-boundaries">Ranh giới của security, transaction, request và reactive context</a>

<details>
<summary>Xem chi tiết</summary>

Các loại "context" khác nhau có chủ sở hữu và vòng đời khác nhau. Task decorator tổng quát không nên copy tất cả một cách tùy ý.

- **Logging/observation context** phù hợp với cơ chế propagation tổng quát vì mục tiêu là liên kết việc thực thi qua nhiều ranh giới. `ContextPropagatingTaskDecorator` được thiết kế cho nhóm use case này.
- **Security context** có cơ chế propagation riêng của Spring Security cùng các quy tắc dành riêng cho security. Ngữ nghĩa đó thuộc curriculum Security.
- **Transaction** thông thường là phạm vi tài nguyên gắn với thread. `@Async` chuyển execution sang thread khác; không được giả định transaction của bên gọi tự đi theo. Ranh giới transaction trong async execution phải được tạo theo abstraction của Transaction Management.
- **Request context** thuộc vòng đời của web request. Chỉ sao chép dữ liệu vẫn còn ý nghĩa sau khi bên gọi return.
- **Reactive context** thuộc reactive chain, không phải cơ chế truyền `ThreadLocal` thông thường. Ngữ nghĩa Reactor Context/backpressure thuộc Reactive Programming/Spring Reactive.

Quy tắc hữu ích: truyền **identity/correlation context** có chủ đích; tạo lại **phạm vi tài nguyên** như transaction trong abstraction sở hữu nó; truyền dữ liệu nghiệp vụ một cách tường minh. Cách này tránh biến executor decorator thành một context framework thứ hai đầy rủi ro.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-async-demo">Executable evidence cho context propagation</a>

<details>
<summary>Xem chi tiết</summary>

Module có thử nghiệm tập trung tại:

```text
TaskExecutorController#asyncWithContext()
GET /spring-executor/async-context
```

Controller ghi lại tên và id của thread bên gọi, gán `DemoContext=REQUEST-123` và MDC `requestId=REQUEST-123`, sau đó gọi `TaskExecutorService#runAsync(...)` qua một Spring bean khác:

```java
@Async("threadLearningTaskExecutor")
public CompletableFuture<Map<String, Object>> runAsync(
        String callerThread,
        long callerThreadId
) { ... }
```

`threadLearningTaskExecutor` được cấu hình với `DemoTaskDecorator`. Response cho thấy:

- tên và id của thread bên gọi cùng worker thread;
- hai thread id có khác nhau hay không;
- custom context mà worker quan sát được;
- MDC request id worker quan sát được;
- Spring `RequestAttributes` có hiện diện ở worker hay không.

Minh chứng mong đợi gồm caller/worker thread id khác nhau và worker vẫn nhận được demo/MDC value đã capture. Thread name vẫn hữu ích cho chẩn đoán, nhưng phép kiểm tra identity dùng id thay vì dựa vào tên thread. Decorator còn restore trạng thái cũ của worker trong `finally`. Controller cũng restore trạng thái demo/MDC cũ của bên gọi sau khi gửi task để chính thử nghiệm không làm rò trạng thái trên request thread.

Thử nghiệm chứng minh *cơ chế hoạt động*, không khuyến nghị truyền toàn bộ trạng thái request vào job chạy lâu. Mã production nên capture tập context nhỏ nhất và ổn định mà task thực sự cần.

</details>

- [Quay lại đầu trang](#back-to-top)
