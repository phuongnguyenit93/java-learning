<a id="back-to-top"></a>

# Proxy và lời gọi

## Menu
- [Đường đi lời gọi từ bên gọi qua proxy tới tác vụ bất đồng bộ](#proxy-invocation-001)
- [Vì sao phương thức tự gọi trong cùng bean không kích hoạt @Async ở chế độ proxy](#proxy-invocation-002)
- [Phân biệt đối tượng tự tạo bằng new với bean được tiêm](#proxy-invocation-003)
- [Proxy theo interface và proxy theo lớp](#proxy-invocation-004)
- [Ảnh hưởng của private, final và khả năng chặn lời gọi](#proxy-invocation-005)
- [Tách phương thức bất đồng bộ sang bean cộng tác](#proxy-invocation-006)
- [Không trông cậy @Async trên callback @PostConstruct của chính bean](#proxy-invocation-007)
- [Giới hạn khởi tạo sớm khi dùng AsyncConfigurer](#proxy-invocation-008)
- [Nhận diện chế độ AspectJ và yêu cầu weaving khi cần lựa chọn khác](#proxy-invocation-009)

## <a id="proxy-invocation-001">Đường đi lời gọi từ bên gọi qua proxy tới tác vụ bất đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Đường đi lời gọi từ bên gọi qua proxy tới tác vụ bất đồng bộ

## 1. Vấn đề cốt lõi: ai thực sự chạy phương thức @Async?

Trong Spring Framework, đặt `@Async` lên một phương thức **không tự biến thân phương thức thành một thread mới**. Với chế độ mặc định, Spring dùng **AOP proxy** để chặn **lời gọi đi qua bean proxy**, chọn một executor, đóng gói thao tác gọi phương thức thành tác vụ và giao tác vụ cho executor thực hiện.

Vì vậy, để giải thích chính xác một lời gọi `service.generateReport()`, phải phân biệt ba đối tượng và hai đoạn thực thi:

1. **Caller**: controller, service hoặc một thành phần khác đang thực hiện lời gọi.
2. **Spring proxy**: đối tượng bean mà caller nhận qua dependency injection. Nó có cơ hội chặn lời gọi đủ điều kiện và áp dụng xử lý `@Async`.
3. **Target**: đối tượng chứa logic thật của phương thức. Với đường đi bất đồng bộ thông thường, logic target được gọi trong tác vụ do executor nhận.

Hai đoạn thực thi:

- **Trên caller thread**: đi vào proxy, xác định lời gọi bất đồng bộ, chọn executor, gửi tác vụ và trả quyền điều khiển/handle kết quả.
- **Trên worker thread** (với executor dựa trên thread pool): lấy tác vụ, gọi target method, thu kết quả hoặc ngoại lệ, rồi hoàn tất handle tương ứng.

`@Async` thay đổi **cách một lời gọi hợp lệ được định tuyến**, không phải định nghĩa cú pháp mới của Java. Khi lời gọi **không đi qua proxy**, annotation mặc định không được kích hoạt và method chạy trực tiếp như một lời gọi Java thông thường.

## 2. Sơ đồ đường đi caller → proxy → executor → worker → target

```text
Caller thread                            Executor / worker thread
-------------                            ------------------------
Controller / other bean
    |
    | 1. service.calculate(21)
    v
Spring-managed proxy
    |
    | 2. Async interceptor kiểm tra @Async
    | 3. Xác định executor (ví dụ "asyncPool")
    | 4. Đóng gói lời gọi target vào task
    | 5. submit(task) -----------------------------------+
    |                                                   |
    | 6. Trả Future/CompletableFuture                    v
    +----> caller tiếp tục                    Worker của asyncPool
                                                         |
                                                         | 7. invoke target
                                                         v
                                                   Target.calculate(21)
                                                         |
                                                         | 8. Tính ra kết quả
                                                         v
                                                 Hoàn tất Future
                                                         |
    | 9. caller gọi future.get() / callback <-----------+
    v
Nhận giá trị / lỗi / trạng thái cuối
```

Sơ đồ là **diễn giải về luồng xử lý điển hình**, không khẳng định mọi executor đều luôn tạo thread riêng: nếu cố tình cấu hình cơ chế thực thi đồng bộ hoặc thay executor bằng implementation có chính sách khác, đặc tính threading sẽ khác. Ngoài ra, thứ tự “caller tiếp tục” và “worker bắt đầu chạy” là **cạnh tranh theo lịch thread**; worker có thể hoàn thành rất nhanh ngay trước khi caller nhận handle.

Trọng tâm vẫn là: **đường đi qua proxy mới là điểm kích hoạt interceptor**, còn executor quyết định lịch chạy thực tế.

## 3. Điều kiện để lời gọi được proxy chặn

Một lời gọi `@Async` hoạt động theo cơ chế proxy khi thỏa đồng thời các điều kiện cần:

1. **Bật hỗ trợ async**: thường đặt `@EnableAsync` ở cấu hình Spring. Chỉ thêm `@Async` mà không kích hoạt xử lý async thì annotation có thể không tạo hành vi mong muốn.
2. **Target là bean được Spring quản lý**: Spring có cơ hội dùng bean post-processing để tạo/cấu hình proxy. Tự `new ReportService()` ngoài container sẽ không tự được bọc bởi proxy này.
3. **Caller gọi vào đối tượng proxy**: thường nhận qua constructor injection từ container, không tự gọi bằng `this` vào phương thức trong cùng target.
4. **Phương thức có thể được advice** theo kiểu proxy và cấu hình của ứng dụng: nên đặt `@Async` trên phương thức nghiệp vụ `public` được gọi từ một bean khác.
5. **Executor được chọn là hợp lệ và đang chấp nhận task**: nếu bean executor không tìm được, đã shutdown hoặc bị saturation, lời gọi có thể thất bại khi định tuyến/gửi việc.

Ví dụ lộ trình đúng:

```java
@Service
public class ApiService {
    private final ReportService reports;

    public ApiService(ReportService reports) {
        this.reports = reports; // Tham chiếu do Spring inject: có thể là proxy.
    }

    public void handleRequest() {
        reports.generateReport(); // Lời gọi từ bên ngoài qua proxy.
    }
}
```

Đoạn này là **mảnh mã trong ứng dụng Spring**, cần các bean và imports được khai báo. Nó chỉ mô tả điểm vào proxy; hãy xem các phần dưới để có dự án chạy độc lập và quan sát thread thực tế.

## 4. Proxy tạo và làm gì khi thấy @Async?

Spring có hạ tầng cho annotation `@Async`: khi bật `@EnableAsync`, framework đăng ký các thành phần nhận diện lời gọi đủ điều kiện và gắn async advice vào bean phù hợp. Ở chế độ proxy mặc định (`AdviceMode.PROXY`), advice được kích hoạt khi một caller gọi qua proxy.

Từng bước trên caller thread:

1. Caller đang giữ một tham chiếu tới bean. Tham chiếu ấy trông giống object Java thông thường nhưng thực tế có thể là proxy.
2. Lời gọi `service.method(args)` đi vào proxy.
3. Async interceptor tìm chính sách `@Async`, có thể lấy tên executor từ `@Async("beanName")`.
4. Interceptor đóng gói lời gọi target thành một tác vụ, thay vì lập tức gọi target ngay trên caller thread theo cách thông thường.
5. Tác vụ được đưa vào executor đã chọn (`Executor`/`AsyncTaskExecutor` thích hợp), nơi nó có thể bắt đầu chạy hoặc chờ queue.
6. Proxy trả về cho caller theo kiểu mà phương thức khai báo cho phép, không bắt caller tự tạo thread.

Từng bước trên worker thread:

1. Executor cấp một worker nhận task.
2. Task thực hiện chuỗi interceptor/target call cần thiết.
3. Logic phương thức `@Async` thật chạy trong môi trường của worker. Nếu nó trả `Future` hoặc `CompletableFuture`, Spring có thể bóc kết quả tương ứng để hoàn tất handle mà proxy trả cho caller.
4. Kết quả, ngoại lệ hoặc trạng thái hủy được phản ánh qua handle hoặc cơ chế báo lỗi của Spring.

**Không nên hình dung “proxy chính là thread worker”.** Proxy là cơ chế *chặn và định tuyến* lời gọi. Executor là cơ chế *thực thi task*. Target là nơi chứa *nghiệp vụ*.

## 5. Ví dụ Spring Framework có thể chạy với Java 17+

Ví dụ này dùng Spring Framework thuần (không yêu cầu Spring Boot) và `ThreadPoolTaskExecutor`. Nó chứng minh từ caller lấy bean qua context tới proxy, rồi phương thức target thực hiện trên thread có tiền tố được cấu hình.

Cấu trúc:

```text
async-call-path/
├── pom.xml
└── src/main/java/demo/AsyncProxyDemo.java
```

Nội dung `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>async-call-path</artifactId>
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

Nội dung `src/main/java/demo/AsyncProxyDemo.java`:

```java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncProxyDemo {

    @Bean("asyncPool")
    public ThreadPoolTaskExecutor asyncPool() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(10);
        pool.setThreadNamePrefix("async-worker-");
        return pool;
    }

    @Bean
    public WorkService workService() {
        return new WorkService();
    }

    public record Answer(int value, String workerThread) {}

    public static class WorkService {
        @Async("asyncPool")
        public CompletableFuture<Answer> calculate(int number) {
            Answer result = new Answer(
                    number * 2,
                    Thread.currentThread().getName()
            );
            return CompletableFuture.completedFuture(result);
        }
    }

    public static void main(String[] args) throws Exception {
        try (var context =
                 new AnnotationConfigApplicationContext(AsyncProxyDemo.class)) {
            WorkService service = context.getBean(WorkService.class);
            String callerThread = Thread.currentThread().getName();

            CompletableFuture<Answer> future = service.calculate(21);
            Answer answer = future.get(5, TimeUnit.SECONDS);

            System.out.println("value = " + answer.value());
            System.out.println("worker name matches = "
                    + answer.workerThread().startsWith("async-worker-"));
            System.out.println("different thread = "
                    + !callerThread.equals(answer.workerThread()));
        }
    }
}
```

Chạy ở thư mục dự án bằng Maven và JDK 17+:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.AsyncProxyDemo
```

Stdout do phần chương trình tạo ra:

```text
value = 42
worker name matches = true
different thread = true
```

Tại sao output này có ý nghĩa?

- `@EnableAsync` bật xử lý annotation trong context.
- `@Bean("asyncPool")` cung cấp executor có thread name `async-worker-`.
- `@Bean workService()` trả target `WorkService`, sau đó Spring có thể cung cấp proxy tới caller. Caller **lấy bean từ context**, không tự khởi tạo `new WorkService()` bên ngoài.
- `service.calculate(21)` đi qua async advice vì method `public` có `@Async("asyncPool")`.
- Worker tính `21*2` và ghi lại tên thread của chính nó.
- `future.get(5, TimeUnit.SECONDS)` đợi kết quả, nên dòng cuối không phụ thuộc vào việc worker nhanh hơn hay chậm hơn caller.
- Kết quả kiểm tra thread name và phép so sánh caller/worker đều đúng: logic target chạy trên worker của pool.

Ví dụ dùng một `record` Java 17 và phương thức `CompletableFuture.completedFuture` trong **target**. Điều này không có nghĩa caller đã nhận một kết quả hoàn tất đồng bộ: Spring interceptor trả về **handle bất đồng bộ** cho caller; `completedFuture` bên trong chỉ phù hợp chữ ký target và chuyển giao kết quả sau khi worker tính xong. Không được lấy thời điểm tạo handle làm bằng chứng target đã chạy xong.

Để người đọc dễ kiểm tra cú pháp, ví dụ có thể dùng trực tiếp với Spring `6.2.9` và JDK 17+. Hành vi đã được đối chiếu bằng JShell trên Spring `6.2.9`: giá trị 42 và hai kiểm tra thread đều `true`. Khi đóng context, Spring quản lý lifecycle của executor; service không tự gọi `shutdown()` cho bean dùng chung.

## 6. Vấn đề quan trọng: self-invocation bỏ qua proxy

Xem một bean có hai phương thức:

```java
@Service
public class InvoiceService {
    public void createInvoice() {
        this.sendEmailAsync(); // Gọi cùng object, bỏ qua proxy async.
    }

    @Async("asyncPool")
    public void sendEmailAsync() {
        // Logic gửi email.
    }
}
```

Đây là **mảnh mã minh họa** cần import `@Service`, `@Async` và cấu hình executor. Khi `createInvoice()` đã chạy **bên trong target**, `this.sendEmailAsync()` là một lời gọi trực tiếp trong cùng object; nó không đi qua proxy nên `@Async` **không được áp dụng cho lời gọi này** trong chế độ proxy mặc định.

Sơ đồ đối chiếu:

```text
ĐÚNG:   OtherBean --> InvoiceService proxy --> async advice --> executor --> target.sendEmailAsync()

BỎ QUA: OtherBean --> InvoiceService proxy --> target.createInvoice()
                                                |
                                                +--> this.sendEmailAsync()
                                                     (không quay lại proxy)
```

Cách thiết kế dễ hiểu nhất là **tách phương thức bất đồng bộ sang một bean khác**, rồi inject bean đó vào thành phần gọi:

```java
@Service
public class EmailTaskService {
    @Async("asyncPool")
    public void sendEmailAsync() {
        // Logic gửi email chạy theo executor.
    }
}

@Service
public class InvoiceService {
    private final EmailTaskService emailTasks;

    public InvoiceService(EmailTaskService emailTasks) {
        this.emailTasks = emailTasks;
    }

    public void createInvoice() {
        emailTasks.sendEmailAsync(); // Đi qua bean proxy của EmailTaskService.
    }
}
```

Hai class trên là **mảnh tích hợp**, cần nằm trong các tệp Java riêng tương ứng hoặc được khai báo phù hợp trong ứng dụng Spring; phương thức `createInvoice` không cần tự biết cách tạo thread.

Một số kỹ thuật như self-injection thông qua proxy hoặc lấy proxy từ context có thể thay đổi đường đi, nhưng làm code phụ thuộc vào AOP/container nhiều hơn. Tách trách nhiệm thành hai bean thường dễ hiểu và dễ kiểm thử.

## 7. Vì sao private/final hoặc tự new có thể làm @Async không chạy?

Trong chế độ `AdviceMode.PROXY`, proxy chỉ chặn những lời gọi **có thể đi qua nó**.

- **`private` method** không phải điểm vào phù hợp để gọi qua Spring proxy. Đừng đặt `@Async` lên một phương thức `private` rồi mong lời gọi nội bộ trở thành bất đồng bộ.
- **`final` class** không thể được subclass bởi class-based proxy. **`final` method** không thể bị class-based proxy override để chặn; cần thận trọng với thiết kế dựa trên CGLIB.
- **JDK dynamic proxy** chặn lời gọi qua **interface** được proxy triển khai; khi caller phụ thuộc interface, kiểu proxy và cách injection ảnh hưởng kiểu bean mà caller nhìn thấy.
- **Class-based proxy** (dựa trên subclass) có thể áp dụng khi không có interface phù hợp hoặc được cấu hình. Nó có giới hạn với `final` và một số phương thức không thể override.
- **Đối tượng được tạo bằng `new` ngoài Spring** không đi qua quy trình proxy của container.
- **Lời gọi qua `this`** vẫn bỏ qua proxy, dù phương thức có khai báo `public` và annotation hợp lệ.

Không nên khẳng định mọi `final` method luôn không thể được intercept trong **mọi loại proxy**: ràng buộc override áp dụng trực tiếp cho class-based proxy, còn JDK interface proxy là mô hình chặn khác. Tuy nhiên đối với code nghiệp vụ cần `@Async` đáng tin, nên dùng một phương thức public có thể advice và gọi qua bean Spring-managed.

Có thể kiểm tra trong thử nghiệm:

```java
import org.springframework.aop.support.AopUtils;

boolean proxied = AopUtils.isAopProxy(context.getBean(WorkService.class));
```

Đây là **mảnh kiểm tra** chạy trong phạm vi có `context` và `WorkService` của ví dụ. Nó chỉ xác nhận đối tượng đang cầm là AOP proxy, **không tự chứng minh mọi lời gọi ở mọi đường đi đều đi qua advice**.

## 8. @EnableAsync, tên executor và việc lựa chọn nơi thực thi

`@EnableAsync` kích hoạt xử lý annotation ở một Spring application context. `@Async` có thể chỉ định tên bean/qualifier của executor:

```java
@Async("asyncPool")
public CompletableFuture<Integer> calculate() {
    return CompletableFuture.completedFuture(42);
}
```

Đây là **mảnh method trong Spring-managed bean**, không phải một file Java độc lập.

`@Async("asyncPool")` gắn công việc với bean `asyncPool`. Nếu không chỉ định tên, Spring chọn executor theo cấu hình hỗ trợ async (`AsyncConfigurer`, bean executor phù hợp hoặc cơ chế fallback tùy framework/phiên bản). Vì vậy khi cần biết chính xác task chạy ở pool nào, hãy **khai báo và chọn executor rõ ràng** thay vì đoán theo tên thread mặc định.

Một số khác biệt quan trọng:

| Abstraction | Vai trò | Có tự tạo proxy @Async không? |
| --- | --- | --- |
| `@Async` | Đánh dấu phương thức là ứng viên chạy async | Không nếu không được hỗ trợ async và gọi đúng đường đi |
| `@EnableAsync` | Kích hoạt hạ tầng xử lý annotation | Bật cơ chế, còn mỗi lời gọi vẫn phải đi qua điểm chặn |
| `AsyncTaskExecutor` | Hợp đồng submit tác vụ bất đồng bộ | Không; nó là hợp đồng thực thi |
| `ThreadPoolTaskExecutor` | Implementation thread pool cho executor | Không; nó quản lý worker và queue |
| `AsyncAnnotationBeanPostProcessor`/advisor | Hỗ trợ áp async advice trên bean phù hợp | Tham gia tạo/gắn advice vào proxy |
| `Future`/`CompletableFuture` | Handle trạng thái và kết quả | Không; chúng không quyết định có proxy hay không |

`@EnableAsync` mặc định dùng `AdviceMode.PROXY`. Chế độ `AdviceMode.ASPECTJ` sử dụng weaving khi được cấu hình cùng các thành phần AspectJ cần thiết; nó có mô hình chặn khác và có thể xử lý self-invocation, nhưng **không phải** hành vi mặc định của Spring `@Async`. Đừng đổi mode chỉ để “sửa” self-invocation khi tách bean là thiết kế đơn giản hơn.

## 9. Điều gì xảy ra với void, Future và CompletableFuture?

Kiểu trả về của phương thức quyết định caller quan sát được gì sau khi proxy gửi tác vụ.

### void

```java
@Async("asyncPool")
public void writeAuditLog() {
    // Xử lý trên executor khi lời gọi đi qua proxy.
}
```

Đây là **mảnh method**. Caller không nhận `Future`, nên không thể gọi `get()` để nhận kết quả hoặc ngoại lệ. Nếu worker ném exception chưa được xử lý, Spring có cơ chế `AsyncUncaughtExceptionHandler` dành cho phương thức `void`; cần cấu hình logging/giám sát phù hợp. Exception nghiệp vụ ở worker không tự xuất hiện trong `try/catch` của caller sau một lời gọi `void` đã submit thành công.

### Future<T>

Phương thức `@Async` có thể khai báo `Future<T>`; phía proxy trả một Future dùng để nhận kết quả. Caller có thể `get()`, chờ timeout, hoặc quan sát hủy nếu implementation hỗ trợ.

### CompletableFuture<T>

`CompletableFuture<T>` hỗ trợ `thenApply`, `thenCompose`, `exceptionally`, `handle`, `whenComplete` và các phương thức kết hợp khác. Target có thể trả `CompletableFuture.completedFuture(value)` sau khi tính toán trên worker, trong khi caller nhận handle bất đồng bộ từ proxy. Callback không có hậu tố `Async` có thể chạy trên thread đang hoàn tất stage, vì vậy **không nên mặc định callback chạy trên caller thread**.

So sánh ngắn:

| Kiểu khai báo | Caller nhận | Nơi thấy lỗi của worker |
| --- | --- | --- |
| `void` | Không có handle hoàn tất | Handler/log/giám sát ở async infrastructure |
| `Future<T>` | `Future<T>` | `get()` ném `ExecutionException` chứa cause |
| `CompletableFuture<T>` | `CompletableFuture<T>` | `get()`, `join()`, `handle`, `exceptionally`... |

`get()` làm **thread gọi nó chờ** nếu task chưa hoàn tất. Như vậy `@Async` không có nghĩa mọi đoạn code sử dụng nó đều không chặn: nếu controller gọi `future.get()` ngay lập tức, HTTP request vẫn có thể phải đợi.

## 10. Hai loại lỗi khác nhau trong đường đi lời gọi

**Lỗi lúc submit** xảy ra trên nhánh caller → proxy → executor, ví dụ executor không tìm được, đã đóng hoặc hàng đợi/thread pool từ chối tác vụ. Spring `ThreadPoolTaskExecutor` thường báo `TaskRejectedException` khi quá tải. Đây là lỗi **chưa thực thi tác vụ hợp lệ**, có thể xuất hiện ngay khi gọi phương thức `@Async` (tùy cấu hình và thời điểm).

**Lỗi khi chạy task** xảy ra sau khi worker đã bắt đầu gọi target. Ví dụ truy vấn database lỗi hoặc logic ném `IllegalStateException`:

- Nếu return type là `Future<T>`, đọc lỗi qua `Future.get()` và `ExecutionException.getCause()`.
- Nếu là `CompletableFuture<T>`, có thể xử lý bằng `exceptionally`/`handle`, hoặc đọc `CompletionException` từ `join()`.
- Với `void`, không có handle trả cho caller; dùng handler/log/metric thích hợp.

Đừng đặt `try/catch` ở caller rồi cho rằng nó sẽ bắt mọi exception bên trong một `@Async void` chạy trên worker:

```java
try {
    bean.sendEmailAsync();
} catch (RuntimeException ex) {
    // Có thể bắt lỗi ở nhánh submit, không phải mọi lỗi chạy sau đó.
}
```

Đây là **mảnh minh họa** có bean được Spring inject. Nếu cần caller biết task thành công hay thất bại, hãy thiết kế API trả `CompletableFuture` hoặc một cơ chế lưu trạng thái tác vụ.

## 11. Ngữ cảnh theo thread không đi theo proxy một cách thần kỳ

Đường đi từ caller tới worker là một **ranh giới thread**. Điều này ảnh hưởng:

- **`ThreadLocal` và MDC logging**: giá trị gắn với caller thread không tự đảm bảo có mặt trên worker. Với executor có thread pool, worker được tái sử dụng; nếu truyền context, cần khôi phục và xóa sau mỗi task.
- **Security context**: cần cơ chế chuyển ngữ cảnh bảo mật có chủ ý, chẳng hạn executor wrapper của Spring Security khi dùng Spring Security.
- **Transaction context**: giao dịch Spring thông thường gắn với thread thực thi; không tự xem transaction của caller là một transaction chung cho worker. Nếu task cần transactional boundary, thiết kế giao dịch bên worker và kiểm tra advisor/transaction propagation phù hợp.
- **Trace context**: cần instrument hoặc context propagation tương thích với thư viện quan sát; tránh tưởng chỉ thêm `@Async` là trace ID tự xuất hiện trên worker.

Một `TaskDecorator` có thể wrap một `Runnable` thực thi để truyền và khôi phục một số context có chọn lọc. Nhưng decorator không thay thế cơ chế quản lý transaction và có chi phí; hãy làm rõ context nào thực sự cần cho nghiệp vụ.

Đừng chuyển nguyên object chứa dữ liệu mutable giữa caller và worker mà không bảo đảm an toàn đồng thời. Các hành động trước khi submit có một số bảo đảm *happens-before* theo contract của executor JDK, nhưng điều đó không làm `HashMap` dùng chung trở thành thread-safe nếu nhiều thread tiếp tục sửa đồng thời.

## 12. Proxy và worker ảnh hưởng gì đến thời gian trả về?

Có thể phân biệt các mốc thời gian:

```text
T0 caller invoke proxy
T1 executor nhận task
T2 proxy trả handle về caller
T3 worker bắt đầu chạy target
T4 target kết thúc
T5 Future hoàn tất
T6 caller xử lý callback/get
```

Đây là **mốc logic**, không phải thứ tự tuyệt đối giữa T2 và T3: worker có thể chạy trước khi proxy trả về caller. Với executor có queue, khoảng từ T1 tới T3 có thể dài đáng kể; như vậy thời gian từ caller nhận handle đến khi tác vụ hoàn tất chịu ảnh hưởng của cả **queue wait** và **execution time**.

Nếu đo hiệu năng, phân biệt:

1. **Submit latency**: caller mất bao lâu để đi qua proxy và được executor nhận việc.
2. **Queue wait**: bao lâu từ lúc submit tới lúc worker bắt đầu target.
3. **Execution time**: target thật chạy trong bao lâu.
4. **End-to-end completion**: toàn bộ thời gian từ yêu cầu tới kết quả cuối cùng.

`ThreadPoolTaskExecutor` có các tham số `corePoolSize`, `maxPoolSize`, `queueCapacity`; nếu queue còn chỗ, tăng `maxPoolSize` chưa chắc tăng số worker đang chạy. Sự khác biệt giữa “proxy đã trả về” và “task đã thực sự xong” là lý do phải đo **task completion**, không chỉ đo HTTP response khi request trả trạng thái accepted.

## 13. Cách chẩn đoán @Async không chạy trên thread khác

Khi một phương thức có `@Async` vẫn in cùng tên thread với caller, kiểm tra theo thứ tự sau:

1. **`@EnableAsync` đã có trong đúng application context chưa?** Một context khác hoặc thiếu cấu hình có thể khiến bean không được xử lý như kỳ vọng.
2. **Đối tượng caller gọi có phải bean do Spring inject không?** Nếu `new` trực tiếp, không có async proxy.
3. **Có self-invocation không?** `this.asyncMethod()` trong cùng target thường bypass proxy.
4. **Phương thức có được advice không?** Kiểm tra visibility, `final` đối với subclass proxy, lớp/class proxy và interface injection.
5. **Executor nào được chọn?** `@Async("name")` trỏ tới bean nào; cấu hình pool có thực sự khác caller thread không?
6. **Đã đo bên trong target chưa?** Chỉ xem thread trên caller sau khi gọi `@Async` không chứng minh worker chạy ở đâu.
7. **Có gọi `future.get()` ngay sau submit?** Điều này làm caller chờ, nhưng không có nghĩa target chạy trực tiếp trên caller.
8. **Có lỗi submit/rejection không?** Task chưa được nhận không thể chạy trên worker; nên quan sát queue, active workers và exception.

Mẹo đo tối giản (đoạn tích hợp Spring):

```java
String before = Thread.currentThread().getName();
CompletableFuture<Answer> future = service.calculate(21);
Answer answer = future.get(5, TimeUnit.SECONDS);

System.out.println("caller: " + before);
System.out.println("worker: " + answer.workerThread());
```

Hai tên thread đầy đủ **phụ thuộc runtime**, nên không dùng chúng làm stdout cố định trong tài liệu kiểm thử; ví dụ hoàn chỉnh ở mục 5 kiểm tra prefix thay vì yêu cầu một tên thread chính xác. Bên cạnh log, có thể kiểm tra `AopUtils.isAopProxy(bean)` để xác nhận loại tham chiếu nhưng vẫn cần thử **đường đi lời gọi thực sự**.

## 14. Một số ngộ nhận cần tránh

1. **“Có annotation `@Async` là chắc chắn chạy khác thread.”** Sai: phải bật hạ tầng, lời gọi đúng đường qua proxy, và executor phù hợp.
2. **“Spring proxy chỉ để logging.”** Sai: nó thực thi advice, có thể thay đổi cách lời gọi được xử lý, bao gồm submit task vào executor.
3. **“TaskExecutor tự nhận ra annotation.”** Không: hạ tầng `@Async` và proxy mới gắn hành vi async cho method; executor chỉ thực thi task đã được giao.
4. **“Gọi method `@Async` từ method cùng bean vẫn async.”** Trong proxy mode mặc định, self-invocation bypass advice.
5. **“Annotation chạy trên worker, còn mọi code trong target chạy trên caller.”** Ngược lại trong đường đi tiêu chuẩn: interceptor định tuyến, target chạy khi worker thực thi task.
6. **“Chỉ vì Future đã được trả về nên công việc đã xong.”** Future có thể còn pending/queue/running.
7. **“`@Async` tự chuyển transaction, MDC và security context từ caller.”** Không được mặc định; phải cấu hình truyền context và transactional boundary theo nhu cầu.
8. **“Method ném exception nên `try/catch` caller sẽ bắt được.”** Với lỗi worker async, cần Future, callback hoặc handler phù hợp.
9. **“Cứ tăng `maxPoolSize` thì worker sẽ lập tức tăng.”** Không đúng nếu queue còn rộng hoặc tác vụ đang chờ phụ thuộc bên ngoài.
10. **“`@Async` luôn tương đương `CompletableFuture.supplyAsync()`.”** Chúng đều hỗ trợ bất đồng bộ nhưng `@Async` phụ thuộc vào Spring proxy, bean lifecycle và interceptor; `supplyAsync` là API Java có lựa chọn executor riêng.
11. **“Một `@Async` method trên class cấu hình là cách chuẩn để khởi chạy job khi tạo bean.”** Không nên áp `@Async` trên phương thức `@Configuration` hoặc mong `@PostConstruct` trong cùng bean được proxy intercept. Hãy dùng thành phần điều phối sau khi bean sẵn sàng.

## 15. Tự kiểm tra

**Câu 1.** Controller inject `ReportService` từ Spring rồi gọi `calculate()` được đánh dấu `@Async`. Lời gọi đi qua những thành phần nào?  
**Đáp án:** Caller → bean proxy → async interceptor → executor/queue → worker → target method; kết quả/lỗi được phản ánh qua handle thích hợp nếu return type cho phép.

**Câu 2.** Trong cùng class, `run()` gọi `this.sendAsync()`. Tại sao không chạy async?  
**Đáp án:** Đây là lời gọi nội bộ trên target, không đi qua Spring proxy trong `AdviceMode.PROXY` mặc định.

**Câu 3.** `@Async("asyncPool")` có làm method chạy trên thread tên `asyncPool` không?  
**Đáp án:** Nó chọn executor bean/qualifier tên `asyncPool`; thread name và chính sách tạo thread do implementation/configuration quyết định.

**Câu 4.** Vì sao target `CompletableFuture.completedFuture(value)` không phủ định bản chất async?  
**Đáp án:** `completedFuture` được tạo **bên trong worker** để đáp ứng chữ ký target; proxy đã chuyển lời gọi sang executor và trả handle bất đồng bộ cho caller.

**Câu 5.** Worker ném exception trong `@Async void`. Có nên mong `try/catch` ngay sau lời gọi ở caller bắt được?  
**Đáp án:** Không; phải dùng handler/log/metric cho phương thức void hoặc thiết kế API trả Future/CompletableFuture để quan sát lỗi.

**Câu 6.** Có cách nào để self-invocation được intercept mà không tách bean?  
**Đáp án:** Có thể thay đổi thiết kế gọi qua proxy hoặc dùng `AdviceMode.ASPECTJ` với weaving phù hợp, nhưng tách tác vụ bất đồng bộ sang bean riêng là lựa chọn thường dễ hiểu và dễ bảo trì hơn.

**Ghi nhớ:** lời gọi bất đồng bộ trong Spring là kết quả của **đường đi qua proxy cộng với chính sách executor**. Khi phân tích bug hay performance, luôn tách *caller*, *proxy/interceptor*, *executor/queue*, *worker* và *target*.

### Tài liệu tham khảo

- [Spring Framework Reference — Task Execution and Scheduling (@Async, proxy mode, executor qualification)](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — @Async JavaDoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Async.html)
- [Spring Framework — @EnableAsync JavaDoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Spring Framework — AsyncAnnotationBeanPostProcessor JavaDoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/AsyncAnnotationBeanPostProcessor.html)
- [Spring Framework — ThreadPoolTaskExecutor JavaDoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-invocation-002">Vì sao phương thức tự gọi trong cùng bean không kích hoạt @Async ở chế độ proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao phương thức tự gọi trong cùng bean không kích hoạt @Async ở chế độ proxy?

## 1. Vấn đề thường gặp: có @Async nhưng công việc vẫn chạy đồng bộ

Trong Spring Framework, `@Async` được dùng để yêu cầu Spring thực thi một method trên executor, thay vì tiếp tục chạy phần thân method ngay trên thread gọi. Một trường hợp rất dễ gây nhầm lẫn:

```java
@Service
public class MailService {

    public void register(String id) {
        validate(id);
        this.sendMail(id); // Tự gọi trong cùng bean.
    }

    @Async("mailExecutor")
    public void sendMail(String id) {
        // Gửi email hoặc công việc tốn thời gian.
    }

    private void validate(String id) {
        // Kiểm tra dữ liệu.
    }
}
```

Đây là **fragment minh họa**, chưa phải chương trình chạy độc lập; cần Spring-managed bean, cấu hình executor và bật hỗ trợ async. Rất nhiều người thấy có đủ `@Async`, `@EnableAsync` và một thread pool nhưng `sendMail()` vẫn chạy trên thread đang thực hiện `register()`.

**Nguyên nhân không phải do Spring bỏ sót annotation.** Với chế độ AOP proxy mặc định, Spring chỉ áp dụng async interceptor khi lời gọi method **đi qua proxy**. Lời gọi `this.sendMail(id)` nằm trong cùng object/target và **không đi qua Spring proxy**, nên interceptor không được kích hoạt.

Kết quả:

- Thread gọi `register()` trực tiếp chạy `sendMail()`.
- Nếu gửi email chờ mạng, thread gọi có thể bị chặn.
- Nếu method ném exception, đường truyền lỗi có thể khác với lần gọi được proxy xử lý.
- Có thể có bean proxy và executor hoàn toàn đúng, nhưng riêng đường self-invocation vẫn không async.

**Quy tắc ngắn:** có `@Async` trên method **không có nghĩa** mọi lời gọi đến method đó đều bất đồng bộ. Cần kiểm tra *đường đi của lời gọi*.

## 2. @Async hoạt động như thế nào trong Spring proxy mode?

Khi bật xử lý annotation bằng `@EnableAsync` trong Java config hoặc `<task:annotation-driven>` trong XML, Spring đăng ký hạ tầng xử lý `@Async`. Ở chế độ mặc định `AdviceMode.PROXY`, Spring có thể đưa một proxy vào ApplicationContext tại vị trí người dùng lấy bean.

Khái niệm đơn giản:

```text
                         ApplicationContext
                                |
                   bean exposed to callers
                                |
                        Spring AOP proxy
                                |
         external call to @Async method intercepted?
                                |
                    Async interceptor / advice
                                |
                       submit to executor
                                |
                    worker thread invokes target
                                |
                         method body runs
```

Khi một bean khác nhận dependency từ Spring, dependency đó có thể là **proxy** thay cho target gốc. Proxy có khả năng nhận lời gọi, kiểm tra advice, rồi quyết định submit task vào executor.

Điểm quan trọng là proxy không “viết lại” mọi lời gọi Java xuất hiện bên trong class. Khi bạn đã đi vào code của target, biểu thức `this.someMethod()` là lời gọi method trên chính instance đang thực hiện phần thân, không phải một lời gọi mới qua bean reference lấy từ ApplicationContext.

### 2.1. Hai đường gọi trông giống nhau nhưng có kết quả khác

```text
ĐƯỜNG A — Gọi qua bean/proxy từ bên ngoài:

Controller / Facade
      |
      v
Spring proxy (MailWorker)
      |
      +-- @Async interceptor --> mailExecutor.submit(...)
                                   |
                                   v
                              worker thread
                                   |
                                   v
                              MailWorker.deliver()

ĐƯỜNG B — Tự gọi bên trong cùng target:

Controller / Facade
      |
      v
Spring proxy (MailWorker)
      |
      v
MailWorker.selfCall()  [target method body]
      |
      +-- this.deliver() ---------> MailWorker.deliver()
                                     (KHÔNG vào lại proxy)
                                     (KHÔNG có async interceptor)
```

Ngay cả ở đường B, điểm vào `selfCall()` có thể được gọi qua proxy. Nhưng vì `selfCall()` không phải async advice point trong ví dụ, nó chỉ ủy quyền đến target. Khi target tự gọi `deliver()`, không phát sinh một lượt interception mới.

### 2.2. Không phải tính năng riêng của CGLIB

Spring AOP có thể dùng JDK dynamic proxy hoặc class-based proxy. Cả hai đều dựa trên nguyên lý interception ở **biên lời gọi đi qua proxy**.

- Với JDK dynamic proxy, các lời gọi khả dụng thường đi qua interface mà proxy implement.
- Với class-based proxy, Spring dùng subclass proxy, nên client có thể lấy bean theo class trong những tình huống hỗ trợ.
- **Cả hai cách không giải quyết self-invocation chỉ bằng việc đổi loại proxy.** Đặt `@EnableAsync(proxyTargetClass = true)` không tự biến `this.sendMail()` thành một lời gọi qua proxy.

Một sự thật ít hiển nhiên: `AopUtils.isAopProxy(bean) == true` chỉ xác nhận reference bạn đang cầm là proxy; nó **không bảo đảm** mỗi lời gọi bên trong target đều đi qua advice.

## 3. Điều kiện cần để @Async thực sự được chặn

Trong Spring Framework thuần, muốn một lời gọi được chạy async theo proxy mode thì cần xem đồng thời các điều kiện:

1. **Bật xử lý annotation:** có `@EnableAsync` hoặc cấu hình XML `<task:annotation-driven>` được load vào context phù hợp.
2. **Bean được Spring quản lý:** client phải sử dụng bean mà Spring tạo và post-process; đối tượng tự `new MailWorker()` ngoài container không tự nhận AOP advice.
3. **Đường gọi đi qua proxy:** bean khác gọi một method được advice intercept, thay vì target tự gọi bằng `this.method()`.
4. **Method phù hợp cơ chế proxy:** không trông đợi interception trên method `private`, method `final` với class-based proxy, hoặc lời gọi không thuộc bề mặt interface tương ứng của JDK proxy.
5. **Executor phù hợp:** nếu chỉ định `@Async("mailExecutor")`, cần có bean executor tương ứng; executor thực tế phải được cấu hình theo yêu cầu vận hành.
6. **Method không bị gọi quá sớm trong lifecycle:** tự gọi từ constructor hoặc callback khởi tạo như `@PostConstruct` không phải cách đáng tin để tạo lời gọi `@Async` qua proxy.

Mất một điều kiện có thể khiến behavior khác mong đợi. Tuy nhiên **self-invocation là lỗi về đường đi của lời gọi**, không sửa được chỉ bằng cách tăng pool hoặc thêm `@EnableAsync` lần nữa.

## 4. Ví dụ Java 21 + Spring Framework 6.2.1 chạy được

Ví dụ kiểm chứng độc lập ba đường gọi:

- **Self-invocation:** method của bean gọi `this.deliver(...)`.
- **External invocation:** code lấy bean từ Spring và gọi `deliver(...)` qua reference proxy.
- **Cross-bean invocation:** một Spring bean khác được inject dependency và gọi `deliver(...)`.

Chương trình chủ động kiểm tra thread gọi và prefix của executor, thay vì dùng `Thread.sleep(...)` để đoán thời điểm kết thúc. Kết quả in ra dưới dạng boolean để không phụ thuộc tên thread do Maven hoặc hệ điều hành đặt.

### 4.1. Cấu trúc

```text
async-self-demo/
├── pom.xml
└── src/main/java/demo/SelfInvocationDemo.java
```

### 4.2. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>async-self-demo</artifactId>
  <version>1.0</version>
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
          <mainClass>demo.SelfInvocationDemo</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

Không cần Spring Boot để quan sát nguyên lý. Dependency `spring-context` cho phép cấu hình context, `@EnableAsync`, `ThreadPoolTaskExecutor` và sử dụng hạ tầng AOP cần thiết trong bài.

### 4.3. Tệp src/main/java/demo/SelfInvocationDemo.java

```java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class SelfInvocationDemo {
    public static class MailWorker {
        @Async("mailExecutor")
        public CompletableFuture<String> deliver(String id) {
            return CompletableFuture.completedFuture(
                    Thread.currentThread().getName() + "|" + id);
        }

        public CompletableFuture<String> selfCall(String id) {
            return this.deliver(id);
        }
    }

    public static class MailFacade {
        private final MailWorker worker;

        public MailFacade(MailWorker worker) {
            this.worker = worker;
        }

        public CompletableFuture<String> crossBeanCall(String id) {
            return worker.deliver(id);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class AppConfig {
        @Bean("mailExecutor")
        public ThreadPoolTaskExecutor mailExecutor() {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(1);
            pool.setMaxPoolSize(2);
            pool.setQueueCapacity(8);
            pool.setThreadNamePrefix("mail-");
            return pool;
        }

        @Bean
        public MailWorker mailWorker() {
            return new MailWorker();
        }

        @Bean
        public MailFacade mailFacade(MailWorker worker) {
            return new MailFacade(worker);
        }
    }

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            String caller = Thread.currentThread().getName();
            MailWorker worker = context.getBean(MailWorker.class);
            MailFacade facade = context.getBean(MailFacade.class);

            String self = worker.selfCall("S").get(5, TimeUnit.SECONDS);
            String external = worker.deliver("E").get(5, TimeUnit.SECONDS);
            String crossBean =
                    facade.crossBeanCall("C").get(5, TimeUnit.SECONDS);

            System.out.println("proxy=" + AopUtils.isAopProxy(worker));
            System.out.println("selfCallOnCaller=" +
                    self.equals(caller + "|S"));
            System.out.println("externalCallOnPool=" +
                    (external.startsWith("mail-") && external.endsWith("|E")));
            System.out.println("crossBeanCallOnPool=" +
                    (crossBean.startsWith("mail-") && crossBean.endsWith("|C")));

            if (!AopUtils.isAopProxy(worker)
                    || !self.equals(caller + "|S")
                    || !external.startsWith("mail-")
                    || !crossBean.startsWith("mail-")) {
                throw new AssertionError("Async proxy behavior not as expected");
            }
        }
    }
}
```

### 4.4. Chạy và kết quả kiểm chứng

Chạy trong thư mục chứa `pom.xml`, có Maven và JDK 21 trở lên:

```bash
mvn -q -DskipTests compile exec:java
```

Output đã được biên dịch và chạy thực tế với Spring Framework 6.2.1 (có thể xuất hiện log riêng từ Maven/JVM tùy môi trường):

```text
proxy=true
selfCallOnCaller=true
externalCallOnPool=true
crossBeanCallOnPool=true
```

Diễn giải từng dòng:

- `proxy=true`: reference `worker` lấy qua context là một Spring AOP proxy.
- `selfCallOnCaller=true`: gọi `worker.selfCall("S")` cuối cùng đi đến `this.deliver("S")` trong target; phần thân `deliver` chạy trên đúng thread đã gọi `selfCall`.
- `externalCallOnPool=true`: gọi trực tiếp `worker.deliver("E")` từ bên ngoài target đi qua proxy nên interceptor submit công việc vào executor có thread prefix `mail-`.
- `crossBeanCallOnPool=true`: `MailFacade` được inject bean `MailWorker` từ Spring; lời gọi `worker.deliver("C")` trong facade đi qua proxy và thực thi trên pool.

Đây là phép so sánh được thiết kế để phân biệt **call path**: cùng method `deliver`, cùng annotation và cùng cấu hình, nhưng có đường chạy đồng bộ và có đường chạy bất đồng bộ.

Không in một tên thread cố định như `main` hoặc `mail-1` vì tên caller khi chạy qua Maven có thể khác; phép kiểm tra so sánh bằng reference thread name tại runtime và prefix đã đặt mới là phần ổn định. Ngoài ra, `Future.get(timeout)` bảo đảm chương trình đợi hoàn tất đúng lúc thay vì phụ thuộc thứ tự scheduler.

## 5. Phân tích chi tiết luồng chạy của ví dụ

### 5.1. Khi gọi worker.selfCall("S")

Mã ở caller:

```java
String self = worker.selfCall("S").get(5, TimeUnit.SECONDS);
```

Đây là một **fragment trích từ chương trình hoàn chỉnh**. Reference `worker` là bean proxy, nhưng `selfCall` không gắn `@Async`. Khi code bên trong target chạy đến:

```java
return this.deliver(id);
```

nó gọi trực tiếp method của target và bỏ qua proxy/interceptor `@Async`. Vì thế `Thread.currentThread().getName()` trong `deliver` bằng thread ban đầu của caller. Việc method trả về một `CompletableFuture` đã hoàn thành **không chứng minh rằng nó đã thực thi bất đồng bộ**.

### 5.2. Khi gọi worker.deliver("E")

Mã ở caller:

```java
String external = worker.deliver("E").get(5, TimeUnit.SECONDS);
```

Lời gọi đi qua proxy. Async advice chọn bean `mailExecutor` từ tên trong `@Async("mailExecutor")` và submit task. Worker thực thi phần thân method, hoàn thành `CompletableFuture`, sau đó caller lấy kết quả qua `get()`.

Lưu ý `get()` là lời gọi **blocking ở caller**, dù task được chạy trên pool. Ví dụ chủ động dùng nó để quan sát kết quả; trong đường HTTP production nhạy latency, không nên submit rồi đợi vô hạn ngay lập tức nếu mục tiêu là giải phóng request thread.

### 5.3. Khi gọi facade.crossBeanCall("C")

`MailFacade` nhận `MailWorker` qua constructor injection:

```java
public MailFacade(MailWorker worker) {
    this.worker = worker;
}
```

Dependency này được container cung cấp, có thể là reference proxy. Do đó khi facade gọi `worker.deliver("C")`, nó chạm đúng advice. Đây là một hướng sửa lỗi self-invocation rõ ràng vì biến dependency thể hiện **ranh giới giữa orchestration và công việc nền**.

### 5.4. Vì sao không chỉ nhìn tên thread?

Nếu một method `@Async` bên ngoài đã được gọi qua proxy, nó sẽ bắt đầu chạy trên worker. Nếu *bên trong worker ấy* lại dùng `this.otherAsync()` để tự gọi method khác, method bên trong vẫn chạy ngay trên **cùng worker**, không tạo lượt submit mới.

Vì thế, chỉ thấy log trên `mail-1` không đủ để kết luận mọi annotation async lồng nhau đã được chặn. Cần phân biệt:

- **Ai đưa lời gọi đầu tiên lên pool?**
- **Method đang quan sát có thực sự đi qua proxy không?**
- **Có tạo một task/submission mới hay chỉ là lời gọi Java thường trong worker?**

## 6. Cách sửa ưu tiên: tách công việc async sang bean riêng

Khi một service vừa xử lý yêu cầu vừa muốn gửi công việc nền, phương án dễ bảo trì là **tách trách nhiệm**. Ví dụ thiết kế:

```java
@Service
public class NotificationWorker {
    @Async("mailExecutor")
    public CompletableFuture<Void> send(String id) {
        // Công việc nền: tải dữ liệu, gửi thông báo, ghi log phù hợp.
        return CompletableFuture.completedFuture(null);
    }
}

@Service
public class RegistrationService {
    private final NotificationWorker notificationWorker;

    public RegistrationService(NotificationWorker notificationWorker) {
        this.notificationWorker = notificationWorker;
    }

    public void register(String id) {
        // Xử lý đăng ký và kiểm tra hợp lệ.
        notificationWorker.send(id); // Lời gọi sang bean được inject.
    }
}
```

Đây là **fragment ứng dụng**, yêu cầu `@EnableAsync`, bean `mailExecutor`, các import `org.springframework.stereotype.Service`, `org.springframework.scheduling.annotation.Async` và `java.util.concurrent.CompletableFuture`, cùng logic nghiệp vụ thực tế.

Lợi ích:

1. `RegistrationService` chỉ quản lý luồng nghiệp vụ.
2. `NotificationWorker` quản lý ranh giới xử lý async.
3. Lời gọi từ `RegistrationService` đến `NotificationWorker` qua Spring-managed dependency có cơ hội đi qua proxy.
4. Dễ kiểm thử orchestration bằng mock/fake worker, kiểm thử async behavior bằng integration test riêng.
5. Tránh circular dependency hoặc gọi ngược ApplicationContext chỉ để lấy lại chính bean của mình.

**Chú ý:** nếu caller không giữ và quan sát `CompletableFuture`, phải có cơ chế ghi nhận thất bại phù hợp. Không nên bỏ Future của công việc quan trọng mà vẫn mặc định rằng nó sẽ hoàn tất.

Đây thường là giải pháp cân bằng nhất: cấu trúc rõ ràng và không cần can thiệp sâu vào Spring AOP.

## 7. Một lựa chọn khác: gửi tác vụ trực tiếp qua AsyncTaskExecutor

Không phải yêu cầu nào cũng cần annotation. Nếu một service có trách nhiệm điều phối task, có thể chủ động gọi `executor.submit(...)` hoặc `submitCompletable(...)`. Khi đó việc bất đồng bộ xảy ra **ngay tại lệnh submit**, không dựa vào việc Java gọi method qua proxy.

Ví dụ:

```java
@Service
public class ReportService {
    private final AsyncTaskExecutor executor;

    public ReportService(
            @Qualifier("reportExecutor") AsyncTaskExecutor executor) {
        this.executor = executor;
    }

    public CompletableFuture<String> startReport(String reportId) {
        return executor.submitCompletable(
                () -> generateReport(reportId));
    }

    private String generateReport(String reportId) {
        // Công việc nền có thể là private method vì
        // đây là Callable chạy do executor submit, không phải @Async proxy.
        return "report:" + reportId;
    }
}
```

Đây là **fragment**, cần Spring Framework có `AsyncTaskExecutor.submitCompletable(...)` (Spring 6.1+), một bean `reportExecutor` phù hợp, `@Service`, `@Qualifier` và imports tương ứng.

Ưu điểm:

- Không bị vấn đề self-invocation của `@Async` khi gọi private `generateReport`: worker nhận một lambda do `submitCompletable` tạo ra.
- Phân biệt rõ vị trí gửi task với business method.
- Có thể nhận `CompletableFuture<String>` để quản lý kết quả và exception.

Nhược điểm:

- Business/orchestration code biết đến executor API, thay vì chỉ dùng annotation.
- Cần quản lý kết quả, timeout, rejection và context propagation như mọi task nền.
- Không tự làm thao tác blocking I/O bên trong method thành nonblocking; nó chỉ chuyển nơi chạy sang executor.

**Lưu ý về API:** `TaskExecutor` cơ bản chỉ có `execute(Runnable)`; nếu cần `submitCompletable` thì dùng `AsyncTaskExecutor` phù hợp.

## 8. Có thể tự lấy proxy hoặc tự inject chính bean không?

Về kỹ thuật, một số giải pháp gián tiếp có thể đưa lời gọi đi qua proxy, chẳng hạn tra cứu bean qua `ApplicationContext`, self-injection thông qua một indirection phù hợp, hoặc dùng `AopContext.currentProxy()` khi AOP được cấu hình `exposeProxy` và lời gọi nằm trong đúng ngữ cảnh proxy.

Tuy nhiên đây **không phải phương án mặc định nên chọn**, vì:

- Class bắt đầu phụ thuộc trực tiếp vào container/AOP infrastructure.
- Self-injection có thể tạo vòng phụ thuộc, khó theo dõi trong constructor và phụ thuộc vào cấu hình Spring.
- `AopContext.currentProxy()` yêu cầu điều kiện bổ sung, không hoạt động với mọi lời gọi, và làm business code khó tái sử dụng ngoài proxy context.
- Người đọc code khó hiểu ranh giới nghiệp vụ hơn so với tách hai bean.

Nếu một thiết kế phải gọi chính proxy của mình để “kích hoạt annotation”, hãy xem đó là tín hiệu nên đánh giá lại trách nhiệm của class. **Tách worker sang bean riêng** thường đơn giản và kiểm thử tốt hơn.

## 9. AspectJ có xử lý self-invocation được không?

Có, nhưng phải hiểu đây là một **chế độ advice khác**, không chỉ là tùy chọn đổi kiểu proxy.

Với `@EnableAsync(mode = AdviceMode.PROXY)` (mặc định), Spring dùng AOP proxy: method self-call không đi qua advice.

Với `@EnableAsync(mode = AdviceMode.ASPECTJ)`, Spring có thể áp dụng advice bằng AspectJ weaving lên class. Khi **weaving thực sự được cấu hình và hoạt động** (compile-time hoặc load-time weaving, kèm dependencies tương ứng như `spring-aspects`), những lời gọi nội bộ có thể được intercept vì advice không còn phụ thuộc proxy boundary.

Đoạn khai báo minh họa:

```java
@Configuration
@EnableAsync(mode = AdviceMode.ASPECTJ)
public class AsyncAspectConfig {
    // Cần cấu hình và dependencies AspectJ weaving thực sự.
}
```

Đây **không phải ví dụ chạy độc lập**: thêm annotation này mà không có weaving được cấu hình đúng sẽ không tự khiến self-invocation bất đồng bộ. Hơn nữa, lựa chọn AspectJ làm tăng độ phức tạp build/deploy/debug. Hãy dùng khi có yêu cầu AOP liên quan nhiều call site thực sự đáng giá; không nên chuyển toàn bộ hệ thống sang weaving chỉ để sửa một method tự gọi.

**Không nhầm `proxyTargetClass=true` với AspectJ.** Tham số này chỉ chọn class-based proxies trong PROXY mode; self-call vẫn bypass advice.

## 10. Khi self-invocation làm thay đổi exception và Future semantics

Giả sử method `@Async` được thiết kế trả `CompletableFuture<String>`:

```java
@Async("mailExecutor")
public CompletableFuture<String> calculate() {
    // Có thể ném exception trước khi tạo Future hoặc
    // trả về CompletableFuture chứa kết quả.
    return CompletableFuture.completedFuture("ok");
}
```

Nếu caller đi qua proxy, Spring thực thi method trên executor và trả một async handle; exception của method được xử lý theo hợp đồng Future/CompletionStage tương ứng.

Nếu target tự gọi `this.calculate()`, async interceptor **không chạy**:

- Toàn bộ code trước câu lệnh `return` thực thi ngay trên thread đang gọi.
- Method trả về đúng object `CompletableFuture` do body tạo ra, không thông qua bước submit của async advice.
- Nếu body ném một runtime exception trực tiếp trước khi trả Future, exception đó có thể thoát **đồng bộ** trên lời gọi self-call.
- Với lời gọi qua proxy, các lỗi trong task thường được quan sát qua `Future.get()`/`CompletableFuture.join()`; nếu là `void` async method thì không có Future cho caller lấy lỗi, cần cấu hình `AsyncUncaughtExceptionHandler` hoặc cơ chế ghi nhận khác.

Vì vậy, self-invocation không chỉ ảnh hưởng hiệu năng. Nó có thể làm thay đổi cả **vị trí bắt lỗi, thời điểm phát sinh lỗi và behavior của request**.

### Phân biệt get() và join()

- `CompletableFuture.get()`: ném `ExecutionException` (cùng checked `InterruptedException`).
- `CompletableFuture.join()`: ném `CompletionException` (unchecked) khi có lỗi.
- Cả hai đều có thể **block caller** nếu Future chưa hoàn tất, nên không đặt tùy tiện trên event-loop hoặc request thread nhạy latency.

## 11. @Async lồng nhau: đã ở worker không có nghĩa lời gọi mới được async

Xem fragment:

```java
@Service
public class Pipeline {
    @Async("mailExecutor")
    public CompletableFuture<Void> outer() {
        this.inner();
        return CompletableFuture.completedFuture(null);
    }

    @Async("mailExecutor")
    public void inner() {
        // Bên trong outer() sẽ chạy trực tiếp trên thread hiện tại.
    }
}
```

Nếu một bean khác gọi `pipeline.outer()` qua proxy, `outer()` được chuyển sang pool. Nhưng `this.inner()` ở bên trong `outer()` vẫn không qua proxy nên `inner()` chạy trực tiếp trên chính thread đang xử lý `outer()`.

Điều này đặc biệt quan trọng khi bạn muốn tác vụ con được submit độc lập, chạy song song hay có cơ chế lỗi riêng. Chỉ thấy tên thread `mail-1` ở cả hai method không chứng minh đã có hai task khác nhau. Dùng instrumentation theo số task submit, log correlation id, timing có điều kiện và test controlled execution để xác định.

Tương tự, nếu `outer()` ném exception, cần kiểm tra Future của outer; không mặc định lỗi từ self-call bên trong được xử lý như một async submission độc lập.

## 12. Những tình huống khác thường bị nhầm với self-invocation

### 12.1. Thiếu @EnableAsync

Không có cấu hình annotation processing trong Spring Framework thuần thì `@Async` có thể chỉ còn là metadata trên method. Bật `@EnableAsync` hoặc XML `<task:annotation-driven>` phù hợp.

### 12.2. Bean không do Spring quản lý

`new MailWorker().deliver("E")` là lời gọi Java thông thường. Nó không đi qua proxy do container tạo.

### 12.3. Method private, final hoặc không thuộc interface proxy

Proxy-based AOP không thể chặn những lời gọi không khả dụng trên bề mặt proxy phù hợp. Private method và final method với class proxy không phải cách đặt `@Async` đáng tin. Với JDK proxy, hãy kiểm tra method được công khai qua interface đang được proxy.

### 12.4. Constructor và @PostConstruct

Trong constructor của target, bean chưa đi qua đầy đủ post-processing cần thiết. Tự gọi `@Async` từ constructor hoặc `@PostConstruct` không phải cách khởi tạo bất đồng bộ phù hợp. Theo hướng dẫn của Spring, có thể dùng **một Spring bean khởi tạo khác** gọi method `@Async` của target thông qua dependency đã sẵn sàng sau khi container tạo bean.

### 12.5. Executor thực thi đồng bộ

Mặc dù lỗi self-invocation khá phổ biến, vẫn có tình huống proxy hoạt động nhưng executor được cấu hình đồng bộ hoặc caller phải chờ kết quả ngay; hãy kiểm tra implementation thực tế và thời gian/blocking của caller, không kết luận chỉ dựa trên một log.

### 12.6. Mở rộng async pool không giải quyết root cause

Khi lời gọi bypass proxy, không có task nào được submit qua interceptor cho method ấy. Thay `corePoolSize`, `maxPoolSize`, `queueCapacity` hoặc prefix thread không thể biến self-call thành proxy call. Trước khi tuning, đo **task submission** và xác nhận đúng AOP boundary.

## 13. ThreadLocal, transaction và các tác động trên dữ liệu

Nếu lời gọi được chặn thành công và chạy ở worker, cần xem xét việc chuyển thread:

- **ThreadLocal/MDC:** giá trị gắn với thread HTTP/request không tự động có ở worker; cần cơ chế context propagation hoặc `TaskDecorator` phù hợp khi thật sự cần.
- **Security context:** không mặc nhiên được copy; dùng cơ chế propagation được thiết kế đúng thay vì lén giữ request object.
- **Transaction:** thread-bound transaction của caller không tự chuyển sang async worker. Một method `@Transactional` trên cùng bean cũng có self-invocation proxy limitation tương tự trong PROXY mode, nhưng phải phân biệt ngữ nghĩa transaction và async.
- **JPA persistence context:** entity/lazy-loaded dữ liệu có thể không còn hợp lệ khi worker chạy sau request/transaction; truyền identifier/DTO và mở transaction thích hợp ở phía worker.
- **Consistency:** công việc async có thể bắt đầu trước khi transaction của caller commit. Với hậu xử lý sau commit, cân nhắc event-after-commit hoặc outbox nếu yêu cầu độ tin cậy.

Một hệ quả đáng chú ý: trong self-call vô tình chạy đồng bộ, bạn có thể tưởng rằng `@Async` đang “mang được transaction/MDC”, trong khi thực ra chưa có thread switch nào xảy ra. Khi sửa sang cross-bean call, bất ngờ mất context là **hành vi đúng theo biên thread mới**. Cần thiết kế truyền context có chủ đích, không nên quay lại self-call để giữ state thread.

## 14. Kiểm thử chính xác và quan sát hiệu năng

### 14.1. Đừng chỉ kiểm tra isDone()

Một method gọi trực tiếp rồi trả `CompletableFuture.completedFuture(...)` sẽ có `isDone() == true`. Một async call chạy rất nhanh cũng có thể tạo Future đã hoàn tất trước khi bạn kiểm tra. Vì thế `isDone()` **không chứng minh** method có được interceptor chuyển sang thread khác hay không.

### 14.2. So sánh thread và dùng latch thay vì sleep

Ví dụ runnable trong mục 4 lấy thread name lúc caller bắt đầu, và bắt worker đặt tên qua `setThreadNamePrefix("mail-")`. Điều này giảm phụ thuộc timing so với `Thread.sleep(100)`.

Nếu test cần chứng minh caller **trả về trước khi worker hoàn thành**, hãy dùng `CountDownLatch`, `Phaser` hoặc test executor để giữ task ở một mốc xác định. Dùng timeout hữu hạn để tránh test bị treo khi logic thay đổi.

### 14.3. Quan sát proxy bằng AopUtils

`AopUtils.isAopProxy(bean)` trả true khi reference là AOP proxy. Nó hỗ trợ chẩn đoán: nếu false, một trong các điều kiện cấu hình có thể chưa thỏa. Nhưng kết quả true không khẳng định method cụ thể đã được advice chọn, vì advisor có pointcut và self-call vẫn bỏ qua proxy.

Cần xem cả proxy type, method signature, annotation, executor selection và call path. Một integration test đi qua bean reference như production sẽ có giá trị hơn unit test tự `new` target.

### 14.4. Không dùng thread name làm cơ chế nghiệp vụ

Prefix của executor phù hợp để debug và test minh họa. Trong production, đừng dùng `Thread.currentThread().getName().startsWith(...)` để quyết định tính đúng của transaction, quyền truy cập, thứ tự hoặc mức độ hoàn thành. Thread có thể được tái sử dụng, đổi cấu hình hoặc là virtual thread tùy implementation; hành vi nghiệp vụ cần dựa trên hợp đồng rõ ràng.

## 15. Bảng đối chiếu lỗi thường gặp

| Hiện tượng | Nguyên nhân khả dĩ | Cách sửa/kiểm tra |
| --- | --- | --- |
| `this.asyncMethod()` chạy ngay trong method cùng bean | Self-invocation bypass proxy | Tách bean hoặc submit qua executor |
| `@Async` đặt trên method nhưng mọi lời gọi đều đồng bộ | Chưa bật async processing hoặc không dùng Spring-managed bean | `@EnableAsync`, ApplicationContext, đúng method visibility |
| `AopUtils.isAopProxy(bean)` là true nhưng call vẫn đồng bộ | Đường gọi ở bên trong target hoặc method không được advice | Theo dõi call graph và method signature |
| Bật `proxyTargetClass=true` nhưng self-call vẫn đồng bộ | Class proxy vẫn cần external interception | Không xem CGLIB là cách sửa; tách bean |
| `@Async` method private/final không như mong đợi | Proxy không intercept được method | Tạo method public/khả dụng qua proxy |
| Gọi trong constructor hoặc `@PostConstruct` | Bean/proxy chưa sẵn sàng cho call đó | Khởi động thông qua bean khác sau lifecycle phù hợp |
| Đã chuyển sang bean khác nhưng có `this.otherAsync()` ở worker | Chỉ outer call qua proxy | Tách task con rõ ranh giới, không đánh đồng “đã trên pool” với interceptor mới |
| Sau khi sửa, thiếu MDC hoặc transaction | Worker chạy trên thread khác | Thiết kế context propagation và transaction riêng |
| Exception không còn ném ở caller | Async exception đi qua Future/handler | Thu nhận Future, cấu hình error reporting |
| Tăng thread pool mà không đổi kết quả | Không có async submission cho self-call | Xác nhận proxy boundary trước khi tuning |
| Test luôn pass vì Future đã completed | Test không kiểm tra thread hoặc concurrency | Dùng latch và kiểm tra đường gọi thực tế |

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Có `@EnableAsync` và một bean `ThreadPoolTaskExecutor` đúng tên. Vì sao `this.sendMail(id)` vẫn chạy đồng bộ?

Vì trong `AdviceMode.PROXY`, lời gọi nội bộ trên target không đi qua proxy. Async interceptor không được áp dụng ở điểm gọi này, bất kể executor có tồn tại.

**Câu 2.** Đổi thành `@EnableAsync(proxyTargetClass = true)` có giải quyết được không?

Không. Tham số chỉ chọn class-based proxy thay vì JDK interface proxy trong PROXY mode; nó không tạo một interception cho self-call.

**Câu 3.** Một bean khác được inject `MailWorker`, gọi `worker.deliver()` thì có khác không?

Có. Khi reference được Spring cung cấp là proxy và method phù hợp advice, lời gọi từ bean khác đi qua proxy, nên `@Async` có thể submit task lên executor.

**Câu 4.** Nếu gọi `@Async outer()` từ bên ngoài, rồi bên trong `outer()` dùng `this.innerAsync()`, tại sao log của `innerAsync()` vẫn xuất hiện trên thread pool?

Vì `outer()` đã được chuyển sang worker; `innerAsync()` chạy **trực tiếp trên worker đó**, không phải vì có thêm một lượt async interception.

**Câu 5.** Muốn chạy async nhưng không muốn dùng Spring AOP proxy thì làm sao?

Tiêm `AsyncTaskExecutor`/`Executor` và gửi `Runnable` hoặc `Callable` bằng `execute/submit/submitCompletable`. Đây là explicit task submission và không phụ thuộc self-invocation proxy.

**Câu 6.** AspectJ có giúp chặn self-invocation không?

Có khi thực sự bật `AdviceMode.ASPECTJ` cùng AspectJ weaving và dependencies đầy đủ. Chỉ đổi annotation mà thiếu weaving chưa đủ. Với bài toán nghiệp vụ thông thường, tách sang bean khác thường ít phức tạp hơn.

## 17. Checklist trước khi đưa vào production

- **Điểm vào:** caller lấy reference từ Spring ApplicationContext hay dùng `new`?
- **Bật advice:** `@EnableAsync` hoặc XML `<task:annotation-driven>` đã được đăng ký ở context phù hợp?
- **Đường gọi:** method được gọi từ bean khác qua proxy hay chính `this.method()`?
- **Chữ ký:** method có thể được proxy intercept không? Có rơi vào private/final/interface mismatch?
- **Executor:** `@Async("...")` trỏ đúng bean, pool sizing/queue/rejection có giới hạn phù hợp?
- **Chứng minh:** có integration test chứng minh caller thread khác worker thread hoặc có submit độc lập, không dùng `Thread.sleep` đoán thời gian?
- **Kết quả/lỗi:** `void` hay `CompletableFuture`, ai quan sát exception, timeout và cancellation?
- **Ngữ cảnh:** transaction, security, logging MDC và dữ liệu JPA có được xử lý tại thread mới đúng cách?
- **Lifecycle:** đã tránh async call trong constructor/`@PostConstruct` và có chính sách shutdown rõ ràng?
- **Độ bền:** tác vụ quan trọng có cần outbox/broker thay vì chỉ queue trong tiến trình không?

## 18. Kết luận

**Lý do cốt lõi:** trong Spring `@Async` ở chế độ proxy mặc định, advice chỉ chạy khi một lời gọi đi qua Spring proxy. Lời gọi `this.asyncMethod()` trong cùng target bean là lời gọi Java thường, **không đi qua proxy**, nên không kích hoạt interceptor, không submit một task mới lên executor cho method đó.

Cách sửa dễ đọc, dễ test và thường nên ưu tiên: **đưa method async sang một Spring-managed bean riêng và gọi qua dependency được inject**. Nếu cần một vị trí gửi task tường minh, có thể dùng `AsyncTaskExecutor.submit(...)`/`submitCompletable(...)`. AspectJ weaving là phương án nâng cao cần cấu hình riêng, không đồng nghĩa với `proxyTargetClass=true`.

Đừng chẩn đoán chỉ từ annotation, tên thread hoặc `Future.isDone()`. Hãy xác minh **proxy boundary, đường gọi và task submission thực tế**.

### Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling: Annotation Support](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework 6.2 — EnableAsync API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Spring Framework 6.2 — Async API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/annotation/Async.html)
- [Spring Framework — Understanding AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-invocation-003">Phân biệt đối tượng tự tạo bằng new với bean được tiêm</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt đối tượng tự tạo bằng new với bean được tiêm

## 1. Mục tiêu: theo đúng đối tượng mà lời gọi thực sự đi qua

Trong Spring Framework, hai biến có thể cùng kiểu `ReportService` nhưng hành vi **khác hẳn nhau**:

- `new ReportService()` là đối tượng Java do code gọi constructor trực tiếp; không tự được Spring container quản lý.
- `ReportService` nhận qua constructor injection, `@Autowired`, hoặc `ApplicationContext.getBean(...)` là **bean reference** do container cung cấp. Tùy cấu hình, reference này có thể là **proxy**, thay vì đối tượng target thuần.

Sự phân biệt đặc biệt quan trọng trong chủ đề **Spring Framework - concurrency**, vì `@Async` ở chế độ mặc định dùng **Spring AOP proxy** để intercept lời gọi và submit task cho executor. Method có `@Async` trên một instance tự `new` thường chạy đồng bộ như method Java bình thường.

**Mục tiêu sau bài học:**

1. Giải thích khác nhau giữa đối tượng tự tạo, bean được đăng ký, và proxy được inject.
2. Nhận biết khi nào `@Async` thực sự chuyển execution sang executor, và khi nào annotation không được intercept.
3. Phân biệt `new` **bên ngoài container** với `new` **nằm trong method `@Bean`**: cả hai đều gọi constructor Java, nhưng chỉ trường hợp thứ hai được đăng ký làm Spring bean.
4. Viết constructor injection đúng cách, tránh tự `new` service có dependency và interceptor.
5. Hiểu self-invocation, proxy type, method visibility, scope và lifecycle.
6. Kiểm chứng thực tế bằng chương trình Java 17+ chạy với Spring Framework, thread name, proxy detection và test có điều phối.
7. Chọn được thiết kế không chia sẻ mutable state thiếu an toàn khi Spring singleton được gọi đồng thời.

**Phạm vi:** bean management, đường đi lời gọi và ảnh hưởng đến `@Async`. Ví dụ về `@Transactional`, caching và scope chỉ đóng vai trò đối chiếu cơ chế; không thay thế bài học riêng về những annotation đó.

## 2. Một đối tượng Java không tự nhiên là một bean Spring

Trong Java:

```java
ReportService service = new ReportService();
service.process();
```

Lời gọi `new`:

- Cấp phát đối tượng theo semantics của Java và chạy constructor thích hợp.
- Không tự yêu cầu `ApplicationContext` tạo/đăng ký đối tượng.
- Không tự kích hoạt `@Autowired` trên các field/setter, `BeanPostProcessor`, các callback init/destroy hay AOP proxy của Spring cho instance vừa tạo.
- Không tự đọc `@Async`, `@Transactional`, `@Cacheable` rồi thay đổi method dispatch.
- Quyền quản lý vòng đời, tài nguyên và các dependency của đối tượng thuộc code đã tạo và giữ tham chiếu đến nó.

Ngược lại, một Spring bean tồn tại nhờ **bean definition**, có thể đến từ `@Component`/`@Service` và component scan, `@Bean` trong lớp cấu hình, XML, đăng ký programmatic hoặc cơ chế auto-configuration của Spring Boot. Container chịu trách nhiệm tạo hoặc lấy object, resolve dependency, xử lý lifecycle và áp dụng bean post-processors/advice khi phù hợp.

Ví dụ về cách nhận bean qua constructor injection:

```java
@Service
public class ReportControllerFacade {
    private final ReportService reportService;

    public ReportControllerFacade(ReportService reportService) {
        this.reportService = reportService;
    }

    public void requestReport() {
        reportService.process();
    }
}
```

Đây là fragment minh họa: cần import Spring annotations và một project có bean `ReportService`. `ReportControllerFacade` phải được Spring quản lý thì Spring mới tự resolve constructor dependency. Trong Spring hiện đại, class có **một constructor** thường không cần thêm `@Autowired` vào constructor đó.

**Điều quyết định không phải từ khóa `new` có xuất hiện ở đâu trong source**, mà là object có được container **nhận làm bean**, áp dụng các bước cấu hình, và trả reference tương ứng cho caller hay không.

### 2.1. Ba trường hợp cần phân biệt

| Cách tạo/nhận | Đối tượng được Spring quản lý? | Spring có cơ hội inject/apply post-processor/proxy? |
| --- | --- | --- |
| `new ReportService()` trong method nghiệp vụ | Không, nếu không có bước đăng ký/tích hợp đặc biệt | Không tự động |
| `@Bean ReportService reportService() { return new ReportService(); }` | Có, kết quả của factory method được đăng ký là bean | Có, theo config và eligibility |
| `@Service class ReportService` và inject qua constructor | Có, khi class được scan/đăng ký | Có, theo config và eligibility |
| `context.getBean(ReportService.class)` | Có, lấy bean reference từ container | Có, nhưng có thể nhận proxy hoặc target tùy cấu hình |
| `new ReportService(dependency)` với dependency đã inject | **Object mới** không vì thế thành bean | Chỉ dependency được truyền có thể là bean; instance mới không tự được post-process |
| `ObjectProvider<ReportService>.getObject()` | Có, yêu cầu container resolve một bean | Theo scope và các post-processors có hiệu lực |

Trong ứng dụng thực, **không phải mọi bean đều được proxied**. Một bean đơn giản không có advisor phù hợp có thể được inject dưới dạng object thật. Ngược lại, khi `@Async`, `@Transactional` hoặc advisor khác đủ điều kiện, reference do container cung cấp có thể là proxy. Đừng viết “bean = proxy” như một đẳng thức tuyệt đối.

### 2.2. Sơ đồ đường đi

```text
                Tạo đối tượng thủ công
Caller ----------> new ReportService()
                      |
                      +-- gọi trực tiếp process()
                      +-- @Async chỉ là annotation metadata
                      +-- không có Spring async interceptor

                  Bean do Spring quản lý
ApplicationContext -- bean definition --> tạo target / inject / initialize
                                            |
                                            +-- BeanPostProcessor/AOP khi áp dụng
                                            |
                                            +-- proxy hoặc reference được container trả ra
                                                       |
Caller / bean khác -------- constructor injection ----+
                                                       |
                                              @Async interceptor
                                                       |
                                                TaskExecutor
                                                       |
                                                worker thread
                                                       |
                                                  target.process()
```

Sơ đồ thứ hai mô tả **trường hợp có async advice và gọi qua proxy**. Nếu cấu hình async chưa bật, method không đủ điều kiện hay caller bỏ qua proxy, interceptor sẽ không xuất hiện trên đường thực thi.

## 3. Vì sao @Async thay đổi hành vi của bean, nhưng không thay đổi một object new?

Với `@EnableAsync`, Framework đăng ký hạ tầng xử lý `@Async`. Ở chế độ mặc định `AdviceMode.PROXY`:

1. Spring nhận diện các bean/method có async advice thích hợp.
2. Khi cần, bean được expose thông qua proxy được cấu hình.
3. Caller gọi method **qua reference proxy**.
4. Interceptor chọn `Executor`/`TaskExecutor` và submit lời gọi thực tế thành task.
5. Thread worker chạy method target; caller có thể nhận `Future`/`CompletableFuture` để quan sát kết quả.

Nếu code gọi:

```java
ReportService service = new ReportService();
service.generateAsync();
```

thì `service` chỉ là object Java thường. Spring không chèn proxy vào lời gọi đó; `generateAsync()` chạy như method thông thường trên thread gọi, trừ khi **bản thân implementation** tạo thread hoặc submit một executor nào khác.

Điều này không có nghĩa mọi object thường đều luôn chạy sync; một object tự `new` vẫn có thể sử dụng `ExecutorService` hoặc `CompletableFuture.supplyAsync(...)` bằng code Java thuần. **Điểm phân biệt:** async do chính implementation quyết định khác với **async advice của Spring** qua `@Async`.

### 3.1. Ba điều kiện tối thiểu của một lời gọi @Async

- **Annotation processing đã bật:** `@EnableAsync` được nạp trong đúng Spring application context, hoặc một cấu hình tương đương.
- **Object và method đủ điều kiện:** service là Spring-managed bean và method có `@Async` ở vị trí framework nhận diện được.
- **Lời gọi đi qua advice:** thường là lời gọi từ một bean khác tới **proxy**, không phải lời gọi nội bộ `this.method()`.

Để có hệ thống tốt trong production còn cần executor được cấu hình phù hợp, xử lý lỗi, kết quả, timeout, tài nguyên và vòng đời. Có ba điều kiện trên chỉ là điểm khởi đầu, không phải chứng nhận thread-safety.

### 3.2. Đặt @Async trên method không có nghĩa Java compiler đổi method call

`@Async` là metadata được Spring xử lý qua infrastructure. Compiler Java không tự thay:

```java
service.generateAsync();
```

thành một `executor.submit(...)` bất kể `service` được tạo thế nào. Cơ chế intercept tại runtime mới tạo khác biệt. Đó là lý do unit test trực tiếp bằng `new` có thể không tái hiện behavior khi ứng dụng chạy với Spring context.

## 4. Ví dụ hoàn chỉnh Java 17+: so sánh new, getBean và constructor injection

Bài thực hành dùng **Spring Framework thuần**, không cần Spring Boot, để chứng minh rằng container/proxy mới là yếu tố quyết định.

### 4.1. Cấu trúc dự án

```text
new-vs-injected-demo/
├── pom.xml
└── src/main/java/demo/NewVsInjectedDemo.java
```

Sử dụng JDK 17+ và Maven, `spring-context` 6.2.14.

Tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>new-vs-injected-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.14</version>
        </dependency>
    </dependencies>
</project>
```

Tệp `src/main/java/demo/NewVsInjectedDemo.java`:

```java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class NewVsInjectedDemo {
    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class AppConfig {
        @Bean("lessonExecutor")
        public ThreadPoolTaskExecutor lessonExecutor() {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setThreadNamePrefix("lesson-worker-");
            pool.setCorePoolSize(2);
            pool.setMaxPoolSize(4);
            pool.setQueueCapacity(16);
            return pool;
        }

        @Bean
        public Worker worker() {
            return new Worker(); // "new" bên trong @Bean: Spring quản lý kết quả
        }

        @Bean
        public Caller caller(Worker worker) {
            return new Caller(worker); // worker do Spring resolve
        }
    }

    public static class Worker {
        @Async("lessonExecutor")
        public CompletableFuture<String> currentThreadName() {
            return CompletableFuture.completedFuture(
                    Thread.currentThread().getName());
        }

        public CompletableFuture<String> selfInvocation() {
            return this.currentThreadName(); // Bỏ qua proxy khi gọi nội bộ
        }
    }

    public static class Caller {
        private final Worker worker;

        public Caller(Worker worker) {
            this.worker = worker;
        }

        public CompletableFuture<String> viaInjection() {
            return worker.currentThreadName();
        }
    }

    public static void main(String[] args) throws Exception {
        String callerThread = Thread.currentThread().getName();

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            Worker raw = new Worker();
            Worker managed = context.getBean(Worker.class);
            Caller caller = context.getBean(Caller.class);

            String directThread = raw.currentThreadName()
                    .get(5, TimeUnit.SECONDS);
            String managedThread = managed.currentThreadName()
                    .get(5, TimeUnit.SECONDS);
            String injectedThread = caller.viaInjection()
                    .get(5, TimeUnit.SECONDS);
            String selfThread = managed.selfInvocation()
                    .get(5, TimeUnit.SECONDS);

            System.out.println("new-on-caller="
                    + directThread.equals(callerThread));
            System.out.println("managed-on-executor="
                    + managedThread.startsWith("lesson-worker-"));
            System.out.println("injected-on-executor="
                    + injectedThread.startsWith("lesson-worker-"));
            System.out.println("self-on-caller="
                    + selfThread.equals(callerThread));
            System.out.println("managed-is-proxy="
                    + AopUtils.isAopProxy(managed));
            System.out.println("distinct-object=" + (raw != managed));
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa POM:

```bash
mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=demo.NewVsInjectedDemo
```

Sáu dòng do `System.out.println` tạo ra, khi môi trường tải và cấu hình như ví dụ:

```text
new-on-caller=true
managed-on-executor=true
injected-on-executor=true
self-on-caller=true
managed-is-proxy=true
distinct-object=true
```

Maven có thể ghi log riêng. Kết quả kiểm tra **tên thread và proxy** chứ không phụ thuộc vào giả định worker phải chậm hơn caller.

### 4.2. Giải thích chính xác sáu kết quả

**(1) `new-on-caller=true`**

`raw = new Worker()` tự tạo object bên ngoài container. Nó có method mang `@Async` nhưng chưa được Spring bọc bằng async proxy. Do đó, method chạy trên thread caller và trả `CompletableFuture.completedFuture(...)` ngay trong lời gọi.

**(2) `managed-on-executor=true`**

`managed = context.getBean(Worker.class)` là reference được Spring cung cấp. Vì context nạp `@EnableAsync` và method public có `@Async("lessonExecutor")`, lời gọi qua reference này được intercept; phương thức target thực thi trên thread mang prefix `lesson-worker-`.

**(3) `injected-on-executor=true`**

`Caller` là bean do `@Bean` tạo, và tham số `Worker worker` trong factory method được Spring resolve. Nó nhận reference proxy thích hợp rồi gọi `worker.currentThreadName()`, nên vẫn có async advice.

**(4) `self-on-caller=true`**

`managed.selfInvocation()` không có `@Async`. Proxy chuyển lời gọi này tới target; bên trong target, `this.currentThreadName()` gọi trực tiếp method trên `this`, bỏ qua proxy; annotation ở method đích không được intercept.

**(5) `managed-is-proxy=true`**

`AopUtils.isAopProxy(managed)` kiểm tra một dấu hiệu runtime. Trong ví dụ cụ thể, async annotation làm Spring cung cấp proxy. Ở ứng dụng khác, một bean có thể **không** có proxy, hoặc proxy tồn tại vì advice khác. Kết quả `true` đơn lẻ không chứng minh tất cả lời gọi đều đi qua async interceptor.

**(6) `distinct-object=true`**

`raw` và bean do context cung cấp là hai reference khác nhau. Chúng không có lifecycle hay dependency injection chung chỉ vì cùng kiểu `Worker`.

### 4.3. Tại sao dòng return new Worker() trong @Bean lại khác?

Đây là ý quan trọng nhất:

```java
@Bean
Worker worker() {
    return new Worker();
}
```

Spring gọi factory method để **tạo bean theo bean definition**. Sau khi nhận đối tượng, container có thể inject các thuộc tính, gọi initialization callbacks và áp dụng `BeanPostProcessor` để trả ra **bean reference cuối cùng**, có thể là AOP proxy. `new` bên trong method chỉ là một bước cấp phát, không có quyền quyết định liệu object sau đó có trở thành bean hay không.

Trong khi đó:

```java
void businessMethod() {
    Worker worker = new Worker();
    worker.currentThreadName();
}
```

không có bean definition hay bean registration nào dành cho biến `worker`, nên Spring không tự intercept lời gọi đó.

Đừng kết luận “cấm dùng `new` trong Spring”. Chính Spring cũng có thể gọi constructor hoặc factory để tạo bean. Câu hỏi đúng là **container đã nhận quyền quản lý instance và reference mà caller đang giữ có phải là reference Spring cung cấp hay không?**

## 5. Constructor injection, @Autowired và đối tượng không được inject

### 5.1. Constructor injection: lựa chọn mặc định cho dependency bắt buộc

Fragment Spring (mỗi public class đặt trong file tương ứng):

```java
@Service
public class InvoiceFacade {
    private final BillingWorker worker;

    public InvoiceFacade(BillingWorker worker) {
        this.worker = worker;
    }

    public CompletableFuture<String> invoice(String id) {
        return worker.generate(id);
    }
}

@Service
public class BillingWorker {
    @Async("billingExecutor")
    public CompletableFuture<String> generate(String id) {
        return CompletableFuture.completedFuture("invoice:" + id);
    }
}
```

Giả định có `@EnableAsync`, bean `billingExecutor` và import `@Service`/`@Async`/`CompletableFuture`. Khi `InvoiceFacade` được Spring tạo, `BillingWorker` được resolve theo DI. Nếu `BillingWorker` có proxy phù hợp, field `worker` trong facade thường giữ reference proxy đó. Lời gọi từ facade sang worker đi qua async interception.

Ngược lại:

```java
public CompletableFuture<String> invoice(String id) {
    BillingWorker worker = new BillingWorker();
    return worker.generate(id);
}
```

là **fragment phản ví dụ**: service được tạo thủ công, không được Spring áp dụng proxy. Method được gọi đồng bộ nếu implementation chỉ trả `completedFuture` như ví dụ.

Một nuance: bạn *có thể* truyền một dependency được inject vào constructor của object `new` theo Java thuần. Nhưng như vậy chỉ cung cấp dependency, **không** biến object mới thành Spring bean được quản lý và proxied.

### 5.2. Tại sao @Autowired field trên object new dễ thành null?

Xét fragment:

```java
@Service
class NotificationService {
    @Autowired
    private MailGateway gateway;

    void send(String id) {
        gateway.send(id);
    }
}
```

Nếu `NotificationService` được Spring tạo và bean `MailGateway` được resolve, injection có thể thành công. Nếu code tự gọi `new NotificationService()`, `gateway` giữ giá trị mặc định `null` trừ khi bạn can thiệp thủ công; gọi `send` có thể gây `NullPointerException`.

Đó là một lý do ưu tiên constructor injection:

```java
@Service
class NotificationService {
    private final MailGateway gateway;

    NotificationService(MailGateway gateway) {
        this.gateway = gateway;
    }

    void send(String id) {
        gateway.send(id);
    }
}
```

Constructor buộc caller cung cấp dependency hợp lệ; trong ứng dụng Spring, container resolve bean `MailGateway` và truyền vào. Nếu muốn unit test bằng `new NotificationService(fakeGateway)`, điều đó hoàn toàn hợp lý: test logic Java trực tiếp, nhưng **không** nên từ test đó suy ra AOP async/transaction đã hoạt động.

### 5.3. @Autowired không thay thế @Bean hoặc component scanning

`@Autowired` là hướng dẫn **resolve dependency** khi Spring tạo/quản lý bean. Nó không tự phát hiện mọi object có field annotated trong toàn JVM.

- `@Service`/`@Component` đánh dấu class để component scanning có thể đăng ký bean.
- `@Bean` định nghĩa bean từ factory method khi lớp config được nạp.
- `@Autowired`, constructor parameters và `@Qualifier` cho biết dependency cần resolve ở injection point.
- `@EnableAsync` bật hạ tầng async; `@Async` đánh dấu nơi cần interception.

Nếu class có `@Service` nhưng nằm ngoài component scan hoặc không được import/đăng ký, annotation đó không đủ để bảo đảm có bean. Nếu class được tạo bằng `new` trong business code, có annotation cũng không tự khiến Spring quản lý object đó.

## 6. Proxy Spring: inject có thể nhận proxy, nhưng không phải lúc nào cũng vậy

### 6.1. JDK dynamic proxy và class-based proxy

Spring AOP thường sử dụng:

| Cách proxy | Nguyên tắc | Hệ quả khi inject |
| --- | --- | --- |
| JDK dynamic proxy | Proxy implement interface cần thiết | Nên inject qua interface; không nhất thiết cast được về concrete class |
| Class-based proxy (CGLIB) | Proxy tạo subclass của target class | Có thể inject qua concrete class đủ điều kiện; class/method `final` không thể được advise theo cách subclass |
| Không proxy | Không có advice áp dụng, hoặc infrastructure chưa bật | Spring vẫn có thể inject target bean bình thường, nhưng method không có interception AOP |

Loại proxy mặc định phụ thuộc cấu hình Framework/Boot, interfaces của bean, advisor và phiên bản. Không dựa cứng vào kết quả `bean.getClass() == Worker.class`: class runtime có thể là generated proxy class.

**Giới hạn quan trọng với class-based proxy:**

- Class `final` không thể bị subclass để tạo kiểu proxy này.
- Method `final` không thể được override để advise.
- Method `private` không thể được advise bởi cơ chế override.
- Method không visible thích hợp có thể có giới hạn tương tự.
- Static method không là lời gọi instance qua Spring proxy.

Hướng đi dễ kiểm chứng cho `@Async`: đặt method public trên Spring-managed service, tránh final và gọi nó thông qua bean khác.

### 6.2. Ví dụ interface injection

Fragment:

```java
public interface ExportPort {
    CompletableFuture<String> export(String id);
}

@Service
class ExportService implements ExportPort {
    @Async("exportExecutor")
    public CompletableFuture<String> export(String id) {
        return CompletableFuture.completedFuture(id);
    }
}

@Service
class ExportControllerFacade {
    private final ExportPort exportPort;

    ExportControllerFacade(ExportPort exportPort) {
        this.exportPort = exportPort;
    }

    CompletableFuture<String> run(String id) {
        return exportPort.export(id);
    }
}
```

Đây là fragment dự án Spring với các import và bean executor phù hợp; không tự chạy chỉ bằng `javac`. Inject theo `ExportPort` giúp code không phụ thuộc concrete proxy type. Nếu muốn dùng concrete-class injection, phải kiểm tra proxy strategy và tính tương thích của class, thay vì ép kiểu từ JDK proxy.

### 6.3. Làm sao biết proxy có tồn tại?

Trong một test Spring đã có bean:

```java
import org.springframework.aop.support.AopUtils;

Object bean = context.getBean("exportService");

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(bean.getClass().getName());
```

Fragment cần `context` thích hợp. Dùng `AopUtils.isAopProxy` và thông tin bean class là bước chẩn đoán, nhưng **không** đủ khẳng định method nào đang được advise. Kiểm tra annotation, call path và tên thread/runtime result thực tế.

## 7. Phân biệt instance không được quản lý với self-invocation

Một object được tự tạo trong business code và một Spring-managed bean tự gọi method của chính nó đều có thể bỏ qua `@Async`, nhưng **đó là hai nguyên nhân độc lập**:

| Tình huống | Vì sao không có async advice? | Cách sửa |
| --- | --- | --- |
| `new ExportWorker().exportAsync(id)` trong business code | Instance này không được Spring đăng ký, post-process hoặc cung cấp qua proxy. | Inject bean `ExportWorker` hoặc lấy managed bean qua provider/factory. |
| `this.exportAsync(id)` trong target đã là Spring bean | Lời gọi nội bộ không vượt qua proxy của method cần advice. | Gọi từ bean cộng tác khác qua Spring-managed reference hoặc explicit executor. |

Ví dụ một bean đã được container quản lý vẫn mắc self-invocation:

```java
@Service
class CsvExportService {
    public void start(String id) {
        this.exportAsync(id); // Gọi thẳng trong target ở PROXY mode.
    }

    @Async("exportExecutor")
    public void exportAsync(String id) {
        // Tác vụ chạy trên executor khi được gọi qua proxy.
    }
}
```

Đoạn này là fragment Spring, cần imports, `@EnableAsync` và bean `exportExecutor`. Nếu gọi `start()` qua proxy, lời gọi `this.exportAsync()` vẫn không được chuyển thành submission mới. Thêm thread hoặc đổi scope không sửa được lỗi. Trọng tâm của bài này là **nguồn gốc và ownership của instance**, còn bài trước phân tích sâu self-call và exception semantics.

## 8. Bean scope: Spring quản lý object không có nghĩa mọi lần gọi nhận instance mới

Đừng suy từ “injected” ra “mỗi request một object” hoặc “không có shared mutable state”. Scope độc lập với sự tồn tại của proxy và với việc gọi qua `new`.

| Scope | Hành vi tổng quát | Điểm cần nhớ khi có concurrency |
| --- | --- | --- |
| `singleton` (mặc định) | Thường một instance cho mỗi bean definition trong một container | Nhiều request/worker có thể truy cập cùng state; Spring không tự khóa mutable fields |
| `prototype` | Tạo instance khi container được yêu cầu cung cấp bean | Prototype inject **một lần** vào singleton không tự đổi instance theo từng method call |
| `request` | Instance gắn request lifecycle trong web-aware context | Không nên giữ request bean/reference để xử lý background sau request |
| `session` | Instance gắn một HTTP session | Một session có thể có request đồng thời; state mutable vẫn cần kiểm soát |
| Custom scope | Do ứng dụng/container quy định | Phải hiểu lifetime, owner và thread safety cụ thể |

**Ví dụ phản trực giác:** constructor của singleton facade nhận `@Scope("prototype") Worker worker`. Worker prototype thường được resolve vào thời điểm facade tạo; facade giữ reference đó, chứ không được tạo worker mới cho mỗi lần `facade.process()`.

Nếu thật sự muốn xin bean prototype theo từng invocation, dùng `ObjectProvider<Worker>` hoặc một factory/lookup phù hợp:

```java
@Service
class TaskStarter {
    private final ObjectProvider<Worker> workers;

    TaskStarter(ObjectProvider<Worker> workers) {
        this.workers = workers;
    }

    void start() {
        Worker worker = workers.getObject();
        worker.execute();
    }
}
```

Đây là fragment, yêu cầu bean `Worker` và các imports. Với method `execute()` cần `@Async`, hãy bảo đảm bean prototype được Spring post-process/proxied phù hợp; chỉ đổi scope không giải quyết self-invocation. Việc Spring hủy bean prototype cũng có giới hạn: Spring quản lý tạo/init, nhưng container thường **không tự quản lý toàn bộ destruction callback** của các prototype đã cấp phát như đối với singleton; caller cần tự xử lý resource vòng đời dài khi cần.

### 8.1. Vì sao singleton @Async có thể gây race condition?

Một `@Service` singleton có field:

```java
@Service
class LastOrderService {
    private String lastOrderId;

    @Async("orderExecutor")
    public CompletableFuture<String> record(String id) {
        lastOrderId = id;
        return CompletableFuture.completedFuture(lastOrderId);
    }
}
```

Nếu nhiều task chạy đồng thời trên cùng singleton, `lastOrderId` là shared mutable state, có thể bị ghi đè giữa hai task. Kể cả có proxy, `@Async` không tự khiến field an toàn. Dùng immutable command/DTO, local variables và kết quả riêng cho từng invocation; nếu cần shared state, thiết kế atomicity/locking và ownership đúng nhu cầu.

Việc tạo `new LastOrderService()` cho từng request có thể vô tình làm giảm chia sẻ field trong ví dụ này, nhưng **không** phải giải pháp hợp lý để né concurrency: nó bỏ DI, AOP, lifecycle và có thể vẫn chia sẻ dependencies/static state. Sửa đúng là thiết kế state và phạm vi trách nhiệm.

## 9. @Transactional, @Cacheable và các advice khác: cùng nguyên lý, khác ý nghĩa

Sự khác biệt `new` so với injected bean không chỉ ảnh hưởng `@Async`.

| Khả năng Spring | Khi gọi qua bean/proxy phù hợp | Khi gọi trên đối tượng new thuần |
| --- | --- | --- |
| `@Async` | Interceptor có thể submit method vào executor | Không tự chuyển thread |
| `@Transactional` (proxy mode) | Interceptor có thể mở/gắn transaction theo semantics phù hợp | Annotation không tự mở transaction |
| `@Cacheable` (proxy mode) | Advice có thể kiểm tra/cache giá trị trả về | Annotation không tự cache |
| Custom `@Aspect` | Advice phù hợp có thể chạy trước/sau/xung quanh method | Không được intercept trừ khi cơ chế khác được áp dụng |
| DI `@Autowired` | Container có thể resolve dependencies | Không được inject tự động |
| Lifecycle callbacks | Có thể được chạy khi bean được khởi tạo/đóng theo điều kiện | Không tự được Spring gọi |

Đây là **mô hình proxy-based điển hình**. Một số chức năng có thể dùng AspectJ weaving hoặc một kỹ thuật khác, và Spring cung cấp những cơ chế đặc biệt để autowire/configure objects ngoài container khi ứng dụng chủ ý thiết lập. Không vì ngoại lệ nâng cao mà kết luận mọi object `new` được Spring xử lý như bean.

### 9.1. @Transactional không tự theo @Async sang thread khác

Giả sử một caller có transaction và gọi method `@Async` trên injected bean: worker thường chạy ở **thread khác**. Transaction context gắn thread caller không tự động đi theo. Nếu worker cần transaction, đặt boundary giao dịch rõ ràng ở code chạy trong worker và gọi qua proxy thích hợp. Ngoài ra, task có thể chạy sau khi transaction của caller hoàn tất.

Đừng giải thích tình huống này là “do dùng `new`” khi thực tế lỗi bắt nguồn từ **thread boundary**. Cần phân biệt:

1. Không được Spring quản lý → không có async advice.
2. Có async advice → code chạy trên worker, context giao dịch thread caller không tự chảy theo.
3. Trong worker, một lời gọi `this.transactionalMethod()` vẫn có thể bỏ qua proxy transaction nếu là self-invocation.

### 9.2. Spring không nên được dùng làm service locator ở khắp nơi

Chuyển mọi `new` thành `applicationContext.getBean(...)` trong business code thường giảm tính rõ ràng của dependency graph. Ưu tiên constructor injection với typed interfaces, và chỉ dùng `ObjectProvider`/factory khi thật sự cần lấy bean theo runtime hoặc lấy prototype nhiều lần.

## 10. Các tình huống đặc biệt với @Bean, @Configuration và new

### 10.1. Return new trong @Bean **không** phải lỗi

```java
@Configuration(proxyBeanMethods = false)
class TaskConfig {
    @Bean
    NotificationWorker notificationWorker(MailClient mailClient) {
        return new NotificationWorker(mailClient);
    }
}
```

Spring gọi method `notificationWorker(...)` và đăng ký object trả về dưới bean definition. Nó nhận constructor dependency từ container, sau đó áp dụng các bước lifecycle/post-processing thích hợp. `new` tại đây là một cách implementation factory method, không phải business code tự tạo service ngoài Spring.

### 10.2. Nhưng tự gọi phương thức @Bean không nhất thiết tạo bean được quản lý

Với cấu hình ở chế độ `proxyBeanMethods=false`, lời gọi Java trực tiếp từ một method đến một method `@Bean` khác **không** được Spring chặn để trả singleton theo bean definition. Ví dụ phản mẫu:

```java
@Configuration(proxyBeanMethods = false)
class WrongFactoryConfig {
    @Bean
    Worker worker() {
        return new Worker();
    }

    @Bean
    Caller caller() {
        return new Caller(worker()); // Gọi Java trực tiếp tới @Bean method
    }
}
```

Trong tình huống này, `Caller` có thể giữ một `Worker` được tạo trong lời gọi method trực tiếp thay vì bean reference container đã post-process/proxied. Việc `worker()` có annotation `@Bean` không biến **mọi lần gọi trực tiếp method** thành container lookup.

**Đúng hơn** là yêu cầu Spring inject tham số:

```java
@Configuration(proxyBeanMethods = false)
class GoodFactoryConfig {
    @Bean
    Worker worker() {
        return new Worker();
    }

    @Bean
    Caller caller(Worker worker) {
        return new Caller(worker);
    }
}
```

Các snippet này là fragments và giả định có lớp `Worker`, `Caller` phù hợp. Nếu dùng `@Configuration(proxyBeanMethods=true)` kiểu full configuration, Spring có cơ chế intercept một số inter-bean method calls để trả bean container; nhưng phụ thuộc vào cơ chế enhancement và cách viết code. **Constructor/method parameter injection** vẫn rõ ràng hơn, nhất là khi đưa class sang chế độ lite/proxyBeanMethods=false.

### 10.3. @ComponentScan không biến đối tượng đã tạo thành bean

`@ComponentScan` tìm class để đăng ký bean definitions. Nếu trong method bạn viết `new ComponentClass()`, đối tượng riêng đó không được “nhặt” vào container sau khi scan, dù cùng class với một bean đã đăng ký.

### 10.4. Prototype bean và đối tượng new không đồng nghĩa

Cả prototype lookup và `new` đều có thể sinh instance mới, nhưng khác nhau ở việc **prototype vẫn được container tạo và post-process**, còn `new` thuần không. Nếu worker cần proxy/lifecycle thì xin prototype từ container, không dùng `new` thay thế một cách tùy tiện.

## 11. Spring Boot không tự sửa đường gọi sai

Spring Boot thêm auto-configuration và convention, nhưng không làm thay đổi nguyên tắc container/proxy của Spring Framework:

- `@SpringBootApplication` thường kích hoạt component scanning và auto-configuration trong phạm vi phù hợp; class nằm ngoài scan có thể không được đăng ký.
- Boot có thể tạo executor theo điều kiện, nhưng vẫn cần bật `@EnableAsync` để annotation processing chạy trong cấu hình thông thường.
- Có bean executor không làm cho `new Worker()` trở thành managed bean.
- Có `@EnableAsync` không làm self-invocation `this.work()` qua proxy.
- Có `@Service` trên class nhưng code gọi trực tiếp `new Service()` vẫn là một instance riêng ngoài container.

Ví dụ fragment Boot:

```java
@SpringBootApplication
@EnableAsync
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

@Service
class DocumentWorker {
    @Async("documentExecutor")
    public CompletableFuture<String> parse(String id) {
        return CompletableFuture.completedFuture("parsed:" + id);
    }
}

@Service
class DocumentFacade {
    private final DocumentWorker worker;

    DocumentFacade(DocumentWorker worker) {
        this.worker = worker;
    }

    CompletableFuture<String> request(String id) {
        return worker.parse(id);
    }
}
```

Đây là ví dụ cấu trúc, **chưa phải chương trình độc lập**, vì cần định nghĩa bean `documentExecutor`, imports và project Spring Boot. Nó minh họa đường gọi `DocumentFacade → injected DocumentWorker → proxy async`. Nếu thay `worker` bằng `new DocumentWorker()`, code cùng tên method không còn được Spring `@Async` intercept.

Để biết pool nào thực thi, xem `@Async("documentExecutor")`, loại bean `Executor`, tình trạng Spring context và thread name. Không giả định một bean custom sẽ tự nhận `spring.task.execution.*` properties của Boot.

## 12. Khi nào dùng new là đúng?

Dùng `new` rất bình thường cho **value objects, DTOs, collections, domain models và các thuật toán thuần Java**, khi bạn **không cần** container quản lý đối tượng đó:

```java
record ExportCommand(String requestId, String path) {}

ExportCommand command =
        new ExportCommand("R-123", "/reports/summary.csv");
List<String> lines = new ArrayList<>();
```

Đây là fragment với import Java cần thiết. Những object dữ liệu ngắn hạn này không tự động cần `@Component` hay `@Service`. Việc inject mọi DTO vào Spring container thường làm khó quản lý identity, state và scope.

**Nên inject Spring bean** khi đối tượng:

- Chứa business service được chia sẻ, có nhiều dependencies cần DI.
- Cần AOP advice như `@Async`, `@Transactional`, `@Cacheable`.
- Có tài nguyên cần lifecycle container (executor, datasource, client với cấu hình, scheduler).
- Cần được cấu hình tập trung, thay thế implementation qua interface, quản lý scope hoặc tích hợp metrics.
- Cần thống nhất ownership và test integration với Spring.

**Không cần inject** chỉ vì “có class Java”. Một object thuần có thể được tạo bằng `new` bên trong Spring-managed service; Spring service vẫn là bean, trong khi DTO mà nó tạo vẫn là object bình thường. Đó là thiết kế hoàn toàn hợp lệ.

### 12.1. Nhà máy (factory) tạo domain object có thể là bean

Bạn có thể inject một factory bean có dependency/configuration, còn object factory tạo ra vẫn là domain object bình thường:

```java
@Component
class ExportCommandFactory {
    ExportCommand create(String id, String path) {
        return new ExportCommand(id, path);
    }
}
```

Fragment này cho thấy không phải mọi object do bean tạo ra đều trở thành bean. **Spring quản lý factory**, không mặc nhiên quản lý từng `ExportCommand`.

## 13. Kiểm chứng bằng test: unit test khác integration test

### 13.1. Unit test cho logic Java

Nếu bạn tạo service bằng `new` và tự truyền mock/fake dependency, bạn đang test method logic. Đây là test hợp lệ, nhanh và dễ kiểm soát. Nhưng một assertion thành công ở unit test ấy **không chứng minh `@Async`, transaction advice hay bean lifecycle có hiệu lực**, vì không có container/proxy tương ứng.

Ví dụ với method thuần:

```java
var worker = new BillingWorker();
var result = worker.generate("INV-1").join();

// Kiểm tra logic result, không kiểm tra Spring async advice.
assertEquals("invoice:INV-1", result);
```

Đây là fragment JUnit, giả định `BillingWorker` đủ điều kiện khởi tạo bằng no-arg constructor như ví dụ trước và đã import JUnit. Nếu worker cần dependency bắt buộc, cung cấp fake/mock bằng constructor thay vì bỏ qua hợp đồng object.

### 13.2. Integration test phải dùng Spring context

Với test xác minh AOP:

1. Nạp config có `@EnableAsync` và bean executor.
2. Inject service từ Spring; đừng `new` nó trong test.
3. Xác định reference nhận được có proxy khi bạn kỳ vọng advice.
4. Gọi method qua bean từ context/bean khác.
5. Kiểm tra thread name của target, future result, exception, timeout.
6. Tạo một đối tượng `new` để làm nhóm đối chứng, chứng minh đường chạy khác nhau.
7. Kiểm tra self-invocation: method bên trong target chạy trên caller dù bean bên ngoài được inject.
8. Đóng context để xác nhận lifecycle executor và dừng tài nguyên đúng thời hạn.

### 13.3. Tránh test timing bằng Thread.sleep

Dùng `CountDownLatch` để điều phối sự kiện thay vì dùng `Thread.sleep(50)` rồi hy vọng worker chưa kịp chạy:

```java
// Fragment minh họa ở bên trong @Async method được gọi qua proxy:
started.countDown();
release.await();
return CompletableFuture.completedFuture("finished");
```

Caller có thể đợi `started.await(timeout, unit)`, xác minh task chưa hoàn thành, sau đó `release.countDown()` và đợi future. Cần xử lý `InterruptedException`, đặt timeout để test không treo và bảo đảm giải phóng latch ở `finally`.

Một test stress có thể hỗ trợ phát hiện race, nhưng **không** thay thế việc hiểu bean scope, state ownership và thread boundary.

## 14. Debug một method @Async “không chạy async”

Lần lượt trả lời, đừng sửa pool theo cảm tính:

1. **Instance đến từ đâu?** Lần theo `new`, `@Bean`, `@Service`, constructor injection, `getBean` hoặc factory.
2. **Context có quản lý instance không?** Có bean definition thực và object được Spring cung cấp không? Object bị tạo lại thủ công ở đâu?
3. **Bean có proxy không?** Kiểm tra `AopUtils.isAopProxy(...)` và loại proxy (JDK/interface hoặc class-based) khi phù hợp.
4. **Async infrastructure đã bật chưa?** `@EnableAsync` có được load trong đúng context?
5. **Method có eligible không?** `@Async` ở method/class nào; method public/non-final? Return type `void`/`Future`/`CompletableFuture`?
6. **Lời gọi qua reference nào?** Reference từ DI/proxy hay `this`? Có reflection, private helper hay tự tạo object không?
7. **Executor nào được chọn?** `@Async("...")`, `AsyncConfigurer`, default resolver, bean name, prefix.
8. **Task có bị reject hoặc dừng không?** Pool/queue có hữu hạn? Executor có còn sống không?
9. **Task thực tế chạy trên thread nào?** In caller và worker thread names; không chỉ suy từ có `CompletableFuture`.
10. **Nếu Spring Boot:** bean nào do Boot auto-configure, bean nào do bạn khai báo; property có bind vào đúng executor không?
11. **Nếu vấn đề là dữ liệu sai:** kiểm tra singleton shared mutable state, context propagation, transaction boundaries, chứ không chỉ proxy.
12. **Nếu vấn đề là shutdown:** xem owner executor, task đang chờ và chiến lược graceful stop.

Một bảng nhanh:

| Triệu chứng | Nguyên nhân khả dĩ | Cách xác minh |
| --- | --- | --- |
| Tạo service bằng `new` rồi `@Async` chạy sync | Không có Spring interception | So sánh với bean inject, kiểm tra thread |
| Method async chạy sync trong cùng bean | Self-invocation | Vẽ call graph, tách bean rồi test lại |
| `@Autowired` field là null | Instance được tạo ngoài container | Tìm điểm `new`; dùng constructor injection |
| `@Transactional` không mở transaction | Không qua proxy hoặc thiếu infrastructure | Xác minh bean, advice và call path |
| Inject bean theo class bị lỗi | JDK dynamic proxy chỉ expose interface | Inject interface, kiểm tra proxy strategy |
| Bean inject được nhưng không có proxy | Advisor không áp dụng hoặc infrastructure chưa bật | `AopUtils`, annotations, condition/config |
| `@Async("pool")` không tìm executor | Bean name/qualifier sai hoặc bean chưa được đăng ký | Liệt kê bean Executor trong context |
| Unit test qua `new` pass nhưng runtime khác | Unit test bypass AOP/lifecycle | Có integration test Spring context |
| Một request nhìn thấy dữ liệu request khác | Mutable field trong singleton/shared dependency | Đổi state sang local/immutable hoặc đồng bộ đúng |
| Tăng số thread vẫn không nhanh | Queue/backpressure/downstream bottleneck | Metric active/queue/rejection/latency |

## 15. Bài tập tích hợp: xác định ai đang quản lý từng object

### Bài 1: phân loại sáu reference

Cho các fragment sau trong ứng dụng đã bật `@EnableAsync`:

```java
Worker a = new Worker();
Worker b = context.getBean(Worker.class);
Worker c = injectedWorker;
Worker d = factory.createWorker();
Worker e = provider.getObject();
Worker f = new Worker(injectedDependency);
```

Với mỗi biến, hãy ghi: **nguồn tạo**, **container-managed?**, **có thể là proxy?**, **có được áp dụng `@Async` nếu method được gọi qua reference đó?**, **ai quản lý lifecycle?**. Với `d`, không đoán: phải đọc implementation của factory và xem nó dùng `new` hay Spring container. Với `e`, phải biết `ObjectProvider` lấy bean nào và bean scope là gì. Với `f`, việc dependency là bean không khiến `f` tự thành bean.

### Bài 2: sửa lỗi bypass proxy

Refactor:

```java
@Service
class ExportFacade {
    public void start(String id) {
        ExportWorker worker = new ExportWorker();
        worker.exportAsync(id);
    }
}
```

thành một facade constructor-inject `ExportWorker` và gọi `worker.exportAsync(id)`; bật `@EnableAsync` và có bean executor. Giải thích vì sao thay đổi này **chỉ** giải quyết một vấn đề: vẫn cần kiểm tra invocation thực qua proxy, result/error và thread-safety.

### Bài 3: self-invocation

Thêm method `startInternal()` vào `ExportWorker` và cho nó gọi `this.exportAsync(id)`. Ghi lại thread chạy. Tách `startInternal()` vào bean facade khác và lặp lại. So sánh tại sao cả hai lần đều dùng bean được Spring tạo nhưng chỉ lời gọi từ facade mới qua proxy.

### Bài 4: @Bean với new

Viết hai phiên bản cấu hình:

1. `@Bean Worker worker() { return new Worker(); }`, rồi `@Bean Caller caller(Worker worker) { return new Caller(worker); }`.
2. Với `proxyBeanMethods=false`, `@Bean Caller caller() { return new Caller(worker()); }`.

Quan sát đối tượng được inject, proxy và hành vi `@Async`. Giải thích vì sao factory method chỉ nên lấy bean dependency qua tham số để tránh bypass container.

### Bài 5: đồng thời và scope

Tạo một singleton `@Service` có mutable field `lastRequestId`, hai lời gọi `@Async` ghi field khác giá trị, rồi dùng latch điều phối để chứng minh kết quả có thể bị trộn. Sửa theo hướng stateless/immutable input, không chỉ thay bằng `new` hoặc `prototype`. Làm rõ ai sở hữu trạng thái và thời điểm kết thúc request.

### Bài 6: kiểm thử đối chứng

Tạo một unit test dùng `new Worker()` và một integration test nạp Spring context. Mỗi test kiểm tra output nghiệp vụ; integration test kiểm tra thêm `AopUtils.isAopProxy` và thread prefix khi gọi qua bean. Giải thích vì sao unit test không phải bằng chứng về async advice.

## 16. Checklist kiến thức trước khi chuyển sang bài tiếp theo

- [ ] Tôi giải thích được vì sao `new Service()` ngoài container không tự nhận Spring DI/AOP.
- [ ] Tôi phân biệt được `new` trong method `@Bean` với `new` trong business method.
- [ ] Tôi biết `@Service` chỉ dẫn đến bean khi class thực sự được scan/đăng ký.
- [ ] Tôi biết constructor injection cung cấp bean reference, có thể là Spring AOP proxy.
- [ ] Tôi biết một injected bean không phải lúc nào cũng có proxy, và proxy không đồng nghĩa với instance mới theo mỗi lời gọi.
- [ ] Tôi giải thích được điều kiện `@EnableAsync` + `@Async` + invocation qua proxy.
- [ ] Tôi phân biệt được đối tượng tự tạo và self-invocation: hai nguyên nhân khác nhau dẫn đến bỏ qua advice.
- [ ] Tôi hiểu giới hạn của JDK interface proxy, class-based proxy và final/private method.
- [ ] Tôi có thể kiểm chứng bằng `ApplicationContext`, `AopUtils`, thread name và future.
- [ ] Tôi biết async advice không tự sửa race condition, transaction context hoặc shared mutable state.
- [ ] Tôi chọn `new` cho DTO/value object khi phù hợp, inject service/resource cần Spring quản lý.
- [ ] Tôi biết prototype bean vẫn khác instance `new` và không tự thay đổi theo mỗi lần gọi từ singleton.
- [ ] Tôi không dùng Spring Boot auto-configuration làm lý do bỏ qua đường đi proxy.
- [ ] Tôi có test Spring integration riêng để bảo đảm concurrency behavior được cấu hình đúng.

**Kết luận:** `new` là cách Java tạo object; **DI là cách Spring cung cấp bean reference từ container**. Sự khác biệt quan trọng nhất khi học Spring concurrency là **lời gọi chạy trên object nào, reference đó có đi qua proxy hay không, và executor nào thực sự nhận task**. Khi mô tả chính xác đường đi từ caller → injected proxy → interceptor → executor → target, bạn có thể giải thích vì sao `@Async` có hoặc không có hiệu lực, đồng thời tránh nhầm lẫn giữa bean scope, lifecycle và thread-safety.

## 17. Tài liệu chính thức để tra cứu

- Spring Framework — [Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html): container tạo/resolve dependencies và constructor injection.
- Spring Framework — [Bean Overview](https://docs.spring.io/spring-framework/reference/core/beans/definition.html): bean definition và quản lý object.
- Spring Framework — [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK proxy, class-based proxy và self-invocation.
- Spring Framework — [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@EnableAsync`, `@Async`, executor qualification và exception handling.
- Spring Framework — [Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): singleton, prototype và vòng đời.
- Spring Framework Javadoc — [@Bean](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/Bean.html): factory method tạo managed bean.
- Spring Framework Javadoc — [@EnableAsync](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html): proxy advice mode và cấu hình async.

Các chi tiết proxy mặc định và auto-configuration có thể thay đổi theo release. Khi triển khai, hãy đối chiếu **đúng phiên bản Spring Framework/Spring Boot của dự án** thay vì suy luận từ một ví dụ duy nhất.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-invocation-004">Proxy theo interface và proxy theo lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Proxy theo interface và proxy theo lớp

## 1. Vì sao Spring cần proxy?

Trong Spring Framework, nhiều tính năng không chỉ chạy mã của phương thức gốc mà còn phải làm việc **trước, sau hoặc xung quanh lời gọi**. Ví dụ:

- `@Async`: gửi phương thức tới `TaskExecutor` để thực thi bất đồng bộ.
- `@Transactional`: mở/tham gia transaction, commit hoặc rollback theo kết quả.
- Spring AOP: logging, đo thời gian, kiểm soát quyền hoặc các *advice* tùy chỉnh.
- Một số cơ chế cache, retry hoặc bảo mật được cài đặt bằng interceptor/proxy.

Spring thường cung cấp cho đối tượng gọi **một proxy** thay vì tham chiếu trực tiếp tới đối tượng nghiệp vụ (*target*). Proxy quyết định chạy interceptor nào, rồi mới chuyển lời gọi tới target.

```text
Bean A (caller)
      |
      | gọi service.process()
      v
  [ SPRING PROXY ]
      |
      +-- advice/interceptor: @Async / @Transactional / AOP ...
      |
      v
  [ TARGET OBJECT ]
      |
      v
  business method
```

**Hai cơ chế proxy chủ yếu** là **JDK dynamic proxy theo interface** và **CGLIB proxy theo lớp**. Chọn cơ chế nào ảnh hưởng đến kiểu đối tượng Spring có thể inject, phương thức nào có thể được proxy, những hạn chế liên quan đến `final` và việc annotation có được áp dụng ở ranh giới lời gọi hay không.

**Điểm quan trọng nhất cho concurrency:** dù là JDK proxy hay CGLIB proxy, trong chế độ Spring AOP *proxy-based* mặc định, **lời gọi nội bộ `this.someMethod()` trên target không đi qua proxy**. Vì thế `@Async` có thể không tạo task mới và `@Transactional` có thể không mở transaction theo mong đợi.

## 2. Proxy theo interface: JDK dynamic proxy

### 2.1. Cấu trúc

Một đối tượng nghiệp vụ triển khai interface:

```java
public interface ReportService {
    String generate(String id);
}

public class ReportServiceImpl implements ReportService {
    @Override
    public String generate(String id) {
        return "report:" + id;
    }

    public String internalStatus() {
        return "ready";
    }
}
```

Hai khai báo này là **snippet mô tả API**, không phải một chương trình độc lập (mỗi `public` type thường cần tệp riêng). Spring có thể tạo JDK dynamic proxy triển khai `ReportService` và những interface được chọn của target. Một caller có tham chiếu kiểu `ReportService` sẽ gọi vào proxy.

```text
ReportService interface
          ^
          | implements
          |
   JDK dynamic proxy ---- advice ----> ReportServiceImpl (target)
          ^
          |
      caller injects ReportService
```

**Đặc điểm:**

- Proxy là một đối tượng **triển khai interface**, không phải subclass của `ReportServiceImpl`.
- Các phương thức nghiệp vụ đi qua proxy phải nằm trong **interface được proxy**. Phương thức `internalStatus()` chỉ có trên lớp implementation, không phải API có thể gọi qua biến `ReportService`.
- Với cấu hình JDK proxy, `proxy instanceof ReportService` thường là `true`, nhưng `proxy instanceof ReportServiceImpl` là `false`.
- Vì thế, inject theo **kiểu interface** tương thích, còn ép JDK proxy về `ReportServiceImpl` sẽ không hợp lệ và thường dẫn tới `ClassCastException`. Inject bean bị proxy theo kiểu concrete class có thể gây lỗi dependency resolution ở startup nếu kiểu proxy không gán được.
- JDK proxy không đòi target class có thể được subclass. Tuy nhiên, phương thức cần được expose qua interface và cơ chế annotation/advisor phải nhận diện đúng phương thức nghiệp vụ.

Không hiểu “proxy theo interface” là Spring tạo **một interface mới** chứa code. Interface chỉ mô tả hợp đồng; JDK tạo lớp proxy ở runtime để chuyển cuộc gọi tới interceptor/target.

### 2.2. Hệ quả với @Async

Nếu service có phương thức `@Async` chỉ hiện diện trên implementation nhưng không khai báo trong interface, caller chỉ nhìn thấy biến kiểu interface **không thể trực tiếp gọi phương thức đó**. Với API công khai được gọi từ bean khác, hãy đưa chữ ký phương thức cần sử dụng vào interface hoặc cân nhắc class-based proxy có lý do rõ ràng.

Với Spring, nên đặt annotation nghiệp vụ trên **phương thức implementation** để rõ nguồn hiệu lực, đặc biệt khi code có thể chuyển qua nhiều chế độ proxy/weaving. Không suy luận rằng đổi từ JDK sang CGLIB tự kích hoạt mọi phương thức mang `@Async`: phương thức vẫn cần lời gọi đi qua proxy và đủ điều kiện được intercept.

## 3. Proxy theo lớp: CGLIB subclass proxy

Khi target không có interface phù hợp, hoặc cấu hình buộc dùng proxy theo lớp, Spring có thể dùng **CGLIB** (được repackaged trong `spring-core`) để tạo một **subclass runtime** của lớp target.

```text
ReportServiceImpl
       ^
       | extends
       |
 CGLIB-generated subclass(proxy)
       |
       +-- interceptor/advice
       +-- delegates method invocation to target
       |
     caller
```

**Khác biệt cốt lõi:**

- Proxy CGLIB là một subtype của target class, vì vậy có thể được inject theo **concrete class** hoặc interface mà nó triển khai.
- Phương thức public của target dù **không khai báo trong interface** vẫn có thể là điểm được advice nếu phương thức đủ điều kiện override/intercept và advisor áp dụng cho nó.
- Không cần viết interface chỉ để Spring tạo proxy.
- CGLIB sử dụng cơ chế subclass, vì vậy có những ràng buộc thật của Java inheritance.

### Những giới hạn không thể bỏ qua

| Tình huống | CGLIB class-based proxy |
| --- | --- |
| Lớp `final` | Không thể tạo subclass để proxy lớp đó |
| Phương thức `final` | Không thể override để advice phương thức đó |
| Phương thức `private` | Không override/intercept được bằng subclass |
| Phương thức không đủ khả năng truy cập | Có thể không advice được, chẳng hạn package-private ở parent package khác |
| `this.process()` gọi nội bộ | Vẫn bypass proxy trong Spring AOP proxy mode |
| Lớp thuộc module/JDK bị đóng truy cập | Có thể gặp ràng buộc Java Module System |

**Không nhầm “CGLIB là subclass” với “mọi lần gọi `this.method()` đều đi qua subclass proxy”.** Trong Spring AOP, proxy chuyển lời gọi tới target; bên trong target, `this` là tham chiếu target, nên lời gọi nội bộ không tự đi lại qua proxy. Đây là lý do tự gọi `@Async` hoặc `@Transactional` không được advice trong cả hai chế độ.

Spring thường dùng Objenesis khi tạo proxy theo lớp để tránh gọi constructor hai lần; tuy nhiên không nên xây dựng logic nghiệp vụ dựa trên chi tiết khởi tạo proxy, và vẫn cần tuân thủ khả năng subclass, visibility và Java module constraints.

### Trường hợp có cả interface và class-based proxy

Một implementation có interface **vẫn có thể** được CGLIB proxy nếu cấu hình bật `proxyTargetClass=true`. JDK proxy không phải là quy tắc bất biến cho mọi bean có interface; đó là **lựa chọn mặc định của Spring AOP trong cấu hình không ép lớp**, còn Spring Boot và các cấu hình tự động có thể thay đổi quyết định cuối cùng.

## 4. So sánh nhanh hai loại proxy

| Khía cạnh | JDK dynamic proxy | CGLIB class-based proxy |
| --- | --- | --- |
| Nền tảng | `java.lang.reflect.Proxy` | Subclass tạo ở runtime |
| Điều kiện chính | Có ít nhất một interface được proxy | Target class có thể subclass |
| Kiểu proxy | Triển khai interface | Kế thừa target class |
| Có thể inject theo interface | Có | Có, nếu target triển khai interface |
| Có thể inject theo concrete implementation | Không, với JDK proxy thông thường | Có |
| Phương thức chỉ tồn tại trên implementation | Không được expose qua interface proxy | Có thể được advice nếu override/visibility hợp lệ |
| Lớp `final` | JDK proxy vẫn có thể wrap interface của một target final | Không subclass được |
| Phương thức `final` | Có thể intercept lời gọi **qua interface** trước khi đi vào target | Không advice được bằng override |
| Phương thức `private` | Không nằm trong hợp đồng interface proxy | Không advice trực tiếp được |
| Lời gọi nội bộ `this.method()` | Bypass proxy | Bypass proxy |
| Hạn chế JPMS | Theo giới hạn proxy/interface/JVM | Có thể vướng quyền mở package cho subclass generation |

Trong thực tế, **thiết kế ranh giới lời gọi** và **tính đúng đắn của nghiệp vụ** quan trọng hơn chênh lệch hiệu năng giữa hai cơ chế. Đừng đổi sang CGLIB chỉ với giả định “nó luôn nhanh hơn” hoặc “nó sửa được self-invocation”.

## 5. Spring chọn kiểu proxy như thế nào?

### 5.1. Trong Spring Framework thông thường

Với Spring AOP theo proxy, khi target có interface và không có cấu hình buộc proxy theo lớp, Spring thường chọn **JDK dynamic proxy**. Khi không có interface phù hợp, Spring sử dụng **CGLIB class proxy**. Có thể ép dùng proxy theo lớp bằng:

```java
@EnableAspectJAutoProxy(proxyTargetClass = true)
```

hoặc với quản lý transaction:

```java
@EnableTransactionManagement(proxyTargetClass = true)
```

và với xử lý asynchronous:

```java
@EnableAsync(proxyTargetClass = true)
```

Đây là **snippet cấu hình annotation**, không phải file Java độc lập. Các annotation này cần import, đặt ở class cấu hình thích hợp và bổ sung dependency tương ứng. `proxyTargetClass=true` chỉ có ý nghĩa trong chế độ proxy; nó **không chuyển** sang AspectJ weaving.

### 5.2. Trong Spring Boot

Spring Boot có auto-configuration AOP và **thường mặc định dùng CGLIB class-based proxy** cho Spring AOP. Khi muốn bật JDK interface-based proxy cho phần auto-configuration đó, có thể cấu hình:

```properties
spring.aop.proxy-target-class=false
```

Hoặc chọn class-based:

```properties
spring.aop.proxy-target-class=true
```

**Không kết luận** chỉ dựa trên thuộc tính này rằng mọi `@Async`/`@Transactional` bean trong mọi phiên bản Spring Boot chắc chắn dùng cùng kiểu. Kiểu proxy thực tế chịu ảnh hưởng phiên bản Framework/Boot, cấu hình annotation, loại bean, post-processor và các cấu hình proxy kết hợp. Khi nhiều bộ auto-proxy cùng hoạt động, cấu hình buộc subclass có thể có ảnh hưởng rộng hơn một bean.

Ví dụ trong **Spring Framework 6.2**, `@EnableAsync(proxyTargetClass = true)` có thể ảnh hưởng tới các bean Spring-managed khác cần proxy, không chỉ những bean có `@Async`. Không dùng nó như một “switch cục bộ tuyệt đối” rồi bỏ qua transaction/AOP đang chạy.

### 5.3. Kiểm tra thay vì đoán

Dùng API Spring:

```java
import org.springframework.aop.support.AopUtils;

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
```

Đây là snippet chạy trong môi trường có bean Spring `bean` thực tế và module `spring-aop`. Kiểm tra sau khi lấy bean từ `ApplicationContext`, không phải đối tượng tự tạo bằng `new`. `bean.getClass().getName()` có thể hữu ích cho debug, nhưng tên lớp proxy tạo động không nên được hard-code trong test.

## 6. Ví dụ thực hành Maven + Java 17: JDK proxy và CGLIB trên cùng một target type

Ví dụ này tạo proxy **bằng Spring `ProxyFactory`**, không phụ thuộc vào cơ chế tự động scan bean. Cách đó làm lựa chọn JDK/CGLIB hoàn toàn rõ ràng. Một `MethodBeforeAdvice` in log khi phương thức `hello(...)` được gọi **qua proxy**; đồng thời phương thức `localCall()` cho thấy self-invocation không được advice.

**Điều kiện:** JDK 17 trở lên, Maven 3.x, Spring Framework **6.2.10** (phiên bản được khóa cho ví dụ).

Cấu trúc:

```text
spring-proxy-modes/
├── pom.xml
└── src/main/java/demo/ProxyModeDemo.java
```

### 6.1. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>spring-proxy-modes</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.10</version>
        </dependency>
        <dependency>
            <groupId>aopalliance</groupId>
            <artifactId>aopalliance</artifactId>
            <version>1.0</version>
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
                <configuration>
                    <mainClass>demo.ProxyModeDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 6.2. Tệp src/main/java/demo/ProxyModeDemo.java

```java
package demo;

import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

public final class ProxyModeDemo {
    // Hợp đồng mà JDK dynamic proxy có thể triển khai.
    public interface Greeter {
        String hello(String name);
    }

    // Phải không-final để CGLIB có thể tạo subclass.
    public static class GreeterImpl implements Greeter {
        @Override
        public String hello(String name) {
            return "hello:" + name;
        }

        // Phương thức này KHÔNG được khai báo trong interface.
        public String localCall() {
            return "local:" + this.hello("inside");
        }
    }

    private static ProxyFactory createFactory(boolean classProxy) {
        ProxyFactory factory = new ProxyFactory(new GreeterImpl());
        factory.setInterfaces(Greeter.class);
        factory.setProxyTargetClass(classProxy);

        factory.addAdvice((MethodBeforeAdvice) (method, args, target) -> {
            if ("hello".equals(method.getName())) {
                System.out.println("advice:" + args[0]);
            }
        });
        return factory;
    }

    public static void main(String[] args) {
        Greeter jdk = (Greeter) createFactory(false).getProxy();
        System.out.println("jdkProxy=" + AopUtils.isJdkDynamicProxy(jdk));
        System.out.println("jdkConcrete=" + (jdk instanceof GreeterImpl));
        System.out.println(jdk.hello("outside"));

        GreeterImpl cglib =
                (GreeterImpl) createFactory(true).getProxy();
        System.out.println("cglibProxy=" + AopUtils.isCglibProxy(cglib));
        System.out.println("cglibConcrete=" + (cglib instanceof GreeterImpl));
        System.out.println(cglib.hello("outside"));
        System.out.println(cglib.localCall());
    }
}
```

Chạy tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java
```

**Standard output từ chương trình (không tính log của Maven):**

```text
jdkProxy=true
jdkConcrete=false
advice:outside
hello:outside
cglibProxy=true
cglibConcrete=true
advice:outside
hello:outside
local:hello:inside
```

### 6.3. Phân tích từng quan sát

- `jdkProxy=true`: factory được chỉ định dùng JDK dynamic proxy. Biến `jdk` là một `Greeter`; proxy không kế thừa `GreeterImpl` nên `jdkConcrete=false`.
- Gọi `jdk.hello("outside")` từ main đi qua proxy, vì vậy dòng `advice:outside` xuất hiện trước dòng kết quả `hello:outside`.
- Với `classProxy=true`, Spring tạo CGLIB proxy kế thừa `GreeterImpl`. `cglibConcrete=true` là hệ quả của quan hệ subclass; gọi `cglib.hello(...)` từ main cũng đi qua advice.
- `cglib.localCall()` gọi tới target; bên trong target, `this.hello("inside")` **không đi lại qua proxy**, nên kết quả chỉ là `local:hello:inside`, **không có** dòng `advice:inside`.
- Ví dụ thể hiện nguyên tắc cho **Spring AOP proxy**. Nó không dùng annotation `@Async` hay mở transaction DB; advice ở đây là `MethodBeforeAdvice` đơn giản để nhìn rõ ranh giới interception.

Nếu có thêm advisor cho `localCall()`, lời gọi bên ngoài có thể được advise ở chính phương thức `localCall()`, nhưng điều đó **không làm phát sinh lời gọi proxy thứ hai cho `this.hello()`**.

**Vì sao dùng Maven thay vì `javac ProxyModeDemo.java`?** Vì `ProxyFactory`, `MethodBeforeAdvice` và CGLIB integration thuộc thư viện Spring; biên dịch trực tiếp chỉ với JDK mà không có classpath của Spring sẽ thất bại. Maven tải dependency và dựng classpath thích hợp; mã nguồn vẫn là Java 17.

## 7. Tác động lên @Async: lời gọi bên ngoài và tự gọi nội bộ

Xét một service do Spring quản lý:

```java
public interface NotificationService {
    java.util.concurrent.CompletableFuture<String> send(String id);
    void trigger(String id);
}
```

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {
    @Override
    @Async("notificationExecutor")
    public CompletableFuture<String> send(String id) {
        String result = "sent:" + id;
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void trigger(String id) {
        // SAI nếu mong @Async được áp dụng ở lời gọi này:
        this.send(id);
    }
}
```

Đây là các **snippet tích hợp**: interface và implementation cần tệp riêng; ứng dụng phải bật `@EnableAsync` và có bean executor tên `notificationExecutor`.

Có hai đường đi khác nhau:

```text
Bean A inject NotificationService
     |
     +-- notificationService.send("x")
     |        |
     |        +--> Spring proxy --> @Async interceptor --> executor
     |                                           |
     |                                           +--> target.send("x")
     |
     +-- notificationService.trigger("x")
              |
              +--> Spring proxy --> target.trigger("x")
                                      |
                                      +--> this.send("x")
                                              |
                                              +--> target.send("x") trực tiếp
                                                   (KHÔNG qua @Async proxy)
```

Vì `trigger()` gọi `this.send()` trên cùng target, mã `send()` ở đường này có thể chạy **trong thread hiện tại** thay vì được dispatch qua executor. Điều này đúng với **cả JDK và CGLIB** ở chế độ proxy thông thường.

### Cách sửa nên ưu tiên: gọi qua bean khác

Tách phương thức bất đồng bộ sang bean riêng và để orchestration ở bean khác:

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

@Service
public class NotificationCoordinator {
    private final NotificationService notificationService;

    public NotificationCoordinator(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public CompletableFuture<String> trigger(String id) {
        // Gọi qua reference được Spring inject -> đi qua proxy.
        return notificationService.send(id);
    }
}
```

Đây cũng là snippet tích hợp với `NotificationService` ở trên. Ưu điểm: ranh giới async rõ ràng, dễ test bằng bean khác và ít phụ thuộc chi tiết AOP. Cần bảo đảm đối tượng được inject thực sự là Spring bean đã proxied; tự tạo `new NotificationServiceImpl()` rồi gọi sẽ không được Spring intercept.

### Chọn kiểu proxy có giải quyết tự gọi nội bộ không?

**Không.** Chuyển `@EnableAsync(proxyTargetClass=true)` chỉ đổi công nghệ tạo proxy, không khiến `this.send()` trên target quay lại proxy. Nếu cần intercept self-invocation, Spring hỗ trợ chế độ `AdviceMode.ASPECTJ` với compile-time/load-time weaving nhưng đây là cơ chế khác, có dependency và vận hành phức tạp hơn. Với phần lớn ứng dụng, thiết kế service gọi service rõ ràng đơn giản và ít rủi ro hơn.

## 8. Tác động lên @Transactional và phạm vi transaction

Với Spring transaction ở proxy mode:

```java
@Service
public class BillingService {
    @Transactional
    public void charge(String id) {
        // Thao tác DB cần nằm trong transaction.
    }

    public void chargeFromSameBean(String id) {
        this.charge(id); // Không đi qua proxy transaction.
    }
}
```

Đây là snippet minh họa; cần import `@Service`, `@Transactional`, datasource/transaction manager và môi trường Spring. Nếu bean khác gọi `billingService.charge(id)` qua proxy, Spring transaction advice có cơ hội mở/tham gia transaction theo annotation. Nếu gọi `this.charge(id)` từ target, advice của phương thức `charge` **không được kích hoạt bởi lời gọi đó**.

**Phân biệt tinh tế:** “self-invocation bỏ qua transaction advice” không đồng nghĩa “chắc chắn không có transaction nào cả”. Nếu phương thức ngoài đã được gọi qua proxy và **đang có transaction**, lời gọi nội bộ vẫn có thể thực thi trong transaction sẵn có, nhưng không kích hoạt thêm cấu hình transaction riêng của phương thức trong (ví dụ `REQUIRES_NEW`). Ngược lại, nếu phương thức ngoài không có transaction, self-invocation thường sẽ không tạo transaction mới chỉ vì phương thức trong được annotate.

Với xử lý bất đồng bộ, transaction imperative do Spring quản lý thường gắn với **thread hiện tại**. Khi `@Async` thực sự chuyển việc sang thread khác, transaction của caller không tự đi theo như một context có thể dùng lại. Nếu task async phải thao tác DB trong transaction, đặt ranh giới transaction tại service/method chạy trong worker qua cơ chế Spring quản lý đúng.

**Lưu ý về visibility trên Spring Framework 6.x:** `@Transactional` trên JDK interface proxy thường đòi method `public` và được khai báo trong interface; với class-based proxy, Spring Framework 6.0+ có thể nhận `protected` hoặc package-visible transactional methods theo cấu hình mặc định, miễn đủ điều kiện intercept. Không suy rộng quy tắc này sang mọi loại annotation/interceptor; để dễ tương thích và dễ test, ưu tiên **public method, non-final, lời gọi từ bean khác**.

Spring khuyến nghị thường đặt `@Transactional` trên **concrete class/method**. Đặt annotation trên interface có thể hoạt động với proxy mode ở các phiên bản mới, nhưng AspectJ weaving không thừa kế annotation từ interface theo quy tắc Java; chuyển cơ chế có thể khiến annotation bị bỏ qua.

## 9. Những lỗi cấu hình và dependency injection điển hình

### Lỗi A: inject implementation nhưng thực tế bean là JDK proxy

```java
@Autowired
private ReportServiceImpl reportService; // Có thể không gán được JDK proxy.
```

**Cách sửa:** inject `ReportService` interface hoặc, nếu có nhu cầu hợp lý về kiểu concrete, cấu hình/kiểm tra class-based proxy. Đổi mỗi kiểu khai báo mà không xem bean được tạo ra thế nào dễ dẫn đến lỗi khởi động.

### Lỗi B: ép kiểu JDK proxy về implementation

```java
ReportService service = applicationContext.getBean(ReportService.class);
ReportServiceImpl impl = (ReportServiceImpl) service; // Sai với JDK proxy.
```

Đây là snippet minh họa với `applicationContext` đã có. JDK proxy không extend implementation. **Không** coi `applicationContext.getBean(...)` trả về target nguyên bản; Spring có thể trả proxy đang chứa advice.

### Lỗi C: đổi qua CGLIB nhưng class final

```java
public final class FinalService {
    public void execute() { }
}
```

Lớp `final` không thể được CGLIB subclass. Với Kotlin, classes/methods thường final mặc định; ứng dụng có thể cần plugin `kotlin-spring`/all-open hoặc thiết kế không-final phù hợp. Đừng bỏ `final` bừa bãi khỏi đối tượng chứa invariant quan trọng; xác định cơ chế proxy và ranh giới API trước.

### Lỗi D: phương thức final/private có annotation nhưng tưởng được advice

Một `@Async` hoặc `@Transactional` trên `private`/`final` method không có nghĩa class-based proxy có thể intercept nó. Khi CGLIB không override được, advice không hoạt động như kỳ vọng. **Sửa:** chuyển logic sang public non-final method của Spring bean khác và gọi qua proxy; tránh sửa visibility chỉ để “vượt qua” kiểm tra mà vẫn còn self-invocation.

### Lỗi E: lấy proxy bằng new, hoặc dùng trong @PostConstruct

Tạo `new BillingService()` không chạy qua Spring post-processors và không có transaction interceptor. Tương tự, `@PostConstruct` là giai đoạn khởi tạo bean; không nên dựa vào proxy để tự gọi các phương thức có `@Async`/`@Transactional` trong đó. Hãy tạo một bean khởi động riêng gọi bean cần xử lý sau khi context đã được thiết lập.

### Lỗi F: tưởng hai loại proxy sửa được mọi lời gọi nội bộ

Cả JDK và CGLIB đều bị giới hạn bởi **đường đi của lời gọi** khi dùng Spring AOP proxy. `this.method()` không qua advice. Nếu cố dùng `AopContext.currentProxy()`, phải cấu hình `exposeProxy=true` và chấp nhận coupling vào Spring AOP; đây không phải lựa chọn mặc định được khuyến nghị. Tách bean hoặc gọi qua một component orchestration riêng thường rõ hơn.

### Lỗi G: ép mọi nơi sang CGLIB mà không xét ảnh hưởng toàn ứng dụng

Một setting proxy-target-class ở mức cấu hình có thể ảnh hưởng nhiều bean AOP/transaction/async cùng lúc. Class proxy còn có hạn chế với final, module path, visibility. **Sửa:** cấu hình có mục đích, kiểm thử kiểu proxy và chức năng giao dịch/async sau thay đổi.

### Lỗi H: suy ra hành vi chỉ từ tên lớp proxy

Tên sinh bởi runtime có thể đổi theo Framework, JVM, build hoặc cấu hình. Dùng `AopUtils.isAopProxy`, `AopUtils.isJdkDynamicProxy` và `AopUtils.isCglibProxy` để kiểm tra thay vì viết logic phụ thuộc chuỗi tên class.

## 10. Checklist kiểm tra proxy trong ứng dụng Spring

Khi `@Async` không chạy bất đồng bộ, transaction không rollback như mong đợi hoặc bean injection lỗi:

1. **Bean có do Spring quản lý không?** Kiểm tra đối tượng được lấy từ `ApplicationContext`, không phải object mới tạo bằng `new`.
2. **Annotation infrastructure đã bật chưa?** Ví dụ `@EnableAsync` cho xử lý async, `@EnableTransactionManagement` hoặc auto-configuration phù hợp cho transaction.
3. **Kiểu proxy thực tế là gì?** Dùng ba phương thức `AopUtils.isAopProxy/isJdkDynamicProxy/isCglibProxy`.
4. **Caller đang giữ kiểu nào?** Với JDK proxy, biến nên có kiểu interface được proxy; với class proxy, kiểm tra lớp có thể subclass không.
5. **Phương thức có khả năng được intercept không?** Có nằm trên interface JDK được proxy? Có final/private/unavailable visibility ở class proxy không? Annotation được đặt nơi Spring nhận diện không?
6. **Lời gọi có đi qua proxy?** `beanFromContainer.method()` khác với `this.method()` bên trong target.
7. **Advice của tính năng đã được đăng ký chưa?** Proxy tồn tại không tự chứng minh interceptor cụ thể cho `@Async`/`@Transactional` được áp dụng.
8. **Async executor có đúng không?** Kiểm tra tên executor trong `@Async("...")`, khả năng reject và thread thực thi.
9. **Transaction đang tồn tại ở đâu?** Xác minh method entry/commit/rollback, propagation và thread boundary; không chỉ nhìn annotation.
10. **Có proxy lồng nhau hoặc nhiều advisor không?** Kiểm tra loại bean thực tế và chuỗi interceptor; không giả định thứ tự advice nếu chưa cấu hình/đo.

### Test phân biệt external và self invocation

Tạo hai test cases:

```text
Case 1:
  otherBean -> proxiedService.process()
  mong đợi: advice của process được áp dụng

Case 2:
  otherBean -> proxiedService.wrapper()
  target.wrapper() -> this.process()
  mong đợi: lời gọi nội bộ KHÔNG kích hoạt advice riêng của process
```

Đối với `@Async`, ghi thread name hoặc dùng latch/future để xác nhận worker khác; đừng chỉ dựa vào `Thread.sleep`. Với `@Transactional`, tạo integration test có transaction manager/database thật, cố ý ném exception rồi kiểm tra rollback, vì stdout một mình không chứng minh transaction hoạt động.

## 11. Chọn kiểu nào cho thiết kế service và concurrency?

**Ưu tiên thiết kế API trước, rồi chọn proxy phù hợp:**

- Nếu ứng dụng có **interface nghiệp vụ ổn định**, inject theo interface giúp caller ít phụ thuộc implementation; JDK proxy hoạt động tự nhiên.
- Nếu không có interface và muốn advice public method của class, CGLIB class proxy là lựa chọn phổ biến, miễn class/method có thể subclass/override.
- Nếu ứng dụng dùng Spring Boot, **đừng mặc định rằng Spring Framework thuần và Spring Boot có cùng lựa chọn proxy**; kiểm tra cấu hình Boot, cấu hình annotation và phiên bản.
- Nếu phải dùng `final` ở class hoặc method, cân nhắc interface proxy nếu method được expose qua interface, hoặc refactor kiến trúc. Không mặc định subclass sẽ hoạt động.
- Nếu cần `@Async` để chạy song song, tách orchestration và async worker thành **hai Spring beans khác nhau** giúp lời gọi chắc chắn đi qua proxy và làm rõ lifecycle/thread boundary.
- Nếu cần transaction đúng trong xử lý async, để async service gọi một **transactional service riêng** trên worker thay vì trông cậy transaction của caller tự được chuyển qua executor.
- Nếu bị giới hạn bởi executor pool/DB connections, thay kiểu proxy **không giải quyết được quá tải**. Cần cấu hình thread pool, queue, connection pool, timeout và backpressure ở tầng phù hợp.

### Sơ đồ kiến trúc tham khảo

```text
RestController
      |
      v
JobCoordinator
      |
      | gọi qua Spring-injected interface
      v
AsyncWorker proxy
      |
      | @Async advice: submit vào executor
      v
worker thread
      |
      v
AsyncWorker target
      |
      | gọi qua Spring-injected transactional service
      v
TransactionalService proxy
      |
      | @Transactional advice trên worker thread
      v
TransactionalService target -> DB
```

Mô hình này tách rõ **proxy boundary**, **thread boundary** và **transaction boundary**; ba điều đó không nhất thiết trùng nhau. Các thành phần trên là vai trò kiến trúc, không phải những tên lớp bắt buộc phải có trong mọi dự án.

## 12. Bài tập và tiêu chí tự kiểm tra

1. Chạy ví dụ Maven ở mục 6. Xác minh JDK proxy triển khai interface nhưng không phải subtype của implementation; CGLIB proxy thì là subtype.
2. Thêm một phương thức public `extra()` chỉ có trong `GreeterImpl`. Quan sát: caller typed `Greeter` không thể gọi `extra()`, còn biến `GreeterImpl` nhận từ CGLIB proxy có thể gọi; nếu thêm advice phù hợp, lời gọi ngoài có thể được intercept.
3. Thử thay `GreeterImpl` thành `final class` và dự đoán hành vi class-based proxy. Không coi bài test thành công nếu nó chỉ tạo JDK proxy do bạn đã vô tình tắt `proxyTargetClass`.
4. Thêm `final` cho phương thức `hello()` và tìm hiểu vì sao class-based advice không còn bảo đảm intercept; so sánh với JDK proxy trên interface method.
5. Đo sự khác biệt giữa `cglib.hello("outside")` và `cglib.localCall()`: vì sao chỉ trường hợp đầu có dòng `advice:outside`?
6. Trong một Spring app, tạo method `@Async` và một method gọi `this.asyncMethod()`. Kiểm tra thread đang chạy và refactor sang bean khác để chứng minh external call được advice.
7. Với `@Transactional(propagation = REQUIRES_NEW)`, thử self-invocation và external invocation trong integration test có database; đối chiếu ranh giới transaction thực tế thay vì chỉ nhìn source code.
8. Cấu hình `spring.aop.proxy-target-class=false` trong ứng dụng Spring Boot có AOP auto-configuration và interface service. Đọc `AopUtils.isJdkDynamicProxy(...)` để xác nhận trong topology/cấu hình thực tế; không suy luận từ property nếu còn cấu hình proxy khác.

**Tiêu chí đạt:** phân biệt được proxy/target, mô tả đúng kiểu và phương thức có thể advice, dự đoán được lỗi inject/cast, chỉ ra rõ self-invocation bypass và tự kiểm tra được bằng code/test thay vì dựa trên tên annotation.

## 13. Tóm tắt và tài liệu chính thức

| Câu hỏi | Câu trả lời ngắn |
| --- | --- |
| JDK dynamic proxy được tạo dựa trên gì? | Interface của target |
| CGLIB được tạo dựa trên gì? | Subclass của target class |
| Interface proxy có thể inject theo implementation class? | Không, với JDK proxy thông thường |
| CGLIB có thể proxy final class/final method? | Không |
| CGLIB có thể intercept phương thức ngoài interface? | Có, nếu class/method đủ điều kiện và advisor áp dụng |
| Đổi JDK sang CGLIB có sửa self-invocation? | **Không** |
| Tại sao `@Async` trên self-call không chuyển thread? | Lời gọi không đi qua async proxy interceptor |
| Chọn JDK/CGLIB chỉ bằng `@EnableAsync` hoặc property? | Cần xét phiên bản và cấu hình AOP tổng thể; đo proxy thực tế |
| Cách khắc phục phổ biến cho self-invocation? | Tách service và gọi qua bean được Spring inject |

**Tài liệu tham khảo:**

1. [Spring Framework 6.2 – Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB, self-invocation, final/private/module constraints.
2. [Spring Framework 6.2 – Creating AOP Proxies with ProxyFactory](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/prog.html): tạo proxy có advice bằng Java code.
3. [Spring Framework 6.2 – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): `@Async`, executor và proxy mode.
4. [Spring Framework 6.2 – Using @Transactional](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html): proxy semantics, method visibility, AspectJ và self-invocation.
5. [Spring Framework 6.2 – @EnableAsync Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html): `proxyTargetClass` và `AdviceMode.PROXY`.
6. [Spring Boot – Aspect-Oriented Programming](https://docs.spring.io/spring-boot/reference/features/aop.html): mặc định CGLIB trong AOP auto-configuration và thuộc tính `spring.aop.proxy-target-class`.

**Ghi nhớ:** JDK proxy dựa trên interface; CGLIB proxy dựa trên subclass. Cả hai cùng giúp Spring đặt interceptor **ở biên lời gọi đi vào proxy**, chứ không tự thay đổi các lời gọi giữa những phương thức của cùng target. Với concurrency, hiểu rõ biên này là điều kiện để `@Async` thực sự chuyển công việc sang executor và `@Transactional` có hiệu lực đúng nơi mong muốn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-invocation-005">Ảnh hưởng của private, final và khả năng chặn lời gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng của private, final và khả năng chặn lời gọi trong Spring concurrency

Trong Spring Framework, khi một phương thức được đánh dấu **@Async**, việc chạy bất đồng bộ thường không xảy ra nhờ bản thân annotation hay vì từ khóa của Java làm đổi luồng. Ở chế độ mặc định **proxy-based**, Spring tạo một **proxy** đứng trước bean. Một lời gọi đi **qua proxy** mới có cơ hội được bộ chặn (interceptor/advice) nhận diện, gửi tác vụ sang **TaskExecutor** và trả về cho caller theo hợp đồng async.

Vì vậy, **private**, **final**, kiểu proxy và **đường đi của lời gọi** ảnh hưởng trực tiếp đến khả năng chặn lời gọi:

- **private method** không thể được gọi qua giao diện public của proxy và không thể bị class-based proxy ghi đè để chặn.
- **final method** không thể bị **class-based (CGLIB) proxy** ghi đè. Nhưng **JDK interface proxy** có thể chặn một lời gọi qua **interface**, kể cả khi phương thức triển khai ở target được khai báo **final**; đây là điểm cần phân biệt.
- **final class** không thể được subclass bằng CGLIB. Tuy vậy, một **final class triển khai interface** vẫn có thể được bao bởi **JDK interface proxy**, nếu cấu hình và lời gọi đi qua interface.
- **self-invocation**, chẳng hạn **this.work()** hoặc gọi tên method không có this ngay trong bean, thường **bỏ qua proxy**, dù work() là public và không final.
- Một **public method có thể chặn** không có nghĩa tất cả lời gọi vào nó đều được chặn: phải xem caller giữ **proxy hay target**.

Bài Knowledge thuộc nhóm **Proxy và lời gọi** của **Spring Framework - concurrency**. Trọng tâm là hiểu rõ vì sao **@Async không chạy trên worker như mong đợi**, cách phân tích lời gọi, tự kiểm chứng bằng Java 17, chọn kiểu proxy và sửa cấu trúc code an toàn.

## 1. Proxy nằm ở đâu trong đường gọi?

Một đối tượng Java thông thường thực hiện lời gọi trực tiếp:

~~~text
Caller ----> instance service ----> service.work()
~~~

Trong Spring AOP ở chế độ proxy, bean được đưa cho caller có thể là **proxy**, không phải đối tượng target nguyên bản:

~~~text
                         lời gọi qua proxy
Caller ----> Proxy ----> Async interceptor ----> Executor
                 |                                 |
                 |                                 +-- worker thread:
                 |                                      target.work()
                 |
                 +-- có thể trả về Future/CompletableFuture

Khi target gọi this.work():
target ------> target.work()         (không quay lại Proxy)
~~~

Đường đi này lý giải hai hiện tượng quan trọng:

1. **Chỉ annotation chưa đủ.** Spring phải bật hỗ trợ async, bean phải được Spring quản lý và lời gọi phải đi qua cấu trúc mà interceptor có thể chặn.
2. **Một lần gọi vào target từ bên trong target không tự động vòng qua proxy.** Vấn đề không nằm ở tên method hoặc độ phức tạp của logic; đó là đặc tính phân phối lời gọi trong proxy-based AOP.

Ở tầng JDK, Spring có thể dùng **JDK dynamic proxy** (đứng trên interface) hoặc **CGLIB subclass proxy** (tạo lớp con của target). Hai phương án đều là proxy, nhưng giới hạn kỹ thuật khác nhau.

**Phạm vi bài:** giải thích cơ chế proxy-based **@Async**, đồng thời chỉ ra những giới hạn proxy cũng thường tác động tới **@Transactional**, **@Cacheable** và lời khuyên AOP khác. Không đồng nhất tất cả annotation hoặc tất cả phiên bản Spring: chúng có quy tắc phát hiện annotation, phạm vi phương thức và cấu hình riêng.

## 2. private trong Java và vì sao proxy không chặn được

Một **private method** chỉ truy cập trực tiếp được bên trong class khai báo nó, theo quy tắc truy cập của Java. Khi một method public gọi helper private, lời gọi private được xử lý bên trong đối tượng; nó **không trở thành một lời gọi đi qua proxy chỉ vì method private có @Async**.

~~~java
import org.springframework.scheduling.annotation.Async;

public class InvoiceService {
    public void submitInvoice() {
        sendInBackground(); // Gọi nội bộ, không đi qua proxy.
    }

    @Async
    private void sendInBackground() {
        // Không được Spring proxy intercept để chuyển sang thread khác.
    }
}
~~~

Đây là **đoạn minh họa trong một ứng dụng Spring**; không phải tệp Java độc lập. Với proxy-based async:

- CGLIB tạo subclass để intercept các method có thể override; **private không được override** trong Java, nên proxy không chèn interceptor vào lời gọi này.
- JDK proxy chỉ trình bày phương thức thuộc **interface được proxy**, không tạo một điểm gọi private của implementation cho caller.
- Annotation đặt trên private method **không biến lời gọi nội bộ thành async**.
- Nếu gọi **submitInvoice()** trực tiếp từ request thread, helper private thường chạy trong cùng thread đó, trừ khi code chủ động dùng một executor ở chỗ khác.

**Điều cần tránh:** chạy qua debugger thấy @Async trên private rồi suy ra method đã gửi task; cần kiểm tra **Thread.currentThread()** hoặc kiểm thử tương quan thread identity.

### Một helper private vẫn có thể chạy trên worker khi nào?

Nếu **một public method được proxy chặn và chuyển sang executor**, method public đó có thể gọi helper private *bên trong worker*. Điều này hợp lệ và hữu ích:

~~~java
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    @Async("notificationExecutor")
    public void deliver(String recipient) {
        validateAndSend(recipient); // Chạy trên thread đã thực thi deliver.
    }

    private void validateAndSend(String recipient) {
        // Helper nội bộ; không cần @Async riêng.
    }
}
~~~

**deliver** phải được **bean khác gọi qua proxy** và cấu hình hỗ trợ async phải hợp lệ. Helper **validateAndSend** được chạy từ body của deliver, tức là có thể chạy trên worker sau khi deliver được dispatch; **không phải** vì proxy đã chặn helper private.

Tách hai ý:

- “private method được intercept độc lập bởi Spring AOP proxy?” → **Không**.
- “private helper có thể được thực hiện trong worker do public @Async gọi?” → **Có**, nếu public @Async đã thực sự được dispatch.

Đây là lý do việc **đổi @Async từ private helper sang một public entry point** thường giải quyết được trường hợp lỗi, miễn caller gọi public entry point qua proxy.

## 3. final method: không thể override bằng CGLIB

**final method** trong Java không cho lớp con override. CGLIB class-based proxy dùng cách tạo subclass của target, rồi **ghi đè các method có thể override** để đưa advice vào đường gọi.

Vì thế, một method **public final** không thể được CGLIB chặn bằng kỹ thuật override:

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;

public class BillingService {
    @Async("billingExecutor")
    public final CompletableFuture<String> calculate() {
        return CompletableFuture.completedFuture("ready");
    }
}
~~~

Đây là **đoạn minh họa**, không phải chương trình chạy đầy đủ. Khi Spring dùng **class-based proxy**, **calculate()** là final nên interceptor không thể override method đó để dispatch sang executor. Lời gọi tới final method có thể đi thẳng vào implementation theo Java và **không có ranh giới @Async** như dự kiến.

**Không được phát biểu quá rộng rằng mọi final method trên mọi kiểu proxy đều không thể chặn.** Hạn chế override này thuộc **class-based proxy**. Một interface-based proxy có cơ chế khác, phân tích ở mục 5.

### final class nghiêm ngặt hơn final method

~~~java
public final class ClosedService {
    public String work() {
        return "ok";
    }
}
~~~

Một subclass **không thể kế thừa ClosedService**, nên CGLIB không thể tạo class proxy của class này. Nếu bean cần class-based proxy, ứng dụng có thể gặp lỗi tạo proxy trong quá trình khởi động; không nên giả định Spring sẽ tự biến một final class thành proxy hợp lệ.

Nếu **final class có interface nghiệp vụ** và cấu hình phù hợp, JDK dynamic proxy vẫn có thể đứng **trước interface** để chặn lời gọi đến instance của final class. Khi đó bean được inject bằng **interface**, không phải đối tượng proxy có cùng concrete class.

**final field khác final method.** Field final ngăn gán lại tham chiếu sau khởi tạo (theo quy tắc Java), không ngăn một **public non-final method** của class được subclass proxy intercept. Việc bean chứa field final **không tự động khiến @Async thất bại**.

| Thứ được khai báo final | Tác động |
| --- | --- |
| **final local variable** | Không cho gán lại biến trong phạm vi Java. |
| **final field** | Không cho gán lại field theo quy tắc Java; không khóa các object nested mutable. |
| **final method** | Không cho override; **CGLIB không thể đặt advice bằng override** lên method đó. |
| **final class** | Không cho subclass; **CGLIB không thể tạo subclass proxy** của class đó. |

Khi review một class, hãy xem final áp lên **điều gì**, không chỉ tìm chuỗi “final”.

## 4. Giới hạn modifier: đối chiếu trực tiếp JDK và CGLIB

Bài về hai kiểu proxy đã so sánh cấu trúc và khả năng injection. Ở đây tập trung vào một câu hỏi hẹp: **modifier Java nào quyết định method có thể được chặn độc lập?**

| Điều kiện trên target | JDK interface proxy | CGLIB class-based proxy |
| --- | --- | --- |
| `private method` | Không có lời gọi vào private method qua interface proxy. | Không thể override để intercept. |
| `public final method` thuộc interface | Có thể intercept **lời gọi qua interface proxy** trước khi delegate tới implementation final. | Không thể override final method để advice. |
| `final class` triển khai interface | JDK proxy vẫn có thể đứng trước interface nếu advisor/cấu hình phù hợp. | Không tạo subclass từ final class. |
| `this.publicAsync()` nội bộ | Lời gọi bỏ qua proxy. | Lời gọi bỏ qua proxy. |

JDK proxy xử lý lời gọi qua `InvocationHandler` **trước** khi ủy quyền tới target, nên implementation final không ngăn interface interception. CGLIB cần subclass/override, do đó tuân thủ `final` và `private`. Đây là bảng **khả năng kỹ thuật**, không bảo đảm annotation đã được Spring nhận diện hoặc executor đã nhận task; luôn kiểm tra proxy type, method signature và đường gọi thực. Ví dụ tiếp theo chứng minh ranh giới JDK Proxy bằng Java thuần.

## 5. Ví dụ Java 17 chạy độc lập: final target vẫn được JDK interface proxy chặn

Ví dụ đầu tiên **không dùng Spring**; nó chỉ minh họa **đúng cơ chế JDK dynamic proxy** để phân biệt “final ở target” và “chặn lời gọi ở proxy”. Ta ghi số lần handler nhìn thấy method run. Sau khi gọi run qua proxy, counter tăng. Sau khi gọi outer qua proxy, target tự gọi run bằng **this.run()**, counter không tăng thêm.

Lưu thành tệp **InterfaceProxyDemo.java**:

~~~java
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

public class InterfaceProxyDemo {
    public interface TaskPort {
        String run();
        String outer();
    }

    public static final class FinalTarget implements TaskPort {
        @Override
        public final String run() {
            return "work-done";
        }

        @Override
        public String outer() {
            return this.run();
        }
    }

    public static void main(String[] args) {
        FinalTarget target = new FinalTarget();
        AtomicInteger advisedRunCount = new AtomicInteger();

        TaskPort proxy = (TaskPort) Proxy.newProxyInstance(
                TaskPort.class.getClassLoader(),
                new Class<?>[] { TaskPort.class },
                (p, method, params) -> {
                    if (method.getName().equals("run")) {
                        advisedRunCount.incrementAndGet();
                    }
                    return method.invoke(target, params);
                }
        );

        System.out.println("Kết quả proxy.run: " + proxy.run());
        System.out.println("Final method qua interface được chặn: "
                + (advisedRunCount.get() == 1));

        System.out.println("Kết quả proxy.outer: " + proxy.outer());
        System.out.println("Lời gọi this.run được chặn: "
                + (advisedRunCount.get() == 2));
    }
}
~~~

Lệnh:

~~~bash
javac -encoding UTF-8 InterfaceProxyDemo.java
java InterfaceProxyDemo
~~~

**Stdout của chương trình:**

~~~text
Kết quả proxy.run: work-done
Final method qua interface được chặn: true
Kết quả proxy.outer: work-done
Lời gọi this.run được chặn: false
~~~

**Lý giải:**

1. **FinalTarget** là final class, và **run()** của nó cũng là final.
2. **proxy.run()** là lời gọi qua interface **TaskPort**. InvocationHandler chạy, tăng counter rồi mới gọi target.run().
3. **proxy.outer()** cũng qua handler, nhưng handler chỉ tăng counter nếu method được gọi tên **run**. Đến bên trong target.outer(), **this.run()** gọi target.run() trực tiếp, **không gọi lại interface proxy**.
4. Vì vậy counter của run vẫn bằng 1 sau lời gọi outer, nên dòng cuối là false.
5. Đây là ví dụ **JDK Proxy tự viết**, không chứng minh mọi cấu hình Spring @Async áp dụng thành công trên final method. Muốn kiểm chứng Spring phải dùng Spring context và @Async thực sự như các phần sau.

**Quan sát:** final class và final implementation không tự chặn JDK interface proxy. Vấn đề nằm ở việc lời gọi có đi qua proxy và advice được cấu hình để xử lý lời gọi đó hay không.

## 6. Lời gọi nội bộ (self-invocation): public cũng có thể mất advice

Xét một service:

~~~java
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
    public void startExport() {
        exportAsync(); // Tương đương this.exportAsync().
    }

    @Async("exportExecutor")
    public void exportAsync() {
        // Công việc nặng.
    }
}
~~~

Nếu một bean khác gọi **startExport()** qua proxy, phương thức startExport có thể được thực thi như một cuộc gọi thông thường. Bên trong, lời gọi **this.exportAsync()** không quay ra proxy để Spring phát hiện @Async. Vì thế **exportAsync có thể chạy đồng bộ ngay trên thread caller của startExport**.

Đây là lỗi rất dễ xuất hiện khi lập trình viên nghĩ:

> “Tôi đã ghi @Async trên method rồi, mọi lần gọi method đều chạy bất đồng bộ.”

Thực tế **@Async ở chế độ proxy chặn *đường gọi*, không biến method thành một thân hàm có cơ chế đa luồng tự động**.

Không chỉ cách viết explicit this mới mắc lỗi. Cả hai dạng sau đều là lời gọi nội bộ:

~~~java
this.exportAsync();
exportAsync();
~~~

Nếu gọi trực tiếp một instance tạo bằng **new ExportService()**, kể cả từ bean khác, instance đó không được Spring proxy quản lý; annotation không có tác dụng như khi đi qua Spring bean proxy.

## 7. Cách sửa self-invocation an toàn: tách điểm gọi sang bean khác

Cách dễ hiểu và dễ test nhất là **tách orchestration** và **async worker** thành hai bean; một bean inject bean còn lại.

**ExportWorker.java** — public entry point:

~~~java
package example.demo;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ExportWorker {
    @Async("exportExecutor")
    public CompletableFuture<String> export(long orderId) {
        String result = "export-" + orderId;
        return CompletableFuture.completedFuture(result);
    }
}
~~~

**ExportFacade.java** — gọi qua bean do Spring inject:

~~~java
package example.demo;

import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

@Service
public class ExportFacade {
    private final ExportWorker worker;

    public ExportFacade(ExportWorker worker) {
        this.worker = worker;
    }

    public CompletableFuture<String> start(long orderId) {
        return worker.export(orderId);
    }
}
~~~

Đoạn code này giả định có **@EnableAsync**, executor tên **exportExecutor** và Spring component scanning hoặc đăng ký bean phù hợp. Đây là **mẫu nhiều file cho ứng dụng Spring**, không phải chương trình javac độc lập.

Vì **worker** là dependency được Spring cung cấp, caller dùng đối tượng proxy hợp lệ khi bean được tạo với async advice. Lời gọi worker.export(...) đi qua proxy, interceptor có thể đưa việc vào executor. Đây là cách làm ưu tiên so với việc nhồi tất cả method vào cùng một class rồi thử gọi self-reference.

### Có nên inject proxy của chính mình?

Có thể có các kỹ thuật tự inject chính bean qua proxy hoặc gọi **AopContext.currentProxy()**, nhưng chúng tạo coupling khó hiểu, có thể gây vòng phụ thuộc hoặc yêu cầu cấu hình exposeProxy riêng. **Không nên chọn làm giải pháp đầu tiên**. Tách một worker bean với trách nhiệm rõ ràng thường đơn giản, dễ kiểm thử và ít bất ngờ hơn.

## 8. Ví dụ Spring thực tế: so sánh public, private, final và self-call

Ví dụ sau sử dụng **Spring Framework 6.2.x**, **Java 17**, một **class-based proxy** được yêu cầu tường minh bằng **@EnableAsync(proxyTargetClass = true)**. Nó dùng method trả **CompletableFuture<Boolean>**, rồi kiểm tra body thực sự chạy ở thread khác caller hay không. Để kết quả rõ, không in tên thread vốn có thể thay đổi.

Cấu trúc:

~~~text
async-proxy-demo/
├── pom.xml
└── src/main/java/example/ProxyAsyncDemo.java
~~~

**pom.xml**:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>async-proxy-demo</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.12</version>
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
                <configuration>
                    <mainClass>example.ProxyAsyncDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/example/ProxyAsyncDemo.java**:

~~~java
package example;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class ProxyAsyncDemo {
    @Configuration
    @EnableAsync(proxyTargetClass = true)
    public static class Config {
        @Bean("lessonExecutor")
        public ThreadPoolTaskExecutor lessonExecutor() {
            ThreadPoolTaskExecutor executor =
                    new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(10);
            executor.setThreadNamePrefix("lesson-");
            return executor;
        }

        @Bean
        public Worker worker() {
            return new Worker();
        }
    }

    public static class Worker {
        @Async("lessonExecutor")
        public CompletableFuture<Boolean> publicAsync(Thread caller) {
            return CompletableFuture.completedFuture(
                    Thread.currentThread() != caller);
        }

        public boolean selfCall(Thread caller) {
            return this.publicAsync(caller).join();
        }

        @Async("lessonExecutor")
        private CompletableFuture<Boolean> privateAsync(Thread caller) {
            return CompletableFuture.completedFuture(
                    Thread.currentThread() != caller);
        }

        public boolean callPrivate(Thread caller) {
            return privateAsync(caller).join();
        }

        @Async("lessonExecutor")
        public final CompletableFuture<Boolean> finalAsync(Thread caller) {
            return CompletableFuture.completedFuture(
                    Thread.currentThread() != caller);
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            Worker worker = context.getBean(Worker.class);
            Thread caller = Thread.currentThread();

            System.out.println("Public qua proxy: "
                    + worker.publicAsync(caller)
                            .get(5, TimeUnit.SECONDS));
            System.out.println("Tự gọi public: "
                    + worker.selfCall(caller));
            System.out.println("Gọi private bên trong: "
                    + worker.callPrivate(caller));
            System.out.println("Gọi final qua CGLIB: "
                    + worker.finalAsync(caller).join());
        }
    }
}
~~~

Chạy từ thư mục chứa **pom.xml** với JDK 17 và Maven:

~~~bash
mvn -q compile exec:java
~~~

**Stdout mong đợi của ứng dụng** trong cấu hình đã nêu (không tính thông tin Maven/Spring logging):

~~~text
Public qua proxy: true
Tự gọi public: false
Gọi private bên trong: false
Gọi final qua CGLIB: false
~~~

**Giải thích kỹ các dòng:**

1. **Public qua proxy: true.** Một caller ngoài bean gọi method publicAsync trên proxy CGLIB. Async interceptor gửi task tới lessonExecutor; body so sánh **Thread.currentThread()** với caller và thấy khác nhau. **get(timeout)** chờ kết quả để output ổn định.
2. **Tự gọi public: false.** Method **selfCall** chạy bình thường, rồi gọi **this.publicAsync** trên target, không vòng qua proxy. Body chạy ngay tại caller thread. Giá trị CompletableFuture ở đây chỉ là completedFuture được tạo trực tiếp; không có dispatch @Async.
3. **Gọi private bên trong: false.** **callPrivate** gọi privateAsync trực tiếp, không có điểm override/interception. Body vẫn chạy trên thread gọi callPrivate.
4. **Gọi final qua CGLIB: false.** Spring được ép tạo class-based proxy, nhưng CGLIB không thể override finalAsync. Lời gọi không được interceptor @Async bao quanh; code so sánh thread ra false.
5. **Worker** không final, để CGLIB tạo subclass proxy. Chính **final method** trong class mới được dùng để minh họa hạn chế. Class-based proxy không thể khắc phục lỗi này chỉ bằng cách thêm annotation.

Ví dụ này **dùng các boolean và lệnh get(timeout)** thay vì dựa vào lịch scheduler, thứ tự log hay tên thread. Các cấu hình **proxyTargetClass**, tên executor và phạm vi bean đều được khai báo tường minh. Nếu môi trường dùng cấu hình AOP khác hoặc phiên bản Spring khác, hãy kiểm tra proxy thực tế và điều chỉnh cách kiểm thử, thay vì lấy stdout này làm bảo đảm cho mọi môi trường.

**Lưu ý quan trọng về @Async:** return type trong ví dụ là **CompletableFuture<Boolean>**. Theo hợp đồng Spring, một phương thức @Async trả kết quả được khai báo bằng **Future/CompletableFuture**; bên trong thân method có thể trả **completedFuture** chứa kết quả đã tính, còn interceptor cung cấp ranh giới bất đồng bộ cho caller. Không bọc thêm **supplyAsync** vào common pool nếu không có chủ đích.

## 9. Khi dùng interface-based proxy: final implementation có thể vẫn intercept

Giả sử một service cung cấp **interface public**:

~~~java
package example.api;

import java.util.concurrent.CompletableFuture;

public interface TaskPort {
    CompletableFuture<String> perform(String id);
}
~~~

Implementation:

~~~java
package example.api;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public final class TaskPortImpl implements TaskPort {
    @Override
    @Async("taskExecutor")
    public final CompletableFuture<String> perform(String id) {
        return CompletableFuture.completedFuture("done:" + id);
    }
}
~~~

Đây là **hai tệp riêng trong một ứng dụng Spring**, không phải một ví dụ Maven đầy đủ. Với **JDK interface-based proxy** và async advisor phát hiện được annotation, lời gọi **taskPort.perform(id)** qua **TaskPort proxy** có thể được intercept, dù implementation method và class đều là final. Vì JDK proxy không tạo subclass của **TaskPortImpl**.

Để áp dụng an toàn:

- Inject bean theo **TaskPort**, không yêu cầu proxy là TaskPortImpl.
- Kiểm tra ứng dụng thực sự chọn **JDK dynamic proxy** chứ không bị cấu hình toàn cục ép CGLIB.
- Kiểm tra annotation được Spring phát hiện theo vị trí đặt annotation/phiên bản đang dùng.
- Viết integration test chứng minh **method thực sự chạy trên executor**, không chỉ assert annotation tồn tại.
- Nếu ứng dụng ép class-based proxy đối với **TaskPortImpl final**, cách tạo proxy có thể lỗi. Khi đó cần bỏ final class, đổi strategy hay tổ chức lại interface/service.

Điểm quan trọng là **không dùng phương án “cứ bỏ final khỏi tất cả method”** nếu dự án có chính sách Java khác. Trước tiên xác định proxy type và API exposure; tuy nhiên với một worker @Async mới, một **public non-final method trên bean không final được Spring quản lý** vẫn là lựa chọn dễ hiểu, dễ dùng và ít rủi ro.

## 10. Spring chọn proxy nào? Cách kiểm tra runtime

Ở ứng dụng Spring 6.x, **@EnableAsync** mặc định hoạt động với **AdviceMode.PROXY**. Thuộc tính **proxyTargetClass** cho phép yêu cầu class-based proxy khi dùng chế độ PROXY.

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync(proxyTargetClass = true)
public class AsyncConfiguration {
}
~~~

Đây là **tệp cấu hình Spring**. Việc ép proxy class-based có thể ảnh hưởng tới các bean cần AOP khác tùy cơ chế cấu hình toàn cục. Bởi vậy, không tùy tiện bật nó chỉ để sửa một private/final method.

Một số ứng dụng Spring Boot có thể kích hoạt class-based proxy theo property hoặc cấu hình AOP khác. Khi cần kết luận chính xác, **kiểm tra instance proxy thật**:

~~~java
import org.springframework.aop.support.AopUtils;

Object bean = applicationContext.getBean("worker");

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
~~~

Đây là **đoạn minh họa trong một method có biến applicationContext**; cần Spring AOP trong classpath. Các boolean phản ánh kiểu proxy của bean **tại thời điểm chạy**. Đừng đoán proxy type từ tên class source hoặc từ tên bean.

Cần chú ý rằng **JDK proxy không phải subtype của concrete target**. Code inject phụ thuộc bằng concrete class có thể không hoạt động, dù interface-based proxy hoàn toàn hợp lệ cho các method được interface công khai.

### Bảng kiểm đường gọi

| Trường hợp | Có cơ hội Spring proxy advice chạy? | Vì sao |
| --- | --- | --- |
| Bean khác gọi public non-final method qua class proxy | **Có**, nếu advice khớp | Proxy override method để intercept |
| Bean khác gọi public interface method qua JDK proxy | **Có**, nếu advice khớp | Lời gọi đi tới invocation handler |
| Method private có @Async, caller nội bộ gọi | **Không** | Private không là method có thể intercept qua proxy |
| Method final qua CGLIB | **Không** | Không thể override |
| Method final của target được gọi qua JDK interface proxy | **Có thể** | Điểm chặn nằm ở interface proxy, không cần override final implementation |
| final class bị yêu cầu CGLIB proxy | **Không** | Không thể subclass |
| final class triển khai interface, JDK proxy phù hợp | **Có thể** | JDK proxy implements interface |
| Bean gọi this.publicAsync() | **Không** | Self-invocation bỏ qua proxy |
| Một instance tạo bằng new thay cho Spring bean | **Không** | Instance không qua proxy của Spring |
| Public @Async chạy trên worker và gọi private helper | Helper chạy trên worker, **nhưng không được intercept riêng** | Worker context đã được thiết lập ở public entry point |

Bảng nói về **khả năng chặn lời gọi**, không phải cam kết mọi lời gọi luôn bất đồng bộ: còn phụ thuộc @EnableAsync, advisor, executor, bão hòa, rejection, cách inject và độ hợp lệ của return type.

## 11. Vấn đề còn lại: private/protected/package-private và quy tắc riêng từng annotation

Trong Spring AOP, private và final là hai giới hạn cấu trúc rất rõ của class-based proxy. Tuy nhiên, với những mức visibility còn lại, không nên đưa một quy tắc duy nhất áp cho tất cả annotation:

- Với **@Async**, để bài học và code production dễ dự đoán, **public method** được bean khác gọi qua proxy là mẫu nên ưu tiên.
- Một số subsystem khác trong Spring 6.x, chẳng hạn **@Transactional** trên class-based proxy, có hỗ trợ những method **protected hoặc package-private** trong điều kiện nhất định; điều đó **không tự chứng minh @Async trên mọi non-public method hoạt động tương tự**.
- Một package-private method ở superclass khác package có thể **không nhìn thấy được để override** từ proxy subclass, hiệu quả gần giống private.
- **Static method** cũng không được override như instance method thông thường; @Async được thiết kế quanh lời gọi instance qua Spring bean proxy. Không xem static method như một @Async entry point đáng tin.
- **Constructor** và **@PostConstruct** không phải một điểm vào proxy-based @Async bình thường. Nếu cần khởi động việc async sau khi bean sẵn sàng, dùng một bean khác thực hiện lời gọi phù hợp khi lifecycle cho phép.

**Sai lầm thường gặp:** đọc một bài hướng dẫn riêng về @Transactional, rồi cho rằng private/protected/final có cùng hành vi ở @Async. Hãy phân biệt giới hạn AOP **chung** với hợp đồng và cơ chế **riêng** của từng annotation.

### Kotlin và record là những trường hợp đáng chú ý

Kotlin class và method mặc định là **final** trừ khi khai báo open hoặc áp dụng plugin hỗ trợ. Nếu dùng Spring AOP class-based proxy, cần chú ý cách compiler/plugin tạo class và method có thể được proxy. **Không tự giả định annotation Spring luôn khiến mọi Kotlin method mở.**

Trong Java, **record** là **final class**. Một record làm DTO bất biến có thể rất phù hợp để **truyền dữ liệu cho task**, nhưng không phải loại bean thích hợp nếu bạn cần CGLIB subclass proxy cho @Async entry point. Tách DTO record và service bean có method public non-final sẽ giảm xung đột.

## 12. Sự khác nhau giữa @Async và CompletableFuture.supplyAsync

Một câu hỏi quan trọng: “Nếu private hoặc final method không bị @Async intercept, có cách nào vẫn chạy task trên thread khác không?”

Có, nhưng **đó không phải interception của annotation**. Một method có thể **chủ động gửi task tới ExecutorService hoặc CompletableFuture.supplyAsync**, dù method bao quanh private/final. Cần tách hai cơ chế:

**Cơ chế A — Spring proxy chặn lời gọi:**

~~~java
@Async("jobExecutor")
public CompletableFuture<String> runAsync() {
    return CompletableFuture.completedFuture("ok");
}
~~~

Khi gọi qua Spring proxy hợp lệ, interceptor chuyển việc sang executor.

**Cơ chế B — tự submit bên trong method:**

~~~java
private CompletableFuture<String> createTask(
        java.util.concurrent.Executor executor) {
    return CompletableFuture.supplyAsync(
            () -> "ok",
            executor);
}
~~~

Đây là **hai đoạn minh họa không độc lập**, có import/context tương ứng. Trong cơ chế B, task có thể chạy trên executor **vì code gọi supplyAsync**; **không có yêu cầu phải proxy private method**.

Đừng lấy việc thấy một CompletableFuture trả về để kết luận @Async đã được áp dụng. **CompletableFuture.completedFuture** có thể hoàn tất ngay trong caller. Ngược lại, supplyAsync có thể thật sự chạy ở worker **mà không cần annotation**.

**Nguy cơ double scheduling:** nếu method public đã được @Async proxy chuyển sang executor, rồi trong body lại gọi supplyAsync không truyền executor hoặc truyền executor khác, tác vụ có thể bị đẩy sang luồng thứ hai ngoài kế hoạch. Điều này làm khó quản lý context, shutdown, transaction, lỗi và đo latency. Chỉ thêm một tầng bất đồng bộ khi có lý do cụ thể.

## 13. Tính đúng đắn của việc chạy @Async cần kiểm tra gì ngoài proxy?

Một lời gọi đã đi qua proxy chưa bảo đảm hệ thống xử lý công việc thành công:

1. **Executor có nhận task không?** Pool có thể bão hòa hoặc shutdown và ném **TaskRejectedException** ở ranh giới submit.
2. **Tác vụ đã bắt đầu chưa?** Có thể phải chờ trong queue tùy cấu hình ThreadPoolTaskExecutor.
3. **Kết quả/exception được trả qua gì?** Với Future/CompletableFuture, caller có thể quan sát thành công hoặc lỗi. Với @Async void, lỗi task không tự biến thành exception trong caller.
4. **Caller có tự chặn lại bằng join()/get()?** Nếu gọi get ngay, caller đợi và không còn hưởng lợi về thời gian phản hồi.
5. **Dữ liệu đầu vào đã an toàn để sang thread khác chưa?** Mutable object và request-scoped context có thể gây race/đọc sai dữ liệu, kể cả khi proxy chạy đúng.
6. **ThreadLocal/SecurityContext/MDC/transaction có cần propagation không?** Những ngữ cảnh gắn thread không mặc nhiên xuất hiện đúng ở worker.
7. **Task có deadline/cancellation và graceful shutdown không?** Chạy async mà giữ worker vô hạn vẫn có thể làm nghẽn hệ thống.

Đừng sửa lỗi proxy bằng cách làm **mọi field singleton mutable** rồi đọc từ background thread. Cách đó khiến nhiều request ảnh hưởng tới nhau. Hãy truyền dữ liệu nghiệp vụ qua method parameter/immutable snapshot và quản lý giới hạn executor.

## 14. Ngoại lệ: AspectJ weaving không dùng cùng mô hình proxy

Spring có thể dùng **AdviceMode.ASPECTJ** cho hỗ trợ async với cấu hình và dependency phù hợp, thay vì **AdviceMode.PROXY** mặc định. AspectJ dùng cơ chế **weaving** (compile-time hoặc load-time) để gắn advice ở các join point, không phụ thuộc vào một wrapper proxy đứng trước target theo cùng cách.

Hệ quả quan trọng:

- Giới hạn **self-invocation bỏ qua proxy** có thể được khắc phục với cơ chế AspectJ weaving đúng, vì không còn phụ thuộc vào lời gọi đi qua một đối tượng proxy.
- Chỉ đổi **@EnableAsync(mode = AdviceMode.ASPECTJ)** trong source rồi chạy lại **không đủ**. Cần **spring-aspects** và cấu hình weaving thích hợp; phải kiểm chứng artifact đã được weave và advisor thực tế có hoạt động.
- Thuộc tính **proxyTargetClass** không phải chìa khóa khi dùng AdviceMode.ASPECTJ, vì lúc đó không tạo proxy theo hai kiểu JDK/CGLIB như phần trên.
- Không tự động khẳng định **mọi private/final method** được xử lý theo cách giống nhau trong mọi thiết lập weaving; cần xem loại join point, rules của AspectJ, phiên bản, visibility và integration tests.

Trong phần lớn ứng dụng Spring sử dụng @Async thông thường, **tách một public async service bean và gọi qua proxy** là giải pháp đơn giản, ít cấu hình hạ tầng hơn chuyển sang weaving.

## 15. Lỗi khó nhận biết khi kết hợp @Transactional và @Async

Đây là tình huống liên quan trực tiếp tới **khả năng chặn lời gọi**, không phải bài chuyên sâu về transaction:

~~~java
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

public class OrderService {
    public void create() {
        saveInTransaction(); // Self-invocation có thể bỏ qua advice.
    }

    @Transactional
    public void saveInTransaction() {
        // Nếu chỉ được gọi qua this, transaction proxy có thể không chạy.
    }

    @Async
    public void notifyAsync() {
        // Nếu gọi this.notifyAsync(), async proxy có thể không chạy.
    }
}
~~~

Đây là **đoạn minh họa** với cấu hình Spring tương ứng; không phải service hoàn chỉnh. Với proxy-based AOP, cả @Transactional và @Async có thể bị bypass bởi self-invocation. Nhưng sửa bằng cách tách bean không có nghĩa **transaction sẽ tự chuyển từ request thread sang worker thread**. Transaction context gắn thread và thường cần boundary của chính worker.

Ví dụ về thiết kế:

~~~text
OrderFacade (HTTP/request thread)
    |
    +-- orderService.save(...)       -> transaction riêng
    |
    +-- notificationWorker.send(...) -> @Async qua proxy
                                         -> worker logic / transaction nếu cần
~~~

Nếu cần công việc chỉ được gửi **sau khi giao dịch commit**, cân nhắc cơ chế **transactional event/outbox** phù hợp. Chỉ gọi @Async trước commit có thể làm worker đọc trạng thái database chưa được commit hoặc gặp thứ tự nghiệp vụ sai.

**Không trộn khái niệm:** lỗi “@Async không chạy vì final/private/self-call” là lỗi **interception**. Lỗi “worker không có transaction/request context” là lỗi **thread/context boundary**. Hai vấn đề có thể cùng xuất hiện nhưng phải chẩn đoán và sửa riêng.

## 16. Kiểm thử thật: đừng chỉ nhìn thread name

Trong kiểm thử Spring AOP, tối thiểu cần kiểm tra:

- **Bean lấy từ ApplicationContext**, không phải instance tự new.
- **AopUtils.isAopProxy(bean)** xác nhận có proxy nếu cấu hình yêu cầu.
- **AopUtils.isJdkDynamicProxy / isCglibProxy** xác định kiểu, không suy đoán.
- Lời gọi @Async được thực hiện từ **một bean khác** hoặc từ runner giữ Spring-managed proxy.
- Tác vụ xác nhận **Thread.currentThread() khác caller** khi cần chứng minh dispatch sang worker.
- Với CompletableFuture, **get(timeout)** để tránh test treo không giới hạn.
- Đặt test rõ cho **private, final và self-invocation** để ghi nhận các trường hợp không có interception.
- Test trường hợp executor **reject** và trường hợp logic worker **ném lỗi**, không nhầm với lỗi proxy.

Một test vô tình có thể báo pass dù @Async không hoạt động:

~~~java
@Async
public CompletableFuture<String> method() {
    return CompletableFuture.completedFuture("ok");
}
~~~

Nếu test chỉ assert future.get() trả “ok”, nó vẫn đúng **khi method chạy đồng bộ vì self-invocation**. Cần kiểm tra **thread identity hoặc tín hiệu về dispatch**, chẳng hạn dùng CountDownLatch chặn body và quan sát caller có trả về trước khi body hoàn tất không.

Không nên dựa vào **Thread.sleep(100)** để mong chắc chắn worker đã chạy. Latch/Barrier và timeout khiến kiểm thử rõ ràng hơn. Đừng assert chính xác tên thread hoặc thứ tự log nếu executor có nhiều worker.

### Dùng CountDownLatch để kiểm chứng ranh giới proxy

Một kiểm thử có thể:

1. Ghi lại **Thread caller** trước khi invoke bean.
2. Chuẩn bị worker method public non-final có **CountDownLatch started** và **CountDownLatch release**.
3. Gọi qua proxy, kiểm tra caller có nhận Future trước khi mở release, khi executor chưa bão hòa.
4. Sau đó mở release và chờ future.get(timeout).
5. Ghi **Thread.currentThread()** trong worker, assert khác caller.
6. Lặp lại cho self-call từ target và đối chiếu, nhưng **không dùng chính một caller thread đang bị giữ để điều phối release** vì sẽ tự deadlock; dùng thread test riêng hoặc thiết kế barrier đúng.

Với class-based proxy, test public final method cần phân biệt **không bị intercept** khỏi “đã chạy nhanh nên nhìn như đồng bộ”. Một method chạy rất nhanh có thể hoàn tất tức thì ngay cả khi được dispatch đúng. Thử so sánh thread identity như ví dụ đầy đủ sẽ mạnh hơn.

## 17. Debug từng bước khi @Async không chạy trên thread khác

Dùng một quy trình điều tra ngắn, theo thứ tự có thể xác minh:

**Bước 1 — Xác nhận điểm gọi.** Code gọi bean lấy từ Spring (injected hoặc context.getBean) hay dùng **new**?

**Bước 2 — Kiểm tra Spring có bật async chưa.** Có @EnableAsync, cấu hình tương đương hoặc cơ chế framework bật async thực tế?

**Bước 3 — Xác định method được annotate.** Annotation đặt trên **public entry point** thích hợp hay bị đặt ở private helper/final method?

**Bước 4 — Xác định proxy type.** AopUtils cho biết JDK hay CGLIB? Có class/method final xung đột với subclass proxy không?

**Bước 5 — Kiểm tra đường gọi.** Caller gọi qua proxy từ bean khác hay **this.method()**? Có method A gọi method B trong cùng target rồi kỳ vọng B được intercept không?

**Bước 6 — Kiểm tra executor.** Bean name trong @Async("...") có tồn tại, đúng loại Executor, đang chạy và có capacity nhận task không?

**Bước 7 — Kiểm tra kết quả.** Đo thread identity trong body, future completion và exception path. Đừng chỉ nhìn “method trả về quá nhanh/chậm”.

**Bước 8 — Phân biệt lỗi khác.** Worker có thể chạy đúng thread nhưng mất MDC, request context, security context hoặc transaction; đó là vấn đề propagation/ownership, không phải bản thân proxy.

**Bước 9 — Đưa test vào CI.** Một integration test nhỏ xác nhận proxy type và thread boundary giúp phát hiện thay đổi do refactor, việc thêm final, đổi interface, cấu hình Boot hoặc nâng version Spring.

## 18. Những nhầm lẫn thường gặp

**“Cứ thấy @Async là Java tạo luồng.”** Sai. @Async cần Spring interception và executor; annotation không biến bytecode Java thành lệnh tạo thread trong chế độ proxy.

**“Method private có @Async sẽ chạy background khi được public method gọi.”** Sai. Private method không được proxy intercept riêng. Nếu public method được @Async dispatch trước thì private helper có thể chạy **trên worker của public method**, nhưng đó là chuyện khác.

**“final method luôn không thể chặn bởi mọi loại proxy.”** Sai. CGLIB không override được final; JDK interface proxy vẫn có thể chặn **interface call** tới target có method final.

**“final class chắc chắn không bao giờ dùng được AOP.”** Không chính xác. Không thể CGLIB subclass final class, nhưng JDK interface proxy có thể bao một final implementation triển khai interface.

**“Có interface thì chắc chắn Spring đang dùng JDK proxy.”** Không thể khẳng định. Spring Boot hoặc cấu hình global có thể ép class-based proxy; xác nhận runtime.

**“@Async trên method public non-final chắc chắn chạy khác thread.”** Chưa chắc. Self-invocation, gọi instance new, chưa bật @EnableAsync, rejection, cấu hình executor và caller-runs policy đều ảnh hưởng.

**“this.async() vẫn chạy qua proxy vì this là bean Spring.”** Sai trong proxy-based AOP: lời gọi nội bộ trực tiếp trên target không qua wrapper proxy.

**“Gọi method proxy rồi immediately future.join() vẫn hoàn toàn nonblocking.”** Sai. join() có thể chặn caller đến khi xong.

**“Một private helper chạy trong worker có nghĩa @Async trên helper đã được intercept.”** Sai. Nó được gọi bởi body public method đã được dispatch.

**“Cứ bật proxyTargetClass=true là sửa được private/final.”** Sai. Đó chính là mô hình subclass nên càng chịu ràng buộc override, không vượt qua private/final.

**“@Transactional và @Async có cùng quy tắc visibility trong mọi phiên bản.”** Sai. Cần phân biệt từng annotation/subsystem, nhất là protected/package-private.

**“Đổi AdviceMode sang ASPECTJ là đủ.”** Sai. Cần dependency, weaving thực tế và test.

**“Future trả đúng giá trị nghĩa là đã chạy bất đồng bộ.”** Sai. Một completedFuture tạo trực tiếp trong caller vẫn trả kết quả giống hệt.

## 19. Bài tập suy luận

**Câu 1.** Một method có @Async, được khai báo private; một public method trong cùng class gọi nó. Trong chế độ proxy, private method có bị Spring intercept không?

**Đáp án:** Không. Không có điểm vào proxy có thể intercept private method. Nó chạy trong thread của public caller trừ khi public caller đã được chuyển thread bởi cơ chế khác.

**Câu 2.** Public @Async method không final nhưng được gọi bằng this.method() từ cùng bean. Có chắc chạy trên executor không?

**Đáp án:** Không. Self-invocation bỏ qua proxy trong AdviceMode.PROXY. Chuyển điểm gọi sang bean khác dùng Spring-managed proxy.

**Câu 3.** Một public final method trong bean được tạo class-based proxy. Có thể CGLIB override method để advice không?

**Đáp án:** Không. final cấm override. Cần đổi cấu trúc hoặc sử dụng cơ chế khác đáp ứng yêu cầu.

**Câu 4.** Một final class implements TaskPort, method perform() final. JDK dynamic proxy có thể intercept lời gọi TaskPort.perform() không?

**Đáp án:** Có thể. JDK proxy implements interface và chặn lời gọi trước khi delegate tới target; không cần subclass final class. Phải bảo đảm Spring advisor khớp và app dùng interface proxy thật.

**Câu 5.** Bean có field **private final TaskRepository repo**. Điều đó có ngăn public non-final @Async method được proxy không?

**Đáp án:** Không. Field final khác method/class final; field injection/cấu trúc dữ liệu của bean và khả năng override method là hai chuyện khác nhau.

**Câu 6.** Một public @Async method chạy trên worker, bên trong gọi private validate(). validate có chạy trên worker không?

**Đáp án:** Có, vì đó là một lời gọi nội bộ xảy ra **sau khi** public entry point đã được dispatch. Nhưng validate không được Spring intercept độc lập.

**Câu 7.** App Spring Boot được cấu hình dùng class-based proxy. Việc service có interface có chứng minh final method được chặn không?

**Đáp án:** Không. Nếu runtime thực tế dùng CGLIB, final method vẫn không thể override. Kiểm tra AopUtils hoặc bean proxy type.

**Câu 8.** Trong Java 17 JDK Proxy ví dụ, vì sao proxy.outer() không tăng bộ đếm advice của run()?

**Đáp án:** Invocation handler thấy **outer**, nhưng target.outer() gọi **this.run()** trực tiếp trên target. Lời gọi run không quay về proxy.

**Câu 9.** Nếu chuyển sang CompletableFuture.supplyAsync trong private method, task có thể chạy async không?

**Đáp án:** Có thể, vì **supplyAsync** tự submit tới executor. Đây không phải tác dụng của @Async proxy trên private method.

**Câu 10.** Một phép thử chỉ gọi method và so sánh Future.get() với chuỗi expected có đủ xác nhận @Async không?

**Đáp án:** Không. Nó chỉ xác nhận kết quả. Cần quan sát đường gọi, proxy, thread identity hoặc tín hiệu dispatch.

## 20. Checklist trước khi phát hành service dùng @Async

- [ ] Method async entry point được đặt ở **bean Spring-managed**, không phải instance new.
- [ ] Async support được bật trong đúng ApplicationContext.
- [ ] Lời gọi vào method async **đi qua proxy**; tránh self-invocation.
- [ ] Với class-based proxy, **bean class không final** và **method cần chặn không private/final**.
- [ ] Nếu dùng JDK proxy, inject bằng **interface** và xác nhận method được giao diện công khai.
- [ ] Không đánh đồng field final với method final.
- [ ] Kiểm tra proxy type bằng **AopUtils** thay vì suy đoán default.
- [ ] Chọn executor name rõ ràng, xác nhận capacity, rejection và shutdown policy.
- [ ] Dùng Future/CompletableFuture cho tác vụ cần kết quả và xử lý exceptional completion.
- [ ] Chụp input thích hợp trước khi qua thread boundary; không chia sẻ mutable state của singleton bean giữa request.
- [ ] Kiểm tra transaction/context propagation tại worker nếu nghiệp vụ cần.
- [ ] Viết integration test chứng minh method thật sự chạy trên thread của executor; không chỉ assert giá trị return.
- [ ] Nếu sử dụng AspectJ weaving, kiểm chứng dependency, cấu hình weaving và kết quả trong môi trường chạy.

## Kết luận

**Khả năng chặn lời gọi của Spring phụ thuộc vào *đường gọi và kiểu proxy*, không phụ thuộc vào việc source chỉ có một annotation.** Trong chế độ @Async dựa trên proxy, private method không phải điểm chặn thích hợp, final method không thể được CGLIB override, final class không thể được CGLIB subclass, và lời gọi nội bộ this.method() bỏ qua proxy dù method public. **JDK interface proxy có điểm mạnh riêng:** có thể intercept lời gọi qua interface ngay cả khi implementation class hoặc method là final, với điều kiện Spring thực sự dùng interface proxy và advice nhận diện đúng.

Thiết kế dễ bảo trì nhất thường là **public non-final @Async method trên một worker bean không final, được bean khác gọi qua dependency do Spring inject**. Luôn kiểm tra runtime proxy type, kết quả thực thi trên thread nào, overload của executor và hợp đồng Future để tránh “async giả” — phương thức trả kết quả đúng nhưng thực tế chạy đồng bộ.

**Tài liệu tham khảo:** Spring Framework Reference — [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html), [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html); Javadoc của **@EnableAsync**, **@Async**, **AopUtils**, **ThreadPoolTaskExecutor**; Java 17 Javadoc của **java.lang.reflect.Proxy** và **java.lang.reflect.InvocationHandler**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-invocation-006">Tách phương thức bất đồng bộ sang bean cộng tác</a>

<details>
<summary>Xem chi tiết</summary>

# Tách phương thức bất đồng bộ sang bean cộng tác

## 1. Mục tiêu và vấn đề

Trong Spring Framework, `@Async` cho phép một phương thức được thực thi thông qua `TaskExecutor` mà caller không phải tự tạo `Thread`. Tuy nhiên, với **chế độ proxy mặc định**, annotation được áp dụng khi lời gọi **đi qua Spring proxy**. Nếu một method trên bean gọi method `@Async` khác bằng `this.method()`, đó là lời gọi nội bộ trên đối tượng đích và thường **không được proxy chặn**. Kết quả là code chạy đồng bộ trên thread gọi, trái với mong đợi.

**Tách phương thức bất đồng bộ sang một bean cộng tác** là cách thiết kế đơn giản, rõ trách nhiệm và thường được khuyến nghị: bean thứ nhất điều phối nghiệp vụ; bean thứ hai sở hữu method `@Async` và được inject vào bean điều phối. Lời gọi giữa hai bean đi qua reference do Spring cấp, để interceptor có cơ hội chuyển công việc sang executor.

~~~text
Trước — self-invocation:

Caller -> OrderService.start()
                   |
                   +--> this.sendEmailAsync()
                           ^ 
                           | Không đi qua Spring proxy
                           v
                       Chạy trực tiếp trên thread hiện tại


Sau — tách bean cộng tác:

Caller -> OrderService.start()
                   |
                   +--> EmailWorker proxy.sendAsync()
                               |
                               +--> @Async interceptor
                                         |
                                         +--> TaskExecutor
                                                   |
                                                   +--> worker thread
                                                            |
                                                            +--> EmailWorker target
~~~

**Hai điều kiện cùng cần:** bean thực thi phải được **Spring IoC container quản lý**, và method `@Async` phải được gọi qua cơ chế interception thích hợp (trong proxy mode là **gọi qua proxy**). Chỉ tách class mà vẫn dùng `new EmailWorker()` thì không giải quyết được vấn đề.

## 2. Vì sao gọi nội bộ làm @Async không có hiệu lực?

Spring hỗ trợ xử lý `@Async` qua `@EnableAsync` và hạ tầng AOP. Trong `AdviceMode.PROXY` mặc định, một object được dùng bởi caller có thể là proxy. Proxy kiểm tra những lời gọi đi qua nó, gửi method `@Async` đến executor, rồi thực thi method thật trên worker.

Nhưng bên trong method của **target bean**, biểu thức `this` tham chiếu chính target. Lời gọi `this.asyncMethod()` **không trở lại proxy** để đi qua interceptor.

~~~java
@Service
public class OrderService {
    public void checkout(Order order) {
        saveOrder(order);
        this.sendEmailAsync(order.id()); // KHÔNG kích hoạt @Async qua proxy
    }

    @Async
    public void sendEmailAsync(long orderId) {
        sendEmail(orderId);
    }
}
~~~

Đây là **đoạn code minh họa**, không phải ứng dụng độc lập: các kiểu `Order`, `saveOrder`, `sendEmail` và import chưa được khai báo. Kể cả khi `OrderService` là singleton được container tạo và đã có `@EnableAsync`, dòng `this.sendEmailAsync(...)` vẫn là lời gọi Java nội bộ thông thường. Nó chạy trên thread hiện tại.

Điểm dễ nhầm: gọi `checkout()` **thông qua proxy** không làm **mọi lời gọi nội bộ** bên trong `checkout()` tự động được proxy tiếp tục intercept. Tại ranh giới target, `this` không phải cùng proxy bên ngoài theo cách Spring proxy AOP mặc định vận hành.

### 2.1. Các điều kiện không thể thay thế lẫn nhau

| Điều kiện | Vì sao cần? | Nếu thiếu? |
| --- | --- | --- |
| `@EnableAsync` hoặc hạ tầng async tương ứng | Cho phép Spring đăng ký xử lý annotation | Annotation có thể không tạo async interceptor |
| Worker là bean do container quản lý | Bean được đi qua các bước post-processing/proxy | Object tự tạo bằng `new` không được Spring tự động proxy |
| Method có thể được intercept | Ví dụ public instance method, không final, với kiểu proxy phù hợp | Private/final/static method không thể được proxy can thiệp như kỳ vọng |
| Caller dùng reference được inject từ Spring | Lời gọi đi qua proxy | `this.method()` hoặc `new Worker().method()` bỏ qua proxy |
| Executor đúng tên/kiểu và còn khả năng nhận task | Dispatcher có tài nguyên để gửi task | Có thể bị từ chối hoặc chạy theo chính sách khác |

Một method trả về `CompletableFuture` **không chứng minh** rằng nó đang chạy bất đồng bộ: ngay cả lời gọi Java đồng bộ cũng có thể trả `CompletableFuture.completedFuture(value)`. Phải quan sát ranh giới lời gọi và executor.

## 3. Thiết kế trước – sau: tách coordinator và worker

### 3.1. Trước: một service làm cả điều phối lẫn tác vụ nền

~~~java
@Service
public class ReportService {

    public void requestReport(long reportId) {
        markRequested(reportId);
        generateAsync(reportId); // lời gọi nội bộ
    }

    @Async("reportExecutor")
    public CompletableFuture<String> generateAsync(long id) {
        return CompletableFuture.completedFuture(renderReport(id));
    }
}
~~~

Đoạn trên để giải thích anti-pattern; `markRequested`, `renderReport`, import và executor cần được định nghĩa thêm. `generateAsync(reportId)` **không** đi qua Spring proxy, vì vậy `renderReport` vẫn chạy trên caller thread. Nếu `renderReport` chặn I/O, HTTP request hoặc thread điều phối tiếp tục bị giữ chờ.

Kể cả đổi thành `this.generateAsync(reportId)` hoặc thêm `@Async` vào chính method `requestReport`, cũng chưa giải quyết đúng vấn đề về ranh giới, lifetime và hợp đồng nghiệp vụ. Đánh dấu coordinator là async có thể chỉ chuyển cả workflow sang thread khác, không tạo ra ranh giới rõ giữa lưu trạng thái và dựng báo cáo.

### 3.2. Sau: coordinator chỉ điều phối, worker thực thi async

~~~java
@Service
public class ReportWorker {

    @Async("reportExecutor")
    public CompletableFuture<String> generateAsync(long reportId) {
        String output = renderReport(reportId);
        return CompletableFuture.completedFuture(output);
    }

    private String renderReport(long id) {
        return "report-" + id;
    }
}

@Service
public class ReportCoordinator {
    private final ReportWorker worker;

    public ReportCoordinator(ReportWorker worker) {
        this.worker = worker;
    }

    public CompletableFuture<String> requestReport(long id) {
        return worker.generateAsync(id); // đi qua bean được inject
    }
}
~~~

Đây là các class minh họa trong project Spring có component scan, import, `@EnableAsync` và bean `reportExecutor`. **Tách đúng** không phải vì class tên Worker, mà vì `ReportCoordinator` nhận **Spring-managed reference** của `ReportWorker`. Khi `generateAsync` được gọi từ coordinator, Spring proxy có thể gửi method sang executor.

Bảng trách nhiệm:

| Thành phần | Trách nhiệm | Thường chạy ở đâu? |
| --- | --- | --- |
| Controller / consumer | Nhận yêu cầu, validate input, chọn giao thức trả kết quả | HTTP/request/message thread |
| `ReportCoordinator` | Điều phối use case, tạo job ID, kiểm tra quyền, lưu trạng thái yêu cầu | Caller thread (trừ khi chủ động cấu hình khác) |
| `ReportWorker` | Thực thi tác vụ nặng, I/O hoặc CPU theo hợp đồng | Executor thread khi gọi qua `@Async` proxy |
| `TaskExecutor` | Cấp worker, queue, giới hạn độ đồng thời và xử lý quá tải | Hạ tầng thực thi |
| Job store / outbox nếu cần | Lưu việc và trạng thái bền vững, hỗ trợ restart/retry | Hệ lưu trữ bên ngoài process |

Cấu trúc hai bean thường giúp test độc lập, chọn pool riêng và thể hiện rõ chỗ code chuyển sang xử lý nền.

## 4. Các bước triển khai đúng trong ứng dụng Spring

### Bước 1 — bật cơ chế @Async trong ApplicationContext

~~~java
@Configuration
@EnableAsync
public class AsyncConfiguration {
    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("report-worker-");
        return executor;
    }
}
~~~

Đây là **mảnh cấu hình** (cần import từ Spring). `@EnableAsync` bật xử lý asynchronous method execution trong context tương ứng. `ThreadPoolTaskExecutor` là bean do Spring quản lý; Spring sẽ khởi tạo và đóng nó theo lifecycle thích hợp.

Không nên tự tạo `new ThreadPoolTaskExecutor()` trong mỗi request: ngoài việc khó quản lý tài nguyên, bạn còn phải tự initialize/shutdown, và method `@Async` không tự chọn executor mới ấy chỉ vì nó tồn tại.

### Bước 2 — worker là bean của Spring

Worker có thể được tạo bằng `@Service`/`@Component` và component scan, hoặc qua factory method `@Bean`:

~~~java
@Bean
public ReportWorker reportWorker() {
    return new ReportWorker();
}
~~~

Có từ khóa `new` **bên trong factory method `@Bean` không phải lỗi**: object trả về sẽ được container đăng ký và post-process. Lỗi là việc caller tự giữ một object khác được tạo ngoài container, như `new ReportWorker().generateAsync(...)`.

### Bước 3 — inject worker qua constructor, không tạo thủ công

~~~java
public ReportCoordinator(ReportWorker worker) {
    this.worker = worker;
}
~~~

Với constructor injection, dependency được resolve từ Spring context. Trong chế độ proxy, Spring có thể inject proxy tương ứng thay vì target trực tiếp. Điều này giúp ranh giới AOP minh bạch hơn so với tự lấy bean từ `ApplicationContext` trong code nghiệp vụ.

### Bước 4 — gọi public @Async method qua reference cộng tác

~~~java
return worker.generateAsync(reportId);
~~~

Method nên được gọi từ bean **khác**, không phải `this.generateAsync(...)`. Dùng `@Async("reportExecutor")` để chỉ định pool rõ ràng khi có nhiều executor. Không suy ra việc khai báo bean tên `reportExecutor` khiến mọi `@Async` tự chọn nó nếu không có qualifier/quy tắc resolution phù hợp.

### Bước 5 — quyết định hợp đồng trả về và xử lý lỗi

Đối với nghiệp vụ cần kết quả, dùng `CompletableFuture<T>`/`Future<T>` phù hợp. Với fire-and-forget `void`, caller không có Future để quan sát lỗi; cần `AsyncUncaughtExceptionHandler`, logging và cơ chế giám sát phù hợp. `@Async` không cung cấp hàng đợi bền vững hay bảo đảm exactly-once.

Không gọi `future.join()` hoặc `future.get()` ngay sau dispatch nếu mục tiêu là giải phóng thread gọi. Khi thật sự phải đợi kết quả, việc đợi là hợp lệ nhưng cần timeout/cancellation và UX phù hợp.

## 5. Ví dụ hoàn chỉnh, chạy được bằng Java 17 và Spring Framework

Demo cho thấy **cùng một Spring context** có cả anti-pattern self-invocation và cách tách bean đúng. Chúng ta không dựa vào `Thread.sleep()` hoặc thứ tự scheduling ngẫu nhiên để kết luận, mà kiểm tra tên thread và proxy sau khi `Future` hoàn thành.

Sử dụng **JDK 17+, Gradle 8.5+ và Spring Framework 6.1.14**. Demo là ứng dụng command-line, không cần Spring Boot hoặc HTTP server.

### 5.1. Cấu trúc project

~~~text
async-collaborator-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/AsyncCollaboratorDemo.java
~~~

**settings.gradle**

~~~groovy
rootProject.name = 'async-collaborator-demo'
~~~

**build.gradle**

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
    mainClass = 'demo.AsyncCollaboratorDemo'
}
~~~

**src/main/java/demo/AsyncCollaboratorDemo.java**

~~~java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public final class AsyncCollaboratorDemo {

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            String callerThread = Thread.currentThread().getName();

            ReportWorker worker = context.getBean(ReportWorker.class);
            ReportCoordinator coordinator =
                    context.getBean(ReportCoordinator.class);
            LegacyReportService legacy =
                    context.getBean(LegacyReportService.class);

            System.out.println("worker is Spring proxy: " +
                    AopUtils.isAopProxy(worker));

            // Anti-pattern: managed bean nhưng tự gọi method @Async.
            String selfName = legacy.startBySelfCall()
                    .get(5, TimeUnit.SECONDS);
            System.out.println("self-call runs on caller: " +
                    selfName.equals(callerThread));

            // Cách đúng: coordinator gọi method của worker đã inject.
            WorkResult result = coordinator.start("R-42")
                    .get(5, TimeUnit.SECONDS);
            System.out.println("collaborator uses executor: " +
                    result.threadName().startsWith("report-worker-"));
            System.out.println("report ID preserved: " +
                    result.reportId().equals("R-42"));

            // Object tự new không được Spring đăng ký/proxy.
            String manualThread = new ReportWorker()
                    .generateAsync("R-43")
                    .get(5, TimeUnit.SECONDS).threadName();
            System.out.println("manual new runs on caller: " +
                    manualThread.equals(callerThread));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class Config {

        @Bean("reportExecutor")
        public ThreadPoolTaskExecutor reportExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(8);
            executor.setThreadNamePrefix("report-worker-");
            return executor;
        }

        @Bean
        public LegacyReportService legacyReportService() {
            return new LegacyReportService();
        }

        @Bean
        public ReportWorker reportWorker() {
            return new ReportWorker();
        }

        @Bean
        public ReportCoordinator reportCoordinator(ReportWorker worker) {
            return new ReportCoordinator(worker);
        }
    }

    public record WorkResult(String reportId, String threadName) { }

    public static class LegacyReportService {

        public CompletableFuture<String> startBySelfCall() {
            return this.buildAsync();
        }

        @Async("reportExecutor")
        public CompletableFuture<String> buildAsync() {
            return CompletableFuture.completedFuture(
                    Thread.currentThread().getName());
        }
    }

    public static class ReportWorker {

        @Async("reportExecutor")
        public CompletableFuture<WorkResult> generateAsync(
                String reportId) {

            WorkResult result = new WorkResult(
                    reportId, Thread.currentThread().getName());
            return CompletableFuture.completedFuture(result);
        }
    }

    public static class ReportCoordinator {
        private final ReportWorker worker;

        public ReportCoordinator(ReportWorker worker) {
            this.worker = worker;
        }

        public CompletableFuture<WorkResult> start(String reportId) {
            return worker.generateAsync(reportId);
        }
    }
}
~~~

### 5.2. Chạy chương trình

Tại thư mục gốc của project, khi đã có Java 17+ và Gradle:

~~~bash
gradle run
~~~

Nếu dự án có Gradle Wrapper, dùng `./gradlew run` (Linux/macOS) hoặc `gradlew.bat run` (Windows).

Với executor hoạt động bình thường và task hoàn tất trong giới hạn `get(5, TimeUnit.SECONDS)`, **stdout do chương trình tự in chính xác**:

~~~text
worker is Spring proxy: true
self-call runs on caller: true
collaborator uses executor: true
report ID preserved: true
manual new runs on caller: true
~~~

Gradle/Spring có thể thêm log khởi động hoặc thông báo build ngoài năm dòng trên. Tên thread thực tế có thể là `report-worker-1`, nhưng ví dụ kiểm tra **prefix** nên không phụ thuộc hậu tố/số lượng worker.

### 5.3. Vì sao kết quả đúng?

1. `@EnableAsync` được xử lý trong cùng `AnnotationConfigApplicationContext` chứa các bean.
2. `ReportWorker` được tạo qua `@Bean` và đi qua Spring bean post-processing; `AopUtils.isAopProxy(worker)` trả về `true` trong cấu hình mặc định này (class không khai báo interface và method đủ điều kiện proxy).
3. `LegacyReportService` cũng là managed bean, nhưng `startBySelfCall()` gọi `this.buildAsync()` **bên trong target**. Method đánh dấu `@Async` không được proxy chặn; giá trị tên thread bằng caller.
4. `ReportCoordinator` nhận `ReportWorker` qua tham số `@Bean`. Spring inject reference đã qua xử lý; `worker.generateAsync(...)` **đi qua proxy**, rồi `TaskExecutor` chạy code trên worker có prefix `report-worker-`.
5. `new ReportWorker()` tạo object ngoài context; lời gọi trả một CompletableFuture đã hoàn tất nhưng **vẫn chạy đồng bộ** trên caller.
6. Context được đóng bằng `try-with-resources` sau khi các Future đã hoàn tất, tránh để thread pool tồn tại ngoài vòng đời demo.

Đây là **chương trình hoàn chỉnh**; các ví dụ rời ở phần trước cần được đặt vào project Spring có import và cấu hình phù hợp.

## 6. Thiết kế API của hai bean để không che mất lỗi

### 6.1. Giữ ranh giới bất đồng bộ trong API worker

Một phương pháp thực tế là để worker trả `CompletableFuture<ReportResult>` còn coordinator trả lại future đó hoặc transform nó:

~~~java
public CompletableFuture<ReportResult> requestReport(ReportRequest request) {
    validate(request);
    return worker.generateAsync(request)
            .thenApply(this::toResponseModel);
}
~~~

Đây là **mảnh code minh họa**: `ReportResult`, `ReportRequest`, `validate`, `toResponseModel` chưa được định nghĩa. Trong `CompletableFuture`, `thenApply` không có hậu tố `Async` có thể chạy trên thread hoàn thành stage trước hoặc thread caller nếu stage đã hoàn tất. Nếu việc biến đổi nặng, xem xét `thenApplyAsync(..., chosenExecutor)` với executor được chọn rõ ràng.

Nếu coordinator chỉ cần xác nhận yêu cầu đã được tiếp nhận và không chờ kết quả, cần thiết kế **job ID và trạng thái job** riêng, không trả về lời hứa mơ hồ rằng job chắc chắn hoàn tất.

### 6.2. Lỗi trong async worker không giống lỗi synchronous

Với `@Async` trả `CompletableFuture<T>`, lỗi của phần thực thi nên được quan sát qua future:

~~~java
CompletableFuture<ReportResult> future = worker.generateAsync(request);

return future.whenComplete((value, error) -> {
    if (error != null) {
        // Ghi nhận thất bại và cập nhật job status phù hợp.
    }
});
~~~

Đoạn này là minh họa. Đừng coi việc gọi `whenComplete` đã xử lý hoặc phục hồi lỗi: nếu callback không thay đổi trạng thái kết quả, future vẫn hoàn thành lỗi. Có thể dùng `handle`, `exceptionally` hoặc retry có giới hạn khi hợp đồng nghiệp vụ cho phép.

Với `@Async void`, caller không có Future để chờ. Lỗi không tự ném trở lại thread caller vốn đã trả về; cần `AsyncUncaughtExceptionHandler`, logging và theo dõi lỗi.

`@Async` sử dụng executor trong bộ nhớ và có thể gặp **task rejection** khi pool quá tải/shutdown. Không được xem một lời gọi trả bình thường là bảo đảm durability nếu đã chọn rejection handler bỏ việc âm thầm. Với công việc bắt buộc, ưu tiên phản hồi từ chối rõ ràng hoặc durable queue/outbox.

## 7. Transaction: vì sao tách bean còn giúp thấy rõ ranh giới

Trong ứng dụng có `@Transactional`, một hiểu nhầm thường thấy:

~~~java
@Transactional
public void placeOrder(Order order) {
    repository.save(order);
    emailWorker.sendAsync(order.id());
}
~~~

Đây là đoạn minh họa (cần khai báo `Order`, repository và worker). Nếu lời gọi `sendAsync` qua proxy kích hoạt worker khác thread, **transaction gắn với thread của caller không tự đi theo worker**. Worker có thể chạy **trước khi transaction caller commit**. Nếu worker đọc database, nó có thể không thấy record vừa ghi hoặc thấy trạng thái chưa như mong đợi.

Tách worker giúp kiến trúc buộc phải trả lời:

- Worker cần tham số/DTO gì, và có cần đọc lại dữ liệu đã commit từ DB không?
- Khi nào mới được gửi email hoặc phát sự kiện — ngay khi submit hay chỉ sau commit?
- Nếu transaction caller rollback, công việc nền đã gửi phải xử lý thế nào?
- Nếu worker thất bại, ai retry/cập nhật trạng thái? Có cần idempotency không?

Nếu hành động nền chỉ được xảy ra **sau commit**, có thể dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` cho tình huống phù hợp, hoặc **transactional outbox** khi phải có bảo đảm bền vững qua crash/restart. Không chỉ đơn giản thêm `@Async` hoặc `@Transactional` trên cùng method và kỳ vọng công việc tự đợi transaction của caller.

Trong worker, nếu cần transaction riêng, cấu hình ranh giới transaction trên method thực thi với cơ chế Spring phù hợp và lưu ý cách phối hợp nhiều proxy. Một transaction trong worker không phải continuation tự động của transaction caller.

## 8. Dữ liệu, context và tính an toàn luồng

Việc tách bean giải quyết **proxy interception**, không tự giải quyết tất cả bài toán concurrency.

**Không truyền object mutable dùng chung một cách tùy tiện.** Nếu controller nhận request rồi gửi cùng một object có thể bị sửa vào `@Async`, worker có thể đọc giá trị thay đổi giữa chừng. Ưu tiên immutable DTO/record hoặc snapshot dữ liệu tại điểm dispatch; dữ liệu không được thiết kế immutable cần có chính sách ownership rõ ràng.

**Đừng giữ state theo request trong field singleton của worker.** Một `@Service` mặc định là singleton. Nhiều thread pool có thể gọi cùng worker instance đồng thời. Ví dụ `private String currentOrderId` trên singleton có nguy cơ race và lẫn thông tin giữa các tác vụ. Đưa ID vào tham số, local variable hoặc storage đúng scope.

**ThreadLocal không tự truyền sang executor thread.** MDC/tracing, SecurityContext, request attributes và transaction context thường cần cơ chế propagate hoặc truyền dữ liệu rõ ràng. Spring Security có các wrapper phù hợp, còn `TaskDecorator` có thể trang trí Runnable để capture/restore thông tin. Phải cleanup để không rò rỉ context khi pool tái sử dụng thread.

**Cancellation là hợp tác.** `Future.cancel(true)` có thể gửi interrupt khi cấu hình/implementation hỗ trợ, nhưng không ép mọi I/O bên ngoài dừng ngay. Thiết kế timeout và trạng thái job ở tầng nghiệp vụ.

**Thread scheduling không đảm bảo thứ tự.** Nếu coordinator gửi task A rồi task B vào một pool nhiều worker, B có thể hoàn thành trước A. Nếu cần ordering/idempotency, phải thiết kế độc lập.

## 9. Những trường hợp tách bean vẫn chưa đủ

| Trường hợp | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Tách thành hai class nhưng dùng `new ReportWorker()` | Worker không qua container/proxy | Đăng ký bean và inject từ Spring |
| Bean worker có `@Service` nhưng ngoài vùng scan | Bean không được đăng ký | Kiểm tra `@ComponentScan`, cấu hình ứng dụng và profile |
| Có `@Async` nhưng thiếu `@EnableAsync` | Chưa bật infrastructure | Bật async trong ApplicationContext chứa worker |
| Coordinator tự gọi method `@Async` trên chính nó | Self-invocation | Chuyển method sang bean cộng tác khác |
| Gọi private/final/static method qua proxy | Không thỏa khả năng interception | Public, non-final instance method trên bean phù hợp |
| Bean có interface nhưng inject concrete class khi dùng JDK proxy | Proxy có thể chỉ implement interface | Inject qua interface hoặc cấu hình class proxy thích hợp |
| Dùng `@Async` trên method `@Configuration` | Không phải nơi được hỗ trợ như service method | Để `@Configuration` khai báo bean, logic trong service |
| Gọi worker từ `@PostConstruct` của chính worker | Lifecycle/self-call không qua proxy | Điều phối từ bean khác sau startup |
| Đã tách bean nhưng caller gọi `future.get()` ngay | Caller vẫn block đợi kết quả | Trả future hoặc tách job lifecycle |
| Worker chạy rồi nhưng caller thiếu transaction context | ThreadLocal/transaction không tự kế thừa | Xác định ranh giới transaction trong worker hoặc sau commit |
| Worker dùng mutable shared field trên singleton | @Async không cung cấp synchronization | Stateless/immutable hoặc lock/atomic operation phù hợp |
| Tác vụ phải survive restart nhưng chỉ dùng in-memory executor | Task không bền vững | Job store/outbox/message broker |

### 9.1. Vì sao không nên mặc định dùng self-injection?

Một cách thường được nhắc đến là inject chính bean vào một field rồi gọi `selfProxy.asyncMethod()` để đi qua proxy. Cách này có thể cần vòng phụ thuộc/cơ chế lazy injection, khiến code khó hiểu và khó kiểm thử. Nó giấu ý đồ nghiệp vụ trong mẹo AOP thay vì diễn đạt một dependency thực.

Tương tự, `AopContext.currentProxy()` phụ thuộc cấu hình exposeProxy và khiến business logic phụ thuộc hạ tầng Spring AOP. Khi trách nhiệm async thực sự là một công việc riêng, **tách bean cộng tác thường rõ ràng và ít ràng buộc hơn**.

### 9.2. Có phương án không dùng bean thứ hai không?

Có. Ví dụ tự gửi `Runnable`/`Callable` trực tiếp vào `TaskExecutor`, hoặc sử dụng `CompletableFuture.supplyAsync(..., executor)` với executor được inject. Các cách này **không phụ thuộc interception của `@Async`**, nhưng caller phải tự quản lý cách gửi, xử lý lỗi và phối hợp kết quả.

Có thể dùng `@EnableAsync(mode = AdviceMode.ASPECTJ)` và weaving được thiết lập đúng để can thiệp cả self-invocation. Tuy nhiên đây là một chế độ triển khai khác, cần dependency/cấu hình riêng; không phải lựa chọn mặc định cho một ứng dụng chỉ cần tách coordinator và worker.

Không nên tách vô số class chỉ để tránh một `this`: kiến trúc cần có ranh giới trách nhiệm có ý nghĩa. Với service nhỏ, chọn cách direct executor khi phù hợp cũng là thiết kế hợp lệ.

## 10. Kiểm thử đúng ranh giới proxy

Một unit test gọi `new ReportWorker()` **không thể xác nhận** `@Async` hoạt động. Nó chỉ kiểm tra thân method Java. Muốn kiểm tra interception, test cần **ApplicationContext** có `@EnableAsync`, executor và bean thật.

Kịch bản test gợi ý:

1. Lấy worker qua context, dùng `AopUtils.isAopProxy` như một tín hiệu bổ trợ.
2. Gọi method `@Async` từ **bean khác**, nhận `Future` và đợi kết quả có timeout.
3. Kiểm tra worker nhìn thấy thread name có prefix cấu hình; không phụ thuộc số thứ tự cụ thể của thread.
4. Kiểm tra `this.method()` trong phiên bản anti-pattern vẫn chạy trên caller để chứng minh test có giá trị phân biệt.
5. Test exception và rejection; timeout không được xem là kết quả thành công.
6. Test dữ liệu truyền qua worker và tính idempotency khi có retry.

**Tránh test chỉ dựa trên tốc độ** như “method trả về dưới 100ms là async”. Scheduling có thể làm test không ổn định. Kiểm tra đúng executor/proxy và kết quả cụ thể đáng tin hơn.

Trong production, thêm metrics cho active threads, queue depth, rejected tasks, thời gian task chờ và thời gian xử lý. Tên pool riêng (`report-worker-`) giúp đọc log/thread dump, nhưng không thay thế trace ID hoặc correlation ID.

## 11. Checklist khi refactor một method @Async đang self-call

- [ ] Xác định chính xác method nào gọi `this.asyncMethod()` và kỳ vọng async hiện tại.
- [ ] Chỉ tách phần công việc nền có ý nghĩa sang bean `@Service`/`@Component` hoặc `@Bean` riêng.
- [ ] Bật `@EnableAsync` trong context chứa worker.
- [ ] Inject worker vào coordinator bằng constructor; **không tự `new`** worker từ coordinator.
- [ ] Đặt `@Async("executorName")` trên public non-final method của worker khi dùng proxy mode.
- [ ] Cấp executor đúng tên, capacity và rejection policy; tránh queue không kiểm soát.
- [ ] Thiết kế kiểu trả về `void`/`Future`/`CompletableFuture` và cách caller quan sát lỗi.
- [ ] Không dùng `get()`/`join()` ngay sau dispatch khi cần non-blocking caller.
- [ ] Đánh giá transaction và thời điểm commit; dùng after-commit/outbox nếu cần.
- [ ] Truyền immutable request data hoặc DTO, không ghi state của request vào singleton field.
- [ ] Xác minh bằng integration test có Spring context, proxy và worker thread đúng.
- [ ] Có logging/metrics và chiến lược shutdown, timeout, retry, idempotency nếu nghiệp vụ cần.

## 12. Câu hỏi tự kiểm tra

1. **Vì sao `this.generateAsync()` không chạy nền dù bean có `@Service`?** Trong proxy mode, self-invocation gọi target trực tiếp, bỏ qua async interceptor.
2. **Tách phương thức sang class mới rồi `new` class ấy có sửa được không?** Không; worker phải được Spring quản lý và gọi qua proxy.
3. **Một phương thức `@Bean` trả về `new ReportWorker()` có làm worker unmanaged không?** Không. Object trả về được đăng ký thành bean và đi qua bean post-processing phù hợp.
4. **Vai trò khác nhau giữa coordinator và worker là gì?** Coordinator nhận/điều phối use case; worker thực hiện tác vụ cần chạy bằng executor.
5. **Tại sao `CompletableFuture.completedFuture(...)` không chứng minh đang async?** Nó cũng có thể tạo và hoàn thành ngay trên caller thread.
6. **Khi controller gọi worker rồi ngay lập tức `future.get()` thì sao?** Caller vẫn chặn chờ; không đạt mục tiêu giảm thời gian giữ request thread.
7. **Tách bean có tự chuyển transaction của caller sang worker không?** Không; transaction thường gắn với thread, cần ranh giới riêng và chính sách sau commit.
8. **Tại sao không nên dùng shared mutable field trong singleton worker?** Nhiều worker threads có thể cùng thao tác và gây race condition.
9. **Có cách nào tránh tách bean không?** Có: gửi việc trực tiếp vào executor hoặc dùng AspectJ weaving phù hợp, nhưng hợp đồng và độ phức tạp khác.
10. **Làm sao test chắc `@Async` hoạt động?** Gọi qua Spring-managed collaborator trong context đã bật async và kiểm tra thread thực thi/kết quả có timeout.
11. **Có một worker bean đã qua proxy nhưng method được gọi từ constructor/`@PostConstruct` của chính worker thì sao?** Không nên mong proxy interception cho lời gọi nội bộ trong lifecycle; điều phối sau khởi tạo qua bean khác.
12. **Nếu job bắt buộc xử lý qua restart, chỉ tách bean và gắn `@Async` đã đủ chưa?** Chưa. Cần cách lưu công việc bền vững như outbox hoặc job store, cùng retry và idempotency.

**Kết luận:** Tách phương thức bất đồng bộ sang **bean cộng tác được Spring quản lý và inject** giúp chuyển lời gọi từ **self-invocation** thành **cross-bean proxy invocation**. Đây là cách rõ ràng để `@Async` phát huy tác dụng trong proxy mode; còn an toàn luồng, transaction, độ bền công việc, lỗi và kiểm soát quá tải vẫn phải được thiết kế riêng.

### Tài liệu tham khảo chính thức

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — @Async](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/annotation/Async.html)
- [Spring Framework — @EnableAsync](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Spring Framework — Proxying Mechanisms and Self-Invocation](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — Bean Definitions](https://docs.spring.io/spring-framework/reference/core/beans/definition.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-invocation-007">Không trông cậy @Async trên callback @PostConstruct của chính bean</a>

<details>
<summary>Xem chi tiết</summary>

# Không trông cậy @Async trên callback @PostConstruct của chính bean

## 1. Bài học cốt lõi

Trong Spring Framework, `@PostConstruct` là **callback vòng đời bean**: container gọi phương thức này trong quá trình khởi tạo bean. `@Async` là **cơ chế interceptor**: trong chế độ mặc định dựa trên proxy, một lời gọi từ bên ngoài phải đi qua Spring proxy thì nhiệm vụ mới được gửi đến executor.

Hai cơ chế xảy ra ở **những thời điểm khác nhau**. Vì vậy, **không đặt `@Async` trên chính phương thức `@PostConstruct` rồi mong nó tự chạy nền**. Cũng không mong việc gọi `this.loadData()` trong `@PostConstruct` sẽ kích hoạt `@Async` trên `loadData()` của cùng bean.

Spring Framework nêu rõ: `@Async` không được hỗ trợ kết hợp với lifecycle callback như `@PostConstruct`; để khởi tạo bất đồng bộ, hãy dùng một **bean khởi phát riêng** gọi phương thức `@Async` trên bean đích. Với tác vụ nặng, cách đơn giản và an toàn hơn trong Spring Boot là khởi phát sau khi context đã được khởi tạo, chẳng hạn qua `ApplicationRunner` hoặc sự kiện `ApplicationReadyEvent`, rồi gọi **bean worker khác**.

Ví dụ dễ gặp:

- Ứng dụng khởi động và cần tải dữ liệu tham chiếu từ API.
- Developer viết `@PostConstruct @Async public void warmUp()` và nghĩ startup sẽ trả về ngay.
- Thực tế callback có thể chạy trực tiếp trên thread khởi tạo bean, kéo dài startup; nếu callback ném lỗi, startup có thể thất bại.
- Developer chuyển logic sang `@Async public void warmUp()` nhưng lại gọi `this.warmUp()` ngay từ `@PostConstruct`. Lời gọi vẫn không đi qua proxy, nên vẫn chạy đồng bộ.
- Cách sửa là có **đường gọi qua proxy của bean khác**, từ một thời điểm khởi phát hợp lý, có executor, timeout, xử lý lỗi và chính sách readiness rõ ràng.

Phạm vi: Java 17, Spring Framework 6.x, Spring Boot 3.3.x với `@EnableAsync` mặc định `AdviceMode.PROXY`. Với AspectJ weaving hoặc lifecycle tùy biến, một số chi tiết interceptor khác, nhưng nguyên tắc **không dựa vào `@Async` trên callback khởi tạo** vẫn hữu ích.

## 2. Phân biệt ba đối tượng: target bean, proxy và callback

Spring thường xây dựng một bean theo chuỗi khái quát:

1. Tạo instance của class (constructor).
2. Inject dependencies và thiết lập properties.
3. Chạy các `BeanPostProcessor` trước khởi tạo, trong đó có xử lý `@PostConstruct` do `CommonAnnotationBeanPostProcessor` phụ trách.
4. Chạy callback khởi tạo như `InitializingBean.afterPropertiesSet()` hoặc init method khi được khai báo.
5. Các post-processor sau khởi tạo có thể tạo AOP proxy bao quanh bean.
6. Những bean khác được inject một reference phù hợp, có thể chính là proxy; gọi public method qua reference này mới có cơ hội được async advice chặn.

Đây là sơ đồ **khái niệm**, không phải cam kết mọi extension của mọi Spring version có cùng thứ tự chi tiết nội bộ:

~~~text
Spring container
   |
   +-- new TargetBean()
   +-- inject dependencies
   +-- @PostConstruct chạy trên bean trong pha init
   +-- hoàn tất initialization / tạo proxy khi áp dụng
   |
   +-- expose Spring proxy -----> caller ở bean khác
                                      |
                                      +-- proxy intercept @Async
                                      +-- executor.execute(task)
                                      +-- worker thread chạy target method

Trong TargetBean:
@PostConstruct init() -> this.asyncMethod()
                          |
                          +-- gọi trực tiếp target, KHÔNG đi qua proxy
~~~

**Không có chuyện tự động "nhảy sang thread mới" chỉ vì bytecode method mang `@Async`.** Interceptor cần nhận lời gọi qua đúng proxy. Đây cũng là lý do code tự tạo bằng `new MyService()` hoặc self-invocation `this.method()` thường bỏ qua `@Async`.

**Lưu ý chính xác về `@PostConstruct`:** nó chạy ở giai đoạn bean chưa hoàn tất khởi tạo. Spring có thể tạo proxy cho *bean được expose* sau đó, nhưng callback không phải lời gọi ứng dụng từ ngoài vào proxy đã được dùng ổn định. Không nên dùng callback như một điểm dispatch background work tùy ý, càng không nên cố workaround bằng cách inject chính mình để lấy proxy: self-reference/circular dependency làm startup phức tạp và dễ hỏng.

## 3. Hai anti-pattern thường gặp

### 3.1. Gắn đồng thời @PostConstruct và @Async lên một method

~~~java
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class CacheBootstrap {
    @PostConstruct
    @Async("bootstrapExecutor") // Không làm callback này chạy async.
    public void preload() {
        fetchAllRemoteRecords(); // Có thể giữ thread khởi tạo rất lâu.
    }

    private void fetchAllRemoteRecords() {
        // Đại diện cho I/O chậm, không phải ví dụ chạy độc lập.
    }
}
~~~

Nếu `fetchAllRemoteRecords()` kéo dài 20 giây, thời gian khởi tạo bean có thể tăng tương ứng. `@Async` không biến callback thành một job chạy nền. Trong Spring Boot 3 dùng import `jakarta.annotation.PostConstruct`, **không** dùng `javax.annotation.PostConstruct` của các dự án Spring cũ nếu dependency không tương thích.

### 3.2. @PostConstruct gọi @Async trên cùng bean

~~~java
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class InventoryBootstrap {
    @PostConstruct
    public void init() {
        this.refreshAsync(); // Bỏ qua proxy: gọi trực tiếp trên cùng object.
    }

    @Async("bootstrapExecutor")
    public void refreshAsync() {
        // Ở đây vẫn có thể chạy trên thread của callback init().
    }
}
~~~

Đây là trường hợp **self-invocation**. Kể cả khi bean đã được proxy và gọi từ một method khác của chính target sau khi startup, `this.refreshAsync()` cũng không được proxy mode intercept; ở `@PostConstruct` còn thêm vấn đề thời điểm lifecycle.

Hãy phân biệt với lời gọi hợp lệ:

~~~java
// Trích đoạn minh họa nằm trong bean KHÁC, đã inject Spring proxy:
private final InventoryWorker worker;

public StartupTrigger(InventoryWorker worker) {
    this.worker = worker;
}

public void trigger() {
    worker.refreshAsync(); // Lời gọi tới bean khác qua proxy.
}
~~~

Đoạn trên chưa phải class đầy đủ; class `InventoryWorker` phải là Spring-managed bean và method `refreshAsync()` đủ điều kiện async.

## 4. Ví dụ đầy đủ, chạy được trên Java 17 và Spring Boot 3.3

Ví dụ minh họa **cả lỗi lẫn cách đúng trong một lần chạy**. Nó không dựa vào `Thread.sleep` để chứng minh việc chuyển thread. Executor riêng dùng prefix `init-worker-`; code ghi lại tên thread **ngay trong method thật**, rồi in kết quả boolean xác định.

### 4.1. Cấu trúc dự án

~~~text
lifecycle-async-demo/
├── pom.xml
└── src/main/java/com/example/lifecycle/
    ├── LifecycleAsyncApplication.java
    ├── WrongLifecycleBean.java
    └── WarmupWorker.java
~~~

### 4.2. pom.xml

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
    <artifactId>lifecycle-async-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
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

`spring-boot-starter` cung cấp Boot và các dependency nền; `spring-boot-starter-aop` cho hạ tầng proxy AOP rõ ràng. `jakarta.annotation.PostConstruct` được dùng theo Spring Boot 3. Không cần web server hay database để chạy demo.

### 4.3. LifecycleAsyncApplication.java

~~~java
package com.example.lifecycle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
@EnableAsync
public class LifecycleAsyncApplication {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                     SpringApplication.run(
                             LifecycleAsyncApplication.class, args)) {
            // Đóng context sau khi ApplicationRunner hoàn tất.
        }
    }

    @Bean("initExecutor")
    ThreadPoolTaskExecutor initExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(8);
        pool.setThreadNamePrefix("init-worker-");
        pool.setWaitForTasksToCompleteOnShutdown(true);
        return pool;
    }

    @Bean
    ApplicationRunner afterStartup(WarmupWorker worker) {
        return args -> {
            System.out.println("RUNNER_ON_POOL="
                    + isWorkerThread());

            // Đây là lời gọi TỪ bean/runner khác vào Spring proxy.
            CompletableFuture<String> result = worker.prepare();

            // Runner cố ý chờ để demo có kết quả xác định.
            String actualThread = result.get(5, TimeUnit.SECONDS);
            System.out.println("SEPARATE_BEAN_ON_POOL="
                    + actualThread.startsWith("init-worker-"));
        };
    }

    private static boolean isWorkerThread() {
        return Thread.currentThread().getName().startsWith("init-worker-");
    }
}
~~~

Điểm quan trọng: `afterStartup` là một `ApplicationRunner`. Spring Boot gọi runner **sau khi ApplicationContext đã refresh** và các singleton cần thiết đã được tạo. `worker.prepare()` là lời gọi vào một **Spring-managed worker khác**, qua proxy của `@Async("initExecutor")`.

Runner cố ý dùng `get(5, TimeUnit.SECONDS)` chỉ để đảm bảo stdout demo có thứ tự, có hạn chờ và cho phép chương trình đóng context sạch. Nếu yêu cầu thực sự là “khởi động job nền rồi cho web server nhận request ngay”, **không nên chờ Future ngay trên startup thread**; phải có trạng thái job/readiness và cơ chế theo dõi riêng.

### 4.4. WrongLifecycleBean.java

~~~java
package com.example.lifecycle;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class WrongLifecycleBean {
    @PostConstruct
    @Async("initExecutor")
    public void initialize() {
        System.out.println("POST_CONSTRUCT_ON_POOL="
                + isWorkerThread());

        // Cùng bean, gọi this; @Async của method bên dưới bị bỏ qua.
        this.selfInvokedAsync();
    }

    @Async("initExecutor")
    public void selfInvokedAsync() {
        System.out.println("SELF_CALL_ON_POOL="
                + isWorkerThread());
    }

    private boolean isWorkerThread() {
        return Thread.currentThread().getName().startsWith("init-worker-");
    }
}
~~~

Hai giá trị được in tại chính callback và method gọi nội bộ, không phải kiểm tra một biến giả lập. Spring tạo `WrongLifecycleBean` trong lúc khởi tạo context, vì vậy hai dòng này xuất hiện trước các dòng từ `ApplicationRunner`.

Dù `initialize()` được đánh dấu cả `@PostConstruct` và `@Async`, callback vẫn không chạy qua async advice. Tương tự, `selfInvokedAsync()` không được gửi tới executor vì lời gọi là `this.selfInvokedAsync()`.

### 4.5. WarmupWorker.java

~~~java
package com.example.lifecycle;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class WarmupWorker {
    @Async("initExecutor")
    public CompletableFuture<String> prepare() {
        // Đọc thread name NGAY trong code thực tế của worker.
        String name = Thread.currentThread().getName();
        return CompletableFuture.completedFuture(name);
    }
}
~~~

Chữ ký trả `CompletableFuture<String>` phù hợp với `@Async`. Method implementation trả một completed future để Spring async proxy có thể lấy kết quả của task đã được executor chạy; caller nhận một Future đại diện cho công việc được gửi. Điều đó **không** có nghĩa implementation chạy trên caller: tên thread được lấy bên trong method và kết quả sẽ có prefix `init-worker-`.

### 4.6. Chạy và kết quả mong đợi

Yêu cầu JDK 17+ và Maven. Tại thư mục chứa `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

Bỏ qua log của Spring và Maven, **bốn dòng do chương trình in** theo thứ tự:

~~~text
POST_CONSTRUCT_ON_POOL=false
SELF_CALL_ON_POOL=false
RUNNER_ON_POOL=false
SEPARATE_BEAN_ON_POOL=true
~~~

Vì sao kết quả đáng tin?

1. `WrongLifecycleBean.initialize()` được container gọi trong lúc khởi tạo bean, trên luồng startup chứ không phải luồng của `initExecutor`. Do đó dòng đầu là `false`.
2. `this.selfInvokedAsync()` là lời gọi trực tiếp, không qua proxy, nên dòng thứ hai cũng là `false`.
3. `ApplicationRunner` chạy trên luồng ứng dụng khởi động bình thường, nên dòng thứ ba là `false`.
4. `WarmupWorker.prepare()` được gọi trên proxy của một bean khác sau startup và dùng đúng `initExecutor`; method thật chạy trên luồng có prefix `init-worker-`, cho dòng thứ tư là `true`.

Demo không đòi hỏi đồng thời nhiều worker để chứng minh đường đi proxy. Với `ThreadPoolTaskExecutor`, `corePoolSize=1`, `queueCapacity=8` chỉ mô tả cấu hình của **demo**, không phải mặc định của Spring. Ngoài ba file và `pom.xml` đã liệt kê, không cần các file code bí mật khác. Đây là ví dụ để **hiểu cơ chế**, không phải thiết kế thực hiện batch nặng thực tế.

## 5. Cách sửa ở môi trường thật

### 5.1. Ưu tiên khởi phát bằng ApplicationRunner

Spring Boot có `ApplicationRunner` và `CommandLineRunner` để thực thi code khi context đã được chuẩn bị và khởi động. Tách làm hai bean:

~~~java
// Trích đoạn trong bean khởi phát, không phải chương trình độc lập.
@Component
public class WarmupTrigger implements ApplicationRunner {
    private final CatalogWorker worker;

    public WarmupTrigger(CatalogWorker worker) {
        this.worker = worker;
    }

    @Override
    public void run(ApplicationArguments args) {
        worker.refreshCatalog(); // Caller đi qua proxy của bean KHÁC.
    }
}
~~~

Cần import `org.springframework.stereotype.Component`, `org.springframework.boot.ApplicationRunner` và `org.springframework.boot.ApplicationArguments`. `CatalogWorker` là bean service tách riêng, có public method `@Async("catalogExecutor")`. Đoạn trên không chờ kết quả; để xử lý production cần kiểm soát lỗi, retry/timeout và trạng thái hoàn tất.

Khi cần nhiều runner, dùng `@Order` hoặc `Ordered` để điều khiển thứ tự trong phạm vi runners, nhưng **không** biến mọi task async thành đã hoàn tất theo thứ tự ấy. Nếu runner A gửi job rồi trả về ngay, runner B có thể chạy khi job A vẫn đang xử lý.

### 5.2. Dùng @EventListener(ApplicationReadyEvent.class) khi cần bắt đầu sau runners

Trong Spring Boot, `ApplicationReadyEvent` được phát **sau khi các ApplicationRunner/CommandLineRunner đã được gọi**. Một listener có thể gọi worker khác:

~~~java
// Trích đoạn minh họa trong Spring-managed bean riêng.
@Component
public class ReadyTrigger {
    private final CatalogWorker worker;

    public ReadyTrigger(CatalogWorker worker) {
        this.worker = worker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        worker.refreshCatalog();
    }
}
~~~

Cần import `org.springframework.context.event.EventListener`, `org.springframework.boot.context.event.ApplicationReadyEvent` và `org.springframework.stereotype.Component`. Với event listener thông thường, handler ban đầu chạy trong luồng publish event; trong đoạn trên **chính lời gọi `worker.refreshCatalog()` qua proxy** mới đưa phần nặng sang executor.

**Không nhầm “event ready đã phát” với “dữ liệu warm-up đã sẵn sàng”.** Nếu listener chỉ gửi một việc nền rồi trả ngay, Spring Boot vẫn có thể chuyển readiness sang nhận traffic trước khi công việc xong. Nếu API chỉ hoạt động đúng sau khi warm-up hoàn tất, phải có readiness gate, kiểm soát trạng thái khởi tạo hoặc startup đồng bộ có hạn định. Không dùng một cờ boolean không đồng bộ mà quên memory visibility.

Nếu context refresh lại hoặc ứng dụng có hierarchy nhiều context, phải chú ý sự kiện trùng lặp và idempotency của công việc khởi tạo. Với nhiều pod, mỗi pod có thể nhận sự kiện, nên job dùng chung toàn cụm cần cơ chế coordination.

### 5.3. Dùng bean khởi phát riêng với @PostConstruct — có điều kiện

Tài liệu Spring cũng mô tả mô hình một bean khởi tạo độc lập inject bean đích và gọi method async trong `@PostConstruct`:

~~~java
// Mô hình tối giản theo tài liệu; không phải chương trình Java độc lập.
@Component
public class CatalogInitializer {
    private final CatalogWorker worker;

    public CatalogInitializer(CatalogWorker worker) {
        this.worker = worker;
    }

    @PostConstruct
    public void initialize() {
        worker.refreshCatalog(); // Khác bean, reference có thể là proxy.
    }
}
~~~

So với `this.refreshCatalog()` trên cùng bean, lời gọi trên **có khả năng đi qua proxy của CatalogWorker** nếu bean đích đã được xây dựng và inject đúng. Tuy nhiên, `@PostConstruct` của bean khởi phát vẫn diễn ra trong pha tạo singleton. Nó không bảo đảm mọi dependency trong context đều đã sẵn sàng, có thể tạo vòng phụ thuộc, deadlock hoặc race nếu worker lập tức truy cập các bean/tài nguyên còn đang khởi tạo. Với tác vụ nặng, đa dependency hoặc yêu cầu startup rõ ràng, thường nên chọn runner/sự kiện sau refresh thay vì cố gắng dispatch trong lifecycle callback.

Nếu thật sự dùng biến thể này, hãy giữ initializer **chỉ làm nhiệm vụ dispatch**, tránh chờ `future.get()` bên trong `@PostConstruct` và kiểm thử startup có timeout/circular dependency.

### 5.4. Còn SmartInitializingSingleton hay ContextRefreshedEvent?

`SmartInitializingSingleton.afterSingletonsInstantiated()` chạy sau giai đoạn tạo các singleton không-lazy thông thường, do đó hữu ích nếu công việc cần các singleton sẵn sàng nhưng không cần đợi đến `ApplicationReadyEvent`. `ContextRefreshedEvent` diễn ra sau khi refresh context; listener có thể dispatch tới bean khác.

Hai hook này không thay thế mọi semantics startup: còn lazy beans, nhiều context, cơ chế refresh, trạng thái server/readiness và các task dispatch bất đồng bộ. Chọn hook theo **điều kiện phải hoàn thành trước khi chạy** và mức chấp nhận job nền, không dựa đơn thuần vào tên “after”.

## 6. Nếu công việc là bắt buộc trước khi ứng dụng sẵn sàng

Có hai thiết kế cần tách bạch:

| Mục tiêu | Điểm khởi phát | Điều kiện readiness | Cách xử lý lỗi |
| --- | --- | --- | --- |
| Khởi tạo nhẹ, bắt buộc, nhanh | `@PostConstruct` hoặc init method | Khởi tạo bean chỉ kết thúc sau công việc | Fail startup rõ ràng nếu không hợp lệ |
| Nạp dữ liệu bắt buộc trước khi phục vụ | `ApplicationRunner`, gọi nghiệp vụ (đồng bộ hoặc chờ Future **có timeout**) | Runner chỉ thành công khi việc bắt buộc thành công | Fail startup hoặc readiness REFUSING_TRAFFIC |
| Warm-up tùy chọn, có thể làm sau | `ApplicationReadyEvent` / runner dispatch async | Cho phép ứng dụng phục vụ trong trạng thái có fallback | Log, metric, retry/backoff, health status phù hợp |
| Batch chỉ cần chạy một lần trên toàn cluster | Job scheduler/queue/leader election, DB lease | Tùy contract chung | Idempotency, khóa/claim và trạng thái bền vững |

**Không nên lấy một @Async method làm bằng chứng tính sẵn sàng.** Một Future được tạo chứng minh có yêu cầu gửi task (và còn phải xử lý từ chối); nó không nói task đã hoàn thành.

Nếu runner chờ Future để chặn startup, phải có deadline:

~~~java
// Trích đoạn minh họa trong ApplicationRunner, không độc lập.
CompletableFuture<Void> job = worker.initializeRequiredData();

try {
    job.get(20, TimeUnit.SECONDS);
} catch (TimeoutException ex) {
    job.cancel(true); // Chỉ là yêu cầu hủy, không bảo đảm worker đã dừng.
    throw new IllegalStateException("Bắt buộc khởi tạo dữ liệu nhưng quá hạn", ex);
}
~~~

Cần import các kiểu từ `java.util.concurrent`; `initializeRequiredData()` ở đây là method do ứng dụng định nghĩa. Còn phải cân nhắc `ExecutionException` và `InterruptedException`: ghi nhận nguyên nhân lỗi, khôi phục interrupt flag khi bắt interrupted, rồi quyết định fail startup hay degraded readiness. `cancel(true)` không tự rollback giao dịch DB và không dừng tác vụ phớt lờ interrupt.

Spring Boot phát `ApplicationReadyEvent` sau runners, rồi có trạng thái readiness chấp nhận traffic nếu không có logic tùy chỉnh. Nếu runner chờ xong mới trả, bạn có thể bảo đảm startup không tuyên bố ready quá sớm, nhưng việc chờ cũng tăng thời gian deployment và có thể làm probe timeout. Chọn giới hạn phù hợp với nền tảng vận hành.

## 7. Phân biệt lỗi khởi tạo và lỗi trong tác vụ async

### 7.1. @PostConstruct ném lỗi có thể làm bean creation thất bại

~~~java
// Đoạn minh họa bên trong @Component.
@PostConstruct
public void checkConfiguration() {
    if (requiredEndpoint == null) {
        throw new IllegalStateException("Missing endpoint");
    }
}
~~~

Không cần `@Async` để kiểm tra một điều kiện đơn giản như trên. Nếu lỗi phát sinh trong callback, Spring có thể dừng tạo bean và từ đó khởi động context thất bại. Điều này hợp lý cho **điều kiện cấu hình bắt buộc** và là lý do nên giữ `@PostConstruct` ngắn, rõ và ổn định.

### 7.2. @Async void ném lỗi không quay về caller theo Future

~~~java
// Đoạn minh họa bên trong bean worker.
@Async("initExecutor")
public void refreshCatalog() {
    throw new IllegalStateException("remote service unavailable");
}
~~~

Nếu được gọi qua proxy và thực thi bằng executor bất đồng bộ, exception phát sinh trong worker. Với return type `void`, caller không có Future để nhận lỗi; Spring dùng `AsyncUncaughtExceptionHandler` (mặc định chủ yếu log lỗi). Với `CompletableFuture<T>`/`Future<T>`, caller có thể quan sát lỗi qua `get()/join()` hoặc completion callback.

Vì vậy, nếu warm-up quan trọng, **trả Future để giám sát trạng thái** thay vì fire-and-forget không ghi nhận. Trường hợp executor bão hòa và từ chối ngay tại submission còn có thể tạo `TaskRejectedException` tại caller. Đừng dùng silently-discard rejection policy cho tác vụ mà readiness hoặc Future phụ thuộc kết quả, vì job có thể mất mà Future không hoàn tất.

### 7.3. Không che giấu lỗi bằng callback khởi động

Nếu chọn dispatch từ `ApplicationReadyEvent` và sau đó không đợi, startup vẫn có thể báo ready dù warm-up lỗi. Với job tùy chọn, phải ghi log kèm job ID và đo metrics; với job bắt buộc, dùng readiness gate và fallback đúng yêu cầu nghiệp vụ. Một startup hook được gọi thành công không chứng minh remote data, cache hay DB snapshot đã sẵn sàng.

## 8. ThreadLocal, transaction, proxy và async thật sự

Sau khi sửa để lời gọi đi qua proxy, có thể xuất hiện những thay đổi mà test trước đây không phát hiện do callback chạy đồng bộ:

- **Transaction:** transaction association thuộc về thread; `@Transactional` của callback/caller không tự chuyển sang worker. Worker cần boundary transaction riêng, thường thông qua một service bean được Spring proxy quản lý. Không tự giữ JDBC connection/EntityManager qua threads.
- **MDC/trace/security:** `ThreadLocal` thường không tự truyền sang worker; capture các ID cần thiết rồi truyền qua tham số, hoặc cấu hình context propagation như `TaskDecorator`. Worker phải dọn context khi pool tái sử dụng thread.
- **Request scope:** lúc khởi động ứng dụng thường không có HTTP request. Đừng inject/use request-scoped bean trong callback startup như thể request context tồn tại.
- **Mutable object:** việc gửi task sang thread khác có thể tạo race nếu callback tiếp tục sửa object mà worker đang đọc. Dùng immutable snapshot hoặc đồng bộ hóa đúng.
- **Bean dependencies:** worker được inject là bean Spring quản lý nhưng có thể gọi đến service khác còn lazy hoặc có external resource chưa sẵn sàng; kiểm soát điều kiện và retry.
- **ThreadPoolTaskExecutor:** cấu hình core/max/queue, rejection, thread naming và graceful shutdown phù hợp; mặc định Framework hay Boot không bảo đảm một số luồng cố định trong mọi môi trường.

Tương tự, `@Async` trên method `private`/`final` hoặc trên class `final` có giới hạn proxy trong các phương thức proxy thông dụng; dùng **public, non-final** method trên Spring bean, được bean khác gọi. Việc bật `@EnableAsync` đúng `ApplicationContext` và gọi qua proxy là điều kiện quan trọng. Nếu dùng `SyncTaskExecutor`, lời gọi qua proxy có thể đã được intercept nhưng vẫn chạy trên **caller thread**, vì bản thân executor là synchronous. Do đó, khi chẩn đoán, phải xác minh cả **proxy path** và **executor implementation**, không chỉ nhìn thread name.

## 9. Chẩn đoán: @Async không chạy sau @PostConstruct

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra / sửa |
| --- | --- | --- |
| `@PostConstruct @Async` vẫn chạy trên main thread | Callback được gọi trong giai đoạn bean initialization, không qua async proxy | Bỏ `@Async` khỏi callback; dispatch từ runner/sự kiện sang bean worker khác |
| `this.asyncWork()` vẫn đồng bộ | Self-invocation bỏ qua proxy | Tách worker sang bean khác, inject proxy |
| Việc nền làm chậm khởi động | Callback làm I/O nặng trước khi init bean hoàn tất | Đưa sang post-refresh hook, timeout rõ |
| `@Async` có nhưng không thấy worker thread | Thiếu `@EnableAsync`, self-call, gọi object tự `new` hoặc dùng `SyncTaskExecutor` | Kiểm tra bean, interceptor, executor và thread prefix |
| Startup hiển thị ready nhưng dữ liệu chưa nạp | Async task chạy sau khi runner/listener trả về | Chọn readiness gate hoặc chờ Future có hạn định |
| Exception warm-up không làm startup fail | `void @Async` chạy tách biệt, lỗi vào uncaught handler | Trả CompletableFuture, xử lý failure và đánh giá readiness |
| Thỉnh thoảng deadlock khi startup | Chờ/khởi phát cross-bean từ `@PostConstruct` dưới singleton creation lock | Chuyển sang runner sau refresh, giảm circular dependency |
| Đã sửa sang worker bean nhưng @Transactional không đúng | Transaction context caller không đi theo worker | Định nghĩa transaction trên đường gọi trong worker |
| Test sync pass, production race | Worker thật chạy song song, mutable state chia sẻ | Dùng latch, immutable data, lock/transaction phù hợp |
| Có nhiều pod cùng warm-up | Startup hook chạy ở mỗi instance | Dùng idempotency hoặc coordination nếu cần chỉ một lần toàn cụm |

### Checklist trước khi merge

1. `@PostConstruct` chỉ validate config/khởi tạo trạng thái nhẹ, không làm I/O nặng và không dựa vào `@Async`.
2. Phương thức cần chạy nền nằm ở **Spring-managed bean khác**, được gọi từ một trigger thích hợp sau khi proxy sẵn sàng.
3. `@EnableAsync` được bật đúng context, và code không đi qua `this.method()` hoặc object tạo bằng `new`.
4. Executor được chỉ rõ (tên/qualifier) và có thread prefix để quan sát; queue/rejection/shutdown được xem xét.
5. Phương thức async có return type hợp lệ: `void`, `Future<T>` hoặc `CompletableFuture<T>`. Nếu startup phụ thuộc kết quả, không dùng `void` không có đường báo lỗi.
6. Có timeout, error handler, logging và trạng thái thất bại cho warm-up. Không giả định `Future.get(timeout)` tự cancel task.
7. Chính sách readiness tường minh: công việc có bắt buộc xong trước traffic không? Event/runner có trả trước khi công việc xong không?
8. Context/security/transaction và mutable state được thiết kế lại khi chuyển sang worker; không giả định giữ nguyên những gì có ở callback.
9. Test có xác minh **callback inline**, **self-call inline** và **cross-bean async thực sự**; test thêm overload và shutdown nếu job quan trọng.
10. Với multi-instance, job có thể được khởi phát nhiều lần; dùng idempotency, job ledger hoặc cluster coordination khi cần.

## 10. Bài tập tư duy

1. Vì sao `@PostConstruct @Async public void init()` không bảo đảm chạy nền dù bean có `@EnableAsync`?
2. Một method `load()` được đánh dấu `@Async`, nhưng `@PostConstruct` trong cùng bean gọi `this.load()`. Cần thay cấu trúc bean như thế nào để interceptor được áp dụng?
3. Lời gọi từ `ApplicationRunner` sang bean khác có gì khác với self-invocation? Khi nào runner nên đợi kết quả, khi nào nên trả ngay?
4. `ApplicationReadyEvent` được phát sau runners. Nếu listener gửi job rồi trả ngay, job đã hoàn tất khi readiness chấp nhận traffic chưa?
5. Tại sao `@Async void` có thể log lỗi mà không khiến startup thất bại? Nếu warm-up là bắt buộc, nên chọn kiểu return gì?
6. Một test chạy với `SyncTaskExecutor` thấy callback và worker cùng thread. Làm sao phân biệt proxy không kích hoạt với proxy đã kích hoạt nhưng executor đồng bộ?
7. Nếu một job warm-up tốn 30 giây và gọi DB chậm, nên cân nhắc timeout, retry, transaction và readiness ra sao?
8. Nếu ứng dụng có ba pod, việc chuyển từ `@PostConstruct` sang `ApplicationRunner` có tự ngăn ba pod cùng xử lý một job global không?

**Kết luận:** `@PostConstruct` là **callback của container trong pha tạo bean**, không phải điểm gọi ứng dụng qua Spring proxy. `@Async` trong proxy mode chỉ có ý nghĩa khi lời gọi đi qua proxy và executor phù hợp. Đừng đặt `@Async` trên chính lifecycle callback hoặc dựa vào `this.asyncMethod()`. Hãy dùng một trigger tách biệt sau khi bean sẵn sàng để gọi worker bean khác, đồng thời làm rõ quy tắc timeout, lỗi và readiness.

## Tài liệu tham khảo chính thức

- [Spring Framework — Task Execution and Scheduling, @Async](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — @PostConstruct / @PreDestroy](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/postconstruct-and-predestroy-annotations.html)
- [Spring Framework — Bean lifecycle and initialization](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html)
- [Spring Boot 3.3 — SpringApplication and Application Events](https://docs.spring.io/spring-boot/3.3/reference/features/spring-application.html)
- [Spring Framework — @EnableAsync Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-invocation-008">Giới hạn khởi tạo sớm khi dùng AsyncConfigurer</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn khởi tạo sớm khi dùng AsyncConfigurer trong Spring

Khi bật xử lý bất đồng bộ bằng `@EnableAsync`, Spring cho phép tùy biến executor mặc định và cách xử lý exception của phương thức `@Async void` qua interface **`AsyncConfigurer`**. Đây là một cơ chế hữu ích, nhưng có một giới hạn vòng đời dễ bị bỏ qua: **configuration class triển khai `AsyncConfigurer` được Spring khởi tạo sớm trong giai đoạn bootstrap của ApplicationContext**.

Điều này ảnh hưởng trực tiếp đến việc tiêm dependency và proxy. Nếu class `AsyncConfigurer` yêu cầu tạo một service khác quá sớm, service đó có thể chưa đi qua đủ các `BeanPostProcessor`, bao gồm các bộ xử lý tạo proxy cho các annotation nhất định. Kết quả có thể là bean được khởi tạo không đúng thời điểm, vòng phụ thuộc, thiếu một số advice hoặc hành vi không như kỳ vọng. Spring khuyên **để các dependency đó lazy tối đa có thể**.

Bài học thuộc nhóm **Proxy và lời gọi** của module **Spring Framework – concurrency**. Trọng tâm là hiểu giới hạn trên, phân biệt nó với việc `@Async` không hoạt động do self-invocation, và tổ chức cấu hình executor sao cho **đúng bean, đúng lifecycle, đúng proxy**.

## 1. `AsyncConfigurer` dùng để làm gì?

Spring `@EnableAsync` kích hoạt xử lý phương thức đánh dấu `@Async`. Với phương thức gọi **qua proxy** trong chế độ mặc định, Spring chọn executor để chuyển phần thực thi sang một thread khác. `AsyncConfigurer` là hook cấp cấu hình cho hai vấn đề:

```java
public interface AsyncConfigurer {
    default Executor getAsyncExecutor() {
        return null;
    }

    default AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return null;
    }
}
```

Đây là **hình dạng API rút gọn để giải thích**; không nên sao chép interface này vào dự án vì Spring đã cung cấp `org.springframework.scheduling.annotation.AsyncConfigurer`. Các phương thức thực tế là phương thức mặc định và có thể trả null khi không cần tùy biến mục tương ứng.

- `getAsyncExecutor()`: cung cấp **executor mặc định** cho những lời gọi `@Async` không chọn executor cụ thể.
- `getAsyncUncaughtExceptionHandler()`: xử lý các exception không thể gửi trực tiếp về caller từ phương thức `@Async` có kiểu trả về `void`.
- Nếu `@Async("reportExecutor")` chọn một executor có tên/qualifier cụ thể, cấu hình chọn tên ấy được xét cho lời gọi; không nên cho rằng mọi `@Async` đều dùng executor mặc định của `AsyncConfigurer`.
- Nếu dùng `Future<T>` hoặc `CompletableFuture<T>`, caller cần quan sát completion/exception qua Future. Exception handler cho `void` không thay thế đường báo lỗi của Future.

Để tùy biến:

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        // Trả về executor mặc định đã cấu hình.
    }

    @Override
    public AsyncUncaughtExceptionHandler
            getAsyncUncaughtExceptionHandler() {
        // Trả về handler cho @Async void khi cần.
    }
}
```

Đây là **fragment ý tưởng, không phải chương trình biên dịch độc lập**: phần body và imports chưa được viết. Phần sau sẽ có chương trình Spring Boot đầy đủ và các cấu hình thay thế hoàn chỉnh.

## 2. “Khởi tạo sớm” nghĩa là gì?

Trong tiến trình tạo `ApplicationContext`, Spring phải đăng ký các thành phần infrastructure để nhận diện `@Async`, quyết định cách áp advice/proxy và cấu hình executor/exception handler. Chính vì vậy, **các configuration class triển khai `AsyncConfigurer` được khởi tạo sớm**, thay vì được giả định chỉ khởi tạo ở cuối bootstrap.

Một timeline giản lược:

```text
Khởi động ApplicationContext
       |
       v
Đọc @Configuration / @EnableAsync
       |
       v
Đăng ký async infrastructure và các BeanPostProcessor liên quan
       |
       +--> Cần phát hiện AsyncConfigurer
       |       |
       |       v
       |    AsyncConfigurer có thể được khởi tạo SỚM
       |       |
       |       +--> Nếu eager inject service khác:
       |               service đó có thể bị tạo quá sớm
       |               so với một số post-processor/proxy
       |
       v
Hoàn tất đăng ký/áp dụng các hậu xử lý cho bean bình thường
       |
       v
Các singleton sẵn sàng / context bắt đầu phục vụ
```

Hình trên **không phải thứ tự mọi callback cố định giữa tất cả phiên bản Spring**. Nó minh họa **nguyên nhân cơ chế**: khi một infrastructure component đòi truy cập `AsyncConfigurer` từ sớm, các dependency bị kéo theo có thể chưa trải qua điều kiện xử lý bình thường của ứng dụng.

### 2.1. Cảnh báo chính thức của Spring

Javadoc của `@EnableAsync` lưu ý rằng configuration class triển khai `AsyncConfigurer` **khởi tạo sớm khi bootstrap**; nếu cần phụ thuộc vào các bean khác, nên **khai báo chúng lazy nhiều nhất có thể**, để chúng có cơ hội đi qua các post-processor khác.

Điều này có hai hệ quả:

1. **Không giả định** mọi dependency đang được inject vào `AsyncConfigurer` đã có đầy đủ proxy/advice giống như khi được resolve muộn ở runtime.
2. **Không tạo dây chuyền dependency phức tạp** bên trong configurer chỉ để lựa chọn một executor; ưu tiên cấu hình đơn giản, tách bean rõ ràng và trì hoãn resolve khi cần.

Nguồn chính: Javadoc Spring Framework 6.2 `EnableAsync` (phần chú ý về `AsyncConfigurer`): https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html

### 2.2. Vì sao post-processor lại quan trọng?

Spring dùng các `BeanPostProcessor` và infrastructure AOP để can thiệp quá trình tạo bean, có thể bọc bean bằng proxy trước khi đưa vào sử dụng. Tùy cấu hình, proxy/advice có thể xử lý `@Async`, `@Transactional`, caching, security hoặc các cross-cutting concerns khác.

Ví dụ một service có `@Transactional` cần được gọi **qua proxy chứa transaction interceptor** thì annotation mới có tác dụng trong chế độ proxy thông thường. Nếu service được khởi tạo/tiếp cận quá sớm rồi một tham chiếu chưa được xử lý đầy đủ bị giữ lại, bạn có thể thấy lời gọi không có behavior như mong đợi.

Cần diễn đạt thận trọng:

- **Không phải cứ tiêm service vào `AsyncConfigurer` thì chắc chắn mất proxy.** Tác động phụ thuộc thời điểm và chuỗi khởi tạo thực tế.
- **Không phải `@Transactional` hay `@Async` luôn mất tác dụng** nếu class configurer được tạo sớm. Đây là **rủi ro phụ thuộc lifecycle** cần tránh và kiểm chứng.
- Vấn đề chính là **lấy/khởi tạo dependency quá sớm**, không phải bản thân từ khóa `@Configuration` hay interface `AsyncConfigurer` luôn lỗi.

## 3. Ví dụ cấu hình có rủi ro: tiêm eager service vào AsyncConfigurer

Đoạn sau minh họa **mẫu nên tránh**:

```java
@Configuration
@EnableAsync
public class RiskyAsyncConfig implements AsyncConfigurer {
    private final BillingService billingService;

    public RiskyAsyncConfig(BillingService billingService) {
        this.billingService = billingService; // Eager dependency
    }

    @Override
    public Executor getAsyncExecutor() {
        billingService.auditBootstrap(); // Gọi business logic lúc cấu hình!
        return Executors.newFixedThreadPool(4);
    }
}
```

Đây là **fragment giải thích rủi ro**, không phải chương trình hoàn chỉnh: `BillingService` do ứng dụng định nghĩa, imports và shutdown policy chưa được cung cấp. **Không dùng đoạn mã trên như một giải pháp production.**

Các vấn đề độc lập:

1. Constructor của `RiskyAsyncConfig` yêu cầu `BillingService` ngay lúc configurer được tạo. Nếu Spring đang ở pha bootstrap sớm, dependency có thể bị kéo vào quá trình tạo bean trước thời điểm thích hợp.
2. Nếu `BillingService` cần AOP proxy (ví dụ `@Transactional` hoặc `@Async`), việc bị resolve sớm có thể dẫn tới hành vi proxy không đúng kỳ vọng hoặc thông báo bean chưa đủ điều kiện đi qua toàn bộ post-processor.
3. Gọi `billingService.auditBootstrap()` bên trong `getAsyncExecutor()` trộn **khởi tạo infrastructure** với **nghiệp vụ**, dễ gây side effect, truy cập DB ngoài thời điểm cho phép hoặc khởi động task trước khi context sẵn sàng.
4. `Executors.newFixedThreadPool(4)` là executor JDK mới tạo, không tự là Spring bean và không tự được Spring đóng theo lifecycle bean nếu không có quản lý bổ sung.
5. Nếu dependency lại cần chính executor/`@Async` infrastructure đang thiết lập, có thể phát sinh vòng phụ thuộc: configurer → service → executor/proxy → configurer.

**Triệu chứng có thể gặp** (không phải triệu chứng luôn xảy ra): lỗi bean đang được tạo, BeanCurrentlyInCreationException, startup thất bại, log báo bean “not eligible for getting processed by all BeanPostProcessors”, proxy không xuất hiện đúng lúc, hoặc startup có side effect bất thường.

Đừng kết luận nguyên nhân chỉ từ một log. Cần nhìn **dependency graph**, thời điểm resolve và thực tế bean được proxy hay chưa.

## 4. Cách ưu tiên: không dùng AsyncConfigurer nếu chỉ muốn chọn một executor cụ thể

Trong nhiều dự án, mục tiêu chỉ là “dùng thread pool A cho báo cáo, thread pool B cho email”. Không nhất thiết triển khai `AsyncConfigurer`. Có thể:

1. Tạo một bean `ThreadPoolTaskExecutor` được Spring quản lý.
2. Bật `@EnableAsync`.
3. Dùng `@Async("beanName")` trên method cần dùng pool đó.
4. Gọi method qua một Spring bean khác để đi qua proxy.

Mô hình:

```text
@Configuration @EnableAsync
          |
          +-- @Bean("reportExecutor") --> ThreadPoolTaskExecutor (managed)
          |
Controller bean --> ReportService proxy --> @Async("reportExecutor")
                                               |
                                               v
                                         Worker threads
```

Cách này giảm số lý do phải dùng `AsyncConfigurer`, tránh phụ thuộc business service trong hook infrastructure, đồng thời tường minh executor nào được chọn.

### 4.1. Chương trình Spring Boot hoàn chỉnh cho môi trường Spring Boot 3.x

Dùng **Java 17+**, **Spring Boot 3.x / Spring Framework 6.x** và dependency `spring-boot-starter-web`. Đặt mã vào **src/main/java/com/example/demo/AsyncStartupApplication.java**. Đây là ứng dụng Boot hoàn chỉnh về cấu trúc class, cần được biên dịch/chạy trong **project Spring Boot có dependencies tương ứng**; không thể chạy bằng `javac` trần khi thiếu Spring jars.

```java
package com.example.demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableAsync
public class AsyncStartupApplication {
    public static void main(String[] args) {
        SpringApplication.run(AsyncStartupApplication.class, args);
    }

    @Bean("reportExecutor")
    ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(4);
        pool.setQueueCapacity(20);
        pool.setThreadNamePrefix("report-");
        pool.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return pool;  // Spring quản lý initialize/shutdown của bean
    }
}

@Service
class ReportService {
    @Async("reportExecutor")
    public CompletableFuture<String> build(String reportId) {
        String worker = Thread.currentThread().getName();
        if (!worker.startsWith("report-")) {
            throw new IllegalStateException("Unexpected executor: " + worker);
        }
        return CompletableFuture.completedFuture("built:" + reportId);
    }
}

@RestController
class ReportController {
    private final ReportService reports;

    ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/reports/{id}/build")
    public CompletableFuture<String> build(@PathVariable String id) {
        return reports.build(id);
    }
}
```

Trong một dự án có Gradle wrapper và Spring Boot Gradle plugin, chạy:

```bash
./gradlew bootRun
curl -s http://localhost:8080/reports/R1/build
```

Trên Windows, dùng `gradlew.bat bootRun`. Khi request được nhận và method chạy thành công, response body là:

```text
built:R1
```

Giải thích:

- `@EnableAsync` được đặt trên application class để bật async infrastructure.
- `@Bean("reportExecutor")` tạo **bean executor có tên**, không cần `AsyncConfigurer`.
- `ReportController` gọi **bean `ReportService` khác**, nên lời gọi đi qua proxy `@Async` trong chế độ mặc định.
- `@Async("reportExecutor")` chọn bean tường minh; tên thread bắt đầu bằng `report-` là một cách kiểm tra minh họa.
- Spring MVC có thể xử lý response kiểu `CompletableFuture` bằng cơ chế async request; **HTTP client vẫn chờ Future hoàn thành để nhận body**, không phải ngay khi vừa submit.
- Method `build` ở đây chỉ trả chuỗi để minh họa execution path, **không tạo báo cáo thực**. Nếu cần xử lý job dài và trả 202 ngay, phải có giao thức job ID/status riêng.

**Lợi ích về bootstrap:** không có `AsyncConfigurer` giữ tham chiếu đến service nghiệp vụ hoặc bắt nó khởi tạo khi Spring còn đang cấu hình async infrastructure.

### 4.2. Nếu cần executor mặc định mà không chỉ rõ mỗi @Async?

Javadoc `@EnableAsync` mô tả việc Spring tìm một `TaskExecutor` duy nhất hoặc `Executor` có tên `taskExecutor`, nếu không có cấu hình custom khác. Vì vậy có thể dùng một bean executor được đặt tên và bảo đảm không mơ hồ. Trong dự án Spring Boot, **auto-configuration và số lượng executor bean có thể ảnh hưởng lựa chọn thực tế**; không nên suy luận default chỉ dựa vào tên class.

Để có kết quả dễ hiểu, nếu cần chạy trên một pool cụ thể thì `@Async("reportExecutor")` vẫn là lựa chọn tường minh. Khi muốn thay đổi executor mặc định cho toàn bộ `@Async` không qualifier, `AsyncConfigurer` là một tùy chọn, nhưng cần xử lý đúng vấn đề khởi tạo sớm.

## 5. Nếu phải dùng AsyncConfigurer: trì hoãn dependency một cách có chủ đích

Có những trường hợp thực sự cần `getAsyncExecutor()` làm mặc định hoặc tùy biến `getAsyncUncaughtExceptionHandler()` tập trung. Khi đó cần **đơn giản hóa graph của configurer**, và tránh eagerly lấy các bean có thể cần đi qua post-processor.

### 5.1. Dùng @Lazy tại điểm inject

Một phương án là inject dependency qua **lazy-resolution proxy** thay vì lấy target thật ngay tại constructor của `AsyncConfigurer`.

Ví dụ **cấu hình thay thế**, không dùng đồng thời với application class ở mục 4 trong cùng project nếu không chủ ý phối hợp các cấu hình async:

```java
package com.example.demo;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class LazyAsyncConfig implements AsyncConfigurer {
    private final Executor defaultExecutor;

    public LazyAsyncConfig(
            @Lazy @Qualifier("reportExecutor") Executor defaultExecutor) {
        this.defaultExecutor = defaultExecutor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return defaultExecutor;
    }
}
```

Đoạn này là **class cấu hình đầy đủ** nhưng phụ thuộc vào một bean `reportExecutor` đã được khai báo riêng (chẳng hạn bean ở mục 4) và những import/dependency Spring có sẵn. `@Lazy` trên **injection point** nói với Spring cung cấp một proxy trì hoãn resolve dependency đích. Việc trả proxy từ `getAsyncExecutor()` không nhất thiết buộc target executor khởi tạo ngay khi configurer được tạo.

**Giới hạn cần nắm:**

- `@Lazy` tại injection point **không biến toàn bộ ApplicationContext thành lazy**. Nếu bean đích được một singleton khác eager inject, nó vẫn có thể bị tạo lúc startup.
- Lazy proxy trì hoãn **lần truy cập thực sự vào target**; nếu constructor, `@PostConstruct` hoặc `getAsyncExecutor()` trực tiếp gọi method trên proxy quá sớm, target vẫn có thể bị resolve quá sớm.
- `@Lazy` không sửa được một **dependency cycle thực sự** hay logic nghiệp vụ đang tự kích hoạt lúc startup.
- Nếu bean không tồn tại, proxy lazy có thể được inject nhưng lỗi chỉ bộc lộ **khi thực sự truy cập**. Cần startup validation/test phù hợp.
- Mục tiêu là giảm khả năng tạo dependency trước khi hệ thống post-processor sẵn sàng, không phải lời hứa tuyệt đối rằng mọi cấu hình sẽ luôn an toàn.

### 5.2. ObjectProvider để trì hoãn việc resolve

`ObjectProvider<T>` giúp mang một **handle tra cứu** thay vì bắt container lấy target ngay khi constructor chạy:

```java
package com.example.demo;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class ProviderAsyncConfig implements AsyncConfigurer {
    private final ObjectProvider<Executor> executors;

    public ProviderAsyncConfig(
            @Qualifier("reportExecutor") ObjectProvider<Executor> executors) {
        this.executors = executors;
    }

    @Override
    public Executor getAsyncExecutor() {
        return executors.getObject();
    }
}
```

Đây cũng là **phương án cấu hình thay thế** chỉ dành cho dự án đã khai báo bean `reportExecutor`; không kích hoạt đồng thời nhiều class `AsyncConfigurer` nếu không có chủ đích. Trong cấu hình trên:

- Constructor chỉ nhận provider; không trực tiếp gọi `getObject()` tại đó.
- Khi `getAsyncExecutor()` được gọi, `getObject()` mới yêu cầu bean thực; điều này **dời thời điểm resolve** so với eager constructor injection.
- **Không giả định thời điểm `getAsyncExecutor()` luôn là “sau khi tất cả post-processor hoàn tất”** trong mọi phiên bản/tình huống. Nếu framework hoặc code khác gọi hook từ sớm, provider cũng sẽ resolve sớm.
- Provider hữu ích khi muốn xử lý optional/lazy resolution rõ ràng; tuy nhiên nếu chỉ cần định tuyến method cụ thể, bean executor riêng cộng `@Async("reportExecutor")` thường đơn giản và ít coupling hơn.

**So sánh nhanh hai cách:**

| Cách | Target được lấy lúc nào? | Điều cần chú ý |
| --- | --- | --- |
| Inject `Executor` trực tiếp | Eager khi tạo configurer nếu không lazy | Có thể kéo theo khởi tạo sớm |
| `@Lazy` tại điểm inject | Proxy trước, target lúc lần đầu dùng | Dùng proxy quá sớm vẫn tạo target sớm |
| Inject `ObjectProvider<Executor>` | Provider trước; target lúc gọi `getObject()` | Đừng gọi `getObject()` trong constructor |
| Bean executor riêng + `@Async("name")` | Container quản lý bean và chọn bằng qualifier | Thường không cần AsyncConfigurer |

### 5.3. Vì sao chỉ đặt @Lazy trên class service có thể chưa đủ?

Trong Spring, một **bean definition lazy** thường chỉ được tạo khi có yêu cầu. Nhưng nếu một singleton **không lazy** cần dependency đó để hoàn tất constructor, Spring phải tạo dependency để thỏa quan hệ inject. Do vậy:

```java
@Lazy
@Service
class ExpensiveService {
    // Không bảo đảm được trì hoãn nếu singleton eager khác yêu cầu ngay.
}
```

Chỉ đặt `@Lazy` trên bean class không giải quyết mọi eager dependency chain. Ngược lại, `@Lazy` tại **constructor parameter** có thể tạo proxy tại injection point và tách việc nhận tham chiếu khỏi việc tạo target. Tuy nhiên, **dùng proxy sớm sẽ lại kích hoạt target**.

Tài liệu: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-lazy-init.html

## 6. Java 17+ runnable: hình dung “giữ tham chiếu quá sớm” và “tra cứu muộn”

Ví dụ thuần Java sau không dùng Spring, nhưng mô phỏng đúng **mối nguy khi chụp tham chiếu trước khi object được bọc/decorate**. Nó không phải tái hiện chính xác Spring `BeanPostProcessor`; nó chỉ tách biệt cách lấy tham chiếu.

Lưu toàn bộ thành **EarlyReferenceDemo.java**:

```java
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class EarlyReferenceDemo {
    interface Worker {
        String work();
    }

    static final class RawWorker implements Worker {
        @Override
        public String work() {
            return "work";
        }
    }

    static Worker addAdvice(Worker target) {
        return () -> "advised(" + target.work() + ")";
    }

    public static void main(String[] args) {
        Worker raw = new RawWorker();

        // Eager: giữ tham chiếu trước khi wrapping.
        Worker capturedTooEarly = raw;

        // Một bước mô phỏng post-processing.
        AtomicReference<Worker> registry =
                new AtomicReference<>(addAdvice(raw));

        // Lazy: chỉ tra cứu ở thời điểm sử dụng.
        Supplier<Worker> lazyLookup = registry::get;

        System.out.println("early=" + capturedTooEarly.work());
        System.out.println("lazy=" + lazyLookup.get().work());
    }
}
```

Biên dịch/chạy:

```bash
javac EarlyReferenceDemo.java
java EarlyReferenceDemo
```

**Stdout xác định:**

```text
early=work
lazy=advised(work)
```

**Phân tích:**

- `capturedTooEarly` nắm đúng `RawWorker`; việc sau đó đưa wrapper mới vào registry **không thay** tham chiếu cũ.
- `lazyLookup` tra cứu ở thời điểm dùng, lấy wrapper được ghi vào registry và thấy hành vi `advised(...)`.
- `AtomicReference` ở đây là nơi giữ tham chiếu registry trong demo; Java có cơ chế visibility phù hợp cho thao tác `set/get`. Nhưng chương trình chạy một thread, không nhằm chứng minh thuật toán concurrent.
- Đây là **ẩn dụ kỹ thuật**, không đồng nhất `ObjectProvider` của Spring với `AtomicReference` và cũng không nói Spring luôn sẽ tạo “raw bean” như demo. Spring quản lý nhiều bước lifecycle và proxy phức tạp hơn.
- Bài học thực tiễn: **đừng bắt configuration class sớm giữ một dependency trước khi cơ chế bọc/hậu xử lý có thể áp dụng**.

## 7. Phân biệt ba thời điểm: tạo configurer, lấy executor và chạy @Async

Trong thực tế, ba hành động có thể nằm ở các giai đoạn khác nhau:

1. **Spring tạo instance `AsyncConfigurer`** để đọc cấu hình async infrastructure.
2. **Hook `getAsyncExecutor()` cung cấp executor** được sử dụng như default; lần resolve executor có thể bị dời lại tùy cấu hình.
3. **Caller gọi một method `@Async` qua proxy**, interceptor chọn executor và giao một task.

Sơ đồ:

```text
BOOTSTRAP
  @EnableAsync
       |
       +-- Tạo AsyncConfigurer (có thể rất sớm)
       |        |
       |        +-- eager service injection: có thể kéo service lên sớm
       |        +-- @Lazy/ObjectProvider: giảm coupling tới thời điểm tạo target
       |
       +-- Thiết lập async proxy infrastructure
       |
RUNTIME
  Bean A --> proxy Bean B --> @Async method --> executor --> worker
```

**Khởi tạo sớm của `AsyncConfigurer` không đồng nghĩa method `@Async` sẽ chạy đồng bộ.** Ngược lại, một phương thức chạy đồng bộ dù đã cấu hình pool đúng có thể do **self-invocation** hoặc không đi qua Spring proxy. Đây là **hai vấn đề khác nhau**.

## 8. Proxy và self-invocation: vấn đề riêng dễ bị chẩn đoán nhầm

Với `@EnableAsync` mặc định (proxy mode), chỉ lời gọi **đi qua Spring proxy** mới bị interceptor xử lý. Ví dụ:

```java
@Service
public class ExampleService {
    public void outer() {
        inner(); // Gọi nội bộ cùng instance, không đi qua proxy.
    }

    @Async("reportExecutor")
    public void inner() {
        System.out.println(Thread.currentThread().getName());
    }
}
```

Đây là fragment; cần bean executor và `@EnableAsync` trong application. Nếu gọi `outer()` qua proxy và nó tiếp tục gọi `inner()` bằng `this`, lời gọi nội bộ **không được `@Async` interceptor chặn** trong proxy mode. Điều này **không phải** triệu chứng đủ để kết luận `AsyncConfigurer` được tạo quá sớm.

### Cách kiểm tra tách bạch

- Gọi `@Async` từ **một bean khác**, không phải `this.method()`.
- Kiểm tra bean đang được gọi có phải **Spring-managed bean** không; đừng dùng `new Service()` rồi kỳ vọng proxy.
- Kiểm tra tên/qualifier executor, thread name prefix và hành vi khi submit.
- Nếu nghi ngờ khởi tạo sớm, kiểm tra dependency graph, vòng phụ thuộc, thời điểm bean bị tạo và danh sách proxy/advisor.
- Nếu nghi ngờ self-invocation, tách method sang một Spring bean chuyên biệt hoặc dùng chiến lược interception khác có chủ ý; **không kỳ vọng `@Lazy` tự sửa self-invocation**.

Javadoc `EnableAsync` nhấn mạnh `AdviceMode.PROXY` chỉ intercept lời gọi đi qua proxy; với `AdviceMode.ASPECTJ` có cơ chế weaving khác và yêu cầu cấu hình phụ thuộc riêng. Không đổi sang AspectJ chỉ để che lỗi dependency graph khi chưa hiểu tác động.

## 9. Executor do Spring quản lý khác executor tự tạo trong getAsyncExecutor

Một ví dụ chính thức của `@EnableAsync` có hình dạng:

```java
@Override
public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("custom-");
    executor.initialize();
    return executor;
}
```

Đây là fragment minh họa cho **cách tự tạo executor bên trong hook**. Khi chỉ `new` và return từ method này mà **không khai báo thành bean do Spring quản lý**, executor **không tự có đầy đủ lifecycle bean**. Vì vậy cần chú ý:

- `initialize()` thủ công khi dùng executor kiểu Spring ngoài quy trình `@Bean`.
- Shutdown/resource cleanup do code ứng dụng chịu trách nhiệm nếu executor không được quản lý như một Spring bean.
- Nếu Spring quản lý executor qua `@Bean` thông thường, container sẽ đảm nhiệm initialization và lifecycle phù hợp; không cần gọi `initialize()` thủ công.
- Tài liệu `EnableAsync` cho biết có thể chú thích `@Bean` trên chính `getAsyncExecutor()` để biến executor trả về thành bean được quản lý. Đây là lựa chọn hợp lệ theo Javadoc, nhưng vẫn cần hiểu dependency và thời điểm configurer khởi tạo.
- Có thể đơn giản hơn khi khai báo `@Bean("reportExecutor")` trong một configuration class **không** triển khai `AsyncConfigurer` rồi tham chiếu bằng `@Async("reportExecutor")`.

### Không gọi phương thức @Bean như factory thông thường một cách vô ý

Đoạn sau dễ gây rối nếu bạn không hiểu `@Configuration(proxyBeanMethods = false)`:

```java
@Configuration(proxyBeanMethods = false)
class ExecutorConfig {
    @Bean
    ThreadPoolTaskExecutor reportExecutor() {
        return new ThreadPoolTaskExecutor();
    }

    ThreadPoolTaskExecutor anotherReference() {
        return reportExecutor(); // Lời gọi Java trực tiếp, tạo instance mới.
    }
}
```

Đây là **fragment giải thích**: nếu proxyBeanMethods=false, tự gọi method `reportExecutor()` như một method Java thông thường sẽ chạy body và tạo object mới, **không phải lấy bean do container đã quản lý**. Nếu cần bean, hãy **inject bean** bằng constructor/qualifier hoặc provider phù hợp. Với cấu hình full `@Configuration` có inter-bean proxy, hành vi khác, nhưng phụ thuộc kiểu proxy config không đáng dùng làm giải pháp mơ hồ cho vấn đề early initialization.

Đừng trả một executor mới chưa initialize ở mỗi lượt gọi hook hoặc tạo nhiều pool ngoài kiểm soát chỉ vì thiếu phương thức lấy bean đúng cách.

## 10. Exception handler không nên kéo service phức tạp vào bootstrap

`getAsyncUncaughtExceptionHandler()` xử lý exception từ phương thức `@Async` có return type `void`. Đây cũng là một hook trong `AsyncConfigurer`. Nếu handler được xây dựng bằng cách eager inject một service logging/metrics phức tạp ngay vào constructor configurer, bạn vẫn có nguy cơ kéo dependency graph lên sớm.

Một cấu hình đơn giản có thể trả handler không phụ thuộc bean nghiệp vụ:

```java
@Override
public AsyncUncaughtExceptionHandler
        getAsyncUncaughtExceptionHandler() {
    return (exception, method, params) -> {
        System.err.println(
                "Async void error in " + method.getName()
                + ": " + exception.getClass().getSimpleName());
    };
}
```

Đây là **fragment method** để đặt trong một class `AsyncConfigurer`, cần import `org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler`. Ví dụ dùng `System.err` cho dễ hiểu; production nên dùng logger/metrics thích hợp, tránh ghi thông tin nhạy cảm.

Nếu cần chuyển lỗi vào một service quan sát khác, có thể:

- Dùng một provider/lazy reference và chỉ resolve khi **handler thực sự được gọi**, không gọi trong constructor.
- Tránh tạo vòng dependency giữa exception handler, executor và chính async infrastructure.
- Ưu tiên phương thức trả `CompletableFuture<T>` khi caller cần kết quả hoặc xử lý exception. `AsyncUncaughtExceptionHandler` chỉ có vai trò riêng với `void`, không thay thế `Future.get()`/`whenComplete()`.

## 11. Vòng phụ thuộc và vì sao @Lazy không phải cách sửa mọi thứ

Một đồ thị có vấn đề có thể trông như sau:

```text
AsyncConfigurer
     |
     | eager requires
     v
AuditService (@Async / @Transactional)
     |
     | requires
     v
Executor or another configuration bean
     |
     +--------------------> AsyncConfigurer infrastructure
```

Vòng như vậy có thể dẫn tới `BeanCurrentlyInCreationException` hoặc trạng thái bootstrap khó suy luận. Thêm `@Lazy` đôi khi tránh được việc **khởi tạo target đúng lúc đang mắc vòng**, nhưng không biến một thiết kế phụ thuộc vòng thành thiết kế tốt.

Thứ tự ưu tiên:

1. Tách bean executor ra khỏi business service và hạ tầng async config.
2. Đảm bảo executor **không phụ thuộc ngược** vào service sử dụng nó.
3. Dùng qualifier rõ ràng nếu có nhiều executor.
4. Chỉ khi thực sự cần trì hoãn reference, dùng `@Lazy` tại injection point hoặc `ObjectProvider`.
5. Tránh gọi service nghiệp vụ, truy cập DB, publish event hoặc start task từ constructor của `AsyncConfigurer`.
6. Nếu startup vẫn có vòng phụ thuộc, sửa dependency graph thay vì bật cơ chế cho phép circular references một cách mù quáng.

**Một hệ quả quan trọng:** nếu executor phục vụ cả request và background work, không nên bắt constructor executor đi lấy một service có `@Async` để thiết lập cấu hình. Executor là dependency của service, không nên ngược lại.

## 12. Các hiểu nhầm phổ biến về @Lazy

| Nhận định | Đúng hay sai? | Giải thích |
| --- | --- | --- |
| “Đánh `@Lazy` trên bean là chắc chắn nó chỉ tạo lúc request đầu tiên.” | Sai | Singleton eager khác có thể cần nó ngay lúc startup |
| “`@Lazy` tại injection point có thể tạo proxy trì hoãn lookup target.” | Đúng | Spring dùng lazy-resolution proxy cho dependency đó |
| “Nếu gọi method qua lazy proxy trong constructor thì vẫn lazy.” | Sai | Lần sử dụng thật có thể buộc resolve ngay |
| “ObjectProvider được inject nghĩa là target đã được tạo.” | Sai | Provider có thể được inject trước, target resolve lúc lookup |
| “Gọi `provider.getObject()` trong constructor vẫn bảo đảm không eager.” | Sai | Lúc gọi `getObject()` chính là lúc yêu cầu target |
| “AsyncConfigurer khởi tạo sớm nên mọi `@Async` sẽ chạy sai.” | Sai | Vấn đề phát sinh khi dependency/proxy/lifecycle xử lý không phù hợp |
| “`@Lazy` sửa được self-invocation `this.asyncMethod()`.” | Sai | Self-invocation bỏ qua proxy trong chế độ mặc định |
| “Bean executor tạo bằng `@Bean` có lifecycle quản lý bởi Spring.” | Thông thường đúng | Với một bean đầy đủ do context quản lý, Spring gọi lifecycle tương ứng |
| “Executor tự `new` bên trong hook thì đương nhiên được container đóng.” | Sai | Không phải Spring bean nếu chỉ trả object tự tạo không đăng ký |

Tài liệu `@Lazy` injection points và giới hạn eager dependency: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/context/annotation/Lazy.html

## 13. Cách chẩn đoán ứng dụng có nghi vấn khởi tạo sớm

Nếu sau khi thêm `AsyncConfigurer`, một service bỗng thiếu advice hoặc context không khởi động, hãy phân tích theo thứ tự:

**Bước 1 — Vẽ dependency graph thật ngắn.** Ghi các bean trực tiếp phụ thuộc vào `AsyncConfigurer`, executor, logger/metrics service, business service và các dependency ngược lại. Mục tiêu là tìm đường kéo một service lên trước khi post-processor sẵn sàng.

**Bước 2 — Xác nhận configurer là nguồn tạo sớm.** Tạm tách executor thành một `@Bean` độc lập và dùng `@Async("beanName")` cho đường gọi cần kiểm thử. Nếu lỗi biến mất, đây là bằng chứng hữu ích về dependency/lifecycle (nhưng vẫn cần hiểu nguyên nhân, không kết luận chỉ từ tương quan).

**Bước 3 — Xem cảnh báo BeanPostProcessor.** Nếu log có câu kiểu “not eligible for getting processed by all BeanPostProcessors”, kiểm tra bean nào bị tạo sớm và hạ tầng nào đang yêu cầu nó. Không mặc định mọi cảnh báo đều là lỗi AOP; chúng là tín hiệu cần kiểm chứng.

**Bước 4 — Xác minh proxy ở đường gọi.** Đảm bảo service đang được inject bởi Spring chứ không phải `new`; kiểm tra self-invocation và qualifier. Khi cần, dùng `AopUtils.isAopProxy(bean)` hoặc inspect proxy/advisor trong test, nhưng nhớ rằng **không phải mọi bean đều phải có AOP proxy**.

**Bước 5 — Kiểm tra behavior chứ không chỉ lớp bean.** Với `@Async("reportExecutor")`, có thể ghi thread name prefix từ method, cùng với callback Future/exception và test gọi qua bean khác. Với `@Transactional`, dùng test có transaction boundary rõ ràng và quan sát commit/rollback, không chỉ kiểm tra annotation tồn tại.

**Bước 6 — Phân biệt startup lỗi và runtime lỗi.** Lỗi thiếu bean/vòng dependency ngay lúc refresh context khác với việc worker chạy sai thread ở runtime. `@Lazy` có thể trì hoãn lỗi cấu hình tới lần gọi đầu, nên test integration cần exercise đường sử dụng.

**Bước 7 — Không để giải pháp làm mất lifecycle.** Khi tách hoặc thay executor, xác minh pool được khởi tạo, shutdown đúng, core/max/queue/rejection policy và exception handler vẫn đúng.

### Một checklist test tích hợp thực tế

Trong integration test Spring Boot có thể đặt:

- **Startup check:** `ApplicationContext` refresh thành công với `@EnableAsync`/bean executor.
- **Proxy check:** gọi `@Async` từ bean khác và xác nhận thread name prefix/tín hiệu Future hoàn tất theo kỳ vọng.
- **Advice check:** nếu service liên quan cần `@Transactional` hoặc cache/security advice, kiểm tra bằng hành vi thực tế của advice.
- **Lazy check:** một dependency chưa bị gọi từ constructor/configurer; instrument constructor hoặc log với test fixture để kiểm tra số lần/timing khởi tạo khi cần.
- **Failure check:** khi executor bị từ chối, exception tại submit phải được xử lý; khi worker ném lỗi, Future/handler phải báo đúng đường.
- **Shutdown check:** context đóng và bean executor giải phóng resource phù hợp.

Đừng viết test phụ thuộc vào thứ tự in log của nhiều worker. Dùng `CountDownLatch` hoặc `CompletableFuture` để đợi trạng thái xác định.

## 14. Những lỗi thường gặp

**Lỗi 1 — Inject một service AOP phức tạp thẳng vào constructor `AsyncConfigurer`.** Có thể khiến service tạo quá sớm. Ưu tiên executor bean riêng, lazy injection hoặc provider khi thật sự cần.

**Lỗi 2 — Chỉ đặt `@Lazy` lên class service rồi coi mọi eager resolution đã hết.** Nếu singleton eager cần target, container vẫn phải thỏa dependency; placement của `@Lazy` rất quan trọng.

**Lỗi 3 — Gọi `ObjectProvider.getObject()` ngay trong constructor configurer.** Provider không còn giúp dời thời điểm resolve.

**Lỗi 4 — Gọi method trên lazy proxy ở `@PostConstruct` lúc bootstrap.** Target vẫn có thể bị tạo sớm ngay tại lần dùng.

**Lỗi 5 — Dùng `@Async("executorName")` nhưng bean executor không có tên đúng.** Lỗi chọn executor khác với vấn đề early initialization.

**Lỗi 6 — Gọi `this.asyncMethod()` rồi cho rằng `AsyncConfigurer` hỏng.** Self-invocation bỏ qua advice khi `AdviceMode.PROXY`.

**Lỗi 7 — Định nghĩa executor tự `new` mà không initialize/shutdown.** Dễ thiếu lifecycle và rò rỉ thread khi context đóng.

**Lỗi 8 — Đặt business logic hoặc query DB bên trong `getAsyncExecutor()`.** Hook cấu hình infrastructure không phải nơi thực hiện side effect nghiệp vụ.

**Lỗi 9 — Cho executor phụ thuộc vào service đang dùng executor ấy.** Tạo vòng dependency hoặc startup behavior khó đoán.

**Lỗi 10 — Tin rằng có một `AsyncConfigurer` là mọi method chắc chắn dùng nó.** `@Async("qualifier")` có thể chọn executor riêng; cần kiểm tra routing.

**Lỗi 11 — Cho rằng `@Async void` sẽ ném exception về HTTP caller.** Với `void`, cần handler/log/metric; với Future, caller phải quan sát result/exception.

**Lỗi 12 — Sửa startup bằng cách bật circular references hoặc tắt advice.** Có thể che triệu chứng nhưng làm hệ thống khó bảo trì; nên sửa graph và kiểm chứng proxy.

**Lỗi 13 — Tạo nhiều AsyncConfigurer cạnh tranh trong một context.** Không nên dùng nhiều configurer cấp mặc định không có ý nghĩa phối hợp rõ ràng; nên hợp nhất cấu hình hoặc chọn executor qua qualifier.

**Lỗi 14 — Nhầm một cảnh báo BPP thành bằng chứng duy nhất.** Cần kết hợp stacktrace, thời điểm bean creation và test hành vi; không suy luận rằng mọi proxy đều đã hỏng.

## 15. Quyết định thiết kế theo nhu cầu

| Nhu cầu | Hướng thiết kế nên ưu tiên |
| --- | --- |
| Một method cần pool riêng | `@Bean("specificExecutor")` + `@Async("specificExecutor")` |
| Nhiều nhóm task cần isolation | Nhiều executor bean có qualifier cụ thể |
| Muốn chọn default executor cho `@Async` không qualifier | Bean theo quy tắc resolution hoặc `AsyncConfigurer` khi cần |
| Cần custom handler cho `@Async void` | `AsyncConfigurer#getAsyncUncaughtExceptionHandler()`, hạn chế dependency sớm |
| Configurer phải phụ thuộc bean phức tạp | Cân nhắc `@Lazy` tại injection point hoặc `ObjectProvider` và tránh dereference sớm |
| Cần dùng service có AOP proxy bên trong config | Tách logic nghiệp vụ ra khỏi infrastructure; chỉ truy cập khi context sẵn sàng |
| Task không chạy async khi tự gọi trong class | Sửa self-invocation/proxy boundary, không chỉ thêm `@Lazy` |
| Nghi ngờ BeanPostProcessor thiếu cơ hội xử lý | Kiểm tra early dependency resolution và proxy/advisor bằng integration test |
| Executor cần shutdown sạch | Đăng ký thành bean Spring quản lý; không chỉ trả object tự `new` không quản lý |

**Quy tắc thực dụng:** nếu không cần override executor mặc định hoặc exception handler toàn cục, **đừng triển khai `AsyncConfigurer` chỉ để chọn một thread pool cho một nhóm method**. Dùng named bean và qualifier, giúp loại bỏ phần lớn rủi ro cấu hình khởi tạo sớm.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Tại sao cấu hình class triển khai `AsyncConfigurer` có thể bị tạo trước các business singleton khác?

**Đáp án:** Spring cần đọc cấu hình async infrastructure trong quá trình bootstrap. Javadoc `@EnableAsync` cảnh báo configuration class `AsyncConfigurer` được khởi tạo sớm; các dependency bị yêu cầu eager ở giai đoạn này có thể chưa trải qua tất cả post-processor.

**Câu 2.** Cứ inject một service vào constructor `AsyncConfigurer` là chắc chắn `@Transactional` mất tác dụng?

**Đáp án:** Không. Đây là nguy cơ phụ thuộc thời điểm bootstrap, không phải quy tắc “100% mất proxy”. Cần xác minh dependency graph và behavior của bean trong context thực tế.

**Câu 3.** Sự khác nhau giữa `@Lazy` trên bean class và `@Lazy` tại constructor parameter?

**Đáp án:** `@Lazy` trên bean definition muốn trì hoãn bean tới lần đầu cần, nhưng singleton eager khác có thể buộc tạo nó lúc startup. `@Lazy` tại injection point có thể cung cấp lazy-resolution proxy, trì hoãn lấy target đến lần sử dụng.

**Câu 4.** Inject `ObjectProvider<Executor>` rồi gọi `getObject()` ngay trong constructor configurer có giải quyết vấn đề không?

**Đáp án:** Không. Bạn vẫn yêu cầu target được tạo tại thời điểm constructor. Lợi ích của provider chỉ tồn tại khi việc resolve thật sự được dời đến lúc thích hợp.

**Câu 5.** Một lời gọi `this.runAsync()` vẫn chạy trên caller thread dù executor đã cấu hình đúng. Có chứng minh AsyncConfigurer bị khởi tạo sớm dẫn đến lỗi không?

**Đáp án:** Không. Trong `AdviceMode.PROXY` mặc định, self-invocation bỏ qua proxy nên `@Async` interceptor không chạy. Đây là vấn đề đường gọi, không tự chỉ ra lỗi bootstrap.

**Câu 6.** Muốn method `generate` dùng pool `reportExecutor`, cách tối giản là gì?

**Đáp án:** Định nghĩa `@Bean("reportExecutor")` với `ThreadPoolTaskExecutor`, bật `@EnableAsync`, đánh dấu `@Async("reportExecutor")` và gọi method qua Spring proxy. Thường không cần `AsyncConfigurer`.

**Câu 7.** `getAsyncExecutor()` tạo `new ThreadPoolTaskExecutor()` và chỉ return thì pool đã là bean được Spring quản lý chưa?

**Đáp án:** Chưa chắc. Nếu object chỉ tự tạo trong hook, nó không mặc nhiên thành bean. Theo Javadoc, có thể thêm `@Bean` trên hook để quản lý lifecycle hoặc tốt hơn là tách một `@Bean` named executor rõ ràng.

**Câu 8.** Nếu lỗi startup biến mất sau khi thêm `@Lazy`, đã đủ để khẳng định ứng dụng an toàn chưa?

**Đáp án:** Chưa. Có thể lỗi chỉ bị trì hoãn đến lần đầu target được dùng. Cần integration test đường `@Async`, proxy/advice liên quan, handler, shutdown và behavior thực tế.

## Tóm tắt

- `AsyncConfigurer` tùy biến executor mặc định và exception handler cho `@Async void`.
- **Class triển khai `AsyncConfigurer` có thể được Spring khởi tạo rất sớm**, nên eager injection của bean phức tạp có thể gây vấn đề với lifecycle và hậu xử lý/proxy.
- **`@Lazy` tại injection point** hoặc **`ObjectProvider`** giúp trì hoãn việc resolve target, nhưng sẽ mất tác dụng nếu gọi target ngay trong constructor/pha khởi tạo.
- Đối với nhu cầu thông thường, **tách executor thành bean Spring quản lý và chọn bằng `@Async("beanName")`** là cách đơn giản, rõ ràng, ít phụ thuộc vào early configurer.
- Self-invocation, executor selection, AOP proxy creation và early initialization là **những vấn đề cần phân biệt**, không gộp vào một lỗi.
- Kiểm chứng bằng integration test **vòng đời + proxy + worker execution + exception + shutdown**, thay vì chỉ quan sát annotation hoặc tên thread.

## Tài liệu tham khảo

1. Spring Framework 6.2 Javadoc – `@EnableAsync`, cảnh báo khởi tạo sớm của `AsyncConfigurer` và `@Bean` trên `getAsyncExecutor()`: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html
2. Spring Framework Javadoc – `AsyncConfigurer`: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/annotation/AsyncConfigurer.html
3. Spring Framework – Task Execution and Scheduling, `TaskExecutor` và `@Async`: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
4. Spring Framework – Lazy-initialized Beans: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-lazy-init.html
5. Spring Framework – `@Lazy` injection points: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/context/annotation/Lazy.html
6. Spring Framework – AOP proxying mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-invocation-009">Nhận diện chế độ AspectJ và yêu cầu weaving khi cần lựa chọn khác</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện chế độ AspectJ và yêu cầu weaving khi cần lựa chọn khác

Trong Spring Framework, `@Async` thường hoạt động thông qua **Spring AOP proxy**. Khi một bean khác gọi method được đánh dấu `@Async` **qua proxy**, advice chuyển công việc sang executor; nhưng khi một method trong cùng bean gọi `this.asyncMethod()`, lời gọi nội bộ không đi qua proxy nên advice không được kích hoạt. Đây là giới hạn quan trọng của `AdviceMode.PROXY`.

Spring còn hỗ trợ **`@EnableAsync(mode = AdviceMode.ASPECTJ)`**. Ở chế độ này, advice của `@Async` không chỉ nằm ở đối tượng proxy; AspectJ có thể can thiệp tại **method execution join point** sau khi mã ứng dụng **đã được weave**. Nhờ đó, self-invocation có thể được chặn. Nhưng chỉ đổi enum `mode` là chưa đủ: cần có module `spring-aspects`, cơ chế compile-time/load-time weaving thực sự, cùng kiểm thử để xác nhận bytecode của lớp cần thiết đã được xử lý.

Bài học thuộc nhóm **Proxy và lời gọi**, tập trung vào cách **nhận diện khi nào cần lựa chọn AspectJ**, phụ thuộc kỹ thuật và giới hạn thực tế, không mặc định rằng mọi ứng dụng Spring đều cần weaving.

## 1. Mục tiêu học tập

Sau bài này, bạn sẽ có thể:

- Giải thích `AdviceMode.PROXY` mặc định và vì sao `this.method()` không kích hoạt advice của Spring AOP proxy.
- Phân biệt JDK dynamic proxy, class-based proxy và AspectJ bytecode weaving.
- Mô tả `AdviceMode.ASPECTJ`, sự khác biệt khi advice được gắn vào method execution, và lý do self-invocation có thể hoạt động.
- Biết các điều kiện tối thiểu: `@EnableAsync(mode=AdviceMode.ASPECTJ)`, `spring-aspects`, compiler/weaver tương thích và lớp đích thật sự được weave.
- Phân biệt compile-time weaving (CTW), post-compile weaving và load-time weaving (LTW).
- Biết rằng `@EnableAspectJAutoProxy`, `proxyTargetClass=true` hoặc chỉ thêm dependency **không tương đương** với weaving.
- Xây dựng ví dụ và test không dùng `Thread.sleep`, nhận diện luồng chạy và thời điểm hoàn tất.
- Cân nhắc refactor thành bean riêng hoặc dùng `TaskExecutor` trực tiếp trước khi tăng độ phức tạp của build/deployment.

## 2. Cơ chế @Async với Spring proxy

Trong cấu hình thường gặp:

~~~java
@Configuration
@EnableAsync
public class AppAsyncConfig {
}
~~~

Đây là **đoạn cấu hình Spring** (cần import `Configuration` và `EnableAsync`), không phải chương trình Java chạy độc lập. `@EnableAsync` không ghi `mode` có nghĩa là dùng **`AdviceMode.PROXY`**, cơ chế mặc định của Spring.

Luồng thực thi khi một component gọi bean khác:

~~~text
Caller (thread A)
       |
       v
Spring-managed reference -> proxy
       |
       +-- @Async interceptor
               |
               +-- submit work -> TaskExecutor
                       |
                       +-- worker thread B chạy target method
~~~

Thực tế còn phụ thuộc executor: `SyncTaskExecutor` chạy trên caller, `CallerRunsPolicy` có thể chạy trên caller khi pool bão hòa. Vì vậy ngay cả khi advice đã intercept, không nên hứa luôn có thread vật lý khác.

### Self-invocation: vì sao method gọi chính nó lại bỏ qua proxy?

~~~text
Caller -> proxy -> target.outer()
                        |
                        +-- this.innerAsync()
                                   |
                                   +-- trực tiếp target.innerAsync()
                                       KHÔNG qua proxy
~~~

Trong Java, lời gọi `this.innerAsync()` nằm trong đối tượng đích. Spring proxy không được gọi lại nên `@Async` trên `innerAsync` không có cơ hội tác động ở đường gọi đó.

Ví dụ:

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
    public CompletableFuture<String> begin(String exportId) {
        return perform(exportId); // self-invocation
    }

    @Async("exportExecutor")
    public CompletableFuture<String> perform(String exportId) {
        return CompletableFuture.completedFuture("done:" + exportId);
    }
}
~~~

Đây là **Spring service**, không phải file chạy độc lập vì cần Spring context. Nếu một bean khác gọi `exportService.perform("A")` qua proxy, advice có thể chuyển phần body sang executor. Nếu bean khác gọi `exportService.begin("B")`, method `begin` tự gọi `perform`; trong PROXY mode, body `perform` chạy trực tiếp trên thread của `begin`.

Do body quá nhanh, `CompletableFuture.isDone()` có thể là `true` trong cả hai đường; **không dùng trạng thái ngay sau lời gọi làm test duy nhất**. Hãy giữ body bằng latch hoặc ghi thread identity khi kiểm chứng.

## 3. JDK proxy, CGLIB proxy và AspectJ là ba khái niệm khác nhau

| Thành phần | Vai trò | Có chữa self-invocation của @Async không? |
| --- | --- | --- |
| JDK dynamic proxy | Proxy dựa trên interface | Không: self-call vẫn bỏ qua proxy |
| Class-based proxy (thường CGLIB) | Proxy dựa trên subclass | Không tự giải quyết self-call bên trong target |
| AspectJ weaving | Biến đổi bytecode của class, gắn advice vào join point | Có thể: khi method đã weave và khớp pointcut |
| `@EnableAspectJAutoProxy` | Bật Spring AOP dùng cú pháp `@AspectJ` | **Không đủ** để tạo AspectJ bytecode weaving |
| `@EnableAsync(mode=AdviceMode.ASPECTJ)` | Chọn AspectJ advice mode cho async | **Chưa đủ** nếu thiếu compiler/weaver và aspect |

`proxyTargetClass=true` có ý nghĩa khi Spring đang **dùng proxy mode**: chọn proxy dạng subclass thay cho interface proxy khi thích hợp. Với `AdviceMode.ASPECTJ`, Spring ghi rõ `proxyTargetClass` **bị bỏ qua** đối với advice async. Tham số đó không thể thay thế việc weave.

Tương tự, `@EnableAspectJAutoProxy` dễ gây hiểu nhầm vì có chữ “AspectJ”, nhưng hạ tầng đó chủ yếu sử dụng **Spring AOP proxy-based** để thực thi advice được viết bằng cú pháp `@Aspect`. Nó không biến mọi class thành class đã được AspectJ compiler/weaver sửa bytecode.

## 4. AspectJ mode can thiệp như thế nào?

`@EnableAsync(mode = AdviceMode.ASPECTJ)` chọn cơ chế sử dụng aspect `org.springframework.scheduling.aspectj.AnnotationAsyncExecutionAspect` thuộc module `spring-aspects`. Khi weaving đã thực sự áp vào class đích, advice can thiệp tại method execution join point. Lời gọi ngoài hoặc trong object đều dẫn đến execution join point đã được weave.

~~~text
Caller -> gọi WorkService.outer()
                    |
                    +-- this.innerAsync()
                               |
                               +-- EXECUTION join point đã weave
                                      |
                                      +-- async advice / executor
                                             |
                                             +-- worker chạy body
~~~

Điều này khác cơ bản với một Spring proxy chỉ chặn lời gọi **đi qua proxy reference**. Vì thế AspectJ mode là một giải pháp được Spring tài liệu nêu ra cho những trường hợp cần interception nâng cao, chẳng hạn self-invocation.

Nhưng còn các điều kiện bắt buộc:

- `@Async` phải có chữ ký được hỗ trợ: `void` hoặc một kiểu `Future` phù hợp, bao gồm `CompletableFuture`.
- Annotation và điểm method execution phải khớp pointcut mà `AnnotationAsyncExecutionAspect` hỗ trợ.
- Lớp chứa method phải thực sự được weave, không chỉ được Spring component-scan.
- Aspect cần khả năng tìm executor và/hoặc được Spring cấu hình phù hợp.
- Executor phải còn nhận việc; overload, shutdown và lỗi trong tác vụ vẫn có thể xảy ra.
- `@Async` không được hỗ trợ trên method khai báo bên trong lớp `@Configuration` theo hợp đồng annotation của Spring.

**Không suy diễn:** AspectJ không làm mọi constructor, mọi callback vòng đời hoặc bất kỳ method Java nào trở thành async. Nó chỉ thêm advice tại các join point được weave và khớp định nghĩa aspect. Spring còn cảnh báo không kết hợp `@Async` trực tiếp với lifecycle callback như `@PostConstruct`.

## 5. Weaving là gì, và vì sao không thể bỏ qua?

**Weaving** là quá trình gắn các aspect/advice vào bytecode của lớp đích. Với Spring `@Async` ở AspectJ mode, chỉ tải `spring-aspects.jar` lên classpath **không đủ**: cần có công cụ thực thi weaving để thay đổi class file được JVM chạy.

Có ba giai đoạn đáng biết:

| Cơ chế | Khi nào weave? | Ưu điểm | Lưu ý |
| --- | --- | --- | --- |
| Compile-time weaving (CTW) | Khi build/biên dịch bằng AspectJ compiler `ajc` | Artifact được weave cố định, dễ kiểm soát trước deploy | Build tool và compiler phải hỗ trợ Java bytecode version |
| Post-compile/binary weaving | Sau khi `javac` tạo class, dùng `ajc` trên class/JAR | Có thể tách bước weave ra khỏi compile thông thường | Phải đảm bảo artifact cuối chính là bản woven |
| Load-time weaving (LTW) | Khi JVM nạp class, qua agent/transformer | Không cần tạo class woven ở build nếu runtime hỗ trợ | Classloader, startup, thời điểm load và deployment phức tạp hơn |

### CTW: weave ở quá trình build

~~~text
Source Java + annotation + Spring aspects
                  |
                  v
          AspectJ compiler / ajc
                  |
                  v
        Class đã được weave
                  |
                  v
        Đóng gói, deploy, chạy
~~~

`javac` bình thường **không** thực thi AspectJ weaving. Nếu dự án chỉ chạy Maven compiler plugin Java thông thường, `@EnableAsync(mode=ASPECTJ)` sẽ không tự làm file `.class` thay đổi. Muốn CTW, cần cấu hình `ajc`/AspectJ build plugin cùng aspect path và weave target phù hợp.

Ưu điểm của CTW là có thể xác minh file `.class` trong artifact cuối trước khi deploy. Đổi lại, pipeline phải bảo đảm compiler AspectJ đọc được source/class target đang dùng; đặc biệt khi nâng phiên bản Java, phải kiểm tra compatibility của `ajc`.

### LTW: weave khi JVM nạp class

~~~text
JVM startup
   |
   +-- AspectJ weaver/agent có hiệu lực
   |
   +-- phát hiện META-INF/aop.xml và định nghĩa aspect
   |
   +-- classloader nạp com.example.ExportService
                 |
                 +-- biến đổi bytecode tại thời điểm load
                 |
                 +-- JVM định nghĩa class đã weave
~~~

Nếu class cần weave đã được nạp **trước** lúc weaver hoạt động, việc bật annotation hoặc thêm agent sau đó không tự bảo đảm class sẽ được sửa lại. Thông thường phải khởi động JVM đúng cấu hình ngay từ đầu, hoặc dùng giải pháp instrumentation/retransformation phù hợp được hỗ trợ và kiểm chứng riêng.

Một đường LTW phổ biến dùng AspectJ Java agent:

~~~bash
java -javaagent:/opt/app/lib/aspectjweaver.jar -jar app.jar
~~~

Đây là **lệnh mẫu cho hệ đã có aspectjweaver.jar ở đường dẫn đó**, không phải lệnh cài đặt tự động. Tham số `-javaagent` là **JVM option** và phải đứng trước `-jar`. `spring-aspects` cần có trong runtime classpath ứng dụng; classloader phải nhìn thấy cả aspect lẫn class được weave.

Spring cũng có `@EnableLoadTimeWeaving` và cơ chế `LoadTimeWeaver` theo classloader; trong một số môi trường có thể sử dụng `spring-instrument.jar` để cung cấp instrumentation. Cần phân biệt Spring instrumentation agent với AspectJ weaver, và không khẳng định chỉ thêm `@EnableLoadTimeWeaving` là mọi `@Async` AspectJ đã hoạt động.

## 6. Các thành phần phải có khi chọn ASPECTJ

Bốn điều kiện cần phối hợp:

1. **Chế độ advice:** cấu hình `@EnableAsync(mode = AdviceMode.ASPECTJ)` trong Spring ApplicationContext thích hợp.
2. **Spring aspect:** module `org.springframework:spring-aspects`, chứa `AnnotationAsyncExecutionAspect` và metadata aspect.
3. **Công cụ weaving:** AspectJ compiler `ajc` ở CTW hoặc AspectJ weaver/agent tương thích ở LTW; weaving phải áp **đúng class đích**.
4. **Executor/configuration/test:** executor được resolve, class không bị load quá sớm, aspect thực sự can thiệp, và test xác nhận đường self-call hoạt động.

Ví dụ dependency Maven, **chỉ là fragment bên trong `<dependencies>` của dự án có Spring Boot BOM/parent quản lý phiên bản**:

~~~xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-aspects</artifactId>
</dependency>
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjweaver</artifactId>
</dependency>
~~~

Nếu không có dependency management, mỗi dependency cần version tương thích với Spring và JDK của dự án. Với CTW, cần thêm build plugin/compiler AspectJ và cấu hình weave; chỉ thêm hai dependency chưa thay thế được bước đó.

`spring-aspects.jar` phát hành `META-INF/aop.xml` để đăng ký những Spring aspect phổ biến, trong đó có `AnnotationAsyncExecutionAspect`. Với LTW, AspectJ thường đọc metadata `META-INF/aop.xml` trên runtime classpath. Nếu tự đặt file cấu hình với include/exclude, phải đảm bảo lớp của mình **không bị loại khỏi phạm vi weave** và không đăng ký aspect trùng lặp một cách tùy tiện.

## 7. Ví dụ Spring sử dụng PROXY mode mặc định

Tệp **DefaultAsyncConfig.java**:

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class DefaultAsyncConfig {

    @Bean(name = "exportExecutor")
    public ThreadPoolTaskExecutor exportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("export-");
        return executor;
    }
}
~~~

Tệp **ExportService.java** (như ví dụ trước):

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
    public CompletableFuture<String> begin(String exportId) {
        return perform(exportId);
    }

    @Async("exportExecutor")
    public CompletableFuture<String> perform(String exportId) {
        return CompletableFuture.completedFuture("done:" + exportId);
    }
}
~~~

Đây là **hai class của một ứng dụng Spring**, cần Spring context và thư viện; không chạy đơn lẻ bằng `java ExportService`. Không gọi `initialize()` lặp lại trên executor do container đang quản lý.

Kết quả về mặt đường chạy:

| Lời gọi từ bean khác | PROXY mode |
| --- | --- |
| `exportService.perform("A")` | Gọi qua proxy, async advice có thể gửi task tới `exportExecutor` |
| `exportService.begin("B")` | `begin` chạy và tự gọi `perform` trên target; phần `@Async` không được chặn qua proxy |

Ở ví dụ này, Future có thể hoàn thành rất nhanh nên không đo đường chạy bằng `future.isDone()` tức thì; hãy ghi thread identity hoặc thêm một latch có thời gian chờ kiểm soát.

### Phương án thường tốt hơn: tách bean

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

@Service
public class ExportStarter {
    private final ExportWorker worker;

    public ExportStarter(ExportWorker worker) {
        this.worker = worker;
    }

    public CompletableFuture<String> begin(String exportId) {
        return worker.perform(exportId);
    }
}
~~~

Tệp **ExportWorker.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ExportWorker {
    @Async("exportExecutor")
    public CompletableFuture<String> perform(String exportId) {
        return CompletableFuture.completedFuture("done:" + exportId);
    }
}
~~~

Đây là **hai class trong các tệp riêng** với thư viện Spring. Bean `ExportStarter` inject `ExportWorker` đã được Spring quản lý, nên lời gọi `worker.perform(...)` đi qua reference proxy của bean, thay vì self-invocation. Nếu vấn đề chỉ có một method nội bộ, refactor này thường đơn giản, minh bạch hơn và tránh đưa AspectJ weaving vào build/deploy.

Một lựa chọn khác là gửi `Runnable` hoặc `Callable` trực tiếp qua `TaskExecutor` tại nơi muốn chuyển thread; lúc đó không phụ thuộc interception của proxy, nhưng phải chủ động quản lý Future, exception và lifecycle.

## 8. Cấu hình thay thế: ASPECTJ mode

Khi việc refactor không phù hợp và thực sự cần interception bên trong class, cấu hình có thể là:

Tệp **AspectJAsyncConfig.java**:

~~~java
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync(mode = AdviceMode.ASPECTJ)
public class AspectJAsyncConfig {

    @Bean(name = "exportExecutor")
    public ThreadPoolTaskExecutor exportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("export-");
        return executor;
    }
}
~~~

Chỉ chọn **một cấu hình `@EnableAsync` phù hợp** trong ApplicationContext cho kịch bản thử nghiệm; không nạp song song hai class config mẫu như hai cấu hình cạnh tranh. `@EnableAsync(mode=ASPECTJ)` có ý nghĩa sau khi đã có **Spring aspect + weaving thực sự**. Nếu `ExportService` đã được weave, `begin("B")` có thể dẫn đến advice ở execution của `perform` mặc dù gọi trong cùng bean.

Không có proxy **cho phần async interception theo AspectJ mode**, nhưng các tính năng Spring AOP khác vẫn có thể tạo proxy độc lập. Bật AspectJ cho `@Async` không có nghĩa `@Transactional` hoặc toàn bộ các advice khác cũng tự chuyển sang AspectJ mode.

Tương tự, cấu hình:

~~~java
@EnableAsync(mode = AdviceMode.ASPECTJ, proxyTargetClass = true)
~~~

là **một fragment annotation**; `proxyTargetClass` bị bỏ qua cho AspectJ mode, không tạo ra “weaving mạnh hơn”. Muốn xác minh AspectJ, hãy kiểm tra build agent, weave info và kết quả integration test, không dựa vào một flag proxy.

## 9. Ví dụ Java 17+ độc lập: self-call vượt qua wrapper

Ví dụ này minh họa **nguyên lý đường gọi proxy bằng một wrapper Java thuần**, không phụ thuộc Spring/AspectJ. Wrapper gửi `work()` qua executor nếu caller gọi qua nó, nhưng `Target.entry()` tự gọi `work()` trên `this` nên bỏ qua wrapper. Đây không phải cài đặt Spring AOP đầy đủ; nó chỉ minh họa vì sao self-invocation có kết quả khác.

Tệp **ProxyBoundaryDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ProxyBoundaryDemo {
    interface WorkPort {
        void entry();
        void work();
    }

    static final class Target implements WorkPort {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Thread> executedBy = new AtomicReference<>();

        @Override
        public void entry() {
            work(); // this.work(), không đi qua wrapper
        }

        @Override
        public void work() {
            executedBy.set(Thread.currentThread());
            done.countDown();
        }
    }

    static final class DispatchWrapper implements WorkPort {
        private final Target target;
        private final ExecutorService executor;

        DispatchWrapper(Target target, ExecutorService executor) {
            this.target = target;
            this.executor = executor;
        }

        @Override
        public void entry() {
            target.entry();
        }

        @Override
        public void work() {
            executor.execute(target::work);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Thread caller = Thread.currentThread();

        try {
            Target first = new Target();
            WorkPort wrapper1 = new DispatchWrapper(first, executor);

            wrapper1.work(); // đi qua wrapper -> executor
            if (!first.done.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Async task timed out");
            }
            System.out.println(
                    "externalUsesWorker=" + (first.executedBy.get() != caller));

            Target second = new Target();
            WorkPort wrapper2 = new DispatchWrapper(second, executor);

            wrapper2.entry(); // wrapper -> target.entry -> this.work
            if (!second.done.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Self-call timed out");
            }
            System.out.println(
                    "selfCallUsesCaller=" + (second.executedBy.get() == caller));
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch/chạy với Java 17 trở lên:

~~~bash
javac ProxyBoundaryDemo.java
java ProxyBoundaryDemo
~~~

Stdout của lần chạy thành công:

~~~text
externalUsesWorker=true
selfCallUsesCaller=true
~~~

Giải thích:

1. `wrapper1.work()` gọi `executor.execute(target::work)`; method `Target.work()` được chạy trên worker thread khác thread main. `CountDownLatch` chứng minh task đã chạy trước khi đọc kết quả.
2. `wrapper2.entry()` chỉ ủy quyền tới `target.entry()`. Trong `entry()`, lời gọi `work()` trực tiếp trên `Target`, không hề đi qua `DispatchWrapper.work()`; nó chạy trên caller.
3. Không dùng `Thread.sleep` để đoán lịch thread. Chúng ta so sánh **đối tượng Thread** và chờ latch, nên kết quả không phụ thuộc tên thread hoặc độ nhanh của máy.

AspectJ sau khi weave có thể đặt interception tại `execution` của `work()`, khác với mô hình wrapper. Do đó cần một **integration test riêng với Spring + weaver** để xác nhận behavior thực, không dùng ví dụ Java thuần này làm bằng chứng đã cấu hình AspectJ thành công.

## 10. Kiểm thử self-invocation thật với Spring

Một test đúng nên có hai tình huống tách biệt: **PROXY mode** và **ASPECTJ mode đã weave**. Không cố bật/tắt mode liên tục trong cùng một ApplicationContext đang chạy.

Tệp **AsyncProbeService.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncProbeService {

    public CompletableFuture<String> callInside(
            CountDownLatch entered, CountDownLatch release) {
        return asyncWork(entered, release); // self-invocation
    }

    @Async("exportExecutor")
    public CompletableFuture<String> asyncWork(
            CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try {
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Wait timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", ex);
        }
        return CompletableFuture.completedFuture(
                Thread.currentThread().getName());
    }
}
~~~

Đây là **Spring service test**, cần ApplicationContext, không có `main` riêng. Sử dụng hai latch truyền qua từng lần gọi để không lưu trạng thái mutable của một test trong singleton bean.

### Test PROXY mode

- Bean/test khác inject `AsyncProbeService`; gọi `asyncWork(entered, release)` từ ngoài qua proxy sẽ dispatch tới executor nếu configured đúng.
- Với `callInside(entered, release)`, body `asyncWork` được gọi trực tiếp trong cùng bean. Vì nó đang chờ `release` nên lời gọi `callInside` **bị chặn** cho đến khi test thả latch.
- Để tránh chính test thread tự bị chặn, hãy gọi `callInside` từ một test helper thread, chờ `entered` rồi thả `release`. Kiểm tra tên thread trả về là helper thread.
- Dùng timeout ở mọi lần chờ và dọn helper executor trong `finally`.

### Test ASPECTJ mode đã weave

- Tạo ApplicationContext khác với `@EnableAsync(mode=ASPECTJ)`; bảo đảm `AsyncProbeService` đã weave bằng CTW/LTW và `exportExecutor` có capacity.
- Gọi `callInside(entered, release)` từ test thread. Execution của `asyncWork` được weave nên có thể dispatch tới executor và trả `CompletableFuture` trước khi body thoát khỏi `release.await()`.
- Chờ `entered.await(timeout)` để biết worker đã vào body; trước khi thả `release`, future chưa nên hoàn thành.
- Thả `release`, lấy kết quả bằng `future.get(timeout)`, so sánh thread name với thread gọi; tốt hơn có thể truyền recorder/thread identity qua test fixture.
- Bật weave-info trong môi trường test hoặc kiểm tra class woven để chứng minh class/method thực sự được advice.

**Tránh nhầm kết quả:** một executor kiểu `SyncTaskExecutor` hoặc `CallerRunsPolicy` khi bão hòa có thể chạy body trực tiếp trên caller **dù advice AspectJ đã hoạt động**. Do đó test dùng executor bất đồng bộ thật, queue còn chỗ, và tách rõ “advice đã interception” khỏi “thread nào thực thi”.

## 11. Những điều AspectJ không tự giải quyết

### Kiểu trả về và exception

Method mang `@Async` phải theo chữ ký được hỗ trợ: `void` hoặc `Future` phù hợp, bao gồm `CompletableFuture`. Trong worker, target method có thể trả `CompletableFuture.completedFuture(value)`; caller giữ Future do advice quản lý. Một method trả `String` rồi annotate `@Async` không tự trở thành method bất đồng bộ hợp lệ chỉ nhờ weaving. `AnnotationAsyncExecutionAspect` còn xác định lỗi/cảnh báo weaving cho các chữ ký không phù hợp.

Với `@Async void`, exception trong body thường đi tới `AsyncUncaughtExceptionHandler`. Với `Future`/`CompletableFuture`, caller có đường quan sát exceptional completion. Nếu executor từ chối ngay khi submit, `TaskRejectedException` có thể xuất hiện tại ranh giới gửi. Không được nhầm submission rejection với body failure.

### Executor overload và shutdown

Cả PROXY và ASPECTJ đều cần executor thực tế. Khi queue đầy, `AbortPolicy` báo rejection; `CallerRunsPolicy` có thể chạy trên caller; một chính sách discard có thể làm mất task im lặng. AspectJ không tự tăng `maxPoolSize`, không tự retry và không hứa một lời gọi async luôn trả rất nhanh.

### Transaction và thread context

Async work trên thread khác không tự thừa hưởng transaction, security context, MDC hay `ThreadLocal` từ caller. Nếu gọi việc trước khi transaction commit, worker có thể thấy dữ liệu chưa sẵn sàng hoặc request sau đó rollback. Cần xác định ranh giới transaction và cơ chế after-commit/outbox khi yêu cầu.

Bật AspectJ cho `@Async` **không** tự chuyển `@Transactional` sang AspectJ mode; từng hệ thống advice có cấu hình riêng. Nếu ứng dụng dùng nhiều advice, cần test hành vi composition/thứ tự và thread context theo đúng kịch bản.

### Độ bền và nhiều replica

Một task đang chờ trong RAM queue của một JVM có thể mất khi tiến trình crash. AspectJ weaving không phải hàng đợi bền vững và không giúp các replica tự chuyển giao job. Với công việc phải hoàn thành sau restart, dùng job store/message queue, retry, idempotency và trạng thái đủ quan sát.

## 12. Chi phí vận hành và tương thích phiên bản

Khi chọn weaving, phải lập kế hoạch cho:

- **Version matrix:** AspectJ compiler/weaver phải đọc được bytecode của JDK đang dùng; Spring modules và `spring-aspects` phải tương thích. Nâng JDK có thể cần nâng AspectJ.
- **Build reproducibility:** ở CTW, artifact release phải đúng là class đã weave; không để bước đóng gói dùng nhầm bản biên dịch Java thuần.
- **Classloader:** ở LTW, agent/weaver phải có hiệu lực khi class cần can thiệp đang nạp; Boot executable JAR, WAR, app server và test runner có thể có cách classloading khác nhau.
- **Phạm vi weave:** hạn chế package cần thiết khi có thể, tránh chạm quá nhiều class; nhưng không loại nhầm class mục tiêu.
- **Instrumentation khác:** code coverage, profiler, tracing agent và bytecode enhancer khác có thể cùng tham gia biến đổi class; cần thử nghiệm phối hợp.
- **Startup/debug:** LTW có thể tăng thời gian nạp class, CTW tăng thời gian build; stack trace và bytecode sau weave có thể khó debug hơn.
- **Deployment/rollback:** cần giữ cùng JVM flags, dependencies và artifacts ở CI, staging, production; nên có kiểm thử smoke khi cập nhật runtime.

Không dựa vào việc thấy `spring-aspects` trong dependency tree để kết luận đã weave. Dùng kết hợp **weave-info + integration test + kiểm tra runtime artifact**.

## 13. Lỗi và ngộ nhận thường gặp

1. **Chỉ thêm `mode=ASPECTJ` rồi cho rằng xong.** Phải có aspect và weaving thật.
2. **Chỉ thêm `spring-aspects`.** Aspect definition trong classpath không tự sửa bytecode.
3. **Chỉ thêm `aspectjweaver` dependency nhưng không chạy agent/CTW.** Thư viện chưa tự được kích hoạt.
4. **Dùng `@EnableAspectJAutoProxy` như một công tắc weaving.** Đó là Spring proxy-based AOP trong trường hợp thông thường.
5. **Bật `proxyTargetClass=true` để sửa self-call.** JDK/CGLIB đều không tự thay đổi đường gọi `this.method()` trên target.
6. **Dùng `javac` thay `ajc` ở pipeline CTW.** Không tạo class đã weave.
7. **Đặt `-javaagent` sai vị trí trong lệnh JVM.** Startup option phải đứng trước `-jar` hoặc tên main class.
8. **Quên `spring-aspects` runtime hoặc aspect metadata.** Weaver không thấy Spring async aspect cần áp.
9. **Sai package/classloader hoặc include/exclude rule.** Class đích không bị weave.
10. **Class được nạp trước khi LTW hoạt động.** Không thể coi việc đổi cấu hình sau đó là retroactive weaving.
11. **Đặt annotation chỉ trên interface mà không kiểm chứng execution của implementation.** Với AspectJ nên annotate trực tiếp implementation method/class khi cần.
12. **Mong private/final/constructor/lifecycle callback tự được hỗ trợ vô điều kiện.** Weaving rộng hơn proxy nhưng pointcut và hợp đồng `@Async` vẫn giới hạn.
13. **Cho rằng AspectJ tự tạo worker pool.** Executor vẫn phải được cấu hình, sizing và theo dõi overload.
14. **Cho rằng @Transactional tự đổi mode cùng @Async.** Mỗi advice có đường cấu hình riêng.
15. **Kiểm thử bằng `Thread.sleep` hoặc `isDone()` trên task cực nhanh.** Dùng latch và thread identity.
16. **Dùng nhiều config `@EnableAsync` cạnh tranh trong cùng test.** Kiểm tra một mode rõ ràng trên từng context.
17. **Bỏ qua `CallerRunsPolicy` khi đo thread identity.** Advice thành công vẫn có thể chạy task trên caller.
18. **Đổi sang AspectJ chỉ để chữa một self-invocation đơn giản.** Tách bean thường dễ bảo trì hơn.
19. **Không thử cấu hình startup trên production packaging.** Agent/classloader khác có thể làm LTW không hoạt động.
20. **Đồng nhất `@Async` với durable job.** Weaving không bảo đảm xử lý sau crash/restart.

## 14. Quy trình quyết định thực tế

Hãy đi từ phương án ít phức tạp nhất:

1. **Chứng minh vấn đề.** Xác định có thực sự self-invocation, method không đi qua proxy, hay chỉ là executor policy khiến code chạy trên caller.
2. **Kiểm tra Spring-managed bean.** Method được gọi từ reference inject không? Có `@EnableAsync` trong context đúng không? Method có kiểu trả về đúng không?
3. **Đánh giá refactor.** Tách method async sang bean khác; hoặc dùng explicit `TaskExecutor` ngay tại nơi cần dispatch.
4. **Định nghĩa yêu cầu nâng cao.** Nếu cần interception nhiều self-call ở lớp không thể refactor hợp lý, mới cân nhắc AspectJ.
5. **Chọn CTW hoặc LTW.** CTW ưu tiên artifact được cố định khi build; LTW phải quản lý agent/classloader/runtime.
6. **Kiểm soát dependencies.** Dùng `spring-aspects` cùng phiên bản Spring tương thích, AspectJ weaver/compiler phù hợp bytecode level.
7. **Kiểm chứng weaving thật.** Dùng weave-info và integration test của cả external call lẫn self-call; không chỉ đọc annotation.
8. **Đo vận hành.** Startup, memory, thread pool, queue, p95/p99, lỗi submit/worker, tương tác với transaction và tracing.
9. **Có đường rollback.** Giữ cách cấu trúc bean đơn giản đủ để quay về proxy mode nếu việc vận hành weaving không phù hợp.

| Bối cảnh | Khuyến nghị |
| --- | --- |
| Các bean gọi nhau qua Spring injection | Giữ `AdviceMode.PROXY` |
| Chỉ một self-call không async | Tách bean/explicit executor trước |
| Có hàng loạt self-call cần interception, refactor không khả thi | Cân nhắc ASPECTJ + weaving + integration test |
| Không kiểm soát được JVM agent và build pipeline | Tránh phụ thuộc vào ASPECTJ mode |
| Vấn đề thật là queue đầy/latency cao | Thiết kế lại executor/backpressure, không đổi advice mode |
| Cần xử lý job bền vững qua restart | Job table/durable queue, không phải AspectJ |
| Có nhiều bytecode agents hoặc classloader đặc thù | Thử nghiệm triển khai trước khi quyết định |
| Muốn code dễ hiểu và dễ debug | Proxy mode với ranh giới bean rõ ràng thường thuận lợi |

## 15. Câu hỏi tự kiểm tra

1. `@EnableAsync` không ghi `mode` thì mặc định dùng gì?
2. Vì sao `this.perform()` trong cùng bean có thể không chạy async dù `perform` có `@Async`?
3. Bật `proxyTargetClass=true` có sửa self-invocation không? Tại sao?
4. Tại sao `@EnableAspectJAutoProxy` không đồng nghĩa với compile-time/load-time weaving?
5. Những điều kiện nào phải cùng đúng để `@EnableAsync(mode=ASPECTJ)` thực sự interception lời gọi nội bộ?
6. CTW khác LTW ở thời điểm nào và cần chú ý class nạp trước lúc weaver hoạt động ra sao?
7. `spring-aspects` và `aspectjweaver` chỉ nằm trong dependency tree có đủ để khẳng định self-call được weave không?
8. Vì sao `@Async` đặt trên implementation method rõ ràng hơn khi dùng AspectJ?
9. Dùng latch và thread identity để kiểm tra self-call với proxy/AspectJ như thế nào?
10. Tại sao AspectJ không tự thay đổi `@Transactional`, executor policy, hoặc bảo đảm xử lý qua restart?
11. Khi `CallerRunsPolicy` kích hoạt, một method đã weave có thể vẫn chạy trên caller không?
12. Nếu chỉ có một vấn đề self-invocation, lựa chọn nào thường ít rủi ro vận hành hơn AspectJ?

## 16. Tài liệu tham khảo

- [Spring Framework — @EnableAsync API: PROXY và ASPECTJ](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — Using AspectJ with Spring Applications](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html)
- [Spring Framework — AOP Concepts](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html)
- [Spring source — AnnotationAsyncExecutionAspect.aj](https://github.com/spring-projects/spring-framework/blob/main/spring-aspects/src/main/java/org/springframework/scheduling/aspectj/AnnotationAsyncExecutionAspect.aj)
- [Spring source — spring-aspects META-INF/aop.xml](https://github.com/spring-projects/spring-framework/blob/main/spring-aspects/src/main/resources/META-INF/aop.xml)
- [AspectJ — Official Documentation](https://eclipse.dev/aspectj/doc/latest/index.html)

**Ghi nhớ:** `AdviceMode.PROXY` xử lý lời gọi qua Spring proxy nên self-invocation thường bỏ qua `@Async`. `AdviceMode.ASPECTJ` có thể can thiệp tại method execution kể cả self-call **nếu bytecode đã được weave**. Đây là giải pháp nâng cao đòi hỏi AspectJ tooling, Spring aspects, runtime/build configuration và integration test; nếu tách bean giải quyết được vấn đề, phương án proxy vẫn thường rõ ràng và nhẹ hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
