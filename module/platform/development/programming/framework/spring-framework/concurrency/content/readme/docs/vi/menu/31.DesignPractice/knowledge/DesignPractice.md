<a id="back-to-top"></a>

# Thiết kế và thực hành

## Menu
- [Chọn xử lý đồng bộ, @Async hoặc lưu công việc bền vững theo yêu cầu](#design-practice-001)
- [Thiết kế đầu vào, kết quả và trạng thái công việc độc lập với request](#design-practice-002)
- [Thực hành nhóm luồng hữu hạn cho tác vụ tạo báo cáo](#design-practice-003)
- [Thực hành kết hợp hai lời gọi độc lập bằng CompletableFuture](#design-practice-004)
- [Thực hành @Async có lỗi, timeout và ngữ cảnh nhật ký](#design-practice-005)
- [Thực hành lịch cập nhật dữ liệu với fixedDelay rồi cron](#design-practice-006)
- [Tái hiện tự gọi @Async, cạn nhóm luồng và mất ngữ cảnh](#design-practice-007)
- [Đo tải và điều chỉnh đồng thời với giới hạn tài nguyên phụ thuộc](#design-practice-008)
- [Danh sách lỗi thường gặp và tiêu chí tự đánh giá giải pháp](#design-practice-009)

## <a id="design-practice-001">Chọn xử lý đồng bộ, @Async hoặc lưu công việc bền vững theo yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn xử lý đồng bộ, `@Async` hoặc lưu công việc bền vững theo yêu cầu

Trong một ứng dụng Spring, cùng một công việc — ví dụ gửi email xác nhận đơn hàng, tạo báo cáo, xử lý ảnh, gọi API đối tác — có thể được thực hiện **ngay trong luồng yêu cầu**, đưa sang **thread khác bằng `@Async`**, hoặc **ghi thành job/message bền vững để worker xử lý sau**. Quyết định không chỉ dựa vào “công việc mất bao lâu”, mà còn dựa vào **khi nào được phép trả lời thành công, có chấp nhận mất việc khi ứng dụng dừng, cần bao nhiêu lần thử, có phải chống thực thi trùng và cần theo dõi trạng thái hay không**.

Trong bài này, “đồng bộ” nghĩa là **đường gọi phải chờ phần công việc cần thiết hoàn tất để trả kết quả**; “`@Async`” nghĩa là **chạy method bằng Spring executor, không tự có hàng đợi bền vững**; “bền vững” nghĩa là **ý định thực hiện công việc được commit vào nơi lưu trữ có khả năng phục hồi** (cơ sở dữ liệu, broker/queue được cấu hình bền vững hoặc hệ thống quản lý job), rồi mới xác nhận đã nhận việc.

## 1. Xác định yêu cầu trước khi chọn công nghệ

Hãy trả lời lần lượt:

1. **Hợp đồng kết quả:** Client cần kết quả cuối ngay trong response (`200 OK`/`201 Created`), hay chỉ cần biết hệ thống đã nhận yêu cầu (`202 Accepted` kèm ID trạng thái)?
2. **Tính bắt buộc:** Nếu JVM crash ngay sau khi API trả lời, công việc được phép mất hay phải có cơ chế chạy lại?
3. **Độ dài và tải:** Thời gian P95/P99, số request/giây, công việc chủ yếu CPU, I/O hay chờ người dùng/hệ thống ngoài? Có giới hạn tài nguyên và thời gian chờ?
4. **Ngữ nghĩa lỗi:** Thất bại có cần hiển thị ngay cho người gọi, tự retry, đưa vào danh sách thất bại cuối cùng (dead-letter/dead job), hay cho người vận hành can thiệp?
5. **Trùng lặp và thứ tự:** Có chấp nhận một công việc chạy lại sau timeout/crash? Có cần bảo đảm một lần thay đổi trạng thái nghiệp vụ, thứ tự theo thực thể hoặc loại trừ thực thi đồng thời?
6. **Giao dịch:** Công việc chỉ được phép bắt đầu **sau khi dữ liệu nghiệp vụ đã commit**? Việc ghi yêu cầu công việc và ghi dữ liệu nghiệp vụ có cần cùng một database transaction?
7. **Vận hành:** Một hay nhiều instance? Có yêu cầu xem trạng thái, hủy, tăng số worker, giám sát backlog và sửa lỗi độc lập?

### Bảng quyết định nhanh

| Yêu cầu chính | Đồng bộ | `@Async` | Job/queue bền vững |
|---|---|---|---|
| Client phải biết kết quả thật trước khi nhận thành công | **Phù hợp nhất** | Chỉ có ích nếu chờ/join kết quả; vẫn chờ về mặt hợp đồng | Thường trả `202`; có thể chờ nhưng tốn thêm cơ chế |
| Tác vụ phụ, có thể bỏ nếu process chết | Có thể, nhưng tăng latency | **Phù hợp** nếu giới hạn tải | Thường phức tạp quá mức |
| Đã trả “đã nhận việc” thì không được mất khi restart | Không phù hợp nếu trả trước khi việc hoàn tất | **Không đủ** | **Phù hợp**, nếu ghi bền vững thành công trước `202` |
| Cần retry sau process crash, tra cứu, requeue | Phải tự thêm | Phải tự xây persistence | **Có thể thiết kế đáp ứng** bằng JobStore/queue và worker |
| Cần kết quả tức thời để giữ bất biến nghiệp vụ | **Phù hợp** | Không nên fire-and-forget | Tách riêng bước bắt buộc và bước hoàn tất sau |
| Khối lượng lớn, chạy phút/giờ | Rủi ro timeout, giữ request lâu | Chiếm pool/bộ nhớ; mất việc khi restart | **Phù hợp** nếu worker được điều tiết |
| Chạy nhiều node và không được xử lý trùng | Cần cơ chế phối hợp nghiệp vụ | Mỗi node có pool riêng | Cần claim/lease, dedupe và idempotency; không tự có exactly-once |

**Điểm mấu chốt:** **“Trả response nhanh”** và **“không mất công việc”** là hai yêu cầu độc lập. `@Async` giải quyết việc không cần chờ execution trên thread gọi; nó **không tự giải quyết durability**.

## 2. Phương án 1 — Xử lý đồng bộ

Luồng cơ bản:

```text
HTTP request
  -> Controller -> Service -> DB / external API / tính toán
                                 |
                            kết quả hoặc lỗi
                                 v
                    HTTP response cuối cùng
```

Trong Spring MVC kiểu servlet, controller/service đồng bộ thông thường chạy trên thread xử lý request. Nếu service gọi các method bình thường, kết quả hoặc exception đi ngược về caller. Dữ liệu transaction dạng imperative của `@Transactional` thường gắn với thread hiện tại. Nếu code chạy xong mới trả response, ứng dụng có thể nói chính xác hơn về kết quả **của phần nó đã hoàn thành**.

**Nên chọn khi:**

- Kiểm tra số dư, tồn kho, quyền truy cập, quy tắc nghiệp vụ trước khi chấp nhận yêu cầu.
- Đọc dữ liệu cần trả ngay; tính toán nhanh và có latency dự đoán được.
- Cần commit một transaction trước khi trả `201 Created` cho bản ghi vừa tạo.
- Bước xử lý ngắn, có thời gian chờ/timeout rõ ràng, số lượng request và tài nguyên cho phép.

**Chi phí và giới hạn:**

- Request thread phải đợi; gọi API ngoài chậm có thể làm tăng P95/P99 và cạn số thread/connection.
- Timeout phía client **không chứng minh server đã rollback hoặc chưa thực hiện tác dụng phụ**. Nếu client retry, vẫn cần idempotency key/định danh nghiệp vụ.
- `@Transactional` không làm các API bên ngoài (gửi email, REST call, hệ thống thanh toán) tự tham gia cùng transaction database.
- Một controller trả `200` sau khi gọi API ngoài không mặc nhiên đảm bảo mọi side effect ở nhiều hệ thống có tính nguyên tử.

**Ví dụ lựa chọn đúng:** Thanh toán đơn hàng cần biết thao tác chấp thuận/từ chối ngay: phần **xác thực, quyết định trạng thái và commit kết quả bắt buộc** phải có hợp đồng phản hồi rõ. Email biên nhận sau đó là một công việc khác, có thể xử lý bền vững ngoài request.

Không phải cứ request chậm là chuyển toàn bộ sang `@Async`. Trước hết tìm bottleneck, đặt timeout, giảm query thừa, cache hợp lý và xem client có thực sự cần đợi kết quả hay không.

## 3. Phương án 2 — `@Async`: chuyển execution sang Spring executor

`@Async` nằm trong `org.springframework.scheduling.annotation.Async`. Khi bật `@EnableAsync`, Spring tạo cơ chế interception (mặc định dùng proxy) cho method phù hợp. Lời gọi **đi qua proxy** được nộp vào `Executor`; thread gọi có thể tiếp tục mà không đợi công việc thực thi xong.

```text
Controller thread                      Thread của Spring executor
    |                                         |
    +--> asyncWorker.run(taskId)  ----------> +--> thực thi method @Async
    |                                         |      xử lý / log / lỗi
    +--> trả HTTP 202                         +--> hoàn tất CompletableFuture
```

`@Async` **không phải scheduler cron, job queue lưu đĩa, hay broker**. Một task nằm trong executor queue ở bộ nhớ của process có thể mất nếu process crash, bị kill hoặc node bị thay thế. “Graceful shutdown” chỉ giúp thu xếp lúc dừng có kiểm soát, không bảo đảm chống mất task khi crash/mất điện.

### Điều kiện và bẫy kỹ thuật

- Bật `@EnableAsync`; gọi method public được đánh dấu `@Async` **từ một bean khác qua Spring proxy**. Gọi `this.sendAsync()` trong chính bean đó (self-invocation) thường **không đi qua proxy**, do đó chạy đồng bộ. Không đặt `@Async` trên method không phù hợp trong `@Configuration` rồi mong Spring intercept.
- `@Async("workExecutor")` chọn executor được khai báo dưới tên/qualifier đó. Nếu dùng mặc định của Spring Boot, hành vi lựa chọn executor còn phụ thuộc bean và cấu hình; không giả định pool đã được tối ưu cho workload.
- Method `@Async` thông thường trả `void` hoặc `Future`/`CompletableFuture`. Với `CompletableFuture`, caller có thể nhận completion/failure qua future; exception của method `void` không truyền tự động về caller, cần `AsyncUncaughtExceptionHandler` hoặc tự xử lý/log.
- `@Transactional` trên **thread gọi** không tự đi theo thread mới. Nếu method async cần database transaction, mở transaction **ở chính execution của worker**, qua Spring proxy; tránh dựa vào entity/session/request scope vốn gắn với request thread.
- `SecurityContext`, MDC/tracing context, `LocaleContext` và các `ThreadLocal` khác không tự nhiên luôn có trên worker. Phải truyền dữ liệu cần thiết một cách rõ ràng hoặc dùng `TaskDecorator`/cơ chế context propagation phù hợp. Tránh truyền entity JPA đang managed qua thread.
- `CompletableFuture.join()` hoặc `get()` ngay sau submit khiến caller lại **đợi kết quả**, nên không đạt lợi ích giảm latency. Có thể vẫn hữu ích để chạy nhiều tác vụ I/O độc lập song song rồi tổng hợp, nhưng hợp đồng phản hồi vẫn đồng bộ.
- Nếu pool/queue đầy, executor có thể **từ chối nộp việc**, ví dụ ném `TaskRejectedException`. Đừng âm thầm nuốt lỗi hoặc trả “đã nhận chắc chắn” khi submission thất bại.
- Một request đến controller trả `202` sau khi submit `@Async` chỉ nên được hiểu là **đã nộp thành công vào xử lý trong process hiện tại**, không phải công việc đã được ghi bền vững.

### Cấu hình executor có giới hạn

`ThreadPoolTaskExecutor` cho phép chỉnh `corePoolSize`, `maxPoolSize`, `queueCapacity`, tên thread và chính sách từ chối:

```java
@Bean("workExecutor")
ThreadPoolTaskExecutor workExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(40);
    executor.setThreadNamePrefix("work-");
    executor.setRejectedExecutionHandler(
        new ThreadPoolExecutor.AbortPolicy()
    );
    return executor;
}
```

Đây là **đoạn cấu hình bên trong một Spring `@Configuration`**, cần import `org.springframework.context.annotation.Bean`, `org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor` và `java.util.concurrent.ThreadPoolExecutor`. Các con số chỉ mang tính minh họa, phải đo tải thực tế. Executor nhận thêm task vào queue khi core threads đã bận; khi **queue có giới hạn bị đầy** mới tăng thread đến max; nếu vẫn đầy thì từ chối. Queue quá lớn dễ tăng độ trễ và chiếm bộ nhớ; queue không giới hạn khiến `maxPoolSize` hầu như không có tác dụng cho việc mở rộng trên mức core.

CPU-bound nên khống chế số luồng chạy dựa trên lõi CPU/đo tải; I/O-bound cần giới hạn connection, timeout và mức đồng thời với upstream. **Virtual threads** (khi dùng Java 21+ và cấu hình thích hợp) có thể giảm chi phí chờ I/O, nhưng không đem lại durability, retry hay giới hạn truy cập hệ thống ngoài.

## 4. Ví dụ chạy được: cùng nghiệp vụ, hai hợp đồng HTTP khác nhau

Ứng dụng mẫu dùng **Java 17 + Spring Boot 3.5.0**. Nó mô phỏng công việc mất khoảng 500 ms; so sánh endpoint `/sync` đợi kết quả với `/async` trả `202` sau khi submit vào in-memory executor. Ví dụ độc lập về mặt Spring Boot, **không có lưu công việc bền vững**.

Cấu trúc:

```text
spring-work-demo/
  pom.xml
  src/main/java/demo/WorkDemoApplication.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>spring-work-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
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
```

`src/main/java/demo/WorkDemoApplication.java`:

```java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableAsync
public class WorkDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkDemoApplication.class, args);
    }

    @Bean("workExecutor")
    public ThreadPoolTaskExecutor workExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(40);
        executor.setThreadNamePrefix("work-");
        executor.setRejectedExecutionHandler(
            new ThreadPoolExecutor.AbortPolicy()
        );
        return executor;
    }

    @Service
    public static class WorkLogic {
        public String perform(String id) {
            try {
                Thread.sleep(500); // Mô phỏng I/O; không dùng sleep trong production.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Work interrupted", e);
            }
            if (id.startsWith("fail")) {
                throw new IllegalStateException("Simulated failure: " + id);
            }
            return "done:" + id;
        }
    }

    @Service
    public static class AsyncWorker {
        private final WorkLogic logic;

        public AsyncWorker(WorkLogic logic) {
            this.logic = logic;
        }

        @Async("workExecutor")
        public CompletableFuture<String> performAsync(String id) {
            // Chạy trên executor thread khi được gọi qua Spring proxy.
            return CompletableFuture.completedFuture(logic.perform(id));
        }
    }

    @RestController
    public static class WorkController {
        private final WorkLogic logic;
        private final AsyncWorker worker;

        public WorkController(WorkLogic logic, AsyncWorker worker) {
            this.logic = logic;
            this.worker = worker;
        }

        @PostMapping("/sync")
        public ResponseEntity<String> sync(@RequestParam String id) {
            // Đợi hoàn tất hoặc để exception được xử lý qua HTTP error.
            return ResponseEntity.ok(logic.perform(id));
        }

        @PostMapping("/async")
        public ResponseEntity<String> async(@RequestParam String id) {
            try {
                CompletableFuture<String> future = worker.performAsync(id);
                future.whenComplete((result, error) -> {
                    if (error != null) {
                        System.err.println("async-error:" + error);
                    } else {
                        System.out.println("async-result:" + result);
                    }
                });
                // Chỉ xác nhận executor đã nhận task vào process hiện tại.
                return ResponseEntity.accepted()
                    .body("accepted-in-memory:" + id);
            } catch (TaskRejectedException e) {
                return ResponseEntity.status(503)
                    .body("executor-overloaded");
            }
        }
    }
}
```

Chạy ở thư mục dự án (JDK 17, Maven):

```bash
mvn spring-boot:run
```

Trong terminal khác:

```bash
curl -sS -X POST "http://localhost:8080/sync?id=one"
curl -sS -X POST "http://localhost:8080/async?id=two"
```

Response body thành công lần lượt:

```text
done:one
accepted-in-memory:two
```

Endpoint `/sync` chỉ trả lời sau khi `WorkLogic.perform` kết thúc. `/async` trả ngay sau submit; log server **sau đó** có `async-result:done:two` (thứ tự thời gian phụ thuộc scheduler). Thử `id=fail-one` ở `/async`: client vẫn có thể nhận `202` còn log in `async-error`, chứng minh `202` **không đồng nghĩa đã xử lý thành công**. Nếu executor bão hòa, endpoint cố gắng trả `503` thay vì im lặng làm mất task. Trong production, dùng logging framework, metrics và idempotency thay cho `System.out`.

**Chỗ quan trọng trong code:** `WorkController` gọi `AsyncWorker` qua **bean khác** nên `@Async` được proxy intercept. Nếu chuyển `performAsync` thành method của `WorkController` rồi gọi `this.performAsync(...)`, kỳ vọng xử lý async sẽ không còn đúng.

**Khi không nên dùng ví dụ này:** Hệ thống đã hứa “email chắc chắn sẽ được gửi” hoặc “export đã được nhận để hoàn tất sau restart”. Tại điểm HTTP `202`, `future` chưa được lưu ngoài RAM và không có giao thức phục hồi job; cần phương án bền vững bên dưới.

## 5. Phương án 3 — Ghi công việc bền vững rồi worker xử lý

“Lưu công việc bền vững” không nhất thiết là một thư viện. Nó là hợp đồng hệ thống:

1. API **xác thực và kiểm tra điều kiện nghiệp vụ**.
2. API ghi **job/message cùng đủ dữ liệu để worker xử lý lại** vào PostgreSQL/MySQL hoặc broker đã được cấu hình persist theo bảo đảm mong muốn.
3. Sau khi **commit thành công**, API trả `202 Accepted`, một `jobId`/`operationId` và đường dẫn tra trạng thái.
4. Một hoặc nhiều worker **claim/consume**, thực hiện công việc, cập nhật trạng thái thành công hoặc ghi lỗi để retry.
5. Khi worker crash, công việc còn trong hệ lưu trữ và có thể được giao lại. Thiết kế **chấp nhận duplicate attempts** và ngăn duplicate side effect bằng idempotency.

```text
Client
  | POST /exports {requestId}
  v
API ---- transaction commit ----> durable jobs / outbox / broker
  |                                   |
  +---- 202 + jobId ----------------> | claim/delivery
                                      v
                                   Worker
                                      |
                            success / retry / DEAD
                                      |
Client GET /jobs/{jobId} <---- job status / progress
```

### Các lựa chọn triển khai

| Cơ chế | Thích hợp | Điều phải cấu hình/kiểm chứng |
|---|---|---|
| **Database job table** với worker polling/claim | Khối lượng vừa, trạng thái job và dữ liệu chính cùng database, triển khai đơn giản | Index, locking/lease, transaction, retry, max attempts, cleanup, tải polling |
| **Message broker** (RabbitMQ/Kafka/SQS…) | Tách producer–consumer, mở rộng worker, tích hợp nhiều service | Durability, publish confirmation, ack, redelivery, retention/DLQ, ordering và idempotency theo từng broker |
| **Transactional outbox** | Cần cập nhật nghiệp vụ trong DB và phát sự kiện/job ra broker mà không có khoảng trống “DB commit rồi publish fail” | Ghi outbox **cùng transaction**, relay CDC/polling, idempotent consumer và giám sát backlog |
| **Quartz JDBC JobStore** | Cần lịch chạy, trigger, pause/resume, misfire và quản trị job/cron | JDBC JobStore/cluster, lịch trigger; **không tự biến side effect thành exactly-once** |
| **Batch/workflow engine** | Pipeline nhiều bước, retry/compensation, tiến độ dài ngày, fan-out/fan-in | Trạng thái workflow, lịch sử, versioning và vận hành tương ứng |

Đừng chọn Kafka/Quartz chỉ vì “mạnh hơn `@Async`”: nếu tải nhỏ và job có trạng thái đơn giản, **bảng job trong database** có thể dễ bảo trì hơn. Ngược lại, database job table phải được thiết kế như một queue thực sự, không chỉ thêm một dòng rồi gọi method nền.

### Ghi nghiệp vụ + job trong cùng một transaction

Nguy hiểm nhất là kiểu:

```text
1. UPDATE order SET status = 'PAID';  (commit)
2. executor.submit(sendReceipt);    <-- process crash tại đây
3. trả HTTP thành công
```

Đơn hàng đã commit nhưng ý định gửi email **không được ghi ở đâu**. Gắn `@Async` vào bước (2) không giải quyết khoảng trống. Gắn `@TransactionalEventListener(phase = AFTER_COMMIT)` có thể giúp tránh xử lý trước commit, **nhưng listener/event trong process vẫn không phải một queue bền vững**; process crash sau commit trước khi nhận/chạy listener vẫn có thể mất việc.

Nếu cả trạng thái nghiệp vụ và job nằm trong cùng database, dùng:

```text
BEGIN;
UPDATE orders ...;                  -- thay đổi nghiệp vụ
INSERT INTO work_job (... READY);  -- lưu ý định thực hiện
COMMIT;
--> trả 202/201 tùy hợp đồng API
```

Nếu `COMMIT` thất bại, **không xác nhận đã nhận job**. Nếu thành công, worker có thể tìm job ngay cả sau restart. Nếu phải phát event sang broker khác, ghi **outbox record** trong cùng DB transaction và để relay publish sau; direct dual-write (commit DB rồi publish broker) không có tính nguyên tử trừ khi có giao thức giao dịch phân tán tương ứng.

## 6. Thiết kế tối thiểu một durable job table (PostgreSQL)

Ví dụ một bảng job xử lý độc lập, dùng PostgreSQL. SQL dưới đây là **schema/chương trình giao thức cho thiết kế**, không phải toàn bộ ứng dụng sản xuất. `operation_key` dùng để chống tạo trùng **ý định nghiệp vụ**; `attempts` và `lease_until` dùng cho retry/phục hồi.

```sql
CREATE TABLE work_job (
    job_id        UUID PRIMARY KEY,
    operation_key TEXT NOT NULL UNIQUE,
    job_type      TEXT NOT NULL,
    payload       JSONB NOT NULL,
    status        TEXT NOT NULL
                  CHECK (status IN ('READY', 'RUNNING', 'DONE', 'DEAD')),
    attempts      INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_until   TIMESTAMPTZ,
    owner         TEXT,
    last_error    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_work_job_ready
    ON work_job (available_at, created_at)
    WHERE status = 'READY';

CREATE INDEX idx_work_job_running_lease
    ON work_job (lease_until)
    WHERE status = 'RUNNING';
```

Ví dụ thêm công việc “gửi biên nhận” bằng một operation key ổn định. Trong ứng dụng thực, đặt lệnh insert **trong cùng `@Transactional` với cập nhật order**, hoặc trong outbox transaction nếu cần gửi qua broker:

```sql
INSERT INTO work_job (
    job_id, operation_key, job_type, payload, status
)
VALUES (
    '6e4e01a8-6aef-499f-8530-97327f5b2984',
    'receipt:order-42',
    'SEND_RECEIPT',
    '{"orderId":"order-42"}'::jsonb,
    'READY'
)
ON CONFLICT (operation_key) DO NOTHING;
```

Khi request cùng `operation_key` lặp lại, hệ thống nên trả về **`job_id` hiện có** sau khi đọc bản ghi đã tồn tại, thay vì tạo một job mới và gửi mail hai lần. Khóa unique ở database xử lý race giữa các request đồng thời.

### Claim an toàn khi có nhiều worker

Một giao thức polling dùng PostgreSQL `FOR UPDATE SKIP LOCKED` có thể **claim một job bằng một câu SQL nguyên tử**. Mỗi worker có `owner` riêng (ID instance/worker). Câu SQL sau dùng tham số `:owner` của tầng truy cập dữ liệu, và chạy trong một transaction **ngắn, commit trước khi gọi API ngoài**:

```sql
WITH picked AS (
    SELECT job_id
    FROM work_job
    WHERE (status = 'READY' AND available_at <= now())
       OR (status = 'RUNNING' AND lease_until < now())
    ORDER BY available_at, created_at
    FOR UPDATE SKIP LOCKED
    LIMIT 1
)
UPDATE work_job AS j
SET status      = 'RUNNING',
    attempts    = j.attempts + 1,
    owner       = :owner,
    lease_until = now() + INTERVAL '2 minutes',
    updated_at  = now()
FROM picked
WHERE j.job_id = picked.job_id
RETURNING j.job_id, j.operation_key, j.job_type,
          j.payload, j.attempts;
```

**Cơ chế:** row lock/`SKIP LOCKED` ngăn hai transaction cùng claim một dòng trong thời điểm cạnh tranh. Sau commit, worker xử lý side effect **ngoài transaction claim** để không giữ khóa DB xuyên thời gian chờ network. Lease cho phép node khác claim lại nếu worker cũ chết.

**Quan trọng:** Một worker quá chậm có thể hết lease mà vẫn đang chạy; worker khác có thể claim cùng job. Với công việc dài, cần **gia hạn lease định kỳ** và dùng **fencing token/attempt number** khi update trạng thái. Lease không tạo ra exactly-once. Chọn thời lượng lease dựa trên timeout và xử lý mất kết nối, không chọn tùy tiện.

### Ghi hoàn thành, retry và DEAD

Sau khi side effect thành công, worker cập nhật trạng thái với điều kiện owner + số lần claim:

```sql
UPDATE work_job
SET status = 'DONE',
    lease_until = NULL,
    owner = NULL,
    updated_at = now()
WHERE job_id = :job_id
  AND status = 'RUNNING'
  AND owner = :owner
  AND attempts = :attempts;
```

Kiểm tra **số dòng bị update phải bằng 1**. Nếu bằng 0, lease hoặc quyền sở hữu có thể đã thay đổi. Lúc gặp lỗi tạm thời, chuyển về `READY` với `available_at` ở tương lai (exponential backoff + jitter); sau tối đa N lần thì `DEAD` để vận hành can thiệp hoặc áp dụng chính sách replay có kiểm soát. Chỉ owner/attempt hiện tại được đánh dấu lại; không để worker cũ ghi đè worker mới.

```text
READY --claim--> RUNNING --success--> DONE
  ^               |
  |               +--transient error--> READY (delay/backoff)
  |               +--max attempts-----> DEAD
  +--lease expired / worker crash-----+
```

Trong worker production, bổ sung timeout cho external call, limit concurrency, phân loại lỗi (4xx không retry vô hạn, 429/5xx có thể retry có giới hạn), dead-job inspection, cảnh báo backlog và quy trình xử lý dữ liệu lỗi.

### Tại sao vẫn phải idempotent?

Giả sử worker gọi dịch vụ gửi email và **email đã gửi**, nhưng worker crash **trước lệnh `UPDATE ... DONE`**. Sau khi lease hết, job được giao lại. Hệ thống lưu bền vững giúp **không mất yêu cầu**, nhưng có thể **thực hiện side effect lần nữa**.

Cách bảo vệ phụ thuộc nghiệp vụ:

- Gửi một request tới provider có idempotency key, dùng **cùng operation key** khi retry; xác nhận provider thật sự hỗ trợ idempotency và phạm vi hiệu lực.
- Với thay đổi trong cùng database, lưu `processed_operation`/unique business key cùng transaction với thay đổi trạng thái để retry không nhân đôi.
- Với chuyển tiền/ghi nợ, định danh giao dịch bất biến, kiểm tra trạng thái upstream khi timeout, thực hiện state machine/compensation thích hợp; **không lấy “retry là an toàn” làm giả định**.
- Với email không có idempotency hỗ trợ, có thể phải chấp nhận nguy cơ gửi trùng và thiết kế workflow nghiệp vụ tương ứng.

“**At least once**” nghĩa là yêu cầu có thể giao nhiều lần. “**Exactly once**” cho toàn bộ side effect liên hệ nhiều hệ thống thường không có chỉ nhờ queue/DB job/Quartz; cần ràng buộc và cơ chế idempotency ở nơi thực thi tác dụng phụ.

## 7. `@Transactional`, `@Async` và event sau commit

### Lỗi 1: truyền context giao dịch từ controller sang thread khác

```java
@Transactional
public void createOrder() {
    orderRepository.save(new Order(...));
    asyncWorker.sendReceipt(orderId); // worker có thể chạy trước COMMIT
}
```

Đây là **đoạn minh họa vấn đề**, không phải lớp Java độc lập. Nếu `@Async` chạy trên thread khác, transaction của caller **không tự truyền sang**. Worker có thể query không thấy dữ liệu chưa commit, hoặc gửi mail cho đơn hàng sau đó bị rollback.

Các lựa chọn:

1. Nếu task **không bắt buộc bền vững**, đăng ký sau commit (ví dụ `@TransactionalEventListener(phase = AFTER_COMMIT)`), đồng thời chấp nhận rủi ro crash sau commit trước execution.
2. Nếu task **bắt buộc phải chạy**, ghi job/outbox trong **cùng database transaction** với nghiệp vụ, worker đọc sau commit.
3. Nếu worker cần giao dịch riêng khi sửa DB, đặt `@Transactional` trên service method được gọi qua Spring proxy **trong worker thread**, không dựa vào transaction bên gọi.

### Lỗi 2: hiểu `@Async` là một transaction khác

`@Async` không tự bắt đầu transaction. `@Transactional` và `@Async` là hai interceptor độc lập. Khi phối hợp, phải xác định transaction nào quản lý bước nào và bảo đảm interceptor được kích hoạt qua proxy. Tránh chuyển một entity JPA lazy-loaded từ request thread sang worker rồi truy cập khi persistence context đã đóng; chuyển ID/value object bất biến và tải dữ liệu tại worker.

### Lỗi 3: nghĩ `@TransactionalEventListener` là outbox

Spring có thể gọi listener ở `AFTER_COMMIT` để tránh làm việc trước khi transaction thành công. Nhưng listener chưa được ghi thành một **job bền vững**; crash ngay sau commit vẫn có thể mất việc. Nếu hệ thống hứa gửi mail/thông báo chắc chắn, transaction nên ghi **outbox** trước commit; relay có thể publish lại sau crash, và consumer phải idempotent.

## 8. Đặt API contract đúng với kết quả

### Đồng bộ

```http
POST /payments
200 OK
{"status":"PAID","paymentId":"p-123"}
```

Chỉ gửi kết quả `PAID` khi hệ thống có bằng chứng nghiệp vụ đã hoàn tất tương ứng. Nếu upstream timeout và trạng thái không rõ, cần trả trạng thái “pending/unknown” hoặc hợp đồng đối soát riêng, tránh tự khẳng định đã thành công.

### `@Async` best-effort

```http
POST /notifications/best-effort
202 Accepted
{"status":"ACCEPTED_IN_MEMORY"}
```

Response cho biết request đã được đưa vào executor hiện tại. Nó **không phải cam kết hoàn thành**, và không có trạng thái bền vững sau restart. Sản phẩm cần truyền đạt rõ giới hạn này nếu client phụ thuộc vào tác vụ.

### Durable

```http
POST /exports
202 Accepted
Location: /jobs/6e4e01a8-6aef-499f-8530-97327f5b2984
{"jobId":"6e4e01a8-6aef-499f-8530-97327f5b2984",
 "status":"READY"}
```

Sau khi commit job, client có thể poll `GET /jobs/{jobId}`. Kết quả nên phân biệt `READY`, `RUNNING`, `DONE`, `DEAD`; nếu có thể hủy, xác định hủy trước/đang chạy, cleanup và tác dụng phụ đã xảy ra. Khi idempotent submit lại cùng request key, trả ID của job đã có.

Đối với kết quả tải file, lưu artifact ở object storage và trả URL hoặc location khi `DONE`; không giữ HTTP connection nhiều phút chỉ để đợi worker hoàn thành.

## 9. Đánh đổi tài nguyên, lỗi và giám sát

| Rủi ro / yêu cầu | Đồng bộ | `@Async` | Durable worker |
|---|---|---|---|
| Thread/request timeout | Có; giới hạn request và upstream timeout | Request nhanh, nhưng worker vẫn có thể timeout | Request ngắn; job có timeout thực thi riêng |
| Saturation | HTTP thread pool, DB connection pool, upstream | Thread/queue memory, rejection | Consumer concurrency, queue lag, DB poll contention |
| Process crash sau acknowledge | Nếu việc đã hoàn thành thì không phải “pending task”; nhưng cần xử lý trạng thái chưa rõ | Task đang chờ/chạy có thể mất | Job còn tồn tại nếu storage/ack được cấu hình đúng |
| Exception | Có thể trả lỗi HTTP ngay | Future/log/uncaught handler; client đã nhận response | Lưu lỗi, backoff, DEAD/DLQ, alert |
| Retry từ client | Phải xét idempotency | Có thể nộp trùng task | Dùng request key/operation key và dedupe |
| Giám sát | HTTP latency, error rate, timeout | Active threads, queue depth, reject count, completion/failure | Queue age, lag, attempts, lease expiry, DONE/DEAD, time-to-complete |
| Điều phối nhiều node | Yêu cầu nghiệp vụ riêng | Pool trên mỗi JVM, không có global coordination | Claim/ack/lease hoặc broker consumer semantics |

**Các chỉ số hữu ích**:

- Đồng bộ: request duration (P50/P95/P99), error/timeout, số thread đang bận, kết nối DB/upstream, tỉ lệ retry.
- `@Async`: active threads, queue depth, số task submitted/completed/failed/rejected, thời gian từ submit đến start và độ dài execution.
- Durable: số `READY` quá tuổi, `RUNNING` hết lease, độ trễ enqueue→start/finish, attempts, `DEAD`, retry rate, duplicate suppression và độ trễ outbox relay.

**Backpressure:** Khi hết năng lực, từ chối có kiểm soát (`429`/`503` kèm chỉ dẫn retry thích hợp) hoặc giới hạn tốc độ enqueue thay vì để queue/bộ nhớ tăng vô hạn. Với durable queue, việc “lưu được job” không có nghĩa worker xử lý kịp SLA; phải giám sát backlog và áp dụng quota/ưu tiên.

**Security:** Chỉ lưu dữ liệu tối thiểu cần cho worker; không nhét access token ngắn hạn, mật khẩu, session object hoặc toàn bộ HTTP request vào payload. Worker tự kiểm tra quyền phù hợp, dùng service credentials và truy vết bằng ID. Thiết kế chính sách retention và xóa payload nhạy cảm.

## 10. Các tình huống thực tế

| Tình huống | Phương án phù hợp và lý do |
|---|---|
| Validate quyền truy cập, số dư, số lượng tồn trước khi trả kết quả | **Đồng bộ**: kết quả là điều kiện để chấp nhận yêu cầu |
| Ghi đơn hàng, tạo bản ghi trong DB và trả ID đơn | **Đồng bộ cho commit đơn hàng**; các việc hậu xử lý chọn riêng |
| Ghi log debug/telemetry phụ, mất một bản ghi được chấp nhận | **`@Async`** hoặc batching telemetry có giới hạn nếu muốn giảm latency |
| Gửi email giao dịch đã hứa với khách | **Durable job/outbox**: cần phục hồi sau crash, retry, trạng thái |
| Sinh báo cáo CSV lớn mất 10 phút | **Durable job** + `202`/job status, object storage kết quả |
| Gọi hai API độc lập để tổng hợp kết quả response | **Đồng bộ về hợp đồng**, có thể thực thi I/O song song bằng future nếu vẫn đợi đầy đủ và kiểm soát timeout |
| Resize ảnh phụ để tăng tốc giao diện, ảnh có thể tái tạo | `@Async` nếu được phép mất và tài nguyên đủ; **durable** nếu cam kết ảnh cuối cùng phải có |
| Ghi nhận yêu cầu thanh toán, ngân hàng trả kết quả không xác định | Dùng **state machine + idempotency + đối soát**; không trả `PAID` nếu chưa xác minh; có thể đẩy phần đối soát vào durable job |
| Chạy báo cáo định kỳ/cron, cần misfire/cluster | **Scheduler bền vững** (ví dụ Quartz JDBC) nếu yêu cầu lịch; job thực thi vẫn cần idempotency |
| Phát event đổi trạng thái tới nhiều hệ thống | **Transactional outbox + broker/relay** nếu phải bảo đảm event tồn tại sau commit DB |

### Ví dụ tách các bước của một use case

“Đặt hàng và gửi biên nhận” không buộc phải chọn một cơ chế cho **cả** luồng:

```text
POST /orders
  1. Đồng bộ: validate, reserve/commit order.
  2. Cùng DB transaction: insert outbox/send-receipt job.
  3. Commit rồi trả 201 với orderId.
  4. Worker bền vững: gửi biên nhận, retry khi provider lỗi.
  5. GET /orders/{id}: trạng thái đơn (tách khỏi trạng thái gửi email).
```

Nếu email không phải cam kết sản phẩm và bỏ một lần được chấp nhận, bước (2)/(4) có thể thay bằng `@Async` sau commit, nhưng cần ghi rõ đây là tradeoff chủ động.

## 11. Checklist thiết kế trước khi triển khai

**Hợp đồng API**

- “Thành công” nghĩa là đã xử lý xong hay mới tiếp nhận?
- Có cần request ID/job ID, `Location`, status endpoint và khả năng retry từ client?
- Khi timeout, client biết trạng thái cuối bằng cách nào?

**Executor và concurrency**

- `@EnableAsync` đã bật? Lời gọi có đi qua proxy và bean riêng?
- Pool/queue/connection limit đã có ngưỡng, rejection policy và metrics?
- Các thread context/transaction có được mở/truyền một cách có chủ đích?
- Có timeout, cancellation hoặc quy tắc ứng phó khi worker dừng?

**Durability và vận hành**

- Đã xác định chính xác điểm **persist/commit trước acknowledge**?
- Nếu ghi DB và publish broker, đã có outbox hoặc giải pháp nhất quán tương ứng?
- Có atomic claim/ack, lease renewal/fencing, retry/backoff/jitter và DEAD/DLQ?
- Có operation key và cơ chế idempotency tại nơi thực thi side effect?
- Đã diễn tập worker crash trước/sau external call, server restart, duplicate delivery, broker outage, DB outage và backlog tăng nhanh?

**Không có lựa chọn duy nhất cho mọi bước:** Hãy chọn **đồng bộ** cho phần mà người gọi phải biết kết quả ngay, **`@Async`** cho công việc phụ chấp nhận mất khi process dừng, và **lưu việc bền vững** cho lời hứa phải hoàn tất hoặc khôi phục sau lỗi. Đối với nhiều bước, tách từng bước theo hợp đồng của nó và nối chúng bằng ranh giới transaction rõ ràng.

## 12. Nguồn tham khảo

- Spring Framework — Task Execution and Scheduling (proxy `@Async`, return types, executors, exceptions): https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Boot — Task Execution and Scheduling (executor auto-configuration, cấu hình): https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html
- Spring Framework — Declarative Transactions (`@Transactional` thread-bound): https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html
- Spring Framework — Transaction-bound Events (`@TransactionalEventListener`): https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html
- Transactional Outbox Pattern: https://microservices.io/patterns/data/transactional-outbox.html
- Idempotent Consumer Pattern: https://microservices.io/patterns/communication-style/idempotent-consumer.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="design-practice-002">Thiết kế đầu vào, kết quả và trạng thái công việc độc lập với request</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế đầu vào, kết quả và trạng thái công việc độc lập với request

## 1. Bài toán: HTTP request kết thúc trước khi công việc kết thúc

Trong ứng dụng Spring Framework, request có thể chỉ mất vài mili giây để yêu cầu tạo báo cáo, nhập dữ liệu hoặc xử lý hàng loạt, trong khi công việc thật sự kéo dài hàng giây hoặc hàng phút. Nếu đưa việc xử lý sang `TaskExecutor` hoặc phương thức `@Async`, thread xử lý HTTP có thể trả response ngay; worker chạy sau đó trong **thread khác** và có thể tiếp tục khi client ngắt kết nối.

Thiết kế cần tách thành ba hợp đồng (contract) có thể được lưu, tra cứu và kiểm thử riêng:

1. **Đầu vào của job:** dữ liệu cần để công việc chạy mà không phụ thuộc đối tượng HTTP request còn sống.
2. **Trạng thái của job:** tiến trình được quản lý bằng ID duy nhất và các chuyển trạng thái hợp lệ; cả client và worker có thể tra cứu.
3. **Kết quả của job:** dữ liệu thành công hoặc thông tin thất bại được lưu bằng kiểu rõ ràng, chỉ công bố sau khi xử lý thật sự hoàn tất.

Sơ đồ ranh giới:

```text
HTTP POST /jobs
    |
    | xác thực, kiểm tra quyền, validate input
    | tạo jobId + job input độc lập
    v
Job service ----> Job store (jobId, input, status=QUEUED)
    |                                     ^
    | gửi thông báo / enqueue             |
    v                                     |
Executor / Queue ---> Worker ------------+
                       | chạy business logic, lưu result/error
                       v
                    Job store
                         ^
                         |
HTTP GET /jobs/{jobId} --+    (request mới, có quyền truy cập)
```

**Điểm cốt lõi:** request gửi công việc và request xem trạng thái là hai request khác nhau. Worker được phép chạy mà không có `HttpServletRequest`, `HttpServletResponse`, session hay thread phục vụ HTTP đầu tiên. Kể cả một request đơn bị timeout, kết quả công việc vẫn phải có nơi tồn tại nếu nghiệp vụ yêu cầu.

## 2. Đầu vào: DTO độc lập với servlet và thread hiện tại

Một command tốt là **giá trị rõ nghĩa, tự chứa những tham số cần thiết**, không mang theo tham chiếu tới object có vòng đời request. Ví dụ yêu cầu tạo báo cáo:

```json
{
  "reportType": "sales",
  "period": "2026-09",
  "format": "CSV"
}
```

Request trên chỉ là dữ liệu do client gửi. Sau khi authentication/authorization, server tạo đầu vào nội bộ, chẳng hạn:

```json
{
  "schemaVersion": 1,
  "jobType": "EXPORT_REPORT",
  "tenantId": "tenant-a",
  "requestedBy": "user-42",
  "reportType": "sales",
  "period": "2026-09",
  "format": "CSV"
}
```

`tenantId` và `requestedBy` trong command nội bộ phải được lấy từ **identity đáng tin của server tại thời điểm tiếp nhận**, không mù quáng tin giá trị tương tự trong body client. Worker dùng chúng để kiểm soát phạm vi dữ liệu và ghi audit; nếu chính sách yêu cầu quyền hiện tại, worker vẫn cần xác minh quyền ở lúc thực thi.

### Nguyên tắc thiết kế đầu vào

- **DTO không gắn HTTP:** không truyền `HttpServletRequest`, `HttpSession`, `MultipartFile` còn phụ thuộc request, `ServletOutputStream`, `BindingResult`, `SecurityContextHolder`, `RequestContextHolder` hoặc bean `@RequestScope` vào job. Một số object có thể không còn hợp lệ hoặc không tồn tại trên worker thread.
- **Dữ liệu tối thiểu và có cấu trúc:** ưu tiên `record`/DTO bất biến, giá trị nguyên thủy, enum, ID tài nguyên, thời hạn và metadata cần thiết. Nếu truyền `List`/`Map`, sao chép bất biến hoặc serialize; tránh đưa tập hợp có thể bị sửa đồng thời.
- **Không chuyển JPA entity quản lý theo request:** entity có lazy relation, `EntityManager`, connection hoặc transaction gắn thread ban đầu có thể lỗi sau khi request kết thúc. Truyền khóa bản ghi và load lại trong transaction riêng của worker.
- **Tệp lớn:** upload vào storage trước (hoặc lưu bản ghi staging), sau đó đưa `objectKey`/ID vào command; không giữ stream upload và toàn bộ bytes trong RAM chỉ để worker dùng sau.
- **Validate trước khi nhận:** kiểu dữ liệu, enum, khoảng giá trị, giới hạn kích thước, phạm vi tenant, quyền trên tài nguyên, quota. Việc từ chối input sai thường là lỗi đồng bộ HTTP 400/403/422, **không** cần tạo job để rồi báo `FAILED`.
- **Phân biệt snapshot và lookup:** nếu command ghi `reportId`, worker có thể đọc phiên bản mới khi thực thi. Nếu cần “dữ liệu đúng như lúc đặt hàng”, lưu `sourceVersion`/ETag hoặc snapshot được phép dùng; không ngầm hứa dữ liệu bất biến trong khi vẫn đọc bản mới.
- **Phân biệt idempotency và identity:** `jobId` nhận diện một job; `Idempotency-Key` nhận diện ý định tạo job được retry; `attemptId` nhận diện một lần worker thực thi khi retry nội bộ. Không dùng các khái niệm này thay thế nhau.
- **Phiên bản hóa contract:** `schemaVersion` giúp worker hiểu command đã tồn tại trong queue trước khi deploy phiên bản code mới. Tránh thay đổi ý nghĩa trường mà không có cơ chế tương thích hoặc migration.

Trong Java 17+, có thể biểu diễn command theo kiểu bất biến:

```java
public record ExportReportCommand(
        int schemaVersion,
        String tenantId,
        String requestedBy,
        String reportType,
        String period,
        String format
) {}
```

Đây là **đoạn minh họa cấu trúc**; ở production cần validate tại boundary, giới hạn lựa chọn và xác minh `tenantId`/`requestedBy` từ principal trước khi tạo command. `record` chỉ làm các trường final; nếu có collection mutable hoặc object lồng nhau mutable, vẫn cần defensive copy/deep snapshot khi thích hợp.

## 3. Kết quả: giá trị nghiệp vụ tách khỏi HTTP response

Job chạy xong phải có một trong hai loại đầu ra có nghĩa rõ ràng:

- **Thành công:** result DTO có schema rõ ràng, ví dụ `resultId`, `downloadKey`, `rowsProcessed`, `checksum` hoặc dữ liệu nhỏ có thể trả inline.
- **Thất bại:** error code ổn định cho client (`SOURCE_NOT_FOUND`, `VALIDATION_FAILED_DURING_RUN`, `PROCESSING_ERROR`...), thông điệp an toàn và khả năng retry; stack trace chi tiết nằm trong log nội bộ kèm `jobId`, không để lộ SQL/secret trong response.

Ví dụ kết quả thành công cho job xuất báo cáo:

```json
{
  "resultVersion": 1,
  "reportId": "report-784",
  "format": "CSV",
  "rowsProcessed": 1200,
  "artifactKey": "exports/tenant-a/report-784.csv"
}
```

Khi artifact lớn, lưu file ở object storage và ghi **ID/key do server quản lý** vào result. Endpoint lấy kết quả phải kiểm quyền riêng; nếu phát signed download URL thì cho thời hạn ngắn, không coi đường dẫn trả về là dữ liệu công khai. Nếu job thất bại, `result` nên rỗng, `error` có code xác định. Không dùng một chuỗi bất kỳ để trộn result, exception và trạng thái.

Nên định nghĩa rõ “hoàn tất” là thời điểm nào: logic sinh dữ liệu xong, file đã upload được xác nhận, dữ liệu đã commit, hay email đã được nhà cung cấp chấp nhận. Chỉ chuyển sang `SUCCEEDED` sau **điểm cam kết kết quả** mà nghiệp vụ thực sự yêu cầu; không chuyển thành công ngay khi chỉ mới gọi một phương thức bất đồng bộ tiếp theo.

## 4. Mô hình trạng thái và luật chuyển

Một state machine thực dụng:

```text
             +---------+
      +----> | QUEUED  | ----- từ chối nhận ----> REJECTED
      |      +----+----+
      |           |
      |    worker nhận job
      |           v
      |      +---------+
      |      | RUNNING |
      |      +----+----+
      |           |
      |       +---+------------------+
      |       v                      v
      |  +-----------+          +---------+
      |  | SUCCEEDED |          | FAILED  |
      |  +-----------+          +---------+
      |
   tiếp nhận hợp lệ
```

| Trạng thái | Ý nghĩa | Kết quả trả client |
| --- | --- | --- |
| `QUEUED` | Đã lưu/yêu cầu xử lý; chưa chạy; có thể đang chờ trong queue | `result=null`; còn chờ |
| `RUNNING` | Worker bắt đầu tác vụ | Có thể có progress; chưa có kết quả cuối |
| `SUCCEEDED` | Đã hoàn thành và commit kết quả theo định nghĩa nghiệp vụ | Có `result` hoặc `resultRef` |
| `FAILED` | Job đã bắt đầu nhưng không hoàn thành thành công | Có `error.code`, `completedAt` |
| `REJECTED` | Hệ thống từ chối nhận/chạy job vì capacity/policy | Không có `startedAt`; thường trả HTTP 429/503 phù hợp |
| `CANCEL_REQUESTED` / `CANCELLED` | Tùy chọn: yêu cầu hủy / worker xác nhận dừng theo giao thức | Không mặc định `cancel()` đồng nghĩa task đã dừng |
| `EXPIRED` / `UNKNOWN` | Tùy chọn: hết thời hạn lưu hoặc không thể xác nhận kết cục | Phân biệt hết retention với thất bại nghiệp vụ |

Có thể thêm `VALIDATING`, `SCHEDULED`, `RETRY_WAIT` hoặc `TIMED_OUT` khi có nghiệp vụ tương ứng; không cần nhiều trạng thái nếu không thể giải thích chuyển đổi và quyền sở hữu của chúng.

### Bất biến cần giữ

1. Có **một `jobId` ổn định** cho client. Một job có một trạng thái hiện tại, được lưu độc lập với request.
2. Sau trạng thái kết thúc (`SUCCEEDED`, `FAILED`, `REJECTED`, `CANCELLED`), callback đến muộn không được ghi ngược thành `RUNNING`. Dùng cập nhật có điều kiện, optimistic locking `version` hoặc CAS.
3. `startedAt` chỉ được ghi khi worker **thực sự bắt đầu**; `completedAt` chỉ có khi thực thi kết thúc. Một job bị từ chối trước khi bắt đầu cần `rejectedAt` riêng nếu cần đo.
4. `SUCCEEDED` gắn với result đã hợp lệ và bền theo hợp đồng; `FAILED` gắn với error code đã chuẩn hóa. Không suy kết quả từ `Future.isDone()` vì future bị lỗi/hủy cũng có thể done.
5. Job có timeout không nhất thiết đồng nghĩa worker đã dừng. Nếu tác vụ chạy quá `deadline`, đặt chính sách cancel/interrupt hợp tác, kiểm tra quá hạn và đối soát side effect.
6. Mọi thao tác xem, tải hoặc hủy job phải kiểm **tenant/owner/quyền tài nguyên**, kể cả khi client biết UUID của job.

Trong hệ thống có retry, nên tách `jobId` (lần yêu cầu logic) và `attemptId` (lần worker chạy). Trạng thái và thông số `startedAt`/`completedAt` của từng attempt có thể được lưu riêng, trong khi job tổng hợp chuyển từ `RETRY_WAIT` sang `RUNNING` ở attempt kế tiếp. Không coi việc retry tự động đảm bảo xử lý *exactly once*.

## 5. Ví dụ tự chạy: một job service không giữ HTTP request

Ví dụ dưới đây dùng **Java 17+** và **Spring Framework 6.1.x**. Để chạy ngay không cần web server, ví dụ dùng `ThreadPoolTaskExecutor`, DTO bất biến và `ConcurrentHashMap` lưu trạng thái. `CompletableFuture` trong `Ticket` chỉ là **điểm chờ phục vụ console demo**; controller thật chỉ trả `jobId` và client hỏi trạng thái bằng request mới.

Để minh họa input và result có kiểu, nghiệp vụ demo tính tổng các số nguyên. Trong ứng dụng thật, thay hàm tính tổng bằng việc đọc tài nguyên qua ID, xử lý và ghi result trong transaction riêng.

Cấu trúc:

```text
pom.xml
src/main/java/demo/JobContractDemo.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>job-contract-demo</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.14</version>
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
                <version>3.4.1</version>
            </plugin>
        </plugins>
    </build>
</project>
```

`src/main/java/demo/JobContractDemo.java`:

```java
package demo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class JobContractDemo {
    enum State { QUEUED, RUNNING, SUCCEEDED, FAILED, REJECTED }

    // Command đã được xây dựng từ dữ liệu request + identity đã xác thực.
    record JobInput(String tenantId, String ownerId, List<Integer> numbers) {
        JobInput {
            if (tenantId == null || tenantId.isBlank()
                    || ownerId == null || ownerId.isBlank()) {
                throw new IllegalArgumentException("Missing identity");
            }
            numbers = List.copyOf(Objects.requireNonNull(numbers));
            if (numbers.isEmpty() || numbers.size() > 100) {
                throw new IllegalArgumentException("Invalid input size");
            }
        }
    }

    record JobResult(long sum, int count) {}

    // Snapshot bất biến: một bản ghi có thể được đọc trên thread khác.
    record JobView(
            String jobId,
            String tenantId,
            String ownerId,
            State state,
            Instant submittedAt,
            Instant startedAt,
            Instant completedAt,
            JobResult result,
            String errorCode
    ) {}

    // Completion chỉ dùng để chờ trong main demo, không đưa vào HTTP DTO.
    record Ticket(String jobId, CompletableFuture<JobView> completion) {}

    static final class JobService {
        private final ThreadPoolTaskExecutor executor;
        private final ConcurrentMap<String, JobView> jobs =
                new ConcurrentHashMap<>();

        JobService(ThreadPoolTaskExecutor executor) {
            this.executor = executor;
        }

        Ticket submit(JobInput input) {
            Objects.requireNonNull(input);
            String id = UUID.randomUUID().toString();
            jobs.put(id, new JobView(
                    id, input.tenantId(), input.ownerId(), State.QUEUED,
                    Instant.now(), null, null, null, null));
            CompletableFuture<JobView> completion = new CompletableFuture<>();

            try {
                executor.execute(() -> runJob(id, input, completion));
            } catch (TaskRejectedException rejection) {
                JobView rejected = transition(
                        id, State.QUEUED, State.REJECTED, null,
                        "EXECUTOR_BUSY");
                completion.complete(rejected);
            }
            return new Ticket(id, completion);
        }

        // Với tenant/owner khác, trả null để không lộ job thuộc ai.
        JobView find(String id, String tenantId, String ownerId) {
            JobView view = jobs.get(id);
            if (view == null
                    || !view.tenantId().equals(tenantId)
                    || !view.ownerId().equals(ownerId)) {
                return null;
            }
            return view;
        }

        private void runJob(
                String id,
                JobInput input,
                CompletableFuture<JobView> completion
        ) {
            // Claim một lần: callback đến muộn không khởi động lại job.
            AtomicBoolean claimed = new AtomicBoolean(false);
            jobs.computeIfPresent(id, (key, old) -> {
                if (old.state() != State.QUEUED) return old;
                claimed.set(true);
                return new JobView(
                        old.jobId(), old.tenantId(), old.ownerId(),
                        State.RUNNING, old.submittedAt(), Instant.now(),
                        null, null, null);
            });
            if (!claimed.get()) {
                completion.complete(jobs.get(id));
                return;
            }

            try {
                long sum = 0L;
                for (Integer value : input.numbers()) {
                    sum = Math.addExact(sum, value.longValue());
                }
                JobResult result = new JobResult(sum, input.numbers().size());
                JobView success = transition(
                        id, State.RUNNING, State.SUCCEEDED, result, null);
                completion.complete(success);
            } catch (Throwable failure) {
                JobView failed = transition(
                        id, State.RUNNING, State.FAILED, null,
                        "PROCESSING_ERROR");
                completion.complete(failed);
                // Không che một lỗi nghiêm trọng của JVM.
                if (failure instanceof Error fatal) throw fatal;
            }
        }

        private JobView transition(
                String id, State expected, State next,
                JobResult result, String errorCode
        ) {
            return jobs.computeIfPresent(id, (key, old) -> {
                if (old.state() != expected) return old;
                Instant started = next == State.RUNNING
                        ? Instant.now() : old.startedAt();
                Instant completed =
                        next == State.SUCCEEDED || next == State.FAILED
                        ? Instant.now() : old.completedAt();
                return new JobView(
                        old.jobId(), old.tenantId(), old.ownerId(), next,
                        old.submittedAt(), started, completed,
                        result, errorCode);
            });
        }
    }

    public static void main(String[] args) {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(8);
        pool.setThreadNamePrefix("job-");
        pool.initialize();

        try {
            JobService service = new JobService(pool);
            // Trong HTTP, tenantId/ownerId phải lấy từ principal đáng tin.
            JobInput command = new JobInput(
                    "tenant-a", "user-42", List.of(2, 3, 5));
            Ticket ticket = service.submit(command);

            // Chỉ console demo chờ; controller HTTP trả jobId ngay.
            JobView finished = ticket.completion().join();
            JobView visible = service.find(
                    ticket.jobId(), "tenant-a", "user-42");

            System.out.println("has jobId: " + !ticket.jobId().isBlank());
            System.out.println("state: " + finished.state());
            System.out.println("sum: " + visible.result().sum());
            System.out.println("count: " + visible.result().count());
            System.out.println("cross-tenant visible: " +
                    (service.find(
                            ticket.jobId(), "tenant-b", "user-42") != null));
        } finally {
            pool.shutdown();
        }
    }
}
```

Tại thư mục có `pom.xml`, dùng Maven và JDK 17+:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.JobContractDemo
```

**Stdout của chương trình demo** (không tính thông báo phụ do Maven/JVM phát ra):

```text
has jobId: true
state: SUCCEEDED
sum: 10
count: 3
cross-tenant visible: false
```

Phân tích ranh giới:

- `JobInput` là record immutable và dùng `List.copyOf` để tránh caller sửa danh sách trong lúc worker chạy. Nó chỉ chứa dữ liệu, không giữ request object.
- `JobService.submit()` tạo ID và snapshot `QUEUED` **trước khi** gọi `execute()`; callback có thể chạy/hoàn tất ngay trước lúc `execute()` trả về, nhưng vẫn tìm được job.
- Worker claim `QUEUED → RUNNING` rồi tính toán; `ConcurrentHashMap.computeIfPresent` giữ phép thay trạng thái của một job nguyên tử trong **một process**. `SUCCEEDED` chỉ có sau khi tạo `JobResult`; nhánh lỗi chuyển `FAILED` với error code an toàn.
- `TaskRejectedException` chuyển sang `REJECTED` (không có `startedAt`/`completedAt`); một REST controller biết việc từ chối đồng bộ nên phản hồi lỗi capacity phù hợp thay vì nói job đang chạy.
- `find()` yêu cầu đúng tenant và owner, nhưng **không phải hệ thống authorization đầy đủ**; ứng dụng thực cần principal đáng tin, role/permission phù hợp và kiểm quyền trên tài nguyên kết quả.
- `completion.join()` chỉ ở console demo. Không đặt lệnh chờ này vào HTTP handler nếu mục tiêu là trả ngay để client polling.
- `ConcurrentHashMap` biến mất khi JVM dừng, không chia sẻ giữa các instance, không giải quyết queue/job bị mất khi restart, không lưu retry/cancellation. Bản demo minh họa **contract và trạng thái**, không hứa durability.

## 6. Contract HTTP cho tạo job và đọc kết quả

Tạo job và lấy kết quả cần endpoint khác nhau, dù cả hai có thể dùng cùng service phía sau. Ví dụ API minh họa (không thuộc chương trình Java ở phần trước):

**Tạo job:**

```http
POST /api/report-jobs HTTP/1.1
Content-Type: application/json
Idempotency-Key: request-2026-09-01-a

{"reportType":"sales","period":"2026-09","format":"CSV"}
```

Khi đã tiếp nhận để xử lý bất đồng bộ, một phản hồi thường dùng là HTTP **202 Accepted** kèm `Location` để client biết nơi xem tiến trình:

```http
HTTP/1.1 202 Accepted
Location: /api/report-jobs/8de3d341-a62b-4396-944a-c4b71226a111
Content-Type: application/json

{
  "jobId": "8de3d341-a62b-4396-944a-c4b71226a111",
  "state": "QUEUED",
  "statusUrl": "/api/report-jobs/8de3d341-a62b-4396-944a-c4b71226a111"
}
```

`202 Accepted` không đảm bảo task đã bắt đầu hoặc chắc chắn thành công. Nếu executor từ chối ngay và không có hàng đợi bền vững để nhận job, không giả vờ trả `202`; có thể dùng `503 Service Unavailable` hoặc `429 Too Many Requests` (tùy nguyên nhân) cùng `Retry-After`. Nếu API lưu job bền vững trước khi phát queue, trạng thái “đã nhận” vẫn có nghĩa ngay cả khi worker chưa sẵn sàng.

**Tra cứu tiến trình qua request khác:**

```http
GET /api/report-jobs/8de3d341-a62b-4396-944a-c4b71226a111 HTTP/1.1
Accept: application/json
```

Ví dụ trạng thái chưa kết thúc:

```json
{
  "jobId": "8de3d341-a62b-4396-944a-c4b71226a111",
  "state": "RUNNING",
  "submittedAt": "2026-10-29T03:00:00Z",
  "startedAt": "2026-10-29T03:00:02Z",
  "completedAt": null,
  "result": null,
  "error": null
}
```

Sau khi xử lý và commit kết quả:

```json
{
  "jobId": "8de3d341-a62b-4396-944a-c4b71226a111",
  "state": "SUCCEEDED",
  "submittedAt": "2026-10-29T03:00:00Z",
  "startedAt": "2026-10-29T03:00:02Z",
  "completedAt": "2026-10-29T03:00:05Z",
  "result": {
    "reportId": "report-784",
    "format": "CSV",
    "rowsProcessed": 1200
  },
  "error": null
}
```

Bản ghi thất bại nên có `state="FAILED"`, `result=null` và error dạng `{"code":"PROCESSING_ERROR","retryable":false}`, tránh ném nguyên stack trace cho client. Endpoint `GET` trả HTTP 200 khi đọc được *job resource* dù bản thân job báo `FAILED`; HTTP 500 phù hợp khi chính endpoint tra cứu bị lỗi server.

Về quyền truy cập: cần xác thực cả POST/GET/download/cancel. Với job không tồn tại hoặc không thuộc phạm vi caller, thường trả 404 để hạn chế tiết lộ job của tenant khác; áp dụng policy cụ thể. Đừng dùng `jobId` đoán khó như một cơ chế phân quyền duy nhất. Polling nên có giới hạn tần suất hoặc `Retry-After`; khi kết thúc có thể cung cấp URL tải artifact hay callback/webhook đã đăng ký và xác thực.

## 7. Đảm bảo job sống qua restart: database + queue/outbox

Chuyển một task sang `@Async` hoặc `ThreadPoolTaskExecutor` **chỉ tách thread**, không tự động tạo bền vững: khi process restart, hàng đợi trong RAM, map kết quả và task đang chạy đều có thể mất. Nếu hợp đồng yêu cầu “client vẫn tra cứu sau restart” hoặc “đã nhận thì sẽ được xử lý”, cần thay phần lưu tạm bằng kho bền vững.

Mô hình thường dùng:

```text
[API transaction]
  INSERT jobs(jobId, tenantId, input, QUEUED)
  INSERT outbox(eventId, jobId, JOB_CREATED)
  COMMIT
         |
[Outbox dispatcher] -- publish --> [durable broker/queue]
                                          |
                                      [Worker]
                                          |
                                claim job + process
                                          |
                            store result + terminal status
                                          |
                              GET status via database
```

Lưu job và outbox event **trong một transaction** tránh tình huống response báo thành công mà job không được ghi hoặc DB commit rồi sự kiện gửi queue bị mất. Dispatcher có thể publish lại sau crash; vì vậy worker vẫn phải **chịu được message trùng**, không mặc định exactly-once.

Gợi ý schema tối thiểu, ví dụ **PostgreSQL** (minh họa thiết kế, không phải schema bắt buộc):

```sql
CREATE TABLE background_job (
    job_id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    requested_by TEXT NOT NULL,
    job_type TEXT NOT NULL,
    input_schema_version INTEGER NOT NULL,
    input_json JSONB NOT NULL,
    state TEXT NOT NULL CHECK (
        state IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED',
                  'REJECTED', 'CANCEL_REQUESTED', 'CANCELLED')
    ),
    result_json JSONB,
    error_code TEXT,
    submitted_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    idempotency_key TEXT,
    expires_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_job_idempotency
    ON background_job (tenant_id, job_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
```

Một giao thức chuyển trạng thái an toàn có thể dùng câu lệnh có điều kiện:

```sql
UPDATE background_job
SET state = 'RUNNING',
    started_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE job_id = :job_id
  AND state = 'QUEUED'
  AND version = :expected_version;
```

Chỉ worker nhận được **một hàng được cập nhật** mới được quyền tiếp tục giai đoạn đã claim. Với job cần recovery khi worker chết giữa chừng, thêm `lease_owner`/`lease_until`, heartbeat và quy tắc reclaim sau lease hết hạn; khi ghi kết quả cần fencing token/attempt ID để worker cũ không ghi đè worker mới.

Các chi tiết dễ sai:

- **Đăng ký job trước khi phát lệnh chạy.** Với kiến trúc chỉ dùng executor trong memory, không có giao dịch nguyên tử giữa DB commit và `execute()`. Nếu cần bảo đảm sau crash, ưu tiên outbox + queue/poller.
- **Kết quả phải lưu trước trạng thái thành công**, hoặc ghi cả hai trong một transaction thống nhất. Tránh nhìn thấy `SUCCEEDED` trong khi artifact vẫn chưa tồn tại.
- **Duplicate delivery và retry:** giao dịch trên DB có thể bảo vệ chuyển trạng thái, nhưng side effect bên ngoài (gửi tiền, email, gọi API) cần idempotency key riêng hoặc quy trình bù trừ. Không thể suy exactly-once chỉ từ queue.
- **TTL và retention:** quy định lúc xóa input/result, cách xử lý `GET` sau khi job hết hạn, cách dọn file artifact và giảm dữ liệu nhạy cảm. Hạn chế lưu payload gốc nếu chỉ cần reference.
- **Multi-instance:** worker có thể ở pod khác với controller. Không lấy `ConcurrentHashMap` của một JVM làm nguồn trạng thái chung; sử dụng database hoặc cache có tính nhất quán phù hợp và nguồn chân lý rõ ràng.

Đây là bước tăng mức bảo đảm vận hành, **không bắt buộc** đối với mọi task nhỏ mang tính best-effort. Chọn kiến trúc dựa trên việc có chấp nhận mất job khi restart hay không.

## 8. Transaction, security và context trong Spring

Một HTTP request thường có những thứ “trông như có sẵn” nhưng chỉ đúng trong thread đang phục vụ nó:

| Thứ có trong request | Vấn đề khi chuyển sang worker | Cách thiết kế |
| --- | --- | --- |
| `HttpServletRequest`, session, request-scope bean | Request đã kết thúc hoặc context không được bind ở worker | Trích xuất dữ liệu cho command ngay ở boundary |
| `SecurityContextHolder` | Mặc định dùng thread-local; worker thread không tự có principal cùng request | Lưu identity đã xác thực ở command; nếu cần truyền context, dùng cơ chế được quản lý và vẫn kiểm quyền tài nguyên |
| `MDC`/trace context | Log worker có thể thiếu correlation; pool tái sử dụng thread | Chụp context lúc gửi, phục hồi đúng scope và clear/restore trong `finally` |
| `@Transactional` của controller | Transaction gắn với thread/luồng gọi; không tự bao phủ worker | Mở transaction trong service worker để load/update dữ liệu |
| JPA entity/`EntityManager` | Lazy loading/managed state có thể hết hiệu lực khi đổi thread | Truyền ID/phiên bản rồi load lại trong transaction mới |
| `MultipartFile`/stream | Nội dung/stream có thể không còn đọc được sau request | Lưu object vào storage trước và truyền storage key |
| `LocaleContextHolder`/timezone | Không tự có context bản địa hóa của caller | Truyền locale/timezone hợp lệ nếu nghiệp vụ cần |

`TaskDecorator` của Spring có thể hỗ trợ context propagation, nhưng **không thay thế job input bền vững**. Với `@Async`, lời gọi cùng bean kiểu `this.doAsync()` thường bỏ qua Spring proxy trong chế độ proxy mặc định, nên có thể chạy đồng bộ. Đảm bảo gọi qua bean/proxy đúng, cấu hình executor có giới hạn rõ ràng và theo dõi lỗi từ future hoặc exception handler phù hợp. `@Async void` không trả exception cho caller qua future; không dùng nó làm cơ chế duy nhất xác định `FAILED` nếu cần truy vấn trạng thái.

Nếu chuyển identity tới worker để thực hiện thao tác dưới danh nghĩa người dùng, phải có chính sách rõ ràng: chụp quyền tại lúc enqueue hay tái kiểm quyền khi thực thi; nếu người dùng bị thu hồi quyền giữa lúc chờ, hành vi hệ thống phải được định nghĩa. Không lưu token truy cập hoặc toàn bộ security context dài hạn trong job input.

## 9. Idempotency, retry, timeout và hủy

Request HTTP có thể bị timeout **sau khi server đã tạo job**. Client gửi lại cùng POST có thể vô tình tạo hai job. Dùng `Idempotency-Key` do client sinh cho một ý định và lưu duy nhất theo `tenantId + jobType + key`, kèm hash của payload để từ chối trường hợp dùng lại key nhưng nội dung khác. Khi retry cùng key và payload, trả **cùng jobId/trạng thái** theo contract, không tạo bản thứ hai.

Với message queue, redelivery có thể tạo nhiều worker attempt. Tách `jobId` và `attemptId`; chỉ một attempt giữ claim hợp lệ tại một thời điểm. Nếu job thao tác hệ thống ngoài DB, truyền key idempotent tới hệ thống đích hoặc lưu ledger giao dịch để ngăn side effect lặp. Nếu không thể đảm bảo, nêu rõ delivery có thể là at-least-once.

Timeout có ít nhất ba nghĩa: HTTP request timeout (caller dừng đợi), job deadline (nghiệp vụ quá thời hạn) và worker lease timeout (không còn heartbeat). Ba thứ không đồng nghĩa. Hủy cũng là một giao thức:

```text
RUNNING -- requestCancel() --> CANCEL_REQUESTED
                          |
                worker quan sát tín hiệu,
                dừng an toàn / cleanup
                          |
                          v
                      CANCELLED
```

`Future.cancel(true)` chỉ **yêu cầu** interrupt; task blocking hoặc thư viện không hỗ trợ interrupt có thể tiếp tục chạy. Đừng phát trạng thái `CANCELLED` và đồng thời cho phép worker commit side effect cuối mà không có rào chắn. Dùng token hủy, kiểm tra điểm an toàn, compare-and-set trước commit và reconciliation.

## 10. Kiểm thử contract trước khi triển khai

Dùng `CountDownLatch`/executor kiểm soát lịch chạy khi test concurrency; tránh phụ thuộc vào `Thread.sleep`. Các bài kiểm thiết yếu:

| Kịch bản | Kết quả mong đợi |
| --- | --- |
| Input thiếu, sai enum, quá lớn | Reject tại HTTP/service boundary, chưa tạo job |
| Client gửi, đóng kết nối ngay | Worker vẫn dùng command độc lập; có thể tra cứu job bằng request mới |
| Caller sửa collection sau submit | Worker đọc snapshot bất biến, không bị đổi nội dung |
| Task chờ queue lâu | `QUEUED` trước khi worker claim; sau đó `RUNNING`; thời gian bắt đầu thực tế không lấy từ lúc submit |
| Task thành công | `SUCCEEDED` chỉ sau khi result sẵn sàng; result đọc được ở scope hợp lệ |
| Worker ném lỗi | `FAILED`, error code sạch, result không giả thành công |
| Pool/queue bị từ chối | `REJECTED` hoặc HTTP 429/503 theo hợp đồng; không nói job chạy rồi |
| Hai callback/attempt cạnh tranh | Một claim thắng; terminal state không bị ghi ngược |
| Gửi lại cùng idempotency key | Một jobId; payload khác cùng key bị xử lý như xung đột |
| GET job tenant khác | Không lộ input, trạng thái, kết quả hoặc sự tồn tại theo chính sách |
| Restart worker khi đang chạy | Job vẫn tra cứu được với kho bền vững; lease/reconciliation xác định có retry |
| Timeout/cancel | Không nhầm client thôi đợi với worker đã dừng; không có commit side effect trễ bất hợp lệ |

Các lỗi thường gặp là: truyền thẳng `HttpServletRequest` vào `@Async`; giữ `EntityManager` hoặc lazy entity theo request; lưu job chỉ trong static map nhưng quảng bá “xử lý bền vững”; coi `Future.isDone()` là thành công; ghi `SUCCEEDED` trước khi kết quả commit; dùng `jobId` làm bí mật truy cập; và không phân biệt retry HTTP với retry worker.

## 11. Checklist thiết kế

Trước khi phát hành một luồng background job, phải trả lời được:

1. Input DTO có thể serialize/validate và được tạo từ dữ liệu đã xác thực mà không giữ tài nguyên request hay không?
2. Một job được nhận từ thời điểm nào, có `jobId`/idempotency policy rõ ràng và tồn tại ở đâu khi request kết thúc?
3. Trạng thái nào là terminal, ai được phép chuyển trạng thái, và điều gì ngăn callback/attempt đến muộn ghi đè?
4. Kết quả có kiểu/schema, điểm commit và quyền truy cập xác định không? Lỗi có code an toàn cho client không?
5. Cần bảo đảm best-effort trong RAM hay cần DB/queue/outbox để sống qua restart và multi-instance?
6. Transaction của worker, validation, authorization, trace context và retention đã tách khỏi thread request hay chưa?
7. Đã kiểm chứng queue đầy, job lỗi, request mất kết nối, retry trùng, hủy và crash chưa?

Khi ba contract **input – state – result** tồn tại độc lập, Spring concurrency trở thành chi tiết thực thi phía sau một API job rõ nghĩa: HTTP có thể kết thúc, worker có thể đổi thread hoặc đổi instance, nhưng client vẫn có cách hợp lệ và nhất quán để theo dõi công việc.

### Tài liệu tham khảo

- Spring Framework Reference — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework Javadoc — `ThreadPoolTaskExecutor`: https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Spring Framework Reference — Proxying Mechanisms (`@Async` và self-invocation): https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework Javadoc — `ContextPropagatingTaskDecorator`: https://docs.spring.io/spring-framework/docs/6.1.16/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="design-practice-003">Thực hành nhóm luồng hữu hạn cho tác vụ tạo báo cáo</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành nhóm luồng hữu hạn cho tác vụ tạo báo cáo trong Spring Framework

> **Ngữ cảnh:** Spring Framework / Concurrency → Thiết kế và thực hành. Bài thực hành xây dựng một hệ thống tạo báo cáo chạy nền bằng nhóm luồng có giới hạn, kiểm soát cả số worker lẫn số tác vụ chờ, xử lý quá tải, lỗi và shutdown. Ví dụ nền tảng dùng Java 17+; phần tích hợp dùng Spring Framework 6.x (phù hợp các ứng dụng Spring Boot 3.x).

## 1. Bài toán và tiêu chí hoàn thành

Giả sử người dùng yêu cầu xuất báo cáo CSV. Mỗi yêu cầu có mã `reportId`, phải đọc dữ liệu, tính tổng, tạo file/kết quả và thông báo trạng thái. Tác vụ có thể mất vài giây đến vài phút; không nên giữ HTTP request thread chờ toàn bộ việc xuất file và không thể khởi tạo một platform thread mới không giới hạn cho mỗi yêu cầu.

Một thiết kế thực tế gồm các thành phần:

```text
Client/API
   │ yêu cầu tạo báo cáo
   ▼
Admission / kiểm tra hạn mức
   │ ghi nhận jobId, trạng thái QUEUED
   ▼
Bộ điều phối → reportExecutor (pool + queue đều hữu hạn)
                   │
                   ├─ worker-1 ── đọc dữ liệu → ghi báo cáo
                   ├─ worker-2 ── đọc dữ liệu → ghi báo cáo
                   ├─ ...
                   └─ queue hữu hạn: tác vụ đang chờ
                      │ đầy + hết worker cho phép → REJECT
   ▼
Trạng thái READY / FAILED / REJECTED
   ▼
API tra cứu trạng thái và tải báo cáo
```

**Tiêu chí cần bảo đảm:**

1. Số worker đồng thời không vượt `maxPoolSize`; queue không vượt `queueCapacity`.
2. Khi đầy cả worker và queue, hệ thống **có phản hồi quá tải rõ ràng**, không âm thầm bỏ tác vụ.
3. Kết quả hoặc lỗi của task có thể được quan sát; mỗi job có danh tính, thời hạn chờ và trạng thái riêng.
4. Những job tạo báo cáo dài không làm chiếm hết mọi thread của ứng dụng (HTTP, scheduler, tác vụ giao dịch ngắn).
5. Có kiểm thử xác định được trường hợp pool bão hòa mà không dùng `Thread.sleep()` làm cơ chế đồng bộ.
6. Khi dừng ứng dụng, quy tắc hoàn tất/hủy/khôi phục job được xác định trước; không coi nội dung queue trong RAM là nơi lưu trữ bền vững.

Ở phần chạy mẫu, ta dùng báo cáo CSV nhỏ để kiểm tra điều phối; ở production, thường dùng luồng đọc theo trang và ghi ra file/object storage, chỉ lưu đường dẫn kết quả trong trạng thái job.

## 2. Hiểu đúng pool hữu hạn và queue hữu hạn

`ThreadPoolExecutor` và `ThreadPoolTaskExecutor` của Spring dùng các khái niệm sau:

| Tham số | Vai trò | Minh họa |
| --- | --- | --- |
| `corePoolSize` | Mức worker cơ bản được tạo dần khi có việc | `2` |
| `maxPoolSize` | Trần platform thread trong pool | `4` |
| `queueCapacity` | Số Runnable/Future task **đang đợi** tối đa trong queue | `8` |
| `keepAliveSeconds` | Thời gian worker vượt quá core được phép rảnh trước khi thu hồi | `30` |
| `threadNamePrefix` | Nhận diện worker trong log/trace/dump | `report-` |
| Rejection policy | Quy tắc khi không còn chỗ nhận task hoặc pool đang shutdown | `AbortPolicy` |

Với cấu hình `core=2`, `max=4`, `queue=8` và các tác vụ đang thực sự bị chặn:

1. Hai tác vụ đầu có thể làm pool tăng tới hai core thread.
2. Những tác vụ tiếp theo được **đưa vào queue** đến khi queue có tám phần tử.
3. Chỉ khi queue đầy, các yêu cầu kế tiếp mới làm pool tăng từ hai lên tối đa bốn thread.
4. Nếu bốn worker đều bận và queue còn đủ tám tác vụ, yêu cầu tiếp theo bị từ chối theo policy.

Trong trạng thái bão hòa ổn định, tối đa **4 đang chạy + 8 đang xếp hàng = 12 tác vụ được executor giữ**. Đây là giới hạn tại executor, không tính các yêu cầu chưa được gửi vào executor, job đã lưu trong database hoặc những producer khác. Do lịch chạy cạnh tranh, không nên khẳng định "12 tác vụ đầu luôn được nhận" cho mọi interleaving; tiêu chí chính là **không vượt trần và từ chối rõ ràng**.

Một số điểm thường bị hiểu sai:

- `maxPoolSize=4` **không** có nghĩa executor lập tức chạy bốn thread. Với queue dương, executor ưu tiên queue sau khi đủ hai core thread; chỉ tăng vượt core khi queue đầy.
- `Executors.newFixedThreadPool(4)` giới hạn số thread xử lý, **nhưng dùng queue không giới hạn**. Nếu producer nhanh hơn worker lâu dài, hàng chờ vẫn có thể tăng đến khi cạn bộ nhớ.
- `ThreadPoolTaskExecutor` mặc định cũng có queue rất lớn; phải cấu hình `queueCapacity` hữu hạn để có backpressure thực sự.
- `setQueueCapacity(0)` sử dụng cơ chế bàn giao trực tiếp (không trữ task trong queue); cần đặt `maxPoolSize` phù hợp vì pool có thể mở rộng nhanh khi tải tăng.
- Không dùng `DiscardPolicy`/`DiscardOldestPolicy` cho báo cáo cần bảo toàn kết quả: tác vụ có thể mất mà caller tưởng vẫn được xử lý.
- `CallerRunsPolicy` khiến thread **gửi task** phải chạy công việc khi pool bão hòa. Nó tạo áp lực ngược nhưng có thể khiến HTTP request thread chạy một báo cáo kéo dài, trái mục tiêu tách tác vụ; chỉ chọn khi đã hiểu và chấp nhận hệ quả này.

**Câu hỏi thiết kế:** giới hạn worker bảo vệ CPU/connection/IO; giới hạn queue bảo vệ bộ nhớ và độ trễ. Một queue lớn làm người dùng phải chờ lâu hơn mà không tự tăng throughput.

## 3. Chứng minh pool hữu hạn bằng Java 17: trường hợp bão hòa xác định

Chương trình dưới đây chỉ dùng JDK 17, tạo pool `core=max=2` và queue sức chứa `1`. Hai worker đầu được giữ bằng `CountDownLatch`; một tác vụ thứ ba vào queue; tác vụ thứ tư bị từ chối. Điểm dừng có chủ đích bảo đảm kiểm tra không phụ thuộc tốc độ máy.

Lưu tệp `BoundedReportPoolDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BoundedReportPoolDemo {
    static Callable<String> heldReport(
            int id,
            CountDownLatch started,
            CountDownLatch release,
            AtomicInteger active,
            AtomicInteger peak) {

        return () -> {
            int current = active.incrementAndGet();
            peak.accumulateAndGet(current, Math::max);
            started.countDown();

            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release timeout");
                }
                return "R" + id; // Mô phỏng một báo cáo đã tạo
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } finally {
                active.decrementAndGet();
            }
        };
    }

    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());

        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        List<Future<String>> running = new ArrayList<>();

        try {
            running.add(pool.submit(
                    heldReport(1, started, release, active, peak)));
            running.add(pool.submit(
                    heldReport(2, started, release, active, peak)));

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("workers did not start");
            }

            Future<String> queued = pool.submit(() -> "R3");

            boolean rejected = false;
            try {
                pool.submit(() -> "R4");
            } catch (RejectedExecutionException expected) {
                rejected = true;
            }

            if (!rejected || active.get() != 2
                    || pool.getQueue().size() != 1) {
                throw new AssertionError("capacity rule violated");
            }
            System.out.println("active=2 queued=1 rejected=true");

            release.countDown();

            TreeSet<String> results = new TreeSet<>();
            for (Future<String> future : running) {
                results.add(future.get(5, TimeUnit.SECONDS));
            }
            results.add(queued.get(5, TimeUnit.SECONDS));

            if (peak.get() != 2) {
                throw new AssertionError("unexpected peak concurrency");
            }
            System.out.println("reports=" + String.join(",", results));
            System.out.println("peak=" + peak.get());
        } finally {
            release.countDown(); // Luôn giải phóng worker kể cả khi assert lỗi
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError("pool did not terminate");
            }
        }
    }
}
```

Chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 BoundedReportPoolDemo.java
java BoundedReportPoolDemo
```

Stdout chính xác khi thành công:

```text
active=2 queued=1 rejected=true
reports=R1,R2,R3
peak=2
```

**Giải thích kiểm thử:**

- `started.await(...)` chỉ trả về thành công khi **cả hai worker đã tới điểm chặn**. Cả hai giữ thread cho đến khi `release.countDown()`.
- Vì không có worker rảnh và queue chứa đúng `R3`, `submit(R4)` tất yếu ném `RejectedExecutionException` với `AbortPolicy`.
- `Future.get(timeout)` vừa đợi task xong vừa chuyển exception của worker về test. `TreeSet` chỉ giúp stdout có thứ tự ổn định; thứ tự worker hoàn thành không được giả định.
- `finally` mở cổng và shutdown pool, tránh để tiến trình treo nếu một assertion thất bại.
- Đây là cách chứng minh giới hạn, **không** là chương trình benchmark: kết quả tốc độ/throughput cần đo dưới tải thực tế.

## 4. Cấu hình executor riêng cho báo cáo trong Spring

Trong ứng dụng Spring Framework 6.x đã có Spring Context, đặt bean này ở package được quét hoặc import vào cấu hình. Ví dụ sử dụng platform threads; các giá trị là điểm xuất phát để đo và điều chỉnh, **không phải thông số tối ưu chung**.

Tệp `ReportExecutorConfig.java`:

```java
package example.reports;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class ReportExecutorConfig {
    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("report-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
```

`ThreadPoolTaskExecutor` là bean do Spring quản lý: framework khởi tạo và quản lý vòng đời executor. Không tự `new ThreadPoolTaskExecutor()` tại mỗi request, vì như vậy mỗi request lại có một pool mới và tổng số thread không được giới hạn. Chỉ dùng một bean có tên `reportExecutor` cho nhóm công việc này.

`@EnableAsync` kích hoạt xử lý `@Async`. `@Async("reportExecutor")` chọn **đích danh** executor riêng; nếu bỏ tên, Spring sẽ tìm executor mặc định theo cấu hình, dễ làm báo cáo tranh chấp với những tác vụ không liên quan.

Về thứ tự triển khai: `queueCapacity=8` tạo queue hữu hạn, do đó `maxPoolSize=4` mới có cơ hội phát huy khi queue đầy. Một cấu hình `max=4` với queue không giới hạn thường chỉ vận hành gần mức `core=2` dù còn khả năng tăng worker.

### Chính sách khi đầy

Với `AbortPolicy`, việc gửi task lúc pool đầy gây lỗi tại thời điểm submission. Qua giao diện Spring `TaskExecutor`, thường gặp `TaskRejectedException`. Một ứng dụng có thể chuyển thành HTTP `503 Service Unavailable` cho tình huống quá tải tạm thời, kèm hướng dẫn retry; dùng `429 Too Many Requests` khi đó là hạn mức của một caller/tenant. Đừng bắt exception rồi trả về `202 Accepted` nếu job thực tế không được nhận.

### Shutdown có chủ đích

Báo cáo đang chạy có thể bị gián đoạn khi ứng dụng dừng. `ThreadPoolTaskExecutor` cung cấp `setWaitForTasksToCompleteOnShutdown(boolean)` và `setAwaitTerminationSeconds(int)`; lựa chọn phụ thuộc thời gian shutdown của hệ thống và độ tin cậy yêu cầu. Chờ các task trong RAM chạy xong **không bảo đảm** dữ liệu sống sót sau kill, crash hoặc máy mất điện. Với job quan trọng phải có trạng thái bền vững, cơ chế resume/retry/idempotency và nơi lưu kết quả bên ngoài tiến trình.

Không tự gán `setWaitForTasksToCompleteOnShutdown(true)` ở mọi nơi: lựa chọn này còn ảnh hưởng thời điểm executor nhận task khi context đóng và có thể kéo dài shutdown; phải thống nhất với shutdown grace period của Spring/đơn vị triển khai.

## 5. Một worker Spring `@Async` tạo báo cáo CSV nhỏ

Ví dụ sau tạo nội dung CSV từ `rowCount` dòng giả lập để minh họa **đường đi của kết quả và pool thread**. Nó hoạt động trong Spring context với cấu hình ở phần 4. Đối với báo cáo lớn, thay `StringBuilder` bằng writer streaming tới file/object storage và để `ReportResult` chỉ chứa đường dẫn/kích thước/checksum.

Tệp `ReportWorker.java`:

```java
package example.reports;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ReportWorker {
    public record ReportRequest(String reportId, int rowCount) {}

    public record ReportResult(
            String reportId,
            int rowCount,
            long totalCents,
            String csv,
            String workerThread) {}

    @Async("reportExecutor")
    public CompletableFuture<ReportResult> generate(ReportRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.reportId() == null || request.reportId().isBlank()
                || request.rowCount() < 1 || request.rowCount() > 100) {
            throw new IllegalArgumentException("invalid report request");
        }

        StringBuilder csv = new StringBuilder("row,amountCents\n");
        long total = 0;

        for (int i = 1; i <= request.rowCount(); i++) {
            long cents = i * 100L;
            total += cents;
            csv.append(i).append(',').append(cents).append('\n');
        }

        ReportResult report = new ReportResult(
                request.reportId(),
                request.rowCount(),
                total,
                csv.toString(),
                Thread.currentThread().getName());

        return CompletableFuture.completedFuture(report);
    }
}
```

**Diễn biến:** code gọi `generate()` qua bean do Spring inject → proxy nhận lời gọi → gửi việc vào `reportExecutor` → worker thực thi phương thức → hoàn thành `CompletableFuture<ReportResult>` mà caller nhận được. Bên trong method, `completedFuture(report)` là đối tượng kết quả cho adapter async của Spring; gọi method qua proxy mới tạo ra việc dispatch sang executor.

Ví dụ deliberately nhỏ và nhanh: không dùng việc xử lý 100 dòng giả lập để suy đoán hiệu năng thực tế. Nếu muốn kiểm thử thời điểm worker đang bận, dùng latch như phần 3 thay vì tăng `rowCount` hoặc chèn `sleep`.

### Kiểm thử tích hợp Spring và JUnit 5

Tệp `ReportWorkerTest.java` (cùng package). Test yêu cầu `spring-test`, Spring Context và JUnit Jupiter trên test classpath. Trong dự án Spring Boot, thường có thể dùng starter test phù hợp BOM đang dùng; Gradle/Maven phải bật JUnit Platform.

```java
package example.reports;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(classes = {
        ReportExecutorConfig.class,
        ReportWorker.class
})
class ReportWorkerTest {
    @Autowired
    ReportWorker worker;

    @Test
    void createsCsvOnReportExecutor() throws Exception {
        ReportWorker.ReportRequest request =
                new ReportWorker.ReportRequest("R-42", 3);

        ReportWorker.ReportResult result =
                worker.generate(request).get(5, TimeUnit.SECONDS);

        assertEquals("R-42", result.reportId());
        assertEquals(3, result.rowCount());
        assertEquals(600L, result.totalCents());
        assertEquals(
                "row,amountCents\n1,100\n2,200\n3,300\n",
                result.csv());
        assertTrue(result.workerThread().startsWith("report-"));
    }
}
```

Các assertion chứng minh giá trị báo cáo và việc code chạy trên worker có prefix được cấu hình. `get(5, SECONDS)` có timeout để test thất bại có thông tin nếu task không hoàn thành. Test thành công do JUnit báo **passed**, không có stdout cố định. Tên worker có hậu tố do Spring tạo ra nên chỉ kiểm tra prefix; không assert số thứ tự thread.

**Quan trọng:** gọi `this.generate(...)` bên trong chính `ReportWorker` sẽ bypass proxy ở chế độ `@Async` proxy mặc định, dẫn tới chạy đồng bộ. Tách worker thành bean riêng và gọi từ controller/service khác qua bean được inject. Không đặt logic kỳ vọng `@Async` chạy trong constructor hay `@PostConstruct`.

## 6. Điều phối nhiều báo cáo mà không dồn vô hạn task vào queue

Một lỗi thiết kế phổ biến là:

```java
// Ví dụ KHÔNG NÊN áp dụng cho batch không giới hạn:
for (ReportRequest request : hugeBatch) {
    futures.add(reportWorker.generate(request));
}
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```

Với executor hữu hạn, cách này hoặc gây rejection hàng loạt, hoặc nếu dùng queue không giới hạn sẽ giữ quá nhiều task, request object và Future trong RAM. Caller còn đang tạo `futures` chưa có giới hạn. Nếu queue giới hạn và `AbortPolicy`, phản hồi quá tải phải được xử lý ngay lúc gọi `generate`.

Có thể áp dụng **cửa sổ in-flight**: giữ tối đa `W` tác vụ đã gửi nhưng chưa thu kết quả; chỉ gửi tiếp khi một tác vụ hoàn tất. Đoạn Java 17 sau là **phương thức tái sử dụng** cần đặt trong class và cung cấp `ExecutorService` phù hợp; nó không phải chương trình có `main` độc lập. Ví dụ dành cho batch hữu hạn trong một nguồn submission được kiểm soát.

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public final class BoundedBatch {
    private BoundedBatch() {}

    public static <T, R> List<R> run(
            List<T> jobs,
            int window,
            ExecutorService executor,
            Function<T, R> work)
            throws InterruptedException, ExecutionException,
                   TimeoutException {

        if (window < 1) {
            throw new IllegalArgumentException("window must be positive");
        }

        ExecutorCompletionService<R> completed =
                new ExecutorCompletionService<>(executor);
        List<R> results = new ArrayList<>(jobs.size());

        int submitted = 0;
        int received = 0;

        while (received < jobs.size()) {
            while (submitted < jobs.size()
                    && submitted - received < window) {
                T job = jobs.get(submitted);
                completed.submit(() -> work.apply(job));
                submitted++;
            }

            Future<R> ready = completed.poll(30, TimeUnit.SECONDS);
            if (ready == null) {
                throw new TimeoutException("no report completed in 30s");
            }

            results.add(ready.get());
            received++;
        }
        return results; // Theo thứ tự hoàn thành, không phải thứ tự input
    }
}
```

Nếu `window=4` và batch này là producer duy nhất của một pool có tối thiểu bốn chỗ xử lý, số task chưa nhận kết quả không vượt bốn. Tuy nhiên `window` **không thay thế** `queueCapacity`: nếu nhiều producer dùng chung executor, queue có thể vẫn đầy và submission vẫn có thể bị từ chối. Đoạn mã minh họa cách giữ số task in-flight; trong production cần thêm chính sách xử lý `RejectedExecutionException`, hủy các Future còn chạy khi batch thất bại, giới hạn tổng thời gian và ghi kết quả theo luồng nếu batch quá lớn.

`ExecutorCompletionService` trả các kết quả theo thứ tự hoàn thành; nếu thứ tự báo cáo quan trọng, dùng khóa `reportId` để ghép lại, không giả định job được xử lý theo thứ tự submit. Nếu ứng dụng yêu cầu kết quả từng job được lưu ngay, nên ghi từng kết quả vào kho trạng thái thay vì giữ toàn bộ `List<R>` trong bộ nhớ.

### Không để worker đợi worker trong cùng pool

Ví dụ có bốn worker, mỗi worker lại gửi một task con vào **cùng pool** rồi gọi `join()`/`get()` chờ task con. Nếu bốn worker đều chờ, các task con nằm trong queue và không có thread rảnh để chạy: thread starvation deadlock. Cách xử lý là tạo graph công việc từ lớp điều phối, kết hợp các Future theo kiểu không chặn worker; hoặc cấp tài nguyên/nhóm executor khác một cách có chủ đích. Tăng `maxPoolSize` ngẫu nhiên chỉ che triệu chứng.

## 7. Tích hợp với API nhận yêu cầu và trạng thái job

Trong ứng dụng HTTP, gửi yêu cầu tạo báo cáo nên tách **admission** và **result**:

1. Validate input, quyền người dùng, kích thước báo cáo và quota.
2. Tạo `jobId`, ghi bền vững thông tin `QUEUED` cùng idempotency key. Nếu worker truy cập bản ghi đó, phải bảo đảm giao dịch tạo job đã commit trước khi worker đọc.
3. Submit task vào executor; **catch `TaskRejectedException` tại điểm submission**. Nếu bị từ chối, cập nhật `REJECTED`/`RETRY_LATER`; trả response quá tải có chủ đích, không trả `202` thành công giả.
4. Khi worker thực sự bắt đầu, ghi `RUNNING`; khi thành công ghi `READY` với location của output; khi exception ghi `FAILED` với mã lỗi đủ để chẩn đoán (tránh lộ dữ liệu nhạy cảm).
5. `GET /reports/{jobId}` tra trạng thái; khi `READY`, cấp link tải đúng quyền, có TTL nếu phù hợp.

```text
QUEUED ── accepted by worker ──► RUNNING ── success ──► READY
  │                                │
  │ rejection                      ├─ error ─────► FAILED
  ▼                                └─ cancel ────► CANCELED
REJECTED
```

**Phân biệt failure ở hai thời điểm:**

- **Submission thất bại:** `@Async` proxy có thể ném `TaskRejectedException` ngay lúc caller gọi worker; lúc đó không có future hợp lệ để chờ.
- **Task chạy rồi thất bại:** caller có `CompletableFuture<ReportResult>`, nhưng future hoàn tất ngoại lệ. Kiểm tra bằng `get`/`whenComplete`/`handle`; nếu dùng `@Async void`, exception không được truyền về caller qua future và cần `AsyncUncaughtExceptionHandler` hoặc cơ chế cập nhật trạng thái riêng.

Trong code điều phối, logic khái niệm là:

```java
// Minh họa ranh giới điều phối; jobStore là interface của ứng dụng.
jobStore.markQueued(jobId);
try {
    CompletableFuture<ReportWorker.ReportResult> future =
            reportWorker.generate(request); // Có thể bị từ chối ngay
    future.whenComplete((result, error) -> {
        if (error != null) {
            jobStore.markFailed(jobId, error);
        } else {
            jobStore.markReady(jobId, result);
        }
    });
} catch (org.springframework.core.task.TaskRejectedException ex) {
    jobStore.markRejected(jobId);
    throw ex;
}
```

Đoạn này **không độc lập biên dịch**: cần lớp bao, `jobId`, `request`, `reportWorker` và interface `jobStore` của ứng dụng. Nó chỉ minh họa chỗ phải xử lý hai loại lỗi. Trong triển khai thật, ghi nhận trạng thái phải có transaction/compare-and-set/idempotency để các callback đến muộn, retry trùng hoặc nhiều instance không ghi lùi trạng thái. Khi công việc cần đảm bảo giao nhận kể cả khi server crash, dùng hàng đợi bền vững hoặc transactional outbox thay vì phụ thuộc queue trong bộ nhớ của executor.

HTTP `202 Accepted` có nghĩa yêu cầu đã **được chấp nhận để xử lý**, không phải đã tạo CSV xong; nên trả `jobId`/URL tra cứu. Thời điểm worker hoàn thành không được suy luận từ việc response `202` đã gửi.

## 8. Giới hạn tài nguyên phải tính xuyên suốt pipeline

Bốn report worker không đồng nghĩa chỉ có bốn đơn vị tải:

- **CPU-bound:** tổng hợp, nén hoặc render PDF nặng có thể dùng nhiều CPU. Nhiều worker hơn số CPU có thể làm tăng context switching và giảm throughput.
- **I/O-bound:** đọc database, gọi API, lưu object storage có thể dành nhiều thời gian chờ. Có thể cho phép nhiều worker hơn, nhưng đồng thời bị ràng buộc bởi connection pool, rate limit và băng thông.
- **Bộ nhớ:** mỗi task đang chạy và đang queue giữ closure, tham số và dữ liệu. Queue 8 phần tử không giúp an toàn nếu mỗi phần tử giữ hàng trăm MB dữ liệu.
- **Database:** nếu report có thể giữ connection trong thời gian dài, pool bốn worker cùng những HTTP request khác có thể làm cạn connection pool. Đọc theo page, giữ transaction ngắn, không mở transaction xuyên cả quá trình ghi file.
- **Disk/object storage:** output phải được ghi có giới hạn, kiểm tra dung lượng, dọn file tạm khi lỗi, lưu metadata kết quả và thiết lập vòng đời xóa phù hợp.
- **Đa instance:** mỗi instance có pool riêng. Nếu có 5 instance và mỗi instance `max=4`, hệ thống có thể có tới 20 report worker; cần quota/tài nguyên **toàn cụm** nếu database hoặc downstream chỉ chịu được 8 request.

Một cách hiệu chỉnh dựa trên đo đạc:

1. Đo thời gian chờ queue, thời gian chạy, CPU/memory peak, connection và tỷ lệ lỗi ở mức tải thực tế.
2. Đặt worker limit dựa trên tài nguyên nghẽn, không chỉ dựa trên số request tới.
3. Đặt queue limit theo **độ trễ chờ tối đa chấp nhận được**. Nếu tốc độ tiếp nhận thường xuyên lớn hơn tốc độ hoàn tất, queue cuối cùng vẫn đầy; tăng queue chỉ làm trì hoãn rejection.
4. Bật metric/alert theo hàng chờ, active thread, thời gian làm báo cáo và rejection; thử burst load có giám sát.
5. Tách nhóm report nặng khỏi tác vụ nhanh để tránh head-of-line blocking; nếu báo cáo rất dài hoặc có SLA, cân nhắc worker queue bền vững và service riêng.

Có thể lấy snapshot monitor từ bean:

```java
// Ví dụ quan sát, không phải phép kiểm chứng logic đồng bộ.
int active = reportExecutor.getActiveCount();
int currentThreads = reportExecutor.getPoolSize();
int waiting = reportExecutor.getThreadPoolExecutor()
                            .getQueue().size();
long completed = reportExecutor.getThreadPoolExecutor()
                               .getCompletedTaskCount();
```

Các số trên là snapshot tại thời điểm gọi, thay đổi giữa các lệnh đọc. `completed` không tự cho biết task thành công hay lỗi; cần metric kết quả nghiệp vụ và rejection. Trong test bão hòa cần dùng latch để giữ trạng thái trước khi assert queue/active count.

## 9. Timeout, cancellation, transaction và context

### Timeout và dừng tác vụ

- `future.get(30, SECONDS)` chỉ giới hạn **thời gian caller chờ**; nếu quá hạn, worker có thể vẫn đang chạy.
- `CompletableFuture.orTimeout(...)` làm future hoàn tất ngoại lệ theo thời gian, nhưng không bảo đảm interrupt/tự dừng code đang chạy.
- `cancel(true)` là yêu cầu hủy tốt nhất có thể; worker phải kiểm tra interrupt, IO timeout và điều kiện dừng. Một tác vụ không hợp tác có thể tiếp tục chạy.
- Đặt timeout ở cả truy vấn DB, HTTP client, remote storage và thời gian thực thi tổng. Mỗi job cần deadline, khả năng retry có kiểm soát và cách dọn file tạm.

Đừng để request thread `join()` vô hạn chỉ để chờ report. Đối với API trạng thái job, thường kết thúc request nhanh và cho client polling hoặc dùng cơ chế thông báo khác.

### Transaction và dữ liệu

`@Transactional` trong thread nhận HTTP không tự di chuyển sang thread của `@Async`. Worker cần transaction riêng cho bước đọc/ghi cần atomicity. Nếu submit trước khi transaction tạo job commit, worker có thể đọc `jobId` chưa tồn tại. Có thể dùng after-commit event, outbox hoặc điều phối sau commit để giữ thứ tự.

Nếu retry sau timeout/restart, thao tác tạo file và cập nhật trạng thái phải idempotent theo `jobId` và phiên bản. Dùng đường dẫn output duy nhất hoặc giao thức tạo tạm → ghi đầy đủ → publish/rename, tránh hiển thị file nửa chừng; xử lý duplicate delivery trong môi trường nhiều worker.

### Security/logging context

`ThreadLocal`, MDC, security context, locale và request scope không tự động được truyền một cách tổng quát qua worker thread. Không chuyển thẳng HttpServletRequest hay request-scoped bean vào task chạy nền. Chỉ truyền dữ liệu cần thiết có ranh giới rõ ràng (user ID, tenant ID, quyền được xác nhận, correlation ID), và thiết lập/giải phóng context đúng vòng đời. Spring cung cấp `TaskDecorator` để hỗ trợ context propagation có kiểm soát; tránh rò context từ job trước sang job sau.

### Shutdown và độ tin cậy

Khi container nhận tín hiệu dừng, ứng dụng cần ngừng nhận job mới, quyết định có chờ job đang chạy không, ghi lại trạng thái còn dang dở và giải phóng tài nguyên. `ThreadPoolTaskExecutor` là bộ điều phối trong **một tiến trình**; nó không tự cung cấp durable job queue, exactly-once execution hoặc tự khởi động lại job sau crash. Nếu yêu cầu bắt buộc không mất job, lưu nguồn công việc bền vững trước khi dispatch và có bộ xử lý khôi phục/reconcile.

## 10. Kiểm thử đúng các ranh giới

Các bài test quan trọng, tương ứng với tiêu chí ở phần 1:

| Trường hợp | Cách kích hoạt xác định | Điều cần assert |
| --- | --- | --- |
| Report thành công | Gọi bean `@Async` và chờ `get(timeout)` | CSV/tổng đúng; worker prefix đúng |
| Pool bão hòa | Hai worker giữ latch + queue một phần tử | Submit mới bị reject; không vượt pool/queue |
| Worker lỗi | Dữ liệu gây exception trong worker | Future lỗi; job `FAILED`; không trả `READY` |
| Worker không phản hồi | Fake datasource chặn ở checkpoint | Timeout quan sát được; cleanup, interrupt/hủy được thử |
| Tác vụ độc lập | Nhiều report IDs, chờ tất cả Future | Không trộn result/correlation ID giữa job |
| Retry trùng | Gửi hai yêu cầu cùng idempotency key | Không sinh hai báo cáo công bố trùng nếu contract yêu cầu |
| Shutdown/restart | Job đang chạy, đóng context có kiểm soát | Trạng thái phục hồi đúng theo policy; không mất job bền vững |
| Tài nguyên ngoại vi bão hòa | Fake DB/HTTP client giới hạn concurrency | Giới hạn riêng được tôn trọng, không tạo vòng chờ |

Tránh biến kiểm thử concurrency thành phép thử đo thời gian ngẫu nhiên:

- Dùng `CountDownLatch` để biết worker đã vào đúng đoạn code; dùng latch khác để điều khiển lúc worker được tiếp tục.
- Không khẳng định thread A phải hoàn thành trước B nếu bài toán không yêu cầu thứ tự đó.
- Mỗi `await`, `get`, `poll` trong test nên có timeout có thông điệp lỗi rõ.
- Luôn release latch và dọn executor trong `finally`.
- Nếu test Spring dùng bean singleton chứa latch/trạng thái, phải cô lập theo từng test; context cache hoặc chạy test song song có thể gây nhiễu.
- Không tạo lệnh gọi sync chờ task con trong chính pool đang bão hòa.

## 11. Bài tập mở rộng

**Bài A – chỉnh cấu hình.** Với `core=2`, `max=4`, `queueCapacity=8`, tạo các worker cố ý chờ latch để lần lượt chiếm đầy core, lấp queue, mở rộng tới max, sau đó thử gửi thêm. Ghi lại số task đang chạy, chờ và số lần reject tại checkpoint có kiểm soát. Giải thích vì sao max thread chỉ tăng sau khi queue đã đầy.

**Bài B – batch có hạn mức.** Có 50 `reportId`, triển khai cửa sổ `window=4`, ghi kết quả ngay khi từng task hoàn thành, giữ tối đa bốn in-flight. Tạo lỗi ở report thứ bảy, quyết định rõ chính sách: dừng cả batch và hủy task còn lại, hay thu mọi kết quả rồi báo lỗi tổng hợp. Không giấu `ExecutionException`.

**Bài C – production adapter.** Thay báo cáo CSV trong bộ nhớ bằng đọc database phân trang và ghi output streaming tới object storage. Tạo trạng thái `QUEUED → RUNNING → READY/FAILED`, bắt rejection tại submission, bổ sung idempotency key, retry giới hạn và dọn temporary output.

**Bài D – tải và quan sát.** Chạy burst tải, đo queue waiting time, report run time, active thread, memory, DB connections và rejection. Thử thay `queueCapacity` mà giữ `core/max`; phân tích trade-off giữa độ trễ, thông lượng và số yêu cầu bị từ chối.

### Checklist trước khi đưa vào ứng dụng

- [ ] Có executor dành riêng cho report, `core/max/queue` đều được lựa chọn có chủ đích.
- [ ] Bằng chứng rằng task không bị rơi âm thầm; overload có phản hồi rõ ràng.
- [ ] `@Async` được gọi qua Spring proxy; có Future hoặc cơ chế theo dõi trạng thái/lỗi.
- [ ] Không giữ toàn bộ dữ liệu report lớn trong closure/Future/`StringBuilder`.
- [ ] Giới hạn tài nguyên DB, file, API ngoài và tổng số worker giữa các instance.
- [ ] Submission batch có cửa sổ, timeout, cancellation và không đợi task con trong cùng pool theo kiểu gây starvation.
- [ ] Trạng thái job, giao dịch và idempotency đủ để handle restart/retry.
- [ ] Test dùng latch/Future với timeout, có cleanup, không dựa vào `sleep`.
- [ ] Metric và log cho biết `jobId`, thời gian chờ/chạy, kết quả, saturation/rejection.
- [ ] Chính sách shutdown và recovery được thử trong điều kiện thực tế.

## Tài liệu chính thống

- [Java 17: ThreadPoolExecutor – core/max, queue và rejection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Java 17: CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html)
- [Java 17: ExecutorCompletionService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorCompletionService.html)
- [Spring Framework: Task Execution and Scheduling, `@Async`](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework: ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="design-practice-004">Thực hành kết hợp hai lời gọi độc lập bằng CompletableFuture</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành kết hợp hai lời gọi độc lập bằng CompletableFuture

## 1. Bài toán và kết quả cần đạt

Giả sử API Spring cần trả một bản tóm tắt khách hàng gồm **thông tin hồ sơ** và **điểm thưởng**. Hai lời gọi đi tới hai nguồn khác nhau:

- `ProfileClient.fetch(customerId)` lấy `Profile(id, name)`.
- `PointsClient.fetch(customerId)` lấy `Points(value)`.

Lời gọi lấy điểm **không cần** kết quả lấy hồ sơ, và ngược lại. Vì thế có thể khởi chạy cả hai, sau đó ghép kết quả thành một `Summary`.

Nếu thực hiện tuần tự, thời gian xử lý lý tưởng xấp xỉ `T_profile + T_points`. Khi chạy song song thật sự trên đủ năng lực thực thi, thời gian xấp xỉ `max(T_profile, T_points) + chi phí phối hợp`. Đây chỉ là ước lượng: queue, giới hạn kết nối, CPU, tải downstream và timeout đều có thể làm thay đổi thời gian thực tế.

```text
Tuần tự:
request --> fetchProfile (250 ms) --> fetchPoints (400 ms) --> summary
          |--------------------- khoảng 650 ms ---------------------|

Song song:
                      +--> fetchProfile (250 ms) --+
request --> submit ---|                            +--> thenCombine --> summary
                      +--> fetchPoints (400 ms) ---+
          |----------- khoảng 400 ms + overhead ---------------------|
```

**Mục tiêu thực hành:** khởi chạy cả hai tác vụ trước khi chờ, dùng `thenCombine` để tạo một kết quả có kiểu rõ ràng, sử dụng executor có kiểm soát trong Spring, xử lý lỗi/timeout, và viết test chứng minh việc ghép chỉ hoàn tất khi cả hai đầu vào thành công.

## 2. Chọn đúng phép kết hợp của CompletableFuture

`CompletableFuture<T>` biểu diễn một kết quả có thể hoàn tất trong tương lai. `supplyAsync(Supplier<T>, Executor)` gửi phép tính trả giá trị cho executor. `thenCombine` ghép **hai future độc lập**:

```java
CompletableFuture<Profile> profileFuture =
        CompletableFuture.supplyAsync(() -> fetchProfile(id), executor);
CompletableFuture<Points> pointsFuture =
        CompletableFuture.supplyAsync(() -> fetchPoints(id), executor);

CompletableFuture<Summary> summaryFuture =
        profileFuture.thenCombine(pointsFuture,
                (profile, points) -> new Summary(profile, points));
```

Đây là **đoạn minh họa**, trong đó các kiểu, hàm và biến được khai báo ở ví dụ đầy đủ bên dưới. Điểm quyết định: **cả hai `supplyAsync` phải được gọi trước khi chờ kết quả**. Nếu gọi `profileFuture.join()` ngay sau future thứ nhất, rồi mới khởi chạy future thứ hai, đoạn code lại tuần tự.

| API | Dùng khi nào? | Kết quả |
| --- | --- | --- |
| `supplyAsync(supplier, executor)` | Cần chạy tác vụ trả về giá trị trên executor | `CompletableFuture<T>` |
| `thenCombine(other, fn)` | Hai kết quả độc lập, cần **cả hai** để ghép | Future chứa kết quả của `fn` |
| `thenCompose(fn)` | Lời gọi B **phụ thuộc kết quả A**, `fn` trả về future | Future phẳng, không lồng `CompletableFuture<CompletableFuture<T>>` |
| `thenApply(fn)` | Biến đổi **một** kết quả đồng bộ sau khi có dữ liệu | Future của giá trị đã biến đổi |
| `allOf(f1, f2, ...)` | Chờ nhiều future cùng hoàn tất | `CompletableFuture<Void>`, sau đó vẫn phải lấy/ghép các giá trị |
| `exceptionally(fn)` / `handle(fn)` | Áp dụng chính sách fallback hoặc biến đổi lỗi | Future mới theo chính sách xử lý |

`thenCombine` chỉ chạy hàm ghép khi **cả hai future hoàn tất bình thường**. Nếu một lời gọi lỗi, future ghép hoàn tất bất thường và không tạo `Summary` bình thường. `thenCombine` không tự biến hai lời gọi đồng bộ thành song song; concurrency đã đến từ cách tạo hai future và executor.

`thenCombine` không có hậu tố `Async` có thể thực thi hàm ghép trên luồng hoàn tất một trong hai future (hoặc luồng gắn callback nếu cả hai đã xong). Hãy giữ phép ghép nhẹ, không làm I/O chặn ở callback. Nếu phép ghép tốn nhiều CPU, cân nhắc `thenCombineAsync(other, fn, executor)` và tính cả chi phí executor.

## 3. Chương trình Java 17+ chạy độc lập

Tệp **`TwoIndependentCalls.java`** sau chỉ dùng JDK, không yêu cầu Spring. `Thread.sleep` mô phỏng hai I/O call độc lập với thời gian khác nhau; mỗi call chạy trên một worker của pool hai luồng.

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TwoIndependentCalls {

    record Profile(String id, String name) {}
    record Points(int value) {}
    record Summary(String id, String name, int points) {}

    private static Profile fetchProfile(String customerId) {
        pause(250); // Giả lập độ trễ I/O của lời gọi thứ nhất.
        return new Profile(customerId, "An");
    }

    private static Points fetchPoints(String customerId) {
        pause(400); // Giả lập độ trễ I/O của lời gọi thứ hai.
        return new Points(150);
    }

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            String customerId = "C01";

            // Khởi chạy cả hai công việc, chưa chờ kết quả.
            CompletableFuture<Profile> profileFuture =
                    CompletableFuture.supplyAsync(
                            () -> fetchProfile(customerId), executor);

            CompletableFuture<Points> pointsFuture =
                    CompletableFuture.supplyAsync(
                            () -> fetchPoints(customerId), executor);

            // Summary chỉ được tạo khi hai lời gọi đều thành công.
            CompletableFuture<Summary> summaryFuture =
                    profileFuture.thenCombine(pointsFuture,
                            (profile, points) ->
                                    new Summary(
                                            profile.id(),
                                            profile.name(),
                                            points.value()));

            // Chỉ chờ tại ranh giới của chương trình dòng lệnh.
            Summary result = summaryFuture.join();
            System.out.println("customer=" + result.id()
                    + ", name=" + result.name()
                    + ", points=" + result.points());
        } finally {
            executor.shutdown();
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Task interrupted", e);
        }
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac TwoIndependentCalls.java
java TwoIndependentCalls
```

Đầu ra **xác định** (thời gian chạy có thể khác nhau tùy máy):

```text
customer=C01, name=An, points=150
```

Giải thích từng mốc:

1. `newFixedThreadPool(2)` cho phép tối đa hai worker thực thi các task mô phỏng đồng thời.
2. Hai lời gọi `supplyAsync` được submit liên tiếp nhưng **không `join` ở giữa**; chúng có thể thực thi song song.
3. `thenCombine` định nghĩa cách tạo `Summary` với hai giá trị có kiểu `Profile` và `Points`. Thứ tự call nào xong trước không làm đổi dữ liệu ghép.
4. `summaryFuture.join()` chờ kết quả cuối cùng ở `main` — ranh giới đồng bộ chấp nhận được cho ví dụ CLI. Không dùng cách chặn này trên Servlet request thread nếu mục tiêu là trả response bất đồng bộ.
5. `finally` đóng executor kể cả khi chờ kết quả ném lỗi; trong chương trình thực tế, executor thường do container Spring quản lý vòng đời.

Không suy luận rằng tốc độ chắc chắn nhanh hơn đúng 250 ms: trên một worker, với downstream giới hạn kết nối hoặc CPU nghẽn, hai task có thể phải chờ nhau. Phép đo tải thực tế quan trọng hơn thời gian sleep mô phỏng.

## 4. Chuyển vào ứng dụng Spring Framework

Ở mức ứng dụng, thường có các client đồng bộ (HTTP/SDK/database) cần chạy ở pool riêng, sau đó trả một future cho lớp web. Một cách tổ chức dễ quan sát là **executor do Spring quản lý → service ghép future → controller trả future**.

### 4.1. Cấu hình executor có tên riêng

Tạo tệp `AggregationExecutorConfig.java` trong package được Spring component-scan:

```java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AggregationExecutorConfig {

    @Bean("aggregationExecutor")
    public ThreadPoolTaskExecutor aggregationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("customer-agg-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
```

Spring quản lý khởi tạo và vòng đời bean `ThreadPoolTaskExecutor`. `ThreadPoolTaskExecutor` triển khai `java.util.concurrent.Executor` nên có thể truyền vào `supplyAsync(..., executor)`.

Các số `4/8/32` chỉ là **giá trị minh họa**, phải điều chỉnh bằng đo tải. Với `ThreadPoolExecutor` thông thường, khi đạt core thì task được đưa vào queue trước; chỉ khi queue đầy mới phát triển worker vượt core tới max. Queue có giới hạn cho phép phát hiện overload qua rejection thay vì tích lũy vô hạn. Lỗi bị reject ở thời điểm submit có thể ném trực tiếp, nên ứng dụng phải có cách trả lỗi và giới hạn đầu vào phù hợp.

### 4.2. Service chạy hai client độc lập và ghép DTO

Tạo tệp `CustomerSummaryService.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class CustomerSummaryService {
    private final ProfileClient profileClient;
    private final PointsClient pointsClient;
    private final Executor executor;

    public CustomerSummaryService(
            ProfileClient profileClient,
            PointsClient pointsClient,
            @Qualifier("aggregationExecutor") Executor executor) {
        this.profileClient = profileClient;
        this.pointsClient = pointsClient;
        this.executor = executor;
    }

    public CompletableFuture<Summary> load(String customerId) {
        CompletableFuture<Profile> profileFuture =
                CompletableFuture.supplyAsync(
                        () -> profileClient.fetch(customerId), executor);

        CompletableFuture<Points> pointsFuture =
                CompletableFuture.supplyAsync(
                        () -> pointsClient.fetch(customerId), executor);

        return profileFuture.thenCombine(pointsFuture,
                    (profile, points) -> new Summary(
                            profile.id(), profile.name(), points.value()))
                .orTimeout(1500, TimeUnit.MILLISECONDS);
    }

    // Contract cho các adapter thực tế, do Spring inject.
    public interface ProfileClient {
        Profile fetch(String customerId);
    }

    public interface PointsClient {
        Points fetch(String customerId);
    }

    public record Profile(String id, String name) {}
    public record Points(int value) {}
    public record Summary(String id, String name, int points) {}
}
```

Đây là **mã tích hợp trong dự án Spring**, không phải chương trình chạy độc lập chỉ với JDK. Phải có các bean triển khai `ProfileClient` và `PointsClient`, ví dụ adapter HTTP hoặc service dữ liệu. Hai client trong bài này là hợp đồng minh họa, không phải Spring API có sẵn.

Điểm đáng chú ý:

- `@Qualifier("aggregationExecutor")` chọn đúng pool thay vì vô tình dùng executor khác.
- Hai future được submit trước khi gọi `thenCombine`; không có `join()` giữa hai lệnh.
- Mỗi kết quả được gom về record `Summary` bất biến và dễ serialize.
- `orTimeout(1500, TimeUnit.MILLISECONDS)` áp timeout **cho future đã ghép**. Nếu hai lời gọi không hoàn tất kịp, kết quả ghép hoàn tất bất thường với `TimeoutException`. Con số 1500 ms minh họa một deadline; hãy chọn theo SLO và timeout HTTP/DB thực.
- Hàm `load` trả ngay `CompletableFuture<Summary>` mà không chặn đợi tất cả kết quả.

**Không cần `@EnableAsync` hoặc `@Async` cho cách làm này.** `supplyAsync` đã nhận executor tường minh. Nếu chọn mô hình `@Async` cho client/service, phải hiểu proxy của Spring và tránh tự gọi phương thức `@Async` trên cùng instance rồi tưởng đã chuyển luồng.

### 4.3. Trả future trực tiếp từ Spring MVC controller

Tạo tệp `CustomerSummaryController.java` trong cùng package hoặc import service từ package tương ứng:

```java
import java.util.concurrent.CompletableFuture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
public class CustomerSummaryController {
    private final CustomerSummaryService service;

    public CustomerSummaryController(CustomerSummaryService service) {
        this.service = service;
    }

    @GetMapping("/{id}/summary")
    public CompletableFuture<CustomerSummaryService.Summary> summary(
            @PathVariable String id) {
        return service.load(id);
    }
}
```

Spring MVC hỗ trợ `CompletableFuture`/`CompletionStage` làm giá trị trả về bất đồng bộ. Controller không gọi `join()` hay `get()`, nên không giữ Servlet worker chỉ để chờ hai upstream. Khi future hoàn tất, Spring MVC tiếp tục xử lý response. Muốn code chạy trong một ứng dụng Spring Boot, thêm dependencies Spring Web và cung cấp hai adapter bean thực. Mã ví dụ này không đưa ra một HTTP client giả định hoặc endpoint ngoài đời.

Nếu client vốn đã trả `CompletableFuture<Profile>` và `CompletableFuture<Points>` bằng API non-blocking, hãy dùng trực tiếp hai future đó rồi `thenCombine`; việc bọc thêm `supplyAsync` có thể chỉ tạo overhead và chuyển luồng không cần thiết.

## 5. Lỗi, timeout, fallback và hủy công việc

### 5.1. Nếu một lời gọi thất bại

Khi `profileClient.fetch` hoặc `pointsClient.fetch` ném exception, supplier của `supplyAsync` khiến future tương ứng hoàn tất bất thường. `thenCombine` không gọi hàm tạo `Summary` bình thường; future kết quả cũng thất bại. Khi đọc qua `join()`, lỗi thường được bao trong `CompletionException`; khi dùng `get()`, phải xử lý `InterruptedException` và `ExecutionException`. Tại controller, nên chuyển lỗi upstream thành HTTP status/response phù hợp bằng cơ chế exception handling của ứng dụng.

Để truy lỗi đúng upstream, có thể gắn callback quan sát **không thay đổi kết quả**:

```java
profileFuture.whenComplete((profile, error) -> {
    if (error != null) {
        // Ghi log theo correlation ID, không ghi dữ liệu nhạy cảm.
        log.error("profile lookup failed", error);
    }
});
```

Đây là **đoạn minh họa tích hợp**, trong đó `log` là logger ứng dụng. `whenComplete` trả future phụ; nếu chỉ gắn để log, cần hiểu lỗi trong callback có thể tạo một exceptional stage phụ nếu không được tiêu thụ. Giữ logging ngắn và không blocking.

### 5.2. Timeout trên future không nhất thiết dừng I/O

- `orTimeout(duration, unit)` làm chính future bị gọi hoàn tất bất thường nếu quá hạn; nó **không đảm bảo** interrupt, đóng socket hay dừng hai supplier đã chạy.
- `completeOnTimeout(fallback, duration, unit)` làm chính future hoàn tất bình thường bằng fallback; chỉ dùng nếu business cho phép trả kết quả giảm chất lượng. Không âm thầm gán `0 điểm` khi `0` có nghĩa nghiệp vụ khác với `không truy cập được`.
- Cả hai API trên **thay đổi chính instance future** nhận lời gọi và trả lại instance đó. Nếu future được chia sẻ giữa nhiều consumer, việc áp timeout có thể ảnh hưởng mọi consumer. Nên đặt deadline ở kết quả ghép mà service sở hữu, như ví dụ.
- Cấu hình connect/read/call timeout ở HTTP client/DB driver, kiểm soát queue wait, hủy tác vụ nếu thư viện hỗ trợ và đặt deadline toàn request. Timeout của `CompletableFuture` một mình không giải phóng tài nguyên upstream.

### 5.3. Chọn fallback theo nghiệp vụ

Nếu cả hồ sơ lẫn điểm là dữ liệu bắt buộc, thất bại một lời gọi nên khiến toàn bộ bản tóm tắt thất bại với thông báo rõ ràng. Nếu điểm chỉ là dữ liệu bổ sung, có thể chủ động biến lỗi của future điểm thành trạng thái `unavailable` thay vì báo `0`:

```java
// Minh họa: thay đổi DTO để phân biệt không có dữ liệu với 0 điểm.
record OptionalPoints(Integer value, boolean available) {}

CompletableFuture<OptionalPoints> safePoints =
        pointsFuture.handle((points, error) -> {
            if (error != null) {
                return new OptionalPoints(null, false);
            }
            return new OptionalPoints(points.value(), true);
        });
```

Đoạn trên là biến thể độc lập, không tự thay `Summary` trong ví dụ Spring ở mục 4. Phải đổi kiểu dữ liệu kết quả/contract API tương ứng nếu áp dụng. Tương tự, `exceptionally` thích hợp để trả fallback cùng kiểu, còn `handle` cho phép xử lý cả thành công lẫn thất bại.

Một future thất bại hoặc bị timeout **không tự hủy task còn lại**. Tùy chính sách, có thể yêu cầu cancel khi không còn cần kết quả, nhưng `CompletableFuture.cancel(true)` không bảo đảm interrupt việc I/O đã chạy. Đừng dựa vào cancel làm cơ chế timeout network.

### 5.4. Rejection khi pool quá tải

`supplyAsync(..., executor)` có thể ném lỗi **ngay lúc submit** nếu executor từ chối task; đừng cho rằng mọi lỗi chỉ xuất hiện sau đó trong future. Với `ThreadPoolTaskExecutor`, Spring thường báo `TaskRejectedException` theo contract của `TaskExecutor`. Cần quan sát active threads, queue depth, rejection rate; áp dụng rate limit/backpressure và trả lỗi như HTTP 503 nếu không thể nhận thêm công việc. Đừng tăng pool vô hạn để giấu tình trạng quá tải.

## 6. Các bẫy về concurrency trong Spring

**Dùng đúng executor và hạn mức:** ví dụ một request ghép hai blocking calls sẽ tạo **tối đa hai task** trong pool; 100 request đồng thời có thể submit đến 200 task trước khi tính các công việc khác. Chọn `corePoolSize`, `maxPoolSize`, queue capacity và giới hạn kết nối downstream dựa trên đo tải, không suy từ số CPU hoặc ví dụ nhỏ. Với ThreadPoolExecutor, queue thường được sử dụng sau khi đạt core, trước khi tăng worker tới max.

**Tránh chờ bên trong worker cùng pool:** một supplier dùng `join()` để đợi task con được submit vào chính executor nhỏ có thể gây starvation: worker đều chờ, task con nằm trong queue, không còn worker thực thi. Dùng composition để biểu diễn phụ thuộc, hoặc tách executor khi có lý do rõ ràng.

**Không truyền ngầm ThreadLocal:** `SecurityContext`, MDC/correlation ID, request-scoped state và transaction context không mặc định xuất hiện trong thread do `supplyAsync` sử dụng. Truyền dữ liệu cần thiết dưới dạng tham số/DTO bất biến, dùng `TaskDecorator` hoặc context propagation phù hợp khi thật sự cần. Đừng coi transaction `@Transactional` trên thread HTTP là đang bao trùm công việc ở thread pool; transaction gắn với luồng hiện hành.

**Không chia sẻ trạng thái mutable tùy tiện:** hai supplier không nên cùng sửa một `ArrayList`, entity JPA đang quản lý hoặc một mutable object nếu thiếu đồng bộ. Kết quả nên là immutable record/DTO và chỉ ghép sau khi future hoàn tất.

**Phân biệt loại I/O:** nếu gateway đã non-blocking, không thêm worker chỉ vì thấy `CompletableFuture`. Nếu gateway blocking (RestTemplate, JDBC blocking, SDK sync...), executor tách biệt và timeout downstream là quan trọng. Trong WebFlux, không đưa blocking code lên event-loop.

**Đừng nhầm callback với thread mới:** `thenCombine` là cách mô tả sự phụ thuộc của hai kết quả; nó không tự tạo thread mới. `thenCombineAsync` thêm scheduling theo executor được chọn; dùng khi có khối xử lý đủ lớn hoặc cần tách biệt callback, không coi đó là tối ưu mặc định.

## 7. Test deterministically thay vì dựa vào sleep

Kiểm tra đồng thời **hai nhánh hoàn tất thì kết quả mới xuất hiện** và **lỗi một nhánh lan lên kết quả**. Với JUnit Jupiter 5, tạo tệp `TwoCallCompositionTest.java`:

```java
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class TwoCallCompositionTest {
    record Profile(String name) {}
    record Points(int value) {}
    record Summary(String name, int points) {}

    @Test
    void waitsForBothSuccessfulResults() {
        CompletableFuture<Profile> profile = new CompletableFuture<>();
        CompletableFuture<Points> points = new CompletableFuture<>();

        CompletableFuture<Summary> summary =
                profile.thenCombine(points,
                        (p, q) -> new Summary(p.name(), q.value()));

        profile.complete(new Profile("An"));
        assertFalse(summary.isDone()); // Nhánh points chưa xong.

        points.complete(new Points(150));
        assertEquals(new Summary("An", 150), summary.join());
    }

    @Test
    void failsWhenEitherRequiredCallFails() {
        CompletableFuture<Profile> profile = new CompletableFuture<>();
        CompletableFuture<Points> points = new CompletableFuture<>();

        CompletableFuture<Summary> summary =
                profile.thenCombine(points,
                        (p, q) -> new Summary(p.name(), q.value()));

        profile.completeExceptionally(new IllegalStateException("profile down"));
        points.complete(new Points(150));

        CompletionException exception =
                assertThrows(CompletionException.class, () -> summary.join());
        assertEquals("profile down", exception.getCause().getMessage());
    }
}
```

Test chỉ cần JUnit 5 dependency (ví dụ từ `spring-boot-starter-test` trong dự án Spring Boot), không cần chờ thời gian thực. Bài test xác nhận contract của composition; một test tích hợp riêng nên stub hai gateway, xác nhận chúng được gọi, kiểm tra response DTO, timeout và rejection theo cấu hình thực.

Để chứng minh hai tác vụ được khởi chạy **đồng thời**, có thể dùng hai `CountDownLatch` hoặc một barrier trong test tích hợp: cả hai mock gateway báo đã bắt đầu, giữ lại đến khi test mở barrier, rồi mới hoàn thành. Kiểm tra cả hai đã bắt đầu **trước khi** mở barrier. Cách này ổn định hơn assertion kiểu “thời gian chạy phải nhỏ hơn 400 ms”, vốn dễ flaky trên CI.

## 8. Checklist trước khi đưa vào sử dụng

1. Hai lời gọi thực sự **độc lập**? Nếu B cần dữ liệu A, chọn `thenCompose` hoặc pipeline có phụ thuộc rõ ràng.
2. Cả hai công việc có được khởi chạy trước lần `join/get` đầu tiên không? Có đủ worker/connection để chồng thời gian chạy không?
3. Hai kết quả có DTO/kiểu rõ ràng, null policy và quy tắc ghép chính xác không?
4. Executor có tên riêng, queue hữu hạn, rejection policy, metrics và giới hạn tải phù hợp không?
5. Timeout ở **client thật** lẫn deadline của thao tác ghép đã được định nghĩa chưa? Timeout xong task nào còn có thể chạy?
6. Lỗi một nhánh nên làm thất bại toàn response hay có fallback được định nghĩa rõ? Có tránh đánh đồng “không có dữ liệu” với giá trị 0 không?
7. Có lỡ gọi `join()` trong Servlet thread hoặc trong worker đang giữ tài nguyên không? Controller có thể trả future trực tiếp không?
8. Transaction, MDC/SecurityContext và mutable state có được xử lý đúng khi vượt qua ranh giới thread không?
9. Test có bao phủ cả thứ tự hoàn tất đảo ngược, lỗi, timeout, pool bão hòa và thiếu dữ liệu khi cần không?
10. Có quan sát được latency tổng, latency từng upstream, queue depth, active count, rejection và error rate để kiểm chứng lợi ích không?

## 9. Lỗi phổ biến và cách sửa

| Sai lầm | Hệ quả | Sửa |
| --- | --- | --- |
| `supplyAsync(A).join()` rồi mới submit B | Hai lời gọi thực tế tuần tự | Submit A và B trước, sau đó `thenCombine` |
| Dùng `thenCompose` khi A/B độc lập | Tạo chuỗi phụ thuộc không cần thiết, tăng latency | `thenCombine` |
| Không truyền executor | Mặc định dựa vào common pool, khó cô lập blocking I/O | Executor chuyên dụng, có giới hạn, được Spring quản lý |
| `join()` ngay trong controller | Chặn request worker, mất lợi ích response async | Trả `CompletableFuture` cho Spring MVC |
| Dùng queue rất lớn để tránh reject | Che quá tải, latency hàng đợi tăng | Giới hạn queue/đầu vào, đo rejection |
| `orTimeout` rồi cho rằng network task đã dừng | Worker/socket có thể vẫn bận | Timeout ở client thật và hủy có kiểm soát |
| Gắn fallback `0` cho dữ liệu điểm lỗi | Trả thông tin sai nghĩa nghiệp vụ | Dùng trạng thái unavailable hoặc trả lỗi |
| Tin rằng `@Transactional`/MDC tự theo sang worker | Thiếu transaction hoặc mất correlation | Truyền context có chủ đích, phân định transaction |
| Gọi tác vụ con rồi `join` ngay trong cùng pool | Có thể làm cạn worker và deadlock kiểu starvation | Compose tương lai và xem lại phụ thuộc |
| Kiểm tra concurrency chỉ bằng `sleep`/thời gian | Test chập chờn khi máy CI chậm | Dùng `CompletableFuture` thủ công và latch/barrier |

## 10. Tài liệu tra cứu chính thức

- [Java 17 — CompletableFuture API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): `supplyAsync`, `thenCombine`, `thenCompose`, `orTimeout`, `completeOnTimeout`.
- [Java 17 — ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html): core/max/queue, rejection và lifecycle.
- [Spring — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): cấu hình, metrics, contract rejection.
- [Spring — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): executor, `@Async` và lập lịch.
- [Spring MVC — Controller Return Values](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html): controller trả `CompletableFuture` và `CompletionStage`.

Một cách triển khai đúng có ba đặc điểm: **khởi chạy cả hai công việc độc lập**, **ghép chúng bằng `thenCombine`**, và **giới hạn/quan sát executor cùng timeout và lỗi thực tế**. Nhờ đó kết quả đúng nghĩa nghiệp vụ, đồng thời độ trễ có cơ hội tiến gần thời gian của lời gọi chậm hơn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="design-practice-005">Thực hành @Async có lỗi, timeout và ngữ cảnh nhật ký</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành `@Async` có lỗi, timeout và ngữ cảnh nhật ký

## 1. Mục tiêu bài thực hành

Giả sử ứng dụng Spring nhận yêu cầu tạo báo cáo. Việc tạo báo cáo diễn ra trên một thread của executor để thread gọi có thể tiếp tục xử lý. Khi thử tải, ta cần trả lời đồng thời ba câu hỏi:

1. Nếu công việc ném exception, người gọi hoặc hệ thống giám sát nhận được lỗi bằng cách nào?
2. Nếu người gọi chỉ chờ 50 ms nhưng công việc thực hiện 500 ms, thao tác nào hết thời gian chờ, và công việc có thật sự dừng hay không?
3. Log ở thread `main`/HTTP có `requestId` nhưng log ở worker `lab-*` có còn giá trị đó hay không, và dữ liệu của yêu cầu trước có bị “dính” sang yêu cầu sau không?

Sau bài, bạn sẽ tự chạy một ứng dụng Spring Boot nhỏ để quan sát:

- `@Async` trả về `CompletableFuture<T>`: kết quả và lỗi được quan sát qua future.
- `@Async` trả về `void`: exception đi vào `AsyncUncaughtExceptionHandler`, không trả ngược trực tiếp cho người gọi.
- `Future.get(timeout, unit)`: hết thời gian **đợi kết quả**, không mặc nhiên hủy công việc.
- `TaskDecorator` + SLF4J MDC: chụp context ở lúc submit, khôi phục tại worker và dọn sạch sau khi xử lý.
- `@Async` qua Spring proxy: lời gọi giữa các bean; tự gọi trong cùng bean không kích hoạt advice trong proxy mode mặc định.

Bài dùng **Java 17+**, Spring Framework 6.x và minh họa bằng **Spring Boot 3.3.13** để có một cấu hình phụ thuộc cụ thể, tái lập được. Với Spring Boot 3.3.x, dùng JDK 17 hoặc 21 cho bài lab; khi đưa vào dự án thật, chọn phiên bản Spring Boot còn được hỗ trợ và kiểm tra API tương ứng. Không cần web server hay cơ sở dữ liệu.

## 2. Mô hình thực thi và các điểm có thể lỗi

```text
Thread gọi (main/HTTP)                      Executor worker (lab-*)
        |                                             |
        | đặt MDC requestId                           |
        | gọi beanProxy.generate(...)                 |
        | -- @Async submit task --------------------> | TaskDecorator cài MDC snapshot
        | nhận CompletableFuture ngay                 | chạy generate(...)
        | bỏ MDC của lời gọi                          |     | thành công: completedFuture
        |                                             |     | lỗi: throw exception
        | get(50 ms) đợi result                        | hoàn tất task/future
        | timeout / result / exception <------------- | TaskDecorator khôi phục MDC
```

Có **ba vị trí lỗi khác nhau**, đừng xử lý chúng bằng cùng một `catch`:

| Vị trí | Ví dụ | Nơi quan sát |
| --- | --- | --- |
| Khi **submit** | Pool/queue đầy, executor đã shutdown | Ngay thread gọi: Spring có thể ném `TaskRejectedException` |
| Khi **chạy task** | SQL/HTTP thất bại, dữ liệu không hợp lệ, `IllegalStateException` | Với `CompletableFuture`: future hoàn tất exceptional; với `void`: `AsyncUncaughtExceptionHandler` |
| Khi **đợi kết quả** | `get(50, MILLISECONDS)` nhưng task chưa xong | Thread gọi nhận `TimeoutException`; task có thể vẫn đang chạy |

`@Async` không tự cung cấp retry, giới hạn thời gian chạy, circuit breaker hay truyền mọi `ThreadLocal`. Những hành vi này phải được thiết kế tường minh.

## 3. Chuẩn bị project có thể chạy

Tạo project độc lập theo cấu trúc:

```text
async-lab/
├── pom.xml
└── src/main/
    ├── java/dev/example/asynclab/
    │   ├── AsyncLabApplication.java
    │   ├── AsyncLabConfig.java
    │   ├── AsyncLabService.java
    │   ├── VoidFailureSignal.java
    │   └── AsyncLabRunner.java
    └── resources/
        └── application.properties
```

Yêu cầu: JDK 17/21, Maven 3.6.3+ và kết nối Maven Central cho lần tải dependency đầu tiên. Dùng package `dev.example.asynclab` đúng như đường dẫn; tên class public phải trùng tên file. Mẫu có thể đưa vào ứng dụng Spring hiện hữu bằng cách điều chỉnh package và cơ chế khởi chạy.

**`pom.xml`**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.13</version>
        <relativePath/>
    </parent>
    <groupId>dev.example</groupId>
    <artifactId>async-lab</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
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
```

`spring-boot-starter` cung cấp Spring context và logging (SLF4J + Logback qua starter logging); bài không cần `spring-boot-starter-web`. Trong ứng dụng sẵn có, đừng thêm một ứng dụng `@SpringBootApplication` thứ hai vào cùng package.

**`src/main/resources/application.properties`**

```properties
spring.main.web-application-type=none
logging.pattern.console=%d{HH:mm:ss.SSS} %-5level [%thread] [requestId=%X{requestId:-none}] %logger{36} - %msg%n
```

Pattern hiển thị timestamp, thread, `requestId`, logger và thông điệp. `none` khi không có MDC giúp dễ thấy sự khác nhau giữa thread gọi và worker.

## 4. Cấu hình executor, MDC và xử lý exception `void`

**`src/main/java/dev/example/asynclab/AsyncLabConfig.java`**

```java
package dev.example.asynclab;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncLabConfig implements AsyncConfigurer {
    private static final Logger log =
            LoggerFactory.getLogger(AsyncLabConfig.class);

    private final VoidFailureSignal signal;

    public AsyncLabConfig(VoidFailureSignal signal) {
        this.signal = signal;
    }

    @Bean("labExecutor")
    public ThreadPoolTaskExecutor labExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("lab-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(4);
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());

        executor.setTaskDecorator(task -> {
            // Chạy trên thread GỌI lúc submit.
            Map<String, String> captured = MDC.getCopyOfContextMap();

            return () -> {
                // Chạy trên worker: lưu context cũ nếu worker tái sử dụng.
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    restore(captured);
                    task.run();
                } finally {
                    // Không để requestId của task này rò sang task tiếp.
                    restore(previous);
                }
            };
        });
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (exception, method, params) -> {
            log.error("VOID_FAILURE method={} requestId={} cause={}",
                    method.getName(), MDC.get("requestId"),
                    exception.toString(), exception);
            signal.markHandled();
        };
    }

    private static void restore(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
```

Cấu hình trên chỉ định tên bean `labExecutor` để `@Async("labExecutor")` dùng đúng pool. `ThreadPoolTaskExecutor` được Spring khởi tạo và quản lý vòng đời; không cần gọi `initialize()` thủ công với bean này.

`MDC.getCopyOfContextMap()` được gọi **khi decorator nhận task ở thread submit**, trước khi `MDC` phía caller bị dọn. Phần `try/finally` ở worker quan trọng vì worker của pool được tái sử dụng. Nếu request A đặt `requestId=A`, request B đặt `requestId=B`, log B không được mang `A`. Nếu caller không có MDC, `restore(null)` xóa context còn sót.

Không biến `TaskDecorator` thành chỗ nuốt mọi exception: task được decorate có thể là wrapper `FutureTask`; exception bên trong có thể đã được future giữ lại. Hãy xử lý exception ở future hoặc `AsyncUncaughtExceptionHandler` như các phần tiếp theo.

Để đợi chắc rằng exception của tác vụ `void` đã vào handler trước khi chương trình demo thoát, tạo một tín hiệu đơn giản.

**`src/main/java/dev/example/asynclab/VoidFailureSignal.java`**

```java
package dev.example.asynclab;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class VoidFailureSignal {
    private final CountDownLatch handled = new CountDownLatch(1);

    public void markHandled() {
        handled.countDown();
    }

    public boolean await(long time, TimeUnit unit)
            throws InterruptedException {
        return handled.await(time, unit);
    }
}
```

Đây là công cụ đồng bộ **cho bài lab** để kết quả không phụ thuộc `Thread.sleep` đoán mò. Ứng dụng thực không nên dùng một latch toàn cục để theo dõi mọi request; thay bằng metrics/counter/log có correlation ID.

## 5. Viết service có trường hợp thành công và thất bại

**`src/main/java/dev/example/asynclab/AsyncLabService.java`**

```java
package dev.example.asynclab;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncLabService {
    private static final Logger log =
            LoggerFactory.getLogger(AsyncLabService.class);

    @Async("labExecutor")
    public CompletableFuture<String> generate(
            String id, long delayMillis, boolean fail) {
        log.info("WORK_START id={} mdc={}", id, MDC.get("requestId"));

        try {
            Thread.sleep(delayMillis); // Mô phỏng tác vụ chờ I/O.
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("INTERRUPTED id=" + id, ex);
        }

        if (fail) {
            throw new IllegalStateException("SIMULATED_FAILURE id=" + id);
        }

        log.info("WORK_OK id={}", id);
        return CompletableFuture.completedFuture("report:" + id);
    }

    @Async("labExecutor")
    public void failVoid(String id) {
        log.info("VOID_START id={} mdc={}", id, MDC.get("requestId"));
        throw new IllegalArgumentException(
                "SIMULATED_VOID_FAILURE id=" + id);
    }
}
```

Lưu ý ba điểm:

- Đối với `@Async` trả `CompletableFuture`, phương thức **được Spring proxy đưa lên worker**. Bên trong worker, ta trả một future hoàn thành ngay với kết quả đã tính. Spring cung cấp future ngoài cho caller; không cần tự `supplyAsync()` lần nữa, vì điều đó có thể tạo thêm lần dispatch sang executor khác và mất MDC.
- `throw` trong `generate()` không quay ngược ngay qua lời gọi từ caller; nó khiến future nhận được hoàn tất exceptional. Khi `.get()` kết quả, lỗi nghiệp vụ thường nằm trong `ExecutionException.getCause()`.
- `failVoid()` không có đối tượng future để caller chờ. Exception được chuyển cho `AsyncUncaughtExceptionHandler` đã cấu hình. Handler log đầy đủ stack trace và báo tín hiệu cho demo.

`Thread.sleep` chỉ tạo độ trễ giả lập có kiểm soát; khi thử trong hệ thống thật, hãy thay bằng thao tác I/O có timeout, hoặc tác vụ xử lý thực sự. Không dùng `Thread.sleep` trong code production để mô phỏng backpressure.

## 6. Runner thực thi đủ bốn kịch bản

**`src/main/java/dev/example/asynclab/AsyncLabRunner.java`**

```java
package dev.example.asynclab;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AsyncLabRunner implements ApplicationRunner {
    private static final Logger log =
            LoggerFactory.getLogger(AsyncLabRunner.class);

    private final AsyncLabService service;
    private final VoidFailureSignal signal;

    public AsyncLabRunner(
            AsyncLabService service, VoidFailureSignal signal) {
        this.service = service;
        this.signal = signal;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 1. Thành công: giá trị đi qua CompletableFuture.
        CompletableFuture<String> ok = withRequestId("r-ok",
                () -> service.generate("r-ok", 40, false));
        log.info("SUCCESS result={}", ok.get(2, TimeUnit.SECONDS));

        // 2. Lỗi trong worker: caller quan sát ExecutionException.
        CompletableFuture<String> bad = withRequestId("r-fail",
                () -> service.generate("r-fail", 40, true));
        try {
            bad.get(2, TimeUnit.SECONDS);
            throw new AssertionError("Expected future failure");
        } catch (ExecutionException expected) {
            log.info("FUTURE_FAILURE cause={}",
                    expected.getCause().getMessage());
        }

        // 3. Chờ hết hạn: task vẫn có thể hoàn thành sau đó.
        CompletableFuture<String> slow = withRequestId("r-slow",
                () -> service.generate("r-slow", 500, false));
        try {
            slow.get(50, TimeUnit.MILLISECONDS);
            throw new AssertionError("Expected caller timeout");
        } catch (TimeoutException expected) {
            log.warn("CALLER_TIMEOUT id=r-slow done={}", slow.isDone());
        }
        log.info("LATE_SUCCESS result={}",
                slow.get(2, TimeUnit.SECONDS));

        // 4. Lỗi @Async void: đi vào handler, không phải future.
        withRequestId("r-void", () -> {
            service.failVoid("r-void");
            return null;
        });
        if (!signal.await(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "void failure handler did not run");
        }
        log.info("VOID_HANDLER_SEEN");
        log.info("DEMO_DONE");
    }

    private <T> T withRequestId(String id, Supplier<T> submit) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            MDC.put("requestId", id);
            log.info("SUBMIT id={}", id);
            return submit.get(); // Gọi public method của BEAN KHÁC.
        } finally {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }
}
```

Phương thức `withRequestId` đặt MDC **chỉ trong phạm vi submit**, rồi khôi phục context phía caller bằng `finally`. Executor decorator đã chụp snapshot, nên worker vẫn nhìn thấy request ID sau khi caller xóa nó. `Supplier<T>` ở đây chạy trên thread gọi để submit; nó **không tự tạo asynchronous execution**.

Trong runner, `get(...)` được dùng để quan sát kết quả một cách có chủ đích. Với HTTP handler thực, gọi `get`/`join` trên request thread sẽ chặn request thread; nên cân nhắc để framework xử lý một async response (`CompletionStage`, `DeferredResult`...) hoặc thiết kế job ID + polling/event khi nghiệp vụ dài.

**`src/main/java/dev/example/asynclab/AsyncLabApplication.java`**

```java
package dev.example.asynclab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class AsyncLabApplication {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                     SpringApplication.run(AsyncLabApplication.class, args)) {
            // ApplicationRunner hoàn thành trước khi run() trả về.
            // Đóng context để pool được shutdown gọn.
        }
    }
}
```

Chạy từ thư mục `async-lab`:

```bash
java -version
mvn -version
mvn spring-boot:run
```

Không cần endpoint hay database. Maven sẽ tải artifact trong lần đầu; nếu chạy trong môi trường không có mạng, cần cache dependency từ trước.

## 7. Đọc kết quả log

Thời gian, tên thread cụ thể và thứ tự các dòng khởi động Spring có thể khác nhau. Tìm những **dấu hiệu cốt lõi**, không so sánh nguyên log theo từng byte:

```text
[main]  [requestId=r-ok]    ... SUBMIT id=r-ok
[lab-1] [requestId=r-ok]    ... WORK_START id=r-ok mdc=r-ok
[lab-1] [requestId=r-ok]    ... WORK_OK id=r-ok
[main]  [requestId=none]    ... SUCCESS result=report:r-ok

[main]  [requestId=r-fail]  ... SUBMIT id=r-fail
[lab-2] [requestId=r-fail]  ... WORK_START id=r-fail mdc=r-fail
[main]  [requestId=none]    ... FUTURE_FAILURE cause=SIMULATED_FAILURE id=r-fail

[main]  [requestId=r-slow]  ... SUBMIT id=r-slow
[lab-1] [requestId=r-slow]  ... WORK_START id=r-slow mdc=r-slow
[main]  [requestId=none]    ... CALLER_TIMEOUT id=r-slow done=false
[lab-1] [requestId=r-slow]  ... WORK_OK id=r-slow
[main]  [requestId=none]    ... LATE_SUCCESS result=report:r-slow

[main]  [requestId=r-void]  ... SUBMIT id=r-void
[lab-2] [requestId=r-void]  ... VOID_START id=r-void mdc=r-void
[lab-2] [requestId=r-void]  ... VOID_FAILURE method=failVoid requestId=r-void ...
[main]  [requestId=none]    ... VOID_HANDLER_SEEN
[main]  [requestId=none]    ... DEMO_DONE
```

Đây là **log minh họa theo các marker**, không phải stdout nguyên văn: Logback có thêm timestamp, tên logger, level và stack trace cho lỗi `void`; `lab-1`/`lab-2` có thể hoán đổi do thread scheduling. `SUCCESS`, `FUTURE_FAILURE`, `CALLER_TIMEOUT`, `LATE_SUCCESS`, `VOID_FAILURE`, `DEMO_DONE` là các mốc cần kiểm tra.

Bảng quan sát:

| Kịch bản | Điều mong đợi | Điều không nên suy diễn |
| --- | --- | --- |
| `r-ok` | Worker log đúng `requestId`; caller nhận `report:r-ok` | `get()` trong runner không phải phương thức HTTP không blocking |
| `r-fail` | Future hoàn thành với lỗi; caller lấy nguyên nhân từ `ExecutionException` | Không có nghĩa exception được ném đồng bộ ở thời điểm gọi `generate()` |
| `r-slow` | `get(50 ms)` timeout, sau đó `get(2 s)` vẫn lấy kết quả | Timeout lúc đợi **không** chứng minh task đã dừng |
| `r-void` | Handler log exception và request ID; latch xác nhận handler chạy | Caller không nhận `ExecutionException` vì `void` không trả future |

Trong ví dụ `r-slow`, giá trị `done=false` gần như chắc chắn theo mốc 500 ms so với 50 ms; trên máy quá tải, thời gian scheduling có thể biến động. Điểm cốt lõi là `get(timeout)` không hoàn tất exceptional hay hủy future gốc: caller chỉ ngừng chờ trong lần gọi `get` đó.

## 8. Lỗi bất đồng bộ: đặt cơ chế xử lý đúng nơi

### 8.1. Future trả về có lỗi

Với kết quả `CompletableFuture<T>`, có hai cách thường dùng:

```java
// get(): blocking, có thể ném checked exception.
try {
    String value = future.get(300, TimeUnit.MILLISECONDS);
} catch (TimeoutException ex) {
    // Caller đã hết ngân sách chờ.
} catch (ExecutionException ex) {
    Throwable root = ex.getCause();
    // Xử lý lỗi nghiệp vụ thực tế.
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Dừng đường xử lý của caller cho phù hợp.
}
```

Đoạn trên là **mẫu chèn vào caller đã có biến `future`**, không phải một chương trình Java độc lập. Không nuốt `InterruptedException` mà quên khôi phục interrupt flag.

Cách non-blocking hơn là gắn completion callback:

```java
CompletableFuture<String> observed = future.whenComplete((value, error) -> {
    if (error != null) {
        // Ghi metric/log thất bại; không đổi kết quả của future.
    }
});
```

`whenComplete` có mục đích quan sát; `exceptionally`/`handle` phù hợp khi muốn **biến đổi kết quả** hoặc trả fallback có chủ đích. Nếu `future` đã hoàn tất trước khi đăng ký callback, callback không `Async` có thể chạy ngay ở thread đăng ký; nếu chưa, nó có thể chạy tại worker hoàn tất future. Vì vậy **không được giả định MDC của callback luôn là MDC của request**. Muốn callback chạy với executor/context nhất quán, chọn executor có context propagation hoặc truyền `requestId` tường minh.

### 8.2. `@Async void`

`AsyncUncaughtExceptionHandler` dành riêng cho lỗi không có future để trả về. Handler có thể log, tăng counter, gửi event nội bộ phù hợp; phải tự đảm bảo handler không gây lỗi dây chuyền. Đừng dùng `void @Async` cho nghiệp vụ mà caller bắt buộc biết thành công/thất bại. Dùng `CompletableFuture<Result>` hoặc một job record có trạng thái.

### 8.3. Exception ngay lúc submit

Executor hữu hạn có thể từ chối trước khi task chạy. Lỗi ở **thời điểm gọi proxy** cần được xử lý tại điểm submit, chẳng hạn:

```java
try {
    CompletableFuture<String> result =
            service.generate("job-123", 100, false);
    // Gắn xử lý kết quả/lỗi cho result.
} catch (org.springframework.core.task.TaskRejectedException ex) {
    // Pool/queue không nhận được task: trả tín hiệu quá tải
    // hoặc áp dụng backoff/retry có giới hạn.
}
```

Đây là đoạn minh họa trong bean khác đã inject `service`; không chạy độc lập. Tùy executor/adapter, lỗi từ chối có thể lộ dưới một loại `RejectedExecutionException` thích hợp, nên kiểm tra cấu hình thực tế. Không retry tức thì vô hạn: nó tạo thêm tải đúng lúc pool đã đầy.

## 9. Timeout của caller khác timeout của công việc

Cần phân biệt ít nhất bốn loại thời hạn:

| Nơi đặt deadline | Ví dụ | Điều được bảo đảm |
| --- | --- | --- |
| Chờ kết quả | `future.get(50, MILLISECONDS)` | Thread gọi ngừng đợi sau giới hạn; **không** tự hủy tác vụ |
| Hoàn tất future | `future.orTimeout(50, MILLISECONDS)` | Chính `CompletableFuture` được hoàn tất exceptional nếu chưa xong; không mặc định ngắt phép tính đang chạy |
| Fallback sau timeout | `future.completeOnTimeout(fallback, 50, MILLISECONDS)` | Future có giá trị fallback nếu chưa xong; công việc gốc có thể vẫn tiếp tục |
| I/O / nghiệp vụ | HTTP connect/read timeout, query timeout, job deadline và kiểm tra interrupt | Tác động trực tiếp hơn đến chỗ chờ thật, vẫn phụ thuộc API và khả năng hủy của hệ thống phía sau |

`orTimeout` và `completeOnTimeout` **thay đổi chính future được gọi**, không phải chỉ tạo một bản sao độc lập. Vì vậy các consumer dùng chung future có thể bị ảnh hưởng. `CompletableFuture.cancel(true)` của JDK cũng không bảo đảm ngắt worker đang thực thi; tài liệu `CompletableFuture` nói cờ `mayInterruptIfRunning` không điều khiển cơ chế xử lý interrupt của lớp này. Đừng gọi timeout/cancel là “đã hủy tác vụ” nếu chưa quan sát nguồn xử lý thật sự dừng.

Khi xử lý một yêu cầu dài, nên có **deadline xuyên suốt**: tính thời gian còn lại rồi truyền xuống HTTP client, DB driver, queue và nghiệp vụ; bảo đảm idempotency để retry/hủy không tạo kết quả trùng lặp. Với I/O không hỗ trợ hủy, có thể cần đánh dấu kết quả muộn là không còn hợp lệ. Đo riêng `caller_timeout`, `task_completed_late`, `task_cancelled` và lỗi phụ thuộc.

### Bài Java thuần chứng minh `get(timeout)` không dừng task

Tạo file `WaitVsWork.java`, không cần Spring:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WaitVsWork {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<String> future = executor.submit(() -> {
                release.await();
                return "finished";
            });

            try {
                future.get(50, TimeUnit.MILLISECONDS);
            } catch (TimeoutException expected) {
                System.out.println("wait=timed-out");
            }

            System.out.println("workDone=" + future.isDone());
            release.countDown();
            System.out.println("outcome=" +
                    future.get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
```

Chạy:

```bash
javac WaitVsWork.java
java WaitVsWork
```

Kết quả dự kiến, độc lập tốc độ xử lý của task vì `CountDownLatch` giữ task chưa thể hoàn tất:

```text
wait=timed-out
workDone=false
outcome=finished
```

`Future.get(timeout)` trả `TimeoutException` nhưng `future.isDone()` vẫn `false`. Khi gọi `release.countDown()`, task mới được phép hoàn thành và lần `get` tiếp theo trả `"finished"`. Ví dụ này giải thích timeout ở **nơi chờ**; nó không đo độ trễ của Spring hay mạng.

## 10. MDC: nguyên nhân mất dấu vết và giới hạn truyền context

MDC (Mapped Diagnostic Context) thường gắn `requestId`/`traceId` vào log của thread hiện tại. Executor worker là thread khác và còn được tái sử dụng; sao chép từ caller là một thao tác riêng, không tự có trong `@Async` mặc định.

Đối chiếu trước–sau:

```text
Không có TaskDecorator:
caller: requestId=R1, SUBMIT
worker: requestId=none, WORK_START

Có TaskDecorator:
caller: requestId=R1, SUBMIT
worker: requestId=R1, WORK_START
worker: requestId=R1, WORK_OK
worker: restored previous MDC (không giữ R1)
```

Bài này chỉ truyền **SLF4J MDC map**, không tự truyền SecurityContext, `LocaleContextHolder`, transaction, request-scoped bean hay toàn bộ distributed tracing context. `@Transactional` của caller cũng không mặc định đi qua thread boundary. Nếu cần truyền các context đó, dùng cơ chế chính thức tương ứng và kiểm tra chiến lược context propagation trong stack đang dùng.

Các quy tắc vận hành:

1. Đặt `requestId` tại boundary rõ ràng (HTTP filter/interceptor, message consumer, job entrypoint); khi có trace ID của hệ thống observability, dùng nó thống nhất thay vì tạo ID ngẫu nhiên không liên kết.
2. Chụp snapshot đúng lúc submit; không đặt một `Map` dùng chung cho tất cả request.
3. Trong worker, lưu MDC cũ, `setContextMap` hoặc `clear`, rồi **khôi phục trong `finally`** để không rò dữ liệu qua thread được tái sử dụng.
4. Với callback `thenApplyAsync`, executor phụ hoặc publisher khác, phải đánh giá propagation riêng; decorator của `labExecutor` không tự áp dụng cho `ForkJoinPool.commonPool()`.
5. Không đặt access token, mật khẩu, nội dung cá nhân hoặc payload nhạy cảm vào MDC/log. Giới hạn nhãn và độ dài ID để tránh bùng nổ log/metric cardinality.
6. Log có thể bị xử lý trễ hoặc đảo thứ tự giữa thread; dùng `requestId`/`traceId` và timestamp để tương quan, không dựa vào thứ tự mắt thấy của toàn bộ console.

Trong ứng dụng Spring Boot sử dụng Micrometer Context Propagation hoặc tracing, có thể dùng `ContextPropagatingTaskDecorator` theo phiên bản Spring tương thích thay vì tự viết cơ chế riêng. Trước khi áp dụng, xác nhận các accessor và context nào thực sự được capture/restore; điều đó không đồng nghĩa mọi `ThreadLocal` đều tự động đi theo.

## 11. Checklist quan sát và bài kiểm thử mở rộng

Đầu tiên chạy ứng dụng nguyên bản và xác nhận:

- Có hai tên thread khác nhau cho caller và worker (ví dụ `main` và `lab-1`), chứng minh `@Async` chạy qua proxy.
- Các dòng `WORK_START` và `VOID_START` có MDC trùng request ID được đặt tại `SUBMIT`.
- `r-fail` đi qua `FUTURE_FAILURE`; `r-void` đi qua `VOID_FAILURE`.
- `r-slow` có `CALLER_TIMEOUT` **trước** `LATE_SUCCESS`.
- Sau `r-void`, chương trình in `VOID_HANDLER_SEEN` và `DEMO_DONE` rồi đóng context. Không có request ID của một task xuất hiện trên log của task khác.

Thử thay đổi có mục đích:

| Thử nghiệm | Thay đổi | Kết quả cần tự lý giải |
| --- | --- | --- |
| A: Bỏ MDC propagation | Tạm không gọi `setTaskDecorator` | Worker không tự có request ID từ caller; log mất liên kết |
| B: Cố tình không `finally` restore | Bỏ khôi phục MDC trong decorator, sau đó chạy nhiều request | Worker tái sử dụng có nguy cơ lẫn context; sửa lại ngay sau khi quan sát |
| C: Timeout ngắn hơn task | Giữ `500 ms` nhưng `get(10 ms)` | Caller hết thời gian chờ, worker vẫn hoàn tất về sau |
| D: Chỉ đổi sang `@Async void` | Quan sát handler | Không có future cho caller kiểm tra kết quả |
| E: Gọi `generate` từ cùng bean | Tạo method tự gọi trong service | Trong proxy mode mặc định lời gọi nội bộ bỏ qua advice; cần đi qua bean proxy |
| F: Làm đầy executor | Chặn worker với latch, submit nhiều task hơn `pool + queue` | Lỗi từ chối lúc submit phải có policy rõ ràng |
| G: Đổi `get` thành `orTimeout` | Áp dụng vào một future riêng | Future hoàn tất exceptional khi hết hạn nhưng không có bằng chứng worker đã dừng |

Sau mỗi thay đổi, trả về phiên bản chạy đúng trước khi thử tiếp, rồi đối chiếu **độ trễ end-to-end, số task hoàn tất, số lỗi, timeout, rejection và hoạt động của downstream**. Tác vụ phụ thuộc vào mạng/DB cần timeout riêng vì một thread bị kẹt có thể giữ tài nguyên lâu sau khi caller đã hết hạn.

## 12. Các lỗi thường gặp khi đưa `@Async` vào ứng dụng thật

- **Self-invocation:** `this.generate()` trong cùng service chạy đồng bộ theo proxy mode mặc định; giải pháp là gọi qua bean khác hoặc thiết kế lại boundary.
- **Quên `@EnableAsync`:** annotation có thể chỉ là metadata, phương thức vẫn chạy trên thread gọi.
- **Sai executor:** nhiều pool cùng tồn tại, `@Async` không có qualifier; dùng `@Async("labExecutor")` và kiểm tra tên thread/metrics.
- **Dùng `void` rồi cố `try/catch` lỗi ở caller:** lỗi trong worker không quay lại caller qua stack ban đầu; cần future hoặc handler.
- **Lẫn `get` với `join`:** `get` dùng `ExecutionException`/`InterruptedException` và hỗ trợ timeout; `join` dùng `CompletionException` không checked, và không nhận timeout parameter.
- **Cho rằng `orTimeout` giết task:** timeout thay đổi completion của future; phải cấu hình deadline/cancel hợp tác ở công việc và I/O.
- **Gắn `supplyAsync` bên trong `@Async`:** tạo thêm thread hop, mặc định có thể dùng common pool, khó theo dõi lỗi và MDC.
- **Log `requestId` rồi quên clear:** thread pool dùng lại worker, có nguy cơ gắn nhầm ID cho task kế tiếp.
- **Dùng MDC như transaction/security context:** MDC chỉ phục vụ dữ liệu chẩn đoán, không cấp quyền và không mở transaction.
- **Tự động retry mọi exception:** cần phân loại lỗi tạm thời/vĩnh viễn, backoff, giới hạn số lần và idempotency.
- **Không đếm rejection và timeout:** throughput chỉ tính task submit có thể che giấu backlog và thất bại; đo kết quả hoàn tất thật.
- **Đóng executor quá sớm:** chương trình console có thể kết thúc khi task chưa xong. Trong demo, runner đợi đủ các trường hợp rồi application context mới đóng; trong service thật, phải có shutdown policy và giới hạn await hợp lý.

## 13. Câu hỏi tự đánh giá

1. Vì sao `@Async("labExecutor")` có thể trả `CompletableFuture` rất nhanh nhưng lỗi chỉ xuất hiện sau đó?
2. Nếu `get(50 ms)` trả `TimeoutException`, liệu `future.isDone()` bắt buộc phải là `true`? Khi nào công việc thực sự dừng?
3. Vì sao cùng một task có log `requestId=r-ok` ở caller nhưng `requestId=none` ở worker khi không có `TaskDecorator`?
4. Tại sao khôi phục MDC cũ trong `finally` quan trọng ngay cả khi chỉ có hai worker?
5. Khi pool đầy, lỗi submit khác gì lỗi trong `generate()`? Có nên retry ngay liên tục không?
6. Nếu đổi `generate()` thành `void` và bỏ `AsyncUncaughtExceptionHandler`, caller có biết task đã thất bại hay không?
7. Nếu callback `whenComplete` chạy trên một thread khác, làm sao bảo đảm log callback mang đúng correlation ID?

**Gợi ý:** (1) Proxy giao việc sang executor và trả handle; lỗi tương lai nằm trong future. (2) Không; timeout chỉ giới hạn chờ, công việc dừng do hoàn tất, cơ chế hủy/interrupt hoặc timeout I/O thực sự. (3) MDC gắn với thread. (4) Worker tái sử dụng, cần chống rò context. (5) Rejection có thể ném ngay khi submit; retry không giới hạn làm quá tải nặng hơn. (6) Caller không có future; handler/log là kênh quan sát riêng. (7) Truyền ID tường minh hoặc dùng context propagation và executor được cấu hình.

## Tài liệu tham khảo

- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@EnableAsync`, `@Async`, executor qualification, exception handling, `TaskDecorator`.
- [Spring Framework – `AsyncConfigurer`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/AsyncConfigurer.html): thiết lập async executor và uncaught handler.
- [Spring Boot 3.3 – Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.3/reference/features/task-execution-and-scheduling.html): hành vi auto-configure executor và các lựa chọn cấu hình.
- [JDK 17 – `Future`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): `get(timeout)`, `cancel`, `isDone`.
- [JDK 17 – `CompletableFuture`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): `orTimeout`, `completeOnTimeout`, `whenComplete`, `handle`, `join`.
- [SLF4J – Mapped Diagnostic Context](https://www.slf4j.org/manual.html): cơ chế MDC và giới hạn theo thread.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="design-practice-006">Thực hành lịch cập nhật dữ liệu với fixedDelay rồi cron</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành lịch cập nhật dữ liệu với fixedDelay rồi cron

## Mục tiêu bài thực hành

Ta sẽ làm một ứng dụng **Spring Boot (Spring Framework Scheduling)** tự lấy dữ liệu từ một nguồn giả lập và thay thế bản snapshot đang phục vụ ứng dụng. Bài thực hành đi **theo đúng hai bước**:

1. Chạy cập nhật bằng `@Scheduled(fixedDelayString = ...)` để lần sau bắt đầu **sau khi lần trước kết thúc** một khoảng cố định.
2. Giữ nguyên logic cập nhật, **chuyển sang cron** để chạy ở các mốc thời gian theo đồng hồ, có múi giờ rõ ràng.

Kết quả mong đợi: chạy được một project Java 17+, xem log START/OK, phân biệt nhịp thực thi, tự kiểm tra dữ liệu sau mỗi lần refresh, kiểm tra cron expression, và hiểu rủi ro khi đem lịch này lên môi trường nhiều instance hoặc gặp nguồn dữ liệu lỗi.

Bài sử dụng nguồn giả lập để không cần database/API bên ngoài. Khi đưa vào ứng dụng thật, thay phần lấy dữ liệu bằng truy vấn DB hoặc HTTP client và giữ nguyên nguyên tắc **chỉ công bố snapshot mới sau khi đọc thành công toàn bộ**.

## 1. Trước khi viết code: hiểu hai kiểu lịch

| Tiêu chí | `fixedDelay` | `cron` |
| --- | --- | --- |
| Khi nào chạy lần kế? | Sau khi **lần chạy trước hoàn tất** rồi đợi thêm một khoảng. | Tại các mốc khớp biểu thức lịch và múi giờ. |
| Dùng khi nào? | Polling nguồn dữ liệu, muốn nghỉ một khoảng sau mỗi lần xử lý. | Đồng bộ đúng giờ, đúng phút, theo lịch nghiệp vụ. |
| Phụ thuộc thời gian chạy task? | Có: chu kỳ giữa hai lần bắt đầu xấp xỉ thời gian chạy + delay. | Mốc dự kiến dựa trên đồng hồ; task chậm hoặc scheduler bận có thể làm trễ/bỏ lỡ một số mốc. |
| Cấu hình ban đầu | Có thể dùng `initialDelay` / `initialDelayString`. | Dựa vào mốc cron kế tiếp; không dùng `initialDelay` cho cùng cron trigger. |
| Múi giờ | Delay là khoảng thời gian, không gắn giờ địa phương. | Nên chỉ rõ `zone`, ví dụ `Asia/Ho_Chi_Minh`. |

Giả sử một lần cập nhật mất **2 giây**:

- `fixedDelay = 5000 ms`, `initialDelay = 1000 ms`: nếu khởi động ở thời điểm tương đối `t=0`, các lần bắt đầu xấp xỉ `t=1s`, `8s`, `15s` (1s + 2s làm việc + 5s nghỉ). Đây là minh họa khi chỉ có một job và scheduler không bị chặn bởi việc khác.
- Cron `0/10 * * * * *`: dự kiến có mốc giây **00, 10, 20, 30, 40, 50** của mỗi phút. Nếu task mất 2 giây và tài nguyên sẵn có, log START xuất hiện gần những mốc đó; Spring/OS không cam kết độ chính xác thời gian tuyệt đối.

Đừng nhầm `fixedDelay` với `fixedRate`: `fixedRate` đặt nhịp theo khoảng giữa các lần kích hoạt dự kiến; `fixedDelay` dựa vào thời điểm hoàn tất. Cũng đừng mặc định cron chạy đúng một lần trên **toàn cụm**: `@Scheduled` thường được đăng ký riêng ở mỗi instance của ứng dụng.

## 2. Kiến trúc bài thực hành

Ta tách nhiệm vụ thành ba phần để dễ thử và dễ thay đổi lịch:

```text
                 @EnableScheduling
                        |
             Chọn profile khi khởi động
                  /           \
         "delay"                 "cron"
      FixedDelayJob            CronJob
                  \           /
                CatalogRefreshService
                 /            \
         CatalogSource      AtomicReference<Snapshot>
              |
       SimulatedCatalogSource
         (giả lập đọc 2s)
```

- Hai job là **hai bộ kích hoạt thay thế nhau**: mỗi lượt chỉ chọn một profile (`delay` hoặc `cron`). Không gắn cả hai annotation lịch vào cùng một method để “chuyển chế độ”.
- `CatalogRefreshService` chứa quy trình cập nhật duy nhất, nên thay đổi lịch **không sao chép logic nghiệp vụ**.
- `CatalogSource` là ranh giới kết nối nguồn thật. Implementation giả lập tạo dữ liệu khác nhau qua mỗi lượt đọc.
- `AtomicReference<Snapshot>` thay cả snapshot bằng một phép cập nhật tham chiếu: người đọc không thấy trạng thái cập nhật dở.
- `AtomicBoolean` chặn hai lời gọi `refresh()` chồng lấn **trong cùng một JVM** nếu sau này job bị kích hoạt từ nhiều đường gọi. Nó không khóa được các instance khác.

## 3. Tạo project Java 17+ hoàn chỉnh

Yêu cầu: **JDK 17 trở lên**, Gradle tương thích Spring Boot 3.3.x (ví dụ Gradle 8.5), kết nối Maven Central để tải dependency lần đầu. Dùng các đường dẫn dưới đây; tất cả các file Java có package `com.example.refresh`.

**`settings.gradle`**

```groovy
rootProject.name = 'scheduled-refresh-lab'
```

**`build.gradle`**

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

**`src/main/resources/application.properties`**

```properties
# Mặc định chỉ chạy job fixedDelay; đổi profile bằng đối số CLI.
spring.profiles.default=delay

# Spring Boot 3.3: scheduler pool một thread cho bài thực hành.
spring.task.scheduling.pool.size=1
spring.task.scheduling.thread-name-prefix=refresh-

# Bước 1: sau khi một lượt xong, chờ 5 giây rồi chạy lượt mới.
refresh.fixed-delay-ms=5000
refresh.initial-delay-ms=1000

# Bước 2: tại giây 0,10,20,... của mỗi phút theo múi giờ chỉ định.
refresh.cron=0/10 * * * * *
refresh.zone=Asia/Ho_Chi_Minh

# Chỉ để mô phỏng độ trễ đọc nguồn, không phải thao tác production.
refresh.simulated-read-ms=2000
```

**`src/main/java/com/example/refresh/RefreshApplication.java`**

```java
package com.example.refresh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RefreshApplication {
    public static void main(String[] args) {
        SpringApplication.run(RefreshApplication.class, args);
    }
}
```

`@EnableScheduling` cho phép Spring tìm các method `@Scheduled` trên những bean được Spring quản lý. Chỉ đặt `@Scheduled` lên method tự tạo bằng `new` mà không đăng ký bean sẽ không tạo lịch.

**`src/main/java/com/example/refresh/CatalogSource.java`**

```java
package com.example.refresh;

import java.util.List;

@FunctionalInterface
public interface CatalogSource {
    List<String> load();
}
```

**`src/main/java/com/example/refresh/SimulatedCatalogSource.java`**

```java
package com.example.refresh;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SimulatedCatalogSource implements CatalogSource {
    private final AtomicInteger readNumber = new AtomicInteger();
    private final long simulatedReadMillis;

    public SimulatedCatalogSource(
            @Value("${refresh.simulated-read-ms:2000}") long simulatedReadMillis) {
        this.simulatedReadMillis = simulatedReadMillis;
    }

    @Override
    public List<String> load() {
        // Mô phỏng DB query/HTTP call tốn khoảng 2 giây.
        try {
            Thread.sleep(simulatedReadMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Source read interrupted", ex);
        }

        int n = readNumber.incrementAndGet();
        return List.of("sku-A-stock-" + n, "sku-B-stock-" + n);
    }
}
```

`Thread.sleep(...)` ở đây **chỉ mô phỏng độ trễ** để quan sát khoảng cách giữa các lần chạy. Không dùng sleep làm cơ chế cập nhật dữ liệu thật. Trong production, `load()` cần truy vấn nguồn, có connection/read timeout và xử lý thất bại rõ ràng.

**`src/main/java/com/example/refresh/CatalogRefreshService.java`**

```java
package com.example.refresh;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CatalogRefreshService {
    private static final Logger log =
            LoggerFactory.getLogger(CatalogRefreshService.class);

    private final CatalogSource source;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Snapshot> latest = new AtomicReference<>(
            new Snapshot(0, Instant.EPOCH, List.of()));

    public CatalogRefreshService(CatalogSource source) {
        this.source = source;
    }

    public void refresh() {
        // Chặn hai lời gọi trùng nhau trong cùng tiến trình.
        if (!running.compareAndSet(false, true)) {
            log.warn("REFRESH SKIPPED: another invocation is running");
            return;
        }

        Instant started = Instant.now();
        long previousVersion = latest.get().version();
        log.info("REFRESH START previousVersion={} thread={}",
                previousVersion, Thread.currentThread().getName());

        try {
            List<String> fetched = List.copyOf(source.load());
            Snapshot next = new Snapshot(
                    previousVersion + 1, Instant.now(), fetched);

            // Chỉ công bố snapshot sau khi đọc thành công toàn bộ.
            latest.set(next);
            log.info("REFRESH OK version={} items={} elapsedMs={}",
                    next.version(), next.items().size(),
                    Duration.between(started, Instant.now()).toMillis());
        } catch (RuntimeException ex) {
            // Lượt lỗi không ghi đè snapshot thành công gần nhất.
            log.error("REFRESH FAILED; keeping version={}",
                    latest.get().version(), ex);
        } finally {
            running.set(false);
        }
    }

    public Snapshot current() {
        return latest.get();
    }

    public record Snapshot(
            long version, Instant refreshedAt, List<String> items) {
    }
}
```

Dữ liệu trong ví dụ có một “phiên bản” tăng sau mỗi lần đọc thành công. `List.copyOf` tạo danh sách không thể sửa qua reference công bố; `AtomicReference` giúp các luồng đọc snapshot nhất quán. Nếu nguồn lỗi, log ghi FAILED và snapshot trước giữ nguyên. Với DB thật, không suy ra từ ví dụ rằng nhiều bảng được cập nhật nguyên tử; cần transaction đúng phạm vi và cơ chế idempotency khi ghi dữ liệu.

## 4. Bước 1 — Chạy fixedDelay

**`src/main/java/com/example/refresh/FixedDelayRefreshJob.java`**

```java
package com.example.refresh;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("delay")
public class FixedDelayRefreshJob {
    private final CatalogRefreshService service;

    public FixedDelayRefreshJob(CatalogRefreshService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${refresh.fixed-delay-ms:5000}",
            initialDelayString = "${refresh.initial-delay-ms:1000}")
    public void updateCatalog() {
        service.refresh();
    }
}
```

`@Profile("delay")` chỉ đăng ký bean này khi profile delay hoạt động. `fixedDelayString` và `initialDelayString` nhận property placeholder. Với giá trị số thuần như `5000`, đơn vị mặc định là **mili-giây**. Spring còn hỗ trợ một số dạng duration với `*String`, nhưng bài thực hành dùng số rõ đơn vị để dễ đối chiếu.

Trong thư mục project có Gradle Wrapper, chạy `./gradlew test`; trên Windows dùng `.\gradlew.bat test`. Nếu chưa có wrapper và đã cài Gradle, dùng lệnh `gradle` như sau:

```bash
gradle test
gradle bootRun --args="--spring.profiles.active=delay"
```

Hoặc chỉ chạy `gradle bootRun` vì `spring.profiles.default=delay`. Giữ ứng dụng chạy để xem log từ hai lượt trở lên, rồi dừng bằng Ctrl+C.

**Cách đọc log:** Thời gian in ra phụ thuộc máy, scheduler và tải hệ thống; dưới đây là **mốc tương đối minh họa**, không phải stdout cố định:

| Mốc gần đúng | Sự kiện |
| --- | --- |
| `t=1s` | `REFRESH START previousVersion=0` |
| `t=3s` | `REFRESH OK version=1 items=2` |
| `t=8s` | `REFRESH START previousVersion=1` |
| `t=10s` | `REFRESH OK version=2 items=2` |
| `t=15s` | `REFRESH START previousVersion=2` |

Quan sát khoảng cách từ **OK lần 1 đến START lần 2** khoảng 5 giây, thay vì 5 giây giữa hai lần START. Thay `refresh.simulated-read-ms=4000`, restart app, vẫn giữ delay 5 giây: khoảng cách hai START sẽ xấp xỉ 9 giây khi không có tác vụ khác ảnh hưởng.

**Bài tự kiểm tra:** Đổi delay từ 5 giây thành 3 giây, để thời gian đọc giả lập là 2 giây; dự đoán hai lần START cách nhau khoảng 5 giây rồi quan sát log. Sau đó cho thời gian đọc là 6 giây: khoảng cách START dự kiến khoảng 9 giây. Các con số là nhịp tương đối, không phải SLA thời gian.

## 5. Bước 2 — Chuyển sang cron mà không viết lại nghiệp vụ

**`src/main/java/com/example/refresh/CronRefreshJob.java`**

```java
package com.example.refresh;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("cron")
public class CronRefreshJob {
    private final CatalogRefreshService service;

    public CronRefreshJob(CatalogRefreshService service) {
        this.service = service;
    }

    @Scheduled(
            cron = "${refresh.cron:0/10 * * * * *}",
            zone = "${refresh.zone:Asia/Ho_Chi_Minh}")
    public void updateCatalog() {
        service.refresh();
    }
}
```

Dừng tiến trình ở bước 1, sau đó khởi động lại bằng đúng **một profile `cron`**:

```bash
gradle bootRun --args="--spring.profiles.active=cron"
```

Khi profile `cron` hoạt động, bean `FixedDelayRefreshJob` không được tạo, và ngược lại. Giữ nguyên `CatalogRefreshService` và `CatalogSource` để việc so sánh hai lịch công bằng.

### Cú pháp cron của Spring: sáu trường

```text
0/10  *  *  *  *  *
 |    |  |  |  |  |
giây phút giờ ngày tháng thứ
```

Khác với cron năm trường quen thuộc của một số hệ thống UNIX, Spring dùng **sáu trường**, từ trái sang phải: giây, phút, giờ, ngày trong tháng, tháng, thứ trong tuần. Một số lịch minh họa:

| Biểu thức | Ý nghĩa theo `zone` |
| --- | --- |
| `0/10 * * * * *` | Mỗi 10 giây tại giây 0, 10, 20, 30, 40, 50. |
| `0 */5 * * * *` | Tại giây 0 của mỗi phút chia hết cho 5. |
| `0 0 2 * * *` | Mỗi ngày lúc 02:00:00. |
| `0 30 8 * * MON-FRI` | 08:30:00 vào thứ Hai đến thứ Sáu. |
| `-` | Vô hiệu hóa trigger cron qua property, nếu muốn tạm dừng lịch. |

`zone = "Asia/Ho_Chi_Minh"` định nghĩa cách quy đổi lịch theo giờ Việt Nam; nó không làm cho tác vụ luôn chạy đúng đến từng mili-giây. Ở múi giờ có DST, một số giờ địa phương có thể bị thiếu hoặc lặp; cần test riêng nếu nghiệp vụ phụ thuộc những ngày chuyển DST.

**Quan sát:** với `0/10 * * * * *` và nguồn giả lập 2 giây, các dòng START dự kiến xuất hiện gần giây 00, 10, 20... của mỗi phút; các dòng OK theo sau khoảng 2 giây. Bây giờ đổi `refresh.simulated-read-ms=12000`, restart và quan sát: task kéo dài hơn chu kỳ 10 giây. **Đừng mong Spring tự tạo các lần chạy bù cho mọi mốc bị lỡ.** Cách xử lý mốc bị lỡ phụ thuộc scheduler/trigger và trạng thái tác vụ; khi scheduler bận, các lượt có thể trễ hoặc bỏ qua. Cron mô tả các thời điểm kích hoạt dự kiến, không tạo bảo đảm exactly-once.

Với lịch chạy hằng ngày trong sản phẩm thực, đổi thành `refresh.cron=0 0 2 * * *`. Nhớ rằng lịch lúc 02:00 sẽ cần đợi đến 02:00 tiếp theo để quan sát; dùng lịch 10 giây chỉ cho phòng thực hành.

## 6. Kiểm thử nhanh mà không chờ scheduler ngoài đời

Kiểm thử **logic cập nhật** và **cú pháp cron** bằng unit test xác định; kiểm tra chạy theo thời gian là một bài test tích hợp riêng.

**`src/test/java/com/example/refresh/CatalogRefreshServiceTest.java`**

```java
package com.example.refresh;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import static org.junit.jupiter.api.Assertions.*;

class CatalogRefreshServiceTest {

    @Test
    void successfulRefreshReplacesEntireSnapshot() {
        AtomicInteger sourceReads = new AtomicInteger();
        CatalogSource source = () ->
                List.of("sku-" + sourceReads.incrementAndGet());
        CatalogRefreshService service = new CatalogRefreshService(source);

        assertEquals(0, service.current().version());
        service.refresh();

        assertEquals(1, service.current().version());
        assertEquals(List.of("sku-1"), service.current().items());
        assertEquals(1, sourceReads.get());
    }

    @Test
    void failedRefreshKeepsLastSuccessfulSnapshot() {
        AtomicInteger calls = new AtomicInteger();
        CatalogSource source = () -> {
            if (calls.getAndIncrement() == 0) {
                return List.of("initial");
            }
            throw new IllegalStateException("source unavailable");
        };
        CatalogRefreshService service = new CatalogRefreshService(source);

        service.refresh();
        CatalogRefreshService.Snapshot valid = service.current();
        service.refresh();

        assertSame(valid, service.current());
        assertEquals(1, service.current().version());
    }

    @Test
    void cronExpressionTargetsNextTenthSecond() {
        CronExpression cron =
                CronExpression.parse("0/10 * * * * *");

        LocalDateTime from =
                LocalDateTime.of(2026, 1, 1, 12, 0, 3);
        LocalDateTime expected =
                LocalDateTime.of(2026, 1, 1, 12, 0, 10);

        assertEquals(expected, cron.next(from));
    }
}
```

Chạy `gradle test`. Test 1 chứng minh snapshot mới chỉ xuất hiện sau khi `refresh()` thực hiện thành công; test 2 cố tình tạo lần đọc thất bại để bảo vệ snapshot cũ; test 3 chứng minh cách parse **một cron expression cụ thể**. Chúng **không** chứng minh ứng dụng đã đăng ký đúng bean `@Scheduled` hoặc scheduler thật đã kích hoạt đúng thời gian.

Bổ sung smoke/integration test nếu sản phẩm cần chứng minh wiring: khởi động context với **một profile**, chu kỳ test ngắn, quan sát số lần gọi nguồn qua fake có thread-safe counter và chờ bằng cơ chế có timeout (ví dụ `CountDownLatch.await(timeout)`). Tránh `Thread.sleep` làm cách chờ mơ hồ trong test, tránh khẳng định exact timestamp trên CI, và bảo đảm context/scheduler được đóng sau test. Bài thực hành dùng quan sát log thực tế để kiểm tra hành vi thời gian.

## 7. Kiểm tra đúng yêu cầu nghiệp vụ trước khi vận hành thật

**Tiêu chí nghiệm thu thực hành**

1. `delay`: sau khi startup, một bản snapshot có version 1; các lần cập nhật tiếp theo tăng version đúng một; khoảng từ một lần OK đến START kế tiếp xấp xỉ `fixedDelay`.
2. `cron`: sau khi restart bằng profile cron, không có job delay hoạt động; các lần START xuất hiện gần mốc của biểu thức cron theo `zone`.
3. Dữ liệu: list mới được công bố nguyên vẹn; khi `load()` lỗi, version/data cũ vẫn được giữ, có log thất bại và lượt tiếp theo vẫn có thể thử lại.
4. Kiểm thử: các JUnit assertion pass; bài test cron parse không bị hiểu nhầm thành test scheduler đang chạy.
5. Cấu hình: chỉ một profile được bật; tắt hoặc đổi lịch có chủ đích và restart ứng dụng khi thay property tĩnh được resolve lúc đăng ký task.

**Các tình huống bắt buộc suy nghĩ nếu thay nguồn giả lập bằng DB/HTTP:**

- **Timeout và lỗi mạng:** giới hạn thời gian đọc; log/metric số lần thành công, thất bại, độ trễ, thời điểm refresh cuối. Không xuất bản snapshot nửa chừng.
- **Nguồn không có thay đổi:** cân nhắc version/hash hoặc `updatedAt` từ nguồn để tránh ghi cache/DB vô ích. Version tăng mỗi lần trong demo chỉ để dễ quan sát, không đại diện version nghiệp vụ.
- **Tác vụ lâu hơn khoảng cách lịch:** `fixedDelay` tạo khoảng nghỉ sau khi hoàn tất, còn cron có thể lỡ mốc khi scheduler bận. Xác định yêu cầu bỏ qua, chạy muộn hay cần một hàng đợi durable; không mặc định catch-up.
- **Nhiều @Scheduled khác:** Spring Boot thường tạo `ThreadPoolTaskScheduler` mặc định với **một** thread (trừ khi chỉnh cấu hình). Job dài có thể làm trễ job khác; tăng pool có thể giúp throughput nhưng tăng rủi ro chạy đồng thời.
- **Nhiều instance/replica:** mỗi JVM thường lên lịch riêng, nên cùng dữ liệu có thể bị refresh nhiều lần. `AtomicBoolean` trong ví dụ chỉ chống chồng lấn **nội bộ JVM**. Nếu yêu cầu một writer trên toàn cụm, dùng distributed lock/leader election hoặc scheduler có cơ chế phối hợp, thiết kế idempotent và kiểm tra recovery khi instance chết.
- **Transaction và idempotency:** lịch kích hoạt không bảo đảm exactly-once. Với side effect như cập nhật DB, dùng transaction đúng biên, unique key/version để chống ghi trùng, retry có giới hạn và cơ chế phục hồi lỗi.
- **Timezone/DST:** cron phụ thuộc `zone`; luôn ghi rõ múi giờ và ý nghĩa giờ nghiệp vụ, nhất là tại các hệ thống nhiều vùng.
- **Thay đổi lịch runtime:** ví dụ resolve placeholder khi bean được đăng ký; sửa file property không tự làm trigger cập nhật tức thời. Muốn sửa lịch động không restart, cần `TaskScheduler`/`Trigger` và cơ chế reschedule thích hợp.
- **Giữ phương thức schedule ngắn:** `@Scheduled` chỉ gọi service. Tránh đưa toàn bộ business logic, retry, I/O và logging vào annotation method.

## 8. Lỗi phổ biến cần tránh

| Lỗi | Hệ quả/cách sửa |
| --- | --- |
| Nghĩ `fixedDelay=5000` nghĩa là START cách nhau đúng 5 giây | Khoảng 5 giây được tính **sau completion**. Thời gian làm việc và scheduler contention làm START cách xa hơn. |
| Dùng cron năm trường như `*/10 * * * *` | Thiếu trường giây của Spring, có thể lỗi parse. Dùng sáu trường và test với `CronExpression`. |
| Bật cả `delay` và `cron` | Hai bộ schedule có thể cùng cập nhật nguồn, nhân đôi việc. Chỉ chọn một profile cho bài này. |
| Để cron dùng timezone của server vô tình | Deploy sang máy khác có thể đổi giờ nghiệp vụ. Đặt `zone` rõ ràng. |
| Nghĩ có `@Scheduled` là đủ | Cần Spring-managed bean và `@EnableScheduling` hoặc cấu hình tương đương. |
| Nghĩ một test xử lý dữ liệu pass là cron đã đúng | Test logic, cron parser và integration schedule là ba kiểm chứng khác nhau. |
| Tưởng một instance chạy là toàn cluster chạy một lần | Mỗi replica có lịch riêng. Dùng thiết kế idempotent/điều phối phân tán nếu yêu cầu một writer. |
| Ghi đè cache trước khi lấy xong dữ liệu hoặc nuốt lỗi không quan sát được | Giữ bản hợp lệ cũ, công bố dữ liệu mới atomically và có log/metric lỗi. |
| Dùng sleep, thời điểm tuyệt đối để assert trong CI | Test thời gian dễ flaky; dùng công cụ chờ có timeout và phạm vi assertion rõ ràng. |

## 9. Tự tổng kết

Sau bài này, hãy giải thích bằng lời:

1. Nếu task mất 4 giây và `fixedDelay=6s`, tại sao hai START thường cách nhau gần 10 giây?
2. Muốn đồng bộ đúng **02:00 giờ Việt Nam** mỗi ngày, cron sáu trường và `zone` cần khai báo thế nào?
3. Nếu hai replica cùng khởi động, có bao nhiêu bộ kích hoạt `@Scheduled` và điều gì ngăn ghi trùng?
4. Vì sao `AtomicReference` bảo vệ tính nhất quán của snapshot trong một JVM nhưng không thay thế transaction DB hoặc distributed lock?
5. Khi source lỗi giữa chừng, dữ liệu cũ có được giữ và failure có được quan sát không?

Hoàn thành hai chế độ với cùng một service, quan sát log và trả lời được năm câu trên nghĩa là bạn đã phân biệt được **khoảng nghỉ sau hoàn tất** của `fixedDelay` với **mốc lịch theo đồng hồ** của `cron`, và biết giới hạn của một lịch chạy tự động trong Spring.

## Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html) — `@EnableScheduling`, `@Scheduled` và `TaskScheduler`.
- [Spring Framework 6.2 — Javadoc `Scheduled`](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html) — fixedDelay/fixedRate/cron, six-field cron, `zone`, duration properties và hằng `CRON_DISABLED`.
- [Spring Boot 3.3 — Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.3/reference/features/task-execution-and-scheduling.html) — scheduler tự cấu hình và `spring.task.scheduling.pool.size`.
- [Spring Guide — Scheduling Tasks](https://spring.io/guides/gs/scheduling-tasks/) — ví dụ kích hoạt @Scheduled và chạy ứng dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="design-practice-007">Tái hiện tự gọi @Async, cạn nhóm luồng và mất ngữ cảnh</a>

<details>
<summary>Xem chi tiết</summary>

# Tái hiện tự gọi @Async, cạn nhóm luồng và mất ngữ cảnh

> **Phạm vi:** Spring Framework — concurrency, nhóm “Thiết kế và thực hành”. Bài này chủ động tạo ra ba lỗi thực tế của `@Async`: (1) gọi nội bộ qua `this` khiến advice không chạy; (2) nhóm luồng và hàng đợi bị lấp đầy khiến việc nộp tác vụ thất bại hoặc chờ quá lâu; (3) dữ liệu gắn với thread của request không tự có trong worker. Mục tiêu là **tái hiện có kiểm soát, đọc đúng tín hiệu, rồi sửa đúng nguyên nhân**.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Vẽ đúng đường đi của một lời gọi `@Async` trong chế độ proxy mặc định, và phân biệt “đối tượng có proxy” với “lời gọi đã đi qua proxy”.
- Tạo bài test tái hiện **self-invocation**, chứng minh đoạn code tưởng bất đồng bộ đang chạy trên thread của caller.
- Dựng một `ThreadPoolTaskExecutor` nhỏ với queue bằng 0, chủ động chiếm worker và kiểm tra `TaskRejectedException` khi pool hết khả năng tiếp nhận.
- Hiểu tác động của `corePoolSize`, `maxPoolSize`, `queueCapacity` và các chính sách từ chối tác vụ; không che lỗi bằng cách tăng pool vô hạn.
- Tái hiện việc mất dữ liệu `ThreadLocal` khi đổi thread, sửa bằng `TaskDecorator` có cơ chế **capture → set → restore**, và nhận ra loại context không nên sao chép sang worker.
- Thiết kế test không phụ thuộc vào `Thread.sleep`, có timeout và cleanup để chạy ổn định trong CI.

**Lưu ý:** `@Async` chỉ chuyển việc thực thi đến một executor. Nó không tự cung cấp transaction xuyên thread, lưu bền tác vụ qua lần restart, retry, ngữ cảnh request hay bảo đảm hoàn tất nghiệp vụ.

## 2. Ba lỗi có cùng biểu hiện “@Async không chạy như mong đợi” nhưng khác nguyên nhân

| Hiện tượng | Nguyên nhân gốc | Dấu hiệu cần thu thập | Cách sửa |
| --- | --- | --- | --- |
| Tự gọi `this.generateAsync()` | Lời gọi không đi qua Spring AOP proxy | Method chạy trên thread của caller, dù bean được proxy | Tách method async sang bean khác và inject nó |
| Pool hết thread/queue | Tốc độ nộp task vượt khả năng xử lý, hoặc worker bị chặn | Active thread đạt max, queue đầy, `TaskRejectedException`/độ trễ tăng | Executor có giới hạn, admission control, backpressure, timeout và metric |
| Mất `traceId`/MDC/SecurityContext | Context thường gắn vào `ThreadLocal` của caller, không tự truyền qua executor | Worker thấy `null`, thiếu log correlation hoặc authentication | Truyền dữ liệu cần thiết tường minh, hoặc wrapper/decorator phù hợp; dọn context trong `finally` |

Ba lỗi cần được phân tích riêng: **self-invocation có thể không hề dùng pool**, pool saturation xảy ra dù proxy hoạt động đúng, và mất context xảy ra ngay cả khi task được chạy ở worker thành công.

## 3. Nền tảng tối thiểu: @Async đi qua proxy và TaskExecutor như thế nào?

Với `@EnableAsync` và chế độ `AdviceMode.PROXY` (mặc định), Spring tạo proxy cho bean có phương thức `@Async`. Caller gọi phương thức công khai phù hợp **qua reference của Spring bean**; interceptor chọn executor và nộp task. Thân method chạy trong worker thread. Với kiểu trả về `CompletableFuture<T>`, caller nhận một future đại diện cho kết quả.

```text
ĐƯỜNG ĐÚNG
Controller/Facade/Test --> Spring bean proxy
                             |
                             v
                        Async advice
                             |
                             v
                      TaskExecutor.submit
                             |
                             v
                      Worker chạy method

ĐƯỜNG TỰ GỌI
Spring proxy --> target.outer()
                    |
                    +--> this.innerAsync()
                         không quay lại proxy
                         nên thực thi ngay trên caller thread
```

Trong proxy mode, lời gọi `this.innerAsync()` hoặc gọi ngầm `innerAsync()` bên trong cùng object **không được advice bắt**. Việc có `AopUtils.isAopProxy(bean) == true` chỉ xác nhận đối tượng được bọc; nó không chứng minh mọi đường gọi đều qua advice.

Điều kiện cần quan sát khi tái hiện:

- Spring phải quản lý bean; dùng `new MyService()` tạo object nằm ngoài cơ chế proxy.
- Test phải nạp cấu hình `@EnableAsync`.
- Method để test cần thích hợp với proxy: trong ví dụ dùng `public` và class không `final`; `private` hoặc self-call không thể được class proxy intercept theo cách kỳ vọng.
- `@Async("executorName")` phải khớp bean executor định dùng.
- Executor bản thân cũng có thể thực thi đồng bộ (`SyncTaskExecutor`); vì vậy phải đo **thread thực thi**, không chỉ có annotation/proxy.
- `@Async` hỗ trợ return type `void`, `Future` hoặc `CompletableFuture`; nên dùng `CompletableFuture` trong test để đợi kết quả/lỗi rõ ràng.

## 4. Phòng thí nghiệm tích hợp: ba tình huống trong một lớp JUnit

Ví dụ dùng **Java 17+**, Spring Boot 3.x / Spring Framework 6.x và JUnit Jupiter. Có thể áp dụng vào module đang dùng Gradle và `spring-boot-starter-test`.

Trong dự án Spring Boot đã cấu hình plugin, cần dependency cơ bản:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.withType(Test).configureEach {
    useJUnitPlatform()
}
```

Đây là **đoạn cấu hình dependency cho dự án Boot đã có sẵn**, không phải toàn bộ `build.gradle`; version Boot và dependency management theo module thực tế.

Tạo file **`src/test/java/demo/async/AsyncFailureModesTest.java`**:

```java
package demo.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(AsyncFailureModesTest.Config.class)
@Execution(ExecutionMode.SAME_THREAD)
class AsyncFailureModesTest {

    // Giả lập dữ liệu correlation gắn với thread xử lý request.
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    @Autowired
    private ProbeService service;

    @Test
    void selfInvocationBypassesAsyncProxy() throws Exception {
        assertTrue(AopUtils.isAopProxy(service),
            "Bean do Spring inject phải có proxy");

        Thread caller = Thread.currentThread();

        // selfCall() -> this.gated(): lời gọi bên trong target.
        Thread internal = service.selfCall()
            .get(5, TimeUnit.SECONDS);
        assertSame(caller, internal,
            "Self-invocation phải tái hiện chạy đồng bộ");

        // Gọi cùng method từ ngoài qua Spring proxy.
        Thread external = service.gated(
                new CountDownLatch(0),
                new CountDownLatch(0))
            .get(5, TimeUnit.SECONDS);
        assertNotSame(caller, external,
            "Lời gọi qua proxy phải chạy trên worker");
    }

    @Test
    void exhaustedPoolRejectsAnotherSubmission() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // tinyExecutor chỉ có 1 worker, không có hàng đợi.
        CompletableFuture<Thread> first = service.gated(entered, release);

        try {
            // Chỉ nộp task 2 khi task 1 đã chiếm worker.
            assertTrue(entered.await(5, TimeUnit.SECONDS),
                "Task đầu chưa vào worker");

            assertThrows(TaskRejectedException.class,
                () -> service.gated(
                    new CountDownLatch(0),
                    new CountDownLatch(0)));
        } finally {
            // Tránh worker bị giữ lại khi assertion thất bại.
            release.countDown();
        }

        // Task đầu được giải phóng và hoàn tất bình thường.
        Thread worker = first.get(5, TimeUnit.SECONDS);
        assertNotSame(Thread.currentThread(), worker);
    }

    @Test
    void threadLocalIsMissingUnlessExplicitlyPropagated()
            throws Exception {
        TRACE_ID.set("trace-325");
        try {
            // Executor mặc định của ví dụ không sao chép ThreadLocal.
            assertNull(service.rawTrace().get(5, TimeUnit.SECONDS));

            // Decorator capture context lúc submit và set trên worker.
            assertEquals("trace-325",
                service.decoratedTrace().get(5, TimeUnit.SECONDS));
        } finally {
            TRACE_ID.remove();
        }

        // Lời gọi sau không có trace: không được giữ giá trị cũ.
        assertNull(service.decoratedTrace().get(5, TimeUnit.SECONDS));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync(proxyTargetClass = true)
    static class Config {

        @Bean("tinyExecutor")
        ThreadPoolTaskExecutor tinyExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(0);
            executor.setThreadNamePrefix("tiny-async-");
            executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
            // Spring quản lý khởi tạo và shutdown bean executor.
            return executor;
        }

        @Bean("contextExecutor")
        ThreadPoolTaskExecutor contextExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(8);
            executor.setThreadNamePrefix("context-async-");

            executor.setTaskDecorator(task -> {
                // Bước CAPTURE: chạy khi caller submit task.
                String captured = TRACE_ID.get();

                return () -> {
                    // Lưu context trước đó của thread trong pool.
                    String previous = TRACE_ID.get();
                    try {
                        // Bước SET: đặt context riêng cho task này.
                        if (captured == null) {
                            TRACE_ID.remove();
                        } else {
                            TRACE_ID.set(captured);
                        }
                        task.run();
                    } finally {
                        // Bước RESTORE: ngăn rò context qua task khác.
                        if (previous == null) {
                            TRACE_ID.remove();
                        } else {
                            TRACE_ID.set(previous);
                        }
                    }
                };
            });
            return executor;
        }

        @Bean
        ProbeService probeService() {
            return new ProbeService();
        }
    }

    public static class ProbeService {

        @Async("tinyExecutor")
        public CompletableFuture<Thread> gated(
                CountDownLatch entered,
                CountDownLatch release) {

            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    return CompletableFuture.failedFuture(
                        new IllegalStateException("Worker gate timeout"));
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return CompletableFuture.failedFuture(ex);
            }

            return CompletableFuture.completedFuture(
                Thread.currentThread());
        }

        public CompletableFuture<Thread> selfCall() {
            // this.gated(...) bỏ qua proxy => chạy ngay tại caller.
            return gated(
                new CountDownLatch(0),
                new CountDownLatch(0));
        }

        @Async("tinyExecutor")
        public CompletableFuture<String> rawTrace() {
            return CompletableFuture.completedFuture(TRACE_ID.get());
        }

        @Async("contextExecutor")
        public CompletableFuture<String> decoratedTrace() {
            return CompletableFuture.completedFuture(TRACE_ID.get());
        }
    }
}
```

Chạy trong module đang có Gradle wrapper:

```bash
./gradlew test --tests "demo.async.AsyncFailureModesTest"
```

Trên Windows PowerShell:

```powershell
.\gradlew.bat test --tests "demo.async.AsyncFailureModesTest"
```

**Kỳ vọng:** ba test đều **PASS**; vì chúng chủ động *assert* các biểu hiện lỗi và kết quả đối chiếu. Tên thread/timing là dữ liệu thực thi, nên bài không giả định một stdout cụ thể. Nếu test đang chạy trong multi-project Gradle, cần chọn task `:module-path:test` thực sự sở hữu lớp test thay cho `test` ở root.

### Vì sao ví dụ chạy ổn định?

- `@SpringJUnitConfig` khởi tạo Spring TestContext thật với `@EnableAsync`, một proxy được inject và hai executor được tạo bằng `@Bean`.
- `@Execution(ExecutionMode.SAME_THREAD)` giữ các test trong lớp không chạy song song gây tranh chấp `tinyExecutor`. Nếu cả bộ test dùng context chung, vẫn cần cô lập tài nguyên khi test suite bật parallel ở phạm vi khác.
- `CountDownLatch` chứng minh task 1 **đang ở trong method** trước khi nộp task 2. Không dùng `Thread.sleep` để đoán thời điểm worker bận.
- `finally` luôn mở latch. Mỗi `get`/`await` có timeout, nên lỗi không dễ khiến CI treo vô hạn.
- Dùng `Thread` identity để chứng minh thread thực thi thay vì dựa vào một tên thread được hard-code.
- Bài test context dùng `ThreadLocal` thường, vì vậy worker mới hay worker tái sử dụng đều không tự có giá trị request nếu không có cơ chế truyền; lần gọi thứ hai sau khi xóa trace kiểm tra việc dọn context.

**Ghi chú về phạm vi kiểm chứng:** test này tái hiện lỗi ở mức tích hợp Spring/Executor thật trong cùng process. Nó không tự chứng minh hành vi của tải production, cấu hình executor khác, lưu bền công việc qua restart hay an toàn của toàn bộ business transaction.

## 5. Sự cố 1 — Tự gọi @Async: bean có proxy nhưng method chạy đồng bộ

Phương thức `selfCall()` được gọi qua proxy Spring, nhưng code của nó chạy trên target rồi gọi `gated(...)` bằng `this` (ngầm định). Vì call này không đi vào proxy lần thứ hai, async advice không được áp dụng. Với hai latch đã mở, method trả kết quả ngay trên caller thread.

Kết quả đã được test mô tả bằng hai điều kiện:

```java
assertSame(caller, service.selfCall().get(5, TimeUnit.SECONDS));
assertNotSame(caller, service.gated(
    new CountDownLatch(0), new CountDownLatch(0))
    .get(5, TimeUnit.SECONDS));
```

Đây là fragment trích từ test đầy đủ ở trên; nó dựa vào biến `service`/`caller` của test.

### Ví dụ trong ứng dụng

**Sai** — một service tự gọi method async của chính nó:

```java
@Service
public class ReportService {
    public void requestReport() {
        buildReportAsync(); // self-invocation: chạy đồng bộ
    }

    @Async("reportExecutor")
    public CompletableFuture<Void> buildReportAsync() {
        // Tác vụ lâu...
        return CompletableFuture.completedFuture(null);
    }
}
```

**Sửa bằng cách tách trách nhiệm:** để caller nhận bean khác thông qua constructor injection.

```java
@Service
public class ReportFacade {
    private final ReportWorker worker;

    public ReportFacade(ReportWorker worker) {
        this.worker = worker;
    }

    public CompletableFuture<Void> requestReport() {
        return worker.buildReportAsync(); // call đi qua proxy bean khác
    }
}

@Service
public class ReportWorker {
    @Async("reportExecutor")
    public CompletableFuture<Void> buildReportAsync() {
        // Tác vụ lâu...
        return CompletableFuture.completedFuture(null);
    }
}
```

Hai đoạn này chỉ **minh họa thiết kế**; trong ứng dụng thật cần imports, bean `reportExecutor` và `@EnableAsync`. Dùng tách bean giúp dependency rõ ràng hơn việc self-inject hoặc gọi `AopContext.currentProxy()`. Nếu chủ động dùng `AdviceMode.ASPECTJ` với weaving, cách intercept có thể khác; bài này theo chế độ proxy tiêu chuẩn.

### Khi nào dễ mắc lỗi?

- Một method đồng bộ trong cùng service gọi method `@Async` để gửi email/log/report.
- Method `@Scheduled`/event callback trong cùng target gọi method `@Async` bằng tên ngắn.
- Lập trình viên thấy proxy trong debugger rồi mặc định lời gọi nội bộ cũng được advice.
- Unit test dùng `new` hoặc mock hoàn toàn: test logic thuần vẫn xanh nhưng không kiểm chứng việc chạy qua proxy.

**Cách kiểm tra:** gọi đúng entrypoint mà production dùng, assert worker thread khác caller; nếu self-call được phát hiện, sửa đường gọi trước khi điều chỉnh executor.

## 6. Sự cố 2 — Cạn nhóm luồng: worker bận, queue đầy, task bị từ chối

Trong test `tinyExecutor`:

- `corePoolSize = 1`: có thể duy trì một core worker.
- `maxPoolSize = 1`: không thể tăng quá một worker.
- `queueCapacity = 0`: dùng dạng hàng đợi giao trực tiếp (`SynchronousQueue`), không có ô lưu task chờ.
- `AbortPolicy`: khi không nhận thêm task, từ chối; Spring `ThreadPoolTaskExecutor` biểu diễn việc từ chối bằng `TaskRejectedException`.

`service.gated(entered, release)` chiếm worker, mở `entered` và đợi `release`. Sau khi `entered.await(...)` báo method đã bắt đầu, lời gọi `gated(...)` thứ hai cũng **đi qua proxy** nhưng executor không còn khả năng nhận task. Đó là lý do assert rejection, không phải lỗi proxy.

```text
Caller                     tinyExecutor
  |
  +-- submit task #1 ----> [WORKER 1: đợi release latch]
  |
  +-- submit task #2 ----> [QUEUE = 0; MAX = 1]
                             |
                             +--> REJECT
                                  TaskRejectedException
```

### Quan hệ giữa core, max và queue trong ThreadPoolExecutor

Với `ThreadPoolTaskExecutor` dựa trên `ThreadPoolExecutor` thông dụng, việc nhận task diễn ra theo logic:

1. Nếu số worker chưa đạt `corePoolSize`, tạo worker để xử lý.
2. Nếu đã đạt core, thử thêm task vào queue.
3. Nếu queue đã đầy và chưa đạt `maxPoolSize`, tạo worker bổ sung.
4. Nếu worker đã đạt max **và** queue đầy, áp dụng rejection policy.

Ví dụ `core=2, max=8, queue=100`: sau khi hai core worker bận, các task thường vào queue **trước** khi pool tăng đến 3…8. Vì vậy tăng `maxPoolSize` mà để queue không giới hạn có thể không tạo hiệu ứng mong muốn; queue vô hạn có thể làm độ trễ tăng và gây cạn bộ nhớ.

### Chính sách từ chối tác vụ là quyết định nghiệp vụ/vận hành

| Chính sách JDK | Khi executor đầy | Điều phải cân nhắc |
| --- | --- | --- |
| `AbortPolicy` (mặc định thông dụng) | Ném rejection, Spring chuyển thành `TaskRejectedException` | Caller cần xử lý, trả 429/503 hoặc ghi nhận retry tùy hợp đồng |
| `CallerRunsPolicy` | Chạy task trên thread nộp (nếu executor chưa shutdown) | Backpressure nhưng có thể **biến @Async thành chạy ngay trên caller**, tăng latency |
| `DiscardPolicy` | Bỏ task không báo lỗi | Nguy hiểm nếu tác vụ có ý nghĩa nghiệp vụ; có thể mất công việc âm thầm |
| `DiscardOldestPolicy` | Loại task cũ ở queue rồi thử nộp lại | Có thể mất thứ tự/độ đầy đủ; không phù hợp công việc bắt buộc |

**Không phải mọi trường hợp pool đầy đều ném `TaskRejectedException`.** Kết quả phụ thuộc rejection policy. Test ở đây cố ý cấu hình `AbortPolicy` để cho tín hiệu quan sát được và dễ kiểm chứng.

### Hai dạng “cạn” cần phân biệt

**Saturation do tải thực:** số tác vụ đến nhiều hơn năng lực xử lý. Tăng concurrency có thể hỗ trợ nếu các tác vụ chờ I/O và upstream chịu được, nhưng tăng bừa sẽ dồn tải xuống DB/API hoặc gây mất ổn định.

**Thread starvation do phụ thuộc vòng:** một task đang chiếm worker lại chờ kết quả task khác được nộp vào **chính executor bị chiếm hết**. Chẳng hạn với pool chỉ có một worker, method A gọi async B rồi `join()` chờ B, trong khi B chỉ có thể chạy sau A; nếu queue nhận B, chương trình có thể kẹt đến timeout. Tăng pool có thể che một ca test nhưng không giải quyết thiết kế chặn lẫn nhau ở tải lớn.

### Thiết kế giảm rủi ro

- Dùng executor riêng theo loại công việc (ví dụ CPU vs I/O; job ngắn vs job dài) khi có nhu cầu cô lập tải.
- Đặt **giới hạn hữu hạn** cho pool và queue, thời gian chờ, giới hạn số request đồng thời; quản lý retry có backoff/jitter.
- Xác định response khi reject: caller nhận lỗi có thể retry, ghi task vào hàng đợi bền vững, hoặc chấp nhận bỏ với lý do rõ ràng. Không tự nói “đã nhận” khi task thực tế bị từ chối.
- Chú ý lời gọi `@Async` với `void`: exception ở worker không quay về caller như future; cấu hình `AsyncUncaughtExceptionHandler` hoặc đổi API trả `CompletableFuture` khi cần theo dõi kết quả.
- Tránh `join()/get()` ngay trên đường request nếu mục tiêu là giảm latency; nếu nghiệp vụ phải chờ, cần cân nhắc còn lợi ích bất đồng bộ nào.
- Giám sát `activeCount`, `poolSize`, queue depth, rejection count, thời gian từ submit tới bắt đầu, thời lượng task, số task lỗi và độ trễ business deadline.

Các metric có thể lấy qua `ThreadPoolTaskExecutor.getActiveCount()`, `getPoolSize()`, `getQueueSize()` và instrument thêm rejection. Với hệ thống lớn, thêm alert theo xu hướng backlog/SLA; chỉ nhìn số thread chưa đủ.

## 7. Sự cố 3 — Mất ngữ cảnh khi nhảy sang worker

Nhiều framework lưu context liên quan request vào `ThreadLocal` hoặc một holder dựa trên thread:

- MDC / correlation ID của logger.
- `SecurityContextHolder` của Spring Security (chiến lược thường gặp là thread-local).
- `LocaleContextHolder`, `RequestContextHolder`.
- Transaction và persistence context thông qua cơ chế gắn với thread.
- Tenant ID hoặc user/request context do ứng dụng tự xây.

Khi `@Async` submit task sang worker, **worker là một thread khác**. Một `ThreadLocal` thông thường ở caller không tự xuất hiện trên worker.

Trong test, caller:

```java
TRACE_ID.set("trace-325");
```

Còn `rawTrace()` chạy ở `tinyExecutor` và đọc:

```java
return CompletableFuture.completedFuture(TRACE_ID.get());
```

Kết quả là `null`, mặc dù task được chuyển sang worker đúng cách. Đây là **bằng chứng mất context**, không phải bằng chứng `@Async` thất bại.

### 7.1. Cách dùng TaskDecorator cho metadata an toàn

`TaskDecorator` được gắn vào `ThreadPoolTaskExecutor` để bọc từng `Runnable`. Trong ví dụ:

1. **Capture** một giá trị bất biến (`String traceId`) từ caller tại thời điểm submit.
2. **Set** giá trị đó trên worker ngay trước khi chạy task.
3. **Restore/Remove** context cũ của worker trong `finally`.

Bước 3 đặc biệt quan trọng vì pool **tái sử dụng thread**. Nếu task A đặt `traceId=A` nhưng không dọn, task B có thể log nhầm `traceId=A` hoặc vô tình dùng dữ liệu người dùng A.

Với logging MDC thực tế, cần capture đúng snapshot map (`MDC.getCopyOfContextMap()`), đặt snapshot khi task chạy và **khôi phục map trước đó** trong `finally`. Khi dùng hệ thống observability có sẵn, nên tận dụng cơ chế context propagation phù hợp thay vì tự viết lại toàn bộ mapping.

Trong test, `decoratedTrace()` trả `trace-325` và lần gọi sau khi caller `TRACE_ID.remove()` trả `null`. Hai assertion kết hợp kiểm tra **đã truyền được** và **không rò từ lần trước**.

### 7.2. Context không nên copy nguyên trạng

| Loại dữ liệu | Hướng xử lý |
| --- | --- |
| Trace/correlation ID, tenant ID dạng immutable | Truyền rõ qua tham số hoặc decorator; luôn restore |
| Spring Security context | Dùng cơ chế `DelegatingSecurityContextAsyncTaskExecutor`/wrapper phù hợp, đánh giá lifetime và quyền |
| MDC, tracing span, observation | Dùng công cụ context propagation tương thích thư viện đang chạy |
| HTTP request, servlet response | Tránh giữ đối tượng request sống ngoài vòng đời của nó; trích dữ liệu cần dùng thành DTO |
| Transaction, EntityManager, JDBC connection | **Không copy transaction của request sang worker bằng ThreadLocal**; worker tạo transaction/đơn vị công việc riêng nếu cần |
| Thread-bound locale/timezone | Truyền thông tin cần thiết rõ ràng hoặc dùng context wrapper thích hợp |

**Không dùng `InheritableThreadLocal` như một “bản sửa” chung.** Nó chỉ liên quan đến lúc tạo thread con, trong khi thread của pool được tạo/tái sử dụng ở thời điểm khác. Giá trị có thể thiếu hoặc rò từ lần trước; chuyển context cần có quy tắc per-task rõ ràng.

### 7.3. Chú ý transaction khi dùng @Async

Giả sử caller đang ở transaction chưa commit rồi submit async method để đọc chính dữ liệu vừa ghi. Worker có thể dùng connection/transaction khác nên không thấy dữ liệu chưa commit. Điều đó không được giải quyết bằng cách copy một `TransactionSynchronizationManager` hay `EntityManager` vào `TaskDecorator`.

Cần chọn mô hình phù hợp:

- Commit dữ liệu cần thiết trước khi khởi chạy tác vụ, hoặc phát sự kiện khi transaction đã commit.
- Truyền khóa/DTO bất biến cho worker; worker đọc lại dữ liệu và mở transaction riêng khi cần.
- Nếu công việc phải bền vững sau crash, dùng outbox/queue/job scheduler phù hợp. `@Async` chạy trong process không thay thế lưu bền.

## 8. Phân biệt lỗi submit task với lỗi thực thi bên trong task

Có hai điểm thất bại với đường gọi qua proxy:

**Tại lúc submit:** executor đầy, bị shutdown hoặc từ chối theo policy. Với `AbortPolicy` trong ví dụ, caller có thể nhận `TaskRejectedException` ngay khi gọi phương thức, trước khi có future. Hãy test và xử lý đường lỗi này như một hành vi riêng.

**Sau khi worker đã nhận task:** method ném exception hoặc trả failed future. Nếu return type là `CompletableFuture<T>`, caller có thể quan sát lỗi bằng `get()`/`join()`; không phải mọi exception đều ném ngay tại dòng gọi.

```java
// Fragment minh họa cho một CompletableFuture đã được nhận:
ExecutionException error = assertThrows(
    ExecutionException.class,
    () -> future.get(5, TimeUnit.SECONDS));

assertInstanceOf(IllegalStateException.class, error.getCause());
```

Đây là đoạn minh họa cần `future` và imports JUnit/JDK trong test; không phải file Java độc lập. `get()` bọc lỗi worker vào `ExecutionException`, còn `join()` thường dùng `CompletionException`. Timeout nên được chọn theo SLA và điều kiện test, không coi timeout là cách retry nghiệp vụ.

Nếu caller dùng `void @Async` thì không có future để lấy exception. Có thể cấu hình `AsyncUncaughtExceptionHandler` để ghi nhận lỗi, nhưng nếu nghiệp vụ cần xác nhận thành công/thất bại hoặc retry có kiểm soát, thường nên dùng `CompletableFuture` hoặc cơ chế tác vụ có trạng thái bền vững.

## 9. Tình huống tổng hợp: self-invocation che giấu saturation và context loss

Giả sử một endpoint báo cáo:

```text
HTTP request
  -> ReportService.start()
       -> this.runAsync()           (lỗi 1: bypass proxy)
           -> đọc SecurityContext   (tình cờ còn, vì chạy ngay trên request)
           -> gọi DB chậm          (block HTTP request)
```

Khi developer sửa bằng cách tách `ReportWorker` sang bean khác, `runAsync()` bắt đầu chạy trên pool; lúc này có thể lộ ra hai vấn đề trước đó chưa nhìn thấy: `SecurityContext` bị thiếu (lỗi 3), và tải đồng thời làm pool/queue đầy (lỗi 2).

Cách xử lý theo thứ tự có bằng chứng:

1. Đảm bảo caller dùng Spring-injected worker bean; test method thật chạy trên thread khác.
2. Rà dữ liệu cần cho worker: trích các ID/DTO hợp lệ, triển khai context propagation nếu phải dùng holder.
3. Định giới hạn pool/queue/timeout theo tải và ngân sách tài nguyên; chọn rejection policy, xử lý khi đầy.
4. Test endpoint/entrypoint thực với dữ liệu đủ và tải gần thực tế; quan sát latency, rejection, queue backlog và correctness của kết quả.
5. Nếu tác vụ không được phép mất khi process chết, đưa request công việc vào cơ chế bền vững, không dựa vào future in-memory.

Không có một annotation hay một con số `maxPoolSize` nào giải quyết đủ cả ba lỗi.

## 10. Những bài kiểm thử bổ sung có giá trị

| Test | Điều kiện tạo lỗi | Assertion chính |
| --- | --- | --- |
| External call qua Spring proxy | Gọi bean inject từ context | Target chạy khác thread caller |
| Self-invocation | Wrapper cùng class gọi `this.async()` | Target chạy cùng caller |
| Pool vừa đầy | Worker được giữ bằng latch, queue = 0 | Nộp tiếp nhận `TaskRejectedException` với AbortPolicy |
| Pool có queue hữu hạn | Core worker bận, queue còn chỗ rồi đầy | Task chờ đúng, sau đó bị từ chối/được xử lý theo policy |
| CallerRunsPolicy | Worker + queue bận | Lời gọi có thể chạy trên caller; latency tăng |
| ThreadLocal không truyền | Caller set value rồi submit | Worker thấy `null` |
| TaskDecorator truyền/restore | Hai request có trace khác nhau, sau đó không trace | Mỗi worker thấy đúng trace, lần không trace thấy `null` |
| Exception trong worker | Worker ném lỗi có kiểm soát | Future exceptional, caller đọc nguyên nhân |
| SecurityContext | Caller có principal; worker đọc context | Chỉ có khi truyền bằng cơ chế phù hợp; không rò giữa request |
| Transaction visibility | Caller ghi chưa commit, worker đọc | Hành vi nhất quán với transaction boundary đã thiết kế |

Với tests chạy CI, đừng phụ thuộc vào thứ tự test. Cô lập pool/test context, giải phóng latch và xóa `ThreadLocal` trong `finally`. Để mô phỏng saturation, hãy **chủ động khóa worker với tín hiệu**, không dùng vòng lặp gửi hàng nghìn task hy vọng cuối cùng pool sẽ đầy.

## 11. Cách chẩn đoán khi chạy thực tế

### 11.1. Method chạy đồng bộ bất ngờ

Trước tiên kiểm tra đối tượng caller sử dụng có phải Spring proxy không, rồi rà **đúng call stack**: lời gọi đi từ bean khác hay `this`? Phân biệt test gọi thẳng method và production đi qua wrapper. Đừng coi tên thread trong log là bằng chứng duy nhất; có thể instrument thread ID ở đầu method.

### 11.2. Có TaskRejectedException hoặc request chậm

Đọc cấu hình `corePoolSize`/`maxPoolSize`/`queueCapacity` và rejection handler. Ghi metric lúc lỗi: active count, queue size, số task đến/hoàn tất, thời gian xử lý và dependency nào giữ worker. Nếu đang dùng `CallerRunsPolicy`, người gọi có thể đang làm chính việc nặng nên latency tăng nhưng không có rejection exception.

### 11.3. Worker mất trace, user hoặc transaction

Tìm nơi dữ liệu được lưu: tham số immutable, MDC, SecurityContext, RequestContextHolder hay transaction context. Xác định cái nào cần truyền và có thể truyền an toàn. Thêm test hai request liên tiếp để chứng minh vừa truyền đúng vừa dọn sạch. Transaction cần thiết kế boundary riêng; copy toàn bộ thread-local state là lựa chọn nguy hiểm.

### 11.4. Test pass nhưng production lỗi

So sánh executor thật với executor test, entrypoint thật với cách test gọi bean, loại proxy đang dùng (JDK vs class-based), có dùng virtual threads hoặc custom executor/rejection policy không. Một test giả lập queue=0 chỉ minh họa cơ chế; để định cỡ production cần đo với tải thực và SLO.

## 12. Những sai lầm phổ biến

1. **Gắn `@Async` rồi gọi nội bộ:** annotation tồn tại nhưng proxy không intercept; phải thay đổi đường gọi.
2. **Chỉ assert `AopUtils.isAopProxy`:** bean có proxy chưa chứng minh method cụ thể đã đi qua proxy.
3. **Thấy CompletableFuture trả về và tưởng async:** target chạy đồng bộ vẫn có thể trả `completedFuture`.
4. **Dùng `Thread.sleep` để ép thứ tự:** dễ flaky; dùng latch/barrier với timeout.
5. **Tăng max pool trong khi queue vô hạn:** tác vụ có thể chỉ xếp hàng, pool không tăng; backlog tiếp tục tích tụ.
6. **Dùng `CallerRunsPolicy` mà không biết tác động:** caller có thể phải thực hiện task, thay đổi đáng kể latency.
7. **Nuốt `TaskRejectedException`:** báo nhận công việc dù task chưa được thực thi.
8. **Trông đợi ThreadLocal tự truyền sang pool:** thread khác không dùng cùng dữ liệu gắn thread.
9. **Đặt context lên worker nhưng không remove/restore:** thông tin request trước có thể rò sang request sau.
10. **Copy transaction/HTTP request object sang worker:** lifetime và thread-boundary sai; truyền dữ liệu cần thiết và mở đơn vị công việc riêng.
11. **Thử nghiệm bằng mock thay proxy thật:** không kiểm chứng interceptor, executor và context propagation.
12. **Coi @Async là hàng đợi bền vững:** crash ứng dụng có thể làm mất task in-memory; chọn queue/outbox/job store nếu yêu cầu bền vững.

## 13. Bài tập tự đánh giá

**Bài 1.** Bạn thấy `AopUtils.isAopProxy(reportService)` trả `true`, nhưng `reportService.start()` gọi `this.sendEmailAsync()` và request HTTP vẫn phải chờ gửi mail. Giải thích và đề xuất sửa.

**Gợi ý:** `start()` có đi qua proxy, nhưng lời gọi `sendEmailAsync()` ở trong cùng target không đi qua proxy. Tách gửi mail vào Spring bean khác; test ngoài bean quan sát thread khác và kết quả thực.

**Bài 2.** Với `core=2`, `max=8`, queue capacity = 1.000, active worker = 2 và queue đang có 400 task, tăng `max` từ 8 lên 32 có chắc ngay lập tức tạo thêm worker không?

**Trả lời:** Không. Theo luồng nhận task thông dụng của `ThreadPoolExecutor`, task thường được xếp vào queue cho đến khi queue đầy trước khi tăng worker quá core; cần nhìn cả queue, arrival rate, throughput và giới hạn downstream.

**Bài 3.** Tại sao `CallerRunsPolicy` có thể khiến một lời gọi đã qua proxy `@Async` vẫn chạy trên request thread?

**Trả lời:** Khi worker/queue không còn chỗ, chính sách yêu cầu thread đang submit trực tiếp chạy task. Advice đã được gọi nhưng executor quyết định chạy task inline để tạo backpressure.

**Bài 4.** Một task trong pool có trace ID của user trước. Cần sửa phần nào?

**Trả lời:** Đảm bảo decorator/wrapper capture context tại lúc submit, đặt đúng snapshot trên worker rồi restore/remove trong `finally`. Thêm test nhiều request kế tiếp, bao gồm một request không có trace.

**Bài 5.** Có nên truyền `TransactionSynchronizationManager` từ request sang worker bằng cách sao chép ThreadLocal không?

**Trả lời:** Không. Transaction gắn với tài nguyên/connection và lifetime riêng; worker nên đọc dữ liệu đã commit, dùng DTO/ID và thiết lập transaction riêng khi cần.

## 14. Ghi nhớ

**@Async đi qua proxy, executor quyết định nơi và lúc chạy task, còn context gắn thread không tự di chuyển theo task.** Vì vậy cần ba nhóm bằng chứng khác nhau:

- **Đường gọi:** caller sử dụng Spring bean proxy; tự gọi `this.asyncMethod()` là trường hợp âm phải tái hiện.
- **Năng lực executor:** test đầy worker và queue để quan sát rejection/backpressure một cách quyết định.
- **Ngữ cảnh:** test cả việc context xuất hiện đúng trong worker và việc không rò sang task sau.

Khi cả ba được kiểm thử độc lập, ta mới có cơ sở thiết kế luồng bất đồng bộ dễ dự đoán, đo lường và xử lý khi có sự cố.

### Tham khảo chính thức

- [Spring Framework — Task Execution and Scheduling, @Async và ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — Understanding AOP Proxies / self-invocation](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — TaskDecorator Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/TaskDecorator.html)
- [Spring Security — Concurrency Support](https://docs.spring.io/spring-security/reference/features/integrations/concurrency.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="design-practice-008">Đo tải và điều chỉnh đồng thời với giới hạn tài nguyên phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# Đo tải và điều chỉnh đồng thời với giới hạn tài nguyên phụ thuộc

## 1. Bài toán: tăng thread chưa chắc tăng tốc hệ thống

Một ứng dụng Spring nhận nhiều yêu cầu đồng thời, đưa công việc vào ThreadPoolTaskExecutor rồi gọi database, HTTP service, Redis hoặc một dịch vụ bên ngoài. Khi tải tăng, thời gian phản hồi tăng mạnh và hàng đợi của executor dài ra. Phản xạ phổ biến là tăng số worker từ 8 lên 32 hoặc 64. Tuy nhiên, nếu phía dưới chỉ cho phép tối đa 8 kết nối đang hoạt động, các worker bổ sung có thể chỉ đứng chờ lấy kết nối; throughput không tăng, trong khi độ trễ, bộ nhớ và xác suất timeout tăng.

Bài này hướng dẫn **đo tải, tìm giới hạn của tài nguyên phụ thuộc và điều chỉnh mức đồng thời** trong một hệ thống dùng Spring Framework. Mục tiêu là xử lý được nhiều yêu cầu *thành công* trong thời hạn cho phép, đồng thời không làm quá tải database, dịch vụ HTTP hoặc hạ tầng bên dưới.

Sau bài học, bạn có thể:

- Phân biệt số tác vụ đồng thời, tốc độ hoàn thành, thời gian chờ hàng đợi, thời gian xử lý và mức bận của dependency.
- Ước lượng nhu cầu đồng thời từ tải thực tế và thời gian giữ tài nguyên; xác định khi nào một dependency đã chạm trần.
- Thiết kế thí nghiệm tăng tải có điều kiện kiểm soát, đọc p50/p95/p99, throughput, timeout và rejection cùng lúc.
- Cấu hình ThreadPoolTaskExecutor, queue có giới hạn và bộ giới hạn đồng thời ở đúng ranh giới.
- Đánh giá lợi ích và tác dụng phụ của mỗi thay đổi; tránh tối ưu một chỉ số bằng cách chuyển nghẽn sang nơi khác.

## 2. Mô hình đường đi của một yêu cầu

Một đường xử lý điển hình:

~~~text
HTTP request / event / scheduled job
          |
          v
  Admission / rate limit
          |
          v
  Spring TaskExecutor
  [queue] -> [worker threads]
                 |
                 v
        Acquire connection / permit
                 |
                 v
       Database / HTTP / Redis
                 |
                 v
        Release resource, complete
~~~

Có ít nhất **hai nơi có thể hình thành hàng chờ**: hàng đợi của executor và hàng chờ lấy connection/permit bên dependency. Đặt nhiều thread hơn không xóa bỏ hàng chờ; đôi khi chỉ chuyển hàng chờ từ executor xuống connection pool.

Các đại lượng cần phân biệt:

| Đại lượng | Ý nghĩa | Đơn vị điển hình |
| --- | --- | --- |
| Arrival rate | Tốc độ yêu cầu/tác vụ đến hệ thống | requests/s hoặc tasks/s |
| Throughput / goodput | Tốc độ công việc hoàn thành; *goodput* chỉ tính kết quả hữu ích, thành công | successes/s |
| In-flight concurrency | Số yêu cầu/tác vụ đang tồn tại hoặc đang xử lý đồng thời, phải nêu rõ ranh giới đo | count |
| Executor queue wait | Thời gian từ submit tới khi worker bắt đầu xử lý callback | ms |
| Executor run duration | Thời gian worker giữ callback, kể cả lúc block đợi kết nối/I/O | ms |
| Dependency acquisition wait | Thời gian chờ lấy DB/HTTP connection hoặc permit | ms |
| Dependency hold / call duration | Thời gian thực sự sử dụng tài nguyên sau khi được cấp | ms |
| End-to-end latency | Từ lúc nhận yêu cầu đến khi kết quả cuối được xác nhận | ms |
| Rejected / timed-out / cancelled | Việc không được chấp nhận, không kịp hoàn tất hoặc bị hủy | count, rate |

**Quan trọng:** thời gian chạy của worker *không đồng nghĩa* với CPU time hay thời gian thực hiện truy vấn. Một worker có thể “đang active” trong 500 ms nhưng phần lớn thời gian đó chỉ chờ connection trong HikariCP. Nếu chỉ đo executor run duration, ta có thể chẩn đoán nhầm rằng mã nghiệp vụ quá chậm.

Để tìm nguyên nhân phải quan sát đầy đủ:

~~~text
End-to-end
  = admission wait
  + executor wait
  + worker overhead
  + dependency acquisition wait
  + dependency call / CPU / I/O
  + các bước còn lại
~~~

Đây là sơ đồ phân rã khái niệm, không phải một phép cộng luôn tách biệt: trong thực tế các stage có thể song song, retry, fork/join hoặc truyền qua nhiều dịch vụ. Chỉ cộng các thời gian nếu chúng thuộc cùng một đường xử lý tuần tự, cùng ranh giới đo và không bị đếm trùng.

## 3. Mức đồng thời, throughput và định luật Little

Trong một hệ thống ổn định, định luật Little liên hệ:

**L = λ × W**

Ở đây:

- **L**: số yêu cầu trung bình đang tồn tại trong *cùng phạm vi hệ thống* được đo.
- **λ**: tốc độ hoàn thành trung bình (công việc/giây) trong trạng thái ổn định.
- **W**: thời gian lưu lại trung bình của mỗi yêu cầu trong đúng phạm vi đó, tính bằng giây.

Ví dụ một HTTP dependency thực sự giữ connection trung bình 0,2 giây và đang xử lý 30 lời gọi mỗi giây. Mức sử dụng kết nối đồng thời trung bình vào khoảng 30 × 0,2 = **6 connection**. Nếu service giới hạn 8 cuộc gọi đồng thời, còn một ít dư địa cho biến thiên, nhưng không thể kết luận p99 sẽ ổn chỉ từ số trung bình.

Nếu dependency có **tối đa 8 slot** và mỗi lời gọi chiếm slot trung bình **0,2 giây**, trần throughput lý tưởng theo riêng tài nguyên này xấp xỉ:

**capacity ≈ 8 / 0,2 = 40 calls/s**.

Đây là ước lượng *trong điều kiện lý tưởng* với tất cả slot hoạt động hữu ích và thời gian giữ tài nguyên ổn định. Tốc độ thực tế có thể thấp hơn do lock, transaction dài, connection churn, latency biến động và các giới hạn khác. Khi nhận 50 calls/s kéo dài mà chỉ hoàn thành xấp xỉ 40 calls/s, tồn đọng sẽ tăng trừ khi ứng dụng từ chối, giới hạn đầu vào hoặc có cơ chế giảm tải.

Đừng thay **W trung bình** bằng p95/p99 trong công thức Little. Ví dụ p95 = 200 ms không cho phép suy ra W trung bình = 200 ms. Cũng cần phân biệt L của toàn request (gồm chờ) với số connection đang được giữ (chỉ tính thời gian hold) để không trộn hai phạm vi.

**Không có “thread count tối ưu” tách rời workload và dependency.** Với tác vụ CPU-bound, tăng thread vượt khả năng CPU dễ gây context switching. Với tác vụ I/O-bound, nhiều thread có thể tăng mức đồng thời cho đến khi connection pool, downstream quota, network hoặc memory trở thành giới hạn tiếp theo.

## 4. Xác định tài nguyên phụ thuộc đang giới hạn cái gì

### 4.1. Database connection pool

Một Spring Boot ứng dụng dùng JDBC thường sử dụng HikariCP. Các thông số cần xem là **maximumPoolSize**, thời gian chờ lấy connection (**connectionTimeout**), số kết nối active/idle/pending và thời gian giữ kết nối.

Giả sử HikariCP chỉ cho tối đa 10 connection:

- 40 worker gọi DB cùng lúc không có nghĩa là 40 truy vấn chạy song song trên 40 connection.
- Nếu 10 connection đã bị giữ, những worker còn lại có thể phải chờ cấp connection và sau đó timeout.
- Tăng corePoolSize/maxPoolSize có thể làm nhiều worker cùng chờ hơn, trong khi số connection thực tế không tăng.
- Tăng maximumPoolSize cũng chưa chắc cải thiện throughput: DB server còn có giới hạn CPU, I/O, lock, số client, tài nguyên query và kích thước transaction.

Ví dụ cấu hình **minh họa**, không phải giá trị khuyên dùng cho production:

~~~yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      connection-timeout: 1000
~~~

Trong HikariCP, connection-timeout dùng mili giây cho thời gian tối đa chờ lấy connection từ pool. Cần đồng thời đo lỗi timeout, số pending, thời gian query và tài nguyên của chính DB. Một request có thể dùng kết nối lâu vì transaction giữ lock, không phải vì truy vấn SQL “chậm” theo nghĩa CPU.

### 4.2. HTTP dependency

HTTP client thường có các giới hạn riêng: kết nối tổng, kết nối mỗi host, thời gian chờ lấy kết nối, connect timeout, response/read timeout, và quota do service bên kia áp đặt.

Ví dụ dịch vụ ngoài chỉ chịu được 20 lời gọi đồng thời mỗi instance, nhưng ứng dụng có 4 instance và mỗi instance tự cho 12 cuộc gọi: mức đồng thời tiềm năng lên đến 4 × 12 = **48**, vượt khả năng tổng nếu 20 là giới hạn toàn hệ thống. Semaphore cục bộ không tự biến thành bộ giới hạn phân tán.

Hãy phân biệt:

- **Concurrency limit:** tối đa bao nhiêu lời gọi đang diễn ra cùng lúc.
- **Rate limit:** tối đa bao nhiêu lời gọi trong mỗi khoảng thời gian, ví dụ 100 requests/s.
- **Connection limit:** tối đa bao nhiêu kết nối mạng; một kết nối HTTP/2 có thể mang nhiều stream, không luôn ánh xạ 1:1 với request.
- **Quota dịch vụ:** có thể giới hạn theo tenant, account, key hoặc vùng.

Một semaphore chặn đồng thời **không** thay thế token bucket/rate limiter khi dependency công bố quota theo số request mỗi giây.

### 4.3. Các phụ thuộc khác

- **Redis:** xem số connection, độ trễ command, event loop/client pooling, CPU server và network. Tăng worker có thể chỉ làm Redis chậm thêm.
- **Messaging:** consumer concurrency phải đi cùng số partition, prefetch, tốc độ ACK và giới hạn của handler/DB.
- **Filesystem/object storage:** chú ý băng thông, số socket, throttling và thời gian I/O; số thread không vượt được giới hạn ổ đĩa hoặc mạng.
- **CPU/memory:** worker quá nhiều có thể làm CPU bão hòa, GC và allocation tăng; với virtual threads cũng không có thêm DB connection hay quota HTTP chỉ nhờ đổi loại thread.

Vì vậy **giới hạn hữu ích là giới hạn của toàn đường xử lý**, không đơn giản là số core CPU hay số thread trong executor.

## 5. Quan sát đủ trước khi chỉnh cấu hình

Đo các chỉ số dưới **cùng khoảng thời gian, cùng loại tác vụ và cùng mức tải**:

| Nhóm | Chỉ số cần theo dõi | Tín hiệu khi gặp nghẽn |
| --- | --- | --- |
| Đầu vào | Arrival rate, request mix, payload size, burst | Việc đến nhanh hơn việc hoàn thành |
| Chất lượng kết quả | Success rate, timeout, 4xx/5xx, retry, cancellation | Throughput nhìn cao nhưng goodput giảm |
| Độ trễ | End-to-end p50/p95/p99, SLO, timeout count | Tail latency tăng mạnh khi gần bão hòa |
| Executor | Active threads, pool size, queue depth/capacity, queue wait p95/p99, rejection | Backlog, thread bận hoặc queue chạm trần |
| Công việc | Run duration, CPU time nếu cần, lỗi Future/@Async | Worker bị giữ lâu bởi nghiệp vụ hoặc I/O |
| Database | Connections active/idle/pending, acquisition wait, query duration, lock wait | Hết connection, lock hoặc DB quá tải |
| HTTP downstream | In-flight, connection-pool pending, call p95/p99, 429/503, timeout | Quota/chậm từ service phụ thuộc |
| Runtime | CPU, heap, allocation, GC pauses, thread count, network | Giới hạn tài nguyên chính ứng dụng |

Không chỉ nhìn **trung bình**. Hai hệ thống cùng latency mean = 100 ms có thể có p99 rất khác nhau. SLO thường bị chi phối bởi tail latency và lỗi. Khi một task bị reject trước khi chạy, nó có thể *không xuất hiện* trong timer run duration của các task đã chạy; cần đo rejection riêng để không “làm đẹp” histogram bằng cách loại mất các task quá tải.

Các chỉ số pool chỉ có nghĩa nếu bạn gắn đúng tên executor. Một ứng dụng có thể có nhiều ThreadPoolTaskExecutor, HTTP client pool và DataSource; số đo tổng hợp có thể che mất một pool cụ thể đang nghẽn.

### 5.1. Quan sát queue wait và run duration đúng ranh giới

Lấy ba thời điểm bằng System.nanoTime() trong **cùng JVM**:

~~~text
t0 = thời điểm chuẩn bị submit vào executor
t1 = worker bắt đầu callback
t2 = callback kết thúc

submission-to-start wait = t1 - t0
callback run duration   = t2 - t1
submit-to-finish        = t2 - t0
~~~

Submission-to-start là phép đo thực dụng cho độ trễ chờ trước khi chạy; nó bao gồm cả chi phí lập lịch/chuyển giao, nên không nhất thiết bằng thời gian thật nằm trong một BlockingQueue. Run duration bao gồm thời gian chờ DB/HTTP/permit nếu callback block. Đo acquisition wait riêng để tách điểm nghẽn bên dưới.

Một TaskDecorator có thể gắn timer cho các callback executor. Đây là **trích đoạn cấu hình** dùng Micrometer, giả định đã có hai Timer tên queueWaitTimer và runTimer:

~~~java
executor.setTaskDecorator(task -> {
    long submittedAt = System.nanoTime();
    return () -> {
        long startedAt = System.nanoTime();
        queueWaitTimer.record(
                startedAt - submittedAt, TimeUnit.NANOSECONDS);
        try {
            task.run();
        } finally {
            runTimer.record(
                    System.nanoTime() - startedAt,
                    TimeUnit.NANOSECONDS);
        }
    };
});
~~~

Đây là mã minh họa gắn vào cấu hình executor, không phải tệp Java độc lập. Timer cần được tạo/đăng ký với MeterRegistry trước đó. Đặt tag ổn định như executor="dependent-io"; tránh taskId hay userId trong tag vì cardinality sẽ phình to. Lưu ý TaskDecorator có thể bọc callback/FutureTask chứ không phải chính body nghiệp vụ; lỗi của FutureTask có thể được giữ trong Future. Cần theo dõi outcome ở lớp phù hợp và đếm task bị từ chối riêng.

### 5.2. Phân biệt “nghẽn do executor” với “nghẽn do dependency”

Nếu **queue wait cao nhưng dependency còn rảnh và CPU rảnh**, cần kiểm tra số worker, executor được chọn, scheduling, queue policy và cấu hình. Nếu **active threads cao, run duration cao, Hikari pending cao**, tăng worker thường làm tình hình tệ hơn: nhiều thread hơn cùng chờ connection.

Nếu **DB active cao, query p95 tăng và lock wait tăng**, cần điều tra query/transaction và khả năng DB xử lý; không coi chỉnh pool là lời giải gốc. Nếu **HTTP trả 429 hoặc 503**, phải xem rate/concurrency quota, retry và admission control.

Để chẩn đoán, cùng một biểu đồ thời gian nên có input rate, completed rate, queue depth, wait p95, dependency acquisition p95 và error rate. Một ảnh chụp queue size = 0 sau khi burst kết thúc không chứng minh không có backlog trước đó.

## 6. Thiết kế thử nghiệm đo tải có thể lặp lại

Đừng đổi cấu hình bằng cảm giác. Hãy định nghĩa **mức tải, thời gian chạy, dữ liệu đầu vào, điều kiện dừng và điều kiện thành công** trước khi thử.

1. **Đặt SLO:** ví dụ 99% yêu cầu thành công và p95 end-to-end dưới 500 ms ở tải mục tiêu 30 requests/s; phân biệt response HTTP trả 202 với công việc nền đã hoàn tất.
2. **Chọn workload đại diện:** tỉ lệ truy vấn nhẹ/nặng, kích thước payload, số tenant, cache hit/miss, thao tác DB thật sự và tỷ lệ request có retry. Một endpoint no-op không thay thế được luồng nghiệp vụ.
3. **Chuẩn bị môi trường:** xác nhận giới hạn CPU/RAM, số replica, DB pool, HTTP quotas, dataset, cache và version. Tránh tải lên môi trường dùng chung khi chưa phối hợp.
4. **Warm up:** cho JIT, connection pool và cache đạt trạng thái làm việc; ghi rõ nếu phép đo bao gồm hoặc loại bỏ warm-up.
5. **Tăng tải từng bậc:** chẳng hạn 10 → 20 → 30 → 40 → 50 requests/s, giữ mỗi bậc đủ lâu để nhìn backlog và tail latency; bổ sung thử burst nếu workload thực có burst.
6. **Thu thập cùng lúc:** arrival, throughput/goodput, error/timeout, latency histogram, pool và dependency metrics. Lưu lại cấu hình chính xác của mỗi lượt.
7. **Lặp lại và đối chiếu:** giữ các yếu tố khác ổn định, đổi một nhóm tham số có giả thuyết cụ thể, đo lại nhiều lần nếu kết quả nhiễu.

**Kiểm thử vòng kín và vòng hở:** nếu load generator chỉ gửi request mới sau khi request trước hoàn thành, khi server chậm tốc độ phát request cũng giảm, có thể che phần tải mà server không đáp ứng được. Một phép thử với **constant arrival rate** phù hợp khi cần biết hệ thống xử lý thế nào dưới tốc độ đến đã định. Tuy nhiên, nếu máy tạo tải không đủ virtual users/tài nguyên, kết quả cũng bị sai: cần theo dõi số iteration bị bỏ và tải phát thực tế.

### 6.1. Ví dụ k6: tăng tốc độ đến có kiểm soát

Tệp **load.js** cho một endpoint HTTP đồng bộ giả định có tên /api/work:

~~~javascript
import http from "k6/http";
import { check } from "k6";

export const options = {
  scenarios: {
    steady: {
      executor: "constant-arrival-rate",
      rate: 20,
      timeUnit: "1s",
      duration: "2m",
      preAllocatedVUs: 40,
      maxVUs: 100
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500"]
  }
};

export default function () {
  const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
  const response = http.get(baseUrl + "/api/work");
  check(response, {
    "response is 200": (r) => r.status === 200
  });
}
~~~

Khi đã cài k6, chạy ví dụ trên môi trường thử nghiệm bằng:

~~~bash
BASE_URL=http://localhost:8080 k6 run load.js
~~~

Đây là lệnh theo cú pháp shell POSIX; trong PowerShell có thể thiết lập biến môi trường bằng cú pháp tương ứng trước khi chạy k6. Thay /api/work bằng endpoint thật và điều chỉnh workload để không dùng một key cache-hit duy nhất. Threshold minh họa **không phải SLO áp dụng sẵn cho dự án**.

Nếu endpoint trả **202 Accepted** để khởi tạo tác vụ nền, metric http_req_duration đo thời gian **nhận phản hồi 202**, chưa phải thời gian công việc hoàn tất. Phải bổ sung đo từ khi gửi job đến khi trạng thái cuối READY/DONE/FAILED; nếu không, load test có thể báo “rất nhanh” dù queue nền dài hàng phút. Cũng cần theo dõi dropped_iterations để biết load generator có duy trì được tốc độ đến không.

Dụng cụ có thể là k6, JMeter, Gatling hoặc công cụ nội bộ; nguyên tắc quan trọng là số liệu có thể đối chiếu cùng tải, cùng ranh giới và cùng tài nguyên phụ thuộc.

## 7. Java 17 độc lập: chứng minh giới hạn tài nguyên phía dưới

Ví dụ sau tạo **8 worker** nhưng chỉ cho **3 tác vụ cùng lúc** sử dụng dependency mô phỏng bằng Semaphore. Dù tăng số worker, đỉnh số lời gọi ở dependency không vượt 3. Chương trình dùng queue có giới hạn và kiểm tra Future để không âm thầm bỏ qua lỗi.

Tệp **DependencyLimitDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DependencyLimitDemo {
    private static final int TASKS = 24;
    private static final int DOWNSTREAM_LIMIT = 3;

    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                8, 8, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32),
                new ThreadPoolExecutor.AbortPolicy());

        Semaphore slots = new Semaphore(DOWNSTREAM_LIMIT);
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger peakCalls = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < TASKS; i++) {
                futures.add(executor.submit(() -> {
                    boolean acquired = false;
                    try {
                        acquired = slots.tryAcquire(5, TimeUnit.SECONDS);
                        if (!acquired) {
                            throw new IllegalStateException(
                                    "Dependency permit timeout");
                        }

                        int active = activeCalls.incrementAndGet();
                        peakCalls.accumulateAndGet(active, Math::max);

                        try {
                            // Mô phỏng thời gian giữ một slot của dependency.
                            TimeUnit.MILLISECONDS.sleep(40);
                            completed.incrementAndGet();
                        } finally {
                            activeCalls.decrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                "Worker interrupted", e);
                    } finally {
                        if (acquired) {
                            slots.release();
                        }
                    }
                }));
            }

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            System.out.println("completed=" + completed.get());
            System.out.println(
                    "peakWithinLimit=" +
                    (peakCalls.get() <= DOWNSTREAM_LIMIT));
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Executor did not stop");
            }
        }
    }
}
~~~

Biên dịch và chạy trên Java 17+:

~~~bash
javac DependencyLimitDemo.java
java DependencyLimitDemo
~~~

**stdout khi toàn bộ 24 tác vụ hoàn tất trong giới hạn thời gian:**

~~~text
completed=24
peakWithinLimit=true
~~~

Số peak thực tế phụ thuộc lịch chạy thread và không được in ra dưới dạng giá trị cố định. Điều luôn được chứng minh bởi Semaphore là **không có hơn 3 tác vụ giữ permit cùng lúc**; nếu môi trường quá tải khiến chờ quá 5 giây hoặc Future quá 10 giây, chương trình báo lỗi chứ không giả vờ thành công. Ví dụ không nhằm benchmark throughput máy.

Điều gì đáng quan sát?

- Executor có thể chạy đến **8 callback** đồng thời, nhưng tối đa **3 callback** đang giữ permit của dependency.
- Trong ví dụ này, **5 worker khác có thể block** trong tryAcquire chờ permit, đồng thời queue executor vẫn còn việc. Nhiều worker không đồng nghĩa nhiều công việc hữu ích.
- finally giải phóng permit khi việc giữ tài nguyên kết thúc, kể cả callback bị ngắt hoặc có lỗi; chỉ release nếu đã acquire thành công.
- Semaphore là **giới hạn cục bộ trong một JVM**. Nhiều instance cần cộng giới hạn của các instance hoặc dùng cơ chế giới hạn chia sẻ phù hợp.
- Timeout đặt tại permit giúp tránh chờ vô hạn, nhưng vì nó chặn worker trong khi đợi, đây là mô hình **dễ quan sát giới hạn** hơn là thiết kế admission tối ưu cho ứng dụng tải cao.

Trong production, ưu tiên quyết định **chấp nhận/từ chối hoặc giới hạn tác vụ trước khi chúng chiếm worker** khi có thể. Nếu đã có hàng đợi của executor, đừng vô tình tạo thêm một hàng đợi chờ permit thật dài mà không có deadline và backpressure.

## 8. Áp dụng với Spring ThreadPoolTaskExecutor

### 8.1. Bắt đầu từ executor có giới hạn

Spring ThreadPoolTaskExecutor cho phép cấu hình corePoolSize, maxPoolSize và queueCapacity. Ví dụ **một cấu hình ứng dụng**, không phải chương trình độc lập:

Tệp **DependentIoExecutorConfig.java**:

~~~java
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class DependentIoExecutorConfig {

    @Bean("dependentIoExecutor")
    public ThreadPoolTaskExecutor dependentIoExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("dependent-io-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
~~~

Trong ThreadPoolExecutor truyền thống, khi số worker đạt corePoolSize, tác vụ thường được xếp vào queue trước khi pool cố mở rộng đến maxPoolSize; với queue chưa đầy, tăng maxPoolSize có thể **không thay đổi số worker thực tế**. Khi queue đã đầy và pool đạt maxPoolSize, tác vụ bị từ chối theo handler. Spring có thể bọc lỗi từ chối dưới TaskRejectedException. Vì vậy hãy đo poolSize, activeCount, queueSize và rejection khi điều chỉnh thay vì chỉ nhìn các số đã cấu hình.

Các số **4/8/16** được chọn để minh họa cơ chế; không thể coi đây là cấu hình chuẩn. Queue capacity phải cân bằng giữa hấp thụ burst ngắn và tránh giữ request/job quá thời hạn sử dụng. Một queue rất lớn có thể che overload và làm latency tăng trong khi request gốc đã timeout.

### 8.2. Giới hạn riêng số cuộc gọi tới dependency

Nếu một remote API chỉ chịu được số cuộc gọi đồng thời hữu hạn, có thể dùng semaphore để giới hạn **đoạn gọi remote**. Ví dụ sau cần một bean RemoteApi do ứng dụng cung cấp.

Tệp **RemoteLookupService.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

interface RemoteApi {
    String fetch(String id);
}

@Service
public class RemoteLookupService {
    private final RemoteApi remoteApi;
    private final Semaphore remotePermits = new Semaphore(6);

    public RemoteLookupService(RemoteApi remoteApi) {
        this.remoteApi = remoteApi;
    }

    @Async("dependentIoExecutor")
    public CompletableFuture<String> lookup(String id) {
        // Fail fast nếu dependency đã đạt giới hạn cục bộ.
        if (!remotePermits.tryAcquire()) {
            return CompletableFuture.failedFuture(
                    new RejectedExecutionException(
                            "Remote API is at concurrency limit"));
        }

        try {
            String value = remoteApi.fetch(id);
            return CompletableFuture.completedFuture(value);
        } finally {
            remotePermits.release();
        }
    }
}
~~~

**Cách dùng:** phải gọi lookup(id) **qua Spring proxy** (ví dụ từ một bean khác được inject), để @Async thật sự gửi việc vào dependentIoExecutor. Self-invocation trong cùng bean có thể bỏ qua proxy. RemoteApi cần implementation, timeout HTTP và cơ chế xử lý lỗi phù hợp; ví dụ trên chỉ mô tả ranh giới đồng thời.

Có hai giới hạn độc lập:

- Executor tối đa 8 worker và tối đa 16 task chờ trong queue.
- Semaphore cho tối đa 6 lời gọi remote **đang chạy đoạn bảo vệ** trong một instance.

Nếu 8 worker cùng chạy, 2 worker có thể nhận ngay lỗi “remote at limit” khi 6 permit đã dùng; chúng **không đứng chờ permit**. Việc trả failedFuture giúp người gọi quan sát lỗi thay vì âm thầm bỏ việc, nhưng ứng dụng còn cần quyết định cách chuyển lỗi thành HTTP 429/503 hoặc kết quả nghiệp vụ thích hợp. Nếu executor từ chối task **trước khi gọi phương thức @Async**, lỗi xuất hiện ở lớp submit; nhánh failedFuture bên trong phương thức không bắt được loại từ chối này. Hãy kiểm thử cả hai ranh giới.

**Cảnh báo về hàng đợi:** ở ví dụ này các task vẫn có thể xếp hàng tại executor *trước khi* kiểm tra semaphore. Với yêu cầu kiểm soát admission ngay tại đầu vào, hãy đặt giới hạn trước khi submit hoặc dùng bulkhead/rate limiter có hợp đồng giải phóng permit an toàn khi submit thất bại, task bị cancel, queue bị drain hoặc executor shutdown. Không nên chuyển semaphore ra trước submit một cách máy móc nếu không xử lý đầy đủ việc giải phóng permit trong những trường hợp đó.

Nếu hệ thống chạy 3 instance và mỗi instance giới hạn 6, tổng tối đa là **18 lời gọi đồng thời** tới remote API, trước khi tính các ứng dụng khác cũng gọi API đó. Khi quota là *toàn hệ thống*, hãy phân bổ ngân sách theo replica hoặc dùng cơ chế giới hạn phân tán phù hợp, đồng thời xem autoscaling thay đổi ngân sách này như thế nào.

## 9. Tối ưu có điều kiện: đọc triệu chứng để chọn cách chỉnh

| Dấu hiệu khi tăng tải | Nút thắt có thể xảy ra | Hướng kiểm chứng và điều chỉnh |
| --- | --- | --- |
| Executor queue wait tăng, run duration ổn định, dependency còn dư | Pool xử lý không kịp, queue policy hoặc phân loại tác vụ sai | Xem active/poolSize, thread dump, arrival/completion; thử điều chỉnh pool/queue có kiểm soát |
| Executor run tăng, Hikari pending tăng, DB active chạm max | Worker chờ connection DB | Giảm/giới hạn request dùng DB, rút transaction, tối ưu query/lock, xem kích cỡ DB pool dựa trên năng lực DB |
| HTTP dependency p95 tăng, nhiều 429/503 | Quota hoặc downstream quá tải | Giảm concurrency/rate, backoff có jitter, circuit breaker, deadline; phối hợp hạn mức |
| CPU gần 100%, GC/alloc tăng, nhiều runnable thread | Giới hạn CPU/memory chính ứng dụng | Profile hotspot, giảm work/alloc, tối ưu thuật toán; tăng thread thường không hiệu quả |
| Queue ngắn nhưng rejection cao | Queue nhỏ, pool bão hòa hoặc chính sách fail-fast | Đánh giá rejection có dự kiến hay không, backpressure và cách trả lỗi |
| p50 ổn nhưng p99 rất xấu | Burst, head-of-line blocking, slow dependency, lock | Xem histogram, tách fast/slow task, kiểm tra connection acquisition và dependency tail |
| Tăng thread làm throughput không đổi, timeout tăng | Đã đạt trần bottleneck bên dưới | Dừng tăng thread, đo capacity dependency và giảm in-flight/queue |
| Tăng DB pool làm DB CPU/lock wait tăng | Pool lớn gây quá tải DB | Hạ concurrency và tối ưu truy vấn/index/transaction; kiểm tra bằng tải tương đương |

Tùy tính chất công việc, có thể:

- **Tách executor** cho công việc chậm và nhanh để tránh tác vụ dài giữ hết worker phục vụ tác vụ ngắn.
- **Giới hạn việc nhận vào** (bounded admission), fail fast khi quá tải, hoặc chuyển sang durable queue có consumer concurrency hữu hạn khi chấp nhận xử lý muộn.
- **Đặt timeout từng stage**: chờ queue/permit, chờ connection, gọi dependency và deadline toàn request. Một timeout ở phía caller không tự dừng công việc đang chạy.
- **Chọn queueCapacity nhỏ vừa đủ hấp thụ burst** trong ngân sách latency; queue to làm backlog bền vững và lãng phí bộ nhớ.
- **Giảm thời gian giữ connection** bằng transaction ngắn, query hiệu quả, không giữ transaction qua HTTP call chậm nếu không cần.
- **Kiểm soát retry** vì request retry thêm là tải mới đổ vào dependency đang lỗi. Backoff có jitter, giới hạn số lần và tổng deadline; tránh retry storm.
- **Cân nhắc cache/batch** khi đúng semantics; cache có thể hạ áp lực DB, batch có thể cải thiện hiệu suất nhưng cũng có thể làm một transaction hoặc worker bị giữ lâu.

Không thay đổi nhiều tham số đồng thời rồi coi việc throughput tăng là do một tham số duy nhất. Nên có giả thuyết và phép đo trước/sau cho từng thay đổi.

## 10. Ví dụ tính và chọn thí nghiệm tuning

Giả sử một ứng dụng Spring có:

- 4 worker đang xử lý task dùng DB, queueCapacity 100.
- HikariCP maximumPoolSize = 8, DB query/transaction giữ connection trung bình 200 ms.
- Đầu vào cao điểm 50 tác vụ/giây; mỗi tác vụ cần một lần dùng connection.
- Mục tiêu nghiệp vụ: p95 end-to-end < 500 ms và error rate < 1%.

Với giả định mỗi connection phục vụ tuần tự một task trong 0,2 s và mọi connection đều bận hữu ích, trần lý tưởng của dependency là khoảng 8 / 0,2 = **40 tasks/s**. Lượng đến 50 tasks/s vượt trần này; ngay cả khi pool mở đến 50 worker, backlog vẫn tăng nếu công việc và thời gian giữ connection không đổi.

Giả thuyết kiểm chứng:

1. **Baseline:** giữ cấu hình, đo arrival/completion, p95/p99 total, queue wait, DB active/pending, connection acquisition, timeout, CPU DB.
2. **Nếu query/transaction là giới hạn:** giảm thời gian giữ connection trung bình từ 200 ms xuống 120 ms bằng thay đổi được kiểm chứng. Trần lý tưởng khi đó khoảng 8 / 0,12 ≈ **66,7 tasks/s**; đây chỉ là dự báo cần thử thực tế.
3. **Nếu lượng đến không thể giảm:** có thể xem DB capacity và pool size sau khi chứng minh DB có dư tài nguyên. Tăng connection mà DB đang bão hòa chưa chắc tạo lợi ích.
4. **Nếu burst rất ngắn nhưng average dưới capacity:** bounded queue có thể hấp thụ burst; cần đo tác vụ đợi bao lâu và có vượt deadline hay không.
5. **Nếu đầu vào vượt capacity kéo dài:** áp dụng admission control/backpressure, đưa việc vào durable queue hoặc tăng năng lực thật của dependency. Không thể dùng RAM queue vô hạn để “xóa” mất cân bằng bền vững.

Kết quả cần ghi lại dưới cùng một tập workload:

| Lượt thử | Concurrency / queue | DB pool | Goodput | p95 total | p95 queue wait | Timeout/reject |
| --- | --- | --- | --- | --- | --- | --- |
| A — baseline | Giá trị hiện tại | Giá trị hiện tại | Đo thực tế | Đo thực tế | Đo thực tế | Đo thực tế |
| B — đổi executor | Chỉ một thay đổi | Giữ nguyên | Đo lại | Đo lại | Đo lại | Đo lại |
| C — cải thiện query | Giữ cấu hình đối chứng | Giữ nguyên | Đo lại | Đo lại | Đo lại | Đo lại |
| D — giới hạn admission | Chọn giới hạn có căn cứ | Giữ nguyên | Đo lại | Đo lại | Đo lại | Đo lại |

Bảng này là **mẫu ghi phép đo**, không chứa số liệu benchmark giả định. Chỉ chấp nhận một thay đổi khi nó cải thiện mục tiêu có ý nghĩa và không gây lỗi phụ ở dependency hoặc workload khác.

## 11. Các bẫy thường gặp khi điều chỉnh concurrency

### 11.1. Chỉ tăng maxPoolSize

Trong ThreadPoolExecutor với hàng đợi có khả năng nhận việc, sau khi đạt corePoolSize, task thường vào queue trước khi pool tạo worker vượt core. Nếu queue chưa đầy, tăng maxPoolSize từ 8 lên 32 có thể không thay đổi số worker quan sát được. Cần biết chính xác queueCapacity và policy đang chạy.

### 11.2. Coi tất cả active thread là công việc hữu ích

Active thread có thể đang chờ DB connection, semaphore, HTTP response hoặc lock. Tăng worker sẽ nhân số thread chờ mà chưa cải thiện latency của một cuộc gọi. Dùng thread dump, executor run timer và dependency acquisition timer để phân biệt.

### 11.3. Bỏ qua các tầng queue nối tiếp

Executor queue + semaphore wait + connection pool pending + downstream queue có thể khiến tổng thời gian chờ lớn dù mỗi tầng riêng nhìn có vẻ bình thường. Một request có thể hết timeout trước khi tới bước xử lý thật; task nền vẫn có thể tiếp tục chiếm tài nguyên sau khi caller bỏ cuộc.

### 11.4. Dùng timeout như công cụ tăng throughput

Timeout rút ngắn thời gian bỏ cuộc, không làm dependency xử lý nhanh hơn. Timeout quá ngắn có thể tăng retry và giảm goodput. Hãy thiết kế deadline end-to-end cùng chính sách cancellation, retry budget và đo trạng thái thực của task.

### 11.5. Tăng parallelism nhưng không kiểm tra cấu hình downstream

Hikari pool size, DB CPU, HTTP max connections, per-host quota, Redis limits và số replica đều ảnh hưởng concurrency thực. Nếu có ba instance và limit mỗi instance tăng từ 5 lên 10, tổng yêu cầu đồng thời tối đa có thể tăng từ 15 lên 30; autoscaling cũng làm phép tính thay đổi.

### 11.6. Chỉ nhìn “requests/s” mà bỏ qua errors

Một endpoint chấp nhận 200 requests/s nhưng trả lỗi hoặc 202 trong khi job nền tồn đọng không chứng minh khả năng hoàn thành 200 công việc/s. Đếm **goodput**, tỷ lệ hoàn thành đúng deadline và queue age cùng rejection/timeout.

### 11.7. Đo tải vòng kín rồi bỏ sót backlog

Khi client phát yêu cầu tiếp theo chỉ sau khi nhận phản hồi trước, tốc độ đến có thể tự giảm lúc hệ thống chậm. Để đo khả năng chịu một luồng đầu vào định trước, cần kiểm chứng actual arrival rate và số iteration không được phát.

### 11.8. Dùng queue hoặc thread vô hạn để “không từ chối việc”

Unbounded queue có thể làm memory tăng, job cũ chờ quá lâu và che overload. Thread tạo không giới hạn gây context switching, OOM hoặc làm downstream bị dồn tải. Bounded queue và policy từ chối rõ ràng là cơ chế bảo vệ, không phải lỗi mặc định.

### 11.9. Áp dụng CallerRunsPolicy mà không đo phía caller

Khi queue/pool bão hòa, CallerRunsPolicy có thể để thread gọi tự xử lý task. Điều này tạo backpressure nhưng cũng có thể chặn HTTP request thread, thay đổi latency và ngữ cảnh chạy. Cần đo cả thời gian submit và tác động lên thread gọi; không giả định tác vụ luôn chạy trong thread executor.

### 11.10. Dùng virtual thread để “mở khóa” mọi giới hạn

Virtual threads giảm chi phí giữ nhiều tác vụ blocking so với platform threads trong những trường hợp phù hợp, nhưng **không tăng số DB connection, quota API, CPU hoặc RAM của dependency**. Việc bỏ giới hạn thread mà không bổ sung concurrency limit rõ ràng có thể khiến hàng ngàn request cùng tranh chấp cùng một connection pool.

### 11.11. Không xử lý kết quả lỗi từ @Async

Với phương thức @Async trả void, exception không trở về caller như một return value thông thường. Nên quan sát qua AsyncUncaughtExceptionHandler hoặc thiết kế trả Future/CompletableFuture để theo dõi kết quả. Với Future, phải xử lý exceptional completion và timeout; task bị từ chối khi submit có thể thất bại ngay trước khi Future được trả.

### 11.12. Nhầm lẫn giới hạn per-instance và toàn hệ thống

Local Semaphore có hiệu lực trong **một JVM**. Nếu service có 10 pod và mỗi pod cho 20 cuộc gọi, tổng tiềm năng là 200; không được coi semaphore 20 là global limit. Quota dùng chung cần cách chia ngân sách hoặc rate limiter phân tán, kèm cơ chế xử lý thay đổi số replica và các client khác.

## 12. Quy trình đề xuất cho một lần tuning thực tế

**Bước 1 — Xác định mục tiêu đo.** Chọn một luồng nghiệp vụ cụ thể và SLO end-to-end, error budget, mức tải mục tiêu và đơn vị goodput. Ghi rõ cách xử lý HTTP 202/job nền.

**Bước 2 — Vẽ ranh giới tài nguyên.** Nêu executor nào nhận task, queue ở đâu, DB/HTTP client dùng pool nào, concurrency/rate limit của downstream và số instance cùng sử dụng tài nguyên.

**Bước 3 — Thu baseline.** Ghi config, input rate, completion rate, wait/run p95/p99, dependency acquisition/call, active/pending, CPU/GC và rejected/timeout trong cùng cửa sổ.

**Bước 4 — Xác định saturation point.** Tăng tải theo bậc đến khi p95/p99, queue age, lỗi hoặc dependency pending bắt đầu tăng nhanh hơn throughput hữu ích. Đây là điểm cần hiểu, chưa phải lý do tự động nâng mọi giới hạn.

**Bước 5 — Đưa giả thuyết và chọn một thay đổi.** Nếu DB là bottleneck, ưu tiên rút thời gian query/transaction hoặc kiểm soát số in-flight DB; nếu CPU là giới hạn, profile CPU; nếu executor thiếu worker nhưng dependency còn dư, mới thử điều chỉnh pool. Nếu request rate vượt năng lực lâu dài, dùng backpressure hoặc nâng capacity thật.

**Bước 6 — Đo lại dưới tải tương đương.** So sánh goodput, p95/p99 total, queue wait, dependency pending, CPU, timeout và error. Kiểm tra burst, tải nền và nhiều replica nếu liên quan.

**Bước 7 — Đặt guardrails.** Ghi lại threshold cảnh báo (queue age, connection pending, error), giới hạn queue/concurrency, deadline, rejection policy và kế hoạch rollback. Kiểm tra tác dụng phụ khi traffic tăng đột biến hoặc downstream ngừng đáp ứng.

Không có một con số poolSize đúng cho mọi ứng dụng. Cấu hình tốt là cấu hình có **dữ liệu đo chứng minh** đáp ứng tải mục tiêu, bảo vệ dependency và ổn định khi có lỗi.

## 13. Câu hỏi tự kiểm tra

**Câu 1:** Executor có 32 worker, DB pool chỉ 8 connection, active DB luôn 8, pending connection cao, p99 tăng. Nên tăng worker lên 64 không?  
**Trả lời:** Chưa có căn cứ. Các worker có thể đang block chờ DB. Cần đo thời gian giữ connection, query/lock và CPU DB; giảm số in-flight hoặc tối ưu DB trước. Thay đổi worker chỉ có ý nghĩa nếu dependency còn capacity thật.

**Câu 2:** Một HTTP dependency có 10 slot, mỗi call giữ slot trung bình 100 ms. Có thể tuyên bố năng lực chắc chắn là 100 rps không?  
**Trả lời:** Không. 10 / 0,1 = 100 calls/s là ước lượng lý tưởng theo riêng tài nguyên đó, chưa tính biến thiên tail, network, retries, quota, CPU và chi phí khác. Cần benchmark ở tải đại diện và giữ margin an toàn.

**Câu 3:** Queue wait gần bằng 0 nhưng nhiều request bị reject, có thể kết luận hệ thống không nghẽn không?  
**Trả lời:** Không. Queue rất nhỏ hoặc fail-fast có thể giữ wait thấp bằng cách từ chối việc. Phải xem rejection, goodput và latency trên cả request thất bại.

**Câu 4:** Ba pod có cùng Semaphore(6). Phía downstream chỉ cho 12 lời gọi đồng thời trên toàn bộ account. Cấu hình hiện tại đủ bảo vệ chưa?  
**Trả lời:** Chưa. Ba pod có thể tạo 18 lời gọi đồng thời, chưa tính client khác. Cần điều chỉnh ngân sách per-pod hoặc dùng giới hạn chung.

**Câu 5:** k6 ghi p95 HTTP = 30 ms cho endpoint trả 202, nhưng job mất 10 giây mới hoàn tất. Có mâu thuẫn không?  
**Trả lời:** Không. Response 202 chỉ xác nhận tiếp nhận công việc; phải đo trạng thái cuối và queue age của pipeline để đánh giá SLO công việc nền.

**Câu 6:** Khi thêm worker, throughput hữu ích tăng từ 25 lên 30 successes/s, nhưng p99 tăng từ 0,5 lên 3 giây và lỗi timeout tăng gấp đôi. Có thể gọi là cải thiện không?  
**Trả lời:** Không thể chỉ dựa vào throughput. Phải so với SLO, error budget và chi phí tác động lên dependency. Có thể một cấu hình ít concurrency hơn cho tỷ lệ hoàn thành đúng hạn tốt hơn.

## 14. Checklist khi đo tải và chốt giới hạn

- [ ] Nêu rõ ranh giới đo **request, task, executor và dependency**.
- [ ] Biết **số replica**, concurrency limit và rate/quota của từng dependency.
- [ ] Có **goodput, errors, timeout, rejection** bên cạnh throughput.
- [ ] Có **p50/p95/p99 end-to-end**, queue wait và dependency acquisition/call.
- [ ] Kiểm tra **pool active/idle/pending**, queue depth/age và CPU/GC.
- [ ] Workload đại diện, có warm-up, nhịp đến thực tế và nhiều mức tải.
- [ ] Mỗi thay đổi cấu hình có giả thuyết, baseline và đo lại cùng tải.
- [ ] Queue, admission và retry đều có **giới hạn**, timeout và đường xử lý lỗi.
- [ ] Tính đến **tổng concurrency qua các instance** thay vì chỉ một JVM.
- [ ] Kết luận dựa trên **SLO và khả năng hoàn tất hữu ích**, không chỉ thread count.

**Điểm nhớ:** mức đồng thời là công cụ để sử dụng năng lực đang có, không tạo ra năng lực vô hạn. Hãy tìm tài nguyên nào thực sự giới hạn throughput, đo thời gian chờ tại từng tầng, rồi tăng hoặc giảm concurrency sao cho dependency luôn hoạt động trong khoảng chịu tải hiệu quả và các tác vụ hoàn thành đúng hạn.

## Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): cấu hình TaskExecutor, @Async, pool và queue.
- [Spring Framework — ThreadPoolTaskExecutor API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): corePoolSize, maxPoolSize, queueCapacity, pool/active/queue metrics.
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html): cách nhận task, queue và rejection policies.
- [Java SE 17 — Semaphore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html): permit, tryAcquire timeout, release và giới hạn truy cập.
- [HikariCP — Configuration](https://github.com/brettwooldridge/HikariCP): maximumPoolSize, connectionTimeout và các lưu ý về kích cỡ pool.
- [Grafana k6 — Constant arrival rate](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/): phát tải theo tốc độ đến xác định.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="design-practice-009">Danh sách lỗi thường gặp và tiêu chí tự đánh giá giải pháp</a>

<details>
<summary>Xem chi tiết</summary>

# Danh sách lỗi thường gặp và tiêu chí tự đánh giá giải pháp Spring Concurrency

## Mục tiêu và phạm vi

Trong một giải pháp sử dụng `@Async`, `TaskExecutor`, `CompletableFuture`, `@Scheduled` hoặc các worker chạy nền, “chạy được trên máy của tôi” không đủ để kết luận thiết kế đúng. Một giải pháp concurrency cần **đúng kết quả, đúng ngữ nghĩa thực thi, chịu được tải và có thể quan sát, kiểm thử, dừng an toàn**.

Bài học này là danh sách kiểm tra dùng lúc **thiết kế, code review, kiểm thử và trước khi triển khai** một giải pháp concurrency trong Spring Framework (ví dụ Spring 6.x chạy Java 17+). Nó không mặc định mọi hệ thống đều cần nhiều thread: nếu công việc cực ngắn, thuần CPU hoặc API cần kết quả ngay, chuyển sang bất đồng bộ có thể chỉ thêm độ phức tạp.

Hãy bắt đầu bằng bốn câu hỏi:

1. **Contract:** Caller mong nhận gì và lúc nào? Future, kết quả đồng bộ, acknowledgment hay trạng thái job? “Đã nhận tác vụ” khác “đã hoàn tất tác vụ”.
2. **Execution:** Ai nhận task, chạy ở đâu, giới hạn song song ra sao? Có queue, timeout, khả năng từ chối, retry hay cancellation không?
3. **Correctness:** Khi hai task cùng chạy, dữ liệu, giao dịch, bảo mật, thứ tự và side effect còn đúng không?
4. **Lifecycle/evidence:** Khi lỗi, quá tải, restart hoặc đóng context, điều gì xảy ra? Có test và metrics để **chứng minh** hành vi mong muốn không?

> Quy tắc tự đánh giá: với mỗi quyết định, ghi rõ **yêu cầu → thiết kế đáp ứng → phép kiểm chứng → kết quả**. Không đánh dấu “đạt” chỉ vì thấy annotation hoặc cấu hình xuất hiện trong mã.

## 1. Danh sách lỗi: ranh giới Spring proxy và API bất đồng bộ

| Mã | Lỗi thường gặp | Dấu hiệu và nguyên nhân | Cách sửa và bằng chứng cần có |
| --- | --- | --- | --- |
| P01 | Viết `@Async` nhưng tự gọi trong cùng bean | `this.runAsync()` không đi qua proxy ở proxy mode mặc định; mã chạy trên thread caller | Tách bean gọi/bean worker; lấy bean từ Spring container; test quan sát worker khác caller và việc caller nhận future |
| P02 | Quên `@EnableAsync` hoặc gọi `new Service()` | Annotation không được xử lý; method trông như đồng bộ | Bật async processing trong cấu hình thích hợp; integration test qua Spring context, không chỉ unit test Java thuần |
| P03 | Không chỉ định rõ executor | Task rơi vào executor mặc định/auto-config; khi thêm bean khác hành vi có thể đổi | Đặt tên executor theo workload, dùng `@Async("ioExecutor")`; xác minh đúng bean và cấu hình thực tế |
| P04 | Dùng `@Async void` cho tác vụ cần báo thành công/lỗi | Caller không có future để truy lỗi hoặc đợi hoàn tất; exception không thể truyền qua kết quả | Ưu tiên `CompletableFuture<T>` khi cần phản hồi; nếu `void` là chủ đích, cấu hình `AsyncUncaughtExceptionHandler` và metrics/log |
| P05 | Gọi `get()`/`join()` ngay sau `@Async` | Caller vẫn phải chờ; không có lợi ích latency nếu chưa làm việc độc lập | Thiết kế hợp đồng trả kết quả async hoặc xử lý thêm công việc trước khi join; đo thời gian đầu cuối |
| P06 | Kết hợp `@Async` và `supplyAsync(...)` mặc định một cách vô tình | Có thể có hai lần dispatch; stage nội bộ sử dụng `ForkJoinPool.commonPool()`, ngoài executor định cấu hình | Chỉ có một ranh giới offload rõ ràng, hoặc truyền `Executor` tường minh vào `supplyAsync`/`thenApplyAsync` |
| P07 | Nhầm giá trị future với bằng chứng có async | Method tính xong rồi trả `completedFuture(...)` trên caller vẫn cho ra kết quả đúng | Kiểm thử riêng giá trị nghiệp vụ và việc chạy qua proxy/worker; dùng latch khi cần chứng minh caller không bị chặn |
| P08 | “Fire-and-forget” nhưng caller báo hoàn tất giao dịch nghiệp vụ | HTTP 200/202 hoặc sự kiện “success” bị hiểu sai: task còn trong queue, có thể bị từ chối hoặc lỗi về sau | Xác định nghĩa response; nếu cần xử lý bền vững qua restart, cân nhắc outbox/queue bền vững và tracking job thay vì chỉ pool trong JVM |

**Ví dụ self-invocation (mã minh họa, không phải chương trình độc lập):**

```java
@Service
class WrongService {
    public void submit() {
        runAsync(); // Gọi qua this: proxy @Async mặc định không intercept.
    }

    @Async
    public void runAsync() {
        // Tác vụ tưởng chạy nền nhưng có thể chạy ngay trên caller.
    }
}
```

Một thiết kế phù hợp hơn cho proxy mode là đặt `@Async` ở bean khác và để caller inject bean đó. Khi dùng `@Transactional`, `@Cacheable` hoặc `@Retryable` cũng phải hiểu interceptor nào thực sự được áp dụng; đừng suy luận chỉ từ annotation.

## 2. Danh sách lỗi: pool, queue, tải và tài nguyên

### 2.1. Pool không có giới hạn có thể “ổn” lúc ít tải nhưng hỏng khi burst

| Mã | Lỗi thường gặp | Rủi ro | Hướng sửa và cách xác minh |
| --- | --- | --- | --- |
| C01 | Queue mặc định gần như không giới hạn | Task chờ tăng không kiểm soát, độ trễ phình to, giữ object trong bộ nhớ | Xác định `queueCapacity` theo ngân sách chờ/bộ nhớ; load test ở tải ổn định và đột biến |
| C02 | Chỉ tăng `maxPoolSize`, bỏ qua queue | `ThreadPoolExecutor` thường lấp core trước, tiếp tục queue, chỉ tăng thêm thread khi queue không nhận task | Đọc đồng thời `corePoolSize`, `maxPoolSize`, `queueCapacity`; quan sát poolSize/active/queue trong test tải |
| C03 | Một pool dùng chung cho tác vụ CPU, I/O chậm và tác vụ ưu tiên | Task I/O chiếm hết worker, làm chậm việc quan trọng dù CPU còn rảnh | Tách pool theo đặc tính và mức ưu tiên khi cần; test khi một nhóm upstream chậm hoặc treo |
| C04 | Không định nghĩa chính sách khi pool bão hòa | Submit có thể bị từ chối, caller chạy thay, hoặc task bị bỏ tùy `RejectedExecutionHandler` | Chọn `AbortPolicy`/`CallerRunsPolicy`/cơ chế khác có chủ đích; kiểm tra hợp đồng caller và hệ quả đối với HTTP/event |
| C05 | Dùng `DiscardPolicy` cho việc quan trọng | Task mất âm thầm, caller không biết đã mất | Không dùng silent discard khi phải hoàn tất; từ chối rõ ràng, lưu bền vững hoặc có cơ chế tái xử lý |
| C06 | “Tạo thêm thread” thay vì quản lý concurrency | Dễ hết connection, memory, file descriptor; cùng lúc gây quá tải downstream | Giới hạn cả concurrency tại client/downstream, có timeout, rate limit/bulkhead và phép đo |
| C07 | Cho tác vụ chờ tương hỗ trên cùng pool nhỏ | Pool starvation/deadlock: mọi worker đang đợi tác vụ khác nhưng không có worker rảnh để chạy chúng | Tránh `Future#get()` của child cùng pool hoặc dành executor riêng; dùng composition phù hợp và test với pool nhỏ |
| C08 | Chọn timeout tùy ý, không có ngân sách đầu-cuối | Queue wait + network + retry có thể vượt deadline dù mỗi bước có timeout | Xác định deadline end-to-end, phân bổ thời gian chờ queue, I/O và retry; kiểm chứng p95/p99 |
| C09 | Nhầm virtual threads với “không cần giới hạn tài nguyên” | Virtual thread giảm chi phí chờ kiểu blocking nhưng DB pool, API quota, CPU vẫn có trần | Với Java 21+ mới cân nhắc virtual threads; giới hạn downstream bằng semaphore/rate limit, đo pinning và tải thực tế |

**Đọc đúng `ThreadPoolTaskExecutor`:** nếu không tự giới hạn, queue capacity mặc định rất lớn (trên Spring Framework 6.2 là `Integer.MAX_VALUE`). Vì vậy cấu hình `maxPoolSize = 32` không có nghĩa pool sẽ lập tức tăng lên 32 worker khi mới có 32 yêu cầu: trước tiên core worker được dùng và queue nhận task. `queueCapacity = 0` lại là chiến lược handoff trực tiếp, có thể tạo nhiều thread rất nhanh nếu `maxPoolSize` không được giới hạn.

Khi quá tải, các policy có ý nghĩa khác nhau:

- `AbortPolicy` từ chối bằng exception; phải chuyển thành lỗi/retry hợp lý ở ranh giới gọi.
- `CallerRunsPolicy` có thể chạy task trên chính thread submit, làm chậm caller. Đây là backpressure đơn giản, **không** phù hợp nếu caller tuyệt đối không được blocking, ví dụ event loop.
- `DiscardPolicy` bỏ task một cách im lặng; không phù hợp với tác vụ cần bảo đảm xử lý.
- `DiscardOldestPolicy` có thể bỏ task đã chờ lâu nhất; cần hiểu hệ quả mất việc/thứ tự trước khi chọn.

### 2.2. Một phép tính sơ bộ để phát hiện cấu hình vô lý

Giả sử hệ thống nhận trung bình `40 task/giây`, mỗi task chiếm một worker khoảng `0,2 giây`. Nếu tải và thời gian xử lý ở trạng thái ổn định, số task thực thi đồng thời trung bình cần khoảng:

```text
Concurrency ≈ arrival_rate × service_time
            ≈ 40 task/s × 0,2 s
            ≈ 8 task đang thực thi
```

Một pool `4 worker` xử lý tối đa xấp xỉ `4 / 0,2 = 20 task/giây` trong mô hình đơn giản này; phần đến vượt khả năng xử lý sẽ tích trong queue. Với queue hữu hạn `30`, tốc độ tồn đọng lý tưởng hóa là `20 task/giây` nên sau khoảng `1,5 giây` có thể đầy (bỏ qua độ biến thiên và overhead). Đây **chỉ là ước lượng phát hiện rủi ro**, không phải thông số được bảo đảm: latency có phân phối, burst, remote throttling, lock và retry có thể thay đổi kết quả rất lớn.

Khi tự đánh giá, cần ghi **tải bình thường/đỉnh, thời gian phục vụ p50/p95/p99, giới hạn downstream, queue wait, rejection rate, CPU và memory**. Với công việc CPU-bound, nhiều thread hơn số CPU hữu ích có thể làm throughput kém đi; với I/O-bound, cần xét connection pool và độ trễ external service.

## 3. Danh sách lỗi: dữ liệu đồng thời, lỗi, timeout và hủy

| Mã | Vấn đề | Hành vi sai thường thấy | Bằng chứng cần kiểm tra |
| --- | --- | --- | --- |
| D01 | Chia sẻ mutable `ArrayList`, `HashMap` hoặc bộ đếm không đồng bộ | Mất cập nhật, `ConcurrentModificationException`, dữ liệu khó tái hiện | Test nhiều lượt với interleaving có kiểm soát; dùng immutable data, `ConcurrentHashMap`, `AtomicInteger` hoặc khóa phù hợp |
| D02 | “Đã thấy chạy đúng” nên bỏ qua visibility | Thread A cập nhật flag nhưng thread B có thể không nhìn thấy như mong đợi | Xác định happens-before qua volatile/lock/latch/future; không dùng `sleep` làm đồng bộ |
| D03 | Giữ khóa khi gọi I/O hoặc khi chờ future khác | Thread bị khóa lâu, throughput thấp, có thể deadlock | Xem thread dump và độ dài critical section; di chuyển I/O ra ngoài khóa khi bảo toàn invariant |
| D04 | Gộp mọi exception thành `null` hoặc log rồi bỏ qua | Caller không phân biệt thành công/thất bại; retry có thể xử lý trùng | Định nghĩa error contract, nguyên nhân gốc, retryable/non-retryable; kiểm tra path lỗi qua future |
| D05 | Dùng `CompletableFuture.orTimeout` rồi cho rằng công việc chắc chắn đã dừng | Future có thể hoàn thành lỗi vì timeout trong khi tác vụ I/O nền vẫn tiếp tục | Thiết kế timeout ở client I/O, deadline và cơ chế dừng tác vụ phù hợp; kiểm tra side effect sau timeout |
| D06 | Nhầm `CompletableFuture.cancel(true)` với ngắt worker | Ở `CompletableFuture`, tham số `mayInterruptIfRunning` không có tác dụng điều khiển interruption | Tách cancellation của kết quả khỏi cancellation của hoạt động thực; test hợp đồng cancellation thực tế |
| D07 | Retry vô hạn, không backoff/jitter | Retry storm làm hệ thống vốn quá tải càng quá tải, side effect bị lặp | Giới hạn số lần/thời gian, phân loại lỗi, backoff và idempotency key; test timeout và duplicate |
| D08 | Dùng `parallelStream()`/`CompletableFuture.supplyAsync` không chỉ định executor trong app đã quản lý pool | Workload “trốn” vào common pool, khó cô lập/giám sát | Truyền executor khi cần; phân biệt stage đồng bộ (`thenApply`) và stage async (`thenApplyAsync`) |
| D09 | Coi `get()` thành công là đã dọn sạch executor | Task hoàn thành nhưng pool có thể giữ worker và thread-locals | Kiểm tra shutdown và termination khi context đóng, kèm lifecycle test |
| D10 | Bỏ qua quyền sở hữu kết quả khi caller đã hủy request | Tác vụ vẫn ghi DB/gửi message dù caller không còn đợi | Định nghĩa nghiệp vụ: tiếp tục background hay abort; gắn job id, idempotency và trạng thái rõ ràng |

**Ví dụ về race (mã minh họa, không phải chương trình độc lập):**

```java
// Không an toàn khi nhiều task cùng tăng:
private int processed;
void onDone() {
    processed++; // read-modify-write không atomic
}

// Nếu chỉ cần counter độc lập:
private final AtomicInteger processedSafe = new AtomicInteger();
void onDoneSafely() {
    processedSafe.incrementAndGet();
}
```

`AtomicInteger` không tự giải quyết invariant gồm nhiều biến hoặc nhiều bản ghi DB. Khi cần “trừ tồn kho đúng một lần”, phải định nghĩa ranh giới giao dịch/khóa/điều kiện cập nhật tại nơi có nguồn dữ liệu chuẩn, thay vì chỉ dùng counter trong RAM.

## 4. Danh sách lỗi: transaction, context, scheduling và shutdown

### 4.1. Transaction và dữ liệu theo thread

Trong Spring transaction kiểu imperative với `PlatformTransactionManager`, transaction thường gắn với **thread hiện tại**. Tác vụ chạy trên worker mới không tự tham gia transaction ở caller.

**Sai lầm:** một method `@Transactional` mở giao dịch, gửi `@Async` ghi dữ liệu, rồi cho rằng rollback của caller sẽ tự rollback cả worker; hoặc worker đọc dữ liệu chưa commit của caller. Điều này có thể gây dữ liệu không nhất quán hoặc lỗi khó tái hiện.

**Tiêu chí đạt:** định nghĩa rõ khi nào dữ liệu được commit và nơi bắt đầu transaction của worker. Nếu nghiệp vụ cần “sự kiện chỉ được phát khi transaction commit thành công”, cân nhắc transaction-bound event/outbox hoặc cơ chế đảm bảo sau commit. Với dữ liệu cập nhật trong worker, transaction phải được thiết kế ở chính worker/service được gọi qua proxy; kiểm tra cả rollback và retry.

Tương tự, `SecurityContextHolder`, MDC/trace ID và các `ThreadLocal` không mặc nhiên được truyền cho thread của executor. Spring Security cung cấp `DelegatingSecurityContextAsyncTaskExecutor`/các wrapper phù hợp. Với MDC, có thể dùng `TaskDecorator` để chụp và phục hồi context, rồi **dọn context trong `finally`** để không rò thông tin giữa request. Không sao chép tùy tiện toàn bộ thông tin bảo mật sang job kéo dài sau phiên đăng nhập.

### 4.2. Lập lịch có thể chạy trùng

| Mã | Lỗi | Kết quả và cách giải quyết |
| --- | --- | --- |
| S01 | Nhầm `fixedRate` với `fixedDelay` | `fixedRate` đo giữa thời điểm bắt đầu theo lịch; `fixedDelay` tính khoảng trễ sau khi lần chạy trước hoàn tất. Chọn theo mục tiêu và đo drift |
| S02 | Đặt nhiều `@Scheduled` hoặc có nhiều instance chạy cùng job | Trigger có thể chồng lên nhau; triển khai nhiều replica có thể chạy một job ở nhiều JVM | Thiết kế idempotency và coordination/lock phù hợp khi chỉ một instance được phép làm |
| S03 | Job dài hơn khoảng lịch, không có chiến lược trùng lặp | Chạy dồn hoặc gây trễ tùy scheduler và pool; backlog bùng lên | Đo execution duration, lag, concurrency; quyết định skip/coalesce/serialize |
| S04 | Retry gửi lại sự kiện mà không có deduplication | Cùng một side effect xuất hiện hai lần | Job id/idempotency key, unique constraint, checkpoint hoặc outbox |
| S05 | Không đóng scheduler/executor khi dừng context | Thread nền còn sống, test treo, task bị mất/tiếp tục chạy sau shutdown | Test đóng context, kiểm tra executor `isShutdown()` và `isTerminated()`, công bố chính sách drain |
| S06 | Coi `shutdownNow()` là đảm bảo hủy tức thời | Chỉ cố ngắt tác vụ; I/O không ngắt được hoặc task bỏ qua interrupt vẫn chạy | Deadline hữu hạn, xử lý `InterruptedException`, cleanup trong `finally`, kiểm tra worker kết thúc |

Với Spring TestContext, `@SpringBootTest`/`@SpringJUnitConfig` thường **cache ApplicationContext** để tái sử dụng. Kết thúc một `@Test` không có nghĩa context đã đóng. Muốn test cleanup, dùng context tự tạo/đóng trong test hoặc cơ chế `@DirtiesContext` đúng lifecycle; không gọi `close()` tùy tiện trên context chung rồi làm hỏng test khác.

### 4.3. Khác nhau giữa một JVM và xử lý bền vững

`ThreadPoolTaskExecutor` chịu trách nhiệm thực thi task trong JVM hiện tại; queue trong RAM không phải hàng đợi bền vững. Khi process bị kill, task chưa hoàn tất có thể mất. Nếu hợp đồng nghiệp vụ đòi at-least-once, audit, replay hay không mất task qua restart, cần thêm cơ chế lưu task bền vững (database/outbox/message broker) và người tiêu thụ có idempotency. Đó là một **quyết định kiến trúc** khác với việc tăng số thread.

## 5. Một ví dụ có thể chạy: ranh giới async và executor có giới hạn

Ví dụ cố ý dùng tác vụ xử lý chuỗi đơn giản để code và kết quả kiểm tra được mà không cần dịch vụ ngoài. Trong thực tế, chỉ nên offload khi có lợi ích rõ (công việc blocking/lâu hoặc cần điều phối); **không** nên dùng `@Async` cho phép đổi chữ hoa này chỉ vì có thể.

Bộ tệp (Java 17+, Spring Framework 6.2.x, JUnit Jupiter 5):

```text
build.gradle
src/main/java/example/ReviewApp.java
src/test/java/example/ReviewAppTest.java
```

**`build.gradle`**

```groovy
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
    implementation 'org.springframework:spring-context:6.2.13'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.11.4'
}

application {
    mainClass = 'example.ReviewApp'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

**`src/main/java/example/ReviewApp.java`**

```java
package example;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class ReviewApp {
    public static void main(String[] args) throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            AsyncNormalizer service = context.getBean(AsyncNormalizer.class);
            Result result = service.normalizeAsync("  spring framework  ")
                    .get(3, TimeUnit.SECONDS);
            System.out.println(result.text());
        }
    }

    public record Result(String text, String executedOn) {}

    public static class Rules {
        public String normalize(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("input must not be blank");
            }
            return raw.strip().toUpperCase(Locale.ROOT);
        }
    }

    public static class AsyncNormalizer {
        private final Rules rules;

        public AsyncNormalizer(Rules rules) {
            this.rules = rules;
        }

        @Async("reviewExecutor")
        public CompletableFuture<Result> normalizeAsync(String raw) {
            String result = rules.normalize(raw);
            return CompletableFuture.completedFuture(
                    new Result(result, Thread.currentThread().getName()));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class Config {
        @Bean
        public Rules rules() {
            return new Rules();
        }

        @Bean
        public AsyncNormalizer asyncNormalizer(Rules rules) {
            return new AsyncNormalizer(rules);
        }

        @Bean("reviewExecutor")
        public ThreadPoolTaskExecutor reviewExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(4);
            executor.setQueueCapacity(16);
            executor.setThreadNamePrefix("review-");
            executor.setRejectedExecutionHandler(
                    new ThreadPoolExecutor.AbortPolicy());
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(5);
            return executor;
        }
    }
}
```

**Phân tích giải pháp:**

- `Rules` độc lập Spring, có thể unit test bằng dữ liệu đầu vào/ngoại lệ.
- `AsyncNormalizer` là bean được gọi qua Spring proxy; `@Async("reviewExecutor")` chọn pool rõ ràng.
- `completedFuture(...)` là **future bên trong method chạy trên worker**. Spring proxy trả future bất đồng bộ cho caller. Không cần gọi `CompletableFuture.supplyAsync` lần nữa trong method.
- `core=2, max=4, queue=16` làm đồng thời và lượng việc chờ có giới hạn. Đây chỉ là cấu hình minh họa; không được coi là sizing đủ cho production khi chưa đo tải.
- `AbortPolicy` chủ đích từ chối submission khi bão hòa; caller của ứng dụng thực phải chuyển rejection thành cơ chế backpressure/thông báo/retry hợp đồng, tránh báo “success” nhầm.
- Context đóng khi ra khỏi `try-with-resources`; executor được Spring quản lý lifecycle. `awaitTerminationSeconds(5)` là giới hạn chờ, không cam kết mọi task đều hoàn thành đúng hạn.
- `executedOn` chỉ phục vụ quan sát trong bài tập. Trong hệ thống thực nên dùng trace/metrics và tránh trả thông tin thread nội bộ như dữ liệu API.

Chạy:

```bash
./gradlew run
```

Trên Windows: `gradlew.bat run`. Chương trình in ra một dòng (không tính log của Gradle):

```text
SPRING FRAMEWORK
```

**`src/test/java/example/ReviewAppTest.java`**

```java
package example;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

class ReviewAppTest {
    @Test
    void asyncProxyReturnsCorrectResultAndExecutorStops() throws Exception {
        ThreadPoolExecutor pool;

        try (var context =
                new AnnotationConfigApplicationContext(ReviewApp.Config.class)) {
            ReviewApp.AsyncNormalizer service =
                    context.getBean(ReviewApp.AsyncNormalizer.class);
            ReviewApp.Result result = service
                    .normalizeAsync("  spring framework  ")
                    .get(3, SECONDS);

            assertEquals("SPRING FRAMEWORK", result.text());
            assertTrue(result.executedOn().startsWith("review-"));
            assertNotEquals(Thread.currentThread().getName(),
                    result.executedOn());

            pool = context.getBean(
                    "reviewExecutor", ThreadPoolTaskExecutor.class)
                    .getThreadPoolExecutor();
        }

        assertTrue(pool.isShutdown());
        assertTrue(pool.isTerminated());
    }

    @Test
    void workerFailureIsVisibleToCaller() {
        try (var context =
                new AnnotationConfigApplicationContext(ReviewApp.Config.class)) {
            ReviewApp.AsyncNormalizer service =
                    context.getBean(ReviewApp.AsyncNormalizer.class);

            ExecutionException error = assertThrows(
                    ExecutionException.class,
                    () -> service.normalizeAsync("  ").get(3, SECONDS));

            assertInstanceOf(IllegalArgumentException.class,
                    error.getCause());
        }
    }
}
```

Chạy:

```bash
./gradlew test --tests example.ReviewAppTest
```

Kỳ vọng: **hai test pass**. Test này kiểm tra kết quả, chạy trên worker có prefix cấu hình, lỗi qua `Future#get` và pool đã terminate sau khi đóng context. Nó **chưa** chứng minh tính không chặn của caller dưới mọi workload, xử lý overload hay retry; những hợp đồng đó cần test riêng với latch và saturation case. Một test kết quả đơn giản không thể thay thế toàn bộ rubric ở phần tiếp theo.

### Kiểm tra bằng chứng ở mức tải

Khi cần xác nhận policy từ chối, hãy dùng fake task chờ `CountDownLatch` để chiếm đủ worker và queue có giới hạn, sau đó submit thêm task và xác minh rejection **theo hợp đồng đã chọn**. Luôn nhả latch trong `finally`; dùng `Future#get(timeout, unit)`, không dựa vào `Thread.sleep` hoặc thứ tự scheduling giả định.

Với pool thực tế, log/tracing nên ghi task ID và event submit/start/finish/fail; metrics cần theo dõi `activeCount`, kích thước queue, số task hoàn tất, số bị từ chối và thời gian chờ từ submit đến start. Kiểm tra cả đường success, failure và khi downstream chậm.

## 6. Rubric tự đánh giá: từ cảm giác sang bằng chứng

Chấm mỗi tiêu chí **0 / 1 / 2**:

- **0 — Chưa đạt:** chưa có thiết kế rõ hoặc đã thấy lỗi.
- **1 — Có thiết kế nhưng thiếu bằng chứng:** đã mô tả, cấu hình hoặc có test đơn lẻ, song chưa kiểm chứng trường hợp quan trọng.
- **2 — Đạt và kiểm chứng:** hợp đồng rõ ràng, có test/metric/quan sát phù hợp, chỉ ra kết quả và giới hạn.

Nếu tiêu chí không áp dụng, đánh dấu **N/A kèm lý do** và loại khỏi mẫu số. Không dùng tổng điểm để che một lỗi có thể gây mất dữ liệu, lộ quyền, deadlock hoặc mất task quan trọng.

| # | Tiêu chí | Bằng chứng tối thiểu để đạt 2 điểm | Mức ưu tiên |
| --- | --- | --- | --- |
| 1 | **Đúng nghiệp vụ** | Unit test đầu vào hợp lệ, biên, lỗi và side effect; có invariant độc lập lịch thread | Bắt buộc |
| 2 | **Hợp đồng caller rõ** | Biết lúc nào nhận/hoàn thành task, cách trả result/error, API không báo success sớm trái hợp đồng | Bắt buộc |
| 3 | **Ranh giới async thật** | Test qua Spring proxy và executor đúng; self-invocation được loại trừ; có bằng chứng worker thực thi nếu yêu cầu | Bắt buộc |
| 4 | **Pool và queue có giới hạn hợp lý** | Có lý do chọn core/max/queue dựa trên tải và downstream; biết hành vi queue khi core bận | Bắt buộc |
| 5 | **Overload/backpressure** | Có policy từ chối/rate limit; test bão hòa và caller biết kết quả, không mất task âm thầm | Bắt buộc |
| 6 | **Thread safety và thứ tự** | Nêu immutable/shared state, happens-before, lock, yêu cầu ordering; test concurrency liên quan | Bắt buộc |
| 7 | **Lỗi, timeout, retry/cancel** | Test task exception, timeout và retry budget; phân biệt fail của future với hủy tác vụ nền | Bắt buộc |
| 8 | **Transaction và context** | Kiểm tra worker transaction/security/trace được tạo và dọn đúng; không giả định kế thừa thread-local | Bắt buộc khi có dữ liệu/người dùng |
| 9 | **Scheduling và duplicate** | Với job định kỳ, có chính sách overlap, nhiều replica, idempotency, checkpoint/replay nếu cần | Bắt buộc nếu có scheduler |
| 10 | **Quan sát và điều tra** | Có metrics/structured log/trace đủ đo queue wait, execution time, active, reject và xác định task lỗi | Rất nên có |
| 11 | **Kiểm thử xác định** | Test latch/timeout, không có `sleep` mơ hồ; có kiểm tra load/failure injection đúng trọng tâm | Bắt buộc |
| 12 | **Shutdown và giải phóng** | Test context close, pool shutdown/termination và cleanup tài nguyên; không để leak qua test/redeploy | Bắt buộc |

**Cách tính** (với các tiêu chí áp dụng): `Điểm = tổng điểm nhận được / (2 × số tiêu chí áp dụng) × 100%`. Ví dụ không có scheduler nên #9 là N/A; 11 tiêu chí còn lại có tổng `19/22 ≈ 86,4%`. Một ngưỡng nội bộ như `≥ 90%` có thể giúp thảo luận, **nhưng phải chặn việc thông qua** nếu còn một tiêu chí “Bắt buộc” ở mức 0 hoặc một rủi ro mất dữ liệu/bảo mật chưa được xử lý, bất kể phần trăm.

### Mẫu review có thể điền cho một giải pháp

```markdown
### Review: [Tên công việc / mã thay đổi]
- Contract caller: ...
- Workload: CPU/I/O; throughput bình thường/đỉnh; latency p95/p99: ...
- Executor: bean name, core/max/queue, rejection policy: ...
- Tải khi downstream chậm: ...
- Shared state/invariant/thứ tự: ...
- Transaction, security context, MDC/trace: ...
- Timeout/cancel/retry/idempotency: ...
- Scheduler/replica coordination (nếu có): ...
- Test: logic / async proxy / saturation / failure / shutdown: ...
- Metrics: active, queue, rejected, wait, execution: ...
- Rubric: ... điểm / ... điểm khả dụng
- Rủi ro còn lại, người chịu trách nhiệm, tiêu chí đóng rủi ro: ...
```

Mẫu trên là biểu mẫu Markdown **để áp dụng**, không phải một bài test tự động. Phần `...` phải được thay bằng bằng chứng cụ thể trong một lần review thực tế.

## 7. Bài tập tự đánh giá có lời giải mẫu

**Tình huống:** API nhận yêu cầu gửi thông báo; mỗi lần gọi tạo task đi tới dịch vụ ngoài, trung bình mất `200 ms`. Trong giờ cao điểm có thể nhận `100 yêu cầu/giây`. Thiết kế ban đầu: controller gọi `this.sendAsync(...)` trong cùng bean; task dùng `@Async void`; một `ThreadPoolTaskExecutor` có queue mặc định; client nhận HTTP 200 ngay, không có job id; khi downstream lỗi chỉ ghi log. Sau khi hệ thống được triển khai hai replica, scheduled retry chạy trên cả hai.

Tự đánh giá:

1. Liệt kê từng lỗi theo bảng ở trên, phân biệt **lỗi chắc chắn về thiết kế** và **rủi ro cần phép đo**.
2. Nêu hợp đồng API: client được xác nhận **đã chấp nhận** hay **đã gửi thành công**? Nếu chỉ chấp nhận, phải có cơ chế theo dõi và phục hồi job nào?
3. Với `100 task/s × 0,2 s`, tối thiểu cần bao nhiêu task đồng thời trong mô hình đơn giản nếu muốn theo kịp tải bình quân? Nếu chỉ có pool 8 worker, bottleneck nào có thể xảy ra?
4. Chọn phương án xử lý overload, duplicate và shutdown. Chỉ ra test đo được, thay vì nói “executor tự lo”.
5. Chấm rubric cho thiết kế ban đầu và sau khi sửa; mỗi điểm 2 phải gắn với test/metric.

**Gợi ý đáp án:**

- `this.sendAsync(...)` không đi qua Spring proxy ở chế độ proxy mặc định (P01). `void` làm caller không thể nhận kết quả qua future (P04). HTTP 200 có thể khiến client hiểu nhầm đã gửi xong (P08). Queue mặc định quá lớn (C01). Không có error contract (D04). Retry trên hai replica có nguy cơ trùng side effect (S02/S04). Tải thực tế có thể quá khả năng downstream (C06).
- Hợp đồng có thể là **202 Accepted + job ID**, trạng thái `PENDING/RUNNING/SUCCEEDED/FAILED` và một cơ chế lưu yêu cầu bền vững nếu không được phép mất job khi process restart. Nếu caller cần xác nhận đã giao thành công, buộc phải chờ một boundary có thể chứng minh được, hoặc dùng quy trình khác.
- Mô hình lý tưởng yêu cầu khoảng `100 × 0,2 = 20` task thực thi đồng thời. Pool 8 worker chỉ xử lý gần `8/0,2 = 40` task/s nếu I/O độc lập và ổn định; backlog tăng khoảng `60 task/s`. Nhưng 20 worker cũng chưa chắc đúng nếu nhà cung cấp giới hạn connection/quota; cần đo hiệu năng, rate limit và tail latency.
- Phương án tốt có thể tách bean dispatcher/worker, queue bền vững + idempotency key, consumer có concurrency giới hạn và backoff, lỗi được ghi trạng thái; scheduler retry có leader/lock/claim atomic hoặc thiết kế retry không tạo duplicate side effect.
- Test phải có ít nhất một trường hợp overload/rejection, một trường hợp remote lỗi, một duplicate delivery, một shutdown/restart và phép đo queue wait. Không thể kết luận “đạt 2” ở mọi mục trước khi chạy các kiểm tra đó.

## 8. Checklist cuối trước khi merge

- [ ] Có lý do nghiệp vụ hoặc hiệu năng để dùng bất đồng bộ; caller hiểu rõ ranh giới acknowledgment/completion.
- [ ] `@Async`/`@Scheduled` được bật và gọi qua cơ chế Spring phù hợp; executor/scheduler đúng bean.
- [ ] Pool, queue, downstream concurrency và rejection policy có giới hạn được giải thích bằng số liệu.
- [ ] Không còn trạng thái mutable không bảo vệ, khóa giữ qua I/O hoặc chờ child task trên pool dễ starvation.
- [ ] Future/exception, timeout, cancel, retry, idempotency và duplicate đều có hợp đồng.
- [ ] Transaction và security/MDC context của worker được quản lý đúng; không dựa vào transaction/thread-local của caller.
- [ ] Với scheduling: hiểu `fixedDelay`/`fixedRate`/cron, overlap và multi-replica.
- [ ] Có unit test logic, integration test proxy/worker, kiểm thử overload và shutdown theo mức rủi ro.
- [ ] Có quan sát đủ để giải thích “task nào, chờ bao lâu, chạy ở đâu, vì sao lỗi, bị từ chối hay chưa”.
- [ ] Đã ghi điểm rubric, rủi ro chưa đóng và hành động tiếp theo; không thông qua nếu còn lỗi bắt buộc gây mất dữ liệu hoặc treo tài nguyên.

## Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `TaskExecutor`, `@Async`, `@Scheduled`, proxy, executor qualification, exception handling.
- [Spring Framework 6.2 — ThreadPoolTaskExecutor API](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): core/max/queue, rejection và introspection.
- [Spring Framework — Declarative Transaction Implementation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html): transaction gắn với thread của imperative transaction manager.
- [Spring Security — Concurrency Support](https://docs.spring.io/spring-security/reference/servlet/integrations/concurrency.html): truyền và dọn `SecurityContext` khi chạy việc trên thread khác.
- [Spring Boot — Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.3/reference/features/task-execution-and-scheduling.html): sự khác biệt giữa Spring Boot auto-configured executor và executor tự khai báo.
- [JDK 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html), [CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html), [CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html).


</details>

- [Quay lại đầu trang](#back-to-top)
