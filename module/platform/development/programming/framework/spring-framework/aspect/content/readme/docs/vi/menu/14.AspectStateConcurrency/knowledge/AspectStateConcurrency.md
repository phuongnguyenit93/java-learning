<a id="back-to-top"></a>

# Trạng thái và đồng thời

## Menu
- [Aspect singleton được nhiều lời gọi sử dụng đồng thời](#aspect-state-concurrency-001)
- [Giữ dữ liệu riêng cho lời gọi trong biến cục bộ](#aspect-state-concurrency-002)
- [Nguy cơ lưu JoinPoint, người dùng hoặc thời gian bắt đầu vào field](#aspect-state-concurrency-003)
- [Phụ thuộc được tiêm vào aspect và yêu cầu an toàn luồng](#aspect-state-concurrency-004)
- [Scope của bean đích không tự thay đổi scope của aspect](#aspect-state-concurrency-005)
- [ThreadLocal trong aspect và trách nhiệm dọn dữ liệu](#aspect-state-concurrency-006)
- [Chuyển luồng, @Async và giới hạn ngữ cảnh gắn với luồng](#aspect-state-concurrency-007)
- [Luồng reactive và thời điểm phương thức trả đối tượng Publisher](#aspect-state-concurrency-008)
- [Đo đạc khi có lời gọi lồng nhau hoặc đệ quy qua proxy](#aspect-state-concurrency-009)
- [Kiểm thử trạng thái chéo giữa nhiều yêu cầu đồng thời](#aspect-state-concurrency-010)

## <a id="aspect-state-concurrency-001">Aspect singleton được nhiều lời gọi sử dụng đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: Aspect singleton được nhiều lời gọi sử dụng đồng thời

## 1. Bản chất vấn đề

Trong Spring Framework, một aspect khai báo bằng `@Aspect` và đăng ký như Spring bean (ví dụ `@Component`) **mặc định dùng singleton instantiation model**: về cơ bản có **một instance của aspect trong một ApplicationContext**. Cùng instance này có thể xử lý advice cho **nhiều lời gọi và nhiều luồng** đồng thời.

```java
@Aspect
@Component
public class AuditAspect {
    @Before("execution(* demo.concurrent.WorkerService.process(..))")
    public void before(JoinPoint jp) {
        System.out.println(jp.getSignature().getName());
    }
}
```

Đây là **fragment**: cần có imports, Spring configuration, bean đích và caller đi qua AOP proxy. Không có quy tắc nào nói rằng Spring sẽ tạo một `AuditAspect` mới cho mỗi invocation `process()`. Nếu ứng dụng web nhận nhiều requests hoặc hai worker threads cùng gọi service, lời gọi advice có thể **đè thời gian lên nhau** trong cùng một object aspect.

Phải phân biệt ba câu hỏi:

1. **Có bao nhiêu instance aspect?** Mặc định một aspect singleton cho mỗi bean/application context tương ứng.
2. **Có bao nhiêu lần advice được gọi?** Tương ứng những invocations match pointcut và thật sự đi qua Spring AOP proxy.
3. **Bao nhiêu luồng có thể thực thi cùng advice?** Có thể nhiều luồng cùng lúc; Spring không tự `synchronized` toàn bộ method advice chỉ vì aspect là singleton.

**Kết luận:** Singleton **không có nghĩa** single-threaded, không phải mutex, và không tự bảo đảm thread safety. Vấn đề quan trọng nhất là **state nào nằm trong field của aspect** và **state nào chỉ tồn tại cục bộ trong mỗi lần advice invocation**.

Bài tập trung hoàn toàn vào **Spring Aspect / Spring AOP proxy**, concurrency của aspect instance, điểm đặt state và những giới hạn proxy/lifecycle. Không đi sâu vào AOP nói chung hoặc AspectJ compile-time/load-time weaving.

## 2. Singleton của Spring là theo container, không phải toàn JVM

Spring IoC định nghĩa singleton scope theo **một bean definition trong một container**:

- Cùng Spring bean name trong cùng ApplicationContext thường trả lại cùng object được quản lý.
- Có **hai ApplicationContext độc lập** thì có thể có hai instance aspect tương ứng, nếu mỗi context tạo aspect bean.
- Không đồng nhất Spring singleton scope với GoF singleton dựa trên `static instance` toàn JVM/classloader.
- Singleton aspect **khác** singleton target service: đó có thể là hai bean definitions khác nhau, được khởi tạo và quản lý độc lập.

Cấu hình quen thuộc:

```java
@Configuration
@ComponentScan("demo.concurrent")
@EnableAspectJAutoProxy
public class AppConfig {}
```

Trong package được scan, Spring thấy `@Aspect @Component` rồi đăng ký aspect bean. Hạ tầng Spring AOP tạo advisor từ các advice và gắn chúng vào Spring proxy cho target beans phù hợp.

Một người có thể gọi:

```java
var context = new AnnotationConfigApplicationContext(AppConfig.class);
AuditAspect first = context.getBean(AuditAspect.class);
AuditAspect second = context.getBean(AuditAspect.class);
System.out.println(first == second); // true trong cùng context singleton.
```

Đây là **fragment**, nên đóng context khi dùng xong. Nó chứng minh hai lần tra cứu bean trả cùng reference; **không chứng minh advice chỉ thực thi một lần**. Một object có thể có một triệu lần gọi method, kể cả nhiều lần đang chạy song song.

### Khi nào aspect được tạo?

- Spring tạo bean/đăng ký advisor trong quá trình context khởi tạo theo bean lifecycle và cấu hình.
- Khi invocation đi qua proxy và pointcut khớp, advice method được gọi trên aspect instance đã được Spring quản lý.
- Advice không được tạo mới theo từng request chỉ vì một HTTP request mới xuất hiện.
- `@PostConstruct` (nếu dùng) trên aspect nói về **khởi tạo bean**, không phải hook “trước mỗi invocation”.

**Không giữ request data trong field của singleton aspect** chỉ vì field được gán bên trong một before advice.

## 3. Sơ đồ hai lời gọi song song trên cùng một aspect

```text
                     Spring ApplicationContext
                              |
                       singleton AuditAspect
                         shared fields
                         /          \
                        /            \
               Thread A             Thread B
                  |                    |
       Spring proxy.process("A")  Spring proxy.process("B")
                  |                    |
          advice invocation A   advice invocation B
          local variables A     local variables B
                  |                    |
                  +---- cùng một object aspect ----+
                  |                    |
              Target A             Target B
                  |                    |
           return "done-A"       return "done-B"
```

Những thứ **có thể chia sẻ**:

- Các instance fields của aspect: `currentId`, `startTime`, counter, cache, map, collection...
- Các collaborator objects được inject vào aspect: repository, metrics registry, logger wrapper, service...
- Static fields/global state do ứng dụng tự tạo.
- Trạng thái của **singleton target bean** nếu target cũng là singleton.

Những thứ **riêng cho mỗi lần invocation**:

- Method parameters của advice, ví dụ `JoinPoint jp`, `ProceedingJoinPoint pjp`.
- Local variables tạo bên trong advice, ví dụ `String id`, `long startedAt`.
- Kết quả của `proceed()` và exception của một invocation cụ thể (trừ khi bạn cố ý gán chúng vào shared state).

Không cần tạo một aspect object cho từng invocation để bảo vệ local variables: **mỗi lời gọi Java có stack frame riêng**. Nhưng nếu một local variable tham chiếu tới **một object mutable chia sẻ**, object đó vẫn có thể chịu race condition.

## 4. Phản ví dụ: lưu dữ liệu từng request vào field

```java
@Aspect
@Component
public class UnsafeAuditAspect {
    private String currentRequestId; // SHARED giữa các lời gọi.

    @Before("execution(* demo.concurrent.WorkerService.process(..))"
          + " && args(requestId)")
    public void before(String requestId) {
        currentRequestId = requestId; // Ghi đè state singleton.
    }

    @AfterReturning("execution(* demo.concurrent.WorkerService.process(..))")
    public void after() {
        System.out.println("completed request=" + currentRequestId);
    }
}
```

Đây là **phản ví dụ cố ý không an toàn** và không thuộc chương trình runnable ở mục 8. Dù field `currentRequestId` chỉ có một String, logic ghi rồi đọc xuyên qua hai advice calls khác nhau đã ràng buộc dữ liệu của **invocation A** vào shared field của **aspect bean**.

Một interleaving hợp lệ:

```text
Thread A: before("A") -> currentRequestId = "A"
Thread B: before("B") -> currentRequestId = "B"
Thread A: target A returns
Thread A: after() -> đọc currentRequestId là "B" (SAI)
Thread B: after() -> cũng có thể đọc "B"
```

Không có mối liên hệ invocation-A riêng biệt được Spring tạo ra cho field. `@Order` hay tên method khác nhau không ngăn hai threads xen kẽ nhau. **Việc advice “vào trước, ra sau” áp dụng cho một invocation chain**, không biến toàn bộ singleton aspect thành vùng critical section.

### 4.1. Thêm `volatile` đã đủ chưa?

```java
private volatile String currentRequestId;
```

**Chưa đủ.** `volatile` có thể cung cấp visibility của giá trị mới nhất, nhưng không gắn giá trị A với invocation A. Trong ví dụ, thread A càng dễ nhìn thấy giá trị B được cập nhật mới. Mục tiêu không phải “đọc giá trị mới nhất”, mà là **đọc đúng dữ liệu thuộc request hiện tại**.

### 4.2. `synchronized` advice có phải giải pháp?

Nếu chỉ đặt `synchronized` trên `before()` và `after()`, hai method calls được khóa riêng rẽ, **không có lock giữ xuyên suốt target execution**. Lời gọi A có thể nhường chỗ cho before B rồi mới vào after A, nên vẫn sai.

Kể cả khóa toàn bộ `@Around`:

```java
@Around("execution(* demo.concurrent.WorkerService.process(..))")
public synchronized Object serializeAll(ProceedingJoinPoint pjp)
        throws Throwable {
    return pjp.proceed();
}
```

đoạn trên chỉ là **fragment**, không phải khuyến nghị. Nó làm một số invocation theo cùng aspect instance chạy tuần tự, có thể tạo nút thắt cổ chai và tác động tới latency/throughput; việc dùng `synchronized` không tự bao quát mọi đường gọi bypass proxy, callback async hoặc các bean contexts khác. Đối với dữ liệu per-invocation, **local variables** tốt hơn là khóa toàn bộ business operation.

### 4.3. `AtomicReference<String>` có sửa “current request” không?

Không. `AtomicReference` làm các thao tác cập nhật/đọc reference trở nên atomic và có memory visibility; nó vẫn đại diện cho **một ô giá trị dùng chung**. Khi A ghi rồi B ghi, A không có cách biết giá trị sau cùng thuộc A. Sử dụng atomic references cho **shared coordination state**, không thay thế context theo invocation.

## 5. An toàn nhất: dữ liệu theo invocation nằm trong local variables

Với yêu cầu log/timing trước và sau method, Spring khuyến nghị `@Around` vì nó có thể giữ cả hai giá trị trên **cùng stack frame của một advice invocation**:

```java
@Aspect
@Component
public class SafeAuditAspect {
    @Around("execution(* demo.concurrent.WorkerService.process(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String requestId = (String) pjp.getArgs()[0]; // Local.
        long started = System.nanoTime();             // Local.

        try {
            Object result = pjp.proceed();
            System.out.println("finished " + requestId
                    + " in " + (System.nanoTime() - started) + " ns");
            return result;
        } catch (Throwable ex) {
            System.out.println("failed " + requestId);
            throw ex;
        }
    }
}
```

Đoạn trên là **fragment minh họa**, giả định method `process(String)` đúng signature và có một argument kiểu String. Nếu pointcut rộng cho nhiều signatures, phải kiểm tra `getArgs().length` và types. Trong production, requestId có thể nhạy cảm: dùng log policy/redaction phù hợp.

**Vì sao đúng hơn?**

- Mỗi lời gọi `around(...)` có `requestId` và `started` riêng.
- Thread B không thể gán trực tiếp vào local variable của stack frame thread A.
- `pjp.proceed()` có thể chạy các interceptors khác hoặc target, rồi return về **cùng** frame chứa `started`.
- Exception được rethrow, result được giữ nguyên. Không vô tình nuốt exception hoặc thay return value.
- Aspect có thể vẫn chỉ có **một instance** trong Spring context; không cần tạo aspect mới.

**Một giới hạn:** `@Around` đo thời gian cho phần invocation đồng bộ bên trong `proceed()`. Nếu method trả `CompletableFuture<T>` hoặc reactive publisher chưa hoàn tất, elapsed time này có thể chỉ là thời gian tạo wrapper, không phải thời gian hoàn thành tác vụ async. Thời gian completion cần được đo bằng API completion/reactive tương ứng.

## 6. Khi thực sự cần shared metrics thì dùng cấu trúc thread-safe

Không phải **mọi field** đều xấu. Một Spring aspect có thể giữ immutable configuration hoặc **state chia sẻ có mục đích và thread-safe**.

### 6.1. Immutable dependencies và config

```java
@Aspect
@Component
public class PolicyAspect {
    private final String environment = "demo";
    private final AuditPort auditPort;

    public PolicyAspect(AuditPort auditPort) {
        this.auditPort = auditPort;
    }
}
```

Đây là fragment, `AuditPort` là interface giả định. `final` giúp cố định reference sau khởi tạo, nhưng nếu `auditPort` trỏ tới một mutable object không thread-safe thì việc inject vào singleton aspect **không tự biến collaborator đó thành thread-safe**.

Spring chịu trách nhiệm lifecycle và safe publication của singleton bean state được hoàn tất trong giai đoạn initialization thông thường. Nếu config field được mutate **sau** khi bean đã công bố, phải áp dụng concurrency control thích hợp như `volatile` hoặc lock; Spring không tự đồng bộ runtime mutations của bạn.

### 6.2. Counters: `AtomicInteger` / `LongAdder`

```java
private final AtomicInteger active = new AtomicInteger();
private final AtomicInteger peak = new AtomicInteger();
private final LongAdder completed = new LongAdder();

int now = active.incrementAndGet();
peak.accumulateAndGet(now, Math::max);

try {
    return pjp.proceed();
} finally {
    active.decrementAndGet();
    completed.increment();
}
```

Đây là fragment dành cho `@Around`, cần các imports từ `java.util.concurrent.atomic`. Các counters này là **shared aggregate state**, không phải request-specific data:

- `active`: số invocations hiện trong đoạn `try/finally` theo mức quan sát của aspect.
- `peak`: giá trị concurrent cao nhất quan sát được theo atomic accumulation.
- `completed`: số lần rời advice, bao gồm cả exceptional exits nếu `finally` chạy.
- `LongAdder` thường phù hợp counters chịu contention cao, nhưng `sum()` khi đang cập nhật đồng thời không phải snapshot tuyến tính hoàn toàn; các kết quả quan sát metrics có thể là gần thời điểm đọc.
- Counter metrics không chứng minh target method đã thành công; cần naming/logic riêng nếu bạn chỉ muốn đếm success.

**Không** dùng `int count++` không đồng bộ trên singleton aspect để đếm chính xác mọi lời gọi: phép tăng gồm đọc-sửa-ghi, nên concurrent updates có thể bị mất.

### 6.3. Map/cache chia sẻ

Nếu cần lookup/cache metrics theo method name hoặc tag ổn định, có thể dùng `ConcurrentHashMap` hoặc library cache có concurrency policy phù hợp. Nhưng thread-safe map không tự bảo vệ **mutable value objects** trong map. Cần quy tắc expire/evict, cardinality control và tránh key chứa PII.

Ví dụ sơ lược:

```java
private final ConcurrentHashMap<String, LongAdder> counts =
        new ConcurrentHashMap<>();

void countByMethod(String name) {
    counts.computeIfAbsent(name, key -> new LongAdder()).increment();
}
```

Fragment này phù hợp với keys có cardinality được quản lý; không nên dùng raw request IDs làm keys mãi mãi, vì dễ gây memory growth. Trong ứng dụng thực, hệ thống metrics như Micrometer/Observation có thể giảm việc tự quản lý counter lifecycle.

## 7. Khi state phải sống ngoài một advice invocation

### 7.1. `ThreadLocal` chỉ tách theo thread

Một `ThreadLocal<String>` có thể phục vụ code đồng bộ sâu bên trong **cùng thread**, nhưng không phải request scope: thread pool tái sử dụng thread, nested proxy calls có thể ghi đè context, và executor không tự truyền giá trị sang thread khác. Nếu phải dùng, lưu giá trị cũ rồi restore/remove trong `finally`:

```java
String previous = operationContext.get();
operationContext.set(operationId);
try {
    return pjp.proceed();
} finally {
    if (previous == null) operationContext.remove();
    else operationContext.set(previous);
}
```

Đây là fragment cần `operationContext`, `operationId` và `pjp`. Khi chỉ cần đo thời gian cho một `@Around`, local `long started` vẫn đơn giản hơn; bài ThreadLocal phía sau sẽ kiểm tra nesting, MDC và thread reuse bằng test.

### 7.2. Request-scoped dependency không biến aspect thành per-request

Aspect singleton có thể sử dụng một request-scoped collaborator qua scoped proxy/provider trong web-aware context. Scope quyết định **target dependency được resolve thế nào**; aspect instance không tự đổi scope. Callback chạy khi request context không còn hoạt động cần xử lý riêng, đặc biệt ở background tasks. Không đổi `@Aspect` sang `@RequestScope` rồi coi mọi rủi ro concurrency đã hết.

### 7.3. Completion của async/reactive khác lúc method return

`@Around` thông thường kết thúc sau `pjp.proceed()`. Nếu target trả `CompletableFuture` chưa hoàn thành hoặc `Mono` chưa subscribe, duration chỉ đo boundary đồng bộ. Muốn ghi thời gian hoàn tất, gắn đo tại completion callback hoặc reactive subscription/terminal operator, có state riêng và lifecycle rõ ràng. Bài `@Async` và Reactor phía sau phân tích cách propagate context giữa các luồng và subscriptions.

## 8. Ví dụ chạy được: một singleton aspect xử lý hai invocation đồng thời

Dự án sau dùng **Java 17, Maven và Spring Framework 6.2.x** (không cần Spring Boot). Nó cố ý tránh log bên trong hai worker threads để stdout **có thứ tự ổn định**. Hai lời gọi vẫn được chứng minh **thực sự overlap** nhờ `CountDownLatch` trong target.

Mục tiêu quan sát:

1. Hai lần `getBean(MonitorAspect.class)` trong cùng context cho một instance.
2. Consumer nhận Spring AOP proxy qua `WorkerPort`.
3. Hai threads có thể đi vào **cùng advice method** song song; chỉ số `peak=2` xác nhận overlap.
4. Dữ liệu theo invocation `id` nằm trong local variable, không trong field.
5. Counter `AtomicInteger` an toàn khi increment/decrement; sau hai lời gọi `activeAfter=0`.
6. Kết quả từ hai requests không bị trộn: sau khi sort, `[done-A, done-B]`.

### 8.1. Cấu trúc thư mục

```text
spring-aop-concurrent-aspect/
├── pom.xml
└── src/main/java/demo/concurrent/
    ├── AppConfig.java
    ├── WorkerPort.java
    ├── WorkerService.java
    ├── MonitorAspect.java
    └── Main.java
```

### 8.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-concurrent-aspect</artifactId>
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
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.12</version>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.24</version>
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
                <version>3.5.1</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 8.3. `AppConfig.java`

```java
package demo.concurrent;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {
}
```

Spring component scan đăng ký service và aspect. Với `WorkerService implements WorkerPort` và `proxyTargetClass=false`, Spring Framework thuần ưu tiên **JDK dynamic proxy** cho service.

### 8.4. `WorkerPort.java`

```java
package demo.concurrent;

public interface WorkerPort {
    String process(String id);
}
```

Consumer chỉ cần interface. JDK proxy triển khai interface này; không cast nó thành concrete target service.

### 8.5. `WorkerService.java`

```java
package demo.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class WorkerService implements WorkerPort {
    private final CountDownLatch bothEntered = new CountDownLatch(2);

    @Override
    public String process(String id) {
        bothEntered.countDown();

        try {
            if (!bothEntered.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Expected two concurrent calls");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Worker interrupted", ex);
        }

        return "done-" + id;
    }
}
```

`CountDownLatch(2)` giữ lời gọi thứ nhất ở target cho tới khi lời gọi thứ hai cũng vào. Do đó không dựa vào cách scheduler in log xen kẽ để **chứng minh hai invocations cùng lúc**.

Target chỉ phục vụ bài demo đúng **hai invocations trong một context**; latch không tự reset và sẽ không đại diện cho mỗi request pair trong một ứng dụng thực. Việc dùng synchronized trên target sẽ khiến ví dụ deadlock/timeout; đó cũng chính là lý do không khóa toàn bộ lời gọi chỉ để làm metrics.

### 8.6. `MonitorAspect.java`

```java
package demo.concurrent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MonitorAspect {
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();
    private final LongAdder elapsedNanos = new LongAdder();

    @Around("execution(* demo.concurrent.WorkerService.process(..))")
    public Object monitor(ProceedingJoinPoint pjp) throws Throwable {
        String id = (String) pjp.getArgs()[0]; // Riêng mỗi invocation.
        long started = System.nanoTime();       // Riêng mỗi invocation.

        int now = active.incrementAndGet();
        peak.accumulateAndGet(now, Math::max);

        try {
            Object result = pjp.proceed();

            if (!("done-" + id).equals(result)) {
                throw new IllegalStateException("Mismatched result for " + id);
            }
            return result;
        } finally {
            elapsedNanos.add(System.nanoTime() - started);
            active.decrementAndGet();
        }
    }

    public int active() {
        return active.get();
    }

    public int peak() {
        return peak.get();
    }

    public long elapsedNanos() {
        return elapsedNanos.sum();
    }
}
```

Điểm quan trọng:

- Aspect có ba **shared fields** nhưng chúng thuộc nhóm aggregate/metrics có cơ chế thread-safe; không giữ `id` hoặc `started` trong field.
- Mỗi lần advice gọi `proceed()` đúng **một** lần.
- `id` và `started` là local, nên hai threads không ghi đè dữ liệu của nhau.
- `finally` đưa `active` về đúng 0 khi hai invocations hoàn thành, kể cả một invocation ném exception.
- `peak.accumulateAndGet(now, Math::max)` giữ max concurrent count một cách atomic.
- `elapsedNanos.sum()` có thể dùng cho aggregate timing, nhưng demo không in thời gian vì số nanoseconds phụ thuộc máy và scheduling.
- Validation `"done-" + id` chỉ để chứng minh **per-invocation identity**; trong logging aspect sản xuất, không hardcode target result contract như vậy.

### 8.7. `Main.java`

```java
package demo.concurrent;

import java.util.List;
import java.util.concurrent.Executors;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) throws Exception {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            WorkerPort port = context.getBean(WorkerPort.class);
            MonitorAspect aspect = context.getBean(MonitorAspect.class);

            System.out.println("singletonAspect="
                    + (aspect == context.getBean(MonitorAspect.class)));
            System.out.println("jdkProxy="
                    + AopUtils.isJdkDynamicProxy(port));

            var pool = Executors.newFixedThreadPool(2);
            try {
                var a = pool.submit(() -> port.process("A"));
                var b = pool.submit(() -> port.process("B"));

                List<String> results = List.of(a.get(), b.get())
                        .stream().sorted().toList();

                System.out.println("results=" + results);
                System.out.println("peak=" + aspect.peak());
                System.out.println("activeAfter=" + aspect.active());
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
```

Chạy từ thư mục chứa `pom.xml`, máy cần JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.concurrent.Main
```

**Stdout dự kiến nếu hai worker threads đều chạy trong timeout 10 giây** (không tính log Maven/Spring riêng):

```text
singletonAspect=true
jdkProxy=true
results=[done-A, done-B]
peak=2
activeAfter=0
```

Đây là ví dụ **deterministic về thứ tự print** vì chỉ thread main in; các worker thực thi song song nhưng không in stdout. Nếu môi trường cực kỳ chậm, deadline 10 giây có thể khiến `CountDownLatch.await(...)` timeout và chương trình báo lỗi thay vì ra stdout trên. Đây là ràng buộc môi trường của demo, không phải kết quả bình thường của Spring AOP.

### 8.8. Giải thích vì sao `peak=2`

1. Main lấy bean `WorkerPort` qua context, nên lời gọi đi qua AOP proxy. Aspect được đăng ký Spring singleton.
2. Thread A đi vào `MonitorAspect.monitor(...)`, tăng `active` lên 1, sau đó tiến đến `WorkerService.process("A")` và đợi latch.
3. Thread B đi vào **cùng MonitorAspect instance**, tăng `active` lên 2, cập nhật `peak` thành 2 rồi tiến tới target.
4. Hai target invocations gặp latch và đều có thể trở về; không có sự khóa tuần tự trong aspect.
5. Mỗi around advice instance giữ `id`/`started` trong local variables, bảo toàn result tương ứng A và B.
6. Cả hai advice invocations rời `finally` và giảm counter. Khi Main gọi `Future.get()` thành công cho cả hai, `activeAfter=0`.
7. Thứ tự tasks hoàn thành là không chắc chắn, nhưng kết quả được sorted nên in `[done-A, done-B]` một cách ổn định.

**Kiểm chứng khác biệt:** nếu thêm `synchronized` trên method `monitor(...)`, thread A có thể giữ lock và đợi B tại latch, trong khi B không thể vào advice để tới target; kết quả là timeout. Đây là lý do dùng local state và thread-safe metrics thay vì serialize toàn bộ business execution.

## 9. Mô hình aspect instantiation nâng cao không đồng nghĩa per-invocation

Tài liệu Spring cho `@AspectJ` style còn đề cập một số instantiation models khác singleton:

| Model trong Spring @AspectJ | Ý nghĩa đại thể | Có đảm bảo một instance / mỗi invocation? |
| --- | --- | --- |
| Mặc định singleton | Một aspect instance được dùng lại trong context | **Không** |
| `perthis(...)` | Gắn aspect instance với một proxy object phù hợp theo biểu thức | **Không** — nhiều invocations trên cùng proxy có thể dùng chung |
| `pertarget(...)` | Gắn aspect instance với một target object phù hợp | **Không** — target có thể phục vụ nhiều invocations đồng thời |
| `pertypewithin(...)` | Gắn aspect instance với một type phù hợp | **Không** — không phải per-call |
| `percflow(...)` / `percflowbelow(...)` | Không được Spring AOP proxy hỗ trợ theo docs | Không dùng như cách sửa concurrency |

Các mô hình trên là **chủ đề nâng cao**; không phải giải pháp đầu tiên cho state per-invocation. Đừng phát biểu sai rằng Spring AOP chỉ hỗ trợ singleton mà hoàn toàn không có mô hình per-object — tài liệu chính thức xác nhận `perthis`, `pertarget` và `pertypewithin` có hỗ trợ trong @AspectJ configuration.

Ví dụ dạng fragment:

```java
@Aspect("pertarget(execution(* demo.concurrent.WorkerService.process(..)))")
public class TargetScopedAspect {
    // State vẫn chia sẻ giữa các lời gọi trên cùng target.
}
```

Đây chỉ minh họa cú pháp, **không** đặt vào chương trình runnable ở mục 8. Với `pertarget`, việc tạo aspect instance dựa trên target identity khi pointcut phù hợp, không tự tách dữ liệu per-thread/per-request. Trước khi dùng những mô hình nâng cao, kiểm tra Spring version, cấu hình đăng ký aspect và integration tests.

**Ghi chú quan trọng:** Scope của Spring bean `@Scope("prototype")` và AspectJ-style per-clause instantiation **không phải cùng một mô hình**. Không đổi annotation bean scope rồi suy ra một advice method instance được tạo cho từng invocation.

## 10. Lỗi thường gặp và cách khắc phục

| Triệu chứng | Nguyên nhân thường gặp | Cách sửa |
| --- | --- | --- |
| Log request A hiện ID của request B | Field singleton `currentId` bị ghi đè | Dùng local variable trong `@Around`, hoặc context được thiết kế rõ |
| Start time/duration giữa hai lời gọi bị trộn | Lưu `startedAt` trong aspect instance field | Đặt `long started` trong advice local frame |
| Counter thấp hơn số lần gọi thực | `count++` không atomic | Dùng `AtomicInteger`/`LongAdder`/metrics registry |
| Thêm `volatile` vẫn sai request | Visibility không tạo per-invocation isolation | Không lưu request context trong một shared field |
| `synchronized @Before`/`@After` vẫn lệch dữ liệu | Lock chỉ quanh từng method advice, không nối một invocation | Đổi thiết kế sang local state/around |
| `synchronized @Around` làm service nghẽn | Serialize toàn bộ chain/target | Không khóa nếu chỉ cần đo/log; dùng thread-safe aggregator |
| `ThreadLocal` còn dữ liệu từ request trước | Worker thread pool tái sử dụng, quên `remove()` | Cleanup/restore trong finally; ưu tiên explicit context |
| Nested advice xóa ThreadLocal của outer | Inner call `remove()` sau khi ghi đè old value | Save old, restore trong finally hoặc context API phù hợp |
| `@Async` không thấy ThreadLocal | Task chuyển thread, không tự propagate | Task decoration/context propagation tùy Spring module |
| `@Around` elapsed thấp hơn async processing | Method return future/publisher sớm | Đo completion theo async/reactive API |
| Singleton aspect inject singleton mutable service và bị race | Collaborator không thread-safe | Sửa state ownership/concurrency ở collaborator |
| Dùng `@Order` để bảo đảm không có hai threads xen kẽ | `@Order` chỉ precedence trong một interceptor chain | Chọn concurrency control thích hợp |
| `perthis` vẫn có race | Nhiều threads gọi cùng proxy | Dùng local state hoặc atomic/lock có mục đích |
| `pertarget` vẫn có race | Nhiều threads gọi cùng target instance | Không xem per-target là per-thread/per-request |
| Bean tự `new` không có advice | Không có Spring proxy | Dùng Spring-managed dependency |
| `this.process()` không qua advice | Self-invocation bypass Spring proxy | Tách operation sang Spring bean khác nếu cần advice |
| Aspect không có effect | `@Aspect` chưa được đăng ký bean hoặc chưa bật auto-proxy | `@Component`/`@Bean` và `@EnableAspectJAutoProxy`/Boot AOP |
| JDK proxy không expose implementation-only method | Interface surface giới hạn | Dùng public interface method hoặc cấu hình CGLIB có cân nhắc |
| CGLIB không intercept `final`/`private` method | Không override được | Đổi method/bean boundary phù hợp |
| Nhiều ApplicationContexts có nhiều singleton aspects | Singleton là per-container/bean | Kiểm tra context ownership và cấu hình thực tế |

## 11. Checklist thiết kế singleton Spring Aspect an toàn

1. **Xác định scope thật:** aspect là Spring bean trong context nào, có per-clause instantiation model đặc biệt không.
2. **Kiểm tra target và proxy:** Spring có quản lý target không, pointcut match, caller có đi qua proxy không.
3. **Liệt kê tất cả aspect fields:** config immutable, collaborator references, shared aggregate state, hay dữ liệu per-invocation?
4. **Di chuyển per-invocation state vào local variables** của advice; dùng `@Around` khi cần nối đoạn trước/sau `proceed()`.
5. **Shared aggregates phải thread-safe:** atomic/LongAdder hoặc metrics library; nghĩ tới snapshot consistency và cardinality.
6. **Không lạm dụng `volatile`/`synchronized`:** chúng giải quyết visibility/mutual exclusion, không tạo request identity.
7. **Đừng lưu `JoinPoint` hay `ProceedingJoinPoint` trong singleton fields** để dùng lại về sau; đây là state của invocation hiện tại.
8. **Xem collaborator thread safety:** `final` dependency reference không làm các mutable internals bên trong collaborator an toàn.
9. **Xem ThreadLocal lifecycle:** cleanup, nested calls, async hops, executor reuse và context propagation.
10. **Đảm bảo outcome:** advice gọi `proceed()` theo nhu cầu, giữ return value và truyền exception đúng.
11. **Test concurrency thật:** dùng latch/barrier để ép overlap và kiểm tra race/độ đúng, không đoán từ một lần log may mắn.
12. **Khi có transactions/cache/security/async:** kiểm tra advisor ordering và ranh giới thực thi, không coi một singleton aspect là global lock.

### Bộ test tình huống hữu ích

| Test | Kỳ vọng |
| --- | --- |
| Tra `getBean(Aspect.class)` hai lần cùng context | Cùng reference nếu default singleton |
| Hai threads gọi chung Spring proxy đồng thời | Advice chạy concurrent nếu không tự serialize |
| Request A/B có argument khác nhau | Local request data giữ đúng A/B |
| Atomics đếm active/peak | Không mất increment, số peak phản ánh overlap |
| Shared field `currentId` | Có thể sai khi interleave; không dùng trong production |
| Có method gọi `this.inner()` | Không có interception mới cho self-invocation |
| Object tạo qua `new` | Không được Spring AOP advice |
| Lỗi từ target | `finally` cleanup metrics đúng, exception vẫn truyền ra ngoài |
| Lời gọi trả future | Đừng hiểu đồng bộ elapsed là toàn bộ thời gian async |
| Multiple contexts | Không suy rằng một aspect singleton duy nhất toàn JVM |

## 12. Câu hỏi ôn tập

**1. Spring `@Aspect @Component` singleton có nghĩa advice chỉ chạy một lần không?** Không. Một aspect instance phục vụ nhiều matched invocations; các threads có thể vào advice cùng lúc.

**2. Vì sao lưu `currentRequestId` vào field là nguy hiểm?** Hai calls đồng thời ghi đè shared state, khiến after advice đọc dữ liệu của invocation khác.

**3. `volatile` sửa được race “request A đọc ID của B” không?** Không. Nó giúp visibility, không tạo dữ liệu riêng theo invocation.

**4. Tại sao `@Around` local `started` an toàn hơn field `started`?** Local thuộc stack frame từng invocation; khi `proceed()` trả, cùng frame nhận kết quả và duration tương ứng.

**5. Trường hợp nào nên có mutable aspect fields?** Khi đó là shared state thật sự như metrics aggregates và được bảo vệ bằng concurrency primitives/structures phù hợp.

**6. `@Order(1)` có bảo đảm Thread B đợi Thread A kết thúc không?** Không. Nó điều chỉnh precedence của advisor trong một invocation chain, không là mutex giữa requests.

**7. Vì sao `ThreadLocal` cần `remove()` hoặc restore?** Thread pool tái sử dụng threads và nested calls có thể ghi đè context; cleanup tránh data leak/cross-request contamination.

**8. `perthis` hoặc `pertarget` có nghĩa một aspect instance mỗi lời gọi không?** Không. Chúng liên hệ aspect instance với proxy/target object, mà một object có thể có nhiều calls concurrent.

**9. Một `@Around` đo timer quanh `proceed()` có đếm xong CompletableFuture chưa?** Không nhất thiết; future có thể hoàn thành sau khi method invocation đồng bộ return.

**10. Spring singleton scope có phải đúng một instance aspect trong toàn JVM không?** Không; thông thường per bean definition/per ApplicationContext, khác GoF static singleton.

## Tài liệu Spring Framework chính thức

- [Spring Framework 6.2 — Aspect Instantiation Models: singleton, perthis, pertarget, pertypewithin](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/instantiation-models.html)
- [Spring Framework — Bean Scopes: singleton theo container](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [Spring Framework — Declaring Advice: Around Advice và local state khi đo thời gian](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html)
- [Spring Framework — Bean Initialization / thread-safe publication và mutable runtime fields](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html)
- [Spring Framework — Proxying Mechanisms: JDK/CGLIB và self-invocation](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — @AspectJ support và aspect beans](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-state-concurrency-002">Giữ dữ liệu riêng cho lời gọi trong biến cục bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ dữ liệu riêng cho lời gọi trong biến cục bộ của Spring Aspect

## 1. Bài toán thực hành: ghép đúng dữ liệu trước và sau một lời gọi

Một aspect cần ghi **thời gian, kết quả và lỗi** của `OrderService.submit(id)`. Cùng một singleton aspect có thể xử lý hai invocations đồng thời: A thành công, B ném exception. Mục tiêu không chỉ là tránh sai timestamp, mà còn bảo đảm `outcome` của A không bị ghi bằng trạng thái của B.

Với state gắn một invocation, giữ toàn bộ tiến trình **capture → `proceed()` → observe → cleanup** trong một `@Around`:

```java
@Around("execution(* com.example.orders.OrderService.submit(..))")
public Object measure(ProceedingJoinPoint pjp) throws Throwable {
    final long startedNanos = System.nanoTime();
    String outcome = "THROWN";
    try {
        Object result = pjp.proceed();
        outcome = "RETURNED";
        return result;
    } finally {
        record(outcome, System.nanoTime() - startedNanos);
    }
}
```

Đây là fragment: `record(...)` là helper của ứng dụng và phải có chính sách nếu sink lỗi. Khi A/B cùng gọi advice, mỗi execution frame chứa `startedNanos`/`outcome` riêng; `final` không phải nguồn bảo đảm thread safety, **phạm vi biến local** mới là điểm cốt lõi. `proceed()` có thể ném `Throwable`; `finally` vẫn quan sát thất bại đồng bộ rồi exception tiếp tục lan ra ngoài, trừ khi bản thân recorder ném lỗi.

Bài này thực hành cách giữ state per-invocation trong Spring AOP, bao gồm return/throw, shared metrics, nhiều lời gọi lồng nhau và giới hạn future/reactive. Demo mục 7 sử dụng hai luồng có latch để xác nhận **A→RETURNED** và **B→THROWN** mà không phụ thuộc thứ tự scheduler.

## 2. Chọn lifetime đúng cho từng giá trị

Spring `@Aspect` là bean singleton theo mặc định trong `ApplicationContext`, nhưng một method advice có **execution frame riêng cho mỗi lần được gọi**. Field của aspect tồn tại giữa các lần advice; local của `@Around` chỉ sống trong invocation đó.

| Dữ liệu | Lifetime đúng | Thiết kế |
| --- | --- | --- |
| `startedNanos`, `outcome`, `Throwable`, method name | Một lời advice invocation | Local variables trong `@Around` |
| ID của lời gọi hiện tại | Một invocation | Local `String`/immutable snapshot từ argument hoặc provider |
| Số lượt thực thi/latency aggregate | Nhiều invocations | `LongAdder`, atomic counter hoặc metrics backend |
| Kết quả audit tra cứu về sau | Nhiều invocations | Concurrent sink/map với key duy nhất và retention policy |
| Config hoặc dependency | Vòng đời bean aspect | `final` reference, đánh giá riêng thread safety của dependency |
| Context xuyên threads/subscriptions | Theo contract async/reactive | Propagation/Reactor Context, không dựa vào field “current request” |

`local Object[] args = pjp.getArgs()` chỉ tách **reference của array** khỏi field aspect; các object mutable bên trong vẫn có thể được chia sẻ. Khi cần snapshot đáng tin, copy dữ liệu scalar cần thiết theo chính sách audit. Một `LongAdder` thread-safe cũng không tự làm phép kiểm tra–cập nhật nhiều trường trở thành atomic.

Điều quan trọng hơn scope là **cách kết thúc**: local state còn dùng được trong cùng `finally` của `@Around`; hai advice methods `@Before` và `@After` thì không chia sẻ Java locals. Các phần sau phân tích return/exception và chạy demo.

## 3. Vì sao @Around đặc biệt phù hợp để giữ state một invocation?

Spring reference khuyên dùng around advice khi cần chia sẻ state từ lúc **trước** đến lúc **sau** một method execution một cách thread-safe, ví dụ đồng hồ đo thời gian.

Một `@Around` bao quanh `ProceedingJoinPoint.proceed()`:

```text
Client -> Spring AOP proxy
          |
          v
        @Around
          |
          +-- local started = now
          |
          +-- try {
          |      pjp.proceed()
          |         |
          |         +-- other advisors
          |         +-- target method
          |    }
          |
          +-- catch Throwable: local outcome = failure
          |
          +-- finally: local elapsed = now - started
          |
          v
        return / rethrow tới caller
```

Dù target return bình thường, return `null` hay ném exception, khi control flow đi vào `try` rồi unwind thông thường, `finally` có thể truy cập **cùng local `started`** của invocation đó.

### 3.1. Ví dụ chỉ đo thời gian

```java
@Around("execution(public * com.example.service..*.*(..))")
public Object time(ProceedingJoinPoint pjp) throws Throwable {
    final long start = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        final long elapsed = System.nanoTime() - start;
        System.out.println(pjp.getSignature().getName() + "=" + elapsed);
    }
}
```

Đây là **snippet minh họa**, không độc lập: cần `@Aspect`, các imports và Spring AOP configuration. Timestamp được lưu trong Java local primitive, không nằm ở field Spring bean. Stdout của duration **không cố định** giữa các lần chạy.

### 3.2. Quan sát normal/exception bằng state local

```java
@Around("execution(public * com.example.service..*.*(..))")
public Object observe(ProceedingJoinPoint pjp) throws Throwable {
    final long start = System.nanoTime();
    String outcome = "THROWN";

    try {
        Object result = pjp.proceed();
        outcome = "RETURNED";
        return result;
    } finally {
        long elapsed = System.nanoTime() - start;
        record(pjp.getSignature().getName(), outcome, elapsed);
    }
}
```

Trong snippet này `record(...)` là helper giả định, không phải API Spring. Nếu `proceed()` return (kể cả `null`), `outcome` đổi thành `"RETURNED"`. Nếu `proceed()` ném exception, lệnh gán đó không chạy, nên `outcome` còn là `"THROWN"` và `finally` nhận trạng thái failure.

**Giới hạn:** Nếu method `record` trong `finally` tự ném exception, nó có thể che exception gốc từ `proceed()`. Instrumentation phải tránh làm hỏng business flow; dùng metrics backend/logging được cấu hình an toàn và có chính sách khi backend lỗi.

### 3.3. try/catch/finally có thay đổi proxy outcome không?

Một `@Around` chỉ dùng `try/finally` và `return pjp.proceed()` giữ nguyên normal result hoặc exception thoát ra từ inner chain. Thêm `catch(Throwable ex) { throw ex; }` để quan sát cũng giữ nguyên exception khi **rethrow cùng object**.

Ngược lại:

```java
catch (IllegalStateException ex) {
    return "FALLBACK";
}
```

là **chuyển kết cục**: inner chain đã throw nhưng caller bên ngoài có thể thấy normal return. Nếu bạn lưu outcome `"THROWN"` trong finally trước khi trả fallback, phải đặt tên metric là **inner failure**, không được tự gọi nó là **caller-visible failure**. Với Spring transaction/cache/retry, vị trí advisors trong chain ảnh hưởng nơi kết quả được quan sát.

## 4. Anti-pattern: field giữ outcome của request đang chạy

```java
@Aspect
public class UnsafeTraceAspect {
    private String currentId;
    private long startedNanos;

    @Around("execution(* com.example.service..*.*(..))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        currentId = String.valueOf(pjp.getArgs()[0]);
        startedNanos = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            System.out.println(currentId + ":" +
                    (System.nanoTime() - startedNanos));
        }
    }
}
```

Đây là phản ví dụ, không phải cách triển khai. Sau khi A set `currentId="A"` rồi vào target, B có thể set `currentId="B"`; A thoát `finally` và ghi nhầm B. Thêm `volatile` chỉ thay đổi visibility của field dùng chung; `synchronized` toàn bộ advice có thể tuần tự hóa cả nghiệp vụ, ảnh hưởng throughput và làm các lời gọi lồng nhau khó quản lý.

Sửa bằng cách khai báo `String currentId` và `long startedNanos` **bên trong method advice**, giống pattern ở mục 1. Một giá trị local tham chiếu DTO mutable vẫn có thể bị thay đổi từ nơi khác; nếu báo cáo cần bản chụp chính xác, dùng immutable snapshot thay vì lưu cả argument graph.

## 5. Dùng local cho giá trị per-invocation, dùng thread-safe structure cho số liệu tổng hợp

Một ứng dụng thường cần hai lớp dữ liệu:

1. **Trong invocation**: `start`, `id`, `outcome`, `Throwable error`, `Object result` — nên là local.
2. **Sau invocation**: total requests, latency metrics, audit records — cần lưu ra nơi có nhiều threads ghi cùng lúc.

Không phải vì `private long completed` nằm trong `@Aspect` mà Spring tự quản concurrency. Nếu đếm:

```java
private long completed;

public void increment() {
    completed++; // Read-modify-write không atomic.
}
```

thì update có thể bị mất khi hai threads cùng tăng.

Một ví dụ shared counter phù hợp hơn:

```java
private final LongAdder completed = new LongAdder();

public long completedCount() {
    return completed.sum();
}
```

Với import `java.util.concurrent.atomic.LongAdder`, các calls `completed.increment()` có thể được nhiều threads thực hiện an toàn, phù hợp số liệu thống kê có concurrency cao. `sum()` là snapshot **không phải giao dịch atomic với tất cả increments đang diễn ra**; khi có các requests in-flight, không nên dùng nó làm invariant tuyệt đối nếu không đồng bộ thêm.

Đối với dữ liệu mỗi invocation phải tra cứu, có thể dùng:

```java
private final ConcurrentMap<String, Observation> observations =
    new ConcurrentHashMap<>();
```

mà `Observation` là immutable record. Nhưng bạn cần key **duy nhất cho mỗi invocation** và chính sách tránh memory leak: xóa/giới hạn dữ liệu hoặc đẩy sang tracing/metrics backend. Ví dụ chạy phía dưới dùng key `A`, `B` **duy nhất trong demo**, không phải contract phù hợp nếu production có thể nhận cùng business id đồng thời nhiều lần.

## 6. Vì sao @Before và @After thường không chia sẻ được biến local?

Hai advice methods là **hai Java method invocations khác nhau**:

```java
@Before("execution(* com.example.service..*.*(..))")
public void before() {
    long started = System.nanoTime();
}

@After("execution(* com.example.service..*.*(..))")
public void after() {
    // Không thể truy cập biến local "started" của before().
}
```

`started` chỉ tồn tại trong execution frame của `before()`. `after()` không thể tự lấy lại nó chỉ bằng trùng tên pointcut, và Spring không có cơ chế mặc định “ghép đôi local variables” giữa hai advice methods.

Nếu mục tiêu là đo thời gian từ before đến after của **cùng một method invocation**, dùng một `@Around` với `try/finally`. Đây là thiết kế đơn giản hơn tạo `ThreadLocal` rồi tự ghép cặp `@Before`/`@After`.

### Khi nào vẫn dùng @Before/@After?

- `@Before` thích hợp nếu chỉ cần check điều kiện/log trước method, **không** cần giữ biến qua phần thực thi.
- `@AfterReturning` thích hợp nếu chỉ cần biết normal result.
- `@AfterThrowing` thích hợp nếu chỉ cần ghi nhận exception.
- `@After` thích hợp cho cleanup không phụ thuộc state tạo từ lời gọi `@Before`.
- `@Around` thích hợp khi cần **một local scope chứa cả phần trước và phần sau**.

Spring reference khuyên dùng **advice ít quyền nhất** đáp ứng nhu cầu; không cần dùng `@Around` cho mọi việc. Nhưng đối với start/stop timer cần chia sẻ `start` per invocation, `@Around` mang lại scope tốt nhất.

## 7. Project chạy được: hai lời gọi đồng thời, không lẫn trạng thái

Để chứng minh tính tách biệt, ví dụ Java 17 sau tạo một Spring target bean, một singleton aspect, rồi gọi method target **đồng thời trên hai threads**. `CountDownLatch` giữ cả hai lời gọi ở trong target trước khi chúng được phép kết thúc.

Aspect ghi nhận:
- `id` lấy từ arguments, lưu vào local;
- `startedNanos` lưu vào local;
- `outcome` là local được cập nhật sau `proceed()`;
- `elapsedNanos` là local trong `finally`;
- kết quả cuối được đưa vào `ConcurrentHashMap` an toàn cho concurrent writes.

**Stdout được in sau khi cả hai `Future` hoàn tất**, nên không phụ thuộc thread A/B nào chạy trước. Demo cố ý cho `A` return bình thường, `B` throw unchecked exception; ta xác minh cả hai được ghi đúng outcome.

### 7.1. Môi trường và cấu trúc

- Java **17+**;
- Spring Framework **6.1.14**;
- AspectJ Weaver **1.9.22.1**;
- Gradle hỗ trợ Java 17;
- Không cần Spring Boot, web server hoặc database.

```text
spring-aop-local-state/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/local/
    ├── Gate.java
    ├── WorkService.java
    ├── TraceAspect.java
    ├── AppConfig.java
    └── DemoApplication.java
```

### 7.2. Gradle project

`settings.gradle`:

```groovy
rootProject.name = 'spring-aop-local-state'
```

`build.gradle`:

```groovy
plugins {
    id 'java'
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
    implementation 'org.springframework:spring-aop:6.1.14'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'
}

application {
    mainClass = 'demo.local.DemoApplication'
}
```

`spring-context` cung cấp container/configuration; `spring-aop` cung cấp proxying infrastructure; `aspectjweaver` hỗ trợ phân tích Spring `@AspectJ` pointcut expressions.

### 7.3. Đồng bộ hai invocations bằng CountDownLatch

`src/main/java/demo/local/Gate.java`:

```java
package demo.local;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class Gate {
    private final CountDownLatch entered = new CountDownLatch(2);
    private final CountDownLatch release = new CountDownLatch(1);

    public void waitInsideTarget() {
        entered.countDown();

        try {
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("release timeout");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", ex);
        }
    }

    public void awaitBothEntered() throws InterruptedException {
        if (!entered.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("workers did not enter");
        }
    }

    public void open() {
        release.countDown();
    }
}
```

`Gate` chỉ phục vụ **test concurrency**, không phải một component AOP. Mỗi worker gọi `waitInsideTarget()` sẽ báo đã vào method rồi chờ. `main` đợi cả hai tới chỗ chờ trước khi thả cho chúng chạy tiếp. Timeout bảo vệ demo khỏi chờ vô hạn nếu môi trường lỗi.

### 7.4. Spring target service

`src/main/java/demo/local/WorkService.java`:

```java
package demo.local;

public class WorkService {
    private final Gate gate;

    public WorkService(Gate gate) {
        this.gate = gate;
    }

    public String handle(String id) {
        gate.waitInsideTarget();

        if ("B".equals(id)) {
            throw new IllegalStateException("failed-B");
        }
        return "OK-" + id;
    }
}
```

Method `handle` là public non-final, target class cũng non-final; Spring có thể tạo CGLIB class-based proxy. Với `id="A"` sẽ return `"OK-A"`, với `id="B"` ném `IllegalStateException("failed-B")`. Cả hai execution cùng đi qua một shared `Gate` chỉ để xác minh chúng thực sự overlap.

### 7.5. Aspect an toàn: local state + ConcurrentHashMap

`src/main/java/demo/local/TraceAspect.java`:

```java
package demo.local;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TraceAspect {
    private final ConcurrentMap<String, Observation> records =
        new ConcurrentHashMap<>();

    public record Observation(String outcome, long elapsedNanos) {}

    @Around(
        value = "execution(public String demo.local.WorkService.handle(String))"
              + " && args(id)",
        argNames = "id"
    )
    public Object observe(ProceedingJoinPoint pjp, String id)
            throws Throwable {

        // Per-invocation locals:
        final String invocationId = id;
        final long startedNanos = System.nanoTime();
        String outcome = "THROWN";

        try {
            Object result = pjp.proceed();
            outcome = "RETURNED";
            return result;
        } finally {
            final long elapsedNanos =
                System.nanoTime() - startedNanos;

            // Chỉ bước tổng hợp cross-invocation mới ghi shared state.
            records.put(invocationId,
                new Observation(outcome, elapsedNanos));
        }
    }

    public String outcomeFor(String id) {
        Observation found = records.get(id);
        return found == null ? "MISSING" : found.outcome();
    }

    public int recordedCount() {
        return records.size();
    }
}
```

**Phân tích từng biến:**
- `pjp` là current `ProceedingJoinPoint`. `ProceedingJoinPoint` phải đứng đầu chữ ký `@Around`.
- `id` được Spring bind từ `args(id)`; `argNames="id"` cố ý bỏ tên `pjp` vì đó là tham số `ProceedingJoinPoint` đầu tiên.
- `invocationId` sao chép **reference immutable `String`** đến local riêng. Nếu actual argument là một object mutable, muốn snapshot dữ liệu thì cần immutable representation/copy.
- `startedNanos`, `outcome`, `elapsedNanos` là locals thuộc execution frame của chính lần advice chạy.
- Nếu `proceed()` return, `outcome` đổi sang `"RETURNED"`; nếu throw, nó giữ `"THROWN"`.
- `finally` đặt kết quả cuối cùng vào `ConcurrentHashMap`. Bản ghi `Observation` là Java record immutable.
- Demo dùng `id` duy nhất `A`/`B` làm key. Trong production **phải dùng invocation id thực sự unique** hoặc aggregation design khác; hai calls có cùng business id không nên vô tình ghi đè nhau.
- `records` là **field shared** nhưng an toàn khi nhiều threads `put` đồng thời nhờ `ConcurrentHashMap`; nó dành cho kết quả đã hoàn thành, không chứa những timestamps đang xử lý.
- Advice **không đổi exception/result**: trả đúng `result`, và khi `proceed()` throw thì `finally` xong, exception vẫn tiếp tục đi ra.
- `elapsedNanos` được lưu nhưng **không in giá trị đo** vì duration thực tế thay đổi theo môi trường.

**Điểm kiểm chứng chính:** Khi A và B overlap trong target, cả hai đều chạy **cùng `TraceAspect` instance** nhưng locals không đè nhau. Sau khi hai futures hoàn tất, map chứa `A→RETURNED` và `B→THROWN`.

### 7.6. Cấu hình Spring AOP

`src/main/java/demo/local/AppConfig.java`:

```java
package demo.local;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public Gate gate() {
        return new Gate();
    }

    @Bean
    public WorkService workService(Gate gate) {
        return new WorkService(gate);
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }
}
```

`@Aspect` một mình **không đăng ký bean**. `@Bean traceAspect()` tạo aspect trong Spring container; `@EnableAspectJAutoProxy(proxyTargetClass = true)` bật Spring AOP và ép class-based proxy cho target `WorkService` không khai báo service interface.

`Gate` là shared Spring bean nhưng dùng các primitives `CountDownLatch` thread-safe. `TraceAspect` là shared bean chứa một map concurrent, còn mỗi lời gọi `observe(...)` có local state riêng.

### 7.7. Main thực thi hai threads và in kết quả ổn định

`src/main/java/demo/local/DemoApplication.java`:

```java
package demo.local;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApplication {

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            WorkService service = context.getBean(WorkService.class);
            TraceAspect aspect = context.getBean(TraceAspect.class);
            Gate gate = context.getBean(Gate.class);

            ExecutorService pool = Executors.newFixedThreadPool(2);

            try {
                Future<String> a =
                    pool.submit(() -> service.handle("A"));

                Future<String> b = pool.submit(() -> {
                    try {
                        return service.handle("B");
                    } catch (IllegalStateException ex) {
                        return ex.getClass().getSimpleName()
                            + ":" + ex.getMessage();
                    }
                });

                // Bảo đảm cả hai lời gọi đã vào target trước khi mở gate.
                gate.awaitBothEntered();
                gate.open();

                String aValue = a.get();
                String bValue = b.get();

                System.out.println("proxy="
                    + AopUtils.isAopProxy(service));
                System.out.println("overlapped=true");
                System.out.println("A=" + aValue);
                System.out.println("B=" + bValue);
                System.out.println("traceA="
                    + aspect.outcomeFor("A"));
                System.out.println("traceB="
                    + aspect.outcomeFor("B"));
                System.out.println("recorded="
                    + aspect.recordedCount());

            } finally {
                gate.open();
                pool.shutdownNow();
            }
        }
    }
}
```

**Tại sao stdout không phụ thuộc race?**
- `CountDownLatch entered` đợi **cả hai invocations** đã tới code target.
- Main mở `release` sau khi chúng đều đã đi vào, đảm bảo thời gian overlap.
- Không in trực tiếp từ target, advice hoặc worker threads, chỉ in **sau khi `a.get()` và `b.get()` xong**.
- Vì `Future.get()` hoàn thành sau khi advice đã chạy xong `finally`, các entries đã nằm trong concurrent map khi main đọc.
- `A`/`B` là keys riêng nên order `put` không ảnh hưởng kết quả được đọc.
- `pool.shutdownNow()` dùng trong `finally` để giải phóng resources cả khi có lỗi; `gate.open()` đảm bảo không để worker chờ gate vô hạn.

### 7.8. Chạy bằng Gradle

Từ thư mục project có `build.gradle`:

```bash
gradle run
```

Nếu đã cài Gradle Wrapper, dùng `./gradlew run` (Linux/macOS) hoặc `gradlew.bat run` (Windows).

**Stdout do Java code in ra** khi các tasks hoàn thành trong khoảng timeout cấu hình:

```text
proxy=true
overlapped=true
A=OK-A
B=IllegalStateException:failed-B
traceA=RETURNED
traceB=THROWN
recorded=2
```

Các dòng Gradle hoặc logging hệ thống không nằm trong khối stdout này. Nếu máy không thể khởi tạo hai workers hoặc bị treo quá 10 giây, demo báo timeout thay vì khẳng định kết quả trong điều kiện môi trường không được đáp ứng.

### 7.9. Giải thích chứng minh

| Yếu tố | Invocation A | Invocation B |
| --- | --- | --- |
| Target call | `service.handle("A")` | `service.handle("B")` |
| Kết cục của `proceed()` | Return `"OK-A"` | Throw `IllegalStateException("failed-B")` |
| Local `invocationId` | `"A"` | `"B"` |
| Local `outcome` sau proceed | `"RETURNED"` | `"THROWN"` |
| Chạy `finally` | Có | Có |
| Dữ liệu aggregate | `records["A"] = Observation("RETURNED", ...)` | `records["B"] = Observation("THROWN", ...)` |

Cả A và B cùng dùng **một instance `TraceAspect`**, nhưng không có `currentId` hoặc `startedNanos` field để chúng tranh nhau ghi. Dùng locals nghĩa là không có trường shared để rò rỉ identifier hay timestamp của B sang A.

### 7.10. Nếu bỏ @Around, gọi trực tiếp target?

Lời gọi:

```java
WorkService service = new WorkService(new Gate());
```

tạo object ngoài Spring container; nó không phải proxy nên `TraceAspect.observe` **không tự chạy**. Ví dụ trên chỉ là minh họa về proxy boundary, **không tự chạy được như project ở phần 7** vì cần điều khiển `Gate` và threads đúng cách.

Lời gọi nội bộ kiểu `this.handle("A")` từ một method của chính `WorkService` cũng bypass Spring AOP proxy; advice method và locals của advice **không được khởi tạo** cho lời gọi nội bộ ấy. Không thể giải quyết self-invocation chỉ bằng đổi field thành local.

## 8. `ThreadLocal` không phải lựa chọn mặc định cho timer

`@Before` và `@After` là hai method calls khác nhau nên không thể trực tiếp dùng chung một local `started`; đó là lý do `@Around` thuận tiện hơn. Dùng `ThreadLocal<Long>` để truyền timestamp giữa chúng đưa thêm các nghĩa vụ: xóa/restore trong `finally`, bảo toàn outer state nếu nested advice chạy cùng thread, kiểm tra thread pool reuse và xử lý lúc advisor không được đi vào vì pointcut/ordering.

Khi bắt buộc dùng context theo thread, nên giữ previous value để trả lại outer scope:

```java
String previous = traceId.get();
traceId.set(currentId);
try {
    return pjp.proceed();
} finally {
    if (previous == null) traceId.remove();
    else traceId.set(previous);
}
```

Đây là fragment cần `traceId`, `currentId`, `pjp`. State của một advice invocation đơn lẻ vẫn nên là local; chuyên bài ThreadLocal sẽ triển khai cleanup, MDC, nested calls và thread hops đầy đủ.

## 9. Local state khi chuyển sang `@Async`, `CompletableFuture` hoặc Reactor

Local variables tiếp tục **cách ly các lần advice được gọi**, nhưng khoảng lifetime mà chúng mô tả phụ thuộc vào điểm intercept. Nếu `@Async` có async advisor ngoài `@Around`, phần code target/advice trong worker có thể diễn ra ở thread khác caller. Nếu `@Around` đo phía caller, nó có thể chỉ thấy thời gian submit và future được trả về. Phải quan sát advisor ordering và proxy path thực tế trước khi đặt tên metric.

```java
@Around("execution(* com.example.service..*.loadAsync(..))")
public Object measureCall(ProceedingJoinPoint pjp) throws Throwable {
    long start = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        System.out.println("method-boundary-ns=" + (System.nanoTime() - start));
    }
}
```

Snippet đo đúng **method execution boundary**, không hứa đo được completion của một `CompletableFuture` còn pending. Với Reactor `Mono`/`Flux`, trả publisher có thể xảy ra trước subscription; muốn đo mỗi subscription, tạo timestamp bên trong `defer` và dùng `doFinally` phù hợp. Cả hai trường hợp đều không có lý do chuyển timestamp của một invocation thành mutable singleton field.

Các bài về `@Async` và reactive pipeline phía sau dạy riêng vấn đề thread/context propagation, completion, lỗi và cancellation.

## 10. Invocation lồng nhau và retry: local state có còn đúng không?

### 10.1. Nested invocation qua proxy

Nếu một service method A gọi method B **qua một Spring-managed proxy** và cả hai match cùng `@Around`, Spring gọi advice hai lần:

```text
A advice frame:
  local startedA
  proceed A
    -> B advice frame:
         local startedB
         proceed B
         finally elapsedB
  finally elapsedA
```

`startedA` và `startedB` là locals khác nhau ngay cả khi cả hai lời gọi xảy ra **trên cùng thread**. Đây là lợi thế so với một `ThreadLocal<Long>` đơn lẻ bị overwritten ở lần B.

Nếu A gọi `this.B()` bên trong cùng target và bypass proxy, sẽ **không có B advice frame**, nên điều này không phải bằng chứng local bị lẫn — đơn giản là Spring AOP không chặn B.

### 10.2. Retry với nhiều lần proceed()

Spring `@Around` được phép gọi `pjp.proceed()` nhiều lần. Nếu một invocation tạo local `started` trước loop rồi thực hiện ba attempts, timestamp đo **toàn bộ retry scope**, không phải thời gian từng attempt.

Muốn mỗi attempt có thời gian riêng:

```java
for (int attempt = 1; attempt <= 3; attempt++) {
    long attemptStart = System.nanoTime();
    try {
        Object result = pjp.proceed();
        recordAttempt(attempt, System.nanoTime() - attemptStart);
        return result;
    } catch (RetryableException ex) {
        recordAttempt(attempt, System.nanoTime() - attemptStart);
        if (attempt == 3) {
            throw ex;
        }
    }
}
throw new AssertionError("unreachable");
```

**Snippet thuật toán** cần định nghĩa `RetryableException`/`recordAttempt(...)` và thêm nó trong `@Around` với `throws Throwable`. Đây không phải mã production: retry cần backoff, idempotency, timeout và transaction semantics. Ý chính: local `attemptStart` là **per-attempt**, còn local `started` bên ngoài vòng lặp là **per-around-invocation**. Bạn phải chọn đúng scope muốn đo.

Một lời gọi của caller có thể gồm nhiều attempts và multiple target executions. Đừng coi `one @Around invocation = exactly one target execution` nếu chính advice của bạn hoặc retry advisor gọi `proceed()` nhiều lần.

## 11. Những ngoại lệ mà local state không tự giải quyết

### 11.1. Mutable target arguments

```java
Object[] args = pjp.getArgs();
SomeMutableDto dto = (SomeMutableDto) args[0];
```

Các biến `args` và `dto` là local **references**, nhưng DTO object có thể là shared mutable argument được nhiều threads truyền vào. Nếu advice mutate `dto` không đồng bộ, vẫn có race.

Muốn ghi snapshot cho audit, chỉ lưu những trường cần thiết thành immutable values, hoặc tạo defensive copy theo semantics của DTO.

### 11.2. Shared dependency của aspect

```java
private final SimpleDateFormat formatter = new SimpleDateFormat("...");
```

Nếu nhiều advice invocations cùng gọi shared formatter không thread-safe, việc đổi timestamp sang local **không sửa vấn đề ở dependency**. Dùng thread-safe API như `java.time.format.DateTimeFormatter`, immutable configuration hoặc synchronization hợp lý trong dependency.

### 11.3. Chính advice ghi log hoặc metrics có thể ném exception

Nếu `finally` dùng một mutable logger/sink sai cách hoặc ném exception, kết cục nghiệp vụ có thể bị thay đổi. Local variable giúp chống state interleaving; nó không bảo đảm logging backend luôn an toàn. Cần có policy instrumentation-failure rõ: metric có thể bị drop? Audit bắt buộc thì rollback? Ai quan sát sự cố sink?

### 11.4. Dữ liệu bí mật không nên vào logs

`pjp.getArgs()` có thể chứa password/token/PII. Local snapshot không làm dữ liệu an toàn hơn về quyền riêng tư. Chỉ log metadata đã allowlist, che thông tin bí mật, và không giữ object graph lớn trong map singleton khiến memory retention.

### 11.5. Context boundary và nhiều ApplicationContexts

Aspect singleton là theo **container scope** thực tế; ứng dụng có nhiều `ApplicationContext` có thể tạo nhiều aspect instances. Không suy luận từ “singleton Spring bean” rằng mọi application trong JVM cùng dùng đúng một object. Trái lại, một singleton aspect trong một context vẫn phục vụ nhiều threads, nên per-invocation locals luôn hữu ích.

## 12. Ma trận lỗi và hướng xử lý

| Triệu chứng | Nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| Log request A in ID của B | Field `currentRequestId` bị threads ghi đè | Chuyển ID thành local trong `@Around` |
| Duration sai khi gọi đồng thời | Shared field `startedNanos` bị overwrite | Đặt `startedNanos` trong scope invocation |
| Thêm `volatile` vẫn sai | Visibility không tạo per-invocation copies | Dùng local; chọn shared-state design đúng |
| `synchronized` làm throughput giảm mạnh | Serializes whole advice/target invocation | Bỏ shared-state dependency thay vì lock cả method |
| Số lượt hoàn thành nhỏ hơn thực tế | `counter++` trên singleton aspect bị lost update | `AtomicLong`/`LongAdder` hoặc metrics backend |
| ThreadLocal của request trước còn ở request mới | Quên `remove()` trong pooled thread | Dùng `try/finally` và restore/remove đúng |
| Nested method B làm duration A sai | `ThreadLocal<Long>` bị overwriting bởi B | Dùng nested locals của `@Around` hoặc stack/context có thiết kế |
| Async future còn chạy nhưng around đã in completed | Đo method return thay vì async task completion | Instrument completion stage / tracing framework |
| Reactor Mono mới được tạo đã thấy “duration” | Method trả publisher trước khi subscription xử lý | Dùng Reactor operators/context theo subscription |
| Map aggregate ghi sai khi same business ID | Key không unique per invocation | Dùng invocation ID duy nhất, tránh silent overwrite |
| Map quan sát tăng mãi | Không có eviction/retention policy | Xóa/TTL/metrics backend, không giữ mọi invocation vô hạn |
| Aspect không chạy khi gọi `this.method()` | Self-invocation bypass proxy | Tách Spring collaborator, gọi qua proxy |
| Local chứa DTO nhưng data vẫn race | Reference local trỏ tới shared mutable DTO | Snapshot immutable/copy hoặc synchronize dependency |
| Advice không chạy vì không có proxy | Aspect chưa là bean, chưa bật AOP, pointcut mismatch | Kiểm tra registration, `AopUtils.isAopProxy` và method signature |
| Logging exception che lỗi gốc | `finally` tự throw | Tách instrumentation failure policy và preserve cause |
| Concurrency test lúc pass lúc fail | Test phụ thuộc thread scheduling | Dùng `CountDownLatch`/phối hợp deterministic và xác minh sau `Future.get()` |

## 13. Checklist review một Spring Aspect có state

- [ ] Mỗi giá trị gắn với **một invocation** (`started`, `id`, `error`, `result`) được lưu dưới dạng local.
- [ ] Không có singleton mutable fields chỉ phục vụ “current request” hoặc “current method invocation”.
- [ ] `@Around` return đúng `pjp.proceed()` và rethrow exception khi không có fallback policy.
- [ ] `finally` ghi nhận cleanup/timing cho cả normal và throwing completion, không `return`/`throw` ngoài chủ đích.
- [ ] Mutable shared counters dùng primitive/thread-safe implementation phù hợp.
- [ ] Shared maps dùng concurrent collection, key unique và có retention policy.
- [ ] Không hiểu `final` reference là deep immutability của object mà nó trỏ tới.
- [ ] Không dùng `ThreadLocal` cho việc có thể giải quyết đơn giản bằng `@Around` local variables.
- [ ] Nếu dùng ThreadLocal, restore/remove trong finally và kiểm thử nested invocation/thread reuse.
- [ ] Đã xác định async/reactive boundary: cần đo method call hay entire task/subscription?
- [ ] Nested calls, retries và nhiều `proceed()` có scope đo tường minh.
- [ ] Advice không log dữ liệu nhạy cảm hoặc giữ object graph lớn trong singleton state.
- [ ] Spring `@Aspect` thực sự là Spring bean; target qua Spring-managed proxy và pointcut khớp.
- [ ] Có test đồng thời thật, không chỉ test tuần tự; không phụ thuộc thứ tự threads trong stdout.
- [ ] Có test normal return và thrown exception để xác nhận per-invocation state không bị lẫn.

## 14. Kết luận

Đối với **Spring Aspect**, cách an toàn và đơn giản nhất để giữ dữ liệu riêng cho một lời gọi là **biến cục bộ trong một `@Around` advice**:

```java
@Around("execution(* com.example.service..*.*(..))")
public Object measure(ProceedingJoinPoint pjp) throws Throwable {
    long started = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        long elapsed = System.nanoTime() - started;
        // elapsed thuộc đúng một advice invocation.
    }
}
```

Từ đó phân biệt rõ:
- **Local state per invocation:** timestamp, argument snapshot, outcome, failure, temporary buffer.
- **Shared state across invocations:** aggregate metrics, counters, audit storage — cần thread-safe mechanism và lifecycle rõ ràng.
- **Thread-bound/reactive context:** chỉ dùng khi nhu cầu vượt phạm vi một advice invocation, có context propagation và cleanup phù hợp.

Spring AOP chỉ áp dụng advice khi lời gọi đi qua **Spring-managed proxy**. Biến local bảo vệ dữ liệu của **advice invocation thật sự tồn tại**; nó không khiến self-invocation tự được advise, không tự tách shared mutable DTO, và không biến một synchronous `@Around` thành bộ đo full asynchronous task duration.

### Tài liệu Spring chính thức

- [Spring Framework — Declaring Advice, Around Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): Spring khuyến nghị around advice khi cần chia sẻ state trước–sau invocation theo cách thread-safe, `ProceedingJoinPoint.proceed()` và return/throw.
- [Spring Framework — Aspect Instantiation Models](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/instantiation-models.html): singleton aspect mặc định, `perthis`/`pertarget`/`pertypewithin` và giới hạn `percflow`.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB, call path, self-invocation.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): đăng ký và kích hoạt aspects trong Spring context.
- [Spring Framework — AOP Concepts](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html): phạm vi method-execution join points và semantics các loại advice.
- [Spring Framework — Asynchronous Execution](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): execution context của `@Async` và task scheduling.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-state-concurrency-003">Nguy cơ lưu JoinPoint, người dùng hoặc thời gian bắt đầu vào field</a>

<details>
<summary>Xem chi tiết</summary>

# Nguy cơ lưu JoinPoint, người dùng hoặc thời gian bắt đầu vào field của Spring Aspect

## 1. Lỗi dữ liệu nghiêm trọng hơn sai số đo

Khi audit aspect giữ một `ProceedingJoinPoint`, current actor hoặc start timestamp trong mutable instance fields, request A có thể **ghi nhận tên người dùng hoặc đối số của request B** dù business result của A vẫn đúng. Trong Spring AOP, `@Aspect` là Spring bean thường có một singleton instance trong context; những fields đó không đại diện riêng cho invocation nào.

```java
@Aspect
public class UnsafeAuditAspect {
    private ProceedingJoinPoint currentJoinPoint;
    private String currentActor;

    @Around("execution(* com.example..service.*.*(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        currentJoinPoint = pjp;
        currentActor = currentActorId(); // helper giả định
        try {
            return pjp.proceed();
        } finally {
            auditSink.write(currentActor, currentJoinPoint); // helper giả định
        }
    }
}
```

Đây là **anti-pattern minh họa**, không phải ví dụ chạy độc lập. Sai lầm còn có thể gây giữ dữ liệu nhạy cảm lâu trong bộ nhớ, nhất là khi lưu cả `JoinPoint`/arguments/target. Bài này tập trung vào **giới hạn vòng đời `JoinPoint`, snapshot identity có kiểm soát và rủi ro audit xuyên yêu cầu**; bài biến cục bộ ngay trước đó giải thích tổng quát về stack frame và timer.

## 2. Cái gì được phép giữ sau invocation?

| Giá trị | Cách sử dụng thích hợp | Rủi ro khi giữ trên singleton aspect |
| --- | --- | --- |
| `ProceedingJoinPoint` | Đọc signature/args và gọi `proceed()` trong advice hiện tại | Lẫn invocation, giữ reference đến target/proxy/arguments |
| Actor từ `SecurityContextHolder` | Chụp **ID đã cho phép ghi audit** vào local | Giữ security principal hoặc token qua request |
| Thời điểm bắt đầu | `long started = System.nanoTime()` local để đo elapsed | Time của request B ghi đè A |
| `Throwable`/result | Xử lý trong `catch/finally` của invocation | Rò rỉ lỗi/kết quả request khác |
| `AuditSink` hoặc `MeterRegistry` | Inject dependency có contract concurrency và retention rõ | Chính sink vẫn có thể không thread-safe |

Không dùng `JoinPoint` như một background command có thể `proceed()` tùy ý sau khi advice kết thúc. Nếu cần truyền thông tin cho queue/audit storage, trích xuất scalar snapshot được phép lưu rồi chuyển cho sink thread-safe; `pjp.getArgs().clone()` chỉ tạo shallow copy, không đóng băng object graph.

## 3. Timeline chứng minh nhầm danh tính

```text
T1 (Alice): currentActor = "alice"; currentJoinPoint = jpAlice
T1 (Alice): vào target rồi chờ I/O
T2 (Bob):   currentActor = "bob";   currentJoinPoint = jpBob
T1 (Alice): target trả về; finally đọc actor "bob" và jpBob
```

Đây là một lịch xen kẽ hợp lệ, đủ để chứng minh shared fields **không diễn đạt được identity của một invocation**. `volatile` hoặc `AtomicReference` có thể cải thiện visibility/atomic reference operations nhưng vẫn chỉ giữ dữ liệu của invocation cập nhật gần nhất. Khóa riêng từng phép gán không bảo vệ vòng đời của target call. Khóa toàn bộ `@Around` làm tuần tự hóa nghiệp vụ và không giải quyết callback async, nhiều context hay advice bypass proxy.

Demo mục 7 tạo timeline Alice/Bob bằng `CountDownLatch`, rồi so sánh `unsafe(alice)=bob` với local snapshot `safe(alice)=alice`. Không cần log credentials hay dữ liệu người dùng thật để kiểm thử hiện tượng này.

## 4. Giữ `ProceedingJoinPoint` trong field còn có rủi ro vòng đời và bảo mật

`ProceedingJoinPoint` cho phép đọc signature, arguments, proxy, target và chuyển tiếp invocation bằng `proceed()`. Nó gắn với **lời gọi đang được advice**.

```java
@Aspect
public class BadJoinPointCache {
    private ProceedingJoinPoint saved;

    @Around("execution(* com.example..service.*.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        saved = pjp;         // Sai: lưu invocation vào shared field.
        return pjp.proceed();
    }
}
```

Đây là fragment phản ví dụ. Ba vấn đề:

1. **Sai invocation:** request khác thay `saved`; callback/log đọc nhầm tên method, target hoặc arguments.
2. **Giữ object graph quá lâu:** join point có thể tham chiếu đến arguments, proxy, target và invocation chain; giữ lâu có thể giữ PII, credentials, mutable DTOs và bộ nhớ.
3. **Lạm dụng `proceed()`:** đừng lưu `pjp` để một thread hoặc event callback khác gọi `saved.proceed()` sau khi advice đã return. `pjp` không phải general-purpose background command; nó mang invocation context/interceptor chain của thời điểm gọi.

Đúng hơn, trích xuất metadata tối thiểu trong local scope:

```java
final String methodName = pjp.getSignature().getName();
final String actorId = actorProvider.currentActorId();
final long startedNanos = System.nanoTime();
```

Đây là **fragment** cần `actorProvider`. Sau đó chỉ gửi `methodName`, `actorId` được cho phép và elapsed duration sang audit/metrics sink. Không giữ raw `pjp` hoặc toàn bộ `Object[] args` lâu hơn cần thiết.

### Copy mảng đối số không phải copy toàn bộ dữ liệu

```java
Object[] arguments = pjp.getArgs().clone();
```

Thao tác này chỉ **shallow-copy** array; nếu phần tử là object mutable, nội dung object có thể tiếp tục thay đổi. Không nên log nguyên argument graph hoặc lưu mảng không giới hạn. Nếu cần immutable snapshot cho audit, định nghĩa DTO ghi nhận **các trường scalar được cho phép** với chính sách redaction rõ ràng.

## 5. Thiết kế an toàn: biến local trong `@Around`

```java
@Around("execution(public * com.example..service.*.*(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    final String method = pjp.getSignature().getName();
    final String actor = actorProvider.currentActorId();
    final long startedNanos = System.nanoTime();
    String outcome = "SUCCESS";

    try {
        return pjp.proceed();
    } catch (Throwable error) {
        outcome = "FAILURE";
        throw error;
    } finally {
        long elapsed = System.nanoTime() - startedNanos;
        auditRecorder.record(actor, method, outcome, elapsed);
    }
}
```

Đây là **production-style fragment**, chưa phải chương trình độc lập vì cần `actorProvider` và `auditRecorder` được inject. Điểm đúng:

- `actor` và `startedNanos` thuộc stack frame của **invocation hiện tại**.
- `pjp` được sử dụng trong phạm vi advice đang thực thi, không bị copy sang singleton field.
- `auditRecorder` có thể là shared dependency, với điều kiện implementation chịu được concurrency và quản lý data retention.
- `finally` quan sát normal return và synchronous exception.
- Throw lại lỗi gốc giúp giữ method exception semantics; đừng nuốt Throwable vô tình.

**Lưu ý:** Nếu `auditRecorder.record(...)` tự ném exception trong `finally`, nó có thể che exception gốc. Cần policy telemetry failure rõ ràng, ví dụ xử lý logging sink failures có kiểm soát để không làm sai business operation, nếu yêu cầu hệ thống cho phép.

### `System.nanoTime()` đúng cho elapsed duration

Dùng `System.nanoTime()` để đo khoảng thời gian trong cùng JVM; không dùng nó như epoch timestamp. `System.currentTimeMillis()` hoặc `Instant.now()` phù hợp ghi thời điểm lịch sử, nhưng wall clock có thể được hiệu chỉnh, nên không lý tưởng để đo latency ngắn.

Tránh `private long startNanos` trên singleton aspect. Nếu A và B overlap, hiệu `now - startNanos` có thể thuộc về B và không phản ánh duration của A.

**Nếu target trả Future/Publisher:** thời gian từ trước tới sau `pjp.proceed()` đo **lúc Java method trả handle/publisher**, không tự đo Future completion hoặc reactive terminal signal. Muốn metric completion phải gắn callback/Publisher operator với **snapshot data cục bộ**, không giữ `ProceedingJoinPoint` để dùng bất đồng bộ.

## 6. Lấy người dùng hiện tại trong Spring: đọc xong, snapshot local

Với Spring Security servlet stack, một ứng dụng có thể đọc:

```java
Authentication authentication =
        SecurityContextHolder.getContext().getAuthentication();

String actorId =
        authentication == null ? "anonymous" : authentication.getName();
```

Đây là fragment Spring Security; cần imports `Authentication` và `SecurityContextHolder`. Default strategy thường dùng thread-bound security context, nhưng ứng dụng có thể cấu hình khác. Không suy rằng principal hiện tại luôn có thể đọc từ thread bất kỳ.

Không làm:

```java
private Authentication currentAuth;

@Before("serviceOperation()")
public void capture() {
    currentAuth = SecurityContextHolder.getContext().getAuthentication();
}

@AfterReturning("serviceOperation()")
public void log() {
    // currentAuth có thể thuộc về lời gọi khác.
}
```

Đây là **anti-pattern**. `@Before` và `@AfterReturning` là hai advice invocations khác nhau; shared field không phải kênh trao đổi per-request đáng tin cậy. Ngay cả khi identity không đổi trong một request, các requests có thể xen kẽ.

Đúng hơn, snapshot **actor ID đã được cho phép dùng** vào local variable của **một `@Around`**, rồi truyền tới audit sink. Không nên giữ `Authentication` hoặc raw principal/token nếu không thực sự cần.

### Người dùng không chỉ đến từ HTTP request

Spring Aspect có thể được kích hoạt từ scheduled jobs, message listeners, batch executors, integration tests hoặc CLI. Những lời gọi đó có thể không có servlet request hoặc authenticated user. `actorProvider` cần định nghĩa rõ fallback như `system-job`/`anonymous` tùy policy, thay vì ngầm giả định `SecurityContextHolder.getContext().getAuthentication()` luôn khác null.

Với đa tenant, không dùng một field `currentTenant` trên aspect làm policy source of truth. Một request tenant A bị gắn ID tenant B có thể biến vấn đề observability thành lỗi data isolation nghiêm trọng.

## 7. Demo Java 17+ chạy được: hai calls đồng thời, output xác định

Demo này chứng minh lỗi chia sẻ field trên **một Spring singleton aspect**, không dựa vào race ngẫu nhiên.

Các bước:

1. Thread Alice gọi `service.process("alice")` qua Spring proxy; aspect ghi fields `alice`, target chờ ở gate.
2. Main xác nhận Alice đã tới target.
3. Thread Bob gọi `service.process("bob")` qua **cùng proxy**; aspect ghi đè fields thành `bob`, target chờ.
4. Main xác nhận Bob tới target rồi mới mở gate.
5. Hai calls hoàn tất; main in **sau khi lấy cả hai `Future.get()`**, nên thứ tự output ổn định.

Aspect sẽ lưu một snapshot **unsafe** (đọc từ shared fields) và **safe** (đọc từ local variables) vào `ConcurrentHashMap` chỉ để so sánh. Đây là ví dụ giáo dục, **không khuyến nghị** giữ raw request data trong map của aspect trên production.

Môi trường cố định: **JDK 17+, Spring Framework 6.1.14, AspectJ Weaver 1.9.22.1, Maven**. Đây là versions minh họa, không tuyên bố mới nhất.

### 7.1. Cấu trúc project

```text
spring-aop-invocation-scope/
├── pom.xml
└── src/main/java/com/example/scope/
    ├── GatedService.java
    ├── SnapshotAspect.java
    ├── AppConfig.java
    └── DemoApp.java
```

### 7.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-aop-invocation-scope</artifactId>
    <version>1.0.0</version>

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
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.22.1</version>
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
            </plugin>
        </plugins>
    </build>
</project>
```

`spring-context` cung cấp Spring application context và những Spring AOP dependencies trong cây phụ thuộc; `aspectjweaver` hỗ trợ parsing/matching của `@AspectJ` expressions. Không cần web server hoặc AspectJ weaving agent.

### 7.3. `src/main/java/com/example/scope/GatedService.java`

```java
package com.example.scope;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class GatedService {
    private final CountDownLatch aliceEntered = new CountDownLatch(1);
    private final CountDownLatch bobEntered = new CountDownLatch(1);
    private final CountDownLatch releaseGate = new CountDownLatch(1);

    public String process(String user) {
        if ("alice".equals(user)) {
            aliceEntered.countDown();
        } else if ("bob".equals(user)) {
            bobEntered.countDown();
        } else {
            throw new IllegalArgumentException("Unknown demo user");
        }

        try {
            releaseGate.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", ex);
        }

        return "ok:" + user;
    }

    public void awaitAlice() throws InterruptedException {
        if (!aliceEntered.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timeout waiting for alice");
        }
    }

    public void awaitBob() throws InterruptedException {
        if (!bobEntered.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timeout waiting for bob");
        }
    }

    public void release() {
        releaseGate.countDown();
    }
}
```

`CountDownLatch` đảm bảo thứ tự **Alice vào target trước, Bob vào target sau, rồi cả hai mới được phép rời target**. Các method điều khiển `awaitAlice`/`awaitBob`/`release` không khớp pointcut, nên không ghi đè aspect fields.

### 7.4. `src/main/java/com/example/scope/SnapshotAspect.java`

```java
package com.example.scope;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class SnapshotAspect {
    // CỐ TÌNH SAI: per-invocation state nằm trong singleton fields.
    private volatile String sharedUser;
    private volatile ProceedingJoinPoint sharedJoinPoint;
    private volatile long sharedStartNanos;

    // Chỉ dùng trong DEMO để ghi snapshots thread-safe.
    private final ConcurrentMap<String, String> unsafe =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> safe =
            new ConcurrentHashMap<>();

    @Around(
        value = "execution(public java.lang.String "
              + "com.example.scope.GatedService.process(..))"
              + " && args(user)",
        argNames = "user"
    )
    public Object around(ProceedingJoinPoint pjp, String user)
            throws Throwable {
        // ĐÚNG: mỗi invocation có local snapshot riêng.
        final String localUser = user;
        final ProceedingJoinPoint localJoinPoint = pjp;
        final long localStarted = System.nanoTime();

        // SAI: mỗi call ghi đè slot chung.
        sharedUser = user;
        sharedJoinPoint = pjp;
        sharedStartNanos = localStarted;

        try {
            return pjp.proceed();
        } finally {
            unsafe.put(user,
                    "fieldUser=" + sharedUser
                    + ", fieldJoinArg=" + sharedJoinPoint.getArgs()[0]);

            safe.put(user,
                    "localUser=" + localUser
                    + ", localJoinArg=" + localJoinPoint.getArgs()[0]);

            // Đo timing bằng localStarted, KHÔNG bằng sharedStartNanos.
            long elapsed = System.nanoTime() - localStarted;
            // elapsed được cố ý không in để giữ stdout xác định.
        }
    }

    public String unsafeFor(String user) {
        return unsafe.get(user);
    }

    public String safeFor(String user) {
        return safe.get(user);
    }
}
```

Các fields `sharedUser`/`sharedJoinPoint`/`sharedStartNanos` được viết **cố ý sai** để chứng minh race; `volatile` giúp các threads nhìn thấy updates nhưng không tạo isolation. Khi Bob vào target, shared fields cuối cùng là của Bob. Gate chỉ mở **sau** sự kiện ấy, nên cuối hai invocations đều đọc field của Bob.

`localUser`, `localJoinPoint` và `localStarted` vẫn riêng cho lời gọi đang thực thi. `ConcurrentHashMap` chỉ giúp main in snapshots sau completion mà không phát sinh thêm data race trong test; đừng copy nó thành unbounded production storage của user data.

### 7.5. `src/main/java/com/example/scope/AppConfig.java`

```java
package com.example.scope;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    public GatedService gatedService() {
        return new GatedService();
    }

    @Bean
    public SnapshotAspect snapshotAspect() {
        return new SnapshotAspect();
    }
}
```

`@EnableAspectJAutoProxy(proxyTargetClass=true)` bật Spring proxy-based AOP; CGLIB class proxy được dùng vì `GatedService` không implement interface. `SnapshotAspect` được đăng ký làm Spring bean bằng `@Bean`; `@Aspect` đơn lẻ chưa đăng ký bean.

### 7.6. `src/main/java/com/example/scope/DemoApp.java`

```java
package com.example.scope;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApp {
    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            GatedService service = context.getBean(GatedService.class);
            SnapshotAspect aspect = context.getBean(SnapshotAspect.class);
            System.out.println("isAopProxy=" + AopUtils.isAopProxy(service));

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<String> alice =
                        executor.submit(() -> service.process("alice"));
                service.awaitAlice();

                Future<String> bob =
                        executor.submit(() -> service.process("bob"));
                service.awaitBob();
                service.release();

                String aliceResult = alice.get(10, TimeUnit.SECONDS);
                String bobResult = bob.get(10, TimeUnit.SECONDS);

                System.out.println("resultAlice=" + aliceResult);
                System.out.println("resultBob=" + bobResult);
                System.out.println("unsafe(alice)=" + aspect.unsafeFor("alice"));
                System.out.println("unsafe(bob)=" + aspect.unsafeFor("bob"));
                System.out.println("safe(alice)=" + aspect.safeFor("alice"));
                System.out.println("safe(bob)=" + aspect.safeFor("bob"));
            } finally {
                service.release();
                executor.shutdownNow();
            }
        }
    }
}
```

Hai threads gọi **cùng Spring-managed proxy**. Main đợi kết quả futures trước khi in nên worker A/B hoàn thành theo thứ tự nào cũng không làm thay đổi **thứ tự các dòng stdout do main in ra**. Nếu timeout xảy ra trên môi trường quá chậm, ví dụ sẽ báo lỗi thay vì cho một kết luận sai.

### 7.7. Chạy và stdout dự kiến

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.scope.DemoApp
```

Trong điều kiện không timeout, các dòng do code tạo ra:

```text
isAopProxy=true
resultAlice=ok:alice
resultBob=ok:bob
unsafe(alice)=fieldUser=bob, fieldJoinArg=bob
unsafe(bob)=fieldUser=bob, fieldJoinArg=bob
safe(alice)=localUser=alice, localJoinArg=alice
safe(bob)=localUser=bob, localJoinArg=bob
```

Diễn giải:

1. Spring trả AOP proxy cho `GatedService`, không phải raw object được caller tự `new`.
2. Alice vào advice, gán shared fields, rồi đứng chờ trong target.
3. Bob vào **cùng singleton aspect**, ghi đè fields thành dữ liệu Bob, rồi cũng đứng chờ.
4. Main mở gate; target của Alice/Bob lần lượt có thể rời trước/sau tùy scheduler, nhưng cả hai trả đúng kết quả nghiệp vụ.
5. Cả hai unsafe snapshots nhìn **cùng slot cuối cùng** là Bob, nên Alice bị gán nhầm identity và join point.
6. Hai safe snapshots dùng **local variables** nên mỗi call được ghi đúng identity.

Đây là ví dụ có chủ đích chứng minh **cross-invocation data contamination**, không phải khuyến nghị ghi user ID thật vào log hoặc map. Code và stdout đã được rà soát tĩnh, chưa chạy Maven trên máy mục tiêu.

## 8. Cách tổ chức production-style

Một aspect production nên chứa **shared-safe dependencies**, không giữ mutable current invocation fields:

```java
@Aspect
public class SafeAuditAspect {
    private final ActorProvider actors;
    private final AuditSink sink;

    public SafeAuditAspect(ActorProvider actors, AuditSink sink) {
        this.actors = actors;
        this.sink = sink;
    }

    @Around("execution(public * com.example..service.*.*(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        final String actorId = actors.currentActorId();
        final String method = pjp.getSignature().getName();
        final long started = System.nanoTime();
        String outcome = "SUCCESS";

        try {
            return pjp.proceed();
        } catch (Throwable ex) {
            outcome = "FAILURE";
            throw ex;
        } finally {
            long duration = System.nanoTime() - started;
            sink.write(actorId, method, outcome, duration);
        }
    }
}
```

Đây là fragment cần interfaces `ActorProvider` và `AuditSink`. Một `AuditSink` thread-safe có thể ghi các sự kiện độc lập; không lưu “actor hiện tại” vào field của chính aspect. `actorId` nên là identifier được quyền ghi nhận, đã redacted/normalized theo policy.

Nếu `sink.write` có nguy cơ ném exception, cần quyết định chính sách xử lý để lỗi logging/telemetry không che lỗi nghiệp vụ nếu đó không phải yêu cầu. Nếu operation trả Future/Publisher, đo separately **method return** và **asynchronous completion**.

### Field nào hợp lý?

| Aspect field | Đánh giá |
| --- | --- |
| `private final AuditSink sink` | Đúng nếu implementation xử lý concurrency an toàn |
| `private final MeterRegistry registry` | Hợp lý theo contract thư viện |
| `private final String serviceName` | Immutable config |
| `private final AtomicLong totalCalls` | Có thể đúng nếu cố ý đếm tổng số calls |
| `private String currentUser` | Sai cho singleton aspect nhiều invocations |
| `private ProceedingJoinPoint lastJoinPoint` | Sai nếu dùng làm current invocation state hoặc lưu lâu |
| `private long startTime` | Sai làm timestamp cho invocation hiện tại |
| `private Throwable lastException` | Dễ trộn exceptions giữa requests |
| `private Map<String, Object> requests` không cleanup | Nguy cơ retention, PII và memory growth |

Cần phân biệt **shared aggregate state** (ví dụ counter) với **shared per-request state** (sai). Một counter thread-safe có thể dùng `LongAdder` hoặc Micrometer registry; nhưng đừng dùng counter để lưu user hay start time.

## 9. ThreadLocal không phải lời giải phổ quát

`ThreadLocal` có một slot **theo thread**, không phải theo Spring AOP invocation:

```java
private static final ThreadLocal<Long> STARTED = new ThreadLocal<>();

@Before("serviceOperation()")
public void before() {
    STARTED.set(System.nanoTime());
}

@After("serviceOperation()")
public void after() {
    long elapsed = System.nanoTime() - STARTED.get();
    STARTED.remove();
}
```

Đây là **fragment có nhiều rủi ro**, cần named pointcut. Các vấn đề:

- **Thread pool reuse:** quên `remove()` khiến request khác trên cùng worker thread thấy dữ liệu cũ.
- **Nested proxy calls:** cùng thread đi vào outer advice rồi inner advice, `STARTED.set` của inner **ghi đè** start time outer trước khi outer kết thúc.
- **Async callbacks:** Future completion có thể ở thread khác, không thấy ThreadLocal của caller.
- **Reactive Reactor:** có thể đổi scheduler/thread và nhiều subscriptions; cần `Reactor Context`/framework context propagation phù hợp.
- **Exception:** phải cleanup trong `finally`, không dựa vào normal return.
- **Ordering/matching:** hai advice riêng có thể không chạy đối xứng trong mọi cấu hình; không nên nối lifecycle qua ThreadLocal khi `@Around` local dễ hơn.

`InheritableThreadLocal` cũng không tự propagate an toàn vào worker pools đã tạo sẵn. Nếu bắt buộc dùng ThreadLocal để tương tác legacy API, hãy thiết kế **stack push/pop**, cleanup trong `finally`, handling nested invocation và thread hops. Với start time/actor của **chính một `@Around`**, local variables ít lỗi hơn.

### MDC: khôi phục context cũ

Nếu aspect gắn operation ID vào MDC, cần lưu giá trị cũ trong local rồi khôi phục:

```java
String previous = MDC.get("operationId");
try {
    MDC.put("operationId", safeOperationId);
    return pjp.proceed();
} finally {
    if (previous == null) {
        MDC.remove("operationId");
    } else {
        MDC.put("operationId", previous);
    }
}
```

Đây là fragment cần SLF4J `MDC`, `pjp` và `safeOperationId`. Việc khôi phục giúp nested calls, nhưng không tự giải quyết propagation qua thread khác. Đừng giữ raw security context/principal trong singleton aspect field.

## 10. Ngay cả một thread cũng có thể ghi đè field: reentrancy

```text
Thread 1:
  around(OuterService.run)  -> sharedStart = t1
     target outer
        -> gọi InnerService qua Spring proxy
           around(InnerService.work) -> sharedStart = t2
           target inner
           inner finally đọc t2
     outer finally đọc sharedStart=t2  <-- sai, phải là t1
```

Không cần đa luồng, **nested advice invocations trên cùng thread** cũng có thể overwite singleton field. Một `ThreadLocal<Long>` duy nhất cũng bị ghi đè trong nested calls.

**Đúng:** local `long started = System.nanoTime()` trong **mỗi** invocation của `@Around`.

Chú ý Spring AOP proxy boundary: nếu target gọi `this.otherMethod()`, đó là self-invocation bypass proxy thường thấy và không tạo một advisor invocation mới. Nhưng việc ấy không làm field-state an toàn, vì nested **cross-bean calls qua Spring proxy** vẫn xảy ra.

## 11. Thay đổi aspect instantiation có đủ để bảo vệ audit?

`perthis` và `pertarget` có thể liên kết instance aspect với proxy/target tương ứng theo cấu hình Spring hỗ trợ, nhưng **hai lời gọi cùng proxy/target vẫn có thể xảy ra đồng thời**. Prototype scope cũng không hứa tạo aspect mới cho từng method invocation. Vì vậy không thay field `currentActor` bằng một field trên per-target aspect rồi cho rằng audit đã được cách ly. Snapshot local và sink có concurrency contract rõ ràng vẫn là giải pháp phù hợp cho bài này.

## 12. Future, @Async và reactive Publisher

Một lời gọi Spring service có thể return:

- `CompletableFuture<T>` khi background task vẫn đang chạy.
- `Mono<T>` hoặc `Flux<T>` trước subscription, đặc biệt cold publisher.
- Handle từ task đã hoàn tất ngay, tùy implementation.

Nếu `@Around` chỉ đo `System.nanoTime()` quanh `pjp.proceed()`, nó đo **Java method invocation/assembly**, không tự đo Future completion/Publisher terminal signal.

Nếu cần đo completion:

```java
@Around("execution(* com.example.jobs.JobService.start(..))")
public Object monitor(ProceedingJoinPoint pjp) throws Throwable {
    final String methodName = pjp.getSignature().getName();
    final long started = System.nanoTime();

    CompletionStage<?> stage = (CompletionStage<?>) pjp.proceed();
    return stage.whenComplete((value, error) -> {
        long duration = System.nanoTime() - started;
        metrics.record(methodName, duration, error);
    });
}
```

Đây là **fragment** chỉ cho `CompletionStage` và cần `metrics`, không dành cho mọi `Future` thuần. Callback chỉ capture **methodName/start** (scalar local values), không capture raw `pjp` để truy cập sau khi original advice đã return.

Với Reactor, `@Around` có thể return publisher được decorate bằng `doOnSubscribe`/`doOnError`/`doFinally`, chú ý **một method return có thể có nhiều subscriptions**. Không tự `subscribe()` trong aspect chỉ để log vì có thể kích hoạt công việc thêm một lần. Không lưu `ProceedingJoinPoint` vào một lambda sống lâu chỉ để biết tên method; snapshot scalar metadata trước khi return.

Cần đánh giá thêm propagation của Spring Security context, Reactor `Context`, MDC và tracing khi callbacks chạy ở threads khác. Không cố dùng một shared `currentUser` field để “truyền context” sang async worker.

## 13. Kiểm thử và chẩn đoán

Một integration test đáng tin cho chủ đề này cần:

1. Bật Spring AOP, đăng ký aspect là Spring bean và lấy target qua `ApplicationContext`.
2. Dùng `AopUtils.isAopProxy(bean)` để xác minh proxy hiện diện; nhưng vẫn assert lời advice thực sự chạy.
3. Gọi **cùng proxy** từ hai threads với inputs/actor IDs phân biệt.
4. Dùng `CountDownLatch`/`CyclicBarrier` để tạo overlap có chủ đích; không trông chờ `Thread.sleep()` làm race “thỉnh thoảng” xuất hiện.
5. Assert event của A giữ đúng method/user/arguments của A và tương tự B.
6. Test nested calls: service A proxy → service B proxy → cùng aspect trên **một thread**.
7. Test exception path để cleanup/timing không bị bỏ qua.
8. Nếu dùng ThreadLocal/MDC, kiểm tra cleanup sau requests và reuse executor threads.
9. Với Future/Publisher, kiểm tra method return **khác** async completion/reactive terminal.
10. Kiểm tra data retention: audit sink không vô hạn giữ `JoinPoint` hoặc raw request payloads.
11. Nếu record timing, assert duration hợp lý chứ không cứng một số nanosecond chính xác.
12. Đảm bảo output kiểm thử không in credentials/tokens hoặc toàn bộ người dùng thật.

**Không nhầm việc một `AopUtils.isAopProxy(bean)` trả true với việc aspect cụ thể đang khớp mọi method.** Bean có thể được proxy bởi advisor khác; phải kiểm tra pointcut và events.

## 14. Lỗi phổ biến và cách sửa

| Dấu hiệu | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Audit của Alice gắn Bob | Shared `currentUser` singleton field | Snapshot actor thành local variable per advice call |
| Log args/method của request khác | Shared `ProceedingJoinPoint` field bị override | Giữ `pjp` local, chỉ extract metadata cần thiết |
| Duration quá ngắn/dài hoặc sai | Shared `startedNanos` bị overwrite | `final long start` local và elapsed trong `finally` |
| Thêm `volatile` vẫn sai | Visibility không tạo isolation | Xóa per-call fields |
| Dùng `AtomicReference` vẫn lẫn | Một slot chung giữ “last invocation” | Truyền scalar metadata explicit |
| Khóa `synchronized` gây nghẽn | Serialize business calls | Giữ locals, không khóa quanh `proceed()` |
| Aspect giữ request references quá lâu | Lưu JoinPoint/args trong field/map | Không retain raw request, dùng bounded/approved audit sink |
| ThreadLocal sai ở nested invocation | Một slot theo thread không phải stack | Local `@Around` hoặc push/pop có cleanup nếu cần |
| Log dính dữ liệu request trước trong pool | Quên `ThreadLocal.remove()`/MDC cleanup | `finally` + khôi phục state cũ |
| Future metric ghi trước job completion | Đo method return thay vì completion | CompletionStage callbacks hoặc reactive operators |
| Async callback không thấy user/MDC | Context chuyển thread | Framework context propagation/snapshot ID |
| Prototype/per-proxy aspect vẫn race | Không đồng nghĩa per invocation | Trạng thái chỉ để trong advice local |
| `@Before` ghi start, `@After` đọc field sai | Hai advice khác nhau chia sẻ singleton state | Dùng một `@Around` và local |
| Không có advice | Bean không thuộc Spring, AOP chưa bật, self-invocation | Kiểm tra context/proxy/invocation path |

## 15. Checklist review

- [ ] Không có field mang nghĩa `currentUser`, `currentJoinPoint`, `currentRequest`, `startTime` của invocation.
- [ ] Shared dependencies là thread-safe theo contract; configuration bất biến.
- [ ] `JoinPoint`/`ProceedingJoinPoint` được dùng tại invocation scope, không cache lâu dài.
- [ ] Actor ID được snapshot local và là dữ liệu được phép ghi audit.
- [ ] Timing dùng local `System.nanoTime()`, có `finally` cho sync success/failure.
- [ ] Method-return timing và Future/Publisher completion được phân biệt.
- [ ] Không dùng `volatile`/`AtomicReference` như giải pháp thay thế per-call isolation.
- [ ] Nếu có ThreadLocal/MDC, cleanup đúng và hỗ trợ nested calls.
- [ ] Không mặc định perthis/pertarget/prototype đồng nghĩa per request.
- [ ] Test overlap hai threads trên cùng Spring proxy với actor khác nhau.
- [ ] Test cross-bean nested invocation trên một thread.
- [ ] Không giữ raw request/credentials/arguments trong unbounded field/map.
- [ ] Spring bean registration, proxy boundary và pointcut đều được kiểm tra.

### Tài liệu Spring Framework chính thức

- **Aspect Instantiation Models** — default singleton, perthis và pertarget: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/instantiation-models.html
- **Declaring Advice / Around Advice** — `ProceedingJoinPoint`, `proceed()`: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- **AOP Concepts** — Spring proxy và advice execution: https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html
- **Proxying Mechanisms** — JDK/CGLIB, self-invocation: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- **Spring Security concurrency support** — context propagation: https://docs.spring.io/spring-security/reference/servlet/integrations/concurrency.html
- **Spring scheduling and @Async**: https://docs.spring.io/spring-framework/reference/integration/scheduling.html

**Kết luận:** Một Spring `@Aspect` singleton có thể xử lý nhiều join points đồng thời. Lưu **`JoinPoint`, người dùng hiện tại hoặc start timestamp vào fields** dễ tạo race, lẫn identity, sai duration và giữ dữ liệu nhạy cảm quá lâu. Giữ state trong **local scope của mỗi advice invocation**, đưa metadata tối thiểu đến sink thread-safe và dùng cơ chế async/reactive context propagation đúng lifecycle.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-state-concurrency-004">Phụ thuộc được tiêm vào aspect và yêu cầu an toàn luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Phụ thuộc được tiêm vào aspect và yêu cầu an toàn luồng trong Spring AOP

## 1. Vấn đề cần giải quyết

Trong ứng dụng Spring, một `@Aspect` thường là bean singleton của `ApplicationContext`. Khi nhiều request gọi những Spring bean được proxy, các advice có thể chạy đồng thời trên **cùng một instance aspect**. Việc Spring tiêm một dependency thành công chỉ xác nhận rằng bean được tìm thấy và gắn vào aspect; nó **không chứng minh** dependency hoặc cách dùng dependency là thread-safe.

Bài này tập trung vào **Spring-managed aspect, Spring AOP proxy, DI và phạm vi bean Spring**, không đi sâu lại các khái niệm AOP tổng quát.

Một `@Before`, `@AfterReturning` hay `@Around` có thể gọi repository, recorder, clock, policy service, tracer và client HTTP. Mọi tương tác với trạng thái dùng chung của những dependency này cần được kiểm tra theo cùng các tiêu chuẩn của Java concurrency.

## 2. Ba loại trạng thái cần phân biệt

| Loại | Ví dụ | Quy tắc |
| --- | --- | --- |
| Dữ liệu của **một lần gọi** | `JoinPoint`, đối số, user ID, thời điểm bắt đầu, stopwatch, exception | Giữ trong biến cục bộ của advice hoặc một context object cục bộ |
| Trạng thái **dùng chung có chủ đích** | Tổng số lượt gọi, bộ đếm metric, bộ nhớ đệm đồng thời | Dùng primitive/concurrent abstraction phù hợp: `AtomicInteger`, `LongAdder`, `ConcurrentHashMap`, hoặc synchronization khi cần |
| Dependency được tiêm | Logger, repository, singleton service, custom recorder | Xem contract và implementation; injection, `final` và singleton **không tự tạo** bảo đảm thread-safe |

Ví dụ **sai** trong một singleton aspect:

~~~java
@Aspect
@Component
class UnsafeTraceAspect {
    private Object[] lastArgs;
    private long startedAt;

    @Around("execution(* demo.CatalogService.quote(..))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        lastArgs = pjp.getArgs();
        startedAt = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            // Một thread khác có thể đã thay lastArgs và startedAt.
            System.out.println(lastArgs[0] + ":" + (System.nanoTime() - startedAt));
        }
    }
}
~~~

Điều nguy hiểm nằm ở fields dùng chung, không nằm ở `ProceedingJoinPoint` như một khái niệm. Một lời gọi B có thể ghi fields giữa hai bước của lời gọi A và làm A ghi sai request, log nhầm định danh, hoặc thậm chí rò rỉ thông tin.

Cách sửa đúng:

~~~java
@Around("execution(* demo.CatalogService.quote(..))")
public Object trace(ProceedingJoinPoint pjp) throws Throwable {
    Object[] argsForThisCall = pjp.getArgs().clone();
    long startedForThisCall = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        long elapsed = System.nanoTime() - startedForThisCall;
        record(argsForThisCall, elapsed);
    }
}
~~~

`record(...)` trong ví dụ chỉ là lời gọi minh họa; bản thân implementation của recorder cũng phải an toàn khi truy cập đồng thời. Sao chép mảng chỉ bảo vệ **cấu trúc mảng**, không deep-copy các đối tượng mutable bên trong.

## 3. Spring tạo và tiêm aspect như thế nào?

Với cấu hình `@EnableAspectJAutoProxy` hoặc hạ tầng AOP tương đương của Spring Boot, Spring lấy bean đã khai báo `@Aspect` và biến các advice phù hợp thành advisors cho proxy.

- `@Component` + `@Aspect` có thể được component scan tìm thấy; có thể thay bằng method `@Bean` trả về instance aspect.
- Constructor injection là lựa chọn thuận tiện nhất: dependency bắt buộc được xác nhận lúc khởi tạo và reference của nó có thể là `final`.
- Spring aspect mặc định sử dụng mô hình singleton; target bean được proxy có thể có scope khác. Scope target và scope dependency cần xem riêng.
- Spring AOP áp dụng tại **method execution qua proxy**. Một self-invocation như `this.quote(...)` bên trong target đi thẳng đến target và bỏ qua advice trên lời gọi đó.

Ví dụ constructor injection:

~~~java
@Aspect
@Component
public class TraceAspect {
    private final MetricRecorder recorder;

    public TraceAspect(MetricRecorder recorder) {
        this.recorder = recorder;
    }

    @Before("execution(* demo.CatalogService.quote(..))")
    public void before(JoinPoint joinPoint) {
        Object[] invocationArgs = joinPoint.getArgs().clone();
        recorder.increment();
        // invocationArgs chỉ tồn tại trong lần advice hiện tại.
    }
}
~~~

`final` giữ reference `recorder` không đổi sau construction; nếu implementation `recorder.increment()` dùng `count++` trên một `int` thường, vẫn có race/lost update.

## 4. Ví dụ chạy được: Spring 6.2, Java 17, Maven

Project tối thiểu, có đầy đủ các file:

~~~text
spring-aspect-dependency-concurrency/
  pom.xml
  src/main/java/demo/App.java
  src/main/java/demo/CatalogService.java
  src/main/java/demo/MetricRecorder.java
  src/main/java/demo/TraceAspect.java
~~~

**pom.xml**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-aspect-dependency-concurrency</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-context</artifactId>
      <version>6.2.19</version>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-aop</artifactId>
      <version>6.2.19</version>
    </dependency>
    <dependency>
      <groupId>org.aspectj</groupId>
      <artifactId>aspectjweaver</artifactId>
      <version>1.9.25</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
        <configuration><release>17</release></configuration>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.3.0</version>
      </plugin>
    </plugins>
  </build>
</project>
~~~

**CatalogService.java**

~~~java
package demo;

import org.springframework.stereotype.Component;

@Component
public class CatalogService {
    public String quote(String sku) {
        return "quote:" + sku;
    }
}
~~~

**MetricRecorder.java**

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class MetricRecorder {
    private final AtomicInteger calls = new AtomicInteger();

    public void increment() {
        calls.incrementAndGet();
    }

    public int count() {
        return calls.get();
    }
}
~~~

**TraceAspect.java**

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TraceAspect {
    private final MetricRecorder recorder;

    public TraceAspect(MetricRecorder recorder) {
        this.recorder = recorder;
    }

    @Before("execution(public * demo.CatalogService.quote(..))")
    public void beforeQuote(JoinPoint jp) {
        Object[] localArgs = jp.getArgs().clone();
        if (localArgs.length != 1) {
            throw new IllegalStateException("Unexpected method signature");
        }
        recorder.increment();
    }
}
~~~

**App.java**

~~~java
package demo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan("demo")
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class App {
    public static void main(String[] args) throws Exception {
        try (var ctx = new AnnotationConfigApplicationContext(App.class)) {
            CatalogService catalog = ctx.getBean(CatalogService.class);
            MetricRecorder recorder = ctx.getBean(MetricRecorder.class);

            ExecutorService pool = Executors.newFixedThreadPool(4);
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<String>> futures = new ArrayList<>();
                for (int i = 0; i < 20; i++) {
                    final int index = i;
                    Callable<String> task = () -> {
                        start.await();
                        return catalog.quote("sku-" + index);
                    };
                    futures.add(pool.submit(task));
                }
                start.countDown();
                Set<String> distinct = new HashSet<>();
                for (Future<String> future : futures) {
                    distinct.add(future.get());
                }
                System.out.println("distinctResults=" + distinct.size());
                System.out.println("recordedCalls=" + recorder.count());
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy trong thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.App
~~~

Kết quả ứng dụng mong đợi (bỏ qua thông báo riêng của Maven nếu có):

~~~text
distinctResults=20
recordedCalls=20
~~~

Kết quả trên xác nhận bộ đếm thread-safe và dữ liệu tác vụ độc lập. Nó **không chứng minh rằng mọi dependency tùy ý** cũng thread-safe. Đoạn mã này đã được rà soát tĩnh theo API Spring/Java; không có kết quả thực thi Maven được khẳng định.

## 5. Dependency nào cần kiểm tra kỹ?

### Singleton service có state mutable

Một `@Service` singleton chứa `ArrayList`, `HashMap`, `int counter`, `StringBuilder` hoặc request ID trong field có nguy cơ race khi aspect gọi đồng thời. Đừng coi `private final HashMap` là an toàn chỉ vì reference final. Dùng cấu trúc đồng thời, lock hoặc thiết kế stateless tùy invariant.

### Spring Data repository, giao dịch và persistence context

Các bean repository thường là proxy Spring được thiết kế cho shared injection. Nhưng entity/JPA persistence context cụ thể, transaction resource và các mutable objects trả về không mặc nhiên được chia sẻ giữa các luồng. Giữ transaction theo đúng ranh giới nghiệp vụ; đừng lưu `EntityManager` đã gắn với một invocation vào field aspect để dùng cho request tiếp theo.

### Bean request-scoped và scoped proxy

Nếu một singleton aspect cần dependency request-scoped, hãy thiết kế scoped proxy hoặc `ObjectProvider` theo Spring. Việc chuyển thread qua `@Async` hoặc `CompletableFuture` có thể không còn request context để resolve dependency. Không cache một request-scoped target vừa resolve vào field singleton.

### HTTP clients, metrics, loggers và tracing

Đọc contract của thư viện cụ thể. Nhiều client/metric abstractions cho phép dùng lại đồng thời, nhưng builder/request/response/session cụ thể có thể mutable. Dùng per-invocation builder, message và span. Với tracing context gắn thread, close scope trong `finally` đúng thread và tránh để lẫn request.

## 6. Atomic không đồng nghĩa mọi thuật toán đều đúng

`AtomicInteger.incrementAndGet()` bảo đảm tăng đếm không mất cập nhật. Nó **không** bảo đảm một chuỗi kiểm tra–cập nhật trên nhiều field:
 
~~~java
if (active.get() < limit) {
    active.incrementAndGet(); // Có thể vượt limit khi nhiều thread chạy cùng lúc.
}
~~~

Nếu invariant cần toàn bộ thao tác nguyên tử, dùng CAS loop, `Semaphore`, lock hoặc cơ chế dữ liệu có thao tác nguyên tử tương ứng. Với metric tần suất rất cao, `LongAdder` có thể phù hợp hơn `AtomicInteger` nếu mục tiêu là throughput và không cần snapshot tuyến tính hóa chính xác tại mọi thời điểm.

## 7. Advice chạy trên luồng nào?

Advice Spring AOP đồng bộ chạy trong luồng đang thực hiện lời gọi qua proxy, trừ khi các advisor như `@Async`, scheduler hoặc reactive pipeline thay đổi thời điểm/luồng quan sát.

- `@Async`: advice bên ngoài async interceptor có thể chạy ở caller thread, trong khi target thực thi ở executor thread. Thứ tự advisor và cấu hình proxy quyết định ranh giới quan sát.
- `Future`/`CompletionStage`: method return thành công với future **khác** công việc async hoàn tất thành công. Không coi `@AfterReturning` là callback terminal result.
- Reactor `Mono`/`Flux`: method có thể trả một publisher trước khi pipeline được subscribe. Để theo dõi terminal signal, tích hợp Reactor operators một cách có chủ đích, không chia sẻ mutable field ở aspect.
- ThreadLocal: không lưu vào field để “thay thế” ThreadLocal; nếu dùng ThreadLocal thì có hợp đồng set/restore/remove, kiểm tra thread pool và self-invocation.

## 8. Cách kiểm thử race và phạm vi Spring

Kiểm thử song song nên sử dụng barrier/latch để tạo cạnh tranh, chạy nhiều lượt và khẳng định invariant. Tránh so sánh thứ tự log giữa threads vì scheduler không cố định. Với demo trên, assert được `recordedCalls == N` và mỗi response đúng input; cố tình dùng `int++` không bảo đảm mọi lần chạy đều thất bại, vì race là bất định.

Kiểm tra bean thực sự đi qua proxy bằng `AopUtils.isAopProxy(bean)`, chạy cả JDK proxy và class-based proxy khi ứng dụng có hai mô hình. Thử self-invocation, method `final`/`private` và target tạo bằng `new` để phân biệt lỗi proxy boundary với lỗi concurrency.

## 9. Checklist áp dụng trong Spring

1. Xác nhận lifecycle của aspect trong `ApplicationContext`, loại target và cấu hình proxy.
2. Giữ đối số, `JoinPoint`, user ID, thời điểm bắt đầu, kết quả và exception trong scope của invocation.
3. Kiểm tra mọi dependency được tiêm: có mutable state không, thread-safety contract nào, scope nào?
4. Dùng constructor injection và `final` reference để rõ dependency, nhưng kiểm tra riêng tính an toàn của implementation.
5. Đo/kiểm thử concurrency thực tế, kiểm tra multi-field invariants và event ordering, không dựa trên một lần test.
6. Rà soát `@Async`, giao dịch, request scope, reactive context và cleanup trước khi triển khai.

## 10. Tài liệu chính thức

- Spring Framework Reference — Core AOP, `@AspectJ` support, advice và Spring proxies: https://docs.spring.io/spring-framework/reference/core/aop.html
- Spring Framework Reference — Bean scopes: https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html
- Java API — AtomicInteger: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html

**Ghi nhớ:** Trong Spring, một dependency được tiêm vào aspect có thể được **nhiều lời gọi proxy dùng đồng thời**. Hãy giữ trạng thái lời gọi trong local variables và chứng minh an toàn luồng cho mọi trạng thái thực sự chia sẻ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-state-concurrency-005">Scope của bean đích không tự thay đổi scope của aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Scope của bean đích không tự thay đổi scope của aspect trong Spring AOP

## 1. Tình huống hay bị hiểu nhầm

Một Spring `@Aspect` ghi nhận lời gọi của `JobService`. Người phát triển đổi `JobService` từ singleton thành `@Scope("prototype")` và mong rằng mỗi `JobService` cũng có một bản sao aspect riêng. Trong Spring AOP, suy luận đó **không đúng**.

**Scope của target bean, vòng đời của Spring proxy và vòng đời của bean `@Aspect` là những quyết định khác nhau.** Một singleton aspect vẫn có thể tư vấn nhiều target instances, kể cả các target có scope prototype/request/session.

Bài này chỉ nói về **Aspect trong Spring**, Spring bean factory, Spring AOP proxy và phạm vi thực thi advice. Các kiến thức AOP tổng quát không được lặp lại.

## 2. Ba đối tượng phải phân biệt

| Thành phần | Vai trò | Ai quyết định lifecycle? |
| --- | --- | --- |
| **Aspect bean** | Chứa các method `@Before`, `@Around`, ... và dependency của advice | Spring IoC container, bean definition của aspect; mặc định singleton với `@Component` hoặc `@Bean` |
| **Target bean** | Chứa logic nghiệp vụ được advice áp dụng | Bean definition của target: singleton, prototype, request, session hoặc custom scope |
| **AOP proxy** | Đối tượng mà caller thực sự gọi để interceptor/advisor chạy | Cấu hình proxy/advisor và bean lifecycle; có thể thêm scoped proxy khi cần |

Chỉ nhìn vào `this`, `getClass()` hoặc danh tính proxy không đủ để kết luận target đang có scope nào. Với một **scoped proxy** request/session, một reference proxy có thể tồn tại lâu và delegate đến các target khác nhau theo scope; với **prototype bean** lấy trực tiếp từ `getBean()`, mỗi lần lấy có thể tạo một instance/proxy mới.

Trong một `@Aspect` singleton, fields là shared state của các lời gọi advice. Hai target khác nhau **không** biến các fields đó thành state riêng.

## 3. Ví dụ sai: lưu state của target trong field aspect

~~~java
@Aspect
class WrongTrackingAspect {
    private String lastJobId;

    @Before("execution(* demo.JobService.execute(..))")
    public void before(JoinPoint jp) {
        lastJobId = ((JobService) jp.getTarget()).toString();
    }

    @After("execution(* demo.JobService.execute(..))")
    public void after() {
        // lastJobId có thể do một target/proxy/lời gọi khác ghi vào.
        System.out.println("completed " + lastJobId);
    }
}
~~~

Dù mọi `JobService` là prototype, `WrongTrackingAspect` singleton vẫn có một field `lastJobId`. Khi hai lời gọi xen nhau, người dùng A có thể ghi nhầm ID của B. Sai lầm tương tự xảy ra nếu lưu `JoinPoint`, request context, exception, `StopWatch`, `startNanos` hoặc danh sách arguments vào field aspect.

Cách đúng nhất cho một phép đo bao quanh invocation thường là **local variable**:

~~~java
@Around("execution(* demo.JobService.execute(..))")
public Object time(ProceedingJoinPoint pjp) throws Throwable {
    long started = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        long elapsed = System.nanoTime() - started;
        // Ghi metric bằng dependency thread-safe; không lưu started trên field.
        System.out.println("elapsedNanos=" + elapsed);
    }
}
~~~

Đây là snippet về scope, không phải ví dụ cho stdout cố định: thời gian là không xác định và Spring `@Around` phải được đăng ký vào container để chạy.

## 4. Demo Java 17 + Spring Framework: target prototype, aspect singleton

Demo sau minh họa **hai target khác nhau**, **hai proxy**, nhưng **một aspect bean** với tổng số lượt advice bằng hai. Tất cả code cần thiết được liệt kê.

~~~text
spring-aop-target-vs-aspect-scope/
  pom.xml
  src/main/java/demo/JobService.java
  src/main/java/demo/JobServiceImpl.java
  src/main/java/demo/AuditAspect.java
  src/main/java/demo/App.java
~~~

**pom.xml**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-aop-target-vs-aspect-scope</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-context</artifactId>
      <version>6.2.19</version>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-aop</artifactId>
      <version>6.2.19</version>
    </dependency>
    <dependency>
      <groupId>org.aspectj</groupId>
      <artifactId>aspectjweaver</artifactId>
      <version>1.9.25</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
        <configuration><release>17</release></configuration>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.3.0</version>
      </plugin>
    </plugins>
  </build>
</project>
~~~

**JobService.java**

~~~java
package demo;

public interface JobService {
    String execute();
}
~~~

**JobServiceImpl.java**

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

public class JobServiceImpl implements JobService {
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private final int id = NEXT_ID.incrementAndGet();

    @Override
    public String execute() {
        return "job-" + id;
    }
}
~~~

Mỗi target instance giữ `id` riêng. `NEXT_ID` chỉ phục vụ việc nhìn thấy rằng constructor đã tạo hai target khác nhau; không phải state của aspect.

**AuditAspect.java**

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    private final AtomicInteger calls = new AtomicInteger();

    @Before("execution(* demo.JobService.execute(..))")
    public void beforeExecution() {
        System.out.println("aspectCall=" + calls.incrementAndGet());
    }

    public int callCount() {
        return calls.get();
    }
}
~~~

Bộ đếm aspect là shared state **có chủ đích** và dùng `AtomicInteger` để an toàn cho tăng đếm đồng thời. Nếu cần tách metric theo target, nên dùng key ổn định trong một concurrent registry và xem xét cleanup/lifecycle, thay vì đặt một field `lastTarget` trên singleton aspect.

**App.java**

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Scope;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class App {
    @Bean
    public AuditAspect auditAspect() {
        return new AuditAspect(); // singleton theo mặc định của @Bean
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public JobService jobService() {
        return new JobServiceImpl();
    }

    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(App.class)) {
            JobService one = ctx.getBean(JobService.class);
            JobService two = ctx.getBean(JobService.class);

            AuditAspect aspectOne = ctx.getBean(AuditAspect.class);
            AuditAspect aspectTwo = ctx.getBean(AuditAspect.class);

            System.out.println("first=" + one.execute());
            System.out.println("second=" + two.execute());
            System.out.println("differentProxies=" + (one != two));
            System.out.println("aopProxy=" + AopUtils.isAopProxy(one));
            System.out.println("sameAspect=" + (aspectOne == aspectTwo));
            System.out.println("adviceCalls=" + aspectOne.callCount());
        }
    }
}
~~~

Chạy:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.App
~~~

Kết quả mong đợi của phần `System.out` do ứng dụng sinh ra:

~~~text
aspectCall=1
first=job-1
aspectCall=2
second=job-2
differentProxies=true
aopProxy=true
sameAspect=true
adviceCalls=2
~~~

Lý do: bean method `jobService()` có scope prototype nên hai lần `getBean(JobService.class)` tạo hai target riêng. Spring AOP nhận diện `AuditAspect`, tạo advisor cho `JobService.execute()` và tạo proxy cho từng bean cần advice. Bean `AuditAspect` vẫn singleton, được gọi hai lần và cộng cùng một `AtomicInteger`.

Đây là **expected output được rà soát tĩnh** theo Spring AOP/Java API; không khẳng định đã thực thi Maven trong môi trường hiện tại.

## 5. Đổi scope target sẽ thay đổi điều gì?

### Singleton target

Một bean singleton thường được lấy lặp lại dưới cùng một reference proxy/target trong một context. Nếu được advice matching thì nhiều caller chạy qua cùng proxy; aspect cũng có thể singleton. Target và aspect là **hai singleton khác nhau**, không dùng chung fields của nhau.

### Prototype target

Mỗi lần container tạo bean prototype mới có thể có một target mới và proxy mới. Nếu singleton bean chỉ được inject một lần với prototype bean bình thường, reference prototype ấy **không tự đổi target sau mỗi lần gọi method**. Muốn lấy instance mới trên từng nhu cầu, dùng `ObjectProvider<JobService>` hoặc lookup method phù hợp:

~~~java
@Component
class WorkCoordinator {
    private final ObjectProvider<JobService> jobServices;

    WorkCoordinator(ObjectProvider<JobService> jobServices) {
        this.jobServices = jobServices;
    }

    void runTwice() {
        jobServices.getObject().execute();
        jobServices.getObject().execute();
    }
}
~~~

Snippet cần import `org.springframework.beans.factory.ObjectProvider` và `org.springframework.stereotype.Component`, cùng một bean definition prototype của `JobService`. Dù hai lần có hai target, singleton aspect vẫn được dùng chung.

### Request hoặc session scoped target

Trong web application, scoped proxy có thể là một object được tiêm lâu dài nhưng delegate đến request/session target khác theo context hiện hành. Do đó **proxy reference giống nhau không chứng minh target reference giống nhau**.

Ví dụ annotation thường dùng trong ứng dụng web:

~~~java
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
@Component
class RequestJobService { /* ... */ }
~~~

Đây là đoạn cấu hình minh họa cho môi trường web, cần import `org.springframework.context.annotation.Scope`, `ScopedProxyMode`, `Component`; ứng dụng demo Maven ở phần 4 không cần request scope và không tạo web request context.

## 6. Scope của aspect và mô hình instantiation

Trong Spring proxy-based `@AspectJ` support, mô hình phổ biến và được Spring AOP hỗ trợ là **singleton aspects**. Instantiation models như `perthis`, `pertarget`, `percflow` của AspectJ không phải cơ chế tự có của Spring AOP proxy để làm một aspect mới cho mỗi target hoặc invocation.

Nếu cần state riêng cho mỗi target, cân nhắc đặt nó ở target (khi phù hợp domain), ở một dependency được tra cứu theo scope, hoặc dùng registry keyed theo target lifecycle với dọn dẹp rõ ràng. Nếu bài toán thật sự đòi hỏi AspectJ instantiation hoặc field/method join points ngoài Spring proxy execution, đó là yêu cầu thiết kế tích hợp AspectJ weaving riêng, không phải hệ quả của `@Scope` trên một target Spring.

Tránh đặt `@Scope("prototype")` lên aspect và **mặc định giả định** mọi proxy target sẽ nhận aspect instance riêng. Hãy kiểm thử lifecycle thực tế của configuration nếu làm một thiết kế ngoài mô hình singleton thông thường.

## 7. Một số hậu quả khi dùng nhiều ApplicationContext

Singleton nghĩa là **một instance trên từng Spring bean definition trong mỗi container phù hợp**, không nhất thiết là đúng một instance cho toàn JVM.

- Hai ApplicationContext độc lập có thể tạo hai bean aspect singleton khác nhau, mỗi cái có counter riêng.
- Parent/child context và bean visibility có thể khiến một aspect được đăng ký ở nhiều nơi hoặc advisors xuất hiện theo cách không dự kiến.
- Các target prototype trong cùng context vẫn có thể được một aspect singleton tư vấn.
- Nếu test khởi tạo context mới, static fields trong demo `JobServiceImpl.NEXT_ID` có lifecycle khác Spring bean scope; không đồng nhất static Java state với Spring singleton scope.

Để phân biệt, kiểm tra `ctx.getBean(AuditAspect.class)`, `ctx.isSingleton("auditAspect")`, `ctx.isPrototype("jobService")` và inspection qua `AopUtils.isAopProxy`.

## 8. Điều kiện advice thực sự chạy

Thay đổi scope không sửa các giới hạn nền của Spring AOP:

1. Caller phải đi qua Spring AOP proxy. Đối tượng do `new JobServiceImpl()` bên ngoài Spring tạo ra không tự được advice.
2. Self-invocation (`this.execute()` nội bộ target) không đi lại qua proxy tương ứng.
3. Spring AOP tập trung vào **method execution**, không bắt trực tiếp constructor hoặc field access.
4. JDK dynamic proxy thể hiện interface methods; class-based proxy có giới hạn với `final`, `private`, method không override được.
5. Pointcut phải thực sự match target method, và aspect/advisor cần được đăng ký trong đúng context.

Vì vậy, nếu advice có vẻ chạy một lần rồi mất, trước khi kết luận scope “chuyển” sang aspect, cần kiểm tra xem lần gọi thứ hai lấy proxy từ Spring hay dùng raw instance.

## 9. Concurrent calls vẫn dùng chung aspect

Prototype target có thể giảm khả năng chia sẻ fields **bên trong target**, nhưng không loại bỏ khả năng hai lời gọi chạy đồng thời trong cùng aspect singleton. Tương tự, scoped proxy request có thể gọi các target request-specific khác nhau nhưng dùng cùng một aspect recorder.

Không làm như sau:

~~~java
@Aspect
class RiskyAspect {
    private long startedAt;
    private String currentUser;

    @Around("execution(* demo.JobService.execute(..))")
    Object around(ProceedingJoinPoint pjp) throws Throwable {
        startedAt = System.nanoTime();
        currentUser = findUser(); // giả sử helper riêng
        return pjp.proceed();
    }
}
~~~

Hãy đặt `startedAt` và user context vào local variables hoặc một scoped dependency có contract rõ. `ThreadLocal` không phải “per request” tự động trong `@Async`/reactive flows; dùng nó thì phải cleanup/restore và hiểu thread boundary.

`AtomicInteger` phù hợp cho counter, nhưng với invariant liên quan nhiều fields hoặc nhiều map operations cần synchronization/atomic operation tổng thể. Đồng thời, **đừng giữ reference tới target prototype trong map mãi mãi** nếu không có eviction, vì như vậy tạo memory leak.

## 10. Kiểm thử lifecycle và nội dung advice

Một bộ integration test thực tế nên khẳng định:

| Kiểm tra | Kỳ vọng trong cấu hình demo |
| --- | --- |
| `ctx.isPrototype("jobService")` | `true` |
| `ctx.isSingleton("auditAspect")` | `true` |
| `ctx.getBean(JobService.class)` hai lần | Hai reference khác |
| `ctx.getBean(AuditAspect.class)` hai lần | Cùng reference |
| `AopUtils.isAopProxy(jobService)` | `true` |
| Gọi `execute()` trên cả hai proxy | Aspect counter tăng hai |
| Tạo `new JobServiceImpl()` và gọi trực tiếp | Không tự bị Spring advice |
| Hai luồng gọi hai target cùng lúc | Shared aspect fields phải thread-safe |

Khi kiểm thử race, dùng `CountDownLatch`/barrier và assertions thay vì dựa vào thứ tự log giữa threads; scheduler không đảm bảo thứ tự output. Với Java 17, không suy ra tính an toàn luồng chỉ từ việc một lần chạy test cho kết quả đúng.

## 11. Lỗi phổ biến và cách gỡ

**“Target prototype rồi, sao aspect chỉ có một constructor call?”** — Đây là hai bean definitions riêng. Kiểm tra `ctx.isSingleton("auditAspect")` và bean scope riêng của target.

**“Một proxy được inject vào singleton, sao target không đổi?”** — Prototype injection thông thường xảy ra lúc inject. Dùng `ObjectProvider` nếu thực sự cần lấy prototype mới mỗi lần; với request scoped proxy, thiết kế delegate lại theo scope phù hợp.

**“Hai target khác nhau nhưng log lại cùng một user/startTime?”** — Aspect lưu dữ liệu invocation vào field dùng chung. Sửa bằng local variables và dependency thread-safe.

**“Có `@Aspect` rồi nhưng không log?”** — Kiểm tra `@EnableAspectJAutoProxy`, advisor/pointcut, bean managed, JDK/CGLIB proxy, method visibility và self-invocation.

**“Test đơn luồng đúng, production lẫn request?”** — Các lỗi race cần kiểm thử với concurrency và assertions về dữ liệu, không chỉ đếm log.

## 12. Tài liệu đối chiếu

- Spring Framework Reference — `@AspectJ` support và instantiation models: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/instantiation-models.html
- Spring Framework Reference — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework Reference — Bean Scopes: https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html
- Spring Framework Reference — `@Bean`, `@Scope` và Java-based configuration: https://docs.spring.io/spring-framework/reference/core/beans/java.html

**Kết luận thực hành:** Hãy kiểm tra scope target, scope aspect và dạng proxy **độc lập**. Dù target là prototype hay request-scoped, đừng đặt dữ liệu mỗi invocation vào fields của singleton `@Aspect`; hãy dùng local state và chứng minh thread safety cho dữ liệu thực sự dùng chung.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-state-concurrency-006">ThreadLocal trong aspect và trách nhiệm dọn dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# ThreadLocal trong aspect và trách nhiệm dọn dữ liệu trong Spring AOP

## 1. Vấn đề cần giải quyết

Một Spring `@Aspect` dùng để logging, profiling hoặc gắn operation context thường được đăng ký thành **singleton Spring bean**. Nhiều request trên nhiều thread cùng đi qua instance aspect đó. Lưu thông tin invocation vào một field mutable bình thường có thể gây **race condition**. Lưu nó trong `ThreadLocal<T>` giúp mỗi thread truy cập giá trị riêng, nhưng không có nghĩa Spring tự tạo, truyền hoặc dọn context cho từng request.

Hai lỗi thường xảy ra:

1. **Không gọi `remove()`:** một thread trong pool giữ dữ liệu của request trước, khiến request sau thấy sai operation, tenant hoặc correlation ID.
2. **Luôn gọi `remove()` trong lời advice lồng nhau:** khi một Spring proxy gọi Spring proxy khác trên cùng thread, inner advice có thể xóa context mà outer advice vẫn cần.

Mẫu an toàn cho scope được sở hữu bởi Spring `@Around` advice là:

~~~java
String previous = context.get();
context.set(operation);
try {
    return pjp.proceed();
} finally {
    if (previous == null) {
        context.remove();
    } else {
        context.set(previous);
    }
}
~~~

Mẫu này **lưu trạng thái cũ trước khi set, chỉ tiếp tục interceptor chain một lần, luôn restore/remove khi invocation thoát**, đồng thời giữ nguyên return value hoặc exception mà `proceed()` cung cấp.

Bài học tập trung vào **Spring Aspect và Spring AOP proxy-based** theo Special Note của module, không lặp lại kiến thức Aspect tổng quát. Mục tiêu là giải thích lifecycle của ThreadLocal, nested interception, cleanup trên exception, self-invocation, thread reuse và context propagation khi dùng Spring async/reactive.

## 2. ThreadLocal trong singleton aspect hoạt động ra sao?

Aspect sai về concurrency:

~~~java
@Aspect
public class UnsafeAspect {
    private String activeOperation;
}
~~~

Nếu hai threads chạy cùng advice, T1 có thể set `activeOperation="pay"` rồi T2 set `"report"` trước khi T1 đọc. Một object aspect singleton có **một shared field** cho mọi thread.

Với:

~~~java
@Aspect
public class ScopedAspect {
    private final ThreadLocal<String> activeOperation = new ThreadLocal<>();
}
~~~

Aspect vẫn có một field `ThreadLocal` được chia sẻ, nhưng mỗi thread sử dụng entry gắn với riêng thread đó. T1 và T2 có thể thấy các value độc lập.

Điều này **không** biến ThreadLocal thành:

- Request scope do Spring quản lý;
- Transaction scope;
- Security context đã được xác thực;
- Context cho mọi tác vụ CompletableFuture hoặc `@Async`;
- Reactor subscriber context;
- Một giá trị khác nhau cho mọi nested method trên cùng thread;
- Một cơ chế tự cleanup khi Spring method trả về.

Các API quan trọng:

| API | Ý nghĩa |
| --- | --- |
| `get()` | Đọc value của ThreadLocal trên thread hiện tại |
| `set(value)` | Đặt hoặc ghi đè value trên thread hiện tại |
| `remove()` | Xóa entry hiện tại trên chính thread đang gọi |
| `ThreadLocal.withInitial(...)` | Tạo value ban đầu khi thread chưa có entry; cần quản lý lifetime của object được cấp phát |

Nếu bạn set một value rồi không remove, entry có thể tồn tại xuyên nhiều lượt sử dụng **cùng thread**. Với web server hoặc executor có thread pool, đó là rủi ro contamination và memory retention.

## 3. Vì sao cleanup phải nằm trong finally?

Cách sai:

~~~java
@Around("execution(* example.work..*.*(..))")
public Object track(ProceedingJoinPoint pjp) throws Throwable {
    operation.set(pjp.getSignature().getName());
    Object result = pjp.proceed();
    operation.remove();
    return result;
}
~~~

Nếu target hoặc advisor phía trong ném `Throwable`, lệnh `remove()` sau `proceed()` bị bỏ qua. Caller vẫn thấy lỗi, còn ThreadLocal có thể giữ giá trị cũ.

Cách sửa đơn giản khi **không có scope trước đó cần bảo toàn**:

~~~java
operation.set(pjp.getSignature().getName());
try {
    return pjp.proceed();
} finally {
    operation.remove();
}
~~~

`finally` được thực thi trên normal return và exceptional exit thông thường một khi đã vào khối try. Tuy nhiên, nếu có **nested invocation trên cùng ThreadLocal**, remove vô điều kiện trong inner advice sẽ xóa mất context của outer advice.

**Quy tắc:** Cleanup không chỉ là “xóa”. Cleanup là **khôi phục trạng thái thread trước khi scope được mở**; xóa chỉ là trường hợp trước đó chưa có value.

## 4. Nested Spring proxy calls: lỗi remove vô điều kiện

Giả sử:

~~~text
Caller -> Spring proxy của PrimaryService.process()
           @Around outer: set("process")
             target PrimaryService.process()
               -> Spring proxy SecondaryService.check()
                    @Around inner: set("check")
                      target SecondaryService.check()
                    @Around inner: finally
               target PrimaryService cần lại "process"
           @Around outer: finally
~~~

Cả hai lời gọi cùng dùng một thread và một OperationContext bean.

Nếu inner `finally` gọi `remove()`, khi trở về outer target, `get()==null` thay vì `"process"`. Outer còn đang chạy nhưng scope đã bị phá.

Cách đúng:

~~~java
String previous = context.get();  // Inner nhớ "process".
context.set("check");
try {
    return pjp.proceed();
} finally {
    if (previous == null) {
        context.remove();
    } else {
        context.set(previous);      // Inner trả lại "process".
    }
}
~~~

Outer vào từ thread trống nên `previous=null`, cuối cùng `remove()`. Inner vào lúc outer đang active nên `previous="process"`, cuối cùng restore previous. Cấu trúc này bảo toàn **cả normal và exceptional exits**.

Nếu null có nghĩa là một value hợp lệ trong domain, không được tùy tiện suy `null=không có entry`; nên dùng representation riêng cho presence hoặc scope stack. Trong bài này null là “không có current operation”.

## 5. Spring AOP proxy boundary quan trọng thế nào?

Spring AOP dùng **JDK dynamic proxy hoặc CGLIB class-based proxy**. Khi client gọi method qua Spring-managed proxy và pointcut match, around advice có thể được gọi; ngược lại:

- Object tạo bằng `new Service()` bên ngoài Spring **không** tự được advice.
- Lời gọi `this.someMethod()` bên trong target **không quay lại proxy**, nên không tạo một lượt advice mới cho method đó.
- Chuyển JDK sang CGLIB **không khắc phục self-invocation** trong Spring AOP.
- Một outer `@Around` advisor khác có thể shortcut không gọi `proceed()`, khiến inner advice chưa được vào.
- Một bean có Spring AOP proxy không đồng nghĩa **mọi method** đều match cùng advisor.
- Các method `final` và `private` không được CGLIB override/intercept như method bình thường.

**Ownership:** Một advice chỉ dọn state do chính nó đặt. Nếu inner advice chưa bao giờ được vào do outer shortcut, inner không có nghĩa vụ cleanup state mà nó chưa tạo. Nếu một component khác đặt value, scope này phải restore đúng previous value của component đó.

## 6. Ví dụ Maven/Java 17+ hoàn chỉnh

Demo sử dụng **Spring Framework 6.2.19**, Java **17+**, AspectJ Weaver **1.9.25.1**. Hai Spring-managed services đều match cùng aspect, nên chúng có thể tạo **nested interception**. Bốn tình huống được kiểm chứng:

1. Normal nested call;
2. Inner target ném exception, lỗi lan đến caller;
3. Inner ném lỗi nhưng outer target catch và recover;
4. Self-invocation `this.local()` không tạo nested advice mới.

Project dùng một `OperationContext` bean chứa ThreadLocal. `ScopeAspect` là Spring-managed `@Aspect`, dùng save/restore trong `try/finally`. Dòng stdout ổn định và `Main` có assertion để xác minh caller thread không còn context sau từng lời gọi.

### 6.1. Cấu trúc project

~~~text
spring-aop-threadlocal-demo/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── Main.java
    ├── aop/ScopeAspect.java
    ├── context/OperationContext.java
    └── work/
        ├── PrimaryService.java
        └── SecondaryService.java
~~~

### 6.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-aop-threadlocal-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework</groupId>
                <artifactId>spring-framework-bom</artifactId>
                <version>6.2.19</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.25.1</version>
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
            </plugin>
        </plugins>
    </build>
</project>
~~~

Với ứng dụng Spring Boot thực tế, nên để Spring Boot parent/BOM quản lý các Spring dependencies thay vì cố sao chép version của demo.

### 6.3. OperationContext.java

~~~java
package example.context;

public final class OperationContext {
    private final ThreadLocal<String> value = new ThreadLocal<>();

    public String get() {
        return value.get();
    }

    public void set(String operation) {
        value.set(operation);
    }

    public void remove() {
        value.remove();
    }

    public String label() {
        String current = get();
        return current == null ? "(none)" : current;
    }
}
~~~

Đây là singleton bean do `@Bean` ở AppConfig tạo. Nó sở hữu ThreadLocal, không tự set hoặc cleanup ngoài các method đã công bố. Aspect sẽ quản lý scope, còn service có thể đọc context khi đang thực thi.

### 6.4. ScopeAspect.java

~~~java
package example.aop;

import example.context.OperationContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class ScopeAspect {

    private final OperationContext context;

    public ScopeAspect(OperationContext context) {
        this.context = context;
    }

    @Around("execution(public * example.work..*.*(..))")
    public Object withOperation(ProceedingJoinPoint pjp) throws Throwable {
        String operation = pjp.getSignature().getName();
        String previous = context.get();

        context.set(operation);
        try {
            System.out.println("aspect enter " + operation
                + " previous " + (previous == null ? "(none)" : previous));
            return pjp.proceed();
        } finally {
            if (previous == null) {
                context.remove();
            } else {
                context.set(previous);
            }
            System.out.println("aspect exit " + operation
                + " restored " + context.label());
        }
    }
}
~~~

Lưu ý:

- `@Around` nhận `ProceedingJoinPoint` và gọi `proceed()` đúng **một lần**.
- Advice trả nguyên result hoặc truyền tiếp exception (không catch/biến đổi).
- Pointcut chỉ áp dụng các **public method executions trong package example.work**; không intercept getter/setter của OperationContext hoặc method advice của ScopeAspect.
- `finally` restore/remove trước khi in log exit.
- Tên method dùng làm operation label để stdout minh họa đơn giản. Trong production, sử dụng key có phạm vi rõ ràng, tránh chứa user input hoặc PII.

### 6.5. SecondaryService.java

~~~java
package example.work;

import example.context.OperationContext;

public class SecondaryService {

    private final OperationContext context;

    public SecondaryService(OperationContext context) {
        this.context = context;
    }

    public String check() {
        System.out.println("secondary check sees " + context.label());
        return "CHECKED";
    }

    public void crash() {
        System.out.println("secondary crash sees " + context.label());
        throw new IllegalStateException("boom");
    }
}
~~~

Vì được inject qua Spring, SecondaryService là bean proxy khi advisor match. Caller khác cũng có thể gọi check/crash trực tiếp qua proxy; ở demo các lượt gọi đến từ PrimaryService.

### 6.6. PrimaryService.java

~~~java
package example.work;

import example.context.OperationContext;

public class PrimaryService {

    private final SecondaryService secondary;
    private final OperationContext context;

    public PrimaryService(SecondaryService secondary, OperationContext context) {
        this.secondary = secondary;
        this.context = context;
    }

    public String process() {
        System.out.println("primary process sees " + context.label());
        String result = secondary.check();
        System.out.println("primary resumed sees " + context.label());
        return "DONE:" + result;
    }

    public void fail() {
        System.out.println("primary fail sees " + context.label());
        secondary.crash();
    }

    public String recover() {
        System.out.println("primary recover sees " + context.label());
        try {
            secondary.crash();
        } catch (IllegalStateException ex) {
            System.out.println("primary caught " + ex.getMessage()
                + " sees " + context.label());
        }
        return "RECOVERED";
    }

    public String selfCall() {
        System.out.println("primary selfCall sees " + context.label());
        return this.local();
    }

    public String local() {
        System.out.println("primary local sees " + context.label());
        return "LOCAL";
    }
}
~~~

Sự khác nhau quan trọng:

- `secondary.check()` đi qua một Spring-managed bean khác (proxy) nên sinh **nested advice invocation**: state chuyển process → check → process.
- `this.local()` ở trong target **bỏ qua proxy** nên không có nested enter/exit cho local; nó vẫn đọc context selfCall của thread hiện tại.

### 6.7. AppConfig.java

~~~java
package example;

import example.aop.ScopeAspect;
import example.context.OperationContext;
import example.work.PrimaryService;
import example.work.SecondaryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public OperationContext operationContext() {
        return new OperationContext();
    }

    @Bean
    public SecondaryService secondaryService(OperationContext context) {
        return new SecondaryService(context);
    }

    @Bean
    public PrimaryService primaryService(
            SecondaryService secondary,
            OperationContext context) {
        return new PrimaryService(secondary, context);
    }

    @Bean
    public ScopeAspect scopeAspect(OperationContext context) {
        return new ScopeAspect(context);
    }
}
~~~

Spring AOP khám phá aspect bean được đăng ký qua `@Bean`. `@EnableAspectJAutoProxy(proxyTargetClass = true)` bật Spring AOP và sử dụng CGLIB class-based proxy khi cần, giúp lấy target bean qua concrete class. **CGLIB không tự intercept self-invocation**.

### 6.8. Main.java

~~~java
package example;

import example.context.OperationContext;
import example.work.PrimaryService;
import example.work.SecondaryService;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    private static void assertClean(OperationContext context) {
        if (context.get() != null) {
            throw new AssertionError("ThreadLocal leaked: " + context.get());
        }
    }

    public static void main(String[] args) {
        try (var spring =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {
            PrimaryService primary = spring.getBean(PrimaryService.class);
            SecondaryService secondary = spring.getBean(SecondaryService.class);
            OperationContext context = spring.getBean(OperationContext.class);

            System.out.println("primary proxy: " + AopUtils.isAopProxy(primary));
            System.out.println("secondary proxy: " + AopUtils.isAopProxy(secondary));

            System.out.println("-- normal nested --");
            System.out.println("caller: " + primary.process());
            assertClean(context);
            System.out.println("caller context: " + context.label());

            System.out.println("-- exceptional nested --");
            try {
                primary.fail();
            } catch (IllegalStateException ex) {
                System.out.println("caller caught: " + ex.getMessage());
            }
            assertClean(context);
            System.out.println("caller context: " + context.label());

            System.out.println("-- caught inside outer --");
            System.out.println("caller: " + primary.recover());
            assertClean(context);
            System.out.println("caller context: " + context.label());

            System.out.println("-- self invocation --");
            System.out.println("caller: " + primary.selfCall());
            assertClean(context);
            System.out.println("caller context: " + context.label());
        }
    }
}
~~~

Chạy trong thư mục project chứa `pom.xml`:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
~~~

### 6.9. Stdout dự kiến

~~~text
primary proxy: true
secondary proxy: true
-- normal nested --
aspect enter process previous (none)
primary process sees process
aspect enter check previous process
secondary check sees check
aspect exit check restored process
primary resumed sees process
aspect exit process restored (none)
caller: DONE:CHECKED
caller context: (none)
-- exceptional nested --
aspect enter fail previous (none)
primary fail sees fail
aspect enter crash previous fail
secondary crash sees crash
aspect exit crash restored fail
aspect exit fail restored (none)
caller caught: boom
caller context: (none)
-- caught inside outer --
aspect enter recover previous (none)
primary recover sees recover
aspect enter crash previous recover
secondary crash sees crash
aspect exit crash restored recover
primary caught boom sees recover
aspect exit recover restored (none)
caller: RECOVERED
caller context: (none)
-- self invocation --
aspect enter selfCall previous (none)
primary selfCall sees selfCall
primary local sees selfCall
aspect exit selfCall restored (none)
caller: LOCAL
caller context: (none)
~~~

Các log khác của Maven/JVM có thể xuất hiện tùy môi trường; bảng chỉ liệt kê những dòng `System.out.println` trong ví dụ. Nếu context bị leak trên caller thread, `assertClean()` ném AssertionError thay vì in tiếp các dòng kế tiếp.

### 6.10. Giải thích bốn đường gọi

**Normal:** outer set process; inner set check; inner finally restore process; outer đọc process; cuối cùng outer remove, caller thấy (none).

**Exception:** outer set fail; inner set crash rồi ném boom; inner finally restore fail; outer finally remove; caller mới catch boom. Cleanup không đồng nghĩa exception đã được xử lý bởi aspect.

**Recover:** inner crash ném exception; inner finally restore recover trước khi outer target catch. Outer target có context recover để log/khôi phục; outer finally remove.

**Self-call:** target selfCall gọi this.local() trực tiếp. Context vẫn là selfCall và không có cặp enter/exit riêng cho local. Outer finally remove khi kết thúc.

## 7. Bảng lỗi và cách sửa

| Cách viết | Normal return | Exceptional exit | Nested |
| --- | --- | --- | --- |
| Chỉ set rồi proceed | Có thể để lại giá trị cũ | Có thể để lại giá trị cũ | Có thể làm nhiễu context |
| Set, proceed, remove ở phía sau | Dọn khi thành công | **Bỏ qua cleanup** khi lỗi | Inner remove làm mất outer |
| Set, try/finally, luôn remove | Dọn được ở top-level | Dọn được khi lỗi | **Xóa state outer khi inner thoát** |
| **Capture previous, set, try/finally, restore/remove** | Bảo toàn state trước scope | Bảo toàn state khi lỗi | **Đúng với nested calls trên cùng thread** |

Không có giá trị trước scope thì dùng `remove()`; có giá trị trước scope thì restore nó. Quy tắc ownership quan trọng hơn việc đếm số lần gọi `remove()`.

## 8. Stack-based alternative

Nếu cần **danh sách các nested operations** chứ không chỉ current operation, có thể dùng một stack per thread:

~~~java
// Snippet minh họa; không phải một file của project runnable mục 6.
private final ThreadLocal<Deque<String>> stack =
    ThreadLocal.withInitial(ArrayDeque::new);

public void enter(String value) {
    stack.get().push(value);
}

public void exit() {
    Deque<String> values = stack.get();
    values.pop();
    if (values.isEmpty()) {
        stack.remove();
    }
}
~~~

Cần import `java.util.Deque` và `java.util.ArrayDeque`. `exit()` phải chạy trong finally của đúng scope sau khi enter thành công. Sản phẩm thực cần guard cho empty pop, mismatch nesting, double-close và thread switch; tốt hơn có thể cung cấp một `Scope implements AutoCloseable` được đóng đúng một lần.

**Không truyền một reference Deque mutable sang thread khác** chỉ vì nó được lưu trong ThreadLocal. Và khi stack rỗng, hãy remove toàn bộ ThreadLocal entry để không giữ đối tượng rỗng trên thread pool quá lâu.

## 9. @Async và ThreadPoolTaskExecutor không tự truyền ThreadLocal

Ví dụ luồng:

~~~text
HTTP thread T1:
  aspect set "submit"
    method tạo hoặc submit @Async task
    method trả Future/handle
  aspect finally restore/remove

Worker thread T2:
  task thực thi sau đó
  ThreadLocal T2 độc lập với ThreadLocal T1
~~~

Task trên T2 không tự kế thừa đúng value từ T1. Ngược lại, nếu T2 đã giữ state cũ do một task trước quên cleanup, nó có thể thấy **value sai**.

Spring cung cấp `org.springframework.core.task.TaskDecorator` để trang trí task trên executor như `ThreadPoolTaskExecutor`. Khi cần truyền một context tự quản lý, phải **capture ở phía submit** rồi **restore worker context trong finally**.

~~~java
// Snippet bổ sung, không thuộc project demo ở mục 6.
@Bean
public ThreadPoolTaskExecutor decoratedExecutor(OperationContext context) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);

    executor.setTaskDecorator(task -> {
        String submittedContext = context.get();
        return () -> {
            String workerPrevious = context.get();
            if (submittedContext == null) context.remove();
            else context.set(submittedContext);

            try {
                task.run();
            } finally {
                if (workerPrevious == null) context.remove();
                else context.set(workerPrevious);
            }
        };
    });
    return executor;
}
~~~

Đoạn snippet cần imports Spring cho `@Bean` và `ThreadPoolTaskExecutor`, cùng configuration/lifecycle executor phù hợp. Nó chỉ minh họa **một loại OperationContext**, không tự xử lý Spring Security context, transactions hay mọi MDC keys.

**Không dùng `InheritableThreadLocal` như cách sửa mặc định cho thread pool.** Nó kế thừa theo thread creation, không theo mỗi task submission, nên worker tái sử dụng có thể giữ thông tin lỗi thời.

## 10. Reactive Spring: ThreadLocal không phải subscription context

Một Spring service trả `Mono<T>` hoặc `Flux<T>` có thể kết thúc **method execution** trước khi pipeline được subscribe. `@Around` advice quanh method đó chạy finally khi method trả publisher, chứ không mặc nhiên chờ stream hoàn tất.

Trong Reactor, các operators có thể chạy qua nhiều threads. Một `ThreadLocal` được đặt khi tạo publisher không tự theo subscriber tới mọi signal. Khi cần dữ liệu theo stream, hãy sử dụng **Reactor Context** hoặc cơ chế context propagation/observation phù hợp với phiên bản Spring/Reactor đang dùng.

Cần kiểm tra riêng các trường hợp completion, failure, cancellation và thread hopping. Không suy “finally của Spring @Around đã chạy” nghĩa là “tác vụ reactive đã hoàn tất và mọi state được dọn ở worker khác”.

## 11. Spring logging MDC và quyền sở hữu key

SLF4J MDC thường dùng để gắn `requestId`, `traceId` hoặc `operation` vào log trên thread hiện tại. Nếu custom aspect chỉ sở hữu key `operation`, không được xóa toàn bộ MDC trong finally vì có thể làm mất keys do thành phần khác đặt.

Ví dụ **snippet bổ sung**, cần SLF4J API/logging implementation và imports; không thuộc project Maven demo:

~~~java
@Around("execution(* com.acme.service..*.*(..))")
public Object withLoggingKey(ProceedingJoinPoint pjp) throws Throwable {
    String previous = MDC.get("operation");
    MDC.put("operation", pjp.getSignature().getName());

    try {
        return pjp.proceed();
    } finally {
        if (previous == null) MDC.remove("operation");
        else MDC.put("operation", previous);
    }
}
~~~

Ưu tiên `MDC.remove("operation")` và restore previous, **không gọi `MDC.clear()` bừa bãi** khi aspect chỉ quản lý một key.

Không đưa password, bearer token, dữ liệu tenant nhạy cảm, customer ID hoặc raw request body vào MDC/log. MDC cũng không tự động di chuyển sang thread khác; cần context propagation có chọn lọc nếu dùng executor.

## 12. @Before + @After so với @Around

Hai advice tách rời:

~~~java
@Before("serviceOperations()")
public void before() {
    operation.set("current");
}

@After("serviceOperations()")
public void after() {
    operation.remove();
}
~~~

có nguy cơ lệch ownership khi có nhiều aspects với thứ tự khác nhau, nesting, pointcut lệch nhau hoặc interceptor ở ngoài ném lỗi trước khi after advice được vào. Nó cũng khó truyền một biến previous riêng cho **đúng invocation** nếu lưu biến đó ở field singleton.

Với một context được mở và đóng trong **cùng invocation**, một `@Around` method chứa `try/finally` giúp pairing rõ ràng hơn. Tất nhiên, chính around advice đó phải thật sự được Spring AOP gọi; nếu một advisor bên ngoài shortcut trước khi around được vào, không có state từ around ấy cần dọn.

## 13. @Order, transaction và phạm vi context thực sự

Nếu có Spring transaction/cache/security advisors, một method có thể đi qua nhiều layers:

~~~text
Caller -> ScopeAspect @Around
            -> TransactionInterceptor
               -> TargetService
            <- TransactionInterceptor hoàn tất
         <- ScopeAspect finally dọn context
~~~

Nếu ScopeAspect ở ngoài transaction interceptor, nó bao phủ cả phần xử lý transaction **phía trong**; nếu order đảo, một phần transaction work có thể xảy ra ngoài ScopeAspect. Order number không tự chứng minh context có mặt tại mọi layer. Cần kiểm tra actual advisor chain và Spring transaction configuration khi scope là yêu cầu chức năng.

Nếu một inner advisor shortcut không gọi proceed, finally của outer scope **vẫn cleanup sau khi inner trả về**. Nhưng nếu outer không proceed nên một inner scope chưa được vào, inner không set state và không có cleanup cần chạy.

## 14. Kiểm thử cleanup trên cùng worker thread

Một test chỉ kiểm:

~~~java
assertNull(context.get());
~~~

sau khi một job hoàn tất ở **test thread** không đủ để chứng minh worker thread đã dọn state. ThreadLocal thuộc về **thread chạy code**, nên phải đo trên đúng worker thread.

Ma trận kiểm thử nên có:

| Case | Bên trong lời gọi | Sau khi thoát lời gọi |
| --- | --- | --- |
| Return bình thường | Thấy operation đúng | Restore/remove đúng |
| Throwable thoát target | Thấy operation trước khi throw | Finally chạy, original error vẫn propagate |
| Nested service proxy | Inner thấy tên inner | Outer thấy lại tên outer |
| Inner throw, outer catch | Inner thấy tên inner | Outer catch thấy tên outer |
| Self-invocation | Inner Java method đọc context outer | Không có nested AOP scope riêng |
| Caller có context trước | Scope tạm thời override | Restore chính giá trị trước |
| Hai threads đồng thời | Value của mỗi thread riêng | Cleanup mỗi thread độc lập |
| Pool một worker, hai requests nối tiếp | Request 1 có value riêng | Request 2 không thấy state request 1 |
| Executor TaskDecorator | Worker thấy context capture được cho phép | Worker restore previous sau task |
| Reactive/async completion | Method boundary khác task completion | Đo lifecycle đúng abstraction |

Có thể dùng `Executors.newSingleThreadExecutor()` trong test để submit hai jobs liên tiếp và kiểm tra `context.get()` **ngay ở worker thread** sau mỗi job. Với nhiều luồng, một global mutable list sự kiện không có correlation ID không đủ để chứng minh thứ tự/ownership.

## 15. Khi nào không nên dùng ThreadLocal?

Không nên dùng ThreadLocal làm giải pháp mặc định nếu:

- Dữ liệu là business input cần truyền rõ qua service methods.
- Cần xác thực/ủy quyền/tenant isolation có security contract quan trọng.
- Dữ liệu phải tồn tại xuyên nhiều threads, futures, message queues hoặc processes.
- Hệ thống dùng Reactor/WebFlux và logical flow di chuyển theo subscriptions.
- Bạn không kiểm soát được boundary cleanup hoặc có nhiều nhóm cùng sửa một key.
- Dữ liệu lớn hoặc chứa secrets/PII và có nguy cơ bị giữ sống trên worker threads.
- Một thư viện như Micrometer Observation/context propagation đã cung cấp abstraction phù hợp, tránh tự viết propagation thiếu sót.

Thay vào đó có thể cân nhắc Spring request-scoped dependency, explicit parameter/context object, Reactor Context hoặc observability library phù hợp. **ThreadLocal là một công cụ theo thread, không phải một hợp đồng xuyên mọi tầng Spring.**

## 16. Các lỗi phổ biến

| Triệu chứng | Khả năng nguyên nhân | Cách sửa |
| --- | --- | --- |
| Request sau thấy state request trước | Không `remove()` ở thread pool thread | Dọn trong finally, test thread reuse |
| Outer mất context sau inner call | Inner remove vô điều kiện | Capture previous và restore |
| Context vẫn còn sau target throw | Cleanup đặt sau proceed, không trong finally | Dùng try/finally |
| Hai request ghi đè cùng field aspect | Shared mutable singleton field | Dùng local state / scoped context / ThreadLocal khi hợp lệ |
| @Async không thấy giá trị | ThreadLocal không tự đi sang worker | TaskDecorator hoặc context propagation |
| Worker thấy giá trị lạ | Worker cũ không cleanup | Restore/remove phía worker |
| MDC traceId bị mất | Custom aspect gọi `MDC.clear()` | Remove/restore đúng key sở hữu |
| WebFlux result hoàn tất sau cleanup | @Around dọn khi method trả Publisher | Reactor Context và reactive lifecycle |
| Self-call không tạo scope inner | `this.method()` bypass proxy | Gọi qua Spring bean khác khi cần advice |
| Bean là proxy nhưng method không vào advice | Pointcut không match method đó | Kiểm tra eligible advisor |
| Trạng thái sau method không null | Trước scope đã có context hợp lệ | Restore previous là đúng; không xóa bừa |
| Memory retention tăng | Thread pool giữ value/object lớn | Giảm lifetime và remove |
| Finally log làm che exception target | Instrumentation tự ném exception | Thiết kế failure policy cho logging/metrics |
| Scoped stack báo empty pop | Push/pop không cân xứng | Scope ownership và close đúng một lần |
| Dùng InheritableThreadLocal để truyền qua pool | Kế thừa theo thread creation, không task submission | Explicit propagation có cleanup |

## 17. Bài tập kiểm tra

**Câu 1.** Vì sao singleton `@Aspect` có thể dùng một field `ThreadLocal<String>` mà không khiến tất cả threads cùng thấy một String?

**Đáp án:** ThreadLocal giữ entry riêng theo thread hiện tại. Nhưng nó không tự dọn khi method/request kết thúc, nên phải quản lý lifetime.

**Câu 2.** Inner advice gọi remove trong finally làm outer thấy null. Vì sao?

**Đáp án:** Outer và inner dùng chung ThreadLocal entry trên cùng thread; inner đã xóa context outer. Cần restore previous khi inner thoát.

**Câu 3.** Một target method ném IllegalStateException. Cleanup đặt ngay sau proceed có chạy không?

**Đáp án:** Không ở exceptional exit. Đưa cleanup vào finally; exception vẫn propagate nếu advice không nuốt lỗi.

**Câu 4.** `this.local()` trong target có tạo nested AOP scope không?

**Đáp án:** Không mặc nhiên. Self-invocation bypass Spring AOP proxy. Method local vẫn có thể đọc context outer trên cùng thread.

**Câu 5.** Thread T1 submit một task chạy trên T2; T2 có tự đọc ThreadLocal của T1 không?

**Đáp án:** Không. Cần propagation có chọn lọc tại task boundary và restore/remove ở T2.

**Câu 6.** Aspect quản lý MDC key operation nhưng gọi MDC.clear() trong finally. Sai ở đâu?

**Đáp án:** Nó có thể xóa các keys requestId/traceId do component khác sở hữu. Restore/remove đúng key.

**Câu 7.** Một method trả Mono đã xong, around advice finally đã chạy, nhưng publisher chưa có subscriber. Có mâu thuẫn không?

**Đáp án:** Không. Spring AOP method execution kết thúc khi trả publisher object, khác vòng đời reactive subscription.

**Câu 8.** Khi caller đã có context `"request"` trước method, after advice nên luôn remove?

**Đáp án:** Không. Sau invocation phải restore `"request"` theo ownership. Remove chỉ nếu trước đó trống.

**Câu 9.** Có thể chỉ kiểm tra ThreadLocal trên main thread để chứng minh worker pool không leak không?

**Đáp án:** Không. Phải kiểm tra trên worker thread được tái sử dụng.

**Câu 10.** Vì sao không nên gắn tenant/authorization policy chỉ vào một ThreadLocal chưa được validate?

**Đáp án:** Nó không tự xác thực provenance, có nguy cơ stale/missing data và không tự truyền xuyên async boundaries. Security/tenant state phải được xác thực và quản lý bằng cơ chế phù hợp.

## 18. Tài liệu chính thức

- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): Spring proxy boundary, JDK/CGLIB và self-invocation.
- [Spring Framework — Declaring @Around Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): `ProceedingJoinPoint.proceed()`, try/finally và giới hạn state per invocation.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): aspect phải là Spring bean, bật `@EnableAspectJAutoProxy`.
- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): TaskDecorator, ThreadPoolTaskExecutor và async execution.
- [Oracle Java 17 — ThreadLocal API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html): get/set/remove và giá trị riêng theo thread.
- [Spring Framework — Observability](https://docs.spring.io/spring-framework/reference/observability.html): các boundary của observation/context propagation.
- [Reactor — Context](https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html): context theo subscription, không theo một thread cố định.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-state-concurrency-007">Chuyển luồng, @Async và giới hạn ngữ cảnh gắn với luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển luồng, `@Async` và giới hạn ngữ cảnh gắn với luồng trong Spring AOP

## 1. Vì sao Spring Aspect phải quan tâm đến chuyển luồng?

Spring AOP thường chặn **lời gọi phương thức đi qua Spring proxy**. `@Async` cũng dùng cơ chế proxy trong chế độ mặc định: khi caller gọi một phương thức đủ điều kiện, bộ chặn bất đồng bộ gửi công việc vào `TaskExecutor`, còn caller nhận lại ngay một `Future` / `CompletableFuture` hoặc kết thúc lần gọi `void`. Phần thân phương thức được thực thi sau đó trong worker.

```text
CALLER THREAD                              EXECUTOR / WORKER THREAD
    |                                                |
    |  otherBean.work()                               |
    v                                                |
 Spring AOP proxy                                     |
    |                                                |
 Async advisor --------- submit(task) --------------->|
    |                                                | AOP @Around (target)
    | future returned                                |   -> @Before / target
    |                                                |   -> @AfterReturning
    |                                                | completes future
    +---------------- await/result <-----------------+
```

Trong cấu hình Spring phổ biến, `AsyncAnnotationBeanPostProcessor` đặt advisor bất đồng bộ **trước các advisor hiện có** để chuyển luồng càng sớm càng tốt. Do đó một `@Around` áp dụng **trên chính phương thức `@Async`** thường chạy ở worker; một `@Around` trên **phương thức caller** chạy ở caller. Nếu ứng dụng dùng nhiều proxy, thay đổi thứ tự advisor hoặc `@EnableAsync(mode = AdviceMode.ASPECTJ)`, cần quan sát đường đi thực tế. `@Order` giữa các `@Aspect` không thay thế việc xác định vị trí async advisor.

**Phân biệt:** (a) thời điểm proxy gửi tác vụ; (b) thời điểm thân phương thức chạy; (c) thời điểm `Future` hoàn tất. Ba thời điểm này có thể thuộc các luồng khác nhau.

## 2. Điều kiện kích hoạt `@Async` và advice

1. Bật `@EnableAsync`, cung cấp executor thích hợp, có thể chọn rõ `@Async("workerExecutor")`. `@EnableAspectJAutoProxy` cần cho aspect `@Aspect` của ứng dụng; chỉ khai báo aspect không tự bật xử lý `@Async`.
2. Bean phải được Spring quản lý và lời gọi phải **đi qua proxy**. `this.work()` trong cùng bean là self-invocation, bỏ qua async advisor và các advice proxy của lời gọi ấy. Tách phương thức async sang bean khác là cách đơn giản để bảo đảm có ranh giới proxy.
3. Tránh áp dụng proxy advice cho `private` hoặc `final` method; proxy CGLIB không ghi đè được phương thức `final`, còn JDK proxy chỉ phơi bày API qua interface. `@Async` không được hỗ trợ trực tiếp trên callback vòng đời như `@PostConstruct`.
4. Với `@Async`, return phù hợp gồm `void`, `Future<T>`, `CompletableFuture<T>`. Phương thức trả giá trị bất đồng bộ có thể trả `CompletableFuture.completedFuture(value)` trong worker; proxy cung cấp future để caller theo dõi thực thi.
5. Không nhầm `@Async` với một aspect tự khai báo: Spring dùng `AsyncAnnotationAdvisor` / interceptor để thực thi hành vi bất đồng bộ. Trong các phương thức AOP, `ProceedingJoinPoint.proceed()` thực thi bước kế tiếp trong chuỗi chặn hiện hành.

### Vị trí advice quyết định ý nghĩa của phép đo

| Điểm chặn | Luồng thường chạy | Điều thực sự đo |
| --- | --- | --- |
| `@Around` của service **gọi** phương thức `@Async` | Caller | Thời gian gửi task / xử lý của caller |
| `@Around` của phương thức **đích** `@Async` | Worker (async advisor ngoài cùng) | Thời gian chạy phương thức đích |
| `@AfterReturning` trên caller nhận `CompletableFuture` | Caller | Đã **nhận future**, chưa chắc task hoàn tất |
| `future.whenComplete(...)` | Luồng hoàn tất future hoặc luồng gắn callback nếu future đã xong | Kết quả cuối cùng của stage được theo dõi |

Nếu cần đo end-to-end, lấy mốc trước khi gửi và kết thúc tại `whenComplete`; đừng kết luận “xong nghiệp vụ” chỉ vì `@AfterReturning` đã chạy.

## 3. Ví dụ Spring AOP chạy được trên Java 17+

Ví dụ này cố ý lưu mã yêu cầu vào `ThreadLocal` ở caller. Nó chứng minh aspect chạy tại worker nhưng **mã yêu cầu không tự đi theo**. Dùng Maven và Spring Framework 6.2.x; không cần Spring Boot.

Cấu trúc:

```text
async-aop-demo/
├── pom.xml
└── src/main/java/example/AsyncAspectDemo.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>async-aop-demo</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-context</artifactId>
      <version>6.2.8</version>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-aop</artifactId>
      <version>6.2.8</version>
    </dependency>
    <dependency>
      <groupId>org.aspectj</groupId>
      <artifactId>aspectjweaver</artifactId>
      <version>1.9.22</version>
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

`src/main/java/example/AsyncAspectDemo.java`:

```java
package example;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class AsyncAspectDemo {
    static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    @Configuration(proxyBeanMethods = false)
    @EnableAsync(proxyTargetClass = true)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class Config {
        @Bean
        ThreadPoolTaskExecutor workerExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(5);
            executor.setThreadNamePrefix("worker-");
            return executor;
        }

        @Bean WorkService workService() {
            return new WorkService();
        }

        @Bean TraceAspect traceAspect() {
            return new TraceAspect();
        }
    }

    static class WorkService {
        @Async("workerExecutor")
        public CompletableFuture<String> work() {
            System.out.println("target: " + Thread.currentThread().getName()
                    + ", request=" + REQUEST_ID.get());
            return CompletableFuture.completedFuture("seen:" + REQUEST_ID.get());
        }
    }

    @Aspect
    static class TraceAspect {
        @Around("execution(* example..*.work(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("advice before: "
                    + Thread.currentThread().getName()
                    + ", request=" + REQUEST_ID.get());
            Object result = pjp.proceed();
            System.out.println("advice after: "
                    + Thread.currentThread().getName()
                    + ", request=" + REQUEST_ID.get());
            return result;
        }
    }

    public static void main(String[] args) throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            REQUEST_ID.set("REQ-42");
            try {
                System.out.println("caller request=" + REQUEST_ID.get());
                CompletableFuture<String> future =
                        context.getBean(WorkService.class).work();
                System.out.println("result=" + future.get(5, TimeUnit.SECONDS));
            } finally {
                REQUEST_ID.remove();
            }
        }
    }
}
```

Chạy tại thư mục `async-aop-demo`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.AsyncAspectDemo
```

`stdout` với executor một worker tên `worker-1`, advice ở worker và chưa có cơ chế truyền `ThreadLocal`:

```text
caller request=REQ-42
advice before: worker-1, request=null
target: worker-1, request=null
advice after: worker-1, request=null
result=seen:null
```

`future.get(5, TimeUnit.SECONDS)` đợi đến khi task hoàn tất nên thứ tự hiển thị ở ví dụ được xác định; không cần `sleep`. `ThreadLocal` có ngăn giá trị riêng trên từng luồng, worker không tự nhận `REQ-42`. Điều này **không phải** lỗi của pointcut `@Around`. Nếu dùng cấu hình proxy/executor khác, có thể có thêm log hoặc tên worker khác: hãy quan sát tên luồng thực tế.

## 4. Phân loại ngữ cảnh tại ranh giới executor

| Ngữ cảnh | Cơ chế phổ biến | Trên worker mới hoặc luồng pool |
| --- | --- | --- |
| `ThreadLocal` tùy chỉnh, mã tương quan, SLF4J MDC | Giá trị gắn với luồng | Không tự có; cần chuyển giao và dọn |
| `RequestContextHolder` (Spring MVC) | `RequestAttributes` theo luồng | Không mặc định có; request có thể kết thúc trước task |
| `LocaleContextHolder` | `Locale` theo luồng | Không mặc định có; có thể truyền `Locale` đã chụp |
| `SecurityContextHolder` của Spring Security | `SecurityContext` theo luồng (mặc định) | Không tự có; cần cơ chế truyền bảo mật riêng |
| Giao dịch `@Transactional` imperative | `TransactionSynchronizationManager` theo luồng | **Transaction caller không đi theo worker** |
| Micrometer observation / trace | Context snapshot và các accessor | Chỉ truyền khi được cấu hình hỗ trợ |
| `Reactor Context` trong WebFlux | Context của luồng dữ liệu reactive | Không đồng nhất với `ThreadLocal`; cần cầu nối nếu trộn mô hình |

Ngữ cảnh chưa hẳn nên sao chép toàn bộ. Với task chạy nền lâu hơn HTTP request, tham số như mã tương quan, `Locale` hoặc định danh nghiệp vụ đã kiểm tra thường bền vững và minh bạch hơn giữ nguyên `RequestAttributes`. Tránh chuyển tham chiếu request/session, tài nguyên giao dịch hoặc thông tin xác thực rộng không cần thiết.

### Trường hợp `@Transactional` và `@Async`

Với `PlatformTransactionManager`, Spring ràng buộc transaction imperative vào **luồng hiện tại**. Khi caller đang trong `@Transactional` gọi một bean khác có `@Async`, worker **không tham gia cùng transaction**; rollback phía caller không đồng nghĩa rollback phía worker. Nếu worker cần giao dịch, cho một phương thức chạy trong worker đi qua Spring transactional proxy để mở transaction **riêng**. Không sao chép các ThreadLocal nội bộ của `TransactionSynchronizationManager` sang worker bằng decorator.

### Trường hợp `RequestContextHolder` và `InheritableThreadLocal`

`RequestContextHolder` có cơ chế kế thừa tùy chọn, nhưng `InheritableThreadLocal` chỉ sao chép giá trị khi **tạo luồng con**, không chuyển giao đáng tin cậy cho thread pool **dùng lại** luồng. Nó còn dễ lộ ngữ cảnh cũ sang task khác. Trích riêng dữ liệu bất biến còn cần trước khi rời request; không bật chế độ kế thừa để “chữa” lỗi mất context trong pool.

## 5. Ba phương án xử lý đúng phạm vi Spring Aspect

### Phương án A: Truyền tham số rõ ràng

```java
@Async("workerExecutor")
public CompletableFuture<String> work(String requestId) {
    return CompletableFuture.completedFuture("seen:" + requestId);
}

// Caller trên một bean khác: service.work("REQ-42");
```

Đây là đoạn thay thế minh họa trong Spring bean ở trên, **không** phải chương trình độc lập. Tham số là dữ liệu bất biến, không phụ thuộc scheduler hay ThreadLocal. Phù hợp khi code nghiệp vụ có thể sửa chữ ký.

### Phương án B: `TaskDecorator` chụp/khôi phục context cho AOP

Nếu advice của bạn hoặc thư viện logging **phải đọc ThreadLocal**, gắn decorator lên **chính executor mà `@Async` dùng**. Trong `Config` của ví dụ, thay bean `workerExecutor()` bằng:

```java
@Bean
ThreadPoolTaskExecutor workerExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(5);
    executor.setThreadNamePrefix("worker-");

    executor.setTaskDecorator(task -> {
        // Chụp ở lúc executor nhận tác vụ.
        String captured = REQUEST_ID.get();
        return () -> {
            // Worker có thể đang dùng lại: nhớ trạng thái cũ.
            String previous = REQUEST_ID.get();
            try {
                if (captured == null) REQUEST_ID.remove();
                else REQUEST_ID.set(captured);
                task.run();
            } finally {
                if (previous == null) REQUEST_ID.remove();
                else REQUEST_ID.set(previous);
            }
        };
    });
    return executor;
}
```

Khi chạy lại, advice và target nhìn thấy `request=REQ-42`, future cho `result=seen:REQ-42`. Quy tắc bắt buộc: **capture khi gửi task → restore ở worker → phục hồi trạng thái worker trong `finally`**. Không truyền trạng thái của task A sang task B khi pool dùng lại luồng. Với MDC, chụp `MDC.getCopyOfContextMap()`, áp dụng bằng `MDC.setContextMap(...)` hoặc `MDC.clear()`, và phục hồi MDC cũ trong `finally`. Nếu dùng nhiều decorator, phối hợp có chủ đích (ví dụ `CompositeTaskDecorator`), tránh ghi đè lẫn nhau.

### Phương án C: Dùng hỗ trợ tích hợp đúng loại context

- **Micrometer / Spring Framework 6.1+:** `org.springframework.core.task.support.ContextPropagatingTaskDecorator` có thể chuyển observation/MDC/context đã được đăng ký `ThreadLocalAccessor`. Ví dụ trên executor có đủ dependency: `executor.setTaskDecorator(new ContextPropagatingTaskDecorator());`. Nó **không tự hiểu mọi `ThreadLocal` tùy ý**. Spring Boot có tùy chọn tích hợp context propagation theo phiên bản; khi tự dựng executor phải xác minh decorator thực sự được cài.
- **Spring Security:** `org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor` bọc `AsyncTaskExecutor` và khôi phục `SecurityContext` trong thời gian thực thi task. Ví dụ minh họa: `new DelegatingSecurityContextAsyncTaskExecutor(rawExecutor)`. Đặt wrapper làm executor thực tế của `@Async` (ví dụ bean `securedExecutor` và `@Async("securedExecutor")`); tạo wrapper nhưng vẫn dispatch qua `rawExecutor` thì không có tác dụng.

Hai cơ chế trên phục vụ phạm vi context được hỗ trợ, **không truyền transaction của caller** và cũng không đảm bảo mọi ngữ cảnh tùy biến được sao chép. Hãy kiểm thử số lượng task nhiều và chi phí capture/restore.

## 6. Ngoại lệ, callback, luồng nối tiếp

**Khi có `CompletableFuture`:** lỗi worker xuất hiện ở future; `get()` ném `ExecutionException` bao nguyên nhân và `join()` thường ném `CompletionException`. Khi `@Async` trả `void`, caller thường không thể dùng `try/catch` quanh lần gọi để bắt lỗi worker; dùng `AsyncUncaughtExceptionHandler` để xử lý/log. Lỗi submit do executor từ chối có thể xuất hiện ngay khi gọi proxy, cần xử lý riêng.

**Khi thêm stage:** `thenApply` có thể chạy ở luồng hoàn thành future hoặc luồng gắn callback nếu future đã xong; `thenApplyAsync` sử dụng executor riêng hoặc executor mặc định. Vì thế decorator của lần gọi `@Async` **không cam kết** mọi stage sau đều được truyền context. Truyền giá trị qua tham số, hoặc chỉ định executor được trang bị decorator tại từng ranh giới thật sự cần.

**Khi dùng virtual threads:** việc sử dụng luồng ảo không làm `ThreadLocal` của caller tự động xuất hiện trong task được dispatch. Vẫn cần hiểu proxy và đường truyền ngữ cảnh. Đừng áp dụng phương án `InheritableThreadLocal` như một quy tắc chung cho mọi kiểu executor.

## 7. Những lỗi cần nhận diện

| Hiện tượng | Nguyên nhân thường gặp | Kiểm tra / khắc phục |
| --- | --- | --- |
| `@Async` chạy trên caller | `this.work()`, thiếu `@EnableAsync`, bean tạo bằng `new` | Gọi qua bean proxy, log thread name |
| Aspect thấy `ThreadLocal=null` | Ngữ cảnh ở caller chưa được truyền | Dùng tham số hoặc `TaskDecorator` |
| `@AfterReturning` chạy trước khi kết quả hoàn tất | Chỉ quan sát thời điểm trả future | Đo bằng `whenComplete` |
| Task B mang trace/user của task A | Quên phục hồi context khi pool dùng lại | Luôn `finally`, kiểm thử 2 task liên tiếp |
| Request/locale vắng hoặc không hợp lệ | Context gắn vòng đời request/luồng | Trích dữ liệu cần trước khi submit |
| Transaction worker không rollback cùng caller | Transaction imperative gắn luồng | Thiết kế transaction độc lập và xử lý đồng bộ nghiệp vụ |
| Worker ném lỗi nhưng caller không thấy | `@Async void` không có future | `CompletableFuture` hoặc `AsyncUncaughtExceptionHandler` |
| `@Order` không giải thích được vị trí luồng | Async advisor/proxy khác thứ tự bạn tưởng | Kiểm tra advisor thật, log caller/advice/target |

Bài kiểm thử tốt: executor **một worker**, đặt `REQUEST_ID` ở caller rồi submit task A, xóa ở caller và submit task B; khi có decorator, task A nhận mã A, task B không được nhận mã A. Thêm tình huống task A ném ngoại lệ để kiểm tra `finally` không rò rỉ. Dùng `CompletableFuture` hoặc `CountDownLatch` đồng bộ thay cho đoán thời gian bằng `sleep`.

## 8. Ghi nhớ

`@Async` tạo một **ranh giới thực thi** khi đi qua Spring proxy; vị trí advice quyết định nó chạy trên caller hay worker. ThreadLocal, MDC, request context, security context và transaction có cơ chế sống khác nhau; không mặc định chuyển sang thread pool. Ưu tiên tham số bất biến; khi cần truyền ngữ cảnh cho Spring Aspect hoặc thư viện, dùng executor/decorator chuyên biệt, phục hồi trạng thái trong `finally` và kiểm thử khi luồng được dùng lại. Đo thời điểm hoàn tất future thay vì nhầm với thời điểm phương thức đã trả future.

### Tài liệu Spring để đối chiếu

- `@Async`, executor và xử lý lỗi: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring AOP proxy và self-invocation: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Thứ tự advice: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Transaction imperative gắn với luồng: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html
- Async advisor trước existing advisors: https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/scheduling/annotation/AsyncAnnotationBeanPostProcessor.html
- `ContextPropagatingTaskDecorator`: https://docs.spring.io/spring-framework/docs/6.1.2/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html
- Spring Security context và concurrency: https://docs.spring.io/spring-security/reference/features/integrations/concurrency.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-state-concurrency-008">Luồng reactive và thời điểm phương thức trả đối tượng Publisher</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP với phương thức trả Publisher: thời điểm thực thi và trạng thái reactive

## Mục tiêu

Khi một Spring bean có phương thức trả Reactor Mono/Flux hoặc Reactive Streams Publisher, cần phân biệt **lần gọi phương thức đi qua Spring AOP proxy** và **lần subscribe vào Publisher mà phương thức trả về**. Hai thời điểm có thể cách nhau lâu, xảy ra trên thread khác nhau hoặc không bao giờ có subscribe. Vì thế advice đo thời gian, log lỗi hay quản lý trạng thái bằng cách chỉ đặt lệnh trước/sau ProceedingJoinPoint.proceed() thường không quan sát được toàn bộ công việc bất đồng bộ.

Sau bài này, có thể xác định advice nào chạy lúc trả Publisher, đặt xử lý vào đúng vòng đời subscription, lưu trạng thái an toàn khi nhiều subscriber đồng thời và kiểm thử kết quả khi hoàn tất, lỗi hoặc hủy.

## 1. Ranh giới Spring AOP proxy và reactive pipeline

Spring AOP ở chế độ proxy chặn **method execution** được gọi qua Spring-managed bean proxy. Phương thức trả Mono/Flux vẫn là một lần thực thi phương thức Java bình thường: lời gọi đi qua proxy, vào advice, tới thân phương thức, rồi trả một đối tượng Publisher.

Ví dụ:

~~~java
@Service
class BookService {
    public Mono<String> findTitle() {
        System.out.println("method body: build Mono");
        return Mono.fromSupplier(() -> {
            System.out.println("deferred work");
            return "Spring AOP";
        });
    }
}
~~~

Giả sử client nhận bean qua Spring proxy và gọi:

~~~java
Mono<String> result = bookService.findTitle(); // proxy/advice chạy tại đây
// ... thời gian trôi qua, chưa subscribe ...
String title = result.block();                  // subscribe tại đây
~~~

- Khi gọi findTitle(): thân phương thức chạy và tạo Mono; mọi logic advice thông thường bao quanh lần gọi cũng chạy. Dòng "method body: build Mono" được in ngay.
- Khi block() hoặc subscribe(): Mono.fromSupplier mới chạy supplier và in "deferred work". Spring AOP **không tự chặn một lần thứ hai** tại thời điểm này.
- Nếu không ai subscribe thì phần deferred work chưa chạy. Nhưng các hiệu ứng phụ *eager* đặt trực tiếp trong thân phương thức/advice đã xảy ra.
- Nếu hai subscriber đăng ký vào một cold Publisher, phần công việc upstream có thể chạy hai lần; **advice ở ranh giới phương thức** vẫn chỉ chạy một lần nếu chỉ gọi phương thức một lần.
- Nếu phương thức trả hot/eager Publisher, hoặc dùng cache/share, công việc và hành vi chia sẻ có thể khác. Không suy đoán mọi Publisher đều lazy, cold hay phát lại cho mỗi người subscribe.

Mô hình thời gian điển hình:

~~~text
T0  caller -> Spring proxy -> @Around.enter
T1  @Around -> proceed() -> target tạo Publisher -> trả Publisher
T2  @Around.exit -> caller nhận Publisher
    ... chưa có subscription; có thể đổi thread ...
T3  subscriber đăng ký vào Publisher
T4  upstream xử lý và phát onNext (0..n), onError hoặc onComplete
T5  subscription có thể kết thúc bằng cancel trước tín hiệu cuối
~~~

T0–T2 đo **thời gian gọi method / lắp pipeline (assembly)**. T3–T5 là **vòng đời subscription / thực thi luồng**. Với thao tác bất đồng bộ, T2 không đồng nghĩa truy vấn đã hoàn tất, kết nối đã giải phóng hay phản hồi đã gửi.

## 2. Spring advice nào nhìn thấy điều gì?

| Thành phần Spring AOP | Khi nào chạy đối với phương thức trả Publisher? | Không nên hiểu nhầm |
| --- | --- | --- |
| @Before | Trước lời gọi phương thức đích, khi đi qua proxy | Không phải callback trước mỗi subscription |
| @AfterReturning | Khi phương thức trả bình thường; giá trị trả về thường là đối tượng Mono/Flux/Publisher | Không phải dữ liệu onNext hay sự kiện onComplete |
| @AfterThrowing | Khi bản thân lần thực thi phương thức qua proxy ném lỗi đồng bộ | Không tự nhận onError phát ra muộn từ Publisher |
| @After | Khi lần thực thi phương thức qua proxy thoát, kể cả ném lỗi | Không đợi pipeline hoàn thành/cancel |
| @Around | Bao quanh proceed() và có thể thay Publisher trả về bằng Publisher đã gắn operator | Chỉ phần mã chạy trong advice là đồng bộ; operator bên trong Publisher có vòng đời riêng |

Ví dụ dưới đây chỉ log thời gian gọi phương thức (có ích để đo chi phí xây pipeline, **không phải** độ trễ reactive):

~~~java
@Around("execution(reactor.core.publisher.Mono demo.BookService.*(..))")
public Object assemblyTime(ProceedingJoinPoint pjp) throws Throwable {
    long start = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        long nanos = System.nanoTime() - start;
        System.out.println("method/assembly ns = " + nanos);
    }
}
~~~

Nếu một Mono đã được trả thành công nhưng sau đó phát onError, @AfterReturning cho lần gọi phương thức vẫn có thể đã chạy. Hai kết quả không mâu thuẫn: một là *return bình thường của Java method*, một là *terminal signal của reactive sequence*. Tương tự, catch (Throwable) quanh proceed() chỉ bắt lỗi xảy ra ngay khi gọi method, không bắt lỗi do supplier, map, flatMap hoặc I/O phát ra lúc subscribe.

## 3. Cách viết @Around để quan sát subscription

Khi mục tiêu là đếm subscription, đo thời gian xử lý thực tế và ghi nhận trạng thái terminal, advice cần **trả lại một Publisher được trang trí bằng Reactor operators**.

Một pattern cho phương thức khai báo trả Mono:

~~~java
@Around("execution(reactor.core.publisher.Mono demo.BookService.*(..))")
public Object observeMono(ProceedingJoinPoint pjp) throws Throwable {
    Object returned = pjp.proceed(); // gọi target tại thời điểm method invocation
    if (!(returned instanceof Mono<?> source)) {
        return returned; // hợp đồng return type cần được giữ nguyên
    }

    String operation = pjp.getSignature().toShortString();

    return Mono.defer(() -> {
        long start = System.nanoTime(); // mỗi subscription có state riêng
        System.out.println("subscribed: " + operation);

        return source
            .doOnNext(value -> System.out.println("onNext: " + operation))
            .doOnError(error -> System.out.println(
                "onError: " + operation + ", " + error.getClass().getSimpleName()))
            .doFinally(signal -> {
                long elapsedNs = System.nanoTime() - start;
                System.out.println("terminal: " + operation + ", "
                    + signal + ", elapsedNs=" + elapsedNs);
            });
    });
}
~~~

Điểm cần đọc chính xác:

1. proceed() vẫn được gọi khi Spring proxy nhận lời gọi, vì vậy thân phương thức không bị hoãn sang một thời điểm khác ngoài ý muốn.
2. Mono.defer tạo mới start cho **mỗi lần subscribe**. Không để start trong field mutable của singleton @Aspect hay biến dùng chung giữa các subscriber.
3. doOnNext quan sát phần tử; một Mono rỗng có thể hoàn thành mà không phát onNext. doOnSuccess (nếu cần) có quy ước riêng cho Mono rỗng: callback có thể nhận null.
4. doOnError quan sát tín hiệu lỗi từ upstream. Khi upstream phát onError, doFinally nhận ON_ERROR; thành công nhận ON_COMPLETE; hủy nhận CANCEL.
5. doFinally hữu ích cho metrics và cleanup vì có cả cancellation. Callback doFinally là side effect, không nên dựa vào nó để sửa giá trị Publisher hoặc bảo đảm một thứ tự log tuyệt đối với thread đang chờ block().
6. Advice phải trả về kiểu tương thích với chữ ký phương thức. Không trả Flux cho phương thức khai báo Mono; không ép mọi Publisher về Mono (sẽ làm mất ngữ nghĩa nhiều phần tử).

Trường hợp đặc biệt: nếu proceed() ném Throwable **ngay tại thời điểm gọi phương thức**, khối advice ở trên chưa tạo được wrapper; đó là lỗi đồng bộ, vẫn nên cho Spring xử lý theo đường lỗi đồng bộ hoặc quan sát riêng bằng try/catch quanh proceed(). Không âm thầm biến mọi lỗi đồng bộ thành onError nếu không chủ đích thay đổi hợp đồng API.

### Biến thể với Flux

Với phương thức khai báo Flux, sử dụng Flux.defer và các operator cùng ý nghĩa:

~~~java
@Around("execution(reactor.core.publisher.Flux demo.StreamService.*(..))")
public Object observeFlux(ProceedingJoinPoint pjp) throws Throwable {
    Flux<?> source = (Flux<?>) pjp.proceed();
    String operation = pjp.getSignature().toShortString();

    return Flux.defer(() -> {
        long start = System.nanoTime();
        return source
            .doOnSubscribe(s -> System.out.println("subscribe " + operation))
            .doOnError(e -> System.out.println("error " + operation))
            .doFinally(signal -> System.out.println(
                operation + ": " + signal + " after "
                + (System.nanoTime() - start) + " ns"));
    });
}
~~~

Với chữ ký trả org.reactivestreams.Publisher<T>, có thể dùng Flux.from(source) **khi tương thích với hợp đồng**. Cần xét riêng đối tượng trả về Mono, Flux, publisher tùy biến, empty/null (trong API reactive thường cần tránh null), khả năng nhiều phần tử, backpressure và yêu cầu giữ identity/behavior; đừng áp một phép ép kiểu Mono/Flux không an toàn cho mọi endpoint.

## 4. Ví dụ chạy được: Spring proxy + Mono trả về trước khi chạy công việc

Ví dụ độc lập sau dùng Java 17, Spring Framework 6.1, Reactor Core và Spring AOP kiểu @AspectJ (proxy của Spring, không cần AspectJ weaving). Cấu trúc:

~~~text
reactive-aop-demo/
├── pom.xml
└── src/main/java/demo/App.java
~~~

**pom.xml**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>reactive-aop-demo</artifactId>
    <version>1.0.0</version>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.15</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.1.15</version>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.22.1</version>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
            <version>3.6.12</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration><release>17</release></configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration><mainClass>demo.App</mainClass></configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/demo/App.java**

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Configuration
@ComponentScan("demo")
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class App {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            GreetingService service = context.getBean(GreetingService.class);

            System.out.println("A. call through proxy");
            Mono<String> mono = service.greet("Ada");
            System.out.println("B. method returned");
            String value = mono.block();
            System.out.println("C. value = " + value);
        }
    }
}

@Component
class GreetingService {
    public Mono<String> greet(String name) {
        System.out.println("target: build Mono");
        return Mono.fromSupplier(() -> {
            System.out.println("source: compute");
            return "Hello " + name;
        });
    }
}

@Aspect
@Component
class ReactiveObservationAspect {
    @Around("execution(reactor.core.publisher.Mono demo.GreetingService.greet(..))")
    public Object observe(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("advice: enter");
        Mono<?> source = (Mono<?>) pjp.proceed();
        System.out.println("advice: wrap Publisher");

        return Mono.defer(() -> {
            System.out.println("pipeline: subscribe");
            return source
                .doOnNext(value -> System.out.println("pipeline: onNext"))
                .doOnError(error -> System.out.println("pipeline: onError"))
                .doFinally(signal ->
                    System.out.println("pipeline: finally " + signal));
        });
    }
}
~~~

Tại thư mục dự án, chạy:

~~~bash
mvn -q compile exec:java
~~~

Phần thứ tự có ý nghĩa ở output (bỏ qua mọi log khởi động của thư viện) là:

~~~text
A. call through proxy
advice: enter
target: build Mono
advice: wrap Publisher
B. method returned
pipeline: subscribe
source: compute
pipeline: onNext
~~~

Kết quả "C. value = Hello Ada" xuất hiện sau khi block() đã lấy được giá trị. Callback "pipeline: finally onComplete" cũng chạy khi chuỗi kết thúc, nhưng không nên phụ thuộc vào thứ tự chính xác của dòng log này so với dòng C trong mọi cách lập lịch/thread. Thay block() bằng việc chỉ giữ biến mono: sẽ chỉ thấy các log đến "B. method returned", vì Mono từ supplier chưa có subscriber.

Ví dụ này cố ý đo thời điểm subscription thông qua Mono.defer. Nó không gọi subscribe() trong advice: quyền subscribe thuộc về downstream (ví dụ tầng WebFlux), nơi quyết định demand, xử lý lỗi và cancellation.

## 5. Trạng thái và đồng thời: một lời gọi, nhiều subscriptions

Giả sử:

~~~java
Mono<String> onePublisher = service.greet("Ada");
Mono<String> first = onePublisher;
Mono<String> second = onePublisher;
~~~

Lúc khai báo onePublisher, Spring proxy chạy **một lần**. Nếu first và second được subscribe độc lập, Mono.defer trong wrapper chạy **hai lần**, mỗi lần có start riêng. Với publisher cold từ supplier, công việc có thể thực hiện hai lần; với publisher được cache/hot, số lần thực thi upstream có thể khác. Trạng thái advice nên phản ánh đúng đại lượng đang đo:

| Đại lượng | Nơi đếm phù hợp | Ý nghĩa |
| --- | --- | --- |
| Số lần bean method được gọi qua proxy | Thân @Before/@Around trước proceed() | Invocation count |
| Chi phí tạo/trả Publisher | Trước và sau proceed() | Assembly time |
| Số lần subscribe vào Publisher đã trả | Mono.defer/Flux.defer hoặc doOnSubscribe phù hợp | Subscription count |
| Thời gian một subscription tồn tại | Bắt đầu mỗi subscription, kết thúc ở doFinally | Subscription duration |
| Số onNext/onError/onComplete/cancel | Reactor operator tương ứng | Reactive signal count |

Trong hệ thống concurrent, @Aspect thường là Spring singleton. Field như "long startNanos" trên @Aspect bị ghi đè giữa nhiều invocation và nhiều subscriber; dùng biến local của invocation nếu đo assembly và biến local **bên trong defer** nếu đo subscription. Khi ghi counters/metrics dùng cơ chế thread-safe (LongAdder, AtomicLong, metrics recorder); đừng cập nhật một HashMap thường từ nhiều callback concurrent.

Reactive có thể đổi thread do scheduler hoặc I/O callback. ThreadLocal và MDC được thiết lập trong @Around lúc method được gọi **không mặc nhiên có mặt** trong onNext/onError ở thread khác. Nếu cần correlation id/request context, ưu tiên Reactor Context và cơ chế context propagation được cấu hình của hệ sinh thái Spring, thay vì giả định ThreadLocal lan truyền tự động:

~~~java
return Mono.deferContextual(context -> {
    String requestId = context.getOrDefault("requestId", "unknown");
    return source.doOnError(error ->
        logger.warn("requestId={} failed", requestId, error));
});
~~~

Mã trên minh họa việc lấy context theo subscriber; logger và source là các biến đã tồn tại trong method/advice tương ứng. Chỉ đọc context tại assembly bằng ThreadLocal rồi giữ làm state toàn cục dễ gắn nhầm request khi có concurrent subscriptions. Nếu ứng dụng có cơ chế bridge Reactor Context ↔ MDC, kiểm tra chính sách propagating context thực tế của ứng dụng.

## 6. Completion, lỗi, cancellation và ngữ nghĩa đo thời gian

Trong reactive flow, "method returns" khác với các tín hiệu sau:

- onNext mang dữ liệu (Mono tối đa một phần tử, Flux có thể nhiều phần tử).
- onComplete xác nhận Publisher kết thúc bình thường; Mono rỗng vẫn có onComplete.
- onError kết thúc luồng bằng lỗi; callback @AfterThrowing của Spring AOP cho lần trả Publisher không thay thế doOnError.
- cancel là hành động downstream yêu cầu hủy subscription; upstream có thể đang xử lý tài nguyên/tác vụ, và việc hủy không đồng nghĩa mọi tác vụ ngoại vi đã bị dừng tức thì.

Đo một yêu cầu WebFlux bằng đồng hồ đặt trước proceed() rồi dừng ngay sau proceed() thường chỉ đo việc tạo pipeline. Dùng defer + doFinally giúp đo thời gian mỗi subscription, bao gồm khoảng chờ dữ liệu khi subscription thực sự bắt đầu. Chọn rõ định nghĩa latency: **từ lúc method invocation**, **từ lúc subscribe**, **đến phần tử đầu tiên**, hay **đến terminal**. Chúng là các metric khác nhau; tên metric không nên đánh đồng.

Operator doFinally là nơi hợp lý để quan sát ON_COMPLETE, ON_ERROR và CANCEL; với resource thực sự, dùng API quản lý tài nguyên đúng ngữ nghĩa reactive (ví dụ usingWhen) thay vì dựa vào finally của method tạo Publisher. Đặt doOnError gần upstream chỉ ghi lỗi, không tự xử lý/phục hồi lỗi; nếu pipeline có onErrorResume ở downstream thì kết quả cuối cùng của toàn chuỗi có thể là thành công. Vị trí operator trong chuỗi ảnh hưởng đại lượng được quan sát.

Với Flux không kết thúc (stream dài hạn, SSE), duration đến terminal có thể rất dài hoặc chỉ dừng khi cancel; nên bổ sung chỉ số "time to first signal" khi đó mới là nhu cầu thực tế. Nếu publisher chưa bao giờ có subscriber, metrics subscription không được tạo, nhưng invocation metrics vẫn được ghi.

## 7. Spring-managed transaction advice và Reactor Context

Một trường hợp quan trọng trong Spring là @Transactional. Spring dùng TransactionInterceptor dựa trên AOP proxy. Với **ReactiveTransactionManager** và phương thức trả reactive type phù hợp, Spring sử dụng nhánh giao dịch reactive; ngữ cảnh giao dịch được truyền bằng **Reactor Context** gắn với reactive pipeline, thay vì dựa vào thread-bound transaction của PlatformTransactionManager.

Do đó:

- Không kết luận transaction đã commit chỉ vì một @AfterReturning quan sát thấy phương thức trả Mono thành công.
- Không lấy transaction state bằng ThreadLocal tùy ý trong callback reactive rồi giả định nó luôn đúng trên mọi scheduler.
- Không mở/đóng transaction thủ công quanh proceed() và cho rằng nó tự bao phủ toàn bộ pipeline; phần làm việc với DB có thể diễn ra sau khi proceed() trả về.
- Nếu phối hợp nhiều aspect (metrics, authorization, transactional advice), cân nhắc thứ tự advisor/@Order và vị trí các operator, vì lớp wrapper của mỗi interceptor ảnh hưởng vị trí quan sát tín hiệu.
- Quy tắc giao dịch cụ thể còn phụ thuộc transaction manager đã cấu hình, kiểu trả về và cách pipeline được subscribe. Không coi mọi @Transactional trên Mono là reactive transaction nếu ứng dụng chỉ có imperative manager.

Phần này chỉ giải thích hệ quả tại ranh giới Spring AOP; cấu hình transaction và thiết kế dữ liệu reactive thuộc phạm vi bài transaction/WebFlux riêng.

## 8. Các lỗi thường gặp và cách sửa

| Cách làm có lỗi | Hệ quả | Hướng sửa trong Spring AOP |
| --- | --- | --- |
| Log "request success" trong @AfterReturning khi nhận Mono | Báo thành công trước khi pipeline có thể phát onError | Phân biệt "Publisher returned" với doOnComplete/doOnError |
| Dùng try/catch quanh proceed() để bắt mọi lỗi async | Bỏ sót onError phát ra khi subscribe | Quan sát doOnError trong Publisher; bắt đồng bộ riêng |
| Đặt mutable startTime ở field của @Aspect | Race khi nhiều request/subscriber | Local theo invocation hoặc local mỗi defer |
| Gọi block()/subscribe() bên trong advice | Chiếm quyền thực thi của WebFlux, thêm subscription, có thể block event loop | Trả Publisher được decorate; để downstream subscribe |
| Gọi proceed() từ callback của Mono.defer mà không xét chủ đích | Dịch việc chạy method và chuỗi advice sang subscription; có thể chạy nhiều lần | Thông thường proceed() khi invocation, chỉ defer các callbacks reactive |
| Trả Flux từ advice cho method khai báo Mono | Sai return contract, có thể ClassCastException | Giữ nguyên Mono/Flux tương ứng |
| Cho rằng publisher nào cũng cold | Đo sai side effects, duplicate work, timing | Biết rõ cold/hot/cache/share của publisher nguồn |
| Tin vào MDC/ThreadLocal xuyên scheduler | Sai request id hoặc mất context | Reactor Context và propagation được cấu hình |
| Dùng @After để release tài nguyên async | Đóng trước khi subscriber dùng xong | Quản lý resource theo reactive lifecycle |
| Gọi nội bộ this.otherMethod() và mong advice chạy | Self-invocation bỏ qua Spring proxy | Gọi qua bean proxy hợp lệ hoặc tổ chức lại bean boundary |
| Target là final class/final method dùng class-based proxy | Proxy khó/không thể advice method như mong muốn | Dùng kiểu bean/proxy phù hợp, kiểm tra proxying constraints |

## 9. Checklist kiểm thử

Kiểm thử đúng ranh giới Spring AOP phải **lấy bean từ Spring context**, không tự new target. Kiểm thử ít nhất các tình huống sau:

1. **Chưa subscribe:** gọi method, xác nhận advice invocation đã chạy; deferred supplier và subscription observer chưa chạy.
2. **Subscribe một lần:** xác nhận upstream phát giá trị, observer thấy tín hiệu; lỗi không bị biến thành success.
3. **Subscribe hai lần vào cùng Publisher:** advice invocation một lần, observer subscription hai lần; đối chiếu tính cold/hot/cached của nguồn.
4. **Lỗi đồng bộ:** method ném ngay trước khi trả Publisher; quan sát qua Spring advice lỗi đồng bộ.
5. **Lỗi reactive:** method trả Mono.error(...) bình thường; nhận onError trong pipeline nhưng @AfterThrowing không bị gọi chỉ bởi terminal error này.
6. **Mono.empty():** không có onNext nhưng có complete; mã đo duration vẫn dừng.
7. **Cancellation:** subscription bị dispose/cancel; doFinally nhận CANCEL khi cancellation đi qua operator đó.
8. **Concurrency:** nhiều subscription trên các scheduler, không chia sẻ timestamp hoặc request id sai.
9. **Proxy boundary:** gọi từ bean bên ngoài qua Spring proxy; so sánh self-invocation nếu cần xác định vì sao advice không kích hoạt.

Với Reactor Test có thể dùng StepVerifier để xác nhận completion/error/cancel và dùng AtomicInteger/LongAdder để đếm invocation, subscription, terminal. Tránh assert thứ tự tuyệt đối của log hoặc thời gian nanosecond; đồng bộ qua tín hiệu reactive và điều kiện kiểm thử có thể quan sát.

## Ghi nhớ

**Spring AOP chặn lần gọi phương thức trả Publisher; Reactive Streams điều khiển công việc theo subscription và signal.** Với Mono/Flux lazy, việc Spring advice thấy một đối tượng trả về không có nghĩa công việc upstream đã hoàn tất. Muốn quan sát tiến trình thực, gắn Reactor operators vào Publisher được trả về, quản lý state theo từng subscription và phân biệt rạch ròi lỗi đồng bộ của method với onError bất đồng bộ của pipeline.

Tài liệu chính thức:
- Spring Framework — Declaring Advice: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — TransactionInterceptor và giao dịch reactive: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html
- Reactor — Error handling và doFinally: https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-state-concurrency-009">Đo đạc khi có lời gọi lồng nhau hoặc đệ quy qua proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Đo đạc lời gọi lồng nhau và đệ quy qua Spring AOP proxy

## 1. Mục tiêu và phạm vi

Trong Spring AOP, advice đo thời gian chỉ chạy khi một lời gọi phương thức **đi qua Spring proxy** và khớp pointcut. Điều này quyết định liệu một lời gọi lồng nhau, một bước đệ quy hay chỉ lời gọi ngoài cùng xuất hiện trong số liệu quan sát.

Sau bài này, bạn có thể:
- Phân biệt lời gọi Java nội bộ trong cùng bean với lời gọi qua proxy do Spring quản lý.
- Giải thích vì sao một lần gọi từ ngoài vào bean có thể tạo nhiều lần chạy `@Around`, hoặc chỉ một lần dù có nhiều phương thức thực thi.
- Đo từng lần đi qua proxy, giữ nguyên kết quả/ngoại lệ, và quản lý mức lồng nhau theo thread.
- Lựa chọn cách tổng hợp **inclusive time**, **exclusive time** và chỉ đo lần ngoài cùng theo mục tiêu quan sát.
- Tránh hiểu sai số liệu khi kết hợp Spring AOP với transaction, async, luồng xử lý reactive hoặc lời gọi chạy trên thread khác.

Bài tập trung vào **cơ chế proxy và advice của Spring AOP**; không triển khai lại lý thuyết AspectJ/AOP tổng quát.

## 2. Chính xác khi nào advice đo thời gian chạy?

Giả sử một Spring bean có hai phương thức công khai `outer()` và `inner()`. Có ba trường hợp khác nhau:

| Đường gọi | Có đi qua Spring proxy tại điểm gọi đó? | Advice khớp `inner()` có chạy? |
| --- | --- | --- |
| Bean khác gọi `service.inner()`, với `service` là Spring proxy | Có | Có |
| `outer()` gọi `this.inner()` hoặc gọi trực tiếp `inner()` trong cùng đối tượng đích | Không | Không |
| `outer()` lấy proxy của bean rồi gọi `proxy.inner()` | Có | Có |

Khi mã bên ngoài gọi `proxy.outer()`, lời gọi `outer()` được advice xử lý. Sau đó, nếu thân `outer()` gọi trực tiếp `inner()`, lời gọi bên trong **không tự quay lại proxy**. Vì vậy việc thêm pointcut cho `inner()` không làm cho lời gọi nội bộ đó được đo riêng.

Tương tự với đệ quy:

~~~text
otherBean -> proxy.outer()        => advice outer
                  |
                  +-> this.inner() => không có advice inner
                         |
                         +-> this.inner() => không có advice inner

otherBean -> proxy.outer()        => advice outer
                  |
                  +-> proxy.inner(2) => advice inner(2)
                           |
                           +-> proxy.inner(1) => advice inner(1)
                                    |
                                    +-> proxy.inner(0) => advice inner(0)
~~~

Một phương thức có thể chạy đệ quy nhiều lần mà advice chỉ thấy **một lần** nếu các bước sau đầu tiên đều gọi trực tiếp vào `this`. Điều này không có nghĩa Spring AOP “bỏ sót” lời gọi đã qua proxy: các lần lồng nhau ấy chưa từng đi qua proxy.

Spring AOP ở đây sử dụng proxy JDK hoặc proxy dựa trên lớp (thường là CGLIB), theo cấu hình. Với proxy JDK, lời gọi phải đi qua giao diện được proxy cung cấp; với proxy dựa trên lớp, phương thức `final` không thể được override để advice áp dụng. Lời gọi `private`, constructor và mã không đi qua Spring proxy cũng không trở thành method join point được đo bằng proxy Spring AOP.

## 3. Ví dụ Spring Boot có thể chạy: trực tiếp so với qua proxy

Ví dụ dùng **Java 17+**, **Spring Boot 3.3.x**, Spring AOP và một bean `WorkService`. Việc dùng `ObjectProvider<WorkService>` chỉ nhằm minh họa cách lấy **Spring-managed proxy ngay tại thời điểm gọi** mà không phải tự tạo đối tượng bằng `new`.

Cấu trúc dự án:

~~~text
pom.xml
src/main/java/com/example/aopdemo/
    DemoApplication.java
    WorkService.java
    TimingAspect.java
~~~

### 3.1. Maven dependency

Tệp `pom.xml`:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>aop-recursion-demo</artifactId>
    <version>1.0.0</version>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.0</version>
        <relativePath/>
    </parent>
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
~~~

`spring-boot-starter-aop` cung cấp các phụ thuộc AOP cần thiết và cấu hình tự động Spring AOP trong ứng dụng Spring Boot. Các ví dụ bên dưới giả định cấu hình proxy mặc định của Spring Boot dùng class-based proxy; `WorkService` và các phương thức của nó không khai báo `final`.

### 3.2. Service: hai con đường đệ quy

Tệp `src/main/java/com/example/aopdemo/WorkService.java`:

~~~java
package com.example.aopdemo;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class WorkService {
    private final ObjectProvider<WorkService> self;

    public WorkService(ObjectProvider<WorkService> self) {
        this.self = self;
    }

    public void direct() {
        recurseDirect(2); // gọi trên this, bỏ qua proxy
    }

    public void recurseDirect(int remaining) {
        if (remaining > 0) {
            recurseDirect(remaining - 1); // vẫn trên this
        }
    }

    public void throughProxy() {
        self.getObject().recurseProxy(2); // đi qua Spring proxy
    }

    public void recurseProxy(int remaining) {
        if (remaining > 0) {
            self.getObject().recurseProxy(remaining - 1);
        }
    }
}
~~~

Trong `direct()`, mọi lời gọi `recurseDirect` đều là self-invocation. Trong `throughProxy()`, mỗi lần gọi `recurseProxy` dùng bean lấy từ container; khi Spring áp dụng aspect cho bean này, đó là proxy. Việc gọi `getObject()` tại runtime tránh việc bắt Spring phải giải quyết một tham chiếu tự thân trực tiếp trong constructor.

**Lưu ý thiết kế:** Tự gọi qua proxy hữu ích để chứng minh hành vi AOP nhưng làm service phụ thuộc gián tiếp vào cơ chế proxy. Trong mã nghiệp vụ thường ưu tiên tách thao tác cần advice sang **một Spring bean khác**, sau đó gọi bean đó bằng injection. Tránh tạo bean thủ công với `new WorkService(...)` vì đối tượng ấy không được Spring AOP quản lý.

### 3.3. Aspect: đo mỗi lần proxy gọi vào service

Tệp `src/main/java/com/example/aopdemo/TimingAspect.java`:

~~~java
package com.example.aopdemo;

import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TimingAspect {
    private final ThreadLocal<Integer> depth =
            ThreadLocal.withInitial(() -> 0);

    @Around("execution(public * com.example.aopdemo.WorkService.*(..))")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        int currentDepth = depth.get();
        depth.set(currentDepth + 1);
        long started = System.nanoTime();

        try {
            return joinPoint.proceed();
        } finally {
            long elapsed = System.nanoTime() - started;

            if (currentDepth == 0) {
                depth.remove();
            } else {
                depth.set(currentDepth);
            }

            System.out.printf(
                    "depth=%d | %s%s | %.3f ms%n",
                    currentDepth,
                    joinPoint.getSignature().getName(),
                    Arrays.toString(joinPoint.getArgs()),
                    elapsed / 1_000_000.0
            );
        }
    }
}
~~~

Các quyết định có chủ đích:

1. `@Around` bao trọn `proceed()` để đo thời gian thực thi tại **lần gọi qua proxy tương ứng**. `return joinPoint.proceed()` giữ nguyên giá trị trả về, kể cả khi ví dụ hiện tại dùng `void`.
2. `finally` vẫn chạy khi nghiệp vụ ném ngoại lệ, và ngoại lệ gốc tiếp tục truyền ra ngoài. Aspect không âm thầm chuyển một lần thực thi lỗi thành thành công.
3. `System.nanoTime()` dùng đo khoảng thời gian trôi qua (elapsed time), khác `System.currentTimeMillis()` dùng thời gian lịch.
4. `ThreadLocal` đánh dấu mức lồng nhau **trên cùng một thread**. Trước khi thoát advice phải khôi phục mức trước đó; ở mức gốc dùng `remove()` để không giữ state trên thread được tái sử dụng.
5. `depth=0` là lần advice ngoài cùng **trong chuỗi lời gọi đang đo của thread này**; `depth=1` là lời gọi qua proxy nằm trong nó. Đây là độ sâu của advice, không phải độ sâu stack Java hay số bước đệ quy thực tế.
6. `System.out.printf` thích hợp cho bài thực hành; trong ứng dụng thật, ưu tiên hệ thống metrics/tracing và tránh log từng lời gọi nóng.

Pointcut chỉ chọn các phương thức `public` của `WorkService`, giúp thấy chính xác bốn tên phương thức trong ví dụ. Nếu có nhiều aspect, `@Order` và thứ tự interceptor có thể làm điểm bắt đầu/kết thúc phép đo khác nhau; số liệu chỉ mô tả vùng thực thi nằm trong advice này.

### 3.4. Chạy hai kịch bản

Tệp `src/main/java/com/example/aopdemo/DemoApplication.java`:

~~~java
package com.example.aopdemo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    CommandLineRunner run(WorkService service) {
        return args -> {
            System.out.println("== DIRECT ==");
            service.direct();

            System.out.println("== THROUGH PROXY ==");
            service.throughProxy();
        };
    }
}
~~~

Từ thư mục chứa `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

Bỏ qua log khởi động Spring Boot, phần log của ví dụ có **thứ tự** như sau; giá trị thời gian thực tế sẽ khác ở mỗi lần chạy:

~~~text
== DIRECT ==
depth=0 | direct[] | <elapsed> ms
== THROUGH PROXY ==
depth=3 | recurseProxy[0] | <elapsed> ms
depth=2 | recurseProxy[1] | <elapsed> ms
depth=1 | recurseProxy[2] | <elapsed> ms
depth=0 | throughProxy[] | <elapsed> ms
~~~

`recurseDirect(2)`, `recurseDirect(1)`, `recurseDirect(0)` **có được thực thi**, nhưng không có dòng đo riêng. Ngược lại, `recurseProxy(2)`, `recurseProxy(1)`, `recurseProxy(0)` đều đi qua proxy; mỗi lời gọi tạo một lượt advice. Log được in ở `finally`, nên xuất hiện theo thứ tự **kết thúc từ sâu nhất ra ngoài cùng**, không phải thứ tự bắt đầu.

Lưu ý: chuỗi `<elapsed>` ở trên là **ký hiệu minh họa**, không phải stdout nguyên văn. Chương trình sẽ in một số dấu phẩy động với ba chữ số thập phân tại vị trí đó, tùy máy và thời điểm chạy.

## 4. Hiểu đúng cách cộng/trừ thời gian

Gọi `T(X)` là khoảng thời gian đo tại advice bọc lời gọi `X`.

- **Inclusive time** của `outer`: tính thời gian nằm trong `outer`, bao gồm thời gian của các lời gọi con chạy đồng bộ bên trong nó.
- **Exclusive/self time** của `outer`: gần đúng bằng thời gian inclusive trừ thời gian inclusive của các **lời gọi con trực tiếp đã đo, không chồng lấn**.
- **Số lần advice chạy**: số lần *cross proxy boundary* khớp pointcut, không nhất thiết bằng số lời gọi Java hoặc số tầng đệ quy thực.

Ví dụ thời gian minh họa: nếu `outer` mất 12 ms và một lời gọi con trực tiếp đã đo mất 7 ms, thì exclusive time của `outer` xấp xỉ 5 ms. Khi `outer` chứa `childA` và `childB` lồng nhau, phải tránh trừ một lần thời gian của cháu rồi lại trừ thông qua thời gian của con.

**Sai lầm thường gặp:** cộng tất cả inclusive time của các lời gọi đệ quy rồi gọi kết quả là tổng thời gian xử lý. Khi lời gọi cha chứa lời gọi con, phép cộng ấy **đếm lặp** thời gian con trong cha. Để đo latency của cả tác vụ, lấy thời gian advice ngoài cùng hoặc một timer bọc toàn bộ tác vụ; để phân tích từng tầng, giữ separate metrics cho từng invocation.

Một cách tính exclusive time trong lời gọi đồng bộ cùng thread là dùng `ThreadLocal<Deque<Frame>>`:

~~~text
Khi vào advice:
    tạo frame {startedAt, childInclusiveNanos=0}
    push frame lên stack

Khi ra advice (trong finally):
    inclusive = nanoTime() - frame.startedAt
    exclusive = inclusive - frame.childInclusiveNanos
    pop frame
    nếu stack còn frame cha:
        frameCha.childInclusiveNanos += inclusive
    ghi metric cho frame vừa hoàn thành
    nếu stack rỗng: remove ThreadLocal
~~~

Đây là **thuật toán minh họa**, không phải lớp Java độc lập biên dịch. Chỉ trừ thời gian của các frame con trực tiếp. Cần xác định liệu thời gian ghi metric/logging của advice con có nằm trong thời gian đo cha hay không; phép trừ dựa trên mốc thời gian không đồng nhất sẽ tạo sai số. Phép tính này cũng không thể áp dụng nguyên xi cho các nhánh chạy song song.

## 5. Ba chính sách đo và cách chọn

| Chính sách | Dữ liệu thu được | Khi nên dùng | Điều cần kiểm soát |
| --- | --- | --- | --- |
| Đo mọi lần qua proxy | Một sample cho từng lời gọi con/đệ quy được intercept | Phân tích hotspot, latency từng method hoặc từng tầng | Số sample có thể tăng mạnh khi đệ quy |
| Đo chỉ mức ngoài cùng của advice (`depth == 0`) | Một sample cho chuỗi lời gọi lồng nhau cùng thread | Latency end-to-end của lời gọi vào proxy | Không có thời gian riêng của các tầng con |
| Ghi inclusive và exclusive | Nhìn tổng latency và ước lượng thời gian tự thân từng lời gọi | Phân tích call hierarchy đồng bộ | Cần stack frame và quy tắc cộng thời gian không chồng lấn |

Nếu dùng `Timer` của Micrometer, hãy xác định rõ metric biểu diễn **một lần gọi method**, **một request**, hay **một tác vụ gốc**. Thẻ phù hợp thường là tên method có tập giá trị giới hạn và outcome (success/error). Tránh đưa input động, ID request, stack đầy đủ hoặc số tầng đệ quy không giới hạn vào tags: những giá trị này gây bùng nổ cardinality của hệ thống metrics.

Có thể chọn policy chỉ đo root bằng một guard trước khi ghi metric, nhưng vẫn phải quản lý `depth` ở mọi lượt advice và khôi phục state trong `finally`. Một guard đánh dấu “đã đo” mà không reset sẽ làm các lượt gọi độc lập sau bị bỏ qua khi dùng thread pool.

## 6. Lỗi thực tế và giới hạn của proxy

### 6.1. Advice không xuất hiện trong lời gọi nội bộ

**Triệu chứng:** có log của `outer()` nhưng không có log `inner()`, mặc dù debug chứng minh `inner()` đã chạy.

**Nguyên nhân:** `this.inner()` / lời gọi trần `inner()` không qua proxy. Kiểm tra thêm bean thực sự lấy từ Spring container và pointcut có khớp method/class hay không.

**Cách giải quyết ưu tiên:** chuyển logic cần advice thành method của bean khác và gọi bean đó qua injected Spring dependency. Nếu muốn giữ trong cùng bean và đã cân nhắc coupling, có thể lấy self proxy như ví dụ. `AopContext.currentProxy()` chỉ hoạt động khi cấu hình expose proxy và khi đang ở lời gọi được proxy intercept; nó làm mã phụ thuộc trực tiếp vào Spring AOP nên không phải lựa chọn mặc định.

### 6.2. Có nhiều lớp advice quanh cùng một lời gọi

Nếu `@Transactional`, security advice, caching hoặc một `@Around` khác cùng tác động, **thứ tự advisor** ảnh hưởng phạm vi thực tế mà timer bao quanh. Ví dụ một timer ở ngoài transaction advice có thể đo thêm thời gian bắt đầu/hoàn tất transaction, trong khi timer ở trong có thể không thấy một phần chi phí đó. Nếu ý nghĩa số đo quan trọng, cấu hình rõ thứ tự bằng `@Order`, chạy kiểm chứng và ghi rõ boundary.

### 6.3. Đệ quy sâu, ngoại lệ và chi phí instrumentation

- Mỗi lần proxy intercept đều chạy chuỗi interceptor, có overhead và thêm frame; đệ quy sâu vẫn có nguy cơ `StackOverflowError`.
- `finally` nên record cả success/failure và vẫn truyền nguyên `Throwable` ra ngoài. Nếu thêm tags kết quả, giữ cardinality hữu hạn; tránh đưa raw message ngoại lệ làm tag.
- Không dùng `System.currentTimeMillis()` cho phép đo ngắn cần tính monotonic; `nanoTime()` chỉ cung cấp độ phân giải thời gian, không bảo đảm độ chính xác tuyệt đối hay đo CPU.
- Tránh log hoặc tạo stack trace trong mỗi bước đệ quy ở đường xử lý nóng. Thử với workload thực tế và xem overhead của advice.

### 6.4. Ranh giới thread và lời gọi bất đồng bộ

`ThreadLocal` chỉ theo dõi những lời gọi chạy trên **cùng một thread**. Khi dùng `@Async`, thread pool hoặc tác vụ song song, cây lồng nhau không tự được ghép lại chỉ nhờ `ThreadLocal`. Một `@Around` đo lời gọi method trả về `CompletableFuture` có thể chỉ đo thời gian **tạo/trả future**, không đo đến khi future hoàn thành. Cần đo vòng đời tác vụ bằng instrumentation phù hợp, hoặc dùng tracing context được truyền có chủ đích qua biên thread.

Với Spring WebFlux/Reactor, method có thể trả `Mono` hoặc `Flux` ngay lập tức; `proceed()` xong không đồng nghĩa publisher đã được subscribe hay xử lý xong. Muốn đo subscription-to-termination nên dùng operator như `doOnSubscribe`, `doFinally` hoặc hệ thống observability tương ứng, thay vì diễn giải thời gian method return là thời gian xử lý reactive hoàn chỉnh.

## 7. Cách kiểm chứng kết quả đo

Khi một biểu đồ cho thấy “ít lượt gọi” hoặc tổng latency lớn bất thường, hãy kiểm tra theo thứ tự:

1. **Đường gọi thật:** caller đang giữ Spring proxy hay đối tượng tự khởi tạo? Các lần gọi tiếp theo có đi qua proxy hay là self-invocation?
2. **Pointcut và khả năng intercept:** method có đúng lớp/giao diện, modifier và scope? Với class-based proxy có dính `final` hay `private` không?
3. **Số lượt và thứ tự:** ở ví dụ này, `direct()` sinh một dòng đo; `throughProxy()` sinh bốn dòng, kết thúc từ sâu nhất. Nếu sai, so lại code và cấu hình AOP.
4. **Ngữ nghĩa thời gian:** đang cộng inclusive samples gây double count hay cần root-only/exclusive? Có advisor khác bao ngoài/bên trong?
5. **Biên bất đồng bộ:** đo method return, future completion hay thời gian hoàn thành publisher? Trace context có được truyền qua thread không?
6. **Sức khỏe state:** sau exception, ThreadLocal depth có trở về 0/remove không? Số lượng method/tags có ổn định khi tải cao?

## 8. Ghi nhớ

Trong Spring AOP, **proxy boundary mới là ranh giới tạo một lần chạy advice**. Self-invocation và đệ quy trực tiếp chạy được nhưng không được advice chặn từng tầng; đi qua Spring proxy ở mỗi lần gọi sẽ làm advice chạy theo từng tầng. Khi đã chọn đúng đường gọi, phải công bố rõ metric là inclusive, exclusive hay chỉ lời gọi ngoài cùng, vì cộng thời gian của các lời gọi lồng nhau có thể đếm lặp. Với async và reactive, luôn xác định chính xác **vòng đời nào** đang được đo.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-state-concurrency-010">Kiểm thử trạng thái chéo giữa nhiều yêu cầu đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử trạng thái chéo giữa nhiều yêu cầu đồng thời trong Spring AOP

## 1. Mục tiêu và phạm vi

Khi nhiều HTTP request hoặc lời gọi dịch vụ đi qua cùng một Spring AOP proxy, **mỗi lời gọi phải giữ đúng dữ liệu ngữ cảnh của chính nó**: mã request, mã người dùng, tenant, thông tin audit, nhãn đo thời gian, correlation ID và kết quả xử lý. Bài này tập trung vào **cách thiết kế và kiểm thử tính cách ly trạng thái trong Spring Aspect**, đặc biệt khi aspect được Spring quản lý như một bean singleton. Không đi sâu vào lý thuyết AOP tổng quát.

Sau bài học, bạn có thể:

- Phân biệt dữ liệu nằm ở **biến cục bộ của advice** với dữ liệu đặt trong **field dùng chung của aspect**.
- Tái hiện một lỗi trạng thái chéo giữa hai lời gọi đồng thời bằng JUnit và các primitive đồng bộ, thay vì hy vọng lỗi xuất hiện khi chạy nhiều lần.
- Kiểm thử đúng **proxy/advice của Spring AOP**, kiểm tra trường hợp thành công, ngoại lệ và việc thu dọn context.
- Xác định giới hạn khi ứng dụng dùng thread pool, tác vụ bất đồng bộ hoặc Spring WebFlux.

**Tình huống:** request A mang correlation ID `A`, request B mang `B`. Nếu advice ghi ID hiện tại vào field `currentId` của một singleton aspect, B có thể ghi đè trong lúc A đang thực thi. Khi A tiếp tục, log hoặc audit của A lại nhận ID của B. Đây là lỗi cách ly ngữ cảnh; hệ thống có thể trả đúng kết quả nghiệp vụ mà audit vẫn sai.

## 2. Vì sao Spring Aspect dễ gặp lỗi này?

Ví dụ có lỗi:

~~~java
@Aspect
@Component
class AuditAspect {
    private String currentRequestId; // shared state trên singleton aspect

    @Around("execution(* com.example.orders.OrderService.*(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        currentRequestId = getRequestId();
        try {
            Object result = pjp.proceed();
            writeAudit(currentRequestId, result);
            return result;
        } finally {
            currentRequestId = null;
        }
    }

    // getRequestId và writeAudit là hàm ứng dụng, lược bỏ ở ví dụ minh họa.
}
~~~

Các lời gọi advice chạy trong các thread khác nhau nhưng thường cùng thao tác trên **một đối tượng aspect**. Field `currentRequestId` là trạng thái dùng chung. `volatile`, nếu thêm vào, chỉ hỗ trợ visibility; nó **không** biến dữ liệu thành riêng cho từng request. Đặt `synchronized` lên toàn bộ advice có thể tuần tự hóa cả đoạn nghiệp vụ, làm giảm concurrency và không phải thiết kế audit đúng đắn.

Bản sửa phù hợp cho dữ liệu chỉ sống trong một lần gọi:

~~~java
@Aspect
@Component
class AuditAspect {
    @Around("execution(* com.example.orders.OrderService.*(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        String requestId = getRequestId(); // biến cục bộ riêng cho mỗi invocation
        try {
            Object result = pjp.proceed();
            writeAudit(requestId, result);
            return result;
        } catch (Throwable error) {
            writeFailureAudit(requestId, error);
            throw error;
        }
    }

    // getRequestId, writeAudit, writeFailureAudit do ứng dụng triển khai.
}
~~~

Đây là **mã minh họa cách tổ chức advice**, chưa phải chương trình chạy độc lập vì các hàm tích hợp audit chưa có phần thân. Điểm cốt lõi là không giữ dữ liệu của từng request trong field aspect. Field chỉ nên chứa collaborator an toàn để dùng chung (service, repository, meter registry, cấu hình bất biến), hoặc trạng thái đã được thiết kế đồng bộ riêng với ý nghĩa dùng chung rõ ràng.

| Vị trí trạng thái | Phạm vi tồn tại thực tế | Đánh giá |
| --- | --- | --- |
| Biến cục bộ bên trong một lần chạy advice | Riêng cho invocation đó | Phù hợp nhất cho correlation ID, thời gian bắt đầu, kết quả tạm |
| Field mutable của singleton `@Aspect` | Chia sẻ giữa các invocation đồng thời | Nguy cơ ghi đè, đọc nhầm và race |
| `ThreadLocal` / MDC | Gắn với thread hiện tại | Có thể dùng trong luồng đồng bộ, **phải thu dọn**; không tự động theo một request qua mọi thread |
| Request-scoped context của Spring MVC | Gắn với request đang hoạt động | Hữu ích trong HTTP servlet request, cần truy cập đúng scope/proxy |
| Reactor `Context` trong WebFlux | Theo chuỗi reactive/subscription | Hướng phù hợp cho ngữ cảnh reactive; không xem ThreadLocal là request context tổng quát |

## 3. Thiết kế ca kiểm thử có khả năng tái hiện lỗi

Kiểm thử thông thường gọi lần lượt A rồi B sẽ khó thấy lỗi. Một bài kiểm thử concurrent cần tổ chức thứ tự đủ rõ:

1. Tạo **một proxy service và một instance aspect dùng chung**.
2. Chạy hai lời gọi `execute("A")` và `execute("B")` ở hai worker thread.
3. Trong target service, dùng `CyclicBarrier(2)` giữ cả hai thread cho đến khi **cả hai đã đi qua đoạn ghi dữ liệu của advice**.
4. Thả barrier để cả hai quay lại advice, đọc dữ liệu mà advice đang giữ.
5. Khẳng định dữ liệu trả cho A vẫn là A và cho B vẫn là B. Chạy tương tự với advice chỉ sử dụng biến cục bộ.

Barrier tạo được lịch thực thi đủ để phơi bày race theo thiết kế. `Thread.sleep(...)` không đảm bảo hai lời gọi thực sự chồng lấn, nên dễ gây test lúc pass lúc fail.

Lưu ý: đoạn mẫu dưới đây gọi service **qua Spring AOP proxy được tạo bằng `AspectJProxyFactory`**, không tạo HTTP request thật. Nó kiểm thử trực tiếp lỗi chia sẻ trạng thái trong Spring advice. Phần sau hướng dẫn kiểm thử qua application context và HTTP boundary.

## 4. Ví dụ JUnit 5: chứng minh lỗi và kiểm tra bản sửa

Với ứng dụng Spring Boot 3.x/Java 17+, cần có các dependency sau trong cấu hình Gradle của module Spring Boot hiện hữu:

~~~groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

`spring-boot-starter-aop` cung cấp tích hợp Spring AOP và AspectJ cần cho `@Aspect`; `spring-boot-starter-test` cung cấp JUnit Jupiter và các thành phần kiểm thử. Đoạn dependency trên giả định project đã cấu hình plugin Spring Boot cùng cơ chế quản lý phiên bản phù hợp.

Tạo file **`src/test/java/demo/AspectStateIsolationTest.java`**:

~~~java
package demo;

import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AspectStateIsolationTest {

    // Target service chỉ phục vụ test: cả hai invocation cùng đi tới barrier.
    static class WorkService {
        private final CyclicBarrier bothInsideTarget = new CyclicBarrier(2);

        public String execute(String requestId) throws Exception {
            bothInsideTarget.await(10, TimeUnit.SECONDS);
            return requestId;
        }
    }

    @Aspect
    static class LeakyAspect {
        // Cố ý sai: field thuộc một aspect instance dùng chung.
        private volatile String currentRequestId;

        @Around("execution(* *..*.execute(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            String requestId = (String) pjp.getArgs()[0];
            currentRequestId = requestId;
            pjp.proceed();
            return currentRequestId;
        }
    }

    @Aspect
    static class IsolatedAspect {
        @Around("execution(* *..*.execute(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            String requestId = (String) pjp.getArgs()[0];
            pjp.proceed();
            return requestId; // giá trị local của chính invocation này
        }
    }

    private static WorkService proxyWith(Object aspect) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new WorkService());
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    private static Map<String, String> twoConcurrentCalls(WorkService service)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = pool.submit(() -> service.execute("A"));
            Future<String> b = pool.submit(() -> service.execute("B"));

            return Map.of(
                    "A", a.get(15, TimeUnit.SECONDS),
                    "B", b.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void mutableAspectFieldLeaksBetweenConcurrentCalls() throws Exception {
        Map<String, String> actual = twoConcurrentCalls(proxyWith(new LeakyAspect()));

        // Sau barrier, cả hai advice đọc cùng field chứa ID được ghi cuối.
        // Vì A và B khác nhau nên ít nhất một kết quả phải sai.
        assertNotEquals(Map.of("A", "A", "B", "B"), actual);
    }

    @Test
    void methodLocalStateStaysIsolated() throws Exception {
        Map<String, String> actual = twoConcurrentCalls(proxyWith(new IsolatedAspect()));

        assertEquals(Map.of("A", "A", "B", "B"), actual);
    }
}
~~~

Chạy:

~~~bash
./gradlew test --tests demo.AspectStateIsolationTest
~~~

Trên Windows có thể dùng `gradlew.bat test --tests demo.AspectStateIsolationTest`.

**Kết quả có thể khẳng định:** cả hai test **PASS** nếu Spring AOP advice được áp dụng chính xác. Test đầu **chủ động kỳ vọng phát hiện lỗi** bằng `assertNotEquals`: nếu B là thread ghi cuối, map quan sát sẽ là `{A=B, B=B}`; nếu A ghi cuối, map là `{A=A, B=A}`. Không dự đoán thread nào sẽ ghi cuối hoặc thứ tự hiển thị của `Map`. Test thứ hai khẳng định map luôn là `{A=A, B=B}`. Kết quả chi tiết thời gian/đường log của Gradle khác nhau theo môi trường nên không coi chúng là stdout cố định.

Vì test dùng `AspectJProxyFactory`, nó kiểm tra AOP proxy thực, không cần khởi động web server. Target là class không `final`, method không `final`, để Spring tạo class-based proxy được. Trong ứng dụng thực, pointcut cần giới hạn chính xác tới package/annotation dịch vụ; wildcard rộng ở đây chỉ để bài test nhỏ không phụ thuộc tên module.

## 5. Kiểm thử qua Spring application context và HTTP request

Unit test ở mục 4 phơi bày chính xác lỗi chia sẻ field. Khi tích hợp vào ứng dụng, nên thêm kiểm thử qua bean thật để xác nhận hệ thống wiring cũng đúng:

- Bật Spring AOP bằng dependency và cấu hình phù hợp (chẳng hạn Spring Boot AOP auto-configuration hoặc `@EnableAspectJAutoProxy` trong cấu hình không dùng Boot).
- Lấy service từ `ApplicationContext`; kiểm tra `AopUtils.isAopProxy(service)` trong test setup và chứng minh pointcut thực sự chạy, ví dụ đếm invocation/ghi nhận event có kiểm soát.
- Gọi bean đã inject từ context; tránh `new Service()` và tránh một method trong bean tự gọi method khác của chính nó nếu đang kỳ vọng Spring proxy intercept self-invocation.
- Kiểm tra song song dữ liệu trả về và metadata do advice xử lý (correlation ID, tenant, audit event hoặc MDC), không chỉ mã HTTP 200.
- Trong test HTTP, gửi hai request đồng thời với header riêng như `X-Correlation-ID: A` và `X-Correlation-ID: B`; cho service đi qua một barrier có timeout để cố định thời điểm overlap. Kiểm tra cả response và sự kiện audit gắn với từng request.
- Dùng `@SpringBootTest(webEnvironment = RANDOM_PORT)` cùng HTTP client phù hợp nếu cần thử toàn bộ filter/controller/service/aspect; `MockMvc` cũng có thể được gọi đồng thời để kiểm tra nhiều lời gọi servlet nhưng không thay thế kiểm thử mọi thành phần của web server thật.

Một kiểm thử tốt phải có tín hiệu **advice đã thực sự chạy**. Nếu pointcut không match hoặc lời gọi bỏ qua proxy, một test chỉ kiểm tra business output có thể PASS dù phần aspect hoàn toàn không được thực thi.

Với nhiều hơn hai request, có thể chạy 10–100 task qua một proxy, gán ID duy nhất, thu thập dữ liệu theo ID và kiểm tra ánh xạ một-một. Đây là stress test bổ sung để phát hiện đường đi khác; nó không thay thế ca barrier có lịch thực thi kiểm soát.

## 6. Kiểm thử cleanup khi có ngoại lệ và thread pool tái sử dụng

Một lỗi khác là advice gán `ThreadLocal` hoặc MDC nhưng quên xóa. Trong servlet MVC đồng bộ, thread từ pool có thể phục vụ request kế tiếp; metadata cũ có thể xuất hiện trong log của request mới. Mẫu chuẩn nên khôi phục **giá trị trước đó**, kể cả khi target ném exception:

~~~java
@Aspect
@Component
class CorrelationAspect {
    @Around("execution(* com.example.orders.OrderService.*(..))")
    public Object withCorrelation(ProceedingJoinPoint pjp) throws Throwable {
        String previous = org.slf4j.MDC.get("requestId");
        String current = resolveRequestId(); // hàm tích hợp của ứng dụng
        try {
            org.slf4j.MDC.put("requestId", current);
            return pjp.proceed();
        } finally {
            if (previous == null) {
                org.slf4j.MDC.remove("requestId");
            } else {
                org.slf4j.MDC.put("requestId", previous);
            }
        }
    }

    // resolveRequestId được ứng dụng triển khai.
}
~~~

Đoạn này là **mã minh họa**. Nó thể hiện phạm vi cleanup trong `finally`, không phải cơ chế tự động truyền MDC tới task bất đồng bộ.

Bộ test nên có các trường hợp sau:

| Ca kiểm thử | Thiết lập | Điều phải xác minh |
| --- | --- | --- |
| Hai lời gọi thành công cùng lúc | A, B và barrier ở target | Không nhầm ID/tenant/audit giữa A và B |
| A thành công, B ném exception | Hai request chạy xen kẽ, một đường lỗi | Advice không làm mất exception, audit lỗi đúng ID |
| Hai task nối tiếp trên **cùng một worker thread** | Executor một thread; request thứ nhất có ID, request sau không có | Không còn MDC/ThreadLocal cũ sau request đầu |
| Advice gọi lồng nhau | Có giá trị context bên ngoài; advice bên trong thay tạm | Giá trị ngoài được khôi phục khi thoát |
| Target bị hủy hoặc timeout | Tạo lỗi có kiểm soát, thread được trả về pool | Cleanup trong `finally` vẫn chạy nếu control flow thực sự thoát khỏi advice |
| Lời gọi bỏ qua proxy | Self-invocation hay đối tượng `new` trực tiếp | Test phát hiện advice không chạy, không báo isolation pass giả |

Không nên viết test chỉ đọc MDC ở thread của JUnit rồi suy luận về MDC trên worker khác: MDC thường gắn với từng thread. Phải quan sát ở chính thread xử lý hoặc thu event có request ID trước khi worker kết thúc. Với phép thử tái sử dụng thread, chọn executor một thread, thu lại MDC trong callable thứ hai và kết thúc executor ở `finally`.

## 7. ThreadLocal, @Async và WebFlux: chọn đúng ranh giới test

**Servlet MVC đồng bộ:** một request thường chạy trên một worker thread ở đoạn synchronous pipeline; ThreadLocal/MDC có thể phù hợp cho logging, với điều kiện giữ phạm vi rõ ràng, xử lý các dispatch bất đồng bộ và luôn cleanup.

**`@Async`, `CompletableFuture`, executor:** luồng thực thi có thể chuyển sang thread khác. ThreadLocal/MDC của thread gọi không tự động truyền sang worker. Khi thật sự cần propagation, dùng cơ chế được kiểm soát như `TaskDecorator`/wrapper chụp và khôi phục context; test phải chứng minh cả propagation **và** cleanup ở thread thực thi. Việc dùng virtual thread không loại bỏ nhu cầu xác định lifetime của context.

**Spring WebFlux/Reactor:** một request không tương đương một thread cố định. Không dựa vào mutable field của aspect hoặc MDC ThreadLocal như nguồn ngữ cảnh chính cho toàn chuỗi reactive. Với reactive context, ưu tiên `Reactor Context` và các cơ chế context propagation chính thức; kiểm thử qua việc subscribe, đổi scheduler, lỗi, hủy subscription khi có giá trị học tập. Advice `@Around` trả `Mono`/`Flux` có thể kết thúc khi **publisher được tạo**, trước khi pipeline thực thi; muốn ghi nhận kết quả cuối phải dùng operator thích hợp trên publisher, không mặc định coi `pjp.proceed()` là lúc dữ liệu đã được phát ra.

**Request scope:** một bean `@RequestScope` tạo đối tượng theo request web đang hoạt động; nếu aspect singleton cần collaborator theo request, thường inject scoped proxy/provider thay vì chép request ID vào field. Kiểm thử request scope phải chạy trong môi trường có request context hợp lệ.

## 8. Những sai lầm thường gặp khi viết test

1. **Gọi target trực tiếp** khiến advice không chạy; nếu đang kiểm thử Spring AOP thì bắt buộc đi qua proxy.
2. **Tạo aspect mới cho mỗi task** làm mất tình huống singleton dùng chung cần thử; ít nhất một case phải có nhiều task trên cùng proxy/aspect instance.
3. **Chỉ dùng sleep hoặc tăng số lượng thread** mà không tạo overlap có kiểm soát; test dễ pass ngẫu nhiên.
4. **Thấy lỗi biến mất sau khi thêm volatile rồi kết luận an toàn**: visibility không tạo isolation.
5. **Không kiểm tra MDC/ThreadLocal sau exception** khiến lỗi xuất hiện ở request tiếp theo trên thread pool.
6. **Chỉ assert business response** trong khi lỗi nằm trong logging, audit, tenant routing hoặc tracing metadata.
7. **Dùng một barrier không timeout** có thể làm CI treo nếu một nhánh lỗi trước khi tới điểm đồng bộ.
8. **Nhầm mô hình reactive với servlet**: chuyển thread/scheduler làm suy luận theo thread hiện tại sai.

## 9. Checklist áp dụng vào dự án

- Xác định `@Aspect` có field mutable nào lưu dữ liệu phụ thuộc invocation/request không; các field này là ứng viên kiểm tra đầu tiên.
- Ghi rõ nguồn request ID/tenant và điểm advice lấy, truyền, log, audit, cleanup.
- Tạo ít nhất một test deterministic với 2 concurrent calls đi qua **cùng Spring AOP proxy**, dùng barrier hoặc latch có timeout.
- Assert kết quả **theo từng request ID**, bao gồm cả audit/logging metadata; xác nhận advice thực sự khớp pointcut.
- Thêm case ngoại lệ, nested invocation và reuse worker thread nếu hệ thống có ThreadLocal/MDC.
- Nếu có `@Async` hoặc Reactor, xây dựng test truyền ngữ cảnh riêng cho mô hình thực thi tương ứng.
- Đảm bảo thread pool được shutdown và mọi primitive đồng bộ đều có timeout để test dừng khi lỗi.

**Tiêu chí hoàn thành:** Với một aspect đúng, hai yêu cầu có ngữ cảnh A/B dù chạy xen kẽ vẫn sinh dữ liệu A/B đúng chủ sở hữu; khi xảy ra lỗi, advice giữ nguyên exception và thu dọn context; khi đổi thread, việc truyền ngữ cảnh được kiểm thử theo cơ chế mà ứng dụng thực sự dùng.


</details>

- [Quay lại đầu trang](#back-to-top)
