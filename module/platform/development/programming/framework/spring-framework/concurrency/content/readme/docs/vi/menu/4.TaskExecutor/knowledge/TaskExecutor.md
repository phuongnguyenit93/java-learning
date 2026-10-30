<a id="back-to-top"></a>

# TaskExecutor

## Menu
- [TaskExecutor là hợp đồng thực thi tác vụ của Spring](#task-executor-001)
- [execute(Runnable) không mặc nhiên bảo đảm chạy trên luồng khác](#task-executor-002)
- [AsyncTaskExecutor và các phương thức submit()](#task-executor-003)
- [Nhận CompletableFuture qua submitCompletable() khi phiên bản hỗ trợ](#task-executor-004)
- [Tiêm bộ thực thi qua interface để tách khỏi cách triển khai](#task-executor-005)
- [Phân biệt TaskExecutor với TaskScheduler](#task-executor-006)
- [TaskRejectedException và hợp đồng khi không nhận được tác vụ](#task-executor-007)
- [Vai trò của bộ thực thi trong các thành phần Spring khác](#task-executor-008)

## <a id="task-executor-001">TaskExecutor là hợp đồng thực thi tác vụ của Spring</a>

<details>
<summary>Xem chi tiết</summary>

# TaskExecutor là hợp đồng thực thi tác vụ của Spring

## 1. TaskExecutor giải quyết vấn đề gì?

Trong ứng dụng Spring, một thành phần có thể cần giao một công việc cho cơ chế thực thi: gửi email, xử lý tệp, tạo báo cáo hoặc tính toán một phần dữ liệu. Thành phần gọi chỉ cần biết **cách gửi tác vụ**; nó không nên phải tự tạo `Thread`, quản lý hàng đợi và chọn thread cho mỗi lần gọi.

`org.springframework.core.task.TaskExecutor` là hợp đồng (interface) mà Spring cung cấp cho việc đó. Nó **kế thừa `java.util.concurrent.Executor`** và khai báo đúng một thao tác cốt lõi:

```java
public interface TaskExecutor extends java.util.concurrent.Executor {
    @Override
    void execute(Runnable task);
}
```

Đoạn trên là **mô hình chữ ký API**, không phải mã để chép thành một interface mới. Trong ứng dụng thật, hãy import `org.springframework.core.task.TaskExecutor` từ Spring.

`execute(Runnable)` có nghĩa: **yêu cầu một executor thực thi một `Runnable` theo chính sách của implementation**. Nó *không* cam kết task sẽ chạy trên thread khác, không cam kết thời điểm bắt đầu, và không trả về giá trị hay `Future`. Một implementation có thể chạy đồng bộ ngay trên thread gọi, có thể tạo thread mới, hoặc đưa công việc vào thread pool.

Phân biệt ba vai trò:

- **`Runnable`**: *làm việc gì?* Một hàm không có tham số truyền vào `run()` và không trả kết quả trực tiếp.
- **`TaskExecutor`**: *giao việc cho cơ chế thực thi nào?* Hợp đồng `execute(Runnable)` mà code nghiệp vụ phụ thuộc vào.
- **Implementation** như `ThreadPoolTaskExecutor`: *thực thi ra sao?* Quyết định thread, hàng đợi, giới hạn song song, từ chối tác vụ và vòng đời.

Vì chỉ phụ thuộc interface, một service có thể thay executor đồng bộ trong kiểm thử bằng executor sử dụng pool khi triển khai thật, mà không phải sửa logic gọi `execute`.

## 2. Hợp đồng `execute(Runnable)` và những gì không được giả định

Một lời gọi điển hình:

```java
taskExecutor.execute(() -> generateReport(reportId));
```

Đây là **mảnh mã bên trong một service đã có `taskExecutor` và `generateReport`**, không phải chương trình Java chạy độc lập. Nó chỉ biểu thị thao tác submit một `Runnable`.

Luồng xử lý khái niệm:

```text
Service gọi execute(task)
          |
          v
 TaskExecutor (hợp đồng)
          |
          +-- SyncTaskExecutor --------> chạy task ngay trên caller thread
          |
          +-- SimpleAsyncTaskExecutor -> tạo thread mới cho mỗi task
          |
          +-- ThreadPoolTaskExecutor --> thread đang rảnh / hàng đợi / thêm thread
          |
          +-- Adapter khác -----------> chuyển giao cho executor bên dưới
```

Cụ thể:

1. **Giao việc không nhất thiết là bất đồng bộ.** `SyncTaskExecutor` thực thi ngay trong lời gọi `execute`; `ThreadPoolTaskExecutor` thường giao cho thread khác. Không nên suy luận cách chạy chỉ từ tên interface.
2. **`execute` không có kết quả trả về.** Kiểu trả về là `void`, nên một lời gọi thành công chỉ cho biết executor đã xử lý yêu cầu theo hợp đồng của nó. Nếu triển khai bất đồng bộ, task có thể vẫn đang xếp hàng hoặc chạy.
3. **Executor có quyền từ chối.** Khi pool đóng hoặc không nhận thêm việc, Spring có thể ném `TaskRejectedException` (một subtype của `RejectedExecutionException`); tác vụ bị từ chối không được xem là đã xử lý.
4. **Executor không tự làm dữ liệu chia sẻ thành an toàn.** Dù task được xếp hàng đúng, truy cập đồng thời tới một biến `int` hoặc `ArrayList` mutable vẫn cần cơ chế đồng bộ thích hợp.
5. **Interface không có `submit()`, `get()`, `shutdown()` hay `schedule()`.** Những khả năng này nằm ở abstraction mở rộng hoặc API khác.

Một quy tắc thiết kế hữu ích: tham số, dependency hoặc field của service nên là `TaskExecutor` khi service **chỉ cần giao `Runnable`**. Nếu cần dùng Future để chờ kết quả, sử dụng `AsyncTaskExecutor` hoặc API trả về Future tương ứng.

## 3. Các kiểu TaskExecutor thường gặp

| Implementation | Cách chạy | Khi nào phù hợp | Cần lưu ý |
| --- | --- | --- | --- |
| `SyncTaskExecutor` | Chạy task trực tiếp trên caller thread | Kiểm thử xác định, đường xử lý đồng bộ | `execute` có thể chặn tới khi task xong; không tạo bất đồng bộ |
| `SimpleAsyncTaskExecutor` | Tạo thread mới cho mỗi tác vụ, không tái sử dụng pool | Công việc độc lập, trường hợp muốn tránh quản lý pool cố định | Nhiều tác vụ có thể tạo nhiều thread; cần xem giới hạn đồng thời |
| `ThreadPoolTaskExecutor` | Dùng `ThreadPoolExecutor` và hàng đợi được cấu hình | Phần lớn công việc bất đồng bộ cần kiểm soát tài nguyên | Phải hiểu `corePoolSize`, `maxPoolSize`, `queueCapacity`, rejection |
| `ConcurrentTaskExecutor` | Adapter bao quanh một `java.util.concurrent.Executor` sẵn có | Tích hợp executor JDK hoặc runtime cung cấp | Hành vi thực tế theo delegate; adapter không tạo quy tắc lập lịch mới |
| `VirtualThreadTaskExecutor` (Spring hỗ trợ trên JDK 21+) | Mỗi task chạy trên virtual thread | Tác vụ blocking/I/O phù hợp với mô hình virtual thread | Không nên dùng làm cách tự động xử lý mọi giới hạn tải; vẫn cần quản lý tài nguyên ngoài thread |

`TaskExecutor` kế thừa `Executor` nên có thể truyền Spring `TaskExecutor` vào nơi chấp nhận `Executor`. Chiều ngược lại không mặc định đúng: một `Executor` JDK bất kỳ không tự trở thành `TaskExecutor`; dùng adapter phù hợp nếu cần API Spring.

Lưu ý thêm: `AsyncTaskExecutor extends TaskExecutor` là hợp đồng **bất đồng bộ** của Spring; nó bổ sung các phương thức như `submit(Runnable)`, `submit(Callable<T>)`, `submitCompletable(...)` trong các phiên bản Spring hiện đại. `ThreadPoolTaskExecutor` triển khai abstraction mở rộng này.

## 4. Ví dụ Spring Framework có thể chạy trên Java 17+

Ví dụ dưới đây chứng minh ba điểm: client lấy bean bằng interface `TaskExecutor`; pool được Spring quản lý; caller dùng một tín hiệu hoàn tất riêng vì `execute` không trả `Future`.

Tạo dự án Maven tối thiểu:

```text
taskexecutor-demo/
├── pom.xml
└── src/main/java/demo/TaskExecutorContractDemo.java
```

Nội dung `pom.xml` (Spring Framework 6.2.7, không yêu cầu Spring Boot):

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>taskexecutor-demo</artifactId>
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

Nội dung `src/main/java/demo/TaskExecutorContractDemo.java`:

```java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class TaskExecutorContractDemo {

    @Bean("workerPool")
    public ThreadPoolTaskExecutor workerPool() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(4);
        pool.setQueueCapacity(8);
        pool.setThreadNamePrefix("worker-");
        return pool;
        // Spring khởi tạo và hủy bean theo vòng đời context.
    }

    public static void main(String[] args) throws InterruptedException {
        try (var context =
                 new AnnotationConfigApplicationContext(TaskExecutorContractDemo.class)) {
            TaskExecutor taskExecutor =
                    context.getBean("workerPool", TaskExecutor.class);

            AtomicInteger answer = new AtomicInteger();
            CountDownLatch finished = new CountDownLatch(1);

            taskExecutor.execute(() -> {
                try {
                    answer.set(6 * 7);
                } finally {
                    finished.countDown();
                }
            });

            finished.await();
            System.out.println("Result = " + answer.get());
        }
    }
}
```

Tại thư mục gốc dự án, chạy với JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.TaskExecutorContractDemo
```

Dòng stdout do chương trình tạo ra khi tác vụ hoàn thành bình thường:

```text
Result = 42
```

Giải thích từng bước:

1. `@Configuration` và `@Bean("workerPool")` tạo một bean `ThreadPoolTaskExecutor`. Container tự gọi lifecycle khởi tạo thích hợp; **không cần gọi `pool.initialize()` thủ công trong phương thức `@Bean`**.
2. Client lấy bean bằng kiểu `TaskExecutor`, chỉ sử dụng `execute`. Nó không cần phụ thuộc vào phương thức riêng của `ThreadPoolTaskExecutor`.
3. Task chạy qua pool, đặt `answer = 42` rồi gọi `countDown()`. Caller dùng `await()` để chờ trước khi đọc kết quả; quan hệ đồng bộ của `CountDownLatch` cũng giúp dữ liệu đã ghi hiển thị đúng.
4. Khối `try` đóng Spring context; container quản lý việc hủy executor theo lifecycle. Đừng tạo pool mới cho mỗi yêu cầu nghiệp vụ.

Ví dụ được thiết kế sao cho **thứ tự xuất stdout không phụ thuộc vào lịch chạy thread**. Nó không giả định `execute()` trả về khi tác vụ đã xong. Nếu cần xử lý lỗi hoặc kết quả tác vụ như một đối tượng, phần tiếp theo dùng `Future` sẽ phù hợp hơn.

## 5. Sử dụng TaskExecutor qua dependency injection

Trong ứng dụng có nhiều service, hãy inject executor qua constructor và chỉ định bean rõ ràng nếu có nhiều executor cùng kiểu:

```java
@Service
public class ReportService {
    private final TaskExecutor executor;

    public ReportService(@Qualifier("workerPool") TaskExecutor executor) {
        this.executor = executor;
    }

    public void requestReport(String reportId) {
        executor.execute(() -> generateReport(reportId));
    }

    private void generateReport(String reportId) {
        // Logic tạo báo cáo chạy theo chiến lược của executor.
    }
}
```

Đây là **mảnh mã tích hợp Spring**, cần import `@Service`, `@Qualifier`, `TaskExecutor` và có bean `workerPool` trong context. `requestReport` gửi yêu cầu thực thi; trong cấu hình thread pool, lời gọi có thể trở về trước khi báo cáo hoàn thành. Nếu việc tạo báo cáo thất bại sau đó, caller không tự nhận được exception qua `requestReport`.

Ưu điểm của việc inject theo hợp đồng: `ReportService` không phải tự quyết định số thread, queue hoặc shutdown. Cấu hình và giám sát các vấn đề đó nằm ở bean executor.

## 6. Cần kết quả: TaskExecutor khác AsyncTaskExecutor

`TaskExecutor.execute(Runnable)` trả `void`. Nếu cần lấy kết quả và nhận exception bất đồng bộ, Spring cung cấp `AsyncTaskExecutor` mở rộng hợp đồng:

```java
AsyncTaskExecutor executor = /* một bean AsyncTaskExecutor */;
Future<Integer> result = executor.submit(() -> 21 * 2);
int answer = result.get(); // 42
```

Đây là **mảnh mã API**, không phải chương trình biên dịch độc lập: nó cần import, bean executor và xử lý `InterruptedException`, `ExecutionException`. `submit(Callable<T>)` trả `Future<T>`; `Future.get()` chờ kết thúc và trả kết quả hoặc báo lỗi. `submit(Runnable)` trả `Future<?>` dùng để chờ hoàn tất (giá trị thành công là `null`). Với Spring 6+, `submitCompletable(...)` có thể trả `CompletableFuture` để ghép các bước xử lý.

So sánh nhanh:

| Nhu cầu | API thích hợp | Điều caller nhận |
| --- | --- | --- |
| Giao một công việc `Runnable` | `TaskExecutor.execute(task)` | Không có handle kết quả |
| Chạy tác vụ và đợi hoàn tất/lỗi | `AsyncTaskExecutor.submit(task)` | `Future` |
| Chạy `Callable<T>` để lấy giá trị | `AsyncTaskExecutor.submit(callable)` | `Future<T>` |
| Kết hợp nhiều bước bất đồng bộ | `submitCompletable` hoặc `CompletableFuture` | Có thể compose, chờ, xử lý lỗi |

Không nên cố ép `TaskExecutor` thành `ExecutorService` chỉ để gọi `submit`: hai abstraction này có API và vòng đời khác nhau. Cũng không nên dùng một biến thường kèm `Thread.sleep()` để đoán thời điểm task hoàn thành.

## 7. Chính sách thread pool nằm ở implementation

Với `ThreadPoolTaskExecutor`, ba thuộc tính cốt lõi là:

- `corePoolSize`: số thread nền tảng mà pool có thể tạo khi nhận việc.
- `queueCapacity`: số tác vụ có thể chờ trong hàng đợi; giá trị dương tạo hàng đợi có sức chứa tương ứng.
- `maxPoolSize`: giới hạn thread có thể mở rộng khi hàng đợi đã đầy.

Trong cấu hình JDK `ThreadPoolExecutor` mặc định mà Spring bao quanh, trình tự khái quát là: ưu tiên tạo thread tới mức core khi cần; khi đã đạt core, đưa task mới vào queue nếu còn chỗ; khi queue đầy mới tăng số thread tới max; khi không còn khả năng nhận thì từ chối theo chính sách đã cấu hình.

Ví dụ đã cấu hình `core=2, max=4, queue=8`: các tác vụ có thể chờ trong queue khi hai thread lõi bận, sau đó pool mới mở rộng vượt core nếu queue đã đầy. Con số này chỉ là **ví dụ cấu hình**, không phải mức mặc định bắt buộc cho mọi ứng dụng.

Một bẫy quan trọng: `ThreadPoolTaskExecutor` theo mặc định có `corePoolSize=1`, `maxPoolSize` rất lớn và queue gần như không giới hạn. Vì queue có thể tiếp tục nhận việc nên pool thường không cần tăng vượt core. **Chỉ tăng `maxPoolSize` mà giữ queue không giới hạn không đảm bảo tăng throughput song song**. Ngược lại, `queueCapacity=0` dùng cơ chế bàn giao trực tiếp và có thể tạo thread nhanh hơn; cần giới hạn max phù hợp để tránh quá tải.

Khi số task tới nhiều hơn khả năng xử lý, mặc định Spring có thể ném `TaskRejectedException`. Hãy chọn hành vi rõ: phản hồi bận, retry có giới hạn, lưu công việc vào hàng đợi bền vững hoặc áp dụng backpressure, tùy yêu cầu hệ thống. **Executor trong bộ nhớ không thay thế hệ thống hàng đợi bền vững**: task chưa thực thi có thể mất khi tiến trình kết thúc đột ngột.

## 8. Hợp đồng giao việc không tự bao gồm các bảo đảm khác

### Thứ tự và khả năng nhìn thấy bộ nhớ

Vì `TaskExecutor` kế thừa `java.util.concurrent.Executor`, quy tắc của JDK về giao task áp dụng: các hành động của caller **trước khi gửi `Runnable` tới Executor** có quan hệ *happens-before* với việc task thực thi. Nhưng một lời gọi `execute` bất đồng bộ trả về **không** tự công bố mọi thay đổi trong task về caller. Khi cần đọc kết quả, phải dùng `Future.get()`, `CountDownLatch.await()` hoặc cơ chế đồng bộ có hợp đồng rõ ràng.

Ví dụ sai về giả định hoàn tất:

```java
executor.execute(() -> state.value = 42);
System.out.println(state.value); // Có thể đọc giá trị cũ; thiếu tín hiệu hoàn tất.
```

Đây là **đoạn minh họa** giả sử `executor` bất đồng bộ và `state` là dữ liệu chia sẻ. Đổi thành `SyncTaskExecutor` có thể làm ví dụ *trông đúng* do chạy cùng thread, nhưng khi thay implementation thành pool thì lỗi xuất hiện. Đó là lý do không viết code nghiệp vụ dựa trên chi tiết thực thi ngoài hợp đồng.

### Exception, rejection và quan sát

Có hai thời điểm lỗi khác nhau: **lúc giao việc** (`TaskRejectedException` do executor không nhận task), và **lúc chạy task** (exception bên trong `Runnable`). Với executor bất đồng bộ và `execute`, exception trong worker không mặc nhiên được ném lại ở thread gọi. Muốn truyền kết quả/lỗi về caller, dùng Future hoặc cơ chế thông báo rõ ràng. Với Future, kiểm tra `ExecutionException`, ngắt, hủy và timeout thay vì luôn giả định thành công.

### Dữ liệu và ngữ cảnh theo thread

`ThreadLocal`, MDC logging, security context và transaction context của thread gọi thường **không tự chuyển nguyên vẹn** sang thread worker. Khi cần truyền một phần context có chủ đích, dùng cơ chế phù hợp (ví dụ `TaskDecorator`) và phải khôi phục/xóa context trong `finally` để tránh rò rỉ sang task kế tiếp trong pool. `TaskDecorator` wrap callback thực thi; với Future, callback được wrap có thể không phải chính `Runnable` gốc và không nhất thiết phơi ra exception của task để decorator bắt trực tiếp.

### Vòng đời

Một executor là bean Spring thường được khởi tạo và đóng theo application context. Nếu tự tạo `ThreadPoolTaskExecutor` bằng `new` **ngoài Spring container**, cần chủ động `initialize()` và shutdown khi không dùng nữa. `TaskExecutor` interface thuần không có `shutdown()`; không nên viết service nghiệp vụ tự ép kiểu để dừng bean executor dùng chung.

## 9. Quan hệ với @Async, TaskScheduler và Spring Boot

- **`TaskExecutor`**: cách một thành phần chủ động gọi `execute(task)` để giao `Runnable`.
- **`@Async`**: cơ chế khai báo trên phương thức; khi được bật bằng `@EnableAsync`, lời gọi đi qua Spring proxy được interceptor chuyển thành tác vụ trên executor. Có thể chọn bean bằng `@Async("workerPool")`. Tự gọi `this.someAsyncMethod()` trong cùng bean thường bỏ qua proxy ở chế độ mặc định.
- **`TaskScheduler`**: hợp đồng khác dùng để lên lịch theo thời điểm, chu kỳ hoặc `Trigger`; `TaskExecutor` chỉ nhận tác vụ để thực thi, không tự định nghĩa lịch cron.
- **Spring Boot**: có thể tự cấu hình executor theo điều kiện của Boot. **Đó là tiện ích của Boot**, không phải thuộc tính mặc định của interface `TaskExecutor` trong Spring Framework thuần.

Ví dụ tối giản để nhìn thấy ranh giới:

```java
// Tự giao Runnable:
taskExecutor.execute(() -> processOnce());

// Đánh dấu phương thức: framework gọi executor qua proxy khi đã bật @EnableAsync.
@Async("workerPool")
public void processLater() {
    processOnce();
}
```

Đây là **mảnh mã minh họa ngữ cảnh Spring**, không phải một class Java độc lập; `@Async` cần import và cấu hình, đồng thời phải được gọi qua proxy phù hợp. Dùng `@Async` không làm cho mọi lời gọi trực tiếp đến phương thức trong cùng object đều bất đồng bộ.

## 10. Lỗi thường gặp khi học TaskExecutor

1. **Nghĩ `TaskExecutor` đồng nghĩa thread pool.** Nó chỉ là hợp đồng; `SyncTaskExecutor` chạy trên caller thread. Kiểm tra implementation thực tế.
2. **Nghĩ `execute()` trả về là task xong.** Điều đó chỉ đúng nếu chiến lược thực thi đồng bộ. Với pool, cần handle hoặc tín hiệu hoàn tất.
3. **Đòi `get()` từ biến khai báo `TaskExecutor`.** Interface này chỉ có `execute`. Dùng `AsyncTaskExecutor` khi cần Future.
4. **Tự tạo thread/pool trong mỗi HTTP request.** Dễ mất kiểm soát tài nguyên và lifecycle. Inject bean executor dùng chung với giới hạn thích hợp.
5. **Chỉ chỉnh `maxPoolSize` mà để queue rất lớn.** Pool có thể không mở rộng như mong đợi; hiểu mối quan hệ core → queue → max.
6. **Bỏ qua task rejection và lỗi bên trong worker.** Phân biệt lỗi gửi việc với lỗi xử lý; theo dõi và xác định chính sách thông báo/retry.
7. **Phụ thuộc vào `ThreadLocal` của caller.** Xác định context nào cần truyền; không mặc định transaction hoặc dữ liệu thread-local đi theo task.
8. **Dùng `Thread.sleep()` để đợi hoàn thành.** Ngủ không cung cấp bảo đảm đồng bộ; dùng Future/latch/CompletionStage.
9. **Lẫn `TaskExecutor` với `TaskScheduler` hoặc `@Async`.** Một thứ là hợp đồng thực thi, một thứ là lên lịch, một thứ là cách kích hoạt bất đồng bộ qua proxy.

## 11. Tự kiểm tra hiểu bài

Hãy thử trả lời:

- Nếu đổi bean từ `ThreadPoolTaskExecutor` sang `SyncTaskExecutor`, lời gọi `execute` còn hợp lệ không? **Có**, vì cả hai tuân theo hợp đồng `TaskExecutor`; hành vi threading thay đổi.
- Tại sao caller không thể gọi `taskExecutor.get()`? **Vì `TaskExecutor` không quản lý một kết quả cụ thể và không có phương thức đó**.
- Cần chắc chắn tác vụ đã tạo xong kết quả để HTTP response đọc được: `execute` đủ chưa? **Chưa**; cần cơ chế chờ kết quả rõ ràng hoặc thiết kế workflow trả kết quả bất đồng bộ.
- Pool có `maxPoolSize=20` nhưng queue không giới hạn; liệu nó luôn dùng tới 20 thread? **Không**; queue thường nhận việc sau khi đạt core nên max có thể không được sử dụng.
- Nếu cùng một service chạy được khi dùng `SyncTaskExecutor` nhưng sai khi chuyển sang pool, nên kiểm tra gì? **Giả định hoàn tất ngay, dữ liệu chia sẻ, thứ tự và context theo thread**.

**Điểm cần nhớ:** `TaskExecutor` định nghĩa *cách giao việc*, còn cách thực thi thuộc implementation. Thiết kế đúng là chọn mức abstraction sát với nhu cầu: `execute` khi chỉ cần giao `Runnable`, `AsyncTaskExecutor`/Future khi cần kết quả, và một cơ chế đồng bộ hoặc workflow rõ ràng khi nhiều task phụ thuộc nhau.

### Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — `TaskExecutor` JavaDoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/TaskExecutor.html)
- [Spring Framework — `AsyncTaskExecutor` JavaDoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/AsyncTaskExecutor.html)
- [Spring Framework — `ThreadPoolTaskExecutor` JavaDoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-executor-002">execute(Runnable) không mặc nhiên bảo đảm chạy trên luồng khác</a>

<details>
<summary>Xem chi tiết</summary>

# execute(Runnable) không mặc nhiên bảo đảm chạy trên luồng khác

> **Chủ đề:** Spring Framework – Concurrency / TaskExecutor  
> **Ý chính:** `TaskExecutor.execute(Runnable)` là yêu cầu thực thi một tác vụ theo chiến lược của executor. Chỉ nhìn vào lời gọi `execute(...)` **không thể kết luận** tác vụ sẽ chạy trên một thread khác, không thể kết luận luôn trả về ngay và cũng không thể kết luận tác vụ đã chạy xong khi phương thức trả về.

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Đọc đúng hợp đồng của `java.util.concurrent.Executor` và `org.springframework.core.task.TaskExecutor`.
2. Giải thích vì sao `SyncTaskExecutor` thực thi ngay trong **luồng gọi**, trong khi `SimpleAsyncTaskExecutor` và `ThreadPoolTaskExecutor` thường giao việc cho luồng khác.
3. Chỉ ra tình huống `CallerRunsPolicy` khiến một thread pool chạy task ngay trên luồng gọi dù bình thường pool có worker.
4. Phân biệt **gửi tác vụ**, **bắt đầu tác vụ**, **hoàn thành tác vụ**, **chạy bất đồng bộ** và **chạy trên một luồng khác**.
5. Viết ví dụ Java 17 và kiểm thử đáng tin bằng định danh `Thread`, `CountDownLatch`, thay vì đoán qua log hoặc `Thread.sleep(...)`.
6. Chọn cấu hình executor, xử lý từ chối và quản lý ngữ cảnh luồng thích hợp trong ứng dụng Spring.

## 1. `execute(Runnable)` thật sự cam kết điều gì?

Trong Java, giao diện `Executor` có một phương thức cơ bản:

~~~java
public interface Executor {
    void execute(Runnable command);
}
~~~

Trong Spring, `TaskExecutor` kế thừa `Executor` và cũng cung cấp `execute(Runnable task)`. Hai giao diện tách **mã tạo công việc** khỏi **cách chạy công việc**. Người gọi đưa một `Runnable` cho executor; chính implementation và cấu hình của executor quyết định nó chạy ở đâu và khi nào.

Bởi vậy:

- `execute` **không đồng nghĩa** `new Thread(task).start()`.
- Một implementation có thể gọi thẳng `task.run()` trong cùng call stack, vì vậy tác vụ hoàn thành **trước khi `execute` trả về**.
- Một implementation khác có thể đưa `task` vào hàng đợi để worker chạy sau; task cũng có thể bắt đầu **trước khi `execute` trả về**.
- Có implementation dùng thread mới, dùng worker có sẵn, hoặc ủy quyền cho executor khác. Không phải mọi `TaskExecutor` đều là thread pool.
- Task có thể bị từ chối khi không còn khả năng nhận việc hoặc đã shutdown. Tùy chính sách từ chối, lời gọi có thể ném lỗi, tự chạy trong caller hoặc bỏ tác vụ. Vì vậy, chỉ thấy `execute` trả về không đồng nghĩa tác vụ chắc chắn đã hoàn thành.

Spring Javadoc của `TaskExecutor` ghi rõ các chiến lược thực thi có thể **synchronous** hoặc **asynchronous**. `execute` trả về kiểu `void`: nó không cung cấp kết quả hay handle chờ hoàn thành. Nếu cần phản hồi kết quả/thất bại, hãy chọn API trả `Future`/`CompletableFuture` và quản lý hợp đồng executor tương ứng.

### Đừng đồng nhất ba câu hỏi

| Câu hỏi | Ý nghĩa | Có suy ra chỉ từ `execute(Runnable)` không? |
| --- | --- | --- |
| Tác vụ chạy trên **cùng thread** hay **thread khác**? | Thread identity | **Không** |
| Lời gọi `execute` có **trả về nhanh** không? | Độ trễ của caller | **Không** |
| Khi `execute` trả về, task có **hoàn thành** không? | Trạng thái hoàn thành | **Không** |

Ví dụ, `SyncTaskExecutor` làm cả ba điều theo cách dễ đoán: **cùng thread**, caller bị chặn trong khi chạy task, và task đã kết thúc trước khi `execute` trả về thành công. Một executor có hàng đợi có thể giao việc cho worker nhưng vẫn chặn caller lúc chờ tài nguyên. Việc “không chặn” và việc “không chạy cùng thread” là hai tính chất cần xét riêng.

## 2. Hình dung đường đi của lời gọi

### Trường hợp A: thực thi đồng bộ ngay tại caller

~~~text
Thread request/main
    |
    | gọi executor.execute(task)
    |       |
    |       +-- task.run()
    |       |     làm việc / có thể ném exception
    |       +-- run() kết thúc
    |
    +-- execute() trả về
    +-- câu lệnh tiếp theo
~~~

Đây là `SyncTaskExecutor`: nó gọi `Runnable.run()` trực tiếp. Trong trường hợp này không xuất hiện một thread thực thi task mới chỉ vì ta dùng API executor.

### Trường hợp B: chuyển việc sang worker

~~~text
Thread request/main                 Thread worker
    |                                     |
    +-- execute(task)                     |
    |      +-- nhận/đưa vào queue --------+-- lấy task
    +-- có thể tiếp tục                    +-- task.run()
    |                                     +-- hoàn thành
~~~

Đây là chiến lược thông thường của `ThreadPoolTaskExecutor` khi pool chấp nhận tác vụ. Thời điểm worker bắt đầu, thời điểm caller quay về và thời điểm task hoàn thành **không có thứ tự tổng quát cố định** nếu không có một cơ chế chờ/đồng bộ rõ ràng.

### Trường hợp C: pool đầy và chính sách chuyển sang caller

~~~text
Thread request/main                 Thread pool
    |                                     |
    +-- execute(task)                     +-- mọi worker bận
    |      +-- queue đầy                   +-- queue đầy
    |      +-- CallerRunsPolicy
    |             +-- task.run()
    |             +-- hoàn thành
    +-- execute() trả về
~~~

Trong cấu hình này, một executor bình thường chạy async có thể thực thi **task bị từ chối bởi pool ngay trên luồng gọi**. Đó là điểm cần kiểm tra kỹ khi chọn chính sách chống quá tải.

## 3. Ví dụ Java SE 17 độc lập: cùng API, hai cách chạy

Đoạn này chỉ dùng Java SE để chứng minh hợp đồng `Executor` cho phép nhiều chiến lược. Tạo tệp **`ExecutorDispatchDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExecutorDispatchDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread caller = Thread.currentThread();

        // Hợp lệ: implementation chạy trực tiếp trong thread gọi.
        Executor direct = Runnable::run;

        System.out.println("before direct");
        direct.execute(() -> System.out.println(
                "direct same thread = " + (Thread.currentThread() == caller)));
        System.out.println("after direct");

        // ExecutorService này tạo một worker riêng để xử lý task.
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch completed = new CountDownLatch(1);

        try {
            pool.execute(() -> {
                try {
                    System.out.println(
                            "pool same thread = " + (Thread.currentThread() == caller));
                } finally {
                    completed.countDown();
                }
            });

            completed.await(); // Chờ task in xong, không dựa vào sleep.
            System.out.println("after pool completed");
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac ExecutorDispatchDemo.java
java ExecutorDispatchDemo
~~~

**Stdout xác định:**

~~~text
before direct
direct same thread = true
after direct
pool same thread = false
after pool completed
~~~

Giải thích:

- `Runnable::run` là một implementation hợp lệ của `Executor`: truyền `Runnable` vào, nó gọi phương thức `run()` ngay trên caller.
- `Executors.newSingleThreadExecutor()` sử dụng một worker riêng; so sánh **đối tượng `Thread`** trả về `false`.
- `CountDownLatch.await()` giữ thứ tự dòng `after pool completed`; không suy luận thứ tự từ thời gian hệ điều hành xếp lịch.
- `shutdown()` giải phóng executor sau khi xử lý xong. Một chương trình dùng executor sống lâu cần quản lý vòng đời hoặc để Spring container quản lý bean.

Ví dụ này đã chứng minh điều cốt lõi ở mức JDK. Ta tiếp tục áp dụng trực tiếp vào `TaskExecutor` của Spring.

## 4. Ví dụ Spring: `SyncTaskExecutor` và `SimpleAsyncTaskExecutor`

### 4.1. Chuẩn bị dự án nhỏ có thể chạy

Dùng **JDK 17+** và **Maven**. Tạo cấu trúc:

~~~text
executor-spring-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── SpringTaskExecutorDemo.java
~~~

Nội dung **`pom.xml`** (dùng Spring Core 6.2.16 để ví dụ có phiên bản xác định):

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>executor-spring-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-core</artifactId>
            <version>6.2.16</version>
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
            </plugin>
        </plugins>
    </build>
</project>
~~~

Nội dung **`src/main/java/SpringTaskExecutorDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;

import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

public class SpringTaskExecutorDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread caller = Thread.currentThread();

        TaskExecutor sync = new SyncTaskExecutor();

        System.out.println("before sync");
        sync.execute(() -> System.out.println(
                "sync same thread = " + (Thread.currentThread() == caller)));
        System.out.println("after sync");

        TaskExecutor async = new SimpleAsyncTaskExecutor("demo-worker-");
        CountDownLatch finished = new CountDownLatch(1);

        async.execute(() -> {
            try {
                System.out.println(
                        "async same thread = " + (Thread.currentThread() == caller));
            } finally {
                finished.countDown();
            }
        });

        finished.await();
        System.out.println("after async completed");
    }
}
~~~

Chạy trong thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=SpringTaskExecutorDemo
~~~

**Stdout từ chương trình** (các log Maven nếu có không thuộc stdout minh họa):

~~~text
before sync
sync same thread = true
after sync
async same thread = false
after async completed
~~~

### 4.2. Rút ra điều gì từ ví dụ?

- Cả hai biến có **cùng kiểu khai báo** `TaskExecutor`; sự khác biệt nằm ở implementation thực tế.
- `SyncTaskExecutor.execute` gọi trực tiếp `run()` nên thread thực thi là caller. Dòng `after sync` chỉ xuất hiện sau khi task hoàn thành.
- `SimpleAsyncTaskExecutor` khởi chạy luồng khác cho tác vụ, không phải pool tái sử dụng thread theo nghĩa `ThreadPoolTaskExecutor`. Ví dụ dùng latch để xác nhận task đã in trước khi chương trình kết thúc.
- Việc đặt tên thread `demo-worker-...` giúp quan sát khi debug nhưng không thay thế phép so sánh `Thread.currentThread() == caller`.
- `SimpleAsyncTaskExecutor` có thể áp dụng giới hạn đồng thời và chặn lúc nhận thêm việc. Đừng suy từ chữ `Async` rằng submission luôn tức thì hay tài nguyên vô hạn là an toàn.

### 4.3. Phân biệt `TaskExecutor` và `AsyncTaskExecutor`

Spring có subinterface `AsyncTaskExecutor` dành cho executor có hợp đồng thực thi bất đồng bộ và hỗ trợ các API tác vụ trả handle. Javadoc của subinterface này mô tả rằng `execute(Runnable)` được thực thi ở luồng khác, khác với hợp đồng tổng quát của `TaskExecutor`.

Khi sử dụng một executor cụ thể, vẫn phải xem **cấu hình xử lý từ chối và adapter**. Chẳng hạn, tùy chọn `CallerRunsPolicy` của thread pool cố ý chuyển việc về caller lúc pool bị bão hòa. Chính sách vận hành cụ thể có thể làm sai giả định “task này luôn off-thread”, kể cả với đối tượng có interface async. Nếu ứng dụng **bắt buộc** chạy ngoài caller, hãy chọn implementation/cấu hình giữ đúng yêu cầu ấy và xử lý từ chối minh bạch.

## 5. `ThreadPoolTaskExecutor`: bình thường giao cho worker nhưng phải xem chính sách quá tải

`ThreadPoolTaskExecutor` của Spring bọc cấu hình `ThreadPoolExecutor` của JDK. Nó thường nhận task vào worker/queue; task không đồng nghĩa với một thread mới. Worker có thể được dùng lại cho nhiều task, và các task có thể chờ queue.

Trong ứng dụng Spring có dependency `spring-context` (hoặc Spring Boot starter phù hợp), có thể định nghĩa bean cấu hình rõ ràng:

~~~java
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfiguration {
    @Bean("applicationExecutor")
    ThreadPoolTaskExecutor applicationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("application-job-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor; // Spring quản lý initialize và shutdown cho bean.
    }
}
~~~

**Đây là đoạn cấu hình của một ứng dụng Spring**, chưa phải chương trình chạy độc lập. Spring container sẽ khởi tạo bean trước khi nó được inject và sử dụng.

Với `AbortPolicy`, khi pool từ chối công việc, lệnh gọi sẽ ném exception thay vì lặng lẽ chuyển task sang caller. Trong Spring, việc từ chối qua `TaskExecutor` thường được biểu diễn bằng `TaskRejectedException` (thuộc họ `RejectedExecutionException`). Ứng dụng phải quyết định có retry, đưa sang queue bền vững, trả lỗi quá tải hoặc áp dụng cơ chế khác tùy nghiệp vụ.

### 5.1. Tại sao queue và kích thước pool ảnh hưởng tới hành vi?

Quy tắc điển hình của `ThreadPoolExecutor`:

1. Nếu số worker hiện có thấp hơn `corePoolSize`, có thể khởi tạo worker để xử lý task.
2. Khi đủ worker cơ bản, executor thường ưu tiên đưa công việc vào queue.
3. Khi queue không nhận thêm việc, executor có thể tăng worker tới `maxPoolSize`.
4. Khi cả worker và queue đã đạt giới hạn, **rejection policy** quyết định tác vụ tiếp theo.

Với queue không giới hạn thực tế, `maxPoolSize` có thể ít cơ hội phát huy tác dụng; vì thế phải xem đồng thời số worker, giới hạn queue và tốc độ xử lý, không chỉ nhìn tên bean.

### 5.2. `CallerRunsPolicy`: thread pool cũng có thể chạy task trong caller

Nếu cấu hình:

~~~java
executor.setRejectedExecutionHandler(
        new ThreadPoolExecutor.CallerRunsPolicy());
~~~

thì khi pool bão hòa **và chưa shutdown**, task bị từ chối khỏi pool sẽ chạy trực tiếp trong thread đang gọi `execute`. Đây là hình thức **backpressure**: caller bị chiếm dụng để tự làm công việc, nên tốc độ nộp task giảm. Khi executor đã shutdown, `CallerRunsPolicy` có thể **bỏ** task thay vì chạy nó. Không được coi chính sách này là bảo đảm giao việc thành công trong mọi tình huống.

Hệ quả thực tế: một controller hoặc luồng xử lý request có thể mất nhiều thời gian hơn vì nó bất ngờ phải thực thi chính tác vụ nền, làm biến động latency. Đối với code dựa vào phân tách transaction, security context hoặc thread-local, việc chuyển task vào caller còn có thể thay đổi hành vi quan sát được.

### 5.3. Ví dụ Java 17 độc lập ép `CallerRunsPolicy` một cách xác định

Tệp **`CallerRunsDemo.java`** sau chủ động làm bận một worker, lấp đầy queue có dung lượng 1, rồi gửi task thứ ba. Vì mọi điều kiện đều được phối hợp bằng latch, **task thứ ba chắc chắn đi qua đường CallerRunsPolicy** nếu executor chưa shutdown.

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CallerRunsDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread caller = Thread.currentThread();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1,
                0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.CallerRunsPolicy());

        try {
            pool.execute(() -> {
                workerStarted.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            workerStarted.await();     // Worker đang bận.
            pool.execute(() -> { });  // Một slot queue đã đầy.

            // Pool bão hòa: policy sẽ gọi run() trên chính caller.
            pool.execute(() -> System.out.println(
                    "third task same thread = " +
                    (Thread.currentThread() == caller)));
        } finally {
            releaseWorker.countDown();
            pool.shutdown();
        }

        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("pool did not terminate");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CallerRunsDemo.java
java CallerRunsDemo
~~~

Kết quả chính xác:

~~~text
third task same thread = true
~~~

**Giải thích tại sao test đáng tin:** `workerStarted.await()` chắc chắn worker đang giữ task đầu và đợi `releaseWorker`; task thứ hai được đưa vào queue dung lượng 1; task thứ ba không có worker/queue trống. `CallerRunsPolicy` thực hiện chính task thứ ba trên caller. Không cần `Thread.sleep`, không phụ thuộc vào việc máy nhanh hay chậm.

**Lưu ý:** Ví dụ này kiểm tra cơ chế của `ThreadPoolExecutor` trong JDK. Với `ThreadPoolTaskExecutor`, kết quả tương tự áp dụng khi bean dùng cùng rejection policy và task thực sự đi đến trạng thái quá tải. Nếu chọn mặc định `AbortPolicy`, task thứ ba sẽ bị từ chối, **không được chạy trên caller**.

## 6. `execute()` trả về lúc nào và exception đi về đâu?

Không thể suy ra “đã hoàn tất” từ dấu `;` cuối câu lệnh `executor.execute(task);`.

| Cơ chế | Khi nào `execute` thường trả về? | Task ném `RuntimeException` sẽ xuất hiện ở đâu? |
| --- | --- | --- |
| `SyncTaskExecutor` | Sau khi `Runnable.run()` kết thúc | Propagate ngay trong lời gọi của caller |
| `SimpleAsyncTaskExecutor` | Sau khi giao/khởi chạy task, có thể chịu giới hạn đồng thời | Trong thread thực thi task; caller không tự nhận lại exception tác vụ |
| `ThreadPoolTaskExecutor` | Sau khi chấp nhận task, hoặc ném lỗi nếu từ chối | Ở worker khi task chạy; riêng lỗi submission có thể ném tại caller |
| Pool với `CallerRunsPolicy` khi bão hòa | Sau khi caller trực tiếp chạy xong task | Có thể propagate trong caller, theo handler và wrapper thực tế |

Các nhận định trên mô tả **đường xử lý thông thường**; adapter, decorator và policy cụ thể có thể bổ sung hành vi.

Ví dụ dễ mắc lỗi:

~~~java
try {
    taskExecutor.execute(() -> {
        throw new IllegalStateException("task failed");
    });
} catch (IllegalStateException e) {
    // Chỉ chắc chắn bắt được exception tác vụ nếu task chạy đồng bộ
    // và exception được truyền ra đúng lời gọi này.
}
~~~

Đây là **đoạn minh họa** dùng biến `taskExecutor` đã được inject. Với executor bất đồng bộ, catch bên ngoài thường không bắt được lỗi xảy ra sau đó trên worker. Nếu muốn caller có một điểm để chờ và lấy kết quả/thất bại, dùng API trả handle như `Future`, `CompletableFuture` hoặc thiết kế kênh kết quả/queue phù hợp.

Có thể sử dụng `CompletableFuture.runAsync(task, taskExecutor)` vì `TaskExecutor` cũng là `Executor`. Nhưng việc trả `CompletableFuture` **không tự đảm bảo** task chạy khác thread nếu executor được truyền vào là direct/sync. Future là cơ chế theo dõi hoàn thành và exception, không phải bản thân chính sách lựa chọn thread.

## 7. Hệ quả trong ứng dụng Spring

### 7.1. Controller gửi việc nền chưa chắc đã giao sang thread nền

Mẫu sau nhận một `TaskExecutor` qua dependency injection:

~~~java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
    private final TaskExecutor executor;

    public ExportService(
            @Qualifier("applicationExecutor") TaskExecutor executor) {
        this.executor = executor;
    }

    public void exportLater(long reportId) {
        executor.execute(() -> generateReport(reportId));
    }

    private void generateReport(long reportId) {
        // Gọi service hoặc thành phần nghiệp vụ tạo báo cáo.
    }
}
~~~

**Đây là đoạn tích hợp Spring, không phải chương trình Java SE độc lập.** Hành vi phụ thuộc bean thật sự mang tên `applicationExecutor`. Nếu cấu hình bean bằng `SyncTaskExecutor`, lời gọi `exportLater` **chạy tạo báo cáo trên thread hiện tại** rồi mới trả về. Nếu là thread pool bình thường, tác vụ có thể chạy ngoài caller. Nếu cấu hình `CallerRunsPolicy`, tác vụ đôi khi chạy trên caller khi bão hòa.

Vì vậy, tài liệu thiết kế nên ghi rõ **executor bean nào**, **loại implementation nào**, **pool/queue/rejection policy** và **tính chất off-thread có bắt buộc hay không**. Đừng khẳng định “đã chạy background” chỉ vì mã gọi `execute`.

### 7.2. `@Async` là một cơ chế khác, nhưng vẫn phải biết executor được chọn

Spring cung cấp `@Async` để thực thi phương thức qua cơ chế interceptor và executor được cấu hình:

~~~java
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    @Async("applicationExecutor")
    public void sendNotification(String recipient) {
        // Công việc gửi thông báo.
    }
}
~~~

Ứng dụng cần bật `@EnableAsync` hoặc cấu hình tương đương. Khi gọi qua proxy Spring, phương thức được điều phối qua executor được chỉ định. **Self-invocation** (gọi `this.sendNotification(...)` từ chính instance trong proxy mode thông thường) không đi qua proxy, nên không kích hoạt interception `@Async`. Không nhầm một lời gọi `@Async` có hiệu lực với việc gọi trực tiếp `taskExecutor.execute(...)`; chúng có cơ chế tích hợp khác nhau dù cùng dùng executor.

Ngữ nghĩa cuối cùng của việc chạy task còn phụ thuộc bean executor và policy. Với nghiệp vụ cần chạy ngoài luồng xử lý request trong mọi trường hợp được chấp nhận, phải xác minh cả async interception lẫn chiến lược executor.

### 7.3. Transaction, security context, MDC và `ThreadLocal`

Một thay đổi thread là thay đổi **execution context**, không chỉ thay đổi tên hiển thị:

- Với `SyncTaskExecutor`, task chạy trong caller, có thể nhìn thấy các `ThreadLocal` và transaction association gắn với caller tại thời điểm đó.
- Với worker ở luồng khác, transaction association, MDC tracing, request context và security context **không mặc nhiên được chép sang**. Phải dựa vào cơ chế propagation được framework hỗ trợ hoặc truyền dữ liệu tường minh.
- Dùng `TaskDecorator` cho metadata phù hợp và bảo đảm dọn trạng thái sau task nếu tự quản lý `ThreadLocal`; tránh giữ dữ liệu request qua các lần tái sử dụng worker.
- Với `@Transactional`, giao dịch của caller không tự “đi theo” tác vụ chạy ở thread khác. Nếu task cần transaction, hãy thiết kế biên giao dịch cho phần thực thi tương ứng.
- Khi task đôi khi chạy trên caller do `CallerRunsPolicy`, vấn đề không chỉ hiệu năng: giả định về context, logging, transaction và latency có thể khác lúc bình thường.

Vì thế, không nên dùng thread-local làm kênh truyền dữ liệu nghiệp vụ không được khai báo. Hãy truyền các giá trị cần thiết qua argument/message và áp dụng context propagation đúng nhu cầu.

### 7.4. TaskExecutor không tự làm trạng thái chia sẻ thành thread-safe

Task executor điều phối **việc chạy**, không tự khóa các mutable field của bean. Nếu nhiều worker tăng `counter++` trên một singleton `@Service`, vẫn có thể xảy ra lost update. Phải bảo vệ trạng thái bằng cấu trúc thích hợp (`AtomicInteger`, lock, immutable state, transaction ở tầng dữ liệu...) và tách biệt yêu cầu **thread-safety** khỏi yêu cầu **off-thread**.

Ngay cả khi `SyncTaskExecutor` chỉ chạy đồng bộ trong từng lời gọi, hai request khác nhau vẫn có thể gọi cùng singleton qua **hai caller thread khác nhau**. Do đó “executor sync” cũng **không bảo đảm** toàn bộ ứng dụng chỉ có một thread.

## 8. Kiểm thử đúng giả định chạy trên thread nào

Khi cần kiểm tra implementation/cấu hình, lưu tham chiếu đến thread caller rồi so sánh bên trong task, và dùng cơ chế chờ để không đọc kết quả sớm.

Mẫu **minh họa** cho một `TaskExecutor` đã inject:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.core.task.TaskExecutor;

public class ExecutorThreadProbe {
    public static boolean runsOnCaller(TaskExecutor executor)
            throws InterruptedException {
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> actual = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        executor.execute(() -> {
            try {
                actual.set(Thread.currentThread());
            } finally {
                finished.countDown();
            }
        });

        if (!finished.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("task did not finish in time");
        }

        return actual.get() == caller;
    }
}
~~~

Đoạn này là một **lớp hỗ trợ kiểm thử**, không có `main`. Sau khi đã cung cấp executor thật, giá trị trả về nói rằng **lần thực thi đó** có dùng caller hay không. Nó không chứng minh mọi lần chạy trong tương lai sẽ giống vậy, đặc biệt khi chính sách thay đổi theo tải.

Không nên dùng:

- `Thread.sleep(100)` rồi đoán task đã hoàn thành: scheduler và tải máy không cho thời hạn chắc chắn.
- Chỉ nhìn tên thread `pool-1-thread-1`: tên có thể tùy chỉnh; nên so sánh `Thread` hiện tại hoặc kiểm tra id khi cần.
- Chỉ xem thứ tự timestamp của hai dòng log: việc log bất đồng bộ, buffering hoặc độ phân giải timestamp có thể khiến suy luận sai.
- Viết test chỉ kiểm tra rằng `execute` không ném lỗi: test này không đo việc task đã hoàn thành hoặc đã được chạy trên thread khác.
- Ép test “luôn off-thread” trong khi cấu hình thực tế có `CallerRunsPolicy` mà không tạo tải: test có thể pass ở tải nhẹ và sai khi pool bão hòa.

**Đối với kiểm thử quy tắc tuyệt đối**, hãy kiểm tra cả đường bình thường, đường quá tải, shutdown và lỗi từ chối. Đối với kiểm thử chức năng, nên dùng latch/Future và kiểm tra kết quả nghiệp vụ thay vì dựa vào một lịch thực thi cụ thể.

## 9. Chọn cách thực thi theo yêu cầu thực tế

| Nhu cầu | Cách tiếp cận phù hợp | Phải xác minh thêm |
| --- | --- | --- |
| Muốn task chạy ngay, tuần tự trong caller (ví dụ unit test) | `SyncTaskExecutor` | Runtime exception có thể truyền thẳng cho caller |
| Muốn mỗi task chạy trên thread riêng, không cần pool | `SimpleAsyncTaskExecutor` | Giới hạn đồng thời, tài nguyên, vòng đời |
| Muốn xử lý nền bằng worker và queue có giới hạn | `ThreadPoolTaskExecutor` | Pool size, queue capacity, rejection policy, shutdown |
| Muốn giảm tải bằng cách bắt caller tự làm khi bão hòa | `CallerRunsPolicy` | Caller latency có thể tăng, task có thể chạy trên caller |
| Cần giữ off-thread ngay cả khi quá tải | Cấu hình executor chấp nhận hoặc **từ chối** minh bạch, thay vì caller-runs | Xử lý `TaskRejectedException`, retry/backpressure ở tầng phù hợp |
| Cần biết khi nào task xong hoặc nhận lỗi | `Future` / `CompletableFuture` / completion signal | Future không tự quy định thread thực thi |
| Cần xử lý bền vững sau khi request và process kết thúc | Queue/message broker hoặc cơ chế job bền vững | `TaskExecutor` nội bộ JVM không tự bảo đảm lưu trữ, retry, hay exactly-once |

Cần phân biệt **độ trễ request**, **điều kiện nhận task**, **hành vi khi bão hòa** và **yêu cầu tin cậy của công việc**. Một giải pháp đúng cho tác vụ phụ trợ có thể không phù hợp với tác vụ cần đảm bảo xử lý từng đơn hàng hoặc không mất thông báo.

## 10. Lỗi phổ biến và cách sửa

**Sai 1: “Gọi `execute` chắc chắn tạo thread mới.”**  
Sửa: đọc contract và xác định implementation. `SyncTaskExecutor` chạy ngay trong caller; `ThreadPoolTaskExecutor` thường tái sử dụng worker.

**Sai 2: “Có chữ `TaskExecutor` nên task luôn async.”**  
Sửa: `TaskExecutor` là giao diện tổng quát, chấp nhận cả sync và async; xem `AsyncTaskExecutor` và bean cấu hình nếu cần contract chuyên biệt.

**Sai 3: “Thread pool thì tuyệt đối không bao giờ dùng caller.”**  
Sửa: xem `RejectedExecutionHandler`; `CallerRunsPolicy` làm caller chạy task khi pool bão hòa.

**Sai 4: “`execute` trả về là task chạy xong.”**  
Sửa: chỉ đúng với executor đồng bộ hoặc khi có đồng bộ khác. Với async, dùng latch, Future hoặc callback để biết kết quả.

**Sai 5: “Trả về nhanh chứng tỏ task chạy thread khác.”**  
Sửa: một task cực ngắn chạy sync cũng có thể trả về nhanh; một async executor có thể chặn lúc chờ slot. Dùng thread identity/contract.

**Sai 6: “Task throw exception thì try/catch quanh `execute` luôn bắt được.”**  
Sửa: phân biệt lỗi submission với lỗi trong worker. Dùng result handle/log/exception handler tương ứng.

**Sai 7: “Một bean Spring inject là cấu hình được biết chắc.”**  
Sửa: dùng qualifier/tên bean rõ ràng, kiểm tra implementation, queue, pool và policy; môi trường test và production có thể cấu hình khác.

**Sai 8: “`@Async` là đủ, bất kể gọi từ đâu.”**  
Sửa: bảo đảm gọi qua Spring proxy, bật async support và kiểm tra executor, tránh tự gọi cùng instance trong proxy mode thông thường.

**Sai 9: “Task có ThreadLocal/transaction giống caller.”**  
Sửa: task chạy khác thread cần cơ chế truyền context phù hợp; caller-runs có thể khiến hành vi khác giữa tải nhẹ và tải nặng.

**Sai 10: “Một task chạy đúng thread trong một lần test chứng minh hệ thống luôn thế.”**  
Sửa: kiểm tra cả đường từ chối/quá tải/shutdown và áp dụng bất biến thiết kế, không chỉ dựa vào log một lần chạy.

## 11. Tình huống luyện tập

### Bài tập 1: đọc code và dự đoán output

~~~java
TaskExecutor executor = new SyncTaskExecutor();
System.out.println("A");
executor.execute(() -> System.out.println("B"));
System.out.println("C");
~~~

**Đáp án:** `A`, `B`, `C` theo thứ tự vì `SyncTaskExecutor` gọi `run()` trực tiếp. Đoạn code minh họa cần classpath Spring, imports và một phương thức chứa nó để chạy.

### Bài tập 2: thread pool có thể làm HTTP request bị chậm không?

Một controller gọi `execute(...)` tới pool với 2 worker, queue 10 và `CallerRunsPolicy`. Khi tải cao, vì sao latency request tăng đột ngột?

**Đáp án:** khi hai worker bận và queue đầy, handler chạy task trên chính request thread. Controller chỉ tiếp tục sau khi task hoàn thành; đây là backpressure nhưng có thể phá giả định tác vụ luôn chạy nền.

### Bài tập 3: phát biểu nào đúng?

1. Nếu `execute` trả về `void` thì task chạy đồng bộ.
2. `TaskExecutor` không loại trừ implementation đồng bộ.
3. `SimpleAsyncTaskExecutor` và `ThreadPoolTaskExecutor` đều bắt buộc tạo thread mới cho mỗi task.
4. `CallerRunsPolicy` có thể khiến task chạy trong caller khi pool quá tải.

**Đáp án:** (2) và (4). Kiểu trả về `void` không quy định thời điểm/chỗ chạy; thread pool thường tái sử dụng worker.

### Bài tập 4: làm thế nào chứng minh chắc chắn job đã hoàn thành?

Một test gọi `executor.execute(job)` rồi ngay lập tức `assertEquals(1, result.get())`. Test không ổn định với async executor.

**Đáp án:** nếu cần chờ job hoàn thành, truyền `CountDownLatch`/`Future` và chờ có timeout, sau đó mới assert. Không chèn `sleep`. Nếu còn yêu cầu “task không được chạy trên request thread”, kiểm tra/khống chế executor và policy riêng.

## 12. Checklist khi review code có `execute(Runnable)`

1. **Executor nào?** Bean name, concrete class và cấu hình ở runtime đã rõ chưa?
2. **Thread nào?** Có yêu cầu off-thread bắt buộc không? Hợp đồng có bảo đảm, hay đang suy từ tên method?
3. **Quá tải ra sao?** Queue, max threads, `CallerRunsPolicy`, `AbortPolicy` và shutdown đã được thiết kế chưa?
4. **Caller có thể chặn?** Cần xác định ảnh hưởng latency và thực thi trong request transaction.
5. **Biết task đã xong bằng gì?** Latch, Future, callback, trạng thái job hay thông điệp kết quả?
6. **Lỗi được xử lý ở đâu?** Lỗi nhận task khác với exception trong task.
7. **Context nào được truyền?** Transaction, security, MDC, request data và ThreadLocal.
8. **Trạng thái dùng chung đã an toàn?** Executor không bảo vệ hộ mutable fields của singleton.
9. **Test có kiểm soát được interleaving và tình huống overload không?** Tránh lệ thuộc `sleep` hoặc log order.

## 13. Ghi nhớ

- `execute(Runnable)` mô tả **nộp một tác vụ cho executor**; nó **không mặc nhiên yêu cầu chạy trên thread khác**.
- `SyncTaskExecutor` thực thi **đồng bộ trên caller**, còn `SimpleAsyncTaskExecutor`/`ThreadPoolTaskExecutor` được thiết kế cho công việc bất đồng bộ với đặc điểm quản lý thread khác nhau.
- Ngay cả thread pool, `CallerRunsPolicy` có thể chuyển task sang caller khi quá tải; chính sách từ chối cũng quyết định liệu task có được chấp nhận hay không.
- Đừng dùng tốc độ trả về, tên method, timestamp hay `Thread.sleep` để chứng minh thread identity hoặc completion. Hãy xem contract/config và dùng primitive đồng bộ đúng mục đích.
- Trong Spring, tác động thực tế còn liên quan request latency, `@Async` proxy, transaction, security/MDC context, exception handling và lifecycle của executor.

## Tài liệu tham khảo

- Spring Framework Reference — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework Javadoc — `TaskExecutor` (6.2.16): https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/core/task/TaskExecutor.html
- Spring Framework Javadoc — `SyncTaskExecutor` (6.2.16): https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/core/task/SyncTaskExecutor.html
- Spring Framework Javadoc — `AsyncTaskExecutor` (6.2.16): https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/core/task/AsyncTaskExecutor.html
- Java SE 17 — `Executor`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html
- Java SE 17 — `ThreadPoolExecutor` và rejection handlers: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
- Java SE 17 — `CountDownLatch`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-executor-003">AsyncTaskExecutor và các phương thức submit()</a>

<details>
<summary>Xem chi tiết</summary>

# AsyncTaskExecutor và các phương thức submit()

> **Chủ đề:** Spring Framework – concurrency  
> **Nhóm kiến thức:** TaskExecutor  
> **Mục tiêu:** Hiểu hợp đồng của `AsyncTaskExecutor`, lựa chọn đúng giữa `execute()`, `submit(Runnable)` và `submit(Callable<T>)`, theo dõi kết quả qua `Future`, quản lý lỗi, hủy, timeout và tài nguyên khi dùng executor trong ứng dụng Spring.

## 1. AsyncTaskExecutor nằm ở đâu trong hệ sinh thái executor?

Java có `java.util.concurrent.Executor` với phương thức `execute(Runnable)`; Spring khai báo `org.springframework.core.task.TaskExecutor` như một abstraction tương thích `Executor`. Hợp đồng chung của `TaskExecutor` cho phép nhiều cách thực thi, kể cả đồng bộ trên caller thread (ví dụ `SyncTaskExecutor`).

`org.springframework.core.task.AsyncTaskExecutor` **mở rộng `TaskExecutor`**, bổ sung khả năng nộp tác vụ và nhận `Future` hoặc `CompletableFuture` để quan sát kết quả. Theo hợp đồng interface, `execute(Runnable)` của một `AsyncTaskExecutor` thực thi tác vụ **bất đồng bộ trên luồng khác caller**, thay vì chạy `Runnable` ngay trên caller thread. Tuy vậy, **lệnh nộp task** có thể phải đợi theo chính sách giới hạn concurrency của implementation hoặc bị từ chối khi executor quá tải; không được khẳng định mọi lệnh `submit` đều trả về ngay lập tức.

~~~text
java.util.concurrent.Executor
            ^
            |
      Spring TaskExecutor
         |         |
         |         +-- SyncTaskExecutor (TaskExecutor đồng bộ)
         |
         +-- AsyncTaskExecutor
                   |
                   +-- SimpleAsyncTaskExecutor
                   +-- ThreadPoolTaskExecutor
                   +-- ConcurrentTaskExecutor / TaskExecutorAdapter
                   +-- các implementation khác
~~~

Lưu ý: Sơ đồ diễn đạt **quan hệ interface và ví dụ implementation**, không biểu diễn đầy đủ cây kế thừa Java giữa mọi class.

`AsyncTaskExecutor` **không phải là** `ExecutorService`. Dù cùng có hai overload `submit`, interface Spring không có các phương thức `invokeAll()`, `invokeAny()`, `shutdown()` hay overload `submit(Runnable, T result)` của JDK `ExecutorService`. Việc quản lý vòng đời thường do Spring bean và implementation cụ thể thực hiện.

## 2. Các phương thức cốt lõi: execute hay submit?

| Lời gọi | Kiểu đầu vào | Kiểu trả về | Ý nghĩa |
| --- | --- | --- | --- |
| `execute(Runnable)` kế thừa `TaskExecutor` | Công việc không khai báo giá trị trả về | `void` | Chỉ yêu cầu thực thi, không có `Future` từ lời gọi này để đợi/thu hồi lỗi |
| `submit(Runnable)` | `Runnable` với `void run()` | `Future<?>` | Nhận handle theo dõi việc hoàn tất; `get()` trả **null** nếu thành công |
| `submit(Callable<T>)` | `Callable<T>` với `T call()` | `Future<T>` | Nhận handle theo dõi việc hoàn tất và lấy chính giá trị do `call()` trả về |
| `submitCompletable(Runnable)` | `Runnable` | `CompletableFuture<Void>` | Được bổ sung từ Spring Framework **6.0**; thuận tiện ghép các bước xử lý |
| `submitCompletable(Callable<T>)` | `Callable<T>` | `CompletableFuture<T>` | Được bổ sung từ Spring Framework **6.0**; nhận giá trị và composition API |

Hai overload `submit` tồn tại từ lâu trong Spring; từ **Spring Framework 6.1**, interface có default implementation, về mặt ý tưởng sử dụng task thích hợp rồi chuyển đến `TaskExecutor.execute(Runnable)`. Implementation vẫn có thể override cách nộp; `ThreadPoolTaskExecutor` có các override riêng. Đừng dựa vào một loại `Future` implementation cụ thể trong mã nghiệp vụ.

Chữ “async” ở đây mô tả **mô hình thực thi**. Còn `Future` là **đại diện cho kết quả/trạng thái trong tương lai**. `submit` thành công chỉ chứng minh executor **chấp nhận** task, chưa khẳng định task bắt đầu, hoàn thành, hay thành công nghiệp vụ.

### 2.1. submit(Runnable): thực thi công việc, không nhận giá trị từ run()

~~~java
Future<?> future = executor.submit(() -> {
    auditWriter.writeEntry("user-42", "LOGIN"); // minh họa một dependency của ứng dụng
});

// Chờ đến khi Runnable hoàn tất hoặc báo lỗi:
Object result = future.get(); // null nếu Runnable thành công
~~~

Đây là **đoạn ghép vào ứng dụng** đã có biến `executor` kiểu `AsyncTaskExecutor` và `auditWriter`; không phải chương trình biên dịch độc lập. Dùng `Runnable` khi tác vụ thực hiện một hành động và caller chỉ cần trạng thái hoàn tất. Sau khi `get()` trả về bình thường, `result == null` là đúng; không có dữ liệu trả về từ `void run()`.

### 2.2. submit(Callable<T>): nhận kết quả có kiểu

~~~java
Future<Integer> totalFuture = executor.submit(() -> {
    return 10 + 20;
});

int total = totalFuture.get(); // 30
~~~

`Callable<T>` có thể trả `T` và khai báo `throws Exception`. Kiểu `T` được suy luận từ lambda/assignment; `Future<Integer>` giúp compiler kiểm tra kiểu. Khi muốn trả giá trị (tổng hợp thống kê, đọc dữ liệu, tính điểm), đây là overload cần chọn.

**Lỗi overload lambda thường gặp:** một expression lambda như `() -> counter.incrementAndGet()` có thể tương thích với cả `Runnable` và `Callable<Integer>`, gây khó hiểu hoặc lỗi overload. Khi mục đích là `Runnable`, viết block không trả giá trị `() -> { counter.incrementAndGet(); }`, hoặc ép kiểu `(Runnable)` tường minh.

### 2.3. execute(Runnable) khác submit(Runnable) ở đường quan sát lỗi

~~~java
executor.execute(() -> doWork());      // không có Future từ execute()
Future<?> future = executor.submit(() -> doWork()); // có handle
~~~

Với `submit`, failure xảy ra **bên trong task** thường được bắt giữ trong `Future`. Nếu không bao giờ quan sát `Future.get()` hoặc một cơ chế theo dõi khác, lỗi có thể bị bỏ sót. Với `execute`, cách báo/ghi log uncaught exception phụ thuộc implementation, thread và error handling. Cả hai đều có thể ném exception **ngay tại thời điểm nộp**, chẳng hạn `TaskRejectedException`.

## 3. Vòng đời của một Future

Hình dung `Future` như biên nhận của một task:

~~~text
Caller                       AsyncTaskExecutor                 Worker thread
  | submit(Runnable/Callable)       |                                |
  |------------------------------->| kiểm tra pool / queue           |
  |                                | có thể ACCEPT hoặc REJECT       |
  |<----- Future (nếu accept) ------|                                |
  |                                |------------ chạy task -------->|
  | làm việc khác                  |                                |
  | future.get()                   |                       tính toán / lỗi
  | ... có thể phải chờ ...        |                                |
  |<------------------------- kết quả / lỗi / bị hủy <---------------|
~~~

Các API JDK `Future` cần hiểu:

- `get()`: **chờ** task hoàn tất và lấy kết quả. Nếu task thất bại, ném `ExecutionException` với `getCause()` là lỗi gốc. Nếu task bị hủy, ném `CancellationException` (unchecked). Nếu thread đang đợi bị interrupt, ném `InterruptedException`.
- `get(timeout, unit)`: chờ **tối đa** thời gian chỉ định. Ném `TimeoutException` khi **hết thời gian chờ**, không mặc định hủy task đang chạy. Muốn hủy, cần quyết định gọi `cancel(...)` và chấp nhận tính best effort.
- `isDone()`: `true` khi Future đã kết thúc theo một cách nào đó: **thành công, lỗi hoặc bị hủy**. Nó **không đồng nghĩa với thành công**.
- `isCancelled()`: đánh dấu task đã bị cancel theo hợp đồng Future.
- `cancel(false)`: yêu cầu hủy; task đã chạy thường được phép tiếp tục mà không bị yêu cầu interrupt.
- `cancel(true)`: yêu cầu interrupt nếu task đang chạy và implementation biết worker thread. **Không bảo đảm** dừng tức thì, rollback database, thu hồi email đã gửi hay chấm dứt mọi I/O.
- **Memory visibility:** theo hợp đồng `Future` của JDK, hành động trong task *happens-before* những hành động theo sau lời gọi `Future.get()` tương ứng ở thread khác. Điều này giải thích vì sao có thể đọc một kết quả đã được task cập nhật sau khi chờ `get()`; nó không biến mọi shared mutable state của hệ thống thành thread-safe.

Một task có thể chạy và kết thúc trước khi caller nhận được reference `Future`; đó là bình thường. Tương tự, một `Future` chưa hoàn tất không cho biết chính xác nó đang ở queue hay đang chạy nếu implementation không cung cấp thêm metric.

## 4. Ví dụ Spring Framework 6.2.x, Java 17+: hai overload submit() chạy được

Ví dụ tạo `ThreadPoolTaskExecutor` dưới dạng Spring bean, lấy qua **interface `AsyncTaskExecutor`** và nộp ba task: một `Runnable`, một `Callable<Integer>`, một `Callable<Integer>` phát lỗi để quan sát cơ chế `ExecutionException`. Không dùng `@Async`, vì nộp task **trực tiếp** cho executor; không cần bật `@EnableAsync`.

Tạo project Maven:

~~~text
spring-submit-demo/
├── pom.xml
└── src/main/java/demo/SubmitDemo.java
~~~

**pom.xml** (chọn Spring Framework 6.2.9 để ví dụ có phiên bản xác định; yêu cầu JDK 17+ và Maven):

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-submit-demo</artifactId>
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
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/demo/SubmitDemo.java**

~~~java
package demo;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class SubmitDemo {

    @Bean
    public ThreadPoolTaskExecutor workerExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(8);
        pool.setThreadNamePrefix("lesson-");
        return pool;
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(SubmitDemo.class)) {

            AsyncTaskExecutor executor =
                context.getBean(AsyncTaskExecutor.class);
            AtomicInteger counter = new AtomicInteger();

            // Block void: compiler chọn submit(Runnable).
            Future<?> action = executor.submit(() -> {
                counter.incrementAndGet();
            });

            // Expression trả số: compiler chọn submit(Callable<Integer>).
            Future<Integer> calculation = executor.submit(() -> 7 * 6);

            // Bắt lỗi từ một Callable đã được chấp nhận.
            Future<Integer> failure = executor.submit(
                (Callable<Integer>) () -> {
                    throw new IllegalStateException("simulated failure");
                }
            );

            System.out.println("Runnable result = " + action.get());
            System.out.println("Counter = " + counter.get());
            System.out.println("Callable result = " + calculation.get());
            System.out.println("Callable done = " + calculation.isDone());

            try {
                failure.get();
            } catch (ExecutionException ex) {
                System.out.println("Cause = " + ex.getCause().getMessage());
            }
        }
    }
}
~~~

Chạy từ thư mục `spring-submit-demo`:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.SubmitDemo
~~~

Các dòng **do chương trình tự in ra**, có thứ tự và giá trị xác định:

~~~text
Runnable result = null
Counter = 1
Callable result = 42
Callable done = true
Cause = simulated failure
~~~

Giải thích tính xác định:

1. `action.get()` đợi `Runnable` hoàn tất; Runnable không trả giá trị nên in `null`. Cập nhật `AtomicInteger` đã hoàn tất trước khi in `Counter = 1`.
2. `calculation.get()` đợi `Callable<Integer>` và nhận `42`. Sau `get()`, `isDone()` chắc chắn là `true`.
3. `failure.get()` ném `ExecutionException`; `getCause()` là `IllegalStateException` có thông điệp đã xác định. Future này không gây chương trình dừng đột ngột vì được catch.
4. Hai worker có thể thực thi ba task ở các thứ tự khác nhau, nhưng vì caller tự đợi rồi in theo trình tự, **nội dung và thứ tự output vẫn giữ nguyên**.
5. Đóng `AnnotationConfigApplicationContext` qua try-with-resources kết thúc bean lifecycle; `ThreadPoolTaskExecutor` là bean Spring được quản lý và có cơ chế shutdown. Sản phẩm thực tế vẫn phải cấu hình chính sách hoàn tất task đang chờ khi shutdown.

Đây là ví dụ nhỏ về **cách gọi và Future semantics**, không phải bảo đảm cho retry, giao dịch, tác vụ sống qua restart hay toàn bộ nghiệp vụ phân tán.

## 5. Xử lý lỗi đúng ở hai thời điểm khác nhau

**Thời điểm nộp task:**

~~~java
Future<String> future;
try {
    future = executor.submit(() -> externalClient.fetchResult());
} catch (org.springframework.core.task.TaskRejectedException ex) {
    // Pool đóng hoặc quá tải: chưa có Future để theo dõi.
    throw ex;
}
~~~

Đây là **đoạn tích hợp minh họa**, cần các biến `executor` và `externalClient` trong ứng dụng. Nếu caller cần chống lệ thuộc Spring exception, `TaskRejectedException` cũng là một `RejectedExecutionException` theo API hiện đại.

**Sau khi task được chấp nhận:**

~~~java
try {
    String result = future.get(2, java.util.concurrent.TimeUnit.SECONDS);
    processResult(result);
} catch (java.util.concurrent.TimeoutException ex) {
    // Hết thời gian CHỜ, task không tự bị hủy.
    future.cancel(true);  // Best effort; cân nhắc side effect.
} catch (java.util.concurrent.ExecutionException ex) {
    Throwable originalFailure = ex.getCause();
    recordTaskFailure(originalFailure);
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    throw ex;
} catch (java.util.concurrent.CancellationException ex) {
    recordCancellation(ex);
}
~~~

Đây cũng là **đoạn ghép vào ứng dụng**, đặt trong phương thức khai báo `throws InterruptedException` hoặc xử lý lại lỗi, và có các hàm `processResult`, `recordTaskFailure`, `recordCancellation` của nghiệp vụ. Điểm cốt lõi: phân biệt **TaskRejectedException ở submit()** với **ExecutionException ở get()**, và **TimeoutException khi caller chờ quá lâu**. Không chọn timeout/retry tuỳ tiện: cần kiểm soát tác dụng phụ đã chạy và đảm bảo idempotency.

| Hiện tượng | Xảy ra ở đâu | Hành động nên cân nhắc |
| --- | --- | --- |
| `TaskRejectedException` | `submit`/nộp task | Giới hạn tải, phản hồi backpressure, queue bền vững, ghi nhận reject; thường không có Future |
| `ExecutionException` | `Future.get()` | Kiểm tra `getCause()`, phân loại lỗi, retry có kiểm soát nếu idempotent |
| `InterruptedException` | Thread caller đang chờ `get()` | Khôi phục interrupt flag và hủy/thoát theo contract của caller |
| `TimeoutException` | `get(timeout)` | Đánh giá timeout, trả kết quả tạm, có thể yêu cầu cancel; task chưa chắc ngừng |
| `CancellationException` | `get()` trên Future đã cancel | Phân biệt chủ động hủy với lỗi xử lý |
| `isDone() == true` | Kiểm tra trạng thái | Chỉ nói “đã kết thúc”; cần `get()` để biết thành công/thất bại |

**Lưu ý vận hành:** `Future.get()` chặn **thread đang gọi**, không phải chuyển việc chờ sang Spring. Nếu gọi `get()` ngay sau `submit()` trong HTTP handler, bạn đã dùng executor để chạy tác vụ nhưng vẫn giữ request thread đợi; đôi khi cần, nhưng không phải một pipeline bất đồng bộ end-to-end.

## 6. Hủy và interrupt có ý nghĩa giới hạn

`future.cancel(false)` cố hủy công việc chưa bắt đầu hoặc đánh dấu cancel mà không yêu cầu ngắt thread đang chạy. `future.cancel(true)` có thể gửi interrupt đến worker. Cả hai là yêu cầu điều khiển **Future/task**, không phải transaction rollback hay “undo”.

Một tác vụ phản hồi interrupt đúng cách:

~~~java
Future<?> task = executor.submit(() -> {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            performOneBoundedUnitOfWork();
        }
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
    } finally {
        releaseResources();
    }
});

// Khi nghiệp vụ chủ động quyết định dừng:
task.cancel(true);
~~~

Đoạn này minh họa **mẫu kiểm soát vòng đời**, các hàm `performOneBoundedUnitOfWork()` và `releaseResources()` thuộc mã ứng dụng. Ví dụ giả định một đơn vị công việc có thể ném `InterruptedException`, hoàn tất trong giới hạn và được viết an toàn khi thử lại. Nếu underlying API không đáp ứng interrupt, task có thể tiếp tục chạy dù Future đã ở trạng thái cancelled. Cần timeout riêng cho HTTP/database và phương án xử lý tác dụng phụ một phần.

**Không dùng `isDone()` hay `cancel(true)` như cơ chế xác nhận rằng việc ghi DB, gửi email hoặc thanh toán đã được hoàn tác.** Với các công việc quan trọng, trạng thái cuối cùng phải được lưu/kiểm chứng ở hệ thống sở hữu dữ liệu.

## 7. submitCompletable(): lựa chọn cho chuỗi xử lý không chặn

Spring Framework 6.0+ thêm:

~~~java
CompletableFuture<Integer> total = executor.submitCompletable(() -> {
    return expensiveCalculation();
});

CompletableFuture<String> display =
    total.thenApply(value -> "total=" + value)
         .exceptionally(error -> "unavailable");

display.thenAccept(System.out::println);
~~~

Đoạn trên là **mã minh họa tích hợp**, giả định đã có `executor` và `expensiveCalculation()`. `CompletableFuture` cho phép nối `thenApply`, `thenCompose`, `whenComplete`, `exceptionally` và phối hợp nhiều tác vụ mà không cần caller gọi `get()` ngay. `submitCompletable(Runnable)` trả `CompletableFuture<Void>`, hoàn thành bình thường với `null`.

Tuy nhiên, **continuation cũng cần chọn nơi chạy**:

- `thenApply(...)` không có hậu tố `Async` có thể chạy trên thread hoàn tất future hoặc thread gọi thao tác, tùy trạng thái completion.
- `thenApplyAsync(...)` nếu không truyền executor dùng asynchronous facility mặc định của `CompletableFuture` (thường là `ForkJoinPool.commonPool()`), không mặc định quay về pool Spring đã dùng.
- Nếu muốn quy định tài nguyên, dùng `thenApplyAsync(function, executor)` với `AsyncTaskExecutor` đang sở hữu và giới hạn tải.
- `join()` cũng là lời gọi chặn nếu chưa hoàn tất; failure được bọc bằng `CompletionException`. `get()` dùng `ExecutionException`. Việc dùng `CompletableFuture` không tự đảm bảo timeout, idempotency hay transaction context.

`submitCompletable` giúp biểu diễn pipeline tốt hơn `Future` đơn thuần; khi chỉ cần chờ một tác vụ đơn lẻ, `submit(Runnable/Callable)` vẫn là lựa chọn phù hợp.

## 8. Chọn implementation của AsyncTaskExecutor

| Implementation | Đặc điểm | Cần lưu ý |
| --- | --- | --- |
| `ThreadPoolTaskExecutor` | Adapter kiểu Spring cho `ThreadPoolExecutor`; có core/max pool size, queue capacity, thread prefix, metric như active count/queue size | Cần chọn giới hạn và rejection policy; bean lifecycle và shutdown khác nhau theo cấu hình |
| `SimpleAsyncTaskExecutor` | Tạo thread mới cho mỗi task, **không tái sử dụng thread kiểu pool**; có thể giới hạn concurrency | Không dùng như pool vô hạn; trong môi trường JDK 21+ có thể bật virtual threads theo cấu hình |
| `ConcurrentTaskExecutor` | Adapter cho một `java.util.concurrent.Executor` hiện có | Hành vi scheduling/queue/reject đến từ executor được delegate; chọn adapter khi có executor tùy chỉnh |
| `TaskExecutorAdapter` | Adapter Spring cho executor gốc của JDK | Cần hiểu resource ownership, exception translation và semantics của delegate |
| `SyncTaskExecutor` | Thực thi trực tiếp trên caller thread | **Chỉ triển khai `TaskExecutor`**, không thỏa contract async của `AsyncTaskExecutor` |

Ví dụ cấu hình pool giới hạn qua Spring:

~~~java
@Bean("ioExecutor")
ThreadPoolTaskExecutor ioExecutor() {
    ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(4);
    pool.setMaxPoolSize(8);
    pool.setQueueCapacity(100);
    pool.setThreadNamePrefix("io-");
    pool.setRejectedExecutionHandler(
        new java.util.concurrent.ThreadPoolExecutor.AbortPolicy()
    );
    return pool;
}
~~~

Đây là **đoạn @Bean minh họa**, cần đặt trong `@Configuration` và import các kiểu Spring tương ứng. Số 4/8/100 chỉ minh họa, không phải cấu hình phù hợp mọi hệ thống. Với `ThreadPoolTaskExecutor`, queue có capacity dương thường khiến task xếp hàng trước khi tăng worker vượt `corePoolSize`; khi queue đã đầy mới xét mở rộng đến `maxPoolSize`. Vì vậy chỉ tăng `maxPoolSize` nhưng giữ queue rất lớn có thể không làm tăng concurrency như mong đợi.

Lưu ý `ThreadPoolTaskExecutor` mặc định có core size 1 và queue capacity rất lớn; **không** dùng mặc định này như lựa chọn sản phẩm mà chưa đo tải. Khi pool/queue đều hết sức chứa, tác vụ có thể bị từ chối. Nếu dùng policy chạy trên caller thread thay vì abort, thời gian `submit` có thể tăng và caller phải tự quản lý backpressure.

## 9. Chọn đúng abstraction: manual submit, @Async, ExecutorService

| Tình huống | Cách chọn |
| --- | --- |
| Cần chủ động gửi một `Runnable`, nhận `Future` để đợi/hủy | Inject `AsyncTaskExecutor` rồi `submit(Runnable)` |
| Tác vụ trả về `T` | `submit(Callable<T>)` |
| Cần xử lý nhiều giai đoạn/ghép future | `submitCompletable(...)` và continuation có executor rõ ràng |
| Muốn lời gọi phương thức bean được interceptor thực thi async | `@EnableAsync` + `@Async`, gọi qua Spring proxy |
| Cần `invokeAll`, `invokeAny` hoặc shutdown trực tiếp trên một raw Java pool | Sở hữu/inject `ExecutorService` theo contract JDK; không ép interface Spring cung cấp API không có |
| Chỉ cần thực thi fire-and-forget và đã có giám sát lỗi riêng | `execute(Runnable)` có thể đủ, nhưng không nhận Future |

**Manual `submit` không đòi hỏi Spring proxy:** một service có thể gọi `executor.submit(...)` dù đang trong chính instance đó; đây là API trực tiếp của executor. Trái lại, `@Async` dựa vào interceptor trong chế độ proxy mặc định; `this.someAsyncMethod()` thường bỏ qua proxy nên không được điều phối async.

**Không đánh đồng `Future` của manual submit với return type của `@Async` method.** Với `@Async`, proxy là bên điều phối lời gọi và trả future cho caller; cách khai báo return type, propagation và error handling phải theo tài liệu annotation. Bài này tập trung vào `AsyncTaskExecutor.submit`.

## 10. Những trách nhiệm không do submit tự làm

### 10.1. Thread safety và dữ liệu đầu vào

Hai task đồng thời có thể dùng cùng reference đến collection, entity hay bean singleton. `submit` không sao chép, không đóng băng dữ liệu đầu vào và không biến `HashMap` thành thread-safe. Ưu tiên command immutable, biến local, cấu trúc concurrent phù hợp và cơ chế đồng bộ có phạm vi rõ.

### 10.2. ThreadLocal, security và logging

Caller thường có context như MDC, request attributes, principal, transaction resources. Executor thread không tự được bảo đảm có toàn bộ các context này; nếu cần, chọn `TaskDecorator` hoặc security-aware/context propagation adapter tương ứng và dọn context trong `finally`. Không truyền request object sống sang task chạy sau request.

**Điểm khó với TaskDecorator:** ở các lời gọi `submit` của `ThreadPoolTaskExecutor`, wrapper nhận được có thể là `FutureTask` đã bắt exception của công việc gốc. `try/catch` quanh `runnable.run()` ở decorator có thể không thấy lỗi nghiệp vụ; cần theo dõi `Future.get()` hoặc thiết kế quan sát kết quả khác.

### 10.3. Transaction và request boundary

Trong ứng dụng imperative, transaction Spring thường gắn với thread hiện tại. Nộp việc qua `submit` **không chuyển transaction của caller sang thread worker**. Nếu cần ghi DB trong task, mở transaction phù hợp ngay tại worker, ví dụ gọi một `@Transactional` service bean qua Spring proxy hoặc dùng `TransactionTemplate`. Worker có thể chạy trước khi giao dịch caller commit, nên không nên cho task đọc dữ liệu vừa ghi nhưng chưa commit hoặc phát sự kiện ngoại vi theo giả định commit thành công.

### 10.4. Reliability và idempotency

`submit` đưa task vào executor trong **process đang sống**. Nếu JVM bị kill sau khi task được nhận nhưng trước khi xử lý xong, `Future` không phải durable job record. Khi nghiệp vụ cần chạy lại sau restart hoặc chống duplicate, lưu command/sự kiện ở cơ sở dữ liệu hoặc durable queue, thiết kế idempotency key, retry/backoff và trạng thái hoàn tất.

## 11. Lỗi thường gặp và cách sửa

| Lỗi | Vì sao sai | Cách sửa |
| --- | --- | --- |
| “`submit` trả Future nên chắc chắn task thành công” | Future có thể đang chờ, fail hoặc cancel | Quan sát `get`, callback/completion hoặc metric |
| “`future.isDone()` bằng true tức là đã thành công” | Exception và cancellation cũng kết thúc Future | Kiểm tra lỗi/hủy rồi mới công nhận thành công |
| Gọi `get()` ngay sau từng `submit` trong vòng lặp | Vô tình tuần tự hóa phần chờ, giảm concurrency | Nộp các tác vụ trước, gom Future, rồi đợi/aggregate có deadline |
| Bỏ `Future` của `submit` không quan sát | Exception trong Future có thể bị lặng | Bảo đảm có owner theo dõi kết quả/lỗi |
| Gọi `future.get(1, SECONDS)` rồi tin task đã dừng khi timeout | Timeout chỉ giới hạn thời gian đợi | Chính sách cancel riêng và deadline ở tầng I/O |
| Dùng `cancel(true)` như rollback | Interrupt không hoàn tác tác dụng phụ đã xảy ra | Transaction/compensation, idempotency, checkpoint |
| Catch mỗi `ExecutionException` quanh lời gọi `submit` | Task rejection xảy ra khi nộp, không nằm trong Future | Catch `TaskRejectedException` quanh submit |
| Trộn `AsyncTaskExecutor` với `ExecutorService` | Không cùng API đầy đủ | Chọn interface đúng nhu cầu |
| Trông chờ `@Async`/transaction/request context tự truyền | Thread và proxy boundary độc lập | Context propagation tường minh; transaction riêng |
| Dùng pool/queue không giới hạn để “không bị reject” | Tăng latency/bộ nhớ và có thể OOM | Bounded queue, rejection policy, backpressure và metric |

## 12. Cách kiểm thử một đường submit()

1. **Trạng thái:** kiểm tra `submit(Runnable)` cho `null`, `submit(Callable<T>)` cho đúng `T` và `Future.get()` đợi hoàn tất.
2. **Exception:** nộp task ném một exception xác định; assert `get()` ném `ExecutionException` có `getCause()` đúng loại. Tránh test chỉ dựa vào log worker.
3. **Timeout/cancel:** dùng `CountDownLatch`/barrier kiểm soát task đang chờ rồi đo `get(timeout)`, `cancel(true)`, `isCancelled`; hạn chế phụ thuộc `Thread.sleep` cố định.
4. **Rejection:** cấu hình pool và queue nhỏ, chặn worker bằng latch, nộp vượt capacity; assert chính sách từ chối và đường xử lý của caller.
5. **Concurrency:** đo số luồng chạy đồng thời, xác minh giới hạn tải, kiểm thử shared mutable state trong bean.
6. **Lifecycle:** đóng ApplicationContext khi có task đang chạy/chờ; kiểm tra policy shutdown và việc không để mất dữ liệu nghiệp vụ quan trọng.
7. **Observability:** log taskId/correlationId hợp lệ; theo dõi queue depth, active count, task duration, failure/rejection và timeout.

## 13. Checklist sử dụng đúng

- Tôi biết biến `executor` có đúng kiểu `AsyncTaskExecutor` hay chỉ là `TaskExecutor`.
- Tôi chọn `Runnable` khi không cần giá trị; `Callable<T>` khi cần kết quả.
- Tôi phân biệt `submit` được nhận, `Future` đã hoàn tất và nghiệp vụ đã thành công.
- Tôi có người chịu trách nhiệm quan sát `Future` và xử lý task exception.
- Tôi phân biệt lỗi reject ở **submit** với lỗi xử lý ở **get**.
- Tôi xử lý interrupt, timeout, cancel mà không suy diễn rollback tự động.
- Tôi cấu hình giới hạn executor, queue, overload/rejection và shutdown theo tải thật.
- Tôi không trông chờ tự truyền transaction, request hoặc security context sang worker.
- Tôi dùng durable queue/outbox/idempotency nếu công việc phải phục hồi khi process chết.

**Kết luận:** `AsyncTaskExecutor` mở rộng khả năng thực thi task của Spring để nhận **`Future` quan sát kết quả bất đồng bộ**. `submit(Runnable)` theo dõi hoàn tất và có kết quả `null`; `submit(Callable<T>)` trả giá trị kiểu `T`. Điều đúng của hệ thống còn phụ thuộc cấu hình executor, chính sách lỗi, ranh giới trạng thái/giao dịch và thiết kế nghiệp vụ.

## Tài liệu tham khảo

- Spring Framework Javadoc – AsyncTaskExecutor: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/AsyncTaskExecutor.html
- Spring Framework Javadoc – TaskExecutor: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/TaskExecutor.html
- Spring Framework Javadoc – ThreadPoolTaskExecutor: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Spring Framework Reference – Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Java SE 17 Javadoc – Future: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html
- Java SE 17 Javadoc – ExecutorService: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-executor-004">Nhận CompletableFuture qua submitCompletable() khi phiên bản hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận CompletableFuture qua submitCompletable() khi phiên bản Spring hỗ trợ

## Mục tiêu và phạm vi

Trong Spring Framework, `TaskExecutor` cung cấp cách giao công việc cho một executor, còn `AsyncTaskExecutor` mở rộng nó bằng khả năng nộp tác vụ có kết quả. Kể từ **Spring Framework 6.0**, `AsyncTaskExecutor` có hai overload `submitCompletable(...)`, cho phép nhận ngay một `CompletableFuture` để chờ, ghép kết quả, xử lý lỗi và xây dựng chuỗi xử lý bất đồng bộ.

Sau bài này, bạn sẽ:

- Xác định **đúng phiên bản và interface** có `submitCompletable()`.
- Chọn overload `Runnable` hoặc `Callable<T>`, hiểu kết quả `Void`/`T` và cách exception đi vào future.
- Gọi `thenApply`, `thenCombine`, `exceptionally`/`handle` và `join()` đúng mục đích.
- Tạo ví dụ chạy được với `ThreadPoolTaskExecutor`, phân biệt lỗi thực thi và lỗi từ chối nhận task.
- Có phương án tương thích nếu đang dùng Spring Framework **trước 6.0**.
- Hiểu giới hạn của timeout, cancellation, thread pool và context propagation trong ứng dụng Spring.

**Phiên bản dùng để chạy ví dụ:** JDK **17+**, Maven, **Spring Framework 6.1.3**. Chức năng được giới thiệu từ **Spring Framework 6.0**; phiên bản 6.1.3 được chọn để có một tọa độ dependency cụ thể, có thể lặp lại. Nếu dự án dùng phiên bản khác, đọc Javadoc tương ứng trước khi áp dụng.

## 1. Phân biệt TaskExecutor, AsyncTaskExecutor và CompletableFuture

`org.springframework.core.task.TaskExecutor` kế thừa `java.util.concurrent.Executor` và có hợp đồng cốt lõi:

~~~java
void execute(Runnable task);
~~~

`execute` giao tác vụ để thực thi nhưng **không trả kết quả** cho người gọi. `AsyncTaskExecutor` là **interface con** của `TaskExecutor`. Từ Spring 6.0, nó khai báo hai phương thức mặc định:

~~~java
CompletableFuture<Void> submitCompletable(Runnable task);

<T> CompletableFuture<T> submitCompletable(Callable<T> task);
~~~

Các khai báo ở trên là **chữ ký API**, không phải file Java độc lập. Thư viện trả `CompletableFuture` đại diện cho việc tác vụ hoàn tất thành công, thất bại hoặc bị hủy. Việc gọi `submitCompletable` **không chặn để lấy kết quả công việc**; nhưng executor có thể tốn thời gian lúc nộp task do giới hạn/chiến lược từ chối, và việc chờ bằng `join()` hay `get()` sẽ chặn thread gọi đến khi có kết quả.

| Cách nộp | Interface / API | Giá trị trả về | Có chuỗi xử lý `thenApply` được ngay? |
| --- | --- | --- | --- |
| `execute(Runnable)` | `TaskExecutor` | `void` | Không |
| `submit(Runnable)` | `AsyncTaskExecutor` | `Future<?>`, kết quả thành công là `null` | Không trực tiếp |
| `submit(Callable<T>)` | `AsyncTaskExecutor` | `Future<T>` | Không trực tiếp |
| `submitCompletable(Runnable)` | `AsyncTaskExecutor`, **6.0+** | `CompletableFuture<Void>`, kết quả thành công `null` | Có |
| `submitCompletable(Callable<T>)` | `AsyncTaskExecutor`, **6.0+** | `CompletableFuture<T>` | Có |
| `CompletableFuture.supplyAsync(supplier, executor)` | Java 8+ và `Executor` bất kỳ | `CompletableFuture<T>` | Có |

**Đừng nhầm tên interface với khả năng biên dịch:** biến chỉ khai báo `TaskExecutor executor` không có phương thức `submitCompletable` ở **kiểu tĩnh**, ngay cả khi đối tượng thực là `ThreadPoolTaskExecutor`. Hãy khai báo/tiêm `AsyncTaskExecutor` hoặc kiểu triển khai phù hợp.

~~~java
// Minh họa cách chọn kiểu biến (đặt trong class có các import tương ứng).
TaskExecutor basic = pool;
basic.execute(() -> doWork()); // hợp lệ
// basic.submitCompletable(() -> "x"); // không biên dịch: TaskExecutor không có API này

AsyncTaskExecutor async = pool;
CompletableFuture<String> result = async.submitCompletable(() -> "x");
~~~

Đoạn trên chỉ minh họa **kiểu biên dịch**, giả sử `pool` là `ThreadPoolTaskExecutor` và `doWork()` tồn tại ở lớp chứa mã. Không phải chương trình độc lập.

### Kiểm tra phiên bản trước khi gọi

| Phiên bản thư viện Spring Framework | Hành vi cần ghi nhớ |
| --- | --- |
| **5.x** | `AsyncTaskExecutor` có `submit(...)` trả `Future`; **chưa có** `submitCompletable(...)`. |
| **6.0 trở lên** | Có `submitCompletable(Runnable)` và `submitCompletable(Callable<T>)`, cả hai được ghi `Since: 6.0` trong Javadoc. |
| **6.1/6.2** | Hai overload vẫn có; một số phương thức `submit(...)` cũ có default implementation từ 6.1, nhưng điều này **không đổi** mốc của `submitCompletable`. |

Spring Framework 6.x yêu cầu Java 17 hoặc mới hơn. Với dự án Spring Boot, hãy nhìn **phiên bản Spring Framework thực sự được resolve trong dependency tree**, không đoán chỉ dựa trên tên `spring-boot-starter` hoặc một bài viết về Boot.

## 2. Hai overload: Runnable và Callable<T>

### 2.1. `Runnable`: quan tâm trạng thái hoàn tất, không có payload

~~~java
CompletableFuture<Void> saved = async.submitCompletable((Runnable) () -> {
    saveToDatabase();
});

saved.thenRun(() -> notifyUser());
~~~

Đoạn trên là **mã minh họa**: `async`, `saveToDatabase()` và `notifyUser()` thuộc ứng dụng cụ thể. `Runnable.run()` không trả giá trị; khi thành công, `saved.join()` là **`null`**, nên `CompletableFuture<Void>` phù hợp với thao tác ghi dữ liệu, gửi sự kiện hoặc lưu audit log. Ép kiểu `(Runnable)` có thể giúp làm rõ overload với lambda phức tạp; với lambda body chỉ gọi một phương thức `void`, Java thường suy luận được tự nhiên.

### 2.2. `Callable<T>`: task có trả kết quả

~~~java
CompletableFuture<Integer> count = async.submitCompletable(() -> {
    return 21 * 2;
});

CompletableFuture<String> message =
        count.thenApply(value -> "count=" + value);
~~~

Đây cũng là **fragment** trong chương trình có biến `async`. Vì lambda trả `int`, Java suy luận overload `Callable<Integer>` và `T = Integer`; `count.join()` trả `42`, `message.join()` trả `"count=42"`. Khác `Supplier<T>`, `Callable<T>.call()` được phép khai báo **checked exception**; nếu task ném exception, future hoàn tất **exceptionally**.

### 2.3. Đường đi của kết quả

~~~text
Caller
  |
  | async.submitCompletable(callable)
  v
AsyncTaskExecutor -----------------> executor / queue / worker
  |                                      |
  | trả CompletableFuture<T> ngay        | gọi Callable.call()
  v                                      |
Caller làm việc khác                      +-- thành công --> complete(value)
  |                                      |
  +-- thenApply / thenCombine            +-- thất bại ----> completeExceptionally(error)
  |                                      |
  +-- join() / get() <------------------- trạng thái future hoàn tất
~~~

`submitCompletable` chỉ tạo **kênh nhận kết quả**. Nó không hứa rằng task bắt đầu ngay, không tăng số worker vô hạn và không tự cấu hình thread pool. Tốc độ, thread name, hàng đợi và quy tắc từ chối phụ thuộc **executor triển khai**.

## 3. Ví dụ hoàn chỉnh chạy với Maven và Spring Framework 6.1.3

Ví dụ này chạy độc lập với một chương trình `main`, **không cần Spring Boot server**, `@EnableAsync` hay `@Async`. Nó dùng `ThreadPoolTaskExecutor` (thực thi `AsyncTaskExecutor`), cấu hình pool cố định hai worker và hàng đợi hữu hạn.

Cấu trúc dự án:

~~~text
submit-completable-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── SubmitCompletableDemo.java
~~~

### 3.1. Tệp `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>submit-completable-demo</artifactId>
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
                    <mainClass>demo.SubmitCompletableDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Dependency `spring-context` mang theo các thư viện Spring cần cho `ThreadPoolTaskExecutor`. Maven tải các dependency từ repository đã cấu hình khi máy có quyền truy cập. Trong ứng dụng Spring hiện có, dùng hệ thống quản lý phiên bản của dự án thay vì tự thêm `spring-context` với một phiên bản khác.

### 3.2. Tệp `src/main/java/demo/SubmitCompletableDemo.java`

~~~java
package demo;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public final class SubmitCompletableDemo {

    public static void main(String[] args) {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(8);
        pool.setThreadNamePrefix("lesson-");
        pool.initialize();

        try {
            AsyncTaskExecutor executor = pool;

            // Runnable: không có giá trị trả về.
            AtomicInteger counter = new AtomicInteger();
            CompletableFuture<Void> incremented =
                    executor.submitCompletable((Runnable) () -> {
                        counter.incrementAndGet();
                    });

            // Callable: mỗi task trả về một String.
            CompletableFuture<String> first =
                    executor.submitCompletable(() -> "Spring");
            CompletableFuture<String> second =
                    executor.submitCompletable(() -> "CompletableFuture");

            // Ghép hai kết quả độc lập khi cả hai hoàn thành.
            CompletableFuture<String> combined = first.thenCombine(
                    second, (a, b) -> a + " + " + b
            );

            System.out.println("combined=" + combined.join());
            System.out.println("runnable-result=" + incremented.join());
            System.out.println("counter=" + counter.get());

            // Chỉ lỗi trong task; submit vẫn được chấp nhận.
            CompletableFuture<Integer> failed =
                    executor.submitCompletable((Callable<Integer>) () -> {
                        throw new IllegalStateException("demo-failure");
                    });

            int recovered = failed.exceptionally(error -> -1).join();
            System.out.println("recovered=" + recovered);
        } finally {
            // Khi tự tạo pool ngoài Spring container, tự đóng nó.
            pool.shutdown();
        }
    }
}
~~~

Trong thư mục `submit-completable-demo` chạy:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do **chương trình Java** in ra (không tính log của công cụ xây dựng, nếu có):

~~~text
combined=Spring + CompletableFuture
runnable-result=null
counter=1
recovered=-1
~~~

**Vì sao kết quả ổn định dù task thực thi song song?** `thenCombine` chỉ hoàn tất khi cả hai future đã có giá trị; `combined.join()` đợi kết quả ghép. `incremented.join()` đợi thao tác tăng hoàn tất trước khi đọc `counter`. Future lỗi được `exceptionally` chuyển thành `-1` trước khi in. Thứ tự chạy thực tế của các worker **không được cố định**, chỉ thứ tự các câu `println` trong `main` là cố định.

**Vì sao phải `initialize()` và `shutdown()`?** Đây là executor tạo thủ công. `initialize()` thiết lập executor nội bộ; `shutdown()` kết thúc vòng đời của pool. Khi cùng loại executor được đăng ký làm Spring bean, Spring container quản lý vòng đời cho bean đó.

**Vì sao dùng `AtomicInteger`?** Tác vụ chạy ở thread khác; đối tượng AtomicInteger thể hiện một cách cập nhật an toàn. Ở ví dụ chỉ có một lần tăng, nhưng thói quen dùng cấu trúc thread-safe cho trạng thái chia sẻ vẫn cần được hình thành.

## 4. Kết hợp CompletableFuture sau khi nhận từ submitCompletable

Ưu điểm của `CompletableFuture` so với `Future` thuần là khả năng **xây dựng tiếp các giai đoạn xử lý** thay vì chỉ gọi `get()` để chờ kết quả.

| Phương thức | Vai trò | Ghi chú |
| --- | --- | --- |
| `thenApply(fn)` | Biến đổi kết quả `T → U` sau khi thành công | Callback không phải `Async`, có thể chạy trên thread hoàn tất future hoặc thread đã gọi completion |
| `thenCompose(fn)` | Nối một bước trả về `CompletionStage<U>`; tránh `Future<Future<U>>` | Hữu ích cho các tác vụ phụ thuộc nhau |
| `thenCombine(other, fn)` | Ghép hai task độc lập khi cả hai hoàn thành | Có thể chạy song song nếu executor còn worker |
| `allOf(f1, f2, ...)` | Chờ tất cả future hoàn tất | Future trả về có kiểu `CompletableFuture<Void>`; cần đọc từng kết quả riêng |
| `exceptionally(fn)` | Cung cấp giá trị dự phòng khi chuỗi lỗi | Không chạy khi future thành công |
| `handle((value, error) -> ...)` | Xử lý cả thành công và thất bại | Có thể biến đổi sang một kết quả khác |
| `whenComplete((value, error) -> ...)` | Quan sát/log hoàn tất | Không phải cách chính để phục hồi lỗi; callback tự ném lỗi có thể làm giai đoạn mới lỗi |
| `join()` / `get()` | Đợi và lấy kết quả cuối | Dùng ở ranh giới cần giá trị, hạn chế chặn bên trong worker |

Một đoạn mã minh họa **khi đã có biến `async` kiểu `AsyncTaskExecutor`**:

~~~java
CompletableFuture<Integer> price =
        async.submitCompletable(() -> 100);

CompletableFuture<Integer> tax =
        async.submitCompletable(() -> 10);

CompletableFuture<String> display = price
        .thenCombine(tax, Integer::sum)
        .thenApply(total -> "total=" + total);

System.out.println(display.join()); // total=110
~~~

Không cần gọi `join()` ngay sau khi nộp từng task; nếu làm thế, người gọi sẽ tuần tự chờ và làm mất lợi ích ghép công việc độc lập.

### `thenApply` khác `thenApplyAsync`

`thenApply` không cam kết chọn một executor riêng. `thenApplyAsync(fn)` **không truyền executor** mặc định dùng `ForkJoinPool.commonPool()` theo hợp đồng của `CompletableFuture` trong Java 17 (ngoại lệ runtime theo tài liệu JDK). Nếu muốn stage tiếp tục chạy trên cùng Spring executor, truyền rõ:

~~~java
CompletableFuture<String> next = async
        .submitCompletable(() -> "Spring")
        .thenApplyAsync(value -> value.toUpperCase(), async);
~~~

Đây là fragment với `async` đã tồn tại. Cách này hữu ích khi cần kiểm soát số worker hoặc gắn `TaskDecorator` vào task sau đó; song hãy tránh ép mọi thao tác biến đổi nhỏ sang một thread khác vì mỗi lần scheduling cũng có chi phí.

## 5. Ba nhóm lỗi cần phân biệt

### 5.1. Lỗi trong nội dung Callable/Runnable

Nếu task ném `IOException`, `IllegalStateException` hay exception khác, future **hoàn tất thất bại**, không mang giá trị bình thường. Ví dụ:

~~~java
CompletableFuture<Integer> failed =
        async.submitCompletable((Callable<Integer>) () -> {
            throw new IllegalArgumentException("invalid input");
        });

int value = failed.exceptionally(error -> 0).join(); // 0
~~~

Ở đây ép `Callable<Integer>` làm rõ overload cho lambda chỉ có `throw`. Với `join()` trên future bị lỗi **chưa được phục hồi**, Java thường ném `CompletionException` (unchecked), với `get()` thường là `ExecutionException` (checked); cần xem `getCause()` để xác định lỗi nghiệp vụ ban đầu. `get()` còn có thể ném `InterruptedException`, nên xử lý đúng tín hiệu interrupt.

### 5.2. Executor từ chối nhận task ngay lúc submit

`AsyncTaskExecutor.submitCompletable` được phép ném `TaskRejectedException` **ngay trong lời gọi nộp task** khi executor từ chối (ví dụ pool đã shutdown hoặc quá tải theo cấu hình). Đây là khác biệt lớn với **lỗi xảy ra sau khi task đã được chấp nhận**.

~~~java
try {
    CompletableFuture<String> future =
            async.submitCompletable(() -> "ok");
    // Chỉ có future khi bước submit thành công.
} catch (org.springframework.core.task.TaskRejectedException ex) {
    // Ứng dụng quyết định phản hồi quá tải hoặc retry có kiểm soát.
}
~~~

Mã này là fragment trong ứng dụng có `async`. Không giả định mọi lỗi đều đi vào `exceptionally(...)`: nếu quá trình submit **ném exception trước khi trả future**, chưa có future để nối stage. Nếu tạo fallback với `CompletableFuture.failedFuture(ex)`, hãy thực hiện rõ ràng tại tầng API khi chính sách nghiệp vụ cho phép.

### 5.3. Lỗi ở callback phía sau

Ngay cả khi `submitCompletable` thành công, callback `thenApply(...)` hoặc `thenCombine(...)` vẫn có thể ném lỗi. Khi đó **future giai đoạn sau** hoàn tất thất bại. Hãy gắn `exceptionally` hoặc `handle` ở giai đoạn phù hợp; nếu ghi log qua `whenComplete` thì log thêm nguyên nhân nhưng không coi nó tự động phục hồi lỗi.

## 6. Fallback khi phiên bản chưa có submitCompletable()

Trong **Spring Framework 5.x**, `AsyncTaskExecutor.submitCompletable` **không tồn tại**. Nếu thử gọi phương thức, lỗi biên dịch dạng `cannot find symbol: method submitCompletable(...)` là hợp lý. Không sửa lỗi bằng ép kiểu vì việc ép kiểu **không thêm phương thức** vào class của thư viện cũ.

Vì `TaskExecutor` kế thừa `java.util.concurrent.Executor`, có thể dùng phương thức JDK vốn có từ Java 8:

~~~java
// Fragment dùng được với TaskExecutor trong Spring 5.x.
// taskExecutor là một instance TaskExecutor đã được cấu hình.
CompletableFuture<String> withValue =
        CompletableFuture.supplyAsync(() -> "result", taskExecutor);

CompletableFuture<Void> withSideEffect =
        CompletableFuture.runAsync(() -> System.out.println("saved"),
                                   taskExecutor);
~~~

Đoạn này không phải class độc lập; cần import `java.util.concurrent.CompletableFuture` và có biến `taskExecutor`. Tham số thứ hai **rất quan trọng**: nếu gọi `supplyAsync(supplier)` hoặc `runAsync(runnable)` **không truyền executor**, JDK chọn executor mặc định của `CompletableFuture`, thường là common pool, chứ không phải Spring `TaskExecutor` bạn đã cấu hình.

**Khác biệt khi chuyển đổi:** `supplyAsync` nhận `Supplier<T>`, không phải `Callable<T>`; `Supplier.get()` không khai báo checked exception. Nếu tác vụ cần ném checked exception, bạn phải bắt/chuyển đổi ở lambda hoặc thiết kế API cho phù hợp. `runAsync` dùng `Runnable`; cả hai vẫn tạo `CompletableFuture`, nhưng đây là **phương án tương thích**, không phải `submitCompletable` tồn tại trên Spring 5.x.

Ngoài ra, nếu implementation thật là `SyncTaskExecutor`, việc gọi `CompletableFuture.supplyAsync(..., taskExecutor)` có thể **chạy đồng bộ ngay trong caller** vì executor được truyền không có tính bất đồng bộ. Không suy luận concurrency chỉ từ kiểu `CompletableFuture` của kết quả.

## 7. Đưa vào ứng dụng Spring: bean và dependency injection

Trong ứng dụng Spring thực, thay vì `new` executor ở mỗi request, cấu hình nó thành **singleton bean**, sau đó inject bằng interface `AsyncTaskExecutor`.

Tệp `ReportExecutorConfig.java` trong một ứng dụng đã bật Spring component scanning (đoạn minh họa không phải project chạy độc lập):

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ReportExecutorConfig {

    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("report-");
        return executor; // Spring quản lý initialize/destroy
    }
}
~~~

Tệp `ReportService.java` cùng module:

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    private final AsyncTaskExecutor executor;

    public ReportService(
            @Qualifier("reportExecutor") AsyncTaskExecutor executor) {
        this.executor = executor;
    }

    public CompletableFuture<String> buildReport(String reportId) {
        return executor.submitCompletable(
                () -> "report:" + reportId
        );
    }
}
~~~

Hai class trên minh họa cách gắn vào **ứng dụng Spring có sẵn**, không phải chương trình `main` độc lập. Spring container gọi các hook khởi tạo và dọn dẹp bean `ThreadPoolTaskExecutor`. Khi cần nhiều executor, dùng `@Qualifier` hoặc cấu hình chọn bean để bảo đảm dùng đúng pool.

**Khác với `@Async`:** `submitCompletable` gọi API trực tiếp nên **không cần `@EnableAsync`** và không phụ thuộc Spring AOP proxy. `@Async` là cơ chế khác: Spring chặn lời gọi method qua proxy để chuyển sang executor đã cấu hình. Một method `@Async` có thể trả `CompletableFuture`, nhưng không vì thế mà mọi `CompletableFuture` do ứng dụng tạo đều đi qua `@Async`.

**Ngữ cảnh Spring:** transaction, security context, MDC và request context thường gắn với thread hoặc phạm vi riêng. Không mặc định coi mọi context được copy qua task mới. Nếu cần, dùng cơ chế propagation được framework hỗ trợ, `TaskDecorator` hoặc truyền dữ liệu tường minh; dọn dẹp context trong `finally`. Tránh làm DB transaction của thread caller bị hiểu nhầm thành transaction tự động trải sang background thread.

## 8. Những bẫy thường gặp ở production

### 8.1. `join()` chặn và có thể tạo nghẽn pool

Ví dụ có **một worker duy nhất**: task A chạy trong pool, nộp task B vào **cùng pool** rồi gọi `B.join()`; B không có worker để chạy trong khi A giữ worker đợi B. Có thể dẫn đến **thread-starvation deadlock**. Dùng các stage bất đồng bộ như `thenCompose`/`thenCombine` thay cho việc đợi future con bên trong worker, hoặc điều chỉnh kiến trúc/executor.

### 8.2. Timeout của future không tự dừng I/O đang chạy

`future.orTimeout(2, TimeUnit.SECONDS)` làm **future hoàn tất exceptionally** với timeout nếu chưa có kết quả trong 2 giây, nhưng **không bảo đảm** dừng HTTP request, câu lệnh SQL hay Runnable đang tiếp tục. Tương tự, `completeOnTimeout(fallback, ...)` chỉ thay đổi trạng thái future khi quá hạn; business side effects có thể vẫn diễn ra.

`CompletableFuture.cancel(true)` cũng không bảo đảm interrupt thread đang thực hiện tác vụ: hợp đồng `CompletableFuture` xem cancellation như exceptional completion, và cờ `mayInterruptIfRunning` **không có tác dụng ngắt tương đương `FutureTask`**. Cần timeout cho chính HTTP client, JDBC/DB query, và cơ chế hủy tác vụ hợp tác ở tầng tác vụ.

### 8.3. Backpressure và rejection

`CompletableFuture` không làm tăng năng lực pool. Cấu hình `corePoolSize`, `maxPoolSize`, `queueCapacity` và `RejectedExecutionHandler` theo workload, rồi kiểm tra thông lượng, queue length, active count và tail latency. Khi queue đầy, task có thể bị từ chối đồng bộ; gắn `exceptionally` **chỉ ở future** không xử lý được lỗi submit đó.

Với `ThreadPoolTaskExecutor`, pool thường **ưu tiên xếp hàng** khi đã có core workers; chỉ tăng worker đến max khi hàng đợi không nhận thêm task. Vì vậy maxPoolSize lớn nhưng queueCapacity quá lớn có thể không tạo nhiều worker như bạn tưởng.

### 8.4. Callback chạy trên thread nào?

`thenApply` không bảo đảm ở worker Spring đã cấu hình; `thenApplyAsync` không chỉ định executor thường chạy trên `ForkJoinPool.commonPool()`. Khi lựa chọn thread, MDC hoặc giới hạn concurrency quan trọng, truyền executor tường minh cho các phương thức `...Async`.

Việc thread nào thực thi callback cũng phụ thuộc thời điểm future hoàn tất, nên không viết kiểm thử phụ thuộc vào tên thread cho các callback không `Async`.

### 8.5. Lỗi bị che khuất

Nếu nộp task rồi bỏ future, exception có thể **không được quan sát** tại caller. Với `submitCompletable`, lỗi task được phản ánh ở trạng thái future; hãy nối stage xử lý, trả future lên tầng gọi hoặc log/quan sát với trách nhiệm rõ ràng. Không chỉ dựa vào `Thread.UncaughtExceptionHandler` hay log của executor để biết mọi exception từ task do `Future`/`CompletableFuture` quản lý.

## 9. Chọn API cho đúng bài toán

| Nhu cầu thực tế | Gợi ý |
| --- | --- |
| Chỉ cần ra lệnh chạy `Runnable`, không cần theo dõi hoàn tất | `TaskExecutor.execute` |
| Cần chờ và lấy kết quả nhưng không nối callback | `AsyncTaskExecutor.submit` trả `Future` |
| Cần kết quả, ghép nhiều tác vụ, phục hồi lỗi ở chuỗi xử lý; Spring 6.0+ | **`AsyncTaskExecutor.submitCompletable`** |
| Mới có bean kiểu `TaskExecutor` hoặc dùng Spring 5.x | `CompletableFuture.supplyAsync/runAsync(..., taskExecutor)` |
| Muốn method Spring tự chạy qua proxy, có cấu hình `@EnableAsync` | Cân nhắc `@Async` và kiểu trả `CompletableFuture` |
| Tác vụ chủ yếu CPU-bound | Pool giới hạn theo tài nguyên CPU; không chỉ tăng task/future |
| Tác vụ I/O-bound có pool/connection downstream hạn chế | Giới hạn concurrency ở downstream, timeout, backpressure và đo tải |

### Checklist áp dụng

1. Xác minh Spring Framework resolve trong build là **6.0+** và kiểm tra kiểu biến là **`AsyncTaskExecutor`**.
2. Chọn `Callable<T>` khi cần giá trị, `Runnable` khi chỉ cần trạng thái hoàn tất.
3. Đảm bảo executor được khởi tạo/được container quản lý; cấu hình queue, worker và rejection phù hợp.
4. Nối `thenApply`/`thenCombine`/`exceptionally` theo luồng dữ liệu; chỉ `join` ở chỗ thật sự cần đồng bộ.
5. Bắt riêng `TaskRejectedException` ở **thời điểm submit**, và xử lý exception trong task qua future.
6. Có timeout thật ở I/O, hủy tác vụ hợp tác, context propagation và giám sát pool.

## 10. Tự kiểm tra

**Câu 1.** `TaskExecutor executor = new ThreadPoolTaskExecutor();` có thể gọi trực tiếp `executor.submitCompletable(...)` không?  
**Đáp án:** Không, vì kiểu tĩnh `TaskExecutor` chỉ khai báo `execute`. Dùng `AsyncTaskExecutor` hoặc kiểu triển khai phù hợp.

**Câu 2.** Tại sao `CompletableFuture<Void>.join()` trả `null` sau khi task `Runnable` hoàn tất thành công?  
**Đáp án:** `Runnable` không có giá trị trả về. Future dùng để theo dõi việc hoàn tất.

**Câu 3.** Một task đã được chấp nhận nhưng ném exception khác gì pool từ chối lúc nộp?  
**Đáp án:** Exception trong task hoàn tất future thất bại; từ chối nộp có thể ném `TaskRejectedException` trước khi nhận được future.

**Câu 4.** Vì sao `thenApplyAsync(fn)` có thể bỏ qua pool Spring?  
**Đáp án:** Không truyền đối số executor, JDK thường dùng default async execution facility, cụ thể là common pool theo hợp đồng `CompletableFuture` chuẩn.

**Câu 5.** Nếu chạy Spring 5.3, giải pháp tương thích nào vẫn cho phép nhận `CompletableFuture`?  
**Đáp án:** `CompletableFuture.supplyAsync(supplier, taskExecutor)` hoặc `runAsync(runnable, taskExecutor)` với bean `TaskExecutor` đã cấu hình; không gọi API chỉ xuất hiện từ Spring 6.0.

**Câu 6.** `cancel(true)` trên `CompletableFuture` có bảo đảm hủy việc ghi DB đang chạy?  
**Đáp án:** Không. Cancellation của `CompletableFuture` không bảo đảm interrupt tác vụ; cần timeout/cancellation thật tại tầng I/O và nghiệp vụ.

## Tham khảo chính thức

- [Spring Framework 6.1.3 — AsyncTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/core/task/AsyncTaskExecutor.html): chữ ký hai overload, `Since: 6.0`, return types và `TaskRejectedException`.
- [Spring Framework 6.1.3 — TaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/core/task/TaskExecutor.html): interface cơ bản chỉ có `execute(Runnable)`.
- [Spring Framework 6.1.3 — ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): executor, pool/queue, lifecycle và rejection.
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): TaskExecutor, các implementation và sự khác biệt với `@Async`.
- [Java SE 17 — CompletableFuture Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): completion stage, thread thực thi callback, `join`/`get`, timeout và cancellation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-executor-005">Tiêm bộ thực thi qua interface để tách khỏi cách triển khai</a>

<details>
<summary>Xem chi tiết</summary>

# Tiêm bộ thực thi qua interface để tách khỏi cách triển khai trong Spring

## 1. Vấn đề: nghiệp vụ cần gửi tác vụ, không cần quản lý thread pool

Giả sử ứng dụng cần ghi nhật ký nghiệp vụ, gửi thông báo hoặc tạo báo cáo sau khi nhận một yêu cầu. Thành phần nghiệp vụ cần phát lệnh **“hãy thực hiện công việc này”**. Nó không nhất thiết phải biết công việc chạy trên một thread mới, trên pool, trong thread hiện tại hay qua một executor do hạ tầng quản lý.

Nếu service tự tạo `new Thread(...)` hoặc `new ThreadPoolTaskExecutor()`, service bắt đầu chịu thêm trách nhiệm chọn số thread, cấu hình hàng đợi, khởi tạo, dừng executor và xử lý tài nguyên. Khi muốn đổi cách chạy trong môi trường production, integration test hay unit test, nhiều chỗ trong mã nghiệp vụ phải thay đổi.

**Dependency Injection (DI) qua interface** giúp thiết lập ranh giới rõ ràng:

- **Business service:** chỉ phụ thuộc vào hợp đồng `TaskExecutor` và gửi một `Runnable`.
- **Cấu hình Spring:** quyết định bean nào hiện thực hợp đồng: `ThreadPoolTaskExecutor`, `SyncTaskExecutor` hoặc một implementation thích hợp khác.
- **Spring container:** tạo, tiêm và quản lý vòng đời các bean liên quan.

Hướng phụ thuộc nên là:

```text
BusinessService  --depends on-->  TaskExecutor (interface)
                                    ^
                                    |
                       Spring injects a concrete bean
                         /                    \
            ThreadPoolTaskExecutor       SyncTaskExecutor
                 (pool)                     (same thread)
```

Điểm mấu chốt là **tách “công việc cần làm” khỏi “cách thực thi công việc”**. Thay đổi implementation ở lớp cấu hình không buộc sửa logic xử lý trong service.

## 2. Hợp đồng `TaskExecutor` thực sự bảo đảm gì?

`org.springframework.core.task.TaskExecutor` là interface của Spring, mở rộng `java.util.concurrent.Executor` của Java. Phương thức cốt lõi:

```java
void execute(Runnable task);
```

Khi gọi `execute(task)`, caller yêu cầu executor thực thi một `Runnable` theo chính sách của implementation. Hợp đồng **không hứa** rằng tác vụ luôn chạy trên thread khác, được chạy ngay lập tức, chạy theo thứ tự gửi, hoặc chắc chắn được chấp nhận khi hệ thống đã quá tải.

| Implementation | Cách thực thi nổi bật | Phù hợp khi |
| --- | --- | --- |
| `ThreadPoolTaskExecutor` | Dùng thread pool và hàng đợi có thể cấu hình; Spring quản lý vòng đời | Ứng dụng cần pool để xử lý nhiều tác vụ |
| `SyncTaskExecutor` | Gọi `Runnable.run()` ngay trên thread hiện tại | Test đơn giản hoặc trường hợp chủ ý chạy đồng bộ |
| `SimpleAsyncTaskExecutor` | Thường tạo thread mới cho mỗi tác vụ, không tái sử dụng pool; có thể cấu hình virtual threads trên nền tảng hỗ trợ | Nhu cầu thực thi khác với mô hình pool truyền thống |
| `ConcurrentTaskExecutor` | Adapter bọc một Java `Executor` có sẵn | Muốn dùng executor do nơi khác cung cấp |

Một implementation có thể chạy *synchronous*, một implementation khác chạy *asynchronous*. Vì thế, tên interface `TaskExecutor` nói về **khả năng gửi tác vụ**, không nói rằng mọi implementation đều luôn bất đồng bộ.

Đừng nhầm các mức trừu tượng:

- `TaskExecutor`: cần chức năng `execute(Runnable)`; không nhận trực tiếp kết quả trả về.
- `AsyncTaskExecutor`: interface mở rộng với khả năng gửi tác vụ nhận `Future` (tùy phương thức và phiên bản API); chỉ phụ thuộc vào nó nếu service thực sự cần tính năng này.
- `TaskScheduler`: dùng để lên lịch chạy tại thời điểm cụ thể hoặc theo chu kỳ; đây là mục đích khác với gửi một task để chạy.
- `Executor` của JDK: cũng có `execute(Runnable)`; có thể dùng làm kiểu tham số nếu không cần hợp đồng riêng của Spring.

**Nguyên tắc lựa chọn:** dùng interface hẹp nhất đáp ứng nghiệp vụ. Service chỉ “gửi việc” nên thường chỉ cần `TaskExecutor`.

## 3. Ví dụ chạy được: một service, hai cách thực thi

Ví dụ dưới đây dùng **Java 21, Spring Framework 6.2.1 và Maven**. Service phụ thuộc `TaskExecutor`; phần cấu hình chọn bean có tên `businessExecutor`. Mặc định ứng dụng dùng pool; khi bật profile `sync`, ứng dụng dùng executor chạy đồng bộ. Không cần sửa `MessageService` giữa hai chế độ.

### 3.1. Tạo cấu trúc dự án

```text
executor-di-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── example/
                └── ExecutorDiDemo.java
```

**Tệp `pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>executor-di-demo</artifactId>
    <version>1.0-SNAPSHOT</version>

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
                    <mainClass>example.ExecutorDiDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tệp Maven định nghĩa dependency cần thiết cho Spring Context, dùng mã nguồn tương thích Java 21. Không cần Spring Boot để chứng minh constructor injection; Boot cũng sử dụng cùng nguyên tắc DI này.

### 3.2. Mã Java hoàn chỉnh

**Tệp `src/main/java/example/ExecutorDiDemo.java`:**

```java
package example;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class ExecutorDiDemo {
    public static final class MessageService {
        private final TaskExecutor executor;

        public MessageService(TaskExecutor executor) {
            this.executor = executor;
        }

        public CountDownLatch submit(String message) {
            CountDownLatch done = new CountDownLatch(1);
            executor.execute(() -> {
                try {
                    System.out.println("Processed: " + message);
                } finally {
                    done.countDown();
                }
            });
            return done;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean("businessExecutor")
        @Profile("!sync")
        public ThreadPoolTaskExecutor pooledExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(4);
            executor.setQueueCapacity(32);
            executor.setThreadNamePrefix("business-");
            return executor;
        }

        @Bean("businessExecutor")
        @Profile("sync")
        public TaskExecutor syncExecutor() {
            return new SyncTaskExecutor();
        }

        @Bean
        public MessageService messageService(
                @Qualifier("businessExecutor") TaskExecutor executor) {
            return new MessageService(executor);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        String mode = args.length > 0 && "sync".equals(args[0])
                ? "sync" : "pool";
        try (var context = new AnnotationConfigApplicationContext()) {
            if ("sync".equals(mode)) {
                context.getEnvironment().setActiveProfiles("sync");
            }
            context.register(AppConfig.class);
            context.refresh();

            System.out.println("Mode: " + mode);
            CountDownLatch done =
                    context.getBean(MessageService.class).submit("M001");
            if (!done.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Task timed out");
            }
            System.out.println("Completed: true");
        }
    }
}
```

### 3.3. Chạy và kiểm chứng

Chạy tại thư mục chứa `pom.xml` với JDK 21 trở lên và Maven:

```bash
mvn -q -DskipTests compile exec:java
```

Output của ứng dụng:

```text
Mode: pool
Processed: M001
Completed: true
```

Chuyển sang executor đồng bộ bằng profile `sync`:

```bash
mvn -q -Dexec.args=sync exec:java
```

Output của ứng dụng:

```text
Mode: sync
Processed: M001
Completed: true
```

Các output trên đã được chạy kiểm chứng với Maven và biên dịch ở mức ngôn ngữ Java 21. Maven/JVM có thể in thêm thông báo môi trường khi chạy không ở chế độ `-q`.

**Vì sao thứ tự output ổn định trong ví dụ?** Luồng chính đợi `CountDownLatch.await(...)`; task in dòng `Processed` trước khi gọi `countDown()`. Dù bản pool có thể xử lý task trên thread khác, chương trình chỉ in `Completed: true` sau khi latch đã được giải phóng.

`CountDownLatch` ở đây chỉ minh họa cách đợi task kết thúc. Trong ứng dụng thực tế, một latch được giảm trong `finally` không khẳng định công việc thành công; để nhận giá trị hoặc lỗi, hãy dùng cơ chế trả kết quả và xử lý exception phù hợp.

## 4. Phân tích vì sao ví dụ tách được implementation

### 4.1. Service chỉ biết interface

```java
private final TaskExecutor executor;

public MessageService(TaskExecutor executor) {
    this.executor = executor;
}
```

Không có lời gọi `new ThreadPoolTaskExecutor()`, không biết kích thước pool và không kiểm tra `instanceof` để quyết định cách chạy. Điều service biết là **đã có một đối tượng nhận `Runnable` để thực thi**.

Trong Spring, constructor injection làm dependency hiển thị rõ khi tạo service, dễ kiểm thử và cho phép trường `final`. Khi service chỉ có một constructor, Spring có thể tự tiêm mà không cần `@Autowired` trên constructor.

### 4.2. Cấu hình sở hữu implementation

Hai method cấu hình đều khai báo bean tên `businessExecutor`, nhưng điều kiện `@Profile` bảo đảm chỉ cấu hình phù hợp được kích hoạt:

- Không bật profile `sync` → `pooledExecutor()` tạo `ThreadPoolTaskExecutor`.
- Bật profile `sync` → `syncExecutor()` tạo `SyncTaskExecutor`.

Bean `messageService(...)` chỉ yêu cầu tham số kiểu `TaskExecutor`. Spring tra cứu bean tương thích rồi tiêm. Chính phần cấu hình biết tên implementation và các thông số kỹ thuật.

Ở bản pool, đối tượng cụ thể là `ThreadPoolTaskExecutor` để có thể đặt `corePoolSize`, `maxPoolSize`, `queueCapacity` và tên thread. Khi Spring quản lý bean này, Spring kích hoạt callback khởi tạo và đóng executor theo vòng đời context. Đừng tự khởi tạo rồi bỏ mặc một thread pool ở bên trong business service.

### 4.3. Vì sao cần `@Qualifier`?

Trong ứng dụng lớn, có thể cùng tồn tại executor cho gửi email, ghi báo cáo, xử lý sự kiện và các tác vụ khác. Nếu chỉ inject kiểu `TaskExecutor` khi có nhiều bean cùng kiểu, Spring có thể không xác định được bean nào cần chọn, hoặc một bean `@Primary` có thể vô tình được dùng không đúng mục đích.

```java
@Bean
public MessageService messageService(
        @Qualifier("businessExecutor") TaskExecutor executor) {
    return new MessageService(executor);
}
```

`@Qualifier("businessExecutor")` xác định **vai trò** của executor, không ràng buộc service vào một class implementation. Tên bean mang nghĩa nghiệp vụ ổn định qua các cấu hình; tên implementation có thể thay đổi.

Nếu chỉ có một bean thích hợp, qualifier có thể không cần. Khi có nhiều bean, ưu tiên qualifier hoặc cách đặt tên rõ ràng để tránh đổi executor ngoài ý muốn.

## 5. Kiểm thử dễ hơn khi tiêm interface

Một lợi ích lớn của DI là có thể kiểm thử phần logic **mà không phải tạo pool, không chờ thread hay dùng `Thread.sleep`**.

Ví dụ dùng executor giả thu lại các `Runnable`. Đoạn dưới là một fragment minh họa trong test; nó dùng `MessageService` của ví dụ phía trên và các import Java tương ứng:

```java
List<Runnable> submittedTasks = new ArrayList<>();
TaskExecutor fakeExecutor = submittedTasks::add;
ExecutorDiDemo.MessageService service =
        new ExecutorDiDemo.MessageService(fakeExecutor);

CountDownLatch done = service.submit("T001");

// Task mới chỉ được gửi vào fake executor, chưa được chạy.
if (submittedTasks.size() != 1 || done.getCount() != 1) {
    throw new AssertionError("Expected exactly one pending task");
}

submittedTasks.get(0).run(); // Test chủ động quyết định khi nào chạy.
if (done.getCount() != 0) {
    throw new AssertionError("Task did not finish");
}
```

Các import cho fragment: `java.util.List`, `java.util.ArrayList`, `java.util.concurrent.CountDownLatch` và `org.springframework.core.task.TaskExecutor`.

Cách làm này kiểm tra được **service có gửi đúng một tác vụ** và việc thực thi task hoàn tất khi test chủ động gọi `run()`. Test không phụ thuộc thread scheduling. Nếu cần test tuần tự đơn giản hơn, dùng `new SyncTaskExecutor()`.

Lưu ý rằng “test được gửi tác vụ” chưa đồng nghĩa test được side effect cuối cùng như gửi email thành công. Hãy tách việc tạo nội dung/email ra khỏi cơ chế gửi nền để có thể assert kết quả nghiệp vụ độc lập.

## 6. Khi nào nên tiêm interface nào?

Chọn theo thứ **business service thật sự sử dụng**:

| Nhu cầu | Phụ thuộc phù hợp | Điều không nên giả định |
| --- | --- | --- |
| Chỉ giao `Runnable` để thực thi | `TaskExecutor` hoặc `Executor` | Task chắc chắn chạy ở thread khác |
| Muốn API `submit(...)` và nhận handle kết quả | `AsyncTaskExecutor` hoặc loại executor hỗ trợ `Future` | Mọi `TaskExecutor` đều có `submit` |
| Cần tác vụ chạy theo giờ/chu kỳ | `TaskScheduler` | `TaskExecutor` tự tạo lịch |
| Cần lấy active count, queue size để giám sát | Thành phần cấu hình/monitoring gắn với implementation, ví dụ `ThreadPoolTaskExecutor` | Service nghiệp vụ phải ép kiểu từ `TaskExecutor` |
| Chỉ muốn tuân theo JDK không phụ thuộc Spring API | `java.util.concurrent.Executor` | Phải dùng class Spring trong business logic |

Không nên tiêm `ThreadPoolTaskExecutor` vào service chỉ để gọi `execute()`. Nếu service phải gọi `getActiveCount()` hoặc thay đổi `setCorePoolSize()` trong lúc xử lý request, đó thường là dấu hiệu đang trộn trách nhiệm nghiệp vụ với giám sát/vận hành.

Đừng dùng ép kiểu như `((ThreadPoolTaskExecutor) executor).getActiveCount()` trong service: một ngày chuyển implementation sang `SyncTaskExecutor` sẽ gây `ClassCastException`. Những tính năng đặc thù nên nằm ở cấu hình hoặc thành phần chuyên giám sát hạ tầng.

## 7. Tiêm `TaskExecutor` khác gì với dùng `@Async`?

**Inject một `TaskExecutor`** nghĩa là business code chủ động gửi `Runnable` bằng `executor.execute(...)`. Nó hoạt động theo implementation đã tiêm, không yêu cầu cơ chế proxy của `@Async`.

**`@Async`** là cơ chế riêng của Spring để chuyển lời gọi method phù hợp sang executor. Với cấu hình Spring Framework thuần, thường cần `@EnableAsync` và method phải được gọi thông qua Spring proxy từ bên ngoài bean. Method tự gọi bằng `this.someAsyncMethod()` thường không đi qua proxy và không kích hoạt hành vi `@Async` theo cấu hình proxy mặc định.

Ví dụ minh họa (cần cấu hình `@EnableAsync` và một Spring-managed bean thích hợp, **không phải đoạn chạy độc lập**):

```java
@Async("businessExecutor")
public void sendNoticeAsync(String id) {
    // Xử lý nội dung thông báo.
}
```

`@Async("businessExecutor")` yêu cầu Spring tra executor theo tên; còn `@Qualifier("businessExecutor") TaskExecutor executor` là DI tại điểm khai báo dependency. Hai cơ chế có thể dùng cùng một bean nhưng khác đường gọi, vòng đời và nơi quan sát exception.

Tương tự, `CompletableFuture.runAsync(runnable, executor)` có thể nhận một `Executor`, nhưng tương tác đó không tự làm I/O bên trong task thành nonblocking. Một worker đang chờ JDBC/HTTP blocking vẫn có thể bị chiếm suốt thời gian chờ.

## 8. Ranh giới thực thi cần được quản lý dù đã tách interface

### 8.1. Số thread, hàng đợi và quá tải

Cấu hình minh họa `corePoolSize=2`, `maxPoolSize=4`, `queueCapacity=32` là thông số cho ví dụ, **không phải giá trị mặc định thích hợp cho mọi ứng dụng**. Với một `ThreadPoolTaskExecutor` dùng hàng đợi có dung lượng dương, pool thường ưu tiên dùng core threads rồi xếp hàng; chỉ khi queue đầy mới mở rộng vượt core đến max. Vì thế, đặt `maxPoolSize` lớn không đảm bảo pool lập tức mở rộng khi nhiều tác vụ đến.

Khi pool và queue đều không còn chỗ, lời gọi `execute(...)` có thể bị từ chối bằng `TaskRejectedException` (theo hợp đồng của Spring). Service phải có chính sách phù hợp: báo lỗi, retry có giới hạn, ghi nhận trạng thái thất bại hoặc sử dụng hàng đợi bền vững nếu nghiệp vụ cần bảo đảm giao việc.

Đo `active threads`, `queue depth`, tỷ lệ rejection, độ trễ xử lý và mức sử dụng CPU để cấu hình; không tăng pool vô hạn để giải quyết vấn đề downstream chậm.

### 8.2. Exception và kết quả

Khi dùng `execute(Runnable)` với implementation bất đồng bộ, exception phát sinh **bên trong worker** không tự động trở thành exception được ném lại trên thread gọi. Tách hai trường hợp:

- **Lỗi lúc gửi task:** có thể xảy ra trực tiếp tại `execute(...)`, ví dụ bị từ chối do quá tải.
- **Lỗi trong lúc chạy task:** cần ghi log/capture ở worker hoặc dùng cơ chế Future/CompletionStage, hệ thống quan sát task, hay cơ chế báo lỗi nghiệp vụ.

Không trả về “đã xử lý thành công” chỉ vì `execute(...)` không ném exception. Điều đó thường chỉ cho biết executor đã chấp nhận tác vụ theo hợp đồng implementation.

### 8.3. Giao dịch, bảo mật và ThreadLocal

Khi task thực sự chạy trên thread khác, không giả định các giá trị gắn với thread gọi được mang theo tự động. `ThreadLocal`, MDC logging, security context hoặc transaction context có thể không xuất hiện tại worker. Spring transaction khai báo bằng `@Transactional` không tự mở rộng nguyên vẹn sang một thread mới chỉ vì caller đang có transaction.

Thiết kế truyền **dữ liệu cần thiết** vào task, giới hạn thời gian thực thi, xử lý context propagation có chủ đích khi cần và đặt ranh giới transaction ở nơi tác vụ thực sự chạy. Có thể dùng `TaskDecorator` hoặc giải pháp context propagation phù hợp cho metadata quan sát, nhưng không lạm dụng sao chép trạng thái mutable giữa threads.

### 8.4. Thread safety và lifecycle

Một service được Spring tạo mặc định là singleton, và nhiều task có thể dùng chung cùng một instance. Mã trong `Runnable` phải bảo vệ trạng thái chia sẻ bằng cơ chế đồng bộ phù hợp hoặc ưu tiên dữ liệu bất biến. Việc inject qua interface không làm logic tự động thread-safe.

Để Spring quản lý executor nhằm có cơ chế khởi tạo, shutdown và lifecycle thích hợp. Khi đóng ứng dụng, cần xác định task đang chạy được hoàn tất, bị hủy hay bị chờ theo cấu hình; đừng giả định mọi công việc nền luôn được flush khi context đóng.

## 9. Lỗi thiết kế thường gặp

1. **Tiêm implementation vào mọi service:** làm code nghiệp vụ phụ thuộc API cấu hình pool; thay executor buộc sửa nhiều nơi.
2. **Gọi `new ThreadPoolTaskExecutor()` trong service:** bỏ qua quản lý bean lifecycle, dễ quên khởi tạo hoặc shutdown và khó thay thế khi test.
3. **Cho rằng `TaskExecutor` luôn async:** `SyncTaskExecutor` là phản ví dụ rõ ràng. Nếu nghiệp vụ yêu cầu tuyệt đối không chặn caller, phải đưa yêu cầu đó vào thiết kế và kiểm thử cấu hình thực tế.
4. **Có nhiều bean `TaskExecutor` nhưng không chỉ định rõ:** dễ injection ambiguity hoặc chọn sai pool; dùng tên vai trò và `@Qualifier` phù hợp.
5. **Dựa vào `maxPoolSize` khi queue còn rộng:** cấu hình pool có thể xếp hàng lâu và không mở rộng như tưởng tượng.
6. **Không xử lý rejection hoặc lỗi worker:** service báo đã gửi việc dù task bị từ chối hoặc thất bại bên trong.
7. **Tưởng thread mới giữ nguyên context request/transaction:** gây mất correlation id hoặc lỗi quyền truy cập, hoặc dùng transaction không đúng ranh giới.
8. **Test bằng `Thread.sleep` chờ tác vụ:** thường chậm và flakey; thay bằng test executor, latch có timeout, hoặc Future có kết quả.
9. **Dùng một pool cho mọi loại công việc:** task chờ I/O dài có thể chiếm hết worker, khiến tác vụ nhạy độ trễ bị starvation. Tách pool theo nhu cầu thực tế và đo tải.

## 10. Checklist áp dụng vào dự án Spring

Trước khi đưa một executor vào service, hãy tự trả lời:

- **Business contract:** service chỉ cần gửi `Runnable`, hay cần `Future`/kết quả, lên lịch hoặc bảo đảm giao việc?
- **Injection boundary:** field và constructor của service dùng `TaskExecutor` (hoặc `Executor`) hay đang vô tình phụ thuộc class pool?
- **Bean identity:** có bean executor đúng vai trò, tên nhất quán và `@Qualifier` rõ ràng khi có nhiều bean?
- **Runtime behavior:** trong môi trường hiện tại executor chạy đồng bộ hay bất đồng bộ, pool/queue có thể quá tải thế nào?
- **Testability:** có thể thay bằng `SyncTaskExecutor` hoặc fake executor và kiểm thử không phụ thuộc timing?
- **Operations:** timeout, exception, rejection, propagation, shutdown và các metric đã được xử lý ở đúng tầng chưa?

Một cách chia lớp dễ bảo trì là **business service nói “thực hiện tác vụ này” qua interface; phần cấu hình nói “thực hiện bằng cách nào” qua implementation**. Cấu trúc này giữ nghiệp vụ ổn định khi chuyển từ thực thi đồng bộ trong test sang thread pool trong runtime hoặc thay executor về sau.

## Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — TaskExecutor API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/core/task/TaskExecutor.html)
- [Spring Framework — ThreadPoolTaskExecutor API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-executor-006">Phân biệt TaskExecutor với TaskScheduler</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt TaskExecutor với TaskScheduler trong Spring Framework

Trong Spring Framework, **`TaskExecutor` trả lời câu hỏi “công việc này sẽ được thực thi bằng cơ chế nào?”**, còn **`TaskScheduler` trả lời câu hỏi “khi nào công việc này phải được kích hoạt?”**. Cả hai đều xử lý tác vụ (`Runnable`), nhưng khác nhau ở hợp đồng API, cách kích hoạt, cơ chế thời gian, đối tượng trả về và cách vận hành.

Ví dụ: người dùng vừa xác nhận đơn hàng và cần xử lý email nền → dùng `TaskExecutor`. Hệ thống phải kiểm tra đơn hàng quá hạn vào 09:00 mỗi ngày → dùng `TaskScheduler`. Nếu cứ 5 phút hệ thống phải kích hoạt một lô xử lý tốn nhiều thời gian, ta có thể **dùng scheduler để quyết định thời điểm, rồi executor để chạy phần công việc nặng**. Phân chia rõ hai trách nhiệm giúp cấu hình pool, giới hạn tải và theo dõi lỗi chính xác.

## 1. Bảng so sánh cốt lõi

| Tiêu chí | `TaskExecutor` | `TaskScheduler` |
| --- | --- | --- |
| Mục đích | Nhận và thực thi tác vụ được giao | Hẹn một lần chạy hoặc quy tắc chạy theo thời gian |
| Thời điểm kích hoạt | Caller chủ động gọi `execute(task)`; tác vụ chạy khi tài nguyên thực thi cho phép | Dựa trên `Instant`, chu kỳ `Duration` hoặc `Trigger` |
| Hợp đồng chính | `void execute(Runnable task)` | `schedule(Runnable, Instant/Trigger)`, `scheduleAtFixedRate(...)`, `scheduleWithFixedDelay(...)` |
| Phạm vi | Một lần submit không tự trở thành định kỳ | Một lần, fixed-rate, fixed-delay, cron/custom trigger |
| Kết quả thao tác API | `TaskExecutor.execute` không trả về Future; các API của `AsyncTaskExecutor` có thể trả Future | Các phương thức đặt lịch thường trả `ScheduledFuture<?>`; với Trigger không có lần chạy tiếp theo, có thể trả `null` |
| Annotation gắn với khái niệm | `@Async` và `@EnableAsync` | `@Scheduled` và `@EnableScheduling` |
| Triển khai thường gặp | `ThreadPoolTaskExecutor`, `SimpleAsyncTaskExecutor` | `ThreadPoolTaskScheduler`, `ConcurrentTaskScheduler`, `SimpleAsyncTaskScheduler` |
| Vấn đề khi dùng sai | Queue quá dài, pool bão hòa, task bị từ chối | Chạy muộn/lỡ nhịp, scheduler thread bị chặn, tác vụ định kỳ chồng lấn |

**Điểm quan trọng:** scheduler cũng cần luồng để thực thi callback; executor cũng có thể xếp queue nên không hứa “chạy ngay tức thì”. Sự khác biệt là **hợp đồng và ý nghĩa của lời gọi**, không phải hai loại luồng tách biệt tuyệt đối. Một lớp triển khai có thể hỗ trợ cả nhiều interface.

## 2. TaskExecutor: “hãy thực thi tác vụ này”

Spring định nghĩa giao diện tối thiểu:

~~~java
public interface TaskExecutor extends java.util.concurrent.Executor {
    void execute(Runnable task);
}
~~~

Đây là **chữ ký API rút gọn để giải thích**, không phải lớp nguồn cần tự viết lại trong ứng dụng. Caller gọi `execute` một lần cho mỗi công việc. Tùy implementation và cấu hình, lời gọi có thể được xử lý trên luồng khác, qua pool, hoặc theo một chiến lược thực thi cụ thể. `TaskExecutor` không có API biểu diễn “chạy lúc 9 giờ” hay “lặp lại mỗi 10 giây”.

### 2.1. `ThreadPoolTaskExecutor`

`ThreadPoolTaskExecutor` tích hợp với cơ chế `ThreadPoolExecutor` của Java, có thể cấu hình:

- `corePoolSize`: số worker lõi.
- `maxPoolSize`: giới hạn số worker trong điều kiện pool được phép tăng.
- `queueCapacity`: số tác vụ chờ tối đa trong queue.
- Chính sách khi hết khả năng nhận tác vụ, cách đặt tên luồng và lifecycle.

Với queue có dung lượng dương, sau khi đã đạt core size, tác vụ thường **vào queue trước**, rồi pool mới mở thêm worker tới max khi queue đầy. Vì vậy `maxPoolSize` lớn không bảo đảm các task sẽ chạy song song ngay. Với queue không giới hạn, có thể không cần tăng tới max. Executor bị quá tải có thể ném `TaskRejectedException`; phía gọi cần xử lý thay vì giả định mọi lần submit đều thành công.

`SimpleAsyncTaskExecutor` phù hợp với một số nhu cầu đơn giản nhưng về cơ bản **không phải pool tái sử dụng một nhóm worker cố định**; phải hiểu giới hạn concurrency và chi phí tạo luồng/virtual threads theo phiên bản. Không chọn implementation chỉ dựa vào chữ “async”.

### 2.2. `@Async` là một cách gửi công việc vào executor

Trong ứng dụng Spring:

~~~java
@Async("appExecutor")
public CompletableFuture<String> export(String reportId) {
    // Công việc chạy trong executor được chọn bởi @Async.
    return CompletableFuture.completedFuture("exported:" + reportId);
}
~~~

Đây là **phương thức minh họa bên trong một Spring-managed bean**, không phải chương trình độc lập. Cần bật `@EnableAsync`, có bean executor tên `appExecutor` và gọi phương thức thông qua Spring proxy. `@Async` không định nghĩa chu kỳ thời gian; mỗi lần bên ngoài gọi `export("r1")` sẽ yêu cầu thực thi một tác vụ.

Lưu ý: gọi `this.export(...)` từ chính instance thường **bỏ qua proxy trong chế độ proxy mặc định**, nên có thể không chuyển sang executor. Phương thức `@Async` trả về `void`, `Future` hoặc `CompletableFuture` (tùy API); đối với kiểu có Future, caller có thể theo dõi kết quả/lỗi, nhưng chờ bằng `join()`/`get()` sẽ khiến caller bị chặn. Exception của phương thức async trả `void` cần cơ chế theo dõi/xử lý lỗi phù hợp.

## 3. TaskScheduler: “hãy kích hoạt tác vụ theo lịch”

`TaskScheduler` có các nhóm API (chữ ký rút gọn theo Spring Framework 6.x):

~~~java
ScheduledFuture<?> schedule(Runnable task, Instant startTime);
ScheduledFuture<?> schedule(Runnable task, Trigger trigger);
ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period);
ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay);
~~~

Đây là **các chữ ký minh họa**, thuộc interface `TaskScheduler` trong Spring, không phải chương trình Java độc lập. Các overload cho phép định nghĩa thời điểm bắt đầu (`Instant`), chu kỳ (`Duration`) hoặc một chiến lược xác định lần chạy tiếp theo (`Trigger`). `CronTrigger` có thể biểu diễn lịch theo giờ/ngày trong tuần; `PeriodicTrigger` biểu diễn chu kỳ và có thể áp dụng qua interface `Trigger`.

- **Một lần tại thời điểm cụ thể:** `schedule(task, instant)`. Nếu thời điểm đã qua, việc thực thi có thể được đưa vào lịch sớm nhất có thể theo implementation; không dùng nó như bộ hẹn giờ chính xác tuyệt đối.
- **Fixed rate:** lịch nhắm tới khoảng cách đều giữa các **thời điểm bắt đầu**; nếu công việc quá lâu hoặc scheduler bận, lần chạy thực tế có thể muộn.
- **Fixed delay:** chờ hết một khoảng delay **sau khi lần chạy trước kết thúc** rồi mới chạy tiếp.
- **Cron/custom trigger:** lần chạy tiếp theo phụ thuộc biểu thức thời gian hoặc logic tính lịch; timezone và thay đổi giờ mùa hè có thể ảnh hưởng hành vi.

Các lời gọi lập lịch trả handle `ScheduledFuture<?>` để kiểm tra/cancel ở phạm vi mà implementation hỗ trợ. Đối với lịch lặp lại, Future không mang nghĩa “đã hoàn thành toàn bộ công việc định kỳ”; `cancel(...)` dừng việc lập lịch về sau và có thể yêu cầu ngắt công việc đang chạy, nhưng **không bảo đảm rollback một side effect đã xảy ra**. Với trigger không còn thời điểm hợp lệ, việc đặt lịch có thể trả `null`.

### 3.1. Fixed-rate và fixed-delay không giống nhau

Giả sử thời lượng một lần chạy là **8 giây**, chu kỳ đặt là **5 giây**, không tính độ trễ bổ sung:

| Cách lập lịch | Ý nghĩa | Mốc minh họa |
| --- | --- | --- |
| Fixed rate | Các thời điểm bắt đầu mục tiêu cách nhau 5 giây; lần đến hạn bị muộn nếu lần trước còn chạy | Dự kiến 0s, 5s, 10s...; nếu chỉ có một job tuần tự, thực tế có thể bắt đầu khoảng 0s, 8s, 16s... |
| Fixed delay | Chờ 5 giây sau **mỗi lần kết thúc** | Bắt đầu khoảng 0s → kết thúc 8s → bắt đầu 13s → kết thúc 21s → bắt đầu 26s |
| Cron | Khớp lịch trên đồng hồ theo biểu thức, không tính từ thời lượng tác vụ | Ví dụ 09:00 các ngày làm việc; nếu đang quá tải, cần hiểu chính sách lần chạy bị lỡ |

Các mốc trên chỉ giúp hình dung, không phải cam kết thời gian thực. `ThreadPoolTaskScheduler` truyền thống dùng `ScheduledThreadPoolExecutor`: các lần thực thi của **cùng một periodic task đã đăng ký** không tự chồng nhau trong executor này; tuy nhiên **nhiều đăng ký lịch khác nhau**, nhiều instance ứng dụng, hoặc callback chủ động gửi việc qua executor khác vẫn có thể khiến nghiệp vụ chạy đồng thời.

### 3.2. `@Scheduled` và cron

Ví dụ phương thức Spring-managed bean:

~~~java
@Scheduled(fixedDelay = 10_000, initialDelay = 2_000)
public void pollStatus() {
    // Mỗi lần kết thúc pollStatus(), đợi thêm 10 giây rồi gọi lần tiếp theo.
}
~~~

Hoặc một lịch cố định theo giờ Việt Nam:

~~~java
@Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
public void prepareDailyReport() {
    // 09:00 thứ Hai đến thứ Sáu, theo múi giờ đã chỉ định.
}
~~~

Hai đoạn là **phương thức minh họa**, đặt trong các Spring bean và cần `@EnableScheduling`; không độc lập chạy bằng `javac`. Biểu thức cron của Spring dùng **6 trường**: giây, phút, giờ, ngày trong tháng, tháng, ngày trong tuần. `@Scheduled` được Spring kích hoạt theo lịch, nên phương thức thông thường **không nhận tham số nghiệp vụ từ caller**. Khác `@Async`, không cần có người gọi phương thức mỗi lần chạy.

**Không hiểu `fixedDelay` là khoảng giữa hai lần bắt đầu.** Định nghĩa đúng là khoảng sau **kết thúc callback mà scheduler trực tiếp quản lý**. Nếu callback chỉ submit một tác vụ nền rồi trả về, scheduler có thể xem callback đã xong dù tác vụ nền vẫn đang chạy; chi tiết này rất quan trọng khi kết hợp hai abstraction.

## 4. Mối quan hệ giữa interface, implementation và annotation

~~~text
A. Yêu cầu phát sinh từ nghiệp vụ
HTTP / service method / event
          |
          v
  TaskExecutor.execute(task)
          |
          v
  ThreadPoolTaskExecutor
          |
          v
    Worker thực thi

B. Công việc cần chạy theo thời điểm
Instant / fixed-rate / fixed-delay / CronTrigger
          |
          v
    TaskScheduler
          |
          v
  ThreadPoolTaskScheduler
          |
          v
    Callback được kích hoạt

C. Kết hợp khi có công việc nặng
TaskScheduler --> callback ngắn --> TaskExecutor --> worker làm việc
                         |
                         +--> callback xong KHÔNG có nghĩa worker xong
~~~

`@Async` liên quan đến việc **chuyển lời gọi phương thức sang executor**. `@Scheduled` liên quan đến việc **đăng ký một lịch gọi phương thức**. Cả hai đều là tính năng do Spring quản lý, nhưng dùng các bean và chính sách khác nhau. `@EnableAsync` không thay thế `@EnableScheduling`; bật scheduling cũng không tự biến mọi phương thức thành async.

### 4.1. Có implementation làm được cả hai việc không?

Có. Ví dụ, `ThreadPoolTaskScheduler` truyền thống cũng hỗ trợ một số hợp đồng thực thi tác vụ ngoài `TaskScheduler`. Vì thế không nên định nghĩa khác biệt theo kiểu “hai interface không thể cùng tồn tại trong một object”.

Tuy nhiên việc dùng **riêng** `ThreadPoolTaskExecutor` cho khối lượng blocking/CPU-bound và `ThreadPoolTaskScheduler` cho điều phối thời gian có thể dễ kiểm soát hơn. Tác vụ chạy lâu trực tiếp trên scheduler chiếm scheduler worker, khiến các lịch khác bị chậm. Sự tách biệt là một quyết định kiến trúc có lý do, không phải ràng buộc bắt buộc của Spring.

## 5. Java 17: ví dụ chạy độc lập để hiểu “execute” và “schedule”

Trước khi kết nối với Spring, ta có thể nhìn ý tưởng tương đương trong Java chuẩn: `ExecutorService` tiếp nhận công việc, `ScheduledExecutorService` hẹn công việc. Ví dụ này **không sử dụng Spring** nhưng cho phép kiểm chứng sự khác biệt bằng JDK 17.

Tạo tệp `ExecutorVsSchedulerDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ExecutorVsSchedulerDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            CountDownLatch firstDone = new CountDownLatch(1);
            executor.execute(() -> {
                System.out.println("executor: task completed");
                firstDone.countDown();
            });
            if (!firstDone.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Executor task timed out");
            }

            ScheduledFuture<?> later = scheduler.schedule(
                    () -> System.out.println("scheduler: task completed"),
                    100, TimeUnit.MILLISECONDS);
            later.get(5, TimeUnit.SECONDS);
            System.out.println("main: finished");
        } finally {
            executor.shutdownNow();
            scheduler.shutdownNow();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 ExecutorVsSchedulerDemo.java
java ExecutorVsSchedulerDemo
~~~

Kết quả đã kiểm chứng trên OpenJDK (thứ tự được bảo đảm bằng `CountDownLatch` và `Future.get()`):

~~~text
executor: task completed
scheduler: task completed
main: finished
~~~

`executor.execute(...)` giao một việc để thực hiện, còn `scheduler.schedule(..., 100, MILLISECONDS)` đặt việc thứ hai chạy **sau ít nhất khoảng trễ đã yêu cầu** (thời gian thực tế phụ thuộc lịch hệ điều hành). `CountDownLatch` đảm bảo việc thứ nhất đã xong mới đặt lịch cho việc thứ hai; `later.get(...)` chờ việc đã hẹn hoàn tất. Chương trình hủy nguồn lực trong `finally`. Trong Spring, implementation có thể bọc hoặc quản lý các executor Java này, nhưng API của Spring cung cấp sự trừu tượng để không gắn trực tiếp nghiệp vụ vào cách tạo luồng.

## 6. Ví dụ Spring Framework: cấu hình cả executor và scheduler

Các đoạn dưới đây dành cho dự án **Java 17+, Spring Framework 6.x** có `spring-context` trên classpath và component scanning; có thể dùng trong Spring Boot 3.x. Chúng là **mã tích hợp nhiều tệp**; không thể chỉ biên dịch một tệp bằng `javac` nếu thiếu Spring dependencies.

### 6.1. Cấu hình hai bean với trách nhiệm rõ ràng

Tệp `ConcurrencyConfig.java`, trong package ví dụ `example.concurrency`:

~~~java
package example.concurrency;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableAsync
@EnableScheduling
public class ConcurrencyConfig {
    @Bean("appExecutor")
    public ThreadPoolTaskExecutor appExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("work-");
        return executor;
    }

    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("schedule-");
        return scheduler;
    }
}
~~~

- Bean `appExecutor` dùng cho `@Async("appExecutor")`; `4/8/32` chỉ là thông số minh họa, phải tuning theo tải, thời gian xử lý, bộ nhớ và giới hạn dịch vụ phía sau.
- Bean `taskScheduler` là scheduler duy nhất và mang tên thông dụng mà cơ chế `@Scheduled` của Spring có thể tìm thấy. `setPoolSize(2)` giới hạn concurrency của các callback chạy trực tiếp trong scheduler pool, không phải số lần thực thi nghiệp vụ mà các callback có thể submit sang executor.
- `@EnableAsync` bật xử lý annotation async; `@EnableScheduling` bật xử lý annotation scheduled. Việc đặt tên và chọn bean cụ thể tránh phụ thuộc vào các mặc định có thể thay đổi tùy cách cấu hình Spring Boot.
- Để Spring tự quản lý lifecycle, dùng các bean trong application context. Không tự tạo hàng loạt scheduler/executor thô mà quên shutdown khi ứng dụng dừng.

### 6.2. Tác vụ được yêu cầu bất đồng bộ

Tệp `ReportWorker.java`:

~~~java
package example.concurrency;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportWorker {
    @Async("appExecutor")
    public CompletableFuture<String> generate(String reportId) {
        // Minh họa thao tác nghiệp vụ; thay bằng xử lý thật với timeout hợp lý.
        return CompletableFuture.completedFuture("generated:" + reportId);
    }
}
~~~

Khi một controller/service khác gọi `reportWorker.generate("R-7")` qua bean Spring, tác vụ được submit vào `appExecutor`. Người gọi có thể tiếp tục xử lý và nhận `CompletableFuture<String>`. Không có lịch chạy định kỳ trong method này; **một lời gọi tương ứng một lần yêu cầu công việc**. Dù return value của method minh họa là `completedFuture`, bản thân lời gọi `@Async` vẫn được Spring proxy chuyển sang executor nếu wiring đúng.

### 6.3. Scheduler quyết định khi nào kích hoạt việc

Tệp `ReportSchedule.java`:

~~~java
package example.concurrency;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReportSchedule {
    private final ReportWorker reportWorker;

    public ReportSchedule(ReportWorker reportWorker) {
        this.reportWorker = reportWorker;
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 2_000)
    public void requestReport() {
        reportWorker.generate("periodic")
                .whenComplete((result, error) -> {
                    if (error != null) {
                        System.err.println("Report failed: " + error.getMessage());
                    } else {
                        System.out.println(result);
                    }
                });
    }
}
~~~

Một lần chạy điển hình:

1. `taskScheduler` gọi `requestReport()` sau lần trễ khởi đầu.
2. Callback gọi sang **bean khác** `ReportWorker`, vì vậy lời gọi `@Async` đi qua Spring proxy.
3. `appExecutor` nhận nhiệm vụ và worker thực hiện `generate(...)`.
4. `requestReport()` trở về sau khi gửi tác vụ và gắn callback hoàn thành, không chờ báo cáo thật sự xong.
5. `fixedDelay=10_000` tính từ **lúc `requestReport()` hoàn tất**; nó **không đảm bảo** hai lượt `generate(...)` cách nhau 10 giây hoặc không chồng lấn.

Ví dụ minh họa đầy đủ cách hai abstraction phối hợp. Trong sản phẩm thật, nếu **không cho phép hai báo cáo xử lý đồng thời**, cần kiểm soát một-job-at-a-time bằng trạng thái `in-flight`, giới hạn nhận việc hoặc cơ chế khóa phù hợp; không chỉ dựa vào `fixedDelay` trên callback gửi việc. Tương tự, queue `appExecutor` có thể đầy và lời submit có thể bị từ chối; cần quan sát lỗi và chính sách xử lý cụ thể.

### 6.4. Dùng TaskScheduler trực tiếp khi lịch đến từ dữ liệu nghiệp vụ

`@Scheduled` thuận tiện cho lịch tương đối cố định bằng annotation. Khi thời điểm chạy là dữ liệu runtime (ví dụ “gửi thông báo vào lúc khách hàng đã chọn”), có thể dùng API `TaskScheduler`:

~~~java
package example.concurrency;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class DeferredTaskService {
    private final TaskScheduler scheduler;
    private final TaskExecutor executor;

    public DeferredTaskService(
            @Qualifier("taskScheduler") TaskScheduler scheduler,
            @Qualifier("appExecutor") TaskExecutor executor) {
        this.scheduler = scheduler;
        this.executor = executor;
    }

    public ScheduledFuture<?> scheduleDispatch(Instant when, Runnable job) {
        return scheduler.schedule(() -> executor.execute(job), when);
    }
}
~~~

Đây cũng là **mã tích hợp Spring**, không phải ví dụ `javac` độc lập. `scheduleDispatch(when, job)` trả về handle của **callback gửi tác vụ**; nếu callback đã submit thành công, `ScheduledFuture<?>` có thể đã hoàn thành dù `job` còn đang chạy trong `appExecutor`. Không dùng handle đó làm bằng chứng xử lý nghiệp vụ đã thành công. Nếu muốn theo dõi tiến độ/kết quả thật, thiết kế thêm `Future`, job id, trạng thái lưu trữ hoặc callback hoàn thành thuộc executor. Việc hủy lịch chỉ có hiệu lực phù hợp với trạng thái chưa kích hoạt/đã kích hoạt, không tự động hủy mọi tác vụ nền đã được submit.

Lịch lưu trong bộ nhớ scheduler cũng không phải **lịch bền vững**: ứng dụng restart có thể mất đăng ký một lần chưa được khôi phục. Khi lịch là cam kết nghiệp vụ, cần lưu dữ liệu lịch bền vững và phục hồi/điều phối lại theo thiết kế.

## 7. Chọn đúng abstraction cho tình huống thực tế

| Tình huống | Dùng gì? | Vì sao? |
| --- | --- | --- |
| API vừa nhận yêu cầu xuất file và trả response sớm | `TaskExecutor` hoặc `@Async` | Công việc xuất hiện khi có request, không cần trigger thời gian |
| Sau 30 phút mới thực hiện một thao tác | `TaskScheduler.schedule(..., Instant)` | Có thời điểm thực hiện trong tương lai |
| Cứ 10 giây kiểm tra trạng thái | `@Scheduled(fixedDelay=...)` / `TaskScheduler` | Muốn tự động kích hoạt lặp lại |
| 09:00 mỗi ngày làm việc tổng hợp báo cáo | Cron trên `TaskScheduler` / `@Scheduled(cron=...)` | Lịch theo thời gian trên đồng hồ và timezone |
| Scheduler phát hiện dữ liệu mới rồi cần xử lý nhiều tác vụ nặng | `TaskScheduler` + `TaskExecutor` | Một lớp điều phối nhịp, lớp kia quản lý concurrency công việc |
| Controller gọi đồng thời nhiều bước I/O có thể chạy độc lập | `TaskExecutor` hoặc cơ chế bất đồng bộ thích hợp | Điều phối song song theo yêu cầu, không phải lịch định kỳ |
| Nhiều instance cần **duy nhất một** lượt chạy toàn cụm | Scheduler **kèm cơ chế điều phối phân tán** | `@Scheduled` mặc định đăng ký trên mỗi application context, không tự cung cấp exactly-once toàn cụm |

Đừng biến mọi tác vụ chạy nền thành `@Scheduled` chỉ vì nó ở background. Nếu không có nhu cầu **thời gian kích hoạt**, một executor đủ. Ngược lại, nếu phải chạy lại theo lịch, một executor thuần không tự quản lý cron/độ trễ.

## 8. Giới hạn, lỗi thường gặp và cách chẩn đoán

### 8.1. “Đã dùng @Async nhưng vẫn chạy trên caller thread”

Kiểm tra `@EnableAsync`, vị trí bean do Spring quản lý, tên executor và lời gọi thông qua proxy. Self-invocation như `this.generate()` trong cùng bean có thể bypass interception. Thêm `@Scheduled` không làm `@Async` hoạt động trở lại. Ghi thread name và kiểm tra stack chỉ để xác nhận wiring, tránh phụ thuộc vào tên luồng như một hợp đồng API.

### 8.2. “Đã dùng @Scheduled nhưng lịch không chạy”

Kiểm tra `@EnableScheduling`, bean có thực sự được đăng ký, cron có đúng **6 trường** và timezone không, ứng dụng có đang chạy, scheduler có bị block hoặc ngừng không. Tác vụ ném exception cần được ghi nhận qua error handling/metrics. Không giả định một lịch sẽ chạy chính xác từng mili giây hoặc các lần bị trễ được tự chạy bù giống nhau trong mọi Trigger.

### 8.3. “Tăng pool scheduler thì tác vụ async có nhanh hơn?”

Không nhất thiết. `taskScheduler.setPoolSize(...)` tăng worker có thể dùng cho callback của scheduler; `appExecutor.setCorePoolSize(...)`/`setMaxPoolSize(...)` ảnh hưởng executor nhận tác vụ nghiệp vụ. Nếu callback chỉ submit nhanh vào executor, bottleneck có thể ở `appExecutor`, DB pool hoặc dịch vụ bên ngoài. Đo đúng queue và độ trễ ở từng tầng; tăng một pool không thay thế kiểm tra nút thắt phía sau.

### 8.4. “Fixed-delay đã cấu hình nhưng các job vẫn chồng nhau”

Nếu phương thức scheduled **trực tiếp chạy đồng bộ toàn bộ công việc**, fixed-delay tính từ khi công việc đó kết thúc. Nếu phương thức scheduled **chỉ gọi `@Async`** và trả về sớm, fixed-delay chỉ bao quanh lượt *dispatch*. Một công việc vẫn chạy khi lượt dispatch tiếp theo đến. Giải pháp là chờ có kiểm soát nếu phù hợp, kiểm soát `in-flight` trước khi submit hoặc dùng mô hình hàng đợi/idempotency/lock phân tán theo yêu cầu. Đừng chặn scheduler vô hạn để “chắc chắn không chồng lấn”.

### 8.5. “TaskScheduler và TaskExecutor có thể deadlock/cạn luồng không?”

Có. Nếu mọi scheduler thread đều chờ `Future.get()` từ những task mà executor không còn tài nguyên chạy, lịch bị trì hoãn; nếu worker trong executor lại chờ chính scheduler để giải phóng tài nguyên, có thể xuất hiện chu trình phụ thuộc. Không chặn executor worker chờ task con của cùng pool; không giữ khóa qua I/O chặn lâu; đặt timeout và kiểm soát giới hạn queue/connection.

### 8.6. “Một job scheduled chạy trên nhiều pod có bình thường không?”

Có thể hoàn toàn bình thường: mỗi instance Spring có `@EnableScheduling` và bean `@Scheduled` sẽ đăng ký lịch của nó. `TaskScheduler` không tự thực hiện leader election. Với yêu cầu chỉ một lần toàn cụm, cần thiết kế distributed lock, leader election, job store hoặc hệ thống điều phối phù hợp và bảo vệ idempotency. Cần làm rõ khác biệt giữa “lịch chạy đúng giờ trên từng instance” và “xử lý đúng một lần toàn hệ thống”.

### 8.7. Đo những gì?

- **Ở scheduler:** số callback kích hoạt, thời điểm dự kiến và thực tế, độ trễ kích hoạt, thời lượng callback, lần chạy lỗi, lần chạy bị bỏ lỡ theo trigger.
- **Ở executor:** active threads, kích thước pool, queue depth/queue wait, số tác vụ hoàn tất, rejection, duration và exception.
- **Ở nghiệp vụ:** số job đang chạy, thời gian end-to-end, số job thành công/thất bại, kết quả side effect, khả năng retry.
- **Ở downstream:** connection pool, timeout, CPU quota, độ trễ DB/HTTP; nhiều worker không tạo thêm năng lực cho một dịch vụ bị giới hạn.

Với các API async, callback lỗi phải ghi nhận exception. Với lịch một lần quan trọng, phải nghĩ tới restart và cancel. Với tác vụ lặp lại, cần chọn chính sách khi trễ, chống chạy chồng, retry/backoff và shutdown một cách rõ ràng.

## 9. Câu hỏi tự kiểm tra

**1. Có phải TaskExecutor luôn tạo thread mới?** Không. `TaskExecutor` là hợp đồng giao tác vụ. `ThreadPoolTaskExecutor` thường tái sử dụng worker, còn implementation khác có thể dùng chiến lược riêng.

**2. Muốn chạy job lúc 09:00 thứ Hai–thứ Sáu nên dùng gì?** `TaskScheduler` với cron/`@Scheduled(cron=...)` và timezone rõ ràng. Executor có thể được dùng thêm nếu callback cần offload việc nặng.

**3. `@Async` làm cho method tự lặp sau 10 giây không?** Không. Nó gắn với việc submit mỗi lần method được gọi, không phải cơ chế tính thời điểm gọi lại.

**4. `fixedDelay=10_000` trên callback chỉ submit một `@Async` task có bảo đảm cách nhau 10 giây giữa hai lần task *kết thúc* không?** Không. Delay tính sau khi callback scheduled trở về; worker xử lý thật có thể tiếp tục chạy.

**5. `ScheduledFuture<?>` trả từ `scheduler.schedule(() -> executor.execute(job), when)` đại diện cho điều gì?** Việc đặt lịch và hoàn thành callback **submit job**; không bảo đảm `job` đã hoàn tất thành công trong executor.

**6. Nếu scheduler có 2 threads và executor có 8 workers, hệ thống có tổng cộng chỉ 2 công việc nghiệp vụ cùng lúc không?** Không. Nếu scheduler chỉ dispatch ngắn, các công việc nghiệp vụ có thể chạy trong executor với giới hạn của executor và downstream. Cần phân biệt số callback đang chạy với số job đã gửi.

**7. Nếu nhiều replica đều có cùng `@Scheduled`, Spring có tự đảm bảo toàn cụm chỉ chạy một lần không?** Không. Phải có cơ chế điều phối phân tán và thiết kế chống thực thi trùng khi nghiệp vụ yêu cầu.

## 10. Tài liệu tham khảo

- Spring Framework Reference — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework API — TaskExecutor: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/core/task/TaskExecutor.html
- Spring Framework API — TaskScheduler: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/TaskScheduler.html
- Spring Framework API — ThreadPoolTaskExecutor: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Spring Framework API — ThreadPoolTaskScheduler: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html
- Java SE 17 API — ScheduledExecutorService: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-executor-007">TaskRejectedException và hợp đồng khi không nhận được tác vụ</a>

<details>
<summary>Xem chi tiết</summary>

# TaskRejectedException và hợp đồng khi không nhận được tác vụ trong Spring TaskExecutor

Khi một ứng dụng Spring gửi công việc nền cho `TaskExecutor`, đôi khi executor không thể hoặc không muốn nhận thêm tác vụ. Đây là một tình huống vận hành bình thường cần được thiết kế trước, đặc biệt khi hệ thống chịu tải cao hoặc đang shutdown. Trong Spring, `TaskRejectedException` là tín hiệu quan trọng của tình huống đó. Hiểu đúng tín hiệu này giúp tránh trả lời “đã nhận việc” khi thực tế chưa có nơi nào chịu trách nhiệm xử lý công việc.

Bài học tập trung vào **hợp đồng tại thời điểm submit**, nguyên nhân từ chối, chính sách xử lý khi quá tải và cách ứng dụng Spring phản hồi. Nó không giả định rằng mọi `TaskExecutor` đều chạy bất đồng bộ hoặc mọi tác vụ đều được lưu bền vững.

## 1. Sau bài học, bạn cần phân biệt được điều gì?

- Phân biệt **executor nhận tác vụ**, **tác vụ được bắt đầu**, **tác vụ hoàn thành** và **kết quả nghiệp vụ được lưu bền vững**.
- Xác định khi nào `TaskExecutor.execute(Runnable)` có thể ném `TaskRejectedException` và quan hệ của nó với `RejectedExecutionException` trong JDK.
- Hiểu trình tự sử dụng `corePoolSize`, hàng đợi, `maxPoolSize` và thời điểm pool đạt giới hạn.
- So sánh `AbortPolicy`, `CallerRunsPolicy`, `DiscardPolicy`, `DiscardOldestPolicy` theo **hợp đồng mất việc, độ trễ và backpressure**.
- Cấu hình `ThreadPoolTaskExecutor` có giới hạn, bắt lỗi ở ranh giới gửi việc, và thiết kế response/retry phù hợp với mức bảo đảm thực tế.
- Kiểm thử tình huống từ chối **một cách xác định bằng latch**, thay vì dựa vào `Thread.sleep()`.

## 2. `TaskExecutor` hứa điều gì?

Spring định nghĩa `org.springframework.core.task.TaskExecutor` kế thừa `java.util.concurrent.Executor`, với phương thức:

~~~java
void execute(Runnable task);
~~~

Lời gọi chuyển một `Runnable` cho executor. Tùy implementation, tác vụ có thể chạy ngay trên thread gọi (`SyncTaskExecutor`), chạy trên worker thread hoặc được xếp vào hàng đợi. API không trả về job ID, kết quả, trạng thái hoàn thành hay cam kết lưu bền vững.

Theo hợp đồng `TaskExecutor`, khi không chấp nhận được tác vụ, `execute` có thể ném **`TaskRejectedException`** (unchecked). Đây là lỗi **tại ranh giới giao việc**. Không đồng nhất nó với exception xảy ra bên trong `Runnable.run()` sau khi tác vụ đã bắt đầu.

### Bốn cột mốc khác nhau

| Cột mốc | Ý nghĩa | Điều chưa được bảo đảm |
| --- | --- | --- |
| `execute` được gọi | Bên gửi yêu cầu executor nhận việc | Chưa biết có nhận hay không |
| Không có rejection trong cấu hình báo lỗi rõ ràng | Executor đã nhận việc theo chính sách đang dùng, hoặc có thể đã chạy đồng bộ | Chưa bảo đảm task bắt đầu, hoàn thành, thành công hoặc bền vững |
| `Runnable.run()` bắt đầu | Worker/caller bắt đầu thực thi công việc | Công việc có thể lỗi, bị hủy hoặc dừng giữa chừng |
| Tác vụ hoàn tất và kết quả được commit | Có một kết quả thực thi; mức bền vững do tầng lưu trữ quyết định | Không có cam kết chống trùng lặp nếu retry không được thiết kế |

**Ngoại lệ quan trọng:** nếu cấu hình chính sách **im lặng bỏ tác vụ** như `DiscardPolicy`, `execute` có thể trở về bình thường dù tác vụ không hề được chạy hay xếp hàng. Vì vậy câu “không ném exception tức là chắc chắn đã nhận việc” chỉ có thể dựa vào **chính sách từ chối cụ thể** (ví dụ `AbortPolicy`), không phải một sự thật cho mọi executor.

Để đáp ứng yêu cầu “mỗi job phải được xử lý”, một hàng đợi in-memory và lời gọi `execute` thành công **chưa đủ**. Cần có nơi tiếp nhận bền vững, định danh job, cơ chế khôi phục, theo dõi trạng thái và quy tắc retry/idempotency.

## 3. `TaskRejectedException` có ý nghĩa thế nào?

`org.springframework.core.task.TaskRejectedException` **kế thừa `java.util.concurrent.RejectedExecutionException`**. Nó là runtime exception biểu thị rằng một `TaskExecutor` không nhận tác vụ theo hợp đồng của Spring. Khi `ThreadPoolTaskExecutor` nhận `RejectedExecutionException` từ JDK executor bên dưới, nó thường chuyển thành `TaskRejectedException` để phía Spring có loại exception nhất quán.

Hệ quả khi bắt lỗi:

~~~java
try {
    taskExecutor.execute(task);
} catch (TaskRejectedException ex) {
    // Hợp đồng Spring: job không được tiếp nhận.
    // Phải công khai trạng thái không nhận việc cho caller.
}
~~~

Đây là **đoạn minh họa đặt trong phương thức** có `taskExecutor` và `task` hợp lệ; phần xử lý trong `catch` phải do yêu cầu nghiệp vụ quyết định. Nếu gọi trực tiếp `java.util.concurrent.ThreadPoolExecutor`, exception tương ứng thường là `RejectedExecutionException`. Vì `TaskRejectedException` là subclass, một `catch (RejectedExecutionException ex)` cũng bắt được nó, nhưng bắt loại cụ thể của Spring thường truyền tải ý định rõ hơn ở tầng ứng dụng Spring.

Không được diễn giải `TaskRejectedException` thành “task đã chạy rồi lỗi”, “đã retry”, hoặc “tự động xử lý ở lần sau”. Tại thời điểm đó, ứng dụng phải quyết định có trả lỗi, giảm tải, lưu việc để gửi lại hay chuyển sang cơ chế dự phòng.

### Phân biệt với exception từ tác vụ

Giả sử đã gửi được một task:

~~~java
taskExecutor.execute(() -> {
    throw new IllegalStateException("Không đọc được tệp đầu vào");
});
~~~

Trong mô hình executor **bất đồng bộ** thông thường, exception của `run()` xảy ra trên worker thread, **không** quay lại lời gọi `execute` ở thread ban đầu theo cách tương tự rejection. Nếu implementation chạy đồng bộ, exception của body có thể xuất hiện ngay trên caller thread. Các lớp `Future`/`CompletableFuture` có đường truyền kết quả hoặc lỗi riêng; cần đọc đúng completion state thay vì chỉ bắt `TaskRejectedException`.

## 4. Khi nào một pool từ chối?

Với `ThreadPoolTaskExecutor` thường dùng trong Spring, lớp nền là `ThreadPoolExecutor` của JDK. Hai nguyên nhân điển hình:

1. **Đạt giới hạn tải:** các worker hiện có đang bận, queue đã đầy và không còn chỗ tăng thread dưới `maxPoolSize`.
2. **Đã/đang shutdown:** executor ngừng chấp nhận tác vụ mới theo vòng đời hoặc chính sách shutdown.

Các implementation khác có thể có quy tắc riêng. Ví dụ executor có concurrency limit, chính sách chờ hoặc chạy đồng bộ có ngữ nghĩa khác; **không** áp công thức của `ThreadPoolExecutor` lên mọi `TaskExecutor`.

### Trình tự phân bổ công việc của một `ThreadPoolExecutor` thông thường

1. Nếu số worker hiện có nhỏ hơn `corePoolSize`, ưu tiên tạo worker để chạy task mới.
2. Sau mức core, thử **đưa task vào queue**.
3. Khi queue không còn nhận thêm, nếu số worker nhỏ hơn `maxPoolSize` thì thử mở rộng pool.
4. Nếu đã hết khả năng nhận task, gọi `RejectedExecutionHandler`.

Ví dụ cấu hình `corePoolSize=2`, `maxPoolSize=4`, `queueCapacity=3`: dưới tải liên tục khiến task đang chạy chưa xong, pool dùng 2 worker core; sau đó 3 task chờ; tiếp theo có thể mở rộng lên 4 worker; tác vụ tiếp theo bị từ chối nếu cả worker và queue vẫn bão hòa. Đây là mô hình **tại thời điểm tài nguyên đang bận**; trong thực tế task kết thúc đồng thời có thể làm thay đổi kết quả.

### Đừng bỏ qua cấu hình mặc định

`ThreadPoolTaskExecutor` thường mặc định `corePoolSize=1`, `maxPoolSize=Integer.MAX_VALUE` và `queueCapacity=Integer.MAX_VALUE`. Với queue gần như không giới hạn, pool khó tăng vượt core và số job tồn đọng có thể tăng lớn, thậm chí gây thiếu bộ nhớ. **Không thấy rejection không có nghĩa hệ thống khỏe mạnh.**

Một vài lựa chọn queue cần hiểu:

- `queueCapacity > 0`: Spring dùng `LinkedBlockingQueue` có sức chứa khai báo.
- `queueCapacity = 0`: Spring dùng `SynchronousQueue`, không có buffer; task cần được giao cho worker hoặc dẫn tới mở rộng pool/rejection.
- Queue hữu hạn cùng `maxPoolSize` hữu hạn giúp xác định được ngưỡng nhận việc và đường xử lý overload.
- Tăng `maxPoolSize` không giải quyết được queue quá lớn nếu cơ chế vẫn luôn đưa vào queue trước khi tăng worker.

Đừng dựa vào con số cấu hình như một cam kết tuyệt đối về latency hoặc thông lượng. Tốc độ mỗi task, block I/O, thời gian chờ dependency, lịch CPU và chính sách từ chối mới quyết định tải thực tế.

## 5. Các `RejectedExecutionHandler` và mặt trái của từng chính sách

`ThreadPoolTaskExecutor` cho phép cấu hình `setRejectedExecutionHandler(...)` để chuyển chính sách từ chối xuống JDK pool. Bốn lựa chọn có sẵn của `ThreadPoolExecutor`:

| Chính sách | Khi pool bão hòa | Phù hợp khi | Rủi ro cần chủ động xử lý |
| --- | --- | --- | --- |
| `AbortPolicy` (mặc định) | Ném `RejectedExecutionException`; Spring thường chuyển thành `TaskRejectedException` | Muốn caller **biết chắc không nhận được việc** và quyết định retry/HTTP error | Nếu caller không bắt/không chuyển lỗi phù hợp sẽ lộ lỗi chưa kiểm soát |
| `CallerRunsPolicy` | Caller thread **tự chạy** task nếu pool chưa shutdown | Chấp nhận tăng latency của producer để tạo backpressure | Có thể chặn request thread, event loop, hoặc làm việc chạy đồng bộ bất ngờ; nếu pool đã shutdown, task có thể bị bỏ im lặng |
| `DiscardPolicy` | Bỏ task mà **không ném lỗi** | Chỉ với tín hiệu best-effort có thể mất và được thiết kế chấp nhận mất | Caller có thể tưởng đã nhận; `Future` tương ứng có thể không bao giờ hoàn thành |
| `DiscardOldestPolicy` | Bỏ phần tử ở đầu queue rồi thử gửi task mới, trừ trường hợp shutdown | Trường hợp đặc biệt nơi task chờ cũ được phép hy sinh | Mất job cũ, phá thứ tự, làm `Future` của job cũ treo nếu không hủy/báo lỗi, không phù hợp queue ưu tiên |

Với đơn hàng, xuất báo cáo cần truy vết hoặc xử lý dữ liệu nghiệp vụ, **`AbortPolicy` thường là lựa chọn an toàn để không che giấu việc bị từ chối**. Với metrics có thể mất, chính sách bỏ tác vụ có thể có lý nhưng phải đo số lần rơi và thỏa thuận rõ với consumer. Không nên chọn `CallerRunsPolicy` theo thói quen mà không tính ảnh hưởng đến thời gian phản hồi và thread gọi.

Chính sách tùy biến cũng có thể được triển khai qua `RejectedExecutionHandler`. Nếu muốn caller nhận được lỗi, custom handler phải **ném `RejectedExecutionException`**; handler chỉ log rồi return sẽ có nguy cơ biến lỗi thành thành công giả tại ranh giới `execute`.

~~~java
executor.setRejectedExecutionHandler((runnable, pool) -> {
    throw new java.util.concurrent.RejectedExecutionException(
            "Executor unavailable: saturated or shutting down");
});
~~~

Đoạn trên chỉ minh họa setter trong code đang cấu hình một `ThreadPoolTaskExecutor executor`, không phải một chương trình Java độc lập. Trong ứng dụng thật cần ghi chỉ số rejection nhưng tránh log dữ liệu nhạy cảm từ task và tránh xử lý I/O nặng ngay trong rejection handler.

## 6. Ví dụ Java 17+: tạo rejection một cách xác định

Ví dụ sau dùng **JDK `ThreadPoolExecutor` thuần** để học chính xác thời điểm queue đầy. Chúng ta chủ động giữ task đầu trong worker, xếp task thứ hai vào queue capacity 1, rồi gửi task thứ ba. Khi chưa thả latch, worker và queue đều không còn chỗ.

**Tệp `JdkRejectionDemo.java`**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class JdkRejectionDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger completed = new AtomicInteger();

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());

        try {
            pool.execute(() -> {
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                    completed.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });

            if (!firstStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("First task did not start");
            }

            pool.execute(() -> {
                completed.incrementAndGet();
                done.countDown();
            });

            boolean rejected = false;
            try {
                pool.execute(() -> completed.incrementAndGet());
            } catch (RejectedExecutionException ex) {
                rejected = true;
            }

            System.out.println("rejected=" + rejected);
            if (!rejected) {
                throw new AssertionError("Expected a rejection");
            }

            releaseFirst.countDown();
            if (!done.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Tasks did not finish");
            }
            System.out.println("completed=" + completed.get());
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
        }
    }
}
~~~

Biên dịch/chạy (JDK 17 trở lên):

~~~bash
javac JdkRejectionDemo.java
java JdkRejectionDemo
~~~

Kết quả chuẩn của lần chạy thành công:

~~~text
rejected=true
completed=2
~~~

**Vì sao kết quả xác định?** `firstStarted.await` chỉ tiếp tục khi task thứ nhất đang nắm worker; `releaseFirst` giữ worker đó chờ, nên task thứ hai nằm trong queue và task thứ ba không có chỗ. Sau khi bắt rejection, `releaseFirst.countDown()` cho phép xử lý nốt hai tác vụ đã nhận; `done.await()` đảm bảo đọc kết quả sau khi cả hai hoàn thành. Hai timeout 5 giây là chốt tránh treo vô hạn trên môi trường lỗi, không phải công cụ tạo race.

Nếu dùng **Spring `ThreadPoolTaskExecutor`** với cùng các giới hạn và `AbortPolicy`, nguyên lý bão hòa vẫn vậy, nhưng exception tại lời gọi `execute` của Spring sẽ là `TaskRejectedException`.

## 7. Cấu hình Spring `ThreadPoolTaskExecutor` để rejection có nghĩa rõ ràng

Mẫu dưới đây dùng cấu hình hữu hạn và `AbortPolicy`. Các thông số `2/4/20` chỉ là ví dụ, không phải sizing được khuyến nghị cho mọi ứng dụng.

**Tệp `ExportExecutorConfig.java` trong dự án Spring có dependency `spring-context`:**

~~~java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExportExecutorConfig {

    @Bean(name = "exportExecutor")
    public ThreadPoolTaskExecutor exportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("export-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
~~~

Khi là Spring bean bình thường, container quản lý khởi tạo và lifecycle của executor. Nếu tự tạo `new ThreadPoolTaskExecutor()` ngoài container rồi trực tiếp sử dụng, phải gọi `initialize()` trước khi `execute` và quản lý shutdown thích hợp. Không đặt `setQueueCapacity` bằng một số rất lớn chỉ để che đi tín hiệu overload.

Một lớp service có thể để exception **đi tới điểm quyết định nghiệp vụ**, thay vì bắt và nuốt lỗi:

**Tệp `ExportSubmissionService.java`:**

~~~java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class ExportSubmissionService {
    private final TaskExecutor exportExecutor;

    public ExportSubmissionService(
            @Qualifier("exportExecutor") TaskExecutor exportExecutor) {
        this.exportExecutor = exportExecutor;
    }

    public void submit(String exportId) {
        exportExecutor.execute(
                () -> System.out.println("Processing export " + exportId));
    }
}
~~~

Đây là ví dụ về đường giao việc, trong đó `System.out.println` đại diện cho một thao tác đơn giản có thể nhìn thấy. Nó **không** tạo job bền vững, không có trạng thái để truy vấn và không xử lý xuất tệp thật. Phần lưu job, transaction, retry và xử lý kết quả cần bổ sung theo yêu cầu nghiệp vụ.

Lớp gọi `submit` phải quyết định ý nghĩa khi gặp `TaskRejectedException`. Ví dụ một API không thể nhận thêm job tại thời điểm quá tải có thể trả HTTP 503, hoặc HTTP 429 khi thực sự áp giới hạn theo người gọi. Có thể cung cấp `Retry-After` nếu có chính sách retry đáng tin. **HTTP 202 Accepted** chỉ nên được sử dụng với ý nghĩa mà ứng dụng thực sự bảo đảm: nếu hứa chắc việc sẽ được xử lý về sau, hãy ghi nhận yêu cầu vào nơi lưu bền vững trước khi xác nhận.

### Phải đặt `catch` tại đúng ranh giới

~~~java
try {
    exportSubmissionService.submit(exportId);
    // Chỉ báo đã giao thành công theo hợp đồng executor và policy hiện tại.
} catch (TaskRejectedException ex) {
    // Không trả thành công giả. Chuyển thành phản hồi quá tải,
    // hoặc lưu công việc vào kênh dự phòng đáng tin cậy.
}
~~~

Đoạn này là **mẫu xử lý minh họa**, cần đặt trong một method có `exportSubmissionService`, `exportId` và phản hồi nghiệp vụ thích hợp. Không chạy lại ngay trong vòng lặp vô hạn: trong lúc pool đang bão hòa, việc retry liên tục chỉ làm tăng tải.

## 8. `execute`, `submit`, `Future` và `@Async`: ba đường lỗi khác nhau

| Trường hợp | Lỗi xuất hiện ở đâu? | Caller nên làm gì? |
| --- | --- | --- |
| `TaskExecutor.execute` bị từ chối theo `AbortPolicy` | Tại thời điểm gọi `execute` bằng `TaskRejectedException` | Xử lý không nhận việc, trả trạng thái hoặc persist để retry |
| Task đã nhận nhưng `Runnable.run()` ném exception | Trên thread thực thi; với executor bất đồng bộ, không quay về `execute` thông thường | Quan sát log/task wrapper/status; bổ sung future nếu cần kết quả |
| `AsyncTaskExecutor.submit` nhận task, task chạy lỗi | `Future.get()` báo `ExecutionException` (cause là lỗi gốc) | Xử lý execution failure, phân biệt với rejection khi submit |
| `submit` không được executor nhận | Có thể ném `TaskRejectedException` **trước khi trả về `Future`** | Xử lý tại caller của `submit`; không chờ một future không tồn tại |
| `CompletableFuture` được tạo và tác vụ chạy lỗi | Future hoàn thành exceptionally; `join()` thường ném `CompletionException` | Quan sát `whenComplete/handle/exceptionally` hoặc `join/get` |
| `@Async` qua Spring proxy không gửi được task | Lỗi có thể phát sinh ở thread gọi khi proxy dispatch công việc | Bắt lỗi tại boundary gọi method; không dựa vào handler xử lý lỗi trong body |
| `@Async void` đã bắt đầu rồi ném lỗi | Đường `AsyncUncaughtExceptionHandler` cho exception không có Future | Cấu hình handler/ghi nhận job thất bại phù hợp |

`@Async` chỉ hoạt động theo proxy khi lời gọi thực sự đi **qua Spring proxy**; self-invocation trong cùng bean theo proxy mode mặc định thường không được intercept. Ngoài ra, nếu dùng `CallerRunsPolicy` khi bão hòa thì work có thể chạy trên chính thread gọi, dù đường code mang nhãn async. Vì vậy không được xây dựng hợp đồng nghiệp vụ chỉ dựa vào kỳ vọng “method này luôn chạy trên thread khác”.

**Cần đặc biệt tránh:** kết hợp `submit` với `DiscardPolicy` hoặc `DiscardOldestPolicy` cho công việc cần kết quả. Task/Future có thể bị bỏ mà không nhận completion/cancellation tương ứng, khiến chờ `Future.get()` mãi không xong. Nếu chủ động bỏ task, phải có cơ chế ghi nhận kết quả “dropped” hoặc hủy future một cách thích hợp.

## 9. Thiết kế hợp đồng từ góc nhìn bên gửi việc

Trước khi code, cần trả lời: **khi không nhận được việc, hệ thống phải bảo đảm điều gì cho người gọi?** Có thể dùng bảng quyết định:

| Loại công việc | Có thể mất không? | Hợp đồng đề xuất khi bị reject |
| --- | --- | --- |
| Cập nhật biểu đồ thống kê best-effort | Có, nếu đã được thống nhất | Đo rejected/dropped; có thể bỏ với cảnh báo vận hành |
| Render ảnh tạm có thể sinh lại | Có thể retry | Trả “bận”, retry có giới hạn hoặc lưu yêu cầu ở kênh khác |
| Email giao dịch, báo cáo người dùng đặt tạo | Thông thường không nên mất âm thầm | Ghi job bền vững; xử lý lại có idempotency và trạng thái tra cứu |
| Trừ tiền, thay đổi số dư hay tồn kho | Không được dựa vào RAM queue | Dùng transaction/điều kiện cập nhật ở database và cơ chế phát việc sau commit |
| Yêu cầu HTTP làm việc nền ngắn và không cần bền vững | Tùy SLA | Thông báo rõ nếu không nhận; không trả thành công giả |

### Một state machine tối thiểu cho job cần bảo đảm

Một hệ thống có lưu trữ job có thể có trạng thái:

~~~text
RECEIVED -> PENDING -> RUNNING -> SUCCEEDED
                     \-> FAILED_RETRYABLE -> PENDING
                     \-> FAILED_FINAL
~~~

Nếu từ chối trước khi lưu và trước khi nhận job, phản hồi phải nêu rõ **NOT_ACCEPTED/REJECTED** hoặc equivalent của API. Nếu ghi bền vững thành công nhưng executor đang đầy, job vẫn có thể ở **PENDING** để worker về sau lấy lại; đó là một kiến trúc khác với enqueue in-memory đơn thuần.

Với thay đổi database và phát tác vụ cần nhất quán, cân nhắc **transactional outbox**: ghi dữ liệu nghiệp vụ và thông điệp outbox trong cùng transaction; worker/dispatcher gửi về sau và chỉ đánh dấu hoàn tất khi có chứng cứ. Đây là một giải pháp phổ biến để giảm rủi ro mất việc giữa commit và submit. Handler vẫn có thể retry nên consumer phải xử lý idempotency theo business key/job ID.

**Đừng đồng nhất retry với an toàn:** nếu một job đã chạy một phần rồi crash, retry có thể lặp hành động bên ngoài. Thiết kế idempotency, deduplication và trạng thái bền vững quan trọng hơn một vòng lặp gọi lại `execute`.

## 10. Retry và backpressure: kiểm soát tải thay vì tạo bão retry

Khi chính sách báo rejection, có nhiều lựa chọn tùy mức quan trọng:

1. **Fail fast:** trả lỗi bận cho caller (ví dụ 503), kèm thông tin retry khi có cơ sở. Dễ bảo toàn tài nguyên và không che giấu giới hạn.
2. **Retry có hạn:** dùng số lần tối đa, deadline, backoff và jitter; chỉ retry khi hợp đồng cho phép và tác vụ có tính idempotent phù hợp. Tránh giữ request thread quá lâu trong lúc chờ.
3. **Durable queue / database job table:** xác nhận sau khi đã persist; một worker riêng rút backlog theo năng lực thực thi. Cần vận hành, monitoring và cơ chế xử lý dead-letter/failure.
4. **Caller-runs backpressure:** chấp nhận chuyển độ trễ sang caller; phù hợp một số batch producer nhưng không nên dùng tùy tiện cho request handler nhạy latency hoặc event-loop.
5. **Giới hạn ngay từ nguồn:** rate limiting, bounded input queue, semaphore/permit, kiểm soát concurrency của upstream, circuit breaker cho dependency. Tính được tốc độ cung cấp việc và tốc độ hoàn thành hữu ích hơn cố tăng số thread không giới hạn.

Ví dụ đánh giá năng lực sơ bộ: nếu có 4 worker và mỗi tác vụ trung bình giữ worker 200 ms, **mức hoàn thành lý thuyết** khoảng 20 tác vụ/giây khi mọi worker luôn hữu dụng, chưa tính overhead và phân phối đuôi độ trễ. Nếu bên gửi đẩy 100 tác vụ/giây liên tục, queue 20 sẽ đầy nhanh; tăng queue chỉ trì hoãn rejection trong khi latency tăng. Trong thực tế phải đo thời gian thực thi và phân phối p95/p99, không lấy phép tính đơn giản làm sizing cuối cùng.

Cần lưu ý `CallerRunsPolicy` khi executor shutdown: theo hợp đồng JDK, handler có thể **bỏ tác vụ mà không chạy**, do đó chính sách này không giải quyết bài toán dừng nhận việc một cách chắc chắn. Nếu yêu cầu luôn báo lỗi lúc shutdown, ưu tiên `AbortPolicy` hoặc custom handler ném lỗi rõ ràng.

## 11. Shutdown và cạnh tranh đúng lúc ngừng nhận việc

Tình huống khó không chỉ xuất hiện ở giờ cao điểm. Khi Spring ApplicationContext đóng, executor có thể không nhận thêm task dù request vẫn đang tới hoặc một component khác còn đang dispatch. Ngoài rejection, những task **đã được nhận nhưng còn nằm trong queue** có được chạy tiếp hay bị hủy phụ thuộc vào chính sách shutdown.

Cần phân biệt:

- **Không nhận task mới:** signal rejection nếu dùng chính sách throw.
- **Task đã queue nhưng chưa chạy:** có thể hoàn tất, bị hủy hoặc bị dừng tùy vòng đời; acceptance chưa phải completion.
- **Task đang chạy:** có thể bị interrupt hoặc được phép hoàn thành khi graceful shutdown, tùy cấu hình.
- **Job bền vững:** có thể hồi phục sau restart nếu trạng thái và giao thức re-drive được thiết kế đúng.

`ThreadPoolTaskExecutor` có các cấu hình về chờ task hoàn thành lúc shutdown và khoảng đợi terminate (ví dụ `setWaitForTasksToCompleteOnShutdown` và `setAwaitTerminationSeconds`). Cần đọc đúng phiên bản Spring và chọn theo ngân sách shutdown thực tế của hạ tầng. Không nên tự gọi `shutdownNow()` trên Spring-managed bean bất kỳ lúc nào chỉ để “giải phóng tài nguyên”.

### Đường ranh giới với `@Transactional`

Một tác vụ chạy trên worker thread không tự động thừa hưởng transaction database đang gắn với thread gửi. Nếu gửi việc trước khi transaction chính commit, worker có thể đọc dữ liệu chưa commit hoặc sự kiện nghiệp vụ cuối cùng bị rollback. Khi cần ngữ nghĩa “sau commit”, dùng after-commit integration hoặc outbox, tùy yêu cầu độ bền. Tránh dùng JVM executor làm nguồn sự thật cho giao dịch xuyên tiến trình.

## 12. Observability: biết vì sao và khi nào bị reject

Nếu chỉ log mỗi lần ném exception mà không đo tình trạng pool, rất khó quyết định nên giảm tải hay tăng năng lực. Một bộ quan sát có ích:

- **Rejection count/rate** theo executor, loại task, lý do bão hòa/shutdown nếu xác định được; tránh gắn label job ID gây nổ cardinality.
- **Pool size / active threads**, **queue size / capacity** và số job đang chờ. `ThreadPoolTaskExecutor` cung cấp `getPoolSize()`, `getActiveCount()` và `getQueueSize()` để quan sát.
- **Queue wait time**, thời gian chạy task, throughput, timeout, cancelled, retry và số job lỗi cuối cùng.
- **Thread pool/queue utilization** đi cùng p95/p99 response time của API, tỷ lệ 429/503 và độ trễ hệ thống downstream.
- **Shutdown/cancel events** để phân biệt rejection do tải cao với rejection do vòng đời.

Trong hệ dùng Micrometer/Actuator, có thể instrument executor và publish các metrics phù hợp; đừng giả định mọi cấu hình Spring đều tự xuất đủ mọi chỉ số. Nếu viết custom rejection counter, chú ý code có thể chạy trên caller thread: metric nên rẻ, không gây blocking kéo dài.

Một log lỗi có ích thường chứa tên executor, loại công việc, correlation ID hoặc job ID an toàn, lý do quan sát được, kích thước queue/active thread và quyết định fallback. Tránh log toàn bộ nội dung tác vụ hoặc thông tin cá nhân.

## 13. Kiểm thử rejection đúng cách

**Không dùng `Thread.sleep(100)` để “hy vọng pool đầy”.** Scheduler và máy CI có thể khác nhau. Hãy dùng latch như ví dụ JDK để kiểm soát chính xác lúc worker đang bận và queue đã chứa task.

Các kịch bản nên có:

| Kịch bản | Điều cần chứng minh |
| --- | --- |
| Pool cấu hình 1 worker, queue 1, giữ task đầu bằng latch rồi gửi task thứ ba | `AbortPolicy` báo rejection; trong Spring bắt `TaskRejectedException` |
| Pool còn capacity | Task được nhận; không kết luận task đã hoàn thành chỉ từ việc `execute` trả về |
| Pool đã shutdown | Gửi mới bị từ chối theo policy đã chọn; không có success giả |
| Task đã nhận rồi ném lỗi trong body | Không bị gắn nhầm nhãn “rejected”; quan sát bằng future/status/error handler |
| `CallerRunsPolicy` dưới tải | Task thứ ba chạy trên caller thread; latency tăng; không vượt ràng buộc capacity theo cách của pool |
| `DiscardPolicy`/`DiscardOldestPolicy` | Chứng minh đường mất tác vụ và đánh giá hợp đồng quan sát/cancel để không chờ future vô hạn |
| Nhiều request đồng thời | Không có vòng retry vô hạn, không mất đơn hàng im lặng, metric phản ánh rejection |
| Graceful shutdown và restart | Job bền vững không mất; worker xử lý lại được và idempotency vẫn đúng |

Đối với integration test của Spring, khởi tạo `ThreadPoolTaskExecutor` có `core=1, max=1, queue=1`, đặt `AbortPolicy`, rồi dùng cùng chiến lược latch. Nếu tự khởi tạo executor trong test, nhớ `initialize()` và đóng nó trong `finally`; nếu dùng bean do Spring quản lý, để container đảm nhiệm vòng đời. Trong test HTTP, assert đúng response/status và không đánh dấu “accepted” khi submit bị reject.

## 14. Những lỗi triển khai thường gặp

1. **Nuốt `TaskRejectedException` rồi trả HTTP 200/202 vô điều kiện.** Caller tưởng đã giao việc; phải làm rõ nghĩa của accepted và thông báo no-capacity.
2. **Mặc định queue không giới hạn, chờ overload tự biến mất.** Đây thường biến từ chối sớm thành tồn đọng tăng dần và có nguy cơ thiếu bộ nhớ.
3. **Tăng `maxPoolSize` nhưng giữ queue rất lớn.** Pool có thể vẫn dừng ở mức core vì task đi vào queue trước.
4. **Bắt lỗi trong `Runnable.run()` để xử lý rejection.** Lỗi từ chối xảy ra lúc gửi, trước khi Runnable có cơ hội chạy.
5. **Dùng `@Async void` rồi cho rằng `AsyncUncaughtExceptionHandler` bắt luôn mọi rejection khi dispatch.** Submission failure và body failure là hai đường khác nhau.
6. **Dùng `CallerRunsPolicy` trong request/event-loop mà không tính độ trễ.** Tải cao có thể khiến logic nặng chạy ngay trong thread của người gọi, phá kỳ vọng phản hồi nhanh.
7. **Dùng `DiscardPolicy` cho công việc có nghĩa vụ phải hoàn thành.** Việc mất task trở nên im lặng, khó khôi phục; có thể khiến future treo.
8. **Retry liên tục trong `catch`.** Khi pool đầy, retry nóng làm CPU/logging tăng và cản hệ thống hồi phục.
9. **Cho rằng có `Future` tức task nhất định đã được xử lý.** Future thể hiện pending completion, không tự bảo đảm durability hoặc completion nếu task bị bỏ qua.
10. **Dùng queue in-memory thay cho job store sau khi đã hứa với khách hàng.** Restart hoặc shutdown có thể làm mất việc chưa chạy.
11. **Không ghi nhận rejection metric.** Không phân biệt tải vượt thiết kế, shutdown hay lỗi cấu hình.
12. **Test bằng timing ngẫu nhiên.** Dùng latch/barrier để khóa interleaving và assert chính xác rejection.
13. **Cấu hình executor ở một bean nhưng `@Async` dùng executor khác.** Dùng qualifier `@Async("exportExecutor")` khi cần chọn cụ thể, và kiểm chứng đường gọi qua proxy.
14. **Tưởng transaction từ thread submit tự chảy sang thread worker.** Cần thiết kế đường lưu trạng thái, after-commit hoặc outbox phù hợp.

## 15. Tình huống áp dụng

**Tình huống A — tạo báo cáo nặng:** pool dành cho report có `core=2, max=4, queue=20`. Request số 27 đến khi cả worker và queue vẫn đang bận theo ngưỡng không được nhận. API không nên trả “báo cáo đang xử lý” khi không có job ID bền vững. Có thể trả 503 cho caller retry có giới hạn; tốt hơn, nếu phải đảm bảo xử lý, persist job trước và để worker xử lý backlog theo tốc độ của mình.

**Tình huống B — log/telemetry không quan trọng:** bỏ một số event khi quá tải có thể được phép, nhưng cần theo dõi drop rate và chấp nhận được mất dữ liệu. `DiscardPolicy` không phù hợp nếu dashboard hoặc hệ thống cảnh báo đang mặc định mọi event được thu đủ.

**Tình huống C — batch producer:** một producer chuyên dụng tạo task nhanh hơn pool hoàn tất. `CallerRunsPolicy` có thể giảm tốc producer vì khi pool bão hòa producer phải tự chạy task, nhưng cách đó đổi mô hình latency, thread context và xử lý shutdown. Chỉ dùng khi người sở hữu producer chấp nhận những thay đổi đó.

**Tình huống D — shutdown rolling deployment:** một instance bắt đầu ngừng nhận việc nhưng request vẫn đến. `TaskRejectedException` báo cần định tuyến sang instance khác hoặc trả unavailable; nếu job đã lưu trong database/message queue thì worker khác có thể tiếp tục. Chỉ một `ThreadPoolTaskExecutor` nội bộ không tạo được cơ chế chuyển giao giữa các replica.

## 16. Tự kiểm tra

1. `TaskExecutor.execute` trả bình thường có luôn đồng nghĩa “task chắc chắn hoàn thành”? Cấu hình nào có thể làm nó thậm chí không được đưa vào queue?
2. Với `corePoolSize=2, maxPoolSize=4, queueCapacity=3`, vì sao task thứ ba thường được đưa vào queue thay vì làm pool tăng ngay lên 3 worker?
3. Vì sao một Spring `ThreadPoolTaskExecutor` với `AbortPolicy` báo `TaskRejectedException`, trong khi JDK `ThreadPoolExecutor` trực tiếp thường báo `RejectedExecutionException`?
4. Vì sao `CallerRunsPolicy` có thể làm thời gian phản hồi HTTP tăng mạnh? Điều gì xảy ra khi pool đã shutdown?
5. Lỗi do `Runnable.run()` ném và lỗi `TaskRejectedException` khác nhau về thời điểm và đường quan sát thế nào?
6. Tại sao gửi việc trong transaction chưa commit có thể khiến worker đọc trạng thái sai?
7. Khi API trả 202 Accepted, cần thêm những thành phần nào để hứa job không bị mất qua restart?
8. Hãy thiết kế integration test không dùng `Thread.sleep()` để chứng minh queue 1 + worker 1 có thể từ chối task thứ ba.

## 17. Tài liệu tham khảo chính thức

- [Spring Framework — TaskExecutor API (6.1)](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/core/task/TaskExecutor.html)
- [Spring Framework — TaskRejectedException API (6.1)](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/core/task/TaskRejectedException.html)
- [Spring Framework — ThreadPoolTaskExecutor API (6.1)](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Java SE 17 — RejectedExecutionHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RejectedExecutionHandler.html)

**Nguyên tắc thực hành:** Một executor hữu hạn cần có **chính sách từ chối rõ ràng**, ranh giới xử lý rejection có thể quan sát được, và hợp đồng nói chính xác caller nhận lại điều gì. “Đã gửi vào pool” chỉ là bước đầu; độ bền, kết quả và retry thuộc trách nhiệm kiến trúc của ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-executor-008">Vai trò của bộ thực thi trong các thành phần Spring khác</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của bộ thực thi trong các thành phần Spring khác

## 1. Một TaskExecutor, nhiều điểm tích hợp

Trong Spring Framework, **TaskExecutor** là giao diện dùng để **giao một công việc cho cơ chế thực thi**. Nó mở rộng java.util.concurrent.Executor và cung cấp phương thức **execute(Runnable)**. Cùng một kiểu giao diện này có thể được dùng ở nhiều tầng: phương thức bất đồng bộ, xử lý HTTP, sự kiện nội bộ, message channel hay các tác vụ của Spring Batch.

Ý nghĩa của TaskExecutor trong từng thành phần **không hoàn toàn giống nhau**. Thành phần tích hợp quyết định **lúc nào** và **vì sao** gửi task, còn executor quyết định **tài nguyên thực thi, hàng đợi, cách cấp luồng và hành vi khi quá tải** theo cấu hình của nó.

~~~text
Nguồn phát sinh công việc                 Điểm giao cho TaskExecutor
────────────────────────                  ────────────────────────────
Lời gọi @Async qua Spring proxy   ──────> thực thi thân phương thức
Spring MVC controller -> Callable ──────> chạy Callable, trả kết quả qua async dispatch
@EventListener / multicaster      ──────> gọi listener trên worker thread
Spring Batch                       ──────> xử lý công việc của Step/Flow/partition
Spring Integration ExecutorChannel ──────> chuyển message đến handler trên worker
Mã nghiệp vụ gọi execute(...)     ──────> chạy Runnable trực tiếp

@Scheduled / TaskScheduler        ──────> quyết định thời điểm kích hoạt
                                         (có thể giao công việc tiếp cho TaskExecutor)
~~~

Mô hình này giúp phân biệt ba trách nhiệm: **bên tạo yêu cầu**, **bộ thực thi công việc** và **cơ chế quan sát kết quả/lỗi**. Nếu chỉ tăng số thread mà không hiểu bên nào gửi task và điều gì xảy ra khi task bị từ chối, ứng dụng có thể trễ, tốn bộ nhớ hoặc mất công việc.

## 2. Nền tảng API và lựa chọn triển khai

### TaskExecutor và AsyncTaskExecutor

- **TaskExecutor**: API lõi nhận Runnable bằng execute(Runnable), tương thích với Executor của JDK. Bản thân interface không hứa công việc chạy ở luồng khác. Ví dụ **SyncTaskExecutor** gọi công việc ngay trên luồng gửi.
- **AsyncTaskExecutor**: mở rộng TaskExecutor với các tiện ích gửi task và nhận Future (API cụ thể phụ thuộc phiên bản). Tích hợp **Spring MVC Callable** yêu cầu kiểu executor phù hợp với AsyncTaskExecutor.
- **ThreadPoolTaskExecutor**: triển khai thông dụng quản lý pool, queue, các ngưỡng core/max và vòng đời Spring; có thể dùng ở nhiều điểm tích hợp cần TaskExecutor/AsyncTaskExecutor.
- **SimpleAsyncTaskExecutor**: chạy tác vụ trên thread mới, không phải pool thread tái sử dụng; có thể dùng virtual threads khi chạy trên JDK hỗ trợ và cấu hình đúng.
- **ConcurrentTaskExecutor**: adapter cho một java.util.concurrent.Executor khác, hữu ích khi hệ thống đã có executor tự quản lý.

Ví dụ lời gọi trực tiếp, trong một Spring bean đã được inject TaskExecutor:

~~~java
public void submit(TaskExecutor executor) {
    executor.execute(() ->
            System.out.println("Running on " +
                    Thread.currentThread().getName()));
}
~~~

Đoạn code này là **minh họa phương thức**, chưa phải chương trình độc lập. Khi executor là pool bất đồng bộ, phương thức submit thường trả về trước khi Runnable hoàn tất. Nếu executor từ chối task vì đã shutdown hoặc hết capacity, bên gửi phải xử lý **TaskRejectedException/RejectedExecutionException** phù hợp; không mặc định task chắc chắn được nhận.

**Sự phân tách cần ghi nhớ:** API TaskExecutor không tự định nghĩa thứ tự xử lý, độ bền công việc, retry, giao dịch, truyền ngữ cảnh bảo mật hay lịch kích hoạt. Những bảo đảm ấy phải đến từ thiết kế thành phần sử dụng và cấu hình liên quan.

## 3. Bảng ánh xạ TaskExecutor vào các thành phần Spring

| Thành phần | Khi nào gửi việc? | Vai trò của executor | Điểm cần kiểm soát |
| --- | --- | --- | --- |
| **@Async** | Bean khác gọi method qua async proxy | Thực thi thân method trên worker | Proxy/self-invocation, Future, exceptions, queue |
| **Spring MVC Callable/WebAsyncTask** | Controller trả về một Callable | Chạy tác vụ blocking trên AsyncTaskExecutor | Timeout, async dispatch, pool, request context |
| **DeferredResult / CompletableFuture trong MVC** | Kết quả được hoàn thành bởi phía ứng dụng | Executor chỉ tham gia khi ứng dụng thực sự dùng nó; MVC không tự submit mọi DeferredResult | Ai làm việc, ai hoàn thành, timeout/lỗi |
| **Application events** | ApplicationEventPublisher phát sự kiện | @Async trên listener hoặc TaskExecutor của event multicaster | Thứ tự, exception, transaction/context |
| **Spring Batch** | Step/Flow/partition cần thực thi song song | Cấp worker cho chiến lược parallelism đã chọn | Reader/writer thread safety, transaction, restart |
| **Spring Integration** | Message đi vào ExecutorChannel | Chuyển việc dispatch message sang worker | Ranh giới transaction, ordering, lỗi/rejection |
| **Spring WebSocket/message broker** | Message tới các channel inbound/outbound | Một số channel sử dụng executor để dispatch | Kích thước queue, ordering, backpressure |
| **TaskScheduler / @Scheduled** | Đến thời điểm trigger | Vai trò chính thuộc scheduler, không tự động dùng pool @Async | Tác vụ dài, chồng lấp, thread scheduler |
| **Spring WebFlux** | Dòng reactive được subscribe/chuyển Scheduler | Reactor Scheduler là abstraction khác; executor có thể được điều chỉnh để dùng ở chỗ phù hợp | Không chặn event-loop, tách blocking work |

Không phải tất cả các thành phần này tự động chọn **cùng một bean** có tên taskExecutor. Quy tắc lựa chọn phụ thuộc framework/module, phiên bản và cấu hình. Có thể dùng một pool chung cho những workload tương đồng; với HTTP, batch và tác vụ nặng có độ ưu tiên khác nhau, nên cân nhắc phân chia để tránh chúng làm nghẽn lẫn nhau.

## 4. @Async: TaskExecutor ở phía sau một lời gọi method

**@Async** là cơ chế Spring tiện lợi nhất để gửi công việc sang executor mà người gọi không phải tự tạo Runnable. Khi được bật bằng **@EnableAsync**, lời gọi method hợp lệ đi qua Spring proxy; proxy giao phần thực thi cho executor. Có thể chỉ định pool bằng qualifier:

~~~java
@Async("reportExecutor")
public CompletableFuture<String> buildReport(long reportId) {
    return CompletableFuture.completedFuture("report-" + reportId);
}
~~~

Khi method trên được gọi **từ bean khác qua proxy**, caller nhận Future để chờ, kiểm tra lỗi hoặc ghép pipeline nếu cần. Nếu method chỉ trả về void, lỗi trong quá trình thực thi không được truyền đồng bộ lại cho caller; cần logging hoặc **AsyncUncaughtExceptionHandler**. Với Future/CompletableFuture, xử lý completion và lỗi ở ranh giới phù hợp.

Quan trọng:

1. **@EnableAsync** phải có hiệu lực trong ApplicationContext; annotation một mình chưa tạo ra async proxy.
2. **Self-invocation** (this.buildReport(...)) thường không đi qua proxy trong chế độ proxy mặc định; method chạy như lời gọi Java thông thường.
3. **@Async("reportExecutor")** chọn rõ bean tương ứng; tránh phụ thuộc quy tắc tự chọn khi ứng dụng có nhiều executor.
4. **@Async không tự làm an toàn luồng.** Nhiều worker có thể cùng gọi một singleton có mutable fields.
5. **Không dùng Future.get()/join() ngay sau lời gọi** nếu mục tiêu là không chặn luồng gọi; đôi khi chờ là đúng nhưng phải là quyết định có chủ đích.
6. Async task thường **không kế thừa transaction hay ThreadLocal** của caller một cách tự động.

Tự gọi executor.execute(...) phù hợp khi chỉ cần một Runnable. Dùng @Async phù hợp khi muốn thể hiện ranh giới async ở mức service method và tận dụng proxy, qualifier, các kiểu Future. Không nên lạm dụng @Async cho logic CPU rất ngắn nếu overhead và tác động lên luồng xử lý lớn hơn lợi ích.

## 5. Spring MVC: giải phóng request thread trong lúc chờ việc

Spring MVC xử lý request trên Servlet stack. Một controller đồng bộ giữ request thread cho đến khi xử lý xong. Với **Callable<T>**, MVC có cơ chế Servlet asynchronous processing:

1. Controller nhận request rồi trả về Callable.
2. MVC bắt đầu async request, gửi Callable vào **AsyncTaskExecutor** được cấu hình cho MVC.
3. Servlet request thread ban đầu được giải phóng trong khi response còn mở.
4. Worker chạy Callable và trả kết quả/exception.
5. Container thực hiện async dispatch để MVC xử lý kết quả và hoàn tất response.

Ví dụ controller, **cần spring-webmvc và web runtime nên không thuộc chương trình độc lập ở mục 10**:

~~~java
@RestController
class ReportController {
    @GetMapping("/reports/current")
    public Callable<String> current() {
        return () -> {
            // Có thể truy cập backend blocking từ MVC async worker.
            return "ready: " + Thread.currentThread().getName();
        };
    }
}
~~~

MVC cho phép cấu hình AsyncTaskExecutor qua **WebMvcConfigurer.configureAsyncSupport(AsyncSupportConfigurer)**, hoặc dùng tích hợp auto-configuration của Spring Boot. Khi tự cấu hình Spring Boot 3.3, một điểm đặc biệt là bean **applicationTaskExecutor** cần tương thích AsyncTaskExecutor để MVC dùng trong cơ chế chọn mặc định. Một custom Executor bất kỳ chỉ tên taskExecutor có thể được @Async chọn nhưng **không bảo đảm MVC chọn cùng bean đó**.

**Callable khác DeferredResult.** Với DeferredResult, controller trả về một holder chưa có kết quả; chính ứng dụng, một callback I/O hoặc message handler khác phải gọi setResult/setErrorResult. MVC **không tự động gửi phần tính toán của mọi DeferredResult sang executor**. Với controller trả về CompletableFuture, nơi sản xuất future quyết định phần công việc chạy ở đâu. Vì thế việc controller "trả về kiểu async" không tự chứng minh một TaskExecutor cụ thể đã được dùng.

Bất đồng bộ MVC không có nghĩa ứng dụng xử lý vô hạn request. Cần đặt timeout, quan sát số request đang mở, không dùng pool vô hạn và quản lý kết nối/downstream. Việc chuyển blocking I/O sang worker giúp giải phóng Servlet thread chờ nhưng **không làm blocking I/O trở thành non-blocking**.

## 6. Application Events: listener chạy đồng bộ hoặc được đẩy sang executor

Với **ApplicationEventPublisher.publishEvent(...)**, Spring thường chuyển sự kiện tới listener **đồng bộ trên thread của publisher** theo mặc định. Spring có hai con đường chủ yếu để giao việc cho executor.

### Cách A — chỉ một listener cần chạy bất đồng bộ

~~~java
@Component
class NotificationListener {
    @Async("notificationExecutor")
    @EventListener
    public void onOrderCreated(OrderCreated event) {
        // Xử lý trên notificationExecutor.
    }
}
~~~

Đây là mã minh họa, giả định đã định nghĩa **OrderCreated**, bean notificationExecutor, @EnableAsync và các import cần thiết. Ứng dụng chỉ cần async ở listener này thì annotation thể hiện ý định khá rõ. **publishEvent() không chờ việc xử lý thực tế kết thúc** khi listener đã được dispatch thành công.

### Cách B — cấu hình multicaster để điều phối listener bằng TaskExecutor

Một bean tên **applicationEventMulticaster** có thể dùng SimpleApplicationEventMulticaster và gọi setTaskExecutor(...) để chuyển việc dispatch sang pool phù hợp. Ví dụ nằm trong class @Configuration (cần import các kiểu được dùng):

~~~java
@Bean(name = "applicationEventMulticaster")
ApplicationEventMulticaster eventMulticaster(
        @Qualifier("eventExecutor") TaskExecutor eventExecutor) {
    SimpleApplicationEventMulticaster multicaster =
            new SimpleApplicationEventMulticaster();
    multicaster.setTaskExecutor(eventExecutor);
    return multicaster;
}
~~~

Đây là cấu hình minh họa riêng, **không có trong bản demo độc lập**. Khi chọn hướng này, event dispatch có thể async cho các listener **hỗ trợ async execution**. Không suy ra mọi listener đều bị chuyển thread: ví dụ transactional listener có những ràng buộc phải giữ đồng bộ khi gắn với transaction. Không cần gắn cả @Async lẫn multicaster async nếu chúng tạo thêm tầng dispatch khó kiểm soát.

**Ranh giới transaction:** một listener chạy ở worker khác không có sẵn transaction đang mở hay các ThreadLocal của publisher. Nếu muốn xử lý sau commit, hãy cân nhắc **@TransactionalEventListener(phase = AFTER_COMMIT)**, sau đó thiết kế riêng ranh giới công việc nền và tính bền vững cần thiết. Event trong ApplicationContext là cơ chế nội bộ, **không phải message broker bền vững**; process crash sau khi trả lời publisher có thể làm mất task chỉ đang nằm trong memory.

**Xử lý lỗi:** exception của async listener không tự ném ngược về publisher. Cần logging, error handler, retry có kiểm soát hoặc cơ chế lưu trữ công việc nếu sự kiện có giá trị nghiệp vụ bắt buộc.

## 7. Spring Batch: cung cấp worker cho xử lý song song

Spring Batch sử dụng TaskExecutor như một **thành phần hạ tầng** khi cấu hình một chiến lược parallelism. Những kịch bản phổ biến:

- **Multi-threaded Step:** cấu hình taskExecutor trên Step để thực hiện đồng thời phần xử lý mà phiên bản Spring Batch hỗ trợ. Các reader/processor/writer và trạng thái chia sẻ phải được đánh giá thread-safety; hành vi chunk/transaction chi tiết phụ thuộc phiên bản.
- **Parallel Steps:** mỗi flow có thể chạy đồng thời khi cấu hình split flow với executor.
- **Partitioning:** chia workload thành nhiều StepExecution; một partition handler cục bộ có thể dùng TaskExecutor để thực thi các partition. Việc chọn grid size, tài nguyên và idempotency vẫn thuộc ứng dụng.

Ví dụ **phác thảo cấu hình Spring Batch 5.x**, cần thêm spring-batch-core, JobRepository, transaction manager, ItemReader/Processor/Writer nên không phải file Java độc lập:

~~~java
Step step = new StepBuilder("importStep", jobRepository)
        .<InputRow, OutputRow>chunk(100, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .taskExecutor(batchExecutor)
        .build();
~~~

**ThreadPoolTaskExecutor không làm cho reader/writer tự an toàn khi được truy cập đồng thời.** Nếu framework/version cho các thành phần chạy trên nhiều thread, cần xác nhận compatibility, kiểm tra thứ tự đầu ra, commit boundaries, restart/retry và vấn đề duplicate. Trong Batch, tăng concurrency có thể bị chặn bởi DataSource connection pool, I/O throughput hoặc lock ở database.

Phân biệt **TaskExecutor trong một JVM** với phân phối công việc qua nhiều process/worker: muốn phân phối từ xa phải dùng cơ chế messaging, partitioning hoặc remote execution tương ứng. Một ThreadPoolTaskExecutor đơn thuần không tạo được distributed job queue.

## 8. Spring Integration và các hệ thống messaging

Spring Integration cung cấp **ExecutorChannel** để điều phối message sang subscriber/handler bằng TaskExecutor. Ví dụ sử dụng spring-integration-core, là đoạn minh họa cần khai báo MessageChannel và các dependency:

~~~java
@Bean
MessageChannel processingChannel(
        @Qualifier("integrationExecutor") TaskExecutor executor) {
    return new ExecutorChannel(executor);
}
~~~

Khi message được gửi vào channel, việc gọi handler thường chuyển sang worker. So với **DirectChannel**, ExecutorChannel làm **đứt ranh giới thread của sender**, do đó không thể giả định handler dùng chung transaction/ThreadLocal với sender. Thao tác send thường có thể return trước khi handler chạy; tuy nhiên nếu executor dùng **CallerRunsPolicy** hay một chính sách backpressure đặc biệt, sender **có thể bị block hoặc tự chạy task**, nên không coi "channel async" là một bảo đảm tuyệt đối.

Khi nhiều message được xử lý đồng thời, **thứ tự hoàn tất** có thể khác thứ tự gửi. Nếu cần bảo đảm ordering theo key, idempotency hoặc retry, phải chọn message pattern và cơ chế tương ứng.

Các thành phần như Spring WebSocket message channels, JMS listener containers hay AMQP listener containers có thể dùng executor để quản lý thread hoặc dispatch tùy implementation. Nhưng nhiều listener container cũng có cấu hình **concurrency riêng** và lifecycle độc lập; **không được suy ra cứ định nghĩa một TaskExecutor trong context là tất cả listener tự chuyển sang pool ấy**.

Với hệ thống cần lưu message khi application restart, phân phối nhiều node hoặc bảo đảm delivery, một executor chạy trong process **không thay thế** queue/broker bền vững (Kafka, RabbitMQ, JMS broker, v.v.).

## 9. Quan hệ với TaskScheduler, @Scheduled và Reactor Scheduler

**TaskScheduler** giải quyết **thời điểm kích hoạt**, còn TaskExecutor giải quyết **cách thực thi công việc được giao**. Dù có class Spring thực thi nhiều interface và có thể dùng thread pool ở cả hai vai trò, hai abstraction vẫn tách biệt.

~~~text
@Scheduled mỗi 30 giây
       |
       v
TaskScheduler kích hoạt method ngắn
       |
       v
method gọi @Async qua một Spring bean khác
       |
       v
TaskExecutor nhận công việc nặng, có queue/capacity riêng
~~~

Method @Scheduled có thể chạy trực tiếp trên thread scheduler nếu nó không gửi việc sang nơi khác. Nếu method chỉ submit việc async rồi return, **fixedDelay** đo từ lúc method được scheduler gọi kết thúc, **không phải từ lúc async task hoàn tất**. Vì thế có thể có nhiều việc nền chồng lấp dù lịch trông giống chạy tuần tự. Cần thêm quy tắc chống trùng/chồng lấp nếu nghiệp vụ yêu cầu.

**Reactor Scheduler trong WebFlux** cũng không phải TaskExecutor. Reactor dùng Scheduler để quyết định execution context của reactive signal/operators. Có thể điều chỉnh để một reactive chain dùng executor thích hợp trong những trường hợp cụ thể, nhưng không nên coi việc thêm @Async vào service là giải pháp chung cho blocking code trong reactive pipeline. Cần đưa blocking work ra khỏi event-loop, dùng scheduler phù hợp và quản lý giới hạn concurrency.

## 10. Demo độc lập: một executor phục vụ execute(), @Async và @EventListener

Demo này dùng **Java 17+, Spring Framework 6.1.14, Gradle 8.5+**. Nó chứng minh bằng một ứng dụng command-line rằng ba đường vào khác nhau có thể gửi việc tới **cùng một ThreadPoolTaskExecutor**. Không cần Spring Boot, web server, Spring Batch hay Spring Integration để chạy.

Cấu trúc:

~~~text
executor-integrations-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/ExecutorIntegrationsDemo.java
~~~

Tệp **settings.gradle**:

~~~groovy
rootProject.name = 'executor-integrations-demo'
~~~

Tệp **build.gradle**:

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
    mainClass = 'demo.ExecutorIntegrationsDemo'
}
~~~

Tệp **src/main/java/demo/ExecutorIntegrationsDemo.java**:

~~~java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class ExecutorIntegrationsDemo {

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(DemoConfig.class)) {

            var executor = context.getBean(
                    "workerExecutor", ThreadPoolTaskExecutor.class);

            // Đường 1: submit Runnable trực tiếp qua TaskExecutor.
            var directDone = new CountDownLatch(1);
            executor.execute(directDone::countDown);
            System.out.println("execute completed: " +
                    directDone.await(5, TimeUnit.SECONDS));

            // Đường 2: @Async qua Spring proxy của một bean khác.
            var service = context.getBean(ReportService.class);
            var report = service.createReport(7)
                    .get(5, TimeUnit.SECONDS);
            System.out.println("async result: " + report.name());
            System.out.println("async pool: " +
                    report.threadName().startsWith("worker-"));

            // Đường 3: ApplicationEventPublisher + @Async listener.
            var listener = context.getBean(ReportListener.class);
            context.publishEvent(new ReportRequested(8));
            System.out.println("event completed: " +
                    listener.await(5, TimeUnit.SECONDS));
            System.out.println("event pool: " +
                    listener.wasOnWorkerThread());
        }
    }

    @Configuration
    @EnableAsync
    public static class DemoConfig {
        @Bean(name = "workerExecutor")
        public ThreadPoolTaskExecutor workerExecutor() {
            var executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(10);
            executor.setThreadNamePrefix("worker-");
            return executor;
        }

        @Bean
        public ReportService reportService() {
            return new ReportService();
        }

        @Bean
        public ReportListener reportListener() {
            return new ReportListener();
        }
    }

    public record ReportRequested(int id) { }

    public record ReportResult(String name, String threadName) { }

    public static class ReportService {
        @Async("workerExecutor")
        public CompletableFuture<ReportResult> createReport(int id) {
            var result = new ReportResult(
                    "report-" + id,
                    Thread.currentThread().getName());
            return CompletableFuture.completedFuture(result);
        }
    }

    public static class ReportListener {
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile boolean onWorkerThread;

        @EventListener
        @Async("workerExecutor")
        public void handle(ReportRequested event) {
            onWorkerThread =
                    Thread.currentThread().getName().startsWith("worker-");
            completed.countDown();
        }

        public boolean await(long timeout, TimeUnit unit)
                throws InterruptedException {
            return completed.await(timeout, unit);
        }

        public boolean wasOnWorkerThread() {
            return onWorkerThread;
        }
    }
}
~~~

Chạy từ thư mục **executor-integrations-demo** bằng Gradle và JDK đã cài:

~~~bash
gradle run
~~~

Nếu dự án có wrapper, dùng **./gradlew run** (Linux/macOS) hoặc **gradlew.bat run** (Windows). Dưới điều kiện bình thường, khi cả ba task hoàn tất trước timeout 5 giây, phần output do chương trình in **đúng năm dòng**:

~~~text
execute completed: true
async result: report-7
async pool: true
event completed: true
event pool: true
~~~

Các log khởi động của Spring/Gradle có thể xuất hiện thêm; tên thread thật có hậu tố biến đổi (worker-1, worker-2, ...), vì vậy ví dụ in boolean kiểm tra prefix, không dựa vào thứ tự/tên thread bất biến. Nếu môi trường quá tải hoặc timeout, các giá trị/ngoại lệ có thể khác; đây là điều kiện vận hành cần xử lý chứ không phải chứng cứ executor luôn bảo đảm task chạy thành công.

Phân tích:

1. **execute(...)** gọi trực tiếp Runnable trên worker được ThreadPoolTaskExecutor quản lý.
2. **@Async("workerExecutor")** khiến lời gọi ReportService.createReport() qua proxy được gửi tới cùng pool; Future cho phép quan sát kết quả.
3. **@EventListener + @Async("workerExecutor")** chuyển phần xử lý sự kiện sang cùng pool trong khi multicaster mặc định vẫn giao lời gọi cho listener theo cơ chế publish của Spring.
4. CountDownLatch giúp main thread chờ kiểm tra kết quả đã hoàn thành và tạo quan hệ happens-before thích hợp cho dữ liệu được worker cập nhật. Trong ứng dụng production, cần thêm cơ chế timeout/rejection/error handling phù hợp.
5. Demo **không** chứng minh MVC, Spring Batch, Spring Integration tự dùng bean này. Những thành phần đó cần dependency và cấu hình riêng, như các mục trên đã giải thích.

## 11. Cấu hình và phân chia executor trong Spring Boot

Với **Spring Boot 3.3**, khi phù hợp điều kiện auto-configuration và chưa có custom Executor làm thay đổi lựa chọn, Boot thường tạo AsyncTaskExecutor cho các tích hợp, thông thường là **ThreadPoolTaskExecutor** nếu không bật virtual threads. Một số tích hợp như Spring MVC/WebFlux có quy ước tên bean **applicationTaskExecutor** và yêu cầu kiểu **AsyncTaskExecutor**. Quy tắc chọn với @EnableAsync và các thành phần khác có thể thay đổi khi có nhiều bean cùng kiểu.

**Nguyên tắc:** khi ứng dụng có nhiều pool, chọn pool **tường minh** ở mỗi điểm cần thiết. Không dựa vào việc bean đầu tiên có chữ "executor" trong tên sẽ được mọi thành phần dùng.

Với auto-configured ThreadPoolTaskExecutor/TaskScheduler, có thể cấu hình từ application.yml:

~~~yaml
spring:
  task:
    execution:
      thread-name-prefix: "app-work-"
      pool:
        core-size: 4
        max-size: 8
        queue-capacity: 100
    scheduling:
      thread-name-prefix: "scheduled-"
      pool:
        size: 2
~~~

Đây là mẫu cấu hình **của Spring Boot**, không có tác dụng lên project thuần Spring Framework ở mục 10. Những property này cũng **không tự ghi đè** thông số của một ThreadPoolTaskExecutor do bạn tự new và cấu hình thủ công; muốn dùng chung cách dựng, xem các builder mà Boot cung cấp.

Một cách tự tạo pool (ví dụ trong class @Configuration):

~~~java
@Bean("ioExecutor")
ThreadPoolTaskExecutor ioExecutor() {
    ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(4);
    pool.setMaxPoolSize(8);
    pool.setQueueCapacity(100);
    pool.setThreadNamePrefix("io-");
    return pool;
}
~~~

Đoạn cấu hình chỉ minh họa; cần import ThreadPoolTaskExecutor, @Bean và áp dụng trong ApplicationContext. Pool này không tự thay thế executor cho MVC, Batch, Integration hoặc event multicaster nếu những thành phần ấy chưa được cấu hình dùng nó.

**Cân nhắc tách pool theo workload:** ví dụ pool nhỏ dành cho điều phối ứng dụng, pool hạn chế cho I/O nặng, executor khác cho xử lý CPU và scheduler riêng cho lịch. Chia quá nhiều pool cũng tốn tài nguyên; quyết định dựa trên lượng task đến, thời gian chạy, yêu cầu ưu tiên và số CPU/connection thực có.

## 12. Rủi ro xuyên suốt các tích hợp và cách vận hành

### 12.1. Queue, capacity và rejection

Với ThreadPoolTaskExecutor, cần hiểu tương tác giữa **corePoolSize, maxPoolSize, queueCapacity**. Trong mô hình queue có dung lượng, pool thường tăng đến core threads, sau đó enqueue và chỉ tăng trên core khi queue đầy. Vì vậy maxPoolSize cao không có nghĩa ứng dụng sẽ lập tức tạo tương ứng số thread.

Khi tốc độ sinh task lớn hơn throughput xử lý, queue có thể tăng, độ trễ lớn dần và cuối cùng executor từ chối công việc. Chính sách như CallerRunsPolicy có thể buộc caller xử lý task, giúp làm chậm phía gửi nhưng làm thay đổi tính chất bất đồng bộ của lời gọi. Với request HTTP, message channel hay event publication, cần xác định cụ thể phản ứng khi bị từ chối: trả mã lỗi, retry có giới hạn, backpressure hoặc đưa công việc vào durable queue.

### 12.2. Context và transaction

**ThreadLocal** thông thường không tự chuyển sang worker. Ví dụ MDC cho logging, SecurityContext và request attributes có thể thiếu hoặc không đúng ở thread mới. Spring có **TaskDecorator** giúp bao bọc Runnable để truyền các context phù hợp; Spring Security còn có các wrapper như **DelegatingSecurityContextAsyncTaskExecutor**. Truyền context cần đặt đúng thời điểm capture/restore/cleanup, tránh rò rỉ ngữ cảnh giữa task trên pool tái sử dụng.

**Transaction** của caller thường gắn với thread hiện tại; chuyển việc sang executor không có nghĩa nó tiếp tục dùng cùng transaction. Với Batch, Integration, Application Events và MVC Callable, phải quyết định rõ transaction bao quanh đơn vị công việc nào. Không dùng synchronized để thay transaction database, cũng không mặc định @Transactional trên caller phủ sang thread async.

### 12.3. An toàn luồng, hủy và shutdown

Một singleton Spring service có thể được gọi đồng thời bởi MVC, @Async, scheduled callback và message handlers. **TaskExecutor không làm các field của singleton tự an toàn luồng**. Ưu tiên stateless service; khi cần shared mutable state thì dùng immutable snapshots, atomic operations, concurrent collection hoặc locking thích hợp.

Khi ứng dụng shutdown, cần xử lý task đang chờ, task đang chạy, interrupt, retry và dữ liệu cần khôi phục. Hủy Future là một tín hiệu/cơ chế phối hợp, không bảo đảm mọi I/O dừng ngay. Vòng đời của pool quản lý bởi Spring phụ thuộc cách bean được tạo và cấu hình shutdown; không nên tự new pool trong method rồi quên đóng.

### 12.4. Đo đạc

Nên quan sát ít nhất: **active threads, pool size, queue depth, submitted/completed/rejected tasks, thời gian chờ trong queue, thời gian xử lý, timeout và lỗi**. Đo riêng từng executor khi có nhiều pool để phân biệt bottleneck từ MVC, event hay batch. Thread count lớn nhưng throughput không tăng có thể do I/O, pool kết nối, lock hoặc downstream, không phải lỗi cấu hình max thread đơn thuần.

## 13. Các sai lầm thường gặp

| Hiện tượng | Suy luận sai | Cách kiểm tra và sửa |
| --- | --- | --- |
| Có một bean TaskExecutor nhưng MVC Callable không chạy ở pool mong muốn | Mọi thành phần tự lấy bean này | Kiểm tra AsyncTaskExecutor, applicationTaskExecutor và WebMvcConfigurer |
| @Async không tạo luồng khác | Cứ gắn annotation là chạy nền | Kiểm tra @EnableAsync và lời gọi có qua Spring proxy |
| @Scheduled chậm làm nghẽn nhiều lịch | TaskExecutor cho @Async luôn xử lý scheduled method | Kiểm tra TaskScheduler, số scheduler threads và thời gian callback |
| Listener nhận event rồi handler vẫn chạy trên thread publish | @EventListener mặc định luôn async | Cần @Async trên listener hoặc cấu hình multicaster thích hợp |
| Gửi vào ExecutorChannel nhưng mất transaction cũ | Chuyển message không đổi ranh giới thread | Thiết kế transaction ở handler/worker và xử lý lỗi độc lập |
| Batch xử lý trùng/lỗi khi chạy song song | Executor tự làm reader/writer an toàn | Kiểm tra idempotency, state, thread-safety, restart, database |
| Thêm maxPoolSize nhưng latency vẫn tăng | Executor luôn dùng max thread ngay | Kiểm tra core, queue, thời gian task, saturation |
| Cùng một bean chạy tốt khi đơn luồng nhưng lỗi lúc tải cao | Singleton tự có synchronization | Kiểm tra shared mutable state, race, atomicity và contention |

## 14. Câu hỏi tự kiểm tra

1. Một service gắn @Async chạy bằng executor nào? **Theo cấu hình async, qualifier trên @Async và quy tắc chọn bean của phiên bản đang dùng; đặt @Async("tênBean") nếu cần kiểm soát rõ.**
2. MVC controller trả về Callable có tự dùng ThreadPoolTaskExecutor của một method @Async bất kỳ không? **Không chắc.** MVC dùng AsyncTaskExecutor theo cấu hình MVC/Boot riêng.
3. MVC controller trả về DeferredResult có tự được MVC submit phần nghiệp vụ sang worker không? **Không**; ứng dụng chịu trách nhiệm hoàn thành DeferredResult.
4. Một event listener mặc định có async không? **Không**; mặc định listener thường chạy đồng bộ. Có thể dùng @Async hoặc cấu hình multicaster.
5. Tăng concurrency của Spring Batch có bảo đảm xử lý nhanh và đúng hơn không? **Không**; còn phụ thuộc thread-safety, I/O, database, transaction và khả năng restart.
6. ExecutorChannel có tự duy trì transaction của sender sang handler không? **Không**, vì handler có thể chạy ở worker khác.
7. @Scheduled gọi worker @Async mất 30 giây rồi return ngay: lần fixedDelay sau chờ worker xong chứ? **Không**; fixedDelay gắn với thời điểm kết thúc method được scheduler kích hoạt.
8. TaskExecutor có phải durable queue cho công việc quan trọng qua restart không? **Không**; cần cơ chế lưu và khôi phục công việc riêng.

**Điểm cần nhớ:** TaskExecutor là cơ chế dùng chung để **cấp tài nguyên thực thi**, nhưng mỗi Spring component xác định thời điểm gửi task, cách chờ kết quả và ranh giới lỗi/transaction khác nhau. Nắm rõ ranh giới đó giúp chọn đúng pool, không nhầm async với scheduling, và tránh mất kiểm soát khi ứng dụng chịu tải.

### Tài liệu chính thức

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — Async Requests in Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)
- [Spring Framework — ApplicationContext Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html)
- [Spring Boot 3.3 — Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.3/reference/features/task-execution-and-scheduling.html)
- [Spring Batch — Scaling and Parallel Processing](https://docs.spring.io/spring-batch/reference/scalability.html)
- [Spring Integration — Message Channel Implementations](https://docs.spring.io/spring-integration/reference/channel/implementations.html)


</details>

- [Quay lại đầu trang](#back-to-top)
