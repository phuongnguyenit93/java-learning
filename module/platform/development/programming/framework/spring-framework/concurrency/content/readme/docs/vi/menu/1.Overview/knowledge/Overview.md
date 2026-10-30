<a id="back-to-top"></a>

# Tổng quan

## Menu
- [Đồng thời trong Spring: mục đích và các tình huống sử dụng](#overview-001)
- [Phân biệt đồng bộ, bất đồng bộ, song song và lập lịch](#overview-002)
- [Tác vụ, luồng gọi, luồng xử lý và kết quả](#overview-003)
- [Mối liên hệ giữa TaskExecutor, @Async, TaskScheduler và @Scheduled](#overview-004)
- [Tách thời điểm nhận yêu cầu khỏi thời điểm hoàn thành công việc](#overview-005)
- [Những bảo đảm Spring cung cấp và trách nhiệm của mã nghiệp vụ](#overview-006)
- [Ví dụ xuyên suốt: xử lý báo cáo và cập nhật dữ liệu định kỳ](#overview-007)
- [Lộ trình học và kiến thức Java concurrency cần có](#overview-008)

## <a id="overview-001">Đồng thời trong Spring: mục đích và các tình huống sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Đồng thời trong Spring: mục đích và các tình huống sử dụng

## 1. Bối cảnh và mục tiêu

Trong một ứng dụng Spring, nhiều yêu cầu HTTP, sự kiện, tác vụ nền và lần gọi dịch vụ bên ngoài có thể diễn ra trong cùng một khoảng thời gian. **Lập trình đồng thời (concurrency)** giúp tổ chức các công việc đang tiến triển cùng lúc và kiểm soát tài nguyên chúng dùng. Đây là một quyết định về cách điều phối công việc, không phải bảo đảm rằng mọi thao tác sẽ chạy nhanh hơn.

Sau bài này, người học cần phân biệt được đồng thời, song song, bất đồng bộ và không chặn; nhận ra tình huống nên dùng cơ chế đồng thời trong Spring; chọn đúng công cụ ở mức tổng quan; và đánh giá rủi ro của luồng, hàng đợi, lỗi, giao dịch, dữ liệu dùng chung.

### Bốn khái niệm thường bị nhầm

| Khái niệm | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| **Concurrency – đồng thời** | Nhiều công việc cùng tồn tại và được điều phối trong một khoảng thời gian; có thể xen kẽ trên một lõi CPU. | Server xử lý nhiều request đang chờ I/O. |
| **Parallelism – song song** | Hai hay nhiều phần việc thật sự thực thi cùng thời điểm, thường trên nhiều lõi CPU. | Tính toán độc lập trên nhiều worker. |
| **Asynchrony – bất đồng bộ** | Bên gọi có thể tiếp tục công việc thay vì đợi tác vụ kết thúc ngay tại lời gọi; kết quả thường tới qua `Future`, callback hoặc tín hiệu. | Gọi phương thức `@Async` và nhận `CompletableFuture`. |
| **Blocking – chặn** | Luồng hiện tại phải đợi để thao tác hoàn tất. | `Thread.sleep(...)`, JDBC đồng bộ, `future.get()`. |
| **Non-blocking – không chặn** | Thao tác không giữ luồng để ngồi chờ I/O; cơ chế thông báo khi dữ liệu sẵn sàng. | I/O theo mô hình Reactor/WebFlux với thư viện tương thích. |

Một lời gọi `@Async` thường **chuyển công việc chặn sang luồng khác**; bản thân I/O trong công việc ấy chưa trở thành non-blocking. Ngược lại, một pipeline WebFlux non-blocking không mặc nhiên chạy nhiều phép tính CPU song song.

## 2. Spring cần concurrency để làm gì?

### Giữ hệ thống phản hồi tốt khi có thao tác chờ

Giả sử một API tổng hợp thông tin sản phẩm phải hỏi dịch vụ giá (khoảng 400 ms) và tồn kho (khoảng 600 ms). Nếu hai yêu cầu **độc lập** và gọi nối tiếp, thời gian chờ phần I/O xấp xỉ 1.000 ms. Nếu khởi chạy đồng thời và chỉ ghép kết quả sau khi cả hai hoàn thành, phần chờ lý tưởng có thể gần 600 ms cộng chi phí điều phối. Đây là minh họa, không phải cam kết về latency: giới hạn connection, thread pool, downstream, hàng đợi và lỗi đều có thể thay đổi kết quả.

Để hưởng lợi, phải có công việc độc lập đủ lớn để chồng lấp thời gian chờ. Gọi `@Async` rồi lập tức `join()` ngay tại cùng một điểm thường chỉ thêm chi phí chuyển luồng mà không tạo ra mức chồng lấp hữu ích.

### Tách công việc không nằm trên đường phản hồi chính

Ví dụ: sau khi xử lý yêu cầu, ứng dụng có thể khởi động việc tạo thumbnail, gửi thông báo không trọng yếu hoặc chuẩn bị bản tổng hợp. Người dùng không nhất thiết cần đợi các công việc này trước khi nhận phản hồi.

Điều kiện là nghiệp vụ **cho phép hoàn thành sau** và có cách quan sát/lấy lại lỗi. Đẩy việc sang thread nền trong cùng tiến trình không bảo đảm tác vụ còn tồn tại khi ứng dụng khởi động lại. Với việc bắt buộc giao thành công, nên dùng outbox/queue/job bền vững thay vì coi `@Async void` là cơ chế giao hàng tin cậy.

### Tăng khả năng phục vụ nhiều request chờ I/O

Khi request thường phải đợi database hoặc mạng, một luồng có thể rảnh CPU nhưng vẫn bị giữ vì blocking. Thread pool giúp giới hạn và phân phối công việc; các API async của Spring MVC hoặc I/O non-blocking có thể giúp dùng luồng hiệu quả hơn trong các mô hình phù hợp. **Throughput**, **latency** và **mức sử dụng tài nguyên** là các mục tiêu khác nhau: tăng số luồng quá nhiều có thể làm hệ thống chậm, tốn bộ nhớ và gây quá tải downstream.

### Điều phối công việc theo thời điểm

Nhu cầu quét dữ liệu định kỳ, gia hạn token, dọn cache hoặc tổng hợp báo cáo hàng đêm cần cơ chế lập lịch. Trong Spring, đây là nhóm `@Scheduled`/`TaskScheduler`. Lập lịch quyết định *khi nào* công việc được kích hoạt; điều khiển đồng thời quyết định *có bao nhiêu* công việc chạy và *chạy ở đâu*.

## 3. Tình huống sử dụng điển hình

| Tình huống | Cách tiếp cận phù hợp | Điều cần kiểm soát |
| --- | --- | --- |
| Ghép kết quả từ hai API độc lập trong một request | `CompletableFuture`/executor có giới hạn; trả future cho Spring MVC nếu cần | Timeout của từng API, lỗi từng nhánh, số kết nối ra ngoài |
| Gửi email thông báo không trọng yếu sau khi phản hồi | `@Async` với executor riêng nếu chấp nhận best-effort | Retry, theo dõi thất bại; chuyển sang queue nếu phải giao chắc chắn |
| Tạo báo cáo/export có thể mất vài phút | Job nền có trạng thái, idempotency; queue/job store nếu cần sống qua restart | Tiến độ, hủy, retry, dung lượng bộ nhớ |
| Xử lý file, tạo thumbnail hoặc gọi API chậm | Executor riêng cho I/O, giới hạn số công việc | I/O, băng thông, giới hạn từ dịch vụ phụ thuộc |
| Đồng bộ dữ liệu, dọn dẹp theo lịch | `@Scheduled` và cơ chế chống trùng phù hợp | Nhiều instance có thể cùng chạy; tránh chạy chồng |
| Tác vụ CPU nặng, có thể chia độc lập | Worker pool giới hạn theo năng lực CPU, hoặc hệ xử lý batch | Tranh CPU, memory pressure, không nhân thread vô hạn |
| Lưu lượng lớn với chuỗi I/O non-blocking tương thích | Spring WebFlux/Reactor (hoặc mô hình async phù hợp) | Không chặn event loop; backpressure, observability |

Không nên tạo tác vụ nền cho thao tác rất ngắn, có tính thứ tự bắt buộc hoặc cần giao dịch nguyên tử với request hiện tại chỉ vì muốn “dùng đa luồng”. Cũng không nên tự tạo `new Thread(...)` cho mỗi request để né cấu hình năng lực hệ thống.

## 4. Các công cụ liên quan trong Spring

- **`TaskExecutor` / `AsyncTaskExecutor`:** abstraction của Spring để gửi `Runnable`/`Callable` vào cơ chế thực thi; implementation có thể là pool, luồng riêng hoặc thậm chí chạy đồng bộ. Không phải executor nào cũng có pool.
- **`ThreadPoolTaskExecutor`:** executor có `corePoolSize`, `maxPoolSize` và `queueCapacity`; phù hợp để giới hạn số tác vụ chạy/đợi trong ứng dụng. Hàng đợi lớn có thể che giấu tình trạng nghẽn và làm tăng latency.
- **`@EnableAsync` + `@Async`:** bật interception của Spring và chuyển lời gọi phương thức qua executor khi phương thức được gọi **qua Spring proxy**. Thường dùng ở service được inject vào bean khác; có thể chỉ định executor bằng `@Async("ioExecutor")`.
- **`CompletableFuture<T>`:** đại diện kết quả sẽ có sau, cho phép ghép, xử lý lỗi và đợi kết quả. `thenApply`, `thenCombine` và `allOf` giúp mô tả quan hệ giữa các tác vụ; luôn cân nhắc thread thực thi và ngoại lệ.
- **`@Scheduled` + `TaskScheduler`:** dùng cho công việc định kỳ/đến hạn. `@Scheduled` không có nghĩa là tự động có persistence hay khóa phân tán.
- **Spring MVC async:** controller có thể trả `Callable`, `DeferredResult`, `CompletableFuture` hoặc kiểu async hỗ trợ để trả lại servlet request thread trong lúc chờ kết quả, tùy adapter và cấu hình. HTTP response vẫn chỉ hoàn tất khi có dữ liệu.
- **Spring WebFlux/Reactor:** mô hình reactive/non-blocking, phù hợp khi cả chuỗi thao tác hỗ trợ non-blocking. Không mang JDBC chặn trực tiếp lên event-loop thread.
- **Message broker/job system:** phù hợp cho hàng đợi bền vững, retry và xử lý kéo dài qua vòng đời process. Đây là mô hình kiến trúc khác với việc gửi việc vào pool trong JVM.

**Phân biệt ba đường đi:**

```text
Đồng bộ:    request thread -> gọi tác vụ -> chờ -> nhận kết quả -> phản hồi
@Async:     caller -> Spring proxy -> executor -> worker chạy tác vụ
                                      -> Future hoàn thành hoặc lỗi
MVC async:  request thread -> trả Future/async handle -> giải phóng request thread
                                                    -> kết quả -> hoàn tất HTTP
```

Không có cơ chế nào trong ba cơ chế này tự làm một truy vấn SQL blocking trở thành truy vấn non-blocking. Sự khác biệt nằm ở thread nào chờ và thread nào được giải phóng.

## 5. Ví dụ có thể chạy: API Spring MVC thực hiện công việc I/O mô phỏng qua `@Async`

Ví dụ dùng Java 17 và Spring Boot 3.5.7 (Spring Framework 6.x). Đây là project Maven độc lập với hai tệp, đủ để chạy một endpoint. Nó mô phỏng một lần gọi I/O chậm bằng `Thread.sleep(300)` để quan sát ranh giới bất đồng bộ; mã sản xuất nên thay bằng client thật có timeout.

**Tệp `pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.7</version>
        <relativePath/>
    </parent>
    <groupId>example</groupId>
    <artifactId>spring-concurrency-demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
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

**Tệp `src/main/java/example/demo/ConcurrencyDemoApplication.java`:**

```java
package example.demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableAsync
public class ConcurrencyDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConcurrencyDemoApplication.class, args);
    }

    @Bean("ioExecutor")
    public ThreadPoolTaskExecutor ioExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("io-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}

@Service
class QuoteService {
    @Async("ioExecutor")
    public CompletableFuture<String> getQuote(String productId) {
        try {
            Thread.sleep(300); // Chỉ mô phỏng I/O blocking.
            return CompletableFuture.completedFuture("Báo giá cho " + productId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(ex);
        }
    }
}

@RestController
class QuoteController {
    private final QuoteService quoteService;

    QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping(value = "/quotes/{id}", produces = "text/plain")
    public CompletableFuture<ResponseEntity<String>> quote(@PathVariable String id) {
        return quoteService.getQuote(id).thenApply(ResponseEntity::ok);
    }
}
```

Đặt cấu trúc file đúng như trên. Tại thư mục có `pom.xml`, dùng JDK 17+ và Maven:

```bash
mvn spring-boot:run
```

Ở terminal khác:

```bash
curl -s http://localhost:8080/quotes/A12
```

**Nội dung HTTP response body:**

```text
Báo giá cho A12
```

Thứ tự log và tên các luồng khác nhau giữa các lần chạy, nên ví dụ không khẳng định một trình tự stdout cố định. `Thread.sleep(300)` mô phỏng khoảng chờ chứ không chứng minh hiệu năng khi có tải.

**Cơ chế thực thi:**

1. `QuoteController` nhận request; `quoteService` là một bean riêng nên lời gọi `getQuote` đi qua proxy do `@EnableAsync` thiết lập.
2. Spring gửi phương thức `@Async("ioExecutor")` tới `ThreadPoolTaskExecutor`; worker có thể chạy thao tác chờ 300 ms mà không giữ request thread ở lời gọi đó.
3. Controller trả `CompletableFuture<ResponseEntity<String>>`; Spring MVC hỗ trợ async response và hoàn tất HTTP khi future có kết quả. Client vẫn đợi response; lợi ích trước hết là điều phối thread trong server.
4. Vì executor có số worker và hàng đợi hữu hạn, khi quá tải có thể xảy ra từ chối tác vụ. Chính sách `AbortPolicy` chỉ làm lỗi được bộc lộ, chưa phải cơ chế phản hồi 503/retry hoàn chỉnh cho production.

Ví dụ chỉ minh họa **một** cuộc gọi. Không thể kết luận endpoint này giảm tổng thời gian của một lần I/O; muốn giảm latency do chờ nối tiếp, cần có ít nhất hai nhánh công việc *độc lập* và khởi chạy chúng trước lúc ghép kết quả.

## 6. Quyết định thực tế: khi nào nên và không nên chạy đồng thời?

### Nên cân nhắc

1. Có nhiều tác vụ I/O độc lập và đo được phần thời gian đang chờ; concurrency có thể chồng lấp phần chờ đó.
2. Có tác vụ phụ mà kết quả HTTP không bắt buộc đợi; nghiệp vụ cho phép xử lý sau và có phương án lỗi rõ ràng.
3. Có nhóm công việc batch đủ lớn, độc lập, có giới hạn tài nguyên và chiến lược gộp kết quả.
4. Cần xử lý nhiều event/request nhưng phải tránh để một tác vụ chậm chiếm toàn bộ năng lực phục vụ.

### Không nên áp dụng máy móc

- Tác vụ rất nhỏ, kết quả phụ thuộc tuần tự vào nhau, hoặc caller lập tức đợi `join()`; overhead có thể vượt lợi ích.
- Công việc phải cam kết cùng giao dịch với thao tác chính: chuyển sang luồng khác có thể phá giả định về thứ tự, context và atomicity.
- Công việc có trạng thái bắt buộc phải tồn tại sau khi JVM tắt: chỉ `@Async` trong bộ nhớ là không đủ.
- Tác vụ CPU-bound đang bão hòa CPU: tăng thread không tạo thêm lõi và có thể tăng context switching.
- Code blocking chạy trên event loop WebFlux: các request khác có thể bị ảnh hưởng. Dùng thư viện non-blocking hoặc chuyển công việc blocking sang scheduler chuyên dụng có giới hạn, tùy kiến trúc.
- Tác vụ “fire-and-forget” không có log, metric, timeout, retry hoặc người chịu trách nhiệm xử lý khi thất bại.

## 7. Các giới hạn và lỗi phổ biến cần biết ngay từ đầu

### Giới hạn pool, queue, tài nguyên downstream

Executor không có năng lực vô tận. Kích thước pool, hàng đợi và số kết nối HTTP/DB quyết định hệ thống có thể hấp thụ bao nhiêu công việc. Với `ThreadPoolTaskExecutor`, một hàng đợi còn chỗ thường được dùng trước khi pool tăng lên `maxPoolSize`; cấu hình queue quá lớn có thể khiến request chờ lâu mà số worker không tăng như mong đợi.

Chọn giới hạn theo phép đo: tốc độ đến của task, thời gian xử lý, CPU, bộ nhớ, khả năng dịch vụ phụ thuộc, timeout và SLO. Có metric về số active threads, số task đang đợi, số từ chối, latency và tỉ lệ lỗi. Khi quá tải, phải có chính sách rõ: từ chối sớm, trả trạng thái phù hợp, giảm tải, điều tiết hoặc chuyển sang queue bền vững.

### Dữ liệu dùng chung và tính đúng đắn

Spring bean `@Service`/`@Component` thường là singleton; nhiều request/worker có thể đồng thời gọi cùng bean. Không đặt trạng thái thay đổi theo request vào field chia sẻ nếu chưa có thiết kế đồng bộ đúng. Ưu tiên biến cục bộ, dữ liệu immutable, lớp thread-safe; khi dùng lock/concurrent collection/transaction phải xác định ranh giới bảo vệ thực sự.

Một `HashMap` thường không an toàn khi nhiều thread cùng ghi. Ngay cả `ConcurrentHashMap` cũng không tự làm an toàn chuỗi thao tác “đọc rồi ghi” nhiều bước nếu các bước cần tính nguyên tử.

### Proxy, lời gọi nội bộ và kiểu trả về

- `@Async` mặc định hoạt động qua Spring AOP proxy. Gọi `this.doAsync()` từ chính bean đó không đi qua proxy, nên thường chạy đồng bộ. Nên tách phương thức sang service khác hoặc sử dụng cơ chế interception phù hợp.
- Method `@Async` nên được gọi qua Spring-managed bean và có chữ ký hợp lệ; các kiểu trả về thường dùng là `void`, `Future<T>` hoặc `CompletableFuture<T>`. Ví dụ dùng `CompletableFuture<T>` để xử lý kết quả/lỗi.
- Nếu dùng `@Async void`, caller không có future để nhận exception. Cần quan sát lỗi và có thể cấu hình `AsyncUncaughtExceptionHandler`; với future, xử lý exception ở phía nhận kết quả.
- Khi gọi `get()` hoặc `join()`, luồng gọi sẽ chặn đến khi kết quả sẵn sàng. Không gọi chặn ngay nếu mục tiêu là chồng lấp tác vụ.

### Giao dịch, SecurityContext và trace

`@Transactional` truyền thống dựa trên ngữ cảnh gắn với thread. Một tác vụ `@Async` chạy trên thread khác **không tự động kế thừa** transaction của thread gọi; cần thiết kế giao dịch riêng ở nơi xử lý, và không truyền entity lazy chưa tải ra ngoài ranh giới persistence context. `ThreadLocal`, MDC/log trace và `SecurityContext` cũng không nên được giả định là tự lan truyền; nếu cần, phải dùng cơ chế propagation được thiết kế và kiểm thử phù hợp, tránh rò rỉ context giữa task.

### Timeout, hủy, shutdown và consistency

Một future chưa hoàn tất có thể treo luồng đợi nếu không có timeout thực tế ở HTTP/DB/client. Hủy future không bảo đảm thư viện downstream dừng công việc; phải kiểm soát khả năng interruption. Khi ứng dụng shutdown, tác vụ còn trong bộ nhớ có thể mất; kiểm thử chính sách drain/shutdown, retry và idempotency. Nếu phát sự kiện sau khi ghi dữ liệu, cân nhắc phát sau commit hoặc outbox để tránh người nhận nhìn thấy dữ liệu chưa commit.

### Đa instance và lịch định kỳ

Một ứng dụng có ba instance có thể chạy tác vụ `@Scheduled` trên cả ba. Nếu nghiệp vụ yêu cầu “chỉ một lần trên toàn cụm”, cần lock/job coordinator hay hệ thống lập lịch phân tán. Không thể suy ra tính duy nhất từ annotation.

### Virtual threads không bỏ qua giới hạn công suất

Trên Java 21+, virtual threads có thể giảm chi phí sử dụng thread cho nhiều công việc blocking phù hợp; Spring có khả năng tích hợp tùy phiên bản/cấu hình. Nhưng chúng không làm API downstream nhanh hơn, không tăng kết nối DB vô hạn và không thay thế timeout, rate limit, backpressure. Với CPU-bound, virtual thread cũng không tạo thêm CPU.

## 8. Chọn phương án bằng các câu hỏi

Trước khi đưa concurrency vào thiết kế, hãy trả lời:

1. **Độc lập ở đâu?** Có thể chạy các nhánh trước khi cần kết quả của nhau không? Có ràng buộc thứ tự/transaction không?
2. **Đang chờ cái gì?** CPU, mạng, database, file hay khóa? Số liệu latency/throughput trước khi thay đổi là bao nhiêu?
3. **Ai đợi kết quả?** Client phải nhận ngay, nhận sau qua job ID, hay không cần kết quả? Công việc được phép mất khi process chết không?
4. **Giới hạn ở đâu?** Pool, queue, DB connections, downstream quota, memory, số instance?
5. **Lỗi xử lý ra sao?** Timeout, trả lỗi, retry có idempotency, bỏ qua có giám sát hay đẩy vào queue?
6. **Đo điều gì sau thay đổi?** P95/P99 latency, throughput, error rate, queue delay, saturation và mức sử dụng CPU/heap.

**Ba tình huống tự kiểm tra:**

- *Một API gọi hai service độc lập, mỗi service mất vài trăm ms:* có thể thử gọi đồng thời và ghép kết quả sau; giới hạn số request ra ngoài và xác định timeout/partial failure.
- *Đơn hàng đã ghi DB, cần bảo đảm phát sự kiện cho hệ thống kho dù ứng dụng restart:* chỉ `@Async` không đủ; cân nhắc transactional outbox và broker/job worker, có retry/idempotency.
- *Một phép tính mã hóa tốn CPU trên máy 4 lõi, thêm 100 worker có giúp nhanh gấp 25 lần?* Không. Cần đo và giới hạn concurrency theo CPU, chi phí chia việc, bộ nhớ.

## 9. Tài liệu đối chiếu

- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `TaskExecutor`, `@Async`, `@Scheduled`, proxy và exception.
- [Spring Boot – Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html): executor mặc định và cấu hình tích hợp theo phiên bản Boot.
- [Spring Framework – WebFlux Overview](https://docs.spring.io/spring-framework/reference/web/webflux.html): cách tiếp cận reactive/non-blocking.
- [Spring Framework – WebFlux Concurrency Model](https://docs.spring.io/spring-framework/reference/web/webflux/new-framework.html): khác biệt blocking/thread model giữa MVC và WebFlux.

Concurrency trong Spring có ý nghĩa khi nó giải quyết một điểm chờ hoặc nhu cầu điều phối thật sự. Chọn mô hình theo **độ độc lập của công việc, yêu cầu độ tin cậy và giới hạn tài nguyên**, rồi kiểm chứng bằng số liệu và xử lý lỗi, thay vì mặc định chuyển mọi phương thức sang `@Async`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-002">Phân biệt đồng bộ, bất đồng bộ, song song và lập lịch</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt đồng bộ, bất đồng bộ, song song và lập lịch trong Spring Framework

Bốn thuật ngữ **đồng bộ (synchronous)**, **bất đồng bộ (asynchronous)**, **song song (parallel)** và **lập lịch (scheduling)** thường xuất hiện cùng nhau khi xây dựng ứng dụng Spring, nhưng chúng trả lời **bốn câu hỏi khác nhau**. Nhầm lẫn chúng dễ dẫn đến thiết kế API gây chờ đợi không cần thiết, cấu hình thread pool sai, công việc chạy trùng hoặc giả định một tác vụ đã hoàn thành khi nó mới được đưa vào hàng đợi.

Sau bài này, bạn cần phân biệt được hành vi của bên gọi, cách công việc được thực thi, thời điểm kích hoạt và các thành phần Spring phù hợp; đồng thời nhận ra khi nào các cơ chế có thể kết hợp với nhau.

## 1. Bản chất của từng khái niệm

### 1.1. Đồng bộ: bên gọi chờ kết quả

Một lời gọi **đồng bộ** chỉ hoàn thành đối với bên gọi khi thao tác đã trả về kết quả hoặc ném lỗi. Trong thời gian đó, luồng xử lý của bên gọi chưa thể chuyển qua câu lệnh kế tiếp. Ví dụ:

```java
String report = reportService.generate("R1"); // Chờ generate hoàn thành
sendResponse(report);                         // Chỉ chạy sau khi có report
```

Hãy tưởng tượng trình tự:

```text
Caller:  gọi generate ───────────── chờ ───────────── nhận kết quả → tiếp tục
Công việc:              bắt đầu → xử lý → kết thúc
```

Điểm cốt lõi là **quan hệ chờ giữa lời gọi và kết quả**, không phải số lượng thread. Một phương thức có thể giao việc sang thread khác nhưng gọi `Future.get()` hoặc `CompletableFuture.join()` ngay lập tức; về trải nghiệm của bên gọi, đoạn mã đó vẫn chờ đồng bộ.

### 1.2. Bất đồng bộ: bên gọi tiếp tục trước khi công việc xong

Một thao tác **bất đồng bộ** cho phép bên gọi nhận lại quyền điều khiển trước khi công việc kết thúc. Kết quả đến sau qua `CompletableFuture`, callback, sự kiện, hàng đợi hoặc một cơ chế thông báo khác.

```java
CompletableFuture<String> result = reportService.generateAsync("R1");
doOtherWork();                  // Có thể chạy khi generateAsync còn xử lý
String report = result.join();  // Chỉ đến đây mới chờ nếu kết quả chưa sẵn sàng
```

```text
Caller:   gọi → nhận Future → làm việc khác ── join/đọc kết quả → tiếp tục
Worker:          nhận task ── thực thi ── hoàn thành Future
```

Bất đồng bộ **không đồng nghĩa** với: chạy nhanh hơn, luôn có thread mới, không dùng thread, không bao giờ chờ, hoặc công việc chắc chắn chạy song song. Một Future có thể hoàn thành ngay; bên gọi cũng có thể tự làm mất lợi ích bất đồng bộ bằng cách `join()` ngay sau lúc gửi việc.

Trong Spring, `@Async` thường giao phương thức cho một `TaskExecutor`, giúp thread gọi phương thức không cần trực tiếp làm toàn bộ công việc. Tuy nhiên, thread worker vẫn có thể bị chặn bởi I/O đồng bộ bên trong.

### 1.3. Song song: nhiều công việc thực sự chạy cùng lúc

**Song song** mô tả **cách thực thi**: hai hay nhiều phần việc cùng tiến triển tại một thời điểm trên các tài nguyên xử lý khác nhau (thường là nhiều lõi CPU). Mục tiêu phổ biến là giảm thời gian xử lý tổng của công việc có thể chia nhỏ.

Phân biệt thêm với **concurrency (tính đồng thời)**:

- **Concurrency:** nhiều tác vụ đang được quản lý trong cùng khoảng thời gian; trên một lõi CPU, chúng có thể xen kẽ từng lượt chạy mà không thực sự chạy đồng thời tại từng khoảnh khắc.
- **Parallelism:** thực thi thực sự trùng thời điểm trên nhiều tài nguyên xử lý.

```text
Một lõi, chạy xen kẽ (concurrent):
Thời gian →  [A][B][A][B][A][B]

Hai lõi, chạy song song (parallel):
Lõi 1     →  [A][A][A][A]
Lõi 2     →  [B][B][B][B]
```

Ví dụ `list.parallelStream().map(...).toList()` có thể chia xử lý sang nhiều worker, nhưng lệnh `toList()` chỉ trả về khi xử lý cần thiết đã xong; tức là **bên trong có thể song song, còn bên gọi vẫn chờ đồng bộ**. Ngược lại, một executor chỉ có một worker vẫn có thể phục vụ API bất đồng bộ và xếp các task tuần tự.

Song song không tự bảo đảm tăng tốc: chi phí chia/gộp việc, tranh chấp khóa, I/O, giới hạn CPU và dữ liệu dùng chung có thể khiến kết quả chậm hơn hoặc sai.

### 1.4. Lập lịch: quyết định lúc nào kích hoạt

**Lập lịch** mô tả **thời điểm** một công việc được kích hoạt: vào 08:00 mỗi ngày làm việc, sau 5 giây, hoặc cứ một chu kỳ nhất định. Trong Spring, `@Scheduled` và `TaskScheduler` là các công cụ tiêu biểu.

```java
@Scheduled(fixedDelay = 5_000)
public void refreshCache() {
    refresh();
}
```

Lập lịch không xác định sẵn công việc phải chạy đồng bộ hay bất đồng bộ, trên một hay nhiều thread. Một job được scheduler gọi có thể tự xử lý trên thread lập lịch, hoặc chuyển tiếp sang executor rồi trở về. **Scheduler trả lời “khi nào?”; executor trả lời “thực thi ở đâu và bằng những worker nào?”**

## 2. Đừng nhầm với blocking/non-blocking

**Blocking** và **non-blocking** là một trục riêng: thread có phải chờ tài nguyên hay kết quả hay không.

- **Blocking I/O:** thread đứng chờ thao tác mạng, tệp hoặc cơ sở dữ liệu; gọi qua `@Async` chỉ chuyển việc chờ đó sang worker.
- **Non-blocking I/O:** thao tác không giữ thread để đợi kết quả I/O; kết quả được xử lý khi dữ liệu sẵn sàng.
- **Bất đồng bộ:** bên gọi có thể tiếp tục trước khi thao tác hoàn thành, dù thao tác bên dưới dùng blocking hay non-blocking I/O.

Ví dụ, Spring MVC có thể trả về `CompletableFuture` để xử lý HTTP bất đồng bộ nhưng tác vụ bên trong vẫn dùng JDBC blocking. Spring WebFlux có thể dùng pipeline reactive/non-blocking khi mọi thành phần liên quan hỗ trợ; chỉ bọc một lệnh JDBC blocking trong `Mono` **không biến JDBC thành non-blocking**. Không đặt lời gọi blocking lên event-loop của WebFlux.

## 3. Bảng so sánh nhanh

| Khái niệm | Câu hỏi chính | Quan sát từ bên gọi/hệ thống | Công cụ thường gặp |
| --- | --- | --- | --- |
| Đồng bộ | Khi nào lời gọi trả về? | Bên gọi chờ thao tác hoàn thành | Lời gọi Java trực tiếp, `get()`/`join()` |
| Bất đồng bộ | Có trả quyền điều khiển sớm không? | Kết quả/ lỗi đến sau; caller có thể làm việc khác | Spring `@Async`, `TaskExecutor`, `CompletableFuture` |
| Song song | Có nhiều việc chạy thực sự đồng thời không? | Nhiều worker/lõi xử lý các phần việc trùng thời điểm | Thread pool nhiều worker, `ForkJoinPool`, parallel stream |
| Lập lịch | Khi nào công việc được kích hoạt? | Có trigger theo thời gian/chu kỳ | `@Scheduled`, `TaskScheduler`, cron |

**Những tổ hợp hoàn toàn hợp lệ:**

1. **Đồng bộ và tuần tự:** gọi dịch vụ, nhận kết quả, rồi mới gọi dịch vụ tiếp theo.
2. **Bất đồng bộ nhưng các job xếp hàng tuần tự:** gửi nhiều task vào executor có một worker.
3. **Song song bên trong một lời gọi đồng bộ:** chia việc cho nhiều worker rồi `join()` trước khi trả kết quả.
4. **Lập lịch và xử lý tuần tự:** một scheduler kích hoạt job, job chạy xong mới tới lượt sau.
5. **Lập lịch kết hợp bất đồng bộ:** scheduler chỉ gửi task vào executor; worker xử lý sau và có thể chồng lấn với lần kích hoạt tiếp theo.

Do đó, phát biểu “đã dùng `@Scheduled` thì tự động đa luồng” hoặc “có `@Async` thì chắc chắn tận dụng nhiều lõi CPU” đều sai.

## 4. Ví dụ Java 17+ độc lập: hiểu sự khác nhau từ phía caller

Lưu đoạn sau thành **CallVsExecution.java**:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CallVsExecution {
    private static String work(String name) {
        return "done " + name;
    }

    public static void main(String[] args) {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            String first = work("A"); // Đồng bộ
            System.out.println(first);

            CompletableFuture<String> second =
                    CompletableFuture.supplyAsync(() -> work("B"), worker);

            System.out.println("Caller continues");
            System.out.println(second.join()); // Chờ nếu B chưa xong
        } finally {
            worker.shutdown();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac CallVsExecution.java
java CallVsExecution
```

**Stdout chính xác** (chỉ thread chính in ra màn hình):

```text
done A
Caller continues
done B
```

Ở bước A, caller nhận kết quả trước khi tiếp tục. Ở bước B, caller nhận Future rồi in dòng tiếp theo; việc B có thể hoàn thành trước hoặc sau dòng “Caller continues”, nhưng thứ tự **in kết quả** là xác định nhờ `join()`. Một worker duy nhất xử lý các task được gửi vào executor theo lượt; API bất đồng bộ không yêu cầu nhiều worker. Không suy luận thứ tự thực thi thật chỉ từ thời điểm in log.

## 5. Các thành phần Spring liên quan

### 5.1. `@Async` và `TaskExecutor`: gửi việc chạy sau

Spring cung cấp `TaskExecutor` để thực thi `Runnable`/task; `ThreadPoolTaskExecutor` là một implementation quản lý nhóm thread. Khi bật `@EnableAsync`, Spring có thể tạo proxy để chặn lời gọi tới phương thức `@Async` và gửi công việc sang executor đã chọn.

Luồng ý tưởng:

```text
HTTP/service caller
      │ gọi qua Spring proxy
      ▼
@Service @Async("jobExecutor") ── submit ──► hàng đợi executor ──► worker
      │                                                     │
      └── trả Future sớm                                    └── hoàn thành Future
```

Lưu ý quan trọng: **phải gọi qua Spring proxy**. Một phương thức trong cùng bean gọi `this.asyncMethod()` sẽ không đi qua proxy ở chế độ mặc định, nên annotation có thể không hoạt động như mong muốn. Tương tự, không nên đặt kỳ vọng `@Async` hoạt động trên lời gọi trực tiếp vào đối tượng được tạo bằng `new`, hoặc trên phương thức `private`.

`@Async` thường dùng với kiểu trả về `void`, `Future` hoặc `CompletableFuture`. Để nhận lỗi và giá trị theo cách kiểm soát được, ưu tiên Future/CompletableFuture; nếu dùng `void`, cần chú ý cơ chế xử lý ngoại lệ bất đồng bộ.

### 5.2. `@Scheduled` và `TaskScheduler`: phát trigger theo thời gian

Spring hỗ trợ khai báo lịch bằng `@EnableScheduling` và `@Scheduled`. Method thông thường không nhận tham số; lịch được quyết định bởi cấu hình annotation, không phải lời gọi nghiệp vụ từ một controller.

- `fixedDelay = 5_000`: đợi 5 giây **sau khi lần gọi phương thức được lập lịch trước kết thúc** rồi mới kích hoạt lần kế tiếp.
- `fixedRate = 5_000`: nhắm tới chu kỳ 5 giây tính theo **mốc bắt đầu** của các lượt; khi job chạy lâu, khả năng đúng giờ hoặc chồng lấn phụ thuộc scheduler, executor và cách triển khai, không được tự giả định chạy song song.
- `initialDelay = 2_000`: đợi ban đầu 2 giây trước lần kích hoạt đầu tiên trong lịch định kỳ tương ứng.
- `cron = "0 0 8 * * MON-FRI", zone = "Asia/Ho_Chi_Minh"`: kích hoạt lúc 08:00 các ngày thứ Hai đến thứ Sáu theo múi giờ chỉ định. Cron của Spring thông dụng gồm **6 trường**: giây, phút, giờ, ngày trong tháng, tháng, ngày trong tuần.

Nếu một job trực tiếp gửi `@Async` rồi return, `fixedDelay` đo khoảng cách từ **lúc phương thức scheduled kết thúc việc gửi**, chứ không đo thời điểm task bất đồng bộ hoàn thành. Các lượt nền vì thế có thể chồng nhau khi xử lý lâu.

Ví dụ trong điều kiện lý tưởng, job chạy 2 giây và khoảng lặp cấu hình là 5 giây:

```text
fixedRate:   bắt đầu tại khoảng t = 0s, 5s, 10s, 15s...
fixedDelay:  bắt đầu tại khoảng t = 0s, 7s, 14s, 21s...
```

Đây là minh họa về mốc thời gian, không phải cam kết job luôn chạy đúng mili giây. Thread bận, dừng ứng dụng, GC pause hoặc giới hạn tài nguyên đều có thể làm trễ việc kích hoạt.

## 6. Ví dụ Spring Boot hoàn chỉnh: kết hợp ba cơ chế

Ví dụ dùng **Java 17+, Spring Boot 3.x/Spring Framework 6.x** với dependency `spring-boot-starter-web` và plugin chạy ứng dụng của Spring Boot. Đặt toàn bộ mã dưới đây trong **src/main/java/com/example/demo/ModesApplication.java**. Đây là ứng dụng minh họa độc lập về mặt mã nguồn, nhưng cần Spring Boot dependencies để biên dịch/chạy; lệnh `javac` thuần không đủ.

```java
package com.example.demo;

import java.util.concurrent.CompletableFuture;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class ModesApplication {
    public static void main(String[] args) {
        SpringApplication.run(ModesApplication.class, args);
    }

    @Bean("jobExecutor")
    ThreadPoolTaskExecutor jobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("job-");
        executor.initialize();
        return executor;
    }

    @Bean("taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("schedule-");
        scheduler.initialize();
        return scheduler;
    }
}

@Service
class ReportService {
    public String createSync(String id) {
        return slowWork(id);
    }

    @Async("jobExecutor")
    public CompletableFuture<String> createAsync(String id) {
        return CompletableFuture.completedFuture(slowWork(id));
    }

    private String slowWork(String id) {
        try {
            Thread.sleep(800); // Chỉ mô phỏng I/O blocking, không phải giải pháp production
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Work interrupted", e);
        }
        return "done:" + id;
    }
}

@RestController
class ReportController {
    private final ReportService service;

    ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/sync/{id}")
    public String sync(@PathVariable String id) {
        return service.createSync(id);
    }

    @GetMapping("/async/{id}")
    public CompletableFuture<String> async(@PathVariable String id) {
        return service.createAsync(id);
    }
}

@Component
class ReportTicker {
    private final ReportService service;

    ReportTicker(ReportService service) {
        this.service = service;
    }

    @Scheduled(initialDelay = 3_000, fixedDelay = 10_000)
    public void tick() {
        service.createAsync("periodic").whenComplete((result, error) -> {
            if (error != null) {
                System.err.println("Scheduled work failed: " + error.getMessage());
            } else {
                System.out.println("Scheduled work result: " + result);
            }
        });
    }
}
```

Trong một dự án Spring Boot đã cấu hình Gradle, chạy `./gradlew bootRun` (Windows: `gradlew.bat bootRun`), rồi gọi:

```bash
curl -s http://localhost:8080/sync/A
curl -s http://localhost:8080/async/B
```

**Body phản hồi tương ứng:**

```text
done:A
done:B
```

Mỗi lệnh `curl` chỉ trả về khi HTTP response hoàn tất. Hai body giống dạng nhau, nhưng phía server xử lý khác:

1. `/sync/A`: request thread trực tiếp chạy `slowWork` và chờ khoảng 800 ms.
2. `/async/B`: controller trả `CompletableFuture` cho Spring MVC; phần `slowWork` diễn ra trên thread `job-*`, và Spring MVC hoàn tất response khi Future hoàn thành. Điều này **không có nghĩa client HTTP nhận kết quả ngay**; client vẫn chờ nội dung của response.
3. `ReportTicker.tick()`: scheduler gọi phương thức theo `fixedDelay`; phương thức giao việc cho `jobExecutor`, đăng ký callback quan sát kết quả rồi trả về. Scheduler và executor là **hai vai trò riêng**.
4. `createAsync` chỉ được gọi từ các bean khác (`ReportController`, `ReportTicker`), nên lời gọi đi qua proxy `@Async`. Nếu đổi thành `this.createAsync(...)` bên trong `ReportService` thì hiệu lực khác.

Log của job và thứ tự xử lý giữa các request phụ thuộc lịch chạy thread nên **không có thứ tự stdout tổng thể cố định**. Không dùng thứ tự log ngẫu nhiên để kết luận có hoặc không có parallelism.

### Một điểm thường bị hiểu nhầm về Future

Trong thân `createAsync()`, `CompletableFuture.completedFuture(slowWork(id))` chạy `slowWork` **trước**, rồi mới tạo một future đã hoàn tất. Sự bất đồng bộ của lời gọi phía ngoài đến từ **Spring proxy + executor**. Nếu bỏ `@Async` và gọi trực tiếp dòng này, toàn bộ `slowWork` sẽ xảy ra trên caller thread trước khi Future được trả về. Chỉ thấy kiểu trả về `CompletableFuture` chưa đủ kết luận là chạy nền.

## 7. Kết hợp cơ chế theo nhu cầu thực tế

| Nhu cầu | Cách tiếp cận | Điều cần kiểm soát |
| --- | --- | --- |
| Đọc dữ liệu ngắn, phải có kết quả ngay | Lời gọi đồng bộ | Timeout, độ trễ của dependency |
| Tạo báo cáo tốn thời gian, có thể lấy sau | Gửi việc bất đồng bộ; có thể trả HTTP 202 kèm mã job và endpoint tra trạng thái | Lưu trạng thái, xử lý lỗi, retry, idempotency |
| Xử lý nhiều phần độc lập dùng CPU | Executor nhiều worker, phân chia/gộp dữ liệu có kiểm soát | Số lõi, shared state, overhead chia việc |
| Đồng bộ cache định kỳ | `@Scheduled` với `fixedDelay` hoặc cron | Lịch chạy, trễ lịch, trùng tác vụ |
| Lịch định kỳ kích hoạt xử lý I/O chậm | `@Scheduled` gọi service `@Async` qua bean khác | Queue, backlog, chồng lấn, mất lỗi |
| Job phải chạy một lần cho cả cụm nhiều instance | Scheduler có phối hợp phân tán hoặc hệ thống job/queue chuyên dụng | Ownership, khóa, deduplication, khôi phục sau sự cố |

**Không nên nhầm “trả Future” với “đã có API nền”**: một HTTP endpoint trả Future và đợi hoàn tất vẫn khiến HTTP client chờ. Muốn client gửi yêu cầu rồi tự quay lại lấy kết quả, thường cần giao thức job riêng: trả `202 Accepted` và job ID, lưu trạng thái, có endpoint tra cứu/kết quả và chính sách hết hạn/retry.

## 8. Những lỗi phổ biến và cách phòng tránh

**Lỗi 1 — Gọi `@Async` trong cùng bean.** Spring AOP mặc định chặn lời gọi qua proxy. Tách phương thức bất đồng bộ sang bean khác, inject bean rồi gọi qua đó. Đừng dùng `this` để kỳ vọng annotation được áp dụng.

**Lỗi 2 — Tạo Future rồi chờ ngay.** `supplyAsync(...).join()` tại cùng vị trí khiến caller chờ kết quả. Chỉ chuyển sang Future khi bạn thật sự có việc hữu ích để làm trước khi cần kết quả, hoặc cần kết hợp nhiều kết quả.

**Lỗi 3 — Cho rằng `@Scheduled` mặc định song song.** Việc một job có chạy chồng lần trước, và các job có được worker khác nhau phục vụ hay không, tùy scheduler, thời gian chạy và việc chuyển giao executor. Thiết kế dựa trên hành vi cấu hình cụ thể và kiểm thử dưới tải.

**Lỗi 4 — Giữ một thread pool/queue không giới hạn.** Công việc có thể đến nhanh hơn xử lý, khiến hàng đợi tích tụ, tăng bộ nhớ/độ trễ và cuối cùng bị từ chối hoặc quá tải. Với `ThreadPoolTaskExecutor`, đặt `corePoolSize`, `maxPoolSize` và `queueCapacity` phù hợp; khi dùng queue có sức chứa dương, pool thường ưu tiên xếp task vào queue trước khi tăng vượt số core thread. Theo dõi active threads, queue depth, task duration và rejection.

**Lỗi 5 — Gắn `@Async` rồi tưởng đã non-blocking.** Một lời gọi HTTP blocking/JDBC/blocking file I/O vẫn giữ worker trong thời gian đợi. Cần giới hạn đồng thời, timeout và biết rõ tài nguyên nào đang chờ. Non-blocking end-to-end yêu cầu cả pipeline phù hợp.

**Lỗi 6 — Dùng shared mutable state không an toàn.** Khi nhiều worker truy cập cùng `HashMap` hoặc biến đếm mà không có đồng bộ hóa, có thể xuất hiện race condition và dữ liệu sai. Dùng dữ liệu bất biến, chia vùng sở hữu, `ConcurrentHashMap`, `AtomicInteger` hoặc khóa khi thực sự cần; song song không thay thế thread safety.

**Lỗi 7 — Quên thread-local context và transaction.** Transaction của Spring thường gắn với thread hiện tại; một phương thức `@Async` chạy ở thread khác không tự kế thừa transaction đang chạy của caller. Tương tự, MDC logging hoặc security context không nên mặc định là được truyền sang worker. Xác định lại ranh giới transaction và truyền context có chủ đích.

**Lỗi 8 — Nuốt ngoại lệ của job nền.** Exception từ Future không được quan sát có thể trôi qua mà caller không biết; callback như `whenComplete`, `exceptionally`, metric và log giúp nhận lỗi. Với job định kỳ, bổ sung chiến lược retry/backoff có giới hạn, cảnh báo và idempotency trước khi chạy lại.

**Lỗi 9 — Triển khai nhiều replica và nghĩ cron chỉ chạy một lần.** Mỗi instance Spring bật lịch có thể tự kích hoạt cùng job. Nếu yêu cầu “một lần duy nhất toàn hệ thống”, cần cơ chế phối hợp như distributed lock, queue có consumer ownership, hoặc scheduler phân tán; `@Scheduled` đơn lẻ không bảo đảm điều đó.

**Lỗi 10 — Dựa vào lịch để bảo đảm giao việc bền vững.** Một trigger trong tiến trình có thể không chạy khi ứng dụng tắt hoặc có thể bị trễ khi hệ thống quá tải. Nếu cần lưu job, phục hồi sau restart, giữ lịch sử và retry đáng tin cậy, dùng giải pháp lưu trữ/phối hợp phù hợp thay vì chỉ một cron trong bộ nhớ.

## 9. Cách chọn giải pháp trong Spring

Trước khi thêm annotation, lần lượt trả lời:

1. **Caller có bắt buộc chờ kết quả ngay không?** Nếu có, đồng bộ thường đơn giản hơn. Nếu không, xác định Future/callback/event hoặc giao thức job.
2. **Công việc nặng CPU hay phải đợi I/O?** CPU-bound nên xét số lõi và chia việc; I/O-bound cần giới hạn số tác vụ chờ và timeout. Không tăng thread vô hạn.
3. **Có cần kích hoạt theo thời gian?** Nếu có, chọn `fixedDelay`, `fixedRate` hay cron dựa trên nghĩa nghiệp vụ; thời điểm kích hoạt độc lập với cách xử lý.
4. **Có được phép chạy chồng không?** Nếu không, cần khóa, tuần tự hóa hoặc kiểm tra trạng thái; đừng mặc nhiên trông cậy vào pool một thread trong mọi cấu hình.
5. **Có cần bảo đảm chạy lại và chạy một lần trong cụm không?** Thiết kế retry, idempotency, trạng thái lưu bền vững và phối hợp giữa các instance.
6. **Sẽ đo và xử lý sự cố như thế nào?** Quan sát độ trễ, backlog, số worker bận, job trễ, lỗi, timeout và hành vi khi shutdown.

Một quy tắc dễ nhớ là:

> **Đồng bộ/bất đồng bộ** nói về lúc caller tiếp tục. **Concurrency/song song** nói về mức công việc có thể tiến triển cùng lúc. **Lập lịch** nói về lúc bắt đầu. **Blocking/non-blocking** nói về cách thread chờ tài nguyên. Các lựa chọn này có thể kết hợp, nhưng không thay thế lẫn nhau.

## 10. Tự kiểm tra

**Câu 1.** Method có `@Async` trả `CompletableFuture`, nhưng caller gọi `join()` ngay. Đây có phải caller đang chờ không?

**Đáp án:** Có. Công việc được gửi bất đồng bộ, nhưng caller sau đó chủ động chờ đồng bộ ở `join()`.

**Câu 2.** Một `@Scheduled(fixedRate = 1_000)` có chắc tạo thread mới mỗi giây và cho các lượt chạy song song không?

**Đáp án:** Không. Cấu hình định nghĩa mục tiêu thời gian kích hoạt; thread và mức chồng lấn phụ thuộc scheduler, pool và cách job thực thi.

**Câu 3.** `parallelStream().toList()` có phải lời gọi bất đồng bộ đối với đoạn code phía sau không?

**Đáp án:** Không theo mặc định. Các phần xử lý có thể chạy song song bên trong, nhưng lời gọi terminal trả kết quả sau khi đã hoàn tất các phần cần thiết.

**Câu 4.** Nếu controller Spring MVC trả `CompletableFuture<String>`, liệu trình duyệt nhận nội dung ngay lập tức?

**Đáp án:** Không. Server có thể xử lý request bất đồng bộ, nhưng HTTP response mang kết quả chỉ hoàn tất khi Future hoàn thành. Muốn client không chờ job dài, thiết kế endpoint nhận job và tra kết quả riêng.

**Câu 5.** Có ba instance cùng chạy `@Scheduled(cron = "...")`. Có được bảo đảm chỉ một instance làm việc không?

**Đáp án:** Không. Cần cơ chế phối hợp để bảo đảm thực thi duy nhất trong cụm.

## Tài liệu tham khảo

- Spring Framework Reference — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework Reference — Asynchronous Requests trong Spring MVC: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- Java SE — `CompletableFuture`, `ExecutorService`, `ScheduledExecutorService` trong gói `java.util.concurrent`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-003">Tác vụ, luồng gọi, luồng xử lý và kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# Tác vụ, luồng gọi, luồng xử lý và kết quả trong Spring Framework

## Mục tiêu

Sau bài này, bạn có thể phân biệt **tác vụ (task)**, **luồng gọi (caller thread)**, **luồng thực thi (worker/execution thread)** và **kết quả (result)**; lần theo một lời gọi đồng bộ hoặc bất đồng bộ; nhận ra vai trò của `TaskExecutor`, `@Async` và `CompletableFuture`; đồng thời giải thích khi nào luồng gọi còn phải chờ, lỗi đi đâu và những dữ liệu nào không tự đi theo luồng mới.

Phạm vi bài là mô hình thực thi và dòng chảy kết quả trong Spring Framework. Các kỹ thuật lập lịch định kỳ, tinh chỉnh thread pool sâu và lập trình reactive chỉ được nhắc đến để phân biệt, không phải trọng tâm.

## 1. Bốn khái niệm cần tách riêng

| Thành phần | Bản chất | Câu hỏi cần trả lời |
| --- | --- | --- |
| **Task (tác vụ)** | Một đơn vị công việc có thể được thực hiện, ví dụ ghi báo cáo, gọi dịch vụ ngoài, tính toán. Trong Java thường được biểu diễn bằng `Runnable`, `Callable<T>` hoặc thân một phương thức. | *Cần làm việc gì?* |
| **Caller thread (luồng gọi)** | Luồng đang chạy mã gọi phương thức hoặc gửi task cho executor; có thể là `main`, luồng HTTP request hoặc một worker khác. | *Ai khởi phát công việc?* |
| **Execution/worker thread (luồng xử lý)** | Luồng thực sự chạy phần thân công việc sau khi cơ chế thực thi quyết định; có thể là luồng trong pool, luồng mới hoặc ngay luồng gọi nếu dùng bộ thực thi đồng bộ. | *Công việc thực sự chạy ở đâu?* |
| **Result (kết quả)** | Giá trị trả về, trạng thái hoàn tất, ngoại lệ hoặc hủy; với bất đồng bộ có thể được đại diện bởi `Future<T>`/`CompletableFuture<T>` trước khi giá trị sẵn sàng. | *Người gọi nhận gì, lúc nào và bằng cách nào?* |

**Task không đồng nghĩa với thread.** Một thread có thể chạy nhiều task nối tiếp; một executor có thể xếp task vào hàng đợi, dùng lại thread hoặc từ chối task khi quá tải. Việc tạo `CompletableFuture` cũng không tự chứng minh công việc đang chạy trên thread khác: phải xem nơi task được thực thi.

**Kết quả bất đồng bộ có hai lớp:** đối tượng đại diện cho kết quả trong tương lai được trả về trước; giá trị hoặc lỗi thực tế chỉ xuất hiện khi task hoàn thành. Một `CompletableFuture` có thể đã hoàn thành ngay lúc caller nhận được nó; không được dựa vào trạng thái “chưa hoàn thành” để xác định một lời gọi là bất đồng bộ.

## 2. Đường đi của một lời gọi

### Lời gọi đồng bộ

```text
Caller thread
  -> gọi service.process()
  -> chính caller chạy thân phương thức
  -> nhận giá trị hoặc ngoại lệ
  -> mới chạy tiếp câu lệnh kế tiếp
```

Ví dụ `String value = service.process();` chạy trực tiếp theo cách thông thường: nếu `process()` mất 500 ms, caller phải đợi nó kết thúc. Kể cả bên trong phương thức có I/O hoặc `Thread.sleep`, lời gọi vẫn là đồng bộ nếu caller chưa được trả quyền điều khiển.

### Lời gọi bất đồng bộ qua executor

```text
Caller thread            Executor / hàng đợi             Worker thread
  | submit(task)  -------------> |                            |
  | <--- Future/handle           | ---- giao task ----------> |
  | tiếp tục việc khác           |                            | chạy task
  |                              |                            | tạo value/lỗi
  | Future được hoàn tất <----------------------------------- |
  | chỉ đợi nếu gọi get()/join() hoặc có bước phụ thuộc cần chờ
```

Một trình tự thường gặp:

1. Caller tạo dữ liệu đầu vào và gửi công việc; việc gửi có thể tốn thời gian hoặc bị từ chối.
2. Executor chọn lúc và nơi chạy task theo chính sách cấu hình.
3. Caller nhận một handle, thường là `Future`/`CompletableFuture`, và *có thể* làm việc khác.
4. Worker chạy task, rồi ghi nhận giá trị hoặc ngoại lệ vào handle.
5. Caller quan sát kết quả bằng callback/composition hoặc bằng `get()`/`join()` nếu chấp nhận chờ.

**Bất đồng bộ không có nghĩa là không có chỗ nào bị chặn.** `join()` chặn thread đang gọi nó nếu kết quả chưa sẵn sàng; I/O blocking trong worker vẫn chiếm worker. Bất đồng bộ là cách tách thời điểm/địa điểm xử lý khỏi luồng gọi, còn non-blocking là đặc tính của thao tác cụ thể.

## 3. Spring đặt cơ chế thực thi ở đâu?

Spring cung cấp `TaskExecutor` như một cách trừu tượng hóa việc thực thi task. `ThreadPoolTaskExecutor` là một triển khai thường dùng với thread pool và hàng đợi; tùy executor, công việc không nhất thiết luôn chạy trên luồng khác. Không có quy tắc “mỗi task tạo một thread”.

| Cách gọi | Đường thực thi | Cách nhận kết quả |
| --- | --- | --- |
| Gọi phương thức Java thông thường | Thân phương thức chạy ngay trên caller, trừ khi nó tự chuyển việc đi. | Giá trị hoặc ngoại lệ trực tiếp. |
| `taskExecutor.execute(runnable)` | Executor nhận `Runnable`, chọn thread/thời điểm thực thi. | `execute` không trả handle chứa giá trị; phải tự tổ chức tín hiệu/kết quả nếu cần. |
| Gọi bean có `@Async` **qua Spring proxy** | Interceptor gửi lời gọi sang executor được chọn; phần thân phương thức chạy trong executor. | Thường dùng `CompletableFuture<T>`/`Future<T>` hoặc `void`; lỗi phải được quan sát đúng kênh. |
| `CompletableFuture.supplyAsync(supplier, executor)` | Java gửi `Supplier` tới executor được chỉ định. | `CompletableFuture<T>` do JDK quản lý, có thể ghép các bước xử lý. |

Đường đi điển hình của `@Async`:

```text
Bean A (caller)
  -> Spring proxy của Bean B
     -> bộ chặn @Async chọn executor
        -> gửi công việc vào executor
        -> caller nhận CompletableFuture
           -> worker chạy phương thức thật của Bean B
              -> hoàn tất kết quả hoặc lỗi
```

Để `@Async` có tác dụng theo cơ chế proxy mặc định, cần bật hỗ trợ bằng `@EnableAsync`, gọi phương thức qua bean do Spring quản lý và có executor phù hợp. `this.someAsyncMethod()` trong chính bean đó là **self-invocation**: không đi qua proxy, nên thường chạy đồng bộ trên thread hiện tại. Các phương thức không thể được proxy/intercept phù hợp cũng không cho kết quả mong đợi.

`@Async("lessonExecutor")` chọn rõ bean executor có tên `lessonExecutor`. Khi không chỉ định, Spring lựa chọn theo cấu hình async hiện hành; đừng dựa vào một tên thread hoặc executor mặc định nếu chưa kiểm tra cấu hình thực tế.

## 4. Ví dụ Spring Framework có thể chạy với Java 17+

Ví dụ độc lập này cố định Spring Framework 6.2.0 để tái lập. Nó chủ động lưu đối tượng `Thread` vào kết quả **chỉ để kiểm chứng đường thực thi**, không phải mẫu dữ liệu nên trả về từ nghiệp vụ thật.

Cấu trúc:

```text
async-flow-demo/
├── pom.xml
└── src/main/java/example/AsyncFlowDemo.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>async-flow-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.0</version>
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
                    <mainClass>example.AsyncFlowDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

`src/main/java/example/AsyncFlowDemo.java`:

```java
package example;

import java.util.concurrent.CompletableFuture;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class AsyncFlowDemo {
    public record WorkResult(String value, Thread executedBy) {}

    public static class WorkService {
        @Async("lessonExecutor")
        public CompletableFuture<WorkResult> process(String input) {
            WorkResult value = new WorkResult(
                "done:" + input,
                Thread.currentThread()
            );
            return CompletableFuture.completedFuture(value);
        }
    }

    @Configuration
    @EnableAsync
    public static class Config {
        @Bean
        public WorkService workService() {
            return new WorkService();
        }

        @Bean("lessonExecutor")
        public ThreadPoolTaskExecutor lessonExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(16);
            executor.setThreadNamePrefix("lesson-");
            return executor;
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(Config.class)) {
            WorkService service = context.getBean(WorkService.class);
            Thread caller = Thread.currentThread();

            CompletableFuture<WorkResult> handle = service.process("A");
            // Ở đây caller đã có handle; task có thể đang chạy hoặc đã xong.
            WorkResult result = handle.join();

            System.out.println("differentThread="
                + (result.executedBy() != caller));
            System.out.println("result=" + result.value());
        }
    }
}
```

Chạy từ thư mục `async-flow-demo` với JDK 17+ và Maven:

```bash
mvn -q compile exec:java
```

Hai dòng do chương trình in ra:

```text
differentThread=true
result=done:A
```

Ý nghĩa từng chặng:

1. `main` giữ tham chiếu `caller` tới thread đã gọi service.
2. `context.getBean(WorkService.class)` lấy bean đã được Spring xử lý; lời gọi `process("A")` qua proxy được gửi tới `lessonExecutor`.
3. Worker của pool chạy thân `process`, tạo `WorkResult("done:A", currentThread)`. `completedFuture(...)` ở **bên trong worker** chỉ đóng gói giá trị đã tính; nó không biến việc thực thi thành đồng bộ trên caller.
4. `handle` là đối tượng kết quả mà caller cầm được. `handle.join()` chờ nếu worker chưa xong, rồi lấy `WorkResult`.
5. `differentThread=true` xác nhận trong cấu hình cụ thể này, worker là thread khác caller. Thứ tự chạy thực tế vẫn do executor và scheduler quyết định.

Nếu cần quan sát tên luồng, tạm thêm `System.out.println(Thread.currentThread().getName())` trong caller và thân `process`. Tên chính xác và thời điểm các dòng log xen kẽ phụ thuộc runtime, nên không nên viết test phụ thuộc thứ tự log giữa các thread.

## 5. Kết quả, lỗi và bước xử lý tiếp theo

### Giá trị và chờ

- `Future<T>.get()` lấy kết quả khi sẵn sàng; có thể chặn và khai báo các ngoại lệ kiểm tra, gồm `InterruptedException` và `ExecutionException`.
- `CompletableFuture<T>.join()` cũng chờ khi cần nhưng báo lỗi hoàn tất thông qua `CompletionException` (unchecked).
- `thenApply` biến đổi giá trị sau khi hoàn thành; `thenCompose` ghép một bước trả về `CompletionStage` khác; `thenCombine` kết hợp hai kết quả độc lập.
- `thenApply` không bảo đảm callback chạy trên một thread riêng: nó có thể chạy trong luồng hoàn tất stage hoặc thread đăng ký nếu stage đã xong. Các biến thể `...Async` chọn cơ chế thực thi bất đồng bộ; nên truyền executor tường minh khi cần kiểm soát tài nguyên.

Đoạn sau minh họa cách dùng trong một phương thức đã có `WorkService service` (không phải một chương trình Java độc lập):

```java
CompletableFuture<String> displayName = service.process("A")
    .thenApply(work -> "Output = " + work.value());

displayName.whenComplete((value, error) -> {
    if (error != null) {
        System.err.println("Task failed: " + error.getMessage());
    } else {
        System.out.println(value);
    }
});
```

`whenComplete` quan sát cả thành công và thất bại; nếu cần *phục hồi* bằng giá trị dự phòng, cân nhắc `exceptionally` hoặc `handle`. Đừng nuốt lỗi bằng cách trả về giá trị giả mà không ghi nhận nguyên nhân.

### Ngoại lệ, timeout, hủy

Với `@Async` trả về `Future`/`CompletableFuture`, ngoại lệ của công việc được ghi vào kết quả bất đồng bộ và chỉ hiện rõ khi bạn quan sát stage (ví dụ `join`, `get`, `whenComplete`). Với phương thức `@Async void`, caller không thể nhận lỗi qua giá trị trả về; cấu hình `AsyncUncaughtExceptionHandler` khi cần xử lý tập trung, tránh cơ chế “fire-and-forget” cho tác vụ quan trọng.

`orTimeout(2, TimeUnit.SECONDS)` có thể làm `CompletableFuture` hoàn tất bằng lỗi timeout; **timeout kết quả không tự bảo đảm dừng tác vụ nền**. Hủy một `CompletableFuture` cũng không phải cam kết ngắt thread đang chạy. Muốn có hủy thực sự cần thiết kế tác vụ hợp tác kiểm tra trạng thái, timeout ở I/O, và cơ chế hủy phù hợp.

Nếu executor hết khả năng nhận task, việc *gửi* có thể thất bại bằng lỗi từ chối (ví dụ `TaskRejectedException` trong Spring). Trường hợp này khác với ngoại lệ phát sinh sau khi worker bắt đầu xử lý; cả hai phải được tính trong thiết kế lỗi.

## 6. Luồng HTTP và luồng tác vụ không phải một

Ví dụ trong một ứng dụng Spring MVC Servlet:

```text
HTTP request -> servlet request thread -> Controller
                 |                        |
                 | gọi @Async service     | có thể nhận CompletableFuture
                 |----------------------> | rồi return / join / xử lý khác
                                          |
                                 worker thread xử lý service
```

Nếu controller gọi `service.process(...).join()` ngay, thread đang thực thi controller vẫn chờ kết quả. Việc service chạy ở worker **không tự động giải phóng HTTP request thread** trong thời gian chờ.

Spring MVC có cơ chế xử lý request bất đồng bộ riêng, ví dụ controller trả về `Callable<T>`, `DeferredResult<T>` hoặc `CompletionStage<T>`/`CompletableFuture<T>` được hỗ trợ. Với `Callable`, Spring MVC gửi phần tính toán tới `AsyncTaskExecutor`, nhả thread Servlet đang phục vụ request rồi dispatch lại khi có kết quả. Với `DeferredResult`, một nguồn bên ngoài hoàn tất kết quả và MVC dispatch lại để trả response. HTTP response vẫn chỉ hoàn tất khi đã có dữ liệu phù hợp hoặc lỗi/timeout.

Vì vậy cần nêu rõ **hai hợp đồng tách biệt** khi thiết kế: (1) phương thức service được thực thi trên executor nào, (2) request HTTP được giữ chờ hay được framework quản lý theo chế độ async. Không coi `@Async` là một cách tự động biến toàn bộ controller thành non-blocking; một worker chạy JDBC blocking vẫn là một thread bị chiếm trong khoảng thời gian đó.

## 7. Dữ liệu đi qua ranh giới thread

**Tham số và đối tượng chia sẻ:** dữ liệu được truyền cho task vẫn tuân theo các quy tắc an toàn luồng của Java. Nếu caller sửa một `ArrayList` hoặc DTO mutable trong lúc worker đọc nó, dễ xảy ra race condition. Ưu tiên dữ liệu bất biến, snapshot trước khi submit hoặc đồng bộ hóa đúng cách.

**`ThreadLocal` và context:** MDC logging, `SecurityContext`, request/locale context và transaction theo thread không mặc nhiên chuyển sang worker. Nếu cần, chọn cơ chế truyền context có chủ đích, ví dụ `TaskDecorator` cho dữ liệu logging và executor hỗ trợ security context. Không dùng context của HTTP request sau khi request kết thúc nếu nó không còn hợp lệ.

**Giao dịch:** giao dịch gắn với thread caller không tự “đi theo” `@Async`. Một thao tác ghi dữ liệu trên worker cần ranh giới giao dịch riêng nếu yêu cầu; thường đặt xử lý transactional ở bean được gọi từ worker thông qua Spring proxy. Tránh dựa vào transaction của caller để bảo đảm atomicity cho công việc nền.

**Vòng đời:** task `@Async` chạy trong tiến trình ứng dụng. Nếu tiến trình dừng, tác vụ chưa hoàn tất có thể mất; task quan trọng phải được thiết kế có tính bền vững, retry/idempotency và có thể cần hàng đợi/broker hoặc job store. Đóng executor và xử lý shutdown có kiểm soát.

## 8. Sai lầm thường gặp và cách xác minh

| Sai lầm | Hậu quả | Cách kiểm tra/sửa |
| --- | --- | --- |
| Nghĩ `@Async` luôn chạy trên luồng khác mà không kiểm tra proxy | Lời gọi có thể vẫn đồng bộ do thiếu `@EnableAsync` hoặc self-invocation. | Gọi qua bean Spring được inject; kiểm tra cấu hình, thread và proxy. |
| Cho rằng task = thread | Kỳ vọng mỗi lời gọi có một thread riêng; khó dự đoán dưới tải. | Đo số worker, queue và hành vi executor. |
| Gọi `join()` ngay rồi tuyên bố caller “không chờ” | Caller bị chặn cho tới khi task xong. | Ghi nhận thời điểm submit, thời điểm join và thời điểm hoàn tất. |
| Đoán thứ tự chạy qua thứ tự câu lệnh submit | Task khác nhau có thể hoàn tất không theo thứ tự gửi. | Ghép kết quả bằng ID hoặc các stage, không dựa vào thứ tự log. |
| Dùng `@Async void` cho việc cần bảo đảm thành công | Không có handle để biết lỗi/kết quả. | Trả `CompletableFuture<T>` hoặc lưu trạng thái công việc bền vững. |
| Đặt executor quá nhỏ, queue không giới hạn hoặc thiếu chính sách từ chối | Độ trễ, bùng bộ nhớ, task bị loại trong giờ cao điểm. | Chọn giới hạn phù hợp, đo queue/active threads/rejection, thiết kế backpressure. |
| Dùng chung state hoặc giả định transaction/context theo thread được kế thừa | Race condition, mất thông tin logging/security, giao dịch không đúng. | Dùng dữ liệu an toàn luồng và truyền context/ràng buộc giao dịch tường minh. |

Một quy trình chẩn đoán ngắn: đặt ID cho task → log thời điểm/caller thread tại điểm gửi → log worker thread tại đầu/cuối task → quan sát trạng thái/completion của handle → xác định nơi `get`/`join` hoặc response HTTP đang chờ → ghi nhận thành công, lỗi, timeout và rejection. Dùng `Thread.currentThread().getName()` để quan sát; không suy luận chỉ từ tên annotation.

## 9. Tự kiểm tra

1. **Controller gọi `@Async` service rồi `join()` ngay.** Service có thể chạy trên worker, nhưng thread controller vẫn chờ; muốn HTTP request bất đồng bộ còn cần hợp đồng trả về phù hợp với Spring MVC.
2. **Một executor chỉ có một worker nhận ba task.** Chúng vẫn là ba task độc lập, nhưng có thể phải chạy nối tiếp; “đã submit” không đồng nghĩa “đã chạy song song”.
3. **Phương thức `@Async` được gọi bằng `this.method()` trong cùng bean.** Với proxy mode mặc định, lời gọi nội bộ không đi qua interceptor, nên không có chuyển việc sang executor nhờ annotation.
4. **Caller nhận `CompletableFuture` đã `isDone() == true`.** Điều đó không chứng minh lời gọi chạy đồng bộ; task có thể hoàn tất rất nhanh trước khi kiểm tra.
5. **Worker ném ngoại lệ.** Với phương thức trả `CompletableFuture`, phải quan sát kết quả bất đồng bộ để biết lỗi; ngoại lệ không nhất thiết được ném ngay tại dòng caller gọi proxy.

### Ghi nhớ

Một lời gọi có bốn câu hỏi độc lập: **việc gì cần chạy, ai gửi việc, thread nào thực sự chạy và kết quả được quan sát lúc nào**. Khi đọc mã Spring concurrency, lần theo cả bốn trước khi kết luận hệ thống có chạy song song, có giải phóng thread hay có xử lý lỗi an toàn.

### Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `TaskExecutor`, `ThreadPoolTaskExecutor`, `@EnableAsync`, `@Async`, lựa chọn executor và lỗi.
- [Spring Framework — Asynchronous Requests (Spring MVC)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html): phân biệt xử lý service task với vòng đời request Servlet bất đồng bộ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-004">Mối liên hệ giữa TaskExecutor, @Async, TaskScheduler và @Scheduled</a>

<details>
<summary>Xem chi tiết</summary>

# Mối liên hệ giữa TaskExecutor, @Async, TaskScheduler và @Scheduled trong Spring Framework

## 1. Bức tranh tổng thể: thực thi tác vụ và quyết định thời điểm chạy

Spring Framework cung cấp hai nhóm khả năng thường được dùng cùng nhau nhưng giải quyết **hai câu hỏi khác nhau**:

- **Thực thi (execution):** một công việc đã được yêu cầu chạy thì chạy ở đâu, bằng luồng nào, có hàng đợi và bao nhiêu công việc được xử lý đồng thời? Đây là vai trò của **TaskExecutor**; **@Async** là cách khai báo để Spring gửi lời gọi phương thức sang executor.
- **Lập lịch (scheduling):** khi nào một công việc cần được kích hoạt: một lần trong tương lai, sau mỗi khoảng nghỉ, theo nhịp đều đặn hay theo lịch cron? Đây là vai trò của **TaskScheduler**; **@Scheduled** khai báo lịch kích hoạt một phương thức.

Hai nhóm này **độc lập**: ứng dụng chỉ cần xử lý bất đồng bộ có thể dùng @Async mà không bật @Scheduled; ứng dụng chỉ cần công việc định kỳ có thể dùng @Scheduled mà không bật @Async. Chúng kết hợp khi muốn *đến giờ thì kích hoạt một công việc, rồi giao phần xử lý nặng cho một executor khác*.

| Thành phần | Bản chất | Chức năng chính | Khi nào hoạt động? |
| --- | --- | --- | --- |
| **TaskExecutor** | Interface, mở rộng java.util.concurrent.Executor | Nhận Runnable và thực thi theo chính sách của executor | Khi có mã gửi task bằng execute hoặc Spring gửi lời gọi @Async |
| **@Async** | Annotation trên phương thức/kiểu | Đánh dấu lời gọi qua Spring proxy để đưa phần thân phương thức sang executor | Khi một đối tượng khác gọi phương thức qua Spring proxy |
| **TaskScheduler** | Interface | Đăng ký Runnable để chạy một lần hoặc lặp theo thời gian/Trigger | Khi mã đăng ký task hoặc Spring đăng ký @Scheduled |
| **@Scheduled** | Annotation trên phương thức | Khai báo fixedDelay, fixedRate, cron, initialDelay | Spring đăng ký lịch khi bean được khởi tạo; scheduler tự kích hoạt sau đó |

Hình dung hai luồng điều khiển:

~~~text
Người gọi HTTP/event/service
    └── gọi bean.method() qua Spring proxy
            └── @Async ──> TaskExecutor ──> worker thread ──> phương thức

Đồng hồ / lịch kích hoạt
    └── TaskScheduler ──> @Scheduled method trên bean
                               ├── xử lý trực tiếp trên scheduler thread
                               └── gọi bean khác qua proxy @Async
                                        └── TaskExecutor ──> worker thread
~~~

**Điểm mấu chốt:** @Async không tự tạo lịch; @Scheduled không mặc nhiên chuyển toàn bộ công việc sang pool @Async. Việc kích hoạt theo lịch và nơi thực thi công việc là hai quyết định riêng.

## 2. TaskExecutor và @Async liên quan với nhau thế nào?

**TaskExecutor** nhận tác vụ dạng Runnable qua phương thức execute(Runnable). Spring cung cấp nhiều triển khai; tên "executor" không bảo đảm rằng có thread pool: ví dụ SyncTaskExecutor chạy ngay trên luồng gọi, SimpleAsyncTaskExecutor tạo thread cho mỗi task (có thể được cấu hình dùng virtual threads trên JDK phù hợp), còn ThreadPoolTaskExecutor quản lý thread pool và queue.

Cách gọi trực tiếp:

~~~java
// Ví dụ phương thức nằm bên trong một Spring bean đã inject TaskExecutor.
public void submit(TaskExecutor executor) {
    executor.execute(() ->
            System.out.println(Thread.currentThread().getName()));
}
~~~

Với **@Async**, lập trình viên không tự bọc Runnable. Spring dùng cơ chế AOP/proxy (mặc định) để chặn lời gọi hợp lệ, gửi việc sang executor, rồi trả quyền điều khiển cho caller mà không chờ phần thân phương thức chạy xong. Điều này không có nghĩa mọi phương thức gắn @Async đều chạy nền: điều kiện kích hoạt proxy rất quan trọng.

Để bật annotation trong cấu hình Spring, dùng **@EnableAsync**. Phương thức @Async thường khai báo trả về **void**, **Future<T>** hoặc **CompletableFuture<T>**. Phương thức void phù hợp cho fire-and-forget có cơ chế xử lý lỗi riêng; CompletableFuture cho phép caller nhận kết quả, ghép bước hoặc xử lý thất bại mà không block. Tránh gọi get()/join() ngay lập tức nếu mục tiêu là không chặn luồng gọi.

Ví dụ ngắn về tách bean:

~~~java
@Service
class EmailWorker {
    @Async("appTaskExecutor")
    public CompletableFuture<String> send(String address) {
        // Thân phương thức chạy trên executor được chỉ định.
        return CompletableFuture.completedFuture("sent to " + address);
    }
}

@Service
class CheckoutService {
    private final EmailWorker worker;

    CheckoutService(EmailWorker worker) {
        this.worker = worker;
    }

    public CompletableFuture<String> checkout(String address) {
        // Gọi qua bean khác để proxy của Spring chặn được.
        return worker.send(address);
    }
}
~~~

Đoạn trên chỉ minh họa quan hệ giữa các bean; các import, cấu hình executor và ứng dụng chạy độc lập có ở ví dụ hoàn chỉnh bên dưới.

**Bẫy self-invocation:** nếu cùng một object gọi this.send() hoặc chỉ gọi send() nội bộ, lời gọi không đi qua proxy nên @Async có thể không phát huy tác dụng. Trong chế độ proxy mặc định, tránh đặt công việc async ở phương thức private/final không thể được proxy chặn đúng cách; ưu tiên phương thức public trên Spring bean được gọi từ bean khác. @Async không nên dùng như cách khởi tạo async trực tiếp trên phương thức @PostConstruct.

## 3. TaskScheduler và @Scheduled liên quan với nhau thế nào?

**TaskScheduler** biểu diễn năng lực lên lịch. Nó có các API như schedule(Runnable, Instant), schedule(Runnable, Trigger), scheduleAtFixedRate(Runnable, Duration), scheduleWithFixedDelay(Runnable, Duration). Các phương thức trả về ScheduledFuture trong những trường hợp thích hợp, cho phép hủy lịch bằng cancel(...).

**@Scheduled** là lớp khai báo phía trên chức năng ấy: Spring tìm những phương thức có annotation trên bean, đăng ký các lần kích hoạt với scheduler; bạn không cần tự gọi scheduler.schedule(...) cho từng phương thức. Bật tính năng này bằng **@EnableScheduling**.

Ví dụ khai báo:

~~~java
@Scheduled(initialDelay = 1000, fixedDelay = 5000)
public void refreshCache() {
    // Kích hoạt lần đầu sau khoảng 1 giây;
    // mỗi lần tiếp theo chờ 5 giây kể từ khi lần trước kết thúc.
}

@Scheduled(fixedRate = 5000)
public void updateHeartbeat() {
    // Nhịp dự kiến là 5 giây giữa các thời điểm bắt đầu.
}

@Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
public void weekdayReport() {
    // 09:00 sáng thứ Hai đến thứ Sáu theo múi giờ đã chọn.
}
~~~

Mặc định các giá trị số trong ví dụ trên tính bằng **mili giây**; có thể dùng thuộc tính timeUnit của @Scheduled. Cron của Spring thông thường có **sáu trường** gồm giây, phút, giờ, ngày trong tháng, tháng, thứ trong tuần; không tự lấy cron năm trường của hệ Unix rồi áp dụng y nguyên. Với phương thức @Scheduled kiểu Java đồng bộ thông thường, khai báo **không tham số và trả về void**; kết quả trả về thông thường không được dùng. Spring Framework 6.1+ còn hỗ trợ riêng một số kiểu Publisher/reactive và Kotlin suspend với các quy tắc khác, không nên suy diễn từ ví dụ Java đồng bộ.

Phân biệt ba cách lập lịch:

| Cấu hình | Khoảng thời gian đo từ đâu? | Điều cần nhớ |
| --- | --- | --- |
| **fixedDelay** | Sau khi một lần *gọi phương thức được lập lịch* kết thúc mới chờ thêm khoảng delay | Phù hợp khi muốn có khoảng nghỉ giữa các lần chạy; nếu phương thức chỉ gửi async task rồi return, nó không chờ async task hoàn thành |
| **fixedRate** | Nhắm tới chu kỳ tính theo thời điểm bắt đầu đã lên lịch | Tác vụ có thể bị trễ vì scheduler bận; không suy ra mọi lần của một registration chắc chắn chạy chồng nhau |
| **cron** | Những thời điểm lịch khớp biểu thức, có thể gắn zone | Dùng cho lịch theo giờ/ngày; cân nhắc múi giờ và thay đổi giờ mùa hè ở các zone có DST |

Spring có **ThreadPoolTaskScheduler**, thường thực thi callback ngay trên các scheduler threads mà nó quản lý. Vì vậy một callback @Scheduled chạy lâu có thể chiếm chỗ và làm những lịch khác trễ nếu scheduler quá nhỏ. Spring 6.1 có **SimpleAsyncTaskScheduler** với cách thực thi khác (đặc biệt fixedDelay vẫn thực thi trên một scheduler thread); đừng đồng nhất mọi TaskScheduler với cùng một mô hình thread.

Nếu không chỉ định scheduler rõ ràng, Spring có cơ chế tìm scheduler phù hợp hoặc tạo fallback cục bộ; trong Spring Boot còn có auto-configuration phụ thuộc các bean và thuộc tính của ứng dụng. Vì vậy **không nên giả định scheduler và async executor là cùng một pool**. Khi có nhiều lựa chọn, khai báo bean hoặc chỉ định cấu hình minh bạch sẽ dễ kiểm soát hơn.

## 4. Ví dụ chạy được: lịch kích hoạt, executor xử lý

Bộ ví dụ này dùng **Java 17+ và Spring Boot 3.3.x (Spring Framework 6.1)**. Tạo project Gradle với cấu trúc:

~~~text
schedule-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/concurrency/
    ├── DemoApplication.java
    ├── TaskConfiguration.java
    ├── ReportWorker.java
    └── ReportSchedule.java
~~~

Tệp **settings.gradle**:

~~~groovy
rootProject.name = 'schedule-demo'
~~~

Tệp **build.gradle** (phiên bản là ví dụ ổn định, không hàm ý là bản mới nhất):

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'demo'
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
}
~~~

**DemoApplication.java**:

~~~java
package demo.concurrency;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
~~~

**TaskConfiguration.java** tạo **hai pool riêng biệt**. Tên appTaskExecutor được @Async chọn tường minh; tên taskScheduler cho Spring nhận diện scheduler của các phương thức @Scheduled.

~~~java
package demo.concurrency;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class TaskConfiguration {
    @Bean(name = "appTaskExecutor")
    public ThreadPoolTaskExecutor appTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("async-");
        return executor;
    }

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("schedule-");
        return scheduler;
    }
}
~~~

**ReportWorker.java** chạy việc nặng trên executor; CompletableFuture ghi nhận thành công hoặc thất bại. Giữ lại cờ ngắt khi sleep bị interrupt.

~~~java
package demo.concurrency;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportWorker {
    @Async("appTaskExecutor")
    public CompletableFuture<String> buildReport(int sequence) {
        System.out.printf("WORK start #%d thread=%s%n",
                sequence, Thread.currentThread().getName());

        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(ex);
        }

        System.out.printf("WORK end   #%d thread=%s%n",
                sequence, Thread.currentThread().getName());
        return CompletableFuture.completedFuture("report-" + sequence);
    }
}
~~~

**ReportSchedule.java** chỉ nhận nhịp lịch và ủy quyền qua **bean khác** để Spring proxy xử lý @Async; không đợi công việc kéo dài hai giây.

~~~java
package demo.concurrency;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReportSchedule {
    private final AtomicInteger counter = new AtomicInteger();
    private final ReportWorker worker;

    public ReportSchedule(ReportWorker worker) {
        this.worker = worker;
    }

    @Scheduled(initialDelay = 1000, fixedDelay = 4000)
    public void onTick() {
        int sequence = counter.incrementAndGet();

        System.out.printf("TICK       #%d thread=%s%n",
                sequence, Thread.currentThread().getName());

        worker.buildReport(sequence).whenComplete((result, error) -> {
            if (error != null) {
                System.err.printf("FAIL       #%d cause=%s%n",
                        sequence, error);
            } else {
                System.out.printf("DONE       #%d result=%s%n",
                        sequence, result);
            }
        });
    }
}
~~~

Dùng **Gradle 8.5+ và JDK 17+** cài trên máy; chạy tại thư mục schedule-demo:

~~~bash
gradle bootRun
~~~

Nếu dự án có Gradle Wrapper, dùng **./gradlew bootRun** trên Linux/macOS hoặc **gradlew.bat bootRun** trên Windows. Dừng bằng Ctrl+C để đóng application context.

Một lần chạy *có thể* in ra các dòng sau (đã bỏ log khởi động):

~~~text
TICK       #1 thread=schedule-1
WORK start #1 thread=async-1
WORK end   #1 thread=async-1
DONE       #1 result=report-1
TICK       #2 thread=schedule-1
WORK start #2 thread=async-2
WORK end   #2 thread=async-2
DONE       #2 result=report-2
~~~

Tên/số thứ tự thread thực tế, thời điểm và thứ tự các dòng phụ thuộc runtime; **không coi transcript trên là stdout duy nhất hoặc bất biến**. Điều cần quan sát là dòng TICK chạy trên **schedule-**, WORK chạy trên **async-**, còn lần kích hoạt tiếp theo không bị buộc phải chờ worker của lần trước.

### Vì sao ví dụ cần cả hai bean?

1. **taskScheduler** quản lý các lần kích hoạt onTick() theo fixedDelay. Nó không nhận trách nhiệm chạy toàn bộ report nặng.
2. **ReportSchedule** chạy ngắn, giao task rồi return. Vì thế khoảng fixedDelay bốn giây đo từ lúc onTick() kết thúc, không đo từ lúc ReportWorker hoàn tất.
3. **@Async("appTaskExecutor")** là nơi lời gọi buildReport() được chuyển sang executor pool. Annotation sẽ không có tác dụng như kỳ vọng nếu thay worker.buildReport(...) bằng một lời gọi nội bộ trên cùng object.
4. **appTaskExecutor** điều khiển lượng worker có thể thực thi/đang chờ. Nếu thời gian report lớn hơn chu kỳ kích hoạt và tốc độ đến vượt tốc độ xử lý, queue có thể tích tụ rồi dẫn đến từ chối task.
5. **whenComplete** quan sát lỗi hoặc giá trị của CompletableFuture. Callback có thể chạy trên thread hoàn thành future hoặc thread gắn callback, tùy thời điểm; không nên mặc định đó là scheduler thread hay một pool riêng.

Với cấu hình executor trên, corePoolSize=2, maxPoolSize=4 và queueCapacity=20. ThreadPoolExecutor thường ưu tiên tạo đến core pool, sau đó đưa task vào queue, rồi mới tăng trên core khi queue đầy; **maxPoolSize=4 không có nghĩa bốn worker luôn chạy ngay từ đầu**. Đặt queue quá lớn có thể gây độ trễ và sử dụng bộ nhớ tăng.

## 5. Các tổ hợp đúng và cách chọn thiết kế

| Nhu cầu | Dùng gì? | Giải thích |
| --- | --- | --- |
| Xử lý theo lời gọi người dùng mà không chặn caller | **@Async** + TaskExecutor | Caller chủ động tạo yêu cầu; executor quyết định thực thi |
| Gửi một Runnable thủ công sang pool | **TaskExecutor.execute(...)** | Không cần annotation nếu muốn gửi task trực tiếp |
| Chạy dọn dẹp định kỳ, nhanh, không block | **@Scheduled** + TaskScheduler | Scheduler tự đánh thức phương thức theo lịch |
| Chạy job định kỳ nhưng phần việc nặng nên tách pool | **@Scheduled** trong bean A gọi **@Async** trong bean B | Phân tách chính sách thời gian với năng lực xử lý |
| Tự đăng ký lịch linh hoạt và cần hủy | **TaskScheduler.schedule(...)**, giữ ScheduledFuture | Phù hợp khi lịch được tạo/hủy theo yêu cầu runtime |

Một ứng dụng cũng có thể dùng TaskScheduler trực tiếp mà không viết @Scheduled:

~~~java
// Trích đoạn nằm trong một Spring bean đã inject TaskScheduler.
ScheduledFuture<?> handle = taskScheduler.scheduleAtFixedRate(
        () -> System.out.println("heartbeat"),
        java.time.Duration.ofSeconds(5)
);

// Khi không cần chạy nữa:
handle.cancel(false);
~~~

Ở mức triển khai, TaskScheduler có thể dựa trên ScheduledExecutorService, còn TaskExecutor có thể dùng ThreadPoolExecutor. Dù cùng họ java.util.concurrent, API của Spring vẫn đại diện hai vai trò riêng. Việc một scheduler có thể thực thi Runnable không làm nó tự động trở thành executor cho mọi @Async.

### Có nên đặt @Scheduled và @Async trên cùng một phương thức?

Có những cấu hình có thể khiến chúng phối hợp, nhưng **khó dự đoán giới hạn chồng lấp và vòng đời** hơn: lúc phương thức được gọi theo lịch, lời gọi async có thể trả về trước khi công việc kết thúc; fixedDelay vì vậy không còn đại diện cho khoảng nghỉ giữa hai lần làm việc thực tế. Với một bài toán cần vận hành rõ ràng, nên tách **bean kích hoạt ngắn** (@Scheduled) và **bean xử lý nền** (@Async), như ví dụ phía trên.

Nếu yêu cầu là "chỉ được chạy một lần tại một thời điểm", riêng @Scheduled + @Async không đáp ứng cam kết đó. Phải có cơ chế không cho chạy chồng (khóa, semaphore, trạng thái job, queue giới hạn hoặc giải pháp orchestration phù hợp). Trong hệ nhiều instance, mỗi instance có thể tự đăng ký lịch giống nhau; @Scheduled tự nó không cung cấp điều phối **chạy duy nhất trên cả cluster**.

## 6. Lỗi và các ràng buộc quan trọng

**Không thấy @Async hoạt động:** kiểm tra @EnableAsync, bean có do Spring quản lý hay không, lời gọi có đi qua proxy hay không, method có phù hợp để proxy intercept hay không, executor có tồn tại theo qualifier. In Thread.currentThread().getName() ở cả caller và worker là cách chẩn đoán nhanh.

**Không thấy @Scheduled kích hoạt:** kiểm tra @EnableScheduling, bean có được tạo trong ApplicationContext, thuộc tính cron/fixedDelay có hợp lệ, scheduler có bị nghẽn hay không. Một phương thức bình thường gắn @Scheduled vẫn có thể bị gọi trực tiếp bằng Java; đó là lời gọi thường, không phải một lần kích hoạt do lịch tạo ra.

**Nhầm fixedDelay với thời gian hoàn thành công việc async:** nếu method @Scheduled gọi async rồi trả ngay, lần kích hoạt sau đo từ thời gian method ngắn đó hoàn tất. Muốn delay kể từ khi toàn bộ xử lý hoàn thành, hãy thiết kế callback được lập lịch để đợi/điều phối việc hoàn thành một cách chủ đích; cân nhắc tác động lên scheduler thread, timeout và throughput. Không sửa bằng cách thêm Thread.sleep() mù quáng.

**Bão task hoặc tài nguyên cạn:** tăng scheduler pool chỉ giúp đẩy nhiều callback, không giải quyết worker pool chậm; tăng worker pool vô hạn cũng không tạo thêm tài nguyên hệ thống. Cần đo thời gian xử lý, backlog, task rejected, CPU/I/O, giới hạn hàng đợi và dùng backpressure/chính sách bỏ hoặc gộp việc khi phù hợp.

**Lỗi bị mất dấu:** với @Async trả void, ngoại lệ không thể tự chuyển về caller giống lời gọi đồng bộ; cấu hình AsyncUncaughtExceptionHandler hoặc ghi log/giám sát phù hợp. Với Future/CompletableFuture, xử lý complete exceptionally, whenComplete, exceptionally hoặc get() ở ranh giới có chủ đích. Với @Scheduled đồng bộ, lỗi thuộc luồng scheduler và cơ chế ErrorHandler tương ứng; đừng coi hành vi xử lý lỗi của hai annotation là giống nhau.

**Ngữ cảnh thread không tự truyền:** transaction, ThreadLocal, MDC logging, SecurityContext hoặc request context không chắc có mặt trên async worker. Đặc biệt, @Transactional trên caller không có nghĩa worker dùng cùng transaction/connection. Nếu cần, truyền dữ liệu rõ ràng, bắt đầu transaction ở ranh giới worker, hoặc cấu hình TaskDecorator/cơ chế truyền context có kiểm soát. Không chia sẻ EntityManager hay đối tượng không thread-safe giữa các task.

**Tắt ứng dụng:** khi shutdown, xem xét chính sách chờ task hoàn thành, hủy scheduled trigger và xử lý interrupt. Không giả định chương trình dừng ngay hoặc sẽ luôn hoàn tất mọi công việc đang đợi; điều đó phụ thuộc cấu hình executor/scheduler và vòng đời Spring.

**Nhiều lịch trên một phương thức:** @Scheduled có thể lặp lại; mỗi declaration có trigger riêng và có thể tạo các lần chạy gần nhau/chồng lấp. Không trông cậy một pool nhỏ như một cơ chế khóa nghiệp vụ.

## 7. Câu hỏi tự kiểm tra

1. Một REST endpoint gọi phương thức @Async nhưng code gọi bằng this.method(): vì sao thread vẫn là HTTP request thread? **Vì lời gọi nội bộ bỏ qua proxy trong mode mặc định.**
2. @Scheduled(fixedDelay = 5000) gọi @Async rồi return sau 10 ms, worker mất 30 giây: lần kích hoạt kế tiếp có cần chờ worker xong không? **Không; fixedDelay gắn với lời gọi được lập lịch kết thúc, không tự quan sát completion của future.**
3. Có @EnableScheduling mà không có @EnableAsync, phương thức @Scheduled gọi một phương thức @Async khác: điều gì chắc chắn? **Lịch vẫn được kích hoạt, nhưng @Async chưa được bật thông qua annotation configuration này nên không thể mặc nhiên coi việc gọi đó là bất đồng bộ.**
4. Tạo ThreadPoolTaskExecutor với 2 core threads, 4 max threads và queue 20: khi tới task thứ ba, có luôn tạo thread thứ ba không? **Không; thường task được xếp vào queue trước khi tăng quá core size.**
5. Deploy ba bản sao ứng dụng cùng có @Scheduled cron 09:00: sẽ có bao nhiêu lần gọi? **Thông thường mỗi instance tự kích hoạt một lần; cần phối hợp bên ngoài nếu nghiệp vụ yêu cầu đúng một lần toàn hệ thống.**

## 8. Ghi nhớ và tài liệu gốc

- **TaskExecutor / @Async** trả lời "chạy công việc bằng tài nguyên nào và làm sao không giữ caller chờ".
- **TaskScheduler / @Scheduled** trả lời "khi nào Spring kích hoạt công việc".
- Hai nhóm chỉ phối hợp khi ta thiết kế chúng phối hợp; nếu một lần lịch có thể tạo nhiều việc nền, phải kiểm soát queue, lỗi, chồng lấp, context và shutdown.
- Bắt đầu bằng cách xác định mục tiêu: tác vụ theo sự kiện, tác vụ định kỳ, hay cả hai; sau đó chọn pool và chính sách giám sát riêng cho từng vai trò.

Tài liệu chính thức: [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html). Các chi tiết về mặc định executor/scheduler có thể khác theo phiên bản Spring Framework, Spring Boot và cấu hình ứng dụng; ví dụ trên chỉ định rõ hai bean để tránh phụ thuộc các mặc định đó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-005">Tách thời điểm nhận yêu cầu khỏi thời điểm hoàn thành công việc</a>

<details>
<summary>Xem chi tiết</summary>

# Tách thời điểm nhận yêu cầu khỏi thời điểm hoàn thành công việc

> **Module:** Spring Framework - concurrency · **Nhóm kiến thức:** Tổng quan  
> **Trọng tâm:** Hiểu sự khác nhau giữa thời điểm hệ thống tiếp nhận một yêu cầu, thời điểm bắt đầu xử lý và thời điểm công việc thực sự hoàn tất; chọn đúng cách tổ chức tác vụ bất đồng bộ trong ứng dụng Spring.

## 1. Vấn đề và mô hình tư duy

Một yêu cầu HTTP có thể đến trong vài mili giây nhưng công việc phía sau lại mất vài giây hoặc vài phút: tạo báo cáo, xuất tệp, gửi email, lập chỉ mục, xử lý ảnh hoặc đẩy dữ liệu sang dịch vụ khác. Nếu controller làm toàn bộ công việc rồi mới trả lời, thời gian phản hồi HTTP gắn chặt với thời gian xử lý nghiệp vụ.

Có **ba mốc thời gian** cần phân biệt:

1. **Tiếp nhận (accept):** xác thực, phân quyền, kiểm tra dữ liệu, quyết định có nhận việc hay không, tạo mã theo dõi hoặc ghi nhận yêu cầu.
2. **Thực thi (execute):** công việc được lấy khỏi hàng đợi hoặc được giao cho worker để chạy; có thể bắt đầu ngay, bắt đầu muộn hoặc bị từ chối khi hệ thống quá tải.
3. **Hoàn thành (complete):** có kết quả thành công/thất bại cuối cùng; trạng thái này có thể đến rất lâu sau khi client đã nhận phản hồi tiếp nhận.

Ví dụ thời gian:

```text
t0   Client gửi POST /jobs
t1   Server kiểm tra, ghi nhận job J42
t2   Server trả HTTP 202 + địa chỉ GET /jobs/J42
t3   Worker bắt đầu xử lý J42
t4   Worker hoàn tất, lưu SUCCEEDED (hoặc FAILED)
t5   Client tra cứu GET /jobs/J42 để lấy trạng thái
```

Thời điểm `t2` **không cần chờ** `t4`. Đây là ý nghĩa chính của việc tách thời điểm nhận yêu cầu khỏi thời điểm hoàn thành công việc. Tách thời điểm **không đồng nghĩa** tự động có hệ thống chạy song song vô hạn, không chặn luồng hoặc có khả năng chịu lỗi; từng thuộc tính phải được thiết kế riêng.

### Vì sao cần tách?

- **Độ trễ phản hồi:** người dùng có thể nhận ngay mã công việc và tiếp tục thao tác.
- **Sức chứa hệ thống:** thread xử lý HTTP không phải đứng chờ toàn bộ tác vụ dài; các worker có giới hạn tài nguyên riêng.
- **Điều tiết tải:** hàng đợi, giới hạn số việc đang chạy và chính sách từ chối giúp tránh quá tải dây chuyền.
- **Quan sát và phục hồi:** trạng thái công việc, lỗi và lần thử lại được theo dõi tách khỏi vòng đời kết nối HTTP.

Không phải yêu cầu nào cũng nên tách. Nếu client cần kết quả ngay để đưa ra quyết định tiếp theo, công việc rất ngắn hoặc cần trả một lỗi nghiệp vụ trực tiếp trước khi kết thúc HTTP, xử lý đồng bộ có thể đơn giản và phù hợp hơn.

## 2. Phân biệt các cơ chế dễ nhầm trong Spring

| Cơ chế | HTTP trả lúc nào? | Thread HTTP ban đầu | Công việc có sống sau khi HTTP trả? | Phù hợp |
| --- | --- | --- | --- | --- |
| Controller đồng bộ | Sau khi hoàn tất xử lý | Thường bị giữ suốt quá trình | Không phải mô hình tách | Tác vụ nhanh, cần kết quả ngay |
| Spring MVC `Callable`, `WebAsyncTask`, `DeferredResult` | Khi kết quả bất đồng bộ sẵn sàng hoặc hết thời gian chờ | Được giải phóng trong lúc chờ; response vẫn mở | Thông thường **không** tách vòng đời kết quả khỏi HTTP | Tối ưu chờ trong một HTTP response |
| `@Async` + trả `202 Accepted` | Ngay sau khi chấp nhận/giao việc thành công | Trả sớm; việc chạy ở executor | Có thể, trong cùng tiến trình JVM | Tác vụ nền đơn giản, chấp nhận mất việc nếu tiến trình chết |
| Ghi job bền vững + broker/queue + worker | Sau khi job được ghi nhận an toàn | Trả sớm | Có, nếu thiết kế lưu trữ/retry đúng | Việc quan trọng, dài, cần chịu lỗi và mở rộng |

**Hai kiểu “async” không thể hoán đổi:**

- **Async HTTP response:** `Callable` hoặc `DeferredResult` giúp Spring MVC thoát khỏi luồng Servlet ban đầu, sau đó tiếp tục xử lý để trả **cùng một** HTTP response. Kết nối phía client vẫn đợi kết quả.
- **Async business job:** controller trả `202` và mã job; worker hoàn tất sau. Kết quả có thể được lấy bằng polling, thông báo hoặc webhook. Đây mới là cách tách thời điểm phản hồi với thời điểm hoàn thành nghiệp vụ.

`CompletableFuture`, `@Async`, WebFlux hay `Mono` chỉ là công cụ lập trình bất đồng bộ. Việc trả một future hoặc dùng non-blocking I/O không tự động tạo ra một job bền vững, có cơ chế retry và có API theo dõi trạng thái.

## 3. Thiết kế hợp đồng HTTP cho công việc chạy sau

Một hợp đồng phổ biến:

```http
POST /jobs
HTTP/1.1 202 Accepted
Location: /jobs/7c5c...

{
  "jobId": "7c5c...",
  "statusUrl": "/jobs/7c5c..."
}
```

```http
GET /jobs/7c5c...
HTTP/1.1 200 OK

{
  "jobId": "7c5c...",
  "status": "RUNNING"
}
```

Các mã/ý nghĩa cần rõ ràng:

- `202 Accepted`: hệ thống **đã nhận yêu cầu để xử lý**, không khẳng định xử lý thành công; tác vụ vẫn có thể thất bại hoặc không được thực thi.
- `200 OK` từ endpoint tra cứu: việc **tra cứu trạng thái** thành công; trường `status` mới cho biết job đang chạy hay hoàn tất.
- `400`/`422` (tùy hợp đồng): dữ liệu không hợp lệ, không thể nhận việc. `401`/`403`: chưa xác thực hoặc không có quyền.
- `429 Too Many Requests` hoặc `503 Service Unavailable`: quá tải/tạm không nhận thêm việc, kèm chính sách thử lại nếu có.
- `404 Not Found`: không tìm thấy mã job hoặc mã không thuộc quyền truy cập; không suy diễn rằng job đã thất bại.

Trạng thái tối thiểu:

```text
QUEUED ---> RUNNING ---> SUCCEEDED
                 |
                 +------> FAILED
```

Trong hệ thống thực, có thể bổ sung `CANCELLED`, `RETRY_WAIT`, `EXPIRED`, số lần thử, lý do lỗi, `createdAt`, `startedAt`, `finishedAt` và đường dẫn kết quả. Hãy định nghĩa rõ trạng thái cuối và thời gian lưu trữ của job.

**Nguyên tắc:** trước khi phát `202`, phải biết chắc hệ thống đã *chấp nhận* việc theo mức bảo đảm của mình. Nếu thao tác đưa việc vào executor/queue bị từ chối, hãy trả lỗi phù hợp. Nếu công việc quan trọng phải không mất khi ứng dụng khởi động lại, việc “chấp nhận” phải bao gồm một bản ghi/điệp tin bền vững, chứ không chỉ tạo một đối tượng trong RAM.

## 4. Ví dụ chạy được: Spring Boot MVC + `@Async` + tra cứu trạng thái

Ví dụ sau dùng **Java 17+, Spring Boot 3.5.7 (Spring Framework 6.x)** và Maven. Nó cho thấy controller trả `202` nhanh trong khi worker chờ 3 giây để mô phỏng xử lý. Để dễ quan sát, trạng thái chỉ lưu trong `ConcurrentHashMap` **trong bộ nhớ**; đây là bản demo, không phải triển khai chịu lỗi production.

Cấu trúc dự án:

```text
async-jobs-demo/
├── pom.xml
└── src/main/java/com/example/asyncjobs/
    ├── JobDemoApplication.java
    ├── JobStore.java
    ├── JobWorker.java
    └── JobController.java
```

**`pom.xml`**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.7</version>
        <relativePath/>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>async-jobs-demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
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

**`src/main/java/com/example/asyncjobs/JobDemoApplication.java`**

```java
package com.example.asyncjobs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
@EnableAsync
public class JobDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobDemoApplication.class, args);
    }

    @Bean("jobExecutor")
    public ThreadPoolTaskExecutor jobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("job-");
        return executor;
    }
}
```

`@EnableAsync` kích hoạt cơ chế xử lý `@Async`. Bean `jobExecutor` là nhóm luồng **có giới hạn**: tối đa 4 worker và một hàng chờ giới hạn 16 phần tử. Khi hàng chờ đầy và đạt giới hạn luồng, executor từ chối tác vụ; ví dụ xử lý tình huống đó ở controller. Đây là executor chạy **bên trong JVM**.

**`src/main/java/com/example/asyncjobs/JobStore.java`**

```java
package com.example.asyncjobs;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class JobStore {
    private final ConcurrentHashMap<UUID, String> states = new ConcurrentHashMap<>();

    public void create(UUID id) {
        states.put(id, "QUEUED");
    }

    public void update(UUID id, String state) {
        states.put(id, state);
    }

    public String find(UUID id) {
        return states.get(id);
    }

    public void remove(UUID id) {
        states.remove(id);
    }
}
```

`ConcurrentHashMap` cho phép luồng request và worker truy cập an toàn tới từng thao tác trên map. Nó **không** làm job bền vững sau restart và không thay thế cơ sở dữ liệu.

**`src/main/java/com/example/asyncjobs/JobWorker.java`**

```java
package com.example.asyncjobs;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class JobWorker {
    private final JobStore store;

    public JobWorker(JobStore store) {
        this.store = store;
    }

    @Async("jobExecutor")
    public void start(UUID id) {
        store.update(id, "RUNNING");
        try {
            // Mô phỏng xử lý mất 3 giây; chỉ worker bị chờ.
            TimeUnit.SECONDS.sleep(3);
            store.update(id, "SUCCEEDED");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            store.update(id, "FAILED");
        } catch (RuntimeException ex) {
            // Trong thực tế cần ghi log, mã lỗi và thông tin chẩn đoán.
            store.update(id, "FAILED");
        }
    }
}
```

Lệnh gọi `worker.start(id)` đi qua **Spring proxy** nên phương thức chạy trên thread của `jobExecutor`. `TimeUnit.SECONDS.sleep(3)` chỉ dùng minh họa tác vụ dài và **không** chạy trên thread HTTP. Trường hợp thất bại cập nhật `FAILED`; với phương thức `@Async void`, exception không tự quay về HTTP response đã gửi.

**`src/main/java/com/example/asyncjobs/JobController.java`**

```java
package com.example.asyncjobs;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
public class JobController {
    private final JobStore store;
    private final JobWorker worker;

    public JobController(JobStore store, JobWorker worker) {
        this.store = store;
        this.worker = worker;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> submit() {
        UUID id = UUID.randomUUID();
        store.create(id);

        try {
            worker.start(id); // Giao việc cho executor qua Spring proxy.
        } catch (TaskRejectedException ex) {
            store.remove(id);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Executor is full"));
        }

        String path = "/jobs/" + id;
        return ResponseEntity.accepted()
                .location(URI.create(path))
                .body(Map.of("jobId", id.toString(), "statusUrl", path));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, String>> status(@PathVariable UUID id) {
        String state = store.find(id);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "jobId", id.toString(),
                "status", state
        ));
    }
}
```

Controller **không gọi** `get()`, `join()` hoặc đợi kết quả của worker. Sau khi executor nhận tác vụ, controller trả HTTP `202 Accepted` kèm `Location` để client tra cứu. Nếu executor hết chỗ, controller trả `503`, tránh thông báo nhận việc khi chưa thể giao việc. Trong hệ thống thật, phải xác thực, phân quyền và kiểm tra input trước khi chấp nhận; ở đây cố ý không có payload để tập trung vào mốc thời gian.

Chạy dự án (cài JDK 17+ và Maven):

```bash
mvn spring-boot:run

# Ở terminal khác:
curl -i -X POST http://localhost:8080/jobs
# Sao chép jobId từ response:
curl -i http://localhost:8080/jobs/<jobId>
```

Kết quả **minh họa** (UUID thay đổi theo mỗi lần gọi):

```http
HTTP/1.1 202
Location: /jobs/<jobId>

{"jobId":"<jobId>","statusUrl":"/jobs/<jobId>"}
```

Ngay sau POST, GET có thể thấy `QUEUED` hoặc `RUNNING`; khoảng 3 giây sau khi worker bắt đầu sẽ thường thấy `SUCCEEDED`. Nếu worker được lập lịch/chạy nhanh, trạng thái có thể thay đổi trước khi client GET lần đầu; **không** dựa vào thứ tự timing giả định. Không có stdout cố định vì thời điểm xử lý và UUID phụ thuộc lần chạy.

**Các điểm cần quan sát:** POST có thể hoàn tất trước 3 giây; endpoint GET trả trạng thái tại thời điểm được hỏi; thread `job-*` xử lý công việc thay cho thread HTTP; công việc có thể mất nếu JVM dừng.

## 5. Trường hợp chỉ muốn giải phóng thread HTTP, vẫn trả kết quả trong cùng response

Spring MVC có cơ chế async Servlet cho tình huống **client vẫn cần dữ liệu ở chính HTTP response đang mở**. Ví dụ phương thức bổ sung trong một `@RestController`:

```java
@GetMapping("/preview")
public java.util.concurrent.Callable<String> preview() {
    return () -> {
        java.util.concurrent.TimeUnit.SECONDS.sleep(2);
        return "PREVIEW_READY";
    };
}
```

Đây là **đoạn phương thức minh họa** cần đặt trong controller có `@GetMapping` hoạt động; nó không phải chương trình Java độc lập. Spring MVC nhận `Callable`, bắt đầu xử lý Servlet async, giải phóng thread Servlet ban đầu rồi chạy callable trên `AsyncTaskExecutor`. Sau khi callable hoàn tất, Spring dispatch lại để trả body `PREVIEW_READY`. Client vẫn phải đợi khoảng 2 giây để nhận response này.

`DeferredResult<T>` dùng khi kết quả được cung cấp bởi **một sự kiện bên ngoài** hoặc callback thay vì chạy trực tiếp một `Callable`. Ứng dụng giữ đối tượng `DeferredResult` và một luồng khác gọi `setResult` hoặc `setErrorResult`. Cần xử lý timeout và completion để không giữ yêu cầu treo mãi. `WebAsyncTask` cung cấp cách đặt timeout/executor riêng cho `Callable`.

Không nên dùng `Callable`/`DeferredResult` rồi kết luận “đã trả việc về nền”: kết nối HTTP vẫn gắn với việc chờ kết quả. Ngược lại, một endpoint `202` không cần giữ response mở nhưng phải cung cấp cách quan sát kết quả về sau.

## 6. Thiết kế production: nhận việc chắc chắn, thực thi có kiểm soát

Ví dụ trên dạy **cơ chế**, chưa bảo đảm độ bền. Với báo cáo lớn, gửi email quan trọng hoặc tác vụ có thể chạy hàng phút, hãy tách thành các thành phần:

```text
Client
  |
  | POST /jobs (+ Idempotency-Key nếu cần)
  v
HTTP API --validate/auth--> DB: INSERT job(status=QUEUED)
  |                                |
  | HTTP 202 + jobId               | Worker/broker nhận nhiệm vụ
  v                                v
Client --GET /jobs/{id}--> DB <-- Worker cập nhật RUNNING/SUCCEEDED/FAILED
```

Có hai triển khai phổ biến:

1. **DB làm job queue:** giao dịch lưu job `QUEUED`, worker truy vấn/claim job có khóa hoặc lease, cập nhật trạng thái. Cần xử lý claim hết hạn, phân chia job giữa nhiều instance, retry và crash.
2. **DB + message broker:** API ghi nhận ý định làm việc và phát message sang RabbitMQ/Kafka hoặc hệ tương đương; worker tiêu thụ. Nếu phải bảo đảm không mất job giữa thao tác commit DB và publish message, dùng **transactional outbox** (ghi job + outbox trong cùng giao dịch, publisher phát message sau commit). Consumer phải xử lý khả năng message được nhận lặp.

Các quyết định tối thiểu cần xác định:

- **Ranh giới chấp nhận:** xác thực, quyền truy cập, input hợp lệ, quota, ghi job/queue thành công rồi mới trả `202`. Nếu ghi thất bại, trả lỗi thay vì tạo mã job “ảo”.
- **Backpressure:** executor/queue có giới hạn; từ chối hoặc trì hoãn nhận job khi quá tải, không tạo thread không giới hạn. Tách metric **HTTP latency** với **queue wait time** và **job execution time**.
- **Idempotency:** client có thể gửi lại POST do timeout; nếu nghiệp vụ không được chạy hai lần, hỗ trợ idempotency key và ánh xạ khóa đó tới một job. Worker cũng cần idempotent khi retry/redelivery.
- **Retry và lỗi:** phân biệt lỗi tạm thời với lỗi vĩnh viễn, dùng backoff, giới hạn lần thử, lưu nguyên nhân và đưa trường hợp hết lượt retry vào `FAILED`/dead-letter theo hợp đồng.
- **Kết quả và quyền:** lưu output hoặc URL tải kết quả khi hoàn tất; kiểm tra quyền sở hữu job trên mọi lần GET/cancel/download, không để ai biết UUID cũng xem được dữ liệu.
- **Timeout và hủy:** timeout của HTTP không tự hủy job; cancellation cần trạng thái, tín hiệu worker và chính sách dọn tài nguyên riêng.
- **Quan sát:** có correlation/job ID trong log và trace, đo số job đang chờ, tuổi job lâu nhất, số worker đang chạy, tỷ lệ thất bại/thử lại, thời gian từ nhận đến hoàn tất.
- **Khả năng khôi phục:** sau restart phải nhận ra job `RUNNING` bị bỏ dở, có lease/heartbeat và cơ chế chạy lại hợp lệ; không dựa vào RAM.

**Lưu ý về giao dịch và context:** `@Async` đổi thread. Transaction của request không tự lan sang thread worker; `SecurityContext`, request attributes và MDC dựa trên `ThreadLocal` cũng không mặc nhiên đi theo. Worker nên nhận các dữ liệu cần thiết dưới dạng giá trị/ID tường minh và tự mở transaction khi cần. Nếu yêu cầu “chỉ bắt đầu sau DB commit”, dùng điểm kích hoạt sau commit thích hợp; nếu cần *bền vững* qua crash, dùng outbox/queue thay vì chỉ `@TransactionalEventListener` + `@Async`.

## 7. Lỗi thường gặp và cách tự kiểm tra

| Hiểu sai/cách làm sai | Hậu quả | Hướng xử lý |
| --- | --- | --- |
| Trả `200 OK` hoặc “completed” khi vừa giao việc | Client tưởng nghiệp vụ đã thành công | Trả `202`, mã job và endpoint trạng thái |
| Gọi `future.get()`, `join()` hoặc chờ worker trong controller | HTTP vẫn đợi công việc dài | Chỉ giao việc rồi trả; đọc trạng thái ở lần gọi riêng |
| Gọi phương thức `@Async` bằng `this.start()` trong cùng bean | Self-invocation bỏ qua proxy, công việc có thể chạy đồng bộ | Gọi thông qua Spring bean khác hoặc cơ chế scheduling rõ ràng |
| Cho rằng `@Async` tự có hàng đợi bền vững | Restart/crash làm mất việc, mất trạng thái RAM | DB/broker, retry, recovery |
| Dùng thread pool/queue không giới hạn hoặc `CallerRunsPolicy` không cân nhắc | Hết tài nguyên; `CallerRunsPolicy` có thể chạy job ngay trên thread HTTP | Đặt giới hạn, quan sát độ sâu queue, từ chối có chủ đích |
| Không xử lý exception trong tác vụ `@Async void` | HTTP đã trả; lỗi không được gửi về client | Bắt lỗi, cập nhật trạng thái, log và alert |
| Dùng `DeferredResult` mà quên timeout/cleanup | HTTP request treo, giữ tài nguyên | Timeout callback, error result, dọn listener |
| Dựa vào transaction/context của thread gọi | Đọc dữ liệu chưa commit, thiếu principal/trace | Truyền ID rõ ràng, transaction/context riêng |
| Retry vô điều kiện với thao tác tạo side effect | Gửi email/thu tiền/ghi dữ liệu hai lần | Idempotency, dedupe và chính sách retry |

**Câu hỏi tự kiểm tra:**

1. Nếu POST trả sau 20 ms nhưng worker mất 15 giây, HTTP status nào hợp lý và client biết kết quả qua đâu?
2. `Callable` trong Spring MVC giải phóng thread HTTP ban đầu; tại sao client vẫn phải chờ response?
3. Điều gì xảy ra với job lưu trong `ConcurrentHashMap` và executor khi tiến trình JVM tắt?
4. Tại sao việc gọi `@Async` trong cùng bean có thể chạy đồng bộ?
5. Khi queue đầy hoặc broker không nhận message, API có nên tiếp tục trả `202` không?

Đáp án định hướng: (1) `202` + job ID/status endpoint; (2) response HTTP vẫn mở đến lúc có kết quả; (3) job/trạng thái RAM không bền vững; (4) không đi qua Spring proxy; (5) không, trừ khi đã có một bản ghi bền vững bảo đảm job được xử lý sau.

## 8. Ghi nhớ

**Tách tiếp nhận khỏi hoàn thành** là quyết định về **vòng đời nghiệp vụ và hợp đồng với client**. Spring hỗ trợ nhiều cơ chế chạy bất đồng bộ, nhưng cần chọn đúng theo điều người dùng chờ đợi:

- Cần **kết quả trong cùng HTTP response**: cân nhắc Spring MVC `Callable`/`DeferredResult` với timeout và executor phù hợp.
- Cần **nhận việc nhanh, biết kết quả sau**: dùng `202 Accepted`, job ID và cơ chế tra cứu; `@Async` đủ cho demo/tác vụ nền chấp nhận rủi ro trong JVM.
- Cần **không mất việc, retry và mở rộng nhiều instance**: lưu job bền vững, queue/outbox/worker, idempotency và quan sát vận hành.

**Tài liệu chính thức:** [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html) · [Spring Framework – Asynchronous Requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html) · [HTTP Semantics RFC 9110, §15.3.3 – 202 Accepted](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.3.3)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-006">Những bảo đảm Spring cung cấp và trách nhiệm của mã nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Những bảo đảm Spring cung cấp và trách nhiệm của mã nghiệp vụ

> **Phạm vi:** Spring Framework – concurrency, phần **Tổng quan**. Bài học phân định chính xác đâu là cơ chế Spring quản lý, đâu là tính đúng đắn khi nhiều request, luồng, tác vụ và nhiều instance đồng thời thực thi mà mã nghiệp vụ phải tự bảo vệ.

## 1. Vì sao cần phân biệt?

Một ứng dụng Spring có thể tiếp nhận nhiều request HTTP, chạy tác vụ @Async, xử lý thông điệp, kích hoạt @Scheduled và thực thi giao dịch cùng lúc. Các lời gọi có thể truy cập cùng một bean singleton, cùng một hàng dữ liệu hoặc cùng một dịch vụ bên ngoài. Framework có thể tổ chức nơi thực thi nhưng không tự biết các bất biến như “tồn kho không được âm”, “một đơn không được thanh toán hai lần” hay “sự kiện phải được xử lý lại an toàn”.

Có ba tầng trách nhiệm cần phân tách:

1. **Điều phối:** nơi và thời điểm chạy, executor/scheduler, chuyển lời gọi qua Spring proxy, vòng đời của bean/tác vụ.
2. **An toàn truy cập:** dữ liệu chia sẻ, Java Memory Model, atomicity, locking, isolation và thứ tự cập nhật.
3. **Tính đúng đắn nghiệp vụ:** invariant, idempotency, khả năng phục hồi sau lỗi, duplicate, timeout và nhất quán giữa hệ thống.

Spring hỗ trợ tầng thứ nhất và cung cấp công cụ cho hai tầng còn lại. **Tính thread-safe và exactly-once không tự xuất hiện khi thêm annotation.**

## 2. Ma trận bảo đảm và trách nhiệm

| Cơ chế | Spring cung cấp | Mã nghiệp vụ cần bảo đảm |
| --- | --- | --- |
| IoC, DI và bean scope | Tạo, nối dependency, quản lý lifecycle; singleton mặc định là một bean instance cho mỗi bean definition trong một container | Trạng thái của bean và dependency được chia sẻ phải thread-safe hoặc stateless |
| TaskExecutor, @Async | Gửi lời gọi qua proxy tới executor đã chọn và trả Future/CompletableFuture nếu contract yêu cầu | Pool, queue, giới hạn tải, từ chối, lỗi, huỷ, timeout, shared state và kết quả cuối |
| TaskScheduler, @Scheduled | Kích hoạt tác vụ theo trigger trong runtime đã cấu hình | Chạy chồng, chạy trùng ở nhiều instance, bỏ lỡ lượt chạy, retry, idempotency và khôi phục |
| @Transactional (imperative) | Áp dụng transaction interceptor và transaction manager trên luồng đang thực thi khi lời gọi đi qua proxy | Transaction boundary, isolation, khóa/phiên bản, conflict và các tác dụng phụ ngoài DB |
| Request/session scope | Quản lý object theo lifetime của request/session khi web context hợp lệ | Không dùng object request sau khi request kết thúc; một session vẫn có thể được truy cập đồng thời |
| TaskDecorator và context-aware adapter | Cơ chế tùy biến việc mang một số execution context vào task | Chọn context hợp lệ để truyền, xóa ThreadLocal sau task, không rò dữ liệu giữa request |
| Lifecycle và shutdown | Hook khởi tạo/hủy, quản lý executor và scheduler, tùy chọn shutdown | Thời hạn chờ, hủy có kiểm soát, lưu trạng thái công việc dở dang, phục hồi sau crash |

Một cơ chế Spring chỉ có hợp đồng khi được **cấu hình và sử dụng đúng đường đi**. @Async không có ý nghĩa nếu annotation bị bỏ qua do self-invocation; @Transactional không tạo transaction cho lời gọi không đi qua interceptor của cấu hình proxy tương ứng.

## 3. Singleton bean có thể bị gọi đồng thời

Singleton là **cùng một object** được dùng lại trong một Spring IoC container, không phải một object riêng cho mỗi request. Ví dụ nguy hiểm:

~~~java
@Service
public class UnsafeCounterService {
    private int value;

    public int next() {
        return ++value;
    }
}
~~~

Đây là đoạn minh họa Spring (các import được bỏ để tập trung vào lỗi). Phép ++ là đọc–tính–ghi, nên hai luồng có thể cùng đọc một giá trị và ghi kết quả trùng. Bean được Spring tạo/inject **không** khiến thao tác này atomic.

Thực hành đúng theo loại dữ liệu:

- **Stateless singleton:** field chủ yếu là dependency hoặc cấu hình immutable; dùng local variables và input/output riêng từng lời gọi. Constructor injection giúp kiểm soát các dependency.
- **Mutable state nội bộ một JVM:** dùng AtomicInteger/AtomicLong, synchronized/Lock, ConcurrentHashMap hay primitive JDK khác theo bất biến cụ thể. volatile hỗ trợ visibility/ordering nhưng **không** biến phép tăng hay chuỗi “nếu chưa có thì thêm” thành một thao tác atomic.
- **Trạng thái bền vững chia sẻ nhiều replica:** đặt invariant ở datastore với transaction, constraint, update có điều kiện, optimistic/pessimistic locking, unique key; khóa trong JVM không bảo vệ các instance khác.
- **Scope khác chưa chắc giải quyết:** prototype tạo object khi được yêu cầu; một prototype được inject một lần vào singleton vẫn có thể bị giữ và chia sẻ. Request scope tách instance giữa request nhưng không biến session/application scope thành thread-safe.

### Ví dụ Java 17+ độc lập: cập nhật atomic

Lưu thành **AtomicCounterDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounterDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger counter = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tasks.add(executor.submit(() -> {
                    for (int j = 0; j < 1_000; j++) {
                        counter.incrementAndGet();
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
            System.out.println("Total = " + counter.get());
        } finally {
            executor.shutdown();
        }
    }
}
~~~

~~~bash
javac AtomicCounterDemo.java
java AtomicCounterDemo
~~~

Khi mọi Future hoàn thành thành công, stdout chính xác là:

~~~text
Total = 8000
~~~

Tám task nhân 1.000 phép incrementAndGet atomic tạo 8.000, còn get() chờ tất cả hoàn tất trước khi in. Đây chỉ là **an toàn bộ đếm trong một process**, không phải giải pháp cho tồn kho hoặc số dư lưu trên DB và được nhiều instance cập nhật.

## 4. Điều mà @Async thực sự thực hiện

Khi @EnableAsync (hoặc cấu hình tương đương) có hiệu lực và caller đi qua Spring proxy, @Async nộp công việc cho TaskExecutor phù hợp. Spring lo phần interception và điều phối; executor quyết định thực thi bằng thread pool, virtual thread hoặc cơ chế khác. Một TaskExecutor thậm chí có thể chạy đồng bộ tùy implementation; không được suy ra “mọi @Async luôn tạo thread mới”.

- Lời gọi **this.asyncMethod()** trong cùng instance bỏ qua Spring proxy ở proxy mode mặc định, làm async advice không chạy. Hãy đặt ranh giới gọi giữa các bean hoặc thiết kế để caller dùng bean được Spring inject.
- @Async không tự bảo đảm thứ tự task bắt đầu, thứ tự task kết thúc, bounded queue, timeout, retry hay durable completion. Từng thứ phụ thuộc executor và thiết kế.
- @Async trả Future/CompletableFuture cho phép quan sát kết quả/thất bại bất đồng bộ; get()/join() có thể ném exception dạng wrapper. Với @Async trả void, lỗi không quay về stack của caller; cần AsyncUncaughtExceptionHandler hoặc contract trả Future phù hợp. Lỗi **nộp task** khi pool bị từ chối khác với lỗi **trong task**.
- Một invocation @Async không có nghĩa là được giữ transaction, security context, request context hoặc MDC của caller sang worker thread.

### Ví dụ tích hợp Spring: giới hạn executor, transaction riêng tại worker

Ví dụ này dùng **Java 17+, Spring Framework, spring-jdbc, transaction manager và một DB có bảng inventory(product_id, available)**. Đặt toàn bộ snippet trong tệp **BusinessAsyncConfig.java** thuộc package được component scan; cần ApplicationContext/DataSource/schema/transaction management để thực thi. Đây là ví dụ tích hợp, **không phải** chương trình javac/java độc lập.

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@EnableAsync
public class BusinessAsyncConfig {
    @Bean("businessExecutor")
    public ThreadPoolTaskExecutor businessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("business-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}

record ReservationCommand(long productId, int quantity) {
    ReservationCommand {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}

@Service
class ReservationAsyncService {
    private final ReservationTxService transactionService;

    ReservationAsyncService(ReservationTxService transactionService) {
        this.transactionService = transactionService;
    }

    @Async("businessExecutor")
    public CompletableFuture<Boolean> reserveAsync(ReservationCommand command) {
        boolean reserved = transactionService.reserve(command);
        return CompletableFuture.completedFuture(reserved);
    }
}

@Service
class ReservationTxService {
    private final JdbcTemplate jdbc;

    ReservationTxService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public boolean reserve(ReservationCommand command) {
        int updatedRows = jdbc.update(
            "UPDATE inventory SET available = available - ? " +
            "WHERE product_id = ? AND available >= ?",
            command.quantity(), command.productId(), command.quantity()
        );
        return updatedRows == 1;
    }
}
~~~

Phân tích đúng từng ranh giới:

1. **Invocation:** caller từ một bean khác gọi ReservationAsyncService qua proxy; @Async đưa task vào businessExecutor. Self-invocation sẽ không kích hoạt proxy advice.
2. **Dữ liệu đầu vào:** record ReservationCommand bất biến; không truyền EntityManager, HttpServletRequest, connection hoặc object session mutable sang worker.
3. **Transaction:** trong worker, AsyncService gọi **bean khác** là ReservationTxService. @Transactional có hiệu lực qua transaction proxy và mở/tham gia giao dịch theo transaction manager **trên worker thread**; không kế thừa transaction từ HTTP caller.
4. **Bất biến DB:** SQL UPDATE kiểm tra available >= quantity trong cùng statement cập nhật. Khi không thỏa, cập nhật 0 dòng. Các bảo đảm thực tế phụ thuộc DB, schema, constraint và transaction manager. Cần thêm idempotency key để tránh trừ lại nếu cùng command bị retry.
5. **Tài nguyên:** pool cấu hình 4 core, 8 max và queue 100; task vượt sức chứa có thể bị từ chối. Đây là **số liệu minh họa**, cần đo throughput/connection pool/downstream latency trước khi chọn cho sản phẩm. Mã gọi cần chính sách xử lý rejection.
6. **Shutdown:** cho phép chờ task trong giới hạn cấu hình 30 giây; crash, kill hoặc mất điện vẫn có thể làm job chưa xong. Khi yêu cầu bền vững, cần lưu command vào datastore/queue đáng tin cậy.

## 5. Transaction không tự vượt ranh giới thread

Với **imperative transaction** dùng PlatformTransactionManager, Spring thường ràng buộc transaction và resource vào luồng hiện tại. Tạo luồng mới bằng @Async, ExecutorService, CompletableFuture.supplyAsync hoặc new Thread không tự truyền transaction của caller.

~~~text
Thread A (HTTP request)                       Thread B (@Async worker)
@Transaction đặt đơn hàng
  ghi đơn (chưa commit)
  gửi async task ---------------------------> xử lý tiếp, không ở transaction A
  commit / rollback A                        cần transaction riêng khi ghi DB
~~~

Hệ quả:

- Worker có thể chạy **trước commit**. Nó có thể không đọc thấy bản ghi của A hoặc gửi thông báo cho một đơn hàng sau đó bị rollback.
- Nếu worker cần transaction, hãy mở transaction ở worker qua Spring-managed service/interceptor hoặc TransactionTemplate.
- @Transactional không tự ngăn lost update, deadlock hay race ngoài DB; mức isolation mặc định tùy data source/manager. Cần kiểm soát version, lock, unique constraint và retry conflict có giới hạn.
- Email, HTTP call, file và message broker không mặc nhiên nằm chung transaction với DB. Khi cần chuyển trạng thái DB và phát sự kiện bền vững, cân nhắc **transactional outbox** rồi xử lý consumer **idempotent**.
- Với **ReactiveTransactionManager**, ngữ cảnh giao dịch nằm trong Reactor Context, không theo quy tắc ThreadLocal của imperative transaction. Không áp nhầm mô hình giữa hai stack.

## 6. Ngữ cảnh luồng và request không tự được sao chép

RequestContextHolder, SecurityContextHolder theo chiến lược ThreadLocal mặc định, logging MDC, locale và một số resource transaction phụ thuộc context của luồng. Worker thread có thể khác thread gọi và thường được tái sử dụng. Không được giả định task tự nhìn thấy principal, correlation ID, locale hay request object của caller.

- Truyền **tường minh** qua command immutable các dữ liệu nghiệp vụ đã xác thực: orderId, tenantId, correlationId cần thiết.
- Để lan truyền security/tracing/logging context khi thật sự cần, dùng adapter phù hợp, ví dụ security-aware executor, TaskDecorator hoặc cơ chế context propagation tương thích với runtime. Chỉ truyền dữ liệu cho phép; đảm bảo khôi phục/dọn ThreadLocal trong finally để task sau không nhận dữ liệu của task trước.
- Tránh mang request/response/session object sống vào tác vụ kéo dài hơn request. Truyền userId đã kiểm tra cũng không thay thế việc kiểm tra quyền ở thời điểm công việc được thực thi.
- InheritableThreadLocal không phải cách chung để truyền context vào thread pool: pool thường tái sử dụng thread đã được tạo từ trước.

## 7. Scheduler và message listener không bảo đảm exactly-once

@Scheduled/fixedRate/fixedDelay/cron giúp kích hoạt một phương thức trong runtime; nó **không** tự lưu lịch bền vững, khôi phục mọi lượt bị bỏ lỡ hoặc đảm bảo chỉ một JVM trong cluster chạy. Có thể có thực thi chồng lấn tùy cấu hình lịch/executor và bản thân một job có thể được đăng ký ở nhiều instance.

Với job quét đơn hết hạn, luôn hỏi:

1. Nếu job lâu hơn chu kỳ, cơ chế hiện tại có thể chồng lấn hay không? Hành vi của fixedRate, fixedDelay và scheduler được cấu hình thực tế là gì?
2. Khi triển khai ba replica, có ba bản sao cùng quét dữ liệu không?
3. Nếu process chết giữa job, checkpoint nào cho phép tiếp tục?
4. Nếu cùng item được xử lý hai lần, tác động có idempotent không?
5. Có giới hạn batch, timeout, lock/lease, retry hoặc dead-letter handling phù hợp không?

Nếu cần một job duy nhất toàn cluster, dùng lease/lock có vòng đời rõ ràng, scheduler hỗ trợ clustering, hoặc queue/job store thích hợp; local @Scheduled không tự bầu leader. Với message listener, số consumer, thứ tự theo key/partition, ack và redelivery phụ thuộc broker/adapter/configuration. Consumer phải xử lý duplicate, poison message và lỗi một phần theo hợp đồng thực tế.

## 8. Chọn cơ chế bảo vệ đúng bất biến

Ví dụ bất biến: **tồn kho không được âm**.

| Kỹ thuật | Phạm vi hữu ích | Giới hạn |
| --- | --- | --- |
| Immutable input, biến local | Tránh race do chia sẻ biến giữa những request | Không ngăn hai DB transaction cùng cập nhật |
| AtomicInteger/synchronized | Một giá trị hoặc critical section trong **một JVM** | Không đồng bộ nhiều replica |
| ConcurrentHashMap | Các thao tác collection được API bảo đảm; compute/merge có thể hỗ trợ cập nhật nguyên tử theo key | Không biến quy trình nhiều bước/đa tài nguyên thành một transaction |
| SQL UPDATE có điều kiện | Điều kiện kiểm tra và cập nhật cùng statement trong DB | Không tự chống retry trùng logic của cùng operation |
| Unique constraint + idempotency key | Chặn command/order trùng theo khóa nghiệp vụ | Cần thiết kế response, hiệu lực khóa và trạng thái retry |
| Optimistic locking/version | Phát hiện concurrent update conflict | Caller phải xử lý exception/retry có giới hạn |
| Transactional outbox + queue | Ghi ý định phát sự kiện cùng DB transaction, phục hồi tiến trình phát | Consumer vẫn có thể nhận lặp và phải idempotent |

**Nguyên tắc:** Bất biến phải được bảo vệ ở tầng có quyền kiểm soát tất cả các bên cập nhật nó. Mutex trong một Spring singleton không bảo vệ tồn kho khi hai replica cùng ghi một DB. Transaction DB không tự làm cuộc gọi gửi email trở thành atomic với câu lệnh UPDATE.

## 9. Giới hạn tài nguyên và khi bị quá tải

Concurrency làm tăng khả năng xử lý nhiều công việc chờ I/O, nhưng cũng dùng CPU, memory, connection pool, lock và quota của dịch vụ bên ngoài. MaxPoolSize lớn không khắc phục được DB pool nhỏ hoặc API bên ngoài đã đến giới hạn.

- **Pool và queue:** giới hạn rõ; queue dài có thể tăng latency và chiếm memory. Với ThreadPoolExecutor, cách queue nhận task ảnh hưởng thời điểm executor tăng số worker đến max; hiểu cấu hình queue trước khi tăng maxPoolSize.
- **Backpressure/rejection:** xác định cách caller xử lý khi không nhận thêm task: trả trạng thái quá tải, retry có backoff/jitter, hay ghi vào durable queue; tránh retry vô hạn.
- **Timeout và cancel:** đặt deadline cho I/O, DB và tác vụ; hủy Future không tự đảo ngược các tác dụng phụ đã hoàn thành. Đóng resource bằng finally/try-with-resources.
- **Shutdown:** dừng nhận việc, chờ trong giới hạn, lưu checkpoint, tái xử lý việc dở. Graceful shutdown không giải quyết sự cố đột ngột.
- **Virtual threads:** có thể giảm overhead của nhiều công việc blocking tương thích, nhưng không biến shared mutable state thành thread-safe, cũng không làm tăng số connection DB hoặc quota remote API.

Theo dõi active task, queue depth, rejection count, queue wait time, task duration, timeout, retry, downstream latency, DB connection pool usage. Những số liệu này giúp lựa chọn chính sách có bằng chứng thay vì suy diễn từ annotation.

## 10. Tình huống trước và sau

### A. Bộ đếm của dịch vụ singleton

**Trước:** int total++ trong @Service; hai request có thể làm mất lần cộng. @Transactional không chữa race của Java field.

**Sau:** nếu chỉ là metric nội bộ một JVM, dùng AtomicLong hoặc metrics library; nếu là điểm thưởng lưu bền và được nhiều replica cập nhật, đặt điều kiện/constraint và logic cập nhật trong DB, xử lý optimistic conflict/retry theo invariant.

### B. Đặt hàng rồi gửi email @Async

**Trước:** request tạo đơn trong transaction A rồi gọi @Async gửi email. Worker có thể gửi email trước khi A commit; A rollback vẫn để lại email.

**Sau:** ghi sự kiện “OrderCommitted” vào transactional outbox cùng transaction A; worker chỉ đọc sự kiện đã commit, gửi email rồi lưu trạng thái. Nếu worker crash sau khi gửi nhưng trước khi đánh dấu, sự kiện có thể được chạy lại: phải có idempotency key/cơ chế dedupe hoặc chấp nhận chính sách gửi trùng đã xác định. Spring cung cấp thành phần lập trình, còn quy tắc đúng của hệ thống do nghiệp vụ quyết định.

## 11. Kiểm chứng bằng concurrency và failure test

1. Đọc cấu hình thật: số bean, bean scope, executor được dùng, pool/queue/rejection, scheduler, proxy, transaction manager, số instance và shutdown policy.
2. Dùng CountDownLatch/CyclicBarrier để tạo tình huống hai hoặc nhiều worker cùng tranh chấp; assert invariant, đừng chỉ dùng Thread.sleep và hi vọng race xuất hiện.
3. Kiểm thử tích hợp với **transaction và connection DB thật**, nhiều caller song song; xác minh không âm tồn kho, không tạo trùng order và xử lý optimistic conflict/deadlock.
4. Giả lập rollback caller, executor rejection, timeout, crash/restart, message redelivery và retry trùng; kiểm tra kết quả cuối cùng cùng trạng thái phục hồi.
5. Kiểm tra context được mang đúng đối tượng, không lộ giữa task trong thread pool; log correlation ID và metric saturation quan sát được.

## 12. Checklist thiết kế

- Tôi đã nêu chính xác invariant nghiệp vụ và dữ liệu nào có thể bị nhiều luồng/instance cập nhật.
- Bean singleton là stateless hoặc bảo vệ mọi shared mutable state theo mô hình concurrency đã chọn.
- Phạm vi bảo vệ nằm ở tầng đúng: JVM, DB, queue hoặc coordinator phân tán.
- @Async và @Transactional thực sự đi qua proxy; không phụ thuộc self-invocation.
- Worker có transaction và dữ liệu đầu vào riêng; không dùng nhầm transaction/request context của caller.
- Executor/scheduler có giới hạn, chính sách thất bại và vận hành khi restart/shutdown.
- Công việc có thể retry/duplicate được thiết kế idempotent khi cần.
- Concurrency test, failure test và metric xác nhận được các giả định trên.

**Ghi nhớ:** Spring quản lý **cách bean và tác vụ được tạo, gọi và thực thi theo cấu hình**. Mã nghiệp vụ phải bảo đảm **kết quả chính xác, nhất quán và có thể phục hồi** trước tranh chấp, quá tải và lỗi. Không có một annotation nào thay thế được thiết kế bất biến.

## Tài liệu tham khảo chính thức

- Spring Framework – Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework – Bean Scopes: https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html
- Spring Framework – Declarative Transaction Implementation: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html
- Spring Framework – Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Java Language Specification 17, Chapter 17 – Threads and Locks: https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-007">Ví dụ xuyên suốt: xử lý báo cáo và cập nhật dữ liệu định kỳ</a>

<details>
<summary>Xem chi tiết</summary>

# Ví dụ xuyên suốt: xử lý báo cáo và cập nhật dữ liệu định kỳ

## 1. Bài toán và mục tiêu

Giả sử một dịch vụ Spring nhận yêu cầu tạo báo cáo doanh thu/tổng hợp qua HTTP. Việc tổng hợp có thể mất vài giây hoặc vài phút; đồng thời ứng dụng phải cập nhật số liệu nền theo chu kỳ. Nếu xử lý báo cáo ngay trên luồng HTTP, người dùng phải giữ kết nối lâu và số luồng phục vụ request có thể bị chiếm hết. Nếu tự tạo luồng mới cho mỗi request, số luồng và bộ nhớ dễ tăng ngoài kiểm soát.

Ta tách hai loại công việc:

1. **Báo cáo theo yêu cầu:** `POST /reports` tạo một mã công việc, trả `202 Accepted` sớm; một `TaskExecutor` có giới hạn luồng xử lý ở nền. `GET /reports/{id}` trả trạng thái `PENDING`, `RUNNING`, `DONE` hoặc `FAILED`.
2. **Cập nhật dữ liệu định kỳ:** `TaskScheduler` kích hoạt một phương thức `@Scheduled`, cập nhật bảng số liệu mỗi 30 giây sau khi lần trước kết thúc. API `GET /metrics` cho phép quan sát giá trị hiện tại.

Trong bản demo, dữ liệu là một bộ đếm trong H2 và báo cáo đọc bộ đếm rồi giả lập bước tổng hợp lâu. Đây là mô hình tối giản để thấy luồng thực thi và thời điểm đọc dữ liệu; thực tế có thể thay SQL bằng truy vấn tổng hợp, xuất CSV/PDF, đọc nguồn ngoài hoặc lưu kết quả lên object storage.

~~~mermaid
flowchart LR
    U[Client] -->|POST /reports| C[ReportController]
    C -->|lưu PENDING| R[ReportStore]
    C -->|gọi bean khác| E[reportExecutor]
    E --> W[ReportWorker]
    W -->|đọc số liệu| DB[(H2 metrics)]
    W -->|RUNNING / DONE / FAILED| R
    U -->|GET /reports/id| C
    S[taskScheduler] -->|mỗi 30s sau khi hoàn tất| J[MetricJob]
    J -->|UPDATE atomic| DB
    U -->|GET /metrics| DB
~~~

**Hai khái niệm khác nhau:** `TaskExecutor` quyết định *ở đâu và với bao nhiêu luồng* một tác vụ được chạy; `TaskScheduler` quyết định *khi nào* tác vụ được kích hoạt. `@Async` thường gửi công việc đến executor; `@Scheduled` đăng ký lịch với scheduler. Không có cơ chế nào trong hai cơ chế này tự bảo đảm lưu bền vững công việc khi tiến trình bị tắt.

## 2. Tạo ứng dụng chạy được (Java 17, Spring Boot 3.3.5)

Dùng Spring Boot để khởi tạo ứng dụng, web server và kết nối H2; các annotation và executor/scheduler được minh họa là chức năng của **Spring Framework**. Cấu trúc:

~~~text
spring-concurrency-demo/
├── pom.xml
└── src/main/
    ├── java/com/example/demo/
    │   ├── DemoApplication.java
    │   ├── ReportStatus.java
    │   ├── ReportStore.java
    │   ├── ReportWorker.java
    │   ├── ReportController.java
    │   ├── MetricJob.java
    │   └── MetricsController.java
    └── resources/
        ├── schema.sql
        └── data.sql
~~~

### 2.1. `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>spring-concurrency-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
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
~~~

### 2.2. Bật scheduling/async và cấu hình hai pool

`src/main/java/com/example/demo/DemoApplication.java`:

~~~java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean("reportExecutor")
    ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("report-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }

    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("schedule-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }
}
~~~

`reportExecutor` có 2 luồng lõi, tối đa 4 luồng, hàng đợi tối đa 20 công việc; khi đầy có thể từ chối yêu cầu mới. Với cấu hình `ThreadPoolExecutor` thông thường, các luồng vượt số lõi chỉ được tạo khi hàng đợi đã đầy, vì thế `maxPoolSize` không có nghĩa lúc nào cũng có 4 luồng. `taskScheduler` là pool riêng cho tác vụ định kỳ, tránh để việc tạo báo cáo chiếm luồng đặt lịch. Tên bean `taskScheduler` cho phép cơ chế `@Scheduled` tìm scheduler này.

### 2.3. Khởi tạo dữ liệu

`src/main/resources/schema.sql`:

~~~sql
CREATE TABLE metrics (
    id INTEGER PRIMARY KEY,
    total BIGINT NOT NULL
);
~~~

`src/main/resources/data.sql`:

~~~sql
INSERT INTO metrics (id, total) VALUES (1, 0);
~~~

Spring Boot tự khởi tạo các script SQL này khi dùng H2 nhúng theo cấu hình mặc định. Mỗi lần khởi động mới với H2 in-memory, dữ liệu demo có thể bắt đầu lại từ 0. Cập nhật `total = total + 1` thực hiện ngay trong SQL, tránh kiểu đọc số cũ rồi ghi lại dễ mất cập nhật khi có nhiều luồng.

### 2.4. Kho trạng thái báo cáo

`src/main/java/com/example/demo/ReportStatus.java`:

~~~java
package com.example.demo;

import java.util.UUID;

public record ReportStatus(UUID id, String status, Long total, String error) {
}
~~~

`src/main/java/com/example/demo/ReportStore.java`:

~~~java
package com.example.demo;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class ReportStore {
    private final ConcurrentMap<UUID, ReportStatus> jobs = new ConcurrentHashMap<>();

    public void pending(UUID id) {
        jobs.put(id, new ReportStatus(id, "PENDING", null, null));
    }

    public void running(UUID id) {
        jobs.put(id, new ReportStatus(id, "RUNNING", null, null));
    }

    public void done(UUID id, long total) {
        jobs.put(id, new ReportStatus(id, "DONE", total, null));
    }

    public void failed(UUID id, String error) {
        jobs.put(id, new ReportStatus(id, "FAILED", null, error));
    }

    public ReportStatus find(UUID id) {
        return jobs.get(id);
    }
}
~~~

`ConcurrentHashMap` cho phép các luồng HTTP và luồng worker truy cập đồng thời; mỗi `ReportStatus` là record bất biến nên các lần cập nhật thay thế nguyên giá trị. Đây chỉ là trạng thái trong RAM: restart sẽ mất công việc và trạng thái; hệ thống thật nên lưu ở bảng `report_jobs` hoặc kho lưu bền vững, có chính sách giữ/xóa trạng thái.

### 2.5. Worker chạy bất đồng bộ

`src/main/java/com/example/demo/ReportWorker.java`:

~~~java
package com.example.demo;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportWorker {
    private final JdbcTemplate jdbc;
    private final ReportStore store;

    public ReportWorker(JdbcTemplate jdbc, ReportStore store) {
        this.jdbc = jdbc;
        this.store = store;
    }

    @Async("reportExecutor")
    public void generate(UUID id) {
        store.running(id);
        try {
            Long total = jdbc.queryForObject(
                    "SELECT total FROM metrics WHERE id = 1", Long.class);
            if (total == null) {
                throw new IllegalStateException("Không tìm thấy dữ liệu");
            }

            // Chỉ giả lập bước tổng hợp/tạo file mất thời gian.
            Thread.sleep(1500);
            store.done(id, total);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            store.failed(id, "Công việc bị ngắt khi dừng ứng dụng");
        } catch (Exception ex) {
            store.failed(id, "Không thể tạo báo cáo");
        }
    }
}
~~~

`@Async("reportExecutor")` chỉ có hiệu lực khi gọi phương thức qua Spring proxy, trong ví dụ là **ReportController gọi bean ReportWorker khác**. Nếu gọi `this.generate(id)` từ một phương thức cùng class, lời gọi nội bộ sẽ bỏ qua proxy và thường chạy đồng bộ. Không truyền `HttpServletRequest`, `EntityManager` hay tài nguyên request đang đóng sang luồng nền; chuyển ID/dữ liệu đầu vào cần thiết rồi mở tài nguyên ở worker. `Thread.sleep` chỉ là mô phỏng, không phải kỹ thuật xử lý báo cáo.

Nếu muốn lấy kết quả qua API Java (thay vì tra trạng thái bằng HTTP), phương thức `@Async` có thể trả `CompletableFuture<T>`. Không gọi `join()` hay `get()` ngay trên luồng HTTP nếu mục tiêu là trả phản hồi nhanh.

### 2.6. HTTP API trả về mã công việc

`src/main/java/com/example/demo/ReportController.java`:

~~~java
package com.example.demo;

import java.net.URI;
import java.util.UUID;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
public class ReportController {
    private final ReportStore store;
    private final ReportWorker worker;

    public ReportController(ReportStore store, ReportWorker worker) {
        this.store = store;
        this.worker = worker;
    }

    @PostMapping
    public ResponseEntity<ReportStatus> submit() {
        UUID id = UUID.randomUUID();
        store.pending(id);
        try {
            worker.generate(id);
        } catch (TaskRejectedException ex) {
            store.failed(id, "Hàng đợi đang đầy, hãy thử lại");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(store.find(id));
        }
        return ResponseEntity.accepted()
                .location(URI.create("/reports/" + id))
                .body(store.find(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportStatus> status(@PathVariable UUID id) {
        ReportStatus result = store.find(id);
        return result == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(result);
    }
}
~~~

`202 Accepted` xác nhận yêu cầu đã được nhận để xử lý, chưa cam kết báo cáo thành công. Header `Location` trỏ đến tài nguyên trạng thái. Tại thời điểm phản hồi, `status` có thể đã là `PENDING` hoặc `RUNNING`, thậm chí `DONE` nếu công việc hoàn thành rất nhanh: không được viết test giả định chính xác một trạng thái tức thời. Nếu executor từ chối nhận vì đầy, API trả `503 Service Unavailable`.

### 2.7. Cập nhật định kỳ và API xem số liệu

`src/main/java/com/example/demo/MetricJob.java`:

~~~java
package com.example.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MetricJob {
    private final JdbcTemplate jdbc;

    public MetricJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(initialDelay = 5_000, fixedDelay = 30_000)
    public void refresh() {
        int rows = jdbc.update(
                "UPDATE metrics SET total = total + 1 WHERE id = 1");
        if (rows != 1) {
            throw new IllegalStateException("Thiếu dòng metrics id=1");
        }
    }
}
~~~

`src/main/java/com/example/demo/MetricsController.java`:

~~~java
package com.example.demo;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {
    private final JdbcTemplate jdbc;

    public MetricsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/metrics")
    public Map<String, Long> current() {
        Long total = jdbc.queryForObject(
                "SELECT total FROM metrics WHERE id = 1", Long.class);
        return Map.of("total", total);
    }
}
~~~

Sau khi ứng dụng chạy khoảng 5 giây, `MetricJob` bắt đầu lần đầu. Với `fixedDelay = 30_000`, lần tiếp theo bắt đầu ít nhất 30 giây *sau khi lần trước kết thúc* (có thể trễ vì hệ thống bận). Bộ đếm tăng 1 mỗi lượt thành công; nếu một báo cáo đọc trước lúc cập nhật, nó có thể phản ánh giá trị cũ. Đó là ảnh chụp dữ liệu tại thời điểm đọc, không phải lỗi đồng bộ.

## 3. Chạy thử luồng xử lý

Cần JDK 17+ và Maven. Tại thư mục chứa `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

Trong terminal khác:

~~~bash
curl -s http://localhost:8080/metrics
curl -i -X POST http://localhost:8080/reports
# Lấy id trong JSON phản hồi POST, rồi thay UUID_THUC bằng giá trị đó:
curl -s http://localhost:8080/reports/UUID_THUC
~~~

Ví dụ `GET /metrics` lúc mới chạy trả `{"total":0}`. Sau lần cập nhật đầu, số này trở thành 1, sau đó tăng tiếp khi các lượt chạy thành công. `POST /reports` trả HTTP 202 và một JSON dạng:

~~~json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "total": null,
  "error": null
}
~~~

UUID trên **chỉ minh họa**; trạng thái ban đầu có thể khác do xử lý song song. Dùng UUID thực tế do POST trả về để gọi GET. Sau khoảng 1,5 giây tính từ khi worker bắt đầu, phản hồi có dạng:

~~~json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DONE",
  "total": 1,
  "error": null
}
~~~

Giá trị `total` phụ thuộc thời điểm worker đọc bảng, không cố định là 1. Để quan sát giới hạn hàng đợi, gửi đồng thời nhiều POST; khi executor quá tải, một số request có thể nhận 503. Khi ứng dụng bị tắt/khởi động lại, H2 in-memory và `ReportStore` mất dữ liệu demo.

## 4. Tại sao thiết kế này không làm nghẽn HTTP?

- **Luồng request:** tạo UUID và trạng thái `PENDING`, gửi nhiệm vụ vào pool, trả 202 nhanh; không chờ `Thread.sleep`.
- **Luồng report:** đánh dấu `RUNNING`, đọc DB, thực hiện bước chậm, lưu `DONE`/`FAILED`; số tác vụ hoạt động đồng thời bị giới hạn bởi pool.
- **Luồng scheduler:** độc lập với report pool; gọi thao tác cập nhật nhỏ, rồi đăng ký lượt tiếp theo.
- **Trạng thái chia sẻ:** `ConcurrentHashMap` an toàn cho các thao tác `put/get` riêng lẻ; nếu cần nhiều bước cập nhật trạng thái có điều kiện (ví dụ `PENDING -> RUNNING` một lần), phải dùng `compute`/CAS hoặc ràng buộc trạng thái trong DB.

Dù hai pool tách luồng, chúng vẫn dùng chung tài nguyên thật như connection pool, CPU, RAM và DB. Nếu 50 báo cáo đều chạy truy vấn nặng, tác vụ scheduled vẫn có thể chậm vì DB quá tải. Do đó phải đo thời gian, giới hạn truy cập DB và có cơ chế backpressure.

### So sánh cơ chế đặt lịch

| Cách | Ý nghĩa | Phù hợp |
| --- | --- | --- |
| `@Scheduled(fixedDelay = 30000)` | Chờ 30 giây sau khi lần trước *kết thúc* | Đồng bộ dữ liệu/polling không cần nhịp khởi động cố định |
| `@Scheduled(fixedRate = 30000)` | Nhắm thời điểm bắt đầu theo chu kỳ 30 giây | Công việc nhẹ, cần nhịp đều; nếu tác vụ chậm có thể bị trễ |
| `@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")` | Chạy theo lịch, ví dụ 02:00 mỗi ngày theo múi giờ xác định | Báo cáo cuối ngày, đóng sổ |

Đây là **ba lựa chọn thay thế** cho lịch của cùng một job, không đặt cả ba annotation lên phương thức demo. `fixedRate` không tự bảo đảm thực thi song song các lượt của cùng một job; lịch và pool còn ảnh hưởng đến hành vi. Nếu từ một lượt scheduled lại gửi công việc sang executor, các lần xử lý nền có thể chồng nhau, vì thế cần kiểm soát trùng lặp.

## 5. Chuyển demo thành thiết kế dùng trong sản xuất

### 5.1. Lưu bền vững và idempotency

Với báo cáo mất hàng phút, lưu mỗi job trong DB với các trường như `id`, `request_key`, `state`, `created_at`, `attempts`, `result_location`, `error_code`. Khi client gửi lại do timeout, dùng idempotency key hoặc ràng buộc unique để tránh tạo hai báo cáo giống nhau. Worker nên cập nhật có điều kiện, ví dụ chỉ một worker được chuyển `PENDING` sang `RUNNING`; lưu file báo cáo vào nơi dùng chung thay vì RAM của một instance.

Tác vụ định kỳ cần lưu watermark như `last_processed_at` hoặc `source_version`. Mỗi lượt chỉ đọc dữ liệu mới và `UPSERT` theo khóa nghiệp vụ; nếu retry, kết quả không bị cộng/nhân đôi sai. Câu SQL tăng bộ đếm trong demo **không idempotent**: gọi hai lần sẽ tăng hai lần, nên không dùng nguyên mẫu này cho dữ liệu tài chính thực.

### 5.2. Nhiều instance và transaction

`@Scheduled` được đăng ký **trong từng application context**. Nếu chạy 3 pod, mỗi pod có thể chạy job của chính nó: bảng chung có nguy cơ bị cập nhật 3 lần. Để bảo đảm một lượt logic chạy một lần trên toàn cụm, dùng khóa phân tán/DB lock, scheduler bên ngoài hoặc hệ thống hàng đợi có cơ chế nhận việc; thiết kế tiếp nhận retry vì sự cố có thể xảy ra giữa lúc làm việc và lúc ghi nhận hoàn tất.

`@Transactional` gắn với ngữ cảnh của luồng đang thực thi. Transaction của HTTP request **không tự truyền** vào worker `@Async`. Nếu cần giao dịch, mở transaction ở phương thức service được worker gọi trên luồng nền hoặc dùng `TransactionTemplate`; tránh giữ transaction suốt thời gian tạo file hay gọi API ngoài. Với JDBC demo, mỗi câu UPDATE tự commit qua kết nối mặc định khi không có transaction bao quanh.

### 5.3. Lỗi, tài nguyên, dừng ứng dụng

- `@Async void` không có Future trả về cho caller; worker nên bắt/ghi lỗi nghiệp vụ, cập nhật `FAILED`, đồng thời log kèm job ID. Nếu bỏ qua exception, cần cấu hình `AsyncUncaughtExceptionHandler` cho lỗi chưa được bắt.
- Job `@Scheduled` có thể gặp lỗi mỗi lượt. Không chỉ trông chờ log: thêm số lần thất bại, timestamp lần thành công gần nhất, cảnh báo khi quá hạn, timeout khi gọi dịch vụ ngoài và retry với backoff phù hợp.
- Giới hạn `corePoolSize`, `maxPoolSize`, `queueCapacity` theo tải và connection pool; trả 429/503 khi không nhận thêm việc. Hàng đợi trong bộ nhớ không phải message queue bền vững.
- Theo dõi `active threads`, `queued tasks`, `rejected tasks`, thời gian hoàn thành, tỷ lệ lỗi, và độ trễ từ `PENDING` sang `RUNNING`. Dùng correlation/job ID trong log; MDC và Spring Security context không mặc nhiên được mang qua mọi luồng, cần propagation có chủ đích.
- Khi dừng, pool có thể đợi công việc đang chạy trong giới hạn đã cấu hình; giới hạn 20 giây ở demo không bảo đảm báo cáo dài hoàn tất. Với job quan trọng, cần cơ chế ghi nhận checkpoint và khôi phục sau restart.

### 5.4. Các lỗi dễ gặp

| Triệu chứng | Nguyên nhân hay gặp | Hướng xử lý |
| --- | --- | --- |
| `@Async` vẫn chặn HTTP | Gọi nội bộ `this.method()`; thiếu `@EnableAsync` | Gọi qua bean khác/proxy, kiểm tra thread name |
| `@Scheduled` không chạy | Thiếu `@EnableScheduling`; bean chưa được Spring quản lý | Đăng ký bean, kiểm tra logs và startup |
| Báo cáo trùng | Client retry hoặc nhiều worker/pod nhận cùng việc | Idempotency key, trạng thái chuyển có điều kiện |
| Số liệu bị cộng lặp | Nhiều pod cùng chạy lịch hoặc retry không idempotent | Khóa toàn cụm, watermark, upsert |
| Báo cáo thấy dữ liệu cũ | Đọc snapshot trước lần cập nhật mới | Ghi thời điểm snapshot, định nghĩa SLA về độ mới |
| Request hết luồng/DB nghẽn | Pool và hàng đợi không giới hạn hoặc truy vấn nặng | Pool bounded, timeout, backpressure, đo tải |
| Công việc mất sau restart | Trạng thái/queue nằm trong RAM | DB/job broker, cơ chế resume/retry |

## 6. Checklist kiểm thử và câu hỏi tự kiểm tra

1. **Luồng HTTP:** POST trả 202 nhanh hơn bước giả lập 1,5 giây; kiểm tra `Location` và GET bằng ID thực tế; trạng thái cuối là `DONE` hoặc `FAILED`.
2. **Lịch:** GET `/metrics` trước và sau ít nhất một lần chạy scheduled, giá trị tăng; thay `fixedDelay` bằng khoảng ngắn trong test và xác minh thời điểm bắt đầu được tính từ lần trước hoàn tất.
3. **Tải đồng thời:** gửi nhiều POST, đo số luồng `report-*`, độ dài queue, 503 khi bão hòa; bảo đảm scheduler tiếp tục hoạt động.
4. **Lỗi:** tạm làm truy vấn DB thất bại, xác minh job chuyển `FAILED` và có log/metric đủ để điều tra; kiểm thử interrupt/dừng ứng dụng.
5. **Đa instance:** chạy hai instance cùng DB thử nghiệm để quan sát lịch có thể chạy hai lần; sau đó thử cơ chế khóa hoặc dedup tương ứng.
6. **Tính nhất quán:** xác định báo cáo phản ánh số liệu tại thời điểm bắt đầu, thời điểm SELECT hay thời điểm hoàn tất; không hứa giá trị “mới nhất” nếu nghiệp vụ chưa quy định.

**Tự trả lời:** (a) Vì sao `@Async` nên nằm ở bean khác với caller? (b) Khi chạy 3 pod, sẽ có bao nhiêu lần `@Scheduled` có thể được kích hoạt mỗi chu kỳ? (c) Vì sao phải giới hạn hàng đợi? (d) Transaction HTTP có theo sang async worker không? (e) Làm thế nào để retry bản cập nhật định kỳ mà không tính trùng?

Tài liệu chính thức: [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-008">Lộ trình học và kiến thức Java concurrency cần có</a>

<details>
<summary>Xem chi tiết</summary>

# Lộ trình học và kiến thức Java concurrency cần có

## 1. Mục tiêu và vị trí trong Spring Framework concurrency

Concurrency (lập trình đồng thời) là khả năng quản lý nhiều công việc có thời gian thực thi chồng lấn; parallelism (song song) là thực sự chạy nhiều công việc cùng lúc trên nhiều lõi xử lý. Một ứng dụng Java có thể xử lý đồng thời nhiều yêu cầu dù không phải mọi thao tác đều chạy song song.

Trước khi học `TaskExecutor`, `@Async`, `TaskScheduler` hay cách Spring quản lý tác vụ nền, cần hiểu những vấn đề mà các cơ chế đó giải quyết: tạo và quản lý luồng, chia sẻ trạng thái an toàn, đồng bộ, hủy tác vụ, xử lý lỗi, giới hạn tài nguyên và kiểm thử. Spring cung cấp tầng trừu tượng/tích hợp, nhưng quy tắc Java Memory Model và vòng đời tác vụ vẫn có hiệu lực.

Sau bài này, người học nên có thể:

- Phân biệt concurrency, parallelism, asynchronous execution, non-blocking I/O và multithreading.
- Giải thích race condition, visibility, atomicity, ordering và quan hệ *happens-before*.
- Chọn giữa `synchronized`, `volatile`, atomic variables, locks và concurrent collections theo loại trạng thái được chia sẻ.
- Dùng `ExecutorService`, `Future`, `CompletableFuture` và cơ chế phối hợp để tổ chức công việc có kiểm soát.
- Thiết kế đường hủy bằng interruption, timeout và shutdown; tránh deadlock, starvation và tạo quá nhiều tác vụ.
- Hiểu virtual threads của Java 21 và liên hệ được các khái niệm Java với concurrency trong Spring.

**Điểm xuất phát:** nắm vững lớp/đối tượng, interface và lambda, exception handling, generic, collections, try/finally và cách biên dịch chương trình Java. Các ví dụ mặc định chạy trên Java 17+, trừ phần virtual threads ghi rõ Java 21+.

## 2. Bức tranh tổng thể: vấn đề nào dẫn tới công cụ nào?

| Vấn đề | Kiến thức/công cụ Java cần học | Khi nào dùng |
| --- | --- | --- |
| Có công việc độc lập chạy ngoài luồng gọi | `Thread`, `Runnable`, `Callable` | Hiểu mô hình thực thi cơ bản |
| Nhiều luồng cùng đọc/ghi trạng thái | Java Memory Model, `synchronized`, `volatile`, `AtomicInteger` | Đảm bảo tính đúng đắn |
| Cần phối hợp thời điểm và kết quả | `join`, `Future`, `CountDownLatch`, `BlockingQueue` | Tránh phụ thuộc vào `sleep` để đoán thời điểm |
| Nhiều tác vụ cần được quản lý | `ExecutorService`, thread pool, queue, shutdown | Quản lý tài nguyên và vòng đời |
| Cần kết hợp chuỗi tác vụ bất đồng bộ | `CompletableFuture` | Chuyển đổi, kết hợp kết quả, xử lý lỗi |
| Nhiều yêu cầu chờ I/O | Virtual threads (Java 21+) và giới hạn tải | Tăng khả năng phục vụ khi tác vụ thường chờ |
| Đưa concurrency vào Spring | `TaskExecutor`, `@Async`, `TaskScheduler` | Gắn mô hình tác vụ Java với cấu hình và vòng đời ứng dụng |

Không chọn công cụ chỉ vì “chạy nhanh hơn”. Trước tiên xác định tác vụ **CPU-bound** (tính toán) hay **I/O-bound** (chờ mạng, tệp, cơ sở dữ liệu), có chia sẻ dữ liệu hay không, có giới hạn tài nguyên nào và kết quả/lỗi phải trả về đâu.

## 3. Mô hình thread và vòng đời thực thi

Một *process* có vùng tài nguyên riêng; trong Java, nhiều thread của cùng JVM thường chia sẻ heap nhưng mỗi thread có call stack riêng. Dữ liệu trong các đối tượng dùng chung có thể bị truy cập đồng thời. Việc một biến được khai báo trong phương thức không tự bảo đảm an toàn nếu tham chiếu của nó trỏ tới đối tượng có thể thay đổi và được chia sẻ.

- `Thread` đại diện cho luồng thực thi; `Runnable` mô tả tác vụ không trả kết quả; `Callable<V>` mô tả tác vụ trả kết quả hoặc ném ngoại lệ.
- `start()` yêu cầu JVM bắt đầu luồng mới; gọi trực tiếp `run()` chỉ là lời gọi phương thức trên luồng hiện tại.
- `join()` đợi một thread kết thúc. `sleep()` chỉ tạm dừng thread hiện tại, **không** tạo ra cam kết rằng thread khác đã hoàn thành.
- Các trạng thái Java `NEW`, `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`, `TERMINATED` phản ánh trạng thái theo API; `RUNNABLE` không có nghĩa là thread chắc chắn đang dùng CPU tại thời điểm quan sát.
- Thread không phải daemon có thể giữ JVM còn hoạt động; daemon thread không phù hợp cho công việc bắt buộc phải hoàn tất trước khi ứng dụng dừng.

Ví dụ đầy đủ `ThreadBasics.java`:

```java
public class ThreadBasics {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> System.out.println("Worker finished"));
        worker.start();
        worker.join();
        System.out.println("Main finished");
    }
}
```

```bash
javac ThreadBasics.java
java ThreadBasics
```

Kết quả:

```text
Worker finished
Main finished
```

Thứ tự này được bảo đảm vì `join()` chờ worker kết thúc trước khi thread chính in dòng thứ hai. Không suy luận thứ tự chạy chỉ từ thứ tự gọi `start()` nếu không có phép phối hợp.

## 4. Race condition và Java Memory Model: nền tảng bắt buộc

**Race condition** xuất hiện khi kết quả phụ thuộc vào thứ tự xen kẽ thao tác giữa các thread mà chương trình không kiểm soát đúng. Ví dụ `counter++` thực chất gồm đọc giá trị, cộng 1 rồi ghi lại; hai thread có thể cùng đọc một giá trị và làm mất một lần tăng.

Cần phân biệt ba tính chất:

1. **Atomicity:** một thao tác được quan sát như một đơn vị không bị xen kẽ. `counter++` trên biến `int` dùng chung không atomic.
2. **Visibility:** cập nhật của thread này có được thread khác nhìn thấy đúng lúc hay không.
3. **Ordering:** compiler, CPU và JVM có thể sắp xếp lại thao tác trong giới hạn cho phép; không thể dựa vào trực giác về thứ tự mã nguồn giữa các thread.

Java Memory Model (JMM) định nghĩa khi nào các thao tác có quan hệ *happens-before*. Một số quy tắc quan trọng:

- Các thao tác trước `Thread.start()` trong thread gọi happens-before các thao tác của thread mới.
- Mọi hành động của một thread happens-before hành động khác sau khi `join()` trên thread đó trả về thành công.
- Thao tác mở khóa monitor happens-before lần khóa tiếp theo trên chính monitor đó.
- Một lần ghi `volatile` happens-before lần đọc tiếp theo đối với cùng biến `volatile`.
- Những thao tác trước `CountDownLatch.countDown()` happens-before những thao tác sau khi thread khác thoát `await()` thành công.

Không có quan hệ đồng bộ phù hợp thì mã có *data race* có thể đọc giá trị cũ hoặc biểu hiện hành vi bất ngờ. Thêm `sleep` không sửa được lỗi bộ nhớ.

### 4.1. Chọn cơ chế bảo vệ trạng thái

| Cơ chế | Cung cấp | Giới hạn |
| --- | --- | --- |
| `synchronized` | Mutual exclusion, visibility khi vào/ra monitor, lock có thể reentrant | Giữ khóa lâu làm giảm concurrency; cần tránh khóa theo nhiều thứ tự |
| `volatile` | Visibility và ordering cho đọc/ghi một biến; phù hợp cờ trạng thái | Không biến phép đọc–sửa–ghi phức hợp thành atomic |
| `AtomicInteger` / `AtomicReference` | Thao tác atomic riêng lẻ dựa trên CAS/biến thể | Không tự bảo đảm invariant trải trên nhiều trường độc lập |
| `ReentrantLock` | Khóa tường minh, `tryLock`, khóa có thể ngắt khi chờ | Phải `unlock` trong `finally` |
| Dữ liệu immutable | Tránh sửa trạng thái sau khi tạo, dễ chia sẻ | Vẫn cần *safe publication* khi công bố đối tượng |
| Concurrent collections | Cơ chế đồng bộ chuyên biệt theo cấu trúc dữ liệu | Chuỗi nhiều thao tác có thể vẫn cần phối hợp bổ sung |

Một cờ `volatile boolean running` phù hợp để một thread báo thread khác dừng vòng lặp, nếu vòng lặp thường xuyên kiểm tra cờ. Ngược lại, `volatile int count` **không** làm `count++` an toàn; cần atomic variable hoặc vùng khóa. Với điều kiện liên quan nhiều biến, bảo vệ toàn bộ invariant bằng cùng một cơ chế đồng bộ.

### 4.2. Ví dụ đếm an toàn với AtomicInteger

```java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounterDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        Runnable increment = () -> {
            for (int i = 0; i < 1_000; i++) {
                counter.incrementAndGet();
            }
        };

        Thread first = new Thread(increment);
        Thread second = new Thread(increment);
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println(counter.get());
    }
}
```

```bash
javac AtomicCounterDemo.java
java AtomicCounterDemo
```

Kết quả:

```text
2000
```

`incrementAndGet()` cập nhật atomic; hai lần `join()` bảo đảm chương trình đợi đủ hai thread trước khi đọc. Nếu thay bằng biến `int` dùng chung rồi tăng bằng `++`, giá trị cuối có thể nhỏ hơn 2000, dù đôi khi tình cờ bằng 2000; đó không phải một bài kiểm thử đúng đắn.

### 4.3. Ví dụ truyền tín hiệu và bảo đảm visibility

```java
import java.util.concurrent.CountDownLatch;

public class LatchDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        int[] shared = {0};

        Thread reader = new Thread(() -> {
            try {
                ready.await();
                System.out.println(shared[0]);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        reader.start();

        shared[0] = 42;
        ready.countDown();
        reader.join();
    }
}
```

```bash
javac LatchDemo.java
java LatchDemo
```

Kết quả khi không có yêu cầu ngắt ngoài chương trình:

```text
42
```

`countDown()` giải phóng thread đang `await()` và thiết lập quan hệ happens-before cho lần ghi trước đó. `join()` giúp thread chính đợi việc in hoàn tất. Không cần thêm `volatile` cho `shared[0]` trong đúng trình tự đồng bộ của ví dụ này.

## 5. Phối hợp giữa các tác vụ

Khi nhiều tác vụ phụ thuộc nhau, hãy diễn đạt sự phụ thuộc bằng primitive rõ ràng:

- `CountDownLatch(n)`: một hoặc nhiều thread đợi `n` sự kiện; latch chỉ dùng một lượt, không reset.
- `CyclicBarrier`: nhiều thread gặp nhau ở điểm đồng bộ theo vòng; hữu ích khi các pha thực sự cần bước cùng nhau.
- `Phaser`: phối hợp nhiều pha với số thành viên linh hoạt hơn.
- `Semaphore`: giới hạn số tác vụ đồng thời sử dụng tài nguyên; acquire/release phải cân bằng, kể cả khi có exception.
- `BlockingQueue`: kênh producer–consumer có cơ chế chờ khi lấy/đưa phần tử; queue có dung lượng giới hạn giúp áp dụng *backpressure*.
- `Condition` đi kèm `Lock`: chờ một điều kiện trạng thái; kiểm tra điều kiện trong vòng `while`, không dùng `if` đơn lẻ, vì có thể có lần đánh thức không đáp ứng điều kiện.

Chỉ dùng `wait()/notify()/notifyAll()` sau khi hiểu monitor, điều kiện vòng lặp và yêu cầu giữ khóa; trong phần lớn ứng dụng nên ưu tiên công cụ cấp cao từ `java.util.concurrent`.

Nguyên tắc: chia sẻ dữ liệu càng ít càng tốt. Bất biến (*immutable value*), message passing qua queue và tách trạng thái theo từng tác vụ thường đơn giản hơn một mạng lưới khóa.

## 6. ExecutorService, thread pool và Future

Tạo `new Thread(...)` cho mọi yêu cầu dễ khiến vòng đời và tài nguyên không được kiểm soát. `Executor` là cơ chế nộp công việc; `ExecutorService` thêm cơ chế nộp tác vụ có kết quả và quản lý shutdown; `Future<T>` đại diện cho kết quả có thể chưa sẵn sàng.

Quy trình tối thiểu:

1. Chọn executor phù hợp với đặc điểm tải và giới hạn tài nguyên.
2. Nộp `Runnable` hoặc `Callable<T>` bằng `execute`/`submit`.
3. Nhận kết quả qua `Future.get()` hoặc thiết kế chuỗi bất đồng bộ; `get()` có thể block và ném `ExecutionException`.
4. Đặt timeout khi việc chờ không được phép vô hạn; xử lý `InterruptedException`.
5. `shutdown()` ngừng nhận công việc mới nhưng để tác vụ đã nhận hoàn tất; `shutdownNow()` cố gắng ngắt các tác vụ đang chạy và trả lại danh sách chưa bắt đầu, **không** bảo đảm dừng cưỡng bức.
6. Nếu cần, dùng `awaitTermination` để đợi executor kết thúc theo một thời hạn.

Ví dụ hoàn chỉnh `ExecutorDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ExecutorDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> answer = pool.submit(() -> 40 + 2);
            System.out.println(answer.get());
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac ExecutorDemo.java
java ExecutorDemo
```

Kết quả:

```text
42
```

Dùng `finally` bảo đảm gọi `shutdown()` cả khi `get()` thất bại. Trong ứng dụng dài hạn, executor thường thuộc vòng đời của một thành phần quản lý, thay vì tạo một pool mới cho mỗi lời gọi.

### 6.1. Hiểu pool và giới hạn tải

Pool có ít thread thích hợp để giới hạn lượng tính toán CPU đồng thời; pool nhiều thread phục vụ tác vụ I/O blocking có thể cải thiện mức sử dụng CPU nhưng gây tốn memory và chuyển ngữ cảnh. Số lượng thích hợp phụ thuộc cấu hình máy, thời gian chờ, tài nguyên downstream và SLA; không có “một con số tốt cho mọi ứng dụng”.

Bốn chỉ số cần quan sát: số tác vụ đang chạy, độ dài hàng đợi, thời gian chờ trong hàng đợi, và tỷ lệ từ chối/timeout. Hàng đợi không giới hạn có thể giấu quá tải bằng độ trễ và bộ nhớ ngày càng tăng. Với `ThreadPoolExecutor` cần hiểu `corePoolSize`, `maximumPoolSize`, `keepAliveTime`, queue và `RejectedExecutionHandler`; tương tác của chúng quan trọng hơn từng con số riêng lẻ.

Tránh deadlock do phụ thuộc trong pool: nếu tất cả worker đều đang `Future.get()` chờ các tác vụ khác cũng phải chạy trên chính pool đã kín worker, hệ thống có thể đứng mãi. Hãy xem lại cấu trúc phụ thuộc, tách loại công việc hoặc dùng composition không chặn phù hợp.

## 7. Cancellation, interruption, timeout và xử lý lỗi

Interruption trong Java là **tín hiệu hợp tác**. `Thread.interrupt()` đặt trạng thái interrupt; nhiều thao tác blocking như `sleep`, `wait`, `BlockingQueue.take` ném `InterruptedException` và thường xóa cờ interrupt khi ném. Mã tác vụ phải có chính sách phản hồi: thoát, dọn dẹp tài nguyên hoặc truyền tín hiệu lên tầng quản lý.

Ví dụ đoạn mã **minh họa trong phương thức chạy tác vụ**:

```java
try {
    // Giả lập một thao tác chờ có thể bị ngắt.
    Thread.sleep(1_000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // giữ tín hiệu để tầng gọi xử lý
    return; // chỉ hợp lệ nếu đang ở trong phương thức trả về void
}
```

Đoạn trên là fragment, không phải một lớp Java độc lập. Với `Callable` cho phép ném `InterruptedException`, có thể để exception truyền ra; không nên bắt rồi im lặng tiếp tục chạy như chưa có yêu cầu hủy.

- `Future.cancel(true)` yêu cầu ngắt nếu tác vụ đang chạy; không ép mã đang chờ CPU hoặc thư viện không hỗ trợ interrupt dừng ngay.
- `Future.get(timeout, unit)` giới hạn thời gian *người gọi đợi*, không tự động hủy công việc đang chạy; cân nhắc gọi `cancel(true)` theo chính sách ứng dụng.
- Timeout ở tầng tác vụ khác với timeout của socket/database/HTTP client. Cần giới hạn tại nguồn I/O để tránh giữ tài nguyên quá lâu.
- Exception trong `submit(Callable/Runnable)` được giữ trong `Future` và bộc lộ qua `get()`; bỏ qua `Future` có thể khiến lỗi bị bỏ sót.
- Không dùng `Thread.stop()` để hủy cưỡng bức; bảo đảm cleanup với `finally` hoặc `try-with-resources`.

## 8. CompletableFuture: xây chuỗi công việc bất đồng bộ

`CompletableFuture<T>` vừa biểu diễn kết quả cuối, vừa hỗ trợ các stage phụ thuộc nhau. Điểm cần học theo thứ tự:

- `supplyAsync` tạo công việc trả kết quả; `runAsync` không trả dữ liệu. Mặc định các phương thức async không truyền executor thường dùng `ForkJoinPool.commonPool()`.
- `thenApply` biến đổi giá trị; `thenCompose` dùng khi bước sau cũng trả về `CompletionStage` để tránh lồng `Future<Future<T>>`; `thenCombine` kết hợp hai kết quả độc lập.
- `thenApplyAsync`/`thenComposeAsync` cho phép chỉ định executor; method không có hậu tố `Async` không mặc nhiên chạy trên thread riêng, và có thể chạy ngay trong thread hoàn thành stage trước.
- `exceptionally` tạo giá trị fallback sau lỗi; `handle` xử lý cả thành công/lỗi; `whenComplete` quan sát kết quả và side effect, không phải cơ chế thay đổi kiểu kết quả.
- `join()` và `get()` đều có thể chặn khi chưa hoàn thành; `join()` bọc lỗi theo hướng unchecked (`CompletionException`), còn `get()` dùng checked exceptions.
- Khi đã có chuỗi stages, ưu tiên ghép pipeline thay vì gọi `join()` giữa từng bước, đặc biệt trong môi trường số worker hạn chế.

Ví dụ đầy đủ `CompletableFutureDemo.java`:

```java
import java.util.concurrent.CompletableFuture;

public class CompletableFutureDemo {
    public static void main(String[] args) {
        CompletableFuture<String> result =
                CompletableFuture.supplyAsync(() -> "java")
                        .thenApply(String::toUpperCase);

        System.out.println(result.join());
    }
}
```

```bash
javac CompletableFutureDemo.java
java CompletableFutureDemo
```

Kết quả:

```text
JAVA
```

Không suy ra tên thread hoặc thứ tự chạy stage từ kết quả này. Trong ứng dụng có I/O blocking, cân nhắc executor riêng có giới hạn và lifecycle rõ ràng thay cho mặc định common pool.

## 9. Concurrent collections, lock và thread-local state

### 9.1. Cấu trúc dữ liệu dùng chung

- `ConcurrentHashMap` hỗ trợ nhiều truy cập đồng thời và các thao tác atomic theo key như `compute`, `merge`, `putIfAbsent`. Mẫu `if (!map.containsKey(k)) map.put(k, v)` gồm hai thao tác riêng, không tự atomic.
- `CopyOnWriteArrayList` phù hợp trường hợp đọc rất nhiều, ghi rất ít; mỗi lần thay đổi có chi phí sao chép mảng.
- `BlockingQueue` giúp phối hợp producer–consumer; queue có capacity giới hạn thường an toàn hơn queue tăng vô hạn khi tải lớn.
- `ConcurrentLinkedQueue` là queue thread-safe không chặn; không tự tạo giới hạn dung lượng/backpressure.
- Bản thân một collection thread-safe không biến cả luồng xử lý gồm nhiều câu lệnh thành transaction. Cần xác định invariant của nghiệp vụ.

### 9.2. Khi nào cần Lock?

`ReentrantLock` cho phép chờ có timeout hoặc phản hồi interrupt khi giành khóa, nhưng phải luôn mở khóa đúng cách:

```java
lock.lock();
try {
    // Truy cập trạng thái dùng chung đã được bảo vệ.
} finally {
    lock.unlock();
}
```

Đây là fragment minh họa, giả định `lock` đã là `ReentrantLock` được khởi tạo và chia sẻ. `ReadWriteLock`/`StampedLock` có ích cho một số mô hình nhiều đọc ít ghi nhưng tăng độ phức tạp; chỉ sử dụng sau khi đo và xác minh tính đúng đắn.

### 9.3. ThreadLocal và ranh giới thread

`ThreadLocal<T>` lưu giá trị theo từng thread, **không** truyền tự động giữa executor worker khác nhau. Trong pool, worker được tái sử dụng, vì thế dữ liệu request còn tồn tại sau khi tác vụ kết thúc có thể gây rò rỉ hoặc lẫn ngữ cảnh. Khi đặt ThreadLocal thủ công, thường cần `remove()` trong `finally`. Không dùng ThreadLocal để thay cơ chế truyền tham số rõ ràng.

Trong Spring, transaction context, logging context (MDC) và một số security/request context thường gắn với thread. Việc chuyển việc qua `@Async` hoặc executor có thể làm mất ngữ cảnh, nên phải dùng cơ chế truyền ngữ cảnh được hỗ trợ và kiểm soát vòng đời; không giả định “cùng một request” nghĩa là “cùng một thread”.

## 10. Virtual threads của Java 21: bổ sung vào lộ trình

Java 21 cung cấp virtual threads ở mức ổn định. Virtual thread vẫn có ngữ nghĩa `Thread` của Java, nhưng nhiều virtual threads có thể được JVM quản lý trên một số lượng nhỏ carrier platform threads. Chúng đặc biệt hữu ích cho tác vụ có nhiều thời gian **chờ I/O blocking**, giúp viết mã tuần tự dễ hiểu mà vẫn đạt concurrency cao.

Ví dụ đầy đủ, **chỉ chạy trên Java 21+**, `VirtualThreadDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VirtualThreadDemo {
    public static void main(String[] args) throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> result = executor.submit(() -> "hello virtual");
            System.out.println(result.get());
        }
    }
}
```

```bash
javac VirtualThreadDemo.java
java VirtualThreadDemo
```

Kết quả:

```text
hello virtual
```

Virtual threads **không** làm tính toán CPU nhanh hơn số lõi có thể xử lý, không khiến cấu trúc dữ liệu tự thread-safe và không làm biến mất giới hạn connection pool, database hoặc API bên ngoài. Không dùng “pool virtual threads để hạn chế số luồng”; khi cần giới hạn concurrency đối với tài nguyên bên ngoài, dùng semaphore, connection pool hoặc cơ chế backpressure. Trên Java 21, một số tình huống blocking bên trong monitor `synchronized` còn có thể giữ carrier thread (pinning); cần đo trên đúng phiên bản JDK đang triển khai. Hiểu platform threads và executor thông thường trước khi chọn virtual threads.

## 11. Những lỗi concurrency thường gặp và cách nhận biết

| Triệu chứng | Nguyên nhân hay gặp | Hướng khắc phục |
| --- | --- | --- |
| Counter hoặc tổng cuối bị thiếu ngẫu nhiên | Race trong read–modify–write | Atomic operation hoặc khóa bảo vệ invariant |
| Thread dường như không thấy cờ dừng | Không có bảo đảm visibility | `volatile`, khóa hoặc primitive đồng bộ |
| Ứng dụng đứng dù CPU thấp | Deadlock, chờ vòng hoặc tác vụ chờ trong pool kín | Thread dump, xác định chuỗi khóa/`Future.get`, tổ chức lại phụ thuộc |
| Độ trễ tăng và memory tăng theo tải | Queue vô hạn, không có backpressure | Bounded queue, timeout, rejection policy |
| Có exception nhưng luồng gọi không thấy | `Future` bị bỏ qua hoặc xử lý lỗi bất đồng bộ thiếu | Quan sát completion/error và logging có ngữ cảnh |
| Shutdown kéo dài, tác vụ không dừng | Không xử lý interruption hoặc I/O không có timeout | Cooperative cancellation, timeout tại nguồn |
| Transaction/log context sai khi dùng async | Ngữ cảnh thread-local không tự chuyển | Truyền context đúng cách, thiết kế transaction boundary rõ |
| Test lúc được lúc không | Dùng `sleep` để giả định thứ tự, hoặc có data race | Latch/barrier, future, timeout rõ ràng, stress test |

Ba khái niệm khác nhau: **deadlock** là chuỗi chờ mà các bên không thể tiến lên; **starvation** là tác vụ không được cấp tài nguyên/cơ hội chạy đủ; **livelock** là các bên liên tục phản ứng nhưng không đạt tiến triển. Cả ba đều cần phân tích tiến triển hệ thống, không chỉ “thêm thread”.

## 12. Kiểm thử, đo đạc và vận hành

Concurrency cần chứng minh cả **correctness** lẫn **performance**:

1. Viết invariant cụ thể: ví dụ “1000 yêu cầu thành công thì bộ đếm chính xác tăng 1000”, “mỗi job chỉ xử lý một lần”, “không có tác vụ chạy sau shutdown”.
2. Tạo điểm bắt đầu có điều khiển bằng `CountDownLatch`/barrier; tránh dùng `Thread.sleep` thay cơ chế đồng bộ.
3. Dùng timeout cho test và các thao tác chờ, bảo đảm test lỗi kết thúc có thông tin thay vì treo vô hạn.
4. Chạy nhiều lượt với tải và lịch thực thi khác nhau để tăng cơ hội lộ race; kết quả test “xanh” không phải chứng minh tuyệt đối rằng không có race.
5. Đọc thread dump để xem stack, trạng thái thread, monitor đang giữ/chờ; dùng Java Flight Recorder (JFR) và metric để đo blocking, lock contention, độ dài queue, số task active, timeout và latency.
6. Phân biệt throughput (số công việc hoàn tất mỗi đơn vị thời gian), latency (độ trễ từng công việc) và mức tiêu thụ tài nguyên; throughput tăng không đồng nghĩa latency tốt hơn.
7. Thử tình huống lỗi: task ném exception, client timeout, executor bị từ chối, hủy giữa chừng, shutdown khi còn tác vụ và hệ thống downstream chậm.

Dữ liệu đo chỉ có nghĩa trong bối cảnh workload, cấu hình CPU, JVM, pool và dịch vụ phụ thuộc. Không tối ưu bằng cảm giác.

## 13. Lộ trình học theo chặng và tiêu chí hoàn thành

| Chặng | Học nội dung | Bài thực hành nên hoàn thành | Điều kiện chuyển chặng |
| --- | --- | --- | --- |
| **1. Mô hình thực thi** | Concurrency vs parallelism; `Thread`, `Runnable`, `Callable`, `start/join`, lifecycle | Chạy hai công việc và giải thích thứ tự output khi có/không có `join` | Giải thích vì sao `run()` không tạo thread mới |
| **2. Tính đúng đắn bộ nhớ** | Race, atomicity, visibility, ordering, JMM, happens-before | Tạo race trên counter; sửa bằng `AtomicInteger` và `synchronized` | Chứng minh được vì sao `volatile count++` vẫn sai |
| **3. Phối hợp và hủy** | Latch, queue, semaphore, interrupt, timeout | Producer–consumer với queue hữu hạn; dừng an toàn theo yêu cầu | Không dùng `sleep` làm tín hiệu hoàn thành, không nuốt interrupt |
| **4. Quản lý thực thi** | Executor, pool sizing, queue, Future, shutdown/rejection | Xử lý nhiều jobs có giới hạn và thu kết quả/lỗi | Không rò thread, kiểm soát được overload |
| **5. Async composition** | `CompletableFuture`, các stage, kết hợp/lỗi, executor phù hợp | Chạy song song hai lời gọi giả lập, ghép kết quả, tạo fallback | Phân biệt non-blocking composition với gọi `join` chặn |
| **6. Công cụ bổ sung** | Concurrent collections, lock, ThreadLocal, Java 21 virtual threads | So sánh thiết kế state-sharing với message passing; đo tác vụ I/O chờ | Nêu được trade-off và giới hạn downstream |
| **7. Chuyển sang Spring** | `TaskExecutor`, `@Async`, `TaskScheduler`, lifecycle/context | Một service chạy nền có log, timeout, error handling và test rõ ràng | Biết chọn executor và hiểu context/transaction khi đổi thread |

Đây là thứ tự phụ thuộc kiến thức, không phải cam kết thời lượng cố định. Nếu mới bắt đầu, ưu tiên làm chắc chặng 1–4 trước khi chuyển sang các trừu tượng Spring. Người đã có kinh nghiệm có thể chứng minh năng lực bằng bài thực hành rồi bỏ qua phần đã thành thạo.

### Bài tập tích hợp đề xuất

Xây một chương trình Java xử lý danh sách 100 “công việc” giả lập, tối đa 5 công việc cùng hoạt động; mỗi công việc có ID, kết quả hoặc lỗi. Chương trình cần:

- Không tạo thread tùy tiện; dùng một executor có lifecycle và giới hạn concurrency rõ ràng.
- Thu đủ kết quả thành công, thống kê lỗi chính xác và không để tác vụ chưa hoàn tất bị bỏ quên.
- Có timeout/hủy hợp tác khi vượt deadline; shutdown gọn sau khi hoàn tất.
- Dùng dữ liệu/thread-safe primitives đúng chỗ; chứng minh invariant bằng test nhiều lượt.
- Đo thời gian toàn bộ, số job cùng chạy tối đa và giải thích điều gì xảy ra khi tăng số job lên gấp 10.

Một lời giải tốt có thể lựa chọn pool hữu hạn trên Java 17+, hoặc virtual-thread-per-task executor của Java 21 kết hợp semaphore khi I/O là bottleneck. Điều quan trọng là giải thích được giới hạn tài nguyên, hành vi hủy và tính đúng đắn, không phải chọn API “mới nhất”.

## 14. Ánh xạ kiến thức Java sang Spring Framework concurrency

Khi đã chắc phần Java, người học chuyển sang Spring với các câu hỏi tương ứng:

| Java concurrency | Câu hỏi tương ứng trong Spring |
| --- | --- |
| `Executor` / `ExecutorService` | `TaskExecutor` nào chạy tác vụ và ai quản lý lifecycle/pool? |
| `Future` / `CompletableFuture` | Service `@Async` trả kết quả/lỗi về nơi gọi như thế nào? |
| Thread boundary | Proxy của `@Async` có thực sự được gọi qua Spring bean? Self-invocation có bỏ qua advice không? |
| `ScheduledExecutorService` | `TaskScheduler` / `@Scheduled` định lịch, overlap và shutdown ra sao? |
| Interrupt, timeout, rejection | Khi quá tải hay hủy, chính sách lỗi/giới hạn và quan sát của ứng dụng là gì? |
| `ThreadLocal` và transaction | Security/logging/transaction context được xử lý thế nào qua thread boundary? |
| Virtual threads | Phiên bản JDK và Spring/Boot có cấu hình hỗ trợ nào, và downstream có chịu được concurrency cao không? |

Lưu ý quan trọng: lời gọi `@Async` thông thường hoạt động qua Spring proxy; gọi phương thức async từ một phương thức khác trên chính cùng đối tượng (*self-invocation*) có thể bỏ qua cơ chế proxy. Transaction bound với thread không tự chạy theo thread async; cần xác định lại transaction boundary và context trước khi triển khai. Phần Spring chuyên sâu sẽ giải quyết cách khai báo executor, enable async, cấu hình scheduler, exception handler và quan sát vận hành; bài Java này cung cấp nền tảng để hiểu các quyết định đó.

## 15. Checklist tự đánh giá trước khi học Spring concurrency

- [ ] Tôi phân biệt được việc nhiều task đang chờ với việc nhiều task đang dùng CPU song song.
- [ ] Tôi giải thích được `start`, `run`, `join`, `sleep` và ý nghĩa trạng thái thread.
- [ ] Tôi chỉ ra được race/visibility trong một ví dụ và mô tả quan hệ happens-before giải quyết nó.
- [ ] Tôi hiểu khi nào dùng `volatile`, atomic type, khóa và concurrent collection.
- [ ] Tôi viết được producer–consumer hoặc task pipeline có cơ chế phối hợp đúng, không phụ thuộc vào `sleep`.
- [ ] Tôi dùng executor có shutdown, timeout, kết quả và xử lý lỗi rõ ràng.
- [ ] Tôi biết cách xử lý `InterruptedException` và không giả định `cancel(true)` chắc chắn dừng tác vụ.
- [ ] Tôi kết hợp được nhiều `CompletableFuture` và phân biệt tác vụ bất đồng bộ với thao tác không chặn.
- [ ] Tôi nhận diện được queue không giới hạn, deadlock, thread starvation và sai lệch do ThreadLocal.
- [ ] Tôi giải thích được khi nào virtual threads hữu ích và giới hạn của chúng.
- [ ] Tôi có thể chỉ rõ nơi Spring đổi thread, nơi transaction/context có thể bị ảnh hưởng và phương án kiểm thử.

**Tiêu chuẩn hoàn thành:** tự viết được một ứng dụng concurrency nhỏ, chứng minh tính đúng đắn của dữ liệu chia sẻ, kiểm soát được lỗi/hủy/tài nguyên và giải thích được các quyết định sử dụng thread hoặc executor. Khi đó các tính năng concurrency của Spring trở thành cách tích hợp những nguyên lý đã hiểu vào vòng đời ứng dụng.


</details>

- [Quay lại đầu trang](#back-to-top)
