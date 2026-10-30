<a id="back-to-top"></a>

# @Scheduled

## Menu
- [Bật @EnableScheduling và điều kiện bean được phát hiện](#scheduled-tasks-001)
- [Phương thức lập lịch không nhận đối số](#scheduled-tasks-002)
- [fixedDelay đo khoảng cách từ lần chạy trước hoàn tất](#scheduled-tasks-003)
- [fixedRate theo nhịp lịch và hành vi khi công việc chạy lâu](#scheduled-tasks-004)
- [initialDelay và đơn vị timeUnit](#scheduled-tasks-005)
- [Chạy một lần bằng initialDelay trên phiên bản Spring hỗ trợ](#scheduled-tasks-006)
- [cron và zone cho lịch theo thời gian địa phương](#scheduled-tasks-007)
- [Giá trị trả về thông thường bị bỏ qua và khác biệt với phương thức reactive](#scheduled-tasks-008)
- [Annotation lặp lại tạo nhiều lịch độc lập trên cùng phương thức](#scheduled-tasks-009)
- [Nhiều thể hiện bean có thể đăng ký nhiều lịch](#scheduled-tasks-010)
- [Đưa chu kỳ ra cấu hình và dùng giá trị cron vô hiệu hóa](#scheduled-tasks-011)
- [Phối hợp @Scheduled với @Async làm thay đổi thời điểm được coi là đã hoàn tất](#scheduled-tasks-012)

## <a id="scheduled-tasks-001">Bật @EnableScheduling và điều kiện bean được phát hiện</a>

<details>
<summary>Xem chi tiết</summary>

# Bật @EnableScheduling và điều kiện bean được phát hiện trong Spring Framework

Một method Java được gắn `@Scheduled(fixedRate = 5000)` **chưa tự động trở thành một công việc chạy định kỳ**. Annotation là metadata; phải có cơ chế của Spring đọc metadata và đăng ký lịch thực thi. Đồng thời, đối tượng chứa method phải được Spring quản lý và được xử lý trong **ApplicationContext đã bật scheduling**. Nếu thiếu một trong hai điều kiện này, method có thể không bao giờ được gọi theo lịch, dù source code trông hoàn toàn đúng.

Bài học thuộc nhóm **@Scheduled** của module **Spring Framework – concurrency**, tập trung vào hai câu hỏi: **bật `@EnableScheduling` ở đâu**, và **bean nào thực sự được phát hiện để đăng ký phương thức `@Scheduled`**. Nội dung giải thích scheduling infrastructure, component scanning, `@Bean`, package boundaries, `@Profile`, `@ConditionalOnProperty`, lazy initialization, parent/child context, method signature, ví dụ Spring Boot 3.x và cách kiểm thử có bằng chứng.

## 1. Annotation không thay thế việc đăng ký lịch

Ví dụ:

```java
import org.springframework.scheduling.annotation.Scheduled;

public class CleanupJob {
    @Scheduled(fixedRate = 5000)
    public void clean() {
        System.out.println("Cleaning...");
    }
}
```

Nếu code tạo `new CleanupJob()` trong một method bình thường, JVM **không tự mở timer** để gọi `clean()`. Lập trình viên cũng có thể gọi `new CleanupJob().clean()` một lần; đó chỉ là lời gọi Java thông thường, không tạo lịch định kỳ.

Trong Spring, cần hai phần độc lập:

1. **Bean registration:** container tạo và quản lý instance `CleanupJob`.
2. **Scheduling registration:** `ScheduledAnnotationBeanPostProcessor` đọc `@Scheduled` trên bean được xử lý và đăng ký một `Runnable` với scheduler.

Luồng tổng quát:

```text
@Configuration + @EnableScheduling
             |
             v
  SchedulingConfiguration
             |
             v
  ScheduledAnnotationBeanPostProcessor
             |
             | nhận các Spring-managed beans
             v
  phát hiện method @Scheduled
             |
             | fixedRate / fixedDelay / cron / initialDelay
             v
   ScheduledTaskRegistrar / TaskScheduler
             |
             | khi đến mốc phù hợp
             v
       scheduler worker gọi method
```

Sơ đồ mô tả quan hệ cơ chế, **không phải** biểu đồ thứ tự từng callback nội bộ của mọi phiên bản Spring. Điều thiết yếu là **không có bean thì processor không có instance để xử lý; không có processor thì annotation không tự tạo lịch**.

| Thành phần | Chịu trách nhiệm | Có tự làm nhiệm vụ còn lại không? |
| --- | --- | --- |
| `@ComponentScan` / `@Component` / `@Bean` | Đăng ký bean | Không tự kích hoạt scheduling processor |
| `@EnableScheduling` | Bật annotation processing cho scheduling | Không tự scan toàn classpath và tạo mọi class job |
| `@Scheduled` | Khai báo lịch cho một method | Không tự tạo thread, bean hoặc timer |
| `TaskScheduler` | Thực thi/lập lịch `Runnable` | Không tự tìm class có `@Scheduled` nếu chưa có processor tương ứng |
| `@EnableAsync` | Bật xử lý `@Async` | Không thay thế `@EnableScheduling` |

## 2. @EnableScheduling kích hoạt hạ tầng nào?

Cấu hình tối thiểu:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
```

Theo Javadoc, `@EnableScheduling` import `SchedulingConfiguration`. Cấu hình đó đăng ký `ScheduledAnnotationBeanPostProcessor`, một bean post-processor có nhiệm vụ tìm `@Scheduled` trên các bean Spring được xử lý và đăng ký các task theo metadata của annotation.

Đối tượng quan trọng là **Spring-managed bean**; processor không đi vòng qua heap để tìm mọi instance Java mà code đã `new`.

### 2.1. Đặt annotation trong Spring Boot

Với Boot 3.x:

```java
@SpringBootApplication
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

Đây là fragment, cần imports Spring Boot và scheduling. `@SpringBootApplication` bao gồm cấu hình và cơ chế component scan. Đặt `@EnableScheduling` tại đây thường đủ khi job beans nằm trong **cùng context** và có thể được scan/đăng ký.

Cũng có thể đặt `@EnableScheduling` lên **một configuration class khác được context thực sự nạp**. Chỉ viết class `@Configuration @EnableScheduling` trong source mà để nó ngoài scan root, không `@Import` và không khai báo với context thì không giúp gì.

**Không cần đặt `@EnableScheduling` lên từng job class.** Một context đã bật scheduling có thể xử lý nhiều bean `@Scheduled`.

### 2.2. @EnableScheduling chỉ áp dụng cho local context

Javadoc `EnableScheduling` nhấn mạnh annotation có phạm vi **ApplicationContext cục bộ**. Nếu ứng dụng có root context và một child `DispatcherServlet` context riêng:

```text
Root ApplicationContext
   @EnableScheduling
   CleanupJob bean  ──> processor của root xử lý
        |
        +── Child WebApplicationContext
               ReportJob bean  ──> không tự được xử lý
                                   chỉ vì root đã bật
```

Nếu thật sự cần áp dụng cho cả hai cấp, phải bật scheduling trong **từng context cần xử lý**. Không suy diễn rằng bật ở root đồng nghĩa có processor trên mọi context khác trong JVM.

Một ứng dụng Spring Boot đơn giản thường chỉ dùng một application context chính, nhưng hệ thống MVC cũ hoặc cấu hình nhiều context có thể không như vậy.

### 2.3. @EnableAsync không phải @EnableScheduling

`@EnableAsync` kích hoạt interceptor/proxy cho `@Async`. `@EnableScheduling` kích hoạt registration cho `@Scheduled`. Có thể dùng cả hai nếu cần, nhưng không phụ thuộc lẫn nhau:

```java
@Configuration
@EnableScheduling
@EnableAsync
class ConcurrencyConfig {
}
```

Đây là fragment cần imports. Nếu ứng dụng chỉ cần lịch định kỳ, `@EnableAsync` không phải điều kiện. Nếu ứng dụng chỉ dùng `@Async`, `@EnableScheduling` cũng không bắt buộc.

## 3. Điều kiện quan trọng: bean phải được Spring quản lý

**“Được phát hiện” không nhất thiết nghĩa là có `@Component`.** Spring có nhiều cách tạo bean hợp lệ.

### 3.1. @Component và @Service

```java
package com.example.demo.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupJob {
    @Scheduled(fixedDelay = 5000)
    public void clean() {
        // Một lượt dọn dẹp.
    }
}
```

Nếu component scan bao phủ `com.example.demo.jobs`, Spring tạo bean. Với `@EnableScheduling` trong cùng context, processor có cơ hội đăng ký method. `@Service` cũng là stereotype được component scanning hỗ trợ.

### 3.2. @Bean cũng hợp lệ, không cần @Component trên class job

```java
public class ReportJob {
    @Scheduled(fixedRate = 10_000)
    public void run() {
        // Công việc định kỳ.
    }
}
```

Trong một configuration class đã được context nạp:

```java
@Configuration
class JobConfiguration {
    @Bean
    ReportJob reportJob() {
        return new ReportJob();
    }
}
```

Đây là **hai fragment có liên quan**, cần imports. `ReportJob` không có `@Component` nhưng instance do Spring gọi factory `@Bean` để đăng ký vẫn là **managed bean**. Khi bean được khởi tạo và scheduling processor đang hoạt động, `@Scheduled` có thể được phát hiện.

**Khác biệt:** code tự `new ReportJob()` ở nơi bất kỳ không giống instance được Spring tạo và quản lý thông qua `@Bean`.

### 3.3. @Import hoặc bean registration khác

```java
@Configuration
@EnableScheduling
@Import(JobConfiguration.class)
class RootConfig {
}
```

Đây là fragment. Nếu `JobConfiguration` không được component scan, `@Import` là một cách đưa nó vào context. Kết luận chính: **component scan là một đường đăng ký bean, không phải đường duy nhất**.

### 3.4. Tự new() một job không làm Spring quản lý nó

```java
CleanupJob job = new CleanupJob();
// Dòng này không khiến Spring tự đăng ký một lịch:
job.clean();
```

Đây chỉ là lời gọi method Java thông thường. Nếu muốn quản lý lịch cho object bên ngoài container, có thể đăng ký chủ động qua `TaskScheduler`; không kỳ vọng processor Spring theo dõi object tự tạo.

## 4. Vì sao package scan sai khiến @Scheduled không chạy?

`@SpringBootApplication` mặc định component scan từ package đặt application class và các package con.

Ví dụ **đúng phạm vi**:

```text
src/main/java/
  com/example/app/
    Application.java       @SpringBootApplication @EnableScheduling
    jobs/
      CleanupJob.java      @Component @Scheduled
    service/
      CleanupService.java  @Service
```

Application tại `com.example.app` thường scan `com.example.app.jobs`.

Ví dụ dễ bỏ sót:

```text
src/main/java/
  com/example/app/
    Application.java       @SpringBootApplication @EnableScheduling
  com/example/jobs/
    CleanupJob.java        @Component @Scheduled
```

`com.example.jobs` là sibling của `com.example.app`, **không phải package con**. `@Component` có thể không được scan; không có bean, không có lịch.

Các cách sửa có chủ đích:

- Chuyển application class lên package cha `com.example`.
- Chuyển job xuống package con của application class.
- Dùng `@ComponentScan(basePackages = {"com.example.app", "com.example.jobs"})` với phạm vi rõ.
- Dùng `@Import` hoặc `@Bean` để đăng ký class cụ thể.

Không nên quét toàn bộ classpath bừa bãi chỉ để tìm một job, vì có thể làm phát sinh bean ngoài dự kiến.

**Lưu ý:** một class nằm trong scan root vẫn có thể bị loại bởi `@Profile`, `@Conditional`, exclude filter hoặc vấn đề configuration. “Đúng package” là điều kiện cần trong mô hình scan thông thường, **không phải bảo đảm bean chắc chắn đã tồn tại**.

## 5. Bean lifecycle và việc đăng ký @Scheduled

Một luồng khái niệm:

```text
Đọc @Configuration
   |
   +-- @EnableScheduling --> scheduled annotation processor
   |
   +-- @ComponentScan / @Bean / @Import
                |
                v
          Bean definition
                |
                v
          Khởi tạo instance
                |
                v
          BeanPostProcessor
                |
                +--> kiểm tra @Scheduled
                +--> đăng ký trigger
                |
                v
           Context refresh
                |
                v
        Scheduler gọi khi đến hạn
```

Không phải mọi bước diễn ra đúng thứ tự tuyến tính như hình trong tất cả cấu hình, nhưng processor cần một bean **được tạo và xử lý** để phát hiện annotation.

### 5.1. @Lazy có thể ảnh hưởng thời điểm phát hiện

```java
@Lazy
@Component
class LazyCleanupJob {
    @Scheduled(fixedRate = 5000)
    public void run() {
        // ...
    }
}
```

Đây là fragment. Nếu một singleton lazy chưa từng được resolve/khởi tạo, nó chưa đi qua toàn bộ bean lifecycle, do đó không nên mong method của nó được đăng ký ngay khi startup.

**Không kết luận rằng `@Lazy` luôn luôn làm job không chạy:** nếu một singleton eager khác yêu cầu bean ấy, Spring vẫn có thể khởi tạo nó. Điều kiện thực tế là **bean đã được tạo và đi qua scheduling processor chưa**.

Với những job phải chạy ngay sau application startup, tránh đặt lazy initialization vô tình lên bean scheduled. Nếu cần bật/tắt theo môi trường, ưu tiên condition/feature flag hoặc dynamic schedule rõ ràng.

### 5.2. @PostConstruct không phải cách tự bật annotation

Nếu bạn viết:

```java
@PostConstruct
void start() {
    cleanup();
}
```

thì `cleanup()` chỉ bị gọi **một lần** theo logic Java từ lifecycle callback; nó không đăng ký recurring schedule. Nếu method `cleanup` đồng thời có `@Scheduled` và bạn còn tự đăng ký thêm một `TaskScheduler` trong `@PostConstruct`, có thể tạo **lịch trùng nhau**.

Không cần gọi một method `@Scheduled` thủ công để processor “nhìn thấy” nó; Spring xử lý metadata lúc tạo bean.

## 6. @Profile, @ConditionalOnProperty và cron disabled

### 6.1. Profile loại bean khỏi context

```java
@Component
@Profile("prod")
class NightlyJob {
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void work() {
        // ...
    }
}
```

Nếu `prod` không active, bean không được tạo từ declaration này. `@EnableScheduling` **không ép Spring tạo một bean bị profile loại**.

### 6.2. Feature flag trong Spring Boot

```java
@Component
@ConditionalOnProperty(
    prefix = "jobs.cleanup",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
class ConditionalJob {
    @Scheduled(fixedDelayString = "${jobs.cleanup.delay:PT30S}")
    public void clean() {
        // ...
    }
}
```

Đây là fragment Spring Boot, cần imports. Các property:

```properties
jobs.cleanup.enabled=true
jobs.cleanup.delay=PT30S
```

Nếu `jobs.cleanup.enabled=false` hoặc thiếu khi `matchIfMissing=false`, `ConditionalJob` không được tạo. Không có method scheduled của bean ấy để đăng ký.

**Phân biệt:** `@ConditionalOnProperty` thuộc Spring Boot, `@Profile` thuộc Spring Framework. Thay đổi property trong file khi ứng dụng đang chạy **không mặc nhiên** tái tạo bean hay thay schedule đã đăng ký. Muốn runtime rescheduling, cần cơ chế lifecycle/reconfiguration chủ động.

### 6.3. Cron bị disable không có nghĩa bean bị mất

`@Scheduled` hỗ trợ sentinel `Scheduled.CRON_DISABLED`, giá trị `"-"`. Ví dụ:

```java
@Scheduled(cron = "${jobs.cleanup.cron:-}")
public void clean() {
    // ...
}
```

Nếu cron resolve thành `-` thì trigger cron bị vô hiệu hóa. Bean **vẫn có thể tồn tại** và được scheduling processor kiểm tra; chỉ là không có lịch cron hoạt động từ declaration này.

Ba trạng thái cần phân biệt:

- **Không có bean** do scan/condition.
- **Có bean nhưng không có scheduled annotation processing** do chưa bật hạ tầng đúng context.
- **Có bean và processor nhưng trigger bị disable/chưa đến thời điểm chạy**.

Chúng có cùng biểu hiện “không thấy tick”, nhưng cách giải quyết hoàn toàn khác nhau.

## 7. Ràng buộc method @Scheduled

### 7.1. Không nhận tham số

```java
@Scheduled(fixedRate = 5000)
public void refresh() {
    // Hợp lệ.
}
```

Scheduler không có caller cung cấp business arguments. Vì thế:

```java
@Scheduled(fixedRate = 5000)
public void refresh(String tenantId) {
    // Không đúng contract method scheduled.
}
```

Đây là fragment phản ví dụ. Muốn dependency, inject qua constructor; muốn tenant ID, đọc từ cấu hình/repository khi tick hoặc lập job riêng.

### 7.2. Giá trị trả về không là kết quả job

Với method đồng bộ thông thường, `void` là lựa chọn rõ ràng:

```java
@Scheduled(fixedRate = 5000)
public void refresh() {
    refreshCache();
}
```

Nếu method synchronous trả giá trị, scheduler thường **bỏ qua kết quả trả về**; không tự lưu thành trạng thái job hoặc đưa cho HTTP caller. Kết quả nghiệp vụ cần logging, metrics hoặc job store riêng.

**Ngoại lệ theo phiên bản:** Spring Framework 6.1+ hỗ trợ một số method trả reactive `Publisher` hoặc kiểu có deferred adapter, cùng Kotlin suspending functions theo cơ chế subscription. Không suy rộng rằng một `CompletableFuture` bất kỳ được scheduler coi là deferred publisher; loại không hỗ trợ deferred subscription không phù hợp với con đường reactive scheduling ấy.

### 7.3. Trigger hợp lệ

Dùng một dạng lịch chủ yếu cho một declaration: `fixedRate`, `fixedDelay` hoặc `cron`; `initialDelay` cho fixed-rate/fixed-delay, hoặc theo API phiên bản mới có thể dùng riêng cho một lần chạy.

```java
@Scheduled(
    initialDelay = 2,
    fixedRate = 5,
    timeUnit = TimeUnit.SECONDS)
public void poll() {
    // ...
}
```

Các mốc chỉ mang tính scheduling, không phải hard real-time guarantee.

### 7.4. Visibility và proxy

Spring có thể dùng reflection/proxy để gọi method của bean. Với JDK dynamic proxy, method chỉ có trên implementation mà không được proxy expose có thể gây vấn đề trong một số cấu hình AOP; mức truy cập cũng cần phù hợp.

Một quy ước dễ bảo trì là **public void no-arg** method trên một Spring bean được inject, tránh private/final/proxy edge cases khi chưa test. Không phải mọi method scheduled đều bắt buộc `public` trong tất cả phiên bản, nhưng “được nhận diện” và “có thể invoke trên bean/proxy thực tế” là hai việc cần cùng thỏa.

**Đừng nhầm với `@Async`:** `@Async` proxy mode phụ thuộc lời gọi **đi qua proxy**; `@Scheduled` được processor chủ động đăng ký từ bean. Không cần một controller gọi scheduled method để nó bắt đầu.

## 8. Ví dụ Spring Boot 3.x đầy đủ

Ví dụ dùng **Java 17+, Spring Boot 3.x / Spring Framework 6.x** và dependency `spring-boot-starter-web`. Nó tạo một `ThreadPoolTaskScheduler` managed bean để dễ kiểm tra, một `@Component` job và endpoint quan sát số lần tick.

Đặt tất cả code dưới đây vào **src/main/java/com/example/demo/SchedulingDiscoveryApplication.java**:

```java
package com.example.demo;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableScheduling
public class SchedulingDiscoveryApplication {
    public static void main(String[] args) {
        SpringApplication.run(SchedulingDiscoveryApplication.class, args);
    }

    @Bean("taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduled-");
        return scheduler;
    }
}

@Component
class InventoryPollingJob {
    private static final Logger log =
            LoggerFactory.getLogger(InventoryPollingJob.class);
    private final AtomicInteger ticks = new AtomicInteger();

    @Scheduled(initialDelay = 1, fixedDelay = 3,
               timeUnit = TimeUnit.SECONDS)
    public void poll() {
        int number = ticks.incrementAndGet();
        log.info("Inventory poll {}, thread={}",
                number, Thread.currentThread().getName());
    }

    public int tickCount() {
        return ticks.get();
    }
}

record ScheduleStatus(boolean beanPresent, int ticks) {}

@RestController
class ScheduleStatusController {
    private final InventoryPollingJob job;

    ScheduleStatusController(InventoryPollingJob job) {
        this.job = job;
    }

    @GetMapping("/schedule/status")
    public ScheduleStatus status() {
        return new ScheduleStatus(true, job.tickCount());
    }
}
```

Spring Boot project cần dependency web và logging tương ứng. Đây là **đầy đủ về Java classes/imports**, không biên dịch được chỉ bằng `javac` trần nếu không có Spring jars.

### 8.1. Chạy ứng dụng

Trong project có Gradle wrapper và Spring Boot Gradle plugin:

```bash
./gradlew bootRun
curl -s http://localhost:8080/schedule/status
```

Trên Windows dùng `gradlew.bat bootRun`; với PowerShell có thể dùng `curl.exe`.

Một JSON **ví dụ** sau khi đã chạy qua vài tick:

```json
{"beanPresent":true,"ticks":2}
```

Giá trị `ticks` **không cố định**, phụ thuộc thời điểm gọi endpoint và scheduler. Log có dạng minh họa:

```text
INFO ... [scheduled-1] Inventory poll 1, thread=scheduled-1
INFO ... [scheduled-1] Inventory poll 2, thread=scheduled-1
```

Tên worker/timestamp không phải hợp đồng chính xác. `beanPresent=true` là giá trị controller hardcode vì constructor đã inject bean; nếu bean không tồn tại, ứng dụng không khởi tạo controller thành công, thay vì âm thầm trả false.

### 8.2. Vì sao job chạy?

1. `@SpringBootApplication` scan package `com.example.demo` và những bean bên trong.
2. `@EnableScheduling` import/register scheduling processor trong context.
3. `InventoryPollingJob` mang `@Component` nên được tạo như managed bean.
4. Processor nhận diện `@Scheduled` trên method `poll()` không tham số.
5. Lịch có `initialDelay` và `fixedDelay` hợp lệ, được đăng ký với scheduler bean `taskScheduler`.
6. Scheduler worker gọi `poll()`, tăng `AtomicInteger`; controller đọc `tickCount()`.
7. Không cần `@EnableAsync` và controller **không gọi trực tiếp `poll()`** để khởi động lịch.

### 8.3. Hai thí nghiệm phân biệt nguyên nhân

**Bỏ `@EnableScheduling`** (và không có config khác bật scheduling): `InventoryPollingJob` vẫn là bean, endpoint vẫn có thể hoạt động, nhưng processor không tự đăng ký phương thức annotated; `ticks` không tăng vì lịch này.

**Bỏ `@Component` trên job** mà không đăng ký qua `@Bean`: bean không được tạo qua scan. Vì controller đòi inject `InventoryPollingJob`, ứng dụng có thể lỗi startup do thiếu dependency. Đây là lỗi bean detection, không phải lỗi `fixedDelay`.

Đừng làm hai thay đổi cùng lúc nếu muốn biết nguyên nhân. Chạy thí nghiệm trên project demo/test thay vì sửa ngẫu nhiên production.

## 9. Java 17 độc lập: annotation chỉ là metadata

Ví dụ này không có Spring. Nó minh họa tại sao một annotation được JVM nhìn thấy **không tự tạo lịch**; phải có một đoạn mã đăng ký thật. Đây là **ẩn dụ về vai trò của processor**, không phải mô phỏng chính xác `ScheduledAnnotationBeanPostProcessor`.

Lưu thành **AnnotationIsMetadataDemo.java**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AnnotationIsMetadataDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Tick {}

    static final class ExampleJob {
        private final AtomicInteger calls = new AtomicInteger();

        @Tick
        public void run() {
            calls.incrementAndGet();
        }
    }

    public static void main(String[] args) throws Exception {
        ExampleJob job = new ExampleJob();
        boolean annotated = ExampleJob.class.getMethod("run")
                .isAnnotationPresent(Tick.class);

        System.out.println("annotated=" + annotated);
        System.out.println("beforeRegistration=" + job.calls.get());

        ScheduledExecutorService pool =
                Executors.newSingleThreadScheduledExecutor();
        CountDownLatch fired = new CountDownLatch(1);
        try {
            pool.schedule(() -> {
                job.run();
                fired.countDown();
            }, 0, TimeUnit.MILLISECONDS);

            fired.await();
            System.out.println("afterRegistration=" + job.calls.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Chạy với JDK 17+:

```bash
javac AnnotationIsMetadataDemo.java
java AnnotationIsMetadataDemo
```

**Stdout xác định:**

```text
annotated=true
beforeRegistration=0
afterRegistration=1
```

`@Tick` có runtime retention nên được reflection phát hiện; trước khi scheduler đăng ký task thì chưa có lệnh gọi method. `pool.schedule` mới thực sự đưa task vào scheduler. `CountDownLatch` đợi đúng một lần chạy, không phụ thuộc `Thread.sleep`. Với Spring, phần tương ứng là **bean lifecycle + scheduled annotation processor + TaskScheduler**, chứ không phải annotation tự chạy.

## 10. Integration test cho bean được đăng ký

Với dependency `spring-test` và JUnit Jupiter (ví dụ `spring-boot-starter-test` ở test scope), có thể dùng một context Spring nhỏ và `CountDownLatch` để kiểm tra registration.

Đặt vào **src/test/java/com/example/demo/SchedulingDetectionTest.java**:

```java
package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = SchedulingDetectionTest.TestConfig.class)
class SchedulingDetectionTest {
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class TestConfig {
        @Bean
        ProbeJob probeJob() {
            return new ProbeJob();
        }
    }

    static class ProbeJob {
        private final CountDownLatch firstRun =
                new CountDownLatch(1);

        @Scheduled(initialDelay = 10, fixedDelay = 60_000)
        public void tick() {
            firstRun.countDown();
        }
    }

    @Autowired
    ProbeJob job;

    @Test
    void scheduledMethodOnBeanIsRegistered() throws Exception {
        assertTrue(job.firstRun.await(5, TimeUnit.SECONDS));
    }
}
```

Những điều test này chứng minh:

- `@EnableScheduling` thực sự nằm trong test context.
- `ProbeJob` không có `@Component` nhưng `@Bean` tạo managed instance.
- `@Scheduled` không có args, có trigger hợp lệ.
- Có một lượt thực thi đủ để `CountDownLatch` mở.
- Test **không đếm cứng số tick trong một khoảng nhỏ**; thời gian scheduler luôn có jitter. Ngưỡng 5 giây là giới hạn chờ phù hợp nhiều môi trường test, có thể cần điều chỉnh trên CI chậm.

Để làm negative test, tạo **context riêng** không có `@EnableScheduling` và không có processor cấu hình tương đương, nhưng vẫn đăng ký `ProbeJob`. Bean có thể tồn tại mà method không có schedule. Nếu chỉ dùng `Thread.sleep(20)` rồi thấy không chạy, không đủ bằng chứng; nên kiểm tra cả infrastructure/registered tasks và context isolation.

### 10.1. containsBean không chứng minh job đang chạy

```java
boolean beanPresent = context.containsBean("probeJob");
```

Fragment trên xác nhận tên bean, **không chứng minh** trigger đã được đăng ký hoặc scheduler đã chạy. Để chẩn đoán chắc chắn phải tách hai phép kiểm tra:

- Bean discovery: bean có tồn tại và được khởi tạo theo điều kiện cần?
- Schedule execution: processor đã đăng ký task và có tick hợp lệ?

## 11. Không định nghĩa scheduler bean riêng vẫn có thể chạy

Một nhận định sai phổ biến: “Không thấy `@Bean("taskScheduler")` trong code, vậy `@Scheduled` không thể hoạt động.”

Trong Spring, hạ tầng scheduling có cơ chế **tìm một scheduler phù hợp hoặc dùng fallback**. Spring Boot cũng có thể auto-configure scheduler khi điều kiện phù hợp. Vì thế, **không có custom scheduler bean không đồng nghĩa annotation không hoạt động**.

Khi cần kiểm soát executor, có thể tạo bean rõ ràng như ví dụ Spring Boot. Với auto-configured `ThreadPoolTaskScheduler` trong Spring Boot 3.x và không có custom override, ví dụ properties:

```properties
spring.task.scheduling.thread-name-prefix=scheduling-
spring.task.scheduling.pool.size=2
```

Nếu đã khai báo custom scheduler bean, các property trên **không mặc nhiên cấu hình lại object bạn tự tạo**. Nếu dùng Java 21+ và Boot virtual-thread mode, implementation có thể khác và pool property không mang cùng ngữ nghĩa. Không sử dụng những giả định về virtual threads cho Java 17.

Khi có nhiều scheduler beans, hãy chọn naming/qualification rõ ràng hoặc dùng `SchedulingConfigurer` và `ScheduledTaskRegistrar`. Không dựa vào thứ tự tạo bean để đoán scheduler nào được dùng.

**Phân biệt:** nếu code tự gọi `taskScheduler.scheduleAtFixedRate(...)`, đó là **đăng ký API trực tiếp**, không phụ thuộc `@EnableScheduling` để tự tìm `@Scheduled`. Bài học này nói về **annotation discovery**, không phải mọi cách dùng TaskScheduler.

## 12. Những trường hợp “không thấy log” nhưng không phải thiếu bean

| Nguyên nhân | Thực tế có thể xảy ra | Cách kiểm tra |
| --- | --- | --- |
| Thiếu `@EnableScheduling` đúng context | Bean tồn tại nhưng method không được processor đăng ký | Kiểm tra config được nạp |
| Ngoài package scan | Không có bean | Kiểm tra package root / bean definitions |
| `@Profile` hoặc condition sai | Bean bị loại khỏi context | Kiểm tra active profiles, properties, condition report |
| `@Lazy` bean chưa tạo | Processor chưa xử lý instance | Kiểm tra bean initialization |
| Context cha/con khác nhau | Hạ tầng ở context này, job ở context khác | Kiểm tra context ownership |
| Cron = `-` | Bean có thể tồn tại, trigger cron bị disable | Kiểm tra giá trị property |
| Cron chưa đến giờ | Task được đăng ký nhưng chưa được trigger | Kiểm tra timezone/mốc cron |
| Initial delay dài | Chưa tới lượt đầu | Kiểm tra `initialDelay` |
| Scheduler worker bị block | Lượt mới trễ | Thread dump, pool active và run duration |
| Job ném exception | Có tick nhưng nghiệp vụ thất bại | ErrorHandler, metric, logs |
| Log level/filter | Job có chạy nhưng log bị ẩn | Metric/latch và logging config |
| Nhiều replicas | Job chạy ở instance khác | Log aggregation và instance ID |
| Lịch bị trùng | Job chạy nhiều lần thay vì không chạy | Kiểm tra bean instances, annotations, contexts |

### Quy trình chẩn đoán theo bằng chứng

1. Xác định **ApplicationContext** nào sở hữu bean job và class `@EnableScheduling`.
2. Kiểm tra cấu hình `@EnableScheduling` **có được scan/import** không, không chỉ tồn tại trong source.
3. Kiểm tra bean job xuất hiện qua component scan hay `@Bean` và có bị profile/condition loại không.
4. Xác nhận **method no-args, trigger và placeholder** hợp lệ.
5. Kiểm tra bean có `@Lazy` khiến chưa được tạo không.
6. Kiểm tra scheduler bean/pool, có bị blocking/shutdown hay không.
7. Dùng một test có latch hoặc metric tick thật để xác nhận execution.
8. Phân biệt **tick started** và **business succeeded**.
9. Kiểm tra contexts/replicas để loại trừ lịch chạy ở nơi khác.
10. Chỉ sau đó mới thay đổi cấu hình, tránh thêm `@EnableScheduling` hoặc `@ComponentScan` tràn lan.

## 13. Proxy, self-invocation và việc chạy trùng

`@Scheduled` không cần bean khác gọi method để được đăng ký: processor tạo lịch gọi method trên managed bean. Do đó, một lời gọi Java nội bộ:

```java
public void manualRefresh() {
    refresh(); // Chỉ gọi thêm một lần, không đăng ký lịch.
}

@Scheduled(fixedRate = 5000)
public void refresh() {
    // ...
}
```

không phải cách “bật” scheduling. Ngược lại, `@Async` trong proxy mode phụ thuộc đường gọi qua proxy để interceptor chạy. Đừng dùng quy tắc self-invocation của `@Async` để kết luận `@Scheduled` phải chờ controller gọi qua proxy.

**AOP caution:** nếu job bean bị bọc JDK dynamic proxy và scheduled method không được proxy expose hợp lệ, việc invoke method có thể gặp vấn đề. Có thể tách một scheduler entrypoint đơn giản gọi sang business service được inject để transaction/security/caching advice rõ ràng, rồi viết integration test cho cấu hình AOP thực tế.

### 13.1. @Scheduled là repeatable

`@Scheduled` có thể khai báo nhiều lần trên một method:

```java
@Scheduled(cron = "0 */5 * * * *")
@Scheduled(cron = "30 */5 * * * *")
public void update() {
    // Hai trigger độc lập.
}
```

Các trigger được xử lý độc lập và có thể overlap theo timing/pool. Đừng cho rằng chúng hợp thành một lịch duy nhất.

### 13.2. Nhiều instances và nhiều replicas

Một class job được đăng ký thành **hai bean instances**, hoặc có nhiều `ApplicationContext`, có thể tạo nhiều schedules. Một ứng dụng được triển khai trên **ba replicas** cũng có thể có ba local schedulers.

`@EnableScheduling` **không tự cung cấp distributed lock, leader election hoặc exactly-once processing**. Với nghiệp vụ phải duy nhất toàn cụm, cần claim/lease, idempotency, unique constraint hoặc distributed scheduler. `poolSize=1` chỉ giới hạn worker trong một scheduler instance, không giới hạn toàn cluster.

## 14. Kiểm soát lỗi và quan sát

Ba cấp trạng thái cần phân biệt:

1. **Bean registered:** class job được tạo trong context.
2. **Schedule registered/tick started:** processor đăng ký trigger và scheduler thực sự gọi method.
3. **Business completed:** công việc đã đạt điều kiện thành công nghiệp vụ.

Đừng báo `SUCCEEDED` chỉ vì method được phát hiện hoặc tick đã bắt đầu.

Metric/log hữu ích: số job bean, số trigger, số tick bắt đầu, thành công/thất bại, thời gian chạy, scheduler lag, active workers, task overlap, retry và duplicate counts.

Với Spring periodic scheduler thông thường, một exception có thể được ErrorHandler log rồi suppress để các tick sau tiếp tục; raw JDK `ScheduledExecutorService` có thể dừng chuỗi periodic sau exception không bắt. Do đó phải hiểu **implementation và error handler** chứ không suy luận chỉ từ `@Scheduled`.

Scheduled worker thường không có HTTP request context; `MDC`, transaction và security context không tự được thừa kế từ một request. Nếu cần correlation ID, tạo job-run ID theo tick, truyền context có chủ đích và cleanup trong `finally`.

## 15. Các lỗi thường gặp

**Lỗi 1.** Có `@Scheduled` nhưng không bật scheduling processor.

**Lỗi 2.** Có `@EnableAsync` và tưởng đã bật `@Scheduled`.

**Lỗi 3.** Đặt `@EnableScheduling` trong configuration class không được context scan/import.

**Lỗi 4.** Nghĩ `@EnableScheduling` tự động scan tất cả classpath.

**Lỗi 5.** Class job mang `@Component` nhưng nằm ngoài component scan root.

**Lỗi 6.** Tự `new` job rồi chờ Spring gọi method theo lịch.

**Lỗi 7.** Cho rằng class phải có `@Component`, bỏ qua cách đăng ký qua `@Bean`.

**Lỗi 8.** Không kiểm tra `@Profile`/`@ConditionalOnProperty`/feature flags.

**Lỗi 9.** Để scheduled bean ở chế độ lazy mà không ai tạo nó.

**Lỗi 10.** Root context đã bật scheduling, tưởng mọi child context cũng được bật.

**Lỗi 11.** Method `@Scheduled` nhận tham số và mong scheduler tự cung cấp.

**Lỗi 12.** Nhầm return value của method synchronous là một kết quả job tự được lưu.

**Lỗi 13.** Cron bị disable hoặc chưa đến giờ nhưng suy ra bean không tồn tại.

**Lỗi 14.** Không test cấu hình JDK/class-based proxy và visibility của method.

**Lỗi 15.** Vừa có `@Scheduled` vừa đăng ký thủ công cho cùng công việc.

**Lỗi 16.** Khai báo repeatable schedules và tin chỉ có một trigger.

**Lỗi 17.** Nghĩ `@Scheduled` chỉ chạy một lần trên toàn cluster.

**Lỗi 18.** Chỉ nhìn log để kết luận có/không có tick; log level có thể che sự kiện.

**Lỗi 19.** Thấy thiếu custom `ThreadPoolTaskScheduler` là kết luận scheduling lỗi, dù Spring có lookup/fallback.

**Lỗi 20.** Thiết kế job quan trọng không có idempotency, trạng thái hoặc crash recovery.

## 16. Checklist triển khai

- [ ] `@EnableScheduling` nằm trên một config class **được nạp trong đúng ApplicationContext**.
- [ ] Bean chứa `@Scheduled` được tạo qua scan, `@Bean` hoặc registration hợp lệ.
- [ ] Package class job nằm trong vùng scan dự kiến.
- [ ] Không có profile/conditional/lazy behavior đang loại hoặc trì hoãn bean ngoài ý muốn.
- [ ] Method **không có tham số**, trigger hợp lệ, cron/zone/initialDelay như yêu cầu.
- [ ] Cấu hình scheduler bean, pool và error handling đủ rõ; thiếu custom bean không nhất thiết là lỗi.
- [ ] Không đăng ký trùng bằng nhiều instances, repeatable annotation hoặc manual scheduling.
- [ ] Nếu có nhiều context, bật scheduling tại từng context cần sử dụng.
- [ ] Nếu có nhiều replicas, áp distributed coordination/idempotency theo nghiệp vụ.
- [ ] Dùng integration test có `CountDownLatch` để chứng minh tick; không chỉ test `containsBean`.
- [ ] Theo dõi bean discovered, schedule registered, tick started và business success **như các cấp khác nhau**.
- [ ] Bảo đảm timeout, shutdown, retry và dữ liệu trạng thái bền vững nếu công việc quan trọng.

## 17. Câu hỏi tự kiểm tra

**Câu 1.** Một class có `@Scheduled` nhưng object được tạo bằng `new` trong controller. Có tự chạy định kỳ?

**Đáp án:** Không. Object không phải managed bean, không đi qua scheduling bean post-processor. Phải đăng ký bean hoặc chủ động dùng scheduler API.

**Câu 2.** Bean tồn tại và có `@Scheduled`, nhưng context thiếu `@EnableScheduling` và không có processor tương đương. Có lịch không?

**Đáp án:** Không tự có lịch từ annotation. Bean creation và scheduling registration là hai giai đoạn khác nhau.

**Câu 3.** `@Component` job ở package sibling của application class. Vì sao không chạy?

**Đáp án:** Component scan mặc định không tìm thấy class; không có bean. Sửa vị trí package hoặc khai báo scan/import tường minh.

**Câu 4.** Class không có `@Component` nhưng được `@Bean` trả về; Spring có phát hiện method không?

**Đáp án:** Có thể, vì instance là managed bean và được processor xử lý trong context bật scheduling. `@Component` không phải điều kiện duy nhất.

**Câu 5.** Root context bật `@EnableScheduling`, child có job bean riêng. Child tự được bật?

**Đáp án:** Không mặc nhiên. Annotation có phạm vi local application context; bật trong từng context cần xử lý.

**Câu 6.** `@Lazy` có chắc chắn khiến job không chạy?

**Đáp án:** Không tuyệt đối; nếu bean được một dependency khác eager resolve, nó vẫn được tạo. Nhưng nếu chưa được khởi tạo thì processor chưa xử lý instance.

**Câu 7.** `@ConditionalOnProperty(havingValue="true")` không khớp thì `@EnableScheduling` có ép tạo bean không?

**Đáp án:** Không. Bean bị loại theo condition nên không có method của bean đó để đăng ký lịch.

**Câu 8.** Cron placeholder resolve thành `-` và job không chạy. Có chắc bean bị loại?

**Đáp án:** Không. Bean có thể tồn tại nhưng cron trigger bị disable theo giá trị đặc biệt.

**Câu 9.** Có bắt buộc tự định nghĩa `@Bean TaskScheduler` thì `@Scheduled` mới hoạt động?

**Đáp án:** Không. Spring có scheduler lookup/fallback, Boot có thể auto-configure scheduler phù hợp. Tùy version/context.

**Câu 10.** Một method có hai declarations `@Scheduled` thì có một hay hai triggers?

**Đáp án:** Hai trigger độc lập, có thể chạy gần nhau hoặc overlap.

**Câu 11.** Có ba replica cùng bật scheduling. Một scheduled job có chắc chỉ chạy một lần toàn cụm?

**Đáp án:** Không. Mỗi instance có thể tạo schedule riêng; cần distributed lock/lease/claim và idempotency nếu nghiệp vụ đòi duy nhất.

**Câu 12.** `ApplicationContext.containsBean("job")` trả true đủ chứng minh tick đang chạy chưa?

**Đáp án:** Chưa. Nó cho biết bean tồn tại, không chứng minh processor đăng ký task hay trigger đã thực thi. Cần kiểm tra registration/tick riêng.

## Tóm tắt

**`@EnableScheduling` bật cơ chế phát hiện `@Scheduled` trong một Spring ApplicationContext; nó không tự biến mọi class có annotation thành Spring bean.** Để job chạy, phải có **bean thực sự được Spring quản lý, tạo và xử lý**, method có trigger hợp lệ, và scheduling infrastructure ở đúng context.

Khi không thấy job chạy, hãy kiểm tra lần lượt **configuration → bean registration/component scan → profile/condition/lazy → method/trigger → scheduler execution → business outcome**. Đừng khắc phục bằng cách thêm annotation tràn lan hoặc chỉ xem log.

Spring Boot example và integration test giúp xác nhận bằng hành vi; với nhiều replicas, cần điều phối phân tán và idempotency nếu công việc không được trùng lặp.

## Tài liệu tham khảo

1. Spring Framework Reference — Task Execution and Scheduling, `@EnableScheduling` và `@Scheduled`: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
2. Spring Framework 6.2 Javadoc — `EnableScheduling` và local ApplicationContext: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html
3. Spring Framework 6.2 Javadoc — `Scheduled` và method contract: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html
4. Spring Framework 6.2 Javadoc — `ScheduledAnnotationBeanPostProcessor`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/ScheduledAnnotationBeanPostProcessor.html
5. Spring Framework 6.2 Javadoc — `SchedulingConfiguration`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/SchedulingConfiguration.html
6. Spring Boot 3.3 Reference — Task Execution and Scheduling: https://docs.spring.io/spring-boot/3.3/reference/features/task-execution-and-scheduling.html
7. Spring Framework Reference — Component scanning: https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html
8. Spring Framework Reference — Lazy initialization: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-lazy-init.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-tasks-002">Phương thức lập lịch không nhận đối số</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức lập lịch không nhận đối số trong Spring @Scheduled

## 1. Quy tắc quan trọng nhất

Một phương thức do Spring tự động kích hoạt bằng `@Scheduled` **không được khai báo tham số**. Đây là hợp đồng của `@Scheduled`, không chỉ là khuyến nghị về style.

Ví dụ đúng:

~~~java
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InventoryPollingJob {
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void pollInventory() {
        // Spring gọi method này theo lịch, không truyền đối số.
    }
}
~~~

Ví dụ sai:

~~~java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InvalidInventoryJob {
    @Scheduled(fixedDelay = 5000)
    public void pollInventory(String tenantId) {
        // Sai: Spring scheduling không cấp tenantId cho tham số này.
    }
}
~~~

Hai đoạn là **ví dụ riêng biệt**, không đặt class sai vào dự án chạy thật. Với Spring Framework 6.x, phương thức `pollInventory(String tenantId)` có thể biên dịch Java nhưng không thỏa quy tắc đăng ký `@Scheduled`. Khi Spring khởi tạo context và xử lý annotation, nó có thể báo lỗi đăng ký tác vụ vì phương thức phải có **zero arguments**, dẫn đến lỗi khởi động. Đừng chờ tới lần chạy cron để hy vọng Spring tự truyền một giá trị.

**Điểm cần nhớ:** các thuộc tính **của annotation** (ví dụ `fixedDelay`, `fixedRate`, `cron`, `initialDelay`, `zone`) là cấu hình **khi nào chạy**; chúng không phải tham số của Java method. `@Scheduled(cron = "...") public void job()` hợp lệ vì method `job()` có danh sách tham số rỗng.

Nếu cần nhập `tenantId`, `orderId`, `Clock`, `HttpServletRequest`, `JobContext` hoặc một bản ghi từ database, hãy lấy dữ liệu qua dependency injection hoặc gọi **một service khác có tham số bên trong phương thức no-arg**, hoặc chuyển sang API lập lịch programmatic bằng `TaskScheduler`.

## 2. Vì sao Spring yêu cầu zero arguments?

Có hai cơ chế dễ bị nhầm:

- Khi code ứng dụng gọi `billingService.invoice("A-123")`, caller Java biết rõ đối số là gì và truyền vào phương thức.
- Với `@Scheduled`, **Spring là bên đăng ký lịch**. `ScheduledAnnotationBeanPostProcessor` quét phương thức đã đánh dấu, tạo một tác vụ dựa trên method đó và scheduler tự gọi vào thời điểm phù hợp. Không có một HTTP caller hay event source cụ thể đứng ở đó để cung cấp `tenantId` hoặc `orderId`.

Sơ đồ khái niệm:

~~~text
Khởi tạo Spring ApplicationContext
            |
            v
       @EnableScheduling
            |
            v
ScheduledAnnotationBeanPostProcessor
            |
            +-- phát hiện @Scheduled trên bean đã quản lý
            +-- xác minh chữ ký phương thức: 0 tham số
            +-- tạo và đăng ký scheduled task
            |
            v
TaskScheduler / scheduler worker ---- tới hạn ----> bean.job()
                                                  (không có đối số)
                                                          |
                                                          v
                                                service.handle(input)
                                                (input lấy trong method)
~~~

Không được diễn giải rằng Spring có một cơ chế tự suy luận tham số như `@RequestParam` hay `@PathVariable` của Spring MVC cho scheduled method. Một annotation lập lịch không chứa thông tin để map một request vào các tham số Java.

### 2.1. @Scheduled khác @Async, @EventListener và controller

| Cơ chế | Ai phát lời gọi? | Method có được nhận đối số? | Trường hợp điển hình |
| --- | --- | --- | --- |
| `@Scheduled` | Scheduler đăng ký khi Spring khởi tạo | **Không** | Quét công việc quá hạn, refresh cache theo lịch |
| `@Async` | Caller Java khác gọi method (qua proxy) | **Có** | `generateReport(reportId)` chạy nền |
| `@EventListener` | Spring event infrastructure dispatch event | **Có**, theo event listener contract | `onOrderCreated(OrderCreatedEvent event)` |
| `@PostMapping`/`@GetMapping` | Spring MVC dispatch HTTP request | **Có**, theo resolver của MVC | `getOrder(@PathVariable String id)` |
| `TaskScheduler.scheduleWithFixedDelay(Runnable, Duration)` | Scheduler gọi một `Runnable` đã đăng ký | `Runnable.run()` không có đối số, nhưng **lambda có thể capture dữ liệu** | Tạo lịch riêng cho từng job/tenant |

Một event listener có đối số không trở thành scheduled method chỉ bằng cách gắn thêm `@Scheduled`. Nếu method có chữ ký `onMessage(Message m)`, giữ nó ở cơ chế listener và cho một scheduled method no-arg gọi service có tham số khi cần; không gắn hai kiểu annotation với hợp đồng mâu thuẫn.

### 2.2. @EnableScheduling là điều kiện khởi phát

Với Java configuration, bật annotation scheduling trong context:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
~~~

Spring Boot không khiến mọi method `@Scheduled` tự được đăng ký nếu scheduling annotation processing chưa được bật trong context thích hợp. Một ứng dụng có thể đặt `@EnableScheduling` ngay trên main configuration class mang `@SpringBootApplication`.

`@EnableScheduling` là để scheduler tự quét/đăng ký method theo lịch; nó **khác** `@EnableAsync`, vốn kích hoạt cơ chế interceptor `@Async` với lời gọi qua proxy. Không dùng quy tắc self-invocation của `@Async` để kết luận rằng `@Scheduled` cần controller hay một method bên ngoài gọi mới chạy: scheduled infrastructure tự kích hoạt các tác vụ đã đăng ký.

## 3. Những chữ ký hợp lệ, không hợp lệ và return type

### 3.1. Không đối số mới hợp lệ

~~~java
// Đúng: phương thức không có tham số.
@Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
public void refreshMetrics() {
    metricsService.refresh();
}

// Đúng: cron với múi giờ, method vẫn không có tham số.
@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")
public void nightlyCleanup() {
    cleanupService.removeExpiredRows();
}
~~~

Đây là các snippet nằm trong Spring-managed component, với `metricsService`/`cleanupService` do ứng dụng inject. Chúng chưa phải một class độc lập.

~~~java
// SAI - Spring không inject tham số method @Scheduled.
@Scheduled(fixedDelay = 5000)
public void refreshMetrics(String serviceName) { }

// SAI - không có argument resolver kiểu MVC ở đây.
@Scheduled(cron = "0 * * * * *")
public void refreshMetrics(HttpServletRequest request) { }

// SAI - kể cả khi tham số là một Spring bean:
@Scheduled(fixedDelay = 5000)
public void refreshMetrics(MetricsService service) { }
~~~

Những đoạn sai trên có thể là Java hợp lệ về chữ ký nhưng **không phải scheduled callback hợp lệ**. Các kiểu `HttpServletRequest` và `MetricsService` chỉ minh họa; không cố biên dịch chúng riêng. `@Autowired` hay `@Qualifier` đặt lên tham số cũng không biến scheduled method thành endpoint có argument resolver.

### 3.2. Return type thông thường: ưu tiên void

Một method `@Scheduled` kiểu Java đồng bộ **thường nên trả `void`**. Spring Javadoc nói rằng nếu phương thức đồng bộ có kiểu trả về khác `void`, giá trị trả về **bị bỏ qua** bởi scheduler. Ví dụ:

~~~java
// Không khuyến khích: return value không được Spring gửi cho caller.
@Scheduled(fixedDelay = 10000)
public int countExpiredOrders() {
    return orderService.countExpiredOrders();
}
~~~

Code có thể tính được số `42`, nhưng không có controller nào đang đợi `42`; không có kênh kết quả tương ứng trong scheduled registration. Nếu số này quan trọng, hãy lưu metric, log, job status, DB record hoặc gọi service với một contract có thể quan sát:

~~~java
// Phù hợp hơn: method lịch không trả dữ liệu.
@Scheduled(fixedDelay = 10000)
public void countExpiredOrders() {
    int count = orderService.countExpiredOrders();
    metricsRecorder.recordExpiredOrders(count);
}
~~~

`orderService` và `metricsRecorder` là dependencies giả định. **Không đồng nhất “return bị bỏ qua” với “task không chạy”**: code vẫn được thực thi, chỉ là scheduler không tiêu thụ giá trị như một Future kết quả.

Phân biệt một ngoại lệ quan trọng từ **Spring Framework 6.1+**: phương thức reactive trả một `Publisher` có thể được Spring đăng ký theo cơ chế subscription định kỳ. Ngoại lệ này **không xóa yêu cầu zero arguments**; chi tiết ở mục 8.

### 3.3. Vì sao @Async có thể nhận tham số nhưng @Scheduled không?

~~~java
// Service do ứng dụng gọi theo phương thức Java thông thường:
@Async("reportExecutor")
public CompletableFuture<String> generate(String reportId) {
    return CompletableFuture.completedFuture("reports/" + reportId);
}
~~~

Caller cung cấp `reportId` khi gọi qua Spring proxy: `reportService.generate("R-15")`. Với `@Scheduled`, scheduler không có `reportId` tương ứng cho mỗi tick. Nếu cần vừa chạy theo lịch vừa gửi việc nền, hãy tách hai method: một method scheduled **không đối số** và một service async **có đối số**. Xem xét liệu việc gửi việc nền rồi trả về có thay đổi semantics `fixedDelay` hay không.

## 4. Giải pháp 1: constructor injection vào bean có @Scheduled

Đây là cách nên ưu tiên khi scheduled job dùng cùng dependencies qua nhiều lượt. Spring inject constructor **một lần khi tạo bean**, rồi scheduler gọi method no-arg mỗi lần tới hạn.

~~~java
package com.example.jobs;

import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiredOrderJob {
    private final OrderCleanupService cleanup;
    private final JobProperties config;

    public ExpiredOrderJob(
            OrderCleanupService cleanup, JobProperties config) {
        this.cleanup = cleanup;
        this.config = config;
    }

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
    public void runOnce() {
        // Input không đến từ tham số method @Scheduled.
        cleanup.removeExpired(config.batchSize());
    }
}
~~~

Class này là **snippet tích hợp**, không phải project độc lập: `OrderCleanupService` và `JobProperties` là các bean/kiểu do ứng dụng định nghĩa, phải được đăng ký và cấu hình. `runOnce()` vẫn đúng zero arguments; dữ liệu đi vào phương thức có tham số `cleanup.removeExpired(int)` thông qua object dependency.

Nếu cần cấu hình `batchSize`, có thể dùng `@ConfigurationProperties` và record/property bean tương ứng, hoặc tách một `@Bean` cung cấp config. Đừng gọi `new ExpiredOrderJob(...)` ngoài Spring rồi tưởng annotation sẽ tự đăng ký; bean phải được Spring quản lý.

### 4.1. @Value/configuration properties không phải tham số callback

~~~java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PricingRefreshJob {
    private final PricingService service;
    private final int batchSize;

    public PricingRefreshJob(
            PricingService service,
            @Value("${jobs.pricing.batch-size:50}") int batchSize) {
        this.service = service;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${jobs.pricing.delay:PT30S}")
    public void refresh() {
        service.refreshBatch(batchSize);
    }
}
~~~

Đây là **class cần ghép vào một ứng dụng có `PricingService` bean**. `@Value` ở **constructor**, không nằm trên tham số scheduled method. Thuộc tính `fixedDelayString` của annotation hỗ trợ placeholder/property, còn method `refresh()` vẫn không nhận tham số.

`PT30S` là duration 30 giây dạng ISO-8601. Kiểm tra version Spring và các giá trị được resolve theo environment/profile. Nếu dùng `@Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)` thì giá trị `30` nằm trong annotation, vẫn không phải đối số của method.

### 4.2. Dữ liệu thay đổi giữa các lượt chạy

Đừng cache toàn bộ trạng thái job ở constructor nếu nghiệp vụ yêu cầu đọc mới mỗi lần. Dependencies có thể là service/repository:

~~~java
// Snippet bên trong một Spring-managed component.
@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
public void scanDueTasks() {
    List<TaskRecord> pending = taskRepository.findDueTasks(100);
    for (TaskRecord task : pending) {
        taskProcessor.process(task.id(), task.tenantId());
    }
}
~~~

`TaskRecord`, `taskRepository` và `taskProcessor` là thành phần ứng dụng giả định. Scheduled callback không nhận tham số, nhưng nó chủ động **truy vấn danh sách mới tại mỗi lượt**, rồi truyền ID vào service xử lý nghiệp vụ. Cần giới hạn batch size, transaction boundary, retry và idempotency; nhiều pod có thể quét cùng tập bản ghi, nên phải có claim/lock nếu cần tránh xử lý trùng.

## 5. Ví dụ hoàn chỉnh chạy được: Java 17 + Spring Boot

Dự án dưới đây dùng **Java 17, Maven, Spring Boot 3.3.5** và một `ThreadPoolTaskScheduler` có tên rõ ràng. Một method `@Scheduled` **không nhận tham số** chạy ba lượt, tạo `Request` từ dữ liệu nội bộ rồi chuyển cho service **có tham số**. `ApplicationRunner` chỉ đợi ba lượt đầu bằng `CountDownLatch` để in kết quả kiểm chứng ổn định.

Không cần HTTP server, database hoặc `Thread.sleep`. Bốn tệp Java/Maven dưới đây đầy đủ cho demo.

### 5.1. Cấu trúc

~~~text
scheduled-no-args/
├── pom.xml
└── src/main/java/com/example/scheduled/
    ├── ScheduledNoArgsApplication.java
    ├── NoArgPoller.java
    └── BatchService.java
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
    <artifactId>scheduled-no-args</artifactId>
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
~~~

### 5.3. ScheduledNoArgsApplication.java

~~~java
package com.example.scheduled;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
@EnableScheduling
public class ScheduledNoArgsApplication {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                SpringApplication.run(ScheduledNoArgsApplication.class, args)) {
            // ApplicationRunner đã chạy trước khi SpringApplication.run trả về.
        }
    }

    @Bean("taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("sched-demo-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }

    @Bean
    ApplicationRunner verify(NoArgPoller poller, BatchService service) {
        return args -> {
            if (!poller.awaitThree(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for 3 runs");
            }

            System.out.println("COMPLETED_AT_LEAST_3="
                    + (service.completed() >= 3));
            System.out.println("FIRST_REQUEST_PRESENT="
                    + (service.firstRequestId() != null));
            System.out.println("SCHEDULER_THREAD_USED="
                    + poller.usedExpectedSchedulerThread());
            System.out.println("METHOD_PARAMETER_COUNT="
                    + NoArgPoller.class
                            .getDeclaredMethod("poll")
                            .getParameterCount());
        };
    }
}
~~~

`ThreadPoolTaskScheduler` là một bean được Spring quản lý và khởi tạo đúng lifecycle. `setPoolSize(1)` chỉ để demo, **không phải** tuyên bố mọi scheduler của Spring chỉ dùng một worker. Đặt tên bean `taskScheduler` giúp scheduling infrastructure có ứng viên rõ ràng; Spring Boot có thể auto-config một scheduler nếu không có bean phù hợp và điều kiện được đáp ứng.

### 5.4. BatchService.java

~~~java
package com.example.scheduled;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class BatchService {
    public record Request(String id) { }

    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicReference<String> firstRequestId =
            new AtomicReference<>();

    public void process(Request request) {
        firstRequestId.compareAndSet(null, request.id());
        completed.incrementAndGet();
    }

    public int completed() {
        return completed.get();
    }

    public String firstRequestId() {
        return firstRequestId.get();
    }
}
~~~

Service `process(Request request)` **được phép nhận đối số** vì nó không mang `@Scheduled` và được `NoArgPoller` gọi như Java bình thường. Dữ liệu `Request` trong demo do scheduled job tạo ở từng tick. `AtomicInteger`/`AtomicReference` làm trạng thái quan sát thread-safe dù ví dụ chỉ có một scheduler worker.

### 5.5. NoArgPoller.java

~~~java
package com.example.scheduled;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NoArgPoller {
    private final BatchService service;
    private final CountDownLatch firstThree = new CountDownLatch(3);
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicBoolean expectedThread = new AtomicBoolean(true);

    public NoArgPoller(BatchService service) {
        this.service = service;
    }

    @Scheduled(
            initialDelay = 100,
            fixedDelay = 150,
            timeUnit = TimeUnit.MILLISECONDS)
    public void poll() {
        // Spring tự gọi poll() không có đối số.
        if (!Thread.currentThread().getName().startsWith("sched-demo-")) {
            expectedThread.set(false);
        }

        String id = "request-" + sequence.incrementAndGet();
        service.process(new BatchService.Request(id));

        firstThree.countDown();
    }

    public boolean awaitThree(long timeout, TimeUnit unit)
            throws InterruptedException {
        return firstThree.await(timeout, unit);
    }

    public boolean usedExpectedSchedulerThread() {
        return expectedThread.get();
    }
}
~~~

Luồng chạy thực tế:

1. Context được refresh, `@EnableScheduling` cho phép Spring đăng ký method `NoArgPoller.poll()`.
2. Scheduler chờ khoảng `initialDelay=100ms` để bắt đầu lượt đầu khi đủ tài nguyên.
3. Mỗi lượt, `poll()` không nhận đối số; nó tạo `Request("request-" + sequence)`.
4. `service.process(Request)` có đối số và cập nhật trạng thái của service.
5. Sau ba lượt hoàn tất, latch về 0; runner xác minh trạng thái rồi main đóng context.

Lưu ý `CountDownLatch` chỉ dùng cho **bài kiểm chứng**, không phải cách cần dùng để cung cấp đối số cho scheduled method. `fixedDelay=150ms` tính từ lúc lượt trước kết thúc; nếu service xử lý lâu, các lượt cách nhau lâu hơn 150ms. Code không in từ worker nên các dòng stdout mong đợi không bị xen theo thứ tự ngẫu nhiên.

### 5.6. Lệnh chạy và stdout

Ở thư mục chứa `pom.xml`, cần JDK 17+ và Maven:

~~~bash
mvn spring-boot:run
~~~

Bỏ qua log khởi động/dừng của Spring và Maven. Bốn dòng do `ApplicationRunner` in:

~~~text
COMPLETED_AT_LEAST_3=true
FIRST_REQUEST_PRESENT=true
SCHEDULER_THREAD_USED=true
METHOD_PARAMETER_COUNT=0
~~~

Kết quả dựa trên `firstThree.await(...)`: runner chỉ in sau ít nhất ba lần `service.process(...)` thành công và latch đã được giảm, không dựa vào `Thread.sleep`. `METHOD_PARAMETER_COUNT=0` được xác minh qua reflection trên chính method `poll()`. Prefix `sched-demo-` đến từ bean scheduler đã đặt rõ ràng. Số lượt có thể tăng thêm trước lúc context đóng, nên dùng điều kiện **ít nhất ba**, không giả định lúc in luôn đúng ba.

Đây là bài demo semantics, **không phải** ví dụ job bền vững sau restart hoặc exactly-once toàn cluster. Trên production cần xử lý job state, idempotency, retry, shutdown và phân quyền phù hợp với tác vụ.

## 6. Nếu cần truyền ID cho một lịch riêng: TaskScheduler programmatic

Một yêu cầu khác: ứng dụng có hàng trăm tenant, mỗi tenant có lịch polling riêng với một `tenantId` đã xác thực. Không thể yêu cầu Spring tự gọi:

~~~java
// SAI nếu dùng @Scheduled: phương thức nhận đối số.
@Scheduled(fixedDelay = 30000)
public void pollTenant(String tenantId) { }
~~~

Thay vào đó đăng ký `Runnable`/lambda riêng cho mỗi tenant bằng `TaskScheduler`. `Runnable.run()` vẫn zero-arg, nhưng lambda **capture** `tenantId` trong closure:

~~~java
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class TenantScheduleRegistry implements DisposableBean {
    private final TaskScheduler scheduler;
    private final TenantPollingService worker;

    private final Map<String, ScheduledFuture<?>> schedules =
            new ConcurrentHashMap<>();

    public TenantScheduleRegistry(
            @Qualifier("taskScheduler") TaskScheduler scheduler,
            TenantPollingService worker) {
        this.scheduler = scheduler;
        this.worker = worker;
    }

    public void register(String verifiedTenantId) {
        schedules.computeIfAbsent(verifiedTenantId, tenant ->
                scheduler.scheduleWithFixedDelay(
                        () -> worker.poll(tenant),
                        Duration.ofSeconds(30)));
    }

    public void unregister(String tenantId) {
        ScheduledFuture<?> handle = schedules.remove(tenantId);
        if (handle != null) {
            handle.cancel(false);
        }
    }

    @Override
    public void destroy() {
        schedules.values().forEach(handle -> handle.cancel(false));
        schedules.clear();
    }
}
~~~

Đây là một **class tích hợp**, không phải toàn bộ chương trình: ứng dụng cần `TenantPollingService`, bean `taskScheduler` và logic xác minh `verifiedTenantId`. `worker.poll(tenant)` là **service bình thường nhận đối số**; scheduled callback thực sự là `() -> worker.poll(tenant)`, tức `Runnable.run()` không tham số.

Với nhiều tenant, mẫu này cần thêm giới hạn số lịch, cơ chế lưu và khôi phục đăng ký sau restart, xử lý concurrent register/unregister, lifecycle cancellation, số worker phù hợp và phân quyền. `ConcurrentHashMap.computeIfAbsent` chỉ đảm bảo mapping trong **một JVM**, không khóa giữa nhiều pod. Nếu số tenant rất lớn hoặc job phải survive restart, có thể tốt hơn dùng **một** scheduled scanner đọc danh sách tenant cần chạy từ DB thay vì tạo hàng nghìn schedules trong bộ nhớ.

Quan trọng: `scheduleWithFixedDelay` tính delay **sau khi Runnable hoàn tất**. Nếu `worker.poll(tenant)` chỉ dispatch một async job rồi trả về, completion của lịch là lúc **dispatch xong**, không phải lúc xử lý tenant thực sự xong. Để tránh overlap, cần thiết kế trạng thái task/lease có kiểm soát.

### 6.1. Dùng Java method reference khi không cần tham số

~~~java
// Snippet: scheduler và service là các object hợp lệ.
ScheduledFuture<?> handle =
        scheduler.scheduleWithFixedDelay(
                service::refreshAll,
                Duration.ofSeconds(15));
~~~

`service::refreshAll` là một method reference tương thích `Runnable` **khi `refreshAll()` không có tham số và trả void**. Nếu method đích cần `tenantId`, dùng lambda capture `() -> service.refreshTenant(tenantId)`; không tự thay đổi chữ ký `@Scheduled` để có tham số.

## 7. Trường hợp cần input biến thiên từ request, event hoặc message

Có hai câu hỏi cần tách:

- **Khi nào chạy?** Fixed delay, fixed rate, cron hoặc lịch đăng ký động.
- **Input của một lần chạy đến từ đâu?** HTTP request, message, DB queue, property, service dependency hay immutable job payload.

`@Scheduled` chỉ trả lời phần thời điểm, không tự tạo event/payload cho một lượt. Nếu input đến từ request cụ thể, có thể dùng một trong các thiết kế sau:

| Input đến từ | Nên chọn | Lý do |
| --- | --- | --- |
| Cấu hình cố định hoặc profile | Constructor injection / `@ConfigurationProperties` | Scheduled method vẫn no-arg |
| Trạng thái cập nhật trong DB | Scheduled scanner gọi repository rồi service nhận ID | Dữ liệu mỗi lượt là dữ liệu mới |
| Mỗi HTTP request có `reportId` riêng | API nhận request + service `@Async(reportId)` hoặc job queue | Lịch cố định không đại diện cho một request |
| Event bất đồng bộ | `@EventListener(Event)`/message consumer | Framework event/message biết payload |
| Lịch riêng theo tenant | `TaskScheduler` đăng ký `Runnable` closure | Mỗi registration nắm ID có chủ đích |
| Job quan trọng phải survive pod restart | DB job ledger / message broker + worker | Scheduled annotation không phải durable queue |
| Đọc input ngoài hệ thống định kỳ | `@Scheduled` no-arg gọi HTTP/DB service với timeout | Input được lấy khi task chạy |

Không copy `HttpServletRequest` hoặc mutable session object vào một singleton scheduled bean rồi đọc ở lượt sau. Scheduled job thường chạy trên thread không có request context. Nếu cần audit actor, tenant ID hay job owner, lưu dữ liệu đã xác thực trong payload/job store và kiểm tra quyền tại service boundary.

### 7.1. Tại sao không dùng @Scheduled ngay trên @EventListener có đối số?

~~~java
// Anti-pattern: trộn hai contract bất đồng bộ khác nhau.
@EventListener
@Scheduled(fixedDelay = 5000)
public void onOrderCreated(OrderCreatedEvent event) {
    process(event);
}
~~~

`@EventListener` có thể nhận một đối tượng event; `@Scheduled` yêu cầu method **không đối số**. Cùng một method không thỏa cả hai contract. Thiết kế tách riêng:

~~~java
// Snippet trong bean 1: event-driven.
@EventListener
public void onOrderCreated(OrderCreatedEvent event) {
    orderService.enqueue(event.id());
}

// Snippet trong bean 2: schedule-driven, no-arg.
@Scheduled(fixedDelay = 5000)
public void scanQueuedOrders() {
    orderService.processPending();
}
~~~

Những kiểu `OrderCreatedEvent`/`orderService` do dự án định nghĩa; các snippet không phải class độc lập. Cả hai cùng sử dụng một service xử lý với contract rõ, nhưng cơ chế kích hoạt khác nhau.

## 8. Spring 6.1+: reactive @Scheduled vẫn không được nhận đối số

Spring Framework 6.1 bổ sung hỗ trợ các method `@Scheduled` reactive trả về `Publisher` hoặc kiểu có thể được chuyển thành Publisher bằng `ReactiveAdapterRegistry` **khi adapter hỗ trợ deferred subscription**, cùng Kotlin suspending functions/`Flow`/`Deferred` khi đủ dependency.

**Quy tắc zero arguments vẫn giữ nguyên**, kể cả với reactive scheduled methods. Không được viết `@Scheduled public Mono<Void> poll(String tenant)` rồi mong Reactor tự inject tenant.

### 8.1. Ví dụ Reactive Publisher

~~~java
// Snippet Spring 6.1+ khi project có Project Reactor trên classpath.
@Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
public Mono<Void> refreshReactive() {
    // Mono.defer giữ công việc thực tế cho từng subscription.
    return Mono.defer(() ->
            Mono.fromRunnable(() -> catalogService.refresh()));
}
~~~

Đây là **snippet trong Spring-managed bean**, cần import `reactor.core.publisher.Mono`, `org.springframework.scheduling.annotation.Scheduled` và `java.util.concurrent.TimeUnit`; `catalogService` là dependency ứng dụng. Tài liệu Spring 6.1+ mô tả:

1. Spring lấy `Publisher` từ annotated method **một lần** khi thiết lập scheduling.
2. Mỗi lần đến hạn, scheduler tạo **một subscription mới** tới Publisher đó.
3. Các tín hiệu `onNext`/giá trị phát ra **bị bỏ qua**; không có caller nhận dữ liệu từng lượt.
4. Nếu Publisher phát `onError`, Spring ghi WARN và tiếp tục các đợt subscription sau; lỗi reactive không đi vào `ErrorHandler` của Runnable theo cách sync.
5. Khi dùng `fixedDelay` với reactive method, Spring phải chờ subscription hoàn tất để giữ đúng fixed-delay semantics. Với Publisher không kết thúc, lần sau có thể không được kích hoạt như mong muốn.

Vì Publisher được tạo một lần, hãy dùng dạng **lạnh/deferred, có thể subscribe lại** nếu cần thực thi thao tác ở mỗi tick. Nếu code chạy `catalogService.refresh()` ngay khi tạo Publisher rồi chỉ trả giá trị đã cache, việc đó có thể thực thi ở lúc đăng ký, **không phải mỗi lần đến hạn**.

`CompletableFuture` tuy có thể được adapter chuyển sang Publisher trong một số API nhưng adapter đó **không hỗ trợ deferred subscription**; đừng coi trả trực tiếp một `CompletableFuture` từ `@Scheduled` là cách đúng để Spring chờ một lần xử lý async mới ở mỗi tick. Nếu muốn reactive scheduled job, dùng một Publisher đúng điều kiện; nếu muốn job thường, dùng `void` và chủ động quản lý Future/job status.

### 8.2. Kotlin suspend functions

Trong Spring 6.1+, Kotlin `suspend fun` được hỗ trợ với coroutine/reactor bridge `kotlinx-coroutines-reactor` trên classpath. Chữ ký ở mã nguồn vẫn là **không nhận tham số nghiệp vụ**:

~~~kotlin
@Scheduled(fixedDelay = 5000)
suspend fun refreshFromPartner() {
    partnerService.refresh()
}
~~~

Đây là **snippet Kotlin**, không thuộc project Java 17 ở mục 5. Mặc dù compiler Kotlin dùng continuation nội bộ khi biên dịch một suspending function, đó **không phải giấy phép** cho developer khai báo thêm `tenantId`/`requestId` vào tham số của method `@Scheduled`.

## 9. Tránh hiểu nhầm về tham số, kết quả và thời điểm hoàn tất

### 9.1. Fixed delay đếm từ lúc method hoàn tất, không phải lúc job con xong

~~~java
// Snippet trong @Component:
@Scheduled(fixedDelay = 5000)
public void trigger() {
    reportService.generateAsync("daily");
}
~~~

Nếu `generateAsync("daily")` là `@Async` method được gọi qua Spring proxy, `trigger()` có thể trả về ngay sau dispatch. Với fixed delay, **lượt scheduled invocation** đã hoàn tất tại thời điểm đó; nó không tự đợi business task hoàn tất trong executor khác. Các job business có thể overlap dù scheduled callback không nhận tham số.

Nếu yêu cầu phải “làm xong report rồi mới nghỉ 5 giây”, dùng workflow với completion boundary rõ, timeout, tránh blocking scheduler worker vô hạn; hoặc dùng job state/lease để không dispatch khi lượt trước chưa terminal.

### 9.2. Fixed rate / cron cũng không cấp đối số

~~~java
@Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
public void pullEveryTenSeconds() { }

@Scheduled(cron = "0 0/5 * * * *", zone = "Asia/Ho_Chi_Minh")
public void runEveryFiveMinutes() { }
~~~

Hai phương thức đều không tham số. Scheduler chọn **thời điểm**, không quyết định tham số đầu vào. Muốn biết thời gian hiện tại, inject `Clock` hoặc gọi một API time thích hợp bên trong method, thay vì viết `void run(Instant scheduledAt)` và mong Spring cung cấp `Instant`.

Một method scheduled có thể được khai báo hai `@Scheduled` (repeatable annotation) hoặc hai bean instance có cùng annotation. Mỗi registration hoạt động riêng; các invocation có thể đè lên nhau hoặc chạy sát nhau. Không có tham số không đồng nghĩa singleton job không thể overlap giữa registrations hoặc giữa nhiều pod.

### 9.3. Return void nhưng lỗi phải được quan sát

Một periodic method trả `void` không có Future trả về cho HTTP caller, nên lỗi cần log/metric hoặc cập nhật job state. Spring thông thường có `ErrorHandler` cho repeating tasks, có thể ghi log và suppress exception để các lượt sau vẫn chạy. Đây là **behavior phải xác minh theo scheduler/handler thực**, không bảo đảm mọi lỗi sẽ dừng job.

~~~java
// Snippet minh họa bên trong scheduled bean.
@Scheduled(fixedDelay = 5000)
public void reconcile() {
    long started = System.nanoTime();
    try {
        reconciliationService.reconcile();
        metrics.success();
    } catch (RuntimeException ex) {
        metrics.failure();
        logger.error("Reconciliation iteration failed", ex);
        // Có thể rethrow nếu error policy phía scheduler được thiết kế rõ.
        throw ex;
    } finally {
        metrics.durationNanos(System.nanoTime() - started);
    }
}
~~~

`reconciliationService`, `metrics` và `logger` là dependencies giả định; đoạn code không độc lập. `finally` ghi duration dù thành công hay lỗi. Không tự catch `Exception` rồi nuốt hết để bên ngoài luôn tưởng job thành công. Với job bền vững, lưu trạng thái `SUCCEEDED`/`FAILED` ở DB hoặc monitoring, không chỉ dựa vào console.

## 10. Scheduler, thread và context thực thi

### 10.1. Scheduler bean không phải một tham số method

`@Scheduled` được đăng ký với một `TaskScheduler` hoặc `ScheduledExecutorService` phù hợp. Spring có quy tắc tìm scheduler, Spring Boot có thể auto-config scheduler tùy điều kiện, và ứng dụng cũng có thể khai báo `ThreadPoolTaskScheduler` như trong demo.

Không cần viết:

~~~java
// Sai: scheduler không tự inject ở mỗi tick qua tham số.
@Scheduled(fixedDelay = 1000)
void poll(TaskScheduler scheduler) { }
~~~

Nếu bean cần quản lý lịch programmatic, inject `TaskScheduler` vào **constructor của bean**, không đưa vào tham số scheduled callback. Khi có nhiều schedulers, dùng `@Qualifier` để tránh lựa chọn mơ hồ. Spring Framework 6.1+ còn có attribute `scheduler` trên `@Scheduled` để chọn scheduler bean/qualifier cụ thể; đó vẫn là **annotation attribute**, không phải Java method parameter.

### 10.2. @Scheduled chạy ngoài request thread

Không được mặc định:

- `RequestContextHolder` có `HttpServletRequest` hiện hành.
- `SecurityContextHolder` mang principal của user gần nhất.
- Transaction của HTTP caller đi theo scheduled worker.
- `MDC`/trace ID tự có sẵn giá trị của một request cụ thể.

Lịch chạy là một hoạt động của ứng dụng, không đại diện cho một HTTP request. Nếu job cần quyền đặc biệt, dùng service identity/principal được thiết kế rõ, authorization ở tầng service/DB và audit actor; không đọc lại security context của task trước trên worker.

Nếu job cần database transaction, mở transaction trong code nghiệp vụ được scheduler gọi qua Spring service proxy, ví dụ `reconciliationService.reconcile()` được đặt `@Transactional` ở bean khác. Đừng truyền `EntityManager`/connection/request object xuyên thread.

### 10.3. Nhiều pod không có lịch toàn cục duy nhất

Nếu ứng dụng có ba instance, mỗi instance có thể đăng ký `@Scheduled` và chạy job của mình. Quy tắc zero arguments không liên quan tới tính exactly-once; để chỉ một worker toàn cụm xử lý cùng dữ liệu, cần lock/lease/DB claim, message broker hoặc scheduler tập trung, cùng idempotency.

## 11. Các lỗi phổ biến và cách sửa

| Hiện tượng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Startup fail khi dùng `@Scheduled void poll(String id)` | Scheduled processor yêu cầu zero arguments | Chuyển tham số vào service, inject dependency hoặc đăng ký TaskScheduler lambda |
| Tưởng `@Autowired` sẽ inject vào tham số scheduled method | Không có argument resolver cho scheduled callback | Constructor injection vào bean |
| Muốn Spring truyền current `Instant` cho method | Scheduler chỉ gọi callback zero-arg | Inject `Clock`/tự lấy thời gian trong method |
| Scheduled method trả `String` nhưng không ai nhận | Return value của method đồng bộ bị ignore | Dùng `void`; ghi metric/DB/log hoặc gọi service có return |
| `CompletableFuture` trả về nhưng scheduler không đợi việc thực | CompletableFuture không là deferred Publisher thích hợp | Dùng reactive Publisher hợp lệ hoặc quản lý completion explicit |
| @Scheduled không chạy | Chưa bật `@EnableScheduling`/bean không do Spring quản lý | Bật scheduling trong đúng context và kiểm tra bean |
| Cứ mỗi 5s nhưng job chưa xong đã có job mới | Scheduled callback chỉ dispatch sang async worker | Định nghĩa business completion và giới hạn overlap |
| Cron chạy nhiều lần trên nhiều pod | Mỗi instance tự đăng ký job | Distributed coordination và idempotency |
| Task lúc chạy không có request/user context | Không phải HTTP request thread | Truyền ID đã xác thực, dùng service identity |
| Có nhiều scheduler beans, chạy sai pool | Lựa chọn scheduler mơ hồ | Qualifier/`scheduler` attribute, audit runtime |
| Một method chứa hai @Scheduled annotations | Hai registrations độc lập | Kiểm tra lặp annotation, concurrency và job lock |
| Reactive method không chạy lại logic mỗi tick | Publisher được tạo một lần, logic không deferred | Dùng cold/deferred Publisher với tác vụ trong subscription |
| Lỗi một lượt không dừng lịch như tưởng | ErrorHandler lặp có thể suppress | Ghi metric, chọn error policy và job state rõ |

### Checklist review

1. Phương thức mang `@Scheduled` có **đúng zero parameters** (kể cả reactive/suspend)?
2. Có `@EnableScheduling` trên đúng context và bean do Spring quản lý?
3. Có chọn đúng `fixedDelay`, `fixedRate`, `cron`, `initialDelay`, đơn vị thời gian và timezone?
4. Dữ liệu đầu vào đến từ constructor injection, service/repository, configuration hay lambda capture, **không phải method parameter**?
5. Method sync trả `void`; nếu trả dữ liệu thì có nơi lưu/quan sát, không trông cậy scheduler đọc return value?
6. Nếu reactive, Publisher có deferred/multiple subscriptions và được xử lý lỗi đúng contract?
7. Nếu callback dispatch async, completion boundary có được xác định rõ cho fixed delay?
8. Có xử lý lỗi, timeout, retry và health/metrics cho từng lần chạy?
9. Có quản lý tenant/security context và transaction đúng worker thread?
10. Có chống trùng job giữa nhiều pod/registrations nếu nghiệp vụ yêu cầu?
11. Với lịch đăng ký động, ai giữ và cancel `ScheduledFuture<?>`, khôi phục registrations sau restart?
12. Có test startup của chữ ký sai và test behavior của method no-arg hợp lệ?

## 12. Câu hỏi tự kiểm tra

1. Vì sao `@Scheduled(fixedRate = 5000) void poll(String id)` không hợp lệ, dù Java compiler chấp nhận method này?
2. `@Value` đặt ở constructor khác gì đặt trên tham số của chính `@Scheduled` method?
3. Cần poll từng tenant trong DB. Vì sao một scheduled scanner no-arg có thể xử lý các ID mà không cần sửa chữ ký?
4. Khi cần một lịch riêng cho `tenant-A`, cách dùng `TaskScheduler` với lambda capture hoạt động thế nào?
5. `@Async` có thể nhận `reportId`, tại sao `@Scheduled` lại không?
6. Spring có tự dùng return value `int` từ scheduled method để gửi cho controller không?
7. Reactive `@Scheduled` trên Spring 6.1+ có được nhận `String tenantId` không? Publisher nên có đặc tính gì?
8. Một `@Scheduled(fixedDelay = ...)` gọi service async rồi trả về ngay. Delay tính từ khi công việc nghiệp vụ xong hay khi callback scheduler xong?
9. Vì sao trên ba pod có thể xuất hiện ba lần xử lý một job vào cùng thời điểm, dù method không có tham số?
10. Khi nào nên dùng `@EventListener` hoặc message consumer thay vì cố nhận dữ liệu trên scheduled callback?

**Kết luận:** `@Scheduled` là lời hẹn của **Spring scheduling infrastructure** để gọi một method **không nhận đối số** tại những thời điểm đã đăng ký. Hãy giữ callback no-arg, inject dependencies vào bean và gọi service có tham số **từ bên trong**; nếu input cần gắn riêng từng lịch, dùng `TaskScheduler` với `Runnable`/lambda capture hoặc một job store. Dữ liệu đầu vào, kết quả, lỗi, concurrency và lifecycle vẫn phải được thiết kế rõ, không thể nhờ scheduler tự suy ra argument từ request hay application context.

## Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling, @Scheduled](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework 6.2 — @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework 6.2 — @EnableScheduling Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html)
- [Spring Framework 6.2 — TaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/TaskScheduler.html)
- [Spring Framework — Annotation-driven reactive scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-reactive)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-tasks-003">fixedDelay đo khoảng cách từ lần chạy trước hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# fixedDelay đo khoảng cách từ lần chạy trước hoàn tất

## 1. Khái niệm cốt lõi của @Scheduled(fixedDelay)

Trong Spring Framework, `@Scheduled` cho phép thực thi định kỳ một phương thức trên Spring-managed bean. Khi chọn `fixedDelay`, khoảng thời gian được tính **từ lúc lượt chạy trước hoàn tất cho tới lúc lượt tiếp theo bắt đầu**. Đây không phải khoảng cách cố định giữa hai lần bắt đầu.

```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupJob {
    @Scheduled(fixedDelay = 5000)
    public void clean() {
        System.out.println("Dọn dữ liệu...");
    }
}
```

Đây là **Spring component**, không phải file Java có `main` độc lập; ứng dụng cần bật `@EnableScheduling` trong context. Giá trị `5000` mặc định tính bằng **mili giây**, tức 5 giây. Nếu một lượt `clean()` xử lý 3 giây, thì trong điều kiện scheduler rảnh, lượt tiếp theo bắt đầu khoảng 8 giây sau lần bắt đầu trước: 3 giây chạy + 5 giây nghỉ.

Sơ đồ:

```text
t0                           t1                         t2
|------ RUN lần thứ n --------|------ fixedDelay --------|-- RUN n+1
               3 giây                    5 giây
t2 - t0 xấp xỉ 8 giây, không phải 5 giây
```

**Nhớ một câu:** `fixedDelay` = *khoảng nghỉ sau khi một lần thực thi đã kết thúc*. Nếu lượt trước chậm, lượt sau được đẩy muộn tương ứng; không tự có cơ chế “chạy bù” mọi mốc đã trôi qua.

### Mục tiêu học tập

Bài này giúp bạn:

- Mô tả chính xác timeline của `fixedDelay` và phân biệt `fixedRate`.
- Cấu hình `@Scheduled` với `fixedDelay`, `initialDelay`, `timeUnit`, `fixedDelayString` và scheduler bean.
- Đọc một kết quả đo bằng `System.nanoTime()`, `CountDownLatch` với bài Java 17 chạy được.
- Hiểu “hoàn tất” là **phương thức mà scheduler quan sát**, không tự động bao gồm việc đã gửi sang background executor.
- Đánh giá tác động của blocking I/O, lỗi, reactive, virtual threads, transaction và nhiều pod.
- Kiểm thử, quan sát và quản lý lifecycle để không hiểu sai khoảng nghỉ.

## 2. Công thức và timeline chính xác

Đặt:

- `Sₙ` là lúc bắt đầu lượt thứ `n`.
- `Eₙ` là lúc lượt đó kết thúc.
- `Rₙ = Eₙ − Sₙ` là thời gian thực thi.
- `D` là độ dài `fixedDelay`.

Lượt kế tiếp sớm nhất đủ điều kiện chạy sau:

\[
S_{n+1} \geq E_n + D
\]

Nếu scheduler không bị trì hoãn thêm:

\[
S_{n+1} - S_n \approx R_n + D
\]

Dấu lớn hơn hoặc bằng đặc biệt quan trọng: Spring không hứa một task sẽ được CPU thực thi chính xác tại mili giây đủ điều kiện. Worker bận, GC pause, contention hoặc hệ thống bị quá tải có thể khiến bắt đầu **muộn hơn**.

Giả sử `D = 5 giây`, hệ thống không có trì hoãn bên ngoài:

| Lượt | Bắt đầu | Kết thúc | Nghỉ 5 giây sau kết thúc | Lượt sau bắt đầu |
|---|---|---|---|---|
| 1 | 09:00:00 | 09:00:02 | 09:00:02–09:00:07 | 09:00:07 |
| 2 | 09:00:07 | 09:00:19 | 09:00:19–09:00:24 | 09:00:24 |
| 3 | 09:00:24 | 09:00:25 | 09:00:25–09:00:30 | 09:00:30 |

Bảng trên là **ví dụ lý tưởng**, không phải các thời điểm đã được một JVM thực tế bảo đảm. Lượt 2 chạy 12 giây thì khoảng cách giữa **hai lần bắt đầu** là 17 giây; delay vẫn là 5 giây từ lúc kết thúc lần trước.

### 2.1. Lượt chạy lâu hơn delay

Với `fixedDelay = 2 giây` nhưng logic cần 20 giây, khoảng giữa các lần bắt đầu gần 22 giây nếu không bị trễ thêm. Không có 10 lượt mới cùng lúc được khởi chạy để bù cho thời gian xử lý dài.

### 2.2. Lượt chạy rất nhanh

Nếu logic chỉ tốn 10 mili giây và `fixedDelay = 2 giây`, khoảng giữa hai lần bắt đầu xấp xỉ 2,01 giây. Vì thế giảm thời gian xử lý **có thể làm tần suất bắt đầu cao hơn** dù không thay đổi cấu hình delay.

### 2.3. Completion là mốc nào?

Mốc `Eₙ` là lúc **invocation của scheduled task được xem là hoàn tất bởi scheduler**. Nếu method chỉ gửi việc vào executor khác rồi trả về, mốc này đến trước lúc công việc bên ngoài xong. Đây là một ngoại lệ cực kỳ quan trọng, sẽ phân tích ở mục 12.

## 3. fixedDelay so với fixedRate và cron

Ba cơ chế không thể dùng thay nhau tùy tiện:

| Tiêu chí | `fixedDelay` | `fixedRate` | `cron` |
|---|---|---|---|
| Mốc đo | Sau khi lượt trước hoàn tất | Theo nhịp thời điểm bắt đầu được lên lịch | Thời điểm theo biểu thức lịch |
| Khi một lượt chậm | Lượt sau chờ hoàn tất + delay | Lượt sau có thể trễ so với mốc định kỳ | Phụ thuộc trigger/scheduler và lịch |
| Mục đích | Nghỉ giữa hai lần xử lý | Cố duy trì cadence đều | Chạy theo giờ/ngày/múi giờ |
| Có bảo đảm chính xác giờ thực? | Không | Không | Không tuyệt đối |
| Ví dụ | Poll API có cooldown | Thu thập mẫu đều | Báo cáo lúc 06:00 mỗi ngày |

Ví dụ:

```java
@Scheduled(fixedDelay = 5000)
public void pollAfterRest() {
    // 5 giây tính từ lúc lần trước trả về.
}

@Scheduled(fixedRate = 5000)
public void sampleAtRate() {
    // Nhịp mục tiêu 5 giây giữa các thời điểm bắt đầu.
}
```

Hai phương thức trên là **mảnh code trong Spring bean**, không phải chương trình độc lập. Nếu thời gian chạy của lần đầu là 8 giây:

```text
fixedDelay = 5s:
RUN #1 00s -------- 08s -> nghỉ 5s -> RUN #2 sớm nhất 13s

fixedRate = 5s:
RUN #1 00s -------- 08s
Lịch nhắm tới lượt tiếp ở 05s nhưng lượt cũ chưa xong;
RUN #2 có thể bắt đầu ngay sau 08s trên scheduler phù hợp.
```

Với `ScheduledThreadPoolExecutor`/`ThreadPoolTaskScheduler` truyền thống, các lượt **của cùng một periodic task registration** không được chạy đồng thời. Tuy nhiên hai lịch độc lập, các bean khác nhau, hai annotation `@Scheduled` lặp lại trên cùng method, hoặc hai JVM khác nhau vẫn có thể tạo các lần chạy chồng lên nhau.

Cron như `@Scheduled(cron = "0 */5 * * * *")` là lịch ở những mốc phút chia hết cho 5, không có nghĩa “luôn chờ 5 phút sau khi xử lý hoàn tất”.

## 4. Cú pháp và đơn vị thời gian

Mặc định, `fixedDelay` tính bằng mili giây:

```java
@Scheduled(fixedDelay = 5000)
public void refresh() {
    // Nghỉ 5000 ms = 5 s sau completion.
}
```

Có thể ghi rõ đơn vị:

```java
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;

@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
public void refresh() {
    // Cùng ngữ nghĩa với fixedDelay = 5000.
}
```

Lỗi điển hình: `fixedDelay=5` bị tưởng là 5 giây, nhưng mặc định là 5 mili giây. Ngược lại, `fixedDelay=5000, timeUnit=SECONDS` vô tình tạo khoảng nghỉ hơn một giờ.

### 4.1. initialDelay chỉ dành cho lần đầu

`initialDelay` đo từ khi schedule được đăng ký đến lần đầu **đủ điều kiện bắt đầu**, không được cộng lại ở các lượt sau:

```java
@Scheduled(
    initialDelay = 10,
    fixedDelay = 5,
    timeUnit = TimeUnit.SECONDS
)
public void synchronize() {
    // Lần đầu sau khoảng 10s; các lần sau nghỉ 5s sau completion.
}
```

`initialDelay` không được hiểu là delay “mỗi lượt”. Nếu không khai báo initial delay trong một lịch fixed-delay thông thường, lần đầu được lên lịch thực hiện ngay khi scheduler có thể.

### 4.2. fixedDelayString cho cấu hình ngoài code

Có thể đọc độ dài từ Spring Environment thay vì hard-code:

```properties
app.cleanup.delay=PT5S
app.cleanup.initial=PT10S
```

```java
@Scheduled(
    fixedDelayString = "${app.cleanup.delay:PT5S}",
    initialDelayString = "${app.cleanup.initial:PT10S}"
)
public void cleanup() {
    // Spring resolve placeholder thành Duration khi đăng ký lịch.
}
```

Sau khi thay placeholder chuẩn, annotation trên dùng cú pháp Spring `$` + `{...}`. `PT5S` là 5 giây, `PT10S` là 10 giây theo ISO-8601. Trong Spring Framework 6.2, `fixedDelayString` còn chấp nhận một số duration string có đơn vị và expression/placeholder theo contract của annotation. Khi chuỗi đã có đơn vị, đừng nhân thêm `timeUnit` bằng suy đoán.

Trong Spring Boot, `application.properties` được hỗ trợ theo hệ thống cấu hình Boot. Với Spring Framework thuần, chỉ khai báo file `application.properties` ở đâu đó **không bảo đảm** Spring tự đọc nó; cần property source/Environment phù hợp.

**Không khai báo cả `fixedDelay` và `fixedRate` trên cùng một annotation periodic**, và không dùng cả `fixedDelay` lẫn `fixedDelayString` như hai giá trị của cùng lịch.

### 4.3. Cấu hình có tự thay đổi ngay ở runtime không?

Giá trị placeholder thường được resolve khi Spring đăng ký lịch. Sửa property file trong lúc ứng dụng chạy **không tự buộc mọi schedule đã đăng ký lấy lại delay**. Nếu cần thay đổi thời gian ngay, phải có cơ chế refresh/re-register, hoặc dùng `TaskScheduler` programmatic/custom `Trigger`.

## 5. Bật @Scheduled: EnableScheduling, bean và context

Cách bật Java Configuration:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

Một method `@Scheduled` phải nằm trong bean do Spring quản lý; `ScheduledAnnotationBeanPostProcessor` đăng ký và điều phối lịch. Nó không yêu cầu một controller tự gọi method để bắt đầu chạy định kỳ.

Khác với `@Async`:

- `@EnableScheduling` bật xử lý annotation `@Scheduled`.
- `@EnableAsync` bật xử lý annotation `@Async`.
- Không cần `@EnableAsync` nếu ứng dụng chỉ dùng `@Scheduled` đồng bộ.
- `@Scheduled` thường không được khai báo tham số; phương thức đồng bộ thường trả `void`, dữ liệu trả về nếu có không được giao cho caller.
- Với các biến thể reactive được Spring 6.1+ hỗ trợ có những quy tắc khác; xem mục 13.

Với ứng dụng có nhiều `ApplicationContext`, hãy bật scheduling đúng context chứa bean. Nếu tạo bean trùng hoặc gắn nhiều `@Scheduled` lên cùng method, có thể có nhiều đăng ký độc lập mà developer không để ý.

### 5.1. Một scheduled method không đồng nghĩa luôn chỉ có một lịch ở cả cluster

Mỗi instance Spring thường đăng ký lịch của mình. Khi có 3 pod cùng chạy ứng dụng, sẽ có 3 scheduled registrations (trừ khi ứng dụng có cơ chế điều phối khác). Không có distributed lock hoặc “exactly once” tự động từ `fixedDelay`.

## 6. Cấu hình ThreadPoolTaskScheduler và chọn đúng scheduler bean

`ThreadPoolTaskScheduler` dựa trên `ScheduledThreadPoolExecutor`, có thể chạy nhiều **lịch độc lập** trên các worker. Ví dụ:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class MaintenanceSchedulerConfig {
    @Bean("maintenanceScheduler")
    public ThreadPoolTaskScheduler maintenanceScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("maintenance-");
        return scheduler;
    }
}
```

Đây là Spring configuration class cần context, không có `main` độc lập. Spring 6.1+ hỗ trợ thuộc tính `scheduler` trong `@Scheduled` để xác định bean/qualifier của `TaskScheduler`:

```java
@Scheduled(
    fixedDelay = 5,
    timeUnit = TimeUnit.SECONDS,
    scheduler = "maintenanceScheduler"
)
public void compact() {
    // Dùng đúng scheduler bean đã khai báo.
}
```

Một lịch fixed-delay của method trên không tự tạo 2 lượt chạy song song chỉ vì pool size=2; worker thứ hai giúp lịch độc lập khác không bị chờ nếu còn tài nguyên. Nếu scheduler chỉ một worker và một job giữ thread chờ I/O rất lâu, các lịch khác có thể bị trễ.

**Không tự gọi `shutdown()` của scheduler bean trong mỗi lượt job.** Scheduler là tài nguyên dùng chung do Spring quản lý theo lifecycle; muốn hủy riêng lịch động phải giữ `ScheduledFuture<?>` của API programmatic phù hợp.

## 7. Ví dụ Java 17 chạy độc lập: đo khoảng nghỉ tối thiểu

Để kiểm chứng `fixedDelay` một cách **không phụ thuộc `Thread.sleep` để đoán tác vụ đã chạy xong**, dùng `CountDownLatch` để kiểm soát hai lượt chạy và `System.nanoTime()` để đo khoảng cách.

Đây là Java SE `ScheduledExecutorService.scheduleWithFixedDelay(...)`, không phải Spring, nhưng dùng cùng nguyên lý lập lịch.

**Tệp `FixedDelayJavaDemo.java`:**

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FixedDelayJavaDemo {

    public static void main(String[] args)
            throws InterruptedException {

        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch finishFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch finishSecond = new CountDownLatch(1);
        CountDownLatch secondExited = new CountDownLatch(1);

        AtomicInteger runs = new AtomicInteger();
        AtomicLong firstCompletedAt = new AtomicLong();
        AtomicBoolean correctGap = new AtomicBoolean();

        ScheduledFuture<?> schedule =
                scheduler.scheduleWithFixedDelay(() -> {
                    int n = runs.incrementAndGet();
                    long startedAt = System.nanoTime();
                    try {
                        if (n == 1) {
                            firstStarted.countDown();
                            finishFirst.await();
                        } else if (n == 2) {
                            long gap = startedAt -
                                    firstCompletedAt.get();

                            correctGap.set(gap >=
                                    TimeUnit.MILLISECONDS.toNanos(200));

                            secondStarted.countDown();
                            finishSecond.await();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        if (n == 1) {
                            firstCompletedAt.set(System.nanoTime());
                        }
                        if (n == 2) {
                            secondExited.countDown();
                        }
                    }
                }, 0, 200, TimeUnit.MILLISECONDS);

        try {
            if (!firstStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Lượt 1 chưa chạy");
            }
            System.out.println("Lượt 1 đã bắt đầu: true");

            finishFirst.countDown();

            if (!secondStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Lượt 2 chưa chạy");
            }
            System.out.println("Nghỉ sau lần 1 >= 200 ms: "
                    + correctGap.get());

            System.out.println("Hủy lịch: " + schedule.cancel(false));

            finishSecond.countDown();
            if (!secondExited.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Lượt 2 chưa thoát");
            }

            System.out.println("Số lượt bắt đầu: " + runs.get());
        } finally {
            finishFirst.countDown();
            finishSecond.countDown();
            schedule.cancel(true);
            scheduler.shutdownNow();
        }
    }
}
```

Biên dịch và chạy với JDK 17:

```bash
javac FixedDelayJavaDemo.java
java FixedDelayJavaDemo
```

Đầu ra khi scheduler được chạy bình thường:

```text
Lượt 1 đã bắt đầu: true
Nghỉ sau lần 1 >= 200 ms: true
Hủy lịch: true
Số lượt bắt đầu: 2
```

Giải thích:

1. `firstStarted.await(...)` bảo đảm worker đã bắt đầu lượt đầu.
2. `finishFirst.countDown()` cho phép lượt một thoát; `firstCompletedAt` được ghi trong `finally` ngay trước khi Runnable kết thúc.
3. Với fixed-delay, lượt hai chỉ được bắt đầu sau khi lượt đầu đã kết thúc **và** ít nhất 200 ms nữa trôi qua.
4. `correctGap` so `startedAt` của lượt hai với `firstCompletedAt`, không so hai mốc bắt đầu.
5. Lượt hai bị giữ bằng `finishSecond.await()`, để main kịp gọi `cancel(false)` rồi thả latch; vì thế không có lượt ba.
6. `cancel(false)` không bắt buộc interrupt lượt hai đang chạy; sau khi main cho phép, lượt hai mới kết thúc.
7. `System.nanoTime()` phù hợp để đo khoảng thời gian; `System.currentTimeMillis()` dễ chịu ảnh hưởng chỉnh đồng hồ.
8. Những `await(5, TimeUnit.SECONDS)` khiến chương trình **báo timeout** nếu worker quá chậm, thay vì treo mãi hoặc in kết quả không chắc chắn.

Mốc `firstCompletedAt` được chụp trước khi Runnable thực sự return, nên điều kiện `gap >= 200ms` là một kiểm tra một phía hợp lệ. Không nên assert `gap == 200ms`, vì scheduler có thể chạy muộn hơn.

## 8. Ví dụ Maven Spring hoàn chỉnh: @Scheduled(fixedDelay) thật

Ví dụ này chạy trên **Java 17 + Spring Framework 6.2.7**, dùng một `ThreadPoolTaskScheduler` xác định. Lượt một bị giữ bởi latch, lượt hai đo khoảng cách từ completion của lượt một; main đóng `ApplicationContext` sau khi kiểm chứng.

### 8.1. Cấu trúc dự án

```text
spring-fixed-delay-demo/
├── pom.xml
└── src/main/java/lesson/SpringFixedDelayDemo.java
```

### 8.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lesson</groupId>
    <artifactId>spring-fixed-delay-demo</artifactId>
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

### 8.3. src/main/java/lesson/SpringFixedDelayDemo.java

```java
package lesson;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class SpringFixedDelayDemo {

    public static void main(String[] args)
            throws InterruptedException {

        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class)) {

            Job job = context.getBean(Job.class);

            try {
                if (!job.firstStarted.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Chưa thấy lượt 1");
                }
                System.out.println("Lượt 1 chạy: true");

                job.releaseFirst.countDown();

                if (!job.secondStarted.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Chưa thấy lượt 2");
                }

                System.out.println("Delay sau lần 1 >= 200 ms: "
                        + job.gapOk.get());
                System.out.println("Số lượt bắt đầu: "
                        + job.runs.get());
            } finally {
                job.releaseFirst.countDown();
                job.releaseSecond.countDown();
            }
        }
    }

    @Configuration
    @EnableScheduling
    public static class Config {

        @Bean("lessonScheduler")
        public ThreadPoolTaskScheduler lessonScheduler() {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("fixed-delay-");
            return scheduler;
        }

        @Bean
        public Job job() {
            return new Job();
        }
    }

    public static class Job {
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final CountDownLatch secondStarted = new CountDownLatch(1);
        final CountDownLatch releaseSecond = new CountDownLatch(1);

        final AtomicInteger runs = new AtomicInteger();
        final AtomicLong firstCompletedAt = new AtomicLong();
        final AtomicBoolean gapOk = new AtomicBoolean();

        @Scheduled(
            fixedDelay = 200,
            initialDelay = 0,
            scheduler = "lessonScheduler"
        )
        public void run() {
            int n = runs.incrementAndGet();
            long startedAt = System.nanoTime();

            try {
                if (n == 1) {
                    firstStarted.countDown();
                    releaseFirst.await();
                } else if (n == 2) {
                    long elapsed = startedAt -
                            firstCompletedAt.get();

                    gapOk.set(elapsed >=
                            TimeUnit.MILLISECONDS.toNanos(200));

                    secondStarted.countDown();
                    releaseSecond.await();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (n == 1) {
                    firstCompletedAt.set(System.nanoTime());
                }
            }
        }
    }
}
```

### 8.4. Lệnh chạy và stdout

```bash
mvn -q compile exec:java -Dexec.mainClass=lesson.SpringFixedDelayDemo
```

Đầu ra từ chương trình khi việc lên lịch đáp ứng timeout 5 giây:

```text
Lượt 1 chạy: true
Delay sau lần 1 >= 200 ms: true
Số lượt bắt đầu: 2
```

`@EnableScheduling` làm Spring đăng ký method `Job.run()`; thuộc tính `scheduler` chọn bean `lessonScheduler`. Method là đồng bộ, nghĩa là khi nó đang đợi `releaseFirst`, Spring không được xem lượt đầu đã hoàn tất. Sau khi method return, scheduler chờ khoảng 200ms trước khi chạy lượt hai. Lượt hai đợi `releaseSecond` để main in số lượt đúng 2; cuối cùng context đóng và Spring dừng scheduler theo vòng đời.

Các ví dụ ở mục 7–8 là **bài kiểm chứng logic thời gian**, không phải benchmark. Trên máy bị quá tải, timeout có thể khiến chương trình thất bại có chủ đích. Bài không hứa thời gian chính xác 200ms, chỉ kiểm tra rằng không có lượt hai **sớm hơn** mốc đủ điều kiện.

## 9. Thời gian chạy thay đổi làm nhịp bắt đầu thay đổi

Bảng dưới đây giả định scheduler có worker rảnh và không có jitter đáng kể:

| Thời gian chạy một lượt | `fixedDelay` | Khoảng bắt đầu đến bắt đầu xấp xỉ |
|---|---|---|
| 100 ms | 1.000 ms | 1.100 ms |
| 500 ms | 1.000 ms | 1.500 ms |
| 3.000 ms | 1.000 ms | 4.000 ms |
| 20.000 ms | 1.000 ms | 21.000 ms |

Nếu cần “chờ tối thiểu 30 giây sau khi kết thúc poll API”, `fixedDelay = 30s` phù hợp. Nếu cần chạy đúng 00:00 mỗi ngày, nên nghĩ tới cron. Nếu cần nhịp đo đều, xem xét `fixedRate` với hiểu biết về việc có thể trễ khi tác vụ chạy lâu.

`fixedDelay` không phải rate limiter phân tán. Mỗi pod tự poll sau delay riêng, nên tổng lượng request có thể bằng số pod nhân lên; cần giới hạn ở cấp hệ thống nếu downstream có quota chung.

## 10. Completion của scheduled method không phải completion của mọi việc nó gửi đi

Phản ví dụ:

```java
@Scheduled(fixedDelay = 5000)
public void scheduleMoreWork() {
    backgroundExecutor.execute(() -> slowHttpCall());
}
```

Mảnh code này giả định `backgroundExecutor` và `slowHttpCall()` đã tồn tại trong class. Method `scheduleMoreWork()` hoàn tất **sau khi việc gửi task trả về**, không bắt buộc sau khi `slowHttpCall()` trên worker khác kết thúc.

Nếu việc HTTP chạy 30 giây và scheduled method chỉ mất 1ms để gửi, các lần gọi schedule cách nhau gần 5 giây **có thể tạo nhiều tác vụ còn chờ hoặc đang chạy**. Đây không phải lỗi của fixedDelay; scheduler đo đúng completion của method đã đăng ký.

Khi yêu cầu là “đợi 5 giây sau khi toàn bộ công việc nghiệp vụ hoàn tất”, cần thiết kế tín hiệu completion phù hợp: giữ công việc trong scheduled invocation có timeout, hoặc điều phối tiếp lượt sau bằng `CompletableFuture` callback/`TaskScheduler` programmatic, tùy khả năng chặn và kiến trúc. Không dùng một lời gọi `execute()` rồi cho rằng scheduler sẽ theo dõi các side effect phía sau.

### 10.1. Đặt @Async cùng @Scheduled có thể thay đổi completion quan sát được

`@Async` dùng hạ tầng chuyển method sang executor khác qua proxy. Nếu kết hợp `@Async` và `@Scheduled` trên một đường gọi, method có thể trả quyền điều khiển trước khi I/O trên worker xong. Ngữ nghĩa thời điểm hoàn tất mà scheduler thấy cần được **kiểm chứng trong đúng phiên bản Spring, advice ordering và executor**.

Đừng kết luận “fixedDelay luôn đợi background job” chỉ vì annotation nằm trên một method có `@Async`. Một thiết kế dễ hiểu thường tách service thực thi và thành phần scheduling, đặt hợp đồng hoàn tất rõ ràng tại ranh giới.

## 11. Một lượt chạy có thể bị chậm bởi tài nguyên

Nếu scheduler chỉ một worker, Job A gọi API chậm 20 giây, còn Job B đủ điều kiện chạy giữa khoảng ấy, Job B vẫn có thể chờ đến khi A thả worker.

```text
10:00:00  Worker chạy Job A, I/O chờ 20s
10:00:02  Job B đáng ra đủ điều kiện
10:00:20  Job A kết thúc
10:00:20  Job B mới bắt đầu
```

Đây là một **tình huống tài nguyên minh họa**. Đừng thấy gap thực tế lớn hơn cấu hình rồi kết luận fixedDelay bị cộng nhầm. Cần phân biệt **delay theo hợp đồng** và **lệch thời điểm bắt đầu do scheduler/CPU/downstream**.

Cách xử lý gồm giới hạn timeout ở I/O, cấu hình pool hợp lý, tách những lịch quan trọng khỏi tác vụ chậm, giám sát thời gian chờ và giới hạn lượng việc. Tăng pool không tự tăng năng lực của database hoặc downstream HTTP nếu chúng có giới hạn kết nối khác.

## 12. Ngoại lệ trong @Scheduled(fixedDelay): lịch có tiếp tục không?

Với `ThreadPoolTaskScheduler` phổ biến, Spring bọc periodic task bằng cơ chế error handler. Lỗi của một lượt periodic **thường được log và suppress theo mặc định**, nhờ đó lịch vẫn có thể chạy lượt kế tiếp sau delay.

Điều này khác Java `ScheduledThreadPoolExecutor` thuần, nơi exception không được xử lý trong periodic runnable có thể chấm dứt các lượt tiếp theo.

```java
@Scheduled(fixedDelay = 5000)
public void poll() {
    throw new IllegalStateException("Downstream lỗi");
}
```

Đây là **method cố ý ném lỗi** để minh họa. Với policy Spring thông thường, có thể thấy log lỗi rồi lượt sau vẫn đến. Vì vậy không dùng `ScheduledFuture.isDone()` làm chỉ số “lượt vừa rồi thành công” và không cho rằng chỉ một lỗi làm schedule biến mất.

`ThreadPoolTaskScheduler` có thể cấu hình `setErrorHandler(...)`:

```java
scheduler.setErrorHandler(error -> {
    recordScheduledError(error);
});
```

`recordScheduledError` do ứng dụng định nghĩa. Nếu handler không ném tiếp, exception có thể được suppress. Nếu muốn dừng chuỗi sau lỗi, cần một chính sách propagate có chủ đích và hiểu rằng nó khác logic tiếp tục mặc định.

**Không nhầm** handler này với `AsyncConfigurer.getAsyncUncaughtExceptionHandler()` của `@Async void`; hai cơ chế có đường thực thi và hợp đồng riêng.

### 12.1. Retry storm khi một lượt thất bại rất nhanh

Nếu call API thất bại tức thì và `fixedDelay` là 100ms, mỗi lần lỗi xong lại có thể đến lượt tiếp theo sau 100ms. Điều đó có thể làm downstream tệ hơn. `fixedDelay` cố định **không tự exponential backoff**.

Cân nhắc circuit breaker, retry có giới hạn, backoff+jitter, hoặc một `Trigger`/scheduler programmatic tính thời điểm theo kết quả lỗi, tùy nghiệp vụ.

## 13. Reactive @Scheduled trong Spring 6.1+: một quy tắc đặc biệt

Spring Framework 6.1+ cho phép một số kiểu method `@Scheduled` trả reactive `Publisher` (hoặc kiểu có thể chuyển sang Publisher và hỗ trợ deferred subscription). Khác với method `void`:

- Framework lấy Publisher và **subscribe định kỳ** theo schedule.
- Với `fixedDelay`, Spring **chặn cho tới khi subscription hoàn tất** để giữ ngữ nghĩa delay sau completion.
- `onNext` bị bỏ qua trong ngữ cảnh scheduled invocation.
- `onError` được log ở mức WARN rồi tiếp tục các lượt subscribe sau; lỗi reactive không đi qua Runnable `ErrorHandler` giống một exception đồng bộ.
- Nếu Publisher không hoàn tất, lần sau có thể không được kích hoạt đúng như kỳ vọng.

Mảnh Reactor minh họa, **cần dependency Reactor, không nằm trong Maven demo mục 8**:

```java
import reactor.core.publisher.Mono;
import org.springframework.scheduling.annotation.Scheduled;

@Scheduled(fixedDelay = 1000)
public Mono<Void> syncReactive() {
    return Mono.fromRunnable(this::performSync);
}
```

`performSync` là method ứng dụng. Không suy rằng **mọi** CompletableFuture/pipeline mà một scheduled method tạo ra cũng được Spring chờ hoàn tất tương tự; hợp đồng reactive scheduled method có quy tắc riêng.

## 14. Virtual threads và SimpleAsyncTaskScheduler

Spring Framework 6.1+ có `SimpleAsyncTaskScheduler`, phù hợp mô hình virtual threads của Java 21. Spring lưu ý rằng **fixed-delay tasks trên implementation này chạy trên một scheduler thread đơn**, trong khi fixed-rate/cron có chiến lược kích hoạt phù hợp hơn với virtual threads.

Nếu có nhiều fixed-delay job I/O dài, worker scheduling đơn có thể bị chiếm và các job khác bị chậm. Tài liệu Spring khuyến nghị fixed-rate hoặc cron khi dùng `SimpleAsyncTaskScheduler` với virtual threads **nếu yêu cầu nghiệp vụ cho phép**.

Không áp nguyên kết luận này cho `ThreadPoolTaskScheduler`: implementation ấy có thread pool có thể cấu hình cho các lịch độc lập. Ví dụ Java 17 của bài **không dùng virtual threads**; chúng trở thành tính năng chuẩn ở Java 21.

## 15. Transaction, context và side effects

`@Scheduled` không tự bảo đảm một transaction phân tán hoặc thực hiện đúng một lần:

- Nếu scheduled method gọi transactional service đồng bộ và chờ xong, thời gian transaction góp vào thời gian lượt chạy.
- Nếu method chỉ gửi một lệnh background, transaction của nhánh background không tự kéo dài completion của scheduled method.
- Khi một request khác chưa commit dữ liệu, task scheduled có thể nhìn thấy trạng thái cũ theo isolation.
- Nếu task gửi email/ghi thanh toán rồi timeout, lần retry có thể tạo side effect trùng.

Cần đặt ranh giới `@Transactional` phù hợp trên service Spring-managed, dùng idempotency key và đối soát với downstream. Khi nhiều pod cùng chạy, `fixedDelay` không thay thế leader election/distributed lease.

Việc đọc `ThreadLocal`, MDC, security context trong scheduler worker cũng không mặc nhiên nhận dữ liệu của HTTP request. Đây là worker có vòng đời độc lập; nếu cần metadata, dùng cơ chế context propagation hoặc truyền tham số bất biến phù hợp.

## 16. Observability: đo finish-to-next-start thay vì start-to-start

Nếu muốn chứng minh `fixedDelay` đang hoạt động, đo ba mốc:

```text
start[n]        = lúc lượt n bắt đầu
finish[n]       = lúc lượt n kết thúc
start[n+1]      = lúc lượt kế tiếp bắt đầu

runDuration[n]  = finish[n] - start[n]
actualGap[n]    = start[n+1] - finish[n]
startSpan[n]    = start[n+1] - start[n]
```

Trong điều kiện bình thường, `actualGap` lớn hơn hoặc bằng độ dài `fixedDelay` (có sai số đo và vị trí instrument). `startSpan` bao gồm **cả thời gian chạy trước**, không nên dùng nó rồi gán nhãn là actual fixed delay.

Mảnh instrumentation:

```java
private final AtomicLong lastFinished = new AtomicLong(-1L);

@Scheduled(fixedDelay = 5000)
public void monitoredPoll() {
    long started = System.nanoTime();
    long previousEnd = lastFinished.get();

    if (previousEnd >= 0) {
        long gapMillis = TimeUnit.NANOSECONDS.toMillis(
                started - previousEnd);
        recordGap(gapMillis);
    }

    try {
        performPoll();
    } finally {
        lastFinished.set(System.nanoTime());
    }
}
```

Đây là **mảnh code trong Spring bean**, với `recordGap`/`performPoll` do ứng dụng cung cấp. Đối với một lịch duy nhất, đo được khoảng nghỉ xấp xỉ. Với **nhiều registrations độc lập** vào cùng method, một trường `lastFinished` chung không đủ; phải giữ state riêng cho từng lịch và thiết kế thread-safety.

`System.nanoTime()` phù hợp đo khoảng thời gian; thêm `Instant.now()` khi cần mốc calendar cho log. Không giả định đồng hồ wall-clock không bao giờ được chỉnh bởi NTP.

### 16.1. Chỉ số hữu ích

| Metric | Ý nghĩa |
|---|---|
| `scheduled.run.duration` | Mỗi lượt chạy mất bao lâu |
| `scheduled.run.success`/`failure` | Lượt thành công và lỗi |
| `scheduled.run.last_success` | Có còn làm việc hữu ích không |
| `scheduled.gap.actual` | Khoảng nghỉ thực đo |
| `scheduled.active` | Số lượt đang chạy |
| `scheduler.lateness` | Trễ thêm so với thời điểm đủ điều kiện |
| `scheduled.registration.count` | Số lịch được tạo, phòng đăng ký trùng |

Nếu dùng Micrometer, tránh đặt `requestId`/`jobId` duy nhất vào tag metrics gây cardinality lớn. Mã correlation phù hợp với log/trace có kiểm soát hơn.

## 17. Vòng đời, cancellation và ứng dụng nhiều instance

`@Scheduled` được đăng ký khi application context sẵn sàng theo cơ chế Spring, và được hủy/dừng theo lifecycle. Nếu cần một nút bật/tắt từng lịch theo người dùng, API programmatic `TaskScheduler.scheduleWithFixedDelay(...)` trả `ScheduledFuture<?>` thuận tiện để giữ theo job ID và `cancel(false)`.

`cancel(false)` thường ngăn các lượt sau, **không nhất thiết ngắt lượt đang chạy**. `cancel(true)` chỉ *cố gắng* interrupt; không tự rollback side effect. Không đóng scheduler bean dùng chung chỉ để ngừng một lịch.

Trong cluster, mỗi pod/JVM có schedule riêng. Nếu có 4 pod cùng `@Scheduled(fixedDelay=5s)`, tổng cộng có thể có 4 luồng công việc, dù **mỗi pod** tuân thủ khoảng nghỉ. Nếu nghiệp vụ yêu cầu một job trên toàn hệ thống, cần coordinator/leader/lock/lease/job store và idempotency.

Một lịch trong bộ nhớ không tồn tại sau khi JVM crash/redeploy. Nếu cần bảo đảm job không mất qua restart, sử dụng persistence/broker/scheduler chuyên dụng và logic retry/đối soát.

## 18. Những lỗi hiểu sai thường gặp

| Hiểu sai | Điều đúng |
|---|---|
| `fixedDelay = 5000` nghĩa là start-to-start luôn 5 giây | Khoảng nghỉ **sau completion** là 5 giây |
| Job chạy 20s nhưng vẫn phải bắt đầu lại mỗi 5s | Fixed-delay đợi job xong rồi mới nghỉ |
| Hai log START cách nhau 8s nghĩa là fixedDelay sai | Có thể job chạy 3s + nghỉ 5s |
| `initialDelay` cộng vào mọi lượt | Chỉ dùng cho lượt đầu |
| `fixedDelay=5` là 5 giây | Mặc định là 5 mili giây |
| Tăng poolSize sẽ làm một fixed-delay registration chạy nhiều bản song song | Không đối với cùng một registration trên ScheduledThreadPoolExecutor |
| `@Scheduled` tự chờ task con gửi bằng `execute` | Scheduler thường quan sát method scheduled đã return |
| `@EnableAsync` tự bật `@Scheduled` | Cần `@EnableScheduling` |
| `@Scheduled` hoạt động trên object tự `new` | Cần Spring-managed bean và scheduling infrastructure |
| Một lỗi trong task định kỳ luôn dừng hẳn lịch | Spring có thể log/suppress để chạy tiếp |
| `Thread.sleep` trong test chứng minh đã có hai lượt | Cần latch/condition/timeout dựa trên mốc thật |
| Bật property mới làm lịch đổi ngay trong mọi ứng dụng | Thường cần re-register/refresh |
| FixedDelay trên một pod chống trùng toàn cluster | Các pod có lịch độc lập |
| fixedDelay luôn phù hợp virtual threads | Với SimpleAsyncTaskScheduler, fixed-delay dùng scheduler thread đơn |
| Có cấu hình fixedDelay là có timeout/circuit breaker | Đây là các cơ chế riêng |

## 19. Bài tập thực hành

### Bài 1 — Tính timeline

`@Scheduled(fixedDelay=3000)`; lượt đầu chạy 7s từ 12:00:00, lượt hai chạy 1s. Giả định không có trễ thêm. Lượt hai và lượt ba sớm nhất bắt đầu khi nào?

**Đáp án:** Lượt đầu xong 12:00:07, nghỉ 3s → lượt hai 12:00:10. Lượt hai xong 12:00:11, nghỉ 3s → lượt ba 12:00:14.

### Bài 2 — Chọn đúng thuộc tính

Downstream yêu cầu “chỉ gửi request tiếp theo ít nhất 30 giây sau khi request trước hoàn tất”. Chọn `fixedRate=30000` hay `fixedDelay=30000`?

**Đáp án:** `fixedDelay=30000` theo hợp đồng nghỉ sau completion.

### Bài 3 — Kiểm tra phép đo

Hai dòng START cách nhau 9s. Thời gian xử lý lượt trước là 4s. Delay cấu hình 5s. Có lỗi không?

**Đáp án:** Chưa có bằng chứng lỗi; 4+5=9s phù hợp. Hãy đo Finish-to-next-Start.

### Bài 4 — Đơn vị

Đổi `@Scheduled(fixedDelay=5,timeUnit=TimeUnit.SECONDS)` thành `@Scheduled(fixedDelay=5)` thì thay đổi thế nào?

**Đáp án:** 5 giây thành 5 mili giây, dễ gây tải quá lớn.

### Bài 5 — @Async phụ

Scheduled method gửi một HTTP call vào executor rồi trả về sau 1ms. HTTP call xử lý 20s; fixedDelay là 2s. Có bảo đảm hai HTTP call cách nhau tối thiểu 2s sau completion không?

**Đáp án:** Không. Scheduler đo từ lúc method scheduled hoàn tất, không tự chờ task con.

### Bài 6 — Reactive

Một reactive scheduled publisher không hoàn tất. Với fixedDelay, có thể xảy ra chuyện gì với lần subscribe tiếp theo?

**Đáp án:** Spring chờ subscription hoàn tất để giữ fixed-delay; có thể không đến lượt tiếp theo. Thiết kế timeout/cancellation phù hợp.

### Bài 7 — Virtual threads

Một hệ thống dùng `SimpleAsyncTaskScheduler` trên Java 21 và có nhiều fixed-delay jobs blocking. Vì sao các lịch khác bị trễ?

**Đáp án:** Fixed-delay tasks chạy trên một scheduler thread đơn ở implementation này. Đánh giá loại scheduler/trigger phù hợp.

### Bài 8 — Chạy mã

Chạy `FixedDelayJavaDemo.java` rồi `SpringFixedDelayDemo.java`. Vì sao cả hai kiểm tra `gap >= 200ms` thay vì `gap == 200ms`?

**Đáp án:** Delay là thời điểm sớm nhất đủ điều kiện, không phải thời điểm thực thi chính xác tuyệt đối. Jitter/worker load có thể làm gap lớn hơn.

### Bài 9 — Nhiều pod

Ba pod cùng poll một bảng bằng `@Scheduled(fixedDelay=5s)`. Nếu một pod ghi log “không overlap”, liệu có bảo đảm chỉ một pod xử lý một record?

**Đáp án:** Không. Không overlap trong một registration không thay thế distributed lock/idempotency.

## 20. Checklist triển khai production

1. Nghiệp vụ thật sự cần khoảng nghỉ **sau completion**, không phải cadence giữa start hoặc mốc calendar?
2. Đơn vị tính rõ ràng: mặc định milliseconds, hoặc `timeUnit`/`fixedDelayString` có đơn vị?
3. `initialDelay` chỉ dùng cho lượt đầu và đã được tính đúng?
4. `@EnableScheduling` áp dụng đúng ApplicationContext và bean không được đăng ký trùng ngoài ý muốn?
5. Method được scheduler quan sát tới lúc logic nghiệp vụ **thật sự kết thúc**, không chỉ gửi việc rồi return?
6. Có timeout cho I/O và cơ chế xử lý task có thể treo?
7. Pool scheduler có đủ worker cho các lịch độc lập; không chặn toàn bộ scheduler vì một job dài?
8. Hiểu error policy Spring (log/suppress cho periodic theo mặc định) và có metrics lỗi per-run?
9. Có backoff/circuit breaker nếu downstream liên tục lỗi thay vì poll dày?
10. Nếu dùng reactive, hiểu ngữ nghĩa subscribe/complete và `onError`.
11. Nếu dùng virtual threads/SimpleAsyncTaskScheduler, xem lại fixed-delay trên scheduler thread đơn.
12. Nếu cần thay delay runtime, có re-register/refresh đúng cơ chế?
13. Nếu chạy nhiều instance, có distributed coordination khi cần singleton job?
14. Nếu shutdown/restart, có chính sách hủy và lưu job bền vững khi cần?
15. Integration test đo Finish-to-next-Start với monotonic timer và latch, không dùng sleep để đoán task đã hoàn tất?

## 21. Tổng kết

- **`@Scheduled(fixedDelay=D)` đo D từ khi lượt trước kết thúc tới khi lượt sau bắt đầu.**
- Khoảng cách hai lần bắt đầu trong điều kiện lý tưởng gần bằng **thời gian chạy lượt trước + D**.
- `fixedRate` đo nhịp các mốc bắt đầu; `cron` dựa trên giờ lịch; không dùng ba cơ chế thay nhau.
- `initialDelay` chỉ cho lần đầu, `fixedDelayString`/`timeUnit` giúp cấu hình khoảng nghỉ rõ ràng.
- Scheduler chỉ chờ **completion mà nó quan sát**; gửi một task sang executor khác rồi trả về không làm fixedDelay tự chờ task ấy.
- Error policy, pool capacity, reactive subscription, virtual threads và nhiều JVM đều có thể làm khác cách hệ thống thực tế vận hành.
- **Dùng timestamp finish/start, latch và timeout để kiểm chứng**, không đánh đồng hai dòng START với fixedDelay.

### Tài liệu tham khảo chính thức

- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework Javadoc — Scheduled](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework Javadoc — EnableScheduling](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html)
- [Spring Framework Javadoc — ThreadPoolTaskScheduler](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html)
- [Spring Framework Javadoc — SimpleAsyncTaskScheduler](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/SimpleAsyncTaskScheduler.html)
- [Spring Framework Javadoc — TaskScheduler](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/TaskScheduler.html)
- [Java SE 17 Javadoc — ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-tasks-004">fixedRate theo nhịp lịch và hành vi khi công việc chạy lâu</a>

<details>
<summary>Xem chi tiết</summary>

# fixedRate theo nhịp lịch và hành vi khi công việc chạy lâu

> **Chủ đề:** Spring Framework – concurrency  
> **Nhóm:** `@Scheduled`  
> **Câu trả lời ngắn:** `@Scheduled(fixedRate = P)` thiết lập **nhịp bắt đầu dự kiến** cách nhau `P`, không phải “đợi `P` sau khi task xong”. Với `ThreadPoolTaskScheduler` dùng `ScheduledThreadPoolExecutor` thông thường, khi một lần thực thi **dài hơn `P`**, lần kế tiếp có thể bắt đầu **muộn** hoặc sát ngay sau lần trước, nhưng **các lần chạy của cùng một periodic registration không chạy chồng lên nhau**. Điều đó không ngăn các **registration khác, nhiều bean, nhiều pod hoặc công việc được tách sang `@Async`** chạy đồng thời.

Bài này xét **Spring Framework 6.2.x và Java 17**, phân biệt rõ **mốc dự kiến**, **mốc thực tế**, **thời lượng xử lý**, **độ trễ**, **catch-up**, giới hạn worker và **hành vi khi task lỗi**. Ví dụ mô hình thời gian là deterministic; ví dụ Spring Context chứng minh không chồng lấp bằng `CountDownLatch` mà không dùng `Thread.sleep` để suy đoán.

## 1. Mục tiêu học tập

Sau bài học, bạn có thể:

1. Nêu đúng ý nghĩa `fixedRate`, `initialDelay`, `timeUnit` và `fixedRateString` trong Spring `@Scheduled`.
2. Dùng công thức `planned_n = planned_0 + n × period` để phân tích lịch dự kiến.
3. Giải thích vì sao một task chạy lâu làm lần tiếp theo trễ, đôi khi chạy sát nhau để bắt kịp, nhưng **không tự phát sinh nhiều invocation đồng thời cho cùng periodic task của JDK**.
4. Phân biệt `fixedRate` với `fixedDelay`, cron và polling dựa trên deadline.
5. Phân biệt **một registration** với nhiều `@Scheduled` độc lập, nhiều bean instance, `@Async` delegation và nhiều replica.
6. Thiết kế chính sách **skip, coalesce, catch-up, idempotency, timeouts, concurrency guard** theo yêu cầu nghiệp vụ.
7. Cấu hình `ThreadPoolTaskScheduler` và `@EnableScheduling` đúng lifecycle, hiểu khi nào cần pool lớn hơn và khi nào tăng pool **không giải quyết** task đơn chạy quá lâu.
8. Kiểm thử logic thời gian bằng mô hình deterministic và kiểm thử Spring thực bằng latch/future có timeout.
9. Xử lý exception, shutdown, monitoring và giới hạn chậm trễ theo SLO.

## 2. fixedRate chính xác là gì?

Với method:

~~~java
@Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
public void refreshCache() {
    refreshFromDatabase();
}
~~~

Đây là **method snippet Spring**, cần `@EnableScheduling`, bean được Spring quản lý, import `Scheduled`/`TimeUnit` và method `refreshFromDatabase()` của ứng dụng.

Ý nghĩa: scheduler hướng tới các lần bắt đầu theo nhịp **5 giây**. Nó **không** có nghĩa phải chờ 5 giây kể từ khi lần chạy trước hoàn thành. Nó cũng không có nghĩa scheduler bảo đảm hard real-time chính xác 5 giây trong mọi tải.

Nếu lần đầu dự kiến tại thời điểm `t0`, các mốc dự kiến có dạng:

~~~text
t0, t0 + P, t0 + 2P, t0 + 3P, ...
P = 5 seconds

planned: |-----5s-----|-----5s-----|-----5s-----|
         0            5           10           15
~~~

Giá trị `P` là **độ dài thời gian** (`Duration/TimeUnit`), không phải “5 giây của phút nào” hay một timezone. Với scheduled executor thông thường, khi task chưa thể bắt đầu đúng mốc vì worker đang bận, nó **chạy muộn** theo khả năng của scheduler.

### 2.1. “Nhịp lịch” không phải “cron”

- `fixedRate = 5s`: chu kỳ tương đối dựa trên một mốc ban đầu của registration.
- `cron = "0 */5 * * * *"`: các mốc lịch dân sự theo phút/giờ/ngày và timezone; có quy tắc cron và tình huống DST khác.
- `fixedRate = 24 hours`: 24 giờ elapsed, không nhất thiết là “09:00 sáng mỗi ngày ở New York” khi đổi giờ mùa hè.
- `fixedDelay = 5s`: nghỉ 5 giây **sau khi lần trước hoàn tất**.

Để chạy “09:00 hàng ngày theo timezone của khách hàng”, dùng cron với `zone` và business key cho từng ngày, không dùng `fixedRate = 24h` như một cách thay thế mặc định.

### 2.2. Mỗi khoảng period có phải một job riêng đã enqueue?

Không. Với `ScheduledThreadPoolExecutor.scheduleAtFixedRate(...)`, JDK quản lý một **periodic task series**. Không nên mô tả rằng cứ sau mỗi tick là scheduler tạo vô hạn `Runnable` độc lập, chất chồng các bản sao rồi chạy song song. Thực thi lặp lại của **cùng** periodic task có quy tắc không overlap.

Tuy nhiên, có thể có các **task series khác** trên cùng executor; chúng được quản lý riêng và có thể chạy đồng thời khi pool có tài nguyên. Nếu một periodic task chạy quá lâu, nó có thể gần như luôn chạy mà không có thời gian nghỉ, gây starvation cho những việc khác khi số worker quá ít.

## 3. Công thức thời gian: planned, actual và finish

Đặt:

- `P`: period được cấu hình.
- `S_n`: thời điểm **dự kiến** cho invocation thứ `n` (bắt đầu từ 0).
- `A_n`: thời điểm invocation đó **thực sự bắt đầu**.
- `F_n`: thời điểm invocation đó **hoàn tất**.
- `R_n = F_n − A_n`: thời gian chạy thực tế.
- `L_n = A_n − S_n`: độ trễ bắt đầu so với lịch.

Với nhịp fixed-rate lý tưởng:

~~~text
S_n = S_0 + n * P
~~~

Trong **mô hình một periodic task chạy tuần tự**, khi chưa xét các yếu tố scheduling khác:

~~~text
A_0 >= S_0
A_n >= max(S_n, F_(n-1)), n >= 1
F_n = A_n + R_n
L_n = A_n - S_n
~~~

Dấu `>=` dùng để nhắc rằng OS scheduling, GC pause, các task khác hoặc pool bận có thể làm việc khởi động muộn hơn cả hai mốc. Khi làm mô phỏng lý tưởng, ta có thể đặt `A_n = max(S_n, F_(n-1))`.

**Diễn giải:** nếu lần trước chưa xong, lần mới **không thể** bắt đầu sớm hơn `F_(n-1)` trong cùng một periodic registration của JDK. Nếu lúc hoàn thành, các mốc lịch `S_n` đã trôi qua, lần tiếp theo có thể được chạy ngay khi scheduler có thể.

### 3.1. Khi thời lượng ngắn hơn period

Ví dụ `P=1000ms`, mỗi run mất 300ms:

~~~text
planned: 0         1000        2000        3000
         |-----------|-----------|-----------|
run:     [300]       [300]       [300]       [300]
idle:        <700ms>     <700ms>     <700ms>
~~~

Hệ thống có khoảng nghỉ giữa các execution của cùng periodic registration nếu không gặp sự cố/tải bất thường.

### 3.2. Khi một run dài hơn period

Giả sử `P=1000ms`, run đầu mất 2500ms:

~~~text
planned: 0   1000   2000   3000   4000   5000 ...
         |-----|------|------|------|------|
run 1:   [-----------2500ms-----------]
run 2:                              [------]
         ^                       ^
       start 0                  run 1 finishes at 2500ms
~~~

Tại `t=1000ms` và `t=2000ms`, run đầu vẫn đang chạy. Đối với cùng một periodic task của `ScheduledThreadPoolExecutor`, **không có run 2 đồng thời với run 1**. Run kế tiếp **có thể bắt đầu ngay sau khi run 1 hoàn thành**, vì mốc dự kiến đã quá khứ.

### 3.3. Khi nhiều run liên tục dài hơn period

Nếu mỗi run mất 2500ms trong khi period là 1000ms, thời điểm bắt đầu thực tế có thể gần:

~~~text
planned:       0ms    1000ms    2000ms    3000ms    4000ms
actual:        0ms    2500ms    5000ms    7500ms    10000ms
start lag:     0ms    1500ms    3000ms    4500ms     6000ms
~~~

Đây là **mô hình tuần tự lý tưởng**, không phải cam kết từng mili-giây của hệ điều hành. Ý nghĩa là khi tải không thể hoàn tất một unit of work trong một period, **lag có thể tăng dần**. Tăng `poolSize` của scheduler không tự làm hai invocation của **cùng registration** chạy song song trong `ScheduledThreadPoolExecutor`.

Nếu nghiệp vụ yêu cầu xử lý mọi “tick” như một sự kiện bền vững, đừng dùng counter của periodic invocation như một job store: tiến trình có thể restart, clock có thể đổi, task có thể fail, và không có cam kết toàn bộ missed tick được persist/replay.

## 4. Không overlap: bảo đảm nào và phạm vi nào?

Javadoc `ScheduledThreadPoolExecutor` ghi rõ **các lần thực thi kế tiếp của cùng một task được đăng ký bằng `scheduleAtFixedRate` hoặc `scheduleWithFixedDelay` không overlap**; các run có thể được thực hiện bởi thread khác nhau, nhưng hiệu ứng run trước có quan hệ *happens-before* với run sau.

Với Spring `ThreadPoolTaskScheduler` thông thường, schedule của một `@Scheduled(fixedRate=...)` synchronous method dựa vào JDK scheduled executor, nên quan sát tương ứng:

- **Cùng một registration:** run tiếp theo không gọi đồng thời khi run trước chưa thoát.
- **Pool có 4 hay 8 worker:** không thay đổi quy tắc tuần tự của *một periodic registration*.
- **Run kéo dài:** lần tiếp theo có thể bắt đầu muộn, có thể sát run trước để bắt kịp.
- **Registration khác:** có thể chạy đồng thời nếu tài nguyên cho phép.
- **Chạy qua `@Async`/reactive:** scheduler có thể chỉ dispatch công việc rồi return, khiến công việc thật vẫn còn chạy khi tick mới đến.

### Bảng xác định có thể overlap hay không

| Trường hợp | Có thể có hai công việc đồng thời? | Lý do |
| --- | --- | --- |
| Một `@Scheduled(fixedRate)` sync method, một registration trên `ThreadPoolTaskScheduler` | **Không** cho hai invocation của cùng registration | JDK periodic future có non-overlap |
| Hai method `@Scheduled` khác nhau | **Có thể** | Hai registrations riêng, tùy pool |
| Hai annotation `@Scheduled` trên cùng method | **Có thể** | Spring xử lý độc lập từng schedule |
| Hai bean instances cùng có method `@Scheduled` | **Có thể** | Mỗi bean có thể được đăng ký riêng |
| Một method scheduled nhưng gọi `@Async` ở bean khác | **Có thể** có nhiều async work in-flight | Tick kết thúc sau dispatch, không chờ async task |
| Một scheduler registration gọi reactive Publisher không chờ completion | **Có thể** có xử lý downstream chồng lấp | Scheduling và subscription completion khác nhau |
| Ba pod đều bật cùng `@Scheduled` | **Có thể**, thường là ba invocation | Mỗi JVM/pod có scheduler riêng |
| Spring `SimpleAsyncTaskScheduler` hoặc custom scheduler | **Cần kiểm tra implementation** | Không thể áp bất biến JDK tùy tiện |

**Kết luận:** “fixedRate luôn chạy song song khi task lâu hơn period” là sai; “fixedRate không bao giờ có hai công việc cùng loại đang chạy” cũng sai nếu đã có nhiều registration, delegation sang async hoặc nhiều replica.

## 5. Ví dụ minh họa dài 700ms, 1300ms và 1700ms

Đặt period = 1000 ms, durations các run lần lượt: `700, 1300, 1700, 200 ms`.

Nhịp fixed-rate lên kế hoạch: `0, 1000, 2000, 3000 ms`. Trong mô hình tuần tự:

| Run | Planned start (ms) | Actual start (ms) | Finish (ms) | Start lag (ms) |
| --- | ---: | ---: | ---: | ---: |
| 1 | 0 | 0 | 700 | 0 |
| 2 | 1000 | 1000 | 2300 | 0 |
| 3 | 2000 | 2300 | 4000 | 300 |
| 4 | 3000 | 4000 | 4200 | 1000 |

Run thứ 3 trễ 300ms vì run thứ 2 đến `t=2300` mới xong. Run thứ 4 có mốc planned `t=3000` nhưng run thứ 3 đến `t=4000` mới xong, nên trễ 1000ms. Điều này không tạo overlap; **lag** tăng khi các run kéo dài.

Với fixed-delay = 1000ms, mốc bắt đầu mỗi run sau lần đầu phụ thuộc **finish trước + 1000ms**:

| Run | Actual start (ms) | Finish (ms) |
| --- | ---: | ---: |
| 1 | 0 | 700 |
| 2 | 1700 | 3000 |
| 3 | 4000 | 5700 |
| 4 | 6700 | 6900 |

Cả hai cách đều có thể tuần tự cho một registration, nhưng thời gian chờ giữa các run khác hẳn. Bài tập Java 17 phía sau tính chính xác các bảng này không dùng đồng hồ thật.

## 6. So sánh fixedRate, fixedDelay và cron

| Đặc tính | `fixedRate` | `fixedDelay` | `cron` |
| --- | --- | --- | --- |
| Ý nghĩa | Nhịp planned start theo `period` | Khoảng nghỉ sau khi invocation trước hoàn thành | Các mốc calendar theo cron + zone |
| Mốc kế tiếp dựa trên | Mốc planned định kỳ, với cơ chế trì hoãn của executor | Completion trước + delay | Lịch dân sự/trigger context |
| Run lâu hơn khoảng cấu hình | Có thể chạy muộn và sát nhau khi bắt kịp | Vẫn chờ đủ delay sau completion | Có thể bỏ lỡ/điều chỉnh fire tùy trigger semantics |
| Ý nghĩa timezone/DST | Không phải lịch ngày giờ địa phương | Không phải lịch ngày giờ địa phương | Có, nếu cron theo zone có DST |
| Phù hợp | Poll/refresh theo cadence, sampling cần nhịp tương đối | Tránh quá tải, cần nghỉ giữa các lượt | Daily/weekly local-time business schedule |
| Có tự đảm bảo cluster singleton? | Không | Không | Không |

### 6.1. fixedRate: ưu tiên nhịp

~~~java
@Scheduled(fixedRate = 2, timeUnit = TimeUnit.SECONDS)
public void poll() {
    fetchAndProcess();
}
~~~

**Snippet Spring:** hai mốc khởi đầu *dự kiến* cách nhau hai giây. Nếu `fetchAndProcess` có lúc mất bốn giây, lần tiếp theo có thể bắt đầu ngay sau khi run trước trả về; không có khoảng nghỉ bắt buộc 2 giây.

### 6.2. fixedDelay: ưu tiên khoảng nghỉ

~~~java
@Scheduled(fixedDelay = 2, timeUnit = TimeUnit.SECONDS)
public void poll() {
    fetchAndProcess();
}
~~~

**Snippet Spring:** chờ hai giây **sau khi run trước hoàn thành**, nên nếu mỗi run mất bốn giây, hai lần bắt đầu thường cách nhau ít nhất khoảng sáu giây trong mô hình không có thêm delay.

### 6.3. cron: ưu tiên mốc lịch

~~~java
@Scheduled(cron = "0 0 9 * * *", zone = "Asia/Ho_Chi_Minh")
public void daily() {
    generateReportForBusinessDate();
}
~~~

**Snippet Spring:** cron 09:00 giờ Việt Nam; không phải một `Duration.ofHours(24)` tính từ lúc application khởi động. Nếu muốn đảm bảo một báo cáo/ngày/tenant, cần idempotency theo business date và policy backfill; cron không tự tạo distributed single execution.

### 6.4. Chọn theo yêu cầu

- Muốn “**thử thăm dò mỗi 5 giây nếu có tài nguyên**”: `fixedRate` có thể phù hợp, nhưng phải có overload policy.
- Muốn “**xử lý xong nghỉ đúng 5 giây rồi làm lại**”: `fixedDelay`.
- Muốn “**cứ 09:00 giờ khách hàng**”: cron + `ZoneId`.
- Muốn “**xử lý tất cả mốc 5 phút không mất dù restart**”: job store có persistence và business period keys; `@Scheduled` chỉ có thể làm trigger/quét việc cần xử lý.
- Muốn “**nếu run trước chưa xong thì bỏ tick mới**”: tự thiết kế **skip-on-busy**, không mặc định hiểu fixedRate là sẽ bỏ mọi tick.

## 7. @Scheduled trong Spring: cú pháp và mặc định

### 7.1. Bật annotation processing

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
~~~

Đây là **class cấu hình Spring**. Nếu không có `@EnableScheduling` hoặc cơ chế tương đương, annotation `@Scheduled` có thể không được processor đăng ký.

### 7.2. Milliseconds là mặc định

~~~java
@Scheduled(fixedRate = 5000)
public void pollEveryFiveSeconds() { /* work */ }
~~~

`fixedRate = 5000` mặc định là **5000 milliseconds**. Để rõ đơn vị:

~~~java
@Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
public void pollEveryFiveSeconds() { /* work */ }
~~~

Đây là **hai phương thức minh họa riêng**; không phải khuyến nghị đặt cả hai trong cùng bean vì khi đó cả hai được schedule độc lập.

### 7.3. initialDelay

~~~java
@Scheduled(
        initialDelay = 2,
        fixedRate = 5,
        timeUnit = TimeUnit.SECONDS)
public void refresh() {
    refreshCache();
}
~~~

**Snippet Spring**. Lần đầu sẽ được lên lịch sau khoảng `2s` từ khi registration được thiết lập theo cơ chế scheduler; các lần sau theo nhịp `5s`. `initialDelay` không tạo “mốc lịch 00:00:02 UTC” hay đồng bộ cùng thời điểm giữa các pod. Nếu không cấu hình initial delay, task có thể bắt đầu sớm khi scheduler registration hoạt động.

### 7.4. fixedRateString và cấu hình ngoài

~~~java
@Scheduled(
        fixedRateString = "PT5S",
        initialDelayString = "PT1S")
public void runConfigured() {
    refreshCache();
}
~~~

Đây là **snippet**. `fixedRateString` hỗ trợ dạng `Duration` như `PT5S`. Trong dự án có thể truyền placeholder từ config để thay period mà không sửa Java code. Hãy kiểm tra giá trị cuối sau resolve phải hợp lệ/dương, không tạo period 0 hoặc âm.

### 7.5. scheduler qualifier (Spring 6.1+)

~~~java
@Scheduled(
        fixedRate = 5,
        timeUnit = TimeUnit.SECONDS,
        scheduler = "businessScheduler")
public void runOnBusinessScheduler() {
    doWork();
}
~~~

Đây là **snippet Spring 6.1+**, cần bean `TaskScheduler` có tên hoặc qualifier phù hợp. Rất hữu ích khi ứng dụng có scheduler riêng cho job I/O dài và scheduler nhẹ cho metrics/heartbeats. Tuy nhiên, tách executor không làm một task non-overlap trở thành reentrant.

### 7.6. Method signature và return

Với `@Scheduled` synchronous thông thường, method không nhận argument; return value nếu có cũng không phải kênh kết quả được tự động xử lý như lời gọi HTTP. Spring 6.1+ cũng hỗ trợ một số kiểu reactive/publisher với semantics subscription riêng. Khi viết job định kỳ, ưu tiên method `void`, gửi status/metrics qua thành phần do container quản lý.

## 8. Java 17 chạy độc lập: mô phỏng timeline chính xác

Ví dụ sau **không tạo thread và không dùng `sleep`**, vì mục tiêu là hiểu công thức về thời gian, không đo jitter của OS. Nó tính từng planned/actual/finish khi các execution của **cùng task** không chồng lấp.

Tạo tệp **`FixedRateTimelineDemo.java`**:

~~~java
public class FixedRateTimelineDemo {

    public static void main(String[] args) {
        long periodMs = 1000;
        long[] runDurationMs = {700, 1300, 1700, 200};

        long freeAt = 0;

        for (int i = 0; i < runDurationMs.length; i++) {
            long planned = i * periodMs;
            long start = Math.max(planned, freeAt);
            long finish = start + runDurationMs[i];
            long lag = start - planned;

            System.out.printf(
                    "rate %d planned=%d start=%d finish=%d lag=%d%n",
                    i + 1, planned, start, finish, lag);

            freeAt = finish;
        }

        long nextDelayStart = 0;

        for (int i = 0; i < runDurationMs.length; i++) {
            long start = nextDelayStart;
            long finish = start + runDurationMs[i];

            System.out.printf(
                    "delay %d start=%d finish=%d%n",
                    i + 1, start, finish);

            nextDelayStart = finish + periodMs;
        }
    }
}
~~~

Lưu file đúng tên class, rồi chạy bằng JDK 17+:

~~~bash
javac FixedRateTimelineDemo.java
java FixedRateTimelineDemo
~~~

**Stdout chính xác:**

~~~text
rate 1 planned=0 start=0 finish=700 lag=0
rate 2 planned=1000 start=1000 finish=2300 lag=0
rate 3 planned=2000 start=2300 finish=4000 lag=300
rate 4 planned=3000 start=4000 finish=4200 lag=1000
delay 1 start=0 finish=700
delay 2 start=1700 finish=3000
delay 3 start=4000 finish=5700
delay 4 start=6700 finish=6900
~~~

### Giải thích từng dòng quan trọng

- **Rate run 1:** planned 0, chạy 700 ms rồi nghỉ đến mốc 1000 ms.
- **Rate run 2:** scheduled mốc 1000, chạy đến 2300 ms; làm mốc 2000 của run 3 bị lỡ.
- **Rate run 3:** bắt đầu thực ở 2300 thay vì 2000, tức trễ 300 ms; chạy đến 4000.
- **Rate run 4:** mốc 3000 đã qua, nên có thể bắt đầu lúc 4000, trễ 1000 ms.
- **Delay run 2:** không bắt đầu ở mốc fixed-rate 1000 mà đợi run 1 hoàn thành 700 rồi cộng 1000, tức 1700.
- **Delay run 3:** run 2 xong 3000, cộng 1000 → 4000.
- **Delay run 4:** run 3 xong 5700, cộng 1000 → 6700.

Công thức mô phỏng sử dụng `Math.max(planned, freeAt)` nên cố tình **không** tạo overlap. Thực tế vẫn có thêm scheduler lag từ thread pool/OS/GC và có thể có implementation khác; chương trình là một **mô hình toán**, không phải bản sao implementation scheduler.

### Bài tập thay số

Đổi `runDurationMs` thành `{2500, 2500, 2500}` với `periodMs=1000`, dự đoán actual starts `0,2500,5000` và lag `0,1500,3000`. Điều đó minh họa trạng thái **không kịp nhịp mãi mãi**. Tăng pool size có thể giúp các job **khác**, nhưng không làm cùng periodic task này tự sinh các bản sao đồng thời theo JDK `ScheduledThreadPoolExecutor`.

## 9. Java 17: kiểm chứng non-overlap trên executor thật

JDK `ScheduledThreadPoolExecutor` có thể được cấp **3 worker**, nhưng cùng một periodic registration vẫn không overlap. Để test điều đó một cách có kiểm soát:

- Run đầu chặn trên `CountDownLatch` `releaseFirst`.
- Main đợi run đầu chắc chắn đã vào.
- Main kiểm tra run thứ hai **chưa thể bắt đầu** khi run đầu vẫn giữ latch.
- Main mở latch, đợi run thứ hai bắt đầu.
- Theo dõi `maxActive`; kỳ vọng đúng 1 dù executor có 3 thread.

Ví dụ JDK 17 độc lập **`JdkFixedRateProbe.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class JdkFixedRateProbe {

    public static void main(String[] args) throws Exception {
        ScheduledThreadPoolExecutor pool =
                new ScheduledThreadPoolExecutor(3);

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger invocations = new AtomicInteger();

        ScheduledFuture<?> task = pool.scheduleAtFixedRate(() -> {
            int nowActive = active.incrementAndGet();
            maximum.accumulateAndGet(nowActive, Math::max);

            int run = invocations.incrementAndGet();

            try {
                if (run == 1) {
                    firstStarted.countDown();
                    if (!releaseFirst.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("gate timeout");
                    }
                }
                if (run == 2) {
                    secondStarted.countDown();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
        }, 0, 20, TimeUnit.MILLISECONDS);

        try {
            if (!firstStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("first did not start");
            }

            System.out.println("first started = true");
            System.out.println("second while first blocked = "
                    + (secondStarted.getCount() == 0));

            releaseFirst.countDown();

            if (!secondStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("second did not start");
            }

            System.out.println("second after release = true");
            System.out.println("max concurrent = " + maximum.get());
        } finally {
            releaseFirst.countDown();
            task.cancel(true);
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
~~~

Chạy:

~~~bash
javac JdkFixedRateProbe.java
java JdkFixedRateProbe
~~~

**Stdout khi chương trình hoàn tất bình thường:**

~~~text
first started = true
second while first blocked = false
second after release = true
max concurrent = 1
~~~

### Tại sao kết quả này không phụ thuộc “ngủ đủ lâu”?

- `firstStarted.await(timeout)` chỉ trả thành công sau khi run 1 thực sự bắt đầu.
- Run 1 đang giữ `releaseFirst` nên chưa thể kết thúc. Theo contract JDK, run 2 của **cùng** periodic registration không được bắt đầu lúc ấy.
- Việc kiểm tra `getCount()` là quan sát tức thời **trước khi mở gate**, không dựa vào đoán 20ms hay 200ms đã trôi.
- Sau `releaseFirst.countDown()`, scheduler có cơ hội chạy run 2. `secondStarted.await(timeout)` chờ chứng cứ thay vì đợi sleep.
- `maximum` ghi số execution cùng lúc; vì cùng periodic task không overlap, giá trị tối đa là 1.
- Timeout 5s/20s là **guard** cho CI bị treo, không dùng để định nghĩa kết quả. Nếu môi trường quá tải khiến timeout, test nên thất bại có lý do thay vì in số giả.
- `finally` cancel periodic future và shutdown pool để JVM không bị giữ bởi worker sau test.

**Giới hạn:** ví dụ chỉ chứng minh invariant của **một registration trong JDK `ScheduledThreadPoolExecutor`**. Nó không chứng minh hai method Spring khác nhau, ba pod hoặc hai `@Scheduled` trên cùng method không overlap.

## 10. Spring @Scheduled thực tế: ThreadPoolTaskScheduler

`ThreadPoolTaskScheduler` là triển khai Spring của `TaskScheduler`, dựa trên `ScheduledExecutorService`. Nó có pool threads để xử lý các scheduled jobs, khác với `ThreadPoolTaskExecutor` dùng cho việc thực thi tasks nói chung.

Cấu hình:

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulerConfiguration {

    @Bean("taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("scheduled-");
        return scheduler;
    }
}
~~~

Đây là **class cấu hình Spring**, cần Spring context/app. Spring container khởi tạo và quản lý shutdown của bean.

### 10.1. Vì sao pool size 3 không khiến một @Scheduled chạy 3 bản?

`setPoolSize(3)` cho executor khả năng chạy nhiều **scheduled task series khác nhau** trên nhiều worker. Nó không tự phá non-overlap của **cùng `ScheduledFuture` periodic series** do JDK quản lý.

Nếu chỉ một `@Scheduled(fixedRate=...)` đang chạy lâu hơn period, tăng 1 lên 3 worker không giải quyết việc nó bị trễ. Cần giảm thời lượng, điều chỉnh period, thay đổi dependency, tách workload có chủ đích hoặc đổi chính sách nghiệp vụ.

### 10.2. Pool size vẫn rất quan trọng

Nếu có ba job khác nhau:

~~~java
@Scheduled(fixedRate = 2, timeUnit = TimeUnit.SECONDS)
public void heartbeat() { /* quick */ }

@Scheduled(fixedRate = 2, timeUnit = TimeUnit.SECONDS)
public void reconcile() { /* slow I/O */ }

@Scheduled(fixedRate = 2, timeUnit = TimeUnit.SECONDS)
public void updateCache() { /* medium */ }
~~~

Đây là **ba method snippets**. Với một scheduler thread, job chậm có thể làm heartbeat trễ. Với pool size đủ lớn, các **series khác** có thể dùng worker riêng, nhưng mỗi series vẫn phải xử lý backlog/overload riêng. Tăng worker có giới hạn theo CPU, DB pool và API quota; không cứ tăng vô hạn.

## 11. Maven + Spring 6.2 + Java 17: test một @Scheduled thật

Ví dụ này dùng một `@Scheduled(fixedRate=20ms)` chạy trên `ThreadPoolTaskScheduler` có **3 worker**. Lần đầu bị giữ bởi latch, nên lần sau **chưa thể vào method** cho đến khi lần đầu được thả. Chương trình in các cờ Boolean ổn định thay vì timestamp do hệ điều hành quyết định.

### 11.1. Cấu trúc dự án

~~~text
spring-fixed-rate-probe/
├── pom.xml
└── src/main/java/demo/SpringFixedRateProbe.java
~~~

**Tệp `pom.xml`:**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-fixed-rate-probe</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
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

**Tệp `src/main/java/demo/SpringFixedRateProbe.java`:**

~~~java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class SpringFixedRateProbe {

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    public static class Config {
        @Bean("taskScheduler")
        ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(3);
            scheduler.setThreadNamePrefix("schedule-probe-");
            return scheduler;
        }

        @Bean
        Probe probe() {
            return new Probe();
        }
    }

    public static class Probe {
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final CountDownLatch secondStarted = new CountDownLatch(1);
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();
        final AtomicInteger invocations = new AtomicInteger();

        @Scheduled(
                fixedRate = 20,
                timeUnit = TimeUnit.MILLISECONDS,
                scheduler = "taskScheduler")
        public void tick() {
            int active = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(active, Math::max);
            int number = invocations.incrementAndGet();

            try {
                if (number == 1) {
                    firstStarted.countDown();
                    if (!releaseFirst.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("gate timeout");
                    }
                }
                if (number == 2) {
                    secondStarted.countDown();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            Probe p = context.getBean(Probe.class);
            if (!p.firstStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("first not started");
            }

            System.out.println("first started = true");
            System.out.println("second while first blocked = "
                    + (p.secondStarted.getCount() == 0));

            p.releaseFirst.countDown();
            if (!p.secondStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("second not started");
            }

            System.out.println("second after release = true");
            System.out.println("max concurrent = "
                    + p.maxInFlight.get());
        }
    }
}
~~~

Chạy:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.SpringFixedRateProbe
~~~

**Stdout của class demo khi chạy thành công** (không tính log Maven/Spring):

~~~text
first started = true
second while first blocked = false
second after release = true
max concurrent = 1
~~~

### 11.2. Diễn giải

- `@EnableScheduling` bật scheduled annotation processor.
- Spring tạo một `taskScheduler` có ba worker.
- Bean `Probe` là bean managed, method `tick()` có **một `@Scheduled` registration**.
- Invocation đầu đếm số active và chờ latch. Trong thời gian chờ, lần thứ hai của **cùng periodic series** không bắt đầu, dù pool còn hai worker khác.
- Main mở latch, lần kế tiếp vào method. Counter xác nhận không có hai invocation chạy cùng lúc.
- Spring context đóng theo `try-with-resources` và quản lý lifecycle scheduler; chương trình không phải tự gọi `System.exit` hoặc để worker chạy mãi.
- Timeout chỉ là guard, không phải cách “ước lượng 20ms đã trôi”. Không in tên thread/timestamp biến thiên vào expected stdout.

**Lưu ý khi dùng CI:** nếu môi trường bị treo quá các timeout guard thì ví dụ sẽ fail rõ ràng. Không được đổi expected stdout thành mốc giây cứng như 00.020, 00.040; thread scheduling có jitter.

## 12. Một thay đổi nhỏ khiến công việc thật có thể overlap: giao sang @Async

Giả sử một trigger chỉ gửi việc nền rồi trả về:

~~~java
@Service
class AsyncIndexWorker {
    @Async("indexExecutor")
    public void rebuildAsync() {
        rebuildIndexTakingLongTime();
    }
}

@Component
class IndexTrigger {
    private final AsyncIndexWorker worker;

    IndexTrigger(AsyncIndexWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.SECONDS)
    public void tick() {
        worker.rebuildAsync();
    }
}
~~~

Đây là **hai class snippets Spring**, cần import `@Service`, `@Component`, `@Async`, `@Scheduled`, `TimeUnit`, cấu hình `@EnableScheduling`, `@EnableAsync`, bean `indexExecutor` và method nghiệp vụ. Phải gọi qua bean proxy; `this.rebuildAsync()` trong cùng bean có thể bypass `@Async`.

Ở đây:

1. Scheduled tick gọi `worker.rebuildAsync()`.
2. Async proxy gửi task vào executor rồi trả nhanh.
3. Scheduled invocation xem như **đã hoàn tất** ngay sau dispatch, dù việc rebuild thật còn chạy.
4. Tick sau một giây có thể gửi rebuild thứ hai, gây **hai rebuild cùng lúc** nếu async pool có worker và chính sách cho phép.

Vì vậy, bất biến “một registration fixedRate không overlap” chỉ áp dụng cho **thời gian thực thi của chính scheduled runnable**, không phải mọi việc nền mà nó nộp đi nhưng không đợi hoàn tất.

### 12.1. Nếu cần single-flight trong một JVM

Một guard local:

~~~java
private final AtomicBoolean running = new AtomicBoolean();

@Scheduled(fixedRate = 1, timeUnit = TimeUnit.SECONDS)
public void tick() {
    if (!running.compareAndSet(false, true)) {
        recordSkippedTick();
        return;
    }

    try {
        CompletableFuture<Void> work = worker.rebuildAsync();
        work.whenComplete((value, error) -> {
            try {
                if (error != null) {
                    reportFailure(error);
                }
            } finally {
                running.set(false);
            }
        });
    } catch (RuntimeException ex) {
        running.set(false); // Bao gồm rejection ngay lúc dispatch.
        throw ex;
    }
}
~~~

Đây là **snippet tích hợp**, giả định `worker.rebuildAsync()` trả `CompletableFuture<Void>`, cùng các method `recordSkippedTick`/`reportFailure` và imports `AtomicBoolean`/`CompletableFuture`. **Không** dùng signature `void` của ví dụ trước nếu code cần `whenComplete`; phải đổi worker thành một async method trả future đúng contract.

Điểm quan trọng:

- `running.compareAndSet` chỉ chặn dispatch mới khi job trước còn in-flight **trên cùng instance/JVM**.
- Gắn `whenComplete` để reset guard **sau completion thật**, không reset ngay sau gọi `@Async`.
- Guard không thay thế monitoring error/cancellation, không đảm bảo exactly-once, không hoạt động trên nhiều pod.
- Nếu `reportFailure` tự ném exception, `finally` vẫn reset flag.
- Nếu process crash, in-memory flag mất; business side effect phải idempotent hoặc có job store.

### 12.2. Reactive @Scheduled không giống method synchronous blocking

Spring 6.1+ hỗ trợ một số kiểu trả về reactive/publisher cho `@Scheduled`. Framework có thể đăng ký việc **subscribe** định kỳ; việc hoàn tất subscription không luôn đồng nghĩa với việc scheduled runnable phải chặn đến khi reactive pipeline xong. Với `fixedRate`, các pipeline bất đồng bộ có thể cùng tồn tại nếu mỗi subscription chưa kết thúc. Cần hiểu backpressure, cancellation, retry, subscription lifetime và concurrency của Reactor chain tương ứng.

Đừng suy từ quy tắc JDK periodic Runnable rằng mọi database call asynchronous phát ra từ một scheduled tick cũng sẽ tự tuần tự hóa.

## 13. Hai @Scheduled độc lập trên cùng method cũng có thể overlap

Spring `@Scheduled` là **repeatable annotation**; mỗi khai báo có trigger riêng:

~~~java
@Scheduled(fixedRate = 3, timeUnit = TimeUnit.SECONDS)
@Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
public void refresh() {
    doWork();
}
~~~

Đây là **snippet**. Spring có thể đăng ký hai scheduled task series độc lập. Với scheduler có đủ worker, một lần kích hoạt từ schedule A có thể overlap một lần từ schedule B, dù cùng gọi một Java method.

Tương tự nếu hai `@Scheduled` method khác nhau cùng gọi một service không thread-safe. Nếu mỗi method có period dài, điều đó không ngăn các khoảng thực thi giao nhau.

### Nếu method gắn @Scheduled ở nhiều bean instances

Nếu ứng dụng tạo hai bean instances có cùng annotation, hoặc vừa dùng mechanism container vừa tự đăng ký cùng task, có thể có hai independent registrations. Không nên dùng `new` thủ công cho scheduled component rồi tự gọi method để suy là processor cũng sẽ quản lý nó; Spring chỉ đăng ký bean mà nó xử lý.

### Nếu có ba replicas

Mỗi replica có scheduler trong JVM của nó. Khi ba pod chạy cùng `fixedRate`, có thể có ba lần thực hiện việc nghiệp vụ. Chúng cũng có thể **lệch pha**, vì mốc bắt đầu của fixedRate thường gắn với lúc đăng ký tại từng instance, không đồng bộ với một mốc UTC chung.

**Không dùng `fixedRate` như cơ chế distributed leader election, distributed mutex hoặc exactly-once billing.**

## 14. Catch-up, skip và coalescing: yêu cầu nghiệp vụ khác nhau

Nếu một run mất 15 giây trong khi `P=5s`, có ít nhất ba câu hỏi:

1. Có cần xử lý **mọi tick đã đi qua** như từng công việc bắt buộc?
2. Nếu bỏ một tick, dữ liệu cuối có vẫn đúng?
3. Nếu xử lý bù nhiều tick sát nhau, downstream có chịu được tải không?

`fixedRate` nói về **cơ chế scheduling**, không quyết định được cả ba câu trả lời nghiệp vụ.

### 14.1. Catch-up theo periodic schedule

Trong JDK scheduled executor, các mốc dự kiến vẫn cách nhau `P`. Khi task bị trễ, một execution kế tiếp có thể chạy ngay sau execution hiện tại vì mốc planned đã quá khứ. Nếu những lần sau rất nhanh, có thể thấy các invocation liên tiếp sát nhau cho đến khi bắt kịp nhịp, nhưng vẫn không overlap **cùng periodic registration**.

**Không** nên hứa mọi tick đều được persist/replay qua restart. Đây là cơ chế trong-memory runtime, không phải event log.

### 14.2. Skip-on-busy

Nếu công việc chỉ cập nhật trạng thái mới nhất, có thể chọn:

~~~text
Tick mới -> có job đang chạy?
     yes -> ghi metric skipped, không dispatch thêm
      no -> bắt đầu xử lý latest snapshot
~~~

Đó là một **business policy riêng**. Với một sync method trên JDK periodic future, run kế tiếp vốn đợi run trước xong; nếu muốn **không bắt kịp tick cũ**, có thể cần mechanism trigger có skip semantics hoặc một aggregator riêng, thay vì chỉ áp giả định `fixedRate` sẽ tự skip.

### 14.3. Coalesce

Nếu trong 10 giây có 5 lần yêu cầu refresh cùng một cache, bạn có thể **gộp** chúng thành một lần refresh lấy state mới nhất. Điều này phù hợp với state synchronization hoặc cache refresh, nhưng không phù hợp với 5 sự kiện thanh toán độc lập. Coalesce cần định nghĩa rõ event identity, state source và mất thông tin gì nếu bỏ intermediates.

### 14.4. Catch-up có durability

Với billing mỗi 5 phút, đừng đếm số lần scheduled method được gọi rồi phát hóa đơn trực tiếp. Nên có bảng:

~~~sql
CREATE TABLE billing_period_jobs (
    tenant_id      VARCHAR(100) NOT NULL,
    period_start   TIMESTAMPTZ NOT NULL,
    status         VARCHAR(30) NOT NULL,
    PRIMARY KEY (tenant_id, period_start)
);
~~~

Đây là **DDL minh họa PostgreSQL**. Trigger định kỳ tìm các kỳ chưa được xử lý, claim atomic và thực hiện job idempotent theo `tenant_id + period_start`. Khi app restart, việc xử lý kỳ còn thiếu dựa vào **business dates/DB state**, không dựa vào số tick của Java scheduler còn nhớ.

Cần thiết kế status, lease/recovery, idempotency với provider bên ngoài, atomicity và quyền truy cập thích hợp; table đơn giản chưa tự bảo đảm exactly-once end-to-end.

## 15. Khi công việc chạy lâu, giải pháp là gì?

### 15.1. Đầu tiên: đo đúng nơi mất thời gian

Phân chia một lần run thành:

~~~text
scheduled time -> actual start -> DB query -> HTTP call ->
transform -> persist -> actual finish
         |                                 |
         |------ scheduler lag -----------?|
                execution duration
~~~

Nên thu thập:

- `job_start_lag_seconds` nếu biết planned time.
- `job_run_duration_seconds` với monotonic timer.
- `job_success_total`/`job_failure_total`.
- `job_timeout_total`, `job_cancelled_total`.
- `scheduler_pool_active_threads` và queue/delayed task metrics nếu scheduler expose.
- `downstream_latency`, connection pool saturation, lock contention.
- `concurrent_job_count` (đặc biệt nếu chuyển qua `@Async`).
- `job_skipped_or_coalesced_total` nếu có policy đó.

Phân biệt **scheduler lag** với **job run duration**. Một job có thể chỉ chạy 30ms nhưng bắt đầu chậm 5 phút vì scheduler pool nghẽn, nên chỉ log duration của method không đủ.

### 15.2. Giảm thời lượng

- Tối ưu query, batch I/O, index; loại bỏ N+1 queries.
- Dùng HTTP connect/read timeout, giới hạn downstream concurrency.
- Bỏ sleep/poll vô hạn trong scheduler thread.
- Giữ transaction/lock scope ngắn; tránh khóa DB xuyên toàn bộ network call.
- Nếu workflow lớn, tách thành job bền vững và cho scheduler làm nhiệm vụ **enqueue/báo tín hiệu** trong thời gian ngắn.

### 15.3. Chọn period phù hợp

Nếu p95 thời lượng một invocation đã lớn hơn period, hệ thống có thể không theo kịp nhịp trong điều kiện bình thường. Một lựa chọn là tăng period, dùng `fixedDelay` nếu chấp nhận nhịp phụ thuộc completion, hoặc thiết kế xử lý theo batch/state.

Không chỉ chỉnh period để che lỗi downstream đang quá tải; đánh giá SLO, độ mới dữ liệu và nhu cầu business.

### 15.4. Tách scheduler và worker khi cần

Để scheduler không bị giữ bởi một công việc dài, có thể schedule một **dispatcher ngắn** rồi gửi work sang worker pool có giới hạn. Nhưng đổi sang dispatch nhanh tạo một nguy cơ mới: **overlapping background jobs và backlog**, nên phải bổ sung single-flight/bulkhead, queue capacity, rejection, metrics và cancellation.

Nếu cần đảm bảo không mất job khi crash, dùng durable job queue thay vì `ThreadPoolTaskExecutor` RAM-only. Sự tách rời này không tự tăng năng lực downstream.

### 15.5. Không tăng poolSize mù quáng

Nếu chỉ có **một** job chiếm 12 giây mỗi 5 giây, tăng scheduler poolSize từ 1 lên 16 **không** khiến cùng periodic task trong JDK chạy 16 invocation song song. Nếu có 16 job khác nhau tranh worker, tăng pool size có thể cải thiện sự công bằng nhưng cũng tăng pressure lên DB/HTTP.

### 15.6. Khi có nguy cơ overflow

Một API downstream trả 429/503 khi scheduler liên tục bắn các request, bạn cần rate limiter, backoff/circuit breaker, retry budget và deadline. Nếu retry trong chính scheduled method rồi ngủ vài chục giây, method có thể chiếm scheduler thread và kéo dài lag. Đặt retry ở ranh giới đúng và tránh nhân retries qua nhiều tầng.

## 16. Scheduler cadence không đồng nghĩa số business jobs đang xử lý

Một lần gọi `@Scheduled(fixedRate=...)` có thể chỉ gửi một job sang một service `@Async` và **trả về ngay**. Khi đó nguyên tắc non-overlap của một periodic registration chỉ giới hạn *scheduled callback*, không giới hạn thời gian các business tasks đang chạy trên worker khác.

```text
fixedRate tick --> short dispatcher --> bounded executor --> business completion
       |                |                    |                   |
 planned cadence    callback ends       accepted/queued      success/failure
```

Với một worker pool có 10 threads, một scheduler thread vẫn có thể gửi nhiều job đang chạy hoặc nằm trong queue. Ngược lại, scheduler pool 10 threads không làm một periodic registration của `ScheduledThreadPoolExecutor` tự overlap chính nó. Nếu nghiệp vụ muốn **chỉ một job đang hoạt động**, dùng single-flight guard trong scope thích hợp hoặc claim/job store; đừng đồng nhất hai loại pool.

Nếu cần xử lý nhiều partition, hãy gán partition key và business idempotency key cho từng work item, đặt giới hạn tổng concurrency, quan sát completion và retry từng partition. Trường hợp `@Scheduled` phối hợp trực tiếp với `@Async` có những mốc riêng: trigger, submit, worker finish và durable commit; không dùng kết thúc callback như bằng chứng đã xong business.


## 17. Lỗi trong periodic task có thể ảnh hưởng lần tiếp theo

Đây là nuance quan trọng giữa JDK raw và Spring wrapper.

### 17.1. JDK ScheduledThreadPoolExecutor

Trong JDK `scheduleAtFixedRate`, nếu một execution ném exception ra ngoài `Runnable.run()`, **những execution tiếp theo của cùng periodic task bị suppress**. `ScheduledFuture.get()` có thể báo `ExecutionException` cho series kết thúc bất thường; đối với periodic series còn chạy bình thường, `get()` **không** phải một cách chờ “run tiếp theo” như one-shot.

~~~java
ScheduledFuture<?> periodic =
        scheduledExecutor.scheduleAtFixedRate(
                () -> performWork(),
                0, 1, TimeUnit.SECONDS);
~~~

Đây là **snippet**, `scheduledExecutor` và `performWork` phải được khai báo. Đừng gọi `periodic.get()` trên request thread để đợi một tick: periodic future không kết thúc bình thường sau mỗi invocation.

### 17.2. Spring @Scheduled với handler

Spring thường bọc task định kỳ bằng error handler. Với cấu hình handler mặc định dành cho repeating tasks của `TaskUtils`, exception có thể được **log và suppress để các tick kế tiếp vẫn tiếp tục**. Nếu custom `ErrorHandler` tái ném exception hoặc behavior của scheduler khác, series có thể dừng.

Vì vậy, đừng dạy rằng “mọi `@Scheduled` lỗi một lần là dừng luôn” hoặc “mọi `@Scheduled` luôn tiếp tục dù lỗi”. Hãy quan sát error handling thực tế của `ThreadPoolTaskScheduler`/framework version, kiểm thử cả exception path.

### 17.3. Lỗi có thể tái diễn theo fixed rate

Nếu dữ liệu hỏng khiến method thất bại liên tục, fixedRate ngắn có thể log lỗi rất nhanh, tăng tải/alert noise. Đặt exception classifier, circuit breaker, cooldown, rate limit hoặc disable job có kiểm soát. Nếu method chạy một transaction dài rồi ném lỗi, không nên giả định rollback của một resource đồng nghĩa toàn bộ downstream side effect đã được hoàn tác.

### 17.4. Lỗi trong background work

Nếu scheduled method chỉ dispatch `CompletableFuture`/`@Async` và return, exception của background task không nhất thiết đi qua error handler của scheduler. Phải có `Future`/callback/job status hoặc `AsyncUncaughtExceptionHandler` phù hợp. Đây là một lý do khác khiến fixedRate schedule status **không phản ánh trạng thái business job** nếu đã tách worker.

## 18. Cancellation và shutdown

Với scheduling trực tiếp từ `TaskScheduler`, các API trả `ScheduledFuture<?>` có thể hủy:

~~~java
ScheduledFuture<?> future =
        scheduler.scheduleAtFixedRate(
                () -> poll(),
                Duration.ofSeconds(5));

future.cancel(false);
~~~

Đây là **snippet**, cần `scheduler` và `poll`. `cancel(false)` không cố interrupt invocation đang chạy; `cancel(true)` có thể cố interrupt tùy executor, nhưng không đảm bảo blocking I/O dừng hoặc business side effect rollback.

Đối với `@Scheduled` được framework đăng ký tự động, lifecycle thường gắn với bean/context và việc cancel xảy ra khi bean/context đóng. Nếu cần pause/resume hoặc disable dynamic job, hãy dùng cơ chế quản lý scheduler/registration có chủ đích, không tự sửa annotation ở runtime rồi đoán task sẽ biến mất.

Khi shutdown:

- Dừng nhận work mới.
- Xác định task đang chạy cần hoàn tất hay hủy theo deadline.
- Hủy kế hoạch recurring để không còn tick mới.
- Nếu đã dispatch sang worker/queue, dừng đúng workflow và lưu trạng thái.
- Thực hiện cleanup resource và log shutdown outcome.
- Với job không được mất qua crash, persist intention và phục hồi theo job store.

`ThreadPoolTaskScheduler` của Spring 6.1+ hỗ trợ các khả năng pause/resume và graceful shutdown theo lifecycle; lựa chọn thực tế phụ thuộc cấu hình container và yêu cầu business.

## 19. Runtime clock và độ chính xác của scheduled execution

`fixedRate` dùng khoảng thời gian tương đối để thiết lập nhịp; `timeUnit` chuyển đơn vị, không có `zone` cho fixedRate. Mốc thực thi vẫn có thể bị trễ vì:

- OS thread scheduling.
- CPU hoặc scheduler workers bận.
- GC pause và stop-the-world pause.
- Lock contention.
- Network I/O chậm hoặc downstream timeout.
- Application đang startup/shutdown.
- Host throttling hoặc container CPU quota.

Do đó, “configured 100ms” **không** có nghĩa mọi run thật bắt đầu cách nhau chính xác 100ms. Đặt `P` quá nhỏ để đạt timer chính xác cỡ microsecond là không phù hợp với Spring scheduled tasks dùng JVM general-purpose.

### Đo thời gian đúng loại

~~~java
long started = System.nanoTime();
try {
    doWork();
} finally {
    long elapsedNanos = System.nanoTime() - started;
    recordDuration(elapsedNanos);
}
~~~

**Snippet**. `System.nanoTime()` là nguồn đo elapsed/monotonic trong một JVM, không phải Unix timestamp. Nếu cần log planned/actual ở UTC, lưu `Instant` riêng; không convert `nanoTime()` thành timestamp lịch.

### Tìm mốc planned ở đâu?

`@Scheduled` không tự inject một tham số `plannedAt` vào synchronous method. Nếu cần đo **precise schedule lag**, hãy thiết kế một wrapper/trigger/job metadata có planned timestamp và correlation ID, hoặc instrumentation của scheduler tương ứng. **Đừng mặc định lấy `Instant.now() - P` là mốc đã được schedule chính xác**, vì registration start, drift, missed periods và restart có thể thay đổi pha.

## 20. Sự khác nhau giữa “bắt kịp lịch” và “bắt kịp dữ liệu”

Giả sử cứ 5 giây bạn poll một bảng event:

~~~text
t0:    query events after cursor 100
t+5s:  query events after cursor 120
t+10s: query events after cursor 150
~~~

Nếu t0 mất 15 giây, cố chạy các invocation missed ngay sau đó **không nhất thiết** giúp xử lý event đầy đủ. Điều quyết định dữ liệu đúng là **cursor/idempotency và trạng thái đã commit**.

Thay vì dựa vào số lần được scheduler gọi:

~~~java
public void pollNewEvents() {
    long cursor = stateStore.loadCommittedCursor();
    Batch batch = eventStore.readAfter(cursor, batchSize);
    processIdempotently(batch);
    stateStore.commitCursor(batch.lastProcessedId());
}
~~~

Đây là **snippet**; các service/field là giả định, cần xử lý empty batch, transactionality và crash giữa processing/commit. Mục đích là chỉ ra **đơn vị tiến độ nghiệp vụ** nên nằm trong trạng thái bền chứ không phải “đã chạy bao nhiêu ticks”.

Với mọi nguồn dữ liệu thay đổi, nếu bỏ một tick nhưng lần sau đọc được tất cả event chưa xử lý, hệ thống vẫn có thể đúng. Ngược lại, nếu mỗi tick đại diện một business period bắt buộc, việc bỏ tick mà không lưu business key là nguy hiểm.

## 21. fixedRate là cadence tương đối, không phải giờ dân sự

Mốc `fixedRate` tương đối với lúc scheduling được đăng ký/initial delay: hai pod khởi động lệch 12 giây có thể có hai chu kỳ lệch pha. Nếu nghiệp vụ đòi chạy **09:00 theo một vùng giờ**, dùng `cron` và `zone`, kiểm thử DST và định nghĩa business date; `fixedRate=24h` không thay thế giờ địa phương.

Sau restart, scheduled task trong RAM được đăng ký lại và không tự nhớ số lần đã bỏ lỡ. Nếu mỗi kỳ bắt buộc phải được xử lý, quét các kỳ còn thiếu từ job store và áp idempotency thay vì đếm tick.


## 22. Concurrency guard và dữ liệu khi có nhiều registrations

Các executions kế tiếp của **một** JDK fixed-rate periodic task không overlap, nhưng hai `@Scheduled` declarations hoặc hai bean instances là hai registrations riêng và có thể chạy đồng thời. Nếu chúng cùng thực hiện `counter++` trên shared field, việc cập nhật không atomic; `AtomicInteger` sửa race của bộ đếm nhưng không chống duplicate business side effects.

Với hóa đơn hoặc đồng bộ external API, dùng business key (tenant, kỳ, operation), unique constraint/transaction và claim idempotent. `AtomicBoolean` hay `synchronized` chỉ giúp trong scope local và không tự bảo vệ nhiều JVM; nếu scheduled method chỉ giao cho async worker, guard phải được giữ đến **completion của worker**, không reset ngay lúc submit.


## 23. Kiểm thử: không dựa vào sleep làm bằng chứng

### 23.1. Pure unit test của timeline

Đặt `period=1000ms`, durations `{700,1300,1700,200}`. Assert mốc planned, actual, lag như bảng mục 5. Không cần Spring context, scheduler hoặc đồng hồ thật. Mô phỏng khái niệm “nhịp lịch” trước khi test implementation.

### 23.2. Integration test của một periodic registration

Dùng `CountDownLatch` như hai demo. Khi run đầu bị giữ, assert run thứ hai chưa vào. Sau khi release, assert run 2 bắt đầu và maximum active luôn là 1.

Nếu scheduler pool size >1, kết quả này vẫn đúng cho **cùng registration** trên JDK scheduled executor; đây là một test rất có giá trị vì phản bác ngộ nhận “nhiều worker sẽ tự overlap cùng `@Scheduled`”.

### 23.3. Test khi @Scheduled dispatch sang @Async

Cho async worker bị giữ bởi latch nhưng scheduled method return nhanh. Nếu async pool có hơn một worker và không có guard, có thể thấy hai async tasks cùng active. Đây là **behavior mong muốn để hiểu** chứ không nhất thiết bug; nghiệp vụ phải quyết định có được phép hay không.

### 23.4. Test hai independent registrations

Đặt hai `@Scheduled` annotations trên cùng method và một scheduler có pool size ≥2. Dùng latch/barrier để xác nhận có thể overlap, nhưng tránh assertion đòi cả hai vào trong một số millisecond cụ thể. Nếu môi trường không có đủ worker, không kết luận không overlap là contract chung.

### 23.5. Test exception path

- Với JDK raw scheduled executor, tạo Runnable ném exception và quan sát periodic future kết thúc exceptional; những lượt sau bị suppress.
- Với Spring error handler mặc định cho repeating tasks, kiểm tra log/metric và việc còn tick sau exception khi áp dụng đúng cấu hình.
- Với `@Async` background failure, kiểm tra future/handler riêng; scheduler error handler không thay thế async observer.
- Dọn scheduled future/context/executor sau mỗi test, tránh test suite bị treo.

### 23.6. Test tải

Mô phỏng job có p95 runDuration > period, đo schedule lag, mức sử dụng worker, concurrent job count, tài nguyên downstream, số missed/coalesced và số duplicate business effect. Tăng period/pool hoặc tách worker dựa trên số liệu, không chọn theo cảm giác.

## 24. Những ngộ nhận cần tránh

**1. “fixedRate luôn đợi P sau khi chạy xong.”** Sai. Đó là `fixedDelay`; fixedRate dựa vào cadence planned starts.

**2. “Task chạy lâu hơn P thì fixedRate tự mở thêm thread để chạy lần sau.”** Sai với cùng periodic registration trên `ScheduledThreadPoolExecutor`; các run không overlap.

**3. “Pool size 10 nên một @Scheduled được gọi 10 lần song song.”** Sai với một periodic registration; pool size giúp các task khác có cơ hội chạy.

**4. “Vì fixedRate không overlap nên toàn hệ thống không có duplicate.”** Sai khi nhiều schedules, bean instances, pods hoặc async delegation.

**5. “@Scheduled(fixedRate=5000) nghĩa là mỗi 5000 giây.”** Sai. Default milliseconds, trừ khi dùng `timeUnit`.

**6. “initialDelay làm job đúng phút chẵn trên đồng hồ.”** Sai. Nó là delay tương đối từ registration, không phải cron calendar alignment.

**7. “fixedRate luôn skip những tick bị lỡ.”** Không phải bảo đảm như vậy; cùng periodic series có thể chạy sát nhau để bắt kịp nhịp.

**8. “fixedRate bảo đảm mọi tick sẽ tồn tại qua restart.”** Sai. Scheduled tasks thông thường trong JVM không phải durable event log.

**9. “Nếu rate period = 1000ms thì actual start phải cách đúng 1000ms.”** Sai khi OS/GC/pool hoặc run duration làm trễ.

**10. “Cùng method có hai @Scheduled nên vẫn chỉ có một lịch.”** Sai. Hai annotation được xử lý độc lập, có thể overlap.

**11. “@Async task vẫn thuộc run của @Scheduled tới khi async hoàn tất.”** Không nếu scheduled method chỉ dispatch rồi return.

**12. “Một ScheduledFuture.get() trả về sau mỗi tick.”** Sai với periodic future bình thường; `get()` không phải await-next-tick API.

**13. “JDK và Spring luôn phản ứng giống nhau với exception của periodic task.”** Sai. Spring có error-handler wrapper có thể suppress để tiếp tục, còn raw JDK periodic future suppress lượt sau khi run ném ra ngoài.

**14. “fixedRate thích hợp cho chạy 09:00 mỗi ngày xuyên DST.”** Không mặc định; dùng cron + zone explicit.

**15. “Nếu tăng worker thì CPU/DB chắc chắn sẽ bắt kịp.”** Không nếu bottleneck là downstream/lock/DB hoặc một series không được overlap.

**16. “Counter gọi tick là nguồn chân lý số business periods hoàn tất.”** Sai. Cần durable business key/watermark và trạng thái xử lý.

**17. “Scheduler thread chỉ chạy code nhẹ nếu method annotated ngắn.”** Không nếu method gọi blocking DB/HTTP hoặc bị lock; measure runtime thực.

**18. “Chỉ cần guard AtomicBoolean là chạy đúng một lần trên cluster.”** Sai. Guard local không giải quyết nhiều JVM/pod hoặc crash.

**19. “@Scheduled kết thúc thành công nghĩa background job đã thành công.”** Sai nếu method chỉ gửi vào `@Async` hoặc queue.

**20. “Không cần xử lý exception vì cứ có tick sau là tự sửa.”** Sai. Lỗi deterministic có thể lặp vô hạn, gây log storm/overload và lệch dữ liệu.

## 25. Bài tập luyện tập

### Bài 1 — Tính lag

Period = 1 giây, run 1 mất 0,4 giây, run 2 mất 1,8 giây. Nếu mốc planned là 0, 1, 2, lần 3 bắt đầu lúc nào trong mô hình một task tuần tự lý tưởng?

**Đáp án:** run 1 bắt đầu 0, xong 0,4; run 2 bắt đầu 1, xong 2,8; run 3 planned 2 nhưng thực bắt đầu 2,8 → lag 0,8 giây.

### Bài 2 — Có chạy song song?

`ThreadPoolTaskScheduler` có pool size 8, một method sync `@Scheduled(fixedRate=1000)` mất 3 giây. Có tám invocation đồng thời của cùng registration không?

**Đáp án:** không đối với triển khai periodic trên `ScheduledThreadPoolExecutor`. Invocation sau bắt đầu muộn, có thể chạy sát sau invocation trước; không tự nhân bản.

### Bài 3 — Async delegation

Một method scheduled mỗi 1 giây gọi `worker.runAsync()`, work thật mất 10 giây. Có thể có 10 jobs đang chạy đồng thời không?

**Đáp án:** có thể nếu async executor/queue đủ khả năng và không có single-flight guard. Scheduled invocation có thể kết thúc ngay sau dispatch; non-overlap của scheduled Runnable không bảo vệ thời gian sống của async tasks.

### Bài 4 — Chọn fixedDelay

Một crawler phải nghỉ 2 giây sau khi lần quét trước hoàn thành để tránh API bị rate limited. Chọn fixedRate hay fixedDelay?

**Đáp án:** fixedDelay = 2 giây hợp semantics, cùng với timeout và rate policy. FixedRate có thể chạy ngay sau một lần quét quá chậm, không cho thời gian nghỉ.

### Bài 5 — Cron

Báo cáo cần chạy 09:00 giờ Việt Nam mỗi ngày. Dùng `fixedRate=24h` có đủ không?

**Đáp án:** không phải biểu đạt tốt. Dùng cron `0 0 9 * * *` với zone `Asia/Ho_Chi_Minh`; nếu yêu cầu không mất/không trùng kỳ, thêm durable job key theo date/tenant.

### Bài 6 — Exception

Một JDK periodic task ném unchecked exception không được bắt. `ScheduledFuture` của series tiếp tục bình thường không?

**Đáp án:** không; raw JDK `scheduleAtFixedRate` suppress các run sau và future có exceptional termination. Với Spring wrapper cần kiểm tra error handler, không áp kết luận JDK tuyệt đối sang mọi `@Scheduled`.

### Bài 7 — Restart

Pod restart sau 10 phút outage trong khi `fixedRate=1minute`. Nó có bắt buộc tạo đủ 10 events bền ngay khi quay lại không?

**Đáp án:** không. Một lịch trong-memory được đăng ký lại không phải durable ledger. Nếu cần, quét business periods còn thiếu từ job store và xử lý idempotent.

### Bài 8 — Duplicate

Hai pods chạy cùng `@Scheduled(fixedRate=5s)` và cùng tạo hóa đơn cho ngày hôm nay. Cần gì để tránh phát hành hai hóa đơn?

**Đáp án:** phân quyền thực thi bằng distributed claim/lease hoặc leader phù hợp, và **idempotency** theo tenant + ngày + loại hóa đơn với unique constraint/transaction; không chỉ Java `synchronized`.

### Bài 9 — Quan sát

Task chạy 100ms nhưng bắt đầu trễ 2 phút. Metric run duration có đủ phát hiện không?

**Đáp án:** không. Cần đo scheduler lag từ planned tới actual, cùng queue/pool saturation và trạng thái triggers.

### Bài 10 — Chọn chiến lược backlog

Một task refresh cache cập nhật state mới nhất, bị trễ 30 giây. Có cần chạy bù 30 lần “mỗi giây” không?

**Đáp án:** thường không, nếu chỉ cần state mới nhất; có thể coalesce/skip hoặc đọc lại state hiện tại. Với mỗi tick đại diện một sự kiện nghiệp vụ riêng thì phải lưu business key và xử lý đủ.

## 26. Checklist triển khai

- [ ] Xác định `fixedRate` là planned cadence, không phải delay sau completion.
- [ ] Biết `timeUnit` (mặc định ms) và `initialDelay`; kiểm tra config thực tế.
- [ ] Xác định scheduler implementation và **phạm vi một periodic registration**.
- [ ] Không kỳ vọng cùng periodic task trên `ScheduledThreadPoolExecutor` tự overlap khi chạy lâu.
- [ ] Đếm tất cả independent registrations, repeatable annotations, bean instances và pods.
- [ ] Nếu tách `@Async`, có giới hạn in-flight/queue, error observer và lifecycle riêng.
- [ ] Có chính sách skip/coalesce/catch-up rõ theo business outcome.
- [ ] Với job quan trọng, dùng durable job key, idempotency và recovery sau restart.
- [ ] Có timeout/deadline cho I/O, không block scheduler thread vô hạn.
- [ ] Tách scheduler pool với worker pool khi cần nhưng không tăng concurrency vô hạn.
- [ ] Đo planned-vs-actual lag và execution duration, không chỉ log số tick.
- [ ] Hiểu khác biệt exception handling của JDK raw periodic future và Spring repeating-task error handler.
- [ ] Hủy recurring schedules và shutdown Spring context/executor đúng.
- [ ] Dùng fixedDelay khi muốn nghỉ sau hoàn thành; dùng cron + zone cho lịch dân sự.
- [ ] Đảm bảo multi-instance không phát business effect trùng theo tenant/period key.
- [ ] Test deterministic timeline, test latch non-overlap, test async overlap và error path.
- [ ] Không đưa latency exact milisecond không ổn định làm assertion cứng trong CI.

## 27. Tổng kết

**`@Scheduled(fixedRate=P)` là nhịp lịch bắt đầu dự kiến, không phải khoảng nghỉ sau kết thúc.** Với Spring `ThreadPoolTaskScheduler` trên `ScheduledThreadPoolExecutor`, một execution kéo dài hơn `P` làm run sau trễ hoặc chạy ngay sau run trước; **một periodic registration không tự chạy chồng lên chính nó**. Tăng worker pool không phá bất biến này.

Muốn biết công việc thật có overlap, cần nhìn **toàn bộ architecture**: có nhiều registration, có `@Async`/reactive delegation, có nhiều bean/pod, có queue và nhiều worker hay không. Muốn hệ thống đúng, còn phải xác định **bỏ tick, gộp tick hay xử lý bù**, cùng idempotency/job state và giới hạn overload.

Chọn `fixedDelay` khi cần nghỉ sau completion; chọn cron + zone cho lịch theo ngày giờ; chọn durable job store khi không được mất kỳ nghiệp vụ qua crash/restart. Luôn đo **scheduler lag** riêng với **run duration** và kiểm thử bằng đồng bộ xác định, không chỉ bằng `Thread.sleep`.

## Tài liệu tham khảo chính thức

- Spring Framework 6.2 — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html
- Spring Framework 6.2 — `@Scheduled` (fixedRate, initialDelay, timeUnit, repeatable): https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html
- Spring Framework 6.2 — `@EnableScheduling`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html
- Spring Framework 6.2 — `TaskScheduler`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/TaskScheduler.html
- Spring Framework 6.2 — `ThreadPoolTaskScheduler`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html
- Spring Framework 6.2 — `TaskUtils` (default error handlers): https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/TaskUtils.html
- Java SE 17 — `ScheduledThreadPoolExecutor` (periodic non-overlap): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html
- Java SE 17 — `ScheduledExecutorService.scheduleAtFixedRate`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html
- Java SE 17 — `CountDownLatch`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-tasks-005">initialDelay và đơn vị timeUnit</a>

<details>
<summary>Xem chi tiết</summary>

# initialDelay và đơn vị timeUnit trong Spring @Scheduled

## 1. Vì sao cần phân biệt initialDelay với chu kỳ chạy?

Một ứng dụng Spring có thể dùng `@Scheduled` để thăm dò trạng thái đơn hàng, cập nhật cache, làm sạch dữ liệu tạm hoặc gửi các bản tổng hợp định kỳ. Có những job không nên chạy ngay lúc ứng dụng khởi động: cấu hình chưa được làm nóng, kết nối tới dịch vụ ngoài còn đang ổn định, hoặc ta chỉ muốn tránh gọi một API ngay khi bean được đăng ký.

Spring cung cấp thuộc tính `initialDelay` để **lùi lần chạy đầu tiên** của một tác vụ `fixedRate` hoặc `fixedDelay`. Spring cũng có thuộc tính `timeUnit` để giải thích đơn vị thời gian của các con số khai báo trong `@Scheduled`.

Ví dụ:

```java
@Scheduled(initialDelay = 5, fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
public void refreshCache() {
    // Phần xử lý nghiệp vụ.
}
```

Đây là **fragment** đặt trong một Spring-managed bean; cần `@EnableScheduling` và import `java.util.concurrent.TimeUnit`, `org.springframework.scheduling.annotation.Scheduled`.

Ý nghĩa:

- Sau khi **đăng ký schedule**, lần chạy đầu thường được lên lịch **sau 5 giây** (có thể thực thi muộn hơn nếu scheduler đang bận).
- Sau khi **mỗi lần thực thi kết thúc**, Spring đợi thêm **30 giây** trước lần fixed-delay tiếp theo.
- Cả hai con số `5` và `30` đều mang đơn vị **giây**, vì `timeUnit = TimeUnit.SECONDS`.
- `initialDelay` không lặp lại trước mỗi lần chạy; nó chỉ điều khiển **lần đầu**.

**Điểm nhớ:** `initialDelay` = độ trễ khởi đầu; `fixedDelay` = khoảng chờ sau khi lần trước hoàn tất; `fixedRate` = nhịp kỳ vọng giữa các lần kích hoạt; `timeUnit` = cách đọc đơn vị các giá trị tương ứng.

## 2. Các thuộc tính liên quan và mặc định chính xác

Trong Spring Framework 6.2, `@Scheduled` khai báo sáu thuộc tính thời gian có liên quan đến `timeUnit`:

| Thuộc tính | Kiểu | Mặc định | Ý nghĩa |
| --- | --- | --- | --- |
| `initialDelay` | `long` | `-1` | Độ trễ trước lần thực thi đầu tiên của fixed-rate/fixed-delay |
| `initialDelayString` | `String` | Chuỗi rỗng | Độ trễ khởi đầu viết dưới dạng chuỗi, property expression hoặc duration |
| `fixedDelay` | `long` | `-1` | Khoảng cách từ lúc **kết thúc lần trước** đến lúc **bắt đầu lần sau** |
| `fixedDelayString` | `String` | Chuỗi rỗng | Phiên bản cấu hình động/duration của fixed delay |
| `fixedRate` | `long` | `-1` | Khoảng nhịp định kỳ của cùng một fixed-rate registration |
| `fixedRateString` | `String` | Chuỗi rỗng | Phiên bản cấu hình động/duration của fixed rate |
| `timeUnit` | `TimeUnit` | `TimeUnit.MILLISECONDS` | Đơn vị fallback cho sáu giá trị thời gian ở trên |

Hai giá trị mặc định rất dễ nhầm:

- `initialDelay = -1` có nghĩa **không khai báo một độ trễ đầu tiên cụ thể**. Nó không phải yêu cầu scheduler chờ **âm một mili giây**.
- `timeUnit = MILLISECONDS` có nghĩa mọi giá trị số không có đơn vị tường minh được hiểu là **mili giây** theo mặc định.

Ví dụ:

```java
@Scheduled(initialDelay = 10, fixedRate = 20)
public void fastJob() {
    // initialDelay 10 ms, fixedRate 20 ms.
}
```

Đây là **fragment**. Nếu người viết muốn 10 và 20 **giây** nhưng quên `timeUnit`, schedule sẽ chạy nhanh hơn dự định khoảng một nghìn lần. Đó là lỗi đặc biệt nguy hiểm khi job gọi dịch vụ trả phí hoặc cập nhật dữ liệu có side effect.

### 2.1. initialDelay=0 khác “không cấu hình”

`initialDelay = 0` là khai báo một độ trễ bằng không; đây là **không có thời gian chờ chủ động** cho lần đầu của lịch fixed-rate/fixed-delay. Nó không bảo đảm method bắt đầu thực thi đúng tại khoảnh khắc ứng dụng lên trạng thái ready, bởi việc đăng ký task, scheduler thread, contention và lifecycle đều có thể tạo thêm trễ.

Khi bỏ thuộc tính `initialDelay`, Spring dùng mặc định `-1` biểu diễn *unspecified*. Trong trường hợp đăng ký fixed-rate/fixed-delay hợp lệ, kết quả thực tế thường là lần đầu đủ điều kiện chạy ngay khi scheduling được thiết lập. Đừng dùng sự trùng hợp này để suy rằng mọi giá trị âm đều là cách khuyến nghị điều khiển delay; hãy ghi rõ `0` hoặc một delay dương khi yêu cầu nghiệp vụ cần tường minh.

### 2.2. timeUnit thuộc về toàn annotation

Một `@Scheduled` có **một** giá trị `timeUnit`, không có một timeUnit riêng cho initialDelay và một cái khác cho fixedDelay/fixedRate.

Ví dụ:

```java
@Scheduled(initialDelay = 2, fixedRate = 5, timeUnit = TimeUnit.MINUTES)
public void synchronizeCatalog() {
    // Đợi 2 phút trước lần đầu; fixed rate 5 phút.
}
```

Muốn khởi đầu là **30 giây** nhưng chu kỳ là **5 phút**, có thể đổi tất cả sang giây, ví dụ `initialDelay=30`, `fixedRate=300`, `timeUnit=SECONDS`; hoặc dùng các thuộc tính `String` có đơn vị tường minh, được giải thích ở phần sau.

## 3. Trình tự thời gian: initialDelay chỉ tác động lần chạy đầu

Với:

```java
@Scheduled(initialDelay = 2, fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
public void reload() {
    doReload();
}
```

Giả sử schedule được đăng ký tại t=0s và mỗi lượt `doReload()` mất 3s.

```text
t=0          t=2       t=5           t=10       t=13          t=18
 |------------|----------|-------------|----------|-------------|
   initial     run #1      fixed delay  run #2     fixed delay   run #3 ...
   delay 2s    3s          5s           3s         5s
```

Lần 1 **bắt đầu xấp xỉ t=2s**; chạy tới t=5s. Lần 2 bắt đầu xấp xỉ t=10s; không phải t=7s, vì fixed-delay tính từ **lúc hoàn tất lượt trước**.

Sơ đồ là mô hình lý tưởng, giả định scheduler có worker rảnh và không bị pause. Trong thực tế, một task đến hạn có thể **bắt đầu muộn hơn** vì scheduler pool bận, JVM pause hoặc hệ điều hành chưa lập lịch thread. `initialDelay=2s` không phải SLA “phải chạy đúng sau chính xác 2.000ms”.

### 3.1. Phân biệt với fixedRate

Với:

```java
@Scheduled(initialDelay = 2, fixedRate = 5, timeUnit = TimeUnit.SECONDS)
public void sample() {
    readSensors();
}
```

Các mốc fixed-rate mong muốn là xấp xỉ t=2,7,12,17 giây. Nếu một lần chạy lâu hơn chu kỳ, lần tiếp theo **có thể bị trễ**, không bắt buộc tạo một phiên bản task mới chạy chồng lấn. Trên `ScheduledThreadPoolExecutor` của JDK, các lần thực thi kế tiếp của **cùng một periodic registration** không overlap; nhiều registrations độc lập vẫn có thể chạy song song khi có threads.

Trong cả fixed-rate và fixed-delay, **initialDelay chỉ thiết lập mốc khởi đầu**. Không lấy initialDelay cộng vào mỗi period về sau.

### 3.2. Nếu không khai báo initialDelay

```java
@Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
public void poll() {
    fetchUpdates();
}
```

Fragment trên có nhịp 5 giây và không yêu cầu delay ban đầu. Khi Spring đăng ký lịch, lượt đầu thường đủ điều kiện chạy ngay, nhưng thời điểm bắt đầu thực tế vẫn phụ thuộc scheduler. Muốn trì hoãn lần đầu, khai báo `initialDelay` một cách rõ ràng.

## 4. timeUnit thay đổi con số nào?

Mặc định của `timeUnit` là `TimeUnit.MILLISECONDS`. Từ Spring 5.3.10 trở đi, thuộc tính này cho phép tránh các con số lớn khó đọc như `300000`.

Một số ví dụ tương đương:

| Khai báo | Trễ ban đầu | Chu kỳ |
| --- | --- | --- |
| `initialDelay=5000, fixedDelay=30000` | 5 giây | 30 giây |
| `initialDelay=5, fixedDelay=30, timeUnit=SECONDS` | 5 giây | 30 giây |
| `initialDelay=1, fixedRate=2, timeUnit=MINUTES` | 1 phút | 2 phút |
| `initialDelay=750, fixedRate=2000, timeUnit=MILLISECONDS` | 750ms | 2 giây |

**Đổi `timeUnit` mà không đổi các số** sẽ đổi toàn bộ ý nghĩa thời gian. Ví dụ:

```java
@Scheduled(initialDelay = 10, fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
```

và

```java
@Scheduled(initialDelay = 10, fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
```

không tương đương: cái thứ nhất là 10 giây/30 giây, cái thứ hai là 10 phút/30 phút. Hai annotation trên là **fragment của khai báo method**, chưa phải chương trình hoàn chỉnh.

### 4.1. timeUnit là enum của JDK

`@Scheduled.timeUnit` có kiểu `java.util.concurrent.TimeUnit`. Những giá trị như `MILLISECONDS`, `SECONDS`, `MINUTES` và `HOURS` đều dùng được khi phù hợp. Đây không phải một String như `"seconds"`, không phải `Duration` object và cũng không liên quan trực tiếp đến timezone.

Khi nhiều nhóm cùng chỉnh cấu hình, ưu tiên dùng `TimeUnit.SECONDS` hoặc chuỗi duration có đơn vị thay vì buộc người đọc tự đoán rằng `5000` có nghĩa là mili giây.

## 5. initialDelayString, fixedDelayString và fixedRateString

Một phương thức thường cần nhận các khoảng thời gian từ application configuration, chứ không hardcode trực tiếp vào source code. Các biến thể `String` hỗ trợ việc đó:

```java
@Scheduled(
    initialDelayString = "${jobs.catalog.initial-delay}",
    fixedDelayString = "${jobs.catalog.delay}"
)
public void refresh() {
    refreshCatalog();
}
```

Đây là fragment. Hai key cần xuất hiện trong PropertySources/environment phù hợp, ví dụ:

```properties
jobs.catalog.initial-delay=10s
jobs.catalog.delay=PT2M
```

Spring có thể resolve biểu thức `${...}` và diễn giải Duration. Đối với Java config không dùng Boot, có thể thêm property source hoặc `@PropertySource`/resolver thích hợp trước khi bean post-processor đăng ký schedule.

### 5.1. Các kiểu chuỗi duration

Theo Javadoc Spring 6.2, các biến thể *String có thể nhận:

| Giá trị chuỗi | Ý nghĩa | Lý do |
| --- | --- | --- |
| `"1500"` | Mặc định 1500ms | Chỉ có số, chưa có đơn vị tường minh |
| `"1500"` với `timeUnit=SECONDS` | 1500 giây | Số thuần dùng timeUnit làm đơn vị fallback |
| `"750ms"` | 750 mili giây | SIMPLE duration format có đơn vị |
| `"5s"` | 5 giây | SIMPLE duration có đơn vị tường minh |
| `"2m"` | 2 phút | SIMPLE duration có đơn vị tường minh |
| `"PT5S"` | 5 giây | ISO-8601 Duration |
| `"PT2M"` | 2 phút | ISO-8601 Duration |

**Quy tắc ưu tiên:** đơn vị **viết trực tiếp trong chuỗi duration** có hiệu lực cao hơn timeUnit; timeUnit chỉ là fallback nếu chuỗi là số thuần/không ghi đơn vị.

Ví dụ:

```java
@Scheduled(
    initialDelayString = "750ms",
    fixedDelayString = "PT2S",
    timeUnit = TimeUnit.MINUTES
)
public void fetch() {
    // Khởi đầu 750ms, sau đó fixed delay 2s.
}
```

Mặc dù `timeUnit=MINUTES`, initialDelay **không phải 750 phút** và fixedDelay **không phải 2 phút**. Hai chuỗi đã mang đơn vị `ms` và `S` (ISO Duration) rõ ràng, vì vậy chúng được hiểu theo đơn vị của chính chúng.

### 5.2. Chuỗi chỉ có số sử dụng timeUnit

```java
@Scheduled(
    initialDelayString = "2",
    fixedRateString = "5",
    timeUnit = TimeUnit.SECONDS
)
public void sampleStringNumbers() {
    doWork();
}
```

Fragment này có delay 2 giây và fixed rate 5 giây, không phải 2ms/5ms. Nếu bỏ `timeUnit`, các chuỗi số thuần này mặc định được hiểu bằng mili giây.

### 5.3. Ưu tiên dùng giá trị có đơn vị trong configuration

Trong Spring Boot `application.properties`, viết:

```properties
jobs.sync.initial-delay=15s
jobs.sync.fixed-delay=PT1M
```

thường dễ review hơn `jobs.sync.initial-delay=15000` và `jobs.sync.fixed-delay=60000`. Khi môi trường staging/production dùng các đơn vị khác nhau, chuỗi tường minh tránh việc người vận hành tưởng một con số là giây trong khi app hiểu là mili giây.

Với giá trị đặc biệt nhỏ, kiểm tra xem chuỗi duration có chính xác theo phiên bản Spring thực tế; **đừng tự đoán cú pháp** `"1sec"` hay `"2minutes"` là hợp lệ nếu chưa xác nhận qua tài liệu/bộ parser mà ứng dụng sử dụng.

## 6. Những tổ hợp @Scheduled không hợp lệ

Các tham số thời gian chỉ hợp lý khi xác định **một cơ chế kích hoạt chính**.

### 6.1. Đừng khai báo initialDelay và initialDelayString cùng lúc

Ví dụ **không hợp lệ**, chỉ dùng để nhận diện lỗi:

```java
@Scheduled(
    initialDelay = 2,
    initialDelayString = "3s",
    fixedRate = 10,
    timeUnit = TimeUnit.SECONDS
)
public void invalidDelayConfiguration() {}
```

Spring kiểm tra không được cung cấp cả hai dạng initial delay có hiệu lực đồng thời. Chọn **một**: dạng số hoặc dạng chuỗi. Tương tự, chọn cơ chế fixedDelay hoặc fixedRate hoặc cron, đừng trộn các chế độ schedule mâu thuẫn trong cùng một annotation.

### 6.2. Cron KHÔNG hỗ trợ initialDelay

Ví dụ **không hợp lệ**, ứng dụng có thể lỗi khi khởi tạo scheduling:

```java
@Scheduled(cron = "0 */5 * * * *", initialDelay = 10, timeUnit = TimeUnit.SECONDS)
public void invalidCronInitialDelay() {}
```

Với Spring Framework 6.2, thuộc tính `initialDelay` và `initialDelayString` là để lùi lần đầu của **fixed-rate hoặc fixed-delay**, không phải cron triggers. Spring kiểm tra và báo lỗi khi kết hợp initialDelay có hiệu lực với cron.

Nếu muốn cron chỉ chạy sau khi ứng dụng sẵn sàng hoặc sau một sự kiện nghiệp vụ, hãy thiết kế **readiness guard**, cấu hình bật/tắt job, hay điều khiển đăng ký schedule bằng API tương ứng. Không cố dùng `initialDelay` với cron.

### 6.3. timeUnit không điều khiển cron expression

```java
@Scheduled(
    cron = "0 */10 * * * *",
    zone = "Asia/Ho_Chi_Minh",
    timeUnit = TimeUnit.MINUTES
)
public void periodicCronJob() {
    doWork();
}
```

Đây là fragment. Biểu thức cron được đọc theo **cú pháp cron sáu trường của Spring**, trong đó trường đầu tiên là **giây**, chứ không lấy đơn vị từ `timeUnit`. Thuộc tính `timeUnit` bị **bỏ qua cho cron**; `zone` điều khiển timezone dùng để tính lịch cron, không phải timeUnit.

Nhiều người nhầm `timeUnit=MINUTES` sẽ biến biểu thức cron “mỗi 10 phút” thành “mỗi 10 giờ”, nhưng hai thứ hoàn toàn độc lập.

### 6.4. Lịch nhanh quá mức có thể làm ứng dụng quá tải

`@Scheduled(fixedRate = 1)` khi mặc định `MILLISECONDS` có thể lên lịch rất dày. Nếu mỗi lượt gọi network hoặc DB, hệ thống có thể không theo kịp; tăng pool không bảo đảm throughput tương ứng. Dùng đơn vị tường minh, kiểm soát backlog/concurrency và xem actual task runtime trước khi chọn chu kỳ.

## 7. Đừng nhầm initialDelay với trạng thái ứng dụng sẵn sàng

`initialDelay` được tính từ khi **schedule được Spring đăng ký** theo lifecycle của scheduler, không được hứa tính từ một mốc business như:

- Khi tất cả remote services đã healthy.
- Khi dữ liệu migration đã hoàn tất trên mọi instance.
- Khi pod đã chuyển sang ready trong Kubernetes.
- Khi người dùng đầu tiên đăng nhập.
- Khi một giao dịch đầu tiên được commit.

Nếu mục tiêu là “không làm gì cho đến khi service thật sự sẵn sàng”, chỉ dùng một delay cố định thường không đủ: môi trường có lúc khởi động nhanh, có lúc chậm. Hãy cân nhắc trạng thái readiness, callback lifecycle thích hợp, hoặc thiết kế cho job kiểm tra điều kiện trước khi chạy.

**Mỗi lần restart/redeploy** thường đăng ký các schedules mới và initialDelay được áp dụng lại; đây không phải một timestamp cố định được lưu bền vững qua các lần khởi động. Với business deadline/recurrence phải tồn tại qua restart, in-memory `@Scheduled` không thay thế job store hoặc scheduler bền vững.

## 8. Ví dụ runnable: đối chứng số, String và Duration thực tế

Ví dụ dùng **Java 21 + Spring Framework 6.2.1 + Maven**, không cần Spring Boot. Ba method được lập lịch:

1. `numericValues()`: `initialDelay=1, fixedRate=2, timeUnit=SECONDS`. Kiểm tra lượt đầu có delay và lượt thứ hai đến theo chu kỳ khoảng 2 giây.
2. `bareStrings()`: `initialDelayString="${demo.start}"` và `fixedDelayString="${demo.every}"`; hai property đều chứa **số thuần** nên dùng `timeUnit=SECONDS`.
3. `explicitDurations()`: `initialDelayString="750ms"`, `fixedDelayString="PT2S"` nhưng `timeUnit=MINUTES`. Đơn vị ghi trong chuỗi **được ưu tiên**, không phải 750 phút.

Chương trình dùng `CountDownLatch` để đợi lần chạy mong muốn, `System.nanoTime()` để đo duration và in các boolean/assertions thay vì in số mili giây “chính xác” không ổn định giữa các máy.

### 8.1. Cấu trúc

```text
initial-delay-demo/
├── pom.xml
└── src/main/java/demo/InitialDelayDemo.java
```

**Tệp `pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>initial-delay-demo</artifactId>
  <version>1.0.0</version>
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
          <mainClass>demo.InitialDelayDemo</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

### 8.2. Tệp Java hoàn chỉnh

**Tệp `src/main/java/demo/InitialDelayDemo.java`:**

```java
package demo;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class InitialDelayDemo {
    public static class Probe {
        final long created = System.nanoTime();
        final AtomicLong numericFirst = new AtomicLong();
        final AtomicLong numericSecond = new AtomicLong();
        final AtomicLong bareFirst = new AtomicLong();
        final AtomicLong durationFirst = new AtomicLong();
        final AtomicInteger numericRuns = new AtomicInteger();
        final CountDownLatch numericTwo = new CountDownLatch(2);
        final CountDownLatch bareOne = new CountDownLatch(1);
        final CountDownLatch durationOne = new CountDownLatch(1);
        final AtomicReference<String> observedThread = new AtomicReference<>("");

        @Scheduled(initialDelay = 1, fixedRate = 2, timeUnit = TimeUnit.SECONDS)
        public void numericValues() {
            observedThread.compareAndSet("", Thread.currentThread().getName());
            int call = numericRuns.incrementAndGet();
            if (call == 1) {
                numericFirst.set(System.nanoTime());
                numericTwo.countDown();
            } else if (call == 2) {
                numericSecond.set(System.nanoTime());
                numericTwo.countDown();
            }
        }

        @Scheduled(initialDelayString = "${demo.start}",
                   fixedDelayString = "${demo.every}",
                   timeUnit = TimeUnit.SECONDS)
        public void bareStrings() {
            bareFirst.compareAndSet(0, System.nanoTime());
            bareOne.countDown();
        }

        @Scheduled(initialDelayString = "750ms", fixedDelayString = "PT2S",
                   timeUnit = TimeUnit.MINUTES)
        public void explicitDurations() {
            durationFirst.compareAndSet(0, System.nanoTime());
            durationOne.countDown();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    public static class AppConfig {
        @Bean("taskScheduler")
        public ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(3);
            scheduler.setThreadNamePrefix("scheduled-");
            return scheduler;
        }

        @Bean
        public Probe probe() {
            return new Probe();
        }
    }

    private static void await(CountDownLatch latch, String label)
            throws InterruptedException {
        if (!latch.await(6, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for " + label);
        }
    }

    public static void main(String[] args) throws Exception {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("demo", Map.of(
                        "demo.start", "1",
                        "demo.every", "2")));
        context.register(AppConfig.class);
        context.refresh();

        try (context) {
            Probe probe = context.getBean(Probe.class);
            await(probe.numericTwo, "two numeric executions");
            await(probe.bareOne, "bare-string execution");
            await(probe.durationOne, "explicit-duration execution");

            long numericFirstMs = TimeUnit.NANOSECONDS.toMillis(
                    probe.numericFirst.get() - probe.created);
            long numericPeriodMs = TimeUnit.NANOSECONDS.toMillis(
                    probe.numericSecond.get() - probe.numericFirst.get());
            long bareFirstMs = TimeUnit.NANOSECONDS.toMillis(
                    probe.bareFirst.get() - probe.created);
            long durationFirstMs = TimeUnit.NANOSECONDS.toMillis(
                    probe.durationFirst.get() - probe.created);

            boolean numericFirstDelayed = numericFirstMs >= 700;
            boolean numericRepeatPeriodic = numericPeriodMs >= 1500;
            boolean bareStringUsesSeconds = bareFirstMs >= 700;
            boolean explicitUnitsOverrideMinutes =
                    durationFirstMs >= 450 && durationFirstMs < 6000;
            boolean schedulerThread = probe.observedThread.get()
                    .startsWith("scheduled-");

            if (!numericFirstDelayed || !numericRepeatPeriodic ||
                    !bareStringUsesSeconds || !explicitUnitsOverrideMinutes ||
                    !schedulerThread) {
                throw new AssertionError("Scheduler timing validation failed");
            }

            System.out.println("numericFirstDelayed=" + numericFirstDelayed);
            System.out.println("numericFixedRateRepeat=" + numericRepeatPeriodic);
            System.out.println("bareStringUsesTimeUnit=" + bareStringUsesSeconds);
            System.out.println("explicitDurationOverridesTimeUnit=" +
                    explicitUnitsOverrideMinutes);
            System.out.println("runsOnSchedulerThread=" + schedulerThread);
        }
    }
}
```

### 8.3. Chạy ví dụ

Yêu cầu Maven và JDK 21 trở lên. Trong thư mục `initial-delay-demo`, chạy:

```bash
mvn -q -DskipTests compile exec:java
```

**Output thực tế đã kiểm chứng với Spring Framework 6.2.1:**

```text
numericFirstDelayed=true
numericFixedRateRepeat=true
bareStringUsesTimeUnit=true
explicitDurationOverridesTimeUnit=true
runsOnSchedulerThread=true
```

Những dòng boolean thể hiện các điều kiện đã đo và được chương trình assert; không phải số mili giây cụ thể. Khi test chạy trong môi trường CI bị pause quá lâu hoặc scheduler worker không có CPU, một phép kiểm tra thời gian giới hạn có thể false-negative; cần dùng ngưỡng/thời gian chờ phù hợp với môi trường test hoặc kiểm thử sâu bằng scheduler/clock kiểm soát được.

### 8.4. Vì sao ví dụ không in thời gian chính xác?

`initialDelay = 1 second` được diễn giải thành một khoảng trễ dự kiến, nhưng lần chạy thực tế có thể trễ thêm vì startup, scheduler contention và JVM scheduling.

Ví dụ lấy `Probe.created` trước lúc scheduling hoàn tất đăng ký; khi callback chạy, nó lưu `System.nanoTime()`. Các assertion chỉ kiểm tra những bất biến thực tế:

- `numericFirstMs >= 700`: lần đầu không diễn ra ngay trước khi schedule được đăng ký đầy đủ.
- `numericPeriodMs >= 1500`: hai lần fixed-rate không chạy dồn sát nhau như thể cấu hình bằng mili giây.
- `bareFirstMs >= 700`: chuỗi số thuần `"1"` với `timeUnit=SECONDS` hoạt động ở thang giây.
- `durationFirstMs` trong cửa sổ hữu hạn: `"750ms"` không bị diễn giải thành 750 phút.
- Callback thực thi trên thread có prefix `scheduled-` thuộc scheduler do Spring quản lý.

Vì các mốc capture nằm trên cùng JVM, phép trừ `nanoTime()` phù hợp để đo elapsed time. Đừng so sánh trực tiếp `nanoTime()` của hai service khác nhau.

### 8.5. Tại sao tạo ThreadPoolTaskScheduler có ba thread?

Để ba lịch minh họa có cơ hội chạy độc lập, tránh một method giữ scheduler thread làm nhiễu quan sát của method khác. Mặc định `ThreadPoolTaskScheduler` trong Spring Framework 6.2 có `poolSize=1`; với nhiều job độc lập, một job chậm có thể trì hoãn job khác.

Cấu hình `setPoolSize(3)` chỉ phục vụ test này; production cần sizing theo số scheduled jobs, runtime và giới hạn downstream, không dùng 3 như một con số khuyến nghị chung.

## 9. Cách kiểm tra initialDelay thực tế khi chạy nhiều job

Nếu cần đo sát hơn, hãy phân biệt ít nhất ba timestamp:

1. **Registered/due time:** thời điểm scheduler đăng ký trigger và thời điểm nó đủ điều kiện chạy lần đầu theo initialDelay.
2. **Actual start time:** thời điểm worker bắt đầu method.
3. **Finish time:** thời điểm method thực hiện xong phần việc.

Nếu chỉ log “job started at 10:00:03”, bạn không biết delay 3 giây là do `initialDelay=3s` hay do job đã đến hạn sớm hơn nhưng chờ scheduler thread rảnh.

Một logic đo khung:

```java
long startedAtNanos = System.nanoTime();
try {
    performTask();
} finally {
    long elapsed = System.nanoTime() - startedAtNanos;
    observeExecutionTime(elapsed);
}
```

Đây là **fragment**, cần `performTask()` và `observeExecutionTime(...)`. Nó đo **thời gian chạy**, không đo thời gian task bị chờ trước khi method được gọi. Muốn đo scheduling lateness cần biết expected due time từ registration/trigger.

Đối với lịch cron, expected fire time còn phụ thuộc timezone và missed-firing policy; đừng tự tính bằng cách cộng fixed period vì cron không phải một fixed-rate schedule.

## 10. Điều kiện infrastructure để initialDelay có hiệu lực

`initialDelay` và `timeUnit` chỉ có tác dụng khi `@Scheduled` được Spring nhận diện: bean được container quản lý, context bật `@EnableScheduling` và method không nhận tham số. Bạn có thể khai báo một `@Bean("taskScheduler") ThreadPoolTaskScheduler` với pool size và thread prefix phù hợp; Spring sẽ quản lý vòng đời bean và dùng cơ chế chọn scheduler của context.

Từ Spring 6.1+, `@Scheduled(scheduler = "fastScheduler", initialDelay = 30, fixedRate = 5, timeUnit = TimeUnit.SECONDS)` có thể định tuyến đến scheduler có tên/qualifier đúng. Thuộc tính `scheduler` chọn tài nguyên; `initialDelay`, `fixedRate` và `timeUnit` chỉ xác định **khoảng thời gian**. Nếu scheduler có một worker đang bị job khác giữ, method có thể bắt đầu muộn dù initialDelay không thay đổi; phải đo due-to-start lag và execution duration riêng.


## 11. Spring Boot properties và sự khác biệt với annotation

Khi dùng Spring Boot, bạn có thể cấu hình scheduler auto-configuration:

```properties
spring.task.scheduling.pool.size=4
spring.task.scheduling.thread-name-prefix=job-schedule-
```

Đây **không phải** cách đặt initialDelay cho từng method; nó chỉ cấu hình pool scheduler khi auto-configuration áp dụng. Từng `@Scheduled` vẫn cần các thuộc tính về thời gian như:

```java
@Scheduled(
    initialDelayString = "${jobs.prices.start-delay}",
    fixedRateString = "${jobs.prices.interval}"
)
public void updatePrices() {
    updatePriceSnapshot();
}
```

Với properties:

```properties
jobs.prices.start-delay=30s
jobs.prices.interval=5m
```

Hai ví dụ là **fragments**, cần lớp bean, imports, scheduler và method nghiệp vụ thực tế.

Nếu ứng dụng tự tạo một bean `ThreadPoolTaskScheduler`, các properties Boot dành cho auto-configured scheduler có thể không áp vào bean đó. Nếu bật virtual threads ở Boot version hỗ trợ, scheduler auto-configured có thể là `SimpleAsyncTaskScheduler`, với cơ chế khác so với scheduler pool truyền thống. Cần kiểm tra implementation thực tế, không kết luận từ một property duy nhất.

## 12. initialDelay không phải cách tránh trùng job trong nhiều instance

Giả sử triển khai ba pods cùng một ứng dụng, mỗi pod có:

```java
@Scheduled(initialDelay = 10, fixedRate = 60, timeUnit = TimeUnit.SECONDS)
public void reconcile() {
    reconcilePayments();
}
```

Ba pods đều có thể đăng ký schedule riêng sau khi khởi động. `initialDelay=10s` không tự biến job thành **chỉ một instance thực hiện**, cũng không là distributed lock.

Nếu nghiệp vụ không được phép chạy trùng:

- Dùng distributed coordination, leader election hoặc job store phù hợp.
- Thiết kế task idempotent vì retry/deploy vẫn có thể tạo các lần xử lý lặp.
- Với công việc cần tồn tại qua restart/missed runs, dùng cơ chế lịch bền vững và audit trạng thái; `@Scheduled` chỉ đăng ký task trong tiến trình hiện tại.

**Startup delay** và **business deduplication** là hai khái niệm khác nhau.

## 13. Công việc chạy lâu có ảnh hưởng đến initialDelay không?

Có, nhưng theo cách khác với việc thay đổi giá trị annotation. Ví dụ:

```text
Scheduler pool có 1 worker.
Job A bắt đầu và giữ thread tới t=10s.
Job B được đăng ký ở t=0, initialDelay=2s.
t=2s: B đến hạn, nhưng thread vẫn đang chạy A.
t=10s+: B mới có cơ hội bắt đầu.
```

B không “có initialDelay=10s”; nó có initialDelay 2s nhưng **execution latency vượt lịch** do scheduler resource contention.

Khi xảy ra:

- Xác nhận actual scheduler pool size.
- Đo thời gian đến hạn so với bắt đầu thực thi.
- Tìm job chiếm worker lâu hoặc block network/DB.
- Tăng poolSize có giới hạn nếu nhiều jobs độc lập và downstream cho phép.
- Có thể tách scheduler theo criticality hoặc chuyển phần xử lý nặng sang `TaskExecutor` riêng với bounded submission.

Chú ý: nếu callback `@Scheduled` chỉ submit batch vào executor và trả ngay, fixed-delay/fixed-rate theo dõi **thời gian callback**, không theo dõi kết thúc batch; điều này có thể tạo nhiều batch chồng lấn. Cần có cơ chế không cho trùng, backpressure hoặc task-state management.

## 14. Exception, lifecycle và thời gian lên lịch

### 14.1. Exception không làm thay đổi ý nghĩa timeUnit

Khi một lượt chạy ném exception, Spring scheduling infrastructure có error handling riêng cho recurring tasks. Đừng mặc định mọi periodic job luôn dừng hoặc luôn tiếp tục bất kể lỗi; behavior có thể khác giữa Spring error handler và việc tự dùng JDK ScheduledExecutorService. Hãy ghi log, alert và test cấu hình lỗi thực tế.

`initialDelay` chỉ áp trước lượt đầu; lỗi ở các lượt sau không khiến Spring tự “reset initialDelay” trước lần kế tiếp theo một cơ chế chung.

### 14.2. Dừng và khởi động lại context

Khi Spring context đóng, các scheduled registrations/executor tasks được hủy hoặc shutdown theo lifecycle/config tương ứng. Sau khi ứng dụng khởi động lại, schedule được đăng ký mới và initialDelay thường được tính lại. Điều này khác với durable cron schedule có lưu trạng thái firing.

### 14.3. Nếu job cần chạy đúng theo giờ tuyệt đối

`initialDelay` và `fixedRate`/`fixedDelay` là các **khoảng thời gian tương đối**, không phải lịch “09:00 mỗi ngày”. Với thời điểm theo lịch, dùng cron kèm `zone` hoặc scheduler API/implementation phù hợp. Nhưng cron không hỗ trợ initialDelay trên cùng `@Scheduled` annotation, nên nếu yêu cầu “cron và chỉ bắt đầu sau readiness”, phải thiết kế điều kiện bật job riêng.

### 14.4. timeUnit không thay đổi timeout của phương thức

Thuộc tính `timeUnit` chỉ ảnh hưởng cách diễn giải các delay/period scheduling tương ứng. Nó **không đổi** timeout của HTTP client, `Future.get()`, JDBC, `Thread.sleep()` hay một transaction. Các API này có tham số/kiểu Duration riêng.

Nếu job chạy 10 phút trong khi fixedRate chỉ 5 giây, `timeUnit=SECONDS` không hủy job sau 5 giây. Phải cấu hình execution timeout/cooperative cancellation nếu nghiệp vụ yêu cầu.

## 15. Các lỗi cấu hình phổ biến

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| `@Scheduled(initialDelay=10, fixedDelay=20)` rồi hiểu là giây | Mặc định là **10ms/20ms** | Khai báo `timeUnit=SECONDS` hoặc dùng duration String |
| Nghĩ initialDelay chạy trước **mỗi** lượt | Dự đoán sai mốc lần 2, 3 | Chỉ áp lần đầu; các lượt sau theo fixed-delay/rate |
| Nhầm fixedDelay với fixedRate | Không dự đoán được start-to-start interval | Fixed-delay đếm sau completion; fixed-rate theo nhịp |
| Đặt `initialDelay` cùng `initialDelayString` | Cấu hình mâu thuẫn, có thể lỗi startup | Chọn một biến thể |
| Đặt `initialDelay` trên cron job | Spring không hỗ trợ cron + initialDelay | Dùng readiness guard hoặc điều khiển lịch theo cách khác |
| Nghĩ `timeUnit` thay đổi cron expression | Cron không dùng timeUnit | Sử dụng cron fields/zone đúng cú pháp |
| Chuỗi `"750ms"` với `timeUnit=MINUTES` rồi coi là 750 phút | Explicit duration có đơn vị ưu tiên | Đọc chính đơn vị trong string |
| Chuỗi `"5"` mà quên timeUnit | Mặc định 5ms thay vì 5s | Viết `"5s"` hoặc timeUnit |
| Quên bật `@EnableScheduling` | Annotation không được xử lý đúng trong context thuần | Bật scheduling và dùng Spring-managed bean |
| Dùng `new Job()` ngoài Spring | Không có registration của annotation trên object đó | Tạo bean qua container |
| Khai báo pool nhưng scheduler thật là bean khác | Delay thực tế bị contention | Kiểm tra bean resolution và thread names |
| Tăng pool khi job phụ thuộc database đang nghẽn | Dồn thêm tải xuống downstream | Đo DB/HTTP/lock, giới hạn concurrency |
| Tin initialDelay=0 nghĩa chạy chính xác ở ApplicationReadyEvent | Lifecycle và scheduler không hứa mốc tuyệt đối | Dùng sự kiện readiness khi đó mới là yêu cầu |
| Dùng initialDelay để tránh chạy trùng giữa nhiều pods | Vẫn có nhiều schedules độc lập | Leader election/distributed lock, idempotency |
| Dùng timeout cho Future rồi tưởng schedule đã dừng | Timeout chờ không tự cancel task | Chọn chính sách cancellation riêng |

## 16. Bài tập suy luận

**Bài 1.** Ý nghĩa của:

```java
@Scheduled(initialDelay = 5, fixedDelay = 30)
```

Trả lời: trễ ban đầu 5 **mili giây**, sau khi mỗi lần thực thi kết thúc đợi 30 **mili giây** trước lượt tiếp theo, vì timeUnit mặc định là MILLISECONDS. Đây là fragment annotation, chưa phải một method độc lập.

**Bài 2.** Ý nghĩa của:

```java
@Scheduled(initialDelay = 5, fixedRate = 30, timeUnit = TimeUnit.SECONDS)
```

Trả lời: lần đầu xấp xỉ sau 5 giây kể từ khi đăng ký, rồi cố gắng theo nhịp 30 giây. Fixed-rate không có nghĩa lần tiếp theo tự chạy song song nếu lần trước chưa xong.

**Bài 3.** Đoạn sau trễ ban đầu bao lâu?

```java
@Scheduled(initialDelayString = "750ms",
           fixedDelayString = "PT2S",
           timeUnit = TimeUnit.MINUTES)
```

Trả lời: 750 mili giây; fixed-delay 2 giây. Chuỗi duration có đơn vị rõ ràng nên `MINUTES` không ghi đè chúng.

**Bài 4.** `initialDelayString="5"` và `timeUnit=SECONDS` có nghĩa gì?

Trả lời: 5 giây. Nếu bỏ `timeUnit`, số thuần mặc định dùng mili giây.

**Bài 5.** Tại sao đoạn sau không hợp lệ?

```java
@Scheduled(cron = "0 0 9 * * *", initialDelay = 60, timeUnit = TimeUnit.SECONDS)
```

Trả lời: initialDelay không được hỗ trợ cho cron trigger trong Spring Framework 6.2. Muốn cron 09:00 mỗi ngày, chỉ dùng cron/zone; nếu cần điều kiện khởi đầu thêm, quản lý readiness/schedule registration riêng.

**Bài 6.** Một job fixedDelay mỗi 10 giây nhưng body mất 4 giây, initialDelay 2 giây. Khi scheduler rảnh, hai mốc start đầu tiên cách nhau bao lâu?

Trả lời: xấp xỉ 14 giây (4 giây xử lý + 10 giây sau completion). InitialDelay=2 giây chỉ ảnh hưởng lúc đầu.

**Bài 7.** Một pod khởi động lại liên tục, mỗi lần đặt initialDelay=10 phút. Có đảm bảo job nhất định chạy sau 10 phút hệ thống đã hoạt động liên tục không?

Không. Mỗi lần pod restart và lịch đăng ký lại, delay ban đầu thường bắt đầu lại; nếu restart liên tục trước khi tới hạn, công việc có thể không chạy. Cần cơ chế job bền vững và trạng thái nghiệp vụ nếu không được bỏ sót.

## 17. Checklist áp dụng cho dự án Spring

Trước khi triển khai `@Scheduled`:

- Kiểm tra giá trị `initialDelay` là mặc định `-1`, `0` hay một delay dương; xác định yêu cầu delay đầu một cách rõ ràng.
- Ghi đơn vị bằng `timeUnit=...` hoặc `String` duration tường minh, tránh số không chú thích.
- Có đúng **một** cơ chế periodic: fixedRate, fixedDelay hoặc cron?
- Có vô tình khai báo `initialDelay` và `initialDelayString` đồng thời không?
- Nếu sử dụng cron, đã bỏ `initialDelay` và hiểu `timeUnit` không tác dụng với cron chưa?
- Các giá trị `${...}` được resolve từ nguồn cấu hình đúng và hỗ trợ định dạng duration đang dùng?
- Đã bật `@EnableScheduling`/annotation-driven và tạo bean qua Spring?
- Biết actual TaskScheduler implementation, pool size, thread prefix và các scheduled methods dùng chung pool?
- Nếu job chạy lâu, hiểu fixed-delay/fixed-rate theo completion và các job khác có thể trễ vì contention?
- Có metrics cho actual start, execution time, lateness, failures và shutdown?
- Khi restart, có yêu cầu replay/missed-run tracking hoặc lịch bền vững không?
- Nếu ứng dụng chạy nhiều instances, có idempotency và cơ chế tránh duplicate khi cần không?
- Có test integration sử dụng `CountDownLatch`/monotonic clock thay vì ngủ với khoảng ngẫu nhiên không?

## 18. Kết luận

Trong Spring Framework 6.2, `initialDelay` là **độ trễ của lần thực thi đầu tiên** cho lịch `fixedRate` hoặc `fixedDelay`. `timeUnit` mặc định là `TimeUnit.MILLISECONDS` và áp dụng cho các giá trị thời gian dạng số hoặc chuỗi số không có đơn vị; nó không đổi nghĩa chuỗi Duration đã có đơn vị và **không áp dụng cho cron**.

Ưu tiên cấu hình dễ đọc:

```java
@Scheduled(initialDelay = 5, fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
```

hoặc:

```java
@Scheduled(initialDelayString = "5s", fixedDelayString = "PT30S")
```

Đừng biến initialDelay thành cơ chế kiểm tra readiness, execution timeout, durable scheduling hay distributed lock: các bài toán đó cần những thành phần riêng. Tính đúng của lịch phụ thuộc cả **trigging semantics**, **scheduler capacity**, **lifecycle** và **business safety**.

### Tài liệu tham khảo

- [Spring Framework 6.2.x — Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework 6.2.1 — EnableScheduling Javadoc](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Java SE 21 — TimeUnit](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/TimeUnit.html)
- [Spring Boot — Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-tasks-006">Chạy một lần bằng initialDelay trên phiên bản Spring hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Chạy một lần bằng `initialDelay` trong `@Scheduled` trên phiên bản Spring hỗ trợ

## Mục tiêu

Nhiều ứng dụng cần thực hiện một công việc **chỉ một lần sau khi Spring khởi tạo cơ chế scheduling**, chẳng hạn làm nóng cache cục bộ, khởi động một bước kiểm tra nhẹ hoặc kích hoạt một lần đồng bộ ban đầu. Không phải công việc nào cũng cần chạy lặp lại theo `fixedRate`, `fixedDelay` hoặc `cron`.

**Từ Spring Framework 6.1**, bạn có thể khai báo một tác vụ chạy **một lần** bằng cách chỉ định `initialDelay` và không thêm lịch lặp:

```java
@Scheduled(initialDelay = 1000)
public void warmupOnce() {
    // Một lần sau khoảng trễ ban đầu (đơn vị mặc định: mili giây).
}
```

Đây là một phương thức minh họa trong bean do Spring quản lý, chưa phải chương trình độc lập. Phải bật `@EnableScheduling`, giữ application context hoạt động và dùng phiên bản Spring hỗ trợ. Spring Framework 6.0.x và các dòng cũ hơn **chưa hỗ trợ** cách khai báo chỉ có `initialDelay`: khi đó `initialDelay` chỉ đi kèm lịch lặp.

Sau bài này, bạn sẽ:

1. Phân biệt one-time `@Scheduled(initialDelay=...)` với việc **hoãn lần chạy đầu** của một lịch `fixedRate` hoặc `fixedDelay`.
2. Nhớ mốc hỗ trợ chính xác: **Spring Framework 6.1 trở lên**.
3. Nắm đường đi `@EnableScheduling` → `ScheduledAnnotationBeanPostProcessor` → `OneTimeTask` → `TaskScheduler`.
4. Dùng `timeUnit`, `initialDelayString` và tên scheduler đúng cách.
5. Chạy ví dụ Java 17+ / Spring 6.2.12 xác nhận method chạy trên scheduler worker và hạ tầng chỉ đăng ký **one-time task**.
6. Biết vì sao “chạy một lần trong một context” không đồng nghĩa “exactly once trên mọi replica và restart”.
7. Quyết định khi nào cần thay bằng `TaskScheduler.schedule(Runnable, Instant)`, startup event hoặc một job store bền vững.

**Cốt lõi:** one-time `@Scheduled` là **một đăng ký lịch duy nhất trong vòng đời application context**, không phải giao thức xử lý công việc đúng một lần toàn hệ thống.

## 1. Phân biệt sự tồn tại của thuộc tính với phiên bản hỗ trợ tính năng

`@Scheduled` nằm trong package:

```java
org.springframework.scheduling.annotation.Scheduled
```

Annotation đã có từ lâu, thuộc tính `initialDelay()` cũng xuất hiện trước Spring 6.1. Nhưng **chỉ từ Spring 6.1**, việc *không* khai báo `cron`/`fixedDelay`/`fixedRate` mới được công nhận là **one-time schedule**.

| Phiên bản Spring Framework | Chỉ có `@Scheduled(initialDelay = 1000)` | Diễn giải |
| --- | --- | --- |
| 5.x / 6.0.x | **Không được hỗ trợ như one-time** | Phải có một trong `cron`, `fixedDelay`, `fixedRate`. `initialDelay` chỉ điều chỉnh lần đầu của lịch lặp. |
| **6.1.x** | **Được hỗ trợ** | Tính năng one-time `@Scheduled` chỉ dùng initial delay được thêm ở 6.1. |
| **6.2.x** | **Được hỗ trợ** | Javadoc và reference documentation của 6.2 minh họa cách dùng này. |

Spring Framework 6.1 release notes nêu rõ **one-time tasks with just an initial delay**. Javadoc `Scheduled` của Spring 6.0 chỉ yêu cầu một trong ba loại lịch lặp; Javadoc 6.1/6.2 có thêm trường hợp one-time. Đây là lý do tên bài nhấn mạnh **“trên phiên bản Spring hỗ trợ”**.

**Không suy ra rằng** vì Java source biên dịch được với thuộc tính `initialDelay` trên bản cũ nên Spring bản cũ sẽ chấp nhận cấu hình ấy tại runtime. Việc kiểm tra/ràng buộc annotation do scheduling infrastructure của từng phiên bản thực hiện.

### Kiểm tra phiên bản đang chạy

Với Maven hoặc Spring Boot BOM, cần biết **version Spring Framework có hiệu lực**; phiên bản trong IDE hoặc trong một dependency declaration đơn lẻ không chắc là bản được load thực tế.

Có thể in ra trong code đã tải Spring:

```java
System.out.println(
    org.springframework.core.SpringVersion.getVersion()
);
```

Đây là snippet Java; trong ví dụ hoàn chỉnh dùng Spring 6.2.12. Nếu có nhiều dependency và BOM, kiểm tra dependency tree hoặc resolved dependency graph để tránh version skew.

## 2. Cú pháp one-shot hợp lệ

Cách ngắn nhất:

```java
@Scheduled(initialDelay = 2000)
public void doSomethingOnce() {
    System.out.println("one-shot");
}
```

Số `2000` là **mili giây** theo mặc định. Cách viết rõ đơn vị:

```java
@Scheduled(initialDelay = 2, timeUnit = TimeUnit.SECONDS)
public void doSomethingOnce() {
    System.out.println("one-shot");
}
```

Hai method trên chỉ để minh họa hai lựa chọn tương đương, không phải cùng khai báo trên một bean để mong chỉ chạy một lần. Annotation `@Scheduled` có thể **lặp trên cùng method** và mỗi khai báo tạo một đăng ký lịch riêng.

Một số quy tắc:

- Method scheduled thông thường **không nhận tham số**; có thể inject dependency qua constructor hoặc field của Spring bean.
- Thường trả `void`. Nếu method synchronous có kiểu trả về khác, giá trị trả về không phải một RPC result gửi tới caller.
- `initialDelay=0` vẫn là one-shot trong Spring 6.1+, dự định chạy sớm sau khi registration được thiết lập; không phải `@PostConstruct` đồng bộ.
- Delay dương là khoảng chờ **tính tương đối với lúc hạ tầng đăng ký/sắp lịch**, không phải chính xác từ lúc JVM start hay từ khi HTTP server “ready”.
- Không khai báo thêm `fixedRate`, `fixedDelay` hay `cron` nếu muốn một lần.

### Dùng cấu hình bên ngoài với `initialDelayString`

```java
@Scheduled(initialDelayString = "${app.warmup.delay:PT3S}")
public void warmupOnce() {
    // Thời gian lấy từ property, mặc định 3 giây.
}
```

Đây là snippet trong bean có Spring property resolution. `PT3S` là ISO-8601 duration. Với Spring 6.2, `initialDelayString` cũng hỗ trợ một số duration formats khác và Spring-style property placeholders/SpEL theo hợp đồng API.

Không khai báo đồng thời `initialDelay` và `initialDelayString` với hai giá trị có nghĩa riêng; chọn một nguồn cấu hình. Một chuỗi số không có hậu tố thời gian có thể được hiểu theo đơn vị mặc định; dùng duration rõ ràng như `PT3S` giúp tránh nhầm ms/giây.

Trong `@Scheduled`, thuộc tính `timeUnit` được hỗ trợ cho các số `initialDelay`, `fixedRate`, `fixedDelay`; cron expressions dùng quy tắc riêng. Kiểm tra version của API khi dự án hỗ trợ nhiều branch/framework khác nhau.

## 3. Cơ chế đăng ký một lần của Spring

Bật scheduling:

```java
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
```

Đây là snippet cấu hình, không phải chương trình độc lập. Trong context nơi cấu hình này được import:

1. `@EnableScheduling` đăng ký hạ tầng tìm các bean có `@Scheduled`.
2. `ScheduledAnnotationBeanPostProcessor` duyệt method trên Spring-managed beans.
3. Với Spring 6.1+ và **chỉ `initialDelay`**, processor tạo một **`OneTimeTask`** thay vì một `FixedRateTask`, `FixedDelayTask` hay `CronTask`.
4. `TaskScheduler` được chọn dùng `schedule(...)` để thực thi một lần khi đến hạn.
5. Sau khi Runnable hoàn thành, **registration không sinh ra lượt tiếp theo**.

```text
Context refresh / scheduling initialization
                |
                v
         @EnableScheduling
                |
                v
 ScheduledAnnotationBeanPostProcessor
                |
                v
      OneTimeTask(initialDelay)
                |
                v
       ThreadPoolTaskScheduler
                |
           trì hoãn tới hạn
                |
                v
     scheduler worker chạy method
                |
                v
            method return
                |
                v
      không tự đăng ký lượt mới
```

Spring class `OneTimeTask` được bổ sung từ phiên bản **6.1**, là descriptor cho một `Runnable` với initial delay. Ứng dụng thông thường **không** cần khởi tạo lớp hạ tầng này; bài demo truy cập nó **chỉ để kiểm chứng kiểu đăng ký**.

**`@EnableAsync` không thay thế `@EnableScheduling`.** `@Async` xử lý lời gọi do code khác khởi phát, còn `@Scheduled` để container chủ động kích hoạt method theo lịch. Nếu dùng cả hai, cần chọn ranh giới thực thi và executor rõ ràng.

## 4. “Chạy một lần” là một lần ở phạm vi nào?

Hãy đọc chính xác câu “run once”:

> Một khai báo `@Scheduled` chỉ có `initialDelay` tạo ra **một one-time registration trong context đang chạy**.

Nó **không** khẳng định:

- Chỉ một lần cho mọi lần khởi động của ứng dụng.
- Chỉ một lần trong cả cụm Kubernetes có nhiều replicas.
- Không bao giờ có lần gọi thủ công từ code khác.
- Chỉ một lần khi bean có nhiều instance hoặc annotation lặp.
- Sẽ được thực hiện thành công ngay cả khi máy crash trước hạn.
- Khi lỗi thì Spring tự retry đến khi thành công.
- Sẽ hoàn tất trước HTTP request đầu tiên hoặc sau khi mọi external dependency đã sẵn sàng.

Ví dụ deployment:

```text
Pod A: start context -> register one-shot -> run once
Pod B: start context -> register one-shot -> run once
Pod C: start context -> register one-shot -> run once

Đối với nghiệp vụ toàn cụm có thể đã chạy 3 lần.
```

Nếu job chỉ làm nóng **cache cục bộ của từng pod**, mỗi pod chạy một lần có thể đúng. Nếu job **chuyển tiền, tạo hóa đơn hoặc gửi thông báo duy nhất**, annotation không đủ bảo vệ chống trùng. Cần job identity và idempotency, unique constraint, distributed coordination hoặc durable job queue theo yêu cầu.

### Khởi động lại thì sao?

Sau restart, Spring khởi tạo context mới và đăng ký one-time task mới. Bởi vậy `initialDelay`-only không phải cơ chế migration/database seeding “đã chạy vĩnh viễn” nếu bản thân operation không idempotent. Với migration, sử dụng công cụ hoặc job có version/state bền vững; với seeding dùng unique constraints/upsert thích hợp.

## 5. Ví dụ chạy được: `@Scheduled(initialDelay)` với Spring 6.2.12

Ví dụ dùng **Java 17+**, Maven và **Spring Framework 6.2.12**, không cần Spring Boot hay web server. Nó chủ ý có **một** scheduled method và một `ThreadPoolTaskScheduler`.

Chương trình kiểm tra:

1. Spring đăng ký **một `OneTimeTask`** qua `ScheduledAnnotationBeanPostProcessor`.
2. Method được kích hoạt lần đầu sau initial delay, dùng scheduler worker có tên `one-shot-...`.
3. Số lần thực thi được ghi bằng `AtomicInteger`.
4. `CountDownLatch` cho phép main chờ một invocation thực sự thay vì ngủ một khoảng tùy ý.
5. Main chỉ in sau khi task báo đã chạy, nên thứ tự stdout ổn định.

### Cấu trúc thư mục

```text
spring-one-shot-demo/
├── pom.xml
└── src/main/java/example/OneShotDemo.java
```

Tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>example</groupId>
    <artifactId>spring-one-shot-demo</artifactId>
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
                    <mainClass>example.OneShotDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tệp `src/main/java/example/OneShotDemo.java`:

```java
package example;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.OneTimeTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class OneShotDemo {

    public static class StartupJob {
        private final AtomicInteger executions = new AtomicInteger();
        private final AtomicReference<String> ranOn = new AtomicReference<>();
        private final CountDownLatch firstRun = new CountDownLatch(1);

        @Scheduled(
            initialDelay = 150,
            timeUnit = TimeUnit.MILLISECONDS,
            scheduler = "oneShotScheduler"
        )
        public void runOnce() {
            ranOn.set(Thread.currentThread().getName());
            executions.incrementAndGet();
            firstRun.countDown();
        }

        public boolean awaitFirstRun() throws InterruptedException {
            return firstRun.await(10, TimeUnit.SECONDS);
        }

        public int count() {
            return executions.get();
        }

        public String workerName() {
            return ranOn.get();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    public static class Config {

        @Bean
        public StartupJob startupJob() {
            return new StartupJob();
        }

        @Bean("oneShotScheduler")
        public ThreadPoolTaskScheduler oneShotScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("one-shot-");
            return scheduler;
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(Config.class)) {

            StartupJob job = context.getBean(StartupJob.class);
            ScheduledAnnotationBeanPostProcessor processor =
                context.getBean(ScheduledAnnotationBeanPostProcessor.class);

            Set<ScheduledTask> registrations = processor.getScheduledTasks();
            boolean registeredAsOneTime =
                registrations.size() == 1 &&
                registrations.iterator().next().getTask() instanceof OneTimeTask;

            if (!job.awaitFirstRun()) {
                throw new IllegalStateException(
                    "One-shot task did not run within 10 seconds"
                );
            }

            System.out.println("registeredAsOneTime="
                + registeredAsOneTime);
            System.out.println("countAfterFirstRun=" + job.count());
            System.out.println("onSchedulerWorker="
                + job.workerName().startsWith("one-shot-"));
            System.out.println("differentFromCaller="
                + !job.workerName()
                    .equals(Thread.currentThread().getName()));
        }
    }
}
```

Chạy từ thư mục `spring-one-shot-demo` với JDK 17+ và Maven:

```bash
mvn -q compile exec:java
```

Khi chạy bình thường, **bốn dòng do chương trình in ra** theo đúng thứ tự:

```text
registeredAsOneTime=true
countAfterFirstRun=1
onSchedulerWorker=true
differentFromCaller=true
```

### Phân tích vì sao kết quả đáng tin

1. `AnnotationConfigApplicationContext` khởi tạo cấu hình có `@EnableScheduling` và một `StartupJob` Spring bean.
2. Hạ tầng nhận diện `@Scheduled(initialDelay=150, ...)` nhưng **không có** lịch lặp. Với 6.2.12, processor đăng ký `OneTimeTask`. `getScheduledTasks()` có đúng một entry vì demo chỉ có một scheduled declaration.
3. `ThreadPoolTaskScheduler` tạo scheduler worker tên bắt đầu `one-shot-`. Method ghi thread name vào `AtomicReference`, tăng `AtomicInteger` rồi `countDown()`.
4. Main chờ `firstRun.await(...)`, nên kết quả lần đầu đã được công bố khi bắt đầu in. Nó không phụ thuộc vào sự tình cờ `Thread.sleep(300)` có vừa đủ hay không.
5. Main kiểm tra một registration mang kiểu `OneTimeTask`, đây là bằng chứng về **cấu trúc lịch**. `countAfterFirstRun=1` chỉ là ảnh chụp sau lần đầu; số đếm không đủ tự chứng minh rằng **không có lần nào về sau**.
6. `onSchedulerWorker=true` xác nhận method chạy trên pool do chính Spring bean scheduler cấu hình, không chạy trên thread main.
7. Khi context đóng, Spring shutdown các scheduler beans mà nó quản lý. Demo không tạo thread pool chạy nền rồi để ứng dụng treo sau khi main xong.

Nếu runtime không tải dependency, scheduler không được khởi tạo hoặc task không chạy trong 10 giây vì lỗi môi trường, chương trình sẽ ném exception thay vì in đúng bốn dòng. Demo dựa trên **hợp đồng API Spring 6.2.12**; không tuyên bố mọi môi trường đều bắt đầu task đúng đến từng mili giây.

## 6. So sánh với các cách khai báo có lịch lặp

`initialDelay` **không có cùng ý nghĩa về số lần chạy** khi dùng kèm các thuộc tính khác.

| Annotation | Kiểu registration | Hành vi |
| --- | --- | --- |
| `@Scheduled(initialDelay = 3000)` | One-time (6.1+) | Một lần sau khoảng trễ. |
| `@Scheduled(initialDelay = 3000, fixedRate = 5000)` | Periodic fixed-rate | Chạy lần đầu sau 3 giây, sau đó tiếp tục theo fixed rate. |
| `@Scheduled(initialDelay = 3000, fixedDelay = 5000)` | Periodic fixed-delay | Chạy lần đầu sau 3 giây, rồi tiếp tục sau các khoảng nghỉ từ khi lượt trước hoàn tất. |
| `@Scheduled(fixedRate = 5000)` | Periodic | Không có initial delay riêng. |
| `@Scheduled(fixedDelay = 5000)` | Periodic | Chờ sau khi lượt trước hoàn thành. |
| `@Scheduled(cron = "0 0 2 * * *")` | Cron | Khớp các thời điểm cron nhiều lần. |

Ví dụ **không** phải one-time:

```java
@Scheduled(initialDelay = 1000, fixedRate = 60000)
public void refreshCache() {
    // Cứ theo lịch fixed rate sẽ tiếp tục chạy lại.
}
```

Ví dụ one-time trên 6.1+:

```java
@Scheduled(initialDelay = 1000)
public void refreshCacheOnce() {
    // Không đăng ký lượt tiếp theo.
}
```

Đây là các snippet method, không phải hai annotation cho cùng một method. Hệ thống có thể gọi thủ công method được annotate; khi đó số lần gọi thủ công không nằm trong cam kết one-time của schedule.

### Một `@Scheduled` lặp trên cùng method?

Vì `@Scheduled` là repeatable, khai báo nhiều lần có thể tạo nhiều triggers độc lập. Một method được đăng ký hai `initialDelay` khác nhau có thể chạy **hai lần theo hai registrations**. Nếu mục tiêu một lần, phải đảm bảo chỉ có **một** annotation cho đúng bean/context, kể cả khi sử dụng composed annotations.

## 7. Thời điểm chạy: không phải đúng từ lúc JVM startup

Một `initialDelay = 1000` có nghĩa khoảng một giây kể từ khi công việc được scheduler **đăng ký** để bắt đầu tính thời hạn, chứ không phải cam kết một giây sau lệnh `java ...` hoặc sau `SpringApplication.run(...)` bắt đầu.

```text
JVM start
   |
   +-- Spring tạo dependency, bean, post-processor
   |
   +-- context refresh / register scheduled task
   |                                  |
   |                                  +--- initialDelay = 1 s ---> đủ hạn
   |                                                               |
   |                                              scheduler có worker rảnh?
   |                                                               |
   |                                                         chạy method
```

Một scheduled task có thể **bắt đầu trễ** do scheduler workers đang bận, JVM pause, CPU contention, shutdown hoặc các yếu tố vận hành. Chữ “delay” ở đây không phải hard real-time guarantee.

`@Scheduled(initialDelay = 0)` không bằng `@PostConstruct`: method được đăng ký qua scheduling infrastructure và thực hiện theo executor/lifecycle, không phải lời gọi đồng bộ của constructor. Nó cũng không chứng minh mọi external service đã ready.

Nếu ứng dụng yêu cầu **trước khi nhận HTTP traffic phải hoàn tất khởi tạo bắt buộc**, không nên chỉ dựa vào một one-shot timer; hãy dùng lifecycle/readiness mechanism có thể kiểm tra completion.

## 8. One-time và API `TaskScheduler.schedule(...)`

`@Scheduled` rất tiện khi lịch cố định trong Spring bean. Nếu thời điểm do người dùng quyết định lúc runtime hoặc cần giữ `ScheduledFuture`, dùng `TaskScheduler` programmatically thường rõ ràng hơn:

```java
ScheduledFuture<?> handle = taskScheduler.schedule(
    () -> reportService.generate(reportId),
    Instant.now().plus(Duration.ofMinutes(5))
);
```

Đây là snippet trong method đã có `TaskScheduler taskScheduler`, `reportService` và `reportId`; cần import `ScheduledFuture`, `Instant`, `Duration`. Nó tạo một **one-time Runnable schedule** tại `Instant` chỉ định.

Với Spring 6.0, cách dùng `TaskScheduler.schedule(Runnable, Instant)` (và lifecycle hook phù hợp để gọi nó) có thể là phương án hợp lệ nếu runtime chưa có cú pháp one-time `@Scheduled(initialDelay)`. Không nên dùng `fixedRate = Long.MAX_VALUE` như một mẹo mô phỏng chạy một lần: kiểu hack này có nguy cơ thời gian/overflow và semantics khó bảo trì.

### Theo dõi `ScheduledFuture`

Với một one-shot Runnable:

- `isDone()` đúng khi task hoàn tất hoặc bị cancel.
- `get(timeout, unit)` chờ Runnable hoàn tất; exception có thể được phản ánh qua future tùy error handling.
- `cancel(false)` có thể ngăn lượt chưa bắt đầu, nhưng không thể “undo” side effects đã chạy.
- `cancel(true)` có thể yêu cầu interrupt nếu implementation hỗ trợ; không bảo đảm JDBC/network/transaction bị hủy tức thì.

Với **periodic** schedule, future có thể còn sống qua nhiều lần chạy và không hoàn tất bình thường sau mỗi lượt. Không dùng `ScheduledFuture.get()` để chờ “vòng đầu của cron” như với one-shot.

`@Scheduled` method tự nó không trả `ScheduledFuture` nghiệp vụ cho một HTTP caller. Nếu caller cần quan sát kết quả, hãy thiết kế `CompletableFuture`, job record/status hoặc programmatic scheduling phù hợp.

## 9. Scheduler và giới hạn tài nguyên

Trong demo:

```java
@Bean("oneShotScheduler")
public ThreadPoolTaskScheduler oneShotScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("one-shot-");
    return scheduler;
}
```

Đây là snippet đã xuất hiện trong chương trình đầy đủ. `ThreadPoolTaskScheduler` bọc `ScheduledThreadPoolExecutor`: tác vụ thông thường chạy **ngay trên scheduler thread**, không tự chuyển sang một `ThreadPoolTaskExecutor` khác. Với `poolSize = 1`, một scheduled job blocking lâu có thể khiến những công việc khác dùng cùng scheduler bị trễ.

Nếu one-shot task chỉ tính một giá trị nhẹ, chạy trực tiếp trên scheduler worker là đơn giản và có thể hợp lý. Nếu nó chạy I/O blocking lâu hoặc batch lớn, cân nhắc **tách phần thực thi** sang executor:

```java
@Component
class OneShotTick {
    private final BackgroundWorker worker;

    OneShotTick(BackgroundWorker worker) {
        this.worker = worker;
    }

    @Scheduled(initialDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void tick() {
        worker.runAsync();
    }
}

@Component
class BackgroundWorker {
    @Async("businessExecutor")
    public CompletableFuture<Void> runAsync() {
        doWork();
        return CompletableFuture.completedFuture(null);
    }
}
```

Đây là mẫu cấu trúc, không phải project độc lập; cần `@EnableScheduling`, `@EnableAsync`, business executor bean, `doWork` và imports. Hai bean giúp method `runAsync` được gọi **qua Spring proxy**, tránh self-invocation `this.runAsync()` vốn thường bỏ qua `@Async` ở proxy mode.

**Offload thay đổi ý nghĩa completion:** one-shot scheduled method `tick()` có thể return ngay sau khi submit, còn `runAsync()` vẫn pending. Đừng log “job succeeded” chỉ vì tick đã return. Cũng cần xử lý rejection, queue saturation, retry và trạng thái của business job. Với `CallerRunsPolicy` ở pool đích, scheduler thread thậm chí có thể tự chạy phần việc nặng khi pool bão hòa.

`@EnableScheduling` và `@EnableAsync` là hai hạ tầng khác nhau: một bên đăng ký trigger, bên kia intercept lời gọi. Không bật `@EnableAsync` chỉ để “làm `@Scheduled` chạy nền”; scheduler đã có thread theo implementation được chọn.

## 10. Lỗi, retries và shutdown của one-time task

Nếu one-shot method ném exception, Spring không tự biến registration ấy thành lịch lặp retry. Việc log, capture lỗi và chính sách retry tùy scheduler/error handler hoặc các lớp bạn thiết kế.

Ví dụ error handler:

```java
ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
scheduler.setPoolSize(2);
scheduler.setErrorHandler(error ->
    System.err.println("Scheduled job failed: " + error.getMessage())
);
```

Đây là snippet cấu hình, không phải file độc lập. Nó chỉ log và không đảm bảo retry, transaction rollback, durable status hoặc HTTP response. Nếu ErrorHandler cũng lỗi, cần kiểm tra hậu quả của exception handler trong application.

Những câu hỏi production nên quyết định trước:

1. Nếu job chưa chạy mà application dừng, có chấp nhận **mất lượt** không?
2. Nếu job chạy dở rồi crash, có phải retry? Nếu retry, side effect có idempotent không?
3. Nếu task bị từ chối hoặc scheduler shutdown trước initial delay, có alert không?
4. Có đo số lần đăng ký, thời điểm bắt đầu thực tế, completion, duration và lỗi không?
5. Job có mở transaction trên worker nếu ghi JDBC/JPA không?
6. Có phân biệt “one-time trigger submitted” và “business work completed” khi offload không?
7. Có nhiều pods làm cùng job không, cần lock/job claim không?

### Transaction không tự theo từ caller

Với imperative Spring transactions, transaction gắn thread. Scheduled task thường chạy ở scheduler worker, không tự kế thừa JDBC/JPA transaction của HTTP request. Nếu cần atomicity cho nhiều database writes, đặt `@Transactional` ở **bean xử lý được gọi qua Spring proxy trên worker** hoặc dùng `TransactionTemplate`. Không copy `TransactionSynchronizationManager`/JDBC connection sang thread khác.

### Process restart làm mất lịch RAM

`ThreadPoolTaskScheduler` không phải durable job store. Nếu task được dự định chạy sau 10 phút nhưng process restart ở phút thứ 5, một context mới có thể đăng ký lại timer từ đầu; một task đã chạy trong lần boot trước cũng có thể lại chạy. Nếu cần lịch một lần thực sự theo **ngày giờ cố định và lưu qua restart**, hãy lưu job record và scheduled fire time trong database hoặc scheduler bền vững.

## 11. Chọn phương án theo mục đích

| Yêu cầu nghiệp vụ | Phương án phù hợp hơn | Điểm cần chú ý |
| --- | --- | --- |
| Làm ấm cache cục bộ một lần sau khi context khởi động | `@Scheduled(initialDelay=...)` trên 6.1+ | Mỗi replica làm ấm cache của chính nó. |
| Một phép kiểm tra ngắn sau vài giây, không quan trọng nếu mất qua crash | One-shot `@Scheduled` | Đo kết quả và lỗi nếu cần. |
| Job cần chạy vào `Instant` do người dùng chọn | `TaskScheduler.schedule(Runnable, Instant)` | Lịch ở RAM không bền qua restart. |
| Khởi động khi Spring Boot hoàn toàn ready | `ApplicationReadyEvent` nếu phù hợp | Đây là API Spring Boot, không có sẵn chỉ từ `spring-context`; event không tương đương timer. |
| Công việc phải hoàn tất trước khi service nhận traffic | Startup/lifecycle readiness có theo dõi completion | Timer không phải readiness barrier. |
| Mỗi deployment toàn cụm chỉ một worker làm job | Coordination/leader/lock hoặc job claim | Cần xử lý crash và lease expiration. |
| Cần exactly-once effect thực tế | State bền vững, transaction/idempotency/unique constraint | Scheduling API không đủ một mình. |
| Job dài, blocking hoặc cần queue/overload riêng | Scheduled trigger + `TaskExecutor`/durable queue | Phân biệt trigger completion và business completion. |
| Việc phải tồn tại sau khi ứng dụng restart | Durable job store, broker hoặc scheduler persistent | Cần retry/recovery và observability. |

## 12. Những lỗi thường gặp

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| “`initialDelay` đã có từ lâu nên mọi Spring đều cho one-time.” | Khả năng one-time chỉ xuất hiện từ 6.1. | Kiểm tra version runtime và Javadoc. |
| Dùng `initialDelay` một mình trên Spring 6.0 | Cú pháp thiếu lịch chính trong bản cũ. | Nâng 6.1+ hoặc schedule programmatically. |
| Thêm `fixedRate` để “chắc có lịch” rồi tưởng chỉ chạy lần đầu | Có lịch lặp thực sự. | Chỉ dùng `initialDelay` trên bản hỗ trợ. |
| Gắn nhiều `@Scheduled` lên cùng method | Mỗi annotation được đăng ký độc lập. | Chỉ một annotation/registration cho one-shot. |
| Không có `@EnableScheduling` trong context phù hợp | Infrastructure không đăng ký method. | Bật trong đúng Spring context. |
| Tự `new` object có `@Scheduled` | Object không phải Spring-managed bean. | Đăng ký qua `@Bean`/component scanning. |
| Dùng `initialDelay=0` như `@PostConstruct` | Scheduler timing/lifecycle khác constructor callback. | Dùng đúng startup hook khi có yêu cầu thứ tự. |
| Ngủ 100 ms rồi thấy count 1, kết luận job không bao giờ lặp | Quan sát hữu hạn không chứng minh cấu trúc registration. | Kiểm tra `OneTimeTask`/hợp đồng API. |
| Tưởng one-shot = exactly-once across pods/restart | Mỗi context/pod có registration riêng. | Idempotency, durable state/coordination. |
| Chờ scheduled method `void` trả nghiệp vụ cho controller | Scheduled method không nhận RPC caller. | Dùng job status hoặc async handle riêng. |
| Khi task lỗi, tưởng Spring tự retry | One-time không tạo lượt lặp tiếp theo. | Thiết kế retry policy có giới hạn. |
| Lên lịch trong RAM 1 giờ và mong không mất khi crash | Không có persisted schedule. | Dùng durable scheduler/job store. |
| Để scheduled task blocking lâu trong pool size 1 | Các job khác bị trễ. | Timeout, sizing và offload có giới hạn. |
| Nghĩ `@Async` trong cùng bean `this.method()` sẽ offload | Self-invocation bỏ qua proxy mode. | Tách bean và gọi qua proxy. |
| Không giới hạn đồng thời khi nhiều replica cùng chạy | Race, trùng side effect, backlog. | Unique keys, locks/idempotency và quan sát. |

## 13. Checklist khi review một one-time scheduled method

1. **Version:** Spring Framework thực chạy là 6.1+?
2. **Annotation:** Chỉ có `initialDelay` hoặc `initialDelayString`, không có fixed rate/delay/cron?
3. **Context:** `@EnableScheduling` được bật đúng context và bean được Spring quản lý?
4. **Scope:** Chỉ một bean instance, một registration, hay có nhiều contexts/replicas?
5. **Time:** Đơn vị, duration format và nguồn properties đã được kiểm tra?
6. **Timing:** “Sau registration delay” có đủ yêu cầu, hay phải chờ readiness thực sự?
7. **Scheduler:** Bean được chọn và số worker có đủ? Có nguy cơ blocking?
8. **Failure:** Có error logging, metrics, retry policy riêng, trạng thái job?
9. **Idempotency:** Job bị chạy lại sau deploy/restart có an toàn không?
10. **Durability:** Có thể mất nếu process tắt trước thời điểm đến hạn? Có cần job store?
11. **Transaction:** Database writes có được thực hiện trong transaction đúng thread?
12. **Completion:** Nếu trigger gửi việc sang executor, business result được theo dõi thế nào?
13. **Tests:** Có quan sát actual invocation bằng latch và kiểm tra cấu trúc registration?
14. **Shutdown:** Context đóng sớm có làm mất job hoặc ngắt việc đang chạy không?

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Trên Spring 6.2, có thể viết `@Scheduled(initialDelay = 1000)` mà không có `fixedRate`, `fixedDelay`, `cron` không?

**Đáp án:** Có. Đó là một one-time scheduled registration, được hỗ trợ từ Spring Framework 6.1.

**Câu 2.** Tại sao `initialDelay` tồn tại trong Spring 6.0 nhưng không có nghĩa one-time đã được hỗ trợ?

**Đáp án:** Vì trước 6.1, `initialDelay` là lựa chọn **phụ** của lịch lặp; hợp đồng annotation vẫn yêu cầu một trong `cron`, `fixedRate`, `fixedDelay`.

**Câu 3.** `@Scheduled(initialDelay = 3000, fixedRate = 5000)` chạy mấy lần?

**Đáp án:** Lặp lại theo fixed rate, lần đầu sau khoảng trễ ban đầu. Không phải một lần.

**Câu 4.** Với đơn vị giây, nên viết cú pháp nào rõ ràng?

**Đáp án:** `@Scheduled(initialDelay = 3, timeUnit = TimeUnit.SECONDS)`.

**Câu 5.** `initialDelay=0` có bảo đảm job chạy xong trước mọi `@PostConstruct` khác không?

**Đáp án:** Không. Đây là lịch do scheduler hạ tầng đăng ký, không phải lời gọi constructor/lifecycle synchronous.

**Câu 6.** Demo làm sao chứng minh cấu hình one-shot mà không ngủ nhiều giây?

**Đáp án:** Kiểm tra `ScheduledAnnotationBeanPostProcessor.getScheduledTasks()` có một registration kiểu `OneTimeTask`, đồng thời dùng latch để quan sát một lượt thực thi.

**Câu 7.** Mỗi pod trong ba replica có một `@Scheduled(initialDelay=...)`. Có bảo đảm tổng chỉ một lần không?

**Đáp án:** Không. Mỗi pod/context có thể chạy một lần. Cần job identity, idempotency/coordination nếu phải một lần toàn cụm.

**Câu 8.** One-time method thất bại. Có tự retry sau delay khác không?

**Đáp án:** Không theo hợp đồng one-time; ứng dụng cần một cơ chế retry riêng nếu nghiệp vụ yêu cầu.

**Câu 9.** Nếu muốn lập lịch tại thời điểm do user chọn khi app đang chạy, dùng gì?

**Đáp án:** `TaskScheduler.schedule(Runnable, Instant)` hoặc durable job scheduler.

**Câu 10.** One-time job có `@Transactional` trên Spring-managed worker bean, nhưng được gọi từ một scheduler thread khác HTTP caller. Có tự chia transaction của HTTP request không?

**Đáp án:** Không. Transaction imperative gắn thread; phải mở transaction đúng trên thread xử lý dữ liệu.

**Câu 11.** Tại sao `ThreadPoolTaskScheduler` `poolSize=1` có thể làm one-time job khác đến hạn nhưng vẫn phải chờ?

**Đáp án:** Thân Runnable thường chạy ngay trên scheduler worker; nếu worker bị task khác chiếm, task đến hạn phải đợi.

### Ghi nhớ

**Spring Framework 6.1+** hỗ trợ **chỉ `@Scheduled(initialDelay=...)`** để đăng ký một one-shot task; các bản như 6.0.x chưa hỗ trợ cách khai báo này. Initial delay được tính từ scheduling registration theo lifecycle, không phải timestamp tuyệt đối từ JVM start. Nếu thêm `fixedRate`, `fixedDelay` hoặc `cron`, lịch sẽ **lặp lại**. `OneTimeTask` diễn đạt **một registration không tự lặp trong một context**, nhưng không đem lại exactly-once, job durability, retry hay coordination qua restart/replicas. Với nghiệp vụ thực tế, kết hợp một lịch phù hợp với idempotency, transaction, trạng thái và quan sát có chủ đích.

### Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): one-time chỉ cần `initialDelay`, timeUnit, initialDelayString và scheduler.
- [Spring Framework 6.1 — Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.1-Release-Notes): thay đổi hỗ trợ one-time `@Scheduled`.
- [Spring Framework 6.0 — Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.0.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): hợp đồng cũ yêu cầu một trong cron/fixedDelay/fixedRate.
- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): các ví dụ với initialDelay-only và hạ tầng scheduling.
- [Spring Framework 6.2.12 — OneTimeTask](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/config/OneTimeTask.html): kiểu đăng ký một lần từ Spring 6.1.
- [Spring Framework — ScheduledAnnotationBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/ScheduledAnnotationBeanPostProcessor.html): việc phát hiện và đăng ký method scheduled.
- [Java 17 — ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html): Future khi lên lịch bằng API.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-tasks-007">cron và zone cho lịch theo thời gian địa phương</a>

<details>
<summary>Xem chi tiết</summary>

# cron và zone cho lịch theo thời gian địa phương

> **Module:** Spring Framework – concurrency  
> **Nhóm:** @Scheduled  
> **Mục tiêu:** Thiết kế lịch bằng `@Scheduled(cron = ..., zone = ...)` sao cho một công việc chạy theo **giờ địa phương mong muốn**, không phụ thuộc ngẫu nhiên vào timezone của container; hiểu biểu thức cron sáu trường của Spring, `ZoneId`, chuyển đổi sang `Instant`, chuyển giờ mùa hè (DST), cấu hình ngoài, cách kiểm thử lịch và những giới hạn về cluster, bỏ lỡ lượt chạy, quá tải.

## 1. Câu trả lời ngắn: cron nói “lúc nào trên đồng hồ”, zone nói “đồng hồ ở đâu”

Trong Spring Framework, biểu thức `cron` xác định **những thời điểm theo lịch dân sự** như 09:30 các ngày làm việc; thuộc tính `zone` xác định **múi giờ dùng để diễn giải biểu thức**. Cả hai kết hợp lại mới cho biết một lượt chạy tương ứng với `Instant` nào trên trục thời gian thực.

Ví dụ:

~~~java
@Scheduled(cron = "0 30 9 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
public void sendMorningReport() {
    reportService.sendMorningReport();
}
~~~

Đây là **snippet Spring tích hợp**, cần bean được Spring quản lý, import `@Scheduled`, dependency `reportService` và bật `@EnableScheduling`. Ý nghĩa: **09:30, từ thứ Hai đến thứ Sáu, theo múi giờ Thành phố Hồ Chí Minh**. Nó **không** có nghĩa 09:30 UTC, cũng không phải 09:30 ở mỗi máy chạy ứng dụng.

Hai thành phần có vai trò độc lập:

- `cron = "0 30 9 * * MON-FRI"` chọn mốc **09:30 trên lịch** của zone tương ứng.
- `zone = "Asia/Ho_Chi_Minh"` xác định cách đổi mốc ấy sang thời điểm tuyệt đối, bao gồm quy tắc offset/DST của khu vực khi có.
- Nếu bỏ `zone`, Spring dùng timezone mặc định của scheduler. Trong những cấu hình phổ biến đó gần với timezone mặc định của JVM/process, nhưng **không nên coi đây là hợp đồng timezone nghiệp vụ**.

**Quy tắc thực hành:** Lịch mang nghĩa “09:30 giờ Việt Nam” nên chỉ rõ zone Việt Nam. Lịch mang nghĩa “09:30 UTC” nên dùng `UTC`. Không sửa cron từ 09:30 thành 02:30 rồi phó mặc cho timezone máy chủ, vì cách đó dễ sai khi thay hạ tầng hoặc region.

## 2. Spring cron có sáu trường, không phải năm trường Unix

Spring sử dụng biểu thức cron theo thứ tự:

~~~text
   0    30     9      *        *       MON-FRI
   |     |     |      |        |          |
 giây   phút  giờ    ngày     tháng    thứ trong tuần
 0-59   0-59  0-23   1-31     1-12     0-7
                                   (SUN=0 hoặc 7)
~~~

Một cron hợp lệ thường gồm **sáu trường cách nhau bởi dấu cách**: `second minute hour day-of-month month day-of-week`. Đây là khác biệt rất quan trọng so với crontab Unix truyền thống thường có năm trường bắt đầu bằng phút.

| Trường | Giá trị phổ biến | Diễn giải |
| --- | --- | --- |
| Giây | `0`, `*/15` | Chạy ở giây 0, hoặc mỗi 15 giây |
| Phút | `30`, `0/10` | Phút 30 hoặc các phút 0,10,20,... |
| Giờ | `9`, `8-17` | Giờ theo đồng hồ 24 giờ |
| Ngày trong tháng | `1`, `15`, `*` | Ngày cụ thể hoặc mọi ngày hợp lệ |
| Tháng | `1-12`, `JAN`, `*` | Tháng hoặc mọi tháng |
| Thứ | `MON-FRI`, `SUN`, `*` | Thứ được phép, dùng tên dễ đọc |

Ví dụ `0 0 9 * * *` nghĩa là **mỗi ngày lúc 09:00:00** trong zone được chọn. Còn `0 0 9 * * MON-FRI` chỉ chạy từ thứ Hai đến thứ Sáu. `*/5 * * * *` của Unix là **năm trường**, không dùng nguyên xi làm `@Scheduled.cron` của Spring.

Ký hiệu `*` là mọi giá trị hợp lệ; `*/5` diễn tả bước nhảy; dấu phẩy là danh sách; dấu gạch nối chỉ khoảng liên tục. Cần kiểm tra đúng miền giá trị: `60` không phải giây hay phút hợp lệ, `24` không phải giờ hợp lệ.

## 3. “Giờ địa phương” không có nghĩa “giờ của máy chủ”

Giả sử một báo cáo phải chạy **09:30 giờ Việt Nam**. Trong một môi trường deploy, container dùng timezone UTC; môi trường khác dùng `Asia/Ho_Chi_Minh`. Nếu bạn chỉ viết:

~~~java
@Scheduled(cron = "0 30 9 * * MON-FRI")
public void report() { /* ... */ }
~~~

thì hai scheduler có timezone mặc định khác nhau sẽ tính **mốc tuyệt đối khác nhau** cho cùng cron. Máy dùng UTC tính 09:30Z; còn zone Việt Nam tính 09:30+07:00, tương ứng 02:30Z.

Đó là sự khác nhau **bảy giờ** trong ví dụ tại Việt Nam, không phải lỗi `@Scheduled`. Đề bài nghiệp vụ chưa được mô tả đầy đủ khi thiếu zone.

Nên dùng:

~~~java
@Scheduled(
    cron = "0 30 9 * * MON-FRI",
    zone = "Asia/Ho_Chi_Minh")
public void report() { /* ... */ }
~~~

**Không nhầm ba loại thời gian:**

1. **`LocalDateTime`**: “2026-10-29 09:30” trên đồng hồ, chưa nói ở đâu; tự nó không xác định một thời điểm tuyệt đối.
2. **`ZonedDateTime`**: ngày giờ + zone có quy tắc, ví dụ `2026-10-29T09:30+07:00[Asia/Ho_Chi_Minh]`.
3. **`Instant`**: thời điểm tuyệt đối, ví dụ `2026-10-29T02:30:00Z`; phù hợp đối chiếu lịch giữa máy và hệ thống phân tán.

Cron kết hợp với zone là đầu vào để tìm mốc thực thi theo thời gian tuyệt đối. Những ứng dụng nhiều múi giờ cần giữ nguyên **ý nghĩa địa phương** và **định danh zone**, không chỉ lưu một `LocalDateTime` hay một offset trần.

## 4. Chọn ID zone: Asia/Ho_Chi_Minh, UTC và vùng có DST

Một `zone` mang tên vùng như `Asia/Ho_Chi_Minh`, `America/New_York`, `Europe/Paris` lưu quy tắc lịch sử và hiện tại từ cơ sở dữ liệu timezone của JDK.

| Ý định nghiệp vụ | Zone thường dùng | Điều cần nhớ |
| --- | --- | --- |
| Chạy 09:30 giờ Thành phố Hồ Chí Minh | `Asia/Ho_Chi_Minh` | Offset hiện hành +07:00; không có chuyển DST theo lịch hiện tại |
| Chạy 09:30 UTC | `UTC` | Mốc ổn định so với UTC |
| Chạy 09:30 giờ New York | `America/New_York` | Offset thay đổi giữa EDT và EST theo DST |
| Chạy 09:30 giờ Paris | `Europe/Paris` | Có DST; giờ UTC tương ứng thay đổi theo mùa |
| Chạy 09:30 theo offset cố định +07 | `+07:00` (khi API chấp nhận) | Không đại diện đầy đủ các quy tắc lịch sử/tương lai của một vùng |

**Ưu tiên region ID cho lịch nghiệp vụ gắn với địa phương.** `Asia/Ho_Chi_Minh` có ý nghĩa rõ ràng với lịch Việt Nam; `UTC` thích hợp cho các batch được định nghĩa theo giờ UTC. Một offset `+07:00` thể hiện sai mục đích nếu sau này chính sách timezone của khu vực cần được JDK cập nhật.

**Không dùng viết tắt mơ hồ** như `CST`, `IST` hoặc `PST` khi nghiệp vụ cần timezone cụ thể. Các tên viết tắt có thể mơ hồ, xử lý khác nhau giữa thư viện và không diễn tả quy tắc DST như region ID. Trong code Java, kiểm tra bằng `ZoneId.of("Asia/Ho_Chi_Minh")` và log zone đã chọn.

## 5. Bật scheduling và khai báo @Scheduled đúng vị trí

Một Spring Framework application cần có:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
~~~

Sau đó đặt phương thức có lịch trong một bean quản lý bởi Spring:

~~~java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VietnamMorningJob {
    @Scheduled(cron = "0 30 9 * * MON-FRI",
               zone = "Asia/Ho_Chi_Minh")
    public void execute() {
        System.out.println("Morning job triggered");
    }
}
~~~

Hai đoạn trên là **snippet tích hợp Spring**, không phải project độc lập chỉ dùng `javac`. `@EnableScheduling` áp dụng cho `ApplicationContext` thích hợp; nếu application không đăng ký processor hoặc bean không nằm trong context, annotation sẽ không tự hoạt động.

Trong ví dụ trên, `System.out.println` chỉ minh họa thời điểm callback được gọi. Production nên dùng logger có cấu trúc, phân biệt business date và job ID, và xử lý lỗi. Phương thức scheduled imperative thông thường không nhận tham số request; giá trị trả về của nó không là một kết quả được đưa tới caller.

**Nhớ:** `@Scheduled` xác định lúc kích hoạt, không tự tạo transaction, không tự bao bọc toàn bộ công việc trong một retry policy và không bảo đảm callback chỉ chạy một lần trên nhiều replica.

## 6. Cấu hình cron và zone bằng property

Với nhiều môi trường, nên tách giá trị lịch khỏi mã Java:

~~~java
@Component
public class BillingCloseJob {
    @Scheduled(
        cron = "${jobs.billing.cron:0 0 9 * * MON-FRI}",
        zone = "${jobs.billing.zone:Asia/Ho_Chi_Minh}")
    public void closeBillingDay() {
        // Áp dụng luật nghiệp vụ ở service được inject.
    }
}
~~~

Trong `application.yaml`:

~~~yaml
jobs:
  billing:
    cron: "0 0 9 * * MON-FRI"
    zone: "Asia/Ho_Chi_Minh"
~~~

Đây là **cấu hình Spring tích hợp**; bean phải được component-scan hoặc đăng ký trong context, và app cần `@EnableScheduling`. `${...:...}` là cú pháp placeholder với fallback mặc định, khác với cron `?` hay `*`.

Các lý do nên cấu hình ngoài:

- Dev/test có thể thay lịch mà không sửa code; production giữ cùng cấu trúc triển khai.
- Mỗi môi trường có thể chọn zone chính xác và logging rõ.
- Có thể tắt lịch bằng giá trị cron `-` (hằng `Scheduled.CRON_DISABLED`) thông qua property nếu ứng dụng cho phép.
- Dễ kiểm tra thông số trước khi khởi động, dùng cấu hình đã được review và có version.

**Giới hạn:** Spring thường resolve annotation và đăng ký lịch khi bean được xử lý trong lúc context khởi tạo. Việc sửa giá trị `jobs.billing.cron` hoặc `jobs.billing.zone` trong một file/config server **không mặc nhiên hủy lịch cũ và đăng ký lịch mới tại runtime**. Nếu cần thay đổi ngay lập tức, thiết kế cơ chế cancel/re-register qua `TaskScheduler` hoặc workflow quản lý lịch rõ ràng.

## 7. Bảng ví dụ cron trong zone được chọn

| Cron Spring | Ý nghĩa trong `zone` |
| --- | --- |
| `0 0 9 * * *` | 09:00:00 mỗi ngày |
| `0 30 9 * * MON-FRI` | 09:30:00 từ thứ Hai đến thứ Sáu |
| `0 0 2 * * *` | 02:00:00 mỗi ngày, có thể gặp DST gap |
| `0 0 9 1 * *` | 09:00:00 ngày 1 mỗi tháng |
| `0 0 18 * * FRI` | 18:00:00 thứ Sáu |
| `0 0 0 1 JAN *` | 00:00:00 ngày 1 tháng 1 hằng năm |
| `0 */10 8-17 * * MON-FRI` | Mỗi 10 phút trong khung giờ 08:00–17:59 ngày làm việc |
| `0 0 9 * * SUN` | 09:00:00 Chủ nhật |
| `0 0 9 * * MON` | 09:00:00 thứ Hai |
| `0 0 9 * * *` với `zone="UTC"` | 09:00Z |
| Cùng cron `0 0 9 * * *` với `zone="Asia/Ho_Chi_Minh"` | 09:00 giờ Việt Nam, hiện tương ứng 02:00Z |

**Chú ý mốc giờ:** biểu thức `0 */10 8-17 * * MON-FRI` có các lượt vào 17:00, 17:10,...17:50; nếu nghiệp vụ chỉ cho phép đến đúng 17:00, cần một quy tắc khác hoặc kiểm tra thời điểm trong logic nghiệp vụ.

Cron cũng không tự biết ngày lễ, lịch nghỉ đặc thù hoặc “ngày làm việc theo thị trường X”. `MON-FRI` chỉ lọc thứ, **không** lọc nghỉ lễ. Nếu nghiệp vụ đòi hỏi lịch tài chính/nhân sự cụ thể, cần business calendar bổ sung.

## 8. Ngày trong tuần: dùng tên để tránh sai khác Quartz

Spring dùng giá trị số `0` hoặc `7` cho **Chủ nhật**, `1` là thứ Hai, `2` là thứ Ba, v.v. Điều này có thể khác cách một thư viện Quartz hoặc scheduler khác đánh số ngày trong tuần.

~~~text
Spring day-of-week:
SUN = 0 hoặc 7
MON = 1
TUE = 2
WED = 3
THU = 4
FRI = 5
SAT = 6
~~~

Khi yêu cầu “09:00 thứ Hai”, chọn `MON` thay vì số `1` để người đọc và review dễ nhận biết hơn. Không copy một cron expression từ một công cụ Quartz rồi tự cho rằng cùng semantics của `1-7` hay `?`.

Spring hỗ trợ nhiều ký hiệu mở rộng trong `CronExpression`, nhưng **không phải mọi phần của cú pháp Quartz đều giống hệt**. Hãy xác minh bằng Javadoc và test trên chính version Spring đang chạy.

## 9. Ký hiệu đặc biệt: *, ?, /, L, W, # và macro

Ngoài các trường đơn giản, Spring `CronExpression` hỗ trợ một số cách biểu đạt lịch hữu ích:

- `*`: mọi giá trị hợp lệ của trường.
- `?`: có thể dùng ở trường ngày trong tháng hoặc ngày trong tuần như cách thể hiện không chỉ định cụ thể, thường tương đương `*` ở vị trí đó trong Spring.
- `*/15`: lấy các mốc cách nhau 15 đơn vị trong miền của trường tương ứng.
- `MON-FRI`: miền liên tục từ thứ Hai đến thứ Sáu.
- `1,15`: chọn cả ngày 1 và ngày 15.
- `L`: chọn mốc “cuối cùng” phù hợp trường, ví dụ ngày cuối tháng.
- `W`: chọn ngày trong tuần gần một ngày trong tháng theo quy tắc parser.
- `#`: mốc thứ N của một weekday trong tháng, ví dụ một thứ Hai nhất định.
- Macro `@daily`, `@weekly`, `@monthly`, v.v. có thể rút gọn lịch chuẩn được parser hỗ trợ.

Ví dụ:

~~~text
0 0 9 L * *        -> 09:00 vào ngày cuối mỗi tháng
0 0 9 1,15 * *     -> 09:00 vào ngày 1 và 15
0 0 9 * * MON-FRI  -> 09:00 mỗi ngày thứ Hai đến thứ Sáu
~~~

Với `W`, `L` và `#`, hãy tham khảo **Javadoc Spring của version cụ thể** rồi viết test cho tháng ngắn, tháng dài và ngày lễ. Ký hiệu “weekday gần nhất” không đồng nghĩa “ngày làm việc hợp lệ theo lịch công ty”.

**Không lạm dụng cron phức tạp để thay thế business calendar.** Khi lịch nghỉ tùy từng quốc gia/đơn vị và thay đổi theo năm, thường nên tính lịch trong domain service rồi lập lịch theo `Instant`, hoặc dùng job scheduler hỗ trợ calendar đặc biệt.

## 10. Chuyển từ giờ Việt Nam sang UTC đúng cách

Giả sử tham chiếu là `2026-10-29T02:00:00Z`, tương ứng 09:00 tại Việt Nam. Cùng cron `0 30 9 * * MON-FRI` có hai kết quả khác nhau:

| Zone áp dụng | Thời điểm local tiếp theo | Instant tương ứng |
| --- | --- | --- |
| `Asia/Ho_Chi_Minh` | 2026-10-29 09:30 | `2026-10-29T02:30:00Z` |
| `UTC` | 2026-10-29 09:30 | `2026-10-29T09:30:00Z` |

Sự khác nhau là **bảy giờ** trong ví dụ. Nếu server đặt `-Duser.timezone=UTC` nhưng annotation có `zone="Asia/Ho_Chi_Minh"`, Spring vẫn phải diễn giải lịch theo vùng được chỉ định. Điều này giúp triển khai nhất quán trên nhiều môi trường.

Đối với một job theo múi giờ New York, offset UTC tương ứng có thể thay đổi theo mùa do DST; không thể tính bằng cách luôn cộng/trừ một số giờ cố định. Dùng `ZoneId`/rules từ Java time, không tự viết bảng offset thủ công.

## 11. Ví dụ Java 17 + Spring 6.2.9 chạy được: tính next execution

Ví dụ sử dụng `CronExpression` của Spring để **tính** những lượt chạy tiếp theo từ các mốc cố định, không cần chờ đồng hồ thực hay đợi đến ngày hôm sau. Ta kiểm tra cả sự khác nhau giữa hai zone và các thời điểm chuyển DST của New York.

Tạo project:

~~~text
spring-cron-zone-demo/
├── pom.xml
└── src/main/java/demo/CronZoneDemo.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-cron-zone-demo</artifactId>
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

### src/main/java/demo/CronZoneDemo.java

~~~java
package demo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.scheduling.support.CronExpression;

public class CronZoneDemo {
    public static void main(String[] args) {
        CronExpression workday = CronExpression.parse("0 30 9 * * MON-FRI");
        Instant reference = Instant.parse("2026-10-29T02:00:00Z");

        ZonedDateTime vietnam = workday.next(
                reference.atZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        ZonedDateTime utc = workday.next(reference.atZone(ZoneOffset.UTC));

        System.out.println("Vietnam local: " + vietnam.toLocalDateTime());
        System.out.println("Vietnam instant: " + vietnam.toInstant());
        System.out.println("UTC instant: " + utc.toInstant());

        CronExpression dailyAt230 = CronExpression.parse("0 30 2 * * *");
        ZonedDateTime beforeDstGap = ZonedDateTime.of(
                2026, 3, 7, 23, 0, 0, 0, ZoneId.of("America/New_York"));
        ZonedDateTime afterDstGap = dailyAt230.next(beforeDstGap);
        System.out.println("New York spring next: " + afterDstGap);

        CronExpression oneThirty = CronExpression.parse("0 30 1 * * *");
        ZonedDateTime beforeFall = ZonedDateTime.of(
                2026, 11, 1, 0, 0, 0, 0, ZoneId.of("America/New_York"));
        ZonedDateTime first = oneThirty.next(beforeFall);
        ZonedDateTime second = oneThirty.next(first);
        System.out.println("New York fall first: " + first);
        System.out.println("New York fall second: " + second);
    }
}
~~~

Chạy trong thư mục project:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.CronZoneDemo
~~~

**Stdout đã được kiểm tra thực tế** với Spring Framework 6.2.9 và target Java 17:

~~~text
Vietnam local: 2026-10-29T09:30
Vietnam instant: 2026-10-29T02:30:00Z
UTC instant: 2026-10-29T09:30:00Z
New York spring next: 2026-03-09T02:30-04:00[America/New_York]
New York fall first: 2026-11-01T01:30-04:00[America/New_York]
New York fall second: 2026-11-01T01:30-05:00[America/New_York]
~~~

Đây là **chương trình console tính lịch**, không phải một scheduler chạy background thực sự. Nó chứng minh cách parser Spring tính lần xuất hiện tiếp theo trên `ZonedDateTime`. Trong ứng dụng sử dụng `@Scheduled`, Spring gắn cron vào `Trigger` để thực thi; việc lên lịch còn chịu ảnh hưởng của lịch sử completion, scheduler clock, pause và implementation.

**Lưu ý về tính tái lập:** Đầu ra trên phụ thuộc rule timezone trong JDK/tzdb; bài đã chạy với một JDK hiện đại và các mốc năm 2026. Nếu chính sách DST của một quốc gia hoặc dữ liệu tzdb được cập nhật trong tương lai, mốc lịch của những năm liên quan cần được xác minh lại.

## 12. Giải thích Java demo: CronExpression không tự có zone

`CronExpression.parse(...)` nhận một biểu thức cron nhưng **không nhận `zone` trực tiếp**. Phép `next(temporal)` dựa vào kiểu temporal bạn đưa vào.

Trong ví dụ, ta gọi:

~~~java
CronExpression cron = CronExpression.parse("0 30 9 * * MON-FRI");
ZonedDateTime nextInVietnam =
    cron.next(Instant.parse("2026-10-29T02:00:00Z")
        .atZone(ZoneId.of("Asia/Ho_Chi_Minh")));
~~~

Đây là **snippet Java tích hợp**, cần imports từ `java.time` và Spring `CronExpression`. `Instant` xác định thời điểm tham chiếu, còn `atZone(...)` xác định cách diễn giải các trường “09:30”. `nextInVietnam.toInstant()` cho thời điểm thật tương ứng.

Không viết:

~~~java
LocalDateTime next = cron.next(LocalDateTime.now());
~~~

rồi tin rằng `next` đã xác định đúng thời điểm cho mọi khách hàng trên thế giới. `LocalDateTime` không chứa timezone; nếu dùng kết quả đó cho một business schedule theo region, bạn vẫn cần áp dụng rule zone (gồm các thời điểm thiếu/trùng do DST).

Spring cung cấp `CronTrigger` để tính các lần chạy khi kết hợp một scheduler và context; một `CronTrigger` có thể được tạo với biểu thức và `ZoneId`. Khi dùng `@Scheduled(zone="...")`, framework giải quyết ý nghĩa zone tương tự ở tầng đăng ký lịch.

## 13. DST spring-forward: khi 02:30 không tồn tại

Ở một số múi giờ, đồng hồ được chỉnh **nhảy tiến** khi bắt đầu giờ mùa hè. Tại New York, vào **2026-03-08**, sau 01:59:59 đồng hồ chuyển sang 03:00:00; **02:30 ngày đó không tồn tại** theo giờ địa phương.

Nếu cron là:

~~~java
@Scheduled(cron = "0 30 2 * * *", zone = "America/New_York")
public void nightlyJob() { /* ... */ }
~~~

thì lịch “02:30 hằng ngày” gặp một ngày **không có mốc local hợp lệ**. Với `CronExpression` trong ví dụ Java/Spring 6.2.9, lần tiếp theo sau `2026-03-07T23:00` là **2026-03-09 02:30 -04:00**, tức bỏ qua local slot không tồn tại ngày 8.

Nên lựa chọn theo hợp đồng nghiệp vụ:

- Nếu job chỉ mang tính quan sát định kỳ và được phép bỏ qua một lượt DST, có thể chấp nhận.
- Nếu việc cần **đúng một lần mỗi business date**, cron 02:30 ở khu vực DST là chưa đủ. Có thể chọn giờ ít rủi ro hơn, hoặc dựa vào business-date key/ledger và reconciliation để làm bù.
- Nếu muốn “khi 02:30 không tồn tại thì chạy 03:00 cùng ngày”, phải **thiết kế quy tắc bù mốc này**, không giả định annotation tự làm đúng theo mong muốn.

Với `Asia/Ho_Chi_Minh` theo quy tắc hiện hành không có một ngày nhảy qua 02:30 kiểu như ví dụ New York. Tuy vậy code đa thị trường không nên cứng hóa hành vi đó cho mọi zone.

## 14. DST fall-back: khi 01:30 có hai offset

Khi kết thúc giờ mùa hè, đồng hồ có thể **lùi lại** và một khoảng thời gian địa phương xuất hiện hai lần. Tại New York vào **2026-11-01**, mốc 01:30 xuất hiện một lần với offset `-04:00` và một lần với offset `-05:00`.

Kết quả của `CronExpression` 6.2.9 trong demo:

~~~text
2026-11-01T01:30-04:00[America/New_York]
2026-11-01T01:30-05:00[America/New_York]
~~~

Hai giá trị cùng có **giờ/phút địa phương** nhưng đại diện **hai `Instant` khác nhau**. Nếu nghiệp vụ định nghĩa “mỗi ngày chốt sổ một lần”, chỉ đếm callback của cron có thể làm một business date bị xử lý hai lần, tùy chính sách và scheduler implementation.

Giải pháp:

1. Định nghĩa trước “01:30 lần đầu”, “01:30 lần sau” hay “một lần bất kể offset”.
2. Lưu key bất biến theo business date, ví dụ `(tenantId, localDate, jobType)`, để phát hiện một ngày đã được xử lý.
3. Với yêu cầu chỉ một lượt, dùng unique constraint/ledger/idempotency, không phụ thuộc hoàn toàn vào số tick của cron.
4. Viết test quanh thời điểm DST fallback với **zone cụ thể** và version Spring/JDK đang dùng.
5. Nếu đồng bộ với hệ thống ngoài, truyền cả `Instant`, `ZoneId` và business date thay vì chỉ gửi chuỗi `"01:30"`.

**Cẩn thận:** ví dụ chứng minh hành vi của parser `CronExpression.next` với hai lần gọi liên tiếp. Nó không có nghĩa **mọi deployment của `@Scheduled` đều luôn chạy hai callback** ở DST overlap: trigger strategy, lịch sử completion, scheduler downtime và cách xử lý mốc trễ có thể làm runtime khác. Hãy test integration theo hợp đồng sản phẩm.

## 15. Ngày nghỉ, múi giờ địa phương và business calendar

Cron phù hợp những quy tắc lặp theo lịch như mỗi thứ Hai hoặc ngày 1 hằng tháng. Nhưng quy tắc “ngày làm việc cuối cùng của tháng ở Việt Nam” hoặc “9 giờ sáng ngày chứng khoán mở cửa tại thị trường X” có nhiều yếu tố hơn:

- Cuối tuần là thứ Bảy/Chủ nhật hay khác theo từng quốc gia?
- Ngày nghỉ quốc gia, nghỉ bù, lịch ngân hàng có đổi theo năm không?
- Nếu ngày được chọn không phải ngày làm việc, chạy trước hay sau?
- Nếu DST làm mất một local time, dời tới giờ nào?
- Có chạy bù nếu hệ thống đang bảo trì vào đúng mốc không?

**Giải pháp phổ biến:** Dùng `@Scheduled` cho **một nhịp đánh thức (poller)** tương đối đơn giản, rồi hỏi domain calendar xem ngày hôm nay có thực sự phải chạy không; hoặc dùng API `TaskScheduler.schedule(Runnable, Instant)` với các mốc đã được domain service tính toán theo `ZoneId`.

Ví dụ quy tắc minh họa:

~~~text
Calendar service -> next business date in ZoneId
                 -> choose LocalTime 09:30
                 -> resolve ambiguity/gap policy
                 -> convert to Instant
                 -> persist job due_at and unique business key
                 -> worker executes idempotently
~~~

Lịch trong database giúp phục hồi sau restart nhưng không thay thế việc kiểm soát **một instance nào sở hữu công việc** khi scale nhiều node.

## 16. Nhiều tenant ở nhiều múi giờ

Một `@Scheduled` với `zone="Asia/Ho_Chi_Minh"` có một quy tắc timezone cố định cho method đó. Nếu mỗi khách hàng có một `ZoneId` riêng, không nên cố tạo hàng nghìn biến thể annotation bằng cách sửa field trong singleton bean.

Các phương án:

| Quy mô và yêu cầu | Phương pháp phù hợp |
| --- | --- |
| Một lịch toàn ứng dụng theo một zone | `@Scheduled(cron=..., zone=...)` |
| Một số lịch cố định khác nhau theo nghiệp vụ | Nhiều method `@Scheduled`/bean có cấu hình phân biệt rõ |
| Mỗi khách hàng có lịch riêng, thay đổi runtime | Lưu schedule + `ZoneId` theo tenant, dùng `TaskScheduler` hoặc job scheduling service |
| Phải khôi phục lịch sau restart | Persist next due `Instant`, version và trạng thái job |
| Một lần toàn cluster, có audit/retry | Distributed job store/lease/queue và idempotent worker |

Để hỗ trợ timezone thay đổi của một tenant, lưu **region ID** cùng local business time. Nếu chỉ lưu `nextFireInstant` của lần tới, hệ thống sẽ biết mốc gần nhất nhưng không đủ dữ liệu để tái tính các mốc tương lai khi user chuyển zone hoặc daylight saving rules thay đổi.

Nếu lịch “09:30 giờ của tenant mỗi ngày”, tính mốc tiếp theo bằng `ZonedDateTime` hoặc `CronExpression.next` với `ZoneId` tương ứng. Kiểm soát tình huống local datetime không tồn tại hoặc có hai offset và định nghĩa rule nghiệp vụ thay vì để sự cố âm thầm.

## 17. Lịch timezone đúng vẫn cần kiểm soát cấu hình và missed runs

`@Scheduled(cron=..., zone=...)` thông thường resolve property và đăng ký lịch khi context khởi tạo; sửa YAML hoặc config server không mặc định cancel/re-register trigger đang sống. Nếu cần hot update, ứng dụng phải validate cron/ZoneId, giữ `ScheduledFuture`, hủy registration cũ và đăng ký lại có kiểm soát race với callback hiện tại.

Cron trong memory không có durable misfire ledger: nếu app tắt lúc 09:30 rồi khởi động 09:40, lịch 09:30 không tự được chạy bù theo business contract. Với báo cáo một lần mỗi local date, lưu `lastCompletedBusinessDate` hoặc các job keys cần xử lý và reconcile sau restart; đây là trách nhiệm ngoài việc tính đúng `Instant` theo zone.


## 18. Scheduler timezone, deploy và quan sát

Để vận hành nhất quán, cần nhìn cả **cấu hình** lẫn **thời điểm thực tế**:

- **Timezone cấu hình của job**: `Asia/Ho_Chi_Minh` hay `America/New_York`.
- **Timezone mặc định của JVM/scheduler**: chỉ có vai trò fallback khi không xác định `zone`.
- **Tzdb của JDK**: chứa quy tắc chuyển offset/DST. Khi chính phủ thay đổi chính sách, bản cập nhật JDK có thể thay đổi kết quả cron ở tương lai.
- **UTC instant dự kiến**: tính bằng `CronExpression`/scheduler và log để xác minh.
- **UTC instant bắt đầu thực tế**: có thể trễ do GC pause, scheduler thread bận hoặc hạ tầng nghẽn.
- **Business local date/time**: dùng `Instant.atZone(jobZone)` để báo cáo theo nghiệp vụ.
- **Tên instance và version schedule**: giúp phát hiện nhiều node thực hiện cùng một lịch.

Không dựa hoàn toàn vào việc đọc timestamp trong log có timezone mặc định khác. Log có thể đang hiển thị UTC trong khi cron chạy đúng giờ Việt Nam. So sánh **`Instant` dự kiến và thực tế** sẽ ít mơ hồ hơn.

**Các metric hữu ích:** số tick dự kiến, số lượt thực thi thành công/thất bại, scheduling lag, độ dài job, số ngày nghiệp vụ chưa hoàn tất, số duplicate, số lần retry, queue delay và trạng thái scheduler. Dùng label có cardinality thấp; không biến mỗi user ID thành một label metric.

## 19. Lịch cron không phải bảo đảm đúng giờ tuyệt đối hoặc chỉ chạy một lần

Ngay cả khi `cron` và `zone` đã chính xác, vẫn có những giới hạn khác:

| Yếu tố | Hệ quả |
| --- | --- |
| ThreadPoolTaskScheduler có ít worker, task khác chặn lâu | Callback có thể bị chạy trễ |
| GC, CPU throttling, hệ thống tạm dừng | Mốc scheduler bị bỏ lỡ hoặc trễ |
| Ứng dụng dừng/khởi động lại | Lịch trong memory không tự lưu toàn bộ missed runs |
| Deployment có 3 replica | Mỗi replica có thể đăng ký một `@Scheduled` tương tự |
| DST gap/overlap | Local time có thể không tồn tại hoặc xuất hiện hai lần |
| Tzdb thay đổi | Cách ánh xạ future local time sang UTC có thể thay đổi |
| Task dài hơn chu kỳ | Có thể tích tụ trễ/overlap qua các cơ chế scheduling khác nhau |
| Consumer nhận lại message | Business effect có thể trùng nếu không idempotent |

Vì vậy với việc có side effect như phát hóa đơn, chốt sổ, chuyển tiền hoặc đóng phiên, đừng xem một cron đúng cú pháp là chứng minh **exactly-once business effect**. Cần unique business key, transaction, idempotency và một cơ chế khôi phục/đối soát.

`@Scheduled` gọi định kỳ không có nghĩa nó chạy trong một transaction Spring tự động. Nếu cần transaction, đặt ranh giới thích hợp ở service được gọi qua proxy, không dựa vào annotation scheduling để truyền transaction ngầm.

## 20. Kiểm thử cron và zone: chỉ test giờ hệ thống là chưa đủ

Một bộ test đáng tin cần xác minh cả ý nghĩa thời gian và khả năng thực thi:

1. **Cron syntax:** `CronExpression.parse(cron)` chấp nhận biểu thức mong muốn; các biểu thức năm trường/giờ 24 phải bị từ chối.
2. **Zone ID:** `ZoneId.of(zone)` xác thực đúng region, không chấp nhận fallback ngầm sang GMT với một chuỗi bị gõ sai.
3. **Next execution:** từ một `Instant` cố định, tính kết quả cho `Asia/Ho_Chi_Minh` và `UTC`; so sánh cả local và UTC như demo.
4. **Ngày thường/cuối tuần:** kiểm tra `MON-FRI` từ một chiều thứ Sáu phải chuyển tới thứ Hai (trừ quy tắc khác).
5. **Cuối tháng và năm:** với cron ngày 31 hay `L`, kiểm tra tháng 2, tháng 4 và năm nhuận.
6. **DST spring gap:** xác nhận mốc 02:30 không tồn tại ở ngày chuyển giờ và policy có bỏ lượt hoặc bù theo nghiệp vụ.
7. **DST fall overlap:** xác nhận có hai offset nếu 01:30 xuất hiện hai lần; kiểm tra duplicate business date.
8. **Scheduler integration:** tạo Spring context bật `@EnableScheduling`, method `@Scheduled` và scheduler cụ thể; dùng latch để quan sát callback trong một cron test ngắn.
9. **Scheduler saturation:** giữ worker bằng latch, đo scheduling lag; không dùng `Thread.sleep(10)` để kỳ vọng thứ tự ngẫu nhiên.
10. **Multiple replicas:** hai context/instance cùng lịch có thể cùng chạy; xác minh cơ chế lock/dedupe nếu nghiệp vụ đòi hỏi một lượt.
11. **Restart/recovery:** tắt app qua một mốc cron rồi khởi động lại; kiểm tra hợp đồng catch-up từ job store nếu có.
12. **Config change:** đổi cron và zone qua API vận hành, xác minh lịch cũ bị hủy và lịch mới đăng ký đúng.
13. **Tzdb update:** với hệ thống đa quốc gia, chạy test theo các mốc DST quan trọng khi nâng JDK/image.

Các unit test nên dùng thời điểm **được cố định** thay vì `Instant.now()` khi kiểm chứng cron. Việc tính lịch từ một `ZonedDateTime` cố định giúp test không phụ thuộc giờ hiện tại hoặc vị trí đặt máy của developer.

## 21. Lỗi phổ biến khi triển khai

| Lỗi | Triệu chứng | Hướng sửa |
| --- | --- | --- |
| Dùng cron Unix năm trường | Parse error hoặc lịch không hợp lệ | Dùng đúng sáu trường của Spring |
| Không đặt `zone` | Dev chạy đúng, production UTC bị lệch giờ | Chỉ rõ region ID/UTC |
| Đặt UTC trong cron nhưng zone lại Việt Nam | Chạy sai giờ theo địa phương | Cron biểu diễn **giờ của zone**, không phải UTC đã chuyển |
| Dùng timezone viết tắt mơ hồ | Khác môi trường, DST sai | Dùng IANA region ID |
| Sao chép số thứ trong tuần từ Quartz | Chạy sai ngày | Dùng `MON`, `FRI`, `SUN` thay số |
| Tin `MON-FRI` bỏ qua ngày lễ | Chạy vào ngày nghỉ | Dùng business calendar |
| Tin `02:30` luôn tồn tại | Một ngày DST không có callback tương ứng | Chọn policy gap / bù mốc |
| Tin local 01:30 là duy nhất | Fall-back có hai instants | Dedupe theo business date, version |
| Thay YAML giữa runtime và chờ cron đổi ngay | Scheduler vẫn dùng lịch cũ | Cancel/re-register hoặc restart có kiểm soát |
| Tin `@Scheduled` lưu lịch qua restart | Bỏ lỡ job trong downtime | Durable job store/catch-up |
| Ba replica đều bật cùng cron | Tác vụ chạy ba lần | Lease/lock và idempotent worker |
| Chạy blocking I/O trên scheduler thread không timeout | Các job khác bị trễ | Giới hạn worker, tách executor, đặt I/O timeout |
| Chỉ xem log timestamp theo UTC rồi kết luận cron sai | Chẩn đoán lệch timezone log | Log zone, local time và UTC instant |

## 22. Checklist code review cho @Scheduled(cron, zone)

- Tôi đã xác nhận yêu cầu là **giờ địa phương nào**, không chỉ “09:30”.
- Tôi dùng biểu thức **sáu trường** theo Spring, hiểu chính xác thứ tự `second minute hour day-of-month month day-of-week`.
- Tôi ghi `zone` tường minh khi lịch gắn với timezone nghiệp vụ.
- Tôi ưu tiên IANA region ID (`Asia/Ho_Chi_Minh`) hoặc `UTC` thay vì viết tắt mơ hồ.
- Tôi đã kiểm tra ánh xạ local time sang UTC bằng `CronExpression.next(ZonedDateTime)`.
- Tôi đã kiểm thử cuối tuần, cuối tháng, năm nhuận và ngày DST nếu zone có chuyển giờ.
- Tôi phân biệt business date với UTC instant để chống duplicate hoặc ghi nhận trạng thái đã chạy.
- Tôi không coi lịch đã được đăng ký là tự refresh khi property thay đổi.
- Tôi có chính sách rõ khi JVM downtime, bỏ lỡ lượt chạy hoặc có nhiều replica.
- Tôi có logging/metrics của scheduled instant, actual start, zone và duration.
- Tôi giới hạn thời gian xử lý, pool, retry và side effect để một callback chậm không làm nghẽn lịch khác.
- Tôi biết `@Scheduled` không tự bảo đảm exactly-once, idempotency hoặc transaction.
- Tôi có integration test từ Spring context thật, bên cạnh unit test `CronExpression`.

**Kết luận:** Với tác vụ theo đồng hồ địa phương, `cron` định nghĩa **mốc trên mặt đồng hồ** còn `zone` định nghĩa **quy tắc thời gian của địa phương ấy**. Spring diễn giải hai giá trị thành thời điểm chạy. Cấu hình `zone` tường minh tránh lệ thuộc môi trường; kiểm thử với `ZonedDateTime` giúp nhận ra DST gap/overlap. Đối với job quan trọng, còn cần scheduler vận hành ổn định, persistence/leader coordination, idempotency và theo dõi missed runs.

## Tài liệu tham khảo chính thức

- [Spring Framework 6.2 – @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): cron sáu trường, `zone`, CRON_DISABLED và các thuộc tính scheduling.
- [Spring Framework 6.2 – CronExpression Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronExpression.html): `parse()`, `next()`, ký hiệu đặc biệt và macro.
- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@EnableScheduling`, `@Scheduled`, `CronTrigger`, scheduler infrastructure.
- [Spring Framework 6.2 – CronTrigger Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronTrigger.html): tính mốc cron có zone trong scheduler.
- [Java SE 17 – ZoneId](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZoneId.html): region IDs và timezone rules.
- [Java SE 17 – ZonedDateTime](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZonedDateTime.html): gap/overlap, offset, chuyển đổi giờ địa phương.
- [IANA Time Zone Database](https://www.iana.org/time-zones): nguồn quy tắc timezone được nhiều runtime phân phối qua tzdb.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-tasks-008">Giá trị trả về thông thường bị bỏ qua và khác biệt với phương thức reactive</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị trả về thông thường bị bỏ qua và khác biệt với phương thức reactive

## 1. Ý chính và phạm vi

Trong Spring Framework, `@Scheduled` đánh dấu một method được hạ tầng scheduling gọi theo **thời gian**: fixed rate, fixed delay, cron, hoặc một lần sau initial delay. Không có HTTP client hay service caller nào đứng chờ để nhận giá trị trả về của method này.

**Với method đồng bộ thông thường**, Spring gọi method theo lịch và **bỏ qua giá trị trả về**. Vì thế nên khai báo `void` để phản ánh hợp đồng rõ ràng. Nếu method trả `String`, `long` hoặc một POJO, phần thân vẫn chạy và tạo side effect, nhưng Spring không chuyển object trả về thành Future, response HTTP hoặc job result.

**Với method reactive từ Spring Framework 6.1**, tình huống khác về **cách method được thực thi**. Spring chấp nhận method trả `Publisher` (ví dụ Reactor `Mono`/`Flux`) hoặc một kiểu reactive được `ReactiveAdapterRegistry` chuyển sang Publisher **và hỗ trợ deferred subscription**. Spring gọi method **một lần để lấy Publisher**, sau đó **subscribe lại Publisher đó theo mỗi lần trigger**. Các tín hiệu `onNext` vẫn bị bỏ qua, nhưng subscription kích hoạt việc thực thi lazy pipeline; `onError`, completion, fixed delay và cancellation có quy tắc riêng.

~~~text
Method thường:

trigger #1 -> gọi bean.calculate() -> trả String -> bỏ giá trị String
trigger #2 -> gọi bean.calculate() -> trả String -> bỏ giá trị String
trigger #3 -> gọi bean.calculate() -> trả String -> bỏ giá trị String

Method reactive:

setup -> gọi bean.calculateReactive() -> Publisher P (giữ lại)

trigger #1 -> P.subscribe(...) -> thực thi pipeline -> onNext bỏ giá trị
trigger #2 -> P.subscribe(...) -> thực thi pipeline -> onNext bỏ giá trị
trigger #3 -> P.subscribe(...) -> thực thi pipeline -> onNext bỏ giá trị
~~~

Nói ngắn gọn, **đồng bộ: gọi method mỗi tick, bỏ return value**; **reactive: subscribe Publisher mỗi tick, bỏ emitted values**. Đừng đánh đồng "giá trị bị bỏ" với "tác vụ không được chạy". Return value không được scheduler tiêu thụ, nhưng thao tác ghi database, gửi email hoặc xử lý dữ liệu bên trong method/pipeline vẫn có thể xảy ra.

Bài thuộc nhóm **@Scheduled** của **Spring Framework – concurrency**, tập trung vào return values, reactive scheduling kể từ 6.1, cách viết cold/deferred publisher và các lỗi khi chuyển từ lập lịch đồng bộ sang reactive.

## 2. Điều kiện để @Scheduled hoạt động

Spring không tự quét các object bất kỳ được tạo bằng `new`. Một method có `@Scheduled` cần:

1. Được khai báo trên **Spring-managed bean**, đăng ký bằng `@Component`, `@Service`, `@Bean` hoặc cơ chế bean registration hợp lệ.
2. Hạ tầng annotation scheduling được bật bằng **`@EnableScheduling`** hoặc cấu hình tương đương trong đúng ApplicationContext.
3. Method **không có tham số**. Hạ tầng scheduling không tự cung cấp argument từ request/job payload.
4. Có một `TaskScheduler` phù hợp để chạy các triggers. Trong ứng dụng thực, nên xác định executor/pool, thread naming và vòng đời.
5. Với reactive method, có thư viện reactive thích hợp trong classpath; Publisher phải hỗ trợ **nhiều subscription** và deferred execution.

Ví dụ chuẩn cho method thường:

~~~java
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

@Component
public class CleanupJobs {
    @Scheduled(fixedDelay = 5000)
    public void removeExpiredEntries() {
        // Logic chạy theo lịch, không có return value.
    }
}
~~~

Đây là **các mảnh Java minh họa**, cần import các annotations từ Spring, component scanning hoặc bean registration. Method `removeExpiredEntries()` được gọi theo lịch, nhưng không có một đối tượng người gọi nào nhận giá trị.

### 2.1. Tránh đăng ký nhầm nhiều lịch

`@Scheduled` có thể repeatable. Nếu cùng method có nhiều `@Scheduled` annotations, Spring đăng ký từng lịch độc lập. Nếu có nhiều instance bean hoặc nhiều application nodes, mỗi instance/context cũng có thể đăng ký một lịch. Sự lặp này không liên quan đến return value nhưng rất quan trọng khi method có side effects: chỉ một giá trị được trả về không ngăn job chạy trùng.

## 3. Return value của phương thức Java thông thường bị bỏ qua

Đoạn code sau hợp lệ về mặt ý tưởng nhưng return value không có consumer:

~~~java
@Component
public class StatisticsJob {

    @Scheduled(fixedRate = 10000)
    public long countNewEvents() {
        long total = calculateNewEvents();
        return total;  // Spring scheduler không sử dụng con số này.
    }
}
~~~

Đây là **mảnh minh họa**, `calculateNewEvents()` và imports chưa được khai báo. Hạ tầng `ScheduledAnnotationBeanPostProcessor` tạo một scheduled callback. Ở đường synchronous, method được gọi mỗi tick, nhưng object hoặc primitive trả về không được chuyển tới caller.

Nếu `calculateNewEvents()` tốn CPU, nó vẫn thực thi. Nếu method cập nhật cache trước khi return, cache vẫn có thể thay đổi. **Chỉ giá trị trả về bị bỏ**, không phải method bị skip.

Thay vì trả số lượng record:

~~~java
@Scheduled(fixedRate = 10000)
public void countNewEvents() {
    long total = calculateNewEvents();
    metrics.recordNewEvents(total);
}
~~~

Đây là mảnh code minh họa với `metrics` do ứng dụng cung cấp. Khi cần bảo toàn kết quả, hãy **ghi rõ vào một kênh được thiết kế để nhận kết quả**: database/job store, event/message, metric, cache hoặc API trạng thái job. Đừng trông chờ scheduler tự lưu giá trị mà method return.

### 3.1. Phần thân chạy, giá trị trả về không phải ACK

Một phương thức:

~~~java
@Scheduled(cron = "0 0 2 * * *")
public ReportResult buildDailyReport() {
    ReportResult report = service.build();
    return report;
}
~~~

Đây là mảnh minh họa; `ReportResult` và `service` cần định nghĩa. Dù report được tạo, scheduler **không tự lưu report, không tự thông báo cho người dùng** và không tự cập nhật job status. Nếu một report phải truy vấn sau, nên có job ID và nơi lưu rõ ràng.

### 3.2. Vì sao nên dùng void?

Một scheduled method thường được đóng gói để hạ tầng `TaskScheduler` chạy như `Runnable`. `Runnable` không có return channel. `void` giúp API phản ánh điều đó, tránh khiến người đọc tưởng rằng `String` hay `Result` sẽ được framework tiêu thụ.

**Ngoại lệ có chủ đích** là reactive return type, khi chính Publisher là thứ cần thiết để Spring đăng ký subscription, không phải để lấy business result.

## 4. Spring 6.1 xử lý reactive @Scheduled như thế nào?

Spring Framework 6.1 hỗ trợ những dạng reactive scheduled methods sau khi có thư viện tương ứng:

- Trả `org.reactivestreams.Publisher<T>`, ví dụ `Mono<T>` hoặc `Flux<T>` của Reactor.
- Trả một kiểu có adapter thông qua **`ReactiveAdapterRegistry`** và adapter có `getDescriptor().isDeferred() == true`. Tài liệu Spring đưa `RxJava Single<T>` làm ví dụ.
- Kotlin `suspend fun`, với bridge **`kotlinx.coroutines.reactor`** tại runtime.
- Một số kiểu Kotlin `Flow` và `Deferred` khi các dependencies tương ứng được hỗ trợ.

Những method này **không được nhận tham số**. Khả năng reactive không biến method thành một event handler có tham số.

Spring gọi method reactive **một lần trong giai đoạn tạo scheduled task** để lấy một Publisher. Mỗi lần trigger, Spring chạy Runnable chuyên biệt để **subscribe** Publisher đó. Vì vậy method factory không được gọi mỗi tick như synchronous method.

~~~text
Giai đoạn đăng ký:
    reactiveMethod() ---gọi một lần---> Publisher P

Tick 1:
    P.subscribe() --> pipeline thực thi --> onNext / onComplete / onError

Tick 2:
    P.subscribe() --> pipeline thực thi --> onNext / onComplete / onError

Tick 3:
    P.subscribe() --> pipeline thực thi --> onNext / onComplete / onError
~~~

"Chỉ gọi một lần" áp dụng **cho mỗi scheduled registration**. Nếu method có hai `@Scheduled` annotations hoặc bean bị tạo nhiều instance, mỗi registration có thể tạo Publisher riêng. Điều này khác với việc chỉ có **một** Publisher duy nhất trong cả cluster.

### 4.1. Bảng so sánh nhanh

| Quan sát | `@Scheduled` trả `String` | `@Scheduled` trả `Mono<String>` |
| --- | --- | --- |
| Method Java được Spring gọi mỗi tick? | Có | Spring lấy Publisher một lần; subscribe mỗi tick |
| Object trả về dùng để làm gì? | Bị bỏ | Được giữ để đăng ký subscription |
| Giá trị String đến người dùng? | Không | `onNext(String)` cũng bị bỏ |
| Khi nào công việc bắt đầu? | Lúc method được gọi | Khi subscription diễn ra nếu publisher lazy |
| Lỗi theo đường nào? | Exception trong scheduled Runnable và ErrorHandler | `onError` signal, Spring ghi WARN và tiếp tục lịch |
| Fixed delay dựa trên đâu? | Thời điểm method hoàn tất | Thời điểm subscription hoàn tất; có chờ |
| Return type được thiết kế điển hình | `void` | `Mono<Void>` hoặc `Publisher<?>` có semantics thích hợp |

Việc Spring sử dụng Publisher để **subscribe** không có nghĩa `onNext` được tổng hợp hoặc truyền tới một Result collector. Reactive scheduling là mô hình **kích hoạt công việc theo lịch**.

## 5. OnNext cũng bị bỏ qua, nhưng pipeline vẫn chạy

Ví dụ:

~~~java
@Scheduled(fixedRate = 5000)
public Flux<String> greetings() {
    return Flux.just("Hello", "World");
}
~~~

Spring có thể subscribe vào Flux theo lịch. Flux phát ra hai tín hiệu `onNext("Hello")` và `onNext("World")`, nhưng hạ tầng scheduled subscription **bỏ giá trị**. Nó không in, không lưu, không trả chúng cho controller, và cũng không tự chuyển chúng thành message.

Nếu muốn lưu số lượng record đã xử lý, hãy viết side effect **trong pipeline**:

~~~java
@Scheduled(fixedDelay = 30000)
public Mono<Void> cleanData() {
    return Mono.defer(() ->
            repository.deleteExpired()
                    .doOnNext(count -> metrics.recordDeleted(count))
                    .then());
}
~~~

Đây là **mảnh code minh họa**: `repository.deleteExpired()` giả định trả `Mono<Long>`, `metrics` do ứng dụng cung cấp. `doOnNext` được thực thi trước khi tín hiệu đến subscriber Spring. `then()` chuyển pipeline sang `Mono<Void>` để thể hiện chỉ cần completion/error, không cần trả kết quả số học.

Một `Mono<Void>` không phải bắt buộc cho mọi scheduled reactive job, nhưng thường phù hợp hơn với **công việc side effect** khi không có business caller tiêu thụ giá trị.

## 6. Publisher phải deferred và có khả năng subscribe lặp lại

### 6.1. Lỗi quen thuộc: Mono.just với hàm eager

~~~java
@Scheduled(fixedRate = 10000)
public Mono<String> wrong() {
    String result = remoteApi.fetchBlocking();
    return Mono.just(result);
}
~~~

Đây là **mảnh code sai kỳ vọng** (cần bean `remoteApi`). Spring gọi method để lấy Publisher một lần; vì `remoteApi.fetchBlocking()` chạy **trước** khi `Mono.just` được tạo, lời gọi remote có thể chỉ xảy ra **một lần lúc cấu hình lịch**. Các subscription tiếp theo chỉ phát lại cùng `result`, trong khi Spring bỏ các `onNext`.

Thay bằng deferred execution:

~~~java
@Scheduled(fixedRate = 10000)
public Mono<String> correct() {
    return Mono.fromCallable(() -> remoteApi.fetchBlocking())
            .subscribeOn(Schedulers.boundedElastic());
}
~~~

Đây là mảnh code minh họa, cần Reactor `Schedulers` import và remote client. `Mono.fromCallable` chỉ gọi `remoteApi.fetchBlocking()` khi có subscription; `subscribeOn` đưa blocking operation sang Reactor scheduler phù hợp thay vì chiếm scheduled callback thread. Hãy vẫn đặt timeout, rate limit và concurrency bounds cho downstream.

Một cách khác:

~~~java
@Scheduled(fixedDelay = 10000)
public Mono<Void> sendReactive() {
    return Mono.defer(() -> client.send())
            .then();
}
~~~

Mảnh code giả định `client.send()` trả `Mono<Void>` hoặc Publisher thích hợp. `Mono.defer` khiến call `client.send()` được **tạo lại mỗi subscription**.

### 6.2. Cache và hot Publisher là một loại rủi ro khác

~~~java
private final Mono<String> once =
        Mono.fromCallable(this::loadCurrentValue).cache();

@Scheduled(fixedRate = 5000)
public Mono<String> cachedJob() {
    return once;
}
~~~

Đây là mảnh minh họa cần `loadCurrentValue`. Một `Mono.cache()` có thể lưu terminal result của subscription đầu và phát lại cho lần sau, vì thế công việc thật không được chạy mới mỗi tick. Nó có thể hợp lệ nếu mục tiêu là **cache**; nhưng sai nếu nghiệp vụ yêu cầu thực thi một cuộc gọi mới mỗi chu kỳ.

**Publisher hỗ trợ multiple subscribers** không đồng nghĩa mỗi subscription tạo side effect mới. Cần kiểm tra semantics của cold/hot/replay/cache trong pipeline thực tế.

## 7. CompletableFuture không thay thế reactive Publisher của @Scheduled

### 7.1. Tại sao CompletableFuture khác Mono?

Một `CompletableFuture<T>` thường đại diện cho một công việc đã được khởi động khi Future được tạo. Nó không đại diện cho "recipe" mà scheduler có thể subscribe lại để tạo computation mới mỗi tick.

`ReactiveAdapterRegistry` có thể cung cấp adapter chuyển `CompletableFuture` sang Publisher, nhưng adapter của `CompletableFuture` được đánh dấu **`isDeferred() == false`**. Spring Framework 6.1 yêu cầu reactive type được nhận diện **phải hỗ trợ deferred subscription**; `CompletableFuture` là trường hợp không phù hợp và có thể khiến quá trình đăng ký scheduled method **bị từ chối**.

~~~java
@Scheduled(fixedRate = 5000)
public CompletableFuture<String> questionable() {
    return CompletableFuture.supplyAsync(this::fetchRemote);
}
~~~

Đây là **mẫu không khuyến nghị**, không phải demo runnable. Đừng suy luận rằng `CompletableFuture` trả về sẽ được Spring `@Scheduled` chờ, hoặc do nó có object return nên chỉ bị "discard" như `String`: khi ReactiveAdapter nhận diện kiểu này trên Spring 6.1, nó đi qua **kiểm tra deferred type**.

### 7.2. Cách tích hợp Future có chủ đích

Nếu vẫn dùng một asynchronous API trả `CompletableFuture<Payload>`, bạn có hai lựa chọn:

**Cách A — method scheduled bình thường trả void và ứng dụng tự quản lý Future:**

~~~java
@Scheduled(fixedRate = 10000)
public void pollAsync() {
    CompletableFuture<Payload> future = client.fetchAsync();
    future.whenComplete((payload, error) -> {
        if (error != null) {
            recordFailure(error);
        } else {
            persist(payload);
        }
    });
}
~~~

Đây là mảnh code minh họa, cần `Payload`, `client`, `recordFailure`, `persist`. Scheduler coi method `pollAsync()` hoàn tất khi callback được đăng ký và method trả về, **không chờ Future bên trong**. Với fixed delay kiểu void, độ trễ giữa các lần gọi tính từ khi `pollAsync()` trả về, không phải khi I/O Future kết thúc. Điều này có thể gây nhiều jobs chồng lấp nếu I/O chậm.

**Cách B — tạo Future tại mỗi reactive subscription:**

~~~java
@Scheduled(fixedDelay = 10000)
public Mono<Void> pollReactive() {
    return Mono.defer(() ->
            Mono.fromFuture(client.fetchAsync())
                    .doOnNext(this::persist)
                    .then());
}
~~~

Đây là mảnh code minh họa. Vì `client.fetchAsync()` nằm trong `Mono.defer`, mỗi scheduled subscription tạo một Future mới. Spring nhận **Mono** deferred, không nhận trực tiếp một `CompletableFuture` không deferred. Với fixed delay, Spring chờ Mono hoàn tất; nhưng cancellation của Future và side effects của remote request cần được thiết kế riêng.

**Không hiểu nhầm** rằng `Mono.fromFuture(...)` tự hủy mọi external I/O: cancellation semantics của source Future và remote client có giới hạn.

## 8. Lỗi reactive onError và lỗi synchronous khác nhau

### 8.1. Synchronous scheduled method ném exception

~~~java
@Scheduled(fixedRate = 5000)
public void ordinary() {
    throw new IllegalStateException("cannot refresh");
}
~~~

Đây là mảnh minh họa. Với scheduled `Runnable` thông thường, exception đi theo cơ chế **ErrorHandler của scheduler**. Trong cấu hình mặc định Spring, repeating tasks thường được log/suppress lỗi để lịch tiếp tục, nhưng custom handler có thể propagate exception và khiến lịch bị dừng theo semantics JDK periodic scheduling.

### 8.2. Reactive method phát tín hiệu onError

~~~java
@Scheduled(fixedRate = 5000)
public Mono<Void> reactive() {
    return Mono.defer(() ->
            Mono.error(new IllegalStateException("cannot refresh")));
}
~~~

Đây là mảnh minh họa. `onError` được emit trong publisher subscription, không phải là một exception đồng bộ được ném trực tiếp bởi scheduled Runnable. Spring **ghi lỗi ở mức WARN và tiếp tục** các lần scheduled subscriptions. Theo tài liệu Spring, **ErrorHandler của scheduler không tham gia** xử lý reactive `onError` của đường này.

Nếu cần metric/fallback:

~~~java
@Scheduled(fixedRate = 5000)
public Mono<Void> monitored() {
    return Mono.defer(() ->
            repository.refresh()
                    .doOnError(metrics::recordError)
                    .onErrorResume(error -> Mono.empty()));
}
~~~

Đây là mảnh minh họa: `repository.refresh()` giả định trả `Mono<Void>`; `metrics` có method thích hợp. `onErrorResume` **biến lỗi thành completion sau fallback**, không tự khôi phục side effect bị thiếu. Nếu lỗi cần được hệ thống giám sát nhìn thấy, có thể dùng `doOnError` và **không** nuốt signal.

`AsyncUncaughtExceptionHandler` dành cho một số `@Async void` methods, **không thay thế ErrorHandler hoặc onError monitoring** của reactive `@Scheduled`.

## 9. Fixed delay với Publisher: Spring đợi subscription hoàn thành

Trong **reactive `@Scheduled(fixedDelay = ...)`**, Spring Framework 6.1 **chặn scheduler Runnable cho tới khi subscription hoàn thành** để giữ đúng fixed-delay contract.

~~~text
subscribe P ----[P đang chạy / I/O / work]---- onComplete
                                                    |
                                              đợi fixedDelay
                                                    |
                                             subscribe P tiếp
~~~

Nếu Publisher chạy rất lâu, scheduler worker có thể bị giữ chờ; những lịch khác dùng chung pool nhỏ sẽ bị ảnh hưởng. Nếu Publisher **không bao giờ kết thúc**, chẳng hạn một `Flux.interval` vô hạn không giới hạn hoặc không có timeout, lịch fixed delay có thể không có lần subscribe tiếp theo.

~~~java
@Scheduled(fixedDelay = 10000)
public Mono<Void> bounded() {
    return Mono.defer(() -> remoteService.refresh())
            .timeout(Duration.ofSeconds(3));
}
~~~

Mảnh code cần `Duration` và `remoteService.refresh()` trả `Mono<Void>`. `timeout` trong Reactor khiến publisher phát `onError` khi vượt hạn; Spring có thể log WARN và tiếp tục theo lịch. Timeout/cancellation của Publisher **không bảo đảm** một blocking JDBC query hay external side effect bị dừng ngay.

### 9.1. Fixed rate/cron có thể tạo subscriptions đang chồng lấp

Với `@Scheduled(fixedRate = ...)` hoặc cron, Spring **không chặn scheduler Runnable** để đợi mọi subscription kết thúc theo fixed-delay như trên. Một lần subscribe có thể đang xử lý I/O khi tick mới kích hoạt lần subscribe khác.

**Hệ quả:** nếu một job yêu cầu "chỉ một lần chạy active", phải có concurrency guard, semaphore, backpressure/skip policy, hoặc lock/job store. `Mono` không tự bảo đảm mỗi subscription được serialize. Một `AtomicBoolean` chỉ bảo vệ trên một JVM, không bảo vệ khi nhiều replicas cùng chạy.

### 9.2. Synchronous fixed delay và reactive fixed delay không giống nhau hoàn toàn

- Synchronous: scheduler đợi **method body trả về hoặc ném lỗi**, rồi mới tính fixed delay.
- Reactive: method body thường đã chạy **một lần** để lấy Publisher; scheduler đợi **subscription hiện tại hoàn tất**, rồi mới tính fixed delay.
- Synchronous method tự trả `CompletableFuture` từ một callback void khác rồi kết thúc không đồng nghĩa scheduler sẽ đợi Future; phải định nghĩa riêng ranh giới chờ.
- Reactive fixed delay có thể làm scheduler thread block ngay cả khi I/O thực chạy trên Reactor worker khác.

## 10. Ví dụ chạy được: Java 17 + Spring Framework 6.1 + Reactor

Demo sau dùng `CountDownLatch` thay vì so sánh tốc độ để quan sát:

1. Method synchronous trả `String` được Spring **gọi nhiều lần** theo lịch.
2. Reactive method tạo `Mono<String>` được Spring **gọi một lần** lúc đăng ký.
3. `Mono.defer` giúp Publisher **được subscribe nhiều lần**, tương ứng các ticks.
4. Tín hiệu `onNext` được phát trong pipeline nhưng **giá trị không được Spring trả cho business caller**.

Chạy bằng **Java 17+**, **Spring Framework 6.1.14**, **Reactor Core 3.6.11**, Gradle 8.5+ (hoặc bản tương thích JDK 17). Không cần Spring Boot, HTTP server hoặc database.

### 10.1. Cấu trúc project

~~~text
scheduled-return-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/ScheduledReturnDemo.java
~~~

**settings.gradle**

~~~groovy
rootProject.name = 'scheduled-return-demo'
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
    implementation 'io.projectreactor:reactor-core:3.6.11'
}

application {
    mainClass = 'demo.ScheduledReturnDemo'
}
~~~

**src/main/java/demo/ScheduledReturnDemo.java**

~~~java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import reactor.core.publisher.Mono;

public final class ScheduledReturnDemo {

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            DemoJobs jobs = context.getBean(DemoJobs.class);

            if (!jobs.plainRanTwice.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Ordinary task timeout");
            }
            if (!jobs.reactiveEmittedTwice.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Reactive task timeout");
            }

            System.out.println("ordinary invoked twice: " +
                    (jobs.plainCalls.get() >= 2));
            System.out.println("reactive method called once: " +
                    (jobs.publisherFactories.get() == 1));
            System.out.println("reactive subscribed twice: " +
                    (jobs.subscriptions.get() >= 2));
            System.out.println("reactive onNext twice: " +
                    (jobs.emissions.get() >= 2));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    public static class Config {

        @Bean("taskScheduler")
        public ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2);
            scheduler.setThreadNamePrefix("scheduled-demo-");
            return scheduler;
        }

        @Bean
        public DemoJobs demoJobs() {
            return new DemoJobs();
        }
    }

    public static class DemoJobs {
        final AtomicInteger plainCalls = new AtomicInteger();
        final AtomicInteger publisherFactories = new AtomicInteger();
        final AtomicInteger subscriptions = new AtomicInteger();
        final AtomicInteger emissions = new AtomicInteger();

        final CountDownLatch plainRanTwice = new CountDownLatch(2);
        final CountDownLatch reactiveEmittedTwice =
                new CountDownLatch(2);

        // Method bình thường được gọi mỗi tick, return bị bỏ.
        @Scheduled(fixedRate = 40)
        public String ordinaryScheduled() {
            plainCalls.incrementAndGet();
            plainRanTwice.countDown();
            return "unused ordinary return";
        }

        // Spring gọi method này một lần để lấy Publisher.
        @Scheduled(fixedRate = 40)
        public Mono<String> reactiveScheduled() {
            publisherFactories.incrementAndGet();

            // Mỗi scheduled subscription vào Mono.defer sẽ chạy lại đây.
            return Mono.defer(() -> {
                subscriptions.incrementAndGet();
                return Mono.just("unused onNext")
                        .doOnNext(value -> {
                            emissions.incrementAndGet();
                            reactiveEmittedTwice.countDown();
                        });
            });
        }
    }
}
~~~

### 10.2. Chạy chương trình

Tại thư mục `scheduled-return-demo`:

~~~bash
gradle run
~~~

Nếu có Gradle Wrapper, dùng `./gradlew run` (Linux/macOS) hoặc `gradlew.bat run` (Windows).

**Stdout của chương trình khi scheduling hoạt động bình thường, hai latch hoàn tất trước timeout:**

~~~text
ordinary invoked twice: true
reactive method called once: true
reactive subscribed twice: true
reactive onNext twice: true
~~~

Spring/Gradle có thể in các log khác. Các dòng trên được viết ở `main`, không phải do hạ tầng `@Scheduled` tự in return value.

### 10.3. Giải thích kết quả có kiểm chứng

- Bean `DemoJobs` được Spring tạo từ `@Bean`; `@EnableScheduling` bật hạ tầng scheduling. Một bean `ThreadPoolTaskScheduler` tên `taskScheduler` cung cấp scheduler có hai worker.
- `ordinaryScheduled()` trả `String` bình thường, không thuộc kiểu reactive được nhận diện, nên đường synchronous gọi method mỗi tick. `plainCalls` tăng và latch `plainRanTwice` đếm hai lần.
- `reactiveScheduled()` trả `Mono<String>` nên được xử lý như reactive method. `publisherFactories` chỉ tăng khi Spring lấy Mono ban đầu.
- `Mono.defer` chứa computation lazy, `subscriptions` tăng khi Spring subscribe mỗi lần; `doOnNext` nằm trong pipeline và đếm được các giá trị được phát.
- Cả `"unused ordinary return"` và `"unused onNext"` **không được scheduler biến thành output nghiệp vụ**. Demo dùng counters để quan sát cơ chế, không tự chứng minh bằng cách đưa giá trị trả về cho caller (vì không có kênh ấy).
- `CountDownLatch` bảo đảm chương trình chỉ in kết quả khi hai lần chạy đã được quan sát. Nếu scheduler bị nghẽn quá lâu, code ném exception thay vì báo thành công giả.
- `try-with-resources` đóng context, hạ tầng Spring hủy các lịch và đóng scheduler theo lifecycle. Một vài tick có thể chạy thêm giữa lúc latch về 0 và lúc đóng; `>=2` mới là kiểm tra phù hợp.

Interval 40 ms chỉ phục vụ demo. Production phải chọn lịch dựa trên thời gian xử lý, budget CPU/DB/API, số worker và khả năng chồng lấp; không copy một interval cực ngắn cho job nặng.

## 11. Kênh trả kết quả của @Scheduled khác @Async

| Đường gọi | Ai kích hoạt? | Kết quả được quan sát ở đâu? |
| --- | --- | --- |
| Method Java thông thường | Caller | Return value trực tiếp |
| `@Async` qua proxy, trả Future/CompletableFuture | Bean caller | Future/completion/exception |
| `@Scheduled` method đồng bộ trả `String` | TaskScheduler | Return bị bỏ qua |
| `@Scheduled` reactive trả Publisher deferred | TaskScheduler | Subscribe lại mỗi tick, nhưng bỏ `onNext`; quan sát terminal signal riêng |

Một `@Scheduled void` method có thể tự tạo `CompletableFuture` và gắn callback để ghi kết quả; nhưng scheduler **không tự chờ future đó** chỉ vì nó được tạo bên trong method. Với `fixedDelay`, nếu callback trả về sau submit thì delay tính từ **kết thúc callback**, không tính từ lúc I/O Future hoàn thành. Nếu muốn completion-aware, thiết kế worker handle/coordination rõ ràng hoặc dùng reactive deferred Publisher với semantics phù hợp của Spring 6.1+.


## 12. Kết quả nghiệp vụ phải có nơi lưu/tiêu thụ rõ ràng

Một cron job tính báo cáo:

~~~java
@Scheduled(cron = "0 0 2 * * *")
public DailyReport calculateReport() {
    return reportService.calculate();
}
~~~

Mảnh code này chỉ trả một `DailyReport` mà scheduler bỏ. Nếu muốn người dùng tải báo cáo, hãy tạo/lưu job:

~~~java
@Scheduled(cron = "0 0 2 * * *")
public void calculateReport() {
    DailyReport report = reportService.calculate();
    reportStore.save(report);
}
~~~

Mảnh code cần `DailyReport`, `reportService` và `reportStore` phù hợp. Với reactive workflow, ghi dữ liệu trong pipeline:

~~~java
@Scheduled(cron = "0 0 2 * * *")
public Mono<Void> calculateReactiveReport() {
    return Mono.defer(() ->
            reactiveService.calculate()
                    .flatMap(reportStore::save)
                    .then());
}
~~~

Mảnh code giả định `reactiveService.calculate()` và `reportStore.save(...)` trả reactive types thích hợp. `then()` làm pipeline `Mono<Void>`: scheduler chỉ cần biết terminal signals, không nhận report object. Hãy xử lý transaction, timeout, retry và delivery semantics bên trong service/pipeline.

**Đối với công việc không được phép mất**, `@Scheduled` trong memory không đủ làm durable job store. Có thể cần transaction outbox, DB-backed jobs, message broker hoặc scheduler chuyên biệt, cùng idempotency và trạng thái. Một return value bị bỏ không tự biến thành ghi nhận hoàn thành.

## 13. Lựa chọn fixedRate, fixedDelay và cron cho reactive jobs

| Loại lịch | Với sync method | Với reactive Publisher |
| --- | --- | --- |
| `fixedRate` | Cố lập theo khoảng cách giữa các lần bắt đầu; body được gọi từng tick | Publisher được subscribe theo tick; các subscriptions có thể chồng lấp |
| `fixedDelay` | Chờ body hoàn tất rồi mới tính delay | **Chờ subscription hoàn tất**, nên có thể block scheduler Runnable |
| `cron` | Chạy theo lịch calendar; body trả về bình thường | Subscribe theo lịch cron; có thể đang còn subscription cũ |
| `initialDelay` | Lùi lần gọi đầu | Lùi lần subscription đầu; publisher factory có thể đã được tạo lúc setup |

Chọn `fixedDelay` khi cần trì hoãn giữa các lần hoàn thành liên tiếp; tuy vậy nó có thể giữ scheduler thread, và nếu Publisher không complete thì không có lịch tiếp theo. Chọn `fixedRate` khi cần nhịp gửi ổn định, nhưng phải có **giới hạn concurrency** nếu mỗi request chậm. Với cron, quy định timezone khi lịch liên quan giờ kinh doanh.

Không có kiểu lịch nào tự bảo đảm **exactly once** trên nhiều application nodes.

## 14. Shutdown, cancellation và lỗi tải

Khi bean chứa reactive `@Scheduled` bị destroy hoặc context đóng, Spring hủy scheduled tasks, bao gồm **subscription kế tiếp** và **các subscription đang active** mà nó theo dõi. Đây là cơ chế lifecycle của scheduled registration.

Tuy nhiên:

- Reactor cancellation không đảm bảo external API đã hoàn tất/rollback.
- Lịch có thể kích hoạt side effect ở downstream trước khi cancel.
- Fixed-rate có thể đã tạo nhiều active subscriptions.
- Một pool nhỏ hoặc một publisher blocking không đúng chỗ có thể giữ scheduling threads và làm trễ job khác.
- Khi executor shutdown hoặc từ chối công việc, cần quan sát lỗi/rejection tại ranh giới phù hợp.
- Khi JVM crash, những lần lịch trong memory chưa thực thi không tự được chạy bù.

Nếu công việc quan trọng, nên phân biệt **schedule created**, **subscription started**, **business transaction committed**, **result stored** và **subscription cancelled**. Một scheduled tick không phải xác nhận side effect đã thành công.

## 15. Những anti-pattern và cách sửa

| Anti-pattern | Vấn đề | Cách sửa |
| --- | --- | --- |
| Trả `String` từ synchronous `@Scheduled` rồi mong framework lưu | Return bị bỏ | `void` và ghi database/metric/event |
| Dùng `@Scheduled CompletableFuture<T>` như `@Async` | Adapter không deferred, không đúng reactive contract | `void` tự quản Future hoặc `Mono.defer` |
| `Mono.just(api.fetchBlocking())` | Fetch chạy eager lúc tạo Publisher | `Mono.fromCallable`/`Mono.defer`, thread phù hợp |
| `Mono.cache()` cho job phải chạy lại mỗi tick | Subscriptions sau có thể chỉ nhận kết quả cache | Cold pipeline không cache ngoài ý muốn |
| Trả `Flux<String>` rồi kỳ vọng scheduler thu thập values | onNext bị bỏ | Ghi xử lý bên trong pipeline |
| Dùng Publisher vô hạn với `fixedDelay` | Không có terminal event; lịch có thể chờ mãi | Giới hạn/take/timeout hoặc đổi thiết kế |
| Lịch `fixedRate` chạy I/O chậm mà không giới hạn | Nhiều subscription chồng lấp | Concurrency limit, backpressure, skip/queue policy |
| Chỉ cấu hình `TaskScheduler.ErrorHandler` để bắt reactive onError | onError không đi qua ErrorHandler của đường reactive | `doOnError`, metrics, log + Spring WARN |
| `onErrorResume` bỏ lỗi vô điều kiện | Workflow nhìn như thành công dù mất side effect | Fallback rõ nghĩa hoặc giữ onError |
| Không bật `@EnableScheduling` | Annotation có thể không được đăng ký | Bật đúng context |
| Tạo bean nhiều lần hoặc repeat @Scheduled | Đăng ký nhiều lịch | Kiểm tra bean instances/schedules |
| Cho rằng `@Scheduled` trả Future thì caller có handle | Không có business caller trực tiếp | Thiết kế job status API riêng |
| Một lịch in-memory được cho là durable | Mất lịch/run khi process crash | Job store/outbox/scheduler bền vững |
| Dùng cùng pool 1 thread cho nhiều blocking fixed-delay jobs | Task khác bị trì hoãn | Tách scheduler/worker pools hoặc thiết kế non-blocking |

## 16. Checklist kiểm thử và code review

- [ ] Xác định rõ version Spring: reactive `@Scheduled` được hỗ trợ từ **6.1**.
- [ ] Bean được Spring quản lý, `@EnableScheduling` đã bật đúng context.
- [ ] Method `@Scheduled` không có tham số.
- [ ] Với sync method, chọn `void` nếu không có lý do giao diện đặc biệt; return bình thường bị bỏ.
- [ ] Với reactive method, dùng Publisher/deferred adapter thực sự hỗ trợ multiple subscriptions.
- [ ] Không dùng direct `CompletableFuture` như reactive scheduled return.
- [ ] Side effect được đưa vào cold publisher, không gọi eager khi factory method tạo Mono.
- [ ] Không phụ thuộc vào `onNext` được Spring lưu; tự tiêu thụ kết quả/metric bên trong pipeline.
- [ ] Reactive `onError` có monitoring, hiểu WARN/recovery và khác sync ErrorHandler.
- [ ] Với `fixedDelay`, Publisher phải hoàn tất và có timeout khi cần.
- [ ] Với `fixedRate`/cron, đã đánh giá khả năng nhiều subscriptions đồng thời.
- [ ] Có giới hạn pool, downstream rate limit, retry budget, idempotency.
- [ ] Theo dõi số lần method factory được gọi và số lần Publisher subscribe riêng biệt trong test.
- [ ] Đánh giá cancellation khi bean/context đóng.
- [ ] Với job bắt buộc, có cơ chế lưu trạng thái/durability và chính sách xử lý khi restart.
- [ ] Không nhầm `@Scheduled` với `@Async` hoặc `TaskScheduler.schedule(...)`.

## 17. Câu hỏi tự kiểm tra

1. **`@Scheduled` trả `int` thì Spring lưu số đó ở đâu?** Không có nơi lưu mặc định; return bị bỏ.
2. **Return bị bỏ nghĩa là method không chạy?** Không. Body vẫn thực thi theo lịch.
3. **Vì sao `void` thường tốt hơn cho sync scheduled method?** Nó nói rõ không có result channel.
4. **Spring 6.1 bổ sung khả năng reactive scheduled method nào?** Trả Publisher hoặc kiểu có adapter deferred, cùng một số Kotlin coroutine types.
5. **Spring gọi reactive factory method mỗi tick không?** Không theo mô hình hỗ trợ: lấy Publisher một lần cho registration rồi subscribe lại.
6. **`onNext` giá trị được trả cho ai?** Spring scheduled subscriber bỏ qua; xử lý trong pipeline nếu cần.
7. **Vì sao `Mono.defer` quan trọng?** Side effect/computation được tạo khi có subscription mới.
8. **`Mono.just(fetch())` có gọi `fetch` mỗi tick không?** Không mặc nhiên; `fetch()` là eager khi tạo Mono.
9. **`CompletableFuture` có được coi là deferred reactive return phù hợp?** Không; adapter của nó không deferred.
10. **`fixedDelay` reactive chờ kết thúc cái gì?** Chờ current subscription terminal event.
11. **Một Publisher vô hạn trong fixedDelay có thể gây gì?** Scheduler chờ và không có lần subscription tiếp theo đúng mong đợi.
12. **`fixedRate` reactive có thể overlap không?** Có, khi subscription trước chưa xong mà tick mới đến.
13. **Reactive onError có đi qua scheduler ErrorHandler không?** Không; Spring log WARN và tiếp tục đăng ký lịch.
14. **`onErrorResume` có tự retry không?** Không; nó chọn fallback publisher, có thể kết thúc thành công nếu fallback complete.
15. **`@Async` khác @Scheduled ở giá trị trả về thế nào?** @Async có caller nhận Future; @Scheduled không có business caller và bỏ output.
16. **Demo dùng chỉ số nào để phân biệt sync và reactive?** Số lần gọi method thường, số lần tạo Publisher, số lần subscribe và onNext.
17. **Nếu có ba nodes cùng chạy lịch thì một job chỉ chạy một lần?** Không; cần distributed coordination khi cần.
18. **Khi context đóng, reactive subscriptions còn active có thể được cancel không?** Có theo lifecycle Spring, nhưng không bảo đảm side effect bên ngoài tự đảo ngược.
19. **Muốn người dùng xem báo cáo của job, chỉ return report là đủ?** Không; phải lưu kết quả/status vào nơi client có thể tra cứu.
20. **Điểm ghi nhớ quan trọng nhất là gì?** Sync return bị bỏ; reactive Publisher được subscribe lặp lại, nhưng emitted values vẫn bị bỏ.

**Kết luận:** Với `@Scheduled` thông thường, **giá trị trả về bị bỏ qua**, nên `void` thường là lựa chọn rõ ràng. Từ Spring 6.1, reactive scheduled method **trả Publisher được defer**, Spring lấy Publisher một lần và **subscribe vào nó theo mỗi tick**. Các `onNext` values không phải business result; onError, fixed delay, concurrency và cancellation có semantics riêng. Thiết kế kết quả đầu ra, side effects, giám sát lỗi và giới hạn tải một cách tường minh.

### Tài liệu chính thức

- [Spring Framework — Task Execution and Scheduling, mục @Scheduled và Reactive Methods](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework 6.1 — @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework 6.1 — ScheduledAnnotationBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/annotation/ScheduledAnnotationBeanPostProcessor.html)
- [Spring Framework — ReactiveAdapterRegistry](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/ReactiveAdapterRegistry.html)
- [Spring Framework — ReactiveTypeDescriptor](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/ReactiveTypeDescriptor.html)
- [Project Reactor — Reactor Core Reference](https://projectreactor.io/docs/core/release/reference/)
- [Java 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-tasks-009">Annotation lặp lại tạo nhiều lịch độc lập trên cùng phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Annotation lặp lại tạo nhiều lịch độc lập trên cùng phương thức

## 1. Vấn đề cần hiểu

Trong Spring Framework, `@Scheduled` cho phép đăng ký một phương thức để chạy theo `fixedRate`, `fixedDelay` hoặc lịch `cron`. Một chi tiết rất quan trọng nhưng dễ bị bỏ qua: **`@Scheduled` là repeatable annotation**. Ta có thể đặt **nhiều annotation `@Scheduled` trên cùng một phương thức** và Spring sẽ xử lý **mỗi khai báo như một lịch riêng**.

Điều đó **không** có nghĩa Spring gộp các biểu thức thành một lịch có cơ chế chống trùng hay tự khóa method. Hai lịch có thể kích hoạt vào cùng thời điểm hoặc gần nhau; trong những điều kiện thực thi thích hợp, **cùng một phương thức có thể được gọi đồng thời từ nhiều scheduler threads**.

> **Quy tắc cốt lõi:** “Một method” không đồng nghĩa “một lịch”. Số lần `@Scheduled` được khai báo có thể tạo ra số đăng ký lịch tương ứng, mỗi lịch có trigger, nhịp và trạng thái riêng.

Đây là hành vi **được Javadoc `org.springframework.scheduling.annotation.Scheduled` và Spring Reference xác nhận**, không phải bug của scheduler. Bài này tập trung vào kiến trúc **annotation-driven scheduling**, việc cùng phương thức có nhiều trigger và cách kiểm soát hệ quả concurrency trong ứng dụng.

### Mục tiêu học tập

Bạn sẽ:

- Hiểu Java `@Repeatable(Schedules.class)` và cách khai báo hai hay nhiều `@Scheduled` trên một method.
- Mô tả vòng đăng ký lịch của `@EnableScheduling`, `ScheduledAnnotationBeanPostProcessor` và `TaskScheduler`.
- Phân biệt **hai lịch độc lập trên cùng method** với **một lịch có hai lần chạy kế tiếp**.
- Giải thích vì sao `fixedDelay`/`fixedRate` **của từng lịch** không bảo đảm độc quyền trên toàn bộ method.
- Chạy ví dụ Java 17+ và Spring Framework 6.1.3 có `CountDownLatch` để chứng minh **cùng method có thể đang chạy hai lần đồng thời**, không dựa vào may rủi của `sleep`.
- Tổ chức scheduler pool, tránh duplicate side effects, xử lý concurrency, idempotency và trạng thái trong ứng dụng nhiều replica.
- Phân biệt trùng do **repeatable annotation**, do **hai bean instances**, do **hai ApplicationContexts** hoặc do **nhiều ứng dụng chạy song song**.
- Viết test và metric đo số lần kích hoạt, độ trễ lịch, thời gian thực thi và số execution chồng lấn.

**Phiên bản bài học:** Java **17+**, Spring Framework **6.1.3** cho ví dụ Maven. Repeatable `@Scheduled` không phải tính năng chỉ mới có ở Spring 6.1; ví dụ dùng phiên bản này để thống nhất các API và cấu hình.

## 2. Cú pháp: @Scheduled là một repeatable annotation

Ở Java, một annotation chỉ có thể lặp nhiều lần trên cùng phần tử nếu loại annotation đó khai báo `@Repeatable` và một container annotation phù hợp. Spring `@Scheduled` có khai báo tương đương:

~~~java
// Trích ý nghĩa annotation, KHÔNG phải mã nguồn đầy đủ để tự biên dịch.
@Repeatable(Schedules.class)
public @interface Scheduled {
    // cron, fixedRate, fixedDelay, initialDelay, zone, ...
}
~~~

`@Schedules` là container annotation chứa nhiều `@Scheduled`. Trên Java 17, cách viết lặp trực tiếp là gọn nhất:

~~~java
// Fragment: phương thức trong một Spring-managed bean.
@Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
@Scheduled(cron = "0 0 17 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
public void synchronizeCatalog() {
    // Một nghiệp vụ, được kích hoạt bởi hai lịch riêng.
}
~~~

Một lần chạy dự kiến vào **09:00** và một lần vào **17:00** từ thứ Hai đến thứ Sáu theo timezone đã chỉ định. Hai cron expressions nằm trên cùng method, nhưng chúng là **hai đăng ký lịch độc lập**.

Cách tương đương khi cần biểu diễn container tường minh:

~~~java
// Fragment thay thế cho HAI @Scheduled trực tiếp ở trên,
// không đặt cả hai cách trên cùng method.
@Schedules({
    @Scheduled(cron = "0 0 9 * * MON-FRI",
               zone = "Asia/Ho_Chi_Minh"),
    @Scheduled(cron = "0 0 17 * * MON-FRI",
               zone = "Asia/Ho_Chi_Minh")
})
public void synchronizeCatalog() {
    // ...
}
~~~

Các fragment trên yêu cầu import `Scheduled`, `Schedules` và một bean được Spring quản lý; **không phải project hoàn chỉnh**. Spring 6.x cron truyền thống dùng **sáu trường**: giây, phút, giờ, ngày trong tháng, tháng, ngày trong tuần. Thuộc tính `zone` giúp tránh suy đoán múi giờ của máy chủ.

### Hai annotation không “gộp” thành một annotation

Ví dụ cùng method có hai khai báo:

~~~java
@Scheduled(fixedRate = 10_000)
@Scheduled(fixedDelay = 30_000)
public void refreshCache() {
    // ...
}
~~~

Nó **không** có nghĩa “chạy fixedRate 10 giây nhưng phải nghỉ fixedDelay 30 giây giữa mọi lần gọi”. Spring tạo **một lịch fixedRate** và **một lịch fixedDelay**, mỗi lịch tự điều phối. Method `refreshCache()` có thể được gọi từ lịch fixedRate trong khi lần gọi do lịch fixedDelay vẫn đang thực thi.

Tương tự, hai `@Scheduled(cron=...)` trùng thời điểm có thể khiến **cùng logic nghiệp vụ được kích hoạt hai lần**. Khi scheduler chỉ có một worker, hai callback có thể diễn ra **ngay sau nhau** thay vì chồng lấn; vẫn có **hai lần gọi**, không phải một.

## 3. Spring đăng ký lịch như thế nào?

### 3.1. Bật annotation-driven scheduling

Cấu hình cơ bản:

~~~java
// Fragment; cần imports @Configuration, @EnableScheduling.
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Bean TaskScheduler và bean công việc khác.
}
~~~

`@EnableScheduling` đăng ký hạ tầng để phát hiện `@Scheduled` trên những bean trong Spring ApplicationContext. Thành phần quan trọng là `ScheduledAnnotationBeanPostProcessor`; nó xem các khai báo scheduling trên method, đăng ký từng tác vụ với `ScheduledTaskRegistrar` và một `TaskScheduler` phù hợp.

Sơ đồ với một method có hai annotation:

~~~text
@Configuration + @EnableScheduling
              |
              v
ScheduledAnnotationBeanPostProcessor
              |
              v
Một bean có method process()
              |
       +------+------+
       |             |
 @Scheduled(A)  @Scheduled(B)
       |             |
       v             v
  Đăng ký lịch A  Đăng ký lịch B
       |             |
       +------+------+ 
              |
              v
   TaskScheduler / scheduler threads
              |
         +----+----+
         |         |
       gọi       gọi
     process()  process()
        \         /
         \       /
          cùng một bean/method
          có thể overlap
~~~

Mỗi khai báo có thể dùng `fixedRate`, `fixedDelay`, `cron` và các thuộc tính riêng. **Không có lock mặc định trên method chỉ vì hai trigger cùng tham chiếu đến nó.**

### 3.2. Không cần hai Java method để có hai lịch

Khai báo lặp là hợp lý khi **hai mốc thời gian khác nhau cần chạy chính xác cùng một nghiệp vụ**. Nhưng nếu mỗi lịch cần log, retry, phân quyền, tham số hoặc SLA riêng, gộp chúng vào một method có thể khiến việc quan sát rất khó.

Bởi `@Scheduled` không truyền “schedule ID” như một tham số thông thường của method, bản thân method `process()` **không tự biết annotation nào đã kích hoạt lời gọi**. Nếu cần hai hành vi khác nhau, cân nhắc hai method mỏng gọi chung một service nghiệp vụ:

~~~java
// Fragment trong một Spring-managed bean.
@Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
public void morningSync() {
    syncService.synchronize("morning");
}

@Scheduled(cron = "0 0 17 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
public void eveningSync() {
    syncService.synchronize("evening");
}
~~~

Ở đây `syncService` là dependency đã được inject, không có định nghĩa trong fragment. Hai method lịch vẫn gọi một service có thể dùng chung code; nhưng mỗi đường đi có tên rõ ràng, dễ gắn metric và chính sách riêng.

## 4. Hai lịch độc lập có thể chồng lấn

### 4.1. Một trigger không nhất thiết overlap với chính nó

Theo `ScheduledThreadPoolExecutor` của JDK, nhiều lần thực thi của **cùng một periodic task được đăng ký một lần** qua `scheduleAtFixedRate` hay `scheduleWithFixedDelay` không chạy chồng lấn với nhau. Một lần chạy quá lâu có thể làm lần sau trễ.

Nhưng **hai `@Scheduled` trên một method tạo hai lịch riêng**. Ràng buộc “không overlap với chính nó” của một series không trở thành **mutex chung** cho hai series. Khi hai trigger cùng tới hạn và scheduler có nhiều worker, chúng có thể chạy đồng thời trên cùng bean.

Ví dụ:

~~~text
Một method runJob(), có HAI lịch:

Lịch A: @Scheduled(fixedRate = 5000)
          |---- runJob A1 --------|
                     |---- runJob A2 -------|  (A1 và A2 không nhất thiết overlap)

Lịch B: @Scheduled(cron = "*/5 * * * * *")
          |---- runJob B1 ----|
                     |---- runJob B2 ----|

Tổng trên cùng method:
          A1 và B1 có thể cùng chạy
          A2 và B2 có thể cùng chạy
          nếu TaskScheduler có worker và lịch cho phép
~~~

Sơ đồ mang tính khái niệm, không phải phép đo timestamp thực tế. Điểm cần giữ là **độc lập giữa A và B**, không phải khẳng định chúng chắc chắn sẽ start đồng thời trên mọi cấu hình.

### 4.2. Pool size ảnh hưởng biểu hiện, không đổi số đăng ký

`ThreadPoolTaskScheduler` truyền thống của Spring bao một `ScheduledThreadPoolExecutor` và mặc định **một scheduler thread**. Nếu không định nghĩa scheduler bean phù hợp, `@EnableScheduling` cũng có thể dùng một scheduler cục bộ đơn luồng.

| Cấu hình thực thi | Khi hai trigger cùng tới hạn |
| --- | --- |
| Một scheduler thread | Thường chạy lần lượt; có thể sát nhau, vẫn là **hai lần gọi** |
| Hai hay nhiều scheduler threads | Có thể chạy đồng thời nếu cả hai đã tới hạn |
| Callback gửi task sang `@Async`/pool khác rồi return | Work thực có thể overlap cả khi callback được gọi nối tiếp |
| Nhiều ứng dụng/replicas | Mỗi instance có thể tự đăng ký các trigger; số lần chạy toàn hệ thống có thể tăng |

**Không thể dùng `setPoolSize(1)` làm biện pháp chống xử lý trùng bền vững**: nó chỉ làm tuần tự các callback trên đúng scheduler pool đó, không đồng nghĩa “một lần xử lý cho mỗi business period”, và không bảo vệ giữa nhiều JVM.

### 4.3. FixedDelay vẫn là per-registration

Giả sử:

~~~java
@Scheduled(fixedDelay = 60_000)
@Scheduled(fixedDelay = 60_000)
public void batch() {
    // Có hai chuỗi fixedDelay độc lập, mỗi chuỗi 60 giây.
}
~~~

Nếu **một lịch** chạy `batch()` 30 giây, lần tiếp theo của **lịch đó** chờ thêm 60 giây tính từ khi lần trước kết thúc. Nhưng **lịch còn lại** không tự biết phải đợi lịch thứ nhất. Nó có thể kích hoạt trong khoảng 30 giây đó khi có worker rảnh. Vì vậy câu “fixedDelay chống overlap” chỉ có ý nghĩa tương đối với **một series**, không phải bảo đảm toàn method hay toàn hệ thống.

## 5. Ví dụ JDK 17+ — một Runnable được đăng ký thành hai lịch

Trước khi xem Spring, hãy nhìn một phép tương tự ở JDK: cùng một đối tượng `Runnable` được đăng ký **hai lần riêng biệt** qua `ScheduledExecutorService.scheduleWithFixedDelay`. Mỗi lần trả một `ScheduledFuture` khác nhau.

Để kiểm tra overlap không phụ thuộc `Thread.sleep`, dùng hai worker, hai latch:

- `started` đếm đến khi **hai callback đã bắt đầu**.
- `release` buộc cả hai callback dừng ở cùng điểm chờ, để có thể đo `peakConcurrent`.
- Chu kỳ dài một ngày bảo đảm bài demo chỉ quan sát lần đầu, tránh lượt chạy tiếp theo gây nhiễu.

Lưu thành `TwoScheduledHandlesDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class TwoScheduledHandlesDemo {

    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(2);

        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        Runnable sameRunnable = () -> {
            int now = active.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            started.countDown();

            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
        };

        ScheduledFuture<?> first =
                scheduler.scheduleWithFixedDelay(
                        sameRunnable, 0, 1, TimeUnit.DAYS);

        ScheduledFuture<?> second =
                scheduler.scheduleWithFixedDelay(
                        sameRunnable, 0, 1, TimeUnit.DAYS);

        try {
            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Both schedules did not start");
            }

            System.out.println("distinctHandles=" + (first != second));
            System.out.println("peakConcurrent=" + peak.get());
        } finally {
            release.countDown();
            first.cancel(false);
            second.cancel(false);
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        }
    }
}
~~~

Chạy bằng JDK 17+:

~~~bash
javac TwoScheduledHandlesDemo.java
java TwoScheduledHandlesDemo
~~~

**Stdout khi cả hai callback đã bắt đầu:**

~~~text
distinctHandles=true
peakConcurrent=2
~~~

**Giải thích kết quả:** `first` và `second` là hai `ScheduledFuture` của **hai lời gọi schedule** khác nhau. Callback chung `sameRunnable` được chạy trên hai workers; vì cả hai bị giữ tại `release.await()`, khi `started` về 0 thì **cùng có hai lần gọi đang hoạt động**, khiến `peakConcurrent` bằng 2.

Thí nghiệm này **không phải Spring**; nó minh họa cách hai đăng ký độc lập có thể dẫn tới concurrency trên cùng callback. `@Scheduled` lặp lại của Spring tạo ra một tình huống tương tự ở tầng annotation.

## 6. Project Spring Framework 6.1.3 hoàn chỉnh: hai @Scheduled trên một method

Dự án sau chạy với Spring Framework thuần (**không cần Spring Boot**) và Java 17+. Nó tự tạo scheduler **hai threads**, bật `@EnableScheduling` và khai báo **hai annotation `@Scheduled` giống nhau trên cùng method**. Mỗi annotation chạy lần đầu ngay sau khi được đăng ký; vì chu kỳ `fixedDelay` dài một ngày nên không có lượt lặp tiếp làm nhiễu bài test.

Cấu trúc:

~~~text
repeatable-scheduled-demo/
├── pom.xml
└── src/main/java/demo/
    ├── ScheduleConfig.java
    ├── RepeatedJob.java
    └── RepeatableScheduledDemo.java
~~~

### 6.1. Maven pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>repeatable-scheduled-demo</artifactId>
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
                    <mainClass>demo.RepeatableScheduledDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Dependency `spring-context` cung cấp các annotation scheduler, bean context và `ThreadPoolTaskScheduler` cần thiết. Maven plugin chỉ phục vụ biên dịch và chạy `main`.

### 6.2. ScheduleConfig.java

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class ScheduleConfig {

    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("repeatable-");
        return scheduler;
    }

    @Bean
    public RepeatedJob repeatedJob() {
        return new RepeatedJob();
    }
}
~~~

`@EnableScheduling` bật việc dò `@Scheduled` của bean `RepeatedJob`. Scheduler được đặt tên `taskScheduler` để Spring tìm theo quy ước, và `setPoolSize(2)` cho phép hai callback thực thi đồng thời. Spring container quản lý vòng đời scheduler.

### 6.3. RepeatedJob.java

~~~java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;

public final class RepeatedJob {

    private final CountDownLatch entered = new CountDownLatch(2);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch exited = new CountDownLatch(2);

    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger peakConcurrent = new AtomicInteger();

    @Scheduled(initialDelay = 0L, fixedDelay = 86_400_000L)
    @Scheduled(initialDelay = 0L, fixedDelay = 86_400_000L)
    public void tick() {
        int now = active.incrementAndGet();
        peakConcurrent.accumulateAndGet(now, Math::max);
        entered.countDown();

        try {
            release.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            active.decrementAndGet();
            exited.countDown();
        }
    }

    public boolean awaitBothEntered(long timeout, TimeUnit unit)
            throws InterruptedException {
        return entered.await(timeout, unit);
    }

    public int peakConcurrent() {
        return peakConcurrent.get();
    }

    public void releaseBoth() {
        release.countDown();
    }

    public boolean awaitBothExited(long timeout, TimeUnit unit)
            throws InterruptedException {
        return exited.await(timeout, unit);
    }
}
~~~

**Tại sao đặt hai annotation giống nhau?** Để tách hoàn toàn yếu tố “khác cron expression” và chỉ minh họa tính **độc lập về registration**. Khi deploy thật, việc vô ý cấu hình hai lịch giống hệt nhau sẽ dễ tạo duplicate operations, nên đây là **một bài test**, không phải mẫu cấu hình business nên sao chép.

**Tại sao có thể cùng chạy trên một bean?** Cả hai đăng ký đều gọi `tick()` trên **cùng instance `RepeatedJob`**. `AtomicInteger` và `CountDownLatch` bảo đảm dữ liệu kiểm tra an toàn khi hai scheduler threads cùng truy cập. Không dùng biến `int` thường rồi tăng giảm từ hai threads để kiểm chứng concurrency.

### 6.4. RepeatableScheduledDemo.java

~~~java
package demo;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

public final class RepeatableScheduledDemo {

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             ScheduleConfig.class)) {

            RepeatedJob job = context.getBean(RepeatedJob.class);

            Method method = RepeatedJob.class.getMethod("tick");
            int declarationCount =
                    method.getAnnotationsByType(Scheduled.class).length;

            try {
                if (!job.awaitBothEntered(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Two registered schedules did not start");
                }

                System.out.println("declarations=" + declarationCount);
                System.out.println("bothEntered=true");
                System.out.println("peakConcurrent=" + job.peakConcurrent());
            } finally {
                job.releaseBoth();
            }

            if (!job.awaitBothExited(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Scheduled callbacks did not exit");
            }
        }
    }
}
~~~

Chạy trong thư mục project:

~~~bash
mvn -q compile exec:java
~~~

**Đầu ra do chương trình Java in khi hai scheduler callbacks chạy bình thường:**

~~~text
declarations=2
bothEntered=true
peakConcurrent=2
~~~

### 6.5. Giải thích tính xác định của thí nghiệm

1. Java reflection `getAnnotationsByType(Scheduled.class)` đọc hai khai báo lặp lại (được Java hỗ trợ qua container `@Schedules`), vì vậy `declarations=2`.
2. `@EnableScheduling` xử lý chúng **thành hai lịch riêng**, cả hai đến hạn ngay (`initialDelay=0`).
3. `ThreadPoolTaskScheduler` có **hai scheduler threads**, đủ để cả hai lần gọi `tick()` bắt đầu.
4. Mỗi lần gọi `tick()` tăng `active` rồi chờ `release`. Khi `entered` đếm về 0, hai lần gọi **đều chưa thoát khỏi method**, nên `peakConcurrent=2`.
5. Code `main` chỉ giải phóng latch **sau khi** đọc số liệu. Không có race giữa “worker chạy xong quá nhanh” và phép đo của main.
6. `fixedDelay=86_400_000L` là khoảng 24 giờ sau lần hoàn tất mỗi lịch; demo kết thúc trong vài giây nên không có lần thứ hai của từng lịch chen vào.
7. `releaseBoth()` nằm trong `finally`. Khi đóng `AnnotationConfigApplicationContext`, container dọn scheduler bean; bài test không cần chờ 24 giờ.
8. Nếu môi trường nghẽn hoặc scheduler không nhận job trong 5 giây, demo ném exception thay vì in một kết quả thành công giả. Đây là kiểm tra readiness, không phải bảo đảm thời gian thực.

**Không suy luận:** Vì example có `peakConcurrent=2` nên production với `poolSize=1` chắc chắn cũng overlap. Khi chỉ có một scheduler thread, nhiều callback có thể chạy **nối tiếp**; nhưng hai lịch **vẫn được đăng ký độc lập** và vẫn có thể gây duplicate processing.

## 7. FixedRate, FixedDelay, Cron: hiểu theo từng lịch

### 7.1. Với hai @Scheduled(fixedRate)

~~~java
// Fragment trên một method:
@Scheduled(fixedRate = 5_000)
@Scheduled(fixedRate = 7_000)
public void heartbeat() {
    // ...
}
~~~

Lịch A muốn lặp với period khoảng 5 giây; lịch B muốn lặp với period khoảng 7 giây. Tại những mốc gần nhau, cả hai có thể gọi method. **Cùng một periodic series** thường không chồng lấn chính nó theo cơ chế `ScheduledThreadPoolExecutor`, nhưng **A và B là hai series**.

Nếu method chậm 8 giây, một lần của A có thể đang chạy trong khi lần đầu hoặc lần tiếp của B đến hạn. Không có nghĩa executor chắc chắn sẽ tạo thread mới tùy ý; concurrency còn bị giới hạn bởi số scheduler workers.

### 7.2. Với hai @Scheduled(fixedDelay)

~~~java
@Scheduled(fixedDelay = 10_000)
@Scheduled(fixedDelay = 30_000)
public void cleanup() {
    // ...
}
~~~

Lịch A đợi 10 giây sau khi **lần chạy A trước kết thúc**; lịch B đợi 30 giây sau khi **lần chạy B trước kết thúc**. Không có “lần chạy cuối cùng của toàn method” dùng chung cho hai delay. Chỉ thay từ `fixedRate` sang `fixedDelay` không cung cấp lock toàn method.

### 7.3. Với hai @Scheduled(cron)

~~~java
@Scheduled(cron = "0 */15 * * * *", zone = "Asia/Ho_Chi_Minh")
@Scheduled(cron = "0 0 * * * *", zone = "Asia/Ho_Chi_Minh")
public void refreshIndexes() {
    // ...
}
~~~

Lịch A nhắm các mốc mỗi 15 phút ở giây 0; lịch B nhắm đầu mỗi giờ. **Tại phút 00 của mỗi giờ, hai lịch cùng nhắm một thời điểm.** Đây là ví dụ điển hình cho việc hai cron nhìn có vẻ bổ sung cho nhau nhưng lại có **vùng giao**.

Đối với job có side effects, phải quyết định liệu muốn **hai lần xử lý** tại giờ tròn hay chỉ một lần. Nếu chỉ muốn một, hãy thiết kế một cron thống nhất hoặc policy deduplication chứ không giả định Spring sẽ tự hợp nhất fire times.

### 7.4. Mỗi @Scheduled có một loại trigger chính

Trong **một** `@Scheduled` cho scheduling lặp, chọn **một trong** `cron`, `fixedRate` hoặc `fixedDelay`; `initialDelay` có thể bổ sung cho fixed-rate/fixed-delay. Muốn hai loại lịch, viết **hai annotation độc lập** thay vì đặt nhiều thuộc tính trigger cạnh tranh trong một annotation.

Với Spring 6.1, `@Scheduled(initialDelay = ...)` cũng có thể dùng như **một tác vụ một lần** theo hợp đồng phiên bản; đừng áp máy móc kết luận từ các phiên bản Spring cũ. Dù là một lần hay lặp lại, nhiều khai báo khác nhau vẫn là nhiều registrations.

## 8. Rủi ro lớn nhất: cùng bean, cùng method, cùng dữ liệu mutable

Với Spring, một bean service thường là singleton. Nhiều trigger không tạo ra bản sao method hoặc bean riêng: chúng có thể gọi **cùng instance**, đồng thời đọc/ghi **cùng fields**.

Phản ví dụ:

~~~java
// Fragment KHÔNG an toàn khi các lịch overlap.
private int numberOfRuns = 0;

@Scheduled(fixedRate = 5_000)
@Scheduled(fixedRate = 5_000)
public void run() {
    numberOfRuns++;  // data race / lost update khi đồng thời
}
~~~

`numberOfRuns++` là thao tác đọc–tăng–ghi, không atomic; nhiều threads có thể cập nhật chồng nhau. Dùng `AtomicInteger` để làm bộ đếm sẽ sửa **data race của counter**, nhưng **không** giải quyết việc một invoice/job được xử lý hai lần.

### 8.1. Cùng một nghiệp vụ có thể chạy hai lần

Nếu method vừa quét invoice đang chờ vừa gửi email:

~~~java
// Phản ví dụ thiết kế: không có cơ chế claim/idempotency.
@Scheduled(cron = "0 */15 * * * *")
@Scheduled(cron = "0 0 * * * *")
public void sendPendingInvoices() {
    invoiceService.findPending()
            .forEach(invoiceService::send);
}
~~~

Đây là fragment cần `invoiceService`. Vào đầu giờ, hai lần gọi có thể đồng thời cùng nhìn thấy một invoice “PENDING” trước khi bên kia cập nhật trạng thái. Cả hai có thể gửi mail hoặc side effect trùng.

Giải pháp không đơn giản là “đặt `synchronized` lên method”:

- `synchronized` trên đúng cùng bean instance có thể **tuần tự hóa** lời gọi vào method đó trong **một JVM**.
- Nhưng nó **không hợp nhất** hai lịch thành một, nên vẫn có **hai lần xử lý nối tiếp**.
- Nó không bảo vệ nhiều replicas/JVMs, và có thể làm lịch bị trễ khi task dài.
- Nó không tạo idempotency hay transactional exactly-once ở bên nhận.

### 8.2. Nếu chỉ cần ngăn overlap trong một JVM

Có thể dùng `ReentrantLock.tryLock()` hoặc `AtomicBoolean.compareAndSet(...)` để **bỏ qua một lần gọi khi một lần khác đang chạy**:

~~~java
// Fragment trong một singleton job bean.
private final java.util.concurrent.atomic.AtomicBoolean running =
        new java.util.concurrent.atomic.AtomicBoolean(false);

@Scheduled(fixedRate = 5_000)
@Scheduled(fixedRate = 5_000)
public void guardAgainstOverlap() {
    if (!running.compareAndSet(false, true)) {
        return; // chủ động skip lần gọi overlap
    }
    try {
        performBusinessWork();
    } finally {
        running.set(false);
    }
}
~~~

Đây chỉ là **fragment**; `performBusinessWork()` cần định nghĩa. Nó ngăn hai execution **đồng thời trong cùng instance**. **Không** ngăn hai lượt chạy nối tiếp, không phối hợp giữa nhiều replicas, và không bảo vệ nếu mọi thứ đã gửi việc sang một `@Async` service rồi callback kết thúc trước việc thật.

Nếu “skip” gây mất kỳ xử lý, không dùng cách này. Thay thế bằng queue, claim/batch cursor hoặc lock có cơ chế bảo đảm mỗi kỳ được xử lý theo yêu cầu nghiệp vụ.

### 8.3. Idempotency và claim công việc ở tầng dữ liệu

Đối với nghiệp vụ có side effect:

1. Gán **business key** cho kỳ xử lý, ví dụ `(tenantId, reportDate, jobType)`.
2. Thiết kế thao tác **claim** atomic (unique constraint, compare-and-set trạng thái, transaction hoặc một lock có phân phối phù hợp).
3. Nếu đã được claim/xử lý thì **bỏ qua hoặc trả trạng thái hiện có** thay vì nhân side effect.
4. Nếu job thất bại giữa chừng, có lease/retry policy và reconciliation rõ ràng.
5. Với gọi hệ thống bên ngoài, áp dụng idempotency key hoặc transactional outbox khi cần.

Idempotency **không phải** điều Spring `@Scheduled` tự cung cấp. Kể cả ứng dụng chỉ có **một** annotation, restart/multiple replicas/retries vẫn có thể làm công việc chạy lại; với nhiều annotation, cơ hội trùng càng dễ xuất hiện.

## 9. Hai cron trùng nhau không phải hai “khung giờ dự phòng”

Đôi khi một developer dùng hai lịch giống nhau để “dự phòng nếu một lịch không chạy”:

~~~java
@Scheduled(cron = "0 0 * * * *")
@Scheduled(cron = "0 0 * * * *")
public void hourlyImport() {
    // ...
}
~~~

Điều này thường không tạo redundancy đáng tin, mà tạo **hai lần đăng ký** trên một process. Nếu scheduler có vấn đề hệ thống (thread starvation, shutdown, process chết), cả hai đăng ký có thể chịu chung lỗi. Nếu cả hai hoạt động, kết quả có thể là import trùng.

Dự phòng hoặc high availability cho scheduled jobs nên dựa vào **durable job state, idempotency, retry có kiểm soát và điều phối cluster**, thay vì nhân đôi một annotation.

Mặt khác, **có những trường hợp cố ý hợp lý**: cùng logic phải chạy 09:00 và 17:00, hoặc một lần cập nhật nhanh và một lần đối soát toàn bộ. Nhưng khi logic, dữ liệu đầu vào và khả năng xử lý trùng khác nhau, tách các entry point là thiết kế dễ kiểm soát hơn.

## 10. Phân biệt nguồn lặp để tránh chẩn đoán sai

| Bằng chứng | Nguồn thêm execution | Phép kiểm tra |
| --- | --- | --- |
| `getAnnotationsByType(Scheduled.class)` trả 2 | Hai annotations trên **một method** | Đếm repeatable declarations và trigger definitions |
| Hai bean objects cùng class | Hai registrations theo **bean instance** | Đối chiếu bean names, object identity và component scan/@Bean |
| Root/child contexts hoặc nhiều pod | Nhiều context/process tự đăng ký | Ghi context ID, pod ID và logical job key |
| `@Scheduled` và `TaskScheduler.schedule(...)` cùng tồn tại | Hai đường đăng ký | Xác định owner duy nhất của schedule |
| Callback nộp thêm `@Async` work | Execution có thể còn in-flight sau tick | Theo dõi async completion/queue, không chỉ log callback |

Bài này tập trung **repeatable annotation**: nhiều khai báo trên cùng method tạo nhiều trigger trong cùng bean. Không thể kết luận đang có repeatable annotations chỉ từ hai dòng log giống nhau; phân biệt rõ bean, context, scheduler và work execution khi điều tra.


## 11. Chọn TaskScheduler rõ ràng

### 11.1. Khi @Scheduled dùng scheduler nào?

Theo Javadoc `@EnableScheduling`, Spring tìm `TaskScheduler` bean phù hợp: một bean duy nhất, hoặc bean có tên mặc định `taskScheduler`; Spring cũng có đường tìm `ScheduledExecutorService`. Nếu không tìm thấy scheduler phù hợp, hạ tầng có thể tạo một scheduler local một thread.

Trong Spring Framework **6.1+**, mỗi `@Scheduled` còn có thuộc tính `scheduler` (qualifier hoặc tên bean scheduler) để lựa chọn executor cho lịch đó khi cần:

~~~java
// Fragment Spring 6.1+, cần hai scheduler beans thật sự tồn tại.
@Scheduled(fixedRate = 15_000, scheduler = "fastScheduler")
@Scheduled(cron = "0 0 2 * * *",
           zone = "Asia/Ho_Chi_Minh",
           scheduler = "dailyScheduler")
public void runFromTwoSchedulers() {
    // ...
}
~~~

Ngay cả khi **mỗi annotation dùng một scheduler khác nhau**, chúng vẫn gọi cùng method và có thể đồng thời thực thi. Hai scheduler beans chỉ tách tài nguyên lập lịch; chúng không tự thiết lập mutual exclusion hay chống trùng dữ liệu.

Nếu project dùng Spring phiên bản trước 6.1, kiểm tra sự tồn tại của thuộc tính `scheduler` trước khi sao chép snippet; có thể thay bằng `SchedulingConfigurer` hoặc cấu hình scheduler theo application context.

### 11.2. Pool mặc định không bảo đảm hành vi concurrency cố định

`ThreadPoolTaskScheduler` truyền thống mặc định `poolSize=1`. Với một worker, nếu hai lịch cùng tới hạn, callbacks thường được thực thi lần lượt. Khi tăng pool size hoặc khi một phần công việc gửi sang executor khác, callback có thể overlap.

Đặt `poolSize=2` trong ví dụ ở phần 6 **là điều kiện để test overlap có kiểm soát**, không phải khuyến nghị “mọi production scheduler nên dùng hai worker”. Sizing phụ thuộc thời gian task, số lịch, SLA, blocking I/O và downstream capacity. Một pool quá nhỏ làm lịch trễ; pool quá lớn có thể tạo nhiều side effects song song hoặc gây nghẽn database.

## 12. @Async, completion và ranh giới “task đã xong”

Một method chỉ có `@Scheduled` (callback đồng bộ) thường chạy trên scheduler thread. Nếu body của method gửi việc nặng sang một executor khác rồi trở về, thì **độ dài callback** không đồng nghĩa **độ dài công việc thật**:

~~~java
// Fragment: reportService là một bean Spring khác, có method async.
@Scheduled(fixedDelay = 5_000)
@Scheduled(cron = "0 */15 * * * *")
public void triggerReport() {
    reportService.generateAsync();
}
~~~

Khi `generateAsync()` trả Future nhanh, scheduler có thể coi một lần callback đã kết thúc, mặc dù báo cáo vẫn chạy. Vì vậy hai lịch có thể gây **công việc thực song song** ngay cả khi callbacks bị tuần tự trên một scheduler thread.

Nếu yêu cầu nghiệp vụ “đợt sau chỉ bắt đầu khi đợt trước hoàn tất”, cần áp chính sách concurrency **ở nơi công việc thật được xử lý**, dùng Future/callback/job state/queue hoặc lock có scope đúng. Đừng chỉ áp `synchronized` lên đoạn method nộp task rồi nghĩ đã bảo vệ toàn bộ công việc nền.

Lưu ý riêng: Spring Framework 6.1 hỗ trợ một số kiểu trả về reactive của `@Scheduled` với cách subscribe theo lịch, khác callback `void` truyền thống. Đừng khẳng định `@Scheduled` của mọi loại method đều được tính kết thúc theo cùng một cách; ví dụ bài này dùng method `void` đồng bộ để tập trung vào repeatable schedules.

## 13. Error handling: một lịch lỗi có làm lịch khác dừng không?

Với JDK `ScheduledThreadPoolExecutor.scheduleAtFixedRate`/`scheduleWithFixedDelay`, một exception **không được xử lý** trong callback có thể khiến **chuỗi lịch định kỳ cụ thể** không được chạy tiếp. Nhưng Spring `ThreadPoolTaskScheduler` bọc nhiều scheduled callbacks bằng `ErrorHandler`: mặc định các repeating tasks thường được **log và suppress** exception để lịch có thể tiếp tục.

Với hai annotation độc lập, không nên giả định một lịch thất bại là cơ chế ngắt lịch còn lại; cần phân tích **policy của từng registration và wrapper**. Đừng dùng exception như “cách phá deadlock” hoặc “cách dừng toàn bộ method”.

~~~java
// Fragment trong @Configuration trước khi bean initialize.
@Bean("taskScheduler")
public ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler pool = new ThreadPoolTaskScheduler();
    pool.setPoolSize(2);
    pool.setErrorHandler(error -> {
        // Ghi metric/log/alert theo chính sách ứng dụng.
        logScheduledFailure(error);
    });
    return pool;
}
~~~

Đoạn này không độc lập: `logScheduledFailure(...)` là method ứng dụng. Handler không ném lại trong ví dụ, nên nó đang **suppress** exception sau khi ghi lỗi. Với production, xác định lỗi nào có thể retry, lỗi nào phải stop một lịch hoặc chuyển vào dead-letter workflow. **Không** tự động retry job có side effects nếu chưa có idempotency.

## 14. Repeatable annotations nhân số lịch ở mọi replica

Nếu **một** bean có hai `@Scheduled` declarations thì **mỗi** trong ba replicas có thể đăng ký hai lịch riêng, tức tối đa sáu nguồn trigger trong toàn cụm. Không nhầm số registrations với số callbacks thực chạy hoặc business effects được commit.

`ThreadPoolTaskScheduler.setPoolSize(1)` chỉ có thể tuần tự hóa callbacks trên một scheduler, không xóa lịch trùng hoặc đồng bộ giữa JVMs. Với nghiệp vụ chỉ xử lý một operation/period, dùng unique business key, claim/lease (kèm expiry/fencing khi phù hợp), retry và idempotency. Ngăn overlap local bằng lock khác với chống **thực hiện trùng cùng business period**.


## 15. Test thế nào để phát hiện lịch lặp và chồng lấn?

### 15.1. Kiểm tra khai báo tĩnh

Java reflection có thể kiểm tra annotation lặp lại:

~~~java
// Fragment với Class<?> chứa method tick().
int count = RepeatedJob.class
        .getMethod("tick")
        .getAnnotationsByType(Scheduled.class)
        .length;

System.out.println(count); // 2 trong project phần 6
~~~

`getAnnotationsByType` nhìn thấy hai `@Scheduled` qua container `@Schedules`. Nhưng số annotation trong source **không phải bằng chứng duy nhất** cho số registrations đang sống: một bean có thể được đăng ký nhiều instance, một method có thể được schedule thủ công, hoặc ứng dụng có nhiều contexts.

### 15.2. Test overlap bằng latches, không đoán timing

Bài demo ở phần 6 dùng `CountDownLatch(2)`, `AtomicInteger` và `setPoolSize(2)`. Khi cả hai callbacks bị giữ ở `release.await()`, main biết chắc cả hai đang active. Không cần `Thread.sleep(100)` rồi đoán scheduler đã kịp chạy chưa.

Các tình huống test nên có:

| Ca kiểm thử | Ý cần xác nhận |
| --- | --- |
| Chỉ một `@Scheduled` | Một registration theo cấu hình |
| Hai `@Scheduled` trên cùng method | Hai registrations độc lập |
| Hai trigger trùng giờ với poolSize=2 | Có thể overlap; dữ liệu shared cần thread-safe |
| Hai trigger trùng giờ với poolSize=1 | Callback có thể nối tiếp nhưng **không bị gộp** |
| Hai cron có giao nhau ở đầu giờ | Không gây side effect trùng khi business yêu cầu chỉ một |
| Hai replicas cùng tới hạn | Distributed lock/idempotency bảo vệ đúng scope |
| Một callback ném exception | Error handler và future/metric phản ánh chính sách |
| Callback nộp `@Async` job | Work thực được theo dõi, không đánh đồng callback completion |
| Context startup/shutdown | Không để job chạy sau khi cấu hình/connection bị đóng |
| Bean bị đăng ký hai instance | Phát hiện duplicate registration không do repeatable annotation |

### 15.3. Metrics/log tối thiểu

Nên đo:

- Số lần thực thi theo **job type**, **schedule key**, **JVM/pod**, **business period**.
- Scheduler lag: thời gian từ mốc dự kiến tới lúc callback bắt đầu.
- Execution duration và số active executions của cùng nghiệp vụ.
- Số lần bị **skip** do concurrency guard, số lần duplicate bị dedup ở tầng dữ liệu.
- Exception/retry/rejection và thời gian giữ tài nguyên downstream.
- Missed runs/late runs khi process hoặc scheduler bị nghẽn.

**Giới hạn quan sát:** Tên thread `repeatable-1`/`repeatable-2` cho biết worker được dùng, **không cho biết annotation A hay B** đã trigger. Nếu cần phân biệt từng lịch trong metric, tạo entry point rõ ràng hoặc thiết kế registration có identity tường minh.

## 16. Khi nào nên đặt hai @Scheduled trên cùng method?

**Có thể hợp lý** khi hai lịch chỉ khác trigger, còn toàn bộ logic, timeout, permission, idempotency và monitoring **thực sự giống nhau**; ví dụ đồng bộ một cache vào hai giờ cố định trong ngày.

**Nên tách entry point hoặc registration** khi:

1. Hai lịch có **business identity** khác nhau, ví dụ “đối soát buổi sáng” và “báo cáo cuối ngày”.
2. Có yêu cầu throughput, timeout, error policy hoặc retry riêng.
3. Muốn tách scheduler pool/zone/feature flag và quan sát rõ từng trigger.
4. Muốn chỉ chạy một lần cho mỗi business period dù các cron có giao nhau.
5. Logic có shared mutable state hoặc side effects cần transaction/lock với scope rõ ràng.
6. Có nhiều replicas và cần một job owner duy nhất.

Tách hai `@Scheduled` methods vẫn không tự giải quyết idempotency/distributed locking, nhưng làm **đường đi của từng lịch dễ quan sát và thiết kế** hơn.

## 17. Những hiểu nhầm thường gặp

1. **“Hai `@Scheduled` trên một method bị Spring gộp thành một lịch.”** Sai. Mỗi khai báo được xử lý độc lập.
2. **“Method chỉ viết một lần thì chỉ có một lần thực thi tại một thời điểm.”** Sai. Nhiều triggers có thể gọi cùng method đồng thời.
3. **“Dùng `fixedDelay` là không bao giờ overlap.”** Sai trên phạm vi **nhiều registrations**; fixedDelay chỉ điều phối các lần của **chính schedule đó**.
4. **“Hai cron có cùng fire time sẽ tự deduplicate.”** Sai. Có thể có hai invocations.
5. **“Pool một thread bảo đảm một job nghiệp vụ chỉ được xử lý một lần.”** Sai. Nó thường làm callback nối tiếp, không gộp duplicate triggers.
6. **“Mỗi annotation tạo một bean mới.”** Sai. Nhiều registrations có thể cùng tham chiếu tới một bean instance.
7. **“Dùng `AtomicInteger` sửa được mọi duplicate side effects.”** Sai. Nó chỉ sửa cập nhật bộ đếm, không bảo đảm business idempotency.
8. **“`synchronized` trên method ngăn việc chạy hai lần.”** Sai. Nó có thể tuần tự hóa cùng JVM, nhưng hai invocations vẫn xảy ra.
9. **“Lặp hai annotation giống nhau giúp tăng độ tin cậy.”** Không mặc nhiên; thường chỉ nhân số lần thực thi.
10. **“Chỉ cần `@Scheduled` là cron chạy một lần trên toàn cluster.”** Sai. Mỗi application context/instance có scheduling riêng.
11. **“Log trên scheduler thread cho biết chính xác lịch nào gọi method.”** Sai. Thread là tài nguyên thực thi, không phải schedule ID.
12. **“Một exception ở lịch A tự hủy lịch B.”** Không có bảo đảm như vậy; xử lý lỗi còn phụ thuộc Spring wrapper/error handler.
13. **“`@Async` làm mất rủi ro concurrency của repeatable schedules.”** Sai. Nó có thể nhân mức chạy đồng thời sang executor khác.
14. **“`@EnableScheduling` cũng tự bật mọi method `@Async`.”** Sai. `@EnableAsync` và `@EnableScheduling` quản lý hai hạ tầng annotation khác nhau.
15. **“Tách hai method nghĩa là đã chống duplicate trong database.”** Sai. Vẫn cần business key, claim/idempotency và transaction phù hợp.
16. **“Nếu một test chạy không overlap thì các lịch không độc lập.”** Sai. Pool, timing và scheduling contention có thể làm hai callback nối tiếp.

## 18. Câu hỏi tự kiểm tra

**Câu 1.** Hai `@Scheduled(cron=...)` trên cùng method được Spring hiểu thành mấy lịch?  
**Đáp án:** Hai lịch độc lập. Mỗi khai báo được xử lý với trigger riêng.

**Câu 2.** Một method có `@Scheduled(fixedDelay=5000)` hai lần. Khi lần A đang chạy, lịch B có thể gọi method không?  
**Đáp án:** Có, nếu lịch B đến hạn và có scheduler worker phù hợp. FixedDelay không tự tạo lock toàn method.

**Câu 3.** Có thể khai báo hai `@Scheduled` trực tiếp trên một method Java 17 mà không viết `@Schedules` tường minh không?  
**Đáp án:** Có. `@Scheduled` là repeatable annotation, dùng container `@Schedules` theo hợp đồng Java.

**Câu 4.** Một scheduler pool có một worker, hai lịch cùng tới hạn; có thể xảy ra gì?  
**Đáp án:** Các callbacks thường chạy nối tiếp trên worker đó, nhưng vẫn là hai lần gọi độc lập. Không có cơ chế dedup chỉ vì poolSize=1.

**Câu 5.** Một cron chạy mỗi 15 phút và cron khác chạy mỗi giờ trên cùng method. Điều gì xảy ra ở đầu giờ?  
**Đáp án:** Hai cron cùng nhắm một mốc. Nếu cả hai được đăng ký và hoạt động, method có thể được gọi hai lần, song song hoặc nối tiếp.

**Câu 6.** `@Scheduled` lặp lại có tạo bean instance mới cho mỗi khai báo không?  
**Đáp án:** Không. Các registrations có thể gọi cùng object bean, do đó fields mutable và side effects cần concurrency-safe design.

**Câu 7.** `ReentrantLock.tryLock()` trong singleton job giải quyết được gì và chưa giải quyết gì?  
**Đáp án:** Có thể tránh overlap trong cùng instance khi lock đang giữ. Nó không chống duplicate chạy nối tiếp, không xử lý across JVM/replicas và không tạo idempotency.

**Câu 8.** Vì sao test dùng `CountDownLatch` ở phần 6 đáng tin hơn đoán `Thread.sleep`?  
**Đáp án:** Hai callback bị giữ ở cùng điểm chờ, nên khi main quan sát hai lần đã vào method, chúng thực sự đang hoạt động đồng thời.

**Câu 9.** Hai `@Scheduled` mỗi replica và ba replicas có đảm bảo chỉ hai lượt gọi toàn hệ thống không?  
**Đáp án:** Không. Mỗi replica có thể đăng ký cả hai lịch; phải dùng cluster coordination và idempotency nếu yêu cầu chỉ một lần xử lý.

**Câu 10.** Khi nào nên tách hai entry point thay vì đặt repeatable annotations trên một method?  
**Đáp án:** Khi hai lịch cần business identity, cấu hình, metric, retry, permission hoặc concurrency policy riêng, hay cần tránh vùng giao của cron expressions.

## 19. Tài liệu tham khảo chính thức

- [Spring Framework 6.1.3 — Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): `@Repeatable(Schedules.class)`, nhiều khai báo được xử lý độc lập, có thể overlap, thuộc tính `scheduler` từ Spring 6.1.
- [Spring Framework 6.1.3 — EnableScheduling Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html): kích hoạt scheduling, chọn `TaskScheduler` và phạm vi ApplicationContext.
- [Spring Framework 6.1.3 — ScheduledAnnotationBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/annotation/ScheduledAnnotationBeanPostProcessor.html): cách Spring phát hiện/đăng ký scheduled methods.
- [Spring Framework 6.1.x — ThreadPoolTaskScheduler](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): backend `ScheduledThreadPoolExecutor`, mặc định một thread, `setPoolSize`, callback chạy trên scheduler worker.
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): repeatable `@Scheduled`, cron, thực thi và cảnh báo double initialization.
- [Java SE 17 — Repeatable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Repeatable.html): Java container annotation cho annotation lặp lại.
- [Java SE 17 — AnnotatedElement](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html): `getAnnotationsByType` với repeatable annotations.
- [Java SE 17 — ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html): hai periodic task registrations độc lập, pool worker và đặc tính non-overlap của từng periodic series.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-tasks-010">Nhiều thể hiện bean có thể đăng ký nhiều lịch</a>

<details>
<summary>Xem chi tiết</summary>

# Nhiều thể hiện bean có thể đăng ký nhiều lịch với @Scheduled

Trong Spring Framework, khi một phương thức có **@Scheduled**, điều được framework quản lý không chỉ là một đoạn mã trong class. Spring tìm phương thức được đánh dấu trên **những đối tượng bean mà container xử lý**, rồi đăng ký một hay nhiều scheduled tasks tương ứng. Vì thế, **hai instance của cùng một class có @Scheduled có thể tạo ra hai lịch gọi method**, dù source code chỉ chứa một phương thức và một annotation.

Đây là chủ đề **“Nhiều thể hiện bean có thể đăng ký nhiều lịch”** thuộc nhóm **@Scheduled** của module **Spring Framework - concurrency**. Bài học tập trung vào nguyên nhân tạo lịch trùng, cách phân biệt **instance, bean definition, annotation, application context và process/pod**, kiểm chứng bằng Java 17 cùng Spring Context, phân tích hệ quả concurrent execution và đưa ra cách kiểm soát số lượt chạy đúng theo yêu cầu nghiệp vụ.

Thông điệp chính:

- **Một class không đồng nghĩa một instance.** Hai bean riêng của cùng kiểu có thể được Spring quét và đăng ký hai bộ scheduled callbacks.
- **Một method không đồng nghĩa một lịch.** Một method có nhiều khai báo @Scheduled sẽ có nhiều trigger, ngay cả trên cùng một instance.
- **Một Spring application trên mỗi pod không đồng nghĩa một lần chạy trong toàn cluster.** Nhiều process cùng lập lịch thì mỗi process có thể phát lịch riêng.
- **Một lịch được đăng ký không đồng nghĩa công việc chạy đồng thời.** Thread pool size, lịch, độ dài callback và cách delegate vào executor mới quyết định mức song song.
- **@Scheduled không tự cung cấp khóa distributed, de-duplication, exactly-once hoặc durability.** Các bảo đảm nghiệp vụ phải được thiết kế riêng.

## 1. Phân biệt class, object, bean và scheduled registration

Giả sử có class:

~~~java
import org.springframework.scheduling.annotation.Scheduled;

public class CleanupJob {
    @Scheduled(fixedDelay = 30_000)
    public void cleanup() {
        // Công việc dọn dữ liệu.
    }
}
~~~

Đây là **đoạn minh họa trong ứng dụng Spring**, không phải file độc lập chạy bằng javac vì thiếu Spring dependencies.

Ta cần tách bốn thực thể:

| Khái niệm | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| **Class** | CleanupJob.class | Định nghĩa mã nguồn cho kiểu đối tượng |
| **Object instance** | new CleanupJob() | Một đối tượng cụ thể trong bộ nhớ |
| **Spring bean** | Bean được container tạo hoặc quản lý từ CleanupJob | Đối tượng đi qua lifecycle và BeanPostProcessor của Spring |
| **Scheduled registration** | Một scheduled Runnable và trigger gắn với method cleanup trên bean | Cơ chế Spring gọi lại method theo lịch |

Nếu ApplicationContext chứa **hai bean instances khác nhau của CleanupJob**, mỗi bean được xử lý scheduling bình thường, Spring có thể đăng ký **một nhiệm vụ định kỳ cho mỗi instance**.

~~~text
              CleanupJob.class
                    |
           +--------+--------+
           |                 |
           v                 v
       Bean A              Bean B
      instance A          instance B
           |                 |
           v                 v
    @Scheduled on A    @Scheduled on B
           |                 |
           v                 v
       Task A              Task B
           |                 |
           +--------+--------+
                    |
              TaskScheduler
~~~

**Một TaskScheduler chung hoàn toàn có thể quản lý cả Task A lẫn Task B.** Có hai đăng ký lịch **không có nghĩa** tồn tại hai ThreadPoolTaskScheduler hoặc hai thread. Ngược lại, cùng một scheduler thread cũng có thể chạy hai callbacks vào hai thời điểm khác nhau.

### Khi tạo bằng new có tự đăng ký lịch không?

Không. Một đối tượng được tạo bằng **new CleanupJob()** bên ngoài Spring container **không tự được phát hiện, post-process và lập lịch** chỉ vì class chứa @Scheduled. Điều quan trọng là object đó đi qua cơ chế quản lý/đăng ký tương ứng của Spring.

Ngoại lệ cần xem riêng là các cơ chế **@Configurable/AspectJ injection**, tích hợp container đặc thù hoặc logic đăng ký scheduler bằng tay; chúng không biến mọi lệnh new thành một bean @Scheduled mặc định.

## 2. Spring phát hiện @Scheduled như thế nào?

Trong ứng dụng dùng **@EnableScheduling**, Spring đăng ký hạ tầng xử lý annotation, chủ yếu thông qua **ScheduledAnnotationBeanPostProcessor** và cơ chế đăng ký/điều phối lịch.

Hình dung quy trình chuẩn:

1. Spring xử lý cấu hình ApplicationContext và tạo các bean.
2. Những bean đủ điều kiện được BeanPostProcessor kiểm tra sau khi khởi tạo.
3. Nếu bean có method mang **@Scheduled**, hạ tầng trích xuất metadata về fixedRate, fixedDelay, cron, initialDelay hoặc Trigger liên quan.
4. Spring tạo/đăng ký scheduled task gắn với **đối tượng bean được xử lý**.
5. Sau khi context được làm mới và scheduler sẵn sàng, các task được điều phối để chạy theo lịch.
6. Khi bean/context bị hủy theo lifecycle tương ứng, hạ tầng cố gắng hủy các scheduled registrations đã gắn với lifecycle đó.

Điều kiện **bean được quản lý và đi qua bước xử lý** rất quan trọng. Việc nhìn thấy annotation bằng reflection trên một class **không có nghĩa** một scheduler hiện đang có registration cho class ấy.

**@EnableScheduling** bật việc phát hiện @Scheduled **trong phạm vi ApplicationContext mà nó được khai báo**. Nếu ứng dụng có nhiều context, việc bật scheduling và các bean được khai báo ở từng context phải được xét riêng, không mặc định mọi context cùng dùng đúng một registry.

### Tại sao hai instance dẫn tới hai task?

Spring cần biết **đối tượng nào** sẽ nhận lời gọi khi một trigger đến hạn. Hai bean instances có thể giữ hai cấu hình, hai ID, hai tài nguyên riêng hoặc hai bộ dependency. Vì vậy hạ tầng không thể an toàn gộp hai registrations chỉ bởi chúng cùng class/method name.

Ví dụ:

~~~text
bean "cleanupEu" = new CleanupJob(region="EU")
bean "cleanupUs" = new CleanupJob(region="US")

Cùng class: CleanupJob
Cùng method: cleanup()
Cùng annotation: @Scheduled(...)
Khác object: cleanupEu != cleanupUs

=> Hai scheduled callbacks có thể được đăng ký
=> Mỗi callback gọi đúng instance của nó
~~~

Đây có thể là **thiết kế đúng** nếu chủ đích chạy cleanup cho hai vùng độc lập. Nhưng nếu cả hai bean cùng dọn một bảng global và không có cơ chế phối hợp, đó có thể là **lỗi tạo lịch trùng**.

## 3. Ba chiều làm tăng số lịch chạy

Đừng vội kết luận “một method đã bị Spring chạy hai lần” mà chưa phân biệt nguyên nhân.

### Chiều 1 — số bean instances

Có hai bean instances của một class, mỗi instance có một @Scheduled registration đủ điều kiện:

~~~text
Bean instance A --> registration A
Bean instance B --> registration B
~~~

### Chiều 2 — số scheduled declarations trên một method/bean

Một method có thể được annotate bằng **@Scheduled lặp lại**, tức là nhiều trigger độc lập:

~~~java
@Scheduled(cron = "0 0 * * * *", zone = "UTC")
@Scheduled(cron = "0 30 * * * *", zone = "UTC")
public void refresh() {
    // Cùng method nhưng hai lịch: phút 00 và phút 30.
}
~~~

Đây là **method minh họa**, cần được khai báo trong bean Spring. Hai lịch được đăng ký **ngay cả khi chỉ có một bean instance**. Nếu các mốc trùng nhau trong cấu hình khác, các callbacks có thể chạy sát nhau hoặc overlap, tùy scheduler.

### Chiều 3 — số ApplicationContexts/processes/pods

Nếu ứng dụng có ba pods đang chạy cùng code @Scheduled, thông thường mỗi pod có ApplicationContext/scheduler riêng:

~~~text
pod-1 --> bean instance --> schedule
pod-2 --> bean instance --> schedule
pod-3 --> bean instance --> schedule
~~~

Đây là **ba bản sao ở các process khác nhau**, không phải “ba instance trong cùng một Spring container”. Ở tầng nghiệp vụ toàn cluster, kết quả vẫn có thể là **ba lượt xử lý** cùng mốc thời gian.

### Một công thức định hướng, không phải luật nhân luôn đúng

Nếu có *B* bean instances đủ điều kiện và mỗi bean có *R* declarations hoạt động trong một context, số registrations lý tưởng có thể là **B × R**. Với *P* processes có cấu hình giống nhau, số nơi kích hoạt có thể tiến tới **P × B × R**.

Nhưng đây chỉ là **cách đếm hỗ trợ chẩn đoán**, không phải API contract tính số lần thực thi. Thực tế còn phụ thuộc bean có được tạo hay chưa, annotation có bị vô hiệu hóa bởi cấu hình không, method hợp lệ không, application context nào bật scheduling, thời điểm khởi động/tắt, exception, scheduler saturation và cron activation.

**Registrations khác execution count:** một registration fixedRate có thể chạy nhiều lần; một registration cron không còn thuộc phạm vi hoạt động sẽ không chạy; một callback bị delay chưa chắc xuất hiện đúng mốc.

## 4. Ví dụ Java 17 + Spring thực thi: cùng class, hai bean, hai lịch

Ví dụ này tạo **chủ đích hai instances** của cùng một class **ReportTicker**, mỗi instance được đăng ký qua một phương thức @Bean khác nhau. Trong ReportTicker chỉ có **một phương thức @Scheduled**. Vì mỗi bean đi qua Spring scheduling post-processor, hệ thống sẽ kích hoạt cả hai instances.

Chương trình sử dụng **CountDownLatch**, **ConcurrentSkipListSet**, **AtomicInteger** và scheduler có thread prefix rõ ràng. Nó chờ đủ hai lượt gọi lần đầu, rồi in kết quả; không cần ngủ tùy tiện để đoán thứ tự thread.

Cấu trúc Maven:

~~~text
multiple-scheduled-beans/
├── pom.xml
└── src/main/java/example/MultiBeanScheduleDemo.java
~~~

**pom.xml** — một dự án Spring Framework 6.2.12 và Java 17:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>example</groupId>
    <artifactId>multiple-scheduled-beans</artifactId>
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
                    <mainClass>example.MultiBeanScheduleDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/example/MultiBeanScheduleDemo.java**:

~~~java
package example;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class MultiBeanScheduleDemo {
    private static final CountDownLatch FIRST_TICKS =
            new CountDownLatch(2);
    private static final ConcurrentSkipListSet<String> SEEN =
            new ConcurrentSkipListSet<>();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicBoolean CORRECT_EXECUTOR =
            new AtomicBoolean(true);

    public static class ReportTicker {
        private final String owner;

        public ReportTicker(String owner) {
            this.owner = owner;
        }

        @Scheduled(initialDelay = 0, fixedDelay = 60_000)
        public void tick() {
            if (!Thread.currentThread().getName()
                    .startsWith("schedule-")) {
                CORRECT_EXECUTOR.set(false);
            }

            SEEN.add(owner);
            CALLS.incrementAndGet();
            FIRST_TICKS.countDown();
        }
    }

    @Configuration
    @EnableScheduling
    public static class Config {
        @Bean("taskScheduler")
        public ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2);
            scheduler.setThreadNamePrefix("schedule-");
            return scheduler;
        }

        @Bean("eastJob")
        public ReportTicker eastJob() {
            return new ReportTicker("east");
        }

        @Bean("westJob")
        public ReportTicker westJob() {
            return new ReportTicker("west");
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             Config.class)) {

            boolean differentInstances =
                    context.getBean("eastJob")
                    != context.getBean("westJob");

            if (!FIRST_TICKS.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Hai lịch chưa cùng kích hoạt trong thời hạn");
            }

            System.out.println("Hai bean khác instance: "
                    + differentInstances);
            System.out.println("Các bean đã chạy: "
                    + String.join(",", SEEN));
            System.out.println("Số callback đầu tiên: " + CALLS.get());
            System.out.println("Đúng scheduler pool: "
                    + CORRECT_EXECUTOR.get());
        }
    }
}
~~~

Biên dịch và chạy bằng Maven/JDK 17:

~~~bash
mvn -q compile exec:java
~~~

**Stdout mong đợi** khi cả hai callback đầu tiên được scheduler thực thi trong thời hạn 5 giây (bỏ qua log thư viện/Maven):

~~~text
Hai bean khác instance: true
Các bean đã chạy: east,west
Số callback đầu tiên: 2
Đúng scheduler pool: true
~~~

**Vì sao ví dụ thể hiện đúng số registrations?**

1. Trong **Config**, hai phương thức @Bean gọi **new ReportTicker(...)** hai lần, tạo hai instances thật sự khác nhau.
2. Cùng class ReportTicker chỉ có một method **tick()** mang annotation @Scheduled.
3. @EnableScheduling kích hoạt hạ tầng tìm method; mỗi bean được container xử lý, nên lịch áp dụng cho cả **eastJob** và **westJob**.
4. **initialDelay=0** cho phép chạy lần đầu sớm; **fixedDelay=60.000 ms** khiến lượt tiếp theo chỉ có thể xuất hiện sau một khoảng khá dài kể từ khi callback trước hoàn tất. Main chờ tối đa 5 giây, nên hai callback quan sát được trong điều kiện test bình thường là hai lần đầu của hai registrations.
5. **ConcurrentSkipListSet** sắp xếp tên owner và đồng bộ an toàn, nên kết quả east,west không phụ thuộc callback nào chạy trước.
6. **CountDownLatch.countDown()** là thao tác cuối cùng trong callback. Main chỉ in sau khi cả hai countdown đã được nhận, nên CALLS và SEEN đã được ghi trước đó.
7. **CORRECT_EXECUTOR** kiểm tra prefix do ThreadPoolTaskScheduler cấu hình. Hai callback vẫn có thể chạy trên cùng một thread nếu scheduler chọn như vậy; bài chỉ yêu cầu xác minh chúng chạy qua scheduler pool.
8. Khi ApplicationContext đóng, Spring quản lý việc hủy các scheduled tasks và vòng đời scheduler bean.

**Giới hạn của ví dụ:** chúng ta không kiểm tra tốc độ xử lý job lớn, distributed clustering hay một cron mốc giờ cụ thể. Hai instances trong **một context** đã đủ để minh họa duplication. Đây là **ví dụ source tự chứa trong dự án Maven**, không phải cam kết một ứng dụng bất kỳ luôn hoàn thành callback trong 5 giây; test báo lỗi rõ khi timeout.

## 5. Trường hợp sai phổ biến: vừa @Component vừa khai báo @Bean mới

Giả sử dự án có **component scanning** bật ở package chứa CleanupJob:

~~~java
package example.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupJob {
    @Scheduled(fixedDelay = 30_000)
    public void cleanup() {
        // Dọn dữ liệu.
    }
}
~~~

Và một configuration khác:

~~~java
package example.jobs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobConfig {
    @Bean("additionalCleanupJob")
    public CleanupJob additionalCleanupJob() {
        return new CleanupJob();
    }
}
~~~

Đây là **hai tệp minh họa** trong một ứng dụng Spring có @EnableScheduling và component scanning. Khi cả hai definitions cùng được đăng ký:

- **@Component** tạo bean instance A của CleanupJob.
- **@Bean("additionalCleanupJob")** tạo instance B riêng vì method gọi **new CleanupJob()**.
- Mỗi instance có cùng method cleanup chứa @Scheduled.
- Hạ tầng scheduling có thể đăng ký hai lịch, dù người phát triển chỉ thấy một class CleanupJob.

**Đây không phải hai thread do một method tự spawn**. Đây là **hai đối tượng bean độc lập**, mỗi đối tượng được đăng ký scheduled callback.

### Cách sửa nếu chỉ cần một instance

Chọn **một cách đăng ký bean** rõ ràng:

- Giữ @Component và bỏ factory @Bean không cần thiết.
- Hoặc bỏ @Component, chỉ dùng một phương thức @Bean được Spring quản lý.
- Nếu cần nhiều cấu hình theo vùng/tenant, giữ nhiều instances có **ID và phạm vi dữ liệu riêng**, không giả định chúng là một job global.
- Viết integration test đếm bean instances của class và scheduled registrations để tránh refactor vô tình sinh bản sao.

**Không dùng @Primary để “tắt” bean thứ hai.** @Primary chủ yếu giúp chọn ứng viên khi inject dependency theo type; nó **không tự hủy một bean hay ngừng @Scheduled của bean không được chọn**.

Tương tự, thêm **@Qualifier** chỉ làm rõ injection; nếu cả hai beans vẫn tồn tại và được hạ tầng @Scheduled xử lý, cả hai có thể tiếp tục chạy lịch.

## 6. @Configurable và double initialization: cảnh báo chính thức của Spring

Một trường hợp đặc biệt được Spring nhấn mạnh là **đặt @Configurable trên class có @Scheduled**, đồng thời **đăng ký class đó như bean bình thường trong Spring container**.

**@Configurable** (khi dùng AspectJ weaving/configuration phù hợp) cho phép Spring cấu hình những đối tượng không được tạo theo cách bean factory truyền thống. Nếu cùng object/class bị cấu hình qua:

1. **Spring container lifecycle** của bean thường; và
2. **@Configurable aspect** hoạt động nhờ weaving;

thì class được xử lý khởi tạo/cấu hình hai lần, có thể dẫn đến **double scheduling** và method @Scheduled bị gọi hai lượt.

~~~text
Class MyJob
  +-- @Scheduled trên method
  +-- @Configurable trên class
               |
      +--------+--------+
      |                 |
Bean factory path    AspectJ configure path
      |                 |
      +--------+--------+
               |
        Rủi ro double processing/
        double scheduled registration
~~~

**Không nên coi @Configurable đồng nghĩa @Configuration.** Chúng là annotation khác nhau:

- **@Configuration** đánh dấu class cấu hình bean definitions.
- **@Configurable** liên quan hỗ trợ dependency injection cho object được tạo ngoài container, thường cần AspectJ weaving để có tác dụng.

Không phải một class cứ có @Configurable thì chắc chắn hai lần chạy; điều kiện double initialization chỉ xảy ra khi các cơ chế cần thiết thực sự hoạt động và cùng xử lý class/object. Nhưng nếu dự án vừa dùng **@Configurable + @Scheduled** vừa khai báo bean chính thức, hãy kiểm tra sớm vì đây là cảnh báo trực tiếp của tài liệu Spring.

**Giải pháp thường tốt nhất:** để container sở hữu một cách duy nhất vòng đời scheduled bean và bỏ @Configurable khỏi class đó khi không có nhu cầu thực sự.

## 7. Prototype và các bean scope khác: mỗi instance được tạo có thể được schedule

Kể từ Spring Framework 4.3, @Scheduled được hỗ trợ trên beans với nhiều scopes, không chỉ singleton. Điểm này dễ bị bỏ qua khi người học mặc định “Spring bean luôn là singleton”.

Với singleton mặc định:

~~~java
@Component
public class DailyJob {
    @Scheduled(fixedDelay = 60_000)
    public void run() {
        // ...
    }
}
~~~

Thông thường một bean definition singleton trong **một ApplicationContext** tạo ra **một instance**. Nếu không có definitions/context khác và chỉ có một khai báo @Scheduled, ta thường có một scheduled registration cho method ấy trong context.

Với prototype:

~~~java
@Component
@org.springframework.context.annotation.Scope("prototype")
public class PrototypeJob {
    @Scheduled(fixedDelay = 60_000)
    public void run() {
        // ...
    }
}
~~~

Đây là **các class minh họa riêng**, giả sử import Component/Scheduled đã có.

Khi code yêu cầu container tạo nhiều PrototypeJob instances, **mỗi instance được tạo và qua BeanPostProcessor** có thể nhận scheduled registration của riêng nó.

Điều quan trọng:

- **Khai báo prototype không tự tạo ra vô số object**. Nếu không có ai yêu cầu tạo bean, có thể không có instance nào được tạo.
- **Không phải mỗi tick tự sinh prototype bean mới**. Việc tạo instance và việc kích hoạt lịch là hai cơ chế riêng.
- Bean prototype có lifecycle khác singleton. Spring không tự quản lý đầy đủ mọi bước destruction của từng prototype theo cách singleton; nếu cần tạm dừng/hủy lịch cho từng instance, phải xem cụ thể hạ tầng scheduling và trách nhiệm owner.
- Với scoped proxies (request/session scope), khả năng giữ target/resolve scope trên scheduler thread có thể khác kỳ vọng; đừng dùng request-scoped bean như một background job mặc định.
- Nếu lịch phải sống suốt vòng đời ứng dụng, **singleton scheduler/coordinator** thường là thiết kế dễ hiểu hơn việc phát sinh prototype scheduled beans theo request.

### Vì sao prototype dễ tạo hiện tượng bất ngờ?

Một service có thể gọi ApplicationContext.getBean(PrototypeJob.class) hoặc ObjectProvider.getObject() nhiều lần. Nếu PrototypeJob có @Scheduled, mỗi lần lấy tạo instance mới có thể làm số scheduled registrations tăng, mặc dù **không sửa một dòng annotation nào**. Đây có thể vừa gây trùng nghiệp vụ, vừa kéo dài vòng đời instance ngoài dự kiến.

Cần kiểm tra **ai tạo bean, khi nào, bao nhiêu instances đang sống và lịch được hủy ra sao**, thay vì chỉ nhìn scope annotation.

## 8. Đừng nhầm nhiều bean instances với annotation lặp

Một bean instance **vẫn có thể có nhiều registrations** nếu method mang `@Scheduled` lặp lại; ngược lại, hai bean instances với **một annotation mỗi bean** cũng tạo hai registrations. Ví dụ `@Scheduled(fixedRate=10_000)` cộng `@Scheduled(cron="0 */5 * * * *")` trên cùng method là hai triggers độc lập, **không** tự tạo thêm bean object.

Khi điều tra “job chạy hai lần”, kiểm tra song song số instances và `method.getAnnotationsByType(Scheduled.class).length`, kể cả composed/meta-annotations của project. Việc nhiều registrations chạy song song phụ thuộc scheduler capacity; non-overlap của một JDK periodic registration không áp dụng cho hai registrations riêng. Chỉ đổi cron/fixedDelay không khử duplication do bean registration sai.


## 9. Nhiều ApplicationContexts có thể khiến cùng logic được cấu hình nhiều lần

Một ứng dụng Spring không nhất thiết chỉ có một ApplicationContext. Trong các kiến trúc web cũ hoặc những module tách context, có thể có:

- Root ApplicationContext.
- Servlet-specific child context.
- Context tạo bởi một ứng dụng nền khác trong cùng JVM.
- Một test tạo nhiều contexts độc lập.
- Một cấu hình được import/scan trong nhiều contexts khác nhau.

**@EnableScheduling hoạt động trong phạm vi context nơi nó được bật**. Nếu cùng loại scheduled bean được tạo dưới hai contexts có scheduling riêng, có thể có hai scheduled registrations tại một JVM, dù application process chỉ có một.

~~~text
JVM process
    |
    +-- root context
    |       +-- bean CleanupJob A
    |       +-- @EnableScheduling
    |       +-- schedule A
    |
    +-- child/context B
            +-- bean CleanupJob B
            +-- @EnableScheduling
            +-- schedule B
~~~

Đây là **sơ đồ tình huống**, không có nghĩa mọi parent/child context luôn tự nhân đôi bean. Nếu child chỉ tham chiếu bean từ parent mà không tạo lại một instance và không cấu hình scheduling trùng, có thể không có bản sao. Cần đọc **bean definitions theo từng context** và hiểu component scanning/import thực tế.

### Kiểm tra điều gì?

1. Có bao nhiêu context thực sự được tạo?
2. Bean definition của scheduled class nằm ở context nào?
3. @EnableScheduling được bật tại context nào?
4. Một class có bị component-scan/import ở nhiều context không?
5. Có hai singleton instances khác nhau ở hai contexts không?
6. Nếu đóng một context, lịch nào được hủy và lịch nào tiếp tục?

**Một instance ApplicationContext không tương đương một deployment pod**; đây là hai chiều độc lập.

## 10. Nhiều pods/replicas: vấn đề tương tự nhưng khác tầng

Đôi khi triệu chứng được mô tả là “một cron chạy ba lần” nhưng thực chất không có bean duplicate bên trong JVM. Deployment đang có ba replicas:

~~~text
Kubernetes replicas = 3

pod-A: ApplicationContext -> CleanupJob -> @Scheduled
pod-B: ApplicationContext -> CleanupJob -> @Scheduled
pod-C: ApplicationContext -> CleanupJob -> @Scheduled
~~~

Thông thường, mỗi process đăng ký lịch và có thể kích hoạt cùng mốc cron. Đây là **cluster-level duplication**, không phải lỗi Spring tạo sai số bean trong một context.

**Giải pháp cho một instance trong JVM không tự giải quyết một lần chạy toàn cluster**. Ví dụ:

- Xóa @Bean trùng giúp mỗi pod còn một registration, nhưng ba pods vẫn có thể chạy ba job.
- AtomicBoolean trong bean chỉ chặn concurrency của một instance/JVM, không đồng bộ với pods khác.
- Chỉnh poolSize=1 chỉ giới hạn một scheduler trong một process, không tạo leader toàn cluster.
- Thêm synchronized vào method chỉ khóa theo monitor/object trong một process.
- Cron expression giống nhau khiến các process có thể cùng đến hạn gần nhau, không ai biết đã có pod khác chạy.

Nếu nghiệp vụ yêu cầu một job duy nhất trên toàn cluster, cần **distributed coordination**: leader election, lease/lock có timeout và fencing khi cần, Quartz JDBC clustering, hệ thống job tập trung hoặc workflow engine, cùng idempotency/storage constraints phù hợp.

**Ngay cả distributed lock cũng không mặc nhiên bảo đảm exactly-once side effect** khi có network partition, lease hết hạn hoặc worker cũ vẫn làm việc. Cần thiết kế idempotency/uniqueness theo domain.

## 11. Phân biệt duplicate registration với nhiều execution hợp lệ theo thời gian

Một registration fixedRate chạy mỗi 10 giây thì trong 1 phút có thể gọi cùng method nhiều lần. Đây **không phải duplicate scheduling**. Vấn đề thực sự là:

- **Một mốc logic** lại tạo hai callback từ hai registrations không mong muốn.
- Hai callbacks có cùng job key và cùng phạm vi dữ liệu, nhưng đều cố thực thi.
- Có hai bean instances hoặc hai processes thực sự hoạt động độc lập.
- Cùng method được khai báo nhiều trigger.
- Một callback tự submit một job và sau đó scheduler tiếp tục kích hoạt, tạo overlap ở tầng business.

### Đừng chỉ dựa vào log cùng method name

Giả sử log:

~~~text
12:00:00 CleanupJob.cleanup started
12:00:00 CleanupJob.cleanup started
~~~

Có ít nhất năm khả năng:

1. Hai bean instances cùng class trong một context.
2. Một bean có hai scheduled declarations chồng mốc.
3. Hai application contexts.
4. Hai pods/replicas.
5. Một lần @Scheduled và một lần method được gọi thủ công hoặc được đăng ký qua TaskScheduler API.

Nếu log chỉ chứa class và method, không có **bean identity**, **pod ID**, **registration source** hay **job ID**, khó phân biệt.

Một kỹ thuật thực tế là gắn vào log:

~~~text
timestamp
applicationInstance / pod
applicationContextId
beanName (nếu biết chắc)
System.identityHashCode(bean) chỉ dùng để debug trong JVM
scheduledMethod
logicalJobKey
scheduleDefinitionId (nếu tự quản lý)
triggerPlannedTime và actualStart
~~~

**identityHashCode không phải ID bền vững toàn hệ thống**; có thể trùng trong phạm vi lý thuyết, thay đổi khi restart và không thể thay thế trace/job ID. Dùng nó như tín hiệu gỡ lỗi nội bộ để biết hai references có thể đến từ hai instances, đồng thời đối chiếu bằng kiểm tra reference identity thực.

## 12. Một scheduler pool dùng chung vẫn có thể chạy nhiều registrations

Giả sử cấu hình:

~~~java
@Bean("taskScheduler")
public ThreadPoolTaskScheduler scheduler() {
    ThreadPoolTaskScheduler scheduler =
            new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("schedule-");
    return scheduler;
}
~~~

Đây là **method @Bean minh họa** trong configuration class có imports và @EnableScheduling phù hợp.

Ngay cả khi scheduler poolSize=1:

- Bean A vẫn có thể đăng ký một lịch.
- Bean B vẫn có thể đăng ký lịch khác.
- Scheduler thread có thể thực thi callback A rồi B lần lượt.
- Hai callbacks không nhất thiết chạy song song, nhưng **cả hai vẫn có thể chạy**.
- Một callback A chạy lâu có thể làm B bị trễ.

Tăng scheduler poolSize lên 2 cho phép các callbacks khác nhau chạy đồng thời khi tới hạn, nhưng **không giảm số registrations**. Chỉnh thread pool size để “chặn duplicate” là thay đổi hiệu năng/độ song song, không sửa cấu hình tạo nhiều bean.

### Trường hợp callback lại delegate qua TaskExecutor

~~~java
@Scheduled(fixedRate = 10_000)
public void poll() {
    jobExecutor.execute(() -> doActualWork());
}
~~~

Đây là **đoạn minh họa**. Với hai instances cùng chứa poll, hai callbacks có thể submit hai jobs vào worker pool. Nếu scheduler thread chỉ cần vài mili giây để submit, hai business jobs có thể overlap **ngay cả khi scheduler poolSize=1**.

**Số lịch chạy** và **số job thật được gửi** không phải lúc nào 1:1: callback có thể skip, lỗi submit, retry, coalesce hoặc tạo nhiều business jobs. Hãy đếm rõ từng tầng.

## 13. Chọn trigger không giải quyết duplicate instances

`fixedRate` theo planned cadence, `fixedDelay` đợi sau completion của **chính callback trong một registration**, còn `cron` dựa trên mốc lịch và zone. Không loại trigger nào tự gộp hai bean instances hoặc tạo distributed mutual exclusion. Hai objects A/B đăng ký cùng method vẫn có thể gọi nghiệp vụ trùng, song song hoặc nối tiếp tùy scheduler.

Vì vậy, trước khi đổi loại trigger, hãy **đếm registrations và owners**, xác định logical business job key, rồi chọn một scheduled coordinator hoặc partition/claim rõ ràng cho những instances được tạo có chủ đích.


## 14. Cách điều tra nhanh: một checklist chẩn đoán

Khi quan sát @Scheduled chạy gấp đôi, thực hiện theo thứ tự:

**Bước 1 — Xác nhận “gấp đôi” là gì.** Hai log lines có thể chỉ là hai lượt hợp lệ theo thời gian, hai scheduled callbacks, hai worker jobs hoặc hai attempts của cùng job. Cần dùng job ID/trigger ID.

**Bước 2 — Kiểm tra số process.** Có bao nhiêu Kubernetes replicas, JVM processes, background workers, test runners hoặc node instances đang chạy?

**Bước 3 — Xem bean definitions.** Class có vừa @Component/@Service vừa @Bean factory không? Có hai @Bean methods cùng tạo new object? Có scan trùng module, imports hoặc conditional config khác nhau không?

**Bước 4 — Xem scope.** Có @Scope("prototype"), custom scope hay ObjectProvider.getObject() gọi nhiều lần không?

**Bước 5 — Xem lịch trên method.** Có @Scheduled lặp, @Schedules, meta-annotation hoặc programmatic TaskScheduler.schedule không?

**Bước 6 — Xem context.** Có parent/child/multiple ApplicationContexts, mỗi nơi bật @EnableScheduling không?

**Bước 7 — Kiểm tra @Configurable.** Có AspectJ weaving và class đồng thời được cấu hình bằng hai cơ chế không?

**Bước 8 — Kiểm tra manual invocation.** Có REST endpoint, ApplicationRunner, @EventListener hoặc service khác cũng gọi chính method nghiệp vụ không?

**Bước 9 — Kiểm tra scheduling vs execution.** Callback @Scheduled có submit worker task? Có retry/backoff ở worker? Một trigger có thể tạo nhiều jobs?

**Bước 10 — Viết integration test** đếm bean instances và quan sát các triggers đầu tiên bằng CountDownLatch, không chỉ dùng Thread.sleep.

Đừng sửa bằng cách thêm một biến static boolean cấm mọi lần chạy trước khi xác định căn nguyên. Một guard cục bộ có thể giấu duplicate registration nhưng không xóa registration, và dễ làm sai các jobs có nhiều vùng/tenant hợp lệ.

## 15. Dùng Spring Boot Actuator để quan sát scheduled registrations

Trong ứng dụng có **Spring Boot Actuator** và đã cho phép endpoint tương ứng, **/actuator/scheduledtasks** cung cấp thông tin các task có lịch: cron, fixedRate, fixedDelay, target/runnable và metadata khác tùy version.

Ví dụ truy cập nếu endpoint được bật và được bảo vệ bởi cơ chế quản trị thích hợp:

~~~bash
curl http://localhost:8080/actuator/scheduledtasks
~~~

Đây là **lệnh minh họa**, không phải endpoint tự có trong Spring Framework thuần hoặc mọi Spring Boot deployment.

**Điều cần hiểu:**

- Endpoint giúp nhận biết có nhiều registrations cùng target/method hoặc cùng biểu thức lịch.
- Nó **không phải lúc nào cũng cung cấp một bean instance ID duy nhất** để phân biệt hai đối tượng cùng class. Kết hợp logging, ApplicationContext inspection hoặc observability có nhãn bean name.
- Endpoint có thể **không được expose qua HTTP** theo cấu hình mặc định; muốn dùng phải cài Actuator và cấu hình exposure/authorization chính xác.
- Không công khai dữ liệu vận hành nhạy cảm cho Internet chỉ để tiện debug.
- Có thể có tasks đăng ký động hoặc được quan sát ở thời điểm khác với lúc request endpoint trả; snapshot API không phải lịch sử toàn bộ sự kiện.

Đừng dựa vào việc endpoint trả hai entries để kết luận hai bean instances nếu method có @Scheduled repeatable. Phải đối chiếu số declarations và nguồn đăng ký.

### ScheduledTaskHolder trong context

Spring cung cấp abstraction **ScheduledTaskHolder** để truy xuất tập scheduled tasks đã đăng ký trong hạ tầng tương ứng. Trong một số cấu hình, ScheduledAnnotationBeanPostProcessor và ScheduledTaskRegistrar tham gia cung cấp thông tin này.

Đây là gợi ý cho integration diagnostics/test, không phải phương thức tự động cho ra “số bean instances” chính xác. Một chương trình kiểm tra phải biết **registration source, context và time of observation** để không đếm trùng dữ liệu từ nhiều holder.

## 16. Phân biệt số bean names và số object identities

Một bẫy khác: nhìn thấy hai bean names **không đủ** để kết luận có hai object instances. Bean aliases có thể là nhiều tên trỏ tới cùng một bean. Một @Bean method hoặc FactoryBean cũng có thể được cấu hình trả cùng một reference trong những hoàn cảnh nhất định.

Để chẩn đoán chính xác, kiểm tra:

~~~java
Object first = context.getBean("firstJob");
Object second = context.getBean("secondJob");

System.out.println(first == second);
~~~

Đây là **đoạn Java minh họa trong method có biến context**. Nếu false, hai references ở điểm đó là hai đối tượng khác nhau (cũng cần lưu ý proxy có thể che target identity). Nếu true, chúng trỏ cùng một Java object tại thời điểm đó. Nhưng việc Spring scheduling xử lý bao nhiêu lần còn phụ thuộc **đường bean registration/post-processing**.

Nói cách khác:

- **Hai @Bean names ≠ mặc định hai instances.**
- **Hai instances cùng class ≠ chắc chắn được Spring schedule**, nếu một instance không được container quản lý.
- **Một instance ≠ mặc định một registration**, vì có thể có nhiều annotations hoặc xử lý trùng đặc thù.
- **Một registration ≠ một lượt chạy**, vì lịch định kỳ có nhiều executions.

Vì vậy, phép đo tốt là kết hợp **bean names, object/proxy identity, scheduled task registrations và callback event IDs**.

## 17. Khi nào nhiều bean instances là thiết kế đúng?

Không phải cứ hai schedules là lỗi. Trong hệ thống multi-tenant hoặc xử lý nhiều vùng, một class có thể đại diện cho một cấu hình khác nhau.

Ví dụ:

~~~java
@Bean("euMetricsPoller")
public MetricsPoller euMetricsPoller() {
    return new MetricsPoller("EU");
}

@Bean("usMetricsPoller")
public MetricsPoller usMetricsPoller() {
    return new MetricsPoller("US");
}
~~~

Đây là **hai method factory minh họa**; nếu MetricsPoller có @Scheduled, cả hai instances có thể được schedule.

Thiết kế này có thể tốt nếu:

1. EU poller chỉ xử lý dữ liệu EU, US poller chỉ xử lý US.
2. Mỗi instance có ID rõ trong log/metrics.
3. Quy tắc concurrency và timeout được xác định cho từng vùng.
4. Business task idempotent hoặc có partition ownership rõ.
5. Scheduler/worker pool được sizing cho tổng workload của hai instances.
6. Shutdown đảm bảo các nhiệm vụ được dừng/dọn đúng.
7. Nếu nhiều pods, có thêm partition/leader election để mỗi vùng không vô tình bị nhiều pods cùng xử lý.

**Không nên dùng cùng một cron và cùng một domain key rồi cho rằng Spring hiểu hai instances là “một nhiệm vụ logic”.** Spring nhìn thấy hai đối tượng đã được đăng ký; hợp đồng gộp trùng thuộc về ứng dụng.

## 18. Khi chỉ muốn một lịch trong một ApplicationContext

Giải pháp đơn giản và bền vững:

**A. Một bean đăng ký bằng một con đường.**

~~~java
@Component
public class DailyCleanupJob {
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void run() {
        // ...
    }
}
~~~

Không tạo thêm **new DailyCleanupJob()** rồi đăng ký @Bean khác có cùng logic, trừ khi thực sự cần phiên bản độc lập.

**B. Một scheduler/coordinator chịu trách nhiệm phát công việc.**

Nếu cần nhiều worker thực hiện các phần việc khác nhau, tạo **một scheduled coordinator** và gọi các services theo tham số hoặc submit vào worker executor. Điều này giữ một điểm vào lịch rõ ràng, dù vẫn cần xử lý lỗi/overlap.

~~~text
One @Scheduled Coordinator
          |
          +--> EU service
          +--> US service
          +--> cleanup service
~~~

**C. Dùng conditional configuration để tránh tạo bean trùng.**

Có thể dùng **@Profile**, **@ConditionalOnProperty** hoặc **@ConditionalOnMissingBean** (trong Spring Boot phù hợp) để bật/tắt definition trong điều kiện đúng. Nhưng condition chỉ làm việc ở phạm vi cấu hình/bean definitions; cần test khi nhiều module, auto-configuration và user configuration cùng tham gia.

**D. Không cố định cấu hình worker bằng @Primary.**

@Primary có thể giải quyết injection ambiguity nhưng không dừng lịch của một bean thứ hai đã tồn tại.

**E. Tắt lịch theo cấu hình khi cần.**

Spring hỗ trợ cron disabled trigger **"-"** (Scheduled.CRON_DISABLED) để một cron configurable có thể được tắt mà không xóa code. Tuy nhiên, tắt một trigger không làm bean instance biến mất; kiểm tra các @Scheduled declarations khác vẫn còn hoạt động không.

**F. Thêm test số registrations/đầu ra.**

Nếu yêu cầu “một callback cho job logic X trên mỗi process”, test này cần được ghi rõ như invariant của hệ thống, không chỉ có trong tài liệu.

## 19. Nếu muốn một lượt trên toàn cluster, phải xử lý ở tầng khác

**Một scheduled bean singleton trong mỗi pod vẫn có thể tạo nhiều lượt toàn cluster.** Để chỉ một job tác động lên dữ liệu global tại cùng thời điểm, chọn công cụ theo yêu cầu:

| Phương án | Điều giải quyết | Cần chú ý |
| --- | --- | --- |
| **Leader election** | Chỉ node leader chủ động kích hoạt công việc | Leader thay đổi, split-brain, lease expiration; cần fencing/idempotency |
| **Distributed lock/lease** | Tránh nhiều nodes cùng giữ quyền trong một khoảng | TTL, renewal, clock skew, worker cũ tiếp tục sau khi mất lease |
| **Database uniqueness / optimistic locking** | Tránh ghi trùng business effect | Phải thiết kế job key, transaction và retry |
| **Quartz clustered JDBC JobStore** | Scheduling/state qua nhiều nodes theo cơ chế Quartz | Cần cấu hình JobStore, job identity, misfire và transaction đúng |
| **External job scheduler/queue** | Một nguồn tiếp nhận, phân công công việc theo state | Ack/retry, visibility timeout, idempotency và liveness |
| **At-least-once + idempotent handler** | Cho phép retry/duplicate delivery nhưng không lặp side effect | Phải định nghĩa khóa/idempotency tại domain boundary |

Không nên hứa **exactly once** chỉ vì đã dùng lock. Nếu hai processes cùng thấy mình có quyền do lỗi mạng/lease hoặc worker đầu chạy quá lâu, vẫn có thể xảy ra thực thi chồng lấn. Điều quan trọng là **bảo vệ invariants của business effect** bằng state, database constraints và idempotency.

## 20. Sự khác nhau giữa một registration và một business job

Nhiều bài học chỉ đếm callback @Scheduled mà quên tầng thực thi công việc:

~~~text
@Scheduled callback
   |
   +-- load config
   +-- TaskExecutor.submit(job)
   |      |
   |      +-- accepted --> worker queue --> run --> success/fail
   |      +-- rejected --> TaskRejectedException
   |
   +-- callback returns
~~~

Nếu cùng một callback chạy do hai bean instances:

- Có thể có **hai submit attempts**.
- Nếu worker queue có chỗ, có thể có **hai accepted jobs**.
- Nếu worker pool bão hòa, một hoặc cả hai bị reject.
- Nếu có retry, một accepted job còn có thể chạy lại theo policy.
- Nếu business job có shared lock/idempotency, có thể chỉ một side effect được commit.

Các chỉ số phải tách:

| Metric | Mô tả |
| --- | --- |
| **schedule.registrations** | Số lịch được hạ tầng đăng ký ở runtime |
| **schedule.callbacks** | Số callback thực sự bắt đầu |
| **schedule.callback.errors** | Lỗi trong callback |
| **job.submit.attempts** | Số lượt callback định giao việc |
| **job.submit.rejected** | Số lượt không được executor nhận |
| **job.started / job.completed** | Công việc thật đã bắt đầu và kết thúc |
| **job.duplicate.suppressed** | Lượt bị loại bởi de-duplication/lock |
| **job.business_effect.committed** | Số side effects hoàn tất theo domain |

Nếu chỉ nhìn **schedule.callbacks**, bạn chưa biết có bao nhiêu tác động nghiệp vụ đã diễn ra.

## 21. Không overlap của một periodic task không bảo vệ những registrations khác

Một số tài liệu JDK nêu rằng các lượt thực thi liên tiếp của một periodic task trong ScheduledThreadPoolExecutor không overlap. Điều này đúng ở phạm vi **một periodic task registration**.

Nhưng nếu Spring đăng ký:

~~~text
Registration A -> object A.run()
Registration B -> object B.run()
~~~

thì A và B là **hai tasks khác nhau**, có thể được scheduler thực thi song song khi có đủ scheduler threads.

Ngay cả khi A và B gọi vào **cùng database table**, runtime không biết đó là một tác vụ nghiệp vụ global cần khóa. Các nguồn race:

- Cùng đọc một tập record PENDING rồi cả hai cập nhật.
- Hai batch exports cùng tạo một file/output path.
- Hai email sender cùng gửi cùng một thông báo.
- Hai refresh jobs cùng vô hiệu hóa cache.
- Một job ghi checkpoint mà job còn lại ghi lùi version.

Các biện pháp bảo vệ khác nhau:

- **Idempotent update** với unique business key.
- **Optimistic locking** hoặc database constraint.
- **Distributed lock** nếu chạy nhiều JVM.
- **Semaphore/AtomicBoolean** nếu chỉ cần single-flight trong một JVM.
- **Partition-specific jobs** nếu hai instances được phân công dữ liệu độc lập.
- **Durable job store** nếu cần replay/retry có kiểm soát.

**Không lấy synchronized trên method làm đáp án chung:** mỗi bean instance có monitor riêng; synchronized trên A không ngăn method của B chạy. Nếu có hai pods, thậm chí synchronized static chỉ khóa trong một JVM.

## 22. Viết một kiểm thử để phát hiện cấu hình tạo lịch trùng

Mục tiêu kiểm thử phải xác định rõ: **một hay hai registrations là kỳ vọng?**

### Nếu kỳ vọng đúng một bean instance

Trong integration test có Spring ApplicationContext:

~~~java
Map<String, CleanupJob> jobs =
        context.getBeansOfType(CleanupJob.class);

if (jobs.size() != 1) {
    throw new AssertionError(
            "Mong một CleanupJob bean, nhận " + jobs.keySet());
}
~~~

Đây là **đoạn minh họa** với context và CleanupJob hợp lệ. Nó giúp phát hiện nhiều bean definitions cùng type trong context được tra cứu.

Cần nhớ:

- getBeansOfType có thể tác động khởi tạo một số beans tùy cấu hình; test trong context riêng, kiểm soát side effects.
- Nó không tự đếm @Scheduled repeatable declarations.
- Nó không đếm các contexts khác hoặc pods khác.
- Nếu bean được proxy bằng JDK interface, tra type concrete cần xem lại; đo đúng type/bean names.
- Cùng một type có thể được tạo chủ đích nhiều instances, nên assert bằng type chỉ phù hợp khi domain thật sự yêu cầu duy nhất.

### Nếu kỳ vọng hai instances

Dùng **CountDownLatch(2)** như ví dụ đầy đủ để chờ hai callbacks đầu tiên, kiểm tra owner IDs khác nhau. Cách này tốt hơn **Thread.sleep(100)**, vốn có thể pass/fail tùy tải CPU và scheduler start.

### Nếu kỳ vọng một business job, dù có nhiều callbacks

Đừng chỉ assert số logs. Tạo một **idempotency key** hoặc fake repository có uniqueness constraint. Cho hai callbacks cùng tranh xử lý và xác minh:

- Chỉ một lần commit business effect.
- Lượt thứ hai bị **skip/duplicate-suppressed** có đo đếm.
- Lock/lease có cleanup khi exception.
- Nếu task đầu lỗi sau side effect, retry không thực hiện side effect hai lần.

Với distributed coordination, một unit test trong JVM không đủ; cần integration test với database/lease backend và mô phỏng failover phù hợp.

## 23. Phòng ngừa duplicate @Scheduled khi refactor

Những thay đổi có thể khiến số registrations tăng mà code @Scheduled không đổi:

1. Bật thêm **@ComponentScan** cho package đã được một module khác tạo @Bean.
2. Đưa job class từ configuration-only sang @Component mà quên xóa @Bean cũ.
3. Đổi bean từ singleton thành prototype rồi bắt đầu tạo nhiều instances qua ObjectProvider.
4. Tạo một **ApplicationContext** thứ hai trong test hoặc một subsystem.
5. Chuyển từ một pod sang nhiều replicas.
6. Thêm một @Scheduled thứ hai (hoặc composed annotation) trên cùng method.
7. Đưa class qua @Configurable/AspectJ wiring trong khi vẫn là bean thường.
8. Thêm programmatic **TaskScheduler.schedule(...)** song song với @Scheduled.
9. Clone cùng một configuration class cho nhiều tenants nhưng không phân vùng work.
10. Đổi lịch để callback submit job nhanh hơn, khiến công việc trước và sau overlap; trông giống “chạy trùng” dù registration count không tăng.

Trong code review, ghi nhận rõ:

- **Owner của lịch**: bean name, context, process và team.
- **Logical job key**: cùng hay khác giữa instances.
- **Trigger definition**: fixedRate, fixedDelay, cron, zone.
- **Execution policy**: parallel, single-flight, skip, queue hay coalesce.
- **Failure policy**: log, retry, dead-letter, rollback/compensation.
- **Operational invariants**: per-process duy nhất hay toàn cluster duy nhất.

## 24. Những nhầm lẫn phổ biến

**“Có một class CleanupJob nghĩa là có một lịch.”** Sai. Nhiều instances của class có thể được Spring xử lý và đăng ký nhiều lịch.

**“@Scheduled hoạt động trên mọi object tạo bằng new.”** Sai. Object cần đi qua cơ chế quản lý/đăng ký phù hợp của Spring.

**“@Component và @Bean tạo hai bean nhưng @Primary sẽ cho Spring chỉ chạy một.”** Sai. @Primary không tự xóa bean còn lại hoặc hủy scheduling.

**“Một bean có đúng một method @Scheduled nên chỉ có một registration.”** Chưa chắc. Method có thể có **nhiều @Scheduled declarations** hoặc có cấu hình đặc thù.

**“Prototype scope nghĩa là mỗi tick tạo một object mới.”** Sai. Scope quyết định cách tạo bean khi được yêu cầu; scheduler sử dụng instance đã được đăng ký.

**“Chỉ singleton mới được @Scheduled.”** Sai. Spring Framework hỗ trợ @Scheduled trên bean nhiều scope từ 4.3, với lifecycle cần cân nhắc.

**“@Configurable và @Configuration là một.”** Sai. @Configurable có thể liên quan AspectJ weaving; việc kết hợp sai với bean lifecycle có thể gây double initialization.

**“Chỉnh poolSize=1 là sẽ chỉ có một @Scheduled registration.”** Sai. Pool size là số scheduler workers, không phải số tasks được đăng ký.

**“FixedDelay đảm bảo hai beans có cùng method không overlap.”** Sai. Mỗi registration có lịch sử callback của riêng nó.

**“Hai log entries đồng thời chắc chắn do hai bean instances.”** Không đủ bằng chứng. Có thể do repeatable schedules, contexts, pods, manual invocation, retries hoặc delegation.

**“Hai bean names chắc chắn là hai objects.”** Không luôn đúng vì alias/reference và proxy; kiểm tra object identity cùng bean definitions.

**“Một ApplicationContext có một scheduled job nên cả cluster chỉ có một.”** Sai khi deployment nhiều processes.

**“Synchronized trên instance A cũng khóa instance B.”** Sai. Hai instances có monitors riêng.

**“@Scheduled bảo đảm exactly-once.”** Sai. Scheduling chỉ điều phối thời điểm kích hoạt; idempotency và state mới quyết định business effect.

**“Không thấy exception trong callback nghĩa là một job đã thành công.”** Sai nếu callback chỉ submit công việc sang executor khác.

**“Actuator /scheduledtasks luôn hiển thị ID bean duy nhất.”** Không được bảo đảm. Nó hiển thị thông tin scheduled tasks theo API/version; cần đối chiếu context và instance logs.

## 25. Bài tập suy luận

**Câu 1.** Có hai @Bean methods trả hai new CleanupJob() instances, class chỉ có một @Scheduled method. Bao nhiêu registrations có thể xuất hiện trong một ApplicationContext đang bật scheduling?

**Đáp án:** Hai registrations, một cho từng bean instance được xử lý. Không suy rằng chắc chắn hai callbacks chạy đồng thời.

**Câu 2.** Có một singleton bean, nhưng method được đánh dấu @Scheduled hai lần. Có nhất thiết hai instances không?

**Đáp án:** Không. Một instance có thể chứa hai trigger registrations độc lập do repeatable annotation.

**Câu 3.** Có ba Kubernetes pods, mỗi pod có một bean @Scheduled singleton. Tổng số scheduled callbacks mỗi mốc cron có thể là bao nhiêu?

**Đáp án:** Có thể có ba callbacks, một trên mỗi pod, trong điều kiện các pods/schedulers đang hoạt động và lịch tới hạn. Không có cluster de-duplication mặc định.

**Câu 4.** Một class có @Component và một @Bean factory tạo new cùng class. @Primary có tắt lịch của bean kia không?

**Đáp án:** Không. Cần sửa bean registration hoặc chính sách schedule, không chỉ injection priority.

**Câu 5.** @Scope("prototype") đã được đặt trên @Scheduled class nhưng không ai bao giờ yêu cầu container tạo object đó. Có tự xuất hiện 100 lịch không?

**Đáp án:** Không. Prototype chỉ được tạo khi được yêu cầu theo lifecycle; mỗi instance thực sự được xử lý mới có cơ hội schedule.

**Câu 6.** @Scheduled(fixedDelay=5_000) trên hai beans, mỗi callback chạy 30 giây. Bean A có phải chờ B xong vì fixedDelay không?

**Đáp án:** Không theo semantics của fixedDelay; mỗi scheduled registration có chu kỳ dựa trên completion của callback riêng. Có thể chờ do scheduler pool thiếu thread, nhưng đó là giới hạn tài nguyên khác.

**Câu 7.** poolSize=1 và hai registrations cùng cron. Chỉ một registration được giữ lại có đúng không?

**Đáp án:** Không. Scheduler có thể thực thi tuần tự hai callbacks trên cùng một worker.

**Câu 8.** Một class vừa @Configurable vừa @Scheduled và là Spring bean, dự án dùng weaving. Rủi ro là gì?

**Đáp án:** Double initialization/processing khiến scheduled methods có thể bị kích hoạt hai lần, như Spring documentation cảnh báo. Cần bỏ một con đường quản lý lifecycle hoặc thiết kế lại.

**Câu 9.** Actuator hiển thị hai tasks cùng target method. Có thể kết luận hai bean instances không?

**Đáp án:** Chưa. Cần xem method có repeatable annotations không, context/bean identities và cấu hình đăng ký bằng tay.

**Câu 10.** Cùng một method có @Scheduled, nhưng code tự gọi cleanup() từ REST endpoint. Hai lần gọi là do hai schedules chứ?

**Đáp án:** Không chắc. Một lần có thể do REST/manual invocation và một lần do scheduler. Tách source/correlation của invocation trong logs.

**Câu 11.** Nếu business job phải chạy đúng một lần trên toàn cluster, chỉ giữ một bean singleton ở mỗi pod đã đủ chưa?

**Đáp án:** Chưa. Phải có distributed coordination/idempotency và state để bảo vệ hiệu ứng nghiệp vụ.

**Câu 12.** Nếu một instance local cần tránh overlap với chính nó, AtomicBoolean inFlight đủ bảo đảm toàn cluster không overlap chưa?

**Đáp án:** Chưa. Nó chỉ bảo vệ một object/JVM; không điều phối pods khác.

**Câu 13.** Vì sao scheduled registrations có thể nhiều nhưng số business effects cuối cùng là một?

**Đáp án:** Có thể có idempotency key, uniqueness constraint hoặc job coordinator loại duplicate trước khi commit. Cần quan sát riêng callbacks, accepted jobs và committed effects.

**Câu 14.** Nếu chỉ muốn một lịch cho hai vùng EU/US nhưng xử lý cả hai vùng, nên tổ chức ra sao?

**Đáp án:** Có thể dùng một scheduled coordinator, sau đó gọi/submit logic theo vùng. Nếu vẫn có hai bean instances được schedule có chủ đích thì cần đảm bảo mỗi instance chỉ xử lý partition của mình.

**Câu 15.** Tại sao không sửa lỗi duplicate scheduling bằng Thread.sleep trong @Scheduled method?

**Đáp án:** Sleep giữ scheduler worker, làm lịch khác bị trễ và không hủy những registrations trùng. Cần xác định nguồn nhân bản lịch và cơ chế concurrency thực.

## 26. Checklist trước khi triển khai @Scheduled

- [ ] Xác minh bean @Scheduled do **Spring container quản lý** và scheduling đã bật trong context đúng.
- [ ] Đếm bean **instances** thực tế, không chỉ đếm class/method names.
- [ ] Kiểm tra **@Component/@Service kết hợp @Bean factory** có tạo object trùng không.
- [ ] Xem **@Scope("prototype")**, ObjectProvider và những con đường tạo instances theo request.
- [ ] Tránh **@Configurable + @Scheduled** trên class cũng là bean thường khi không có lý do rõ.
- [ ] Kiểm tra **@Scheduled lặp/repeatable**, composed annotations và programmatic scheduling.
- [ ] Phân biệt **ApplicationContexts**, JVM processes và số deployment replicas.
- [ ] Xác định ý nghĩa “một lần”: mỗi instance, mỗi process, mỗi cluster hay mỗi business key.
- [ ] Dùng bean name/context/pod/job key/trigger ID trong metrics/logs để truy nguồn.
- [ ] Không dùng @Primary/@Qualifier làm cách tắt scheduling không mong muốn.
- [ ] Không dùng scheduler poolSize=1 để che việc tạo registrations trùng.
- [ ] Xác định fixedDelay, fixedRate, cron theo callback và điều kiện overlap.
- [ ] Nếu callback delegate, đo riêng submit attempts, accepted/rejected jobs và actual work completion.
- [ ] Với multi-pod, dùng coordination/locking/idempotency thích hợp.
- [ ] Bảo vệ transaction/side effect bằng uniqueness/version/idempotency, không chỉ đồng bộ luồng.
- [ ] Test bằng CountDownLatch/Future.get(timeout), tránh Thread.sleep đoán thứ tự.
- [ ] Bảo đảm destroy/context close dừng các scheduled tasks đúng lifecycle.
- [ ] Dùng Actuator/đo registered tasks nếu có, nhưng xác minh identity bằng chứng bổ sung.
- [ ] Nếu công việc cần durability hoặc retry qua restart, dùng job store/queue phù hợp.

## Kết luận

**@Scheduled được triển khai trên các bean instances mà Spring xử lý, không phải trên khái niệm “mỗi class chỉ một lịch”.** Nếu có nhiều instances của cùng một scheduled class — do nhiều @Bean, trộn @Component với @Bean, prototype beans, double initialization hoặc nhiều contexts — hạ tầng có thể đăng ký **nhiều scheduled callbacks**, kể cả khi mỗi class chỉ có một annotation. Ngoài ra, một method có thể mang **nhiều @Scheduled declarations**, và một deployment nhiều pods có thể lập lịch ở từng process; đây là những nguyên nhân khác phải phân biệt.

Muốn thiết kế đúng, hãy kiểm soát **bean registration và lifecycle**, đếm **scheduled registrations** thực tế, gắn ID rõ cho các nguồn kích hoạt và xác định yêu cầu nghiệp vụ theo instance/process/cluster. Khi chỉ cần một lịch, chọn một chủ sở hữu lịch rõ ràng. Khi nhiều lịch là chủ đích, phân vùng công việc và thiết kế concurrency/idempotency. **@Scheduled không tự đảm bảo exactly-once hoặc tính duy nhất toàn cluster.**

**Tài liệu chính thức:**

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html), mục @Scheduled, repeatable scheduling, bean scopes và cảnh báo @Configurable.
- [Spring Framework — @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html).
- [Spring Framework — ScheduledAnnotationBeanPostProcessor Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/ScheduledAnnotationBeanPostProcessor.html).
- [Spring Framework — ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html).
- [Spring Boot Actuator — Scheduled Tasks endpoint](https://docs.spring.io/spring-boot/api/rest/actuator/scheduledtasks.html), nếu ứng dụng có cài và cho phép truy cập endpoint.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-tasks-011">Đưa chu kỳ ra cấu hình và dùng giá trị cron vô hiệu hóa</a>

<details>
<summary>Xem chi tiết</summary>

# Đưa chu kỳ ra cấu hình và dùng giá trị cron vô hiệu hóa trong @Scheduled

`@Scheduled` của Spring Framework giúp một bean chạy tác vụ theo **cron**, **fixed-rate** hoặc **fixed-delay**. Viết cứng chu kỳ trong annotation là đủ cho bản demo, nhưng thường không phù hợp cho ứng dụng có nhiều môi trường: test cần tránh job thật, staging có lịch khác production, và vận hành đôi khi cần **tạm vô hiệu hóa một lịch** mà không sửa mã nguồn. Spring hỗ trợ chuyển các tham số của `@Scheduled` thành **giá trị cấu hình** thông qua placeholder `${...}`. Riêng thuộc tính `cron` có giá trị đặc biệt **`"-"`**, được công bố bằng hằng `Scheduled.CRON_DISABLED`, có nghĩa là **không đăng ký cron trigger** đó.

Bài này thuộc nhóm **@Scheduled** của module **Spring Framework - concurrency**. Nội dung giải thích cách cấu hình cron/fixed-delay/fixed-rate qua properties/YAML, cách tắt lịch bằng dấu `-`, các giới hạn của cơ chế tắt lịch, cách kiểm chứng Spring thực sự không đăng ký task, và các lưu ý về timezone, deployment nhiều instance, cập nhật cấu hình, transaction và độ bền nghiệp vụ.

**Kết luận trước:** đối với `@Scheduled(cron = "${app.jobs.cleanup.cron:-}")`, Spring sẽ resolve placeholder lúc xử lý scheduled annotation. Khi property không được khai báo, **default value `-`** làm cron trigger bị bỏ qua. Khi property có giá trị như `"0 */15 * * * *"`, Spring đăng ký lịch tương ứng. Nhưng `-` **chỉ là sentinel cho cron**; không dùng nó như một giá trị hợp lệ của `fixedDelayString` hoặc `fixedRateString`.

## 1. Mục tiêu học tập

Sau bài này, bạn sẽ có thể:

- Giải thích khác biệt giữa chu kỳ viết cứng và chu kỳ được resolve từ externalized configuration.
- Sử dụng `cron = "${...}"`, `fixedDelayString = "${...}"`, `fixedRateString = "${...}"` và `initialDelayString` khi phù hợp.
- Nhận biết chính xác hằng `Scheduled.CRON_DISABLED` có giá trị `"-"`, được Spring hỗ trợ từ Spring Framework 5.1.
- Viết placeholder có **default tắt lịch**: `${app.jobs.cleanup.cron:-}`.
- Tách cấu hình `application.yml` / `application.properties`, cấu hình theo profile và các override ở deploy.
- Không nhầm **cron expression sáu trường** của Spring với cron năm trường của Unix, và biết tác động của `zone`/DST.
- Phân biệt disable một cron trigger với disable toàn bộ bean, toàn bộ scheduler hoặc tất cả các lịch.
- Hiểu vì sao thay đổi property ở file hoặc config server **không mặc nhiên reschedule** một `@Scheduled` đã được đăng ký.
- Kiểm thử ở cả ba trạng thái: cron hợp lệ, cron bằng `-`, property không có; kiểm tra task registration thay vì chờ bằng `Thread.sleep`.
- Đánh giá các giải pháp thay thế cho fixed-delay/fixed-rate, hot reload và job cần điều phối toàn cụm.

## 2. Nhắc lại ngắn gọn: Spring @Scheduled chạy như thế nào?

Khi `@EnableScheduling` có hiệu lực, Spring đăng ký `ScheduledAnnotationBeanPostProcessor` để quét các bean được quản lý. Nó đọc metadata của `@Scheduled`, resolve placeholder/SpEL khi thích hợp, chọn scheduler và đăng ký task.

~~~text
Spring ApplicationContext khởi tạo
        |
        v
ScheduledAnnotationBeanPostProcessor
        |
        +-- đọc @Scheduled trên Spring bean
        |
        +-- resolve ${...} từ Environment / property sources
        |
        +-- diễn giải cron, fixedRate hoặc fixedDelay
        |
        +-- đăng ký với TaskScheduler
                    |
                    +-- chạy theo lịch nếu trigger đang bật
~~~

Method được annotate phải phù hợp hợp đồng `@Scheduled`; trường hợp phổ biến là **không có tham số** và trả về `void` (giá trị trả về kiểu không reactive thường bị bỏ qua). Spring 6.1+ có thêm đường hỗ trợ reactive/suspending methods với semantics riêng; bài này tập trung vào Java method đồng bộ được scheduler gọi.

`@EnableScheduling` là công tắc cho Spring scheduling. Nó **khác** `@EnableAsync`, vốn phục vụ `@Async`. Việc sử dụng cron placeholder không cần `@EnableAsync`. Ngược lại, chỉ cấu hình property mà quên `@EnableScheduling` cũng không làm scheduled method tự chạy.

### Ví dụ chưa externalize

~~~java
@Scheduled(cron = "0 */15 * * * *")
public void cleanupExpiredData() {
    cleanupService.removeExpired();
}
~~~

Đây là **đoạn minh họa Spring**, cần bean `cleanupService`. Cron trên chạy tại **giây 0 mỗi 15 phút**, theo timezone của scheduler/cron trigger. Muốn đổi sang 10 phút phải sửa annotation và phát hành lại mã. Muốn tắt cron phải sửa code hoặc thêm logic ngoài, trừ khi ta đưa cron ra property.

## 3. Giải pháp cơ bản: placeholder + CRON_DISABLED

Ví dụ chủ đạo:

~~~java
@Scheduled(
        cron = "${app.jobs.cleanup.cron:-}",
        zone = "${app.jobs.cleanup.zone:UTC}")
public void cleanupExpiredData() {
    cleanupService.removeExpired();
}
~~~

Đây là fragment Spring method; hãy đặt trong một bean có `@Component`/`@Service` được Spring quản lý. Cú pháp của placeholder:

- `${app.jobs.cleanup.cron}`: đọc property tên `app.jobs.cleanup.cron`; nếu thiếu và không có default, cấu hình có thể thất bại do placeholder không resolve.
- `${app.jobs.cleanup.cron:-}`: đọc property nếu có; **nếu thiếu**, lấy chuỗi default sau dấu hai chấm, tức **`-`**.
- `${app.jobs.cleanup.zone:UTC}`: lấy timezone từ property hoặc mặc định `UTC`.

Dấu `-` không phải cú pháp của shell hay Spring Boot profile. Nó là **giá trị cron đặc biệt** được `@Scheduled` công nhận:

~~~java
Scheduled.CRON_DISABLED // "-"
// tương đương về ý nghĩa disable cron với literal:
@Scheduled(cron = Scheduled.CRON_DISABLED)
~~~

Ở đây `Scheduled.CRON_DISABLED` thuộc `org.springframework.scheduling.annotation.Scheduled`, giá trị chính xác là `"-"`. Dòng `@Scheduled(...)` chỉ là fragment annotation minh họa. Ghi trực tiếp hằng sẽ **vô hiệu hóa cố định** ở compile-time; để bật/tắt từ môi trường, hãy dùng placeholder.

### Bảng hoạt động

| Giá trị property `app.jobs.cleanup.cron` | Giá trị Spring resolve | Kết quả |
| --- | --- | --- |
| Không có property | `-` theo default trong `${...:-}` | Không đăng ký cron trigger |
| `-` | `-` | Không đăng ký cron trigger |
| `0 */15 * * * *` | Chính biểu thức đó | Đăng ký chạy mỗi 15 phút, tại giây 0 |
| `0 0 2 * * *` | Chính biểu thức đó | Đăng ký chạy lúc 02:00 mỗi ngày theo zone |
| `not-a-cron` | Chuỗi không hợp lệ | Có thể làm Spring startup/registration lỗi |
| Chuỗi rỗng `""` | Chuỗi rỗng, khác `-` | Không nên dùng làm cờ tắt; semantics khác và có thể gây cấu hình không mong muốn |

**Quan trọng:** mặc định `-` chỉ áp dụng khi **property thiếu**. Nếu property có mặt nhưng là chuỗi rỗng, placeholder không nhất thiết tự thay nó bằng default. Đừng dựa vào biến môi trường rỗng để tắt lịch; cấu hình rõ ràng bằng `"-"`.

## 4. Viết cấu hình application.yml

Ví dụ với Spring Boot có hỗ trợ externalized configuration:

Tệp **application.yml**:

~~~yaml
app:
  jobs:
    cleanup:
      cron: "0 */15 * * * *"
      zone: "Asia/Ho_Chi_Minh"
~~~

Giá trị `"0 */15 * * * *"` có **sáu trường** theo cron của Spring: second, minute, hour, day-of-month, month, day-of-week. Với `zone = "Asia/Ho_Chi_Minh"`, lịch được hiểu theo múi giờ đã chỉ định thay vì timezone máy chủ. Đặt timezone rõ ràng giúp lịch giống nhau giữa laptop, CI và container production, nhưng vẫn cần lưu ý DST nếu sử dụng những vùng có thay đổi giờ.

Để tắt job mà không sửa mã Java:

~~~yaml
app:
  jobs:
    cleanup:
      cron: "-"
      zone: "Asia/Ho_Chi_Minh"
~~~

Nên **đặt dấu ngoặc kép** cho biểu thức cron và `"-"` trong YAML, đặc biệt vì nhiều biểu thức chứa ký tự `*` có ý nghĩa cú pháp trong YAML. Việc trích dẫn giúp tránh lỗi parser không liên quan đến Spring cron.

Nếu muốn môi trường **mặc định không chạy job**, có thể không khai báo `app.jobs.cleanup.cron` và dùng default trong annotation `${app.jobs.cleanup.cron:-}`. Đây là hướng **fail-safe default disabled**. Nếu sản phẩm yêu cầu job bắt buộc chạy ở production, hãy thiết lập property rõ trong deployment và có health/monitoring kiểm chứng job đã được đăng ký; không âm thầm để thiếu cron rồi cho rằng ứng dụng đủ chức năng.

## 5. Cùng ý tưởng với application.properties

Tệp **application.properties** khi bật:

~~~properties
app.jobs.cleanup.cron=0 */15 * * * *
app.jobs.cleanup.zone=Asia/Ho_Chi_Minh
~~~

Khi tắt:

~~~properties
app.jobs.cleanup.cron=-
app.jobs.cleanup.zone=Asia/Ho_Chi_Minh
~~~

Ở file properties, giá trị cron không cần dấu ngoặc kép theo cú pháp YAML; nếu ghi literal `"..."` với dấu ngoặc kép vào properties, dấu ngoặc kép có thể trở thành **một phần của giá trị** và làm cron không hợp lệ. Đừng áp thói quen quoting của YAML sang .properties mà không kiểm tra.

### Dùng profile

Một lựa chọn:

- `application.yml` đặt `cron: "-"` làm mặc định.
- `application-prod.yml` đặt cron hợp lệ ở production.
- `application-test.yml` tiếp tục đặt `cron: "-"` để test không gọi job thật.

Ví dụ tệp **application-prod.yml**:

~~~yaml
app:
  jobs:
    cleanup:
      cron: "0 0 2 * * *"
      zone: "Asia/Ho_Chi_Minh"
~~~

Khi Spring Boot kích hoạt profile `prod`, quy tắc property-source precedence được áp dụng để chọn giá trị thích hợp. Tên file, profile, config import, system properties và environment overrides phải được quản lý theo deployment thực; đừng khẳng định một file YAML nhất định luôn có ưu tiên cao nhất trong mọi cách khởi động.

**Tính quan trọng của môi trường test:** job cron tự chạy trong integration tests có thể xóa dữ liệu fixture hoặc gọi API thật. Cấu hình `"-"` ở test profile, hoặc thay bean/service, giúp tránh tác động không chủ đích. Dù vậy đừng chỉ test khi disabled: cũng cần test cấu hình enabled để bắt lỗi biểu thức cron.

## 6. Ví dụ Spring đầy đủ ở mức ứng dụng

Tệp **SchedulingApplication.java**:

~~~java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SchedulingApplication {
    public static void main(String[] args) {
        SpringApplication.run(SchedulingApplication.class, args);
    }
}
~~~

Tệp **CleanupService.java**:

~~~java
import org.springframework.stereotype.Service;

@Service
public class CleanupService {
    public void removeExpired() {
        // Code thật cần repository, transaction, idempotency, metrics.
        System.out.println("Cleanup executed");
    }
}
~~~

Tệp **CleanupJob.java**:

~~~java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupJob {
    private final CleanupService cleanupService;

    public CleanupJob(CleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(
            cron = "${app.jobs.cleanup.cron:-}",
            zone = "${app.jobs.cleanup.zone:UTC}")
    public void run() {
        cleanupService.removeExpired();
    }
}
~~~

Ba class cần đặt trong các tệp tương ứng và package được Spring Boot component-scan. Cần ít nhất `spring-boot-starter` (đúng phiên bản Spring Boot/JDK) và dependencies ứng dụng. **Đây không phải chương trình Java SE độc lập**: cần Maven/Gradle và runtime Spring Boot. Ví dụ Spring Framework 6.x tương ứng Spring Boot 3.x trở lên nên chú ý phiên bản được dự án quản lý.

Nếu `app.jobs.cleanup.cron=-`, bean `CleanupJob` **vẫn được Spring tạo**, và method `run()` **vẫn có thể được gọi trực tiếp bởi code khác**. Chỉ **cron trigger được bỏ qua khi đăng ký lịch**, không phải class hoặc method bị vô hiệu hóa hoàn toàn. Nếu ai đó gọi `cleanupJob.run()` thủ công hoặc qua một endpoint, code vẫn thực thi.

Nếu bạn muốn tắt **cả bean hoặc chức năng** (không chỉ lịch), cần thêm feature flag/conditional bean riêng. Không đánh đồng việc tắt scheduler với việc vô hiệu hóa logic nghiệp vụ hoặc API liên quan.

## 7. Vì sao đặc biệt dùng dấu "-"?

Trong Spring Framework 5.1+, `@Scheduled` có hằng `CRON_DISABLED`:

~~~java
public static final String CRON_DISABLED = "-";
~~~

Đây là **trích ý nghĩa API**, không phải khai báo lại mã framework trong ứng dụng. Hằng được thiết kế chủ yếu cho `${...}` placeholders, cho phép bên ngoài xác định cron bị vô hiệu.

Cách diễn giải:

~~~text
@Scheduled(cron = "${app.jobs.cleanup.cron:-}")
                    |
                    v
Spring Environment resolves property
                    |
            +-------+--------+
            |                |
       value == "-"      value == valid cron
            |                |
            v                v
     skip cron trigger  register cron trigger
~~~

Spring không parse `-` như một cron sáu trường. Đây là đường xử lý riêng ở lớp đăng ký scheduled annotation. **Đừng lấy `CronExpression.parse("-")` làm cách kiểm tra giá trị disabled**: parser dành cho cron expression thật, còn sentinel cần được kiểm tra **trước** khi parse.

### Phân biệt ký tự quan trọng

- `-`: một dấu gạch ngang ASCII, chính là giá trị sentinel.
- `–`: en dash Unicode, **không phải** sentinel.
- `—`: em dash Unicode, **không phải** sentinel.
- `"-"` trong YAML: dấu ngoặc kép là cú pháp YAML để tạo giá trị thực `-`.
- `'-'` trong YAML: tương tự, giá trị thực là `-`.
- `-` với khoảng trắng đầu/cuối: đừng dựa vào trimming ngầm. Chuẩn hóa nguồn cấu hình và test chính giá trị resolve để tránh ký tự thừa.

Nếu một hệ thống cấu hình tự sinh giá trị từ UI hoặc secrets management, phải lưu đúng một ký tự ASCII `-`. Dấu `–` rất dễ xuất hiện khi copy từ tài liệu định dạng.

## 8. Đưa fixedDelay và fixedRate ra cấu hình

`@Scheduled` còn có các thuộc tính kiểu số nguyên `fixedDelay`, `fixedRate`, `initialDelay` và biến thể chuỗi:

- `fixedDelayString`: thời gian đợi giữa **lúc lần trước hoàn tất** và **lúc lần tiếp theo bắt đầu**.
- `fixedRateString`: khoảng chu kỳ tính theo mốc thực thi của lịch (không đồng nghĩa mọi lần đều đúng giờ khi executor bận).
- `initialDelayString`: khoảng thời gian trước lần đầu, với cách dùng phù hợp cho fixed-delay/fixed-rate.

Ví dụ externalized fixed-delay:

~~~java
@Scheduled(
        fixedDelayString = "${app.jobs.reconcile.delay:PT5M}",
        initialDelayString = "${app.jobs.reconcile.initial-delay:PT10S}")
public void reconcile() {
    reconciliationService.reconcile();
}
~~~

Đây là **Spring method fragment** trong một bean có `reconciliationService`. `PT5M` nghĩa là 5 phút, `PT10S` là 10 giây theo cú pháp ISO-8601 Duration. Các phiên bản Spring hiện đại còn hỗ trợ format duration đơn giản như `30s` hoặc `5m`; đối với cấu hình nhiều team, ISO-8601 thường rõ ràng và nhất quán.

Tệp properties:

~~~properties
app.jobs.reconcile.delay=PT5M
app.jobs.reconcile.initial-delay=PT10S
~~~

Fixed-rate tương tự:

~~~java
@Scheduled(fixedRateString = "${app.jobs.refresh.rate:PT30S}")
public void refresh() {
    refreshService.refresh();
}
~~~

Đây là **method fragment** với service do ứng dụng định nghĩa. Nếu muốn đơn vị thời gian cụ thể cho chuỗi số thuần, `@Scheduled(timeUnit = TimeUnit.SECONDS, fixedRateString = "...")` có thể ảnh hưởng cách đọc **giá trị không kèm đơn vị**; nhưng khi Duration được viết với đơn vị rõ ràng, `timeUnit` không được dùng để thay đổi ý nghĩa ISO/SIMPLE duration. Với cron, `timeUnit` không áp dụng.

### Cảnh báo: "-" KHÔNG tắt fixedDelayString/fixedRateString

`"-"` là **giá trị đặc biệt của cron**, không phải generic “tắt mọi @Scheduled”. Ví dụ dưới đây **không nên dùng**:

~~~java
// SAI Ý ĐỊNH: không có contract CRON_DISABLED cho fixedDelayString
@Scheduled(fixedDelayString = "${app.jobs.reconcile.delay:-}")
public void reconcile() {
}
~~~

Nếu property thiếu và resolve thành `-`, Spring cần diễn giải nó như một thời lượng fixed-delay hợp lệ, nhưng `-` không phải duration; ứng dụng có thể lỗi khi đăng ký lịch. Tương tự với `fixedRateString`. Không tự đặt `-1` và coi đó là “sentinel disable” được tài liệu hóa cho các biến thể String.

**Muốn bật/tắt một job fixed-delay/fixed-rate**, có hai phương án đáng cân nhắc: (1) dùng **cron** với giá trị `-` nếu semantics nghiệp vụ phù hợp, hoặc (2) sử dụng **feature flag để không tạo bean scheduled** hay đăng ký chương trình khi cần. Phần tiếp theo minh họa phương án (2).

## 9. Điều kiện tạo bean: cách khác để tắt fixed-delay/fixed-rate

Trong Spring Boot, có thể dùng `@ConditionalOnProperty` ở **class/bean definition** của component chứa scheduled method.

Tệp **ReconcileJob.java**:

~~~java
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.jobs.reconcile",
        name = "enabled",
        havingValue = "true")
public class ReconcileJob {
    private final ReconciliationService service;

    public ReconcileJob(ReconciliationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.jobs.reconcile.delay:PT5M}")
    public void run() {
        service.reconcile();
    }
}
~~~

Đây là **Spring Boot component**, cần interface/class `ReconciliationService` của ứng dụng. Khi `app.jobs.reconcile.enabled=true`, bean được đăng ký và Spring nhận diện `@Scheduled`. Khi property không tồn tại hoặc không có giá trị `true` theo condition, bean này không được tạo và không có lịch từ bean đó.

Cấu hình:

~~~yaml
app:
  jobs:
    reconcile:
      enabled: false
      delay: "PT5M"
~~~

Phân biệt:

| Cách tắt | Ảnh hưởng |
| --- | --- |
| `@Scheduled(cron=... )` resolve thành `-` | Bean còn tồn tại, nhưng cron trigger cụ thể không đăng ký |
| `@ConditionalOnProperty` trên component | Bean không được tạo nếu condition không khớp; toàn bộ chức năng bean đó vắng mặt |
| `@EnableScheduling` không bật | Cả cơ chế `@Scheduled` của context không được khởi tạo theo cấu hình thông thường |
| `ScheduledFuture.cancel(...)` ở runtime | Chỉ yêu cầu hủy lịch cụ thể, không nhất thiết dừng body đang chạy |
| Tắt JVM/application | Không có scheduled execution, nhưng còn phụ thuộc nơi job được lưu/điều phối |

Không gắn `@ConditionalOnProperty` trực tiếp lên một method bất kỳ trong bean rồi mặc định scheduler bỏ qua method đó. Condition kiểm soát bean definition/configuration. Để condition có ý nghĩa, hãy đặt trên component class hoặc `@Bean` definition thích hợp.

### Tránh thêm quá nhiều flag chồng nhau

Nếu cùng một job có cả `enabled`, `cron=-`, `featureX=false` và `profile` thì việc nó thực sự chạy hay không trở nên khó kiểm tra. Nên chọn **một nguồn quyết định rõ** cho mục tiêu chính, hoặc ghi tài liệu precedence. Với bài toán tắt cron qua cấu hình, `"-"` là lựa chọn đơn giản và đã được Spring hỗ trợ chính thức.

## 10. Cú pháp cron cần xác minh trước khi tắt hoặc bật

Spring cron có **sáu trường**: `second minute hour day-of-month month day-of-week`, ví dụ `0 */15 * * * *` là giây 0 mỗi 15 phút. Cú pháp Unix năm trường không được dùng nguyên dạng. `zone = "Asia/Ho_Chi_Minh"` diễn giải các mốc cron theo giờ địa phương; nếu không chỉ rõ zone, môi trường scheduler có thể cho kết quả khác nhau. Với vùng có DST phải kiểm tra giờ thiếu hoặc lặp.

Khác với biểu thức sáu trường, dấu `-` (`Scheduled.CRON_DISABLED`) là **sentinel** Spring dùng để **không đăng ký cron trigger**, không được đưa vào `CronExpression.parse("-")`. Trong cấu hình, validate `-` trước khi parse các cron values khác. `@Scheduled(cron = "${app.jobs.cleanup.cron:-}")` tắt khi property vắng mặt, nhưng không tắt bean hay các declarations khác trên method.


## 11. Startup và thay đổi cấu hình khi runtime

Với `@Scheduled` thông thường, Spring resolve annotation và đăng ký lịch **khi ApplicationContext được thiết lập**. Thay đổi file `application.yml` trên đĩa trong khi JVM vẫn chạy **không mặc nhiên** khiến Spring hủy lịch cũ và tạo lịch mới.

Kịch bản:

~~~text
10:00 ApplicationContext start
      app.jobs.cleanup.cron = "0 */15 * * * *"
      -> Spring đăng ký cron mỗi 15 phút

10:05 Operator sửa file thành "-"
      -> nếu không có cơ chế refresh/reschedule thích hợp,
         lịch đã đăng ký vẫn đang hoạt động

10:10 ApplicationContext được khởi tạo lại với config "-"
      -> khi tạo registration mới, trigger bị bỏ qua
~~~

Ngay cả khi hệ thống có **Spring Cloud Config** hoặc cơ chế refresh property source, **không được mặc định** rằng `@Scheduled` tự được dỡ/đăng ký lại khi property thay đổi. Nó phụ thuộc lifecycle của bean, scheduler và việc tái tạo registration; phải test cụ thể.

### Thay đổi an toàn trong triển khai

Nếu yêu cầu chỉ là “đổi cron khi deploy”, dùng cấu hình ngoài mã và **rolling restart** / khởi tạo context mới theo quy trình deployment. Nếu yêu cầu “đổi trong vài giây mà không restart”, hãy xây **dynamic scheduling** rõ ràng:

- Lưu cấu hình cron/enablement ở nơi có version.
- Giữ `ScheduledFuture<?>` đại diện cho lịch được đăng ký.
- Khi có thay đổi hợp lệ, cancel lịch cũ và register lịch mới qua `TaskScheduler`/`CronTrigger`.
- Xử lý race giữa một lần chạy đang diễn ra và thao tác đổi lịch.
- Kiểm tra cả các instance khác nếu ứng dụng chạy nhiều replica.
- Ghi log thay đổi version và trạng thái registration, bảo đảm rollback được.

Đừng chỉ dùng `@RefreshScope` theo cảm tính để khẳng định đã có dynamic rescheduling. Hành vi destroy/recreate scheduled bean và queue của scheduler phải được kiểm chứng trên đúng Spring version.

## 12. Đăng ký động với TaskScheduler khi thực sự cần hot change

Spring cung cấp `TaskScheduler` để chủ động đăng ký một lịch. Mẫu sau chỉ minh họa một **in-process dynamic schedule** trong một bean, không có store bền vững/coordination toàn cụm.

~~~java
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@Component
public class DynamicCleanupScheduler {
    private final TaskScheduler taskScheduler;
    private final CleanupService cleanupService;
    private ScheduledFuture<?> scheduled;

    public DynamicCleanupScheduler(
            TaskScheduler taskScheduler,
            CleanupService cleanupService) {
        this.taskScheduler = taskScheduler;
        this.cleanupService = cleanupService;
    }

    public synchronized void changeCron(String cron) {
        // Validate trước khi hủy lịch cũ để tránh mất lịch khi config sai.
        if (!Scheduled.CRON_DISABLED.equals(cron)) {
            CronExpression.parse(cron);
        }

        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }

        if (!Scheduled.CRON_DISABLED.equals(cron)) {
            scheduled = taskScheduler.schedule(
                    cleanupService::removeExpired, new CronTrigger(cron));
        }
    }
}
~~~

Đây là **Spring component minh họa**, không phải giải pháp production hoàn chỉnh. Những điều cần hoàn thiện trước production:

1. **Startup registration:** chủ động gọi `changeCron` từ lifecycle hook phù hợp sau khi bean sẵn sàng; không được mong bean tự có lịch chỉ vì tồn tại method trên.
2. **Transaction:** nếu cleanup cần transaction, gọi một service proxy có `@Transactional` thay vì tự tạo transaction không rõ ràng trong lambda.
3. **Concurrency:** `synchronized` chỉ tuần tự hóa các lời gọi `changeCron` trên **một instance**. Nó không đồng bộ với body đang chạy, không dùng cho cluster.
4. **Cancellation:** `cancel(false)` không ngắt body đã bắt đầu. Nếu một lượt cleanup đang chạy, nó có thể tiếp tục sau khi lịch bị tắt.
5. **Cron/timezone:** ví dụ dùng timezone mặc định; trong ứng dụng thật truyền ZoneId/TimeZone vào trigger phù hợp phiên bản API.
6. **Lifecycle:** khi context đóng, quản lý cancellation/cleanup theo scheduler và không tự shutdown resource do container sở hữu.
7. **Registration failure:** sau khi hủy lịch cũ, lỗi scheduler/rejection khi đăng ký lịch mới có thể để ứng dụng không có lịch. Cần trạng thái/lỗi rõ cho operator.
8. **Idempotency:** một số lần chạy có thể chồng lấn khi đổi cron/restart; có state/lock phù hợp.

Nếu chỉ cần tắt bằng config ở mỗi lần deployment, mẫu `@Scheduled(cron = "${...:-}")` đơn giản hơn và ít rủi ro hơn đáng kể.

## 13. Ví dụ Java 17+ độc lập: mô phỏng lựa chọn bật/tắt từ cấu hình

Java SE **không có cron scheduler chuẩn tích hợp trong `java.util.concurrent`**, và code dưới đây **không mô phỏng parser của Spring**. Ví dụ chỉ minh họa phần quyết định quan trọng: giá trị cấu hình là cron thật hay sentinel `"-"`, default khi thiếu property, và phân biệt với ISO Duration của fixed-delay.

Tệp **CronSettingDemo.java**:

~~~java
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Properties;

public final class CronSettingDemo {
    private static final String KEY = "app.jobs.cleanup.cron";
    private static final String DISABLED = "-";

    private static String resolve(Properties props) {
        String value = props.getProperty(KEY, DISABLED);
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Cron setting must be '-' or a non-blank expression");
        }
        return value;
    }

    private static String status(Properties props) {
        String cron = resolve(props);
        if (DISABLED.equals(cron)) {
            return "disabled";
        }
        return "configured:" + cron;
    }

    public static void main(String[] args) {
        Properties enabled = new Properties();
        enabled.setProperty(KEY, "0 */15 * * * *");

        Properties disabled = new Properties();
        disabled.setProperty(KEY, DISABLED);

        Properties missing = new Properties();

        System.out.println("enabled=" + status(enabled));
        System.out.println("disabled=" + status(disabled));
        System.out.println("missing=" + status(missing));
        System.out.println("delaySeconds="
                + Duration.parse("PT30S").toSeconds());

        try {
            Duration.parse(DISABLED);
            System.out.println("dashIsDuration=true");
        } catch (DateTimeParseException ex) {
            System.out.println("dashIsDuration=false");
        }
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac CronSettingDemo.java
java CronSettingDemo
~~~

Stdout xác định:

~~~text
enabled=configured:0 */15 * * * *
disabled=disabled
missing=disabled
delaySeconds=30
dashIsDuration=false
~~~

**Đọc đúng ý nghĩa ví dụ:**

- `enabled` có cron thật; ở Spring, một bean `@Scheduled(cron = ...)` được đăng ký lịch nếu cron parse hợp lệ.
- `disabled` có literal `-`; trong Spring, `@Scheduled` nhận diện hằng `CRON_DISABLED` và không tạo cron trigger.
- `missing` không có property; trong **mô hình Java này**, `Properties.getProperty(KEY, DISABLED)` tái hiện lựa chọn default, tương tự ý định của `${app.jobs.cleanup.cron:-}`.
- `Duration.parse("PT30S")` là API Java SE, trả 30 giây; `Duration.parse("-")` không hợp lệ. Spring có parsing duration riêng rộng hơn, nhưng `-` **không phải sentinel documented cho fixedDelayString**.

**Giới hạn rõ ràng:** `CronSettingDemo` không có Spring ApplicationContext, không đọc YAML, không tạo `TaskScheduler` và **không chứng minh** method `@Scheduled` chạy hoặc tắt thật. Đó là nhiệm vụ của integration tests ở phần tiếp theo.

## 14. Kiểm thử tích hợp: disable thật và cron hợp lệ

Một test đáng tin không chỉ kiểm tra property có giá trị `-` mà còn xác nhận **Spring không đăng ký ScheduledTask** cho bean đó trong context có `@EnableScheduling`.

### Test case 1: không có property

- Khởi động **ApplicationContext cô lập** chứa duy nhất một `@Scheduled(cron = "${app.jobs.cleanup.cron:-}")` để tránh lẫn các task khác.
- Không cung cấp property `app.jobs.cleanup.cron`.
- Sau khi context refresh, kiểm tra `ScheduledAnnotationBeanPostProcessor.getScheduledTasks()`: không có task được đăng ký từ method đó.
- Xác minh bean vẫn tồn tại trong context; tắt cron không phải xóa bean.

### Test case 2: explicit "-"

- Khởi động context mới với `app.jobs.cleanup.cron=-`.
- Xác nhận phương thức không tạo cron task giống trường hợp property vắng mặt.
- Không cần chờ 15 phút rồi suy đoán job đã tắt. **Kiểm tra registration** nhanh và chắc hơn.

### Test case 3: cron hợp lệ

- Context mới với `app.jobs.cleanup.cron="0 0 0 1 1 *"` (mỗi năm ngày 1 tháng 1 vào 00:00 theo zone).
- Xác minh Spring đăng ký đúng một ScheduledTask cho method trong test context cô lập.
- Không cần chờ đến ngày 1/1. Đóng context sau assertion để hủy các lịch của test.

### Test case 4: cron không hợp lệ

- Context mới có `app.jobs.cleanup.cron="bad-value"`.
- Kỳ vọng quá trình xử lý scheduled annotation báo lỗi/không thể đăng ký; test fail-fast nếu production cần cron hợp lệ.
- Đừng nuốt lỗi và âm thầm đổi sang một chu kỳ khác không được operator chấp thuận.

### JUnit/Spring integration test fragment

Một ví dụ kiểm tra bằng `ScheduledAnnotationBeanPostProcessor`:

~~~java
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisabledCronIntegrationTest {

    @Test
    void explicitDashDisablesCronRegistration() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource(
                            "test", Map.of("app.jobs.cleanup.cron", "-")));

            context.register(
                    ManagedTestSchedulingConfig.class,
                    CleanupJob.class,
                    CleanupService.class);
            context.refresh();

            var processor = context.getBean(
                    ScheduledAnnotationBeanPostProcessor.class);
            assertEquals(0, processor.getScheduledTasks().size());
        }
    }
}
~~~

**Đây là fragment test tích hợp cần các class Spring example trong cùng test source set**, cùng `ManagedTestSchedulingConfig` bên dưới. Trong bối cảnh này, `CleanupJob` và `CleanupService` là class ví dụ phần 6. Sử dụng một Spring context tối giản để tránh task khác xuất hiện trong `getScheduledTasks()`; assertion `0` chỉ hợp lệ khi test context thực sự không có lịch khác.

Tệp **ManagedTestSchedulingConfig.java** dành cho test:

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class ManagedTestSchedulingConfig {
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("test-cron-");
        return scheduler;
    }
}
~~~

Cần dependency JUnit 5 và Spring context/test hoặc Spring Boot test tương ứng. Tên `ManagedTestSchedulingConfig` chỉ là tên của cấu hình **test scheduler do Spring quản lý**, **không phải** `DefaultManagedTaskScheduler` của Jakarta EE. Sau khi test kết thúc, `AnnotationConfigApplicationContext.close()` hủy bean/schedule của context.

Để test trường hợp enabled, tạo context **riêng** với cron hợp lệ rồi assert có một task được đăng ký. Khi muốn test chính xác biểu thức cron, dùng `CronExpression.parse(cron)` trên giá trị **khác `-`**; parser sẽ kiểm tra cú pháp, nhưng xác minh registration vẫn cần context.

## 15. Kiểm tra cron trước khi deploy

Một lớp validation/health check có thể từ chối config sai thay vì để task tự chạy sai lịch. Nguyên tắc:

~~~java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

public final class CronSettingValidator {
    public static void validate(String cron) {
        if (Scheduled.CRON_DISABLED.equals(cron)) {
            return;
        }
        CronExpression.parse(cron);
    }
}
~~~

Đây là **utility Spring class** cần `spring-context` (và phụ thuộc phù hợp), không phải chương trình Java SE độc lập. Phần validation này không mặc định giải quyết timezone, DST hoặc business requirement, nhưng giúp phát hiện cron không hợp lệ.

Đừng sửa giá trị `-` thành `0 0 0 * * *` để parser chấp nhận: như thế sẽ **bật job hằng ngày**, trái với ý định tắt. Ngược lại, đừng nhận `null`/`""` như tín hiệu disable nếu application contract chỉ định chính xác `-`.

Trong CI/CD có thể thêm test application context với từng profile được hỗ trợ, kiểm tra resolved property và task registration. Những lỗi kiểu dấu ngoặc kép trong properties, Cron năm trường, nhầm timezone và missing property sẽ được phát hiện trước production.

## 16. Không trộn cron và fixedDelay trong cùng một @Scheduled

Theo Javadoc `@Scheduled`, một khai báo periodic task phải xác định **một** kiểu trigger chủ đạo:

~~~java
@Scheduled(cron = "${app.jobs.cleanup.cron:-}")
public void cleanup() {
}
~~~

hoặc:

~~~java
@Scheduled(fixedDelayString = "${app.jobs.cleanup.delay:PT5M}")
public void cleanup() {
}
~~~

Không viết như sau:

~~~java
// SAI: một annotation định nghĩa hai loại trigger đồng thời
@Scheduled(
        cron = "${app.jobs.cleanup.cron:-}",
        fixedDelayString = "${app.jobs.cleanup.delay:PT5M}")
public void cleanup() {
}
~~~

Đây là **fragment minh họa lỗi cấu hình**; nó không nên được triển khai. Nếu muốn dùng cron ở một môi trường và fixed-delay ở môi trường khác, hãy tạo configuration/bean registration có điều kiện, hoặc sử dụng programmatic scheduling rõ ràng.

Ngoài ra, `@Scheduled` là **repeatable annotation**: một method có thể mang nhiều khai báo schedule riêng và mỗi khai báo được xử lý độc lập. Nếu một method có một cron disabled bằng `-` nhưng thêm một `@Scheduled(fixedRateString=...)` khác, **method vẫn có thể chạy theo fixed-rate**. Vì vậy tắt **một trigger** không đồng nghĩa tắt **mọi đường thực thi của method**.

## 17. Thiết kế feature flag và quyền vận hành

Một cơ chế tắt lịch nên có contract vận hành rõ:

| Câu hỏi | Quyết định cần công bố |
| --- | --- |
| Property vắng mặt | Tắt mặc định hay dùng lịch mặc định? |
| Property có giá trị `-` | Chỉ disable cron trigger nào? |
| Cron sai cú pháp | Fail startup hay báo lỗi triển khai? |
| Thay config khi JVM đang chạy | Có restart/refresh/reschedule thật không? |
| Tắt cron khi job đang chạy | Có để lượt hiện tại hoàn tất hay yêu cầu cancel? |
| Multi-instance | Mỗi instance tự đăng ký lịch hay có điều phối toàn cụm? |
| Có API gọi method thủ công | Tắt cron có cần cấm API này không? |
| Job cần audit | Ai thay đổi schedule, thời điểm nào, giá trị cũ/mới? |
| Job có thể xóa dữ liệu | Có test và guardrails trước khi bật cron ở production không? |
| Job quan trọng với SLA | Có health alert khi cron bị tắt quá lâu không? |

**Khuyến nghị thực tế:** nếu job quan trọng, một schedule disabled **không thể chỉ im lặng**. Theo dõi active/disabled state theo config đã resolve và cảnh báo khi lịch đáng lẽ phải chạy mà bị tắt ở production. Ngược lại, với test/dev, disabled có thể là expected state; alert phải theo môi trường.

## 18. Các tình huống khi đổi config cần cẩn trọng

### Tình huống A: đổi 15 phút thành 1 phút

Ban đầu `0 */15 * * * *`; muốn dùng `0 * * * * *`. Nếu chỉ thay property file trên một pod đang chạy, job có thể vẫn giữ lịch cũ cho đến khi ApplicationContext được khởi tạo lại. Cần ghi rõ cách deployment áp config và xác nhận schedule mới sau restart.

### Tình huống B: tắt lịch nhưng task đã chạy

Đổi cron thành `-` khi ApplicationContext reload sẽ ngăn **những lần đăng ký lịch tương lai** từ cấu hình mới. Nó **không rollback** side effect đã ghi và không bảo đảm một task đang chạy lập tức dừng. Nếu cần hủy một lần thực thi, thiết kế cancellation token/interrupt, deadline và trạng thái nghiệp vụ riêng.

### Tình huống C: tắt lịch test để tránh xóa dữ liệu

Khai báo `app.jobs.cleanup.cron=-` trong test profile hoặc đưa default `-` vào annotation; integration test không tự tạo cron cleanup. Đồng thời mock/replace service gọi API ngoài để test an toàn nếu một test trực tiếp gọi method `run()`.

### Tình huống D: chỉ chạy cron trên một instance

Nếu cả ba replicas đều chạy với cron hợp lệ, mỗi instance có thể đăng ký lịch riêng. Đặt `-` trên hai instance và cron thật trên một instance có thể là cấu hình vận hành tạm, nhưng có rủi ro khi instance bị thay thế hoặc autoscaling. Để điều phối lâu dài, dùng leader election/distributed lock/lease hoặc persistent job scheduler có cluster semantics.

### Tình huống E: config trống do biến môi trường

Nếu operator đặt `APP_JOBS_CLEANUP_CRON=""`, phải biết Spring Boot/environment property source sẽ resolve ra giá trị gì. Không giả định default `${...:-}` luôn kích hoạt với property rỗng. Hãy chọn một giá trị rõ như `-`, đồng thời validate config trong CI và startup.

### Tình huống F: cron khác giờ sau khi đổi container image

Nếu `zone` không chỉ định, timezone/clock của scheduler có thể khác giữa image/server. Đặt `app.jobs.cleanup.zone=Asia/Ho_Chi_Minh` hoặc zone theo business requirement; log thời điểm planned/actual theo Instant để so sánh.

## 19. Disable cron không giải quyết completion, concurrency và durability

Đổi `cron` thành `-` chỉ ngừng đăng ký trigger trong context được cấu hình; nó không hủy side effect đã commit, không tự cancel callback đang chạy, không cấm gọi business method thủ công và không tắt một `@Scheduled` declaration khác. Runtime config chưa được reschedule còn có thể giữ lịch cũ sống.

Khi bật lại, vẫn phải đo lateness, execution duration, lỗi từ `ErrorHandler`, timeout của DB/HTTP, transaction trong service worker, các registrations trên nhiều replicas và idempotency. Nếu scheduled method chuyển việc sang `@Async`, completion và rejection của worker phải theo dõi riêng; scheduler method return chưa chứng minh business đã xong.

Nếu job không được mất trong downtime, dùng durable job store hoặc queue, không dựa vào một cron property trong ứng dụng.


## 20. Quan sát schedule đang hoạt động

Một số số đo hữu ích:

| Tín hiệu | Mục đích |
| --- | --- |
| `scheduled_job_configured` (0/1) | Job được cấu hình để đăng ký trigger hay bị tắt |
| `scheduled_job_last_start` | Lần gần nhất body thật sự bắt đầu |
| `scheduled_job_last_success` | Lần gần nhất xử lý thành công |
| `scheduled_job_last_failure` | Lần gần nhất xử lý thất bại |
| `scheduled_job_duration` | Độ dài mỗi lần chạy |
| `scheduled_job_start_lag` | Độ trễ so với planned fire time, nếu có thể đo |
| `scheduled_job_skipped_or_disabled` | Phân biệt tắt bằng config và bỏ qua do logic |
| `scheduled_job_schedule_revision` | Revision/version cấu hình đã được đăng ký |
| `scheduled_job_active_instances` | Có bao nhiêu instance đang đăng ký job |

Đừng ghi cron expression như label thay đổi liên tục cho mọi sample metric nếu làm tăng cardinality không cần thiết; log cấu hình đã resolve ở startup và xuất một trạng thái đơn giản cho health/monitoring.

Sự kiện “configuration changed” và “job actually rescheduled” cũng phải tách. Một config server báo đã phát hành `-` không chứng minh một pod cũ đã bỏ cron trigger. Cần đo trạng thái **đăng ký thực tế** hoặc sự kiện lifecycle của scheduler sau reload.

## 21. Những lỗi phổ biến

1. **Viết cứng cron trong code** dù các môi trường cần các chu kỳ khác nhau.
2. **Quên `@EnableScheduling`**, chỉ đặt `@Scheduled` và property rồi chờ job tự chạy.
3. **Nhầm giá trị `-` với cron năm/sáu trường**, rồi cố parse bằng `CronExpression.parse("-")`.
4. **Viết `${app.jobs.cleanup.cron}` nhưng không cung cấp property** và không có default khi môi trường muốn tắt.
5. **Dùng giá trị `-` cho `fixedDelayString` hoặc `fixedRateString`.** Không có contract vô hiệu hóa như cron.
6. **Dùng default rỗng thay vì dấu `-`** mà không test behavior khi đăng ký task.
7. **Nhầm thuộc tính `zone` với `timeUnit`.** Zone ảnh hưởng cron; timeUnit áp vào giá trị thời gian fixedRate/fixedDelay/initialDelay khi có ý nghĩa.
8. **Dùng cron Unix năm trường trong Spring**, thiếu trường giây.
9. **Để cron không có dấu ngoặc kép trong YAML** rồi gặp parser lỗi ký tự `*`.
10. **Đặt dấu ngoặc kép vào value của .properties** rồi vô tình biến quote thành một phần cron.
11. **Dùng en dash/em dash thay dấu `-` ASCII** do copy/paste.
12. **Tin rằng thay application.yml trên đĩa tự cập nhật cron đang chạy.** Registration không tự reschedule.
13. **Tin rằng @RefreshScope luôn đủ** để dynamic schedule, không kiểm thử cancel/re-registration.
14. **Tắt cron rồi nghĩ tất cả lời gọi method đều bị cấm**; method vẫn được gọi thủ công.
15. **Dùng nhiều @Scheduled trên cùng method** rồi nghĩ tắt một cron là tắt hết mọi trigger.
16. **Dùng @ConditionalOnProperty ngay trên một method @Scheduled** rồi cho rằng Spring tự bỏ method; cần đặt condition ở bean registration thích hợp.
17. **Cho rằng Spring Boot test tự vô hiệu hóa mọi @Scheduled**. Cần profile/config/test context được kiểm soát.
18. **Chỉ test bằng Thread.sleep rồi nói job đã disabled**; kiểm tra registration trực tiếp.
19. **Không test đường enabled**; cron sai cú pháp có thể chỉ lộ ở production.
20. **Không ghi timezone**, job chạy khác giờ giữa CI và production.
21. **Không xét DST**, job có thể bị bỏ hoặc lặp tại giờ chuyển đồng hồ.
22. **Cho rằng một @Scheduled job chạy đúng một lần toàn cluster**. Mỗi context thường tự đăng ký trigger.
23. **Tắt lịch khi task đang chạy rồi hứa task dừng ngay**; cancellation của body là vấn đề riêng.
24. **Dựa vào disable cron để bảo đảm side effect không xảy ra** dù có API khác gọi cùng service.
25. **Tự bật cron liên tục ở production** mà không đo queue, run duration, error và idempotency.
26. **Cho rằng cron thuộc tính luôn chỉ có một lịch** dù method mang repeatable annotations.
27. **Cấu hình scheduler bean không được chọn** và không xác minh `@Scheduled` dùng đúng executor.
28. **Biến lỗi cron thành silently disabled** mà không ghi cảnh báo khi job bắt buộc phải chạy.
29. **Đọc fixedDelay như fixedRate**, dẫn đến kỳ vọng sai thời gian giữa các lượt.
30. **Dùng `@Scheduled` làm hàng đợi bền vững** dù app restart có thể mất run/mốc deadline.

## 22. Checklist triển khai

Trước khi phát hành, hãy kiểm tra:

1. Method nằm trong bean Spring được quản lý, có `@Scheduled` với một trigger rõ ràng.
2. `@EnableScheduling` hoặc cơ chế tương đương được bật.
3. Cron lấy từ property có default/required behavior đã thống nhất.
4. Nếu cần cho phép vô hiệu hóa, dùng đúng literal `-` với cron.
5. YAML/properties không có dấu quote sai, dấu Unicode hoặc khoảng trắng không mong muốn.
6. Cron có sáu trường và timezone phù hợp với business requirement.
7. Test case **enabled**, **disabled**, **missing property** và **invalid cron**.
8. Không dùng dấu `-` làm giá trị fixedDelayString/fixedRateString.
9. Nếu fixedDelay cần feature toggle, dùng condition cho bean hoặc programmatic registration.
10. Nếu config có thể đổi khi runtime, biết cách hủy/re-register task và có đường kiểm thử race.
11. Multi-instance có cơ chế tránh chạy trùng nếu job không idempotent.
12. Health/metrics nói rõ trạng thái enabled/disabled và lần chạy thành công cuối.
13. Transaction và I/O timeout ở job body được quản lý độc lập với cron.
14. Job cần bền vững qua restart đã có persistent store/queue phù hợp.
15. Logging startup thể hiện cron/zone đã resolve ở mức đủ quan sát, không lộ secrets.
16. Môi trường production có policy thay đổi schedule và rollback rõ ràng.

## 23. Câu hỏi tự kiểm tra

1. Hằng `Scheduled.CRON_DISABLED` có giá trị chính xác là gì?
2. `${app.jobs.cleanup.cron:-}` khác `${app.jobs.cleanup.cron}` thế nào khi property không tồn tại?
3. Nếu property bằng `-`, Spring bỏ cron trigger hay xóa bean?
4. Một method được tắt cron có còn chạy nếu bean khác gọi trực tiếp vào method không?
5. `CronExpression.parse("-")` có phải một phép kiểm tra hợp lệ cho disabled cron không?
6. Có nên đặt `fixedDelayString = "${app.jobs.delay:-}"` để tắt fixed-delay không? Tại sao?
7. Muốn tắt bean fixed-delay trong Spring Boot theo cấu hình, `@ConditionalOnProperty` nên đặt ở đâu?
8. Chu kỳ `0 */15 * * * *` có bao nhiêu trường, chạy vào thời điểm nào?
9. Thuộc tính `zone` ảnh hưởng gì nếu cron là 02:00 mỗi ngày?
10. Vì sao config YAML cần đặt cron trong dấu ngoặc kép?
11. Vì sao sửa cron property trong file chưa đủ để đổi lịch đã đăng ký trong JVM đang chạy?
12. Nếu một method mang hai `@Scheduled`, tắt cron thứ nhất có vô hiệu hóa trigger thứ hai không?
13. Làm sao test disabled mà không phải chờ hàng phút?
14. Vì sao test disabled và enabled cần ApplicationContext riêng trong integration test?
15. Vì sao job vẫn có thể bị chạy trùng trên nhiều replicas ngay cả khi cron config giống nhau?
16. Nếu cron bị tắt trong khi một lần cleanup đang chạy, có được khẳng định cleanup đã bị hủy không?
17. Trường hợp nào cần persistent scheduler/job store thay vì chỉ `@Scheduled`?
18. Tại sao logging config đã resolve hữu ích hơn chỉ logging source file name?

## 24. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — @Scheduled Javadoc, CRON_DISABLED và duration strings](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — @EnableScheduling Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html)
- [Spring Framework — CronExpression Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronExpression.html)
- [Spring Framework — ScheduledAnnotationBeanPostProcessor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/ScheduledAnnotationBeanPostProcessor.html)
- [Spring Framework — TaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/TaskScheduler.html)
- [Spring Boot — Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Spring Boot — ConditionalOnProperty Javadoc](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/autoconfigure/condition/ConditionalOnProperty.html)
- [Java SE 17 — Duration Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Duration.html)

**Ghi nhớ:** đưa chu kỳ của `@Scheduled` ra cấu hình bằng các thuộc tính chuỗi/placeholder. Với cron, literal **`-`** (hằng **`Scheduled.CRON_DISABLED`**) là **cơ chế chính thức để không đăng ký trigger**, nhất là khi dùng default `${app.jobs.cleanup.cron:-}`. Dấu `-` không phải cơ chế tắt `fixedDelayString`/`fixedRateString`; nếu cần tắt hai loại đó, hãy dùng condition ở cấp bean hoặc tự quản lý registration. Thay config lúc runtime không tự đảm bảo lịch đã được đăng ký được cập nhật; hãy kiểm thử context, lifecycle, timezone, concurrency và nhu cầu durable scheduling.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-tasks-012">Phối hợp @Scheduled với @Async làm thay đổi thời điểm được coi là đã hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# Phối hợp @Scheduled với @Async làm thay đổi thời điểm được coi là đã hoàn tất

## 1. Mục tiêu: phân biệt “đã kích hoạt”, “đã bàn giao” và “đã làm xong”

Một tác vụ Spring chạy theo lịch thường có hai trách nhiệm:

1. **Kích hoạt đúng thời điểm** bằng `@Scheduled` và `TaskScheduler`.
2. **Thực hiện công việc nghiệp vụ** bằng logic của method, có thể được chuyển sang một `TaskExecutor` nhờ `@Async`.

Nếu scheduler **trực tiếp chạy toàn bộ logic** trong một method đồng bộ, thời điểm method trả về có thể là một tín hiệu hợp lý rằng logic trong method đã kết thúc. Nhưng khi method theo lịch chỉ **submit một method `@Async` rồi trả về**, scheduler có thể coi **lần invocation theo lịch** đã xong **trước khi job nghiệp vụ kết thúc**.

Sự khác biệt này ảnh hưởng trực tiếp tới `fixedDelay`, khả năng chồng lấn (overlap), lỗi bị bỏ qua, shutdown và cách hiểu “job đã hoàn thành”.

**Sau bài học, bạn cần:**

- Giải thích `@EnableScheduling` và `@EnableAsync` kích hoạt **hai hạ tầng khác nhau**.
- Phân biệt **scheduled invocation**, **async handoff**, **actual business completion** và **durable side-effect completion**.
- Phân tích chính xác `fixedDelay` khi scheduler method nhanh chóng bàn giao task cho `@Async`.
- Phân biệt `fixedRate`/cron với `fixedDelay`, không suy diễn mọi lịch đều đợi nghiệp vụ xong.
- Hiểu đường gọi qua Spring proxy, self-invocation và vai trò của hai executor.
- Tạo ví dụ Java 17+ kiểm chứng bằng latch rằng lần trigger tiếp theo đến khi worker cũ vẫn bị giữ.
- Biết khi nào phải **đợi future**, **đổi mô hình scheduling**, **chống overlap**, hoặc **lưu trạng thái job bền vững**.
- Xử lý exception, timeout, cancellation, transaction context, lifecycle và multi-replica.

**Phạm vi:** bài tập trung vào ý nghĩa “hoàn tất” khi kết hợp `@Scheduled` với `@Async` trong Spring Framework concurrency. Nó không thay thế bài hướng dẫn tổng quát cron hay toàn bộ API `CompletableFuture`.

## 2. Hai annotation, hai vai trò

### 2.1. @Scheduled: khi nào gọi một method?

Một method đánh dấu `@Scheduled` thường là method Spring-managed bean, **không có tham số**, được Spring scheduling infrastructure đăng ký khi `@EnableScheduling` có hiệu lực. Ví dụ:

```java
@Component
class PeriodicJob {
    @Scheduled(fixedDelay = 5_000)
    public void run() {
        processSynchronously();
    }
}
```

Đây là **fragment Spring**, cần class/import và `processSynchronously()` trong project. `fixedDelay = 5_000` có nghĩa scheduler dùng khoảng nghỉ 5 giây **sau khi invocation được nó theo dõi kết thúc** trước lần kích hoạt tiếp theo. Nếu `processSynchronously()` chạy 10 giây và không tự bàn giao thêm việc ra ngoài, scheduler nhận ra lần invocation đã hoàn tất sau khoảng 10 giây, rồi mới tính 5 giây delay.

### 2.2. @Async: invocation được chuyển sang executor nào?

`@Async` là advice cho method **được gọi qua Spring proxy** trong cấu hình proxy mặc định; hạ tầng thường được bật bằng `@EnableAsync`.

```java
@Service
class WorkService {
    @Async("workExecutor")
    public CompletableFuture<Void> processAsync() {
        doBusinessWork();
        return CompletableFuture.completedFuture(null);
    }
}
```

Đây là **fragment Spring**, cần imports và `doBusinessWork()`. Khi một bean khác gọi `workService.processAsync()` qua proxy, interceptor có thể submit việc vào `workExecutor` rồi trả một `CompletableFuture` cho caller. Method gốc thực sự chạy trên worker sau đó. `@Async` không có nghĩa source code method luôn non-blocking; nó có thể làm blocking I/O bên trong worker.

### 2.3. @Scheduled + @Async không tạo một “future mà scheduler tự đợi”

Khi scheduler gọi method `tick()` rồi method chỉ gọi một worker async:

```java
@Component
class PollSchedule {
    private final WorkService workService;

    PollSchedule(WorkService workService) {
        this.workService = workService;
    }

    @Scheduled(fixedDelay = 5_000)
    public void tick() {
        workService.processAsync(); // Bàn giao và KHÔNG đợi.
    }
}
```

Đây là **fragment**. Scheduler theo dõi **lời gọi `tick()`**. Khi `tick()` trả về sau khi submit, scheduler có thể bắt đầu tính khoảng fixed-delay tiếp theo; nó không mặc định theo dõi `CompletableFuture` mà `processAsync()` đã trả.

Vì thế, **fixed-delay vẫn đúng với invocation được scheduler quan sát**, nhưng có thể **không còn biểu diễn “5 giây kể từ khi công việc thật đã làm xong”**.

## 3. Ba mốc hoàn tất phải phân biệt

Một trigger có thể đi qua những mốc:

```text
time →
t0              t1              t2                 t3
|---------------|---------------|------------------|
scheduler tick  async submit     method tick()      worker hoàn tất
                                đã return          DB/email/output
|____________________|                              |
  trigger / handoff                                  business completion

Fixed-delay của @Scheduled tính từ lúc invocation được theo dõi kết thúc.
Không tự đo từ t3 khi tick() chỉ submit và return.
```

Trong thực tế `t1` và `t2` có thể cách nhau rất ngắn. Với pool đang bận, worker còn có thể bắt đầu rất lâu **sau khi** tick đã trả về.

### 3.1. Scheduled invocation completion

Khi Spring gọi `@Scheduled` method thông thường, scheduler cần biết method invocation kết thúc để áp dụng semantics của trigger. Nếu method chỉ chạy synchronous code, “method return” thường tương ứng với “code trong method đã hoàn tất”.

### 3.2. Handoff/submit completion

Với method theo lịch chỉ gọi `@Async` service, lời gọi qua proxy thường trả về sau khi task được submit thành công. **Handoff hoàn tất** không đồng nghĩa task đã bắt đầu, cũng không đồng nghĩa task đã chạy xong.

Nếu executor queue đang đầy, submission có thể **bị từ chối ngay**, và scheduler method có thể ném exception hoặc xử lý nó tùy code. Đây là lỗi **dispatch**, khác với exception xảy ra sau đó trong worker.

### 3.3. Business completion

Đây là thời điểm nghiệp vụ thực sự kết thúc: ghi DB, gọi API, tính dữ liệu, hoàn thành future hoặc ném exception. Nếu caller bỏ future, scheduler có thể không biết chính xác kết quả này.

**Lưu ý:** future được complete **trước** khi giao dịch DB thật sự commit, hoặc trước khi downstream xác nhận email, thì ngay cả “future done” cũng có thể chưa tương ứng với **durable side effect** mà nghiệp vụ yêu cầu.

### 3.4. Durable completion

Với job quan trọng, cần một mốc có thể xác minh được: transaction đã commit, outbox đã ghi, downstream đã chấp nhận operation ID, hay job record bền vững chuyển `SUCCEEDED`. Local `Future.isDone()` chỉ nói về trạng thái future trong JVM hiện tại.

| Mốc | Ai quan sát? | Có nghĩa “business đã thành công” không? |
| --- | --- | --- |
| Trigger fired | `TaskScheduler` | Không |
| `tick()` returned | Scheduler / method invoker | Không nếu `tick` chỉ submit |
| Async task accepted | `TaskExecutor` | Không |
| Async future completed normally | Caller giữ future | Có thể, tùy body và transaction/effect |
| Database committed | DB transaction manager / store | Có với effect nằm trong commit đó |
| Downstream confirmed | External service | Theo semantics xác nhận của downstream |
| Durable job marked success | Job state authority | Theo hợp đồng job store/worker |

## 4. Fixed delay thay đổi ý nghĩa như thế nào khi thêm @Async?

### 4.1. Trước: method @Scheduled làm việc trực tiếp

```java
@Scheduled(fixedDelay = 1_000)
public void doWork() {
    fetchAndPersist(); // Giả sử mất 3 giây; chạy đồng bộ.
}
```

Giả sử:

- Lần chạy thứ nhất bắt đầu ở giây 0.
- `fetchAndPersist()` mất 3 giây.
- Invocation kết thúc vào giây 3.
- Sau đó chờ 1 giây → lần chạy tiếp theo vào khoảng giây 4.

```text
0s              3s  4s
|----------------|---|
[work 3 seconds]    [next work...]
                 ^  ^
                 |  next invocation
                 completion + fixedDelay=1s
```

Đây là timeline minh họa, không phải cam kết scheduler không bị trễ bởi GC hay tải máy.

### 4.2. Sau: scheduled method chỉ bàn giao qua @Async

```java
@Scheduled(fixedDelay = 1_000)
public void tick() {
    asyncWorker.doWork(); // Return sớm sau submit.
}
```

Giả sử worker vẫn mất 3 giây nhưng `tick()` trả về sau 10 ms:

```text
0s        1.01s      2.02s       3s
|----------|-----------|----------|
tick#1     tick#2      tick#3      worker#1 done
  |           |           |
  +--work#1---+           |
              +--work#2...|
                          +--work#3...

Scheduler delay ~1s kể từ lúc mỗi tick() trả về,
không đợi work#1/work#2 hoàn tất.
```

**Điểm chính:** `fixedDelay` của `tick()` vẫn là 1 giây, nhưng delay tính từ **completion của tick invocation**, không từ **completion của async worker**. Những lần worker có thể **chồng lấn** nếu executor có capacity, hoặc **xếp hàng** nếu executor có ít threads.

### 4.3. Hệ quả về số lượng job đang chạy

Nếu lịch kích hoạt khoảng 1 lần/giây còn mỗi worker làm việc 10 giây:

- Không có giới hạn thích hợp: có thể có khoảng nhiều lần chạy đồng thời khi mỗi lần handoff thành công.
- Pool nhỏ: callback mới có thể xếp hàng; queue wait ngày càng tăng.
- Pool/queue quá tải: submit có thể bị reject, và tác vụ không được thực thi.
- Nếu source dữ liệu giống nhau: có nguy cơ race, cập nhật trùng, gửi email trùng hoặc stale write.

Không thể “sửa overlap” bằng cách chỉ tăng `fixedDelay` tùy ý: thời gian worker thay đổi theo tải và downstream. Cần policy rõ ràng như **skip**, **queue**, **coalesce**, **single-flight** hoặc **allow overlap**.

### 4.4. @Async trên chính method @Scheduled thì sao?

Có những ứng dụng đặt cả hai annotation trên cùng method:

```java
@Scheduled(fixedDelay = 1_000)
@Async("workExecutor")
public void refresh() {
    refreshCache();
}
```

Đây là **fragment tích hợp**. Với proxy/advice đúng, scheduler invocation có thể bị async interceptor bàn giao rồi kết thúc sớm. Nhưng cùng-method setup còn phụ thuộc cách Spring đăng ký scheduled task, proxy strategy, khả năng method được intercept, bean lifecycle và cấu hình; **không dùng nó làm bằng chứng** rằng scheduler đợi body `refreshCache()`.

Để giải thích và kiểm thử chính xác ranh giới, bài này **khuyến nghị hai bean riêng**:

- `ScheduledBridge` sở hữu `@Scheduled`, hoạt động như trigger.
- `AsyncWorker` sở hữu `@Async` và thực hiện logic nghiệp vụ.
- `ScheduledBridge` nhận `AsyncWorker` bằng constructor injection, gọi qua Spring-managed proxy.
- Giữ future do worker trả về để theo dõi completion, error và chính sách overlap.

Tách bean còn tránh lỗi `this.asyncWork()` khi self-invocation bỏ qua `@Async` proxy.

## 5. Fixed rate và cron: không suy ra đều phải đợi lần trước xong

### 5.1. Fixed rate

`@Scheduled(fixedRate = 5_000)` biểu diễn nhịp kích hoạt theo khoảng cách **đến hạn** giữa các lần chạy, khác với `fixedDelay` sau completion. Nhưng **thực tế có overlap hay không** vẫn phụ thuộc implementation scheduler, pool, và việc callback có handoff tiếp hay không.

Ví dụ `ThreadPoolTaskScheduler` với một scheduled periodic task có thể dùng `ScheduledThreadPoolExecutor` và những ràng buộc không chồng lấn của chính periodic task đó. Đừng hứa một method fixed-rate sẽ luôn chạy song song với chính nó khi chỉ đổi annotation.

Nếu scheduled method chỉ submit task vào `@Async` worker, **business jobs** vẫn có thể chồng lấn dù scheduler invocation của cùng periodic task không chồng lấn. Đây là hai lớp overlap khác nhau.

### 5.2. Cron

`@Scheduled(cron = "0 */5 * * * *", zone = "UTC")` kích hoạt theo mốc giờ lịch. Lần cron tiếp theo có thể đến khi worker được handoff ở lần trước vẫn chưa hoàn tất. Cron không tự quyết định job nên skip, chạy tiếp, coalesce hay đợi; đó là quyết định của ứng dụng/scheduler implementation.

### 5.3. Scheduler cụ thể ảnh hưởng đường chạy

Spring Framework có `ThreadPoolTaskScheduler` (dựa trên `ScheduledExecutorService`) và `SimpleAsyncTaskScheduler` (Spring 6.1+, hướng tới mô hình thread-per-task/virtual threads). Với `SimpleAsyncTaskScheduler`, **fixed-delay** chạy trên scheduler thread thay vì handoff sang worker riêng theo cơ chế thông thường; nhưng nếu callback fixed-delay **chỉ gọi một method @Async rồi return**, business work vẫn có thể chạy sau ở executor khác.

Không được nhầm **“fixed-delay callback chạy trên scheduler thread”** với **“scheduler đang đợi tất cả việc async được callback submit”**. Hai mệnh đề nói về hai tầng khác nhau.

### 5.4. Bảng kết hợp

| Cấu hình | Scheduler theo dõi gì? | Thời điểm invocation “xong” | Nguy cơ |
| --- | --- | --- | --- |
| `@Scheduled(fixedDelay)`, body làm việc sync | Body method | Sau khi body trả về | Blocking scheduler worker |
| `@Scheduled(fixedDelay)` gọi `@Async` service rồi return | Method bridge | Sau khi submit/return | Worker cũ chưa xong khi trigger sau tới |
| `@Scheduled(fixedRate)` gọi `@Async` | Nhịp periodic invocation | Handoff nhanh | Business tasks có thể overlap/queue |
| `@Scheduled(cron)` gọi `@Async` | Mốc cron | Handoff nhanh | Jobs có thể chồng lấn giữa mốc |
| `@Scheduled(fixedDelay)` gọi future rồi `join` | Method bridge đang đợi future | Sau completion future | Block scheduler thread; lỗi/timeout cần xử lý |
| Programmatic schedule next khi worker future complete | Business future nếu ứng dụng thiết kế | Khi callback ghi nhận completion | Cần quản lý race, stop/restart, errors |

## 6. Ví dụ Spring Framework Java 17+: trigger thứ hai tới khi worker thứ nhất còn bận

Ví dụ quan trọng nhất dùng **Spring Framework 6.2.14**, JDK 17+, Maven, `ThreadPoolTaskScheduler` và `ThreadPoolTaskExecutor`. Nó chủ động giữ worker thứ nhất bằng `CountDownLatch`, để chứng minh scheduler **tiếp tục kích hoạt lần sau** dù business worker chưa kết thúc.

Đây là một chương trình độc lập có đủ POM, class, beans, commands và stdout ổn định; không cần Spring Boot.

### 6.1. Cấu trúc thư mục

```text
scheduled-async-demo/
├── pom.xml
└── src/main/java/demo/ScheduledAsyncCompletionDemo.java
```

### 6.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>scheduled-async-demo</artifactId>
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

### 6.3. ScheduledAsyncCompletionDemo.java

```java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class ScheduledAsyncCompletionDemo {

    public static class Probe {
        final AtomicInteger triggers = new AtomicInteger();
        final CountDownLatch workerStarted = new CountDownLatch(1);
        final CountDownLatch secondTrigger = new CountDownLatch(1);
        final CountDownLatch releaseWorker = new CountDownLatch(1);
        final CountDownLatch workerFinished = new CountDownLatch(1);
        final AtomicBoolean correctWorkerThread = new AtomicBoolean();
        final AtomicReference<CompletableFuture<Void>> future =
                new AtomicReference<>();
    }

    public static class AsyncWorker {
        private final Probe probe;

        public AsyncWorker(Probe probe) {
            this.probe = probe;
        }

        @Async("workExecutor")
        public CompletableFuture<Void> process() {
            probe.correctWorkerThread.set(
                    Thread.currentThread().getName()
                            .startsWith("work-"));
            probe.workerStarted.countDown();

            try {
                probe.releaseWorker.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            } finally {
                probe.workerFinished.countDown();
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    public static class ScheduledBridge {
        private final AsyncWorker worker;
        private final Probe probe;

        public ScheduledBridge(AsyncWorker worker, Probe probe) {
            this.worker = worker;
            this.probe = probe;
        }

        @Scheduled(initialDelay = 0, fixedDelay = 150)
        public void tick() {
            int n = probe.triggers.incrementAndGet();

            if (n == 1) {
                probe.future.set(worker.process());
            } else if (n == 2) {
                probe.secondTrigger.countDown();
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @EnableAsync
    public static class Config {

        @Bean("taskScheduler")
        public ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("tick-");
            return scheduler;
        }

        @Bean("workExecutor")
        public ThreadPoolTaskExecutor workExecutor() {
            ThreadPoolTaskExecutor executor =
                    new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(8);
            executor.setThreadNamePrefix("work-");
            return executor;
        }

        @Bean
        public Probe probe() {
            return new Probe();
        }

        @Bean
        public AsyncWorker worker(Probe probe) {
            return new AsyncWorker(probe);
        }

        @Bean
        public ScheduledBridge bridge(
                AsyncWorker worker, Probe probe) {
            return new ScheduledBridge(worker, probe);
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            Probe probe = context.getBean(Probe.class);

            if (!probe.workerStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker did not start");
            }
            if (!probe.secondTrigger.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("second tick missing");
            }

            System.out.println("second-tick-before-work-finished="
                    + (probe.workerFinished.getCount() == 1));
            System.out.println("worker-on-async-executor="
                    + probe.correctWorkerThread.get());

            probe.releaseWorker.countDown();
            probe.future.get().get(5, TimeUnit.SECONDS);

            System.out.println("work-finished-after-release="
                    + (probe.workerFinished.getCount() == 0));
        }
    }
}
```

### 6.4. Chạy demo

Trong thư mục có `pom.xml`:

```bash
mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=demo.ScheduledAsyncCompletionDemo
```

Ba dòng do chương trình in ra khi context hoạt động bình thường:

```text
second-tick-before-work-finished=true
worker-on-async-executor=true
work-finished-after-release=true
```

**Tại sao output xác định về logic?**

1. `workerStarted` chứng minh worker đã bắt đầu, nhưng worker vẫn chặn tại `releaseWorker.await()`.
2. `tick()` đầu tiên gọi `worker.process()` qua Spring proxy rồi **trả về ngay**; nó không gọi `join()`.
3. `ThreadPoolTaskScheduler` có pool size 1 vẫn tiếp tục lần scheduled invocation thứ hai, vì scheduled method trước đó đã hoàn thành.
4. `secondTrigger` chỉ báo khi scheduler chạy `tick()` lần 2; lúc này main **chưa** gọi `releaseWorker.countDown()`.
5. Do đó `workerFinished.getCount() == 1`: worker đầu **chưa làm xong** dù tick thứ hai đã tới.
6. Worker dùng executor tên `workExecutor` nên thread có prefix `work-`.
7. Sau khi main giải phóng latch, `probe.future.get().get(...)` chờ async completion thật, và `workerFinished` đã được giảm.

Maven/Spring có thể in thêm thông báo; output trên chỉ liệt kê ba dòng `System.out.println` có chủ đích.

### 6.5. Điều gì thay đổi nếu không có @EnableAsync?

Nếu xóa `@EnableAsync` khỏi context và không còn cấu hình async tương đương, lời gọi `worker.process()` không được proxy intercept. `tick()` đầu tiên sẽ tự **chờ `releaseWorker` trên scheduler thread**, nên tick thứ hai không đến và demo sẽ báo timeout. Đó là một thử nghiệm đối chứng hữu ích trong môi trường riêng. **Không tự chạy thử bằng cách sửa bài gốc trong Google Sheet**; hãy tạo project demo riêng.

### 6.6. Điều gì thay đổi nếu gọi this.process()?

Nếu cả `tick()` và `process()` ở cùng bean rồi `tick()` gọi `this.process()`, Spring proxy-mode mặc định có thể bị bỏ qua do **self-invocation**; `@Async` có thể không có hiệu lực. Chính vì vậy demo cố ý tách `ScheduledBridge` và `AsyncWorker` thành hai bean.

## 7. Ví dụ Java 17+ thuần: fixed-delay chỉ đợi dispatch, không đợi worker

Để nhìn riêng cơ chế mà không cần Spring, có thể dùng `ScheduledExecutorService` gọi `ExecutorService.execute(...)`. Đây là **mô phỏng về kiến trúc**, không phải code nội bộ của `ScheduledAnnotationBeanPostProcessor`.

Tệp `HandoffFixedDelayDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class HandoffFixedDelayDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();
        ExecutorService workers =
                Executors.newSingleThreadExecutor();

        AtomicInteger ticks = new AtomicInteger();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch workDone = new CountDownLatch(1);
        CountDownLatch secondTick = new CountDownLatch(1);

        ScheduledFuture<?> periodic = null;
        try {
            Runnable trigger = () -> {
                int n = ticks.incrementAndGet();

                if (n == 1) {
                    workers.execute(() -> {
                        workerStarted.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        } finally {
                            workDone.countDown();
                        }
                    });
                } else if (n == 2) {
                    secondTick.countDown();
                }
            };

            periodic = scheduler.scheduleWithFixedDelay(
                    trigger, 0, 100, TimeUnit.MILLISECONDS);

            if (!workerStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("no worker");
            }
            if (!secondTick.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("no second trigger");
            }

            System.out.println("second-before-work-done="
                    + (workDone.getCount() != 0));

            release.countDown();

            if (!workDone.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("work unfinished");
            }

            System.out.println("work-done-after-release="
                    + (workDone.getCount() == 0));
        } finally {
            release.countDown();
            if (periodic != null) {
                periodic.cancel(false);
            }
            scheduler.shutdownNow();
            workers.shutdownNow();
        }
    }
}
```

Chạy:

```bash
javac HandoffFixedDelayDemo.java
java HandoffFixedDelayDemo
```

Output:

```text
second-before-work-done=true
work-done-after-release=true
```

`scheduleWithFixedDelay` quản lý **thời gian giữa các lần chạy của `trigger`**, nhưng `trigger` chỉ thực hiện `workers.execute(...)` rồi trả về. Bởi vậy tick thứ hai đến ngay cả khi business worker vẫn chờ latch. Các khái niệm này hoàn toàn song song với tình huống Spring scheduled bridge → Spring async worker.

## 8. Ba cách thiết kế khi cần “hoàn tất thật”

### 8.1. Cách A: để @Scheduled chạy công việc đồng bộ

```java
@Scheduled(fixedDelay = 5_000)
public void perform() {
    service.performSynchronously();
}
```

Nếu `performSynchronously()` thật sự chỉ trả về sau khi nghiệp vụ hoàn tất, `fixedDelay` tính từ lần đó. Đơn giản, đúng nghĩa “khoảng nghỉ sau khi công việc đã xong”.

**Đánh đổi:** scheduler worker thread bị chiếm trong suốt tác vụ. Với `ThreadPoolTaskScheduler`, hãy chọn pool size/giới hạn và không làm tắc các lịch khác. Với `SimpleAsyncTaskScheduler`, fixed-delay chạy trên scheduler thread và tác vụ chậm có thể chặn trigger khác.

### 8.2. Cách B: đợi async future ngay trong scheduled method

```java
@Scheduled(fixedDelay = 5_000)
public void perform() {
    CompletableFuture<Void> result = asyncWorker.perform();
    result.join(); // Đợi completion của async worker.
}
```

Khi future đại diện đúng toàn bộ công việc bắt buộc, method `perform()` chỉ trả về sau khi future complete, nên scheduler có thể đo fixed-delay từ mốc đó.

**Đánh đổi:**

- `join()` **block scheduler thread**; vì vậy bạn mất ưu điểm “bàn giao rồi trả về ngay”.
- Có nguy cơ starvation/deadlock nếu scheduler và async worker **dùng chung một executor nhỏ**, ví dụ một thread duy nhất bị scheduler giữ trong khi async task chờ được chạy.
- `join()` có thể chờ vô hạn; phải có timeout/cancellation policy cho I/O, và xử lý `CompletionException`/shutdown.
- Nếu worker trả future **trước DB commit hoặc trước side effect**, việc đợi future vẫn có thể chưa có nghĩa business thật sự hoàn tất.

Nếu cần completion-based delay với blocking không thành vấn đề, **cách A** thường dễ hiểu hơn. Không dùng cách B chỉ để “ép” dấu `@Async` có mặt.

### 8.3. Cách C: tự lên lịch lần kế tiếp khi future hoàn tất

Nếu cần **giữ scheduler thread rảnh** nhưng vẫn tính khoảng nghỉ sau **business completion**, có thể dùng `TaskScheduler.schedule(...)` programmatic, và trong callback của future mới đặt lịch lần kế tiếp.

Mô hình:

```text
schedule next trigger
        |
        v
invoke async worker --> CompletableFuture<Void>
        |                     |
  scheduler returns            |
                               v
                       business completes
                               |
                           whenComplete
                               |
                   handle success/failure/stop
                               |
                   schedule next after Duration
```

**Fragment Spring** (không phải lớp chạy độc lập; yêu cầu các field và lifecycle bean thích hợp):

```java
private final TaskScheduler scheduler;
private final AsyncWorker asyncWorker;
private final Duration delay = Duration.ofSeconds(5);
private final AtomicBoolean running = new AtomicBoolean();

public void start() {
    if (running.compareAndSet(false, true)) {
        scheduleNext(Duration.ZERO);
    }
}

private void scheduleNext(Duration after) {
    if (running.get()) {
        scheduler.schedule(this::fire, Instant.now().plus(after));
    }
}

private void fire() {
    if (!running.get()) {
        return;
    }
    try {
        asyncWorker.perform().whenComplete((ignored, error) -> {
            recordOutcome(error);
            if (running.get()) {
                scheduleNext(delay);
            }
        });
    } catch (RuntimeException submitError) {
        recordOutcome(submitError);
        if (running.get()) {
            scheduleNext(delay);
        }
    }
}

public void stop() {
    running.set(false);
}
```

Ý nghĩa: lần chạy sau chỉ được xếp lịch khi `whenComplete` của **future thực** đã được gọi. `@Scheduled(fixedDelay)` được thay bằng **completion-aware rescheduling**.

**Cảnh báo production:** snippet này chỉ mô tả mối quan hệ cơ bản, chưa có logic đầy đủ cho stop race, in-flight cancellation, handle của scheduled task, bounded backoff/retry, rejected submission, job ownership và persistence. `AtomicBoolean` chỉ đồng bộ trong một JVM; muốn không chạy trùng giữa replica phải có coordination. Với lỗi liên tục và `delay` nhỏ, cần backoff.

### 8.4. Cách D: dùng durable job queue khi completion mang ý nghĩa nghiệp vụ lớn

Nếu công việc phải được retry qua restart, có nhiều instance, có trạng thái dài hạn hoặc không được gửi trùng, giải pháp không chỉ là chọn A/B/C. Một scheduler có thể phát **command có operation ID** vào queue bền vững, còn worker quản lý state/ack/retry/idempotency theo database và broker.

Trong mô hình đó:

- `@Scheduled` đánh dấu **đã dispatch/enqueue**.
- Job store/broker xác định **đã được tiếp nhận bền vững**.
- Worker và downstream xác định **đã thực thi thật**, thất bại hay chưa rõ.
- Job state machine làm nguồn sự thật khi caller hỏi tiến độ.
- Hủy trigger không tự hủy tác dụng phụ đã commit.

Không đổi local `@Scheduled` thành “guaranteed execution” chỉ vì đã thêm `@Async`.

## 9. Demo Java 17+: future biết job đã xong, không chỉ biết đã submit

Một ví dụ không phụ thuộc Spring, làm rõ sự khác biệt giữa handoff signal và future completion.

Tệp `CompletionHandleDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CompletionHandleDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            CompletableFuture<String> work =
                    CompletableFuture.supplyAsync(() -> {
                        started.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                        }
                        return "DONE";
                    }, executor);

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("not started");
            }

            System.out.println("submitted=true");
            System.out.println("business-completed-before-release="
                    + work.isDone());

            release.countDown();
            System.out.println("business-result="
                    + work.get(5, TimeUnit.SECONDS));
            System.out.println("business-completed-after-release="
                    + work.isDone());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
```

Chạy bằng Java 17+:

```bash
javac CompletionHandleDemo.java
java CompletionHandleDemo
```

Output:

```text
submitted=true
business-completed-before-release=false
business-result=DONE
business-completed-after-release=true
```

Khi `work.isDone()==false`, task đã được submit và thực sự bắt đầu (latch `started`), nhưng **chưa hoàn tất**. Trong Spring, đừng trả một future chỉ phản ánh submit/dispatch rồi đặt tên `completed`; phải trả **future đại diện đúng tác vụ cuối** nếu caller cần biết kết quả.

## 10. Chống overlap: khi tick tiếp theo tới nhưng worker cũ còn chạy

Đối với polling, refresh cache, đồng bộ đơn hàng hay quét email, một chiến lược cần được ghi rõ.

| Chính sách | Hành vi | Thích hợp | Điểm cần kiểm soát |
| --- | --- | --- | --- |
| Allow overlap | Nhiều worker có thể làm song song | Tác vụ độc lập, idempotent | Quota, thread safety, duplicate effects |
| Skip while running | Tick mới bị bỏ nếu job cũ chưa xong | Refresh chỉ cần một việc hoạt động | Reset guard khi lỗi/rejection |
| Queue all | Tick nào cũng được giữ | Mỗi tick mang ý nghĩa nghiệp vụ riêng | Backlog, bounded queue, durability |
| Coalesce/latest-wins | Chỉ giữ một yêu cầu mới nhất | Cache/preview/UI refresh | Stale result, fencing, version |
| Completion-aware delay | Đặt lịch sau khi job cũ thực sự kết thúc | Cần khoảng nghỉ sau completion | Future/lifecycle/retry/stop |
| Distributed lease | Tối đa một owner hợp lệ toàn cụm | Nhiều replica cùng có scheduler | Fencing và lease expiry |

### 10.1. AtomicBoolean guard trong một JVM

Đây là **fragment Spring** khi `ScheduledBridge` inject bean `AsyncWorker` trả `CompletableFuture<Void>`:

```java
private final AtomicBoolean running = new AtomicBoolean();

@Scheduled(fixedRate = 1_000)
public void tick() {
    if (!running.compareAndSet(false, true)) {
        return; // Skip khi job trước chưa xong.
    }

    try {
        CompletableFuture<Void> future = worker.perform();
        future.whenComplete((ignored, error) -> {
            recordOutcome(error);
            running.set(false);
        });
    } catch (RuntimeException submitError) {
        running.set(false);
        throw submitError;
    }
}
```

Guard được set **trước** khi submit. Nó chỉ reset khi **future thực** complete, không reset ngay sau `worker.perform()` trả về.

**Những điểm phải xem lại khi đưa vào production:**

- `whenComplete` có thể thực thi inline trong completing thread; `recordOutcome` không nên block/làm hỏng reset guard. Dùng `try/finally` bên trong callback nếu code ghi outcome có thể ném lỗi.
- Bên ngoài có thể cancel future nhưng underlying task chưa dừng; nếu reset guard quá sớm, có thể overlap dù task cũ còn chạy.
- Nếu executor reject task hoặc `worker.perform()` ném ngay, phải reset guard.
- Nếu process chết, local guard mất; dùng job store/distributed coordination nếu cần.
- Một bean singleton có mutable shared state vẫn phải tuân thủ thread safety.
- Nếu bạn cần queue mỗi lần thay vì skip, guard này không đúng nghiệp vụ.

### 10.2. Cách viết callback reset chắc chắn hơn

```java
future.whenComplete((ignored, error) -> {
    try {
        recordOutcome(error);
    } finally {
        running.set(false);
    }
});
```

Đây là fragment. Nó đảm bảo reset local guard kể cả `recordOutcome` lỗi. Nhưng nếu task đang làm một external side effect và future chỉ báo “timeout” chứ **không phải worker đã dừng**, reset vẫn có thể sớm. Cần bảo đảm future có nghĩa chính xác trong hợp đồng của worker.

## 11. ScheduledFuture không phải luôn là future nghiệp vụ

`TaskScheduler` có các method trả `ScheduledFuture<?>` cho việc đặt lịch. Handle này phục vụ **quản lý trigger/scheduling**, không mặc nhiên là `CompletableFuture<Result>` của business.

Đặc biệt với `SimpleAsyncTaskScheduler`, Javadoc lưu ý nhiều scheduled futures phản ánh **handoff** sang thread thực thi chứ không phản ánh hoàn thành target task. Với `ThreadPoolTaskScheduler`, callback trực tiếp chạy trên scheduler threads và semantics có thể khác; phải xem method và implementation cụ thể.

Nếu bạn có:

```java
ScheduledFuture<?> scheduled =
        taskScheduler.schedule(() -> worker.perform(),
                               Instant.now());
```

đó là **fragment**. `scheduled.get()` không bảo đảm `worker.perform()` (trả future) đã complete: Runnable chỉ gọi method rồi trả về. Nếu cần business completion, giữ reference do worker trả về hoặc quản lý job status.

### 11.1. Định danh cho từng lần chạy

Một scheduler method được gọi lặp lại có thể tạo nhiều execution khác nhau. Gắn `runId`/`operationId`, `triggerTime` và version/revision khi cần:

```text
tick#101 -> submit operationId=A -> worker A RUNNING
tick#102 -> submit operationId=B -> worker B RUNNING
tick#103 -> submit operationId=C -> worker C QUEUED
A finishes; B fails; C is rejected/retried...
```

Chỉ có tên method `poll` không đủ phân biệt ba lần chạy. Logs và metrics nên tách **trigger count**, **submitted**, **started**, **completed**, **failed**, **rejected**, **skipped**.

## 12. Vì sao không nên trả CompletableFuture trực tiếp từ @Scheduled để Spring tự chờ?

Trong các ứng dụng Spring thông thường, `@Scheduled` imperative method được gọi theo scheduling API; return value không phải hợp đồng để scheduler tự chờ hoàn tất business future.

Hơn nữa, từ Spring Framework **6.1**, framework còn hỗ trợ **reactive scheduled methods** theo quy tắc riêng: method trả `Publisher` (hoặc kiểu có adapter hỗ trợ **deferred subscription**) có thể được framework subscribe theo lịch.

**Quan trọng:** `CompletableFuture` tuy có thể adapt thành `Publisher` trong một số tình huống, nhưng adapter của nó thường báo **không hỗ trợ deferred subscription**. Trong đường xử lý reactive `@Scheduled` của Spring 6.1+, method trả một kiểu non-deferred như thế **không phải** giải pháp hợp lệ để được subscribe lặp lại và đợi completion; có thể bị từ chối khi cấu hình.

Vì vậy không khuyến nghị:

```java
// Phản mẫu: đừng kỳ vọng @Scheduled đợi future này.
@Scheduled(fixedDelay = 5_000)
public CompletableFuture<Void> poll() {
    return worker.perform();
}
```

Đây là **phản ví dụ**, không phải snippet để copy chạy. Nếu muốn completion-aware scheduling, dùng thiết kế A/B/C ở phần 8 hoặc một mô hình reactive phù hợp với contract của framework.

### 12.1. Reactive @Scheduled fixed-delay là trường hợp khác

Với một `Publisher` deferred hợp lệ, Spring 6.1+ tạo subscription theo lịch. Với fixed-delay, framework có cơ chế **block chờ subscription hoàn thành** để duy trì fixed-delay semantics. Điều đó **khác** với:

- một method `void` chỉ submit `@Async` rồi return;
- một `CompletableFuture` non-deferred;
- một HTTP response `202 Accepted` báo job đã được nhận nhưng chưa hoàn thành.

Nếu dùng Reactor, cần phân biệt **đăng ký subscription**, **onComplete/onError**, và **durable completion**. Publisher complete không bảo đảm một tác dụng phụ bên ngoài đã commit trừ khi chính pipeline của bạn chờ/xác minh nó.

### 12.2. Reactive error handling khác ErrorHandler của Runnable

Trong reactive scheduling, `onError` được framework ghi log và recover để các lần subscribe sau vẫn tiếp tục; exception không đơn giản được ném từ Runnable như synchronous method. Nếu muốn retry/backoff/đánh dấu job failed, cần đặt policy trong pipeline và giám sát đúng.

## 13. Executor cho @Scheduled và @Async là hai thứ riêng

`@Scheduled` sử dụng `TaskScheduler`. `@Async` sử dụng `Executor`/`TaskExecutor`. Chúng có thể là hai bean khác nhau:

```java
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableAsync
public class SchedulingConfig {

    @Bean("taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("trigger-");
        return scheduler;
    }

    @Bean("workExecutor")
    ThreadPoolTaskExecutor workExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("worker-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        return executor;
    }
}
```

Đây là **fragment cấu hình Spring**; cần imports. Các số liệu 2/4/32 chỉ là ví dụ, không phải cấu hình tối ưu cố định.

| Tài nguyên | Ai dùng | Metric quan trọng |
| --- | --- | --- |
| `taskScheduler` | Đánh thức các `@Scheduled` callbacks | Trigger lag, scheduler active, missed firings |
| `workExecutor` | Chạy methods `@Async("workExecutor")` | Active workers, queue depth, rejection, task duration |
| Database/HTTP clients | Được gọi trong worker | Connection saturation, timeout, failure |
| Job store/outbox | Giữ trạng thái bền vững | Pending, leased, succeeded, retry, duplicate |

Để tránh deadlock khi `tick()` đợi future, **không tùy tiện dùng cùng executor đơn luồng** cho cả chờ và công việc cần được chạy. Nếu scheduler thread bị block và worker lại phụ thuộc vào scheduler để tiến lên, hệ thống có thể dừng.

### 13.1. Spring Boot auto-configuration có điều kiện

Spring Boot có thể auto-configure task execution và scheduling theo loại bean, classpath, version và properties. Thêm một bean executor tùy chỉnh có thể thay điều kiện mặc định của Boot.

Không suy từ `spring.task.execution.*` rằng mọi `@Async` method sẽ chạy trên đúng pool đó; nhất là khi method dùng `@Async("workExecutor")` và bean tùy chỉnh. Không suy từ `spring.task.scheduling.*` rằng có thể điều chỉnh thread pool của mọi scheduler implementation. Luôn kiểm tra **actual bean types** và names ở runtime.

### 13.2. Java 21 virtual threads không thay đổi ý nghĩa completion

Spring Framework 6.1+ có `SimpleAsyncTaskScheduler` hỗ trợ virtual threads, có single trigger thread và handoff task trong các trường hợp phù hợp. Dù task được chạy trên virtual thread, **handoff** vẫn khác với **business completion**. Fixed-delay của `SimpleAsyncTaskScheduler` là ngoại lệ chạy trên scheduler thread; khi callback đó gọi `@Async` worker rồi return, business completion vẫn ở nơi khác.

Virtual threads giúp thay đổi chi phí/concurrency model trong nhiều tình huống I/O, **không** tự thêm transaction, idempotency, overlap guard hay durable delivery.

## 14. Self-invocation và bean ownership

Một phương thức có `@Async` chỉ được intercept khi đường gọi đi qua Spring proxy trong `AdviceMode.PROXY` mặc định.

### 14.1. Lỗi tự gọi chính mình

```java
@Component
class PollJob {
    @Scheduled(fixedDelay = 1_000)
    public void tick() {
        refreshAsync(); // implicit this.refreshAsync()
    }

    @Async
    public void refreshAsync() {
        refresh();
    }
}
```

Đây là **fragment phản mẫu**. `tick()` gọi `refreshAsync()` **trực tiếp trong cùng instance**, không đi qua async proxy; do đó có thể thực thi đồng bộ trên scheduler thread. Khi đó lần scheduled invocation chỉ kết thúc sau khi `refresh()` xong, khác hoàn toàn kỳ vọng nếu bạn nghĩ `@Async` luôn hoạt động.

Giải pháp đơn giản:

```java
@Component
class PollJob {
    private final AsyncWorker worker;

    PollJob(AsyncWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelay = 1_000)
    public void tick() {
        worker.refreshAsync(); // Spring-managed bean proxy.
    }
}

@Service
class AsyncWorker {
    @Async("workExecutor")
    public CompletableFuture<Void> refreshAsync() {
        refresh();
        return CompletableFuture.completedFuture(null);
    }
}
```

Đây là **fragment**, cần một method `refresh()` hợp lệ, các imports và cấu hình. Tách bean cũng làm **thời điểm completion khác đi**, như đã phân tích; phải chọn có chủ đích.

### 14.2. Bean tự new không nhận interception

`AsyncWorker worker = new AsyncWorker();` trong scheduled method tạo object Java ngoài Spring container, không tự được bọc proxy. Dù class có `@Service` và method có `@Async`, object tự tạo vẫn không có async advice. Dùng constructor injection hoặc `ApplicationContext` lookup có kiểm soát thay vì tự new service cần Spring quản lý.

### 14.3. Đừng đồng nhất proxy với thread safety

Một singleton `AsyncWorker` có thể bị nhiều task đồng thời gọi. Spring proxy không làm các field mutable như `lastRunId` hoặc `ArrayList` thành an toàn. Dùng immutable input per run, local state, lock/atomic hoặc nguồn state tập trung theo business invariants.

## 15. Exception: lỗi khi dispatch khác lỗi bên trong worker

Phân biệt hai tình huống:

**(A) Submit bị từ chối ngay:**

```java
@Scheduled(fixedDelay = 1_000)
public void tick() {
    worker.perform(); // Có thể ném TaskRejectedException lúc submit.
}
```

Nếu executor đã shutdown/queue đầy, `worker.perform()` qua proxy có thể thất bại trước khi trả future. Synchronous scheduling `ErrorHandler` có thể nhìn thấy exception từ invocation, tùy cấu hình.

**(B) Submit thành công nhưng worker ném lỗi về sau:**

```java
@Async("workExecutor")
public CompletableFuture<Void> perform() {
    throw new IllegalStateException("downstream failed");
}
```

Đây là **fragment**. Nếu future được trả về nhưng caller chỉ bỏ nó đi, exception từ worker nằm trên future và scheduler không mặc nhiên được thông báo. Với method `@Async` trả `void`, exception thường được đưa tới `AsyncUncaughtExceptionHandler`/logging, không thể truyền qua future kết quả cho caller.

### 15.1. Thiết kế xử lý lỗi

```java
@Scheduled(fixedDelay = 1_000)
public void tick() {
    CompletableFuture<Void> future;
    try {
        future = worker.perform();
    } catch (RuntimeException submitError) {
        recordDispatchFailure(submitError);
        return;
    }

    future.whenComplete((ignored, error) -> {
        if (error != null) {
            recordBusinessFailure(error);
        } else {
            recordBusinessSuccess();
        }
    });
}
```

Đây là **fragment**, cần imports/field/method logging. Dù có callback, `tick()` vẫn return nhanh và fixed-delay **không đợi** callback đó. **Quan sát lỗi** và **thay đổi completion semantics** là hai việc khác nhau.

Nếu `recordBusinessFailure` bản thân ném lỗi, dependent stage của `whenComplete` cũng có thể thất bại; với logging/metrics quan trọng phải có chính sách an toàn và không bỏ exception tùy tiện.

### 15.2. Lỗi không nên biến thành “đã xong thành công”

Một `CompletableFuture` **completed exceptionally** cũng là `isDone()==true`. Nếu business policy chỉ cho phép trigger mới sau **success**, kiểm tra outcome chứ không chỉ completion boolean. Nếu policy cho phép retry khi thất bại, cần backoff, giới hạn số lần và idempotency.

## 16. Transaction và side effect: “future xong” chưa chắc business commit

Trong Spring imperative, transaction thường gắn với thread/transaction context. `@Async` chuyển công việc sang worker, nên `@Transactional` của caller **không tự chuyển** sang worker.

Ví dụ:

```java
@Transactional
public void scheduleBusinessWork() {
    repository.save(entity);
    asyncWorker.notifyCustomer(entity.getId());
}
```

Đây là **fragment phản mẫu** nếu notification chỉ được gửi sau khi transaction commit. Worker có thể chạy trước commit hoặc sau rollback, vì việc submit đã diễn ra trước khi outer transaction kết thúc.

Nếu sự kiện chỉ hợp lệ **sau commit**:

- Dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` với event chỉ mang ID/immutable DTO phù hợp; listener sau commit gọi async worker.
- Với nhiệm vụ không được mất qua restart, ghi **outbox/job record trong cùng transaction** với thay đổi nghiệp vụ.
- Worker mở transaction riêng khi cần đọc/cập nhật DB; không chia sẻ managed JPA entity và persistence context với caller thread.
- Nếu cần biết business thật sự thành công, chỉ complete job state sau khi effect/ack cần thiết được xác nhận.

### 16.1. Ví dụ ranh giới sau commit

```java
// Fragment: service transaction publish event.
@Transactional
public void approve(long orderId) {
    repository.approve(orderId);
    publisher.publishEvent(new OrderApprovedEvent(orderId));
}

// Fragment: listener chỉ submit sau commit.
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onApproved(OrderApprovedEvent event) {
    worker.notifyCustomer(event.orderId());
}
```

Trong Spring Framework, listener sau commit không tự trở thành durable queue; JVM có thể chết giữa commit và submit hoặc trong lúc task còn ở memory. Phải chọn delivery guarantee theo nghiệp vụ.

### 16.2. Timer và business deadline là hai khái niệm

Nếu scheduler tick#101 submit job nhưng tick#102 đến khi job#101 chưa xong, quyết định job#101 **còn hợp lệ** hay không dựa vào policy: bỏ kết quả cũ, dùng optimistic lock, fencing token hoặc cho phép tất cả thực hiện nếu độc lập. Không chỉ cancel future rồi tuyên bố email/DB operation đã bị rollback.

## 17. Cancellation và shutdown

Có ba thứ có thể bị dừng khác nhau:

1. **Lịch kích hoạt:** không phát tick mới.
2. **Async executor task:** gửi tín hiệu cancel/interrupt hoặc shutdown pool.
3. **Tác dụng phụ bên ngoài:** DB/HTTP/broker operation có thể đã commit hoặc đang in-flight.

`ScheduledFuture.cancel(...)` có thể hủy **lịch tiếp theo**; nó không mặc nhiên hủy mọi task đã được `@Async` bàn giao. `CompletableFuture.cancel(true)` không tự cung cấp cơ chế interrupt điều khiển underlying computation trong implementation chuẩn của `CompletableFuture`; cần cooperative cancellation, timeout ở downstream và guard nghiệp vụ.

Với shutdown:

- Dừng tạo tick mới.
- Chọn drain/grace period cho workExecutor riêng.
- Không đóng DB/HTTP client trước khi worker quan trọng hoàn tất hoặc có trạng thái resume.
- Quan sát in-flight jobs thực sự thay vì chỉ số scheduled futures.
- Nếu job phải tồn tại qua JVM restart, cần durable store/broker.

`@Scheduled` và `@Async` là local Spring infrastructure, không cung cấp exactly-once hoặc phân phối job trên toàn cluster.

### 17.1. Multi-replica

Hai pod cùng chạy `@Scheduled` có thể tạo **hai lần trigger độc lập**. Khi cả hai tiếp tục submit `@Async`, có thể có thêm nhiều worker. Local `AtomicBoolean` không ngăn bản khác trên pod khác. Với invariant “chỉ một job ghi”, dùng distributed lease/lock với fencing hoặc conditional DB update và idempotency. Không hứa “fixedDelay 5 giây nghĩa là mỗi 5 giây toàn hệ có một tác vụ”.

## 18. Monitoring: đo hai chiếc đồng hồ

Một hệ thống cần theo dõi đồng thời:

- **Trigger clock:** due time, actual invocation start/end, scheduler lag.
- **Worker clock:** queue wait, actual start/end, duration, exception.
- **Business clock:** commit/ack, timeout, effect applied, retry.
- **Ownership/version:** operation ID, generation, expected/current version, instance/pod.

Ví dụ log events:

```text
run=42 event=TRIGGERED time=t0
run=42 event=SUBMITTED time=t1 executor=workExecutor
run=42 event=WORKER_STARTED time=t2
run=43 event=TRIGGERED time=t3
run=42 event=BUSINESS_COMMITTED time=t4
run=42 event=FUTURE_COMPLETED time=t5
```

Nếu `t3 < t4`, tick sau tới trước khi job trước commit. Một log “scheduled method completed” ở `t1` không thể thay cho `BUSINESS_COMMITTED` ở `t4`.

### 18.1. Gợi ý metrics

| Metric | Diễn giải |
| --- | --- |
| `scheduled_invocations_total` | Số lần scheduled method thực sự được gọi |
| `async_submissions_total` | Số lần gọi executor được chấp nhận |
| `async_rejections_total` | Số lần executor từ chối |
| `async_inflight` | Số worker đang chạy hoặc chờ theo policy |
| `async_queue_wait_seconds` | Thời gian từ submit đến actual start |
| `business_duration_seconds` | Thời gian nghiệp vụ thực tế |
| `business_success_total` | Số job hoàn thành thành công |
| `business_failure_total` | Số job thất bại |
| `overlap_skipped_total` | Số tick bị bỏ qua do single-flight policy |
| `stale_commit_rejected_total` | Số tác dụng phụ cũ bị từ chối theo version |
| `inflight_on_shutdown` | Số job chưa xong khi shutdown |

Không dùng một execution ID duy nhất làm label metric vì cardinality quá cao; giữ ID trong structured logs/traces.

## 19. Quy trình debug khi “fixedDelay không còn đợi job”

1. **Đọc BỘ ĐÔI annotation:** `@Scheduled` nằm ở đâu, `@Async` nằm ở đâu?
2. **Vẽ đường gọi:** scheduler → method theo lịch → Spring proxy → worker → DB/API.
3. **Xác định scheduler bean:** `ThreadPoolTaskScheduler`, `SimpleAsyncTaskScheduler`, custom bean, hay Boot auto-config?
4. **Xác định worker executor:** `@Async("...")` dùng đúng bean nào? Có queue và rejection policy không?
5. **Kiểm tra invocation:** scheduled method có `join/get` hay chỉ submit?
6. **Kiểm tra proxy:** có `@EnableAsync`, gọi qua Spring-managed bean, tránh `this.async()`/`new Worker()`?
7. **Đặt log time/thread:** scheduled start/end, worker start/end, business commit, future completion.
8. **Theo dõi fixed-delay:** khoảng nghỉ tính từ scheduled method return hay từ business completion thật?
9. **Kiểm tra overlap:** guard, single-flight, executor concurrency limit và multi-replica?
10. **Kiểm tra lỗi:** dispatch error vs worker future error, `AsyncUncaughtExceptionHandler` và application error handler.
11. **Kiểm tra transaction:** có worker được chạy trước commit hoặc ghi stale data không?
12. **Kiểm tra shutdown:** còn worker đã handoff khi trigger dừng không?

### 19.1. Bảng lỗi và hướng sửa

| Triệu chứng | Nguyên nhân khả dĩ | Hướng sửa |
| --- | --- | --- |
| Tick sau đến dù job trước chưa xong | Scheduled method submit async rồi return | Dùng completion-aware scheduling/guard |
| Bỏ `@Async` thì “chờ đúng” | Invocation trở thành synchronous | Định nghĩa lại completion boundary rõ ràng |
| Có `@Async` nhưng vẫn chạy trên scheduler thread | Self-invocation/bean tự new/thiếu hạ tầng | Tách bean và kiểm tra proxy |
| `fixedDelay` chạy chậm nhưng `workExecutor` rảnh | Scheduler method vẫn blocking | Đo scheduled invocation duration |
| Async pool nhiều task xếp hàng | Trigger nhanh hơn throughput | Admission control, bounded queue, skip/coalesce |
| Ghi dữ liệu trùng | Overlap hoặc nhiều replica | Idempotency, unique constraint, distributed coordination |
| Scheduler logs “success” nhưng worker lỗi | Future bị bỏ, failure ở worker | Return/observe future, job status |
| `ScheduledFuture.get` trả sớm | Handle phản ánh scheduler invocation/handoff | Theo dõi future business riêng |
| `CompletableFuture` từ `@Scheduled` bị lỗi setup | Non-deferred reactive adapter không phù hợp | Dùng `void` bridge hoặc Publisher deferred đúng API |
| Shutdown scheduler nhưng email vẫn được gửi | Worker đã handoff sang executor khác | Quản lý cả scheduler lẫn executor/durable state |
| Spring Boot config pool-size không tác dụng | Actual scheduler khác kiểu kỳ vọng | Kiểm tra runtime beans/conditions |
| Job mới bắt đầu khi transaction cũ chưa commit | Async work được submit trước commit | AFTER_COMMIT hoặc outbox |

## 20. Kiểm thử deterministic với latch

Điểm mạnh của `ScheduledAsyncCompletionDemo` là không kiểm tra “sau 100ms chắc worker còn chạy” bằng `Thread.sleep`. Thay vào đó nó dùng **hai sự kiện đồng bộ hóa**:

1. `workerStarted` cho biết worker đã bắt đầu và đang chờ release.
2. `secondTrigger` cho biết scheduler thực sự đã chạy tick tiếp theo.

Chỉ khi cả hai được xác nhận và **trước khi** `releaseWorker` được mở, test mới kết luận lần trigger thứ hai đã đến trong lúc worker đầu còn bận.

Một integration test Spring tốt còn cần:

- Timeout cho mọi `await`/`get` để tránh CI treo mãi.
- `try/finally` đảm bảo release latch và đóng executor/context.
- Kiểm tra tên thread hoặc `Thread.isVirtual()` phù hợp runtime.
- Kiểm tra `@Async` proxy có thực sự áp dụng.
- Không giả định chính xác số mili giây giữa các tick trên CI.
- Test executor saturation/rejection.
- Test future complete exceptionally, và callback handling.
- Test stop/restart/multiple contexts khi nghiệp vụ yêu cầu.
- Test nhiều replica nếu invariant toàn cụm.

### 20.1. Bài test “chỉ có một job đang chạy”

Khi dùng guard local `AtomicBoolean`, cố tình giữ worker đầu bằng latch, kích hoạt tick thứ hai, kiểm tra tick thứ hai bị skip. Sau đó release, đợi future complete rồi kích hoạt tick thứ ba; xác nhận guard đã được reset và job mới được phép chạy. Nếu lỗi trong callback `recordOutcome` không reset guard, test phải phát hiện và sửa.

### 20.2. Bài test completion-aware

Với scheduler tự `scheduleNext` sau khi future complete, giữ worker bằng latch. Kiểm tra rằng **không có lần schedule tiếp theo** được đăng ký trước khi future hoàn tất, rồi release để xác nhận lần kế tiếp được đặt lịch sau delay đã cấu hình. Instrument lời gọi `TaskScheduler.schedule(...)` hoặc dùng test scheduler/fake clock để tránh phụ thuộc wall clock.

## 21. Bài tập ứng dụng

### Bài 1 — Vẽ bốn mốc

Cho `@Scheduled(fixedDelay=1_000)` gọi một `@Async` worker chạy 3 giây. Vẽ:

- Scheduled invocation bắt đầu.
- Executor nhận task.
- Scheduled invocation kết thúc.
- Business worker hoàn thành.

Dự đoán thời điểm tick tiếp theo và giải thích vì sao fixed-delay vẫn được tuân thủ ở tầng scheduler dù worker chưa xong.

### Bài 2 — Chứng minh bằng mã chạy

Chạy `ScheduledAsyncCompletionDemo`, quan sát:

```text
second-tick-before-work-finished=true
worker-on-async-executor=true
work-finished-after-release=true
```

Sau đó thay bridge bằng synchronous method (trong một project thử nghiệm riêng) và phân tích vì sao scheduled thread không thể đến tick thứ hai trước khi worker được release.

### Bài 3 — Ba cấu hình lập lịch

Thử `fixedDelay`, `fixedRate` và cron với cùng async worker. Ghi lại trigger invocation duration, worker duration, số task đang chạy, queue wait và overlap. **Không kết luận** bằng quan sát một lần trên máy local rằng mọi scheduler luôn chồng lấn hoặc luôn không chồng lấn.

### Bài 4 — Giữ tính chất completion-based delay

So sánh ba giải pháp:

1. Logic đồng bộ ngay trong `@Scheduled`.
2. Gọi async rồi `join()`.
3. Programmatic completion-aware rescheduling.

Nêu ưu/nhược về blocking thread, exception, shutdown, backpressure và semantics fixed-delay.

### Bài 5 — Sửa self-invocation

Cho `@Scheduled` và `@Async` nằm ở hai method của cùng class, dùng `this.asyncMethod()`. Giải thích tại sao annotation có thể bị bỏ qua. Refactor thành hai bean và kiểm tra thread names cùng completion behavior.

### Bài 6 — Chống trùng và stale update

Viết guard skip-while-running cho refresh cache; sau đó giả lập hai application instances để chứng minh local guard không đủ. Thiết kế optimistic version/conditional update hoặc distributed fencing phù hợp với state cần bảo vệ.

### Bài 7 — Transactional notification

Một lịch quét DB rồi gửi email bằng `@Async`. Tạo tình huống transaction cập nhật trạng thái chưa commit khi worker bắt đầu; kiểm chứng khác biệt giữa send-before-commit và AFTER_COMMIT/outbox, cũng như tình huống email đã gửi nhưng future bị timeout.

### Bài 8 — Reactive scheduled methods

Trên Spring Framework 6.1+, nghiên cứu `@Scheduled(fixedDelay)` với một deferred `Publisher` hợp lệ. Giải thích vì sao subscription completion có semantics khác với việc trả một `CompletableFuture` non-deferred hoặc một `void` bridge submit `@Async`.

## 22. Checklist trước khi đưa vào production

- [ ] Tôi phân biệt được invocation của `@Scheduled` và completion của `@Async` worker.
- [ ] Tôi hiểu `fixedDelay` tính từ thời điểm invocation mà scheduler theo dõi kết thúc.
- [ ] Tôi không nhầm return sau submit với hoàn thành nghiệp vụ.
- [ ] Tôi hiểu `fixedRate`/cron không tự ngăn business async overlap.
- [ ] Tôi biết `@EnableScheduling` và `@EnableAsync` là hai hạ tầng riêng.
- [ ] Tôi gọi `@Async` qua Spring proxy và tránh self-invocation.
- [ ] Tôi biết scheduler và executor có thể là hai bean với queue/lifecycle khác nhau.
- [ ] Tôi không dùng `ScheduledFuture` làm bằng chứng business effect đã commit.
- [ ] Tôi giữ/quan sát `CompletableFuture` nếu job có kết quả/lỗi bắt buộc.
- [ ] Tôi kiểm soát dispatch rejection khác với worker exception.
- [ ] Tôi chọn chính sách overlap rõ: allow, skip, queue, coalesce hoặc completion-aware.
- [ ] Tôi hiểu đợi `join()` trong scheduled method sẽ block scheduler thread.
- [ ] Tôi không coi reactive `@Scheduled` và `CompletableFuture` thường là tương đương.
- [ ] Tôi không để `@Async` chạy trước commit khi effect yêu cầu dữ liệu đã commit.
- [ ] Tôi có timeout, idempotency, retry và durable job state theo nhu cầu.
- [ ] Tôi có test latch kiểm tra second tick xảy ra lúc worker đầu chưa xong.
- [ ] Tôi biết local scheduler/guard không bảo đảm một tác vụ toàn cụm.
- [ ] Tôi quản lý shutdown của cả scheduler và async executor.

**Kết luận:** phối hợp `@Scheduled` với `@Async` **không chỉ đổi thread**, mà có thể **đổi luôn “đối tượng” mà scheduler coi là đã hoàn tất**. Scheduler có thể thấy method bridge đã xong ngay sau submit, trong khi worker và tác dụng phụ vẫn chạy. Vì vậy, nếu yêu cầu nghiệp vụ là “chờ công việc thật kết thúc rồi mới tính delay”, bạn phải thiết kế completion boundary rõ ràng: đồng bộ hóa trong scheduled method, chờ future một cách có kiểm soát hoặc tự lên lịch dựa trên future completion. Nếu chỉ cần kích hoạt định kỳ, phải chấp nhận và quản lý overlap, queue, lỗi cùng độ bền riêng.

## 23. Tài liệu chính thức

- Spring Framework — [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@Scheduled`, `@Async`, fixed-delay/fixed-rate, executor qualification, reactive scheduled methods.
- Spring Framework Javadoc — [@Scheduled](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): no-arg method, return semantics, repeatable schedules, trigger metadata.
- Spring Framework Javadoc — [@Async](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Async.html): proxy async invocation, return type và executor.
- Spring Framework Javadoc — [ThreadPoolTaskScheduler](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): scheduler threads và lifecycle.
- Spring Framework Javadoc — [SimpleAsyncTaskScheduler](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/SimpleAsyncTaskScheduler.html): trigger/handoff distinction, fixed-delay exception và virtual threads.
- Java SE 17 — [CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): completion/exception/cancellation semantics.
- Java SE 17 — [ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html): fixed-delay và fixed-rate ở Java cơ bản.

Đối chiếu phiên bản Spring Framework/Spring Boot/JDK của dự án khi triển khai. Các phần đánh dấu **fragment** hoặc **phản mẫu** không phải những file có thể chạy riêng; ví dụ có tên file, POM, lệnh biên dịch và stdout là bài thực hành độc lập.


</details>

- [Quay lại đầu trang](#back-to-top)
