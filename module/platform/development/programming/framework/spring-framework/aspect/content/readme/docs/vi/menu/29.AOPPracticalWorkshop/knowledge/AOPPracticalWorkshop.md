<a id="back-to-top"></a>

# Thực hành tổng hợp

## Menu
- [Tạo ứng dụng Spring tối thiểu với service và aspect đo thời gian](#aop-practical-workshop-001)
- [So sánh cùng service qua JDK proxy và CGLIB proxy](#aop-practical-workshop-002)
- [Tạo annotation riêng và binding dữ liệu vào advice](#aop-practical-workshop-003)
- [Dựng ma trận pointcut theo package, phương thức và tên bean](#aop-practical-workshop-004)
- [Phối hợp nhiều aspect với thứ tự thực thi xác định](#aop-practical-workshop-005)
- [Mô phỏng self-invocation và sửa bằng tách bean cộng tác](#aop-practical-workshop-006)
- [Tạo advisor và proxy bằng Spring AOP API](#aop-practical-workshop-007)
- [Kiểm chứng đường thành công, ngoại lệ và nhiều luồng đồng thời](#aop-practical-workshop-008)
- [Tích hợp aspect với một cơ chế Spring đang có proxy](#aop-practical-workshop-009)
- [Chuyển cấu hình giữa annotation và XML rồi chạy kiểm thử tương đương](#aop-practical-workshop-010)
- [Thử LTW trong Spring khi thực sự cần vượt giới hạn proxy](#aop-practical-workshop-011)
- [Viết báo cáo vết thực thi và giải thích giới hạn của thiết kế](#aop-practical-workshop-012)

## <a id="aop-practical-workshop-001">Tạo ứng dụng Spring tối thiểu với service và aspect đo thời gian</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo ứng dụng Spring tối thiểu với service và aspect đo thời gian

## 1. Mục tiêu thực hành

Bài học này xây dựng một ứng dụng **Spring Framework thuần** có một service nghiệp vụ và một **Spring AOP aspect** đo thời gian thực thi service method. Mục đích là hiểu trọn đường đi từ cấu hình ApplicationContext đến lời gọi qua Spring proxy, quan sát giá trị trả về và ngoại lệ, và viết integration test có thể kiểm chứng.

Sau khi hoàn thành, bạn sẽ biết:

1. Tạo Maven project với Java 17+, Spring Context, Spring AOP và AspectJ Weaver.
2. Đăng ký service, aspect và các collaborator qua **@Bean**.
3. Bật Spring AOP bằng **@EnableAspectJAutoProxy**.
4. Dùng **@Around**, **ProceedingJoinPoint.proceed()** và **System.nanoTime()** để đo thời gian.
5. Ghi duration cả khi method thành công lẫn khi ném exception, đồng thời giữ nguyên kết quả/ngoại lệ cho caller.
6. Lấy bean từ **ApplicationContext**, xác nhận proxy và kiểm tra giới hạn self-invocation.
7. Viết JUnit 5 integration test với đồng hồ giả để duration chính xác, không phụ thuộc tốc độ CPU.

Bài chỉ tập trung vào cách **Spring Aspect** vận hành và triển khai, không mở rộng sang lý thuyết AOP chung hay AspectJ bytecode weaving. Annotation **@Aspect** và cú pháp pointcut kiểu AspectJ được Spring AOP dùng để cấu hình proxy; chúng không tự làm bytecode của class bị weave.

## 2. Luồng chạy của ứng dụng

~~~text
App.main
  |
  +--> AnnotationConfigApplicationContext(AppConfig.class)
         |
         +--> @EnableAspectJAutoProxy
         +--> BookingService bean
         +--> TimingAspect bean
         +--> TimingRecorder bean
         +--> LongSupplier ticker = System::nanoTime
         |
         +--> Spring tạo AOP proxy cho BookingService
  |
  +--> context.getBean(BookingService.class)
         |
         +--> service.book("B-001") qua proxy
                |
                +--> TimingAspect @Around
                       |
                       +--> start = ticker.getAsLong()
                       +--> joinPoint.proceed()
                       |      |
                       |      +--> BookingService.book(...)
                       |
                       +--> finally: ghi elapsedNanos
                       |
                       +--> trả lại result hoặc ném lại exception
~~~

**Điều kiện then chốt**: lời gọi phải đi qua **Spring-managed proxy** và phương thức phải khớp pointcut. Dùng **new BookingService()** trực tiếp bên ngoài container sẽ không kích hoạt Spring AOP advice. Lời gọi bên trong đối tượng bằng **this.book()** cũng không quay lại proxy.

## 3. Công nghệ và cấu trúc project

Ví dụ dùng **Java 17**, **Maven**, **Spring Framework 6.2.0**, **AspectJ Weaver 1.9.22.1**, **JUnit Jupiter 5.11.3**. Các phiên bản được cố định để bạn có thể chạy lại cùng bài học; dự án thực tế nên quản lý dependencies bằng BOM và policy cập nhật.

~~~text
spring-timing-demo/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── App.java
    │   ├── AppConfig.java
    │   ├── BookingService.java
    │   ├── TimingAspect.java
    │   └── TimingRecorder.java
    └── test/java/demo/
        └── TimingAspectIntegrationTest.java
~~~

Tất cả file Java dùng cùng package **demo**. Chỉ cần năm file Java production và một file test.

## 4. Maven: pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>spring-timing-demo</artifactId>
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
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.22.1</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-test</artifactId>
            <version>6.2.0</version>
            <scope>test</scope>
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
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Các thành phần có vai trò riêng:

- **spring-context**: ApplicationContext, @Configuration, @Bean và quản lý dependency injection.
- **spring-aop**: hệ thống proxy/advices và AopUtils.
- **aspectjweaver**: cung cấp thư viện AspectJ cần thiết để Spring AOP phân tích các annotation và pointcut. **Có dependency này không đồng nghĩa đã bật AspectJ weaving.**
- **spring-test** và **junit-jupiter**: tạo test với Spring TestContext và JUnit 5.
- Maven compiler/surefire/exec plugins: lần lượt biên dịch Java 17, chạy test, chạy main.

Kiểm tra JDK và Maven trước khi thực hành:

~~~bash
java -version
mvn -version
~~~

## 5. Viết service nghiệp vụ

Tệp **src/main/java/demo/BookingService.java**:

~~~java
package demo;

public class BookingService {
    public String book(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                "booking code is required"
            );
        }
        return "booked:" + code;
    }

    public String bookInternally(String code) {
        return this.book(code);
    }
}
~~~

**book(String)** trả **booked:<code>** khi input hợp lệ; nếu code null hoặc blank, nó ném **IllegalArgumentException**. Service không có code đo duration, cũng không cần import API của Spring AOP. Đây là ví dụ thuận lợi để xem cross-cutting behavior được tách khỏi nghiệp vụ.

**bookInternally(String)** được thêm chỉ để quan sát self-invocation: Spring AOP proxy không intercept lời gọi **this.book(code)** từ target.

## 6. Tạo nơi lưu số đo

Tệp **src/main/java/demo/TimingRecorder.java**:

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

public class TimingRecorder {
    public record Sample(
        String operation,
        long elapsedNanos,
        boolean successful
    ) {}

    private final List<Sample> history = new ArrayList<>();

    public synchronized void record(
            String operation,
            long elapsedNanos,
            boolean successful) {
        history.add(new Sample(
            operation, elapsedNanos, successful
        ));
    }

    public synchronized List<Sample> samples() {
        return List.copyOf(history);
    }

    public synchronized void clear() {
        history.clear();
    }
}
~~~

Đây là **test/demo collaborator** có thể quan sát được. Mỗi mẫu lưu tên method, elapsed nanoseconds và trạng thái lời gọi đồng bộ. Sử dụng **synchronized** để danh sách không bị truy cập đồng thời không kiểm soát. Trong production, không nên lưu toàn bộ duration vô hạn trong RAM; thường ghi metric qua Micrometer MeterRegistry hoặc hạ tầng quan sát đã có.

**successful=true** chỉ có nghĩa **proceed()** trả về bình thường. Nó không khẳng định transaction đã commit hay công việc async đã kết thúc.

## 7. Viết TimingAspect bằng @Around

Tệp **src/main/java/demo/TimingAspect.java**:

~~~java
package demo;

import java.util.function.LongSupplier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TimingAspect {
    private final TimingRecorder recorder;
    private final LongSupplier ticker;

    public TimingAspect(
            TimingRecorder recorder,
            LongSupplier ticker) {
        this.recorder = recorder;
        this.ticker = ticker;
    }

    @Around("execution(* demo.BookingService.book(..))")
    public Object measure(ProceedingJoinPoint joinPoint)
            throws Throwable {
        long started = ticker.getAsLong();
        boolean successful = false;

        try {
            Object result = joinPoint.proceed();
            successful = true;
            return result;
        } finally {
            long duration = ticker.getAsLong() - started;
            recorder.record(
                "BookingService.book",
                duration,
                successful
            );
        }
    }
}
~~~

### 7.1. Giải thích từng dòng quan trọng

**@Aspect** xác định lớp mang advice. Nó không tự đăng ký bean; phần cấu hình sẽ tạo **TimingAspect** bằng **@Bean**.

**@Around** cho phép thực thi logic trước và sau method được chọn. Pointcut **execution(* demo.BookingService.book(..))** chọn execution của method **book** trên BookingService. Ký hiệu **\*** cho kiểu trả về bất kỳ, **(..)** cho danh sách tham số bất kỳ; trong demo chỉ có **book(String)**.

**ProceedingJoinPoint.proceed()** tiếp tục chuỗi advisor rồi gọi target. Nếu không gọi nó, target có thể bị bỏ qua. Nếu gọi hai lần, target có thể chạy hai lần; aspect đo thời gian này chỉ gọi **một lần**.

**return result** giữ nguyên kết quả nghiệp vụ từ phương thức đích. **throws Throwable** cho phép lỗi propagate mà advice không phải nuốt/chuyển đổi exception.

**try/finally** làm recorder ghi duration cả khi target ném exception. Vì không catch để return fallback, caller vẫn nhận **IllegalArgumentException** do BookingService ném.

**LongSupplier** là đầu vào đồng hồ. Trong ứng dụng thật ta inject **System::nanoTime**; trong test dùng ticker giả tăng cố định để duration luôn kiểm chứng được. **System.nanoTime()** được thiết kế cho đo khoảng thời gian, không dùng như Unix timestamp.

**finally không được return**, vì return trong finally có thể che exception và ghi đè kết quả. Ngoài ra, nếu chính **recorder.record** ném lỗi, nó cũng có thể thay exception của target. Trong demo in-memory collector không có nguồn lỗi bên ngoài; với exporter production cần một failure policy được định nghĩa rõ.

## 8. Cấu hình Spring và bật auto-proxy

Tệp **src/main/java/demo/AppConfig.java**:

~~~java
package demo;

import java.util.function.LongSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    public BookingService bookingService() {
        return new BookingService();
    }

    @Bean
    public TimingRecorder timingRecorder() {
        return new TimingRecorder();
    }

    @Bean
    public LongSupplier ticker() {
        return System::nanoTime;
    }

    @Bean
    public TimingAspect timingAspect(
            TimingRecorder recorder, LongSupplier ticker) {
        return new TimingAspect(recorder, ticker);
    }
}
~~~

Spring đọc AppConfig, tạo các bean và hạ tầng AOP. Do BookingService có method khớp pointcut, bean trả về cho client sẽ là Spring AOP proxy. **proxyTargetClass=true** yêu cầu class-based/CGLIB proxy, nên trong ví dụ có thể lấy bean bằng **BookingService.class**.

**Không cần @ComponentScan** vì mọi dependency và aspect đều được khai báo bằng @Bean. Nếu chọn @Service/@Component thay vì @Bean, phải bảo đảm component scan bao phủ đúng package.

**@EnableAspectJAutoProxy** chỉ bật Spring AOP proxy. Nó không chạy AspectJ compiler hoặc AspectJ load-time weaving. Với CGLIB, self-invocation vẫn bỏ qua proxy; final/private methods không thể bị advice theo cách override thông thường.

## 9. Chương trình main

Tệp **src/main/java/demo/App.java**:

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(
                    AppConfig.class)) {

            BookingService service =
                context.getBean(BookingService.class);
            TimingRecorder recorder =
                context.getBean(TimingRecorder.class);

            System.out.println(
                "proxy=" + AopUtils.isAopProxy(service)
            );
            System.out.println(
                "success=" + service.book("B-001")
            );

            try {
                service.book("");
            } catch (IllegalArgumentException ex) {
                System.out.println(
                    "caught=" +
                    ex.getClass().getSimpleName() +
                    ":" + ex.getMessage()
                );
            }

            System.out.println(
                "internal=" +
                service.bookInternally("B-002")
            );

            System.out.println(
                "samples=" + recorder.samples().size()
            );

            for (var sample : recorder.samples()) {
                System.out.println(
                    sample.operation() +
                    ",success=" + sample.successful() +
                    ",nonNegative=" +
                    (sample.elapsedNanos() >= 0)
                );
            }
        }
    }
}
~~~

**context.getBean** là ranh giới quan trọng nhất: nó cho client bean đã được Spring proxy khi pointcut áp dụng. Nếu thay bằng **new BookingService()**, aspect không can thiệp.

**book("B-001")** là lời gọi hợp lệ; **book("")** ném exception và ghi sample failure; **bookInternally("B-002")** không tạo sample mới vì lời gọi **book()** ở bên trong không quay qua proxy.

## 10. Biên dịch, chạy và kiểm tra stdout

Từ thư mục **spring-timing-demo**, chạy:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.App
~~~

Các dòng **System.out.println** của chương trình cho kết quả mong đợi:

~~~text
proxy=true
success=booked:B-001
caught=IllegalArgumentException:booking code is required
internal=booked:B-002
samples=2
BookingService.book,success=true,nonNegative=true
BookingService.book,success=false,nonNegative=true
~~~

Maven hoặc logging có thể in thêm thông báo phụ thuộc môi trường. **elapsedNanos thực tế thay đổi** theo tải JVM và CPU; vì thế chương trình in boolean nonNegative để không đưa ra một số thời gian giả định. Đồng hồ System.nanoTime phù hợp tính chênh lệch và trong chu trình bình thường sẽ cho duration không âm ở đây.

Vì sao có **2 sample**? Chỉ có hai lần gọi **book(...)** trực tiếp từ ngoài qua proxy; lần gọi thứ ba nằm trong **bookInternally() → this.book()**, bypass proxy. Việc target chạy thành công không đồng nghĩa mỗi method execution đều được Spring proxy intercept.

Nếu muốn so sánh, thay **service.bookInternally("B-002")** bằng **service.book("B-002")** trong bản demo: số sample sẽ tăng lên 3 khi pointcut vẫn giữ nguyên.

## 11. Tạo integration test có ticker giả

Một unit test tạo thẳng **new TimingAspect(...)** chỉ chứng minh code advice; nó không chứng minh Spring tạo proxy hoặc pointcut thực sự match. Ta dùng **@SpringJUnitConfig** và Spring ApplicationContext thực để kiểm thử wiring, runtime interception và hợp đồng return/exception.

Ticker giả có hành vi: mỗi lần đọc trả giá trị trước đó rồi tăng thêm **100**. Hai lần đọc liên tiếp quanh **proceed()** tạo elapsedNanos chính xác **100**, không phụ thuộc Thread.sleep, JIT, GC hay scheduler.

Tệp **src/test/java/demo/TimingAspectIntegrationTest.java**:

~~~java
package demo;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(TimingAspectIntegrationTest.TestConfig.class)
class TimingAspectIntegrationTest {
    @Autowired
    BookingService service;

    @Autowired
    TimingRecorder recorder;

    @BeforeEach
    void reset() {
        recorder.clear();
    }

    @Test
    void successPreservesReturnValueAndRecordsDuration() {
        assertTrue(AopUtils.isAopProxy(service));
        assertEquals(
            "booked:A-01",
            service.book("A-01")
        );
        assertEquals(
            List.of(
                new TimingRecorder.Sample(
                    "BookingService.book", 100L, true
                )
            ),
            recorder.samples()
        );
    }

    @Test
    void failurePropagatesExceptionAndRecordsDuration() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> service.book("")
        );
        assertEquals(
            "booking code is required",
            error.getMessage()
        );
        assertEquals(
            List.of(
                new TimingRecorder.Sample(
                    "BookingService.book", 100L, false
                )
            ),
            recorder.samples()
        );
    }

    @Test
    void selfInvocationDoesNotTriggerBookAdvice() {
        assertEquals(
            "booked:A-02",
            service.bookInternally("A-02")
        );
        assertEquals(List.of(), recorder.samples());
    }

    @Test
    void plainJavaObjectIsNotSpringProxied() {
        BookingService plain = new BookingService();
        assertEquals(
            "booked:A-03",
            plain.book("A-03")
        );
        assertTrue(recorder.samples().isEmpty());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {
        @Bean
        BookingService bookingService() {
            return new BookingService();
        }

        @Bean
        TimingRecorder timingRecorder() {
            return new TimingRecorder();
        }

        @Bean
        LongSupplier ticker() {
            AtomicLong value = new AtomicLong();
            return () -> value.getAndAdd(100L);
        }

        @Bean
        TimingAspect timingAspect(
                TimingRecorder recorder,
                LongSupplier ticker) {
            return new TimingAspect(recorder, ticker);
        }
    }
}
~~~

### 11.1. Bốn ca test kiểm tra những gì?

- **successPreservesReturnValueAndRecordsDuration:** bean do Spring cấp là AOP proxy, trả đúng chuỗi **booked:A-01** và ghi đúng một sample **100, true**.
- **failurePropagatesExceptionAndRecordsDuration:** **IllegalArgumentException** gốc đến caller, trong khi **finally** vẫn ghi một sample **100, false**.
- **selfInvocationDoesNotTriggerBookAdvice:** target chạy nhưng không có record, minh họa lời gọi **this.book()** không qua Spring proxy.
- **plainJavaObjectIsNotSpringProxied:** gọi đối tượng tạo bằng **new** không kích hoạt aspect trong ApplicationContext.

**@BeforeEach** xóa trạng thái recorder vì Spring TestContext có thể cache ApplicationContext giữa các test; nếu không xóa, một test có thể chịu ảnh hưởng từ các sample của test khác. Ticker tiếp tục tăng nhưng hiệu số hai lần đọc liền nhau vẫn là **100**. Nếu bật parallel test cùng chia sẻ context, cần cô lập fixture cho từng test hoặc tắt parallel đối với những test này.

### 11.2. Chạy test

~~~bash
mvn test
~~~

Có thể chạy riêng class:

~~~bash
mvn -Dtest=TimingAspectIntegrationTest test
~~~

Bốn ca test trên có kết quả mong đợi trong tóm tắt Maven Surefire:

~~~text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
~~~

Đây là kết quả **dự kiến từ code và assertion**, không phải stdout ghi nhận từ một phiên Maven đã chạy. Các dòng Maven khác có thể thay đổi theo môi trường.

## 12. Kiểm chứng pointcut và proxy đúng cách

| Tình huống | Kết luận | Cách xem |
|---|---|---|
| **AopUtils.isAopProxy(service)=true** | Bean là Spring AOP proxy | Kiểm tra trong main/test |
| **service.book("X")** ghi một Sample | Aspect thực sự advice đúng method | Đọc TimingRecorder |
| **service.book("")** ném lỗi, vẫn có Sample | finally hoạt động, exception không bị nuốt | assertThrows và recorder |
| **service.bookInternally("X")** không ghi Sample | Lời gọi nội bộ bypass proxy | Integration test |
| **new BookingService().book("X")** không ghi Sample | Đối tượng ngoài Spring không được auto-proxy | Test plain object |
| Bean proxy nhưng method không có sample | Có thể pointcut không match hoặc đi sai đường gọi | Xem method, package, overload và caller |
| **@Aspect** tồn tại nhưng không có proxy | Có thể aspect chưa là bean hoặc AOP chưa bật | Xem @Bean, @ComponentScan và @EnableAspectJAutoProxy |

**Proxy tồn tại chưa chứng minh mọi advice đã chạy.** Test cần assert kết quả quan sát được sau lời gọi tới đúng method qua bean được Spring quản lý.

Trong main, có thể in thêm thông tin chẩn đoán nếu cần:

~~~java
System.out.println(AopUtils.isCglibProxy(service));
System.out.println(
    AopUtils.getTargetClass(service).getName()
);
~~~

Đây là mảnh mã thêm trong phạm vi đã có biến **service**. Với demo hiện tại, dự kiến dòng đầu là **true**, dòng tiếp theo là **demo.BookingService**.

## 13. Bài thực nghiệm khi timer trả kết quả sai

Với project `BookingService`, chia sai lệch thành ba loại có bằng chứng riêng: **proxy không vào**, **target/advice đi sai nhánh**, hoặc **thời gian bị đo sai**. Sửa từng điều kiện trong bản sao thí nghiệm rồi chạy lại bốn integration tests ở mục 11.

| Thay đổi cố ý | Kết quả cần phát hiện | Assertion quan trọng |
| --- | --- | --- |
| Bỏ đăng ký bean `TimingAspect` | `book` vẫn chạy nhưng không có sample | Positive control đếm đúng một mẫu |
| Thay `context.getBean` bằng `new BookingService()` | Target chạy ngoài proxy; không có metric | `AopUtils.isAopProxy` và recorder |
| Đổi `book(..)` thành `cancel(..)` trong pointcut | Lời gọi `book` không match | Một ca dương và một ca âm |
| Bỏ `return result` sau `pjp.proceed()`, trả `null` | Caller mất giá trị `booked:A-01` | Assert exact return value |
| Bắt lỗi và trả `"success"` | `book("")` không còn ném lỗi gốc | `assertThrows` + trạng thái sample |
| Return từ khối `finally` | Có thể che return hoặc exception | Test cả nhánh thành công và lỗi |
| Cho ticker giả tăng từ 100 thành 250 nanoseconds | Test cũ kỳ vọng 100 phải đỏ | Chỉ sửa expected duration khi chủ động đổi fixture |
| Gọi `bookInternally` qua target | Lời gọi `this.book()` vẫn không tạo sample riêng | Đối chứng external invocation |

Các thay đổi trên là **thí nghiệm lỗi**, không đưa đồng thời vào source chuẩn. Khi quay về baseline, cả bốn test cần xanh và số sample không phụ thuộc tốc độ máy.

### 13.1. Ba giới hạn của đồng hồ cần báo đúng

- `System.nanoTime()` đo *khoảng thời gian* bằng hiệu hai lần đọc, không phải timestamp lịch. Đừng so elapsed duration với một mốc Unix.
- `try/finally` ghi cả exceptional exit nhưng `successful=true` chỉ nói invocation đồng bộ trả bình thường; không xác nhận transaction commit.
- Với `CompletableFuture` hoặc reactive publisher, `proceed()` có thể trả wrapper trước khi công việc hoàn tất. Muốn đo completion, cần instrumentation theo future/reactive signal với ownership và return type phù hợp.

Đối với recorder/exporter lỗi, đặt rõ failure policy: lỗi telemetry có được làm thay đổi business result/exception hay không? Một `@Around` chỉ dùng đo thời gian nên mặc định bảo vệ contract của caller, trừ khi chính sách nghiệp vụ yêu cầu khác.

## 14. Đưa ý tưởng vào ứng dụng Spring Boot

Trong Spring Boot, dependency **spring-boot-starter-aop** có thể kích hoạt AOP auto-configuration khi điều kiện phù hợp. Bạn có thể đánh dấu service bằng **@Service**, aspect bằng **@Aspect + @Component**, rồi cho component scan phát hiện chúng. Nhiều dự án Boot không cần ghi tường minh **@EnableAspectJAutoProxy**.

Tùy cấu hình, Boot có thể mặc định dùng class-based/CGLIB proxy. Property thường dùng:

~~~properties
spring.aop.proxy-target-class=true
~~~

Nó quyết định kiểu proxy, **không phải** công tắc AspectJ bytecode weaving. Tương tự, nếu ứng dụng có @Transactional, caching, security, retry hoặc nhiều advisors, vị trí TimingAspect trong advisor chain có thể làm duration phản ánh các phạm vi khác nhau. Chỉ cấu hình **@Order** khi ordering là yêu cầu cụ thể và phải có test chứng minh.

Trong ứng dụng đã sử dụng Micrometer Observation/Tracing hoặc metrics integration, hãy xem liệu hạ tầng có sẵn đã cung cấp latency signal cần thiết chưa. Không tự dựng một timer thứ hai trùng chức năng chỉ vì đã học cách viết @Around.

## 15. Khi chuyển từ demo sang production

**TimingRecorder** của bài lưu vô hạn mẫu trong bộ nhớ và chủ yếu dành cho việc học/test. Với hệ thống thật:

1. Dùng metric sink có quản lý retention và export thích hợp.
2. Đặt tên operation ổn định, có số lượng nhãn hữu hạn; không dùng booking code hay user ID làm metric tag.
3. Chọn pointcut vừa đủ; không đo tất cả method trong mọi bean khi không có nhu cầu.
4. Xác định metrics backend bị lỗi thì request có được tiếp tục không.
5. Phân biệt latency của method, chuỗi advisor, transaction và end-to-end HTTP request.
6. Dùng sampling, aggregation hoặc histogram phù hợp mục tiêu vận hành.
7. Kiểm tra số lượng lần advice chạy, overhead của chính telemetry, và tránh serialize payload lớn trong interceptor.
8. Với async/reactive, quan sát completion thay vì chỉ thời gian tạo future/publisher.

Một interface collaborator tương lai có thể là:

~~~java
public interface DurationRecorder {
    void record(
        String operation,
        long elapsedNanos,
        boolean successful
    );
}
~~~

Đoạn trên là **đề xuất mở rộng**, không phải file bắt buộc của project runnable. Có thể tạo implementation dùng Micrometer nhưng cần định nghĩa tags và failure policy riêng. Spring Aspect vẫn chỉ cần chịu trách nhiệm chọn đúng invocation và đo khoảng thời gian.

## 16. Bài tập tự kiểm tra

1. Thay **bookInternally("B-002")** bằng **book("B-002")** ở main. Dự đoán số sample và chạy lại để xác minh.
2. Thêm method **cancel(String)** vào service. Gọi cancel qua Spring proxy và xác minh aspect có ghi sample hay không khi pointcut chỉ chọn book.
3. Thu hẹp pointcut thành chữ ký **execution(* demo.BookingService.book(java.lang.String))** và kiểm tra bài vẫn chạy.
4. Tạo thêm test với **book(null)**, assert exception và sample thất bại.
5. Thay ticker giả tăng 100 bằng 250, sửa assertion và xác minh test không phụ thuộc tốc độ máy.
6. Thử bỏ **@EnableAspectJAutoProxy** khỏi cấu hình Spring thuần của demo rồi quan sát sự khác biệt, sau đó khôi phục.
7. Dùng **AopUtils.isCglibProxy(service)** và **isJdkDynamicProxy(service)** để xác định kiểu proxy thực tế.
8. Thêm một method gọi **book** qua bean khác để chứng minh sự khác biệt so với self-invocation.

Không nên sửa hợp đồng của service chỉ để làm log hiển thị đẹp. Mục đích test là bảo đảm aspect quan sát mà không can thiệp ngoài chủ đích.

## 17. Checklist hoàn thành

- [ ] Maven biên dịch project với Java 17+.
- [ ] ApplicationContext được khởi tạo bằng AppConfig.
- [ ] BookingService, TimingAspect, TimingRecorder và ticker được đăng ký.
- [ ] Spring AOP auto-proxy được bật.
- [ ] AopUtils.isAopProxy(service) là true.
- [ ] Gọi book hợp lệ nhận đúng chuỗi kết quả.
- [ ] Gọi book("") ném IllegalArgumentException tới caller.
- [ ] TimingRecorder ghi đúng hai mẫu của hai lần gọi qua proxy.
- [ ] Self-invocation không bị hiểu nhầm thành pointcut không khớp.
- [ ] Advice giữ nguyên return value và exception, đo trong finally.
- [ ] Maven chạy bốn JUnit 5 integration tests.
- [ ] Phân biệt được Spring AOP proxy với AspectJ weaving.
- [ ] Không dùng duration đo trên máy thật làm assertion bằng một số cố định.

## 18. Tài liệu chính thức

- Spring Framework 6.2 — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html
- Spring Framework — Around Advice: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — Pointcut Expressions: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Spring Framework 6.2 — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html
- Spring Framework — Spring JUnit Jupiter Testing Annotations: https://docs.spring.io/spring-framework/reference/testing/annotations/integration-junit-jupiter.html
- Spring Boot — Aspect-Oriented Programming: https://docs.spring.io/spring-boot/reference/features/aop.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-practical-workshop-002">So sánh cùng service qua JDK proxy và CGLIB proxy</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh cùng service qua JDK proxy và CGLIB proxy trong Spring AOP

## 1. Mục tiêu thực hành

Spring AOP có thể bọc **cùng một service** bằng hai kiểu proxy: **JDK dynamic proxy**, dựa trên các interface mà target triển khai, hoặc **class-based proxy (CGLIB)**, tạo lớp con runtime của target. Cả hai đều có thể thực thi cùng một `@Aspect` trong Spring, nhưng khác nhau về **kiểu object mà caller nhận được**, những phương thức caller có thể gọi, giới hạn của phương thức `final`, và việc inject/ép kiểu về implementation.

Bài này tập trung riêng vào **cơ chế proxy của Spring Framework**. Chúng ta sẽ chạy hai `ApplicationContext` tách biệt với:

- **Cùng** `TaskService`, `DefaultTaskService` và `TraceAspect`.
- **Cùng** pointcut `execution(* demo.TaskService.process(..))`.
- **Cùng** lời gọi business method và lời gọi nội bộ từ target.
- **Chỉ khác** `@EnableAspectJAutoProxy(proxyTargetClass = false/true)`.

Sau bài, bạn sẽ đọc được sự khác biệt giữa `AopUtils.isJdkDynamicProxy`, `AopUtils.isCglibProxy`, `getTargetClass`, khả năng `instanceof DefaultTaskService`, kết quả gọi advice và giới hạn của `this.process()`.

Đây là Spring **proxy-based AOP**. `aspectjweaver` được đặt trên classpath để Spring xử lý biểu thức `@AspectJ`; ví dụ **không** bật AspectJ load-time weaving, không dùng Java agent hoặc `META-INF/aop.xml`.

## 2. Hai loại proxy đứng ở vị trí nào?

Giả sử `DefaultTaskService` triển khai `TaskService`:

```text
                 interface TaskService
                         ^
                         | implements
                  DefaultTaskService
                         ^
                         | extends (chỉ ở nhánh class-based)
             DefaultTaskService$$SpringCGLIB$$...

Nhánh JDK:
    JDK dynamic proxy implements TaskService
        └─ ủy quyền lời gọi qua Spring interceptor chain đến target
           (KHÔNG extends DefaultTaskService)

Nhánh CGLIB:
    generated subclass extends DefaultTaskService
        └─ Spring interceptor chain ủy quyền lời gọi đến target
```

Cách suy nghĩ đúng: **target object** và **proxy object** là hai vai trò khác nhau. Spring container có thể đưa proxy cho caller, proxy giữ thông tin target và danh sách advisor. `AopUtils.getTargetClass(proxy)` cho biết target class, **không** chứng minh rằng chính proxy cast được sang class đó.

| Thuộc tính | JDK dynamic proxy | Class-based proxy (CGLIB) |
| --- | --- | --- |
| Điều kiện điển hình | Target triển khai ít nhất một interface phù hợp và không ép class proxy | Target không có interface phù hợp hoặc `proxyTargetClass=true` |
| Loại object nhận từ Spring | Lớp proxy của JDK triển khai interface | Lớp con runtime của target |
| Có thể cast sang business interface? | Có, khi proxy công bố interface đó | Có nếu target triển khai interface |
| Có thể cast sang target implementation? | Không | Có nếu class-based proxy tạo thành công |
| Gọi method public **chỉ có ở implementation** từ proxy? | Không, trừ khi proxy còn công bố interface tương ứng | Có thể qua reference/cast sang implementation |
| `final` class | Có thể proxy qua interface | Không thể subclass `final` class |
| `final` method của target | Lời gọi qua interface có thể được intercept bởi JDK proxy | Không thể advice bằng cách override trong class proxy |
| `private` method | Không được expose qua interface proxy | Không thể được advice bằng override |
| Self-invocation `this.method()` | Thường bỏ qua Spring proxy | **Vẫn** thường bỏ qua Spring proxy |
| Ý nghĩa của `@EnableAspectJAutoProxy` | Bật Spring auto-proxy theo cấu hình | Bật cùng cơ chế, nhưng yêu cầu proxy lớp |

**Lưu ý về Spring Boot:** Spring Framework và Spring Boot có thể có cấu hình mặc định proxy khác nhau. Không suy luận rằng một ứng dụng sẽ dùng JDK proxy chỉ vì bean có interface nếu Spring Boot hoặc một auto-proxy creator khác đã ép `proxyTargetClass=true`.

## 3. Thiết kế thí nghiệm công bằng

Để so sánh có ý nghĩa, chỉ thay **một biến là chiến lược proxy**. Ví dụ dùng hai lớp cấu hình:

```java
@EnableAspectJAutoProxy(proxyTargetClass = false) // Context 1
@EnableAspectJAutoProxy(proxyTargetClass = true)  // Context 2
```

Mỗi cấu hình đăng ký:

- Một bean tên `taskService`, instance mới của `DefaultTaskService`.
- Một bean `TraceAspect`, trong đó advice đếm số invocation và in log.
- Không đăng ký transaction/cache/security advisor khác để tránh thay đổi kết quả.

Chạy tuần tự, tạo và đóng từng `AnnotationConfigApplicationContext`. **Không import cả hai cấu hình vào một context**: Spring thường hợp nhất các cấu hình auto-proxy creator, và yêu cầu mạnh nhất (`proxyTargetClass=true`) có thể ảnh hưởng tất cả proxy trong cùng context. Nếu nhập cả hai vào một context, phép thử JDK/CGLIB sẽ không còn tách biệt.

Ba thao tác quan sát:

1. Lấy service qua **interface** rồi gọi `service.process("A")`: cả hai proxy đều chạy advice.
2. Gọi `service.invokeInternally("X")`, bên trong target thực thi `this.process("X")`: target chạy, nhưng advice trên `process` thường **không** chạy thêm vì self-invocation.
3. Kiểm tra `bean instanceof DefaultTaskService` rồi thử cast: chỉ nhánh CGLIB hỗ trợ cast đó; nhánh JDK proxy thất bại với `ClassCastException` nếu cố ép.

Thêm phép kiểm tra `AopUtils` để xác minh loại proxy thật, thay vì suy luận từ tên lớp sinh tự động.

## 4. Ví dụ Maven/Java 17+ có thể chạy độc lập

Phiên bản ví dụ: Java 17, Spring Framework 6.2.14, AspectJ Weaver 1.9.24, Maven. Chúng ta chỉ dùng Spring AOP annotation style, không cần Spring Boot.

### 4.1. Cấu trúc file

```text
spring-proxy-comparison/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── TaskService.java
    │   ├── DefaultTaskService.java
    │   ├── TraceAspect.java
    │   ├── JdkProxyConfig.java
    │   ├── CglibProxyConfig.java
    │   └── Main.java
    └── test/java/demo/
        └── ProxyComparisonTest.java
```

### 4.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-proxy-comparison</artifactId>
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

`spring-aop` mang cơ chế tạo proxy; CGLIB đã được Spring đóng gói trong `spring-core`, không cần thêm dependency `net.sf.cglib:cglib`. `aspectjweaver` hỗ trợ Spring phân tích pointcut của `@Aspect`; nó không biến ví dụ thành AspectJ weaving.

### 4.3. Business interface `TaskService.java`

```java
package demo;

public interface TaskService {
    String process(String id);
    String invokeInternally(String id);
}
```

Định nghĩa cả `process` và `invokeInternally` trong interface để **cùng đoạn code caller** chạy được ở cả hai chiến lược. Pointcut sẽ chỉ bắt `process`, không bắt `invokeInternally`.

### 4.4. Implementation `DefaultTaskService.java`

```java
package demo;

public class DefaultTaskService implements TaskService {
    @Override
    public String process(String id) {
        System.out.println("TARGET: " + id);
        return "done:" + id;
    }

    @Override
    public String invokeInternally(String id) {
        return this.process(id);
    }

    public String implementationOnly() {
        return "implementation-only";
    }
}
```

`DefaultTaskService` **không phải final** và các method quan trọng có thể override; đó là điều kiện thuận lợi để CGLIB subclass proxy hoạt động.

`invokeInternally` minh họa **self-invocation**: lời gọi `this.process(id)` bắt đầu từ target instance, không đi qua external proxy. `implementationOnly` là method public **không nằm trong** `TaskService`; JDK proxy không expose method đó, còn CGLIB proxy có thể expose qua lớp con. Pointcut không nhắm `implementationOnly`, nên không có advice ở method này.

### 4.5. Aspect duy nhất dùng chung: `TraceAspect.java`

```java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TraceAspect {
    private final AtomicInteger calls = new AtomicInteger();

    @Around("execution(* demo.TaskService.process(..))")
    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
        calls.incrementAndGet();
        System.out.println("ADVICE: before");
        try {
            return joinPoint.proceed();
        } finally {
            System.out.println("ADVICE: after");
        }
    }

    public int invocationCount() {
        return calls.get();
    }
}
```

Aspect không thay đổi giữa hai context. Advice ghi một lần vào counter mỗi khi `process` **đi qua Spring interceptor chain**. `AtomicInteger` giúp counter an toàn hơn nếu được nhiều thread gọi; nhưng mục tiêu thí nghiệm này là so sánh proxy, nên các lời gọi chạy tuần tự.

`finally` in `ADVICE: after` cả khi target ném ngoại lệ, và `joinPoint.proceed()` là bước cho lời gọi đi tiếp đến target. Trong test bên dưới, số advice phải là **1** sau khi gọi trực tiếp `process` và gọi `invokeInternally` một lần: lời gọi nội bộ thứ hai không đi qua proxy để vào advice.

### 4.6. Cấu hình JDK proxy: `JdkProxyConfig.java`

```java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class JdkProxyConfig {
    @Bean("taskService")
    public TaskService taskService() {
        return new DefaultTaskService();
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }
}
```

Ở Spring Framework thuần, nếu target có business interface và không có cấu hình khác ép proxy lớp, `proxyTargetClass=false` cho phép Spring dùng **JDK dynamic proxy**. Bean vẫn có target `DefaultTaskService`; proxy trả về triển khai `TaskService`.

### 4.7. Cấu hình CGLIB proxy: `CglibProxyConfig.java`

```java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class CglibProxyConfig {
    @Bean("taskService")
    public TaskService taskService() {
        return new DefaultTaskService();
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }
}
```

**Chỉ khác một flag** so với cấu hình trước. Class proxy kế thừa `DefaultTaskService`, nên caller có thể cast nếu cần; tuy nhiên, ép toàn bộ ứng dụng dùng CGLIB chỉ để tiện cast thường không phải lựa chọn đầu tiên.

### 4.8. Chạy và quan sát: `Main.java`

```java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        inspect("JDK", JdkProxyConfig.class);
        inspect("CGLIB", CglibProxyConfig.class);
    }

    private static void inspect(String label, Class<?> config) {
        try (var context = new AnnotationConfigApplicationContext(config)) {
            Object bean = context.getBean("taskService");
            TraceAspect aspect = context.getBean(TraceAspect.class);

            System.out.println("=== " + label + " ===");
            System.out.println("isAopProxy=" + AopUtils.isAopProxy(bean));
            System.out.println("isJdkProxy=" + AopUtils.isJdkDynamicProxy(bean));
            System.out.println("isCglibProxy=" + AopUtils.isCglibProxy(bean));
            System.out.println("targetClass="
                    + AopUtils.getTargetClass(bean).getSimpleName());
            System.out.println("isImplementation="
                    + (bean instanceof DefaultTaskService));

            // Business contract dùng được ở cả hai proxy.
            TaskService service = (TaskService) bean;
            System.out.println("direct=" + service.process("A"));
            System.out.println("internal=" + service.invokeInternally("B"));
            System.out.println("adviceCalls=" + aspect.invocationCount());

            try {
                DefaultTaskService concrete = (DefaultTaskService) bean;
                System.out.println("cast=SUCCESS");
                System.out.println("extra=" + concrete.implementationOnly());
            } catch (ClassCastException ignored) {
                System.out.println("cast=ClassCastException");
            }
        }
    }
}
```

`AopUtils` kiểm tra **object trả về** bởi container. Tên lớp được sinh ra từ JDK/CGLIB và chỉ số proxy runtime có thể khác nhau; bài không dùng những tên đó làm assertion.

### 4.9. Lệnh chạy và stdout

Trong thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java
```

**Stdout kỳ vọng** của phần chương trình (bỏ qua log startup/build nếu framework in thêm):

```text
=== JDK ===
isAopProxy=true
isJdkProxy=true
isCglibProxy=false
targetClass=DefaultTaskService
isImplementation=false
ADVICE: before
TARGET: A
ADVICE: after
direct=done:A
TARGET: B
internal=done:B
adviceCalls=1
cast=ClassCastException
=== CGLIB ===
isAopProxy=true
isJdkProxy=false
isCglibProxy=true
targetClass=DefaultTaskService
isImplementation=true
ADVICE: before
TARGET: A
ADVICE: after
direct=done:A
TARGET: B
internal=done:B
adviceCalls=1
cast=SUCCESS
extra=implementation-only
```

**Giải thích từng nhóm dòng:**

- `isAopProxy=true` ở **cả hai**: cùng aspect/pointcut đã khiến Spring bọc target bằng proxy.
- `isJdkProxy` và `isCglibProxy` khác nhau vì flag ở cấu hình. Đây là phép đo trực tiếp bằng Spring API, không đoán theo tên class.
- `targetClass=DefaultTaskService` ở cả hai; nhưng `isImplementation=false` ở JDK, `true` ở CGLIB. Cấu hình **target** giống nhau không đồng nghĩa **proxy runtime type** giống nhau.
- Dòng `ADVICE: before/after` chỉ xuất hiện quanh **direct process("A")**. Lệnh `invokeInternally("B")` gọi `this.process` bên trong target, nên chỉ có `TARGET: B`, không có advice.
- `adviceCalls=1` ở hai nhánh chứng minh rằng việc ép proxy lớp **không tự sửa lỗi self-invocation**.
- Cast sang `DefaultTaskService` thất bại ở JDK, thành công ở CGLIB. `implementationOnly()` chỉ có thể gọi theo cast trong nhánh CGLIB; method đó không được advice vì pointcut không chọn nó.

Lưu ý đây là **kết quả được dự đoán từ ví dụ**. Để xác nhận trong môi trường cụ thể, chạy lệnh Maven và đối chiếu stdout; nếu có proxy creator/auto-configuration khác được thêm vào, loại proxy có thể thay đổi.

## 5. Vì sao JDK proxy không thể cast sang implementation?

Một JDK dynamic proxy công bố các interface được chọn, ví dụ `TaskService`, nhưng không kế thừa `DefaultTaskService`:

```java
Object proxy = context.getBean("taskService");
TaskService accepted = (TaskService) proxy;  // hợp lệ

// Khi runtime là JDK proxy: gây ClassCastException.
DefaultTaskService invalid = (DefaultTaskService) proxy;
```

Đây là snippet **minh họa riêng** có biến `context` đã tồn tại. Nó không được thêm nguyên xi vào ví dụ chạy vì nhánh CGLIB có kiểu runtime khác.

Khi API/controller chỉ cần gọi `process`, cách ổn định là **inject theo interface**:

```java
// Fragment ở một Spring component thực tế, không phải file chạy độc lập.
private final TaskService taskService;

public TaskController(TaskService taskService) {
    this.taskService = taskService;
}
```

Nếu `@Autowired`/constructor injection yêu cầu `DefaultTaskService` nhưng Spring đang cấp JDK proxy, container có thể báo lỗi kiểu `BeanNotOfRequiredTypeException` hoặc lỗi dependency resolution bọc nguyên nhân. Cách xử lý đầu tiên là xem lại kiểu dependency, không unwrap target để bypass proxy. Gọi target trực tiếp có thể bỏ qua transaction, security, cache hoặc custom aspect.

CGLIB proxy hỗ trợ cast khi target cho phép subclass, nhưng đổi `proxyTargetClass=true` là **thay đổi ở cơ chế AOP**, cần đánh giá ảnh hưởng đến nhiều bean khác trong cùng context.

## 6. So sánh giới hạn đối với method và lớp Java

### 6.1. Interface method và method chỉ có trên implementation

Trong ví dụ:

- `process` nằm trong `TaskService`: caller qua interface chạy được ở cả hai proxy.
- `implementationOnly` chỉ có trên `DefaultTaskService`: JDK proxy không expose; CGLIB proxy có method public đó vì kế thừa target class.
- Đặt pointcut `execution(* demo.DefaultTaskService.implementationOnly(..))` **không** làm JDK proxy tự công bố method này. Muốn gọi method qua JDK proxy, nó phải có trên interface proxy công bố.
- Dù class-based proxy có method, method vẫn cần **pointcut phù hợp và lời gọi qua proxy** để chạy advice.

Nếu muốn caller độc lập với implementation, đưa method business cần dùng vào một interface có ý nghĩa hoặc tách service rõ ràng. Không ép CGLIB chỉ để tránh việc khai báo contract.

### 6.2. `final` class

```java
// Fragment minh họa giới hạn, không thay DefaultTaskService của ví dụ.
public final class FinalTaskService implements TaskService {
    public String process(String id) { return "done:" + id; }
    public String invokeInternally(String id) { return process(id); }
}
```

- JDK proxy vẫn có thể proxy **interface** `TaskService` của một target class `final`.
- CGLIB không thể tạo một subclass của `FinalTaskService`; yêu cầu `proxyTargetClass=true` sẽ thất bại cho target không thể proxy bằng subclass, thường xuất hiện lỗi tạo bean/AOP configuration liên quan `final`.

### 6.3. `final` method, `private` method

Class-based proxy dựa vào override/interception, nên **không** intercept được `final` hoặc `private` method qua cơ chế subclass. Với JDK proxy, method được gọi **qua interface method của proxy** có thể được intercept, kể cả implementation method phía sau được khai báo `final`, miễn cấu hình advisor/pointcut khớp. Vì vậy, đừng kết luận rằng có `final` ở implementation thì **mọi** kiểu Spring proxy đều bất lực.

```java
// Fragment thay thế để nghiên cứu, không thuộc project chạy ban đầu.
public class AlternateTaskService implements TaskService {
    @Override
    public final String process(String id) {
        return "done:" + id;
    }
    @Override
    public String invokeInternally(String id) {
        return this.process(id);
    }
}
```

Khi thay target class trong cả hai cấu hình, JDK proxy vẫn có thể intercept lời gọi interface `process`. CGLIB không thể override method `process` đã `final`; đừng lấy việc bean được proxy hoặc cast thành công làm bằng chứng method đó được advise. `private` method không công bố qua interface và không bị class proxy override.

### 6.4. Self-invocation

Hai cơ chế vẫn có giới hạn sau đối với Spring proxy-based AOP:

```java
@Override
public String invokeInternally(String id) {
    return this.process(id); // bắt đầu từ target, không quay qua Spring proxy
}
```

Đó là lý do chính khiến cả hai phần stdout đều có `adviceCalls=1` sau **hai** business method executions. Nếu cần advice trên `process` khi gọi nội bộ, giải pháp thực dụng là tái cấu trúc lời gọi qua một **Spring bean khác** được inject hoặc thiết kế boundary rõ ràng. `AopContext.currentProxy()` cần `exposeProxy=true` và làm logic phụ thuộc Spring AOP; dùng rất có chủ đích, không coi là giải pháp mặc định.

### 6.5. Một số giới hạn khác

- Class-based proxy có thể gặp vấn đề với module encapsulation (JPMS), classloader, các method không visible từ subclass và constructor/final state nếu thiết kế target phức tạp.
- Spring thường sử dụng Objenesis để không gọi constructor hai lần khi tạo proxy; đừng dùng side effect constructor làm test đếm proxy.
- Với JDK proxy, code chỉ nhìn thấy các method trên interface; Java reflection trên proxy class cũng khác reflection trên target.
- Một service có nhiều interface có thể được JDK proxy triển khai nhiều interface cùng lúc; danh sách chính xác phụ thuộc cấu hình proxy.
- Tên lớp proxy như `jdk.proxy...$Proxy...` và hậu tố `$$SpringCGLIB$$...` thay đổi theo JVM/Spring version, nên không hardcode tên làm test.

## 7. Kiểm tra advisor và target class trên cùng service

Nếu kết quả không giống dự đoán, thêm diagnostics trên chính object `bean`:

```java
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

// Snippet đặt trong Main.inspect khi biến bean đã tồn tại.
System.out.println("proxyType=" + bean.getClass().getName());
System.out.println("targetType=" + AopUtils.getTargetClass(bean).getName());

if (bean instanceof Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println("advisor=" + advisor.getClass().getName());
        System.out.println("advice=" + advisor.getAdvice().getClass().getName());
    }
}
```

`getAdvisors()` trả danh sách advisor gắn trên proxy, **không** cho biết một advisor cụ thể đã thực thi trong mọi lời gọi. Spring còn lọc theo method/target class và đôi khi arguments tại runtime. Trong code ví dụ, counter của `TraceAspect` xác nhận advice thực sự chạy cho `process("A")`.

Với JDK proxy và CGLIB proxy, `AopUtils.getTargetClass(bean)` đều cho `DefaultTaskService` nhưng `instanceof DefaultTaskService` sẽ khác. Đây là bằng chứng trực tiếp để tách biệt **loại target** khỏi **loại proxy runtime**.

Nếu `AopUtils.isAopProxy=false`, kiểm tra có đăng ký aspect bean, bật `@EnableAspectJAutoProxy` trong đúng context, pointcut có khớp bean hay không, và liệu caller đang lấy object ngoài Spring container. Đừng chọn loại proxy khi thậm chí chưa có Spring AOP proxy.

## 8. Kiểm thử tự động với JUnit 5

Ví dụ test chạy hai context riêng, assert kiểu proxy, lời gọi qua interface, giới hạn cast và số lần advice. Tạo file **`src/test/java/demo/ProxyComparisonTest.java`**:

```java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class ProxyComparisonTest {

    @Test
    void jdkProxyHasInterfaceButNotImplementationType() {
        try (var context =
                new AnnotationConfigApplicationContext(JdkProxyConfig.class)) {
            Object bean = context.getBean("taskService");
            TraceAspect aspect = context.getBean(TraceAspect.class);

            assertTrue(AopUtils.isAopProxy(bean));
            assertTrue(AopUtils.isJdkDynamicProxy(bean));
            assertFalse(AopUtils.isCglibProxy(bean));
            assertEquals(DefaultTaskService.class, AopUtils.getTargetClass(bean));
            assertInstanceOf(TaskService.class, bean);
            assertFalse(bean instanceof DefaultTaskService);

            TaskService service = (TaskService) bean;
            assertEquals("done:A", service.process("A"));
            assertEquals("done:B", service.invokeInternally("B"));
            assertEquals(1, aspect.invocationCount());

            assertThrows(ClassCastException.class,
                    () -> { DefaultTaskService impl = (DefaultTaskService) bean; });
        }
    }

    @Test
    void classProxyCanBeCastToImplementation() {
        try (var context =
                new AnnotationConfigApplicationContext(CglibProxyConfig.class)) {
            Object bean = context.getBean("taskService");
            TraceAspect aspect = context.getBean(TraceAspect.class);

            assertTrue(AopUtils.isAopProxy(bean));
            assertFalse(AopUtils.isJdkDynamicProxy(bean));
            assertTrue(AopUtils.isCglibProxy(bean));
            assertEquals(DefaultTaskService.class, AopUtils.getTargetClass(bean));
            assertInstanceOf(TaskService.class, bean);
            assertInstanceOf(DefaultTaskService.class, bean);

            TaskService service = (TaskService) bean;
            assertEquals("done:A", service.process("A"));
            assertEquals("done:B", service.invokeInternally("B"));
            assertEquals(1, aspect.invocationCount());

            DefaultTaskService concrete = (DefaultTaskService) bean;
            assertEquals("implementation-only", concrete.implementationOnly());
        }
    }
}
```

Lệnh chạy:

```bash
mvn test
```

Kết quả kiểm thử **dự kiến**: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`. Nếu môi trường có dependency/configuration khác, cần đọc stack trace/test report thực tế. Test đã assert advisor hoạt động bằng counter, không chỉ xác nhận có proxy.

Ý nghĩa của từng nhóm assertion:

| Assert | Kết luận |
| --- | --- |
| `isAopProxy` | Spring đã tạo AOP proxy |
| `isJdkDynamicProxy` / `isCglibProxy` | Xác nhận đúng loại proxy được yêu cầu |
| `getTargetClass` | Cả hai proxy bọc cùng implementation class |
| `instanceof TaskService` | Giao diện chung dùng được với cả hai |
| `instanceof DefaultTaskService` | Chỉ class proxy có quan hệ kế thừa với implementation |
| `service.process` + `aspect.invocationCount()` | Lời gọi đi qua advice đúng một lần |
| `service.invokeInternally` mà counter không tăng | Giới hạn self-invocation vẫn còn với cả hai |
| `implementationOnly()` | CGLIB cho phép truy cập method chỉ có ở implementation khi dùng reference phù hợp |

Một unit test tự `new DefaultTaskService()` mà không đi qua Spring context **không** kiểm tra được loại proxy hay aspect thực tế.

## 9. Spring Boot và cấu hình AOP dùng chung

Trong Spring Framework thuần, `@EnableAspectJAutoProxy(proxyTargetClass=false)` là cấu hình điển hình cho lựa chọn interface-based proxy khi target có interface. Spring Boot có auto-configuration của AOP và property:

```properties
# Spring Boot: yêu cầu class-based proxies.
spring.aop.proxy-target-class=true
```

Hoặc khi muốn ưu tiên interface-based proxies:

```properties
spring.aop.proxy-target-class=false
```

Các ứng dụng Boot thường sử dụng **class-based proxy theo mặc định** khi AOP auto-configuration hoạt động, nhưng phải kiểm tra Spring Boot version, dependencies, cấu hình đã override và các auto-proxy creator khác trong context. Ví dụ so sánh ở mục 4 là **Spring Framework thuần** để không lệ thuộc Boot auto-configuration.

Với Java config có nhiều cơ chế:

```java
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableTransactionManagement(proxyTargetClass = false)
```

Đây là **snippet minh họa**, không phải hai annotation độc lập cần thêm vào project đang chạy. Spring có thể hợp nhất các auto-proxy creator trong cùng context, chọn thiết lập mạnh hơn (`proxyTargetClass=true`). Do đó **không** đặt hai cờ trái nhau trong cùng một context rồi kỳ vọng từng bean tự dùng loại proxy khác nhau chỉ dựa vào annotation nguồn.

Trong một ứng dụng có `@Transactional`, method security, caching hay custom aspects, thay đổi proxy type có thể ảnh hưởng cách inject/cast ở nhiều bean. Trước khi thay đổi trên production, kiểm tra các bean đang phụ thuộc vào implementation type và regression test các đường gọi quan trọng.

## 10. So sánh hiệu năng cho đúng

Không thể kết luận “JDK proxy luôn nhanh hơn” hoặc “CGLIB proxy luôn nhanh hơn” chỉ từ một bài thử ngắn. Hai cơ chế có chi phí dispatch khác nhau và thay đổi theo JDK/Spring version; trong hệ thống thực, thời gian business method, transaction, DB/HTTP calls, advisor chain và JIT thường ảnh hưởng mạnh hơn.

Khi cần benchmark thật:

1. Dùng **cùng** target logic và **cùng** số lượng advice cho hai context.
2. Warm-up đầy đủ trước khi đo; không in `System.out` trong vòng đo vì chi phí logging lấn át proxy overhead.
3. Dùng JMH nếu cần so sánh microbenchmark; tránh tự dùng một vòng `System.nanoTime()` rồi tuyên bố kết quả đáng tin.
4. Tách kết quả theo proxy type, method signature, số lượng advisors và môi trường JDK.
5. Đo cả latency p95/p99 ở nghiệp vụ thật nếu proxy nằm trong request path quan trọng.

**Mục tiêu lựa chọn proxy trước tiên là đúng semantics và API type**, không tối ưu theo phỏng đoán. Với service được thiết kế qua interface, dùng JDK proxy có thể rất phù hợp. Khi cần expose method ở implementation, có ràng buộc legacy hoặc không có interface, class-based proxy là công cụ có ích, nhưng phải xét giới hạn Java subclassing.

## 11. Các lỗi thường gặp trong bài thực hành

| Tình huống | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Cả hai context đều ra `isCglibProxy=true` | Hai config bị import vào **một** context; cấu hình khác ép class proxy; Boot auto-config ảnh hưởng | Tạo hai `ApplicationContext` độc lập như ví dụ và đo lại |
| Không thấy advice trong cả hai | Chưa bật `@EnableAspectJAutoProxy`, aspect chưa thành bean, pointcut không khớp | Kiểm tra registration/context và `AopUtils.isAopProxy` |
| `ClassCastException` ở nhánh JDK | Caller cast interface proxy sang implementation | Inject/gọi qua business interface |
| `BeanNotOfRequiredTypeException` khi inject implementation | Runtime JDK proxy không assignable sang class target | Đổi dependency sang interface; cân nhắc CGLIB nếu bắt buộc |
| Tưởng CGLIB giải quyết self-invocation | Lời gọi `this.process()` vẫn nằm trong target | Thiết kế lời gọi qua bean proxy khác, hoặc refactor boundary |
| Lỗi class proxy với lớp `final` | CGLIB cần subclass target | Bỏ `final` nếu đúng thiết kế hoặc dùng interface/JDK proxy |
| Advice không chạy trên `final` method ở CGLIB | Method không thể override | Đổi thiết kế method/proxy type; kiểm tra advice trên đường gọi interface nếu dùng JDK |
| Có interface nhưng Spring lại chọn CGLIB | `proxyTargetClass=true` từ config hoặc auto-configuration | Đọc cấu hình effective và đo proxy runtime |
| Thử gọi method chỉ ở implementation qua JDK proxy | Method không thuộc interface proxy | Thiết kế interface phù hợp hoặc dùng class-based proxy có chủ đích |
| Test so sánh bị lẫn số counter | Dùng chung một instance aspect/context hoặc biến static | Tạo aspect bean và context riêng cho mỗi nhánh |
| Xem tên target class rồi nghĩ cast được | `AopUtils.getTargetClass` nói về target, không nói runtime inheritance | Dùng `instanceof`, `isJdkDynamicProxy` và `isCglibProxy` |
| Bản chạy IDE khác Spring Boot deployment | Có config, advisor và module/classloader khác | So dependency tree, property, context và proxy diagnostics ở môi trường thật |

## 12. Bài tập mở rộng

1. **Bỏ interface:** tạo service không triển khai `TaskService`, đổi caller lấy theo implementation class và kiểm tra Spring chọn class proxy khi cần advice. Giải thích vì sao không thể dùng JDK proxy nếu không có interface phù hợp.
2. **Thêm method chỉ ở implementation:** đưa `implementationOnly()` vào pointcut riêng, gọi qua CGLIB proxy rồi xem advice có chạy không. So sánh với nhánh JDK không có method này trong interface.
3. **Thêm `final` lên `process`:** giữ `TaskService` interface; dự đoán JDK proxy và class proxy khác nhau ở đâu, sau đó xác minh advice count (đặt breakpoint nếu cần).
4. **Thêm `@Transactional` hoặc cache advisor:** kiểm tra danh sách `((Advised) bean).getAdvisors()` và xem cấu hình proxy type có bị hợp nhất không. Chỉ chạy khi project có infrastructure phù hợp.
5. **Thay self-invocation bằng lời gọi qua bean thứ hai:** tách phần `process` sang một Spring service khác, inject interface của service đó và so sánh advice count.
6. **Chạy dưới Spring Boot:** bật/tắt `spring.aop.proxy-target-class`, kiểm tra loại proxy được tạo bởi đúng version Boot và dependency hiện dùng.
7. **So benchmark đúng cách:** nếu thật sự cần tối ưu, tạo JMH benchmark không in log, có warm-up, cùng chain/target, báo môi trường JDK cùng cả phân bố kết quả.

Khi làm bài tập, giữ nguyên dữ liệu input và cùng aspect logic để **biến đang thử** rõ ràng. Đừng đồng thời đổi pointcut, method visibility, target instance và strategy rồi gán mọi khác biệt cho JDK/CGLIB.

## 13. Kiểm tra cuối trước khi đưa vào ứng dụng

- [ ] Các service được lấy từ Spring container, không tự `new` khi cần AOP advice.
- [ ] Xác nhận bean thật sự là proxy bằng `AopUtils.isAopProxy`.
- [ ] Xác nhận cụ thể `isJdkDynamicProxy` hoặc `isCglibProxy`.
- [ ] `AopUtils.getTargetClass` được dùng để xem target; `instanceof` để kiểm tra khả năng cast.
- [ ] Caller ưu tiên dùng business interface khi hợp đồng cho phép.
- [ ] Nếu dùng CGLIB, đã kiểm tra `final` class/method và Java module visibility.
- [ ] Không phụ thuộc vào self-invocation để kích hoạt Spring AOP advice.
- [ ] Không có config proxy trái nhau được nhập nhầm vào cùng context.
- [ ] `@Transactional`, cache/security và custom aspects có regression tests nếu đổi `proxyTargetClass`.
- [ ] Kiểm tra loại proxy và advice invocation trong môi trường deploy thực tế.

**Kết luận kỹ thuật:** JDK dynamic proxy và CGLIB trong Spring AOP có thể mang **cùng lời gọi business và cùng advice**, nhưng cách proxy công bố kiểu Java khác nhau. JDK proxy phù hợp khi caller phụ thuộc interface; CGLIB phù hợp khi phải proxy lớp, song chịu giới hạn subclass/override. Cả hai vẫn dùng Spring proxy và do đó **không tự sửa self-invocation**.

## 14. Tài liệu chính thức

- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): lựa chọn JDK/CGLIB, giới hạn `final` và self-invocation.
- [Spring Framework 6.2 — AOP Proxies](https://docs.spring.io/spring-framework/reference/6.2/core/aop/introduction-proxies.html): cơ chế proxy và lý do chọn theo interface/lớp.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): `@EnableAspectJAutoProxy` và yêu cầu `aspectjweaver` trên classpath.
- [Spring Framework — AopUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/AopUtils.html): xác định AOP, JDK, CGLIB proxy và target class.
- [Spring Framework — Advised Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/framework/Advised.html): API quan sát advisor.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-practical-workshop-003">Tạo annotation riêng và binding dữ liệu vào advice</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo annotation riêng và binding dữ liệu vào advice trong Spring AOP

## 1. Bài toán: aspect đọc được cả metadata và dữ liệu method

Trong Spring, ta thường muốn áp một hành vi cắt ngang chỉ cho một số method nghiệp vụ, ví dụ ghi audit khi xử lý thanh toán. Nếu chỉ viết pointcut theo tên method như `execution(* *..PaymentService.pay(..))`, rules có thể bị ràng buộc quá chặt với tên method. Một **custom annotation** giúp method tự khai báo ý định và metadata, ví dụ action cần audit. Spring AOP có thể **match method được đánh dấu**, **bind instance annotation** vào tham số của advice, rồi **bind arguments của method** để advice sử dụng.

Một tình huống cụ thể:

~~~java
@TrackedAction("PAY")
public String pay(String orderId, long cents) {
    return "paid:" + orderId + ":" + cents;
}
~~~

Ta muốn một `@Around` advice nhận đồng thời:
- `TrackedAction tracked`: **annotation instance** ở method, có thể đọc `tracked.value()`.
- `String orderId` và `long cents`: **giá trị arguments ở lời gọi thực tế**, không phải tên/thông số compile-time cố định.
- `ProceedingJoinPoint pjp`: thao tác tiếp tục method qua `pjp.proceed()`, lấy kết quả/exception.

Kết quả học tập:
1. Tạo đúng annotation bằng `@Retention(RetentionPolicy.RUNTIME)` và `@Target(ElementType.METHOD)`.
2. Dùng `@annotation(tracked)` của Spring AOP để match **và bind** metadata.
3. Dùng `args(orderId,cents)` để bind method arguments, kết hợp `argNames` để rõ ràng khi build không bật `-parameters`.
4. Tạo Spring bean aspect, bật `@EnableAspectJAutoProxy` và chứng minh advice chạy qua **proxy**.
5. Kiểm thử đường thành công, exception, method không đánh dấu, self-invocation và object tạo bằng `new`.
6. Phân biệt `@annotation`, `@within`, `@target`, `@args` và `args` nhằm tránh điểm cắt quá rộng hoặc bind nhầm đối tượng.
7. Đưa ra quy tắc an toàn về logging tham số, hiệu năng, advice ordering và các trường hợp proxy không hoạt động.

Bài này tập trung vào **Spring Aspect/Spring AOP**; các khái niệm annotation Java hoặc AspectJ chung chỉ giải thích đủ để áp dụng vào Spring.

## 2. Cơ chế trong Spring AOP

Spring AOP chủ yếu intercept **method execution trên Spring-managed proxy**. Một khai báo `@Aspect` chưa tự tác động đến mọi object Java; nó phải được Spring đăng ký là bean, và context phải có auto-proxy infrastructure. Khi caller gọi method trên proxy:
1. Spring xác định method/target type, các advisors và pointcuts thích hợp.
2. Điểm cắt `@annotation(tracked)` yêu cầu method join point mang annotation `@TrackedAction`.
3. Spring bind annotation được match vào tham số `tracked` của advice.
4. Điểm cắt `args(orderId,cents)` bind **hai argument thực tế** ở thời điểm gọi vào `orderId`, `cents`.
5. Advice có thể gọi `pjp.proceed()` để method gốc thực thi, rồi xem return value hoặc bắt exception.
6. Spring tiếp tục chuỗi các advisors khác theo thứ tự thực tế và trả kết quả cho caller.

~~~text
Caller
  |
  +-- paymentService.pay("OD-17", 1200) [Spring AOP proxy]
         |
         +-- execution(* demo.binding.PaymentService.*(..))
         |       chọn tập method/class
         |
         +-- @annotation(tracked)
         |       match method có @TrackedAction, bind instance annotation
         |
         +-- args(orderId,cents)
         |       bind "OD-17", 1200L
         |
         +-- @Around advice
         |       read tracked.value() == "PAY"
         |       record "before"
         |       pjp.proceed()
         |          |
         |          +-- target PaymentService.pay(...)
         |       record "ok" hoặc "error"
         |
         +-- return result / propagate exception
~~~

Trong đoạn trên, annotation không tự viết log và không tự cấu hình AOP. **Custom annotation là metadata**, còn advice là **Spring bean có @Aspect**, pointcut chọn method và proxy quyết định đường lời gọi có bị chặn hay không.

## 3. Annotation riêng cần cấu hình như thế nào?

Một custom annotation cho method Spring AOP thường có:

~~~java
package demo.binding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrackedAction {
    String value();
}
~~~

**`@Retention(RUNTIME)`** giữ metadata khi chương trình chạy, để hạ tầng AOP/reflection có thể quan sát annotation; nếu chỉ dùng `SOURCE` hoặc `CLASS`, không được trông đợi Spring AOP đọc annotation method khi runtime.

**`@Target(METHOD)`** giới hạn annotation đúng vào method; nếu cần đánh dấu class thì đó là một thiết kế khác và nên dùng pointcut phù hợp như `@within`/`@target`. Đừng đặt `@Target(TYPE)` rồi mong `@annotation` luôn match method annotation.

Phần tử `String value()` tạo metadata có kiểu, cho phép `@TrackedAction("PAY")`. Có thể bổ sung `category()`, `logArgs() default false` hay `riskLevel()` nếu nhu cầu thật sự có, nhưng tránh biến annotation thành nơi chứa bí mật hoặc state mutable của request.

Java annotation không chứa logic tự chạy: **không có lời gọi Spring AOP, không có proxy và không có aspect thì annotation chỉ là metadata**.

## 4. Điểm cắt Spring hỗ trợ và ý nghĩa binding

| Biểu thức | Điều kiện match / binding | Đối tượng được đưa vào advice |
| --- | --- | --- |
| `execution(* demo.binding.PaymentService.*(..))` | Method execution trong phạm vi target type/method đã chọn | Không tự bind values; giúp scope rõ ràng |
| `@annotation(tracked)` | Method join point được đánh dấu bằng annotation phù hợp với kiểu `tracked` | **Annotation instance** `TrackedAction` |
| `args(orderId,cents)` | Argument list có 2 vị trí phù hợp các kiểu của biến bind | **Runtime argument values** |
| `@within(TypeAnnotation)` | Method thuộc phạm vi type có annotation tương ứng | Có thể dùng dạng binding để nhận type annotation |
| `@target(TypeAnnotation)` | Target object có type với annotation tương ứng | Có thể dùng dạng binding |
| `@args(ArgumentTypeAnnotation)` | Runtime type của argument mang annotation tương ứng | **Annotation trên kiểu argument**, không phải annotation đặt trên method |
| `this(proxy)` | Proxy Spring phù hợp kiểu | Reference của **proxy** |
| `target(bean)` | Object target phù hợp kiểu | Reference của **target object** |
| `bean(*Service)` | Spring bean có tên match pattern (Spring-specific) | Tên bean là điều kiện match, không tự bind values |

**Hai điều rất hay bị nhầm:**

1. `args(orderId,cents)` khác `@args(Whatever)`: `args` nhìn **giá trị/kiểu arguments của invocation**; `@args` xét **annotation trên runtime class của arguments**. Ví dụ `@TrackedAction` gắn trên method phải dùng `@annotation`, không phải `@args`.
2. `@annotation(tracked)` khác `@annotation(demo.binding.TrackedAction)`. Dạng với **biến `tracked`** đồng thời bind annotation vào lời gọi advice. Dạng với **kiểu annotation** chỉ ràng buộc việc match, không tự tạo biến Java `tracked` để code sử dụng.

Trong Spring proxy AOP, designator `call(...)` của AspectJ đầy đủ không được hỗ trợ như một Spring AOP pointcut. Spring AOP match **method execution**, không tùy ý weave bytecode tại mọi call site.

## 5. Cách đặt tên để bind chính xác: argNames và -parameters

Advice method:

~~~java
@Around(
    value = "execution(* demo.binding.PaymentService.*(..))"
          + " && @annotation(tracked)"
          + " && args(orderId,cents)",
    argNames = "tracked,orderId,cents"
)
public Object audit(
        ProceedingJoinPoint pjp,
        TrackedAction tracked,
        String orderId,
        long cents) throws Throwable {
    return pjp.proceed();
}
~~~

Có ba tham số mà pointcut thực sự bind: `tracked`, `orderId` và `cents`. `ProceedingJoinPoint` là tham số đặc biệt đầu tiên nên **không cần nằm trong `argNames`**. Các tên trong `argNames` phải nhất quán với biến trong biểu thức và tham số advice.

Nếu biên dịch Java bằng `javac -parameters`, Spring có thể xác định tên tham số từ reflection. Spring cũng có các cơ chế suy luận tên từ annotation expression. Tuy nhiên, khi viết bài hướng dẫn hoặc thư viện dùng bởi nhiều build systems, đặt `argNames` tường minh làm ví dụ ít phụ thuộc tùy chọn compiler.

Các lỗi thường gặp:
- Viết `@annotation(meta)` rồi khai báo tham số advice tên `tracked` nhưng không cung cấp `argNames` và không có thông tin parameter name: Spring có thể không bind đúng hoặc báo lỗi khi tạo advisor.
- Viết `argNames="pjp,tracked,orderId,cents"` trong trường hợp đầu tiên là `ProceedingJoinPoint` nhưng lại dựa vào cơ chế Spring bỏ qua tên join point đầu: dùng mẫu rõ ràng ở trên.
- Đổi thứ tự `args(orderId,cents)` thành `args(cents,orderId)` nhưng method thực tế nhận String rồi long: expression không còn mô tả đúng argument positions/types.
- Dùng một pointcut quá rộng cho nhiều method annotation có **số tham số khác nhau** nhưng vẫn ràng buộc `args(orderId,cents)`: những method signature khác sẽ không match (hoặc sẽ không bind như kỳ vọng).
- Thiếu `@Retention(RUNTIME)` khiến annotation method không được quan sát đúng tại runtime.

Không cố giải quyết mọi signature bằng cast từ `Object[]` sang hai giá trị nếu ứng dụng có nhiều kiểu method; khi signature khác nhau, cần pointcut/advice khác hoặc thiết kế một advice tổng quát đọc `pjp.getArgs()` và kiểm tra kiểu an toàn.

## 6. Ví dụ hoàn chỉnh Spring Framework 6.1, Java 17+

Ví dụ có đầy đủ annotation, target bean, aspect, recorder, configuration và main. Nó chủ động **không dùng Spring Boot** để minh họa rõ cách hạ tầng Spring AOP được bật. Hai method có annotation được intercept; method `preview(...)` không có annotation nên đi thẳng qua target, và `payFromInside(...)` minh họa self-invocation bypass.

### 6.1. Cấu trúc tệp

~~~text
build.gradle
src/main/java/demo/binding/
    TrackedAction.java
    PaymentService.java
    AuditEvents.java
    TrackingAspect.java
    DemoConfig.java
    Main.java
src/test/java/demo/binding/
    TrackedActionTests.java
~~~

### 6.2. Gradle

**`build.gradle`**

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

    testImplementation 'org.springframework:spring-test:6.1.14'
    testImplementation platform('org.junit:junit-bom:5.11.3')
    testImplementation 'org.junit.jupiter:junit-jupiter'
}

application {
    mainClass = 'demo.binding.Main'
}

test {
    useJUnitPlatform()
}
~~~

Spring `spring-context` giúp tạo ApplicationContext, `spring-aop` cung cấp proxy/advisor, `aspectjweaver` cung cấp annotation và pointcut parsing mà Spring AOP cần. Không cần `-javaagent`: ví dụ là **Spring proxy AOP**, không phải AspectJ load-time weaving.

Phiên bản trên cố định để ví dụ nhất quán. Trong Spring Boot đang sử dụng, ưu tiên BOM/starter phiên bản tương thích thay vì trộn tùy tiện các Spring Framework artifacts.

### 6.3. Custom annotation

**`src/main/java/demo/binding/TrackedAction.java`**

~~~java
package demo.binding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrackedAction {
    String value();
}
~~~

### 6.4. Business class: đánh dấu hai method

**`src/main/java/demo/binding/PaymentService.java`**

~~~java
package demo.binding;

public class PaymentService {

    @TrackedAction("PAY")
    public String pay(String orderId, long cents) {
        return "paid:" + orderId + ":" + cents;
    }

    @TrackedAction("REJECT")
    public String reject(String orderId, long cents) {
        throw new IllegalArgumentException("blocked:" + orderId);
    }

    public String preview(String orderId, long cents) {
        return "preview:" + orderId;
    }

    public String payFromInside(String orderId, long cents) {
        return this.pay(orderId, cents);
    }
}
~~~

`pay` và `reject` có cùng hai arguments (String, long), nên cùng một `args(orderId,cents)` binding là hợp lý. `preview` không có `@TrackedAction`, còn `payFromInside` gọi `this.pay(...)` **từ target object**.

`reject` nhận `cents` để có signature giống `pay`; trong ví dụ nó cố ý throw nhằm thử `@Around` xử lý/rethrow exception. Trong production, method audit không nên tự suy đoán kết quả nghiệp vụ từ tên method.

### 6.5. Recorder giúp test không phụ thuộc console output

**`src/main/java/demo/binding/AuditEvents.java`**

~~~java
package demo.binding;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuditEvents {

    private final CopyOnWriteArrayList<String> events =
            new CopyOnWriteArrayList<>();

    public void add(String event) {
        events.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }
}
~~~

Recorder giữ một danh sách events để minh họa. `CopyOnWriteArrayList` thuận tiện cho demo số lượng ghi ít; **không phải lựa chọn mặc định cho hot path audit production**. Production cần hệ thống audit/logging đáng tin cậy, bảo vệ dữ liệu và xác định transaction semantics.

### 6.6. Aspect bind annotation + args vào @Around

**`src/main/java/demo/binding/TrackingAspect.java`**

~~~java
package demo.binding;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TrackingAspect {

    private final AuditEvents events;

    public TrackingAspect(AuditEvents events) {
        this.events = events;
    }

    @Around(
        value = "execution(* demo.binding.PaymentService.*(..))"
              + " && @annotation(tracked)"
              + " && args(orderId,cents)",
        argNames = "tracked,orderId,cents"
    )
    public Object track(
            ProceedingJoinPoint pjp,
            TrackedAction tracked,
            String orderId,
            long cents) throws Throwable {

        String action = tracked.value();
        events.add("before:" + action + ":" + orderId + ":" + cents);

        try {
            Object result = pjp.proceed();
            events.add("ok:" + action);
            return result;
        } catch (Throwable failure) {
            events.add("error:" + action + ":"
                    + failure.getClass().getSimpleName());
            throw failure;
        }
    }
}
~~~

**Giải thích từng phần:**
1. `execution(* demo.binding.PaymentService.*(..))` giới hạn type method ở lớp `PaymentService`, tránh aspect vô tình áp lên cả những bean khác.
2. `@annotation(tracked)` match annotation `TrackedAction` và bind đối tượng annotation vào tham số `tracked`.
3. `args(orderId,cents)` bind **argument values của lời gọi** vào `String orderId` và `long cents` theo đúng vị trí.
4. `argNames="tracked,orderId,cents"` làm rõ mapping. `pjp` đứng đầu, không cần liệt kê.
5. `tracked.value()` đọc metadata tĩnh của annotation; `orderId` và `cents` là dữ liệu động tùy từng invocation.
6. `pjp.proceed()` chuyển lời gọi sang target qua advisor chain; return value phải được giữ nguyên.
7. Khi target throw, aspect ghi sự kiện lỗi rồi **throw lại cùng Throwable**, tránh nuốt exception hoặc thay đổi hợp đồng nghiệp vụ ngoài chủ đích.

**Lưu ý bảo mật:** ví dụ dùng `orderId` và `cents` để học binding, không dùng thông tin nhạy cảm. Trong sản phẩm thật, không đưa mật khẩu, token, số thẻ, raw payment payload hay dữ liệu cá nhân vào log audit. Tùy yêu cầu, chỉ ghi mã hoạt động và định danh đã được cho phép/ẩn danh.

### 6.7. Đăng ký các bean và bật Spring AOP

**`src/main/java/demo/binding/DemoConfig.java`**

~~~java
package demo.binding;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class DemoConfig {

    @Bean
    PaymentService paymentService() {
        return new PaymentService();
    }

    @Bean
    AuditEvents auditEvents() {
        return new AuditEvents();
    }

    @Bean
    TrackingAspect trackingAspect(AuditEvents events) {
        return new TrackingAspect(events);
    }
}
~~~

`@EnableAspectJAutoProxy(proxyTargetClass = true)` yêu cầu class-based CGLIB proxy. Ta chọn cách này vì `PaymentService` không có interface nghiệp vụ và muốn lấy bean bằng `PaymentService.class`. `TrackingAspect` là bean hợp lệ nhờ `@Bean`; không cần `@Component`/component scanning.

Trong Spring Boot có thể dùng `@Aspect @Component` cho aspect và Spring Boot AOP starter; quan trọng là aspect **phải tồn tại trong đúng ApplicationContext** của target, auto-proxy creator được bật và bean không được tạo quá sớm để bỏ qua post-processing.

### 6.8. Chạy chương trình và kiểm tra stdout

**`src/main/java/demo/binding/Main.java`**

~~~java
package demo.binding;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(DemoConfig.class)) {

            PaymentService service =
                    context.getBean(PaymentService.class);
            AuditEvents events =
                    context.getBean(AuditEvents.class);

            System.out.println("spring proxy=" + AopUtils.isAopProxy(service));
            System.out.println(service.pay("OD-17", 1200L));
            System.out.println(service.preview("OD-17", 0L));

            try {
                service.reject("OD-18", 999L);
            } catch (IllegalArgumentException ex) {
                System.out.println("caught=" + ex.getMessage());
            }

            System.out.println("events=" + events.snapshot());
        }
    }
}
~~~

Chạy từ project có Gradle wrapper:

~~~bash
./gradlew run
~~~

PowerShell trên Windows:

~~~powershell
.\gradlew.bat run
~~~

**Output của các lệnh `System.out.println` trong ví dụ** (bỏ qua log Spring/Gradle):

~~~text
spring proxy=true
paid:OD-17:1200
preview:OD-17
caught=blocked:OD-18
events=[before:PAY:OD-17:1200, ok:PAY, before:REJECT:OD-18:999, error:REJECT:IllegalArgumentException]
~~~

Các quan sát quan trọng:
- `service.pay(...)` chạy qua proxy, annotation và hai arguments được bind.
- `preview(...)` vẫn chạy nghiệp vụ nhưng không tạo audit event vì không có annotation.
- `reject(...)` vẫn throw exception; aspect quan sát lỗi và rethrow, caller bắt lỗi.
- Các metadata `PAY`/`REJECT` đến từ **annotation**, còn orderId/cents đến từ **invocation arguments**.
- Không có event nào chứng minh tự động persistence; recorder chỉ minh họa advice thực thi.

Ví dụ chạy bằng Spring AOP proxy thuần; không sửa class bytecode như AspectJ LTW.

## 7. JUnit 5: kiểm thử annotation binding trên Spring proxy thật

Một unit test chỉ tạo `new PaymentService()` sẽ không kiểm chứng auto-proxy creator và advisor. Để chắc chắn, dùng Spring TestContext và lấy bean thực.

**`src/test/java/demo/binding/TrackedActionTests.java`**

~~~java
package demo.binding;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(DemoConfig.class)
class TrackedActionTests {

    @Autowired
    PaymentService service;

    @Autowired
    AuditEvents events;

    @BeforeEach
    void reset() {
        events.clear();
    }

    @Test
    void readsAnnotationValueAndMethodArguments() {
        assertTrue(AopUtils.isAopProxy(service));
        assertTrue(AopUtils.isCglibProxy(service));

        assertEquals("paid:OD-17:1200", service.pay("OD-17", 1200L));
        assertEquals(List.of(
            "before:PAY:OD-17:1200",
            "ok:PAY"
        ), events.snapshot());
    }

    @Test
    void methodWithoutAnnotationDoesNotEnterAdvice() {
        assertEquals("preview:OD-17", service.preview("OD-17", 0L));
        assertTrue(events.snapshot().isEmpty());
    }

    @Test
    void methodExceptionIsRecordedAndRethrown() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.reject("OD-18", 999L)
        );
        assertEquals("blocked:OD-18", ex.getMessage());
        assertEquals(List.of(
            "before:REJECT:OD-18:999",
            "error:REJECT:IllegalArgumentException"
        ), events.snapshot());
    }

    @Test
    void selfInvocationDoesNotReenterSpringProxyAdvice() {
        assertEquals("paid:OD-19:100", service.payFromInside("OD-19", 100L));
        assertTrue(events.snapshot().isEmpty());
    }

    @Test
    void rawObjectCreatedWithNewIsNotAdvised() {
        PaymentService raw = new PaymentService();
        assertFalse(AopUtils.isAopProxy(raw));
        assertEquals("paid:OD-20:200", raw.pay("OD-20", 200L));
        assertTrue(events.snapshot().isEmpty());
    }
}
~~~

Chạy test:

~~~bash
./gradlew test --tests demo.binding.TrackedActionTests
~~~

Windows:

~~~powershell
.\gradlew.bat test --tests demo.binding.TrackedActionTests
~~~

Nếu các method, aspect và Spring config giống như ví dụ, kỳ vọng **5 test passed**; đây là kết quả dự kiến dựa trên assertions, không phải báo cáo đã thực thi build trong môi trường của người đọc.

**Điểm đáng giá của bộ test:** có *positive control* — method `pay` được proxy, trả đúng kết quả và recorder nhận event — rồi mới kiểm các negative cases. Nếu mọi test chỉ thấy recorder rỗng, có thể đơn giản là aspect chưa được đăng ký, không phải một kết luận đúng về self-invocation.

## 8. @Before đơn giản nếu chỉ cần đọc metadata và arguments

Không phải mọi case đều cần `@Around`. Nếu chỉ cần một sự kiện trước khi gọi method, có thể chọn `@Before`:

~~~java
@Before(
    value = "execution(* demo.binding.PaymentService.*(..))"
          + " && @annotation(tracked)"
          + " && args(orderId,cents)",
    argNames = "tracked,orderId,cents"
)
public void beforeTrackedAction(
        TrackedAction tracked, String orderId, long cents) {
    System.out.println(
        "action=" + tracked.value()
        + ", order=" + orderId
        + ", amount=" + cents
    );
}
~~~

Đây là **snippet bổ sung trong class `@Aspect`**, cần import `org.aspectj.lang.annotation.Before`. Đừng thêm nó đồng thời với `@Around` trong demo rồi đòi output mục 6 giữ nguyên, vì khi cả hai match, mỗi invocation có **hai advice**. Nếu chỉ đo số lần gọi hoặc validate metadata đầu vào, `@Before` dễ đọc hơn. Khi cần sửa return value, short-circuit, retry hoặc quan sát exception trong cùng advice, `@Around` mới phù hợp nhưng cũng dễ làm thay đổi behavior hơn.

**Không gọi `pjp.proceed()` trong `@Before`** — API đó chỉ có trong `@Around` với `ProceedingJoinPoint`. `@Before` nhận `JoinPoint` thông thường nếu cần metadata join point.

## 9. Điểm khác nhau giữa binding bằng args và pjp.getArgs()

Với một signature đã biết, `args(orderId,cents)` giúp advice có type-safe parameters; không phải tự cast:

~~~java
@Around(
    value = "@annotation(tracked) && args(orderId,cents)",
    argNames = "tracked,orderId,cents"
)
public Object aroundTyped(
        ProceedingJoinPoint pjp,
        TrackedAction tracked,
        String orderId,
        long cents) throws Throwable {
    return pjp.proceed();
}
~~~

Đây là **snippet phương án thay thế**, không phải thay thế nguyên vẹn Spring project. Expression này có thể match method khác trong ứng dụng có cùng annotation và signature, vì không có `execution(...)` để giới hạn package/class. Khi triển khai thật, thêm phần scope rõ ràng.

Nếu một custom annotation gắn trên **nhiều method có signatures khác nhau**, muốn đọc tất cả arguments có thể dùng:

~~~java
@Around(
    value = "execution(* demo.binding..*(..)) && @annotation(tracked)",
    argNames = "tracked"
)
public Object aroundAnySignature(
        ProceedingJoinPoint pjp,
        TrackedAction tracked) throws Throwable {

    Object[] arguments = pjp.getArgs();
    // Xử lý arguments theo policy an toàn, không log mặc định toàn bộ.
    return pjp.proceed();
}
~~~

Đây cũng là **đoạn thay thế**, không chạy đồng thời với `track` của demo. `pjp.getArgs()` trả argument objects (primitive được boxing), không tự mang tên Java của tham số. Muốn suy ra parameter names cần metadata/`-parameters` hoặc annotation metadata riêng; **không giả định danh sách Object[] chứa tên tham số**.

Nếu dùng `pjp.proceed(new Object[] { ... })` để sửa arguments trong `@Around`, cần tuân theo hợp đồng AspectJ/Spring cho argument binding và số lượng phần tử; đây là kỹ thuật có thể làm thay đổi nghiệp vụ, không cần thiết chỉ để ghi audit.

## 10. @annotation, @within, @target: chọn annotation đúng vị trí

Giả sử có hai kiểu annotation:
- `@TrackedAction` đặt trên method như demo.
- `@TrackedComponent` (nếu thiết kế thêm) đặt trên type/class.

Những pointcut sau **không tương đương**:

| Nhu cầu | Pointcut | Lưu ý |
| --- | --- | --- |
| Method được đánh dấu riêng | `@annotation(demo.binding.TrackedAction)` | Kiểm tra method annotation |
| Bind instance annotation method | `@annotation(tracked)` với advice parameter `TrackedAction tracked` | Match + lấy metadata |
| Method nằm trong type có annotation | `@within(demo.binding.TrackedComponent)` | Nhìn annotation trên type của lexical method scope |
| Target runtime type mang annotation | `@target(demo.binding.TrackedComponent)` | Xem annotation trên type target |
| Argument runtime class mang annotation | `@args(demo.binding.TrackedComponent)` | Không đọc method annotation |
| Bind argument value | `args(orderId,..)` với advice parameter tương ứng | Không yêu cầu class của argument mang annotation |

Ví dụ này không có `@TrackedComponent`; các expression liên quan là **minh họa về lựa chọn designator**, không chạy được nếu không tự khai báo annotation type đó.

### Annotation trên interface hoặc superclass

Trong Spring, JDK proxy và CGLIB khác nhau về cách thể hiện method/runtime type. Khi annotation nằm **trên implementation method**, như demo, class-based proxy dễ quan sát hành vi mong đợi. Nếu chỉ gắn annotation trên **interface method** hoặc chỉ trên **superclass**, đừng mặc định các pointcut trong mọi phiên bản/cấu hình proxy sẽ nhận đúng cùng annotation object mà không thử nghiệm.

Java `@Inherited` có semantics chủ yếu với **type annotation**, không bảo đảm method annotation tự "kế thừa" theo cách mọi API reflection hoặc AspectJ pointcut đều xử lý giống nhau. Khi thiết kế thư viện chung, hãy:
1. Quy định đặt annotation ở interface hay implementation và bám một convention.
2. Test ít nhất JDK/CGLIB mà ứng dụng thực sự sử dụng.
3. Xem xét Spring `AnnotatedElementUtils`/`MergedAnnotations` khi cần merged/meta-annotation lookup ở mức Spring, nhưng **không tự đồng nhất chúng với binding của `@annotation`**.
4. Tránh gắn annotation ở cả interface và class với attributes trái nhau mà không có policy ưu tiên.

## 11. Các lỗi không chạy advice dễ gặp

| Triệu chứng | Nguyên nhân cần kiểm tra | Cách xử lý |
| --- | --- | --- |
| Method có `@TrackedAction` nhưng không có event | Aspect chưa đăng ký hoặc chưa bật `@EnableAspectJAutoProxy` | Kiểm tra aspect bean và AOP config trong đúng context |
| Bean tồn tại nhưng không phải proxy | Khởi tạo quá sớm, advisor không match hoặc container khác | `AopUtils.isAopProxy`, bean ownership và BPP logs |
| Không bind được tên biến `tracked` | `argNames` thiếu/sai hoặc build không lưu parameter names | Dùng `argNames="tracked,orderId,cents"` đồng nhất pointcut và signature |
| Có annotation mà pointcut không match | Annotation target không phải METHOD, thiếu runtime retention, hoặc scope `execution` sai | Kiểm tra annotation declaration, actual target method |
| Advice bị bỏ qua trên overload khác | `args(...)` ràng buộc số lượng/kiểu argument khác | Điều chỉnh signature pointcut hoặc chuyển sang `pjp.getArgs()` an toàn |
| Gọi `this.pay(...)` không vào advice | Self-invocation bypass Spring proxy | Tách bean hoặc gọi qua injected proxy |
| `new PaymentService().pay(...)` không vào advice | Object ngoài Spring container | Lấy Spring bean, không tạo target trực tiếp |
| CGLIB không advise final/private | Method không thể được subclass proxy override | Thiết kế proxy boundary là public non-final method |
| Lấy bean trong child context nhưng aspect ở parent/khác context | BPP/auto-proxy creator scoped per container | Đảm bảo target và AOP config tương thích trong container sở hữu bean |
| Annotation bị đặt trên interface, behavior khác khi đổi proxy | Method resolution/annotation lookup phụ thuộc runtime strategy | Viết integration tests JDK và CGLIB, áp convention rõ |
| Exception biến mất hoặc return type sai | `@Around` quên `proceed()`, nuốt exception, trả object sai kiểu | Gọi `proceed()` đúng semantics và rethrow lỗi ngoài chủ đích |
| Log dữ liệu nhạy cảm | Bind arguments rồi in toàn bộ | Chỉ ghi whitelist metadata, sanitize/mask |

Một quy trình debug ngắn:
1. Kiểm tra annotation có `@Retention(RUNTIME)` và `@Target(METHOD)`.
2. Xác nhận method thực sự được caller gọi qua Spring AOP proxy, không qua `new` hay `this`.
3. Kiểm tra `AopUtils.isAopProxy(bean)` và proxy type với `isJdkDynamicProxy`/`isCglibProxy`.
4. Kiểm tra aspect bean đã nằm trong context sở hữu target, auto-proxy BPP đã đăng ký.
5. Xem `bean instanceof Advised` rồi `getAdvisors()` để xác định advisor thực sự được gắn.
6. Xem cả ba điều kiện trong pointcut: **execution scope**, **@annotation metadata**, **args signature**.
7. Nếu lỗi xảy ra khi context khởi động, kiểm tra `argNames`/parameter discovery trước khi nghi ngờ method runtime.
8. Chạy positive-control test từ mục 7, rồi cô lập từng case lỗi.
9. Kiểm tra tác động của transaction/cache/security advisors khác tới thứ tự thực thi và kết quả.

## 12. Binding và bảo mật dữ liệu trong ứng dụng thật

Một aspect nhận method arguments có quyền nhìn thấy dữ liệu mà caller truyền vào. Vì vậy, cần phân biệt **khả năng bind** và **quyền được log hoặc lưu dữ liệu**.

Khuyến nghị:
- Annotation nên thể hiện **policy tĩnh** (action code, audit category, masking strategy) thay vì chứa secret.
- Chỉ chọn các arguments được phép ghi, và cần quyết định có được lưu định danh order/customer hay phải mask/hash.
- Tránh in `pjp.getArgs()` bằng `Arrays.deepToString` mặc định cho tất cả method; có thể vô tình lộ password/token, đối tượng lớn hoặc gọi `toString()` gây side effects/chi phí.
- Nếu audit là yêu cầu tuân thủ, cần kiến trúc lưu trữ đáng tin cậy, policy retry/idempotency và transaction boundary; một `CopyOnWriteArrayList` trong demo không phải audit store.
- Đừng ghi success event **trước khi `pjp.proceed()` hoàn tất thành công** rồi gọi đó là kết quả final. Với async/reactive return type, method Java có thể trả Future/Mono trước khi xử lý thực sự xong.
- Khi xử lý exception, tránh nuốt exception chỉ để đưa vào log; việc đó làm thay đổi nghiệp vụ và có thể gây commit sai ở transaction.
- Nếu cần metrics/tracing, cân nhắc Micrometer/Observation được tích hợp với Spring thay vì tạo logger network blocking ở mọi advice invocation.

### Reactive method và annotation binding

Với method trả `Mono<T>` hoặc `Flux<T>`, `@Around` vẫn bind annotation và args ở **method invocation qua proxy**. `pjp.proceed()` thường chỉ trả một publisher. Logic bên trong reactive source có thể chạy sau khi có subscriber. Nếu muốn ghi trạng thái completed/error/cancel của reactive pipeline, cần trả **publisher đã bọc operator phù hợp** (như `doOnError` hoặc `doFinally`), giữ return type và không tự `block()`/`subscribe()` trong advice.

Đừng lấy event `ok` tại thời điểm `pjp.proceed()` trả `Mono` làm bằng chứng request async đã xử lý thành công.

## 13. Tái sử dụng pointcut và quản lý nhiều annotation

Nếu có nhiều advice cùng cần scope một package/service type, nên tách **named pointcut**:

~~~java
@Pointcut("execution(* demo.binding.PaymentService.*(..))")
public void paymentOperations() {
}

@Before(
    value = "paymentOperations() && @annotation(tracked)",
    argNames = "tracked"
)
public void before(
        TrackedAction tracked) {
    // Không nên duplicate business logging tại đây nếu @Around đã xử lý.
}
~~~

Đây là đoạn **trong class `@Aspect`**, cần import `Pointcut` và `Before`. Nó cho thấy cách tái sử dụng scope; nếu thêm vào `TrackingAspect` demo thì bạn đang có advice mới nên output mục 6 thay đổi.

Khi annotation áp dụng cho nhiều lớp/method, nên chọn giữa:
- **Một annotation thống nhất** với action/category attributes và một aspect có phạm vi rõ.
- **Nhiều annotations độc lập** nếu semantics khác nhau thật sự (audit, authorization, retry). Không dồn tất cả business policy vào một aspect khó test.
- **Composed/meta-annotations** khi có yêu cầu Spring metadata inheritance rõ ràng, nhưng phải kiểm tra tính tương thích cụ thể của pointcut matching (đừng giả định `@annotation` tự nhận mọi meta-annotation như Spring annotation synthesis).

Với pointcut quá rộng như `@annotation(tracked)` không có giới hạn package, advisor eligibility có thể làm proxy thêm bean ngoài mục tiêu. Dùng `execution(...)` hoặc `within(...)` để lọc nhanh theo type/method, đồng thời viết test kiểm tra đủ tập method mong muốn.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `@Retention(RUNTIME)` quan trọng với annotation được bind vào Spring advice?

Vì Spring AOP cần metadata annotation của method có thể được quan sát tại runtime. SOURCE/CLASS retention không cung cấp cùng hợp đồng runtime đó.

**Câu 2.** `@annotation(tracked)` bind gì? `args(orderId,cents)` bind gì?

Thứ nhất bind **annotation instance** `TrackedAction` của method; thứ hai bind **runtime argument values** ở những vị trí tương ứng với tham số advice.

**Câu 3.** Trong advice có `ProceedingJoinPoint pjp` ở tham số đầu, cần viết `argNames` như thế nào?

Dùng `argNames="tracked,orderId,cents"` cho các tham số pointcut cần bind; `ProceedingJoinPoint` đầu tiên được Spring xử lý đặc biệt và không cần tên trong `argNames`.

**Câu 4.** Vì sao `preview(...)` trả kết quả bình thường nhưng không có event?

Vì method không mang `@TrackedAction` nên không match `@annotation(tracked)` của aspect.

**Câu 5.** Có thể thêm annotation vào method `pay(...)` rồi gọi `new PaymentService().pay(...)` và mong advice chạy không?

Không trong Spring proxy AOP. Object tạo trực tiếp không phải Spring AOP proxy.

**Câu 6.** `@args(...)` có lấy annotation đặt trên method không?

Không. `@args` liên quan annotation trên runtime type của **arguments**. Annotation method dùng `@annotation`.

**Câu 7.** Nếu custom annotation được đặt trên nhiều method có signature khác nhau, `args(orderId,cents)` có áp dụng hết không?

Không. Nó ràng buộc số lượng/vị trí/kiểu arguments. Muốn xử lý đa signature, thay pointcut/binding cho từng dạng hoặc dùng advice lấy `pjp.getArgs()` với type checks an toàn.

**Câu 8.** Vì sao `@Around` cần catch/rethrow exception cẩn thận?

Vì nuốt hoặc đổi exception có thể thay đổi contract nghiệp vụ và transaction semantics. Thường audit lỗi rồi rethrow nguyên exception.

**Câu 9.** `@Aspect` là một class Java nhưng không được Spring đăng ký làm bean. Điểm cắt có tự chạy không?

Không trong cơ chế Spring AOP được mô tả. Aspect phải được Spring biết và auto-proxy infrastructure phải hoạt động.

**Câu 10.** Vì sao test cần `@SpringJUnitConfig` thay vì chỉ tạo target bằng `new`?

Vì cần chứng minh behavior **của Spring AOP proxy**, annotation matching và argument binding khi bean được container quản lý.

## 15. Tài liệu Spring chính thức

- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): `@annotation`, `args`, `@args`, `@within`, `@target`, `this`, `target` và `bean`.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): binding parameters, `argNames`, `-parameters`, `ProceedingJoinPoint`.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): đăng ký aspect và Spring proxy-based AOP.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB, self-invocation, final/private, Spring-managed proxy.
- [Spring Framework — Spring TestContext with JUnit Jupiter](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/support-classes.html): `@SpringJUnitConfig` và chạy integration tests.
- [Java SE — RetentionPolicy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/RetentionPolicy.html): vòng đời annotation tại runtime.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-practical-workshop-004">Dựng ma trận pointcut theo package, phương thức và tên bean</a>

<details>
<summary>Xem chi tiết</summary>

# Dựng ma trận pointcut theo package, phương thức và tên bean trong Spring AOP

## 1. Mục tiêu thực hành

Khi một ứng dụng Spring có nhiều bean thuộc các package khác nhau, một custom `@Aspect` thường cần can thiệp **đúng nhóm service và đúng nhóm phương thức**, thay vì áp dụng cho mọi method. Chỉ dùng một `execution(* *(..))` quá rộng khiến advice chạy ở nơi không mong muốn; chỉ dùng tên package lại khó giới hạn chức năng; chỉ dùng tên bean có thể bắt nhầm service khác package.

Bài thực hành này dựng **ma trận pointcut ba chiều** của chính Spring AOP:

1. **Package:** một method có được khai báo trong `demo.service` hoặc package con không?
2. **Phương thức:** method public có tên bắt đầu bằng `find` và thuộc vùng service không?
3. **Tên bean:** Spring bean name có kết thúc bằng `Service` không?

Sau đó kết hợp ba điều kiện bằng `&&` để chọn **giao của ba tập** và kiểm thử cả các trường hợp **được chọn lẫn bị loại**.

Bài tập chỉ xét **Spring AOP proxy mode**, tập trung các pointcut designators `within`, `execution` và **Spring-specific `bean`**. Không cố mở rộng sang các join point của AspectJ weaving, constructor, field get/set hay đối tượng được tạo bằng `new`. Đây là điểm thiết yếu của Special Note: nội dung bám vào cách **Spring** khai báo, áp dụng và kiểm thử aspect.

## 2. Ba điều kiện pointcut cần phân biệt

### 2.1. Package bằng within(...)

```java
@Pointcut("within(demo.service..*)")
public void servicePackage() {}
```

Trong Spring AOP, `within(demo.service..*)` giới hạn các method-execution join points của những type nằm trong `demo.service` và các package con, ví dụ:

- `demo.service.orders.OrdersService`
- `demo.service.billing.BillingService`
- `demo.service.internal.InternalWorker`

Một class `demo.repository.OrdersDao` hay `demo.external.ExternalLookup` **không** khớp. Dấu `..` ở vị trí package thể hiện package hiện tại cùng các tầng con trong type pattern; dấu `*` cuối chọn các tên type phù hợp.

Đây không phải câu lệnh "mọi method đã được gọi ở bất kỳ đâu từ service package". Trong Spring AOP, join point là **method execution trên Spring-managed proxy**, và `within` xét phạm vi type khai báo method được chặn.

### 2.2. Phương thức bằng execution(...)

```java
@Pointcut("execution(public * demo.service..*.find*(..))")
public void finderMethod() {}
```

Ý nghĩa:

| Thành phần | Ý nghĩa |
| --- | --- |
| `execution(...)` | Chọn method-execution join points trong Spring AOP |
| `public` | Chỉ method public |
| `*` đầu | Mọi return type |
| `demo.service..*` | Declaring type trong service package hoặc các subpackages |
| `find*` | Tên method bắt đầu bằng `find`: `find`, `findOne`, `findAll`,... |
| `(..)` | Bất kỳ số lượng và kiểu tham số nào |

Trong ma trận này, `finderMethod()` cố tình chứa **cả điều kiện phương thức lẫn vùng service**, để tránh match `find` của repository hoặc external bean. Điều kiện package ở cột riêng vẫn có giá trị vì các method `save` / `run` thuộc package service nhưng **không** bắt đầu bằng `find`.

Nếu muốn trục "tên method" độc lập tuyệt đối với package, có thể dùng `execution(public * *..*.find*(..))` trong một thử nghiệm khác. Tuy nhiên trong hệ thống thật, nên thêm scoping package/type rõ ràng để tránh pointcut quá rộng.

### 2.3. Tên Spring bean bằng bean(...)

```java
@Pointcut("bean(*Service)")
public void serviceBeanName() {}
```

`bean(*Service)` là **pointcut designator mở rộng chỉ có trong Spring AOP**, dùng tên bean Spring và wildcard `*`. Nó chọn `ordersService`, `billingService`, `externalService` nhưng không chọn `internalWorker` hoặc `ordersDao`.

Cần nhớ:

- `bean(...)` xét **bean name / aliases** do Spring BeanFactory quản lý; không phải simple class name, package name hoặc tên biến Java.
- Tên Spring bean mặc định từ `@Component` thường suy ra từ tên class, nhưng trong bài sẽ khai báo `@Bean` rõ ràng để không phải đoán.
- Biểu thức `bean(*Service)` mang tính quy ước đặt tên; nếu đổi bean ID từ `ordersService` sang `ordersAdapter`, kết quả match có thể thay đổi **dù class và method giữ nguyên**.
- Trong AspectJ compile-time/load-time weaving thuần, `bean(...)` **không phải** một designator của AspectJ standard và không áp dụng như trong Spring AOP.
- `bean(*)` rất rộng, không nên dùng thay điểm cắt có mục tiêu.

### 2.4. Giao của ba điều kiện

```java
@Pointcut("servicePackage() && finderMethod() && serviceBeanName()")
public void selectedServiceFinders() {}
```

Lời gọi được đánh dấu **ALL** nếu cùng lúc:

- Declaring type ở package `demo.service..*`;
- Method public bắt đầu `find`;
- Spring bean name kết thúc `Service`.

Spring sử dụng `&&` cho AND, `||` cho OR và `!` cho NOT trong pointcut expressions. Với Java annotation string, có thể viết trực tiếp `&&` và `||`; không nhầm với `&` / `|`.

## 3. Ma trận kỳ vọng trước khi viết code

Dự án sẽ có năm Spring beans ở những package khác nhau. Một bean sẽ được gọi qua hai method khác nhau để tạo sáu trường hợp.

| Lời gọi qua Spring bean | Package P | Method M | Bean B | Giao P∩M∩B |
| --- | :---: | :---: | :---: | :---: |
| `ordersService.find("A")` | ✓ | ✓ | ✓ | ✓ |
| `ordersService.save("A")` | ✓ | ✗ | ✓ | ✗ |
| `billingService.find("B")` | ✓ | ✓ | ✓ | ✓ |
| `internalWorker.run("I")` | ✓ | ✗ | ✗ | ✗ |
| `externalService.find("E")` | ✗ | ✗ | ✓ | ✗ |
| `ordersDao.find("D")` | ✗ | ✗ | ✗ | ✗ |

Định nghĩa bốn cột:

- **P:** `within(demo.service..*)`
- **M:** `execution(public * demo.service..*.find*(..))`
- **B:** `bean(*Service)`
- **ALL:** `P && M && B`

Lưu ý `externalService.find("E")` có method tên `find` nhưng cột **M** vẫn là ✗, vì method của type `demo.external.ExternalLookup` nằm **ngoài** vùng `demo.service..*` đã có trong biểu thức M. Đây là trường hợp kiểm tra để ngăn hiểu sai tên phương thức là điều kiện duy nhất của cột M.

**Vì sao dùng bean tên `externalService` dù class tên `ExternalLookup`?** Để chứng minh `bean(...)` chọn theo tên bean Spring do `@Bean` khai báo, chứ không chọn theo tên class. Tương tự `internalWorker` nằm trong service package nhưng tên bean không kết thúc bằng `Service`.

## 4. Không suy ra kết quả từ tên class hay proxy name

Một số bẫy phổ biến khi dựng ma trận:

1. **Type và bean name là hai trục khác nhau.** `demo.external.ExternalLookup` có thể được đăng ký tên `externalService`; cột bean name là ✓ còn package là ✗.
2. **Tất cả pointcut đều xét một invocation được Spring AOP hỗ trợ.** Nếu tạo object bằng `new` rồi gọi `find`, bạn không đi qua Spring AOP proxy, nên advice không chạy dù class/method trông có vẻ khớp.
3. **Một bean không có advisor match có thể không bị proxy.** `ordersDao` trong demo dự kiến là bean thường, không phát sinh events. Điều đó không có nghĩa `ordersDao.find` chưa được gọi.
4. **Spring proxy có thể là JDK hoặc CGLIB.** Ví dụ dùng class-based proxy rõ ràng để dễ inject concrete class. Loại proxy ảnh hưởng method visibility và interface exposure nhưng không làm `bean(...)` trở thành kiểm tra tên class.
5. **Self-invocation** (`this.find(...)`) bỏ qua Spring proxy interception. Ma trận áp dụng cho lời gọi client **qua bean Spring**, không phải mọi lời gọi Java nội bộ.
6. **Thứ tự chạy nhiều advice không phải trọng tâm.** Bốn `@Before` cùng aspect có thể không chạy theo thứ tự khai báo, nên test sẽ so **tập sự kiện** thay vì khẳng định thứ tự P, M, B, ALL.

## 5. Ví dụ Java 17 + Spring Framework + JUnit 5 có thể chạy

Ví dụ dùng Spring Framework `6.2.19`, AspectJ Weaver `1.9.24` làm thư viện cần cho @AspectJ pointcut parsing, Java 17 và JUnit Jupiter `5.11.4`. Không cần Spring Boot, database, AspectJ compiler hay JVM agent.

### 5.1. Cấu trúc project

```text
spring-pointcut-matrix/
  pom.xml
  src/main/java/demo/
    MatrixConfig.java
    MatrixAspect.java
    MatrixEvents.java
    MatrixApp.java
  src/main/java/demo/service/orders/
    OrdersService.java
  src/main/java/demo/service/billing/
    BillingService.java
  src/main/java/demo/service/internal/
    InternalWorker.java
  src/main/java/demo/external/
    ExternalLookup.java
  src/main/java/demo/repository/
    OrdersDao.java
  src/test/java/demo/
    MatrixPointcutTest.java
```

Các file package tách rõ để `within` và `execution` có cơ sở so khớp thật, thay vì đặt mọi class lồng nhau trong một file rồi đoán kết quả.

### 5.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-pointcut-matrix</artifactId>
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
      <groupId>org.springframework</groupId>
      <artifactId>spring-test</artifactId>
      <version>6.2.19</version>
      <scope>test</scope>
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
      </plugin>
    </plugins>
  </build>
</project>
```

Tất cả Spring Framework modules pin cùng version để không pha trộn API/runtime. `spring-aop` chạy **proxy-based Spring AOP**, dù project có dependency `aspectjweaver`; dependency này không tự bật AspectJ load-time weaving.

### 5.3. Các target class theo package

`src/main/java/demo/service/orders/OrdersService.java`:

```java
package demo.service.orders;

public class OrdersService {
    public String find(String id) {
        return "orders:" + id;
    }

    public String save(String id) {
        return "saved:" + id;
    }
}
```

Class thuộc `demo.service.orders`, nên cả `find` và `save` cùng khớp cột P; chỉ `find` khớp cột M.

`src/main/java/demo/service/billing/BillingService.java`:

```java
package demo.service.billing;

public class BillingService {
    public String find(String id) {
        return "billing:" + id;
    }
}
```

Cả package P lẫn method M đều khớp, và bean name sau này sẽ là `billingService` (B=✓).

`src/main/java/demo/service/internal/InternalWorker.java`:

```java
package demo.service.internal;

public class InternalWorker {
    public String run(String id) {
        return "work:" + id;
    }
}
```

Class thuộc service subpackage nên P=✓, nhưng `run` không bắt đầu `find`; bean name `internalWorker` không kết thúc bằng `Service`.

`src/main/java/demo/external/ExternalLookup.java`:

```java
package demo.external;

public class ExternalLookup {
    public String find(String id) {
        return "external:" + id;
    }
}
```

Method có tên `find` nhưng không nằm trong service package; cả P/M đều ✗. Ở cấu hình Spring bên dưới, instance được đặt tên bean `externalService` nên B=✓.

`src/main/java/demo/repository/OrdersDao.java`:

```java
package demo.repository;

public class OrdersDao {
    public String find(String id) {
        return "dao:" + id;
    }
}
```

Đây là case âm tính cho cả ba điều kiện: tên class/package không thuộc service, method M đã scope vào service package, và tên bean cuối cùng là `ordersDao`.

### 5.4. Recorder ghi nhãn các pointcut match

`src/main/java/demo/MatrixEvents.java`:

```java
package demo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MatrixEvents {
    private final List<String> events = new CopyOnWriteArrayList<>();

    public void add(String tag) {
        events.add(tag);
    }

    public List<String> sorted() {
        return events.stream().sorted().toList();
    }

    public void clear() {
        events.clear();
    }
}
```

Recorder lưu các nhãn **P**, **M**, **B**, **ALL**. `sorted()` giúp output và assertions ổn định dù Spring không bảo đảm thứ tự gọi bốn `@Before` cùng loại nằm trong cùng `@Aspect`. Trong demo và test mặc định, các lần gọi tuần tự; nếu chạy test parallel trên một singleton recorder, phải tạo isolation để tránh events trộn nhau.

### 5.5. Aspect định nghĩa và thực thi ma trận

`src/main/java/demo/MatrixAspect.java`:

```java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class MatrixAspect {

    private final MatrixEvents events;

    public MatrixAspect(MatrixEvents events) {
        this.events = events;
    }

    @Pointcut("within(demo.service..*)")
    public void servicePackage() {
    }

    @Pointcut("execution(public * demo.service..*.find*(..))")
    public void finderMethod() {
    }

    @Pointcut("bean(*Service)")
    public void serviceBeanName() {
    }

    @Pointcut("servicePackage() && finderMethod() && serviceBeanName()")
    public void selectedServiceFinders() {
    }

    @Before("servicePackage()")
    public void markPackage() {
        events.add("P");
    }

    @Before("finderMethod()")
    public void markMethod() {
        events.add("M");
    }

    @Before("serviceBeanName()")
    public void markBean() {
        events.add("B");
    }

    @Before("selectedServiceFinders()")
    public void markIntersection() {
        events.add("ALL");
    }
}
```

Những phương thức `@Pointcut` có **return type `void` và body rỗng**. Spring dùng tên của chúng để tái sử dụng các biểu thức trong cùng aspect; đây là cú pháp @AspectJ-style Spring AOP.

Bốn `@Before` được Spring áp dụng khi các biểu thức tương ứng match bean/method thực tế. Khi một method khớp cả P/M/B, recorder nhận đủ ba nhãn và `ALL`; với method chỉ khớp P/B, recorder chỉ nhận P/B.

**Không dựa vào thứ tự khai báo `markPackage`, `markMethod`, `markBean`, `markIntersection`.** Bốn advice cùng loại trong cùng aspect không có precedence riêng đảm bảo như thứ tự source file. `MatrixEvents.sorted()` cho một biểu diễn kết quả đáng tin cậy.

### 5.6. Cấu hình Spring bean names tường minh

`src/main/java/demo/MatrixConfig.java`:

```java
package demo;

import demo.external.ExternalLookup;
import demo.repository.OrdersDao;
import demo.service.billing.BillingService;
import demo.service.internal.InternalWorker;
import demo.service.orders.OrdersService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class MatrixConfig {

    @Bean
    MatrixEvents matrixEvents() {
        return new MatrixEvents();
    }

    @Bean("ordersService")
    OrdersService ordersService() {
        return new OrdersService();
    }

    @Bean("billingService")
    BillingService billingService() {
        return new BillingService();
    }

    @Bean("internalWorker")
    InternalWorker internalWorker() {
        return new InternalWorker();
    }

    @Bean("externalService")
    ExternalLookup externalService() {
        return new ExternalLookup();
    }

    @Bean("ordersDao")
    OrdersDao ordersDao() {
        return new OrdersDao();
    }

    @Bean
    MatrixAspect matrixAspect(MatrixEvents events) {
        return new MatrixAspect(events);
    }
}
```

Ở đây `proxyTargetClass=true` yêu cầu Spring sử dụng class-based proxy (CGLIB) cho beans cần proxy. Các class target đều **public, không final** và phương thức cần quan sát đều public, không final. Việc dùng CGLIB trong demo cho phép lấy từng bean theo concrete type (dễ chạy ma trận); nếu ứng dụng dùng JDK proxies, hãy expose/inject theo interface.

Năm tên bean chính xác:

- `ordersService`
- `billingService`
- `internalWorker`
- `externalService`
- `ordersDao`

Bởi tên được khai báo bằng `@Bean("...")`, không phụ thuộc vào `@ComponentScan` hoặc quy ước viết thường class name. Đặc biệt, **bean `externalService` chứa instance class `ExternalLookup` ở package `demo.external`**, là cách chủ động tách biệt trục bean name khỏi trục package.

### 5.7. Chương trình chạy in ma trận

`src/main/java/demo/MatrixApp.java`:

```java
package demo;

import demo.external.ExternalLookup;
import demo.repository.OrdersDao;
import demo.service.billing.BillingService;
import demo.service.internal.InternalWorker;
import demo.service.orders.OrdersService;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class MatrixApp {
    private static void show(String label, MatrixEvents events) {
        System.out.println(label + " => " + events.sorted());
        events.clear();
    }

    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(MatrixConfig.class)) {

            MatrixEvents events = context.getBean(MatrixEvents.class);
            OrdersService orders = context.getBean(OrdersService.class);
            BillingService billing = context.getBean(BillingService.class);
            InternalWorker worker = context.getBean(InternalWorker.class);
            ExternalLookup external = context.getBean(ExternalLookup.class);
            OrdersDao dao = context.getBean(OrdersDao.class);

            System.out.println("ordersProxy=" + AopUtils.isAopProxy(orders));
            System.out.println("daoProxy=" + AopUtils.isAopProxy(dao));

            orders.find("A");
            show("ordersService.find", events);

            orders.save("A");
            show("ordersService.save", events);

            billing.find("B");
            show("billingService.find", events);

            worker.run("I");
            show("internalWorker.run", events);

            external.find("E");
            show("externalService.find", events);

            dao.find("D");
            show("ordersDao.find", events);
        }
    }
}
```

Vì `ordersService` match các pointcut, bean dự kiến có AOP proxy. `ordersDao` không match bất kỳ pointcut nào, nên trong context đơn giản này thường vẫn là target trực tiếp.

Lệnh chạy ở thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.MatrixApp
```

Các dòng do `System.out` tạo **dự kiến** (không tính logging Maven/Spring):

```text
ordersProxy=true
daoProxy=false
ordersService.find => [ALL, B, M, P]
ordersService.save => [B, P]
billingService.find => [ALL, B, M, P]
internalWorker.run => [P]
externalService.find => [B]
ordersDao.find => []
```

Đây là kết quả kỳ vọng từ **cấu hình và code**, không phải tuyên bố đã chạy Maven. Các list được sort theo chữ cái nên `ALL` đứng trước `B`, `M` và `P`; đừng hiểu output đã chứng minh advice `markIntersection()` thực sự chạy trước các advice khác. Điều cần kiểm tra ở bài này là **tập điều kiện được thỏa**, không phải ordering.

## 6. Viết integration tests cho cả trường hợp dương và âm

Để test Spring pointcut, chỉ unit-test `MatrixAspect` bằng cách gọi trực tiếp `markPackage()` là chưa đủ: cách đó không kiểm tra Spring tạo advisor, không xác nhận proxy, và không kiểm tra `bean(...)` designator. Phải dùng Spring TestContext cho phép thực hiện **lời gọi qua bean Spring thật**.

`src/test/java/demo/MatrixPointcutTest.java`:

```java
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import demo.external.ExternalLookup;
import demo.repository.OrdersDao;
import demo.service.billing.BillingService;
import demo.service.internal.InternalWorker;
import demo.service.orders.OrdersService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MatrixConfig.class)
class MatrixPointcutTest {
    @Autowired
    OrdersService orders;

    @Autowired
    BillingService billing;

    @Autowired
    InternalWorker worker;

    @Autowired
    ExternalLookup external;

    @Autowired
    OrdersDao dao;

    @Autowired
    MatrixEvents events;

    @BeforeEach
    void resetEvents() {
        events.clear();
    }

    @Test
    void springProxyExistsForMatchedBeanButNotDao() {
        assertTrue(AopUtils.isAopProxy(orders));
        assertFalse(AopUtils.isAopProxy(dao));
    }

    @Test
    void ordersFindMatchesAllThreeDimensions() {
        assertEquals("orders:A", orders.find("A"));
        assertEquals(List.of("ALL", "B", "M", "P"), events.sorted());
    }

    @Test
    void ordersSaveMatchesPackageAndBeanButNotFindMethod() {
        assertEquals("saved:A", orders.save("A"));
        assertEquals(List.of("B", "P"), events.sorted());
    }

    @Test
    void billingFindMatchesAllThreeDimensions() {
        assertEquals("billing:B", billing.find("B"));
        assertEquals(List.of("ALL", "B", "M", "P"), events.sorted());
    }

    @Test
    void internalWorkerMatchesOnlyPackage() {
        assertEquals("work:I", worker.run("I"));
        assertEquals(List.of("P"), events.sorted());
    }

    @Test
    void externalBeanMatchesByNameDespiteDifferentPackage() {
        assertEquals("external:E", external.find("E"));
        assertEquals(List.of("B"), events.sorted());
    }

    @Test
    void repositoryMethodMatchesNoPointcut() {
        assertEquals("dao:D", dao.find("D"));
        assertTrue(events.sorted().isEmpty());
    }
}
```

Lệnh:

```bash
mvn -q test
```

Dự kiến **7 tests pass**, Maven exit code 0. Không có `-q` thì Surefire thường báo tương đương:

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
```

Đây là kỳ vọng dựa trên assertions. Muốn khẳng định thực tế, phải tạo đúng cấu trúc files, có Maven/JDK/dependency resolution và chạy test trong môi trường cụ thể.

Bộ test không chỉ kiểm tra có advice chạy mà còn kiểm tra **advice không chạy sai chỗ**. Negative cases đặc biệt quan trọng khi thay đổi package hoặc quy ước bean name.

## 7. Quy trình dựng ma trận pointcut trong dự án thật

**Bước 1 — Liệt kê bean name được Spring thực sự đăng ký.** Kiểm tra `ApplicationContext.getBeanDefinitionNames()` hoặc bean metadata; không dựa vào suy đoán từ tên class.

**Bước 2 — Xác định target class và method signature.** Với bean proxy, target implementation có thể nằm ở package khác với proxy generated class. Trong Spring AOP, pointcut matching diễn ra theo Spring proxy/target method semantics, không nên lấy package `jdk.proxy...` hoặc `$$SpringCGLIB$$` làm căn cứ kiểm tra.

**Bước 3 — Tách điều kiện rõ ràng.** Tạo các named `@Pointcut` cho package, method và bean name. Điều này giúp đọc/kiểm thử từng cột; không viết một biểu thức rất dài khó review từ đầu.

**Bước 4 — Lập truth table trước khi code.** Mỗi dòng đại diện một **invocation qua Spring bean**; mỗi cột là một predicate. Cột giao chỉ ✓ khi **tất cả** điều kiện cùng ✓.

**Bước 5 — Tạo positive và negative fixtures.** Phải có ít nhất một bean thuộc service package nhưng không có suffix Service, và một bean suffix Service ở ngoài service package, để chứng minh P và B thực sự độc lập.

**Bước 6 — Viết aspect chỉ ghi nhận match.** Đừng vừa viết pointcut vừa làm transaction, security hoặc logging nặng trong cùng phép kiểm thử. Advice ghi event đơn giản đủ xác nhận predicate.

**Bước 7 — Gọi qua proxy và assert.** Tạo Spring ApplicationContext, xác minh các beans phù hợp được Spring quản lý, rồi so kết quả. Nếu method gọi qua `this` từ target, pointcut có đúng vẫn không được Spring AOP intercept lần nữa.

**Bước 8 — Test sự thay đổi có chủ đích.** Khi đổi package, method name hoặc Spring bean name, cập nhật ma trận kỳ vọng và integration tests cùng PR. Đừng chỉ nhìn logs trong IDE.

**Bước 9 — Kiểm tra phạm vi advice.** Tối ưu pointcut scope để không advise nhầm controller, repository, bean infrastructure hoặc hàm không cần xử lý.

## 8. Tổ hợp AND, OR và NOT dùng trong Spring

Trong ví dụ chính, giao ba trục:

```java
@Pointcut("servicePackage() && finderMethod() && serviceBeanName()")
public void selectedServiceFinders() {}
```

Có thể mở rộng bằng các toán tử:

```java
@Pointcut("servicePackage() && serviceBeanName()")
public void anyServiceMethodOnServiceNamedBean() {}

@Pointcut("finderMethod() || serviceBeanName()")
public void finderOrNamedService() {}

@Pointcut("servicePackage() && !serviceBeanName()")
public void internalServiceObjectsNotNamedService() {}
```

Đây là **snippets mở rộng**, không đưa vào `MatrixAspect` của project chạy để tránh làm thay đổi matrix output hiện có.

Ý nghĩa:

- `P && B`: phải thuộc service package **và** tên bean phù hợp, không nhất thiết `find`.
- `M || B`: method finder **hoặc** bean có suffix `Service`; bao gồm `externalService.find` dù ngoài package.
- `P && !B`: object thuộc service package nhưng bean name không mang suffix Service; ví dụ `internalWorker`.

Đặt ngoặc tường minh khi dùng nhiều loại toán tử để tránh nhầm precedence và giúp code review. Có thể tách named pointcuts thay vì lồng toàn bộ biểu thức trong `@Before("...")`.

Trong Spring AOP có thêm `@annotation`, `args`, `target`, `this` và các designators được hỗ trợ khác. Khi mở rộng bài toán sang annotations/arguments, cần thiết kế bảng test khác. Không dùng `call(...)`, `get(...)`, `set(...)` hoặc `cflow(...)` của AspectJ full language trong Spring AOP proxy expressions: chúng không được Spring AOP hỗ trợ.

## 9. Những lỗi cú pháp và phạm vi thường gặp

| Biểu thức / ý tưởng | Rủi ro | Cách sửa |
| --- | --- | --- |
| `within(demo.service.*)` | Chỉ tập trung types trực tiếp trong package, bỏ sót nested subpackages | Dùng `within(demo.service..*)` khi chủ ý gồm subpackages |
| `execution(* *(..))` | Quá rộng, dễ match nhiều Spring beans | Giới hạn declaring type/package/method |
| `execution(* demo.service..*.find*(..))` | Match cả method non-public có thể intercept được theo proxy mode | Thêm `public` nếu yêu cầu chỉ public |
| `bean(*Service)` | Phụ thuộc quy ước bean name, không phải class type | Khai báo/nêu rõ convention, dùng named beans |
| `bean(OrdersService)` | Dễ nhầm class name thành bean name, phân biệt hoa thường | Dùng bean ID thực tế như `ordersService` |
| `bean(*Service)` trong AspectJ native weaving | Không hỗ trợ vì đó là Spring-specific PCD | Chỉ dùng trong Spring AOP proxy |
| `execution(* demo.repository..*.find*(..))` | Scope repository thay vì service, đổi matrix | Kiểm tra declaring type và mục tiêu |
| `@Pointcut` có method body làm nghiệp vụ | Đi ngược mục đích khai báo signature | Để method `void` body rỗng |
| Hai `@Before` cùng aspect và phụ thuộc thứ tự source | Thứ tự giữa advice cùng loại không được đảm bảo | Test set event hoặc tách aspects có `@Order` khác |
| Advice không chạy trên `this.find()` | Self-invocation bỏ qua proxy | Gọi qua bean khác/proxy theo thiết kế |
| Aspect class không là Spring bean | Không có advisor được auto-proxy infrastructure đăng ký | `@Component` + scan hoặc `@Bean` |
| Spring không bật `@EnableAspectJAutoProxy` | Custom aspect không được áp dụng trong context thuần | Bật AOP trong đúng context hoặc cấu hình tương đương |

## 10. Khi project dùng JDK dynamic proxy thay vì CGLIB

Ví dụ chọn `proxyTargetClass=true` để inject target class vì không có interface. Trong production, nhiều service có interface và Spring có thể chọn JDK dynamic proxy.

Khi đó, cần phân biệt:

- JDK proxy expose các **interface** mà target triển khai; client nên inject theo interface, không cast sang implementation.
- `this(SomeServiceInterface)` xét proxy có implement interface; `target(ConcreteServiceImpl)` xét đối tượng target.
- `within(demo.service..*)` và `execution(...)` vẫn phải được thiết kế dựa trên **type/method mà Spring thực sự match**, không phải tên class proxy do runtime sinh ra.
- Method chỉ có trên implementation class nhưng không nằm trong interface sẽ không được gọi qua JDK proxy reference bằng API interface.
- Với CGLIB, class `final` hoặc method `final` không thể override để intercept; method `private` cũng không được advise.
- Một method public nhưng được gọi bằng `this.method()` trong target vẫn bypass Spring AOP proxy.

**Bài học:** ma trận chỉ có ý nghĩa với **những method-execution join points mà Spring AOP có thể intercept**. Một bảng TRUE về package/method/bean name không biến private/self-invoked method thành join point được proxy chặn.

## 11. Chẩn đoán nếu ma trận thực tế khác kỳ vọng

### 11.1. Kiểm tra đúng bean name

Khi dùng `@ComponentScan`, tên bean thường xuất phát từ BeanNameGenerator và stereotype annotation. Nếu dùng `@Bean(name="...")`, tên do config quyết định. `bean(*Service)` chọn tên bean phù hợp, không phải output `bean.getClass().getSimpleName()`.

Có thể debug trong context:

```java
for (String name : context.getBeanDefinitionNames()) {
    if (name.endsWith("Service")) {
        System.out.println("candidate bean=" + name);
    }
}
```

Đây là **snippet diagnostic** đặt trong nơi có biến `context`, không phải code buộc phải thêm vào project. Danh sách bean definitions không tự chứng minh bean hiện có advisor match method; tiếp tục kiểm thử invocation.

### 11.2. Kiểm tra actual proxy và advisors

```java
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

Object bean = context.getBean("ordersService");
System.out.println(AopUtils.isAopProxy(bean));

if (bean instanceof Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println("advisor=" + advisor);
    }
}
```

Nếu bean không phải proxy, kiểm tra `@EnableAspectJAutoProxy`, aspect bean, pointcut applicability và context. Nếu proxy tồn tại, nhìn advisor chain và gọi method đúng đường. Một advisor có thể có mặt nhưng chỉ match **một số** methods; danh sách advisors không thay thế behavior tests.

### 11.3. Sự khác nhau giữa type pattern và package pattern

Trong `execution(public * demo.service..*.find*(..))`, đoạn `demo.service..*` là pattern của declaring type. Nếu method thực tế được khai báo trong interface ở package hoàn toàn khác, cần kiểm tra Spring dùng method/target class nào khi tính match, nhất là khi đổi giữa JDK proxy và CGLIB. Đừng giả định di chuyển class nhưng giữ interface ở package cũ sẽ cho mọi predicate giữ nguyên.

### 11.4. Spring bean alias và FactoryBean

`bean(...)` được Spring AOP giải quyết theo tên bean (có thể liên quan aliases tùy hoàn cảnh). Với `FactoryBean`, factory và product có ý nghĩa tên bean đặc biệt (`&` prefix), nên phải kiểm tra đúng object được advice. Trong bài không dùng aliases hoặc FactoryBean để giữ ma trận dễ kiểm chứng.

### 11.5. Test có aspect khác can thiệp

Nếu target cũng có `@Transactional`, `@Cacheable` hay security advisor, một interceptor có thể làm transaction, short-circuit ở cache hit hoặc ném exception trước khi custom aspect chạy. Ma trận P/M/B chỉ nói điều kiện **match**, không khẳng định mọi advice luôn được thực thi khi advisor khác có precedence cao và không gọi tiếp chain.

### 11.6. Thứ tự event không ổn định

Bốn advice `@Before` có cùng aspect và cùng loại. Test `events.sorted()` đã giải quyết việc ordering không nằm trong hợp đồng của bài. Nếu có yêu cầu **P chạy trước B** với ý nghĩa nghiệp vụ, hãy tách concern thành những aspects có `@Order` khác nhau hoặc gom vào một advice method rõ thứ tự; đừng thay đổi danh sách events để giả tạo một precedence.

## 12. Thiết kế pointcut có thể bảo trì

Trong dự án lớn, nên tạo một class chứa **named pointcuts tái sử dụng** cho module Spring AOP thay vì copy biểu thức vào mọi advice.

Ví dụ snippet:

```java
package demo.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class CommonSpringPointcuts {
    @Pointcut("within(demo.service..*)")
    public void servicePackage() {}

    @Pointcut("execution(public * demo.service..*.find*(..))")
    public void finderOperations() {}

    @Pointcut("bean(*Service)")
    public void namedServices() {}
}
```

Một aspect khác có thể tham chiếu fully qualified named pointcut:

```java
@Before(
  "demo.aop.CommonSpringPointcuts.servicePackage()" +
  " && demo.aop.CommonSpringPointcuts.finderOperations()" +
  " && demo.aop.CommonSpringPointcuts.namedServices()"
)
public void inspect() {
    // Advice riêng nếu Spring aspect được đăng ký thành bean.
}
```

Hai đoạn là **snippets thiết kế bổ sung**, không phải files trong project chạy ở phần 5. Java visibility rules áp dụng khi aspect tham chiếu named pointcuts: `public` khi cần chia sẻ xuyên class. Việc tách named pointcut làm code dễ review, đặc biệt khi đổi package hoặc quy ước bean name.

Quy tắc thực tế:

1. Đặt tên pointcut mô tả **ý nghĩa kiến trúc**, không phải chỉ mô tả cú pháp.
2. Giới hạn package/type càng sớm càng tốt; tránh pattern toàn ứng dụng không cần thiết.
3. Test một danh sách method/bean đại diện khi đổi rule.
4. Cân nhắc tác động của rename package/method/bean ID lên ma trận.
5. Không gắn business policy quan trọng chỉ vào naming convention dễ bị thay đổi mà thiếu test.
6. Khi cần authorization, transaction hoặc cache, ưu tiên cơ chế Spring có sẵn; dùng custom aspect cho cross-cutting concern riêng.

## 13. Bài tập mở rộng để tự kiểm chứng

### Bài tập A — Đổi tên Spring bean

Đổi `@Bean("externalService")` thành `@Bean("externalLookup")`, giữ cùng class và method. Dự đoán:

- Cột P/M vẫn ✗.
- Cột B chuyển từ ✓ sang ✗ vì bean name không kết thúc bằng `Service`.
- `external.find("E")` vẫn trả `external:E` nhưng không tạo event B.

**Kiểm tra:** nếu chỉ đổi bean name mà class vẫn có thể được lấy theo type duy nhất trong context, test `externalBeanMatchesByNameDespiteDifferentPackage` phải được cập nhật có chủ đích; đây là regression test cho naming convention.

### Bài tập B — Đổi tên method

Đổi `OrdersService.find` thành `load`, cập nhật caller. Nếu method vẫn public và trong `demo.service.orders`:

- P ✓, B ✓, M ✗, ALL ✗.
- Result events tương tự `ordersService.save`.

### Bài tập C — Đổi package

Chuyển `ExternalLookup` sang `demo.service.external` nhưng giữ bean ID `externalService` và method `find`. Khi đó:

- P ✓, M ✓, B ✓, ALL ✓.
- Kết quả cho external bean sẽ từ chỉ B thành đủ bốn events.

### Bài tập D — Chọn các method không tên find

Tạo pointcut mới:

```java
@Pointcut("servicePackage() && !finderMethod()")
public void serviceNonFinderOperations() {}
```

Kỳ vọng `ordersService.save` và `internalWorker.run` match, còn `ordersService.find` không match. Đây là bài tập về **tập bù trong tập P**, không cần thay đổi implementation của `OrdersService`.

### Bài tập E — Chỉ bean suffix Service và method find bất kể package

Dùng:

```java
@Pointcut("bean(*Service) && execution(public * *.find*(..))")
public void anyNamedServiceFinder() {}
```

**Lưu ý:** đây là mẫu suy nghĩ về giao B và tên method; biểu thức `execution(public * *.find*(..))` cần được thử trên type/method thật và có thể quá rộng trong ứng dụng production. Khi muốn match xuyên package rõ ràng, nên dùng type pattern phù hợp, ví dụ `execution(public * *..*.find*(..))` trong một pointcut được test riêng.

Nếu chuyển sang điều kiện `bean(*Service) && execution(public * *..*.find*(..))`, `externalService.find` sẽ được chọn dù package không thuộc `demo.service`. Đây là khác biệt có chủ đích so với ma trận ban đầu.

## 14. Checklist QA cho ma trận Spring pointcut

- [ ] Tab/module học đúng Spring AOP, không nhầm native AspectJ weaving.
- [ ] Có ba predicate P/M/B độc lập đủ để giải thích ý nghĩa.
- [ ] `within` và `execution` có package/type pattern đúng mục tiêu.
- [ ] `bean(...)` sử dụng **Spring bean name**, không phải class name.
- [ ] Named pointcuts `@Pointcut` return `void` và có body rỗng.
- [ ] Aspect là **Spring bean** trong context có `@EnableAspectJAutoProxy`.
- [ ] Test gọi **qua Spring bean/proxy**; không chỉ gọi `new`.
- [ ] Có ít nhất một dòng P-only, B-only, P+B, ALL và none để chứng minh các trục không bị nhập nhằng.
- [ ] Positive tests và negative tests đều có assertions cụ thể.
- [ ] Nếu nhiều `@Before` cùng aspect, không assert thứ tự ngẫu nhiên giữa advice methods.
- [ ] Khi đổi naming convention, update ma trận và JUnit tests.
- [ ] Khi thêm transaction/cache/security advisors, xem cả advisor chain và short-circuit.
- [ ] Khi dùng JDK proxies, inject qua interface và xác minh implementation method matching.
- [ ] Không dùng unsupported `call/get/set/cflow` trong Spring AOP pointcuts.
- [ ] Kết quả chạy/tổng số test được mô tả rõ là expected cho tới khi thực thi build thực tế.

## 15. Câu hỏi ôn tập

**Hỏi:** `within(demo.service..*)` khác `bean(*Service)` ở đâu?  
**Đáp:** Điều kiện thứ nhất xét declaring type ở package service; điều kiện thứ hai xét tên bean do Spring đăng ký. Chúng không tương đương.

**Hỏi:** Tại sao `externalService.find` chỉ có B=✓ dù tên method là find?  
**Đáp:** Cột M trong bài đã giới hạn `execution` vào `demo.service..*`, còn class nằm ở `demo.external`.

**Hỏi:** Điểm khác biệt Spring-specific quan trọng nhất trong ba trục là gì?  
**Đáp:** `bean(...)` là Spring AOP pointcut designator, chọn Spring bean name ở instance level; không phải designator dùng trực tiếp trong native AspectJ weaving.

**Hỏi:** `ordersService.save` khớp những cột nào?  
**Đáp:** P và B; không khớp M vì method không bắt đầu `find`, nên ALL cũng ✗.

**Hỏi:** Vì sao dùng `events.sorted()` trong test?  
**Đáp:** Vì nhiều `@Before` cùng aspect cùng match nhưng thứ tự advice methods không phải điều đang được bảo đảm; bộ test chỉ kiểm chứng **sự hiện diện và số lượng** của từng event.

**Hỏi:** Nếu class ở package đúng và method đúng nhưng gọi bằng `new`, custom Spring aspect có tự chạy không?  
**Đáp:** Không. Spring AOP proxy mode chỉ intercept invocation đi qua Spring proxy, khác với việc class chỉ có tên/package phù hợp.

**Hỏi:** Ma trận P/M/B có đủ để chứng minh một advice luôn được thực thi sau khi caller gọi proxy không?  
**Đáp:** Chưa. Một advisor ngoài có thể short-circuit; pointcut match là điều kiện chọn, execution còn phụ thuộc advisor chain và luồng `proceed()`.

## 16. Tài liệu chính thức

- Spring Framework — Declaring a Pointcut: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Spring Framework — @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — Spring AOP Pointcut API: https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html
- Spring blog — The bean() Pointcut Designator: https://spring.io/blog/2007/09/24/the-new-bean-pointcut/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-practical-workshop-005">Phối hợp nhiều aspect với thứ tự thực thi xác định</a>

<details>
<summary>Xem chi tiết</summary>

# Phối hợp nhiều aspect với thứ tự thực thi xác định trong Spring AOP

## 1. Mục tiêu của bài thực hành tổng hợp

Khi một phương thức Spring bean đồng thời được áp dụng nhiều aspect — ví dụ quan sát (tracing), kiểm tra chính sách (policy) và kiểm toán (audit) — kết quả nghiệp vụ có thể phụ thuộc **aspect nào thực thi trước và aspect nào bao bên ngoài aspect khác**.

Bài này thực hành đúng cơ chế **Spring AOP proxy**:

- Cấu hình **ba class `@Aspect` khác nhau**, cùng match một method trên Spring bean.
- Dùng **`@Order` với giá trị khác nhau** để chỉ định thứ tự precedence một cách tường minh.
- Kiểm chứng **thứ tự khi đi vào** và **thứ tự khi quay ra** của `@Around`.
- So sánh **normal return** với **exception path** bằng một event recorder.
- Viết **JUnit integration tests** kiểm chứng chính xác event sequence, thay vì dựa vào thứ tự log được quan sát tình cờ.
- Chỉ rõ giới hạn: các `@Around` có thể không gọi `proceed()`, những lời gọi không qua proxy không được Spring AOP intercept, thứ tự có thể bị ảnh hưởng khi kết hợp transaction/async và các advisor khác.

Đây là **thực hành phối hợp aspect trong Spring Framework**, không nhắc lại lý thuyết AOP chung hoặc hướng dẫn AspectJ bytecode weaving.

## 2. Quy tắc @Order: số nhỏ bao ngoài số lớn

Theo Spring Framework Reference, khi các advice thuộc **những aspect khác nhau** cùng match một join point:

- **Precedence cao hơn chạy trước trên đường vào**.
- **Precedence cao hơn chạy sau trên đường ra**.
- Class aspect có thể implement `org.springframework.core.Ordered` hoặc khai báo annotation **`org.springframework.core.annotation.Order`**.
- **Giá trị order nhỏ hơn có precedence cao hơn**: `@Order(10)` đứng ngoài `@Order(20)`, còn `@Order(30)` nằm gần target hơn.
- Không quy định order rõ ràng giữa các aspect có thể khiến thứ tự không xác định. Nếu nghiệp vụ yêu cầu thứ tự cụ thể, **đừng dựa vào thứ tự tạo bean, thứ tự file/class trong source, số lần chạy test hay classpath scanning**.

Ví dụ ba `@Around` có thứ tự:

~~~text
Client
  |
  v
Spring AOP proxy
  |
  +-- TraceAspect  @Order(10): before proceed()           [ngoài cùng]
  |     |
  |     +-- PolicyAspect @Order(20): before proceed()
  |     |     |
  |     |     +-- AuditAspect @Order(30): before proceed()
  |     |     |     |
  |     |     |     +-- Target.submit()
  |     |     |     |
  |     |     +-- AuditAspect: after proceed()
  |     |
  |     +-- PolicyAspect: after proceed()
  |
  +-- TraceAspect: after proceed()
  |
  v
Client nhận kết quả
~~~

Trật tự sự kiện bình thường:

~~~text
trace:enter
policy:enter
audit:enter
target:work
audit:exit
policy:exit
trace:exit
~~~

Đây là cấu trúc **lồng như stack** chứ không phải cứ một aspect `@Order(10)` phải log đầu tiên **và** kết thúc đầu tiên. Nó kết thúc **cuối cùng** vì đang bao quanh cả chain.

### Tại sao gọi là precedence, không đơn thuần là thứ tự dòng log?

Với `@Around`, một aspect có thể:

1. Làm điều gì đó **trước** `joinPoint.proceed()`.
2. Quyết định có gọi `proceed()` hay không.
3. Xử lý return value.
4. Bắt exception từ những interceptor nằm **bên trong**.
5. Làm cleanup trong `finally`.

Vì thế `@Order(10)` có ảnh hưởng đến **ai bao quanh ai**, không đơn thuần đến vị trí `System.out.println`. Đối với logic authorization, rate limiting, tracing và transaction, điều này có thể quyết định behavior và lifecycle tài nguyên.

## 3. Dùng @Order ở class aspect, không đoán thứ tự method trong class

Cấu hình:

~~~java
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(10)
public class TraceAspect {
    // @Around advice...
}

@Aspect
@Order(20)
public class PolicyAspect {
    // @Around advice...
}

@Aspect
@Order(30)
public class AuditAspect {
    // @Around advice...
}
~~~

Đây là **fragment** minh họa. Spring đọc `@Order` của aspect để xếp precedence giữa **các aspect**, khi chúng được đăng ký thành bean và Spring AOP auto-proxy bật.

Không dựa vào việc đặt **hai method advice cùng loại trong một `@Aspect`** rồi hy vọng compiler hoặc Spring bảo toàn thứ tự khai báo source code. Với hai `@Before` cùng match cùng join point trong một class `@Aspect`, thứ tự giữa hai method đó **không được đảm bảo**. Nếu cần thứ tự, gộp logic vào một advice hoặc tách thành hai aspect có `@Order` khác nhau.

Với nhiều advice **khác loại trong cùng một aspect**, Spring 6.2+ có quy tắc precedence theo loại `@Around`, `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`; `@After` có semantics kiểu `finally` nên lúc thực thi có thể chạy **sau** `@AfterReturning`/`@AfterThrowing` trong cùng aspect. Đừng suy ra thứ tự output bằng cách sắp các annotation theo số dòng trong source.

**Để xây chuỗi sự kiện hoàn toàn dự đoán được trong bài này, mỗi aspect chỉ có một `@Around` match method mục tiêu.**

## 4. Dự án Java 17+ hoàn chỉnh với Spring AOP và JUnit

Ta tạo ba aspect chạy quanh `OrderService.submit(...)` và một `Events` recorder. Dùng **Spring Framework 6.2.x**, Java 17+, Gradle, JUnit Jupiter. Không dùng Spring Boot để tránh cấu hình AOP auto-configuration ngoài bài.

### 4.1. Cấu trúc thư mục

~~~text
spring-aspects-ordered/
├── settings.gradle
├── build.gradle
└── src
    ├── main/java/demo
    │   ├── OrderService.java
    │   ├── DefaultOrderService.java
    │   ├── Events.java
    │   ├── TraceAspect.java
    │   ├── PolicyAspect.java
    │   ├── AuditAspect.java
    │   ├── AppConfig.java
    │   └── Demo.java
    └── test/java/demo
        └── OrderAspectTest.java
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-aspects-ordered'
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
    implementation platform('org.springframework:spring-framework-bom:6.2.19')
    implementation 'org.springframework:spring-context'
    implementation 'org.springframework:spring-aop'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'

    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.11.4'
}

application {
    mainClass = 'demo.Demo'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

Spring BOM căn chỉnh các module Spring. `aspectjweaver` được Spring AOP dùng cho việc diễn giải `@AspectJ` pointcut; ví dụ **không** chạy AspectJ compiler hay javaagent. `junit-jupiter` và `junit-platform-launcher` dùng cho bài test JUnit 5.

### 4.2. Contract của target

`src/main/java/demo/OrderService.java`:

~~~java
package demo;

public interface OrderService {
    String submit(String orderId);
}
~~~

`src/main/java/demo/DefaultOrderService.java`:

~~~java
package demo;

public final class DefaultOrderService implements OrderService {
    private final Events events;

    public DefaultOrderService(Events events) {
        this.events = events;
    }

    @Override
    public String submit(String orderId) {
        events.add("target:" + orderId);
        if ("fail".equals(orderId)) {
            throw new IllegalStateException("rejected");
        }
        return "accepted:" + orderId;
    }
}
~~~

Target `final` nhưng implement interface, vì vậy Spring có thể dùng **JDK dynamic proxy** khi `proxyTargetClass=false`. Một `CGLIB` subclass không thể kế thừa target `final`; ở đây **không cần subclass**.

### 4.3. Recorder ghi sự kiện theo đúng thứ tự

`src/main/java/demo/Events.java`:

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

public final class Events {
    private final List<String> records = new ArrayList<>();

    public void add(String event) {
        records.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(records);
    }

    public void clear() {
        records.clear();
    }
}
~~~

`Events` phục vụ demo **đồng bộ, tuần tự**. `ArrayList` không thread-safe, nên khi đưa vào production hoặc test đồng thời phải thay bằng collector thread-safe/correlation per invocation. Không để một global mutable recorder trộn event của nhiều request rồi kết luận order sai.

### 4.4. TraceAspect ngoài cùng: @Order(10)

`src/main/java/demo/TraceAspect.java`:

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(10)
public class TraceAspect {
    private final Events events;

    public TraceAspect(Events events) {
        this.events = events;
    }

    @Around("execution(* demo.DefaultOrderService.submit(..))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        events.add("trace:enter");
        try {
            Object result = pjp.proceed();
            events.add("trace:exit");
            return result;
        } catch (Throwable error) {
            events.add("trace:error");
            throw error;
        }
    }
}
~~~

Thứ tự `@Order(10)` là precedence **cao nhất trong ba aspect**. `trace` có thể nhận exception từ bất kỳ phần nào **bên trong** nó, miễn exception được propagate ra khỏi `proceed()`.

### 4.5. PolicyAspect ở giữa: @Order(20)

`src/main/java/demo/PolicyAspect.java`:

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(20)
public class PolicyAspect {
    private final Events events;

    public PolicyAspect(Events events) {
        this.events = events;
    }

    @Around("execution(* demo.DefaultOrderService.submit(..))")
    public Object policy(ProceedingJoinPoint pjp) throws Throwable {
        events.add("policy:enter");
        try {
            Object result = pjp.proceed();
            events.add("policy:exit");
            return result;
        } catch (Throwable error) {
            events.add("policy:error");
            throw error;
        }
    }
}
~~~

Ở đây policy chỉ ghi nhận đường đi để có output xác định. Trong ứng dụng thật, authorization aspect có thể từ chối bằng exception **trước khi gọi `proceed()`**. Nếu điều đó xảy ra, những aspect nằm **bên trong** policy và target **không chạy**; không được viết test kỳ vọng audit/target luôn có event.

### 4.6. AuditAspect gần target nhất: @Order(30)

`src/main/java/demo/AuditAspect.java`:

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(30)
public class AuditAspect {
    private final Events events;

    public AuditAspect(Events events) {
        this.events = events;
    }

    @Around("execution(* demo.DefaultOrderService.submit(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        events.add("audit:enter");
        try {
            Object result = pjp.proceed();
            events.add("audit:exit");
            return result;
        } catch (Throwable error) {
            events.add("audit:error");
            throw error;
        }
    }
}
~~~

Audit chạy sát target nên thấy cả exception được target ném ra nếu không bị xử lý trước đó. Nhưng đặt audit **bên trong** policy có nghĩa khi policy chặn không gọi `proceed()`, audit sẽ không chạy. Chọn vị trí dựa trên yêu cầu nghiệp vụ: muốn audit cả request bị chặn thì phải sắp xếp hoặc thiết kế audit phù hợp; không chỉ nhìn tên aspect để quyết định.

### 4.7. Đăng ký tất cả aspect thành Spring bean và bật auto-proxy

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
    public Events events() {
        return new Events();
    }

    @Bean
    public OrderService orderService(Events events) {
        return new DefaultOrderService(events);
    }

    @Bean
    public TraceAspect traceAspect(Events events) {
        return new TraceAspect(events);
    }

    @Bean
    public PolicyAspect policyAspect(Events events) {
        return new PolicyAspect(events);
    }

    @Bean
    public AuditAspect auditAspect(Events events) {
        return new AuditAspect(events);
    }
}
~~~

`@Aspect` **không tự đăng ký Spring bean**. Ở đây ta khai báo ba `@Bean`, vì vậy không cần `@Component`/`@ComponentScan`. `@EnableAspectJAutoProxy` khiến Spring tạo proxy khi target method match các aspect.

### 4.8. Chương trình chính in kết quả thành công và lỗi

`src/main/java/demo/Demo.java`:

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class Demo {

    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            OrderService service = context.getBean(OrderService.class);
            Events events = context.getBean(Events.class);

            System.out.println("proxy: " + AopUtils.isAopProxy(service));

            System.out.println("success: " + service.submit("A"));
            System.out.println("success-events: " + events.snapshot());

            events.clear();
            try {
                service.submit("fail");
            } catch (IllegalStateException ex) {
                System.out.println("failure: " + ex.getMessage());
            }
            System.out.println("failure-events: " + events.snapshot());
        }
    }
}
~~~

Chạy với **JDK 17+** và Gradle từ thư mục project:

~~~bash
gradle run
~~~

Nếu có Gradle Wrapper, dùng `./gradlew run` (macOS/Linux) hoặc `gradlew.bat run` (Windows). **Stdout của ứng dụng dự kiến** (không tính thông báo Gradle/Spring):

~~~text
proxy: true
success: accepted:A
success-events: [trace:enter, policy:enter, audit:enter, target:A, audit:exit, policy:exit, trace:exit]
failure: rejected
failure-events: [trace:enter, policy:enter, audit:enter, target:fail, audit:error, policy:error, trace:error]
~~~

### 4.9. Giải thích thứ tự success path

1. Caller gọi **Spring proxy** `service.submit("A")`.
2. Aspect `@Order(10)` là ngoài cùng: ghi `trace:enter`.
3. `trace.proceed()` gọi chain tiếp theo: `@Order(20)` ghi `policy:enter`.
4. `policy.proceed()` gọi `@Order(30)` ghi `audit:enter`.
5. `audit.proceed()` gọi target: ghi `target:A`, trả `accepted:A`.
6. Audit ghi `audit:exit`, trả kết quả về policy.
7. Policy ghi `policy:exit`, trả về trace.
8. Trace ghi `trace:exit`, trả về caller.

Tập event có cấu trúc **đảo ngược khi đi ra**, vì lớp ngoài cùng chỉ nhận kết quả sau khi lớp bên trong hoàn tất.

### 4.10. Giải thích thứ tự exception path

1. Cả ba aspect đều ghi `:enter` trước khi target chạy.
2. Target ghi `target:fail` rồi ném `IllegalStateException("rejected")`.
3. Exception đi ngược chain qua `audit`, `policy`, `trace`.
4. Mỗi aspect bắt `Throwable` trong `catch`, ghi `audit:error`, `policy:error`, `trace:error` rồi **ném lại cùng exception**.
5. Caller bắt `IllegalStateException`, in `failure: rejected`.
6. Không có `audit:exit`, `policy:exit`, `trace:exit` vì phần đó chỉ chạy trên normal return.

Đây là một thiết kế minh họa rõ **success và exception control flow**. Đối với cleanup bắt buộc cả hai nhánh, dùng `finally` thay vì chỉ đặt cleanup sau `proceed()`. Nhưng các event `:exit` trong ví dụ cố ý chỉ có ở thành công, để hai nhánh khác nhau dễ kiểm chứng.

## 5. Viết JUnit tests xác nhận order thật, không đoán theo log

`src/test/java/demo/OrderAspectTest.java`:

~~~java
package demo;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class OrderAspectTest {

    @Test
    void successUnwindsInReverseOrder() {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService bean = context.getBean(OrderService.class);
            Events events = context.getBean(Events.class);

            assertTrue(AopUtils.isAopProxy(bean));
            assertEquals("accepted:A", bean.submit("A"));
            assertEquals(
                    List.of(
                        "trace:enter", "policy:enter", "audit:enter",
                        "target:A", "audit:exit", "policy:exit",
                        "trace:exit"
                    ),
                    events.snapshot()
            );
        }
    }

    @Test
    void failuresUnwindInReverseOrder() {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService bean = context.getBean(OrderService.class);
            Events events = context.getBean(Events.class);

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> bean.submit("fail")
            );
            assertEquals("rejected", error.getMessage());
            assertEquals(
                    List.of(
                        "trace:enter", "policy:enter", "audit:enter",
                        "target:fail", "audit:error", "policy:error",
                        "trace:error"
                    ),
                    events.snapshot()
            );
        }
    }

    @Test
    void rawObjectDoesNotRunSpringAspects() {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            Events events = context.getBean(Events.class);
            OrderService raw = new DefaultOrderService(events);

            assertFalse(AopUtils.isAopProxy(raw));
            assertEquals("accepted:RAW", raw.submit("RAW"));
            assertEquals(List.of("target:RAW"), events.snapshot());
        }
    }
}
~~~

Chạy:

~~~bash
gradle test
~~~

Dùng Wrapper tương ứng nếu có. **Kết quả dự kiến:** **3 JUnit tests pass**, Gradle báo `BUILD SUCCESSFUL`. Đầu ra là kỳ vọng theo mã và Spring AOP semantics; bài viết **chưa tự chạy Gradle** trong một JVM độc lập.

Các assertion chứng minh:

- Method được gọi trên bean **đúng là Spring AOP proxy**.
- Chính event sequence của **ba aspect** có order xác định, không chỉ business return value.
- Success và failure có thứ tự unwind khác nhau nhưng cùng quy tắc precedence.
- Raw target tạo trực tiếp chỉ ghi event `target:RAW`, không chạy Spring AOP advice.

Khi test parallel, nên tạo context/recorder độc lập từng test như ở đây hoặc dùng data collector thread-safe. Spring TestContext có thể cache context, nên nếu chuyển sang `@SpringJUnitConfig` cần reset recorder ở `@BeforeEach` hoặc thiết kế per-test state để tránh test phụ thuộc thứ tự.

## 6. Khi một aspect không gọi proceed()

`@Around` có quyền không gọi `ProceedingJoinPoint.proceed()`. Vì thế **@Order còn kiểm soát aspect nào được chạy trước khi request bị short-circuit**.

Ví dụ **fragment** thay thế method advice của `PolicyAspect`:

~~~java
@Around("execution(* demo.DefaultOrderService.submit(..)) && args(orderId)")
public Object policy(ProceedingJoinPoint pjp, String orderId)
        throws Throwable {
    events.add("policy:enter");

    if ("blocked".equals(orderId)) {
        events.add("policy:blocked");
        throw new SecurityException("denied");
    }

    Object result = pjp.proceed();
    events.add("policy:exit");
    return result;
}
~~~

Fragment không chạy độc lập; nó dùng field `events` của `PolicyAspect` đã khai báo ở ví dụ chính và thêm logic minh họa. Với `"blocked"`:

~~~text
trace:enter
policy:enter
policy:blocked
trace:error
~~~

`AuditAspect @Order(30)` và target **không chạy** vì `PolicyAspect @Order(20)` không gọi `proceed()`. Nếu có yêu cầu audit cả những request bị từ chối, có thể cần chuyển audit thành aspect **bên ngoài policy**, đưa audit vào chính policy hoặc tách concern đúng vị trí; chỉ sửa `@Order` của audit cần được test vì có thể làm thay đổi visibility của exception/outcome.

**Hệ quả quan trọng:** với chuỗi aspect, outer aspect có thể quan sát exception do inner aspect ném; inner aspect không thể quan sát method bị outer aspect chặn trước `proceed()`, vì bản thân inner aspect không được gọi.

## 7. Đừng chọn @Order tùy tiện — hãy xác định requirement trước

Một thứ tự thực thi tốt xuất phát từ mục tiêu. Ví dụ thực tế:

| Requirement | Điều cần quyết định | Hệ quả với thứ tự |
| --- | --- | --- |
| Trace toàn bộ request, kể cả bị từ chối | Trace cần bao ngoài policy | Trace có precedence cao hơn policy |
| Audit cả request bị từ chối | Audit cần thấy nhánh policy rejection | Audit có thể cần bao ngoài policy, không đặt sát target như demo |
| Audit chỉ các operation target thực sự chạy | Chỉ cần chạy khi policy cho phép | Audit có thể nằm bên trong policy |
| Validation thất bại không được mở transaction | Validation chạy trước khi transaction bắt đầu | Xem vị trí validation aspect so với transaction advisor |
| Đo latency tổng gồm transaction commit/rollback | Timer cần bao quanh transaction advisor tương ứng | Xác minh cụ thể advisor ordering |
| Retry method nếu lỗi transient | Retry advice có thể gọi `proceed()` nhiều lần | Xác định transaction/advice bên trong retry chain để mỗi attempt có lifecycle đúng |
| Limit rate trước hoạt động tốn tài nguyên | Rate limiting đứng ngoài task/resource allocation | Precedence cao hơn layer cần chặn |
| Ghi metrics cả exception | Observer dùng `try/finally` hoặc `catch` đúng | Không chỉ dựa vào `@AfterReturning` |

**Không có một bộ số `@Order` tối ưu cho mọi hệ thống.** `@Order(10/20/30)` trong bài chỉ để minh họa thứ tự. Với nhiều nhóm thư viện, định nghĩa các hằng số/nhóm order có ý nghĩa và review chúng cùng transaction/security/async infrastructure, tránh tản mát magic numbers.

### Khi kết hợp @Transactional

Transaction advice cũng là một interceptor trong chain. Spring cho phép định cấu hình order của transaction advisors, chẳng hạn với `@EnableTransactionManagement(order=...)`; mặc định thường có precedence thấp (`Ordered.LOWEST_PRECEDENCE`). Kết quả với aspect của ứng dụng phụ thuộc **thứ tự tất cả advisors** chứ không chỉ ba aspect trong bài.

Nếu outer tracing muốn đo cả commit/rollback, cần đặt ngoài transaction interceptor. Nếu audit muốn nằm trong transaction để rollback cùng nghiệp vụ, cần làm rõ khả năng effect được transaction quản lý (ví dụ DB write); chỉ đổi thứ tự không tự khiến mọi log hoặc external call rollback.

### Khi kết hợp @Async

`@Async` có interceptor liên quan executor. Aspect đặt ngoài async interceptor có thể chạy ở caller khi submit task, còn aspect nằm trong có thể chạy trên worker. Đừng suy ra “trace:exit” nghĩa task async đã hoàn tất nếu method chỉ trả Future handle. Khi đo end-to-end completion, cần observer trên `CompletionStage`/Future phù hợp và test thread/ordering trên runtime.

### Khi có caching, security hoặc retry

Các infrastructure advisors khác có thể nằm trong cùng chain, tạo cache hit không đi tới target hoặc policy từ chối sớm, gây khác số lượng event. Điều đó có thể là **hành vi đúng**. Test cần xác nhận mục tiêu thực: “observer thấy request” khác “auditor thấy target execution”.

## 8. Khi nhiều advice thuộc cùng một @Aspect

Trong Spring AOP, **thứ tự giữa nhiều advice cùng loại ở cùng một `@Aspect` áp cùng join point không được cam kết**. Ví dụ:

~~~java
@Aspect
public class FragileAspect {

    @Before("execution(* demo.DefaultOrderService.submit(..))")
    public void first() {
        // Không bảo đảm method này luôn chạy trước second().
    }

    @Before("execution(* demo.DefaultOrderService.submit(..))")
    public void second() {
    }
}
~~~

Đây là fragment cần imports; tên `first`/`second` và vị trí source **không tạo ra hợp đồng ordering**. Nếu hai việc có dependency, làm một trong hai cách:

- Gộp cả hai bước vào **một advice**, gọi tuần tự trong body.
- Tách thành **hai class `@Aspect`**, đặt `@Order` khác nhau và integration test outcome.

Khi mix `@Around`, `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`, đọc mục **Advice Ordering** của Spring Reference đúng phiên bản; không coi tất cả các `after` như một method chạy theo thứ tự source. Với use case cần bắt exception và cleanup chính xác, một `@Around` với `try/catch/finally` thường dễ quan sát hơn việc phân phối state sang nhiều advice methods.

Spring có cơ chế metadata khác cho XML `<aop:aspect>`: thứ tự giữa các advice element cùng một `<aop:aspect>` có thể được xác định bằng **thứ tự khai báo XML**, khác với hai `@Before` cùng một Java `@Aspect`. Không lấy quy tắc XML áp máy móc lên Java annotation.

## 9. Ordered, @Order và giá trị bằng nhau

`@Order` được Spring dùng để xác định precedence. Một aspect có thể implement interface `Ordered` để trả thứ tự động:

~~~java
// Fragment thay cho @Order class-level:
@Aspect
public class DynamicOrderedAspect implements org.springframework.core.Ordered {
    @Override
    public int getOrder() {
        return 50;
    }
}
~~~

Đây là fragment thiếu advice, không phải ví dụ độc lập. Trong tình huống cấu hình phức tạp, cần hiểu framework chọn metadata nào, và tránh vừa đặt `@Order` vừa dùng `Ordered` rồi dựa vào một giá trị tình cờ được ưu tiên. Nếu order là bất biến cấu hình, `@Order` trên class aspect thường đơn giản hơn.

**Hai aspect cùng order không tạo thứ tự ổn định giữa chúng.** Spring có thể xếp chúng theo cơ chế secondary sorting/registration, nhưng **đó không phải contract nghiệp vụ**. Nếu phải có A trước B, cho A và B **hai precedence khác nhau** và viết test rõ.

Cũng không nên đặt tất cả aspect ở `Ordered.HIGHEST_PRECEDENCE`: chúng lại có cùng độ ưu tiên, mất khả năng định nghĩa quan hệ với nhau. Đừng lạm dụng `Integer.MIN_VALUE` nếu nhiều framework advisors đang cần một khoảng precedence riêng.

## 10. Những lỗi phổ biến khi phối hợp nhiều aspect

### 10.1. Chỉ viết @Aspect mà chưa đăng ký bean

`@Aspect` tự nó không đưa class vào `ApplicationContext`. Đăng ký bằng `@Component` + component scanning hoặc `@Bean`. Ví dụ chính dùng ba `@Bean` tường minh.

### 10.2. Quên bật Spring AOP

Trong Spring Framework thuần cần `@EnableAspectJAutoProxy` (hoặc XML tương đương) và dependency AspectJ phù hợp. Với Spring Boot, AOP auto-configuration có thể kích hoạt; kiểm tra thực tế thay vì đoán.

### 10.3. Đoán thứ tự từ tên class hay thứ tự khai báo @Bean

Đổi tên `AuditAspect` thành `AAuditAspect` hoặc đặt `@Bean auditAspect` trước `traceAspect` không phải cách xác định Spring advice precedence. Dùng `@Order`/`Ordered`.

### 10.4. Nghĩ số nhỏ chạy trước cả khi vào lẫn khi ra

Đây là lỗi phổ biến nhất. Với nested `@Around`: số nhỏ **vào trước nhưng ra sau**. Mỗi `proceed()` tương đương đi xuống interceptor tiếp theo; unwind diễn ra ngược lại.

### 10.5. Bỏ qua exception path

Nếu chỉ ghi `:exit` sau `proceed()` mà target ném exception, dòng `:exit` không chạy. Dùng `catch` để log lỗi và rethrow; dùng `finally` cho cleanup bắt buộc. Không nuốt lỗi chỉ để test event order xanh.

### 10.6. Gọi proceed() hai lần hoặc không gọi

`@Around` có quyền gọi `proceed()` zero hoặc nhiều lần, nhưng quyết định đó ảnh hưởng số lần target/advice bên trong thực thi. Nếu không cố ý retry/short-circuit, **nên gọi đúng một lần** và preserve result/exception. Trong ứng dụng có transaction, việc gọi nhiều lần có thể tạo nhiều attempt/resources; phải test riêng.

### 10.7. Một aspect ở ngoài chặn request trước aspect bên trong

Nếu policy ở ngoài audit và từ chối không gọi proceed, audit không thấy request. Hãy quyết định audit nào là mandatory, audit nào chỉ cần ghi operation đã thực thi.

### 10.8. Self-invocation hoặc raw target

`this.submit()` bên trong target hoặc `new DefaultOrderService(...)` ngoài Spring không đi qua proxy, nên các aspect Spring AOP không chạy. Cần lấy bean từ Spring context và gọi qua proxy. AspectJ bytecode weaving có cơ chế khác; bài này không sử dụng weaving.

### 10.9. Pointcut rộng dẫn tới advice trên cả infrastructure

Pointcut `execution(* *(..))` quá rộng có thể áp lên nhiều bean/call không chủ đích, tăng overhead và làm debug order khó khăn. Trong ví dụ, ba aspect match **đúng một method của target**.

### 10.10. Test bằng System.out khi chạy nhiều thread

Output console trong hệ thống concurrent có thể xen kẽ dù **trong từng invocation** order đúng. Để kiểm chứng, dùng event recorder gắn correlation ID theo request hoặc một integration test đồng bộ, so sequence per invocation.

### 10.11. Chỉ assert bean là proxy

`AopUtils.isAopProxy(service)` không tự chứng minh aspect đã match và chạy đúng thứ tự. Một proxy có thể do advisor khác tạo. Phải gọi method match và assert **events** của từng aspect.

## 11. Cách kiểm thử thứ tự trong dự án thực tế

Checklist cho một suite đủ tin cậy:

| Kiểm tra | Bằng chứng cần có |
| --- | --- |
| Bean được Spring quản lý | `ApplicationContext.getBean(...)` cho đúng interface |
| Spring AOP proxy hoạt động | `AopUtils.isAopProxy(bean)` và behavior của aspect |
| Mỗi aspect có order rõ ràng | `@Order`/`Ordered`, tránh tie cho aspects có dependency |
| Normal return | Order vào 10→20→30, order ra 30→20→10 |
| Exception | Order vào 10→20→30, catch/error 30→20→10 |
| Short-circuit | Những aspect bên trong point chặn không chạy |
| Method không match | Không có advice event |
| Raw target | Business method chạy, không có Spring AOP advice |
| Advisor hạ tầng | Transaction/async/cache/security order được test theo requirement |
| Pointcut specificity | Không áp vào target ngoài phạm vi mong muốn |
| Reentrant/call lại | Không sinh event trùng ngoài yêu cầu |
| Concurrent requests | Event mỗi invocation có ID, không so thứ tự liên request |
| Nâng Spring | Re-run trên Spring/Boot version đích, giữ behavior contract |

**Không cần test chính xác class name của Spring proxy** (`jdk.proxy...` hoặc Spring CGLIB generated name). Spring có thể đổi implementation khi cấu hình proxy/upgrade. Cái cần ổn định là **interface được expose, outcome và thứ tự advice có chủ đích**.

### Nếu có nhiều advisor, test ra sao?

Đầu tiên test **ba aspect của ứng dụng** trong context nhỏ như ví dụ, rồi mới integration test ở context có `@Transactional`, `@Async` hoặc caching. Nếu thứ tự bỗng khác, kiểm tra:

1. ApplicationContext có tạo đủ aspect beans và AOP proxy không?
2. Aspect có thật sự được match vào method không?
3. Có advice nào short-circuit khiến inner aspect không chạy không?
4. Advisor ordering có bị thay đổi bởi cấu hình infrastructure không?
5. Event recorder có reset/correlation theo từng test không?
6. Test đang gọi Spring bean proxy hay raw instance?
7. Method có return Future chưa xong khiến phần async completion tách khỏi invocation không?

Nếu production cần contract ordering qua nhiều frameworks, **đừng để test chỉ chạy với `@EnableAspectJAutoProxy` đơn lẻ** rồi suy ra đầy đủ.

## 12. Ghi nhớ

- **`@Order` số nhỏ có precedence cao** giữa những `@Aspect` khác nhau trong Spring AOP.
- Với nested `@Around`, **vào từ order nhỏ đến lớn; ra từ order lớn về nhỏ**.
- `@Around` có thể ném lỗi, chặn invocation hoặc gọi `proceed()` nhiều lần; thứ tự thực tế còn phụ thuộc quyết định đó.
- **Không dựa vào thứ tự khai báo hai advice cùng loại trong một Java `@Aspect`**.
- Đăng ký aspect thành Spring bean, bật auto-proxy và gọi đúng Spring proxy mới kiểm tra được advice.
- Khi kết hợp transaction/security/async/cache/retry, chọn precedence dựa trên requirement và viết integration tests, không dùng một dãy order magic numbers cho mọi môi trường.
- Assert **chuỗi sự kiện cụ thể** cho success, failure và short-circuit để bảo vệ thứ tự trước regression.

**Kết luận:** phối hợp nhiều aspect trong Spring AOP là thiết kế **interceptor chain có thứ tự**, không phải chỉ gắn nhiều annotation `@Aspect`. Khi xác định rõ lớp nào là outer/inner bằng `@Order` và kiểm thử hai chiều `proceed()` bằng JUnit, chúng ta có thể kiểm soát luồng advice một cách có chủ đích, đồng thời nhận ra khi nào advisor khác, proxy boundaries hoặc async execution làm thay đổi kết quả.

## Nguồn chính thức để đối chiếu

- [Spring Framework 6.2 — Declaring Advice và Advice Ordering](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework — Spring AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — AOP Pointcut API](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html)
- [Spring Framework — Spring Transaction Order](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
- [Spring Framework — Task Execution and @Async](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)

Các ví dụ sử dụng Spring Framework 6.2.x và JDK 17+. Khi nâng Spring Framework hoặc Spring Boot, hãy xác minh lại cấu hình proxy, advisor order và kết quả integration test trên phiên bản thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-practical-workshop-006">Mô phỏng self-invocation và sửa bằng tách bean cộng tác</a>

<details>
<summary>Xem chi tiết</summary>

# Mô phỏng self-invocation và sửa bằng tách bean cộng tác trong Spring AOP

## 1. Tình huống thực hành và mục tiêu

Một ứng dụng Spring dùng `@Aspect` để ghi audit, đo thời gian xử lý, thực thi chính sách kiểm soát hoặc khai báo transaction. Ta thấy một method được Spring advice khi gọi từ controller, nhưng **không được advice khi một method khác trong cùng service gọi nó**. Đây là dấu hiệu phổ biến của **self-invocation** trong Spring AOP dùng proxy.

Mục tiêu bài thực hành:

1. Dựng được một Spring context dùng `@EnableAspectJAutoProxy`, `@Aspect` và một service có **hai public methods**.
2. Quan sát lời gọi `service.place(...) -> this.finish(...)` đi thẳng trong target và **bỏ qua advice dành cho `finish`**.
3. Làm một **positive control**: gọi `finish(...)` từ bên ngoài qua bean proxy thì advice chạy, chứng minh aspect/pointcut được cấu hình đúng.
4. Refactor: chuyển `finish(...)` thành trách nhiệm của **một Spring bean cộng tác riêng**, service bên ngoài inject interface của bean này.
5. Kiểm thử cả **before/after**, return value, số lần advice và ca âm tính để chứng minh sau refactor advice chạy **đúng một lần**.
6. Biết khi nào nên chuyển boundary trong Spring, khi nào không nên tự inject chính bean hoặc dùng `AopContext.currentProxy()`.

**Phạm vi:** vấn đề invocation qua **Spring AOP proxy** và cách thiết kế Spring bean. Không cần dùng AspectJ bytecode weaving hay giảng lại những join point ngoài khả năng Spring AOP.

## 2. Vì sao self-invocation không kích hoạt advice?

Spring AOP mặc định gắn interceptors vào một **proxy** bên ngoài target. Caller gọi method trên Spring bean được inject, nếu bean đó là proxy và advisor khớp method, advice sẽ chạy trước/sau target.

Với service chưa refactor:

~~~text
Caller
  -> [Spring AOP proxy của LegacyPort].place("A")
        -> LegacyService.place("A")
              -> this.finish("A")
                    -> LegacyService.finish("A")

Method place() đi qua proxy.
Method finish() được gọi trực tiếp qua this của target.
Pointcut chỉ match finish(): không có advice trên lần gọi nội bộ.
~~~

Đối chứng:

~~~text
Caller
  -> [Spring AOP proxy của LegacyPort].finish("B")
        -> @Before advice
        -> LegacyService.finish("B")

Lần này finish() đi qua proxy, nên advice chạy.
~~~

Điểm quyết định là **đường gọi**, không phải việc method có `public` hay expression `execution(...)` “trông đúng”. Ví dụ cả `place` và `finish` đều public nhưng pointcut chỉ nhằm `finish`; advice không tự xen vào lời gọi `this.finish()` đã xảy ra **sau khi proxy chuyển quyền điều khiển vào target**.

Tương tự, đổi `proxyTargetClass=false` (JDK dynamic proxy) thành `proxyTargetClass=true` (class-based proxy) **không giải quyết self-invocation**. Cả hai vẫn dùng Spring proxy boundary.

| Tình huống | Lời gọi đi qua Spring proxy? | Advice cho `finish` |
| --- | --- | --- |
| Controller gọi `legacyBean.finish("B")`, bean đã được proxy | Có | Có, nếu pointcut khớp |
| `LegacyService.place` gọi `this.finish("A")` | Không | Không |
| `LegacyService.place` gọi ngầm `finish("A")` không ghi `this` | Không | Không |
| Caller gọi `new LegacyService(...).finish(...)` | Không có Spring proxy | Không |
| `CheckoutService` gọi `finishPort.finish(...)`, `finishPort` là proxy của bean khác | Có | Có, nếu pointcut khớp |
| Service gọi private `finishInternal()` | Không thể intercept bằng Spring method proxy như một external public method | Không |

**Chú ý:** Không phải *mọi* self-invocation đều sai. Nếu không cần interceptor ở method nội bộ, giữ nguyên lời gọi `this` có thể hợp lý. Chỉ refactor khi hợp đồng nghiệp vụ thực sự đòi lời gọi đó đi qua advice, ví dụ ranh giới audit, security hoặc transaction.

## 3. Thiết kế sửa bằng bean cộng tác

Tách operation cần advice thành một bean có **interface nhỏ**. Bean điều phối chỉ gọi qua dependency được Spring inject.

~~~text
Caller
  -> CheckoutPort.place("A")  [CheckoutService]
        -> FinishPort.finish("A")  [Spring proxy]
              -> @Before FinishAspect
              -> FinishService.finish("A")

CheckoutService --depends on--> FinishPort
FinishAspect --depends on--> Events (probe/observer)
FinishService --depends on--> Events

Không có CheckoutService -> FinishAspect hoặc FinishAspect -> CheckoutService.
~~~

Ưu điểm:

- Lời gọi tới `FinishPort` đi qua **một Spring bean khác** được container quản lý, nên advice có thể intercept.
- Mỗi service có một trách nhiệm tương đối rõ. Có thể kiểm thử logic điều phối độc lập với logic cần advice.
- Tránh dùng `AopContext`, self-injection hoặc cấu hình `exposeProxy` chỉ để đi vòng qua giới hạn proxy.
- Giảm nguy cơ vòng phụ thuộc: aspect không cần inject target/facade; nó chỉ ghi event vào một probe hoặc sink độc lập.
- Giữ khả năng áp dụng `@Transactional`, `@Async`, caching, security hoặc AOP advice ở boundary bean rõ ràng khi cơ chế tương ứng được bật đúng.

**Điểm cần cân nhắc:** tách bean là thay đổi boundary gọi. Phải xác định transaction propagation, exception handling, thread và dữ liệu trả về; không nên mặc định rằng chuyển một method sang bean mới luôn tương đương về nghiệp vụ. Bộ test dưới đây cố tình đo kết quả và số lần advice để chứng minh phần thay đổi mong muốn.

## 4. Dự án chạy được: Spring AOP và JUnit 5

Ví dụ dùng **Java 21**, Gradle, Spring Framework qua BOM Spring Boot 3.3.5. Test tự tạo hai `AnnotationConfigApplicationContext` độc lập; không dùng Spring Boot main class, database, web server hoặc AspectJ LTW.

### 4.1. Cấu trúc

~~~text
spring-self-invocation-lab/
├── settings.gradle
├── build.gradle
└── src/test/java/com/example/selfcall/
    └── SelfInvocationRefactorTest.java
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-self-invocation-lab'
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

`spring-boot-starter-aop` đưa các dependency Spring AOP và AspectJ annotation support cần thiết vào test classpath. `@EnableAspectJAutoProxy` bật auto-proxy rõ ràng trong từng context; annotation `@Aspect` tự nó **chưa đăng ký một Spring bean**, nên mỗi config vẫn phải khai báo aspect bằng `@Bean`.

### 4.2. Một file Java gồm test và fixture

Lưu toàn bộ đoạn sau thành `src/test/java/com/example/selfcall/SelfInvocationRefactorTest.java`.

Chỉ class test là `public`; các interface và class phụ đều **package-private** trong cùng package/file. Mỗi context có **Events riêng** nên kết quả test không phụ thuộc thứ tự chạy và không chia sẻ counter với test khác.

~~~java
package com.example.selfcall;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SelfInvocationRefactorTest {

    // TRƯỚC: một bean tự gọi method cần advice.
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    static class BeforeConfig {
        @Bean
        Events events() {
            return new Events();
        }

        @Bean
        LegacyPort legacyPort(Events events) {
            return new LegacyService(events);
        }

        @Bean
        LegacyAspect legacyAspect(Events events) {
            return new LegacyAspect(events);
        }
    }

    // SAU: điều phối và thao tác cần advice là hai bean độc lập.
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    static class AfterConfig {
        @Bean
        Events events() {
            return new Events();
        }

        @Bean
        FinishPort finishPort(Events events) {
            return new FinishService(events);
        }

        @Bean
        CheckoutPort checkoutPort(FinishPort finishPort, Events events) {
            return new CheckoutService(finishPort, events);
        }

        @Bean
        FinishAspect finishAspect(Events events) {
            return new FinishAspect(events);
        }
    }

    @Test
    void beforeRefactorInternalCallBypassesAdvice() {
        try (var context =
                new AnnotationConfigApplicationContext(BeforeConfig.class)) {

            LegacyPort legacy = context.getBean(LegacyPort.class);
            Events events = context.getBean(Events.class);

            // Positive: Spring thực sự tạo proxy cho legacy bean.
            assertTrue(AopUtils.isAopProxy(legacy));
            assertTrue(AopUtils.isJdkDynamicProxy(legacy));

            // Gọi place từ bên ngoài; bên trong gọi this.finish.
            assertEquals("done:A", legacy.place("A"));
            assertEquals(
                List.of("place:A", "finish:A"),
                events.snapshot()
            );

            // Positive control: gọi finish trực tiếp qua Spring proxy.
            events.clear();
            assertEquals("done:B", legacy.finish("B"));
            assertEquals(
                List.of("advice:legacy:B", "finish:B"),
                events.snapshot()
            );
        }
    }

    @Test
    void afterRefactorCollaboratorProxyRunsAdviceExactlyOnce() {
        try (var context =
                new AnnotationConfigApplicationContext(AfterConfig.class)) {

            CheckoutPort checkout = context.getBean(CheckoutPort.class);
            FinishPort finish = context.getBean(FinishPort.class);
            Events events = context.getBean(Events.class);

            // FinishPort là proxy; service điều phối không cần advice riêng.
            assertTrue(AopUtils.isAopProxy(finish));
            assertTrue(AopUtils.isJdkDynamicProxy(finish));
            assertFalse(AopUtils.isAopProxy(checkout));

            assertEquals("done:A", checkout.place("A"));
            assertEquals(
                List.of("place:A", "advice:finish:A", "finish:A"),
                events.snapshot()
            );

            // Negative control: method không nằm trong pointcut.
            assertEquals("ready", finish.status());
            assertEquals(
                List.of("place:A", "advice:finish:A", "finish:A"),
                events.snapshot()
            );

            events.clear();
            assertEquals("done:B", checkout.place("B"));
            assertEquals(
                List.of("place:B", "advice:finish:B", "finish:B"),
                events.snapshot()
            );
        }
    }
}

// TRƯỚC REFACTOR

interface LegacyPort {
    String place(String id);
    String finish(String id);
}

class LegacyService implements LegacyPort {
    private final Events events;

    LegacyService(Events events) {
        this.events = events;
    }

    @Override
    public String place(String id) {
        events.add("place:" + id);
        return this.finish(id); // Self-invocation: bỏ qua proxy.
    }

    @Override
    public String finish(String id) {
        events.add("finish:" + id);
        return "done:" + id;
    }
}

@Aspect
class LegacyAspect {
    private final Events events;

    LegacyAspect(Events events) {
        this.events = events;
    }

    @Before(
        value = "execution(* com.example.selfcall.LegacyPort.finish(..))"
                + " && args(id)",
        argNames = "id"
    )
    public void beforeFinish(String id) {
        events.add("advice:legacy:" + id);
    }
}

// SAU REFACTOR

interface CheckoutPort {
    String place(String id);
}

interface FinishPort {
    String finish(String id);
    String status();
}

class CheckoutService implements CheckoutPort {
    private final FinishPort finishPort;
    private final Events events;

    CheckoutService(FinishPort finishPort, Events events) {
        this.finishPort = finishPort;
        this.events = events;
    }

    @Override
    public String place(String id) {
        events.add("place:" + id);
        // finishPort là Spring bean được inject; lời gọi đi qua proxy.
        return finishPort.finish(id);
    }
}

class FinishService implements FinishPort {
    private final Events events;

    FinishService(Events events) {
        this.events = events;
    }

    @Override
    public String finish(String id) {
        events.add("finish:" + id);
        return "done:" + id;
    }

    @Override
    public String status() {
        return "ready";
    }
}

@Aspect
class FinishAspect {
    private final Events events;

    FinishAspect(Events events) {
        this.events = events;
    }

    @Before(
        value = "execution(* com.example.selfcall.FinishPort.finish(..))"
                + " && args(id)",
        argNames = "id"
    )
    public void beforeFinish(String id) {
        events.add("advice:finish:" + id);
    }
}

// Probe không gọi ngược bean cần advice, tránh recursion.

class Events {
    private final List<String> entries = new CopyOnWriteArrayList<>();

    void add(String entry) {
        entries.add(entry);
    }

    List<String> snapshot() {
        return List.copyOf(entries);
    }

    void clear() {
        entries.clear();
    }
}
~~~

**Kiểm tra code:** các ~~~LegacyService~~~ và ~~~FinishService~~~ cùng trả về `done:<id>`; khác biệt có chủ đích là lời gọi advice. `@Before` ghi event trước khi target `finish` ghi event; `status` hoàn toàn nằm ngoài pointcut. Không có vòng constructor dependency: aspect chỉ inject `Events`, CheckoutService chỉ inject FinishPort + Events.

### 4.3. Chạy và đọc kết quả

Từ thư mục project trên Java 21 và Gradle 8.x tương thích:

~~~bash
gradle test --tests com.example.selfcall.SelfInvocationRefactorTest
~~~

Nếu đã có Gradle wrapper, thay bằng `./gradlew test --tests com.example.selfcall.SelfInvocationRefactorTest` (PowerShell: `.\gradlew.bat test --tests com.example.selfcall.SelfInvocationRefactorTest`).

Kỳ vọng **2 JUnit tests pass**. Đây là kỳ vọng theo assertions của ví dụ; không khẳng định tests đã chạy trong bài này. Gradle output có thể khác nhau theo terminal/cache; `Events` chỉ ở trong test và không in ra console.

| Thử nghiệm | `Events.snapshot()` mong đợi | Ý nghĩa |
| --- | --- | --- |
| Trước: `legacy.place("A")` | `[place:A, finish:A]` | Lời gọi `this.finish` chạy target nhưng không chạy advice |
| Trước: `legacy.finish("B")` | `[advice:legacy:B, finish:B]` | Pointcut thực sự hợp lệ khi gọi qua proxy |
| Sau: `checkout.place("A")` | `[place:A, advice:finish:A, finish:A]` | Bean cộng tác nhận advice đúng một lần |
| Sau: `finish.status()` | Không thêm event | Không mở rộng advice sang method khác |
| Sau: `checkout.place("B")` sau `events.clear()` | `[place:B, advice:finish:B, finish:B]` | Behavior ổn định giữa hai lượt gọi |

Nếu test đầu tiên không thấy `advice:legacy:B`, đừng kết luận self-invocation là nguyên nhân: hạ tầng `@EnableAspectJAutoProxy`, đăng ký aspect bean hoặc pointcut còn có vấn đề. Positive control giúp phân biệt **advice chưa bao giờ chạy** với **advice bị bypass ở lời gọi nội bộ**.

Nếu test sau thấy `advice:finish:A` hai lần, hãy kiểm tra có đăng ký trùng aspect, nhiều advisor cùng ghi event, hoặc đường gọi khác bị lặp. Trong ví dụ mỗi config có đúng một aspect tương ứng và `Events` độc lập.

## 5. Khi nào refactor thực sự sửa được vấn đề?

**Điều kiện bắt buộc của cách sửa:**

- `FinishService` được Spring tạo hoặc đăng ký làm bean.
- Caller dùng **Spring bean `FinishPort` đã được inject**, không dùng `new FinishService(...)`.
- Pointcut thực sự match `FinishPort.finish(..)`.
- Aspect được đăng ký trong cùng context/hạ tầng AOP có thể proxy bean đích.
- Lời gọi `finishPort.finish(...)` đi qua proxy, không phải `this.finish(...)` ở cùng target.
- Method target có thể được intercept bởi proxy strategy đang dùng: ví dụ public interface method với JDK dynamic proxy.

Ở một refactor trong dự án thật, giữ cổng method nhỏ và đặt ở một package phù hợp, ví dụ:

~~~text
com.example.checkout
├── CheckoutFacade         (gọi PaymentCommand)
├── CheckoutService        (điều phối)
├── PaymentCommand         (interface cần AOP)
├── PaymentCommandService  (thực thi operation)
└── aspect
    └── AuditAspect
~~~

Không cần tách mỗi method thành một bean. Tách một **boundary có lý do nghiệp vụ**, như ghi nhận thanh toán, thực thi transaction riêng hoặc truy cập tài nguyên bảo mật. Nếu operation vốn là chi tiết thuần Java không cần interceptor, có thể giữ private method trong cùng class.

### 5.1. Nếu dùng `@Transactional`, ý nghĩa còn lớn hơn log

Một ca rất phổ biến:

~~~java
@Service
class InvoiceService {
    public void issue() {
        saveRecord();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRecord() {
        // Ghi database.
    }
}
~~~

Đây là **mảnh minh họa**, cần import `Service`, `Transactional`, `Propagation`, transaction manager và database/repository để chạy. Trong chế độ transaction `PROXY` mặc định, `issue() -> this.saveRecord()` **không kích hoạt interceptor mới** cho `saveRecord()`. Vì vậy, đừng suy ra `REQUIRES_NEW` đã mở transaction riêng chỉ vì annotation có mặt.

Cách đặt boundary rõ ràng:

~~~java
@Service
class RecordWriter {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRecord() {
        // Repository save.
    }
}

@Service
class InvoiceService {
    private final RecordWriter writer;

    InvoiceService(RecordWriter writer) {
        this.writer = writer;
    }

    public void issue() {
        writer.saveRecord(); // Gọi public method trên bean cộng tác.
    }
}
~~~

Đây vẫn chỉ là **mảnh code minh họa**, cần repository, transaction manager và dependency phù hợp. Khi `RecordWriter` là Spring bean nhận transaction proxy, lời gọi từ `InvoiceService` đi qua proxy để transaction advice áp dụng. Test thực tế phải xác minh **propagation, rollback, commit và kết quả dữ liệu**, không chỉ assert rằng service được gọi.

**Cẩn thận với thời gian transaction:** Nếu `issue()` đang ở một transaction, chuyển `saveRecord()` sang `REQUIRES_NEW` có thể tạm ngưng transaction ngoài và bắt đầu transaction mới; đó là thay đổi hành vi nghiệp vụ cần được chủ động kiểm thử. Không nên chuyển annotations rồi tin rằng hai cấu trúc có semantics giống nhau nếu trước đây advice của method nội bộ chưa chạy.

### 5.2. Nếu dùng `@Async` hoặc caching

Cơ chế Spring `@Async` hoặc Spring cache annotations cũng thường dựa trên proxy, nên self-invocation có thể bỏ qua interceptor. Khi chuyển operation sang bean khác, bạn phải xem xét thêm:

- `@Async` chạy bằng executor, có thể đổi thread và ngữ cảnh truyền theo; return type, exception handling và thời điểm hoàn thành có thể thay đổi.
- Caching phụ thuộc cache key, cache manager và điều kiện intercept; lần gọi trực tiếp trong cùng target không có cache advice, nhưng qua collaborator proxy thì có thể được cache.
- Transaction, async và cache có thứ tự interceptor; test nên phản ánh đường gọi sản phẩm, không chỉ đếm `@Before`.

Những cơ chế này được đề cập để minh họa **hậu quả của boundary Spring proxy**, không thay thế phần cấu hình chuyên sâu của từng module.

## 6. Vì sao các cách “sửa nhanh” thường kém hơn?

| Cách tiếp cận | Có thể kích hoạt advice cho self-call? | Đánh đổi |
| --- | --- | --- |
| **Tách bean cộng tác** và inject interface của bean mới | Có, khi method đi qua proxy và pointcut khớp | Tăng số bean nhưng rõ dependency, dễ test |
| Self-injection (`@Lazy` self bean) | Có thể, nếu reference là proxy | Dễ làm phức tạp vòng đời/circular dependency và coupling |
| `AopContext.currentProxy()` | Có thể, khi `exposeProxy=true` và đang trong invocation có proxy | Trói code với Spring AOP, yêu cầu cast/kiểu proxy đúng |
| Đổi JDK proxy sang CGLIB | **Không** khắc phục self-invocation | Còn thêm ràng buộc `final`/`private` |
| Gắn `@Transactional`/`@Aspect` lên method lần nữa | **Không** | Annotation không làm `this.method()` đi qua proxy |
| Đổi method từ `private` thành `public` trong cùng class | **Không**, nếu vẫn gọi bằng `this` | Thay đổi API/encapsulation nhưng giữ nguyên đường gọi |
| AspectJ CTW/LTW | Có thể can thiệp self-invocation nhờ weaving | Phải quản lý build/agent/weaving; là cơ chế khác Spring AOP proxy |

**Self-injection:** một số dự án inject lại chính service qua interface hoặc dùng `ObjectProvider` để tra proxy khi cần. Cách này có thể hoạt động trong điều kiện cụ thể, nhưng làm code phụ thuộc mạnh hơn vào Spring container và có thể dính vòng reference hoặc eager initialization. Đặc biệt, không inject chính bean vào **aspect** để advice gọi lại chính operation: làm thế dễ gây **advice re-entry** vô hạn.

**`AopContext.currentProxy()`:** chỉ dùng khi không thể thay đổi thiết kế, và phải bật `exposeProxy`. Cả Spring documentation cũng khuyến khích tránh cách này nếu có thể refactor. Trong code nghiệp vụ, call graph có tham chiếu dependency rõ ràng dễ bảo trì hơn một cast ngầm tới current proxy.

**AspectJ weaving:** compile-time/load-time weaving có thể xử lý các join point bên trong class vì can thiệp bytecode; không phải chỉ thêm thư viện `aspectjweaver` là Spring proxy tự chuyển sang weaving. Với bài thực hành này, tách bean là cách sửa tự nhiên và đủ kiểm chứng.

## 7. Làm sao kiểm thử refactor không che giấu lỗi?

Một regression suite nên luôn có đủ các **đối chứng dương, âm và kiểm tra hành vi**.

| Rủi ro | Assertion cần thiết |
| --- | --- |
| Aspect chưa được đăng ký nhưng negative case vẫn pass | Ca gọi `finish` trực tiếp qua Spring proxy phải ghi đúng một advice event |
| Pointcut vô tình match `place` hoặc `status` | Không có advice event cho những method đó |
| Refactor vô tình gọi target trực tiếp bằng `new` | `AopUtils.isAopProxy(finish)` phải true; test phải lấy bean từ context |
| Advice chạy hai lần sau refactor | Event list chứa **chính xác một** `advice:finish:A` |
| Nội dung nghiệp vụ thay đổi | Cả trước và sau phải trả cùng `done:A` |
| Caller bỏ qua bước cần xử lý | Phải có cả `finish:A` trong event list sau khi gọi |
| Test phụ thuộc thứ tự hoặc state dùng chung | Mỗi test có context/probe mới hoặc reset đầy đủ; không assert counter tích lũy từ test khác |
| Proxy loại khác trên môi trường triển khai | Kiểm tra `isJdkDynamicProxy`/`isCglibProxy`, injection và method visibility trong test production-like |
| Advice khác như transaction/security bị lệch | Chạy integration test với dependency, transaction manager và cấu hình tương tự production |

### Kiểm tra object đi qua proxy bằng Spring API

Khi debug trong dự án thật:

~~~java
import org.springframework.aop.support.AopUtils;

Object finishBean = context.getBean("finishPort");
System.out.println("Spring proxy = " + AopUtils.isAopProxy(finishBean));
System.out.println("JDK proxy    = " + AopUtils.isJdkDynamicProxy(finishBean));
System.out.println("Target type  = " + AopUtils.getTargetClass(finishBean));
~~~

Đây là **đoạn debug chèn vào test có biến `context`**, không phải file Java độc lập. Tên bean `finishPort` đến từ method `@Bean` trong ví dụ. Nếu `Spring proxy = false` ở một project khác, hãy kiểm tra aspect registration, pointcut và bean ownership trước khi kết luận self-invocation.

### Một kiểm tra về phương thức được Spring nhìn thấy

Khi dùng interface-based proxy, service consumer nên inject interface có method cần advice. Nếu caller yêu cầu một implementation class nhưng Spring tạo JDK proxy, injection theo concrete type có thể thất bại. Nếu chuyển sang CGLIB, method `final`/`private` lại có giới hạn khác; đừng lấy proxy strategy làm giải pháp thay thế refactor.

Thực tế còn có trường hợp `@PostConstruct` hoặc constructor gọi method cần advice khi proxy chưa sẵn sàng; **tách bean** không có nghĩa có thể tùy ý gọi các Spring proxy khi lifecycle chưa hoàn tất. Thay các thao tác cần interceptor ở initialization bằng cơ chế sau khởi tạo phù hợp (event lifecycle, runner hoặc service orchestration), rồi test đường gọi đó.

## 8. Lỗi thường gặp khi làm bài thực hành

| Hiện tượng | Nguyên nhân có thể | Cách khoanh vùng |
| --- | --- | --- |
| Cả hai test không có advice | Chưa bật `@EnableAspectJAutoProxy`; `@Aspect` không là bean; pointcut sai | Kiểm tra `BeforeConfig/AfterConfig` và positive control qua proxy |
| Trước refactor advice cũng chạy cho `place("A")` | Có aspect khác intercept `place`, hoặc code đã gọi qua một bean/proxy khác | Kiểm tra điểm cắt và event key; đảm bảo fixture như đề bài |
| Sau refactor advice vẫn không chạy | `FinishService` tạo bằng `new` ở caller hoặc inject raw target; bean không nằm trong context đúng | Inspect `AopUtils.isAopProxy(finishPort)`, bean name và context |
| Sau refactor advice chạy đôi | Đăng ký aspect 2 lần hoặc aspect gọi lại proxy | Liệt kê bean/advisor và đếm event trên một invocation |
| Bean không tạo được do circular reference | Aspect inject target hoặc service tự inject chính mình | Vẽ constructor dependency graph, bỏ reference ngược |
| Proxy không inject được qua concrete class | JDK proxy chỉ implement interface | Dùng interface consumer hoặc cân nhắc proxy strategy đúng nhu cầu |
| `@Transactional` vẫn không có rollback | Transaction manager/config chưa bật hoặc kiểm thử chỉ đếm event | Test transaction/DB thật, exception propagation và commit/rollback |
| Test chạy riêng pass, chạy cả suite fail | Shared state không reset, context cache, dữ liệu test tích lũy | Dùng context riêng và assert events snapshot của từng test |
| Advice không chạy trong Spring Boot chạy thật | Package component scan, profile, auto-config AOP hoặc bean instance khác test | Chạy integration test trên production-like context/profile |

## 9. Bài tập mở rộng tự kiểm chứng

1. **Mở rộng pointcut:** thêm `@Before` cho một annotation `@Audited` trên `FinishService.finish`; kiểm tra `status` vẫn không có advice và annotation nằm ở implementation method phù hợp.
2. **Tác dụng phụ:** thay `Events` bằng một `AuditRecorder` độc lập không gọi ngược `CheckoutPort`. Kiểm tra không có vòng dependency và không phát sinh re-entry trong advice.
3. **Tình huống thất bại:** để `FinishService.finish` ném `IllegalArgumentException` với ID rỗng; kiểm thử exception được truyền nguyên qua proxy; với `@Around`, đảm bảo `proceed()` đúng một lần.
4. **Hai bean cùng interface:** thêm một `FinishPort` khác và hạn chế pointcut bằng `bean(finishPort)`; kiểm tra chỉ bean được chọn bị advice.
5. **Transaction:** nếu có database và Spring transaction infrastructure, đưa `@Transactional(propagation = REQUIRES_NEW)` lên method của bean cộng tác; dùng test có transaction manager thật để xác minh transaction boundary sau refactor.
6. **Profile và proxy:** chạy một integration test với `proxyTargetClass=true` trong Spring context **riêng**, kiểm chứng Spring AOP vẫn không advice được self-call của target cũ, trong khi lời gọi qua collaborator proxy vẫn hoạt động.

Đối với mỗi bài mở rộng, trước hết ghi rõ expected events/exception/state, rồi chạy test với context có hạ tầng AOP tương ứng. Không thay assertions chỉ để làm test xanh nếu behavior đã thay đổi ngoài kế hoạch.

## 10. Checklist nghiệm thu

- Tái hiện chính xác: `this.finish()` bỏ qua advice nhưng `proxy.finish()` kích hoạt advice.
- Xác định đúng **Spring-managed proxy**, không chỉ nhìn `@Aspect` và code method.
- Đã tách một bean cộng tác thực sự, giữ dependency hướng một chiều.
- Pointcut match bean mới; có positive control và negative control.
- Một business invocation vẫn trả đúng kết quả, method đích chạy một lần và advice chạy **chính xác một lần**.
- Không tạo vòng constructor dependency hoặc advice re-entry.
- Test dùng context và probe độc lập, không phụ thuộc thứ tự chạy.
- Nếu dùng transaction/async/cache/security, đã kiểm thử thêm contract cụ thể sau refactor.
- Không nhầm self-invocation của Spring proxy với lỗi AspectJ weaving hay lỗi do JDK/CGLIB choice.

**Ghi nhớ:** Với Spring AOP, **method call phải đi qua proxy thì advice mới có cơ hội chạy**. Chuyển thao tác cần intercept sang **Spring bean cộng tác** và gọi qua dependency được inject tạo ra một boundary rõ ràng, dễ bảo trì và dễ kiểm thử hơn các mẹo tự tham chiếu proxy.

## Tài liệu chính thức

- Spring Framework — [Proxying Mechanisms và self-invocation](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- Spring Framework — [Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/enable.html)
- Spring Framework — [Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html)
- Spring Framework — [Using @Transactional (proxy mode)](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
- Spring Framework — [Spring TestContext Framework](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-practical-workshop-007">Tạo advisor và proxy bằng Spring AOP API</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo advisor và proxy bằng Spring AOP API

## 1. Mục tiêu bài thực hành

Trong Spring Framework, ngoài cách khai báo `@Aspect` và `@EnableAspectJAutoProxy`, bạn có thể tạo AOP proxy **trực tiếp bằng Spring AOP API**. Cách này hữu ích khi một thư viện, một cấu hình legacy hoặc một cơ chế hạ tầng cần chọn động **target**, **pointcut**, **advice**, **thứ tự advisor** và **kiểu proxy** mà không muốn dựa hoàn toàn vào cơ chế tự phát hiện `@Aspect`.

Bài này tập trung vào **Spring AOP**: tự tạo `MethodInterceptor`, bọc nó thành `DefaultPointcutAdvisor`, cấu hình `ProxyFactory`, tích hợp với `ProxyFactoryBean` trong Spring IoC và dùng `Advised` để kiểm tra proxy sau khi tạo. Bạn sẽ thực hành bằng Java 17+ và Maven với cùng một service, cùng quy tắc chọn method, cùng một interceptor.

Sau bài học, bạn có thể:

1. Giải thích rõ các lớp Spring API: `Advice`, `Pointcut`, `Advisor`, `ProxyFactory`, `ProxyFactoryBean`, `Advised` và `AopUtils`.
2. Tạo proxy từ target với `ProxyFactory` mà **không cần `ApplicationContext`**; chọn JDK interface proxy hoặc CGLIB class proxy một cách có chủ đích.
3. Đưa target, advisor và `ProxyFactoryBean` vào Spring container để caller nhận **proxy** qua `getBean("storeService")`.
4. Kiểm chứng một advisor chỉ áp dụng cho `save(...)`, không áp dụng cho `find(...)`.
5. Viết test kiểm tra proxy thật, danh sách advisor, hiệu ứng advice và những sai lầm như gọi thẳng target hoặc tự gọi nội bộ qua `this`.

**Phạm vi:** Đây là proxy-based Spring AOP, không dùng AspectJ load-time weaving, Java agent hay compile-time weaving. Chỉ nhắc tới cú pháp AspectJ expression như một lựa chọn khác để xây pointcut trong **Spring**.

## 2. Sơ đồ các thành phần trong Spring AOP API

```text
                        Application code
                                |
                        StoreService proxy
                                |
            Spring MethodInterceptor chain
                                |
      +-------------------------+-------------------------+
      |                                                   |
  Advisor A                                          Advisor B
  = Pointcut + Advice                               = Pointcut + Advice
      |                                                   |
  match save(...)                                 match operation khác
      |                                                   |
  interceptor.invoke(MethodInvocation) --> proceed()
                                |
                           Target bean
                       DefaultStoreService
```

| Khái niệm | API thường dùng | Trách nhiệm |
| --- | --- | --- |
| **Target** | Java object hoặc `TargetSource` | Thực thi nghiệp vụ khi invocation đi đến cuối chain |
| **Advice** | `org.aopalliance.intercept.MethodInterceptor`, `MethodBeforeAdvice`, ... | Định nghĩa hành vi trước/quanh/sau invocation |
| **Pointcut** | `org.springframework.aop.Pointcut`; `NameMatchMethodPointcut` | Chỉ ra lớp/method nào được chọn |
| **Advisor** | `org.springframework.aop.Advisor`; `DefaultPointcutAdvisor` | Gắn advice với pointcut hoặc chính sách áp dụng |
| **Proxy factory** | `org.springframework.aop.framework.ProxyFactory` | Tạo proxy bằng Java, kể cả ngoài Spring IoC |
| **Spring `FactoryBean`** | `org.springframework.aop.framework.ProxyFactoryBean` | Định nghĩa proxy là một Spring bean, tham chiếu advisor/target qua tên bean |
| **Introspection** | `org.springframework.aop.framework.Advised`, `AopUtils` | Xem advisor gắn trên proxy và nhận dạng kiểu proxy |

Các thành phần có quan hệ:

```java
// Ý tưởng chính; đây chưa phải một chương trình độc lập.
MethodInterceptor advice = invocation -> invocation.proceed();
NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
pointcut.setMappedName("save");
Advisor advisor = new DefaultPointcutAdvisor(pointcut, advice);

ProxyFactory factory = new ProxyFactory(target);
factory.addAdvisor(advisor);
Object proxy = factory.getProxy();
```

`ProxyFactory` nhận **target**, danh sách **advisor/advice** và các lựa chọn proxy. Nếu dùng `factory.addAdvice(advice)` trực tiếp, Spring tạo một advisor thích hợp để áp dụng advice **không bị giới hạn bởi pointcut cụ thể** (tùy loại advice). Nếu chỉ muốn intercept `save`, dùng `addAdvisor(new DefaultPointcutAdvisor(pointcut, advice))`.

**Khác biệt quan trọng:** `MethodInterceptor` là API của AOP Alliance được Spring hỗ trợ. Nó không phải `org.aspectj.lang.ProceedingJoinPoint` của annotation-style `@Around`; trong interceptor Spring, dùng `MethodInvocation#proceed()` để đi tiếp trong chain.

## 3. `Pointcut`, `Advice` và `Advisor`: phân tách trách nhiệm

### 3.1. Pointcut chọn method

`NameMatchMethodPointcut` là cách đơn giản để lọc **theo tên method**:

```java
NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
pointcut.setMappedName("save");
```

Điều kiện trên chỉ xét tên `save`. Nếu target có nhiều overload cùng tên, chúng đều là ứng viên. `NameMatchMethodPointcut` **không phân biệt overload bằng tham số**; muốn chọn method theo signature hoặc annotation, bạn cần pointcut phù hợp hơn, chẳng hạn subclass của `StaticMethodMatcherPointcut` hoặc `AspectJExpressionPointcut` (yêu cầu thư viện AspectJ hỗ trợ parsing).

Có thể dùng `setMappedNames("save", "update*")` khi cần hợp một số tên. `*` ở đây là **simple name pattern** do Spring hỗ trợ, không phải biểu thức chính quy tùy ý.

Ngoài điều kiện method, Spring `Pointcut` gồm `ClassFilter` và `MethodMatcher`. Trong ví dụ, `NameMatchMethodPointcut` chỉ có điểm lọc tên method; vì vậy phải giữ phạm vi target/proxy rõ ràng.

### 3.2. Advice xử lý invocation

`MethodInterceptor#invoke(MethodInvocation)` có thể thực hiện logic trước/sau, đọc method name, gọi `proceed()`, trả kết quả hoặc để exception đi tiếp.

```java
MethodInterceptor interceptor = invocation -> {
    System.out.println("before " + invocation.getMethod().getName());
    try {
        return invocation.proceed();
    } finally {
        System.out.println("after " + invocation.getMethod().getName());
    }
};
```

Đây là một snippet khác dành cho mục đích minh họa. Trong chương trình hoàn chỉnh bên dưới, `CountingInterceptor` là class riêng để test được số lần advice thực sự chạy.

Giữ đúng semantics:

- Gọi `proceed()` **một lần** nếu mục tiêu là bọc một lời gọi nghiệp vụ bình thường.
- Trả nguyên kết quả `proceed()` hoặc ném tiếp `Throwable`; tránh vô tình nuốt exception.
- `finally` chạy cả khi target lỗi. Nếu chỉ muốn hành vi **sau khi trả thành công**, đặt logic vào nhánh sau `proceed()` thay vì `finally`.
- Interceptor có thể là singleton được nhiều thread dùng chung: tránh shared mutable state không thread-safe; dùng counter atomic khi cần.

### 3.3. Advisor ghép pointcut và advice

```java
DefaultPointcutAdvisor advisor =
        new DefaultPointcutAdvisor(pointcut, interceptor);
```

`DefaultPointcutAdvisor` thường dùng với `MethodInterceptor` hoặc các advice type Spring hỗ trợ. Một proxy có thể mang nhiều advisor; thứ tự `addAdvisor`/`interceptorNames` có ý nghĩa đối với đường đi của interceptor chain. Nếu advice là toàn cục, có thể chỉ dùng `addAdvice`, nhưng lúc đó phải biết nó sẽ ảnh hưởng những method nào.

## 4. Project Java 17+ chạy được với Maven

Ví dụ chạy **Spring Framework 6.2.14**, Java 17+ và Maven, sử dụng thuần Spring AOP API. Vì không dùng `@Aspect` hoặc `AspectJExpressionPointcut`, **không cần dependency `aspectjweaver`**. Spring AOP kéo các dependency hạ tầng cần thiết qua Maven.

### 4.1. Cấu trúc thư mục

```text
spring-aop-api-lab/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── StoreService.java
    │   ├── DefaultStoreService.java
    │   ├── CountingInterceptor.java
    │   ├── SpringProxyConfig.java
    │   └── Main.java
    └── test/java/demo/
        └── SpringAopApiTest.java
```

### 4.2. File `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-api-lab</artifactId>
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

### 4.3. Business contract: `src/main/java/demo/StoreService.java`

```java
package demo;

public interface StoreService {
    String save(String id);
    String find(String id);
}
```

`save` là method cần advice, `find` là method không được pointcut chọn. Cả hai nằm trong interface để có thể thử JDK proxy.

### 4.4. Target: `src/main/java/demo/DefaultStoreService.java`

```java
package demo;

public class DefaultStoreService implements StoreService {
    @Override
    public String save(String id) {
        System.out.println("TARGET save: " + id);
        return "saved:" + id;
    }

    @Override
    public String find(String id) {
        System.out.println("TARGET find: " + id);
        return "found:" + id;
    }
}
```

Đối tượng này là POJO bình thường, **không có `@Aspect`, `@Component` hay Spring annotation**. Đây là target mà chúng ta sẽ bọc bằng Spring AOP proxy.

### 4.5. Advice: `src/main/java/demo/CountingInterceptor.java`

```java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public class CountingInterceptor implements MethodInterceptor {
    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        calls.incrementAndGet();
        String method = invocation.getMethod().getName();
        System.out.println("ADVICE before: " + method);

        try {
            return invocation.proceed();
        } finally {
            System.out.println("ADVICE after: " + method);
        }
    }

    public int callCount() {
        return calls.get();
    }
}
```

Khi kết hợp với advisor chỉ chọn `save`, interceptor sẽ không chạy cho `find`, nên `callCount()` chỉ tăng khi method `save` được gọi **qua proxy**.

`AtomicInteger` tránh data race khi một interceptor instance được nhiều thread dùng chung. Tuy nhiên, việc đếm trong ví dụ không đồng nghĩa đã thiết kế hệ thống telemetry production; lời gọi `System.out.println` chỉ để dễ quan sát, không nên giữ logging đồng bộ này trên hot path của ứng dụng thật.

### 4.6. Cấu hình container: `src/main/java/demo/SpringProxyConfig.java`

Tạo `target`, `interceptor`, `advisor` và **proxy dưới dạng `FactoryBean`**:

```java
package demo;

import org.springframework.aop.Advisor;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SpringProxyConfig {
    @Bean("targetStore")
    public StoreService targetStore() {
        return new DefaultStoreService();
    }

    @Bean("countingInterceptor")
    public CountingInterceptor countingInterceptor() {
        return new CountingInterceptor();
    }

    @Bean("saveAdvisor")
    public Advisor saveAdvisor(CountingInterceptor countingInterceptor) {
        NameMatchMethodPointcut pointcut =
                new NameMatchMethodPointcut();
        pointcut.setMappedName("save");
        return new DefaultPointcutAdvisor(
                pointcut, countingInterceptor
        );
    }

    @Bean("storeService")
    public ProxyFactoryBean storeServiceFactory() {
        ProxyFactoryBean factory = new ProxyFactoryBean();
        factory.setTargetName("targetStore");
        factory.setInterfaces(StoreService.class);
        factory.setInterceptorNames("saveAdvisor");
        factory.setProxyTargetClass(false);
        return factory;
    }
}
```

Các chi tiết có ý nghĩa:

- `targetStore` là **target bean** chưa được bọc bởi cấu hình trong bài.
- `countingInterceptor` là một bean chứa advice instance.
- `saveAdvisor` là bean ghép `NameMatchMethodPointcut` với interceptor.
- `storeService` là **`ProxyFactoryBean`**: `ApplicationContext.getBean("storeService")` thông thường nhận **object proxy** do factory tạo, **không** nhận `ProxyFactoryBean`.
- `setTargetName("targetStore")` trỏ đến tên target bean trong BeanFactory.
- `setInterceptorNames("saveAdvisor")` trỏ đến tên advisor bean; không truyền chuỗi tên service target vào danh sách advice.
- `setInterfaces(StoreService.class)` và `setProxyTargetClass(false)` khiến ví dụ yêu cầu **JDK interface proxy**. Điều kiện tạo proxy phải được kiểm tra lại nếu cấu hình được tích hợp vào context phức tạp.

Vì đây là Spring AOP API được cấu hình **thủ công**, không cần `@EnableAspectJAutoProxy`; annotation đó bật cơ chế Spring tự tìm các `@Aspect` beans, không phải điều kiện bắt buộc để `ProxyFactoryBean` hoạt động.

### 4.7. Main: so sánh `ProxyFactory` và `ProxyFactoryBean`

`src/main/java/demo/Main.java`:

```java
package demo;

import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        CountingInterceptor directAdvice = new CountingInterceptor();

        // Cách 1: tạo proxy trực tiếp, không thông qua IoC.
        NameMatchMethodPointcut onlySave = new NameMatchMethodPointcut();
        onlySave.setMappedName("save");
        Advisor directAdvisor =
                new DefaultPointcutAdvisor(onlySave, directAdvice);

        ProxyFactory proxyFactory = new ProxyFactory(new DefaultStoreService());
        proxyFactory.setInterfaces(StoreService.class);
        proxyFactory.setProxyTargetClass(false);
        proxyFactory.addAdvisor(directAdvisor);

        StoreService directProxy = (StoreService) proxyFactory.getProxy();
        inspect("ProxyFactory", directProxy, directAdvice);

        // Cách 2: Spring container quản lý target/advisor/proxy.
        try (var context =
                new AnnotationConfigApplicationContext(SpringProxyConfig.class)) {
            StoreService springProxy =
                    context.getBean("storeService", StoreService.class);
            CountingInterceptor springAdvice =
                    context.getBean(CountingInterceptor.class);

            // Dấu & yêu cầu chính FactoryBean, không phải object mà nó tạo.
            Object factory = context.getBean("&storeService");
            System.out.println("IS_FACTORY_BEAN="
                    + (factory instanceof ProxyFactoryBean));

            inspect("ProxyFactoryBean", springProxy, springAdvice);
        }
    }

    private static void inspect(
            String label, StoreService service, CountingInterceptor advice) {
        System.out.println("=== " + label + " ===");
        System.out.println("AOP=" + AopUtils.isAopProxy(service));
        System.out.println("JDK=" + AopUtils.isJdkDynamicProxy(service));
        System.out.println("TARGET="
                + AopUtils.getTargetClass(service).getSimpleName());

        if (service instanceof Advised advised) {
            System.out.println("ADVISORS=" + advised.getAdvisors().length);
        }

        System.out.println("SAVE=" + service.save("A"));
        System.out.println("FIND=" + service.find("A"));
        System.out.println("ADVICE_CALLS=" + advice.callCount());
    }
}
```

Ở đây một target được tạo qua `new` rồi bọc bởi `ProxyFactory` (ngoài IoC), trong khi target thứ hai được Spring tạo và một `ProxyFactoryBean` được Spring quản lý. Dù đời sống bean khác nhau, cả hai đều dùng **cùng class** `DefaultStoreService`, **cùng class** `CountingInterceptor` và **cùng quy tắc** chỉ intercept `save`.

Chạy bằng:

```bash
mvn -q compile exec:java
```

**Stdout kỳ vọng**, bỏ qua logging phụ của Maven/Spring nếu có:

```text
=== ProxyFactory ===
AOP=true
JDK=true
TARGET=DefaultStoreService
ADVISORS=1
ADVICE before: save
TARGET save: A
ADVICE after: save
SAVE=saved:A
TARGET find: A
FIND=found:A
ADVICE_CALLS=1
IS_FACTORY_BEAN=true
=== ProxyFactoryBean ===
AOP=true
JDK=true
TARGET=DefaultStoreService
ADVISORS=1
ADVICE before: save
TARGET save: A
ADVICE after: save
SAVE=saved:A
TARGET find: A
FIND=found:A
ADVICE_CALLS=1
```

Điều cần chú ý:

1. `AOP=true` ở cả hai: đối tượng caller sử dụng đã được Spring tạo AOP proxy.
2. `JDK=true` do target triển khai `StoreService` và cấu hình yêu cầu interface proxy.
3. `ADVISORS=1` là số advisor được **chúng ta chủ động gắn** trong project tối giản. Trong một context khác có transaction, caching, security hoặc additional advisors, số này có thể khác.
4. Chỉ `SAVE` kích hoạt cặp log `ADVICE`, còn `FIND` chỉ in `TARGET find`.
5. `ADVICE_CALLS=1` thể hiện pointcut lọc đúng một invocation ở mỗi proxy.
6. `IS_FACTORY_BEAN=true` minh họa cách truy cập factory bằng tên bean có tiền tố `&`; caller nghiệp vụ không dùng tiền tố đó.

**Không dựa vào tên class sinh tự động** như `$Proxy17`: tên này thay đổi theo JDK và thứ tự proxy được tạo.

## 5. Phân tích cơ chế `ProxyFactory`

`ProxyFactory` là lựa chọn khi cần tạo proxy bằng Java với toàn quyền chủ động. Có thể xây từ object target:

```java
ProxyFactory factory = new ProxyFactory(target);
```

hoặc chủ động cấu hình target/interfaces:

```java
// Snippet, chưa phải file chạy độc lập.
ProxyFactory factory = new ProxyFactory();
factory.setTarget(new DefaultStoreService());
factory.setInterfaces(StoreService.class);
factory.addAdvisor(advisor);
StoreService proxy = (StoreService) factory.getProxy();
```

Một số API thường dùng:

| API | Vai trò | Lưu ý |
| --- | --- | --- |
| `setTarget(Object)` | Gán target instance trực tiếp | Caller tự quyết định lifecycle của object nếu ngoài Spring container |
| `setTargetSource(TargetSource)` | Cho target thay đổi theo policy (khi thật sự cần) | Không tùy tiện thay target source trong nghiệp vụ |
| `setInterfaces(Class<?>...)` | Chọn interface mà proxy công bố | JDK proxy chỉ có method công bố qua interface |
| `setProxyTargetClass(true)` | Yêu cầu class-based proxy | Class `final` không thể được subclass |
| `addAdvice(Advice)` | Thêm advice không cần pointcut riêng | Có thể áp dụng rộng hơn mục tiêu |
| `addAdvisor(Advisor)` | Thêm advice gắn pointcut/lựa chọn | Phù hợp kiểm soát method/class |
| `getProxy()` | Sinh proxy instance | Nên giữ reference proxy thay vì quay lại dùng target raw |
| `setFrozen(true)` | Đóng băng cấu hình advisor của proxy | Hữu ích khi không cho phép thêm/bớt advisor sau khi publish |

**Một lỗi thiết kế:** tạo proxy thành công rồi vẫn gọi reference target gốc:

```java
DefaultStoreService target = new DefaultStoreService();
ProxyFactory factory = new ProxyFactory(target);
factory.addAdvisor(advisor);

StoreService proxy = (StoreService) factory.getProxy();
proxy.save("A");   // Đi qua Spring AOP proxy.
target.save("B");  // Bỏ qua advice vì gọi thẳng target.
```

Snippet này mô tả hành vi, không nằm trong `Main.java` ở ví dụ chạy. Dù target là cùng object đằng sau proxy, lời gọi trực tiếp **không** đi qua interceptor chain của Spring AOP.

Một giới hạn khác: trong method target, lời gọi `this.otherMethod()` thường bỏ qua proxy. Dùng `ProxyFactory` thay cho annotation-style `@Aspect` **không** khắc phục self-invocation.

## 6. `ProxyFactoryBean` tích hợp với Spring IoC

`ProxyFactoryBean` là một implementation của Spring `FactoryBean<Object>`. Nó tạo **một proxy được công bố như Spring bean** và cho phép cấu hình bằng tên advisor/interceptor bean.

Phân biệt:

```text
Bean definitions trong ApplicationContext
    |
    +-- targetStore          -> DefaultStoreService
    +-- countingInterceptor  -> CountingInterceptor
    +-- saveAdvisor          -> DefaultPointcutAdvisor
    +-- storeService         -> ProxyFactoryBean
                                 |
                                 +-- getObject() -> Spring AOP proxy
```

Khi dùng:

```java
StoreService service =
        context.getBean("storeService", StoreService.class);
```

Spring trả về proxy mà `ProxyFactoryBean` tạo ra. Khi dùng:

```java
ProxyFactoryBean factory =
        (ProxyFactoryBean) context.getBean("&storeService");
```

Spring trả về chính factory (dấu `&` là Spring `FactoryBean` dereference prefix). Đây là API hữu ích khi debug proxy configuration, không phải cách caller nghiệp vụ nên dùng.

### 6.1. `interceptorNames` không phải object references

Trong `ProxyFactoryBean`:

```java
factory.setInterceptorNames("saveAdvisor");
```

`"saveAdvisor"` là **tên bean** của advisor đã được đăng ký trong BeanFactory. Điều này khác với:

```java
// Dùng với ProxyFactory, khi đã có object Advisor trực tiếp:
proxyFactory.addAdvisor(saveAdvisor);
```

Không truyền text Java class name hoặc dùng `"saveAdvisor"` làm tên pointcut. Spring sẽ lookup các bean theo tên khi tạo proxy. Nếu bean advisor chưa tồn tại hoặc không đúng type, lỗi thường xuất hiện trong quá trình tạo bean/proxy.

Có thể khai báo nhiều tên:

```java
factory.setInterceptorNames("securityAdvisor", "auditAdvisor");
```

Đây là **snippet** chỉ minh họa ordering, không có hai bean đó trong project mẫu. Thứ tự các advisor được khai báo là thứ tự cấu hình quan trọng: advisor đứng ngoài có thể chạy logic “before” trước, nhưng thứ tự “after” của around interceptors có thể ngược lại do stack call/unwind. Nếu kết hợp transaction, security hoặc caching, cần xem interceptor chain thật thay vì đoán chỉ từ array.

### 6.2. FactoryBean singleton và lifecycle

`ProxyFactoryBean` mặc định có thể trả một proxy singleton. Điều này **không đồng nghĩa** mọi target được proxy đều độc lập giữa request/thread. Khi có state mutable trong interceptor hoặc target, cần xử lý thread safety theo scope thực tế.

Nếu muốn tạo proxy mới theo từng request/prototype, cần thiết kế target scope, `FactoryBean#isSingleton` và advisor lifecycle một cách phù hợp, không chỉ chỉnh một cờ rồi mặc định mọi object state được cách ly.

### 6.3. Đừng nhầm `ProxyFactoryBean` với `BeanNameAutoProxyCreator`

Cả hai đều nằm trong hệ sinh thái Spring AOP nhưng đảm nhiệm vai trò khác nhau:

| API | Điểm bắt đầu | Cách gắn proxy |
| --- | --- | --- |
| `ProxyFactory` | Object target trong code | Caller trực tiếp gọi `getProxy()` |
| `ProxyFactoryBean` | Một bean definition/factory cụ thể | Spring tạo proxy cho bean được định nghĩa thông qua factory |
| `BeanNameAutoProxyCreator` | Tên các beans trong container | Spring bean post-processor tự bọc những bean khớp tên |
| `@EnableAspectJAutoProxy` | Spring `@Aspect` beans | Spring tìm advisors từ aspect annotation và auto-proxy beans đủ điều kiện |

Không nên vô tình cấu hình **cả `ProxyFactoryBean` và auto-proxy creator** cho cùng object nếu chưa hiểu thứ tự, vì có thể sinh nhiều lớp proxy hoặc advice chạy trùng. Trong project tối giản, chỉ có `ProxyFactoryBean` trong context thứ hai.

## 7. Khi nào dùng JDK dynamic proxy hay CGLIB?

Trong thí nghiệm chính, ta gọi:

```java
factory.setInterfaces(StoreService.class);
factory.setProxyTargetClass(false);
```

Do `DefaultStoreService` triển khai `StoreService`, Spring có thể tạo JDK dynamic proxy. Caller nên khai báo dependency type là **interface** `StoreService`.

Nếu muốn thử class-based proxy ở một **biến thể riêng**:

```java
// Snippet sửa trên ProxyFactory trước khi gọi getProxy():
factory.setProxyTargetClass(true);
```

Để CGLIB subclass hoạt động, target class không được `final` và các method cần advice không được `final`/`private` nếu phải override. Khi đó:

```java
Object proxy = factory.getProxy();
boolean classProxy = AopUtils.isCglibProxy(proxy);
boolean castable = proxy instanceof DefaultStoreService;
```

Với `DefaultStoreService` của ví dụ, class-based proxy được dự kiến là `isCglibProxy=true` và `castable=true`. Với JDK proxy, ép object sang `DefaultStoreService` có thể ném `ClassCastException`, dù `AopUtils.getTargetClass` vẫn trả lớp đích.

**Không cần `@EnableAspectJAutoProxy(proxyTargetClass=true)`** chỉ để ép `ProxyFactory` đã được tạo trực tiếp dùng class proxy: cấu hình `ProxyFactory` của chính proxy đó là nơi quyết định. Trong Spring Boot, các property AOP auto-configuration điều khiển auto-proxy của Boot; chúng không nên được coi là cờ thay thế cho lời gọi trực tiếp `ProxyFactory.setProxyTargetClass`.

## 8. Kiểm tra advisor sau khi tạo proxy

Proxy Spring AOP thường công bố interface `Advised` cho phép đọc danh sách advisors:

```java
import org.springframework.aop.Advisor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

// Snippet ở nơi đã có biến proxy.
System.out.println("AOP=" + AopUtils.isAopProxy(proxy));
System.out.println("JDK=" + AopUtils.isJdkDynamicProxy(proxy));
System.out.println("CGLIB=" + AopUtils.isCglibProxy(proxy));

if (proxy instanceof Advised advised) {
    for (Advisor candidate : advised.getAdvisors()) {
        System.out.println("advisor=" + candidate.getClass().getName());
        System.out.println("advice=" + candidate.getAdvice().getClass().getName());
        if (candidate instanceof PointcutAdvisor pa) {
            System.out.println("pointcut=" + pa.getPointcut());
        }
    }
}
```

**Chú ý:** `Advised.getAdvisors()` cho biết advisor được **gắn** vào proxy. Nó không chứng minh advisor nào đã chạy với một method cụ thể, vì `MethodMatcher` còn kiểm tra method/class và trong một số trường hợp arguments. Ở ví dụ của bài, xác minh thực thi bằng việc interceptor in log và counter tăng sau `save` nhưng không tăng sau `find`.

Không sửa danh sách advisor trên một proxy production đang chạy chỉ để debug. Các API như `addAdvisor`/`removeAdvisor`/`replaceAdvisor` thay đổi đường thực thi runtime và có thể xung đột với các yêu cầu consistency; với bài thực hành, tạo proxy mới trong test nếu muốn thay cấu hình.

## 9. Integration test bằng JUnit 5

Tạo `src/test/java/demo/SpringAopApiTest.java`:

```java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class SpringAopApiTest {

    @Test
    void directProxyInterceptsOnlySave() {
        CountingInterceptor advice = new CountingInterceptor();
        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedName("save");
        Advisor advisor = new DefaultPointcutAdvisor(pointcut, advice);

        ProxyFactory factory = new ProxyFactory(new DefaultStoreService());
        factory.setInterfaces(StoreService.class);
        factory.setProxyTargetClass(false);
        factory.addAdvisor(advisor);

        StoreService proxy = (StoreService) factory.getProxy();
        assertTrue(AopUtils.isAopProxy(proxy));
        assertTrue(AopUtils.isJdkDynamicProxy(proxy));
        assertInstanceOf(Advised.class, proxy);
        assertEquals(1, ((Advised) proxy).getAdvisors().length);

        assertEquals("saved:A", proxy.save("A"));
        assertEquals(1, advice.callCount());
        assertEquals("found:A", proxy.find("A"));
        assertEquals(1, advice.callCount());
    }

    @Test
    void springFactoryBeanReturnsAnAopProxy() {
        try (var context =
                new AnnotationConfigApplicationContext(SpringProxyConfig.class)) {
            Object factory = context.getBean("&storeService");
            StoreService service =
                    context.getBean("storeService", StoreService.class);
            CountingInterceptor advice =
                    context.getBean(CountingInterceptor.class);

            assertInstanceOf(ProxyFactoryBean.class, factory);
            assertTrue(AopUtils.isAopProxy(service));
            assertTrue(AopUtils.isJdkDynamicProxy(service));
            assertInstanceOf(Advised.class, service);
            assertEquals(1, ((Advised) service).getAdvisors().length);

            assertEquals("saved:B", service.save("B"));
            assertEquals("found:B", service.find("B"));
            assertEquals(1, advice.callCount());
        }
    }

    @Test
    void forcedClassProxyCanBeCastToTargetClass() {
        CountingInterceptor advice = new CountingInterceptor();
        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedName("save");

        ProxyFactory factory = new ProxyFactory(new DefaultStoreService());
        factory.setProxyTargetClass(true);
        factory.addAdvisor(new DefaultPointcutAdvisor(pointcut, advice));

        Object proxy = factory.getProxy();
        assertTrue(AopUtils.isCglibProxy(proxy));
        assertInstanceOf(DefaultStoreService.class, proxy);
        assertEquals("saved:C", ((DefaultStoreService) proxy).save("C"));
        assertEquals(1, advice.callCount());
    }
}
```

Chạy:

```bash
mvn test
```

Kết quả dự kiến với cấu hình mẫu: **3 tests passed, 0 failures, 0 errors**. Nếu dùng lệnh Maven thông thường, xem báo cáo Surefire trong `target/surefire-reports` để đối chiếu; đoạn này mô tả **kỳ vọng từ thiết kế**, không thay cho kết quả thực thi tại môi trường của bạn.

Giá trị của ba test:

- **Programmatic:** cho thấy không cần `ApplicationContext` để `ProxyFactory` tạo Spring AOP proxy; `save` được intercept còn `find` không.
- **Container integration:** khẳng định Spring phân biệt `&storeService` (factory) và `storeService` (proxy), đồng thời advisor được Spring resolve đúng theo bean name.
- **Class-based proxy:** xác minh có thể chủ động chọn CGLIB qua `setProxyTargetClass(true)` với target class không final.

Không dùng unit test gọi trực tiếp `CountingInterceptor.invoke(...)` rồi suy luận đã kiểm thử Spring proxy. Các test trên gọi **object proxy thật** và đọc `Advised`.

## 10. Cấu hình pointcut nâng cao cho Spring API

### 10.1. Pointcut theo tên dễ rộng hơn dự kiến

`setMappedName("save")` khớp mọi overload của tên `save` trên target. Nếu một class có `save(String)`, `save(Order)` và `save(Object)`, tất cả có thể được intercept. Để giới hạn chính xác tham số, tạo `StaticMethodMatcherPointcut`:

```java
import java.lang.reflect.Method;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

// Snippet điểm cắt riêng, không cần ghép vào project mẫu.
StaticMethodMatcherPointcut pointcut = new StaticMethodMatcherPointcut() {
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.getName().equals("save")
            && method.getParameterCount() == 1
            && method.getParameterTypes()[0] == String.class;
    }
};
```

Đây là **static method matcher**: không kiểm tra **giá trị** argument runtime. Muốn lọc theo giá trị thực tế, cần `MethodMatcher` runtime thích hợp; nếu không, đưa điều kiện nghiệp vụ vào service rõ ràng thay vì cố mở rộng pointcut.

### 10.2. Pointcut theo biểu thức AspectJ nhưng vẫn là Spring AOP

Spring có `org.springframework.aop.aspectj.AspectJExpressionPointcut`. Có thể dùng expression để xác định method, sau đó ghép thành `DefaultPointcutAdvisor` như thông thường:

```java
// Snippet bổ sung: cần aspectjweaver trên classpath để parse expression.
import org.springframework.aop.aspectj.AspectJExpressionPointcut;

AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
pointcut.setExpression("execution(* demo.StoreService.save(..))");

Advisor advisor = new DefaultPointcutAdvisor(pointcut, advice);
```

Ở đây `advice` là một `MethodInterceptor` đã có trong code. **Đây vẫn là Spring proxy AOP**, không tự bật AspectJ LTW. Nếu chọn snippet này, bổ sung dependency `org.aspectj:aspectjweaver` có version tương thích với Spring/JDK. Bài mẫu chính cố ý không thêm vì `NameMatchMethodPointcut` không cần nó.

### 10.3. Pointcut `ClassFilter` và class proxy

Khi thư viện bao gồm nhiều target classes, có thể kết hợp `ClassFilter` với `MethodMatcher` để tránh bọc ngoài ý muốn. Tuy nhiên, dù pointcut chính xác, lời gọi **không qua proxy** vẫn không kích hoạt advice. Điều này đặc biệt quan trọng nếu target có self-invocation hoặc người gọi giữ reference target raw.

## 11. Thứ tự advisors và semantics của chain

Giả sử muốn hai `MethodInterceptor`:

- `outer` theo dõi transaction/trace,
- `inner` đếm số lần gọi.

Nếu cùng pointcut và được thêm theo thứ tự `outer` rồi `inner`:

```text
caller
  -> outer BEFORE
      -> inner BEFORE
          -> target
      <- inner AFTER
  <- outer AFTER
```

Đây là **mô tả luồng call stack**, không phải đảm bảo về thứ tự của các advisor khác do Spring transaction/security/caching thêm. Với `ProxyFactory`, thứ tự `addAdvisor` quan trọng. Với `ProxyFactoryBean`, thứ tự tên trong `setInterceptorNames(...)` quan trọng.

Trong thực tế:

- Advisor có pointcut không khớp method sẽ không xuất hiện trong interceptor chain của method đó.
- Những advice dạng before/after có semantics khác around interceptor.
- `@Order`/`Ordered` có thể tác động thứ tự trong các cơ chế Spring tương ứng, nhưng đừng cho rằng mọi danh sách tự động được sắp giống cấu hình bằng tay.
- Dùng `Advised#getAdvisors()` để xem cấu hình proxy; dùng breakpoint tại interceptor chain, `MethodInvocation#proceed()` và log/test để xác định thứ tự thực thi.

Nếu thêm transaction/security/cache advisors, việc thay order có thể ảnh hưởng thời điểm exception hoặc transaction boundary, không chỉ thứ tự dòng log.

## 12. Các lỗi thường gặp khi tự tạo proxy

| Triệu chứng | Nguyên nhân có thể | Hướng xử lý |
| --- | --- | --- |
| Target chạy nhưng advice không chạy | Caller đang gọi target raw thay vì proxy | Giữ reference từ `getProxy()`/`getBean("storeService")` |
| `find` cũng được intercept | Dùng `addAdvice` không có pointcut, hoặc pointcut quá rộng | Tạo `DefaultPointcutAdvisor` với `NameMatchMethodPointcut` chỉ chọn `save` |
| `save` overload không mong muốn cũng bị intercept | `NameMatchMethodPointcut` chỉ kiểm tra tên | Dùng matcher theo signature cụ thể |
| `ClassCastException` khi cast sang implementation | JDK proxy triển khai interface, không kế thừa target | Gọi qua business interface, hoặc chọn class proxy có chủ đích |
| Lỗi subclass/final class khi ép CGLIB | Target class `final` hoặc không đủ visibility | Dùng interface proxy hoặc thay thiết kế class nếu hợp lý |
| `getBean("storeService")` trả proxy nhưng muốn debug factory | Quên Spring `FactoryBean` dereference prefix | Gọi `getBean("&storeService")` chỉ để kiểm tra factory |
| Bean `storeService` không khởi tạo | `setTargetName`/`setInterceptorNames` sai tên | Kiểm tra bean definitions và nested `Caused by` |
| Advice chạy hai lần | Kết hợp nhiều lớp AOP proxy hoặc đăng ký advice trùng | Rà soát `ProxyFactoryBean`, auto-proxy creator, advisors |
| Self-invocation không kích hoạt advice | `this.method()` không đi qua proxy | Tách method sang bean khác và gọi qua proxy |
| Counter lỗi khi chạy nhiều thread | Interceptor dùng shared mutable field không atomic | Dùng `AtomicInteger`/metric thread-safe, không giữ request state trong field |
| Test báo có advisor nhưng hành vi sai | `Advised#getAdvisors()` là cấu hình, không phải bằng chứng runtime | Assert side effect của advice cho đúng invocation |
| Dùng `@EnableAspectJAutoProxy` rồi tưởng tự cấu hình advisor thủ công | Annotation chỉ bật cơ chế auto-proxy `@Aspect` | Tạo `ProxyFactory`/`ProxyFactoryBean` đúng và giữ trách nhiệm rõ ràng |

## 13. Khi nào nên dùng AOP API thay vì annotation?

`@Aspect` và `@EnableAspectJAutoProxy` thường **dễ đọc, dễ bảo trì hơn** cho logging, metrics hoặc auditing thông thường trong Spring app. AOP API có giá trị khi:

1. **Thư viện/framework integration** phải lắp interceptor tự động theo metadata hoặc runtime configuration.
2. **Legacy Spring XML/FactoryBean** đã có quy tắc tạo advisor, nhiều bean target khác nhau và cần duy trì compatibility.
3. **Khung transaction/security/custom interceptor** muốn tái sử dụng lớp advisor/pointcut của Spring mà không khai báo từng `@Aspect` method.
4. **Test hoặc tool phát hiện lỗi** cần tạo một proxy rất nhỏ, tách khỏi application context thật.
5. **Cơ chế target source đặc biệt** cần điều khiển lifecycle/lookup target; đây là tình huống nâng cao, cần xem kỹ semantics trước khi dùng.

Không nên chọn API thủ công chỉ để tiết kiệm vài annotation nếu logic đơn giản. Nó tăng trách nhiệm của người viết đối với scope, tên bean, advice ordering, proxy interface, transaction tương thích và test tích hợp.

## 14. Bài tập mở rộng

### Bài 1: Đổi `save` thành `save*`

Trong `NameMatchMethodPointcut`, đổi `setMappedName("save")` thành `setMappedName("save*")`. Thêm `saveBatch` vào interface/implementation rồi dự đoán số lần interceptor chạy. Đây là simple method-name pattern, không phải regular expression.

### Bài 2: Hai advisors trên cùng proxy

Viết interceptor `AuditInterceptor` khác và thêm sau `CountingInterceptor` bằng `addAdvisor`. Quan sát trước/sau theo thứ tự call stack. Thử đổi thứ tự gắn advisor và so kết quả.

### Bài 3: Kiểm tra self-invocation

Thêm method `runBatch()` trong target gọi `this.save("B")`, tạo lời gọi tới `runBatch` qua proxy. Quan sát advice trên `save` có chạy không. Đề xuất thiết kế tách `save` sang Spring bean được inject qua proxy để xử lý thay vì phụ thuộc `this`.

### Bài 4: Kiểm tra `ProxyFactoryBean` dereference

Tạo test xác nhận `context.getBean("storeService")` và `context.getBean("&storeService")` **không phải cùng loại object**. Giải thích vì sao Spring FactoryBean khác factory method `@Bean` trả trực tiếp một `StoreService`.

### Bài 5: Thay JDK bằng CGLIB

Trong `ProxyFactory`, thử `setProxyTargetClass(true)` rồi kiểm tra `AopUtils.isCglibProxy(proxy)` và `proxy instanceof DefaultStoreService`. Sau đó thử một lớp `final` và tìm lý do không thể dùng class-based proxy.

### Bài 6: Hạn chế pointcut đúng overload

Tạo `StaticMethodMatcherPointcut` kiểm tra `save(String)` nhưng không chọn `save(Object)`. Viết test assert `advice.callCount()` sau khi gọi từng overload qua proxy.

### Bài 7: Tích hợp Spring Boot có chủ đích

Đưa `ProxyFactoryBean` vào một Boot application, kiểm tra auto-proxy configuration hiện hữu có tạo proxy lồng nhau hoặc advisor trùng không. Đừng mặc định property `spring.aop.proxy-target-class` tự thay thế cấu hình `ProxyFactory` đã khai báo trực tiếp.

## 15. Checklist trước khi hoàn thành

- [ ] Xác định target và interface chính xác; không gọi target raw khi kỳ vọng Spring AOP advice.
- [ ] `Pointcut` chọn đúng class/method, không quét quá rộng hoặc làm sai overload.
- [ ] `MethodInterceptor` dùng `proceed()` theo semantics mong muốn; exception được xử lý rõ.
- [ ] `Advisor` ghép đúng advice và pointcut; thứ tự có ý nghĩa khi nhiều advisors.
- [ ] `ProxyFactory` được cấu hình trước `getProxy()` và caller thực sự dùng object proxy.
- [ ] `ProxyFactoryBean` nhận tên target/advisor bean chính xác và dùng `&` chỉ để lấy factory.
- [ ] Kiểm tra JDK/CGLIB strategy và các giới hạn `final`, `private`, self-invocation.
- [ ] Có integration test qua proxy thật, không chỉ xem `getAdvisors().length`.
- [ ] Không duplicate auto-proxy giữa cấu hình manual và annotation khi chưa chủ đích.
- [ ] Xem xét thread safety, logging/metrics overhead và lifecycle của advice singleton.

**Kết luận:** với Spring AOP API, đường đi cốt lõi là **chọn target → viết advice → xác định pointcut → ghép advisor → tạo proxy → gọi qua proxy → kiểm tra advisor và hành vi**. `ProxyFactory` phù hợp tạo trực tiếp bằng Java, còn `ProxyFactoryBean` tích hợp quá trình đó vào Spring IoC. Cả hai đều dùng Spring proxy-based AOP, nên chỉ có hiệu lực khi invocation thật sự đi qua proxy.

## 16. Tài liệu chính thức

- [Spring Framework 6.2 — Spring AOP APIs](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api.html): tổng quan các API AOP cấp thấp của Spring.
- [Spring Framework — Creating AOP Proxies Programmatically with ProxyFactory](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html): `ProxyFactory`, `addAdvice`, `addAdvisor`, `getProxy`.
- [Spring Framework — The Advisor API](https://docs.spring.io/spring-framework/reference/core/aop-api/advisor.html): `DefaultPointcutAdvisor` và kết hợp pointcut/advice.
- [Spring Framework — Using ProxyFactoryBean](https://docs.spring.io/spring-framework/reference/core/aop-api/pfb.html): bean names, `interceptorNames`, `FactoryBean`, singleton.
- [Spring Framework — The Pointcut API](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html): `Pointcut`, `ClassFilter`, `MethodMatcher` và pointcut implementations.
- [Spring Framework — The Advice API](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html): `MethodInterceptor` và `MethodInvocation`.
- [Spring Framework 6.2.14 Javadoc — NameMatchMethodPointcut](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/NameMatchMethodPointcut.html): `setMappedName`, `setMappedNames`, lưu ý overload.
- [Spring Framework — Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html): đọc danh sách advisors từ proxy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-practical-workshop-008">Kiểm chứng đường thành công, ngoại lệ và nhiều luồng đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng đường thành công, ngoại lệ và nhiều luồng đồng thời trong Spring AOP

## 1. Mục tiêu của bài thực hành

Khi custom `@Aspect` được Spring áp dụng qua proxy, một unit test chỉ gọi thẳng advice hoặc chỉ kiểm tra `proceed()` chưa chứng minh được toàn bộ hành vi thực tế. Một phép thử tích hợp cần gọi **bean do Spring quản lý**, ghi dấu vết quanh target và đối chiếu kết quả kỳ vọng trong những nhánh quan trọng:

1. **Thành công:** advice nào được gọi, thứ tự vào/ra, kết quả trả về có giữ nguyên không, target được chạy mấy lần.
2. **Ngoại lệ:** target ném gì, advice có ghi đúng `throw/finally` và **không nuốt/đổi exception ngoài ý muốn** không.
3. **Nhiều luồng đồng thời:** mỗi invocation có trace, context và kết quả độc lập; recorder có an toàn luồng không; một request có bị lẫn sự kiện của request khác không.
4. **Đối chứng pointcut:** một method không thuộc phạm vi advice không bị ghi sự kiện ngoài dự kiến.
5. **Đối chứng proxy:** cùng service phải được gọi qua Spring proxy; gọi `new` trực tiếp không đủ để chứng minh Spring AOP hoạt động.

Bài này chỉ tập trung vào **Spring Aspect / Spring AOP proxy**, đúng nghĩa dùng `@EnableAspectJAutoProxy`, `@Aspect`, `@Around`, `@Order`, JUnit và Spring context. Không dùng AspectJ load-time hoặc compile-time weaving.

### 1.1. Hợp đồng phải xác định trước test

Fixture có hai custom aspects:

- `OuterAspect @Order(10)`, bao bên ngoài, gọi `proceed()` một lần.
- `InnerAspect @Order(20)`, bao bên trong, gọi `proceed()` một lần.
- `ProbeService.run(id, fail)`, target ghi event rồi trả `"ok:" + id` hoặc ném `IllegalStateException("failed:" + id)`.
- `Trace`, collector thread-safe, lưu event **theo một `id` duy nhất của invocation**.

Với lời gọi thành công, expected trace là:

```text
outer.enter
inner.enter
target.enter
target.return
inner.return
inner.finally
outer.return
outer.finally
```

Với target ném exception:

```text
outer.enter
inner.enter
target.enter
target.throw
inner.throw
inner.finally
outer.throw
outer.finally
```

`@Order` có số nhỏ hơn thường mang precedence cao hơn giữa các Spring aspect. Với hai `@Around`, advice ngoài đi vào trước, đi ra sau. **Hai trace trên là hợp đồng cụ thể của fixture**, không nên áp dụng nguyên xi cho mọi tổ hợp `@Before`, `@After`, `@AfterThrowing` hoặc hạ tầng transaction/cache/async khác.

## 2. Thiết kế trace đúng cho bài toán nhiều luồng

Không ghi tất cả event của mọi request vào một `ArrayList` không đồng bộ, sau đó assert một danh sách thứ tự toàn cục. Với nhiều thread, thứ tự đan xen giữa các request không xác định.

Thay vào đó:

```text
Trace:
  request-0 -> [outer.enter, inner.enter, target.enter, ..., outer.finally]
  request-1 -> [outer.enter, inner.enter, target.enter, ..., outer.finally]
  request-2 -> [outer.enter, inner.enter, target.enter, ..., outer.finally]
```

Ta dùng:

```java
ConcurrentHashMap<String, ConcurrentLinkedQueue<Event>>
```

- `ConcurrentHashMap` quản lý bucket theo invocation ID và hỗ trợ nhiều thread.
- `ConcurrentLinkedQueue` lưu các event trong từng bucket theo thứ tự các lời `add` được thực hiện.
- Mỗi invocation fixture chạy đồng bộ trên một worker thread; event của **một ID** có thứ tự xác định dù các ID khác đan xen.
- Event ghi `Thread.currentThread().getName()` để khẳng định lời gọi trong bài test concurrent thực sự đi trên các thread của executor.

Trong hệ thống thật, invocation ID có thể lấy từ request/correlation ID hoặc object context rõ ràng. Nếu hai invocation có cùng business key nhưng không phải cùng lời gọi, **không dùng business key đó làm khóa duy nhất** mà phải tạo invocation ID riêng. Bài tập chủ động truyền ID trực tiếp qua tham số method, tránh phụ thuộc `ThreadLocal`/MDC chưa được đồng bộ với vòng đời request.

### 2.1. Điều được và không được chứng minh

Một queue thread-safe giúp recorder không bị hỏng dữ liệu; nó **không tự chứng minh** các advice nghiệp vụ đều thread-safe. Trong fixture, cả hai aspect chỉ có một tham chiếu tới `Trace`, không ghi state mutable riêng cho từng invocation vào field chung.

Ngược lại, cách viết sau có nguy cơ:

```java
@Aspect
class DangerousAspect {
    private String currentRequestId; // shared mutable field của singleton aspect

    @Around("...")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        currentRequestId = (String) pjp.getArgs()[0];
        return pjp.proceed();
    }
}
```

Đây chỉ là **fragment minh họa lỗi**, không phải advice dùng được độc lập. Nếu aspect là Spring singleton, một thread có thể ghi đè `currentRequestId` của thread khác. Cách đơn giản nhất là dùng **biến cục bộ** `String id` bên trong advice, truyền nó cho logger/recorder và không giữ thông tin request trong instance field.

## 3. Dự án JUnit 5 + Spring Framework chạy được

Cấu trúc:

```text
spring-aop-path-tests/
├── pom.xml
└── src/test/java/lab/SpringAspectPathTest.java
```

Ví dụ dùng **Java 17**, **Spring Framework 6.2.8**, **JUnit Jupiter 5.11.4**, **AspectJ Weaver 1.9.22.1** và Maven 3.9+.

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lab</groupId>
    <artifactId>spring-aop-path-tests</artifactId>
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

`aspectjweaver` ở đây hỗ trợ Spring xử lý cú pháp `@AspectJ` và pointcut; sự có mặt của dependency **không có nghĩa đã bật AspectJ weaving**. Test chỉ dùng Spring AOP proxy.

## 4. File kiểm thử hoàn chỉnh

Tạo `src/test/java/lab/SpringAspectPathTest.java`:

```java
package lab;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;

import static org.junit.jupiter.api.Assertions.*;

public class SpringAspectPathTest {

    record Event(String name, String thread) {}
    record Outcome(String id, boolean shouldFail,
                   String value, String error) {}

    public static class Trace {
        private final ConcurrentHashMap<
            String, ConcurrentLinkedQueue<Event>> byId =
                new ConcurrentHashMap<>();

        void add(String id, String name) {
            byId.computeIfAbsent(
                id, ignored -> new ConcurrentLinkedQueue<>()
            ).add(new Event(name, Thread.currentThread().getName()));
        }

        List<String> names(String id) {
            var queue = byId.get(id);
            if (queue == null) {
                return List.of();
            }
            return queue.stream().map(Event::name).toList();
        }

        String threadFor(String id, String eventName) {
            var queue = byId.get(id);
            if (queue == null) {
                throw new AssertionError("No events for " + id);
            }
            return queue.stream()
                .filter(event -> event.name().equals(eventName))
                .findFirst().orElseThrow().thread();
        }

        Set<String> recordedIds() {
            return Set.copyOf(byId.keySet());
        }
    }

    public static class ProbeService {
        private final Trace trace;
        private final AtomicInteger calls = new AtomicInteger();

        // Chỉ được cài trong test concurrent trước khi worker bắt đầu.
        private volatile CyclicBarrier concurrencyGate;

        public ProbeService(Trace trace) {
            this.trace = trace;
        }

        public void useConcurrencyGate(CyclicBarrier barrier) {
            this.concurrencyGate = barrier;
        }

        public int callCount() {
            return calls.get();
        }

        public String run(String id, boolean fail) {
            calls.incrementAndGet();
            trace.add(id, "target.enter");

            // Bắt buộc đủ worker cùng tới target trong concurrent test.
            CyclicBarrier gate = concurrencyGate;
            if (gate != null) {
                try {
                    gate.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                        "target gate interrupted", interrupted);
                } catch (Exception problem) {
                    throw new IllegalStateException(
                        "target gate failed", problem);
                }
            }

            if (fail) {
                trace.add(id, "target.throw");
                throw new IllegalStateException("failed:" + id);
            }

            trace.add(id, "target.return");
            return "ok:" + id;
        }

        public String unrelated(String id) {
            trace.add(id, "target.unrelated");
            return "unrelated:" + id;
        }
    }

    @Aspect
    @Order(10)
    public static class OuterAspect {
        private final Trace trace;

        public OuterAspect(Trace trace) {
            this.trace = trace;
        }

        @Around("bean(probeService) && execution(public * run(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            String id = (String) pjp.getArgs()[0];
            trace.add(id, "outer.enter");
            try {
                Object value = pjp.proceed();
                trace.add(id, "outer.return");
                return value;
            } catch (Throwable problem) {
                trace.add(id, "outer.throw");
                throw problem;
            } finally {
                trace.add(id, "outer.finally");
            }
        }
    }

    @Aspect
    @Order(20)
    public static class InnerAspect {
        private final Trace trace;

        public InnerAspect(Trace trace) {
            this.trace = trace;
        }

        @Around("bean(probeService) && execution(public * run(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            String id = (String) pjp.getArgs()[0];
            trace.add(id, "inner.enter");
            try {
                Object value = pjp.proceed();
                trace.add(id, "inner.return");
                return value;
            } catch (Throwable problem) {
                trace.add(id, "inner.throw");
                throw problem;
            } finally {
                trace.add(id, "inner.finally");
            }
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
        public ProbeService probeService(Trace trace) {
            return new ProbeService(trace);
        }

        @Bean
        public OuterAspect outerAspect(Trace trace) {
            return new OuterAspect(trace);
        }

        @Bean
        public InnerAspect innerAspect(Trace trace) {
            return new InnerAspect(trace);
        }
    }

    private static List<String> expected(boolean fail) {
        return fail
            ? List.of(
                "outer.enter", "inner.enter", "target.enter",
                "target.throw",
                "inner.throw", "inner.finally",
                "outer.throw", "outer.finally"
              )
            : List.of(
                "outer.enter", "inner.enter", "target.enter",
                "target.return",
                "inner.return", "inner.finally",
                "outer.return", "outer.finally"
              );
    }

    @Test
    void success_keeps_return_value_and_advice_order() {
        try (var context =
                 new AnnotationConfigApplicationContext(Config.class)) {
            ProbeService service = context.getBean(ProbeService.class);
            Trace trace = context.getBean(Trace.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals("ok:ok-1", service.run("ok-1", false));
            assertEquals(expected(false), trace.names("ok-1"));
            assertEquals(1, service.callCount());
            assertEquals(Set.of("ok-1"), trace.recordedIds());
        }
    }

    @Test
    void exception_propagates_and_finally_events_still_run() {
        try (var context =
                 new AnnotationConfigApplicationContext(Config.class)) {
            ProbeService service = context.getBean(ProbeService.class);
            Trace trace = context.getBean(Trace.class);

            assertTrue(AopUtils.isAopProxy(service));
            IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.run("err-1", true)
            );

            assertEquals("failed:err-1", error.getMessage());
            assertEquals(expected(true), trace.names("err-1"));
            assertEquals(1, service.callCount());
        }
    }

    @Test
    void method_outside_pointcut_does_not_run_advice() {
        try (var context =
                 new AnnotationConfigApplicationContext(Config.class)) {
            ProbeService service = context.getBean(ProbeService.class);
            Trace trace = context.getBean(Trace.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals("unrelated:no-aop",
                service.unrelated("no-aop"));
            assertEquals(List.of("target.unrelated"),
                trace.names("no-aop"));
            assertEquals(0, service.callCount());
        }
    }

    @Test
    void concurrent_invocations_keep_their_own_order()
            throws Exception {
        try (var context =
                 new AnnotationConfigApplicationContext(Config.class)) {
            ProbeService service = context.getBean(ProbeService.class);
            Trace trace = context.getBean(Trace.class);

            assertTrue(AopUtils.isAopProxy(service));

            final int workers = 8;
            service.useConcurrencyGate(new CyclicBarrier(workers));

            AtomicInteger sequence = new AtomicInteger();
            ExecutorService pool = Executors.newFixedThreadPool(
                workers,
                task -> new Thread(
                    task, "aop-test-" + sequence.incrementAndGet())
            );

            try {
                List<Callable<Outcome>> tasks =
                    IntStream.range(0, workers)
                        .mapToObj(i -> (Callable<Outcome>) () -> {
                            String id = "request-" + i;
                            boolean shouldFail = (i % 3 == 0);

                            try {
                                String value = service.run(id, shouldFail);
                                return new Outcome(
                                    id, shouldFail, value, null);
                            } catch (IllegalStateException error) {
                                return new Outcome(
                                    id, shouldFail, null,
                                    error.getMessage());
                            }
                        })
                        .toList();

                // Chờ cả nhóm hoàn thành; gate ở trong target
                // buộc cả tám invocation thật sự chồng nhau.
                List<Future<Outcome>> futures = pool.invokeAll(tasks);

                Set<String> targetThreads = new HashSet<>();
                for (int i = 0; i < workers; i++) {
                    Outcome outcome = futures.get(i).get(
                        5, TimeUnit.SECONDS);
                    String id = "request-" + i;
                    boolean shouldFail = i % 3 == 0;

                    assertEquals(id, outcome.id());
                    assertEquals(shouldFail, outcome.shouldFail());
                    assertEquals(expected(shouldFail), trace.names(id),
                        "wrong trace for " + id);

                    if (shouldFail) {
                        assertNull(outcome.value());
                        assertEquals("failed:" + id,
                            outcome.error());
                    } else {
                        assertEquals("ok:" + id, outcome.value());
                        assertNull(outcome.error());
                    }

                    String thread = trace.threadFor(id, "target.enter");
                    assertTrue(thread.startsWith("aop-test-"));
                    targetThreads.add(thread);
                }

                assertEquals(workers, service.callCount());
                assertEquals(workers, trace.recordedIds().size());
                assertEquals(workers, targetThreads.size(),
                    "all workers must reach the barrier");
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
```

### 4.1. Vì sao ví dụ này đúng phạm vi Spring?

- `Config` đăng ký `Trace`, `ProbeService`, hai `@Aspect` làm Spring beans; `@EnableAspectJAutoProxy(proxyTargetClass=true)` cho phép Spring tạo class-based proxy.
- Các lời gọi cần đo luôn dùng `context.getBean(ProbeService.class)`. Đừng thay bằng `new ProbeService(trace)` vì object đó không đi qua Spring AOP proxy.
- Target là public non-final class, `run()` là public non-final method, có thể được proxy kiểu CGLIB.
- `bean(probeService)` là Spring AOP pointcut designator giới hạn bean name; `execution(public * run(..))` giới hạn method. `unrelated()` trên cùng bean proxy không thuộc pointcut của hai advice.
- `OuterAspect` và `InnerAspect` chỉ có một `@Around` mỗi aspect, với `@Order(10)` và `@Order(20)` khác nhau. Do đó phép thử có thứ tự expected rõ ràng và không dựa vào thứ tự khai báo method trong class.
- `pjp.getArgs()[0]` được đọc trong **mỗi invocation** rồi lưu ở biến local `id`; các aspect không chia sẻ mutable request state giữa threads.
- `Trace` thuộc mỗi context. Mỗi `@Test` tạo context mới nên dữ liệu của test trước không thể làm test sau PASS giả hoặc FAIL vì event cũ.

`CyclicBarrier` được đưa vào `ProbeService` chỉ ở ca concurrency nhằm biến bài test thành một phép thử thực sự đồng thời: **tám lời gọi phải cùng tới target** rồi mới được đi tiếp. Nếu chỉ tạo tám `Callable` mà target xử lý quá nhanh, runtime có thể chạy gần như tuần tự và không phát hiện một số lỗi tương tranh. Barrier ở target tránh kết luận “có executor” là đồng nghĩa “đã kiểm thử overlap”.

## 5. Chạy và xem kết quả

Trong thư mục `spring-aop-path-tests`:

```bash
mvn test
```

Kỳ vọng ở mức JUnit assertions là bốn test sau PASS:

```text
success_keeps_return_value_and_advice_order
exception_propagates_and_finally_events_still_run
method_outside_pointcut_does_not_run_advice
concurrent_invocations_keep_their_own_order
```

Đây là **tên test và trạng thái kỳ vọng**, không phải toàn bộ stdout Maven/Surefire. Ví dụ lưu trace trong bộ nhớ để JUnit so sánh; không tự in mọi event ra console.

### 5.1. Bảng kết quả cần chứng minh

| Trường hợp | Return hoặc exception | Event/khẳng định bắt buộc |
| --- | --- | --- |
| `run("ok-1", false)` | Trả `"ok:ok-1"` | 8 event success đúng thứ tự; target đúng 1 lần |
| `run("err-1", true)` | `IllegalStateException("failed:err-1")` | 8 event failure đúng thứ tự, có đủ `throw/finally`; target đúng 1 lần |
| `unrelated("no-aop")` | Trả `"unrelated:no-aop"` | Chỉ `target.unrelated`, không có outer/inner advice |
| 8 lời gọi trên 8 threads | 3 failures và 5 successes (i = 0, 3, 6 thất bại) | Mỗi ID có đúng 8 event riêng; target tổng 8 lần; 8 worker threads đến được barrier |

Trong concurrent test, danh sách ID dự kiến là `request-0` đến `request-7`. Với `i % 3 == 0`, ba lời gọi thất bại có ID `request-0`, `request-3`, `request-6`; năm ID còn lại trả giá trị riêng. Mỗi kết quả được truy ra từ `Future<Outcome>` tại cùng index của `tasks`, rồi assert đối chiếu ID và trace tương ứng.

**Không assert toàn bộ thứ tự event giữa các thread.** Một `outer.enter` của request-1 có thể xuất hiện trước hoặc sau `inner.enter` của request-2 và cả hai đều hợp lệ. Hợp đồng chỉ yêu cầu thứ tự **trong từng invocation**, sự tách biệt context và số lần thực thi.

## 6. Phân tích ý nghĩa của đường thành công

Đối với `run("ok-1", false)`:

```text
caller
  OuterAspect.enter
    InnerAspect.enter
      ProbeService.run:
        target.enter
        target.return
      InnerAspect.return
      InnerAspect.finally
  OuterAspect.return
  OuterAspect.finally
caller nhận "ok:ok-1"
```

Ba nhóm assertion quan trọng:

1. **Kết quả không bị thay đổi:** `assertEquals("ok:ok-1", result)`.
2. **Thứ tự đúng:** so sánh danh sách event bằng `assertEquals(expected(false), trace.names(id))`, không chỉ so sánh `size()==8`.
3. **Không gọi target nhiều lần:** `callCount()==1`.

Nếu một custom `@Around` vô tình gọi `pjp.proceed()` hai lần, test có khả năng phát hiện vì `target.enter/return` xuất hiện lặp và counter vượt kỳ vọng. Nếu advice bỏ `proceed()` và trả dữ liệu giả, trace sẽ thiếu các event của inner aspect và target.

Nếu business policy yêu cầu advice thay đổi kết quả một cách có chủ đích, expected return phải mô tả điều đó rõ ràng. Bài tập chọn **không thay đổi return value** để chứng minh interceptor chỉ quan sát luồng thực thi.

## 7. Phân tích đường ngoại lệ

Đối với `run("err-1", true)`:

```text
target ném IllegalStateException("failed:err-1")
      |
      v
inner.catch    → inner.throw
inner.finally  → inner.finally
      |
      v
outer.catch    → outer.throw
outer.finally  → outer.finally
      |
      v
caller nhận lại cùng loại exception với message mong đợi
```

`@Around` phải cẩn thận với khối `try/catch/finally`. Nếu aspect nuốt exception và `return null`, caller không còn nhận `IllegalStateException`; test `assertThrows` sẽ thất bại. Nếu aspect ném exception mới, loại/message mà caller quan sát có thể đổi; test cũng phải bảo vệ hợp đồng cần thiết.

**Phân biệt `@AfterThrowing` và `finally`:** ví dụ dùng `catch/finally` trong `@Around` để minh họa thứ tự. Nó không phát biểu rằng một `@AfterThrowing` advice bất kỳ luôn chạy cùng vị trí hoặc có cùng điều kiện match. Khi dự án thực dùng `@AfterThrowing`, tạo expected riêng theo advice kind, `throwing` binding và kiểu exception.

Đối với exception nghiêm trọng ngoài `IllegalStateException`, phần `catch(Throwable)` trong hai aspect vẫn log rồi rethrow. Fixture test tập trung vào exception nghiệp vụ đã định nghĩa; không chủ động gây JVM `Error` chỉ để kiểm chứng.

## 8. Vì sao concurrency phải kiểm tra cả thứ tự và tính cô lập?

Nếu dùng:

```java
private String lastId; // field trong singleton aspect
```

rồi `lastId = currentRequestId` trước `proceed()` và đọc lại sau đó, nhiều threads có thể ghi đè lẫn nhau. Test tuần tự thường PASS vì không có interleaving. Trong concurrent test, lúc tám target đang chờ tại barrier, tất cả advice invocation đã đi tới `proceed()`, tạo cơ hội quan sát lỗi state bị dùng chung khi tiếp tục.

Cấu trúc đúng trong ví dụ:

```java
String id = (String) pjp.getArgs()[0]; // local cho một invocation
trace.add(id, "outer.enter");
Object value = pjp.proceed();
trace.add(id, "outer.return");
```

**Đây là fragment logic của `@Around`, không phải một method đầy đủ.** Biến local `id` riêng theo stack của invocation; recorder chia bucket theo ID. Ngay cả khi nhiều threads cùng gọi một bean singleton, không thread nào có thể ghi đè `id` của lời gọi khác.

Một integration test concurrency nên bảo vệ các invariant:

- Có đúng **N invocation** và đúng **N target executions** nếu không có retry/short-circuit cố ý.
- Mỗi invocation ID có số event và thứ tự dự kiến; không có sự kiện thuộc ID khác.
- Exception của ID thất bại không làm lời gọi thành công của ID khác thất bại.
- Đường `finally` có event đầy đủ cho mọi ID, kể cả thất bại.
- Executor hoàn thành trong khoảng chờ hợp lý, không deadlock ở advice hoặc recorder.
- Shared infrastructure trong fixture (ở đây `Trace`) có cấu trúc dữ liệu an toàn luồng.

**Không nhầm “nhiều threads” với `@Async`:** test này chạy service đồng bộ qua Spring proxy từ **nhiều caller threads**. `@Async` sẽ thêm ranh giới executor riêng và có thể thay vị trí advice; để kiểm tra `@Async` thật phải bổ sung test chuyên biệt chờ `Future/CompletableFuture`, xác định thread của target và xử lý exception từ worker.

## 9. Các lỗi cố ý có thể đưa vào để chứng minh test nhạy

Sau khi test baseline PASS, thay một biến mỗi lượt và chạy lại.

### 9.1. Đảo thứ tự aspect

Đổi `OuterAspect @Order(10)` thành `@Order(30)`, giữ `InnerAspect @Order(20)`. Khi đó `InnerAspect` có precedence cao hơn, đi vào trước. Expected success ban đầu phải FAIL. Điều này chứng minh test bảo vệ **thứ tự**, không chỉ sự hiện diện của advice.

### 9.2. Bỏ `proceed()` trong advice ngoài

Ví dụ một đoạn ngắn thay cho logic cũ:

```java
trace.add(id, "outer.enter");
return "bypassed";
```

Đây là fragment mô tả lỗi. Test return value và trace sẽ FAIL vì target/inner không được gọi. Không dùng kiểu stub này trong hệ thống thật trừ khi short-circuit là chính sách được thiết kế rõ và có test riêng.

### 9.3. Nuốt exception

Nếu `OuterAspect` thay `throw problem` bằng `return null`, test ngoại lệ sẽ thất bại ở `assertThrows`. Test success vẫn có thể PASS; do đó bắt buộc có hai nhánh riêng.

### 9.4. Đưa request ID vào field của aspect

Thay local `id` bằng field `currentId`, gán trước `proceed()` rồi dùng field khi ghi `outer.return` hoặc `outer.throw`. Trong test nhiều luồng, các `return/throw/finally` có thể bị ghi sang bucket khác. Kết quả từng ID không còn khớp và test phải báo FAIL. Với race condition, một lần chạy không phải bằng chứng duy nhất để loại trừ lỗi; stress test, kiểm tra code và tooling phân tích đồng thời là lớp bổ sung.

### 9.5. Thay một hàng đợi thread-safe bằng `ArrayList` chia sẻ

Một `ArrayList` được nhiều threads sửa mà không đồng bộ có thể gây mất event, sai dữ liệu hoặc lỗi runtime. Tuy nhiên lỗi race không bắt buộc phát lộ mỗi lần. Không đánh giá một container là an toàn luồng chỉ vì test một lần PASS; lựa chọn cấu trúc dữ liệu phải dựa trên hợp đồng Java concurrency của nó.

### 9.6. Gọi target bằng `new`

Nếu test tự khởi tạo `new ProbeService(trace)`, Spring proxy sẽ không có mặt, advice không chạy. Đó là negative control có ích nhưng không phải bài test thực về advisor chain. Hãy luôn assert `AopUtils.isAopProxy(service)` trước test trace.

## 10. Điểm cần lưu ý khi đưa bài thực hành vào ứng dụng Spring Boot

### 10.1. Giữ contract theo từng loại concern

Không nhất thiết mọi aspect đều có 8 event như fixture. Một aspect chỉ dùng `@Before` có thể chỉ cần event `before`; một aspect audit cần sự kiện **sau commit** phải kiểm thử transaction outcome, không chỉ `after returning` của target. Với caching, lời gọi cache hit có thể bỏ qua target và một số advice tùy advisor order. Với retry, target có thể chạy nhiều lần theo thiết kế.

Vì vậy expected trace trong production phải xuất phát từ hợp đồng chính sách và cấu hình advisor thật, chứ không sao chép máy móc trace mẫu.

### 10.2. Self-invocation và method visibility

Trong proxy mode, lời gọi nội bộ `this.run(...)` trên target không đi qua proxy của method được gọi; advice method đó có thể bị bỏ qua. Method `private`, `final` hoặc không được proxy theo loại proxy đang dùng cũng không thể được intercept theo cách ta giả định. Với JDK dynamic proxy, thường truy cập qua interface; với class-based proxy, chú ý method không thể override.

Nếu test gọi một controller/service nhưng chỉ thấy `target.enter`, hãy kiểm tra đường đi qua proxy, loại bean và pointcut trước khi sửa `@Order`.

### 10.3. Scope của aspect

Spring thường tạo `@Aspect` làm singleton bean. Điều này nghĩa là field mutable của aspect có thể bị chia sẻ giữa nhiều lời gọi. Không dùng shared `StringBuilder`, `HashMap` thường hay `currentUser` field để giữ dữ liệu per-invocation. Dùng local variables, immutable values, explicit context hoặc cấu trúc thread-safe phù hợp.

`ThreadLocal` không phải giải pháp mặc định cho mọi async/thread-pool: cần cleanup và propagation có kiểm soát. Nếu Spring application dùng `@Async`, reactive streams hoặc virtual threads, những giả định về thread-affinity cần kiểm thử riêng.

### 10.4. Context và isolation

Fixture tạo `AnnotationConfigApplicationContext` mới cho mỗi JUnit test. Trong Spring Boot, `@SpringBootTest` có thể cache context giữa các test; nếu collector là singleton, cần reset đúng trước từng test và tránh chạy hai test cùng ghi một trace mà không có isolation. Khi không cần toàn bộ Boot auto-configuration, một context hẹp cũng giúp chẩn đoán AOP dễ hơn.

### 10.5. Hạn chế assertion dựa vào implementation details

`Advised.getAdvisors()` và `AopUtils.isAopProxy()` giúp chẩn đoán. Nhưng số lượng advisor nội bộ, tên proxy class, order của hạ tầng transaction/security/cache hoặc log startup có thể phụ thuộc cấu hình/phiên bản. Tốt nhất assert những event mà **application aspect và target** chủ động ghi, cộng với kết quả/exception có nghĩa nghiệp vụ.

## 11. Mở rộng bài kiểm thử

| Mở rộng | Bổ sung tối thiểu | Invariant cần đo |
| --- | --- | --- |
| `@Transactional` | Transaction manager và DB in-memory; commit/rollback test | Exception rollback đúng; custom advice có `TransactionSynchronizationManager` state phù hợp |
| `@Async` | `TaskExecutor`, `CompletableFuture`, chờ có timeout | Target chạy trên executor; exception thể hiện qua future; trace không mất |
| `@Cacheable` | Cache manager, hai lần gọi cùng key | Miss chạy target, hit theo policy đã định; aspect outer/inner đúng `@Order` |
| `@AfterReturning` | Advice riêng bind return value | Chỉ chạy khi target trả về bình thường, giá trị bind đúng kiểu |
| `@AfterThrowing` | Advice riêng bind exception | Chỉ chạy với exception phù hợp; không tự nuốt lỗi |
| Nhiều `@Around` | `@Order` tường minh cho các aspect khác nhau | Expected trace nested đúng thứ tự |
| Retry policy | Bộ đếm attempt/target và correlation ID | Số lần `proceed()` theo contract retry, không bị coi nhầm trùng lỗi |
| Audit publisher | Fake publisher thread-safe, expectation theo ID | Một audit/result theo policy, không trộn ID |
| Metrics/tracing | Recorder theo request/span | Không kết luận thứ tự toàn cục giữa concurrent requests |

Nếu một aspect của ứng dụng thực hiện **hai trách nhiệm khác nhau**, ví dụ vừa audit vừa đổi exception, nên tách assertion thành hai lớp: business result/exception và trace. Tránh làm test chỉ PASS vì một logging line xuất hiện trong khi hành vi nghiệp vụ đã sai.

## 12. Quy trình kiểm thử có thể lặp lại

1. **Chọn Spring bean/method chính xác** và xác định caller có đi qua proxy. Ghi chú target class, interface và cấu hình `@EnableAspectJAutoProxy`/auto-proxy tương ứng.
2. **Liệt kê các custom aspects** match method, xác định `@Order`, pointcut, advice kind, điều kiện return/exception, có gọi `proceed()` bao nhiêu lần.
3. **Xây expected trace** theo từng đường success/failure trước khi chạy. Ghi rõ đoạn nào thuộc target, đoạn nào thuộc advice.
4. **Tạo trace collector an toàn luồng**, nhóm theo invocation/correlation ID; dùng local variables trong advice cho state per-request.
5. **Gọi qua Spring-managed bean**, assert proxy tồn tại, kết quả/exception đúng, số target invocations chính xác và toàn bộ event list đúng thứ tự.
6. **Đối chứng method không match** để phát hiện pointcut quá rộng.
7. **Buộc overlap giữa các caller threads** bằng barrier/latch khi cần kiểm thử concurrency, chờ future hoàn thành và assert riêng từng invocation ID.
8. **Làm test FAIL có chủ đích** bằng việc đảo `@Order`, bỏ `proceed()` hoặc nuốt exception; xác nhận assertion thực sự phát hiện sai.
9. **Đưa vào CI**, tránh assertion timing/thứ tự toàn cục đa luồng. Đối với race phức tạp, chạy stress tests và phân tích thread-safety bổ sung.
10. **Khi ghép Spring transaction/async/cache**, xác định boundary riêng trước khi bổ sung expected trace của hạ tầng.

### Checklist trước khi kết luận hoàn thành

- [ ] Dùng Spring context thật, aspect là Spring bean, service lấy bằng `getBean`.
- [ ] `AopUtils.isAopProxy(service)` đúng và pointcut chỉ match intended method.
- [ ] Có expected trace cho thành công và exception, assert cả list theo thứ tự.
- [ ] Return value/exception không bị advice thay đổi ngoài chính sách.
- [ ] Mọi `@Around` gọi `proceed()` đúng số lần.
- [ ] Method không match không chạy custom advice.
- [ ] Concurrent invocation ID duy nhất, trace per-ID không lẫn nhau.
- [ ] Recorder và state trong aspect phù hợp với nhiều threads.
- [ ] Có cơ chế đồng bộ hoàn thành, không dùng `Thread.sleep` để giả định concurrency.
- [ ] Có negative controls chứng minh test thực sự phát hiện hồi quy.
- [ ] Không suy diễn `@Async`, transaction/cache semantics từ fixture synchronous.

## 13. Kết luận

**Kiểm chứng đường thành công, ngoại lệ và nhiều luồng đồng thời trong Spring AOP** cần nhiều hơn việc thấy advice đã được gọi. Phải dùng **Spring proxy thật**, định nghĩa thứ tự advice theo cấu hình, ghi vết có correlation ID, đo result/exception, kiểm tra target invocation count và bảo vệ state trong singleton aspect khỏi race condition.

Một test thành công phải chứng minh cả thứ tự vào/ra của `@Around` lẫn kết quả nghiệp vụ. Một test ngoại lệ phải chứng minh exception đi xuyên qua chuỗi advisor và `finally` chạy đúng. Một test nhiều luồng phải buộc các lời gọi chồng thời gian, sau đó xác minh từng trace độc lập thay vì đòi một thứ tự tổng quát không tồn tại giữa các thread.

### Tài liệu chính thức

- [Spring Framework — @AspectJ Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): `@Around`, `proceed()`, exception và advice ordering.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): proxy invocation, self-invocation và giới hạn intercept.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): `@EnableAspectJAutoProxy`, aspect bean.
- [Spring Framework — AOP API](https://docs.spring.io/spring-framework/reference/core/aop-api.html): advisor/proxy introspection.
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/): `@Test`, `assertEquals`, `assertThrows`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-practical-workshop-009">Tích hợp aspect với một cơ chế Spring đang có proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Tích hợp custom aspect với cơ chế Spring đã có AOP proxy

## 1. Bối cảnh và mục tiêu

Trong một ứng dụng Spring, không phải custom `@Aspect` mới là nguồn duy nhất tạo ra proxy. Các tính năng của chính Spring — ví dụ `@Transactional`, `@Cacheable`, method security hoặc Spring Retry — cũng có thể đăng ký **Advisor/MethodInterceptor** để bao quanh Spring bean bằng AOP proxy.

Khi bổ sung một aspect ghi audit, tracing, đo thời gian hoặc chuẩn hóa logging vào service vốn đã được caching/transaction advice xử lý, những câu hỏi sau trở nên quan trọng:

- Spring tạo **một proxy có nhiều advisors** hay hai proxy lồng nhau?
- Aspect nào chạy trước? `@Around` có bao cả cache lookup, transaction begin/commit hoặc retry attempts không?
- Khi cache hit, **target method không chạy**; custom audit có chạy không?
- Khi transaction rollback, event mà aspect ghi có rollback không?
- Khi retry nhiều lần, custom advice được gọi **một lần theo request** hay một lần theo **mỗi attempt**?
- Tại sao cấu hình có `@EnableAspectJAutoProxy` và `@EnableCaching` nhưng không có hai lần business execution?
- Làm sao kiểm thử advisor order, proxy identity và actual behavior mà không phải nạp toàn bộ Spring Boot application?

Bài giải quyết trực tiếp yêu cầu **tích hợp một Spring `@Aspect` vào một bean đã được một cơ chế Spring khác advice**. Ví dụ chính dùng **Spring caching** để quan sát dễ dàng: cache hit có thể bỏ qua target nhưng thứ tự advisor vẫn quyết định custom audit có thấy invocation đó không. Sau đó bài chỉ ra những khác biệt thiết yếu nếu thay caching bằng transaction hoặc retry.

Đây là bài **Spring AOP proxy-based integration**, không phải một bài tổng quát về AspectJ weaving hoặc thiết kế cache/database. Mọi ví dụ vận dụng `@Aspect` trong Spring ApplicationContext.

### Các kết quả cần đạt

1. Hiểu target bean, AOP proxy, advisor, pointcut và advice trong một context Spring.
2. Kết hợp `@EnableAspectJAutoProxy` với `@EnableCaching` mà không tạo proxy thủ công không cần thiết.
3. Định nghĩa thứ tự bằng `@Order` và `@EnableCaching(order=...)`.
4. Giải thích được vì sao **3 audit invocations nhưng chỉ 2 target executions**.
5. Xác minh bằng `AopUtils`, `Advised` và JUnit 5 tests.
6. Nhận biết các khác biệt về transaction, retry, self-invocation, bean scopes và proxy configuration.
7. Tránh double proxy, double advice registration và logging/side effects không an toàn.

## 2. Một bean, nhiều advisors: mô hình cần hình dung

Giả sử `CatalogService` có method `find(String code)` được khai báo `@Cacheable("books")`. Ngoài ra, `AuditAspect` có `@Around` match chính method ấy.

Cấu hình Spring có thể tạo một đối tượng proxy như sau:

~~~text
Caller
  |
  v
Spring AOP proxy implements CatalogApi
  |
  +-- Advisor: AuditAspect @Around              [order -100]
  |      |
  |      +-- log before
  |      +-- proceed()
  |              |
  |              v
  |         Advisor: Spring CacheInterceptor  [order LOWEST_PRECEDENCE]
  |              |
  |              +-- Cache hit? YES --> return cached value
  |              |
  |              +-- Cache miss? NO --> invoke target
  |                                    |
  |                                    v
  |                           CatalogService.find(code)
  |                                    |
  |                                    v
  |                              cache result
  |              |
  |              v
  |         return to AuditAspect
  |      +-- log after
  |
  v
Caller nhận kết quả
~~~

Đây là **mô hình một proxy chứa nhiều advisors**, thường xảy ra khi Spring infrastructure hợp nhất auto-proxy processing trong cùng ApplicationContext. **Không** được khẳng định rằng mọi combination/framework version/custom BeanPostProcessor đều luôn tạo đúng một proxy; quan sát bean thực tế khi nghi ngờ nested proxies.

Vì AuditAspect ở ngoài cache interceptor, mỗi invocation từ caller vào proxy đều có `audit.before`/`audit.after`. Tuy nhiên:

- Lần đầu `find("A")` cache miss → audit **và** target cùng chạy.
- Lần hai `find("A")` cache hit → audit vẫn chạy nhưng **target không chạy**.
- Lần ba `find("B")` cache miss → audit và target đều chạy.

Kết luận: **đếm advice invocations không đồng nghĩa đếm target method invocations**. Đây là cách quan sát rất hữu ích khi tích hợp custom aspect với Spring transaction/cache/retry.

## 3. Spring auto-proxy infrastructure và khả năng hợp nhất

### 3.1. Hai @Enable không tự suy ra hai proxy

Một cấu hình thường thấy:

~~~java
@Configuration
@EnableAspectJAutoProxy
@EnableCaching
public class AopConfig {
    // @Bean target, @Bean aspect, @Bean CacheManager...
}
~~~

- `@EnableAspectJAutoProxy` kích hoạt việc xử lý Spring `@AspectJ` style aspects thành advisors.
- `@EnableCaching` đăng ký hạ tầng đọc `@Cacheable`, `@CachePut`, `@CacheEvict` và tư vấn caching vào những beans phù hợp.
- Các cơ chế có thể cùng tham gia Spring AOP proxy processing; thông thường Spring quản lý sự phối hợp bằng auto-proxy infrastructure.
- Chúng **không tự động có cùng order**, cũng không làm cache hoặc aspect tự chạy nếu không có metadata pointcut phù hợp.
- `@Aspect` class cần là **Spring bean**: `@Component`, component scan hoặc `@Bean`; viết `@Aspect` trên một class không đăng ký chưa đủ.

Spring Framework documentation mô tả một số cấu hình auto-proxy có thể được hợp nhất và áp dụng proxy settings mạnh hơn (chẳng hạn buộc CGLIB từ một configuration). Điều này không có nghĩa mọi `BeanPostProcessor` tùy chỉnh, `ProxyFactoryBean` hoặc một `ProxyFactory` do code ứng dụng tạo cũng tự hợp nhất vào đúng một object proxy.

### 3.2. Đừng thêm một ProxyFactory vô điều kiện

Một mẫu dễ gây nhầm:

~~~java
CatalogApi alreadyAdvised = context.getBean(CatalogApi.class);

ProxyFactory factory = new ProxyFactory(alreadyAdvised);
factory.addAdvice(customAuditInterceptor);
CatalogApi extraProxy = (CatalogApi) factory.getProxy();
~~~

Đây chỉ là **snippet minh họa**; nếu `alreadyAdvised` đã là Spring proxy có caching/aspect advisors, `extraProxy` có thể thành **proxy bọc proxy**. Khi hai lớp cùng chứa audit behavior, log có thể lặp dù target chỉ chạy một lần.

Cần xem:

~~~java
AopUtils.isAopProxy(alreadyAdvised);
AopUtils.isAopProxy(extraProxy);
~~~

Với target source tĩnh, `AopProxyUtils.getSingletonTarget(extraProxy)` có thể giúp xác minh inner target có phải proxy nữa không. Không unwrap dynamic/scoped/pooling `TargetSource` một cách tùy tiện.

### 3.3. Spring Boot và proxyTargetClass

Spring Framework core có thể sử dụng JDK interface proxy khi target implement interface; class-based proxy (CGLIB) được dùng khi không có suitable interface hoặc bị cấu hình buộc. Một số Spring Boot versions/configurations chọn class proxy mặc định.

Điều phải kiểm tra là **proxy trên bean đang chạy thật**, không chỉ những giá trị tại một `@Enable...` mà bạn tình cờ nhìn thấy:

~~~java
Object bean = context.getBean("catalogService");
System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
~~~

`proxyTargetClass=true` từ một auto-proxy configuration có thể ảnh hưởng những infrastructure AOP khác trong context. Vì vậy một Kotlin `final` class implement interface có thể hoạt động dưới JDK proxy nhưng lỗi khi cấu hình tổng thể buộc CGLIB. Không đổi proxy strategy chỉ để “sửa” audit order.

## 4. Thứ tự advice: outer và inner không thể hoán đổi vô hại

Spring dùng `Ordered`/`@Order` và advisor `order` để quyết định precedence. Giá trị order **càng nhỏ thì precedence càng cao**; advice precedence cao chạy **trước trên đường vào** và **sau trên đường ra**.

Ở ví dụ chính:

~~~java
@Aspect
@Order(-100)
public class AuditAspect {
    @Around("execution(* demo.CatalogService.find(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        // ...
    }
}
~~~

~~~java
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE)
public class CacheConfig {
    // ...
}
~~~

`-100` có precedence cao hơn `Ordered.LOWEST_PRECEDENCE`, nên custom audit nằm **ngoài caching advice**. Lệnh `pjp.proceed()` đi qua cache advisor phía trong; cache hit có thể trả kết quả mà không gọi target.

### 4.1. Cache trong audit — audit thấy cả cache hits

~~~text
Audit (outer)
  Cache (inner)
    Target (chỉ trên cache miss)

call find("A") #1 -> audit 1, target 1
call find("A") #2 -> audit 2, target vẫn 1
~~~

Đây là cấu hình hợp lý khi audit/tracing muốn ghi nhận **mọi lần client gọi API** dù kết quả đến từ cache.

### 4.2. Cache ngoài audit — cache hit bypass audit

Nếu cache advice có order nhỏ hơn custom audit, chẳng hạn:

~~~java
@EnableCaching(order = -200)
~~~

trong khi audit vẫn `@Order(-100)`, caching advisor có precedence cao hơn:

~~~text
Cache (outer)
  Audit (inner)
    Target

call find("A") #1 -> audit chạy, target chạy
call find("A") #2 -> cache hit, audit không chạy, target không chạy
~~~

Trường hợp này có thể đúng nếu audit chỉ muốn quan sát **cache miss/actual computation**, nhưng có thể sai nếu compliance cần log **mọi request**. Hãy chọn order từ yêu cầu, không chỉ theo giá trị mặc định thuận tiện.

### 4.3. Vì sao không dựa vào thứ tự ngẫu nhiên?

Nếu nhiều advisors cùng `Ordered.LOWEST_PRECEDENCE`, thứ tự tương đối có thể không được bảo đảm theo logic ứng dụng. Một thay đổi version, bean registration hoặc advisor khác có thể làm assertion “trước cache” thay đổi. Đối với yêu cầu quan trọng, hãy cho aspect/advisor **order tường minh**, test behavior và không hard-code số thứ tự advisor trong array nội bộ.

Đừng dùng `@Order` để xóa advisor trùng. `@Order` chỉ thay thứ tự, không deduplicate registrations.

## 5. Ví dụ hoàn chỉnh Java 17 + Spring Framework + JUnit 5

Dự án dùng Java 17, Maven, **Spring Framework 6.1.15**, AspectJ Weaver 1.9.22.1 và JUnit Jupiter. Toàn bộ Spring context được cấu hình bằng Java, không dùng Spring Boot, không có database hay network. Caching dùng `ConcurrentMapCacheManager` in-memory.

### 5.1. Cấu trúc

~~~text
spring-aop-cache-integration/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── CatalogApi.java
    │   ├── Events.java
    │   ├── CatalogService.java
    │   ├── AuditAspect.java
    │   ├── AppConfig.java
    │   └── App.java
    └── test/java/demo/
        └── AopCacheIntegrationTest.java
~~~

### 5.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-cache-integration</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
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
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>17</release>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>demo.App</mainClass>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` cung cấp `ApplicationContext`, caching annotations và `ConcurrentMapCacheManager`; `spring-aop` cung cấp proxy/advisor APIs; `aspectjweaver` được Spring dùng để xử lý `@AspectJ` pointcuts. **Không** cần AspectJ `-javaagent` vì ta đang dùng Spring proxy mode.

### 5.3. CatalogApi: business interface

**src/main/java/demo/CatalogApi.java**

~~~java
package demo;

public interface CatalogApi {
    String find(String code);
}
~~~

Caller sẽ lấy bean theo `CatalogApi`. Vì implementation class implement interface và `proxyTargetClass=false`, ví dụ dự kiến dùng Spring **JDK dynamic proxy**, không ép cast proxy thành `CatalogService`.

### 5.4. Events: bộ ghi trạng thái để test

**src/main/java/demo/Events.java**

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

public final class Events {
    private final List<String> events = new ArrayList<>();

    public void add(String event) {
        events.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }
}
~~~

Dùng `ArrayList` vì demo và mỗi test đều đơn luồng, tạo Spring context riêng. Nếu chuyển sang async/retry đa thread, dùng một recorder thread-safe hoặc synchronized collections; không dùng mutable `ArrayList` chung giữa nhiều requests trong production.

### 5.5. CatalogService: @Cacheable nằm trên target

**src/main/java/demo/CatalogService.java**

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.cache.annotation.Cacheable;

public class CatalogService implements CatalogApi {
    private final Events events;
    private final AtomicInteger executions = new AtomicInteger();

    public CatalogService(Events events) {
        this.events = events;
    }

    @Override
    @Cacheable("books")
    public String find(String code) {
        int number = executions.incrementAndGet();
        events.add("target:" + code + ":" + number);

        if ("FAIL".equals(code)) {
            throw new IllegalStateException("unavailable:" + code);
        }

        return code + "#" + number;
    }

    public int executions() {
        return executions.get();
    }
}
~~~

`@Cacheable("books")` là metadata của Spring caching. Khi gọi `find` qua Spring proxy và caching infrastructure đã bật, giá trị được lưu theo key do `SimpleKeyGenerator` tạo từ argument `code`. Một `String` argument duy nhất thường chính là key ấy.

`executions` đếm **lượt target method thực sự chạy**, không đếm caller requests. Không trông đợi `AtomicInteger` tự rollback khi transaction thất bại (demo không có transaction). Những exception ném ra từ target thông thường **không được cache** như một successful return value bởi `@Cacheable` trong cấu hình này.

### 5.6. AuditAspect: custom @Around nằm ngoài cache

**src/main/java/demo/AuditAspect.java**

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(-100)
public class AuditAspect {
    private final Events events;

    public AuditAspect(Events events) {
        this.events = events;
    }

    @Around("execution(* demo.CatalogService.find(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        String code = String.valueOf(pjp.getArgs()[0]);
        events.add("audit.before:" + code);

        try {
            Object result = pjp.proceed();
            events.add("audit.after:" + code);
            return result;
        } catch (Throwable failure) {
            events.add("audit.error:" + code);
            throw failure;
        }
    }
}
~~~

`@Aspect` không tự làm class thành Spring bean. `AppConfig` đăng ký `AuditAspect` bằng `@Bean`. `@Order(-100)` kiểm soát vị trí của custom aspect so với cache advisor (cache advisor sẽ có `LOWEST_PRECEDENCE`). `pjp.proceed()` là điểm các advisors phía trong và target có cơ hội chạy.

`@Around` giữ nguyên result và exception; advice chỉ thêm events phục vụ quan sát. Với caching, `pjp.proceed()` **không luôn có nghĩa target method chạy**: nó có thể đến `CacheInterceptor` rồi được trả về từ cache.

### 5.7. AppConfig: bật cả aspect và cache

**src/main/java/demo/AppConfig.java**

~~~java
package demo;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE)
public class AppConfig {

    @Bean
    Events events() {
        return new Events();
    }

    @Bean
    CatalogApi catalogService(Events events) {
        return new CatalogService(events);
    }

    @Bean
    AuditAspect auditAspect(Events events) {
        return new AuditAspect(events);
    }

    @Bean
    CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("books");
    }
}
~~~

Context có bốn application beans: `Events`, target `CatalogService` (expose interface `CatalogApi`), `AuditAspect` và `CacheManager`; Spring tự thêm infrastructure advisors/auto-proxy logic. Ta **không tạo `ProxyFactory` hoặc `ProxyFactoryBean` thủ công**, tránh nested proxy do ứng dụng tự bọc lại.

Một `@Bean CatalogApi catalogService(...)` trả implementation `CatalogService`. Spring biết target class thực khi quyết định pointcut, đồng thời JDK proxy vẫn expose `CatalogApi`. `@Cacheable` được gắn trên concrete implementation method, đúng với khuyến nghị nên đặt caching annotations ở concrete class/method khi dùng các chế độ AOP khác nhau.

### 5.8. App: in kết quả tích hợp

**src/main/java/demo/App.java**

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class App {

    public static void main(String[] args) {
        try (var context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {
            CatalogApi service = context.getBean(CatalogApi.class);
            Events events = context.getBean(Events.class);

            System.out.println("aop=" + AopUtils.isAopProxy(service));
            System.out.println("jdk="
                    + AopUtils.isJdkDynamicProxy(service));

            System.out.println("first=" + service.find("A"));
            System.out.println("second=" + service.find("A"));
            System.out.println("third=" + service.find("B"));
            System.out.println("events="
                    + String.join("|", events.snapshot()));
        }
    }
}
~~~

Ở ví dụ này, caller không cần lấy `CatalogService.class`, vì JDK interface proxy không phải subclass của `CatalogService`. Nếu muốn quan sát `executions`, dùng recorder event ở target như phần output; không unwrap một Spring-managed target ngoài test chỉ để gọi method nghiệp vụ.

### 5.9. Lệnh chạy và output dự kiến

Tại thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Những dòng chương trình tự in trong cấu hình này:

~~~text
aop=true
jdk=true
first=A#1
second=A#1
third=B#2
events=audit.before:A|target:A:1|audit.after:A|audit.before:A|audit.after:A|audit.before:B|target:B:2|audit.after:B
~~~

Phân tích từng bước:

1. `aop=true`: bean là Spring AOP proxy.
2. `jdk=true`: interface `CatalogApi` được JDK dynamic proxy expose; không dùng cast concrete class.
3. Lần 1 với key `"A"`: `AuditAspect` log `before`; cache miss; target tạo `A#1`; cache lưu; audit log `after`.
4. Lần 2 với cùng key: audit **vẫn** ghi `before/after`, nhưng cache hit trả `A#1` mà **không chạy target** (không có event `target:A:2`).
5. Lần 3 với key `"B"`: cache miss; target chạy lần hai, trả `B#2`.
6. Tổng hợp: **3 caller invocations, 3 audit invocations, 2 target executions**.

Log build Maven/Spring có thể thêm thông báo tùy môi trường; chỉ những dòng `System.out.println` được liệt kê là expected application output. Đây là **kết quả dự kiến từ source**, không phải tuyên bố rằng Maven đã được chạy trong môi trường viết bài.

## 6. JUnit 5: kiểm thử order, cache hit và proxy

**src/test/java/demo/AopCacheIntegrationTest.java**

~~~java
package demo;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.junit.jupiter.api.Assertions.*;

class AopCacheIntegrationTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    static class WithoutCacheConfig {
        @Bean
        Events events() {
            return new Events();
        }

        @Bean
        CatalogApi catalogService(Events events) {
            return new CatalogService(events);
        }

        @Bean
        AuditAspect auditAspect(Events events) {
            return new AuditAspect(events);
        }
    }

    @Test
    void cacheAndAuditShareSpringProxyAndHonorOrder() {
        try (var context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {
            CatalogApi service = context.getBean(CatalogApi.class);
            Events events = context.getBean(Events.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertTrue(AopUtils.isJdkDynamicProxy(service));
            assertTrue(service instanceof Advised);

            assertEquals("A#1", service.find("A"));
            assertEquals("A#1", service.find("A"));

            assertEquals(
                List.of(
                    "audit.before:A", "target:A:1", "audit.after:A",
                    "audit.before:A", "audit.after:A"
                ),
                events.snapshot()
            );
        }
    }

    @Test
    void distinctCacheKeyStillInvokesTarget() {
        try (var context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {
            CatalogApi service = context.getBean(CatalogApi.class);
            Events events = context.getBean(Events.class);

            assertEquals("A#1", service.find("A"));
            assertEquals("B#2", service.find("B"));
            assertEquals(
                List.of(
                    "audit.before:A", "target:A:1", "audit.after:A",
                    "audit.before:B", "target:B:2", "audit.after:B"
                ),
                events.snapshot()
            );
        }
    }

    @Test
    void failureIsVisibleToAspectAndNotCachedAsSuccess() {
        try (var context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {
            CatalogApi service = context.getBean(CatalogApi.class);
            Events events = context.getBean(Events.class);

            for (int i = 0; i < 2; i++) {
                IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> service.find("FAIL")
                );
                assertEquals("unavailable:FAIL", error.getMessage());
            }

            assertEquals(
                List.of(
                    "audit.before:FAIL",
                    "target:FAIL:1",
                    "audit.error:FAIL",
                    "audit.before:FAIL",
                    "target:FAIL:2",
                    "audit.error:FAIL"
                ),
                events.snapshot()
            );
        }
    }

    @Test
    void withoutCachingInfrastructureMethodRunsTwice() {
        try (var context =
                 new AnnotationConfigApplicationContext(
                         WithoutCacheConfig.class)) {
            CatalogApi service = context.getBean(CatalogApi.class);
            Events events = context.getBean(Events.class);

            assertTrue(AopUtils.isAopProxy(service));

            assertEquals("A#1", service.find("A"));
            assertEquals("A#2", service.find("A"));

            assertEquals(
                List.of(
                    "audit.before:A", "target:A:1", "audit.after:A",
                    "audit.before:A", "target:A:2", "audit.after:A"
                ),
                events.snapshot()
            );
        }
    }

    @Test
    void rawObjectDoesNotReceiveSpringAopInterceptors() {
        Events events = new Events();
        CatalogApi raw = new CatalogService(events);

        assertFalse(AopUtils.isAopProxy(raw));
        assertEquals("A#1", raw.find("A"));
        assertEquals("A#2", raw.find("A"));
        assertEquals(
            List.of("target:A:1", "target:A:2"),
            events.snapshot()
        );
    }
}
~~~

`WithoutCacheConfig` là một **context đối chứng**, cố tình không bật `@EnableCaching` nhưng vẫn bật `@EnableAspectJAutoProxy` và đăng ký custom aspect. Do đó advice vẫn chạy nhưng không có Spring cache advisor để short-circuit target; hai calls cùng key chạy target hai lần.

`rawObjectDoesNotReceiveSpringAopInterceptors` dùng object tự `new`: annotation `@Cacheable` và aspect có ở mã nguồn nhưng không có Spring-managed proxy, nên hai calls đều chạy target và không log audit.

Mỗi test tự tạo và đóng context riêng, tránh `ConcurrentMapCacheManager` hoặc recorder state rò từ test này sang test khác. Tests cũng **không giả định số advisors luôn bằng hai**: Spring có thể thêm infrastructure advisors; chỉ assert proxy identity và behavior.

Chạy:

~~~bash
mvn -q test
~~~

Kỳ vọng khi tất cả mã biên dịch và context tạo thành công:

~~~text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
~~~

Báo cáo nằm trong `target/surefire-reports/demo.AopCacheIntegrationTest.txt`. Đây là số lượng test theo mã mẫu, không phải kết quả đã thực thi trên máy người học.

## 7. Quan sát advisor chain khi đã có caching proxy

Trong một Spring `ApplicationContext`, lấy **bean reference thực** rồi inspect:

~~~java
Object bean = context.getBean("catalogService");

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.getTargetClass(bean));

if (bean instanceof Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
~~~

Snippet cần imports `org.springframework.aop.support.AopUtils` và `org.springframework.aop.framework.Advised`, cùng biến `context`. Trong demo, một số advisors thuộc Spring caching, một số từ `@AspectJ` infrastructure. Không kết luận mỗi class advisor xuất hiện chính xác một lần chỉ bằng tên `toString()`; Spring có thể có infrastructure advisors như `ExposeInvocationInterceptor`.

### 7.1. Phân biệt type-level và invocation-level matching

Một advisor có thể nằm trên proxy vì **một method nào đó** của target class thỏa class/method pointcut. Nhưng method `status()` không match custom `execution(* CatalogService.find(..))` nên audit không chạy nếu chỉ gọi `status()`.

Với `PointcutAdvisor`, có thể kiểm tra:

~~~java
if (advisor instanceof org.springframework.aop.PointcutAdvisor pa) {
    var pointcut = pa.getPointcut();
    boolean classMatch =
            pointcut.getClassFilter().matches(targetClass);
    boolean staticMethodMatch =
            pointcut.getMethodMatcher().matches(method, targetClass);
}
~~~

Đây là snippet trong scope đã khai báo `advisor`, `targetClass` và `java.lang.reflect.Method method`. `MethodMatcher.isRuntime()` có thể yêu cầu đối chiếu thêm arguments runtime. Static match không đảm bảo advice chạy nếu outer advisor short-circuit hoặc caller bypass proxy.

### 7.2. Nested proxy không giống nhiều advisors trên cùng proxy

~~~text
Trường hợp thông thường:
    Spring proxy
       +-- AuditAspect advisor
       +-- CacheInterceptor advisor
       +-- Target

Trường hợp có proxy lồng:
    Outer Spring proxy
       +-- AuditAspect
       +-- Inner Spring proxy
              +-- CacheInterceptor
              +-- Target
~~~

Hai cách có thể cho **stdout giống nhau** ở một số method calls nhưng khác cấu trúc lifecycle/ordering. Nếu cần xác minh trong test và biết `TargetSource` là singleton tĩnh, dùng:

~~~java
Object inner = org.springframework.aop.framework.AopProxyUtils
        .getSingletonTarget(bean);

boolean nested = inner != null && AopUtils.isAopProxy(inner);
~~~

Không đọc `getTargetSource().getTarget()` tùy tiện để đi xuyên các dynamic/scoped/pooled proxies; nó có thể kích hoạt lazy creation hoặc làm sai vòng đời target. Kết quả `getSingletonTarget()==null` **không** chứng minh không thể có nested proxy ở mọi loại TargetSource.

## 8. Nếu cơ chế Spring sẵn có là @Transactional

Một `@Transactional` bean thường có transaction advisor quanh method qua `TransactionInterceptor` khi transaction management ở proxy mode được bật. Thêm custom aspect có thể làm thay đổi phạm vi mà custom tracing/audit quan sát, nhưng **không tự động tạo transaction manager hay thay rollback policy**.

### 8.1. Audit ngoài transaction

~~~java
@Aspect
@Order(-100)
public class AuditAspect {
    @Around("execution(* demo.PaymentService.pay(..))")
    public Object log(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed();
    }
}
~~~

~~~java
@Configuration
@EnableAspectJAutoProxy
@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE)
public class PaymentConfig {
    // @Bean transactionManager, @Bean paymentService, @Bean auditAspect...
}
~~~

Đây là các **snippets minh họa**, không phải chương trình chạy độc lập: cần Spring `PlatformTransactionManager`, target `PaymentService`, các imports và beans tương ứng.

Trong trường hợp **không có outer transaction đang hoạt động**, transaction advisor ở trong custom audit có thể bắt đầu/commit/rollback khi `pjp.proceed()` đi qua. Custom audit ngoài transaction sẽ thấy thời gian bao gồm xử lý transaction interceptor, bao gồm commit/rollback, chứ không chỉ method body. Một `@Around` dùng `finally` để log sau `proceed()` có thể chạy dù transaction cuối cùng bị rollback.

### 8.2. Audit trong transaction

Nếu custom audit có order thấp ưu tiên hơn? **Không**: muốn audit **ở trong transaction**, phải để transaction advisor có **giá trị order nhỏ hơn** và custom audit có order lớn hơn:

~~~java
@EnableTransactionManagement(order = -200)
~~~

với `@Order(-100)` trên custom aspect. Khi transaction advice thật sự tạo transaction ở ngoài, audit có thể kiểm tra `TransactionSynchronizationManager.isActualTransactionActive()` và thấy transaction đang active trong `pjp.proceed()` tương ứng.

**Nhưng:** nếu caller đã có transaction, `Propagation.REQUIRED` có thể tham gia outer transaction; kiểm tra thứ tự advisor không đủ để bảo đảm **physical transaction mới** cho từng invocation. Nếu custom audit ghi database bằng transaction khác hoặc gọi API ngoài, rollback business transaction **không tự rollback** mọi side effects đó.

### 8.3. Quy tắc rollback và exception

Spring transaction mặc định rollback khi `RuntimeException` hoặc `Error` thoát khỏi transactional method; checked exceptions có thể cần `rollbackFor`. Nếu custom `@Around` bắt exception và trả thành công **trước khi lỗi đến transaction interceptor**, transaction semantics sẽ khác với trường hợp exception thoát ra. Nếu transaction interceptor nằm bên trong aspect và đã thấy exception, transaction có thể rollback trước khi outer aspect bắt nó.

Để tránh làm thay đổi nghiệp vụ vô ý, custom audit nên:

- Không swallow exception; rethrow đúng exception khi không có một recovery policy đã được thiết kế.
- Giữ nguyên method return value, kể cả `null` nếu contract cho phép.
- Không tự commit/rollback khi chỉ có chức năng logging/tracing.
- Ghi rõ audit là **attempt-level** hay **business-operation-level**.
- Nếu audit phải bền vững cùng nghiệp vụ, thiết kế transaction/outbox thích hợp thay vì tin rằng `@Around` tự bảo đảm atomicity.

## 9. Nếu cơ chế Spring sẵn có là Retry

Với Spring Retry `@Retryable`, một lời gọi vào proxy có thể làm target chạy nhiều lần. Custom audit có thể nằm **ngoài** retry advisor hoặc **bên trong** nó:

~~~text
Audit (outer) -> Retry (inner) -> Target
  1 audit invocation
  Retry: attempt 1, attempt 2, attempt 3
  3 target invocations

Retry (outer) -> Audit (inner) -> Target
  Retry: attempt 1 -> audit -> target
         attempt 2 -> audit -> target
         attempt 3 -> audit -> target
  3 audit invocations, 3 target invocations
~~~

Những con số trên giả định maxAttempts=3 và hai attempts đầu thực sự thất bại, attempt thứ ba thành công; không có advisor khác short-circuit hoặc caller retry ở lớp ngoài.

`@EnableRetry` có thuộc tính `order`. Với Spring Retry 2.x, default order được thiết kế gần `LOWEST_PRECEDENCE` nhưng ưu tiên hơn transaction interceptor mặc định, để mỗi attempt có cơ hội đi qua transactional boundary riêng. Nếu bổ sung audit, cần xác định **audit muốn log request hay từng attempt** trước khi chọn order; đừng dựa vào default precedence khi có nhiều aspects.

### 9.1. Retry + transaction + custom audit

Khi cả ba kết hợp, sơ đồ có thể là:

~~~text
Audit (per business call)
    Retry
        Transaction
            Target

- audit: 1 lần
- retry: tối đa N attempts
- transaction: mỗi attempt đi qua interceptor
- target: có thể N lần
~~~

**Cảnh báo về outer transaction:** nếu một caller bên ngoài đã mở transaction và phương thức trong dùng `Propagation.REQUIRED`, các attempts có thể **tham gia cùng outer transaction** dù transaction interceptor nằm trong retry. Một failure đánh dấu rollback-only có thể phá những attempts tiếp theo. Không tự suy ra “order đúng = mỗi attempt luôn có transaction vật lý mới”.

Nếu target gọi remote payment, gửi email hoặc publish message, retry có thể tạo **tác dụng phụ lặp**. Cần idempotency key, bounded retry, transaction outbox/compensation tùy use case. Custom audit có thể đếm attempts, nhưng **nó không làm side effect an toàn hơn** chỉ nhờ ở trong/outside proxy.

## 10. Khi một tính năng có proxy nhưng aspect lại không chạy

| Hiện tượng | Nguyên nhân khả dĩ | Cách kiểm chứng |
| --- | --- | --- |
| `AopUtils.isAopProxy=true`, custom audit không chạy | Proxy do caching/transaction tạo, còn `@Aspect` chưa được đăng ký hoặc pointcut không match | Check aspect bean, advisor chain, target class/method |
| `@Cacheable` hoạt động nhưng audit không thấy cache hits | Caching advisor nằm ngoài custom audit | Kiểm tra `@Order` vs `@EnableCaching(order=...)` |
| Audit chạy mọi request nhưng target ít hơn | Cache hit bỏ qua target | Assert cache key/hit count, target execution counter |
| Audit chạy nhiều lần hơn một request | Retry nằm ngoài audit hoặc aspect/advisor được đăng ký trùng | Phân biệt attempts, nested proxies và duplicate advisors |
| Transaction hoạt động nhưng audit gọi ngoài transaction | Audit advisor precedence cao hơn transaction advisor | Kiểm tra order, `isActualTransactionActive()` và outer transaction |
| `@Transactional` self-invocation không có boundary mới | Gọi `this.method()` bypass Spring proxy | Tách transactional worker sang bean khác |
| JDK proxy không cast được thành concrete class | Proxy triển khai interface, không subclass target | Inject qua business interface |
| Có cả two `@Enable...` nhưng không có hai lớp proxy | Spring có thể hợp nhất infrastructure; đây là hành vi bình thường | Inspect `Advised`, `AopProxyUtils.getSingletonTarget` |
| Bean được `@MockitoBean` thay thế và mất AOP | Mock replacement thường là bare mock không có target advice | Mock dependency thay vì chính bean cần test AOP |
| Custom audit ghi DB không rollback khi target lỗi | Audit có transaction/resource/lifecycle khác | Thiết kế audit transaction/outbox, test rollback thật |
| CGLIB tạo proxy nhưng method final không được advice | Method không overrideable | Kiểm tra class/method modifiers, proxy strategy |
| Bật `@EnableCaching` nhưng không có cache | Không có `CacheManager` phù hợp, annotation/method bypass proxy, context khác | Đăng ký CacheManager, kiểm tra key/proxy/bean |
| Thứ tự đổi sau nâng cấp Spring/Boot | Default order hoặc global proxy settings khác | Cố định order cần thiết và regression test behavior |

### 10.1. Không nhầm “hai @Enable” với “hai proxy”

Trong cùng Spring context, các AOP configuration processors thường phối hợp để tạo proxy có nhiều advisors thay vì luôn tạo proxy lồng. Thứ cần quan sát:

~~~java
Object bean = context.getBean("catalogService");
boolean aop = AopUtils.isAopProxy(bean);

if (bean instanceof Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor);
    }
}
~~~

Tuy nhiên danh sách advisor **không có nghĩa tất cả advisors sẽ chạy** ở mỗi method. Phải kiểm tra method matching, ordering và những advisors có thể short-circuit.

### 10.2. Pointcut custom aspect có đang match đúng target class?

Trong ví dụ, `execution(* demo.CatalogService.find(..))` match concrete target method. Nếu đổi class, package, method signature hoặc proxy mode, test phải kiểm tra lại pointcut. Spring caching annotation có thể vẫn áp dụng qua resolver, nhưng custom execution pointcut đã không match.

Một diagnostic hữu ích khi xử lý Spring bean:

~~~java
System.out.println(AopUtils.getTargetClass(bean));
~~~

Không viết pointcut dựa trên generated JDK/CGLIB proxy class name; hãy mô tả **target method contract** thực tế.

## 11. Chọn @Around, @Before hay @After cho integration

Không phải custom aspect nào cũng cần `@Around`.

- `@Before`: ghi metric hoặc kiểm tra một precondition trước khi đi vào phần tiếp theo của chain. Nếu chỉ log, thường đơn giản hơn `@Around`.
- `@AfterReturning`: ghi nhận một execution **trả về bình thường**, nhưng vị trí advisor quyết định event xảy ra trước/sau cache/transaction wrapper nào.
- `@AfterThrowing`: ghi nhận exception được ném qua join point mà advice quan sát; nếu inner retry nuốt/recover exception trước khi lỗi tới outer advice, outer `@AfterThrowing` có thể không thấy từng attempt.
- `@After`: finally-style advice, dùng cho cleanup/tracing cần chạy cả đường return/throw.
- `@Around`: đo elapsed time, trace cả before/after/error và kiểm soát `proceed()`; mạnh nhất nhưng dễ vô tình thay semantics nếu không rethrow exception hoặc không trả đúng result.

**Đặc biệt với caching**: một `@Around` ở ngoài cache đo **thời gian caller nhìn thấy**, gồm cả cache lookup; một advice ở trong cache chỉ đo phương thức được xử lý trên cache miss. Chọn điều cần đo trước khi chọn order.

**Đặc biệt với transactions**: logging sau `pjp.proceed()` ở outer aspect có thể diễn ra **sau khi inner transaction interceptor commit**; nếu custom aspect nằm trong transaction, log after của nó có thể diễn ra **trước khi outer transaction commit**. Hai vị trí không tương đương khi cần audit sự kiện bền vững.

## 12. Một bài tập mở rộng: đảo thứ tự cache advisor

Để tự kiểm chứng, giữ nguyên `AuditAspect @Order(-100)` nhưng đổi cấu hình thành:

~~~java
@EnableCaching(order = -200)
~~~

Với cùng một `CatalogService` và hai calls `find("A")`:

~~~text
Expected khi Cache OUTER / Audit INNER:

1st call:
  audit.before:A
  target:A:1
  audit.after:A

2nd call:
  (cache hit; không có audit event, không có target event)
~~~

Ở trạng thái này, cache interceptor có precedence cao hơn audit; cache hit short-circuit chain phía trong. Nhưng **không** chỉnh code ví dụ chính rồi vẫn đòi output `audit.before:A` hai lần — phải đổi expected behavior trong JUnit test.

Đây là một **biến thể thay thế** cho demo, không phải đồng thời bật hai `@EnableCaching` registrations với hai orders khác nhau trong cùng context. Nếu có nhiều Spring AOP annotations/auto-proxy configurations từ các modules, phải kiểm tra effective ordering của context thực tế.

Một test so sánh đáng viết:

~~~java
// Với audit ngoài cache: 2 caller calls -> 2 audit entries, 1 target.
assertEquals("A#1", service.find("A"));
assertEquals("A#1", service.find("A"));
assertEquals(5, events.snapshot().size());

// Với cache ngoài audit: 2 caller calls -> 1 audit around, 1 target.
// Dùng một ApplicationContext riêng và config order=-200.
// Expected events size = 3 (before, target, after).
~~~

Đây là snippet đối chiếu, không chèn vào project hoàn chỉnh ở phần 6 nếu chưa tạo alternate config; muốn test nó hãy thêm một `@Configuration` thay thế và assert event list đúng.

## 13. Test integration với transaction hoặc retry có gì khác cache?

### 13.1. Transaction không thể kiểm chứng chỉ bằng log advice

Nếu mục tiêu là custom audit integration với `@Transactional`, cần một `PlatformTransactionManager` và resource phù hợp, ví dụ H2 + `DataSourceTransactionManager`. Trong integration test, kiểm tra:

1. Bean là AOP proxy và có transaction advisor.
2. Audit xảy ra trong hay ngoài transaction theo order.
3. Một method thành công commit database row.
4. Một method ném `RuntimeException` rollback database row.
5. Audit event side effect có được rollback không nếu nó ghi cùng resource; đừng đánh đồng memory list/log với database transaction.
6. Khi có outer transaction hoặc `REQUIRES_NEW`, isolation/commit semantics được test riêng.

Một counter `before` không đủ chứng minh transaction đã commit. Nếu `@AfterReturning` chạy **trước commit** vì nằm trong transaction advisor, commit cuối cùng có thể vẫn fail.

### 13.2. Retry cần assert attempts, không chỉ một return value

Nếu dùng `@Retryable(maxAttempts=3)`:

1. Tạo hai failures có thể tái hiện rồi một success.
2. Assert `retry policy` đúng số attempts, không vô tình nhân retries ở nhiều tầng.
3. Assert custom audit invocation count là **1** hay **3** theo vị trí advisor đã chọn.
4. Assert target method attempts và external side effects độc lập.
5. Kiểm tra transaction bị rollback-only hay chưa khi retry trong same transaction.
6. Nếu có `@Recover`, kiểm tra output/recovery side effects rõ ràng.

Một external HTTP POST không được local JDBC transaction rollback. Spring AOP chỉ giúp thực thi advice theo chain, không bảo đảm exactly-once external side effects.

### 13.3. Caching test nên dùng cache tách biệt

Cache manager in-memory phù hợp để chứng minh Spring AOP ordering, nhưng một cache chung giữa test methods/classes có thể khiến first call là **hit** thay vì miss. Tạo context mới mỗi test như ví dụ, hoặc clear cache trong setup. Với Spring TestContext cache và `@SpringJUnitConfig`, reset đúng cache bean, không chỉ reset Mockito mocks.

## 14. Rủi ro khi deploy vào Spring Boot hoặc nhiều contexts

### 14.1. Auto-configuration và các advisor khác

Một ứng dụng Spring Boot có thể thêm transactions, caching, security, metrics và retry thông qua auto-configuration. Vì thế **một bean có thể có nhiều advisors hợp lệ**. Không hard-code “số advisors phải là 2” trong test production nếu hạ tầng còn thêm advisor khác.

Spring auto-proxy configurations có thể áp dụng settings mạnh hơn khi hợp nhất, ví dụ class-based proxying; thế nên `@EnableAspectJAutoProxy(proxyTargetClass=false)` ở một module không luôn là điều kiện cuối cùng nếu module khác ép true. Quan sát `AopUtils` trên context thực và bảo đảm interface/proxy strategy tương thích.

### 14.2. Parent/child ApplicationContext

`@EnableCaching` và `@EnableAspectJAutoProxy` có phạm vi theo ApplicationContext. Nếu custom aspect chỉ được đăng ký trong web child context còn service nằm ở root context, không thể mặc định advisor của child bọc root bean. Spring infrastructure thường chỉ xử lý beans được tạo trong context mà nó quản lý.

Giải pháp: đặt aspect và AOP infrastructure ở **context sở hữu service target**, hoặc định nghĩa một kiến trúc parent/child có proxy boundary rõ ràng. Khi debug, ghi cụ thể bean ID, bean factory/context và injected reference.

### 14.3. Self-invocation qua các cơ chế vẫn là self-invocation

~~~java
public class CatalogService {
    public String outer(String code) {
        return find(code); // this.find(code)
    }

    @Cacheable("books")
    public String find(String code) {
        // ...
    }
}
~~~

Đây là snippet minh họa. Nếu outer method vào proxy nhưng gọi `find` trên chính target, lời gọi nội bộ **không qua caching advisor**, cũng không qua custom aspect dành riêng cho `find`. Thêm `@EnableAspectJAutoProxy` không sửa trường hợp này. Nếu thật sự cần lời gọi được advise, ưu tiên tách `find` sang một Spring bean khác và inject proxy đó vào caller.

### 14.4. Kotlin final, method visibility và JDK/CGLIB

Một Kotlin class mặc định final vẫn có thể làm target của **JDK interface proxy** nếu có interface phù hợp. Với CGLIB cần class và methods overrideable; `kotlin-spring`/all-open plugin thường được dùng để mở Spring stereotype classes. Không đổi proxy mode vô ý khi bật caching hoặc custom aspect; các thay đổi ấy có thể làm một target final trước đây chỉ JDK-proxied trở nên không proxy được.

## 15. Bộ đối chứng giúp phân biệt custom aspect và caching advisor

Bài này đã dùng `AuditAspect @Order(-100)` nằm ngoài `CacheInterceptor`. Khi debug, phải phân biệt **một lần gọi từ client**, **một lượt vào custom aspect** và **một lượt thực thi target**. Chỉ nhìn `AopUtils.isAopProxy` không nói lên event nào đã xảy ra.

| Kịch bản qua `CatalogApi` | Caller calls | Audit before/after | Target executions | Kết luận |
| --- | ---: | ---: | ---: | --- |
| `find("A")` rồi `find("A")`; audit ngoài cache | 2 | 2 | 1 | Lần thứ hai là cache hit, audit vẫn quan sát |
| Hai lượt `find("A")`; cache ngoài audit | 2 | 1 | 1 | Cache hit short-circuit aspect ở trong |
| Hai lượt `find("A")`; tắt `@EnableCaching` | 2 | 2 | 2 | Không có cache interceptor |
| Hai lượt `find("FAIL")` theo source demo | 2 | 2 lần vào + 2 error | 2 | Thất bại không lưu thành giá trị cache thành công |
| Gọi thẳng `new CatalogService(events)` | 2 | 0 | 2 | Raw object không nhận Spring advice/caching |

**Cách thực thi đối chứng:** mỗi trường hợp tạo một `ApplicationContext` và `Events` mới; giữ nguyên target/method/pointcut, chỉ đổi một yếu tố là thứ tự advisor hoặc có bật caching hay không. Kiểm tra cả giá trị trả về, exception, số event và target execution. Không sửa expected traces cho khớp code vừa vô tình hỏng.

### Khi behavior lệch bảng

1. **Aspect mất nhưng cache vẫn hoạt động:** xác nhận `AuditAspect` được đăng ký làm Spring bean, advisor match đúng implementation method và cache có phải outer advisor không. Dùng `Advised.getAdvisors()` để xác nhận presence, rồi dùng events để xác minh execution.
2. **Cache tưởng hit nhưng target vẫn chạy:** so actual key, cache name/manager, cấu hình `@EnableCaching`, self-invocation và context instance. Không chẩn đoán chỉ từ proxy type.
3. **Audit nhân đôi ngoài dự kiến:** xác minh có nested proxy, aspect đăng ký lặp hoặc retry; đếm *logical calls*, *advice entries* và *target attempts* riêng.
4. **Thêm transaction/retry:** mô tả rõ advisor nào nằm ngoài, ai chịu trách nhiệm commit/rollback, và một retry có thể tạo mấy target attempts. Event audit thường không cùng transactional resource; nếu yêu cầu atomic audit phải thiết kế transaction/outbox phù hợp.
5. **Test thay service bằng mock:** mock raw bean có thể thay đổi proxy/advisor; giữ real service trong integration test và mock collaborator nếu muốn chứng minh AOP integration.

Khi đổi cấu hình hoặc framework version, chạy lại đối chứng trên **Spring context thật**. Thứ tự và số lượt gọi cần được chứng minh bằng dữ liệu, không suy từ tên annotation hoặc số advisor.

## 16. Checklist code review trước khi tích hợp custom aspect

- [ ] Bean đích thực sự là Spring-managed bean trong đúng ApplicationContext.
- [ ] `@Aspect` class được đăng ký thành bean.
- [ ] `@EnableAspectJAutoProxy` hoặc infrastructure tương ứng đã bật ở đúng nơi.
- [ ] Feature sẵn có (`@EnableCaching`, transaction/retry/security config) cũng được bật với manager/policy phù hợp.
- [ ] Aspect pointcut match **target method thực**, không phải tên generated proxy class.
- [ ] Caller gọi qua Spring proxy, không phải raw target hoặc self-invocation.
- [ ] Tôi đã quyết định audit/tracing đo **caller invocation**, **target execution** hay **retry attempt**.
- [ ] `@Order` trên custom aspect và order của feature advisor được chọn theo contract.
- [ ] Tôi biết cache hit có thể short-circuit target và các inner advisors.
- [ ] Nếu có transaction, tôi biết outer vs inner advice và thời điểm commit/rollback.
- [ ] Nếu có retry, tôi biết tối đa bao nhiêu attempts và side effects có idempotent không.
- [ ] Tôi không tạo thêm `ProxyFactory`/ `ProxyFactoryBean` bọc proxy có sẵn ngoài ý định.
- [ ] Tôi đã kiểm tra `AopUtils.isAopProxy` và `Advised.getAdvisors` khi cần chẩn đoán.
- [ ] Tôi không hard-code số advisors/proxy classes hoặc order không ổn định trong production test.
- [ ] Tôi có test cache hit/miss, exception, feature-disabled control và raw-object control.
- [ ] Tôi kiểm soát mutable recorder/cache state giữa JUnit tests.
- [ ] Tôi đã kiểm tra JDK/CGLIB strategy, Kotlin final và bean scope.
- [ ] Tôi có regression test trước khi nâng cấp Spring Framework/Boot hoặc đổi AOP configuration.

## 17. Kết luận

Khi tích hợp một **custom Spring aspect** với cơ chế Spring đã có proxy như caching, transaction hoặc retry, mục tiêu không phải tạo thêm proxy bằng mọi giá. Thông thường bạn để **Spring container quản lý advisors trên bean** và kiểm soát **đúng thứ tự** theo hành vi muốn quan sát.

Trong ví dụ `AuditAspect @Order(-100)` và `@EnableCaching(order=LOWEST_PRECEDENCE)`, audit nằm ngoài cache: ba lần client gọi tạo **ba audit invocations** nhưng chỉ **hai target executions** do một cache hit. Nếu đảo order để cache nằm ngoài audit, cache hit có thể bỏ qua audit. Với transaction và retry, thứ tự lại quyết định thời điểm audit quan sát transaction begin/commit hoặc retry attempts.

Hãy kiểm tra **bean → Spring proxy → advisor order → pointcut → advice → target execution**, đồng thời dùng `AopUtils`, `Advised`, JUnit và state assertions thay vì đoán theo annotations. Một aspect được tích hợp tốt cần quan sát đúng việc Spring feature làm, không làm thay đổi transaction/cache/retry semantics hoặc vô tình nhân đôi side effects.

### Tài liệu chính thức

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).
- [Spring Framework — Declaring Advice and Advice Ordering](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html).
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html).
- [Spring Framework — Declarative Annotation-based Caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html).
- [Spring Framework — Declarative Transaction Implementation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html).
- [Spring Framework — @Transactional Annotation Semantics](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html).
- [Spring Framework — Spring AOP Programmatic ProxyFactory](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html).
- [Spring Framework 6.1 — Advised Javadoc](https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/aop/framework/Advised.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-practical-workshop-010">Chuyển cấu hình giữa annotation và XML rồi chạy kiểm thử tương đương</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển cấu hình Spring AOP giữa annotation và XML rồi chạy kiểm thử tương đương

## 1. Mục tiêu thực hành và điều kiện “tương đương”

Trong Spring Framework, có thể định nghĩa advice bằng **annotation style** (`@Aspect`, `@Around`, `@EnableAspectJAutoProxy`) hoặc bằng **XML schema-based Spring AOP** (`<aop:config>`, `<aop:aspect>`, `<aop:around>`). Khi bảo trì ứng dụng cũ hoặc chuyển dần một module sang Java configuration, nhiệm vụ không chỉ là dịch vài thẻ XML: phải chứng minh **cùng một lời gọi nghiệp vụ sẽ nhận cùng hành vi quan sát được**, đồng thời không vô tình bật hai tầng proxy/advice.

Bài thực hành này xây dựng hai `ApplicationContext` độc lập cho **cùng một business class**:
- **Annotation configuration:** aspect có `@Aspect` và method có `@Around`; Spring kích hoạt bằng `@EnableAspectJAutoProxy`.
- **XML configuration:** advice là một class Java thông thường, XML dùng `<aop:config>` với `<aop:around method="around" ...>` để gắn nó vào method execution tương ứng.

Cả hai gọi cùng method `InvoiceService.submit(...)` và kiểm tra:
1. Bean được Spring AOP proxy đúng loại (class-based CGLIB trong ví dụ).
2. Advice chạy **một lần mỗi lời gọi qua proxy** trên method có pointcut khớp.
3. Business result giữ nguyên khi thành công.
4. Advice quan sát exception rồi **rethrow cùng exception**, không nuốt hoặc biến đổi lỗi.
5. Method không match pointcut **không có event**.
6. Self-invocation `this.submit(...)` **không kích hoạt lại advice của submit** trong cả hai cấu hình proxy.
7. Kết quả quan sát được và danh sách event từ annotation context bằng **chính xác** XML context.

**Phạm vi:** chỉ tập trung vào cơ chế Aspect trong **Spring AOP**. Không nghiên cứu AspectJ compile-time/load-time weaving như một sản phẩm riêng và không coi mọi đặc tính của hai kiểu cấu hình luôn tương đương. Ta xác minh tương đương **theo hợp đồng và các tình huống được test**.

## 2. Hai cách cấu hình Spring AOP khác nhau ở đâu?

| Khía cạnh | Annotation style | XML schema-based style |
| --- | --- | --- |
| Bật Spring AOP | `@EnableAspectJAutoProxy` | `<aop:config>` đăng ký hạ tầng advisor/proxy cần thiết |
| Khai báo aspect | `@Aspect` trên class được Spring đăng ký thành bean | `<aop:aspect ref="...">` tham chiếu bean chứa advice methods |
| Around advice | `@Around("execution(...)")` | `<aop:around method="around" pointcut-ref="..."/>` |
| Pointcut dùng lại | `@Pointcut` trong aspect | `<aop:pointcut id="..." expression="..."/>` |
| Tham chiếu logic advice | Java method trong class aspect | Java method trong Spring bean có `ref` |
| Chọn CGLIB | `proxyTargetClass = true` | `proxy-target-class="true"` |
| Nơi quản lý thứ tự | `@Order` / `Ordered` cùng advisor precedence | `order` trên cấu hình aspect/advisor, thứ tự advice và precedence |
| Object nhận advice | Lời gọi qua Spring AOP proxy khi pointcut match | Cũng là lời gọi qua Spring AOP proxy khi pointcut match |
| Có bắt buộc AspectJ bytecode weaver agent? | Không | Không |

**Phân biệt cực kỳ quan trọng:**
- `<aop:aspectj-autoproxy/>` là **XML switch bật hỗ trợ @AspectJ-style aspects**. Khi dùng nó, aspect vẫn khai báo bằng `@Aspect` trên Java class.
- `<aop:config>...<aop:aspect>...<aop:around>...` là **XML schema-based declaration**, cho phép class advice **không cần `@Aspect`**.
- `@EnableAspectJAutoProxy` tương tự về mục đích bật proxy cho annotation-style aspect; không phải bản dịch trực tiếp của mọi phần tử `<aop:config>`.
- Dù tên AspectJ xuất hiện trong các API Spring, **các ví dụ này đều là Spring proxy-based AOP**, không phải AspectJ LTW.

Để thực hành chuyển cấu hình thật sự, chúng ta chọn cách **XML schema-based** ở phiên bản XML thay vì chỉ chuyển annotation `@EnableAspectJAutoProxy` thành `<aop:aspectj-autoproxy/>`.

## 3. Xác định hợp đồng cần giữ trước khi chuyển

Trước khi đụng vào cấu hình, viết hợp đồng rõ ràng cho một method:

~~~text
Given InvoiceService.submit("INV-7", 2)
When gọi qua Spring AOP proxy
Then:
  return == "invoice:INV-7:2"
  events == ["before:submit",
             "after:submit:invoice:INV-7:2"]

Given InvoiceService.submit("INV-8", -1)
When gọi qua Spring AOP proxy
Then:
  throw IllegalArgumentException("units must be positive")
  events == ["before:submit",
             "error:submit:IllegalArgumentException"]

Given InvoiceService.preview("INV-7")
Then:
  return == "preview:INV-7"
  events == []

Given InvoiceService.submitInternally("INV-9", 3)
Then:
  return == "invoice:INV-9:3"
  events == []  (đường gọi this.submit bỏ qua proxy)
~~~

Đây là specification, không phải output của một chương trình. Ngoài kết quả và exception, ta quan sát chính xác **số lần advice chạy, thời điểm ghi event, phương thức được chặn**.

Ta cố tình giữ:
- **Cùng target class** và cùng method signature cho cả hai context.
- **Cùng pointcut expression** `execution(* demo.equivalent.InvoiceService.submit(..))`.
- **Cùng proxy strategy** `proxyTargetClass=true` / `proxy-target-class="true"`.
- **Cùng logic ghi event** đặt trong một helper dùng chung, để phần khác nhau chỉ là wiring annotation/XML.
- **Không có advisor bên ngoài** như transaction, method-security, cache hay retry trong demo, tránh kết quả bị ảnh hưởng bởi thứ tự advisor khác.

## 4. Kiến trúc project Java 17+ có thể chạy

Ta dùng **Spring Framework 6.1.14**, JUnit Jupiter 5.11.3, Gradle, không cần web server hoặc Spring Boot. File structure:

~~~text
build.gradle
src/main/java/demo/equivalent/
    InvoiceService.java
    AuditEvents.java
    AuditLogic.java
    AnnotatedTraceAspect.java
    XmlTraceAdvice.java
    AnnotatedAopConfig.java
    ParityMain.java
src/main/resources/
    spring-xml-aop.xml
src/test/java/demo/equivalent/
    ConfigurationParityTests.java
~~~

### 4.1. Gradle dependencies

**`build.gradle`**

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

    testImplementation platform('org.junit:junit-bom:5.11.3')
    testImplementation 'org.junit.jupiter:junit-jupiter'
}

application {
    mainClass = 'demo.equivalent.ParityMain'
}

test {
    useJUnitPlatform()
}
~~~

`spring-context` cung cấp `ApplicationContext`, `spring-aop` cung cấp proxy/advisor; `aspectjweaver` hỗ trợ annotation/pointcut parsing cho `@AspectJ` style Spring AOP và expression dùng bởi schema-based AOP. **Không cần `-javaagent`**: không dùng bytecode weaving.

Nếu đang trong Spring Boot project, nên dùng BOM/starter AOP của Boot và điều chỉnh version đồng bộ thay vì trộn module Spring Framework 6.1.14 vào một BOM khác.

### 4.2. Target bean dùng chung, không chứa AOP code

**`src/main/java/demo/equivalent/InvoiceService.java`**

~~~java
package demo.equivalent;

public class InvoiceService {

    public String submit(String invoiceId, int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("units must be positive");
        }
        return "invoice:" + invoiceId + ":" + units;
    }

    public String preview(String invoiceId) {
        return "preview:" + invoiceId;
    }

    public String submitInternally(String invoiceId, int units) {
        return this.submit(invoiceId, units);
    }
}
~~~

Lý do có ba method:
- `submit` là **method duy nhất** cần advice.
- `preview` là trường hợp âm tính: pointcut không match nên không có event.
- `submitInternally` tạo một self-invocation. Dù phần thân gọi `submit`, lệnh `this.submit` không đi qua Spring proxy, nên advice dành cho `submit` không chạy.

Target class và method ở ví dụ đều **public, non-final**, giúp CGLIB proxy chặn được `submit`, không lẫn với vấn đề final/private.

### 4.3. Recorder giúp kiểm thử độc lập console

**`src/main/java/demo/equivalent/AuditEvents.java`**

~~~java
package demo.equivalent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuditEvents {

    private final CopyOnWriteArrayList<String> events =
            new CopyOnWriteArrayList<>();

    public void add(String event) {
        events.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }
}
~~~

Recorder được tạo **riêng** trong mỗi `ApplicationContext`. Ta dùng nó để chứng minh lời gọi qua advisor chain, thay vì chỉ kiểm tra method result. `CopyOnWriteArrayList` đủ thuận tiện cho demo ghi ít; không phải giải pháp audit production với lượng ghi cao.

### 4.4. Logic advice dùng chung để cô lập khác biệt cấu hình

**`src/main/java/demo/equivalent/AuditLogic.java`**

~~~java
package demo.equivalent;

import org.aspectj.lang.ProceedingJoinPoint;

public final class AuditLogic {

    private AuditLogic() {
    }

    public static Object around(
            ProceedingJoinPoint joinPoint,
            AuditEvents events) throws Throwable {

        String method = joinPoint.getSignature().getName();
        events.add("before:" + method);

        try {
            Object result = joinPoint.proceed();
            events.add("after:" + method + ":" + result);
            return result;
        } catch (Throwable error) {
            events.add("error:" + method + ":"
                    + error.getClass().getSimpleName());
            throw error;
        }
    }
}
~~~

Method này **không phải một aspect**. Nó chứa logic quan sát mà cả annotation aspect lẫn XML advice sẽ gọi tới. Điều đó hạn chế việc hai bản cấu hình lệch nhau vì vô tình thay đổi code nghiệp vụ.

`joinPoint.proceed()` cho target chạy đúng một lần. Nếu thành công trả nguyên result; nếu ném exception, ghi event rồi **throw lại chính Throwable**. Một `@Around` bỏ `proceed()` hoặc trả sai kiểu sẽ làm thay đổi hợp đồng và phá parity test.

## 5. Cấu hình A: Annotation @Aspect + @EnableAspectJAutoProxy

**`src/main/java/demo/equivalent/AnnotatedTraceAspect.java`**

~~~java
package demo.equivalent;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class AnnotatedTraceAspect {

    private final AuditEvents events;

    public AnnotatedTraceAspect(AuditEvents events) {
        this.events = events;
    }

    @Around("execution(* demo.equivalent.InvoiceService.submit(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        return AuditLogic.around(joinPoint, events);
    }
}
~~~

Chú ý ba vị trí:
1. `@Aspect` cho Spring biết class có advice declarations.
2. `@Around` chứa **method execution pointcut** match đúng `InvoiceService.submit`.
3. Class cần được đăng ký thành Spring bean; chỉ đặt `@Aspect` trên source code nhưng không đưa nó vào context sẽ không bảo đảm auto-proxy creator phát hiện.

**`src/main/java/demo/equivalent/AnnotatedAopConfig.java`**

~~~java
package demo.equivalent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AnnotatedAopConfig {

    @Bean
    InvoiceService invoiceService() {
        return new InvoiceService();
    }

    @Bean
    AuditEvents auditEvents() {
        return new AuditEvents();
    }

    @Bean
    AnnotatedTraceAspect annotatedTraceAspect(AuditEvents events) {
        return new AnnotatedTraceAspect(events);
    }
}
~~~

`@EnableAspectJAutoProxy(proxyTargetClass=true)` khiến auto-proxy creator xem xét các Spring beans và có thể tạo **CGLIB proxy** cho `invoiceService`. Chính `@Bean annotatedTraceAspect` mới đưa `@Aspect` instance vào context.

Đây là annotation/Java configuration hoàn chỉnh; **không đồng thời import XML schema AOP vào context này** khi chạy parity test.

## 6. Cấu hình B: XML schema-based <aop:config>

### 6.1. Advice class thông thường, không dùng @Aspect

**`src/main/java/demo/equivalent/XmlTraceAdvice.java`**

~~~java
package demo.equivalent;

import org.aspectj.lang.ProceedingJoinPoint;

public class XmlTraceAdvice {

    private final AuditEvents events;

    public XmlTraceAdvice(AuditEvents events) {
        this.events = events;
    }

    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        return AuditLogic.around(joinPoint, events);
    }
}
~~~

`XmlTraceAdvice` là bean Java thông thường, **không có `@Aspect` và không có `@Around`**. Spring XML schema ánh xạ Java method `around` vào một around advisor. Nó dùng cùng `AuditLogic` để bảo đảm behavior, event names và exception path giống annotation style.

### 6.2. File Spring XML đầy đủ

**`src/main/resources/spring-xml-aop.xml`**

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:aop="http://www.springframework.org/schema/aop"
       xsi:schemaLocation="
         http://www.springframework.org/schema/beans
         https://www.springframework.org/schema/beans/spring-beans.xsd
         http://www.springframework.org/schema/aop
         https://www.springframework.org/schema/aop/spring-aop.xsd">

    <bean id="invoiceService"
          class="demo.equivalent.InvoiceService"/>

    <bean id="auditEvents"
          class="demo.equivalent.AuditEvents"/>

    <bean id="xmlTraceAdvice"
          class="demo.equivalent.XmlTraceAdvice">
        <constructor-arg ref="auditEvents"/>
    </bean>

    <aop:config proxy-target-class="true">
        <aop:aspect id="traceAspect" ref="xmlTraceAdvice">
            <aop:pointcut
                id="invoiceSubmissions"
                expression="execution(* demo.equivalent.InvoiceService.submit(..))"/>
            <aop:around
                pointcut-ref="invoiceSubmissions"
                method="around"/>
        </aop:aspect>
    </aop:config>
</beans>
~~~

Các cặp ánh xạ:
- `@Bean InvoiceService` ↔ `<bean id="invoiceService".../>`.
- `@Bean AuditEvents` ↔ `<bean id="auditEvents".../>`.
- `@Bean AnnotatedTraceAspect` ↔ `<bean id="xmlTraceAdvice".../>` (nhưng **khác class wrapper**, cùng `AuditLogic`).
- `@EnableAspectJAutoProxy(proxyTargetClass=true)` ↔ trong biến thể schema-based, `<aop:config proxy-target-class="true">` đăng ký Spring AOP infrastructure và buộc class proxy.
- `@Around("execution(...)")` ↔ `<aop:pointcut expression="execution(...)"/>` + `<aop:around ... method="around"/>`.

**Cực kỳ quan trọng:** `<aop:config>` không phải file `META-INF/aop.xml` của AspectJ load-time weaving. Nó là namespace của **Spring AOP**, điều khiển advisor/proxy trong `ApplicationContext`. Ví dụ XML này không cần javaagent.

### 6.3. Vì sao không thêm <aop:aspectj-autoproxy/> vào ví dụ XML này?

`<aop:aspectj-autoproxy/>` dành cho việc phát hiện các **`@Aspect` bean** theo style annotation trong một XML-configured context. Ta đang thực hành **khai báo aspect/advice bằng `<aop:aspect>`**; `<aop:config>` là đủ cho advisor ấy.

Bật cả `<aop:config>` và `<aop:aspectj-autoproxy/>` một cách tùy tiện có thể hợp nhất/nâng cấp auto-proxy creator, làm khó kiểm soát advisor set hoặc khiến bạn đăng ký cùng behavior hai lần. Một ứng dụng Spring lớn có thể chủ đích dùng chung các kiểu advisor, nhưng **test tương đương phải cô lập đúng hai phương án**.

## 7. Chương trình Main: chạy cả hai context và so sánh output

**`src/main/java/demo/equivalent/ParityMain.java`**

~~~java
package demo.equivalent;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ParityMain {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext annotated =
                     new AnnotationConfigApplicationContext(AnnotatedAopConfig.class);
             ClassPathXmlApplicationContext xml =
                     new ClassPathXmlApplicationContext("spring-xml-aop.xml")) {

            runScenario("annotation", annotated.getBean(InvoiceService.class),
                    annotated.getBean(AuditEvents.class));

            runScenario("xml", xml.getBean(InvoiceService.class),
                    xml.getBean(AuditEvents.class));
        }
    }

    private static void runScenario(
            String label, InvoiceService service, AuditEvents events) {

        System.out.println(label + " proxy=" + AopUtils.isAopProxy(service));
        System.out.println(label + " result=" + service.submit("INV-7", 2));
        System.out.println(label + " preview=" + service.preview("INV-7"));
        System.out.println(label + " internal="
                + service.submitInternally("INV-9", 3));

        try {
            service.submit("INV-8", -1);
        } catch (IllegalArgumentException error) {
            System.out.println(label + " caught=" + error.getMessage());
        }

        System.out.println(label + " events=" + events.snapshot());
    }
}
~~~

Lấy bean theo `InvoiceService.class` hợp lệ trong ví dụ vì **cả hai cấu hình chọn CGLIB class-based proxy**. Nếu đổi sang JDK interface proxy, cần thiết kế interface nghiệp vụ và inject/lấy bean qua interface; đừng tự động dùng concrete class type trong mọi cấu hình.

Chạy từ thư mục project với Gradle wrapper:

~~~bash
./gradlew run
~~~

PowerShell:

~~~powershell
.\gradlew.bat run
~~~

**Output từ `System.out.println` của chương trình** (không tính log Spring/Gradle):

~~~text
annotation proxy=true
annotation result=invoice:INV-7:2
annotation preview=preview:INV-7
annotation internal=invoice:INV-9:3
annotation caught=units must be positive
annotation events=[before:submit, after:submit:invoice:INV-7:2, before:submit, error:submit:IllegalArgumentException]
xml proxy=true
xml result=invoice:INV-7:2
xml preview=preview:INV-7
xml internal=invoice:INV-9:3
xml caught=units must be positive
xml events=[before:submit, after:submit:invoice:INV-7:2, before:submit, error:submit:IllegalArgumentException]
~~~

Chỉ có tên context khác nhau trong tiền tố log; **số event và thứ tự event giống nhau**. Một direct `submit` thành công có “before” rồi “after”; một direct `submit` lỗi có “before” rồi “error”. `preview` không match, còn `submitInternally` gọi `this.submit(...)` bên trong target nên không bổ sung event.

**Giới hạn quan trọng:** kết quả trên là **expected output từ source code**, không phải khẳng định test/build đã chạy trong môi trường người đọc. Khi thực hành phải chạy lại và đối chiếu trên đúng JDK/Spring versions.

## 8. JUnit Jupiter: parity test dựa trên observable behavior

Không nên so XML text với Java source text rồi kết luận hai cấu hình tương đương. Ta dựng **hai context thực**, gọi cùng kịch bản, rồi so **record dữ liệu**: loại proxy, kết quả trả về, exception class/message và sự kiện advice. Đây là **integration test nhỏ** của hạ tầng Spring AOP.

**`src/test/java/demo/equivalent/ConfigurationParityTests.java`**

~~~java
package demo.equivalent;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationParityTests {

    record Observation(
            boolean isProxy,
            boolean isCglib,
            String success,
            String preview,
            String internal,
            String errorType,
            String errorMessage,
            List<String> events) {
    }

    static Observation runCase(ConfigurableApplicationContext context) {
        InvoiceService service = context.getBean(InvoiceService.class);
        AuditEvents events = context.getBean(AuditEvents.class);
        events.clear();

        boolean isProxy = AopUtils.isAopProxy(service);
        boolean isCglib = AopUtils.isCglibProxy(service);

        String success = service.submit("INV-7", 2);
        String preview = service.preview("INV-7");
        String internal = service.submitInternally("INV-9", 3);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.submit("INV-8", -1)
        );

        return new Observation(
                isProxy,
                isCglib,
                success,
                preview,
                internal,
                error.getClass().getSimpleName(),
                error.getMessage(),
                events.snapshot()
        );
    }

    @Test
    void annotationAndXmlHaveEquivalentObservableBehavior() {
        try (AnnotationConfigApplicationContext annotated =
                     new AnnotationConfigApplicationContext(
                             AnnotatedAopConfig.class);
             ClassPathXmlApplicationContext xml =
                     new ClassPathXmlApplicationContext("spring-xml-aop.xml")) {

            Observation annotationResult = runCase(annotated);
            Observation xmlResult = runCase(xml);

            assertEquals(annotationResult, xmlResult);
            assertTrue(annotationResult.isProxy());
            assertTrue(annotationResult.isCglib());
            assertEquals("invoice:INV-7:2", annotationResult.success());
            assertEquals("preview:INV-7", annotationResult.preview());
            assertEquals("invoice:INV-9:3", annotationResult.internal());
            assertEquals("IllegalArgumentException",
                    annotationResult.errorType());
            assertEquals("units must be positive",
                    annotationResult.errorMessage());

            assertEquals(List.of(
                    "before:submit",
                    "after:submit:invoice:INV-7:2",
                    "before:submit",
                    "error:submit:IllegalArgumentException"
            ), annotationResult.events());
        }
    }

    @Test
    void xmlAdviceIsPlainClassNotAnnotatedAspect() {
        assertNull(XmlTraceAdvice.class.getAnnotation(
                org.aspectj.lang.annotation.Aspect.class));
        assertNotNull(AnnotatedTraceAspect.class.getAnnotation(
                org.aspectj.lang.annotation.Aspect.class));
    }

    @Test
    void rawObjectIsNotProxiedInEitherSpringProxyModel() {
        InvoiceService raw = new InvoiceService();
        assertFalse(AopUtils.isAopProxy(raw));
        assertEquals("invoice:RAW-1:1", raw.submit("RAW-1", 1));
    }
}
~~~

### 8.1. Assertion quan trọng nhất là gì?

`assertEquals(annotationResult, xmlResult)` dựa trên Java record equality, so tất cả fields:
- `isProxy` và `isCglib` giúp xác nhận **hai cấu hình cùng dùng Spring AOP CGLIB proxy**.
- `success` và `preview` chứng minh method output không đổi.
- `internal` giữ kỳ vọng self-invocation trong hai phương án.
- `errorType`, `errorMessage` chứng minh exception contract không đổi.
- `events` chứng minh **advice thực sự chạy**, số lần và thứ tự tương ứng.

Quan trọng: chỉ so `isProxy=true` chưa đủ. Một bean có thể được proxy bởi transaction advisor mà **không hề nhận đúng custom around advice**. Vì vậy, **events** là bằng chứng bắt buộc để phát hiện mất/dư/nhân đôi advice.

### 8.2. Chạy bài test

~~~bash
./gradlew test --tests demo.equivalent.ConfigurationParityTests
~~~

PowerShell:

~~~powershell
.\gradlew.bat test --tests demo.equivalent.ConfigurationParityTests
~~~

Với code/config đúng và các dependencies có thể tải, dự kiến JUnit chạy **3 test** không lỗi; đây là kết quả dự kiến từ thiết kế assertions, không phải log test đã được thực thi trong bài viết. Nếu một test fail, đọc root cause và các differences trước khi đổi cấu hình.

**Không tái sử dụng singleton recorder giữa hai context**: mỗi context có một bean `AuditEvents` riêng. Nếu test chia sẻ cùng recorder hoặc context caching mà không reset, dữ liệu có thể bị trộn và tạo kết quả sai.

## 9. Chuyển @Before / @AfterReturning / @AfterThrowing sang XML

Bài runnable dùng `@Around` để quan sát cả success và exception với một advice. Khi chuyển code thật, các loại advice khác cũng có XML equivalents:

| Annotation | Spring AOP XML schema | Điều cần giữ |
| --- | --- | --- |
| `@Before("pointcut")` | `<aop:before pointcut-ref="..." method="before"/>` | Thực thi trước join point |
| `@AfterReturning(pointcut=..., returning="result")` | `<aop:after-returning ... method="afterSuccess" returning="result"/>` | Tham số trả về phải đúng tên/type binding |
| `@AfterThrowing(pointcut=..., throwing="ex")` | `<aop:after-throwing ... method="afterFailure" throwing="ex"/>` | Bind đúng throwable type/name |
| `@After("pointcut")` | `<aop:after ... method="afterFinally"/>` | Finally semantics, không phải success |
| `@Around("pointcut")` | `<aop:around ... method="around"/>` | `ProceedingJoinPoint` và `proceed()` |
| `@Pointcut("expression")` | `<aop:pointcut id="..." expression="..."/>` | Giữ biểu thức và tập join point match |

Ví dụ **chỉ là XML fragment**, phải đặt trong `<aop:aspect ref="...">` của một context có bean advice phù hợp:

~~~xml
<aop:before pointcut-ref="invoiceSubmissions"
            method="before"/>
<aop:after-returning pointcut-ref="invoiceSubmissions"
                     method="afterSuccess" returning="result"/>
<aop:after-throwing pointcut-ref="invoiceSubmissions"
                    method="afterFailure" throwing="ex"/>
~~~

Nếu dùng fragment này, phải tạo các Java methods `before`/`afterSuccess`/`afterFailure` đúng signature và binding. **Không ghép đồng thời fragment này với `<aop:around>` của demo rồi mong chỉ có một advice**: bạn đã thêm nhiều advice, nên event/ordering cần test lại.

Đối với returning/throwing binding, đảm bảo tên thuộc tính XML khớp với tên argument Java theo cơ chế discovery/metadata tương thích. Nếu version/build không giữ parameter names và cấu hình khó suy luận, cần kiểm tra hướng binding trong schema-based Spring AOP, tránh dựa vào tên không tồn tại.

## 10. Chuyển Java pointcut sang XML: escape và cú pháp

### 10.1. Biểu thức pointcut không đổi về ý nghĩa

Annotation:

~~~java
@Around("execution(* demo.equivalent.InvoiceService.submit(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    // ...
    return pjp.proceed();
}
~~~

XML:

~~~xml
<aop:pointcut id="invoiceSubmissions"
    expression="execution(* demo.equivalent.InvoiceService.submit(..))"/>
<aop:around pointcut-ref="invoiceSubmissions" method="around"/>
~~~

Nếu method execution expression được giữ nguyên, adviser sẽ match cùng loại join point **trong điều kiện những context có bean target tương đương và proxy setup tương đương**.

### 10.2. Dấu &, <, > phải tuân theo quy tắc XML

Trong annotation pointcut, ta có thể nối bằng `&&`:

~~~java
@Around("execution(* demo.equivalent..*(..)) && @annotation(tracked)")
~~~

Trong XML attribute, ký tự `&` phải được escape. Có thể dùng `and` của AspectJ pointcut language cho điều kiện logic (đơn giản hơn với XML), ví dụ **fragment minh họa**:

~~~xml
<aop:pointcut id="annotatedMethods"
    expression="execution(* demo.equivalent..*(..)) and @annotation(demo.equivalent.TrackedAction)"/>
~~~

Nếu dự án dùng XML entity escaping, `&amp;&amp;` trong XML source tương ứng `&&` sau parse. Không copy raw `&&` vào attribute XML vì XML parser sẽ gặp lỗi.

Với `<` hoặc `>` trong nội dung attribute, cần dùng `&lt;` hoặc `&gt;` khi có liên quan. Expression của demo không chứa ký tự đặc biệt này nên giữ nguyên.

### 10.3. Không dùng “đổi cú pháp” để đổi cả phạm vi

Ví dụ pointcut Java chỉ match `submit`, nhưng XML mới dùng `execution(* demo.equivalent..*(..))` thì **không phải cấu hình tương đương**: nó còn có thể chặn `preview`, `submitInternally` hoặc nhiều method ở bean khác. Trước khi chuyển, ghi lại và test:
- Type/package chính xác.
- Method name và overloads.
- Parameter list.
- Annotation bindings.
- Modifier và visibility khi dùng class-based proxy.
- Self-invocation semantics (Spring AOP proxy).

## 11. @EnableAspectJAutoProxy, <aop:aspectj-autoproxy/> và <aop:config>

Ba thành phần này thường gây nhầm lẫn trong khi migration:

### Trường hợp A — giữ @Aspect Java, chuyển chỉ phần enabling sang XML

Nếu annotation aspect vẫn có `@Aspect`, có thể dùng XML:

~~~xml
<aop:aspectj-autoproxy proxy-target-class="true"/>
<bean id="annotatedTraceAspect"
      class="demo.equivalent.AnnotatedTraceAspect">
    <constructor-arg ref="auditEvents"/>
</bean>
~~~

Đây là **fragment cần đặt trong Spring XML có namespace `aop` và beans phù hợp**. Nó cho phép Spring phát hiện advisor trên `@Aspect` bean. Hành vi có thể tương tự `@EnableAspectJAutoProxy` nếu bean graph/proxy options tương đương.

### Trường hợp B — chuyển luôn khai báo aspect/advice sang XML

Dùng `XmlTraceAdvice` thông thường và `<aop:config><aop:aspect>...` như mục 6. Đây là **chuyển đổi toàn bộ định nghĩa advice** mà bài parity test đang kiểm tra.

**Không nhầm hai trường hợp:** nếu requirement là xóa `@Aspect`/`@Around` khỏi class, chỉ thêm `<aop:aspectj-autoproxy/>` **không thay thế khai báo advice**. Ngược lại, nếu chỉ đổi cách bật AOP nhưng giữ annotation aspect, không cần sửa logic advice hay định nghĩa `<aop:around>` mới.

### Trường hợp C — hỗn hợp

Spring có thể hợp nhất auto-proxy settings từ các cách cấu hình khác nhau trong cùng context (ví dụ `@EnableAspectJAutoProxy`, `<aop:aspectj-autoproxy/>`, `<aop:config>`). Một option proxy mạnh hơn có thể chi phối toàn context, và advisor có thể bị đăng ký trùng nếu cấu hình không được kiểm soát.

**Trong test migration, tốt nhất có một cấu hình AOP hiệu lực tại một thời điểm** hoặc ít nhất chứng minh advisor set bằng inspection. Việc bật trùng không đồng nghĩa mọi advice luôn chạy đúng một lần.

## 12. Advice order và một số trường hợp không tương đương tự nhiên

Một test chỉ có **một around advice** không thể phát hiện mọi khác biệt. Khi ứng dụng có nhiều aspect/advisor, cần thêm test:

- **Thứ tự outer/inner** khi có transaction, retry, metrics và audit. `@Order`/`Ordered` trong Java config và `order`/advisor ordering XML phải được kiểm chứng bằng event nesting.
- **Returning/throwing bindings** nếu schema-based advice nhận `result` hoặc `Throwable`.
- **JDK vs CGLIB** vì caller có thể lấy bean theo interface hoặc concrete class khác nhau; kiểm tra `AopUtils.isJdkDynamicProxy`/`isCglibProxy`.
- **Bean scope:** singleton, prototype hoặc request scope có thể dẫn đến số proxy/target instance khác nhau.
- **Bean qualification:** tên bean/context ownership nếu XML bean `id` khác Java `@Bean` name.
- **Pointcut có tên bean:** Spring hỗ trợ `bean(...)` designator; đổi bean name làm thay đổi matching.
- **Annotation bindings:** `@annotation(tracked)`/`args(...)` cần đối chiếu names, parameter discovery và method annotation resolution.
- **Async/reactive:** advice quanh method trả `Mono`/`Flux` thường chạy ở method invocation; subscription/terminal semantics cần test riêng.
- **Exceptions:** advice `@AfterReturning` không quan sát exception; `@AfterThrowing` không quan sát return bình thường, và `@Around` có thể thay đổi behavior nếu catch sai.
- **Introductions:** Spring XML `<aop:declare-parents>` và `@DeclareParents` cần thiết kế implementation/state/type filters riêng; không tự suy ra parity từ một phép gọi around.

Kết luận đúng của ví dụ là **hai configuration được viết cho cùng hợp đồng đã kiểm thử**, không phải tuyên bố mọi annotation AOP configuration có thể dịch 1:1 sang XML mà không đổi tác động.

## 13. Debug khi chỉ một cấu hình được advice

| Triệu chứng | Nguyên nhân khả dĩ | Hướng kiểm tra |
| --- | --- | --- |
| Annotation context không có proxy | Quên `@EnableAspectJAutoProxy` hoặc `@Aspect` chưa thành Spring bean | Inspect configuration/bean registrations |
| XML context không có proxy | Thiếu namespace/schema hoặc `<aop:config>`, `ref` sai | Xem lỗi parse XML và context startup |
| Cả hai proxy nhưng event khác nhau | Pointcut mismatch, advice/order/binding khác | So actual advisors, expression, recorder, Java advice wrapper |
| XML ghi event hai lần | Đăng ký lặp advice bằng `<aop:config>` và annotation-style auto-proxy | Xem mỗi advisor/advice được đăng ký bao nhiêu lần |
| `getBean(InvoiceService.class)` fail chỉ ở một context | Một phía dùng JDK proxy thay vì CGLIB | So `proxyTargetClass`/`proxy-target-class` và injection type |
| `preview` bị advise trong XML | Pointcut XML rộng hơn | Chép chính xác biểu thức và test negative control |
| Self-invocation được advise trong một phương án | Có thêm AspectJ weaving hoặc proxy invocation đặc biệt | Kiểm tra agent/mode và call path |
| Exception bị nuốt | XML advice `around` không rethrow hoặc Java advice catch sai | Test error class/message và events |
| Bean tồn tại nhưng advisor không match | Bean khác owner/context, pointcut target class sai | `AopUtils.getTargetClass`, `Advised.getAdvisors` |
| XML parse error khi pointcut có `&&` | Thiếu escape entity XML | Dùng `and` hoặc `&amp;&amp;` trong attribute |
| Advice chạy lúc startup nhưng không ở runtime | Bean bị tạo sớm hoặc caller nắm raw reference | Kiểm tra BeanPostProcessor và bean từ context |
| Test khác production | Cấu hình import/profiles/component scanning khác | Tạo context test cùng ownership, kiểm advisor thực tế |

Một helper **chẩn đoán** (snippet trong nơi đã có biến `bean`):

~~~java
System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
System.out.println(AopUtils.getTargetClass(bean).getName());

if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getSimpleName());
    }
}
~~~

Đây là snippet, cần import `org.springframework.aop.support.AopUtils`. **Proxy tồn tại không bảo đảm đúng custom advisor đã match**: kiểm event/điểm cắt cùng advisor listing.

## 14. Quy trình migration được khuyến nghị

1. **Chốt baseline hành vi của cấu hình đang chạy**: service method, return/throw, advisor chain, transaction boundary, proxy type, interception count.
2. **Viết integration tests trước khi đổi XML/annotation**. Test thành công, lỗi, method không match, self-invocation và các overload/annotations liên quan.
3. **Tách business code ra khỏi AOP wiring**. Không thay target nghiệp vụ cùng lúc chuyển cấu hình nếu không cần.
4. **Cô lập advice behavior** hoặc giữ cùng implementation class khi hợp đồng cho phép; mục tiêu là chỉ thay wiring được kiểm soát.
5. **Chuyển chính xác pointcut/ordering/proxy options**. Chú ý XML escaping, bean ids/refs, JDK/CGLIB.
6. **Tạo ApplicationContext của phương án mới** và kiểm tra startup errors, actual proxy, actual advisor list.
7. **Chạy cùng scenario với cả hai configurations**; so observable outcomes như `Observation` test.
8. **Thêm test cho tính năng thực tế**: `@Transactional` propagation, `@Cacheable`, Spring Security/method authorization nếu có.
9. **Tránh chạy đồng thời hai advice trùng nhiệm vụ** trong deployment khi chưa có chiến lược loại bỏ bản cũ.
10. **Thay cấu hình một cách có kiểm soát** rồi chạy test suite/end-to-end, quan sát latency và logs; giữ rollback plan.
11. **Ghi lại mode được chọn** và lý do dự án còn dùng XML (legacy integration, package không được sửa, cấu hình tập trung...) hoặc Java configuration (type safety, IDE navigation).
12. **Cập nhật tài liệu**: nêu nguồn pointcut, nơi khai báo aspect, bean ownership, proxy option và parity tests là tiêu chí nghiệm thu.

Đừng chuyển bằng thao tác “copy cả XML sang Java rồi bỏ tất cả test”: một behavior audit/security sai có thể không làm method result thay đổi, nhưng lại bỏ mất interception rất quan trọng.

## 15. Bài tập mở rộng

### Bài tập A — chuyển một @Before thành XML

Từ annotation:

~~~java
@Before("execution(* demo.equivalent.InvoiceService.submit(..))")
public void beforeSubmit() {
    // ...
}
~~~

Viết `<aop:before>` trong XML tham chiếu đúng Java advice method. **Không chạy đồng thời nó với `<aop:around>` demo nếu chưa cập nhật expected events**. Viết test cho số lần advice bằng recorder, không chỉ return value.

### Bài tập B — làm test phát hiện pointcut XML sai

Cố tình đổi `expression` XML để match thêm `preview`. Test `annotationAndXmlHaveEquivalentObservableBehavior` phải fail vì `events` khác nhau. Đây là **kiểm tra chất lượng bài test**, không phải thay đổi nên giữ trong production.

### Bài tập C — thay CGLIB bằng interface-based JDK proxy

Định nghĩa `InvoiceOperations`, cho `InvoiceService implements InvoiceOperations` và để cả hai config dùng interface proxy. Đổi caller sang `InvoiceOperations`. Xác nhận `AopUtils.isJdkDynamicProxy` trong cả hai context, rồi chạy lại parity tests. Nếu chỉ thay một context, test `isCglib` hoặc lookup bean theo class phải phát hiện mismatch.

### Bài tập D — kiểm tra ordering

Thêm một advisor thứ hai có events `outer.before`/`outer.after`; chọn precedence rõ ràng trong cả Java và XML. So thứ tự nesting. Nếu test chỉ so outcome thành công, lỗi về thứ tự có thể bị bỏ sót.

### Bài tập E — method trả Mono

Thay `submit` thành method trả `Mono<String>` và chọn điểm quan sát đúng: method invocation hay subscription/terminal. So không chỉ method return mà cả `doOnError`, `doFinally` khi subscribe. Không chèn `block()` trong Spring WebFlux advice.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** `@EnableAspectJAutoProxy` tương ứng với `<aop:config>` hay `<aop:aspectj-autoproxy/>`?

Nếu giữ `@Aspect` annotation-style, XML switch gần về vai trò là `<aop:aspectj-autoproxy/>`. Nếu chuyển luôn khai báo advice sang XML, dùng `<aop:config>` và `<aop:aspect>`. Hai cách đều dùng Spring AOP proxy, nhưng không phải cùng kiểu định nghĩa.

**Câu 2.** Vì sao XML advice class `XmlTraceAdvice` không có `@Aspect` vẫn chạy?

Vì Spring XML schema gắn method `around` vào advisor qua `<aop:aspect ref="xmlTraceAdvice">` / `<aop:around ...>`; Spring tạo proxy theo advisor đó.

**Câu 3.** Có thể kết luận hai cấu hình tương đương chỉ vì trả cùng string không?

Không. Một bên có thể không chạy audit advice, hoặc chạy hai lần, nhưng target vẫn trả cùng string. Cần so event, exception và proxy metadata.

**Câu 4.** `preview` không được advise vì sao?

Vì pointcut chỉ match execution của `InvoiceService.submit(..)`. Trong parity test, negative control bảo đảm không vô tình mở rộng scope.

**Câu 5.** Vì sao XML pointcut có `&&` dễ parse fail?

XML attribute yêu cầu escape `&` thành `&amp;`. Có thể dùng `and` của pointcut language để tránh viết escaped ampersand.

**Câu 6.** Nếu hai context đều tạo CGLIB proxy, có chứng minh advisor chain giống nhau không?

Chưa. Proxy strategy giống nhau chỉ xác nhận kiểu proxy. Phải kiểm advisor list và behavior.

**Câu 7.** Vì sao một self-invocation `this.submit(...)` không vào advice của cả annotation lẫn XML?

Vì cả hai đều là **Spring proxy-based AOP**, và lời gọi nội bộ trên target không đi qua proxy.

**Câu 8.** Có nên cùng lúc dùng `@Aspect` bean và XML `<aop:around>` cho cùng logic audit không?

Không nếu mục tiêu chỉ là chuyển đổi 1:1: có thể tạo advice trùng, khác thứ tự và tăng chi phí. Cần cô lập/tắt một phương án và xác minh.

**Câu 9.** Parity test phát hiện exception bị nuốt bằng cách nào?

`assertThrows(...)` yêu cầu exception tiếp tục đến caller, sau đó so class/message cùng event `error:submit:IllegalArgumentException` giữa hai context.

**Câu 10.** XML `<aop:config>` khác `META-INF/aop.xml` như thế nào?

`<aop:config>` là cấu hình Spring AOP proxy trong Spring context. `META-INF/aop.xml` là cấu hình AspectJ weaver; chúng phục vụ hai cơ chế và không thể thay tên file cho nhau.

## 17. Tài liệu tham khảo

- [Spring Framework — Schema-based AOP Support](https://docs.spring.io/spring-framework/reference/core/aop/schema.html): `<aop:config>`, `<aop:aspect>`, `<aop:around>` và schema-based advice.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): `@Aspect`, `@EnableAspectJAutoProxy` và phát hiện aspect bean.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): method interception, `ProceedingJoinPoint.proceed()` và advice lifecycle.
- [Spring Framework — AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK vs CGLIB và self-invocation.
- [Spring Framework — XML Schema Configuration](https://docs.spring.io/spring-framework/reference/core/appendix/xsd-schemas.html): XML schema location và namespace.
- [JUnit Jupiter User Guide](https://junit.org/junit5/docs/current/user-guide/): assertions, test lifecycle và integration tests.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-practical-workshop-011">Thử LTW trong Spring khi thực sự cần vượt giới hạn proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Thử LTW trong Spring khi thực sự cần vượt giới hạn proxy

## 1. Bối cảnh và mục tiêu

Spring AOP thông thường dựa trên **proxy**: Spring quản lý target bean, tạo proxy và áp dụng advisor khi client gọi method **qua proxy**. Với đa số business services, đó là lựa chọn dễ cấu hình, kiểm thử và bảo trì. Nhưng `this.inner()`, đối tượng tạo bằng `new` bên ngoài IoC và một số method không thể được proxy intercept là những ranh giới mà Spring AOP proxy mode không xử lý theo mong đợi.

**Load-time weaving (LTW)** cho phép AspectJ thay đổi bytecode khi class được JVM tải vào. Nhờ vậy một AspectJ aspect có thể áp dụng cho execution của method đã woven, kể cả khi lời gọi đi từ method khác trong cùng instance. Spring có hạ tầng `LoadTimeWeaver` và các AspectJ aspects tích hợp Spring trong `spring-aspects` để phục vụ nhu cầu vượt giới hạn proxy.

Bài này tập trung vào **Spring Aspect**, không trình bày toàn bộ ngôn ngữ AspectJ. Ta sẽ:

1. Xác định rõ lỗi self-invocation trong Spring proxy mode.
2. Dựng **hai phép chạy có đối chứng**: Spring auto-proxy và AspectJ LTW.
3. Viết project Java 17 + Maven, cùng service và cùng pointcut, nhưng khác runtime mechanism.
4. Xem stdout, `AopUtils.isAopProxy(...)`, `META-INF/aop.xml`, agent logs và `weaveinfo`.
5. Hiểu `@EnableLoadTimeWeaving`/`spring-instrument` và `spring-aspects` transaction/`@Configurable` integration.
6. Quyết định khi nào cần LTW, khi nào nên giữ Spring proxy hoặc tách bean.

**Lưu ý:** dùng `@Aspect` trong Spring **không có nghĩa** code đang được AspectJ weave. Spring đọc @AspectJ declaration style để tạo proxy, còn bytecode weaving là hoạt động khác.

## 2. Vì sao Spring AOP không thấy self-invocation?

Giả sử service:

```java
public class Work {
    public void outer() {
        System.out.println("outer");
        this.inner();
    }

    public void inner() {
        System.out.println("inner");
    }
}
```

Một custom Spring `@Before("execution(* demo.Work.inner(..))")` có thể chặn `work.inner()` khi client gọi Spring bean proxy. Nhưng `work.outer()` chạy qua proxy, rồi `this.inner()` được gọi **trực tiếp trong target**, không quay lại proxy lần nữa.

```text
PROXY MODE
caller -> proxy.outer() -> target.outer()
                           -> this.inner() -> target.inner()
                              [không có proxy.inner()]
caller -> proxy.inner() -> advice -> target.inner()

LTW MODE (khi method đã woven)
caller -> Work.outer() -> this.inner()
                         -> woven advice -> body inner()
caller -> Work.inner() -> woven advice -> body inner()
```

Đổi `@Before` thành `@Around`, bật `proxyTargetClass=true` hoặc tăng precedence của `@Order` **không giải quyết** self-invocation. Dù proxy dựa trên JDK dynamic proxy hay CGLIB, target gọi `this.method()` sẽ bypass proxy advice cho method đó.

Với `@Transactional`, có thể outer đã mở transaction và inner vẫn chạy trong transaction ấy, nhưng annotation riêng trên inner như `REQUIRES_NEW` **không được intercept lại** qua `this.inner()`. Với `@Cacheable` và `@Async` cũng có nguy cơ tương tự nếu dùng proxy mode.

## 3. Khi nào LTW là lựa chọn hợp lý?

| Yêu cầu | Ưu tiên cách Spring đơn giản hơn | Xem xét LTW khi |
| --- | --- | --- |
| Advice cho self-invocation | Tách method sang Spring bean khác, inject bean | Không thể tổ chức lại đường gọi và interception là bắt buộc |
| Transaction propagation trên internal method | Tạo transaction boundary rõ giữa các beans | Cần `AdviceMode.ASPECTJ` và weaving thật |
| Cache method được gọi nội bộ | Tách cacheable method thành bean riêng | Cache AspectJ mode thực sự phù hợp kiến trúc |
| Inject dependency vào object do `new`/ORM tạo | Constructor injection hoặc Spring factory | Object creation do thư viện khác kiểm soát, `@Configurable` có giá trị |
| Logging/metrics | Spring AOP hoặc observability infrastructure | Cần method execution không đi qua Spring bean proxy |
| Tối ưu hiệu năng | Profiling, benchmark và loại I/O không cần thiết | Không chọn LTW chỉ với giả định weaving nhanh hơn |

**Không dùng LTW làm mặc định** chỉ vì custom `@Aspect` đã có. LTW đòi hỏi runtime instrumentation, packaging aop.xml, class loading đúng thời điểm, cùng integration tests trong JVM/app server gần production.

## 4. Các thành phần trong hệ sinh thái Spring

| Thành phần | Chức năng |
| --- | --- |
| `spring-aop` | Infrastructure proxy, advisor, interceptor của Spring |
| `@EnableAspectJAutoProxy` | Spring tạo proxies từ @AspectJ-style Spring aspect beans |
| `aspectjweaver.jar` | AspectJ transformer/Java agent và runtime support |
| `META-INF/aop.xml` | LTW aspects và filter những target types cần weave |
| `@EnableLoadTimeWeaving` | Spring cấu hình `LoadTimeWeaver` cho ApplicationContext/ClassLoader thích hợp |
| `spring-instrument.jar` | Java instrumentation agent dùng bởi một số Spring-managed LTW setups |
| `spring-aspects.jar` | AspectJ aspects do Spring cung cấp (transaction, bean configurer, cache, async,...) |
| `AdviceMode.ASPECTJ` | Lựa chọn AspectJ advice cho một số tính năng Spring, **không tự bật weaver** |

Trong ví dụ runnable, dùng `aspectjweaver` như JVM agent **trực tiếp** để việc so sánh tái lập rõ ràng. Phần sau giải thích cách chọn Spring-managed LTW và Spring aspects khi cần.

## 5. Project Java 17: cấu trúc và Maven

Ví dụ dùng Java 17+, Maven, Spring Framework `6.2.19` và AspectJ Weaver `1.9.24`. Cùng một class `Work` và `TraceAspect` chạy ở hai mode:

- **PROXY:** Spring tạo `TraceAspect` bean, bật `@EnableAspectJAutoProxy`; không có JVM agent.
- **LTW:** Spring chỉ tạo `Work` bean; AspectJ agent đọc `aop.xml` và weave `Work.inner()`, không đăng ký Spring auto-proxy.

```text
spring-ltw-demo/
  pom.xml
  src/main/java/demo/
    App.java
    Work.java
    TraceAspect.java
  src/main/resources/META-INF/
    aop.xml
```

`pom.xml` hoàn chỉnh:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-ltw-demo</artifactId>
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
        <artifactId>maven-dependency-plugin</artifactId>
        <version>3.7.1</version>
        <configuration>
          <artifactItems>
            <artifactItem>
              <groupId>org.aspectj</groupId>
              <artifactId>aspectjweaver</artifactId>
              <version>1.9.24</version>
              <outputDirectory>${project.build.directory}</outputDirectory>
              <destFileName>aspectjweaver.jar</destFileName>
            </artifactItem>
          </artifactItems>
        </configuration>
      </plugin>

      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.5.1</version>
        <configuration>
          <executable>java</executable>
          <arguments>
            <argument>-javaagent:${project.build.directory}/aspectjweaver.jar</argument>
            <argument>-Ddemo.ltw=true</argument>
            <argument>-cp</argument>
            <classpath/>
            <argument>demo.App</argument>
          </arguments>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

`maven-dependency-plugin` tạo `target/aspectjweaver.jar`. `exec:exec` mở **JVM con có agent từ đầu startup**; `exec:java` dùng Maven JVM hiện hữu, không phải phép thử đúng để chứng minh cấu hình `-javaagent` từ `exec:exec`.

`aspectjweaver` là dependency cần để Spring hiểu @AspectJ-style pointcuts, nhưng Maven dependency **không tự kích hoạt LTW** khi chạy PROXY mode.

## 6. Work.java: method được chọn để weave

`src/main/java/demo/Work.java`:

```java
package demo;

public class Work {
    public void outer() {
        System.out.println("outer");
        this.inner();
    }

    public void inner() {
        System.out.println("inner");
    }
}
```

Ta cố ý dùng class và methods public, không `final` để loại bỏ vấn đề proxyability khác. Không có interface; Spring sẽ dùng class-based proxy cho `Work` trong PROXY mode. `outer()` không có advice riêng, còn `inner()` sẽ được advice chọn theo `execution`.

## 7. TraceAspect.java: một @Aspect, hai runtime mechanisms

`src/main/java/demo/TraceAspect.java`:

```java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    @Before("execution(* demo.Work.inner(..))")
    public void beforeInner() {
        System.out.println("trace-before-inner");
    }
}
```

Class không có `@Component` vì PROXY mode sẽ đăng ký rõ `@Bean TraceAspect`, còn LTW mode dùng AspectJ `aop.xml` để register aspect.

Điều nên nhớ:

- Spring AOP tạo advisor dựa trên method `beforeInner` của **Spring aspect bean**; advice chỉ chạy khi client vào `inner()` qua proxy.
- AspectJ LTW quản lý aspect instance theo AspectJ model và weave bytecode của `Work.inner()`; không cần Spring tạo proxy của Work.
- Không đăng ký cả hai cơ chế cho cùng invocation nếu mục tiêu là phép thử đối chứng rõ ràng; nếu đồng thời dùng proxy + weaving, advice có thể chạy **trùng**.

`@Before` đủ để chứng minh interception; không cần đặt I/O hoặc business logic trong `@Around` của thử nghiệm nhỏ này.

## 8. App.java: chọn PROXY hoặc LTW, không chọn cả hai

`src/main/java/demo/App.java`:

```java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class App {

    @Configuration(proxyBeanMethods = false)
    public static class BaseConfig {
        @Bean
        Work work() {
            return new Work();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class ProxyConfig {
        @Bean
        TraceAspect traceAspect() {
            return new TraceAspect();
        }
    }

    public static void main(String[] args) {
        boolean ltw = Boolean.getBoolean("demo.ltw");

        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(BaseConfig.class);
            if (!ltw) {
                context.register(ProxyConfig.class);
            }
            context.refresh();

            Work work = context.getBean(Work.class);
            System.out.println("mode=" + (ltw ? "LTW" : "PROXY"));
            System.out.println("springProxy=" + AopUtils.isAopProxy(work));

            work.outer();
            work.inner();
        }
    }
}
```

`BaseConfig` luôn tạo một Spring `Work` bean. Chỉ khi `demo.ltw=false` thì Spring bật `@EnableAspectJAutoProxy` và tạo `TraceAspect` bean.

`Boolean.getBoolean("demo.ltw")` đọc **JVM system property**, không đọc program args. LTW run cần `-Ddemo.ltw=true` trong JVM con, như `pom.xml` đã quy định.

## 9. META-INF/aop.xml và phạm vi weaving

`src/main/resources/META-INF/aop.xml`:

```xml
<aspectj>
  <weaver options="-verbose -showWeaveInfo">
    <include within="demo.Work"/>
  </weaver>
  <aspects>
    <aspect name="demo.TraceAspect"/>
  </aspects>
</aspectj>
```

`aop.xml` cho AspectJ LTW biết **aspect nào tham gia** và giới hạn bytecode transformation ở type `demo.Work`. `-verbose` cho log kiểm chứng configuration/aspect được nhận; `-showWeaveInfo` cho log về join point mà aspect advise.

Đây là **AspectJ configuration**, không phải bean definition của Spring, và Spring AOP proxy mode không cần dùng `aop.xml`. Do đó một source tree có thể chứa `aop.xml` nhưng chỉ JVM có AspectJ transformer mới thực sự weave các target class.

Không đặt `<include within="*"/>` trên production không kiểm soát: quá rộng có thể gây startup overhead, classloader warnings, và tác động ngoài package ứng dụng.

## 10. Chạy PROXY baseline và giải thích output

Chạy từ thư mục `spring-ltw-demo` với JDK 17+ và Maven, **không gắn JVM AspectJ agent**:

```bash
mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.5.1:java -Dexec.mainClass=demo.App
```

Những dòng `System.out` do chương trình in **dự kiến**:

```text
mode=PROXY
springProxy=true
outer
inner
trace-before-inner
inner
```

Diễn giải:

1. `springProxy=true`: auto-proxy infrastructure đã wrap bean `Work` do method `inner` match.
2. Client gọi `work.outer()` qua proxy nhưng advice chỉ match `inner`, nên proxy chuyển tiếp tới `outer`.
3. Bên trong target, `this.inner()` chạy thẳng body `inner`, in `inner` lần thứ nhất **không có trace**.
4. Client gọi `work.inner()` trực tiếp qua proxy, in `trace-before-inner` rồi `inner` lần thứ hai.

Đây là kết quả **Spring AOP đúng theo thiết kế**, không phải bug parser pointcut. Cố gắng "sửa" bằng `@Order` hay `proxyTargetClass` không đổi điều đó.

## 11. Chạy LTW và kiểm tra self-invocation

Lệnh sau copy weaver agent và mở JVM mới có `-javaagent`:

```bash
mvn -q compile dependency:copy exec:exec
```

Dòng `System.out` của ứng dụng **dự kiến** khi AspectJ weave thành công:

```text
mode=LTW
springProxy=false
outer
trace-before-inner
inner
trace-before-inner
inner
```

Diễn giải:

1. `springProxy=false` vì không đăng ký `ProxyConfig` trong LTW mode; Spring vẫn tạo `Work` bean bình thường.
2. AspectJ agent có mặt trước khi `demo.Work` được load. Nó đọc aop.xml, đăng ký `TraceAspect` và weave method execution của `Work.inner()`.
3. `this.inner()` từ `outer()` đi vào **woven method**, vì thế có trace trước body `inner` lần thứ nhất.
4. `work.inner()` gọi từ client cũng đi vào cùng woven method, có trace lần thứ hai.
5. AspectJ có thể in thêm verbose logs ngoài năm dòng stdout do chương trình in, tùy JVM/weaver.

`springProxy=false` **không chứng minh** weaving một mình; cần log `weaveinfo` và kết quả của lời gọi nội bộ. Ngược lại, `springProxy=true` chỉ là dấu hiệu Spring proxy, không phải bằng chứng AspectJ LTW.

## 12. Thu thập bằng chứng triển khai LTW

Để kết luận "LTW đã hoạt động", kiểm tra theo trình tự:

1. **JVM thực sự chạy target:** startup args có `-javaagent:.../aspectjweaver.jar`; không chỉ ở JVM khác như Maven daemon, IDE hoặc Surefire.
2. **Runtime classpath:** `META-INF/aop.xml` và `demo.TraceAspect` có mặt trong process thực sự.
3. **Weaver registration:** verbose log cho biết aop.xml đã được load và aspect đã register.
4. **Type selection:** `demo.Work` không bị loại bởi include/exclude, và class được load sau khi agent cài vào.
5. **Join point:** log `weaveinfo` thể hiện `execution(void demo.Work.inner())` hoặc signature tương đương đã được advise.
6. **Behavior assertion:** trong LTW mode có hai lần `trace-before-inner`; trong PROXY baseline chỉ có một.
7. **No duplication:** không vô tình kích hoạt Spring proxy và AspectJ weaving cho cùng custom aspect cùng lúc.

Có thể bật Java system properties chẩn đoán:

```text
-Daj.weaving.verbose=true
-Dorg.aspectj.weaver.showWeaveInfo=true
```

Trong ví dụ, `aop.xml` đã bật `-verbose -showWeaveInfo`. Không để weaving debug logs quá rộng chạy thường trực trên production; chỉ bật ở môi trường cần chẩn đoán.

**Lỗi thường gặp:** `AspectJ agent started` hay `register aspect` xuất hiện nhưng không có join point weaveinfo. Lúc đó phải kiểm tra pointcut, type pattern, module/classloader, thời gian load class, thay vì kết luận advice đã chạy.

## 13. Spring @EnableLoadTimeWeaving khác AspectJ Java agent

Spring Framework hỗ trợ `@EnableLoadTimeWeaving`:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableLoadTimeWeaving;
import org.springframework.context.annotation.EnableLoadTimeWeaving.AspectJWeaving;

@Configuration
@EnableLoadTimeWeaving(aspectjWeaving = AspectJWeaving.ENABLED)
public class SpringLtwConfiguration {
}
```

Đây là **snippet lựa chọn thay thế**, không thêm vào project runnable đang dùng JVM AspectJ agent để tránh trộn setup. Với Spring-managed LTW:

- Spring tạo các infrastructure components như `LoadTimeWeaver` và `AspectJWeavingEnabler`.
- `LoadTimeWeaver` lắp AspectJ transformer vào một ClassLoader được Spring hỗ trợ.
- Trong generic Java process, có thể cần `-javaagent:/path/to/spring-instrument.jar` để cung cấp instrumentation cho `InstrumentationLoadTimeWeaver`.
- Một số app servers có classloader hỗ trợ local instrumentation nên có thể không cần chỉnh JVM launch script.
- `AspectJWeaving.AUTODETECT` là lựa chọn mặc định, phụ thuộc việc phát hiện `META-INF/aop.xml`.
- `DISABLED` không weave, `ENABLED` yêu cầu Spring setup weaving khi infrastructure hỗ trợ.

**Phân biệt hai agent:**

| Agent / thiết lập | Vai trò |
| --- | --- |
| `-javaagent:aspectjweaver.jar` | AspectJ LTW transformer trực tiếp trong JVM |
| `-javaagent:spring-instrument.jar` | Cung cấp Java instrumentation cho Spring `LoadTimeWeaver` trong môi trường cần nó |
| `@EnableLoadTimeWeaving` | Yêu cầu Spring đăng ký hạ tầng LTW; không tự biến mọi JVM thành có instrumentation |
| `@EnableAspectJAutoProxy` | Tạo Spring AOP proxies, **không** dùng AspectJ bytecode weaving |

Không đổi qua lại các thiết lập này tùy tiện. Khi ClassLoader đã load target trước lúc Spring LTW transformer được cài, target đó thường không được weave hồi tố bằng LTW thông thường.

## 14. Khi Spring-aspects là phần không thể thiếu

Trong custom `TraceAspect` của ví dụ, ta không dùng `spring-aspects` vì chỉ muốn một aspect tự viết hoạt động bằng AspectJ agent. Nhưng khi muốn sử dụng **AspectJ aspects do Spring cung cấp** (không tự viết lại transaction/caching/bean-configurer), dependency `org.springframework:spring-aspects` là thành phần quan trọng.

### 14.1. Transaction mode ASPECTJ

Trong proxy mode mặc định, `@Transactional` chỉ chặn external method calls vào proxy. Nếu cần xử lý `this.innerTransactional()` khi class đã weave, Spring có:

```java
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
public class AspectjTransactionConfig {
    // PlatformTransactionManager phải được cấu hình đúng ở context.
}
```

Đây là **snippet**, không phải ứng dụng giao dịch đầy đủ. Cần có `spring-aspects` chứa `org.springframework.transaction.aspectj.AnnotationTransactionAspect`, Spring transaction infrastructure với `PlatformTransactionManager` và **weaving thật** cho concrete implementation method.

Quan trọng:

- `@Transactional` nên đặt trên **implementation class/method**, không chỉ interface; AspectJ weaving không kế thừa Java method annotation từ interface.
- `@EnableTransactionManagement(mode=ASPECTJ)` **không tự cài javaagent**.
- Không tự viết custom `@Around` begin/commit/rollback thay Spring transaction manager.
- Kiểm tra **DB state và propagation** (`REQUIRES_NEW`, rollback), không kết luận đúng chỉ vì một log hoặc `TransactionSynchronizationManager.isActualTransactionActive()` trả true.
- Không bật cả proxy transaction advisor và AspectJ transaction aspect cho cùng concern mà thiếu kiểm soát.

### 14.2. @Configurable cho object ngoài IoC

Nếu object do ORM hoặc mã ngoài Spring tạo bằng `new` nhưng cần collaborator từ BeanFactory, Spring `AnnotationBeanConfigurerAspect` phối hợp với `@Configurable`:

```java
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;

@Configurable(autowire = Autowire.BY_TYPE)
public class Invoice {
    private Formatter formatter;

    public void setFormatter(Formatter formatter) {
        this.formatter = formatter;
    }

    public String render() {
        return formatter == null ? "NO_INJECTION" : formatter.format();
    }
}
```

Đây là **snippet minh họa** (cần type `Formatter` và bean tương ứng). Spring có:

```java
@Configuration
@EnableSpringConfigured
public class ConfigurableSupportConfig {
    // Bean Formatter và các dependencies.
}
```

`@EnableSpringConfigured` cấu hình `AnnotationBeanConfigurerAspect` để truy cập Spring BeanFactory. Nó **không tự weave bytecode**; cần `spring-aspects`, AspectJ weaving đúng class và object phải được tạo sau khi aspect có BeanFactory. Object được cấu hình **không tự trở thành Spring singleton bean**.

Thời điểm: với `@Configurable` mặc định, injection có thể xảy ra **sau constructor initialization**; không dùng field kỳ vọng injected trong constructor body khi chưa lựa chọn/cấu hình `preConstruction=true` và kiểm thử tương ứng. Tránh đánh dấu `@Configurable` trên bean vốn đã được Spring quản lý và cấu hình để không bị double injection.

### 14.3. Cache và async

Spring-aspects cũng có `AnnotationCacheAspect` và `AnnotationAsyncExecutionAspect` phục vụ mode AspectJ:

```java
@Configuration
@EnableCaching(mode = AdviceMode.ASPECTJ)
@EnableAsync(mode = AdviceMode.ASPECTJ)
class CacheAsyncConfig {
    // CacheManager và executor phải được cung cấp.
}
```

Đây là snippet định hướng; không tự bật weaving, không tự tạo CacheManager, TTL, executor hoặc truyền transaction/thread context an toàn. Đổi mode chỉ giải quyết điểm interception; vẫn phải kiểm tra cache hit/miss, exception và async completion.

## 15. Debug các tình huống hỏng thường gặp

| Triệu chứng | Nguyên nhân có thể | Xử lý |
| --- | --- | --- |
| LTW mode không có trace, `springProxy=false` | AspectJ agent chưa chạy, aop.xml không được thấy, class không weave | Xem đúng JVM args, aop.xml và `weaveinfo` |
| Có aspectjweaver dependency nhưng `this.inner()` không được advise | Chỉ có thư viện, không có LTW | Bật agent/transformer thực và chạy JVM sạch |
| `aop.xml` được đọc nhưng không có method weaveinfo | Include/exclude hoặc `execution` không match | Kiểm tra FQCN/signature/classloader |
| Một invocation in hai dòng trace | Proxy + LTW cùng áp dụng | Chọn đúng một mechanism cho phép thử |
| LTW hoạt động local nhưng fail trong app server | Classloader, JDK module, startup flags khác | Kiểm tra process đang chạy thật và log weave |
| `@EnableLoadTimeWeaving` có nhưng không weave | ClassLoader không hỗ trợ transformer hoặc cài quá muộn | Xác minh `LoadTimeWeaver` và instrumentation |
| `@Configurable` không inject | Thiếu BeanFactory binding, thiếu weaving hoặc dependency | Kiểm tra `@EnableSpringConfigured` và target |
| `@Transactional` self-invocation không có transaction riêng | Vẫn mode PROXY, annotation không đúng place hoặc thiếu manager/weave | Kiểm tra mode, target implementation và DB |
| LTW logs rất lớn/startup chậm | Weave quá rộng và verbose/debug quá nhiều | Giới hạn packages và giảm log |
| Aspect tạo recursion | Advice gọi lại chính method match pointcut | Thu hẹp pointcut, tránh gọi vòng ngoài chủ ý |
| Một type đã được load trước khi transformer hoạt động | Class loading order | Đưa transformer sớm hơn và kiểm tra class loading |

Một lỗi **auto-proxy** nên được debug bằng Spring beans, `AopUtils`, `Advised.getAdvisors()` và pointcut match. Một lỗi **LTW** phải được debug bằng startup agent, transformer, `META-INF/aop.xml`, ClassLoader và weaveinfo. Không đánh đồng hai đường.

## 16. Kiểm thử và xác nhận trước production

**Bộ đối chứng tối thiểu cho project:**

| Kiểm tra | PROXY | LTW |
| --- | --- | --- |
| Có Spring-managed Work bean | Có | Có |
| `AopUtils.isAopProxy(work)` | true | false |
| `work.outer()` gọi `this.inner()` được advice | Không | Có |
| `work.inner()` gọi từ client được advice | Có | Có |
| `META-INF/aop.xml` và weaver được xử lý | Không yêu cầu | Bắt buộc |
| Số lần `trace-before-inner` trên hai lần gọi body | 1 | 2 |

Nên tự động hóa hai process riêng trong CI, xác minh **mỗi dòng output do ứng dụng in** và kiểm tra AspectJ weaveinfo ở LTW mode. Nếu muốn dùng JUnit, cấu hình JVM fork của test runner với agent phù hợp; không nghĩ `mvn test` mặc định tự chạy AspectJ LTW.

Với Spring infrastructure annotations, bổ sung tests:

- `@Transactional`: đúng transaction manager, rollback/commit state, propagation self-invocation.
- `@Cacheable`: cache hit/miss, số lần target thực thi, cache provider và key.
- `@Async`: executor thread và future completion, exception propagation.
- `@Configurable`: object tạo ngoài container được inject đúng, không double-configure.

Không coi chỉ một dòng "aspect registered" đủ xác minh production. Các class nên được weave trong phạm vi được chọn, đúng runtime JVM và phải có behavior test thật.

## 17. Checklist quyết định và vận hành

1. **Đề bài thực tế:** cần advice trên self-invocation, object ngoài container hay chỉ muốn code ít hơn?
2. **Proxy trước:** thử chia bean/factory/DI, kiểm tra tất cả lời gọi hiện qua Spring bean proxy.
3. **Chọn concern:** custom @Aspect, transaction, cache, async hoặc bean configurer? Mỗi loại có Spring infrastructure riêng.
4. **Giới hạn weaving:** chỉ weave app packages/classes cần thiết; không bật toàn bộ library nếu không có mục đích.
5. **Chọn mechanism:** AspectJ JVM agent hay Spring-managed `LoadTimeWeaver` cho app server/classloader phù hợp.
6. **Đồng bộ dependency:** JDK, Spring Framework modules, Spring-aspects khi dùng và AspectJ weaver tương thích.
7. **Kiểm tra timing:** transformer sẵn sàng trước khi type cần weave được ClassLoader định nghĩa.
8. **Dựng test đối chứng:** PROXY vs LTW ở JVM độc lập, kiểm tra trace count và `isAopProxy`.
9. **Đọc weaveinfo:** chứng minh target method thực sự được woven, không chỉ aspect trên classpath.
10. **Không double advice:** không vô tình đăng ký cùng aspect vừa qua Spring proxy vừa qua AspectJ agent.
11. **Quan sát runtime:** startup cost, classloader warnings, module restrictions, context lifecycle và performance.
12. **Bảo trì:** ghi lệnh chạy/test/deploy với agent flags; nếu refactor giúp bỏ LTW, đánh giá giảm phức tạp.

## 18. Câu hỏi tự kiểm tra

**1. Vì sao `this.inner()` bị bỏ qua trong Spring AOP proxy mode?**  
Vì lời gọi nội bộ diễn ra trực tiếp trên target object, không qua Spring proxy và advisor chain một lần nữa.

**2. `@EnableAspectJAutoProxy(proxyTargetClass=true)` có làm self-invocation được advise không?**  
Không. Nó dùng class-based proxy thay JDK proxy, không weave bytecode.

**3. Tại sao Run B in `springProxy=false` nhưng có hai trace?**  
AspectJ weave vào method execution của `Work.inner()` nên advice chạy mà không cần Spring proxy.

**4. Có `aspectjweaver` trong pom.xml đã bật LTW chưa?**  
Chưa. Cần cấu hình weaving thật: Java agent hoặc Spring LTW transformer thích hợp, cùng aop.xml/aspects.

**5. Vì sao phải chạy PROXY và LTW trong hai JVM khác nhau?**  
Để tách agent/classloading/JIT và ngăn cùng aspect được áp dụng bởi hai cơ chế trong một process.

**6. Trong AspectJ transaction mode, đặt `@Transactional` ở interface có đủ không?**  
Không nên. AspectJ weaving cần thấy annotation trên implementation method/class, và vẫn cần Spring transaction manager.

**7. Vì sao `@EnableSpringConfigured` không đủ cho `new Invoice()`?**  
Nó cấp BeanFactory cho Spring `AnnotationBeanConfigurerAspect` nhưng object class vẫn phải được AspectJ weave, và cần `spring-aspects`.

**8. Kết luận "LTW thành công" cần bằng chứng nào?**  
Agent/transformer đúng JVM, aspect/aop.xml được nhận, weaveinfo cho target join point và hành vi chạy khác proxy baseline đúng kỳ vọng.

**9. Khi nào không nên dùng LTW?**  
Khi một refactor nhỏ tách service bean hoặc dùng Spring-native transaction/cache/security/observability đáp ứng đủ yêu cầu với deployment đơn giản hơn.

## Tài liệu chính thức

- Spring Framework — Using AspectJ with Spring Applications: https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — Using @Transactional: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
- Spring Framework — Using @Transactional with AspectJ: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/aspectj.html
- Spring Framework — @EnableLoadTimeWeaving Javadoc: https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/EnableLoadTimeWeaving.html
- AspectJ — LTW Developer Guide: https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-practical-workshop-012">Viết báo cáo vết thực thi và giải thích giới hạn của thiết kế</a>

<details>
<summary>Xem chi tiết</summary>

# Viết báo cáo vết thực thi và giải thích giới hạn của thiết kế Spring AOP

## 1. Mục đích của báo cáo

Sau khi gắn `@Aspect`, `@Around`, `@Before` hay `@AfterThrowing` vào Spring bean, nhóm phát triển cần biết **lời gọi nào thực sự đi qua proxy, aspect nào chạy, lúc nào target chạy, kết quả cuối của method invocation là gì, và điều gì không thể suy ra từ vết đó**.

Một báo cáo vết thực thi (execution trace report) không chỉ là bản sao stdout. Nó gồm **bối cảnh cấu hình**, **các sự kiện có chứng cứ**, **phân tích control flow**, **giới hạn quan sát**, **tác động tới hiệu năng** và **các đề xuất kiểm thử**.

Tập trung vào Spring Aspect/Spring AOP trong Spring Framework:

- Xác định thứ tự các aspect khi có `@Order`.
- Phân biệt vào proxy, vào target, return, throw và finally.
- Phân biệt exception từ chính target với exception do một aspect chặn trước target.
- Trình bày self-invocation và object được tạo bằng `new` nằm ngoài Spring AOP proxy.
- Viết trace summary từ một recorder cho các tình huống success, policy rejected, target failed, raw invocation.
- Xác nhận báo cáo bằng integration tests chạy trên `ApplicationContext` thật.
- Nêu thẳng phạm vi không bao phủ: phương thức không được proxy, async completion, concurrent requests, transaction/caching advisors và mất sự kiện.

**Nguyên tắc:** từ Spring AOP event chỉ kết luận những gì cấu hình proxy, pointcut và phép kiểm chứng thực sự hỗ trợ. Không biến vết của một phương thức thành tuyên bố đã theo dõi toàn bộ JVM hoặc các service khác.

## 2. Vết thực thi Spring AOP là vết của interceptor chain

Khi caller có reference tới Spring AOP proxy và gọi một method match pointcut, proxy chạy chuỗi interceptor/advice. Một `@Around` ghi before event, gọi `ProceedingJoinPoint.proceed()`, sau đó quan sát normal return hoặc exception; code `finally` chạy trong cả hai nhánh.

~~~text
Caller
  |
  v
Spring AOP proxy
  |
  +-- TraceAspect @Order(10): enter
        |
        +-- PolicyAspect @Order(20): enter
              |
              +-- target.handle(code) (nếu policy cho phép)
              |
            PolicyAspect: return/throw/finally
        |
      TraceAspect: return/throw/finally
  |
  v
Caller nhận value hoặc exception
~~~

Theo Spring Reference, giá trị `@Order` nhỏ hơn có precedence cao hơn giữa các aspects khác nhau. **Khi đi vào:** order 10 → order 20 → target. **Khi quay ra:** order 20 → order 10. Một outer aspect cũng có thể quan sát exception do inner aspect ném ra trước khi target chạy.

Cần phân biệt ba lớp:

| Lớp quan sát | Dữ liệu có thể thu | Không nên mặc định suy ra |
| --- | --- | --- |
| Proxy entry | Invocation được advice match, tham số đã chọn, bắt đầu thời gian | Target chắc chắn chạy |
| Target event | Method body thật sự đã chạy tới instrumentation point | Request cuối cùng thành công |
| Return/throw/finally | Kết quả hoặc exception rời khỏi `proceed()` | Future async đã xong, transaction commit hoàn tất, downstream side effect đã durable |

`@Around` không nhất thiết chỉ quan sát: nó có thể **không gọi `proceed()`** (policy reject/cache hit), gọi nhiều lần (retry) hoặc trả giá trị khác. Vì vậy số sự kiện method execution có thể khác số request đi vào proxy.

## 3. Thiết kế ca thực hành để báo cáo đủ bằng chứng

Ta xây service tên `WorkService` với hai phương thức:

- `handle("ok")`: target chạy và trả `done:ok`.
- `handle("deny")`: `PolicyAspect` ném `IllegalArgumentException("denied")` trước khi target chạy.
- `handle("boom")`: target chạy rồi ném `IllegalStateException("processing failure")`.
- `outer("self")`: caller gọi Spring proxy, nhưng trong target gọi `this.handle("self")`; vì aspect chỉ match `handle`, advice không chạy cho lần gọi nội bộ đó.
- Một raw `new DefaultWorkService(...).handle("raw")`: target chạy mà không qua Spring AOP proxy.

Hai aspects khác nhau:

| Aspect | `@Order` | Chức năng | Hệ quả |
| --- | --- | --- | --- |
| `TraceAspect` | 10 | Outer observer, ghi enter/return/throw/finally | Vẫn nhìn thấy policy rejection |
| `PolicyAspect` | 20 | Inner policy, ghi enter/reject/return/throw/finally | Có thể short-circuit, không gọi target |

Đây là một **sắp xếp có chủ đích**. Nếu audit chỉ đứng phía trong policy, audit sẽ không thấy những request bị từ chối trước khi đi qua nó. Ngược lại, đặt tracer ngoài policy giúp báo cáo cả rejection. Không có một thứ tự advice tối ưu cho mọi ứng dụng; thứ tự phải phản ánh requirement.

## 4. Project Java 17+ hoàn chỉnh

Ví dụ dùng Spring Framework 6.2, AspectJ annotations, Gradle và JUnit Jupiter. Đây là **Spring AOP proxy**, không phải AspectJ compile-time weaving hay load-time weaving.

### 4.1. Cấu trúc file

~~~text
spring-aop-trace/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/demo/
    │   ├── WorkService.java
    │   ├── DefaultWorkService.java
    │   ├── TraceEvents.java
    │   ├── TraceAspect.java
    │   ├── PolicyAspect.java
    │   ├── AopConfig.java
    │   └── Demo.java
    └── test/java/demo/
        └── TraceTest.java
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-aop-trace'
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
    implementation 'org.springframework:spring-context'
    implementation 'org.springframework:spring-aop'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'

    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.11.4'
}

application {
    mainClass = 'demo.Demo'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

Spring BOM giữ các module Spring đồng bộ. Dependency AspectJ hỗ trợ Spring parse và match `@AspectJ` pointcuts; ví dụ không cài AspectJ agent và không dùng compiler `ajc`.

### 4.2. Interface nghiệp vụ

`src/main/java/demo/WorkService.java`:

~~~java
package demo;

public interface WorkService {
    String handle(String code);
    String outer(String code);
}
~~~

`src/main/java/demo/DefaultWorkService.java`:

~~~java
package demo;

public final class DefaultWorkService implements WorkService {
    private final TraceEvents events;

    public DefaultWorkService(TraceEvents events) {
        this.events = events;
    }

    @Override
    public String handle(String code) {
        events.add("target:handle:" + code);
        if ("boom".equals(code)) {
            throw new IllegalStateException("processing failure");
        }
        return "done:" + code;
    }

    @Override
    public String outer(String code) {
        events.add("target:outer:" + code);
        return this.handle(code);
    }
}
~~~

`DefaultWorkService` là `final` nhưng implement interface `WorkService`. Với `proxyTargetClass=false`, Spring có thể dùng JDK dynamic proxy, không cần subclass class cuối cùng này. `outer` cố ý dùng `this.handle` để minh họa điểm mù của Spring AOP self-invocation.

### 4.3. Recorder đơn giản cho bài thực hành

`src/main/java/demo/TraceEvents.java`:

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

public final class TraceEvents {
    private final List<String> events = new ArrayList<>();

    public void add(String event) {
        events.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }
}
~~~

Lớp này **chỉ dùng trong demo đồng bộ**. `ArrayList` không thread-safe; nếu đặt một singleton recorder tương tự vào production concurrent web application, event giữa các request sẽ xen kẽ và có race. Phần giới hạn và hướng cải tiến được trình bày phía sau.

### 4.4. TraceAspect đứng ngoài cùng

`src/main/java/demo/TraceAspect.java`:

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(10)
public class TraceAspect {
    private final TraceEvents events;

    public TraceAspect(TraceEvents events) {
        this.events = events;
    }

    @Around("execution(* demo.DefaultWorkService.handle(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        events.add("trace:enter");
        try {
            Object result = pjp.proceed();
            events.add("trace:return");
            return result;
        } catch (Throwable error) {
            events.add("trace:throw:" + error.getClass().getSimpleName());
            throw error;
        } finally {
            events.add("trace:finally");
        }
    }
}
~~~

`trace:enter` nghĩa advice đã chạy, không có nghĩa target đã chạy. `trace:return` chỉ nói `proceed()` return bình thường, không đủ để kết luận kết quả bất đồng bộ đã hoàn tất. `trace:finally` xuất hiện cả khi thành công lẫn khi có exception.

### 4.5. PolicyAspect ở bên trong

`src/main/java/demo/PolicyAspect.java`:

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(20)
public class PolicyAspect {
    private final TraceEvents events;

    public PolicyAspect(TraceEvents events) {
        this.events = events;
    }

    @Around("execution(* demo.DefaultWorkService.handle(..)) && args(code)")
    public Object around(ProceedingJoinPoint pjp, String code)
            throws Throwable {
        events.add("policy:enter");
        try {
            if ("deny".equals(code)) {
                events.add("policy:reject");
                throw new IllegalArgumentException("denied");
            }
            Object result = pjp.proceed();
            events.add("policy:return");
            return result;
        } catch (Throwable error) {
            events.add("policy:throw:" + error.getClass().getSimpleName());
            throw error;
        } finally {
            events.add("policy:finally");
        }
    }
}
~~~

`args(code)` liên kết tham số pointcut với tham số advice. Khi copy sang Gradle project khác với code phức tạp hơn, cân nhắc bật `-parameters` hoặc khai báo `argNames` thích hợp theo hướng dẫn Spring `@AspectJ` để tránh lỗi khám phá tên tham số. Với ví dụ một tham số duy nhất, matching và binding đều rõ ràng.

Cơ chế chặn là một nhánh **trước `pjp.proceed()`**: với `deny`, target không chạy. Outer `TraceAspect` thấy `IllegalArgumentException` vì lời gọi inner advice ném lỗi ra khỏi `proceed()` của trace.

### 4.6. Spring configuration

`src/main/java/demo/AopConfig.java`:

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AopConfig {
    @Bean
    public TraceEvents traceEvents() {
        return new TraceEvents();
    }

    @Bean
    public WorkService workService(TraceEvents events) {
        return new DefaultWorkService(events);
    }

    @Bean
    public TraceAspect traceAspect(TraceEvents events) {
        return new TraceAspect(events);
    }

    @Bean
    public PolicyAspect policyAspect(TraceEvents events) {
        return new PolicyAspect(events);
    }
}
~~~

`@Aspect` **không tự làm class thành bean**. Ở đây ta đăng ký hai aspect qua `@Bean`; `@EnableAspectJAutoProxy` bật Spring auto-proxy. Nếu chỉ `new TraceAspect` ở bên ngoài, đó không phải đăng ký Spring advice.

### 4.7. Main dùng Spring bean proxy và in trace

`src/main/java/demo/Demo.java`:

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class Demo {
    public static void main(String[] args) {
        try (var ctx =
                new AnnotationConfigApplicationContext(AopConfig.class)) {
            WorkService bean = ctx.getBean(WorkService.class);
            TraceEvents events = ctx.getBean(TraceEvents.class);

            System.out.println("spring-proxy: " + AopUtils.isAopProxy(bean));

            System.out.println("case=ok");
            System.out.println("result: " + bean.handle("ok"));
            System.out.println("events: " + events.snapshot());

            events.clear();
            System.out.println("case=deny");
            try {
                bean.handle("deny");
            } catch (IllegalArgumentException ex) {
                System.out.println("error: " + ex.getMessage());
            }
            System.out.println("events: " + events.snapshot());

            events.clear();
            System.out.println("case=boom");
            try {
                bean.handle("boom");
            } catch (IllegalStateException ex) {
                System.out.println("error: " + ex.getMessage());
            }
            System.out.println("events: " + events.snapshot());

            events.clear();
            System.out.println("case=self");
            System.out.println("result: " + bean.outer("self"));
            System.out.println("events: " + events.snapshot());

            events.clear();
            System.out.println("case=raw");
            WorkService raw = new DefaultWorkService(events);
            System.out.println("result: " + raw.handle("raw"));
            System.out.println("events: " + events.snapshot());
        }
    }
}
~~~

Chạy trên **JDK 17+** bằng Gradle:

~~~bash
gradle run
~~~

Hoặc `./gradlew run`/`gradlew.bat run` nếu project đã có wrapper.

**Stdout logic dự kiến** (bỏ qua phần Gradle):

~~~text
spring-proxy: true
case=ok
result: done:ok
events: [trace:enter, policy:enter, target:handle:ok, policy:return, policy:finally, trace:return, trace:finally]
case=deny
error: denied
events: [trace:enter, policy:enter, policy:reject, policy:throw:IllegalArgumentException, policy:finally, trace:throw:IllegalArgumentException, trace:finally]
case=boom
error: processing failure
events: [trace:enter, policy:enter, target:handle:boom, policy:throw:IllegalStateException, policy:finally, trace:throw:IllegalStateException, trace:finally]
case=self
result: done:self
events: [target:outer:self, target:handle:self]
case=raw
result: done:raw
events: [target:handle:raw]
~~~

Đây là **expected stdout theo mã**, không phải log thực tế đã chạy trong tài liệu. Event order cần được xác nhận bằng test khi sử dụng version/framework/config khác.

## 5. Từ raw trace tới một báo cáo dễ đọc

Không nên dán stdout vào báo cáo rồi kết luận ngay. Hãy diễn giải riêng từng case, nêu cả bằng chứng và giới hạn.

### 5.1. Case thành công

~~~text
trace:enter
  policy:enter
    target:handle:ok
  policy:return
  policy:finally
trace:return
trace:finally
~~~

**Kết luận:** request vào Spring proxy, đi qua hai aspect theo `@Order(10)` và `@Order(20)`; target đã chạy; caller nhận `done:ok`. Trật tự lúc vào ngược với trật tự unwind lúc ra. Không có lỗi đồng bộ trong case này.

Không suy luận thêm rằng DB đã commit, message queue đã nhận event hay Future async đã hoàn tất, bởi ví dụ không chứa các cơ chế đó.

### 5.2. Case policy từ chối

~~~text
trace:enter
  policy:enter
  policy:reject
  policy:throw:IllegalArgumentException
  policy:finally
trace:throw:IllegalArgumentException
trace:finally
~~~

**Kết luận:** policy đã đưa ra quyết định từ chối và ném exception trước `proceed()`. Target không chạy theo nhánh mã cụ thể; outer trace vẫn quan sát được failure. Trong hệ thống thực, cần có instrumented target counter hoặc audit event đúng vị trí để chứng minh target không bị gọi, thay vì coi thiếu log là bằng chứng tuyệt đối.

### 5.3. Case target thất bại

~~~text
trace:enter
  policy:enter
    target:handle:boom
  policy:throw:IllegalStateException
  policy:finally
trace:throw:IllegalStateException
trace:finally
~~~

**Kết luận:** khác với `deny`, target **đã chạy** rồi mới throw. Lỗi propagate qua inner và outer aspect; `finally` của cả hai vẫn thực thi. Nếu chỉ thấy dòng `trace:throw` mà không có target marker, chưa xác định được lỗi bắt nguồn từ policy hay target.

### 5.4. Case self-invocation và raw target

~~~text
target:outer:self
target:handle:self

target:handle:raw
~~~

Trong `outer("self")`, caller bắt đầu qua Spring bean proxy nhưng pointcut không chọn `outer`, và `this.handle` ở bên trong target không gọi lại qua proxy. Trong `raw`, object tạo trực tiếp bằng `new` không được Spring auto-proxy. Cả hai vẫn có target events.

**Kết luận:** **vắng advice event không chứng minh method không được thực thi**. Nó có thể được gọi ngoài Spring proxy boundary, không match pointcut hoặc instrumentation bị thiếu.

### 5.5. Bảng tổng hợp cho báo cáo

| Case | Proxy pointcut `handle` được áp | Target reached | Outcome caller | Kết luận chính |
| --- | --- | --- | --- | --- |
| `ok` | Có | Có | `done:ok` | Chain chạy đủ, unwind bình thường |
| `deny` | Có | Không | `IllegalArgumentException` | Policy short-circuit |
| `boom` | Có | Có | `IllegalStateException` | Target error được rethrow qua chain |
| `self` | Không với lời gọi `handle` nội bộ | Có | `done:self` | Self-invocation bypass proxy |
| `raw` | Không | Có | `done:raw` | Object không phải Spring proxy |

## 6. Định dạng báo cáo vết trong dự án thực tế

Mỗi report cần chỉ rõ **phạm vi quan sát**, không chỉ tổng số dòng.

| Trường | Ví dụ / cách ghi | Vì sao cần |
| --- | --- | --- |
| Application/version | Spring 6.2.14; artifact/build id | Ngữ cảnh tái lập |
| Proxy | Spring AOP, JDK proxy dự kiến; kiểm tra bằng `AopUtils` | Loại cơ chế đã áp |
| Target | `DefaultWorkService.handle(String)` | Định nghĩa join point |
| Pointcut | `execution(* demo.DefaultWorkService.handle(..))` | Vùng được instrument |
| Aspect order | Trace=10, Policy=20 | Giải thích nesting |
| Invocation ID | Một ID độc lập mỗi call | Group event, tránh interleaving |
| Outcome | success/rejected/failed/unknown | Tách nguyên nhân |
| Target reached | yes/no/unknown | Tránh suy diễn từ thiếu event |
| Timing | `System.nanoTime()` delta | Duration trong một JVM |
| Thread | id/name theo mốc | Phát hiện chuyển thread |
| Exception type | Class name, đã sanitize | Phân loại lỗi |
| Evidence | Event sequence + test assertion | Truy vết kết luận |
| Instrumentation gaps | Self-invocation, raw, async, sampling | Nêu điểm mù |

### Mẫu Markdown báo cáo cho một invocation

~~~markdown
# Spring AOP Execution Report

## Scope
- Framework: Spring 6.2.x
- Bean: WorkService
- Target: DefaultWorkService.handle(String)
- Proxy/advice: Spring AOP, Trace(10) -> Policy(20)

## Scenario
- Invocation: handle("deny")
- Outcome: policy_rejected
- Caller exception: IllegalArgumentException("denied")
- Target reached: NO (verified by policy path and test)

## Ordered evidence
1. trace:enter
2. policy:enter
3. policy:reject
4. policy:throw:IllegalArgumentException
5. policy:finally
6. trace:throw:IllegalArgumentException
7. trace:finally

## Interpretation
Policy rejected before invoking proceed().
The outer tracing aspect observed the failure.

## Known limitations
The report covers this Spring AOP proxy invocation only.
It does not prove visibility of raw objects, self-calls,
async completion, or other methods outside the pointcut.
~~~

Mẫu này minh họa một artifact Markdown **được viết bằng tay từ trace**. Muốn sinh báo cáo tự động, nên lưu events thành cấu trúc có schema và render report từ dữ liệu bất biến.

## 7. Kiểm chứng báo cáo bằng JUnit Jupiter

`src/test/java/demo/TraceTest.java`:

~~~java
package demo;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class TraceTest {

    @Test
    void successfulCallHasNestedOrder() {
        try (var ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
            WorkService bean = ctx.getBean(WorkService.class);
            TraceEvents events = ctx.getBean(TraceEvents.class);

            assertTrue(AopUtils.isAopProxy(bean));
            assertEquals("done:ok", bean.handle("ok"));
            assertEquals(List.of(
                "trace:enter", "policy:enter",
                "target:handle:ok",
                "policy:return", "policy:finally",
                "trace:return", "trace:finally"
            ), events.snapshot());
        }
    }

    @Test
    void policyRejectsWithoutReachingTarget() {
        try (var ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
            WorkService bean = ctx.getBean(WorkService.class);
            TraceEvents events = ctx.getBean(TraceEvents.class);

            IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> bean.handle("deny"));
            assertEquals("denied", error.getMessage());
            assertEquals(List.of(
                "trace:enter", "policy:enter", "policy:reject",
                "policy:throw:IllegalArgumentException", "policy:finally",
                "trace:throw:IllegalArgumentException", "trace:finally"
            ), events.snapshot());
        }
    }

    @Test
    void targetFailureIsDifferentFromPolicyRejection() {
        try (var ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
            WorkService bean = ctx.getBean(WorkService.class);
            TraceEvents events = ctx.getBean(TraceEvents.class);

            IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> bean.handle("boom"));
            assertEquals("processing failure", error.getMessage());
            assertEquals(List.of(
                "trace:enter", "policy:enter", "target:handle:boom",
                "policy:throw:IllegalStateException", "policy:finally",
                "trace:throw:IllegalStateException", "trace:finally"
            ), events.snapshot());
        }
    }

    @Test
    void selfInvocationHasNoHandleAdvice() {
        try (var ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
            WorkService bean = ctx.getBean(WorkService.class);
            TraceEvents events = ctx.getBean(TraceEvents.class);

            assertEquals("done:self", bean.outer("self"));
            assertEquals(List.of(
                "target:outer:self", "target:handle:self"
            ), events.snapshot());
        }
    }

    @Test
    void rawObjectBypassesSpringProxy() {
        try (var ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
            TraceEvents events = ctx.getBean(TraceEvents.class);
            WorkService raw = new DefaultWorkService(events);

            assertFalse(AopUtils.isAopProxy(raw));
            assertEquals("done:raw", raw.handle("raw"));
            assertEquals(List.of("target:handle:raw"), events.snapshot());
        }
    }
}
~~~

Chạy:

~~~bash
gradle test
~~~

**Kết quả kỳ vọng:** năm tests pass và Gradle báo `BUILD SUCCESSFUL`. Các tests tạo `ApplicationContext` mới, tránh phụ thuộc state từ test trước. Đây là **kết quả dự kiến khi chạy đúng project**, không phải kết quả được ghi nhận từ một lần thực thi Gradle trong nội dung bài.

Những assertion này phân biệt **đã vào advice**, **đã vào target**, **return bình thường**, **exception path** và **bypass proxy**. Chỉ kiểm tra `AopUtils.isAopProxy` là chưa đủ: một proxy có thể do advisor khác tạo; phải kiểm tra event của chính aspects đang được nghiên cứu.

## 8. Báo cáo phải phân biệt điều gì đã thấy với điều chỉ suy đoán

Những bài trước thực hành riêng các giới hạn proxy, concurrency, advisor ordering và LTW. Ở đây ta đưa chúng vào **quy tắc diễn giải bằng chứng**. Một báo cáo tốt gắn từng kết luận với dữ liệu có thể tái lập; không để người đọc nhầm vết Spring AOP với toàn bộ execution của hệ thống.

| Quan sát trong báo cáo | Kết luận được phép | Kết luận chưa đủ chứng cứ |
| --- | --- | --- |
| `trace:enter` trên Spring proxy | Outer trace advice bắt đầu xử lý invocation đó | Target đã chạy hoặc commit đã thành công |
| `policy:reject` nhưng không có `target:handle` | Trong fixture này, policy chặn trước `proceed()` | Toàn bộ ứng dụng không xử lý request ở nơi khác |
| `target:handle:boom` và `trace:throw` | Target đã vào rồi lỗi đi qua outer advice | Lỗi chắc chắn sinh ra ở DB hoặc rollback đã thành công |
| `outer("self")` có target logs nhưng không có advice `handle` | Self-invocation đi ngoài điểm chặn của proxy cho `handle` | Pointcut của mọi bean trong container sai |
| Không thấy event của một invocation | Chưa có bằng chứng instrumentation ghi nhận event | Target chắc chắn không được gọi |
| `@AfterReturning` ghi nhận future/publisher | Java method trả một handle bình thường | Async/reactive work đã hoàn tất thành công |
| Audit chạy nhưng target không chạy sau cache hit | Advisor ngoài cache quan sát caller invocation | Target đã tính toán lại giá trị được cache |

### 8.1. Độ tin cậy của dữ kiện

Ghi **Spring version, proxy type, bean/method, pointcut, advisor order** và các công tắc `@Enable...` liên quan. Nếu collector có sampling, dropped events, buffering hay lỗi exporter, nêu rõ nguồn và tỷ lệ mất nếu đo được. Không biến việc thiếu log thành bằng chứng âm; đối chứng bằng target instrumentation và integration tests trên cùng cấu hình.

Trace một JVM không tự chứng minh trình tự xuyên nhiều server. `System.nanoTime()` phù hợp cho elapsed time nội bộ, còn wall-clock timestamps giữa máy có thể lệch. Khi nhiều threads cùng ghi trace, mỗi invocation cần correlation ID duy nhất, event record an toàn luồng và thứ tự riêng cho invocation. Một business ID có thể được dùng lại qua nhiều calls, vì vậy không mặc định đó là invocation ID.

### 8.2. Những boundary nằm ngoài vết của ví dụ

- **Spring proxy:** lời gọi vào raw object (`new`), self-invocation, private/final method không thể proxy chặn hoặc code ngoài pointcut có thể thực thi mà không tạo advice event. AspectJ weaving muốn vượt giới hạn này phải được cấu hình và kiểm thử riêng.
- **Aspect control flow:** `@Around` có thể quên `proceed()`, gọi nhiều lần hoặc chuyển return/exception. Collector cũng có thể tự ném lỗi. Báo cáo phân biệt sự kiện do policy cố ý short-circuit với failure trong instrumentation.
- **Spring advisors khác:** cache hit có thể bỏ target, retry có thể gọi lại target, security có thể từ chối trước business method; transaction commit/rollback nằm ở ranh giới advisor thực tế. Một trace event bình thường chưa chứng minh side effects hoặc commit bền vững.
- **Async:** `@Async`, `CompletableFuture` và `Mono/Flux` có điểm trả handle, điểm chạy worker/subscription và điểm completion khác nhau. Không đo end-to-end bằng cách chờ blocking ngay trong advice chỉ để có một duration duy nhất.
- **Concurrency:** nhiều request cùng vào aspect, thứ tự log toàn cục không phản ánh nesting riêng. Dùng immutable event, bounded collector và context propagation khi chuyển executor.

### 8.3. Chi phí, thông tin nhạy cảm và phạm vi báo cáo

Instrumentation có chi phí allocations, locks, formatting và I/O; khi đưa vào production phải đo p95/p99 dưới tải tương đương. Chỉ lưu metadata cần thiết, che credentials/token và dữ liệu cá nhân; tránh metric labels chứa raw user/order/trace ID vì cardinality lớn. Xác định retention, access và exporter failure policy; audit compliance có thể yêu cầu độ bền và fail policy khác telemetry thông thường.

Nếu hệ thống đã có `Micrometer Observation` hoặc tracing integration do Spring cung cấp, trước tiên đánh giá khả năng tái sử dụng các event/span đã có. Với mọi kết luận chưa được đo trực tiếp, ghi **unknown/chưa chứng minh** và đề xuất phép thử bổ sung thay vì khẳng định theo ý muốn.

## 9. Nâng từ demo thành báo cáo runtime thật

### Bước 1 — Chuẩn hóa event

Một cấu trúc dữ liệu hướng tới production có thể là:

~~~java
// Fragment định hướng; không nằm trong source demo hoàn chỉnh.
public record AopTraceEvent(
        String invocationId,
        String traceId,
        String operation,
        String aspect,
        String phase,
        String outcome,
        String exceptionType,
        long elapsedNanos,
        String threadName) {
}
~~~

Những field không quan sát được phải để `null` hoặc `unknown` theo schema, không bịa kết quả. `phase` có thể là `enter`, `target`, `return`, `throw`, `finally`; `outcome` là `success`, `rejected`, `failed`, `unknown`. Định nghĩa schema ổn định để report generator không phụ thuộc format câu log.

### Bước 2 — Gắn invocation correlation

Outer tracer tạo ID và truyền vào các nơi cần thiết. Với synchronous call có thể dùng MDC/ThreadLocal theo lifecycle rõ, nhớ xóa ở `finally`. Với async phải truyền context đúng cơ chế executor/task; không giả định callback quay lại thread caller.

### Bước 3 — Ghi scope proxy và advisor

Report metadata nên ghi target class, pointcut, proxy strategy, danh sách aspects/`@Order` và Spring version. `AopUtils.isAopProxy` là công cụ chẩn đoán hữu ích nhưng **không chứng minh advice cụ thể match**. Có thể inspect `Advised` khi phù hợp, không dùng số lượng advisor làm contract.

### Bước 4 — Phân loại outcome tách biệt

Mỗi invocation nên ghi các flags:

- `proxyEntered`: lời gọi đã vào outer AOP advice?
- `policyRejected`: có branch từ chối?
- `targetReached`: method body đã bắt đầu?
- `methodReturned` hay `methodThrew`: exit path của invocation?
- `asyncCompleted`: chỉ khi đã thực sự quan sát completion stage.
- `traceComplete`: recorder có thu đủ tất cả events kỳ vọng không?

Những giá trị `unknown` cần được giữ nguyên nếu không có evidence.

### Bước 5 — Thêm test hỏng instrumentation

Integration tests ngoài success/deny/boom/self/raw cần có recorder failure, concurrent requests, async completion, retry/cache/transaction advisor, sampling và dropped events. Quan sát không nên vô tình phá semantics nghiệp vụ.

### Bước 6 — Viết báo cáo định kỳ có giới hạn rõ

Một report được review nên chứa:

1. Phạm vi systems/services/beans được instrument.
2. Thời gian đo, version và cấu hình instrumentation.
3. Số invocation, success/reject/failed/unknown, sampling và dropped count.
4. Một số vết tiêu biểu với event order/causality.
5. Thống kê latency với đúng definition và percentiles nếu có số liệu.
6. Điểm mù Spring proxy, async, multi-thread, exporter.
7. Tác động hiệu năng, bảo mật dữ liệu.
8. Hành động đề xuất và regression tests dùng xác minh.

Không công bố “100% lời gọi được trace” trừ khi có inventory và test/telemetry bao phủ toàn bộ phạm vi đã xác định.

## 10. Các nhầm lẫn thường gặp

| Phát biểu dễ sai | Vì sao sai | Cách sửa trong báo cáo |
| --- | --- | --- |
| `@Before` đã chạy nên target chắc chắn chạy | Inner policy có thể short-circuit | Gắn `targetReached` bằng bằng chứng thực |
| Không có advice log nên target không chạy | Raw/self invocation bypass proxy | Đối chứng target instrumentation |
| Outer `@Around` thấy exception nên lỗi từ target | Policy có thể throw trước target | Tách `policyRejected` và `targetFailure` |
| `@Order(10)` chạy trước cả khi vào lẫn khi ra | Outer aspect unwind sau inner | Vẽ nested trace |
| `trace:return` nghĩa async job xong | Có thể chỉ là Future handle | Quan sát completion riêng |
| `isAopProxy=true` nghĩa mọi aspect đều chạy | Proxy có thể do advisor khác | Assert event từng aspect |
| Log events giữa threads theo thứ tự in ra là một chuỗi | Concurrent calls interleave | Group bằng invocation ID |
| Ghi mọi args/exception giúp debug tốt | Lộ secrets, PII, cardinality | Redact và whitelist |
| Event chưa thấy chắc chắn không xảy ra | Sampling, buffer drop, startup race | Ghi confidence/trace gaps |
| Sample một bean đại diện cho cả ứng dụng | Scope chỉ một pointcut | Nêu rõ boundaries |

## 11. Checklist bàn giao báo cáo

- [ ] Đã mô tả Spring version, bean target, method/pointcut và loại proxy.
- [ ] Đã ghi aspect list, `@Order` và cấu hình enable proxy.
- [ ] Có raw event sequence cùng dữ kiện thời gian nếu đã đo.
- [ ] Có case bình thường, bị chặn, target lỗi và bypass proxy.
- [ ] Đã phân biệt proxy entered, target reached, return/throw/finally.
- [ ] Đã xác nhận advice bằng JUnit assertions thay vì chỉ kiểm tra business result.
- [ ] Không dựa vào thứ tự source method hay tên generated proxy.
- [ ] Với Future/`@Async`, có định nghĩa riêng mốc method return và completion.
- [ ] Với transaction/cache/retry, có chú giải vị trí infrastructure advisor.
- [ ] Có correlation theo invocation nếu hệ thống có concurrent threads.
- [ ] Đã đánh giá logging overhead, sampling, drop/backpressure.
- [ ] Có redaction, không ghi secrets vào raw trace.
- [ ] Nêu rõ những trường hợp không được Spring AOP proxy intercept.
- [ ] Không kết luận từ absence of log khi chưa có negative control.
- [ ] Có kịch bản regression test khi nâng Spring.

## 12. Kết luận

Một **báo cáo vết thực thi có giá trị** phải chỉ ra **ai đi qua Spring proxy, aspect nào chạy theo thứ tự nào, target có thực sự được gọi không, kết quả/exception nào trở lại caller và điều gì chưa được quan sát**.

Ví dụ này giải thích bằng bằng chứng cụ thể năm đường đi: `ok`, `deny`, `boom`, `self` và `raw`. Hai aspect `@Order(10)`, `@Order(20)` làm rõ nested interceptor chain; `try/catch/finally` làm rõ outcome; JUnit kiểm chứng từng trace.

**Giới hạn then chốt:** vết Spring AOP không mặc nhiên bao phủ self-invocation, raw objects, constructor/field access, các method ngoài pointcut, Future completion hoặc toàn bộ hệ thống phân tán. Báo cáo tốt luôn công khai boundaries, tránh tự khẳng định phạm vi rộng hơn dữ liệu thực chứng.

## Nguồn Spring chính thức

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — Declaring Advice and Advice Ordering](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html)
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework — Observability Support](https://docs.spring.io/spring-framework/reference/integration/observability.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — Declarative Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html)

Khi áp dụng vào dự án thật, xác minh version, proxy/advisor order, executor/context propagation, instrumentation overhead và policies bảo vệ dữ liệu trên môi trường thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)
