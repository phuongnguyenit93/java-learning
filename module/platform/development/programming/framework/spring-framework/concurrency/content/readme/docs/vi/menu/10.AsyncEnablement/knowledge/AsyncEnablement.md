<a id="back-to-top"></a>

# Bật @Async

## Menu
- [Khai báo @EnableAsync trong lớp cấu hình](#async-enablement-001)
- [Bean phải được container quản lý để annotation được xử lý](#async-enablement-002)
- [Đặt @Async trên phương thức hoặc lớp](#async-enablement-003)
- [Kiểu trả về void, Future và CompletableFuture](#async-enablement-004)
- [Giới hạn dùng @Async trên phương thức trong lớp @Configuration](#async-enablement-005)
- [Chọn bộ thực thi mặc định bằng AsyncConfigurer](#async-enablement-006)
- [Cơ chế tìm TaskExecutor hoặc bean taskExecutor khi không cấu hình tường minh](#async-enablement-007)
- [Chỉ định bộ thực thi theo tên hoặc qualifier trong @Async](#async-enablement-008)
- [Vai trò của AsyncAnnotationBeanPostProcessor](#async-enablement-009)

## <a id="async-enablement-001">Khai báo @EnableAsync trong lớp cấu hình</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo @EnableAsync trong lớp cấu hình

## 1. Mục tiêu của bài học

Trong Spring Framework, thêm `@Async` vào một phương thức **chưa đủ** để khẳng định lời gọi được chạy bất đồng bộ. Ứng dụng phải bật cơ chế xử lý annotation async trong **Spring application context**. Cách khai báo phổ biến là đặt `@EnableAsync` trên một lớp `@Configuration`.

Sau bài học, bạn cần làm được các việc:

1. Khai báo đúng `@Configuration` + `@EnableAsync` và các import cần thiết.
2. Giải thích thứ gì được bật: hạ tầng intercept `@Async`, **không phải** một lời hứa tạo thread pool có giới hạn.
3. Định nghĩa bean `ThreadPoolTaskExecutor` và chỉ định bean đó bằng `@Async("...")`.
4. Hiểu tại sao phải gọi phương thức qua Spring-managed proxy; nhận diện self-invocation, đối tượng tự `new` và giới hạn proxy.
5. Phân biệt cấu hình tối thiểu, cấu hình mặc định của Spring Framework và hỗ trợ auto-configuration có điều kiện của Spring Boot.
6. Nhận đúng `Future`/`CompletableFuture`, xử lý exception, kiểm soát thread và vòng đời.
7. Chạy một ví dụ Java 17+ có thể biên dịch, và xác minh rằng phương thức thực sự được thực thi trong executor được chỉ định.

**Phạm vi:** bài tập trung vào hành động **khai báo `@EnableAsync` trong lớp cấu hình** và điều kiện để khai báo này có tác dụng; không biến nó thành bài tổng quan về mọi mô hình concurrency hoặc lập lịch.

## 2. Cấu hình tối thiểu của Spring Framework

`@EnableAsync` nằm trong package `org.springframework.scheduling.annotation`. `@Configuration` nằm trong `org.springframework.context.annotation`.

Ví dụ tối thiểu:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
}
```

Đây là một lớp cấu hình hợp lệ khi nó được **đăng ký vào Spring application context** (qua component scanning, `@Import`, hoặc đăng ký trực tiếp). Riêng đoạn mã này chưa phải một chương trình độc lập: cần một application context và các bean chứa phương thức `@Async`.

Tác dụng của từng annotation:

- `@Configuration` cho Spring biết lớp này cung cấp cấu hình bean; thường kết hợp với các method `@Bean`.
- `@EnableAsync` import hạ tầng hỗ trợ asynchronous method execution. Trong chế độ mặc định `PROXY`, Spring đăng ký các thành phần thích hợp để intercept các phương thức được đánh dấu `@Async` trên **bean được Spring quản lý**.
- `@Async` được đặt lên method hoặc class cần chạy bất đồng bộ; invocation thích hợp sẽ được chuyển thành task cho executor.
- Việc **bật xử lý annotation** và **chọn executor thực sự** là hai trách nhiệm riêng.

Không cần đặt `@EnableAsync` trên mọi service. Thông thường một lớp cấu hình được nạp trong context thích hợp là đủ để kích hoạt cơ chế cho các bean thuộc context đó.

### 2.1. Hình dung luồng gọi

```text
Spring ApplicationContext
   |
   +-- nhận lớp @Configuration + @EnableAsync
   |         |
   |         +-- đăng ký hạ tầng async/proxy
   |
   +-- quản lý WorkService bean
   |         |
Caller --> Spring proxy --> @Async interceptor
                              |
                              +-- chọn Executor
                              |
                              +-- submit task
                                      |
                                      +-- thực thi method ở worker thread
                                      |
                                      +-- hoàn thành Future/CompletableFuture
```

Hình vẽ mô tả đường gọi khi proxy hoạt động bình thường. Một lời gọi không đi qua proxy, chẳng hạn `this.work()` trong chính bean, không tự nhiên trở thành bất đồng bộ chỉ vì method có annotation.

### 2.2. Vì sao ứng dụng cần cả hai thành phần?

Hai đoạn sau không tương đương:

```java
// Chỉ khai báo method: chưa chứng minh async infrastructure được bật.
@Async
public CompletableFuture<String> work() {
    return CompletableFuture.completedFuture("ok");
}
```

```java
// Bật hạ tầng cho context chứa các bean phù hợp.
@Configuration
@EnableAsync
class AsyncConfig {
}
```

Một cấu hình `@EnableAsync` không làm **mọi** method trong ứng dụng tự chạy async. Chỉ những lời gọi phù hợp với annotation và interception mới được điều phối. Ngược lại, nếu method có `@Async` nhưng context không bật async, annotation thường không đổi hành vi lời gọi trong cấu hình Framework thông thường.

## 3. Ví dụ hoàn chỉnh: tạo cấu hình, executor và service

Bản mẫu sau dùng **Java 17+, Spring Framework 6.2.14 và Maven**; chạy độc lập mà không cần Spring Boot. Nó khai báo `@EnableAsync` trên lớp cấu hình, tạo executor hữu hạn và thực hiện một lời gọi qua Spring-managed bean.

Cấu trúc thư mục:

```text
enable-async-demo/
├── pom.xml
└── src/main/java/demo/EnableAsyncDemo.java
```

### 3.1. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>enable-async-demo</artifactId>
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

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.2. Tệp EnableAsyncDemo.java

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

public class EnableAsyncDemo {
    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class AsyncConfig {
        @Bean("lessonExecutor")
        public ThreadPoolTaskExecutor lessonExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setThreadNamePrefix("lesson-worker-");
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(4);
            executor.setQueueCapacity(16);
            return executor;
        }

        @Bean
        public ReportService reportService() {
            return new ReportService();
        }
    }

    public static class ReportService {
        @Async("lessonExecutor")
        public CompletableFuture<Boolean> onWorker(String callerThread) {
            String current = Thread.currentThread().getName();
            boolean result =
                    current.startsWith("lesson-worker-")
                    && !current.equals(callerThread);
            return CompletableFuture.completedFuture(result);
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             AsyncConfig.class)) {
            ReportService service = context.getBean(ReportService.class);
            String callerThread = Thread.currentThread().getName();

            CompletableFuture<Boolean> result =
                    service.onWorker(callerThread);

            System.out.println("async-on-configured-pool="
                    + result.get(5, TimeUnit.SECONDS));
        }
    }
}
```

### 3.3. Biên dịch và chạy

Tại thư mục chứa `pom.xml`:

```bash
mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=demo.EnableAsyncDemo
```

Kết quả từ dòng `System.out.println` trong điều kiện bình thường:

```text
async-on-configured-pool=true
```

Maven hoặc môi trường có thể in thêm thông báo. Ví dụ cố ý kiểm tra **thread name** thay vì suy đoán từ thời gian chạy; `result.get(5, TimeUnit.SECONDS)` đợi kết quả tối đa năm giây và trả lỗi nếu task thất bại hoặc quá thời gian.

### 3.4. Giải thích từng bước

1. `AnnotationConfigApplicationContext` đăng ký lớp `AsyncConfig`, tức lớp chứa `@Configuration` và `@EnableAsync` đã thực sự được Spring nạp.
2. Bean `lessonExecutor` là `ThreadPoolTaskExecutor`. Các giá trị core=2, max=4, queueCapacity=16 chỉ minh họa một pool hữu hạn, không phải cấu hình chung tối ưu cho mọi tải.
3. Bean `reportService` được Spring tạo. Khi cần, Spring áp dụng async proxy cho phương thức `onWorker(...)`.
4. Client lấy bean từ context, **không** gọi `new ReportService()`, vì object tự tạo sẽ không nhận interception.
5. `@Async("lessonExecutor")` chỉ rõ tên executor được chọn thay vì dựa vào lookup mặc định.
6. Worker lấy tên thread đang chạy method. Prefix `lesson-worker-` cùng việc thread khác caller xác nhận đường chạy dự kiến.
7. Method trả `CompletableFuture.completedFuture(...)` ở **bên trong worker**. Interceptor cung cấp future cho caller để theo dõi hoàn tất.
8. Khi đóng context, Spring quản lý lifecycle của bean executor đã đăng ký; code caller không tự gọi `shutdown()` trên executor dùng chung giữa các service.

Nếu đổi `@Async("lessonExecutor")` thành `@Async`, cần xem quy tắc chọn executor mặc định của context. Nếu xóa `@EnableAsync` và không có cấu hình async khác, lời gọi có thể chạy trực tiếp trên caller thread; khi đó phép kiểm tra prefix sẽ thất bại.

## 4. Khai báo trong ứng dụng Spring Boot

Spring Boot dùng Spring Framework làm nền. Trong một ứng dụng Boot, bạn có thể đặt `@EnableAsync` trực tiếp trên lớp main hoặc trong một lớp `@Configuration` riêng.

### 4.1. Trên lớp main

Đây là **fragment ứng dụng Spring Boot**, cần Spring Boot dependencies, không phải chương trình độc lập chỉ bằng `javac`:

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

`@SpringBootApplication` không phải cách viết đồng nghĩa với `@EnableAsync`. Thông thường, bạn vẫn phải bật async annotation processing có chủ đích khi muốn dùng `@Async`. Boot có thể tạo executor mặc định bằng auto-configuration **có điều kiện**, nhưng **có executor** và **bật annotation interceptor** là hai việc khác nhau.

### 4.2. Trên một lớp cấu hình riêng

Phương án phù hợp khi muốn tách cấu hình concurrency khỏi điểm khởi động:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncInfrastructureConfig {
}
```

Cần bảo đảm `AsyncInfrastructureConfig` nằm trong phạm vi component scan hoặc được import vào context:

```java
@SpringBootApplication
@Import(AsyncInfrastructureConfig.class)
public class Application {
    // Các import và phương thức main được khai báo trong project.
}
```

Đây cũng là fragment; `@Import` là lựa chọn khi cấu hình không được component scan nạp. **Không** đặt cùng cấu hình nhiều nơi nếu điều đó làm người đọc nhầm context hoặc tạo bean trùng lặp. Với ứng dụng có nhiều context (ví dụ parent và child), `@EnableAsync` có hiệu lực ở context nơi nó được khai báo; không suy ra tự động bao phủ mọi context con.

### 4.3. Boot tự cấu hình executor không có nghĩa tự cấu hình mọi bean tùy chỉnh

Một project Spring Boot có thể dùng các thuộc tính như:

```yaml
spring:
  task:
    execution:
      thread-name-prefix: app-async-
      pool:
        core-size: 4
        max-size: 8
        queue-capacity: 64
```

Những thuộc tính đó áp dụng khi loại **Boot-managed executor** tương ứng thực sự được auto-configure và các điều kiện phù hợp. Nếu bạn khai báo `@Bean("lessonExecutor") ThreadPoolTaskExecutor` và đặt setter trực tiếp, Boot không tự buộc bean tùy chỉnh này nhận mọi giá trị `spring.task.execution.*`. Thêm bean `Executor` tùy chỉnh còn có thể ảnh hưởng điều kiện để Boot tạo executor mặc định; hãy xác minh danh sách bean theo đúng phiên bản Boot.

Kết luận của phần này: trong **Spring Framework thuần** hoặc **Spring Boot**, `@EnableAsync` vẫn làm nhiệm vụ **bật async interception**. Boot chỉ bổ sung hệ thống auto-configuration tài nguyên thực thi với các mặc định có điều kiện.

## 5. @EnableAsync thực sự bật cơ chế nào?

Trong chế độ phổ biến `AdviceMode.PROXY`, việc bật hỗ trợ sẽ đăng ký hạ tầng async advisor/interceptor và post-processor thích hợp. Khi một bean được quản lý có phương thức được nhận diện là async, lời gọi **qua proxy** sẽ được intercept trước khi chạy logic thật.

Các khái niệm cần phân biệt:

| Khái niệm | Vai trò |
| --- | --- |
| `@Configuration` | Định nghĩa lớp cấu hình Spring |
| `@EnableAsync` | Kích hoạt xử lý annotation async cho context |
| `@Async` | Đánh dấu method/class mà lời gọi phù hợp cần điều phối bất đồng bộ |
| `AsyncConfigurer` | Tùy chỉnh executor mặc định và handler exception của method async trả `void` |
| `@Async("executorName")` | Chọn executor bằng tên bean hoặc qualifier |
| `ThreadPoolTaskExecutor` | Một implementation executor quản lý worker/queue |
| `CompletableFuture<T>` | Handle quan sát trạng thái hoàn thành và kết quả của lời gọi async |
| Spring AOP proxy | Lớp trung gian cần thiết trong advice mode mặc định |
| `AdviceMode.ASPECTJ` | Chế độ weaving nâng cao; yêu cầu setup AspectJ bổ sung |

Đừng nhầm `@EnableAsync` với `@EnableScheduling`: cái thứ nhất phục vụ `@Async`, cái thứ hai phục vụ các method `@Scheduled`. Nếu ứng dụng cần cả hai, có thể khai báo cả hai trên một lớp cấu hình, nhưng chúng bật hai loại xử lý annotation khác nhau.

### 5.1. Vì sao gọi qua proxy quan trọng?

Trong chế độ mặc định, Spring thường tạo proxy bao quanh bean async. Với method phù hợp, caller gọi **proxy**, proxy submit task vào executor, sau đó method gốc chạy trong worker.

```text
Bean A ---> proxy(Bean B) ---> executor.submit ---> Bean B.work()
Bean B ---> this.work() ---------------------------> Bean B.work()
              (không qua proxy)
```

Dòng thứ hai là **self-invocation**: method trong chính Bean B gọi method khác của cùng instance bằng `this`, bỏ qua proxy trong mặc định `PROXY`. Do đó, dù method đích có `@Async`, nó có thể chạy đồng bộ trên thread hiện tại.

Các cách giải quyết thích hợp:

- **Ưu tiên:** tách phương thức async sang bean khác, inject và gọi qua Spring-managed bean.
- Có thể thiết kế caller dùng service interface/proxy nếu phù hợp cấu trúc dự án.
- Với nhu cầu đặc thù, cân nhắc mode AspectJ có weaving phù hợp; đó là thiết lập riêng, không chỉ đổi enum là xong.
- Không gợi ý tự inject chính mình như giải pháp mặc định, vì dễ gây vòng phụ thuộc và làm API khó hiểu.

### 5.2. Bean nào đủ điều kiện được xử lý?

Phương thức async nên là một method được gọi qua Spring-managed proxy. Cần kiểm tra:

- **Đối tượng có thuộc container không?** `new MyService()` do application tự tạo không được Spring tự áp dụng proxy.
- **Lời gọi có đi qua proxy không?** `this.method()` thường không.
- **Proxy có thể intercept method không?** Với class-based proxy, private/final method có giới hạn; method public là cách rõ ràng và ít rủi ro nhất cho bài học.
- **Annotation có nằm ở nơi framework nhận diện không?** Đặt trên method/class thực thi phù hợp; nếu dùng interface/proxy, kiểm tra theo cấu trúc thực tế.
- **Context có bật async không?** `@EnableAsync` phải được nạp trong đúng application context.

Không nên kết luận “Spring bỏ qua `@Async`” chỉ vì không thấy log thread mới: còn phải xem executor đã chọn, invocation path và giới hạn phương thức.

## 6. Những thuộc tính quan trọng của @EnableAsync

Cú pháp thông thường không cần đối số:

```java
@Configuration
@EnableAsync
public class AsyncConfig {
}
```

Với yêu cầu nâng cao, annotation cho phép điều chỉnh cách áp dụng advice:

| Thuộc tính | Ý nghĩa | Khuyến nghị khi học |
| --- | --- | --- |
| `mode` | Cách áp dụng async advice: `PROXY` (mặc định) hoặc `ASPECTJ` | Dùng `PROXY` trừ khi đã có lý do và hạ tầng weaving |
| `proxyTargetClass` | Với `PROXY`, ưu tiên class-based proxy thay vì interface-based proxy | Không bật bừa bãi; có tác động lên các proxy khác trong context |
| `order` | Thứ tự đăng ký post-processor/advisor async trong chuỗi proxy | Giữ mặc định nếu không có nhu cầu AOP phối hợp đặc biệt |
| `annotation` | Chỉ định loại annotation async tùy chỉnh ngoài các annotation mặc định | Chỉ cần ở hệ thống có convention riêng |

Ví dụ minh họa cấu hình tường minh chế độ mặc định:

```java
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync(mode = AdviceMode.PROXY, proxyTargetClass = false)
public class AsyncConfig {
}
```

Đây là một **fragment lớp cấu hình**; không phải yêu cầu bắt buộc phải viết `mode`/`proxyTargetClass`. Giá trị mặc định đã tương đương. Lưu ý việc Boot hoặc một cơ chế AOP khác có thể chọn loại proxy theo cấu hình của nó; không dùng thuộc tính này để suy ra chắc chắn object cuối cùng là JDK proxy hay class proxy trong mọi ứng dụng.

### 6.1. Khi nào cần AspectJ?

`@EnableAsync(mode = AdviceMode.ASPECTJ)` chỉ có ý nghĩa khi cấu hình đầy đủ AspectJ weaving và các dependency cần thiết. Nó có thể xử lý trường hợp local invocation mà proxy bỏ qua, nhưng tăng độ phức tạp build/runtime và có yêu cầu môi trường riêng. Đừng bật chỉ để sửa một self-invocation đơn giản; tách bean là giải pháp dễ hiểu và dễ test hơn.

## 7. Khai báo executor cùng lớp cấu hình

`@EnableAsync` chỉ bật xử lý; executor quyết định thread, queue, giới hạn tải và vòng đời. Với ứng dụng cần kiểm soát, khai báo một bean `ThreadPoolTaskExecutor`:

```java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {
    @Bean("ioExecutor")
    public ThreadPoolTaskExecutor ioExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("io-task-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}
```

Đây là **fragment cấu hình Spring**, không phải chương trình độc lập. Các số 4/8/50/20 là ví dụ để hiểu cấu trúc; phải đo tải thật, CPU, downstream connection limit, deadline và thời gian chờ để chọn trong production.

Để gắn một method với pool này:

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    @Async("ioExecutor")
    public CompletableFuture<String> prepare(String emailId) {
        return CompletableFuture.completedFuture(
                "prepared:" + emailId);
    }
}
```

Đây là **fragment dự án Spring**: method mẫu chỉ nối chuỗi nên trong thực tế không cần async. Nó minh họa cách chọn executor theo **bean name**: `@Async("ioExecutor")`. Đảm bảo không có nhiều bean trùng tên trong context và caller không gọi method trực tiếp từ bên trong chính `EmailService`.

### 7.1. Số luồng và queue không được để mặc định một cách vô thức

Với `ThreadPoolTaskExecutor` sử dụng underlying `ThreadPoolExecutor`:

1. Pool có thể tạo worker cho task mới khi đang dưới `corePoolSize`.
2. Khi đã đủ core worker, task thường được đưa vào queue nếu queue còn chỗ.
3. Khi queue không nhận thêm và số worker chưa đến `maxPoolSize`, pool có thể mở rộng.
4. Khi không còn chỗ nhận task, chính sách rejection được áp dụng.

Vì vậy tăng `maxPoolSize` nhưng giữ queue vô hạn **không** nhất thiết làm pool dùng thêm worker. Việc `@EnableAsync` đã có không giải quyết queue pressure; phải chọn executor policy theo tải.

Khi dùng `AbortPolicy`, caller có thể nhận exception tại thời điểm submit task nếu pool không nhận. Đây là khác biệt quan trọng giữa **lỗi submit** và **lỗi nghiệp vụ bên trong task** (thường quan sát qua future). Hệ thống phải xử lý cả hai.

### 7.2. Nếu không tự khai báo executor thì sao?

Với Spring Framework, async support có cơ chế tìm executor thích hợp, ví dụ dựa trên một `TaskExecutor` phù hợp hoặc một `Executor` tên quy ước `taskExecutor`; nếu không tìm thấy, có fallback kiểu `SimpleAsyncTaskExecutor` theo phiên bản/framework setup.

Fallback này không đồng nghĩa với một bounded thread pool production-ready. Không dựa vào nó cho khối lượng lớn mà không hiểu giới hạn concurrency và tài nguyên.

Spring Boot có thể auto-configure bean executor, nhưng điều kiện, tên và kiểu bean phụ thuộc phiên bản, custom executor đang có, virtual-thread mode và integration được dùng. Khi cần xác định chắc chắn method dùng pool nào, `@Async("ioExecutor")` là cách ít mơ hồ.

## 8. Chọn mặc định bằng AsyncConfigurer

Trong cùng một ApplicationContext, nếu nhiều method dùng `@Async` **không có qualifier**, `AsyncConfigurer.getAsyncExecutor()` có thể trả về một `Executor` mặc định được chọn có chủ đích. Đây là một điểm cấu hình của hạ tầng đã được bật bằng `@EnableAsync`, không thay thế cho annotation bật async và không ép các lời gọi `@Async("ioExecutor")` dùng cùng pool.

`AsyncConfigurer` còn cho phép đăng ký `AsyncUncaughtExceptionHandler` để quan sát lỗi từ method `@Async void`. Nếu muốn sử dụng `ThreadPoolTaskExecutor`, nên quản lý nó như Spring bean và xác minh vòng đời initialize/shutdown; class implementing `AsyncConfigurer` có thể được khởi tạo sớm nên tránh injection vòng hay dependency chưa sẵn sàng.

Trong bước **bật cơ chế** của bài này, chỉ cần biết chỗ cấu hình executor mặc định nằm ở đâu và không làm thay đổi tác dụng của `@EnableAsync`. Khi thiết kế nhiều executor, phải kiểm thử pool nào thực thi từng method; cấu hình core/max/queue phụ thuộc tải và giới hạn downstream.

## 9. Hợp đồng kiểu trả về và lỗi cần kiểm tra ngay khi bật async

Khi qua Spring async proxy, method hỗ trợ `void`, `Future<T>` hoặc `CompletableFuture<T>`. Method trả trực tiếp `String` hay `int` không phải hợp đồng trả giá trị được interceptor hỗ trợ. Ví dụ sau nằm trong một service bean có executor `ioExecutor`; đây là một fragment, không phải lớp chạy độc lập:

```java
@Async("ioExecutor")
public CompletableFuture<Integer> calculate(int value) {
    if (value < 0) throw new IllegalArgumentException("negative");
    return CompletableFuture.completedFuture(value * 2);
}
```

Caller nhận handle từ proxy để ghép hoặc chờ với timeout; lỗi bên trong worker của method trả Future sẽ được phản ánh qua exceptional completion. Với `void`, lỗi không có kênh Future và cần `AsyncUncaughtExceptionHandler`/log phù hợp. Rejection của executor tại lúc gửi việc lại có thể xảy ra ngay trên đường gọi, trước khi nhận handle.

Đặt `@EnableAsync` thành công không tự bảo đảm timeout, interruption, truyền transaction/context, retry hay durability. Hãy xác minh những hợp đồng đó riêng theo nhu cầu nghiệp vụ.

## 10. Các điều kiện không được bỏ qua khi khai báo @EnableAsync

### 10.1. Annotation phải được Spring nạp

Nếu đặt `@EnableAsync` lên một class không thuộc component scanning, chưa `@Import` hoặc chưa được đăng ký trực tiếp, Spring không thấy cấu hình đó. Việc file Java tồn tại trong source không chứng minh context đã bật async.

Có thể kiểm tra bằng cách lấy bean cấu hình trong context, quan sát condition/bean definitions và test method thực chạy trên executor. Trong Spring Boot, bật debug logs đúng cách giúp hiểu auto-config, nhưng còn phải xác minh annotation infrastructure của Framework.

### 10.2. Phương thức phải được gọi qua proxy

Khi chạy `AdviceMode.PROXY`, self-invocation không đi qua proxy. Xem trường hợp:

```java
@Service
class ExportService {
    public void start() {
        exportAsync(); // Lời gọi nội bộ: bypass proxy trong mode PROXY
    }

    @Async
    public void exportAsync() {
        // Code này không tự chuyển thread do start() gọi nội bộ.
    }
}
```

Đây là fragment (cần import annotations). Cách sửa thường tốt nhất: tách `exportAsync()` sang một bean khác và inject bean đó vào caller, hoặc thiết kế một entry point từ bên ngoài proxy. Không dựa vào `this.exportAsync()`.

### 10.3. Không gọi @Async trực tiếp từ @PostConstruct

`@PostConstruct` chạy trong giai đoạn lifecycle khởi tạo; không giả định async proxy đã sẵn sàng để xử lý chính một callback trên cùng bean. Không dùng `@Async` trên lifecycle callback rồi kỳ vọng nó chuyển thread. Nếu cần khởi động một tác vụ async, để một Spring-managed bean khởi tạo khác gọi qua proxy của service tại thời điểm phù hợp, hoặc dùng cơ chế application lifecycle/event đã được chọn đúng.

### 10.4. Không coi annotation là giải pháp thread-safety

`@EnableAsync` chỉ bật cách thực thi, không bảo vệ shared mutable state. Nếu service singleton chứa field mutable và hai task cùng sửa, vẫn có thể race condition. Tách immutable input/output, lock/atomic khi cần và hạn chế trạng thái request ở singleton.

### 10.5. Không mặc định thread context đi theo task

Spring transaction context, security context, `ThreadLocal`, MDC, request attributes và persistence context không mặc nhiên chuyển từ caller sang worker. Ví dụ, `@Transactional` của caller không có nghĩa mọi thao tác trong method `@Async` nằm trong cùng transaction của caller. Khi cần, mở transaction riêng ở worker, truyền immutable data/ID và cấu hình context propagation cho từng loại context được phép.

## 11. Bốn tình huống cấu hình hay gặp

| Tình huống | @EnableAsync | Executor | Kết luận |
| --- | --- | --- | --- |
| Bean có `@Async` nhưng context không bật xử lý async | Không | Có hoặc không | Annotation chưa chắc tạo async invocation |
| Context có `@EnableAsync`, service được gọi qua proxy | Có | Executor hợp lệ | Task được offload theo lựa chọn executor |
| Context có `@EnableAsync` nhưng method bị gọi qua `this` | Có | Có | Self-invocation có thể chạy đồng bộ |
| Boot auto-configure executor nhưng chưa bật annotation | Không | Có | Tồn tại executor không đồng nghĩa `@Async` đang có hiệu lực |
| `@EnableAsync` + `@Async("missingExecutor")` | Có | Không có bean được chỉ tên | Lời gọi async sẽ lỗi khi resolve executor |
| `@EnableAsync` + bean với pool/queue vô hạn | Có | Có | Vẫn có rủi ro tăng tải, bộ nhớ và độ trễ |
| `@EnableAsync` đặt ở parent context | Ở parent | Phụ thuộc context | Không suy ra hạ tầng của child context tự bật theo |
| `@EnableAsync(mode=ASPECTJ)` nhưng không có weaving thích hợp | Có trên giấy | Tùy | Không thể trông chờ advice hoạt động đúng chỉ từ annotation |

Trong từng trường hợp, hãy phân biệt **khai báo** và **đã quan sát được hành vi runtime**.

## 12. Debug: làm sao biết @EnableAsync có tác dụng?

Đây là một quy trình chẩn đoán nên thực hiện trước khi sửa thread pool:

1. **Xác định context:** lớp `@Configuration` có được scan/import không? `@EnableAsync` có nằm trong đúng context không?
2. **Xác định bean:** service được inject qua Spring hay bạn dùng `new`?
3. **Xác định call path:** caller gọi qua bean/proxy hay gọi `this.method()` trong cùng class?
4. **Xác định annotation:** method/class có `@Async`? Phương thức có phù hợp với loại proxy và kiểu trả về không?
5. **Xác định executor:** `@Async("...")` trỏ tới bean nào? Có `AsyncConfigurer` hoặc executor mặc định nào khác không?
6. **Kiểm tra thực thi:** ghi thread name của caller/worker; ưu tiên test với thread-name prefix của executor và future.
7. **Đo quá tải:** active count, pool size, queue depth, task rejected, timeout, queue wait; một method không chạy ngay có thể là do queue chứ không phải thiếu `@EnableAsync`.
8. **Kiểm tra Boot khi cần:** với Boot, xem auto-configuration conditions, profile, runtime bean type và property binding.
9. **Kiểm tra lỗi:** future có complete exceptionally không? Method `void` có handler/log thích hợp không?
10. **Kiểm tra shutdown:** task mới có bị reject vì context/executor đang đóng không?

Đặt log trong method async và in thread name là một bước tốt, nhưng không nên chỉ dựa vào việc “tên thread khác” để kết luận kiến trúc đã đúng. Đặc biệt nếu method dùng dữ liệu chung, phải test correctness lẫn lifecycle.

### 12.1. Kiểm tra proxy bằng Spring AOP

Trong code test hoặc diagnostic có Spring AOP tương ứng:

```java
Object bean = context.getBean(ReportService.class);
System.out.println(
        org.springframework.aop.support.AopUtils.isAopProxy(bean));
```

Đây là fragment, giả định `context` và `ReportService` tồn tại trong project. Giá trị `true` cho biết bean là AOP proxy, nhưng **không tự chứng minh** method đã được offload bởi đúng async advice. Hãy kết hợp với kiểm tra thread và executor. Một bean có thể có proxy cho nguyên nhân khác, ví dụ `@Transactional`.

### 12.2. Test không phụ thuộc Thread.sleep()

Nếu cần chứng minh task chạy trong worker và caller có thể tiếp tục, dùng `CountDownLatch` hoặc future với timeout, không dùng `Thread.sleep(100)` rồi đoán task sẽ hoặc chưa hoàn tất. Latch giúp điều phối thứ tự trong test, làm test ít phụ thuộc tốc độ máy.

Một test contract tốt cần xác nhận: lớp cấu hình đã được nạp; bean được lấy qua context; method `@Async("lessonExecutor")` thực sự ở worker; kết quả và exception observable; task không bị bỏ qua khi shutdown.

## 13. Bài thực hành: từ cấu hình tối thiểu đến kiểm chứng

### Bài 1 — Bật annotation

Tạo `AsyncConfig` với `@Configuration` và `@EnableAsync`. Khai báo một bean `WorkService` có method `@Async` trả `CompletableFuture<String>`. Gọi method từ một Spring-managed bean khác.

- In tên caller thread và worker thread.
- Giải thích vì sao phải gọi qua bean của Spring.
- Xóa `@EnableAsync` trong môi trường test cô lập và quan sát sự khác biệt.

### Bài 2 — Chỉ rõ executor

Thêm `@Bean("workExecutor") ThreadPoolTaskExecutor` với prefix `work-`, core=2, max=4, queue=10. Dùng `@Async("workExecutor")`. Test rằng thread xử lý có prefix đã đặt. Sau đó đổi tên qualifier sai có chủ đích và quan sát exception khi Spring không tìm thấy executor.

### Bài 3 — Tái hiện self-invocation

Trong cùng service, tạo `start()` gọi `this.runAsync()`, với `runAsync()` có `@Async`. Xác định thread thực thi. Sau đó tách `runAsync()` sang một bean khác, inject bean vào caller và kiểm chứng async trở lại. Giải thích bằng mô hình proxy.

### Bài 4 — Kiểu trả về và lỗi

Cho method `@Async` trả `CompletableFuture<Integer>` và cố ý ném một exception. Dùng `get()` hoặc completion handler để nhận lỗi; sau đó thử method `void` và cấu hình exception handler để ghi nhận lỗi đúng cách. Kiểm tra sự khác biệt giữa exception lúc submit bị reject và exception phát sinh trong task.

### Bài 5 — Spring Boot

Khai báo `@EnableAsync` trên `@SpringBootApplication` rồi chuyển sang một lớp `@Configuration` riêng; xác minh cả hai cách đều hiệu lực khi được load trong context. Thử thêm bean executor tùy chỉnh và kiểm tra property `spring.task.execution.*` có đang chỉnh đúng bean task sử dụng không. Không coi YAML là bằng chứng duy nhất.

### Bài 6 — Vòng đời

Đóng `ApplicationContext` trong lúc executor có task đang chạy. Kiểm tra cách task được xử lý/hủy theo cấu hình và deadline. Nêu rõ vì sao công việc buộc tồn tại qua JVM restart cần durable job/queue, chứ không chỉ `@EnableAsync`.

## 14. Checklist trước khi đưa vào production

- [ ] Có một `@Configuration` được Spring nạp với `@EnableAsync` trong đúng context.
- [ ] Mọi method async dự kiến được gọi thông qua Spring-managed bean và proxy.
- [ ] Đã rà soát self-invocation, private/final method, callback khởi tạo và kiểu trả về.
- [ ] Executor được lựa chọn có chủ đích: tên, kiểu, core/max/queue và rejection policy.
- [ ] Không mặc định Spring Boot tự bật `@Async` hoặc mọi custom executor nhận properties.
- [ ] Caller quan sát được lỗi: qua future/handler, log và metric theo nghiệp vụ.
- [ ] Có timeout, cancellation hợp tác và giới hạn tài nguyên cho các I/O/downstream.
- [ ] Không dựa vào transaction/ThreadLocal của caller tự động xuất hiện ở worker.
- [ ] Bean singleton không giữ per-request mutable state gây race giữa task.
- [ ] Executor do Spring quản lý có chính sách shutdown phù hợp; task bền vững có cơ chế ngoài JVM.
- [ ] Test kiểm chứng đúng thread, executor, exception và tình huống tải; không dựa vào `sleep`.
- [ ] Đã đối chiếu API và default theo Spring Framework / Spring Boot phiên bản thực tế.

**Kết luận:** Khai báo `@EnableAsync` trong lớp `@Configuration` là bước **bật hạ tầng xử lý annotation async**. Để `@Async` hoạt động đúng và an toàn, phải đồng thời bảo đảm cấu hình được nạp, lời gọi đi qua Spring proxy, executor có giới hạn phù hợp và vòng đời/lỗi/kết quả được quan sát. Thành công không chỉ là “method chạy ở thread khác”, mà là thực thi đúng tài nguyên với hành vi có thể kiểm chứng.

## 15. Tài liệu tham khảo chính thức

- Spring Framework — [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@EnableAsync`, `@Async`, executor qualification, exception handling và nguyên tắc proxy.
- Spring Framework Javadoc — [@EnableAsync](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html): annotation attributes, scope context, `AsyncConfigurer` và chế độ advice.
- Spring Framework Javadoc — [@Async](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Async.html): kiểu trả về và mục đích annotation.
- Spring Boot — [Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html): những thành phần executor do Boot tự cấu hình và các trường hợp tương tác với `@EnableAsync`.

Khi nâng cấp framework, hãy dùng đường dẫn tài liệu của **đúng phiên bản** trong project, vì auto-configuration và lựa chọn executor mặc định có thể khác giữa các release.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-enablement-002">Bean phải được container quản lý để annotation được xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Bean phải được container quản lý để annotation được xử lý

## 1. Ý chính trong một câu

Trong Spring Framework, ghi **`@Async` lên một phương thức Java không tự làm phương thức đó chạy trên thread khác**. Ở chế độ mặc định, Spring cần **quản lý bean**, bật xử lý bất đồng bộ bằng **`@EnableAsync`**, tạo hoặc gắn một **proxy** thích hợp và để **lời gọi đi qua proxy**. Nếu tự tạo đối tượng bằng `new` rồi gọi phương thức, annotation chỉ còn là metadata; Spring không tự chạy tới để chặn lời gọi.

~~~text
Đúng: caller ---> Spring-managed proxy ---> @Async interceptor
                                           |
                                           v
                                      TaskExecutor ---> worker thread
                                           |
                                           v
                                      target.method()

Sai kỳ vọng: caller ---> new Service().method()
                              |
                              v
                       lời gọi Java thường
                       (không qua proxy)
~~~

**Phân biệt hai điều kiện:**

1. **Bean phải thuộc Spring IoC container** để được các BeanPostProcessor của context phát hiện và xử lý annotation.
2. **Lời gọi phải đi qua cơ chế can thiệp thích hợp**. Trong chế độ `AdviceMode.PROXY` mặc định, gọi nội bộ `this.asyncMethod()` có thể bỏ qua proxy dù đối tượng đã là bean.

Bài này thuộc chủ đề **Bật @Async**. Trọng tâm là điều kiện container-managed bean, sự hình thành proxy, cách gọi đúng và các dấu hiệu để chẩn đoán khi `@Async` có vẻ không hoạt động. Thread pool, queue, transaction và propagation sẽ được nhắc trong chừng mực chúng giúp giải thích đúng ranh giới của annotation.

## 2. Vì sao Spring phải quản lý đối tượng?

### 2.1. Java annotation không tự thực thi logic

`@Async` là một annotation đánh dấu phương thức hoặc class có thể chạy bất đồng bộ. Nó không phải từ khóa Java bắt buộc JVM mở thread, cũng không đổi bytecode của một object do bạn `new` một cách tự động.

Trong cấu hình proxy mặc định, Spring dùng hạ tầng annotation-processing và AOP để áp dụng interceptor cho các **bean được đăng ký trong ApplicationContext**. Một caller nhận reference đến bean đã xử lý (thường là proxy). Khi caller gọi phương thức có `@Async` qua reference này, interceptor chọn executor và gửi công việc để chạy trên worker.

Ví dụ **sai kỳ vọng**:

~~~java
public class EmailService {
    @Async
    public void send(String address) {
        System.out.println(Thread.currentThread().getName());
    }
}

// Ở một lớp khác:
EmailService service = new EmailService();
service.send("user@example.test"); // gọi đồng bộ trên thread hiện tại
~~~

Đây là các **mảnh code minh họa**, không phải chương trình độc lập (cần import `@Async` và đặt lời gọi trong method). Đối tượng do `new EmailService()` tạo ra **không được Spring đăng ký/quản lý**, vì vậy không được hạ tầng `@Async` của context tự động tạo proxy.

Kết quả là `send()` chạy như Java thông thường. Nó có thể in tên main thread hoặc request thread; việc có annotation trong source code không thay đổi đường thực thi này.

### 2.2. Container quản lý bean khác gì so với chỉ có object trong JVM?

| Tình huống | Có phải bean Spring quản lý? | `@Async` có cơ hội được xử lý? |
| --- | --- | --- |
| Class được `@Component`/`@Service` và nằm trong vùng `@ComponentScan` đang hoạt động | Có | Có, khi bật `@EnableAsync` cùng context và lời gọi qua proxy |
| Object được phương thức `@Bean` trả về và đăng ký trong context | Có | Có, miễn các điều kiện proxy/lifecycle phù hợp |
| Object được đăng ký bằng cơ chế bean registration hợp lệ của Spring | Có | Có, nếu được đi qua các post-processor cần thiết |
| Object được tạo bằng `new MyService()` trong controller rồi gọi trực tiếp | **Không** | **Không** nhờ annotation `@Async` |
| Đối tượng được lưu trong static field nhưng chưa đăng ký thành Spring bean | Không | Không |
| Bean nằm trong một ApplicationContext khác, nơi chưa bật xử lý async | Có trong context đó | Chưa chắc; `@EnableAsync` có hiệu lực theo context |
| Bean có `@Async`, nhưng gọi bằng `this.method()` từ trong chính bean | Có | Lời gọi nội bộ thường **không được intercept** trong proxy mode |

Cụm "**do Spring quản lý**" không đồng nghĩa bắt buộc phải viết `@Service`. Spring có nhiều cách tạo và đăng ký bean. **Điều quyết định là instance dùng để gọi có đi qua cơ chế quản lý và AOP của context hay không.**

## 3. Đăng ký bean đúng cách để @Async được xử lý

### 3.1. @Service hoặc @Component và component scanning

Cách thường dùng trong ứng dụng Spring Boot:

~~~java
@Service
public class InvoiceService {
    @Async("invoiceExecutor")
    public CompletableFuture<String> generate(long invoiceId) {
        // Chạy trên executor được chỉ định khi được gọi qua proxy.
        return CompletableFuture.completedFuture("invoice-" + invoiceId);
    }
}
~~~

Đây là đoạn minh họa cần các import tương ứng, `@EnableAsync` và bean tên `invoiceExecutor`. Khi app có component scan bao phủ package chứa `InvoiceService`, bean sẽ được Spring đăng ký. Hãy **inject bean này** vào một bean khác, thay vì khởi tạo thủ công.

~~~java
@Service
public class BillingFacade {
    private final InvoiceService invoiceService;

    public BillingFacade(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    public CompletableFuture<String> startInvoice(long id) {
        return invoiceService.generate(id);
    }
}
~~~

Lời gọi từ `BillingFacade` tới `InvoiceService` đi qua reference Spring đã inject; trong cấu hình proxy phù hợp, `generate()` được dispatch tới executor. **Constructor injection** giúp rõ ràng dependency và giảm nguy cơ vô tình gọi object không do Spring quản lý.

Nhưng chỉ ghi `@Service` lên class **chưa đủ** nếu package không được scan, cấu hình scan sai, bean bị loại bằng profile/condition hoặc caller lại dùng `new InvoiceService()`. Khi chẩn đoán, cần kiểm tra **bean thực sự tồn tại trong ApplicationContext** và reference đang dùng có nguồn từ context.

### 3.2. @Bean có thể trả về object tạo bằng new — vẫn được quản lý

Một ngộ nhận là "hễ thấy từ khóa `new` thì `@Async` không hoạt động". Điều đúng là **nơi nào sở hữu object sau khi tạo**:

~~~java
@Configuration
@EnableAsync
public class WorkerConfig {
    @Bean
    public ExportWorker exportWorker() {
        return new ExportWorker(); // giá trị trả về được đăng ký làm Spring bean
    }
}
~~~

Đây là đoạn cấu hình minh họa, không độc lập. Object được tạo bằng `new` **bên trong phương thức `@Bean`** rồi được Spring tiếp nhận quản lý như bean. Nó khác với controller tự `new ExportWorker()` và giữ tham chiếu riêng ngoài context.

Không nên **gọi trực tiếp phương thức factory** `new WorkerConfig().exportWorker()` rồi kỳ vọng object trả về đã qua post-processing. Hãy inject bean hoặc lấy qua context đúng cách.

**Lưu ý khác:** dùng `@Async` trực tiếp trên **phương thức khai báo trong class `@Configuration`** không phải cách được Spring hỗ trợ để chạy logic nghiệp vụ bất đồng bộ. Đặt `@Async` trên method của **service bean**, còn `@Configuration` chịu trách nhiệm khai báo infrastructure và bean.

### 3.3. Context nào bật @EnableAsync?

Cấu hình tối thiểu:

~~~java
@Configuration
@EnableAsync
public class AsyncConfig {
    // Có thể khai báo một TaskExecutor tại đây.
}
~~~

Khi được Spring nạp vào ApplicationContext, `@EnableAsync` bật cơ chế annotation-driven asynchronous method execution cho context tương ứng. Trong Spring Boot, có thể đặt annotation này trên một `@Configuration` hoặc class khởi động phù hợp.

Nếu ứng dụng có **nhiều ApplicationContext** (ví dụ root context và context riêng của DispatcherServlet trong một số kiến trúc), `@EnableAsync` ở context này **không mặc nhiên áp dụng cho toàn bộ bean của các context khác**. Phải xác định chính xác service thuộc context nào và hạ tầng async đang hoạt động ở đâu.

## 4. Hai tầng cần đúng: đăng ký bean và lời gọi qua proxy

Tưởng tượng Spring chứa:

~~~text
ApplicationContext
    |
    +-- Async infrastructure: @EnableAsync
    |
    +-- Bean definition: invoiceService
            |
            +-- Tạo target InvoiceService
            +-- BeanPostProcessor kiểm tra @Async
            +-- Cung cấp reference proxy cho consumer
                    |
Caller ---------> proxy.generate(42)
                    |
                    +-- chọn TaskExecutor
                    +-- submit phần thực thi
                              |
                              v
                         worker thread
                              |
                              v
                         InvoiceService.generate(42)
~~~

Đây là **sơ đồ khái niệm**, không hứa Spring luôn dùng đúng một hình dạng proxy hoặc mọi phase diễn ra theo thứ tự giản lược trên. Spring AOP có thể dùng **JDK dynamic proxy** hoặc **class-based proxy** tùy interface, cấu hình và các proxy đã tồn tại.

**Điểm hay bị nhầm:**

- **Bean có annotation** không bảo đảm **mọi** lời gọi của nó đều chạy async.
- **Một method chạy trên thread khác** không nhất thiết là bằng chứng `@Async` đã chạy; caller cũng có thể tự dùng `ExecutorService`.
- **Tồn tại TaskExecutor** trong context không có nghĩa `@EnableAsync` đã bật hoặc `@Async` chắc chắn sử dụng đúng pool đó.
- **Gọi qua proxy** quyết định có interception ở chế độ mặc định hay không; scope singleton/prototype không tự sửa vấn đề interception.

Một ứng dụng có thể gặp cả hai lỗi cùng lúc: tự `new` service và quên bật `@EnableAsync`. Cách chẩn đoán phải kiểm tra từng điều kiện riêng.

## 5. Lỗi tự gọi phương thức (self-invocation)

Đây là lỗi phổ biến **ngay cả khi bean đã được Spring quản lý**:

~~~java
@Service
public class ImportService {
    public void startImport() {
        this.importAsync(); // gọi nội bộ, thường không qua proxy
    }

    @Async
    public void importAsync() {
        // Tác vụ dự kiến chạy nền, nhưng có thể chạy đồng bộ.
    }
}
~~~

Đoạn này là mã minh họa cần import và `@EnableAsync`. Khi `startImport()` đang chạy bên trong **target** của bean, `this.importAsync()` là lời gọi Java nội bộ. Nó không đi qua proxy Spring được caller nắm giữ, nên advice của `@Async` thường không được kích hoạt.

**Cách thiết kế khuyến nghị:** đưa nhiệm vụ bất đồng bộ vào bean riêng và inject qua constructor:

~~~java
@Service
public class ImportWorker {
    @Async("importExecutor")
    public CompletableFuture<Void> runAsync() {
        // Thực thi tác vụ.
        return CompletableFuture.completedFuture(null);
    }
}

@Service
public class ImportFacade {
    private final ImportWorker worker;

    public ImportFacade(ImportWorker worker) {
        this.worker = worker;
    }

    public CompletableFuture<Void> startImport() {
        return worker.runAsync(); // gọi qua proxy được inject
    }
}
~~~

Các class trên là **mã minh họa cần bổ sung import và cấu hình executor**, không trùng với demo độc lập ở mục 8. Phương án tách bean làm ranh giới bất đồng bộ hiển thị rõ, dễ kiểm thử và hạn chế dependency vòng.

Một số cách như tự inject chính bean của mình, lấy `AopContext.currentProxy()` hay bật circular references để gọi qua proxy **không phải lựa chọn mặc định nên dùng**. Chúng làm code phụ thuộc cơ chế AOP, khó bảo trì và có thể phát sinh vấn đề vòng đời.

Nếu chọn **`@EnableAsync(mode = AdviceMode.ASPECTJ)`** với spring-aspects và compile-time/load-time weaving được cấu hình đúng, Spring có thể can thiệp cả self-invocation; đây là **chế độ khác** với proxy mode mặc định, không phải giải pháp chỉ cần đổi một thuộc tính rồi hoạt động ngay.

## 6. Private, final, static, constructor và lifecycle callback

Proxy-based AOP có những ranh giới kỹ thuật. Khi thiết kế method `@Async`, cách đơn giản và dễ dự đoán là:

- Dùng **method public, không final** trên **bean do Spring quản lý**.
- Gọi method đó **từ bean khác qua reference đã được inject**.
- Đảm bảo class có thể được proxied nếu Spring cần class-based proxy.
- Tránh mong chờ advice cho **private method**, **static method**, **constructor**, hoặc code chạy **trước khi proxy sẵn sàng**.

Ví dụ sai:

~~~java
@Service
public class StartupService {
    @PostConstruct
    void init() {
        initializeAsync(); // thời điểm lifecycle, gọi nội bộ
    }

    @Async
    private void initializeAsync() {
        // Không nên kỳ vọng được Spring proxy intercept.
    }
}
~~~

Đây là mảnh minh họa (cần import `@PostConstruct` của Jakarta, `@Service`, `@Async`). Vừa là private method, vừa được gọi nội bộ trong lifecycle callback. `@Async` **không được dùng như cơ chế bất đồng bộ cho chính callback `@PostConstruct`**.

Nếu thực sự cần khởi động tác vụ nền sau khi app sẵn sàng, có thể sử dụng **ApplicationReadyEvent** trong Spring Boot hoặc một bean điều phối riêng để gọi method `@Async` trên bean khác, đồng thời quy định rõ khi nào hệ thống coi việc khởi tạo đã hoàn tất. Không khởi động tác vụ nền trước khi các dependency/context đã ở trạng thái phù hợp.

**Final và proxy:** class-based proxy không thể override final method; một class final cũng gây hạn chế với subclass proxy. JDK dynamic proxy chủ yếu expose các method qua interface tương ứng, nên việc inject theo class concrete có thể không phù hợp. Đừng coi `proxyTargetClass=true` là cách vượt qua final/private; nó chỉ thay đổi cơ chế proxy.

## 7. @EnableAsync, executor và kiểu trả về phải tương thích

Spring chọn executor theo qualifier trên `@Async("beanName")`, cấu hình async và quy tắc tìm kiếm executor của phiên bản/context. Với ứng dụng có nhiều pool, chọn rõ ràng:

~~~java
@Bean("reportExecutor")
public ThreadPoolTaskExecutor reportExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(20);
    executor.setThreadNamePrefix("report-");
    return executor;
}
~~~

Đây là đoạn cấu hình trong class `@Configuration` có import thích hợp. Bean `ThreadPoolTaskExecutor` do Spring quản lý được initialize theo lifecycle. Hãy tránh tự `new ThreadPoolTaskExecutor()` ngoài container rồi quên gọi initialize/shutdown.

Các kiểu trả về hợp lệ của một method `@Async` thường là **`void`**, **`Future<T>`** hoặc **`CompletableFuture<T>`**. Với method trả Future, caller nhận một Future do cơ chế async cung cấp. Phần thân method thường tạo một handle hoàn tất/giá trị tạm thích hợp để interceptor bao bọc kết quả.

~~~java
@Async("reportExecutor")
public CompletableFuture<String> createReport(long id) {
    // Method này chạy trên worker khi được gọi qua proxy đúng cách.
    return CompletableFuture.completedFuture("report-" + id);
}
~~~

Đây là method minh họa trong một bean khác, không phải code trong `@Configuration`. Lời gọi `createReport(...).get()` sẽ đợi kết quả và có thể chặn caller; muốn giữ tính bất đồng bộ cần thiết kế composition/continuation thay vì chờ ngay trong request nếu không cần.

Với `@Async void`, lỗi chạy trong worker không thể tự ném lại cho caller đã trả về; cần logger/`AsyncUncaughtExceptionHandler` hoặc một hợp đồng khác. Với Future, caller cần quan sát completion/exception và timeout. Executor có thể bị quá tải/từ chối task; `@Async` **không phải hàng đợi bền vững** và không hứa task luôn được thực hiện.

## 8. Ví dụ hoàn chỉnh: new, bean proxy, self-invocation, gọi qua bean khác

Demo **chạy được với Java 17+, Gradle và Spring Framework 6.1.14**, không cần Spring Boot hay máy chủ web. Ta cố ý cho cùng một class `ReportWorker` được sử dụng theo bốn đường:

1. `new ReportWorker()`: ngoài container, `@Async` bị bỏ qua.
2. `context.getBean(ReportWorker.class)`: trả về reference được Spring xử lý; gọi method `@Async` qua proxy.
3. Gọi `selfCall()` trên managed bean: method thường tự gọi `this.threadNameAsync()` bên trong target, không qua proxy.
4. Một bean khác inject `ReportWorker` và gọi qua reference đã inject: đúng ranh giới proxy.

### 8.1. Cấu trúc tệp

~~~text
async-bean-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/AsyncBeanDemo.java
~~~

Tệp **settings.gradle**:

~~~groovy
rootProject.name = 'async-bean-demo'
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
    mainClass = 'demo.AsyncBeanDemo'
}
~~~

Tệp **src/main/java/demo/AsyncBeanDemo.java**:

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

public final class AsyncBeanDemo {

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            String callerThread = Thread.currentThread().getName();

            // Object Java thông thường: annotation không được xử lý.
            ReportWorker unmanaged = new ReportWorker();
            String unmanagedThread = unmanaged.threadNameAsync()
                    .get(5, TimeUnit.SECONDS);
            System.out.println("new runs on caller: " +
                    callerThread.equals(unmanagedThread));

            // Bean được xử lý và trả về từ Spring container.
            ReportWorker managed = context.getBean(ReportWorker.class);
            System.out.println("managed is proxy: " +
                    AopUtils.isAopProxy(managed));

            // Lời gọi bên ngoài qua proxy kích hoạt @Async.
            String managedThread = managed.threadNameAsync()
                    .get(5, TimeUnit.SECONDS);
            System.out.println("external call uses pool: " +
                    managedThread.startsWith("demo-worker-"));

            // Lời gọi this.threadNameAsync() bên trong cùng target.
            String selfThread = managed.selfCall()
                    .get(5, TimeUnit.SECONDS);
            System.out.println("self-call stays on caller: " +
                    callerThread.equals(selfThread));

            // Một bean khác nhận managed proxy bằng constructor injection.
            DelegatingCaller caller = context.getBean(DelegatingCaller.class);
            String delegatedThread = caller.call()
                    .get(5, TimeUnit.SECONDS);
            System.out.println("injected caller uses pool: " +
                    delegatedThread.startsWith("demo-worker-"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class Config {

        @Bean("demoExecutor")
        public ThreadPoolTaskExecutor demoExecutor() {
            ThreadPoolTaskExecutor executor =
                    new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(10);
            executor.setThreadNamePrefix("demo-worker-");
            return executor;
        }

        // Có 'new' ở đây nhưng giá trị trả về là Spring-managed bean.
        @Bean
        public ReportWorker reportWorker() {
            return new ReportWorker();
        }

        @Bean
        public DelegatingCaller delegatingCaller(ReportWorker worker) {
            return new DelegatingCaller(worker);
        }
    }

    public static class ReportWorker {

        @Async("demoExecutor")
        public CompletableFuture<String> threadNameAsync() {
            String current = Thread.currentThread().getName();
            return CompletableFuture.completedFuture(current);
        }

        public CompletableFuture<String> selfCall() {
            return this.threadNameAsync(); // Bỏ qua proxy khi self-invoke.
        }
    }

    public static class DelegatingCaller {
        private final ReportWorker worker;

        public DelegatingCaller(ReportWorker worker) {
            this.worker = worker;
        }

        public CompletableFuture<String> call() {
            return worker.threadNameAsync();
        }
    }
}
~~~

### 8.2. Chạy và kết quả

Từ thư mục `async-bean-demo`, có JDK 17+ và Gradle được cài:

~~~bash
gradle run
~~~

Nếu có Gradle Wrapper, dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows.

Với điều kiện executor hoạt động bình thường và task hoàn thành trước timeout năm giây, **năm dòng do chương trình tự in** là:

~~~text
new runs on caller: true
managed is proxy: true
external call uses pool: true
self-call stays on caller: true
injected caller uses pool: true
~~~

Gradle và các thư viện có thể in thêm thông báo build/log. Ví dụ dùng kiểm tra boolean thay vì in số thứ tự worker để kết quả không phụ thuộc việc pool tạo `demo-worker-1` hay `demo-worker-2`. Nếu task bị từ chối hoặc hệ thống quá tải, `get(timeout)` có thể ném ngoại lệ; demo giả định runtime chạy bình thường.

### 8.3. Điều cần quan sát

- `unmanaged` là object do code tạo trực tiếp; Spring không biết tới object này. Method vẫn trả `CompletableFuture`, nhưng được tạo **ngay trên caller thread**. Chỉ nhìn thấy kiểu Future không chứng minh method được gửi đến executor.
- `managed` lấy từ `ApplicationContext` là bean đã qua post-processing. Vì `ReportWorker` không khai báo interface, Spring dùng một dạng class-based proxy phù hợp trong demo; `AopUtils.isAopProxy(managed)` trả true.
- `managed.threadNameAsync()` là lời gọi từ bên ngoài qua proxy, nên nội dung method chạy trên thread có prefix `demo-worker-`.
- `managed.selfCall()` không có `@Async`; method chạy đồng bộ, và lời gọi `this.threadNameAsync()` bên trong target **không qua proxy**, vì vậy `threadNameAsync()` chạy trên caller.
- `DelegatingCaller` là bean khác; nó nhận `ReportWorker` do Spring inject và gọi method qua proxy, nên async hoạt động.
- `@Bean` tạo object bằng `new` **không làm object mất tính managed**: khác biệt là instance trả về được container đăng ký và post-process.
- `try-with-resources` đóng context sau khi Future đã hoàn tất, giúp Spring quản lý lifecycle của executor.

Code ở mục 8 là **chương trình hoàn chỉnh**; các đoạn ngắn ở các mục trước là **minh họa cần context/import bổ sung**, không nên copy riêng lẻ và kỳ vọng chúng tự chạy.

## 9. Gọi đúng bean trong ứng dụng web và ứng dụng có nhiều lớp

Trong một Spring MVC hoặc Spring Boot application, controller thường đã là bean do Spring quản lý. Nhưng nếu controller tự tạo service bằng `new`, service đó vẫn nằm ngoài cơ chế xử lý annotation:

~~~java
@RestController
public class ExportController {
    @GetMapping("/export")
    public String wrong() {
        ExportService service = new ExportService();
        service.exportAsync(); // @Async không được Spring intercept.
        return "submitted";
    }
}
~~~

Đây là ví dụ **sai có chủ đích**, chưa phải mã đầy đủ. Controller thuộc Spring không khiến mọi object do controller tạo ra **tự trở thành bean**.

Cách đúng là inject service:

~~~java
@RestController
public class ExportController {
    private final ExportService service;

    public ExportController(ExportService service) {
        this.service = service;
    }

    @PostMapping("/export")
    public ResponseEntity<Void> start() {
        service.exportAsync(); // reference tới Spring-managed proxy
        return ResponseEntity.accepted().build();
    }
}
~~~

Đoạn này cũng là minh họa, cần định nghĩa `ExportService`, `@EnableAsync` và các import. Một HTTP 202 chỉ nên hứa "**đã tiếp nhận yêu cầu**" nếu hệ thống có contract tương ứng. `@Async` với executor trong bộ nhớ **không** tự đảm bảo công việc được lưu bền vững qua restart, nên endpoint production cần xác định rõ ý nghĩa "accepted".

Nếu một method chạy chậm, không nên chỉ gắn `@Async` vào controller handler mà không xem xét hợp đồng MVC, kiểu trả về và cơ chế async request. Thường tốt hơn tách việc nền sang service bean có giao diện rõ ràng, rồi lựa chọn cách trả kết quả phù hợp.

## 10. Vì sao đã có bean mà @Async vẫn không hoạt động?

Có một số nguyên nhân độc lập cần kiểm tra:

| Hiện tượng | Nguyên nhân khả dĩ | Cách xử lý |
| --- | --- | --- |
| Method in tên request/main thread | `new` service ngoài context | Inject bean đã đăng ký |
| Có `@Service` nhưng startup không thấy bean | Package ngoài `@ComponentScan`, profile/condition không phù hợp | Kiểm tra scan và bean registration |
| Bean tồn tại nhưng method chạy đồng bộ | Thiếu `@EnableAsync` trong context chứa bean | Bật async đúng context |
| Method được gọi từ chính class bằng `this` | Self-invocation bỏ qua proxy mặc định | Tách worker thành bean khác và inject |
| Method private/final/static hoặc class không proxy được | Proxy không intercept được method | Dùng public non-final instance method và cấu trúc proxied phù hợp |
| `@Async` đặt trên method của `@Configuration` | Đây không phải nơi annotation được hỗ trợ như service method | Tách logic sang bean service |
| Gọi lúc `@PostConstruct` | Lifecycle/self-invocation trước ranh giới proxy thích hợp | Điều phối từ bean khác sau khi khởi tạo |
| Không thấy prefix executor mong đợi | Method chọn executor khác, qualifier sai hoặc chưa qua proxy | Chỉ định `@Async("executorName")`, kiểm tra bean và luồng |
| Inject class concrete gặp lỗi với JDK proxy | Proxy expose interface, không phải concrete type | Inject interface thích hợp hoặc cấu hình proxy dựa trên class khi tương thích |
| `@Async` có chạy nhưng request vẫn đợi lâu | Caller lập tức `get()`/`join()` hoặc logic khác chặn | Xem lại chiến lược trả kết quả và điểm chờ |
| Lỗi background không ném tới caller | Method `void` và exception xảy ra trên worker | Dùng Future và theo dõi lỗi, hoặc AsyncUncaughtExceptionHandler |

**Cách chẩn đoán từng bước:**

1. In tên thread tại **đầu method** (chỉ khi chẩn đoán; không dùng tên thread làm điều kiện nghiệp vụ).
2. Xác nhận `@EnableAsync` đã được xử lý trong context thật của bean.
3. Kiểm tra bean có xuất hiện trong context và được inject từ context, không phải object tự new.
4. Kiểm tra đường gọi: là **bean khác -> proxy.method()** hay **this.method()**?
5. Xác nhận method có thể được proxy intercept (public/non-final là lựa chọn an toàn phổ biến).
6. Kiểm tra executor được chọn và dấu hiệu task bị từ chối/quá tải.
7. Quan sát exception/completion của `Future` nếu có.
8. Đừng bỏ qua cấu hình tests: unit test tạo object bằng `new` không phản ánh hành vi AOP; muốn kiểm tra proxy hãy chạy test có ApplicationContext phù hợp.

Với `AopUtils.isAopProxy(obj)` trong ví dụ chạy Spring, kết quả true chứng minh reference là AOP proxy, nhưng **không đủ một mình** để chứng minh lời gọi cụ thể được `@Async` intercept. Một bean có thể được proxy vì các advice khác; cần kiểm tra cả `@EnableAsync`, method và đường gọi.

## 11. Những ranh giới liên quan: transaction, context và thread safety

Khi method `@Async` được dispatch thành công:

- Worker thường là **thread khác** caller. **ThreadLocal** như request attributes, MDC hay security context **không tự động được sao chép** nếu không có cơ chế propagation.
- **Transaction đang gắn với thread của caller** không mặc nhiên theo worker. Nếu method async cần transaction riêng, xác định cách mở transaction và xử lý exception/commit ở ranh giới worker phù hợp.
- **Bean singleton** được chia sẻ giữa các worker: `@Async` không khiến mutable fields trong bean an toàn luồng; ưu tiên stateless service, immutable data và đồng bộ khi cần.
- **Executor có capacity hữu hạn**. Nếu quá tải, task có thể bị từ chối; dùng queue bounded, rejection policy rõ ràng và theo dõi trạng thái công việc.
- **Context có vòng đời**. Async task phụ thuộc bean có thể chạy khi shutdown nếu không cấu hình/kiểm soát phù hợp; không coi annotation là cơ chế bảo đảm task hoàn thành sau khi process kết thúc.

Các vấn đề trên **không phủ định** điều kiện gốc của bài: muốn annotation hoạt động theo cơ chế proxy mặc định, instance phải nằm trong Spring container và lời gọi phải đi qua proxy. Nhưng sau khi đạt hai điều kiện ấy, thiết kế concurrency vẫn cần xem xét độc lập.

## 12. Checklist trước khi dùng @Async trong code review

- [ ] `@EnableAsync` được bật trong **đúng ApplicationContext**.
- [ ] Class service được đăng ký bằng `@Service`/`@Component`, `@Bean` hoặc cơ chế bean registration phù hợp.
- [ ] Caller **inject** service bean, không tự `new` một instance khác.
- [ ] Lời gọi async đi qua **proxy**; không gọi bằng `this.asyncMethod()`.
- [ ] Method được thiết kế dễ proxy: public, không final; không đặt `@Async` vào lifecycle callback hay method trong `@Configuration`.
- [ ] Executor được chọn đúng bằng qualifier hoặc cấu hình async; giới hạn queue/threads/rejection được xác định.
- [ ] Kiểu trả về `void`/`Future`/`CompletableFuture` và chiến lược xử lý lỗi được chọn có chủ đích.
- [ ] Không giả định transaction, ThreadLocal, request context hoặc security context tự đi theo worker.
- [ ] Không đặt request-specific mutable state lên field singleton dùng chung.
- [ ] Có test chạy **trong ApplicationContext** để xác minh proxy thực sự thực thi.

## 13. Câu hỏi tự kiểm tra

1. **Một class có `@Service` nhưng bị `new` thủ công thì annotation `@Async` có hiệu lực không?** Không nhờ Spring AOP trên object ấy. Muốn Spring xử lý, phải dùng instance đã được Spring quản lý.
2. **Vì sao `return new ReportWorker()` trong phương thức `@Bean` lại khác với `new ReportWorker()` trong controller?** Object được `@Bean` trả về đi qua đăng ký và bean post-processing của context; object tự new trong controller thì không.
3. **Bean đã được Spring quản lý, vậy `this.runAsync()` có async không?** Thường không trong `AdviceMode.PROXY` mặc định vì self-invocation bỏ qua proxy.
4. **Có cần `@EnableAsync` không?** Có để bật xử lý annotation-driven async ở cấu hình Spring tương ứng, trừ khi hạ tầng tương đương đã được cấu hình rõ bằng cách khác.
5. **`@Async` có tạo thread mới mỗi lần không?** Không nhất thiết. Nó gửi task đến executor; ThreadPoolTaskExecutor có thể tái sử dụng worker.
6. **`CompletableFuture` chứng minh method đã chạy async chưa?** Không. Một method Java thông thường cũng có thể trả về Future đã hoàn tất đồng bộ.
7. **Có nên đặt `@Async` vào private method trong cùng bean không?** Không nên kỳ vọng proxy mode intercept được; tách sang public method trên bean khác.
8. **Có thể dùng AspectJ mode để can thiệp self-invocation không?** Có khi weaving và các phụ thuộc cần thiết được cấu hình đúng; đây không phải hành vi mặc định.
9. **Tại sao unit test gọi `new SomeService()` không quan sát async?** Vì test chỉ gọi object Java, không có Spring proxy/interceptor.
10. **Gọi `@Async` đã thành công có bảo đảm transaction, dữ liệu request và hoàn tất nghiệp vụ không?** Không; từng hợp đồng phải được thiết kế và kiểm soát riêng.

**Kết luận:** Spring xử lý `@Async` như một khả năng của **bean được container quản lý**, không phải như lệnh Java có hiệu lực trên mọi object. Với chế độ proxy mặc định, hai thao tác dễ nhớ nhất là **đăng ký service thành bean** và **gọi service qua reference đã được Spring inject từ bean khác**. Đây là nền tảng trước khi tinh chỉnh executor hoặc tối ưu concurrency.

### Tài liệu chính thức

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — EnableAsync API](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Spring Framework — Async API](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/annotation/Async.html)
- [Spring Framework — Bean Overview](https://docs.spring.io/spring-framework/reference/core/beans/definition.html)
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-enablement-003">Đặt @Async trên phương thức hoặc lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Đặt @Async trên phương thức hoặc lớp trong Spring Framework

## 1. Vấn đề và kết quả cần hiểu

Trong Spring Framework, `@Async` đánh dấu một **phương thức ứng viên chạy bất đồng bộ**. Có hai vị trí phổ biến:

- Đặt `@Async` **trực tiếp trên phương thức** để chỉ phương thức đó được gửi tới executor khi được gọi qua Spring proxy.
- Đặt `@Async` **trên lớp** để các phương thức nghiệp vụ đủ điều kiện trong lớp được xem là phương thức async; có thể ghi đè executor của riêng một phương thức bằng annotation ở mức phương thức.

Ví dụ: một service có `generateReport()` mất nhiều giây và `getDescription()` chỉ đọc dữ liệu tại chỗ. Đặt annotation lên `generateReport()` giúp hai hành vi tách bạch; đặt annotation lên cả lớp vô tình có thể đưa `getDescription()` vào cơ chế async dù API của nó vẫn trả về `String`. Điều này có thể dẫn đến lỗi runtime vì kiểu trả về của phương thức async không hợp lệ.

**Điểm cốt lõi:** vị trí annotation quyết định **phương thức nào được proxy intercept**, còn `@EnableAsync` và executor quyết định **cơ chế chạy thực tế**. Có annotation thôi chưa bảo đảm code chạy trên luồng khác: lời gọi phải đi qua proxy và executor được chọn phải thực sự có khả năng gửi việc sang luồng khác.

Phạm vi ví dụ: Spring Framework 6.x, Spring Boot 3.3.x, Java 17, chế độ proxy mặc định. Hành vi AspectJ weaving được nhắc riêng vì có luật self-invocation khác proxy.

## 2. Điều kiện để @Async có hiệu lực

### 2.1. Bật hỗ trợ

Trong ứng dụng Java configuration, bật `@EnableAsync` một lần ở cấu hình thích hợp:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfiguration {
}
~~~

Đây là đoạn cấu hình minh họa, chưa phải chương trình hoàn chỉnh. `@SpringBootApplication` có thể nằm cùng lớp với `@EnableAsync`. `@EnableAsync` áp dụng cho `ApplicationContext` nơi nó được khai báo; không tự động kích hoạt cho một context độc lập khác.

Spring sẽ áp dụng một advisor/interceptor cho những bean được quản lý và có annotation async phù hợp. Khi code bên ngoài gọi method qua proxy, interceptor xác định executor, gửi công việc và trả quyền điều khiển cho caller theo hợp đồng async. Nếu không bật `@EnableAsync` (và không có cơ chế khác kích hoạt async), annotation chỉ là metadata và lời gọi chạy như Java thông thường.

### 2.2. Lời gọi phải đi qua Spring proxy

~~~mermaid
flowchart LR
    Caller["Controller / bean khác"] --> Proxy["Spring proxy"]
    Proxy -->|"gửi task"| Executor["Executor được chọn"]
    Executor --> Worker["Luồng xử lý"]
    Worker --> Target["Phương thức trên bean thật"]
    Self["this.someMethod() trong chính bean"] -.->|"bỏ qua proxy"| Target
~~~

Với `@EnableAsync` mặc định `mode = AdviceMode.PROXY`:

1. **Gọi từ một bean khác qua proxy:** annotation được áp dụng nếu method đủ điều kiện.
2. **Gọi `this.asyncMethod()` trong chính đối tượng:** bỏ qua proxy, method thường chạy đồng bộ trên luồng hiện tại.
3. **Tự `new ReportService()` thay vì lấy bean Spring:** không có proxy; annotation không tự tạo bất đồng bộ.
4. **Gọi trong constructor, `@PostConstruct` hoặc quá sớm trước khi proxy sẵn sàng:** không phải cách đáng tin để kích hoạt async. Để khởi chạy sau startup, có thể cho bean khác gọi service đã được proxy hóa.

Với proxy dựa trên subclass, `private` và `final` method không thể được subclass proxy override để intercept; class `final` cũng gây trở ngại. Với JDK interface proxy, chỉ các lời gọi đi qua interface proxy mới được intercept. Để code dễ đoán, đặt `@Async` trên **public method của Spring-managed service**, được caller khác inject và gọi.

`@EnableAsync(mode = AdviceMode.ASPECTJ)` kết hợp weaving có thể intercept cả lời gọi nội bộ, nhưng đòi hỏi cấu hình AspectJ và không phải mặc định của ví dụ.

## 3. Đặt @Async trên từng phương thức

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    @Async("reportExecutor")
    public CompletableFuture<String> generate(String id) {
        String path = "reports/" + id + ".csv";
        return CompletableFuture.completedFuture(path);
    }

    public String description() {
        return "Dịch vụ báo cáo";
    }
}
~~~

Đây là class có thể đặt trong ứng dụng Spring đã bật async và khai báo executor tên `reportExecutor`; cần caller gọi qua Spring proxy.

- `generate(id)` là ứng viên chạy async với `reportExecutor` vì annotation nằm ngay trên method.
- `description()` không có annotation và lớp cũng không có `@Async`, nên lời gọi thông thường vẫn chạy đồng bộ.
- Việc `generate()` trả `CompletableFuture.completedFuture(path)` **bên trong method thực thi** không mâu thuẫn với async: Spring proxy trả Future đại diện cho công việc được executor thực hiện, còn implementation phải tuân kiểu chữ ký đã khai báo.
- Đoạn này tạo chuỗi đường dẫn nhanh chỉ để minh họa; đổi thành tạo báo cáo thật cần lưu trạng thái, backpressure, timeout và kiểm soát tài nguyên.

Có thể dùng `@Async` không chỉ định tên executor:

~~~java
// Đoạn minh họa trong Spring-managed bean:
@Async
public void refreshCache() {
    // Phương thức async không trả kết quả cho caller.
}
~~~

Khi đó Spring dùng executor mặc định được cấu hình hoặc tìm được trong context. Đừng đoán số lượng luồng chỉ dựa trên sự hiện diện của `@Async`; Spring Framework có fallback riêng, Spring Boot lại có lớp auto-configuration riêng.

## 4. Đặt @Async trên cả lớp

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Async("batchExecutor")
public class BatchService {
    public CompletableFuture<String> rebuildIndex() {
        return CompletableFuture.completedFuture("index");
    }

    public CompletableFuture<String> publishSnapshot() {
        return CompletableFuture.completedFuture("snapshot");
    }

    @Async("reportExecutor")
    public CompletableFuture<String> urgentRebuild() {
        return CompletableFuture.completedFuture("urgent");
    }
}
~~~

Khi caller ở bean khác gọi qua Spring proxy:

- `rebuildIndex()` và `publishSnapshot()` được áp dụng async bằng executor `batchExecutor`, vì `@Async("batchExecutor")` đặt ở lớp.
- `urgentRebuild()` có annotation riêng `@Async("reportExecutor")`, **ưu tiên qualifier của phương thức** so với qualifier ở lớp.
- Nếu method-level `@Async` có giá trị rỗng, nó biểu thị chọn executor mặc định theo luật resolution, không phải nhất thiết kế thừa tên executor ở lớp. Khi thiết kế nhiều pool, nên ghi rõ qualifier trên method cần khác biệt.
- Class-level annotation không khiến constructor, field initializer, các lời gọi nội bộ hay mọi method private trở thành các task độc lập.

Cách đặt annotation ở lớp hợp lý khi phần lớn API public của service được thiết kế từ đầu để async. Tuy nhiên thay đổi sau này rất dễ gây tác dụng phụ: người khác thêm một method tiện ích trả `String` hay `long` vào lớp và vô tình biến nó thành ứng viên async với kiểu trả về không hợp lệ.

### So sánh phạm vi

| Cách đặt | Phương thức nào bị áp dụng? | Ưu điểm | Rủi ro |
| --- | --- | --- | --- |
| `@Async` trên một method | Method đó khi gọi qua proxy | Kiểm soát chính xác, dễ review | Có thể quên đánh dấu method mới cần async |
| `@Async` trên class | Các method nghiệp vụ đủ điều kiện của class khi gọi qua proxy | Gọn khi toàn bộ service dùng async | Dễ ảnh hưởng thêm method mới, kiểu trả về không phù hợp |
| Class `@Async("batchExecutor")` + method `@Async("reportExecutor")` | Mặc định class, method riêng ghi đè | Phân pool cho task ưu tiên | Cần rà soát qualifier và cấu hình executor |
| Class không có annotation + method không có annotation | Không có interceptor async từ những annotation này | Hành vi đồng bộ rõ ràng | Muốn async cần gọi qua cơ chế khác |

**Ví dụ không nên dùng:**

~~~java
// Anti-pattern minh họa; không gọi method này qua @Async proxy.
@Service
@Async("batchExecutor")
public class MixedPurposeService {
    public String status() {
        return "READY";
    }
}
~~~

Trong proxy async thông thường, Spring chỉ hỗ trợ return type `void`, `Future<T>` hoặc `CompletableFuture<T>`. Nếu `status()` bị async interceptor xử lý, chữ ký trả `String` không phù hợp và có thể bị `IllegalArgumentException` thay vì trả chuỗi như trước. Hãy tách status/query đồng bộ sang bean khác, hoặc đặt annotation trên từng method async.

## 5. Dự án chạy được: method-level so với class-level

Ứng dụng Java 17 + Spring Boot 3.3.5 dưới đây có hai executor với prefix khác nhau. Ta quan sát trên **luồng thật của method được intercept** và tránh dựa vào `Thread.sleep` hay thứ tự chạy ngẫu nhiên.

### 5.1. Cấu trúc

~~~text
async-placement-demo/
├── pom.xml
└── src/main/java/com/example/async/
    ├── AsyncPlacementApplication.java
    ├── MethodOnlyService.java
    └── ClassWideService.java
~~~

### 5.2. pom.xml

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
    <artifactId>async-placement-demo</artifactId>
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

`spring-boot-starter-aop` bảo đảm hạ tầng proxy AOP có trên classpath của demo; `@Async` được kích hoạt qua `@EnableAsync`. Đây là ứng dụng command-line, không cần web server.

### 5.3. AsyncPlacementApplication.java

~~~java
package com.example.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
@EnableAsync
public class AsyncPlacementApplication {
    public static void main(String[] args) {
        // ApplicationRunner chạy trong SpringApplication.run() trước khi trả context.
        SpringApplication.run(AsyncPlacementApplication.class, args).close();
    }

    @Bean("reportExecutor")
    ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(8);
        pool.setThreadNamePrefix("report-");
        return pool;
    }

    @Bean("batchExecutor")
    ThreadPoolTaskExecutor batchExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(8);
        pool.setThreadNamePrefix("batch-");
        return pool;
    }

    @Bean
    ApplicationRunner verifyPlacement(
            MethodOnlyService methodService, ClassWideService classService) {
        return args -> {
            String caller = Thread.currentThread().getName();

            // Gửi qua proxy rồi mới chờ, để đọc thread name thật bên trong task.
            CompletableFuture<String> method = methodService.generateReport();
            CompletableFuture<String> cls1 = classService.rebuildIndex();
            CompletableFuture<String> cls2 = classService.publishSnapshot();
            CompletableFuture<String> override = classService.urgentRebuild();

            System.out.println("METHOD_LEVEL_REPORT_POOL="
                    + method.get(5, TimeUnit.SECONDS).startsWith("report-"));
            System.out.println("UNANNOTATED_STAYS_CALLER="
                    + methodService.descriptionThread().equals(caller));
            System.out.println("CLASS_METHOD_1_BATCH_POOL="
                    + cls1.get(5, TimeUnit.SECONDS).startsWith("batch-"));
            System.out.println("CLASS_METHOD_2_BATCH_POOL="
                    + cls2.get(5, TimeUnit.SECONDS).startsWith("batch-"));
            System.out.println("METHOD_OVERRIDES_CLASS="
                    + override.get(5, TimeUnit.SECONDS).startsWith("report-"));
            System.out.println("SELF_INVOCATION_STAYS_CALLER="
                    + methodService.selfInvocationThread().equals(caller));
        };
    }
}
~~~

Hai bean executor được Spring quản lý, khởi tạo và dọn vòng đời. Mỗi pool chỉ có một worker để tên luồng dễ theo dõi; `queueCapacity=8` không có nghĩa tám worker. Không dựa vào mặc định số thread của Spring Framework hay Boot trong bài thử này.

### 5.4. MethodOnlyService.java

~~~java
package com.example.async;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MethodOnlyService {
    @Async("reportExecutor")
    public CompletableFuture<String> generateReport() {
        return CompletableFuture.completedFuture(
                Thread.currentThread().getName());
    }

    // Không có @Async ở method hay class -> chạy ngay trên caller.
    public String descriptionThread() {
        return Thread.currentThread().getName();
    }

    public String selfInvocationThread() {
        // Gọi qua this, bỏ qua Spring proxy, nên chạy inline.
        return generateReport().join();
    }
}
~~~

Chú ý `selfInvocationThread()` **không** được chú thích `@Async`; vì lời gọi từ ApplicationRunner là vào method đồng bộ, nó chạy trên caller. Bên trong nó, `generateReport()` được gọi trực tiếp qua `this` nên method mang annotation chạy inline. Nếu thay bằng một lời gọi từ bean khác qua proxy, `generateReport()` sẽ được gửi sang `reportExecutor`.

### 5.5. ClassWideService.java

~~~java
package com.example.async;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Async("batchExecutor")
public class ClassWideService {
    // Không cần @Async tại method vì class đã có.
    public CompletableFuture<String> rebuildIndex() {
        return CompletableFuture.completedFuture(
                Thread.currentThread().getName());
    }

    public CompletableFuture<String> publishSnapshot() {
        return CompletableFuture.completedFuture(
                Thread.currentThread().getName());
    }

    @Async("reportExecutor")
    public CompletableFuture<String> urgentRebuild() {
        return CompletableFuture.completedFuture(
                Thread.currentThread().getName());
    }
}
~~~

### 5.6. Chạy và kiểm tra stdout

Tại thư mục chứa `pom.xml`, dùng JDK 17+ và Maven:

~~~bash
mvn spring-boot:run
~~~

Bỏ qua log startup/shutdown của Spring, chương trình in **sáu dòng** theo thứ tự (đều là boolean; không giả định hậu tố tên thread):

~~~text
METHOD_LEVEL_REPORT_POOL=true
UNANNOTATED_STAYS_CALLER=true
CLASS_METHOD_1_BATCH_POOL=true
CLASS_METHOD_2_BATCH_POOL=true
METHOD_OVERRIDES_CLASS=true
SELF_INVOCATION_STAYS_CALLER=true
~~~

Giải thích: `get(5, TimeUnit.SECONDS)` chỉ được gọi **sau khi** nhận handle future, nên chương trình không khẳng định thứ tự hoàn tất giữa các executor; nó chỉ chờ kết quả mỗi việc có hạn định. Method-level `generateReport` chạy trong `report-*`, hai method thuộc class-level chạy trong `batch-*`, `urgentRebuild` ghi đè dùng `report-*`. `descriptionThread()` và lời gọi nội bộ `selfInvocationThread()` đều chạy trên caller nên trả đúng thread name đã ghi lại.

**Kiểm thử này xác minh vị trí annotation và đường đi proxy**, chưa chứng minh giới hạn throughput hay mức chịu tải thực tế. Muốn kiểm thử xử lý chồng nhau, hãy dùng `CountDownLatch`/barrier với một pool có nhiều worker và kiểm tra invariant, không chỉ nhìn tên thread.

## 6. Hợp đồng method khi quyết định vị trí annotation

Vị trí `@Async` phải phù hợp chữ ký của **từng method được intercept**: `void`, `Future<T>` hoặc `CompletableFuture<T>`. Khi gắn annotation ở cấp lớp, cần rà soát toàn bộ public business methods; một getter trả `String` thêm sau này có thể vô tình bị async advice áp dụng và lỗi vì return type không hỗ trợ.

| Loại method | Vị trí annotation nên chọn | Kênh quan sát |
| --- | --- | --- |
| Chạy nền, không trả kết quả | Method-level `@Async` | Handler/log cho lỗi `void` |
| Có kết quả và cần phối hợp | Method-level hoặc class-level đã kiểm soát | Future/CompletableFuture, timeout và lỗi |
| Query/getter đồng bộ trả `String`/DTO | Không được phủ bởi class-level `@Async` | Kết quả trực tiếp trên caller |

Caller gọi `get()`/`join()` ngay sau submit vẫn có thể block; callback trên `CompletableFuture` cho phép chuỗi tác vụ linh hoạt hơn. Khi task lỗi, future hoàn thành lỗi; khi executor từ chối ngay lúc submit, phải xử lý rejection tại biên gọi. Các cơ chế composition, cancellation và exception chi tiết thuộc hợp đồng của chính kiểu trả về, tách biệt với quyết định **đặt annotation ở method hay class**.

## 7. Hiểu đúng executor khi gắn annotation

`@Async("reportExecutor")` chỉ định **bean name hoặc qualifier**, không chỉ định số luồng. Cần khai báo bean executor tương ứng và theo dõi chính sách queue/rejection.

Nếu `@Async` không có qualifier:

1. `@EnableAsync` có thể dùng executor do `AsyncConfigurer` cung cấp.
2. Trong Spring Framework, cơ chế mặc định tìm bean `TaskExecutor` duy nhất; nếu không chọn được, xét bean `Executor` tên `taskExecutor`.
3. Nếu không có ứng viên phù hợp, Spring Framework có fallback `SimpleAsyncTaskExecutor`. Đây không phải pool worker tái sử dụng kiểu `ThreadPoolTaskExecutor`.
4. Spring Boot có thể tự cung cấp executor theo auto-configuration khi điều kiện phù hợp; trong môi trường Boot, kết quả thực tế còn phụ thuộc bean hiện hữu và properties.

Khi dùng nhiều executor, **chỉ rõ qualifier cho method có yêu cầu riêng**. Với class-level annotation, nêu cả default của class và method override trong review. Không giả định rằng thêm một bean tên tùy ý sẽ khiến mọi `@Async` tự dùng bean ấy.

Trong ứng dụng sản xuất, nên cấu hình `corePoolSize`, `maxPoolSize`, `queueCapacity`, thread name prefix, timeout và hành vi khi overload; đừng để queue tăng không giới hạn. Một pool 4 worker không bảo đảm bốn việc CPU-bound sẽ nhanh hơn một core hoặc DB đủ connection; đo thực tế.

## 8. Trạng thái request, transaction và tác động khi chạy trên thread khác

Lời gọi async chuyển việc sang thread khác (khi dùng executor bất đồng bộ thật sự) làm thay đổi các giả định về ngữ cảnh:

- **Transaction:** transaction của HTTP/request thread không tự truyền sang worker. Nếu method async cần giao dịch, tạo boundary ở service được worker gọi trên thread ấy, ví dụ `@Transactional` với lời gọi qua proxy, và giới hạn transaction quanh DB operations. Không giữ transaction trong suốt lúc gọi API chậm hoặc tạo file.
- **Request scope:** bean `@RequestScope` và `RequestContextHolder` thường gắn với request thread. Không giả định worker luôn truy cập được request context. Lấy các giá trị cần thiết (request ID, user ID, immutable DTO) trước khi dispatch.
- **SecurityContext/MDC/ThreadLocal:** không mặc nhiên hiện diện trong worker. Dùng cơ chế context propagation thích hợp hoặc truyền dữ liệu rõ ràng, đồng thời dọn trạng thái thread-local khi luồng được tái sử dụng.
- **Mutable inputs:** nếu caller tiếp tục sửa danh sách/DTO sau khi gửi việc, worker có thể thấy dữ liệu thay đổi không nhất quán. Truyền snapshot bất biến hoặc dùng đồng bộ hóa thích hợp.
- **Lỗi/side effects:** worker có thể ghi kết quả sau khi caller timeout hoặc client retry. Thiết kế idempotency key và trạng thái job để tránh xử lý trùng.

Đặt `@Async` trên cả lớp **không biến field singleton thành thread-safe**. Nhiều worker có thể đồng thời gọi cùng một instance, nên `count++` trên field mutable vẫn mất cập nhật. Ưu tiên service stateless, `AtomicLong`/lock cho state trong JVM hoặc transaction/optimistic locking cho dữ liệu chia sẻ giữa nhiều pod.

## 9. Các lỗi phổ biến khi chọn vị trí @Async

| Triệu chứng | Nguyên nhân hay gặp | Cách xác minh/sửa |
| --- | --- | --- |
| Có `@Async` nhưng vẫn chạy trên caller | Thiếu `@EnableAsync`, tự `new` object, hoặc self-invocation | Kiểm tra bean proxy và thread name; gọi từ bean khác |
| Method trong class `@Async` trả `String` bị lỗi | Class-level annotation đã phủ lên getter/query đồng bộ | Tách bean hoặc chỉ annotate method async; trả Future hợp lệ |
| Method đặc biệt chạy sai pool | Class-level qualifier đang áp dụng; method chưa override đúng | Dùng `@Async("executorName")` và kiểm tra tên thread |
| `private`/`final` method không được intercept | Giới hạn proxy mặc định | Dùng public, non-final method gọi từ bên ngoài proxy |
| Exception của `void` không tới caller | Task chạy ở worker, không có result handle | Cấu hình `AsyncUncaughtExceptionHandler`, log và metric |
| Future không bao giờ hoàn thành | Task bị mất khi overload/shutdown hoặc mắc kẹt I/O | Dùng rejection có tín hiệu, deadline, timeout và job status |
| HTTP trả chậm dù đã dùng async | Caller gọi `future.join()/get()` ngay | Trả job ID/202 sớm, tách endpoint tra trạng thái |
| Đọc request/transaction context lỗi trong worker | Ngữ cảnh gắn với luồng, không tự chuyển | Truyền ID/dữ liệu cần thiết; mở transaction ở worker |
| Test bằng executor đồng bộ vẫn pass | `SyncTaskExecutor` không chuyển thread và che giấu race/context | Test thêm với executor production và concurrency có kiểm soát |
| Đặt `@Async` lên `@PostConstruct` | Lifecycle callback không phải điểm kích hoạt async thích hợp | Cho bean khởi tạo khác gọi qua proxy sau khi context sẵn sàng |

## 10. Hướng dẫn quyết định và checklist review

**Ưu tiên đặt trên phương thức khi:** lớp có cả query đồng bộ và command async, ít method cần chạy nền, hoặc mỗi job cần executor riêng. Đây thường là lựa chọn dễ kiểm soát cho controller/service có API hỗn hợp.

**Có thể đặt trên lớp khi:** mọi public business method của lớp đều chủ đích async, kiểu trả về đều tương thích, và team thống nhất rằng method thêm mới sẽ tự chịu quy tắc async. Để tránh vô tình thay đổi contract, tài liệu hóa ràng buộc ở lớp và giữ các utility/query đồng bộ trong bean khác.

Trước khi đưa code vào production, kiểm tra:

1. Bean được Spring quản lý và `@EnableAsync` bật trong đúng context; không gọi `new` để thay bean.
2. Caller gọi qua proxy từ bean khác; không dựa vào `this.method()` hoặc constructor/`@PostConstruct`.
3. Annotation đặt trên method hay cả lớp đúng chủ đích; với class-level, đã rà soát **tất cả public business method** và return type.
4. Executor tên/qualifier đúng và sẵn có; có pool/queue hữu hạn, rejection policy có tín hiệu và graceful shutdown hợp lý.
5. Caller biết cách nhận kết quả: `void` được log/metric; `Future/CompletableFuture` có error handling và timeout; không block HTTP request không cần thiết.
6. Task nhận input an toàn và quản lý transaction, request context, security context, MDC đúng khi chuyển thread.
7. Dùng test xác minh method-level/class-level/method override; dùng concurrency test thực cho shared state và test bão hòa để xác minh rejection.
8. Khi chạy nhiều instance, trạng thái job quan trọng nằm ở DB/broker đáng tin cậy và có retry/idempotency thích hợp.

### Câu hỏi tự kiểm tra

1. Nếu chỉ `generateReport()` cần chạy nền còn `getSummary()` phải trả `String` ngay, bạn sẽ đặt `@Async` ở đâu và vì sao?
2. `@Async("batchExecutor")` ở class và `@Async("reportExecutor")` ở method cùng tồn tại: method đó dùng executor nào?
3. Trong cùng bean, `this.generateReport()` có được interceptor của `@EnableAsync` mặc định bắt không? Làm thế nào để kích hoạt đúng?
4. Vì sao một method trong class có `@Async` không nên trả trực tiếp `List<Report>` khi proxy intercept?
5. Khác biệt giữa exception trong worker trả `CompletableFuture` và exception trong method `@Async void` là gì?
6. Nếu test dùng `SyncTaskExecutor` còn production dùng `ThreadPoolTaskExecutor`, điều gì có thể thay đổi về transaction, ThreadLocal, thứ tự và lỗi?
7. Với endpoint muốn trả 202 ngay sau khi tiếp nhận yêu cầu, vì sao không nên gọi `future.get()` ngay trên request thread?

**Ghi nhớ:** đặt `@Async` ở **method** để chỉ rõ việc nào chạy nền; đặt ở **class** khi tất cả method nghiệp vụ đủ điều kiện được thiết kế async. Cả hai đều phụ thuộc `@EnableAsync`, proxy, executor và return type hợp lệ. Annotation có mặt trong source code không phải bằng chứng task thật sự chạy trên worker.

## Tài liệu chính thức

- [Spring Framework 6.2 — The @Async Annotation](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html#scheduling-annotation-support-async)
- [Spring Framework 6.2 — EnableAsync Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Spring Framework 6.2 — Async Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/annotation/Async.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-enablement-004">Kiểu trả về void, Future và CompletableFuture</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu trả về void, Future và CompletableFuture của @Async trong Spring Framework

`@Async` cho phép Spring chuyển một lời gọi phương thức sang executor để thực thi công việc theo cơ chế bất đồng bộ. Nhưng **kiểu trả về** của phương thức quyết định caller có nhận được tín hiệu hoàn thành, kết quả và exception hay không. Với Spring Framework, ba kiểu phổ biến nhất là **`void`**, **`Future<T>`** và **`CompletableFuture<T>`**. Chúng không chỉ khác cú pháp mà còn khác cách tổ chức luồng xử lý, quan sát lỗi, timeout, cancellation và phối hợp nhiều tác vụ.

Bài học này thuộc nhóm **Bật `@Async`**: tập trung vào hợp đồng kiểu trả về khi sử dụng Spring proxy, cách triển khai đúng và cách caller tiêu thụ kết quả. Các ví dụ sử dụng Java 17+ và Spring Framework 6.x; code Spring cần dependency `spring-context`, còn ví dụ Java thuần có thể biên dịch bằng JDK.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích tại sao phương thức `@Async` chỉ nên khai báo `void` hoặc kiểu `Future` được Spring hỗ trợ, và không thể đổi trực tiếp thành `String`, `int`, `ResponseEntity` rồi kỳ vọng proxy hoạt động như một lời gọi async trả giá trị.
- Chọn `void` cho tình huống không cần trả kết quả, hiểu cách báo lỗi qua `AsyncUncaughtExceptionHandler`.
- Trả về `Future<T>` khi caller cần nhận kết quả bằng `get()`, và xử lý `ExecutionException`, `TimeoutException`, `InterruptedException`.
- Dùng `CompletableFuture<T>` để áp dụng `thenApply`, `thenCompose`, `thenCombine`, `allOf`, `handle`, `exceptionally` và quan sát thất bại.
- Hiểu sự khác biệt giữa **Future tạm trong target method** và **Future thực từ Spring proxy**.
- Phân biệt lỗi **tại thời điểm gửi task** với lỗi **xảy ra trong task**, cùng tác động của timeout, cancellation và executor bão hòa.
- Tránh tự gọi `@Async` trong cùng bean, tránh block thread không cần thiết và tránh hiểu nhầm `CompletableFuture.completedFuture` hoặc `supplyAsync`.

## 2. Điều kiện để @Async thật sự hoạt động

`@Async` là một annotation của Spring, không phải từ khóa Java tự tạo thread. Trong cách dùng phổ biến, Spring đăng ký interceptor thông qua `@EnableAsync` rồi tạo proxy quanh bean. Khi bean khác gọi method **qua proxy**, interceptor quyết định executor và gửi một tác vụ tới executor đó. Lời gọi từ client thông thường trở về mà không đợi phần việc trên worker hoàn thành; ngoại lệ gồm executor chạy trực tiếp trên caller hoặc cấu hình policy đặc biệt.

Mô hình khái niệm:

~~~text
Caller thread
  |
  | gọi asyncService.compute(...)
  v
Spring proxy / @Async interceptor
  |
  | submit Callable/Runnable tới executor
  +-------------------------------+
  |                               |
  | trả về void hoặc Future        v
  |                          Worker thread
  |                           chạy target method
  |                           hoàn thành hoặc lỗi
  v                               |
Caller có thể làm việc khác       |
  |                               |
  +---- Future / callback <-------+
~~~

Để mô hình này đúng:

1. Bật `@EnableAsync` ở phần cấu hình ứng dụng (Spring Boot cũng có thể dùng nó để kích hoạt annotation-driven async).
2. Sử dụng một bean được Spring quản lý và gọi qua proxy; method `public`, có thể được override/intercept là lựa chọn dễ hiểu nhất.
3. Đặt cấu hình executor rõ ràng; có thể chỉ định bằng `@Async("reportExecutor")`.
4. Tránh `this.methodAsync()` trong cùng bean theo proxy mode mặc định: lời gọi nội bộ bỏ qua proxy và chạy như method Java bình thường.
5. Tránh đặt `@Async` trên method `private` hoặc `final` rồi kỳ vọng proxy mode thông thường chặn được lời gọi. `@Async` không hỗ trợ method khai báo trong `@Configuration` class.

**Giới hạn hợp đồng kiểu trả về:** với `@Async` theo Spring Framework 6.x, khai báo method nên là `void` hoặc một kiểu `java.util.concurrent.Future` được framework hỗ trợ, bao gồm `CompletableFuture`. Spring một số phiên bản cũ còn hỗ trợ `ListenableFuture` (API đã lỗi thời); trong code Java hiện đại, `CompletableFuture` thường phù hợp hơn. Một method đồng bộ có thể tự trả `String`; nhưng method đó không phải một chữ ký hợp lệ cho interceptor `@Async` trả giá trị bất đồng bộ.

## 3. Ba kiểu trả về: bảng so sánh nhanh

| Tiêu chí | `@Async void` | `@Async Future<T>` | `@Async CompletableFuture<T>` |
| --- | --- | --- | --- |
| Caller nhận ngay | Không có handle kết quả | Một `Future<T>` | Một `CompletableFuture<T>` |
| Đọc giá trị cuối | Không thể qua lời gọi này | `get()` | `get()`, `join()` hoặc callback |
| Quan sát kết thúc | Cần log/metric/state hoặc tín hiệu ngoài | `isDone()`, `get()` | `isDone()`, `whenComplete()`, chuỗi completion |
| Quan sát exception trong body | `AsyncUncaughtExceptionHandler` | `get()` ném `ExecutionException` | Hoàn thành exceptionally; `join()` thường ném `CompletionException` |
| Chuỗi biến đổi không chặn caller | Không có kết quả để nối | Không có API chaining chuẩn | `thenApply`, `thenCompose`, `thenCombine`, ... |
| Hủy | Không có `Future` để hủy từ caller | `cancel(...)` | `cancel(...)` và các completion method |
| Rủi ro chính | Lỗi không đến caller; dễ “fire-and-forget” mất dấu | `get()` có thể block | Callback/exception chain phức tạp; vẫn có nguy cơ block |
| Tình huống tiêu biểu | Telemetry/báo hiệu best-effort | Tích hợp API cũ cần nhận kết quả | Orchestrate nhiều bước async, trả kết quả cho dịch vụ khác |

Đừng chọn `void` chỉ để tránh viết mã xử lý lỗi. Với công việc phải đảm bảo hoàn thành, dù chọn kiểu nào bạn vẫn cần lưu trạng thái và cơ chế retry/khôi phục phù hợp.

## 4. @Async void: không có kênh kết quả cho caller

Method dạng:

~~~java
@Async("lessonExecutor")
public void sendAudit(String event) {
    auditRepository.save(event);
}
~~~

Đây là **đoạn minh họa** trong một Spring-managed bean có `auditRepository`; nó không tự chạy độc lập. Khi caller gọi qua proxy và executor nhận việc, caller thường không đợi repository lưu xong. Method trả về `void` nên caller không có `get()`, `join()`, `isDone()` hay API cancellation.

Một lỗi trong body, ví dụ `auditRepository.save` ném exception, sẽ xảy ra trong quá trình chạy tác vụ. Với `void`, Spring không có Future nào để trả lỗi cho caller nên chuyển exception chưa được xử lý tới **`AsyncUncaughtExceptionHandler`**. Mặc định lỗi thường được log; có thể cung cấp handler riêng bằng `AsyncConfigurer`.

### Cấu hình handler cho lỗi của void

~~~java
import java.lang.reflect.Method;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncErrorConfiguration implements AsyncConfigurer {
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable exception, Method method, Object... params) -> {
            System.err.println(
                    "Async void failed in " + method.getName()
                    + ": " + exception.getClass().getSimpleName());
        };
    }
}
~~~

Đây là **Spring configuration class** cần các thư viện Spring; không phải Java SE độc lập. Ứng dụng thật nên dùng logging/metrics/correlation ID có kiểm soát, không in toàn bộ params nếu chúng chứa dữ liệu nhạy cảm. Handler nên xử lý nhanh và không ném lỗi thứ cấp.

**`AsyncUncaughtExceptionHandler` không biến exception thành response cho caller.** Nếu API cần báo “job thành công/thất bại” cho người dùng, sử dụng `Future`/`CompletableFuture` hoặc một job ID trỏ tới trạng thái bền vững. Handler cũng không thay thế đường xử lý **TaskRejectedException khi không gửi được task**: rejection có thể xảy ra ngay lúc proxy submit công việc, trước khi body chạy.

### Khi nào void phù hợp?

- Công việc rất ngắn, có thể mất theo hợp đồng và đã có logging/metrics.
- Phát một sự kiện nội bộ best-effort có đường xử lý lỗi độc lập.
- Khởi động một quy trình mà trạng thái được quản lý ở một nơi khác, ví dụ job table; lúc đó `void` chỉ là giao diện dispatch chứ không phải bằng chứng hoàn thành.

Không dùng `@Async void` để hứa giao dịch tài chính, email quan trọng hoặc cập nhật đơn hàng được thực hiện chắc chắn. Một hàng đợi executor ở RAM không tạo bảo đảm durability.

## 5. @Async Future<T>: nhận handle và chủ động chờ kết quả

`Future<T>` của Java biểu diễn một phép tính có kết quả trong tương lai. Caller có thể sử dụng:

- `get()`: chờ vô hạn cho tới khi hoàn thành; khi task lỗi, ném `ExecutionException` có `getCause()` là lỗi gốc.
- `get(timeout, unit)`: chờ tối đa khoảng cho trước; khi hết thời gian, ném `TimeoutException`.
- `isDone()`: cho biết Future đã kết thúc theo một cách nào đó (thành công, lỗi hoặc bị cancel), **không** cho biết chỉ riêng thành công.
- `cancel(mayInterruptIfRunning)` và `isCancelled()`: yêu cầu/cung cấp trạng thái hủy; kết quả thực tế còn phụ thuộc implementation và việc task có hợp tác dừng.

Với `@Async Future<T>`, caller nhận một Future từ **proxy**. Target method phải giữ đúng chữ ký `Future<T>`, nên bên trong target method trả một **Future tạm** chứa giá trị vừa tính xong. Một cách đơn giản trong Java 17 là `CompletableFuture.completedFuture(value)`, có thể gán cho `Future<T>`.

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class UserLookupService {

    @Async("lessonExecutor")
    public Future<String> lookup(int userId) {
        // Giả lập công việc blocking thực sự; ở đây chỉ tính chuỗi.
        String value = "user-" + userId;
        return CompletableFuture.completedFuture(value);
    }
}
~~~

**Quan trọng:** câu lệnh `completedFuture(value)` trên được thực hiện **bên trong thread xử lý target method**; nó làm phần target thỏa chữ ký kiểu trả về. Future mà **caller nhìn thấy thông qua Spring proxy** là handle đại diện cho tác vụ thực sự được submit tới executor và có thể chưa hoàn thành. Nó không phải lời cam kết rằng toàn bộ lời gọi `@Async` đã hoàn tất đồng bộ ngay tại caller.

Một cách làm ở mã Spring cũ là `new AsyncResult<>(value)`; trong tài liệu hiện đại, `CompletableFuture.completedFuture(value)` thường đủ rõ và không cần thêm lớp helper.

### Caller lấy kết quả và xử lý exception

Đoạn dưới là **mã minh họa trong một service/controller khác** đã inject `UserLookupService userLookupService`, không phải code độc lập:

~~~java
Future<String> future = userLookupService.lookup(7);

try {
    String user = future.get(2, TimeUnit.SECONDS);
    System.out.println(user);
} catch (TimeoutException ex) {
    // Timeout chỉ giới hạn thời gian chờ; không tự dừng worker.
    future.cancel(true);
} catch (ExecutionException ex) {
    // Lỗi trong worker nằm ở ex.getCause().
    throw new IllegalStateException("Lookup failed", ex.getCause());
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Trả quyền quyết định hủy/thoát cho tầng gọi.
}
~~~

Cần import `java.util.concurrent.*` hoặc các lớp cụ thể. `Future.get()` là **blocking**: nếu gọi ngay sau `lookup()` trên request thread rồi chờ cho tới khi xong, bạn vẫn chặn thread đang phục vụ request. Việc chuyển công việc sang worker không đồng nghĩa toàn bộ request trở thành non-blocking.

`get(timeout)` ném `TimeoutException` không có nghĩa task bị hủy hoặc không bao giờ thành công; timeout là trạng thái của **người chờ**, không phải trạng thái thực thi của công việc. `cancel(true)` có thể yêu cầu interrupt task đang chạy với Future dạng thông thường, nhưng task phải phản hồi interruption và các thư viện blocking phải hỗ trợ; không có bảo đảm dừng side effect đã bắt đầu.

### Future<T> không có chaining trực tiếp

`Future` chỉ có API chờ, kiểm tra và hủy. Nó không trực tiếp cung cấp `thenApply` hoặc `thenCompose`. Vì vậy khi cần nhiều phép biến đổi liên tiếp, caller thường phải block bằng `get()` hoặc tự quản lý callback/worker bổ sung. Đây là lý do `CompletableFuture` thường tiện hơn cho code Spring hiện đại.

## 6. @Async CompletableFuture<T>: kết quả và composition

`CompletableFuture<T>` vừa triển khai `Future<T>` vừa triển khai `CompletionStage<T>`. Nó cho phép mô tả **cần làm gì khi có kết quả**, thay vì luôn buộc caller chờ.

Mẫu method Spring:

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class PricingService {

    @Async("lessonExecutor")
    public CompletableFuture<Integer> doublePrice(int price) {
        if (price < 0) {
            throw new IllegalArgumentException("price must be >= 0");
        }
        int calculated = price * 2;
        return CompletableFuture.completedFuture(calculated);
    }
}
~~~

Đây là **Spring service** cần proxy và executor hợp lệ. Trong target method, `completedFuture(calculated)` chỉ tạo handle tạm đúng chữ ký. Khi gọi từ một bean khác qua proxy:

~~~java
CompletableFuture<Integer> result = pricingService.doublePrice(21);

CompletableFuture<String> rendered =
        result.thenApply(value -> "price=" + value);
~~~

`rendered` biểu diễn phép tính nối tiếp; caller có thể trả nó cho tầng khác thay vì block. Giá trị thành công sau khi hoàn tất sẽ là `price=42`. Nếu `doublePrice(-1)` ném exception trong worker, future từ proxy sẽ hoàn thành **exceptionally**, không trả về một giá trị `Integer` thành công.

### Vì sao không nên luôn gọi supplyAsync trong @Async?

Đây là cách làm dễ gây nhầm:

~~~java
@Async("lessonExecutor")
public CompletableFuture<String> fetch() {
    return CompletableFuture.supplyAsync(() -> remoteCall());
}
~~~

Đoạn trên là **mã minh họa không độc lập**, với `remoteCall()` do ứng dụng cung cấp. `@Async` đã giao target method cho một executor. Gọi `supplyAsync` không truyền executor lại gửi tiếp công việc lên **ForkJoinPool.commonPool()** theo mặc định, tạo thêm một tầng scheduling, làm việc kiểm soát thread pool, context, cancellation và quan sát lỗi phức tạp hơn. Nếu cần compose nhiều công đoạn, hãy thiết kế rõ mỗi executor và lý do chuyển thread; nếu chỉ tính một kết quả trong `@Async` method, thường trả `CompletableFuture.completedFuture(value)` sau khi tính xong là đủ.

Điều này **không** có nghĩa `CompletableFuture.supplyAsync` luôn sai. Nó rất hữu ích trong code Java thuần hoặc trong service không dùng `@Async`, khi bạn chủ động sở hữu executor và muốn tạo một chuỗi công việc bất đồng bộ.

## 7. Các phép kết hợp CompletableFuture quan trọng

| API | Hợp đồng | Khi nên dùng |
| --- | --- | --- |
| `thenApply(fn)` | Biến đổi giá trị `T -> U`; kết quả là stage mới | Mapping, formatting, tính toán ngắn |
| `thenCompose(fn)` | Chờ một stage do `fn` trả về rồi ghép phẳng kết quả | Bước sau phụ thuộc kết quả bước trước và chính nó async |
| `thenCombine(other, fn)` | Kết hợp hai future độc lập khi cả hai hoàn thành bình thường | Ghép profile và preferences |
| `allOf(f1, f2, ...)` | Future tổng hoàn tất khi tất cả future đã hoàn tất | Đợi một tập job; tự đọc từng kết quả sau khi thành công |
| `anyOf(f1, f2, ...)` | Hoàn tất theo future đầu tiên hoàn thành | Đua các tác vụ; không tự hủy các tác vụ còn lại |
| `whenComplete((v, ex) -> ...)` | Thực hiện callback quan sát giá trị/lỗi, giữ nguyên kết quả gốc nếu callback không lỗi | Metrics, logging, cleanup quan sát |
| `handle((v, ex) -> r)` | Biến đổi cả đường thành công lẫn lỗi sang kết quả mới | Chuyển lỗi sang giá trị fallback có chủ đích |
| `exceptionally(ex -> fallback)` | Cung cấp giá trị thay thế khi lỗi | Fallback an toàn khi nghiệp vụ cho phép |

### thenApply và thenCompose: tránh Future lồng nhau

Giả sử `profileService.load(id)` trả `CompletableFuture<Profile>` và `scoreService.score(profile)` trả `CompletableFuture<Integer>`. Các kiểu `Profile`, service ở đây là **placeholder khái niệm** của hệ thống, không phải ví dụ chạy độc lập.

Sai ý định khi muốn chỉ một future cuối:

~~~java
CompletableFuture<CompletableFuture<Integer>> nested =
        profileService.load(id)
                .thenApply(profile -> scoreService.score(profile));
~~~

`thenApply` chỉ biến đổi giá trị và giữ Future mới thành **giá trị** của stage ngoài. Dùng `thenCompose` để ghép phẳng:

~~~java
CompletableFuture<Integer> score =
        profileService.load(id)
                .thenCompose(profile -> scoreService.score(profile));
~~~

Nếu hai tác vụ độc lập, có thể thực thi đồng thời rồi kết hợp:

~~~java
CompletableFuture<String> description =
        nameService.loadName(id)
                .thenCombine(scoreService.loadScore(id),
                        (name, score) -> name + ":" + score);
~~~

Các đoạn này dùng service giả định, minh họa API composition; cần các interface/service tương ứng mới biên dịch. Chú ý quan sát exception từ **cả hai nguồn** và tránh tự chặn bằng `join()` bên trong callback đang chạy trên cùng executor có ít worker.

### allOf không tạo List<T> tự động

`CompletableFuture.allOf(a, b)` trả `CompletableFuture<Void>`: nó là tín hiệu tổng hợp, không phải `List<T>`. Khi tất cả hoàn thành thành công, có thể lấy kết quả từng future. Nếu một future hoàn thành exceptionally, future tổng cũng có thể hoàn thành exceptionally. Nếu muốn hiển thị lỗi riêng từng job, hãy handle chúng trước khi ghép.

Ví dụ chỉ minh họa với future `a` và `b` có kiểu `CompletableFuture<String>`:

~~~java
CompletableFuture<Void> all = CompletableFuture.allOf(a, b);
CompletableFuture<String> summary =
        all.thenApply(ignored -> a.join() + ", " + b.join());
~~~

`join()` bên trong callback chỉ đọc kết quả đã hoàn tất thành công do `allOf` đứng trước; không dùng `join()` bừa bãi trong worker khi nó có thể phải chờ task khác chưa được lịch chạy.

## 8. Exception: void, Future và CompletableFuture truyền lỗi qua những kênh khác nhau

Ta có một target method ném `IllegalArgumentException`. Hình thức quan sát từ caller phụ thuộc kiểu trả về:

| Method | Kênh quan sát lỗi từ body | Cách xử lý |
| --- | --- | --- |
| `@Async void` | `AsyncUncaughtExceptionHandler` khi task chạy qua interceptor | Log, metrics, lưu trạng thái nghiệp vụ |
| `@Async Future<T>` | Future hoàn thành lỗi; `get()` ném `ExecutionException` | Đọc `getCause()`, phân loại lỗi, fallback hoặc fail |
| `@Async CompletableFuture<T>` | Future hoàn thành exceptionally | `handle`, `exceptionally`, `whenComplete`, hoặc `join/get` |
| Rejection trước khi task chạy | Có thể ném `TaskRejectedException` ngay tại lời gọi qua proxy | Xử lý từ chối submit, thông báo overload/persist retry |

Ví dụ xử lý lỗi với `CompletableFuture`:

~~~java
CompletableFuture<Integer> future = pricingService.doublePrice(-1);

CompletableFuture<String> message =
        future.handle((value, error) -> {
            if (error != null) {
                return "calculation failed";
            }
            return "result=" + value;
        });
~~~

Đây là **đoạn minh họa**, cần `pricingService` là Spring bean đã inject. `handle` chuyển cả đường lỗi thành một chuỗi thành công; điều này chỉ nên làm nếu fallback là một hành vi nghiệp vụ được phép. Nếu cần giữ lỗi cho tầng trên, `whenComplete` thường phù hợp hơn để log/metric mà không cố nuốt exception.

### get() và join(): khác loại exception

`Future.get()` (kể cả khi gọi trên `CompletableFuture`) có thể ném:

- `InterruptedException` nếu thread đang chờ bị interrupt.
- `ExecutionException` nếu phép tính lỗi; nguyên nhân nằm trong `getCause()`.
- `TimeoutException` với overload `get(timeout, unit)` nếu quá thời hạn chờ.
- `CancellationException` nếu future đã bị hủy.

`CompletableFuture.join()` không khai báo checked exceptions; khi tính toán thất bại thường ném `CompletionException` với cause là lỗi gốc, và khi hủy thì ném `CancellationException`. **Không nên coi `join()` là “non-blocking”**: nó vẫn chờ cho đến khi hoàn tất nếu future chưa done.

Mẫu bắt lỗi:

~~~java
try {
    int result = future.join();
    System.out.println(result);
} catch (CompletionException ex) {
    Throwable original = ex.getCause();
    System.err.println("Task failed: " + original);
} catch (CancellationException ex) {
    System.err.println("Task was cancelled");
}
~~~

Đây là **đoạn minh họa** với biến `future` kiểu `CompletableFuture<Integer>`; cần import `java.util.concurrent.CompletionException` và `CancellationException`.

### Lỗi có thể đến ngay lúc gọi @Async

Nếu executor quá tải và dùng `AbortPolicy`, `ThreadPoolTaskExecutor` của Spring thường thông báo `TaskRejectedException` **ngay lúc submit qua proxy**, trước khi có `Future` cho caller. Vì vậy nếu caller cần xử lý tình huống không tiếp nhận công việc, hãy bắt ở ranh giới gọi method:

~~~java
try {
    CompletableFuture<Integer> result = pricingService.doublePrice(21);
    // Chỉ xử lý result sau khi đã nhận được Future.
} catch (org.springframework.core.task.TaskRejectedException ex) {
    // Executor không tiếp nhận tác vụ theo policy đang dùng.
}
~~~

Đoạn này không chạy độc lập và không được nhầm với lỗi `IllegalArgumentException` xuất hiện **trong body** của `doublePrice`. Nếu policy là `CallerRunsPolicy`, việc gửi có thể chạy inline và khiến caller block; nếu là `DiscardPolicy`, tác vụ có thể bị bỏ im lặng. Kiểu Future không sửa được sự mơ hồ do một policy bỏ task không báo lỗi.

## 9. Timeout, cancellation và interruption: các mức bảo đảm

| Công cụ | Ý nghĩa | Điều không được suy diễn |
| --- | --- | --- |
| `future.get(2, SECONDS)` | Người chờ tối đa hai giây | Không có nghĩa worker dừng ở giây thứ hai |
| `future.cancel(true)` | Yêu cầu hủy; với một số Future có thể đề nghị interrupt task | Không chắc side effect đã dừng |
| `future.isCancelled()` | Future được đánh dấu hủy | Không chứng minh I/O đã rollback |
| `CompletableFuture.orTimeout(2, SECONDS)` | Hoàn thành chính CompletableFuture đó exceptionally nếu chưa xong sau thời gian quy định | Không tự ngắt công việc chạy trên executor |
| `CompletableFuture.completeOnTimeout(default, 2, SECONDS)` | Hoàn thành bằng giá trị mặc định nếu quá hạn | Không chắc tác vụ nền không tiếp tục chạy |
| `CompletableFuture.cancel(true)` | Đánh dấu `CompletableFuture` là cancelled | `mayInterruptIfRunning` không được dùng để điều khiển interruption của worker |

`CompletableFuture` là một mô hình completion; việc gắn timeout/cancel trên handle **không đồng nghĩa** đang nắm quyền ngừng thread thực tế. Với tác vụ cần dừng đúng hạn, áp deadline vào I/O client, transaction và logic xử lý; thiết kế cancellation token, interruption hợp tác hoặc khả năng idempotent nếu phù hợp.

Lưu ý: `orTimeout` và `completeOnTimeout` thay đổi/trả về **cùng CompletableFuture** theo hợp đồng API, nên nhiều consumer chia sẻ một future có thể bị ảnh hưởng bởi timeout đó. Khi không muốn thay đổi nguồn, tạo stage riêng một cách có chủ đích, nhưng cần hiểu propagation/cancellation semantics của các stage.

### Nếu bạn chờ Future trong request thread

Một lời gọi `@Async` rồi `future.get()` ngay tại controller có thể làm cả request chờ. Có thể có lý do khi cần giới hạn timeout và trả kết quả trong request, nhưng hãy đo thread occupancy và deadline. Nếu ứng dụng dùng cơ chế HTTP asynchronous response, cân nhắc trả `CompletableFuture` qua integration được framework hỗ trợ thay vì tự block; đó là chủ đề tương tác web riêng, không tự động áp cho mọi phương thức.

## 10. Mã Java 17+ độc lập để thực hành Future và CompletableFuture

Ví dụ dưới đây dùng `ExecutorService` thuần Java, **không dùng Spring**, nhằm kiểm chứng chắc chắn semantics `Future`, `CompletableFuture` và một task không trả kết quả cho caller. Sau đó sẽ đối chiếu với `@Async` trong Spring.

Tệp **FutureModesDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class FutureModesDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<String> future = executor.submit(() -> {
                started.countDown();
                release.await();
                return "ready";
            });

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Task did not start");
            }

            System.out.println("pending=" + !future.isDone());
            release.countDown();
            System.out.println("future=" + future.get(5, TimeUnit.SECONDS));

            CompletableFuture<Integer> doubled =
                    CompletableFuture.supplyAsync(() -> 21, executor)
                            .thenApply(value -> value * 2);
            System.out.println("composed=" + doubled.get(5, TimeUnit.SECONDS));

            CompletableFuture<Integer> failed =
                    CompletableFuture.supplyAsync(() -> {
                        throw new IllegalArgumentException("invalid");
                    }, executor);

            try {
                failed.join();
            } catch (CompletionException ex) {
                System.out.println(
                        "cause=" + ex.getCause().getClass().getSimpleName());
            }

            CountDownLatch voidCompleted = new CountDownLatch(1);
            executor.execute(voidCompleted::countDown);
            if (!voidCompleted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Void task did not finish");
            }
            System.out.println("voidTaskDone=true");
        } finally {
            release.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac FutureModesDemo.java
java FutureModesDemo
~~~

Stdout xác định trong điều kiện chạy thành công:

~~~text
pending=true
future=ready
composed=42
cause=IllegalArgumentException
voidTaskDone=true
~~~

Giải thích:

1. `started.await()` chứng minh task đã được worker nhận và đang chờ `release`, nên khi đọc `future.isDone()` kết quả chưa thể hoàn tất: `pending=true`.
2. `release.countDown()` cho phép worker trả `"ready"`, `future.get(5,...)` trả giá trị đúng.
3. `supplyAsync` chạy một phép tính trong executor được truyền vào; `thenApply` biến `21` thành `42`.
4. Một phép tính ném `IllegalArgumentException` khiến future hoàn thành exceptionally; `join()` bọc lỗi trong `CompletionException`, cause phản ánh lỗi gốc.
5. Task truyền cho `execute` không có giá trị trả về; ví dụ dùng latch độc lập để xác minh `voidTaskDone=true`, vì bản thân `execute` không cung cấp Future.

Nếu chuyển sang Spring, phần `@Async` đóng vai trò đưa lời gọi method qua proxy và gửi task tới executor, còn hợp đồng `Future`/`CompletableFuture` của JDK vẫn được dùng để quan sát kết quả. **Đừng copy nguyên `supplyAsync` vào mọi `@Async` method chỉ vì đây là demo Java thuần.**

## 11. Cấu hình Spring cho ví dụ ba kiểu trả về

Dưới đây là cấu hình gọn cho một ứng dụng Spring Framework có `spring-context`. Kích thước thread pool chỉ để minh họa; khi triển khai thực phải đo tải và giới hạn queue/rejection cho phù hợp.

Tệp **LessonAsyncConfig.java**:

~~~java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class LessonAsyncConfig {

    @Bean(name = "lessonExecutor")
    public ThreadPoolTaskExecutor lessonExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("lesson-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
~~~

Tệp **LessonAsyncService.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class LessonAsyncService {

    @Async("lessonExecutor")
    public void publishNotice(String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("blank notice");
        }
        System.out.println("Notice: " + text);
    }

    @Async("lessonExecutor")
    public Future<String> loadName(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("invalid id");
        }
        return CompletableFuture.completedFuture("user-" + id);
    }

    @Async("lessonExecutor")
    public CompletableFuture<Integer> calculateScore(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative value");
        }
        return CompletableFuture.completedFuture(value * 10);
    }
}
~~~

Đây là **hai file Java Spring riêng**, không có `main` và cần được Spring component-scan/import từ một ApplicationContext. Nhờ `@EnableAsync` và `@Async("lessonExecutor")`, lời gọi qua bean proxy sẽ submit công việc vào executor có tên `lessonExecutor`. `publishNotice` phù hợp cho demo tác vụ không kết quả; `loadName` trả giá trị thông qua Future; `calculateScore` trả CompletableFuture để tiếp tục composition. Ví dụ cố ý đơn giản nên không chứa truy vấn database hay side effect cần bền vững.

Tệp **LessonAsyncConsumer.java** cho một bean khác gọi service:

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

@Service
public class LessonAsyncConsumer {

    private final LessonAsyncService worker;

    public LessonAsyncConsumer(LessonAsyncService worker) {
        this.worker = worker;
    }

    public CompletableFuture<String> start(int id) {
        return worker.calculateScore(id)
                .thenApply(score -> "score=" + score);
    }
}
~~~

`start(3)` cho caller một future mà khi hoàn thành thành công có giá trị `"score=30"`. Nó không gọi `get()` hay `join()` trong service, nên không tự block để chờ kết quả. Mỗi file có tên class đúng như ví dụ. Khi chạy trong Spring Boot, cần đặt chúng trong package được component-scan, hoặc khai báo/import bean tương ứng.

**Để dùng kiểu Future thường:** một bean khác có thể gọi `worker.loadName(3)` rồi `get(timeout)` ở ranh giới thật sự cần giá trị. Không gọi `get` ngay trong cùng worker thread khi việc chờ phụ thuộc vào task khác đang chờ chính pool đó; với pool nhỏ, có thể xảy ra starvation hoặc deadlock.

## 12. “Future tạm” và “Future của proxy” không phải một đối tượng

Một điểm thường gây hiểu sai là code target:

~~~java
@Async("lessonExecutor")
public CompletableFuture<String> greeting() {
    String value = slowOperation();
    return CompletableFuture.completedFuture(value);
}
~~~

`slowOperation()` ở đây là **method giả định** được gọi trong worker của executor; đoạn này không độc lập. Có hai giai đoạn:

1. **Caller** gọi proxy `greeting()`; Spring tạo/gửi một task và trả về **Future đại diện cho công việc đó**.
2. **Worker** chạy target method, gọi `slowOperation()`, tạo `completedFuture(value)` tạm. Spring lấy giá trị/trạng thái từ handle tạm rồi hoàn thành handle đại diện mà caller đang giữ.

Trong một hệ thống có công việc chạy lâu, caller có thể thấy `future.isDone()==false` ngay cả khi dòng `return CompletableFuture.completedFuture(value)` xuất hiện trong code target. Dòng return chưa chạy cho đến khi work bên trong method hoàn tất.

Cần tránh sai lầm “Spring @Async vô dụng vì method trả completedFuture”. `completedFuture` đúng vai trò **thỏa chữ ký ở target**, còn việc gọi qua proxy mới tạo hợp đồng async cho client.

Nếu tự gọi `this.greeting()` trong chính class target, **proxy bị bỏ qua**; lúc này bạn nhận trực tiếp completedFuture do target tạo, và `slowOperation()` chạy ngay trên thread gọi, không có tính async như dự kiến.

## 13. Chaining đồng bộ hay bất đồng bộ: thenApply và thenApplyAsync

`CompletableFuture.thenApply(fn)` không yêu cầu tạo thread mới. Callback có thể chạy trên thread hoàn thành stage trước hoặc thread đăng ký callback khi stage đã hoàn tất; không dựa vào tên thread cụ thể. Nếu callback nặng, nó có thể chiếm worker đang hoàn thành việc khác.

`thenApplyAsync(fn)` lịch callback sang một executor. Nếu **không truyền executor**, Java thường sử dụng `ForkJoinPool.commonPool` cho async completion methods; điều này có thể khác pool `@Async` đã cấu hình.

~~~java
CompletableFuture<String> formatted =
        worker.calculateScore(3)
                .thenApplyAsync(
                        score -> "score=" + score,
                        formattingExecutor);
~~~

Đây là đoạn minh họa trong bean có `worker` và `formattingExecutor` (kiểu `Executor`) được inject. Khi chọn executor riêng, bạn phải quản lý pool size, queue, rejection, shutdown và context propagation ở **cả hai**. Nếu callback ngắn, `thenApply` có thể đủ. Nếu callback blocking hoặc CPU nặng, cân nhắc `thenApplyAsync` với executor phù hợp.

Không mặc định rằng `thenApplyAsync` luôn nhanh hơn; nó có thêm chi phí scheduling, có thể gặp `RejectedExecutionException` khi executor dùng để chạy callback không nhận việc, và có thể tăng tổng concurrency ngoài dự kiến.

## 14. @Async và transaction, ThreadLocal, MDC

Thực thi trên thread khác có nghĩa nhiều ngữ cảnh gắn với thread gửi **không tự kế thừa**:

- Transaction resource/`@Transactional` thường gắn với thread; method `@Async` chạy trong thread riêng không tự thừa hưởng transaction của caller. Nếu cần transaction riêng ở worker, định nghĩa boundary phù hợp cho công việc đó.
- Security context, MDC/trace context và các `ThreadLocal` khác thường cần truyền theo cách có chủ đích (`TaskDecorator`, cơ chế của framework, hoặc dữ liệu bất biến qua tham số).
- Một callback `thenApply` có thể chạy ở thread khác với chỗ đăng ký; đừng dùng ThreadLocal ngầm để quyết định nghiệp vụ quan trọng.

Nếu gọi `@Async` trong một transaction trước khi commit, worker có thể chạy trước khi dữ liệu cần đọc đã commit. Với yêu cầu “chỉ làm sau khi giao dịch đã thành công”, cân nhắc after-commit event processing hoặc **transactional outbox** nếu cần độ bền khi ứng dụng crash.

Một lỗi khác: đặt `@Transactional` vào method `@Async` rồi nghĩ transaction của caller tự được truyền. Trên thực tế việc intercept, mở transaction và thread chạy phải được thiết kế/kiểm thử theo thứ tự advice; đừng dựa vào giả định.

## 15. Khi tải tăng: kiểu trả về không bảo đảm tác vụ được nhận

`@Async` cần executor đủ sức xử lý. Nếu pool bão hòa hoặc đã shutdown:

- `AbortPolicy` thường gây `TaskRejectedException` qua `ThreadPoolTaskExecutor` khi submit; caller phải xử lý tình huống **không nhận được việc**, và có thể chưa có `Future` nào để đợi.
- `CallerRunsPolicy` có thể làm tác vụ chạy **ngay trên caller thread** khi pool bão hòa: kể cả method có `@Async`, việc gọi có thể mất thời gian tương ứng với body, phá kỳ vọng phản hồi nhanh.
- `DiscardPolicy` có thể bỏ task im lặng; `Future` có thể không bao giờ hoàn thành nếu bị discard mà không hủy.
- Khi shutdown, `CallerRunsPolicy` có thể bỏ task im lặng theo JDK, không chạy trên caller nữa.

**Không nhầm một `Future` với lời hứa bền vững.** Một task được nhận vào queue RAM có thể mất khi tiến trình chết. Nếu cần đảm bảo xử lý báo cáo, thông báo quan trọng hoặc đơn hàng, dùng job table/queue bền vững, retry, idempotency và trạng thái để truy vấn.

Đặc biệt với HTTP 202 Accepted: chỉ xác nhận “đã nhận” ở mức mà ứng dụng thật sự bảo đảm. Nếu không có job store bền vững thì response không nên hứa chắc sẽ xử lý sau restart.

## 16. Test @Async có đáng tin hay không?

Đừng dùng `Thread.sleep(100)` rồi giả định worker đã hoàn thành. Các phương án tốt hơn:

1. **Đúng proxy**: inject bean có `@Async` từ bean/test khác; không gọi trực tiếp method trên `new LessonAsyncService()` rồi tưởng đã kiểm thử Spring.
2. **Future/CompletableFuture**: kiểm tra giá trị bằng `get(timeout, unit)` để test không treo; assert nguyên nhân lỗi khi `get` ném `ExecutionException` hoặc `join` ném `CompletionException`.
3. **void**: dùng `CountDownLatch` hoặc một recorder thread-safe/metric để xác nhận completion; kiểm thử đường `AsyncUncaughtExceptionHandler` riêng.
4. **Lỗi submit**: tạo pool 1 worker + queue 1, dùng latch giữ worker để bảo đảm queue đầy, sau đó submit thêm và xác minh rejection tại caller.
5. **Timeout/cancel**: khống chế bằng latch, xác định future chuyển trạng thái thế nào và task thực sự có dừng hay không.
6. **Thread context**: xác minh transaction/MDC/security context theo cả đường worker, caller-runs hoặc callback thread đã chọn.
7. **Shutdown**: kiểm thử đã nhận nhưng chưa chạy, hủy, drain queue và retry từ nơi lưu bền vững.

Ví dụ JUnit 5 **minh họa** (cần lớp Spring bean, test context và imports tương ứng):

~~~java
CompletableFuture<Integer> task = lessonAsyncService.calculateScore(4);
assertEquals(40, task.get(2, TimeUnit.SECONDS));

CompletableFuture<Integer> bad = lessonAsyncService.calculateScore(-1);
ExecutionException error = assertThrows(
        ExecutionException.class,
        () -> bad.get(2, TimeUnit.SECONDS));
assertInstanceOf(IllegalArgumentException.class, error.getCause());
~~~

Ví dụ cần `assertEquals`, `assertThrows`, `assertInstanceOf` từ JUnit Jupiter và biến `lessonAsyncService` được inject **qua Spring context**. Đây không phải file JUnit đầy đủ, nên không có lệnh `javac` độc lập. Mục tiêu là assert kết quả/lỗi thay vì phụ thuộc thời gian ngủ.

## 17. Lỗi phổ biến và cách sửa

1. **Đánh dấu `@Async` rồi khai báo trả `String`.** Kiểu trả về không phù hợp với interceptor; trả `Future<String>` hoặc `CompletableFuture<String>`.
2. **Không bật `@EnableAsync`, không dùng Spring bean hoặc gọi nội bộ `this.method()`.** Proxy không chạy, lời gọi có thể trở thành đồng bộ.
3. **Dùng `void` cho công việc cần biết kết quả.** Caller không nhận tín hiệu; thay bằng Future hoặc job status bền vững.
4. **Cho rằng exception của `@Async void` sẽ ném về controller như lỗi đồng bộ.** Lỗi body đi qua `AsyncUncaughtExceptionHandler` trong đường thông thường.
5. **Gọi `get()` ngay sau submit tại request thread.** Caller lại bị block; chỉ chờ khi thật sự cần và dùng deadline.
6. **Không xử lý `InterruptedException` đúng cách.** Thường khôi phục cờ bằng `Thread.currentThread().interrupt()` nếu không ném tiếp, rồi hủy/thoát theo hợp đồng.
7. **Đánh đồng `isDone()` với thành công.** Future hoàn thành lỗi hoặc bị hủy vẫn có thể `isDone()==true`.
8. **Nhầm `TimeoutException` với việc worker bị dừng.** Timeout của người chờ không tự hủy hoặc rollback task.
9. **Nhầm `cancel(true)` của `CompletableFuture` với interrupt chắc chắn.** Tham số này không điều khiển interruption như một `FutureTask` truyền thống.
10. **Dùng `supplyAsync` bên trong mọi `@Async` method.** Có thể tạo scheduling hai tầng và dùng nhầm commonPool.
11. **Dùng `thenApply` cho hàm trả Future rồi nhận Future lồng nhau.** Dùng `thenCompose` khi bước sau cũng async.
12. **Dùng `exceptionally` trả fallback cho mọi lỗi và che mất sự cố nghiệp vụ.** Fallback phải được thiết kế, log/metric và phân loại hợp lý.
13. **Block `join()` trong callback chạy trên executor ít thread.** Có thể chặn toàn bộ pool khi đang đợi task cũng cần chính pool đó.
14. **Dựa vào context/transaction của request trong worker.** Thread-bound state không tự truyền theo.
15. **Bỏ qua `TaskRejectedException` ở ranh giới gọi.** Task có thể chưa được nhận; không trả “đã xử lý” giả.
16. **Mặc định CompletableFuture làm mọi thứ non-blocking.** Future là handle; body vẫn có thể block một worker, callback có thể chạy inline, `join/get` vẫn block caller.
17. **Không đo queue, active threads, timeout, completion failure.** Khi overload, biến động latency và lost task có thể xuất hiện trước khi metrics tổng thể báo vấn đề.
18. **Cho rằng một exception từ task Future sẽ đi qua `AsyncUncaughtExceptionHandler` như `void`.** Với Future, lỗi được lưu vào Future và nên được consumer xử lý.

## 18. Chọn kiểu nào trong thực tế?

| Yêu cầu | Kiểu phù hợp ban đầu | Lưu ý |
| --- | --- | --- |
| Ghi telemetry best-effort, không cần trả kết quả | `void` | Phải quan sát lỗi và chính sách mất event |
| Trả kết quả từ một API legacy cần chờ | `Future<T>` | Caller dùng `get(timeout)`; tránh block luồng nhạy latency |
| Biến đổi kết quả trong pipeline nhiều bước | `CompletableFuture<T>` | `thenApply/thenCompose` và xử lý exceptional completion |
| Ghép hai nguồn dữ liệu độc lập | `CompletableFuture<T>` | `thenCombine` hoặc `allOf`, quản lý lỗi cả hai nhánh |
| Hệ thống cần hủy/timeout rõ ràng | `Future` hoặc `CompletableFuture` cùng cancellation protocol | Handle không tự đảm bảo thread dừng hoặc side effect rollback |
| Job bắt buộc không mất qua restart | Job ID + durable queue/job store, có thể bổ sung `CompletableFuture` cho request hiện tại | Future trong RAM không phải nguồn sự thật |
| API muốn trả kết quả HTTP bất đồng bộ | `CompletableFuture<T>` trong integration phù hợp | Cần xét behavior web framework và deadline client |

Không có kiểu trả về duy nhất phù hợp cho mọi bài toán. Lựa chọn nên dựa trên **nghĩa của kết quả**, **ai phải biết lỗi**, **cách caller chờ hay compose**, **deadline**, và **hợp đồng khi task không được executor tiếp nhận**.

## 19. Câu hỏi tự kiểm tra

1. Vì sao `@Async public String load()` không phải một chữ ký trả kết quả bất đồng bộ hợp lệ của Spring proxy?
2. Với `@Async void`, exception trong worker được quan sát ở đâu? Vì sao controller thường không bắt được trực tiếp?
3. Bên trong target method, tại sao có thể trả `CompletableFuture.completedFuture(value)` nhưng caller vẫn nhận Future chưa hoàn thành?
4. `Future.get(2, SECONDS)` timeout có chắc task đã dừng không?
5. Sự khác nhau giữa `Future.get()` và `CompletableFuture.join()` về kiểu exception khi task lỗi là gì?
6. Khi nào dùng `thenCompose` thay vì `thenApply`?
7. Vì sao `CompletableFuture.supplyAsync(...)` không truyền executor trong method đã `@Async` có thể làm code khó kiểm soát?
8. Nếu `@Async("lessonExecutor")` bị pool rejection trước khi submit, Future sẽ xuất hiện ở caller không? Bạn sẽ báo lỗi thế nào?
9. Vì sao gọi `this.calculateScore(3)` trong cùng bean không chứng minh method chạy async?
10. Nếu method `@Async CompletableFuture` cần transaction và truy cập security context, bạn kiểm tra những ranh giới nào?
11. `CompletableFuture.cancel(true)` có đảm bảo ngắt một HTTP call đang chạy không?
12. Với job cần bảo đảm hoàn thành sau khi ứng dụng restart, Future phải kết hợp thêm thành phần nào?

## 20. Tài liệu tham khảo chính thức

- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Spring Framework 6.1 — @Async API](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/annotation/Async.html)
- [Spring Framework 6.1 — @EnableAsync API](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Spring Framework 6.1 — AsyncConfigurer API](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/annotation/AsyncConfigurer.html)
- [Spring Framework 6.1 — ThreadPoolTaskExecutor API](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)

**Ghi nhớ:** `void` không cho caller một kênh kết quả; `Future` cung cấp handle để chờ, xem trạng thái và hủy; `CompletableFuture` bổ sung composition và xử lý lỗi bằng callback. **`@Async` quyết định cách chuyển lời gọi qua executor, kiểu trả về quyết định hợp đồng quan sát kết quả.** Muốn có semantics đáng tin cậy, cần thêm timeout, error handling, thread context và admission/durability đúng yêu cầu nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-enablement-005">Giới hạn dùng @Async trên phương thức trong lớp @Configuration</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn dùng @Async trên phương thức trong lớp @Configuration

## 1. Mục tiêu và vấn đề cần phân biệt

Trong Spring Framework, `@Configuration` có nhiệm vụ **khai báo và cấu hình bean**, còn `@Async` dùng để **gọi phương thức của một bean theo cách bất đồng bộ** thông qua cơ chế xử lý của Spring. Hai annotation này có thể xuất hiện trong cùng một ứng dụng, nhưng có một giới hạn dễ gây nhầm:

> **Spring không hỗ trợ `@Async` trên phương thức được khai báo bên trong một lớp `@Configuration`.**

Đây là nội dung được ghi trực tiếp trong Javadoc của `org.springframework.scheduling.annotation.Async` (bao gồm tài liệu Spring 5.3 và 6.x). Đừng suy luận rằng một phương thức trong lớp cấu hình sẽ chuyển sang worker thread chỉ vì đã gắn `@Async` và bật `@EnableAsync`.

Sau bài này, bạn có thể:

- Phân biệt **`@EnableAsync` đặt trên lớp `@Configuration` (đúng, được hỗ trợ)** với **`@Async` đặt trên phương thức bên trong chính lớp đó (không được hỗ trợ)**.
- Hiểu trách nhiệm của `@Configuration`/`@Bean` khi tạo bean và trách nhiệm của `@Async` khi xử lý lời gọi nghiệp vụ.
- Chuyển công việc bất đồng bộ sang bean `@Service`/`@Component` riêng, được Spring quản lý và gọi **qua proxy**.
- Tạo ví dụ Spring Framework 6.1.3, Java 17+ thực sự chạy được và xác minh thread thực thi.
- Phân biệt giới hạn này với các lỗi khác: tự gọi phương thức trong cùng instance, gọi quá sớm ở lifecycle, chưa bật async processing hoặc chọn nhầm executor.
- Kiểm tra xử lý lỗi, kiểu trả về `Future`/`CompletableFuture`, và lập kế hoạch refactor mà không phá vỡ khởi tạo bean.

**Phạm vi:** Spring Framework, Java-based configuration và `@Async` trong chế độ proxy thông dụng. Ví dụ dùng Java 17+ và Spring Framework 6.1.3, nhưng quy tắc **không hỗ trợ `@Async` trên method khai báo trong `@Configuration`** đã có trong Javadoc Spring 5.3 và vẫn có trong Spring 6.x. Nếu ứng dụng sử dụng AspectJ weaving, vẫn nên tổ chức theo các bean nghiệp vụ riêng và kiểm tra tài liệu phiên bản; đừng xem weaving là cách bỏ qua giới hạn được ghi rõ của annotation.

## 2. Quy tắc cốt lõi: nơi bật và nơi thực thi

### 2.1. Cách đúng: `@EnableAsync` trên lớp cấu hình

~~~java
@Configuration
@EnableAsync
public class AsyncConfig {
    // Các @Bean cấu hình executor hoặc dependency khác.
}
~~~

Đây là **fragment minh họa**, chưa phải một Java file hoàn chỉnh (cần import hai annotation). `@EnableAsync` kích hoạt cơ chế xử lý lời gọi bất đồng bộ trong **Spring application context**. Đặt nó trên lớp `@Configuration` là cách dùng được tài liệu Spring khuyến nghị.

`@EnableAsync` **không tự chạy mọi phương thức của lớp cấu hình trên worker thread**. Nó đăng ký thành phần xử lý annotation, để các phương thức `@Async` **hợp lệ trên bean khác** được Spring áp dụng interceptor/proxy theo cấu hình.

### 2.2. Cách sai: `@Async` nằm trên method khai báo trong `@Configuration`

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class WrongConfig {
    @Async
    public CompletableFuture<String> generateReport() {
        return CompletableFuture.completedFuture("report");
    }
}
~~~

Đoạn trên là **phản ví dụ về cách tổ chức**, dù cú pháp Java trông có vẻ hợp lệ. Theo hợp đồng annotation Spring, `@Async` **không được hỗ trợ trên phương thức khai báo trong lớp `@Configuration`**. Không được dựa vào đó để bảo đảm `generateReport()` chạy bất đồng bộ, không lấy một lần chạy ngẫu nhiên làm bằng chứng được hỗ trợ.

Một phản ví dụ liên quan là gắn `@Async` lên **factory method `@Bean`**:

~~~java
// Phản ví dụ: @Async không nên nằm trên method @Bean
@Configuration
class WrongFactoryConfig {
    @Bean
    @Async
    public ReportService reportService() {
        return new ReportService();
    }
}
~~~

Đây **không phải mã độc lập** và chưa cung cấp định nghĩa `ReportService`. `@Bean` là phương thức **tạo đối tượng để container đăng ký**; gắn `@Async` vào chính phương thức khởi tạo này không biến các phương thức nghiệp vụ của bean được trả về thành async. Hơn nữa, `@Async` quy định kiểu trả về method phải là `void` hoặc loại `Future` được hỗ trợ; ở ví dụ trên method trả `ReportService` còn không phù hợp hợp đồng đó. Hãy gắn `@Async` vào **phương thức của service được trả về**, nếu service thật sự cần xử lý nền.

### 2.3. Điều gì vẫn được phép?

| Vị trí đặt annotation | Được hỗ trợ? | Ý nghĩa |
| --- | --- | --- |
| `@Configuration` + `@EnableAsync` trên lớp cấu hình | **Có** | Bật hỗ trợ xử lý `@Async` cho application context |
| `@Bean` tạo `ThreadPoolTaskExecutor` trong lớp `@Configuration` | **Có** | Cấu hình executor được Spring quản lý |
| `@Async` trên method khai báo trong chính lớp `@Configuration` | **Không** | Không nằm trong phạm vi hỗ trợ của annotation |
| `@Async` trên method `@Bean` để cố chạy factory method nền | **Không dùng** | Không đúng trách nhiệm của bean factory method |
| `@Async` trên method của `@Service` hoặc `@Component` do Spring quản lý | **Có**, nếu qua đúng đường gọi | Method được Spring proxy/interceptor đưa sang executor |
| Một method `@Bean` trả về service có method `@Async` riêng | **Có** với service đủ điều kiện | Factory method là đồng bộ; method của **bean trả về** có thể bất đồng bộ |
| `@Async` ở cấp lớp `@Configuration` | **Không dùng để async hóa config** | Class-level `@Async` cũng không làm các method cấu hình trở thành API async được hỗ trợ |

Điểm khác biệt là **lớp khai báo method thực tế**. Nếu `AppConfig` khai báo `@Bean ReportService reportService()` và method `buildReport()` nằm trong **lớp `ReportService`**, giới hạn “method declared within `@Configuration`” không áp dụng lên `buildReport()` chỉ vì `ReportService` được tạo bởi method `@Bean`.

## 3. Vì sao hai cơ chế khác nhau?

### 3.1. Vai trò của `@Configuration` và `@Bean`

Lớp `@Configuration` diễn tả **metadata và các bean definition**. Container dùng các method `@Bean` để khởi tạo dependency, đặt tên, xác định scope và quản lý lifecycle. Với `@Configuration(proxyBeanMethods=true)` mặc định, Spring có thể **tăng cường (enhance)** lớp bằng subclass CGLIB để lời gọi giữa các method `@Bean` tuân theo ngữ nghĩa singleton/scoping.

Cơ chế tăng cường đó phục vụ **quản lý bean**, không đồng nghĩa với việc Spring `@Async` đã bao quanh các method cấu hình như các business method trên service. Một factory method bị gọi trong lúc container đang khởi tạo bean càng không nên trở thành một lời gọi nền trả về trước khi bean được tạo xong.

### 3.2. Vai trò của `@Async` và async interceptor

Khi Spring bật `@EnableAsync`, trong chế độ mặc định `AdviceMode.PROXY`, những lời gọi tới method `@Async` hợp lệ **đi qua Spring proxy** sẽ được gửi đến một `TaskExecutor`. Caller nhận kết quả theo kiểu trả về khai báo; work thực chạy ở worker thread.

~~~text
             LÚC KHỞI ĐỘNG CONTEXT
@Configuration -- @Bean --> Spring đăng ký / tạo executor và service
      |
      +-- @EnableAsync --> đăng ký async annotation processing

             LÚC GỌI NGHIỆP VỤ
Controller / caller
      |
      v
Service bean lấy từ Spring context
      |
      v
Spring proxy / async interceptor
      |
      +-- submit task --> TaskExecutor --> worker thread
      |
      +-- caller nhận void / Future / CompletableFuture
~~~

**Không có bước “Spring async hóa method trong `@Configuration`” ở sơ đồ trên.** Cấu hình chỉ **bật cơ chế và cung cấp bean**; logic nghiệp vụ nằm trong một bean riêng mà interceptor có thể xử lý đúng hợp đồng.

### 3.3. Một lớp có thể vừa là config vừa là service?

Về mặt Java, ta có thể đặt nhiều method với nhiều annotation chung một class, nhưng ở Spring bạn không nên biến lớp `@Configuration` thành nơi chứa business methods `@Async`. Lý do cốt lõi là **API `@Async` nói rõ không hỗ trợ method khai báo tại đó**. Tách responsibilities còn giúp bootstrap rõ ràng, test dễ hơn và tránh phụ thuộc vào chi tiết proxy/khởi tạo của config class.

Đặt `@EnableAsync(proxyTargetClass=true)` không giải quyết giới hạn này: thuộc tính đó chỉ thay đổi chiến lược proxy trong `PROXY` mode. Tương tự, `@Configuration(proxyBeanMethods=false)` điều chỉnh khả năng proxy của các method `@Bean`; nó **không biến** method `@Async` trong config thành trường hợp được hỗ trợ.

## 4. Cấu trúc đúng: config tạo executor, service khai báo @Async

Ví dụ kiến trúc:

~~~text
demo
├── AppConfig.java
│   ├── @Configuration
│   ├── @EnableAsync
│   └── @Bean("lessonExecutor"): ThreadPoolTaskExecutor
├── ReportService.java
│   └── @Service + @Async("lessonExecutor") trên generate()
└── AsyncConfigurationDemo.java
    └── lấy ReportService từ Spring context và gọi generate()
~~~

Executor được cấu hình **một lần**; business service do Spring quản lý. Điều quan trọng là caller **lấy bean từ context**, tránh tự `new ReportService()` rồi mong annotation được xử lý. Việc tạo service bằng `@Bean` cũng hợp lệ nếu trả về bean đúng cách; ví dụ chọn `@Service` để phân chia trách nhiệm dễ thấy.

### 4.1. Maven project chạy độc lập

Dùng **JDK 17+**, Maven và Spring Framework 6.1.3. Chương trình không cần Spring Boot server; Spring Framework thuần đủ để minh họa proxy.

Cấu trúc file:

~~~text
async-config-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                ├── AppConfig.java
                ├── ReportService.java
                └── AsyncConfigurationDemo.java
~~~

Tệp `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>async-config-demo</artifactId>
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
                    <mainClass>demo.AsyncConfigurationDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Dependency `spring-context` kéo theo các dependency Spring cần cho `@EnableAsync`, AOP và `ThreadPoolTaskExecutor` trong ví dụ. Ứng dụng Spring Boot thực tế nên để BOM/parent quản lý các phiên bản Spring thống nhất.

### 4.2. Cấu hình trong `AppConfig.java`

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AppConfig {

    @Bean("lessonExecutor")
    public ThreadPoolTaskExecutor lessonExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("lesson-");
        return executor;
    }
}
~~~

`@EnableAsync` nằm **đúng chỗ**, trên class cấu hình. `@Bean` tạo một `ThreadPoolTaskExecutor` được Spring container quản lý. Đáng chú ý: **không có `@Async` trên method `lessonExecutor()`**; method này chỉ tạo và cấu hình executor.

Ở đây pool nhỏ để minh họa, không phải thông số mặc định nên dùng trong production. Trong dự án thực tế hãy cấu hình số worker, queue, rejection và shutdown theo workload.

### 4.3. Business method trong `ReportService.java`

~~~java
package demo;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    @Async("lessonExecutor")
    public CompletableFuture<String> generate(String name) {
        boolean onWorker =
                Thread.currentThread().getName().startsWith("lesson-");

        String value = name.toUpperCase(Locale.ROOT)
                + "|worker=" + onWorker;

        return CompletableFuture.completedFuture(value);
    }
}
~~~

Chính method `generate` nằm trên **`ReportService`**, một bean độc lập với `AppConfig`. `@Async("lessonExecutor")` chọn executor có tên `lessonExecutor`. Ta đặt tiền tố thread để kiểm tra method đã chạy ở worker thread được cấu hình.

**Tại sao target method trả `CompletableFuture.completedFuture(...)`?** Với `@Async`, Spring proxy nộp lời gọi của method vào executor và trả một `CompletableFuture` đại diện cho việc thực thi ở mức proxy. Method trong target thực sự chạy trên worker có thể trả một future tạm thời đã hoàn tất. Điều đó **không** có nghĩa caller đã tự chạy target method đồng bộ. Spring sẽ lấy kết quả của target method qua cơ chế interceptor.

### 4.4. Bootstrap trong `AsyncConfigurationDemo.java`

~~~java
package demo;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class AsyncConfigurationDemo {

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             AppConfig.class,
                             ReportService.class)) {

            ReportService service = context.getBean(ReportService.class);

            String result =
                    service.generate("spring").get(5, TimeUnit.SECONDS);

            System.out.println(result);
        }
    }
}
~~~

Trong thư mục `async-config-demo` chạy:

~~~bash
mvn -q compile exec:java
~~~

Đầu ra do chương trình Java in (không tính log build nếu Maven/plugin có hiển thị):

~~~text
SPRING|worker=true
~~~

**Vì sao output đáng tin?** Chương trình tạo một bean executor có tiền tố thread `lesson-`, lấy `ReportService` **từ Spring context** (nên có async interception), gọi method annotated `@Async("lessonExecutor")`, rồi chờ future bằng `get(5, TimeUnit.SECONDS)`. Trong task, thread hiện tại có tên bắt đầu bằng tiền tố đó, nên phần `worker=true` được hình thành tại **worker**. `get` chờ kết quả và có timeout để bài demo không chờ vô hạn nếu xảy ra lỗi.

Với môi trường quá chậm, `get(5, ...)` có thể ném `TimeoutException`; đó là lỗi timing/bất khả dụng cần điều tra, không phải dòng output thứ hai. Khi tự tạo context trong `main`, `try-with-resources` đóng context và cho container dọn dẹp executor bean; **không tự gọi `shutdown()` bên trong `generate`**.

Ví dụ này có ba Java file được biên dịch cùng package. Các phản ví dụ ở phần 2 là fragment phân tích **không** thuộc project này.

## 5. Phân biệt ba vấn đề thường bị trộn lẫn

### 5.1. Giới hạn đặc biệt của `@Configuration`

**Quy tắc:** Method được **khai báo bên trong `@Configuration`** không được Spring `@Async` hỗ trợ. Đây là điều kiện theo **loại lớp chứa declaration**, chứ không phải chỉ do caller quên dùng proxy.

**Cách sửa:** Di chuyển method nghiệp vụ sang một service/component bean, giữ `@Configuration` để bật `@EnableAsync` và định nghĩa executor/dependency. Gọi service đó qua Spring proxy.

### 5.2. Self-invocation trên một bean thường

Ngay cả trên một class `@Service` đủ điều kiện, lời gọi từ method này sang method khác **trong cùng instance** cũng thường bỏ qua Spring proxy khi dùng `AdviceMode.PROXY` mặc định.

~~~java
@Service
public class DataService {
    public void start() {
        calculate(); // this.calculate(), không đi qua proxy
    }

    @Async
    public void calculate() {
        // Không được chuyển thread khi gọi trực tiếp từ start() như trên.
    }
}
~~~

Đây là **fragment minh họa**, cần import `Service` và `Async`; không phải chương trình độc lập. Khác với giới hạn `@Configuration`, method `calculate()` ở đây *có thể được hỗ trợ* khi một bean khác gọi qua **proxy `DataService`**. Cách sửa thông thường là tách lời gọi sang bean khác và inject service đã được Spring quản lý, hoặc thiết kế interface cho async boundary.

Với self-invocation của service bình thường, Spring còn có `AdviceMode.ASPECTJ` và weaving như một lựa chọn nâng cao. Nó cần dependency/cấu hình riêng và không nên được coi là lý do giữ business `@Async` trong `@Configuration`.

### 5.3. Lifecycle và khởi tạo bean

Đừng đặt `@Async` trực tiếp lên `@PostConstruct` và kỳ vọng nó chạy nền: tài liệu Spring ghi rõ hai cơ chế không được kết hợp. Lúc khởi tạo bean, proxy và dependencies có thể chưa sẵn sàng để thực hiện lời gọi bất đồng bộ theo thiết kế.

Nếu cần warm-up hoặc xử lý sau startup, để **một bean khởi tạo khác** nhận lifecycle/event rồi gọi method `@Async` trên **bean service** được inject. Với Spring Boot có thể dùng listener `ApplicationReadyEvent` khi việc đó thực sự phải bắt đầu sau khi ứng dụng sẵn sàng; chọn event và chính sách lỗi theo nhu cầu ứng dụng. Không tự gọi một async method trong constructor hoặc `@PostConstruct` của chính đối tượng đó rồi mong proxy can thiệp.

### Bảng chẩn đoán

| Dấu hiệu | Nguyên nhân cần kiểm tra | Hướng xử lý |
| --- | --- | --- |
| `@Async` đặt trên method trong `@Configuration` | Method thuộc vùng không được hỗ trợ | Chuyển sang service bean |
| Method `@Async` của service vẫn chạy ở caller | Self-invocation, object tạo bằng `new`, chưa bật `@EnableAsync` hoặc proxy không được đi qua | Lấy bean từ Spring context, gọi qua proxy, kiểm tra cấu hình |
| `@Async("name")` không dùng đúng thread pool | Executor bean sai tên/qualifier hoặc cấu hình khác | Kiểm tra `@Bean("name")`, injection và thread prefix |
| Method `@Async` bị lỗi trong khi chạy | Exception trong task và cách nhận lỗi qua `Future`/`CompletableFuture` | Theo dõi future hoặc `AsyncUncaughtExceptionHandler` nếu `void` |
| Async method bị từ chối trước khi chạy | Executor quá tải/đã shutdown | Xử lý `TaskRejectedException` ở điểm submit, kiểm tra queue và lifecycle |
| Gọi async trong quá trình dựng bean | Lifecycle chưa phù hợp | Gọi từ bean khác sau khi context sẵn sàng |

## 6. Kiểu trả về và xử lý lỗi của @Async

Spring `@Async` hỗ trợ các phương thức có kiểu trả về `void` hoặc một `Future` hợp lệ, bao gồm `CompletableFuture<T>`. Những method muốn trả dữ liệu nghiệp vụ nên dùng `CompletableFuture<T>` để caller có thể chờ, ghép kết quả và xử lý lỗi.

~~~java
// Fragment trong @Service với async proxy và executor đã cấu hình.
@Async("lessonExecutor")
public CompletableFuture<Integer> calculate(int x) {
    if (x < 0) {
        throw new IllegalArgumentException("x must be non-negative");
    }
    return CompletableFuture.completedFuture(x * 2);
}
~~~

Đây là **fragment**, không phải class độc lập. Nếu task được nhận nhưng ném exception trong worker, `CompletableFuture` mà caller nhận có thể hoàn tất thất bại. Caller dùng `get()` để nhận `ExecutionException` (kiểm tra cause) hoặc `join()` để nhận `CompletionException`; cần tôn trọng interrupt khi dùng `get()`.

~~~java
// Fragment trong caller, method khai báo throws InterruptedException.
CompletableFuture<Integer> future = service.calculate(21);
try {
    System.out.println(future.get()); // 42
} catch (java.util.concurrent.ExecutionException ex) {
    System.err.println("Task failed: " + ex.getCause());
}
~~~

Không dùng `@Async` trên method trả trực tiếp `String`/`int` và mong Spring tự bọc giá trị như một Future; đó không phải kiểu trả về được hỗ trợ theo hợp đồng annotation.

Với method `@Async void`, caller không có Future để nhận lỗi từ worker. Spring cho phép cấu hình `AsyncUncaughtExceptionHandler` qua `AsyncConfigurer`; mặc định lỗi thường được log. Trong ứng dụng production, đừng để các tác vụ quan trọng thất bại mà không có cơ chế quan sát/truyền trạng thái.

Một lỗi khác xảy ra **ngay lúc nộp task**: executor có thể ném `TaskRejectedException` nếu pool quá tải hoặc shutdown. Khi điều đó xảy ra, chưa có công việc đã được nhận; riêng `future.exceptionally(...)` không thay cho catch rejection ở ranh giới caller. Cấu hình queue/backpressure và chính sách xử lý quá tải là phần riêng của thiết kế async.

## 7. Refactor an toàn: từ cấu hình trộn nghiệp vụ sang hai bean

Giả sử project cũ có method nghiệp vụ `sendInvoice()` bên trong `@Configuration` và gắn `@Async`. Cách sửa theo thứ tự:

1. **Đặt lại trách nhiệm:** Giữ `@Configuration` để định nghĩa bean và `@EnableAsync`. Di chuyển xử lý gửi hóa đơn sang `InvoiceSender` hoặc một service phù hợp.
2. **Giữ hợp đồng dữ liệu:** Method ở service dùng `CompletableFuture<Result>` khi cần kết quả, hoặc `void` nếu thật sự fire-and-forget và có xử lý lỗi riêng. Không đổi chữ ký public một cách tùy tiện.
3. **Định nghĩa executor:** Tạo/reuse `ThreadPoolTaskExecutor` bean có tên ổn định, sizing và rejection phù hợp với tải.
4. **Đổi nơi gọi:** Caller inject `InvoiceSender` do Spring quản lý; không tạo bằng `new`, không giữ lời gọi tự chạy trong config class.
5. **Kiểm tra transaction/context:** Transaction/MDC/security context của thread gọi không tự nhiên được chia sẻ sang worker. Truyền dữ liệu cần thiết, cấu hình context propagation và transaction tại ranh giới thích hợp.
6. **Test đúng hành vi:** Kiểm tra thread thực thi bằng thread prefix/ID của worker, quan sát kết quả Future và kiểm tra lỗi khi pool từ chối.
7. **Giữ logic sau startup rõ ràng:** Nếu công việc phải chạy khi khởi động, một initializer/event listener khác gọi service qua proxy sau thời điểm phù hợp.

### Phác thảo trước và sau

**Trước — cách không được hỗ trợ:**

~~~java
@Configuration
@EnableAsync
public class InvoiceConfig {
    @Async
    public CompletableFuture<String> sendInvoice() {
        return CompletableFuture.completedFuture("sent");
    }
}
~~~

**Sau — cách được hỗ trợ:**

~~~java
@Configuration
@EnableAsync
public class InvoiceConfig {
    // @Bean executor và các dependency cấu hình khác.
}

// Trong một file riêng, class do Spring quản lý:
@Service
public class InvoiceSender {
    @Async("invoiceExecutor")
    public CompletableFuture<String> sendInvoice() {
        return CompletableFuture.completedFuture("sent");
    }
}
~~~

Hai đoạn trên là **phác thảo kiến trúc**: cần import annotation, bean executor có tên `invoiceExecutor` và caller đi qua proxy. Ví dụ project Maven ở phần 4 mới là chương trình hoàn chỉnh có thể chạy nguyên trạng.

### Trường hợp service được tạo từ `@Bean`

Bạn **không bị bắt buộc** dùng `@Service` cho mọi service. Ví dụ:

~~~java
@Configuration
@EnableAsync
class InvoiceBeans {
    @Bean
    InvoiceSender invoiceSender() {
        return new InvoiceSender();
    }

    @Bean("invoiceExecutor")
    ThreadPoolTaskExecutor invoiceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        return executor;
    }
}
~~~

Đây là **fragment cấu hình**, cần import và lớp `InvoiceSender` với method `@Async` nằm trong **lớp riêng**. `InvoiceSender` do Spring quản lý vì được trả từ `@Bean`; sau khi container xử lý bean, async proxy có thể áp dụng lên method của đối tượng đó. **Factory method `invoiceSender()` không cần `@Async`.**

Tách lớp theo trách nhiệm là điều kiện quan trọng; cách đăng ký bean (`@Service` hay `@Bean`) là lựa chọn cấu hình.

## 8. Những hiểu lầm thường gặp

1. **“`@EnableAsync` trên `@Configuration` là sai vì không được dùng async trong cấu hình.”** Sai. Spring khuyến nghị bật `@EnableAsync` trong `@Configuration`; giới hạn chỉ dành cho **`@Async` trên methods khai báo ở đó**.
2. **“`@Async` trên method `@Bean` làm bean được tạo bất đồng bộ.”** Sai cách dùng. `@Bean` phục vụ vòng đời tạo bean; xử lý nghiệp vụ async ở method của service bean.
3. **“Có thể đổi `proxyBeanMethods=false` để `@Async` trong config hoạt động.”** Không. Tùy chọn này thay đổi interception của `@Bean`, không biến config method thành async method được hỗ trợ.
4. **“Bật `proxyTargetClass=true` là đã xử lý toàn bộ vấn đề.”** Không. Nó chỉ thay loại proxy; không thay hợp đồng của `@Async` với `@Configuration`.
5. **“Nếu service được tạo bởi `@Bean` thì method `@Async` của service cũng bị cấm.”** Sai. Giới hạn dựa trên **nơi method được khai báo**; bean trả về có thể được Spring xử lý như những bean khác.
6. **“Method `@Async` trong `@Service` luôn chạy nền, kể cả khi gọi `this.method()`.”** Sai trong proxy mode mặc định: self-invocation không đi qua proxy.
7. **“`@Async` hoạt động tự động trên object tạo bằng `new`.”** Không. Bean cần đi qua cơ chế quản lý/proxy hoặc weaving được cấu hình.
8. **“Bật `@EnableAsync` là mọi tác vụ đều được xử lý tốt bất kể executor.”** Không. Hàng đợi, số worker, timeout, rejection, dữ liệu và lỗi vẫn phải được thiết kế.
9. **“Future trả về có nghĩa method chạy đồng bộ.”** Không. Proxy có thể trả Future đại diện cho task; method trong worker có thể tạo một completed future để chứa kết quả thực.
10. **“Lỗi trong task và lỗi từ chối lúc gửi là cùng một đường.”** Không. Rejection có thể ném trước khi trả Future, còn lỗi trong worker đi vào Future hoặc handler theo loại return.

## 9. Câu hỏi tự kiểm tra

**Câu 1.** Có thể viết `@Configuration @EnableAsync` trong cùng class không?  
**Trả lời:** Có. `@EnableAsync` được dùng trên class cấu hình để bật asynchronous annotation processing.

**Câu 2.** Có thể đặt `@Async` trên method `generate()` được khai báo trực tiếp trong `@Configuration` rồi bảo đảm nó chạy background không?  
**Trả lời:** Không. Spring Javadoc nói rõ method trong `@Configuration` không được `@Async` hỗ trợ.

**Câu 3.** Nếu `@Bean` trong config trả về `ReportService`, còn `ReportService.generate()` có `@Async`, giới hạn có áp dụng không?  
**Trả lời:** Không theo cách đó; `generate()` được khai báo ở `ReportService`. Bean phải được Spring quản lý và lời gọi đi qua async proxy để interception xảy ra.

**Câu 4.** Một `@Service` gọi `this.calculateAsync()` trong chính class. Vì sao có thể chạy đồng bộ?  
**Trả lời:** Với `AdviceMode.PROXY`, lời gọi nội bộ không đi qua proxy. Gọi method qua bean proxy từ lớp khác.

**Câu 5.** Tại sao Spring có CGLIB proxy cho `@Configuration` mà method trong đó vẫn không nên gắn `@Async`?  
**Trả lời:** CGLIB enhancement của config xử lý `@Bean`/scope. Nó không thay đổi quy định hỗ trợ của annotation `@Async` trên method khai báo trong config.

**Câu 6.** Muốn làm warm-up bất đồng bộ sau khi ứng dụng sẵn sàng, cần tổ chức ra sao?  
**Trả lời:** Dùng một bean initializer/listener phù hợp, gọi method `@Async` trên bean service khác qua Spring proxy; không trực tiếp dùng `@Async` trên `@PostConstruct` hoặc method trong `@Configuration`.

## 10. Tài liệu tham khảo chính thức

- [Spring Framework 6.2.7 — Async Javadoc](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/annotation/Async.html): quy định rõ `@Async` không hỗ trợ phương thức khai báo trong lớp `@Configuration`; kiểu trả về `void`/`Future`/`CompletableFuture`.
- [Spring Framework 5.3.36 — Async Javadoc](https://docs.spring.io/spring-framework/docs/5.3.36/javadoc-api/org/springframework/scheduling/annotation/Async.html): xác nhận giới hạn trên cũng có ở nhánh 5.3.
- [Spring Framework 6.1.11 — EnableAsync Javadoc](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html): cách đặt `@EnableAsync` trên `@Configuration`, chế độ proxy và self-invocation.
- [Spring Framework 6.1.3 — Configuration Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/context/annotation/Configuration.html): `@Bean`, config class enhancement, `proxyBeanMethods` và bootstrapping.
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@EnableAsync`, `@Async`, chọn executor, kiểu trả về và xử lý exception.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-enablement-006">Chọn bộ thực thi mặc định bằng AsyncConfigurer</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn bộ thực thi mặc định bằng `AsyncConfigurer`

**Module:** Spring Framework - concurrency  
**Nhóm kiến thức:** Bật `@Async`  
**Trọng tâm:** Chủ động chọn executor dùng cho các phương thức `@Async` không chỉ rõ tên pool, phân biệt cấu hình mặc định với `@Async("beanName")`, quản lý vòng đời executor và xử lý ngoại lệ đúng cách.

## 1. Bài toán: `@Async` chạy ở pool nào?

Trong ứng dụng Spring, một phương thức được đánh dấu `@Async` có thể được Spring giao cho một executor để chạy ở thời điểm khác. Nhưng chỉ thêm annotation chưa trả lời được những câu hỏi vận hành rất quan trọng:

- Có bao nhiêu thread được phép chạy?
- Công việc đi vào queue nào, queue có giới hạn không?
- Tác vụ bị từ chối khi quá tải như thế nào?
- Một service chạy I/O chậm có chiếm hết worker của những service khác không?
- Nếu có nhiều executor bean, phương thức **không ghi tên executor** sẽ chọn cái nào?
- Lỗi trong phương thức trả `void` được xử lý ở đâu?

`AsyncConfigurer` là điểm mở rộng của Spring để cung cấp **executor mặc định** cho hạ tầng `@Async` và, nếu cần, **handler cho ngoại lệ của phương thức `@Async void`**.

```text
Lời gọi từ bean A -> Spring proxy của bean B
                         |
                         | @Async có tên executor?
                         |
              +----------+-----------+
              |                      |
         @Async("ioExecutor")     @Async
              |                      |
              v                      v
       Bean "ioExecutor"     AsyncConfigurer.getAsyncExecutor()
              |                      |
              +----------+-----------+
                         |
                   Executor đã chọn
                         |
                 Worker / queue / rejection
                         |
                      Hoàn tất
```

Cần phân biệt **chọn executor** với **bật cơ chế @Async**:

- `@EnableAsync` bật cơ chế phát hiện/intercept `@Async` trong **application context** tương ứng.
- `AsyncConfigurer` cung cấp lựa chọn mặc định khi lời gọi `@Async` **không** chỉ rõ một executor khác.
- `@Async("tenBean")` định tuyến riêng lời gọi đó đến bean executor có tên/qualifier tương ứng.
- Executor thực tế quyết định thread, queue, giới hạn nhận việc và cách từ chối. `AsyncConfigurer` **không** tự tạo một thread pool chỉ vì interface được implement.

## 2. Giao diện `AsyncConfigurer` cung cấp gì?

Trong Spring Framework 6.2, interface ở package `org.springframework.scheduling.annotation` có hai phương thức **default**:

```java
package org.springframework.scheduling.annotation;

// Phác thảo chữ ký, không phải mã nguồn đầy đủ của framework.
public interface AsyncConfigurer {
    default java.util.concurrent.Executor getAsyncExecutor() {
        return null;
    }

    default org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
            getAsyncUncaughtExceptionHandler() {
        return null;
    }
}
```

Đây là **phác thảo để hiểu API**, không phải bản sao chính xác của source code Spring; hãy dùng interface có sẵn từ thư viện. Hai điểm mở rộng:

1. **`getAsyncExecutor()`** trả về `java.util.concurrent.Executor` mà Spring dùng làm mặc định cho `@Async` không chỉ định executor. Có thể trả `ThreadPoolTaskExecutor`, một `ExecutorService` của JDK hoặc implementation hợp lệ khác. Phương thức trả `null` thì Spring áp dụng cơ chế tìm executor mặc định bình thường.
2. **`getAsyncUncaughtExceptionHandler()`** trả về `AsyncUncaughtExceptionHandler` dùng khi phương thức `@Async` có kiểu trả về `void` ném ngoại lệ mà phía gọi không có `Future` để quan sát. Có thể trả `null` để dùng handler mặc định của Spring.

`AsyncConfigurer` **không** thay thế `@EnableAsync`, **không** điều khiển `@Scheduled`, không tự tạo durable queue và không đảm bảo context/transaction của thread gọi được truyền sang worker.

Vì Spring có cơ chế proxy để thực thi `@Async`, phương thức phải được gọi **qua Spring-managed proxy** trong chế độ mặc định `AdviceMode.PROXY`. Gọi nội bộ `this.doAsync()` không đi qua proxy nên annotation có thể bị bỏ qua.

## 3. AsyncConfigurer thay đổi điều gì so với lookup mặc định?

Nếu `AsyncConfigurer.getAsyncExecutor()` trả executor cụ thể, hạ tầng dùng nó cho lời gọi `@Async` **không chỉ định tên**. Nếu không có cấu hình mặc định phù hợp, Spring Framework tìm một bean `TaskExecutor` có thể chọn theo kiểu, sau đó xem bean `Executor` tên đúng `taskExecutor`, và cuối cùng có fallback `SimpleAsyncTaskExecutor`. Spring Boot có thể auto-configure executor theo các điều kiện riêng; phải xem context thực tế.

| Trường hợp | Executor được dùng |
| --- | --- |
| `@Async` không tên và AsyncConfigurer trả pool X | Pool X |
| `@Async("ioExecutor")` | Bean/qualifier `ioExecutor`, dù mặc định là X |
| AsyncConfigurer không trả executor | Lookup type/name rồi fallback của Framework khi cần |

Đây là ranh giới của **bài chọn mặc định bằng AsyncConfigurer**: không coi tên bean bất kỳ hay auto-configuration là bằng chứng pool nào được dùng. Khi có nhiều ứng viên, kiểm thử thực tế với thread-prefix/bean wiring; nội dung tra cứu theo kiểu/tên và các trường hợp mơ hồ được giải thích riêng trong bài về thuật toán tìm executor.

## 4. Mẫu cấu hình tối thiểu và điểm dễ sai về lifecycle

Mẫu theo tài liệu Spring:

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

Đây là **đoạn class minh họa**; cần các import tương ứng:

```java
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
```

Điểm cần nhớ: `ThreadPoolTaskExecutor` được tạo trực tiếp trong method trên **không tự động là một Spring bean được quản lý đầy đủ** chỉ vì method nằm trong `@Configuration`. Việc gọi `initialize()` là cần thiết trong ví dụ tạo thủ công; bạn cũng phải tổ chức shutdown/lifecycle khi ứng dụng đóng.

**Cách ưu tiên với Spring-managed lifecycle:** thêm `@Bean` cho phương thức `getAsyncExecutor()` hoặc tạo một bean executor riêng rồi trả lại bean đó. Khi `ThreadPoolTaskExecutor` là bean do container quản lý, Spring tự gọi lifecycle khởi tạo và hủy; **không cần gọi `initialize()` thủ công**.

Đặc biệt, `AsyncConfigurer` có thể bị khởi tạo **sớm** trong quá trình bootstrap context. Nếu cấu hình phải dùng dependency Spring khác, hãy cân nhắc `ObjectProvider<T>` hoặc injection lazy và tránh khởi tạo sớm cả một đồ thị bean có proxy/post-processing. Đừng inject một service nghiệp vụ phức tạp vào configurer chỉ để tính số thread.

## 5. Ví dụ Spring Boot chạy được: mặc định, chỉ định pool và lỗi `void`

Ví dụ đầy đủ dưới đây dùng **Java 17+**, **Spring Boot 3.5.7** (Spring Framework 6.2.x) và Maven. Nó tạo:

- Một executor mặc định có tên `configuredAsyncExecutor`, thread name bắt đầu `default-`.
- Một executor khác có tên `ioExecutor`, thread name bắt đầu `io-`.
- Một service dùng `@Async` **không chỉ định tên**, và một method dùng `@Async("ioExecutor")`.
- Một method trả `CompletableFuture` bị lỗi, được quan sát từ future.
- Một method `@Async void` bị lỗi, được đưa vào `AsyncUncaughtExceptionHandler`.

Hai pool đều có queue hữu hạn. Chương trình main **đợi Future có chủ đích để kiểm chứng**, không lấy mẫu này làm API HTTP trả lời ngay.

### 5.1. Cấu trúc dự án

```text
async-configurer-demo/
├── pom.xml
└── src/main/java/com/example/asyncconfig/
    ├── AsyncConfigurerDemoApplication.java
    ├── AsyncConfiguration.java
    └── AsyncJobService.java
```

### 5.2. `pom.xml`

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
    <artifactId>async-configurer-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
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
```

`spring-boot-starter-aop` cung cấp phần hỗ trợ proxy/AOP và các thành phần nền Spring cần thiết; không cần một web server để trình diễn `@Async`.

### 5.3. `src/main/java/com/example/asyncconfig/AsyncConfiguration.java`

```java
package com.example.asyncconfig;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfiguration implements AsyncConfigurer {

    // Chỉ để chương trình demo kiểm chứng handler void một lần.
    public static final CompletableFuture<String> VOID_FAILURE =
            new CompletableFuture<>();

    @Override
    @Bean("configuredAsyncExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("default-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Bean("ioExecutor")
    public ThreadPoolTaskExecutor ioExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("io-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (exception, method, parameters) ->
                VOID_FAILURE.complete(
                        method.getName() + ":"
                        + exception.getClass().getSimpleName());
    }
}
```

**Giải thích:**

- `@EnableAsync` kích hoạt proxy advice cho `@Async`.
- `getAsyncExecutor()` trả `Executor` mặc định. `@Bean("configuredAsyncExecutor")` biến executor thực tế thành bean do Spring quản lý, bao gồm `initialize` và `destroy` thích hợp.
- `ioExecutor()` tạo một bean thứ hai. Nó **không** tự thay thế lựa chọn mặc định, vì configurer đã trả pool `default-`.
- `VOID_FAILURE` là cầu nối quan sát lỗi dùng **một lần cho chương trình demo**, không phải hệ thống ghi nhận lỗi production. Ứng dụng thật dùng logger/metrics/tracing có context đầy đủ.
- `AbortPolicy` khiến lời giao task bị từ chối khi pool và queue không thể nhận nữa. `ThreadPoolTaskExecutor` thể hiện rejection theo contract của Spring (`TaskRejectedException`).
- Cấu hình core/max/queue chỉ để minh họa, phải điều chỉnh theo CPU, I/O, RAM, downstream và SLO thật.

### 5.4. `src/main/java/com/example/asyncconfig/AsyncJobService.java`

```java
package com.example.asyncconfig;

import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncJobService {

    @Async
    public CompletableFuture<String> defaultJob() {
        String thread = Thread.currentThread().getName();
        String pool = thread.startsWith("default-")
                ? "default" : "unexpected";
        return CompletableFuture.completedFuture(pool);
    }

    @Async("ioExecutor")
    public CompletableFuture<String> ioJob() {
        String thread = Thread.currentThread().getName();
        String pool = thread.startsWith("io-")
                ? "io" : "unexpected";
        return CompletableFuture.completedFuture(pool);
    }

    @Async
    public CompletableFuture<String> failedFutureJob() {
        throw new IllegalStateException("demo failure");
    }

    @Async
    public void failedVoidJob() {
        throw new IllegalArgumentException("demo void failure");
    }
}
```

Phương thức `defaultJob()` không có qualifier nên dùng pool do `AsyncConfigurer` trả về. `ioJob()` ghi rõ `"ioExecutor"` nên được giao cho pool riêng. Cả hai phương thức được gọi từ **bean khác** qua Spring proxy trong chương trình main.

Một phương thức có chữ ký `CompletableFuture<T>` trong implementation có thể trả `CompletableFuture.completedFuture(value)` để cung cấp kết quả do worker tính xong; phía gọi nhận future mà proxy cung cấp. Exception từ method `failedFutureJob()` làm future phía gọi hoàn thành lỗi; handler `AsyncUncaughtExceptionHandler` **không** phải nơi xử lý lỗi future đó.

### 5.5. `src/main/java/com/example/asyncconfig/AsyncConfigurerDemoApplication.java`

```java
package com.example.asyncconfig;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class AsyncConfigurerDemoApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication app =
                new SpringApplication(AsyncConfigurerDemoApplication.class);

        // Chạy ở chế độ CLI, không mở HTTP server.
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.setDefaultProperties(Map.of("logging.level.root", "OFF"));

        try (ConfigurableApplicationContext context = app.run(args)) {
            AsyncJobService jobs = context.getBean(AsyncJobService.class);

            System.out.println("default=" +
                    jobs.defaultJob().get(5, TimeUnit.SECONDS));

            System.out.println("qualified=" +
                    jobs.ioJob().get(5, TimeUnit.SECONDS));

            try {
                jobs.failedFutureJob().get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                System.out.println("futureError=" +
                        ex.getCause().getClass().getSimpleName());
            }

            jobs.failedVoidJob();
            System.out.println("voidError=" +
                    AsyncConfiguration.VOID_FAILURE.get(
                            5, TimeUnit.SECONDS));
        }
    }
}
```

`try-with-resources` đóng Spring context sau khi demo xong; các executor dưới dạng bean được Spring quản lý theo lifecycle. `Future.get(timeout, unit)` ở đây **cố tình chờ** để output xác định và phát hiện lỗi, không đại diện cho lựa chọn hợp lý của một HTTP controller có deadline ngắn.

### 5.6. Chạy và quan sát

Điều kiện: đã cài JDK 17+ và Maven. Tại thư mục chứa `pom.xml`:

```bash
mvn -q spring-boot:run
```

**Các dòng `System.out` dự kiến khi chương trình hoàn tất bình thường:**

```text
default=default
qualified=io
futureError=IllegalStateException
voidError=failedVoidJob:IllegalArgumentException
```

Ứng dụng đã tắt Spring banner và logging của root logger trong ví dụ để tập trung vào kết quả. Nếu công cụ chạy/build hoặc môi trường in thêm dòng riêng, chỉ bốn dòng trên là output do main chủ động in. Thứ tự hoàn tất thật của worker có thể thay đổi, nhưng `main` đọc từng kết quả bằng future nên thứ tự bốn dòng `System.out` này là cố định.

**Tại sao đây là bằng chứng chọn đúng executor?**

1. `defaultJob()` chạy trên một thread có prefix `default-`; không phải bean `ioExecutor` dù trong context có hai pool.
2. `ioJob()` chạy trên thread prefix `io-`; qualifier của method thắng lựa chọn mặc định.
3. `failedFutureJob()` trả exception vào future; `Future.get()` ném `ExecutionException` và `getCause()` là `IllegalStateException`.
4. `failedVoidJob()` không có future để quan sát; handler của `AsyncConfigurer` nhận exception và hoàn thành `VOID_FAILURE` để main kiểm tra.

Không nên xây dựng logic production dựa trên **tên thread**. Prefix chỉ là cách trình diễn và debug; trong hệ thống thật, đo executor được chọn qua bean, task metrics và tracing.

## 6. Khi nào `@Async("...")` thay thế executor mặc định?

Mặc định từ `AsyncConfigurer` áp dụng cho **những lời gọi @Async không có executor qualifier**. Với qualifier, Spring định tuyến tới bean được chỉ định.

```java
@Service
public class ExportService {

    @Async
    public CompletableFuture<String> smallExport() {
        return CompletableFuture.completedFuture("small");
    }

    @Async("largeExportExecutor")
    public CompletableFuture<String> largeExport() {
        return CompletableFuture.completedFuture("large");
    }
}
```

Đây là **đoạn class minh họa** trong ứng dụng Spring: cần import `@Service`, `@Async`, `CompletableFuture` và có bean `"largeExportExecutor"` tồn tại. Nếu bean không tồn tại hoặc không tương thích với executor cần dùng, việc gọi có thể thất bại; Spring không nên âm thầm chọn một pool bất kỳ rồi che lỗi cấu hình.

Có thể đặt `@Async("defaultPool")` ở cấp class và chỉ định executor khác ở một method theo các quy tắc annotation; lựa chọn cụ thể của method thường ưu tiên hơn class-level. Hãy dùng tên bean/qualifier rõ ràng khi một service có nhiều loại tác vụ.

**Lưu ý:** `getAsyncExecutor()` không bắt mọi `CompletableFuture.supplyAsync(...)` hay `ExecutorService.submit(...)` tự viết trong code. Những lời gọi trực tiếp của JDK dùng executor được truyền cho chúng hoặc mặc định riêng của JDK, không tự tham chiếu Spring `AsyncConfigurer`.

## 7. Bắt đúng ngoại lệ: `void` và `Future` khác nhau

Spring cần một điểm xử lý lỗi khác nhau tùy chữ ký method:

| Return type của phương thức `@Async` | Bên gọi thấy gì? | Hướng xử lý lỗi |
| --- | --- | --- |
| `void` | Không có kết quả/future để chờ | `AsyncUncaughtExceptionHandler`, log/metric/alert |
| `Future<T>` | `Future` đại diện tác vụ | `get()` ném `ExecutionException` nếu task lỗi |
| `CompletableFuture<T>` | Có thể `get`, `join`, `whenComplete`, `handle` | Dùng completion stage và xử lý exceptional completion |
| Giá trị tùy ý như `String` | Không phải return type hỗ trợ cho `@Async` | Chuyển sang `void`/`Future`/`CompletableFuture` theo hợp đồng |

Ví dụ handler cho phương thức `void` trong ứng dụng thật:

```java
@Override
public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (error, method, arguments) -> {
        log.error("Async void method {} failed",
                method.getName(), error);
        failedTaskCounter.increment();
    };
}
```

Đoạn trên **không phải chương trình độc lập**; `log` và `failedTaskCounter` là các dependency mà ứng dụng phải cung cấp. Các tham số method có thể chứa dữ liệu nhạy cảm; không tự log toàn bộ `arguments` khi chưa có chính sách bảo vệ.

Với `CompletableFuture`:

```java
CompletableFuture<Report> future = reportService.createReport(id);

future.whenComplete((report, error) -> {
    if (error != null) {
        recordFailure(id, error);
    } else {
        recordSuccess(id, report);
    }
});
```

Đây là đoạn minh họa có dependency `reportService`, `id`, kiểu `Report`, hai hàm ghi kết quả. `whenComplete` dùng để quan sát; bản thân callback ném lỗi cũng có cơ chế exceptional completion cần giám sát. Tránh bỏ mặc future của công việc quan trọng.

**Không kỳ vọng một exception từ worker được ném trực tiếp trở lại caller synchronous.** Exception xảy ra sau khi lời gọi `@Async` đã được giao cho executor; riêng lỗi **rejection khi giao việc** có thể xảy ra ngay tại thời điểm caller gọi method.

## 8. Vòng đời và khởi tạo sớm của `AsyncConfigurer`

### Hai cách cấp executor và trách nhiệm khác nhau

**Tạo thủ công trong `getAsyncExecutor()` (không `@Bean`):**

```java
@Override
public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(10);
    executor.initialize();
    return executor;
}
```

Phải xác định ai sở hữu và đóng executor. Vì đối tượng không được quản lý đầy đủ bởi container, không tự giả định Spring gọi destroy. Nếu ứng dụng tạo nhiều object không kiểm soát, có thể rò thread.

**Trả Spring bean:**

```java
@Override
@Bean("configuredAsyncExecutor")
public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(10);
    return executor;
}
```

Khi method thực sự được Spring đăng ký thành `@Bean`, bean lifecycle quản lý initialize/destroy và không cần gọi initialize thủ công. Đây cũng là cách mà ví dụ chạy hoàn chỉnh ở phần trên sử dụng.

### Nếu executor là bean được định nghĩa nơi khác?

Một số dự án cần đưa executor sang module cấu hình khác, hoặc sử dụng bean có decorator/monitoring phức tạp. `AsyncConfigurer` được khởi tạo sớm; tránh hard dependency gây vòng lặp bootstrap. Một cấu hình minh họa:

```java
@Configuration
@EnableAsync
class AsyncSetup implements AsyncConfigurer {
    private final ObjectProvider<Executor> executorProvider;

    AsyncSetup(
            @Qualifier("sharedAsyncExecutor")
            ObjectProvider<Executor> executorProvider) {
        this.executorProvider = executorProvider;
    }

    @Override
    public Executor getAsyncExecutor() {
        return executorProvider.getObject();
    }
}
```

Đây là **đoạn minh họa phụ thuộc vào Spring context**, cần import `java.util.concurrent.Executor`, `@Configuration`, `@EnableAsync`, `AsyncConfigurer`, `ObjectProvider`, `@Qualifier` và phải tồn tại bean `"sharedAsyncExecutor"`. `ObjectProvider` trì hoãn việc lấy bean đến khi cần, giúp tránh khởi tạo sớm dependency có proxy. Đừng tạo thêm nhiều `AsyncConfigurer` rời rạc để mỗi configurer chọn một pool khác nhau; lựa chọn mặc định là cấu hình ở cấp context, còn từng method có thể dùng qualifier.

### Chú ý khi shutdown

- `ThreadPoolTaskExecutor` là bean Spring có lifecycle; ứng dụng phải đóng context đúng cách.
- Các task đang chạy và nằm trong queue khi shutdown cần chính sách rõ: tiếp tục/chờ/cancel và deadline dừng; điều này phụ thuộc Spring version, cấu hình executor và container lifecycle.
- Không đóng pool ở cuối mỗi request nếu pool là shared bean.
- Bản thân việc có `AsyncConfigurer` không lưu task sang ổ đĩa/DB. Khi JVM chết, queue trong RAM có thể mất, kể cả bean lifecycle được quản lý tốt.
- Một Spring child context khác có thể không tự hưởng `@EnableAsync` của parent theo cách bạn hình dung; tài liệu nêu `@EnableAsync` áp dụng trong **local application context**.

## 9. Chọn pool kích thước bao nhiêu và từ chối thế nào?

`getAsyncExecutor()` trả `Executor`; cấu hình năng lực thực tế nằm ở executor đó. Với `ThreadPoolTaskExecutor`, ba tham số cốt lõi là:

| Tham số | Ý nghĩa | Sai lầm thường gặp |
| --- | --- | --- |
| `corePoolSize` | Số worker nền thường được duy trì theo cơ chế của pool | Đặt quá thấp rồi hy vọng `maxPoolSize` luôn được dùng |
| `maxPoolSize` | Trần worker có thể tạo khi tải và queue cho phép | Nghĩ rằng `max` đồng nghĩa số task sẽ chạy ngay |
| `queueCapacity` | Số task chờ, không tính task đang chạy | Đặt quá lớn khiến yêu cầu hết deadline nhưng vẫn nằm trong queue |

Với mô hình `ThreadPoolExecutor` phía dưới, khi đủ core workers, task thường **được xếp vào queue trước**; chỉ khi queue không nhận thêm, pool mới cố mở rộng đến `maxPoolSize`. Nếu pool và queue đều đầy, task bị từ chối.

Ví dụ khởi đầu cho hai loại công việc:

- **CPU-bound:** worker xấp xỉ ngân sách CPU khả dụng sau khi tính tải của JVM khác; pool cố định hoặc gần cố định; queue hữu hạn.
- **Blocking I/O:** có thể cần nhiều worker hơn số CPU vì nhiều thread đợi mạng/DB, nhưng còn bị giới hạn bởi connection pool, rate limit, timeout và RAM.
- **Tác vụ nghiệp vụ quan trọng:** cân nhắc job bền vững qua DB/queue/broker thay vì chỉ đẩy vào executor trong JVM.

Trong một ứng dụng có hai pool, hãy đặt **ngân sách tổng** theo tài nguyên. Việc `@Async` mặc định đi vào pool A và `@Async("ioExecutor")` đi vào pool B không làm CPU/DB connections tự tăng.

### Rejection khi quá tải

`ThreadPoolTaskExecutor` tuân theo ngoại lệ từ chối dạng `TaskRejectedException` của Spring. Với `AbortPolicy`, lời giao việc có thể thất bại ngay khi pool không còn sức chứa. Nếu một controller muốn báo “đã nhận xử lý”, cần phân biệt trường hợp giao việc thành công với trường hợp bị từ chối:

```java
try {
    reportService.submitAsync(id);
    // Chỉ phản hồi accepted nếu hợp đồng thực sự cho phép
    // nhận tác vụ vào bộ nhớ; durability phải được thiết kế riêng.
} catch (TaskRejectedException ex) {
    // Ánh xạ sang lỗi overload có chủ đích, chẳng hạn HTTP 503.
}
```

Đây là **đoạn minh họa**, có `reportService`, `id`, `TaskRejectedException` và response HTTP do ứng dụng định nghĩa. Nếu yêu cầu **không mất job sau restart**, dùng DB/broker bền vững trước khi trả `202`, thay vì coi `executor.execute(...)` là xác nhận durable.

`CallerRunsPolicy` của JDK có thể chạy task trên **thread gửi** khi pool từ chối, gây chặn HTTP thread. Vì vậy nếu cần fail fast, chọn rejection policy có chủ đích. `@Async` không đảm bảo chạy trên thread riêng trong mọi trường hợp nếu bạn chọn delegate hoặc policy có đặc tính chạy trực tiếp.

## 10. Các lỗi phổ biến khi chọn executor mặc định

| Lỗi | Hiện tượng | Cách kiểm tra/sửa |
| --- | --- | --- |
| Quên `@EnableAsync` | Gọi `@Async` vẫn xử lý trong thread caller | Bật trong đúng application context |
| Gọi `this.doAsync()` bên trong cùng bean | Bỏ qua proxy, không chuyển sang executor | Gọi qua Spring bean khác, thiết kế lại ranh giới |
| Có hai executor và không biết cái nào được chọn | Method không qualifier chạy vào pool khác kỳ vọng | Dùng `AsyncConfigurer` hoặc `@Async("beanName")`, kiểm tra bean thực tế |
| Tưởng `getAsyncExecutor()` cấu hình tất cả API bất đồng bộ | `CompletableFuture.supplyAsync` hoặc `@Scheduled` vẫn dùng tài nguyên khác | Truyền executor tường minh cho JDK API; cấu hình scheduler riêng |
| Dùng executor không có `@Bean`, cũng không gọi `initialize()` | Pool Spring chưa được khởi tạo đúng | Initialize và quản lý lifecycle thủ công, hoặc chuyển thành Spring bean |
| Khởi tạo `AsyncConfigurer` với dependency phức tạp/autowired sớm | Bean/proxy chưa sẵn sàng, lỗi bootstrap | Dùng lazy `ObjectProvider`, tách cấu hình rõ ràng |
| Tưởng `@Bean` return `Executor` làm mất lifecycle của `ThreadPoolTaskExecutor` | Lo lắng phải gọi initialize thêm | Bean thực tế vẫn là `ThreadPoolTaskExecutor`, Spring quản lý lifecycle |
| `@Async void` ném exception mà không có handler theo dõi | Caller không nhận được lỗi | Cài `AsyncUncaughtExceptionHandler`, log/metrics |
| `CompletableFuture` thất bại nhưng bị bỏ qua | Lỗi nền không ai quan sát | `whenComplete`/`handle`/`get`, trạng thái job |
| Tạo pool mới bên trong từng method/request | Rò thread, không có giới hạn toàn ứng dụng | Bean executor dùng chung, shutdown theo context |
| Queue không giới hạn, `maxPoolSize` rất lớn | Backlog tăng, max threads ít được dùng, OOM | Bounded queue, sizing và rejection policy |
| Chỉ test tên thread mà không có kiểm thử hành vi | Không phát hiện lỗi overload/exception/timeout | Test loại executor, số task đang chạy, rejection và shutdown |
| Copy transaction/request context qua ThreadLocal tùy tiện | Đọc dữ liệu chưa commit, rò security context | Worker tự mở transaction; truyền ID/dữ liệu rõ ràng và cleanup đúng |
| Đưa CPU-heavy task vào pool I/O, hoặc ngược lại | Starvation, p99 không ổn định | Phân pool theo tài nguyên, đo thực tế |

## 11. Kiểm thử cấu hình trong dự án thật

**Kiểm thử chức năng tối thiểu:**

1. Bật `@EnableAsync` trên một config class được component scan.
2. Implement `AsyncConfigurer` và trả executor được cấu hình có `threadNamePrefix` phân biệt.
3. Gọi một method public `@Async` **qua bean khác** và xác minh nó dùng pool được configurer trả.
4. Định nghĩa một bean executor thứ hai; dùng `@Async("otherExecutor")` và xác minh qualifier chọn đúng pool.
5. Bật nhánh lỗi `void` và kiểm tra `AsyncUncaughtExceptionHandler` nhận exception.
6. Bật nhánh lỗi `CompletableFuture` và kiểm tra exceptional completion.
7. Cho queue đầy bằng `CountDownLatch`, kiểm tra rejection/response thay vì dùng `Thread.sleep` để đoán race.
8. Đóng context để kiểm tra không còn thread rò, xử lý đúng các task còn chạy.

**Kiểm thử vận hành:** dưới tải thật, đo active threads, queue depth, queue wait, task execution time, reject count, p95/p99 latency, CPU throttling, JDBC/HTTP connections và startup/shutdown. Cấu hình đúng API chưa đồng nghĩa cấu hình năng lực đúng workload.

**Tình huống cần đo riêng:** khi đã bật `@Async`, controller có thể nhận được `CompletableFuture` nhưng vẫn chặn request nếu gọi `.get()`/`.join()` ngay. Nếu mục tiêu là giải phóng Servlet thread **mà giữ một response đang chờ kết quả**, dùng cơ chế async request của Spring MVC phù hợp. Nếu muốn trả `202 Accepted` và xử lý dài sau, cần API job/status/durability riêng.

## 12. Câu hỏi tự kiểm tra

**1.** Có hai `ThreadPoolTaskExecutor` bean `"cpuExecutor"` và `"ioExecutor"` nhưng muốn mọi method `@Async` không qualifier mặc định dùng `"cpuExecutor"`. Chọn gì?  
**Đáp:** Implement `AsyncConfigurer.getAsyncExecutor()` trả bean cpuExecutor (hoặc cấu hình ứng dụng có quy tắc mặc định rõ ràng). Những method cần pool I/O dùng `@Async("ioExecutor")`.

**2.** `getAsyncExecutor()` bắt buộc trả `ThreadPoolTaskExecutor` không?  
**Đáp:** Không, return type là `java.util.concurrent.Executor`; implementation có thể là executor JDK hoặc loại phù hợp khác, miễn đáp ứng contract/nhu cầu vận hành.

**3.** Vì sao thêm `@Bean` vào `getAsyncExecutor()` có ý nghĩa?  
**Đáp:** Executor trả về trở thành bean Spring; lifecycle initialization/destroy được container quản lý nếu là loại hỗ trợ lifecycle như `ThreadPoolTaskExecutor`.

**4.** Phương thức `@Async("ioExecutor")` có dùng executor trả từ `AsyncConfigurer` không?  
**Đáp:** Thường không. Giá trị chỉ định tên/qualifier trên `@Async` yêu cầu bean tương ứng và thay thế lựa chọn mặc định cho lời gọi đó.

**5.** `@Async void` ném `IllegalArgumentException` thì caller có bắt được bằng `try/catch` quanh lời gọi không?  
**Đáp:** Không theo cách thông thường khi task thực thi trên worker sau đó; handler `AsyncUncaughtExceptionHandler` nhận lỗi. Ngoại lệ ngay lúc **giao task**, như rejection, là tình huống khác.

**6.** `@Async CompletableFuture<String>` thất bại; handler dành cho `void` có bắt không?  
**Đáp:** Không. Bên gọi phải quan sát future hoàn thành lỗi qua `get()`, `whenComplete` hoặc cơ chế tương đương.

**7.** `this.generate()` có `@Async` nhưng vẫn chạy synchronous; sai ở đâu?  
**Đáp:** Self-invocation bỏ qua proxy trong `AdviceMode.PROXY` mặc định.

**8.** Vì sao có `maxPoolSize=32` mà chỉ thấy bốn worker chạy khi queue chưa đầy?  
**Đáp:** `ThreadPoolExecutor` thường xếp queue sau core trước khi tạo worker vượt core. Cần hiểu `corePoolSize`, `queueCapacity` và deadline.

**9.** Có nên cho `AsyncConfigurer` tự tạo `new ThreadPoolTaskExecutor()` ở mỗi lần xử lý request không?  
**Đáp:** Không. Executor dùng chung trong context với một cấu hình và lifecycle rõ ràng; request chỉ gửi task vào executor.

**10.** `getAsyncExecutor()` bảo đảm job được xử lý sau khi JVM restart?  
**Đáp:** Không. Đây là lựa chọn executor thực thi trong JVM. Durability cần persistence/queue/broker và recovery.

## 13. Ghi nhớ

`AsyncConfigurer` giúp **chọn minh bạch executor mặc định cho `@Async`** và cung cấp handler cho **ngoại lệ của phương thức `void`**. Cấu hình tác động đến lời gọi đi qua Spring async advice trong context tương ứng; `@Async("beanName")` có thể chỉ định pool riêng.

- **Bật:** `@EnableAsync`.
- **Chọn mặc định:** implement `AsyncConfigurer.getAsyncExecutor()`.
- **Chọn riêng:** `@Async("ioExecutor")`.
- **Xử lý lỗi:** `getAsyncUncaughtExceptionHandler()` cho `void`; `Future`/`CompletableFuture` được quan sát bởi caller hoặc job orchestration.
- **Quản lý vòng đời:** ưu tiên executor là Spring bean, queue có giới hạn và shutdown theo context.
- **Xác minh:** gọi qua Spring proxy, đo thread/queue/rejection, thử nghiệm dưới tải và ghi rõ hợp đồng khi job phải sống sau HTTP.

**Tài liệu chính thức:**

- [Spring Framework 6.2 — `@EnableAsync` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html): quy tắc tìm executor, ví dụ `AsyncConfigurer`, vòng đời, proxy.
- [Spring Framework 6.2 — `AsyncConfigurer` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/scheduling/annotation/AsyncConfigurer.html): hai method cấu hình và lưu ý init sớm.
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): abstraction, `@Async`, executor, exception handling.
- [Spring Framework 6.2 — `ThreadPoolTaskExecutor`](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): cấu hình và lifecycle pool.
- [Java SE 17 — `Executor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html): contract gốc của JDK.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-enablement-007">Cơ chế tìm TaskExecutor hoặc bean taskExecutor khi không cấu hình tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# Cơ chế tìm TaskExecutor hoặc bean taskExecutor khi không cấu hình tường minh

> **Module:** Spring Framework – concurrency  
> **Chủ đề cha:** Bật @Async  
> **Mục tiêu:** Giải thích chính xác Spring chọn `Executor` nào cho một phương thức `@Async` không ghi tên executor và ứng dụng không cung cấp `AsyncConfigurer.getAsyncExecutor()`; phân biệt tìm theo **kiểu bean**, tìm theo **tên bean**, executor dự phòng và cấu hình tự động của Spring Boot.

## 1. Câu trả lời trực tiếp

Trong Spring Framework, sau khi kích hoạt xử lý `@Async` (thường bằng `@EnableAsync`), nếu một phương thức được gọi **qua Spring proxy**, không ghi qualifier trong `@Async` và không có executor mặc định cấu hình tường minh, bộ xử lý async tìm executor theo thứ tự:

1. **Tìm một bean `TaskExecutor` có thể xác định được theo kiểu trong `ApplicationContext`.** Nếu có, dùng bean đó, dù tên bean không phải `taskExecutor`.
2. **Nếu bước 1 không chọn được, tìm một bean có tên chính xác `taskExecutor` và có kiểu `java.util.concurrent.Executor`.** Nếu tìm được, dùng bean này. Bean `ThreadPoolTaskExecutor` cũng hợp lệ vì nó triển khai cả hai interface.
3. **Nếu không giải quyết được cả hai, Spring sử dụng một `SimpleAsyncTaskExecutor` dự phòng cục bộ** để thực thi lời gọi `@Async`.

Điểm quyết định là **tìm theo kiểu trước, theo tên sau**. Không phải cứ có một bean bất kỳ tên `taskExecutor` thì nó luôn thắng mọi `TaskExecutor` khác; cũng không phải mọi `Executor` bất kỳ đều được tự động chọn.

Quy tắc trên được mô tả trong Javadoc chính thức của `@EnableAsync` và `AsyncExecutionAspectSupport`. Nó nói về **default executor của xử lý `@Async` trong Spring Framework**; các integration khác như Spring MVC async request hay WebFlux có thể dùng quy tắc chọn executor riêng.

## 2. Điều kiện để cơ chế này thực sự được áp dụng

Trước khi bàn về lựa chọn executor, phải có một lời gọi `@Async` được Spring chặn:

~~~java
@Configuration
@EnableAsync
class AsyncSupportConfiguration {
}
~~~

Đây là **đoạn cấu hình Spring tích hợp**, cần các import `@Configuration` và `@EnableAsync`; không phải chương trình Java chạy độc lập. `@EnableAsync` bật async method advice trong **ApplicationContext tương ứng**. Nó không đồng nghĩa với việc Spring tự thêm một bean `ThreadPoolTaskExecutor` có queue và kích thước pool do bạn mong muốn.

Trong chế độ `AdviceMode.PROXY` mặc định:

- Caller phải gọi phương thức thông qua **bean proxy của Spring**, thường từ một bean khác đã inject service.
- `this.sendAsync()` gọi nội bộ trong cùng instance bỏ qua proxy; annotation có thể không hoạt động, nên không có quá trình tìm executor ở lời gọi đó.
- Phương thức `private` hoặc `final` không phải điểm chặn phù hợp cho class-based proxy thông thường; tránh dựa vào cấu hình proxy không hỗ trợ phương thức đó.
- Không dùng `@Async` trực tiếp cho các phương thức khai báo bên trong `@Configuration` class. Đặt tác vụ vào service bean.
- Nếu ứng dụng có nhiều context, `@EnableAsync` áp dụng tại context được bật, không mặc nhiên kích hoạt mọi context con/cha.

Khi đã có lời gọi thực sự qua interceptor, Spring mới chọn executor để nộp công việc. Gọi trực tiếp `executor.submit(...)` là cơ chế khác: caller đã tự chọn executor, không tham gia thuật toán mặc định của `@Async`.

## 3. Sơ đồ tìm kiếm khi @Async không chỉ rõ executor

~~~text
Caller ---> Spring proxy ---> async interceptor
                                      |
                         @Async có tên/qualifier?
                           | CÓ              | KHÔNG
                           v                  v
                 Tìm bean Executor      Có AsyncConfigurer
                 theo tên/qualifier     cung cấp default?
                           |                  | CÓ
                           v                  v
                     Dùng bean đó       Dùng executor cấu hình
                                              |
                                              | KHÔNG
                                              v
                                   Tìm TaskExecutor theo kiểu
                                              |
                                  +-----------+----------+
                                  | có thể chọn           | không
                                  v                       v
                            Dùng TaskExecutor     Tìm bean "taskExecutor"
                                                          kiểu Executor
                                                      /            \
                                                    có              không
                                                    |                |
                                                    v                v
                                                Dùng bean      SimpleAsyncTaskExecutor
~~~

Sơ đồ mô tả **ưu tiên logic**, không phải pseudocode nội bộ đầy đủ. Cơ chế thực tế có thêm các nhánh xử lý bean không tồn tại, nhiều ứng viên, qualifier sai, cache và cấu hình `BeanFactory`. Một qualifier được chỉ rõ mà không giải quyết được thường dẫn đến **lỗi tra cứu bean**, không nên giả định nó âm thầm quay về default.

## 4. Bước 1: ưu tiên một TaskExecutor có thể chọn theo kiểu

`org.springframework.core.task.TaskExecutor` là abstraction Spring, mở rộng `java.util.concurrent.Executor`. Những implementation thông dụng gồm `ThreadPoolTaskExecutor`, `SimpleAsyncTaskExecutor` hoặc `ConcurrentTaskExecutor`.

Ví dụ một context chỉ có một bean kiểu `TaskExecutor`:

~~~java
@Bean("reportPool")
ThreadPoolTaskExecutor reportPool() {
    ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(4);
    pool.setMaxPoolSize(8);
    pool.setQueueCapacity(50);
    pool.setThreadNamePrefix("report-");
    return pool;
}
~~~

Đây là **một phương thức @Bean minh họa** đặt trong `@Configuration`; Spring quản lý việc khởi tạo và shutdown pool. Vì `reportPool` là ứng viên `TaskExecutor` duy nhất, `@Async` không tên sẽ chọn nó theo kiểu, **dù tên bean là `reportPool`**. Cấu hình này không cần đặt tên `taskExecutor` để được nhận diện.

Nếu có nhiều bean kiểu `TaskExecutor`, phép tìm theo kiểu có thể trở nên không xác định. Cơ chế `BeanFactory` có thể lựa chọn ứng viên ưu tiên (chẳng hạn theo `@Primary` khi áp dụng), nhưng nếu vẫn mơ hồ, Spring chuyển sang thử tên `taskExecutor`.

Không nên suy ra rằng `@Primary` trên một bean bất kỳ kiểu `Executor` sẽ được chọn giống một `TaskExecutor` duy nhất: **bước đầu tìm `TaskExecutor`**, rồi mới đến bean `Executor` theo tên.

## 5. Bước 2: bean tên taskExecutor làm đường dự phòng

Nếu tìm theo kiểu không chọn được, Spring tìm bean với tên **chính xác**:

~~~text
taskExecutor
~~~

Bean đó phải tương thích với `java.util.concurrent.Executor`. Tên này phân biệt với các tên như `tasksExecutor`, `myTaskExecutor`, `applicationTaskExecutor` hay `asyncExecutor`.

Ví dụ khi có nhiều `TaskExecutor` và muốn chỉ định default ổn định mà không thêm `AsyncConfigurer`:

~~~java
@Bean("ioPool")
ThreadPoolTaskExecutor ioPool() {
    return poolWithPrefix("io-");
}

@Bean("cpuPool")
ThreadPoolTaskExecutor cpuPool() {
    return poolWithPrefix("cpu-");
}

@Bean("taskExecutor")
ThreadPoolTaskExecutor defaultAsyncPool() {
    return poolWithPrefix("default-");
}
~~~

Đây là **đoạn minh họa tích hợp**: hàm `poolWithPrefix` thuộc ứng dụng và không được định nghĩa trong snippet; ví dụ hoàn chỉnh chạy được ở mục 8. Khi nhiều pool kiểu `TaskExecutor` cùng tồn tại mà không có ứng viên ưu tiên theo type, `@Async` không ghi tên có thể chọn bean `taskExecutor` trong bước tra cứu thứ hai.

Cũng cần chú ý: bean theo tên `taskExecutor` **có thể là JDK Executor hoặc ExecutorService**, không bắt buộc phải là Spring `TaskExecutor`. Interceptor có cơ chế thích ứng với executor gốc khi cần. Ngược lại, một bean có đúng tên nhưng không triển khai `Executor` là cấu hình không hợp lệ, có thể gây lỗi kiểu bean; đừng trông đợi fallback an toàn trong trường hợp cấu hình sai kiểu.

**Nếu có cả một `TaskExecutor` được chọn theo kiểu và một bean `Executor` tên `taskExecutor` khác**, bước tìm theo kiểu được ưu tiên. Đây là nguyên nhân phổ biến khiến chỉ đổi tên bean vẫn chưa thay đổi pool thực thi như mong muốn.

## 6. Bước 3: không tìm thấy thì SimpleAsyncTaskExecutor

Khi không có executor mặc định được cấu hình và cả hai bước tra cứu đều không chọn được, `@EnableAsync` dùng `SimpleAsyncTaskExecutor` dự phòng. Đây là executor thực sự chạy tác vụ bất đồng bộ, **không phải trường hợp Spring bỏ qua @Async**.

Đặc điểm cần nhớ:

- `SimpleAsyncTaskExecutor` tạo **thread mới cho mỗi task** trong cách dùng thông thường, **không tái sử dụng thread như thread pool**.
- Concurrency mặc định của loại này không có giới hạn hữu hạn do bạn thiết lập. Nếu nhiều request cùng kích hoạt hàng nghìn task chậm, số thread có thể tăng nhanh và gây áp lực bộ nhớ, scheduler và downstream.
- Nó có lựa chọn giới hạn concurrency và, trên JDK 21+, lựa chọn virtual threads; nhưng việc được dùng **ngầm định** không có nghĩa đã được tune phù hợp tải sản phẩm.
- Nó không phải `ThreadPoolTaskExecutor` mặc định với `corePoolSize=...`, `maxPoolSize=...` hoặc `queueCapacity=...`. Không thể dùng các thuộc tính pool đó để suy đoán hành vi fallback.
- Executor dự phòng được tạo cho cơ chế interceptor, không tương đương một bean pool do ứng dụng quản lý, giám sát và shutdown theo lifecycle chung.

**Rủi ro thực tế:** một ứng dụng có hai pool riêng, `ioPool` và `cpuPool`, nhưng không có bean tên `taskExecutor` và không có default rõ ràng. Người phát triển tưởng `@Async` tự chọn pool “hợp lý” giữa chúng. Trong khi đó, nó có thể dùng `SimpleAsyncTaskExecutor` cục bộ. Vì vậy nên khai báo lựa chọn default tường minh, theo dõi log và xác minh thread/pool thực tế.

## 7. Ma trận tình huống cần phân biệt

| Bean/config trong một context Spring Framework | `@Async` không qualifier chọn gì? | Lý do |
| --- | --- | --- |
| Chỉ có `ThreadPoolTaskExecutor` tên `reportPool` | `reportPool` | Một `TaskExecutor` chọn được theo kiểu |
| Có nhiều `TaskExecutor`, không ứng viên ưu tiên, có `Executor` tên `taskExecutor` | Bean tên `taskExecutor` | Tra cứu type mơ hồ; dùng tên fallback |
| Có nhiều `TaskExecutor`, không ứng viên ưu tiên, không bean `taskExecutor` | `SimpleAsyncTaskExecutor` dự phòng | Không thể chọn type hoặc name |
| Không có `TaskExecutor`, chỉ có `ExecutorService` tên `backgroundPool` | `SimpleAsyncTaskExecutor` dự phòng | Một raw `Executor` tên tùy ý không đủ cho bước 1 hoặc 2 |
| Không có `TaskExecutor`, có `ExecutorService` tên `taskExecutor` | Bean `taskExecutor` | Bước tìm theo tên cho phép kiểu `Executor` |
| Có một `TaskExecutor` chọn được và một raw `Executor` tên `taskExecutor` | `TaskExecutor` được chọn theo kiểu | Lookup type diễn ra trước |
| Có `AsyncConfigurer.getAsyncExecutor()` trả executor cụ thể | Executor do `AsyncConfigurer` cung cấp | Đã cấu hình default tường minh |
| Method ghi `@Async("reportPool")` | Bean theo tên/qualifier `reportPool` | Override default ở cấp phương thức |

**Điểm về @Primary:** khi có nhiều ứng viên `TaskExecutor`, `BeanFactory` có thể áp dụng quy tắc lựa chọn ứng viên như `@Primary`. Vì thế câu “nhiều `TaskExecutor` thì **luôn** dùng bean tên `taskExecutor`” là sai. Xác nhận cách bean được chọn trong ứng dụng cụ thể; ưu tiên khai báo `AsyncConfigurer` hoặc qualifier rõ ràng khi có nhiều pool.

## 8. Ví dụ Spring Framework + Java 17 chạy được: ba kịch bản lookup

Ví dụ sau dùng Java 17+, Maven, Spring Framework **6.2.9**. Nó tạo ba `ApplicationContext` riêng để không làm các bean của từng kịch bản ảnh hưởng nhau:

1. Một `TaskExecutor` duy nhất tên `analyticsPool` được chọn dù không có tên `taskExecutor`.
2. Có ba `TaskExecutor`, trong đó một bean tên `taskExecutor`; Spring chọn bean tên đó khi lookup theo kiểu mơ hồ.
3. Không có bean executor nào; Spring dùng fallback chạy trên một thread khác caller.

Mỗi trường hợp gọi `Probe.threadName()` **thông qua Spring bean proxy** rồi đợi `CompletableFuture`. Chúng ta không phụ thuộc vào thứ tự scheduling giữa task.

### Cấu trúc project

~~~text
spring-async-lookup-demo/
├── pom.xml
└── src/main/java/demo/ExecutorLookupDemo.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-async-lookup-demo</artifactId>
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

### src/main/java/demo/ExecutorLookupDemo.java

~~~java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class ExecutorLookupDemo {
    public static class Probe {
        @Async
        public CompletableFuture<String> threadName() {
            return CompletableFuture.completedFuture(
                Thread.currentThread().getName());
        }
    }

    private static ThreadPoolTaskExecutor pool(String prefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(4);
        executor.setThreadNamePrefix(prefix);
        return executor;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class UniqueConfig {
        @Bean public Probe probe() { return new Probe(); }
        @Bean public ThreadPoolTaskExecutor analyticsPool() {
            return pool("unique-");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class NamedConfig {
        @Bean public Probe probe() { return new Probe(); }
        @Bean public ThreadPoolTaskExecutor firstPool() {
            return pool("first-");
        }
        @Bean public ThreadPoolTaskExecutor secondPool() {
            return pool("second-");
        }
        @Bean("taskExecutor")
        public ThreadPoolTaskExecutor preferredPool() {
            return pool("named-");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class MissingConfig {
        @Bean public Probe probe() { return new Probe(); }
    }

    private static String runIn(Class<?> configClass) throws Exception {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(configClass)) {
            return context.getBean(Probe.class)
                    .threadName()
                    .get(5, TimeUnit.SECONDS);
        }
    }

    public static void main(String[] args) throws Exception {
        String callerThread = Thread.currentThread().getName();
        System.out.println("Unique TaskExecutor: " +
            runIn(UniqueConfig.class).startsWith("unique-"));
        System.out.println("Named taskExecutor: " +
            runIn(NamedConfig.class).startsWith("named-"));
        System.out.println("Fallback runs elsewhere: " +
            !runIn(MissingConfig.class).equals(callerThread));
    }
}
~~~

Chạy trong thư mục `spring-async-lookup-demo`:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.ExecutorLookupDemo
~~~

Các dòng **stdout do chương trình tự in ra**:

~~~text
Unique TaskExecutor: true
Named taskExecutor: true
Fallback runs elsewhere: true
~~~

Khi rơi vào kịch bản fallback, Spring **có thể in thêm log thông tin** về việc không tìm thấy `TaskExecutor` hay bean tên `taskExecutor`; đó là log framework, không phải dòng `System.out.println` của ví dụ. Ví dụ đã được **biên dịch với mục tiêu Java 17 và chạy trên OpenJDK 24**, ba dòng stdout được đối chiếu đúng. Các giá trị `core=1`, `max=2`, `queue=4` chỉ phục vụ test, không phải mặc định production.

Giải thích vì sao kết quả đáng tin:

- Từng context độc lập nên thuật toán chỉ nhìn thấy các bean của chính kịch bản đó.
- `@EnableAsync` bật proxy tại cả ba context; `Probe` là bean được tạo và lấy từ Spring, không dùng `new Probe().threadName()` trong caller.
- `@Async` không ghi tên nên cả ba đều chạy thuật toán default.
- `CompletableFuture.completedFuture(...)` được tạo **bên trong worker**; proxy trả cho caller một future thực sự đại diện việc thực thi async. `get(5, SECONDS)` là điều kiện chờ để kiểm tra kết quả.
- Prefix `unique-` và `named-` phân biệt rõ executor đã chọn. Trường hợp không có bean executor được kiểm tra bằng việc thread khác caller; không cố ghim prefix của executor dự phòng.
- `try`-with-resources đóng `AnnotationConfigApplicationContext`, nên các `ThreadPoolTaskExecutor` được khai báo `@Bean` có lifecycle do Spring quản lý.

## 9. Khi nào chuyển từ lookup mặc định sang lựa chọn tường minh?

Thuật toán của bài này chỉ áp dụng khi lời gọi `@Async` qua proxy **không chỉ định tên** và không có default từ `AsyncConfigurer`. Nếu muốn method luôn dùng pool I/O, ghi `@Async("ioExecutor")` và bảo đảm bean tương thích tồn tại. Khi nhiều method không qualifier cần cùng một default, có thể implement `AsyncConfigurer.getAsyncExecutor()` trả một bean executor đã được Spring quản lý.

Ví dụ phân biệt tại điểm gọi: `@Async` dùng nhánh lookup theo kiểu/tên/fallback; `@Async("billingExecutor")` chọn bean hoặc qualifier đó; `CompletableFuture.supplyAsync(...)` trong mã Java thuần lại dùng executor được truyền hoặc mặc định của JDK, không tham gia lookup Spring.

Đừng tạo một pool mới cho mỗi request. Với executor tự tạo ngoài `@Bean`, ứng dụng phải quản lý initialize/shutdown; với bean Spring nên để container xử lý lifecycle. Mục tiêu vận hành là chọn pool có giới hạn, quan sát được và không phụ thuộc vào fallback khó kiểm soát.

## 10. Đặc biệt: Spring Boot có thể đã tạo Executor trước đó

**Spring Framework thuần** và **Spring Boot auto-configuration** là hai lớp cơ chế khác nhau.

Trong **Spring Boot 3.5** làm ví dụ, khi không có bean `Executor` phù hợp do ứng dụng tự khai báo, Boot thường auto-configure một bean `AsyncTaskExecutor`:

- Thông thường là `ThreadPoolTaskExecutor` với cấu hình Boot cung cấp.
- Khi dùng Java 21+ và bật virtual threads, Boot có thể dùng `SimpleAsyncTaskExecutor` gắn virtual threads.
- Bean auto-configured thường tên `applicationTaskExecutor` và có thể được `@Async` dùng qua lookup theo kiểu, **không cần phải đổi tên thành `taskExecutor`**.
- Khi có bean `Executor` do ứng dụng cung cấp, auto-configuration có thể back off; phải đọc **phiên bản Spring Boot và cấu hình thực tế** để kết luận executor nào còn tồn tại.
- Spring MVC, WebFlux, GraphQL và các integration khác có thể yêu cầu bean tên `applicationTaskExecutor` với loại cụ thể; tên đó có ý nghĩa riêng, không đồng nhất với fallback `taskExecutor` của Spring Framework.

Do vậy không nên ghi “không cấu hình gì thì `@Async` chắc chắn dùng SimpleAsyncTaskExecutor” cho **mọi Spring Boot app**. Đó là kết luận đúng cho lookup Framework khi thực sự không có default executor hay bean phù hợp, nhưng Boot có thể tạo ra một executor trước khi thuật toán được thực thi.

Hãy phân biệt thêm: `@EnableAsync` kích hoạt annotation-based async; `spring.task.execution.*` là cấu hình Boot cho executor được Boot quản lý khi auto-configuration tương ứng áp dụng. Có property nhưng bean executor thực tế là bean tùy chỉnh hoặc fallback thì property đó có thể không ảnh hưởng như kỳ vọng.

## 11. Một số trường hợp dễ chọn nhầm

### 11.1. Nhiều TaskExecutor nhưng không ai tên taskExecutor

~~~text
@Bean("emailPool")     ThreadPoolTaskExecutor
@Bean("reportPool")    ThreadPoolTaskExecutor
Không có @Primary hoặc AsyncConfigurer
Không có bean tên taskExecutor
~~~

Đây là mô tả tình huống, không phải code Java hoàn chỉnh. Nếu lookup theo kiểu không phân giải được, Spring không tự bốc ngẫu nhiên `emailPool` hay `reportPool`; nó tìm theo tên `taskExecutor` rồi có thể dùng `SimpleAsyncTaskExecutor` fallback. **Sửa:** đặt tên/qualifier rõ ràng, chọn default bằng `AsyncConfigurer`, hoặc thiết kế ứng viên mặc định có thể xác định được.

### 11.2. Có bean tên taskExecutor nhưng cũng có ứng viên type phù hợp khác

~~~text
TaskExecutor bean: paymentPool (ứng viên duy nhất theo type)
Executor bean: taskExecutor (không implement TaskExecutor)
~~~

Nếu `paymentPool` là ứng viên kiểu `TaskExecutor` duy nhất, nó được chọn **trước** bean tên `taskExecutor`. Đổi tên bean thứ hai không tự làm thay đổi luật ưu tiên. **Sửa:** chọn tên tường minh tại method hoặc cấu hình `AsyncConfigurer`.

### 11.3. Không có @EnableAsync hoặc gọi self-invocation

Nếu `@Async` không được kích hoạt hoặc lời gọi không đi qua proxy, phương thức có thể chạy **đồng bộ trên caller thread**. Đừng chẩn đoán đây là “Spring tìm sai executor”; lúc này interceptor còn chưa tham gia vào lời gọi.

### 11.4. Tạo ExecutorService rồi cho rằng Spring sẽ tự chọn

Một `ExecutorService` bean tên `myExecutor` chỉ là `Executor` JDK, không nhất thiết là `TaskExecutor` Spring. Không có bean `TaskExecutor` nào và bean đó cũng không tên `taskExecutor`, thuật toán default Framework không tự chọn `myExecutor`. **Sửa:** dùng tên `taskExecutor`, `AsyncConfigurer`, hoặc `@Async("myExecutor")`.

### 11.5. Tưởng ứng dụng chỉ có một Spring ApplicationContext

Trong ứng dụng web có parent/child context, bean nằm ở context khác hoặc annotation chỉ bật ở một context có thể làm kết quả tra cứu khác dự kiến. Hãy xác nhận chính context chứa interceptor/bean được xử lý và những executor bean nó có thể nhìn thấy.

## 12. Cách xác nhận executor thật ở runtime

Đọc cấu hình là bước đầu; kiểm thử thread và metric là cách chứng minh lựa chọn được áp dụng:

1. **Tạo thread name prefix đặc trưng** cho từng `ThreadPoolTaskExecutor`: `email-`, `billing-`, `report-`. Trong task, log `Thread.currentThread().getName()` kèm operation ID.
2. **Lấy bean qua Spring context**, gọi `@Async` từ bên ngoài bean chứa method và quan sát kết quả; không tạo object bằng `new` trong test.
3. **Kiểm tra type và name:** liệt kê ứng viên `TaskExecutor`, bean `Executor` tên `taskExecutor`, `AsyncConfigurer` và qualifier tại method/class. Khi sử dụng Spring Boot, kiểm tra cả `applicationTaskExecutor` và auto-configuration.
4. **Bật log phù hợp** để thấy thông báo khi fallback; một số phiên bản ghi log kiểu “No task executor bean found for async processing” khi không tìm thấy bean.
5. **Xem metric pool:** active worker, core/max, queue size, rejected tasks và thread-name prefix. Nếu `@Async` thực sự chạy qua pool cụ thể, metric và thread name phải nhất quán.
6. **Kiểm tra overload:** với `ThreadPoolTaskExecutor`, việc nộp task có thể bị từ chối khi bão hòa; với `SimpleAsyncTaskExecutor` fallback không giới hạn concurrency mặc định, profile tải có thể rất khác.

Không nên dùng **chỉ** tên luồng `main` trong log để kết luận thiếu proxy: executor có thể dùng `CallerRunsPolicy` để chạy task trên caller thread khi quá tải. Kết hợp config, proxy, thread names, metric và một test không bão hòa để phân biệt.

## 13. Chọn executor đúng chưa đủ để bảo đảm nghiệp vụ

Khi đã biết Spring nộp `@Async` vào executor nào, còn những hợp đồng riêng phải thiết kế:

- **Exception:** method trả `CompletableFuture`/`Future` cho phép caller theo dõi completion và failure. Với `@Async void`, exception không quay về stack của caller; Spring có `AsyncUncaughtExceptionHandler` (mặc định thường log).
- **Rejection:** queue đầy/executor shutdown có thể khiến lời gọi nộp task thất bại ngay; phương thức nghiệp vụ vẫn chưa được xử lý trong worker. Cần backpressure, xử lý lỗi và retry có giới hạn nếu phù hợp.
- **Transaction:** với giao dịch imperative, transaction gắn với worker thread; không tự truyền transaction đang mở của HTTP request sang thread async. Cần ranh giới transaction ở worker nếu có thao tác DB.
- **Thread safety:** singleton bean có thể bị nhiều request gọi cùng lúc; việc chọn pool không bảo vệ shared mutable state.
- **Execution context:** `ThreadLocal`, MDC, security/request context không tự được bảo đảm truyền vào worker. Dùng cơ chế propagation phù hợp, dọn context đúng cách và truyền command immutable khi có thể.
- **Ordering và delivery:** thread pool không bảo đảm xử lý đúng thứ tự nộp hoặc chỉ một lần trên nhiều instance. `@Async` không làm công việc tồn tại qua crash; yêu cầu bền vững cần job store/queue/idempotency.
- **Lifecycle:** `ThreadPoolTaskExecutor` do Spring quản lý có hook shutdown/configuration; local fallback không tương đương một pool bean do ứng dụng quan sát được.

Nếu workload gồm nhiều công việc blocking I/O, CPU-bound và thao tác có rủi ro khác nhau, nên dùng executor được **chọn rõ** cho từng nhóm thay vì phụ thuộc vào tên mặc định khó kiểm soát.

## 14. Kiểm thử không mơ hồ

Bộ kiểm thử tích hợp hữu ích cho chủ đề này nên có:

1. **Một TaskExecutor:** đăng ký một bean với prefix riêng, gọi `@Async` không tên; assert thread name mang prefix đó.
2. **Nhiều TaskExecutor + taskExecutor:** đăng ký nhiều ứng viên type và một bean tên `taskExecutor`; assert đúng prefix tên fallback.
3. **Không có ứng viên:** context chỉ có `@EnableAsync` + service; assert task chạy ở thread khác caller, nhận biết thông báo fallback nếu có.
4. **@Async explicit qualifier:** khai báo `@Async("ioExecutor")` và default khác; assert chọn io executor.
5. **Self-invocation:** gọi qua `this` để minh họa không đi qua proxy trong default proxy mode; test phải tách khỏi bài test async thật.
6. **ApplicationContext riêng:** tránh lẫn bean của kịch bản trước, không để auto-configuration của Boot làm biến đổi bài test Framework thuần.
7. **Failure và overload:** khi pool bounded bão hòa, assert chính sách từ chối và cách caller xử lý; khi task ném lỗi, assert Future/handler nhận đúng lỗi.

Các test kiểu này xác thực điều quan trọng là **executor thực sự được chọn**, không chỉ kiểm tra bean tồn tại. Ví dụ ở mục 8 minh họa ba kịch bản trung tâm mà không cần so sánh thread-name cụ thể của fallback.

## 15. Checklist cấu hình @Async an toàn

- Tôi biết `@EnableAsync` được bật ở context nào và lời gọi đã đi qua Spring proxy.
- Tôi biết method có qualifier `@Async("...")` hoặc có `AsyncConfigurer` hay không.
- Tôi kiểm kê được các bean kiểu `TaskExecutor` và xác định liệu lookup type có duy nhất/ưu tiên rõ ràng.
- Tôi kiểm tra chính xác bean tên `taskExecutor`, kiểu phải là `Executor`.
- Tôi không suy ra `applicationTaskExecutor` của Boot giống hệt fallback `taskExecutor` của Framework.
- Tôi không phụ thuộc vào `SimpleAsyncTaskExecutor` fallback cho luồng tải sản phẩm chưa được giới hạn.
- Tôi chủ động thiết kế core/max/queue/rejection, shutdown và metric nếu sử dụng thread pool.
- Tôi phân biệt task đã được nộp với task đã hoàn thành thành công.
- Tôi kiểm soát exception, transaction, context propagation và shared mutable state của tác vụ.
- Tôi xác minh bằng một integration test và log thread prefix thay vì chỉ nhìn annotation.

**Kết luận:** Khi `@Async` được bật và không có cấu hình executor tường minh, Spring Framework ưu tiên **`TaskExecutor` theo kiểu**, sau đó **`Executor` có tên `taskExecutor`**, cuối cùng dùng **`SimpleAsyncTaskExecutor` dự phòng**. Trong Spring Boot, auto-configuration có thể tạo sẵn executor nên cần kiểm tra context thực tế trước khi kết luận.

## Tài liệu tham khảo chính thức

- [Spring Framework – EnableAsync Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html): thuật toán tìm executor, fallback, proxy và AsyncConfigurer.
- [Spring Framework – AsyncExecutionAspectSupport Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/interceptor/AsyncExecutionAspectSupport.html): lookup theo type/name và lựa chọn executor cho từng method.
- [Spring Framework – Async Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Async.html): qualifier ở method/class, kiểu trả về `Future` và `CompletableFuture`.
- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): bật `@Async`, qualifier và quản lý exception.
- [Spring Framework – SimpleAsyncTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/SimpleAsyncTaskExecutor.html): tạo thread, giới hạn concurrency và lifecycle.
- [Spring Boot 3.5 – Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html): `applicationTaskExecutor`, auto-configuration và tương tác `@EnableAsync`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-enablement-008">Chỉ định bộ thực thi theo tên hoặc qualifier trong @Async</a>

<details>
<summary>Xem chi tiết</summary>

# Chỉ định bộ thực thi theo tên hoặc qualifier trong @Async

## 1. Mục tiêu: một ứng dụng Spring, nhiều nhóm công việc

Trong Spring Framework, `@Async` cho phép một phương thức của bean Spring được gọi qua cơ chế xử lý bất đồng bộ. Thay vì thực thi ngay toàn bộ thân phương thức trong luồng gọi, Spring có thể gửi lời gọi vào một `Executor`/`TaskExecutor`. Khi ứng dụng chỉ có một nhóm công việc, cấu hình executor mặc định đôi khi là đủ. Nhưng trong hệ thống thực tế, nhiều nhóm tác vụ có đặc điểm rất khác nhau:

- **I/O-bound:** gọi API, đọc dữ liệu, gửi thông báo; thời gian chờ ngoài có thể lớn.
- **CPU-bound:** tính toán, chuyển đổi dữ liệu; nhiều worker hơn năng lực CPU chưa chắc có ích.
- **Tác vụ nhạy độ trễ:** cập nhật thông tin người dùng, phản hồi nhanh.
- **Tác vụ nền dài:** kết xuất báo cáo, xử lý tệp, công việc có thể chờ lâu hơn.

Nếu tất cả dùng chung một pool, các tác vụ dài có thể chiếm worker hoặc làm đầy queue, khiến tác vụ quan trọng phải đợi. Vì vậy cần **định tuyến từng phương thức `@Async` đến đúng executor**, bằng **tên bean** hoặc **qualifier**.

Ví dụ ý định thiết kế:

```text
                 Spring-managed service
                         |
             +-----------+-----------+
             |                       |
      @Async("ioExecutor")    @Async("computeLane")
             |                       |
             v                       v
       Executor A                Executor B
       io-worker-*               cpu-worker-*
       chờ I/O                   tính CPU
       queue riêng               queue riêng
```

**Cú pháp cần nắm:**

```java
@Async("ioExecutor")
public CompletableFuture<String> fetchData() {
    // Chạy bằng executor phù hợp với tên "ioExecutor".
    return CompletableFuture.completedFuture("done");
}
```

Đây là một **mảnh phương thức Spring**, không phải chương trình Java độc lập. Chuỗi `"ioExecutor"` nằm trong thuộc tính `value` của `@Async`. Spring dùng nó để tìm một bean executor phù hợp theo **bean name hoặc qualifier**, không phải để tạo pool mới và cũng không phải tên thread.

Kết quả mong đợi sau bài: bạn hiểu cách khai báo nhiều executor, chọn theo bean name/qualifier, ưu tiên annotation ở phương thức so với lớp, khai báo chọn executor bằng property, tránh các lỗi proxy/self-invocation, và kiểm thử việc định tuyến có thực sự hoạt động.

## 2. Điều kiện tiên quyết để @Async có hiệu lực

Chỉ viết `@Async("...")` trên một method **chưa đủ**. Trong mô hình dùng proxy của Spring, thường cần:

1. Có `@EnableAsync` trong một lớp `@Configuration` thuộc `ApplicationContext` phù hợp, hoặc cấu hình annotation-driven tương ứng.
2. Đối tượng chứa method phải là **bean được Spring quản lý**, không phải đối tượng do code nghiệp vụ tự `new` rồi gọi trực tiếp.
3. Lời gọi phải đi **qua Spring proxy**, ví dụ một bean khác inject service rồi gọi method.
4. Method phải có chữ ký hỗ trợ `@Async`: điển hình `void`, `Future<T>` hoặc `CompletableFuture<T>`; không khai báo return type bất kỳ như `String` rồi mong Spring tự chuyển đổi.
5. Executor được chỉ định phải là một bean `Executor`/`TaskExecutor` thực sự phù hợp và có thể được Spring tìm thấy.

Một lời gọi đúng theo hướng proxy:

```text
Bean A (caller)
  -> proxy của Bean B
      -> interceptor @Async
          -> tìm Executor theo value
              -> execute/submit nhiệm vụ
                  -> worker gọi thân phương thức Bean B
```

Nếu Bean B tự gọi `this.asyncMethod()` trong chính nó, lời gọi không đi qua proxy Spring ở chế độ mặc định và thân phương thức thường chạy đồng bộ trên caller. Khi đó, dù cú pháp `@Async("ioExecutor")` hoàn toàn đúng, **không có định tuyến sang executor** như mong đợi.

Tài liệu Spring ghi rõ `@EnableAsync` chỉ kích hoạt xử lý trong **application context cục bộ**. Với ứng dụng có nhiều context (ví dụ root context và servlet context tách nhau), phải đặt cấu hình đúng nơi các bean được quản lý.

## 3. @Async("name"): chọn executor theo bean name

Cách trực tiếp nhất là khai báo một executor bean có tên rõ ràng:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfiguration {

    @Bean("ioExecutor")
    public ThreadPoolTaskExecutor ioExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("io-worker-");
        return executor;
    }
}
```

Đây là **class cấu hình Spring** cần dependency Spring Framework. Nó không có phương thức `main`. Khi Spring tạo bean, tên được đăng ký là `ioExecutor`. Một phương thức bất đồng bộ khác có thể dùng đúng tên đó:

```java
@Async("ioExecutor")
public CompletableFuture<String> callRemoteApi(String id) {
    // Mã minh họa; không thực hiện mạng thật.
    return CompletableFuture.completedFuture("fetched-" + id);
}
```

Điểm mấu chốt: **tên trong `@Async` phải khớp bean name hoặc qualifier được đăng ký**. Nếu đặt `@Bean("ioExecutor")` nhưng ghi `@Async("io-worker-")` thì Spring không suy ra executor từ thread name prefix. Thread prefix chỉ giúp quan sát worker trong log/thread dump.

`@Async("ioExecutor")` và `@Async(value = "ioExecutor")` mang cùng ý nghĩa: `value` là thuộc tính của annotation và có thể lược tên thuộc tính khi cung cấp một giá trị.

**Đừng nhầm tên bean với tên phương thức cấu hình.** Với:

```java
@Bean("ioExecutor")
public ThreadPoolTaskExecutor makeIoPool() { /* ... */ }
```

Tên bean được chỉ định là `ioExecutor`, dù method Java tên `makeIoPool`. Với `@Bean` không truyền tên, tên phương thức thường trở thành tên bean mặc định; nên ưu tiên tên tường minh khi dùng để định tuyến `@Async`.

## 4. @Async("qualifier"): chọn theo nhãn @Qualifier

Spring cho phép đánh dấu một executor bean bằng qualifier. `@Async` có thể dùng **giá trị qualifier** làm tên tra cứu, ngay cả khi bean thực sự mang tên khác.

```java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ComputePoolConfiguration {

    @Bean("computeExecutor")
    @Qualifier("computeLane")
    public ThreadPoolTaskExecutor computeExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("cpu-worker-");
        return executor;
    }
}
```

Phía service:

```java
@Async("computeLane")
public CompletableFuture<Integer> calculate(int input) {
    return CompletableFuture.completedFuture(input * 2);
}
```

Trong trường hợp này:

| Thành phần | Giá trị | Vai trò |
|---|---|---|
| `@Bean("computeExecutor")` | `computeExecutor` | Tên bean thực sự trong container |
| `@Qualifier("computeLane")` | `computeLane` | Nhãn qualifier gắn với định nghĩa bean executor |
| `@Async("computeLane")` | `computeLane` | Yêu cầu Spring chọn executor có qualifier này |
| `setThreadNamePrefix("cpu-worker-")` | `cpu-worker-` | Tiền tố đặt tên worker thread, **không** được dùng để chọn bean |

**Qualifier không phải một executor khác.** Dùng qualifier chỉ thay cách xác định bean, còn tài nguyên thread/queue vẫn thuộc về đối tượng executor đã đăng ký. Điều này hữu ích khi muốn giữ tên bean ổn định cho wiring cũ, nhưng gắn nhãn theo ý nghĩa nghiệp vụ cho các phương thức `@Async`.

Một qualifier có thể là cách ghi lại vai trò logic, ví dụ `computeLane`, `reporting`, `notifications`, thay vì buộc phương thức nghiệp vụ biết tên triển khai cụ thể. Tuy nhiên, đừng cố tình gán một qualifier chung cho nhiều executor tương đương rồi dùng giá trị đó trong `@Async`: nếu không chọn được một bean duy nhất, việc phân giải có thể thất bại vì nhập nhằng.

### 4.1. @Qualifier ở vị trí nào mới liên quan?

`@Qualifier` dùng để chọn bean ở **điểm inject**:

```java
public MyService(@Qualifier("computeExecutor") Executor executor) {
    this.executor = executor;
}
```

không có nghĩa mọi method `@Async` trong `MyService` sẽ tự dùng executor đó. `@Async` có cơ chế chọn executor **riêng** thông qua `value`, executor mặc định hoặc `AsyncConfigurer`.

Muốn chọn qua qualifier trong annotation, phải gắn qualifier **lên định nghĩa executor bean** và viết `@Async("computeLane")` ở phương thức cần chạy, như ví dụ trên. Đừng đặt `@Qualifier` lên một tham số không liên quan rồi cho rằng nó sẽ thay thế nội dung `@Async`.

## 5. Ví dụ đầy đủ chạy được bằng Java 17 + Spring Framework

Ví dụ này cố ý dùng **hai executor tách biệt**. Một method chọn theo **tên bean**, method khác chọn theo **qualifier**, sau đó in tên thread thực sự đang chạy để chứng minh định tuyến.

Đây là dự án Maven tối giản, chạy trên **JDK 17+** với **Spring Framework 6.2.7**. Lấy đúng dependency/version trong `pom.xml` của ví dụ; khi dùng ứng dụng thật, chọn phiên bản phù hợp với BOM/stack của dự án.

### 5.1. Cấu trúc thư mục

```text
async-routing-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── lesson/
                └── AsyncRoutingDemo.java
```

### 5.2. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>lesson</groupId>
    <artifactId>async-routing-demo</artifactId>
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
                <version>3.13.0</version>
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

`spring-context` cung cấp những thành phần Spring sử dụng trong bài; Maven sẽ tải các dependency liên quan theo metadata. Ví dụ sử dụng **proxy mode mặc định của `@EnableAsync`**, không cần thêm cấu hình AspectJ weaving.

### 5.3. Tệp src/main/java/lesson/AsyncRoutingDemo.java

```java
package lesson;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class AsyncRoutingDemo {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class)) {

            // Luôn lấy service từ Spring để lời gọi đi qua proxy.
            WorkService service = context.getBean(WorkService.class);

            String ioThread = service.readFromIo().join();
            String cpuThread = service.calculate().join();

            System.out.println("I/O executor: " + ioThread);
            System.out.println("CPU executor: " + cpuThread);
        }
    }

    @Configuration
    @EnableAsync
    public static class Config {

        @Bean("ioExecutor")
        public ThreadPoolTaskExecutor ioExecutor() {
            ThreadPoolTaskExecutor executor =
                    new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(4);
            executor.setThreadNamePrefix("io-worker-");
            return executor;
        }

        @Bean("cpuExecutor")
        @Qualifier("computeLane")
        public ThreadPoolTaskExecutor cpuExecutor() {
            ThreadPoolTaskExecutor executor =
                    new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(4);
            executor.setThreadNamePrefix("cpu-worker-");
            return executor;
        }

        @Bean
        public WorkService workService() {
            return new WorkService();
        }
    }

    public static class WorkService {

        // Chọn executor qua tên bean.
        @Async("ioExecutor")
        public CompletableFuture<String> readFromIo() {
            String thread = Thread.currentThread().getName();
            return CompletableFuture.completedFuture(thread);
        }

        // Chọn executor qua qualifier, không phải bean name.
        @Async("computeLane")
        public CompletableFuture<String> calculate() {
            String thread = Thread.currentThread().getName();
            return CompletableFuture.completedFuture(thread);
        }
    }
}
```

### 5.4. Chạy và đọc kết quả

Trong thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=lesson.AsyncRoutingDemo
```

Hai dòng do chương trình in ra trong lần chạy thành công:

```text
I/O executor: io-worker-1
CPU executor: cpu-worker-1
```

Maven có thể in thêm thông tin riêng của công cụ nếu thay đổi tùy chọn chạy. Phần **hai dòng trên** là nội dung từ `System.out.println` trong chương trình.

Tại sao có thể dự đoán tên thread ở ví dụ tối giản này?

- Hai pool được tạo độc lập, mỗi pool chỉ thực thi **một task** trong toàn bộ lần chạy.
- Spring sử dụng prefix tương ứng khi khởi tạo worker đầu tiên của mỗi pool, nên các tên là `io-worker-1` và `cpu-worker-1`.
- Chương trình lấy service từ `ApplicationContext`, nên lời gọi đi qua proxy có `@Async`.
- `join()` chạy ở luồng main để chờ từng kết quả **chỉ nhằm kiểm tra ví dụ**; thân method được worker của executor tương ứng thực thi.
- Các method trả `CompletableFuture.completedFuture(...)` bên trong worker vì phương thức đích phải giữ đúng kiểu trả về. Spring proxy trả một `CompletableFuture` đại diện cho phần chạy bất đồng bộ.

**Không sao chép cách gọi `join()` ngay trong mọi request HTTP** rồi cho rằng endpoint vẫn bất đồng bộ hoàn toàn: luồng request sẽ phải đợi. Trong ứng dụng thật, caller có thể trả về future, gắn callback, hoặc phối hợp kết quả không chặn theo contract của tầng trên.

Ví dụ này không làm HTTP thật hay tính toán nặng. Nó **kiểm chứng đường định tuyến** giữa method và executor, không phải bài benchmark I/O/CPU.

## 6. Có gì khác nhau giữa tên bean, qualifier và tên thread?

Ba khái niệm này khác hẳn nhau:

| Thuộc tính | Ai sử dụng? | Ví dụ | Nếu đổi thì sao? |
|---|---|---|---|
| Bean name | Spring IoC/lookup | `ioExecutor` | Các lời gọi `@Async("ioExecutor")` có thể không còn tìm đúng bean |
| Qualifier | Spring IoC chọn một bean theo nhãn | `computeLane` | Các lời gọi chọn bằng qualifier phải đổi theo |
| Thread name prefix | Executor tạo worker thread | `cpu-worker-` | Chỉ ảnh hưởng tên worker, không đổi bean được chọn |
| Java method name khai báo bean | Java/Spring tạo bean | `cpuExecutor()` | Nếu `@Bean` có tên tường minh, đổi method name không nhất thiết đổi bean name |
| Tên method `@Async` | Nghiệp vụ / proxy method | `calculate()` | Không tự quyết định tên executor |

Không dùng tên thread để tìm executor: log có dòng `cpu-worker-1` không có nghĩa bean tên `cpu-worker-1`. Ngược lại, một executor bean tên `reportingExecutor` có thể đặt tiền tố thread là `report-` mà vẫn được chọn bằng `@Async("reportingExecutor")`.

Khi đặt tên, hãy ưu tiên mô tả **nhóm tải** hoặc **ý nghĩa tài nguyên**: `ioExecutor`, `reportExecutor`, `mailExecutor`, `computeLane`. Tránh biến tên bean thành thông số triển khai như `poolWithEightThreads`, vì sau khi thay cấu hình kích thước thì tên sẽ gây hiểu lầm.

## 7. @Async ở cấp lớp và ưu tiên ở cấp phương thức

`@Async` cho phép khai báo trên **class** lẫn **method**. Nếu gắn ở class, các phương thức phù hợp trong class trở thành ứng viên xử lý bất đồng bộ. Method có `@Async` với `value` cụ thể sẽ **ưu tiên hơn executor được chỉ định ở class**.

Ví dụ:

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Async("ioExecutor")
public class MixedWorkService {

    public CompletableFuture<String> fetch() {
        return CompletableFuture.completedFuture("I/O");
    }

    @Async("computeLane")
    public CompletableFuture<String> transform() {
        return CompletableFuture.completedFuture("CPU");
    }
}
```

Đây là **Spring component**, không có chương trình `main` độc lập; giả định các executor `ioExecutor` và qualifier `computeLane` đã tồn tại.

| Method | Chọn executor | Vì sao |
|---|---|---|
| `fetch()` | `ioExecutor` | Dùng cấu hình `@Async("ioExecutor")` tại class |
| `transform()` | `computeLane` | `@Async("computeLane")` tại method ghi đè lựa chọn của class |

**Đừng hiểu class-level annotation là cách đổi default executor toàn ứng dụng**: nó chỉ áp dụng cho phạm vi class được đánh dấu. Nếu muốn đặt mặc định chung cho các phương thức dùng `@Async` không có `value`, cấu hình executor mặc định ở `@EnableAsync`/`AsyncConfigurer` hoặc cấu hình tương đương.

Dùng class-level khi đa số method trong service thực sự cùng một nhóm tải. Nếu chỉ có một hoặc hai method cần bất đồng bộ, gắn method-level thường dễ kiểm soát hơn: tránh vô tình biến method khác thành async, đặc biệt sau khi thêm method mới.

Lưu ý JavaDoc của `@Async`: không áp dụng annotation này cho các method khai báo trong một class `@Configuration`. Hãy đặt business method vào service/component riêng và để configuration chỉ khai báo tài nguyên.

## 8. Khi annotation không có value, điều gì thay đổi?

`@Async` không qualifier dùng executor mặc định của context: có thể do `AsyncConfigurer` trả về, do Spring Framework tra một `TaskExecutor` có thể chọn, rồi bean `Executor` tên `taskExecutor`, hoặc fallback khi không có ứng viên. Spring Boot còn có thể cung cấp sẵn executor qua auto-configuration. Vì thế có hai đường khác nhau:

```java
@Async                    // Mặc định, phụ thuộc cấu hình context.
public CompletableFuture<String> generalJob() {
    return CompletableFuture.completedFuture("general");
}

@Async("ioExecutor")       // Chỉ định bean/qualifier cụ thể.
public CompletableFuture<String> ioJob() {
    return CompletableFuture.completedFuture("io");
}
```

Đây là các method minh họa trong Spring service, không phải file Java độc lập. Khi cần kiểm soát SLA theo nhóm công việc, phương thức có yêu cầu riêng nên chọn qualifier rõ ràng và được test trong context thực; `@Primary` hay một tên bean bất kỳ không thay thế bằng chứng đường định tuyến.

## 9. Chọn tên executor thông qua property placeholder hoặc SpEL

JavaDoc `Async.value` cho phép cung cấp một **property placeholder** hoặc **SpEL expression** thay vì hard-code tên bean. Đây là cách hữu ích để chuyển tuyến theo môi trường triển khai mà không phải sửa annotation.

### 9.1. Property placeholder

Giả sử cấu hình Environment chứa:

```properties
app.async.report-executor=ioExecutor
```

Service có thể viết:

```java
@Async("${app.async.report-executor}")
public CompletableFuture<String> createReport(String id) {
    return CompletableFuture.completedFuture(id);
}
```

Trong văn bản code ở trên, chuỗi bắt đầu bằng **dấu dollar và dấu ngoặc nhọn** theo cú pháp Spring property placeholder. Khi Spring resolve thành `ioExecutor`, executor bean có tên ấy được chọn. Đây là **mảnh method Spring** cần đúng property source, context và bean executor.

Trong Spring Boot, property có thể được khai báo trong `application.properties`, biến môi trường hoặc cơ chế cấu hình tương thích; khi dùng Spring Framework thuần, cần khai báo property source/Environment phù hợp. Nếu property không được giải quyết hoặc giá trị chỉ tới bean không tồn tại, việc định tuyến có thể thất bại, không nên lặng lẽ thay bằng executor khác.

### 9.2. SpEL

Có thể lấy tên qua Spring Expression Language:

```java
@Async("#{environment['app.async.report-executor']}")
public CompletableFuture<String> createReport(String id) {
    return CompletableFuture.completedFuture(id);
}
```

Giá trị property trong ví dụ vẫn là `ioExecutor`. JavaDoc mô tả giá trị `@Async.value` được resolve động khi là SpEL hoặc placeholder; điều này không đồng nghĩa nó là một expression tùy ý có thể phụ thuộc trực tiếp vào mọi tham số method mà không có thiết kế context phù hợp.

**Chọn cách nào?**

- Tên cố định `@Async("ioExecutor")`: dễ đọc và dễ phát hiện sai cấu hình.
- Placeholder `@Async("${app.async.report-executor}")`: phù hợp khi cần chọn tên theo môi trường hoặc cấu hình deploy.
- SpEL: phù hợp khi cần logic resolution rõ ràng hơn, nhưng nên giữ biểu thức đơn giản, dễ test.

Với nhu cầu chuyển executor **theo từng request/tenant** ở runtime, đừng mặc định placeholder/SpEL sẽ làm một bộ điều phối công việc động hoàn chỉnh. Hãy xác định chính sách định tuyến trong service hoặc một abstraction riêng, tránh biến annotation thành nơi chứa logic khó kiểm soát.

## 10. Phân biệt qualifier cho injection và qualifier cho @Async

Xét service sau:

```java
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;

public class ConfusingService {
    private final Executor executor;

    public ConfusingService(
            @Qualifier("ioExecutor") Executor executor) {
        this.executor = executor;
    }

    @Async("computeLane")
    public void process() {
        System.out.println("Đang xử lý");
    }
}
```

Đây là class minh họa, cần đăng ký làm bean Spring để `@Async` có hiệu lực.

Ý nghĩa chính xác:

- `@Qualifier("ioExecutor")` ở constructor lựa chọn bean để gán vào **field executor**.
- `@Async("computeLane")` trên `process()` lại yêu cầu **computeLane** thực thi thân method.
- Field `executor` không hề được gọi trong `process()`; qualifier trên constructor **không** ép `@Async` phải dùng `ioExecutor`.

Đây là hai cơ chế tách biệt, dù cùng dùng từ “qualifier”. Nếu service vừa gửi thủ công qua field vừa dùng `@Async`, rất dễ có hai đường thực thi khác nhau. Hãy đặt tên nhất quán, tài liệu hóa nhiệm vụ của từng pool và viết test phân biệt chúng.

### 10.1. Có thể dùng Executor thay vì TaskExecutor?

Có. JavaDoc `Async.value` đề cập matching với bean kiểu `java.util.concurrent.Executor` **hoặc** `org.springframework.core.task.TaskExecutor`. Vì `TaskExecutor` mở rộng `Executor`, một Spring `ThreadPoolTaskExecutor` đáp ứng cả hai.

Bạn cũng có thể đăng ký `Executor` do thư viện khác cung cấp rồi cho Spring sử dụng, nhưng phải xác định ai sở hữu pool, vòng đời và chính sách từ chối. Không nên biến một tài nguyên chỉ dùng chung làm pool bị shutdown bởi service con.

Trong Jakarta EE, `DefaultManagedTaskExecutor` có thể được đăng ký như Spring bean để chuyển tới managed executor qua JNDI. `@Async("managedExecutor")` chọn bean Spring có tên đó; việc quản lý worker do Jakarta EE server thực hiện. Đây là một lựa chọn executor khác, không thay đổi ý nghĩa của tham số `value`.

## 11. Chọn đúng tên executor nhưng method vẫn không được định tuyến?

Trong `AdviceMode.PROXY` mặc định, việc phân giải bean name/qualifier chỉ diễn ra khi lời gọi đi **qua Spring-managed proxy**. Nếu cùng bean gọi `this.runInBackground()`, hoặc object được tạo thủ công bằng `new`, async interceptor không tham gia. Method `private`/`final` và code trong constructor/lifecycle cũng không phải điểm chặn thích hợp của proxy thông thường; `@Async` không được hỗ trợ trên method khai báo trong `@Configuration`.

Để tách hai nhóm lỗi, trước tiên xác minh context có `@EnableAsync`, bean do Spring quản lý và method public/non-final được gọi từ bean khác. Sau đó kiểm tra bean `Executor` theo tên hoặc qualifier `@Async("...")`. Nếu đã qua proxy nhưng tên sai hoặc qualifier nhập nhằng, lời gọi có thể thất bại lúc chọn executor; nếu pool đầy, submission có thể bị từ chối. Đổi `proxyTargetClass` không sửa tên bean sai hay làm private/final method trở thành điểm chặn hợp lệ.

Khi rename `@Bean("ioExecutor")` hoặc `@Qualifier("computeLane")`, tìm tất cả các method `@Async` đang tham chiếu và chạy lại integration test với cả hai pool. Dùng thread-prefix để quan sát, nhưng không dùng tên thread làm dữ liệu nghiệp vụ.

## 12. Kết quả và ngoại lệ khi đã định tuyến đúng

Chọn đúng executor chỉ giải quyết **nơi công việc được chạy**. Caller và service vẫn phải quản lý kết quả, lỗi, thời hạn và trạng thái nghiệp vụ.

### 12.1. Method trả void

```java
@Async("ioExecutor")
public void sendNotification(String id) {
    // Làm việc trên executor đã chọn; không có kết quả cho caller.
}
```

Nếu thân method ném ngoại lệ trên worker, caller không có `Future` để bắt trực tiếp. Spring cung cấp `AsyncUncaughtExceptionHandler` cho những ngoại lệ từ method `@Async` có kiểu trả về `void`; hành vi mặc định là ghi log. Các lỗi xảy ra **trước khi task được nhận** (ví dụ executor bị từ chối) là tình huống khác cần xử lý ở biên gửi việc.

### 12.2. Method trả CompletableFuture

```java
@Async("computeLane")
public CompletableFuture<Integer> twice(int x) {
    if (x < 0) {
        throw new IllegalArgumentException("x phải không âm");
    }
    return CompletableFuture.completedFuture(x * 2);
}
```

Khi gọi **qua Spring proxy**, caller nhận một `CompletableFuture<Integer>` đại diện cho phần thực thi async. Nếu phần code trên worker ném `IllegalArgumentException`, future của caller hoàn tất theo hướng lỗi; caller có thể gắn `exceptionally(...)`, `handle(...)` hoặc quan sát exception khi `join()/get()`.

Ví dụ:

```java
CompletableFuture<Integer> future = service.twice(-1);
future.whenComplete((value, error) -> {
    if (error != null) {
        System.err.println("Tác vụ thất bại: " + error);
    }
});
```

Đây là **mảnh mã caller** có biến `service` được inject/tra cứu từ Spring; không phải chương trình chạy độc lập. Nếu dùng `join()` hoặc `get()` thì caller có thể bị chặn; điều này cần cân nhắc nếu đang ở HTTP request thread.

### 12.3. Lỗi khi executor quá tải

`ThreadPoolTaskExecutor` có thể từ chối tác vụ mới khi worker/queue đầy hoặc pool đóng. Với cấu hình `AbortPolicy` thông thường, Spring thể hiện rejection bằng `TaskRejectedException` từ đường gửi. Điều đó không có nghĩa method nghiệp vụ đã được chạy rồi thất bại.

Hai loại lỗi khác nhau:

| Thời điểm | Ví dụ | Nơi xử lý |
|---|---|---|
| Phân giải/chọn executor | `@Async("missingPool")`, tên sai hoặc qualifier nhập nhằng | Sửa cấu hình, bean name và test wiring |
| Gửi task | Pool đã shutdown hoặc quá tải | Caller hoặc biên nhận request: backpressure, lỗi/retry phù hợp |
| Task đang chạy | Exception của logic bên trong `@Async` | `Future/CompletableFuture` hoặc `AsyncUncaughtExceptionHandler` với `void` |
| Task hoàn thành quá muộn | Deadline request đã hết hoặc kết quả hết giá trị | Timeout, cancel, kiểm tra deadline và idempotency |

Không được nuốt exception từ chối rồi vẫn trả “đã xử lý” cho người dùng. `@Async` không phải message broker bền vững: nếu JVM crash, các công việc chỉ còn trong queue bộ nhớ có thể mất.

## 13. Đặt pool theo đặc điểm tải, không chỉ theo tên

Định tuyến có ích khi pool được thiết kế theo hành vi công việc.

| Nhóm | Ví dụ | Điều cần cân nhắc |
|---|---|---|
| I/O-bound | HTTP, database, RPC | Giới hạn connection, timeout, downstream capacity, số worker |
| CPU-bound | Xử lý số liệu, nén, transform | Số lõi, CPU quota, contention, tránh over-subscription |
| Ít tốn thời gian nhưng quan trọng | Cập nhật trạng thái nhanh | Queue và độ trễ p95/p99; không bị job dài làm nghẽn |
| Công việc nền dài | Báo cáo, xử lý tệp | Hàng đợi giới hạn, khả năng retry, lưu job bền vững nếu cần |

Có hai pool riêng không có nghĩa chúng cách ly hoàn toàn. Chúng vẫn chia sẻ CPU, heap, GC, database, mạng và cả tài nguyên của các hệ thống ngoài. Nếu hai pool cùng tranh nhau một connection pool nhỏ, cô lập ở tầng executor chưa đảm bảo cô lập năng lực downstream.

Các cấu hình `corePoolSize`, `maxPoolSize`, `queueCapacity` cần được chọn cùng nhau. Với `ThreadPoolExecutor` bên dưới `ThreadPoolTaskExecutor`, khi đạt số worker lõi, hệ thống thường ưu tiên xếp hàng trước khi mở thêm worker tới max. Một queue quá lớn có thể khiến `maxPoolSize` gần như không được dùng; queue quá nhỏ có thể dẫn tới rejection khi burst.

**Cách đo và xác nhận:**

- Log/metrics phân tách theo **tên executor**: submitted, active, queued, completed, rejected.
- Kiểm tra `getPoolSize()`, `getActiveCount()` và queue size bằng API/monitoring được Spring hỗ trợ cho phiên bản đang chạy.
- Đo thời gian từ lúc task được gửi đến khi worker bắt đầu, p95/p99 latency và thời gian xử lý.
- Giám sát connection pool, CPU, heap, GC và upstream retries.
- Đặt giới hạn đồng thời hoặc rate limit nếu lượng gửi vượt năng lực xử lý; tăng queue không tạo thêm thông lượng.
- Chọn chính sách khi ứng dụng đóng: task mới bị từ chối, task đang chạy xử lý thế nào, task đang chờ có được hoàn tất hay hủy.

Không tạo một pool mới mỗi lần gọi `@Async` và không tự `shutdown()` một executor bean dùng chung từ một service đơn lẻ. Để Spring/container quản lý lifecycle của bean; trách nhiệm nghiệp vụ là gửi việc có kiểm soát và quan sát kết quả.

## 14. ThreadLocal, security context và transaction không tự theo executor

Một executor được chọn đúng theo `@Async("ioExecutor")` không đồng nghĩa mọi dữ liệu của luồng gọi tự có ở worker:

- `ThreadLocal` tự tạo và MDC logging thường gắn với thread đang gọi.
- `SecurityContextHolder` và ngữ cảnh request của Spring cần cơ chế truyền phù hợp nếu task thực sự cần chúng.
- Spring transaction thường gắn với thread; method `@Async` chạy trên worker **không tự tham gia transaction của caller**.
- Một `EntityManager`, JDBC connection hoặc request object có vòng đời riêng; không nên chuyển các đối tượng đó sang worker để sử dụng tùy tiện.

Nếu task cần `correlationId`, `tenantId` hay thông tin nhận diện phục vụ xử lý, truyền **dữ liệu bất biến cần thiết** qua tham số hoặc dùng cơ chế context propagation đã được kiểm chứng. Nếu task cần transaction, thiết kế transaction mới trên đường thực thi thích hợp.

Quan trọng: chọn executor là một **quyết định định tuyến tác vụ**, không thay thế đảm bảo về transaction, security, context propagation hay sự tồn tại bền vững của công việc.

## 15. Kiểm chứng chọn đúng executor trong kiểm thử

Tối thiểu nên có một bài integration test chạy với `ApplicationContext` thật:

1. Khởi tạo context có `@EnableAsync` và cả hai executor.
2. Lấy service từ Spring, không `new` trực tiếp.
3. Gọi method `@Async("ioExecutor")` và method `@Async("computeLane")`.
4. Quan sát thread name/pool metrics hoặc thực thi task đánh dấu rõ mốc bắt đầu; xác định mỗi method đi vào pool mong muốn.
5. Dùng `CompletableFuture`/`CountDownLatch` để chờ có thời hạn khi kiểm thử async; không dùng `Thread.sleep(...)` rồi giả định task đã xong.
6. Thử đổi qualifier sai trong môi trường kiểm thử để hiểu lỗi lookup; khôi phục đúng tên.
7. Thử self-invocation để biết vì sao annotation có thể không có hiệu lực.
8. Thử quá tải có kiểm soát và xác nhận rejection được báo đúng.

### 15.1. Vì sao không chỉ kiểm tra @Bean tồn tại?

`context.containsBean("ioExecutor")` chỉ chứng minh có bean tên đó. Nó **không chứng minh** method `@Async` đã được proxy hoặc thực sự dùng bean ấy. Có thể có bean đúng nhưng lời gọi đi qua `this` làm mất interception.

Ngược lại, nhìn thấy hai tên thread khác nhau cũng chưa đủ trong một hệ thống lớn: thread có thể được tạo bởi pool khác trùng prefix. Ví dụ `AsyncRoutingDemo` đặt hai prefix phân biệt và chạy riêng nên đủ để học nguyên tắc; trong production nên kết hợp quan sát bean wiring, pool metrics và test cụ thể.

### 15.2. Không biến thread name thành hợp đồng nghiệp vụ

Tên thread phù hợp để debug hoặc test mẫu, nhưng **không phải API bảo đảm kết quả nghiệp vụ**. Người dùng không cần biết task chạy trên `io-worker-1` hay `io-worker-3`; họ cần biết công việc đã được tiếp nhận, có thể theo dõi, và lỗi được xử lý.

Nếu test production phụ thuộc số thứ tự thread cụ thể, nó dễ lỗi khi tăng pool size hoặc thay thread factory. Test nên chứng minh tác vụ đi vào **đúng executor logic**, không đóng đinh một thread ID không liên quan.

## 16. Sử dụng qualifier từ XML và trường hợp hỗn hợp

Dự án Spring cũ có thể cấu hình executor và qualifier bằng XML. Nguyên tắc vẫn giống Java Configuration: `@Async.value` có thể tìm theo tên bean hoặc qualifier gắn với executor.

Đoạn XML **minh họa**, nằm trong tệp Spring XML đã khai báo namespace `beans`/`task` hợp lệ:

```xml
<bean id="computeExecutor"
      class="org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor">
    <qualifier value="computeLane"/>
    <property name="corePoolSize" value="2"/>
    <property name="maxPoolSize" value="2"/>
    <property name="queueCapacity" value="8"/>
</bean>
```

Sau đó `@Async("computeLane")` vẫn có thể dùng nhãn đó để lựa chọn. Nếu XML cấu hình annotation-driven executor mặc định, đó là cấu hình default; nó không thay thế được lựa chọn cụ thể của mọi method có `@Async("...")`.

Khi một ứng dụng trộn Java Configuration, XML, Spring Boot auto-configuration và cấu hình có điều kiện theo profile, việc trùng tên bean hoặc trùng qualifier dễ xảy ra. Hãy liệt kê **những bean `Executor` thực sự tồn tại tại runtime**, theo đúng active profiles, thay vì suy luận chỉ từ file cấu hình nào đó.

## 17. Những lỗi thường gặp

| Dấu hiệu | Nguyên nhân có thể | Cách kiểm tra và sửa |
|---|---|---|
| `@Async("ioExecutor")` nhưng method chạy trên thread request | Self-invocation, đối tượng tự `new`, chưa bật `@EnableAsync` | Gọi qua bean proxy; kiểm tra ApplicationContext |
| Không tìm thấy `ioExecutor` | Sai tên hoặc bean không được đăng ký vì profile/condition | Kiểm tra tên bean thực tế, namespace/context |
| `@Async("cpu-worker-")` không tìm được pool | Dùng thread prefix thay cho tên/qualifier | Dùng `@Bean("computeExecutor")` hoặc `@Qualifier("computeLane")` |
| `@Async("computeLane")` không chọn được bean | Qualifier chưa gắn lên executor bean, hoặc nhập nhằng | Khai báo qualifier đúng trên bean và bảo đảm chọn duy nhất |
| Constructor inject `@Qualifier("ioExecutor")` nhưng @Async dùng pool khác | Nhầm qualifier injection với `@Async.value` | Chỉ rõ executor ngay trên method |
| Đặt `@Async` ở class nhưng một method quan trọng vào sai pool | Không khai báo override ở method | Dùng `@Async("otherExecutor")` cho method cần khác |
| Ghi `@Async` không có value nhưng dự đoán theo một bean bất kỳ | Default selection khác mong đợi | Cấu hình AsyncConfigurer hoặc dùng tên tường minh |
| Đổi property chứa tên pool nhưng task không chạy | Placeholder không resolve, tên đích sai | Kiểm tra Environment và bean name/qualifier |
| Method annotated có return type `String` | Không phải kiểu trả về hợp lệ cho xử lý async thông thường | Dùng `void`, `Future` hoặc `CompletableFuture` |
| Exception từ method `void` không tới caller | Không có kết quả trả về | Cấu hình `AsyncUncaughtExceptionHandler` hoặc đổi API |
| Future bị lỗi nhưng không ai quan sát | Không có xử lý hoàn tất | Dùng `whenComplete`, `handle` hoặc cơ chế giám sát |
| Task bị từ chối khi queue đầy | Pool quá tải hoặc đã đóng | Thiết kế backpressure, xử lý `TaskRejectedException` |
| Context/security/transaction không còn | Thread mới không mặc định mang mọi context | Truyền thông tin đúng cách và tạo transaction phù hợp |
| Annotation trên method cấu hình `@Bean` | `@Async` không hỗ trợ trên method trong `@Configuration` | Đưa công việc sang service được Spring quản lý |

## 18. Bài tập thực hành

### Bài 1 — Chọn bằng tên bean

Khai báo hai `ThreadPoolTaskExecutor` có bean name `imageExecutor` và `mailExecutor`. Viết hai method tương ứng `resizeImage(...)` và `sendMail(...)` dùng `@Async` để mỗi method vào pool riêng.

**Định hướng:** Đặt `@Bean("imageExecutor")`/`@Bean("mailExecutor")` rồi dùng `@Async("imageExecutor")` và `@Async("mailExecutor")`. Hãy kiểm tra `@EnableAsync` và lời gọi qua proxy.

### Bài 2 — Chọn bằng qualifier

Bean có tên `batchPool` nhưng qualifier là `reports`. Viết method `generateReport()` định tuyến theo qualifier mà không đổi tên bean.

**Định hướng:** Khai báo `@Bean("batchPool")`, `@Qualifier("reports")` trên định nghĩa bean, và `@Async("reports")` tại method. Qualifier dùng ở constructor injection của một thành phần khác không tự gán lại qualifier cho bean.

### Bài 3 — Ưu tiên class/method

Class `@Async("ioExecutor")` có hai method `fetch()` và `compute()`; riêng `compute()` khai báo `@Async("computeLane")`. Khi gọi qua proxy, method nào dùng pool nào?

**Định hướng:** `fetch()` dùng executor chỉ định ở class, `compute()` dùng executor chỉ định ở method.

### Bài 4 — Thay tên qua cấu hình

Thiết lập `app.async.report-executor=ioExecutor` và method `@Async("${app.async.report-executor}")`. Khi thay property thành `computeExecutor`, cần xác minh điều gì?

**Định hướng:** Phải có bean executor tên `computeExecutor`, property được resolve đúng trong context, và workload phải phù hợp pool mới. Đổi value không tự thay kích thước pool hoặc bảo đảm cách ly tài nguyên.

### Bài 5 — Tìm lỗi self-invocation

Một method đồng bộ `start()` của bean gọi `this.doAsync()`, dù `doAsync()` có `@Async("ioExecutor")`. Vì sao quan sát thấy nó vẫn chạy trên thread caller?

**Định hướng:** Lời gọi không đi qua proxy. Tách method async sang bean khác rồi inject/gọi qua Spring.

### Bài 6 — Chạy dự án Maven

Chạy `AsyncRoutingDemo` ở mục 5, xác minh hai dòng có prefix khác nhau. Sau đó đổi `@Async("computeLane")` thành `@Async("ioExecutor")` và dự đoán dòng thứ hai.

**Định hướng:** Hai lời gọi sẽ chọn cùng bean `ioExecutor`; ở cấu hình ví dụ với một worker đang được dùng tuần tự, prefix dòng thứ hai sẽ là `io-worker-`. Đây là thay đổi routing, không phải đổi tên method hay dữ liệu tính toán.

### Bài 7 — Chỉ định executor hay chỉnh pool?

Một team định tuyến đúng `@Async("reporting")` nhưng queue báo cáo tăng không ngừng. Cần tăng `maxPoolSize` hay tăng `queueCapacity` trước?

**Định hướng:** Không quyết định chỉ từ tên executor. Đo queue wait, throughput, thời gian chạy, giới hạn downstream và memory. Nếu queue dài, tăng queue thường làm chờ lâu hơn; nếu pool đã bị downstream chặn, tăng worker có thể gây thêm nghẽn. Xử lý giới hạn đầu vào và SLA trước khi chỉnh số.

## 19. Checklist áp dụng vào dự án

Khi một PR thêm hoặc sửa `@Async("...")`, hãy kiểm tra:

1. **Tên/qualifier:** Chuỗi trong annotation khớp chính xác bean name hoặc qualifier của một `Executor`/`TaskExecutor` hiện hữu.
2. **Đăng ký bean:** Bean executor có hiệu lực trong ApplicationContext và active profile cần dùng.
3. **Bật async:** `@EnableAsync`/cấu hình tương đương đã áp dụng trong đúng context.
4. **Đường gọi:** Method public, non-final (ở thiết kế proxy thông thường), được gọi qua proxy chứ không qua `this`.
5. **Ưu tiên:** Annotation method-level có chủ ý so với lựa chọn ở class-level/default.
6. **Kết quả:** Kiểu trả về `void/Future/CompletableFuture` phù hợp nhu cầu theo dõi; lỗi có kênh quan sát.
7. **Tài nguyên:** Worker/queue có giới hạn, phù hợp workload, có rejection policy.
8. **Ngữ cảnh:** Thông tin request/security/transaction cần dùng trong worker được xử lý đúng.
9. **Vòng đời:** Không tạo/shutdown pool trong mỗi request; hiểu hành vi dừng ứng dụng.
10. **Kiểm thử:** Có bằng chứng định tuyến qua Spring proxy và giám sát ở mức executor, không chỉ kiểm tra bean tồn tại.

## 20. Kết luận

- **`@Async("beanName")` chọn executor bean theo tên** đã đăng ký với Spring.
- **`@Async("qualifier")` cũng có thể chọn executor theo qualifier** như `@Qualifier("computeLane")` gắn lên định nghĩa bean.
- `@Async.value` không phải tên thread, không phải tên class tùy ý và không tự tạo pool mới.
- **Method-level selection ưu tiên class-level**, còn method không chỉ định executor dùng cấu hình mặc định phù hợp.
- `@EnableAsync` và **lời gọi đi qua Spring proxy** là điều kiện căn bản để việc định tuyến có hiệu lực ở chế độ proxy mặc định.
- Chọn đúng pool chưa đủ: phải quản lý **kết quả/lỗi, tài nguyên, context, transaction và quá tải** theo trách nhiệm của ứng dụng.
- Dùng **một bài kiểm thử tích hợp với hai executor** như ví dụ Maven để chứng minh method thực sự đi đến pool đã chọn.

### Tài liệu chính thức

- [Spring Framework Javadoc — Async](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Async.html)
- [Spring Framework Javadoc — EnableAsync](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Spring Framework Javadoc — AsyncConfigurer](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/AsyncConfigurer.html)
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework Javadoc — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-enablement-009">Vai trò của AsyncAnnotationBeanPostProcessor</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của `AsyncAnnotationBeanPostProcessor` trong Spring Framework

## Mục tiêu

Viết `@Async` trên một phương thức **chưa đủ** để việc thực thi tự chuyển sang thread khác. Spring cần phát hiện annotation trên bean được quản lý, tạo hoặc bổ sung AOP proxy và gắn logic chuyển lời gọi vào executor. Trong chế độ mặc định của `@EnableAsync`, hạ tầng giữ vai trò then chốt ở bước này là **`org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor`**.

Bài học giải thích chính xác processor làm việc **khi nào**, nó tạo ra **thành phần gì**, và phân biệt trách nhiệm của nó với phần thực sự **gửi và chạy task**. Sau khi học, bạn có thể:

1. Mô tả đường đi từ `@EnableAsync` tới việc đăng ký `AsyncAnnotationBeanPostProcessor`.
2. Giải thích vì sao `BeanPostProcessor` thêm `AsyncAnnotationAdvisor` vào proxy cho bean có `@Async`.
3. Phân biệt bước xử lý bean khi khởi tạo với bước interception khi phương thức được gọi.
4. Lần theo việc chọn executor, tạo `Future`/`CompletableFuture`, xử lý `void` và exception.
5. Biết giới hạn của proxy mode: self-invocation, object tạo bằng `new`, method không thể được proxy, kiểu proxy JDK/CGLIB và phạm vi application context.
6. Tự chạy ví dụ Java 17+ xác nhận infrastructure processor đã đăng ký, service là AOP proxy và logic chạy trong executor được chọn.

**Một câu cần nhớ:** `AsyncAnnotationBeanPostProcessor` **thiết lập đường đi bất đồng bộ cho bean Spring**; nó **không phải thread pool** và không tự thực thi từng task. Khi ứng dụng gọi phương thức qua proxy, interceptor/advice mới gửi lời gọi tới executor.

## 1. Ba thời điểm dễ bị trộn lẫn

Để hiểu `@Async`, hãy tách ba giai đoạn:

| Thời điểm | Thành phần tham gia | Việc xảy ra |
| --- | --- | --- |
| **Khai báo cấu hình** | `@Configuration`, `@EnableAsync`, `AsyncConfigurationSelector`, `ProxyAsyncConfiguration` | Spring import cấu hình hạ tầng async và đăng ký bean processor trong context áp dụng. |
| **Khởi tạo bean** | `AsyncAnnotationBeanPostProcessor`, `AsyncAnnotationAdvisor`, AOP proxy infrastructure | Khi bean Spring được xử lý, processor gắn advisor async vào proxy có sẵn hoặc tạo proxy nếu bean có annotation phù hợp. |
| **Lúc phương thức được gọi** | Caller, AOP proxy, async interceptor, `Executor`/`TaskExecutor`, worker | Interceptor nhận lời gọi qua proxy, xác định executor, gửi công việc; worker mới thực thi thân phương thức và xử lý kết quả/lỗi. |

Có thể hình dung:

```text
          KHỞI TẠO APPLICATION CONTEXT
@Configuration + @EnableAsync
       |
       v
AsyncConfigurationSelector (proxy mode)
       |
       v
ProxyAsyncConfiguration đăng ký AsyncAnnotationBeanPostProcessor
       |
       v
Bean được tạo -> BeanPostProcessor kiểm tra @Async
       |
       v
Bean proxy có AsyncAnnotationAdvisor/async advice
       |
       +----------- CHỜ LỜI GỌI THỰC TẾ ------------+
                                                     |
             LÚC CHẠY                               v
Caller -> service proxy -> async interceptor -> chọn executor
                                        |
                                        v
                                 submit invocation
                                        |
                                        v
                                worker chạy method
                                        |
                                        v
                              value / error / completion
```

Processor không tự lập lịch định kỳ, không gọi method ngay khi context khởi động và không mặc định tạo một thread cho mỗi bean. Công việc chỉ được gửi khi có lời gọi phù hợp đi qua proxy.

## 2. `@EnableAsync` đăng ký processor như thế nào?

Trong Spring Framework, `@EnableAsync` kích hoạt hỗ trợ annotation-driven asynchronous method execution cho **application context nơi nó được khai báo**.

Cấu hình tối thiểu:

```java
@Configuration
@EnableAsync
public class AsyncConfiguration {
}
```

Đây là đoạn cấu hình trong một dự án Spring, chưa phải chương trình độc lập. Ở `AdviceMode.PROXY` (mặc định), `@EnableAsync` import `AsyncConfigurationSelector`; selector chọn cấu hình hạ tầng `ProxyAsyncConfiguration`, từ đó đăng ký một `AsyncAnnotationBeanPostProcessor`.

Trong cơ chế này, processor có một bean name **nội bộ** dùng bởi Spring, tương ứng hằng số trong `TaskManagementConfigUtils`. Tên nội bộ hữu ích để debug container, nhưng mã nghiệp vụ không nên phụ thuộc vào tên hạ tầng nếu chỉ cần `@Async`.

Điều kiện cần nhớ:

- `@EnableAsync` cần được thực thi qua cấu hình trong **đúng application context**. Nếu hệ thống có root context và nhiều servlet child context, kích hoạt ở một context không đồng nghĩa các context khác được bật.
- Nếu dùng Spring Boot, auto-configuration cụ thể có thể khác nhau theo dự án; không suy đoán chỉ vì có dependency `spring-context` hoặc annotation trên service thì async đã được bật.
- `@EnableAsync` **không tạo nghiệp vụ** và không biến mọi method thành async. Bean có phương thức được annotation phù hợp mới có advisor tương ứng.
- `@EnableAsync(mode = AdviceMode.ASPECTJ)` chọn hướng xử lý khác, cần `spring-aspects` và weaving; lúc đó cách chặn lời gọi không dựa trên AOP proxy do bean post-processor ở chế độ proxy tạo ra. Không dùng cách kiểm chứng `AopUtils.isAopProxy` làm điều kiện bắt buộc cho AspectJ mode.

Hai tên gần nhau nhưng nhiệm vụ khác:

| Thành phần | Vai trò |
| --- | --- |
| `@EnableAsync` | Bật và cấu hình cơ chế annotation-driven async. |
| `AsyncConfigurationSelector` / `ProxyAsyncConfiguration` | Chọn và đăng ký các bean hạ tầng khi ở proxy mode. |
| **`AsyncAnnotationBeanPostProcessor`** | Phát hiện async annotation trên Spring bean và bổ sung advisor/proxy cho đường gọi. |
| `AsyncAnnotationAdvisor` | Tập hợp pointcut xác định lời gọi cần chặn và advice thực hiện logic async. |
| `AnnotationAsyncExecutionInterceptor` | Advice/interceptor giải quyết executor theo annotation và gửi lời gọi để thực thi. |
| `Executor` / `TaskExecutor` | Thực sự thực hiện hoặc phân phối task đến worker theo chính sách cụ thể. |

Tên lớp và cách chia trách nhiệm ở bảng này dựa trên hạ tầng Spring Framework 6.2; đây là kiến thức để đọc luồng thực thi, không phải yêu cầu mọi ứng dụng phải tự tạo các lớp infrastructure.

## 3. BeanPostProcessor làm gì khi bean được khởi tạo?

`BeanPostProcessor` là extension point của Spring IoC container cho phép xử lý bean trong quá trình container tạo và cấu hình chúng. `AsyncAnnotationBeanPostProcessor` mở rộng hạ tầng AOP post-processing, vì vậy công việc chính là **trang bị proxy/advisor**, không phải chạy method nghiệp vụ.

Với một bean như sau:

```java
@Service
public class EmailService {
    @Async("mailExecutor")
    public CompletableFuture<String> send(String address) {
        return CompletableFuture.completedFuture("accepted:" + address);
    }
}
```

Đây là **ví dụ một lớp** trong ứng dụng đã có `@EnableAsync`, `@Service` scanning và bean `mailExecutor`; không phải Java file chạy độc lập.

Trước khi ứng dụng gọi `send`, Spring có thể đã:

1. Tạo instance gốc của `EmailService` theo định nghĩa bean.
2. Đưa bean qua chuỗi post-processors trong giai đoạn khởi tạo container.
3. Để `AsyncAnnotationBeanPostProcessor` tạo một `AsyncAnnotationAdvisor` phù hợp với annotation trên lớp hoặc phương thức.
4. Bổ sung advisor vào AOP proxy hiện có hoặc tạo proxy cho bean đủ điều kiện; đối tượng ứng dụng inject thường là **điểm tham chiếu proxy**.
5. Trả bean đã xử lý về nơi inject hoặc `ApplicationContext.getBean(...)`.

Trong những trường hợp đơn giản, `EmailService` không khai báo interface nên Spring có thể dùng class-based proxy. Nếu nó triển khai interface và cấu hình mặc định ưa JDK proxy, object được trả có thể chỉ expose interface của service. Vì vậy khi debug, đừng giả định lúc nào `getBean(ConcreteClass.class)` cũng hợp lệ trong ứng dụng dùng JDK dynamic proxies.

Việc **chỉ có annotation trong source** chưa đảm bảo lời gọi được chặn. Annotation cần được hạ tầng Spring nhận ra trên một bean thực sự được quản lý, với method đủ điều kiện để proxy/intercept.

### Processor có sửa code phương thức gốc không?

Không. Trong proxy mode, processor không viết lại bytecode của method nghiệp vụ. Nó xây **đường gọi trung gian**: client gọi proxy, advisor/interceptor xử lý rồi chuyển việc. Đối tượng đích vẫn chứa logic gốc.

Mô hình ngắn:

```text
Client
  |
  v
Proxy (được bổ sung advisor lúc bean initialization)
  |
  +--- advice xử lý async: lập task và gửi executor
                                  |
                                  v
                           Target.method(...)
```

Tách biệt này giải thích vì sao lệnh `this.send(...)` trong cùng target không chạy lại qua proxy.

## 4. `AsyncAnnotationAdvisor` và interceptor: cầu nối tới lúc chạy

`AsyncAnnotationAdvisor` kết hợp hai yếu tố AOP:

- **Pointcut**: quyết định bean/method nào phù hợp với `@Async` hoặc annotation async tùy chỉnh.
- **Advice**: logic thực thi khi lời gọi phù hợp đi qua proxy, gồm xác định executor, tạo task và chuyển công việc.

Spring có `AnnotationAsyncExecutionInterceptor`, một specialization của `AsyncExecutionInterceptor`, để đọc qualifier của `@Async` và xác định executor thích hợp.

Ví dụ:

```java
@Async("reportExecutor")
public CompletableFuture<Report> renderReport(String id) {
    // Phần xử lý thực tế thực thi trong executor được chọn.
}
```

Đây là phần khai báo method minh họa, không phải chương trình Java độc lập; `Report` và thân xử lý do ứng dụng cung cấp.

Khi caller gọi method qua proxy, đường xử lý thường gồm:

1. Interceptor nhận `MethodInvocation` thay vì gọi ngay target.
2. Từ annotation, chọn `reportExecutor` hoặc cơ chế executor mặc định được cấu hình.
3. Tạo task chứa phần lời gọi target.
4. Gửi task đến executor; caller nhận handle trả về theo hợp đồng async.
5. Worker chạy method thật, rồi hoàn tất kết quả hoặc nắm lỗi để caller/handler quan sát.

**Processor không trực tiếp chọn thread mỗi lần**; việc chọn pool có thể được cấu hình qua `AsyncConfigurer`, tên bean từ `@Async` hoặc mặc định, còn chính executor quyết định worker, queue và chính sách từ chối.

Nếu dùng `Executor` thực thi đồng bộ như `Runnable::run`, hoặc policy `CallerRunsPolicy` khi pool bão hòa, một lời gọi có thể chạy trên caller dù đi qua async proxy. Do đó **proxy có advisor** và **thực thi trên thread khác** là hai khẳng định phải kiểm chứng riêng.

## 5. Ví dụ chạy được: kiểm chứng processor, proxy và worker

Ví dụ sau có thể chạy với **JDK 17+**, Maven và **Spring Framework 6.2.12**. Nó xác minh ba tầng khác nhau:

1. Spring đã đăng ký `AsyncAnnotationBeanPostProcessor`.
2. Bean service lấy từ context thực sự là AOP proxy.
3. Lời gọi `@Async("lessonExecutor")` chạy thân method trong worker của executor có prefix `lesson-`, không phải thread caller.

Cấu trúc thư mục:

```text
async-bpp-demo/
├── pom.xml
└── src/main/java/demo/AsyncBppDemo.java
```

Tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>async-bpp-demo</artifactId>
    <version>1.0.0</version>

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
                <version>3.3.0</version>
                <configuration>
                    <mainClass>demo.AsyncBppDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tệp `src/main/java/demo/AsyncBppDemo.java`:

```java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class AsyncBppDemo {

    public record WorkResult(String value, Thread executedBy) {
    }

    // Lớp public, không final và method public không final để
    // class-based proxy có thể chặn invocation qua Spring bean.
    public static class WorkService {
        @Async("lessonExecutor")
        public CompletableFuture<WorkResult> process(String input) {
            return CompletableFuture.completedFuture(
                new WorkResult(
                    "done:" + input,
                    Thread.currentThread()
                )
            );
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class AppConfig {
        @Bean
        public WorkService workService() {
            return new WorkService();
        }

        @Bean("lessonExecutor")
        public ThreadPoolTaskExecutor lessonExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(4);
            executor.setThreadNamePrefix("lesson-");
            return executor;
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            // Chứng minh BPP hạ tầng do @EnableAsync đăng ký.
            boolean processorRegistered =
                context.getBeanNamesForType(
                    AsyncAnnotationBeanPostProcessor.class).length > 0;

            WorkService service = context.getBean(WorkService.class);
            boolean proxied = AopUtils.isAopProxy(service);
            Thread caller = Thread.currentThread();

            CompletableFuture<WorkResult> handle = service.process("A");
            WorkResult result = handle.get(5, TimeUnit.SECONDS);

            System.out.println("processorRegistered=" + processorRegistered);
            System.out.println("serviceProxied=" + proxied);
            System.out.println("differentThread="
                + (result.executedBy() != caller));
            System.out.println("namedExecutorWorker="
                + result.executedBy().getName().startsWith("lesson-"));
            System.out.println("value=" + result.value());
        }
    }
}
```

Chạy từ thư mục `async-bpp-demo` với JDK 17+ và Maven:

```bash
mvn -q compile exec:java
```

Năm dòng **do chương trình in ra** khi chạy bình thường là:

```text
processorRegistered=true
serviceProxied=true
differentThread=true
namedExecutorWorker=true
value=done:A
```

### Vì sao có thể dự đoán kết quả?

- `@EnableAsync` nằm trên `AppConfig`, vốn được đăng ký trong `AnnotationConfigApplicationContext`. Spring đăng ký BPP async khi khởi động context.
- Bean `WorkService` được tạo bằng `@Bean` và có method public `@Async`. Hạ tầng BPP bổ sung advisor và tạo proxy (class-based do service này không có interface).
- `AopUtils.isAopProxy(service)` quan sát object thực sự được trả cho caller; đó là proxy.
- Caller gọi `service.process("A")` qua proxy, interceptor gửi công việc tới bean `lessonExecutor`.
- `ThreadPoolTaskExecutor` trong ví dụ dùng worker có tên bắt đầu `lesson-` và không dùng `CallerRunsPolicy`; do vậy worker chạy method khác thread caller.
- `WorkResult` chỉ lưu tham chiếu tới `Thread` để phục vụ kiểm chứng trong bài. Trong code nghiệp vụ thật, kết quả nên là dữ liệu domain, không trả `Thread` cho client.
- `handle.get(5, SECONDS)` đợi nếu chưa hoàn tất rồi trả kết quả, nên năm dòng được in theo thứ tự cố định. Đây là **timeout ở caller khi đọc kết quả**, không phải giới hạn thời gian chạy do BPP tự áp đặt.

Nếu môi trường không giải quyết được dependency, executor bị đóng trước khi nhận task hoặc quá hạn 5 giây, chương trình có thể báo lỗi thay vì in đúng năm dòng. Output trên mô tả trường hợp chạy thành công, không phải cam kết về thời gian scheduler.

### Nếu bỏ `@EnableAsync` thì sao?

Với ứng dụng chỉ có cấu hình tự khai báo như ví dụ và không có cơ chế async khác, Spring sẽ không đăng ký BPP qua `@EnableAsync`; phương thức có annotation đơn thuần là một method Java thường. Caller có thể trực tiếp thực thi thân `process` và nhận `CompletableFuture.completedFuture` đã hoàn tất trên caller. Chỉ nhìn thấy kiểu trả về `CompletableFuture` chưa đủ kết luận đã có xử lý async.

## 6. Những ranh giới của proxy mode

### A. Self-invocation: gọi `this.method()` bỏ qua proxy

Ví dụ minh họa:

```java
@Service
class InvoiceService {
    public CompletableFuture<String> generate() {
        // this nằm ở target object, không đi qua proxy async.
        return sendAsync();
    }

    @Async("invoiceExecutor")
    public CompletableFuture<String> sendAsync() {
        return CompletableFuture.completedFuture("sent");
    }
}
```

Đoạn này là một lớp minh họa trong ứng dụng có bean `invoiceExecutor` và `@EnableAsync`, không phải chương trình độc lập. Gọi `generate()` từ ngoài qua proxy sẽ vào target và target tự gọi `sendAsync()` bằng `this`; **lời gọi nội bộ** không đi qua interceptor, dù `sendAsync` mang `@Async`.

Cách giải quyết thường là tách `sendAsync()` vào một Spring bean khác và inject bean đó, rồi gọi qua bean reference. Cần tránh tự inject proxy vào chính mình chỉ để né self-invocation nếu có thể tổ chức lại ranh giới service rõ ràng hơn.

### B. Object tự `new` không qua BeanPostProcessor

```java
WorkService direct = new WorkService();
direct.process("A");
```

Đoạn trên giả định sử dụng class `WorkService` của ví dụ. Object `direct` được tạo bằng `new` không được `ApplicationContext` xử lý, nên BPP không gắn AOP proxy cho nó. Method chạy trực tiếp trên caller và `@Async` không tác động đến lời gọi. Lấy object qua `context.getBean(...)` hoặc inject Spring bean đúng cách mới cho cơ hội áp dụng proxy.

### C. Proxy loại nào và method nào chặn được?

| Tình huống | Điều cần nhớ |
| --- | --- |
| Bean triển khai interface, mặc định proxy theo interface | JDK dynamic proxy expose interface; client nên inject/call qua interface hợp lệ. |
| Bean không có interface phù hợp hoặc ép `proxyTargetClass=true` | Có thể dùng class-based proxy, yêu cầu class/method đủ điều kiện subclass/override. |
| `private` method | Không thể được gọi bên ngoài qua proxy theo cách annotation AOP mong đợi. |
| `final` method trên class-based proxy | Không thể override để chặn; annotation không biến nó thành async. |
| `final` class cần class-based proxy | Không thể subclass thông thường, có thể gặp lỗi tạo proxy. |
| Constructor, gọi nội bộ trong lifecycle setup hoặc method `@PostConstruct` | Không phải các điểm gọi qua proxy ứng dụng sau khi bean đã sẵn sàng; đừng kỳ vọng annotation tự chuyển sang executor. |
| `@Async` trên phương thức khai báo trong `@Configuration` class | Không phải vị trí sử dụng được Spring khuyến nghị/hỗ trợ. Nên đặt nghiệp vụ async ở service bean. |

`@EnableAsync(proxyTargetClass = true)` buộc class-based proxy ở proxy mode, nhưng cũng có thể ảnh hưởng các bean AOP khác trong cùng context. Thay đổi proxy mode cần cân nhắc tương thích kiểu inject/interface, đặc biệt khi hệ thống đã có `@Transactional`, security hoặc caching.

### D. Scope application context

`@EnableAsync` áp dụng trong context tương ứng. Một root context và một servlet child context có thể có bean scopes/proxy infrastructure riêng. Kiểm tra context thực sự quản lý service, thay vì chỉ thấy annotation được khai báo ở một cấu hình khác.

### E. Thực thi đồng bộ từ một executor đặc biệt

Dù `AopUtils.isAopProxy(service) == true`, việc chạy trên worker khác caller vẫn phụ thuộc executor. Một executor chạy trực tiếp như `Runnable::run` có thể xử lý task ngay trên caller. Với `CallerRunsPolicy`, pool bị bão hòa cũng có thể ép caller tự chạy task. Sự tồn tại của BPP/proxy **không chứng minh** mọi lời gọi đều tạo sự song song thực tế.

## 7. Processor không phải nơi quyết định mọi cấu hình async

### Chọn executor theo method hoặc mặc định

Trong code nghiệp vụ, `@Async("mailExecutor")` và `@Async("reportExecutor")` thường làm rõ pool phục vụ từng loại tải. Interceptor dùng qualifier này khi tìm executor tương ứng. Nếu tên bean không tồn tại, lỗi có thể phát sinh ở lúc invocation.

`AsyncConfigurer` là một giao diện cấu hình được Spring hỗ trợ để cung cấp executor mặc định và `AsyncUncaughtExceptionHandler`. Khi không có qualifier, Spring áp dụng quy tắc tìm executor theo cấu hình hiện hành: ví dụ một bean `TaskExecutor` phù hợp, bean `Executor` tên `taskExecutor`, hoặc fallback theo phiên bản/điều kiện. **Không nên phụ thuộc vào tên thread hay pool mặc định nếu chưa xác nhận bean nào được chọn.**

`AsyncAnnotationBeanPostProcessor` có các điểm cấu hình như `setExecutor(...)`, `setExceptionHandler(...)` và `setAsyncAnnotationType(...)`; chúng dành cho trường hợp tùy biến hạ tầng. Trong ứng dụng thông thường, dùng `@EnableAsync`, cấu hình `AsyncConfigurer` hoặc khai báo executor bean rõ ràng sẽ đơn giản và dễ kiểm soát hơn việc tạo một processor thứ hai thủ công.

### `@EnableAsync` có hỗ trợ annotation khác không?

Theo cấu hình Spring, mặc định có thể nhận `@Async` của Spring, annotation async của Jakarta/EJB khi dependency thích hợp hiện diện, hoặc một loại annotation tùy chỉnh được cấu hình qua thuộc tính `annotation` của `@EnableAsync`. Việc thêm custom annotation chỉ có ích khi bộ advisor thực sự được cấu hình để nhận annotation đó; không phải một cơ chế tùy ý bắt mọi annotation.

Ví dụ cú pháp cấu hình:

```java
@EnableAsync(annotation = MyAsync.class)
@Configuration
class CustomAsyncConfig {
}
```

Đây là **mã cấu hình minh họa**; cần khai báo `@interface MyAsync`, import và các điều kiện runtime phù hợp. Đừng giả định annotation mới tự tương đương hoàn toàn `@Async("executorName")` về cách diễn giải qualifier.

### Thứ tự xử lý với AOP khác

Bean async có thể đồng thời có advice về transaction, cache, authorization, tracing. Theo javadoc, `AsyncAnnotationBeanPostProcessor` được sắp xếp mặc định ở cuối chuỗi BeanPostProcessor để có thể bổ sung async advisor lên proxy đã có; advisor async thường được đặt trước các advisor hiện hữu nhằm chuyển việc sang executor sớm.

Điều đó **không thay thế việc kiểm tra advice order thực tế** khi có nhiều proxy hoặc cấu hình tùy chỉnh. Đặc biệt, một transaction gắn với thread caller **không tự chuyển sang worker**. Với nghiệp vụ cần transaction bên trong task, xác định ranh giới giao dịch tại phía thực thi, lựa chọn proxy/advisor và thiết kế failure handling phù hợp; không chỉ đặt cả `@Transactional` và `@Async` rồi suy ra chắc chắn thứ tự.

## 8. Processor liên quan gì đến kết quả và ngoại lệ?

`AsyncAnnotationBeanPostProcessor` trang bị advisor/proxy **khi khởi tạo bean**; nó không hoàn tất công việc thay cho executor. **Khi method được gọi**, interceptor lựa chọn executor, nộp invocation và xử lý kết quả theo kiểu trả về:

| Loại kết quả/tình huống | Điểm quan sát thực tế |
| --- | --- |
| `CompletableFuture<T>` hoặc `Future<T>` | Handle từ proxy, completion/exception được caller quan sát |
| `@Async void` ném lỗi trong worker | `AsyncUncaughtExceptionHandler`, log/metric |
| Executor không tồn tại hoặc từ chối task | Lỗi ở đường phân giải/submit, có thể trước khi caller nhận Future |
| Caller timeout hoặc cancel | Không tự bảo đảm worker dừng hoặc side effect rollback |

Ví dụ rút gọn về API trong bean service: `@Async("lessonExecutor") public CompletableFuture<String> compute()` có thể trả `CompletableFuture.completedFuture("ok")` **trong worker**; caller nhận handle mà proxy cung cấp. Không suy ra phương thức đã chạy ngay trên caller chỉ vì target tạo completed future. Để học chi tiết `get/join`, `thenCompose`, cancellation hay đường lỗi, xem hợp đồng của các kiểu trả về; mục tiêu ở đây là phân biệt trách nhiệm processor với interceptor và executor.

## 9. So sánh với `ScheduledAnnotationBeanPostProcessor`

Hai lớp có tên tương tự, nhưng cơ chế kích hoạt khác nhau:

| | `AsyncAnnotationBeanPostProcessor` | `ScheduledAnnotationBeanPostProcessor` |
| --- | --- | --- |
| Annotation điển hình | `@Async` | `@Scheduled` |
| Bật theo cơ chế thông thường | `@EnableAsync` | `@EnableScheduling` |
| Mục đích | Gắn interceptor/proxy để chuyển **lời gọi phương thức do caller khởi phát** sang executor. | Phát hiện method theo lịch và **đăng ký tác vụ theo lịch** với scheduler. |
| Khi nào công việc bắt đầu? | Khi một caller gọi qua proxy được áp dụng advisor. | Khi điều kiện lịch fixed rate/fixed delay/cron đến hạn. |
| Có tham số runtime từ caller? | Có, caller gọi method với argument bình thường. | Method scheduled thường được gọi theo lịch, không nhận argument động từ caller theo cách đó. |
| Thành phần thực thi | `Executor`/`TaskExecutor` được chọn. | `TaskScheduler` và cấu hình scheduling phù hợp. |

Vì vậy câu “Spring có BeanPostProcessor nên chắc nó quét và tự chạy mọi method async” là sai. Processor async trang bị **đường chặn lời gọi**, còn scheduler chủ động đăng ký **thời điểm phát sinh công việc**.

## 10. Quy trình chẩn đoán `@Async` không hoạt động

Thực hiện theo thứ tự để xác định lỗi nằm ở đâu:

1. **Bean có do Spring tạo không?** Kiểm tra `ApplicationContext` chứa bean và ứng dụng gọi reference được inject/get từ context; tránh đối tượng tự `new`.
2. **Context đó bật async chưa?** Kiểm tra `@EnableAsync` ở cấu hình áp dụng; trong proxy mode có thể kiểm tra sự hiện diện của bean `AsyncAnnotationBeanPostProcessor`.
3. **Có proxy/advisor không?** Dùng `AopUtils.isAopProxy(bean)` trong proxy mode và xem kiểu proxy. Nếu service có interface/JDK proxy, kiểm tra cách inject theo interface.
4. **Method có thể intercept không?** Kiểm tra `public`, không `final` với class proxy, đúng annotation ở class/method, không gọi qua `this`.
5. **Executor nào thực sự được chọn?** Kiểm tra `@Async("...")`, tên bean, `AsyncConfigurer`, `threadNamePrefix` và loại executor. Coi chừng một direct executor hoặc `CallerRunsPolicy`.
6. **Lời gọi có qua proxy thật không?** Log caller thread ở chỗ gửi, worker thread trong method; so sánh tên/ID. Không dựa duy nhất vào việc method trả `CompletableFuture`.
7. **Có xảy ra rejection hoặc lỗi bên trong task không?** Kiểm tra exception khi submit, `Future` completion, `AsyncUncaughtExceptionHandler`, queue depth và shutdown.
8. **Caller có tự chặn sau submit không?** Nếu gọi `get()`/`join()` ngay, nhìn bên ngoài vẫn có vẻ tuần tự dù method thực sự chạy trên worker.
9. **Có phụ thuộc context từ caller không?** `ThreadLocal`, MDC, security context, request scope và transaction không tự đi theo worker. Dùng propagation có chủ đích và cleanup trong `finally`.
10. **Có nhiều context/auto-proxy creators không?** Kiểm tra thứ tự AOP, proxy chain, giao dịch và bean lifecycle trong context thực tế.

Một phép kiểm tra tốt phải trả lời ba câu riêng: **processor đã đăng ký chưa, service có proxy chưa và method đang thực thi trên executor nào**. Chỉ một trong ba điều đúng chưa đủ kết luận mọi đường gọi đang async đúng.

## 11. Các lỗi hiểu sai thường gặp

| Nhận định sai | Vì sao sai | Cách sửa |
| --- | --- | --- |
| “Đặt `@Async` là JVM tự mở thread.” | Annotation không có runtime interceptor khi thiếu cấu hình/bean phù hợp. | Bật `@EnableAsync` và gọi Spring-managed bean qua proxy. |
| “`AsyncAnnotationBeanPostProcessor` là thread pool.” | Nó bổ sung AOP advisor/proxy, còn executor mới nhận task. | Phân biệt initialization và invocation. |
| “Có `@EnableAsync` thì mọi method đều bất đồng bộ.” | Chỉ các lời gọi phù hợp pointcut và qua interception mới được xử lý. | Kiểm tra annotation và proxy boundary. |
| “Processor chạy method async khi bean được tạo.” | Bean post-processing chỉ xây infrastructure. | Task chạy khi caller gọi qua proxy. |
| “Self-invocation sẽ tự chuyển worker.” | `this` gọi trực tiếp target, không qua proxy trong mode mặc định. | Tách method sang bean khác hoặc thiết kế ranh giới AOP phù hợp. |
| “Object `new` có `@Async` vẫn async.” | Không có post-processing của Spring cho object ấy. | Lấy bean qua context/injection. |
| “BPP đã đăng ký thì chắc mọi lời gọi chạy thread khác.” | Executor trực tiếp hoặc CallerRuns có thể dùng caller. | Kiểm tra delegate, overload, log thread và kết quả. |
| “`CompletableFuture` chắc chắn là async.” | Có thể là `completedFuture` được tạo ngay trên caller. | Quan sát proxy/executor thay vì suy từ return type. |
| “`@Async` giữ transaction từ request thread.” | Transaction context thường gắn thread. | Thiết kế transaction trong worker, kiểm tra advice order. |
| “`@Scheduled` và `@Async` cùng một processor.” | Scheduler và async advisor có processor và cơ chế khác nhau. | Phân biệt `ScheduledAnnotationBeanPostProcessor` và async BPP. |
| “Dùng `@Async void` sẽ báo exception tại dòng gọi.” | Lỗi xảy ra ở worker, caller không có future. | Cấu hình uncaught exception handler hoặc trả completion handle. |
| “Thêm nhiều processor thủ công sẽ giúp chạy nhanh hơn.” | Có thể làm proxy/advisor trùng, khó kiểm soát infrastructure. | Dùng một cấu hình `@EnableAsync` chuẩn và executor sizing đúng. |

## 12. Bài tập tự kiểm tra

**Câu 1.** Trong proxy mode, ai chịu trách nhiệm nhận diện `@Async` trên bean và gắn advice tương ứng?

**Đáp án:** `AsyncAnnotationBeanPostProcessor` áp dụng `AsyncAnnotationAdvisor` vào proxy của bean phù hợp. Executor chỉ được sử dụng khi có lời gọi bị interceptor xử lý.

**Câu 2.** Vì sao một bean có `@Async` nhưng method vẫn chạy trên caller khi tạo bằng `new`?

**Đáp án:** Object không được IoC container post-process, không có AOP proxy, nên annotation không tạo hiệu ứng đối với lời gọi Java trực tiếp.

**Câu 3.** `@EnableAsync` làm gì, và nó có bắt đầu chạy tất cả các phương thức `@Async` lúc startup không?

**Đáp án:** Nó đăng ký hạ tầng async cho context. Processor chuẩn bị proxy/advisor; method chỉ thực thi khi được gọi qua đường interception sau đó.

**Câu 4.** `this.sendAsync()` bên trong service có proxy có chạy qua async advice không?

**Đáp án:** Trong proxy mode mặc định, không. Đây là self-invocation trên target; cần gọi qua Spring proxy từ bean khác hoặc cơ chế interception phù hợp.

**Câu 5.** Một service được AOP proxy và `@Async("lessonExecutor")`, nhưng task vẫn chạy ngay trên caller. BPP có chắc lỗi không?

**Đáp án:** Chưa chắc. Delegate executor có thể thực thi trực tiếp, hoặc saturation policy `CallerRunsPolicy` chuyển việc sang caller. Phải kiểm tra executor và đường invocation.

**Câu 6.** Vì sao trong ví dụ có thể nhận cả `processorRegistered=true` và `serviceProxied=true`?

**Đáp án:** `@EnableAsync` đăng ký processor, rồi processor tạo/bổ sung AOP proxy cho `WorkService` có method `@Async`. Hai giá trị kiểm tra hai giai đoạn khác nhau.

**Câu 7.** Có thể lấy exception ném từ `@Async void` bằng `Future.get()` không?

**Đáp án:** Không, vì method `void` không trả handle. Cần `AsyncUncaughtExceptionHandler` hoặc thay hợp đồng method thành `CompletableFuture<T>` khi muốn caller theo dõi kết quả.

**Câu 8.** Một ứng dụng chọn `@EnableAsync(mode = AdviceMode.ASPECTJ)`. `AopUtils.isAopProxy(service)` trả `false` có đủ kết luận async chưa bật không?

**Đáp án:** Không. AspectJ weaving không dựa trên AOP proxy tương tự proxy mode; cần kiểm tra weaving, dependency và hành vi thực tế.

**Câu 9.** Annotation `@Scheduled` trên bean có được chính `AsyncAnnotationBeanPostProcessor` lập lịch không?

**Đáp án:** Không. Lập lịch có hạ tầng riêng, điển hình là `ScheduledAnnotationBeanPostProcessor` và `TaskScheduler`.

### Ghi nhớ

Hãy lần theo **cấu hình → bean post-processing → AOP proxy/advisor → method invocation → executor → completion**. `AsyncAnnotationBeanPostProcessor` thuộc phần **tạo đường chặn lời gọi cho Spring bean**; async interceptor xử lý lời gọi và giao công việc cho executor. Khi debug, xác minh từng mắt xích thay vì suy luận rằng chỉ cần có annotation là task chắc chắn chạy song song.

### Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): bật `@EnableAsync`, proxy mode, `@Async` và executor.
- [Spring Framework — AsyncAnnotationBeanPostProcessor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/AsyncAnnotationBeanPostProcessor.html): processor, advisor, annotation và exception handler.
- [Spring Framework — EnableAsync Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html): cách bật, scope theo context, proxy/AspectJ và thứ tự.
- [Spring Framework — AsyncAnnotationAdvisor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/AsyncAnnotationAdvisor.html): pointcut/advice cho async method.
- [Spring Framework — AnnotationAsyncExecutionInterceptor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/AnnotationAsyncExecutionInterceptor.html): chọn executor theo annotation và giao lời gọi.


</details>

- [Quay lại đầu trang](#back-to-top)
