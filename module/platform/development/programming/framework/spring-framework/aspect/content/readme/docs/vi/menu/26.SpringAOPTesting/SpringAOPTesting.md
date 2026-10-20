<a id="back-to-top"></a>

# Kiểm thử Spring AOP

## Menu
- [Phân biệt kiểm thử logic aspect và kiểm thử wiring trong ApplicationContext](#spring-aop-testing-001)
- [Tạo context kiểm thử tối thiểu với bean đích và aspect](#spring-aop-testing-002)
- [Luôn gọi bean proxy khi kiểm chứng advice](#spring-aop-testing-003)
- [Kiểm thử ma trận pointcut: trường hợp khớp và không khớp](#spring-aop-testing-004)
- [Kiểm thử thứ tự advice bằng vết thực thi có kỳ vọng](#spring-aop-testing-005)
- [Kiểm thử giá trị trả về, đối số và ngoại lệ](#spring-aop-testing-006)
- [Kiểm thử self-invocation, final/private và đối tượng tạo ngoài container](#spring-aop-testing-007)
- [Kiểm thử với proxy JDK và proxy CGLIB](#spring-aop-testing-008)
- [Kiểm thử trạng thái aspect khi nhiều luồng cùng gọi](#spring-aop-testing-009)
- [Tương tác giữa mock, spy và proxy trong Spring Test](#spring-aop-testing-010)
- [Đổi cấu hình Java/XML mà vẫn giữ hành vi đã xác nhận](#spring-aop-testing-011)
- [Kiểm thử tích hợp với transaction, async hoặc cache theo tình huống cần dùng](#spring-aop-testing-012)

## <a id="spring-aop-testing-001">Phân biệt kiểm thử logic aspect và kiểm thử wiring trong ApplicationContext</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt kiểm thử logic aspect và kiểm thử wiring trong ApplicationContext

## 1. Hai câu hỏi khác nhau khi kiểm thử Spring AOP

Một aspect trong Spring có **hai phần trách nhiệm cần được kiểm chứng riêng**:

1. **Logic của advice có đúng không?** Ví dụ: khi advice chạy, nó ghi một sự kiện audit đúng nội dung, gọi dịch vụ phụ trợ đúng tham số, xử lý ngoại lệ hợp lý, hoặc trả về đúng giá trị.
2. **Spring có thực sự gắn advice vào đúng lời gọi không?** Ví dụ: aspect được đăng ký thành bean, cấu hình AOP hoạt động, pointcut khớp đúng phương thức và lời gọi đi qua Spring proxy.

Hai phần tương ứng với hai nhóm test:

- **Unit test logic aspect**: khởi tạo đối tượng aspect trực tiếp bằng Java, gọi phương thức advice hoặc logic được advice sử dụng, kiểm tra kết quả và tương tác. **Không cần ApplicationContext**.
- **Integration test wiring AOP**: tạo **Spring ApplicationContext** có cấu hình thật hoặc cấu hình kiểm thử tối thiểu, lấy target bean **từ context**, gọi phương thức nghiệp vụ **qua Spring proxy**, rồi kiểm tra advice có chạy đúng hay không.

Đây là nội dung kiểm thử **Spring Aspect / Spring AOP**, không phải bài giải thích lại các khái niệm AOP tổng quát.

Điểm then chốt:

> **Unit test có thể pass dù Spring không bao giờ chạy advice.** Chỉ integration test qua ApplicationContext mới chứng minh được cấu hình proxy, đăng ký aspect và pointcut đang kết nối đúng với nhau.

Ngược lại, integration test có thể chứng minh advice được kích hoạt nhưng chưa đủ độ chi tiết để phát hiện đầy đủ các nhánh nghiệp vụ phức tạp bên trong advice. Vì thế hai nhóm test **bổ sung cho nhau**.

## 2. So sánh mục tiêu và phạm vi

| Nội dung kiểm tra | Unit test logic aspect | Integration test wiring trong ApplicationContext |
|---|---|---|
| Đối tượng được kiểm tra | Phương thức advice, dịch vụ phụ thuộc, nhánh logic | Spring container, auto-proxy, aspect bean, pointcut, advice, target bean |
| Khởi tạo đối tượng | **new TraceAspect(fakeSink)** hoặc mock phụ thuộc | **@SpringJUnitConfig**, SpringExtension hoặc AnnotationConfigApplicationContext |
| Lời gọi chính | Gọi trực tiếp **aspect.beforePay()** | Gọi **paymentService.pay()** trên bean lấy từ Spring |
| Cần bật **@EnableAspectJAutoProxy**? | Không | Có, hoặc đã có auto-configuration AOP tương đương |
| Cần khớp pointcut? | Không | Có |
| Tạo và kiểm tra Spring proxy? | Không | Có |
| Có phát hiện thiếu **@Aspect** hoặc thiếu aspect bean? | Thường không | Có, nếu kiểm tra kết quả lời gọi thực tế |
| Có phát hiện sai biểu thức **execution(...)**? | Không | Có, nếu test gọi đúng method phải được advice |
| Có phát hiện self-invocation bị bỏ qua proxy? | Không | Có, khi test thiết kế ca gọi nội bộ tương ứng |
| Tốc độ | Nhanh, ít phụ thuộc hạ tầng | Chậm hơn do khởi tạo/cached context |
| Khi thất bại | Thường gợi ý lỗi trong code advice | Thường gợi ý lỗi cấu hình, proxy, pointcut hoặc đường gọi |
| Khuyến nghị | Nhiều ca kiểm tra chi tiết logic | Ít ca nhưng đúng đường đi runtime, có kiểm tra tích hợp |

Một flow thực tế:

~~~text
UNIT TEST
JUnit -> new TraceAspect(fakeSink) -> beforePay()
                                      |
                                      +-> fakeSink.record("before-pay")
                                             |
                                             +-> assert event

Không có Spring context, proxy, pointcut matching.

INTEGRATION TEST
JUnit -> Spring TestContext -> ApplicationContext
                               |
                               +-> AopTestConfig (@EnableAspectJAutoProxy)
                               +-> PaymentService bean
                               +-> TraceAspect bean
                               +-> InMemoryAuditSink bean
                               |
                            getBean(PaymentService.class)
                               |
                               v
                        Spring AOP proxy
                               |
                            pay() matched
                               |
                      TraceAspect.beforePay()
                               |
                         target.pay()
                               |
                     assert advice + target effect
~~~

## 3. Bộ ví dụ Java 17+ dùng chung cho hai loại test

Bộ ví dụ tập trung vào một service thanh toán giả lập. Khi **pay()** được gọi qua Spring AOP proxy, aspect ghi **before-pay** vào sink. Bản thân **PaymentService** tăng bộ đếm số lần gọi. Không kết nối database, network hay external API, nhờ đó kết quả test có thể xác định chính xác.

Ví dụ này sử dụng **Java 17+**, **Spring Framework 6.2.0**, **JUnit Jupiter 5.11.3**, **AspectJ Weaver 1.9.22.1** và Maven.

Cấu trúc:

~~~text
spring-aspect-testing/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── AuditSink.java
    │   ├── InMemoryAuditSink.java
    │   ├── PaymentService.java
    │   ├── TraceAspect.java
    │   └── AopTestConfig.java
    └── test/java/demo/
        ├── TraceAspectUnitTest.java
        └── AopWiringIntegrationTest.java
~~~

### 3.1. Khai báo Maven

Tệp **pom.xml**:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>spring-aspect-testing</artifactId>
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

**spring-context** cung cấp ApplicationContext; **spring-aop** cung cấp cơ chế proxy; **aspectjweaver** cung cấp hạ tầng cần thiết để xử lý biểu thức/annotation @AspectJ; **spring-test** tích hợp JUnit Jupiter với ApplicationContext; **junit-jupiter** là framework assertion và chạy test. Thêm **aspectjweaver** vào dependencies ở đây **không bật AspectJ weaving**: chương trình đang dùng **Spring AOP proxy**.

### 3.2. Các class ứng dụng

**src/main/java/demo/AuditSink.java**

~~~java
package demo;

import java.util.List;

public interface AuditSink {
    void record(String event);
    List<String> events();
}
~~~

**src/main/java/demo/InMemoryAuditSink.java**

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

public class InMemoryAuditSink implements AuditSink {
    private final List<String> recorded = new ArrayList<>();

    @Override
    public void record(String event) {
        recorded.add(event);
    }

    @Override
    public List<String> events() {
        return List.copyOf(recorded);
    }
}
~~~

Bản này chủ ý đơn giản, chỉ dùng trong test chạy tuần tự. Trong sản phẩm thật, audit có thể được chuyển thành một interface gọi logging, metrics hay persistence; khi unit test ta chỉ cần fake/mock interface phù hợp.

**src/main/java/demo/PaymentService.java**

~~~java
package demo;

public class PaymentService {
    private int invocationCount;

    public void pay() {
        invocationCount++;
    }

    public int invocationCount() {
        return invocationCount;
    }
}
~~~

**src/main/java/demo/TraceAspect.java**

~~~java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    private final AuditSink sink;

    public TraceAspect(AuditSink sink) {
        this.sink = sink;
    }

    @Before("execution(* demo.PaymentService.pay(..))")
    public void beforePay() {
        sink.record("before-pay");
    }
}
~~~

Tách **AuditSink** thành dependency qua constructor làm logic advice dễ kiểm tra bằng object Java thông thường. Trong **TraceAspect**, annotation **@Before** xác định binding khi Spring AOP được kích hoạt; nhưng ta vẫn có thể gọi **beforePay()** trực tiếp trong unit test.

**src/main/java/demo/AopTestConfig.java**

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopTestConfig {
    @Bean
    public InMemoryAuditSink auditSink() {
        return new InMemoryAuditSink();
    }

    @Bean
    public PaymentService paymentService() {
        return new PaymentService();
    }

    @Bean
    public TraceAspect traceAspect(InMemoryAuditSink sink) {
        return new TraceAspect(sink);
    }
}
~~~

Trong config này, cả **PaymentService** và **TraceAspect** đều là Spring bean, và **@EnableAspectJAutoProxy** yêu cầu Spring áp dụng pointcut/advice bằng proxy. **proxyTargetClass=true** khiến ví dụ dùng class-based proxy rõ ràng, tránh phụ thuộc vào sự có mặt của interface trên service.

## 4. Unit test: kiểm tra logic của aspect, không tải Spring

**src/test/java/demo/TraceAspectUnitTest.java**

~~~java
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TraceAspectUnitTest {
    @Test
    void beforePayRecordsExpectedEvent() {
        InMemoryAuditSink sink = new InMemoryAuditSink();
        TraceAspect aspect = new TraceAspect(sink);

        assertEquals(List.of(), sink.events());

        aspect.beforePay();

        assertEquals(List.of("before-pay"), sink.events());
    }
}
~~~

**Giải thích:**

1. Không gọi **SpringApplication.run()**, không có **@SpringJUnitConfig**, **@SpringBootTest**, ApplicationContext hay Spring proxy.
2. Tạo **InMemoryAuditSink** và **TraceAspect** qua **new**.
3. Gọi trực tiếp **beforePay()** và xác minh sink lưu đúng sự kiện.
4. Annotation **@Before** không tự được thực thi bởi JUnit. Test này kiểm tra **phần Java trong advice**, không kiểm tra lúc nào Spring sẽ gọi advice đó.

Ca unit test này vẫn **pass** nếu bạn đổi sai biểu thức pointcut thành một method không tồn tại trong dự án, ví dụ **execution(* demo.UnknownService.pay(..))**, bởi không có bộ máy AOP nào diễn giải biểu thức trong phép thử.

Nó cũng pass nếu quên **@EnableAspectJAutoProxy** hoặc quên đăng ký **TraceAspect** thành bean. Đó là giới hạn có chủ đích của phạm vi **unit**; không phải dấu hiệu mọi thứ trong ứng dụng đã được cấu hình đúng.

**Ứng dụng cho advice phức tạp hơn:**

- Với **@Around**, unit test có thể mock/fake **ProceedingJoinPoint**, xác minh **proceed()** được gọi đúng số lần, kết quả được trả về và exception được truyền/biến đổi đúng yêu cầu.
- Với advice nhận **JoinPoint**, có thể mock signature/arguments để kiểm tra logic logging hoặc metadata.
- Với advice đọc đồng hồ hoặc tạo metric, đưa **Clock**, recorder hoặc collaborator qua constructor để kiểm tra kết quả xác định.

Những cách này vẫn chỉ là **kiểm thử code advice**; mock **ProceedingJoinPoint** không chứng minh Spring đã tạo proxy hay pointcut match đúng service.

## 5. Integration test: kiểm tra wiring trong Spring ApplicationContext

**src/test/java/demo/AopWiringIntegrationTest.java**

~~~java
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(AopTestConfig.class)
class AopWiringIntegrationTest {
    @Autowired
    PaymentService paymentService;

    @Autowired
    InMemoryAuditSink sink;

    @Test
    void springProxyActuallyExecutesMatchingAdvice() {
        assertTrue(AopUtils.isAopProxy(paymentService));
        assertEquals(List.of(), sink.events());

        paymentService.pay();

        assertEquals(1, paymentService.invocationCount());
        assertEquals(List.of("before-pay"), sink.events());
    }

    @Test
    void withoutAopInfrastructureAdviceDoesNotRun() {
        try (var context =
                 new AnnotationConfigApplicationContext(NoAopConfig.class)) {
            PaymentService plainBean = context.getBean(PaymentService.class);
            InMemoryAuditSink audit = context.getBean(InMemoryAuditSink.class);

            assertFalse(AopUtils.isAopProxy(plainBean));
            plainBean.pay();

            assertEquals(1, plainBean.invocationCount());
            assertEquals(List.of(), audit.events());
        }
    }

    @Configuration
    static class NoAopConfig {
        @Bean
        InMemoryAuditSink auditSink() {
            return new InMemoryAuditSink();
        }

        @Bean
        PaymentService paymentService() {
            return new PaymentService();
        }

        @Bean
        TraceAspect traceAspect(InMemoryAuditSink sink) {
            return new TraceAspect(sink);
        }
    }
}
~~~

### 5.1. Vì sao đây là integration test?

**@SpringJUnitConfig(AopTestConfig.class)** kết hợp hỗ trợ **SpringExtension** của JUnit Jupiter với **@ContextConfiguration**, nạp Spring TestContext và tạo/cached ApplicationContext theo cấu hình chỉ định.

Những việc được kiểm chứng **cùng lúc**:

1. Spring đọc **AopTestConfig** và xử lý **@EnableAspectJAutoProxy**.
2. Spring đăng ký **TraceAspect**, **PaymentService** và **InMemoryAuditSink** thành bean.
3. Spring nhận diện **@Aspect** và biểu thức **@Before("execution(* demo.PaymentService.pay(..))")**.
4. Spring tạo proxy cho **PaymentService**; **AopUtils.isAopProxy(paymentService)** kiểm tra đối tượng mà test nhận thật sự là AOP proxy.
5. Khi test gọi **paymentService.pay()** qua proxy, advice ghi **before-pay** và target tăng bộ đếm.
6. Assertion kiểm tra **cả side effect của advice lẫn hành vi của target**. Chỉ assert proxy tồn tại chưa đủ, vì proxy có thể không áp dụng advice mình cần.

Phép thử thứ hai tạo **một ApplicationContext độc lập** với bean tương tự nhưng **không bật auto-proxy**. Dù **TraceAspect** vẫn là bean, **plainBean.pay()** chỉ tăng bộ đếm; sink vẫn trống. Đây là bằng chứng cụ thể rằng tồn tại aspect bean **không đồng nghĩa advice đã được wiring**.

Trong test này, **NoAopConfig** là class cấu hình phụ thuộc vào test, không được đưa vào context của **@SpringJUnitConfig(AopTestConfig.class)**. Nhờ đó không có cấu hình auto-proxy của ca đầu vô tình can thiệp vào ca thứ hai.

### 5.2. Chạy test và diễn giải kết quả

Tại thư mục gốc dự án ví dụ:

~~~bash
mvn test
~~~

Maven Surefire chạy **3 test**: một ở **TraceAspectUnitTest** và hai ở **AopWiringIntegrationTest**. Kết quả mong đợi trong phần tổng kết của Maven (bỏ qua thời gian chạy và các log môi trường khác nhau):

~~~text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
~~~

Bạn có thể chạy riêng từng phạm vi:

~~~bash
mvn -Dtest=TraceAspectUnitTest test
mvn -Dtest=AopWiringIntegrationTest test
~~~

Trong dự án hiện thực, số test và format log phụ thuộc cấu hình build, nhưng các assertion và dự kiến kết quả trong ví dụ ở trên là xác định.

**Một phép thử hồi quy rất hữu ích:** tạm bỏ **@EnableAspectJAutoProxy** trong **AopTestConfig** (với dự án này không có Spring Boot tự bật thay). Unit test vẫn pass, trong khi test wiring **springProxyActuallyExecutesMatchingAdvice** sẽ fail vì service không còn được proxy và không ghi event audit. Điều này cho thấy tại sao không thể chỉ dùng unit test để xác nhận Spring AOP đã hoạt động.

## 6. Test đúng đường đi của lời gọi: những sai lầm thường gặp

### 6.1. Tạo service bằng new trong integration test

~~~java
PaymentService service = new PaymentService();
service.pay();
~~~

Đây là lời gọi trực tiếp đến đối tượng Java được tạo ngoài Spring. Nó không đi qua Spring AOP proxy. Muốn kiểm tra wiring, lấy **service** từ ApplicationContext hoặc để **@Autowired** inject bean. Test gọi **new PaymentService()** có thể là unit test nghiệp vụ, nhưng không thể chứng minh advice được Spring áp dụng.

### 6.2. Chỉ verify advice bean tồn tại

~~~java
assertTrue(context.containsBean("traceAspect"));
~~~

Ngay cả khi assertion đó đúng, aspect có thể không được auto-proxy nhận diện, pointcut có thể không match hoặc service có thể đang được gọi ngoài proxy.

Cần test **hành vi quan sát được** sau khi thực sự gọi **service.pay()** trên bean từ context. Kiểm tra **AopUtils.isAopProxy** hữu ích cho chẩn đoán, nhưng không thay thế assertion về effect của advice.

### 6.3. Lấy target gốc từ proxy rồi gọi trực tiếp

Nếu test cố lấy đối tượng bên dưới proxy và gọi thẳng vào target, nó có thể vô tình bỏ qua interception. Spring có **AopTestUtils.getTargetObject(...)** và các hàm liên quan để phục vụ một số trường hợp cần kiểm tra nội bộ, nhưng **không nên dùng target lấy ra thay cho proxy** khi mục tiêu là xác nhận advice được kích hoạt.

Điều tương tự có thể xảy ra nếu mock/spy hoặc thủ thuật reflection thay đổi đối tượng mà Spring đã proxy. Hãy phân biệt rõ đang verify **logic của target**, **logic của advice**, hay **wiring AOP**.

### 6.4. Self-invocation bị hiểu nhầm thành lỗi unit test

~~~java
public class PaymentService {
    public void checkout() {
        pay(); // gọi this.pay(), bỏ qua proxy
    }

    public void pay() {
        // ...
    }
}
~~~

Với Spring AOP **proxy mode**, nếu pointcut chỉ chọn **pay()**, lời gọi **checkout() → this.pay()** không chạy advice cho **pay()** do đường gọi nội bộ bỏ qua proxy.

Vì vậy, một integration test cố ý kiểm tra giới hạn này phải phân biệt:

- **service.pay()** gọi từ ngoài qua bean proxy → advice chạy.
- **service.checkout()** gọi từ ngoài, sau đó target tự gọi **pay()** bên trong → advice riêng cho **pay()** không chạy.

Việc gọi qua một bean Spring khác hoặc thay ranh giới phương thức có thể thay đổi kết quả. Nếu hệ thống dùng **AspectJ weaving** thay vì Spring AOP proxy, cần test theo cơ chế weaving thật; không suy kết luận proxy mode sang weaving.

### 6.5. Kiểm thử **@Around** nhưng không kiểm tra đường đi và ngoại lệ

Ở logic **@Around**, các assertion quan trọng thường là:

- **ProceedingJoinPoint.proceed()** có được gọi khi cần và có được gọi đúng số lần không?
- Advice có giữ nguyên giá trị return, hoặc cố ý biến đổi nó đúng hợp đồng không?
- Khi target ném exception, advice có ghi metric/log phù hợp và propagate/transform exception đúng yêu cầu không?
- Trường hợp lời gọi thành công và thất bại có cùng được test không?

Các branch xử lý này nên kiểm tra trong **unit test** qua fake/mock. Sau đó chỉ cần một số **integration test** có representative method để xác nhận Spring AOP thực sự áp dụng **@Around** vào đúng method.

## 7. Cần xác minh gì trong Spring Boot?

Trong Spring Boot có **spring-boot-starter-aop** và auto-configuration tương ứng, AOP có thể được bật tự động. Vì vậy, khi test Spring Boot, **thiếu @EnableAspectJAutoProxy** không nhất thiết đồng nghĩa advice không chạy; điều này phụ thuộc context được tải và các dependency/điều kiện auto-configuration.

Lựa chọn test theo mục đích:

| Cách test | Khi nên dùng | Điều cần cảnh giác |
|---|---|---|
| Plain JUnit với new aspect | Logic advice độc lập | Không chứng minh Spring proxy |
| **@SpringJUnitConfig(TestAopConfig.class)** | Kiểm thử wiring Spring AOP với context nhỏ, nhanh | Phải đưa vào config cả aspect, target, AOP infrastructure |
| **@SpringBootTest** | Tác động của AOP trên cấu hình Boot thực tế hoặc tương tác nhiều thành phần | Load nhiều bean; dễ chậm hoặc phát sinh dependency không cần thiết |
| Context được tạo bằng **AnnotationConfigApplicationContext** | Test tích hợp rất nhỏ, muốn kiểm soát rõ startup | Tự quản lý vòng đời context, nhớ đóng context |
| Slice/custom context chỉ import một số bean | Xác minh một ranh giới AOP cụ thể | Đừng vô tình loại bỏ auto-configuration/aspect đang cần |

Trong Boot, một bài test **@SpringBootTest** có thể pass nhưng che giấu vấn đề phân phạm vi: target/pointcut được wiring nhờ cấu hình rộng ngoài ý định của module. Vì vậy nên có test context tối thiểu để chứng minh module hoạt động độc lập khi thích hợp, rồi mới bổ sung test tích hợp ở mức ứng dụng.

Nếu sử dụng mock bean để thay service được advice, cần xác minh mock đó được Spring AOP proxy bọc như mong đợi hay đã thay mất đối tượng ở vị trí interception. Cách đáng tin là test một bean thật tối giản cùng fake collaborator bên dưới advice. Không suy ra wiring đúng chỉ từ **Mockito.verify(...)** trên đối tượng mock tự tạo.

## 8. Thiết kế test bền vững và ít flaky

### 8.1. Assert effect thay vì chi tiết proxy không cần thiết

Đừng khóa test vào class proxy sinh ra như **PaymentService$$SpringCGLIB$$...**. Tên runtime có thể thay đổi theo phiên bản/hạ tầng. Tập trung vào **AopUtils.isAopProxy** nếu cần, và nhất là effect của advice sau lời gọi.

Nếu ứng dụng sử dụng JDK interface proxy, type lấy từ context nên là interface phù hợp. Trong ví dụ ta chọn **proxyTargetClass=true** để có thể inject/get **PaymentService** trực tiếp.

### 8.2. Giữ dữ liệu kiểm thử độc lập

**Spring TestContext** có thể cache ApplicationContext giữa test methods/classes dùng cùng cấu hình. Một **InMemoryAuditSink** có trạng thái dùng chung có thể khiến assertion **events()** bị ảnh hưởng bởi thứ tự chạy hoặc chạy song song.

Trong ví dụ, chỉ một test dùng context Spring chính để ghi vào sink, test âm tính tạo context độc lập. Khi bổ sung nhiều ca test, hãy:

- Tạo/reset fixture riêng một cách có kiểm soát.
- Tránh chia sẻ mutable state giữa test có thể chạy song song.
- Dùng **@DirtiesContext** khi thật sự cần context sạch và chấp nhận chi phí; không áp dụng tràn lan nếu có thể cô lập trạng thái đơn giản hơn.
- Không phụ thuộc vào logger console/timestamp hoặc thứ tự log để assert logic nếu có thể kiểm tra collaborator trực tiếp.

### 8.3. Kiểm tra cả ca nên match và không nên match

Một pointcut có thể **quá hẹp** khiến method mong muốn không được advice; cũng có thể **quá rộng** khiến method khác bị intercept. Integration test nên kiểm tra cả hai chiều khi rule có phạm vi đáng kể:

1. Gọi method bắt buộc được advice và assert side effect hiện diện.
2. Gọi method nằm ngoài phạm vi và assert advice **không** chạy.
3. Nếu có quy tắc package/annotation phức tạp, thêm representative class/method đúng boundary.
4. Nếu aspect áp dụng cho nhiều overload, kiểm tra overload cần thiết thay vì chỉ một path dễ nhất.

Đối với transaction/security, assertion nên phản ánh ý nghĩa thật của cơ chế (ví dụ transaction active hay access denied) thay vì chỉ kiểm tra mock advice đã được gọi; các abstraction và điều kiện runtime có thể khác nhau.

### 8.4. Phân biệt lỗi logic với lỗi wiring khi test thất bại

| Dấu hiệu | Nên kiểm tra trước |
|---|---|
| Unit fail, wiring fail | Code advice, dependency hoặc kỳ vọng nghiệp vụ |
| Unit pass, wiring fail | AOP enablement, aspect bean, pointcut, bean proxy, self-invocation |
| **AopUtils.isAopProxy(bean)=false** | Bean có nằm trong context, đúng đối tượng/kiểu và auto-proxy active không |
| Proxy true nhưng sink không có event | Pointcut không khớp, lời gọi không vào method được chọn hoặc effect bị quan sát sai |
| Advice chạy trên nhiều method ngoài ý muốn | Pointcut rộng, annotation/inheritance hoặc cấu hình advisor |
| Test pass riêng, fail khi chạy suite | ApplicationContext cache, state dùng chung, mock setup hoặc parallel execution |
| Unit pass nhưng production khác | Cấu hình Boot/profile/conditional beans, proxy type hoặc startup/runtime khác test |

## 9. Checklist trước khi kết luận Spring Aspect đã được kiểm thử

- [ ] Logic quan trọng trong advice có unit test với input, output, side effect và nhánh lỗi phù hợp.
- [ ] Có integration test với **ApplicationContext** chứa AOP infrastructure, target bean và aspect bean thực tế.
- [ ] Test lấy target bean từ Spring, không tự **new** target trong phép thử wiring.
- [ ] Gọi đúng method cần được advice qua proxy; assert **effect của advice**, không chỉ sự tồn tại của aspect bean.
- [ ] Pointcut được kiểm tra cả phạm vi match và không match nếu cần.
- [ ] Tình huống self-invocation và loại proxy được xử lý đúng với thiết kế.
- [ ] Fixtures không bị ảnh hưởng bởi cache context, thứ tự test hoặc trạng thái dùng chung.
- [ ] Không lẫn test Spring AOP proxy với test AspectJ compile/load-time weaving.
- [ ] Test có thể chạy ổn định và báo lỗi dễ chẩn đoán.

## 10. Tự kiểm tra hiểu bài

**Câu 1.** Unit test gọi **new TraceAspect(sink).beforePay()** có chứng minh **@Before** khớp method pay không?

**Không.** Nó chỉ kiểm tra phần logic Java của advice.

**Câu 2.** **@SpringJUnitConfig** có vai trò gì trong ví dụ?

Nó kết hợp JUnit Jupiter với Spring TestContext để nạp config, dependency injection và context caching; nhờ vậy ta kiểm tra wiring như một phần runtime của Spring.

**Câu 3.** Vì sao không chỉ assert **AopUtils.isAopProxy(service)**?

Vì bean có thể được proxy do một advisor khác hoặc pointcut không khớp **pay()**. Phải gọi method qua bean proxy rồi assert side effect mong đợi.

**Câu 4.** Tại sao test âm tính với **NoAopConfig** vẫn có **TraceAspect** nhưng không có event?

Vì đăng ký **@Aspect** thành bean chưa đủ; không có hạ tầng Spring AOP tạo proxy nên lời gọi tới **PaymentService** không kích hoạt advice.

**Câu 5.** Với **@Around** có nhiều nhánh lỗi, nên đặt logic test và wiring test thế nào?

Unit test kiểm tra các nhánh của advice bằng fake/mock **ProceedingJoinPoint** và collaborators; integration test kiểm tra một số đường gọi đại diện qua proxy để đảm bảo advice thực sự được áp dụng.

## 11. Tài liệu tham khảo

- Spring Framework 6.2 — Unit Testing: https://docs.spring.io/spring-framework/reference/6.2/testing/unit.html
- Spring Framework 6.2 — Integration Testing: https://docs.spring.io/spring-framework/reference/6.2/testing/integration.html
- Spring Framework 6.2 — Spring JUnit Jupiter Testing Annotations: https://docs.spring.io/spring-framework/reference/6.2/testing/annotations/integration-junit-jupiter.html
- Spring Framework — @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — AOP Test Utilities: https://docs.spring.io/spring-framework/reference/testing/unit.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-testing-002">Tạo context kiểm thử tối thiểu với bean đích và aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo context kiểm thử tối thiểu với bean đích và aspect trong Spring AOP

## 1. Vì sao cần một context kiểm thử riêng?

Khi viết `@Aspect` trong Spring, một unit test chỉ dùng `new OrderService()` rồi gọi method **không đủ** chứng minh advice hoạt động. Spring AOP thực thi advice bằng **Spring-generated proxy**: cần bean đích (target), bean aspect, hạ tầng auto-proxy và một lời gọi **đi qua proxy**.

Nhưng nạp toàn bộ Spring Boot application bằng `@SpringBootTest` cho một aspect nhỏ lại có thể kéo theo database, network clients, scheduled jobs, security hoặc những bean không liên quan. Test chạy chậm, dễ fail bởi phụ thuộc ngoài và khó phân biệt pointcut sai với wiring sai.

Bài này xây một **Spring ApplicationContext tối thiểu**, chỉ chứa:

1. Một bean target có method nghiệp vụ cần được advice.
2. Một bean `@Aspect` khai báo `@Around` cho đúng method.
3. Một bean nhỏ để thu thập các sự kiện trước/trong/sau lời gọi, giúp assert thứ tự.
4. Hạ tầng Spring AOP `@EnableAspectJAutoProxy` trong cấu hình test.

Test kiểm tra **Spring AOP integration** thật nhưng không cần chạy toàn bộ ứng dụng, không giả lập `ProceedingJoinPoint` và không tự gọi method advice như một method Java thông thường.

> Phạm vi: kiểm thử **Aspect trong Spring** và Spring proxy boundary. Những kiến thức JUnit/AspectJ tổng quát chỉ đề cập ở mức cần thiết để xây context và chứng minh advice thực sự chạy.

## 2. Bốn điều kiện để advice chạy trong Spring

Đối với Spring Framework 6.x và cấu hình Java thông thường:

- **Target phải là Spring bean**: bean được tạo/đăng ký trong context đang kiểm thử. Object `new` riêng bên ngoài không tự được Spring AOP proxy.
- **Aspect phải là Spring bean**: class có `@Aspect` vẫn phải được Spring đăng ký, ví dụ bằng `@Bean` hoặc `@Component` + component scanning. Chỉ viết `@Aspect` không đủ để container thấy nó.
- **Phải bật `@AspectJ` auto-proxy support**: `@EnableAspectJAutoProxy` trong `@Configuration` hoặc `<aop:aspectj-autoproxy/>` ở XML.
- **Test phải lấy và gọi proxy từ context**: `context.getBean(OrderOperations.class)`, không gọi trực tiếp target nằm ngoài proxy hoặc tự gọi method advice.

Khi dùng `@AspectJ` pointcut expressions, Maven cần thư viện `org.aspectj:aspectjweaver` trên classpath. Tên `@AspectJ` ở đây nói tới **cú pháp khai báo aspect**, còn cơ chế Spring đang kiểm thử là **proxy-based Spring AOP**, không phải AspectJ compile-time hoặc load-time weaving.

Dòng xử lý mong đợi:

~~~text
JUnit test
  |
  +-- tạo AnnotationConfigApplicationContext(OnlyAopConfig.class)
  |      |
  |      +-- @Bean InvocationLog
  |      +-- @Bean OrderService (target)
  |      +-- @Bean AuditAspect
  |      +-- @EnableAspectJAutoProxy -> advisor + auto-proxy infrastructure
  |
  +-- context.getBean(OrderOperations.class)
  |      |
  |      +-- Spring JDK AOP proxy
  |              |
  |              +-- AuditAspect @Around (nếu pointcut match)
  |              +-- OrderService target
  |
  +-- assert proxy, return value, event order, non-match và exception
  |
  +-- context.close()
~~~

**Điều kiện quan trọng nhất để test có ý nghĩa:** target gọi trong test là **bean reference do Spring trả ra**, không phải một object `new` mà test giữ sẵn. `AopUtils.isAopProxy(bean)` xác nhận có proxy; sau đó phải assert **đúng hiệu ứng advice** vì việc có proxy không có nghĩa mọi method đều match.

## 3. Vì sao AnnotationConfigApplicationContext là lựa chọn gọn?

`AnnotationConfigApplicationContext` là Spring context dùng Java `@Configuration`, `@Bean`, `@Import` và annotation-based bean registration. Nó phù hợp cho test nhỏ không cần Spring Boot runtime.

~~~java
try (var context =
        new AnnotationConfigApplicationContext(OnlyAopConfig.class)) {
    OrderOperations bean = context.getBean(OrderOperations.class);
    // Assert AOP behavior.
}
~~~

Đây là snippet minh họa; `OnlyAopConfig` và `OrderOperations` được định nghĩa trong project mẫu bên dưới. Dùng `try-with-resources` đảm bảo context đóng sau test, tránh singleton/test state rò giữa các trường hợp và hạn chế resource leak.

So với cách nạp toàn app:

| Cách kiểm thử | Phạm vi Spring beans | Phù hợp khi | Lưu ý |
| --- | --- | --- | --- |
| `new OrderService(...)` | Không có Spring context | Test business logic thuần | Không chứng minh Spring AOP advice chạy |
| `AnnotationConfigApplicationContext(TestConfig.class)` | Chỉ beans mà cấu hình test đăng ký | Aspect/pointcut/proxy test độc lập, nhanh | Test phải tự mở/đóng context |
| `@SpringJUnitConfig(TestConfig.class)` | Cấu hình test chỉ định; Spring TestContext quản lý | Cần JUnit 5 + DI test instance + context cache | State mutable trong bean được cache cần reset |
| `@SpringBootTest` | ApplicationContext Boot rộng tùy cấu hình | Integration test nhiều auto-configurations | Dễ nạp nhiều bean không liên quan |
| Mock `ProceedingJoinPoint`, gọi thẳng advice method | Không nhất thiết có proxy | Test helper logic bên trong advice riêng | Không test pointcut matching hoặc Spring proxy chain |

Không bắt buộc dùng `@SpringBootTest`, `@SpringJUnitConfig` hay `@ComponentScan` cho một test Spring AOP tối thiểu. Điều cốt lõi là **đúng những beans và auto-proxy infrastructure cần thiết**.

## 4. Ví dụ hoàn chỉnh chạy bằng Java 17, Maven và JUnit 5

Project dùng Spring Framework **6.1.15**, Java **17**, AspectJ Weaver **1.9.22.1** và JUnit Jupiter **5.10.2**. Đầu ra kiểm thử được assert bằng `List<String>`, không dựa vào console hoặc timing.

Các tiêu chí kiểm thử:

- `submit("A-1")` qua proxy: ghi `before`, `target`, `after` đúng thứ tự và giữ nguyên kết quả.
- `status()` không match pointcut: chỉ chạy target method, không ghi `before/after`.
- `submit("FAIL")` qua proxy: advice ghi `before` và `error`, exception nghiệp vụ đi ra caller.
- Nếu đăng ký `@Aspect` nhưng **quên `@EnableAspectJAutoProxy`**, không có Spring AOP proxy và advice không chạy.

### 4.1. Cấu trúc thư mục

~~~text
spring-aop-minimal-context-test/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── OrderOperations.java
    │   ├── OrderService.java
    │   ├── InvocationLog.java
    │   └── AuditAspect.java
    └── test/java/demo/
        └── MinimalAopContextTest.java
~~~

### 4.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>spring-aop-minimal-context-test</artifactId>
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
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` tạo `AnnotationConfigApplicationContext`; `spring-aop` chứa proxy infrastructure, `AopUtils`; `aspectjweaver` giúp parse/match pointcut annotations; `junit-jupiter` và `maven-surefire-plugin` chạy tests. Ví dụ không cần Spring Boot, không cần database, không cần HTTP server hay AspectJ weaving agent.

### 4.3. Interface nghiệp vụ

**src/main/java/demo/OrderOperations.java**

~~~java
package demo;

public interface OrderOperations {
    String submit(String id);
    String status();
}
~~~

Có interface giúp ví dụ thể hiện mặc định **JDK dynamic proxy** khi `@EnableAspectJAutoProxy(proxyTargetClass = false)`. Caller dùng `OrderOperations`, không ép proxy sang class `OrderService`.

### 4.4. Recorder dùng chung giữa target và aspect

**src/main/java/demo/InvocationLog.java**

~~~java
package demo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InvocationLog {
    private final List<String> events = new CopyOnWriteArrayList<>();

    public void add(String value) {
        events.add(value);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }
}
~~~

Dùng `List.copyOf` để snapshot các sự kiện test theo thứ tự, tránh test phụ thuộc thời gian thực. `CopyOnWriteArrayList` khiến ghi log thread-safe nếu về sau test có concurrency; trong ví dụ mỗi method test chạy tuần tự trên một context riêng nên cũng có thể dùng một collection đơn giản.

Đây là **test seam cho việc xác minh advice**, không phải một loại tracing framework mà Spring AOP cung cấp sẵn. Trong production, interceptor có thể dùng logging, metrics hoặc domain events; test nên assert hành vi có ý nghĩa, tránh assert output console dễ bị logger ảnh hưởng.

### 4.5. Target service

**src/main/java/demo/OrderService.java**

~~~java
package demo;

public class OrderService implements OrderOperations {
    private final InvocationLog log;

    public OrderService(InvocationLog log) {
        this.log = log;
    }

    @Override
    public String submit(String id) {
        log.add("target:" + id);

        if ("FAIL".equals(id)) {
            throw new IllegalArgumentException("rejected:" + id);
        }

        return "ok:" + id;
    }

    @Override
    public String status() {
        log.add("target:status");
        return "UP";
    }
}
~~~

Target này **không** có `@Component` hoặc `@Service`: Spring context test sẽ đăng ký nó bằng một `@Bean` rõ ràng. Điều này tránh component scanning kéo vào nhiều service khác và cho phép nhìn ngay instance nào là target.

`submit("FAIL")` cố tình ném lỗi để test chứng minh `@Around` không nuốt exception. `status()` là method âm tính: target vẫn thực thi nhưng không nên vào pointcut `submit`.

### 4.6. Aspect cần kiểm thử

**src/main/java/demo/AuditAspect.java**

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class AuditAspect {
    private final InvocationLog log;

    public AuditAspect(InvocationLog log) {
        this.log = log;
    }

    @Around("execution(* demo.OrderService.submit(..))")
    public Object observeSubmit(ProceedingJoinPoint joinPoint)
            throws Throwable {
        String id = String.valueOf(joinPoint.getArgs()[0]);
        log.add("before:" + id);

        try {
            Object result = joinPoint.proceed();
            log.add("after:" + id);
            return result;
        } catch (Throwable error) {
            log.add("error:" + id);
            throw error;
        }
    }
}
~~~

Những điểm quyết định:

- `@Aspect` đánh dấu class là aspect theo cú pháp @AspectJ mà Spring hỗ trợ.
- `@Around("execution(* demo.OrderService.submit(..))")` match method `submit` của **target type** trong Spring AOP. Nó không match method `status`.
- `joinPoint.proceed()` chuyển lời gọi vào các advisors tiếp theo và target. Nếu bỏ `proceed()`, target không chạy.
- Aspect trả lại **result nguyên bản** và ném lại **exception nguyên bản**, không làm lệch business contract.
- Recorder được inject vào cả aspect và service từ **cùng một ApplicationContext**, nên test thấy trật tự `before → target → after` hoặc `before → target → error`.
- `AuditAspect` không có `@Component`: test config **đăng ký trực tiếp** aspect dưới dạng một Spring bean bằng `@Bean`. Thêm `@Aspect` đơn độc không khiến component scanner tự phát hiện class.

### 4.7. Test context tối thiểu và bốn JUnit tests

**src/test/java/demo/MinimalAopContextTest.java**

~~~java
package demo;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.junit.jupiter.api.Assertions.*;

class MinimalAopContextTest {

    // Context chính: đúng target + aspect + recorder + auto-proxy.
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    static class OnlyAopConfig {

        @Bean
        InvocationLog invocationLog() {
            return new InvocationLog();
        }

        @Bean
        OrderOperations orderService(InvocationLog log) {
            return new OrderService(log);
        }

        @Bean
        AuditAspect auditAspect(InvocationLog log) {
            return new AuditAspect(log);
        }
    }

    // Context đối chứng: có @Aspect bean nhưng KHÔNG bật auto-proxy.
    @Configuration(proxyBeanMethods = false)
    static class MissingEnableConfig {

        @Bean
        InvocationLog invocationLog() {
            return new InvocationLog();
        }

        @Bean
        OrderOperations orderService(InvocationLog log) {
            return new OrderService(log);
        }

        @Bean
        AuditAspect auditAspect(InvocationLog log) {
            return new AuditAspect(log);
        }
    }

    @Test
    void adviceRunsAroundMatchingTargetMethod() {
        try (var context =
                 new AnnotationConfigApplicationContext(OnlyAopConfig.class)) {
            OrderOperations service = context.getBean(OrderOperations.class);
            InvocationLog log = context.getBean(InvocationLog.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertTrue(AopUtils.isJdkDynamicProxy(service));
            assertFalse(service instanceof OrderService);

            assertEquals("ok:A-1", service.submit("A-1"));
            assertEquals(
                List.of("before:A-1", "target:A-1", "after:A-1"),
                log.snapshot()
            );
        }
    }

    @Test
    void nonMatchingMethodRunsWithoutAuditAdvice() {
        try (var context =
                 new AnnotationConfigApplicationContext(OnlyAopConfig.class)) {
            OrderOperations service = context.getBean(OrderOperations.class);
            InvocationLog log = context.getBean(InvocationLog.class);

            assertEquals("UP", service.status());
            assertEquals(List.of("target:status"), log.snapshot());
        }
    }

    @Test
    void aroundAdviceRethrowsBusinessException() {
        try (var context =
                 new AnnotationConfigApplicationContext(OnlyAopConfig.class)) {
            OrderOperations service = context.getBean(OrderOperations.class);
            InvocationLog log = context.getBean(InvocationLog.class);

            IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> service.submit("FAIL")
            );

            assertEquals("rejected:FAIL", thrown.getMessage());
            assertEquals(
                List.of("before:FAIL", "target:FAIL", "error:FAIL"),
                log.snapshot()
            );
        }
    }

    @Test
    void aspectBeanWithoutAutoProxyDoesNotApplyAdvice() {
        try (var context =
                 new AnnotationConfigApplicationContext(MissingEnableConfig.class)) {
            OrderOperations service = context.getBean(OrderOperations.class);
            InvocationLog log = context.getBean(InvocationLog.class);

            assertFalse(AopUtils.isAopProxy(service));
            assertEquals("ok:RAW", service.submit("RAW"));
            assertEquals(List.of("target:RAW"), log.snapshot());
        }
    }
}
~~~

Chỉ một `@Configuration` được chọn **cho mỗi context**. `OnlyAopConfig` bật auto-proxy; `MissingEnableConfig` cố ý không bật. Hai config không được đồng thời import vào cùng test context, vì sẽ có bean names trùng và làm đối chứng mất ý nghĩa.

**Tại sao khai báo `@Bean OrderOperations orderService(...)`?** Interface `OrderOperations` là contract bên ngoài; target thực tế vẫn là `OrderService`. Spring AOP xét đúng target class khi match `execution(* demo.OrderService.submit(..))`, rồi có thể tạo JDK proxy triển khai `OrderOperations`. Phương thức lấy `context.getBean(OrderOperations.class)` vì thế không phụ thuộc vào class sinh ra của proxy.

**Tại sao đặt config trong test class?** Đây là static nested `@Configuration` chỉ dùng bởi test; nó không biến thành cấu hình chung của application production. Không có `@ComponentScan` nên chỉ ba bean đã chỉ định cùng Spring infrastructure do `@EnableAspectJAutoProxy` đăng ký được sử dụng.

**Tại sao mỗi test tự mở context?** Recorder là mutable. Tạo context mới trong mỗi test giúp test độc lập, không cần reset counter/log và không bị Spring TestContext cache tái sử dụng state. Sau khi `try` kết thúc, Spring đóng context và hủy các singleton mà nó quản lý theo lifecycle thông thường.

### 4.8. Chạy bộ test

Tại thư mục có `pom.xml`:

~~~bash
mvn -q test
~~~

Nếu mọi assertion đúng, Maven Surefire hoàn tất với **exit code 0**; với flag `-q`, console có thể rất ít hoặc không có output. Không cố coi những dòng log của Maven là stdout bất biến.

Để xem báo cáo từng class:

~~~text
target/surefire-reports/demo.MinimalAopContextTest.txt
~~~

Các chỉ số mong đợi từ logic của source test:

~~~text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
~~~

Dòng summary có thể đi kèm thông tin thời gian và tên class tùy phiên bản Surefire. Đây là **kỳ vọng cho project demo khi compile và tests chạy thành công**, không phải số liệu benchmark hay kết quả thực thi được đo trên production.

### 4.9. Ma trận test và nguyên nhân

| Test | Context | Đường gọi | Kỳ vọng |
| --- | --- | --- | --- |
| `adviceRunsAroundMatchingTargetMethod` | Target + aspect + auto-proxy | `service.submit("A-1")` qua proxy | Proxy là Spring JDK proxy; events `before,target,after`; return giữ nguyên |
| `nonMatchingMethodRunsWithoutAuditAdvice` | Tương tự | `service.status()` qua proxy | Chỉ event `target:status`; không ghi advice |
| `aroundAdviceRethrowsBusinessException` | Tương tự | `service.submit("FAIL")` qua proxy | Events `before,target,error`; caller nhận `IllegalArgumentException` |
| `aspectBeanWithoutAutoProxyDoesNotApplyAdvice` | Target + aspect **không** enable AOP | `service.submit("RAW")` | Không phải AOP proxy; chỉ event target |

Điều được test không chỉ là method aspect được gọi. Bộ test chứng minh rằng **Spring context đã đăng ký advisor, đã đưa proxy cho caller, pointcut match đúng và advice giữ đúng return/exception semantics**.

## 5. Cách nhận diện một context tối thiểu *thực sự* tối thiểu

Cấu hình này không có:

- `@SpringBootApplication` hoặc scan nguyên package ứng dụng.
- Web server, HTTP controller, repositories thật hoặc remote clients.
- Database và transaction manager nếu pointcut chỉ nhằm test custom logging aspect.
- AspectJ compiler hoặc `-javaagent`: Spring @AspectJ-style **proxy** không đòi weaving trong ví dụ.
- Mock quá nhiều collaborator khi có thể dùng một recorder đơn giản.
- Một `@Aspect` bean khác có pointcut chồng chéo làm các assertion khó đọc.

Có thể thêm mock bean nếu target service bắt buộc gọi một dependency, nhưng chỉ mock những dependency **ở ranh giới target**, không mock Spring's AOP proxy machinery. Một test Spring AOP integration không được thay `OrderOperations` bean bằng một Mockito mock rồi kết luận aspect cũng sẽ chạy trên mock đó theo cùng cách như target thật.

**Giữ riêng test service thuần và test Spring aspect.** Test service thuần xác minh tính toán nghiệp vụ; test context tối thiểu xác minh wiring/pointcut/proxy semantics. Hai loại test bổ sung cho nhau, không thay thế nhau.

## 6. Khi nào dùng @SpringJUnitConfig thay cho mở context thủ công?

Nếu dự án đã dùng `spring-test`, Spring TestContext Framework và JUnit Jupiter, có thể để Spring quản lý ApplicationContext:

~~~java
@SpringJUnitConfig(OnlyAopConfig.class)
class AspectWithSpringTestContext {

    @Autowired
    OrderOperations service;

    @Autowired
    InvocationLog log;

    @Test
    void matchingCallRunsAdvice() {
        assertEquals("ok:A-1", service.submit("A-1"));
        assertEquals(
            List.of("before:A-1", "target:A-1", "after:A-1"),
            log.snapshot()
        );
    }
}
~~~

Đây là **snippet minh họa**, không phải file độc lập của demo mục 4. Nó cần imports `org.springframework.test.context.junit.jupiter.SpringJUnitConfig`, `org.springframework.beans.factory.annotation.Autowired`, JUnit 5, `OnlyAopConfig` truy cập được từ test class và dependency thêm:

~~~xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-test</artifactId>
    <version>6.1.15</version>
    <scope>test</scope>
</dependency>
~~~

`@SpringJUnitConfig` là shortcut phối hợp Spring JUnit 5 extension với `@ContextConfiguration`; không tự kích hoạt AOP nếu config bạn đưa vào không có `@EnableAspectJAutoProxy`/hạ tầng tương đương.

**Lưu ý về cache:** Spring TestContext có thể cache application context theo cấu hình giữa nhiều test methods/classes. Nếu recorder là singleton mutable, hãy reset trong `@BeforeEach`, thiết kế collector/fixture cách ly hoặc dùng `@DirtiesContext` khi thực sự cần thay đổi context. Không dùng `@DirtiesContext` đại trà chỉ vì muốn xóa một `List`; làm thế tăng chi phí khởi tạo Spring context.

Ở bài học này, tạo `AnnotationConfigApplicationContext` thủ công vẫn là cách rõ ràng nhất để **thấy chính xác bean đích, bean aspect, proxy và thời điểm đóng context**.

## 7. Cách chọn giữa JDK proxy và CGLIB trong test

Ví dụ chính dùng:

~~~java
@EnableAspectJAutoProxy(proxyTargetClass = false)
~~~

và target implement `OrderOperations`, nên Spring Framework 6.x thông thường tạo **JDK dynamic proxy**. Assertions có thể kiểm tra:

~~~java
assertTrue(AopUtils.isAopProxy(service));
assertTrue(AopUtils.isJdkDynamicProxy(service));
~~~

Nếu muốn test **class-based proxy**, đổi thành:

~~~java
@EnableAspectJAutoProxy(proxyTargetClass = true)
~~~

và đảm bảo target class có thể subclass. Khi ấy `AopUtils.isCglibProxy(bean)` có thể đúng. Nhưng **đó là một cấu hình test khác**: không vừa yêu cầu CGLIB vừa giữ assertion `isJdkDynamicProxy=true` từ ví dụ chính.

Đừng đoán proxy mode dựa vào Spring Boot defaults ở production; trong context Java test độc lập, cấu hình bạn chọn điều khiển hành vi. Nếu test đang xác minh **hành vi aspect**, kiểm tra `isAopProxy` và trace trước/sau thường có giá trị hơn hard-code loại JDK/CGLIB trừ khi chính loại proxy là yêu cầu cần kiểm thử.

Hạn chế class-based proxy vẫn áp dụng: final class không được subclass, final/private methods không được advice qua override, và module/package visibility có thể làm proxy creation thất bại. Không bật `proxyTargetClass=true` để chữa lỗi `this.method()` bypass Spring proxy.

## 8. Viết pointcut đủ chính xác cho test

Trong ví dụ:

~~~java
@Around("execution(* demo.OrderService.submit(..))")
~~~

điểm cắt rõ class và method. Nó không phụ thuộc annotation của class `App`, cũng không phụ thuộc bean name được chọn khi @Bean. Với một service cụ thể, kiểu biểu thức này dễ kiểm soát positive/negative coverage.

Có thể dùng các designators Spring hỗ trợ theo từng nhu cầu:

~~~java
@Around("execution(* demo..*Service.submit(..))")
~~~

Match methods tương ứng các class tên theo pattern; có thể quá rộng nếu package chứa nhiều services.

~~~java
@Around("bean(orderService) && execution(* *.submit(..))")
~~~

`bean(...)` là **Spring AOP pointcut designator** đặc thù cho bean name, hữu ích khi cần giới hạn bean cụ thể; nó không phải một cú pháp AspectJ CTW chung. Tại đây `orderService` phải đúng tên bean Spring. Hai đoạn trên là **các biến thể thay thế**, không phải bổ sung tự động vào demo chính.

Nếu đổi target method name từ `submit` sang `place` mà quên đổi pointcut, Spring vẫn có thể tạo context bình thường nhưng aspect không chạy cho method mong đợi. Một **test âm tính** trên `status` và một **test dương tính** trên `submit` giúp phát hiện pointcut quá rộng hoặc quá hẹp.

Không dùng `@Before("execution(* *(..))")` quá rộng khi cần chứng minh chỉ một method được advice: nó có thể match infrastructure/method ngoài ý định và làm recorder khó hiểu.

## 9. Vì sao gọi thẳng method trên aspect không phải integration test?

Ví dụ sau **không** đủ để test Spring AOP:

~~~java
var aspect = new AuditAspect(log);
aspect.observeSubmit(mockJoinPoint);
~~~

Đây là snippet minh họa cần `log` và một `ProceedingJoinPoint` mock. Nó chỉ kiểm tra Java method `observeSubmit` có thể thao tác trên mock, không kiểm chứng:

- Spring context có phát hiện/register aspect bean không.
- `@EnableAspectJAutoProxy` có hoạt động không.
- Spring có tạo proxy cho target bean không.
- `execution(...)` có match method implementation thực sự không.
- Thứ tự advisors so với target thực tế.
- Return value hoặc exception được giữ nguyên qua Spring interceptor chain không.

Có thể tách helper logic ra khỏi aspect để unit-test riêng mà không cần Spring, nhưng **vẫn phải có một Spring integration test** gọi target qua proxy cho phần AOP.

Tương tự:

~~~java
OrderService target = new OrderService(log);
target.submit("A-1");
~~~

chỉ chạy business method trên raw object. Nếu lời gọi đó không thấy `before/after`, đây là hành vi bình thường, không phải dấu hiệu `@Aspect` bị lỗi.

## 10. Negative control: bỏ từng mảnh hạ tầng để hiểu lỗi

Một kỹ thuật dạy và debug hiệu quả là tạo đối chứng có chủ đích, **mỗi lần chỉ bỏ một yếu tố**.

| Thành phần bị thiếu | Kết quả có thể quan sát | Điều phải kiểm tra |
| --- | --- | --- |
| Không đăng ký target bean | `getBean(OrderOperations.class)` lỗi hoặc không có target | Bean definition/method `@Bean` |
| Không đăng ký aspect bean | Context có target nhưng không có advisor của aspect | Aspect `@Bean`/component scan |
| Không bật `@EnableAspectJAutoProxy` | `@Aspect` bean vẫn có thể tồn tại nhưng không tạo advisor/proxy từ aspect | Enable AOP/config scope |
| Thiếu `aspectjweaver` trên classpath | Context có thể fail khi xây @AspectJ infrastructure hoặc parse expressions | Dependency đúng |
| Gọi raw target | Aspect không chạy dù context đúng | Lấy object từ `getBean` thay vì `new` |
| Pointcut không match | Có thể bean không được AOP proxy hoặc proxy không chạy advice đó | Kiểm tra target class, method signature, type/bean pattern |
| Dùng JDK proxy nhưng ép thành concrete `OrderService` | `ClassCastException` | Inject theo interface hoặc đổi proxy strategy có chủ đích |
| Tạo context nhưng quên đóng | Tài nguyên/threads trong các beans có thể bị giữ | `try-with-resources`, TestContext quản lý lifecycle |

**Lưu ý khi assert proxy:** Nếu chỉ có một aspect, mà pointcut không match bất kỳ method nào của target, Spring có thể **không tạo proxy** cho bean đó; khi ấy `isAopProxy=false` là kết quả có thể hiểu được. Ngược lại, nếu có advisor khác match, bean có thể là proxy dù aspect đang kiểm thử không match. Bởi vậy chỉ dùng `isAopProxy` là chưa đủ; phải kiểm tra **events cụ thể của advice**.

## 11. Khi target có dependencies thì tối giản như thế nào?

Một service thường gọi repository/client. Với mục tiêu chỉ test AOP, thay dependency nặng bằng bean giả nhỏ do test cung cấp:

~~~java
@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy
class LocalAopConfig {

    @Bean
    OrderRepository orderRepository() {
        return new InMemoryOrderRepository();
    }

    @Bean
    OrderOperations orderService(OrderRepository repository) {
        return new OrderService(repository);
    }

    @Bean
    AuditAspect auditAspect(InvocationLog log) {
        return new AuditAspect(log);
    }

    @Bean
    InvocationLog invocationLog() {
        return new InvocationLog();
    }
}
~~~

Đây là **snippet thiết kế thay thế**: `OrderRepository`, `InMemoryOrderRepository` và constructor `OrderService(OrderRepository)` cần được định nghĩa; chúng **không** thuộc project chạy đầy đủ ở mục 4. Điểm cần học là giữ Spring bean **target thật**, còn những collaborators không liên quan đến pointcut có thể là fake/stub.

Nếu pointcut dựa vào `@annotation` gắn ở method target, đừng thay target bằng mock object có type khác khi test; mock có thể khiến Spring nhìn thấy method/class metadata khác và advisor không match như production. Hãy để Spring proxy một target class phù hợp rồi stub các phụ thuộc của target.

Với `@Transactional` hoặc `@Cacheable`, test integration có thể cần bổ sung transaction manager/cache manager thật hoặc test substitutes phù hợp để advice có thể chạy chính xác. **Không** giả định context tối thiểu cho logging aspect cũng đủ để test mọi tính năng Spring có advisor.

## 12. Tránh các lỗi về self-invocation và vòng đời proxy

### Self-invocation

~~~java
public class OrderService implements OrderOperations {
    public void outer() {
        submit("A-1"); // gọi trên this, không qua Spring AOP proxy.
    }
}
~~~

Đây là snippet minh họa, không là bản thay thế hợp lệ cho toàn bộ `OrderService` ở mục 4. Nếu method `outer()` được gọi qua proxy rồi gọi `this.submit(...)` bên trong target, aspect `submit` thường **không** intercept lời gọi nội bộ. Test muốn xác minh lời gọi `submit` bị advice thì phải **gọi trực tiếp `service.submit(...)` trên proxy**.

Đổi `proxyTargetClass=true` không loại bỏ self-invocation limitation của Spring AOP proxy. Nếu cần advice trên method nội bộ, ưu tiên thiết kế tách Spring beans để lời gọi đi qua injected proxy.

### Lifecycle và state cô lập

Aspect trong ví dụ là Spring singleton bean có `InvocationLog` riêng theo **mỗi context**. Trong test thực tế:

- Dùng `try-with-resources` để đóng `AnnotationConfigApplicationContext`.
- Nếu dùng Spring TestContext cache, reset state thay vì tin rằng mỗi `@Test` có một aspect instance mới.
- Không giữ static mutable counter trong aspect trừ khi test reset rõ.
- Không phụ thuộc thứ tự tests (`@TestMethodOrder`) để tạo một trạng thái tích lũy.
- Không đặt concurrency/timing assertions không ổn định khi không test concurrency.
- Không tự gọi `context.refresh()` nhiều lần trên cùng `AnnotationConfigApplicationContext` đã refresh/closed; tạo context mới cho test khác.

### Nếu aspect hoặc target có constructor side effects

Test context chỉ nên tạo những beans thiết yếu. Nếu constructor target gọi network hoặc mở resource nặng, hãy refactor để dependency bên ngoài được inject qua fake. Nếu pointcut phụ thuộc runtime annotations hay transaction metadata, giữ đúng method/bean metadata mà production dùng; tránh làm setup “nhẹ” đến mức không còn cùng cơ chế Spring AOP.

## 13. Chẩn đoán khi test thấy advice không chạy

Đi theo thứ tự, đừng đổi annotation ngẫu nhiên:

1. **Kiểm tra `OnlyAopConfig` đã được đăng ký:** đặt breakpoint hoặc test `context.containsBean("orderService")` và `context.containsBean("auditAspect")`.
2. **Kiểm tra aspect bean:** `context.getBean(AuditAspect.class)` phải tồn tại. Nếu chỉ có `@Aspect` class mà không có `@Bean`/`@Component`, context không tự tạo nó.
3. **Kiểm tra enable:** `@EnableAspectJAutoProxy` nằm trong config **được nạp vào chính context test**, không phải ở một config production chưa được import.
4. **Kiểm tra AspectJ dependency:** `aspectjweaver` phải có trên classpath cho Spring parse `@Around` pointcut.
5. **Kiểm tra object caller:** `AopUtils.isAopProxy(service)` có đúng không? Caller nhận từ `context.getBean` hay dùng một instance khác?
6. **Kiểm tra proxy type:** nếu JDK proxy, lấy bean theo interface `OrderOperations`; không ép sang `OrderService`.
7. **Kiểm tra biểu thức:** `execution(* demo.OrderService.submit(..))` có match target method? Xem package/FQCN/overloads/final và method visibility.
8. **Kiểm tra advice method:** `ProceedingJoinPoint.proceed()` có được gọi không? Có exception được nuốt hay result bị thay?
9. **Kiểm tra negative case:** `status()` không nên ghi `before/after`; nếu có, pointcut đang quá rộng hoặc advisor khác đang ghi events.
10. **Kiểm tra điều kiện đặc biệt:** self-invocation, bean scope, final class/method, proxy nesting, annotation ở interface so với implementation class.
11. **Nếu test khác cũng can thiệp:** loại component scan/autoconfiguration không cần, chỉ giữ config mục 4 để cô lập root cause.
12. **Nếu chạy với Spring TestContext:** kiểm tra state singleton/recorder của context cache, khác biệt so với context do từng test tự tạo.

Một đoạn quan sát nhẹ:

~~~java
Object bean = context.getBean(OrderOperations.class);
System.out.println("aop=" + AopUtils.isAopProxy(bean));
System.out.println("jdk=" + AopUtils.isJdkDynamicProxy(bean));
System.out.println("cglib=" + AopUtils.isCglibProxy(bean));
System.out.println("target=" + AopUtils.getTargetClass(bean).getName());
~~~

Đây là snippet trong test method khi đã có `context`; không dùng tên class generated proxy làm assertion cố định, vì tên class phụ thuộc JDK/Spring version và classloader.

Nếu proxy expose `Advised`, có thể xem `getAdvisors()`:

~~~java
if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor);
    }
}
~~~

Nhưng **danh sách advisors không chứng minh advice đã chạy** trên một method: cần gọi method qua proxy và assert kết quả/hành vi như bốn tests.

## 14. Những hiểu lầm thường gặp

| Hiểu lầm | Vì sao không chính xác | Cách làm đúng |
| --- | --- | --- |
| Có `@Aspect` là Spring tự áp advice | Aspect phải được đăng ký thành bean và có auto-proxy infrastructure | `@Bean AuditAspect` + `@EnableAspectJAutoProxy` |
| Chỉ `@EnableAspectJAutoProxy` là đủ | Không có aspect bean thì không có advisor tương ứng | Nạp target và aspect trong cùng context |
| Cần `@SpringBootTest` cho mọi aspect | Có thể nạp rất nhiều bean không liên quan | Context Java nhỏ với đúng ba beans |
| `new OrderService(...)` đủ để test aspect | `new` bypass Spring proxy | Gọi `context.getBean(OrderOperations.class)` |
| Test gọi `aspect.observeSubmit(mockJoinPoint)` là pointcut test | Mock không kiểm tra Spring matching/proxy | Test thực thi method target qua Spring proxy |
| `AopUtils.isAopProxy=true` nghĩa mọi method được advice | Pointcut có thể chỉ match `submit` | Test method dương tính và âm tính |
| Aspect làm thay đổi kết quả dù không có ý định | `@Around` có thể thay result/exception | Assert return và rethrow semantics |
| JDK proxy có thể ép sang concrete class | Proxy chỉ implement interface | Inject theo interface hoặc đổi strategy có chủ đích |
| CGLIB proxy tự sửa self-invocation | Spring AOP vẫn là proxy-based | Tách beans/gọi qua proxy |
| Aspect được scan từ một package rộng sẽ không gây tác dụng phụ | Extra aspects có thể ảnh hưởng advisors/order | Đăng ký test aspect bằng `@Bean` rõ ràng |
| Context sẽ tự reset recorder giữa `@Test` | Singleton state có thể được cache/reuse | Context riêng hoặc reset state |
| `@SpringJUnitConfig` tự bật AOP | Chỉ nạp Spring TestContext, không thay cấu hình AOP | Import config có `@EnableAspectJAutoProxy` |
| Pointcut ở interface và implementation luôn xử lý giống nhau | Proxy mode/metadata có thể ảnh hưởng cách Spring xét method | Test trên target type và proxy mode thực tế |
| Maven exit code 0 có nghĩa mọi use case AOP đã đúng | Chỉ đúng những assertions đã viết | Test cả matched/non-matched/error và proxy identity |

## 15. Checklist để mở rộng từ context tối thiểu sang test thực tế

Một mẫu tốt để tái sử dụng:

1. Viết **một config riêng** cho aspect cần kiểm thử, dùng `@Configuration(proxyBeanMethods=false)`.
2. Bật `@EnableAspectJAutoProxy` theo proxy strategy có ý định.
3. Đăng ký **một target bean thực** qua `@Bean`.
4. Đăng ký **aspect bean thực** qua `@Bean`; không dùng aspect mock làm bằng chứng auto-proxying.
5. Đăng ký những fake collaborators rất nhỏ khi service không thể chạy độc lập.
6. Lấy service qua `ApplicationContext`, assert `AopUtils.isAopProxy` khi dự kiến có proxy.
7. Assert một method **match** pointcut và một method **không match**.
8. Assert advice giữ đúng result, exception và thứ tự khi `proceed()`.
9. Tạo một negative control (bỏ enable hoặc bỏ aspect bean) để phân biệt framework wiring với Java logic.
10. Đóng context hoặc quản lý TestContext cache và test state rõ ràng.
11. Khi đưa aspect vào production, kiểm tra thêm bean visibility, package/annotation pattern, proxy strategy và các advisors khác; test nhỏ không thay thế integration test toàn hệ thống.
12. Đừng dùng test Spring AOP proxy để suy diễn behavior của AspectJ compile-time/load-time weaving; đó là cơ chế khác và cần cách test khác.

## 16. Tổng kết

Một **Spring AOP context kiểm thử tối thiểu** không cần toàn bộ Spring Boot runtime. Chỉ cần bean đích, aspect bean, `@EnableAspectJAutoProxy` và những collaborators cực nhỏ; khi dùng @AspectJ expressions phải có `aspectjweaver` trên classpath. Test lấy bean thông qua `AnnotationConfigApplicationContext`, kiểm tra proxy bằng `AopUtils`, rồi **gọi method qua proxy và assert advice thật**.

Trong ví dụ, bốn JUnit 5 tests bao phủ đường thành công, method không match, exception và tình huống aspect bean hiện hữu nhưng auto-proxy chưa bật. Từ đó có thể mở rộng sang `@SpringJUnitConfig` hoặc Spring Boot integration test, nhưng phải giữ một test nhỏ, độc lập để xác định ranh giới chính xác nơi Spring AOP advice bắt đầu và kết thúc.

### Tài liệu Spring chính thức

- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html).
- [Spring Framework — Declaring an Aspect](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/at-aspectj.html).
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html).
- [Spring Framework — AnnotationConfigApplicationContext Javadoc](https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/context/annotation/AnnotationConfigApplicationContext.html).
- [Spring Framework — Testing with the Spring TestContext Framework](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework.html).
- [Spring Framework — SpringJUnitConfig Javadoc](https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/test/context/junit/jupiter/SpringJUnitConfig.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-testing-003">Luôn gọi bean proxy khi kiểm chứng advice</a>

<details>
<summary>Xem chi tiết</summary>

# Luôn gọi bean proxy khi kiểm chứng advice trong Spring AOP

## 1. Nguyên tắc kiểm thử không được bỏ qua

**Khi muốn xác minh advice của Spring AOP có chạy hay không, phải gọi phương thức qua đúng bean proxy mà Spring cung cấp.** Tự `new` service, gọi trực tiếp raw target, hoặc dùng một reference trỏ thẳng vào target thường **bỏ qua toàn bộ Spring AOP proxy**, làm test báo sai rằng advice không chạy.

Ví dụ:

~~~java
// Minh họa ý tưởng, chưa phải chương trình độc lập.
WorkService raw = new WorkServiceImpl();
raw.process("A");  // Gọi trực tiếp target: không đi qua Spring AOP proxy.

WorkService bean = context.getBean(WorkService.class);
bean.process("A"); // Gọi bean Spring; nếu được advice, đây là proxy.
~~~

Điều cần kiểm tra không chỉ là method trả đúng kết quả, mà còn:

1. Đối tượng được gọi **có thực sự là Spring AOP proxy** không?
2. Pointcut có match **đúng method thực thi** không?
3. Advice trước/sau/lỗi có chạy đúng nhánh và số lần không?
4. Khi gọi raw target, advice có thực sự **không chạy** (negative control) không?
5. Những đường đi như **self-invocation**, method không thể proxy, object tự tạo hoặc lời gọi qua interface khác có đang làm test sai kết luận không?

Bài tập trung vào **kiểm thử cơ chế advice trong Spring Framework AOP**. Kiến thức AOP tổng quát chỉ nhắc vừa đủ để tổ chức test. Không đánh đồng proxy của Spring AOP với AspectJ compile-time/load-time weaving: AspectJ weaving thực sự có thể advice một object không thông qua proxy, nhưng đó là cơ chế khác.

## 2. Vì sao raw target và bean proxy cho kết quả khác nhau?

Spring AOP là **proxy-based**. Client giữ một object đại diện (proxy). Khi client gọi method qua proxy, Spring chạy interceptor/advice chain rồi mới gọi target:

~~~text
Cách đúng khi test Spring AOP:

JUnit test / client
      |
      v
Spring ApplicationContext.getBean(...)
      |
      v
Spring AOP proxy
      |
      +--> @Before / @Around / @AfterReturning / @AfterThrowing
      |
      v
target.method()
      |
      v
proxy trả kết quả hoặc lỗi

Cách sai nếu mục tiêu là test advice:

JUnit test
      |
      v
new Target() / raw target reference
      |
      v
target.method()       (không đi qua proxy, không có Spring AOP advice)
~~~

`@Aspect`, `@Before`, `@Around` có mặt trên class và `@EnableAspectJAutoProxy` có trong application chưa đủ để chứng minh một lời gọi cụ thể có advice. Test phải đi qua **proxy đúng instance**, có **pointcut khớp**, và assert **hành vi** advice.

### Những nguồn reference thường gặp

| Cách lấy service | Có đi qua Spring AOP proxy không? | Ý nghĩa khi test |
| --- | --- | --- |
| `context.getBean(WorkService.class)` | Có nếu Spring đã tạo AOP proxy cho bean đó | Lựa chọn đáng tin để integration test advice |
| `@Autowired WorkService service` từ Spring TestContext | Có nếu dependency chính là bean proxy | Tương đương hướng ứng dụng thực gọi |
| Bean nhận qua constructor injection trong một Spring bean khác | Thường là proxy nếu dependency đã được auto-proxy | Test luồng thực, bảo đảm bean đang ở trong context |
| `new WorkServiceImpl()` | **Không** | Chỉ test logic target, không kiểm chứng Spring advice |
| `Mockito.mock(WorkService.class)` | **Không tự động** | Chỉ test mock behavior; không suy ra advice hoạt động |
| `AopTestUtils.getTargetObject(proxy)` hoặc unwrap trực tiếp target | **Không**, nếu đem target ra gọi | Dùng để kiểm tra state nội bộ khi thật cần, không dùng làm đầu vào chính để test advice |
| `this.process()` bên trong target | Thường **không quay lại proxy** | Self-invocation bypass Spring AOP advice |
| `ProxyFactory.getProxy()` đã được cấu hình thủ công | Có, nhưng chỉ đối với advisors bạn tự gắn | Hữu ích cho test AOP API thuần; khác với auto-proxy integration test |

**Quan trọng:** `context.getBean(...)` **không bảo đảm bean nào cũng là proxy**. Chỉ những bean Spring quyết định áp advice mới được proxy; cần assert kiểu proxy và outcome để phân biệt “bean đúng” với “proxy đúng”.

## 3. Chọn phép thử theo **reference được gọi**

| Reference đang gọi | Câu hỏi có thể kết luận |
| --- | --- |
| `new WorkServiceImpl()` (raw target) | Logic Java của service đúng không? **Không** chứng minh Spring advice chạy |
| Gọi trực tiếp method trên `@Aspect` hoặc mock `ProceedingJoinPoint` | Logic bên trong advice có đúng không? **Không** chứng minh pointcut/wiring |
| Bean `WorkService` lấy từ `ApplicationContext` với AOP đã bật | Spring có tạo proxy, chọn đúng method và thực thi advice không? Đây là **trọng tâm của bài** |
| Bean do `@SpringBootTest` cung cấp | Hành vi khi tích hợp nhiều auto-configuration/advisor của ứng dụng có đúng không? Chỉ nạp full context khi cần |

Trong Spring Framework thuần, test wiring có thể dùng `@SpringJUnitConfig` hoặc `@ContextConfiguration` cùng `SpringExtension`; đăng ký cả target bean, aspect bean và `@EnableAspectJAutoProxy`. Nếu dùng Spring Boot, xác định auto-configuration/proxy strategy thực tế thay vì giả định. Bài **phân biệt logic aspect với wiring** phân tích sâu các tầng test; tại đây, kiểm chứng quan trọng nhất là **chính reference được gọi có đi qua Spring proxy hay không**.

## 4. Ví dụ Java 17+ chạy được: JUnit 5 và Spring TestContext

Ví dụ dưới đây cố ý dùng **JDK dynamic proxy** vì service implement interface. Một `@Aspect` ghi lại các sự kiện advice vào probe riêng. Test sẽ so sánh **proxied method call**, **raw instance**, **self-invocation** và **exception path**.

Cấu trúc:

~~~text
spring-aop-proxy-test/
├── settings.gradle
├── build.gradle
└── src
    ├── main/java/demo
    │   ├── WorkService.java
    │   ├── WorkServiceImpl.java
    │   ├── InvocationProbe.java
    │   ├── RecordingAspect.java
    │   └── AopTestConfig.java
    └── test/java/demo
        └── WorkServiceAopTest.java
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-aop-proxy-test'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation platform('org.springframework:spring-framework-bom:6.2.14')
    implementation 'org.springframework:spring-context'
    implementation 'org.springframework:spring-aop'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'

    testImplementation 'org.springframework:spring-test'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.11.4'
}

test {
    useJUnitPlatform()
}
~~~

`src/main/java/demo/WorkService.java`:

~~~java
package demo;

public interface WorkService {
    String process(String input);

    String outer(String input);
}
~~~

`src/main/java/demo/WorkServiceImpl.java`:

~~~java
package demo;

public final class WorkServiceImpl implements WorkService {

    @Override
    public String process(String input) {
        if ("fail".equals(input)) {
            throw new IllegalArgumentException("bad input");
        }
        return "ok:" + input;
    }

    @Override
    public String outer(String input) {
        // Lời gọi nội bộ: this.process(), không đi qua AOP proxy.
        return this.process(input);
    }
}
~~~

`src/main/java/demo/InvocationProbe.java`:

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

public final class InvocationProbe {
    private final List<String> events = new ArrayList<>();

    public void add(String event) {
        events.add(event);
    }

    public List<String> events() {
        return List.copyOf(events);
    }

    public void reset() {
        events.clear();
    }
}
~~~

`src/main/java/demo/RecordingAspect.java`:

~~~java
package demo;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class RecordingAspect {
    private final InvocationProbe probe;

    public RecordingAspect(InvocationProbe probe) {
        this.probe = probe;
    }

    @Before(
        "execution(* demo.WorkServiceImpl.process(..)) && args(input)"
    )
    public void before(String input) {
        probe.add("before:" + input);
    }

    @AfterReturning(
        pointcut = "execution(* demo.WorkServiceImpl.process(..))",
        returning = "result"
    )
    public void afterReturning(String result) {
        probe.add("after:" + result);
    }

    @AfterThrowing(
        pointcut = "execution(* demo.WorkServiceImpl.process(..))",
        throwing = "error"
    )
    public void afterThrowing(Throwable error) {
        probe.add("error:" + error.getMessage());
    }
}
~~~

`src/main/java/demo/AopTestConfig.java`:

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AopTestConfig {

    @Bean
    public InvocationProbe invocationProbe() {
        return new InvocationProbe();
    }

    @Bean
    public WorkService workService() {
        return new WorkServiceImpl();
    }

    @Bean
    public RecordingAspect recordingAspect(InvocationProbe probe) {
        return new RecordingAspect(probe);
    }
}
~~~

`src/test/java/demo/WorkServiceAopTest.java`:

~~~java
package demo;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(AopTestConfig.class)
class WorkServiceAopTest {
    @Autowired
    private WorkService service;

    @Autowired
    private InvocationProbe probe;

    @Autowired
    private ApplicationContext context;

    @BeforeEach
    void clearProbe() {
        probe.reset();
    }

    @Test
    void injectedServiceMustBeSpringAopProxy() {
        assertSame(service, context.getBean(WorkService.class));
        assertTrue(AopUtils.isAopProxy(service));
        assertTrue(AopUtils.isJdkDynamicProxy(service));
    }

    @Test
    void proxyCallTriggersBeforeAndAfterReturningAdvice() {
        assertEquals("ok:A", service.process("A"));
        assertEquals(
            List.of("before:A", "after:ok:A"),
            probe.events()
        );
    }

    @Test
    void directlyConstructedTargetDoesNotTriggerSpringAdvice() {
        WorkService raw = new WorkServiceImpl();
        assertFalse(AopUtils.isAopProxy(raw));
        assertEquals("ok:B", raw.process("B"));
        assertEquals(List.of(), probe.events());
    }

    @Test
    void selfInvocationInsideTargetBypassesProcessAdvice() {
        assertEquals("ok:C", service.outer("C"));
        assertEquals(List.of(), probe.events());

        // Đối chứng: cũng process("C"), nhưng gọi qua proxy.
        assertEquals("ok:C", service.process("C"));
        assertEquals(
            List.of("before:C", "after:ok:C"),
            probe.events()
        );
    }

    @Test
    void proxyFailureTriggersBeforeAndAfterThrowing() {
        IllegalArgumentException thrown = assertThrows(
            IllegalArgumentException.class,
            () -> service.process("fail")
        );
        assertEquals("bad input", thrown.getMessage());
        assertEquals(
            List.of("before:fail", "error:bad input"),
            probe.events()
        );
    }
}
~~~

**Chạy test:**

~~~bash
gradle test
~~~

Nếu đã có Gradle Wrapper, chạy `./gradlew test` trên Linux/macOS hoặc `gradlew.bat test` trên Windows.

**Kết quả mong đợi:** Gradle báo `BUILD SUCCESSFUL`, cả **5 test** xanh. Gradle thường không in chi tiết `System.out` của mỗi test theo mặc định; ở đây test dùng assertions thay vì dựa vào log. Ví dụ chưa được thực thi trong môi trường riêng ở bài viết này, nên đây là kết quả **dự kiến từ code và cấu hình**, không phải kết quả một lần chạy đã ghi nhận.

### Vì sao các test có ý nghĩa?

1. `@SpringJUnitConfig(AopTestConfig.class)` tích hợp Spring TestContext với JUnit Jupiter, khởi tạo context chứa service, probe và `RecordingAspect`.
2. `@EnableAspectJAutoProxy` làm Spring tạo proxy khi advisor từ `RecordingAspect` match `WorkServiceImpl.process`. `proxyTargetClass = false` kết hợp `WorkService` interface giúp ví dụ dùng JDK proxy. `WorkServiceImpl` là `final` nhưng điều đó không ngăn JDK interface proxy.
3. Inject `WorkService service` đưa **bean proxy** vào test. `context.getBean(WorkService.class)` trả cùng bean singleton. `AopUtils.isAopProxy(service)` xác nhận proxy, nhưng **chưa đủ** nếu không gọi method và assert probe.
4. `service.process("A")` qua proxy: `@Before` ghi `before:A`, method trả `ok:A`, `@AfterReturning` ghi `after:ok:A`. Test chứng minh đúng chuỗi sự kiện Spring advice.
5. `new WorkServiceImpl().process("B")` trả kết quả nghiệp vụ tương tự nhưng probe vẫn rỗng: đây là **negative control** quan trọng chứng minh raw instance không được Spring advice.
6. `service.outer("C")` vào proxy theo method `outer`, nhưng `outer` gọi `this.process("C")` ngay trên target. Vì không đi qua proxy tại lần gọi nội bộ, advice chọn `process` không chạy. Khi test tiếp tục gọi `service.process("C")` bên ngoài, probe lại có sự kiện.
7. Exception từ `service.process("fail")` đi qua proxy: `@Before` và `@AfterThrowing` chạy, còn `@AfterReturning` không ghi success. Assertion xác nhận nhánh lỗi, không chỉ nhánh thành công.

**Điểm đáng chú ý:** dù đang dùng `@Aspect` và AspectJ annotations, ví dụ này vẫn là **Spring AOP proxy**, không phải AspectJ compiler/weaving. Có thư viện `aspectjweaver` để Spring phân tích pointcut, không có `-javaagent` hay `ajc`.

## 5. Không chỉ assert isAopProxy — cần assert advice thật sự chạy

Một test kiểu sau **cần thiết nhưng chưa đủ**:

~~~java
assertTrue(AopUtils.isAopProxy(service));
~~~

Vì bean có thể được proxy bởi transaction/security/cache hoặc advisor khác, việc nó là AOP proxy **không chứng minh advice của bạn đã match method cần test**. Trường hợp có proxy nhưng pointcut sai vẫn có thể cho `isAopProxy=true`.

Thêm assertion về **tác dụng có thể quan sát**:

- Probe/in-memory recorder đếm số lần advice chạy, tham số, return value, exception.
- Fake collaborator được inject vào aspect để ghi những event dễ assert; đừng chỉ assert service output.
- `@Before` phải chạy trước method; `@AfterReturning` chỉ chạy nếu method return bình thường; `@AfterThrowing` chỉ chạy nếu lỗi bị ném ra đúng join point.
- Kiểm tra một method match và một method **không match**, để tránh aspect vô tình áp quá rộng.
- Nếu test cần thứ tự advice, chỉ assert thứ tự có hợp đồng rõ; Spring advice ordering liên quan `@Order`, nhiều advisor và loại advice.
- Nếu dùng `@Around`, phải kiểm tra nhánh `proceed()`, nhánh return, nhánh throw và không làm hỏng outcome của caller.

Ví dụ test kiểm tra số lần:

~~~java
// Fragment minh họa khi probe đã có phương thức phù hợp:
service.process("A");
service.process("B");
assertEquals(4, probe.events().size()); // 2 calls × before/after
~~~

Việc `isAopProxy` là true **không thay thế** hai lời gọi và assertion này.

### AopUtils giúp gì?

Các hàm quan trọng trong Spring `org.springframework.aop.support.AopUtils`:

| API | Ý nghĩa trong test |
| --- | --- |
| `AopUtils.isAopProxy(object)` | Có phải Spring AOP proxy không |
| `AopUtils.isJdkDynamicProxy(object)` | Có phải proxy dựa trên JDK interface không |
| `AopUtils.isCglibProxy(object)` | Có phải proxy dựa trên subclass CGLIB không |
| `AopUtils.getTargetClass(object)` | Xác định target class từ một object/proxy có thể unwrap metadata |

Có thể dùng `Advised` để kiểm tra danh sách advisor trên proxy Spring được expose đúng interface hạ tầng, nhưng không nên viết test chỉ dựa vào số lượng advisors vì infrastructure có thể chèn advisor khác. Test nên ưu tiên **đi qua proxy và kiểm chứng hành vi**.

## 6. JDK proxy hay CGLIB: kiểm tra đúng kiểu reference

Spring AOP có hai cơ chế proxy chính:

### JDK dynamic proxy

Khi target implement interface và không buộc class-based proxy, Spring AOP có thể dùng JDK proxy. Proxy implement các interface cần thiết nhưng **không phải subclass của concrete target class**.

Vì vậy trong ví dụ:

~~~java
WorkService bean = context.getBean(WorkService.class);
// Đúng: làm việc qua interface mà proxy expose.
bean.process("A");

// Không nên giả định:
WorkServiceImpl concrete = (WorkServiceImpl) bean;
// Có thể ClassCastException nếu bean là JDK proxy.
~~~

Đoạn cast concrete trên là **mã không hợp lệ với JDK proxy được kỳ vọng**, chỉ nhằm giải thích sai lầm. Trong integration test, nên inject interface `WorkService` và kiểm tra `AopUtils.isJdkDynamicProxy` nếu cấu hình cố ý dùng JDK proxy.

### CGLIB class-based proxy

Với class-based proxy, Spring tạo subclass của class target, nên test có thể lấy bean qua concrete type nếu target có thể subclass/proxy phù hợp. Nhưng điều đó **không đồng nghĩa mọi method** sẽ được advice:

- `final` class không thể được subclass bằng CGLIB.
- `final` method không thể bị override để intercept theo cách proxy này.
- `private` method không phải method có thể override, nên không được proxy như public method.
- Method không có khả năng override phù hợp cũng có thể không được advice.
- Self-invocation vào method trên `this` vẫn bypass proxy.

**Không ép CGLIB chỉ để test cast concrete class.** Chọn kiểu proxy phản ánh production và dùng đúng interface được public.

### Spring Boot có thể khác cấu hình thuần Spring Framework

Boot có thể mặc định chọn class-based proxies tùy version/settings và auto-configuration. Trong test, tránh assert cứng `isJdkDynamicProxy` nếu môi trường production không cam kết dùng JDK proxy. Trong ví dụ mục 4, ta **chủ đích** cấu hình `proxyTargetClass = false` trong Spring Framework thuần, nên test proxy type có cơ sở.

## 7. Self-invocation: gọi bean proxy ở ngoài cũng chưa đủ cho method bên trong

Một trường hợp khó phát hiện:

~~~java
public class AccountService {
    public void outer() {
        this.inner(); // Gọi target method trực tiếp.
    }

    @Audited
    public void inner() {
        // ...
    }
}
~~~

Đây là fragment minh họa một annotation custom `@Audited`, không phải chương trình độc lập. Giả sử advice chỉ match `inner`. Khi caller gọi `accountProxy.outer()`, đoạn `outer` thực thi trên target và `this.inner()` cũng gọi trên target, nên `inner` **không qua proxy lần thứ hai**. Advice chọn `inner` có thể không chạy.

Cách viết test đúng:

1. Lấy bean proxy qua context.
2. Gọi **trực tiếp `inner` từ ngoài proxy** và chứng minh advice chạy.
3. Gọi `outer` và chứng minh đường self-invocation **không** kích hoạt advice `inner`.
4. Sau khi xác định vấn đề, thiết kế lại luồng production để external call đi qua proxy (ví dụ tách `inner` sang Spring bean khác được inject), thay vì chỉ sửa test cho xanh.

Không nên dùng `AopContext.currentProxy()` làm “mẹo” mặc định: nó yêu cầu expose proxy và khiến code ứng dụng phụ thuộc vào Spring AOP. Tách bean theo trách nhiệm thường rõ ràng hơn.

Cũng cần phân biệt Spring proxy với **AspectJ weaving thực sự**: khi bytecode được weave, lời gọi nội bộ có thể được AspectJ advice, nhưng phạm vi đó nằm ngoài test Spring AOP proxy thông thường.

## 8. Vì sao @Autowired không đủ để bảo đảm test đúng?

`@Autowired` chỉ đảm bảo Spring tìm và inject một dependency theo config; nó không tự đảm bảo dependency:

- là proxy, nếu chưa bật auto-proxy;
- được aspect mong muốn áp advice;
- đang match pointcut của method được test;
- có cùng proxy strategy (JDK/CGLIB) với production;
- không bị thay bằng test double/mock khiến advice không còn match;
- được gọi qua proxy ở mọi đường đi.

Vì vậy **test contract đầy đủ** là:

~~~text
Load context
 → bean exists
 → bean is AOP proxy (khi mong đợi)
 → invoke method on that proxy
 → assert result/exception
 → assert advice side effect exactly
 → negative control raw object or non-matching method
~~~

Nếu aspect có `@Component` nhưng test sử dụng `@ContextConfiguration` chỉ import service config mà **không scan/include aspect**, bean có thể được tạo mà không có advice. Một test trả kết quả nghiệp vụ đúng vẫn xanh trong khi concern AOP hoàn toàn vắng mặt. Ví dụ mục 4 khai báo `RecordingAspect` trong config để tránh tình huống đó.

### Cạm bẫy Mockito

Mockito mock chỉ mô phỏng method call của mock. Gọi `mock.process()` và kiểm tra `verify(mock).process()` **không** chứng minh Spring advice chạy. Nếu Spring test thay bean target bằng mock (`@MockBean` ở một số Spring Boot version hoặc `@MockitoBean` trong Spring Test mới), cấu trúc proxy và pointcut có thể thay đổi so với target thật.

Khi mục tiêu là **kiểm chứng advice**, hãy dùng target thật hoặc test double được đăng ký **bên dưới một proxy Spring thật**, và assert advice độc lập. Khi mục tiêu chỉ là unit test service, mock là bình thường.

### Cạm bẫy AopTestUtils

Spring Test cung cấp `AopTestUtils.getTargetObject(...)` và `getUltimateTargetObject(...)` để kiểm tra raw target khi cần. Nếu dùng object unwrap được để gọi method trong test advice thì **đã chủ động bỏ qua proxy**. Với nested/dynamic proxies, unwrap còn có thể có vấn đề lifecycle; tránh lấy target để gọi tùy tiện.

### Cạm bẫy reflection / method handle

Nếu reflection thực thi method trên **proxy object** theo method proxy expose, Spring advice vẫn có thể chạy. Nếu reflection dùng instance raw target hoặc phương thức được gọi bên ngoài đường proxy, sẽ không có Spring AOP advice. Reflection không tự biến raw object thành proxy.

## 9. Các trường hợp kiểm thử ngoài đường thành công

| Tình huống | Assertion cần có |
| --- | --- |
| Method match pointcut, trả bình thường | `@Before` và `@AfterReturning` chạy đúng |
| Method match pointcut, ném exception | `@Before` và `@AfterThrowing` chạy; `@AfterReturning` không ghi success |
| Method không match | Advice không chạy |
| `new Target()` | Logic target có thể chạy, advice không chạy |
| Self-invocation | Advice trên method nội bộ không tự chạy |
| Bean không bật auto-proxy | Không suy luận “có @Aspect nên chắc chắn advice chạy” |
| JDK proxy | Test inject/cast qua interface được proxy expose |
| CGLIB proxy | Method/class phải có khả năng subclass/override phù hợp |
| Nhiều advisor | Test thứ tự có chủ đích, không chỉ số lượng |
| Method trả `CompletableFuture` | Method return và Future hoàn tất có thể là hai thời điểm |
| `@Async` | Invocation có thể chuyển sang worker, assert completion thay vì timing đoán mò |
| `@Transactional` | Bảo đảm test không rollback/commit giả tạo vì target bị gọi raw |
| Proxy xuất hiện qua Spring scoped bean | Kiểm tra scope/lifecycle và đường đi qua proxy thực |
| Method trên object được tạo ngoài Spring | Không kỳ vọng Spring AOP intercept nếu chưa được proxy |

## 10. Trường hợp cùng bean nhiều tầng proxy

Trong ứng dụng có `@Transactional`, caching, security, async, metrics hoặc nhiều aspects, object có thể là proxy chứa nhiều advisors hoặc proxy lồng. Điều quan trọng không phải tự gọi `getTargetSource().getTarget()` để “kiểm tra sâu”, mà là **giữ nguyên reference từ context rồi invoke qua nó**.

Nếu cần chẩn đoán:

~~~java
// Fragment: bean đã được lấy từ Spring context.
System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.getTargetClass(bean));
~~~

Có thể kiểm tra `Advised`/advisors trong test hạ tầng riêng, nhưng không assert cứng số lượng hoặc class name của proxy như một contract nghiệp vụ trừ khi cấu hình đã cam kết cụ thể. Đổi JDK proxy sang CGLIB không nhất thiết làm advice sai; test nên phát hiện **behavioral regression**, không phải thay đổi chi tiết implementation không liên quan.

### Scope và target source

Proxy có thể ổn định trong khi target thay đổi theo scope hoặc `TargetSource`. Nếu unwrap một target từ scoped/dynamic proxy rồi gọi trực tiếp, bạn có thể bỏ qua advice, tạo resource leak hoặc tác động sai scope. Test Spring AOP nên đi qua proxy và để target source điều khiển acquisition/release.

## 11. Dùng raw target như đối chứng cho một proxy test

Hai lời gọi cùng một đầu vào có thể cho **kết quả nghiệp vụ giống nhau nhưng khác vết advice**:

~~~java
WorkService raw = new WorkServiceImpl();
assertEquals("ok:A", raw.process("A")); // Chỉ chạy business method.

WorkService proxied = context.getBean(WorkService.class);
assertTrue(AopUtils.isAopProxy(proxied));
assertEquals("ok:A", proxied.process("A"));
assertEquals(List.of("before:A", "after:ok:A"), probe.events());
~~~

Đây là đoạn minh họa dùng `context` và `probe` từ project chính; cần gọi **proxy Spring** trong assert thứ hai. Đối chứng không chỉ nhằm chứng minh advice vắng mặt khi gọi raw object: nó giúp phát hiện test vô tình giữ **target reference thay vì proxy reference**, ngay cả khi method vẫn trả giá trị đúng.

## 12. Những lỗi cấu hình dễ khiến test không đi qua proxy

### Quên bật Spring AOP auto-proxy

Chỉ khai báo `@Aspect` class rồi chạy `new WorkServiceImpl()` sẽ không sinh AOP proxy. Với Java config Spring thuần, cần `@EnableAspectJAutoProxy`, aspect bean và target bean trong cùng context phù hợp. Với Boot, xác minh auto-configuration/dependency/config thật sự bật Spring AOP.

### Aspect không phải Spring bean

Một class chỉ có `@Aspect` nhưng không được đăng ký qua `@Bean`, component scan hoặc cách khác sẽ không tự trở thành advice trong context. Ví dụ mục 4 đăng ký `@Bean RecordingAspect` tường minh.

### Pointcut không match implementation

`execution(...)`, `within(...)`, `this(...)` hoặc `target(...)` có thể match khác nhau theo JDK/CGLIB proxy và kiểu target. Khi test thất bại, in `AopUtils.getTargetClass(bean)` và kiểm tra method signature, package, visibility, annotations và pointcut expression. Đừng chỉnh pointcut mù để vừa test; phải khớp yêu cầu production.

### Kiểu bean sai khi lấy từ context

Trong JDK proxy, lấy bean bằng concrete class `WorkServiceImpl.class` có thể không tương thích vì proxy chỉ implement interface. Lấy qua `WorkService.class` hoặc một interface phù hợp.

### Test tự gọi `new` trong setup

Cấu hình sau không kiểm chứng AOP:

~~~java
@BeforeEach
void setUp() {
    service = new WorkServiceImpl(); // Mất proxy mà Spring tạo.
}
~~~

Nếu cần context, dùng `@SpringJUnitConfig`, `@Autowired WorkService service` hoặc `context.getBean(...)`. Đừng gán đè field được inject bằng raw instance.

### Chỉ assert return value, không assert advice

Return value của `raw.process("A")` và `proxy.process("A")` có thể giống nhau. Nếu test chỉ assert `"ok:A"` nó không phát hiện advice bị vô hiệu hóa hoặc mất pointcut. Thêm probe/assert chính xác.

### Không reset state giữa test

Spring TestContext có thể cache `ApplicationContext` giữa các test. Một singleton probe chứa event từ test trước sẽ làm test phụ thuộc thứ tự. Ví dụ dùng `@BeforeEach probe.reset()`. Nếu chạy test parallel, dùng thread-safe recorder hoặc thiết kế context/probe per-test. Đừng dựa vào thứ tự method tests chạy.

## 13. Quy trình debug khi “advice không chạy”

1. **Xác minh test gọi đúng object:** bean do `context.getBean()` hoặc `@Autowired` cung cấp, không phải `new`/unwrap.
2. **Kiểm tra proxy:** `AopUtils.isAopProxy(bean)`; khi `false`, kiểm tra Spring AOP đã được bật và aspect bean có mặt chưa.
3. **Kiểm tra kiểu proxy:** JDK hay CGLIB; class/final/private method có nằm ngoài khả năng proxy không?
4. **Kiểm tra pointcut:** package, class thực, method, args, annotation; đừng chỉ nhìn method interface.
5. **Kiểm tra self-invocation:** nếu bên trong target có `this.method()`, lời gọi đó không đi qua proxy.
6. **Kiểm tra thứ tự advisors:** khi có transaction/async/cache/security, có advisor khác chặn hoặc thay đổi đường thực thi không?
7. **Kiểm tra exception path:** `@AfterReturning` không chạy nếu method throw; `@AfterThrowing` không chạy nếu method return một Future đã failed nhưng không throw ngay.
8. **Kiểm tra state ghi nhận:** probe/metrics collector có được dùng đúng bean instance, có reset và thread-safe không?
9. **Test negative control:** cùng target method trên raw instance không có events, còn trên proxy có events.
10. **Giữ lại test integration:** sau khi sửa, bảo đảm test còn nạp context, không “đơn giản hóa” thành unit test raw target vô tình bỏ mất proxy.

Thay vì thêm `System.out.println` khắp nơi, một probe/recording collaborator như ví dụ giúp xác nhận chính xác **advice nào chạy, khi nào, bao nhiêu lần**. Test khớp kỳ vọng mới là bằng chứng đủ sức phát hiện regression.

## 14. Lưu ý khi dùng Spring AOP với @Async và method trả Future

Một phương thức được advice có thể return `CompletableFuture` ngay rồi tác vụ hoàn tất sau. Khi test `@AfterReturning`, sự kiện return **không đồng nghĩa future hoàn tất thành công**. Phải đợi/quan sát completion bằng API phù hợp trong test nếu yêu cầu là end-to-end outcome.

Khi kết hợp `@Async`, lời gọi qua Spring proxy có thể giao task cho executor; self-invocation bỏ qua async proxy cũng như bỏ qua các advice khác. Test nên gọi bean proxy, dùng `CompletableFuture`/`CountDownLatch` có kiểm soát, không dựa vào `Thread.sleep()` rồi đoán race.

Nếu advice chỉ cần quan sát completion, `whenComplete` của `CompletionStage` có thể phù hợp; `Future` thông thường không có callback đó. Đây là ranh giới execution của Spring cần kiểm tra riêng, không làm mờ nguyên tắc luôn gọi qua proxy.

## 15. Khi nào một lời gọi raw vẫn có advice?

Điều cần nhắc để tránh khẳng định quá mức: **AspectJ compile-time/load-time weaving thực sự** có thể weave advice vào class, nên lời gọi method trên object được tạo bằng `new` vẫn có thể chạy advice. Khi đó test AspectJ weaving khác với **Spring AOP proxy test**. Trong bài này, test chủ đích dùng Spring `@EnableAspectJAutoProxy` với JDK proxy, không chạy compiler/weaver agent của AspectJ.

Ngoài ra, framework khác hoặc bytecode agent khác có thể quan sát raw method; điều đó không biến raw object thành Spring AOP proxy. Muốn phân biệt, kiểm tra có Spring `AopUtils.isAopProxy`, loại hạ tầng đã bật và behavior khi bỏ proxy/agent.

## 16. Checklist review một PR test Spring AOP

- [ ] Test có nạp Spring context chứa bean + aspect + auto-proxy config thực sự.
- [ ] Reference được gọi lấy từ context/injection, không phải `new` raw target.
- [ ] Với JDK proxy, test inject interface thay vì concrete class.
- [ ] Có assertion xác nhận AOP proxy khi đó là yêu cầu của bài.
- [ ] Có assertion về side effect/số lần/nhánh của **advice**, không chỉ return value.
- [ ] Có đối chứng raw instance hoặc method không match để chống false positive.
- [ ] Có test success, failure, self-invocation nếu concern cần.
- [ ] State của probe được reset giữa các test và quản lý concurrency khi parallel.
- [ ] Không unwrap target rồi gọi như đường chính của integration test.
- [ ] Không thay bean thật bằng mock rồi suy ra advice của target thật.
- [ ] Cấu hình auto-proxy, class/interface, annotation và pointcut phản ánh production.
- [ ] Nếu test `@Async`/transaction/scoped proxy, có kiểm tra timing, target lifecycle, thread/transaction boundary phù hợp.

**Kết luận:** kiểm thử Spring AOP đúng không chỉ là chạy method của một class có annotation. Phải **gọi bean proxy mà Spring thực sự cung cấp**, chứng minh pointcut/advice được kích hoạt bằng assertion rõ ràng, đồng thời có đối chứng raw target để nhận diện false negatives. Đó là cách phân biệt **test business logic** với **test hành vi Spring AOP advice**.

## Tài liệu Spring chính thức

- [Spring Framework 6.2 — Spring AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework 6.2 — Spring AOP Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework — TestContext Framework Support Classes / SpringExtension](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/support-classes.html)
- [Spring Framework — Spring JUnit Jupiter Testing Annotations](https://docs.spring.io/spring-framework/reference/testing/annotations/integration-junit-jupiter.html)
- [Spring Framework 6.2 — AopUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/AopUtils.html)
- [Spring Framework 6.2 — AopTestUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/test/util/AopTestUtils.html)

Khi dùng Spring Framework/Boot hoặc JUnit phiên bản khác, kiểm tra dependency management và khả năng proxy của version đó; chạy lại integration test sau mỗi thay đổi auto-proxy, advisor hoặc bean wiring.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-testing-004">Kiểm thử ma trận pointcut: trường hợp khớp và không khớp</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử ma trận pointcut: trường hợp khớp và không khớp trong Spring AOP

## 1. Mục tiêu kiểm thử trong Spring

Một pointcut của Spring AOP là **bộ lọc các lần thực thi method trên Spring bean đi qua AOP proxy**. Viết đúng biểu thức `execution(...)`, `@annotation(...)` hoặc `bean(...)` mới là một nửa vấn đề: bài kiểm thử phải chứng minh advice **chạy khi cần** và **không chạy khi không được phép**.

Ví dụ: aspect ghi audit chỉ dành cho `OrderService.submit()`. Nếu test chỉ gọi `submit()` rồi kiểm tra log xuất hiện, ta chưa phát hiện được việc aspect vô tình ghi audit cho `OrderService.cancel()` hoặc cho bean khác. Một **ma trận pointcut** kết hợp ca dương tính (*match*) và âm tính (*non-match*) giúp khóa chặt phạm vi advice qua các lần refactor.

Bài này tập trung vào **Spring AOP dùng proxy, Spring Test/JUnit và pointcut được diễn giải trong Spring context**; không yêu cầu dùng AspectJ compile-time/load-time weaving. Những thiết kế join point như `call`, `get` hoặc `set` không được Spring AOP proxy hỗ trợ; không dùng chúng làm ca kiểm thử Spring AOP thông thường.

Kết quả cần kiểm chứng theo thứ tự:

1. Biểu thức pointcut có cú pháp hợp lệ và được Spring đăng ký thành advisor.
2. Spring bean **thật sự đi qua proxy** trong đường gọi cần test.
3. Trường hợp phải khớp nhận advice, số lần và dữ liệu đúng.
4. Trường hợp không được khớp không phát sinh advice.
5. Các giới hạn proxy (self-invocation, đối tượng tạo bằng `new`, private/final method) được ghi nhận thành hành vi kỳ vọng, thay vì nhầm là lỗi cú pháp pointcut.

## 2. Thiết kế ma trận từ điều kiện của biểu thức

Một phương pháp nhất quán là chia mỗi biểu thức thành các chiều đầu vào: *phương thức*, *bean*, *annotation*, *kiểu tham số*, *đối tượng proxy/target* và *đường gọi*.

| Chiều kiểm thử | Ca phải khớp | Ca không được khớp | Điều cần quan sát |
| --- | --- | --- | --- |
| `execution(* ..StoreApi.find(..))` | Gọi `find` qua bean proxy | Gọi `save` | Chỉ counter `find` tăng |
| `@annotation(Audited)` | `find` có `@Audited` trên implementation | `save` không có annotation | Chỉ counter `audit` tăng khi gọi `find` |
| `bean(mainStore) && execution(* ..save(..))` | Gọi `mainStore.save` | Gọi `backupStore.save` cùng kiểu | Chỉ counter `main-save` tăng cho main |
| `execution(* ..accept(..)) && args(String)` | Tham số runtime là `String` | Tham số runtime là `Integer` | Counter `string-arg` đúng 1 lần |
| `target(StoreImpl)` + `execution(* ..save(..))` | Target implement là `StoreImpl` | Target class khác, nếu có | Matcher `target` xét target thực |
| `this(StoreImpl)` + `execution(* ..save(..))` | Proxy thực sự là `StoreImpl` (ví dụ CGLIB, nếu áp dụng) | JDK proxy chỉ implements interface | Phân biệt kiểu proxy và target |
| Proxy boundary | Bean được inject từ Spring rồi gọi `find` bên ngoài | `new StoreImpl().find()` hoặc `this.find()` nội bộ | Không nhầm lời gọi bypass proxy là pointcut không khớp |
| Phủ định `!bean(backupStore)` | `mainStore` | `backupStore` | Tránh matcher phạm vi rộng ngoài ý định |
| `within(com.example.app.service..*)` | Method thuộc package chỉ định | Method ở package không nằm trong phạm vi | Kiểm tra phạm vi package thật, không suy đoán từ tên bean |

Đây là **bảng kế hoạch kiểm thử**: các cột “phải khớp/không được khớp” là **kỳ vọng theo biểu thức và cơ chế proxy đã nêu**, không phải lời khẳng định rằng mọi tổ hợp đều được ví dụ bên dưới thực thi. Bộ test đầy đủ trong mục 4–5 thực thi trực tiếp sáu nhóm đầu và proxy boundary; mục 6 chỉ cách mở rộng cho phủ định, `within` và các điều kiện khác.

Cần phân biệt:

- `execution` so khớp theo **chữ ký method**; `execution(* *..accept(java.lang.String))` kiểm tra kiểu khai báo trên method, khác `args(java.lang.String)` kiểm tra **kiểu argument lúc runtime**.
- `@annotation` chọn method có annotation thích hợp; trong Spring proxy, cần quan tâm implementation method và cách Spring tra metadata của target, nhất là khi interface hoặc generic bridge method tham gia.
- `bean(name)` là phần mở rộng riêng của **Spring AOP**, xét danh tính Spring bean; một class có thể được đăng ký thành nhiều bean tên khác nhau. Native AspectJ weaving không hỗ trợ designator `bean`.
- Với `this(Type)`, đối tượng đang được xét là **proxy**; `target(Type)` xét object nghiệp vụ ở phía sau proxy. Hai điều kiện có thể cho kết quả khác dưới JDK dynamic proxy.
- Spring AOP hỗ trợ method **execution** trên Spring bean. Nó không intercept tự động mọi lời gọi method của object Java trong JVM.

## 3. Nguyên tắc tạo bài test đáng tin

**Dùng Spring context thật cho bài kiểm tra tích hợp pointcut.** Nếu tạo aspect bằng `new` hoặc mock service bằng Mockito rồi gọi trực tiếp, sẽ không có Spring proxy theo mặc định. Test lúc đó chỉ kiểm tra method Java, không kiểm tra AOP.

Một test tốt nên:

- Tạo advisor bằng `@Aspect` là **Spring bean**, kèm `@EnableAspectJAutoProxy` (hoặc cấu hình tự động AOP tương đương trong ứng dụng Boot).
- Lấy service từ Spring context bằng `@Autowired`; gọi trực tiếp trên bean được inject thay vì tạo target bằng `new`, trừ ca âm tính dành riêng cho đối chứng.
- Ghi nhận advice qua probe/counter trong bộ nhớ. Không suy luận solely từ log, vì log có thể bị filter hoặc có lời gọi không kiểm soát từ framework.
- Reset probe **trước mỗi test**, kiểm tra cả số lần bằng 0 lẫn số lần bằng 1 hoặc nhiều lần. Đừng assert thứ tự giữa nhiều `@Before` advice nếu không cấu hình ordering rõ ràng.
- Dùng ít nhất hai bean cùng kiểu nhưng khác tên để test `bean(...)` thực sự. Dùng nhiều method, annotation và argument khác nhau để chứng minh negative cases.
- Xác nhận kiểu proxy thật bằng `AopUtils.isJdkDynamicProxy` hoặc `AopUtils.isCglibProxy` nếu kết quả test phụ thuộc loại proxy.
- Giữ test độc lập: đừng chia sẻ counter giữa các test chạy song song nếu không có cách cách ly dữ liệu.

## 4. Ví dụ runnable: Spring context + JUnit 5 + ma trận advisor

Ví dụ sử dụng Java 21, Gradle và Spring Boot 3.3.x để quản lý dependency, nhưng **test khởi tạo một Spring context nhỏ bằng `@SpringJUnitConfig`**. Vì vậy, kết quả không bị nhiễu bởi toàn bộ auto-configuration của ứng dụng Spring Boot.

Cấu trúc tối thiểu:

~~~text
pointcut-matrix/
├── settings.gradle
├── build.gradle
└── src/test/java/com/example/matrix/PointcutMatrixTest.java
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'pointcut-matrix'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
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

`src/test/java/com/example/matrix/PointcutMatrixTest.java` (tất cả các class phụ đều nằm trong **cùng tệp và package**, hợp lệ vì chỉ `PointcutMatrixTest` là class public):

~~~java
package com.example.matrix;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(PointcutMatrixTest.TestConfig.class)
public class PointcutMatrixTest {
    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    static class TestConfig {
        @Bean("mainStore")
        StoreApi mainStore() {
            return new StoreImpl();
        }

        @Bean("backupStore")
        StoreApi backupStore() {
            return new StoreImpl();
        }

        @Bean
        AdviceProbe adviceProbe() {
            return new AdviceProbe();
        }

        @Bean
        MatrixAspect matrixAspect(AdviceProbe probe) {
            return new MatrixAspect(probe);
        }
    }

    @Autowired
    @Qualifier("mainStore")
    StoreApi main;

    @Autowired
    @Qualifier("backupStore")
    StoreApi backup;

    @Autowired
    AdviceProbe probe;

    @BeforeEach
    void resetProbe() {
        probe.clear();
    }

    // execution: hai bean đều khớp với find, save không khớp.
    @Test
    void executionPositiveAndNegative() {
        main.find("A");
        backup.find("B");
        main.save("C");

        assertEquals(2, probe.count("find"));
    }

    // Annotation trên implementation method.
    @Test
    void annotationPositiveAndNegative() {
        main.find("A");
        main.save("B");

        assertEquals(1, probe.count("audited"));
    }

    // Cùng một class target nhưng hai tên bean khác nhau.
    @Test
    void beanNamePositiveAndNegative() {
        main.save("A");
        backup.save("B");

        assertEquals(1, probe.count("main-save"));
    }

    // Signature là Object; giá trị runtime là String hoặc Integer.
    @Test
    void runtimeArgsPositiveAndNegative() {
        main.accept("text");
        main.accept(Integer.valueOf(42));

        assertEquals(1, probe.count("string-arg"));
    }

    // JDK proxy implements StoreApi, không extends StoreImpl.
    @Test
    void thisAndTargetDifferForJdkProxy() {
        assertTrue(AopUtils.isJdkDynamicProxy(main));
        assertFalse(main instanceof StoreImpl);

        main.save("A");

        assertEquals(1, probe.count("target-store"));
        assertEquals(0, probe.count("this-store"));
    }

    // Gọi nội bộ bypass proxy; gọi từ test qua bean proxy thì có advice.
    @Test
    void selfInvocationDoesNotTriggerFindAdvice() {
        main.outerCallsFind();
        assertEquals(0, probe.count("find"));

        main.find("external");
        assertEquals(1, probe.count("find"));
    }

    // Object được tạo ngoài Spring không nhận proxy tự động.
    @Test
    void manualObjectIsNotSpringProxy() {
        StoreApi outside = new StoreImpl();
        assertFalse(AopUtils.isAopProxy(outside));

        outside.find("direct");
        assertEquals(0, probe.count("find"));
        assertEquals(0, probe.count("audited"));
    }
}

interface StoreApi {
    void find(String code);
    void save(String code);
    void accept(Object value);
    void outerCallsFind();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Audited {
}

class StoreImpl implements StoreApi {
    @Override
    @Audited
    public void find(String code) {
        // Business method: không cần side effect trong thí nghiệm này.
    }

    @Override
    public void save(String code) {
    }

    @Override
    public void accept(Object value) {
    }

    @Override
    public void outerCallsFind() {
        this.find("internal");
    }
}

class AdviceProbe {
    private final List<String> events = new CopyOnWriteArrayList<>();

    void record(String event) {
        events.add(event);
    }

    long count(String event) {
        return events.stream().filter(event::equals).count();
    }

    void clear() {
        events.clear();
    }
}

@Aspect
class MatrixAspect {
    private final AdviceProbe probe;

    MatrixAspect(AdviceProbe probe) {
        this.probe = probe;
    }

    @Before("execution(* com.example.matrix.StoreApi.find(..))")
    public void anyFind() {
        probe.record("find");
    }

    @Before("@annotation(com.example.matrix.Audited)")
    public void annotatedMethod() {
        probe.record("audited");
    }

    @Before("bean(mainStore) && execution(* com.example.matrix.StoreApi.save(..))")
    public void mainSave() {
        probe.record("main-save");
    }

    @Before("execution(* com.example.matrix.StoreApi.accept(..))"
          + " && args(java.lang.String)")
    public void stringArgument() {
        probe.record("string-arg");
    }

    @Before("execution(* com.example.matrix.StoreApi.save(..))"
          + " && target(com.example.matrix.StoreImpl)")
    public void targetStore() {
        probe.record("target-store");
    }

    @Before("execution(* com.example.matrix.StoreApi.save(..))"
          + " && this(com.example.matrix.StoreImpl)")
    public void proxyIsStoreImpl() {
        probe.record("this-store");
    }
}
~~~

**Chạy kiểm thử:** sử dụng Gradle 8.x có hỗ trợ Java 21 và có JDK 21. Nếu dự án đã có Gradle wrapper, dùng `./gradlew` (Windows: `.\gradlew.bat`); nếu chưa có, dùng Gradle cài sẵn.

~~~bash
gradle test --tests com.example.matrix.PointcutMatrixTest
~~~

Kỳ vọng: **7 test pass**, không cần Spring Boot application main class, database, web server hay kết nối mạng khi chạy chương trình (lần đầu Gradle vẫn có thể cần mạng để tải dependency). Đầu ra của Gradle phụ thuộc version, bộ nhớ đệm và tùy chọn console nên không giả định một stdout nguyên văn cố định.

Nếu cần xem từng kết quả, dùng:

~~~bash
gradle test --tests com.example.matrix.PointcutMatrixTest --info
~~~

### Vì sao ví dụ bảo đảm kiểm tra Spring AOP thật?

- `@SpringJUnitConfig` khởi tạo `TestConfig` bằng Spring Test; `@EnableAspectJAutoProxy(proxyTargetClass = false)` bật cơ chế đọc `@Aspect` và tự tạo proxy cho bean phù hợp.
- `@Bean("mainStore")` và `@Bean("backupStore")` trả cùng kiểu `StoreImpl` nhưng có **tên bean khác nhau** để thử designator `bean`.
- `StoreImpl` hiện thực `StoreApi`. Khi cấu hình dùng JDK dynamic proxy, proxy là đối tượng thực hiện `StoreApi`, còn target là `StoreImpl`; vì vậy `this(StoreImpl)` không khớp còn `target(StoreImpl)` khớp.
- `AdviceProbe` là một Spring bean ghi sự kiện khi advice chạy. Mỗi test xóa dữ liệu trước khi gọi, chỉ assert counter liên quan để không phụ thuộc thứ tự nhiều advice cùng chạy.
- Test tự gọi `outerCallsFind()` trên bean proxy, nhưng trong implementation method có `this.find(...)`. Advice của `find` không thấy cuộc gọi nội bộ đó vì nó bypass proxy.
- `new StoreImpl()` không được Spring bọc proxy. Nó vẫn thực thi business method nhưng không sinh bất kỳ event advice nào.

## 5. Đọc kết quả từng phép thử qua ma trận kỳ vọng

Bảng này ghi **event cụ thể được assert**; những advice khác có thể đồng thời chạy trên một lần gọi, nên không lấy `probe.events.size()` thay cho counter từng loại.

| Test JUnit | Lần gọi liên quan | Counter kỳ vọng | Ý nghĩa |
| --- | --- | --- | --- |
| `executionPositiveAndNegative` | `main.find`, `backup.find`, `main.save` | `find = 2` | `save` không bị match bởi pointcut `find` |
| `annotationPositiveAndNegative` | `main.find`, `main.save` | `audited = 1` | Annotation chỉ nằm trên `find` |
| `beanNamePositiveAndNegative` | `main.save`, `backup.save` | `main-save = 1` | `bean(mainStore)` không áp dụng cho bean backup |
| `runtimeArgsPositiveAndNegative` | `accept("text")`, `accept(42)` | `string-arg = 1` | Type của argument runtime quyết định `args(String)` |
| `thisAndTargetDifferForJdkProxy` | `main.save` | `target-store = 1`, `this-store = 0` | Kiểu của target khác kiểu JDK proxy |
| `selfInvocationDoesNotTriggerFindAdvice` | `main.outerCallsFind`, sau đó `main.find` | Sau gọi nội bộ: `find = 0`; cuối test: `find = 1` | Chứng minh giới hạn self-invocation |
| `manualObjectIsNotSpringProxy` | `new StoreImpl().find` | `find = 0` và `audited = 0` | Đối tượng không do Spring quản lý nằm ngoài phạm vi proxy |

**Một lỗ hổng test cần tránh:** `assertEquals(0, counter)` có thể pass giả nếu aspect chưa hề được cấu hình. Vì vậy trong **cùng suite** luôn phải có đối chứng dương tính: `executionPositiveAndNegative` và `annotationPositiveAndNegative` chứng minh bộ đo đã thật sự nhận advice. Với nghiệp vụ nhạy cảm, dùng cùng một test hoặc nhóm test độc lập để khóa điều kiện positive/negative.

## 6. Mở rộng ma trận khi pointcut phức tạp hơn

### 6.1. Phủ định và kết hợp boolean

Ví dụ pointcut Spring AOP:

~~~java
@Before("execution(* com.example.matrix.StoreApi.save(..))"
      + " && !bean(backupStore)")
public void notBackupStore() {
    probe.record("not-backup");
}
~~~

Nếu thêm đoạn trên vào `MatrixAspect`, cần test `main.save(...)` tạo đúng một `not-backup` và `backup.save(...)` không tạo `not-backup`. Dấu `!` chỉ phủ định **một điều kiện nằm trong tập join point của proxy Spring**, không làm advice áp dụng lên đối tượng Java ngoài Spring.

Với các biểu thức có `&&`, `||` và `!`, nên lập truth table cho từng nhánh và ít nhất một ca ngoài phạm vi. Ví dụ `(bean(mainStore) || bean(backupStore)) && execution(* ..save(..))` có ba nhóm riêng: main-save, backup-save và method không phải save; nếu có bean thứ ba thì thêm case đó nữa.

### 6.2. Kiểm thử `within` và package scope

Giả sử tổ chức source code như sau:

~~~text
com.example.app
├── service
│   ├── BillingService.java
│   └── internal
│       └── ReconciliationService.java
└── controller
    └── BillingController.java
~~~

Biểu thức:

~~~java
@Pointcut("within(com.example.app.service..*)"
        + " && execution(public * *(..))")
public void serviceMethods() {
}
~~~

Khi thêm aspect này vào Spring context, lập ít nhất các ca:

- Bean `BillingService` trong package `service`, public method đi qua proxy: match.
- Bean `ReconciliationService` trong package con `service.internal`, public method đi qua proxy: match.
- Bean `BillingController` ở package `controller` có public method tương tự: không match.
- Lời gọi nội bộ `this.someMethod()` trong service không qua proxy: không được xem là một invocation đã intercept thành công.

Đây là **bộ case mở rộng**, không phải các class đã tồn tại trong runnable test ở mục 4. Đừng dùng regex Java để suy diễn ý nghĩa cú pháp AspectJ `..` và `*`; hãy kiểm tra trong Spring context thật.

### 6.3. Thử method overload và dữ liệu runtime

Nếu project có:

~~~java
public void send(String value) { }
public void send(Integer value) { }
public void send(Object value) { }
~~~

thì `execution(* send(java.lang.String))` xét **chữ ký overload được gọi**. Trái lại `args(java.lang.String)` xét **đối tượng argument runtime** trong lần gọi thực tế. Với `send(Object)` và giá trị `String`, `args(String)` vẫn có thể match; với `Integer` sẽ không match.

Test các giá trị biên có ý nghĩa như object subclass, proxy làm argument, parameter nullable hoặc empty arguments khi pointcut dùng `(..)`. Đối với `null`, đừng vội diễn dịch `null` thuộc kiểu `String`: cần test chính xác implementation matcher và chữ ký cụ thể trước khi kết luận.

### 6.4. Interface, annotation và loại proxy

Nên tách suite theo cả hai chiến lược nếu project cho phép:

- **JDK proxy**: `@EnableAspectJAutoProxy(proxyTargetClass = false)` với target implements interface. Tiêm bean qua interface, theo dõi `this(Interface)`/`this(ConcreteClass)` và kiểm tra những method không nằm trên interface.
- **Class-based proxy**: `@EnableAspectJAutoProxy(proxyTargetClass = true)` trên một context/test class **tách biệt**. Kiểm tra `AopUtils.isCglibProxy`, sau đó thử `this(ConcreteClass)`; đừng kỳ vọng final/private method được advice như public overridable method.
- **Annotation trên interface so với implementation**: đặt annotation tại từng vị trí trong test riêng, không giả định metadata trên interface luôn có cùng semantics với implementation khi kết hợp designator, hierarchy hoặc annotation inheritance.
- **Annotation kế thừa/meta-annotation**: nếu dự án dùng composed annotations, tạo fixture cụ thể và assert trên Spring runtime thực tế; đừng lấy một biểu thức generic làm bằng chứng.

Tránh đổi `proxyTargetClass` giữa hai test nhưng vẫn dùng cùng một Spring Test context bị cache. Hãy tách context hoặc test configuration để chắc chắn mỗi case có kiểu proxy dự kiến.

## 7. Các lỗi kiểm thử dẫn tới kết luận sai

| Dấu hiệu | Nguyên nhân thường gặp | Cách kiểm tra |
| --- | --- | --- |
| Tất cả negative cases đều pass | Chưa tạo proxy, aspect không là bean, advice chưa từng chạy | Luôn có positive control và kiểm tra `AopUtils.isAopProxy` |
| Test gọi method nhưng không có event | Gọi `new` target, gọi qua `this`, bean bị mock thay, method không accessible qua proxy | Log/kiểm tra reference nhận từ context và đường gọi |
| Test `bean(name)` sai kỳ vọng | Đang xem tên class thay vì Spring bean name, hoặc nhiều aliases/beans cùng kiểu | `ApplicationContext.getBeanNamesForType(...)` và qualifier |
| Kiểm tra `this` giống `target` | Chưa xác định JDK proxy hay CGLIB | Kiểm tra `AopUtils.isJdkDynamicProxy` và loại target |
| `@annotation` bị miss | Annotation nằm trên interface/base method hoặc proxy/bridge method khác với giả định | Test vị trí annotation thực tế, inspect target method |
| Test thỉnh thoảng đỏ | Probe dùng chung, test chạy song song, có background task, bean state giữa test | Tách context/state, dùng counter thread-safe, kiểm soát async |
| Đếm toàn bộ advice thay vì từng advice | Một method được nhiều pointcut match, dẫn đến nhiều event | Đếm theo event key, không ép advice order |
| Kiểm thử cú pháp pass nhưng runtime sai | Chỉ parse pointcut expression hoặc gọi method matcher trong isolation | Bổ sung Spring integration test có cả proxy và target |
| Chỉ test happy path | Aspect bị match quá rộng và chạy lên method không mong muốn | Test thêm method, bean, package và argument negative |

Một kiểm thử unit thuần cho method matcher cũng hữu ích để khoanh vùng lỗi parser, nhưng **không thay thế** integration test của Spring proxy: kiểu proxy, pointcut dynamic matcher, tên bean và self-invocation đều có thể thay đổi kết quả cuối cùng.

## 8. Đưa ma trận vào CI và bảo trì khi refactor

Với aspect cross-cutting quan trọng (audit, authorization, transaction, monitoring), nên duy trì ma trận test như **hợp đồng về phạm vi can thiệp**:

1. Viết một danh sách case positive/negative ứng với các nhánh của pointcut, kết hợp input method + bean + runtime args.
2. Thêm test cho service mới, overload mới, package thay đổi hoặc annotation mới. Khi đổi tên bean, sửa cả kỳ vọng `bean(...)`.
3. Với biến thể tham số nhiều giá trị, dùng `@ParameterizedTest`/`@MethodSource`, nhưng giữ assertion tường minh cho từng case và reset probe.
4. Test tác dụng thực nếu advice có logic nghiệp vụ: ví dụ aspect audit phải ghi đúng bản ghi, aspect phân quyền phải chặn đúng đường gọi; counter chỉ chứng minh **advice được gọi**, chưa chứng minh toàn bộ logic của advice đúng.
5. Chạy test theo cùng cấu hình proxy production. Nếu production còn có AspectJ weaving, viết test riêng cho weaving; đó là một cơ chế khác Spring AOP.
6. Khi assertion negative thất bại sau refactor, kiểm tra phạm vi pointcut trước khi đổi test để “pass lại”: rất có thể advice vừa tràn sang method hoặc bean không mong muốn.

**Ghi nhớ:** Ma trận pointcut tốt không chỉ kiểm chứng **điểm mà advice chạy**, mà còn chứng minh rõ **những lời gọi mà Spring AOP phải bỏ qua**. Hãy đặt mọi case vào Spring context, đo event thực từ advice và kiểm tra ranh giới proxy để tránh kết luận nhầm.

## Tài liệu chính thức

- Spring Framework — [Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html).
- Spring Framework — [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).
- Spring Framework — [Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/enable.html).
- Spring Framework — [Testing / TestContext Framework](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework.html).
- Spring Boot — [Testing Spring Boot Applications](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-testing-005">Kiểm thử thứ tự advice bằng vết thực thi có kỳ vọng</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử thứ tự advice bằng vết thực thi có kỳ vọng trong Spring AOP

## 1. Mục tiêu của phép thử

Khi một phương thức của Spring bean được nhiều aspect can thiệp, việc “có đủ advice” chưa chứng minh được “advice chạy đúng thứ tự”. Bài kiểm thử cần tạo **vết thực thi (execution trace) có thứ tự**, xác định trước kết quả mong đợi rồi so sánh trực tiếp với những sự kiện đã xảy ra qua Spring AOP proxy.

Một bài kiểm thử tốt trả lời được:

1. Lời gọi có **đi qua Spring-managed proxy** hay không?
2. Hai advice được chỉ định `@Order` có đi vào, gọi `proceed()`, đi ra theo thứ tự dự kiến không?
3. Khi target trả về bình thường, vết sự kiện nào là đúng?
4. Khi target ném exception, nhánh `catch` và `finally` của từng advice chạy theo thứ tự nào?
5. Khi một method **không khớp pointcut**, test có chứng minh được advice không chạy không?
6. Thay đổi precedence, pointcut hoặc đường gọi có khiến assertion phát hiện hồi quy không?

Phạm vi là **Spring AOP dựa trên proxy**, dùng `@AspectJ` annotation để Spring tạo advisor/interceptor. Trọng tâm ở cách thiết kế và chạy test trong Spring, không mở rộng sang mô hình AspectJ weaving độc lập.

## 2. Xác định hợp đồng thứ tự trước khi viết assertion

Với hai Spring aspect riêng biệt:

```java
@Aspect
@Order(10)
class OuterAspect { /* @Around(...) */ }

@Aspect
@Order(20)
class InnerAspect { /* @Around(...) */ }
```

Trong Spring AOP, `@Order` nhỏ hơn có precedence cao hơn. Với hai `@Around` advice cùng match một lời gọi, advice có precedence cao hơn chạy **trước trên đường vào**, nhưng **sau trên đường ra**. Như vậy:

```text
caller
  ↓
outer.enter      (@Order 10)
  ↓
inner.enter      (@Order 20)
  ↓
target
  ↑
inner.return
inner.finally
  ↑
outer.return
outer.finally
  ↑
caller
```

Vết quan sát có cấu trúc như một danh sách tuyến tính:

```text
[outer.enter, inner.enter, target.enter, target.return,
 inner.return, inner.finally, outer.return, outer.finally]
```

**Tại sao cần ghi cả hai phía `proceed()`?** Chỉ kiểm tra `outer.enter` đứng trước `inner.enter` có thể bỏ sót advice không gọi `proceed()`, xử lý exception sai hoặc bỏ qua cleanup. Khi ghi `enter`, `return`, `throw` và `finally`, test mô tả được cả quan hệ bao bọc.

Đối với nhánh exception, kỳ vọng khác:

```text
[outer.enter, inner.enter, target.enter, target.throw,
 inner.throw, inner.finally, outer.throw, outer.finally]
```

Không nên áp dụng vô điều kiện quy tắc này cho mọi tổ hợp `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`. Hãy xác định riêng hợp đồng của từng kiểu advice và phiên bản Spring thực tế. Ví dụ bên dưới cố ý dùng **mỗi aspect chỉ một `@Around`** để thứ tự có thể kiểm chứng rõ ràng. Hai advice cùng precedence hoặc nhiều advice trong cùng một aspect không nên được giả định có thứ tự ổn định nếu cấu hình không xác định rõ.

## 3. Thiết kế fixture kiểm thử ổn định

Ta dùng Spring Framework + JUnit 5 với context được tạo **mới trong từng test** để không chia sẻ bộ đệm trace giữa các bài kiểm thử. `ProbeService` có hai phương thức:

- `run(boolean fail)`: được cả hai aspect match; khi `fail=false`, trả `"OK"`; khi `fail=true`, ném `IllegalStateException("boom")`.
- `unmatched()`: được gọi qua cùng bean proxy nhưng không khớp pointcut của hai aspect; chỉ ghi `target.unmatched`.

`Trace` là danh sách sự kiện chạy đồng bộ trong cùng thread. Đây là **đối tượng test độc lập**, không dùng `System.out`, timestamp hay thứ tự log của các thread khác để làm assertion.

| Thành phần | Vai trò | Dấu vết kỳ vọng |
| --- | --- | --- |
| `OuterAspect`, `@Order(10)` | Advice ngoài | `outer.enter`, rồi `outer.return` hoặc `outer.throw`, sau cùng `outer.finally` |
| `InnerAspect`, `@Order(20)` | Advice trong | `inner.enter`, rồi `inner.return` hoặc `inner.throw`, sau cùng `inner.finally` |
| `ProbeService.run(false)` | Target thành công | `target.enter`, `target.return` |
| `ProbeService.run(true)` | Target thất bại | `target.enter`, `target.throw` |
| `ProbeService.unmatched()` | Đối chứng pointcut | Chỉ `target.unmatched` |

### 3.1. Khởi tạo project nhỏ

Ví dụ sử dụng **JDK 17+**, **Spring Framework 6.2.8**, **JUnit Jupiter 5.11.4** và Maven 3.9+.

```text
spring-advice-order-test/
├── pom.xml
└── src/test/java/lab/AdviceOrderTest.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lab</groupId>
    <artifactId>spring-advice-order-test</artifactId>
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

`aspectjweaver` cung cấp hỗ trợ annotation/pointcut cho Spring `@AspectJ` style trong fixture này; test không bật load-time weaving hoặc compile-time weaving. Spring tạo proxy bằng `@EnableAspectJAutoProxy`.

### 3.2. Tạo một file test đầy đủ

`src/test/java/lab/AdviceOrderTest.java`:

```java
package lab;

import java.util.ArrayList;
import java.util.List;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdviceOrderTest {

    @Test
    void success_has_expected_enter_and_exit_order() {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            ProbeService service = ctx.getBean(ProbeService.class);
            Trace trace = ctx.getBean(Trace.class);

            assertTrue(AopUtils.isAopProxy(service),
                "Call must use the Spring proxy");

            String result = service.run(false);

            assertEquals("OK", result);
            assertEquals(List.of(
                "outer.enter",
                "inner.enter",
                "target.enter",
                "target.return",
                "inner.return",
                "inner.finally",
                "outer.return",
                "outer.finally"
            ), trace.snapshot());
        }
    }

    @Test
    void exception_unwinds_the_advisor_chain() {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            ProbeService service = ctx.getBean(ProbeService.class);
            Trace trace = ctx.getBean(Trace.class);

            IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.run(true)
            );

            assertEquals("boom", error.getMessage());
            assertEquals(List.of(
                "outer.enter",
                "inner.enter",
                "target.enter",
                "target.throw",
                "inner.throw",
                "inner.finally",
                "outer.throw",
                "outer.finally"
            ), trace.snapshot());
        }
    }

    @Test
    void non_matching_method_is_not_advised() {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            ProbeService service = ctx.getBean(ProbeService.class);
            Trace trace = ctx.getBean(Trace.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals("OTHER", service.unmatched());
            assertEquals(List.of("target.unmatched"), trace.snapshot());
        }
    }

    @Test
    void observed_bean_exposes_spring_advisors() {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            ProbeService service = ctx.getBean(ProbeService.class);

            assertTrue(service instanceof Advised);
            var advised = (Advised) service;
            assertTrue(advised.getAdvisors().length >= 2,
                "Both ordered aspects must supply advisors");

            // Không cố định số lượng/tên class của advisor nội bộ Spring.
            for (int i = 0; i < advised.getAdvisors().length; i++) {
                var advisor = advised.getAdvisors()[i];
                System.out.printf(
                    "advisor[%d]=%s advice=%s%n",
                    i,
                    advisor.getClass().getName(),
                    advisor.getAdvice().getClass().getName()
                );
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class Config {
        @Bean
        Trace trace() {
            return new Trace();
        }

        @Bean
        ProbeService probeService(Trace trace) {
            return new ProbeService(trace);
        }

        @Bean
        OuterAspect outerAspect(Trace trace) {
            return new OuterAspect(trace);
        }

        @Bean
        InnerAspect innerAspect(Trace trace) {
            return new InnerAspect(trace);
        }
    }

    public static class Trace {
        private final List<String> events = new ArrayList<>();

        public void add(String event) {
            events.add(event);
        }

        public List<String> snapshot() {
            return List.copyOf(events);
        }
    }

    public static class ProbeService {
        private final Trace trace;

        public ProbeService(Trace trace) {
            this.trace = trace;
        }

        public String run(boolean fail) {
            trace.add("target.enter");

            if (fail) {
                trace.add("target.throw");
                throw new IllegalStateException("boom");
            }

            trace.add("target.return");
            return "OK";
        }

        public String unmatched() {
            trace.add("target.unmatched");
            return "OTHER";
        }
    }

    @Aspect
    @Order(10)
    public static class OuterAspect {
        private final Trace trace;

        public OuterAspect(Trace trace) {
            this.trace = trace;
        }

        @Around("bean(probeService) && execution(* run(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            trace.add("outer.enter");
            try {
                Object result = pjp.proceed();
                trace.add("outer.return");
                return result;
            } catch (Throwable error) {
                trace.add("outer.throw");
                throw error;
            } finally {
                trace.add("outer.finally");
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

        @Around("bean(probeService) && execution(* run(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            trace.add("inner.enter");
            try {
                Object result = pjp.proceed();
                trace.add("inner.return");
                return result;
            } catch (Throwable error) {
                trace.add("inner.throw");
                throw error;
            } finally {
                trace.add("inner.finally");
            }
        }
    }
}
```

**Vì sao test này đi qua Spring AOP thật?** Mỗi test tạo `AnnotationConfigApplicationContext`, đăng ký `@EnableAspectJAutoProxy`, `@Bean` cho target và hai aspect, rồi **lấy `ProbeService` từ context**. `proxyTargetClass=true` yêu cầu class-based proxy; `ProbeService` là public, không final, và `run()` public, không final. Hai pointcut dùng Spring `bean(probeService)` để ràng buộc đúng bean, kết hợp `execution(* run(..))` cho method cần kiểm tra.

Không được thay `ctx.getBean(ProbeService.class)` bằng `new ProbeService(trace)` trong test: một object tạo trực tiếp ngoài container không đi qua Spring AOP proxy. Phép thử đó sẽ chỉ có các sự kiện target và không kiểm tra được thứ tự advisor.

## 4. Chạy test và đối chiếu vết kỳ vọng

Trong thư mục chứa `pom.xml`:

```bash
mvn test
```

Bốn bài kiểm thử được thiết kế để trả về thành công khi Spring AOP cấu hình đúng:

```text
success_has_expected_enter_and_exit_order       PASS
exception_unwinds_the_advisor_chain             PASS
non_matching_method_is_not_advised              PASS
observed_bean_exposes_spring_advisors           PASS
```

Bảng trên là **kỳ vọng ở mức assertion**, không phải bản sao nguyên văn stdout của Maven/Surefire. Báo cáo thực tế hiển thị tên lớp test, số tests/failures/errors và có thể có log Spring tùy phiên bản.

Các event cần so sánh chính xác:

| Ca kiểm thử | Danh sách event kỳ vọng | Kết quả trả về/exception |
| --- | --- | --- |
| `run(false)` | `outer.enter → inner.enter → target.enter → target.return → inner.return → inner.finally → outer.return → outer.finally` | `"OK"` |
| `run(true)` | `outer.enter → inner.enter → target.enter → target.throw → inner.throw → inner.finally → outer.throw → outer.finally` | `IllegalStateException("boom")` |
| `unmatched()` | `target.unmatched` | `"OTHER"` |

**Kết quả đặc biệt cần chú ý:** `non_matching_method_is_not_advised` chứng minh cùng một **bean proxy** vẫn có thể có phương thức không khớp advice. Như vậy `AopUtils.isAopProxy(service)` hoặc `Advised.getAdvisors()` chỉ cho biết bean đã được proxy/cấu hình; để biết advisor nào chạy ở **một lời gọi cụ thể**, phải dùng trace và hiểu pointcut.

Phép thử introspection chỉ khẳng định có advisor và giúp in chúng khi điều tra. Không assert `advisor[0]` phải có tên một lớp nội bộ cụ thể: Spring có thể thêm advisor/interceptor hạ tầng, và tên implementation hoặc số phần tử thay đổi theo cấu hình. Điều ổn định ở đây là **hợp đồng thứ tự event của hai advice ta kiểm soát**.

## 5. Đảm bảo test thật sự phát hiện sai thứ tự

Một bài test tốt phải **thất bại có chủ đích** khi ta tạo lỗi. Thực hiện thí nghiệm đối chứng bằng cách thay **duy nhất một biến** và chạy lại `mvn test`.

### 5.1. Đổi precedence của hai aspect

Đổi:

```java
@Order(10) // OuterAspect
@Order(20) // InnerAspect
```

thành:

```java
@Order(20) // OuterAspect
@Order(10) // InnerAspect
```

Nếu giữ nguyên danh sách `expected`, hai test success/exception phải thất bại vì thứ tự vào/ra đổi. Với `run(false)`, vết mới theo precedence là:

```text
inner.enter
outer.enter
target.enter
target.return
outer.return
outer.finally
inner.return
inner.finally
```

Điều quan trọng là **test phát hiện chênh lệch ở chính vị trí khác đầu tiên**, chứ không phải chỉ nhận ra số lượng advice vẫn đủ.

### 5.2. Không match một aspect

Đổi pointcut của `InnerAspect` thành:

```java
@Around("bean(probeService) && execution(* unmatched(..))")
```

Trên `run(false)`, `InnerAspect` không chạy; vết dự kiến trở thành:

```text
outer.enter → target.enter → target.return → outer.return → outer.finally
```

Tuy nhiên `unmatched()` lúc này lại có thể bị `InnerAspect` intercept. Do vậy cả test positive và negative đều có khả năng FAIL nếu vẫn dùng kỳ vọng ban đầu; đó là bằng chứng pointcut đã làm thay đổi hành vi đúng như cấu hình mới.

### 5.3. Advice không gọi `proceed()`

Trong `OuterAspect`, nếu thay `pjp.proceed()` bằng `return "SHORT_CIRCUIT";`, `InnerAspect` và target có thể không được gọi. Assertion bắt buộc phát hiện thiếu `inner.enter`, `target.enter` và các sự kiện tương ứng. Đây là lý do **không chỉ kiểm tra event đầu tiên**.

### 5.4. Gọi trực tiếp object ngoài Spring

Nếu gọi `new ProbeService(new Trace()).run(false)`, cả hai advice sẽ không chạy. Đây là kiểm thử đối chứng đường gọi, nhưng **không phải ca kiểm thử hợp lệ cho advisor chain của Spring**. Dùng nó để giải thích khác biệt giữa proxy invocation và target invocation.

### 5.5. Đồng precedence

Nếu cả hai aspect đều dùng `@Order(10)` và không có quy tắc khác xác định precedence, thứ tự giữa các aspect có thể không được đảm bảo. Không viết assertion giả định một thứ tự tình cờ quan sát được là hợp đồng ổn định. Hãy gán thứ tự rõ ràng hoặc điều chỉnh thiết kế nếu nghiệp vụ thật sự phụ thuộc precedence.

## 6. Mở rộng khi có nhiều loại advice trong Spring

Ví dụ phía trên chỉ dùng `@Around` để kiểm soát quan hệ bọc giữa hai aspect. Trong một ứng dụng Spring thực tế, có thể xuất hiện nhiều loại advice và các advisor hạ tầng như transaction, cache, method security. Khi mở rộng test:

- **`@Before`:** ghi event ngay trước target; test điều kiện và precedence giữa các aspect khác nhau. Một `@Before` advice không có `proceed()` để tự ghi “after”.
- **`@AfterReturning`:** chỉ kỳ vọng khi target hoàn thành theo nhánh trả về thành công.
- **`@AfterThrowing`:** chỉ kỳ vọng trên luồng ném exception phù hợp với binding/type; nếu một `@Around` đã xử lý và thay thế exception bằng return value, các advice khác có thể quan sát kết quả khác.
- **`@After` (finally):** cần phân biệt với `@AfterReturning`; nó chạy khi đường gọi kết thúc dù thành công hay thất bại trong phạm vi join point tương ứng.
- **`@Around`:** có thể thay đổi return value, xử lý exception, quyết định có gọi `proceed()` hay không; đây là biến chính ảnh hưởng đến chuỗi quan sát.

Nếu nhiều advice nằm **trong cùng một aspect**, tránh gán một thứ tự tùy ý chỉ dựa vào thứ tự khai báo method trong source. Khi test phụ thuộc precedence, có thể tách chúng thành các aspect với `@Order` khác nhau hoặc áp dụng rõ quy tắc Spring tương ứng.

Với `@Transactional`, cần lưu ý event `after returning` bên trong một custom `@Around` chưa chắc chứng minh transaction đã **commit**: vị trí transaction advisor trong chuỗi quyết định ranh giới này. Với `@Async`, trace có thể nằm trên thread khác; nếu vẫn dùng một `ArrayList` và assertion chạy ngay lập tức, test có thể có race. Khi cần kiểm thử luồng bất đồng bộ, thêm correlation id, bộ thu event an toàn luồng và cơ chế chờ hoàn thành có giới hạn phù hợp, rồi định nghĩa thứ tự **trong từng nhánh xử lý** thay vì ép thứ tự toàn cục.

## 7. Nguyên tắc xây assertion đáng tin cậy

### 7.1. So sánh cả chuỗi event, không chỉ số lượng

**Yếu:**

```java
assertEquals(8, trace.snapshot().size());
```

Test này vẫn PASS nếu `inner` chạy trước `outer` nhưng số sự kiện không đổi.

**Mạnh hơn:**

```java
assertEquals(List.of(
    "outer.enter",
    "inner.enter",
    "target.enter",
    "target.return",
    "inner.return",
    "inner.finally",
    "outer.return",
    "outer.finally"
), trace.snapshot());
```

So sánh cả list phát hiện thừa, thiếu, đảo thứ tự và chạy lặp advice. Nếu muốn báo lỗi dễ đọc hơn, dùng thư viện assertion có mô tả diff rõ ràng; vẫn giữ một kỳ vọng xác định.

### 7.2. Ghi event tại đúng ranh giới

Mỗi advice chỉ ghi event ngay khi đi vào, sau `proceed()`, trong `catch` và `finally`. Target tự ghi event của nó. Nếu đặt nhiều event ở wrapper bên ngoài test thay cho method thật, thứ tự có thể phản ánh mã test thay vì chain advisor.

### 7.3. Cố định đầu vào và vòng đời Spring context

Giữ một target, một Spring context, một caller, không có retry/async/ngầm gọi nhiều lần cho mỗi ca test. Tạo context mới cho từng test giúp vết trace sạch. Khi dùng `@SpringBootTest` với context cache, hãy bảo đảm reset `Trace` trước mỗi lượt và kiểm soát test chạy song song; `@DirtiesContext` chỉ cần khi test thực sự làm bẩn state của context.

### 7.4. Ghi nhận proxy và cấu hình trước khi kết luận

Kiểm tra bean có thực sự là Spring AOP proxy. `Advised.getAdvisors()` có thể giúp nhìn snapshot danh sách advisor hiện có, nhưng danh sách đó **không chứng minh tất cả advisor đều chạy cho method/arguments đang test**. Runtime method matchers, bean pointcuts và hạ tầng có thể lọc khác nhau; dùng trace lời gọi làm bằng chứng thứ tự thực thi.

### 7.5. Không làm test phụ thuộc chi tiết ngẫu nhiên

Không assert tên class proxy có chứa chuỗi `CGLIB`, thứ tự đăng ký bean, dòng log startup hay index tuyệt đối của advisor nội bộ. Điều cần bảo vệ là chính sách `@Order` và event tại điểm chạy advice/target. Cũng tránh `Thread.sleep(...)` để đo thứ tự; thời gian lập lịch thread không phải phép đo precedence.

## 8. Áp dụng cho method nghiệp vụ thật

Một quy trình thử nghiệm trên ứng dụng Spring hiện có:

1. **Khoanh vùng** bean/method và các aspect/advisor có thể áp dụng; xác định cấu hình `@EnableAspectJAutoProxy` hoặc auto-proxy tương đương đang thực sự được bật trong Spring context của test.
2. **Định nghĩa kỳ vọng** bằng chuỗi event trên một lời gọi, ghi rõ `@Order` của từng aspect, kiểu advice, kết quả trả về hoặc exception, những event sẽ không có nếu pointcut không match.
3. **Chèn recorder vào fixture/test aspects**, hoặc dựng wrapper kiểm thử có phạm vi hẹp; tránh thay đổi logic nghiệp vụ thật chỉ để lấy thứ tự. Nếu cần kiểm chứng advisor framework, đo tại ranh giới có thể quan sát và ghi rõ điều đang kiểm chứng.
4. **Gọi thông qua Spring bean proxy**, không tự khởi tạo target. Với interface-based proxy, inject interface; với class-based proxy, bảo đảm method có thể intercept.
5. **Assert result, exception và toàn bộ event list**; thêm đối chứng một method không match, thứ tự đảo và nhánh lỗi khi có giá trị học tập.
6. **Thay đổi một biến tại một thời điểm**, chạy lại từ context sạch. Chỉ chấp nhận kết luận khi test có thể thất bại lúc cố ý phá thứ tự.
7. **Giữ test trong CI**, ghi phiên bản Spring/JDK và các giả định thiết yếu; cập nhật kỳ vọng có giải thích khi chính sách thứ tự được thay đổi có chủ đích.

**Ví dụ với transaction:** nếu muốn khẳng định custom tracing aspect chạy **bên trong transaction**, assertion nên kiểm tra `TransactionSynchronizationManager.isActualTransactionActive()` tại chính event cần đo, bên cạnh trace. Nếu muốn kiểm chứng sự kiện chỉ được phát sau commit, dùng `TransactionSynchronization` hoặc cơ chế transaction event tương ứng; riêng thứ tự `@Around` chưa đủ bảo đảm thời điểm commit.

**Ví dụ với security:** khi thử method-security advisor, phải thiết lập security context và policy phù hợp với method; khẳng định advice custom chạy trước/sau security check phải dựa vào trace của chính lời gọi thành công/từ chối. Đừng giả định mọi Spring-provided advisor dùng cùng `@Order` với custom aspects.

## 9. Những lỗi phổ biến và cách nhận biết

| Triệu chứng | Nguyên nhân có thể | Cách kiểm chứng/sửa |
| --- | --- | --- |
| Trace chỉ có target | Bean không phải proxy, self-invocation, pointcut không match, aspect bean chưa được đăng ký | Xác nhận Spring context, `AopUtils.isAopProxy`, đường gọi và advisor |
| Có hai `enter` nhưng thiếu `target` | Một `@Around` không gọi `proceed()` hoặc trả về sớm | Assert đầy đủ chuỗi và kết quả |
| Thứ tự đảo so với giả thuyết | `@Order` khác dự kiến, aspect cùng precedence, advisor hạ tầng làm thay đổi điều kiện quan sát | Ghi nhận `@Order`, inspect `Advised`, đo trace thật |
| Test success PASS nhưng error FAIL | `catch/finally` khác, exception bị xử lý hoặc chuyển đổi | Thêm assertion exception và event ở cả hai phía `proceed()` |
| Test FAIL ngẫu nhiên khi chạy cả suite | Context/state dùng chung, nhiều thread, async, retry hoặc trace không reset | Cô lập context, correlation id, đảm bảo completion |
| Test đếm đủ event mà vẫn sai | Chỉ kiểm tra size hoặc dùng set không có thứ tự | `assertEquals(List.of(...), actual)` |
| Test phụ thuộc số advisor | Spring thêm advisor hạ tầng hoặc pointcut được tối ưu khác | Assert hành vi thực tế, dùng advisor snapshot để chẩn đoán |
| Kỳ vọng advice chạy trên self-invocation | Spring proxy không chặn lời gọi qua `this` của target | Gọi qua proxy hoặc thiết kế lại điểm gọi khi cần |

## 10. Tiêu chí hoàn thành

Một test chứng minh **thứ tự advice bằng vết thực thi có kỳ vọng** đạt yêu cầu khi: (1) xác minh bean được gọi qua Spring AOP proxy; (2) tạo được expected event list *trước khi chạy*, dựa trên `@Order`, pointcut và ngữ nghĩa advice; (3) assert **chính xác danh sách event có thứ tự** cho đường thành công và exception; (4) có đối chứng method không match; (5) test có thể chứng minh thất bại khi thay đổi precedence hoặc bỏ `proceed()`; và (6) không lệ thuộc log/timing hay chi tiết advisor nội bộ.

Trong fixture này, `OuterAspect` (`@Order(10)`) bọc `InnerAspect` (`@Order(20)`) khi gọi `run()` qua Spring proxy. Assertion của JUnit ghi nhận riêng `enter/return/throw/finally` và xác nhận target chạy đúng một lần. Khi áp dụng cho ứng dụng khác, xây kỳ vọng từ **cấu hình Spring và lời gọi cụ thể của ứng dụng đó**, rồi giữ phép thử như một hợp đồng chống hồi quy.

### Tài liệu Spring/JUnit để đối chiếu

- [Spring Framework — Declaring Advice / Advice Ordering](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): precedence giữa các Spring aspects và ngữ nghĩa các loại advice.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB proxy, giới hạn self-invocation.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): kích hoạt `@EnableAspectJAutoProxy` và đăng ký aspect.
- [Spring Framework — Advised Objects](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advised.html): introspection advisor của Spring proxy.
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/): `@Test`, `assertEquals`, `assertThrows` và Maven/Surefire.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-testing-006">Kiểm thử giá trị trả về, đối số và ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử giá trị trả về, đối số và ngoại lệ trong Spring AOP

## 1. Mục tiêu và phạm vi

Khi một ứng dụng Spring có `@Aspect`, test một service chỉ bằng cách gọi target và kiểm tra giá trị trả về là chưa đủ. Cần chứng minh đồng thời **Spring đã tạo AOP proxy, pointcut chọn đúng method, advice nhận đúng arguments, bảo toàn hoặc thay đổi return value như yêu cầu, và exception vẫn đi đúng đường**.

Bài này tập trung hoàn toàn vào **kiểm thử Spring AOP**: `@EnableAspectJAutoProxy`, Spring TestContext với JUnit 5, `JoinPoint` / `ProceedingJoinPoint`, `@Before`, `@Around`, `@AfterReturning`, `@AfterThrowing`, `AopUtils` và các lỗi khi test nhầm target thay vì proxy. Ví dụ Java 17 dùng Spring Framework 6.2 và JUnit Jupiter, có Maven project đầy đủ và kết quả kiểm tra cụ thể.

Ba câu hỏi trọng tâm:

1. **Đối số:** Advice nhận được giá trị client gửi vào hay giá trị đã bị một `@Around` khác biến đổi? Method target cuối cùng nhận giá trị gì?
2. **Giá trị trả về:** Client nhận kết quả nào? `@AfterReturning` nhìn thấy return value nào? Nếu `@Around` thay kết quả, test có phát hiện được?
3. **Ngoại lệ:** Client có nhận đúng exception type/message/cause không? `@AfterThrowing` chạy khi nào? Advice có vô tình nuốt lỗi hoặc thay exception không?

Mọi assertion quan trọng phải chạy bằng **bean do Spring ApplicationContext trả về**, tức thực tế đi qua Spring AOP proxy. Tạo object bằng `new` là một phép đối chứng hữu ích nhưng không thể thay thế integration test của Spring AOP.

## 2. Những đối tượng nên kiểm tra

| Thành phần | Cần xác minh gì? | Cách quan sát phù hợp |
| --- | --- | --- |
| Spring bean được inject | Bean có phải Spring AOP proxy, interface được expose đúng? | `AopUtils.isAopProxy(bean)`, cast theo interface |
| Pointcut | Advice chỉ chạy trên method dự kiến | Gọi cả method có match và không match |
| Đối số đầu vào advice | `JoinPoint.getArgs()` hoặc binding `args(...)` nhận dữ liệu đúng | Ghi sự kiện và assert bằng JUnit |
| `@Around` | `proceed()` hoặc `proceed(Object[])` chuyển đúng data tới target; return/throw đúng | Assert public result, target observation, event |
| `@AfterReturning` | Chạy khi method kết thúc bình thường; `returning` bind đúng giá trị | Assert return value và số lần/event |
| `@AfterThrowing` | Chạy khi method kết thúc do exception; `throwing` bind đúng exception type | `assertThrows`, `assertSame`/message/event |
| Advice không nên match | Không thêm side effect không mong đợi | Kiểm thử negative pointcut |
| Khi lời gọi thất bại | Không phát sinh success event; exception được preserve | Assert absence của return event và presence của throw event |

Dùng assertion trên **giá trị, event và exception thực tế**; tránh test chỉ gọi method rồi xác nhận "không ném lỗi". Một test như thế không chứng minh aspect có chạy.

## 3. Spring AOP có thể quan sát những giá trị nào?

### 3.1. Đối số với JoinPoint và ProceedingJoinPoint

`JoinPoint.getArgs()` trả về snapshot mảng arguments mà join point hiện nhận. `@Before` có thể đọc chúng, nhưng không nên giả định thay phần tử của mảng trả về sẽ thay đối số của invocation. Nếu cần **chủ động thay arguments** cho lời gọi tiếp theo, dùng `ProceedingJoinPoint.proceed(Object[])` trong `@Around`.

```java
@Around("execution(* demo.TextService.greet(..))")
public Object trim(ProceedingJoinPoint pjp) throws Throwable {
    String original = (String) pjp.getArgs()[0];
    return pjp.proceed(new Object[] { original.trim() });
}
```

Đây là snippet giải thích, có import `org.aspectj.lang.ProceedingJoinPoint` và `org.aspectj.lang.annotation.Around`. Ví dụ hoàn chỉnh ở phần 5 bổ sung log và test.

Trong **Spring AOP**, array truyền vào `proceed(Object[])` là **danh sách arguments của underlying method**. Điểm này cần phân biệt với một số semantics khác khi viết AspectJ aspect trực tiếp; trong bài này dùng Spring AOP proxy.

### 3.2. Giá trị trả về trong @AfterReturning

```java
@AfterReturning(
    pointcut = "execution(* demo.TextService.greet(..))",
    returning = "answer"
)
public void captured(String answer) {
    // Quan sát return value đã được trả bình thường.
}
```

Giá trị `returning` phải khớp tên tham số `answer`. Kiểu `String` trong signature có thể **giới hạn matching**, chỉ advice cho các return value tương thích. Advice loại này có thể ghi log/đo metrics nhưng **không thay thế reference** trả về của lời gọi bằng cách `return` một object khác: method advice là `void`, client tiếp tục nhận kết quả từ invocation.

Nếu yêu cầu phải thay kết quả cho caller, hãy dùng `@Around` và trả object mới có type phù hợp, rồi test public API. Nếu không cần quyền thay đổi kết quả, ưu tiên `@AfterReturning` vì contract hẹp hơn.

### 3.3. Exception trong @AfterThrowing

```java
@AfterThrowing(
    pointcut = "execution(* demo.TextService.fail(..))",
    throwing = "error"
)
public void recorded(IllegalStateException error) {
    // Quan sát exception được ném tại join point.
}
```

Tên `throwing` phải khớp tên tham số `error`. Kiểu `IllegalStateException` giới hạn advice cho exception tương thích; nếu muốn quan sát rộng hơn có thể nhận `Throwable`. Method `@AfterThrowing` chạy khi join point kết thúc bằng exception, không phải mọi lúc service báo lỗi bằng một giá trị response.

`@AfterThrowing` không phải cơ chế catch-and-recover rồi trả về giá trị mới cho caller. Nếu cần recover/convert exception, `@Around` có thể catch rồi throw exception khác hoặc return fallback; khi đó phải test rõ semantics và tác động lên transaction advisor (nếu có).

## 4. Chọn phép thử theo dữ liệu và đường kết thúc cần quan sát

Bài **phân biệt logic aspect và wiring** đã giải thích lựa chọn unit/integration test. Với mục tiêu ở đây, chọn cấp độ test theo **chứng cứ về arguments, return value và exception**:

| Cần xác minh | Kiểm thử phù hợp |
| --- | --- |
| Quy tắc biến đổi/định dạng dữ liệu thuần trong advice | Unit test helper hoặc gọi advice method với fake/mock `ProceedingJoinPoint`, kiểm tra giá trị và `proceed(Object[])` |
| Binding input thực tế qua `args(...)` hay `JoinPoint.getArgs()` | Spring AOP integration test gọi bean proxy; assert cả input ban đầu và input mà target nhận |
| `@AfterReturning` / `@Around` bảo toàn hoặc thay kết quả | Assert actual return tới caller **và** event của advice trên lời gọi đi qua proxy |
| `@AfterThrowing` / `@Around` quan sát hoặc chuyển exception | `assertThrows` kiểm tra type/message/cause, probe advice và trạng thái target; thêm ca không ném lỗi |

Dùng context Spring tối thiểu có target bean, aspect bean và AOP auto-proxy; với interface proxy, lấy bean qua interface thay vì ép sang implementation. Nếu có transaction, cache hoặc async, thêm integration test dành riêng cho outcome tương ứng, vì một test proxy thuần không chứng minh commit, cache hit hoặc asynchronous completion.

## 5. Ví dụ Java 17 chạy được: assert tham số, return value và exception

Project sử dụng Spring Framework 6.2.19, AspectJ Weaver 1.9.24, JUnit 5 và Maven. Test thực hiện **trên Spring-managed proxy**; không cần Spring Boot, database, servlet hay AspectJ load-time weaving.

### 5.1. Cấu trúc project

```text
spring-aop-test-demo/
  pom.xml
  src/main/java/demo/
    TextService.java
    TextServiceImpl.java
    ProbeAspect.java
  src/test/java/demo/
    SpringAopBehaviorTest.java
```

### 5.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-aop-test-demo</artifactId>
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
    </plugins>
  </build>
</project>
```

JUnit 5 chạy qua Maven Surefire; `spring-test` cung cấp Spring TestContext/JUnit Jupiter integration. `@EnableAspectJAutoProxy` yêu cầu AspectJ pointcut support trên classpath nhưng **không cần Java agent hoặc AspectJ weaving**.

### 5.3. TextService.java

`src/main/java/demo/TextService.java`:

```java
package demo;

public interface TextService {
    String greet(String name);
    String fail(String name);
    String passthrough(String input);
}
```

Interface đảm bảo Spring có thể tạo **JDK dynamic proxy**; test inject type `TextService` thay vì ép proxy sang class implementation.

### 5.4. TextServiceImpl.java

`src/main/java/demo/TextServiceImpl.java`:

```java
package demo;

public class TextServiceImpl implements TextService {

    @Override
    public String greet(String name) {
        return "Hi[" + name + "]";
    }

    @Override
    public String fail(String name) {
        throw new IllegalStateException("failure[" + name + "]");
    }

    @Override
    public String passthrough(String input) {
        return "Raw[" + input + "]";
    }
}
```

`greet()` không chủ động trim input; chỉ trả đúng dữ liệu nhận được. Điều này giúp test xác minh thay đổi arguments đến từ `@Around`. `fail()` cố ý ném unchecked exception, `passthrough()` là phương thức **không match** pointcut thử nghiệm.

### 5.5. ProbeAspect.java

`src/main/java/demo/ProbeAspect.java`:

```java
package demo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class ProbeAspect {
    private final List<String> events = new CopyOnWriteArrayList<>();

    public List<String> events() {
        return List.copyOf(events);
    }

    public void reset() {
        events.clear();
    }

    @Around("execution(* demo.TextService.greet(..))")
    public Object normalizeName(ProceedingJoinPoint pjp) throws Throwable {
        String raw = (String) pjp.getArgs()[0];
        events.add("original=[" + raw + "]");

        String normalized = raw.trim();
        return pjp.proceed(new Object[] { normalized });
    }

    @Before("execution(* demo.TextService.greet(..))")
    public void inspectArguments(JoinPoint jp) {
        events.add("received=[" + jp.getArgs()[0] + "]");
    }

    @AfterReturning(
        pointcut = "execution(* demo.TextService.greet(..))",
        returning = "answer"
    )
    public void recordReturn(String answer) {
        events.add("return=" + answer);
    }

    @AfterThrowing(
        pointcut = "execution(* demo.TextService.fail(..))",
        throwing = "error"
    )
    public void recordFailure(IllegalStateException error) {
        events.add("throw=" + error.getMessage());
    }
}
```

Điểm cần hiểu:

- `@Around` ghi dữ liệu gốc, gọi `proceed(new Object[] { normalized })` để target nhận `trim()`.
- `@Before` quan sát arguments của invocation **sau khi `@Around` tiếp tục chuỗi**. Spring sắp advice theo thứ tự và loại advice. Với nhóm advice trong cùng aspect, `@Around` ở ngoài `@Before`, do đó `received` thấy input đã được thay.
- `@AfterReturning` ghi String trả về khi `greet()` thành công. Không dùng nó để sửa giá trị trả cho client.
- `@AfterThrowing` ghi exception type/message khi `fail()` ném `IllegalStateException`. Không nuốt exception.
- List sự kiện chỉ phục vụ **kiểm thử**, giữ trong singleton aspect bean của context test. `CopyOnWriteArrayList` đơn giản hóa an toàn ghi nếu test có concurrency; `reset()` được gọi trước mỗi test để tránh state của các test trước.

Aspect này cố ý áp dụng `@Around` cho `greet` và `@AfterThrowing` cho `fail`; `passthrough` không match bất kỳ advice nào. Điều đó cho phép một assertion âm tính về pointcut.

### 5.6. SpringAopBehaviorTest.java

`src/test/java/demo/SpringAopBehaviorTest.java`:

```java
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = SpringAopBehaviorTest.TestConfig.class)
class SpringAopBehaviorTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        TextService textService() {
            return new TextServiceImpl();
        }

        @Bean
        ProbeAspect probeAspect() {
            return new ProbeAspect();
        }
    }

    @Autowired
    TextService service;

    @Autowired
    ProbeAspect aspect;

    @BeforeEach
    void clearEvents() {
        aspect.reset();
    }

    @Test
    void springHasCreatedProxy() {
        assertTrue(AopUtils.isAopProxy(service));
        assertFalse(service instanceof TextServiceImpl);
    }

    @Test
    void aroundPassesNewArgumentsAndReturnValueToCaller() {
        String returned = service.greet("  Ada  ");

        assertEquals("Hi[Ada]", returned);
        assertTrue(aspect.events().contains("original=[  Ada  ]"));
        assertTrue(aspect.events().contains("received=[Ada]"));
    }

    @Test
    void afterReturningReceivesResult() {
        assertEquals("Hi[Bob]", service.greet(" Bob "));

        List<String> events = aspect.events();
        assertEquals(1, events.stream()
                .filter(e -> e.equals("return=Hi[Bob]"))
                .count());
        assertTrue(events.contains("received=[Bob]"));
        assertFalse(events.stream().anyMatch(e -> e.startsWith("throw=")));
    }

    @Test
    void afterThrowingObservesAndPreservesException() {
        IllegalStateException thrown =
                assertThrows(IllegalStateException.class,
                        () -> service.fail("X"));

        assertEquals("failure[X]", thrown.getMessage());
        assertTrue(aspect.events().contains("throw=failure[X]"));
        assertFalse(aspect.events().stream()
                .anyMatch(e -> e.startsWith("return=")));
    }

    @Test
    void unmatchedMethodHasNoAspectEvents() {
        assertEquals("Raw[A]", service.passthrough("A"));
        assertTrue(aspect.events().isEmpty());
    }

    @Test
    void directNewIsNotProxied() {
        TextService direct = new TextServiceImpl();

        assertEquals("Hi[  Ada  ]", direct.greet("  Ada  "));
        assertTrue(aspect.events().isEmpty());
    }
}
```

Trong ví dụ, `@SpringJUnitConfig` tích hợp SpringExtension và nạp context từ nested `TestConfig`. Spring tìm thấy `ProbeAspect` do `@Bean` đăng ký và tạo proxy cho `TextService`. Test dùng interface; với `@EnableAspectJAutoProxy` mặc định và target có interface, Spring có thể chọn JDK proxy.

### 5.7. Chạy các test

Tại thư mục chứa `pom.xml`:

```bash
mvn -q test
```

Nếu tất cả điều kiện cấu hình đều đúng, Maven exit code **0** và cả **6 test pass**. Chạy không có `-q` để xem tổng kết surefire trong console:

```bash
mvn test
```

Kết quả kỳ vọng trong báo cáo Surefire:

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```

Dòng tổng kết có thể có tên class/timestamp và logging phụ do Maven/Spring tùy môi trường. Đây là **kết quả mong đợi theo assertions**, không giả định bất kỳ hệ thống CI nào đã thực thi lệnh.

## 6. Vì sao sáu test này có giá trị hơn một test log thông thường?

### Test 1: chứng minh đúng integration boundary

`springHasCreatedProxy()` xác minh `service` thực sự là Spring AOP proxy. Nếu thiếu `@EnableAspectJAutoProxy`, thiếu aspect bean hoặc pointcut không match, các test kết quả có thể hỏng và người sửa có tín hiệu tìm đúng cấu hình.

Tuy nhiên `isAopProxy == true` một mình chưa chứng minh **advisor cụ thể** đã áp dụng; vì vậy những test tiếp theo bắt buộc gọi method và assert behavior. Một proxy có thể tồn tại vì hạ tầng khác không liên quan.

### Test 2: kiểm tra cả nguồn và đích của đối số

`aroundPassesNewArgumentsAndReturnValueToCaller()` đồng thời kiểm tra:

1. Caller cung cấp String chứa khoảng trắng hai đầu.
2. `@Around` quan sát nguyên trạng String trước khi normalize.
3. `@Before` quan sát đối số sau khi `@Around` gọi `proceed(Object[])`.
4. Target thực sự nhận `"Ada"`, vì kết quả là `Hi[Ada]`.
5. Caller nhận đúng giá trị target trả ra.

Chỉ assert `"original=[  Ada  ]"` chưa đủ: aspect có thể log nhưng quên forward arguments đã trim. Chỉ assert `"Hi[Ada]"` cũng thiếu bằng chứng aspect quan sát raw input đúng.

### Test 3: kiểm tra binding giá trị trả về

`afterReturningReceivesResult()` chứng minh `@AfterReturning(returning="answer")` nhận String kết quả **sau khi method hoàn thành bình thường**. Nó đếm đúng một event `return`, xác nhận `@Before` nhận arguments đã chuyển đổi và không có exception event.

Không nên test cứng **thứ tự của các advice không có hợp đồng precedence rõ ràng**. Khi test thật sự cần thứ tự, chỉ định `@Order` hoặc cấu hình order thích hợp, rồi assert sequence dựa trên contract đó. Ở đây assert sự kiện và kết quả là đủ.

### Test 4: kiểm tra exception xuyên qua proxy

`afterThrowingObservesAndPreservesException()` làm ba việc:

- `assertThrows` bắt đúng `IllegalStateException` ở **client gọi proxy**.
- Message `failure[X]` chứng minh thông tin từ target không bị biến đổi bất ngờ.
- Aspect đã nhận exception tương ứng trong `@AfterThrowing`, trong khi không có `@AfterReturning` event.

Việc chỉ có log "caught error" từ aspect mà không `assertThrows` có thể che lỗi advice đã **nuốt exception** và khiến caller nhận một kết quả sai.

### Test 5: negative match

`unmatchedMethodHasNoAspectEvents()` gọi `passthrough` qua **chính proxy**. Nó kiểm tra pointcut không match phương thức khác. Đây là phép kiểm tra quan trọng để tránh khớp quá rộng, chẳng hạn dùng `execution(* demo..*(..))` khiến advice can thiệp vào nhiều code ngoài mục tiêu.

### Test 6: đối chứng ngoài container

`directNewIsNotProxied()` gọi `new TextServiceImpl()`. `greet("  Ada  ")` không được `@Around` normalize, kết quả giữ khoảng trắng; aspect ghi **không sự kiện**. Phép thử chứng minh sự khác biệt giữa **lời gọi trên Spring bean proxy** và **đối tượng Java tự tạo**, không phải phương án chính để test aspect.

## 7. Test return value khi @Around chủ động đổi kết quả

Giả sử yêu cầu là chuẩn hóa kết quả do service trả về. Advice có thể:

```java
@Around("execution(* demo.TextService.greet(..))")
public Object uppercase(ProceedingJoinPoint pjp) throws Throwable {
    String result = (String) pjp.proceed();
    return result.toUpperCase(java.util.Locale.ROOT);
}
```

Đây là **biến thể minh họa**, không đưa vào `ProbeAspect` cùng lúc vì sẽ làm thay đổi kỳ vọng của sáu tests hiện có. Khi sử dụng nó, test phải assert client nhận `"HI[ADA]"` nếu target trả `"Hi[Ada]"`. Không nên chỉ assert log ghi nhận kết quả trước normalize.

Nếu advice trả sai type:

```java
@Around("execution(* demo.TextService.greet(..))")
public Object broken(ProceedingJoinPoint pjp) {
    return 123; // Sai với method contract trả String.
}
```

Đây là anti-pattern minh họa. Spring proxy có thể gặp `ClassCastException` hoặc lỗi tương thích return type. Một test public API gọi `TextService.greet()` và assert String bắt lỗi này tốt hơn chỉ unit test riêng advice.

Với các method trả `void`, `null`, primitive và `Optional`, cần tập trung vào **contract thực tế**. Nếu `@Around` return `null` cho method primitive, proxy có thể thất bại khi unbox; nếu method trả `CompletableFuture`, return value immediate là future, không phải kết quả async đã hoàn tất.

## 8. Test nhiều đối số và metadata đúng cách

Khi method có hai hoặc nhiều parameters:

```java
public String transfer(String account, int amount) {
    return account + ":" + amount;
}
```

Test Spring AOP nên nhìn cả số lượng và vị trí đối số:

```java
Object[] args = pjp.getArgs();
if (args.length != 2) {
    throw new IllegalArgumentException("Unexpected signature");
}
return pjp.proceed(new Object[] {
    ((String) args[0]).trim(),
    args[1]
});
```

Các đoạn này là **snippets**, không nằm trong project `TextService`. Với `proceed(Object[])` phải cung cấp số phần tử và type phù hợp method gốc. Nếu nhầm thứ tự hai tham số hoặc truyền `null` vào parameter primitive, test qua proxy sẽ lộ lỗi invocation.

Nếu dùng Spring AOP annotation binding kiểu:

```java
@Before(
    value = "execution(* demo.TransferService.transfer(..)) && args(account,amount)",
    argNames = "account,amount"
)
public void inspect(String account, int amount) {
    // account/amount là các đối số được bind.
}
```

Đây là snippet cần `demo.TransferService` được định nghĩa riêng. Dùng `argNames` giúp Spring/AspectJ bind tham số rõ ràng khi không có metadata tham số runtime đủ tin cậy. Trong project ví dụ chính, `JoinPoint.getArgs()` tránh phụ thuộc tên param của compiler.

Test args nên dùng các giá trị dễ phân biệt (`"  A "`, `17`), tránh chỉ dùng `"x"` và `"x"` khiến đảo vị trí không bị phát hiện. Nên bổ sung boundary tests cho null, empty strings và invalid arguments nếu hợp đồng cho phép hoặc từ chối chúng.

## 9. Kiểm thử exception sâu hơn: type, message, cause, instance

### 9.1. Không vô tình nuốt exception

Một `@Around` có lỗi:

```java
@Around("execution(* demo.TextService.fail(..))")
public Object swallow(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (IllegalStateException e) {
        return null; // Caller không còn nhận exception ban đầu.
    }
}
```

Đây là anti-pattern minh họa. `assertThrows(IllegalStateException.class, ...)` từ JUnit sẽ **fail**, phát hiện việc advice nuốt lỗi. Trong transaction method, nuốt exception còn có thể khiến Spring `TransactionInterceptor` ở ngoài hiểu rằng lời gọi thành công và commit, trừ khi transaction đã được đánh dấu rollback-only hoặc có rule khác.

### 9.2. Thay exception có chủ đích

Nếu policy muốn chuyển exception sang một loại có ý nghĩa cho boundary, `@Around` có thể throw exception mới và giữ `cause`:

```java
try {
    return pjp.proceed();
} catch (IllegalStateException original) {
    throw new IllegalArgumentException("Invalid request", original);
}
```

Test thích hợp:

```java
IllegalArgumentException ex = assertThrows(
    IllegalArgumentException.class,
    () -> service.fail("X")
);
assertEquals("Invalid request", ex.getMessage());
assertTrue(ex.getCause() instanceof IllegalStateException);
```

Hai đoạn này là **biến thể để thay thế đường xử lý exception**, không đồng thời áp dụng cho project ban đầu. Cần phân biệt exception được target ném với exception mới do advice ném; xem vị trí advisor và `@Transactional.rollbackFor` khi có giao dịch.

### 9.3. Kiểm tra cùng instance exception

Nếu contract đòi advice chỉ quan sát và **ném lại đúng instance ban đầu**, có thể tạo target stub ném một exception đã biết, rồi dùng `assertSame(expected, actual)` tại caller. Đây là test mạnh hơn so sánh message khi identity là một phần yêu cầu. Phần lớn ứng dụng chỉ cần xác minh exception type, message và cause; không buộc identity trừ khi có lý do.

### 9.4. Typed @AfterThrowing

`@AfterThrowing(throwing="error")` với `IllegalStateException error` chỉ chạy trên exception khớp type ấy. Nếu target ném `IOException`, advice có typed signature trên `IllegalStateException` không chạy. Khi cần audit **mọi exception**, dùng `Throwable error`, sau đó phân loại trong logic. Tuy nhiên đừng tự bắt mọi `Throwable` để recover một cách tùy tiện; `Error` thường không nên được chuyển thành kết quả thành công.

### 9.5. Ngoại lệ ở advisor khác

Nếu một advice ở bên trong ném exception, `@AfterThrowing` ở ngoài có thể quan sát nó; ngược lại advice phía trong không thể thấy lỗi do một advisor bên ngoài ném **sau khi** nó đã return. Vì vậy tests cho nhiều aspect nên chỉ định `@Order` khi thứ tự quan trọng. Với Spring transaction, test rollback cần kiểm tra **committed database state**, không chỉ việc Java ném exception.

## 10. Tránh test brittle vì thứ tự event hoặc System.out

**Nên dùng một event recorder test-only** như `ProbeAspect.events()`, assert bằng JUnit và reset trong `@BeforeEach`. Nếu dùng `System.out` làm bằng chứng duy nhất, test có thể bị nhiễu bởi log từ Spring/Maven và khó xác định sự kiện nào thuộc lời gọi nào.

Không hardcode thứ tự advice chỉ vì hai method cùng nằm trong class `@Aspect` và xuất hiện theo thứ tự source code. Spring xử lý precedence theo advice type và advisor chain; nhiều advice cùng type hoặc các advisors trùng order có thể không có thứ tự xác định. Nếu logic phụ thuộc ordering, quy định order rõ, test một **sequence có chủ đích**, và kiểm tra tương tác với transaction/cache advisor.

Dùng `assertEquals` trên kết quả cuối cùng và `assertTrue(events.contains(...))` trên event có tính ổn định cao hơn đoán tên CGLIB-generated class hay dòng log DEBUG của Spring. Negative assertion như `assertFalse(events.stream().anyMatch(...))` cũng có ích để phát hiện advice bị gọi ngoài ý muốn.

Nếu chạy JUnit tests song song và cùng sử dụng một cached ApplicationContext có singleton event recorder, `reset()` không bảo đảm isolation giữa các test khác nhau dù collection thread-safe. Khi cần parallel execution, dùng context riêng, event collector theo test invocation hoặc tắt parallel test cho nhóm kiểm tra state shared. Ví dụ này chạy sequential theo cấu hình Maven mặc định.

## 11. Các lỗi thường gặp khi viết test Spring AOP

| Triệu chứng | Nguyên nhân hay gặp | Bước sửa |
| --- | --- | --- |
| `AopUtils.isAopProxy=false` | Chưa bật `@EnableAspectJAutoProxy`, aspect bean chưa đăng ký hoặc pointcut không match | Kiểm tra `TestConfig`, annotation/import và bean |
| Aspect test pass khi gọi trực tiếp method advice, nhưng integration test fail | Test trước chỉ test code Java trong aspect, không test proxy/advisor | Dùng `@SpringJUnitConfig`, gọi Spring bean proxy |
| Method gốc chạy nhưng recorder không có event | Test inject target trực tiếp hoặc self-invocation | Lấy bean từ context, gọi ngoài qua proxy |
| `getArgs()` thấy raw input nhưng target không nhận input mới | `@Around` quên dùng `proceed(newArgs)` | Assert cả recorder và public return/target observation |
| `@AfterReturning` không nhận String | Binding name `returning` sai, return type không tương thích, method ném exception | Kiểm tra signature, annotation và outcome |
| `@AfterThrowing` không chạy | Exception đã bị inner advice nuốt hoặc signature typed không match | Test type/exception propagation và advisor ordering |
| Test chỉ so message của exception | Advice có thể đổi type/cause nhưng giữ message | Assert type, cause hoặc identity khi cần |
| Event thứ tự khác khi thêm cache/transaction | Advisors/`@Order` đã thay đổi | Xem advisor chain và policy của từng interceptor |
| Test dùng `new` mà chờ aspect chạy | `new` không đi qua Spring AOP proxy | Tách unit target test khỏi AOP integration test |
| Test pass ở context nhỏ nhưng fail trong app | Khác auto-configuration, bean registration, advisor order, JDK/CGLIB | Bổ sung integration test gần production |
| `@Around` trả kiểu không hợp lệ | Thay giá trị sai contract return type | Assert ở client qua interface, kiểm tra null/primitive |
| Một test ảnh hưởng test sau | State singleton aspect bean chưa reset, context cache được tái sử dụng | Reset recorder, dùng test isolation phù hợp |

## 12. Khi cần mock/spy trong Spring AOP

Có thể dùng Mockito để kiểm tra **target collaborator** đã được gọi với argument sau khi `@Around` biến đổi. Nhưng phải nhận thức thứ tự proxy/target/mocks:

- **Mock trực tiếp `TextService` rồi bypass Spring AOP** chỉ test Mockito, không chứng minh advice chạy.
- **Spring proxy bao quanh một mocked target** có thể giúp assert pointcut và `proceed(newArgs)`. Cần cấu hình bean target mock rõ ràng để auto-proxy áp dụng.
- **Mock repository collaborator** được inject vào target là cách thường ổn: client gọi service proxy, aspect can thiệp, target gọi mock repository với arguments đã biến đổi.
- **Spy trên proxy hoặc target** cần cẩn thận vì đối tượng được wrap/unwrap khác nhau; không giả định `verify(proxy).method(...)` có semantics giống `verify(target).method(...)`.
- Với `@AfterThrowing`, mock collaborator ném một exception chính xác rồi kiểm tra exception đi từ target ra client và event ở aspect. Tránh mock luôn method được advice theo cách loại bỏ đường gọi thật.

Một test Spring AOP tốt thường ít phải mock chính proxy. Cấu hình context nhỏ, target giả đơn giản và recorder như phần 5 đã đủ kiểm chứng contract proxy/advice.

## 13. Tương tác với @Transactional, @Cacheable và @Async

Trong ứng dụng thật, cùng một method có thể được nhiều infrastructure advisor áp dụng. **Thứ tự advisor có thể ảnh hưởng trực tiếp các assertions**.

**`@Transactional`:** Nếu `@Around` biến đổi hoặc nuốt exception ở bên trong transaction advisor, rollback rule mà transaction interceptor nhìn thấy có thể khác. Nếu advice nằm ngoài transaction advisor, nó có thể quan sát outcome sau commit/rollback. Đừng test transaction correctness chỉ bằng `assertThrows`; cần kiểm tra DB state thực sau commit/rollback.

**`@Cacheable`:** Một cache hit có thể trả kết quả mà không invoke target hay advice nằm ở phía trong caching advisor. Vì vậy test số lần `@Before` được gọi cần xác định intent: đếm tất cả lời gọi client đến proxy hay chỉ cache misses. Cấu hình precedence của custom `@Aspect` và `@EnableCaching(order=...)` nếu cần deterministic behavior.

**`@Async`:** Client có thể nhận `CompletableFuture` trước khi method thực thi hoàn tất trên executor. `@AfterReturning` ở một tầng proxy có thể quan sát **future**, không phải result sau khi future hoàn thành. Test async cần await đúng completion và assert exception được propagate qua future/`ExecutionException` khi phù hợp, không giả định exception từ task luôn được `assertThrows` trực tiếp ở client thread.

**Method Security:** Khi authorization deny trước target, một aspect nằm bên trong security advisor có thể không chạy. Muốn kiểm tra audit cho cả denied request, xác định ranh giới advisor và assert denial outcome phù hợp với Spring Security. Không suy ra "aspect hỏng" khi một advisor được thiết kế để short-circuit.

Bài ví dụ chủ động không bật transaction/cache/async nhằm giữ tests xác minh **AOP contracts riêng**. Khi bổ sung cơ chế khác, hãy mở rộng tests theo trường hợp thực tế thay vì thay đổi kết quả kỳ vọng trong im lặng.

## 14. Kiểm tra target thật, self-invocation và proxy selection

Một Spring bean có interface thường dùng JDK dynamic proxy. Khi test:

```java
TextService bean = context.getBean(TextService.class);
assertTrue(AopUtils.isAopProxy(bean));
```

Không ép kiểu proxy sang `TextServiceImpl`. Nếu target không có interface hoặc bật `proxyTargetClass = true`, Spring có thể dùng CGLIB, nhưng `final` class/method không được advise qua class-based proxy. Method `private` và self-invocation cũng không đi vào AOP proxy như lời gọi ngoài.

Nếu method `outer()` trên target tự gọi `this.greet()`, một `@Around` chỉ match `greet()` sẽ **không tự chạy cho self-invocation**. Integration test nên có ít nhất một lời gọi trực tiếp qua proxy (positive) và một đường nội bộ (negative) nếu kiến trúc phụ thuộc vào đó.

`AopUtils.isAopProxy()` là xác nhận cơ sở, không thể thay cho assertions về tham số, return và exception. Với hệ thống dùng AspectJ weaving thực sự thay Spring proxy, công cụ AopUtils có thể trả `false` dù advice đã weave; bài này đang test Spring AOP proxy, không dùng LTW.

## 15. Checklist review một bộ test Spring AOP

1. **Đúng ApplicationContext:** aspect và target là các bean ở context được test; `@EnableAspectJAutoProxy` hoạt động.
2. **Đúng tham chiếu:** test gọi bean proxy do Spring inject; unit test dùng `new` chỉ là đối chứng.
3. **Đúng pointcut:** có test method khớp và không khớp.
4. **Đối số:** assert raw values, forwarded values, thứ tự và type; nếu biến đổi, test cả kết quả target.
5. **Return:** assert giá trị client nhận, `@AfterReturning` event/binding và số lần gọi khi cần.
6. **Exception:** assert type/message/cause và rằng caller vẫn nhận lỗi; assert `@AfterThrowing` không bị bỏ sót và success advice không chạy trên failure.
7. **Around contract:** kiểm tra `proceed()` được gọi đúng số lần nếu yêu cầu, không có accidental short circuit.
8. **Advice order:** chỉ assert thứ tự khi có ordering contract rõ; nếu kết hợp advisors khác phải cấu hình thứ tự phù hợp.
9. **Test isolation:** reset event state mỗi test; không chạy parallel trên singleton recorder dùng chung nếu chưa giải quyết isolation.
10. **Infrastructure:** với transaction/cache/async/security phải bổ sung tests xác nhận semantics tương ứng thay vì chỉ test AOP.
11. **Failure visibility:** không chỉ log lỗi rồi cho test pass; dùng assertions đủ mạnh để một regression tạo ra test failure.
12. **Lệnh chạy:** ghi rõ JDK/Maven/dependency versions và test report có số cases được thực thi.

## 16. Câu hỏi tự kiểm tra

**Hỏi:** Tại sao `new TextServiceImpl()` gọi `greet()` không thấy event của `ProbeAspect`?  
**Đáp:** Object đó không đi qua Spring AOP proxy; Spring proxy advisors chỉ áp dụng cho invocation đi qua bean proxy.

**Hỏi:** `@Before` có phải nơi nên mutate `JoinPoint.getArgs()` để thay tham số không?  
**Đáp:** Không. Nếu cần thay arguments truyền tiếp tới method, dùng `@Around` và `proceed(Object[])`.

**Hỏi:** `@AfterReturning` có đổi kết quả trả về cho caller bằng `return "other";` không?  
**Đáp:** Không. Nó dùng để quan sát return value khi lời gọi thành công. Muốn thay kết quả, sử dụng `@Around` phù hợp.

**Hỏi:** `@AfterThrowing` có đảm bảo client nhận exception ban đầu không?  
**Đáp:** Không tuyệt đối; advice khác có thể catch/convert error. Test phải assert exception trên lời gọi của client, không chỉ event log của advice.

**Hỏi:** Vì sao nên kiểm tra một method không match pointcut?  
**Đáp:** Để phát hiện aspect áp dụng quá rộng và tạo side effect lên những method không thuộc hợp đồng.

**Hỏi:** Vì sao trong bài này dùng `@SpringJUnitConfig`?  
**Đáp:** Để tạo Spring ApplicationContext, kích hoạt AOP proxy và inject đúng service/aspect beans, cho phép test cơ chế Spring thật.

**Hỏi:** Nếu cùng method có `@Cacheable` và aspect recording, cache hit có chắc luôn tạo record không?  
**Đáp:** Không. Điều đó phụ thuộc vị trí của aspect so với caching advisor; caching có thể short-circuit phần phía trong.

## Tài liệu Spring chính thức

- Spring Framework — AOP Concepts: https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html
- Spring Framework — Declaring Advice (@Around, @AfterReturning, @AfterThrowing): https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — Spring TestContext Framework with JUnit Jupiter: https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/support-classes.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — Spring AOP API Advice: https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-testing-007">Kiểm thử self-invocation, final/private và đối tượng tạo ngoài container</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử self-invocation, final/private và đối tượng tạo ngoài container trong Spring AOP

## 1. Mục tiêu của bài kiểm thử

Một aspect trong Spring có thể được khai báo đúng bằng `@Aspect`, có pointcut khớp method, nhưng advice vẫn không chạy. Nguyên nhân thường là **ranh giới proxy**: Spring AOP áp dụng advice cho lời gọi method đi qua proxy của Spring, không tự động viết advice vào mọi lời gọi Java.

Những trường hợp cần kiểm thử riêng:

1. **Gọi từ ngoài qua Spring proxy**: một method bình thường đủ điều kiện có advice.
2. **Self-invocation**: `outer()` gọi `this.inner()` trong cùng target; lời gọi nội bộ không qua proxy, nên advice dành cho `inner()` không chạy.
3. **Method `final` trong CGLIB class proxy**: proxy không thể override để intercept method đó.
4. **Method `private`**: không thể được proxy override; lời gọi private nằm trong target, không tạo Spring AOP method join point có thể advise.
5. **Object tạo bằng `new` ngoài `ApplicationContext`**: không nhận Spring auto-proxy và không có advice do container tạo.
6. **Gọi qua một Spring bean khác**: có thể sửa vấn đề self-invocation khi lời gọi tới target đi qua Spring-injected proxy.
7. **JDK interface proxy**: cần kiểm thử riêng vì một implementation method `final` **vẫn có thể được intercept thông qua interface**; quy tắc “final luôn không có advice” chỉ đúng khi cơ chế proxy phải override method ấy.

Đây là bài chuyên sâu về **kiểm thử Spring AOP**, gồm cơ chế proxy, cách dựng ngữ cảnh test, assertion trực tiếp và cách giải thích kết quả. Các khái niệm AspectJ chung được giới hạn ở mức phục vụ Spring.

## 2. Mô hình cần chứng minh bằng test

~~~text
External caller
    |
    v
Spring AOP proxy của SampleService
    |
    +-- gọi outer() ──► advice(outer) ──► target.outer()
    |                                         |
    |                                         +-- this.inner()
    |                                               └─ target.inner()
    |                                                  (không advice(inner))
    |
    +-- gọi inner() ──► advice(inner) ──► target.inner()
    |
    +-- gọi finalResult() [CGLIB] ──► method final không override/intercept
    |
    +-- gọi callPrivate() ──► advice(callPrivate) ──► target.callPrivate()
                                                       └─ privateStep()
                                                          (không advice(privateStep))

new SampleService().inner()
    └─ gọi trực tiếp raw Java object, không Spring AOP proxy

ClientService.call() ──► injected SampleService proxy ──► advice(inner)
~~~

**Điều phải đo trong test** là số lượng/tên **advice invocation**, đồng thời kiểm tra kết quả method để xác nhận target thực sự đã chạy. Chỉ test giá trị trả về `"inner"` không chứng minh aspect được kích hoạt.

Cần phân biệt:
- `AopUtils.isAopProxy(bean)` xác nhận object là Spring AOP proxy.
- `AopUtils.isCglibProxy(bean)` / `isJdkDynamicProxy(bean)` xác nhận proxy strategy.
- Counter/recorder đặt trong advice chứng minh **join point có thực sự đi vào advice**.
- Giá trị trả về của service xác nhận **business method vẫn thực thi**, kể cả khi advice bị bỏ qua.

## 3. Vì sao self-invocation bỏ qua advice?

Spring AOP mặc định dùng proxy. Với object Spring cấp cho caller:

~~~java
service.outer();
~~~

Nếu `service` là proxy, interceptor chain có thể xử lý execution của `outer`. Nhưng bên trong target:

~~~java
public String outer() {
    return this.inner();
}
~~~

`this` trỏ tới **target đang thực thi**, không phải Spring proxy mà caller giữ. Vì vậy execution của `inner` trong lời gọi này không đi qua AOP interceptor chain. Ngay cả khi `inner` là `public` và match pointcut, callback advice trên `inner` cũng không được kích hoạt cho đường gọi `this.inner()`.

Điều đó cũng áp dụng cho lời gọi `inner()` không ghi rõ `this`; Java hiểu tương đương tại instance hiện hành.

Nếu caller gọi `service.inner()` **trực tiếp qua proxy** thì cùng method `inner` có thể được advise. Hai trường hợp chỉ khác **đường đi của lời gọi**, không khác annotation hay pointcut.

Trong Spring, đây là lý do những tính năng dựa trên proxy như `@Transactional`, `@Cacheable` hoặc method security cũng cần đặc biệt chú ý self-invocation. Một unit test gọi trực tiếp target class có thể không mô phỏng đúng behavior khi chạy trong `ApplicationContext`.

## 4. Final và private: giới hạn cần diễn đạt chính xác

### 4.1. Với CGLIB class-based proxy

CGLIB xây proxy bằng subclass của target class. Từ đó:
- **Class `final`** không thể được subclass để tạo CGLIB proxy.
- **Method `final`** không thể được subclass override, vì vậy Spring AOP **không thể advise method đó thông qua CGLIB interception**.
- **Method `private`** không thể override và không tham gia kiểu dispatch cần thiết cho Spring proxy interception.
- Method package-private trong superclass **ở một package khác**, không thấy được từ subclass proxy, về cơ bản cũng không thể advise như một public/protected method.

Đừng suy ra rằng một method có thể làm advice chạy chỉ vì pointcut `execution(...)` về mặt cú pháp match tên của nó. Spring còn bị giới hạn bởi **proxy type**, visibility và cách lời gọi đến bean.

### 4.2. Với JDK interface-based proxy

JDK dynamic proxy implements interface và intercept lời gọi **qua interface**. Nó không cần subclass implementation, do đó một class implementation `final` hoặc implementation method `final` **có thể vẫn được advise qua JDK proxy nếu method được công bố trên interface** và advisor/pointcut match.

Vì vậy, câu “`final` không bao giờ được Spring AOP advise” là quá rộng. Phát biểu chính xác hơn: **CGLIB không advise được final method; JDK interface proxy có thể intercept lời gọi interface dẫn tới implementation final**.

Một `private` method không thuộc hợp đồng interface nghiệp vụ và không thể được caller gọi qua JDK dynamic proxy, nên các test interface proxy cũng không khiến private method thành join point có thể intercept.

### 4.3. Object ngoài container

Object tạo bằng:

~~~java
SampleService raw = new SampleService();
raw.inner();
~~~

là Java instance thuần. Spring không tự bọc object này vì nó không được lấy từ bean factory qua quy trình auto-proxy. Nếu cùng class có một bean Spring được proxy, hai object vẫn có behavior aspect khác nhau.

Cần phân biệt điều này với **AspectJ compile-time/load-time weaving**: AspectJ bytecode weaving có thể advise object do `new` tạo nếu class đã được weave; đây **không phải** behavior mặc định của Spring AOP. Bài test sau cố ý chỉ bật Spring AOP proxy.

## 5. Project Java 17+ và JUnit Jupiter: test ranh giới Spring AOP

Để xác nhận theo behavior thực tế, ví dụ dùng **Spring Framework 6.1.x**, `@EnableAspectJAutoProxy(proxyTargetClass = true)`, JUnit Jupiter và Spring TestContext. Cấu hình test tạo context thực, aspect thực, proxy thực. Bài không dùng mock advice, không chỉ kiểm tra annotation.

Cấu trúc project:

~~~text
build.gradle
src/main/java/demo/limits/
    SampleService.java
    CallRecorder.java
    TraceAspect.java
    ClientService.java
    AopTestConfig.java
src/test/java/demo/limits/
    ProxyBoundaryTests.java
~~~

### 5.1. Dependency Gradle

**`build.gradle`**

~~~groovy
plugins {
    id 'java'
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

test {
    useJUnitPlatform()
    testLogging {
        events 'passed', 'failed'
    }
}
~~~

`spring-aop` cung cấp hạ tầng proxy; `aspectjweaver` cung cấp annotation và hỗ trợ xử lý biểu thức pointcut `@AspectJ`. `spring-test` giúp JUnit quản lý `ApplicationContext` qua `@SpringJUnitConfig`; JUnit Jupiter cung cấp `@Test`, `@BeforeEach` và assertions.

Các version được cố định để làm ví dụ có thể tái tạo. Trong dự án Spring Boot có dependency management, nên dùng starter/test BOM của dự án và **tránh phối nhiều version Spring Framework khác nhau**.

### 5.2. Target class cần kiểm thử

**`src/main/java/demo/limits/SampleService.java`**

~~~java
package demo.limits;

public class SampleService {

    public String outer() {
        return this.inner();
    }

    public String inner() {
        return "inner";
    }

    public final String finalResult() {
        return "final";
    }

    public String callPrivate() {
        return privateStep();
    }

    private String privateStep() {
        return "private";
    }
}
~~~

Lý do cố ý thiết kế như trên:
- `outer()` và `inner()` đều là `public`, nên nếu có proxy interception chúng là candidate thích hợp.
- `outer()` gọi `this.inner()`, tạo một self-invocation rõ ràng.
- `finalResult()` có `final` để chứng minh giới hạn override của CGLIB.
- `callPrivate()` là public entry point, nhưng chỉ gọi `privateStep()` từ trong target. Test phân biệt advice của outer public method với private method.
- Các method trả literal và không phụ thuộc mutable instance field. Đây là chi tiết hữu ích trong demo: một final method thực thi trên CGLIB proxy có thể không đi qua cùng cơ chế target dispatch như method bình thường. Tránh dùng field state ở final method khi chỉ cần kiểm chứng interception.

### 5.3. Recorder đếm advice, không đếm lời gọi target

**`src/main/java/demo/limits/CallRecorder.java`**

~~~java
package demo.limits;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CallRecorder {

    private final CopyOnWriteArrayList<String> calls =
            new CopyOnWriteArrayList<>();

    public void record(String method) {
        calls.add(method);
    }

    public List<String> snapshot() {
        return List.copyOf(calls);
    }

    public void clear() {
        calls.clear();
    }
}
~~~

Recorder là một bean độc lập. Khi aspect chạy, nó ghi tên method vào danh sách. JUnit có thể assertion các sự kiện sau từng hành động.

`CopyOnWriteArrayList` giúp việc ghi/đọc đơn giản nếu một lời gọi tình cờ xảy ra ở thread khác. Đây **không phải** lời khuyên dùng copy-on-write cho workload ghi log dày đặc trong production; ví dụ tập trung vào deterministic assertions.

### 5.4. Aspect áp dụng trên SampleService

**`src/main/java/demo/limits/TraceAspect.java`**

~~~java
package demo.limits;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {

    private final CallRecorder recorder;

    public TraceAspect(CallRecorder recorder) {
        this.recorder = recorder;
    }

    @Before("execution(* demo.limits.SampleService.*(..))")
    public void beforeServiceMethod(JoinPoint joinPoint) {
        recorder.record(joinPoint.getSignature().getName());
    }
}
~~~

Pointcut cố tình match các method trong `SampleService` ở mức biểu thức, nên không thiên lệch về riêng `inner`. Kết quả recorder cho thấy điều gì **thực sự được Spring proxy intercept**, chứ không chỉ điều gì có tên match.

Không thêm lời gọi tới `recorder.record(...)` vào code target: làm vậy sẽ biến test thành test logic nghiệp vụ thay vì test advice.

### 5.5. Bean thứ hai để chứng minh lời gọi qua proxy

**`src/main/java/demo/limits/ClientService.java`**

~~~java
package demo.limits;

public class ClientService {

    private final SampleService service;

    public ClientService(SampleService service) {
        this.service = service;
    }

    public String callInner() {
        return service.inner();
    }
}
~~~

Nếu Spring inject proxy `SampleService` vào `ClientService`, lời gọi `service.inner()` đi qua proxy và nhận advice. Đây là điểm đối chứng với `SampleService.outer()` sử dụng `this.inner()`.

### 5.6. Cấu hình Spring cho test

**`src/main/java/demo/limits/AopTestConfig.java`**

~~~java
package demo.limits;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopTestConfig {

    @Bean
    SampleService sampleService() {
        return new SampleService();
    }

    @Bean
    CallRecorder callRecorder() {
        return new CallRecorder();
    }

    @Bean
    TraceAspect traceAspect(CallRecorder recorder) {
        return new TraceAspect(recorder);
    }

    @Bean
    ClientService clientService(SampleService service) {
        return new ClientService(service);
    }
}
~~~

Vì `proxyTargetClass = true`, bean `SampleService` dùng CGLIB class proxy (đối với target có method đủ điều kiện). `TraceAspect` là Spring bean nhờ `@Bean`; Spring AOP được bật nhờ `@EnableAspectJAutoProxy`. Không cần Spring Boot để chứng minh cơ chế này.

Nếu class cấu hình bị bỏ annotation hoặc aspect không được đăng ký, bean có thể vẫn trả đúng dữ liệu nhưng recorder không ghi gì. Đó là lý do assertion phải kiểm tra cả **loại proxy** lẫn **advice thực sự chạy**.

## 6. JUnit 5: kiểm thử từng trường hợp

**`src/test/java/demo/limits/ProxyBoundaryTests.java`**

~~~java
package demo.limits;

import java.util.ArrayList;
import java.util.List;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(AopTestConfig.class)
public class ProxyBoundaryTests {

    @Autowired
    private SampleService service;

    @Autowired
    private ClientService client;

    @Autowired
    private CallRecorder recorder;

    @BeforeEach
    void resetRecorder() {
        recorder.clear();
    }

    @Test
    void externalInvocationRunsAdviceButSelfInvocationSkipsInnerAdvice() {
        assertTrue(AopUtils.isAopProxy(service));
        assertTrue(AopUtils.isCglibProxy(service));

        assertEquals("inner", service.outer());
        assertEquals(List.of("outer"), recorder.snapshot());

        assertEquals("inner", service.inner());
        assertEquals(List.of("outer", "inner"), recorder.snapshot());
    }

    @Test
    void finalMethodOnCglibProxyIsNotAdvised() {
        assertTrue(AopUtils.isCglibProxy(service));

        assertEquals("final", service.finalResult());
        assertEquals(List.of(), recorder.snapshot());
    }

    @Test
    void privateMethodIsNotAdvisedEvenWhenCalledFromPublicMethod() {
        assertEquals("private", service.callPrivate());
        assertEquals(List.of("callPrivate"), recorder.snapshot());
    }

    @Test
    void instanceCreatedWithNewIsOutsideSpringAopProxy() {
        SampleService raw = new SampleService();

        assertFalse(AopUtils.isAopProxy(raw));
        assertEquals("inner", raw.inner());
        assertEquals(List.of(), recorder.snapshot());
    }

    @Test
    void callingAcrossBeanBoundaryReentersProxy() {
        assertEquals("inner", client.callInner());
        assertEquals(List.of("inner"), recorder.snapshot());
    }

    @Test
    void jdkInterfaceProxyCanAdviseFinalImplementationMethod() {
        List<String> seen = new ArrayList<>();

        ProxyFactory factory = new ProxyFactory(new FinalGateway());
        factory.setInterfaces(Gateway.class);
        factory.setProxyTargetClass(false);
        factory.addAdvice((MethodInterceptor) invocation -> {
            seen.add(invocation.getMethod().getName());
            return invocation.proceed();
        });

        Gateway proxy = (Gateway) factory.getProxy();

        assertTrue(AopUtils.isJdkDynamicProxy(proxy));
        assertEquals("final-via-interface", proxy.execute());
        assertEquals(List.of("execute"), seen);
        assertFalse(proxy instanceof FinalGateway);
    }

    public interface Gateway {
        String execute();
    }

    public static final class FinalGateway implements Gateway {
        @Override
        public final String execute() {
            return "final-via-interface";
        }
    }
}
~~~

### Từng assertion chứng minh điều gì?

| Test method | Điều kiện quan sát | Kết quả cần có |
| --- | --- | --- |
| `externalInvocationRunsAdviceButSelfInvocationSkipsInnerAdvice` | Gọi `outer()` qua proxy; sau đó gọi `inner()` riêng qua proxy | `["outer"]`, sau đó `["outer","inner"]` |
| `finalMethodOnCglibProxyIsNotAdvised` | Gọi final method qua CGLIB proxy | Method trả `"final"`, recorder rỗng |
| `privateMethodIsNotAdvisedEvenWhenCalledFromPublicMethod` | Gọi `callPrivate()` qua proxy | Chỉ `["callPrivate"]`, không có `privateStep` |
| `instanceCreatedWithNewIsOutsideSpringAopProxy` | Gọi raw object `new SampleService()` | `isAopProxy=false` và recorder rỗng |
| `callingAcrossBeanBoundaryReentersProxy` | `ClientService` giữ Spring-injected proxy | `["inner"]` |
| `jdkInterfaceProxyCanAdviseFinalImplementationMethod` | Tự tạo JDK proxy bằng `ProxyFactory` cho class/implementation method final | `["execute"]` mặc dù implementation final |

**Test JDK** sử dụng `ProxyFactory` của **Spring AOP** với `MethodInterceptor` do test cung cấp để cô lập sự khác biệt của proxy mechanism; nó không dùng `@Aspect` thứ hai. Năm test đầu dùng `@Aspect` thật trong Spring context. Vì thế, ngoại lệ về `final` được xác nhận ở tầng proxy engine mà không phụ thuộc một pointcut AspectJ khác.

Trong test cuối, interface `Gateway` là public và implementation `FinalGateway` là public static final nested class trong một lớp test public, nên JDK interface proxy có thể truy cập hợp đồng công khai. `FinalGateway` không bị subclass; JDK proxy chỉ implement `Gateway` và chuyển tiếp lời gọi đến instance target.

### Cách chạy

Với Gradle wrapper đã được tạo trong project:

~~~bash
./gradlew test --tests demo.limits.ProxyBoundaryTests
~~~

Trên Windows PowerShell:

~~~powershell
.\gradlew.bat test --tests demo.limits.ProxyBoundaryTests
~~~

Nếu project chưa có Gradle wrapper nhưng đã cài Gradle:

~~~bash
gradle test --tests demo.limits.ProxyBoundaryTests
~~~

Khi mọi assertion đúng, **kỳ vọng** có 6 test passes, 0 failure. Vì output của Gradle khác nhau theo version và cấu hình logging, không nên khóa test theo chuỗi log Gradle; dùng test assertions ở trên làm bằng chứng chính.

Ví dụ này không yêu cầu chạy một web server, database, Mockito hay AspectJ weaving agent. Các class đủ điều kiện được Spring context tự tạo proxy theo cấu hình.

## 7. Lý do không nên chỉ viết unit test bằng new hoặc Mockito mock

Giả sử unit test làm:

~~~java
SampleService service = new SampleService();
assertEquals("inner", service.inner());
~~~

Test trên chứng minh **business method** hoạt động, nhưng không chứng minh Spring tạo AOP proxy, không chứng minh pointcut match, và không chứng minh advice chạy. Tương tự, một Mockito mock riêng lẻ thường không mô phỏng tự động auto-proxy creator và các advisor được Spring đăng ký.

Cách phân tầng kiểm thử hữu ích:

- **Unit test target**: khởi tạo class trực tiếp để kiểm tra thuật toán thuần, không trông đợi AOP.
- **Spring integration test**: load một `ApplicationContext` tối thiểu có `@EnableAspectJAutoProxy`, aspect và target bean; inject Spring-managed proxy và assert advice.
- **Test feature dựa trên Spring AOP**: nếu thực tế là `@Transactional`, `@Cacheable` hoặc method security, test phải có đúng transaction manager/cache manager/security configuration và verify **observable outcome** của feature; counter aspect chỉ giúp giải thích proxy semantics.
- **Test ở ứng dụng Spring Boot**: có thể dùng `@SpringBootTest` hoặc một slice/config phù hợp khi wiring thực tế phức tạp. Tránh kéo toàn bộ app khi chỉ cần chứng minh auto-proxy behavior.

`@SpringJUnitConfig(AopTestConfig.class)` trong ví dụ sử dụng Spring TestContext thực, không phải một test toàn app. Nó phù hợp vì cần kiểm chứng **Spring bean lifecycle, advisor và proxy**.

## 8. Các kiểm thử âm tính cần làm rõ nguyên nhân, không chỉ khẳng định advice không chạy

**Một test recorder rỗng chưa đủ** để biết tại sao advice không chạy. Các khả năng bao gồm:
- Aspect chưa được đăng ký vào đúng context.
- Auto-proxy bị tắt hoặc bean được tạo quá sớm.
- Pointcut không match type/method.
- Đang cầm raw target thay vì proxy.
- Lời gọi bỏ qua proxy (self-invocation).
- CGLIB không intercept method `final`/`private`.
- Bean nằm trong context khác hoặc bị mock/replacement làm thay đổi proxy.
- Method không được caller gọi do branch logic.

Vì thế, bộ test trên dùng **đối chứng dương tính** là gọi `service.inner()` từ ngoài qua proxy, cùng với `AopUtils.isCglibProxy(service)`. Nếu đối chứng dương tính fail, không được lấy recorder rỗng ở test `final` làm bằng chứng kết luận final là nguyên nhân.

Có thể thêm helper chẩn đoán trong một test:

~~~java
Object bean = service;
System.out.println("isAopProxy=" + AopUtils.isAopProxy(bean));
System.out.println("isCglibProxy=" + AopUtils.isCglibProxy(bean));
System.out.println("isJdkDynamicProxy=" + AopUtils.isJdkDynamicProxy(bean));
System.out.println("targetClass=" + AopUtils.getTargetClass(bean).getName());
~~~

Đây là **snippet trong test đã có biến `service` và import `AopUtils`**, không phải class Java độc lập. Nếu bean implement `org.springframework.aop.framework.Advised`, có thể kiểm tra `getAdvisors()` để chứng minh advisor nào thực sự được gắn vào proxy.

Không dựa duy nhất vào tên class có `$$SpringCGLIB$$` hoặc chuỗi proxy-generated class; những chuỗi đó là chi tiết implementation và có thể thay đổi giữa version.

## 9. Điều gì xảy ra với final class và final method khi đổi proxy strategy?

### 9.1. Final method khi dùng CGLIB

Trong test, `SampleService` không phải final class, vì vậy Spring có thể tạo subclass CGLIB. Nhưng `finalResult()` là final method và không thể override. Test xác nhận method **vẫn trả kết quả** mà recorder advice không chạy.

Khi method final dựa vào mutable state hoặc gọi logic khác, behavior trên object proxy có thể gây những kết quả khó hiểu do cách Spring dùng target và CGLIB subclass. Vì vậy, không thiết kế điểm cần transaction/security/cache advice dưới method final khi ứng dụng sử dụng class-based proxy.

### 9.2. Final class khi dùng CGLIB

Nếu đổi cả `SampleService` thành:

~~~java
public final class SampleService {
    public String inner() {
        return "inner";
    }
}
~~~

đây là **một phiên bản thay thế của class trong ví dụ**, không chèn song song với file đang chạy. Với cấu hình `proxyTargetClass=true` và advisor cần proxy class này, Spring CGLIB không subclass được `SampleService`. Khởi tạo context có thể thất bại khi Spring cố tạo proxy; không nên viết assertion rằng bean là CGLIB proxy và vẫn hoạt động bình thường.

Nếu class final implement một interface nghiệp vụ, cân nhắc **JDK interface proxy** và inject theo interface thay vì concrete class. Phải kiểm tra configuration vì Spring Boot có thể bật class-based proxies theo mặc định và một số cấu hình auto-proxy chia sẻ chính sách proxy hiệu lực.

### 9.3. Final implementation method với JDK proxy

Test `jdkInterfaceProxyCanAdviseFinalImplementationMethod` ở mục 6 chứng minh một ngoại lệ hợp lệ: `FinalGateway.execute()` là final nhưng `Gateway.execute()` là phương thức interface. Spring JDK proxy intercept lời gọi **qua `Gateway`**, rồi chuyển tới method implementation final. Không cần override method trong class.

Điều này khác với CGLIB subclass proxy. Đừng sửa test final CGLIB bằng cách áp dụng kỳ vọng JDK proxy, hoặc suy ngược kỳ vọng của CGLIB sang mọi Spring application.

### 9.4. Private method

Trong Spring proxy AOP, method private không thể được override/công bố qua interface proxy để advise như public method. Nếu method public được advise rồi gọi private helper, advice chỉ xuất hiện tại public entry point.

Nếu cần enforce cross-cutting behavior như transaction cho code bên trong private method, giải pháp thường là đặt boundary transaction/advice trên **public service method được gọi qua proxy** hoặc tái cấu trúc sang một bean khác có public contract phù hợp. Không đơn giản gắn `@Transactional` trên private helper rồi kỳ vọng transaction mới xuất hiện.

## 10. Cách xử lý self-invocation trong ứng dụng Spring

### 10.1. Ưu tiên tách bean và gọi qua dependency được inject

Ví dụ `ClientService.callInner()` trong test giữ `SampleService` do Spring inject. Call tới `service.inner()` đi vào proxy và advice chạy.

Một ứng dụng có `OrderService` điều phối logic và `PaymentService` thực hiện bước cần transaction có thể tách hai bean:

~~~java
@Service
public class OrderService {
    private final PaymentService paymentService;

    public OrderService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public void placeOrder() {
        paymentService.charge();
    }
}

@Service
public class PaymentService {
    @Transactional
    public void charge() {
        // code nghiệp vụ chạy trong transaction nếu
        // transaction infrastructure/manager đã cấu hình đúng
    }
}
~~~

Đây là **snippet thiết kế**: cần import `@Service`, `@Transactional` và cấu hình transaction manager phù hợp. Mục tiêu là lời gọi có cross-cutting behavior đi qua **Spring-injected service proxy**.

### 10.2. Self-injection và AopContext.currentProxy()

Spring có các cách khác như self injection hoặc `AopContext.currentProxy()`, nhưng chúng làm mã nghiệp vụ phụ thuộc ranh giới proxy, có rủi ro circular dependency và cần cấu hình/chính sách proxy phù hợp.

`AopContext.currentProxy()` chỉ có khi proxy được cấu hình `exposeProxy=true` và lời gọi hiện tại thực sự đi qua ngữ cảnh invocation phù hợp. Không dùng giải pháp này như mặc định, đặc biệt trong asynchronous code nơi context thread-bound có thể không còn.

### 10.3. Nếu cần advice cho self-invocation thật sự

Spring AOP proxy có giới hạn tự nhiên. **AspectJ compile-time weaving hoặc load-time weaving** có thể xử lý execution join points trực tiếp trong bytecode, bao gồm nhiều self-invocation, với setup weaving và semantics riêng. Không được kết luận chỉ thêm `@EnableAspectJAutoProxy` là đã bật AspectJ weaving.

Lựa chọn kỹ thuật phải dựa trên yêu cầu thật: phần lớn trường hợp trong Spring application có thể giải quyết bằng tách service boundary rõ ràng mà không phải bật LTW.

## 11. Kết hợp với @Transactional, @Cacheable và method security

Các annotation trong Spring thường được thực hiện qua advisor/interceptor hoặc cơ chế chuyên biệt. Vấn đề self-invocation, final/private và raw object thường xuất hiện trong các lỗi thực tế:

| Feature | Lỗi kiểm thử dễ gặp | Cách thiết kế test |
| --- | --- | --- |
| `@Transactional` | Public outer method gọi `this.innerTransactional()`, không mở transaction riêng bằng proxy; private/final method CGLIB không được intercept như mong đợi | Gọi bean từ Spring context; dùng transaction manager thật, xác minh transaction state/commit/rollback có ý nghĩa |
| `@Cacheable` | Self-call bypass cache advice; gọi raw object vẫn chạy method thay vì cache | Dùng cache manager và gọi qua proxy hai lần; assert execution counter hoặc cache result |
| Method security như `@PreAuthorize` | Test trực tiếp target bypass method security interceptor | Bật method-security configuration, gọi qua proxy trong security context hợp lệ/không hợp lệ |
| Custom `@Aspect` audit | Advice không chạy trên self-invocation, private hoặc final CGLIB method | Dùng recorder, có positive-control test giống bài này |

**Đừng kết luận chỉ từ annotation hiện diện** trên source. Test cần kiểm tra bean đã được proxy và feature hoạt động trên đường gọi thực tế.

Với Spring transaction, còn phải chú ý `@Transactional` đặt trên **outer public method** có thể mở transaction cho toàn bộ `outer`; khi nó gọi `this.inner()`, code inner chạy *bên trong transaction đang tồn tại*. Điều bị bỏ qua là **interceptor riêng cho inner**, không nhất thiết toàn bộ transaction biến mất. Cụ thể, `REQUIRES_NEW` trên inner sẽ không tự được xử lý bởi proxy khi self-invocation. Test cần đo đúng propagation/rollback behavior, không chỉ gọi `isActualTransactionActive()` và suy luận thiếu cơ sở.

Tương tự, nếu external caller đã đi qua security interceptor của outer method, một inner method được gọi trực tiếp vẫn có thể chạy dưới security context hiện hữu; vấn đề là **kiểm tra quyền riêng ở method inner** không được thực thi thông qua proxy.

## 12. Đừng nhầm behavior Spring AOP với Java visibility hay Mockito

Một vài kết luận thường được viết quá mức:
- “Method private không thể gọi” là sai: target hoàn toàn có thể gọi private method của chính nó. Điều đúng là **Spring proxy không advise riêng private method**.
- “Method final không bao giờ có Spring AOP” là sai: JDK interface proxy có thể intercept lời gọi interface dẫn tới implementation final. **CGLIB** mới bị giới hạn override.
- “Method gọi bên trong cùng bean không chạy” là sai: method vẫn chạy, **advice của lời gọi nội bộ** mới bị bypass.
- “@Aspect chạy với mọi object cùng class” là sai với Spring auto-proxy: object tạo bằng `new` không tự có Spring advice.
- “Test class dưới SpringExtension thì mọi `new` object đều được proxy” là sai: load context không biến `new` thành bean factory call.
- “Method có `@Transactional` là đang có transaction” là suy luận thiếu bằng chứng: proxy, transaction manager, propagation và đường gọi đều quan trọng.

Nếu dùng Mockito spy/mock bean trong Spring integration test, hãy xác định đang quan sát **proxy**, **target** hay object thay thế. Stubbing method có thể thay đổi đường invocation, và test giả tạo có thể che lỗi self-invocation. Bộ test ở mục 6 giữ aspect thật và target thật để xác minh cơ chế Spring.

## 13. Cách debug khi một test thất bại

| Triệu chứng | Nguyên nhân cần xem | Kiểm tra cụ thể |
| --- | --- | --- |
| Test `outer` không có `"outer"` | Context chưa tạo proxy hoặc pointcut không match | `AopUtils.isAopProxy`, advisor list, aspect bean |
| `outer` ghi cả `"outer","inner"` không như dự đoán | Có weaving bổ sung, self-reference đặc biệt, hoặc test đã gọi method khác | Xác minh cấu hình chỉ Spring proxy, recorder reset, đường gọi |
| `finalResult` xuất hiện trong recorder khi dự kiến CGLIB bỏ qua | Test thực tế đang dùng JDK proxy hoặc mechanism khác | `isCglibProxy` / `isJdkDynamicProxy`, kiểm tra weaving |
| Gọi final method gây state bất thường | Final method chạy trên CGLIB proxy, không qua target dispatch như method override được | Đừng đặt state-sensitive logic ở final method cần proxy interception |
| `new SampleService()` cũng có advice | Class có thể đã được AspectJ woven hoặc test gọi nhầm bean | Phân biệt LTW/CTW với Spring AOP, kiểm tra instrumentation |
| `ClientService.callInner()` không có advice | Inject raw object, target do tự tạo, hoặc method không match pointcut | Inspect bean injection, target bean registration |
| @Transactional self-call dường như vẫn “có transaction” | Transaction outer đang hoạt động, inner không được intercept riêng | Kiểm tra propagation semantics và transaction boundary |
| Test chạy đơn lẻ đúng nhưng chạy suite sai | Recorder/cache/context bị chia sẻ hoặc test không reset state | `@BeforeEach`, Spring context caching, isolation/concurrency |
| CGLIB không tạo được proxy | Target class final, classloader/module restrictions hoặc lỗi version | Đọc root cause startup, xác nhận proxyTargetClass |

Một quy trình debug ngắn:
1. Chạy positive-control: gọi `service.inner()` trực tiếp qua injected bean và xác nhận recorder có `inner`.
2. Xác nhận proxy type, target class và danh sách advisor (nếu có).
3. Gọi đường gặp lỗi riêng sau khi reset recorder.
4. Kiểm tra modifier của method và kiểu proxy hiệu lực.
5. Đối chiếu call graph: external proxy → target hay target `this` → target?
6. Nếu liên quan transaction/cache/security, kiểm tra outcome thật ở tầng feature sau khi xác nhận proxy semantics.
7. Chỉ cân nhắc AspectJ weaving khi giới hạn proxy không phù hợp với bài toán.

## 14. Câu hỏi tự kiểm tra

**1. `outer()` gọi `this.inner()`; `inner()` có `@Transactional`. Vì sao interceptor của inner có thể không chạy?**

Vì đây là self-invocation trên target; Spring proxy chỉ intercept lời gọi tới bean **qua proxy**, không intercept lời gọi `this.inner()` trong target.

**2. Có thể kết luận toàn bộ code inner “không có transaction” nếu outer đã có transaction không?**

Không. Outer có thể đang mở transaction. Điều bị bỏ qua là interceptor riêng của inner và semantics propagation được khai báo cho inner.

**3. Một method `final` trên class-based CGLIB proxy có được advice không?**

Không qua CGLIB override/interception, vì subclass không override được method final.

**4. Có phải implementation method final luôn không được intercept ngay cả với JDK proxy?**

Không. JDK interface proxy intercept lời gọi qua interface, có thể chuyển tới implementation method final. Test `Gateway` trong bài xác nhận điều đó.

**5. Method private có thể chạy khi public method gọi nó không?**

Có, private method vẫn chạy như Java thông thường. Chỉ việc **advise riêng execution của private method** không xảy ra trong Spring AOP proxy.

**6. Tại sao `new SampleService()` trả kết quả đúng nhưng aspect counter vẫn rỗng?**

Vì Java object trực tiếp không phải Spring AOP proxy. Nó chưa được bean factory auto-proxy và không đi qua advisor chain.

**7. Muốn kiểm chứng sửa self-invocation, nên thử gì trước khi bật AspectJ LTW?**

Tách method cần advice sang bean Spring khác, inject bean đó và gọi qua proxy; kiểm thử advisor/outcome trên đường gọi này.

**8. Vì sao cần cả assertion trên proxy type và recorder event?**

Proxy type chứng minh cơ chế Spring AOP đã thiết lập; recorder chứng minh một invocation cụ thể thực sự đi qua advice. Một trong hai đứng riêng không đủ kết luận.

## 15. Tài liệu Spring chính thức

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB, self-invocation, final/private, self-reference và `AopContext.currentProxy()`.
- [Spring Framework — Declaring Pointcuts](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html): Spring AOP chỉ hỗ trợ method execution join points cho Spring bean.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): ngữ nghĩa `@Before`, `@Around`, `@AfterReturning` và `@AfterThrowing`.
- [Spring Framework — JUnit Jupiter Testing Support](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/support-classes.html): `@SpringJUnitConfig`, `SpringExtension` và quản lý context trong test.
- [Spring Framework — Transaction Annotation-Driven Settings](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html): proxy mode và ranh giới self-invocation cho transaction.
- [Spring Framework — Spring AOP APIs](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api.html): `ProxyFactory`, interceptor và các proxy configuration cần để kiểm thử ngoại lệ JDK.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-testing-008">Kiểm thử với proxy JDK và proxy CGLIB</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử với proxy JDK và proxy CGLIB trong Spring AOP

## 1. Mục tiêu kiểm thử

Spring AOP thực thi advice chủ yếu thông qua **proxy** được Spring tạo cho bean. Trong ứng dụng Spring, hai loại proxy thường gặp là:

- **JDK dynamic proxy**: dựa trên **interface** mà target bean triển khai. Đối tượng trả về từ Spring triển khai các interface được proxy, nhưng **không phải instance của lớp triển khai target**.
- **CGLIB proxy**: Spring tạo lớp con động của target bean để intercept các phương thức có thể override. Đối tượng proxy vẫn có kiểu tương thích với lớp target.

Cả hai đều là **Spring AOP proxy** và có thể áp dụng cùng một **@Aspect**. Vì vậy, test cần phân biệt hai nhóm câu hỏi:

1. **Chọn đúng loại proxy chưa?** Dùng **AopUtils.isJdkDynamicProxy(...)** hoặc **AopUtils.isCglibProxy(...)**, đồng thời kiểm tra interface/concrete type.
2. **Advice có thực sự chạy theo đường gọi cần kiểm thử không?** Gọi method **trên bean lấy từ ApplicationContext**, rồi quan sát effect của advice và target; không chỉ kiểm tra proxy tồn tại.

Bài này chỉ tập trung vào **kiểm thử proxy JDK/CGLIB trong Spring Aspect**: cấu hình test, fixture, cách xác minh, các giới hạn ảnh hưởng đến assertion và lỗi dễ gặp. Các khái niệm AOP chung được xem là kiến thức nền.

## 2. Khác biệt tác động trực tiếp đến test

| Tiêu chí kiểm thử | JDK dynamic proxy | CGLIB proxy |
|---|---|---|
| Điều kiện thường gặp | Target có interface, dùng mặc định **proxyTargetClass=false** trong Spring Framework thuần | Bật **proxyTargetClass=true**, hoặc target không có interface phù hợp |
| Kiểu proxy được cấp | Interface của bean | Subclass động của lớp target |
| Có thể inject bằng interface? | Có | Có, nếu lớp target triển khai interface |
| Có thể inject bằng lớp target? | Thông thường không; proxy JDK không phải subclass target | Có |
| Gọi method chỉ tồn tại trên lớp triển khai | Không gọi được qua interface proxy | Có thể gọi khi method có thể được intercept |
| Kiểm tra loại | **AopUtils.isJdkDynamicProxy(bean)** | **AopUtils.isCglibProxy(bean)** |
| Kiểm tra AOP chung | **AopUtils.isAopProxy(bean)** | **AopUtils.isAopProxy(bean)** |
| Giới hạn final/private | Proxy JDK chỉ expose các method interface; gọi vào method thực thi không cần override target | Final/private method không thể bị CGLIB override để chạy advice |
| Self-invocation bằng this | Không đi qua proxy | Không đi qua proxy |
| Dùng annotation @Aspect | Được Spring AOP hỗ trợ | Được Spring AOP hỗ trợ |
| Cần AspectJ weaving? | Không | Không |

Lưu ý: chỉ đặt **proxyTargetClass=false** không bảo đảm luôn nhận JDK proxy. Nếu bean **không có interface phù hợp**, Spring vẫn có thể chọn CGLIB. Ngược lại, nhiều cấu hình AOP/transaction dùng chung một auto-proxy creator có thể làm tùy chọn **proxyTargetClass=true** được áp dụng trên diện rộng. Khi cần chứng minh hai loại riêng biệt, nên khởi tạo **hai ApplicationContext độc lập** với cấu hình tường minh.

Spring Boot có thể bật class-based proxy mặc định qua auto-configuration; đừng giả định mặc định giống Spring Framework thuần. Tại môi trường Boot, xác minh **spring.aop.proxy-target-class**, dependency AOP và các cấu hình proxy khác.

## 3. Thiết kế một bộ test so sánh được hai cơ chế

Ta dùng cùng target, cùng aspect, cùng pointcut và cùng cơ chế ghi sự kiện cho cả hai loại proxy. Chỉ khác ở **@EnableAspectJAutoProxy(proxyTargetClass = false/true)**.

Hành vi mong đợi của fixture:

- **pay()** là phương thức interface. Cả JDK và CGLIB proxy đều có thể chặn lời gọi qua proxy.
- **classOnly()** chỉ có trên lớp triển khai. JDK interface proxy không cung cấp method này; CGLIB proxy có thể gọi và advice method này.
- **payFromInside()** gọi **this.pay()**. Đây là self-invocation: ở cả JDK và CGLIB, advice cho **pay()** không chạy từ đường gọi nội bộ.
- Cả target và aspect cùng ghi vào **EventLog**. Nhờ thế assertion chứng minh được chính xác thứ tự chạy, không phụ thuộc vào console logger.

Sơ đồ:

~~~text
JDK PROXY:
PaymentPort reference -> JDK proxy -> PaymentService.pay()
             |                |
             |                +-> @Before advice cho pay()
             |
             +-> Không expose PaymentService.classOnly()

CGLIB PROXY:
PaymentService reference -> subclass proxy -> PaymentService.pay()
                   |
                   +-> @Before advice cho pay()/classOnly()
                   +-> Expose classOnly()

CẢ HAI:
proxy.payFromInside() -> target.payFromInside() -> this.pay()
                                                 (bỏ qua proxy,
                                                  không có advice của pay)
~~~

## 4. Ví dụ Java 17+ với Maven, Spring Framework và JUnit 5

Ví dụ độc lập dùng **Java 17+**, **Spring Framework 6.2.0**, **JUnit Jupiter 5.11.3** và **AspectJ Weaver 1.9.22.1**. Các dependency này được chọn cố định để cấu hình dễ tái hiện. Cần có Maven và JDK tương ứng để chạy.

Cấu trúc:

~~~text
spring-proxy-tests/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── PaymentPort.java
    │   ├── EventLog.java
    │   ├── PaymentService.java
    │   ├── TraceAspect.java
    │   ├── JdkAopConfig.java
    │   └── CglibAopConfig.java
    └── test/java/demo/
        ├── JdkProxyTest.java
        └── CglibProxyTest.java
~~~

### 4.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-proxy-tests</artifactId>
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

**spring-context** quản lý beans; **spring-aop** tạo proxy; **aspectjweaver** cho Spring xử lý biểu thức/annotation @AspectJ; **spring-test** và **junit-jupiter** chạy integration test trong ApplicationContext. Thêm **aspectjweaver** trong ví dụ này không kích hoạt AspectJ bytecode weaving.

### 4.2. Interface và target service

**src/main/java/demo/PaymentPort.java**

~~~java
package demo;

public interface PaymentPort {
    void pay();
    void payFromInside();
}
~~~

**src/main/java/demo/EventLog.java**

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

public class EventLog {
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

**src/main/java/demo/PaymentService.java**

~~~java
package demo;

public class PaymentService implements PaymentPort {
    private final EventLog log;

    public PaymentService(EventLog log) {
        this.log = log;
    }

    @Override
    public void pay() {
        log.add("target:pay");
    }

    @Override
    public void payFromInside() {
        log.add("target:outer");
        this.pay(); // Self-invocation, không quay lại Spring proxy.
    }

    // Method này không thuộc PaymentPort.
    public void classOnly() {
        log.add("target:classOnly");
    }
}
~~~

**pay()** và **payFromInside()** được khai báo trên interface. **classOnly()** chỉ hiện diện trên concrete class. Đây là khác biệt ta sẽ chứng minh qua test, đồng thời bảo đảm Spring có một interface thật để chọn JDK dynamic proxy.

### 4.3. Aspect dùng chung

**src/main/java/demo/TraceAspect.java**

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    private final EventLog log;

    public TraceAspect(EventLog log) {
        this.log = log;
    }

    @Before(
        "execution(* demo.PaymentService.pay(..)) || " +
        "execution(* demo.PaymentService.classOnly(..))"
    )
    public void beforeSelectedMethods(JoinPoint joinPoint) {
        log.add("advice:" + joinPoint.getSignature().getName());
    }
}
~~~

Pointcut chỉ chọn execution của **pay()** và **classOnly()**. Không có advice cho **payFromInside()**. Logic advice cũng không phụ thuộc trực tiếp vào loại proxy: loại proxy do Spring config quyết định.

### 4.4. Cấu hình Spring cho JDK proxy

**src/main/java/demo/JdkAopConfig.java**

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class JdkAopConfig {
    @Bean
    public EventLog eventLog() {
        return new EventLog();
    }

    @Bean
    public PaymentPort paymentPort(EventLog log) {
        return new PaymentService(log);
    }

    @Bean
    public TraceAspect traceAspect(EventLog log) {
        return new TraceAspect(log);
    }
}
~~~

Target implements **PaymentPort** và **proxyTargetClass=false**, nên với cấu hình thử nghiệm độc lập này Spring sẽ tạo **JDK dynamic proxy** cho bean paymentPort vì pointcut khớp method của target.

Chúng ta khai báo kiểu trả về của bean là **PaymentPort** và trong test cũng inject **PaymentPort**. Không inject **PaymentService** vào test JDK proxy: interface proxy không phải instance của lớp triển khai.

### 4.5. Cấu hình Spring cho CGLIB proxy

**src/main/java/demo/CglibAopConfig.java**

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class CglibAopConfig {
    @Bean
    public EventLog eventLog() {
        return new EventLog();
    }

    @Bean
    public PaymentService paymentService(EventLog log) {
        return new PaymentService(log);
    }

    @Bean
    public TraceAspect traceAspect(EventLog log) {
        return new TraceAspect(log);
    }
}
~~~

**proxyTargetClass=true** yêu cầu Spring sử dụng class-based proxy cho bean được advice. Test CGLIB có thể inject bằng **PaymentService**, hoặc bằng **PaymentPort** nếu muốn kiểm thử hợp đồng interface.

**Không import đồng thời JdkAopConfig và CglibAopConfig vào một ApplicationContext** để so sánh: một auto-proxy creator thống nhất có thể áp dụng cờ mạnh hơn (class proxy) cho cả hai. Hai cấu hình ở đây cố tình dành cho **hai context độc lập**.

## 5. Test JDK dynamic proxy

**src/test/java/demo/JdkProxyTest.java**

~~~java
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(JdkAopConfig.class)
class JdkProxyTest {
    @Autowired
    PaymentPort service;

    @Autowired
    EventLog log;

    @BeforeEach
    void resetEvents() {
        log.clear();
    }

    @Test
    void jdkProxyImplementsInterfaceAndRunsAdvice() {
        assertTrue(AopUtils.isAopProxy(service));
        assertTrue(AopUtils.isJdkDynamicProxy(service));
        assertFalse(AopUtils.isCglibProxy(service));
        assertTrue(Proxy.isProxyClass(service.getClass()));
        assertFalse(service instanceof PaymentService);

        service.pay();

        assertEquals(
            List.of("advice:pay", "target:pay"),
            log.snapshot()
        );
    }

    @Test
    void jdkProxyDoesNotExposeImplementationOnlyMethod() {
        assertThrows(
            NoSuchMethodException.class,
            () -> service.getClass().getMethod("classOnly")
        );

        assertEquals(List.of(), log.snapshot());
    }

    @Test
    void callingThisPayDoesNotRunAdvice() {
        service.payFromInside();

        assertEquals(
            List.of("target:outer", "target:pay"),
            log.snapshot()
        );
    }
}
~~~

**Test 1** khẳng định đối tượng nhận từ Spring thực sự là JDK AOP proxy, đồng thời kiểm tra **advice chạy trước target** khi gọi **pay()** qua proxy. Một assertion kiểm tra loại proxy mà không gọi method nghiệp vụ là chưa đủ để chứng minh pointcut/advice đã wiring đúng.

**Test 2** xác nhận method **classOnly()** không xuất hiện trên kiểu proxy mà client được cấp. **PaymentService** vẫn có method đó, nhưng JDK proxy chỉ bộc lộ interface được proxy. Đừng dùng reflection vào target thật hoặc tự tạo target bằng **new** rồi kết luận proxy cũng expose method tương tự.

**Test 3** chứng minh self-invocation: test gọi **payFromInside()** qua proxy, nhưng method này không khớp pointcut; target sau đó gọi **this.pay()**, nên không có **advice:pay**. Đây là giới hạn của đường gọi qua Spring AOP proxy.

Ở đây **@BeforeEach** xóa log trước mỗi test. Spring TestContext có thể cache ApplicationContext, nên một bean singleton có trạng thái như **EventLog** cần được reset để assertion không phụ thuộc thứ tự chạy test.

## 6. Test CGLIB proxy

**src/test/java/demo/CglibProxyTest.java**

~~~java
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(CglibAopConfig.class)
class CglibProxyTest {
    @Autowired
    PaymentService service;

    @Autowired
    EventLog log;

    @BeforeEach
    void resetEvents() {
        log.clear();
    }

    @Test
    void cglibProxyIsAssignableToConcreteClass() {
        assertTrue(AopUtils.isAopProxy(service));
        assertTrue(AopUtils.isCglibProxy(service));
        assertFalse(AopUtils.isJdkDynamicProxy(service));
        assertTrue(service instanceof PaymentPort);

        service.pay();

        assertEquals(
            List.of("advice:pay", "target:pay"),
            log.snapshot()
        );
    }

    @Test
    void cglibProxyCanAdviseClassOnlyMethod() {
        service.classOnly();

        assertEquals(
            List.of("advice:classOnly", "target:classOnly"),
            log.snapshot()
        );
    }

    @Test
    void cglibStillDoesNotFixSelfInvocation() {
        service.payFromInside();

        assertEquals(
            List.of("target:outer", "target:pay"),
            log.snapshot()
        );
    }
}
~~~

**Test 1** xác nhận object vừa là AOP proxy kiểu CGLIB vừa tương thích với **PaymentService**; method khai báo trên interface vẫn được advice.

**Test 2** cho thấy CGLIB có thể áp dụng advice tới một **public, overridable method** chỉ được khai báo trên concrete class. Điểm khác biệt nằm ở **hình thức proxy và khả năng expose/intercept method**, không phải ở logic @Aspect.

**Test 3** đặc biệt quan trọng: **CGLIB cũng không khiến self-invocation đi lại qua proxy**. Nếu application cần chặn lời gọi nội bộ, cần thay đổi ranh giới Spring bean/đường gọi hoặc đánh giá cơ chế AspectJ weaving thật sự. Cấu hình **proxyTargetClass=true** không phải công tắc bật weaving.

## 7. Chạy test và đọc kết quả

Chạy trong thư mục **spring-proxy-tests**:

~~~bash
mvn test
~~~

Với đúng các tệp và dependencies ở trên, test suite có **6 test methods**: ba test JDK proxy và ba test CGLIB proxy. Dự kiến tất cả đều pass; phần tổng hợp Maven Surefire tương ứng:

~~~text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
~~~

Có thể chạy từng nhóm:

~~~bash
mvn -Dtest=JdkProxyTest test
mvn -Dtest=CglibProxyTest test
~~~

Đây là **kết quả kỳ vọng theo logic code**, không phải bản chép log Maven từ một lượt chạy thực tế. Nếu dự án có thêm test hoặc tùy chỉnh Surefire, output tổng hợp có thể khác.

Các observation cần thu được:

| Lời gọi | JDK proxy | CGLIB proxy |
|---|---|---|
| **service.pay()** | **advice:pay**, **target:pay** | **advice:pay**, **target:pay** |
| **service.classOnly()** | Không tồn tại trên interface proxy | **advice:classOnly**, **target:classOnly** |
| **service.payFromInside()** | **target:outer**, **target:pay** | **target:outer**, **target:pay** |
| **AopUtils.isJdkDynamicProxy** | true | false |
| **AopUtils.isCglibProxy** | false | true |
| Cast proxy sang **PaymentService** | Không hợp lệ | Hợp lệ |

Để chủ động chứng minh giá trị của các assertion, thử thay một cấu hình test rồi chạy lại:

- Nếu đặt **proxyTargetClass=true** trong **JdkAopConfig**, những assertion yêu cầu JDK proxy sẽ fail; đây là tín hiệu test đã phát hiện thay đổi loại proxy.
- Nếu bỏ **@EnableAspectJAutoProxy** trong config Spring thuần của ví dụ, những assertion về proxy và advice sẽ fail.
- Nếu làm pointcut không còn khớp **pay()**, test **service.pay()** sẽ không còn thấy **advice:pay**.
- Nếu bỏ interface **PaymentPort**, Spring sẽ không có interface phù hợp để tạo JDK proxy cho service nữa; test không còn kiểm chứng đúng tiền đề của nó.

Những thay đổi trên chỉ là **thí nghiệm cục bộ** cho dự án ví dụ, không phải cấu hình nên đưa vào production.

## 8. Các tình huống Spring AOP dễ kiểm thử sai

### 8.1. Mặc định Spring Boot khác Spring thuần

**@EnableAspectJAutoProxy(proxyTargetClass=false)** trong một ví dụ Spring Framework tối thiểu thường có nghĩa ưu tiên JDK proxy cho bean có interface. Nhưng trong một Spring Boot ApplicationContext, cấu hình auto-proxy khác có thể yêu cầu CGLIB trên toàn context. Vì vậy **@SpringBootTest** không đảm bảo JDK proxy chỉ vì service implements interface.

Trong một bài test Boot thực tế, xác minh **spring.aop.proxy-target-class=false** khi cần interface proxy, đồng thời kiểm tra các cấu hình liên quan **@EnableTransactionManagement**, **@EnableAspectJAutoProxy**, advisor/proxy creator khác. Luôn để assertion **AopUtils.isJdkDynamicProxy** phát hiện proxy thực sự tại runtime.

### 8.2. Proxy type và kiểu dependency injection

Với JDK proxy, inject target theo concrete class có thể làm ApplicationContext không đáp ứng được kiểu dependency mà bạn yêu cầu. Client nên phụ thuộc vào interface:

~~~java
@Autowired
PaymentPort paymentPort;
~~~

Còn với class proxy, inject **PaymentService** là hợp lý:

~~~java
@Autowired
PaymentService paymentService;
~~~

Không nên cast vô điều kiện từ **PaymentPort** sang **PaymentService** khi app có thể dùng JDK proxy. Nếu service chỉ sử dụng interface ở production, các test nghiệp vụ nên lập trình theo interface; các test riêng xác minh class method có thể chạy trong một context CGLIB có chủ đích.

### 8.3. final, private và non-visible methods

CGLIB cần tạo subclass và override method để chặn lời gọi:

- **final class** không thể được CGLIB subclass để tạo proxy cho chính lớp đó.
- **final method** và **private method** không thể bị CGLIB override để advice bằng Spring proxy.
- Các method không nhìn thấy từ subclass (chẳng hạn package-private method của parent ở package khác) có thể không được advice.

Với JDK proxy, điểm trọng tâm là lời gọi qua **method trên interface được proxy**. Không thể từ đó suy ra rằng Spring AOP sẽ advice mọi method trên concrete target.

Một test gọi final/private method nhưng không thấy advice không đồng nghĩa aspect bị lỗi logic. Trước tiên kiểm tra giới hạn của proxy và liệu method có thực sự thuộc join point Spring AOP hỗ trợ.

### 8.4. Self-invocation ảnh hưởng cả hai loại

Cả hai đường đi:

~~~java
service.pay(); // Client có tham chiếu Spring proxy.
~~~

và:

~~~java
public void payFromInside() {
    this.pay(); // Lời gọi từ chính target, bỏ qua proxy.
}
~~~

đều có thể khiến target **pay()** được thực thi, nhưng advice chỉ chạy khi lời gọi đi qua proxy tương ứng. Bài test muốn kiểm tra Spring Aspect phải phân biệt **số lần chạy target** và **số lần advice chạy**.

### 8.5. Dùng mock thay bean đang được advice

Nếu test thay hẳn target bean bằng mock, bạn có thể vô tình thay đổi kiểu bean và điều kiện proxy. Khi dùng **@MockitoBean**, Mockito, test slice hoặc mock configuration, hãy xác minh bean được inject thực tế có còn là proxy mong đợi không. Một mock tự tạo bằng **new** hoặc **Mockito.mock(...)** ngoài container không chứng minh Spring AOP wiring.

Để test hành vi advice ổn định, thường hợp lý hơn khi giữ **target bean thật tối giản** và mock/fake dependency nằm phía dưới, giống **EventLog** trong ví dụ.

### 8.6. Coi tên lớp generated proxy là contract

Không nên assert chuỗi tên class dạng **$$SpringCGLIB$$...** hay **jdk.proxy...**, vì đó là chi tiết runtime có thể đổi theo version/JDK. Sử dụng API Spring:

~~~java
AopUtils.isAopProxy(bean);
AopUtils.isJdkDynamicProxy(bean);
AopUtils.isCglibProxy(bean);
~~~

Nếu mục tiêu là đảm bảo advice đúng, cần thêm assertion về **effect**. Loại proxy và hiệu lực advice là **hai tiêu chí độc lập**.

### 8.7. Test nhầm proxy AOP với AspectJ weaving

Spring AOP proxy có thể dùng annotation **@Aspect** và thư viện **aspectjweaver** để parse pointcut mà không weave bytecode. Hai bài test ở đây kiểm tra **Spring proxy**. Nếu ứng dụng thực sự dùng AspectJ compile/load-time weaving, các test phải thiết lập compiler/agent/weaver và quan sát join point được weave; **AopUtils.isAopProxy=false** khi đó không phải bằng chứng aspect bị vô hiệu.

## 9. Phương pháp kiểm thử trong dự án thực tế

Tùy yêu cầu, hãy phân lớp kiểm thử thay vì lặp cùng một bài test ở mọi tầng:

1. **Unit test advice**: kiểm tra xử lý tham số, kết quả, exception, tương tác với metrics/audit sink bằng mock/fake. Không cần Spring context.
2. **Integration test Spring AOP**: khởi tạo ApplicationContext gọn với đúng aspect và service, xác minh proxy thực tế và advice khi gọi service method.
3. **Application-level integration test**: với Spring Boot và nhiều advisor (**@Transactional**, security, caching...), dùng context gần production để phát hiện cấu hình chung làm thay đổi loại proxy hoặc thứ tự advice.
4. **Regression test theo boundary**: kiểm tra method cần được advice và method không nên được advice, cả method nằm trên interface và method chỉ có trên concrete class nếu điều đó là yêu cầu hệ thống.

Nếu sản phẩm chỉ cam kết hành vi nghiệp vụ qua **PaymentPort**, không nhất thiết khóa mọi test vào JDK hay CGLIB. Chỉ assert proxy type khi kiểu proxy là **yêu cầu thiết kế cụ thể** hoặc khi bạn đang viết bài test để phân biệt hai cơ chế này.

### Cô lập các ApplicationContext

Một vài nguyên tắc giúp test không bị nhiễu:

- **Không trộn hai cấu hình auto-proxy trái nhau vào cùng context.**
- Trong Spring TestContext, context có thể được cache theo cấu hình; reset mọi collaborator mutable trước test.
- Tránh dùng **static EventLog** hoặc shared state không kiểm soát.
- Nếu chạy JUnit parallel, mỗi class/context phải có trạng thái độc lập hoặc dùng collector thread-safe phù hợp.
- Với test type, thực hiện kiểm tra **sau** khi lấy bean từ Spring; so sánh kiểu của target mới tạo trực tiếp bằng **new** không chứng minh proxy type của context.

### Diễn giải một test thất bại

| Hiện tượng | Nghi vấn trước tiên |
|---|---|
| Muốn JDK nhưng **isCglibProxy=true** | **proxyTargetClass=true** đến từ Boot hoặc proxy config khác |
| Muốn CGLIB nhưng **isJdkDynamicProxy=true** | Chưa bật class proxy trong context thực tế hoặc dùng nhầm config |
| Bean không phải AOP proxy | Aspect chưa đăng ký, AOP chưa bật hoặc pointcut không khớp |
| Proxy tồn tại nhưng không có **advice:pay** | Điểm gọi sai method, pointcut không khớp, gọi ngoài proxy |
| Inject **PaymentService** thất bại trong JDK context | Interface proxy không phải subtype của concrete target |
| CGLIB không advice **final/private** method | Giới hạn override của class proxy |
| Cả JDK và CGLIB không advice lời gọi bên trong | **this.method()** bypass proxy |
| Chạy cả suite mới fail | Context cache, event log chưa reset, test chạy song song |
| Có advice hai lần | Kiểm tra nhiều advisor, nhiều aspect hoặc đồng thời proxy và weaving |

## 10. Checklist xác nhận bài test đã bao phủ đúng yêu cầu

- [ ] Bean target có interface thật khi kỳ vọng JDK proxy.
- [ ] Hai mode proxy được cấu hình trong **ApplicationContext tách biệt**.
- [ ] Đã dùng **AopUtils** kiểm tra đúng kiểu runtime.
- [ ] Đã kiểm tra injection/type visibility tương ứng với JDK và CGLIB.
- [ ] Method cần advice được gọi từ **proxy bean do Spring cấp**.
- [ ] Test chứng minh **effect và thứ tự advice/target** thay vì chỉ kiểm tra proxy tồn tại.
- [ ] Test phân biệt method chỉ có trên class, method trên interface và self-invocation.
- [ ] Không yêu cầu CGLIB intercept final/private method.
- [ ] State của fixture được reset để không phụ thuộc thứ tự test.
- [ ] Không nhầm Spring AOP proxy với AspectJ weaving.
- [ ] Trong ứng dụng Spring Boot, đã kiểm tra cấu hình AOP chung ảnh hưởng loại proxy.

## 11. Tự kiểm tra

**1. Có thể nhận JDK proxy nếu target không có interface không?** Theo cơ chế Spring AOP thông thường, không; Spring cần dùng class-based proxy cho lớp không có interface phù hợp.

**2. Vì sao JDK proxy vẫn advice được execution của PaymentService.pay() dù test giữ PaymentPort?** Spring xét pointcut dựa trên method/target phù hợp, nhưng client gọi qua interface method trên JDK proxy. Advice vẫn được áp dụng khi proxy intercept lời gọi đó.

**3. CGLIB có làm this.pay() chạy advice không?** Không. Lời gọi self-invocation từ target không quay lại proxy.

**4. Có nên assert tên class generated proxy?** Không. Dùng **AopUtils.isJdkDynamicProxy/isCglibProxy** và kiểm tra hành vi.

**5. Test chỉ gọi Mockito mock hoặc new PaymentService có đủ xác nhận wiring AOP?** Không. Cần Spring ApplicationContext và bean proxy thực tế.

**6. Nếu JDK proxy trả về kiểu PaymentPort, ta có thể cast nó sang PaymentService để gọi classOnly() không?** Không. JDK proxy không phải subclass của PaymentService.

**7. Có phải bật AspectJ weaving để test CGLIB không?** Không. CGLIB ở đây chỉ là cơ chế proxy của Spring AOP.

## 12. Nguồn tham khảo chính thức

- Spring Framework 6.2 — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html
- Spring Framework — @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — Spring JUnit Jupiter Testing Annotations: https://docs.spring.io/spring-framework/reference/testing/annotations/integration-junit-jupiter.html
- Spring Framework — Testing: https://docs.spring.io/spring-framework/reference/testing/index.html
- Spring Boot — Aspect-Oriented Programming: https://docs.spring.io/spring-boot/reference/features/aop.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-testing-009">Kiểm thử trạng thái aspect khi nhiều luồng cùng gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử trạng thái aspect khi nhiều luồng cùng gọi trong Spring AOP

## 1. Mục tiêu và tình huống thực tế

Một aspect của Spring AOP thường được định nghĩa bằng `@Aspect` và đăng ký làm **Spring bean singleton**. Khi nhiều request hoặc task cùng gọi phương thức trên các Spring bean qua AOP proxy, cùng một instance aspect có thể nhận nhiều lời gọi advice **đồng thời**. Nếu advice có mutable field, việc một test chạy tuần tự thành công chưa chứng minh trạng thái an toàn khi hệ thống chạy đa luồng.

Ví dụ thường gặp: aspect ghi nhận số lượt gọi, thời gian xử lý, số request đang chạy, correlation ID hoặc dữ liệu cho audit. Các lỗi cần bắt là **lost update** (`count++` trên shared field), ghi đè nhầm dữ liệu request, `ThreadLocal` không được dọn khi exception, và kiểm thử tưởng đã chạy AOP nhưng thực tế gọi thẳng vào target.

Sau bài này, bạn có thể:

1. Xác định phần nào của advice là **state chia sẻ**, phần nào thuộc **mỗi lần gọi** và phần nào **gắn với thread**.
2. Viết test JUnit 5 tạo **hai lời gọi thực sự chồng lấn** qua Spring AOP proxy bằng `ExecutorService` và `CountDownLatch`.
3. Kiểm tra số lời gọi hoàn tất, số lời gọi đang chạy, đỉnh đồng thời, `ThreadLocal` cleanup và đường đi khi phương thức ném exception.
4. Phân biệt vấn đề trạng thái Spring aspect singleton với `perthis`/`pertarget`, request scope, `@Async` hoặc self-invocation; tránh suy luận sai từ test.

Bài chỉ tập trung vào **Spring AOP và việc kiểm thử aspect của Spring**, không ôn lại lập trình đa luồng hoặc lý thuyết AspectJ tổng quát.

## 2. Trạng thái aspect nằm ở đâu?

Với cấu hình Spring thông thường:

```java
@Aspect
public class CallAspect {
    private int count;            // mutable field trên aspect instance

    @Before("execution(* com.example..service.*.*(..))")
    public void before() {
        count++;                   // KHÔNG an toàn khi nhiều thread cùng chạy
    }
}
```

Đăng ký `CallAspect` thành bean singleton đồng nghĩa `count` được **chia sẻ giữa mọi advice invocation đi qua instance đó**. `count++` gồm đọc, cộng, ghi; hai thread có thể cùng đọc một giá trị rồi cùng ghi ra kết quả giống nhau.

| Loại state | Nơi lưu | Đặc điểm khi có hai lời gọi đồng thời | Cách xử lý thường dùng |
| --- | --- | --- | --- |
| Biến local trong method advice | Stack/frame của từng invocation | Mỗi lời gọi có bản riêng | Ưu tiên cho `startNanos`, args, return value, correlation data chỉ dùng trong advice |
| Field bất biến (`final`, immutable object) | Chung trên instance aspect | Thường an toàn khi object thực sự immutable | Dùng dependency/config immutable |
| Field mutable như `int count`, `List`, `HashMap` | Chung trên aspect instance | Dễ race/lost update/đọc dữ liệu của lời gọi khác | `AtomicInteger`, `LongAdder`, concurrent collection, hoặc đồng bộ phù hợp |
| `ThreadLocal<T>` | Mỗi thread có một giá trị | Tránh ghi đè trực tiếp giữa hai thread khác nhau | `try/finally`, `remove()` hoặc khôi phục giá trị cũ nếu nested call |
| `@Aspect` per-target/per-this | Theo đối tượng proxy/target phù hợp với instantiation model | Nhiều thread vẫn có thể cùng dùng một target/aspect | **Không** coi per-target tương đương per-thread |

Spring AOP hỗ trợ mô hình aspect singleton mặc định và một số instantiation model khác như `perthis`, `pertarget`, `pertypewithin`. Tuy vậy, việc có một aspect instance cho từng target không bảo đảm thread safety nếu cùng target được nhiều thread gọi.

**Một nguyên tắc rất thực dụng:** trong `@Around`, giữ dữ liệu tạm như thời điểm bắt đầu, identifier, exception hoặc kết quả ở **biến local** càng nhiều càng tốt. Chỉ đưa dữ liệu cần tổng hợp xuyên nhiều lời gọi vào state chung, và cập nhật nó bằng cơ chế thread-safe.

## 3. Test phải đi qua proxy và tạo overlap thật

Một phép thử hợp lệ cho Spring AOP cần đáp ứng đồng thời:

- **Khởi tạo Spring context có bật auto-proxy:** `@EnableAspectJAutoProxy` và đăng ký aspect bean.
- **Lấy service từ context:** gọi bean proxy do Spring trả về, tránh `new TargetService()` trong test.
- **Kiểm tra proxy:** dùng `AopUtils.isAopProxy(service)` để phát hiện test vô tình bỏ qua AOP.
- **Tạo ít nhất hai thread thực sự:** ví dụ fixed thread pool có hai worker.
- **Chặn hai lời gọi gặp nhau ở điểm xác định:** dùng `CountDownLatch`/barrier trong target, đảm bảo hai lời gọi đồng thời đi qua advice. Chỉ submit hai task chưa bảo đảm chúng chồng lấn.
- **Giới hạn thời gian chờ:** mọi `await`, `Future.get` có timeout; nếu thiếu lời gọi thứ hai, test thất bại rõ ràng thay vì treo vô hạn.
- **Kiểm tra state sau khi task hoàn tất:** đợi `Future.get`, rồi assert counters/cleanup; tránh assert sớm trước khi hai thread kết thúc.

Điểm quan trọng của **Spring AOP**: chỉ lời gọi **đi qua proxy** mới kích hoạt advice. Lời gọi nội bộ như `this.otherMethod()` trong target thường bỏ qua proxy. Nếu test gọi thẳng đối tượng target, kết quả có thể là số lượt aspect bằng 0 mà không liên quan đến race.

## 4. Ví dụ chạy được với Java 17, Spring Framework 6.2 và JUnit 5

Chúng ta viết một aspect có ba metric: `inFlight` (đang xử lý), `peak` (mức đồng thời cao nhất) và `finishedCalls` (số invocation đã đi đến `finally`). Đồng thời giữ `label` của invocation hiện tại trong `ThreadLocal` và dọn nó ngay cả khi target ném exception.

Mục tiêu của test đầu tiên: hai lời gọi `A` và `B` **chồng lấn thật**, `peak=2`, `finishedCalls=2`, `inFlight=0`, và `ThreadLocal` trên mỗi worker đã được dọn. Test thứ hai kiểm tra `finally` khi một target ném exception.

### 4.1. Cấu trúc thư mục

```text
spring-aop-concurrency-test/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── WorkService.java
    │   ├── BarrierWorkService.java
    │   ├── MetricsAspect.java
    │   └── AopConfig.java
    └── test/java/demo/
        └── ConcurrentAspectTest.java
```

### 4.2. Maven `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>spring-aop-concurrency-test</artifactId>
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
        </plugins>
    </build>
</project>
```

`spring-aop` cung cấp proxy và advice integration; `aspectjweaver` hỗ trợ Spring phân tích cú pháp `@AspectJ`. **Không cần AspectJ load-time weaving hoặc `-javaagent`** cho ví dụ Spring AOP dựa trên proxy.

### 4.3. Interface và target có điểm đồng bộ

**`src/main/java/demo/WorkService.java`:**

```java
package demo;

public interface WorkService {
    String work(String label);
}
```

**`src/main/java/demo/BarrierWorkService.java`:**

```java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class BarrierWorkService implements WorkService {
    private final CountDownLatch bothEntered = new CountDownLatch(2);

    @Override
    public String work(String label) {
        bothEntered.countDown();

        try {
            if (!bothEntered.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timeout waiting for other call");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", ex);
        }

        if ("boom".equals(label)) {
            throw new IllegalArgumentException("boom");
        }

        return "done:" + label;
    }
}
```

Hai lời gọi cùng target phải đến được `bothEntered.countDown()` thì `await` mới hoàn tất. Vì vậy, test chứng minh rằng hai worker đã thực sự tiến vào cùng một instance service, thay vì chỉ trông chờ scheduler tạo ra sự đồng thời. Mỗi `ApplicationContext` trong test bên dưới tạo **một instance `BarrierWorkService` mới**, tránh latch từ test trước còn giá trị 0.

### 4.4. Aspect giữ state an toàn

**`src/main/java/demo/MetricsAspect.java`:**

```java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class MetricsAspect {
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();
    private final LongAdder finishedCalls = new LongAdder();
    private final ThreadLocal<String> currentLabel = new ThreadLocal<>();

    @Around("execution(* demo.WorkService.work(..))")
    public Object track(ProceedingJoinPoint pjp) throws Throwable {
        int active = inFlight.incrementAndGet();
        peak.accumulateAndGet(active, Math::max);

        // Chỉ dùng cho ví dụ; mỗi invocation sở hữu giá trị riêng.
        // Lưu previous giúp an toàn với các lời gọi lồng nhau cùng thread.
        String previous = currentLabel.get();
        currentLabel.set((String) pjp.getArgs()[0]);

        try {
            return pjp.proceed();
        } finally {
            if (previous == null) {
                currentLabel.remove();
            } else {
                currentLabel.set(previous);
            }

            inFlight.decrementAndGet();
            finishedCalls.increment();
        }
    }

    public int inFlight() {
        return inFlight.get();
    }

    public int peak() {
        return peak.get();
    }

    public long finishedCalls() {
        return finishedCalls.sum();
    }

    public String currentLabel() {
        return currentLabel.get();
    }
}
```

Lý do chọn từng cơ chế:

- `AtomicInteger.incrementAndGet()/decrementAndGet()` cho phép cập nhật số lời gọi đang chạy nguyên tử.
- `peak.accumulateAndGet(active, Math::max)` cập nhật giá trị lớn nhất một cách nguyên tử. Một phép gán thường kiểu `peak = Math.max(peak, active)` **không an toàn** nếu nhiều thread chạy cùng lúc.
- `LongAdder` tốt cho bộ đếm được nhiều thread cập nhật; `sum()` là kết quả ổn định khi **đã chờ mọi worker kết thúc**, không nên coi `sum()` là snapshot nguyên tử trong lúc đang cập nhật.
- `previous` và `currentLabel` là biến local/mỗi-thread, không phải field mutable được chia sẻ giá trị giữa các call.
- `try/finally` chạy cả khi `pjp.proceed()` ném ngoại lệ. `finishedCalls` nghĩa là **kết thúc một invocation**, không có nghĩa target xử lý thành công.
- `remove()` khi không có giá trị trước đó ngăn `ThreadLocal` giữ lại dữ liệu request trên worker thread được pool tái sử dụng. Nếu advice có thể lồng nhau trên cùng thread, khôi phục `previous` tránh xóa ngữ cảnh của lời gọi cha.

**Không lưu** `pjp`, `pjp.getArgs()`, `startTime` hay `currentLabel` vào một field kiểu `String lastRequestId`: các lời gọi concurrent sẽ ghi đè nhau và có thể làm lộ dữ liệu từ request khác.

### 4.5. Cấu hình Spring AOP

**`src/main/java/demo/AopConfig.java`:**

```java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy
public class AopConfig {
    @Bean
    public WorkService workService() {
        return new BarrierWorkService();
    }

    @Bean
    public MetricsAspect metricsAspect() {
        return new MetricsAspect();
    }
}
```

`@Bean` có scope singleton theo mặc định trong context này. Mỗi request hay thread **không được cấp một instance `MetricsAspect` mới**. Pointcut `execution(* demo.WorkService.work(..))` nhắm tới lời gọi `work` trên service interface do Spring quản lý.

### 4.6. Test đồng thời và test exception

**`src/test/java/demo/ConcurrentAspectTest.java`:**

```java
package demo;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrentAspectTest {

    private static void waitForStart(CountDownLatch start) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("start gate timeout");
        }
    }

    @Test
    void sharedAspectStateIsSafeWhenCallsOverlap() throws Exception {
        try (var ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
            WorkService service = ctx.getBean(WorkService.class);
            MetricsAspect aspect = ctx.getBean(MetricsAspect.class);

            assertTrue(AopUtils.isAopProxy(service),
                    "Service must be called through a Spring AOP proxy");
            assertSame(aspect, ctx.getBean("metricsAspect"),
                    "Both worker threads should share this singleton aspect");

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch start = new CountDownLatch(1);
                Callable<String> first = () -> {
                    waitForStart(start);
                    String result = service.work("A");
                    assertNull(aspect.currentLabel(),
                            "ThreadLocal must be cleared on worker A");
                    return result;
                };
                Callable<String> second = () -> {
                    waitForStart(start);
                    String result = service.work("B");
                    assertNull(aspect.currentLabel(),
                            "ThreadLocal must be cleared on worker B");
                    return result;
                };

                Future<String> a = executor.submit(first);
                Future<String> b = executor.submit(second);
                start.countDown();

                assertEquals("done:A", a.get(10, TimeUnit.SECONDS));
                assertEquals("done:B", b.get(10, TimeUnit.SECONDS));
                assertEquals(2L, aspect.finishedCalls());
                assertEquals(2, aspect.peak());
                assertEquals(0, aspect.inFlight());
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void aspectReleasesStateEvenWhenTargetThrows() throws Exception {
        try (var ctx = new AnnotationConfigApplicationContext(AopConfig.class)) {
            WorkService service = ctx.getBean(WorkService.class);
            MetricsAspect aspect = ctx.getBean(MetricsAspect.class);
            assertTrue(AopUtils.isAopProxy(service));

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch start = new CountDownLatch(1);
                Future<String> success = executor.submit(() -> {
                    waitForStart(start);
                    String result = service.work("ok");
                    assertNull(aspect.currentLabel());
                    return result;
                });
                Future<String> failure = executor.submit(() -> {
                    waitForStart(start);
                    try {
                        service.work("boom");
                        return "unexpected success";
                    } catch (IllegalArgumentException expected) {
                        assertEquals("boom", expected.getMessage());
                        assertNull(aspect.currentLabel());
                        return "handled failure";
                    }
                });
                start.countDown();

                assertEquals("done:ok", success.get(10, TimeUnit.SECONDS));
                assertEquals("handled failure", failure.get(10, TimeUnit.SECONDS));
                assertEquals(2L, aspect.finishedCalls());
                assertEquals(2, aspect.peak());
                assertEquals(0, aspect.inFlight());
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }
}
```

**Chạy test:**

```bash
mvn -q test
```

Kết quả mong đợi: Maven/Surefire kết thúc thành công với **2 tests, 0 failures, 0 errors**. Vì dùng `-q`, Maven có thể không in summary khi tất cả đều pass; có thể bỏ `-q` hoặc mở `target/surefire-reports/demo.ConcurrentAspectTest.txt` để xem số test. Nếu muốn xem lệnh rõ ràng hơn:

```bash
mvn test
```

Các `assertEquals` là **oracle** quan trọng, không dựa vào thứ tự log của thread:

| Assertion | Điều được chứng minh |
| --- | --- |
| `AopUtils.isAopProxy(service)` | Lời gọi đi qua Spring AOP proxy |
| `assertSame(aspect, ctx.getBean("metricsAspect"))` | Đang kiểm tra cùng một singleton aspect trong context |
| `peak() == 2` | Có lúc hai lời gọi advice đang cùng in-flight |
| `finishedCalls() == 2` | Cả hai invocation đi tới cleanup trong `finally` |
| `inFlight() == 0` | Sau khi chờ hai `Future` xong, không còn lời gọi đang chạy |
| `currentLabel() == null` trên mỗi worker | Context của invocation được xóa/khôi phục trên đúng thread, tránh rò state |
| Nhánh `boom` được bắt là `IllegalArgumentException` | Khi target lỗi, advice vẫn không để lại in-flight và thread-local state |

**Điểm quan trọng:** `currentLabel()` phải được đọc **bên trong chính task đã gọi service**. Gọi `aspect.currentLabel()` trên thread của JUnit và nhận `null` không chứng minh worker thread đã được dọn, vì `ThreadLocal` tách biệt theo thread.

Các phép thử trên xác minh hành vi của **ví dụ này**, không chứng minh mọi mutable field bất kỳ đều thread-safe. Để kiểm tra logic khác, thay assertion và workload theo mục tiêu cụ thể.

## 5. Một bug thật: bộ đếm ở field bị mất lượt cập nhật

Giả sử người viết đổi `finishedCalls` sang `int` thông thường:

```java
@Aspect
public class UnsafeCountAspect {
    private int finishedCalls;

    @After("execution(* demo.WorkService.work(..))")
    public void count() {
        finishedCalls++;  // read -> add -> write, có thể lost update
    }

    public int finishedCalls() {
        return finishedCalls;
    }
}
```

Đây là đoạn minh họa **có lỗi chủ đích**, không thay trực tiếp vào project mẫu đang dùng `MetricsAspect`.

Hai thread có thể chạy:

```text
Ban đầu finishedCalls = 0

Thread A: đọc 0
Thread B: đọc 0
Thread A: ghi 1
Thread B: ghi 1

Kết quả = 1 dù có 2 invocation
```

`volatile int` chỉ giúp visibility và thứ tự nhất định của đọc/ghi, **không** làm phép `++` trở thành nguyên tử.

Một test loop chạy 1.000 lần và vẫn thấy đúng 1.000 không chứng minh code an toàn: interleaving gây race không phải lúc nào cũng xảy ra. Để **tái hiện có kiểm soát**, trong một bản aspect chỉ dành cho test, đặt một `CyclicBarrier(2)` sau khi hai thread đã đọc snapshot nhưng trước khi ghi kết quả:

```java
// Fragment mô phỏng đoạn advice bị lỗi; thuộc về test fixture, không chạy độc lập.
private int finishedCalls;
private final java.util.concurrent.CyclicBarrier bothRead =
        new java.util.concurrent.CyclicBarrier(2);

private void incrementUnsafely() throws Exception {
    int observed = finishedCalls;
    bothRead.await(5, java.util.concurrent.TimeUnit.SECONDS);
    finishedCalls = observed + 1;
}
```

Khi hai thread cùng bắt đầu với `0`, cả hai đều ghi `1`; test dự đoán chính xác lost update. Đừng đưa barrier này vào production advice: nó chỉ có ích để ép interleaving trong thử nghiệm. Bản production dùng `AtomicInteger.incrementAndGet()`, `LongAdder.increment()` hoặc đồng bộ thích hợp tùy nhu cầu snapshot và throughput.

## 6. Các chiến lược state trong Spring aspect

### 6.1. Dùng biến local trước tiên

Nếu chỉ cần đo độ dài một lời gọi, state đặt trong `@Around` là đủ:

```java
@Around("execution(* demo.WorkService.work(..))")
public Object timed(ProceedingJoinPoint pjp) throws Throwable {
    long startedAt = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        long elapsed = System.nanoTime() - startedAt;
        // Ghi metric qua sink thread-safe được tiêm vào aspect.
    }
}
```

`startedAt` không được chia sẻ giữa threads, nên tránh toàn bộ bài toán bảo vệ timestamp. Nếu metric backend là một object dùng chung, backend đó vẫn cần bảo đảm thread safety.

### 6.2. State tổng hợp chia sẻ qua mọi request

Bộ đếm `AtomicInteger`, `LongAdder` và các atomic accumulator phù hợp với thống kê không phụ thuộc thứ tự invocation. Khi logic phải giữ **nhiều giá trị nhất quán cùng lúc**, việc mỗi field là atomic vẫn chưa chắc đủ; có thể cần một lock, immutable snapshot hoặc một object state cập nhật đồng bộ.

Ví dụ `requestsStarted` và `requestsFinished` đều atomic nhưng việc đọc hai số theo hai thời điểm khác nhau không tự tạo thành một snapshot nguyên tử.

### 6.3. State theo thread và request

`ThreadLocal` hữu ích với code đồng bộ chạy trên cùng thread, nhưng cần:

- Đặt context trước `proceed()` và dọn trong `finally`.
- Khôi phục giá trị cũ nếu có lời gọi lồng nhau.
- Đọc/check `ThreadLocal` trên **chính worker thread**.
- Nhớ executor pool tái sử dụng thread; để sót ThreadLocal có thể làm request sau nhìn thấy thông tin request trước.
- Không mặc nhiên giả định giá trị ThreadLocal đi theo `CompletableFuture`, `@Async`, `@Scheduled`, reactor pipeline hoặc task executor khác. Truyền context qua cơ chế chính thức phù hợp nền tảng như task decorator, `Micrometer Context Propagation` hoặc Reactor Context khi cần.

Không đặt state theo mỗi invocation trong field singleton kiểu `String currentUser`, `Map lastHeaders`, `String lastTraceId`; các field đó dễ bị ghi đè khi concurrent request.

### 6.4. Nếu aspect có scope khác singleton?

Đừng coi `@Scope("prototype")` hay `@Aspect("pertarget(...)")` như cách sửa tự động cho race. Spring AOP có hỗ trợ một số AspectJ instantiation model, nhưng `pertarget` chỉ phân chia aspect theo **target**, không phân chia theo từng call/thread. Nếu một singleton service chịu 100 request cùng lúc, một aspect per-target vẫn có thể là instance chung cho 100 lời gọi.

Bởi vậy, luôn kiểm tra lifecycle thật của aspect bean, số target proxy và đặc tính luồng của caller trước khi chọn cơ chế giữ state.

## 7. Phân biệt các loại test AOP khi concurrency

| Test | Có thể chứng minh | Không thể kết luận chỉ từ test đó |
| --- | --- | --- |
| Unit test gọi trực tiếp `aspect.track(...)` với mock `ProceedingJoinPoint` | Logic trong advice riêng lẻ (cleanup, exception, counter) | Spring đã khởi tạo proxy hoặc pointcut khớp thật |
| Integration test một thread qua proxy | Context, registration, pointcut và hành vi cơ bản | Có an toàn khi hai invocation chồng lấn |
| Integration test hai thread qua proxy + barrier | Aspect nhận các lời gọi concurrent; counters và cleanup dưới interleaving có kiểm soát | Không có race trong mọi interleaving/phạm vi tải của hệ thống |
| Stress test nhiều worker, nhiều vòng | Khả năng hoạt động dưới tải và lỗi xác suất cao | Bằng chứng tuyệt đối rằng mã không có data race |
| Test dùng đối tượng tự `new` ngoài Spring | Hành vi target Java | Advice Spring AOP đã chạy |

Trong bài này, integration test có chủ đích dùng `AnnotationConfigApplicationContext` thay vì `@SpringBootTest` để kiểm soát đúng số bean và scope. Nếu dùng `@SpringBootTest`, hãy kiểm tra auto-configured AOP, test context caching, scope của bean và trạng thái chia sẻ với test khác. Với Spring Test, có thể dùng `@DirtiesContext` khi test thay đổi singleton state không thể reset; nhưng tạo context mới như ví dụ thì không cần.

## 8. Những giới hạn quan trọng cần đưa vào test thực tế

### 8.1. Proxy có thể không được gọi

`@EnableAspectJAutoProxy` chỉ áp dụng Spring proxy-based AOP. Với service có interface, JDK proxy thường được dùng. Class/method `final`, `private`, trường hợp self-invocation hay gọi object bằng `new` có các giới hạn khác nhau. Vì vậy:

- Lấy dependency từ Spring container, kiểm tra `AopUtils.isAopProxy`.
- Gọi method **từ một bean khác hoặc từ test qua proxy**.
- Nếu test dùng `@Transactional`, `@Async` và custom aspect, cần hiểu thứ tự advisor để xác định advice chạy trên thread nào.

### 8.2. `@Async` không bảo đảm tự lan truyền state

Một method được Spring gọi bằng `@Async` có thể chạy trên executor thread khác. Dù advice trước và sau `proceed()` hoạt động chính xác ở thread caller, `ThreadLocal` của caller không tự xuất hiện trong async worker. Test nên ghi nhận thread ID/name hoặc assertion ngay tại nơi cần thông tin; khi cần truyền context hãy dùng abstraction chính thức của ứng dụng.

### 8.3. `@After`, `@AfterReturning`, `@AfterThrowing` không thay thế nhau

Nếu cần giảm `inFlight` bất kể target thành công hay thất bại, `@Around` với `finally` hoặc `@After` theo thiết kế phù hợp. `@AfterReturning` chỉ chạy khi method trả về bình thường, nên nếu dùng nó để cleanup sẽ có nguy cơ bỏ sót khi exception. Với `@Around`, luôn gọi `proceed()` nếu mục tiêu là cho target chạy.

### 8.4. Không viết test dựa vào `sleep` hay thứ tự log

`Thread.sleep(100)` không bảo đảm overlap trên máy CI chậm/nhanh khác nhau. Không assert log cố định kiểu `A before`, `B before`, `A after`, `B after` vì scheduler có thể đổi thứ tự hợp lệ. `CountDownLatch`, `CyclicBarrier`, timeout, `Future.get` và trạng thái cuối là bằng chứng tốt hơn.

### 8.5. ThreadLocal và virtual threads

Với virtual threads (Java mới hơn), `ThreadLocal` vẫn có semantics theo thread, nhưng lifecycle và cách tái sử dụng khác thread pool cố định. Ví dụ ở đây dùng **Java 17 platform threads** để test ổn định theo mục tiêu. Khi đổi executor, phải kiểm tra lại cách lan truyền và dọn context thay vì suy diễn rằng test trên fixed thread pool bao phủ mọi execution model.

## 9. Checklist QA khi triển khai Spring aspect có state

- [ ] Xác định aspect là Spring singleton, per-target hay có cách khởi tạo khác.
- [ ] Rà soát các field mutable: bộ đếm, map, cache, request context, time marker.
- [ ] Chuyển dữ liệu chỉ thuộc một lời gọi sang biến local, tránh lưu vào field.
- [ ] Với shared metrics, dùng atomic/concurrent primitives hoặc đồng bộ bảo đảm invariant thực sự.
- [ ] Với `ThreadLocal`, luôn xử lý cleanup ở `finally`, bao gồm khi target ném exception.
- [ ] Test chạy qua Spring proxy với `AopUtils.isAopProxy` và đúng pointcut.
- [ ] Tạo concurrency có kiểm soát bằng gate/barrier và `Future.get(timeout)`.
- [ ] Assert chính xác sau khi hai task kết thúc: `finishedCalls`, `peak`, `inFlight`, cleanup trên từng worker.
- [ ] Có bài test lỗi/exception và kiểm tra context không rò giữa task.
- [ ] Nếu dùng `@Async`, Reactor hoặc task executor, kiểm tra context ở thread thực hiện công việc, không chỉ thread gọi ban đầu.
- [ ] Đọc kết quả từ test thật trước khi tuyên bố code production thread-safe; chạy thêm stress/load test nếu hệ thống có ràng buộc tải lớn.

Một **Spring AOP concurrency test** đáng tin phải kiểm tra cả ba yếu tố: **đúng proxy/pointcut**, **lời gọi thật sự chồng lấn**, và **state/cleanup của cùng aspect instance đúng sau khi toàn bộ Future hoàn tất**.

## 10. Tài liệu chính thức

- [Spring Framework — Aspect Instantiation Models](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/instantiation-models.html): singleton mặc định, `perthis`, `pertarget`, `pertypewithin`.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): proxy-based AOP, self-invocation, JDK/class-based proxy.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): `@Around`, `proceed`, `@After` và exception semantics.
- [Spring Framework — Testing](https://docs.spring.io/spring-framework/reference/testing.html): các khả năng Spring Test và kiểm thử integration.
- [JUnit Jupiter — User Guide](https://docs.junit.org/current/user-guide/): `@Test`, assertions và cách chạy JUnit 5.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-testing-010">Tương tác giữa mock, spy và proxy trong Spring Test</a>

<details>
<summary>Xem chi tiết</summary>

# Tương tác giữa mock, spy và proxy trong Spring Test

## 1. Ba khái niệm dễ bị nhầm khi kiểm thử Spring AOP

Một Spring service trong production có thể là **Spring AOP proxy**, trong khi test muốn thay dependency bằng Mockito mock hoặc bọc một service thật bằng Mockito spy để kiểm tra lời gọi. Có ba đối tượng hoàn toàn khác nhau:

- **Mockito mock**: test double được lập trình phản hồi bằng `when(...)`, `doReturn(...)` và kiểm tra interaction bằng `verify(...)`. Mặc định không chạy business method thực.
- **Mockito spy**: test double bọc instance có thật, thường thực thi method gốc nếu chưa stub. Nó ghi lại lời gọi, nhưng việc stub cũng có thể tương tác với real method.
- **Spring AOP proxy**: object do Spring tạo để áp dụng `@Around`, `@Before`, `@Transactional`, `@Cacheable`, method-security, retry hoặc advisor khác. Spring proxy không phải Mockito mock/spy; nó chuyển invocation qua interceptor chain.

Khi các cơ chế gặp nhau, test cần phân biệt: **đang mock dependency của target**, **đang thay thế chính bean target bằng mock**, hay **đang spy target mà Spring proxy vẫn phải hoạt động**. Ba lựa chọn có ý nghĩa khác nhau đối với advice.

Ví dụ, kiểm tra `@Around` ghi audit cho `OrderService.submit(...)`:

1. Mock `Gateway` mà `OrderService` gọi: **OrderService vẫn thật và vẫn được Spring proxy**, advice có cơ hội chạy.
2. Mock toàn bộ `OrderService`: Spring TestContext **thay bean gốc**, nên mock replacement thông thường không có advice của AOP proxy vốn bọc bean gốc.
3. Spy `OrderService`: Spring TestContext **bọc instance thật bằng spy**, rồi Spring AOP proxy vẫn đứng bên ngoài để advice chạy, tùy hạ tầng/configuration.

Đây là chủ đề **Spring Aspect / Spring Test**, không phải hướng dẫn tổng quát về Mockito. Mọi ví dụ nhấn mạnh đối tượng nào được đưa vào `ApplicationContext`, nơi Spring áp dụng pointcut và ranh giới kiểm chứng hành vi proxy.

## 2. Hình dung chính xác các lớp đối tượng

### 2.1. Bean thông thường có Spring AOP

~~~text
Caller / controller / JUnit
      |
      v
[ Spring AOP proxy ]
      |
      +-- @Around / transaction / cache / retry advisor
      |
      v
[ OrderService target ]
      |
      v
[ Gateway collaborator ]
~~~

`AopUtils.isAopProxy(orderService)` thường là `true` khi bean có advisor match. Không cần mọi method đều match; proxy có thể chứa nhiều advisor cho những methods khác nhau.

### 2.2. Mock dependency: aspect vẫn bọc service thật

~~~text
Caller
  |
  v
[ Spring AOP proxy for OrderService ]
      |
      +-- AuditAspect
      |
      v
[ Real OrderService target ]
      |
      v
[ Mockito mock of Gateway ]
~~~

Thay `Gateway` giữ nguyên **target class**, pointcut và advice của `OrderService`, nhưng kiểm soát response/failure của dependency. Đây thường là lựa chọn tốt nhất khi **đang test chính Spring aspect** trên một service thật.

### 2.3. Mock chính bean service: AOP của bean bị thay mất

~~~text
Caller
  |
  v
[ Mockito mock of OrderApi ]
  |
  +-- stubbed answer
  X-- no OrderService @Around advice
  X-- no real OrderService target
~~~

Trong Spring Framework 6.2+, `@MockitoBean` dùng chiến lược bean override **REPLACE_OR_CREATE** (hoặc **REPLACE** khi `enforceOverride=true`). Override được đăng ký trực tiếp dưới dạng singleton, **bỏ qua những BeanPostProcessor vốn sẽ tạo Spring AOP proxy** cho bean gốc. Vì vậy nếu mock **chính service** cần advice, bạn không còn kiểm thử advice trên service thật nữa.

### 2.4. Spy chính service: proxy nằm ngoài spy

~~~text
Caller / JUnit / collaborator
          |
          v
[ Spring AOP proxy ]
          |
          +-- AuditAspect / Cache / Transaction / Retry
          |
          v
[ Mockito spy for OrderService ]
          |
          +-- unstubbed method -> original behavior
          +-- stubbed method   -> programmed answer
~~~

`@MockitoSpyBean` dùng chiến lược **WRAP**: lấy instance bean gốc ở giai đoạn trước post-processing rồi tạo Mockito spy. Các BeanPostProcessor tiếp theo vẫn có cơ hội tạo Spring AOP proxy bao quanh spy. Trong trường hợp bean thực sự được advised, object được inject vào test và các bean khác là **Spring proxy**; spy thực sự là **target bên trong**.

**Đây là khác biệt cốt lõi:** Mockito verification và stubbing có thể hướng tới spy bên trong, nhưng invocation từ caller vẫn có thể đi qua cache/transaction/retry advice bên ngoài. Nếu stubbing qua proxy, AOP advice cũng nhìn thấy *lời gọi lúc cấu hình stub*, dẫn đến những bẫy khó phát hiện.

## 3. Phiên bản annotation cần dùng

Từ **Spring Framework 6.2**, Spring TestContext có:

~~~java
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
~~~

Chúng được dùng với `@SpringJUnitConfig`, `@ContextConfiguration` hoặc các hình thức Spring TestContext khác. Ở Spring Framework 6.1 trở về trước **không có cặp annotation này** trong `spring-test`; không copy code 6.2 vào dự án 6.1 rồi mong biên dịch được.

Spring Boot từng cung cấp annotation khác:

~~~java
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
~~~

Hai annotation `MockBean`/`SpyBean` của Spring Boot **không cùng package** với `MockitoBean`/`MockitoSpyBean` của Spring Framework. Chúng bị đánh dấu deprecated từ **Spring Boot 3.4**, theo hướng chuyển sang cơ chế bean override của Spring Framework. Khi migrate, không chỉ tìm và thay tên import: cần đánh giá `WRAP`, singleton, field qualifiers, context hierarchy, reset behavior và AOP proxy semantics.

Trong README này, ví dụ runnable dùng **Spring Framework 6.2.18**, `spring-test` cùng phiên bản, Java 17, JUnit Jupiter và Mockito. Không cần Spring Boot.

## 4. Bảng quyết định: mock dependency, mock target hay spy target?

| Ý định kiểm thử | Cách tiếp cận | Spring AOP advice của OrderService? | Business implementation? |
| --- | --- | --- | --- |
| Test aspect trên service, tách network/database | `@MockitoBean` ở **Gateway** | Có, nếu target/pointcut vẫn match | Có |
| Test caller orchestration, không muốn chạy service thật | `@MockitoBean` ở **OrderApi** | Không, nếu chính bean này bị replace | Không, trừ custom answer |
| Test method service thật đồng thời đếm lời gọi | `@MockitoSpyBean` ở **OrderApi/OrderService** | Có thể giữ nguyên AOP proxy bên ngoài spy | Có nếu method chưa stub |
| Test pure service business logic, không quan tâm aspect | Tạo service thuần với mock dependency | Không có Spring proxy | Có |
| Test pointcut/advisor thực sự match | Spring context thật + target thật, mock dependency khi cần | Phải có, được assert | Có |
| Test khi advice bị loại khỏi context | Cấu hình đối chứng không bật aspect | Không | Có |
| Test caching short-circuit/retry loops | Spy service thật + test hạ tầng AOP đúng | Có, nhưng có thể chặn/nhân lần gọi target | Có tùy cache/retry |

Không dùng một test **mock toàn bộ bean service** làm bằng chứng rằng `@Transactional`, `@Cacheable` hoặc `@Around` của service đang chạy. Đó là kiểm thử khác: nó có thể hợp lệ với controller hoặc workflow, nhưng không chứng minh proxy AOP.

## 5. Các ràng buộc quan trọng của bean override

### 5.1. @MockitoBean là replace, không phải tự động AOP-wrap

~~~java
@MockitoBean(name = "orderService", enforceOverride = true)
OrderApi orderService;
~~~

Khi bean `orderService` tồn tại, Spring TestContext thay instance bằng Mockito mock. `enforceOverride=true` yêu cầu **bean thực sự tồn tại**: nếu không, test context fail, thay vì vô tình thêm một mock bean mới rồi làm test pass sai mục tiêu. Nếu không bật flag này, mặc định `REPLACE_OR_CREATE` có thể tạo mock khi bean không có.

`@MockitoBean` không giữ `@Transactional`/retry/cache/AOP advice của bean gốc. Nếu chỉ cần stub dependency, đặt annotation trên **dependency**, không đặt trên chính target đang được kiểm thử aspect.

### 5.2. @MockitoSpyBean cần một bean hiện hữu có thể wrap

~~~java
@MockitoSpyBean(name = "orderService")
OrderApi orderService;
~~~

Spring phải xác định được đúng bean cần spy. `WRAP` không tạo một bean chưa từng tồn tại. Nếu có nhiều candidates cùng type, dùng tên `name` hoặc `@Qualifier`; field name có thể là fallback qualifier. Cần lưu ý **chỉ singleton beans** phù hợp cho `@MockitoSpyBean` trong dòng Spring 6.2; yêu cầu spy một non-singleton/scoped proxy có thể thất bại. Không tự đặt `@MockitoSpyBean` lên mọi bean có vòng đời scoped/prototype.

### 5.3. Chọn đúng bean theo tên và type

Nếu có hai implementation cùng interface:

~~~java
@MockitoBean(name = "billingGateway")
Gateway gateway;
~~~

nên dùng bean name cụ thể. Nếu để annotation suy luận theo type, nó phải chọn được một candidate duy nhất hoặc dựa trên qualifier phù hợp. `@MockitoBean` được dùng trên field test, không phải annotation dán lên bất kỳ service `@Configuration` theo phong cách cũ Spring Boot khi chưa xác minh annotation scope của version.

### 5.4. TestContext cache và reset

Spring TestContext cache `ApplicationContext` theo tổ hợp configuration, overrides và qualifiers. Hai test classes có cùng config nhưng **khác bean override definitions** có thể nhận contexts khác nhau; khác tên field/qualifier cũng có thể làm cache key khác.

Theo mặc định, Mockito mock/spy do Spring Test quản lý thường được **reset sau mỗi test method** (`MockReset.AFTER`). Reset Mockito **không** làm tự động reset mutable fields của các Spring singleton khác như event log/collector hoặc cache manager. Hãy tự clear những state đó trong `@BeforeEach` khi dùng cùng context cho nhiều test methods.

## 6. Ví dụ Java 17 và JUnit 5: ba loại test trong cùng một dự án

Project minh họa bốn tests:

1. Mock `Gateway` nhưng vẫn dùng **real OrderService + Spring AOP proxy**.
2. Spy `OrderService`, verify lời gọi qua **proxy** và thấy method thật chạy.
3. Stub **raw spy** an toàn rồi gọi lại qua AOP proxy để xác nhận advice vẫn chạy.
4. Mock **chính OrderService bean** và chứng minh proxy/advice của bean đó không còn.

Kết quả tests được assert bằng JUnit thay vì suy luận qua tên class proxy sinh tự động.

### 6.1. Cấu trúc thư mục

~~~text
spring-aop-mock-spy-proxy-test/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── OrderApi.java
    │   ├── Gateway.java
    │   ├── OrderService.java
    │   ├── AuditEvents.java
    │   └── AuditAspect.java
    └── test/java/demo/
        ├── TestBeans.java
        ├── MockDependencyTest.java
        ├── SpyTargetTest.java
        └── MockTargetTest.java
~~~

### 6.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>spring-aop-mock-spy-proxy-test</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.18</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.18</version>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.22.1</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-test</artifactId>
            <version>6.2.18</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>5.17.0</version>
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
                <configuration>
                    <release>17</release>
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

Spring TestContext được kích hoạt bởi `@SpringJUnitConfig` cùng JUnit Jupiter; Mockito mock/spy được tạo bằng `@MockitoBean` và `@MockitoSpyBean`. Không cần `MockitoExtension` riêng nếu chỉ sử dụng bean override của Spring Test; Spring tự quản lý reset tương ứng. `aspectjweaver` ở đây phục vụ hỗ trợ `@AspectJ` pointcut expressions trong Spring AOP proxy mode, **không** chạy AspectJ Java agent.

### 6.3. Business interface và gateway

**src/main/java/demo/OrderApi.java**

~~~java
package demo;

public interface OrderApi {
    String submit(String code);
}
~~~

**src/main/java/demo/Gateway.java**

~~~java
package demo;

public interface Gateway {
    String lookup(String code);
}
~~~

Dùng interface `OrderApi` để caller lấy **JDK interface proxy** rõ ràng. `Gateway` là dependency nhẹ đủ để mock; không xây repository hoặc network client chỉ để minh họa Spring AOP.

### 6.4. AuditEvents: recorder được aspect và target chia sẻ

**src/main/java/demo/AuditEvents.java**

~~~java
package demo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AuditEvents {
    private final CopyOnWriteArrayList<String> entries =
            new CopyOnWriteArrayList<>();

    public void add(String entry) {
        entries.add(entry);
    }

    public List<String> snapshot() {
        return List.copyOf(entries);
    }

    public void clear() {
        entries.clear();
    }
}
~~~

Recorder chỉ đóng vai trò **test observation**. Vì `SpringJUnitConfig` có thể reuse context giữa nhiều test methods, `clear()` giúp tránh event cũ ảnh hưởng assertion. `List.copyOf` trả snapshot theo thứ tự ghi và không cho test sửa collection đang được dùng.

### 6.5. OrderService target

**src/main/java/demo/OrderService.java**

~~~java
package demo;

public class OrderService implements OrderApi {
    private final Gateway gateway;
    private final AuditEvents events;

    public OrderService(Gateway gateway, AuditEvents events) {
        this.gateway = gateway;
        this.events = events;
    }

    @Override
    public String submit(String code) {
        events.add("target:" + code);
        return "order:" + gateway.lookup(code);
    }
}
~~~

Target không có `@Component`: cấu hình test đăng ký bằng `@Bean`, nên việc xác định bean được mock/spy hoàn toàn tường minh. Method `submit` chạy thật trong hai test đầu; test thứ tư mock `OrderApi` nên không còn chạy method này.

### 6.6. Spring Aspect

**src/main/java/demo/AuditAspect.java**

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class AuditAspect {
    private final AuditEvents events;

    public AuditAspect(AuditEvents events) {
        this.events = events;
    }

    @Around("execution(* demo.OrderService.submit(..))")
    public Object audit(ProceedingJoinPoint invocation) throws Throwable {
        String code = String.valueOf(invocation.getArgs()[0]);
        events.add("before:" + code);

        try {
            Object result = invocation.proceed();
            events.add("after:" + code);
            return result;
        } catch (Throwable error) {
            events.add("error:" + code);
            throw error;
        }
    }
}
~~~

Đây là aspect thực tế của Spring `@AspectJ` proxy mode. Pointcut match method `submit` của **concrete target type**. Khi `OrderService` được thay bằng **mock `OrderApi`**, bean gốc không còn chạy qua Spring proxy nên aspect này không được thực thi. Khi `OrderService` được spy, method target có thể vẫn chạy qua AOP proxy và advice vẫn ghi `before/after`.

### 6.7. Spring Test configuration

**src/test/java/demo/TestBeans.java**

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
class TestBeans {

    @Bean
    Gateway gateway() {
        return code -> "real:" + code;
    }

    @Bean
    AuditEvents events() {
        return new AuditEvents();
    }

    @Bean
    OrderApi orderService(Gateway gateway, AuditEvents events) {
        return new OrderService(gateway, events);
    }

    @Bean
    AuditAspect auditAspect(AuditEvents events) {
        return new AuditAspect(events);
    }
}
~~~

Chỉ có bốn business/test beans: gateway, recorder, service, aspect. Spring tự đăng ký auto-proxy infrastructure từ `@EnableAspectJAutoProxy`. Không sử dụng `@ComponentScan` toàn ứng dụng, database hoặc Spring Boot context.

Đặc biệt, `@Bean OrderApi orderService(...)` trả **interface type** dù implementation thật là `OrderService`. Spring AOP vẫn có thể xét target class thực tế để match pointcut, sau đó tạo JDK proxy triển khai `OrderApi`. Vì thế tất cả test/caller nên lấy service bằng `OrderApi`, không ép thành `OrderService`.

### 6.8. Test 1 — Mock dependency, giữ Spring AOP proxy

**src/test/java/demo/MockDependencyTest.java**

~~~java
package demo;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.aop.support.AopUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(TestBeans.class)
class MockDependencyTest {

    @MockitoBean(name = "gateway", enforceOverride = true)
    Gateway gateway;

    @Autowired
    OrderApi orderService;

    @Autowired
    AuditEvents events;

    @Test
    void mockingCollaboratorPreservesServiceAopProxy() {
        when(gateway.lookup("A")).thenReturn("stub:A");

        assertTrue(AopUtils.isAopProxy(orderService));
        assertTrue(AopUtils.isJdkDynamicProxy(orderService));

        assertEquals("order:stub:A", orderService.submit("A"));
        assertEquals(
                List.of("before:A", "target:A", "after:A"),
                events.snapshot()
        );

        verify(gateway).lookup("A");
    }
}
~~~

**Điểm chứng minh:** `Gateway` bị replace bằng mock, nhưng `OrderService` vẫn là target thật và Spring AOP proxy vẫn đứng trước nó. Caller gọi `orderService.submit("A")` qua proxy nên aspect chạy. Mockito mock chỉ can thiệp response của dependency, không vô hiệu hóa aspect trên service.

Trong một test phức tạp, nếu chính gateway cũng mang advice cần kiểm chứng, việc mock gateway sẽ bỏ advice của **gateway**. Nhưng đó không làm Spring tự bỏ advice của **OrderService** — hai bean khác nhau.

### 6.9. Test 2 và 3 — Spy target bên trong Spring proxy

**src/test/java/demo/SpyTargetTest.java**

~~~java
package demo;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.aop.support.AopUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.AopTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(TestBeans.class)
class SpyTargetTest {

    @MockitoSpyBean(name = "orderService")
    OrderApi orderService;

    @Autowired
    AuditEvents events;

    @BeforeEach
    void cleanEvents() {
        events.clear();
    }

    @Test
    void spyingTargetPreservesProxyAndRealBehavior() {
        assertTrue(AopUtils.isAopProxy(orderService));
        assertTrue(AopUtils.isJdkDynamicProxy(orderService));

        assertEquals("order:real:B", orderService.submit("B"));
        assertEquals(
                List.of("before:B", "target:B", "after:B"),
                events.snapshot()
        );

        // Spring Test resolves Mockito verification against the spy target.
        verify(orderService).submit("B");
    }

    @Test
    void stubRawSpyButInvokeThroughSpringProxy() {
        assertTrue(AopUtils.isAopProxy(orderService));

        // An toàn trong demo: target source là singleton tĩnh.
        OrderApi rawSpy =
                AopTestUtils.getUltimateTargetObject(orderService);

        assertTrue(mockingDetails(rawSpy).isSpy());

        // Stub trực tiếp spy target, KHÔNG stub qua AOP proxy.
        doReturn("stubbed:C").when(rawSpy).submit("C");

        // Caller vẫn đi qua AOP proxy, nên aspect vẫn hoạt động.
        assertEquals("stubbed:C", orderService.submit("C"));
        assertEquals(
                List.of("before:C", "after:C"),
                events.snapshot()
        );

        verify(rawSpy).submit("C");
    }
}
~~~

**Test 2:** `orderService` là Spring AOP proxy, spy là target bên trong. Unstubbed invocation đi tới real `OrderService`, nên event `target:B` xuất hiện. `verify(orderService)` dùng Spring Test/Mockito proxy-aware integration để kiểm chứng method trên spy; nó **không** chứng minh aspect chạy một mình, do đó test vẫn assert events.

**Test 3:** lấy spy target bằng `AopTestUtils.getUltimateTargetObject(orderService)` rồi stub **chính target spy**. Sau đó gọi `orderService.submit("C")` qua proxy để giữ advice `before/after`. Vì spy đã stub method, thân `OrderService.submit` không chạy, nên không có `target:C`; điều này được assert trực tiếp.

**Cảnh báo về unwrap:** `AopTestUtils.getUltimateTargetObject` thích hợp cho test khi proxy dùng `TargetSource` tĩnh. Spring API cảnh báo có thể có side effect hoặc vòng lặp khi unwrapping proxy dùng target source **không tĩnh** (scoped/pooled/dynamic). Không đưa đoạn unwrap vào production business code; chỉ dùng với test fixture hiểu rõ cấu trúc proxy.

### 6.10. Test 4 — Mock chính bean service làm AOP proxy biến mất

**src/test/java/demo/MockTargetTest.java**

~~~java
package demo;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.aop.support.AopUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(TestBeans.class)
class MockTargetTest {

    @MockitoBean(name = "orderService", enforceOverride = true)
    OrderApi orderService;

    @Autowired
    AuditEvents events;

    @Test
    void mockingTargetReplacesServiceProxyAndAdvice() {
        when(orderService.submit("D")).thenReturn("mock:D");

        assertFalse(AopUtils.isAopProxy(orderService));
        assertEquals("mock:D", orderService.submit("D"));

        // Không chạy OrderService, cũng không có AuditAspect advice.
        assertEquals(List.of(), events.snapshot());

        verify(orderService).submit("D");
    }
}
~~~

**Điều đã chứng minh:** bean service bị replace bằng Mockito mock trước giai đoạn post-processing tạo Spring AOP proxy. Dù `AuditAspect` bean vẫn tồn tại trong context, nó không phải là một interceptor “tự nhảy vào” lời gọi của bare mock. `AopUtils.isAopProxy(orderService)` là `false`; return value đến từ stub; không có event `before`/ `target`/ `after`.

Đây là test có ý nghĩa đối với kiểm thử **caller** cần isolate service, nhưng nó là **negative control** nếu mục tiêu là kiểm thử Spring AOP aspect trên service thật.

### 6.11. Chạy dự án và đọc kết quả

Trong thư mục chứa `pom.xml`:

~~~bash
mvn -q test
~~~

Expected: Maven Surefire chạy bốn test methods trong ba test classes, exit code 0 nếu các assertions đúng. Có thể xem các file:

~~~text
target/surefire-reports/demo.MockDependencyTest.txt
target/surefire-reports/demo.SpyTargetTest.txt
target/surefire-reports/demo.MockTargetTest.txt
~~~

Kỳ vọng:

~~~text
MockDependencyTest : 1 test, 0 failures, 0 errors
SpyTargetTest      : 2 tests, 0 failures, 0 errors
MockTargetTest     : 1 test, 0 failures, 0 errors
TOTAL              : 4 tests, 0 failures, 0 errors
~~~

Đây là **kỳ vọng logic của mã kiểm thử**, không phải một log Maven đã được chạy trong môi trường hiện tại; thời gian thực thi và format báo cáo Surefire có thể khác. Với `-q`, console có thể không in summary. Một số môi trường JDK/Mockito có thể cảnh báo dynamic agent attachment; nên kiểm tra hướng dẫn Mockito/JDK tương ứng nếu warning xuất hiện, không coi warning là chứng cứ advice đã chạy hoặc không chạy.

## 7. Bẫy lớn: stubbing Mockito spy qua AOP proxy

Mockito cho phép:

~~~java
when(spy.calculate()).thenReturn(value);
~~~

Nhưng với **spy**, cú pháp `when(spy.realMethod())` có thể gọi real method ngay trong quá trình thiết lập stub. Nếu method gửi request, ghi database hoặc thay đổi state, test có thể tạo side effect **trước khi bắt đầu lời gọi đang kiểm thử**.

Mockito khuyến nghị khi stub spy:

~~~java
doReturn("stubbed").when(spy).calculate();
~~~

Tuy nhiên trong Spring, biến `spy` mà test nhận từ `@MockitoSpyBean` **có thể thật ra là Spring AOP proxy bao ngoài spy**. Lời gọi `doReturn(...).when(orderService).submit(...)` khi đó vẫn phải đi qua **outer AOP advice** trong lúc Mockito ghi lại stub, dù real target method không chạy.

### 7.1. Vì sao @Cacheable làm stubbing qua proxy nguy hiểm?

Giả sử bean được Spring `@Cacheable` advise:

~~~text
Spring Cache AOP proxy
       |
       v
Mockito spy target
       |
       v
Real method
~~~

Nếu test gọi `doReturn("GOOD").when(proxiedSpy).load("A")`, Mockito đang trong pha đăng ký stub, nên lời gọi stub setup có thể trả một giá trị mặc định/placeholder từ spy (chẳng hạn null). Nhưng **cache interceptor ngoài spy** nhìn thấy invocation đó và có thể cache placeholder theo key `"A"`.

Sau đó `proxiedSpy.load("A")` có thể trúng cache, **không tới spy** để đọc stub `"GOOD"`. Trong trường hợp cụ thể này, test có thể trả null/mặc định hoặc dùng dữ liệu cache cũ, dẫn đến kết luận sai rằng Mockito stub không hoạt động.

Giải pháp được tài liệu Spring Test nêu rõ: **stub raw spy target**, sau đó gọi **proxy** ở bước kiểm thử:

~~~java
OrderApi targetSpy =
        AopTestUtils.getUltimateTargetObject(orderService);

doReturn("GOOD").when(targetSpy).submit("A");

// Lời gọi kiểm thử thật sự mới đi qua AOP proxy:
String result = orderService.submit("A");
~~~

Snippet cần bean `orderService`, static Mockito `doReturn`, import `AopTestUtils`. Nó minh họa cơ chế; với caching phải chuẩn bị cache manager, key và policy phù hợp. Việc unwrap được dùng ở test trên một static singleton target source; không áp dụng bừa với scoped/dynamic target sources.

### 7.2. Retry advice có hành vi khác caching advice

Retry advice thường gọi tiếp vào target và retry khi gặp exception. Stubbing qua proxy không nhất thiết gây lỗi như cache memoization; nhưng một cách viết như:

~~~java
doThrow(new IllegalStateException("temporary"))
    .doReturn("ok")
    .when(targetSpy).submit("R");
~~~

chỉ đạt “một lỗi rồi một thành công” ở mức **spy target invocations**. Để Spring Retry thực sự retry, phải gọi **qua retry-enabled Spring AOP proxy** và policy retry phải cho phép `IllegalStateException` như ví dụ, số attempts/backoff phải được cấu hình. Nếu gọi thẳng `targetSpy.submit("R")`, không có retry advice của proxy dù Mockito vẫn ghi nhận lời gọi.

Với transaction advice, stub raw spy có thể bỏ qua mọi database update/transaction-required logic vốn nằm trong method thật; vì vậy một test chỉ assert `@Transactional` proxy tồn tại sau khi stub method hoàn toàn **không chứng minh rollback/commit hoạt động**. Muốn test transaction, cần dùng method thật và một transaction manager/resource phù hợp.

### 7.3. Verify khác với stub

Trong hỗ trợ Spring Test Mockito hiện đại, `verify(proxiedSpy)` được xử lý theo hướng target-aware nên có thể hoạt động dù object nhận từ context là Spring AOP proxy. Tuy nhiên **verification thành công chỉ chứng minh spy method được gọi**; nó không chứng minh một advisor cụ thể đã chạy, có thể bị short-circuit bởi cache, hoặc đã rollback đúng.

Do đó bài kiểm thử tốt xác minh đồng thời:

- `AopUtils.isAopProxy(service)` — có Spring proxy.
- `Mockito.verify(...)` — target spy nhận method call dự kiến (khi advice cho phép đi tới target).
- Events hoặc trạng thái do aspect ghi — advice thực sự chạy.
- Return/exception/business effects — semantics của method không bị thay đổi ngoài chủ đích.
- Với `@Cacheable`, các cache hit có thể **không gọi target**; vì vậy lần gọi hai trúng cache phải assert `verify(spy, times(1))` chứ không nhất thiết `times(2)`.

## 8. Test nguyên nhân proxy được tạo, không chỉ tên object

`AopUtils` của Spring:

~~~java
Object bean = context.getBean("orderService");

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
System.out.println(AopUtils.getTargetClass(bean).getName());
~~~

Snippet giả định đã có `ApplicationContext context` và import `org.springframework.aop.support.AopUtils`. Đây là **quan sát runtime**, không nên parse tên class `$Proxy...` hay `$$SpringCGLIB$$...` bằng chuỗi để kết luận AOP.

Nếu bean **thay bằng @MockitoBean**, `AopUtils.isAopProxy(bean)` là `false` trong mô hình Spring Test bean override hiện tại. Nếu bean **spy bằng @MockitoSpyBean** và vốn được AOP advice, `isAopProxy` vẫn `true`, nhưng `Mockito.mockingDetails(bean).isSpy()` không phải luôn là phép kiểm tra đúng trên **outer Spring proxy**. Hãy lấy target spy một cách có kiểm soát trong test, như ví dụ.

Nếu proxy expose `Advised`:

~~~java
if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor);
    }
}
~~~

Cần lưu ý một số Spring proxies có thể cấu hình opaque, không expose `Advised`. Danh sách advisors chưa đủ: một pointcut có thể không match method cụ thể. Kiểm thử method thực qua proxy và assert events mới là bằng chứng đủ hơn.

## 9. Spy có thể bị cache/async/transaction làm lệch kỳ vọng số lần gọi

| Spring advice | Ảnh hưởng tới Mockito spy/verify | Cách test phù hợp |
| --- | --- | --- |
| `@Before` / `@Around` logging | Advice chạy khi gọi qua proxy; spy chạy nếu `proceed()` tới target | Assert event order + verify spy |
| `@Cacheable` | Cache hit có thể không gọi target spy | Assert cache hits/misses, `times(n)` theo số lần thực vào target |
| `@Retryable` | Một proxy invocation có thể gọi target spy nhiều attempts | Assert retry policy, `times(attempts)`; stub raw spy |
| `@Transactional` | Advice quản lý transaction bên ngoài target; stub method có thể tránh DB work | Test real DB / transaction boundary thật khi cần |
| `@Async` | Invocation có thể chạy ở executor thread và trả Future/CompletableFuture | Chờ kết quả hoàn tất, tránh verify ngay khi chưa đồng bộ |
| Method security | Proxy có thể từ chối lời gọi trước khi đến spy | Assert access denied và spy không nhận invocation |
| `@Around` short-circuit | Advice có thể tự trả kết quả, không gọi `proceed()` | Không mặc định verify spy phải nhận method |
| Multiple proxies | Có thể có nhiều lớp AOP/target source | Kiểm tra wiring và chỉ unwrap trong test với static target |

**Đặc biệt quan trọng:** `verify(spy, times(1))` không có nghĩa caller chỉ gọi proxy một lần; cache có thể làm một lần miss + nhiều hits, retry có thể làm một proxy call dẫn tới nhiều target calls. Phải xác định **đơn vị đo của assertion**: proxy entrypoint, target invocation, advice invocation hay external side effect.

## 10. Mock và spy có thể thay đổi điều gì trong bean lifecycle?

### 10.1. Thay bean thường không chạy constructor/initialization của target gốc

Khi `@MockitoBean` thay thế một bean definition, test không nên mặc định rằng constructor, `@PostConstruct`, lifecycle callback và AOP weaving/proxy của bean gốc vẫn chạy. Mockito mock là một object khác, chỉ có contract của test double. Đây chính là lý do một controller test với mock service có thể rất nhanh nhưng không thích hợp để kiểm tra aspect trên service thật.

### 10.2. Spy thường dùng existing singleton, nhưng không phải mọi bean đều có thể spy

`@MockitoSpyBean` cần một bean hiện hữu, singleton có thể wrap. Với `prototype`, request-scoped, session-scoped hoặc các proxy đặc biệt, bean override có thể fail; việc ép đổi scope chỉ để spy có thể làm test không còn phản ánh production.

Nếu target object có side effects ngay constructor hoặc init, spy **không** phải mock thuần: instance thật có thể đã được tạo, initialized trước khi spy được wrap. Hãy fake collaborator của target nếu constructor có tài nguyên ngoài, thay vì mong spy ngăn mọi side effects.

### 10.3. FactoryBean và bean override

Với `FactoryBean`, `@MockitoBean` thường thay **object do FactoryBean sản xuất** bằng singleton mock; `@MockitoSpyBean` spy object được tạo bởi FactoryBean thay vì spy chính `FactoryBean`. Tên `&beanName` vẫn có ý nghĩa riêng trong Spring IoC: không đoán target override bằng cách đổi tuỳ tiện prefix `&`.

Khi bean được tạo từ `FactoryBean` đồng thời nhận AOP advisor, nên có integration test riêng xác minh product, spy target và proxy strategy.

### 10.4. Nhiều ApplicationContexts và context hierarchy

`@MockitoBean`/`@MockitoSpyBean` trong context hierarchy có thể ảnh hưởng nhiều levels nếu không giới hạn. Trên các phiên bản hỗ trợ, dùng `contextName` khớp với tên `@ContextConfiguration` của level định thay thế. Một mock đặt nhầm ở parent/child context có thể khiến test tưởng đang kiểm tra aspect nhưng thực ra đang gọi bean từ context khác.

Với nhiều bean cùng interface, sử dụng `name`/qualifier, và giữ cùng tên field ở những test classes muốn reuse test context để tránh tạo cache keys khác ngoài ý muốn.

## 11. Cách viết integration test Spring Aspect đáng tin

Quy trình đề xuất:

1. **Chọn bean cần kiểm thử aspect**: để bean này là **real target** hoặc `@MockitoSpyBean`, tránh mock chính nó.
2. **Cô lập collaborator không liên quan** bằng `@MockitoBean` khi collaborator không phải đối tượng đang test AOP.
3. **Bật advisor thật** trong context test (`@EnableAspectJAutoProxy`, transaction/cache/retry configuration nếu có).
4. **Inject bean từ Spring TestContext**, không tự `new` target hoặc tự tạo Mockito proxy ngoài container.
5. **Assert proxy identity** bằng `AopUtils.isAopProxy` khi cần, rồi assert **advice behavior** bằng event/counter/transaction state.
6. **Nếu spy**, phân biệt outer AOP proxy với inner Mockito spy; stub raw spy cho cache/memoizing/short-circuiting advice.
7. **Nếu verify số lần**, xét cache hits, retries, async completion và pointcut coverage thay vì mặc định một method call = một target invocation.
8. **Assert cả positive và negative paths**: method match và không match, exception, stubbing không gây side effect, override bean thiếu tên.
9. **Kiểm soát state và context cache**: Mockito reset không xóa recorder, repository in-memory hay cache tự động.
10. **Tránh bypass proxy** bằng cách gọi trực tiếp target sau khi unwrap. Unwrap chỉ phục vụ Mockito stubbing/inspection trong test, còn invocation nghiệp vụ vẫn gọi qua Spring bean proxy.
11. **Không nhầm pass của mock-driven test với AOP pass**: target mock có thể làm test green dù aspect chưa được tạo.
12. **Khi chuyển phiên bản**, kiểm tra package annotation, bean override semantics, Spring AOP proxy mode, Mockito agent/JDK compatibility và docs chính thức.

## 12. Những lỗi phổ biến

| Triệu chứng | Nguyên nhân thường gặp | Cách sửa |
| --- | --- | --- |
| Test mock service pass nhưng audit advice không chạy | `@MockitoBean` thay đúng bean cần AOP, làm mất proxy | Mock collaborator, giữ service thật hoặc spy service |
| `AopUtils.isAopProxy=false` ở bean mock | Bean override đăng ký bare mock, không qua AOP post-processing | Đây là hành vi đúng; không dùng mock đó để test aspect |
| `@MockitoSpyBean` field có type interface nhưng `Mockito.mockingDetails(field).isSpy` không như mong đợi | Field thực tế là Spring AOP proxy bao spy | Kiểm tra proxy bằng `AopUtils`, lấy target spy qua `AopTestUtils` khi an toàn |
| `when(spy.method())` gửi HTTP/ghi DB lúc setup | Mockito có thể gọi real method trong khi stub spy | `doReturn`/`doThrow` trên raw spy |
| `doReturn(...).when(proxy)` nhưng `@Cacheable` trả dữ liệu rỗng/cũ | Lời gọi stub setup đã đi qua cache advice | Stub inner spy, gọi outer proxy để test |
| `verify(spy, times(1))` fail dù caller gọi proxy một lần | Retry tăng attempts, hoặc AOP advice gọi thêm target | Assert theo số target invocations thực sự |
| Spy target không nhận call lần hai | Cache hit/short-circuit bypass target | Kiểm tra cache/advisor, không mặc định phải có hai target calls |
| `@MockitoSpyBean` báo thiếu bean | WRAP yêu cầu bean tồn tại | Đăng ký bean trong test config, kiểm tra `name` |
| `@MockitoSpyBean` báo nhiều candidates | Cùng type nhiều beans | Dùng `name` hoặc qualifier |
| Thử spy request-scoped/prototype bean thất bại | WRAP có ràng buộc singleton | Đổi chiến lược test, đừng làm sai production scope |
| Mock tưởng reset nhưng recorder còn events | Mockito reset không reset Spring singleton collaborator | Clear recorder/cache trong `@BeforeEach` |
| `@MockBean` từ Boot không tìm thấy trong dự án Spring thuần | Nhầm package/framework version | Với Spring 6.2+ dùng `@MockitoBean` của `spring-test` |
| JDK proxy không cast sang class implementation | Interface proxy không phải subclass target | Inject theo interface và dùng AopUtils |
| Tự `new OrderService` với Mockito gateway nhưng không thấy advice | Object không qua Spring auto-proxy | Gọi bean Spring TestContext |
| Mockito verify thành công nhưng transaction không rollback | Verify kiểm tra tương tác target, không kiểm tra transaction boundary | Test với transaction manager/resource thật |
| Unwrap scoped/pooled proxy gây lỗi | TargetSource không tĩnh, unsafe unwrapping | Tránh unwrap; dùng fixtures/controls phù hợp |
| Test khác chạy chung context làm state thay đổi | Context cache + mutable singleton | Reset state, tránh shared mutable test fixtures |

## 13. Đối chứng để tách lỗi Mockito và lỗi Spring AOP

Khi test sai, phân chia bằng ba câu hỏi:

### 13.1. Có Spring proxy không?

~~~java
boolean proxied = AopUtils.isAopProxy(orderService);
~~~

Nếu `false`, xem bean đã bị `@MockitoBean` thay thế chưa, auto-proxy infrastructure có bật không, hoặc pointcut có thật sự match target không.

### 13.2. Có Mockito mock hoặc spy không?

~~~java
boolean mock = Mockito.mockingDetails(candidate).isMock();
boolean spy = Mockito.mockingDetails(candidate).isSpy();
~~~

Đây là snippet với biến `candidate` đã được khai báo, import `org.mockito.Mockito`. Khi `candidate` là **Spring AOP proxy ngoài cùng**, Mockito metadata có thể không phản ánh spy nằm trong; phải kiểm tra đúng layer. Không tự ý unwrap dynamic proxy trong production.

### 13.3. Advice nào đã chạy?

Một `@Around` có thể ghi event **trước và sau** method, nhưng một `@Cacheable` hoặc `@Retryable` có thể kiểm soát liệu target có được gọi hay không. Hãy assert event/counter có ý nghĩa và kết quả method thay vì chỉ assert `verify(mock)` trên một instance bất kỳ.

Ví dụ test matched/nonmatched:

~~~java
assertTrue(AopUtils.isAopProxy(orderService));
assertEquals("order:real:X", orderService.submit("X"));
assertEquals(List.of("before:X", "target:X", "after:X"),
             events.snapshot());
~~~

Snippet này giả định cấu hình thật giống ví dụ mục 6, và không có stub khác cho `Gateway`. Nó chứng minh không chỉ **service được proxy** mà **advice thực sự chạy**.

## 14. Khi nào không nên dùng spy?

Spy hấp dẫn vì vừa chạy code thật vừa cho phép Mockito verify, nhưng nó tăng phức tạp trong một Spring AOP context:

- **Cache hoặc retry** thay đổi số target invocations; assertion trên spy cần hiểu policy.
- **Stateful singleton** có thể bị giữ qua Spring TestContext cache, dẫn tới test phụ thuộc thứ tự.
- **Transactional code** chạy thật có thể viết DB hoặc gọi remote service nếu collaborator không được cô lập.
- **Lazy/prototype/scoped targets** không luôn phù hợp với `@MockitoSpyBean`.
- **Stubbing qua AOP proxy** có thể đi qua cache/transaction/security advice ngay ở bước test setup.
- Spy trên class có **final/complex methods** đôi khi phụ thuộc Mockito mock maker và JVM instrumentation; không coi mọi class đều spy dễ dàng trong mọi môi trường.

Nếu mục tiêu chỉ là xác minh advice có chạy, một **real target + fake/mock dependency + EventLog** thường rõ hơn spy. Dùng spy khi thật sự cần **verify số lần gọi bên trong target**, hoặc muốn stub một method cụ thể của target trong khi vẫn giữ Spring AOP proxy.

## 15. Tổng kết

Trong Spring Test, **mock, spy và AOP proxy là ba lớp hành vi khác nhau**. `@MockitoBean` trên một bean dùng chiến lược **REPLACE_OR_CREATE/REPLACE**, nên bean mới là Mockito mock trực tiếp, **không còn Spring AOP proxy/advice của bean gốc**. `@MockitoSpyBean` dùng **WRAP**, thường giữ Spring AOP proxy bên ngoài Mockito spy target, nên có thể kiểm tra advice và behavior của target cùng lúc.

Khi viết test Aspect trong Spring:

- **Ưu tiên mock dependency**, không mock chính target cần kiểm thử advice.
- **Spy target khi cần**, nhưng đừng nhầm outer Spring proxy với inner Mockito spy.
- **Stub raw spy**, rồi gọi **Spring proxy** để tránh side effects hoặc cache pollution lúc thiết lập stub.
- **Verify proxy/advice và method interactions riêng biệt**; một Mockito `verify` thành công không đủ chứng minh aspect chạy.
- **Kiểm soát scope, context cache và lifecycle** để test không phụ thuộc môi trường khác.

### Tài liệu Spring chính thức

- [Spring Framework 6.2 — @MockitoBean và @MockitoSpyBean](https://docs.spring.io/spring-framework/reference/6.2/testing/annotations/integration-spring/annotation-mockitobean.html).
- [Spring Framework 6.2 — Bean overriding trong tests](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/bean-overriding.html).
- [Spring Framework — Spring AOP proxying mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).
- [Spring Framework — AopTestUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/test/util/AopTestUtils.html).
- [Spring Framework — SpringJUnitConfig](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/test/context/junit/jupiter/SpringJUnitConfig.html).
- [Spring Boot — Deprecated MockBean/SpyBean package](https://docs.spring.io/spring-boot/3.4/api/java/org/springframework/boot/test/mock/mockito/package-summary.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-testing-011">Đổi cấu hình Java/XML mà vẫn giữ hành vi đã xác nhận</a>

<details>
<summary>Xem chi tiết</summary>

# Đổi cấu hình Java/XML mà vẫn giữ hành vi đã xác nhận trong Spring AOP

## 1. Bài toán: thay cách khai báo, không đổi hợp đồng AOP

Một ứng dụng Spring có thể cấu hình Aspect-Oriented Programming (AOP) bằng Java configuration hoặc XML. Khi chuyển từ XML sang Java (hoặc ngược lại), tiêu chí thành công là **hành vi tại Spring proxy vẫn đúng như trước**: cùng lời gọi nào phải được advice, cùng lời gọi nào không được advice, cùng loại kết quả/exception, cùng tác dụng phụ cần thiết và cùng quan hệ thứ tự giữa các advice có ý nghĩa.

Chỉ kiểm tra ứng dụng khởi động thành công là chưa đủ. Một migration vẫn có thể làm lệch pointcut, thay kiểu proxy, quên đăng ký aspect bean, nhân đôi advice hoặc đổi thứ tự các interceptor. Các lỗi này thường chỉ xuất hiện khi chạy đúng luồng nghiệp vụ.

**Phạm vi của bài:** cách cấu hình và kiểm thử **Spring AOP proxy**; không suy rộng thành AspectJ compile-time/load-time weaving. `@Aspect` là cách khai báo aspect, còn việc bật Spring AOP, tạo advisor và tạo proxy vẫn phụ thuộc Spring container.

Hai tình huống phải phân biệt ngay từ đầu:

| Cấu hình ban đầu | Cấu hình đích | Có thể giữ nguyên aspect Java? |
| --- | --- | --- |
| `<aop:aspectj-autoproxy/>` và một bean có `@Aspect` | `@EnableAspectJAutoProxy` và đăng ký đúng bean `@Aspect` | **Có**; hai cách đều bật bộ xử lý `@AspectJ` qua Spring proxy |
| `<aop:config>` chứa `<aop:pointcut>`, `<aop:aspect>`, `<aop:before>`... | Các class `@Aspect`, `@Before`, `@Around`... và Java config | **Cần chuyển logic khai báo XML sang annotation**, kiểm tra mapping pointcut, binding và thứ tự; không chỉ thay một dòng bật AOP |
| `<aop:advisor>` dùng advice bean hoặc transaction interceptor | Advisor/`@Aspect`/cấu hình transaction tương ứng | **Không mặc định tương đương**; phải xét semantics của advisor, metadata và transaction trước khi chuyển |

Trong ví dụ runnable dưới đây, ta kiểm chứng tình huống đầu: **cùng một `@Aspect` và cùng ba bean nghiệp vụ/probe**, chỉ thay Java config bằng XML config. Phần sau trình bày cách mở rộng quy trình nếu dự án đang dùng XML `<aop:config>`.

## 2. Định nghĩa hành vi cần bảo toàn trước khi sửa

Với một advice audit chạy trước `CheckoutOperations.checkout(id)`, ma trận hợp đồng phải nêu rõ điều gì có thể quan sát:

| Nhóm hành vi | Ca thử | Kết quả cần giữ |
| --- | --- | --- |
| Advice dương tính | Gọi `checkout("A-42")` qua bean lấy từ Spring | Chính xác một sự kiện `before:A-42` trước khi method hoàn tất |
| Advice lặp | Gọi tiếp `checkout("B-7")` | Chính xác một sự kiện mới `before:B-7`; không gọi lặp cho `A-42` |
| Pointcut âm tính | Gọi `status()` | Không thêm event audit |
| Ranh giới proxy | Gọi `checkoutFromInside()`; target tự gọi `this.checkout("INTERNAL")` | Advice của `checkout` **không** chạy cho lời gọi nội bộ trong Spring AOP proxy |
| Giá trị trả về | `checkout("A-42")` trả `ok:A-42` | Không đổi khi đổi cấu hình |
| Loại proxy | Dịch vụ có interface, cấu hình `proxyTargetClass=false` | JDK dynamic proxy trong context thử nghiệm này |
| Cách đăng ký | `@Aspect` tồn tại đúng một lần | Không mất advice và không chạy đôi |
| Tên bean | `checkoutService`, `auditEvents`, `auditAspect` | Giữ các định danh và dependency cần thiết |

Nếu ứng dụng có transaction, authorization, retry hoặc xử lý exception, phải mở rộng ma trận để kiểm tra **rollback, exception propagation, thứ tự advice, trạng thái bảo mật và tác dụng phụ**. Một counter đơn thuần chỉ chứng minh advice được gọi, chưa chứng minh hành vi nghiệp vụ đã được bảo toàn.

Khi so sánh hai cấu hình, hãy so kết quả cùng bộ đầu vào thay vì chỉ so class hoặc số bean được tạo: hai context có thể khác cấu trúc mà vẫn có cùng hợp đồng, hoặc có cấu trúc tương tự nhưng pointcut thực tế khác nhau.

## 3. Những cặp Java/XML tương ứng trong Spring AOP

| Java configuration | XML configuration | Điều kiện để tương đương |
| --- | --- | --- |
| `@EnableAspectJAutoProxy` | `<aop:aspectj-autoproxy/>` | `@Aspect` vẫn được đăng ký làm **Spring bean** |
| `@EnableAspectJAutoProxy(proxyTargetClass = true)` | `<aop:aspectj-autoproxy proxy-target-class="true"/>` | Kiểm tra class-based proxy và các giới hạn final/private |
| `@EnableAspectJAutoProxy(exposeProxy = true)` | `<aop:aspectj-autoproxy expose-proxy="true"/>` | Chỉ cần khi code thực sự dùng `AopContext.currentProxy()`; cân nhắc refactor |
| `@Bean AuditAspect auditAspect(...)` | `<bean id="auditAspect" class="...AuditAspect">...` | Cùng aspect class, constructor arguments, scope và bean name khi cần |
| `@Bean("checkoutService")` | `<bean id="checkoutService" class="...CheckoutService"/>` | Cùng implementation, lifecycle/dependency và qualifiers |
| `@Before("execution(...)")` | `<aop:before method="..." pointcut="execution(...)"/>` trong `<aop:config>` | Chỉ tương ứng khi dùng **schema-style aspect**; kiểm tra arg binding và semantics |
| `@Order` hoặc `Ordered` trên aspect | `order` của `<aop:aspect>`/`<aop:advisor>` hoặc `@Order` trên bean | Kiểm chứng thứ tự bằng test quan sát được, nhất là nhiều aspect |

**Quan trọng:** `<aop:aspectj-autoproxy>` và `<aop:config>` **không phải cùng một kiểu khai báo**. Cái đầu bật auto-proxy cho Spring bean mang `@Aspect`; cái sau khai báo pointcut, advisor, advice trực tiếp bằng XML. Không chuyển `<aop:config>` sang `@EnableAspectJAutoProxy` rồi xóa toàn bộ XML advice nếu chưa viết lại những advice đó.

Ngoài ra, Spring có thể gom nhiều yêu cầu cấu hình AOP/transaction vào **một auto-proxy creator**. Một cấu hình khác với `proxyTargetClass=true` có thể ép class-based proxy dù ở đây bạn đặt `false`. Với Spring Boot, cấu hình tự động AOP và thuộc tính `spring.aop.proxy-target-class` cũng có thể ảnh hưởng kết quả. Luôn quan sát **proxy thực tế** ở ứng dụng production, không suy luận loại proxy từ một annotation duy nhất.

## 4. Ví dụ chạy được: cùng hợp đồng với Java config và XML config

Ví dụ dùng **Java 21, JUnit 5, Spring Test và Spring AOP**. Gradle dùng BOM của Spring Boot 3.3.5 để đồng bộ dependency. Test tạo **hai Spring context độc lập**, dùng cùng interface, target và `@Aspect` rồi assert hành vi của cả hai.

### 4.1. Cấu trúc tệp

~~~text
spring-aop-config-contract/
├── settings.gradle
├── build.gradle
└── src/test/
    ├── java/com/example/aop/
    │   ├── CheckoutOperations.java
    │   ├── CheckoutService.java
    │   ├── AuditEvents.java
    │   ├── AuditAspect.java
    │   └── AopConfigEquivalenceTest.java
    └── resources/spring-aop.xml
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-aop-config-contract'
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

Dù dùng BOM của Boot, bài kiểm thử chỉ khởi tạo các Spring context do ta chỉ định, nên **không có web server hay Spring Boot auto-configuration** chen vào kết quả.

### 4.2. Interface và target nghiệp vụ

`src/test/java/com/example/aop/CheckoutOperations.java`:

~~~java
package com.example.aop;

public interface CheckoutOperations {
    String checkout(String orderId);
    String status();
    String checkoutFromInside();
}
~~~

`src/test/java/com/example/aop/CheckoutService.java`:

~~~java
package com.example.aop;

public class CheckoutService implements CheckoutOperations {
    @Override
    public String checkout(String orderId) {
        return "ok:" + orderId;
    }

    @Override
    public String status() {
        return "ready";
    }

    @Override
    public String checkoutFromInside() {
        // Spring proxy không nhìn thấy lời gọi nội bộ này.
        return this.checkout("INTERNAL");
    }
}
~~~

Target không biết gì về Spring AOP. Interface cho phép cả hai context dùng JDK dynamic proxy khi cấu hình `proxyTargetClass=false`.

### 4.3. Probe và aspect dùng chung

`src/test/java/com/example/aop/AuditEvents.java`:

~~~java
package com.example.aop;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuditEvents {
    private final List<String> events = new CopyOnWriteArrayList<>();

    public void record(String event) {
        events.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }
}
~~~

`src/test/java/com/example/aop/AuditAspect.java`:

~~~java
package com.example.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    private final AuditEvents events;

    public AuditAspect(AuditEvents events) {
        this.events = events;
    }

    @Before(
        value = "execution(* com.example.aop.CheckoutOperations.checkout(..))"
              + " && args(id)",
        argNames = "id"
    )
    public void beforeCheckout(String id) {
        events.record("before:" + id);
    }
}
~~~

Vì cả hai cấu hình đăng ký **chính class `AuditAspect` này làm Spring bean**, ta loại bỏ sai khác về code advice khi so sánh. `argNames="id"` làm mapping tham số minh bạch, không lệ thuộc vào việc compiler có giữ tên tham số để AspectJ suy ra hay không. Nếu đổi biểu thức pointcut khi migration, hãy thêm test negative/positive cho chính phần thay đổi đó.

### 4.4. Cấu hình Java

Cấu hình Java nằm trong class test ở mục 4.6; phần cốt lõi là:

~~~java
@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false, exposeProxy = false)
public static class JavaAopConfig {
    @Bean("checkoutService")
    public CheckoutOperations checkoutService() {
        return new CheckoutService();
    }

    @Bean("auditEvents")
    public AuditEvents auditEvents() {
        return new AuditEvents();
    }

    @Bean("auditAspect")
    public AuditAspect auditAspect(AuditEvents events) {
        return new AuditAspect(events);
    }
}
~~~

**Đây là đoạn cấu hình trích từ file test đầy đủ ở dưới**, không phải file Java độc lập cần lưu hai lần. `@EnableAspectJAutoProxy` tạo hạ tầng Spring AOP; bản thân annotation này **không scan hoặc tạo `AuditAspect`**. Chính `@Bean("auditAspect")` làm aspect được container quản lý.

### 4.5. Cấu hình XML tương đương

`src/test/resources/spring-aop.xml`:

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

    <aop:aspectj-autoproxy
        proxy-target-class="false"
        expose-proxy="false"/>

    <bean id="checkoutService"
          class="com.example.aop.CheckoutService"/>

    <bean id="auditEvents"
          class="com.example.aop.AuditEvents"/>

    <bean id="auditAspect"
          class="com.example.aop.AuditAspect">
        <constructor-arg ref="auditEvents"/>
    </bean>
</beans>
~~~

Đây là XML **bật @AspectJ auto-proxy**, không phải XML viết mới pointcut trong `<aop:config>`. Aspect `AuditAspect` và business object hoàn toàn giống phía Java. Cả hai context đều khai báo rõ tên bean, constructor dependency, chế độ proxy và `exposeProxy`.

### 4.6. Test duy nhất chạy hai Spring context

`src/test/java/com/example/aop/AopConfigEquivalenceTest.java`:

~~~java
package com.example.aop;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AopConfigEquivalenceTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false, exposeProxy = false)
    public static class JavaAopConfig {
        @Bean("checkoutService")
        public CheckoutOperations checkoutService() {
            return new CheckoutService();
        }

        @Bean("auditEvents")
        public AuditEvents auditEvents() {
            return new AuditEvents();
        }

        @Bean("auditAspect")
        public AuditAspect auditAspect(AuditEvents events) {
            return new AuditAspect(events);
        }
    }

    private record Outcome(
        boolean jdkProxy,
        String status,
        String internalResult,
        String firstResult,
        String secondResult,
        List<String> auditEvents
    ) {
    }

    private static Outcome exerciseContract(
            ConfigurableApplicationContext context) {

        CheckoutOperations service =
            context.getBean("checkoutService", CheckoutOperations.class);
        AuditEvents events =
            context.getBean("auditEvents", AuditEvents.class);

        // Không chỉ tin cấu hình: kiểm tra proxy thực sự.
        assertTrue(AopUtils.isJdkDynamicProxy(service));

        String status = service.status();
        assertEquals("ready", status);
        assertEquals(List.of(), events.snapshot());

        // Ca âm: this.checkout bypass Spring proxy.
        String internal = service.checkoutFromInside();
        assertEquals("ok:INTERNAL", internal);
        assertEquals(List.of(), events.snapshot());

        // Ca dương: gọi checkout qua bean do Spring inject.
        String first = service.checkout("A-42");
        assertEquals("ok:A-42", first);
        assertEquals(List.of("before:A-42"), events.snapshot());

        String second = service.checkout("B-7");
        assertEquals("ok:B-7", second);
        assertEquals(
            List.of("before:A-42", "before:B-7"),
            events.snapshot());

        // Ca âm thêm: status() không làm tăng audit.
        assertEquals("ready", service.status());
        assertEquals(
            List.of("before:A-42", "before:B-7"),
            events.snapshot());

        return new Outcome(
            AopUtils.isJdkDynamicProxy(service),
            status,
            internal,
            first,
            second,
            events.snapshot()
        );
    }

    @Test
    void javaAndXmlHaveTheSameVerifiedAopBehavior() {
        try (AnnotationConfigApplicationContext javaContext =
                 new AnnotationConfigApplicationContext(JavaAopConfig.class);
             ClassPathXmlApplicationContext xmlContext =
                 new ClassPathXmlApplicationContext("spring-aop.xml")) {

            Outcome fromJava = exerciseContract(javaContext);
            Outcome fromXml = exerciseContract(xmlContext);
            assertEquals(fromJava, fromXml);
        }
    }
}
~~~

Chạy bằng Gradle với Java 21 (dự án có thể sử dụng Gradle wrapper đã tạo sẵn):

~~~bash
gradle test --tests com.example.aop.AopConfigEquivalenceTest
~~~

Kết quả kỳ vọng: **1 JUnit test thành công**. Output Gradle phụ thuộc version và terminal; không giả định chuỗi stdout chính xác. Test sẽ thất bại nếu một cấu hình thiếu proxy, thiếu advice, chạy advice trùng, match `status` hoặc self-invocation bị thay đổi thành một đường gọi qua proxy.

Hành vi chuẩn sau mỗi context:

~~~text
status():                    "ready"; audit []
checkoutFromInside():        "ok:INTERNAL"; audit []
checkout("A-42"):            "ok:A-42"; audit [before:A-42]
checkout("B-7"):             "ok:B-7"; audit [before:A-42, before:B-7]
status() lần hai:            "ready"; audit không đổi
~~~

Đây là **bảng kỳ vọng do test assert**, không phải stdout chương trình. Dù test pass, điều này chỉ chứng minh các case đã nêu trong context nhỏ; không thể tự suy rộng đến mọi bean/transaction trong ứng dụng lớn.

## 5. Khi chuyển từ XML `<aop:config>` sang Java `@Aspect`

Với schema-style XML, pointcut/advice thường được định nghĩa **trong XML**, còn bean chứa method advice chỉ là POJO. Chẳng hạn đoạn minh họa:

~~~xml
<aop:config>
    <aop:pointcut id="checkoutPoint"
        expression="execution(* com.example.aop.CheckoutOperations.checkout(..))"/>
    <aop:aspect ref="plainAuditAspect" order="10">
        <aop:before
            pointcut-ref="checkoutPoint"
            method="beforeCheckout"
            arg-names="id"/>
    </aop:aspect>
</aop:config>
~~~

**Đây chỉ là trích đoạn minh họa schema-style**, phải đặt bên trong `<beans>` hợp lệ và khai báo bean `plainAuditAspect` cùng method phù hợp, điều chỉnh binding nếu cần. Nó **khác** runnable XML `<aop:aspectj-autoproxy>` ở mục 4.5: ở đây XML giữ pointcut, advice, thứ tự; class Java chưa cần `@Aspect`. Khi chuyển sang annotation-style, cần làm đồng bộ:

- Di chuyển biểu thức `execution(...)` từ XML sang `@Pointcut`/`@Before`/`@Around` tương ứng, giữ nguyên phạm vi, tên method và argument binding.
- Giữ hoặc điều chỉnh `@Order(10)` cho aspect nếu XML trước đó dùng `order="10"`; lưu ý thứ tự **nhiều advice trong cùng một aspect** có thể có quy tắc khác giữa XML và annotation-style. Kiểm thử thứ tự ở những nơi nó ảnh hưởng kết quả.
- Thay bean plain aspect bằng bean `@Aspect` có các dependency tương ứng, tránh tạo cả hai cùng lúc. Các bean và lifecycle nghiệp vụ không cần thiết phải đổi cùng migration.
- Với `<aop:advisor>`, xem đó có thể là transaction interceptor, security advisor hoặc advice bean custom. Chúng cần migration riêng; `@EnableAspectJAutoProxy` không tự thay thế advisor đã khai báo.
- Kiểm tra các pointcut động như `args`, `@annotation`, `bean` và tên parameter của advice. Dấu `&&` trong thuộc tính XML cần escape thành `&amp;&amp;`. Trong Java string annotation thì dùng `&&` thông thường.

Một cách kiểm thử an toàn là dựng hai context chạy song song **trong test** từ bản cấu hình cũ và mới (cách tiếp cận ở mục 4), không bật hai bộ advisor cùng lúc trong production rồi hy vọng chúng tự hợp nhất.

## 6. Các chiều kiểm thử bắt buộc khi migration có nhiều aspect

Bộ test ở mục 4 bao phủ một advice `@Before` và cả ca match/non-match. Trong ứng dụng thực, hãy bổ sung ma trận dựa trên những yếu tố sau.

### 6.1. Pointcut và bean identity

| Chuyển đổi | Positive control | Negative control |
| --- | --- | --- |
| `execution(...)` với package mới | Method đúng tên/chữ ký và package vẫn có advice | Method ngoài package hoặc overload không được áp dụng |
| `@annotation(Audited)` | Method implementation có annotation | Method không có annotation |
| `bean(primaryCheckout)` | Bean tên primary đi qua proxy | Bean khác tên, dù cùng implementation |
| `args(String)` | Argument runtime là `String` | Argument runtime kiểu khác |
| `this(...)` / `target(...)` | Proxy và target có đúng kiểu mong đợi | Kiểu proxy đổi làm matcher khác đi |
| `!bean(backupCheckout)` | Bean không phải backup | Bean backup tuyệt đối không có advice |

Với mỗi nhánh boolean `&&`, `||` và `!`, xây truth table tối thiểu có cả ca âm tính. Đừng chỉ kiểm tra việc aspect được khởi tạo hoặc tên method khớp trên giấy.

### 6.2. Proxy type, đường gọi và object lifecycle

- **JDK proxy / class-based proxy:** `proxyTargetClass=false` thường chọn JDK proxy nếu target có interface; `true` ép class-based proxy. Đổi loại proxy có thể ảnh hưởng injection theo concrete type, `this(Type)`, method final/private và cả equality/toString được xử lý bởi proxy.
- **Self-invocation:** `this.checkout()` trong target không đi qua proxy. Chuyển Java/XML không tự khắc phục hiện tượng này; nếu phải thay đổi thành lời gọi tới bean khác, đó là **đổi hành vi/design** và phải có test riêng.
- **Đối tượng tạo bằng `new`:** object không được quản lý bởi Spring không tự nhận AOP proxy; test âm tính cần phân biệt với lỗi advisor.
- **Context hierarchy:** `@EnableAspectJAutoProxy` áp dụng cho context nơi đăng ký. Nếu XML nằm ở root context còn annotation mới ở child web context, tập bean được proxy có thể thay đổi; cần xác định ranh giới thực của bean factory.
- **Component scanning:** nếu trước đó XML đăng ký aspect bean rõ ràng còn Java config dựa vào `@ComponentScan`, kiểm tra aspect được scan đúng package và **chỉ có một bean**. `@Aspect` một mình không làm class thành component.
- **Bean aliases, scope và @Lazy:** đổi tên, qualifier, scope hoặc thời điểm khởi tạo làm cách áp dụng `bean(...)` và observable lifecycle khác đi. Chúng là một phần hợp đồng nếu ứng dụng đang dựa vào chúng.

### 6.3. Advice order, exception và transaction

Một lời gọi có thể khớp logging, authorization và transaction cùng lúc. Hãy kiểm tra hành vi theo một chuỗi sự kiện hoặc side effect xác định:

~~~text
authorized request:
  authorization allow
  transaction begin
  business succeeds
  transaction commit
  audit success

rejected request:
  authorization deny
  business not invoked
  transaction behavior as configured
  audit event according to contract
~~~

Đây là **kịch bản kỳ vọng phải tùy biến theo thứ tự advisor và nghiệp vụ**. Spring không tự cam kết chuỗi ví dụ trên cho mọi ứng dụng. Test cần xác minh ở cấu hình cũ trước khi khẳng định cấu hình mới tương đương.

Với `@Around`, phải bảo toàn việc gọi `ProceedingJoinPoint.proceed()`, argument được chuyển tiếp, exception được ném lại hoặc chuyển đổi, return value và số lần proceed. Với `@AfterReturning` và `@AfterThrowing`, kiểm tra ca thành công/lỗi tương ứng và binding `returning`/`throwing`. Với `@Order` hoặc XML `order`, chỉ assert thứ tự mà hợp đồng thực sự phụ thuộc; tránh dựa vào thứ tự không được đặc tả của nhiều advice có cùng precedence.

Nếu XML chứa `<tx:annotation-driven>` hoặc `<tx:advice>`, migration AOP có thể làm thay đổi transaction advisor dù `@Aspect` giữ nguyên. Hãy kiểm tra transaction manager, propagation, rollback rules và thứ tự interceptor bằng integration test thật, thay vì chỉ nhìn XML/annotation tương tự nhau.

## 7. Quy trình đổi cấu hình an toàn theo từng bước

1. **Đóng băng hành vi hiện tại bằng test:** chạy cấu hình cũ, ghi lại danh sách pointcut dương/âm, loại proxy, advice count, return/exception và side effect. Test cũ phải xanh trước khi đổi cấu hình.
2. **Lập bảng ánh xạ từng khai báo:** liệt kê AOP namespace, aspect beans, pointcuts, advisors, `@Order`/`order`, proxy options, context và dependency injection. Phân biệt `<aop:aspectj-autoproxy>` với `<aop:config>`.
3. **Tạo cấu hình mới trong Spring context độc lập:** giữ nguyên business classes và aspect implementation khi có thể. Đổi một nhóm khai báo mỗi lần để diff dễ đánh giá.
4. **Chạy cùng hợp đồng trên hai context:** assert cả positive và negative cases, số lần advice, proxy type và kết quả nghiệp vụ. Nếu có transaction hoặc security, chạy thêm test dữ liệu/authorization thực tế.
5. **Tìm đăng ký trùng:** trong context đích, không để cả cấu hình cũ và mới cùng khởi tạo hai aspect/advisor tương đương. Kiểm tra advisor list và đếm advice thực.
6. **Thử đúng packaging/config production:** Spring Boot auto-configuration, profiles, bean override, parent/child context, starter AOP và XML imports có thể khiến kết quả khác context tối giản.
7. **Chỉ xóa khai báo cũ sau khi hành vi mới đã xanh**, sau đó chạy test lại trên cấu hình cuối cùng. Đừng để trạng thái vừa XML vừa Java tồn tại như một bước production lâu dài trừ khi chủ động quản lý cả hai.

Đừng chỉ so file cấu hình bằng text diff: câu trả lời quan trọng là **Spring bean nào nhận proxy, advisor nào chạy trên method nào, trong thứ tự nào và tạo ra kết quả gì**.

## 8. Chẩn đoán khi hai cấu hình không còn tương đương

| Triệu chứng sau đổi cấu hình | Kiểm tra đầu tiên | Cách xác nhận |
| --- | --- | --- |
| Không có advice | Quên bean `@Aspect`, thiếu `@EnableAspectJAutoProxy`/XML tag, aspect ở sai context | Positive control thất bại; inspect `AopUtils.isAopProxy` và bean definitions |
| Advice chạy hai lần | XML và Java cùng đăng ký aspect/advisor, hoặc advisor trùng pointcut | Đếm event cho **một** method execution; kiểm tra danh sách advisor |
| Method khác bị intercept | Pointcut XML escape sai, package/type pattern đổi hoặc `@annotation` đổi phạm vi | Ma trận negative theo method, annotation và bean name |
| Không inject được concrete class | JDK proxy thay cho class-based proxy | `AopUtils.isJdkDynamicProxy`; injection nên theo interface khi phù hợp |
| `this(Type)`/`target(Type)` cho kết quả khác | Đổi chiến lược proxy | So kiểu proxy và target thực |
| Self-invocation không có advice | Lời gọi trực tiếp qua `this` đã vượt qua proxy | Test qua Spring bean và test nội bộ riêng |
| Transaction hoặc security thay đổi | Advisor/auto-proxy creator/order/transaction mode bị đổi | Test rollback, authorization và advisor order |
| Chỉ fail khi chạy full app | Spring Boot properties, test profile, component scan hoặc parent/child context khác | In bean names, active profiles và advisor chain trong runtime thực |
| Test âm tính đều pass nhưng advice thực tế mất | Chỉ có assertions “không phát sinh event” | Bắt buộc thêm test dương tính cùng suite |
| Test pass nhưng thực tế dữ liệu khác | Chỉ kiểm tra count thay vì nội dung advice | Assert DB state, exception, audit record và rollback tùy hợp đồng |

Có thể kiểm tra advisor chain cho một proxy khi cần chẩn đoán, chỉ trong test/debug:

~~~java
import java.util.Arrays;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

Object bean = context.getBean("checkoutService");
System.out.println("Is proxy: " + AopUtils.isAopProxy(bean));

if (bean instanceof Advised advised) {
    Arrays.stream(advised.getAdvisors())
          .forEach(advisor -> System.out.println(advisor));
}
~~~

Đây là **đoạn chẩn đoán đặt trong test có biến `context`**, không phải chương trình độc lập. Việc bean không implement `Advised` không tự chứng minh AOP chưa hoạt động trong mọi kiểu proxy; kết hợp với assertions hành vi. Không viết logic sản phẩm phụ thuộc chuỗi `advisor.toString()`.

## 9. Checklist nghiệm thu

- Cấu hình Java và XML cùng tạo đúng các Spring bean cần thiết, aspect vẫn được container quản lý.
- Spring đã tạo proxy thực và loại proxy đúng với yêu cầu, kể cả khi Boot hoặc cấu hình AOP khác tham gia.
- Ma trận pointcut có **positive và negative control**, kiểm tra lời gọi qua proxy và self-invocation.
- Advice không bị mất hoặc chạy trùng; thứ tự và binding tham số được test khi có ý nghĩa.
- Return value, exception propagation, transaction, security và side effect không đổi trong phạm vi hợp đồng.
- Test chạy trên cả cấu hình cũ/mới; sau khi migration, test vẫn chạy trên cấu hình triển khai cuối.
- Các khai báo XML cũ hoặc Java cũ đã được gỡ đúng phần, không để advisor thừa gây thay đổi hành vi.

**Kết luận:** Đổi cấu hình Spring AOP Java ↔ XML thành công khi **hợp đồng advice tại proxy** không đổi. Hãy chạy cùng một bộ kiểm thử hành vi qua hai Spring context trước khi thay cấu hình production; chỉ việc thay thế cú pháp cấu hình không đủ chứng minh tính tương đương.

## Tài liệu tham khảo chính thức

- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/enable.html)
- [Spring Framework — Schema-based AOP Support](https://docs.spring.io/spring-framework/reference/core/aop/schema.html)
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html)
- [Spring Framework — TestContext Framework](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-testing-012">Kiểm thử tích hợp với transaction, async hoặc cache theo tình huống cần dùng</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử tích hợp Spring AOP với transaction, async hoặc cache theo tình huống cần dùng

## 1. Mục tiêu và phạm vi

Khi một bean Spring vừa được áp dụng custom `@Aspect` vừa sử dụng `@Transactional`, `@Async` hoặc `@Cacheable`, các interceptor có thể nằm trên cùng đường gọi hoặc tách sang đường thực thi khác. Một test chỉ chứng minh custom advice đã chạy **không đủ** kết luận giao dịch đã commit, công việc bất đồng bộ chạy trên thread nào hoặc cache có thực sự bỏ qua target không.

Bài này tập trung vào **thiết kế kiểm thử tích hợp Spring AOP**: sử dụng context và proxy thật, quan sát những sự kiện có ý nghĩa, xác định rõ điều kiện đạt cho từng trường hợp cần dùng. Ba cơ chế được xét độc lập trước, sau đó mới thảo luận việc kết hợp.

| Tình huống | Điều cần chứng minh | Bằng chứng chính |
| --- | --- | --- |
| Custom aspect + `@Transactional` | Advice nằm bên trong hay bên ngoài transaction; exception gây rollback đúng | `TransactionSynchronizationManager`, dữ liệu trong DB sau khi gọi |
| Custom aspect + `@Async` | Aspect và target được gọi đúng số lần, target thực sự chạy trên executor | Trace có thread name, `CompletableFuture` đã hoàn thành |
| Custom aspect + `@Cacheable` | Advice có chạy trên cache hit không; target có bị bỏ qua khi hit không | Trace hai lần gọi cùng key và bộ đếm target |

**Nguyên tắc chung:** mỗi phép thử phải đi qua bean lấy từ Spring container, có expected trace trước khi chạy, đo trạng thái nghiệp vụ và không suy diễn thứ tự của advisor hạ tầng từ tên annotation.

## 2. Tại sao các cơ chế này ảnh hưởng đến chuỗi advice?

### Transaction

`@EnableTransactionManagement` tạo hạ tầng đọc `@Transactional` và áp dụng `TransactionInterceptor` khi phương thức được gọi qua proxy. Một custom aspect cũng match phương thức này có thể chạy **ngoài** transaction hoặc **bên trong** transaction tùy thứ tự advisor.

Với thứ tự explicit sau đây:

```java
@EnableTransactionManagement(order = 100)  // transaction advice đi trước
@Order(200)                                // custom aspect đi sau
```

Transaction interceptor có precedence cao hơn (số nhỏ hơn), nên khi custom `@Around` bắt đầu, transaction thông thường đã active:

```text
caller
  → TransactionInterceptor [begin]
      → CustomAspect.enter (transaction active)
          → target @Transactional (transaction active)
      ← CustomAspect.finally (transaction vẫn active)
  ← TransactionInterceptor [commit/rollback]
caller
```

Đây là thứ tự được **cấu hình cho fixture**. Nó không phải một thứ tự mặc định áp dụng cho mọi Spring application. Lưu ý `@Around` kết thúc **bên trong transaction interceptor** không chứng minh commit thành công; muốn kiểm tra commit/rollback phải đo trạng thái cuối ở kho dữ liệu hoặc quan sát transaction completion một cách phù hợp.

### Async

`@EnableAsync` và `@Async` có thể chuyển phần thực thi method sang `TaskExecutor`. Khi đó có ít nhất hai mốc thời gian: caller gọi proxy và worker xử lý công việc. Advice custom có thể nằm ngoài hoặc bên trong ranh giới async tùy hệ thống proxy/advisor, nên **không cố định thread của custom advice nếu chưa kiểm chứng**.

```text
caller → Spring proxy → @Async interceptor → executor
                                        worker → [các advice còn lại] → target
caller ← CompletableFuture                 worker hoàn thành
```

Có thể test chắc chắn rằng **target** chạy trên executor đã chỉ định và event của target xuất hiện trước khi future báo hoàn thành. Đừng khẳng định một log của advice trên caller thread đồng nghĩa target chạy trên caller thread.

### Cache

`@EnableCaching` và `@Cacheable` dùng cache advisor. Cache miss thường dẫn đến gọi target và lưu kết quả; cache hit có thể trả về giá trị mà **không thực thi thân phương thức**.

Nếu custom `@Around` được đặt **bên ngoài** cache advisor (ví dụ custom `@Order(50)`, cache advisor `order=100`), custom aspect vẫn chạy ở cả hit lẫn miss, nhưng target chỉ chạy ở miss:

```text
miss:  aspect.enter → target → aspect.exit    (lưu cache)
hit:   aspect.enter → aspect.exit             (dùng cache)
```

Nếu đổi thứ tự để aspect nằm bên trong cache advice, cache hit có thể bỏ qua cả target **và** custom aspect. Vì vậy phải kiểm thử theo chính sách thật của ứng dụng, không chỉ nhìn hai annotation.

## 3. Dựng project kiểm thử Spring AOP độc lập

Ví dụ sử dụng **Java 17+**, Spring Framework **6.2.8**, JUnit Jupiter **5.11.4**, Maven và H2 in-memory. Không cần Spring Boot để chứng minh cơ chế proxy Spring; Spring Boot có thể tự cấu hình nhiều thứ nên fixture thuần Spring giúp cô lập thứ tự.

Cấu trúc:

```text
spring-aspect-integration-tests/
├── pom.xml
└── src/test/java/lab/SpringAspectIntegrationTest.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lab</groupId>
    <artifactId>spring-aspect-integration-tests</artifactId>
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
            <groupId>org.springframework</groupId>
            <artifactId>spring-jdbc</artifactId>
            <version>${spring.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-tx</artifactId>
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
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <version>2.3.232</version>
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

Các dependency gồm: Spring context/AOP để tạo proxy, Spring JDBC/TX để kiểm tra transaction thật, H2 để quan sát rollback, Spring cache có sẵn trong `spring-context`, `aspectjweaver` để Spring xử lý pointcut `@AspectJ`, và JUnit Jupiter. Dù có thư viện AspectJ, fixture này **không bật compile-time hoặc load-time weaving**.

## 4. File test đầy đủ: ba Spring context tách biệt

Tạo đúng một file `src/test/java/lab/SpringAspectIntegrationTest.java`:

```java
package lab;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Test;

import org.springframework.aop.support.AopUtils;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;

class SpringAspectIntegrationTest {

    record Event(String name, String thread) {}

    static class Trace {
        private final List<Event> events = new CopyOnWriteArrayList<>();

        void add(String name) {
            events.add(new Event(name, Thread.currentThread().getName()));
        }

        List<String> names() {
            return events.stream().map(Event::name).toList();
        }

        String threadOf(String name) {
            return events.stream()
                .filter(event -> event.name().equals(name))
                .findFirst()
                .orElseThrow()
                .thread();
        }
    }

    // ------------------------------------------------------------
    // Test 1: Transaction interceptor nằm ngoài custom @Around.
    // ------------------------------------------------------------

    @Test
    void transaction_advice_observes_active_tx_and_real_rollback() {
        try (var ctx = new AnnotationConfigApplicationContext(TxConfig.class)) {
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            TxWork service = ctx.getBean(TxWork.class);
            Trace trace = ctx.getBean(Trace.class);

            jdbc.execute("create table audit(id integer primary key)");
            assertTrue(AopUtils.isAopProxy(service));

            IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.saveAndFail(1)
            );
            assertEquals("expected rollback", error.getMessage());

            assertEquals(List.of(
                "tx.aspect.active=true",
                "tx.target.active=true",
                "tx.aspect.finally.active=true"
            ), trace.names());

            // Query sau khi proxy đã trả exception: transaction đã rollback.
            Integer count = jdbc.queryForObject(
                "select count(*) from audit", Integer.class);
            assertEquals(0, count);
        }
    }

    public static class TxWork {
        private final JdbcTemplate jdbc;
        private final Trace trace;

        public TxWork(JdbcTemplate jdbc, Trace trace) {
            this.jdbc = jdbc;
            this.trace = trace;
        }

        @Transactional
        public void saveAndFail(int id) {
            trace.add("tx.target.active=" +
                TransactionSynchronizationManager.isActualTransactionActive());
            jdbc.update("insert into audit(id) values (?)", id);
            throw new IllegalStateException("expected rollback");
        }
    }

    @Aspect
    @Order(200)
    public static class TxAspect {
        private final Trace trace;

        public TxAspect(Trace trace) {
            this.trace = trace;
        }

        @Around("bean(txWork) && execution(* saveAndFail(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            trace.add("tx.aspect.active=" +
                TransactionSynchronizationManager.isActualTransactionActive());
            try {
                return pjp.proceed();
            } finally {
                trace.add("tx.aspect.finally.active=" +
                    TransactionSynchronizationManager.isActualTransactionActive());
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true, order = 100)
    public static class TxConfig {
        @Bean
        Trace trace() {
            return new Trace();
        }

        @Bean
        DataSource dataSource() {
            var ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:aop_" + UUID.randomUUID());
            ds.setUsername("sa");
            ds.setPassword("");
            return ds;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TxWork txWork(JdbcTemplate jdbc, Trace trace) {
            return new TxWork(jdbc, trace);
        }

        @Bean
        TxAspect txAspect(Trace trace) {
            return new TxAspect(trace);
        }
    }

    // ------------------------------------------------------------
    // Test 2: Async target chạy trên executor được chọn.
    // ------------------------------------------------------------

    @Test
    void async_advice_and_target_complete_once_on_expected_executor()
            throws Exception {
        try (var ctx = new AnnotationConfigApplicationContext(AsyncConfig.class)) {
            AsyncWork service = ctx.getBean(AsyncWork.class);
            Trace trace = ctx.getBean(Trace.class);

            assertTrue(AopUtils.isAopProxy(service));
            String callerThread = Thread.currentThread().getName();
            CompletableFuture<String> result = service.compute();

            // Future chính là ranh giới đồng bộ của test.
            assertEquals("DONE", result.get(5, TimeUnit.SECONDS));
            assertEquals(List.of("async.aspect.before", "async.target"),
                trace.names());
            assertTrue(trace.threadOf("async.target").startsWith("aop-worker-"));
            assertNotEquals(callerThread, trace.threadOf("async.target"));
        }
    }

    public static class AsyncWork {
        private final Trace trace;

        public AsyncWork(Trace trace) {
            this.trace = trace;
        }

        @Async("probeExecutor")
        public CompletableFuture<String> compute() {
            trace.add("async.target");
            return CompletableFuture.completedFuture("DONE");
        }
    }

    @Aspect
    public static class AsyncAspect {
        private final Trace trace;

        public AsyncAspect(Trace trace) {
            this.trace = trace;
        }

        @Before("bean(asyncWork) && execution(* compute(..))")
        public void before() {
            trace.add("async.aspect.before");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableAsync(proxyTargetClass = true)
    public static class AsyncConfig {
        @Bean
        Trace trace() {
            return new Trace();
        }

        @Bean(name = "probeExecutor")
        ThreadPoolTaskExecutor probeExecutor() {
            var executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(8);
            executor.setThreadNamePrefix("aop-worker-");
            return executor;
        }

        @Bean
        AsyncWork asyncWork(Trace trace) {
            return new AsyncWork(trace);
        }

        @Bean
        AsyncAspect asyncAspect(Trace trace) {
            return new AsyncAspect(trace);
        }
    }

    // ------------------------------------------------------------
    // Test 3: Aspect ngoài cache advisor; target chỉ chạy khi miss.
    // ------------------------------------------------------------

    @Test
    void cache_hit_skips_target_but_outer_aspect_still_runs() {
        try (var ctx = new AnnotationConfigApplicationContext(CacheConfig.class)) {
            CacheWork service = ctx.getBean(CacheWork.class);
            Trace trace = ctx.getBean(Trace.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals(1, service.load("A")); // miss
            assertEquals(1, service.load("A")); // hit
            assertEquals(2, service.load("B")); // miss với key khác

            assertEquals(2, service.invocations());
            assertEquals(List.of(
                "cache.aspect.enter",
                "cache.target",
                "cache.aspect.exit",
                "cache.aspect.enter",
                "cache.aspect.exit",
                "cache.aspect.enter",
                "cache.target",
                "cache.aspect.exit"
            ), trace.names());
        }
    }

    public static class CacheWork {
        private final Trace trace;
        private final AtomicInteger invocations = new AtomicInteger();

        public CacheWork(Trace trace) {
            this.trace = trace;
        }

        @Cacheable(cacheNames = "numbers")
        public int load(String key) {
            trace.add("cache.target");
            return invocations.incrementAndGet();
        }

        public int invocations() {
            return invocations.get();
        }
    }

    @Aspect
    @Order(50)
    public static class CacheAspect {
        private final Trace trace;

        public CacheAspect(Trace trace) {
            this.trace = trace;
        }

        @Around("bean(cacheWork) && execution(* load(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            trace.add("cache.aspect.enter");
            try {
                return pjp.proceed();
            } finally {
                trace.add("cache.aspect.exit");
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableCaching(order = 100)
    public static class CacheConfig {
        @Bean
        Trace trace() {
            return new Trace();
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("numbers");
        }

        @Bean
        CacheWork cacheWork(Trace trace) {
            return new CacheWork(trace);
        }

        @Bean
        CacheAspect cacheAspect(Trace trace) {
            return new CacheAspect(trace);
        }
    }
}
```

### Vì sao thiết kế này đo được Spring AOP thật?

- Mỗi test khởi tạo một `AnnotationConfigApplicationContext` và **lấy service từ container**. `AopUtils.isAopProxy(service)` là kiểm tra tiền điều kiện, tránh test nhầm object được tạo bằng `new`.
- `@EnableAspectJAutoProxy(proxyTargetClass = true)` và các `@Enable...` cụ thể tạo hạ tầng thực. Các service là public class với public, non-final methods phù hợp CGLIB proxy.
- Ba cấu hình có `Trace` và infrastructure riêng: không mang cache, dữ liệu transaction hay event từ test này sang test khác.
- `@Around` ghi hai phía của `proceed()` cho cache; transaction aspect ghi `TransactionSynchronizationManager` tại entry/finally; async aspect ghi `@Before` và target ghi thread thực tế.
- `CopyOnWriteArrayList` giúp recorder chịu được việc aspect và target ở các thread khác nhau trong test async. Test chờ `Future.get(timeout)` trước khi đọc event; không dùng `Thread.sleep(...)`.

Các class `TxConfig`, `AsyncConfig`, `CacheConfig` là ba fixture độc lập nằm trong cùng file để người học chạy dễ dàng. Ứng dụng thực tế không cần tách cấu hình kiểu này nếu các capability đã được bật từ trước.

## 5. Chạy và diễn giải kết quả

Trong thư mục chứa `pom.xml`:

```bash
mvn test
```

Kỳ vọng **3 bài test PASS** khi các advisor, executor và cache được thiết lập đúng. Maven Surefire có thể in tên test, số lượng chạy và log Spring; những dòng dưới đây là *vết logic từ assertion*, không phải toàn bộ stdout Maven.

### 5.1. Transaction: kiểm chứng đúng trạng thái và rollback

Vết mong đợi:

```text
tx.aspect.active=true
tx.target.active=true
tx.aspect.finally.active=true
```

Sau khi `saveAndFail(1)` ném `IllegalStateException`, `select count(*) from audit` phải trả `0`. Hai bằng chứng giải quyết hai câu hỏi khác nhau:

1. `true` trong custom aspect chứng minh nó đang chạy **bên trong một transaction active**.
2. Hàng DB bị rollback sau exception chứng minh **transaction interceptor thực hiện xử lý rollback**, thay vì chỉ ghi cờ active.

Đặt `@EnableTransactionManagement(order=100)` và `@Order(200)` là một giả thuyết cụ thể: transaction advice có precedence cao hơn custom advice. Nếu chuyển `TxAspect` thành `@Order(50)`, test về cờ `active=true` trong advice sẽ có lý do để thất bại: aspect nay bọc transaction interceptor. Dù vậy rollback vẫn có thể thành công. Đây là ví dụ vì sao **thứ tự advice** và **kết quả nghiệp vụ** cần được assert riêng.

**Giới hạn:** `TransactionSynchronizationManager.isActualTransactionActive()` phản ánh transaction được Spring đồng bộ với thread hiện tại. Nó không xác nhận DB commit thành công. Cần quan sát dữ liệu sau lời gọi, transaction synchronization callback hoặc công cụ kiểm tra transaction manager tương ứng nếu mục tiêu là kiểm thử thời điểm commit.

### 5.2. Async: không suy thứ tự từ log đa luồng

Trong ví dụ, trace tên event cần là:

```text
async.aspect.before
async.target
```

Thread của `async.target` phải có tiền tố `aop-worker-` và khác thread của JUnit caller. Test **không assert thread của `async.aspect.before`**: tùy vị trí advice so với async interceptor trong chain thực tế, nó có thể xuất hiện phía caller hoặc worker. Điều được cam kết trong phép thử là custom `@Before` và target đều được gọi một lần, và target nằm trên executor đã chỉ định.

Kết quả `result.get(5, TimeUnit.SECONDS) == "DONE"` cung cấp ranh giới hoàn thành trước khi kiểm tra trace. Tránh assert trace ngay sau `service.compute()` mà chưa chờ future: test có thể thất bại do scheduling, không liên quan Spring AOP.

Nếu ứng dụng dùng `@Async void`, không có `Future` để await và ngoại lệ từ worker không tự được ném lại cho caller. Khi đó thiết kế `CountDownLatch`/test listener phù hợp cùng `AsyncUncaughtExceptionHandler`. Với `CompletableFuture`, một exceptional completion có thể được kiểm tra bằng `assertThrows(ExecutionException.class, () -> future.get(...))` rồi kiểm tra `getCause()`.

### 5.3. Cache: phân biệt advice chạy với target chạy

Ba lời gọi và kết quả:

| Lời gọi | Trạng thái key | Kết quả | Số lượt chạy target tích lũy |
| --- | --- | --- | ---: |
| `load("A")` | Miss | `1` | 1 |
| `load("A")` | Hit | `1` | 1 |
| `load("B")` | Miss | `2` | 2 |

Vết mong đợi:

```text
cache.aspect.enter
cache.target
cache.aspect.exit
cache.aspect.enter
cache.aspect.exit
cache.aspect.enter
cache.target
cache.aspect.exit
```

Đây là một ứng dụng rõ ràng của **thứ tự advisor có chủ đích**: `CacheAspect @Order(50)` được ưu tiên trước cache advisor `@EnableCaching(order=100)`, nên custom aspect nằm **ngoài** cơ chế cache. Cache hit đi qua custom advice nhưng không gọi target. Việc `CacheWork.invocations() == 2` kiểm chứng có hai cache miss và một cache hit.

Nếu aspect dùng để đo **số lần gọi API**, vị trí ngoài cache phù hợp. Nếu aspect dùng để đo **số lần tính toán thực sự**, nên đo tại target hoặc cân nhắc đặt advice bên trong cache để không tính cache hit. Không được đặt metric name `business_executions` lên một aspect outer rồi coi mỗi hit là target execution.

## 6. Chọn loại integration test phù hợp

Không phải method nào cũng cần cả ba cơ chế. Dùng test ứng với điều kiện thật của ứng dụng:

| Tình huống thực tế | Kiểm thử nên có | Tránh kết luận sai |
| --- | --- | --- |
| Aspect audit chạy cùng `@Transactional` | Kiểm tra transaction active đúng điểm đo; DB rollback/commit; quan hệ order với transaction advisor | Aspect `@AfterReturning` chạy không mặc nhiên chứng minh DB commit xong |
| Aspect trace chạy cùng `@Async` | Await completion; event id/thread; kết quả/exception; gọi qua Spring bean | Caller return không nghĩa worker đã làm xong |
| Aspect logging chạy cùng `@Cacheable` | Hit/miss, số lượt target, số lượt advice, cache key, precedence | Aspect có chạy không đồng nghĩa target chạy |
| Service vừa `@Transactional` vừa `@Async` | Xác định thread nào mở transaction và nơi đọc DB; assert trạng thái trong worker | Transaction thread-bound của caller không mặc nhiên chuyển sang executor |
| `@Cacheable` gọi method `@Transactional` | Kiểm tra cache miss/hit và commit/rollback với đúng advisor order | Cache và transaction mặc nhiên có cùng phạm vi sống |
| Aspect áp dụng lên method tự gọi qua `this` | Gọi qua bean proxy và so với self-invocation | Annotation trên method tự đủ để kích hoạt proxy |
| Có retry | Đo số attempt và số event theo attempt, không chỉ theo request | Nhiều lần target chạy luôn là lỗi AOP |

**Chỉ dùng các tình huống thực sự có trong module.** Các mục ngoài phạm vi có thể dùng làm gợi ý kiểm thử khi dự án phát triển, không phải bắt buộc thêm transaction, cache và async vào tất cả service.

## 7. Phân tích những ranh giới thường gây hiểu nhầm

### 7.1. Custom advice ngoài transaction: event có thể được ghi trước commit

Giả sử advice outer log `success` ngay sau `proceed()`; `proceed()` có thể bao gồm toàn bộ transaction interceptor và lúc này commit đã kết thúc **nếu advisor transaction nằm trong advice outer**. Trái lại, nếu custom advice nằm **bên trong** transaction interceptor, nó có thể log `success` **trước commit**. Phép thử cần nói rõ đang kiểm chứng thứ tự nào, không dựa vào loại advice `@AfterReturning` một cách chung chung.

Với việc phát sự kiện chắc chắn sau commit, Spring hỗ trợ transaction synchronization hoặc `@TransactionalEventListener(phase = AFTER_COMMIT)`, với điều kiện event được publish trong transaction phù hợp. Test nên kiểm chứng không phát sự kiện sau rollback, thay vì chỉ kiểm tra hàm publish đã được gọi.

### 7.2. Async và transaction thuộc các thread khác nhau

Transaction state của Spring thông thường gắn với thread hiện hành. Chuyển thực thi sang executor không tự chuyển `TransactionSynchronizationManager` của caller. Nếu muốn transaction dành cho công việc bất đồng bộ, đặt transaction boundary trên phương thức worker hoặc một service khác được gọi qua Spring proxy trong worker. Sau đó test trạng thái transaction **bên trong worker**, không suy từ caller.

Một method tự gọi `this.save()` có `@Transactional` bên trong `@Async` target cũng có thể bỏ qua transaction proxy. Để test đúng, gọi method transactional qua Spring bean proxy độc lập hoặc dùng thiết kế phù hợp với transaction manager.

### 7.3. Cache hit có thể che mất exception ở target

Nếu `@Cacheable` trả giá trị đã lưu, thân method không chạy nên nhánh exception trong target cũng không thể phát sinh từ invocation đó. Nếu test chỉ gọi cùng key sau lần thành công, nó có thể bỏ lỡ luồng exception đang muốn kiểm chứng. Dùng key mới hoặc chủ động evict cache trước ca lỗi để buộc cache miss.

Không nên dựa vào implementation `ConcurrentMapCacheManager` của fixture để suy luận cache phân tán ngoài production. TTL, serialization, key generator, cache resolver, consistency và lỗi backend có thể làm thay đổi quan sát. Test unit/integration với cache thật tương ứng khi hành vi đó ảnh hưởng nghiệp vụ.

### 7.4. Advisor order và proxy stacking

`@Order` của custom aspect, `order` của `@EnableTransactionManagement`/`@EnableCaching`, cùng advisor async và các interceptor khác đều có thể ảnh hưởng đường gọi. Các bean có thể mang nhiều advisor trên một proxy hoặc qua nhiều proxy lồng nhau tùy cấu hình. Hãy quan sát proxy thật (`AopUtils.isAopProxy`) và, khi có thể, snapshot `Advised.getAdvisors()` như dữ liệu chẩn đoán.

Nhưng một `Advisor[]` chỉ mô tả cấu hình trên **proxy đó**; pointcut hoặc điều kiện runtime có thể khiến một advisor không chạy với lời gọi cụ thể. Trace và assertion ở target/advice mới cho biết điều gì đã xảy ra.

## 8. Mở rộng phép thử với failure và hành vi bất thường

### Transaction rollback vì checked exception

Với cấu hình Spring mặc định thông thường, unchecked exception (`RuntimeException`, `Error`) kích hoạt rollback; checked exception không tự động rollback nếu không có quy tắc bổ sung. Vì ví dụ dùng `IllegalStateException`, rollback là kỳ vọng đúng. Nếu nghiệp vụ dùng checked exception, thêm `rollbackFor` phù hợp và test outcome thực tế.

### Async exception và timeout

Để kiểm chứng exception từ `@Async` method trả `CompletableFuture`, định nghĩa method hoàn thành exceptional và assert `Future.get` ném `ExecutionException` với cause mong đợi. Dùng timeout có giới hạn và cleanup executor sau context close; đừng thay kết quả bằng `Thread.sleep`.

### Cache stampede và nhiều thread

Fixture một luồng chứng minh semantics của miss/hit với cùng key, **không chứng minh** chỉ một target execution xảy ra khi nhiều request đồng thời tranh cùng key. Nếu chính sách cần đảm bảo một lần tính toán dưới cạnh tranh, xem xét `@Cacheable(sync=true)` và khả năng hỗ trợ của cache provider, sau đó viết concurrency test riêng.

### Advice chỉ chạy khi đi qua proxy

Cả `@Transactional`, `@Async`, `@Cacheable` và custom Spring AOP advice ở **proxy mode** đều chịu giới hạn lời gọi nội bộ `this.otherMethod()`: chúng không đi qua proxy cho method được gọi nội bộ. Nếu kết quả test trái kỳ vọng, kiểm tra đường gọi trước khi sửa thứ tự advisor. AspectJ weaving là chế độ khác và phải kiểm thử riêng nếu thật sự được bật.

## 9. Các bước áp dụng vào codebase Spring Boot hiện có

1. Chọn **một method nghiệp vụ cụ thể**, ghi rõ annotation nào đang được áp dụng, custom aspect nào match, input và output/exception dự kiến.
2. Dựng test với **cùng Spring context cần kiểm chứng** (`@SpringBootTest` nếu cần auto-configuration thật, slice test khi đủ scope, hoặc `AnnotationConfigApplicationContext` như fixture này để cô lập).
3. Lấy bean từ container. Xác nhận `AopUtils.isAopProxy`; khi cần thì in `Advised.getAdvisors()`, advisor class, advice class, `@Order` và pointcut đang match.
4. Ghi trace bằng event name ổn định và, nếu async, thread/correlation id. Cố định `expected` riêng cho success, failure và case không match.
5. Transaction: kiểm tra trạng thái **ngay trong advice/target** và kết quả DB sau invocation. Async: chờ future rồi assert; cache: gọi miss/hit với key xác định, đếm cả target và advice.
6. Tạo ít nhất một **đối chứng làm test thất bại có chủ đích**: đổi precedence transaction/cache, bỏ proxy invocation, tắt cache, đổi key hoặc giả lập exception. Điều này chứng minh assertion bảo vệ đúng hành vi.
7. Khi test qua nhiều thread hoặc component, tránh khẳng định thứ tự toàn cục nếu không có quan hệ happens-before; tách assertion theo request và theo ranh giới hoàn thành.
8. Đưa test có ý nghĩa vào CI và theo dõi thay đổi `@Order` hoặc annotation proxy hạ tầng khi nâng Spring version. Không phụ thuộc số advisor nội bộ hoặc chi tiết stdout của framework.

### Bảng chẩn đoán nhanh

| Test sai | Khả năng cần kiểm tra trước tiên |
| --- | --- |
| `isAopProxy=false` | Service được tạo ngoài Spring, thiếu `@Enable...`, method không thể proxy, pointcut không match |
| `tx.aspect.active=false` nhưng target có transaction | Custom aspect đang **ngoài** transaction advisor; xem `order` |
| DB còn bản ghi sau runtime exception | Transaction chưa được áp dụng, transaction manager không đúng, exception bị xử lý, hoặc thao tác DB không nằm trong transaction |
| `async.target` chạy trên caller thread | `@Async` không được intercept, executor đồng bộ, self-invocation hoặc gọi trực tiếp target |
| Test async ngẫu nhiên FAIL | Assert trước khi future xong, trace không an toàn luồng hoặc context/executor dùng chung |
| Cache hit vẫn gọi target | Cache chưa bật, key thay đổi, self-invocation, cache bị evict, `condition/unless` loại trừ |
| Cache hit không ghi custom advice | Aspect nằm **trong** cache interceptor nên hit bỏ qua nó |
| Cả advice và target chạy hai lần | Hai invocation, retry, nhiều proxy hoặc duplicated advisor; dùng correlation id để phân biệt |

## 10. Tiêu chí hoàn thành

Một **Spring AOP integration test** được coi là đạt khi: (1) chạy qua Spring bean proxy thật; (2) ghi rõ loại concern và vị trí advisor cần kiểm tra; (3) có expected trace độc lập với log/timing; (4) assert kết quả nghiệp vụ gắn với concern: DB rollback/commit, async future/thread hoặc cache hit/miss; (5) chứng minh lỗi bị phát hiện khi đổi precedence, đường gọi hay trạng thái cache; và (6) không lẫn thứ tự advice với kết quả commit, thời điểm worker hoàn thành hoặc số lần target tính toán.

Cốt lõi của bài: **kiểm thử tích hợp với transaction, async, cache phải đo tại ranh giới do từng cơ chế sở hữu**, sau đó mới kết luận custom Spring aspect có đúng vị trí và ngữ nghĩa hay không. Không nhất thiết bật cả ba cơ chế trong mọi bài test; mỗi phép thử cần một nhu cầu nghiệp vụ rõ ràng.

### Tài liệu Spring chính thức

- [Understanding Spring's Declarative Transaction Implementation](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-decl-explained.html): `TransactionInterceptor` và Spring AOP proxy.
- [Using @Transactional](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html): transaction proxy mode, các tham số và thứ tự advisor.
- [Rolling Back a Declarative Transaction](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/rolling-back.html): rollback rules.
- [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): `@EnableAsync`, `@Async`, executor, `Future`.
- [Declarative Annotation-based Caching](https://docs.spring.io/spring-framework/reference/6.2/integration/cache/annotations.html): `@EnableCaching`, `@Cacheable`, proxy và `order`.
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): phạm vi proxy và self-invocation.


</details>

- [Quay lại đầu trang](#back-to-top)
