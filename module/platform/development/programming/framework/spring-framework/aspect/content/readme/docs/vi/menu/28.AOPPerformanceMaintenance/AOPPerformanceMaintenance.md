<a id="back-to-top"></a>

# Hiệu năng và bảo trì

## Menu
- [Chi phí tạo proxy và chi phí mỗi lần đi qua chuỗi advice](#aop-performance-maintenance-001)
- [Pointcut tĩnh, pointcut động và kiểm soát phạm vi khớp](#aop-performance-maintenance-002)
- [Đo đạc bằng thử nghiệm có bean proxy và đường gọi đối chứng](#aop-performance-maintenance-003)
- [Không thực hiện I/O nặng hoặc ghi log nhạy cảm trong mọi advice](#aop-performance-maintenance-004)
- [Giới hạn gọi phản chiếu và trích xuất metadata lặp lại](#aop-performance-maintenance-005)
- [Giảm phụ thuộc vòng và tránh aspect gọi ngược chính bean bị chặn](#aop-performance-maintenance-006)
- [Giữ aspect tập trung vào một trách nhiệm có thể mô tả rõ](#aop-performance-maintenance-007)
- [API công khai và API nội bộ khi nâng phiên bản Spring](#aop-performance-maintenance-008)
- [AOT, native image và nhu cầu khai báo proxy theo phiên bản, môi trường](#aop-performance-maintenance-009)
- [Tương thích Kotlin final và điều kiện tạo proxy khi ứng dụng có Kotlin](#aop-performance-maintenance-010)
- [Tiêu chí loại bỏ aspect khi cơ chế trực tiếp dễ kiểm soát hơn](#aop-performance-maintenance-011)

## <a id="aop-performance-maintenance-001">Chi phí tạo proxy và chi phí mỗi lần đi qua chuỗi advice</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí tạo proxy và chi phí mỗi lần đi qua chuỗi advice trong Spring AOP

## 1. Hai khoản chi phí rất khác nhau

Khi một ứng dụng Spring dùng `@Aspect` để ghi log, đo thời gian, kiểm soát giao dịch hoặc ghi nhận audit, có ít nhất hai khoản chi phí cần phân biệt:

1. **Chi phí tạo proxy** (*proxy creation cost*): Spring kiểm tra bean có cần advisor hay không, chuẩn bị hạ tầng proxy JDK/CGLIB, xây cấu hình interceptor, và tạo object proxy. Chi phí này thường xuất hiện **trong quá trình bean creation** (với singleton thường là lúc khởi động `ApplicationContext`; với lazy/prototype/scoped bean có thể muộn hơn hoặc lặp lại).
2. **Chi phí qua proxy/chuỗi advice trong từng lời gọi method** (*per-invocation overhead*): mỗi method call đi vào proxy, Spring xác định hoặc tái sử dụng chuỗi interceptor tương ứng, thực thi advice theo thứ tự, chuyển tới target, sau đó trả kết quả hoặc xử lý exception.

Chúng tác động lên những chỉ số khác nhau:
- Tạo proxy ảnh hưởng chủ yếu tới **startup, tốc độ tạo bean, số object/class proxy và tài nguyên khởi tạo**.
- Method interception ảnh hưởng chủ yếu tới **latency và throughput ở runtime**, nhất là method nhỏ được gọi với tần suất cao.
- Nội dung advice (**I/O log, ghi database, network, transaction, authorization, serialization...**) thường có chi phí khác hẳn cơ chế chuyển tiếp qua proxy. Không đổ mọi chậm trễ lên Spring AOP khi logic trong advice mới là nút thắt.

Đây là bài về **hiệu năng và bảo trì Spring Aspect/Spring AOP**. Không đi sâu AOP tổng quát hoặc so sánh mọi framework bytecode weaving. Kết luận định lượng cần được **benchmark trên môi trường thật**, không có hằng số kiểu “mỗi advice mất X ns” áp dụng cho tất cả JVM.

## 2. Spring tạo proxy vào lúc nào?

Một luồng phổ biến khi `@EnableAspectJAutoProxy` đã bật và `@Aspect` đã được đăng ký thành Spring bean:

~~~text
ApplicationContext refresh / bean initialization
        │
        ├── Đăng ký Spring AOP auto-proxy creator (BeanPostProcessor)
        ├── Khởi tạo target bean, inject dependency, chạy init callbacks
        ├── Auto-proxy creator xem bean có advisor áp dụng không
        │     ├── Không phù hợp → trả target như cũ
        │     └── Phù hợp:
        │           ├── Chọn JDK dynamic proxy hoặc CGLIB
        │           ├── Chuẩn bị AdvisedSupport, advisor/interceptor metadata
        │           └── Tạo và trả object proxy
        │
        └── Đưa bean/proxy vào singleton registry khi lifecycle hoàn tất
                  │
                  ▼
         Những method invocation về sau
            caller → proxy → advice chain → target
~~~

Sơ đồ là mô hình để tính chi phí, không phải thứ tự tuyệt đối cho mọi bean; `FactoryBean`, scoped proxy, `@Lazy`, circular references, custom `BeanPostProcessor` và sự tồn tại của advisor khác có thể làm đường khởi tạo phức tạp hơn.

### 2.1. Tạo proxy không xảy ra mỗi lần gọi method

Với **singleton bean bình thường**, Spring tạo và cache bean/proxy trong vòng đời container. Gọi `service.process()` một triệu lần **không có nghĩa Spring tạo một triệu proxy**. Phần creation thường trả một lần cho mỗi object proxy; phần method interception có thể phát sinh trong từng lời gọi.

Nhưng lưu ý:
- `@Lazy` trì hoãn lúc bean/proxy được tạo, không xóa chi phí tạo.
- Prototype bean được khởi tạo mỗi lần container cung cấp instance theo quy tắc của prototype; nếu mỗi instance cần proxy thì có chi phí tương ứng.
- Request/session-scoped bean có thể có **scoped proxy** bền hơn object target mà nó trỏ tới; phân biệt **proxy wrapper** và việc tạo **target theo scope**.
- Application context được tạo/đóng liên tục (ví dụ một số test) có thể lặp lại toàn bộ proxy initialization.
- Advisor/interceptor có thể cache metadata hoặc method matching ở nhiều mức; không giả định mọi bước discovery phải chạy nguyên vẹn ở mỗi invocation.

### 2.2. Các yếu tố quyết định chi phí tạo proxy

| Yếu tố | Tác động có thể có | Ghi chú |
| --- | --- | --- |
| Số lượng Spring beans được xét | Tăng công việc phân tích eligibility / advisor | Không phải bean nào cũng sinh proxy |
| Số advisor / aspect / pointcut | Tăng công việc xác định applicability và metadata | Pointcut rộng có thể tăng số bean được proxy |
| JDK vs CGLIB | Khác cơ chế tạo proxy/interface/subclass | Không khẳng định một bên luôn nhanh hơn |
| Class/interface complexity | Ảnh hưởng proxy generation và cấu trúc method | Phụ thuộc Spring/JDK/runtime |
| Singleton, lazy, prototype, scope | Quyết định **khi nào và bao nhiêu lần** tạo instance/proxy | Lazy đổi thời điểm trả chi phí |
| Cold start vs warm start | Classloading/JIT/cache khiến chi phí quan sát khác nhau | Benchmark khởi tạo phải định nghĩa rõ mốc |
| Nhiều `ApplicationContext` | Mỗi container có lifecycle và auto-proxy creator riêng | Rất quan trọng với web root/child contexts và test context caching |

**CGLIB class proxy không tự tạo target constructor thêm lần thứ hai trong điều kiện bình thường**: Spring dùng Objenesis để tránh gọi constructor của target hai lần khi tạo proxy; một số JVM không hỗ trợ bypass có thể xảy ra đường fallback. Không dùng phát biểu “CGLIB lúc nào cũng chạy constructor hai lần” để ước lượng startup cost.

## 3. Một lời gọi qua Spring AOP proxy gồm các bước gì?

Giả sử `checkoutService.checkout(...)` là lời gọi qua Spring AOP proxy và ba advice/interceptor có hiệu lực.

~~~text
Caller
  │
  └── proxy.checkout(order)
          │
          ├── interceptor A (ví dụ security): trước
          │     ├── interceptor B (ví dụ transaction): mở transaction
          │     │     ├── interceptor C (ví dụ metric): start
          │     │     │      └── target.checkout(order)
          │     │     └── interceptor C: stop
          │     └── interceptor B: commit / rollback
          └── interceptor A: sau
               │
               └── trả result / throw exception
~~~

Thứ tự cụ thể là do precedence/advisor order thực tế quyết định, **không mặc định Security luôn ngoài Transaction hoặc Metrics luôn trong cùng vị trí**.

Với `@Around`, một advice thường gọi `ProceedingJoinPoint.proceed()` để tiếp tục chuỗi. Mỗi tầng có thể thêm những chi phí như:
- Dispatch/interceptor invocation, kiểm tra pointcut runtime nếu cần, lấy method/arguments/context.
- Các thao tác trong advice như `System.nanoTime()`, tạo message, metadata lookups, authentication context.
- Tạo object và allocation (ví dụ args array, join point wrapper, tracing spans/timers), tùy đường code/JVM.
- Exception handling, `try/finally` và callback quanh method.
- Chi phí **target method**: thứ cần giữ nguyên khi so sánh direct call và proxied call.

**Chi phí Spring AOP overhead** không đồng nhất với tổng thời gian elapsed của advice. Ví dụ `@Around` ghi log ra console mỗi lần sẽ tạo I/O rất lớn; phần overhead framework có thể nhỏ hơn nhiều so với logger, nhưng mức chênh lệch phải được đo trên workload thật.

### 3.1. Độ dài advisor chain quan trọng nhưng không tuyến tính tuyệt đối

Trên một đường thực thi cụ thể, nếu advice A, B, C đều chạy, thường có thêm công việc khi qua từng interceptor. Nhưng không thể dùng công thức cố định:

~~~text
T(total) = T(target) + N × constant_nanoseconds
~~~

Mỗi interceptor có logic khác nhau; JIT, branch prediction, method matching, allocation, pointcut động, exception path và contention làm chi phí thay đổi. Một `@Around` có `proceed()` gọi nhiều lần hoặc có retry còn **thay đổi số lần target chạy**, không chỉ “thêm một tầng chi phí”.

Khi performance review, hỏi:
1. Bao nhiêu advisor **thực sự áp dụng trên method được gọi**, không chỉ có bao nhiêu class `@Aspect` trong project?
2. Advice là no-op, metric, logger hay giao dịch/database?
3. Đang so sánh số lần gọi, số lần target thực thi hay số lần retry?
4. Đường gọi có đi qua Spring proxy không, hay self-invocation đã bypass?

### 3.2. Pointcut static và dynamic

Một số matcher có thể được quyết định từ type/method metadata tương đối sớm. Một số trường hợp cần xem arguments hoặc runtime state ở thời điểm gọi (ví dụ runtime method matching, bindings). Chi phí của **đánh giá pointcut** và **advice body** có thể khác nhau.

Điều quan trọng là viết pointcut **đúng phạm vi** trước khi tối ưu. Pointcut quá rộng có thể proxy thêm các beans không cần thiết và mở rộng advisor chain. Nhưng không nên đổi pointcut phức tạp thành pointcut hẹp hơn nếu làm mất trường hợp nghiệp vụ cần bảo vệ.

## 4. So sánh JDK dynamic proxy và CGLIB cho đúng

Spring AOP mặc định thường chọn JDK proxy khi target có business interface phù hợp; nếu cần class-based proxy hoặc target không có interface thích hợp, CGLIB có thể được chọn. Cấu hình `proxyTargetClass`, transaction/async auto-proxy settings và default của Spring Boot có thể làm chính sách thực tế khác nhau.

| Tiêu chí | JDK dynamic proxy | CGLIB class-based proxy |
| --- | --- | --- |
| Hình thức | Object runtime implement interface | Runtime subclass của target class |
| Caller nên phụ thuộc | Business interface | Có thể là class hoặc interface |
| Method interception | Qua interface proxy | Qua method override trên subclass |
| Final implementation method | Có thể được intercept qua method của interface | Không override được method final |
| Class final | Có thể proxy interface của final implementation | Không thể subclass final class |
| Creation cost | Có phụ thuộc interface/proxy class/JDK | Có phụ thuộc subclass generation/caching |
| Per-call cost | Interface dispatch và interceptor chain | Subclass dispatch và interceptor chain |
| Kết luận hiệu năng | **Phải benchmark**, không khẳng định luôn thắng | **Phải benchmark**, không khẳng định luôn thắng |

Đừng đổi từ JDK sang CGLIB chỉ vì đọc được một microbenchmark cho thấy chênh vài phần trăm trên một JDK cụ thể: thay đổi proxy strategy còn ảnh hưởng injection type, visibility, final/private methods, module system và khả năng tương thích.

Nếu có quá nhiều method rất nhỏ trong hot path, cả hai có thể thêm measurable overhead so với direct call. Nhưng khi target thực hiện database query hoặc HTTP I/O, chi phí proxy dispatch thường không phải thành phần cần xử lý trước.

## 5. Ví dụ Spring AOP: một lời gọi method và nhiều advice

Cấu hình Spring dưới đây minh họa **advice có logic tối thiểu**; nó là đoạn tham chiếu mô tả cơ chế, **không phải file Java độc lập**. Mã benchmark JMH chạy được nằm ở mục 6.

~~~java
@Aspect
public class WorkAspect {

    @Before("execution(* demo.perf.Calculator.compute(..))")
    public void before() {
        // Không ghi log trong hot path benchmark.
    }

    @Around("execution(* demo.perf.Calculator.compute(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        // Mỗi method invocation qua proxy đi qua advice này.
        return pjp.proceed();
    }
}
~~~

Cần import `org.aspectj.lang.annotation.Aspect`, `Before`, `Around` và `ProceedingJoinPoint`, rồi đăng ký aspect vào `ApplicationContext` có `@EnableAspectJAutoProxy`. Spring xử lý các advice đó thành advisor/interceptor tương ứng; runtime có thể còn nhiều advisor khác như transaction, caching hoặc method security.

**Không gọi `log.info(...)`, `System.out.println(...)`, HTTP client hoặc database trong no-op benchmark** rồi gọi kết quả là “Spring proxy overhead”. Nếu muốn đo production logging, tạo **benchmark riêng có cùng logging sink/appender, log level và payload thực tế** để biết chi phí logging.

## 6. JMH benchmark có thể chạy: direct call, JDK, CGLIB và độ dài chuỗi advice

Để đo thời gian method call nhỏ, dùng **JMH (Java Microbenchmark Harness)** thay vì vòng `System.nanoTime()` tự viết. JMH hỗ trợ warmup, forks, thống kê, benchmark state và `Blackhole` để giảm rủi ro JVM tối ưu làm sai kết quả.

Ví dụ dùng **Java 17+**, Spring Framework 6.1.14 và JMH 1.37. Đây là **Spring AOP proxy engine** được tạo bằng `ProxyFactory` để kiểm soát chính xác số interceptor. Nó không đo toàn bộ Spring context startup hoặc annotation discovery; ở mục 8 sẽ nêu cách đo khởi động ứng dụng thật.

### 6.1. Cấu trúc project

~~~text
build.gradle
src/main/java/demo/perf/
    Calculator.java
    CalculatorImpl.java
    SpringAopBenchmark.java
    ProxyCreationBenchmark.java
~~~

### 6.2. build.gradle

~~~groovy
plugins {
    id 'java'
    id 'application'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.1.14'
    implementation 'org.springframework:spring-aop:6.1.14'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'

    implementation 'org.openjdk.jmh:jmh-core:1.37'
    annotationProcessor 'org.openjdk.jmh:jmh-generator-annprocess:1.37'
}

application {
    mainClass = 'org.openjdk.jmh.Main'
}
~~~

JMH annotation processor tạo benchmark metadata trong quá trình compile. `mainClass` trỏ đến JMH launcher để benchmark được điều phối theo warmup/measurement; không chạy benchmark logic từ `main` tự viết.

### 6.3. Business interface và implementation

**`src/main/java/demo/perf/Calculator.java`**

~~~java
package demo.perf;

public interface Calculator {
    int compute(int value);
}
~~~

**`src/main/java/demo/perf/CalculatorImpl.java`**

~~~java
package demo.perf;

public class CalculatorImpl implements Calculator {

    @Override
    public int compute(int value) {
        int x = value;
        for (int i = 0; i < 16; i++) {
            x = x * 1664525 + 1013904223;
        }
        return x;
    }
}
~~~

Target được chọn cố ý không làm I/O, không allocation trong method và có lượng arithmetic nhỏ để phép đo tập trung vào đường dispatch/advice. Đây chỉ là một workload mẫu, **không đại diện mọi service method**.

### 6.4. Benchmark chi phí mỗi method invocation

**`src/main/java/demo/perf/SpringAopBenchmark.java`**

~~~java
package demo.perf;

import java.util.concurrent.TimeUnit;

import org.aopalliance.intercept.MethodInterceptor;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.aop.framework.ProxyFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SpringAopBenchmark {

    @State(Scope.Thread)
    public static class Inputs {

        // Volatile input giúp tránh cố định giá trị ngay lúc JIT tối ưu.
        public volatile int input = 17;

        public CalculatorImpl direct;
        public Calculator jdkOne;
        public Calculator jdkThree;
        public Calculator cglibOne;
        public Calculator cglibThree;

        @Setup(Level.Trial)
        public void setup() {
            direct = new CalculatorImpl();

            jdkOne = makeProxy(direct, false, 1);
            jdkThree = makeProxy(direct, false, 3);
            cglibOne = makeProxy(direct, true, 1);
            cglibThree = makeProxy(direct, true, 3);
        }
    }

    static Calculator makeProxy(
            CalculatorImpl target, boolean classBased, int adviceCount) {

        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(classBased);

        if (!classBased) {
            factory.setInterfaces(Calculator.class);
        }

        for (int i = 0; i < adviceCount; i++) {
            factory.addAdvice(
                (MethodInterceptor) invocation -> invocation.proceed()
            );
        }

        return (Calculator) factory.getProxy();
    }

    @Benchmark
    public void directCall(Inputs state, Blackhole blackhole) {
        blackhole.consume(state.direct.compute(state.input));
    }

    @Benchmark
    public void jdkOneAdvice(Inputs state, Blackhole blackhole) {
        blackhole.consume(state.jdkOne.compute(state.input));
    }

    @Benchmark
    public void jdkThreeAdvices(Inputs state, Blackhole blackhole) {
        blackhole.consume(state.jdkThree.compute(state.input));
    }

    @Benchmark
    public void cglibOneAdvice(Inputs state, Blackhole blackhole) {
        blackhole.consume(state.cglibOne.compute(state.input));
    }

    @Benchmark
    public void cglibThreeAdvices(Inputs state, Blackhole blackhole) {
        blackhole.consume(state.cglibThree.compute(state.input));
    }
}
~~~

**Lý do thiết kế như vậy:**
1. `@Setup(Level.Trial)` tạo target và proxies **trước khoảng đo**, nên benchmark này chỉ đo **lời gọi method**; không trộn chi phí tạo proxy vào mỗi invocation.
2. Cả JDK và CGLIB đều gọi cùng implementation `CalculatorImpl.compute`; bộ lọc advisor của `ProxyFactory` được kiểm soát bằng số lượng interceptor 1 hoặc 3.
3. Các interceptor no-op vẫn thực hiện `invocation.proceed()`, tạo chain overhead, nhưng không phát log/network.
4. `Blackhole.consume(...)` sử dụng return value để hạn chế dead-code elimination; volatile input làm giá trị đọc không phải hằng compile-time đơn giản.
5. `@State(Scope.Thread)` tách state giữa benchmark threads, tránh đưa contention không mong muốn vào phép so sánh.
6. `directCall` là baseline, không có proxy. Nó có thể được JIT inline rất mạnh; điều này chính là một phần khác biệt cần hiểu nhưng làm **tỷ lệ phần trăm** giữa direct và proxy trở nên nhạy cảm với môi trường.

### 6.5. Benchmark tạo proxy riêng, không gộp với call

**`src/main/java/demo/perf/ProxyCreationBenchmark.java`**

~~~java
package demo.perf;

import java.util.concurrent.TimeUnit;

import org.aopalliance.intercept.MethodInterceptor;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.springframework.aop.framework.ProxyFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ProxyCreationBenchmark {

    @Benchmark
    public Object newTargetOnly() {
        return new CalculatorImpl();
    }

    @Benchmark
    public Object createJdkProxy() {
        ProxyFactory factory = new ProxyFactory(new CalculatorImpl());
        factory.setInterfaces(Calculator.class);
        factory.setProxyTargetClass(false);
        factory.addAdvice(
            (MethodInterceptor) invocation -> invocation.proceed()
        );
        return factory.getProxy();
    }

    @Benchmark
    public Object createCglibProxy() {
        ProxyFactory factory = new ProxyFactory(new CalculatorImpl());
        factory.setProxyTargetClass(true);
        factory.addAdvice(
            (MethodInterceptor) invocation -> invocation.proceed()
        );
        return factory.getProxy();
    }
}
~~~

Phép đo `createJdkProxy`/`createCglibProxy` gồm **tạo target, tạo ProxyFactory, thêm advice và lấy proxy**. Nó đo **steady-state proxy construction theo API này**, không phải toàn bộ discovery/bean injection của `ApplicationContext.refresh()`.

JMH **warmup** và caching của framework/JVM có thể làm class generation chi phí cao lần đầu không xuất hiện đầy đủ trong kết quả đo creation steady-state. Vì thế **không dùng riêng benchmark này để khẳng định cold startup Spring Boot nhanh/chậm bao nhiêu**.

### 6.6. Lệnh chạy JMH

Compile và chạy những benchmark chi phí lời gọi (macOS/Linux):

~~~bash
./gradlew run --args='demo.perf.SpringAopBenchmark -wi 5 -i 5 -w 1s -r 1s -f 2'
~~~

PowerShell trên Windows:

~~~powershell
.\gradlew.bat run --args="demo.perf.SpringAopBenchmark -wi 5 -i 5 -w 1s -r 1s -f 2"
~~~

Đo proxy construction riêng:

~~~bash
./gradlew run --args='demo.perf.ProxyCreationBenchmark -wi 5 -i 5 -w 1s -r 1s -f 2'
~~~

Các tham số:
- `-wi 5`: 5 warmup iterations.
- `-i 5`: 5 measurement iterations.
- `-w 1s`: mỗi warmup iteration dài khoảng 1 giây.
- `-r 1s`: mỗi measurement iteration dài khoảng 1 giây.
- `-f 2`: 2 fork JVM độc lập, giúp quan sát độ biến động qua process.

Kết quả JMH sẽ chứa tên method, mode `avgt`, score, đơn vị `ns/op` hoặc `us/op` và sai số ước lượng. **Không có stdout giá trị benchmark cố định** vì JVM, CPU, nhiệt độ, GC, JIT, phiên bản Spring/JDK và mức tải hệ thống đều ảnh hưởng. Mọi con số đo được phải đi kèm cấu hình môi trường.

Chạy từ terminal có Gradle wrapper và quyền tải dependency Maven Central. Ví dụ này được thiết kế cho Java 17+ và đã được rà soát về cú pháp; khi học thực tế, chạy build/JMH trên máy mục tiêu để xác nhận kết quả. **Không xem kết quả minh họa chưa đo là dữ liệu thực nghiệm.**

## 7. Cách đọc benchmark mà không rút kết luận sai

Giả sử JMH xuất ra bảng gồm năm phép đo `directCall`, `jdkOneAdvice`, `jdkThreeAdvices`, `cglibOneAdvice` và `cglibThreeAdvices`. Không cần gán số cụ thể; đọc như sau:

| So sánh | Câu hỏi được trả lời | Giới hạn |
| --- | --- | --- |
| `jdkOneAdvice - directCall` | Overhead tương đối của JDK proxy + một no-op interceptor trên workload mẫu | Có thể bao gồm khác biệt JIT/inlining |
| `cglibOneAdvice - directCall` | Overhead tương đối của CGLIB proxy + một no-op interceptor | Không suy ra CGLIB luôn chậm/nhanh hơn JDK |
| `jdkThreeAdvices - jdkOneAdvice` | Chi phí tăng thêm khi mở rộng chuỗi từ 1 lên 3 interceptor | Không chứng minh tuyến tính tuyệt đối cho mọi advice |
| `cglibThreeAdvices - cglibOneAdvice` | Chi phí chain bổ sung với class-based proxy | Advisor matching/runtime state khác có thể đổi kết quả |
| `createJdkProxy - newTargetOnly` | Chi phí bổ sung để tạo proxy (steady-state API path) | Không đo toàn bộ Spring startup |
| `createCglibProxy - createJdkProxy` | Chênh lệch tạo proxy trong cùng benchmark | Không đo cold class generation hoặc production dependency injection |

**Cách tính chênh lệch tuyệt đối:**

~~~text
delta_ns_per_call =
    measured_proxy_ns_per_call - measured_direct_ns_per_call
~~~

**Ước lượng ảnh hưởng mỗi giây nếu có N lời gọi:**

~~~text
additional_cpu_time_seconds_per_second
    ≈ delta_ns_per_call * calls_per_second / 1_000_000_000
~~~

Đây chỉ là **ước lượng sơ bộ** khi phép đo delta phản ánh đúng workload; không tự suy ra wall-clock latency của request có nhiều thread, GC, blocking I/O, contention hoặc caching từ phép nhân này. Nếu đo ns/op ở vùng rất nhỏ, sai số và JIT có thể ngang cỡ delta; nên xem error bars và thử nghiệm lại.

Ví dụ về tiêu chí thực dụng: nếu advice thực hiện network call, trước tiên tối ưu network batching/cache/connection pool hoặc bỏ I/O không cần thiết trong advice; việc thay JDK proxy bằng CGLIB hiếm khi giải quyết nút thắt I/O đó.

## 8. Đo startup/chi phí tạo Spring bean proxy thật trong ứng dụng

`ProxyFactory` ở mục 6 cô lập proxy creation. Nhưng **startup thực** của Spring/Boot còn gồm:
- Classpath scanning, đọc annotation/bean definitions.
- Tạo và đăng ký `BeanPostProcessor` cùng auto-proxy creator.
- Phân tích advisor applicability, pointcut, class metadata.
- Dependency injection, init callbacks, `FactoryBean`, lifecycle listeners.
- JIT/classloading và tạo các proxy class cần thiết.
- Tài nguyên ngoài AOP như DB pool, web server, cache warmup và application events.

Muốn đo có ý nghĩa **startup overhead của Spring AOP**, dùng hai cấu hình **tương đương về chức năng** ngoại trừ aspect cần kiểm tra; đồng thời đo số bean/proxy, CPU/memory/allocation và thời điểm ready. Không được tắt advisor transaction/security quan trọng chỉ để làm đẹp baseline.

### 8.1. Định nghĩa rõ phép đo

Có nhiều điểm mốc:
- Thời gian gọi `new AnnotationConfigApplicationContext(...); refresh();` trong một application nhỏ.
- Thời gian tạo **một bean cụ thể** từ `BeanFactory` khi hạ tầng BPP đã sẵn sàng.
- Thời gian Spring Boot từ JVM process start đến `ApplicationReadyEvent`.
- Cold startup đầu tiên hay warm startup trong cùng môi trường/container.
- Thời gian tạo **proxy class đầu tiên** so với proxy instance thứ N khi class đã được cache.

Những mốc này **không thay thế** cho nhau. Nếu sử dụng profile/JFR, đánh dấu timeline và kiểm tra hoạt động thực sự của auto-proxy creator, không chỉ nhìn tổng thời gian startup.

### 8.2. Dùng Spring ApplicationStartup và JFR khi cần

Trong ứng dụng Spring, `ApplicationStartup` có thể ghi lại các bước khởi tạo container. Với Spring Boot có thể sử dụng `BufferingApplicationStartup` và cơ chế observability/Actuator hỗ trợ để xem những bước quan trọng, tùy phiên bản và cấu hình. Java Flight Recorder (JFR) hỗ trợ quan sát CPU, allocation, class loading và call stacks.

Ví dụ dùng API Spring trong một ứng dụng Java có `ConfigurableApplicationContext` (snippet, không phải chương trình độc lập):

~~~java
AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext();

context.setApplicationStartup(
        new org.springframework.core.metrics.jfr.FlightRecorderApplicationStartup()
);

context.register(AopConfiguration.class);
context.refresh();
~~~

Trong ứng dụng thật, `AopConfiguration` là class đã khai báo bean và `@Aspect` cần đo. Sau khi đo xong hãy đóng context và phân tích JFR events. **Không đưa profiler vào hot path benchmark** nếu overhead của profiler chưa được kiểm soát.

### 8.3. Đếm proxy mà không kích hoạt bean ngoài ý muốn

Nếu cần rà soát Spring beans nhận proxy, có thể kiểm tra các bean **đã được tạo** ở giai đoạn context hoàn tất:

~~~java
Object bean = context.getBean("invoiceService");

System.out.println(
        org.springframework.aop.support.AopUtils.isAopProxy(bean));
System.out.println(
        org.springframework.aop.support.AopUtils.isJdkDynamicProxy(bean));
System.out.println(
        org.springframework.aop.support.AopUtils.isCglibProxy(bean));
~~~

Đây là đoạn chẩn đoán, không phải một phép đo overhead. Đừng viết profiler gọi `getBean(...)` với mọi bean name **trong lúc Spring đang đăng ký BPP**, vì chính thao tác đó có thể làm bean khởi tạo sớm và thay đổi phép đo. Trong runtime đã sẵn sàng, `getBean` có thể vẫn instantiate lazy beans, nên cũng phải biết tác động của việc inspect.

Nếu bean implement `org.springframework.aop.framework.Advised`, đọc `getAdvisors()` để biết advisor nào được gắn. **`isAopProxy=true` không chứng minh một custom aspect cụ thể được áp dụng**; proxy có thể tồn tại do transaction hoặc security.

## 9. Đặc biệt khi dùng @Around, annotation binding và pointcut

### 9.1. @Around không mặc nhiên đắt, logic bên trong mới quyết định

~~~java
@Around("execution(* demo.service.OrderService.place(..))")
public Object track(ProceedingJoinPoint pjp) throws Throwable {
    long start = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        long elapsed = System.nanoTime() - start;
        metrics.record(elapsed);
    }
}
~~~

Đây là snippet **bên trong Spring `@Aspect`** có biến `metrics` và method `record` do ứng dụng định nghĩa; không phải class biên dịch độc lập. Thời gian đo sẽ bao gồm cả phần target và những advice nằm **phía trong** aspect này theo actual advisor order; nó không có nghĩa đo “chi phí Spring AOP riêng lẻ”.

Nếu `metrics.record(...)` thực hiện network I/O đồng bộ ở mỗi call, nó có thể là chi phí chủ yếu. Trong ứng dụng production, thường muốn sử dụng in-memory aggregation/metrics backend thiết kế sẵn thay vì gửi request mạng từng invocation.

### 9.2. Pointcut quá rộng

~~~java
@Around("execution(* demo..*(..))")
public Object aroundEverything(ProceedingJoinPoint pjp) throws Throwable {
    return pjp.proceed();
}
~~~

Cú pháp này có thể match rất nhiều method trong package ứng dụng, khiến thêm bean/proxy hoặc advice chain mà không đem lại giá trị tương ứng. Tuy nhiên, “rộng” là một quyết định chức năng: audit hoặc security đôi khi **bắt buộc** bao phủ phạm vi lớn.

Nên:
- Xác định method/bean cần cross-cutting concern.
- Sử dụng type/package/annotation pointcut rõ ràng và kiểm thử pointcut bằng integration tests.
- Giữ advice body ngắn, tránh allocation/I/O khi chạy trên hot path.
- Tách advisor quan trọng khỏi instrumentation tùy chọn để có thể đo tác động mà không làm thay đổi policy nghiệp vụ.

### 9.3. Argument logging và allocation

Tránh lời gọi kiểu:

~~~java
log.info("call={} args={}",
        pjp.getSignature().toShortString(),
        java.util.Arrays.deepToString(pjp.getArgs()));
~~~

Đây là snippet giả định `log` và `pjp` tồn tại. Việc dựng chuỗi `deepToString(...)` có thể chạy **trước khi logger quyết định bỏ thông điệp** theo log level. Ngoài chi phí CPU/allocation, còn có rủi ro log PII hoặc payload lớn.

Nếu thật sự cần log, đặt guard hợp lý theo `logger.isDebugEnabled()` khi biểu thức tạo string tốn kém, chọn metadata an toàn, tránh serialize toàn bộ argument, và đo trên backend logging thực tế.

### 9.4. Exception và retry

Một interceptor `@Around` có thể catch rồi retry `proceed()`. Khi đó target và những interceptor phía trong có thể chạy nhiều lần. Benchmark phải phân biệt:
- Số method calls đến proxy;
- Số lần advice outer chạy;
- Số lần target thực thi;
- Retry attempts, success/error rate.

Không lấy một benchmark "1 advice no-op" để dự đoán cost của "transaction + retry + logging" khi họ có thể phát sinh I/O và nhân số lần target chạy.

## 10. Spring AOP trên reactive method: invocation khác subscription

Với method trả `Mono<T>` hay `Flux<T>`, advice Spring AOP ở **method invocation** có thể hoàn tất khi method trả publisher; công việc nguồn reactive được chạy ở thời điểm **subscription**. Vì vậy:
- Một stopwatch quanh `pjp.proceed()` thường đo **assembly / method return**, không đo toàn bộ reactive pipeline.
- Một publisher được subscribe hai lần không đòi `@Before` của Spring chạy hai lần nếu Java service method chỉ được gọi một lần.
- Nếu cần đo reactive request, áp dụng operator như `Mono.defer`/`Flux.defer` và `doFinally` có chủ đích trên publisher mà advice trả về.
- Không dùng `block()` trong aspect trên WebFlux hot path chỉ để đợi “đo cho đủ”; nó thay đổi thread/non-blocking behavior và có thể gây lỗi.
- Với reactive transaction, Spring transaction interceptor có semantics pipeline riêng; không áp kết quả microbenchmark method-call no-op vào tổng transaction latency.

Điều này rất quan trọng khi đưa ra kết luận hiệu năng: **số subscription, số signal và số method invocation không nhất thiết bằng nhau**.

## 11. Những yếu tố khác ảnh hưởng trực tiếp lên phép đo

| Yếu tố | Cách nó làm sai benchmark | Cách kiểm soát |
| --- | --- | --- |
| JVM warmup / JIT tiered compilation | Vòng đo đầu và vòng đo sau chênh nhiều | Dùng JMH warmup, forks, nhiều iteration |
| Dead-code elimination / constant folding | Target gần như bị loại khỏi phép đo | `Blackhole`, input thay đổi có kiểm soát |
| Inlining và escape analysis | Direct method có thể tối ưu khác proxy | Chấp nhận đó là một phần runtime thực; thử workload khác nhau |
| GC và allocation | Advice tạo object, log string hoặc context | JFR/GC profiler, đo allocation rate |
| CPU frequency / thermal throttling | ns/op dao động theo nhiệt độ/tải | Chạy máy ít background load, ghi cấu hình CPU |
| Forks và class caching | Cold class generation bị ẩn trong benchmark đã warm | Đo cold startup riêng với process mới |
| GC / logging exporter / DB | Chi phí ngoài AOP lấn át | Benchmark no-op trước, rồi benchmark production-like riêng |
| Pointcut/advisor count | Những method test có chain không giống production | Inspect advisors thực tế |
| Concurrent contention | Thread-local hoặc locks trong advice trở thành nút thắt | Thêm throughput/concurrency benchmark |
| Bean scope | Proxy construction lặp ở prototype/scoped beans | Đo đúng lifecycle đang dùng |

Trong kết quả công bố nội bộ, lưu lại **JDK, Spring, JMH, JVM flags, hệ điều hành, CPU, kích thước thread pool, GC, số advisor, loại proxy, warmup, forks và code benchmark**. Không dùng một con số "CGLIB nhanh hơn 20%" nếu thiếu điều kiện đo.

## 12. Ưu tiên tối ưu đúng thứ tự

1. **Xác nhận vấn đề thật:** request latency, CPU profile, allocation hoặc startup đã cho thấy Spring AOP là thành phần đáng kể? Nếu không, tránh tối ưu sớm.
2. **Phân biệt creation và invocation:** nếu startup chậm, đo auto-proxy scanning/bean creation; nếu hot method chậm, đo invocation/advice.
3. **Kiểm tra số proxy và advisor cần thiết:** pointcut có vô tình match hàng nghìn bean hoặc method không?
4. **Đo logic trong advice:** I/O, logging, reflection nặng, annotation resolution mỗi lần, thread-local, authorization, metrics aggregation.
5. **Giữ correctness trước performance:** đừng bỏ transaction/security/audit quan trọng chỉ để giảm một số ns/op.
6. **Giảm xử lý lặp trong advice:** cache immutable metadata khi thích hợp; không cache dữ liệu phụ thuộc runtime arguments/request/context sai scope.
7. **Chọn JDK/CGLIB theo API và ràng buộc kỹ thuật:** benchmark trên đúng JDK/hardware trước khi đổi.
8. **Dùng framework integration có sẵn cho transaction/cache/observability:** chúng xử lý lifecycle/cancellation/exception tốt hơn một custom aspect tự viết thiếu test.
9. **Benchmark lại end-to-end:** cải thiện microbenchmark không bảo đảm cải thiện p95/p99 của request thật.
10. **Tài liệu hóa proxy assumptions:** code review và test nên xác nhận các method quan trọng được gọi qua Spring proxy, không bị self-invocation/final/private bypass.

### Trường hợp optimization sai nghiêm trọng

Nếu thay `@Around` có `pjp.proceed()` bằng lời gọi trực tiếp trên target để bỏ interceptor chain, bạn có thể vô tình **bỏ qua security/transaction/cache/retry/advice khác**. Việc đánh đổi đó không tương đương refactor vô hại và có thể thay đổi dữ liệu nghiệp vụ.

Nếu một method rất nhỏ được gọi nội bộ qua `this.method()` hàng triệu lần, nó có thể không chịu overhead Spring proxy như bạn đang nghĩ, **nhưng đồng thời không nhận advice tương ứng**. Đừng suy luận từ số lần method được gọi trong code mà không biết caller có giữ proxy hay raw target.

## 13. Quy trình benchmark và báo cáo cho đội phát triển

Một báo cáo hữu ích trả lời rõ:

~~~text
Bài toán:
  Hot path: 2,000,000 method calls / giây (giá trị đo thực tế nếu có)
  Target: compute() không làm I/O
  Spring proxy: JDK hoặc CGLIB
  Advice thực tế: N advisor match compute()

Thực nghiệm:
  1. JMH directCall
  2. JMH proxy + 1 no-op interceptor
  3. JMH proxy + 3 no-op interceptors
  4. JMH proxy creation steady-state
  5. Cold/warm ApplicationContext startup (đo riêng)
  6. Benchmark production-like advice và end-to-end latency

Điều phải báo:
  ns/op hoặc us/op, error/CI, throughput, allocation
  JDK, Spring, hardware, GC, forks, warmup, workload
  Số advisor, loại proxy, version/source code

Không kết luận:
  "mỗi advice luôn thêm X ns" từ một phép thử duy nhất
  "đổi JDK sang CGLIB chắc chắn nhanh hơn" không có đối chứng
  "proxy luôn là bottleneck" khi advice chứa database/network I/O
~~~

Các con số minh họa trong đề cương điều tra **không phải kết quả benchmark**. Nếu không có lần chạy thực tế, ghi rõ “chưa đo”.

## 14. Tự kiểm tra kiến thức

**1. Spring singleton proxy được tạo bao nhiêu lần khi caller gọi method một triệu lần?**

Thông thường proxy singleton được tạo trong bean lifecycle rồi tái sử dụng; **không tạo proxy mới cho mỗi method invocation**. Một số scope/lazy/factory hoặc context refresh có thể làm instance được tạo ở thời điểm khác.

**2. Vì sao so sánh `new ProxyFactory(...).getProxy()` với `@SpringBootApplication` startup là sai?**

Phép đầu chỉ đo một phần proxy construction. Startup toàn ứng dụng còn classpath scanning, dependency injection, web server, cache, transaction, database và các hạ tầng khác.

**3. Ba advice có nghĩa overhead đúng ba lần một advice không?**

Không. Chain có thêm tầng công việc, nhưng loại advice, dynamic matching, JIT, allocation, exception và retry có thể khiến quan hệ không tuyến tính.

**4. Dùng `System.out.println` trong interceptor no-op benchmark có đúng không?**

Không. Nó làm đo chi phí console I/O/logging, không tách riêng proxy dispatch.

**5. CGLIB luôn nhanh hơn JDK proxy không?**

Không. Sự khác biệt phụ thuộc Spring/JDK, class/method, JIT và workload. Chọn proxy type theo hợp đồng API và đo trên môi trường thực tế.

**6. Vì sao một method `@Around` có thể “rẻ” trong benchmark nhưng request WebFlux vẫn chậm?**

Method có thể chỉ tạo/trả publisher; phần I/O reactive phát sinh khi subscribe. Microbenchmark method invocation không đo toàn bộ pipeline.

**7. Vì sao nên chạy JMH với warmup và fork?**

Để giảm sai lệch từ JIT compilation, class loading và state của JVM process; kết quả vẫn phải kèm error bars và thông số máy.

**8. Cách nào giảm overhead mà không làm thay đổi security/transaction semantics?**

Tối ưu logic bên trong advice, giảm log/allocations không cần thiết, làm pointcut đúng mục tiêu, chọn công cụ quan sát phù hợp, benchmark rồi xác minh end-to-end. Không bypass proxy hoặc xóa advisor quan trọng.

## 15. Tài liệu tham khảo

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB, tạo proxy và giới hạn method call.
- [Spring Framework — AOP API / ProxyFactory](https://docs.spring.io/spring-framework/reference/core/aop-api.html): ProxyFactory, advisor/interceptor chain và cấu hình.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): semantics của advice và `ProceedingJoinPoint.proceed()`.
- [Spring Framework — BeanPostProcessor extension points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html): mối liên hệ auto-proxy creator với bean creation.
- [JMH — OpenJDK Java Microbenchmark Harness](https://github.com/openjdk/jmh): warmup, forks, benchmarks, annotations và quy tắc đo microbenchmark.
- [JMH sample benchmarks](https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples): ví dụ chính thức về `@State`, `Blackhole` và method benchmark.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-performance-maintenance-002">Pointcut tĩnh, pointcut động và kiểm soát phạm vi khớp</a>

<details>
<summary>Xem chi tiết</summary>

# Pointcut tĩnh, pointcut động và kiểm soát phạm vi khớp trong Spring AOP

## 1. Mục tiêu: advice chạy đúng nơi, đúng lúc và không tạo chi phí không cần thiết

Một ứng dụng Spring có hàng trăm bean, hàng nghìn method và nhiều `@Aspect`. Nếu viết pointcut quá rộng, Spring có thể tạo thêm proxy/advisor cho các bean không cần thiết, chạy advice trên nhiều method và phải kiểm tra điều kiện khi gọi method. Nếu viết pointcut quá hẹp hoặc sai biểu thức, logic audit/metrics/cache/validation lại không được áp dụng đúng.

**Bài toán:** hiểu Spring AOP kiểm tra pointcut theo **thông tin class/method** (*static matching*) và theo **trạng thái lời gọi hoặc đối số runtime** (*dynamic matching*), sau đó thiết kế phạm vi match đủ chính xác, dễ kiểm thử và bảo trì.

Điểm cần nhớ:

- Spring AOP chỉ advice **method execution đi qua Spring AOP proxy**. Không suy diễn sang constructor, field access, call join point hoặc AspectJ weaving.
- **Static pointcut** có thể quyết định dựa trên class và chữ ký method, không cần xét giá trị tham số của từng invocation.
- **Dynamic pointcut** cần thêm bước kiểm tra lúc chạy method (thường liên quan đối số/runtime type); chi phí kiểm tra này có thể lặp lại trên mỗi invocation đã vượt qua static precheck.
- Càng thu hẹp phạm vi **class, package, bean và method** hợp lý, càng dễ tránh proxy/advice không cần thiết.
- Không đánh đồng “pointcut có viết bằng `@AspectJ`” với “toàn bộ biểu thức động”: Spring/AspectJ có thể kết hợp những điều kiện kiểm tra tĩnh và động trong cùng biểu thức.

**Phạm vi:** cơ chế matching và tối ưu pointcut **trong Spring Framework AOP**, gồm `Pointcut`, `ClassFilter`, `MethodMatcher`, `@Pointcut`, `execution`, `within`, `args`, `bean` và `ProxyFactory`. Không bàn sâu ngữ pháp AspectJ weaving ngoài những gì Spring AOP hỗ trợ.

## 2. Spring AOP pointcut gồm hai lớp lọc

Spring AOP API:

~~~java
package org.springframework.aop;

public interface Pointcut {
    ClassFilter getClassFilter();
    MethodMatcher getMethodMatcher();
}
~~~

Đây là **tóm lược đúng chữ ký API cốt lõi**, không phải source code đầy đủ của Spring. `Pointcut` kết hợp:

1. **`ClassFilter`**: class của target có nằm trong phạm vi không? Ví dụ chỉ service thuộc một tầng nghiệp vụ.
2. **`MethodMatcher`**: method trong class đó có match không? Có cần xét thêm giá trị đối số tại runtime không?

`MethodMatcher` có ba method quan trọng:

~~~java
boolean matches(Method method, Class<?> targetClass);
boolean isRuntime();
boolean matches(Method method, Class<?> targetClass, Object... args);
~~~

Ba chữ ký ở đây là **API minh họa**, không phải một Java file độc lập (cần import `java.lang.reflect.Method` và đặt vào interface/class thích hợp).

Luồng logic:

~~~text
Spring xem xét method trên target bean
          |
          v
ClassFilter.matches(targetClass)?
    |
    +-- false: pointcut loại class này
    |
    +-- true
          |
          v
MethodMatcher.matches(method, targetClass)?
    |
    +-- false: loại method
    |
    +-- true
          |
          v
MethodMatcher.isRuntime()?
    |
    +-- false: tĩnh, không kiểm tra args lại cho từng invocation
    |
    +-- true
          |
          v
Mỗi lần method được gọi qua proxy:
  matches(method, targetClass, args)?
    |
    +-- false: bỏ qua advice của pointcut này
    |
    +-- true: thực hiện advice
~~~

### Static precheck không phải runtime outcome

Khi `matches(Method, Class<?>) == false`, Spring không cần gọi matcher runtime cho invocation đó. Khi static precheck trả `true` **và** `isRuntime() == true`, Spring sẽ gọi `matches(Method, Class<?>, Object...)` khi invocation đi qua pointcut.

Spring Reference nêu rõ việc static check **có thể thực hiện khi tạo proxy để tránh kiểm tra lại mỗi invocation**. Framework có thể cache kết quả thích hợp. Không suy rằng mỗi static matcher chắc chắn chỉ được gọi đúng một lần trong toàn bộ đời JVM: số lần đánh giá còn phụ thuộc proxy, cache, method/bridge methods và cấu hình advisor. Hãy kiểm tra semantics thay vì assert count static matcher.

## 3. Pointcut tĩnh: kiểm tra một lần theo đặc điểm cố định

Ví dụ một method tên `submit` thuộc `OrderService` với chữ ký cố định, tất cả lời gọi đều cần audit, không phụ thuộc giá trị `orderId`.

Một lựa chọn Spring AOP API là `NameMatchMethodPointcut`:

~~~java
NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
pointcut.setMappedName("submit");
pointcut.setClassFilter(
    clazz -> OrderServiceImpl.class.isAssignableFrom(clazz)
);
~~~

Đây là **fragment**, cần các class của ví dụ mục 6 và import `org.springframework.aop.support.NameMatchMethodPointcut`.

`NameMatchMethodPointcut` kế thừa `StaticMethodMatcherPointcut`: matcher xác định từ **tên method** và **class**, không cần inspect giá trị đối số lúc gọi. `setClassFilter(...)` tiếp tục giới hạn phạm vi class.

**Nhược điểm của match theo tên:** nếu class có nhiều overload tên `submit` thì mọi overload tên đó là ứng viên; muốn giới hạn chữ ký cụ thể, dùng `StaticMethodMatcherPointcut` tùy chỉnh hoặc một pointcut biểu thức `execution(...)` mô tả signature chính xác hơn.

Các ví dụ static filtering hay gặp:

- `execution(public * com.example.orders.service..*.*(..))`: giới hạn public method trong package service và các package con.
- `execution(* com.example.orders.OrderApi.submit(String))`: ràng buộc method tên `submit` có một parameter kiểu `String` theo method signature.
- `within(com.example.orders.service..*)`: giới hạn target types theo package.
- `@annotation(com.example.aop.Monitored)`: lọc method có annotation phù hợp; matching annotation cần xét vị trí annotation trên interface hay implementation theo Spring proxy/version.

### Lợi thế bảo trì

Pointcut tĩnh giúp người đọc có thể thấy **đường biên class/method từ declaration**, ít điều kiện phụ thuộc runtime. Nó giảm công việc per-invocation của matching và thường dễ kiểm thử hơn dynamic matching dựa trên đối số.

Tuy nhiên **static không có nghĩa advice rẻ**: nếu pointcut match một method gọi rất thường xuyên thì logging synchronous, thao tác I/O hoặc tạo object trong advice vẫn gây overhead đáng kể. Đo thực tế mới biết điểm nghẽn.

## 4. Pointcut động: kiểm tra mỗi lời gọi sau static precheck

Giả sử tất cả `OrderService.submit(String)` đều cùng một method, nhưng chỉ các ID bắt đầu bằng `"vip-"` mới cần ghi dấu đặc biệt. Chữ ký method không cho biết giá trị truyền vào từng lần, vì vậy ta có một điều kiện runtime.

Spring cung cấp `DynamicMethodMatcherPointcut` và `DynamicMethodMatcher`:

~~~java
// Fragment: custom matcher bên trong class/ứng dụng của bạn.
class VipSubmitPointcut extends DynamicMethodMatcherPointcut {

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.getName().equals("submit");
    }

    @Override
    public boolean matches(
            Method method,
            Class<?> targetClass,
            Object... args) {
        return args.length == 1
            && args[0] instanceof String id
            && id.startsWith("vip-");
    }
}
~~~

Các import cần thiết: `java.lang.reflect.Method` và `org.springframework.aop.support.DynamicMethodMatcherPointcut`. Đây là fragment của custom matcher; code runnable đầy đủ có ở mục 6.

`DynamicMethodMatcherPointcut` có `isRuntime() == true` thông qua superclass `DynamicMethodMatcher`. `isRuntime()` là phương thức final ở đó, **không override** trong subclass. Hai-argument `matches(...)` phía trên là **static prefilter**; ba-argument `matches(...)` quyết định theo giá trị runtime.

Khi caller gọi:

~~~text
proxy.submit("normal-1") → method submit match tĩnh
                         → args không match: advice không chạy

proxy.submit("vip-2")    → method submit match tĩnh
                         → args match: advice chạy

proxy.cancel("X")        → method cancel không match tĩnh
                         → không cần kiểm tra args runtime
~~~

### Chi phí runtime và trade-off

Trong case được static precheck chấp nhận, **mỗi invocation** cần chạy matcher runtime. Nếu matcher thực hiện parse JSON, truy vấn database, đọc context đắt tiền hoặc gọi service ngoài thì chi phí có thể lớn hơn nhiều so với bản thân advice đơn giản.

Nên:

1. Thu hẹp class filter và static method precheck càng rõ càng tốt.
2. Cho runtime predicate nhẹ, ổn định và không tạo side effects không cần thiết.
3. Không log/truy vấn mạng trong matcher để quyết định match nếu chưa thật sự cần.
4. Không dựa vào giá trị có thể bị thay đổi ngoài ý muốn bởi một interceptor đứng trước trong chain.
5. Đảm bảo matcher thread-safe và đúng semantics khi nhiều request đồng thời, vì pointcut/advisor có thể dùng chung.
6. Nếu điều kiện runtime thực chất là business rule phức tạp, cân nhắc đặt check trong một advice hẹp hoặc chính service/security layer, thay vì biến pointcut thành business engine.

## 5. So sánh static và dynamic một cách chính xác

| Góc nhìn | Static `MethodMatcher` | Dynamic `MethodMatcher` |
| --- | --- | --- |
| Dữ liệu để quyết định | Method + target class | Method + target class + giá trị args mỗi invocation |
| `isRuntime()` | `false` | `true` |
| `matches(Method, Class)` | Quyết định method có match | Kiểm tra sơ bộ xem method có đáng xét runtime |
| `matches(Method, Class, args)` | Không cần gọi | Gọi khi static check đã true |
| Ví dụ | Tên/signature, class/package | Prefix của `orderId`, runtime type của một argument |
| Chi phí matching mỗi invocation | Thường không có bước check runtime | Có bước check runtime cho candidate method |
| Khả năng cấu hình | Dễ đọc, dễ review | Cần test biến thể argument và context |
| Nguy cơ | Match quá rộng/đặt sai class | Quá nhiều runtime checks, race/state/side effects |
| Ứng dụng | Phần lớn logging/metrics/transaction concern theo service method | Chỉ thực thi advice cho những invocation cụ thể cần xử lý khác nhau |

**Không suy ra mọi dynamic pointcut đều “chậm” và mọi static pointcut đều “nhanh”**. Tổng latency của một method còn có proxy dispatch, số advisors, body service, I/O, lượng dữ liệu log, tỷ lệ match và tải thread. Dynamic predicate nhẹ trên method ít gọi có thể hoàn toàn phù hợp.

## 6. Ví dụ Java 17+ runnable: hai proxy và hai kiểu pointcut

Ví dụ dùng **Spring AOP thấp tầng `ProxyFactory`**. Không cần Spring Boot hoặc một `ApplicationContext` vì mục tiêu ở đây là quan sát trực tiếp `MethodMatcher` trên **Spring AOP proxy**, không phải kiểm thử component scanning.

Có hai proxy độc lập cho cùng interface:

- Proxy `staticProxy` gắn `NameMatchMethodPointcut` chỉ match method tên `submit`. Mọi lần submit đều chạy advice.
- Proxy `dynamicProxy` gắn một `DynamicMethodMatcherPointcut` chỉ match `submit(id)` khi `id` bắt đầu bằng `vip-`.
- Cả hai proxy không advice `cancel` vì method không match static method check.

### 6.1. Cấu trúc Gradle

~~~text
spring-pointcut-matching/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/PointcutDemo.java
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-pointcut-matching'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation platform('org.springframework:spring-framework-bom:6.2.14')
    implementation 'org.springframework:spring-aop'
}

application {
    mainClass = 'demo.PointcutDemo'
}
~~~

Spring AOP dùng AOP Alliance interceptor APIs; module `spring-aop` cung cấp dependency cần thiết qua Gradle metadata/Maven transitive dependencies.

### 6.2. Java code

`src/main/java/demo/PointcutDemo.java`:

~~~java
package demo;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.DynamicMethodMatcherPointcut;
import org.springframework.aop.support.NameMatchMethodPointcut;

public class PointcutDemo {

    public interface OrderService {
        String submit(String id);
        String cancel(String id);
    }

    public static final class OrderServiceImpl implements OrderService {
        @Override
        public String submit(String id) {
            return "saved:" + id;
        }

        @Override
        public String cancel(String id) {
            return "cancelled:" + id;
        }
    }

    public static final class VipSubmitPointcut
            extends DynamicMethodMatcherPointcut {

        private final AtomicInteger checks = new AtomicInteger();

        // Static prefilter: chỉ cân nhắc method submit.
        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return method.getName().equals("submit")
                    && OrderService.class.isAssignableFrom(targetClass);
        }

        // Dynamic predicate: thực thi trên mỗi submit qua proxy.
        @Override
        public boolean matches(
                Method method, Class<?> targetClass, Object... args) {
            checks.incrementAndGet();
            String id = args.length == 1 && args[0] instanceof String s
                    ? s : "";
            System.out.println("dynamic check: " + id);
            return id.startsWith("vip-");
        }

        public int checks() {
            return checks.get();
        }
    }

    private static OrderService buildStaticProxy() {
        NameMatchMethodPointcut pointcut =
                new NameMatchMethodPointcut();
        pointcut.setMappedName("submit");
        pointcut.setClassFilter(
                clazz -> OrderService.class.isAssignableFrom(clazz)
        );

        MethodInterceptor advice = invocation -> {
            System.out.println("static advice");
            return invocation.proceed();
        };

        ProxyFactory factory = new ProxyFactory(new OrderServiceImpl());
        factory.setInterfaces(OrderService.class);
        factory.addAdvisor(new DefaultPointcutAdvisor(pointcut, advice));
        return (OrderService) factory.getProxy();
    }

    private static OrderService buildDynamicProxy(
            VipSubmitPointcut pointcut) {
        MethodInterceptor advice = invocation -> {
            System.out.println("dynamic advice");
            return invocation.proceed();
        };

        ProxyFactory factory = new ProxyFactory(new OrderServiceImpl());
        factory.setInterfaces(OrderService.class);
        factory.addAdvisor(new DefaultPointcutAdvisor(pointcut, advice));
        return (OrderService) factory.getProxy();
    }

    public static void main(String[] args) {
        OrderService staticProxy = buildStaticProxy();
        VipSubmitPointcut dynamicPointcut = new VipSubmitPointcut();
        OrderService dynamicProxy = buildDynamicProxy(dynamicPointcut);

        System.out.println("== static ==");
        System.out.println(staticProxy.submit("normal-1"));
        System.out.println(staticProxy.cancel("X"));

        System.out.println("== dynamic ==");
        System.out.println(dynamicProxy.submit("normal-2"));
        System.out.println(dynamicProxy.submit("vip-3"));
        System.out.println(dynamicProxy.cancel("X"));
        System.out.println("runtime checks: " + dynamicPointcut.checks());
    }
}
~~~

Chạy JDK 17+ trong thư mục project:

~~~bash
gradle run
~~~

Hoặc khi có Gradle Wrapper: `./gradlew run` (Linux/macOS) / `gradlew.bat run` (Windows).

**Stdout của ứng dụng** (bỏ các dòng log Gradle):

~~~text
== static ==
static advice
saved:normal-1
cancelled:X
== dynamic ==
dynamic check: normal-2
saved:normal-2
dynamic check: vip-3
dynamic advice
saved:vip-3
cancelled:X
runtime checks: 2
~~~

### 6.3. Đọc output

1. `staticProxy.submit("normal-1")`: match theo method name, nên advice in `static advice`, sau đó target trả `saved:normal-1`. Giá trị `"normal-1"` **không ảnh hưởng** static matcher.
2. `staticProxy.cancel("X")`: tên method không match, nên không có `static advice`.
3. `dynamicProxy.submit("normal-2")`: static prefilter chấp nhận `submit`, runtime predicate kiểm tra đối số và trả false, nên **không in `dynamic advice`**.
4. `dynamicProxy.submit("vip-3")`: prefilter chấp nhận method, runtime predicate trả true, advice mới chạy.
5. `dynamicProxy.cancel("X")`: static prefilter loại method, không có `dynamic check`.
6. `checks() == 2`: trong ví dụ cụ thể, `submit` được gọi hai lần qua dynamic proxy. Không assert số lượt static matcher ở proxy setup vì đó là chi tiết cache/optimizer của framework.

### 6.4. Bài học từ hai proxy

`StaticMethodMatcherPointcut`/`NameMatchMethodPointcut` phù hợp khi **mọi** lời gọi một method trong phạm vi cần cùng concern. `DynamicMethodMatcherPointcut` phù hợp khi **chỉ một phần** invocation với giá trị args khác nhau cần concern. Nhưng nếu dùng dynamic chỉ để lọc `vip-`, có thể đặt điều kiện ấy trong một advice `@Around` hẹp thay vì tự xây infrastructure. Hãy ưu tiên API cao tầng `@Aspect` khi không cần custom `MethodMatcher`.

## 7. Thu hẹp phạm vi với @AspectJ pointcut expressions của Spring

Trong Spring AOP annotation style, thiết kế pointcut thường không cần tạo `Pointcut` class thủ công. Một pointcut là expression trên `@Pointcut`, `@Before` hoặc `@Around`.

### Ví dụ tránh advice quá rộng

~~~java
@Aspect
public class MetricsAspect {

    // QUÁ RỘNG: dễ match nhiều phương thức không liên quan.
    @Before("execution(* *(..))")
    public void tooBroad() {
        // Instrumentation overhead trên rất nhiều join points.
    }
}
~~~

Đây là **fragment**, không phải ví dụ production hay file runnable độc lập. `execution(* *(..))` rộng hơn mức cần cho concern theo order service. Nó có thể ảnh hưởng những bean do container quản lý mà bạn không dự tính.

### Scope theo tầng nghiệp vụ và method

~~~java
@Aspect
public class MetricsAspect {

    @Pointcut("within(com.example.orders.service..*)")
    public void orderServiceLayer() {}

    @Pointcut(
        "execution(public * com.example.orders.service..*.*(..))"
    )
    public void publicOrderOperation() {}

    @Before("orderServiceLayer() && publicOrderOperation()")
    public void record() {
        // Ghi metrics tối giản.
    }
}
~~~

Đây là **fragment** dùng packages minh họa; muốn biên dịch cần import `org.aspectj.lang.annotation.Aspect`, `Pointcut`, `Before` và phải đăng ký thành Spring aspect bean, bật `@EnableAspectJAutoProxy` hoặc Spring Boot AOP auto-config đúng cách.

Tác dụng:

- `within(com.example.orders.service..*)`: phạm vi package/type để framework loại những target ngoài service layer.
- `execution(public * ...)`: giới hạn method execution public trong type/package và các signature.
- `&&`: **giao** của hai điều kiện; chỉ các method match cả hai mới cần advice.
- `@Pointcut` đặt tên giúp **tái sử dụng và review** một chính sách phạm vi chung giữa nhiều advice.

Không nên xem việc thêm một điều kiện tương tự nhau hai lần như “càng nhanh càng tốt”. Mục tiêu là biểu đạt **đúng phạm vi nghiệp vụ**, giúp matching được tối ưu và tránh false matches. Engine xử lý AspectJ pointcut có logic tối ưu riêng; thứ tự viết `&&` trong expression không nhất thiết là thứ tự framework thực thi các checks.

### Scope theo bean name: Spring AOP-specific

~~~java
@Before(
    "execution(* com.example.orders.service..*.*(..))"
    + " && bean(orderCommandService)"
)
public void observeOnlyOneBean() {
    // ...
}
~~~

Đây là **fragment của annotation trên method advice**. Designator `bean(name)` là **mở rộng riêng của Spring AOP**, match theo tên Spring bean/alias, không phải một designator để sử dụng như thế trong native AspectJ weaving. Có thể dùng wildcard (ví dụ `bean(*Service)`), nhưng chính sách đặt tên bean phải ổn định và nhất quán.

Khi cùng một class được đăng ký làm nhiều Spring beans, `bean(...)` giúp chọn instance/bean registration theo tên. Khi chỉ cần scope theo type/package, dùng `execution`/`within` dễ chống sai sót do đổi tên hơn.

### `this` và `target` không đồng nhất trên Spring proxy

Trong Spring AOP:

- `this(SomeInterface)`: xét **proxy object** có implement interface/type hay không.
- `target(SomeType)`: xét **target object** phía sau proxy.
- Với JDK proxy, proxy chỉ implement interfaces nên việc xét concrete class trong `this` có thể khác `target`.
- Với CGLIB class-based proxy, một số kiểu type checks có thể trùng hơn; đừng suy rằng chúng luôn tương đương.

Không dùng `this`/`target` chỉ để tối ưu nếu không cần thêm semantics. Kiểm tra rõ loại proxy đang dùng và đúng object/type cần giới hạn.

## 8. Phân biệt execution và args khi đánh giá điều kiện động

Đây là một khác biệt quan trọng trong Spring:

~~~java
// Ràng buộc CHỮ KÝ: method khai báo parameter là Serializable.
execution(* com.example..*Service.*(java.io.Serializable))

// Ràng buộc ĐỐI SỐ tại runtime: argument nhận vào
// có kiểu tương thích Serializable.
args(java.io.Serializable)
~~~

Hai dòng trên là **fragment biểu thức AspectJ**, không phải Java statement độc lập. Với một method có parameter khai báo `Object`, invocation có giá trị thực tế là `String` (implement `Serializable`) có thể match `args(java.io.Serializable)` nhưng không match expression đầu theo signature.

Các designators có thể cần runtime context:

- `args(...)`: phân biệt dựa trên actual arguments (runtime types), có thể bind argument vào advice.
- `@args(...)`: xét annotations trên runtime type của argument.
- `this(...)` và `target(...)`: xét proxy/target type (semantics có thể phụ thuộc type runtime và proxy strategy).
- `@annotation(...)`, `@within(...)`, `@target(...)`: xét annotations tại những vị trí khác nhau, có thể có các bước matching khác nhau.

**Không khẳng định mọi expression chứa các designators trên đều luôn phải kiểm tra runtime trên mọi lời gọi.** Spring/AspectJ có thể suy ra một phần từ static metadata, làm prefilter và tối ưu expression. Đúng cách là xem framework `MethodMatcher.isRuntime()` nếu dùng API thấp tầng, kiểm thử matching và profile workload.

### Không nhầm `args` type matching với kiểm tra giá trị cụ thể

`args(java.lang.String)` chỉ biểu đạt **kiểu** đối số, không tự biểu đạt “string bắt đầu bằng vip-”. Khi muốn điều kiện value-based cần đọc giá trị trong advice hoặc custom `MethodMatcher` runtime. Ví dụ:

~~~java
@Around(
    "execution(* com.example.orders.OrderApi.submit(..))"
    + " && args(orderId)"
)
public Object observeVip(
        ProceedingJoinPoint pjp, String orderId) throws Throwable {
    if (orderId.startsWith("vip-")) {
        recordVip(orderId);
    }
    return pjp.proceed();
}
~~~

**Fragment:** cần aspect bean, imports `@Around`, `ProceedingJoinPoint`, và method `recordVip`. Không dùng ví dụ này làm security enforcement nếu không bảo vệ rõ trường hợp `null`, exception và các đường gọi khác. Điều kiện trong advice không phải là **pointcut runtime match tự động**; pointcut lọc theo method/args, **advice** mới tự xét prefix.

Điều này đôi khi dễ bảo trì hơn custom dynamic matcher: ít SPI hạ tầng hơn, dễ đọc/test với unit tests. Nhưng advice vẫn có thể được invoke cho tất cả method matches, nên cần đo workload nếu frequency rất cao.

## 9. Các giới hạn của Spring AOP ảnh hưởng tối ưu matching

### Spring proxy-only, không phải native AspectJ weaving

Spring AOP chỉ xử lý **method execution** qua proxy. Không hỗ trợ các AspectJ pointcut designators như `call`, `get`, `set`, `cflow`, `if` trong expressions dùng bởi Spring proxy. Dùng chúng trong Spring AOP expression có thể gây lỗi parsing/unsupported designator.

### Self-invocation và object ngoài container

Một service đã được proxy có thể gọi `this.submit()` bên trong target. Lời gọi nội bộ đó **không đi qua proxy** nên Spring pointcut, dù static hay dynamic, đều **không được xét ở lượt gọi nội bộ**. Object tạo bằng `new` cũng không tự được Spring AOP proxy.

Đừng “tối ưu” pointcut sai cách để xử lý vấn đề self-invocation. Cần sửa đường gọi để đi qua proxy (thường tách method sang Spring bean khác), hoặc chọn cơ chế khác nếu cần join point ngoài khả năng proxy.

### Visibility, final và proxy strategy

- JDK proxies cần phương thức trên interface được expose.
- CGLIB không override `final` methods hoặc subclass `final` classes.
- `private` methods không phải phương thức có thể proxy theo cơ chế subclass.
- Kiểu proxy có thể bị quyết định bởi nhiều cấu hình AOP/transaction trong cùng context.

Một method không được intercept do giới hạn proxy **không phải lỗi static pointcut filter**. Chẩn đoán proxy/class/method trước khi thay `execution`.

## 10. Cân nhắc hiệu năng thực tế: matching là một phần của overhead

Tổng thời gian một lời gọi có thể hình dung:

~~~text
Invocation through Spring proxy
    = proxy dispatch
    + chọn advisors/chain
    + các kiểm tra dynamic match (nếu có)
    + bản thân advice (logging, allocation, I/O, metrics)
    + target method
    + post-processing/exception handling
~~~

Không phải công thức đo lường tuyệt đối; chi phí thực còn phụ thuộc JVM JIT, CPU cache, GC, threads, branch prediction, mức tải và cấu hình Spring.

Các dấu hiệu pointcut cần thu hẹp:

1. Aspect vô tình match **mọi service và repository**, trong khi concern chỉ dành cho một module.
2. Logging advice trên method rất nóng tạo I/O, synchronized appender hoặc object allocations không cần thiết.
3. Dynamic matcher có thể bị gọi hàng trăm nghìn lần/giây; runtime predicate phức tạp.
4. Nhiều advisor lặp lại cùng concern do package/bean name không rõ, dẫn đến chạy advice trùng.
5. Tính năng mới khiến thêm nhiều bean thành proxy dù logic nghiệp vụ của chúng không cần advice.
6. Pointcut biểu thức quá rộng bắt nhầm startup/management/library beans.
7. Khi test thay đổi package, không có kiểm tra xem pointcut mới match quá ít hoặc quá nhiều.

### Một chiến lược cải thiện theo thứ tự

- **Đúng nghiệp vụ trước:** định nghĩa contract rằng bao nhiêu type/method phải nhận concern.
- **Thu hẹp theo type/package:** `within(...)`, `execution(...)` hoặc `ClassFilter`.
- **Thu hẹp theo method signature hoặc annotation:** chỉ chọn public API/nhóm operation rõ ràng.
- **Chọn bean name nếu thật cần:** `bean(...)` hữu ích cho context có nhiều instances của cùng type.
- **Chỉ dùng runtime match khi cần dữ liệu runtime:** args value/type, context không thể biết từ chữ ký.
- **Đảm bảo advice rẻ:** async logging/metrics buffering phù hợp, không làm blocking I/O trong hot path.
- **Kiểm thử positive/negative boundary:** xác nhận những method không thuộc phạm vi không chạy advice.
- **Profile/baseline sau thay đổi:** đo latency, allocation, proxy count và overhead under load bằng phương pháp có warmup.

**Không optimize dựa vào số lượng ký tự trong expression.** Một pointcut ngắn như `execution(* *(..))` có thể match cực rộng; một expression dài với scoping rõ có thể tiết kiệm hơn.

## 11. Thực hành kiểm thử static/dynamic và phạm vi

Một test đáng tin cần quan sát **cả target result và advice events**. Với Spring AOP `ProxyFactory` ở mục 6, có thể đặt assertions:

~~~java
// Fragments để diễn tả các kiểm thử logic.
assertEquals("saved:normal-1", staticProxy.submit("normal-1"));
// Static advice chạy dù id không bắt đầu bằng vip-.

assertEquals("saved:normal-2", dynamicProxy.submit("normal-2"));
// Dynamic advice không chạy; matcher đã xét argument.

assertEquals("saved:vip-3", dynamicProxy.submit("vip-3"));
// Dynamic advice chạy đúng một lần.

assertEquals("cancelled:X", dynamicProxy.cancel("X"));
// Không qua runtime matcher vì method cancel bị static filter loại.
~~~

Đây là **JUnit assertion fragments**, không phải chương trình độc lập. Khi cần đo count chính xác, dùng `AtomicInteger` cho matcher/advice và tránh phụ thuộc vào số static evaluations. Trong production, kiểm thử cả proxy tạo tự động từ Spring `ApplicationContext`, không chỉ `ProxyFactory` dựng thủ công.

### Ma trận test theo phạm vi

| Test case | Kỳ vọng |
| --- | --- |
| Target thuộc package/lớp được scope, method match signature | Advice chạy |
| Target ở module/package không thuộc phạm vi | Không có advice |
| Method khác trong cùng target | Không có advice |
| Có hai bean cùng type, chỉ một bean match `bean(name)` | Advice chỉ chạy với đúng bean |
| `submit("normal")` với dynamic value predicate | Runtime check có nhưng advice không chạy |
| `submit("vip-1")` | Runtime check và advice đều chạy |
| `submit(null)` | Runtime predicate không ném lỗi bất ngờ; outcome theo contract |
| Method overload cùng tên | `NameMatchMethodPointcut` có thể match cả overload, cần kiểm tra |
| Self-invocation trong target | Không kỳ vọng Spring AOP intercept lượt gọi nội bộ |
| Bean đã bị proxy bởi concern khác | Không dùng `isAopProxy` một mình làm bằng chứng concern này |
| Đổi JDK/CGLIB proxy | Kiểm tra type/visibility, `this`/`target` semantics |
| Đổi package/refactor method | Test boundary cảnh báo pointcut bị rộng/hẹp hơn |
| Exception path | Advice tương ứng có xử lý lỗi, không làm mất exception gốc |

### Test theo semantics thay vì implementation detail

- **Không assert số lần `matches(Method, Class)` tuyệt đối**: framework có quyền evaluate/caching khác nhau.
- **Có thể assert số lần runtime matcher trên method được dùng** nếu fixture cố định rõ một proxy, một advisor, hai lượt call và framework contract như ví dụ.
- **Không assert cố định số lượng advisors** khi integration cùng transaction/caching/async vì infrastructure thêm advisor khác.
- Đảm bảo test gọi **Spring proxy**, không phải raw target.
- So sánh cả advice success và failure; Spring `@AfterReturning` nói về method return, không mặc nhiên về kết quả Future đã hoàn tất.
- Nếu nhiều threads gọi cùng proxy, matcher có state mutable phải thread-safe; counter dùng để test nên tách khỏi logic match và không cản hot path.

## 12. Khi nào không nên dùng custom dynamic matcher?

Custom `DynamicMethodMatcherPointcut` là API mạnh nhưng **ít cần thiết** cho hầu hết dự án. Ưu tiên `@AspectJ` pointcut expressions khi chỉ cần match theo type/method/annotations, hoặc điều kiện runtime đơn giản có thể đặt trong một advice đã scope đủ hẹp.

Nên cân nhắc custom dynamic matcher khi:

- Cần lắp ghép `Advisor` programmatically cho một nhóm proxy hoặc infrastructure.
- Có yêu cầu filter đối số ngay ở AOP matching layer trước khi advice chạy.
- Muốn chia sẻ pointcut runtime predicate giữa nhiều advisors.
- Có benchmark/test chỉ ra overhead và semantics chấp nhận được.

Không nên dùng dynamic matcher chỉ vì “code gọn hơn” nếu nó biến một business check phức tạp thành decision chạy trên mọi invocation. Matcher cần thuần, nhanh, thread-safe và càng ít dependency càng tốt.

**Lưu ý an toàn về phân quyền:** pointcut chọn nơi một advice chạy không tự thay thế authorization/validation ở service boundary. Các policy bảo mật phải có cơ chế enforcement/kiểm thử thích hợp, tránh dựa duy nhất vào một biểu thức pointcut có thể bị bỏ qua khi raw object/self-invocation hoặc code path khác.

## 13. Anti-pattern thường thấy

### `execution(* *(..))` cho một concern hẹp

Match quá rộng làm advice lan sang các method không liên quan. Giới hạn package/module và method hoặc annotation.

### `args(...)` để so sánh string value

`args(java.lang.String)` nói về **kiểu đối số**, không so sánh prefix/value. Dùng advice kiểm tra value hoặc dynamic matcher tùy yêu cầu.

### Matcher runtime gọi database/cache/service

Khi method quá nóng, overhead runtime có thể áp đảo business logic. Tối ưu bằng static prefilter và predicate nhẹ; nếu logic phải truy vấn external dependency thì cân nhắc responsibility của service.

### Custom matcher chỉ override `matches(Method, Class)` nhưng mong kiểm tra args

Đó là static check. Muốn có đối số runtime cần `isRuntime() == true` (dùng `DynamicMethodMatcherPointcut`) và override ba-argument `matches`. Không gọi overload ba-argument bằng tay rồi nghĩ Spring tự dùng khi `isRuntime=false`.

### Không lọc method trước runtime check

`DynamicMethodMatcherPointcut` mặc định có static precheck rộng nếu không override, nên runtime check có thể bị gọi cho các methods không cần. Override `matches(Method, Class)` để loại sớm, đồng thời dùng `ClassFilter` khi phù hợp.

### Cố dùng `call`, `get`, `set` trong Spring proxy pointcut

Đó không phải designators được Spring AOP hỗ trợ. Nếu yêu cầu vượt method execution qua proxy, phải đánh giá AspectJ weaving riêng.

### Chỉ kiểm tra proxy tồn tại

Một bean có thể là AOP proxy do advisor khác. Cần assert **advice cụ thể** chạy trên method thuộc scope, không chạy ở method ngoài scope.

### Dynamic condition dựa trên state không ổn định

Nếu matcher đọc mutable singleton/global state mà không thread-safe, hai request có thể nhận kết quả khác nhau. Không giả định Spring tự đồng bộ custom matcher.

### Chọn `bean(name)` trong app luôn đổi tên bean

Điều kiện match dựa vào **bean name**, nên refactor tên/config có thể âm thầm làm advice biến mất. Dùng type/package/annotation nếu tên không phải chính sách nghiệp vụ ổn định.

## 14. Checklist review và tối ưu Spring pointcut

- [ ] Chỉ áp dụng Spring AOP cho Spring bean và invocation đi qua proxy.
- [ ] Xác định loại proxy JDK/CGLIB, visibility và class/method `final`.
- [ ] Có scope theo module/package/type và method signature rõ ràng.
- [ ] `ClassFilter` bỏ được target classes ngoài phạm vi.
- [ ] Static `matches(Method, Class)` loại được methods không liên quan.
- [ ] Dynamic predicate chỉ dùng khi thật sự phải đọc runtime context/args.
- [ ] Không nhầm `execution(signature)` với `args(runtimeType)`.
- [ ] Không dùng `bean(name)` nếu chính sách không dựa trên Spring bean identity.
- [ ] `@Pointcut` fragments được đặt tên có nghĩa và tái sử dụng được.
- [ ] Khi dùng annotation matcher, kiểm thử annotation trên interface/implementation thực tế.
- [ ] Có positive/negative tests cho package, method, value, overload và proxy strategy.
- [ ] Matcher/advice có thread safety phù hợp.
- [ ] Không nhầm static match cost với body advice/IO cost.
- [ ] Có benchmark hoặc profile trước/sau nếu tối ưu hiệu năng.
- [ ] Đảm bảo refactor package/class/method không vô tình đổi phạm vi.
- [ ] Không dùng pointcut như hàng rào bảo mật duy nhất cho mọi call path.

**Kết luận:** Với Spring AOP, **pointcut tĩnh** có thể được quyết định từ class và method metadata; **pointcut động** cần thêm kiểm tra runtime cho invocation đã vượt qua static prefilter. Mục tiêu không phải biến mọi pointcut thành tĩnh mà là **giới hạn phạm vi match đúng nghiệp vụ**, giữ runtime predicate thật nhẹ và đo **hiệu năng của toàn bộ proxy + advice**, không chỉ nhìn cấu trúc biểu thức.

## Tài liệu Spring chính thức

- [Spring Framework Reference — Pointcut API in Spring](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/pointcuts.html)
- [Spring Framework Reference — Declaring a Pointcut (`@AspectJ` style)](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html)
- [Spring Framework Reference — Creating AOP Proxies Programmatically](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/prog.html)
- [Spring Framework 6.2.14 Javadoc — MethodMatcher](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/MethodMatcher.html)
- [Spring Framework 6.2.14 Javadoc — DynamicMethodMatcherPointcut](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/DynamicMethodMatcherPointcut.html)
- [Spring Framework 6.2.14 Javadoc — StaticMethodMatcherPointcut](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/StaticMethodMatcherPointcut.html)
- [Spring Framework 6.2.14 Javadoc — NameMatchMethodPointcut](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/NameMatchMethodPointcut.html)

Khi áp dụng vào hệ Spring Framework/Boot đang chạy, kiểm chứng phiên bản API, proxy strategy, classpath/auto-proxy configuration và profile thực tế trước khi kết luận một pointcut đã được tối ưu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-performance-maintenance-003">Đo đạc bằng thử nghiệm có bean proxy và đường gọi đối chứng</a>

<details>
<summary>Xem chi tiết</summary>

# Đo đạc bằng thử nghiệm có bean proxy và đường gọi đối chứng trong Spring AOP

## 1. Câu hỏi đo lường: proxy + advice tạo thêm bao nhiêu chi phí?

Trong Spring AOP, chỉ đo một lời gọi vào bean proxy **không cho biết overhead do proxy**. Bài này thiết kế cặp thử nghiệm có kiểm soát: **cùng một target, cùng method, input và trạng thái**, nhưng một đường gọi đi qua Spring proxy có `@Around` và đường còn lại gọi trực tiếp target. Chênh lệch quan sát được thuộc về **hai đường thực thi cụ thể**, không phải hằng số overhead cho mọi cấu hình Spring.

Mục tiêu riêng của bài là kiểm chứng đối chứng dùng *target nằm bên dưới chính proxy*, thay vì `new` một service khác; đảm bảo proxy thật sự có advisor đang cần đo, rồi đo bằng JMH có warmup/forks/Blackhole. Vì control cố ý bỏ qua advice, **không dùng nó trong production** nếu advice cung cấp transaction, authorization hoặc policy bắt buộc. Đường JMH runnable và các giới hạn so sánh nằm ở mục 7 trở đi.

## 2. Khái niệm "đối chứng" phải định nghĩa rõ

Với một service bean có Spring `@Aspect`, các lựa chọn thường thấy:

| Đường gọi | Đi qua Spring AOP proxy? | Đi qua custom advice? | Giá trị để so sánh |
| --- | --- | --- | --- |
| `proxy.work(...)` với advisor match | Có | Có | Đường chính cần đo |
| `target.work(...)` trên cùng target từ proxy | Không | Không | Đối chứng bỏ qua AOP |
| `new WorkServiceImpl().work(...)` | Không | Không | Đối chứng đơn giản, nhưng có thể khác instance và dependency |
| Một Spring bean **không match advisor** | Thường không có proxy do advisor đang xét | Không | So sánh được nếu kiểm soát class/state/dependencies, nhưng dễ không cùng target |
| `ProxyFactory` với no-op `MethodInterceptor` | Có | Có interceptor đơn giản | Đo gần hơn phần dispatch proxy, **không tương đương** Spring @Aspect đầy đủ |
| `this.work(...)` trong cùng target | Không đi qua proxy ở lượt nội bộ | Không cho lần self-invocation | Phép đối chứng hành vi proxy bypass, không nhất thiết là benchmark công bằng |

**Lựa chọn chính trong bài:** lấy **target bên trong chính Spring proxy** rồi đo cả hai reference. Điều này làm việc so sánh chặt chẽ hơn so với hai bean tạo mới có trạng thái khác nhau.

Tuy nhiên, đường gọi direct **không phải lời gọi nghiệp vụ mà ứng dụng nên dùng trong production** khi advice chứa validation, security, transaction hoặc policy bắt buộc. Đọc target trực tiếp ở đây chỉ dành cho **phép thử kiểm soát**.

## 3. Lấy proxy và target đúng cách trong Spring

Trong Spring AOP, `AopUtils.isAopProxy(bean)` cho biết reference có phải Spring AOP proxy hay không. Với những proxy cho phép truy cập `Advised`, có thể đọc advisor chain và `TargetSource`:

```java
WorkService bean = context.getBean(WorkService.class);
if (!AopUtils.isAopProxy(bean)) {
    throw new IllegalStateException("Expected Spring AOP proxy");
}
if (!(bean instanceof Advised advised)) {
    throw new IllegalStateException("Cannot inspect proxy");
}
WorkService target = (WorkService) advised.getTargetSource().getTarget();
```

Đây là **snippet chẩn đoán** (cần `ApplicationContext context`, imports và một `WorkService` có thật). `TargetSource.getTarget()` có thể ném exception; một số target sources có vòng đời/phạm vi động và yêu cầu `releaseTarget`. Đoạn trên phù hợp với **singleton target source** mà project ví dụ cố ý tạo, **không** nên áp dụng máy móc cho prototype/scoped/pooling proxies.

Hãy kiểm tra:

- `AopUtils.isAopProxy(bean) == true`.
- Proxy exposes đúng interface hoặc class cần gọi.
- Proxy có các `Advisor` thực tế. `getAdvisors()` liệt kê advisor của proxy nhưng **không có nghĩa mọi advisor chạy trên mọi method**.
- Target reference khác proxy reference nhưng triển khai cùng method.
- Với input kiểm chứng, `proxy.work(...)` và `target.work(...)` trả **cùng kết quả** khi advice chỉ quan sát/chuyển tiếp. Nếu advice cố tình đổi đối số/kết quả, phép so sánh phải được định nghĩa lại.

Trong Spring, JDK dynamic proxy thường được chọn nếu target implements interface và không ép dùng class proxy; class-based proxy (CGLIB) có đặc trưng khác. Không gộp kết quả hai loại proxy rồi kết luận chi phí "Spring AOP" nói chung.

## 4. Đo đúng thứ gì?

Trước khi viết một benchmark, tách các thành phần chi phí:

```text
T_direct = target business computation + Java call mechanics

T_proxy  = Spring proxy dispatch + matching advisor/interceptor calls
           + custom @Around/proceed chain + target business computation
           + Java call/return mechanics

Hiệu chênh lệch quan sát:
Delta = T_proxy - T_direct
```

`Delta` là **hiệu số thực nghiệm của hai đường gọi cụ thể**, không nhất thiết bằng một hằng số "proxy cost". JVM có thể inline direct call tốt hơn proxy call; JIT và profile của bytecode có thể khác nhau. Điều này là một phần của đường gọi thật, nhưng phải diễn giải khi muốn quy kết nguyên nhân.

Một custom `@Around` chỉ `return pjp.proceed()` là phép thử hữu ích để thấy chi phí của việc **Spring proxy + @Aspect advice chain cơ bản**. Nó không đo riêng một đối tượng proxy không có advice. Nếu aspect thực tế ghi log JSON, gọi network, kiểm tra security, sửa tham số hoặc bắt exception, tổng chi phí có thể cao hơn rất nhiều.

Với công việc dài hàng millisecond như database hoặc HTTP, overhead dispatch có thể nhỏ theo tỉ lệ phần trăm; với một getter hay helper chỉ tốn vài nanosecond, nó có thể chiếm tỉ lệ lớn. **Không được suy ra** rằng mọi ứng dụng sẽ chậm theo cùng một tỷ lệ từ một microbenchmark.

## 5. Vì sao đo một lời gọi bằng System.nanoTime() không đủ?

`System.nanoTime()` có thể hỗ trợ tracing một request cụ thể, nhưng **không đủ để kết luận microbenchmark overhead của Spring AOP**: lần đầu gọi còn classloading/JIT/warmup; lịch CPU, GC, optimizer inlining và dead-code elimination làm kết quả khác nhau. `System.out.println()` ngay trong hot path thậm chí có thể đắt hơn proxy dispatch. Nếu thiếu *direct-call control*, cùng workload, lặp warmup/measurement và error estimate, một con số `ns` không chứng minh Spring AOP chậm ở đâu.

Vì vậy dùng JMH cho phép đo ở mục 7; dùng profiler, production metrics và latency tail để đối chiếu trước khi đưa quyết định tối ưu. Kết quả benchmark được báo cáo theo môi trường/version, **không bịa số đo**.

## 6. Thiết kế phép thử: các biến cần cố định

| Thành phần | Cách làm trong ví dụ | Lý do |
| --- | --- | --- |
| JVM/JDK | Java 17, cùng JVM flags cho hai benchmark | Tránh biến thiên runtime |
| Spring | Spring Framework 6.2.19, cùng dependency versions | Tránh biến thiên proxy implementation |
| Aspect | Một `@Around` chỉ gọi `proceed()` | Đo chi phí interception tối thiểu |
| Target | Cùng một singleton target phía sau proxy | Tránh state/dependency khác biệt |
| Workload | `compute(input, rounds)`, `rounds` có hai mức | Thấy tác động khi logic ngắn hoặc dài hơn |
| Input | Giá trị biến đổi qua các invocation | Giảm constant folding |
| Kết quả | JMH `Blackhole.consume(...)` | Tránh loại bỏ phép tính không dùng |
| Spring startup | Chỉ ở `@Setup(Level.Trial)` | Không tính tạo ApplicationContext/proxy vào ns/op |
| Chạy thử | JMH warmup/measurement/forks | Cho JVM tối ưu và có số mẫu |
| Threading | Ban đầu `-t 1`, `@State(Scope.Thread)` | Không lẫn contention khi đo interception cơ bản |
| Output | ns/op + error estimate, JSON | Có thể so sánh minh bạch và tái lập |
| Trách nhiệm đo | Direct vs proxy through same method signature | Tránh vô tình đo 2 algorithm khác nhau |

Giữ nguyên các biến trong bảng khi chạy A/B; chỉ thay **đường gọi** ở hai benchmark methods. Nếu muốn đo logging, security, transaction, caching, async hoặc thêm advisor, đó là một **thí nghiệm khác** với baseline phù hợp.

## 7. Ví dụ Java 17 + Maven + JMH: cùng target, hai đường gọi

Dưới đây là một project độc lập dùng:

- Spring Framework `6.2.19`.
- `@EnableAspectJAutoProxy` để tạo Spring AOP proxy theo interface.
- Một aspect đơn giản `@Around` truyền tiếp nguyên trạng.
- JMH `1.37` để đo `directTarget` và `viaSpringProxy`.
- Maven compiler annotation processor của JMH và Maven Shade plugin đóng gói benchmark runnable JAR.
- Một chương trình `Verify` để kiểm tra proxy/target và output tương đương **trước khi benchmark**.

**Không cần AspectJ CTW hoặc LTW**; đây là Spring AOP proxy. `aspectjweaver` chỉ hỗ trợ @AspectJ-style pointcut runtime theo nhu cầu Spring AOP.

### 7.1. Cấu trúc project

```text
spring-proxy-benchmark/
  pom.xml
  src/main/java/demo/
    WorkService.java
    DefaultWorkService.java
    PassThroughAspect.java
    BenchConfig.java
    Verify.java
    SpringProxyBenchmark.java
```

### 7.2. pom.xml đầy đủ

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-proxy-benchmark</artifactId>
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
      <version>1.9.24</version>
    </dependency>
    <dependency>
      <groupId>org.openjdk.jmh</groupId>
      <artifactId>jmh-core</artifactId>
      <version>1.37</version>
    </dependency>
  </dependencies>

  <build>
    <finalName>benchmarks</finalName>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
        <configuration>
          <annotationProcessorPaths>
            <path>
              <groupId>org.openjdk.jmh</groupId>
              <artifactId>jmh-generator-annprocess</artifactId>
              <version>1.37</version>
            </path>
          </annotationProcessorPaths>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.6.0</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals>
              <goal>shade</goal>
            </goals>
            <configuration>
              <createDependencyReducedPom>false</createDependencyReducedPom>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>org.openjdk.jmh.Main</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

`jmh-generator-annprocess` tạo các benchmark harness classes và danh sách benchmark khi Maven compile. `jmh-core` chứa runtime và annotations; Maven Shade tạo `target/benchmarks.jar` có entrypoint `org.openjdk.jmh.Main`. Nếu annotation processing bị IDE/build profile vô hiệu hóa, JMH sẽ không tìm thấy benchmark; hãy kiểm tra compile log và generated sources. Các dependency versions trong ví dụ được pin để tái lập, không khẳng định đây là các version mới nhất của mọi thư viện.

### 7.3. WorkService.java

`src/main/java/demo/WorkService.java`:

```java
package demo;

public interface WorkService {
    long compute(int input, int rounds);
}
```

Chủ động dùng interface để Spring tạo **JDK dynamic proxy** trong config mặc định. Method trả `long` để benchmark có một output thật phải được tiêu thụ. Không dùng method `void` với thân rỗng, vì phép đo sẽ rất dễ bị tối ưu lệch.

### 7.4. DefaultWorkService.java

`src/main/java/demo/DefaultWorkService.java`:

```java
package demo;

public class DefaultWorkService implements WorkService {
    private volatile long salt = 17;

    @Override
    public long compute(int input, int rounds) {
        long value = input + salt;
        for (int i = 0; i < rounds; i++) {
            value = Long.rotateLeft(
                value * 1_664_525L + 1_013_904_223L, 7
            );
        }
        return value;
    }
}
```

`salt` là volatile field để có một giá trị instance thực sự được đọc. `input` thay đổi theo lượt gọi trong JMH. Vòng tính toán không truy cập DB/network hay log, cho phép quan sát interception overhead khi target workload nhỏ và vừa.

Đây là **một synthetic workload**, không đại diện cho độ trễ tất cả service thực tế. Java `long` overflow được xác định theo two's complement arithmetic; đoạn tính toán vẫn cho cùng kết quả ở direct và proxy với input/rounds bằng nhau.

### 7.5. PassThroughAspect.java

`src/main/java/demo/PassThroughAspect.java`:

```java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class PassThroughAspect {
    @Around("execution(* demo.DefaultWorkService.compute(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed();
    }
}
```

Aspect không log, không allocate collection, không thực hiện I/O, không cố ý thay đổi args/return value và không nuốt exception. Như vậy đường `viaSpringProxy` đo chi phí **proxy + Spring advice dispatch + around proceed** thay vì đo chi phí println hoặc serialization.

Method target `compute` của implementation cụ thể khớp pointcut Spring AOP; dù client gọi qua `WorkService` JDK proxy, Spring sử dụng target class để xác định matching method khi cần. Để xác minh không cấu hình nhầm, bước `Verify` kiểm tra proxy và advisor chain.

### 7.6. BenchConfig.java

`src/main/java/demo/BenchConfig.java`:

```java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class BenchConfig {

    @Bean
    WorkService workService() {
        return new DefaultWorkService();
    }

    @Bean
    PassThroughAspect passThroughAspect() {
        return new PassThroughAspect();
    }
}
```

Cả service target và aspect là Spring-managed beans. Không dùng component scanning để tránh phụ thuộc package/scan config trong một benchmark nhỏ. `proxyTargetClass=false` không bắt buộc Spring dùng JDK proxy trong mọi hoàn cảnh, nên bước kiểm chứng vẫn dùng `AopUtils.isJdkDynamicProxy` thay vì giả định.

### 7.7. Verify.java: kiểm tra đúng môi trường AOP

`src/main/java/demo/Verify.java`:

```java
package demo;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Verify {
    public static void main(String[] args) throws Exception {
        try (var context =
                new AnnotationConfigApplicationContext(BenchConfig.class)) {
            WorkService bean = context.getBean(WorkService.class);

            if (!(bean instanceof Advised advised)) {
                throw new IllegalStateException("Bean is not an Advised proxy");
            }

            Object targetObject = advised.getTargetSource().getTarget();
            WorkService target = (WorkService) targetObject;

            if (advised.getAdvisors().length == 0) {
                throw new IllegalStateException("Advisor chain is empty");
            }

            long viaProxy = bean.compute(42, 8);
            long direct = target.compute(42, 8);

            System.out.println("springProxy=" + AopUtils.isAopProxy(bean));
            System.out.println("jdkProxy=" + AopUtils.isJdkDynamicProxy(bean));
            System.out.println("sameReference=" + (bean == target));
            System.out.println("sameResult=" + (viaProxy == direct));
            System.out.println("advisorPresent=true");
        }
    }
}
```

Đây là một phép kiểm tra **functional sanity**, không phải benchmark. Chạy từ project root:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.Verify
```

Lệnh trên sử dụng Maven Exec plugin (có thể cần gọi goal có tọa độ đầy đủ nếu project chưa khai báo plugin):

```bash
mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.5.1:java -Dexec.mainClass=demo.Verify
```

Dòng do chương trình in **dự kiến** khi cấu hình như project:

```text
springProxy=true
jdkProxy=true
sameReference=false
sameResult=true
advisorPresent=true
```

Giải thích:

- `springProxy=true`: Spring thực sự tạo AOP proxy.
- `jdkProxy=true`: benchmark đang kiểm tra JDK proxy, không phải CGLIB.
- `sameReference=false`: proxy và target là hai object reference khác nhau.
- `sameResult=true`: pass-through advice không đổi kết quả cho input kiểm tra.
- `advisorPresent=true`: `Advised` chứa advisor. Thông tin này giúp loại trừ phép đo một bean không hề được proxy, nhưng nếu cần kiểm chứng advice class cụ thể, phải inspect loại advisor/pointcut thay vì chỉ đếm số lượng.

**Cảnh báo quan trọng:** `getTargetSource().getTarget()` ở đây dùng với singleton target của `BenchConfig`. Với target source có scope động, pooling/prototype hoặc resource lifecycle đặc biệt, phải thiết kế cách đối chứng riêng và bảo đảm release target phù hợp; không truy cập target raw trong logic production.

### 7.8. SpringProxyBenchmark.java

`src/main/java/demo/SpringProxyBenchmark.java`:

```java
package demo;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SpringProxyBenchmark {

    @Param({"8", "128"})
    public int rounds;

    private AnnotationConfigApplicationContext context;
    private WorkService proxied;
    private WorkService direct;
    private int input;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(BenchConfig.class);
        proxied = context.getBean(WorkService.class);

        if (!AopUtils.isJdkDynamicProxy(proxied)) {
            throw new IllegalStateException("Expected JDK Spring AOP proxy");
        }
        if (!(proxied instanceof Advised advised)) {
            throw new IllegalStateException("Proxy must expose Advised");
        }
        if (advised.getAdvisors().length == 0) {
            throw new IllegalStateException("No advisor on benchmark proxy");
        }

        direct = (WorkService) advised.getTargetSource().getTarget();
        if (direct == proxied || direct == null) {
            throw new IllegalStateException("Invalid target for control path");
        }

        long a = proxied.compute(42, rounds);
        long b = direct.compute(42, rounds);
        if (a != b) {
            throw new IllegalStateException("Different results");
        }

        input = 0;
    }

    @Benchmark
    public void directTarget(Blackhole bh) {
        bh.consume(direct.compute(++input, rounds));
    }

    @Benchmark
    public void viaSpringProxy(Blackhole bh) {
        bh.consume(proxied.compute(++input, rounds));
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (context != null) {
            context.close();
        }
    }
}
```

**Vì sao code này đo được hai đường có đối chứng?**

- `context` được tạo ở `@Setup(Level.Trial)`, nên chi phí startup Spring và tạo proxy **không** nằm trong mỗi phép đo method call.
- `proxied` là bean do Spring trả về. `direct` được lấy từ `TargetSource` của **chính proxy đó**, nên target instance và cấu hình nghiệp vụ là cùng một object.
- Một phương thức `compute` được gọi trong cả hai nhánh, với cùng `rounds` parameter và input biến đổi tương tự.
- `Blackhole.consume` làm kết quả phép tính có ý nghĩa với optimizer, giảm khả năng toàn bộ work bị loại bỏ.
- `@State(Scope.Thread)` giữ state riêng theo benchmark thread; bắt đầu với `-t 1` để không lẫn contention.
- `@Param({"8","128"})` chạy riêng hai mức workload, giúp xem khi target làm ít/nhiều việc thì tỷ trọng AOP overhead thay đổi ra sao.
- `@TearDown` đóng Spring ApplicationContext sau thử nghiệm, không tính vào method-call latency.

JMH có thể tạo các bộ input theo trình tự khác nhau giữa hai benchmark methods do mỗi method chạy trong trial riêng. Ta đang giữ cách sinh input **tương đương**, không hứa hai phương pháp sẽ thấy đúng cùng dãy tại mọi timestamp/fork. Nếu business method có cache, random state, side effects hoặc I/O, cần fixture/seed và isolation chặt hơn.

### 7.9. Build và chạy JMH

Dùng JDK 17+ và Maven từ thư mục có `pom.xml`:

```bash
mvn -q -DskipTests package
java -jar target/benchmarks.jar "demo.SpringProxyBenchmark.*" \
  -wi 4 -i 6 -w 2s -r 2s -f 2 -t 1 \
  -rf json -rff target/jmh-results.json
```

Dòng có dấu `\` continuation ở trên là kiểu shell Unix. Trong PowerShell hoặc CMD, có thể đặt toàn bộ lệnh trên một dòng:

```text
java -jar target/benchmarks.jar "demo.SpringProxyBenchmark.*" -wi 4 -i 6 -w 2s -r 2s -f 2 -t 1 -rf json -rff target/jmh-results.json
```

Ý nghĩa:

- `-wi 4`: bốn warmup iterations trước khi lấy kết quả.
- `-i 6`: sáu measurement iterations.
- `-w 2s` và `-r 2s`: thời gian warmup/measurement mỗi iteration.
- `-f 2`: hai fork JVM độc lập, giảm ảnh hưởng lịch sử tối ưu hóa một process.
- `-t 1`: một benchmark thread để nghiên cứu overhead cơ bản trước.
- `-rf json -rff ...`: lưu raw summary của JMH ở định dạng JSON để so sánh/audit.

Nếu chỉ muốn xác thực benchmark được phát hiện và setup chạy trước, hãy chạy một cấu hình **smoke test** ngắn hơn, nhưng **không dùng kết quả smoke test làm kết luận hiệu năng**:

```text
java -jar target/benchmarks.jar "demo.SpringProxyBenchmark.*" -wi 1 -i 1 -w 1s -r 1s -f 1 -t 1
```

Muốn xem các benchmarks được compiler annotation processor đăng ký:

```bash
java -jar target/benchmarks.jar -l
```

Nếu danh sách trống hoặc JMH báo không tìm thấy benchmark, kiểm tra `jmh-generator-annprocess` được chạy trong compile, synthetic generated benchmark code và packaging của JAR.

## 8. Đọc kết quả JMH mà không tự bịa số đo

JMH thường tạo bảng với các cột:

| Cột JMH | Ý nghĩa |
| --- | --- |
| `Benchmark` | Tên phương thức, ví dụ `directTarget` hoặc `viaSpringProxy` |
| `(rounds)` | Parameter workload, ví dụ `8` và `128` |
| `Mode` | `avgt`: average time |
| `Cnt` | Số measurement observations JMH báo |
| `Score` | Estimate thời gian trung bình cho một operation |
| `Error` | Khoảng sai số mà JMH tính theo mẫu/thiết lập |
| `Units` | Đơn vị, ở đây `ns/op` |

**Không có con số benchmark mặc định.** Kết quả phụ thuộc CPU, JDK build, OS, JIT, thermal profile, agent/profilers và cả những tiến trình khác đang chạy. Chỉ sau khi chạy thật mới được ghi giá trị `Score`, `Error` và so sánh định lượng.

Với mỗi `rounds`, khi cả hai mode `avgt` được đo bằng `ns/op`:

```text
absolute observed difference = proxyScore - directScore

relative observed increase (%) =
    (proxyScore / directScore - 1) * 100
```

Ví dụ cách đọc, **không phải kết quả benchmark thực tế**:

- Nếu `proxyScore` lớn hơn `directScore` đáng kể so với noise, đường proxy + pass-through advice đắt hơn đường target trực tiếp **trong workload đó**.
- Nếu khoảng sai số chồng lấn lớn hoặc chênh lệch rất nhỏ, chưa đủ cơ sở kết luận có khác biệt ổn định.
- Nếu `rounds=128` làm nghiệp vụ lâu hơn, percentage overhead có thể nhỏ lại ngay cả khi absolute interception cost vẫn tồn tại.
- Nếu `proxyScore` bất ngờ nhỏ hơn direct, cần xem JIT profile/inlining, state/input distribution, variance, benchmark harness và artifacts trước khi tuyên bố AOP tăng hiệu năng.

Phép trừ hai giá trị score chỉ là **estimated contrast**, không đi kèm tự động một confidence interval chính xác cho hiệu số. Muốn so sánh thống kê chặt hơn, chạy nhiều fork độc lập, lưu per-iteration data và dùng phương pháp phân tích có xét phân phối/độ nhiễu.

## 9. Đo allocation và chi phí advisor phức tạp

Ngoài thời gian CPU, một Spring `@Around` có thể gây thêm allocation cho invocation context, arguments array, boxing hoặc logging. JMH có profiler `gc`:

```text
java -jar target/benchmarks.jar "demo.SpringProxyBenchmark.*" -wi 4 -i 6 -w 2s -r 2s -f 2 -t 1 -prof gc
```

Profiler có thể báo `gc.alloc.rate.norm` theo bytes/op và các chỉ số GC tùy JVM. `-prof gc` cũng có instrumentation cost, vì vậy nên so sánh có/không profiler, không trộn các runs như thể cùng điều kiện. Nếu thấy allocation, có thể dùng Java Flight Recorder hoặc profiler thích hợp để tìm hot allocations, nhưng đừng vội quy mọi byte/op cho `ProxyFactory`; phải xem call graph và code advice.

Để đo **số lượng advisors**, có thể tạo các cấu hình benchmark riêng:

1. Bean trực tiếp không proxy.
2. Proxy có một no-op `MethodInterceptor`.
3. Proxy có một Spring `@Aspect` pass-through.
4. Proxy có nhiều advisors theo đúng thứ tự của ứng dụng.
5. Proxy có logging hoặc security/transaction/caching thực sự.

Các mức này là **những thí nghiệm khác nhau**, phải kiểm tra cùng target state, cùng method signature, cache state và policy. Không tự thêm logging vào benchmark chính rồi vẫn gọi kết quả là "chi phí proxy tối thiểu".

## 10. Kiểm soát workload và tránh tối ưu JIT gây hiểu sai

### 10.1. Vì sao code có vòng lặp và volatile?

Nếu method chỉ `return 1;`, compiler có thể tối ưu mạnh đến mức đường gọi trực tiếp gần như biến mất, trong khi proxy không được inline cùng cách. Kết quả vẫn mô tả một case cực nhỏ, nhưng dễ gây diễn giải quá mức.

Trong `DefaultWorkService`:

- `salt` là field volatile có giá trị thuộc target instance.
- `input` thay đổi qua các invocation.
- `rounds` được JMH chọn theo parameter, tránh hardcode một workload duy nhất.
- Kết quả được đưa vào `Blackhole` để JMH chống dead-code elimination.

Đây không bảo đảm JVM sẽ tối ưu hai đường như nhau; thực tế proxy dispatch khác call trực tiếp. Benchmark đo đúng **hai đường gọi thật**, sau đó người đọc phải giải thích đó là phí AOP + khác biệt JIT path chứ không là "mỗi advisor luôn tốn N ns".

### 10.2. Không cho phép I/O trong hot path của phép đo overhead thuần

`System.out.println()`, logging JSON, DB, HTTP, Redis, file I/O và `Thread.sleep` có thể che hoàn toàn chi phí Spring proxy. Nếu cần nghiên cứu observability logging aspect, tạo benchmark riêng có đúng logging backend/level/appenders, xác định async logging và output destination.

### 10.3. Warmup cần phản ánh JIT state

Hai phương thức benchmark được JMH chạy trong các trial riêng. Số lượt warmup phải đủ để JIT compile những đường quan trọng và profile ổn định. Kiểm tra kết quả của từng iteration/fork; nếu score trôi dần qua các iteration, cần tăng warmup hoặc điều tra tiered compilation/deoptimization.

### 10.4. Fork cần xử lý ảnh hưởng toàn JVM

Chạy nhiều forks giúp hạn chế profile contamination của một JVM, nhưng mỗi fork vẫn chịu tác động OS và hardware. Đặt benchmark trên máy tương đối rảnh, cố định JDK/OS/CPU power settings khi có thể và tránh CI runner bị noisy neighbors nếu cần quyết định performance nghiêm túc.

## 11. Đừng lẫn overhead với cơ chế short-circuit

Spring AOP proxy không luôn tiếp tục tới target. Một số advisors có thể thay đổi flow:

| Advisor/concern | Điều có thể xảy ra | Hệ quả khi benchmark |
| --- | --- | --- |
| `@Cacheable` | Cache hit trả kết quả trước target | Đường proxy có thể **nhanh hơn** vì bỏ qua expensive computation |
| `@Transactional` | Begin/join/commit/rollback và resource coordination | Đo cả transaction manager và tài nguyên, không còn chỉ proxy overhead |
| Method security | Kiểm tra quyền hoặc từ chối lời gọi | Phải đảm bảo cùng security context và permitted invocation |
| Retry advice | Gọi target nhiều lần khi lỗi | Throughput/latency chứa retry attempts |
| `@Async` | Trả future/dispatch sang thread khác | Thời gian method return không bằng completion latency |
| Custom `@Around` | Nuốt exception hoặc không gọi `proceed()` | Có thể benchmark một path **không hề thực hiện nghiệp vụ** |

Ví dụ `@Cacheable` có cache hit làm method target không chạy. Nếu lấy direct target làm control, direct luôn tính toán còn proxy trả cached value, việc proxy nhanh hơn **không chứng minh Spring AOP interception âm chi phí**; đây là hai contract khác nhau. Muốn đo cache overhead, cần so cache miss/hit cùng key policy, và tách cost provider/cache operations.

Trong phép đo chính của bài, aspect `PassThroughAspect` chỉ gọi `proceed()` đúng một lần để tránh điều này. Việc `sameResult=true` ở setup là một kiểm tra cần thiết, nhưng với side-effectful workload còn phải test số lần target được thực thi.

## 12. Nếu benchmark nhiều @Aspect và @Order

Spring sắp advisors theo precedence: `@Order` có số nhỏ hơn thường có precedence cao hơn, nằm ngoài khi đi vào chuỗi. Với hai `@Around` `@Order(10)` và `@Order(20)`:

```text
@Order(10): before
  @Order(20): before
    target
  @Order(20): after
@Order(10): after
```

Khi benchmark nhiều advice:

- Ghi rõ số advisors và order.
- Dùng `Advised.getAdvisors()` xác nhận **chuỗi advisor thực tế**.
- Không dựa vào thứ tự component scan hoặc thứ tự khai báo advice methods.
- Test behavior trước: cùng return value, cùng exception contract và số lần target chạy.
- Đo riêng từng cấu hình, rồi mới tính incremental overhead tương đối.
- Nhớ `@Cacheable`/`@Transactional` có infrastructure advisors và order riêng; các cơ chế ấy có thể thay đổi phạm vi đo.

Một proxy có 3 advisors không phải cứ lấy chi phí của một proxy 1 advisor nhân 3. Matching cost, caching metadata, MethodInterceptor chain, JIT inlining, pointcut type và branch predictability đều ảnh hưởng phép đo.

## 13. JDK proxy và CGLIB proxy: không so hai class khác nhau

Spring AOP thường chọn JDK dynamic proxy khi target implements interface; class-based proxy sử dụng CGLIB khi cần proxy theo class hoặc được cấu hình `proxyTargetClass=true`. Chúng có call path và giới hạn khác nhau. Muốn so hiệu năng:

- Dùng cùng `DefaultWorkService` và cùng `WorkService` method.
- Kiểm tra `AopUtils.isJdkDynamicProxy(proxy)` hoặc `AopUtils.isCglibProxy(proxy)`.
- Tạo hai cấu hình Spring tách biệt chỉ khác proxy selection.
- Đảm bảo không dùng class `final` hoặc method `final` khiến class proxy không intercept được.
- Không giả định với JDK proxy có thể cast bean sang implementation class.
- Không đo đồng thời startup/class generation và method invocation nếu mục đích là dispatch overhead.

Nếu ứng dụng production đang dùng class-based proxy, benchmark chỉ có JDK proxy như ví dụ **chưa đủ** để suy ra overhead của production. Hãy làm thêm một thí nghiệm CGLIB cùng dữ liệu và workload.

## 14. Đo startup và memory footprint là bài toán khác

Microbenchmark trong phần 7 loại trừ Spring startup, nhưng đôi khi các câu hỏi thực tế lại là:

- Bật `@EnableAspectJAutoProxy` làm ApplicationContext startup tăng bao nhiêu?
- Tạo nhiều Spring beans có aspects khiến retained heap tăng bao nhiêu?
- Lúc cold start, việc tạo proxy/advisor metadata tốn bao nhiêu?
- Khi classloader tạo nhiều CGLIB subclasses, có vấn đề metaspace không?

Đó là các mục tiêu **khác với ns/op của method invocation**. Cần benchmark context startup bằng nhiều JVM forks, cố định số bean/aspect, test các phase riêng và theo dõi JVM/JFR hoặc allocation sampling. Đừng đem kết quả `directTarget` vs `viaSpringProxy` ở steady state trả lời cold startup.

## 15. Kiểm chứng kết quả bằng integration test trước khi tối ưu

JMH tạo dữ liệu hiệu năng, **không thay thế kiểm thử đúng hành vi Spring AOP**. Trước khi so độ trễ, nên có test nhỏ:

1. `AopUtils.isAopProxy(bean) == true`.
2. Advisor mong muốn có trên proxy.
3. Positive pointcut: gọi `compute()` qua bean proxy nhận giá trị chính xác.
4. Đối chứng: gọi target reference nhận cùng giá trị với aspect pass-through.
5. Negative pointcut: một method không match không vô tình nhận advice, nếu API cần test này.
6. Method exception: khi target ném lỗi, pass-through advice không nuốt/đổi exception.
7. Không có `new DefaultWorkService()` ngoài ý muốn trong benchmark chính.
8. JMH `@Setup` và `@TearDown` thành công qua hai JVM forks.

Nếu aspect thực tế là metrics/logging/transaction, thêm test xác minh side effects đúng contract, không chỉ equal return. Hiệu năng của một cấu hình **sai logic** là con số vô nghĩa.

### Kiểm tra trực tiếp advisor chain

Snippet đặt trong nơi có `context`:

```java
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

Object bean = context.getBean("workService");
System.out.println(AopUtils.isAopProxy(bean));

if (bean instanceof Advised advised) {
    int index = 0;
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println(index++ + ": " + advisor);
    }
}
```

Đây là đoạn **chẩn đoán**, không đưa `System.out.println` hoặc vòng introspection này vào `@Benchmark`. Log advisor chỉ dùng ở setup/sanity-check; nếu in mỗi invocation thì benchmark đo I/O thay vì call overhead.

## 16. Những sai lầm dễ làm số đo không đáng tin

| Sai lầm | Vì sao sai | Điều chỉnh |
| --- | --- | --- |
| Đo một lời gọi với `System.nanoTime` rồi kết luận | Nhiễu/JIT/classloading | Dùng JMH warmup/forks/repeated measurement |
| Gọi object `new` làm control nhưng proxy trỏ target khác với state khác | Hai đường có thể làm việc khác nhau | Lấy same target từ singleton `TargetSource` |
| Chỉ assert proxy bean class name có `$$SpringCGLIB$$` | Không chứng minh advisor đã xử lý invocation | Xem `AopUtils`, `Advised` và behavior |
| Benchmark logging bằng `println` rồi quy mọi latency cho Spring proxy | I/O áp đảo AOP dispatch | Tách pass-through overhead và logging overhead |
| Benchmark một method không dùng kết quả | JIT có thể loại bỏ công việc | `Blackhole.consume`, input biến thiên |
| Không warmup | Đo interpreter/tiered JIT và startup | JMH warmup và JVM forks |
| Có cache hit trong một nhánh mà không có trong nhánh khác | Không còn cùng nghiệp vụ | Xác định rõ cache hit/miss riêng |
| Đo transaction method mà chưa xác nhận transaction manager | Lời gọi có thể không thực sự transactional | Test transaction boundary đúng |
| So benchmark ở hai JVM/JDK flags khác nhau | Confounder lớn | Cố định runtime/hardware |
| Chạy nhiều threads ngay từ đầu | Contention có thể che dispatch overhead | Bắt đầu `-t 1`, sau đó đo scalability riêng |
| Coi `Score` là con số đúng tuyệt đối không có noise | Không có confidence/variance context | Xem error, forks, iteration trend |
| Dựa vào một nanosecond overhead estimate để quyết định kiến trúc | Không xét workload thực và maintainability | Cân nhắc profiling production và behavior correctness |
| Đưa Spring context creation vào một benchmark, nhưng control không tạo context | Không cùng setup | Dùng `@Setup(Level.Trial)` cho cả hai |
| Dùng target source không singleton mà giữ raw target cố định | Có thể bỏ qua scope semantics và resource release | Thiết kế control riêng theo target scope |

## 17. Quy trình áp dụng kết quả vào quyết định hiệu năng

**Bước 1 — Xác định câu hỏi:** có phải cần biết overhead thuần của custom `@Aspect`, tổng chi phí transaction/cache advice, hay độ trễ end-to-end? Viết rõ metric: `ns/op`, `ops/s`, allocation `B/op`, startup time hay p99 latency.

**Bước 2 — Kiểm thử correctness:** dùng `Verify` và integration tests chứng minh proxied path chạy đúng Spring advisor và control cùng kết quả.

**Bước 3 — Chạy JMH baseline:** dùng ít nhất hai mức workload, warmup và nhiều forks, xuất JSON. Lưu JDK version, CPU, OS, heap/GC/JVM flags và Maven dependency versions.

**Bước 4 — Đọc số liệu:** so từng parameter và từng benchmark method trên cùng `Mode/Units`. Cẩn trọng khi hiệu số nhỏ gần mức nhiễu; không ép mọi phép đo thành một % overhead cố định.

**Bước 5 — Phân tích profiler:** nếu thấy allocation hoặc latency lớn, dùng JFR/JMH profiler tìm phần chi phí của method invocation, reflection chain, aspect body, cache provider hay database.

**Bước 6 — So với workload thật:** nếu service production làm I/O hoặc business computation nặng, microbenchmark với `rounds=8` có thể phóng đại tỷ lệ AOP overhead. Dùng profiling ở service path thật, với kiểm soát môi trường và công cụ phù hợp.

**Bước 7 — Thay đổi nhỏ có kiểm chứng:** tối ưu pointcut phạm vi quá rộng, giảm logging/serialization trong hot path, tách concern không cần thiết hoặc chọn instrumentation sẵn của Spring. Không tự loại transaction/security advice chỉ vì một microbenchmark thấy overhead.

**Bước 8 — Regression:** lưu kết quả benchmark cùng commit/JDK/hardware và test contract correctness. Khi so trước/sau, chạy cùng môi trường và xem biến thiên qua nhiều lần đo.

## 18. Câu hỏi tự kiểm tra

**Hỏi:** Tại sao phải dùng bean Spring proxy thật?  
**Đáp:** Vì mục tiêu là đo Spring AOP interception. Nếu target không được proxy, benchmark có thể đo hai lời gọi Java thông thường rồi kết luận sai về AOP.

**Hỏi:** Tại sao lấy direct target từ `Advised.getTargetSource()`?  
**Đáp:** Để control và proxy dùng cùng target instance; phương pháp này phù hợp với singleton target source của demo, không áp dụng vô điều kiện cho dynamic target scopes.

**Hỏi:** `proxyScore - directScore` là chi phí cố định của mọi Spring proxy?  
**Đáp:** Không. Đây là observed contrast cho JVM/config/advisor/workload cụ thể, bị ảnh hưởng JIT, allocation, branch prediction và noise.

**Hỏi:** `@Around` pass-through có đo chi phí logging aspect thực không?  
**Đáp:** Không. Nó đo proxy + advice invocation cơ bản; muốn đo logging cần benchmark bổ sung với cùng logging backend và loại bỏ I/O confounders phù hợp.

**Hỏi:** Một cache hit khiến proxy nhanh hơn direct target chứng minh điều gì?  
**Đáp:** Rằng caching short-circuit đã tránh tính toán target ở đường proxy; không chứng minh dispatch proxy không có overhead.

**Hỏi:** Vì sao có cả `@Param({"8","128"})`?  
**Đáp:** Để thấy tỷ lệ chi phí interception so với workload ngắn và dài hơn; không suy diễn một tỷ lệ từ duy nhất một target method.

**Hỏi:** JMH `@Setup(Level.Trial)` loại trừ điều gì khỏi `ns/op`?  
**Đáp:** Chi phí khởi tạo ApplicationContext, tạo proxy và lấy target reference được thực hiện ngoài hot loop đo latency của method call.

**Hỏi:** Có thể dùng kết quả `mvn -q compile exec:java` chạy `Verify` làm benchmark không?  
**Đáp:** Không. `Verify` chỉ là phép kiểm tra tính đúng của config/đối chứng; JMH mới là harness đo hiệu năng trong project này.

## 19. Tài liệu tham khảo

- Spring Framework 6.2 — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html
- Spring Framework — @AspectJ-style AOP: https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html
- Spring Framework — AOP API/ProxyFactory: https://docs.spring.io/spring-framework/reference/6.2/core/aop-api.html
- Spring Framework — AopUtils Javadoc: https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/support/AopUtils.html
- Spring Framework — Advised Javadoc: https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/framework/Advised.html
- OpenJDK JMH — project, samples và cách chạy microbenchmark: https://github.com/openjdk/jmh
- JMH Core 1.37: https://central.sonatype.com/artifact/org.openjdk.jmh/jmh-core/1.37


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-performance-maintenance-004">Không thực hiện I/O nặng hoặc ghi log nhạy cảm trong mọi advice</a>

<details>
<summary>Xem chi tiết</summary>

# Không thực hiện I/O nặng hoặc ghi log nhạy cảm trong mọi advice của Spring AOP

## 1. Mục tiêu và phạm vi

Trong Spring Framework, advice của một aspect thường nằm trên đường thực thi trực tiếp của service, controller hoặc repository được proxy. Một `@Before`, `@Around` hay `@After` có thể chạy **cho mỗi lời gọi phương thức khớp pointcut**. Nếu bên trong advice luôn ghi log đầy đủ arguments, gọi HTTP, truy vấn database, ghi file hoặc đẩy sự kiện qua network, chi phí đó được cộng vào gần như mọi lượt gọi và có thể biến cơ chế cross-cutting thành điểm nghẽn chung.

Ví dụ một service xử lý giao dịch trong 10 ms nhưng advice còn gọi một API audit mất 80 ms, người dùng thường nhìn thấy độ trễ gần 90 ms (chưa tính các yếu tố khác). Khi API audit chậm hoặc timeout, vấn đề có thể lan ra nhiều service cùng chịu advice. Nếu advice log cả `ProceedingJoinPoint#getArgs()` hoặc `result.toString()`, có nguy cơ đưa mật khẩu, token, dữ liệu tài chính và nội dung riêng tư vào hệ thống log, nơi nhiều người/hệ thống có quyền đọc hơn database nghiệp vụ.

Bài tập trung vào **Spring AOP trong ứng dụng Spring**, cụ thể:

- Xác định chi phí thực của advice trên đường gọi qua Spring proxy.
- Giữ advice gọn: thống kê trong bộ nhớ, sử dụng biến local và `try/finally`, hạn chế pointcut.
- Phân biệt metric thông thường, log chậm có điều kiện, log lỗi, audit cần độ bền dữ liệu và công việc I/O nghiệp vụ.
- Áp dụng nguyên tắc **allowlist** khi log: chỉ ghi những trường đã được xác nhận an toàn, không tự động serialize object.
- Hiểu tại sao chuyển I/O sang `@Async` hoặc executor chưa chắc giải quyết được saturation, backlog, context propagation hay độ bền.
- Viết ví dụ Java 17+, Spring AOP và kiểm thử chỉ những trường log cho phép mới xuất hiện.

Các nguyên tắc concurrency/logging tổng quát chỉ được nhắc ở mức cần thiết để thiết kế advice an toàn trong Spring.

## 2. Tại sao một lời gọi nhỏ trong advice có thể gây vấn đề lớn?

Một đường đi đơn giản:

```text
Caller
   |
   v
Spring AOP proxy
   |
   +-- @Around advice
   |      +-- lấy thời gian, cập nhật metric trong bộ nhớ (nhẹ)
   |      +-- gọi REST audit endpoint (I/O, có thể block)
   |      +-- serialize toàn bộ arguments/result (CPU và thông tin nhạy cảm)
   |      +-- ghi log đồng bộ (có thể block)
   |
   +-- target business method
   |
   +-- advice kết thúc
   |
   v
Caller nhận kết quả
```

Với `@Around`, mọi công việc **trước** `proceed()` đều trì hoãn target; mọi công việc **sau** `proceed()` nhưng **trước** khi return tiếp tục trì hoãn caller. Nếu logger dùng appender đồng bộ, việc `log.info(...)` có thể dẫn tới định dạng, lock, ghi file, flush, hoặc network theo cấu hình backend. Việc gọi logger **không phải cam kết phi blocking** của Spring AOP.

Các rủi ro:

| Thao tác trong mọi advice invocation | Chi phí/rủi ro | Hướng tiếp cận |
| --- | --- | --- |
| `RestClient`/`WebClient.block()` gọi dịch vụ audit | Network latency, timeout, pool saturation, lỗi phụ thuộc ngoài | Hạn chế ở workflow phù hợp; đưa event sang ranh giới chịu tải/bền vững nếu cần |
| `JdbcTemplate.update(...)` để ghi log ở mỗi method | Nhiều lần round trip và lock; có thể nằm trong transaction | Metric trong bộ nhớ; sự kiện audit chọn lọc, outbox/transaction phù hợp |
| `Files.writeString(...)` hoặc append file trực tiếp | Disk I/O, contention và có thể lỗi permission/dung lượng | Logging backend/collector chuyên dụng với cấu hình tải được kiểm soát |
| `log.debug("args={}", Arrays.toString(pjp.getArgs()))` | Dù dùng placeholder vẫn duyệt/biến đổi dữ liệu; dễ lộ bí mật | Không truy xuất/serialize toàn bộ args; sử dụng allowlist |
| `log.info("result={}", pjp.proceed())` | Có thể in toàn bộ response/PII; có thể gọi `toString()` tốn kém | Ghi trạng thái thành công/thất bại, loại operation, latency |
| Log stack trace đầy đủ cho mọi exception | Chi phí lớn, trùng log và có thể chứa payload/request data | Chọn tầng xử lý lỗi chính; chỉ log metadata được phép |
| Metric gắn nhãn bằng `userId`, `requestId`, URL raw | Cardinality tăng mạnh, memory/monitoring tốn kém | Dùng operation ổn định, status code nhóm, loại exception đã chuẩn hóa |

Lưu ý: metric cũng cần ngân sách cardinality và backend thu thập đúng. Việc một lệnh `counter.increment()` nhanh không có nghĩa một hệ thống đăng ký **metric series mới cho từng request ID** sẽ an toàn.

## 3. Chọn đúng vị trí can thiệp của Spring AOP

Spring AOP hoạt động dựa trên proxy. Vì vậy:

1. Advice thường được kích hoạt khi caller **đi qua Spring proxy**. Lời gọi tự thân như `this.otherMethod()` có thể bỏ qua advice.
2. Một pointcut rộng như `execution(* com.example..*(..))` có thể bám vào nhiều method, kể cả những method nội bộ có tần suất rất cao.
3. Nếu method được gọi hàng nghìn lần trong một request, ngay cả phép xử lý có chi phí nhỏ trên mỗi invocation cũng tích lũy đáng kể.
4. `@Around` cho phép điều khiển `proceed()`, kết quả và exception, nhưng nên dùng **loại advice ít quyền lực nhất đáp ứng nhu cầu**. Chỉ cần tăng counter sau khi method kết thúc? `@After` có thể phù hợp. Cần đo elapsed trong cùng invocation? `@Around` với biến local và `finally` là lựa chọn rõ ràng.
5. Spring AOP **không tự động tách advice sang thread khác**; logic advice chạy trong luồng gọi theo cơ chế interceptor/proxy thực tế.

Ví dụ pointcut nên tập trung vào những thao tác có giá trị quan sát:

```java
@Around("execution(* demo.PaymentService.submit(..))")
public Object observe(ProceedingJoinPoint pjp) throws Throwable {
    // Theo dõi đúng thao tác submit thay vì mọi method của mọi bean.
    return pjp.proceed();
}
```

Đây là snippet giải thích pointcut, **không** là program Java độc lập. Không copy một pointcut bao trùm toàn bộ repository/controller vào production chỉ vì dễ viết. Cân nhắc dùng marker annotation riêng như `@ObservedBusinessOperation` nếu dự án cần scope có chủ đích, nhưng annotation cũng phải được áp dụng đúng tại điểm Spring AOP nhận diện.

## 4. Chống mẫu: I/O nặng và log toàn bộ dữ liệu ở mỗi advice

Ví dụ sau là **phản ví dụ, không nên dùng**:

```java
// Pseudocode minh họa hành vi cần tránh; không phải class hoàn chỉnh.
@Around("execution(* demo..*.*(..))")
public Object unsafe(ProceedingJoinPoint pjp) throws Throwable {
    log.info("arguments={}", java.util.Arrays.toString(pjp.getArgs()));
    remoteAuditClient.sendSynchronously(pjp.getArgs());
    Object result = pjp.proceed();
    log.info("result={}", result);
    return result;
}
```

Những vấn đề cụ thể:

- Pointcut quá rộng có thể khiến các method rẻ tiền chịu nhiều lần serialization/log/network.
- `getArgs()` có thể chứa `password`, `Authorization`, cookie, token thanh toán hoặc object giữ dữ liệu cá nhân. `toString()` của object cũng có thể đưa dữ liệu nhạy cảm vào log hoặc chạy code không mong muốn.
- Nếu `sendSynchronously(...)` timeout hoặc ném exception **trước** `proceed()`, target nghiệp vụ **không chạy**. Một hệ thống ghi audit phụ bị lỗi có thể làm hỏng logic chính.
- Nếu ném lỗi sau `proceed()`, caller có thể nhận lỗi mặc dù nghiệp vụ đã ghi transaction hoặc side effect thành công, gây retry trùng.
- Nếu advice log cả request và response, thông tin riêng tư có thể bị nhân bản sang log aggregator, backup và dashboard với chính sách truy cập/retention khác biệt.

**Không xem đây chỉ là vấn đề tốc độ**: nhiều loại log có thời hạn lưu, quyền truy cập và yêu cầu bảo vệ khác dữ liệu gốc. Một request ID hoặc exception message cũng có thể nhạy cảm nếu nó liên kết được tới người dùng, session hoặc nội dung đầu vào.

## 5. Thiết kế khuyến nghị: metric nhẹ, log có điều kiện và allowlist

Thay vì phát log chứa payload trên mọi lượt gọi, một aspect quan sát có thể:

- Ghi thời điểm bắt đầu trong biến local bằng `System.nanoTime()`.
- Gọi `pjp.proceed()` đúng một lần trong đường đi thông thường.
- Ghi số lượt thành công/thất bại và tổng thời gian bằng counter thread-safe trong bộ nhớ.
- Ở `finally`, chỉ xét log khi elapsed vượt **ngưỡng cảnh báo** hoặc chính sách sampling/rate limit yêu cầu.
- Log **operation name ổn định, outcome và duration**; không ghi args, return object, header hoặc exception message.
- Chuyển metric sang Micrometer/Observation khi ứng dụng đã có hạ tầng observability; với production, phải cấu hình export, tag, histogram, cardinality và retention.

Điểm đánh đổi: log khi “slow” chưa đủ cho điều tra audit pháp lý hoặc nghiệp vụ yêu cầu đủ 100% sự kiện. **Audit bắt buộc không được tùy tiện sampling hay drop**; nó cần thiết kế riêng cho persistence/retry/transaction/outbox, tách khỏi log chẩn đoán. Các ví dụ bên dưới nói về **telemetry hiệu năng**, không thay thế audit bắt buộc.

### 5.1. Quy tắc dữ liệu an toàn

| Trường định đưa vào log/metric | Khuyến nghị |
| --- | --- |
| Tên thao tác cố định, ví dụ `payment.submit` | Dùng allowlist; nhãn ít biến thiên |
| Outcome nhóm `success`/`failure` | Phù hợp cho metric và log |
| Thời gian xử lý (ms) | Phù hợp, không tự chứa payload |
| Tên exception class được chuẩn hóa | Chỉ khi đã rà soát; tránh nguyên văn message |
| Path HTTP theo **route template** (`/orders/{id}`) | Có thể dùng, không dùng path thực tế chứa ID |
| Raw URL/query string, headers, body | Không log mặc định |
| JWT, API key, session cookie, password, payment secret | Không log |
| `pjp.getArgs()`, `pjp.proceed()` result, `toString()` nguyên object | Không serialize tự động |
| Customer ID, email, IP, correlation ID | Cần xem xét chính sách quyền riêng tư và mục tiêu vận hành; không mặc định “an toàn” |
| Hash của mật khẩu hoặc định danh dễ đoán | Không mặc định vô hại: hash vẫn có thể liên kết hoặc suy đoán dữ liệu |

Sự kiện cần ghi cả dữ liệu nhạy cảm vì yêu cầu nghiệp vụ phải dùng kho dữ liệu và quyền truy cập phù hợp, có trường cần thiết tối thiểu và retention chính sách riêng. Không đẩy bí mật vào exception string chỉ để ghi log ở advice.

## 6. Ví dụ đầy đủ: Spring AOP không log bí mật

Ví dụ sử dụng **Java 17+**, **Spring Framework 6.2.14**, **AspectJ Weaver 1.9.24** và SLF4J/Logback. Đây là **Spring AOP proxy**, không bật AspectJ load-time weaving.

Cấu trúc:

```text
safe-spring-aspect/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── PaymentService.java
    │   ├── DefaultPaymentService.java
    │   ├── ObservationStats.java
    │   ├── SafeMonitoringAspect.java
    │   ├── AopConfiguration.java
    │   └── Main.java
    └── test/java/demo/
        └── SafeMonitoringAspectTest.java
```

### 6.1. Maven `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>safe-spring-aspect</artifactId>
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
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.14</version>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.24</version>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.5.18</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.4</version>
            <scope>test</scope>
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
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
                <configuration>
                    <mainClass>demo.Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Thư viện `aspectjweaver` cho Spring phân tích biểu thức pointcut và annotation `@Aspect`, nhưng dự án **không** truyền `-javaagent` và **không** dùng `META-INF/aop.xml`.

### 6.2. `PaymentService.java` và `DefaultPaymentService.java`

`src/main/java/demo/PaymentService.java`:

```java
package demo;

public interface PaymentService {
    String submit(String secretToken);
}
```

`src/main/java/demo/DefaultPaymentService.java`:

```java
package demo;

public class DefaultPaymentService implements PaymentService {
    @Override
    public String submit(String secretToken) {
        if ("fail-secret".equals(secretToken)) {
            throw new IllegalStateException("provider unavailable");
        }
        return "accepted";
    }
}
```

`secretToken` là trường giả lập dữ liệu nhạy cảm. Nó **không được xuất hiện trong log**; code kiểm tra nhánh exception dùng chuỗi demo cố định, không phải ví dụ xử lý token thanh toán thật. Implementation tách khỏi aspect để chứng minh advice có thể quan sát kết quả mà không tự serialize input.

### 6.3. `ObservationStats.java`: metric trong bộ nhớ

`src/main/java/demo/ObservationStats.java`:

```java
package demo;

import java.util.concurrent.atomic.LongAdder;

public class ObservationStats {
    private final LongAdder succeeded = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();

    public void record(boolean success, long elapsedNanos) {
        if (success) {
            succeeded.increment();
        } else {
            failed.increment();
        }
        totalNanos.add(elapsedNanos);
    }

    public long succeeded() {
        return succeeded.sum();
    }

    public long failed() {
        return failed.sum();
    }

    public long totalNanos() {
        return totalNanos.sum();
    }
}
```

`LongAdder` cập nhật trong bộ nhớ, thread-safe cho tác vụ đếm/ghi nhận tổng giá trị. `sum()` không phải snapshot nguyên tử khi đang có cập nhật đồng thời; nên assert khi các invocation đã kết thúc. Đây là **metric giản lược cho bài học**. Trong production nên sử dụng registry metric/Observation hiện có của ứng dụng, tránh duy trì các bộ đếm tùy tiện không có vòng đời/export/retention rõ ràng.

### 6.4. `SafeMonitoringAspect.java`: advice đo thời gian và log chậm có điều kiện

`src/main/java/demo/SafeMonitoringAspect.java`:

```java
package demo;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
public class SafeMonitoringAspect {
    private static final Logger log =
            LoggerFactory.getLogger(SafeMonitoringAspect.class);

    private final ObservationStats stats;
    private final LongSupplier nanoTime;
    private final long slowThresholdNanos;

    public SafeMonitoringAspect(
            ObservationStats stats,
            LongSupplier nanoTime,
            Duration slowThreshold) {
        this.stats = stats;
        this.nanoTime = nanoTime;
        this.slowThresholdNanos = slowThreshold.toNanos();
        if (slowThresholdNanos < 0) {
            throw new IllegalArgumentException("negative threshold");
        }
    }

    @Around("execution(* demo.PaymentService.submit(..))")
    public Object observe(ProceedingJoinPoint pjp) throws Throwable {
        long started = nanoTime.getAsLong();
        boolean completed = false;

        try {
            Object result = pjp.proceed();
            completed = true;
            return result;
        } finally {
            long elapsed = Math.max(0L, nanoTime.getAsLong() - started);
            stats.record(completed, elapsed);

            // Chỉ những invocation vượt ngưỡng mới được xem xét ghi log.
            // Operation/outcome là giá trị ổn định; KHÔNG dùng args/result.
            if (elapsed >= slowThresholdNanos && log.isWarnEnabled()) {
                log.warn(
                    "spring_aop_slow_call operation={} outcome={} elapsed_ms={}",
                    "payment.submit",
                    completed ? "success" : "failure",
                    TimeUnit.NANOSECONDS.toMillis(elapsed)
                );
            }
        }
    }
}
```

Giải thích chính xác semantics:

- `pjp.proceed()` được gọi **một lần** rồi kết quả được trả lại nguyên vẹn. Nếu target ném `Throwable`, `finally` vẫn ghi metric và exception tiếp tục truyền cho caller.
- `completed=true` nghĩa là `proceed()` trả về bình thường. Nếu advice khác nằm phía trong interceptor chain trả về sớm, trạng thái này phản ánh **kết quả lời gọi qua chain**, không tự khẳng định tất cả code target đã chạy.
- `System.nanoTime()` dùng cho đo khoảng thời gian đã trôi qua, không dùng làm thời gian lịch. `LongSupplier` cho phép test thay bằng đồng hồ giả lập, không cần `Thread.sleep` hoặc dựa vào lịch CPU.
- `stats.record(...)` dùng phép cập nhật trong bộ nhớ thay cho ghi database hoặc HTTP trong advice.
- Không gọi `pjp.getArgs()` hoặc ghi `result`. Đặc biệt **không** in `Throwable#getMessage()` một cách mặc định vì message của thư viện bên dưới có thể chứa URL, query hoặc dữ liệu input.
- `isWarnEnabled()` tránh tạo event khi WARN bị tắt nhưng **không bảo đảm** backend ghi log là phi blocking. Logging path vẫn cần cấu hình và quan sát hiệu năng riêng.
- Một lần gọi chậm có thể tạo một event log. Nếu hệ thống gặp sự cố khiến hàng nghìn call đều chậm, cần thêm **sampling hoặc rate limit** ở tầng logging/collector theo chính sách vận hành. Trong ví dụ mức ngưỡng mặc định làm giảm số event; nó không phải cơ chế hạn lưu lượng tuyệt đối.

### 6.5. Spring Java configuration

`src/main/java/demo/AopConfiguration.java`:

```java
package demo;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AopConfiguration {
    @Bean
    public PaymentService paymentService() {
        return new DefaultPaymentService();
    }

    @Bean
    public ObservationStats observationStats() {
        return new ObservationStats();
    }

    @Bean
    public SafeMonitoringAspect safeMonitoringAspect(
            ObservationStats stats) {
        return new SafeMonitoringAspect(
            stats,
            System::nanoTime,
            Duration.ofMillis(100)
        );
    }
}
```

Bean service được Spring quản lý; aspect cũng là một Spring bean. Không cần `@EnableLoadTimeWeaving` vì đây là proxy-based AOP. Cấu hình này áp dụng pointcut rõ ràng lên `PaymentService.submit`, nên không intercept các method bean khác trong ứng dụng.

### 6.6. Chạy ví dụ

`src/main/java/demo/Main.java`:

```java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AopConfiguration.class)) {
            PaymentService service = context.getBean(PaymentService.class);
            ObservationStats stats = context.getBean(ObservationStats.class);

            System.out.println("PROXY=" + AopUtils.isAopProxy(service));
            System.out.println("SUCCESS_RESULT=" + service.submit("private-token"));

            try {
                service.submit("fail-secret");
            } catch (IllegalStateException expected) {
                System.out.println(
                    "FAILED_WITH=" + expected.getClass().getSimpleName()
                );
            }

            System.out.println("SUCCESS_CALLS=" + stats.succeeded());
            System.out.println("FAILED_CALLS=" + stats.failed());
        }
    }
}
```

Chạy:

```bash
mvn -q compile exec:java
```

Những dòng stdout được mong đợi:

```text
PROXY=true
SUCCESS_RESULT=accepted
FAILED_WITH=IllegalStateException
SUCCESS_CALLS=1
FAILED_CALLS=1
```

Nếu máy xử lý nhanh hơn ngưỡng `100 ms`, có thể không có log WARN. Nếu một invocation vượt ngưỡng, backend SLF4J/Logback có thể in thêm event `spring_aop_slow_call` với `operation`, `outcome`, `elapsed_ms`; **không bao gồm token**. Thời gian tuyệt đối và event WARN phụ thuộc môi trường, vì vậy không đưa số milliseconds cố định vào stdout kỳ vọng.

**Cần xác nhận qua runtime thật** trước khi áp dụng production: logger appender, profiler, export metrics và các advisor khác có thể làm tổng độ trễ khác ví dụ.

## 7. Integration test: qua Spring proxy và kiểm tra log không chứa bí mật

Chỉ test trực tiếp method `observe(...)` không đủ để chứng minh Spring đã tạo AOP proxy, pointcut khớp hoặc advice thực sự chạy. Ta dùng `AnnotationConfigApplicationContext` và một cấu hình test riêng với đồng hồ giả, nhờ đó việc vượt ngưỡng log **xác định được** mà không dựa vào `sleep`.

`src/test/java/demo/SafeMonitoringAspectTest.java`:

```java
package demo;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.junit.jupiter.api.Assertions.*;

class SafeMonitoringAspectTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class TestConfig {
        private final AtomicLong fakeNanos = new AtomicLong();

        @Bean
        PaymentService paymentService() {
            return new DefaultPaymentService();
        }

        @Bean
        ObservationStats observationStats() {
            return new ObservationStats();
        }

        @Bean
        SafeMonitoringAspect safeMonitoringAspect(ObservationStats stats) {
            // Mỗi lần đọc clock tăng 2 ms. Mỗi invocation vượt ngưỡng 1 ms.
            return new SafeMonitoringAspect(
                stats,
                () -> fakeNanos.getAndAdd(2_000_000L),
                Duration.ofMillis(1)
            );
        }
    }

    @Test
    void logsOnlyAllowlistedMetadataAndRecordsBothOutcomes() {
        Logger logger = (Logger) LoggerFactory.getLogger(
            SafeMonitoringAspect.class
        );
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            try (var context =
                    new AnnotationConfigApplicationContext(TestConfig.class)) {
                PaymentService service = context.getBean(PaymentService.class);
                ObservationStats stats = context.getBean(ObservationStats.class);

                assertTrue(AopUtils.isAopProxy(service));
                assertEquals("accepted", service.submit("my-secret-token"));
                assertThrows(IllegalStateException.class,
                    () -> service.submit("fail-secret"));

                assertEquals(1L, stats.succeeded());
                assertEquals(1L, stats.failed());
                assertEquals(4_000_000L, stats.totalNanos());

                assertEquals(2, appender.list.size());
                for (ILoggingEvent event : appender.list) {
                    String line = event.getFormattedMessage();
                    assertTrue(line.contains("spring_aop_slow_call"));
                    assertTrue(line.contains("operation=payment.submit"));
                    assertFalse(line.contains("my-secret-token"));
                    assertFalse(line.contains("fail-secret"));
                    assertFalse(line.contains("provider unavailable"));
                }
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
```

Chạy toàn bộ test:

```bash
mvn test
```

Kết quả mong đợi nếu chạy trên các dependency đã định nghĩa: **1 test passed, 0 failures/errors**. `ListAppender` nhận log event tại logger của aspect, nên assertion trực tiếp kiểm tra nội dung được format. Test có thể phải điều chỉnh nếu dự án dùng một backend logging khác Logback hoặc filter logger đặc biệt.

Các assertion chính:

- `AopUtils.isAopProxy(...)` chứng minh call đi qua Spring AOP.
- Số `success` và `failure` là 1/1, chứng minh `finally` cập nhật metric cả khi target ném exception.
- Đồng hồ giả khiến mỗi invocation đúng 2 ms và threshold là 1 ms; **không phụ thuộc tốc độ CPU** để chọn nhánh log.
- Cả hai event chỉ chứa operation/outcome/duration, không chứa ba chuỗi cần bảo vệ.
- Appender được tháo ở `finally`, tránh ảnh hưởng test khác chạy sau nó.

**Giới hạn của test:** chỉ kiểm tra các chuỗi mẫu và logger riêng của aspect. Đây không phải bằng chứng toàn bộ ứng dụng không lộ dữ liệu qua các logger khác, exception handler, HTTP access log hay collector. Với production cần thêm kiểm thử security/observability theo chính sách tổ chức.

## 8. Khi phải phát sinh I/O từ một aspect

Không phải mọi I/O trong advice đều sai. Ví dụ chính sách audit bắt buộc ghi một sự kiện bền vững khi giao dịch quan trọng xảy ra. Vấn đề là **mục đích, mức đảm bảo và việc I/O có nên nằm trong critical path**.

| Mục đích | Thực hành phù hợp trong Spring | Điều cần quyết định |
| --- | --- | --- |
| Đếm số lần gọi, latency, lỗi | Micrometer MeterRegistry/Observation hoặc aggregator in-memory được quản lý | Labels ít cardinality, exporter, sampling/histogram |
| Log chẩn đoán nghiệp vụ thông thường | Log chọn lọc/sampling theo mức độ; allowlist metadata | Volume, retention, ACL, mức độ chi tiết |
| Slow-call signal | Timer/metric và threshold log có giới hạn | Mức ngưỡng, rate limit, overload |
| Audit bắt buộc phải có đủ sự kiện | Service/domain workflow thiết kế rõ; transaction/outbox hoặc durable queue nếu cần | Atomicity, retry, idempotency, tính đầy đủ và khả năng khôi phục |
| Gọi API bắt buộc để quyết định nghiệp vụ | Logic nghiệp vụ/adapter có timeout, retry, circuit breaker theo yêu cầu | Không che giấu I/O đó trong advice rộng khó nhìn thấy |
| Telemetry không bắt buộc | Async pipeline có queue hữu hạn, drop policy công khai, giám sát backlog | Backpressure, mất event, shutdown và graceful drain |

**Không chuyển mọi I/O sang `@Async` theo phản xạ.** Một task được gửi sang executor vẫn tiêu tốn CPU, memory, connection, file descriptor và queue slot. Nếu tốc độ tạo event lớn hơn tốc độ xử lý, queue sẽ đầy hoặc tăng không giới hạn. Với audit cần đủ 100% dữ liệu, chính sách drop/skip không thể dùng; với telemetry best-effort, có thể chấp nhận mất một số event theo yêu cầu đã được xác định.

Các phương án như **transactional outbox** thích hợp khi cần bảo đảm dữ liệu nghiệp vụ và sự kiện có một trạng thái commit nhất quán: giao dịch ghi dữ liệu chính và bản ghi outbox, worker riêng đọc và gửi sau commit, quản lý retry và idempotency. Đây là **kiến trúc cho event quan trọng** chứ không phải một lời gọi `remoteAudit()` đặt trong mọi aspect.

Nếu cần xác định sự kiện theo commit, xem xét transaction synchronization/application event của Spring theo đúng semantics: một `@AfterReturning` chỉ nói target trả về bình thường tại vị trí advice đó, **không tự chứng minh database transaction đã commit** (thứ tự advisor có thể khác). Audit phục thuộc commit cần gắn với lifecycle transaction được chọn có chủ đích.

## 9. Logging async và `@Async` trong Spring: các giới hạn

Trong Spring, `@Async` thường được thực thi nhờ AOP proxy và `TaskExecutor` được cấu hình qua `@EnableAsync`. Cần lưu ý:

- Gọi một method `@Async` bằng `this.method()` **trong cùng bean** có thể không đi qua async proxy; method vẫn chạy đồng bộ.
- Nếu aspect gọi delegate `@Async` thông qua một **Spring bean khác** được proxy, công việc có thể được thực thi ở executor, nhưng caller vẫn tốn chi phí submit task, cấp phát dữ liệu và cạnh tranh queue.
- Executor có queue vô hạn có nguy cơ tăng bộ nhớ khi upstream bắn quá nhanh. Queue hữu hạn cần chính sách khi đầy: block/reject/drop với lý do rõ ràng và metric giám sát.
- `@Async` không tự động làm dữ liệu log an toàn: object arguments truyền sang thread khác vẫn có thể chứa bí mật, và lưu reference có thể kéo dài thời gian giữ dữ liệu trong RAM.
- `ThreadLocal` và MDC không tự động lan truyền đúng sang mọi executor; nếu cần context, truyền **chỉ những trường đã được allowlist**, dùng cơ chế truyền context phù hợp và dọn bằng `finally`.
- Nếu dùng Logback `AsyncAppender`, vẫn phải hiểu khả năng mất event, ngưỡng bỏ qua event, cách chờ khi queue đầy và hành vi lúc shutdown. Async appender không thay đổi nội dung nhạy cảm của message.
- Không gắn `@Async` vào một method advice chỉ để mong toàn bộ `@Around` bất đồng bộ. `@Around` giữ contract của `proceed()`/result/exception; việc thay đổi thread có thể làm sai semantics của caller.

Snippet bổ sung minh họa **một service delegate riêng** nếu thật sự cần gửi telemetry best-effort; nó không phải một giải pháp audit bền vững:

```java
// Fragment tích hợp vào project Spring có @EnableAsync và executor phù hợp.
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class TelemetryDelegate {
    @Async("telemetryExecutor")
    public void publish(String fixedOperation, String outcome) {
        // Gửi telemetry đã allowlist theo cơ chế best-effort có giới hạn tải.
        // Không nhận request body, token, pjp.getArgs() hoặc return object.
    }
}
```

Cần đăng ký bean `telemetryExecutor` với queue/pool/rejection policy phù hợp và bật `@EnableAsync`. Fragment chỉ minh họa ranh giới; **không chạy độc lập** nếu chưa có cấu hình executor và implementation. Với dòng nghiệp vụ cần độ tin cậy, giữ sự kiện trong luồng bền vững của domain thay vì một lời gọi async fire-and-forget.

## 10. Kết hợp với Micrometer/Observation khi ứng dụng đã có hạ tầng

Spring Framework hỗ trợ hệ thống observability thông qua Micrometer Observation và các integration. Khi cần đo latency và đếm lượt gọi, ưu tiên cơ chế quan sát được dự án đăng ký sẵn thay vì tự phát ra INFO log cho mỗi invocation.

Ví dụ **minh họa ý tưởng**, khi đã có `MeterRegistry` của Micrometer trong ứng dụng:

```java
// Snippet, không thuộc project Maven độc lập phía trên.
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;

public final class MonitoringMetrics {
    private final MeterRegistry registry;

    public MonitoringMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String outcome, long nanos) {
        registry.timer(
            "business.operation.duration",
            "operation", "payment.submit",
            "outcome", outcome
        ).record(nanos, TimeUnit.NANOSECONDS);
    }
}
```

Điểm then chốt là `operation` là nhãn **cố định** và `outcome` nằm trong một danh sách ít giá trị, không gắn `userId`, `email`, token, request ID, raw URL hay exception message vào tags. Nếu ứng dụng đã có instrumentations cho HTTP, database, client calls, cân nhắc chống đo lặp cùng mục tiêu và hiểu rõ ý nghĩa “latency” của từng timer.

Observation hỗ trợ liên kết metrics/tracing/context theo hệ thống đã cấu hình. Tuy nhiên, **tracing cũng phải tránh đưa dữ liệu nhạy cảm vào span attributes/baggage**; baggage có thể lan sang hệ thống bên ngoài. Một trace ID có thể phù hợp cho correlation trong phạm vi kiểm soát, nhưng cần áp dụng đúng chính sách bảo vệ và retention.

Khi dùng Reactor, `CompletableFuture` hoặc trả về `CompletionStage`, thời gian chạy method trả về publisher/future **không đồng nghĩa** thời gian công việc asynchronous hoàn tất. `@Around` đồng bộ chỉ đo thời gian tới lúc tạo/trả về object nếu không tích hợp đúng lifecycle của publisher/future. Không ghi “thành công” khi chỉ mới schedule task nếu outcome thực sự chưa có.

## 11. Tối ưu pointcut, thứ tự advisor và chi phí đo lường

Một lời gọi business có thể đi qua nhiều Spring advisors: security, transaction, caching, custom monitoring. Đo thời gian ở đâu phụ thuộc thứ tự của các advisors và việc invocation có đi qua proxy hay không:

```text
Caller
   -> Security advisor
       -> Monitoring advisor
           -> Transaction advisor
               -> Target service
```

Nếu monitoring nằm bên **ngoài** transaction interceptor, duration có thể gồm thời gian vào/ra transaction; nếu nằm **bên trong**, ý nghĩa metric khác. Nếu caching advisor trả về sớm trước khi monitoring chạy, aspect chỉ thấy một phần lời gọi. `@Order` có thể ảnh hưởng precedence của aspect/advisor trong phạm vi Spring hỗ trợ, nhưng còn các quy tắc riêng của transaction/cache/security; đừng giả định thứ tự chỉ từ tên annotation.

Đối với performance:

1. Dùng pointcut hẹp và tên metric ổn định; tránh reflection, annotation scanning, request serialization và network calls mỗi lần.
2. Nếu logger bị tắt, tránh xây message bằng nối chuỗi, JSON serialization hay gọi `toString()` **trước** khi kiểm tra level.
3. Chỉ log sự kiện hữu ích, ví dụ slow call vượt threshold, lỗi đã chuẩn hóa hoặc audit được thiết kế riêng.
4. Theo dõi overhead của chính hạ tầng telemetry; nếu collector hỏng, ứng dụng chính không nên bị kéo sập vì một bộ đo tùy chọn.
5. Benchmark trên request path thật: baseline không aspect, bật aspect không logging, bật logging có threshold, kịch bản overload, p95/p99 latency và throughput. Không suy ra hiệu năng từ một con số demo không có môi trường.
6. Dùng sampling/rate limit phù hợp trong production để tránh log bùng nổ khi hàng loạt request đều chậm, nhưng **không** giảm sự kiện audit bắt buộc theo cách làm mất dữ liệu.

**Lỗi phổ biến:** “Log level = DEBUG nên gần như không tốn gì” có thể sai nếu code vẫn tính `toJson(args)` hoặc `expensiveToString()` trước lệnh `logger.debug`. Parameterized logging trì hoãn một phần định dạng, **không trì hoãn mọi biểu thức Java đã được tính làm arguments**.

## 12. Checklist bảo trì và rà soát bảo mật

Trước khi đưa custom aspect vào production, kiểm tra:

- [ ] Aspect chỉ nhắm vào các operation cần thiết, không phủ tùy tiện mọi method.
- [ ] `@Around` gọi `proceed()` theo contract và không vô tình nuốt/mất exception.
- [ ] Biến thời gian/context của mỗi invocation nằm trong local scope; shared counters thread-safe.
- [ ] Không gọi HTTP/JDBC/file I/O nặng trực tiếp trong mọi advice invocation mà không có yêu cầu nghiệp vụ rõ.
- [ ] Log payload tuân theo **allowlist**: không `getArgs()`, raw request/response, token, cookie, password, secret hoặc toàn bộ exception message.
- [ ] Tag metric cardinality thấp và thời gian retention được kiểm soát.
- [ ] Nếu có async telemetry: queue, rejection, shutdown, retry, loss policy và context propagation rõ ràng.
- [ ] Nếu cần audit bền vững: workflow/transaction/outbox hoặc durable delivery được kiểm chứng, không dùng best-effort log.
- [ ] Test qua **Spring AOP proxy thực tế**, kiểm tra trường dữ liệu không rò và state được ghi nhận ở cả nhánh success/failure.
- [ ] Có đo p95/p99 latency và log volume dưới tải thật; kiểm tra khi logger/backend/collector chậm hoặc lỗi.
- [ ] Đã quy định ai có quyền xem log, thời hạn lưu và cách xử lý log cũ nếu phát hiện dữ liệu nhạy cảm.

**Kết luận kỹ thuật:** custom advice trong Spring nên thực hiện tối thiểu công việc bắt buộc để quan sát lời gọi: đo thời gian, cập nhật metric nhẹ, và log **chọn lọc những trường an toàn** khi có tín hiệu hữu ích. I/O có độ trễ cao và sự kiện audit cần tính bền vững phải có ranh giới kiến trúc rõ ràng; đưa chúng vào mọi advice có thể gây độ trễ và rủi ro bảo mật trên diện rộng.

## 13. Tài liệu tham khảo

- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): `@Around`, `proceed()`, `@After` và semantics của advice.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): Spring AOP proxy, self-invocation, giới hạn proxy-based interception.
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@Async`, `TaskExecutor`, proxy mode và cấu hình executor.
- [Spring Framework — Observability Support](https://docs.spring.io/spring-framework/reference/integration/observability.html): các integration liên quan Micrometer Observation.
- [Micrometer — Concepts](https://docs.micrometer.io/micrometer/reference/concepts.html): meter, timer, tags và cardinality.
- [Logback — AsyncAppender](https://logback.qos.ch/manual/appenders.html#AsyncAppender): queue, khả năng bỏ event và behavior của asynchronous logging.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-performance-maintenance-005">Giới hạn gọi phản chiếu và trích xuất metadata lặp lại</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn gọi phản chiếu và trích xuất metadata lặp lại trong Spring AOP

## 1. Vấn đề: chi phí nhỏ trên mỗi lời gọi có thể cộng dồn

Một `@Around` advice của Spring AOP có thể chạy trên hàng nghìn lời gọi service. Nếu mỗi lần advice đều tìm `Method` từ tên, duyệt annotations, resolve bridge method, đọc metadata class, dựng chuỗi mô tả và tính lại quy tắc cố định, tổng chi phí sẽ tăng theo lưu lượng dù cấu hình của method không thay đổi.

Ví dụ thường gặp trong Spring:

```java
@Around("execution(* com.example.app.service..*.*(..))")
public Object inspect(ProceedingJoinPoint pjp) throws Throwable {
    Method method = ((MethodSignature) pjp.getSignature()).getMethod();
    Audited tag = AnnotatedElementUtils.findMergedAnnotation(
        method, Audited.class);
    // ... mỗi invocation lại thực hiện cùng một bước khám phá metadata
    return pjp.proceed();
}
```

Đoạn trên là **fragment trong một Spring `@Aspect`**, không phải chương trình Java độc lập. Nó còn có vấn đề: với JDK dynamic proxy, `MethodSignature.getMethod()` có thể trả method của interface, trong khi annotation thực tế nằm trên implementation. Mục tiêu tối ưu vì vậy gồm **tính đúng đắn** và **giảm công việc tĩnh lặp lại**, không đơn thuần thay reflection bằng một `Map`.

Phân biệt hai loại dữ liệu:

- **Metadata ổn định theo method và target type:** annotation, tên policy, biểu thức pattern đã biên dịch, cấu hình tĩnh, cấu trúc tham số, quyền ghi log cho method. Có thể resolve một lần rồi tái sử dụng trong một `ApplicationContext`.
- **Dữ liệu từng invocation:** `pjp.getArgs()`, người gọi, request/correlation id, security context, nội dung argument, kết quả, exception, transaction state hoặc thread hiện tại. Phải đọc/đánh giá ở **mỗi lời gọi**, không cache theo method như thể chúng bất biến.

Một `Method` object được lấy từ `MethodSignature` không đồng nghĩa cứ lấy nó là một phép phản chiếu đắt đỏ. Chi phí thường nằm ở phần **khám phá sâu, tìm annotation và dựng lại policy** do code advice tự làm. Spring đã tối ưu và cache một số phần của chính hạ tầng proxy/pointcut; hãy đo logic của mình trước khi thêm tầng cache.

## 2. Cơ chế Spring AOP tạo ra ranh giới tối ưu

Spring AOP dựa trên proxy: lời gọi được intercept khi đi qua Spring bean proxy. Một advisor có pointcut xác định method nào cần được xét; Spring có thể lưu interceptor chain hoặc kết quả matching thích hợp. Tuy nhiên Spring không tự biết thuật toán tự viết trong advice, chẳng hạn `findMergedAnnotation()`, phân tích một annotation nội bộ rồi tạo `AuditPolicy` ở từng lần chạy, có cần memoize hay không.

Vì vậy nên tối ưu theo thứ tự:

1. **Thu hẹp pointcut** để Spring không gọi advice trên method không liên quan.
2. **Ưu tiên binding sẵn** (`@annotation(marker)`, `args(value)`, v.v.) khi chỉ cần annotation/đối số được Spring bind và không phải tự duyệt metadata.
3. Nếu phải đọc/diễn giải metadata phức tạp, **resolve theo một khóa đúng**, cache cả trường hợp không có annotation.
4. Duy trì logic mỗi invocation ở ngoài cache: quyết định phụ thuộc argument, quyền hiện thời, transaction, kết quả, exception.
5. **Đo lại** độ trễ, allocations và số lần resolver chạy dưới workload thật.

### 2.1. Tránh suy diễn từ kiểu proxy

| Đặc điểm | Rủi ro nếu tra metadata giản đơn | Cách xử lý trong Spring |
| --- | --- | --- |
| JDK dynamic proxy | `MethodSignature.getMethod()` có thể trỏ tới interface | Xác định target type và method triển khai |
| CGLIB proxy | `getClass()` của proxy là lớp được Spring tạo | Ưu tiên target type của bean, không cache theo tên lớp proxy sinh ra |
| Generics/bridge methods | Method bridge có thể không mang metadata kỳ vọng | Dùng `AopUtils.getMostSpecificMethod`, vốn giải quyết bridge method |
| Annotation ở interface | Implementation có thể không khai báo trực tiếp | Kiểm tra chính sách tìm annotation qua hierarchy và fallback nếu cần |
| `@AliasFor`/composed annotation | `getAnnotation` trực tiếp không nắm đủ merged semantics | Cân nhắc `AnnotatedElementUtils.findMergedAnnotation` |
| Method overload | Hai method trùng tên nhưng chữ ký khác | Khóa cache cần chứa `Method`, không chỉ tên method |
| Cùng method interface, hai implementation | Policy có thể khác theo target class | Khóa cache cần cả **target class và method** |

Đặc biệt, **không sử dụng một key như `"save"`**: `save(String)`, `save(long)` và `save` trong nhiều bean có thể có chính sách hoàn toàn khác. Cũng không nên mặc định annotation trên interface và implementation có cùng precedence trong mọi cơ chế; hãy kiểm chứng chính sách annotation của ứng dụng với đúng phiên bản Spring.

## 3. Mẫu tối ưu an toàn: cache metadata theo target class + Method

Giả sử một custom Spring aspect cần đọc `@Audited` trên một số service method. Có thể tạo key:

```java
record LookupKey(Class<?> targetType, Method signatureMethod) {}
```

Sử dụng `ConcurrentHashMap<LookupKey, Optional<Rule>>`:

- `targetType` phân biệt các implementation khi method đến từ cùng interface.
- `signatureMethod` giữ tên, parameter types, declaring class và return type theo `Method.equals`; overload không xung đột.
- `Optional<Rule>` lưu được cả **positive hit** (có annotation) và **negative hit** (không có annotation). `ConcurrentHashMap` không nhận `null` làm value, nên nếu resolver trả `null` thì lời gọi sau lại phân giải.
- `computeIfAbsent` làm resolve khi key chưa có. Với resolver thuần túy và không ném exception, các lời gọi đồng thời cùng key không cần tự dựng khóa/lock riêng.
- `Rule` chỉ chứa dữ liệu immutable đã rút ra từ annotation, ví dụ `String category`. Nó không chứa `ProceedingJoinPoint`, arguments, result hay đối tượng request.

**Ranh giới sống của cache** là bean aspect thuộc một Spring context. Đừng tạo cache `static` toàn JVM khi ứng dụng có hot reload, multi-context, plugin hoặc classloader được dỡ, trừ khi có chiến lược xóa/reference phù hợp.

### 3.1. Resolver đúng cho Spring proxy

Một triển khai điển hình:

```java
Method fromSignature = ((MethodSignature) pjp.getSignature()).getMethod();
Class<?> targetType = pjp.getTarget().getClass();

Method specific = AopUtils.getMostSpecificMethod(fromSignature, targetType);

Audited annotation = AnnotatedElementUtils.findMergedAnnotation(
    specific, Audited.class
);

if (annotation == null && !specific.equals(fromSignature)) {
    annotation = AnnotatedElementUtils.findMergedAnnotation(
        fromSignature, Audited.class
    );
}
```

Đây là **fragment thực thi bên trong `@Around`**. `AopUtils.getMostSpecificMethod()` giải quyết trường hợp `Method` đến từ interface, đồng thời xử lý bridge method để tìm định nghĩa cụ thể. `AnnotatedElementUtils.findMergedAnnotation` hỗ trợ tìm merged/meta annotation qua cơ chế của Spring. Fallback về method ban đầu thể hiện rõ trường hợp annotation chỉ hiện diện ở mặt API mà đường tìm trên implementation chưa phát hiện.

Trong fixture đơn giản có target không null, `pjp.getTarget().getClass()` là đủ. Với hạ tầng đặc biệt có target source động hoặc đối tượng target không sẵn có, cần điều chỉnh cách xác định target type, tránh giả định tất cả proxy đều quản lý cùng một target instance. `AopUtils.getTargetClass(bean)` hữu ích khi quan sát bean từ container.

**Lưu ý quan trọng về semantics:** annotation trực tiếp, meta-annotation, interface annotation và override có thể cho kết quả khác nhau theo chính sách và API tìm kiếm. Test phải bao phủ cách annotation được đặt thực tế; đừng dùng fallback để hợp thức hóa những khai báo không mong muốn.

## 4. Ví dụ tích hợp chạy được với Spring AOP và JUnit 5

Ví dụ sử dụng Java 17+, Spring Framework 6.2.8 và Maven. Ta muốn kiểm chứng bốn việc:

1. Lời gọi vào service qua proxy Spring và aspect được thực thi.
2. Hai lần gọi cùng method dùng **một lần resolve metadata**.
3. Overload `pay(String)` và `pay(int)` không dùng chung policy.
4. Method không được annotate vẫn được cache negative lookup, không phân giải đi phân giải lại.
5. Lời gọi song song cùng method không làm hỏng trạng thái cache.

### 4.1. Cấu trúc và dependencies

```text
spring-aop-metadata-cache/
├── pom.xml
└── src/test/java/lab/MetadataCacheTest.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lab</groupId>
    <artifactId>spring-aop-metadata-cache</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring.version>6.2.8</spring.version>
        <junit.version>5.11.4</junit.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>${spring.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>${spring.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.22.1</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <version>3.13.0</version>
                <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <version>3.5.2</version>
                <artifactId>maven-surefire-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

`aspectjweaver` trong ví dụ cung cấp thành phần parse/match `@AspectJ` cho Spring; **fixture không bật AspectJ compile-time hoặc load-time weaving**. `@EnableAspectJAutoProxy` tạo Spring AOP proxy.

### 4.2. Một file test hoàn chỉnh

`src/test/java/lab/MetadataCacheTest.java`:

```java
package lab;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.junit.jupiter.api.Assertions.*;

class MetadataCacheTest {

    @Test
    void reuses_positive_and_negative_metadata_without_mixing_overloads() {
        try (var context =
                 new AnnotationConfigApplicationContext(Config.class)) {
            PaymentService service = context.getBean(PaymentService.class);
            MetadataAspect aspect = context.getBean(MetadataAspect.class);
            Trace trace = context.getBean(Trace.class);

            assertTrue(AopUtils.isAopProxy(service));

            assertEquals("paid:A", service.pay("A"));
            assertEquals("paid:B", service.pay("B"));
            assertEquals("priority:7", service.pay(7));
            assertEquals("priority:8", service.pay(8));
            assertEquals("healthy", service.health());
            assertEquals("healthy", service.health());

            assertEquals(List.of(
                "policy:payments", "target:pay:A",
                "policy:payments", "target:pay:B",
                "policy:priority", "target:priority:7",
                "policy:priority", "target:priority:8",
                "policy:none", "target:health",
                "policy:none", "target:health"
            ), trace.events());

            // Hai overload và health() = đúng ba khóa metadata.
            assertEquals(3, aspect.cacheSize());
            assertEquals(3, aspect.resolutionCount());
        }
    }

    @Test
    void concurrent_calls_share_one_resolution_for_the_same_method()
            throws Exception {
        try (var context =
                 new AnnotationConfigApplicationContext(Config.class)) {
            PaymentService service = context.getBean(PaymentService.class);
            MetadataAspect aspect = context.getBean(MetadataAspect.class);
            Trace trace = context.getBean(Trace.class);

            ExecutorService pool = Executors.newFixedThreadPool(6);
            try {
                List<Callable<String>> tasks = IntStream.range(0, 24)
                    .mapToObj(n ->
                        (Callable<String>) () -> service.pay("C" + n))
                    .toList();

                List<Future<String>> futures = pool.invokeAll(
                    tasks, 10, TimeUnit.SECONDS);

                for (int i = 0; i < futures.size(); i++) {
                    assertEquals("paid:C" + i,
                        futures.get(i).get(10, TimeUnit.SECONDS));
                }

                assertEquals(1, aspect.cacheSize());
                assertEquals(1, aspect.resolutionCount());
                assertEquals(48, trace.events().size());
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Audited {
        String value();
    }

    public static class Trace {
        private final List<String> events = new CopyOnWriteArrayList<>();

        void add(String text) {
            events.add(text);
        }

        List<String> events() {
            return List.copyOf(events);
        }
    }

    public static class PaymentService {
        private final Trace trace;

        public PaymentService(Trace trace) {
            this.trace = trace;
        }

        @Audited("payments")
        public String pay(String code) {
            trace.add("target:pay:" + code);
            return "paid:" + code;
        }

        @Audited("priority")
        public String pay(int id) {
            trace.add("target:priority:" + id);
            return "priority:" + id;
        }

        public String health() {
            trace.add("target:health");
            return "healthy";
        }
    }

    @Aspect
    public static class MetadataAspect {
        private record LookupKey(
            Class<?> targetType,
            Method signatureMethod
        ) {}

        private record Rule(String category) {}

        private final Trace trace;
        private final ConcurrentMap<LookupKey, Optional<Rule>> cache =
            new ConcurrentHashMap<>();
        private final AtomicInteger resolutions = new AtomicInteger();

        public MetadataAspect(Trace trace) {
            this.trace = trace;
        }

        @Around("bean(paymentService) && execution(public * *(..))")
        public Object observe(ProceedingJoinPoint pjp) throws Throwable {
            Method signatureMethod =
                ((MethodSignature) pjp.getSignature()).getMethod();
            Class<?> targetType = pjp.getTarget().getClass();

            LookupKey key = new LookupKey(targetType, signatureMethod);
            Optional<Rule> rule = cache.computeIfAbsent(
                key, this::resolveRule);

            trace.add(rule
                .map(value -> "policy:" + value.category())
                .orElse("policy:none"));

            return pjp.proceed();
        }

        private Optional<Rule> resolveRule(LookupKey key) {
            resolutions.incrementAndGet();

            Method specific = AopUtils.getMostSpecificMethod(
                key.signatureMethod(), key.targetType());

            Audited annotated = AnnotatedElementUtils.findMergedAnnotation(
                specific, Audited.class);

            if (annotated == null
                    && !specific.equals(key.signatureMethod())) {
                annotated = AnnotatedElementUtils.findMergedAnnotation(
                    key.signatureMethod(), Audited.class);
            }

            return Optional.ofNullable(annotated)
                .map(a -> new Rule(a.value()));
        }

        int cacheSize() {
            return cache.size();
        }

        int resolutionCount() {
            return resolutions.get();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class Config {
        @Bean
        public Trace trace() {
            return new Trace();
        }

        @Bean
        public PaymentService paymentService(Trace trace) {
            return new PaymentService(trace);
        }

        @Bean
        public MetadataAspect metadataAspect(Trace trace) {
            return new MetadataAspect(trace);
        }
    }
}
```

Trong `MetadataAspect`, vòng đời cache gắn với bean trong context. Hai `pay` overload là các `Method` khác nhau; `health()` được ghi một entry `Optional.empty()` để lần sau không phải tìm annotation nữa. **Không bao giờ cache `pjp` hoặc code argument**: mỗi lần `pay("A")` và `pay("B")` vẫn phải thực thi method, ghi trace và tính kết quả riêng.

### 4.3. Chạy test và hiểu các assertion

Trong thư mục chứa `pom.xml`:

```bash
mvn test
```

Kết quả kỳ vọng ở mức test (không phải stdout nguyên văn của Surefire):

```text
reuses_positive_and_negative_metadata_without_mixing_overloads  PASS
concurrent_calls_share_one_resolution_for_the_same_method     PASS
```

Bài thứ nhất gọi sáu lần qua **cùng một Spring proxy**. Kỳ vọng resolver chạy đúng **3 lần**: một lần cho `pay(String)`, một cho `pay(int)`, một cho `health()`. Số event bằng 12 vì mỗi lời gọi có một event advice và một event target. Nếu code cache theo tên method `"pay"` thay vì `Method`, test sẽ nhận nhầm `payments` và `priority`, khiến assertion sai.

Bài thứ hai dùng 24 task trên executor và chờ `Future` hoàn thành. Kỳ vọng `cacheSize()==1` và `resolutionCount()==1`: tất cả task gọi đúng `pay(String)` trên cùng target type. Không assert **thứ tự toàn cục** của các event từ nhiều thread; ở phép thử này, điều cần chứng minh là mỗi invocation có một event aspect, một event target và lookup metadata không bị nhân lên do cạnh tranh.

Các test cũng kiểm tra `AopUtils.isAopProxy` ở trường hợp tuần tự. Nếu gọi `new PaymentService(trace).pay("A")` ngoài Spring container, aspect không chạy, nên đó **không phải** một phép kiểm hiệu năng/binding Spring AOP hợp lệ.

## 5. Những cách tối ưu tốt hơn một cache tùy chỉnh

### 5.1. Thu hẹp pointcut trước

Nếu policy chỉ áp dụng cho một số service method, thay pointcut quá rộng:

```java
@Around("execution(* *(..))")
```

bằng phạm vi Spring bean/package và điều kiện thích hợp:

```java
@Around(
    "within(com.example.app.service..*)"
    + " && execution(public * *(..))"
)
```

Đây là fragment trong `@Aspect` của ứng dụng có package tương ứng. Nếu chạy trên fixture `lab`, dùng `bean(paymentService)` như ví dụ chính. Một pointcut hẹp giảm số invocation phải qua code advice. Tuy nhiên nếu policy cần quan sát cả method không annotate để sinh metric mặc định, đừng thêm điều kiện `@annotation` làm mất chúng.

### 5.2. Nhận annotation trực tiếp qua binding của Spring

Nếu chỉ cần giá trị annotation và pointcut chỉ được áp dụng cho method có annotation, có thể chuyển phần khám phá annotation sang điểm cắt:

```java
@Around(
    value = "execution(public * com.example.app.service..*.*(..))"
          + " && @annotation(audited)",
    argNames = "audited"
)
public Object observe(
        ProceedingJoinPoint pjp,
        Audited audited) throws Throwable {
    // Spring bind annotation khớp cho join point hiện hành.
    recordCategory(audited.value());
    return pjp.proceed();
}
```

Đây là fragment, với `recordCategory` là hàm giả định của ứng dụng. Nếu chỉ quan tâm annotated methods, cách này có thể giúp giảm logic tìm kiếm annotation thủ công. **Không mặc định `@annotation` trên interface luôn match giống như lookup hierarchy tùy chỉnh**; hãy test phương thức interface/implementation thực tế. Binder cũng có chi phí; dùng profiler để biết lợi ích so với resolver có cache.

### 5.3. Tạo metadata một lần ở giai đoạn khởi tạo

Với số lượng method hữu hạn và policy tĩnh, có thể quét metadata khi tạo Spring bean hoặc thiết kế registry/configuration lúc startup, rồi advice chỉ lookup kết quả. Cách này chuyển chi phí sang **startup**, thích hợp khi request path cần độ trễ thấp. Nhưng phải tránh quét rộng toàn classpath, xử lý đúng interface/bridge methods và đồng bộ với bean/proxy được tạo sau. Nếu context có bean đăng ký động, lazy beans hoặc classloader riêng, cache **lazy theo lần gọi đầu** có thể đơn giản và an toàn hơn.

### 5.4. Chỉ cache bước phân giải, không cache quyết định runtime

Ví dụ annotation:

```java
@Audited("PAYMENT")
public String pay(String orderId) { /* ... */ }
```

Cache `"PAYMENT"` hay một `Rule` bất biến theo method là hợp lý. **Không cache** câu hỏi “request này được phép ghi log không?” nếu nó phụ thuộc user, quyền, sampling rate, feature flag động, nội dung payload hoặc config nóng. Có thể cache cấu trúc rule, rồi đánh giá rule với `pjp.getArgs()` và context hiện hành ở mỗi invocation.

## 6. Thiết kế key và vòng đời cache trong ứng dụng lớn

### 6.1. Key phải đúng semantics

Một `Method` chứa declaring class, tên, parameter types và return type. Tuy nhiên method lấy từ interface có thể được nhiều implementation chia sẻ. Dùng `Class<?> targetType` cùng `Method signatureMethod` phân biệt theo class thực thi. Khi dùng scoped proxy, target có thể thay đổi trong một vòng đời proxy; dựa vào target type của **invocation hiện tại**, không giả định mọi instance được proxy có cùng metadata nếu ứng dụng cho phép thay target.

Không dùng `method.getName()` đơn lẻ; `targetType.getSimpleName()` cũng có thể trùng giữa nhiều package/classloader. Không đưa `pjp.getArgs()` vào key chỉ để cache annotation; sẽ biến cache thành một entry mỗi request và có thể giữ dữ liệu nhạy cảm.

### 6.2. Negative caching

`ConcurrentHashMap.computeIfAbsent(key, resolver)` không cache kết quả nếu mapping function trả `null`. Với rất nhiều method không được annotate, điều này làm resolver chạy mãi trên mỗi invocation. Dùng `Optional.empty()`, enum sentinel hoặc một entry immutable biểu diễn “không có rule”. Test riêng ca không annotation, như `health()` phía trên.

### 6.3. Concurrent initialization

Một `HashMap` thường không an toàn khi nhiều request thread cùng chạy aspect. `ConcurrentHashMap` và `computeIfAbsent` phù hợp cho lazy metadata resolution nếu resolver là **pure/nhanh**, không tự cập nhật cùng map theo cách đệ quy và không phụ thuộc state thay đổi mỗi invocation. Resolver ném exception sẽ không tạo cache entry thành công; những invocation tiếp theo có thể thử lại. Với tác vụ khởi tạo rất đắt, nên đo contention tại lần cold start và cân nhắc preload.

### 6.4. Rò rỉ classloader

Một cache `static ConcurrentHashMap<Method, ?>` giữ tham chiếu mạnh đến `Method`, `Class` và gián tiếp đến classloader. Trong Spring Boot DevTools restart, môi trường đa tenant nhiều classloader, plugin hot deployment hoặc test tạo context liên tục, cache tĩnh có thể ngăn classloader cũ được thu gom.

Nên gắn cache với vòng đời Spring bean, dùng cache có cơ chế weak-reference/`ClassValue` trong trường hợp rất đặc thù, hoặc dọn cache khi context đóng. `ClassValue` giải quyết nhóm vấn đề cache theo class nhưng cần thiết kế thêm lớp mapping theo method; đừng đổi sang nó chỉ vì thấy tên API. Dù cache thuộc bean, các bean/cache singleton sống lâu vẫn nên quan sát kích thước cache.

### 6.5. Cấu hình động và invalidation

Nếu policy được tạo từ một annotation bất biến tại thời điểm compile, cache có thể sống suốt context. Nếu metadata trộn với file cấu hình động hoặc tenant policy, tách rõ:

- `ResolvedMethodMetadata`: thông tin tĩnh lấy từ `Method`/annotations.
- `PolicyRuntimeState`: config có version, có thể thay đổi và cần cơ chế refresh/versioned lookup.

Thay đổi source/annotation yêu cầu rebuild/restart để bytecode thay đổi; còn config runtime đổi không nên bị “đóng băng” trong một `Optional<Rule>` chưa được thiết kế invalidation.

## 7. Khi nào reflection thực sự gây vấn đề?

Không nên tối ưu nếu chưa có bằng chứng. Lập phép đo trước/sau tại cùng workload:

| Chỉ số | Trước tối ưu | Sau tối ưu | Ý nghĩa |
| --- | --- | --- | --- |
| Lời gọi resolver metadata | Gần bằng số invocation ở code thủ công | Gần bằng số **method + target type duy nhất** đã được truy cập | Chứng minh cache thực sự tránh phân giải lặp |
| Request latency p50/p95/p99 | Thu thập baseline | So với baseline | Kiểm tra tác động lên độ trễ |
| Allocation rate | Số object tạo do scan/merge/parse | So sau cache | Giảm áp lực GC nếu có |
| CPU profile | Stack ở annotation/reflection APIs | Ít thời gian ở stack cũ | Đúng nơi gây chi phí |
| Startup/cold hit | Không có preload | Có thể phát sinh resolve một lần | Chú ý tail latency ở cold path |
| Cache entries | Không áp dụng | Theo số method/key quan sát | Xác nhận không tăng theo số request |

Đừng đưa con số “tăng tốc 10 lần” khi chưa đo. Một `@Around` proxy invocation vốn đã có overhead nhất định; nếu target dành hàng trăm mili giây để truy cập database thì việc cache lookup annotation có thể khó quan sát. Ngược lại, với getter rất nhanh, logging/metadata allocation trong advice có thể chiếm tỷ trọng đáng kể.

**Cách đo phù hợp:** trước tiên unit/integration test kiểm tra `resolutionCount()`, sau đó benchmark bằng JMH nếu hot-path đáng tối ưu. Chạy warmup đủ, dùng cùng JDK/Spring/config/proxy, cố định thread count và dataset; đo cold và steady state riêng. Không lấy vài lần `System.nanoTime()` trong một bài test JUnit làm bằng chứng hiệu năng đáng tin.

## 8. Tránh tối ưu sai chỗ

1. **Cache `Method.invoke()` result:** `Method` là metadata; kết quả gọi target phụ thuộc request. Cache response là trách nhiệm của `@Cacheable`/business cache, khác bài toán này.
2. **Bỏ `proceed()` để “giảm overhead”:** làm thay đổi hành vi nghiệp vụ. Advice `@Around` phải giữ semantics cần thiết về return/exception.
3. **Cache annotation theo method name:** sai với overload hoặc cùng tên ở nhiều target.
4. **Bỏ qua metadata trên implementation:** với JDK dynamic proxy có thể nhìn thấy method interface thay vì method thực.
5. **Chỉ cache positive lookup:** method không annotate bị tìm lại trên mọi request.
6. **Dùng cache `static` không giới hạn:** có thể giữ classloader và metadata mãi mãi.
7. **Tạo cache mới ở mỗi `@Around`:** map không tái sử dụng giữa invocation nên không giải quyết vấn đề.
8. **Đưa dữ liệu request vào `Rule` đã cache:** gây sai ngữ cảnh, rò rỉ thông tin, khó debug.
9. **Thay toàn bộ Spring advisor chain bằng reflection thủ công:** Spring đã có pointcut, proxy, method matcher và adapter; custom cache nên phục vụ metadata của chính business aspect.
10. **Dùng cache để che lỗi matching:** trước hết phải xác minh pointcut thực sự match method và Spring proxy được gọi; một cache “nhanh” nhưng resolve sai annotation vẫn là lỗi.

## 9. Quy trình áp dụng trong Spring Boot thực tế

1. Chỉ rõ **custom aspect nào đang tốn chi phí**: `@Around`, `@Before`, `@AfterReturning` đang đọc metadata nào, ở mỗi method hay mỗi request. Đo hoặc ghi bộ đếm của resolver trước khi sửa.
2. Xác nhận Spring bean được gọi **qua proxy**, loại proxy và target class; kiểm tra self-invocation nếu advice thiếu. Với JDK proxy, kiểm tra cả interface và implementation annotations.
3. Thu hẹp pointcut với `execution`, `within`, `bean` hoặc annotation binding khi vẫn giữ đúng tập method mong muốn.
4. Nếu cần cache, định nghĩa **giá trị immutable** và khóa `(targetClass, signatureMethod)` hoặc một khóa tương đương; đưa lookup vào `computeIfAbsent`, cache cả absence.
5. Tách phần runtime ngoài resolver: args, authentication, transaction, sampling, logging payload và result; giữ nguyên `proceed()` cùng semantics exception.
6. Viết integration test có method annotated, unannotated, overload, lời gọi lặp và nếu cần concurrent requests. Assert cả **trace kết quả nghiệp vụ** lẫn **số lần resolver**.
7. Kiểm tra cache scope/classloader lifecycle, metric cache size, invalidation nếu policy động.
8. Benchmark trên đúng kiểu proxy, version Spring/JDK và workload, cân nhắc chi phí cold start so với p95/p99. Chỉ giữ tối ưu nếu số đo cho thấy lợi ích thực sự.

### Checklist trước khi merge

- [ ] `AopUtils.isAopProxy(bean)` đúng trong integration test.
- [ ] Method/interface/bridge được resolve theo đúng target type.
- [ ] Không có key collision giữa overload, implementation hay classloader.
- [ ] Có negative caching và không cache request payload/context.
- [ ] Resolver thread-safe, bất biến với dữ liệu runtime và không viết đệ quy vào map.
- [ ] Trace nghiệp vụ/exception của advice giữ nguyên trước và sau tối ưu.
- [ ] Test xác nhận số lần resolve giảm khi nhiều invocation trùng key.
- [ ] Không có cache tĩnh giữ classloader qua nhiều context hoặc có chính sách cleanup phù hợp.
- [ ] Phép đo hiệu năng trước/sau phản ánh đúng workload, không chỉ một synthetic loop.

## 10. Kết luận

Để **giới hạn lời gọi phản chiếu và trích xuất metadata lặp lại trong Spring AOP**, hãy xác định đâu là metadata bất biến theo method, đâu là dữ liệu từng request. Ưu tiên thu hẹp pointcut hoặc Spring annotation binding khi phù hợp. Nếu custom aspect thực sự phải tự phân giải annotation và policy, dùng một cache gắn với vòng đời Spring context, khóa theo **target class + method**, hỗ trợ bridge/interface và cache cả trường hợp không tìm thấy annotation.

Fixture chứng minh một method được gọi nhiều lần vẫn chỉ phải resolve metadata một lần, hai overload không dùng sai policy của nhau, và method không annotate cũng không bị tìm lặp. Sự đúng đắn được bảo vệ bởi trace/assertion; hiệu năng cuối cùng phải dựa vào profiler và benchmark trên ứng dụng thật.

### Tài liệu tham khảo Spring

- [Spring Framework — Pointcuts](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): thu hẹp matching, `execution`, `within`, `bean`, `@annotation`.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB proxy và giới hạn self-invocation.
- [Spring Framework — AopUtils API](https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/aop/support/AopUtils.html): `getMostSpecificMethod`, `isAopProxy`, `getTargetClass`.
- [Spring Framework — AnnotatedElementUtils API](https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/core/annotation/AnnotatedElementUtils.html): merged annotation lookup.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): Spring diễn giải `@Aspect` nhưng runtime vẫn là Spring AOP proxy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-performance-maintenance-006">Giảm phụ thuộc vòng và tránh aspect gọi ngược chính bean bị chặn</a>

<details>
<summary>Xem chi tiết</summary>

# Giảm phụ thuộc vòng và tránh aspect gọi ngược chính bean bị chặn trong Spring AOP

## 1. Hai loại “vòng” rất khác nhau

Khi xây dựng Spring AOP cho audit, logging, metrics, authorization hay kiểm soát truy cập, advice thường cần sử dụng một số dependency. Đặt sai dependency hoặc gọi nhầm Spring proxy có thể tạo thành hai vấn đề:

1. **Vòng phụ thuộc khi tạo bean (circular bean dependency):** ví dụ `OrderService -> AuditAspect -> OrderService` qua constructor. Spring phải tạo bean này để truyền vào bean kia, trong khi bean kia cũng đòi bean đầu tiên. Constructor cycle thường khiến context khởi tạo thất bại với `BeanCurrentlyInCreationException`, có thể được bao trong `BeanCreationException` hoặc `UnsatisfiedDependencyException`.
2. **Vòng gọi lúc chạy (advice re-entry / recursive invocation):** aspect đã tạo được, nhưng trong advice lại gọi **đúng Spring bean/proxy đang bị pointcut chặn**, khiến advice được thực thi lại. Chuỗi `proxy -> advice -> proxy -> advice -> ...` có thể chạy đến `StackOverflowError` hoặc gây trùng audit, transaction, request và chi phí CPU.

Hai lỗi có thể xảy ra độc lập. Không có vòng dependency **không có nghĩa** không bị recursion khi chạy. Ngược lại, vòng phụ thuộc khi tạo bean có thể làm ứng dụng không chạy tới lúc phương thức được gọi.

**Phạm vi:** tập trung vào bean, `@Aspect`, advisor và Spring AOP **dựa trên proxy**. Đây là vấn đề thiết kế Spring container và invocation chain, không cần nghiên cứu AspectJ bytecode weaving để áp dụng các biện pháp của bài.

## 2. Phân biệt vòng tạo bean, vòng lời gọi và self-invocation

~~~text
A. Circular bean construction

OrderService(constructor AuditAspect)
         |
         v
AuditAspect(constructor OrderService)
         |
         +-----------> quay lại OrderService chưa tạo xong

Kết quả: context có thể không khởi tạo được.
~~~

~~~text
B. Re-entry do aspect gọi vào proxy đang được intercept

Controller -> OrderService proxy.place("A")
                 -> @Around OrderAspect
                      -> OrderService proxy.place("AUDIT")
                           -> @Around OrderAspect
                                -> OrderService proxy.place("AUDIT")
                                     -> ...
Kết quả: advice bị gọi lặp, có thể StackOverflowError.
~~~

~~~text
C. Self-invocation thông thường trong chính target

Client -> OrderService proxy.outer()
             -> target.outer()
                  -> this.inner()
                       -> target.inner()

Trong Spring AOP: advice chỉ gắn lên inner() không chạy ở lần self-call này.
Không có chuyện proxy tự động intercept mỗi lần this.inner() được gọi.
~~~

Điểm dễ nhầm: trường hợp B đi qua **proxy Spring từ bên ngoài target**, nên advice có cơ hội chạy lại. Trường hợp C dùng `this` bên trong target và bỏ qua proxy, nên **không** kích hoạt advice riêng của `inner()`. Không lấy hiện tượng self-invocation bypass proxy để kết luận rằng gọi lại một proxy từ advice cũng sẽ tự bỏ qua AOP.

## 3. Cách phát sinh circular dependency giữa Spring bean và aspect

Xem mẫu *không nên sử dụng* dưới đây:

~~~java
@Service
public class OrderService {
    private final AuditAspect auditAspect;

    public OrderService(AuditAspect auditAspect) {
        this.auditAspect = auditAspect;
    }

    public void place(String id) {
        // Business logic.
    }
}

@Aspect
@Component
public class AuditAspect {
    private final OrderService orderService;

    public AuditAspect(OrderService orderService) {
        this.orderService = orderService;
    }

    @Before("execution(* com.example.orders.OrderService.place(..))")
    public void beforePlace() {
        // ...
    }
}
~~~

Đây là **hai class minh họa trong các file độc lập**, cần import `Service`, `Aspect`, `Component` và `Before`. Khi Spring cố khởi tạo cả hai singleton bằng constructor injection, đồ thị dependency chứa chu trình trực tiếp. Trên Spring Boot hiện đại, cơ chế circular references thường bị vô hiệu hóa theo mặc định; ngay cả khi bật cơ chế cho phép circular references, **constructor cycle cũng không được giải quyết chỉ bằng cấu hình đó**.

Một chu trình gián tiếp còn khó thấy hơn:

~~~text
PaymentService
    -> AuditAspect
         -> AuditPublisher
              -> PaymentService
~~~

Aspect có thể không inject target trực tiếp, nhưng dependency sâu trong publisher gọi vào target khiến cả đồ thị bean vẫn đóng vòng. Trong thực tế phải kiểm tra **toàn chuỗi từ constructor của aspect qua publisher, repository, client và facade**, không chỉ các field xuất hiện trong class aspect.

### Nguyên tắc tách đồ thị dependency

- Business service phụ thuộc vào các cổng nghiệp vụ (`PaymentRepository`, `PricingPort`, ...) để thực hiện công việc.
- Aspect phụ thuộc vào **observer/recorder độc lập**, ví dụ `AuditSink` hoặc `ObservationRecorder`; sink chỉ ghi event và **không gọi ngược service nghiệp vụ** đang là target của aspect.
- Framework wiring tạo proxy cho service và aspect tham gia advisor chain; **service không inject aspect để gọi thủ công**.
- Nếu cả service lẫn aspect cần một chức năng chung, đưa nó vào **component dùng chung cấp thấp hơn** không phụ thuộc ngược vào hai bên.

~~~text
CheckoutFacade -> CheckoutPort (Spring AOP proxy)
                      |
                      v
                 CheckoutService -> CheckoutRepository

AuditAspect -> AuditSink -> Dedicated audit storage
       |
       +-- advice được Spring gọi khi proxy intercept, không phải service inject aspect

Không có đường AuditSink -> CheckoutPort quay ngược để tạo vòng gọi.
~~~

Một `AuditSink` có thể dùng queue, logger, metrics registry hoặc repository riêng tùy hệ thống, nhưng vẫn phải quản lý phạm vi pointcut để các thao tác của sink **không vô tình bị chính aspect này chặn**.

## 4. Aspect gọi ngược bean bị chặn nguy hiểm như thế nào?

Hãy xem ví dụ *cố ý sai*, **không chạy nó trong ứng dụng**:

~~~java
@Aspect
@Component
public class UnsafeAuditAspect {
    private final CheckoutPort checkoutPort;

    public UnsafeAuditAspect(CheckoutPort checkoutPort) {
        this.checkoutPort = checkoutPort;
    }

    @Before("execution(* com.example.orders.CheckoutPort.place(..))")
    public void audit() {
        checkoutPort.place("AUDIT"); // Gọi lại proxy => advice lặp lại.
    }
}
~~~

Điều kiện khiến lời gọi lặp là Spring đang inject vào aspect một reference **đi qua proxy** và phương thức `place()` thỏa pointcut. Khi advice gọi `checkoutPort.place("AUDIT")`, framework có thể chạy lại chính `@Before`, rồi advice lại gọi `place`, và cứ thế. Vòng dependency khi wiring có thể làm context fail **trước đó**; nếu dùng lazy lookup để né lỗi khởi tạo bean thì vòng gọi khi runtime vẫn còn.

Ngay cả khi aspect chỉ gọi một method **khác** trên cùng bean, một pointcut quá rộng có thể dẫn tới recursion:

~~~java
@Around("execution(* com.example.orders..*(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    // Nếu recordAudit() cũng là method của bean bị pointcut match:
    // auditService.recordAudit(...); // Có thể kích hoạt cùng aspect lần nữa.
    return pjp.proceed();
}
~~~

Đây chỉ là đoạn minh họa cho phạm vi pointcut. Không coi việc gọi `pjp.proceed()` là nguyên nhân mặc định: `proceed()` chuyển tiếp invocation hiện tại theo advice chain. **Gọi lại proxy qua một method phù hợp**, hoặc **gọi `proceed()` nhiều lần ngoài chủ đích**, mới làm tăng số lượt xử lý ngoài kỳ vọng.

Các hậu quả có thể quan sát gồm log/audit nhân đôi, telemetry khuếch đại, giao dịch lồng nhau, request ra ngoài lặp, không trả response, tăng latency, CPU cao hoặc `StackOverflowError`. Nếu code bắt `Throwable` vô tội vạ, lỗi `StackOverflowError` còn có thể bị che lấp và khiến nguyên nhân khó truy tìm hơn.

## 5. Biện pháp ưu tiên: thu hẹp pointcut và tách sink an toàn

Một thiết kế vững hơn là:

1. **Pointcut chọn đúng cổng public của nghiệp vụ**, ví dụ chỉ `CheckoutPort.place(..)` trên Spring bean. Tránh mẫu wildcard xuyên toàn ứng dụng như `execution(* com.example..*(..))` nếu không có phạm vi bảo vệ.
2. **Aspect chỉ thu thập thông tin cần thiết rồi chuyển tới sink độc lập.** Sink không inject lại CheckoutPort hoặc facade gọi CheckoutPort.
3. **Sink nằm ngoài join point mà aspect bắt**. Điều này giúp cả audit lẫn metrics hoạt động mà không kích hoạt chính advice đang ghi nhận.
4. **Service giữ dependency nghiệp vụ của nó**; không inject aspect và không gọi method của aspect như một service thông thường.
5. **Chỉ gọi `ProceedingJoinPoint.proceed()` đúng số lần hợp đồng yêu cầu.** Một aspect logging/audit thông thường nên proceed một lần, không retry ngầm nếu không có yêu cầu rõ ràng.

Ví dụ tốt có thể tóm tắt thành:

~~~text
Client
  -> CheckoutPort proxy.place(id)
       -> AuditAspect.around()
           -> AuditSink.record("enter", id)
           -> proceed()
                -> CheckoutService.place(id)
           -> AuditSink.record("success", id)
  <- return

AuditSink.record không gọi lại CheckoutPort proxy.
~~~

Pointcut phủ định để loại trừ một package sink có thể giúp giảm phạm vi, ví dụ `execution(* com.example.orders..*(..)) && !within(com.example.orders.observability..*)`. Tuy vậy, **chỉ dựa vào phủ định package** để bảo vệ một aspect quá rộng là mong manh khi refactor: ưu tiên chọn rõ package/service interface và method cần can thiệp ngay từ đầu.

## 6. Ví dụ tích hợp chạy được: Spring AOP dùng audit sink không tạo vòng

Ví dụ Java 21 với Spring Boot 3.3.x cung cấp dependency và JUnit 5; test tạo một **Spring ApplicationContext nhỏ**, có AspectJ annotation syntax nhưng dùng **Spring AOP proxy**, không dùng weaving. Tất cả test chạy trong chính Spring container.

### 6.1. Cấu trúc và build

~~~text
spring-aspect-no-cycles/
├── settings.gradle
├── build.gradle
└── src/test/java/com/example/cycles/AopCycleSafetyTest.java
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-aspect-no-cycles'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
}

group = 'com.example'
version = '1.0.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation platform('org.springframework.boot:spring-boot-dependencies:3.3.5')
    testImplementation 'org.springframework.boot:spring-boot-starter-aop'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

### 6.2. Test, target, aspect và sink trong một file

Lưu toàn bộ phần dưới vào `src/test/java/com/example/cycles/AopCycleSafetyTest.java`. Chỉ class test là `public`; các class phụ là top-level package-private trong cùng file. Spring tạo các bean qua `@Bean` nên không cần component scanning.

~~~java
package com.example.cycles;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(AopCycleSafetyTest.Config.class)
public class AopCycleSafetyTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    static class Config {
        @Bean
        CheckoutPort checkoutPort() {
            return new CheckoutService();
        }

        @Bean
        AuditSink auditSink() {
            return new AuditSink();
        }

        @Bean
        AuditAspect auditAspect(AuditSink sink) {
            return new AuditAspect(sink);
        }

        @Bean
        CheckoutFacade checkoutFacade(CheckoutPort port) {
            return new CheckoutFacade(port);
        }
    }

    @Autowired
    CheckoutPort port;

    @Autowired
    CheckoutFacade facade;

    @Autowired
    AuditSink sink;

    @BeforeEach
    void reset() {
        sink.clear();
    }

    @Test
    void adviceRunsExactlyOnceOnSuccessfulProxyCall() {
        assertTrue(AopUtils.isAopProxy(port));
        assertEquals("saved:A-1", facade.submit("A-1"));
        assertEquals(
            List.of("enter:A-1", "success:A-1"),
            sink.events()
        );
    }

    @Test
    void sinkIsOutsideThePointcut() {
        assertFalse(AopUtils.isAopProxy(sink));

        sink.record("manual");
        assertEquals(List.of("manual"), sink.events());
        // Không có "enter:*" nào phát sinh chỉ vì gọi sink.record().
    }

    @Test
    void failureIsObservedOnceAndPropagated() {
        assertThrows(
            IllegalArgumentException.class,
            () -> port.place("")
        );
        assertEquals(
            List.of("enter:", "error:"),
            sink.events()
        );
    }

    @Test
    void targetIsNotExecutedTwice() {
        assertEquals("saved:C-9", port.place("C-9"));
        assertEquals(1, port.completedCount());
        assertEquals(
            List.of("enter:C-9", "success:C-9"),
            sink.events()
        );
    }
}

interface CheckoutPort {
    String place(String orderId);
    int completedCount();
}

class CheckoutService implements CheckoutPort {
    private final AtomicInteger completed = new AtomicInteger();

    @Override
    public String place(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId");
        }
        completed.incrementAndGet();
        return "saved:" + orderId;
    }

    @Override
    public int completedCount() {
        return completed.get();
    }
}

class CheckoutFacade {
    private final CheckoutPort port;

    CheckoutFacade(CheckoutPort port) {
        this.port = port;
    }

    String submit(String orderId) {
        return port.place(orderId);
    }
}

class AuditSink {
    private final List<String> events = new CopyOnWriteArrayList<>();

    void record(String event) {
        events.add(event);
    }

    List<String> events() {
        return List.copyOf(events);
    }

    void clear() {
        events.clear();
    }
}

@Aspect
class AuditAspect {
    private final AuditSink sink;

    AuditAspect(AuditSink sink) {
        this.sink = sink;
    }

    @Around("execution(* com.example.cycles.CheckoutPort.place(..))")
    public Object aroundPlace(ProceedingJoinPoint joinPoint)
            throws Throwable {

        String orderId = (String) joinPoint.getArgs()[0];
        sink.record("enter:" + orderId);

        try {
            Object result = joinPoint.proceed();
            sink.record("success:" + orderId);
            return result;
        } catch (Throwable failure) {
            sink.record("error:" + orderId);
            throw failure;
        }
    }
}
~~~

**Chạy:** tại thư mục dự án có Gradle wrapper (nếu chưa có wrapper, dùng Gradle cài sẵn):

~~~bash
gradle test --tests com.example.cycles.AopCycleSafetyTest
~~~

Kỳ vọng: **4 test pass** khi dependency và Java toolchain đã được thiết lập. Gradle có thể tải thư viện lần đầu; log và stdout của Gradle thay đổi theo môi trường. Các chuỗi `enter:...` và `success:...`/**`error:...`** là event test assert, **không phải output `System.out`**.

Tại sao thí nghiệm thể hiện đúng vấn đề?

- `@EnableAspectJAutoProxy` kết hợp bean `AuditAspect` cho phép Spring tạo advisor/proxy trên `CheckoutPort`.
- `CheckoutFacade` chỉ biết `CheckoutPort` và gọi qua bean được inject từ Spring; nó không phụ thuộc aspect.
- `AuditAspect` chỉ inject `AuditSink`, **không inject CheckoutPort**; đồ thị constructor không có chu trình.
- `AuditSink.record(...)` lưu event và không gọi service. Pointcut chỉ khớp `CheckoutPort.place(..)`, nên sink không bị advice này chặn.
- Test dùng cả positive control (advice chạy) và negative control (gọi sink không chạy advice), đồng thời assert exception vẫn được ném ra.
- `completedCount()` là method khác `place()`, không bị pointcut intercept; test khẳng định target xử lý thành công đúng một lần.

**Giới hạn test:** một kết quả xanh chứng minh **các ca test này** không có vòng gọi; với hệ thống thực còn nhiều aspect, scheduler, async, transaction hoặc interceptor khác, cần kiểm tra call graph và thiết lập advisor tương ứng.

## 7. Các giải pháp dễ bị lạm dụng và tác dụng thực sự

### 7.1. `@Lazy`, `ObjectProvider<T>` và setter injection

Ví dụ code sau là cách *trì hoãn lookup* target bean:

~~~java
@Aspect
@Component
class LazyAuditAspect {
    private final ObjectProvider<CheckoutPort> checkoutProvider;

    LazyAuditAspect(ObjectProvider<CheckoutPort> checkoutProvider) {
        this.checkoutProvider = checkoutProvider;
    }

    // Trong advice:
    // checkoutProvider.getObject().place("AUDIT");
}
~~~

Đoạn trên là **mảnh minh họa** cần import Spring/AspectJ tương ứng. `ObjectProvider` có thể trì hoãn resolution cho tới khi method chạy, còn `@Lazy` có thể tạo lazy proxy cho dependency; điều này **không sửa** chuỗi recursion nếu lời gọi về sau vẫn đi qua `CheckoutPort` proxy và trúng cùng pointcut. Setter injection có thể làm một số circular reference giải được, nhưng khiến bean nhận dependency khi chưa hoàn tất initialization và dễ tạo lỗi khó chẩn đoán. Đừng sử dụng các cơ chế này chỉ để “làm hết lỗi” mà giữ nguyên vòng thiết kế.

### 7.2. Tự inject chính bean hoặc gọi `AopContext.currentProxy()`

Self-injection và `AopContext.currentProxy()` đôi khi được dùng để **chủ động đi qua proxy** khi method cùng bean gọi nhau. Nhưng bài toán hiện tại là **tránh aspect gọi ngược vào cùng proxy**: dùng các cách trên trong advice càng dễ làm advice bị kích hoạt lại. `AopContext.currentProxy()` còn buộc bật `exposeProxy` và làm code phụ thuộc Spring AOP. Giải pháp ưu tiên vẫn là tách component và giữ hướng dependency một chiều.

### 7.3. `ThreadLocal` “đang chạy advice” để chặn recursion

Một re-entry guard có thể là giải pháp chuyên biệt nếu hợp đồng yêu cầu chủ động bỏ qua một số lần advice nested. Tuy nhiên:

- Guard cần `try/finally` để reset; thiếu cleanup có thể ảnh hưởng request sau trên cùng thread.
- Nếu advice phục vụ business operation thật, việc bỏ qua nested call có thể **bỏ sót audit/authorization/transaction** hợp lệ.
- Trong reactive/async code, công việc có thể chuyển thread; `ThreadLocal` không tự truyền trạng thái qua boundary đó.
- Guard chỉ chữa triệu chứng recursion lúc chạy, **không giải quyết circular dependency của Spring container**.

Với use case quan sát/telemetry thông thường, design an toàn hơn là dùng sink không tái nhập vào service và giới hạn pointcut đủ hẹp; không cần state guard toàn cục.

### 7.4. Bật `spring.main.allow-circular-references=true`

Cấu hình này có thể được nhắc tới khi Spring Boot báo vòng phụ thuộc. Nó **không biến constructor cycle thành kiến trúc đúng**, không đảm bảo mọi vòng được giải và **không giải quyết recursion trong advice lúc runtime**. Nếu phải kích hoạt tạm thời để bảo trì hệ thống cũ, cần có kế hoạch tách dependency thật sự và kiểm thử để tránh early bean reference, proxy khác biệt hoặc bean chưa hoàn toàn khởi tạo.

## 8. Khi một aspect cần gọi dịch vụ phụ

Không phải mọi lời gọi ra khỏi aspect đều sai. Ví dụ, aspect audit cần gửi event tới dịch vụ audit hoặc metrics. Điều kiện an toàn là:

1. Dependency của sink **không tạo vòng trở lại service bị chặn** ở cấp constructor wiring.
2. Method được aspect gọi **không thuộc cùng pointcut** hoặc không khiến đường gọi tái nhập join point ngoài ý định. Kiểm tra toàn bộ chuỗi, không chỉ tên method đầu tiên.
3. Sink có chính sách riêng về timeout, failure, retry, buffering hoặc batch; nếu audit fail, cần xác định rõ có làm request nghiệp vụ fail không.
4. Tránh giữ transaction/lock nghiệp vụ quá lâu trong synchronous advice. Nếu sink ghi network/database đồng bộ, chi phí của nó cộng thẳng vào latency của method target.
5. Bảo đảm thứ tự advice và propagation có ý nghĩa khi đồng thời có `@Transactional`, authorization hoặc tracing; đừng để audit sink vô tình gọi lại boundary transaction để tạo thêm transaction không cần thiết.

Ví dụ đường gọi cần **điều tra**:

~~~text
OrderAspect -> AuditPublisher -> EventService -> OrderFacade -> OrderPort proxy
                                  ^                                |
                                  |---------- cùng join point? -----|
~~~

Dù `OrderAspect` không phụ thuộc trực tiếp `OrderPort`, publisher có thể kích hoạt lại proxy qua chuỗi này. Điều đúng phải chứng minh bằng **đồ thị lời gọi runtime** và test; chỉ nhìn đồ thị bean constructors chưa đủ.

### Tách sự kiện quan sát khỏi hành vi nghiệp vụ

Có thể chọn observer độc lập có giao diện nhỏ:

~~~java
public interface AuditRecorder {
    void record(String operation, String reference, boolean success);
}
~~~

Rồi aspect chỉ phụ thuộc `AuditRecorder`. Implementation có thể ghi vào bounded queue hoặc hệ thống log chuyên dụng. Giao diện hẹp và độc lập giúp audit không cần đọc toàn bộ service/facade, giảm coupling và dễ unit-test. Phần điều phối transaction/outbox/event-delivery là một quyết định kiến trúc riêng; nếu event phải ghi **chính xác theo kết quả commit**, không được coi việc log ngay trong `@Around` là bằng chứng một transaction đã commit thành công.

## 9. Chẩn đoán thực tế khi vừa có lỗi startup vừa có lỗi runtime

| Hiện tượng | Kiểm tra đầu tiên | Cách xác minh |
| --- | --- | --- |
| Startup thất bại với `BeanCurrentlyInCreationException` | Constructor graph giữa aspect, service, publisher và các phụ thuộc | Trace chain bean trong exception; vẽ đồ thị từ constructor |
| Startup pass khi thêm `@Lazy` nhưng request treo/stack overflow | Lời gọi trong advice đang trở về Spring proxy cùng pointcut | Trace event/method call với input khác nhau; đếm số lần enter |
| Log/audit được ghi hàng trăm lần cho một request | Aspect trúng chính sink hoặc aspect gọi lại bean bị chặn | Kiểm tra pointcut advisor và danh sách bean/method match |
| Advice không chạy khi gọi `this.inner()` | Self-invocation bypass proxy | Gọi `inner()` từ bean khác qua proxy làm positive control |
| Bỏ `@Component` trên aspect thì lỗi biến mất | Bean aspect có dependency gây cycle hoặc advisor bị kích hoạt | Tìm nguyên nhân dependency/pointcut, tránh xóa aspect theo triệu chứng |
| Một hành vi bị thực hiện nhiều lần | Gọi `proceed()` nhiều lần hoặc re-enter method thông qua proxy | Assert invocation count của target với input cố định |
| Request chậm bất thường | Advice ghi network/database, áp pointcut quá rộng hoặc call loop | Profiling/span timings, event counts theo invocation |
| Chỉ fail khi đủ aspect production | Advisor khác gọi lại target, thứ tự `@Order` hoặc transaction nesting | Test integration với đầy đủ aspect và Spring config thật |

Quy trình khoanh vùng:

1. Đọc **root cause exception startup**. Vòng constructor injection là lỗi trước khi có business invocation.
2. Với runtime, quan sát class/proxy bằng `AopUtils.isAopProxy(bean)`, `AopUtils.isJdkDynamicProxy(bean)` hoặc `AopUtils.isCglibProxy(bean)` nếu cần. In bean name/context để bảo đảm đang gọi đúng reference.
3. Gắn một probe chỉ cho một pointcut nghiệp vụ cụ thể. Đếm `advice-enter`, `proceed`, `target-call`, `advice-exit` và `sink-record`; so chúng ở cùng một request.
4. Kiểm tra advice có inject target, proxy, facade hay publisher có thể đi về target không. Theo dấu cả dependency graph và runtime call graph.
5. Tạo ca positive và negative trong Spring Test; assert **đúng một lần**, trả về đúng, exception không bị nuốt, sink không tái nhập và target không chạy lặp.
6. Sau refactor, chạy với cấu hình AOP, transaction, proxy type, profile và advisor order tương đương production.

## 10. Checklist bảo trì Spring Aspect

- Bean nghiệp vụ **không inject aspect** như một dependency nghiệp vụ.
- Aspect phụ thuộc vào recorder/sink **không gọi ngược** bean nghiệp vụ đang bị chặn.
- Pointcut chỉ chặn đúng API boundary cần quan sát; không áp wildcard toàn bộ ứng dụng khi không có thiết kế rõ.
- Nếu advice là `@Around`, `proceed()` được gọi đúng số lần kỳ vọng và exception/return value vẫn đúng.
- Positive control chứng minh advice chạy; negative control chứng minh sink/method khác không bị advice.
- Không dùng `@Lazy`, `ObjectProvider`, setter circular injection hoặc `allow-circular-references` để che lỗi thiết kế.
- Nếu có nhiều advice (audit, transaction, authorization), kiểm tra thứ tự và side effect cần thiết khi nested call phát sinh.
- Khi refactor, giữ test tích hợp xác nhận không có chu trình tạo bean, không có re-entry và không nhân đôi nghiệp vụ.

**Ghi nhớ:** Spring AOP được kích hoạt khi lời gọi đi qua **Spring proxy** và khớp pointcut. Thiết kế bền vững có hai ranh giới rõ: **dependency của aspect không quay về target**, và **lời gọi từ advice không quay lại proxy đang bị chặn**. Giữ hai ranh giới này làm hệ thống dễ hiểu, dễ kiểm thử và tránh chi phí lặp ngoài ý muốn.

## Tài liệu chính thức

- Spring Framework — [Dependency Injection và circular dependencies](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
- Spring Framework — [Proxying Mechanisms, self-invocation](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- Spring Framework — [Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html)
- Spring Framework — [Advice và vòng đời @AspectJ](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html)
- Spring Framework — [Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/enable.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-performance-maintenance-007">Giữ aspect tập trung vào một trách nhiệm có thể mô tả rõ</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ aspect tập trung vào một trách nhiệm có thể mô tả rõ trong Spring AOP

## 1. Mục tiêu và bối cảnh

Trong ứng dụng Spring, một lớp **@Aspect** thường rất dễ trở thành nơi gom mọi hành vi cắt ngang: ghi log, đo thời gian, audit, kiểm tra quyền, retry, cache, thay đổi exception và chuẩn hóa response. Ban đầu điều này có vẻ tiện vì cùng áp dụng cho service methods. Sau một thời gian, chỉ sửa cách ghi metric cũng có thể làm thay đổi kết quả nghiệp vụ hoặc ảnh hưởng đến transaction; pointcut của một nhu cầu vô tình kéo theo những nhu cầu khác.

Nguyên tắc thiết kế cần áp dụng là:

> **Một Spring aspect nên có một trách nhiệm mà nhóm phát triển mô tả được bằng một câu, xác định được lúc nào nó chạy, tác động gì, và test được riêng.**

Ví dụ câu mô tả rõ:

- "**ExecutionTimingAspect đo thời lượng hoàn thành các lời gọi service được chọn và chuyển số đo sang hệ thống metrics.**"
- "**BusinessAuditAspect ghi nhận sự kiện nghiệp vụ sau khi các thao tác được audit thành công.**"
- "**ServiceExceptionObservationAspect ghi nhận exception được ném ra từ các method thuộc phạm vi đã chọn, không thay đổi exception.**"

Câu mô tả thiếu trọng tâm:

- "CommonAspect xử lý logging, validation, bảo mật, retry và mọi yêu cầu bổ sung."
- "ServiceAspect chạy trước và sau tất cả method."
- "UtilityAspect làm những việc cần thiết cho hệ thống."

**Một trách nhiệm không có nghĩa chỉ một method advice.** Một aspect audit có thể cần cả **@AfterReturning** và **@AfterThrowing** nếu hai advice cùng phục vụ một chính sách audit nhất quán. Ngược lại, một **@Around** duy nhất vẫn có thể ôm quá nhiều trách nhiệm nếu nó đo thời gian, quyết định quyền truy cập, retry, cache, ghi audit rồi sửa giá trị trả về.

Bài học tập trung vào **Spring Aspect**: cách xác định ranh giới concern, chọn annotation advice, quản lý pointcut, dependency injection, hiệu năng, bảo trì và kiểm thử trong **ApplicationContext**. Các khái niệm AOP chung không được lặp lại thành chủ đề riêng.

## 2. Nhận biết ranh giới một trách nhiệm

Một cách thực dụng là hỏi: **"Nếu yêu cầu thay đổi này xuất hiện, aspect nào cần sửa?"** Nếu câu trả lời thường xuyên là cùng một lớp dù hai thay đổi không liên quan, lớp đó có thể đang chứa nhiều trách nhiệm.

| Trách nhiệm | Câu hỏi nó giải quyết | Kết quả/side effect | Lý do thay đổi |
|---|---|---|---|
| Đo thời lượng | Method mất bao lâu? | Ghi duration vào meter/recorder | Thay tên metric, đơn vị, sampling |
| Audit nghiệp vụ | Ai/thao tác nào đã hoàn tất hoặc thất bại? | Phát sự kiện audit theo chính sách | Yêu cầu pháp lý, trường sự kiện, bảo quản dữ liệu |
| Quan sát lỗi | Lỗi loại nào phát sinh ở boundary? | Ghi exception vào telemetry | Thay chính sách log, correlation ID |
| Phân quyền | Caller có quyền thực hiện thao tác? | Allow/deny theo policy | Thay quy tắc security và identity |
| Retry | Có thử lại một thao tác thất bại không? | Có thể gọi target nhiều lần | Thay retryable exceptions, backoff, giới hạn lần |
| Cache | Có thể dùng kết quả đã lưu không? | Có thể bỏ qua target | Thay key, TTL, invalidation |
| Transaction | Commit/rollback trong phạm vi nào? | Quản lý transaction boundary | Thay propagation/isolation/rollback rules |

Hai concern có thể cùng chạy trên **OrderService.createOrder()** nhưng chưa chắc cùng một trách nhiệm: metric cần độ trễ; audit cần sự kiện đúng nghiệp vụ; retry tác động đến số lần thực thi; transaction quyết định tính toàn vẹn dữ liệu. Chúng nên có chính sách, cấu hình và test riêng.

Trong Spring thực tế, **@Transactional**, **@Cacheable**, method security, retry integration và observability framework đã có hạ tầng chuyên dụng. Đừng viết một custom aspect để thay thế tất cả khi Spring hoặc thư viện tích hợp đang sở hữu cơ chế đó. Nếu cần một custom aspect, hãy giới hạn chính xác phần trách nhiệm chưa được thành phần có sẵn xử lý.

### 2.1. Các câu hỏi xác lập contract

Trước khi viết Spring aspect, ghi rõ:

1. **Mục đích:** tác dụng duy nhất của aspect trong một câu.
2. **Phạm vi:** Spring beans/methods/annotations nào được chọn, và những nơi nào bị loại trừ.
3. **Thời điểm:** before, after returning, after throwing hay around.
4. **Dữ liệu cần:** method signature, argument nào, result, exception hoặc elapsed time.
5. **Hiệu ứng:** gọi collaborator nào, sinh event/metric nào.
6. **Ảnh hưởng hợp đồng service:** aspect có được thay return value, exception, số lần gọi target hay không.
7. **Lỗi của chính aspect:** log/metrics/audit backend lỗi thì fail request hay best-effort?
8. **Kiểm thử:** một unit test cho logic và một integration test qua Spring proxy chứng minh wiring.

Nếu rất khó viết câu số 1 mà không dùng hàng loạt chữ **"và"**, nên xem lại phạm vi trước khi bắt tay vào pointcut.

## 3. Dấu hiệu aspect đang ôm quá nhiều việc

Ví dụ dưới là **phản ví dụ minh họa**, không phải code hoàn chỉnh nên đưa vào ứng dụng:

~~~java
@Aspect
@Component
class CommonAspect {
    @Around("execution(* com.example..service..*.*(..))")
    public Object runEverything(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        checkPermission(pjp.getArgs());
        writeRequestLog(pjp.getArgs());

        try {
            Object result = retryOrReadCache(pjp);
            writeAuditEvent(result);
            return normalizeResponse(result);
        } catch (Throwable ex) {
            logException(ex);
            return fallbackForAllServices(ex);
        } finally {
            publishDuration(System.nanoTime() - start);
        }
    }

    // Những method helper ở đây chỉ biểu diễn nhiều concern bị trộn.
}
~~~

Đoạn trên có nhiều vấn đề riêng:

- **Pointcut quá rộng**: có thể áp dụng cho service không cần audit, security, retry hoặc cache.
- **Nhiều chính sách thay đổi độc lập** nằm trong cùng một lớp.
- **@Around** có thể che lỗi nghiệp vụ và **đổi return value** một cách không mong muốn.
- **Retry** có thể gọi target nhiều lần, khiến audit/metric tính một lần hay nhiều lần khó thống nhất.
- **Logging argument/result** có thể tốn chi phí và lộ dữ liệu nhạy cảm nếu không có chính sách rõ.
- **Exception của metrics/audit backend** có thể thay exception ban đầu tùy vị trí gọi.
- **Test một concern phải khởi tạo nhiều collaborator** không liên quan.
- **Thứ tự advice/transaction** trở nên khó đánh giá; chỉ sửa một rule có thể làm thay đổi behavior của rule khác.

Đây không phải vấn đề số dòng code. Kể cả rút thành một method gọi năm helper, ranh giới trách nhiệm vẫn bị trộn nếu aspect tiếp tục điều phối cả năm chính sách.

### 3.1. Tách đúng nghĩa không chỉ là đổi tên lớp

Nếu tách **CommonAspect** thành **LoggingAspect**, **TimingAspect** và **AuditAspect**, nhưng cả ba vẫn cùng gọi một **SharedPolicyManager** điều khiển auth/retry/cache/exception translation, coupling có thể vẫn cao. Mỗi aspect nên chỉ biết **giao diện collaborator cần cho nhiệm vụ của nó**, có contract và test riêng.

Một aspect audit phụ thuộc **AuditPublisher** là hợp lý. Một aspect đo thời lượng phụ thuộc **MeterRegistry** hoặc **DurationRecorder** là hợp lý. Nếu cả hai cùng phụ thuộc một giant **CommonManager** với quá nhiều thao tác, nên tiếp tục xem xét ranh giới collaborator.

## 4. Chọn loại advice tối thiểu đáp ứng yêu cầu

Spring Framework khuyến nghị chọn **loại advice ít quyền can thiệp nhất** đủ cho chức năng. Điều này giảm số đường đi lỗi và giúp trách nhiệm rõ hơn.

| Nhiệm vụ | Advice thường phù hợp | Lý do |
|---|---|---|
| Ghi một dấu mốc trước method | **@Before** | Không cần tự gọi **proceed()** |
| Ghi event sau khi method thành công | **@AfterReturning** | Có thể lấy result, không cần điều khiển target |
| Quan sát exception thoát ra | **@AfterThrowing** | Phù hợp với failure path |
| Dọn resource/quan sát sau khi kết thúc | **@After** | Chạy theo finally semantics |
| Đo elapsed time bao gồm cả failure | **@Around** | Cần đo trước và sau toàn bộ invocation |
| Chính sách chủ động short-circuit/retry | **@Around** nếu thực sự cần | Đòi quyền quyết định cách và số lần gọi target |

Ví dụ aspect chỉ cần audit khi method trả về thành công:

~~~java
@AfterReturning(
    pointcut = "execution(* com.example.orders.OrderService.create(..))",
    returning = "result"
)
public void recordCreated(Object result) {
    auditPublisher.publishCreated(result);
}
~~~

Đây là **mảnh cấu hình minh họa**, yêu cầu **auditPublisher** và các lớp ứng dụng được khai báo ở dự án thật. Nếu chỉ cần kết quả thành công, dùng **@AfterReturning** thường rõ hơn viết **@Around** chỉ để gọi **proceed()** rồi publish.

Ngược lại, đo thời gian của cả success/failure thường có dạng:

~~~java
@Around("execution(* com.example.orders.OrderService.create(..))")
public Object time(ProceedingJoinPoint pjp) throws Throwable {
    long start = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        durationRecorder.record(System.nanoTime() - start);
    }
}
~~~

Đoạn trên cũng chỉ là **mảnh minh họa**. Với recorder có thể ném lỗi, cần thiết kế chính sách **fail-open/fail-closed** rõ; riêng metric best-effort thường không được làm hỏng outcome nghiệp vụ. Không đặt **return** vào **finally**, vì có thể che mất result/exception.

**Một trách nhiệm không cấm nhiều advice cùng loại.** Ví dụ AuditAspect có thể ghi audit cho "created" và "cancelled" bằng hai **@AfterReturning** nếu cả hai cùng tuân một schema/chính sách audit. Câu hỏi là chúng có cùng **lý do thay đổi** và **hợp đồng dữ liệu** hay không.

## 5. Pointcut phải thể hiện đúng ranh giới trách nhiệm

Aspect có nội dung gọn nhưng pointcut quá rộng vẫn tạo ra coupling với toàn bộ module Spring. Ví dụ:

~~~java
@Around("execution(* com.example..*.*(..))")
~~~

Nó có thể match quá nhiều bean method. Điều này làm tăng overhead interception và rủi ro chạy trên code không có contract phù hợp.

Nên mô tả rõ scope bằng một trong các cách:

- **Signature/package:** **execution(* com.example.orders.OrderService.create(..))** hoặc **within(com.example.orders.service..*)** khi thực sự muốn một nhóm service cụ thể.
- **Annotation chính sách:** **@annotation(com.example.observability.Measured)** nếu cần opt-in theo method. Annotation phải có runtime retention và target phù hợp.
- **Spring bean name:** **bean(orderService)** chỉ khi danh tính bean mới là điều kiện nghiệp vụ; designator này thuộc Spring AOP, không phải mọi môi trường AspectJ weaving.
- **Named pointcut:** khai báo **@Pointcut** để tách cú pháp chọn method khỏi logic advice, nhất là biểu thức có nhiều điều kiện.

Ví dụ:

~~~java
@Aspect
@Component
class TimingAspect {
    @Pointcut("@annotation(com.example.observability.Measured)")
    void measuredOperation() {}

    @Around("measuredOperation()")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        return timer.record(joinPoint); // timer là collaborator minh họa.
    }
}
~~~

Đây là **mảnh minh họa cấu trúc**. Để chạy thực tế cần khai báo annotation **Measured**, **timer** và môi trường Spring phù hợp.

Một điểm cần tránh: đưa **security**, **audit**, **metrics** vào cùng một annotation kiểu **@CrossCuttingEnabled** rồi mọi aspect cùng phụ thuộc nó, nếu thực chất mỗi concern có chính sách áp dụng khác nhau. Annotation chung chỉ nên dùng khi cả nhóm hành vi thực sự tạo thành một capability thống nhất đã có contract rõ.

### 5.1. Pointcut và unit of change

Nếu audit chỉ áp dụng cho hành động tạo/cập nhật đơn hàng, việc bổ sung một service tìm kiếm không nên tự động khiến hệ thống ghi audit. Nếu metric áp dụng cho tất cả public service methods, thay đổi phạm vi metric cũng không nên làm audit thay đổi. Tách pointcut theo mục đích giúp hai nhóm phát triển thay đổi độc lập.

Khi tên package hoặc bean được refactor, một integration test **ca dương tính** và **ca âm tính** cho mỗi pointcut sẽ giúp phát hiện rule không còn match hoặc match quá rộng.

## 6. Ví dụ hoàn chỉnh: tách audit và timing thành hai Spring aspects

Ví dụ dùng **Java 17+**, **Spring Framework 6.2.0**, **AspectJ Weaver 1.9.22.1**, **JUnit Jupiter 5.11.3** và Maven. Mục tiêu là minh họa **hai trách nhiệm độc lập**, nhưng cùng chạy trên một Spring service method:

- **AuditAspect** chỉ ghi event khi thao tác thành công, dùng **@AfterReturning**.
- **TimingAspect** chỉ ghi elapsed time của invocation, dùng **@Around** và bảo toàn result/exception.
- **WorkflowService** giữ toàn bộ nghiệp vụ, không phụ thuộc vào aspect.
- **EventLog** là fake recorder in-memory để quan sát test; trong dự án thật audit và metrics thường phải dùng các collaborator riêng, ví dụ **AuditPublisher** và **MeterRegistry**.

Cấu trúc:

~~~text
spring-focused-aspects/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── EventLog.java
    │   ├── WorkflowService.java
    │   ├── AuditAspect.java
    │   ├── TimingAspect.java
    │   └── AopConfig.java
    └── test/java/demo/
        └── FocusedAspectsTest.java
~~~

### 6.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>spring-focused-aspects</artifactId>
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
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.0</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-test</artifactId>
            <version>6.2.0</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.22.1</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.3</version>
            <scope>test</scope>
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
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**spring-context** cung cấp ApplicationContext, **spring-aop** tạo AOP proxy, **aspectjweaver** hỗ trợ phân tích annotation/pointcut AspectJ mà Spring AOP dùng; dependency này **không tự bật bytecode weaving**. **spring-test** tích hợp Spring TestContext với JUnit 5.

### 6.2. EventLog làm collaborator kiểm thử

**src/main/java/demo/EventLog.java**

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

public class EventLog {
    private final List<String> events = new ArrayList<>();

    public void add(String value) {
        events.add(value);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }
}
~~~

Trong ví dụ, EventLog chỉ dùng để assert được **effect**. Nó không thay thế hệ thống audit hoặc metrics thực tế, và không được thiết kế cho ghi đồng thời từ nhiều thread. Trong production, dùng collaborator đúng công việc và cơ chế thread-safe tương ứng.

### 6.3. Service không phụ thuộc aspect

**src/main/java/demo/WorkflowService.java**

~~~java
package demo;

public class WorkflowService {
    private final EventLog log;

    public WorkflowService(EventLog log) {
        this.log = log;
    }

    public String create(String id) {
        log.add("target:" + id);

        if ("bad".equals(id)) {
            throw new IllegalStateException("creation rejected");
        }

        return "created:" + id;
    }
}
~~~

Tạo thành công trả **created:<id>**, thất bại ném **IllegalStateException**. Service không biết **@Aspect** nào sẽ quan sát mình; target method không bị buộc phải gọi API Spring AOP.

### 6.4. AuditAspect chỉ quan tâm thành công nghiệp vụ

**src/main/java/demo/AuditAspect.java**

~~~java
package demo;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class AuditAspect {
    private final EventLog log;

    public AuditAspect(EventLog log) {
        this.log = log;
    }

    @AfterReturning(
        pointcut = "execution(* demo.WorkflowService.create(..))",
        returning = "result"
    )
    public void afterCreated(Object result) {
        log.add("audit:" + result);
    }
}
~~~

Aspect này có **một nhiệm vụ**: ghi nhận kết quả tạo thành công. Vì dùng **@AfterReturning**, nó không chạy trên exception path; cũng không dùng **ProceedingJoinPoint** để điều khiển target hay tự thay đổi result.

Nếu yêu cầu audit thất bại xuất hiện, nhóm có thể cân nhắc thêm **@AfterThrowing** vào **AuditAspect** nếu cả success và failure event cùng thuộc **một chính sách audit**. Không cần nhập timer hoặc retry vào lớp chỉ vì chúng cùng chạy trên WorkflowService.

### 6.5. TimingAspect chỉ quan tâm elapsed time

**src/main/java/demo/TimingAspect.java**

~~~java
package demo;

import java.util.function.LongSupplier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TimingAspect {
    private final EventLog log;
    private final LongSupplier ticker;

    public TimingAspect(EventLog log, LongSupplier ticker) {
        this.log = log;
        this.ticker = ticker;
    }

    @Around("execution(* demo.WorkflowService.create(..))")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        long started = ticker.getAsLong();

        try {
            return pjp.proceed();
        } finally {
            long elapsed = ticker.getAsLong() - started;
            log.add("duration:" + elapsed);
        }
    }
}
~~~

Ở đây **LongSupplier** được inject để test có clock xác định. Trong production, thường truyền **System::nanoTime** hoặc dùng API meter/timer chuyên dụng. Một elapsed time kiểu nanoTime chỉ dùng để tính chênh lệch thời gian; không phải timestamp lịch.

**TimingAspect** không catch để nuốt exception và không đổi kết quả. **finally** giúp ghi metric cho cả success và failure. Vì **EventLog.add** ở fixture không ném lỗi, nó không che outcome của target; còn với exporter ngoài đời, cần quyết định best-effort hay fail-closed và test theo quyết định đó.

### 6.6. Spring AOP configuration

**src/main/java/demo/AopConfig.java**

~~~java
package demo;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopConfig {
    @Bean
    public EventLog eventLog() {
        return new EventLog();
    }

    @Bean
    public LongSupplier ticker() {
        AtomicLong value = new AtomicLong();
        return () -> value.getAndAdd(10L);
    }

    @Bean
    public WorkflowService workflowService(EventLog log) {
        return new WorkflowService(log);
    }

    @Bean
    public AuditAspect auditAspect(EventLog log) {
        return new AuditAspect(log);
    }

    @Bean
    public TimingAspect timingAspect(EventLog log, LongSupplier ticker) {
        return new TimingAspect(log, ticker);
    }
}
~~~

**@EnableAspectJAutoProxy** bật proxy của Spring. **proxyTargetClass=true** giúp Spring trả class-based proxy có thể inject theo **WorkflowService.class** trong ví dụ. Nó không phải AspectJ weaving.

Hai aspect đăng ký qua **@Bean** độc lập. Nếu team cần tắt timing ở một môi trường, về mặt thiết kế nên dùng điều kiện đăng ký bean hoặc cờ cấu hình của timing concern, không đặt một nhánh **if(enableTiming)** bên trong CommonAspect quản lý luôn audit.

Clock giả cho test luôn tăng 10 đơn vị mỗi lần đọc; do đó mỗi invocation đo được **duration:10**. Đây là **fixture xác định**, không phản ánh thời gian xử lý thật của JVM.

## 7. Integration test chứng minh hai trách nhiệm độc lập

**src/test/java/demo/FocusedAspectsTest.java**

~~~java
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(AopConfig.class)
class FocusedAspectsTest {
    @Autowired
    WorkflowService workflow;

    @Autowired
    EventLog events;

    @BeforeEach
    void resetEvents() {
        events.clear();
    }

    private static List<String> sorted(List<String> values) {
        return values.stream().sorted().toList();
    }

    @Test
    void auditAspectLogicCanBeTestedWithoutSpring() {
        EventLog isolatedLog = new EventLog();
        AuditAspect aspect = new AuditAspect(isolatedLog);

        aspect.afterCreated("created:X");

        assertEquals(
            List.of("audit:created:X"),
            isolatedLog.snapshot()
        );
    }

    @Test
    void successfulCallRunsBothFocusedConcerns() {
        assertTrue(AopUtils.isAopProxy(workflow));

        String result = workflow.create("A");

        assertEquals("created:A", result);
        assertEquals(
            sorted(List.of(
                "target:A",
                "audit:created:A",
                "duration:10"
            )),
            sorted(events.snapshot())
        );
    }

    @Test
    void failureHasTimingButNoSuccessAudit() {
        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> workflow.create("bad")
        );

        assertEquals("creation rejected", failure.getMessage());
        assertEquals(
            sorted(List.of(
                "target:bad",
                "duration:10"
            )),
            sorted(events.snapshot())
        );
    }
}
~~~

**Test unit** khởi tạo AuditAspect trực tiếp và kiểm tra riêng một quy tắc audit mà không cần Spring proxy. Hai **integration tests** dùng ApplicationContext thực: bean **WorkflowService** được lấy từ Spring, được advice và giữ nguyên success/failure contract.

Khi thành công, có ba sự kiện: target chạy, audit thành công, timer ghi duration. Test **không phụ thuộc thứ tự giữa hai aspect** vì Spring AOP có thể sắp xếp advisor theo cấu hình; ở đây chưa định nghĩa một ordering contract, nên sự kiện được sort trước khi so sánh. Việc so sánh list đã sort **vẫn bắt được event thiếu hoặc trùng**.

Khi thất bại, **@AfterReturning** không ghi audit thành công, nhưng **@Around** vẫn ghi duration trong finally. Ngoại lệ gốc vẫn tới caller. Từ đó có thể xác minh mỗi aspect chỉ xử lý trách nhiệm của mình.

### 7.1. Chạy test

Từ thư mục **spring-focused-aspects**, chạy:

~~~bash
mvn test
~~~

Dự án ví dụ khai báo **3 test methods**, dự kiến summary của Maven Surefire là:

~~~text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
~~~

Đây là **kết quả dự kiến theo thiết kế và assertions**; không phải stdout đã được sao chép từ một lần Maven chạy thực tế. Hệ thống build có thể in log bổ sung.

Có thể chạy riêng class:

~~~bash
mvn -Dtest=FocusedAspectsTest test
~~~

### 7.2. Vì sao ví dụ này chứng minh tính dễ bảo trì?

- Thay schema audit ảnh hưởng **AuditAspect** và test audit.
- Thay cách đo duration ảnh hưởng **TimingAspect**, **ticker/meter collaborator** và test timing.
- Logic **WorkflowService.create(...)** không cần gọi thẳng aspect.
- Có thể đổi phạm vi của mỗi pointcut riêng nếu nhu cầu thay đổi.
- Test hợp đồng nghiệp vụ xác minh cả **return value/exception** trong khi kiểm tra advice effect.
- Nếu cần phân quyền, nên đưa policy vào hạ tầng security thích hợp; tránh thêm một nhánh auth vào **TimingAspect**.

Đồng thời không nên hiểu ví dụ là mọi dự án đều phải xây một custom **TimingAspect**: khi hệ thống đang có Micrometer Observation/Tracing hoặc metrics instrumentation phù hợp, tái sử dụng hạ tầng có sẵn thường giảm chi phí bảo trì.

## 8. Các ranh giới với Spring transaction, security, retry và cache

Các concern này có thể cùng xuất hiện trên một service, nhưng thường **không cùng một trách nhiệm**.

### 8.1. Transaction

**@Transactional** có interceptor và quy tắc **propagation**, **rollback**, **isolation** riêng. Custom aspect chỉ ghi audit không nên âm thầm **catch** exception rồi trả thành công, vì nó có thể thay đổi điều kiện transaction interceptor quan sát, tùy thứ tự advisor.

Nếu event audit phải gắn với **transaction commit thành công**, chỉ đặt **@AfterReturning** quanh method không tự chứng minh transaction đã commit; transaction có thể commit ở boundary khác. Cần thiết kế đúng theo transactional events, outbox hoặc after-commit callback phù hợp. Đây là một yêu cầu audit contract riêng, nên diễn đạt rõ thay vì để aspect tự đoán.

### 8.2. Security

Đừng tự thêm một đoạn kiểm tra quyền trong LoggingAspect để "tiện dùng chung". Spring Security có method security và hệ thống authorization riêng. Nếu application có một cross-cutting security rule đặc biệt, cần làm rõ owner, policy, fail behavior và test riêng; tránh coupling nó với metrics.

### 8.3. Retry

Retry thường có quyền gọi target **nhiều lần**. Nếu timer bọc retry, duration có thể tính tổng của mọi attempt. Nếu timer nằm bên trong retry, mỗi attempt có thể có duration riêng. Cả hai đều hợp lệ nhưng mang ý nghĩa metric khác nhau. Audit cũng phải phân biệt "một thao tác nghiệp vụ" với "một lần thử".

Không hard-code giả định về **@Order** nếu chưa xác nhận advisor chain thực tế. Khi thứ tự là contract, khai báo ordering một cách có chủ đích và thêm integration test đúng ngữ nghĩa.

### 8.4. Cache

Cache có thể trả kết quả mà không gọi target. Audit "phương thức đã được người dùng yêu cầu" và audit "nghiệp vụ đã thực sự hoàn tất" là hai sự kiện khác nhau. Nếu cache short-circuit lời gọi, một aspect audit chạy ngoài cache advisor có thể ghi khác với aspect chạy bên trong. Hãy đặt ranh giới audit dựa trên nghĩa của sự kiện, không chỉ vì hai annotation nằm trên cùng method.

## 9. Giữ hiệu năng tốt khi aspect có phạm vi rõ

Aspect tập trung vào một nhiệm vụ dễ đo và tối ưu hơn, nhưng **không có nghĩa mặc nhiên miễn phí**. Mỗi lời gọi qua Spring AOP proxy có thể đi qua advisor chain và thực hiện code advice. Các chi phí thường đáng quan tâm:

- **Pointcut quá rộng:** advice chạy trên lượng lớn method không cần thiết.
- **Serialization trong advice:** chuyển arguments/result thành JSON để log trên mọi invocation, gây allocation và có thể chạm dữ liệu nhạy cảm.
- **Đọc stack trace đầy đủ:** tốn chi phí và tạo log lớn khi xảy ra rất thường xuyên.
- **Gọi network/DB đồng bộ:** đưa latency của audit/metric exporter vào request path.
- **Ghi log trùng nhiều tầng:** một request bị ghi cùng nội dung ở controller, service và repository.
- **Cardinality metric cao:** dùng ID người dùng, order ID hoặc raw URL làm label/tag có thể làm hệ thống metrics quá tải.
- **Gọi proceed() nhiều lần:** biến hoạt động quan sát thành retry không chủ đích và tạo side effect trùng.

Các cách kiểm soát trong Spring:

1. **Thu hẹp pointcut** đến class, method hoặc annotation opt-in thực sự cần đo.
2. Dùng **advice type phù hợp** để giảm logic điều khiển không cần thiết.
3. Chuẩn hóa **metric tag có cardinality thấp**, tránh các giá trị định danh gần như vô hạn.
4. Với telemetry không bắt buộc, cân nhắc **async/buffer/best-effort** đúng yêu cầu sản phẩm; nêu rõ policy khi backend lỗi.
5. Khi có overhead đáng kể, đo bằng **benchmark và production metrics** thay vì đưa ra số microsecond cố định không có bằng chứng.
6. Nếu logging/auditing đòi payload, áp dụng schema, redaction, sampling và size limit thích hợp tại collaborator của concern đó.

Ví dụ ghi thời lượng bằng **System.nanoTime()** là thích hợp cho chênh lệch thời gian ngắn, nhưng không đồng nghĩa số đo chính xác tuyệt đối của công việc chỉ do target thực hiện: phạm vi đo còn tùy vị trí advice trong chuỗi advisor và các thao tác liên quan.

## 10. Tránh ảnh hưởng hợp đồng nghiệp vụ từ aspect chỉ quan sát

Với **logging, metrics và tracing** mang tính quan sát, một contract an toàn phổ biến là:

- **Giữ nguyên số lần target được gọi:** không bỏ qua và không gọi lặp **proceed()**.
- **Giữ nguyên return value:** caller nhận value mà chain bên trong trả về.
- **Giữ nguyên exception:** caller vẫn thấy exception, hoặc translation chỉ diễn ra khi có yêu cầu rõ.
- **Không sửa input arguments** nếu concern không được giao trách nhiệm biến đổi tham số.
- **Không yêu cầu target phụ thuộc Spring AOP API** như **AopContext.currentProxy()**.
- **Không làm mất lỗi gốc** vì log/metric collector bị lỗi.

Mẫu thường thấy:

~~~java
@Around("execution(* com.example.orders.OrderService.create(..))")
public Object observe(ProceedingJoinPoint pjp) throws Throwable {
    long started = System.nanoTime();

    try {
        return pjp.proceed();
    } finally {
        long nanos = System.nanoTime() - started;

        try {
            metrics.record(nanos);
        } catch (RuntimeException telemetryFailure) {
            fallbackLogger.warn(
                "Metrics export failed",
                telemetryFailure
            );
        }
    }
}
~~~

Đây là **mảnh code minh họa**, cần có collaborator **metrics** và **fallbackLogger** trong ứng dụng thật. Nội dung catch thể hiện giả định **metric best-effort** cho lỗi runtime thông thường. Nếu fallback logger cũng có thể fail, hoặc các trường hợp **Error** xảy ra, vẫn cần policy vận hành phù hợp; không tuyên bố đoạn minh họa bảo đảm tuyệt đối không làm thay đổi outcome trong mọi hoàn cảnh.

Đừng đặt **return** trong **finally**, vì nó có thể che exception hoặc ghi đè kết quả. Nếu chính việc xuất audit là yêu cầu bắt buộc của một giao dịch, cần contract fail behavior rõ thay vì tự áp chính sách best-effort của metric cho audit.

## 11. Spring proxy, self-invocation và thiết kế ranh giới

Spring AOP mặc định hoạt động qua JDK dynamic proxy hoặc CGLIB proxy. Caller phải đi qua proxy để method advice được chặn theo cơ chế này.

~~~java
public class OrderService {
    public void outer() {
        inner(); // tương đương this.inner()
    }

    public void inner() {
        // ...
    }
}
~~~

Nếu pointcut chỉ chọn **inner()**, lời gọi **this.inner()** từ chính target thường **không đi qua proxy**, nên advice của Spring AOP không chạy cho lần gọi nội bộ đó.

Điều này quan trọng đối với thiết kế tập trung trách nhiệm: **đừng "chữa" self-invocation bằng cách nhét logic audit hoặc timing trực tiếp vào OrderService** nếu ranh giới AOP đang có vấn đề. Cần xem lại điểm gọi qua Spring bean, chia service phù hợp hoặc cân nhắc weaving khi yêu cầu thực sự cần lời gọi nội bộ. Một class-based/CGLIB proxy cũng không tự xử lý self-invocation.

Một số giới hạn khác:

- JDK proxy bộc lộ những interface được proxy; client không thể tự giả định proxy là concrete target class.
- CGLIB không thể override **final/private method**.
- Aspect được đánh dấu **@Aspect** cần được đưa vào Spring container để auto-proxy phát hiện, bằng **@Component** và scan hoặc **@Bean**.
- **@EnableAspectJAutoProxy** không phải AspectJ bytecode weaving. Tách trách nhiệm về runtime từ Spring Aspect khỏi nội dung học AspectJ general.

## 12. Khi nào nhiều advice trong một aspect vẫn phù hợp?

Không nên áp dụng máy móc "một aspect = một advice". Ví dụ một **RequestCorrelationAspect** có thể:

- Lấy correlation ID trước lời gọi.
- Đưa ID vào đúng context khi lời gọi đang chạy.
- Dọn context sau khi method hoàn thành, kể cả khi exception.

Nếu ba bước cùng phục vụ **một lifecycle correlation**, một **@Around** có try/finally hoặc một nhóm advice được thiết kế rõ vẫn thuộc một trách nhiệm.

Một **AuditAspect** có thể có các advice cho sự kiện **created**, **updated**, **cancelled** nếu tất cả cùng tạo các event theo một schema và chính sách audit. Nhưng nếu audit cho payment phải tuân **after-commit**, trong khi audit cho read request chạy best-effort trước khi trả response, các hợp đồng quá khác nhau có thể cần ranh giới riêng.

Quy tắc đánh giá là **cohesion theo nguyên nhân thay đổi và contract**, không theo số annotation hoặc số method.

### 12.1. Cách đặt tên hữu ích

Tên tốt gắn với một trách nhiệm quan sát được: **ServiceLatencyAspect**, **OrderAuditAspect**, **ExceptionTraceAspect**.

Tên quá chung: **CommonAspect**, **AllServicesAspect**, **GlobalHandlerAspect**, **HelperAspect**. Tên chung không tự chứng minh lỗi thiết kế, nhưng là tín hiệu nên đọc lại pointcut, dependencies và contract.

Không đặt tên **SecurityAspect** cho một lớp chỉ ghi log rằng "người dùng đã được kiểm tra", vì tên như vậy có thể khiến team hiểu lầm aspect thực sự thực thi authorization policy.

## 13. Từng bước refactor một CommonAspect đang tồn tại

Nếu ứng dụng đã có CommonAspect rất lớn, nên thay đổi theo từng concern có test bảo vệ hành vi.

### Bước 1: Lập bản đồ hành vi hiện hữu

Ghi lại tất cả advice methods, pointcuts, đối tượng phụ thuộc, return/throw behavior và side effects. Xác định từng advisor có thể chạy cùng method nào. Đặc biệt lưu ý **@Around** có short-circuit hoặc retry.

### Bước 2: Xác lập contract bên ngoài

Với mỗi service method quan trọng, viết integration test lấy bean từ **ApplicationContext**, gọi qua proxy và assert **giá trị trả về, exception, số lần side effect**. Nếu thứ tự advisor có ý nghĩa, ghi rõ assertion về thứ tự ấy.

### Bước 3: Tách một concern có ranh giới rõ

Ví dụ di chuyển logic đo thời lượng ra **TimingAspect**, giữ pointcut và giá trị metric nhất quán. Cấu hình bean/conditional riêng; xóa phần timer đã chuyển khỏi CommonAspect để tránh thực thi trùng. Các chỉnh sửa phải giữ nguyên loại proxy/advisor ordering cần thiết.

### Bước 4: Tạo test chuyên biệt

Unit test collaborator/logic metric; integration test qua Spring proxy để xác nhận đúng method match và failure path vẫn ghi duration. Thêm một ca không nên match nếu rule được giới hạn theo annotation/package.

### Bước 5: Tách tiếp audit, error observation và policy khác

Mỗi lần di chuyển một trách nhiệm, chạy lại test contract. Nếu có logging/audit được phát hai lần, kiểm tra advisor chain và việc bean cũ vẫn còn được đăng ký.

### Bước 6: Đánh giá lại framework feature có sẵn

Xem concern nào nên giao lại cho **Spring transaction, cache, security, retry hoặc observability**. Nếu chuyển cơ chế, kiểm tra lại ordering, rollback, cache hit/miss và tương thích cấu hình Spring Boot. Không mặc định rằng custom AOP và framework feature có cùng ngữ nghĩa runtime.

### Bước 7: Loại bỏ phần lỗi thời

Khi concern đã có owner mới và test bảo vệ kết quả, xóa đường code cũ cùng pointcut không còn dùng. Đừng giữ hai cơ chế song song trừ khi rõ ràng có hai contract khác nhau.

## 14. Bảng phản biện các quyết định thiết kế

| Tình huống | Quyết định thường hợp lý | Cần chứng minh bằng test |
|---|---|---|
| Muốn log mỗi lời gọi service | Một **LoggingAspect** theo chính sách log cụ thể, pointcut vừa đủ | Method nào được log/không log, dữ liệu nhạy cảm |
| Muốn đo elapsed time | **TimingAspect** riêng hoặc framework observability | Duration trên success/failure, overhead |
| Muốn audit sau khi nghiệp vụ thành công | **AuditAspect**, chọn boundary đúng nghĩa thành công | Event đúng thời điểm, không audit nhầm failed/rolled back |
| Muốn cache và retry cùng lúc | Sử dụng thành phần phù hợp của framework với ordering rõ | Cache hit có gọi target không, retry mấy lần |
| Muốn thêm permission check vào timing aspect | **Không nên**; phân quyền có contract riêng | Allow/deny, exception và identity |
| Muốn nhiều @AfterReturning trong một AuditAspect | Có thể phù hợp nếu cùng schema/chính sách | Mỗi business event và scope được chọn |
| Muốn thay kết quả trong @Around để logging tiện hơn | Cần loại bỏ việc thay kết quả nếu chỉ logging | Exact return value/exception ở caller |
| Muốn bỏ self-invocation | Chỉnh ranh giới bean/đường gọi hoặc chọn cơ chế đúng | Lời gọi nội bộ và lời gọi qua proxy |
| Muốn mở rộng pointcut cho toàn module | Chỉ làm nếu tất cả method thuộc contract, tính được chi phí | Positive/negative matching, volume ảnh hưởng |

## 15. Checklist code review cho một Spring Aspect

- [ ] Có thể mô tả trách nhiệm của aspect bằng **một câu rõ ràng**.
- [ ] Dependencies chỉ phục vụ trách nhiệm đó; không có global manager quá nhiều chức năng.
- [ ] Loại advice là loại tối thiểu cần để thực hiện công việc.
- [ ] Pointcut chỉ chọn Spring beans/methods thực sự nằm trong contract.
- [ ] Có ca test dương tính và âm tính cho phạm vi pointcut quan trọng.
- [ ] Aspect không tự ý sửa input, return value, exception hoặc số lần gọi target.
- [ ] Nếu dùng **@Around**, **proceed()**, try/finally và failure behavior được kiểm tra.
- [ ] Audit, metrics, retry, cache, security và transaction có owner/chính sách riêng.
- [ ] Không ghi payload nhạy cảm hoặc metric tag cardinality quá cao một cách mặc định.
- [ ] Có unit test cho logic riêng, integration test qua Spring ApplicationContext/proxy.
- [ ] Test không phụ thuộc tên class proxy sinh ra hoặc ordering chưa khai báo.
- [ ] Có chiến lược với lỗi của collaborator (best-effort/fail-closed) theo từng concern.
- [ ] Chỉ dùng custom aspect khi hạ tầng Spring/thư viện hiện có chưa đáp ứng hợp đồng.

## 16. Tự kiểm tra hiểu bài

**1. Một AuditAspect có hai @AfterReturning cho tạo và hủy đơn hàng có nhất thiết vi phạm một trách nhiệm?**

Không. Nếu cả hai tuân cùng chính sách audit, schema và lý do thay đổi, chúng có thể thuộc một trách nhiệm.

**2. Một @Around duy nhất vừa kiểm tra quyền, retry, log, đo thời gian và cache có phải "một trách nhiệm" vì chỉ có một advice method?**

Không. Số method không quyết định cohesion; các chính sách kia có hợp đồng và lý do thay đổi khác nhau.

**3. Vì sao không dùng @Around cho một event đơn giản sau thành công nếu @AfterReturning đã đủ?**

Vì @Around buộc code trực tiếp điều khiển **proceed()**, return và exception, làm tăng rủi ro bỏ target hoặc đổi outcome. @AfterReturning đơn giản và khớp mục đích hơn.

**4. Tách TimingAspect khỏi AuditAspect có bảo đảm hai concern không tương tác không?**

Chưa hoàn toàn. Chúng vẫn có thể cùng áp dụng lên một method và tương tác qua advisor ordering, transaction/retry/cache hoặc collaborator dùng chung. Cần integration test và contract rõ.

**5. Gắn timer vào mọi method trong toàn bộ ứng dụng có tự động giúp quan sát tốt hơn không?**

Không. Nó có thể tạo nhiều overhead, log/metric vô ích và high-cardinality tags. Cần xác định nhu cầu quan sát và pointcut phù hợp.

**6. Unit test trực tiếp AuditAspect pass có chứng minh audit được gọi trên Spring bean không?**

Không. Phải có integration test qua ApplicationContext/proxy để xác nhận aspect bean, auto-proxy và pointcut thực tế.

**7. @AfterReturning chạy có luôn có nghĩa transaction đã commit thành công không?**

Không. Nó phản ánh method execution kết thúc bình thường theo vị trí advice trong chain. Boundary commit có thể nằm ngoài hoặc sau nó. Muốn audit theo commit phải dùng cơ chế transactional phù hợp.

## 17. Tài liệu tham khảo chính thức

- Spring Framework — Declaring Advice: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — AOP Concepts: https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html
- Spring Framework — Declaring a Pointcut: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Spring Framework — Advice Ordering: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/ordering.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — AOP Test Utilities: https://docs.spring.io/spring-framework/reference/testing/unit.html
- Spring Framework — Transaction-bound Events: https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html
- Spring Boot — Aspect-Oriented Programming: https://docs.spring.io/spring-boot/reference/features/aop.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-performance-maintenance-008">API công khai và API nội bộ khi nâng phiên bản Spring</a>

<details>
<summary>Xem chi tiết</summary>

# API công khai và API nội bộ khi nâng phiên bản Spring AOP

## 1. Vấn đề bảo trì: "public" trong Java chưa chắc là hợp đồng ứng dụng nên dùng

Khi nâng Spring Framework hoặc Spring Boot, nhiều lỗi AOP không đến từ `@Before` hay `@Around` mà đến từ **phụ thuộc vào implementation detail**: tên lớp auto-proxy creator, cấu trúc subclass proxy, danh sách advisor nội bộ, tên bean hạ tầng, hành vi mặc định của Spring Boot, cách suy luận kiểu proxy hoặc API đã bị deprecated.

Cần tách ít nhất ba nghĩa:

1. **Java `public`**: một class, constructor hay method có thể được mã ứng dụng gọi theo luật Java và module/classpath. Đây là *khả năng truy cập ngôn ngữ*, chưa bảo đảm Spring cam kết giữ ổn định chi tiết đó.
2. **Spring API/SPI được tài liệu công khai hóa**: hợp đồng được Javadoc và Spring Reference mô tả, thường dùng để cấu hình, mở rộng hoặc chẩn đoán một cách có chủ đích. Ví dụ `@Aspect`, `@EnableAspectJAutoProxy`, `Advisor`, `Pointcut`, `MethodInterceptor`, `ProxyFactory`.
3. **Framework internals/implementation detail**: mã hoặc convention cần để Spring tự tạo proxy và tự quản lý advisor; dù đôi lúc khai báo `public`, ứng dụng không nên ghép chặt vào đó trừ khi đang viết infrastructure extension và đã chấp nhận chi phí tương thích.

**Nguyên tắc:** ưu tiên cấu hình declarative của Spring; dùng SPI chính thức khi cần mở rộng; tránh truy cập internals chỉ vì IDE cho phép import hoặc reflection tìm được tên class.

**Phạm vi:** Spring AOP / Spring Aspect và các API liên quan đến proxy, advisor, pointcut, auto-proxy, test khi nâng Spring. Không phân tích toàn bộ thay đổi của Spring MVC/Data/Security, ngoại trừ những nâng cấp platform ảnh hưởng tương thích dự án AOP.

## 2. Bản đồ API và điểm cần dè chừng

| Thành phần | Phân loại sử dụng | Ý nghĩa khi nâng version |
| --- | --- | --- |
| `org.aspectj.lang.annotation.Aspect`, `Before`, `Around`, `AfterReturning`, `AfterThrowing` | Annotation API của AspectJ được Spring AOP hỗ trợ | Hợp đồng aspect phổ biến; kiểm tra tương thích phiên bản `aspectjweaver` |
| `org.springframework.context.annotation.EnableAspectJAutoProxy` | Spring configuration API | Dùng cấu hình hỗ trợ `@AspectJ`; không phụ thuộc cách Spring tạo bean nội bộ |
| `org.springframework.aop.Pointcut`, `Advisor`, `MethodMatcher`, `ClassFilter` | Spring AOP SPI | Phù hợp khi viết infrastructure/điểm mở rộng riêng; đọc Javadoc khi override method |
| `org.aopalliance.intercept.MethodInterceptor`, `MethodInvocation` | AOP Alliance interception SPI dùng trong Spring | Có thể dùng cho lời gọi quanh method; bảo toàn semantics `proceed()` |
| `org.springframework.aop.framework.ProxyFactory` | Spring AOP API được tài liệu chính thức hướng dẫn | Tạo proxy bằng Java khi cần; không gắn vào class implementation của proxy đã tạo |
| `org.springframework.aop.support.DefaultPointcutAdvisor`, `NameMatchMethodPointcut`, `AopUtils` | Spring AOP support API | Hữu ích cho advisor/pointcut/test; xác minh signature và semantics đúng version |
| `org.springframework.aop.framework.Advised` | Spring AOP proxy configuration/inspection SPI | Có thể introspect advisors khi cần, nhưng không biến danh sách/thứ tự internal advisors thành business contract |
| `org.springframework.aop.framework.AopContext` | API được công khai nhưng dùng có điều kiện | `currentProxy()` cần expose proxy; không phải lựa chọn bảo trì mặc định |
| Auto-proxy creator implementations, ví dụ `AnnotationAwareAspectJAutoProxyCreator` | Infrastructure/extension implementation | Không tự khởi tạo hoặc ép kiểu trong business code; để Spring quản lý |
| Auto-proxy helper constants, cơ chế bean name nội bộ | Implementation detail | Tránh hard-code tên bean creator, convention và metadata trừ khi viết framework integration đã kiểm thử |
| Class proxy sinh ra (`...$$SpringCGLIB$$...`, `jdk.proxy...`) | Runtime artifact | Không so sánh tên class, không serialize/ghi business rules dựa vào tên |
| `org.springframework.cglib.*` hoặc cấu trúc bytecode proxy | Repackaged implementation technology | Không gắn ứng dụng vào implementation class; dùng Spring AOP API/SPI |

**Đừng phân loại chỉ dựa trên package:** có những class trong `org.springframework.aop.framework` được Spring **chính thức tài liệu hóa** như `ProxyFactory`, cũng có những class phục vụ hạ tầng nội bộ. `public` không đủ, nhưng package tên `framework` cũng không đồng nghĩa cấm sử dụng. Phải đọc Javadoc, Reference và release notes đúng version, xem API có được mô tả là dành cho người dùng/extension hay không.

Một API được public và documented **vẫn có thể thay đổi trong bản major mới**; điều này khác với lời hứa bất biến vĩnh viễn. Bám public contract làm giảm rủi ro và giúp migration có hướng dẫn rõ, không miễn kiểm thử.

## 3. Spring AOP API tầng cao: điểm bám nên ưu tiên

### 3.1. Annotation style trên Spring beans

~~~java
@Aspect
public class AuditAspect {
    @Before("execution(* com.example.payment..*Service.*(..))")
    public void beforePayment() {
        // Audit concern.
    }
}
~~~

Đây là **fragment**, cần imports AspectJ, aspect phải được đăng ký Spring bean và AOP auto-proxy phải được kích hoạt. So với tự tạo `AnnotationAwareAspectJAutoProxyCreator` trong business config, `@Aspect` cùng `@EnableAspectJAutoProxy` giúp giao quyết định proxy/adapter cho Spring.

Nếu dự án dùng Spring Boot, AOP auto-configuration có thể bật support tự động theo dependencies/properties; **không suy rằng thiếu `@EnableAspectJAutoProxy` trong source là chưa bật AOP**. Luôn xác nhận context thật.

### 3.2. Cấu hình bằng public annotation

~~~java
@Configuration
@EnableAspectJAutoProxy
class AopConfiguration {
    @Bean
    AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
~~~

Fragment trên chỉ minh họa cách đăng ký; cần import `Configuration`, `EnableAspectJAutoProxy`, `Bean` từ Spring. Không cần gọi constructor `AnnotationAwareAspectJAutoProxyCreator`, tự đăng ký các bean names đặc biệt hoặc chỉnh trực tiếp bean definition của proxy creator.

### 3.3. Public SPI khi cần kiểm soát cụ thể

Khi thật sự cần tạo một proxy ngoài IoC hoặc cấu hình advisor bằng Java, Spring Reference hướng dẫn sử dụng `ProxyFactory`:

~~~java
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;

// Fragment, giả định serviceTarget và TaskService đã được định nghĩa.
ProxyFactory factory = new ProxyFactory(serviceTarget);
factory.addAdvice((MethodInterceptor) invocation -> {
    // Gắn hành vi có trách nhiệm ở đây.
    return invocation.proceed();
});
TaskService proxy = (TaskService) factory.getProxy();
~~~

Đây là Spring AOP **public API** cho việc tạo proxy programmatic. Đối với ứng dụng sử dụng Spring container, auto-proxy theo bean/advisor thường đơn giản và dễ bảo trì hơn. Khi dùng `ProxyFactory`, vẫn không được phụ thuộc vào class name cụ thể mà `getProxy()` sinh ra.

## 4. Những dấu hiệu đang phụ thuộc API nội bộ

### 4.1. Tự new auto-proxy creator trong business code

~~~java
// Ý tưởng anti-pattern; không dùng làm wiring application.
var creator =
    new org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator();
~~~

Đây là **fragment minh họa điều không nên làm**; việc tạo object không đủ cho lifecycle `BeanFactory`, registration, BeanPostProcessor, advisor discovery, ordering. Spring có thể thay đổi implementation/chi tiết initialization qua các phiên bản.

**Thay bằng:** cấu hình `@EnableAspectJAutoProxy` trong context thích hợp hoặc cơ chế auto-configuration do Spring Boot hỗ trợ. Nếu đang viết một thư viện hạ tầng cần auto-proxy creator, phải coi đó là một extension được quản lý, có integration tests theo version và Javadoc chuyên biệt.

### 4.2. So sánh class name của proxy

~~~java
// Sai: không có hợp đồng ổn định cho tên generated proxy.
if (service.getClass().getName().contains("$$SpringCGLIB$$")) {
    // business behavior...
}
~~~

JDK proxy, CGLIB naming, proxy interfaces và metadata có thể đổi do config hoặc upgrade. Dùng `AopUtils.isAopProxy(service)` khi mục tiêu là chẩn đoán **Spring AOP proxy**; khi cần biết cơ chế, dùng `isJdkDynamicProxy` hoặc `isCglibProxy`. Tuy nhiên, business logic không nên phân nhánh theo kiểu proxy nếu có cách thiết kế bằng interface/collaborator rõ ràng.

### 4.3. Hard-code tên bean hạ tầng và số lượng advisor

Không nên biến internal bean name như `org.springframework.aop.config.internalAutoProxyCreator` thành điều kiện chạy nghiệp vụ hoặc assertion dễ vỡ. Tên có thể tồn tại lâu nhưng thuộc infrastructure registration.

Tương tự, assert cứng `((Advised) proxy).getAdvisors().length == 2` dễ vỡ khi Spring có thêm transaction, caching, async, metrics advisor hoặc thay cơ chế auto-proxy. Kiểm tra **hành vi của advice** và **thứ tự có hợp đồng công khai**, không dựa vào số lượng nội bộ.

### 4.4. Dùng reflection can thiệp private fields hay Spring-generated bytecode

~~~java
// Anti-pattern mô tả: tìm private field qua reflection trên proxy
// rồi thay TargetSource/advisor để ứng dụng tiếp tục chạy.
~~~

Làm vậy khiến việc nâng major/minor version rủi ro hơn hẳn. Nếu cần thay target/advisors ở runtime, hãy xem public SPI như `Advised`, `TargetSource` trong Javadoc, xác nhận vòng đời `getTarget()/releaseTarget()` và giới hạn tính thread-safe; không tự sửa private field/constructor nội bộ.

### 4.5. Import trực tiếp CGLIB internals

Spring repackages CGLIB trong `spring-core`. Các class ở namespace implementation có thể tồn tại nhưng không nên là điểm bám cho business code. Khi cần Spring AOP proxy, để Spring chọn cách tạo proxy; khi cần tạo proxy thủ công, dùng `ProxyFactory`. Không để type generated proxy rò rỉ sang interface hoặc serialization contract.

## 5. Nâng Spring 6.2 → 7.0: điểm có ảnh hưởng đến Spring Aspect

**Spring Framework 7.0 đã phát hành vào tháng 11/2025** và vẫn giữ **Java 17 baseline**, đồng thời nâng các nền tảng khác (ví dụ Jakarta EE 11); khi nâng dự án cần kiểm tra cùng lúc Spring Boot, thư viện AspectJ, Java runtime và những modules framework đang dùng. Nội dung dưới đây dựa trên **Spring Framework 7.0 release notes**; trước khi triển khai hãy đọc bản release notes tương ứng với *version đích cụ thể*, không áp dụng máy móc mọi thay đổi lên 6.x.

### 5.1. Proxy type configuration thống nhất hơn trong Spring 7

Trong Spring Framework 7.0, cơ chế thiết lập **default proxy type** được áp dụng nhất quán hơn với các proxy processors, bao gồm nhiều đường proxy liên quan đến `@Async`. Spring Boot có thể mặc định dùng class-based proxy trong một số cấu hình, còn Spring Framework core thường ưu tiên interface proxy khi điều kiện cho phép.

Hệ quả bảo trì:

- Một ứng dụng trước đây dựa ngầm vào JDK proxy hoặc CGLIB có thể gặp thay đổi class/interfaces sau migration hoặc đổi property.
- Nếu target là `final class`, ép CGLIB proxy sẽ thất bại vì không subclass được; dùng interface proxy hoặc đổi thiết kế.
- Nếu method là `final`, CGLIB không thể override để áp advice.
- JDK proxy chỉ expose interface phù hợp, không được cast proxy về concrete target class rồi coi đó là contract.
- Test quan trọng phải kiểm tra **method/advice behavior**; chỉ assert `isJdkDynamicProxy` khi product thực sự cam kết proxy type.

**API mới ở Spring 7.0:** `@Proxyable` cho phép lựa chọn proxy type hoặc tập interface ở mức từng bean khi cần, ví dụ định hướng:

~~~java
// Fragment minh họa Spring Framework 7+, không dùng trên Spring 6.x.
@Bean
@Proxyable(INTERFACES)
TaskService taskService() {
    return new DefaultTaskService();
}
~~~

Cần imports và kiểm chứng signature/enum chính xác theo Spring Framework 7 Javadoc của dự án. Đừng copy `@Proxyable` vào code Spring 6.2 rồi kỳ vọng compile. Nếu không có nhu cầu rõ, ưu tiên contract qua interface và cấu hình proxy mặc định phù hợp thay vì tùy biến từng bean.

### 5.2. JSpecify và null-safety

Spring 7 chuyển hệ thống khai báo nullness sang **JSpecify**; các annotation cũ `org.springframework.lang.Nullable`, `NonNull`, `NonNullApi`, `NonNullFields` trở thành deprecated theo release notes. Hệ quả chủ yếu đối với code Java/Kotlin **trực tiếp mở rộng Spring API/SPI**:

- Signature override/interceptor helper có thể xuất hiện cảnh báo nullness mới từ IDE/static analysis.
- Kotlin có thể nhìn thấy nullability khác trước, dẫn tới compilation warnings/errors tại API boundary.
- Những library AOP extension nên cập nhật nullness contract theo Javadoc target version, tránh tự đoán `@Nullable` cũ luôn có semantics giống JSpecify.
- Một số hành vi kiểm tra parameter/nullness ngoài AOP cũng đổi; không suy rằng mọi method AOP đều đổi signature.

Không đặt mục tiêu tự động thay mọi `@Nullable` bằng một import chỉ nhờ search-and-replace. Đánh giá compiler settings, type-use nullness, mảng/generics và hợp đồng override.

### 5.3. Java và Jakarta baseline là rủi ro toàn ứng dụng, không chỉ AOP

Nâng Spring 7 có thể liên quan Servlet, JPA, Bean Validation và dependency alignment. Nếu một aspect match annotation/kiểu API từ thư viện đã đổi namespace hoặc ABI, pointcut có thể không match như trước. Nhưng **đừng gán lỗi đó cho Spring AOP internal API khi chưa có bằng chứng**.

Ví dụ với pointcut dùng `@annotation(...)` hoặc `execution` trên class bên ngoài, cần test đúng type/annotation ở artifact sau nâng phiên bản. Với `@Transactional`, kiểm tra transaction manager và proxy config của app, nhưng tách riêng lỗi điểm match Spring AOP khỏi lỗi transaction semantics.

### 5.4. Những APIs đã bị xóa/deprecated

Không có quy tắc “mọi method public của Spring 6 tồn tại trong Spring 7”. Xem mục **Removed APIs** và **Deprecated APIs** của release notes, `@Deprecated(forRemoval=true)` khi có, Javadoc hai phiên bản và IDE compiler warnings. Đi theo thứ tự:

1. Inventory những `org.springframework.*`/`org.aspectj.*` imports mà code thực sự sử dụng.
2. Phân loại thành documented API/SPI, deprecated, internal coupling.
3. Đối chiếu notes từ source version đến target major, và javadocs của **chính symbol**.
4. Cập nhật API usage trước khi nâng toàn hệ nếu có replacement tương đương.
5. Giữ regression test đủ để phát hiện pointcut/advice behavior đổi.

Một symbol không có `@Deprecated` **không có nghĩa** behavior/semantics của nó được cam kết y nguyên; nhất là các class auto-proxy implementation được `public` vì framework cần gọi qua nhiều packages.

## 6. Ví dụ Java 17+ chạy được với public Spring API

Mục tiêu: tạo một **Spring context thực**, đăng ký aspect bằng `@Bean`, bật AOP qua `@EnableAspectJAutoProxy`, và assert **behavior** thay vì tên class proxy. Ví dụ không đụng auto-proxy creator internals và có thể làm smoke test khi nâng từ Spring 6.2 lên 7.0 sau khi đổi versions/dependencies phù hợp.

Cấu trúc:

~~~text
spring-aop-api-upgrade/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/
    ├── TaskService.java
    ├── DefaultTaskService.java
    ├── Events.java
    ├── TraceAspect.java
    ├── AppConfig.java
    └── Demo.java
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-aop-api-upgrade'
~~~

`build.gradle` — phiên bản khởi đầu Spring Framework 6.2:

~~~groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation platform('org.springframework:spring-framework-bom:6.2.19')
    implementation 'org.springframework:spring-context'
    implementation 'org.springframework:spring-aop'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'
}

application {
    mainClass = 'demo.Demo'
}
~~~

Chạy bằng **JDK 17+** và Gradle phiên bản hỗ trợ JDK đó. BOM giữ các module Spring thống nhất trên một framework release. Khi chuyển sang Spring 7, thay đồng bộ BOM/starter cùng phần phụ thuộc bổ trợ theo ma trận compatibility; không đổi lẻ riêng `spring-aop` hoặc `spring-core`.

`src/main/java/demo/TaskService.java`:

~~~java
package demo;

public interface TaskService {
    String runJob(String id);
}
~~~

`src/main/java/demo/DefaultTaskService.java`:

~~~java
package demo;

public final class DefaultTaskService implements TaskService {
    @Override
    public String runJob(String id) {
        if ("fail".equals(id)) {
            throw new IllegalArgumentException("invalid");
        }
        return "OK:" + id;
    }
}
~~~

`src/main/java/demo/Events.java`:

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

public final class Events {
    private final List<String> values = new ArrayList<>();

    public void add(String value) {
        values.add(value);
    }

    public List<String> snapshot() {
        return List.copyOf(values);
    }
}
~~~

`src/main/java/demo/TraceAspect.java`:

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TraceAspect {
    private final Events events;

    public TraceAspect(Events events) {
        this.events = events;
    }

    @Around("execution(* demo.TaskService.runJob(..))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        events.add("enter");
        try {
            Object result = pjp.proceed();
            events.add("return");
            return result;
        } catch (Throwable ex) {
            events.add("throw");
            throw ex;
        }
    }
}
~~~

`src/main/java/demo/AppConfig.java`:

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {

    @Bean
    public TaskService taskService() {
        return new DefaultTaskService();
    }

    @Bean
    public Events events() {
        return new Events();
    }

    @Bean
    public TraceAspect traceAspect(Events events) {
        return new TraceAspect(events);
    }
}
~~~

`src/main/java/demo/Demo.java`:

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Demo {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            TaskService service = context.getBean(TaskService.class);
            Events events = context.getBean(Events.class);

            System.out.println("proxy: " + AopUtils.isAopProxy(service));

            System.out.println("first: " + service.runJob("A"));
            System.out.println("events: " + events.snapshot());

            // Negative control: raw instance không đi qua proxy Spring.
            TaskService raw = new DefaultTaskService();
            System.out.println("raw: " + raw.runJob("B"));
            System.out.println("events after raw: " + events.snapshot());

            try {
                service.runJob("fail");
            } catch (IllegalArgumentException ex) {
                System.out.println("caught: " + ex.getMessage());
            }
            System.out.println("events final: " + events.snapshot());
        }
    }
}
~~~

Chạy trong thư mục dự án:

~~~bash
gradle run
~~~

Hoặc dùng wrapper nếu có: `./gradlew run` trên Unix/macOS và `gradlew.bat run` trên Windows. **Stdout dự kiến của ứng dụng** (không tính Gradle và log hạ tầng):

~~~text
proxy: true
first: OK:A
events: [enter, return]
raw: OK:B
events after raw: [enter, return]
caught: invalid
events final: [enter, return, enter, throw]
~~~

### Tại sao ví dụ bền hơn cách dùng internals?

- Config chỉ dùng Spring `@Bean`, `@EnableAspectJAutoProxy` và AspectJ `@Aspect`/`@Around`: đây là bề mặt API được tài liệu hướng dẫn.
- Target `DefaultTaskService` là `final` nhưng triển khai `TaskService`; khi Spring dùng **JDK interface proxy**, nó không cần kế thừa target.
- `Demo` chỉ thao tác với interface `TaskService`; không ép proxy về `DefaultTaskService` hoặc generated proxy class.
- `AopUtils.isAopProxy` là hỗ trợ chẩn đoán có tài liệu. Bằng chứng thật là `events` có `enter, return`/`enter, throw` theo invocation.
- Raw object làm phép đối chứng: có cùng business result nhưng không chạy Spring advice.
- Không có tên auto-proxy creator nội bộ, reflection vào private fields hoặc giả định danh sách advisor chính xác.
- Khi nâng Spring/AspectJ, chính hợp đồng ở stdout giúp phát hiện regression.

**Giới hạn:** `Events` là mutable state đơn giản dành cho ví dụ chạy tuần tự; nếu test concurrent, dùng collection/thread-safety phù hợp. Output trên là kết quả **dự kiến theo mã**, không phải log từ một lần biên dịch đã chạy trong tài liệu này.

## 7. Bài kiểm tra migration bổ sung: API trước và sau nâng phiên bản

Nên dùng **hai hệ dependency độc lập** (source version và target version) để chạy *cùng bộ contract tests*; đừng trộn các module Spring thuộc nhiều minor/major version trong một classpath.

Test cần xác nhận:

| Contract | Assertion ổn định nên viết | Assertion dễ vỡ nên tránh |
| --- | --- | --- |
| Bean được advice thật | `AopUtils.isAopProxy(bean)` và lời gọi advice có effect | So sánh `bean.getClass().getName()` với generated name |
| Pointcut match | Gọi method được match, kiểm tra probe/counter | Bắt Spring lưu advisor ở index cụ thể |
| Normal return | Return value đúng; `@AfterReturning`/`@Around` ghi outcome | Chỉ assert phương thức business trả đúng |
| Exception path | Caller nhận exception hợp đồng, advice xử lý lỗi đúng | Catch exception wrapper nội bộ bất kể context |
| Self-invocation | Test behavior theo Spring proxy semantics | Mặc định self-call luôn được advice |
| JDK/CGLIB switch | Business interface vẫn gọi được; methods mong đợi vẫn được advice | Ép proxy sang concrete class hoặc cố định tên subclass |
| Advisor ordering | Có `@Order`/configuration rõ và assert event đúng | Dựa vào thứ tự scan/bean definition tình cờ |
| Multiple contexts | Đúng aspect bean/proxy ở context chứa target | Giả định cấu hình auto-proxy là toàn JVM |
| @Async | Future/callback/worker behavior đã được test | Dựa vào thứ tự advisor implementation mặc định |
| Transaction/caching | Business semantics đúng theo integration test | Count internals của advisors |
| Null-safety | Static analysis/Kotlin compilation đúng | Chỉ search old annotation names |
| Deprecation | Build lint và release notes được review | Tin rằng `public` đồng nghĩa không thể bị loại bỏ |

### Smoke test nên chạy trước khi nâng

Giữ một sample/suite test như mục 6. Chạy trên version cũ để baseline. Nâng dependencies đồng bộ, compile, chạy lại và so **kết quả hành vi** (không so tên class/stack traces byte-for-byte). Khi fail, khoanh vùng: API compilation, bean registration, proxy selection, pointcut match, invocation chain, exception handling.

### Kiểm thử dùng Spring TestContext

Với dự án có test framework, có thể dùng `@SpringJUnitConfig(AppConfig.class)` + inject `TaskService`, `Events` và `assertTrue(AopUtils.isAopProxy(service))`, rồi assert `events.snapshot()`. Đây là **fragment định hướng**, cần dependency `spring-test` và JUnit tương thích; không thay cho chương trình chạy độc lập ở mục 6.

Khi nâng Spring 7, JUnit version/BOM của dự án cũng cần căn chỉnh; không lấy test dependency từ một mã mẫu cũ rồi gán lỗi compile đó cho AOP framework.

## 8. Biết API/SPI nào an toàn hơn bằng cách nào?

Một cách review repository đáng tin:

1. **Xem thư viện chính thức:** API xuất hiện trong Spring Reference hay chỉ nhìn thấy từ decompiler?
2. **Xem Javadoc:** class/method có mô tả là dành cho application, extension, debugging, hay được ghi chú internal?
3. **Kiểm tra deprecation:** `@Deprecated`, `forRemoval`, `since`, replacement được khuyến nghị, kiểm tra cả Javadocs phiên bản đích.
4. **Kiểm tra mức coupling:** chỉ import interface `Advisor` hay subclass/instantiate internal creator? Có cast về proxy implementation không?
5. **Kiểm tra extension points:** `ProxyFactory`, `MethodInterceptor`, `Pointcut`, `TargetSource` có hợp đồng được mô tả; cần tuân thủ đúng method lifecycle.
6. **Kiểm tra runtime assumption:** type proxy, bean scopes, method visibility, thread, advisor ordering, container context.
7. **Kiểm tra test cover:** test dùng behavior và cả negative control chưa?
8. **So release notes nguồn→đích:** không lấy docs “latest” rồi giả định mọi API đã tồn tại trên bản cũ.

### Tại sao phụ thuộc public SPI vẫn phải cẩn trọng?

Một thư viện tự tạo `MethodInterceptor` có thể phải bảo toàn `proceed()` đúng một lần theo yêu cầu. Một custom `TargetSource` phải trả target/lifecycle release đúng; nếu thực thi sai contract, upgrade Spring có thể làm lộ lỗi đã tồn tại. Các API này public, nhưng **SPI** đòi hỏi hiểu lifecycle nhiều hơn việc chỉ dùng `@Aspect`.

Nếu chỉ cần audit/timing/correlation trên Spring beans, ưu tiên `@Aspect` + Spring IoC config và public contracts. Đừng tự viết auto-proxy infrastructure chỉ để thay annotation.

## 9. Code smell và hướng refactor

### 9.1. Ép kiểu proxy về implementation

~~~java
// Sai nếu service là JDK proxy.
DefaultTaskService concrete =
        (DefaultTaskService) context.getBean(TaskService.class);
~~~

Cách ít coupling hơn:

~~~java
TaskService service = context.getBean(TaskService.class);
service.runJob("A");
~~~

Mã trên là fragments sử dụng types ở mục 6. Nếu cần method không thuộc interface, cân nhắc mở rộng interface chính thức hoặc thay thiết kế; không ép CGLIB chỉ để chữa một cast.

### 9.2. Tự lấy private advisors từ auto-proxy creator

Trước: reflection vào `AnnotationAwareAspectJAutoProxyCreator` hay registry và xem những field private để xác định có advice.

Sau: giữ bean qua context, dùng `AopUtils` và một probe callback/in-memory recorder của **chính aspect**. Nếu cần cấu trúc infrastructure, dùng `Advised` trên Spring proxy khi API đó phù hợp; không coi số lượng advisor là contract.

### 9.3. Kiểm tra tên generated class trong điều kiện nghiệp vụ

Trước: `className.startsWith("jdk.proxy")` hoặc `contains("$$SpringCGLIB$$")`.

Sau: business logic chỉ dựa trên `TaskService` interface và kết quả method. Chỉ diagnostic code dùng `AopUtils.isAopProxy`, `isCglibProxy`, `isJdkDynamicProxy` khi cần.

### 9.4. Gọi advice bằng method reflection hoặc tự nạp compiler output

Trước: gọi thẳng một advice method hoặc sửa field của aspect qua reflection rồi coi đó là integration test AOP.

Sau: Spring `ApplicationContext` đăng ký aspect bean, bật auto-proxy, lấy bean proxy và gọi method, sau đó assert side effect. Thử nghiệp vụ raw và proxied riêng để phân biệt.

### 9.5. Cố định auto-proxy defaults một cách ngầm định

Trước: dựa vào “trong dự án hiện nay Boot đang chọn CGLIB” mà không có tests.

Sau: document nhu cầu proxy type thật và test kỹ khi đổi phiên bản. Với Spring 7 có thể dùng cấu hình mới theo bean ở nơi cần thiết; với Spring 6.2 hãy dùng APIs/version phù hợp. Hãy xem release notes của đúng target patch để không đoán semantics.

## 10. Khi nào phải đọc lớp nội bộ?

Không phải mọi truy cập vào implementation đều sai. Một nhóm nhỏ viết Spring infrastructure/library có thể cần:

- Tùy biến advisor discovery/adaptation mà API tầng cao không đủ.
- Chẩn đoán bug của một bản Spring cụ thể có stacktrace từ `AnnotationAwareAspectJAutoProxyCreator`.
- Làm compatibility shim cho framework nhiều version.
- Xây các tích hợp bytecode/proxy cần kiểm tra behavior ở cấp thấp.

Nhưng khi đó phải **đánh dấu rõ đây là coupling có kiểm soát**:

1. Bao implementation detail trong một adapter/module nhỏ, không phát tán imports vào business code.
2. Viết integration tests chạy trên từng minor/major Spring thực sự được hỗ trợ.
3. Có version matrix, tài liệu rủi ro và chính sách bỏ hỗ trợ cũ.
4. Tránh reflection vào private fields nếu có documented extension point tương đương.
5. Theo dõi release notes, javadocs, dependency BOM và runtime generated type.
6. Có đường fallback/rollback nếu Spring đổi internals.

Cần phân biệt: “public class để framework kế thừa nội bộ” và “documented application extension API” là hai thứ khác nhau. Không khẳng định một class chỉ vì nằm trong package `autoproxy` thì luôn bị cấm; đánh giá theo từng symbol.

## 11. Checklist nâng phiên bản Spring cho một module Aspect

### Trước khi nâng

- [ ] Ghi rõ Spring Framework, Spring Boot (nếu có), AspectJ, Java và build-tool version hiện tại.
- [ ] Chạy bộ test contract cho `@Aspect`, pointcuts, proxy, exception, self-invocation.
- [ ] Thống kê các imports `org.springframework.aop.*`, `org.springframework.aop.framework.*`, `org.aspectj.*`.
- [ ] Liệt kê nơi gọi `AopContext`, `ProxyFactory`, `Advised`, `TargetSource`, `BeanPostProcessor` và auto-proxy creator implementations.
- [ ] Tìm reflection vào private fields, class name matching, internal bean name constants.
- [ ] Xác định nơi dùng JDK proxy/CGLIB và class/method `final`.
- [ ] Loại bỏ test phụ thuộc generated class name, advisor order tình cờ hoặc count cố định.
- [ ] Đọc release notes/Javadoc *từ version nguồn sang version đích*, không chỉ latest documentation.

### Khi nâng

- [ ] Căn chỉnh toàn bộ Spring modules qua BOM hoặc Spring Boot dependency management.
- [ ] Căn chỉnh AspectJ/Java build plugin theo ma trận tương thích.
- [ ] Review deprecated/removed APIs, thay bằng documented replacement khi có.
- [ ] Với Spring 7, xem thay đổi proxy defaults và các lựa chọn `@Proxyable` khi thực sự cần.
- [ ] Với Spring 7, review JSpecify/null-safety, đặc biệt nếu viết Kotlin/custom SPI.
- [ ] Compile với warnings có kiểm soát, không bỏ qua tất cả deprecations.
- [ ] Chạy integration tests, cả success/failure, async và transaction nếu module dùng chúng.

### Sau khi nâng

- [ ] Xác nhận aspect bean được đăng ký trong đúng context; auto-proxy creator hoạt động.
- [ ] Xác nhận proxy type phù hợp business contract.
- [ ] Test pointcut match đúng bean/method mà không quét quá rộng.
- [ ] Không xuất hiện advice double-run hoặc mất advice.
- [ ] Thứ tự advice (nếu nghiệp vụ phụ thuộc) có cấu hình và test rõ.
- [ ] Kiểm tra logs/performance/latency, nhất là khi chuyển JDK/CGLIB hoặc có thêm advisors.
- [ ] Không phụ thuộc tên generated proxy ở runtime.
- [ ] Rollback được về BOM cũ nếu phát hiện regression.

## 12. Kết luận

**API công khai** giúp code Spring AOP dễ nâng cấp hơn khi được dùng đúng hợp đồng đã tài liệu hóa: `@Aspect`/`@EnableAspectJAutoProxy` cho đa số ứng dụng, `Advisor`/`Pointcut`/`MethodInterceptor`/`ProxyFactory` khi cần một extension có lý do rõ. **API nội bộ** dù có Java `public` vẫn có thể là implementation detail và dễ đổi giữa version; không nên trở thành dependency của nghiệp vụ.

Đặc biệt từ Spring 6.2 lên 7.0, cần review **proxy-type semantics, JSpecify/nullness, platform baseline** và release notes. Trước khi đi sâu internals, hãy ưu tiên **contract tests chạy qua Spring proxy** để bảo đảm advice vẫn đúng về hiệu năng và hành vi.

## Tài liệu tham khảo

- [Spring Framework 7.0 — Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)
- [Spring Framework — Aspect-Oriented Programming](https://docs.spring.io/spring-framework/reference/core/aop.html)
- [Spring Framework — Enabling @AspectJ support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — Spring AOP APIs](https://docs.spring.io/spring-framework/reference/core/aop-api.html)
- [Spring Framework — Programmatic ProxyFactory](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html)
- [Spring Framework 7 — Null-safety](https://docs.spring.io/spring-framework/reference/core/null-safety.html)
- [Spring Framework — AopUtils Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/support/AopUtils.html)

Một API đang `public` là dữ kiện để đánh giá chứ không phải giấy bảo hành. Bám đúng documented API/SPI, giữ phiên bản đồng bộ và test hành vi proxy/advice mới là chiến lược bảo trì dài hạn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-performance-maintenance-009">AOT, native image và nhu cầu khai báo proxy theo phiên bản, môi trường</a>

<details>
<summary>Xem chi tiết</summary>

# AOT, native image và nhu cầu khai báo proxy theo phiên bản, môi trường trong Spring AOP

## 1. Vì sao Spring AOP cần kiểm tra riêng khi dùng AOT/native image?

Trong ứng dụng Spring thông thường chạy trên JVM, `@Aspect` được Spring đọc để tạo advisor, rồi Spring AOP tạo **JDK dynamic proxy** hoặc **class-based proxy (CGLIB)** cho bean phù hợp. Việc tạo proxy có thể dựa vào thông tin chỉ biết khi context khởi tạo.

Với **Spring Ahead-of-Time (AOT) processing**, Spring phân tích bean definitions ở **giai đoạn build** và sinh mã khởi tạo, bytecode proxy cùng thông tin `RuntimeHints` để giảm nhu cầu cấu hình động khi chạy. Khi biên dịch thành **GraalVM Native Image**, runtime có giới hạn về tạo class động, reflection, classpath và các thành phần có thể được truy cập; những proxy/hints cần thiết phải được phát hiện hoặc đăng ký ở thời điểm build.

Điều này liên quan trực tiếp đến Spring AOP: một `@Aspect` có thể hoạt động trên JVM nhưng thiếu proxy/hint khi đóng thành native executable, hoặc chỉ hoạt động với một profile môi trường mà build-time analysis đã nhìn thấy.

**Không đồng nhất ba cơ chế:**

| Khái niệm | Cơ chế | Ý nghĩa với Spring AOP |
| --- | --- | --- |
| JVM thông thường | Spring tạo proxy khi khởi tạo bean trong application context | Cấu hình bean/advisor có thể linh hoạt hơn theo runtime |
| Spring AOT chạy trên JVM | Sinh trước phần khởi tạo bean/proxy; JVM chạy ứng dụng dùng các tài sản AOT tạo ra | Phát hiện sớm sai khác bean graph/hints mà không cần native compiler |
| GraalVM Native Image | Biên dịch đóng phần ứng dụng được phân tích vào executable; AOT Spring là bước bắt buộc của chuỗi này | Phải có proxy classes và metadata phù hợp với những đường chạy thực tế |

**Điểm quan trọng:** `@Aspect` chỉ là cách mô tả advice. Spring AOP **vẫn hoạt động bằng proxy** ở cả ba môi trường. AOT/native **không tự sửa** các giới hạn self-invocation, method `private`/`final`, hoặc điểm cắt không khớp.

Ví dụ: một `@Before` chỉ đánh dấu lên `PaymentService.transfer()`, nhưng lời gọi `this.transfer()` nội bộ vẫn không đi qua proxy. Nếu JVM và native đều thiếu advice với self-invocation, đó là giới hạn Spring AOP, **không phải** lỗi thiếu GraalVM proxy hint.

## 2. Proxy nào cần được sinh hoặc khai báo?

### 2.1. JDK dynamic proxy

Nếu target service có interface nghiệp vụ và Spring cấu hình dùng interface proxy, proxy cần implement các interface thích hợp. Trong môi trường native, **JDK proxy hints** cho biết tập interface mà dynamic proxy có thể implement ở runtime.

Với Spring AOP, tập interface thực thường có thêm các Spring infrastructure interfaces, ví dụ `SpringProxy`, `Advised` và `DecoratingProxy` trong cấu hình thông thường. Vì vậy, đừng mặc định `registerJdkProxy(PaymentPort.class)` sẽ luôn tương đương với proxy Spring AOP hoàn chỉnh.

API Spring cung cấp `AopProxyUtils.completeJdkProxyInterfaces(...)` để hỗ trợ hoàn thiện tập interface thường dùng khi đăng ký hint JDK proxy. Nhưng **ưu tiên để Spring AOT tự sinh hint** từ một bean/advisor xác định được lúc build; manual hint chỉ dùng sau khi xác minh có khoảng trống cụ thể.

### 2.2. Class-based proxy (CGLIB)

Khi target không có business interface hoặc bị yêu cầu dùng class proxy, Spring tạo subclass proxy. Với Native Image, **class proxy thường được Spring AOT sinh bytecode từ giai đoạn build**. Thêm JDK proxy hint cho một interface không biến class-based proxy thành JDK proxy và không tự tạo subclass cần thiết.

Các giới hạn vốn có vẫn áp dụng:

- `final` class không subclass được; `final` method không override được.
- `private` method không intercept được qua override của proxy.
- Java module/package visibility có thể gây vấn đề khi tạo/định nghĩa subclass.
- Nếu target bị chọn sai kiểu proxy do cấu hình, thêm hints thường không giải quyết đúng nguyên nhân.

Trong Spring Boot, `spring.aop.proxy-target-class` là một trong những cấu hình ảnh hưởng lựa chọn proxy. Nếu đang dùng Spring Framework thuần, `@EnableAspectJAutoProxy(proxyTargetClass = ...)` hoặc XML `<aop:aspectj-autoproxy proxy-target-class="..."/>` có thể điều khiển tương ứng. Nhiều hạ tầng proxy/transaction có thể hợp nhất tùy chọn trong cùng context: hãy **kiểm tra proxy thật**.

### 2.3. Phân biệt hints và AOP advice

| Thành phần | Nó giải quyết | Nó không giải quyết |
| --- | --- | --- |
| `RuntimeHints.proxies()` | Thông báo JDK proxy interface set cần cho native runtime | Không tạo pointcut/advisor; không thay class proxy |
| `RuntimeHints.reflection()` | Cho phép reflection trên những type/member cần thiết | Không làm private method trở thành AOP join point |
| `RuntimeHints.resources()` | Bao gồm resource cần nạp qua classpath/runtime | Không tự đăng ký `@Aspect` thành Spring bean |
| Class proxy bytecode do AOT tạo | Cho phép một số class-based proxies tồn tại trong native executable | Không cho phép subclass `final` class |
| `@Aspect` + hạ tầng Spring AOP | Khai báo và kết nối advice vào Spring-managed proxy | Không đảm bảo native metadata đủ nếu logic dynamic ngoài tầm AOT |

Tập proxy thực, signature, target type và advisor chain cần được đo trong **ứng dụng đang chạy**, không chỉ đọc source code.

## 3. Ranh giới build-time và runtime của Spring AOT

Spring AOT thường được cấu hình từ tập **bean definitions biết ở build time**. Vì thế cần tránh các quyết định cấu trúc container chỉ xuất hiện sau khi native image đã build.

Ví dụ:

~~~text
Build với profile "dev":
  -> AOT phân tích bean definitions của "dev"
  -> tạo cấu hình khởi tạo/proxy/hints phù hợp tập bean đó
  -> native executable được tạo

Chạy native executable với môi trường "prod":
  -> không thể mặc định kỳ vọng mọi bean/advisor chỉ tồn tại trong "prod"
     sẽ tự xuất hiện dù build chưa biết chúng.
~~~

Với Spring AOT, một số lựa chọn như `@Profile` hoặc property quyết định **bean có được đăng ký** cần được xác định nhất quán ở build time. Thay đổi giá trị runtime dùng **bên trong một bean đã biết** là khác với thay đổi cả tập bean/advisor. Đừng coi mọi property đều bị đóng băng, nhưng **đừng dựa vào runtime flag để bổ sung các bean/proxy mới sau build**.

Những trường hợp dễ phát sinh chênh lệch:

1. Aspect chỉ được `@ComponentScan` trong một profile mà pipeline native không bật.
2. Bean được đăng ký động bằng `BeanDefinitionRegistry`, reflection hoặc plugin discovery sau khi AOT phân tích context.
3. `@ConditionalOnProperty` đổi việc tạo bean, vì property dùng để build native không giống runtime.
4. Một interface bổ sung chỉ được attach vào JDK proxy trong nhánh code đặc biệt chưa được Spring AOT nhìn thấy.
5. Spring AOP auto-configuration hoạt động khác giữa JVM test và native pipeline do profile, classpath hoặc dependency khác nhau.
6. Một test trên JVM dùng Spring AOP proxy; production lại bật AspectJ LTW agent. Đó là **đường thực thi khác** và cần kế hoạch hỗ trợ native riêng; không suy ra từ test AOT proxy.

Bất kỳ thay đổi version Spring/GraalVM nào cũng phải được kiểm tra với đúng dependency matrix; không thể lấy kết luận của một phiên bản áp cho mọi phiên bản khác.

## 4. Ví dụ chạy được: Spring Boot + @Aspect và kiểm tra proxy

Ví dụ sử dụng **Spring Boot 3.3.13 / Spring Framework 6.1.x, Java 21, Gradle 8.x** làm một baseline cụ thể. Để tạo native binary bằng GraalVM, cần JDK/GraalVM với `native-image` tương thích và công cụ native phù hợp hệ điều hành. Các version bên dưới là **một bộ minh họa thống nhất**, không có nghĩa là mỗi phiên bản mới đều giữ nguyên plugin/API.

Ứng dụng có `CheckoutPort`, target `CheckoutService` và advice `@Before`. Chúng ta chọn JDK proxy bằng property để dễ quan sát tính tương đương trên JVM và native.

### 4.1. Cấu trúc

~~~text
spring-aop-native-example/
├── settings.gradle
├── build.gradle
└── src/main/
    ├── java/com/example/springaot/
    │   ├── AopNativeApplication.java
    │   ├── CheckoutPort.java
    │   ├── CheckoutService.java
    │   └── AuditAspect.java
    └── resources/application.properties
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-aop-native-example'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.13'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'org.graalvm.buildtools.native' version '0.10.6'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

`src/main/resources/application.properties`:

~~~properties
spring.aop.proxy-target-class=false
spring.main.web-application-type=none
~~~

`CheckoutPort.java`:

~~~java
package com.example.springaot;

public interface CheckoutPort {
    String place(String id);
    String status();
}
~~~

`CheckoutService.java`:

~~~java
package com.example.springaot;

import org.springframework.stereotype.Service;

@Service
public class CheckoutService implements CheckoutPort {
    @Override
    public String place(String id) {
        return "stored:" + id;
    }

    @Override
    public String status() {
        return "ready";
    }
}
~~~

`AuditAspect.java`:

~~~java
package com.example.springaot;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    private final List<String> events = new CopyOnWriteArrayList<>();

    @Before(
        value = "execution(* com.example.springaot.CheckoutPort.place(..))"
                + " && args(id)",
        argNames = "id"
    )
    public void beforePlace(String id) {
        events.add("audit:" + id);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }
}
~~~

`AopNativeApplication.java`:

~~~java
package com.example.springaot;

import org.springframework.aop.support.AopUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AopNativeApplication {
    public static void main(String[] args) {
        SpringApplication.run(AopNativeApplication.class, args);
    }

    @Bean
    CommandLineRunner demonstrate(CheckoutPort checkout, AuditAspect audit) {
        return args -> {
            System.out.println("IS_AOP_PROXY=" + AopUtils.isAopProxy(checkout));
            System.out.println("IS_JDK_PROXY=" + AopUtils.isJdkDynamicProxy(checkout));
            System.out.println("PLACE=" + checkout.place("A42"));
            System.out.println("STATUS=" + checkout.status());
            System.out.println("EVENTS=" + audit.snapshot());
        };
    }
}
~~~

Ở đây `spring-boot-starter-aop` cung cấp hạ tầng Spring AOP; `AuditAspect` được component scan để trở thành Spring bean. `@Aspect` chỉ khai báo advice và không tự tạo bean nếu không có `@Component` hoặc `@Bean`. Vì `CheckoutService` có interface, property cấu hình JDK proxy và pointcut khớp `place`, trường hợp bình thường sẽ có một event. `status` không nằm trong pointcut.

### 4.2. Chạy JVM để xác nhận baseline

Từ dự án đã có Gradle wrapper phù hợp (nếu chưa có thì dùng Gradle cài sẵn để tạo wrapper hoặc thay lệnh):

~~~bash
./gradlew bootRun
~~~

Trên Windows PowerShell, dùng `.\gradlew.bat bootRun`. Bỏ qua log khởi động Spring, **những dòng do chính ứng dụng in** cần có nội dung:

~~~text
IS_AOP_PROXY=true
IS_JDK_PROXY=true
PLACE=stored:A42
STATUS=ready
EVENTS=[audit:A42]
~~~

Đây là **kỳ vọng cần kiểm tra khi chạy**, không phải log đã được thực thi trong tài liệu. Nếu `IS_AOP_PROXY=false` hoặc `EVENTS=[]`, hãy sửa pointcut/registration/context trước, chưa cần thêm native hints.

### 4.3. Phân tích AOT assets

Với Spring Boot Gradle plugin và Native Build Tools được áp dụng như trên:

~~~bash
./gradlew clean processAot
~~~

Kiểm tra các đầu ra **có thể được tạo trong layout Gradle của baseline**:

~~~text
build/generated/aotSources/
build/generated/aotResources/
build/generated/aotClasses/
~~~

Tùy version plugin và task, một số path hoặc nội dung có thể khác. Kiểm tra generated Java bean registration, runtime hints, proxy bytecode và các warning của task `processAot`. Không kết luận chỉ từ việc thấy một file tên `proxy-config.json`: cần quan sát bean AOP trong executable chạy thực tế.

Một phần hints cho GraalVM nằm trong `META-INF/native-image/...` dưới resources khi AOT tạo ra. Nếu không nhìn thấy proxy-hint mong đợi, có thể AOT đã có cơ chế tạo proxy tương ứng theo cách khác; phải so sánh với generated AOT classes và hành vi ứng dụng.

### 4.4. Biên dịch và chạy Native Image

Cài GraalVM Native Image và native toolchain tương thích hệ điều hành, rồi chạy:

~~~bash
./gradlew nativeCompile
~~~

Native Build Tools sẽ kích hoạt các bước Spring AOT liên quan. Executable được tạo trong `build/native/nativeCompile/`, với tên tương ứng tên project và quy ước OS. Ví dụ trên Linux/macOS:

~~~bash
./build/native/nativeCompile/spring-aop-native-example
~~~

Trên Windows có thể là `build\native\nativeCompile\spring-aop-native-example.exe`.

Kiểm tra lại **đúng năm dòng hành vi** ở mục 4.2. Nếu JVM và native cùng in `IS_AOP_PROXY=true`, `IS_JDK_PROXY=true`, trả về đúng và một event `audit:A42`, ta có bằng chứng tối thiểu rằng aspect chạy trong cả hai môi trường. Đối với production, còn cần test exception, transaction, số lần advice, các bean/profile khác và concurrency theo use case.

`nativeCompile` cần nhiều bộ nhớ/thời gian; ứng dụng còn có thể build native container qua `bootBuildImage` với builder phù hợp khi môi trường Docker được chuẩn bị. Không coi thành công của `bootRun` là chứng minh `nativeCompile` sẽ pass.

## 5. Có phải luôn cần tự thêm proxy hints?

**Không.** Spring Boot + Spring AOT thông thường đã tự đóng góp runtime hints cho nhiều bean được phát hiện tại build time, bao gồm proxy Spring AOP được xác định bằng bean definitions/advisors. Hãy xem **đường lỗi cụ thể** trước khi thêm `RuntimeHintsRegistrar`.

Cần đánh giá manual hints khi:

- Code ứng dụng hoặc thư viện thứ ba gọi `Proxy.newProxyInstance(...)` với interface set tạo động mà Spring AOT không phân tích được.
- Một `FactoryBean`/custom `BeanPostProcessor`/proxy factory sinh proxy theo điều kiện mà build time không thể dự đoán.
- Plugin hoặc feature-gated branch có các interface chưa tồn tại/được đăng ký khi `processAot` chạy.
- Khi chạy native xuất hiện lỗi trực tiếp về dynamic proxy interface set, reflection hoặc resource configuration; cần xác định **đúng loại hint còn thiếu**.
- Dependency do bên thứ ba không có metadata, hoặc metadata đi kèm quá cũ so với phiên bản thư viện đang sử dụng.

**Không thêm tùy tiện:** hint dư làm native metadata khó kiểm soát, có thể tăng footprint và che lấp vấn đề thực là bean/advisor không có trong graph AOT.

### 5.1. Ví dụ đăng ký thủ công JDK proxy hint cho Spring AOP khi cần

Trong Spring Framework 6.x, sử dụng `RuntimeHintsRegistrar` và `@ImportRuntimeHints`. Đoạn này là **phần bổ sung tùy chọn** vào ví dụ mục 4; chỉ thêm khi đã chứng minh AOT thiếu interface-set tương ứng.

`src/main/java/com/example/springaot/AdditionalProxyHints.java`:

~~~java
package com.example.springaot;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(AdditionalProxyHints.CheckoutHints.class)
public class AdditionalProxyHints {
    static class CheckoutHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader loader) {
            hints.proxies().registerJdkProxy(
                AopProxyUtils.completeJdkProxyInterfaces(CheckoutPort.class)
            );
        }
    }
}
~~~

Đoạn `completeJdkProxyInterfaces(CheckoutPort.class)` bổ sung các interface thường cần cho **Spring AOP JDK proxy**. Đặc biệt, `@ImportRuntimeHints` giúp registrar được dùng trong quá trình AOT analysis; class `@Configuration` phải nằm trong vùng component scan hoặc được `@Import` theo cấu hình ứng dụng.

**Cảnh báo chính xác về scope:** Ví dụ chỉ minh họa **một tập interface JDK proxy**. Nếu ứng dụng attach nhiều user interfaces, introduction advisors, proxy tùy biến hoặc có các khác biệt `opaque`/`DecoratingProxy`, hint thực tế có thể cần tập interface khác. Khi đó phải dựa vào `Advised`/proxy metadata thật và API trong **đúng phiên bản Spring**. Không khẳng định đoạn code trên chữa được mọi native proxy issue.

### 5.2. Viết test cho hint thay vì suy đoán

Nếu đã thêm `AdditionalProxyHints.CheckoutHints`, có thể kiểm tra registrar riêng bằng Spring AOT `RuntimeHintsPredicates`. Dùng test đơn giản sau trong package `com.example.springaot`:

~~~java
package com.example.springaot;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

class AdditionalProxyHintsTest {
    @Test
    void recordsExpectedJdkProxyInterfaces() {
        RuntimeHints hints = new RuntimeHints();
        new AdditionalProxyHints.CheckoutHints()
            .registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.proxies()
            .forInterfaces(AopProxyUtils.completeJdkProxyInterfaces(
                CheckoutPort.class
            )))
            .accepts(hints);
    }
}
~~~

Đây là kiểm thử **registrar tạo đúng hint**; nó không chứng minh native executable đã build thành công hay mọi Spring advisor đều hoạt động. Test này chỉ phù hợp nếu đã thêm class tùy chọn ở mục 5.1. Tệp phải nằm tại `src/test/java/com/example/springaot/AdditionalProxyHintsTest.java` và Gradle `testImplementation 'spring-boot-starter-test'` như mục 4.

**Nếu không có manual hints, không cần tạo class/test này.** Test tích hợp native cho `CheckoutPort.place()` vẫn là bằng chứng cuối cùng quan trọng nhất.

## 6. Kiểm tra theo version: tránh sao chép cấu hình sai

Spring Boot, Spring Framework, GraalVM và Native Build Tools phát triển độc lập nhưng có ma trận tương thích. Không lấy “latest” của từng thư viện rồi ghép tùy ý.

| Trường hợp | Cần xác minh |
| --- | --- |
| Spring Boot 3.3.x / Spring Framework 6.1.x | Java baseline, version Gradle/Native Build Tools, `RuntimeHintsRegistrar`, `@ImportRuntimeHints` và proxy strategy ở Boot |
| Spring Boot 3.4/3.5 / Spring Framework 6.2.x | Dependency management, cách Gradle plugin sinh AOT assets, thay đổi native metadata và test behavior |
| Spring Framework 7.x / Spring Boot 4.x | Yêu cầu Java/Gradle mới, thay đổi auto-proxy conventions/API. Không copy `@Proxyable` của Spring 7 sang Framework 6.1 |
| JVM dev/prod | `spring.aop.proxy-target-class`, bean registration, profiles, agent và classpath có giống nhau không |
| Native Linux/Windows/macOS | GraalVM JDK, native compiler toolchain, executable extension, binary/OS architecture và build profile |
| Container/native Buildpacks | Builder, Buildpack version, profile trong image build, filesystem/resources và môi trường khởi động |

Một khác biệt phiên bản cụ thể: tài liệu Spring Framework 7 giới thiệu `@Proxyable` để chỉ định kiểu proxy theo bean. Đây **không phải** annotation để dùng cho bài sample Spring Framework 6.1 (Boot 3.3). Khi nâng major, phải đọc lại tài liệu API của **đúng phiên bản** và kiểm thử cấu hình đã có; đừng tự động thay `@EnableAspectJAutoProxy` bằng `@Proxyable`.

Các property như `spring.aop.proxy-target-class` quyết định lựa chọn proxy ở Boot theo cơ chế tự cấu hình. Vì vậy, ví dụ cố định `false` để xác minh interface proxy; nếu đội triển khai chuyển sang class proxy, test kỳ vọng `isJdkDynamicProxy()` cũng phải được điều chỉnh **sau khi** xác nhận đây là thay đổi có chủ đích.

## 7. Chẩn đoán khi AOP trên JVM chạy nhưng Native Image hỏng

| Triệu chứng | Điều cần kiểm tra | Cách xử lý hợp lý |
| --- | --- | --- |
| Native build fail với dynamic proxy lỗi interface set | Gốc lỗi có nêu tập interfaces nào; AOT đã tự sinh hint chưa | Chỉ bổ sung hint cho interface set còn thiếu, rồi native rebuild |
| Native build fail khi tạo class-based proxy | Class/method `final`, access/module visibility, target type đã biết tại build time | Chỉnh proxy strategy/bean definition; JDK hint không sửa được lỗi subclass |
| Native chạy nhưng advice không chạy | Aspect bean có mặt trong AOT graph chưa, pointcut khớp không, target có thực sự là proxy không | Positive control ở runtime; so `isAopProxy` và advisor registration |
| Native thiếu reflection metadata | Stack trace chỉ rõ member nào bị reflection access | Đăng ký reflection hint đúng type/member hoặc dùng supported Spring binding |
| XML/resources bị thiếu | `META-INF`/config resource có được đóng vào native không | Resource hint cho đúng pattern hoặc cải thiện cấu hình build |
| Chạy native với profile khác build và thiếu bean/aspect | Profile/condition quyết định bean có thay đổi không | Build lại native với profile/bean graph phù hợp |
| Đổi loại proxy JDK -> CGLIB làm test fail | Property AOP/auto-proxy creator, kiểu target/interface | Dùng cùng proxy strategy trong build/JVM/native; test hai chiều khi migration |
| JVM tự tạo dynamic proxy bằng reflection, native báo lỗi | Proxy tạo ngoài Spring bean model hoặc interface set động | Metadata tùy chỉnh cho proxy ngoài tầm AOT |
| `@Transactional` hoặc `@Async` behavior khác | Proxy/advisor khác từ auto-config, annotation, execution context hoặc AOT graph | Kiểm thử propagation, rollback, executor và method invocation thật |
| Advice chạy đôi | Duplicate aspect registration, nhiều advisors, mixing weaving với Spring proxy | So advisor chain và event count, không chỉ thêm hints |

Để quan sát proxy tại runtime:

~~~java
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

Object bean = context.getBean("checkoutService");
System.out.println("AOP proxy: " + AopUtils.isAopProxy(bean));
System.out.println("JDK proxy: " + AopUtils.isJdkDynamicProxy(bean));
System.out.println("CGLIB proxy: " + AopUtils.isCglibProxy(bean));

if (bean instanceof Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
~~~

Đây là **mã chẩn đoán cần biến `context` từ một Spring ApplicationContext**, không phải file độc lập. Bean name `checkoutService` phải khớp cấu hình ứng dụng; không phụ thuộc vào chuỗi `advisor.toString()` trong assert tự động.

**Phương pháp đọc log:** nếu không có proxy ngay cả trên JVM, sửa Spring AOP wiring. Nếu JVM có proxy và native build chỉ rõ thiếu JDK proxy interfaces, xem hints. Nếu native thiếu cả bean do profile khác, hints không tạo bean mới. Nếu proxy tồn tại nhưng advice không khớp, kiểm tra pointcut và đường gọi.

## 8. Chiến lược kiểm thử qua ba lớp

### 8.1. Tầng JVM: test hợp đồng AOP

Kiểm thử trên Spring context đầy đủ:

- `AopUtils.isAopProxy(bean)` và loại proxy phải đúng mục tiêu.
- Ca dương: public method đúng pointcut làm advice chạy **chính xác một lần**.
- Ca âm: method khác, bean khác hoặc argument không khớp **không chạy**.
- Self-invocation và final/private method có hành vi đúng với proxy strategy đã chọn.
- `@Around` giữ return value, exception, số lần `proceed()`; transaction/security/audit test cả side effects.

### 8.2. Tầng AOT trên JVM

Dùng `processAot` để quan sát generated assets và kiểm tra rằng build-time configuration biết các bean/aspect cần thiết. Một JAR được đóng với AOT generated code có thể được chạy bằng `-Dspring.aot.enabled=true` nếu build plugin/version hỗ trợ và jar thật sự chứa assets đó; **không** thêm system property vào một JAR thông thường không chứa AOT artifacts.

Mục tiêu ở tầng này là tìm cấu hình bean/hint sai sớm trước khi biên dịch native; điều này tăng độ tin cậy nhưng chưa thay thế native test.

### 8.3. Tầng Native Image: kiểm thử binary thật

Native build rồi gọi các luồng nghiệp vụ giống JVM:

~~~text
Scenario 1: gọi place("A42")
Expected: return stored:A42, một audit event audit:A42

Scenario 2: gọi status()
Expected: return ready, không có audit event mới

Scenario 3: gọi place(...) qua bean tự tạo bằng new (nếu có case đối chứng)
Expected: không tự có Spring AOP proxy

Scenario 4: các profile/condition cụ thể
Expected: tập Spring beans và advisor đúng với bản native đã build
~~~

Các dòng trên là **tiêu chí test**, không phải stdout đã chạy. Để so sánh chính xác số lần advice trong ứng dụng thật, nên dùng counter/test probe có trạng thái riêng cho mỗi test hoặc so chênh lệch trước/sau, tránh counter shared giữa nhiều test. Cần thêm test xử lý lỗi và concurrency khi aspect có logic quản lý transaction/audit.

## 9. Chi phí hiệu năng và quản lý cấu hình

AOT và Native Image có thể cải thiện startup/footprint, nhưng **không có nghĩa advice trở nên miễn phí**. Spring AOP vẫn có chi phí khi intercept: proxy dispatch, pointcut matcher phù hợp, before/around logic và dependency mà advice gọi vào. Nếu advice ghi log/network/database đồng bộ, chi phí nghiệp vụ của advice có thể lớn hơn rất nhiều chi phí dispatch.

Trước khi tối ưu:

1. Đo latency, throughput, CPU và allocated memory khi có/không có aspect trên môi trường tương đương; xác định cách chạy JVM/AOT/native.
2. Thu hẹp pointcut vào service boundary cần thiết thay vì wildcard mọi bean.
3. Không thêm reflection/proxy hints quá rộng chỉ để “an toàn”; ưu tiên hints chính xác cho nhánh thật sự dùng.
4. Giữ bean definitions xác định ở build time, hạn chế proxy factory động nếu không có lý do nghiệp vụ.
5. Kiểm tra version dependencies đồng bộ qua BOM/plugin; nâng cấp có kiểm thử hồi quy, không sao chép mẹo native từ tài liệu khác major.
6. Giữ test AOP behavior như một contract cùng với kiểm thử JVM và native pipeline để phát hiện advice mất hoặc chạy lặp.

## 10. Checklist quyết định có cần custom proxy registration

1. **Xác minh điểm cắt:** Spring bean mang aspect được đăng ký, method đi qua proxy và pointcut match trên JVM.
2. **Xác minh môi trường:** ghi version Spring Boot/Framework, Java/GraalVM, Gradle plugins, profile và proxy-target-class.
3. **Chạy Spring AOT:** kiểm tra `processAot`, generated proxy assets và hint metadata đã có.
4. **Chạy native thật:** lưu stack trace lỗi và interface/type được nhắc tên, nếu có.
5. **Phân loại:** thiếu bean/advisor, lỗi JDK proxy interfaces, lỗi CGLIB subclass, thiếu reflection/resource, hay lỗi runtime logic?
6. **Chỉ khi cần** dùng `RuntimeHintsRegistrar`/`@ImportRuntimeHints` hoặc metadata của dependency, ưu tiên interface set đầy đủ cho Spring AOP.
7. **Kiểm thử lại:** test registrar (nếu có), AOT, native, positive/negative AOP behaviors và đúng build profile; kiểm tra không có advice trùng lặp.
8. **Bảo trì version:** ghi rõ baseline mà hints được xác nhận, kiểm tra lại khi nâng Spring/Boot/GraalVM và khi thay interface, annotation, advisor hoặc proxy strategy.

**Ghi nhớ:** Trong Spring AOP, phần lớn proxy chuẩn được Spring AOT xử lý tự động khi **bean graph xác định ở build time**. Khai báo JDK proxy hints thủ công là giải pháp cho **một thiếu hụt đã được chứng minh**, không phải bước bắt buộc cho mọi `@Aspect`. Quyết định đúng phải dựa trên **phiên bản, profile, loại proxy, AOT outputs và binary thực tế**.

## Tài liệu chính thức

- Spring Boot 3.3 — [Introducing GraalVM Native Images](https://docs.spring.io/spring-boot/3.3/reference/packaging/native-image/introducing-graalvm-native-images.html)
- Spring Boot 3.3 — [Advanced Native Images Topics](https://docs.spring.io/spring-boot/3.3/reference/packaging/native-image/advanced-topics.html)
- Spring Framework — [AOT Optimizations](https://docs.spring.io/spring-framework/reference/core/aot.html)
- Spring Framework — [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- Spring Framework — [@AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html)
- Spring Framework — [AopProxyUtils.completeJdkProxyInterfaces](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/AopProxyUtils.html)
- Spring Boot — [Testing GraalVM Native Images](https://docs.spring.io/spring-boot/how-to/native-image/testing-native-applications.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-performance-maintenance-010">Tương thích Kotlin final và điều kiện tạo proxy khi ứng dụng có Kotlin</a>

<details>
<summary>Xem chi tiết</summary>

# Tương thích Kotlin final và điều kiện tạo proxy trong Spring AOP

## 1. Vấn đề: Kotlin final mặc định khác Java open mặc định

Một ứng dụng Spring có thể dùng Kotlin để viết `@Service`, `@Controller`, `@Configuration` và các method áp dụng `@Transactional`, `@Cacheable`, `@Async` hoặc custom `@Aspect`. Khi port code Java sang Kotlin, cùng một Spring configuration có thể đột nhiên gặp lỗi khi tạo proxy, hoặc chạy được nhưng advice không bao quanh method như kỳ vọng.

Lý do quan trọng nhất là **class và member functions của Kotlin là `final` theo mặc định**. Spring AOP lại có hai chiến lược proxy:

1. **JDK dynamic proxy** triển khai một hoặc nhiều Java/Kotlin interfaces, không cần kế thừa class implementation.
2. **Class-based proxy (Spring CGLIB)** tạo subclass của target class rồi override những methods có thể override để đưa Spring interceptor chain vào.

Nếu target Kotlin `final` nhưng có interface phù hợp, **JDK proxy vẫn có thể hoạt động**. Nếu Spring phải subclass class `final`, class-based proxy **không tạo được**. Nếu class `open` nhưng một method vẫn `final`, CGLIB proxy có thể tồn tại nhưng **không advice được method final ấy**.

Đây là vấn đề **Spring AOP proxy compatibility**, không phải quy tắc chung rằng “mọi class Kotlin đều phải ghi `open`”. Kotlin cung cấp compiler plugin `kotlin-spring` (dựa trên `all-open`) để mở đúng những classes/members được đánh dấu bằng Spring stereotypes hoặc các annotations cấu hình thích hợp, tránh phải viết `open` thủ công trên mọi service.

Bài tập trung vào Spring Aspect và proxy creation: điều kiện tạo proxy, plugin biên dịch, lựa chọn JDK/CGLIB, self-invocation, kiểm thử reflection/bytecode và hướng chẩn đoán để bảo trì ứng dụng Spring đa ngôn ngữ Java–Kotlin.

## 2. Hai cơ chế proxy và Kotlin final

### 2.1. JDK dynamic proxy: interface quan trọng hơn class final

~~~kotlin
interface WorkApi {
    fun run(id: String): String
}

class FinalWorkService : WorkApi {
    override fun run(id: String): String = "done:$id"
}
~~~

Kotlin class `FinalWorkService` mặc định `final` và `run` cũng không phải `open` trên implementation. Tuy nhiên, khi Spring AOP chọn **JDK interface proxy**, object được trả về là proxy triển khai `WorkApi`; nó không cần kế thừa `FinalWorkService`.

~~~text
Caller: WorkApi
    |
    v
JDK dynamic proxy implements WorkApi
    |
    +-- Spring AOP advice chain
    |
    v
FinalWorkService target (Kotlin class final)
~~~

Điều kiện thực tế:

- Spring quản lý target bean và có một advisor/pointcut phù hợp để tạo proxy.
- Proxy expose `WorkApi` và caller sử dụng contract ấy.
- Method cần gọi xuất hiện trên interface được JDK proxy implement.
- Không có cấu hình khác **ép `proxyTargetClass=true`** cho bean hoặc toàn bộ auto-proxy infrastructure.
- Caller không ép kiểu JDK proxy sang `FinalWorkService`; `proxy as FinalWorkService` sẽ sai vì proxy không là subclass của concrete class.

**Chính xác:** JDK proxy có thể proxy **interface** của final class, chứ không biến final class thành class có thể subclass. Class target thật vẫn final.

### 2.2. Class-based proxy (CGLIB): phải có subclass và override

~~~kotlin
open class WorkService {
    open fun run(id: String): String = "done:$id"
}
~~~

Nếu bean không có business interface, Spring thường cần class-based proxy. Spring tạo một subclass runtime của `WorkService` rồi intercept `run`.

~~~text
Caller: WorkService
    |
    v
Spring-generated subclass of WorkService
    |
    +-- override run(...)
    +-- Spring AOP advice chain
    |
    v
Original WorkService target
~~~

Với `class WorkService` không có `open`, subclass không thể được tạo. Với `open class WorkService { fun run(...) }`, class subclass được nhưng method `run` **vẫn final theo mặc định** nên không thể bị CGLIB advice.

Điều này còn đúng với Java `final` class/method, không riêng Kotlin. Kotlin khiến vấn đề phổ biến hơn vì cả hai mặc định final.

### 2.3. Các giới hạn Spring AOP vẫn giữ nguyên

- `private` methods không được proxy advice qua subclass override.
- `final` methods không được override dù class enclosing đã open.
- JDK proxy chỉ expose interfaces; method riêng trên concrete class nằm ngoài interface không thể được gọi qua JDK proxy bằng cast implementation.
- Spring AOP là proxy-based: `this.otherMethod()` / gọi nội bộ target **bypass proxy**; việc mở class bằng compiler plugin **không sửa self-invocation**.
- Spring AOP proxy không tự intercept constructor/field access. `kotlin-spring` không đổi Spring proxy thành AspectJ bytecode weaving.
- Class-based proxies có thể gặp giới hạn method visibility, JPMS, classloaders và reflection tương tự ứng dụng Java.
- `data class`, `object`, `value class`, classes có compiler-generated methods/special semantics cần kiểm tra riêng; không mặc định mọi kiểu Kotlin đều là target AOP phù hợp.

## 3. Bảng chọn proxy theo kiểu Kotlin

| Target và Spring AOP configuration | Có thể tạo proxy? | Hệ quả |
| --- | --- | --- |
| `final class S : ServiceApi`, proxy interface-based, advice match | **Có thể**, JDK proxy | Inject `ServiceApi`, không cast sang `S` |
| `final class S`, không interface, cần CGLIB | **Không** | Spring không subclass được `S` |
| `final class S : ServiceApi` nhưng ép `proxyTargetClass=true` | **Không** với class-based proxy | Có interface cũng không giúp nếu đã ép CGLIB |
| `open class S` có `open fun method`, CGLIB | **Có thể** | Method có thể được advice nếu pointcut match |
| `open class S` nhưng `fun method` final, CGLIB | Proxy class có thể tạo; method final **không được advice** | Một lỗi dễ bị bỏ sót nếu chỉ kiểm tra `isAopProxy=true` |
| `@Service class S` với `kotlin-spring` plugin | Thường mở class và members cho CGLIB | Vẫn cần Spring context, aspect/advisor, pointcut |
| `@Service class S` không có plugin/all-open | Vẫn final nếu không ghi `open` tường minh | JDK interface proxy có thể được; CGLIB có thể lỗi |
| `class S` được tạo bằng `@Bean` nhưng class không mang stereotype và không `open` | Không tự được compiler plugin mở chỉ do `@Bean` trả nó | Nếu CGLIB cần subclass thì phải mở class/method phù hợp |
| `@Configuration(proxyBeanMethods=false)` là final | Chế độ lite có thể không cần CGLIB subclass của config class | Không giải quyết class final của **service target** |

**Đọc bảng theo hai lớp:** khả năng **tạo proxy** và khả năng **method được advice** là hai câu hỏi khác nhau. Một bean có thể được AOP proxy vì advisor match method khác, nhưng lời gọi tới một method final vẫn bypass method advice dự kiến.

## 4. kotlin-spring và all-open compiler plugin làm gì?

Kotlin hỗ trợ `all-open` compiler plugin: những class có annotation được chỉ định (hoặc được meta-annotate tương ứng) sẽ được compiler tạo bytecode **không final**, đồng thời mở các member functions phù hợp. `kotlin-spring` là preset thuận tiện cho Spring.

Ví dụ Gradle Kotlin DSL:

~~~kotlin
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
}
~~~

Plugin `kotlin("plugin.spring")` sẽ nhận biết các annotations như `@Component`, `@Async`, `@Transactional`, `@Cacheable` và meta-annotations tương ứng. Các stereotype `@Service`, `@Controller`, `@Repository`, `@RestController`, `@Configuration` thường được meta-annotate với `@Component`, do đó cũng được compiler mở.

~~~kotlin
@Service
class ReportService {
    fun generate(): String = "report"
}
~~~

Với plugin được bật đúng tại **module chứa source `ReportService`**, class và function trong bytecode trở thành overrideable mặc dù source Kotlin không viết `open`. Spring CGLIB có thể subclass class này và intercept `generate` nếu advisor phù hợp.

### 4.1. Plugin chạy ở thời điểm biên dịch, không phải runtime

Chuỗi hoạt động:

~~~text
Kotlin source @Service class ReportService
              |
              v
Kotlin compiler + kotlin-spring all-open plugin
              |
              v
.class bytecode: ReportService / generate không final
              |
              v
Spring ApplicationContext khởi tạo bean
              |
              v
Spring AOP auto-proxy creator / Pointcut advisors
              |
              v
CGLIB proxy subclass được sinh nếu cần
              |
              v
Caller đi qua proxy -> advice -> target
~~~

Chỉ thêm plugin **không làm aspect tự chạy**. Ứng dụng vẫn phải đăng ký target bean, aspect/advisor, `@EnableAspectJAutoProxy` (hoặc auto-proxy infrastructure tương đương) và gọi bean **qua Spring proxy**.

Tương tự, chỉ thêm `aspectjweaver` dependency không tự mở Kotlin final classes. **All-open** là compile-time modifier; **Spring AOP** là runtime proxying; **AspectJ weaving** là một cơ chế khác. Không trộn ba cơ chế khi tìm nguyên nhân lỗi.

### 4.2. Plugin và module boundaries

Nếu `service-a` trong module Kotlin không bật `kotlin-spring`, nhưng `web-app` bật, class `service-a` **vẫn được biên dịch là final**. Plugin không tự viết lại bytecode của dependency đã được biên dịch ở module khác.

Cần kiểm tra:

1. Module nào thực sự chứa Kotlin source của target bean?
2. Module đó có áp dụng `org.jetbrains.kotlin.plugin.spring` và cùng Kotlin compiler version không?
3. Gradle build có thực sự sử dụng plugin task khi compile, hay source thuộc subproject/source set khác?
4. `@Service`/`@Component` nằm trên **class** hay chỉ annotations ở method/bean factory?
5. Class thuộc library bên ngoài đã đóng gói jar final? Kotlin compiler plugin của project **không thể mở class đã compile** trong jar khác.

### 4.3. Plugin không tự mở mọi class

~~~kotlin
class PlainWorker {
    fun run() = "plain"
}
~~~

`PlainWorker` không có annotation thuộc preset và không có `open`, do đó vẫn final. Đăng ký:

~~~kotlin
@Bean
fun plainWorker(): PlainWorker = PlainWorker()
~~~

không tự thay đổi bytecode của `PlainWorker`. Việc method factory được đánh dấu `@Bean` **không đồng nghĩa return type được all-open transform**. Nếu cần class-based proxy cho `PlainWorker`, chọn một trong các cách có chủ đích: mở class/method bằng `open`, thêm một annotation phù hợp trên class, cấu hình all-open custom annotation, hoặc thiết kế interface-based JDK proxy.

Đừng đặt `@Service` giả tạo lên mọi class thuần chỉ để compiler mở nó nếu class không có mục đích trở thành Spring component. Mở tường minh một class hoặc sử dụng hợp đồng interface có thể rõ ràng hơn.

### 4.4. Annotation tùy chỉnh và meta-annotation

Nếu ứng dụng có stereotype:

~~~kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@org.springframework.stereotype.Component
annotation class UseCase

@UseCase
class ApproveOrder {
    fun execute(): String = "approved"
}
~~~

Plugin hỗ trợ **meta-annotations**, nên `@UseCase` được meta-annotate với `@Component` có thể làm `ApproveOrder` và members được mở khi biên dịch. Tuy nhiên để bean thực sự được Spring quản lý, package scanning hoặc `@Bean` registration phải đưa class vào ApplicationContext.

Nếu annotation riêng không có meta-annotation thuộc preset, thêm `kotlin("plugin.allopen")` và khai báo:

~~~kotlin
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.allopen") version "2.0.21"
}
allOpen {
    annotation("demo.MyAopCandidate")
}
~~~

Đây là **biến thể build.gradle.kts**, không chép trùng toàn bộ khối `plugins` của project đã có. Nếu hai plugins cùng tác động trên một class, cần kiểm tra build/plugin configuration thực tế; không dùng all-open tràn lan để mở các types không liên quan.

## 5. @Transactional, @Cacheable, @Async và custom @Aspect trong Kotlin

Các Spring cross-cutting features thường sử dụng AOP advisors hoặc proxy infrastructure, vì thế Kotlin `final` ảnh hưởng tương tự:

| Feature | Điều cần để lời gọi Spring proxy hoạt động | Bẫy Kotlin |
| --- | --- | --- |
| Custom `@Aspect` + `@Around` | Target/aspect beans, `@EnableAspectJAutoProxy`, pointcut match, eligible proxy | Class/method final khi CGLIB cần subclass |
| `@Transactional` | `@EnableTransactionManagement`/Boot transaction config, transaction manager, proxy-invoked method | Self-invocation và final method có thể bypass |
| `@Cacheable` | `@EnableCaching`, cache manager, call qua proxy | Method final không được override; self-invocation/cache key khác kỳ vọng |
| `@Async` | `@EnableAsync`, executor phù hợp, call qua proxy | `this.asyncMethod()` vẫn synchronous/bypass proxy |
| Method security | Security AOP infrastructure + proxy | Final methods/class và proxy interfaces, annotation metadata |
| `@Retryable` (nếu dùng Spring Retry) | Retry infrastructure/policy và proxy-invoked method | Nhiều attempts khác proxy creation; method final có thể bypass |

**Không lẫn annotation với proxy:** `@Transactional`/ `@Cacheable` trên source code chỉ là metadata cho framework. Nếu không bật infrastructure, không có bean eligible, caller đi qua raw instance hoặc Spring không thể intercept method final bằng class proxy, metadata không tự tạo behavior.

Đối với custom `@Aspect`, không bắt buộc **chính class aspect** phải là target được CGLIB proxy để advice áp dụng lên service khác. `@Aspect` cần trở thành Spring bean; Spring có thể phân tích advice methods của aspect bean mà không biến aspect thành target của advice ấy. Trọng tâm là **Kotlin target class/method** có thể được Spring proxy và intercept đúng hay không.

### 5.1. @Configuration(proxyBeanMethods=false) không sửa mọi vấn đề final

Kotlin `@Configuration` class cũng mặc định final, có thể xung đột với Spring configuration class enhancement nếu `proxyBeanMethods=true`. Với:

~~~kotlin
@Configuration(proxyBeanMethods = false)
class DemoConfig {
    @Bean
    fun helper() = Helper()
}
~~~

Spring không cần tạo subclass để intercept các lời gọi `@Bean` method trong configuration class. Nhưng **`Helper` vẫn final** nếu không được mở bởi compiler plugin hoặc source. Nếu `Helper` có method cần custom `@Around` qua CGLIB, `proxyBeanMethods=false` không giải quyết.

Với `kotlin-spring`, `@Configuration` là một meta-`@Component` type, nên compiler cũng hỗ trợ mở class/member của configuration. Việc đặt `proxyBeanMethods=false` còn có ích để làm rõ wiring bằng constructor/`@Bean` parameters và tránh giả định gọi trực tiếp `@Bean` method sẽ được CGLIB intercept.

### 5.2. @Bean return type, method visibility và Kotlin property getters

Một Spring bean có thể được khai báo:

~~~kotlin
@Bean
fun paymentService(): PaymentApi = FinalPaymentService()
~~~

Với JDK proxy, inject qua `PaymentApi` thường phù hợp nếu pointcut/advisor match. Nhưng nếu có `@Bean fun paymentService(): FinalPaymentService` mà bean bị JDK-proxy theo interfaces, DI theo **concrete class** có thể thất bại. Dùng interface contract ở phía caller hoặc class proxy có chủ đích khi type phù hợp.

Các Kotlin property getter/setter được compiler sinh ra là JVM methods. Spring proxy chỉ intercept những methods thực sự được expose/override phù hợp; một final getter hoặc method liên quan có thể không được advice bởi CGLIB. Với Spring Kotlin, tài liệu nhấn mạnh **type-level `@Component`** thay vì chỉ method-level `@Bean` khi cần plugin mở cả những property-related methods. Không suy đoán visibility từ cú pháp property Kotlin; kiểm tra bytecode khi lỗi khó hiểu.

## 6. Ví dụ Kotlin chạy được: cùng context có JDK proxy cho final class và CGLIB proxy cho @Service

Ví dụ dùng **JDK 17**, **Kotlin 2.0.21**, **Gradle 8.8** và **Spring Framework 6.2.18**. Không cần Spring Boot. Ta cố ý có hai target:

1. `FinalJobService` **không annotated**, Kotlin class final, implement `JobApi`: Spring AOP tạo **JDK proxy**, nên advice vẫn hoạt động.
2. `OpenJobService` annotated `@Service` nhưng không ghi `open`: `kotlin-spring` compiler plugin mở class/method; class không implement business interface nên Spring AOP tạo **CGLIB proxy**.
3. `ObserveAspect` do `@Bean` đăng ký; `@Around` match hai methods tương ứng.
4. `status()` của OpenJobService là method **không match**, cho thấy có proxy không đồng nghĩa mọi method đều được advice.

### 6.1. Cấu trúc

~~~text
kotlin-final-spring-aop/
├── settings.gradle.kts
├── build.gradle.kts
└── src/
    ├── main/kotlin/demo/App.kt
    └── test/kotlin/demo/AopProxyTest.kt
~~~

Gradle wrapper không được liệt kê trong sơ đồ vì có thể dùng Gradle 8.8 đã cài sẵn; tạo wrapper bằng `gradle wrapper --gradle-version 8.8` nếu cần cố định môi trường. Khi đã có wrapper, trên Linux/macOS dùng `./gradlew`, trên Windows dùng `gradlew.bat`.

### 6.2. settings.gradle.kts

~~~kotlin
rootProject.name = "kotlin-final-spring-aop"
~~~

### 6.3. build.gradle.kts

~~~kotlin
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("org.springframework:spring-context:6.2.18")
    implementation("org.springframework:spring-aop:6.2.18")
    implementation("org.aspectj:aspectjweaver:1.9.22.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("demo.AppKt")
}

tasks.test {
    useJUnitPlatform()
}
~~~

`kotlin("plugin.spring")` là điểm then chốt: compile class `@Service` thành dạng có thể được CGLIB subclass. `aspectjweaver` có mặt để Spring parse `@AspectJ` pointcut expressions; ứng dụng này dùng **Spring proxy-based AOP**, không dùng AspectJ load-time/compile-time weaving.

Nếu project nhiều modules, phải đặt compiler plugin ở **subproject thực sự compile `OpenJobService`**.

### 6.4. src/main/kotlin/demo/App.kt

~~~kotlin
package demo

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.aop.support.AopUtils
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.stereotype.Service

class Events {
    private val entries = mutableListOf<String>()

    fun add(value: String) {
        entries.add(value)
    }

    fun snapshot(): List<String> = entries.toList()
}

// Không có @Component/@Service: Kotlin class và methods đều final.
interface JobApi {
    fun run(id: String): String
}

class FinalJobService(private val events: Events) : JobApi {
    override fun run(id: String): String {
        events.add("target:run")
        return "J:" + id
    }
}

// @Service khiến kotlin-spring mở class và methods trong bytecode.
// Bean được đăng ký bằng @Bean, không cần component scan.
@Service
class OpenJobService(private val events: Events) {
    fun process(id: String): String {
        events.add("target:process")
        return "O:" + id
    }

    fun status(): String {
        events.add("target:status")
        return "READY"
    }
}

@Aspect
class ObserveAspect(private val events: Events) {
    @Around(
        "execution(* demo.FinalJobService.run(..)) || " +
        "execution(* demo.OpenJobService.process(..))"
    )
    fun around(pjp: ProceedingJoinPoint): Any? {
        events.add("advice:" + pjp.signature.name)
        return pjp.proceed()
    }
}

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
class DemoConfig {
    @Bean
    fun events() = Events()

    @Bean
    fun finalJobService(events: Events): JobApi =
        FinalJobService(events)

    @Bean
    fun openJobService(events: Events): OpenJobService =
        OpenJobService(events)

    @Bean
    fun observeAspect(events: Events) = ObserveAspect(events)
}

fun main() {
    AnnotationConfigApplicationContext(DemoConfig::class.java).use { ctx ->
        val finalJob = ctx.getBean("finalJobService", JobApi::class.java)
        val openJob = ctx.getBean(OpenJobService::class.java)
        val events = ctx.getBean(Events::class.java)

        println("finalAop=" + AopUtils.isAopProxy(finalJob))
        println("finalJdk=" + AopUtils.isJdkDynamicProxy(finalJob))
        println("finalTarget=" + AopUtils.getTargetClass(finalJob).name)

        println("openAop=" + AopUtils.isAopProxy(openJob))
        println("openCglib=" + AopUtils.isCglibProxy(openJob))

        println("finalResult=" + finalJob.run("A"))
        println("openResult=" + openJob.process("B"))
        println("status=" + openJob.status())
        println("events=" + events.snapshot().joinToString("|"))
    }
}
~~~

**Tính runnable:** file chỉ có một package `demo`, các declarations cùng package, không thiếu bean classes. `DemoConfig` đăng ký đúng 4 application beans: `Events`, `FinalJobService` qua interface, `OpenJobService` qua concrete class, và `ObserveAspect`.

`@Service` trên OpenJobService được compiler plugin xử lý vì annotation này meta-annotated với `@Component`. Vì không có `@ComponentScan`, Spring không tự tạo bản sao OpenJobService; **`@Bean`** là nguồn đăng ký duy nhất. Do đó việc dùng `@Service` ở đây minh họa **compile-time opening** mà không vô tình tạo hai bean instances.

Aspect được đăng ký tường minh bằng `@Bean`, nên không cần class aspect mang `@Component`. `@EnableAspectJAutoProxy` kích hoạt Spring AOP infrastructure. `FinalJobService` final vẫn phù hợp vì `JobApi` JDK proxy; `OpenJobService` không có business interface nhưng được plugin mở, nên dùng class proxy.

### 6.5. Chạy ứng dụng và stdout dự kiến

Tại thư mục project có Gradle 8.8 và JDK 17:

~~~bash
gradle run
~~~

Các dòng **do `main()` tự in** dự kiến:

~~~text
finalAop=true
finalJdk=true
finalTarget=demo.FinalJobService
openAop=true
openCglib=true
finalResult=J:A
openResult=O:B
status=READY
events=advice:run|target:run|advice:process|target:process|target:status
~~~

Gradle/Spring có thể in thêm task logs; chỉ những dòng `println` của ứng dụng được liệt kê.

Giải thích:

1. `finalAop=true`, `finalJdk=true`: Spring trả JDK AOP proxy của interface `JobApi` dù underlying `FinalJobService` final.
2. `finalTarget=demo.FinalJobService`: target class vẫn là Kotlin final class; nó không bị compiler plugin mở vì không mang Spring stereotype.
3. `openAop=true`, `openCglib=true`: `OpenJobService` được all-open compiler plugin mở; Spring có thể tạo subclass proxy và intercept `process`.
4. `finalJob.run("A")` đi qua advice trước khi vào target, trả `J:A`.
5. `openJob.process("B")` đi qua advice trước khi vào target, trả `O:B`.
6. `openJob.status()` được gọi qua cùng proxy nhưng **không match pointcut**, nên chỉ ghi `target:status`.
7. Không có dynamic `-javaagent` hoặc compile-time AspectJ weaving; đây là Spring AOP proxy thông thường.

**Lưu ý:** main minh họa output *dự kiến theo cấu hình*, không phải kết quả benchmark hoặc log đã chạy trên máy đang làm bài.

### 6.6. src/test/kotlin/demo/AopProxyTest.kt

~~~kotlin
package demo

import java.lang.reflect.Modifier
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AopProxyTest {
    @Test
    fun finalKotlinClassCanBeJdkProxiedViaInterface() {
        AnnotationConfigApplicationContext(DemoConfig::class.java).use { ctx ->
            val service = ctx.getBean("finalJobService", JobApi::class.java)
            val events = ctx.getBean(Events::class.java)

            assertTrue(Modifier.isFinal(FinalJobService::class.java.modifiers))
            assertTrue(AopUtils.isAopProxy(service))
            assertTrue(AopUtils.isJdkDynamicProxy(service))
            assertFalse(service is FinalJobService)

            assertEquals("J:X", service.run("X"))
            assertEquals(
                listOf("advice:run", "target:run"),
                events.snapshot()
            )
        }
    }

    @Test
    fun springCompilerPluginOpensServiceForCglib() {
        AnnotationConfigApplicationContext(DemoConfig::class.java).use { ctx ->
            val service = ctx.getBean(OpenJobService::class.java)
            val events = ctx.getBean(Events::class.java)

            assertFalse(Modifier.isFinal(OpenJobService::class.java.modifiers))
            assertFalse(
                Modifier.isFinal(
                    OpenJobService::class.java.getMethod(
                        "process", String::class.java
                    ).modifiers
                )
            )
            assertTrue(AopUtils.isAopProxy(service))
            assertTrue(AopUtils.isCglibProxy(service))
            assertEquals("O:Y", service.process("Y"))
            assertEquals(
                listOf("advice:process", "target:process"),
                events.snapshot()
            )
        }
    }

    @Test
    fun methodNotMatchedByPointcutRunsWithoutAdvice() {
        AnnotationConfigApplicationContext(DemoConfig::class.java).use { ctx ->
            val service = ctx.getBean(OpenJobService::class.java)
            val events = ctx.getBean(Events::class.java)

            assertEquals("READY", service.status())
            assertEquals(listOf("target:status"), events.snapshot())
        }
    }

    @Test
    fun creatingRawFinalObjectBypassesSpringAop() {
        val events = Events()
        val raw: JobApi = FinalJobService(events)

        assertFalse(AopUtils.isAopProxy(raw))
        assertEquals("J:RAW", raw.run("RAW"))
        assertEquals(listOf("target:run"), events.snapshot())
    }
}
~~~

Tests tách bạch bốn điều:

- Kotlin class final không đồng nghĩa Spring AOP chắc chắn fail: JDK interface proxy vẫn làm việc.
- `kotlin-spring` plugin thực sự thay đổi **class/method modifiers ở bytecode**, được kiểm tra qua Java reflection thay vì chỉ nhìn `@Service`.
- Có CGLIB proxy không đồng nghĩa **mọi method** chạy advice; `status` là trường hợp không match.
- Gọi object mới bằng `FinalJobService(events)` không đi qua Spring proxy dù source code giống target được container quản lý.

Chạy:

~~~bash
gradle test
~~~

Bốn JUnit 5 tests được kỳ vọng pass. Khi Gradle test kết thúc thành công, có thể xem `build/reports/tests/test/index.html`. Đó là **kết quả kỳ vọng từ mã ví dụ**; bài này không khẳng định đã chạy test trên máy người học.

## 7. Biến thể kiểm tra thất bại do class final và CGLIB

Để thấy trường hợp ngược lại, khai báo một Kotlin class không có interface và không có stereotype/open:

~~~kotlin
class FinalOnlyService {
    fun work() = "done"
}
~~~

Nếu bạn dùng Spring `ProxyFactory` muốn tạo **class-based proxy** và gắn một interceptor:

~~~kotlin
val factory = org.springframework.aop.framework.ProxyFactory()
factory.setTarget(FinalOnlyService())
factory.isProxyTargetClass = true
factory.addAdvice(
    org.aopalliance.intercept.MethodInterceptor { invocation ->
        invocation.proceed()
    }
)
val proxy = factory.proxy  // Có thể thất bại vì final class.
~~~

Đây là **snippet phản diện để minh họa**, không thêm vào demo chính. Với Kotlin `FinalOnlyService` final, CGLIB không thể tạo subclass, nên Spring có thể ném lỗi proxy creation (ví dụ `AopConfigException` bọc nguyên nhân từ code generation). Exact exception/message phụ thuộc Spring/JDK version; không assert nguyên văn thông báo lỗi.

### 7.1. Sửa bằng interface/JDK proxy

~~~kotlin
interface Worker {
    fun work(): String
}

class FinalOnlyService : Worker {
    override fun work() = "done"
}
~~~

Với `Worker` interface và không ép CGLIB, Spring JDK proxy có thể implement `Worker` dù target Kotlin final. Caller dùng `Worker`, không dùng concrete implementation.

### 7.2. Sửa bằng open class và open method

~~~kotlin
open class OpenWorker {
    open fun work() = "done"
}
~~~

Phù hợp khi cần class proxy nhưng không dùng compiler plugin. Mở cả class lẫn method; chỉ `open class` mà method vẫn `final` có thể dẫn tới “proxy tạo được nhưng advice không chạy”.

### 7.3. Sửa bằng Spring stereotype và kotlin-spring plugin

~~~kotlin
@Service
class SpringWorker {
    fun work() = "done"
}
~~~

Với `kotlin("plugin.spring")` có hiệu lực **đúng module compile class này**, compiler mở class và method theo all-open rules. Spring vẫn cần đăng ký bean, đủ AOP advisors và lời gọi qua proxy. Không cần gắn `open` lên tất cả Spring beans nếu project đã dùng plugin.

## 8. Những điều plugin không thể giải quyết

### 8.1. Self-invocation

~~~kotlin
@Service
class InvoiceService {
    fun process() {
        save()    // Gọi trên this, bỏ qua proxy riêng của save().
    }

    @Transactional
    fun save() {
        // persistence
    }
}
~~~

Snippet này cần import `@Transactional` và transaction infrastructure. Giả sử class/method đã được plugin mở và bean được Spring CGLIB proxy, lời gọi `this.save()` từ target **vẫn không tạo một proxy invocation mới**. Nếu cần transaction/advice riêng trên `save`, ưu tiên tách sang injected Spring bean khác.

Không dùng `proxyTargetClass=true` hay `kotlin-spring` như phép chữa self-invocation.

### 8.2. Annotated method nhưng infrastructure thiếu

Một `@Service` với `@Transactional` không tự có transaction nếu chưa bật transaction management hoặc thiếu `PlatformTransactionManager`. Tương tự `@Cacheable` cần cache infrastructure; `@Async` cần async infrastructure; custom `@Aspect` cần aspect bean và `@EnableAspectJAutoProxy`.

Compiler all-open chỉ giải quyết **khả năng subclass/override**. Nó không tự cấp cache, transaction, retry, security hay aspect semantics.

### 8.3. Kotlin methods đặc biệt

- `suspend fun`: Kotlin compiler tạo continuation-based JVM signature. Spring có hỗ trợ một số suspend/reactive semantics tùy framework version và integration, nhưng không mặc định mọi custom `@Around` dùng arguments/return values sẽ hoạt động giống method synchronous Java.
- `internal` methods: Kotlin có thể dùng JVM name mangling trong một số trường hợp, làm pointcut dựa hoàn toàn vào tên method dễ sai. Hãy kiểm tra bytecode thực và Spring annotation resolution.
- `inline`/compiler-generated functions: JVM method và khả năng proxy interception có thể không tương ứng trực tiếp với source-level call.
- `data class`: compiler-generated `equals`, `hashCode`, `copy` và các method đặc biệt có semantics riêng; thường không phải nơi phù hợp để nhồi Spring AOP cross-cutting concerns.
- `object`: singleton Kotlin không phải một class target bình thường cho CGLIB; không giả định có thể proxy/subclass mọi `object`.
- `private`/ `final override`: Spring CGLIB không thể override những method ấy dù class khác đã open.

Bài học thực tế: khi một method không được advice, **xem JVM bytecode/Modifiers thực**, proxy mode và pointcut; đừng chỉ nhìn Kotlin syntax.

### 8.4. Spring Boot và mặc định proxy mode

Spring Framework core thường ưu tiên interface-based JDK proxy khi target có interface, trừ khi cấu hình yêu cầu class proxy. Trong một ứng dụng Spring Boot, mặc định và property **có thể khác**; một số thiết lập Boot có thể tạo class proxies ngay cả khi bean có interface.

Vì vậy Kotlin final class implement interface **không phải luôn an toàn** nếu project hoặc một auto-proxy configuration ép `proxyTargetClass=true`. Kiểm tra:

~~~properties
spring.aop.proxy-target-class=true
~~~

Đây là **ví dụ cấu hình Spring Boot** có thể ép class-based proxy; không áp dụng trực tiếp vào Spring Framework context tối giản nếu không có Boot auto-configuration. Nếu không cần class-based proxy, cân nhắc cấu hình `false` tại nơi thích hợp, nhưng phải đánh giá các bean khác đang phụ thuộc vào việc inject theo concrete class.

Các cấu hình `@EnableAspectJAutoProxy`, `@EnableTransactionManagement` hoặc XML auto-proxy settings có thể tương tác với cấu hình chung. Đừng kết luận chỉ từ một annotation local rằng toàn bộ context chắc chắn dùng JDK proxies.

## 9. Kiểm tra bytecode và proxy đúng cách

### 9.1. Xem class và method có final không

Java reflection trong Kotlin:

~~~kotlin
import java.lang.reflect.Modifier

val klass = OpenJobService::class.java
println("classFinal=" + Modifier.isFinal(klass.modifiers))
println(
    "methodFinal=" + Modifier.isFinal(
        klass.getMethod("process", String::class.java).modifiers
    )
)
~~~

Snippet cần class `OpenJobService` từ demo. Với compiler plugin `kotlin-spring` đang hoạt động, hai giá trị dự kiến đều `false`. Nếu một giá trị là `true`, xem lại module/plugin/annotation placement.

Có thể dùng `javap` đối với các class đã biên dịch:

~~~bash
javap -p -v -classpath build/classes/kotlin/main demo.OpenJobService
~~~

Đọc flags `ACC_FINAL` của class/method. Có thể đường dẫn class output thay đổi giữa Gradle/Kotlin plugin versions; đây là lệnh **chẩn đoán** không phải điều kiện bắt buộc của demo. Với `FinalJobService`, dự kiến class có `ACC_FINAL`; với `OpenJobService` được plugin mở đúng, class không có `ACC_FINAL` tương ứng.

### 9.2. Xem Spring proxy identity

~~~kotlin
val bean = ctx.getBean("finalJobService")
println(AopUtils.isAopProxy(bean))
println(AopUtils.isJdkDynamicProxy(bean))
println(AopUtils.isCglibProxy(bean))
println(AopUtils.getTargetClass(bean).name)
~~~

Snippet cần `ctx` và import `org.springframework.aop.support.AopUtils`. Đây là diagnostics **trên bean thực tế**: `getBean("finalJobService")` là JDK proxy ở demo chính; `getTargetClass` trả type target. **Không ép** JDK proxy sang `FinalJobService`.

### 9.3. Xem advisor chain, không chỉ proxy type

~~~kotlin
val bean = ctx.getBean("openJobService")
if (bean is org.springframework.aop.framework.Advised) {
    bean.advisors.forEachIndexed { index, advisor ->
        println("advisor[" + index + "]=" + advisor.javaClass.name)
    }
}
~~~

Snippet cần Spring context và target bean thực. Một AOP proxy có thể chứa nhiều advisors, nhưng không phải mọi advisor đều match mọi method. Chỉ dựa vào `isAopProxy=true` không chứng minh `process` được advice; ví dụ chính còn assert sự kiện `advice:process`.

### 9.4. Test hành vi thực thay vì chỉ kiểm tra annotation

Đối với Spring AOP, một regression test nên kiểm tra tối thiểu:

1. Bean được Spring quản lý, caller lấy đúng proxy từ context.
2. Proxy strategy phù hợp: JDK hoặc CGLIB.
3. Kotlin target class/method cần subclass đã được compiler mở.
4. Pointcut match method dương tính và không match một method âm tính.
5. `@Around` gọi `proceed()`, giữ result/exception đúng.
6. Gọi raw `new` hoặc self-invocation không bị nhầm là proxy invocation.
7. Cấu hình nhiều modules không bỏ mất plugin ở subproject chứa Kotlin source.
8. Không có một auto-proxy creator/Boot property khác ép class-based proxy trái với giả định.
9. Với `@Transactional` hoặc `@Cacheable`, assert database/cache state thay vì chỉ assert `isAopProxy`.
10. Với lỗi runtime, log thêm Spring bean name, target type và `AopUtils.isJdkDynamicProxy`/ `isCglibProxy` để xác định hướng sửa.

## 10. Ma trận lỗi thường gặp

| Triệu chứng | Nguyên nhân hay gặp | Hướng giải quyết |
| --- | --- | --- |
| Spring báo không subclass được Kotlin service | Class final và Spring đang dùng CGLIB | Bật `kotlin-spring`, dùng `open` class/method hoặc JDK interface proxy |
| Có interface nhưng vẫn lỗi final class | Cấu hình `proxyTargetClass=true` ép CGLIB | Xem Boot/property/auto-proxy settings; không giả định có interface là đủ |
| Bean proxy tạo thành công nhưng advice không chạy ở một method | Method final, pointcut không match hoặc self-invocation | Kiểm tra `Modifier.isFinal`, advisor, `proceed()`, caller path |
| `@Service` đã gắn nhưng class vẫn final | Compiler plugin thiếu/không áp dụng ở module source | Kiểm tra `kotlin("plugin.spring")`, Gradle tasks, javap |
| `@Bean` tạo Kotlin class vẫn final | Return type không tự được mở bởi compiler vì được trả từ method `@Bean` | Annotate target type phù hợp hoặc `open` tường minh |
| `@ComponentScan` không thấy bean | Annotation chỉ ảnh hưởng bytecode, Spring scanning vẫn cần đúng package | Đăng ký bean bằng scan/`@Bean` rõ |
| `@Transactional` bị bỏ qua khi `this.save()` | Self-invocation bypass proxy | Tách bean/gọi qua Spring-injected proxy |
| JDK proxy không cast được sang class implementation | JDK proxy chỉ implement interface | Inject theo business interface |
| Kotlin property getter không được advice | Final getter/metadata/visibility không phù hợp | Dùng type-level stereotype và kiểm tra JVM method flags |
| Custom annotation `@MyAspectTarget` không làm class mở | Annotation không thuộc kotlin-spring preset và không meta-annotated | Cấu hình all-open annotation riêng hoặc thêm open |
| Một target không phải Spring bean | Caller dùng `new`, không qua container | Để Spring quản lý target hoặc thiết kế factory injection |
| Bean có Spring AOP proxy nhưng annotation không có hiệu lực | Infrastructure của transaction/cache/async chưa bật hoặc pointcut không match | Xem advisor registrations và test behavior thực |
| `final` service không có interface nhưng vẫn muốn JDK proxy | JDK proxy yêu cầu interface contract | Thiết kế interface hoặc mở class để class proxy |
| Spring Boot chạy khác test Spring context thuần | Proxy defaults/configuration khác | Test đúng cấu hình thực tế, inspect `AopUtils` |
| Build Kotlin plugin bật ở web module nhưng service jar vẫn final | Plugin compile ở module không chứa source target | Bật plugin ở module sở hữu target source |
| Attempt fix bằng AspectJ LTW mà không hiểu proxy | Weaving là cơ chế khác, phức tạp hơn | Ưu tiên chọn đúng JDK/CGLIB và Kotlin compiler plugin cho Spring AOP |

## 11. Tối ưu hiệu năng và bảo trì khi ứng dụng có Kotlin

### 11.1. Không đổi toàn ứng dụng sang CGLIB chỉ để tránh interface

JDK proxy và CGLIB đều có chi phí runtime, nhưng chênh lệch performance thường không phải lý do chính để chọn. Với Kotlin, lựa chọn quan trọng là **API contract và khả năng proxy**:

- Dùng interface-based proxies khi có business interfaces rõ và consumer nên phụ thuộc abstraction.
- Dùng class-based proxies khi cần concrete type hoặc class không có interface; đảm bảo class/method có thể subclass/override.
- Tránh ép `proxyTargetClass=true` toàn context nếu phần lớn Kotlin implementation classes cố ý final và đang hoạt động qua interfaces.
- Không mở mọi class một cách vô điều kiện chỉ vì app dùng Spring: compiler plugin cho Spring stereotypes hoặc một list custom annotations có giới hạn sẽ dễ bảo trì hơn.

### 11.2. Đặt compiler plugin tại đúng module

Trong Gradle multi-project, một convention plugin có thể áp dụng `org.jetbrains.kotlin.plugin.spring` cho **mọi module chứa Spring component classes**, nhưng không phải module domain thuần. Một mô hình:

~~~text
:domain            -> Kotlin types không phụ thuộc Spring; mặc định final
:application        -> Spring services, kotlin-spring plugin
:web                -> Spring controllers, kotlin-spring plugin
:integration-tests  -> JUnit/Spring Test; kiểm tra proxy và advisor behavior
~~~

Đây là kiến trúc minh họa, không phải yêu cầu bắt buộc. Nó hạn chế leak Spring stereotypes vào domain layer và tránh mở class cuối cùng mà không cần proxy. Nếu domain services cũng là Spring beans, hãy xem tính phụ thuộc Spring và lựa chọn build convention phù hợp.

### 11.3. Dựa vào tests để phát hiện mất plugin

Test `Modifier.isFinal` là một guard hữu ích: khi nâng cấp Kotlin compiler plugin, đổi Gradle plugin management hoặc chuyển source file sang module khác, test sẽ phát hiện class/method không còn mở đúng ý định. Nhưng **reflection check chỉ là điều kiện kỹ thuật**. Một integration test gọi qua proxy và assert advice vẫn cần thiết để bắt thiếu `@Aspect` bean, pointcut không match hoặc self-invocation.

### 11.4. Hạn chế tạo aspects quá rộng

Một pointcut như `execution(* demo..*(..))` không tự khiến mọi class được proxy an toàn. Nó có thể làm auto-proxy creator cố tạo CGLIB proxy trên Kotlin final classes **không có interface**, gây application startup failure. Chọn pointcut rõ package/type/method/annotation và kiểm tra cả negative coverage.

Nếu điểm cắt được mở rộng khi có module Kotlin mới, hãy test startup `ApplicationContext` với đúng proxy strategy/bean set. Tình huống performance và maintenance đáng quan tâm nhất không chỉ là overhead per-method, mà còn là **khả năng các class mới bất ngờ trở thành AOP candidates**.

## 12. Checklist code review

- [ ] Có xác định target là Kotlin `final` class hay `open` class?
- [ ] Method cần advice có `final` flag trên JVM hay có thể override?
- [ ] Target có business interface không và JDK proxy có expose method cần gọi?
- [ ] Có nơi nào ép `proxyTargetClass=true` hoặc Boot dùng class-proxy default?
- [ ] `kotlin("plugin.spring")` được áp dụng **tại module sở hữu Kotlin source**?
- [ ] Target có `@Component`/`@Service` hoặc một stereotype meta-annotated phù hợp?
- [ ] Nếu dùng `@Bean`, return type target được mở hay có interface proxy?
- [ ] Spring đã đăng ký target bean và aspect bean trong đúng ApplicationContext?
- [ ] `@EnableAspectJAutoProxy` hoặc infrastructure khác đã bật?
- [ ] Lời gọi thực đi qua Spring proxy, không phải `new`/self-invocation?
- [ ] Pointcut match method runtime và advisor advice thực sự chạy?
- [ ] Proxy JDK có được inject/cast theo interface đúng cách?
- [ ] Spring CGLIB có gặp class/method final, visibility hoặc module boundary?
- [ ] Có test positive và negative coverage cho Spring AOP advice?
- [ ] Có tránh mở không cần thiết các domain classes và chỉ bật plugin ở modules phù hợp?
- [ ] Khi nâng cấp Kotlin/Spring/Boot, có chạy proxy regression tests và phản chiếu `Modifier.isFinal`?
- [ ] Nếu có `@Transactional`/cache/async, test behavior thật chứ không chỉ proxy identity?

## 13. Kết luận

Kotlin `final` và Spring AOP **không mâu thuẫn tuyệt đối**. Một Kotlin final class vẫn có thể là **target của JDK interface proxy**; class-based Spring CGLIB proxy lại cần class subclassable và method overrideable. `kotlin-spring` compiler plugin tự động mở các Spring stereotype classes và members phù hợp ở **thời điểm compile**, giúp Spring AOP class proxy hoạt động mà không thêm `open` khắp source.

Muốn chẩn đoán chính xác, đi theo thứ tự **bean → proxy strategy → target JVM modifiers → pointcut/advisor → advice → method thật**. Dùng `AopUtils` để quan sát proxy, `Modifier.isFinal` hoặc `javap` để kiểm tra bytecode, và integration tests để xác nhận method advice chạy. Không dùng `proxyTargetClass=true` hoặc all-open như cách sửa chung cho self-invocation, thiếu bean hay pointcut sai.

### Tài liệu tham khảo chính thức

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html).
- [Spring Framework — Spring Projects in Kotlin](https://docs.spring.io/spring-framework/reference/6.2/languages/kotlin/spring-projects-in.html).
- [Kotlin — All-open Compiler Plugin](https://kotlinlang.org/docs/all-open-plugin.html).
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html).
- [Spring Framework — AspectJ Pointcut Expressions](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html).
- [Spring Framework — Spring AOP ProxyFactory API](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/prog.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-performance-maintenance-011">Tiêu chí loại bỏ aspect khi cơ chế trực tiếp dễ kiểm soát hơn</a>

<details>
<summary>Xem chi tiết</summary>

# Tiêu chí loại bỏ aspect khi cơ chế trực tiếp dễ kiểm soát hơn trong Spring

## 1. Khi nào một custom aspect nên nhường chỗ cho cơ chế tường minh?

Custom Spring `@Aspect` hữu ích khi một policy thực sự lặp lại ở nhiều Spring beans với pointcut ổn định. Nhưng với một vài use case cụ thể, việc kích hoạt **ẩn qua proxy và advisor ordering** đôi khi gây khó bảo trì hơn một lời gọi service/decorator hoặc extension point Spring chuyên dụng.

Bài này đánh giá một quyết định **khác với việc chỉ tách aspect theo trách nhiệm**: có nên loại bỏ lớp interception tùy biến nhưng **vẫn giữ nguyên policy nghiệp vụ**? Cần chứng minh phạm vi method/entry point được bảo vệ, thời điểm hành vi xảy ra (trước/sau transaction commit), exception và async semantics, các ca self-invocation, rồi chọn nơi triển khai và regression test tương đương. Không kết luận chỉ từ benchmark proxy hoặc số dòng code giảm; mục 3–12 cung cấp ma trận lựa chọn và kế hoạch migration.

## 2. Thực chất phải loại bỏ điều gì?

Trong Spring, có thể có ba đối tượng hoàn toàn khác nhau:

1. **Custom aspect** của ứng dụng: một bean `@Aspect` tự viết, ví dụ gửi audit event trước mọi lời gọi `OrderService.submit(..)`.
2. **Advisor/interceptor hạ tầng của Spring:** transaction, cache, async, method security hoặc cơ chế tương ứng được Spring quản lý.
3. **Cross-cutting policy**: quy tắc nghiệp vụ hoặc hạ tầng muốn bảo vệ, ví dụ phát đúng một audit event khi tạo đơn hàng thành công.

Khi nói “loại bỏ aspect”, thông thường ta muốn **bỏ lớp custom `@Aspect` không còn là cách triển khai phù hợp**, nhưng phải giữ đúng business policy. Không mặc định gỡ `@EnableAspectJAutoProxy` hoặc toàn bộ Spring AOP vì những advisor khác có thể còn cần thiết.

Ví dụ:

```text
Trước:
Controller → Spring proxy(OrderService)
              → CustomAuditAspect
                  → OrderService.submit()
              → Spring transaction advisor

Sau (khi audit thuộc đúng một use case):
Controller → OrderApplicationService.submit()
              → OrderDomainService.submit()
              → AuditPublisher.publish(...)
              → transaction/other infrastructure (nếu cần)
```

Sơ đồ “sau” chỉ là một khả năng. Nó chưa chứng minh thời điểm audit đã được giữ nguyên: sự kiện phát ngay trong transaction khác với sự kiện phát sau commit. **Giữ đúng ý định policy và thời điểm thực hiện** quan trọng hơn việc giữ cùng số dòng code.

## 3. Những dấu hiệu nên cân nhắc bỏ custom aspect

| Dấu hiệu quan sát được | Vì sao là vấn đề trong Spring AOP | Hướng cân nhắc |
| --- | --- | --- |
| Pointcut phải liệt kê vài method rất cụ thể | Quan hệ use case–policy đã hẹp và có thể được thể hiện rõ ở service | Gọi trực tiếp collaborator hoặc decorator |
| Hành vi chỉ phục vụ HTTP request/response | Method-level pointcut không có đủ ngữ cảnh HTTP, bị gọi ngoài HTTP vẫn kích hoạt | Servlet `Filter`, Spring MVC `HandlerInterceptor`, Reactive `WebFilter` tùy stack |
| Policy chỉ đúng sau transaction commit | `@AfterReturning` trong aspect không đảm bảo transaction đã commit | Transaction synchronization hoặc `@TransactionalEventListener(AFTER_COMMIT)` |
| Advice đọc nhiều metadata/arguments để suy đoán nghiệp vụ | Ý định nghiệp vụ không thể hiện ở interface và target method | API tường minh nhận dữ liệu/command cần thiết |
| Một method được gọi qua `this.method()` | Self-invocation bỏ qua Spring proxy advice ở method gọi nội bộ | Tách service/bean boundary hoặc gọi collaborator trực tiếp |
| Debug đòi hỏi lần theo nhiều `@Order`/advisor | Thứ tự thực thi và exception handling khó nhận diện | Tách workflow có thứ tự explicit |
| Audit/metric chỉ cần quanh một thao tác có đầu ra rõ | Generic method advice quá rộng so với mục đích | Decorator hoặc wrapper có tên cụ thể |
| Có nguy cơ policy chạy lặp do nhiều aspect | Nhiều nguồn cùng sở hữu một trách nhiệm | Chỉ định một owner và loại đường trùng |
| Test phải mock `ProceedingJoinPoint` để kiểm tra business rule | Rule bị buộc vào API AOP dù có thể hoạt động độc lập | Tách rule/publisher thành service thuần Java |
| Cần xác định lỗi tại từng bước workflow | Hidden interception khiến call graph khó đọc | Application service điều phối các bước trực tiếp |

**Không phải tín hiệu đủ để xóa:** có proxy class trong stack trace, dùng `@Aspect` hoặc một phép benchmark nhỏ cho thấy vài microsecond overhead. Mọi thay đổi phải dựa trên phạm vi, semantics và lợi ích bảo trì có thể quan sát được.

### 3.1. Trường hợp vẫn nên giữ Spring aspect

Giữ một custom aspect khi hành vi thực sự **nhất quán trên nhiều Spring beans/methods**, pointcut có thể diễn đạt rõ và ổn định, thứ tự được định nghĩa, và việc tách policy làm giảm lặp code một cách có ý nghĩa. Ví dụ tracing nội bộ theo một annotation ổn định trên nhiều service có thể phù hợp với advice; cũng có thể phù hợp với Spring Observability instrumentation nếu mục tiêu chỉ là telemetry.

Có thể chọn **thu hẹp aspect** thay vì xóa: bỏ những pointcut quá rộng, tách policy khác nhau, giới hạn `@annotation` rõ, và giữ các advisors hạ tầng hiện hữu. Quyết định “giữ” cũng cần có integration test xác nhận lời gọi qua proxy và đúng pointcut.

## 4. Chọn đúng cơ chế thay thế trong Spring

Bản chất câu hỏi là: **hành vi thuộc lớp nào của ứng dụng?** Chọn extension point của Spring tại lớp đó trước khi tự viết một interception layer nữa.

| Trách nhiệm | Custom aspect dễ bị lạm dụng | Cơ chế có thể dễ kiểm soát hơn |
| --- | --- | --- |
| Một bước bắt buộc trong use case | `@Before` tự tìm order ID để audit | `OrderApplicationService` gọi `AuditPublisher` trực tiếp |
| Bao quanh một interface với hành vi chung | `@Around` chỉ match vài implementation | Decorator triển khai cùng interface và delegate |
| Chặn/ghi nhận request HTTP trên Servlet | Pointcut lên mọi `@Controller`/service | `Filter` hoặc `HandlerInterceptor` |
| Chặn/ghi nhận request WebFlux | Advice lên controller/reactive return values | `WebFilter` hoặc WebFlux instrumentation thích hợp |
| Xử lý lỗi HTTP | `@AfterThrowing` chuyển exception thành response | `@ControllerAdvice`/`@ExceptionHandler` |
| Validation dữ liệu request | Advice tự inspect arguments rồi ném lỗi | Bean Validation và cơ chế validation của Spring |
| Quyền truy cập method | Aspect custom với logic role/permission | Spring Security method authorization |
| Transaction boundary của một đoạn code rõ | Aspect custom mở/đóng transaction theo tên method | `@Transactional` hoặc `TransactionTemplate` khi cần programmatic demarcation |
| Cache kết quả method | Custom `@Around` tự lưu map | `@Cacheable` hoặc `CacheManager` được quản lý |
| Async execution | Aspect tự dispatch vào executor | `@Async`/`TaskExecutor` hoặc workflow async explicit |
| Công việc sau commit | `@AfterReturning` tự gửi event | `@TransactionalEventListener(phase = AFTER_COMMIT)` với điều kiện transaction phù hợp |
| Metrics/tracing/observation | Advice tự đo/log mọi nơi | Micrometer Observation và cơ chế instrumentation của Spring/stack sử dụng |

**Chú ý:** `@Transactional`, `@Cacheable` và `@Async` ở proxy mode bản thân vẫn có thể dùng Spring advisors. Lựa chọn này **giảm custom advice**, không đồng nghĩa không dùng AOP hoặc xóa mọi proxy. Nếu mục tiêu là hiển thị thứ tự nghiệp vụ, dùng application service/decorator; nếu mục tiêu là chuẩn hóa hạ tầng, ưu tiên khả năng chuyên dụng đã có của Spring.

### 4.1. Decorator thích hợp khi nào?

Decorator hợp lý khi nhiều implementation của **cùng một interface** cần bổ sung behavior và caller vẫn dùng interface cũ. Decorator gọi delegate tường minh, có thể inject như một Spring bean, có thể unit test thuần Java. Hạn chế: nó phải giữ đầy đủ hợp đồng interface; nếu có nhiều method, có thể thêm boilerplate và cần chú ý giữ semantic return, exception, cancellation.

### 4.2. Lời gọi trong application service thích hợp khi nào?

Khi audit hoặc publish event thuộc **một use case cụ thể** và cần order ID, user, workflow phase, kết quả từ nhiều collaborator, việc gọi trực tiếp mô tả trách nhiệm rõ hơn một pointcut match theo tên method. Hãy cân nhắc transaction boundary và lỗi của publisher: “gọi audit trước commit” và “đảm bảo event chỉ có khi commit thành công” không tương đương.

### 4.3. HTTP filter/interceptor không thay thế tùy tiện mọi service aspect

`HandlerInterceptor` của Spring MVC nhìn thấy lifecycle xử lý HTTP ở MVC; nó **không nhìn thấy tất cả lời gọi service** từ scheduled task, messaging consumer hay async worker. Một `Filter` hoạt động ở Servlet boundary; với WebFlux dùng `WebFilter`. Chuyển một audit policy phải bao phủ batch jobs từ method aspect sang HTTP interceptor có thể làm mất hoàn toàn các sự kiện ngoài HTTP. Hãy chọn scope theo nguồn truy cập thực tế.

## 5. Ví dụ chạy được: chuyển audit aspect thành decorator trực tiếp

Bài thử nghiệm so sánh **hai cấu hình Spring độc lập**:

- **Baseline:** Spring AOP proxy cùng custom `@Around` ghi `audit.begin`, `audit.success` hoặc `audit.failure`.
- **Thay thế:** một `AuditDecorator` triển khai cùng interface, ghi chính xác event tương đương rồi gọi delegate. Không cần bật Spring AOP trong cấu hình này.

Chúng ta assert **toàn bộ trace, return value và exception** để chứng minh hai phương án cung cấp cùng hợp đồng **trong phạm vi fixture**. Điều này hữu ích hơn việc chỉ nhìn số lời gọi target.

### 5.1. Cấu trúc

Sử dụng Java 17+, Spring Framework 6.2.8, Maven và JUnit Jupiter 5.11.4:

```text
spring-aspect-removal/
├── pom.xml
└── src/test/java/lab/RemovalDecisionTest.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lab</groupId>
    <artifactId>spring-aspect-removal</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring.version>6.2.8</spring.version>
        <junit.version>5.11.4</junit.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>${spring.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>${spring.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.22.1</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
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
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
        </plugins>
    </build>
</project>
```

Trong fixture này `aspectjweaver` giúp Spring xử lý syntax `@AspectJ`; **không có AspectJ load-time/compile-time weaving**. Cấu hình baseline dùng JDK proxy vì chỉ yêu cầu interface `CheckoutService`. Decorator trong cấu hình thay thế là Spring bean bình thường.

### 5.2. Một file kiểm thử đầy đủ

`src/test/java/lab/RemovalDecisionTest.java`:

```java
package lab;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.junit.jupiter.api.Assertions.*;

class RemovalDecisionTest {

    interface CheckoutService {
        String checkout(String orderCode);
    }

    static final class Trace {
        private final List<String> events = new CopyOnWriteArrayList<>();

        void record(String event) {
            events.add(event);
        }

        List<String> events() {
            return List.copyOf(events);
        }
    }

    static final class CoreCheckoutService implements CheckoutService {
        private final Trace trace;

        CoreCheckoutService(Trace trace) {
            this.trace = trace;
        }

        @Override
        public String checkout(String orderCode) {
            trace.record("target:" + orderCode);
            if ("ERR".equals(orderCode)) {
                throw new IllegalArgumentException(
                    "invalid:" + orderCode);
            }
            return "receipt:" + orderCode;
        }
    }

    @Aspect
    static class AuditAspect {
        private final Trace trace;

        AuditAspect(Trace trace) {
            this.trace = trace;
        }

        @Around("bean(checkoutService) && execution(* checkout(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            String code = (String) pjp.getArgs()[0];
            trace.record("audit.begin:" + code);
            try {
                Object result = pjp.proceed();
                trace.record("audit.success:" + code);
                return result;
            } catch (Throwable problem) {
                trace.record("audit.failure:" + code
                    + ":" + problem.getMessage());
                throw problem;
            }
        }
    }

    static final class AuditDecorator implements CheckoutService {
        private final CheckoutService delegate;
        private final Trace trace;

        AuditDecorator(CheckoutService delegate, Trace trace) {
            this.delegate = delegate;
            this.trace = trace;
        }

        @Override
        public String checkout(String orderCode) {
            trace.record("audit.begin:" + orderCode);
            try {
                String result = delegate.checkout(orderCode);
                trace.record("audit.success:" + orderCode);
                return result;
            } catch (RuntimeException problem) {
                trace.record("audit.failure:" + orderCode
                    + ":" + problem.getMessage());
                throw problem;
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class AspectConfig {
        @Bean
        Trace trace() {
            return new Trace();
        }

        @Bean
        CheckoutService checkoutService(Trace trace) {
            return new CoreCheckoutService(trace);
        }

        @Bean
        AuditAspect auditAspect(Trace trace) {
            return new AuditAspect(trace);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class DirectConfig {
        @Bean
        Trace trace() {
            return new Trace();
        }

        @Bean
        CheckoutService checkoutService(Trace trace) {
            return new AuditDecorator(
                new CoreCheckoutService(trace), trace);
        }
    }

    private record Observation(
        boolean springProxy,
        String result,
        Class<? extends Throwable> exceptionType,
        String exceptionMessage,
        List<String> events) {}

    private static Observation exercise(
            Class<?> configClass,
            boolean expectedProxy) {
        try (var context =
                 new AnnotationConfigApplicationContext(configClass)) {
            CheckoutService bean =
                context.getBean(CheckoutService.class);
            Trace trace = context.getBean(Trace.class);

            boolean isProxy = AopUtils.isAopProxy(bean);
            assertEquals(expectedProxy, isProxy);

            String result = bean.checkout("A-1");
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> bean.checkout("ERR")
            );

            return new Observation(
                isProxy,
                result,
                exception.getClass(),
                exception.getMessage(),
                trace.events()
            );
        }
    }

    @Test
    void direct_decorator_preserves_the_observable_audit_contract() {
        Observation viaAspect =
            exercise(AspectConfig.class, true);
        Observation viaDecorator =
            exercise(DirectConfig.class, false);

        List<String> expectedEvents = List.of(
            "audit.begin:A-1",
            "target:A-1",
            "audit.success:A-1",
            "audit.begin:ERR",
            "target:ERR",
            "audit.failure:ERR:invalid:ERR"
        );

        assertEquals("receipt:A-1", viaAspect.result());
        assertEquals(IllegalArgumentException.class,
            viaAspect.exceptionType());
        assertEquals("invalid:ERR", viaAspect.exceptionMessage());
        assertEquals(expectedEvents, viaAspect.events());

        assertEquals(viaAspect.result(), viaDecorator.result());
        assertEquals(viaAspect.exceptionType(),
            viaDecorator.exceptionType());
        assertEquals(viaAspect.exceptionMessage(),
            viaDecorator.exceptionMessage());
        assertEquals(viaAspect.events(), viaDecorator.events());
    }
}
```

Hai cấu hình độc lập tạo service được gọi bằng cùng interface. Trong baseline, Spring tạo proxy vì `AuditAspect` match bean tên `checkoutService`. Trong cấu hình trực tiếp, `checkoutService` là `AuditDecorator` thực sự; caller không cần biết delegate là gì, nhưng người đọc code có thể nhìn trực tiếp thứ tự audit và `delegate.checkout()`.

**Giới hạn của phép thử:** ví dụ chỉ dùng một method đồng bộ, return `String`, exception kiểu `IllegalArgumentException`. Trong `AuditAspect`, `catch (Throwable)` ghi cả lỗi nghiêm trọng và checked exception nếu có; decorator ở fixture bắt `RuntimeException` vì hợp đồng interface không khai báo checked exception. Không suy diễn hai kiểu wrapper luôn tương đương cho mọi `Throwable`. Nếu API có checked exception, `Future`, reactive `Mono/Flux`, cancellation, retry hoặc transaction, phải thêm test và chính sách xử lý tương ứng.

### 5.3. Chạy và diễn giải

Trong thư mục project:

```bash
mvn test
```

Kỳ vọng test `direct_decorator_preserves_the_observable_audit_contract` PASS khi hai đường triển khai cho cùng hành vi đã chọn. Expected event list được assert trong code:

```text
audit.begin:A-1
target:A-1
audit.success:A-1
audit.begin:ERR
target:ERR
audit.failure:ERR:invalid:ERR
```

Đây là **trace logic trong bộ nhớ**, không phải stdout thực tế của Maven. JUnit/Surefire sẽ in báo cáo test theo môi trường. Không cố định tên class của proxy hoặc index advisor nội bộ; chỉ xác nhận `AopUtils.isAopProxy` trong cấu hình có aspect và không có proxy AOP trong cấu hình decorator.

Sự tương đương quan sát được gồm: kết quả `"receipt:A-1"`, cùng exception type/message, đúng một `audit.begin` và một kết quả audit cho mỗi lời gọi, target được gọi đúng một lần. Đây là **bằng chứng cho fixture**, không phải giấy phép gỡ một aspect production đang match hàng chục service chưa được kiểm thử.

## 6. Những gì test tương đương cần bao phủ trước khi xóa aspect thật

### 6.1. Lời gọi nào được bảo vệ?

Hãy liệt kê **tập method/bean mà pointcut thực sự có thể match** trong Spring context. Với một pointcut rộng như:

```java
@Around("within(com.example.application..*) && @annotation(Audited)")
```

cần tìm toàn bộ entry point đáp ứng annotation và loại proxy phù hợp. Nếu chỉ thay bằng lời gọi trực tiếp ở một controller, scheduled job, message consumer hoặc lời gọi từ service khác có thể mất hành vi. Test cả HTTP và non-HTTP entry points khi policy có phạm vi như vậy.

Nếu aspect match nhiều bean, phương án có thể là một decorator/facade chung được inject ở tất cả nơi sử dụng, hoặc giữ aspect nhưng thu hẹp pointcut. Quyết định theo **phạm vi tiêu thụ thật**, không theo số lượng file Java.

### 6.2. Thứ tự so với transaction

`@AfterReturning` của custom Spring aspect không mặc nhiên có nghĩa transaction đã commit. Thứ tự `@Order` tương đối với `TransactionInterceptor` quyết định custom advice chạy bên trong hay bên ngoài transaction boundary.

Ví dụ policy “chỉ gửi audit sau commit thành công” thường cần `@TransactionalEventListener(phase = AFTER_COMMIT)` hoặc `TransactionSynchronization` phù hợp, không chỉ `publisher.publish()` ngay sau repository `save()`. Khi chuyển, phải quyết định event nên phát **trong transaction**, **sau commit** hay theo outbox pattern, rồi test commit và rollback riêng.

Với `@TransactionalEventListener`, khi **không có transaction đang active** listener mặc định không chạy (trừ khi `fallbackExecution` được cấu hình phù hợp). Đó là một thay đổi contract phải cân nhắc nếu lời gọi cũ còn có đường không transactional.

### 6.3. Thứ tự so với async

Nếu custom aspect ghi audit trước `@Async`, event có thể ở caller thread; nếu advice nằm phía worker, thời điểm khác. Chuyển sang direct collaborator trong target `@Async` thay đổi nơi thực thi và thời điểm exception xuất hiện. Dùng `Future`/`CompletableFuture`, correlation ID và executor test để kiểm tra hoàn thành, lỗi, cancellation, timeout, không chỉ đếm method calls.

### 6.4. Thứ tự so với cache

Một custom aspect đứng **ngoài** cache advisor có thể chạy trên cả cache hit và miss, trong khi đặt audit trong target chỉ chạy khi cache miss. Nếu audit cần ghi nhận **mỗi request**, chuyển nó vào target sẽ đánh mất event hit. Nếu metric đếm **số lần tính toán**, việc chạy target ít hơn có thể chính là mục tiêu. Test cả key hit/miss và `@Order` của advisors trước khi chọn vị trí mới.

### 6.5. Trả về, exception và retry

Một `@Around` có thể sửa return value, swallow/replace exception, gọi `proceed()` không lần nào hoặc nhiều lần. Khi bỏ aspect, behavior này có thể là phần hợp đồng thật dù không được document. Contract test cần bao gồm success, failure, null, validation error, retry/short-circuit và loại exception đặc trưng nếu chúng được sử dụng.

Không nên vô tình biến exception thành success chỉ vì decorator dùng `try/finally` hoặc một event publisher tự ném exception. Nếu audit failure không được phép chặn business success, cần định nghĩa chính sách rõ và kiểm thử độc lập.

## 7. Ra quyết định bằng một ma trận kiểm chứng

Một quyết định có căn cứ nên ghi câu trả lời cho từng hàng:

| Tiêu chí | Giữ custom aspect có lợi khi | Cơ chế trực tiếp có lợi khi |
| --- | --- | --- |
| Số lượng nơi áp dụng | Nhiều bean cùng một concern, policy thống nhất | Một số use case rõ, không cần generic matcher |
| Độ ổn định phạm vi | Annotation/pointcut bền vững qua refactor | Mỗi lần đổi package/method phải cập nhật pointcut |
| Dependency của policy | Chỉ cần method/args/annotation và context chung | Cần dữ liệu nghiệp vụ nhiều bước hoặc kết quả trung gian |
| Visibility | Team hiểu rõ Spring advice và test proxy | Call flow trực tiếp giúp review/debug nhanh hơn |
| Advisor interactions | Ít phụ thuộc order, transaction/cache/async đã được test | Thứ tự advisor gây lỗi khó giải thích |
| Cơ chế chuẩn Spring | Không có API chuyên dụng phù hợp | Filter/interceptor/event/validation/security/caching đã hỗ trợ đúng scope |
| Chi phí runtime | Overhead đo được chấp nhận | Scanning reflection/logging dư thừa là hot path đáng kể |
| Vòng đời và lỗi | Central aspect giữ rule không lặp | Cần retry, result/exception semantics explicit theo use case |
| Test | Integration test bao phủ pointcut đầy đủ | Unit + contract test xác nhận policy tại điểm gọi |
| Migration risk | Thay đổi sẽ gây mất behavior ở nhiều nơi | Tập caller hữu hạn, có thể chuyển có kiểm soát |

**Không gán điểm máy móc** từ số lượng hàng; một yêu cầu bắt buộc về transactional audit hoặc authorization có thể quan trọng hơn mọi lợi ích tiết kiệm code. Chọn giải pháp theo invariant có thể chứng minh.

### Ví dụ quyết định

- **Nên thay:** audit cho đúng một `SubmitOrderUseCase` cần order state, khách hàng, payment confirmation và publish sau commit. Một application service kết hợp transaction/event boundary cụ thể thường dễ theo dõi hơn aspect nhận `JoinPoint.getArgs()` rồi tự đoán workflow.
- **Có thể giữ:** logging latency cho hàng trăm methods được đánh dấu `@Measured`, semantics đo rõ, không ảnh hưởng return/exception, aspect có integration tests; nếu đã có Spring Observation instrumentation phù hợp, cân nhắc dùng instrumentation chuẩn.
- **Nên đổi cơ chế Spring:** correlation ID của HTTP request. Servlet filter hoặc WebFlux web filter ở đúng tầng request thường rõ ràng hơn method aspect scan tất cả controller/service.
- **Cần xem xét kỹ:** method security. Thay advice tùy chỉnh bằng lệnh `if` rải rác có thể làm mất centralized authorization; Spring Security method authorization hoặc request rules thường là cơ chế có thể kiểm soát và kiểm thử tốt hơn.

## 8. Quy trình migration an toàn

### Bước 1 — Ghi lại hợp đồng hiện tại

Trước khi sửa aspect, xác định **đúng bean/proxy**, pointcut, danh sách method khớp, `@Order`, dependency on transaction/cache/async, tình huống self-invocation và exception flow.

Một mẫu review:

```text
Policy: Audit khi hoàn thành checkout
Spring aspect: CheckoutAuditAspect
Pointcut: bean(checkoutService) && execution(* checkout(..))
Business entry points: Controller, scheduled job, message consumer?
Spring advisors liên quan: transaction, cache, async?
Bằng chứng: trace, event count, output, exception, commit/rollback
Owner mới: CheckoutApplicationService hoặc AuditDecorator
Tiêu chí giữ nguyên: 1 audit/checkout đúng phase, target gọi đúng số lần
```

Không kết luận chỉ từ `@Aspect` source vì Spring proxy còn phụ thuộc context và bean thực.

### Bước 2 — Tạo contract test cho baseline

Xây test gọi **Spring bean từ container** để AOP thật chạy; log event name ổn định, assert output/exception và policy side effects. Với aspect match nhiều method, tạo ít nhất một test cho từng nhóm rule/pointcut. Có thể dùng test cấu hình Spring Boot thực khi có auto-configuration liên quan.

### Bước 3 — Đặt implementation thay thế

Tạo direct collaborator/decorator/handler phù hợp, định nghĩa rõ ownership và trách nhiệm. Tránh copy nguyên reflection code hoặc giả lập `ProceedingJoinPoint` vào service mới; thay bằng typed input/command. Bản thân business policy nên có method dễ gọi và unit test.

### Bước 4 — So sánh hai đường độc lập

Test baseline qua Spring AOP và test implementation trực tiếp trong **hai context độc lập** như fixture. Kiểm tra các ranh giới success/exception/rollback/async/cache tùy cần. Nếu feature flag được sử dụng để rollout, bảo đảm hai đường **không cùng kích hoạt policy** trong một request.

### Bước 5 — Tắt đúng nguồn duplicate

Khi đường mới đủ coverage, bỏ hoặc thu hẹp **đúng advice/pointcut/bean aspect** cũ. Đừng vô tình vô hiệu hóa tất cả auto-proxying: hạ tầng `@Transactional`, `@Cacheable`, `@Async` hoặc các aspect khác vẫn có thể cần. Kiểm tra không còn double audit/double validation khi cả bean config cũ và mới cùng được scan.

### Bước 6 — Xác minh production-like

Chạy integration tests cho mọi caller quan trọng, cả happy path và lỗi, đặc biệt transaction commit/rollback, async completion, cache hit/miss nếu có. Quan sát event count theo business key và correlation id sau triển khai. Thay đổi chỉ được xem là hoàn tất khi không mất call path hoặc sinh side effect trùng.

### Bước 7 — Dọn code/config không còn dùng

Chỉ khi hợp đồng đã được đảm bảo mới xóa aspect class/bean, pointcut tests hoặc tài liệu hướng dẫn cũ không còn liên quan. Nếu còn advisors khác, giữ đúng infrastructure. Điều này giảm confusion: người đọc sẽ thấy một nguồn duy nhất sở hữu policy.

## 9. Kiểm thử thêm các trường hợp dễ gây hồi quy

| Ca kiểm thử | Vì sao cần | Assertion quan trọng |
| --- | --- | --- |
| HTTP caller và scheduled/background caller | Chuyển policy sang HTTP-only mechanism có thể bỏ sót background | Mỗi entry point cần audit vẫn tạo đúng event |
| Method gọi qua Spring proxy vs `this.method()` | Proxy mode có thể bỏ qua self-invocation | Hành vi mới có đúng phạm vi mong muốn |
| Service trả về bình thường | Xác nhận result và side effect | Return value không đổi, audit một lần |
| Service ném exception | `@AfterThrowing`/`@Around` có nhánh khác | Giữ exception type, propagation và event failure |
| Transaction rollback | Audit có thể đã phát dù transaction không commit | Không phát `AFTER_COMMIT` trên rollback |
| Cache hit | Target không chạy ở hit | Audit/metric có hoặc không đúng như yêu cầu |
| Async worker thất bại | Sự kiện và exception có thể ở thread khác | Future/latch completed, lỗi và event đúng |
| Concurrent calls | Publisher/decorator có thread safety không | Không trộn business keys giữa requests |
| Dependency/bean scanning | Hai policy implementations cùng có thể được đăng ký | Mỗi request chỉ một owner thực thi |
| Upgrade Spring version | Auto-proxy/advisor semantics có thể thay đổi | Integration contract tests tiếp tục PASS |

Khi test đồng thời, tránh dựa vào thứ tự log toàn cục. Ghép sự kiện theo correlation ID hoặc business key, xác nhận thứ tự trong từng invocation. Nếu policy cần idempotency qua retry hoặc distributed delivery, kiểm thử bổ sung tầng nghiệp vụ; chỉ loại bỏ aspect không tự giải quyết duplicate do network retries.

## 10. Lỗi chuyển đổi thường gặp

1. **Xóa aspect trước khi liệt kê những method nó match:** làm mất audit của caller ngoài HTTP hoặc một implementation ít được gọi.
2. **Nhầm `@AfterReturning` với “sau commit”:** có thể gửi event trước transaction commit; khi chuyển phải định nghĩa transaction phase.
3. **Thay bằng decorator nhưng inject delegate và decorator thành hai bean cùng interface không có `@Primary`/`@Qualifier`:** dễ gây ambiguous injection hoặc caller dùng nhầm bean. Trong fixture, chỉ có một `CheckoutService` bean được đăng ký cho mỗi context.
4. **Decorator gọi lại chính nó vì cấu hình bean sai:** tạo vòng lặp vô hạn hoặc stack overflow. Delegate phải là implementation thật, không phải proxy/decorator của chính nó.
5. **Giữ custom aspect và thêm lời gọi direct:** một policy có thể chạy hai lần. Rollout cần dừng nguồn cũ hoặc tách pointcut không giao nhau.
6. **Bỏ context Spring trong integration test:** unit test direct PASS nhưng không chứng minh transaction/cache/security advisor còn chạy.
7. **Dùng `@Order` để sửa trùng event:** order chỉ thay precedence, không loại trừ double invocation.
8. **Bỏ `@EnableAspectJAutoProxy` không xét các bean khác:** làm mất những Spring aspects khác; chú ý framework có thể bật auto-proxying qua cấu hình khác.
9. **Chuyển logging quá rộng thành synchronous remote call:** thêm latency/lỗi mạng vào đường business vốn trước đây không chờ network. Xác định failure policy và queue/outbox khi phù hợp.
10. **Khẳng định “direct nhanh hơn” mà không đo:** decorator cũng có overhead; tốc độ phụ thuộc business work, proxy chain và thao tác logging thực tế. Benchmark sau khi xác minh semantics.
11. **Gom các policy khác nhau vào một class trực tiếp khổng lồ:** làm giảm khả năng bảo trì. Tạo collaborator với tên trách nhiệm rõ và chỉ inject nơi cần.
12. **Bỏ test proxy sau migration:** nếu một số Spring advisors hạ tầng còn tồn tại, vẫn phải kiểm tra đúng `@Transactional`, `@Cacheable`, `@Async` trên bean mới.

## 11. Phân biệt quyết định kiến trúc với tối ưu hiệu năng

Giảm `@Aspect` chưa chắc có tác động đo được lên latency. Nếu aspect chỉ tạo một log line, việc network I/O của logger hoặc database trong target thường lớn hơn chi phí proxy. Nếu advice thực hiện reflection/metadata lookup, việc dùng cache theo method hoặc pointcut hẹp có thể giải quyết hiệu năng mà không cần migration.

Khi đánh giá tác động:

- Đo số invocation được advice match và số side effect thực tế trước/sau.
- Dùng profiler/JMH phù hợp để đo hot path dưới workload tương đương; phân biệt cold context startup và steady state.
- Quan sát p50/p95/p99 latency, allocations và CPU nếu thay đổi được đưa ra vì hiệu năng.
- So sánh số class/pointcut phải hiểu để review một nghiệp vụ, chi phí integration test và mức độ rõ ràng khi debug.
- Kiểm tra memory/classloader lifecycle nếu custom aspect cũ có static metadata cache; xóa cache dư thừa chỉ khi không còn được sử dụng.

**Một lựa chọn chấp nhận được có thể giữ nguyên performance nhưng cải thiện khả năng giải thích hành vi, kiểm thử và ownership.** Ngược lại, nếu aspect rõ ràng, phạm vi rộng và chi phí thấp, việc viết code direct ở hàng chục caller có thể khiến hệ thống khó bảo trì hơn.

## 12. Tiêu chí kết thúc công việc

Có thể quyết định loại bỏ một custom Spring aspect khi **đồng thời** đáp ứng:

1. Đã xác định đúng **policy** mà aspect thực hiện và mọi Spring bean/method/entry point quan trọng nó ảnh hưởng.
2. Đã chọn cơ chế thay thế đúng scope: explicit collaborator/decorator cho use case, hoặc Spring extension point chuyên dụng cho HTTP, transaction, cache, async, security, validation, event.
3. Có **contract tests** cho result, exception, event count, thứ tự và side effect — gồm transaction/async/cache nếu liên quan.
4. Đã xác minh Spring proxy/advisors hạ tầng vẫn hoạt động ở những bean cần chúng.
5. Không còn hai đường cùng sở hữu policy, không còn dependency injection mơ hồ và không mất caller path.
6. Lợi ích kiểm soát, bảo trì, khả năng quan sát hoặc hiệu năng có căn cứ đủ lớn so với chi phí chuyển đổi.

Nếu một trong các điều kiện trên chưa có bằng chứng, nên **thu hẹp hoặc cải thiện aspect hiện tại** trước khi quyết định xóa. Trong Spring, phương án tốt là phương án đặt behavior ở đúng boundary, có một owner rõ ràng, và cho phép kiểm chứng điều xảy ra khi lời gọi đi qua framework.

### Nguồn tham khảo

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): ranh giới proxy, JDK/CGLIB và self-invocation.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): custom aspects do Spring quản lý.
- [Spring Framework — Advice Ordering](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): precedence và luồng vào/ra của advice.
- [Spring Framework — MVC Interceptors](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/interceptors.html): `HandlerInterceptor` và phạm vi HTTP.
- [Spring Framework — Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html): `@TransactionalEventListener` và transaction phases.
- [Spring Framework — Declarative Annotation-based Caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html): `@Cacheable` và semantics cache hit/miss.


</details>

- [Quay lại đầu trang](#back-to-top)
