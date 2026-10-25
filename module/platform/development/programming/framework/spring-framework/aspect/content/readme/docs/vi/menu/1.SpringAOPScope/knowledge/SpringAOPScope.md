<a id="back-to-top"></a>

# Phạm vi Spring AOP

## Menu
- [Vai trò của Spring AOP đối với hành vi dùng chung trên các bean](#spring-aop-scope-001)
- [Ánh xạ aspect, advice và pointcut vào cơ chế của Spring](#spring-aop-scope-002)
- [Spring AOP can thiệp lời thực thi phương thức qua proxy](#spring-aop-scope-003)
- [Phạm vi áp dụng trên đối tượng được Spring quản lý](#spring-aop-scope-004)
- [Phân biệt @AspectJ trong Spring AOP và cơ chế dệt của AspectJ](#spring-aop-scope-005)
- [Điều kiện chọn Spring AOP cho một yêu cầu cụ thể](#spring-aop-scope-006)
- [Quan hệ với IoC, dependency injection và vòng đời bean](#spring-aop-scope-007)
- [Ví dụ xuyên suốt: can thiệp lời gọi service bean trong ApplicationContext](#spring-aop-scope-008)

## <a id="spring-aop-scope-001">Vai trò của Spring AOP đối với hành vi dùng chung trên các bean</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của Spring AOP đối với hành vi dùng chung trên các bean

## 1. Spring AOP giải quyết vấn đề gì trong ứng dụng Spring?

Trong ứng dụng Spring, nhiều bean nghiệp vụ có thể cần cùng một hành vi kỹ thuật: ghi nhật ký gọi phương thức, đo thời gian, kiểm tra chính sách truy cập, thu thập metric hoặc áp dụng một quy tắc vận hành xuyên suốt nhiều service. Nếu mỗi service tự lặp lại phần mã này, logic nghiệp vụ bị trộn với logic hạ tầng; mỗi thay đổi phải sửa ở nhiều nơi và dễ thiếu nhất quán.

**Spring AOP (Aspect-Oriented Programming)** cho phép đặt hành vi dùng chung ở một **aspect được Spring quản lý**, rồi áp dụng nó lên những lần thực thi phương thức bean phù hợp. Service tập trung vào việc nghiệp vụ của mình; aspect giữ trách nhiệm chung và Spring nối hai phần thông qua **proxy**.

Ví dụ: `OrderService.placeOrder()` và `InvoiceService.issueInvoice()` đều cần ghi dấu trước khi xử lý. Spring AOP cho phép viết một advice ghi dấu dùng chung, thay vì chép lệnh log vào cả hai service.

Phạm vi bài này là **cơ chế và cách dùng Spring AOP trên Spring beans**. Các lý thuyết AOP tổng quát hoặc AspectJ weaving độc lập với Spring nằm ngoài trọng tâm.

## 2. Spring ghép hành vi chung vào bean như thế nào?

Trong cấu hình Spring AOP dựa trên proxy, một lời gọi phù hợp có đường đi khái quát:

```text
Bean gọi (controller, service khác, command runner...)
        |
        v
Spring AOP proxy của bean đích
        |
        +--> Xét pointcut: lời gọi phương thức này có khớp không?
                 |
                 +--> Có: chạy advice tương ứng
                 |           |
                 |           v
                 |       Phương thức của bean đích
                 |           |
                 |           v
                 |       Advice sau lời gọi (nếu có)
                 |
                 +--> Không: chuyển thẳng đến bean đích
        |
        v
Trả kết quả hoặc ném ngoại lệ về bên gọi
```

Những thành phần cần nhận diện khi đọc code Spring AOP:

| Thành phần Spring AOP | Vai trò cụ thể trong ứng dụng |
| --- | --- |
| **Target bean** | Bean chứa phương thức nghiệp vụ, ví dụ `OrderService`. |
| **Proxy** | Đối tượng đại diện do Spring tạo, chặn lời gọi đến phương thức phù hợp và điều phối advice. |
| **Aspect** | Bean mang `@Aspect`, tổ chức các quy tắc can thiệp dùng chung. |
| **Pointcut** | Biểu thức chọn phương thức/lần thực thi sẽ được áp dụng, ví dụ các phương thức public của lớp `*Service`. |
| **Advice** | Mã chạy trước, sau hoặc bao quanh phương thức đích. |
| **Join point** | Trong **Spring AOP**, join point được hỗ trợ là **lần thực thi phương thức** của Spring bean. |

Hệ quả quan trọng: Spring AOP thường **không sửa trực tiếp mã nguồn hay bytecode của class nghiệp vụ**. Tương tác phải đi qua proxy thì advice mới có cơ hội chạy; đây là cơ sở để hiểu giới hạn trong phần 6.

## 3. Ví dụ chạy được: một aspect cho hai service bean

Ví dụ dùng **Java 17+, Spring Framework 6.1.14 và Maven**. Ứng dụng khởi động Spring container nhỏ bằng `AnnotationConfigApplicationContext`, không cần Spring Boot hoặc máy chủ web.

Cấu trúc:

```text
spring-aop-beans/
├── pom.xml
└── src/main/java/com/example/demo/
    ├── DemoApp.java
    ├── OrderService.java
    ├── InvoiceService.java
    └── SharedAuditAspect.java
```

**`pom.xml`** — `spring-context` cung cấp container và tích hợp Spring AOP; `aspectjweaver` cung cấp các kiểu và bộ phân tích biểu thức AspectJ mà Spring AOP sử dụng cho cú pháp `@Aspect`.

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
           https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-aop-beans</artifactId>
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

**`src/main/java/com/example/demo/OrderService.java`**

```java
package com.example.demo;

public class OrderService {
    public void placeOrder(String orderId) {
        System.out.println("[ORDER] created " + orderId);
    }
}
```

**`src/main/java/com/example/demo/InvoiceService.java`**

```java
package com.example.demo;

public class InvoiceService {
    public void issueInvoice(String orderId) {
        System.out.println("[INVOICE] created for " + orderId);
    }
}
```

**`src/main/java/com/example/demo/SharedAuditAspect.java`**

```java
package com.example.demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class SharedAuditAspect {
    @Before("execution(public * com.example.demo.*Service.*(..))")
    public void recordCall(JoinPoint joinPoint) {
        System.out.println("[AUDIT] " + joinPoint.getSignature().getName());
    }
}
```

Biểu thức `execution(public * com.example.demo.*Service.*(..))` chọn các lần thực thi phương thức `public` có kiểu trả về bất kỳ (`*`), thuộc các class có tên kết thúc bằng `Service` trong đúng package `com.example.demo`; tên phương thức và danh sách tham số đều có thể khác nhau. Đây là **quy tắc dùng chung cho nhiều bean**, không gắn cứng vào một phương thức. Trong ứng dụng thực, nên giới hạn pointcut sát trách nhiệm cần áp dụng để tránh chặn quá nhiều lời gọi.

**`src/main/java/com/example/demo/DemoApp.java`**

```java
package com.example.demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class DemoApp {
    @Bean
    public OrderService orderService() {
        return new OrderService();
    }

    @Bean
    public InvoiceService invoiceService() {
        return new InvoiceService();
    }

    @Bean
    public SharedAuditAspect sharedAuditAspect() {
        return new SharedAuditAspect();
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(DemoApp.class)) {
            OrderService orderService = context.getBean(OrderService.class);
            InvoiceService invoiceService = context.getBean(InvoiceService.class);

            orderService.placeOrder("O-42");
            invoiceService.issueInvoice("O-42");
        }
    }
}
```

Chạy tại thư mục chứa `pom.xml` (cần JDK 17+ và Maven):

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.demo.DemoApp
```

Các dòng do chương trình in ra, theo đúng thứ tự:

```text
[AUDIT] placeOrder
[ORDER] created O-42
[AUDIT] issueInvoice
[INVOICE] created for O-42
```

**Giải thích kết quả:** hai service hoàn toàn không gọi `SharedAuditAspect`. Container đăng ký ba bean, `@EnableAspectJAutoProxy` kích hoạt hạ tầng tự tạo proxy, Spring phát hiện bean `@Aspect` và lựa chọn advice cho từng phương thức phù hợp. `context.getBean(...)` trả về tham chiếu bean đã được Spring xử lý; lời gọi từ ngoài đi qua proxy nên có dòng `[AUDIT]` trước mỗi dòng nghiệp vụ. Việc dùng `proxyTargetClass = true` chọn proxy dựa trên lớp cho ví dụ có service không khai báo interface.

Nếu bỏ đăng ký aspect hoặc vô hiệu hóa hạ tầng AOP, hai phương thức nghiệp vụ vẫn có thể thực thi, nhưng các dòng `[AUDIT]` do advice tạo sẽ không xuất hiện.

## 4. Các kiểu advice phục vụ hành vi dùng chung

| Advice | Thời điểm và ứng dụng phù hợp |
| --- | --- |
| `@Before` | Trước khi phương thức đích chạy; ghi dấu lời gọi, kiểm tra tiền điều kiện đơn giản. |
| `@AfterReturning` | Khi phương thức trả về bình thường; theo dõi thành công hoặc kết quả. |
| `@AfterThrowing` | Khi phương thức ném ngoại lệ; ghi nhận lỗi theo chính sách. |
| `@After` | Khi lần thực thi kết thúc dù thành công hay lỗi; dọn dẹp/ghi dấu kết thúc, tương tự tinh thần `finally`. |
| `@Around` | Bao quanh lời gọi, có thể quyết định khi nào gọi `proceed()`, đo thời gian, xử lý kết quả/ngoại lệ. |

Ví dụ minh họa `@Around` khi thật sự cần điều khiển cả trước và sau:

```java
@Around("execution(public * com.example.demo.*Service.*(..))")
public Object timeCall(org.aspectj.lang.ProceedingJoinPoint pjp)
        throws Throwable {
    long started = System.nanoTime();
    try {
        return pjp.proceed(); // Phải gọi để nghiệp vụ thực sự chạy.
    } finally {
        long elapsedNanos = System.nanoTime() - started;
        // Gửi elapsedNanos tới hệ thống metric phù hợp.
    }
}
```

Đây là **đoạn phương thức minh họa**, đặt bên trong một bean `@Aspect` cùng với import `org.aspectj.lang.annotation.Around`; nó không phải chương trình độc lập và **không được thêm nguyên xi cạnh advice `@Before` của ví dụ mà mong stdout giữ nguyên**. `try/finally` đảm bảo khâu đo kết thúc được thực hiện cả khi phương thức ném lỗi; `proceed()` truyền tiếp thực thi xuống bean đích. Nếu cần advice đơn giản, ưu tiên `@Before`/`@AfterReturning` thay vì `@Around` để bớt kiểm soát luồng không cần thiết.

Trong cùng một lời gọi, thứ tự khi có nhiều aspect/advice còn tùy loại advice, mức ưu tiên (`@Order`/`Ordered`) và cấu hình. Khi thứ tự có ý nghĩa nghiệp vụ, phải xác định thứ tự tường minh và kiểm thử; không suy ra thứ tự chỉ từ thứ tự khai báo các phương thức.

## 5. Giá trị thực tế và ranh giới trách nhiệm

Spring AOP mang lại giá trị khi **một quy tắc áp dụng nhất quán cho nhiều bean hoặc nhiều phương thức**:

- **Quan sát vận hành:** ghi tên thao tác, thời gian thực thi, số lần gọi, tình trạng thành công/thất bại; dùng metadata phù hợp thay vì in mọi tham số nhạy cảm.
- **Quy tắc kỹ thuật tại ranh giới service:** kiểm tra một điều kiện chung, gắn metadata hoặc chuẩn hóa cách theo dõi lời gọi.
- **Tích hợp chính sách do Spring cung cấp:** các tính năng như giao dịch khai báo `@Transactional` có thể dùng interception/proxy tương tự; cần ưu tiên dùng cơ chế Spring chính thức thay vì tự viết aspect thay thế transaction manager, security interceptor hay cache abstraction.

Service phải vẫn thể hiện rõ tác vụ của nó: xác thực dữ liệu nghiệp vụ, tính toán, cập nhật trạng thái và quản lý invariant thuộc domain. Nếu logic chỉ áp dụng cho một use case, viết trực tiếp trong service hoặc component được gọi tường minh thường dễ hiểu hơn. AOP cũng không biến thao tác bất kỳ thành giao dịch, đồng bộ thread hay bảo đảm phân tán; advice chỉ cung cấp một điểm can thiệp vào phương thức đúng phạm vi.

Đặt pointcut quá rộng, ví dụ bắt mọi phương thức của toàn ứng dụng, có thể tạo chi phí không cần thiết, phụ thuộc ẩn và log ồn. Nên chọn package, interface, annotation hoặc tên phương thức dựa trên trách nhiệm thật sự cần chia sẻ, kèm kiểm thử cho các bean được chọn và không được chọn.

## 6. Những giới hạn cần nhận diện khi dùng chung advice

Spring AOP áp dụng advice tại **lời gọi phương thức đi qua proxy của Spring bean**, không thay đổi mọi đường thực thi Java. Trong ví dụ trên, hai lời gọi lấy từ `ApplicationContext` nhận được hành vi audit; bản thân hai lớp service không gọi aspect.

- **Gọi nội bộ:** nếu `ReportService.export()` gọi `this.validate()`, advice chỉ dành cho `validate()` sẽ không nhận được lời gọi nội bộ này. Muốn chặn `validate()` từ service khác, hãy gọi qua một bean được inject.
- **Đối tượng ngoài container:** `new OrderService().placeOrder("O-99")` chỉ chạy logic của đối tượng tự tạo, không tự nhận Spring AOP advice; ngược lại instance được **đăng ký bằng `@Bean`** vẫn có thể được proxy dù factory method dùng `new`.
- **Khả năng proxy:** JDK proxy công bố phương thức qua interface; CGLIB dùng subclass và không override được `final`/`private`. Constructor, field access và static method không thuộc các method-execution join point Spring AOP hỗ trợ.
- **Bất đồng bộ và lỗi:** `@AfterReturning` của method trả về `CompletableFuture` không chứng minh future đã hoàn tất thành công. Advice quan sát nên giữ nguyên giá trị trả về và exception, đồng thời dùng cơ chế phù hợp để đo thời điểm completion thật.

Các ranh giới trên giúp quyết định có nên tách hành vi sang bean khác hoặc dùng cơ chế Spring chuyên dụng. Phần cơ chế proxy, điều kiện quản lý bean và so sánh với AspectJ weaving được phân tích riêng ở các bài tương ứng.

## 7. Cách thiết kế và kiểm thử hành vi dùng chung

Một quy trình áp dụng Spring AOP an toàn:

1. **Xác định hành vi lặp thật sự:** ví dụ ghi audit ở các entry point service, phân biệt rõ dữ liệu nghiệp vụ với metadata kỹ thuật.
2. **Khoanh điểm áp dụng:** chọn đúng bean và method qua pointcut; nếu cần lựa chọn tường minh, tạo annotation riêng cho nhóm phương thức cần theo dõi.
3. **Chọn advice tối giản:** `@Before` cho dấu bắt đầu, `@AfterReturning`/`@AfterThrowing` cho kết quả, `@Around` khi phải quản lý toàn bộ vòng thực thi.
4. **Đảm bảo đường gọi qua container:** inject các service, gọi proxy từ bean khác, tránh `new` hoặc self-invocation đối với phương thức cần interception.
5. **Kiểm thử cả chiều có và không áp dụng:** gọi hai service khớp pointcut và một bean/method không khớp; kiểm tra số lần advice, thứ tự liên quan và nghiệp vụ trả đúng kết quả. Có thêm trường hợp lỗi để xác nhận advice không đổi ngoại lệ ngoài ý muốn.
6. **Quan sát khi vận hành:** tránh log thông tin nhạy cảm và dữ liệu có kích thước lớn; đo overhead nếu advice chạy trên đường nóng; ghi nhận khi một refactor đổi class/method khiến pointcut không còn khớp.

Ví dụ này cho thấy một thay đổi ở `SharedAuditAspect.recordCall()` có thể cập nhật hành vi audit của **cả `OrderService` và `InvoiceService`** mà không sửa code nghiệp vụ. Đó là vai trò cốt lõi của Spring AOP đối với hành vi dùng chung trên các bean: **tập trung quy tắc kỹ thuật, áp dụng nhất quán ở các phương thức Spring quản lý và giữ ranh giới nghiệp vụ rõ ràng**, đồng thời luôn tôn trọng giới hạn của proxy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-scope-002">Ánh xạ aspect, advice và pointcut vào cơ chế của Spring</a>

<details>
<summary>Xem chi tiết</summary>

# Ánh xạ aspect, advice và pointcut vào cơ chế của Spring AOP

## 1. Mục tiêu và phạm vi

Bài này giải thích **Spring Framework thực thi một aspect khai báo bằng `@Aspect` như thế nào**: Spring nhận diện aspect bean, chuyển biểu thức pointcut và phương thức advice thành cấu hình AOP nội bộ, chọn những Spring bean cần proxy và gọi advice khi phương thức phù hợp được gọi qua proxy. Trọng tâm là **Spring AOP dựa trên proxy**, không đi sâu vào lý thuyết AOP tổng quát hay cơ chế weaving độc lập của AspectJ.

Sau bài học, bạn có thể:
- Chỉ ra vai trò cụ thể của `@Aspect`, `@Pointcut`, các annotation advice, `Advisor`, `Pointcut` và `MethodInterceptor` trong Spring.
- Giải thích đường đi của một lời gọi phương thức từ client đến Spring proxy, advice chain và target object.
- Viết, chạy và kiểm tra một aspect dùng Spring Boot cùng `spring-boot-starter-aop`.
- Chẩn đoán những trường hợp pointcut trông đúng nhưng advice không chạy vì lời gọi không đi qua Spring proxy.

## 2. Một khai báo `@Aspect` được Spring diễn giải thành gì?

Giả sử ứng dụng có Spring bean `OrderService` và một bean `TraceAspect`. Khi lập trình, ta viết ba thành phần nhìn thấy được:

| Thành phần khai báo | Vai trò khi dùng Spring | Cơ chế Spring tương ứng |
| --- | --- | --- |
| `@Aspect` trên `TraceAspect` | Đánh dấu lớp chứa các quy tắc advice | Spring nhận diện một **aspect bean** và phân tích các advice method của nó. Chỉ đánh dấu `@Aspect` chưa tự đăng ký lớp thành bean. |
| `@Pointcut("...")` | Đặt tên cho điều kiện chọn phương thức | Biểu thức được phân tích và áp dụng bằng `AspectJExpressionPointcut`; phương thức pointcut `void` chỉ định nghĩa mẫu khớp, không chứa xử lý nghiệp vụ. |
| `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After`, `@Around` | Khai báo logic bổ sung ở các thời điểm khác nhau | Spring tạo các đối tượng advice tương ứng; trong lời gọi qua proxy, chúng tham gia chuỗi interceptor. |
| `Advisor` | Ghép **điều kiện khớp** với **hành vi chạy** | Proxy có thể chứa nhiều advisor; mỗi advisor quyết định có áp dụng advice của nó cho lời gọi cụ thể hay không. |
| `Pointcut` (API `org.springframework.aop.Pointcut`) | Kiểm tra lớp và phương thức mục tiêu | Gồm `ClassFilter` và `MethodMatcher`; một số điều kiện có thể cần xét thêm argument ở thời điểm gọi. |
| Spring AOP proxy | Đối tượng được Spring đưa cho bên gọi thay cho target bean trực tiếp | Chặn lời gọi phương thức, chọn interceptor phù hợp, chuyển tiếp đến target. |
| Target object | Bean thực hiện logic nghiệp vụ gốc | Chạy khi chuỗi interceptor tiếp tục đến phương thức đích. |

**Phân biệt dễ nhầm:** `@Aspect` là mô hình khai báo của lập trình viên; `Advisor` và `Advice` là những thành phần Spring sử dụng để dựng và vận hành proxy. Một aspect có nhiều advice có thể đóng góp nhiều advisor. `@Pointcut` tự nó không chạy code khi có lời gọi, và không có lời gọi nào tự sinh ra chỉ vì bạn khai báo `@Pointcut`.

Một bean khác cũng có thể nhận advice từ hạ tầng Spring thông qua advisor/interceptor mà không cần có lớp `@Aspect` do bạn tự viết. Ví dụ, hạ tầng giao dịch khai báo qua `@Transactional` có cơ chế advisor/interceptor riêng. Vì vậy, không nên đồng nhất mọi proxy trong Spring với một lớp `@Aspect` cụ thể.

## 3. Spring kích hoạt và tạo proxy như thế nào?

### 3.1. Điều kiện đăng ký

Với Spring Boot có thư viện AspectJ cần thiết trên classpath (thường nhờ `spring-boot-starter-aop`), Boot tự cấu hình hỗ trợ proxy cho `@Aspect`; ví dụ Boot bên dưới **không cần** thêm `@EnableAspectJAutoProxy`. Với ứng dụng Spring Framework thuần, có thể chủ động bật bằng `@EnableAspectJAutoProxy` trên lớp cấu hình và đăng ký aspect dưới dạng Spring bean (thí dụ `@Component` hoặc `@Bean`).

`@Aspect` **không phải** stereotype tự quét bean: lớp aspect cần được đăng ký trong `ApplicationContext`. Dependency chứa annotation mà thiếu cơ chế auto proxy cũng không đủ để advice chạy.

### 3.2. Từ bean definition đến lời gọi thực tế

```text
ApplicationContext tạo hạ tầng AOP
    |
    +-- nhận diện các aspect bean và các advisor hiện có
    |       |
    |       +-- phân tích @Pointcut / biểu thức match
    |       +-- chuyển phương thức @Before/@Around/... thành advice
    |
    +-- trong quá trình tạo bean, auto-proxy creator xem xét advisor phù hợp
            |
            +-- không cần advice --> dùng bean thông thường
            |
            +-- có advisor phù hợp --> tạo Spring proxy cho bean
                                        |
Client lấy bean từ container --> gọi phương thức qua proxy
                                        |
                              chọn các interceptor khớp
                                        |
                               chạy advice theo chain
                                        |
                               gọi phương thức target
                                        |
                            trả kết quả hoặc ném exception
```

Spring sử dụng cơ chế `BeanPostProcessor`, điển hình là hạ tầng auto-proxy creator như `AnnotationAwareAspectJAutoProxyCreator`, để xét bean và áp dụng các advisor phù hợp. Khi gọi một phương thức qua proxy, Spring chọn interceptor chain cho phương thức đó; chuỗi chạy qua advice rồi tới target theo semantics của từng advice. Việc tạo proxy **không đồng nghĩa** mọi phương thức trong bean đều được advice: pointcut và giới hạn proxy vẫn quyết định lời gọi được chặn hay không.

### 3.3. Hai dạng proxy cần nhận biết

- **JDK dynamic proxy** triển khai các interface được proxy. Lời gọi của client đi qua giao diện công khai do proxy cung cấp; nếu tiêm bean theo lớp triển khai thay vì interface, có thể không phù hợp với kiểu proxy này.
- **Proxy dựa trên lớp (CGLIB)** tạo lớp con để chặn các lời gọi có thể override. Nó không thể override phương thức `final`; lớp `final` cũng không thể được subclass để proxy theo cách này. Phương thức `private` không thể được advise bằng dạng proxy này.

Với **Spring Boot 3.3**, auto-configuration mặc định ưu tiên proxy theo lớp; đặt `spring.aop.proxy-target-class=false` để dùng JDK proxy khi bean có interface phù hợp. Với cấu hình Spring Framework trực tiếp, thuộc tính `proxyTargetClass` trong `@EnableAspectJAutoProxy` quyết định ưu tiên tương ứng. Đừng suy ra loại proxy chỉ từ việc có `@Aspect`: hãy kiểm tra proxy thực tế.

## 4. Điểm khớp trong Spring AOP: từ pointcut expression đến method execution

Spring AOP theo cơ chế proxy hỗ trợ **method-execution join point trên Spring bean**. Ví dụ:

```java
@Pointcut("execution(public * com.example.app.service..*.*(..))")
public void publicServiceMethod() { }
```

- `execution(...)`: lọc lời gọi thực thi phương thức; ở đây là phương thức `public`, mọi kiểu trả về, trong package `com.example.app.service` và package con.
- `publicServiceMethod()`: tên pointcut có thể tái sử dụng trong các advice cùng aspect; method Java khai báo là `void` và thân rỗng.
- Biểu thức chỉ mô tả **điều kiện chọn**. Không có lệnh trong method `publicServiceMethod()` được chạy khi một service method khớp.

Các designator hữu ích trong Spring:

| Biểu thức | Ý nghĩa trong Spring AOP |
| --- | --- |
| `execution(* com.example.app.service..*.*(..))` | Phương thức thuộc phạm vi chữ ký/lớp đã chỉ ra. |
| `within(com.example.app.service..*)` | Giới hạn target method thuộc kiểu trong package/phân cấp package đó. |
| `@annotation(com.example.app.TrackAction)` | Phương thức thực thi có annotation được chỉ ra. |
| `bean(*Service)` | Chọn bean theo **tên bean Spring** khớp mẫu; đây là mở rộng riêng của Spring AOP. |
| `this(com.example.app.OrderApi)` | Kiểm tra **Spring proxy** có kiểu được chỉ ra. |
| `target(com.example.app.OrderService)` | Kiểm tra **target object** phía sau proxy có kiểu được chỉ ra. |
| `args(java.lang.String, ..)` | Xét kiểu đối số thực tế khi gọi phương thức. |

Kết hợp điều kiện bằng `&&`, `||` và `!`. Ví dụ, chỉ áp dụng cho phương thức có annotation và nằm trong service layer:

```java
@Pointcut("within(com.example.app.service..*)"
        + " && @annotation(com.example.app.TrackAction)")
public void annotatedServiceOperation() { }
```

Có thể trỏ advice đến tên pointcut: `@Around("annotatedServiceOperation()")`. Nếu dùng pointcut khai báo ở lớp khác, dùng tên đầy đủ đến pointcut method theo quy tắc Spring.

**Giới hạn quan trọng:** biểu thức `call(...)`, `get(...)`, `set(...)` và nhiều loại join point khác của AspectJ không được Spring AOP theo proxy hỗ trợ. Nhìn thấy cú pháp AspectJ trong annotation không có nghĩa Spring đã bật AspectJ compiler hoặc weaving. Trong Spring, `this` là proxy, còn `target` là đối tượng đích nên hai giá trị có thể khác nhau.

## 5. Advice khớp vào chuỗi gọi ra sao?

| Advice annotation | Khi nào logic chạy khi lời gọi được proxy chặn | Khả năng/giới hạn thực tế |
| --- | --- | --- |
| `@Before` | Trước phương thức đích | Thường để kiểm tra/log; không trực tiếp thay kết quả trả về; ném exception sẽ ngăn lời gọi tiếp tục. |
| `@AfterReturning` | Khi phương thức hoàn tất bình thường | Có thể lấy kết quả qua `returning`, nhưng không thay thế kết quả chỉ bằng cách gán lại biến tham số advice. |
| `@AfterThrowing` | Khi phương thức thoát bằng exception | Có thể lấy exception qua `throwing`; nên giới hạn kiểu exception phù hợp để bắt đúng trường hợp. |
| `@After` | Sau khi phương thức kết thúc, dù bình thường hay exception | Gần với hành vi `finally`, không đồng nghĩa luôn thành công. |
| `@Around` | Bao quanh phần còn lại của invocation chain và target | Nhận `ProceedingJoinPoint`; gọi `proceed()` để tiếp tục, có thể đo thời gian, trả kết quả hoặc xử lý lỗi có chủ đích. |

Ví dụ về chữ ký nhận thông tin Spring cung cấp (đoạn minh họa độc lập, không ghép vào ví dụ chạy ở mục sau):

```java
@Before("execution(* com.example.app.service..*.*(..))")
public void beforeCall(org.aspectj.lang.JoinPoint joinPoint) {
    System.out.println(joinPoint.getSignature().getName());
}

@AfterReturning(
    pointcut = "execution(* com.example.app.service..*.*(..))",
    returning = "result"
)
public void afterSuccess(Object result) {
    System.out.println("result=" + result);
}

@AfterThrowing(
    pointcut = "execution(* com.example.app.service..*.*(..))",
    throwing = "error"
)
public void afterFailure(Throwable error) {
    System.out.println("error=" + error.getClass().getSimpleName());
}
```

Trong `@Around`, lời gọi `proceed()` là ranh giới đi vào phần còn lại của chuỗi và cuối cùng là target; tránh quên gọi khi cần nghiệp vụ chạy. `@Around` có thể quyết định không gọi `proceed()`, gọi nhiều lần, hoặc đổi giá trị trả về, nhưng chỉ nên làm vậy khi đó là chủ đích và bảo toàn hợp đồng method. Nếu advice chỉ cần quan sát trước/sau thì chọn loại advice đơn giản hơn.

Khi có **nhiều aspect**, thứ tự advice chịu ảnh hưởng của advisor ordering, loại advice và `@Order`/`Ordered`. Không nên viết ví dụ phụ thuộc một thứ tự không được cấu hình; nếu có thứ tự bắt buộc, chỉ định và kiểm thử nó.

## 6. Ví dụ chạy được: `@Aspect` + `@Pointcut` + `@Around` trên Spring Boot

Ví dụ dùng **Java 21, Spring Boot 3.3.x và Gradle**. Ý đồ là theo dõi các method có `@TrackAction` trong Spring bean. Tất cả lớp Java nằm trong package `com.example.demo` để được component scan phát hiện.

### 6.1. Cấu hình Gradle

Tệp `settings.gradle`:

```groovy
rootProject.name = 'spring-aop-mapping'
```

Tệp `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
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
    implementation 'org.springframework.boot:spring-boot-starter-aop'
}
```

`spring-boot-starter-aop` cung cấp hạ tầng Spring AOP và thư viện AspectJ cần để Spring diễn giải các annotation và pointcut expression. Mã nguồn ví dụ sau sử dụng Spring proxy, không cấu hình AspectJ compile-time weaving/load-time weaving.

Tệp `src/main/resources/application.properties`:

```properties
spring.main.web-application-type=none
spring.main.banner-mode=off
logging.level.root=OFF
```

### 6.2. Annotation đánh dấu phương thức muốn theo dõi

Tệp `src/main/java/com/example/demo/TrackAction.java`:

```java
package com.example.demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackAction {
}
```

### 6.3. Target Spring bean có phương thức được chọn

Tệp `src/main/java/com/example/demo/OrderService.java`:

```java
package com.example.demo;

import org.springframework.stereotype.Service;

@Service
public class OrderService {
    @TrackAction
    public String place(String productId) {
        return "stored:" + productId;
    }

    public String status() {
        return "ok";
    }
}
```

`place(...)` có `@TrackAction`; `status()` không có annotation nên không khớp pointcut trong ví dụ. Cả hai đều là method của cùng một Spring bean.

### 6.4. Aspect khai báo pointcut và advice

Tệp `src/main/java/com/example/demo/TraceAspect.java`:

```java
package com.example.demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TraceAspect {
    @Pointcut("@annotation(com.example.demo.TrackAction)")
    public void trackedAction() {
    }

    @Around("trackedAction()")
    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().getName();
        System.out.println("enter " + method);
        try {
            Object result = joinPoint.proceed();
            System.out.println("exit " + method);
            return result;
        } catch (Throwable error) {
            System.out.println("failed " + method);
            throw error;
        }
    }
}
```

Ánh xạ từ code sang Spring:
1. `@Component` đăng ký `TraceAspect` thành bean; `@Aspect` nói với hạ tầng AOP đây là lớp chứa advice.
2. `@Pointcut` đặt tên `trackedAction` cho mẫu các method có `@TrackAction`.
3. `@Around("trackedAction()")` gắn phương thức `trace` với pointcut đó. Spring cung cấp `ProceedingJoinPoint` của lời gọi được chặn.
4. Auto-proxy creator xét `OrderService` và có thể tạo proxy áp dụng advisor tương ứng; gọi `place(...)` **qua proxy** kích hoạt `trace`.
5. `joinPoint.proceed()` thực thi phần còn lại của chain/target; `trace` trả nguyên kết quả hoặc ném lại exception.

### 6.5. Chạy và quan sát proxy

Tệp `src/main/java/com/example/demo/DemoApplication.java`:

```java
package com.example.demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                     SpringApplication.run(DemoApplication.class, args)) {
            OrderService service = context.getBean(OrderService.class);
            System.out.println(service.place("P-100"));
            System.out.println("isProxy=" + AopUtils.isAopProxy(service));
            System.out.println(service.status());
        }
    }
}
```

Tại thư mục dự án có Gradle đã cài hoặc Gradle Wrapper, chạy tương ứng `gradle bootRun` hoặc `./gradlew bootRun`. Với cấu hình logging bên trên, các dòng do chương trình in ra là:

```text
enter place
exit place
stored:P-100
isProxy=true
ok
```

`enter place` / `exit place` xuất hiện quanh lời gọi được đánh dấu. `status()` vẫn chạy, nhưng **không** sinh hai dòng advice vì không khớp `@annotation(TrackAction)`. `isProxy=true` chứng tỏ bean nhận từ container là Spring AOP proxy. Đoạn mã không phụ thuộc tên lớp proxy vốn có thể khác nhau giữa các cấu hình và phiên bản.

**Một lỗi dễ mắc:** gọi `new OrderService().place("P-100")` sẽ không chạy advice, vì đối tượng được tạo bằng `new` không phải proxy do Spring cấp. Điểm chặn là lời gọi **đi qua Spring proxy**, không phải bản thân annotation tự thực thi logic.

## 7. Kiểm tra ánh xạ `@Aspect` → advisor → proxy

Khi advice không chạy, nên tìm **mắt xích ánh xạ còn thiếu** trước khi sửa biểu thức pointcut. Kiểm tra theo thứ tự: lớp `@Aspect` đã được đăng ký thành bean chưa; hỗ trợ auto-proxy đã bật chưa; biểu thức có tạo được advisor cho kiểu/method đó không; và Spring đã cung cấp proxy cho caller chưa.

Ví dụ, `@Pointcut("@annotation(com.example.demo.TrackAction)")` chỉ chọn những method có annotation phù hợp; nó không thực thi xử lý. `@Around("trackedAction()")` mới cung cấp hành vi, được Spring chuyển thành advice trong interceptor chain. Nếu bỏ `@Component` khỏi `TraceAspect`, không có bean aspect để hạ tầng khám phá; nếu bỏ annotation khỏi `place()`, pointcut không chọn method dù service vẫn có trong container.

Việc thấy `AopUtils.isAopProxy(service) == true` chỉ xác nhận tham chiếu là proxy; vẫn phải xác minh **advisor có khớp lời gọi cụ thể**. Gọi `this.place()` từ trong target hoặc tự tạo service bằng `new` có thể bỏ qua đường đi proxy. Nếu dùng JDK proxy, caller phải truy cập phương thức được công bố qua interface; với subclass proxy phải xét khả năng override.

Phép thử hữu ích: gọi một method có annotation và một method không có annotation qua cùng proxy, ghi lại dấu vết advice và target rồi so với pointcut đã khai báo. Kết quả giúp phân biệt **không tạo proxy**, **không khớp advisor** và **không đi qua proxy**.

## 8. Kiểm chứng có hệ thống và áp dụng thực tế

Khi cần debug, thực hiện theo thứ tự:

1. **Bean:** xác nhận cả aspect và service có trong `ApplicationContext`; Spring Boot đã nhận dependency AOP.
2. **Proxy:** kiểm tra `AopUtils.isAopProxy(service)`, khi cần kiểm tra thêm `AopUtils.isJdkDynamicProxy(service)` / `AopUtils.isCglibProxy(service)`.
3. **Pointcut:** so đúng package/method annotation và điều kiện `this`, `target`, `bean`; biểu thức càng hẹp càng dễ dự đoán.
4. **Đường gọi:** bảo đảm client lấy bean từ Spring và gọi từ ngoài qua proxy; phân biệt gọi method khác với self-invocation.
5. **Advice semantics:** xác minh method trả về bình thường hay throw; với `@Around` phải kiểm tra `proceed()` và quy tắc trả về/ném exception.

Ví dụ áp dụng phù hợp: đo thời gian xử lý một nhóm Spring service methods, log thao tác có annotation riêng, theo dõi exception của boundary dịch vụ, hoặc kiểm tra quy ước nội bộ. Cần giới hạn logging dữ liệu nhạy cảm trong advice; chọn pointcut rõ ràng để tránh áp dụng tràn lan lên nhiều bean và tăng chi phí không cần thiết. Với nhu cầu giao dịch, bảo mật hay cache, ưu tiên các cơ chế khai báo chuyên biệt của Spring khi chúng đã đáp ứng yêu cầu.

## 9. Tóm tắt cơ chế cần ghi nhớ

`@Aspect` và các advice annotation là **đầu vào khai báo**. Spring phân tích pointcut/advice, ghép điều kiện với xử lý thành các advisor, chọn bean cần proxy và dùng interceptor chain để chạy advice khi lời gọi phương thức đi qua proxy. `@Pointcut` định nghĩa nơi advice áp dụng; `@Around` và các advice khác định nghĩa hành vi ở thời điểm tương ứng. Hiểu được ranh giới **Spring-managed bean → proxy → advice chain → target** là chìa khóa để giải thích vì sao aspect chạy, chạy sai lúc hoặc không chạy.

Tài liệu Spring chính thức để đối chiếu:
- Spring Framework — [AOP trong Spring](https://docs.spring.io/spring-framework/reference/core/aop.html).
- Spring Framework — [Khai báo pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html).
- Spring Framework — [Pointcut API](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html).
- Spring Boot 3.3 — [AOP auto-configuration](https://docs.spring.io/spring-boot/3.3/reference/features/aop.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-scope-003">Spring AOP can thiệp lời thực thi phương thức qua proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP can thiệp lời thực thi phương thức qua proxy

## 1. Mục tiêu và phạm vi

Sau bài này, bạn có thể giải thích vì sao một lời gọi phương thức trên Spring bean có thể chạy thêm logic trước/sau phương thức thật; phân biệt proxy với đối tượng đích; xác định lời gọi nào đi qua Spring AOP, lời gọi nào đi thẳng vào đối tượng; và chẩn đoán các trường hợp advice không chạy.

**Trọng tâm:** Spring AOP sử dụng **proxy** do Spring container quản lý để chặn *method execution* trên Spring bean. Kiến thức Aspect nói chung không phải chủ đề ở đây. Những tên như aspect, pointcut, advice được dùng để mô tả cách Spring cấu hình chuỗi xử lý cho phương thức.

## 2. Luồng gọi thật sự trong Spring AOP

Giả sử một thành phần khác được tiêm `OrderService` và gọi `orderService.submit("A01")`. Nếu có advice áp dụng cho phương thức này, tham chiếu bean mà thành phần đó nhận có thể là một **proxy** thay vì chính đối tượng triển khai.

```text
Controller / Client bean
          |
          |  gọi orderService.submit("A01")
          v
     Spring AOP proxy
          |
          |  (1) so khớp phương thức với các advisor/pointcut
          |  (2) chạy chuỗi interceptor / advice phù hợp
          |  (3) tiếp tục tới target khi advice cho phép
          v
   OrderServiceImpl.submit(...)
          |
          |  trả kết quả hoặc ném exception
          v
     advice xử lý kết quả/lỗi
          |
          v
     trả về Controller / Client
```

- **Target:** đối tượng chứa phần triển khai nghiệp vụ thật, ví dụ `OrderServiceImpl`.
- **Proxy:** đối tượng trung gian Spring trả về khi bean đủ điều kiện áp dụng advisor; nó tiếp nhận lời gọi từ bên ngoài rồi điều phối advice và target.
- **Advisor / interceptor chain:** Spring chọn các advice phù hợp với phương thức đang gọi và thực thi chúng theo thứ tự đã cấu hình. Proxy không nhất thiết có advice cho *mọi* phương thức của bean.
- **Join point trong Spring AOP:** chỉ là **lúc thực thi phương thức**. Spring AOP theo cơ chế proxy không trực tiếp chặn việc đọc trường, gọi constructor hay mọi điểm thực thi tùy ý.
- **Điểm quyết định:** đường đi quan trọng là `client → proxy → target`. Nếu client nắm trực tiếp target và gọi nó, chuỗi advice của proxy không tham gia.

Ở thời điểm tạo bean, bộ xử lý auto-proxy của Spring phát hiện các advisor/aspect phù hợp và có thể tạo proxy bao quanh bean. Ứng dụng thường lấy proxy từ `ApplicationContext` hoặc qua dependency injection; không cần tự gọi một “hàm AOP” trong business method.

## 3. Hai dạng proxy và hệ quả thực tế

| Cơ chế | Cách tạo | Những phương thức có thể được gọi từ bên ngoài |
| --- | --- | --- |
| **JDK dynamic proxy** | Proxy triển khai interface của bean | Gọi qua các interface mà proxy công bố |
| **Class-based proxy (CGLIB)** | Proxy là lớp con được sinh từ target class | Gọi qua kiểu lớp của proxy đối với phương thức có thể override |

Trong cấu hình Spring Framework thông thường, target có interface thì JDK dynamic proxy là lựa chọn mặc định; nếu không có interface thì Spring dùng class-based proxy. Cấu hình từ Spring Boot hoặc `proxyTargetClass=true` có thể đổi kiểu proxy mặc định. Vì vậy **đừng suy kiểu proxy chỉ từ việc class có interface** khi chưa xem cấu hình thực tế.

Với JDK proxy, mã tiêm bean theo interface thường an toàn hơn việc ép kiểu sang implementation. Class-based proxy **không thể** tạo bằng cách kế thừa một `final class`; `final method` và `private method` không thể được advice theo cơ chế override. Những phương thức không nhìn thấy từ lớp con, chẳng hạn package-private ở lớp cha thuộc package khác, cũng gặp giới hạn. Các phương thức `static` và lời gọi trực tiếp trên đối tượng ngoài Spring container không phải các lời gọi proxy cần trông đợi Spring AOP chặn.

Đừng nhầm kiểu proxy với phạm vi can thiệp: **dù là JDK hay CGLIB, Spring AOP vẫn hoạt động qua lời gọi được chuyển đến proxy**. Chọn CGLIB không tự sửa được lỗi self-invocation.

## 4. Ví dụ có thể chạy: gọi qua proxy và quan sát advice

Ví dụ độc lập dùng **Java 17+, Maven, Spring Framework 6.2.6**. Đặt đúng hai tệp dưới đây. `spring-context` cung cấp container, `spring-aop` chứa cơ chế proxy; `aspectjweaver` được Spring dùng để diễn giải biểu thức pointcut `@AspectJ`. Ví dụ chỉ dùng Spring AOP, không thiết lập compile-time hoặc load-time weaving.

Tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-proxy-demo</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.6</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.6</version>
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
                <version>3.5.0</version>
                <configuration>
                    <mainClass>demo.Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tệp `src/main/java/demo/Main.java`:

```java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class Main {
    public interface OrderService {
        String submit(String orderId);
    }

    static class OrderServiceImpl implements OrderService {
        @Override
        public String submit(String orderId) {
            System.out.println("TARGET submit " + orderId);
            return "OK:" + orderId;
        }
    }

    @Aspect
    static class AuditAspect {
        @Around("execution(* demo..*.submit(..))")
        public Object trace(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("BEFORE " + pjp.getSignature().getName());
            try {
                Object value = pjp.proceed();
                System.out.println("AFTER " + value);
                return value;
            } catch (Throwable error) {
                System.out.println("ERROR " + error.getClass().getSimpleName());
                throw error;
            }
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    static class AppConfig {
        @Bean
        OrderService orderService() {
            return new OrderServiceImpl();
        }

        @Bean
        AuditAspect auditAspect() {
            return new AuditAspect();
        }
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService service = context.getBean(OrderService.class);
            System.out.println("IS_AOP_PROXY=" + AopUtils.isAopProxy(service));
            System.out.println("RESULT=" + service.submit("A01"));
        }
    }
}
```

Chạy tại thư mục có `pom.xml`:

```bash
mvn -q compile exec:java
```

Phần stdout do chương trình in, theo đúng thứ tự:

```text
IS_AOP_PROXY=true
BEFORE submit
TARGET submit A01
AFTER OK:A01
RESULT=OK:A01
```

Giải thích luồng:

1. `AnnotationConfigApplicationContext` tạo bean `orderService` và `auditAspect`. `@EnableAspectJAutoProxy` bật việc phát hiện `@Aspect` bean và tạo proxy khi phù hợp.
2. `@Bean AuditAspect` đăng ký aspect với container; chỉ đặt `@Aspect` lên một lớp **chưa** tự biến lớp đó thành Spring bean.
3. `@Around("execution(* demo..*.submit(..))")` áp dụng cho các lần **thực thi** phương thức `submit` phù hợp trong package `demo`. Khi client lấy bean từ context, lời gọi đến proxy được chặn.
4. Advice in `BEFORE`, gọi `pjp.proceed()` để tiếp tục tới lời gọi trong chuỗi và cuối cùng tới target; target in `TARGET` rồi trả `OK:A01`.
5. Advice in `AFTER` và trả lại **chính giá trị** từ `proceed()`; client cuối cùng in `RESULT`. Với target ném lỗi, nhánh `catch` in lỗi rồi ném lại, không nuốt exception.

Trong `@Around`, quên gọi `proceed()` có nghĩa target có thể **không được chạy**; gọi `proceed()` nhiều lần có thể khiến target được thực hiện nhiều lần. Đây là quyền điều khiển mạnh của around advice, nên cần chủ đích. Nếu chỉ cần ghi sự kiện trước lời gọi, dùng `@Before` dễ đọc hơn; nếu chỉ cần quan sát kết quả thành công, cân nhắc `@AfterReturning`.

**Giới hạn của output:** dòng trên là các dòng do ví dụ tự in. Thông báo từ Maven, thư viện logging hoặc môi trường chạy có thể xuất hiện thêm tùy thiết lập; chúng không thay đổi thứ tự dòng quan sát được trong ví dụ.

## 5. Khi nào advice được chọn?

Spring cần đồng thời thỏa các điều kiện: bean đi qua cơ chế auto-proxy; lời gọi tới đối tượng proxy; phương thức có thể được proxy chặn; và pointcut khớp lần thực thi đó. Các cách viết pointcut Spring AOP thường gặp:

| Biểu thức | Ý nghĩa trong Spring AOP |
| --- | --- |
| `execution(* demo..service.*.*(..))` | Method execution của kiểu thuộc package `demo..service`, mọi tên phương thức và tham số |
| `execution(public * demo..*Service.*(..))` | Các method execution public ở kiểu có tên kết thúc bằng `Service` thuộc `demo` và package con |
| `@annotation(demo.TraceCall)` | Method execution có annotation `@TraceCall` phù hợp |
| `within(demo..service..*)` | Thu hẹp nơi khai báo kiểu nằm trong `demo..service` |
| `bean(orderService)` | Giới hạn theo tên Spring bean; đây là phần mở rộng riêng của Spring AOP |

`execution` là lựa chọn trung tâm khi nói đến **lời thực thi phương thức**. `bean(...)` phân biệt được nhiều bean cùng kiểu. `@annotation` hữu ích khi chỉ muốn chặn một tập phương thức được gắn nhãn; cần chú ý annotation đặt trên interface hay implementation và cơ chế proxy có thể ảnh hưởng cách nhận diện, nên kiểm tra bằng test thực tế.

Ví dụ tổ hợp:

```java
@Around("execution(public * demo..service..*Service.*(..)) && bean(*Service)")
public Object monitorService(ProceedingJoinPoint pjp) throws Throwable {
    return pjp.proceed();
}
```

Đây chỉ là **phương thức advice minh họa**, phải nằm trong một `@Aspect` bean và cần import phù hợp. Không nên dùng pointcut quá rộng như `execution(* *(..))` cho tất cả bean khi mục tiêu chỉ là một nhóm service.

Spring AOP không hỗ trợ các AspectJ designator như `call`, `get`, `set` để chặn phương thức được gọi tại một vị trí mã nguồn hay thao tác field. Thêm chúng vào Spring AOP pointcut có thể gây lỗi phân tích biểu thức. Cũng vì cơ chế proxy, Spring AOP không thay bytecode của mọi đối tượng `new` trong chương trình.

## 6. Self-invocation: trường hợp hay gây hiểu nhầm nhất

Hãy xem một bean:

```java
class BillingService {
    public void checkout() {
        this.charge();          // gọi trực tiếp trên target
    }

    public void charge() {
        System.out.println("charge");
    }
}
```

Nếu một client gọi `billingService.checkout()` **qua proxy**, advice khớp `checkout()` có thể chạy. Nhưng sau khi target đã vào `checkout()`, lời gọi `this.charge()` đi **thẳng trên đối tượng target**, không quay lại proxy; do đó advice chỉ khớp `charge()` sẽ **không** được kích hoạt trong đường đi này.

```text
Từ client:
client -> proxy.checkout() -> advice(checkout) -> target.checkout()
                                                    |
                                                    | this.charge()
                                                    v
                                                target.charge()
                                           (không qua proxy lần hai)

Một lời gọi độc lập:
client -> proxy.charge() -> advice(charge) -> target.charge()
```

Ngay cả khi đặt `proxyTargetClass=true`, nguyên tắc self-invocation vẫn giữ nguyên. Cách giải quyết thường rõ ràng nhất là **tách thao tác cần chặn sang một Spring bean khác**, rồi inject bean đó để lời gọi đi qua proxy. Nếu buộc phải gọi lại chính proxy, có các kỹ thuật như self-injection qua interface hoặc `AopContext.currentProxy()` với `exposeProxy=true`, nhưng chúng tạo sự phụ thuộc vào AOP và dễ gây rối khi quản lý bean; chỉ dùng sau khi cân nhắc cấu trúc thiết kế.

Một biến thể cùng bản chất: tạo trực tiếp `new OrderServiceImpl()`, truyền target thô thay cho bean do Spring cấp, hoặc gọi phương thức trong constructor trước khi proxy hoàn tất. Các lời gọi đó không có đường đi `client → Spring proxy` để advice chặn.

## 7. Phân biệt proxy, target và dữ liệu lời gọi khi gỡ lỗi

Trong advice, Spring cung cấp các thông tin hữu ích:

- `pjp.getSignature().getName()`: tên phương thức được chặn.
- `pjp.getArgs()`: tham số lời gọi; cân nhắc che thông tin nhạy cảm trước khi log.
- `pjp.getThis()`: proxy tiếp nhận lời gọi.
- `pjp.getTarget()`: đối tượng target đứng sau proxy.
- `pjp.proceed()`: chuyển tiếp qua phần còn lại của chuỗi advice tới target, nếu có.

Ở một bean thực tế, `pjp.getThis() == pjp.getTarget()` thường là `false`. Spring hỗ trợ `this(Type)` để ràng buộc kiểu **proxy** và `target(Type)` để ràng buộc kiểu **target** trong pointcut; điều này đặc biệt đáng lưu ý khi JDK proxy chỉ triển khai interface.

Có thể kiểm tra bean ở Java:

```java
Object bean = context.getBean("orderService");
System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
```

Đây là **đoạn bổ sung** cho ví dụ trước; cần `import org.springframework.aop.support.AopUtils;` (đã có trong ví dụ). Các hàm kiểm tra loại proxy giúp chẩn đoán, nhưng **một bean là proxy không bảo đảm mọi method đều có advice**. Còn phải kiểm tra pointcut và đường đi lời gọi.

## 8. Các tình huống Spring thực tế

**Transaction:** `@Transactional` ở chế độ proxy thường khởi động/kết thúc transaction qua interceptor khi lời gọi vào proxy. Đặt `@Transactional` trên một method rồi gọi nó bằng `this.method()` từ cùng bean không tạo lời gọi proxy mới. Vì vậy việc “annotation đã có nhưng transaction mới không bắt đầu” có thể xuất phát từ self-invocation, chứ không phải lỗi annotation.

**Caching:** Với cơ chế proxy của `@Cacheable`, lời gọi bên ngoài qua bean proxy có thể được tra cache trước khi target thực thi. Một lần gọi nội bộ `this.load()` thường đi thẳng tới implementation và bỏ qua interceptor cache trên method đó. Đặt annotation không đồng nghĩa mọi đường gọi tới method đều được cache.

**Asynchronous execution:** `@Async` cũng thường dựa vào proxy. Gọi nội bộ qua `this` có thể không chuyển tác vụ sang executor như kỳ vọng. Để kiểm tra, quan sát thread tại nơi gọi và bên trong method được advice, với lưu ý thứ tự các interceptor và thiết lập Spring thực tế.

Ba ví dụ trên cùng nhấn mạnh một quy luật Spring: hành vi xuyên suốt được kích hoạt tại **ranh giới gọi qua proxy**. Các annotation cụ thể có cơ chế và điều kiện riêng; không suy rằng mọi advice hoặc mọi annotation đều chạy giống nhau.

## 9. Thứ tự interceptor nhìn từ đường đi proxy

Khi hai `@Around` advice cùng chọn một method, hãy xem chúng như **hai lớp bao quanh** lời gọi. Giả sử aspect A được ưu tiên cao hơn aspect B (chẳng hạn `@Order(1)` và `@Order(2)`), cả hai đều `proceed()` đúng một lần và không có interceptor khác thay đổi luồng, trace thường là:

```text
A before → B before → target → B after → A after
```

Giá trị `@Order` nhỏ hơn có độ ưu tiên cao hơn, nên A đi vào trước và đi ra sau. Nếu target ném ngoại lệ, phần `after` đặt trong `finally` vẫn chạy theo chiều thoát ngược; đoạn chỉ chạy sau `proceed()` thành công sẽ không chạy khi lỗi truyền lên. Đây là lý do cần nhìn **chuỗi interceptor và các nhánh thành công/thất bại**, thay vì suy thứ tự từ vị trí phương thức advice trong source.

Spring có thể ghép transaction, cache hay các advisor khác vào cùng proxy. Khi cần audit *sau commit* hoặc đo chính xác vùng transaction, phải kiểm tra thứ tự advisor thực tế; một dòng log sau `proceed()` không mặc nhiên chứng minh commit thành công.

## 10. Checklist chẩn đoán khi “Spring AOP không chạy”

1. **Bean có do Spring quản lý?** Kiểm tra client lấy bean từ injection/context, không tự `new` đối tượng rồi kỳ vọng proxy.
2. **Aspect có được đăng ký?** Có `@Aspect` và là một Spring bean (ví dụ `@Component` được component-scan hoặc `@Bean`). Cấu hình đã bật hỗ trợ `@AspectJ` và dependency `aspectjweaver` chưa?
3. **Lấy được proxy chưa?** Dùng `AopUtils.isAopProxy(bean)`. Nếu `false`, kiểm tra context/configuration và việc advisor có áp dụng cho bean.
4. **Đang gọi qua proxy hay `this`?** Đặt điểm dừng/trace tại caller, target và advice để xác định đường đi.
5. **Pointcut có thật sự khớp method không?** Rà package, kiểu, tên, modifiers, tham số, annotation và tên bean. `execution` mô tả *execution*, không phải vị trí *call*.
6. **Method có thể bị proxy chặn?** Kiểm tra interface khi dùng JDK proxy; với class-based proxy kiểm tra `final`, `private`, visibility và các giới hạn override.
7. **Advice có gọi `proceed()`?** Với `@Around`, không tiếp tục thì target có thể không chạy; nếu catch exception rồi không ném lại thì caller sẽ nhận hành vi khác.
8. **Có nhiều interceptor cùng khớp?** Kiểm tra `@Order`, transaction/cache/async và các aspect khác để hiểu thứ tự trước/sau.

Cách kiểm thử đáng tin cậy là lấy bean từ chính Spring context trong test, gọi qua tham chiếu do context cấp, quan sát một hành vi ngoài target và đối chiếu với đường gọi trực tiếp vào target. Việc test đơn thuần `new ServiceImpl()` không kiểm tra Spring AOP.

## 11. Tự kiểm tra

**Tình huống A.** Client inject `OrderService`, gọi `submit()`; method được một `@Around` pointcut phù hợp chặn. Dấu vết kỳ vọng? — Trước target có logic advice; advice gọi `proceed()`; target chạy; sau đó advice xử lý kết quả trước khi trả caller.

**Tình huống B.** `checkout()` gọi `this.charge()` trong cùng target, trong khi advice chỉ khớp `charge()`. Advice chạy không? — Không cho lời gọi `this.charge()` này, vì không đi qua proxy. Gọi `charge()` từ một bean khác qua proxy thì có thể chạy.

**Tình huống C.** Đổi từ JDK proxy sang CGLIB thì `this.charge()` tự được chặn? — Không. Đổi loại proxy không thay đổi đường đi self-invocation.

**Tình huống D.** `@Aspect` được đặt lên class nhưng không đưa class ấy vào Spring context. Advice có tự chạy? — Không. Aspect phải được Spring đăng ký và hỗ trợ auto-proxy được bật phù hợp.

**Tình huống E.** Aspect dùng `@Around` chỉ in “before” rồi return `null` mà không gọi `proceed()`. Điều gì xảy ra? — Target có thể không được thực thi; caller nhận `null` (hoặc không tương thích với return type), vì around advice quyết định có tiếp tục hay không.

## Tài liệu Spring tham khảo

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB và self-invocation.
- [Spring Framework — @AspectJ support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): bật proxy, đăng ký aspect và advice.
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): method-execution pointcuts được Spring hỗ trợ.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): `proceed()`, kết quả, exception và thứ tự.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-scope-004">Phạm vi áp dụng trên đối tượng được Spring quản lý</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi áp dụng Spring AOP trên đối tượng được Spring quản lý

## 1. Phạm vi của bài học

Trong **Spring AOP dựa trên proxy**, đối tượng được Spring quản lý (Spring bean) là phạm vi mà cơ chế tự động tạo proxy và áp dụng advice hoạt động. Một lớp Java có phương thức khớp pointcut chưa đủ để mọi instance của lớp đó đều được can thiệp.

Sau bài này, bạn cần phân biệt được:

- **Spring bean**: instance được đăng ký, khởi tạo hoặc cung cấp cho Spring IoC container theo bean definition; container quản lý việc cung cấp tham chiếu bean và các bước xử lý liên quan.
- **Target object**: instance chứa hành vi nghiệp vụ thật.
- **AOP proxy**: đối tượng trung gian do Spring tạo cho target đủ điều kiện, giúp chạy advice trước/sau/xung quanh lời gọi phương thức.
- **Đối tượng tự tạo**: instance do mã ứng dụng tự khởi tạo, không đăng ký làm bean; cơ chế Spring AOP tự động không bao bọc instance này.

Trọng tâm là **Spring AOP trong ApplicationContext**, không bàn về lý thuyết Aspect chung hoặc cơ chế weaving độc lập của AspectJ.

## 2. Quy tắc cốt lõi: Bean + proxy + lời gọi đi qua proxy

Một lời gọi phương thức có cơ hội nhận Spring AOP advice khi đồng thời thỏa các điều kiện:

1. **Đối tượng đích thuộc phạm vi Spring quản lý**: chẳng hạn bean từ `@Component`, `@Service`, `@Repository`, `@Bean` hoặc khai báo bean tương đương.
2. **Spring AOP đã được cấu hình và có advisor phù hợp**: aspect được đăng ký là bean, hỗ trợ `@AspectJ` được bật, và pointcut khớp phương thức/bean. Không phải mọi bean đều sinh ra proxy.
3. **Lời gọi đi qua tham chiếu proxy và phương thức có thể được proxy can thiệp**: nếu gọi trực tiếp target hoặc gọi nội bộ qua `this`, advice của proxy không được thực thi cho lời gọi đó.

```text
Lời gọi bên ngoài -> tham chiếu Spring bean (proxy, nếu có)
                  -> advice phù hợp -> target.method()
                  -> kết quả trả về người gọi

Lời gọi trực tiếp -> new Target().method()
                  -> target.method()       (không qua Spring AOP proxy)
```

**Phân biệt quan trọng:** Bean được Spring quản lý là điều kiện để cơ chế auto-proxy của Spring AOP hoạt động, nhưng **không đảm bảo** bean nào cũng có proxy. Việc proxy có mặt còn phụ thuộc cấu hình AOP, pointcut, khả năng proxy hóa kiểu/phương thức và các điều kiện tạo bean.

### Spring có thể quản lý đối tượng theo những cách nào?

| Cách có được đối tượng | Spring có quản lý instance đó không? | Hệ quả đối với Spring AOP |
| --- | --- | --- |
| `@Component`/`@Service` được component scan phát hiện | Có | Có thể được tạo proxy nếu advisor khớp |
| Phương thức `@Bean` trả về `new OrderService()` | Có, **đối với instance được trả về và đăng ký làm bean** | Có thể được proxy; từ khóa `new` trong factory method không loại trừ AOP |
| Bean khai báo bằng Java configuration, XML hoặc đăng ký bean definition | Có | Có thể được auto-proxy |
| `context.getBean(OrderService.class)` | Lấy bean mà container cung cấp | Thường nhận proxy nếu bean đã được AOP proxy |
| Instance `new OrderService()` bên ngoài container | Không | Không tự nhận advice của Spring AOP |
| `new OrderService()` bên trong một phương thức nghiệp vụ, không đăng ký bean | Không | Không tự nhận advice |
| Bean scope `prototype` được lấy qua container | Có; mỗi lần lấy có thể tạo một instance mới | Instance nhận được có thể được proxy tùy điều kiện |
| Đối tượng được tạo ở thư viện khác hoặc bởi một factory ngoài container | Không mặc nhiên | Cần tích hợp rõ ràng với Spring; chỉ cùng kiểu với bean là chưa đủ |

`@Autowired` hoặc constructor injection thường cung cấp tham chiếu bean từ container; nếu Spring đã bọc bean bằng proxy thì dependency nhận được chính là tham chiếu có thể đi qua advice. Ngược lại, giữ một tham chiếu riêng tới target do chính bạn tạo ra sẽ bỏ qua proxy.

## 3. Ví dụ chạy được: cùng một lớp, hai đường tạo instance

Ví dụ sử dụng **Spring Framework 6.2.x và Java 17+**, không cần Spring Boot. `MessageService` là một lớp thông thường. `AppConfig` đăng ký một instance làm Spring bean; `Main` đồng thời tự tạo một instance khác để đối chiếu.

Cấu trúc Maven:

```text
spring-aop-scope/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── MessageService.java
    ├── AuditAspect.java
    └── Main.java
```

**`pom.xml`**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-aop-scope</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring.version>6.2.19</spring.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>${spring.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>${spring.version}</version>
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

**`MessageService.java`**

```java
package example;

public class MessageService {
    public void send(String message) {
        System.out.println("send: " + message);
    }
}
```

**`AuditAspect.java`**

```java
package example;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    @Before("execution(* example.MessageService.send(..))")
    public void beforeSend() {
        System.out.println("[aspect] before send");
    }
}
```

**`AppConfig.java`**

```java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy
public class AppConfig {
    @Bean
    public MessageService messageService() {
        return new MessageService();
    }

    @Bean
    public AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
```

**`Main.java`**

```java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            MessageService managed = context.getBean(MessageService.class);
            System.out.println("managed proxy: " + AopUtils.isAopProxy(managed));
            managed.send("bean");

            MessageService manual = new MessageService();
            System.out.println("manual proxy: " + AopUtils.isAopProxy(manual));
            manual.send("new");
        }
    }
}
```

Tại thư mục chứa `pom.xml`, chạy:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
```

Các dòng do chương trình in ra:

```text
managed proxy: true
[aspect] before send
send: bean
manual proxy: false
send: new
```

**Giải thích từng bước:**

1. `@EnableAspectJAutoProxy` kích hoạt cơ chế Spring AOP proxy trong context ví dụ; `@Bean auditAspect()` đăng ký aspect để Spring tìm thấy advice.
2. `@Bean messageService()` đăng ký **giá trị trả về** làm bean. Mặc dù thân phương thức dùng `new`, instance này được đưa vào vòng quản lý của container.
3. Pointcut khớp phương thức `MessageService.send(..)` của bean. `context.getBean(...)` cung cấp proxy; lời gọi `managed.send("bean")` đi qua advice.
4. `manual` là instance cùng lớp nhưng được tạo riêng ở `Main`. Không có bước đăng ký/proxy, nên `manual.send("new")` chỉ in dòng nghiệp vụ.

Nếu bỏ `@EnableAspectJAutoProxy` trong ứng dụng Spring Framework thuần ở ví dụ này, hoặc không đăng ký `AuditAspect` làm bean, đừng kỳ vọng advice chạy. Trong Spring Boot, starter AOP và auto-configuration có thể đảm nhiệm bước bật hỗ trợ AOP; cần kiểm tra cấu hình thực tế của ứng dụng.

> **Quy tắc dễ nhớ:** Spring nhìn vào **instance do container cung cấp và đường gọi đi qua proxy**, chứ không tự sửa mọi instance Java có cùng class hay mọi vị trí thỏa chuỗi pointcut.

## 4. Các ngoại lệ cần phân biệt về phạm vi quản lý

Không phải mọi object cùng class với Spring bean đều là bean. `@Bean MessageService messageService() { return new MessageService(); }` đưa **instance trả về** vào container; `new MessageService()` gọi trong một utility khác thì không. Đối với prototype bean, từng lần tra cứu từ container có thể nhận một instance mới và mỗi instance vẫn có thể được AOP proxy nếu phù hợp.

Một bean được Spring quản lý cũng chưa chắc nhận advice: aspect có thể chưa được đăng ký, pointcut không khớp, method không được proxy chặn, hoặc caller giữ tham chiếu target thô. Gọi `this.inner()` từ một method khác của target không quay lại proxy, nên advice chỉ gắn trên `inner()` không chạy trong bước gọi đó. Hãy tách trách nhiệm sang bean cộng tác khi cần interception rõ ràng.

JDK proxy chỉ công bố các method trên interface được proxy; CGLIB không thể override `final`/`private` hoặc subclass một `final class`. Constructor, static method và field access không phải join point của Spring AOP proxy. Những lời gọi trong giai đoạn initialization như `@PostConstruct` cũng không nên được thiết kế dựa trên giả định proxy đã có hiệu lực.

Vì vậy cần xác minh hai sự thật khác nhau: **object đã được Spring đăng ký** và **lời gọi đang đi qua proxy có advisor phù hợp**. Bài này tập trung ở ranh giới instance do container quản lý; chi tiết tự gọi và trình tự advice thuộc phần cơ chế proxy.

## 5. Kiểm tra phạm vi trong ứng dụng thật

Khi dự kiến advice phải chạy nhưng thực tế không chạy, kiểm tra theo thứ tự:

1. **Bean có thật trong context đúng không?** Kiểm tra khai báo `@ComponentScan`, `@Bean`, profile/condition và context chứa bean. `context.containsBean("beanName")` kiểm tra **tên bean**, không chứng minh một object bất kỳ là bean.
2. **Tham chiếu đang gọi có phải proxy không?** Sử dụng `AopUtils.isAopProxy(reference)`, có thể kết hợp `AopUtils.isJdkDynamicProxy(...)` hoặc `AopUtils.isCglibProxy(...)`. `false` nghĩa là tham chiếu đang kiểm tra không phải Spring AOP proxy; không tự động kết luận mọi cấu hình AOP sai.
3. **Caller lấy tham chiếu từ đâu?** Đối chiếu `context.getBean(...)` hoặc dependency injection với một instance tự `new`. Nếu có JDK proxy, inject/gọi qua kiểu interface phù hợp thay vì ép kiểu về implementation.
4. **Đường gọi có tự quay về `this` không?** Nếu có, advice của phương thức được gọi nội bộ không nhận được lượt đi qua proxy.
5. **Pointcut và khả năng can thiệp có đúng không?** Xem package, modifier, kiểu proxy, method signature, annotation, overload và bean thực tế. Một biểu thức khớp tên lớp chưa đủ nếu chính lời gọi không đi qua proxy.
6. **Có nhiều ApplicationContext hoặc bean cùng kiểu không?** Xác minh đúng context và đúng bean reference đang sử dụng; aspect trong context này không mặc nhiên áp dụng lên mọi instance do context khác hoặc thư viện khác tạo ra.

Đoạn chẩn đoán nhỏ khi đã có `context`:

```java
// Giả định có AnnotationConfigApplicationContext context
// và một bean MessageService đã đăng ký trong context.
Object bean = context.getBean("messageService");
System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.getTargetClass(bean).getName());
```

Lệnh đầu quan sát **tham chiếu proxy thực tế**; lệnh sau giúp xem lớp target phía sau proxy. Đừng chỉ kiểm tra `bean.getClass()` rồi đánh đồng lớp proxy sinh động với lớp nghiệp vụ.

## 6. Các tình huống cần phân biệt

| Tình huống | Advice Spring AOP có chạy không? | Lý do |
| --- | --- | --- |
| Controller/bean A gọi một public method trên service B được inject, B đã có proxy khớp pointcut | Có | Lời gọi đi qua proxy B |
| `@Bean` trả về `new Service()`, sau đó caller lấy bean qua context và gọi method khớp | Có, nếu proxy hợp lệ được tạo | Instance đã đăng ký là bean |
| Một utility tạo `new Service()` rồi gọi cùng method | Không | Instance ngoài container, không qua proxy |
| Service được proxy, nhưng `this.work()` gọi trong chính Service | Không đối với advice riêng của `work()` | Self-invocation bỏ qua proxy |
| Một bean Spring không khớp advisor nào | Không | Không có advice phù hợp |
| Cố advice phương thức `private` | Không | Phương thức không được proxy chặn |
| Chỉ thêm `@Aspect` lên lớp mà không đăng ký nó làm bean | Không tự động | Spring không phát hiện aspect để cấu hình AOP |

Các dòng ghi “Có” luôn giả định **cấu hình Spring AOP hoạt động, pointcut đúng và phương thức có thể chặn**. Không nên suy ra việc tất cả bean đều bị can thiệp.

## 7. Giới hạn rõ ràng của chủ đề

Spring AOP được thiết kế cho **method execution trên Spring bean thông qua proxy**. Nếu bài toán bắt buộc áp dụng advice cho đối tượng không do Spring quản lý, constructor, field access hoặc lời gọi nội bộ bất kể proxy, đó là yêu cầu vượt phạm vi proxy-based Spring AOP. Spring có tích hợp với AspectJ weaving cho các tình huống khác, nhưng đây là **cơ chế riêng**, cần cấu hình riêng; không nên nhầm `@EnableAspectJAutoProxy` với việc kích hoạt bytecode weaving.

**Tự kiểm tra:** Cho lớp `PaymentService` có một instance được đăng ký bằng `@Bean` và một instance tạo bằng `new`. Cả hai có phương thức `pay()` khớp pointcut. Lần lượt gọi qua `context.getBean(...).pay()`, `new PaymentService().pay()` và gọi `this.pay()` từ một method trong bean. Hãy dự đoán đường gọi nào đi qua Spring proxy và giải thích vì sao. Đáp án: chỉ lời gọi qua tham chiếu bean proxy (nếu được tạo và khớp) mới chạy advice của `pay()`; hai cách còn lại bỏ qua proxy.

**Tài liệu Spring Framework:**
- [Spring AOP — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html)
- [Spring AOP — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring AOP — Declaring an Aspect](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-scope-005">Phân biệt @AspectJ trong Spring AOP và cơ chế dệt của AspectJ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt @AspectJ trong Spring AOP và cơ chế dệt của AspectJ

## 1. Hai cơ chế có thể dùng cùng cú pháp nhưng hoạt động khác nhau

Trong Spring Framework, `@AspectJ` là **kiểu khai báo aspect bằng annotation của AspectJ**, chẳng hạn `@Aspect`, `@Before`, `@Around` và biểu thức `execution(...)`. **Spring AOP đọc các khai báo đó để tạo proxy cho Spring bean**. Việc dùng các annotation này không có nghĩa AspectJ đang dệt (weave) bytecode của ứng dụng.

Cần tách ba khái niệm khi đọc cấu hình hoặc chẩn đoán:

- **Khai báo:** lớp Java gắn `@Aspect` và advice bằng annotation `org.aspectj.lang.annotation.*`. Đây là cú pháp dùng chung.
- **Bộ máy áp dụng trong Spring AOP:** Spring chọn bean/method phù hợp, tạo JDK dynamic proxy hoặc proxy lớp con CGLIB; lời gọi **đi qua proxy** mới được đưa qua chuỗi advice rồi tới đối tượng đích.
- **Dệt thực sự bằng AspectJ:** AspectJ compiler/weaver sửa bytecode của lớp chịu tác động tại thời điểm biên dịch, sau biên dịch hoặc khi nạp lớp. Advice không phụ thuộc việc lời gọi có đi qua Spring proxy.

Vì vậy, `@Aspect`, `@EnableAspectJAutoProxy`, `spring-boot-starter-aop` hoặc dependency `aspectjweaver` **tự thân không chứng minh bytecode đang được dệt**. Spring dùng thư viện AspectJ để phân tích/khớp pointcut, trong khi cơ chế thực thi của Spring AOP vẫn là proxy.

> Phạm vi bài: cách Spring AOP tích hợp cú pháp @AspectJ, sự khác biệt với AspectJ weaving trong ứng dụng Spring và hệ quả kỹ thuật khi lựa chọn cơ chế. Các lý thuyết AspectJ độc lập ngoài Spring không được mở rộng ở đây.

## 2. Bảng so sánh sát thực tế Spring

| Tiêu chí | Spring AOP sử dụng `@AspectJ` | AspectJ weaving trong ứng dụng Spring |
| --- | --- | --- |
| Công cụ áp dụng advice | Hạ tầng auto-proxy của Spring | AspectJ compiler/weaver |
| Cái được thay đổi | Bean được bọc bởi proxy; mã lớp đích về cơ bản giữ nguyên | Bytecode lớp chịu dệt được biến đổi |
| Điểm can thiệp chính | Join point thực tế của Spring AOP là **method execution** trên Spring bean qua proxy | Có thể dùng method execution và các join point khác như field access, constructor, method call khi weaving hỗ trợ |
| Phạm vi đối tượng | Đối tượng do Spring quản lý và đã được proxy | Lớp/đối tượng được weave theo phạm vi cấu hình, kể cả đối tượng không phải Spring bean |
| Gọi `this.otherMethod()` trong bean | Đi thẳng từ target sang target, thường bỏ qua advice trên `otherMethod()` | Có thể được advice vì bytecode của join point đích đã được dệt |
| `private`, `final`, constructor | Không thể xử lý chỉ bằng proxy theo cách tương đương weaving; CGLIB không override được `final`/`private` | Có thể can thiệp vào nhiều join point đó nếu aspect/pointcut và kiểu weaving phù hợp |
| Khởi động và vận hành | Không cần `ajc`, file `aop.xml` hay Java agent để dùng Spring AOP | Cần cấu hình quy trình dệt: `ajc` hoặc AspectJ LTW (weaver/agent, `META-INF/aop.xml`, classloader phù hợp) |
| Mức độ tích hợp Spring | Đi theo vòng đời bean, proxy, advisor, `ApplicationContext` | Có thể kết hợp Spring để cấu hình weaving/aspect, nhưng weaving thực hiện trên lớp |
| Chi phí và rủi ro cấu hình | Ít khâu vận hành; chịu các giới hạn proxy | Kiểm soát sâu hơn nhưng cần quản lý phạm vi weave, agent/build, class loading và thử nghiệm tương thích |

Mệnh đề chính xác là: **Spring AOP có thể dùng cú pháp @AspectJ; AspectJ weaving cũng có thể dùng aspect viết bằng kiểu @AspectJ; cùng mã annotation không làm hai cách thực thi giống nhau.**

## 3. Spring triển khai `@AspectJ` thành proxy như thế nào?

Một luồng điển hình:

~~~text
@Configuration / @Component + @Aspect được đăng ký thành Spring bean
       |
@EnableAspectJAutoProxy (hoặc Spring Boot AOP auto-configuration)
       |
Spring phát hiện aspect, phân tích pointcut, tạo advisor
       |
Bean phù hợp được trao ra qua proxy
       |
Caller -> proxy -> advice chain -> target.method()
                               -> giá trị trả về / exception
~~~

Để bật bằng Java config thuần Spring:

~~~java
@Configuration
@EnableAspectJAutoProxy
class AopConfiguration {
}
~~~

Một aspect cần là **bean thực sự** trong `ApplicationContext`. Có thể khai báo bằng `@Bean`, hoặc dùng đồng thời `@Aspect` và `@Component` nếu component scanning đã được bật. **Chỉ có `@Aspect` không tự đăng ký thành Spring bean.** Khi dùng Spring Boot, dependency `spring-boot-starter-aop` thường cho sẵn cấu hình auto-proxy; vẫn phải đăng ký aspect.

Spring Framework có hai kiểu proxy thường gặp:

1. **JDK dynamic proxy:** bọc các interface của bean; phía gọi tương tác qua interface.
2. **CGLIB class proxy:** tạo lớp con để intercept method có thể override. Spring có thể chọn CGLIB khi bean không có interface hoặc khi cấu hình ép dùng class proxy như `@EnableAspectJAutoProxy(proxyTargetClass = true)`. Kiểu proxy mặc định cần xem theo cấu hình Spring/Spring Boot thực tế, không đoán chỉ từ việc lớp có interface.

Dù dùng JDK proxy hay CGLIB, **lời gọi nội bộ trên `this` vẫn không quay lại proxy**. Ép `proxyTargetClass = true` không khắc phục self-invocation.

### Giới hạn pointcut trong Spring

Spring AOP hỗ trợ một tập biểu thức AspectJ xoay quanh *method execution* như `execution`, `within`, `args`, `@annotation`, `target`/`@target`, `this` và `bean` (mở rộng riêng của Spring). Spring không biến `call(...)`, `get(...)`, `set(...)` hoặc `cflow(...)` thành join point mới. Các designator đòi cơ chế weaving có thể báo lỗi khi đưa vào Spring AOP.

`bean(orderService)` hữu ích để lọc **tên Spring bean** trong Spring AOP; đây là mở rộng của Spring và không phải pointcut tương đương có thể chuyển nguyên xi sang AspectJ weaving thuần. Trong proxy AOP, `this` tham chiếu proxy còn `target` tham chiếu đối tượng phía sau proxy; khác biệt rõ nhất với JDK dynamic proxy.

## 4. Ví dụ Java 17+ chạy được: cùng bean, hai đường gọi

Ví dụ dùng **Spring Framework 6.2.0 + AspectJ Weaver 1.9.22** để minh họa Spring AOP bằng proxy, không bật weaving. Thư mục:

~~~text
spring-aop-demo/
├── pom.xml
└── src/main/java/demo/Demo.java
~~~

`pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-aop-demo</artifactId>
  <version>1.0-SNAPSHOT</version>
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
        <configuration>
          <mainClass>demo.Demo</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
~~~

`src/main/java/demo/Demo.java`:

~~~java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class Demo {
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class Config {
        @Bean
        PaymentService paymentService() {
            return new PaymentService();
        }

        @Bean
        AuditAspect auditAspect() {
            return new AuditAspect();
        }
    }

    public static class PaymentService {
        public void pay() {
            System.out.println("PAY");
        }

        public void batch() {
            System.out.println("BATCH");
            this.pay();
        }
    }

    @Aspect
    public static class AuditAspect {
        @Before("execution(* demo..*.pay(..))")
        public void beforePay() {
            System.out.println("ADVICE");
        }
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            PaymentService bean = context.getBean(PaymentService.class);
            bean.pay();   // Caller -> Spring proxy -> advice -> target
            bean.batch(); // Proxy -> target.batch() -> this.pay(), không qua proxy lần hai
        }
    }
}
~~~

Chạy từ thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do ứng dụng in ra (nếu Maven báo thêm thông tin môi trường thì bỏ qua phần đó):

~~~text
ADVICE
PAY
BATCH
PAY
~~~

Tại sao chỉ có **một** dòng `ADVICE`?

- `bean.pay()` được gọi **qua Spring proxy** nên `@Before` chạy trước `PaymentService.pay()`.
- `bean.batch()` đi qua proxy nhưng không khớp pointcut `pay`; bên trong target, `this.pay()` gọi trực tiếp method cùng đối tượng nên không đi qua proxy. `@Before` không chạy thêm lần nữa.
- Đổi `proxyTargetClass` thành `true`, thêm dependency `aspectjweaver` hoặc đổi vị trí annotation không làm lời gọi nội bộ đó thành lời gọi đi qua proxy.

Đây là ví dụ chạy độc lập với Maven và Java 17 trở lên. Nó minh họa cơ chế Spring AOP; **không minh họa một quy trình AspectJ weaving được kích hoạt**.

### Cách sửa nhu cầu interception ngay trong Spring AOP

Nếu muốn xử lý mỗi lần `pay()` được gọi theo boundary, giải pháp Spring thường rõ nhất là chuyển `pay()` vào **bean riêng**, rồi để `batch()` gọi bean được Spring inject. Lúc đó lời gọi đi qua proxy. Tự inject proxy hoặc gọi `AopContext.currentProxy()` có thể làm được trong một số thiết lập, nhưng tăng phụ thuộc vào Spring AOP và cần cấu hình/kiểm soát vòng đời cẩn thận. Không dùng weaving chỉ để né mọi self-invocation khi có thể tách trách nhiệm giữa các bean hợp lý.

## 5. Khi nào cơ chế weaving của AspectJ xuất hiện trong một ứng dụng Spring?

Spring có tài liệu tích hợp với **AspectJ compile-time weaving (CTW)**, **post-compile weaving** và đặc biệt **load-time weaving (LTW)**. Điểm cốt lõi: AspectJ biến đổi bytecode đã xác định, thay vì bọc riêng một bean bằng proxy. Với LTW, phép biến đổi diễn ra khi lớp được nạp vào JVM (trước khi class được dùng). Với CTW/post-compile, build tạo ra bytecode đã weave.

Đối với LTW, một cấu hình minh họa thường có ba phần phối hợp:

1. **Aspect phù hợp** được biên dịch và có trong classpath, khai báo bằng cú pháp AspectJ hoặc kiểu `@AspectJ`.
2. **Bộ weaver và phạm vi weave**: AspectJ weaver (ví dụ qua `-javaagent:/path/to/aspectjweaver.jar`) cùng khai báo `META-INF/aop.xml`; chỉ rõ các package cần weave.
3. **Phần tích hợp Spring khi cần**: `@EnableLoadTimeWeaving`/`<context:load-time-weaver/>` giúp Spring bố trí `LoadTimeWeaver` theo classloader và môi trường; một số môi trường dùng `spring-instrument` agent. Chỉ thêm annotation này mà không có cơ sở hạ tầng, cấu hình và classloader phù hợp thì không bảo đảm có weaving.

Ví dụ **phác họa riêng của LTW**, không phải một bản thay thế thẳng cho project proxy ở phần 4:

`src/main/resources/META-INF/aop.xml`:

~~~xml
<!DOCTYPE aspectj PUBLIC "-//AspectJ//DTD//EN"
  "https://www.eclipse.org/aspectj/dtd/aspectj.dtd">
<aspectj>
  <aspects>
    <aspect name="demo.TraceAspect"/>
  </aspects>
  <weaver>
    <include within="demo..*"/>
  </weaver>
</aspectj>
~~~

Trong mô hình dùng AspectJ Java agent, ứng dụng cần được khởi chạy với tham số JVM tương tự:

~~~text
-javaagent:/absolute/path/to/aspectjweaver.jar
~~~

`TraceAspect` phải là class aspect thực sự có trên classpath; ví dụ này chỉ cho thấy **vị trí cấu hình weaving**, không tự nhận là project LTW có thể chạy ngay nếu chưa định nghĩa aspect, đóng gói và thiết lập lệnh chạy. Nên giới hạn `include` vào lớp của ứng dụng; weave tràn toàn bộ classpath làm tăng rủi ro và khó chẩn đoán.

Một bẫy quan trọng: ứng dụng dùng Spring Framework **không bắt buộc** aspect nào cũng do Spring tạo/tiêm dependency theo cách áp dụng cho proxy aspect. Việc khởi tạo, cấu hình và inject vào aspect được AspectJ weave cần được thiết kế riêng, đặc biệt khi dùng aspect ngoài `ApplicationContext`. Không suy ra rằng `@Component` + `@Aspect` + `@EnableAspectJAutoProxy` đã kích hoạt AspectJ weaver.

## 6. Chọn cơ chế nào cho từng yêu cầu trong Spring?

| Yêu cầu | Ưu tiên và lý do |
| --- | --- |
| Log, đo thời gian, audit hoặc kiểm tra tại public method của Spring service khi được bean khác gọi | **Spring AOP @AspectJ**, ít cấu hình, khớp với cấu trúc service bean |
| Cross-cutting ở controller/service/repository do Spring quản lý, pointcut là method execution | **Spring AOP** thường đủ; kiểm tra tình huống async, proxy chain và thứ tự advisor |
| Cần advice trên cuộc gọi `this.method()` hoặc thực thi nội bộ cùng object | Có thể **tách bean để giữ Spring AOP**; nếu cần bao phủ mọi đường gọi bên trong class mới cân nhắc weaving |
| Cần tác động constructor, field access, method call site hoặc object tạo bằng `new` ngoài IoC | **AspectJ weaving** phù hợp về cơ chế; proxy của Spring không bao phủ được |
| Một lớp `final` hoặc method `final` cần interception thực sự | **Weaving hoặc đổi thiết kế**; CGLIB subclass proxy không override được `final` |
| Chỉ có dependency `aspectjweaver` và aspect có `@Aspect` | Chưa đủ chứng minh weaving; đọc thiết lập auto-proxy/`ajc`/agent/`aop.xml` trước khi kết luận |

Ví dụ dễ nhầm: `@Transactional` của Spring trong cấu hình proxy mặc định cũng chịu vấn đề self-invocation; cùng bản chất proxy đó có thể gặp ở logging, security hoặc caching thông qua advisor. Tuy nhiên đây là minh họa hệ quả của proxy Spring; không đồng nhất tất cả annotation với một loại aspect `@Aspect`.

## 7. Những nhầm lẫn và cách chẩn đoán

**Nhầm 1: “Có `@Aspect` nên chắc chắn là AspectJ weaving.”** Sai. Kiểm tra có `@EnableAspectJAutoProxy`/auto-configuration, aspect được đăng ký làm bean và Spring đang cấp proxy hay không. Ngược lại, để xác nhận AspectJ weaving, tìm cấu hình `ajc` của build, startup `-javaagent`, `META-INF/aop.xml` hoặc bằng chứng bytecode/class-load weaving.

**Nhầm 2: “Thêm `aspectjweaver.jar` thì self-invocation sẽ được advice.”** Sai. Dependency thường cần cho việc phân tích expression của Spring @AspectJ; muốn weave phải **chủ động kích hoạt và cấu hình** compiler/weaver.

**Nhầm 3: “Bật CGLIB sẽ bắt được private/final/self-call.”** Sai. Proxy CGLIB là lớp con; method `private`/`final` không thể override để advice, còn `this` gọi vào target không qua proxy. Proxy JDK lại phụ thuộc interface được expose; cần hiểu kiểu proxy thực tế.

**Nhầm 4: “Viết mọi pointcut AspectJ rồi Spring AOP sẽ hiểu.”** Sai. Spring AOP hỗ trợ một tập designator giới hạn và có mở rộng `bean(...)` riêng. Với `call`, `get`, `set`, constructor join point..., phải đánh giá weaving thay vì cố dùng Spring proxy.

**Nhầm 5: “Tạo object bằng `new` bên trong service vẫn được auto-proxy.”** Sai trong ngữ cảnh bean thông thường: đối tượng tự tạo không đi qua cơ chế đăng ký và proxy bean của container. Muốn advice với Spring AOP thì quản lý đối tượng bằng Spring và gọi qua bean proxy; nếu bắt buộc advice đối tượng ngoài container thì xét weaving.

**Nhầm 6: “Spring LTW chỉ cần `@EnableLoadTimeWeaving` là xong.”** Sai. Cần xác minh classloader/instrumentation, khai báo aspect và phạm vi weave, cách nạp lớp, agent khi cần. Nếu lớp đã nạp trước khi transformer có hiệu lực, cấu hình LTW có thể không xử lý lớp như mong muốn.

Quy trình chẩn đoán lỗi advice không chạy:

1. Xác nhận đối tượng thật sự là Spring bean được lấy từ `ApplicationContext`, aspect cũng được đăng ký; tránh vô tình khởi tạo service bằng `new`.
2. Xác nhận auto-proxy đã bật và pointcut khớp đúng method execution/các annotation/signature được gọi.
3. Xác định hướng gọi: **caller có đi qua Spring proxy không?** Kiểm tra self-invocation, method không thể advice, JDK interface và CGLIB class proxy.
4. Nếu yêu cầu nằm ngoài khả năng proxy, xem cấu hình weaving riêng: build/agent/`META-INF/aop.xml`, classpath, classloader và package include.
5. Với lời gọi có nhiều advisor, kiểm tra thứ tự bằng `@Order`, vấn đề exception và trạng thái transaction; không suy đoán thứ tự từ tên lớp.

## 8. Câu hỏi tự kiểm tra

1. Một class có `@Aspect` và `@Before`, trong classpath có `aspectjweaver`. Có đủ căn cứ nói nó dùng weaving chưa? **Chưa**: đây có thể chỉ là cấu hình Spring AOP proxy.
2. Vì sao một bean Spring gọi `this.save()` lại bỏ qua advice trên `save()` trong proxy AOP? **Vì lời gọi nội bộ không đi qua proxy**.
3. Đổi JDK dynamic proxy sang CGLIB có làm `this.save()` đi qua proxy không? **Không**.
4. Muốn chặn truy cập field của đối tượng không do Spring tạo thì `@EnableAspectJAutoProxy` có đủ không? **Không**; phải đánh giá AspectJ weaving và phạm vi loại join point tương ứng.
5. Khi một Spring service gọi bean khác có method khớp `execution(...)`, Spring AOP `@AspectJ` có cần compiler `ajc` không? **Không**.

## Tài liệu chính thức để đối chiếu

- Spring Framework, *@AspectJ support*: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework, *Enabling @AspectJ Support*: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework, *Proxying Mechanisms*: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework, *Declaring a Pointcut*: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Spring Framework, *Using AspectJ with Spring Applications*: https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-scope-006">Điều kiện chọn Spring AOP cho một yêu cầu cụ thể</a>

<details>
<summary>Xem chi tiết</summary>

# Điều kiện chọn Spring AOP cho một yêu cầu cụ thể

## Mục tiêu và phạm vi

Bài này trả lời câu hỏi thực hành: **khi có một yêu cầu trong ứng dụng Spring, điều kiện nào khiến Spring AOP là lựa chọn phù hợp, khi nào nên dùng cơ chế có sẵn của Spring, và khi nào phải chọn cách khác?**

Spring AOP là cơ chế chặn **lời gọi phương thức trên Spring bean thông qua proxy**. Với một lời gọi phù hợp pointcut, proxy thực thi advice trước/sau/quanh phương thức đích. Đó là ranh giới kỹ thuật quyết định lựa chọn, vì Spring AOP không tự chặn mọi đoạn mã Java đang chạy. Nội dung dưới đây tập trung vào tiêu chí chọn, giới hạn và cách xác nhận trong môi trường Spring; các khái niệm AOP tổng quát không được lặp lại thành một chương riêng.

## 1. Bộ điều kiện quyết định

Đối với mỗi yêu cầu, kiểm tra lần lượt các câu hỏi sau.

| Điều kiện | Cần xác nhận trong ứng dụng Spring | Kết luận nếu không đạt |
| --- | --- | --- |
| **C1. Điểm cần xử lý là một phương thức** | Hành vi phải được kích hoạt khi một phương thức của bean chạy, ví dụ `OrderService.submit()`. | Nếu cần bắt truy cập field, constructor, mọi lời gọi nội bộ hay câu lệnh bất kỳ: Spring AOP proxy không đáp ứng. |
| **C2. Đối tượng do Spring quản lý** | Đối tượng đích do ApplicationContext tạo/quản lý và có thể được proxy. | `new OrderService()` tự tạo ngoài container không được Spring AOP tự động gắn advice. |
| **C3. Lời gọi đi qua proxy** | Caller dùng bean được inject/tra cứu từ container, thường là một bean khác hoặc lớp tích hợp Spring. | `this.method()` / gọi nội bộ trên target bỏ qua proxy; advice của phương thức bị gọi có thể không chạy. |
| **C4. Phương thức thực sự chặn được** | Kiểm tra kiểu proxy, visibility, `final` và đối tượng target; thiết kế API public rõ ràng. | `private`/`final` không phù hợp với subclass proxy; JDK proxy chỉ chặn lời gọi public qua interface được proxy. |
| **C5. Quy tắc dùng lại được, ranh giới rõ** | Yêu cầu áp dụng cùng chính sách tại nhiều service/method, không cần hiểu chi tiết luồng nghiệp vụ từng ca. | Nếu quy tắc là một bước nghiệp vụ cụ thể, gọi trực tiếp qua service/helper rõ hơn và dễ kiểm thử hơn. |
| **C6. Spring chưa có giải pháp đúng hơn** | Xem `@Transactional`, Spring Security method security, `@Cacheable`, `@Async`, validation, Micrometer/Observation, MVC interceptor/filter, `@ControllerAdvice`. | Ưu tiên tính năng chuyên dụng; không viết custom aspect mô phỏng lại cơ chế có sẵn. |
| **C7. Hệ quả của advice kiểm soát được** | Xác định thứ tự, ngoại lệ, kiểu trả về, luồng/thread, latency, dữ liệu nhạy cảm và khả năng test. | Nếu độ đúng nghiệp vụ phụ thuộc vào thứ tự khó kiểm soát, ngữ cảnh bất đồng bộ hoặc interception không chắc chắn, hãy thiết kế lại. |

**Chỉ nên chọn custom Spring AOP khi C1–C5 đạt, C6 không cung cấp cách phù hợp hơn và C7 được xác minh.** Điểm quan trọng: một yêu cầu “xuất hiện tại nhiều nơi” chưa đủ; nó còn phải đi qua ranh giới proxy được Spring hỗ trợ.

## 2. Phân loại yêu cầu thường gặp trong Spring

| Yêu cầu thực tế | Chọn gì trước | Có nên tự viết Spring Aspect? |
| --- | --- | --- |
| Mở/commit/rollback transaction của một service | `@Transactional` và Spring transaction management | Thường **không**; custom aspect dễ sai propagation, rollback và thứ tự advisor. |
| Kiểm tra quyền người dùng tại method | Spring Security với `@EnableMethodSecurity`, `@PreAuthorize`, `@PostAuthorize` khi thích hợp | Thường **không**; bảo mật cần ngữ cảnh, chính sách và thứ tự kiểm soát đã chuẩn hóa. |
| Cache kết quả service | Spring Cache (`@Cacheable`, `@CacheEvict`, …) | Thường **không**; tận dụng key, eviction và cache manager chuẩn. |
| Chạy method bất đồng bộ | `@Async` cùng executor được cấu hình | Thường **không**; cần lưu ý giới hạn proxy tương tự và ngữ nghĩa trả về/exception. |
| Đo request HTTP và phân tích request/response | Servlet Filter, Spring MVC `HandlerInterceptor`, Micrometer/Observation, Actuator | Không dùng service aspect như cơ chế thay thế mặc định cho HTTP pipeline. |
| Xử lý ngoại lệ HTTP tập trung | `@ControllerAdvice` / `@RestControllerAdvice` | Không; đó là ranh giới web response. |
| Đảm bảo invariant trong domain, như kiểm tra hạn mức trước khi duyệt đơn | Domain service, policy object, validation ở đúng lớp | Thường **không**; ý nghĩa và thứ tự là logic nghiệp vụ cần được nhìn thấy rõ. |
| Gắn log/audit theo một chính sách đồng nhất cho nhiều service method đã chọn | Logging/observation hiện có; nếu cần logic tùy biến, custom Spring Aspect | **Có thể**, nếu pointcut giới hạn chính xác và xử lý đầy đủ failure/PII. |
| Đo thời gian gọi một tập method Spring bean theo annotation nội bộ | Micrometer/Observation hoặc custom advice nếu cần kết hợp riêng | **Có thể**, đặc biệt khi cần tái sử dụng một quy tắc thực sự dùng chung. |
| Bắt truy cập field, lời gọi constructor, method private hoặc object được tạo ngoài Spring | Thiết kế lại điểm mở rộng, explicit decorator, hoặc AspectJ weaving nếu thật sự cần | **Không thể chỉ dùng proxy-based Spring AOP**. |

**Chú ý về các annotation của Spring:** một số khả năng như transaction, cache, async và method security thường sử dụng proxy/interceptor bên dưới. Điều đó không có nghĩa ta cần tự triển khai aspect tương tự. Hãy dùng hợp đồng của từng tính năng Spring và kiểm tra cấu hình activation của chính tính năng đó.

## 3. Thử áp bộ điều kiện lên ba yêu cầu cụ thể

**Ca A — kiểm tra quyền trước `PaymentService.pay()`.** C1–C4 có thể đạt nếu `pay()` là method của Spring bean gọi qua proxy, nhưng **C6** khiến ta ưu tiên Spring Security method security thay cho custom `@Before`: chính sách quyền cần cơ chế xác thực, kiểm thử và bảo vệ đúng chuẩn.

**Ca B — ghi audit tùy biến trên nhóm method có `@TrackBusiness`.** Khi service được inject qua Spring, phương thức public có thể chặn và pointcut giới hạn bởi annotation, C1–C5 có thể đạt. Nếu công cụ observability hiện có chưa đáp ứng bản ghi riêng và C7 cho phép giữ nguyên exception/return value, custom advice trở thành phương án hợp lý. Ví dụ ở phần 5 triển khai chính ca này.

**Ca C — yêu cầu chặn mọi lần gọi `this.charge()` và mọi đối tượng `new CheckoutService()`.** C2/C3 không đạt với mô hình Spring AOP proxy. Đổi sang CGLIB không thay đổi đường gọi nội bộ. Cần xem lại ranh giới bean, viết gọi cộng tác tường minh, hoặc đánh giá AspectJ weaving nếu bắt buộc áp dụng ngoài container.

Như vậy các câu hỏi C1–C4 là **điều kiện kỹ thuật**, C5–C6 quyết định trách nhiệm nên nằm ở đâu, còn C7 quyết định advice có an toàn về thứ tự, lỗi, bất đồng bộ và chi phí vận hành hay không.

## 4. Quy trình chọn cho một yêu cầu cụ thể

Giả sử đề bài: “Mỗi khi một số thao tác quan trọng của service hoàn tất hoặc phát sinh lỗi, hệ thống cần ghi dấu theo chính sách đồng nhất.”

**Bước 1 — xác định nơi quan sát.** Muốn theo dõi kết quả xử lý của service method, không phải toàn bộ request HTTP hay truy cập repository. Mỗi service là Spring bean và được gọi từ bean khác qua DI: đáp ứng C1–C3.

**Bước 2 — kiểm tra các lựa chọn sẵn có.** Nếu chỉ cần trace/span/metric chuẩn, ưu tiên Micrometer/Observation và hạ tầng logging. Nếu là security/transaction, dùng module chuyên biệt tương ứng. Nếu thật sự cần một bản ghi tùy chỉnh với domain event fields sau mỗi use case được phép, cân nhắc một aspect riêng.

**Bước 3 — xác định phạm vi chính xác.** Dùng annotation tường minh `@TrackBusiness` cho method nghiệp vụ được chọn, thay vì pattern quá rộng như `execution(* com.example..*(..))` vốn có thể match controller, infrastructure hoặc method ngoài ý muốn.

**Bước 4 — quyết định thời điểm.** Nếu chỉ cần tín hiệu *trước* lời gọi, `@Before` là đủ. Khi chỉ ghi nhận hoàn tất thành công, dùng `@AfterReturning`; ghi thất bại, `@AfterThrowing`; ghi cleanup bất kể kết quả, `@After`. Chỉ dùng `@Around` khi phải bao quanh lời gọi, đo thời gian, chuyển đổi kết quả hoặc kiểm soát việc gọi tiếp; `@Around` phải xử lý `proceed()` chính xác.

**Bước 5 — kiểm tra hệ quả.** Không log payload nhạy cảm; giữ nguyên kết quả/ngoại lệ nghiệp vụ; xem lại tương tác với `@Transactional`, `@Async` và advisor order. “Phương thức trả về” không nhất thiết đồng nghĩa transaction đã commit, hoặc tác vụ bất đồng bộ đã hoàn tất.

**Bước 6 — xác nhận bằng integration test.** Kiểm thử lời gọi qua bean thật, trường hợp annotation có/không, thất bại, self-invocation, và proxy mode đang cấu hình. Nếu một tiêu chí không bảo đảm được, dừng chọn Spring AOP cho yêu cầu đó.

## 5. Ví dụ chạy được: chỉ đánh dấu những service method cần audit

Trong ứng dụng Spring Boot hỗ trợ Java 17+, ví dụ này in thông báo trước một method có annotation. Nó minh họa cách lựa chọn Spring AOP khi yêu cầu là chính sách tùy biến, được áp dụng ở ranh giới Spring service.

Dự án cần Spring Boot và dependency AOP phù hợp **đúng major version**:

- Spring Boot **3.x**: `org.springframework.boot:spring-boot-starter-aop`.
- Spring Boot **4.x**: starter đã đổi tên thành `org.springframework.boot:spring-boot-starter-aspectj`. Tên có chữ “aspectj” là dependency hỗ trợ khai báo pointcut/advice dạng `@Aspect`; ví dụ dưới đây vẫn sử dụng **Spring AOP proxy**, không thực hiện AspectJ bytecode weaving.

Ví dụ Gradle cho dự án **Spring Boot 4.x đã cấu hình plugin và dependency management**:

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aspectj'
}
```

Với dự án Boot 3.x, thay dòng dependency thành `spring-boot-starter-aop`. Không thêm `@EnableAspectJAutoProxy` chỉ để chạy ví dụ khi Boot đã auto-configure AOP và không vô hiệu hóa `spring.aop.auto`; với ứng dụng Spring Framework tự cấu hình, phải chủ động bật `@EnableAspectJAutoProxy` và cung cấp những dependency cần thiết.

Các tệp Java sau nằm trong `src/main/java/com/example/demo/`.

**Tệp `TrackBusiness.java` — khai báo phương thức được theo dõi:**

```java
package com.example.demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackBusiness {
}
```

**Tệp `CheckoutService.java` — Spring bean làm nghiệp vụ:**

```java
package com.example.demo;

import org.springframework.stereotype.Service;

@Service
public class CheckoutService {
    @TrackBusiness
    public String placeOrder(String id) {
        return "created:" + id;
    }

    public String status() {
        return "ok";
    }
}
```

**Tệp `BusinessAuditAspect.java` — advice chỉ match annotation đã chọn:**

```java
package com.example.demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class BusinessAuditAspect {
    @Before("@annotation(com.example.demo.TrackBusiness)")
    public void beforeTrackedCall(JoinPoint joinPoint) {
        System.out.println("AOP:" + joinPoint.getSignature().getName());
    }
}
```

**Tệp `DemoApplication.java` — lấy `CheckoutService` qua Spring DI:**

```java
package com.example.demo;

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
    CommandLineRunner demo(CheckoutService checkoutService) {
        return args -> {
            System.out.println(checkoutService.placeOrder("ORD-1"));
            System.out.println(checkoutService.status());
        };
    }
}
```

**Chạy:** với Gradle wrapper và plugin Spring Boot đã cấu hình trong project, sử dụng `./gradlew bootRun` (Linux/macOS) hoặc `gradlew.bat bootRun` (Windows). Trên Maven/Spring Boot Maven Plugin, dùng `mvn spring-boot:run`. Với Spring Boot 3/4, tuân thủ phiên bản JDK và Gradle/Maven tương thích của bản Boot được chọn.

Trong log của ứng dụng, ba dòng do demo in ra theo thứ tự là:

```text
AOP:placeOrder
created:ORD-1
ok
```

Những dòng khởi động khác của Spring Boot được bỏ qua ở đoạn kết quả này. `placeOrder()` được advice vì có `@TrackBusiness` và được gọi qua injected proxy. `status()` không có annotation nên không xuất hiện `AOP:status`. Nếu tạo `new CheckoutService()` và gọi `placeOrder()` trực tiếp, object đó không được container áp proxy, nên không có dòng `AOP:placeOrder`.

**Từ nhu cầu đến quyết định:** đối với chính sách audit tùy biến trên một nhóm service method, đây là trường hợp có thể chọn Spring AOP. Nếu yêu cầu chuyển thành “mọi request HTTP phải có access log”, giải pháp phù hợp cần đánh giá lại ở Servlet Filter/MVC/Micrometer, không mở rộng bừa pointcut toàn ứng dụng.

## 6. Khi phải dùng `@Around`: điều kiện an toàn bổ sung

Chỉ chọn `@Around` nếu yêu cầu thật sự cần đo quanh method hoặc chủ động can thiệp tiến trình. Đây là đoạn minh họa **không phải file độc lập**, thay cho method advice của ví dụ trên khi muốn đo thời gian:

```java
@Around("@annotation(com.example.demo.TrackBusiness)")
public Object measure(ProceedingJoinPoint point) throws Throwable {
    long started = System.nanoTime();
    try {
        return point.proceed();
    } finally {
        long elapsedNanos = System.nanoTime() - started;
        System.out.println("elapsed-ns=" + elapsedNanos);
    }
}
```

Đoạn trên cần import `org.aspectj.lang.ProceedingJoinPoint` và `org.aspectj.lang.annotation.Around` trong class `@Aspect`; giá trị `elapsed-ns` thay đổi theo từng lần chạy nên không có stdout số cố định. `finally` bảo đảm có phép đo cả khi method ném exception, còn `return point.proceed()` giữ nguyên giá trị trả về và ngoại lệ gốc. Không nuốt exception, không quên `proceed()` và không gọi `proceed()` nhiều lần trừ khi yêu cầu nghiệp vụ thực sự cho phép thực thi lại.

Nếu phương thức trả về `CompletableFuture`/`Mono`/`Flux` hoặc khởi chạy xử lý nền, phép đo quanh lời gọi có thể chỉ đo **thời gian tạo/trả đối tượng bất đồng bộ**, không phải thời gian xử lý kết thúc. Để đo completion thật, phải sử dụng cơ chế gắn với lifecycle của loại trả về hoặc observability phù hợp. Với transaction, vị trí advisor quyết định phép đo bao quanh hay nằm bên trong vùng transaction; đừng mặc định thời điểm advice chạy là thời điểm commit thành công.

## 7. Checklist kiểm chứng trước khi phê duyệt Spring AOP

- **Ranh giới:** đã liệt kê method phải chặn, caller và Spring bean sở hữu method chưa? Có lời gọi bằng `this`/`new` làm mất interception không?
- **Loại proxy:** đã xác nhận JDK hoặc class-based proxy và kiểm tra `final`, `private`, method visibility, interface method chưa?
- **Pointcut:** đã dùng `@annotation`, `within`, `execution` hoặc `bean(...)` đúng phạm vi Spring bean chưa? Có nguy cơ trúng method không mong muốn không?
- **Thứ tự:** nếu kết hợp với transaction/security/cache/async, đã kiểm chứng advisor order và vị trí logic chạy chưa?
- **Ngữ nghĩa:** advice giữ nguyên giá trị/exception? Có phân biệt success/failure, rollback, completion bất đồng bộ? Có dữ liệu cá nhân/token cần loại khỏi log không?
- **Vận hành:** có đánh giá chi phí proxy/pointcut/logging, tránh vòng lặp do advice tự gọi bean bị advice match, và bảo đảm quan sát được qua test không?
- **Giải pháp thay thế:** Spring Security, `@Transactional`, caching, `@Async`, Micrometer/Observation, Filter/Interceptor hoặc explicit code có phù hợp hơn không?

Tối thiểu nên có kiểm thử integration với ApplicationContext thật cho (a) method được chọn gọi từ bean khác có advice, (b) method không được chọn không có advice, (c) exception vẫn được truyền, (d) lời gọi nội bộ và object tạo ngoài Spring có hành vi đúng theo giới hạn proxy. Dùng `org.springframework.aop.support.AopUtils.isAopProxy(bean)` để hỗ trợ chẩn đoán khi cần, nhưng **proxy tồn tại không chứng minh mọi method đều match pointcut**: hãy xác nhận hiệu ứng của từng lời gọi.

## 8. Các nhầm lẫn dễ khiến chọn sai

1. **“Có `@Aspect` là mọi method đều được chặn.”** Sai: class aspect phải là Spring bean, hạ tầng AOP phải được bật, pointcut match và lời gọi phải qua proxy.
2. **“Dùng CGLIB là xử lý được `this.method()`/private.”** Sai: self-invocation vẫn đi trên target; private không override được.
3. **“Cross-cutting thì luôn viết custom aspect.”** Sai: nhiều chức năng Spring đã có module chuyên biệt, ngữ nghĩa đầy đủ và công cụ vận hành tốt hơn.
4. **“`@Around` và `@Before` thay thế nhau tùy ý.”** Sai: `@Around` quyết định có gọi target hay không, có thể thay kết quả/exception; chọn advice ít quyền nhất đủ đáp ứng yêu cầu.
5. **“AOP có thể đo thời gian hoàn tất mọi tác vụ async.”** Sai: cần phân biệt thời gian invocation và completion của future/reactive stream.
6. **“Advice chạy sau khi method trả về nghĩa là transaction đã commit.”** Chưa chắc: thứ tự proxy/advisor và transaction boundary phải được xét riêng.
7. **“Bật `@EnableAspectJAutoProxy` nghĩa là có AspectJ weaving.”** Sai: cơ chế proxy của Spring AOP vẫn là nền tảng; weaving là chế độ triển khai khác.

**Quy tắc chốt:** chọn Spring AOP khi cần một hành vi dùng chung tại **ranh giới method của Spring-managed beans**, lời gọi **thực sự qua proxy**, các giới hạn visibility/proxy/advisor **chấp nhận được**, và không có thành phần Spring chuyên dụng phù hợp hơn. Khi bất kỳ điều kiện nền tảng nào không đạt, điều chỉnh ranh giới thiết kế hoặc dùng cơ chế khác trước khi viết aspect.

## Tài liệu tham khảo chính thức

- [Spring Framework — Spring AOP capabilities](https://docs.spring.io/spring-framework/reference/core/aop/introduction-spring-defn.html)
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — Declaring Pointcuts](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html)
- [Spring Boot — AOP auto-configuration](https://docs.spring.io/spring-boot/reference/features/aop.html)
- [Spring Boot 4.0 Migration Guide — AOP starter rename](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-scope-007">Quan hệ với IoC, dependency injection và vòng đời bean</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP và quan hệ với IoC, Dependency Injection, vòng đời bean

## 1. Spring AOP nằm ở đâu trong Spring container?

**Spring IoC container** (`ApplicationContext` hoặc `BeanFactory`) chịu trách nhiệm tạo, cấu hình, kết nối và quản lý vòng đời của bean. **Spring AOP** tận dụng container đó để nhận diện aspect/advisor, xác định bean phù hợp và cung cấp **AOP proxy** khi cần. Ứng dụng vẫn dùng IoC/DI bình thường nếu không bật AOP.

Khi một bean cần được advice áp dụng, đối tượng mà consumer nhận qua container có thể là **proxy** bao quanh đối tượng nghiệp vụ (target), thay vì target trực tiếp. Lời gọi từ bean khác đi vào proxy sẽ qua interceptor/advice rồi đến phương thức của target. Không phải mọi bean hoặc mọi phương thức đều được proxy/advice: điều này phụ thuộc pointcut, cấu hình AOP và loại proxy.

```text
Bean definition + @Component / @Bean + @Aspect
                    |
             ApplicationContext
                    |
              tạo target bean
                    |
        giải quyết / inject dependency
                    |
        chạy lifecycle initialization
                    |
           auto-proxy creator
                    |
       xuất bản bean proxy (nếu cần)
                    |
              DI vào consumer
                    |
Consumer --> AOP proxy --> advice --> target.method()
```

Sơ đồ mô tả luồng **thông thường** cho singleton. Khi có circular dependency, scoped proxy, custom `BeanPostProcessor` hay cấu hình đặc biệt, đường đi thực tế có thể phức tạp hơn.

### Vai trò của từng thành phần

| Thành phần Spring | Trách nhiệm |
| --- | --- |
| `ApplicationContext` / `BeanFactory` | Quản lý bean, dependency và vòng đời |
| Target bean | Chứa logic nghiệp vụ cần gọi |
| Aspect bean | Chứa advice và pointcut, bản thân được Spring quản lý |
| Auto-proxy creator | Hạ tầng Spring xác định bean được advice và tạo proxy |
| AOP proxy | Đón lời gọi từ caller, áp dụng interceptor/advice, chuyển tiếp đến target |
| Dependency Injection | Cấp tham chiếu bean từ container cho đối tượng sử dụng; tham chiếu đó có thể là proxy |

**IoC không phụ thuộc AOP**, trong khi tính năng tự tạo proxy của Spring AOP thường phụ thuộc việc target và aspect được quản lý bởi Spring.

## 2. IoC container phát hiện aspect và tạo proxy thế nào?

Với cách viết theo annotation, quy trình cơ bản:

1. Có dependency Spring AOP và thư viện AspectJ hỗ trợ phân tích biểu thức pointcut.
2. Kích hoạt `@AspectJ` auto-proxy: trong Spring Framework thuần dùng `@EnableAspectJAutoProxy`; Spring Boot thường cấu hình tự động khi AOP dependency hiện diện.
3. Đăng ký aspect thành **Spring bean**: `@Aspect` kết hợp `@Component`, hoặc tạo bean aspect qua `@Bean`.
4. Spring nhận diện advice/pointcut, chọn các target bean phù hợp và tạo proxy. Khi consumer yêu cầu bean qua DI, container cấp tham chiếu đã qua xử lý.

**Chỉ có `@Aspect` là chưa đủ để component scan đăng ký bean.** `@Aspect` khai báo vai trò AOP; `@Component` hoặc `@Bean` đưa aspect vào container. Aspect cũng có thể dùng constructor injection để lấy repository/service, nhưng nên tránh circular dependency giữa aspect và các bean mục tiêu. Một bean có `@Aspect` không phải đối tượng để các aspect khác auto-advice trong Spring AOP.

Ví dụ cấu hình **Spring Framework thuần** (đoạn cấu hình, không phải ứng dụng độc lập):

```java
@Configuration
@EnableAspectJAutoProxy
@ComponentScan("demo.aop")
class AopConfig {
}
```

Trong `@AspectJ` support của Spring AOP, Spring dùng ngôn ngữ pointcut theo AspectJ và dependency `aspectjweaver`, nhưng **không cần AspectJ compiler hoặc load-time weaving** để chặn lời gọi phương thức trên Spring proxy.

### Tại sao `BeanPostProcessor` lại quan trọng?

`BeanPostProcessor` nhận callback trước/sau giai đoạn initialization của bean. Một số thành phần hạ tầng AOP (auto-proxy creator) sử dụng cơ chế này để bọc bean thành proxy. Vì vậy instance của `PaymentService` được tạo ra và instance được inject cho một consumer có thể là hai đối tượng Java khác nhau: target và proxy. Đừng dựa vào `bean.getClass() == PaymentService.class` hoặc ép kiểu nội bộ theo suy đoán để kết luận về AOP.

Hạ tầng AOP/auto-proxy và các post-processor có quy tắc khởi tạo riêng, nên một số bean cơ sở hình thành rất sớm có thể không được áp dụng advice giống các bean nghiệp vụ thông thường.

## 3. Dependency Injection quyết định đường đi của lời gọi

Một consumer do Spring quản lý có thể khai báo constructor injection:

```java
@Service
public class CheckoutService {
    private final PaymentService paymentService;

    public CheckoutService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public void checkout(String orderId) {
        paymentService.pay(orderId);
    }
}
```

Đây là ví dụ minh họa; `PaymentService` được định nghĩa trong ví dụ hoàn chỉnh phía dưới. Nếu container đã bọc `PaymentService` thành AOP proxy, dependency truyền vào constructor có thể chính là proxy. Consumer gọi qua dependency này sẽ được advice áp dụng khi pointcut khớp.

Ngược lại, khi viết `new PaymentService()` và gọi trực tiếp phương thức trên object tự tạo, Spring không tự quản lý object đó và không tự gắn AOP proxy. Điều tương tự xảy ra khi bên trong consumer tự `new` một dependency thay vì nhận qua DI.

### Kiểu proxy và kiểu dependency

- **JDK dynamic proxy** triển khai các interface của target. Nếu dùng kiểu này, điểm inject nên khai báo theo interface; inject class implementation có thể thất bại do kiểu thực tế không tương thích.
- **Class-based proxy (CGLIB)** là lớp con của target, thường hỗ trợ DI theo concrete class. Spring Boot mặc định dùng class-based proxy khi auto-configure AOP; có thể đặt `spring.aop.proxy-target-class=false` để chọn JDK proxy trong trường hợp thích hợp.
- Với CGLIB, class `final` không thể được subclass để proxy; phương thức `final` hoặc `private` không thể bị override để advice theo cách đó.
- Spring Framework thuần và Spring Boot có thể chọn mặc định khác nhau; luôn xét cấu hình thực tế của ứng dụng.

Các hàm chẩn đoán hữu ích: `AopUtils.isAopProxy(bean)`, `AopUtils.isJdkDynamicProxy(bean)`, `AopUtils.isCglibProxy(bean)`. Chúng giúp xác định proxy hiện tại nhưng không thay thế việc kiểm tra pointcut và đường gọi.

## 4. Vòng đời bean: lúc nào advice được kích hoạt?

Với bean singleton thông thường:

1. **Instantiation:** Spring tạo target bằng constructor hoặc factory method.
2. **Dependency population:** container cấu hình target và inject các dependency cần thiết.
3. **Initialization:** các `BeanPostProcessor` trước initialization hoạt động; lifecycle callbacks như `@PostConstruct`, `InitializingBean.afterPropertiesSet()`, custom init method được thực hiện theo quy tắc Spring.
4. **Post-initialization:** auto-proxy creator có thể bọc target và trả về proxy.
5. **Use:** consumer nhận bean qua DI và gọi phương thức đi qua proxy; advice hoạt động nếu pointcut khớp.
6. **Destruction:** context đóng thì Spring điều phối các callback hủy cho singleton thích hợp (chẳng hạn `@PreDestroy` hoặc `DisposableBean.destroy()`).

Từ trình tự trên, có ba hệ quả thực tế:

**`@PostConstruct` chạy trong lifecycle của target.** Các lời gọi ở bước này thường diễn ra trước khi proxy cuối cùng được cấp cho consumer. Vì vậy không nên đặt logic trông chờ `@Transactional`, logging advice hay security advice trong `@PostConstruct` hoặc gọi nội bộ từ phương thức init rồi cho rằng advice chắc chắn chạy.

**`@PreDestroy` là callback do container thực thi.** Không nên trông chờ AOP intercept theo cách của một business method thông thường để giải phóng tài nguyên. Đặt cleanup trực tiếp trong callback; callback cũng không được bảo đảm nếu JVM bị dừng cưỡng bức.

**Bean scope có ảnh hưởng tới lifecycle.** Spring gọi initialization cho prototype mà nó tạo nhưng không tự quản lý toàn bộ destruction của prototype như singleton. Inject trực tiếp một prototype vào singleton cũng không khiến nó tự được tạo lại trên mỗi method call. Scoped proxy là cơ chế riêng, có thể cùng xuất hiện với AOP proxy.

## 5. Ví dụ chạy: Spring Boot, DI, advice, init/destroy và self-invocation

Tạo một dự án **Spring Boot 3.x, Java 17+** bằng Spring Initializr, build bằng Maven, thêm dependency **Spring AOP** (`spring-boot-starter-aop`). Dự án Boot thông thường đã có starter cơ bản. Đặt các tệp sau vào package `demo.aop`. Boot sẽ auto-configure AOP, nên ví dụ không cần khai báo `@EnableAspectJAutoProxy`.

**Tệp `src/main/java/demo/aop/AopApplication.java`**

```java
package demo.aop;

import org.springframework.aop.support.AopUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AopApplication {
    public static void main(String[] args) {
        SpringApplication.run(AopApplication.class, args);
    }

    @Bean
    CommandLineRunner demo(PaymentService service) {
        return args -> {
            System.out.println("[di] proxy=" + AopUtils.isAopProxy(service));
            service.pay("A01");
            service.placeOrder();
        };
    }
}
```

**Tệp `src/main/java/demo/aop/PaymentService.java`**

```java
package demo.aop;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    @PostConstruct
    void initialize() {
        System.out.println("[lifecycle] init target");
    }

    public void pay(String orderId) {
        System.out.println("[business] pay " + orderId);
    }

    public void placeOrder() {
        System.out.println("[business] placeOrder");
        this.pay("B02"); // Gọi nội bộ, không đi qua proxy
    }

    @PreDestroy
    void cleanup() {
        System.out.println("[lifecycle] destroy target");
    }
}
```

**Tệp `src/main/java/demo/aop/TracingAspect.java`**

```java
package demo.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TracingAspect {
    @Around("execution(public void demo.aop.PaymentService.pay(java.lang.String))")
    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
        System.out.println("[aop] before");
        try {
            return joinPoint.proceed();
        } finally {
            System.out.println("[aop] after");
        }
    }
}
```

Chạy tại thư mục gốc của dự án Maven:

```bash
mvn spring-boot:run
```

Những dòng `System.out` cần chú ý, theo thứ tự tương ứng (ngoài các log của Spring Boot):

```text
[lifecycle] init target
[di] proxy=true
[aop] before
[business] pay A01
[aop] after
[business] placeOrder
[business] pay B02
[lifecycle] destroy target
```

Giải thích kết quả:

- `[lifecycle] init target` xuất hiện trước khi runner gọi service. Không có advice cho `initialize()`.
- Spring inject bean `PaymentService` vào tham số của `CommandLineRunner`. Aspect áp dụng lên phương thức `pay` nên kết quả `AopUtils.isAopProxy(service)` là `true`.
- `service.pay("A01")` đi qua proxy: trước `pay` có `[aop] before`, sau đó là `[aop] after`.
- `service.placeOrder()` có lời gọi từ bên ngoài, nhưng advice chỉ chọn `pay`. Bên trong target, `this.pay("B02")` không đi qua proxy nên không xuất hiện hai dòng advice quanh `B02`.
- Khi ứng dụng kết thúc và context đóng bình thường, Spring gọi `@PreDestroy`; dòng `destroy target` phản ánh lifecycle callback, không phản ánh business advice.

**Giới hạn self-invocation là tính chất cốt lõi của Spring AOP dạng proxy.** Khi cần advice cho `pay` trong luồng từ `placeOrder`, cách đơn giản là đưa phần gọi cần intercept vào một Spring bean khác, sau đó constructor-inject bean đó và gọi qua tham chiếu Spring. Có thể dùng proxy tự tham chiếu theo cấu hình đặc biệt, nhưng cách đó làm code phụ thuộc vào hạ tầng AOP và dễ tạo vấn đề vòng đời/circular dependency.

## 6. Những nhầm lẫn liên quan trực tiếp vòng đời bean

| Trường hợp | Điểm cần kiểm tra |
| --- | --- |
| `@PostConstruct` gọi method có advice | Callback initialization thường chạy trên target trước khi proxy cuối cùng được công bố; chuyển công việc cần interception sang một lời gọi qua bean sau khởi tạo. |
| `@PreDestroy` mong chạy qua advice | Cleanup là lifecycle callback do container điều phối; viết cleanup ở callback thay vì phụ thuộc business interceptor. |
| Prototype bean được inject trực tiếp vào singleton | Injection diễn ra theo lifecycle của singleton; prototype không tự được cấp mới ở mỗi lời gọi method. Nếu cần mỗi lần lấy mới, cân nhắc provider hoặc lookup phù hợp. |
| Scoped proxy bị nhầm với AOP proxy | Scoped proxy giải quyết cách tham chiếu bean theo scope; không đồng nghĩa có advisor ứng dụng. Một bean có thể chịu nhiều tầng proxy. |
| Aspect inject đúng target mà nó advice | Có nguy cơ tạo circular dependency, dẫn tới proxy sớm hoặc lỗi khởi tạo. Tách dependency quan sát ra bean độc lập và kiểm tra đồ thị phụ thuộc. |

Những trường hợp này là vấn đề **thời điểm và nguồn tham chiếu bean**; kiểm tra `AopUtils` chỉ giúp biết tham chiếu hiện tại có proxy hay không, còn lifecycle đòi hiểu khi nào container tạo, inject, khởi tạo và hủy bean.

### Một lưu ý về thiết kế DI

Constructor injection thể hiện dependency rõ ràng và cho Spring cơ hội cung cấp proxy phù hợp. Nó **không tự giải quyết** circular dependency. Nếu phải self-inject chỉ để AOP advice một phương thức, nên xem lại ranh giới trách nhiệm của các service thay vì lách vòng đời bean.

`@Lazy` và scoped proxy cũng tạo tham chiếu gián tiếp, nhưng không đồng nghĩa với AOP advice. Một ứng dụng có thể có nhiều lớp proxy; hãy xác định nguồn của proxy bằng cấu hình và công cụ chẩn đoán thay vì chỉ xem tên class runtime.

## 7. Checklist phân tích khi lời gọi không được aspect chặn

1. Target và aspect có thuộc đúng `ApplicationContext`? Có `@ComponentScan`, `@Bean` và dependency AOP đầy đủ?
2. Aspect có `@Aspect` **và** đã được đăng ký thành bean? Spring Framework thuần đã bật `@EnableAspectJAutoProxy` hoặc cấu hình tương đương?
3. `AopUtils.isAopProxy(targetInjected)` có trả `true`? Proxy là JDK hay CGLIB, phù hợp với kiểu dependency?
4. Pointcut có thực sự khớp phương thức **được gọi**? Phương thức có thuộc trường hợp `private`/`final` không thể intercept bằng class proxy?
5. Caller gọi qua Spring-injected reference hay là `new`, `this.method()`, hoặc gọi trong initialization callback?
6. Nếu logic phụ thuộc transaction/cache/async, hãy kiểm tra cách annotation đó tạo và dùng proxy trong cấu hình hiện tại.
7. Nếu liên quan lifecycle, phân biệt rõ `@PostConstruct`, lời gọi sau khi context sẵn sàng và `@PreDestroy`.

## 8. Câu hỏi ôn tập

**1. IoC/DI có cần Spring AOP để hoạt động?** Không. IoC và DI là cơ sở quản lý bean; Spring AOP có thể dùng chúng để tự động cung cấp proxy.

**2. Tại sao DI vào service lại nhận proxy?** Auto-proxy creator có thể thay tham chiếu bean được container công bố bằng proxy chứa interceptor/advice.

**3. Vì sao `@Around` không chạy quanh `this.pay()`?** Vì lời gọi đi trực tiếp trên target, không đi qua tham chiếu proxy của Spring.

**4. Có thể tin rằng `@Transactional` chạy trong `@PostConstruct` không?** Không nên. Initialization callback thường diễn ra trên target trước khi có luồng gọi business qua proxy.

**5. Tại sao DI theo interface chạy nhưng DI theo implementation đôi khi lỗi?** JDK proxy chỉ triển khai interface của target, trong khi class-based proxy dùng subclass của target.

## Tài liệu tham khảo chính thức

- [Spring Framework: @AspectJ support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html)
- [Spring Framework: Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework: BeanPostProcessor / Container Extension Points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html)
- [Spring Framework: @PostConstruct và @PreDestroy](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/postconstruct-and-predestroy-annotations.html)
- [Spring Boot: Aspect-Oriented Programming](https://docs.spring.io/spring-boot/reference/features/aop.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-scope-008">Ví dụ xuyên suốt: can thiệp lời gọi service bean trong ApplicationContext</a>

<details>
<summary>Xem chi tiết</summary>

# Ví dụ xuyên suốt: can thiệp lời gọi service bean trong ApplicationContext bằng Spring AOP

## 1. Mục tiêu và giới hạn

Bài này xây dựng một ứng dụng **Spring Framework thuần** để quan sát cách Spring AOP can thiệp khi mã ứng dụng gọi phương thức của **service bean lấy từ ApplicationContext**. Sau khi thực hành, bạn có thể:

- Đăng ký một service và một Spring aspect trong cùng ApplicationContext.
- Bật cơ chế tự tạo proxy bằng **@EnableAspectJAutoProxy**.
- Xác định đường đi của một lời gọi: client → Spring proxy → advice → service đích → client.
- Kiểm tra bean được trả về có phải AOP proxy hay không.
- Giải thích vì sao gọi từ bean khác đi qua proxy, còn gọi nội bộ bằng **this** thường bỏ qua advice.
- Nhận diện các lỗi cấu hình khiến một phương thức service không được chặn.

Trọng tâm là **cơ chế Spring gắn advice vào lời gọi của bean do container quản lý**. Những khái niệm AOP tổng quát chỉ được nhắc đến ở mức cần thiết để đọc và chạy ví dụ; bài không triển khai AspectJ weaving, bytecode instrumentation hay một hệ thống AOP độc lập với Spring.

## 2. Kịch bản thực tế

Giả sử một ứng dụng có **OrderService** nhận mã đơn hàng. Ta muốn in dấu vết trước và sau khi xử lý mà không thêm lệnh ghi log vào phương thức nghiệp vụ. Chúng ta tạo:

1. **OrderService**: hợp đồng mà mã gọi service sử dụng.
2. **OrderServiceImpl**: bean thực thi nghiệp vụ và được Spring quản lý bằng **@Service**.
3. **TraceAspect**: bean aspect được Spring tìm thấy qua **@Aspect** và **@Component**; advice chỉ áp dụng cho phương thức **place(...)**.
4. **AppConfig**: cấu hình component scanning và kích hoạt Spring AOP.
5. **App**: tạo ApplicationContext, lấy service từ container, gọi các phương thức và kiểm tra kết quả.

Sơ đồ đường đi được kỳ vọng:

~~~text
App
 │
 ├─ context.getBean(OrderService.class)
 │                 │
 │          Spring AOP proxy
 │                 │
 └─ service.place("A-01")
                   │
            TraceAspect: before
                   │
          OrderServiceImpl.place
                   │
            TraceAspect: after
                   │
             Giá trị trả về
~~~

Một điểm cốt lõi: biến **service** ở mã client trỏ tới đối tượng proxy do Spring đưa ra. Advice không tự chèn vào mọi lời gọi Java ở mọi nơi; nó chạy khi lời gọi đi qua proxy phù hợp.

## 3. Chuẩn bị dự án

Ví dụ dùng **Java 17+**, **Maven 3.9+**, **Spring Framework 6.2.12** và **AspectJ Weaver 1.9.24**. Dự án **không yêu cầu Spring Boot** hoặc máy chủ HTTP.

Cấu trúc:

~~~text
spring-aop-service-demo/
├── pom.xml
└── src/main/java/demo/
    ├── App.java
    ├── AppConfig.java
    ├── OrderService.java
    ├── OrderServiceImpl.java
    └── TraceAspect.java
~~~

**pom.xml**

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-service-demo</artifactId>
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
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>demo.App</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**spring-context** mang theo các thành phần container và module Spring AOP cần dùng trong ví dụ; **aspectjweaver** cung cấp kiểu annotation, kiểu JoinPoint và công cụ phân tích biểu thức pointcut dạng AspectJ. Với cách chạy ở đây, Spring vẫn thực hiện **proxy-based AOP**: không bật trình biên dịch AspectJ và không gắn Java agent để weave class.

## 4. Viết mã Java

### 4.1. Định nghĩa hợp đồng service

**src/main/java/demo/OrderService.java**

~~~java
package demo;

public interface OrderService {
    String place(String code);

    String placeTwice(String code);
}
~~~

Client sẽ lấy và sử dụng bean theo **OrderService**, là interface mà proxy JDK có thể triển khai. Phương thức thứ hai chỉ dùng để minh họa trường hợp service tự gọi phương thức khác trên chính đối tượng đó.

### 4.2. Cài đặt bean nghiệp vụ

**src/main/java/demo/OrderServiceImpl.java**

~~~java
package demo;

import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements OrderService {

    @Override
    public String place(String code) {
        System.out.println("SERVICE place(" + code + ")");
        return "saved:" + code;
    }

    @Override
    public String placeTwice(String code) {
        String first = this.place(code + "-1");
        String second = this.place(code + "-2");
        return first + "|" + second;
    }
}
~~~

**@Service** làm cho lớp trở thành ứng viên được component scanning đăng ký vào ApplicationContext. **place(...)** chứa nghiệp vụ đơn giản; không cần biết về aspect. Trong **placeTwice(...)**, cả hai lệnh **this.place(...)** đều đi trực tiếp tới phương thức của target. Đây là chủ đích của ví dụ: khi client gọi **placeTwice(...)** qua proxy, phần gọi nội bộ không tự quay lại proxy.

### 4.3. Tạo Spring aspect chặn đúng phương thức

**src/main/java/demo/TraceAspect.java**

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TraceAspect {

    @Around("execution(* demo.OrderService.place(..))")
    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
        System.out.println("AOP before place");
        try {
            Object result = joinPoint.proceed();
            System.out.println("AOP after place");
            return result;
        } catch (Throwable error) {
            System.out.println("AOP error place: "
                    + error.getClass().getSimpleName());
            throw error;
        }
    }
}
~~~

Các phần cần hiểu trong ngữ cảnh Spring:

- **@Aspect** đánh dấu đây là lớp khai báo advice và pointcut để Spring AOP xử lý. Nó **không tự làm lớp trở thành Spring bean**; ở đây **@Component** thực hiện việc đó.
- **@Around(...)** yêu cầu Spring đặt logic bao quanh lời gọi khớp biểu thức.
- **execution(* demo.OrderService.place(..))** chỉ chọn việc thực thi phương thức **place** của hợp đồng **OrderService**, với bất kỳ kiểu trả về và số tham số phù hợp. Vì bean triển khai interface này, Spring có thể áp dụng advisor cho lời gọi qua proxy.
- **joinPoint.proceed()** chuyển tiếp lời gọi cho bước kế tiếp trong chuỗi interceptor và cuối cùng đến target. Nếu bỏ nó, nghiệp vụ **place(...)** sẽ không chạy; nếu gọi nhiều lần, nghiệp vụ có thể chạy nhiều lần.
- Khi target thành công, advice trả về chính giá trị nhận từ **proceed()**. Khi target ném lỗi, ví dụ log tên kiểu lỗi rồi **ném lại nguyên lỗi** để giữ hành vi của service.
- Biểu thức hiện tại **không** chọn phương thức **placeTwice(...)**.

Trong ứng dụng thực, ghi log bằng framework logging, có chính sách che dữ liệu nhạy cảm, và tránh ghi quá nhiều dữ liệu tại advice. **System.out.println** chỉ giúp ví dụ có đầu ra dễ kiểm tra.

### 4.4. Bật AOP và đăng ký bean

**src/main/java/demo/AppConfig.java**

~~~java
package demo;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackages = "demo")
@EnableAspectJAutoProxy
public class AppConfig {
}
~~~

- **@Configuration** khai báo cấu hình cho container.
- **@ComponentScan(basePackages = "demo")** phát hiện **OrderServiceImpl** và **TraceAspect**.
- **@EnableAspectJAutoProxy** đăng ký hạ tầng auto-proxy dùng để xét các Spring bean với những advisor tương ứng. Với ví dụ có interface, cấu hình mặc định của Spring Framework thuần sử dụng JDK dynamic proxy.
- AOP infrastructure được bật trong **ApplicationContext** đang dùng. Nếu tạo một context khác mà không cấu hình tương ứng, không thể mặc nhiên kỳ vọng proxy giống context này.

### 4.5. Chạy và quan sát từ ApplicationContext

**src/main/java/demo/App.java**

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            OrderService service = context.getBean(OrderService.class);
            System.out.println("IS_PROXY " + AopUtils.isAopProxy(service));

            System.out.println("RESULT " + service.place("A-01"));
            System.out.println(
                    "INTERNAL_RESULT " + service.placeTwice("B-02"));
        }
    }
}
~~~

Điểm quan trọng là **context.getBean(OrderService.class)**. Nếu thay bằng **new OrderServiceImpl()**, client đang giữ target do chính mình tạo, không được Spring bọc proxy nên Spring AOP không chặn lời gọi đó.

## 5. Chạy chương trình và đọc đầu ra

Trong thư mục chứa **pom.xml**, chạy:

~~~bash
mvn -q clean compile exec:java
~~~

Đầu ra logic chuẩn (không tính thông báo riêng của Maven hoặc môi trường):

~~~text
IS_PROXY true
AOP before place
SERVICE place(A-01)
AOP after place
RESULT saved:A-01
SERVICE place(B-02-1)
SERVICE place(B-02-2)
INTERNAL_RESULT saved:B-02-1|saved:B-02-2
~~~

Giải thích lần lượt:

1. **IS_PROXY true**: Spring trả về một đối tượng AOP proxy, thay vì client trực tiếp giữ target thuần.
2. **service.place("A-01")**: đây là lời gọi từ client đi vào proxy; advice in **AOP before place**.
3. **proceed()**: target thực hiện nghiệp vụ, in **SERVICE place(A-01)** và trả về **saved:A-01**.
4. Sau khi target thành công, advice in **AOP after place** rồi trả kết quả về client; client in **RESULT saved:A-01**.
5. **service.placeTwice("B-02")**: lời gọi bên ngoài vẫn được chuyển qua proxy tới target, nhưng biểu thức pointcut không chọn chính phương thức **placeTwice(...)**.
6. Từ bên trong target, **this.place(...)** được gọi hai lần; các lời gọi này **không qua proxy**, nên chỉ xuất hiện hai dòng **SERVICE place(...)**, không có dòng **AOP before/after**.

Vì vậy, việc một phương thức có annotation **@Service** không có nghĩa mọi lần gọi phương thức đó đều tự động bị can thiệp. Điều kiện quyết định trong ví dụ là: **lời gọi đi qua Spring AOP proxy** và **phương thức khớp advisor/pointcut**.

## 6. Phân tích vòng đời proxy trong Spring

### 6.1. Lúc dựng ApplicationContext

Khi thực thi **new AnnotationConfigApplicationContext(AppConfig.class)**, Spring đọc cấu hình, tìm component, tạo các bean liên quan và áp dụng hạ tầng auto-proxy. **TraceAspect** là một Spring bean mang khai báo **@Aspect**; Spring đọc các khai báo advice và xác định bean/phương thức nào có thể phù hợp. **OrderServiceImpl** đủ điều kiện nên bean được đưa đến client qua proxy.

Diễn giải đơn giản:

~~~text
AppConfig
  ├─ @ComponentScan ──> OrderServiceImpl (@Service)
  ├─ @ComponentScan ──> TraceAspect (@Aspect, @Component)
  └─ @EnableAspectJAutoProxy
           │
           └─ Auto-proxy infrastructure
                      │
             OrderService bean exposed as proxy
~~~

Đối tượng đích vẫn chứa nghiệp vụ **place(...)**. Proxy chịu trách nhiệm chuyển lời gọi qua chuỗi interceptor được Spring xây dựng. Đối với ví dụ này, chuỗi chứa advice **trace(...)**.

### 6.2. Lúc client gọi phương thức

Điều kiện áp dụng advice có thể xem qua ba câu hỏi:

| Câu hỏi | Trong ví dụ | Hệ quả |
| --- | --- | --- |
| Đối tượng đến từ ApplicationContext đã bật AOP? | Có, gọi **context.getBean(...)** | Có thể nhận proxy |
| Có advisor phù hợp phương thức gọi? | Có, đối với **place(...)** | Advice được lựa chọn |
| Lời gọi có đi qua proxy? | Có với lời gọi từ **App**; không với **this.place(...)** | Chỉ lời gọi bên ngoài vào **place(...)** được chặn |

Ở đây Spring AOP dùng pointcut theo **lời gọi thực thi phương thức bean**. Không suy rộng rằng proxy tự chặn constructor, truy cập field hoặc toàn bộ mã Java nằm trong phương thức.

### 6.3. JDK proxy và CGLIB trong ví dụ này

**OrderServiceImpl** triển khai **OrderService**, vì vậy với cấu hình Spring Framework thuần như trên, Spring có thể tạo **JDK dynamic proxy** triển khai interface đó. Vì thế hãy lấy bean bằng **OrderService.class**. Không nên mặc định lấy bằng **OrderServiceImpl.class** trong chế độ JDK proxy: kiểu proxy không phải một instance của lớp triển khai.

Nếu service không triển khai interface, Spring AOP có thể tạo proxy dựa trên lớp bằng **CGLIB**. Bạn cũng có thể yêu cầu class-based proxy bằng **@EnableAspectJAutoProxy(proxyTargetClass = true)**. Lúc đó các ràng buộc kế thừa phải được lưu ý: lớp **final** không thể được subclass và phương thức **final/private** không thể được advice theo cách subclass proxy thông thường. Với ứng dụng Spring Boot, loại proxy mặc định có thể bị cấu hình Boot tác động, nên không áp dụng máy móc giả định mặc định của ví dụ Framework thuần.

## 7. Ba thí nghiệm để tự kiểm chứng

### Thí nghiệm A: gọi đối tượng tự tạo

Trong **App.java**, ngay sau phần gọi bean từ context, thêm:

~~~java
OrderService direct = new OrderServiceImpl();
System.out.println("DIRECT_RESULT " + direct.place("C-03"));
~~~

Đầu ra riêng của lần gọi này:

~~~text
SERVICE place(C-03)
DIRECT_RESULT saved:C-03
~~~

Không có **AOP before/after** vì **direct** không đi qua một Spring proxy. Việc lớp **OrderServiceImpl** có **@Service** không biến mọi instance tạo bằng **new** thành Spring bean.

### Thí nghiệm B: để proxy chặn cả lời gọi placeTwice

Trong **TraceAspect.java**, đổi duy nhất biểu thức pointcut:

~~~java
@Around("execution(* demo.OrderService.*(..))")
~~~

Khi gọi **service.placeTwice("B-02")**, advice bây giờ chạy **một lần cho lời gọi bên ngoài vào placeTwice(...)**. Hai lời gọi **this.place(...)** bên trong target vẫn không có vòng advice riêng. Các chuỗi log trong ví dụ cố ý đặt tên **place**, nên nếu chạy thí nghiệm này hãy đổi thông điệp log thành tên phương thức động (chẳng hạn **joinPoint.getSignature().getName()**) để tránh hiểu lầm.

### Thí nghiệm C: tắt Spring AOP trong context

Bỏ **@EnableAspectJAutoProxy** khỏi **AppConfig** trong ứng dụng Spring Framework thuần này. Chạy lại: **AopUtils.isAopProxy(service)** trở thành **false** và không có log của **TraceAspect**. Giữ nguyên annotation **@Aspect** trên lớp là chưa đủ: cần có hạ tầng xử lý aspect và đăng ký aspect bean trong context.

Các thí nghiệm này nên được chạy **từng thí nghiệm một**, rồi hoàn nguyên thay đổi trước khi thử tình huống tiếp theo.

## 8. Những lỗi hay gặp và cách kiểm tra

| Hiện tượng | Nguyên nhân có thể có trong Spring | Cách kiểm tra hoặc sửa |
| --- | --- | --- |
| Không thấy advice chạy | Chưa bật **@EnableAspectJAutoProxy** trong context hiện tại | Kiểm tra **AppConfig** thực sự được nạp |
| Aspect không được phát hiện | Chỉ có **@Aspect** mà không đăng ký bean | Thêm **@Component** hoặc đăng ký bằng **@Bean** |
| Không tìm thấy service | Component scan không bao phủ package **demo** | Xác minh **@ComponentScan** và **@Service** |
| Lấy bean theo lớp triển khai thất bại | Bean được expose bằng JDK proxy triển khai interface | Inject/lấy theo **OrderService** |
| Advice chạy với lời gọi bên ngoài nhưng thiếu ở lời gọi nội bộ | **this.place(...)** bỏ qua proxy | Chuyển luồng cần advice sang một Spring bean khác và gọi qua bean được inject |
| Advice chạy sai hoặc không chạy ở một method | Biểu thức **execution(...)** quá rộng hoặc quá hẹp | Kiểm tra kiểu, tên method, package và đối tượng được proxy |
| Có log trước nhưng không có log sau | Target ném exception hoặc **proceed()** không hoàn thành | Giữ nguyên exception; dùng **finally** nếu muốn ghi log kết thúc dù lỗi |
| Startup báo lỗi liên quan AspectJ annotation/pointcut | Classpath thiếu dependency AspectJ cần cho kiểu annotation/parsing | Kiểm tra **aspectjweaver** cùng dependency Spring |

Khi có nhiều aspect, thứ tự các advice có thể thay đổi chuỗi log. Spring cung cấp **@Order** để quy định mức ưu tiên; không nên suy ra thứ tự chỉ từ vị trí class trong dự án.

## 9. Áp dụng thiết kế vào service thật

Ví dụ logging ở đây rất nhỏ, nhưng nguyên tắc triển khai quan trọng hơn lượng code:

- Đặt nghiệp vụ trong Spring bean và lấy dependency qua injection từ container. Đối tượng tự tạo bằng **new** không tự tham gia cơ chế Spring AOP.
- Viết pointcut đủ hẹp, chẳng hạn chỉ một gói service hoặc một annotation nghiệp vụ, để không vô tình can thiệp các phương thức ngoài ý muốn.
- Khi dùng **@Around**, gọi **proceed()** có chủ đích; giữ nguyên kết quả trả về và ngoại lệ nếu chỉ muốn quan sát. Advice can thiệp kết quả chỉ khi đó thực sự là yêu cầu thiết kế.
- Hạn chế dựa vào **self-injection** hay **AopContext.currentProxy()**. Nếu cần chặn một thao tác được gọi bên trong, giải pháp dễ bảo trì là tách thao tác đó sang bean khác rồi gọi qua dependency do Spring inject.
- Trong các hệ thống dùng **@Transactional**, **@Async** hoặc advice của nhiều thư viện, quy tắc đi qua proxy và self-invocation cũng là điều cần kiểm tra; mỗi cơ chế còn có điều kiện riêng nên phải đối chiếu cấu hình thực tế.
- Kiểm tra bằng cả **AopUtils.isAopProxy(...)** lẫn dấu vết hành vi. Việc có proxy không bảo đảm mọi method đều khớp pointcut.

## 10. Câu hỏi tự đánh giá

**1. Tại sao gọi service bằng context.getBean(...) lại có log advice, còn new OrderServiceImpl() thì không?**

Vì Spring tạo và expose proxy trong ApplicationContext có bật AOP. Instance do ứng dụng tự tạo không đi qua quá trình đó.

**2. Tại sao placeTwice(...) gọi this.place(...) mà không có log advice?**

Vì lời gọi từ chính target tới phương thức của nó là lời gọi Java trực tiếp, không đi qua proxy của Spring.

**3. Có phải @Aspect đã đủ để Spring quét và kích hoạt aspect?**

Không. **@Aspect** khai báo vai trò AOP; lớp vẫn phải được đăng ký là Spring bean (ở đây là **@Component**), đồng thời context cần bật khả năng xử lý @AspectJ.

**4. Nếu đổi pointcut thành execution(* demo.OrderService.*(..)), điều gì thay đổi?**

Các lời gọi bên ngoài qua proxy vào cả **place(...)** và **placeTwice(...)** sẽ khớp. Các lời gọi **this.place(...)** từ bên trong **placeTwice(...)** vẫn bỏ qua proxy.

**5. Trong ví dụ interface-based proxy, tại sao nên lấy bean theo OrderService.class?**

Vì JDK dynamic proxy thực thi interface **OrderService**; nó không nhất thiết có kiểu runtime **OrderServiceImpl**.

## 11. Tài liệu đối chiếu

- Spring Framework Reference — Spring AOP: https://docs.spring.io/spring-framework/reference/core/aop.html
- Spring Framework Reference — @AspectJ support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework Reference — Proxying mechanisms và self-invocation: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html

Kết quả cần nhớ từ thí nghiệm: **Spring AOP can thiệp lời gọi service bean qua proxy do ApplicationContext quản lý, với advisor phù hợp; lời gọi trực tiếp trên target hoặc gọi nội bộ bằng this không tự chạy cùng advice.**


</details>

- [Quay lại đầu trang](#back-to-top)
