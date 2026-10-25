<a id="back-to-top"></a>

# Tổ chức Pointcut

## Menu
- [Khai báo pointcut có tên bằng @Pointcut](#reusable-pointcuts-001)
- [Kết hợp pointcut theo tầng kiến trúc hoặc nhóm chức năng](#reusable-pointcuts-002)
- [Chia sẻ pointcut giữa các aspect](#reusable-pointcuts-003)
- [Visibility của phương thức pointcut và phạm vi tái sử dụng](#reusable-pointcuts-004)
- [Phân biệt visibility của khai báo pointcut với phương thức được khớp](#reusable-pointcuts-005)
- [Đặt tên theo ý định thay vì chi tiết chuỗi biểu thức](#reusable-pointcuts-006)
- [Loại trừ bean hạ tầng, aspect và mã ghi nhận dùng chung](#reusable-pointcuts-007)
- [Kiểm thử ca được khớp và ca không được khớp](#reusable-pointcuts-008)
- [Giữ pointcut ổn định khi đổi gói, tên lớp hoặc tên phương thức](#reusable-pointcuts-009)

## <a id="reusable-pointcuts-001">Khai báo pointcut có tên bằng @Pointcut</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo pointcut có tên bằng `@Pointcut` trong Spring AOP

## 1. Vì sao nên đặt tên cho pointcut?

Trong Spring AOP, các annotation như `@Before`, `@Around`, `@AfterReturning` thường cần một biểu thức để xác định **lời gọi phương thức nào trên Spring bean sẽ được advice**. Nếu viết trực tiếp biểu thức dài ở mọi advice, mã rất dễ bị lặp và khó bảo trì:

```java
@Before("execution(public * com.example.shop.service.*.*(..))")
public void audit() { /* ... */ }

@AfterReturning("execution(public * com.example.shop.service.*.*(..))")
public void recordSuccess() { /* ... */ }
```

Hai lời advice này lặp cùng một chính sách chọn phương thức. Khi đổi phạm vi package hoặc tên service, cần sửa nhiều chỗ và dễ bỏ sót. Spring hỗ trợ cú pháp khai báo **pointcut có tên** bằng `org.aspectj.lang.annotation.Pointcut`. Bạn khai báo một method `void` có `@Pointcut("...")`, rồi **tham chiếu tên method** trong các advice hoặc trong một pointcut khác.

```java
@Pointcut("execution(public * com.example.shop.service.*.*(..))")
public void serviceOperation() {}

@Before("serviceOperation()")
public void audit() { /* ... */ }

@AfterReturning("serviceOperation()")
public void recordSuccess() { /* ... */ }
```

Đây là **fragment** trong một class `@Aspect` đã đăng ký làm Spring bean, không phải file Java độc lập.

Điều quan trọng nhất: **`serviceOperation()` là tên một pointcut được Spring phân tích và tham chiếu, không phải phương thức chứa logic nghiệp vụ để gọi lúc chạy.** Thân phương thức pointcut thường rỗng và Spring không gọi thân phương thức đó mỗi khi có join point khớp.

Phạm vi bài này là **Spring Aspect dùng `@AspectJ` annotation-style trên các Spring-managed beans qua proxy**, theo yêu cầu chỉ tập trung Aspect trong Spring. Không đi sâu vào AspectJ weaving ngoài Spring hoặc các loại join point không được Spring AOP hỗ trợ.

## 2. Cấu tạo một named pointcut

Một khai báo điển hình:

```java
import org.aspectj.lang.annotation.Pointcut;

@Pointcut("execution(public * com.example.shop.OrderService.submit(..))")
public void orderSubmission() {}
```

Có ba phần cần phân biệt:

| Thành phần | Ý nghĩa |
| --- | --- |
| `@Pointcut("execution(...)")` | **Pointcut expression**: diễn tả method executions nào khớp. |
| `public void orderSubmission()` | **Pointcut signature**: tên và các tham số hình thức (nếu có) để nơi khác tham chiếu. |
| `{}` | **Thân phương thức**: thông thường rỗng, không phải nơi để xử lý lời gọi thực tế. |

### Các quy tắc cú pháp

1. Phương thức được đánh dấu `@Pointcut` **phải trả về `void`**.
2. Tên method là tên pointcut: viết `orderSubmission()` trong expression của advice **với cặp dấu ngoặc** ngay cả khi signature không có tham số.
3. Nếu pointcut cần bind đối số, signature có thể có Java parameters; chúng là **các tham số dùng cho binding**, không có nghĩa Spring sẽ gọi method body.
4. Java visibility như `private`, `protected`, `public` quyết định **nơi được tham chiếu tên pointcut** theo quy tắc thông thường; nó **không thay đổi phạm vi method execution mà pointcut expression khớp**.
5. Biểu thức nằm trong annotation là AspectJ pointcut expression được Spring hỗ trợ và phân tích. Spring AOP chỉ hỗ trợ **method execution join points trên các bean được quản lý qua Spring proxy**.

Ví dụ minh họa khác:

```java
@Pointcut("within(com.example.shop.service..*)")
private void inServiceLayer() {}

@Pointcut("execution(public * *(..))")
private void publicMethod() {}

@Pointcut("inServiceLayer() && publicMethod()")
private void publicServiceMethod() {}
```

Khi tham chiếu `publicServiceMethod()`, Spring kết hợp **cả hai điều kiện**. Bạn không cần copy expression `within(...)` và `execution(...)` sang mọi advice.

## 3. Named pointcut không phải advice và không tự kích hoạt logic

Một lỗi hiểu sai phổ biến là cho rằng:

```java
@Pointcut("execution(* com.example..service.*.*(..))")
public void businessOperation() {
    System.out.println("pointcut invoked");
}
```

sẽ in log ở mỗi lần service method chạy. **Không phải vậy.** Thân method chỉ là Java body thông thường; Spring AOP dùng **annotation metadata**, không gọi body để quyết định match.

Nếu một đoạn code thực sự gọi `businessOperation()` như method Java bình thường, nó chỉ gọi body Java như mọi method khác. Nó **không kích hoạt advice, không thực hiện query để tìm các join points và không tự chạy các method mà expression mô tả**.

Thực hành chuẩn:

```java
@Pointcut("execution(* com.example..service.*.*(..))")
public void businessOperation() {}

@Before("businessOperation()")
public void traceServiceCall(org.aspectj.lang.JoinPoint joinPoint) {
    System.out.println(joinPoint.getSignature().getName());
}
```

Đây là **fragment** nằm trong aspect, cần `@Before`, `@Pointcut` imports và bean registration. `businessOperation()` là tên được tham chiếu; việc in log phải đặt trong **advice method**, không đặt trong body pointcut.

Cũng cần phân biệt `@Pointcut` với Spring `org.springframework.aop.Pointcut` **interface**. Annotation `@Pointcut` thuộc phong cách khai báo `@AspectJ`; interface `Pointcut` thuộc API hạ tầng Spring AOP. Cùng diễn đạt “chọn join point”, nhưng không phải hai cách viết tùy ý thay cho nhau ở cùng vị trí annotation.

## 4. Cách tham chiếu named pointcut trong cùng một aspect

Một aspect có thể đặt `@Pointcut` và nhiều advice cùng class:

```java
package com.example.shop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class ServiceAspect {

    @Pointcut("execution(public * com.example.shop.OrderService.*(..))")
    private void orderServiceOperations() {}

    @Before("orderServiceOperations()")
    public void beforeOrderService(JoinPoint jp) {
        System.out.println("before " + jp.getSignature().getName());
    }

    @AfterReturning("orderServiceOperations()")
    public void afterSuccess() {
        System.out.println("success");
    }
}
```

Class trên là Java class có đủ imports, nhưng **chưa là ví dụ chạy độc lập** vì còn thiếu `OrderService`, `@EnableAspectJAutoProxy` và cách đăng ký aspect bean.

Vì `orderServiceOperations()` là **private method trong cùng class**, cả `@Before` và `@AfterReturning` có thể tham chiếu nó bằng tên ngắn. Không cần `public` chỉ vì advice nằm trong cùng aspect.

Ưu điểm cụ thể:

- Một thay đổi phạm vi `OrderService` tác động nhất quán cả hai advice.
- Tên `orderServiceOperations` dễ hiểu hơn expression dài lặp đi lặp lại.
- Review có thể nhìn expression để biết **chọn gì**, nhìn advice để biết **làm gì**.
- Bạn có thể viết integration test kiểm tra một điểm chặn dùng chung cho nhiều advice.

**Lưu ý về số lần thực thi:** nếu có cả `@Before` và `@AfterReturning` cùng named pointcut, một lần gọi method trả về bình thường có thể kích hoạt **hai advice khác loại**. Đây là hành vi được thiết kế, không phải Spring đăng ký pointcut hai lần.

## 5. Chia sẻ pointcut giữa nhiều aspect bằng class riêng

Khi nhiều aspect cùng quan tâm một tập method, hãy công bố một named pointcut trong class chuyên trách và tham chiếu bằng tên đầy đủ:

```java
public class CommonPointcuts {
    @Pointcut("execution(public * com.example.shop.service..*.*(..))")
    public void serviceOperation() {}
}

// Bên trong một @Aspect bean:
@Before("com.example.shop.aop.CommonPointcuts.serviceOperation()")
public void audit() {}
```

Hai mảnh code minh họa nằm ở các file/package tương ứng. Class chỉ chứa khai báo `@Pointcut` không cần là Spring bean; class chứa advice phải là aspect bean. Với pointcut dùng liên package, declaring class và method cần truy cập được (thường là `public`). Bài **“Chia sẻ pointcut giữa các aspect”** đi sâu vào hợp đồng dùng chung, nhiều advice và cách kiểm thử.

## 6. Ghép named pointcuts bằng `&&`, `||`, `!`

Các named pointcuts có thể kết hợp bằng `&&` (giao), `||` (hợp) và `!` (phủ định). Ví dụ:

```java
@Pointcut("within(com.example.shop.service..*)")
private void serviceLayer() {}

@Pointcut("execution(public * *(..))")
private void publicMethod() {}

@Pointcut("serviceLayer() && publicMethod()")
private void publicServiceMethod() {}
```

Trong ví dụ, advice tham chiếu `publicServiceMethod()` chỉ chọn public method executions thuộc service layer; visibility `private` của các method khai báo pointcut không phải điều kiện match. Khi kết hợp AND/OR phức tạp, dùng ngoặc nhóm và test các ca chỉ thỏa một vế. Bài **“Kết hợp pointcut theo tầng kiến trúc hoặc nhóm chức năng”** phân tích các kiểu nhóm, ví dụ runnable và kết quả matching.

## 7. Binding tham số qua named pointcut

Một named pointcut có thể khai báo **tham số hình thức** để đưa dữ liệu của invocation vào advice. Ví dụ:

```java
@Pointcut(
    value = "execution(public * com.example.shop.OrderService.submit(..))"
          + " && args(orderId)",
    argNames = "orderId"
)
public void submittingOrder(String orderId) {}

@Before(
    value = "submittingOrder(orderId)",
    argNames = "orderId"
)
public void traceSubmission(String orderId) {
    System.out.println("submitting " + orderId);
}
```

Đây là **fragment trong `@Aspect`**. Nó khác named pointcut không tham số:

- `args(orderId)` là **binding expression**, yêu cầu một đối số phù hợp và bind giá trị thực tế.
- Method `submittingOrder(String orderId)` khai báo kiểu của đối số được bind.
- `submittingOrder(orderId)` trong `@Before` **truyền binding theo tên**, không phải gọi một Java method trả `void`.
- `argNames` làm rõ tên tham số cho Spring/AspectJ binding và giảm phụ thuộc compiler parameter metadata.
- Advice nhận **giá trị đối số của invocation**, không nhận một object do `@Pointcut` tạo ra.

Nếu method `submit(String orderId)` được gọi từ client qua Spring proxy với `"A-01"`, `traceSubmission` nhận `"A-01"`.

Một pointcut có tham số phải được tham chiếu với **đúng số lượng binding arguments**. Đừng dùng `submittingOrder()` cho một pointcut signature yêu cầu `String orderId`, hoặc khai báo sai tên trong `argNames`.

### `args` lọc kiểu runtime, không phải kiểm tra giá trị

`args(orderId)` có khả năng match theo type của advice parameter; nó không kiểm tra `orderId.startsWith("VIP")` chỉ bởi có chữ `orderId`. Nếu nghiệp vụ cần chọn giá trị đặc biệt, đặt điều kiện đó trong advice/business policy phù hợp, và kiểm thử kết quả.

### Trường hợp nhiều tham số

```java
@Pointcut(
    value = "execution(public * com.example.shop.OrderService.submit(..))"
          + " && args(orderId, request)",
    argNames = "orderId,request"
)
public void submittingOrder(String orderId, OrderRequest request) {}
```

Đây là **fragment** cần class `OrderRequest` và method tương ứng, không phải project độc lập. Method `submit` cần có hai arguments phù hợp để expression khớp, hoặc phải điều chỉnh `args(...)` bằng dấu `..` nếu muốn cho phép nhiều arguments hơn.

Đặc biệt với `null`, primitive/boxing, varargs hoặc generic collection, hãy test trực tiếp trên Spring version của ứng dụng vì runtime/type-matching có các quy tắc mà một biểu thức tên ngắn không thể thay thế.

## 8. Ví dụ Java 17+ hoàn chỉnh: dùng `@Pointcut` chia sẻ giữa các advice

Ví dụ Maven sau đây chạy Spring Framework thuần và cố ý tách named pointcut ra **class `CommonPointcuts`** để thể hiện khả năng tái sử dụng từ một `@Aspect` khác.

Chọn phiên bản minh họa: **JDK 17+, Spring Framework 6.1.14, AspectJ weaver 1.9.22.1**. Đây là một bộ version mẫu nhất quán, không khẳng định là mới nhất.

Yêu cầu quan sát:

1. `CommonPointcuts.orderSubmissions()` ghép hai pointcuts nhỏ.
2. `CommonPointcuts.submissionWithId(String)` bind tham số từ lời gọi thật.
3. `AuditAspect` khai báo `@Before` cho submission binding và `@AfterReturning` cho pointcut có tên chung.
4. Gọi `OrderService.submit(...)` qua Spring AOP proxy chạy hai advice đúng trách nhiệm; gọi `preview(...)` không chạy các advice.
5. `@Pointcut` methods có thân rỗng; không có output nào từ chúng.

### 8.1. Cấu trúc thư mục

```text
spring-named-pointcuts/
├── pom.xml
└── src/main/java/com/example/named/
    ├── AppConfig.java
    ├── OrderService.java
    ├── CommonPointcuts.java
    ├── AuditAspect.java
    └── DemoApp.java
```

### 8.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>spring-named-pointcuts</artifactId>
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

`spring-context` cung cấp ApplicationContext và các module Spring cơ bản (bao gồm Spring AOP trong dependency graph bản mẫu); `aspectjweaver` cung cấp pointcut parser/matching cho `@AspectJ`. Không có AspectJ compiler hoặc weaving agent: đây là **Spring proxy-based AOP**.

### 8.3. `src/main/java/com/example/named/OrderService.java`

```java
package com.example.named;

public class OrderService {
    public String submit(String orderId) {
        System.out.println("[SERVICE] submit " + orderId);
        return "accepted:" + orderId;
    }

    public String preview(String orderId) {
        System.out.println("[SERVICE] preview " + orderId);
        return "preview:" + orderId;
    }
}
```

Class `OrderService` là một target bean với hai public methods. Pointcut bài này cố tình chỉ chọn `submit`, không chọn `preview`.

### 8.4. `src/main/java/com/example/named/CommonPointcuts.java`

```java
package com.example.named;

import org.aspectj.lang.annotation.Pointcut;

public class CommonPointcuts {

    @Pointcut("execution(public * com.example.named.OrderService.*(..))")
    public void orderServiceMethods() {}

    @Pointcut("execution(public * com.example.named.OrderService.submit(..))")
    public void submitMethods() {}

    @Pointcut("orderServiceMethods() && submitMethods()")
    public void orderSubmissions() {}

    @Pointcut(
        value = "orderSubmissions() && args(orderId)",
        argNames = "orderId"
    )
    public void submissionWithId(String orderId) {}
}
```

**Tại sao tổ chức như vậy?**

- `orderServiceMethods()` mô tả phạm vi target class.
- `submitMethods()` mô tả thao tác nghiệp vụ.
- `orderSubmissions()` là **phép giao** của hai điều kiện, giúp nơi sử dụng không cần biết chi tiết expression.
- `submissionWithId(String)` đặt tên cho pointcut có thêm **binding argument**.
- Class `CommonPointcuts` chỉ chứa public named pointcuts dùng chung. Nó không phải aspect bean và **không cần đăng ký thành bean**.

`orderServiceMethods()` có thể khớp cả `submit` và `preview`; `orderSubmissions()` chỉ khớp `submit`. Về mặt demo, hai điều kiện có chủ ý dễ quan sát dù có thể gộp thành một expression ngắn hơn.

### 8.5. `src/main/java/com/example/named/AuditAspect.java`

```java
package com.example.named;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    private final AtomicInteger beforeCount = new AtomicInteger();
    private final AtomicInteger successCount = new AtomicInteger();

    @Before(
        value = "com.example.named.CommonPointcuts.submissionWithId(orderId)",
        argNames = "orderId"
    )
    public void beforeSubmit(String orderId) {
        beforeCount.incrementAndGet();
        System.out.println("[AOP before] " + orderId);
    }

    @AfterReturning(
        pointcut = "com.example.named.CommonPointcuts.orderSubmissions()",
        returning = "result",
        argNames = "result"
    )
    public void afterSuccess(String result) {
        successCount.incrementAndGet();
        System.out.println("[AOP success] " + result);
    }

    public int beforeCount() {
        return beforeCount.get();
    }

    public int successCount() {
        return successCount.get();
    }
}
```

Ở đây một named pointcut **có tham số** cung cấp `orderId` cho `@Before`, còn named pointcut **không tham số** được dùng cho `@AfterReturning`. Chúng đều chọn `submit` theo ý đồ nhưng lấy dữ liệu khác nhau:

- `@Before`: lấy **input** `orderId`, trước lúc target method chạy.
- `@AfterReturning`: lấy **output** thông qua `returning="result"`, sau khi target method kết thúc bình thường.
- Hai counters đo đúng số lần từng advice chạy, không đo số lần pointcut body được gọi.

### 8.6. `src/main/java/com/example/named/AppConfig.java`

```java
package com.example.named;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    public OrderService orderService() {
        return new OrderService();
    }

    @Bean
    public AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
```

`@EnableAspectJAutoProxy(proxyTargetClass = true)` bật hỗ trợ Spring `@AspectJ` và dùng class-based proxy để `OrderService` không cần khai báo interface. `AuditAspect` được đăng ký bằng `@Bean`, bởi `@Aspect` **không tự biến class thành một bean Spring**. `CommonPointcuts` chỉ là class định nghĩa pointcut metadata và không cần `@Bean`.

### 8.7. `src/main/java/com/example/named/DemoApp.java`

```java
package com.example.named;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApp {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            OrderService service = context.getBean(OrderService.class);
            AuditAspect aspect = context.getBean(AuditAspect.class);

            System.out.println("isAopProxy=" + AopUtils.isAopProxy(service));

            String result = service.submit("ORD-1");
            System.out.println("submitResult=" + result);

            String preview = service.preview("ORD-2");
            System.out.println("previewResult=" + preview);

            System.out.println("beforeCount=" + aspect.beforeCount());
            System.out.println("successCount=" + aspect.successCount());
        }
    }
}
```

### 8.8. Chạy ví dụ và kiểm tra stdout

Yêu cầu: JDK 17+, Maven có thể tải dependencies. Tại thư mục chứa `pom.xml`, chạy:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.named.DemoApp
```

Những dòng do code của ví dụ in ra theo thứ tự (không tính log Maven/SLF4J nếu có):

```text
isAopProxy=true
[AOP before] ORD-1
[SERVICE] submit ORD-1
[AOP success] accepted:ORD-1
submitResult=accepted:ORD-1
[SERVICE] preview ORD-2
previewResult=preview:ORD-2
beforeCount=1
successCount=1
```

**Giải thích:**

1. `isAopProxy=true`: auto-proxy creator đã áp dụng advisors từ `AuditAspect` lên `OrderService`.
2. Với `submit("ORD-1")`, `orderSubmissions()` đúng và `submissionWithId(orderId)` bind `"ORD-1"`. `@Before` in dòng đầu tiên.
3. Target in `[SERVICE] submit ...`, sau đó `@AfterReturning` in kết quả trả về.
4. `preview("ORD-2")` thuộc `orderServiceMethods()` nhưng **không thuộc `submitMethods()`**, nên phép giao `orderSubmissions()` là false: không có dòng `[AOP]` cho preview.
5. Hai counters đều bằng 1. Spring không phải chạy body của `CommonPointcuts.orderSubmissions()` mới xác định match.
6. `@AfterReturning` chỉ chạy nếu target method **trả về bình thường**. Nếu target ném exception, advice này không chạy; cần chính sách xử lý ngoại lệ riêng nếu ứng dụng yêu cầu.

Ví dụ hoàn chỉnh này ưu tiên kết quả dễ kiểm tra: **một business method được chọn, một method không được chọn, hai advice tái sử dụng pointcut có tên theo hai cách**.

## 9. Tham chiếu named pointcut ở aspect khác hoặc XML

### 9.1. Nhiều aspects tham chiếu cùng class

Giả sử `CommonPointcuts` có `public void orderSubmissions()`. Aspect audit và aspect metrics có thể cùng tham chiếu:

```java
@Before("com.example.named.CommonPointcuts.orderSubmissions()")
public void auditCall() {
    // ...
}
```

```java
@Around("com.example.named.CommonPointcuts.orderSubmissions()")
public Object timeCall(org.aspectj.lang.ProceedingJoinPoint pjp)
        throws Throwable {
    long start = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        long elapsed = System.nanoTime() - start;
        // Gửi elapsed vào metric recorder.
    }
}
```

Hai đoạn trên là **method fragments** thuộc các class `@Aspect` tương ứng (không chạy độc lập). Chúng chỉ tái sử dụng **quy tắc chọn method**; từng aspect vẫn chịu trách nhiệm cho logic riêng. Khi chạy cùng lúc, nhiều advices cùng chọn một method là bình thường, nhưng cần kiểm tra thứ tự khi có dependency giữa advices.

### 9.2. Tham chiếu từ Spring XML advisor

Tài liệu Spring cũng chỉ ra một named pointcut public có thể được tham chiếu bằng tên đầy đủ trong `<aop:advisor>`, ví dụ:

```xml
<aop:config>
    <aop:advisor
        pointcut="com.example.named.CommonPointcuts.orderSubmissions()"
        advice-ref="someAdvice"/>
</aop:config>
```

Đây là **fragment XML**, cần file có namespace `aop` và bean `someAdvice` implements Spring `Advice` phù hợp. Nó minh họa khả năng tái sử dụng định nghĩa pointcut, **không có nghĩa bạn phải bật đồng thời XML advisor và Java advice cho cùng một hành vi**.

Nếu ứng dụng đang chuyển đổi từ XML sang Java configuration, kiểm tra **trùng chức năng advice** và bean registration; Spring có cơ chế hợp nhất auto-proxy creators chuẩn, nhưng không tự loại bỏ hai advice khác nhau chỉ vì dùng cùng named pointcut.

## 10. Những lỗi phổ biến khi dùng `@Pointcut`

| Triệu chứng | Nguyên nhân thường gặp | Cách kiểm tra/sửa |
| --- | --- | --- |
| Viết logic trong method `@Pointcut` nhưng không chạy | Named method chỉ là signature/metadata, không phải advice callback. | Đưa logic vào `@Before`, `@Around`, `@AfterReturning`... |
| Đặt method `@Pointcut` trả `boolean` hoặc `String` | Signature sai; named pointcut phải trả `void`. | Đổi sang `void` với body rỗng. |
| Advice dùng `@Before("serviceOperation")` | Thiếu `()` ở tên pointcut không tham số. | Viết `@Before("serviceOperation()")`. |
| Aspect khác không tìm thấy named pointcut | Method `private`, thiếu fully-qualified class name hoặc sai package/type. | Dùng method `public` và `com.example.CommonPointcuts.name()` khi chia sẻ. |
| `args(id)` không bind đúng | Signature pointcut và `argNames` / advice parameter không đồng bộ. | Đối chiếu tên, thứ tự, kiểu và số arguments; thêm `argNames` rõ ràng. |
| Advice chạy với nhiều service không mong muốn | Named pointcut quá rộng, ví dụ `execution(* *(..))`. | Hạn chế package/class/method trong expression rồi ghép các điều kiện. |
| Khai báo `@Pointcut` nhưng không có advice nào chạy | Aspect không phải bean, Spring AOP chưa bật, pointcut không match hoặc caller bypass proxy. | Xác minh `@Bean`/`@Component`, `@EnableAspectJAutoProxy`, `AopUtils` và đường gọi. |
| Tưởng `@Pointcut` đặt trong class riêng phải là bean | Nhầm metadata definition với component lifecycle. | Named pointcut container không cần là bean để được tham chiếu theo type. |
| Thay `private` thành `public` làm pointcut “bắt” thêm methods | Nhầm visibility của pointcut method với visibility của target method. | Đặt `public` trong `execution(public ...)`, không dựa vào Java modifier của pointcut method. |
| Lấy tên `@Pointcut` nhưng thiếu `()` khi ghép | Pointcut reference không hợp lệ. | Viết `inServiceLayer() && publicMethod()`. |
| Dùng `@Pointcut` có `call(...)`/field access | Spring proxy AOP không hỗ trợ AspectJ call/get/set join points. | Dùng supported designators, chủ yếu `execution`, cho method execution qua Spring proxy. |
| Advice không chạy do self-invocation | Lời gọi `this.method()` từ target bypass proxy. | Tách business boundary sang bean khác và gọi qua proxy; named pointcut không sửa self-invocation. |
| Hai advice cùng chạy trên method và tưởng pointcut bị nhân đôi | Hai advice độc lập đều tham chiếu cùng named pointcut. | Kiểm tra advisors/bean instances và expected advice count theo từng chính sách. |

## 11. Thiết kế tên và vị trí pointcut dễ bảo trì

Một tên tốt mô tả **tập lời gọi cần chọn**: `serviceOperation()`, `orderSubmission()`, `auditedPaymentOperation()`. Tên `pointcut1()` hoặc tên chỉ mô phỏng ký tự `execution(...)` khiến nơi dùng khó hiểu mục đích. Tránh tên hứa hẹn bảo mật khi pointcut chỉ chọn method, không tự kiểm tra quyền.

Định nghĩa dùng riêng cho một aspect có thể giữ `private`; định nghĩa chia sẻ nên có class sở hữu và public API rõ. Đổi tên named pointcut hoặc package của class chứa nó phải rà mọi chuỗi tham chiếu trong advice/XML. Bài **“Đặt tên theo ý định thay vì chi tiết chuỗi biểu thức”** hướng dẫn đánh giá ý định và giữ tập match sau thay đổi.

## 12. Kiểm thử named pointcuts trong Spring

Để chứng minh một named pointcut hoạt động, hãy khởi tạo Spring context đã bật AOP, lấy target từ context và gọi qua proxy. Chạy ít nhất **một method phải match** và **một method không được match**; xác nhận advice event/counter thay đổi chính xác, đồng thời target trả kết quả mong đợi. Với `A && B`, cần các ca chỉ thỏa A, chỉ thỏa B và thỏa cả hai.

Đối chứng `this.method()` và đối tượng tạo bằng `new` khi kiến trúc có nguy cơ bypass proxy. `AopUtils.isAopProxy(bean)` chỉ cho biết bean có proxy, không chứng minh advice cụ thể đã chạy. Bài **“Kiểm thử ca được khớp và ca không được khớp”** có ma trận và JUnit integration tests đầy đủ.

## 13. Tóm tắt nhanh và tài liệu chính thức

| Câu hỏi | Câu trả lời |
| --- | --- |
| Named pointcut khai báo bằng gì? | Method `void` với annotation `@Pointcut("expression")`. |
| Thân method `@Pointcut` có chạy khi match? | Không; Spring sử dụng tên/signature và annotation metadata. |
| Tham chiếu trong cùng aspect thế nào? | `@Before("namedPointcut()")` hoặc advice khác tương đương. |
| Tham chiếu từ class khác thế nào? | `com.example.CommonPointcuts.namedPointcut()` với Java visibility phù hợp. |
| Kết hợp được điều kiện không? | Có: `&&`, `||`, `!`; đặt tên cho các biểu thức nhỏ. |
| Truyền argument vào advice được không? | Có: `args(name)`, formal parameter và `argNames` đúng. |
| Có cần `CommonPointcuts` là Spring bean không? | Không, nếu class chỉ chứa các named pointcut definitions dùng để tham chiếu. |
| `@Pointcut` có giải quyết self-invocation không? | Không; Spring AOP vẫn proxy-based. |

Tài liệu Spring Framework:

- Declaring a Pointcut: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Declaring Advice (Binding Parameters to Advice): https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html

**Kết luận:** `@Pointcut` biến điều kiện chọn lời gọi phương thức trong Spring AOP thành **một định nghĩa có tên, có thể tái sử dụng và kết hợp**. Named pointcut mô tả **nơi áp dụng**, còn advice chứa **logic được thực thi**; để advice chạy, target vẫn phải là Spring bean và lời gọi phải đi qua proxy phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reusable-pointcuts-002">Kết hợp pointcut theo tầng kiến trúc hoặc nhóm chức năng</a>

<details>
<summary>Xem chi tiết</summary>

# Kết hợp pointcut theo tầng kiến trúc hoặc nhóm chức năng trong Spring AOP

## 1. Mục tiêu tổ chức pointcut

Trong ứng dụng Spring, một `@Aspect` thường chỉ được phép can thiệp một phần hệ thống. Ví dụ, một aspect ghi log lời gọi thuộc **tầng service**, aspect khác đo thời gian cho **repository**, còn audit chỉ nhắm tới những **lệnh ghi dữ liệu**. Nếu mỗi advice sao chép trực tiếp một biểu thức dài, việc đổi package, quy tắc đặt tên hoặc annotation sẽ dễ gây lệch hành vi giữa các aspect.

Giải pháp của Spring AOP là **định nghĩa các named pointcut nhỏ có ý nghĩa, sau đó kết hợp chúng theo phép logic**. Một pointcut mô tả tầng kiến trúc, một pointcut mô tả nhóm chức năng, một pointcut mô tả điều kiện method. Các pointcut được hợp thành quy tắc như “public command trong service” và được tái sử dụng từ nhiều aspect.

Ví dụ đọc như một câu:

```java
@Pointcut("serviceLayer() && commandMethod()")
public void serviceCommand() {}
```

`serviceCommand()` có nghĩa method execution **thuộc service layer VÀ thuộc nhóm command**. Tách các điều kiện giúp dễ đọc và dễ sửa hơn nhồi tất cả vào một `@Before` hoặc `@Around`.

**Phạm vi:** Tổ chức pointcut trong **Spring Framework với `@AspectJ` và Spring AOP proxy-based**. Bài không giảng lại toàn bộ AspectJ độc lập: mọi biểu thức ở đây chỉ có ý nghĩa khi Spring phát hiện aspect, Spring bean có proxy, method được gọi qua proxy và pointcut khớp.

### Kết quả học tập

Sau bài học, bạn có thể:
- Phân loại pointcut theo **tầng kiến trúc**, **nhóm chức năng**, **kiểu method** và **metadata**.
- Dùng `&&`, `||`, `!` với ngoặc để tổ hợp phạm vi chính xác.
- Tổ chức một lớp `CommonPointcuts` dùng chung giữa các `@Aspect`.
- Phân biệt `within`, `execution`, `bean`, `@annotation` trong việc phân nhóm.
- Chạy ví dụ Spring AOP hoàn chỉnh với service, package con, repository và hai aspect.
- Kiểm thử điều kiện khớp/không khớp, xử lý self-invocation và tránh advice chạy trùng ngoài dự kiến.

## 2. Trục kiến trúc và trục chức năng khác nhau thế nào?

Một cấu trúc giả định:

```text
com.example.shop
├── web/
│   └── OrderController
├── service/
│   ├── CheckoutService
│   └── pricing/PricingService
├── repository/
│   └── OrderRepository
└── aop/
    ├── ArchitecturePointcuts
    ├── AuditAspect
    └── TraceAspect
```

| Trục | Câu hỏi | Ví dụ Spring AOP |
| --- | --- | --- |
| Tầng kiến trúc | Type/method ở package nào? | `within(com.example.shop.service..*)`, `within(com.example.shop.repository..*)` |
| Nhóm chức năng | Thao tác có phải đọc, ghi, thanh toán, quản trị? | `execution(public * load*(..))`, `execution(public * place*(..))` |
| Chữ ký | Method public, return type hoặc tham số nào? | `execution(public * *(..))` |
| Annotation | Method hoặc target type có marker nghiệp vụ? | `@annotation(com.example.shop.aop.Audited)` |
| Tên bean | Spring bean có naming convention nào? | `bean(*Service)` |

Ví dụ, `CheckoutService` thuộc service layer nhưng có cả `loadOrder` (query) và `placeOrder` (command). `OrderRepository` thuộc repository, có thể có method `save` mà tên cũng biểu thị ghi dữ liệu. Nếu mục tiêu là “audit command ở service”, không nên dùng riêng `within(service..*)`, vì như vậy sẽ audit cả query. Cũng không nên dùng riêng `execution(* place*(..))` nếu có những method tên như vậy ở layer không liên quan.

**Điểm thiết kế cốt lõi:** Một pointcut mô tả **lớp đối tượng quan tâm**, pointcut khác mô tả **thao tác cần quan sát**. Dùng AND để lấy giao của hai tập.

## 3. Kết hợp với AND, OR, NOT

### 3.1. AND — `&&`

```java
@Pointcut("within(com.example.shop.service..*)")
public void serviceLayer() {}

@Pointcut("execution(public * *(..))")
public void publicOperation() {}

@Pointcut("serviceLayer() && publicOperation()")
public void publicServiceOperation() {}
```

`publicServiceOperation` yêu cầu method vừa thuộc service layer vừa public. Dấu `..` trong `service..*` bao gồm package `service` và các package con (ví dụ `service.pricing`). `service.*` chỉ chọn type trực tiếp trong package đó.

Một yêu cầu khác:

```java
@Pointcut("execution(public * load*(..))")
public void queryMethod() {}

@Pointcut("serviceLayer() && queryMethod()")
public void serviceQuery() {}
```

`queryMethod()` được định nghĩa qua naming convention; Spring không tự biết method có side effect hay không. `serviceQuery()` giới hạn riêng các method `load...` thuộc service.

### 3.2. OR — `||`

```java
@Pointcut("within(com.example.shop.service..*)")
public void serviceLayer() {}

@Pointcut("within(com.example.shop.repository..*)")
public void repositoryLayer() {}

@Pointcut("serviceLayer() || repositoryLayer()")
public void applicationLayer() {}
```

`applicationLayer` chọn method execution ở **service hoặc repository**. Đây là hợp của hai tập. Nó phù hợp khi muốn ghi metric cho cả hai layer, không phù hợp nếu chỉ muốn các method `save` của repository.

### 3.3. NOT — `!`

```java
@Pointcut("within(com.example.shop.service..*)")
public void serviceLayer() {}

@Pointcut("within(com.example.shop.service.internal..*)")
public void internalServicePackage() {}

@Pointcut("serviceLayer() && !internalServicePackage()")
public void serviceExceptInternal() {}
```

Phủ định nên đi kèm **phạm vi dương**. `!within(service.internal..*)` đứng một mình có thể bao gồm gần như tất cả các bean khác trong ứng dụng.

### 3.4. Dấu ngoặc khi kết hợp nhiều điều kiện

```java
@Pointcut("(serviceLayer() || repositoryLayer())"
        + " && execution(public * *(..))"
        + " && !within(com.example.shop.internal..*)")
public void visibleApplicationOperations() {}
```

Ý nghĩa: **(service HOẶC repository)** VÀ **public method** VÀ **không nằm trong package internal**. Dấu ngoặc thể hiện chủ đích rõ ràng và giúp review tránh nhầm precedence.

**Nhớ rằng** phép logic xác định những method execution được chọn; nó không quyết định thứ tự chạy nhiều advice. Thứ tự advice thuộc `@Order`/`Ordered` và cơ chế advisor ordering của Spring.

## 4. Named pointcut để dùng lại

Đặt các điều kiện theo lớp chức năng/tầng thành pointcut nhỏ rồi tham chiếu bằng tên trong điểm cắt tổng hợp:

```java
public class ArchitecturePointcuts {
    @Pointcut("within(com.example.shop.service..*)")
    public void serviceLayer() {}

    @Pointcut("execution(public * place*(..))")
    public void commandMethod() {}

    @Pointcut("serviceLayer() && commandMethod()")
    public void serviceCommand() {}
}
```

Trong aspect bean, `@Before("com.example.shop.aop.ArchitecturePointcuts.serviceCommand()")` chỉ chọn public `place*` tại service layer. Đây là đoạn code của lớp chuyên trách, cần đặt tại package đã nêu. Public named pointcut giúp tái sử dụng giữa aspects; các `@Pointcut` methods không chạy logic lúc match. Chi tiết cấu trúc thư viện và hợp đồng chia sẻ nằm ở bài **“Chia sẻ pointcut giữa các aspect”**.

## 5. Chọn pattern theo kiến trúc Spring thật

### 5.1. Package scope bằng within

```java
@Pointcut("within(com.example.shop.web..*)")
public void webLayer() {}

@Pointcut("within(com.example.shop.service..*)")
public void serviceLayer() {}

@Pointcut("within(com.example.shop.repository..*)")
public void repositoryLayer() {}
```

`within` trong Spring AOP mô tả method execution được khai báo trong matching type. Cẩn thận với method **kế thừa từ superclass nằm ngoài package**: tiêu chí declaring type và runtime target type không hoàn toàn giống nhau. Nếu ý nghĩa thực sự là “bean triển khai service interface”, một `target(ServiceInterface)` hoặc `execution(* ServiceInterface.*(..))` có thể chính xác hơn, tùy kiến trúc.

### 5.2. Nhóm dựa trên chữ ký method

```java
@Pointcut("execution(public * load*(..))"
        + " || execution(public * find*(..))")
public void queryMethods() {}

@Pointcut("execution(public * place*(..))"
        + " || execution(public * cancel*(..))")
public void commandMethods() {}
```

Đây là convention do nhóm phát triển đề ra, không phải ngữ nghĩa tự động do Spring gán. Method `loadAndMutate` có side effect vẫn match nhóm query nếu tên bắt đầu `load`. Đối với audit yêu cầu chính xác hơn, dùng marker annotation có review/test rõ ràng.

### 5.3. Nhóm qua annotation

Giả sử đã định nghĩa `@AuditedCommand` với `@Target(ElementType.METHOD)` và `@Retention(RetentionPolicy.RUNTIME)`. Có thể viết:

```java
@Pointcut("serviceLayer()"
        + " && @annotation(com.example.shop.aop.AuditedCommand)")
public void auditedServiceCommand() {}
```

Điều kiện này chọn annotation trên **method** chứ không phải package hay tên method. Nó tách phân loại command khỏi naming convention, nhưng vị trí đặt annotation trên interface/implementation và lựa chọn JDK/CGLIB proxy cần được kiểm tra trên runtime.

Phân biệt `@annotation` (annotation trên method) với `@within` (trên declaring type), `@target` (trên runtime target type) và `@args` (trên runtime type của actual arguments).

### 5.4. Nhóm theo bean name

Spring AOP có `bean(*Service)`: chọn Spring beans theo **tên**, ví dụ `orderService` hay `pricingService`, khi naming convention phù hợp. Nó không giống `within(...service..*)` xét type/package. Khi bean được đặt tên `paymentGateway` dù class tên `PaymentService`, biểu thức `bean(*Service)` có thể không khớp. `bean` là Spring-specific designator, không phải cú pháp portable cho AspectJ weaving độc lập.

### 5.5. Các bounded contexts khác nhau

Khi code có `com.example.orders.service`, `com.example.catalog.service` và `com.example.billing.service`, có thể dùng một scope pattern như `within(com.example..service..*)` **chỉ khi** package naming thật sự ổn định. Nếu muốn audit chỉ orders, hãy định nghĩa thêm `ordersServiceLayer()` thay vì mở rộng chung tới tất cả bounded contexts.

## 6. Ví dụ Java 17 chạy độc lập trên Spring Framework

Ví dụ gồm hai Spring aspects dùng chung `ArchitecturePointcuts`. Nó minh họa:

1. Audit chỉ các **service commands**.
2. Trace chỉ các **service queries** và **repository operations**.
3. Service ở package con vẫn khớp `within(demo.service..*)`.
4. Self-invocation không tạo thêm một lần advice, dù method nội bộ sẽ khớp nếu được gọi qua proxy.

**Môi trường:** Java 17+, Spring Framework `6.1.14`, Gradle và AspectJ Weaver `1.9.22.1`. Đây là ứng dụng Spring Framework thuần, không dùng Spring Boot auto-configuration.

### 6.1. File và cấu hình Gradle

```text
spring-aop-pointcut-composition/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/
    ├── DemoApplication.java
    ├── config/AppConfig.java
    ├── aop/ArchitecturePointcuts.java
    ├── aop/AuditAspect.java
    ├── aop/TraceAspect.java
    ├── service/CheckoutService.java
    ├── service/pricing/PricingService.java
    └── repository/OrderRepository.java
```

`settings.gradle`:

```groovy
rootProject.name = 'spring-aop-pointcut-composition'
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
    mainClass = 'demo.DemoApplication'
}
```

`spring-context` cung cấp Spring IoC và `AnnotationConfigApplicationContext`; `spring-aop` cung cấp auto-proxy infrastructure; `aspectjweaver` hỗ trợ Spring phân tích biểu thức `@AspectJ`.

### 6.2. Lớp chia sẻ named pointcuts

`src/main/java/demo/aop/ArchitecturePointcuts.java`:

```java
package demo.aop;

import org.aspectj.lang.annotation.Pointcut;

public class ArchitecturePointcuts {

    @Pointcut("within(demo.service..*)")
    public void serviceLayer() {}

    @Pointcut("within(demo.repository..*)")
    public void repositoryLayer() {}

    @Pointcut("execution(public * *(..))")
    public void publicOperation() {}

    @Pointcut("execution(public * load*(..))")
    public void queryOperation() {}

    @Pointcut("execution(public * place*(..))")
    public void commandOperation() {}

    @Pointcut("serviceLayer() && queryOperation()")
    public void serviceQuery() {}

    @Pointcut("serviceLayer() && commandOperation()")
    public void serviceCommand() {}

    @Pointcut("repositoryLayer() && publicOperation()")
    public void repositoryOperation() {}
}
```

`ArchitecturePointcuts` chỉ cung cấp definitions; **không cần là một Spring bean**. Named pointcut `serviceQuery` là AND của tầng service và method query; `serviceCommand` là AND của tầng service và command; `repositoryOperation` là AND của tầng repository và public method.

### 6.3. Target ở package service

`src/main/java/demo/service/CheckoutService.java`:

```java
package demo.service;

public class CheckoutService {

    public void placeOrder(String id) {
        System.out.println("target:placeOrder:" + id);
    }

    public void loadOrder(String id) {
        System.out.println("target:loadOrder:" + id);
    }

    public void placeWithInternalLoad(String id) {
        System.out.println("target:placeWithInternalLoad:" + id);
        loadOrder(id);
    }
}
```

`placeOrder` và `placeWithInternalLoad` là command theo prefix `place`; `loadOrder` là query theo prefix `load`. Khi `placeWithInternalLoad` gọi `loadOrder` trực tiếp, đó là self-invocation trên `this`: Spring AOP proxy không intercept lời gọi nội bộ.

### 6.4. Target ở package service con

`src/main/java/demo/service/pricing/PricingService.java`:

```java
package demo.service.pricing;

public class PricingService {

    public void loadPrice(String sku) {
        System.out.println("target:loadPrice:" + sku);
    }
}
```

`demo.service.pricing` khớp `within(demo.service..*)` nhờ `..*`, và `loadPrice` khớp `queryOperation`.

### 6.5. Target ở repository

`src/main/java/demo/repository/OrderRepository.java`:

```java
package demo.repository;

public class OrderRepository {

    public void save(String id) {
        System.out.println("target:save:" + id);
    }
}
```

Repository nằm ngoài service package, nhưng method `save` là public và khớp `repositoryOperation`. Nó không bị `serviceCommand` chọn chỉ vì tên có ý nghĩa thao tác ghi.

### 6.6. Hai aspect tham chiếu cùng pointcut library

`src/main/java/demo/aop/AuditAspect.java`:

```java
package demo.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {

    @Before("demo.aop.ArchitecturePointcuts.serviceCommand()")
    public void beforeCommand(JoinPoint joinPoint) {
        System.out.println(
            "audit:" + joinPoint.getSignature().getName());
    }
}
```

`src/main/java/demo/aop/TraceAspect.java`:

```java
package demo.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {

    @Before("demo.aop.ArchitecturePointcuts.serviceQuery()")
    public void beforeQuery(JoinPoint joinPoint) {
        System.out.println(
            "trace:query:" + joinPoint.getSignature().getName());
    }

    @Before("demo.aop.ArchitecturePointcuts.repositoryOperation()")
    public void beforeRepository(JoinPoint joinPoint) {
        System.out.println(
            "trace:repository:" + joinPoint.getSignature().getName());
    }
}
```

Lớp `AuditAspect` dùng nhóm command, `TraceAspect` dùng query và repository. Chúng cùng tham chiếu named pointcuts ở một nơi; không cần copy `within(...)` vào từng advice.

### 6.7. Cấu hình Spring bean

`src/main/java/demo/config/AppConfig.java`:

```java
package demo.config;

import demo.aop.AuditAspect;
import demo.aop.TraceAspect;
import demo.repository.OrderRepository;
import demo.service.CheckoutService;
import demo.service.pricing.PricingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public CheckoutService checkoutService() {
        return new CheckoutService();
    }

    @Bean
    public PricingService pricingService() {
        return new PricingService();
    }

    @Bean
    public OrderRepository orderRepository() {
        return new OrderRepository();
    }

    @Bean
    public AuditAspect auditAspect() {
        return new AuditAspect();
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }
}
```

`@EnableAspectJAutoProxy` bật hạ tầng đọc advisor từ `@Aspect`. Hai aspect được tạo thành Spring beans qua `@Bean`; pointcut-only class không được đăng ký. `proxyTargetClass=true` giúp ba target class không có interface vẫn được Spring AOP proxy bằng lớp con (CGLIB). Trong một ứng dụng có interface, có thể dùng JDK proxy nếu cấu hình phù hợp.

### 6.8. Main và stdout

`src/main/java/demo/DemoApplication.java`:

```java
package demo;

import demo.config.AppConfig;
import demo.repository.OrderRepository;
import demo.service.CheckoutService;
import demo.service.pricing.PricingService;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApplication {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            CheckoutService checkout =
                context.getBean(CheckoutService.class);
            PricingService pricing =
                context.getBean(PricingService.class);
            OrderRepository repository =
                context.getBean(OrderRepository.class);

            checkout.placeOrder("O-1");
            checkout.loadOrder("O-1");
            pricing.loadPrice("P-2");
            repository.save("O-1");
            checkout.placeWithInternalLoad("O-2");

            System.out.println("checkoutProxy=" +
                AopUtils.isAopProxy(checkout));
            System.out.println("pricingProxy=" +
                AopUtils.isAopProxy(pricing));
            System.out.println("repositoryProxy=" +
                AopUtils.isAopProxy(repository));
        }
    }
}
```

Lệnh chạy từ thư mục có `build.gradle`:

```bash
gradle run
```

Nếu đã tạo Gradle Wrapper, dùng `./gradlew run` (Linux/macOS) hoặc `gradlew.bat run` (Windows).

**Chỉ tính các dòng do chương trình Java in ra** (không gồm output Gradle/logger):

```text
audit:placeOrder
target:placeOrder:O-1
trace:query:loadOrder
target:loadOrder:O-1
trace:query:loadPrice
target:loadPrice:P-2
trace:repository:save
target:save:O-1
audit:placeWithInternalLoad
target:placeWithInternalLoad:O-2
target:loadOrder:O-2
checkoutProxy=true
pricingProxy=true
repositoryProxy=true
```

### 6.9. Giải thích theo ma trận pointcut

| Method invocation | Layer match | Nhóm chức năng | Kết quả |
| --- | --- | --- | --- |
| `checkout.placeOrder` | `serviceLayer` | `commandOperation` | `audit:placeOrder` trước target |
| `checkout.loadOrder` từ main | `serviceLayer` | `queryOperation` | `trace:query:loadOrder` trước target |
| `pricing.loadPrice` | `serviceLayer` (subpackage) | `queryOperation` | `trace:query:loadPrice` trước target |
| `repository.save` | `repositoryLayer` | `publicOperation` | `trace:repository:save` trước target |
| `checkout.placeWithInternalLoad` | `serviceLayer` | `commandOperation` | `audit:placeWithInternalLoad` trước target |
| `loadOrder` gọi nội bộ từ `placeWithInternalLoad` | Lẽ ra khớp query về type/signature | Nhưng là `this.loadOrder` | Không có advice query lần hai |

Ba dòng `...Proxy=true` xác nhận ba Spring beans được proxy. Mỗi bean có ít nhất một advisor phù hợp. Tuy nhiên, không phải mọi advice chạy trên mọi method; các điều kiện pointcut khác nhau lựa chọn nhóm invocation khác nhau.

**Kết quả self-invocation:** `target:loadOrder:O-2` có xuất hiện, nhưng không có `trace:query:loadOrder` ngay trước dòng đó, vì lời gọi từ `placeWithInternalLoad` không quay lại Spring proxy. Đây là giới hạn cố hữu của Spring AOP proxy, không sửa được chỉ bằng cách nới rộng `execution` hoặc `within`.

## 7. Khi nào dùng OR/NOT, khi nào tạo pointcut mới?

Giả sử hệ thống bắt đầu có thêm `cancelOrder` và `findOrder`:
- Nếu naming convention ghi dữ liệu là `place*` hoặc `cancel*`, có thể định nghĩa nhóm command bằng OR.
- Nếu query là `load*` hoặc `find*`, có thể định nghĩa nhóm query bằng OR.
- Nếu một package `service.internal` cần loại trừ, dùng AND với phủ định scoped `!within(service.internal..*)`.
- Nếu một nhóm đòi logic riêng (ví dụ chỉ audit nghiệp vụ thanh toán), tạo pointcut có tên riêng như `paymentCommand`. Không áp một “serviceCommand” quá rộng lên mọi quy tắc audit.

Ví dụ thay thế cho method `commandOperation`:

```java
@Pointcut("execution(public * place*(..))"
        + " || execution(public * cancel*(..))")
public void commandOperation() {}
```

Vì `serviceCommand` đã tham chiếu `commandOperation`, không cần sửa tất cả `@Before` trong các aspects. Đây là ích lợi trực tiếp của tổ chức pointcut.

## 8. Sự phối hợp với Spring AOP infrastructure

Một số chi tiết Spring-specific cần nhớ:

1. **Aspect phải là Spring bean.** `@Aspect` không tự thay `@Component` hoặc `@Bean`.
2. **AOP phải bật trong đúng context.** `@EnableAspectJAutoProxy` thuộc `ApplicationContext` chứa nó; context khác không tự được hậu xử lý nhờ context cha.
3. **Auto-proxy creator chọn advisor theo bean.** Nếu service không có method phù hợp hoặc không có advisor khác, Spring không bắt buộc tạo proxy cho nó chỉ vì bạn đã khai báo `@EnableAspectJAutoProxy`.
4. **Spring chỉ chặn method execution qua proxy.** Code gọi `new Service()`, constructor execution, field access hoặc `this.method()` không nhận advice giống lời gọi qua Spring proxy.
5. **Proxy type có hệ quả.** Với JDK dynamic proxy, client thường gọi qua interface; với CGLIB, class/method `final` không được override để advice. Không chọn pointcut dựa trên class implementation rồi mặc định mọi method đều intercept được.
6. **Nhiều aspects có thể match cùng một invocation.** `@Order`/`Ordered` và advisor precedence có ảnh hưởng thứ tự. Những pointcuts chồng nhau có thể cố ý hoặc do lỗi thiết kế.
7. **Spring-specific `bean` designator** hữu ích khi module tổ chức bean theo tên, nhưng không thể coi đó là tương đương `within`/`target`.
8. **Không dùng naming convention như security boundary duy nhất.** Nếu thao tác mang tính an ninh/ủy quyền, có thể cần Spring Security method authorization hoặc validation phù hợp và integration test thực.

## 9. Kiểm thử pointcut: cả trường hợp khớp và không khớp

Với `serviceLayer() && commandMethod()`, kiểm thử theo bảng tình huống: service có `placeOrder()` phải match; service có `loadOrder()` không match điều kiện command; bean ngoài service có `placeOrder()` không match điều kiện layer; method ở `service.pricing` cho biết `..*` có bao quát package con hay không. Chỉ quan sát proxy hiện diện là chưa đủ: phải assert đúng advice event/counter.

Bổ sung một ca self-invocation để chứng minh lời gọi nội bộ không quay qua proxy. Các phép thử này bảo vệ ý nghĩa phép giao trước/sau khi đổi expression; bài **“Kiểm thử ca được khớp và ca không được khớp”** triển khai bộ JUnit thực tế.

## 10. Hiệu năng, bảo trì và kiến trúc

Tài liệu Spring khuyên kết hợp một điều kiện **kinded** (đặc biệt `execution`) với điều kiện **scoping** (`within`) khi phù hợp để điểm cắt có phạm vi rõ và dễ tối ưu matching. Tên pointcut không tự tạo hiệu năng nhanh hơn; điều đáng giá là **ràng buộc đúng vùng cần advice** và có cấu trúc dễ kiểm chứng.

Nếu project chuyển từ cấu trúc `com.example.shop.service` sang các module như `com.example.orders.application` hoặc `com.example.billing.application`, việc tập trung layer definitions giúp sửa ở một nơi. Nhưng cần test xác nhận các target mới thực sự được Spring tạo và advice không bị mất ngoài ý muốn.

Một quy ước tổ chức nên có:
- **Tên mô tả ý nghĩa:** `serviceQuery`, `repositoryOperation`, `auditedCommand`, không chỉ `pc1`, `pc2`.
- **Không lẫn logic trong named pointcut method:** phần thân rỗng, logic chạy ở `@Before`/`@Around`.
- **Không sao chép package patterns trên nhiều aspects:** reuse bằng tên class/method.
- **Không gom mọi thứ thành một expression quá rộng:** chia theo layer và functional group.
- **Test phạm vi âm và dương:** đảm bảo lời gọi ngoài layer không bị advice.
- **Tách trách nhiệm:** lớp pointcuts mô tả *where*; các aspects mô tả *what to do*.

## 11. Checklist áp dụng

Trước khi áp dụng, đối chiếu architecture scope (`within`, `execution`, `bean`) với business scope (tên method/annotation); đặt ngoặc rõ khi phối hợp `&&`, `||`, `!`; bảo đảm aspect bean được đăng ký và caller dùng Spring proxy. Test cả service đúng tầng, service thuộc package con, repository và bean ngoài phạm vi. Nếu đổi convention `load*`/`place*`, chạy lại các ca dương và âm để phát hiện tác động.

## 12. Kết luận

**Tổ chức pointcut theo tầng kiến trúc và nhóm chức năng là cách biến một tập biểu thức AOP rời rạc thành những quy tắc rõ ràng, chia sẻ được và có thể kiểm thử.** Dùng `within` hoặc `execution` để mô tả tầng, sử dụng tên method hoặc annotation để phân loại nhóm nghiệp vụ, rồi kết hợp bằng `&&`, `||`, `!`. Đưa named pointcuts dùng chung vào một lớp chuyên trách để mỗi `@Aspect` tập trung vào phần advice.

Cuối cùng, luôn nhớ ranh giới Spring AOP: chỉ lời gọi method **qua Spring proxy** trên Spring bean được advice. Một pointcut đúng về package/chữ ký không khiến method gọi nội bộ trên `this` tự bị chặn.

### Tài liệu chính thức

- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html): kết hợp pointcuts, dùng tên, `CommonPointcuts`, `within`/`execution`/`bean` và quy tắc matching.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): khai báo `@Before`, `@Around` và thứ tự advice.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): kích hoạt Spring AOP và đăng ký `@Aspect`.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB, proxy call boundary, self-invocation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reusable-pointcuts-003">Chia sẻ pointcut giữa các aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Chia sẻ pointcut giữa các aspect trong Spring AOP

## 1. Vấn đề cần giải quyết

Trong một ứng dụng Spring, nhiều `@Aspect` có thể quan tâm đến cùng tập lời gọi, chẳng hạn các method public trong lớp service:

- `AuditAspect` ghi dấu trước khi một operation chạy.
- `MetricsAspect` ghi nhận kết quả operation.
- Một aspect khác có thể đo thời gian hoặc bổ sung thông tin truy vết.

Nếu mỗi aspect sao chép chuỗi `execution(...)` riêng, chỉ cần đổi package hoặc tên method là rất dễ xuất hiện các bản pointcut không nhất quán. Spring AOP cho phép **định nghĩa pointcut có tên** bằng annotation `@Pointcut` rồi **tái sử dụng tên này trong nhiều advice, kể cả ở các aspect khác nhau**.

Điều cần nhớ:

1. Method mang `@Pointcut` đại diện cho **signature của pointcut**; expression trong annotation quyết định method execution nào được chọn.
2. Phần thân method pointcut thường **để trống**, return type `void`.
3. Advice có thể dùng `"package.ClassName.pointcutName()"` thay vì copy biểu thức dài.
4. Điểm cắt chung không làm các advice trở thành một advice duy nhất: Spring vẫn tạo và sắp thứ tự advisor tương ứng với từng advice.
5. Dù dùng chung pointcut, giới hạn proxy của Spring AOP vẫn áp dụng: chỉ những method execution được gọi qua Spring proxy và được matcher chọn mới được advice.

Bài này chỉ xét **cơ chế Spring Framework/Spring AOP**. Các kiến thức AspectJ weaving và lý thuyết Aspect tổng quát không được mở rộng.

## 2. Pointcut có tên được khai báo như thế nào?

Cú pháp `@Pointcut` đặt tên cho một biểu thức, để các advice khác dùng **cùng định nghĩa** mà không chép nguyên chuỗi:

```java
public class SharedPointcuts {
    @Pointcut("execution(public * demo.service..*.*(..))")
    public void serviceMethod() {}

    @Pointcut("execution(* demo.service..*.submit(..))")
    public void submitMethod() {}

    @Pointcut("serviceMethod() && submitMethod()")
    public void submission() {}
}
```

Trong một aspect khác, tham chiếu `@Before("demo.pointcut.SharedPointcuts.submission()")` nếu class thực ở package `demo.pointcut`. Method signature phải là `void`, có `()` khi tham chiếu và body thường trống. Bài **“Khai báo pointcut có tên bằng @Pointcut”** giải thích chi tiết cấu tạo và binding tham số.

## 3. Tổ chức một thư viện pointcut dùng chung

Một ứng dụng lớn thường có các ranh giới kiến trúc rõ ràng: public service operations, repository operations, method cần audit, hoặc một tập method của subsystem cụ thể. Tài liệu Spring khuyên tạo **class chuyên dụng** để mô tả những ranh giới này bằng named pointcuts.

Một cấu trúc tham khảo:

```text
com.example.app/
├── pointcut/
│   └── ApplicationPointcuts.java
├── aspect/
│   ├── AuditAspect.java
│   └── MetricsAspect.java
└── service/
    ├── OrderService.java
    └── OrderServiceImpl.java
```

Trong catalog nên ưu tiên:

- Tên diễn đạt **ý nghĩa nghiệp vụ hoặc kiến trúc**: `serviceOperation()`, `orderSubmission()`.
- Các pointcut nhỏ có thể kết hợp bằng `&&`, `||` và `!`.
- Phạm vi đủ chặt: ưu tiên giới hạn method signature và package/type, tránh vô tình chọn mọi method của toàn bộ ứng dụng.
- Không inject business services chỉ để “thực hiện” pointcut. Catalog là nơi chứa **metadata expression**, không phải nơi chạy nghiệp vụ.
- Có integration test để biết các method thực sự được chọn khi package hoặc service APIs thay đổi.

### Cách ghép named pointcuts

```java
@Pointcut("execution(public * com.example.app..service..*.*(..))")
public void publicServiceMethod() {
}

@Pointcut("execution(* com.example.app..*.submit(..))")
public void submitMethod() {
}

@Pointcut("publicServiceMethod() && submitMethod()")
public void publicSubmission() {
}

@Pointcut("publicServiceMethod() && !submitMethod()")
public void serviceOtherThanSubmit() {
}
```

Đoạn trên **phải nằm trong class `@Aspect` tương ứng**, không phải chương trình Java độc lập. Khi một pointcut khác dùng `publicSubmission()`, Spring xử lý nó như biểu thức kết hợp; method body không cần và không nên chứa logic.

Nếu chỉ một aspect cần một điều kiện riêng, aspect đó có thể thêm `&& bean(*Service)` hoặc `&& @annotation(...)` vào tham chiếu catalog. Các advice không bắt buộc có **tập matching y hệt nhau** chỉ vì có chung một phần pointcut.

## 4. Quy tắc visibility khi dùng chung pointcut

Điều kiện tái sử dụng named pointcut từ một class khác là **tên pointcut và declaring type phải truy cập được** theo Java visibility. Một method `private @Pointcut` phù hợp khi advice ở cùng declaring class dùng nội bộ; package-private và `protected` có giới hạn package/hierarchy; `public` phù hợp cho catalog ở package khác.

Modifier của method `@Pointcut` không làm thay đổi tập method được match; muốn chọn public target method phải có điều kiện như `execution(public * *(..))` trong expression. Hai bài **“Visibility của phương thức pointcut và phạm vi tái sử dụng”** và **“Phân biệt visibility…”** phân tích đầy đủ phạm vi Java và giới hạn JDK/CGLIB proxy.

## 5. Ví dụ runnable: hai aspect chia sẻ một pointcut catalog

Project dùng **Java 17+, Maven và Spring Framework 6.2.6**. Ta tạo:

- `SharedPointcuts` với hai pointcut cơ bản và một pointcut kết hợp.
- `AuditAspect` sử dụng named pointcut ấy trong `@Before`.
- `MetricsAspect` sử dụng **cùng named pointcut** trong `@AfterReturning`.
- `OrderServiceImpl` có `submit()` và `status()`. Chỉ `submit()` được hai advice chọn.

Ví dụ dùng `AnnotationConfigApplicationContext`, không cần Spring Boot. Aspect và catalog đều được đăng ký thành Spring beans bằng `@Bean`, giúp tránh các giả định về component-scan.

### 5.1. Cấu trúc file

```text
spring-shared-pointcuts/
├── pom.xml
└── src/main/java/demo/
    ├── SharedPointcutDemo.java
    ├── pointcut/
    │   └── SharedPointcuts.java
    ├── service/
    │   ├── OrderService.java
    │   └── OrderServiceImpl.java
    └── aspect/
        ├── AuditAspect.java
        └── MetricsAspect.java
```

### 5.2. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-shared-pointcuts</artifactId>
    <version>1.0.0</version>
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
                    <mainClass>demo.SharedPointcutDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 5.3. Tệp src/main/java/demo/pointcut/SharedPointcuts.java

```java
package demo.pointcut;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class SharedPointcuts {

    @Pointcut("execution(public * demo.service..*.*(..))")
    public void serviceMethod() {
    }

    @Pointcut("execution(* demo.service..*.submit(..))")
    public void submitMethod() {
    }

    @Pointcut("serviceMethod() && submitMethod()")
    public void submission() {
    }
}
```

Ba methods này trả `void`, có body rỗng và đều là `public`. Expression `submission()` chọn **phần giao**: method public thuộc service package và có tên `submit`. Catalog là class khai báo pointcut chứ không phải service chạy nghiệp vụ.

### 5.4. Tệp src/main/java/demo/service/OrderService.java

```java
package demo.service;

public interface OrderService {
    String submit(String id);
    String status();
}
```

### 5.5. Tệp src/main/java/demo/service/OrderServiceImpl.java

```java
package demo.service;

public class OrderServiceImpl implements OrderService {

    @Override
    public String submit(String id) {
        System.out.println("TARGET submit " + id);
        return "ACCEPTED:" + id;
    }

    @Override
    public String status() {
        System.out.println("TARGET status");
        return "READY";
    }
}
```

### 5.6. Tệp src/main/java/demo/aspect/AuditAspect.java

```java
package demo.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {

    @Before("demo.pointcut.SharedPointcuts.submission()")
    public void before(JoinPoint joinPoint) {
        System.out.println(
            "AUDIT " + joinPoint.getSignature().getName()
        );
    }
}
```

### 5.7. Tệp src/main/java/demo/aspect/MetricsAspect.java

```java
package demo.aspect;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.AfterReturning;

@Aspect
public class MetricsAspect {

    @AfterReturning(
        pointcut = "demo.pointcut.SharedPointcuts.submission()",
        returning = "result"
    )
    public void after(Object result) {
        System.out.println("METRIC return " + result);
    }
}
```

Cả `AuditAspect` và `MetricsAspect` dùng đúng cùng **qualified pointcut reference**. Việc thực thi advice khác nhau do annotation: `@Before` trước target, `@AfterReturning` sau khi method hoàn thành bình thường.

### 5.8. Tệp src/main/java/demo/SharedPointcutDemo.java

```java
package demo;

import demo.aspect.AuditAspect;
import demo.aspect.MetricsAspect;
import demo.pointcut.SharedPointcuts;
import demo.service.OrderService;
import demo.service.OrderServiceImpl;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class SharedPointcutDemo {

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class Config {

        @Bean
        public OrderService orderService() {
            return new OrderServiceImpl();
        }

        @Bean
        public SharedPointcuts sharedPointcuts() {
            return new SharedPointcuts();
        }

        @Bean
        public AuditAspect auditAspect() {
            return new AuditAspect();
        }

        @Bean
        public MetricsAspect metricsAspect() {
            return new MetricsAspect();
        }
    }

    public static void main(String[] args) {
        try (var ctx =
                new AnnotationConfigApplicationContext(Config.class)) {

            OrderService service = ctx.getBean(OrderService.class);
            System.out.println(
                "AOP_PROXY=" + AopUtils.isAopProxy(service)
            );
            System.out.println("CLIENT " + service.submit("A01"));
            System.out.println("CLIENT " + service.status());
        }
    }
}
```

Chạy tại thư mục `spring-shared-pointcuts`:

```bash
mvn -q compile exec:java
```

Các dòng **do chương trình in ra**, không bao gồm logging riêng của Maven hoặc Spring:

```text
AOP_PROXY=true
AUDIT submit
TARGET submit A01
METRIC return ACCEPTED:A01
CLIENT ACCEPTED:A01
TARGET status
CLIENT READY
```

Giải thích kết quả:

1. `@EnableAspectJAutoProxy` bật Spring AOP với kiểu khai báo @AspectJ; các `@Bean` đăng ký target, catalog và hai advice aspects.
2. Advisor của `AuditAspect` và `MetricsAspect` đều dùng `SharedPointcuts.submission()`. Target `submit()` match nên có proxy, và cả hai advice tham gia vào lời gọi.
3. `@Before` chạy trước target; sau khi target trả `ACCEPTED:A01`, `@AfterReturning` quan sát kết quả; cuối cùng client in kết quả.
4. `status()` nằm trong service package nhưng không phải `submit()`. Nó không match `SharedPointcuts.submitMethod()`, nên hai advice không chạy cho lời gọi `status()`.
5. Tên/lớp proxy runtime không được hardcode vào output. `AopUtils.isAopProxy(...)` kiểm tra bean thực tế do context cấp.

**Lưu ý:** với context phức tạp hơn, một bean vẫn có thể có advisor khác như transaction hoặc caching. Trong demo chỉ có các aspect đã khai báo để kết quả minh họa rõ ràng.

## 6. Tổ chức thêm điều kiện riêng mà không sửa catalog

Aspect A có thể sử dụng toàn bộ catalog, còn aspect B bổ sung điều kiện:

```java
@Aspect
public class BeanScopedAudit {

    @Before(
        "demo.pointcut.SharedPointcuts.serviceMethod() " +
        "&& bean(*Service)"
    )
    public void auditNamedBeans() {
        System.out.println("AUDIT NAMED SERVICE");
    }
}
```

Đây là **snippet**; muốn chạy phải đăng ký thành Spring bean và có tên bean khớp pattern. `bean(*Service)` là pointcut designator **riêng của Spring AOP**, chọn theo Spring bean name thay vì method/class.

Một snippet khác chỉ chọn execution được gắn annotation:

```java
@Aspect
public class AnnotatedAudit {

    @Before(
        "demo.pointcut.SharedPointcuts.serviceMethod() " +
        "&& @annotation(com.example.app.Audited)"
    )
    public void auditAnnotated() {
        System.out.println("AUDITED");
    }
}
```

Đây là **mẫu** cần định nghĩa annotation `com.example.app.Audited` và test cách matcher tra cứu method annotation. Nội dung chung `serviceMethod()` vẫn được tái sử dụng. Các aspect có thể **mở rộng/hạn chế** tập join point so với nhau mà không sửa ranh giới nền tảng.

Đừng cố nhồi mọi điều kiện đặc thù của một aspect vào catalog chung, nếu điều đó khiến các aspect khác phụ thuộc những chi tiết không liên quan.

## 7. Chia sẻ pointcut không phải chia sẻ lời thực thi advice

Một catalog chỉ xác định **tập join points**. Các aspect tham chiếu catalog vẫn có method advice riêng, thứ tự riêng, và mỗi advice được Spring thích ứng thành interceptor/advisor tương ứng.

```text
         SharedPointcuts.submission()
                    |
          +---------+----------+
          |                    |
          v                    v
    AuditAspect           MetricsAspect
    @Before               @AfterReturning
          |                    |
          +---------+----------+
                    |
             Spring AOP proxy
                    |
                    v
             OrderService.submit()
```

**Thứ tự advice không do shared pointcut quyết định.** Nếu hai `@Around` cùng match một method, Spring xử lý theo precedence/ordering; muốn thứ tự xác định giữa các aspects thì dùng `@Order` hoặc `Ordered` khi phù hợp. Trên đường vào, advice ưu tiên cao chạy trước; trên đường ra, thứ tự thường ngược lại do các interceptor lồng nhau.

Không dùng việc chia sẻ catalog để thay thế cơ chế sắp thứ tự advice.

## 8. Đặc điểm và giới hạn riêng của Spring AOP

### 8.1. Pointcut phải được Spring nhận diện trong context

Trong kiểu cấu hình @AspectJ, Spring quét/tìm các classes `@Aspect` được đăng ký thành Spring beans để xây advisor. Demo đăng ký `SharedPointcuts` và hai aspects bằng `@Bean`. Trong ứng dụng Boot có starter AOP, auto-configuration có thể bật xử lý tương đương, nhưng các aspect vẫn phải được container biết đến (ví dụ `@Component` trong vùng component scan).

### 8.2. Proxy boundary không đổi

Shared pointcut chỉ giúp **dùng lại biểu thức**, không mở rộng loại join point. Với Spring AOP, caller phải đi qua proxy; `this.otherMethod()` trong target (self-invocation) không tự trở thành một lần gọi mới qua proxy. Đổi JDK proxy thành CGLIB không giải quyết self-invocation.

### 8.3. JDK/CGLIB không tự khiến mọi method intercept được

JDK proxy công bố interface methods; CGLIB subclass proxy bị giới hạn bởi `final class`, `final/private method` và Java visibility. Nếu catalog match một method nhưng không có lời gọi qua Spring proxy hợp lệ, advice vẫn không chạy.

### 8.4. Spring `bean(...)` không thuộc native AspectJ weaving

Spring AOP có extension `bean(namePattern)` nhờ tích hợp với BeanFactory. Nó có thể được ghép với named pointcuts trong Spring @AspectJ configuration. Tuy nhiên, không copy một expression có `bean(...)` sang môi trường AspectJ compile-time/load-time weaving rồi cho rằng cú pháp sẽ hoạt động giống Spring AOP.

### 8.5. Không giải quyết vấn đề bảo mật chỉ bằng pointcut

Một pointcut dù được chia sẻ bởi nhiều aspects vẫn chỉ là cơ chế chọn lời gọi. Đừng coi nó là security boundary tuyệt đối khi caller có thể giữ target reference thô, gọi phương thức nội bộ hoặc một codepath bỏ qua Spring proxy. Với kiểm soát quyền, cần xác định ranh giới an toàn thực sự và có test cho những đường gọi đó.

## 9. Refactor catalog và các lỗi thường gặp

Khi đổi tên `SharedPointcuts`, tên method `submission()` hoặc package `demo.pointcut`, tìm mọi tham chiếu string trong `@Before`, `@AfterReturning`, `@Around` và cấu hình XML. Tên đầy đủ không được Java compiler tự bảo vệ như lời gọi method bình thường; một chuỗi sai có thể khiến advisor không khởi tạo hoặc mất matching.

Sau khi đổi chính expression trong catalog, kiểm thử lại **các advice của cả hai aspects**: `submit()` cần audit/metrics đúng số lần, `status()` không được chạy các policies ấy. Nếu một aspect thêm `&& @annotation(...)`, tập match của aspect đó có thể hẹp hơn catalog; giữ expectation riêng.

## 10. Named pointcut có tham số

Một ứng dụng có thể muốn chia sẻ pointcut **bind runtime argument** để nhiều advice nhận cùng loại ngữ cảnh. Ví dụ một named pointcut với tham số `String`:

```java
@Aspect
public class ArgumentPointcuts {

    @Pointcut("args(orderId)")
    public void withOrderId(String orderId) {
    }
}
```

Sau đó một aspect khác dùng binding form:

```java
@Aspect
public class ArgumentAuditAspect {

    @Before(
        value =
            "demo.pointcut.ArgumentPointcuts.withOrderId(id) " +
            "&& execution(* demo.service..*.submit(..))",
        argNames = "id"
    )
    public void beforeSubmit(String id) {
        System.out.println("ID=" + id);
    }
}
```

Đây là **hai snippet nâng cao, không thuộc demo chạy độc lập**. Để chạy cần khai báo package/class đúng và đăng ký pointcut/aspect beans; `args(...)` bind runtime method arguments theo signature. Không phải Spring sẽ gọi Java method `withOrderId(String)` để lấy dữ liệu. Khi chia sẻ kiểu pointcut này, cần đặc biệt chú ý tên tham số, `argNames`, type compatibility và method signature của target.

## 11. Kiểm thử đúng cách

Một phép thử chia sẻ thực sự phải gọi target bean qua Spring proxy, ghi event/counter **riêng từng aspect**, và chứng minh method được chọn kích hoạt cả hai policies theo đúng loại advice. Gọi method ngoài rule để xác nhận không có event thừa. Kiểm tra thêm tình huống pointcut được đổi expression mà các aspects vẫn tham chiếu đúng symbol, cùng thứ tự `@Order` nếu nhiều advice có phụ thuộc về thứ tự.

`AopUtils.isAopProxy()` là kiểm tra phụ: proxy có thể tồn tại bởi advisor khác, vì thế không thay thế assertion trên từng advice.

## 12. Câu hỏi tự kiểm tra

**1.** Hai aspect muốn dùng cùng `execution(...)`, cách tránh lặp lại biểu thức dài là gì?  
**Đáp án:** Định nghĩa named `@Pointcut` trong catalog và tham chiếu bằng `package.ClassName.pointcutName()`.

**2.** Spring có gọi body của method `@Pointcut` khi method nghiệp vụ khớp không?  
**Đáp án:** Không. Method này là signature/metadata cho expression; advice mới là phần được Spring thực thi.

**3.** Đổi `public void serviceMethod()` thành `private void serviceMethod()` có làm expression match ít method hơn không?  
**Đáp án:** Bản thân expression không đổi, nhưng pointcut không còn có thể được aspect ở ngoài class tham chiếu theo quy tắc visibility.

**4.** Trong demo, vì sao `submit()` chạy audit và metrics, nhưng `status()` không?  
**Đáp án:** Catalog kết hợp `serviceMethod() && submitMethod()`; chỉ method `submit()` thỏa cả hai điều kiện.

**5.** Nếu một aspect dùng `SharedPointcuts.serviceMethod()`, aspect khác dùng `SharedPointcuts.serviceMethod() && @annotation(...)` thì hai tập method được chọn có như nhau không?  
**Đáp án:** Không nhất thiết, vì aspect thứ hai bổ sung điều kiện annotation.

**6.** Shared pointcut có quy định thứ tự chạy hai `@Around` advices không?  
**Đáp án:** Không. Sắp thứ tự advisor/advice là cơ chế riêng của Spring; dùng `@Order` nếu cần thứ tự.

**7.** Vì sao một pointcut chung không giải quyết được self-invocation?  
**Đáp án:** Lời gọi nội bộ trên target không đi qua Spring proxy, nên không có interception mới để pointcut đánh giá.

**8.** Có thể dùng expression Spring `bean(*Service)` trong mọi loại AspectJ weaving không?  
**Đáp án:** Không. Đây là designator đặc thù của Spring AOP gắn với bean naming.

## 13. Tài liệu chính thức

- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): pointcut signatures, qualified references, visibility, shared pointcut definitions và composition.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): Spring đăng ký và đọc `@Aspect` beans.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): `@Before`, `@AfterReturning`, precedence.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB, proxy boundary và self-invocation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reusable-pointcuts-004">Visibility của phương thức pointcut và phạm vi tái sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Visibility của phương thức pointcut và phạm vi tái sử dụng trong Spring AOP

## 1. Mục tiêu của bài

Trong Spring AOP theo cú pháp `@AspectJ`, bạn có thể đặt tên cho một pointcut expression bằng một phương thức `@Pointcut` rồi tham chiếu lại từ các advice khác. Phương thức pointcut có thể khai báo `private`, package-private, `protected` hoặc `public`.

**Hai vấn đề cần phân biệt tuyệt đối:**

1. **Visibility của phương thức khai báo named pointcut** quyết định **nơi những biểu thức pointcut khác được phép tham chiếu tới tên này**, theo quy tắc truy cập Java.
2. **Phạm vi method mà pointcut khớp** do **biểu thức trong `@Pointcut("...")`** quyết định; `private`/`public` của phương thức **khai báo pointcut** không tự thay đổi target methods được advice.

Ví dụ:

```java
@Aspect
public class TraceAspect {
    @Pointcut("execution(public * com.example.orders..*Service.*(..))")
    private void publicServiceOperations() { }

    @Before("publicServiceOperations()")
    public void beforeService() {
        // Advice áp dụng cho những public service method có invocation qua proxy.
    }
}
```

Pointcut method `publicServiceOperations()` là **`private`**, nhưng biểu thức `execution(public * ...)` vẫn có thể match **public methods của Spring service beans**. Từ `private` ở đây **không đồng nghĩa “chỉ chặn private method”**.

Tương tự, một `@Pointcut` method được khai báo `public` không tự động chặn tất cả public target methods. Tập method vẫn phụ thuộc chính biểu thức pointcut.

**Phạm vi nội dung:** cách Spring Framework tổ chức và tái sử dụng named pointcuts, Java visibility, Spring proxy boundary, cùng cách kiểm chứng bằng Spring `ApplicationContext`. Đây không phải bài về AspectJ weaving hoặc accessibility tổng quát của Java.

## 2. Named pointcut trong Spring có cấu tạo gì?

Một named pointcut gồm **method signature** (tên, tham số binding, Java visibility, kiểu trả về `void`) và **expression trong `@Pointcut`**. Spring đọc annotation metadata và tham chiếu tên như `@Before("serviceMethods()")`; thân method thường để trống, không phải callback thực thi nghiệp vụ.

Ví dụ `@Pointcut("execution(public * com.example..*Service.*(..))") private void serviceMethods() {}` có *private declaration* nhưng biểu thức chọn *public target methods*. Đây là điểm phân biệt cần kiểm chứng khi chia sẻ tên qua những phạm vi truy cập khác nhau.

## 3. Ma trận visibility và phạm vi tham chiếu

| Visibility của method `@Pointcut` | Ai có thể tham chiếu named pointcut? | Tình huống Spring AOP nên dùng |
| --- | --- | --- |
| `private` | Các advice/pointcut declarations trong **cùng declaring type** | Đóng gói predicate dành riêng cho một aspect; ngăn aspect khác phụ thuộc vào implementation detail. |
| Package-private (không modifier) | Code declarations trong **cùng Java package** theo quy tắc visibility | Nhóm một số aspects cùng package có convention nội bộ; ít thích hợp nếu package cần thay đổi thường xuyên. |
| `protected` | Declaring type, code cùng package và hierarchy/subclasses theo quy tắc `protected` | Chia sẻ pointcut qua cấu trúc aspect/class hierarchy có chủ đích; cần kiểm tra đúng ngữ cảnh kế thừa và khả năng resolve. |
| `public` | Bất cứ nơi nào **declaring class/method được truy cập**, kể cả package khác | Named pointcut dùng chung giữa nhiều aspects và nhiều module; tạo API pointcut ổn định. |

**Quy tắc từ Spring Framework:** khi tham chiếu pointcut **bằng tên**, các quy tắc Java visibility thông thường được áp dụng. Visibility **không ảnh hưởng pointcut matching**.

Một vài điều kiện cần nói thêm:

1. Nếu class chứa pointcut là package-private, việc cho method `public` **không tự biến declaring class thành public API** cho package khác.
2. `protected` không có nghĩa “aspect nào cũng dùng được”; đặc biệt ngoài package, phải xét quan hệ kế thừa/access hợp lệ, không chỉ dựa vào chữ `protected`.
3. `private` **không cho phép aspect khác tham chiếu tên đó** chỉ vì chúng cùng Spring context. Spring container không bỏ qua Java visibility để biến named pointcut private thành shared API.
4. Một aspect có thể dùng **nhiều private named pointcuts** để xây biểu thức phức tạp nội bộ, sau đó công bố **một public named pointcut** nếu có nhu cầu chia sẻ.
5. Visibility không phải biện pháp authorization/security đối với target methods. Nó kiểm soát **tái sử dụng tên định nghĩa**, không kiểm soát quyền caller thực thi Spring service.

## 4. Pointcut private: tái sử dụng bên trong một aspect

```java
package com.example.orders.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class OrderAuditAspect {
    @Pointcut("execution(public * com.example.orders..*Service.*(..))")
    private void orderServiceOperations() {}

    @Pointcut("execution(* com.example.orders..*Service.submit(..))")
    private void submitOperations() {}

    @Before("orderServiceOperations() && submitOperations()")
    public void beforeSubmit() {
        System.out.println("audit:submit");
    }
}
```

Đây là **đoạn minh họa**, cần các Spring service tương ứng và hạ tầng Spring AOP để chạy. Nó cho thấy:

- `orderServiceOperations()` và `submitOperations()` là **hai named pointcuts private**.
- `beforeSubmit()` ở **cùng class** có thể tham chiếu cả hai và kết hợp bằng `&&`.
- `private` giúp quy tắc nội bộ không vô tình trở thành contract công khai cho module khác.
- Dù pointcut private, `@Before` vẫn có thể advice một `public submit(..)` của bean khác khi method call đi qua Spring proxy.

Nếu aspect thứ hai muốn tái sử dụng `orderServiceOperations()`, nó **không được** tham chiếu `com.example.orders.aop.OrderAuditAspect.orderServiceOperations()` vì declaration private. Khi thực sự cần reuse, hãy **công khai điểm cắt hợp lý** hoặc chuyển predicate dùng chung sang một class định nghĩa pointcuts riêng.

### Private là lựa chọn mặc định tốt khi nào?

- Predicate có ý nghĩa chỉ trong một aspect.
- Tên pointcut gắn với cách triển khai cục bộ và có thể thay đổi thường xuyên.
- Bạn không muốn một module khác tạo coupling vô tình vào chi tiết biểu thức.
- Nhiều advice trong **cùng aspect** cần sử dụng lại expression hoặc ghép thành `&&`, `||`, `!`.

Đừng biến mọi pointcut thành public chỉ vì “đề phòng tương lai”. Visibility hẹp giúp người bảo trì biết cái gì là implementation detail.

## 5. Pointcut public: chia sẻ giữa nhiều aspects bằng CommonPointcuts

Khi nhiều aspects cùng nhắm các method thuộc **service layer**, Spring Framework khuyến nghị định nghĩa những **named pointcut expressions trong một class dùng chung**.

```java
package com.example.orders.aop;

import org.aspectj.lang.annotation.Pointcut;

public class CommonPointcuts {
    @Pointcut("execution(public * com.example.orders..*Service.*(..))")
    public void serviceOperations() {}

    @Pointcut("execution(* com.example.orders..*Service.submit(..))")
    public void submitOperations() {}
}
```

Đây là **một class mang các định nghĩa pointcut**, không chứa advice. Theo mẫu `CommonPointcuts` trong tài liệu Spring Framework, class này **không cần `@Aspect` hoặc Spring bean riêng** chỉ để các biểu thức pointcut khác tham chiếu các phương thức `@Pointcut` bằng fully qualified name.

Một aspect có thể tham chiếu:

```java
@Aspect
public class AuditAspect {
    @Before("com.example.orders.aop.CommonPointcuts.serviceOperations()")
    public void beforeService() {
        // ...
    }
}
```

Aspect khác có thể dùng chính named pointcut ấy:

```java
@Aspect
public class MetricsAspect {
    @Before("com.example.orders.aop.CommonPointcuts.serviceOperations()")
    public void recordInvocation() {
        // ...
    }
}
```

Các snippets trên **chỉ minh họa reuse**, không phải project chạy đầy đủ. Hai aspect classes **phải là Spring beans** (chẳng hạn `@Aspect` + `@Component` với component scan hoặc `@Bean` trong configuration) để advice hoạt động.

**Không cần sao chép nguyên văn chuỗi `execution(...)` sang mọi aspect.** Nếu service package đổi, sửa expression một nơi trong `CommonPointcuts`; những references hợp lệ sẽ dùng biểu thức mới khi Spring xây advisors.

### Cú pháp tham chiếu ở cùng class và ở class khác

| Vị trí sử dụng | Cú pháp | Điều kiện |
| --- | --- | --- |
| Advice trong cùng declaring type | `@Before("serviceOperations()")` | Named pointcut có thể `private`, package-private, `protected` hoặc `public`. |
| Aspect khác tham chiếu public pointcut | `@Before("com.example.orders.aop.CommonPointcuts.serviceOperations()")` | Declaring class và method có visibility hợp lệ, class được đưa vào classpath, tên chính xác. |
| Từ pointcut khác, ghép hai public declarations | `@Pointcut("com.example.orders.aop.CommonPointcuts.serviceOperations() && execution(* *.submit(..))")` | Mỗi tên tham chiếu được resolve, biểu thức đúng cú pháp; cần giới hạn scope để tránh match quá rộng. |
| Dùng tên private của aspect khác | `@Before("OtherAspect.privatePointcut()")` | **Không hợp lệ** theo quy tắc visibility; không được biến thành công khai chỉ vì cùng Spring context. |

Khi refactor package, đổi tên class/method của `CommonPointcuts`, các references bằng fully-qualified name có thể hỏng trong lúc Spring parse advisor. Do đó hãy coi public named pointcuts là **API cấu hình AOP cần test**, không chỉ là những string literals vô nghĩa.

## 6. Demo độc lập Spring Framework: private pointcut và public pointcut cùng áp lên service

Demo này xác nhận rõ hai phạm vi:

- `TraceAspect` chứa **private named pointcut** `processOnly()` chỉ dùng bên trong chính aspect đó.
- `PointcutCatalog` công bố **public named pointcut** `serviceCalls()`; `AuditAspect` ở một class khác dùng nó bằng fully qualified name.
- **Một Spring service bean** có hai public methods. `process(..)` match cả hai biểu thức; `inspect()` chỉ match public pointcut chung. Kết quả được in bằng counters sau khi tất cả calls kết thúc để không phụ thuộc thứ tự thực thi advice.

Môi trường: **Java 17+**, **Spring Framework 6.2.19**, **AspectJ Weaver 1.9.25.1**, Gradle có plugin `application`. Đây là Spring Framework thuần, không sử dụng Boot AOP auto-configuration.

### 6.1. Cấu trúc project

```text
named-pointcut-visibility/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/visibility/
    ├── Main.java
    ├── AppConfig.java
    ├── OrderService.java
    ├── PointcutCatalog.java
    ├── AuditAspect.java
    └── TraceAspect.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'named-pointcut-visibility'
```

**`build.gradle`**

```gradle
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
    implementation 'org.springframework:spring-context:6.2.19'
    implementation 'org.springframework:spring-aop:6.2.19'
    implementation 'org.aspectj:aspectjweaver:1.9.25.1'
}

application {
    mainClass = 'com.example.visibility.Main'
}
```

Các versions được khai báo cho demo độc lập. Khi áp dụng vào Spring Boot, hãy dùng BOM/dependency management của phiên bản Boot dự án đang chạy thay vì trộn các Spring module versions tùy ý.

### 6.2. Target bean có hai method

**`OrderService.java`**

```java
package com.example.visibility;

public class OrderService {
    public String process(String id) {
        return "processed:" + id;
    }

    public String inspect() {
        return "inspected";
    }
}
```

Cả hai methods đều public/non-final. Tên class kết thúc bằng `Service` để match biểu thức `*Service` của common pointcut.

### 6.3. Class giữ public pointcut dùng chung

**`PointcutCatalog.java`**

```java
package com.example.visibility;

import org.aspectj.lang.annotation.Pointcut;

public class PointcutCatalog {
    @Pointcut(
        "execution(public * com.example.visibility..*Service.*(..))"
    )
    public void serviceCalls() {}
}
```

Class này **không có `@Aspect`, không có `@Component`, không có `@Bean`**, đúng vai trò chỉ **định nghĩa một named pointcut public**. Spring/AspectJ pointcut expression có thể resolve định nghĩa theo class/method name. Bạn không cần gọi `new PointcutCatalog()` hay inject object catalog vào `AuditAspect` để dùng cú pháp biểu thức pointcut.

Không đặt code thực thi trong body của `serviceCalls()`: Spring không dùng nó như một method helper runtime.

### 6.4. Aspect dùng lại public pointcut từ class khác

**`AuditAspect.java`**

```java
package com.example.visibility;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    private final AtomicInteger calls = new AtomicInteger();

    @Before("com.example.visibility.PointcutCatalog.serviceCalls()")
    public void beforeServiceMethod() {
        calls.incrementAndGet();
    }

    public int count() {
        return calls.get();
    }
}
```

Advice tham chiếu **fully qualified public named pointcut** dù nó được khai báo trên một Java class khác. Method `process(..)` và `inspect()` đều match vì chúng là public methods của `OrderService`. Không in trong advice để tránh thứ tự của hai aspects làm stdout không tất định.

### 6.5. Aspect dùng private named pointcut nội bộ

**`TraceAspect.java`**

```java
package com.example.visibility;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class TraceAspect {
    private final AtomicInteger calls = new AtomicInteger();

    @Pointcut(
        "execution(public * com.example.visibility.OrderService.process(..))"
    )
    private void processOnly() {}

    @Before("processOnly()")
    public void beforeProcess() {
        calls.incrementAndGet();
    }

    public int count() {
        return calls.get();
    }
}
```

Pointcut `processOnly()` là **private**, nhưng advice `beforeProcess()` của chính `TraceAspect` dùng được. Biểu thức chỉ match `OrderService.process(..)`; `inspect()` không match.

Nếu chuyển nguyên `@Before("processOnly()")` sang `AuditAspect` mà không định nghĩa lại/bind theo visibility, nó không tự nhận diện named pointcut của aspect khác. Muốn share, công bố một public named pointcut riêng hoặc dùng class catalog.

### 6.6. Cấu hình Spring tạo một bean target và hai aspect beans

**`AppConfig.java`**

```java
package com.example.visibility;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    OrderService orderService() {
        return new OrderService();
    }

    @Bean
    AuditAspect auditAspect() {
        return new AuditAspect();
    }

    @Bean
    TraceAspect traceAspect() {
        return new TraceAspect();
    }
}
```

`@EnableAspectJAutoProxy` bật hạ tầng Spring AOP `@AspectJ` cho context. `AuditAspect`/`TraceAspect` trở thành Spring beans qua `@Bean`, không cần `@Component`. `OrderService` là target Spring bean. `proxyTargetClass=true` làm ví dụ dùng CGLIB class-based proxy để main có thể tra cứu `OrderService` bằng concrete class.

**Không đăng ký `PointcutCatalog`** vì class đó chỉ định nghĩa các pointcut signatures và không chứa advice hay state cần quản lý bởi Spring.

### 6.7. Main quan sát kết quả

**`Main.java`**

```java
package com.example.visibility;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService service = context.getBean(OrderService.class);
            AuditAspect audit = context.getBean(AuditAspect.class);
            TraceAspect trace = context.getBean(TraceAspect.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(service));
            System.out.println(service.process("A"));
            System.out.println(service.inspect());
            System.out.println("publicPointcutCalls=" + audit.count());
            System.out.println("privatePointcutCalls=" + trace.count());
        }
    }
}
```

Từ thư mục project, với Gradle và JDK 17+ đã cài:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper: `./gradlew run --quiet` (Linux/macOS) hoặc `gradlew.bat run --quiet` (Windows).

**Các dòng `System.out` do chương trình in, theo đúng thứ tự** (không tính thông báo bên ngoài do JVM/Gradle/logging):

```text
proxy=true
processed:A
inspected
publicPointcutCalls=2
privatePointcutCalls=1
```

Giải thích:

1. `proxy=true`: `OrderService` đã được Spring proxy vì có advisor áp dụng.
2. `processed:A` và `inspected`: cả hai target methods thực thi và trả giá trị.
3. `publicPointcutCalls=2`: biểu thức `PointcutCatalog.serviceCalls()` match **hai method invocations** qua proxy; advice trong `AuditAspect` chạy hai lần.
4. `privatePointcutCalls=1`: chỉ invocation `process("A")` match `TraceAspect.processOnly()`; `inspect()` không match.
5. Không có sự khác biệt vì `private` pointcut “ít khả năng AOP hơn” `public` pointcut; **hai biểu thức selection khác nhau**, còn modifier chỉ quyết định **khả năng tham chiếu** named pointcut.

Demo không dựa vào thứ tự advice execution giữa `TraceAspect` và `AuditAspect`, vì hai counters được in sau tất cả calls. Nếu cần thứ tự advice cụ thể trong production, xem `@Order` và kiểm thử toàn bộ advisor chain.

## 7. Các phạm vi reuse cụ thể trong codebase

### 7.1. Chỉ dùng bên trong một aspect

```java
@Aspect
public class ServiceTraceAspect {
    @Pointcut("execution(public * com.example.billing..*Service.*(..))")
    private void businessMethods() {}

    @Before("businessMethods()")
    public void before() {}

    @AfterReturning("businessMethods()")
    public void afterSuccess() {}
}
```

Hai advice trong cùng declaring class dùng cùng một private named pointcut. Không cần copy chuỗi biểu thức. Đây là **đoạn minh họa**, không thay demo `TraceAspect` ở trên.

### 7.2. Hai aspects trong cùng Java package

```java
package com.example.billing.aop;

public class InternalPointcuts {
    @Pointcut("execution(public * com.example.billing..*Service.*(..))")
    void businessMethods() {}
}
```

`businessMethods()` ở đây là **package-private**, có thể được tham chiếu từ nơi hợp lệ trong **cùng package**. Tuy nhiên, nếu hai aspects ở hai packages con khác nhau, hãy nhớ Java xem `com.example.billing.aop` và `com.example.billing.aop.audit` là **hai package khác nhau**. Package-private không tự cho phép tham chiếu chỉ vì package path có tiền tố giống nhau.

Đoạn minh họa trên cần import `org.aspectj.lang.annotation.Pointcut` nếu đặt vào file thực. Nếu muốn package nội bộ vẫn được mô-đun khác dùng bằng fully qualified name, hãy chọn một **public API pointcut** chủ động thay vì lách visibility.

### 7.3. Sử dụng `protected` trong hierarchy

```java
public abstract class BasePointcuts {
    @Pointcut("execution(public * com.example.billing..*Service.*(..))")
    protected void businessMethods() {}
}

// Minh họa một aspect kế thừa khai báo, cùng project.
@Aspect
public class ChildAspect extends BasePointcuts {
    @Before("businessMethods()")
    public void beforeBusiness() {}
}
```

Đây là **cú pháp minh họa**, không phải hai file Java chạy độc lập: cần packages/imports và bean registration phù hợp. `protected` có thể hỗ trợ pointcut reuse có chủ đích trong hierarchy, nhưng quan hệ inheritance của aspects/`@AspectJ` có các hạn chế riêng. Spring sử dụng normal Java visibility khi resolve tham chiếu; không được giả định một aspect bất kỳ ở package khác có quyền gọi tên `protected` này nếu không có quan hệ visibility hợp lệ.

Nếu nhiều aspects **không có quan hệ cha–con thực sự** cần reuse giống nhau, class `CommonPointcuts` với `public` named declarations thường đơn giản hơn một inheritance hierarchy chỉ để chia sẻ biểu thức.

### 7.4. Dùng giữa modules

Một module `platform-aop` có thể xuất bản:

```java
package com.example.platform.aop;

import org.aspectj.lang.annotation.Pointcut;

public final class PlatformPointcuts {
    private PlatformPointcuts() {}

    @Pointcut("execution(public * com.example.app..*Service.*(..))")
    public void appServiceCalls() {}
}
```

Đây là **đoạn minh họa**, không ghép với demo ở mục 6. Khi một module khác tham chiếu `com.example.platform.aop.PlatformPointcuts.appServiceCalls()`, class/method phải truy cập được ở runtime, được đưa vào dependency/classpath phù hợp, đồng thời biểu thức nhắm đúng packages của ứng dụng thực. Không cần instantiate class chỉ chứa pointcut signatures để người tiêu dùng tham chiếu tên.

Khi xuất bản như API dùng chung, đổi tên FQCN/method hoặc ý nghĩa expression đều có thể làm các aspects bên tiêu dùng lỗi hoặc chọn tập target methods khác. Hãy quản lý thay đổi như một **compatibility contract**.

## 8. Kết hợp các named pointcuts: visibility kiểm soát reference, không kiểm soát tập match

Có thể giữ những building blocks của catalog là `private` nhưng công bố một pointcut tổng hợp `public` có hợp đồng rõ ràng:

```java
@Pointcut("within(com.example.billing..*)")
private void billingTypes() {}

@Pointcut("execution(public * save*(..))")
private void publicSave() {}

@Pointcut("billingTypes() && publicSave()")
public void billingSaveOperations() {}
```

Các method trên là fragments cùng declaring class. Người dùng bên ngoài chỉ tham chiếu `billingSaveOperations()` nếu declaring class public, trong khi thay đổi nội bộ `billingTypes()` không làm chúng phụ thuộc tên helper. Biểu thức quyết định matching, visibility chỉ quyết định nơi có thể tham chiếu.

## 9. Named pointcut có tham số: lưu ý khi chia sẻ binding

Named pointcut có thể mang **binding parameter**, ví dụ:

```java
@Pointcut(value = "args(orderId,..)", argNames = "orderId")
public void withOrderId(String orderId) {}
```

Aspect ngoài class tham chiếu `SomePointcuts.withOrderId(orderId)` từ advice có `argNames`/tham số tương ứng. Khi dùng liên package, method phải có visibility phù hợp; `public` không làm `args` tự match đối số không đúng kiểu. Đây là snippet, không phải method chạy độc lập; hãy test các kiểu tham số và số lượng đối số thực tế.

## 10. Registration, proxy và lifecycle: điều kiện để reuse có tác dụng thực tế

Để tham chiếu công khai thành công, class chứa named pointcut phải nằm trên classpath, type/method accessible, tên đầy đủ đúng và pointcut expression parse được. Nhưng advice chỉ chạy nếu **aspect có advice là Spring bean**, AOP support đã bật, target là Spring bean, method có thể intercept và caller đi qua proxy.

Một class catalog chỉ khai báo các `@Pointcut` public không cần trở thành Spring bean. Ngược lại, khai báo `@Aspect` mà chưa đăng ký qua `@Component` hoặc `@Bean` sẽ không khiến advice tự chạy.

## 11. Những lỗi thường gặp

Khi gặp lỗi, tách ba lớp nguyên nhân. **Không resolve được tên:** sai package/class/method hoặc named pointcut không accessible. **Resolve được nhưng không match:** `execution`, `within`, `bean` hay annotation không chọn đúng method thực tế. **Match đúng nhưng advice không chạy:** target ngoài context, self-invocation, hoặc JDK/CGLIB không intercept được lời gọi đó.

Đừng đổi `private @Pointcut` thành `public` như cách sửa một expression quá hẹp: Java visibility không thay đổi phạm vi matching. Đồng thời không giả định một public catalog khiến mọi Spring bean đều có AOP proxy.

## 12. Chiến lược tổ chức pointcut trong dự án lớn

Một cấu trúc dễ bảo trì:

```text
com.example.app
├── aop
│   ├── ServicePointcuts.java          # public names dùng lại
│   ├── AuditAspect.java               # advice, internal private pointcuts
│   └── MetricsAspect.java             # advice, tham chiếu ServicePointcuts
├── billing
│   └── BillingService.java
└── inventory
    └── InventoryService.java
```

Tách theo vai trò:

- **Pointcut catalog:** công bố tên theo ý nghĩa business/architecture (`businessServiceOperation()`, `billingWriteOperation()`), chỉ giữ những expression dùng lại nhiều nơi.
- **Aspect classes:** giữ advice và pointcuts nội bộ, khai báo `private` khi không cần chia sẻ.
- **Package-private:** dùng trong một package AOP đóng gói tốt; ghi rõ convention của team.
- **Protected:** chỉ khi thật sự có nhu cầu reuse trong hierarchy hợp lệ.
- **Public:** xem như API, có tài liệu/test cho ý nghĩa match; đổi expression là thay contract.
- **Test module:** kiểm tra các Spring proxy targets thật để phát hiện biểu thức chọn nhầm service hoặc mất advisor do move/rename.

Với Java module system (JPMS), ngoài modifier `public` còn có khả năng **đọc module/exported package** khi compile/runtime. Public method nằm trong class/module không accessible sẽ không tự nhiên trở thành cross-module API. Chạy integration test trong cách đóng gói production để phát hiện.

### Có nên tạo một CommonPointcuts duy nhất cho toàn hệ thống?

Không nhất thiết. Một catalog quá lớn chứa mọi điểm cắt của toàn công ty sẽ gây coupling giữa modules. Thay vào đó, một hoặc nhiều catalog có phạm vi rõ ràng như `BillingPointcuts`, `InventoryPointcuts` hay `PlatformServicePointcuts`; public API chỉ cho những rule thực sự dùng chung.

Cân nhắc tên theo **ý nghĩa method set**, không chỉ theo implementation pattern. Chẳng hạn `businessOperations()` tốt hơn `pointcut1()`, nhưng phải đảm bảo biểu thức trong đó thực sự match business operations ở package/module đang dùng.

## 13. Kiểm chứng tên pointcut và tập method match

Kiểm chứng hai hành vi **độc lập**: (1) Một external aspect tham chiếu public catalog pointcut thành công và advice chạy đúng; (2) Một private pointcut được dùng trong cùng aspect vẫn có thể chọn public target methods. Với mỗi case, thêm method đối chứng không được chọn và kiểm tra counter/event thực tế.

Nếu thay public thành private trong một catalog đang được tiêu thụ, giữ thí nghiệm lỗi cấu hình ở fixture riêng để không phá context chính. Sau khi rename class/method hoặc thay visibility, rà chuỗi FQCN và chạy integration tests trên proxy thực.

## 14. Bảng chọn nhanh

| Yêu cầu | Visibility nên cân nhắc | Kiểu khai báo |
| --- | --- | --- |
| Hai advice của cùng một aspect chia sẻ selection nội bộ | `private` | `@Pointcut` trong chính aspect; dùng `@Before("localName()")`. |
| Nhiều aspects cùng package, rule không dùng ngoài package | Package-private | Pointcut catalog trong package, test access/refs. |
| Aspect hierarchy thực sự cần dùng predicate từ base | `protected` | Named pointcut ở base type; bảo đảm Java inheritance/visibility và Spring @AspectJ parsing phù hợp. |
| Các aspects ở nhiều packages/modules cần một selection chung | `public` | Public `CommonPointcuts`/`PlatformPointcuts` với method public, tham chiếu FQCN. |
| Mỗi aspect chỉ có một rule không tái sử dụng | Không nhất thiết đặt tên | `@Before("execution(...)")` inline đơn giản là hợp lý. |
| Muốn thay tập method bị advice | Không xử lý bằng visibility | Sửa `@Pointcut("...")` expression/bean boundaries, rồi test proxy call paths. |
| Muốn chặn private/final method ở target | Visibility pointcut không giúp | Xem giới hạn Spring JDK/CGLIB proxy, refactor target entry; AspectJ weaving là cơ chế khác. |

## 15. Checklist trước khi dùng trong production

Trước khi chia sẻ, kiểm tra method `@Pointcut` trả `void`, body rỗng và tên thể hiện tập method cần chọn; declaring type và method public khi tiêu thụ từ package khác. Pointcut dùng nội bộ nên hạn chế visibility phù hợp. Đối chiếu expression (đặc biệt modifier trong `execution(public ...)`) với target methods và loại proxy JDK/CGLIB; kiểm tra đăng ký aspect bean, đường gọi qua proxy, ca match và không match.

Với public catalog được tiêu thụ giữa module, quản lý thay đổi tên/FQCN/expression như một contract cấu hình có regression tests.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reusable-pointcuts-005">Phân biệt visibility của khai báo pointcut với phương thức được khớp</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP — Phân biệt visibility của khai báo pointcut với phương thức được khớp

## 1. Hai loại visibility thường bị nhầm lẫn

Trong Spring AOP dùng `@AspectJ` annotation style, một **named pointcut** gồm hai phần:

1. **Khai báo method `@Pointcut`:** một method Java có tên, tham số (nếu cần), visibility `private`/package-private/`protected`/`public` và body thông thường để trống. Method này đại diện cho một **tên có thể được các pointcut/advice khác tham chiếu**; signature của nó phải trả về `void`.
2. **Biểu thức `@Pointcut("...")`:** quy tắc **matching method execution** mà Spring AOP phân tích để chọn nơi có thể áp dụng advice.

Đây là hai chiều độc lập. Visibility đặt trước từ khóa `void` quyết định **ai được tham chiếu tên pointcut** theo các quy tắc visibility Java; visibility nằm **trong expression** như `execution(public * *(..))` mới giới hạn **những method được khớp**.

```java
@Pointcut("execution(public * demo.visibility..*Service.*(..))")
private void servicePublicOperations() {}
```

- `private` ở khai báo chỉ nói named pointcut `servicePublicOperations()` được dùng trong phạm vi khai báo tương ứng, thường trong chính aspect.
- `public` nằm trong `execution(public * ...)` mới là điều kiện yêu cầu **target method public**.
- Method `servicePublicOperations()` không được gọi để chạy business logic; nó chỉ đặt tên cho biểu thức.

Ngược lại:

```java
@Pointcut("execution(* demo.visibility..*Service.*(..))")
public void serviceOperations() {}
```

- `public void serviceOperations()` cho phép các aspect/class khác **tham chiếu named pointcut** nếu dùng cú pháp truy cập phù hợp.
- Ký tự `*` trong expression không giới hạn visibility method thành `public`. Nó là wildcard cho return type, và không có visibility modifier explicit trong method pattern.
- Dù expression có thể mô tả một tập method rộng hơn, Spring AOP **vẫn chỉ intercept được lời gọi đi qua Spring proxy** và những method mà loại proxy hỗ trợ. Public của declaration **không** biến private/static/final method thành interceptable.

**Kết luận cốt lõi:** *visibility của pointcut declaration ảnh hưởng việc **tham chiếu tên**; không thay đổi **matching** của expression.* Đây là quy tắc được Spring Framework nêu trực tiếp trong tài liệu “Declaring a Pointcut”.

Bài chỉ tập trung vào Spring AOP proxy và cách tổ chức/reuse named pointcut trong Spring; không đi sâu vào AOP nói chung hay full AspectJ weaving.

## 2. Cú pháp `@Pointcut` và vai trò của method khai báo

Trong cú pháp `@AspectJ`, method khai báo named pointcut phải trả về `void`; tên method là symbol được các pointcut/advice khác tham chiếu. Ví dụ:

```java
@Pointcut("execution(public * com.example.service..*.*(..))")
private void selectedPublicServiceMethods() {}
```

`private` đứng trước `void` thuộc **Java method khai báo pointcut**. `public` nằm bên trong `execution(...)` là **modifier yêu cầu ở method đích**. Gọi body `selectedPublicServiceMethods()` như Java method thường không kích hoạt advice. Bài khai báo named pointcut phân tích toàn bộ signature và binding.

## 3. Visibility khai báo chỉ ảnh hưởng cách tái sử dụng named pointcut

Quy tắc Java visibility quyết định **quyền tham chiếu symbol**: `private` trong declaring type; package-private trong cùng Java package; `protected` theo package/subclass access; `public` từ các package khác khi declaring class cũng accessible. Không modifier nào trong số đó thêm hoặc bớt method execution được expression match.

Như vậy, `@Pointcut("execution(public * *(..))") private void p() {}` vẫn chọn **public target methods** khi được advice trong cùng class tham chiếu; `@Pointcut("execution(private * *(..))") public void p() {}` dù công khai symbol cũng không thể intercept private target methods bằng Spring proxy. Bài **“Visibility của phương thức pointcut và phạm vi tái sử dụng”** có ma trận access và ví dụ chia sẻ liên module.

## 4. Visibility của target method được mô tả bằng expression

**Target method visibility** phải xét ở **bên trong** biểu thức Spring AOP, ví dụ:

```java
@Pointcut("execution(public * demo.visibility.OrderService.*(..))")
private void onlyPublicServiceMethods() {}
```

Giải nghĩa từng phần:

- `execution(...)`: chọn method **execution join point** mà Spring AOP hỗ trợ.
- `public`: method modifier được yêu cầu khớp.
- `*` đầu tiên: return type bất kỳ.
- `demo.visibility.OrderService`: declaring/target type expression.
- `*` trước `(..)`: tên method bất kỳ.
- `(..)`: chấp nhận mọi danh sách tham số.

Một expression khác:

```java
@Pointcut("execution(* demo.visibility.OrderService.*(..))")
private void anyDeclaredServiceMethod() {}
```

không ràng buộc `public` ở vị trí modifier. Về mặt mô tả, biểu thức rộng hơn trường hợp chỉ public. Nhưng **Spring AOP vẫn có giới hạn của proxy** — không nên suy ra mọi `private`/`static` method mà text của expression có thể mô tả đều được advice.

### 4.1. `within(...)` không tự đồng nghĩa với public

```java
@Pointcut("within(demo.visibility..*)")
public void inVisibilityModule() {}
```

`within` giới hạn type/package mà method execution thuộc về; nó **không có điều kiện method phải public**. Nếu yêu cầu chỉ public, viết:

```java
@Pointcut("within(demo.visibility..*)"
        + " && execution(public * *(..))")
public void publicMethodsInModule() {}
```

Một public pointcut declaration kết hợp `within` cũng không tự làm điều kiện `execution(public...)` xuất hiện.

### 4.2. Named pointcut giống macro biểu thức có tên

Ví dụ:

```java
@Pointcut("execution(public * *(..))")
private void publicMethods() {}

@Pointcut("within(demo.visibility..*)")
private void inModule() {}

@Pointcut("publicMethods() && inModule()")
public void publicModuleMethods() {}
```

Khi Spring phân tích `publicModuleMethods()`, điều kiện thực chất là **public method** VÀ **nằm trong module**. Modifier `private` của hai helper pointcut không bị “truyền” thành một phần điều kiện matching. Modifier `public` của pointcut tổng hợp chỉ giúp module khác tham chiếu symbol pointcut.

## 5. Phạm vi Spring proxy còn quan trọng hơn expression visibility

Spring Framework hỗ trợ chủ yếu những **method execution** mà proxy có thể nhận. Điều này tạo một bộ lọc **độc lập** với Java visibility của method `@Pointcut`:

### 5.1. JDK dynamic proxy

JDK proxy thực hiện interface. Trong Spring AOP, nó intercept các **public interface method calls** qua reference proxy. Nếu target có public method chỉ trên implementation nhưng không được interface công bố, caller giữ interface proxy không thể gọi method ấy như một business API.

Ví dụ:

```java
interface WorkPort {
    void execute();
}

class WorkService implements WorkPort {
    @Override
    public void execute() {}

    private void helper() {}
}
```

Với JDK proxy triển khai `WorkPort`, lời gọi `port.execute()` qua proxy có thể được advice nếu pointcut phù hợp. `helper()` không thuộc interface nên không có lời gọi public nào trên proxy để intercept.

Điều này **không** liên quan đến việc named pointcut của advice được khai báo `public` hay `private`.

### 5.2. CGLIB class-based proxy

CGLIB tạo subclass của target (nếu class có thể subclass). Spring AOP có thể intercept public và protected method calls trên proxy; một số package-visible methods cũng có thể được intercept nếu visibility cho phép. Tuy nhiên:

- `private` method không thể override, không thể được CGLIB advice.
- `final` instance method không thể override, không thể được CGLIB advice.
- `static` method không phải instance dispatch trên proxy.
- `final class` không thể được tạo subclass CGLIB.
- Một package-private method không visible với subclass ở package khác không thể được override theo cách thông thường.
- Self-invocation `this.method()` vẫn bypass proxy kể cả method đó public và override được.

Tài liệu Spring nêu rõ: nếu pointcut **chỉ** muốn public methods, hãy viết điều kiện public thật sự trong expression, ngay cả khi app dùng CGLIB có thể intercept một số non-public methods. Không dùng visibility của named pointcut làm bộ lọc proxy.

### 5.3. Hai chiều kiểm soát độc lập

| Trường hợp | Visibility khai báo pointcut | Expression match | Spring AOP intercept? |
| --- | --- | --- | --- |
| `private void api()` với `execution(public * *(..))` | Private: chỉ reference trong type khai báo | Public methods | Có thể, khi caller dùng proxy và advice khớp |
| `public void api()` với `execution(public * *(..))` | Public: chia sẻ được | Vẫn public methods | Tương tự dòng trước |
| `public void broad()` với `execution(* *(..))` | Public: chia sẻ được | Không giới hạn modifier | Chỉ các method calls mà proxy hỗ trợ |
| `private void narrow()` với `execution(private * *(..))` | Private: helper nội bộ | Mô tả private methods | Không thể intercept private methods bằng Spring AOP proxy |
| `public void p()` với `within(demo.visibility..*)` | Public: có thể reuse | Type/package scope | Có thể cả non-public trong CGLIB nếu interceptable |
| `public void p()` với `execution(public * *(..)) && within(...)` | Public: có thể reuse | Chỉ public trong scope | Chỉ các lời gọi qua proxy phù hợp |

*“Có thể” không phải chắc chắn: còn cần target bean được quản lý, aspect/advisor được đăng ký, pointcut khớp và lời gọi qua proxy.*

## 6. Ví dụ Maven chạy được: private named pointcut match public target

Ví dụ đầy đủ dùng **Java 17, Maven, Spring Framework 6.2.x** không cần Spring Boot. Mục tiêu:

- Dùng `private @Pointcut` để chọn **public method** và quan sát advice chạy.
- Dùng `public @Pointcut` để công bố một named pointcut có expression **không ghi điều kiện visibility**.
- Aspect khác sử dụng named pointcut `public` bằng tên đầy đủ.
- Một private helper của target không chạy advice, dù nó nằm trong class mà public pointcut mô tả.
- Một lời gọi `this.pay()` từ target không đi qua AOP proxy, dù target method public.

Ta dùng hai aspects với advice khác nhau áp dụng trên **hai method khác nhau** để stdout không phụ thuộc thứ tự chạy của nhiều advice trên cùng một invocation.

### 6.1. Cấu trúc dự án

```text
spring-pointcut-visibility/
├── pom.xml
└── src/main/java/demo/visibility/
    ├── AppConfig.java
    ├── OrderService.java
    ├── LocalAspect.java
    ├── CommonPointcuts.java
    ├── SharedAspect.java
    └── Main.java
```

### 6.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-pointcut-visibility</artifactId>
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

### 6.3. `AppConfig.java`

```java
package demo.visibility;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy
public class AppConfig {
}
```

`@ComponentScan` tìm target service và các aspect có `@Component`. `@EnableAspectJAutoProxy` bật AOP proxying. Target không triển khai interface nên Spring Framework sẽ dùng class-based proxy cho target trong ví dụ. Tất cả method cần quan sát từ bên ngoài là public/non-final.

### 6.4. `OrderService.java` — target có public methods và private helper

```java
package demo.visibility;

import org.springframework.stereotype.Service;

@Service
public class OrderService {
    public void pay() {
        System.out.println("[target] pay");
    }

    public void notifyCustomer() {
        System.out.println("[target] notify");
        privateHelper();
    }

    public void batch() {
        System.out.println("[target] batch");
        this.pay(); // Self-invocation: không đi qua proxy.
    }

    private void privateHelper() {
        System.out.println("[target] privateHelper");
    }
}
```

`pay()` và `notifyCustomer()` là public method mà Spring AOP proxy có thể intercept từ caller bên ngoài; `privateHelper()` không thể override/intercept; `batch()` dùng để minh họa self-invocation.

### 6.5. `LocalAspect.java` — private named pointcut chọn public `pay()`

```java
package demo.visibility;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LocalAspect {
    // private là visibility của METHOD KHAI BÁO pointcut.
    // public bên trong execution là visibility của TARGET METHOD.
    @Pointcut("execution(public * demo.visibility.OrderService.pay(..))")
    private void payPublicMethod() {}

    @Before("payPublicMethod()")
    public void beforePay() {
        System.out.println("[local advice] before pay");
    }
}
```

Đây là ví dụ trung tâm: method `payPublicMethod()` **private** vẫn là một named pointcut hợp lệ và match **public** `OrderService.pay()`. Spring không cố gọi `payPublicMethod()` như logic Java để chạy advice. Method declaration chỉ cung cấp **tên** được `@Before` sử dụng trong cùng aspect.

### 6.6. `CommonPointcuts.java` — public named pointcut chia sẻ liên aspect

```java
package demo.visibility;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class CommonPointcuts {
    // Method pointcut PUBLIC để aspect khác có thể tham chiếu.
    // Expression không có điều kiện public/protected/private.
    @Pointcut("execution(* demo.visibility.OrderService.*(..))")
    public void anyOrderServiceMethod() {}
}
```

Class này đóng vai trò thư viện named pointcuts. Một `public @Pointcut` cho phép reuse giữa các aspect. Nó không có nghĩa tự động advice mọi method, cũng không tạo advice khi không có `@Before`/`@After`/`@Around` sử dụng expression.

### 6.7. `SharedAspect.java` — gọi lại pointcut từ class khác

```java
package demo.visibility;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SharedAspect {
    // Reuse public pointcut từ một aspect khác bằng FQN.
    // Giao với method cụ thể để không trùng advice của LocalAspect.
    @Before("demo.visibility.CommonPointcuts.anyOrderServiceMethod()"
          + " && execution(public * demo.visibility.OrderService.notifyCustomer(..))")
    public void beforeNotify() {
        System.out.println("[shared advice] before notify");
    }
}
```

Đây là bằng chứng **public named pointcut có thể được tham chiếu từ aspect khác**. Modifier của `anyOrderServiceMethod()` cho phép cross-aspect reuse, trong khi **tập phương thức thực sự được chọn** là giao expression rộng với `execution(public * ...notifyCustomer(..))`.

Nếu đổi method trong `CommonPointcuts` thành `private` nhưng vẫn giữ tham chiếu FQN từ `SharedAspect`, cấu hình không còn quyền reference named pointcut như đang viết. Đó là vấn đề **visibility của declaration**, không phải lỗi matching của `notifyCustomer()`.

### 6.8. `Main.java`

```java
package demo.visibility;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService orders = ctx.getBean(OrderService.class);

            System.out.println("isAopProxy=" + AopUtils.isAopProxy(orders));
            orders.pay();
            orders.notifyCustomer();
            orders.batch();
        }
    }
}
```

Chạy từ thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.visibility.Main
```

Các dòng stdout do chương trình in, không kể log Maven/Spring tùy môi trường:

```text
isAopProxy=true
[local advice] before pay
[target] pay
[shared advice] before notify
[target] notify
[target] privateHelper
[target] batch
[target] pay
```

**Vì sao có kết quả trên?**

1. `OrderService` là Spring bean; Spring có advisor từ hai aspects và có thể tạo CGLIB proxy (target không có interface). `isAopProxy=true`.
2. Caller gọi `orders.pay()` qua proxy. Advice tham chiếu private pointcut `payPublicMethod()` khớp public target method `pay()` và chạy.
3. Caller gọi `orders.notifyCustomer()` qua proxy. Advice ở `SharedAspect` tham chiếu public named pointcut của `CommonPointcuts`; nhánh kết hợp chọn `notifyCustomer()`.
4. `privateHelper()` được target gọi trực tiếp; không có advice vì private method không thể được CGLIB override.
5. `orders.batch()` không khớp advice nào trong ví dụ. Trong target `batch()`, `this.pay()` bypass proxy, nên in `[target] pay` mà không có `[local advice]` trước nó.
6. Không có hai advice cùng đánh vào một invocation trong ví dụ, nên không cần giả định thứ tự execution giữa nhiều advice trên một method.

Lưu ý nếu có advice khác trong ứng dụng thực, stdout sẽ thêm dòng tùy pointcut và advice order. Chương trình trên chỉ đăng ký các aspects được khai báo trong thư mục ví dụ.

## 7. Những thí nghiệm kiểm chứng hai chiều visibility

### Thí nghiệm A — Chuyển private pointcut thành public

Trong `LocalAspect`, chỉ đổi:

```java
@Pointcut("execution(public * demo.visibility.OrderService.pay(..))")
public void payPublicMethod() {}
```

Không đổi expression và advice. Khi chạy lại, các dòng `[local advice] before pay` vẫn xuất hiện **đúng như trước**. Tập matching của pointcut không phụ thuộc `private` hay `public` của declaration.

### Thí nghiệm B — Chuyển public pointcut thành private

Trong `CommonPointcuts`, đổi:

```java
@Pointcut("execution(* demo.visibility.OrderService.*(..))")
private void anyOrderServiceMethod() {}
```

Giữ nguyên tham chiếu FQN trong `SharedAspect`. Spring/AspectJ parser không có quyền tham chiếu một **private named pointcut** từ một type khác như public API. Kết quả có thể là **lỗi phân tích pointcut / khởi tạo ApplicationContext** thay vì một thay đổi số method match. Không dựa vào đúng tên exception vì thông báo phụ thuộc phiên bản thư viện.

Sửa bằng cách giữ method reusable `public`, hoặc đưa expression riêng vào cùng aspect khi chỉ cần sử dụng nội bộ.

### Thí nghiệm C — Giữ public declaration, giới hạn expression thành private

Đổi pointcut trong `CommonPointcuts` thành:

```java
@Pointcut("execution(private * demo.visibility.OrderService.*(..))")
public void onlyPrivateOrderMethods() {}
```

Đồng thời đổi advice để tham chiếu tên mới, ví dụ:

```java
@Before("demo.visibility.CommonPointcuts.onlyPrivateOrderMethods()")
public void beforePrivate() {
    System.out.println("[shared advice] before private");
}
```

Hai fragment phải thay **đồng bộ** trong hai file để tham chiếu tên chính xác. Trên Spring AOP proxy thông thường, private `OrderService.privateHelper()` không thể được CGLIB intercept, nên lời gọi nội bộ đến helper vẫn không có advice. Việc named pointcut được khai báo `public` **không cấp thêm khả năng intercept private method**.

### Thí nghiệm D — Giữ private declaration, đổi expression rộng hơn

Trong `LocalAspect` đổi:

```java
@Pointcut("execution(public * demo.visibility.OrderService.*(..))")
private void payPublicMethod() {}
```

Tên `payPublicMethod` có thể không còn phản ánh chính xác ý nghĩa expression; đây là một **tên nên đổi** khi thật sự mở rộng pointcut. Nếu giữ tên và advice như cũ, nó có thể advice cả `pay()`, `notifyCustomer()` và `batch()` khi caller gọi từ ngoài proxy. Đây là thay đổi **matching do sửa expression**, không phải do method pointcut private.

Vì dễ nhầm khi debug, hãy đặt tên pointcut mô tả **điều kiện match**, tránh đặt tên theo modifier của declaration. Ví dụ tên tốt hơn là `publicOrderServiceMethods()`.

## 8. Tại sao private pointcut có thể match public method mà private target method không được advice?

Hai tình huống này khác hẳn nhau:

**Trường hợp 1 — private named pointcut:**

```java
@Pointcut("execution(public * demo.visibility.OrderService.pay(..))")
private void chosenPay() {}

@Before("chosenPay()")
public void beforePay() {
    System.out.println("before pay");
}
```

Method Java `chosenPay()` có visibility private, nhưng nó không phải method đích bị advice. Nó chỉ là cách đặt tên cho expression. Spring AOP không cần cho caller bên ngoài gọi `chosenPay()`.

**Trường hợp 2 — private target business method:**

```java
public class OrderService {
    private void privateHelper() {
        System.out.println("helper");
    }
}
```

Method `privateHelper()` là method nghiệp vụ/implementation bên trong target. Với Spring AOP proxy, private method không nằm trên JDK interface proxy surface và không thể được CGLIB subclass override. Dù named pointcut của aspect được `public`, Spring vẫn không intercept được lời gọi riêng tư này.

**Đừng nói “pointcut private thì không bắt được method”.** Phải hỏi **private của method khai báo pointcut hay private của method đích?**

## 9. Các lỗi khi tổ chức named pointcut trong dự án Spring

Ba nhóm lỗi thường bị nhập nhằng:

- **Visibility declaration:** aspect khác tham chiếu `OtherAspect.privatePc()` nên không resolve; cần công bố public named pointcut hoặc đưa rule về đúng nơi sở hữu.
- **Visibility target:** expression không có `public` có thể mô tả method rộng hơn, nhưng JDK proxy chỉ expose interface methods, CGLIB không override `private`/`final` target methods. Đổi modifier của named pointcut không sửa được giới hạn này.
- **Ranh giới lời gọi:** target có thể match expression mà `this.pay()` hoặc `new Service().pay()` không đi qua Spring proxy; aspect bean cũng phải được đăng ký đúng context.

Một Spring bean có proxy không đồng nghĩa mọi method được advice. Hãy assert output/counter của **advice cụ thể**, so sánh một public target method khớp với một private/final hoặc method ngoài scope không thể chặn.

## 10. Câu hỏi tự kiểm tra

**Tự kiểm tra:**

1. `@Pointcut("execution(public * *(..))") private void anyPublic() {}` có thể match public service method? **Có**, nếu advice cùng class tham chiếu và target được gọi qua proxy phù hợp.
2. Đổi `private` thành `public` ở named pointcut thì tập method match thay đổi? **Không**, khi expression/cấu hình giữ nguyên; chỉ phạm vi reference được mở rộng.
3. `@Pointcut("execution(* *(..))") public void allMethods() {}` có thể advice private target method? **Không** qua Spring AOP proxy thông thường.
4. `protected @Pointcut` có chọn protected target methods không? **Không tự động**; muốn lọc target modifier, đặt điều kiện trong expression.
5. Vì sao self-invocation vẫn không advice khi method public? Vì caller `this.method()` bypass Spring proxy.

**Tài liệu:** [Spring — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html), [Spring — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reusable-pointcuts-006">Đặt tên theo ý định thay vì chi tiết chuỗi biểu thức</a>

<details>
<summary>Xem chi tiết</summary>

# Đặt tên Pointcut theo ý định thay vì chi tiết chuỗi biểu thức trong Spring AOP

## 1. Mục tiêu

Trong Spring AOP, viết trực tiếp biểu thức dài vào `@Before` hoặc `@Around` có thể hoạt động đúng hôm nay, nhưng khó đọc và khó bảo trì. Khi biểu thức đó được sao chép ở nhiều advice, người sửa thường không biết nó đại diện cho **ý định nghiệp vụ nào**: giám sát tất cả thao tác nghiệp vụ, audit phê duyệt thanh toán, hay chỉ chặn method của một bean cụ thể?

Spring Framework cho phép định nghĩa **named pointcut** bằng `@Pointcut` đặt trên một phương thức Java trả về `void`, sau đó tham chiếu bằng tên trong advice hoặc kết hợp những pointcut nhỏ thành một pointcut có ý nghĩa hơn. Mục tiêu quan trọng nhất là để người đọc hiểu **vì sao các method ấy thuộc phạm vi advice**, thay vì phải giải mã chi tiết ký tự của một expression dài mỗi khi review code.

Bài này thuộc phần **Tổ chức Pointcut** của Spring Framework và chỉ tập trung vào Spring Aspect/Spring AOP dạng proxy, bao gồm:

1. Phân biệt **tên mô tả ý định** với **biểu thức hiện thực**.
2. Tổ chức các pointcut nhỏ và pointcut tổng hợp bằng `@Pointcut`.
3. Chia sẻ pointcut giữa các Spring aspect mà vẫn tôn trọng Java visibility.
4. Tránh đặt tên sai khiến code review bỏ sót thay đổi phạm vi áp dụng.
5. Viết ứng dụng Java 17+ chạy được để chứng minh một tên nghiệp vụ match đúng bean/method mong muốn.
6. Xây dựng kiểm thử tích cực và tiêu cực để có thể thay expression mà giữ nguyên hợp đồng của pointcut.

Các khái niệm Aspect chung không được lặp lại; trọng tâm là **Spring đọc named pointcut, tạo advisor và áp dụng lên Spring-managed beans**.

## 2. Tên pointcut trả lời câu hỏi gì?

So sánh hai cách viết một advice:

**Cách 1 — nhúng chi tiết expression trong advice:**

~~~java
@Before(
    "within(example.payment..*)"
    + " && execution(public * approve(..))"
)
public void audit() {
    System.out.println("audit");
}
~~~

**Cách 2 — tham chiếu tên thể hiện mục đích:**

~~~java
@Before("example.aop.PaymentPointcuts.paymentApprovalOperation()")
public void audit() {
    System.out.println("audit");
}
~~~

Ở cách 2, người đọc thấy ngay đó là **thao tác phê duyệt thanh toán**. Expression vẫn tồn tại ở nơi định nghĩa named pointcut nhưng không lặp lại trong mọi advice. Người duy trì có thể đổi cách nhận diện thao tác — theo package, kiểu, annotation hoặc tên bean — mà không phải viết lại nhiều advice.

**Tên pointcut là một hợp đồng ngữ nghĩa trong code**:

- Tên nên biểu đạt **tập method/bean mà advice định can thiệp**.
- Tên không nên mô tả trực tiếp **cú pháp sẽ dễ thay đổi** nếu cú pháp chỉ là một chi tiết triển khai.
- Tên càng công khai và được nhiều aspect tái sử dụng, càng cần ổn định, có tài liệu và kiểm thử phạm vi.
- **Tên không thay đổi kết quả matching.** Chính expression trong `@Pointcut` vẫn xác định các method thật sự được chọn.

Ví dụ: `paymentApprovalOperation()` là một tên có ý nghĩa nghiệp vụ; `executionStarApproveDotDot()` chỉ diễn giải ký tự expression. Tuy vậy, `allPaymentOperations()` **không phải** tên tốt nếu expression chỉ match `approve()`: tên rộng hơn thực tế gây hiểu nhầm.

## 3. Named pointcut trong Spring AOP vận hành như thế nào?

Định nghĩa named pointcut bằng method `void` với body thường rỗng; Spring dùng **expression metadata** làm điều kiện match, còn tên method cho phép advice diễn đạt mục đích rõ ràng:

```java
public class PaymentPointcuts {
    @Pointcut("within(example.payment..*) && execution(public * approve(..))")
    public void paymentApprovalOperation() {}
}
```

Ở một Spring-managed aspect, `@Before("example.aop.PaymentPointcuts.paymentApprovalOperation()")` tham chiếu định nghĩa này nếu class thật đặt trong package `example.aop`. Class chỉ lưu pointcut không cần là Spring bean; aspect có advice phải là bean. Bài **“Khai báo pointcut có tên bằng @Pointcut”** đi sâu vào signature, visibility, binding và cú pháp tham chiếu.

## 4. Đặt tên đúng: nhóm ý định và nhóm kỹ thuật

Không cần cấm mọi tên mô tả kỹ thuật. Điều nên tránh là biến **chi tiết expression** thành API công khai mà advice phụ thuộc vào, trong khi ý định nghiệp vụ thực sự bị ẩn.

| Tên | Đánh giá | Lý do |
| --- | --- | --- |
| `paymentApprovalOperation()` | Tốt nếu chỉ match phê duyệt thanh toán | Biểu đạt ý định của advice và có thể dùng khi refactor expression |
| `businessServiceOperation()` | Tốt nếu scope thật là tầng service nghiệp vụ | Phù hợp khi nhiều aspect dùng chung boundary service |
| `inPaymentModule()` | Tốt cho pointcut nhỏ phục vụ kết hợp | Mô tả một điều kiện scoping rõ ràng |
| `publicMethod()` | Tốt cho primitive pointcut dùng nội bộ | Thật thà về phạm vi kỹ thuật, nên phối hợp với scoping |
| `executionPublicStarStar()` | Nên tránh | Chỉ lặp lại cú pháp, khó hiểu mục đích nghiệp vụ |
| `allServices()` | Sai nếu expression chỉ chọn một class | Tên tạo kỳ vọng rộng hơn behavior |
| `secureOperation()` | Nguy hiểm nếu chỉ có pattern tên method | Hứa hẹn tính an toàn mà expression không chứng minh |
| `paymentOperation()` | Quá mơ hồ nếu chỉ chọn `approve()` | Người đọc dễ tưởng mọi method trong payment đều match |
| `pointcut1()` / `aopCheck()` | Không nên dùng | Không có thông tin phạm vi hay ý định |
| `forLogging()` | Cần xem lại | Nêu **hành động advice** chứ không chỉ rõ nơi advice phải chạy |

Với một tên public được dùng ở nhiều nơi, có thể kèm Javadoc: “chọn những public approve operations thuộc payment module” và mô tả **ngoại lệ** như không chọn preview, report hoặc lời gọi trên đối tượng ngoài Spring.

## 5. Tách pointcut theo tầng và kết hợp theo ý định

Spring Framework hỗ trợ tham chiếu named pointcut bằng tên và kết hợp bằng `&&`, `||`, `!`.

**Mẫu đề xuất:**

~~~text
Nhóm primitive pointcuts:
  inPaymentModule()
  publicApprovalMethod()
  hasAuditMarker()

          \______/  \_______/
                |
Nhóm intent pointcuts:
  paymentApprovalOperation()
  sensitiveBusinessOperation()
                |
             Advice
  @Before("...paymentApprovalOperation()")
  @AfterReturning("...paymentApprovalOperation()")
~~~

Một pointcut nghiệp vụ nên đặt ở lớp **gần module sở hữu hợp đồng**. Khi dùng chung trong hệ thống lớn, một lớp chuyên biệt như `CommonPointcuts` có thể cung cấp những boundary ổn định (tầng service/data access); một lớp `PaymentPointcuts` chuyên biệt có thể đặt các tên nghiệp vụ payment. Tránh tạo một tập “global utility pointcuts” quá lớn không có chủ sở hữu.

### 5.1. Quy tắc kết hợp cần review

| Biểu thức | Ý nghĩa | Tác động phạm vi |
| --- | --- | --- |
| `inPaymentModule() && publicApprovalMethod()` | Public approve method **trong** payment | Giao hai tập, thường hẹp hơn |
| `inPaymentModule() || publicApprovalMethod()` | Method thuộc payment **hoặc** public approve ở bất kỳ đâu | Rộng hơn, dễ audit nhầm |
| `inPaymentModule() && !publicApprovalMethod()` | Những method thuộc payment trừ approve | Có thể ngược ý định hoàn toàn |
| `inPaymentModule() && hasAuditMarker()` | Những method được đánh dấu trong module payment | Tốt khi chủ động chọn opt-in |
| `paymentApprovalOperation() && bean(primaryPaymentService)` | Thao tác approve của một Spring bean đúng tên | Hẹp hơn nhưng gắn với bean naming |

Khi muốn đổi từ method-name pattern sang annotation opt-in, chỉ đổi phần triển khai pointcut **sau khi** xác nhận rằng việc đổi tập match là có chủ đích hoặc các test chứng minh tập mong muốn không thay đổi. Không được coi tên pointcut là một lời cam kết được runtime tự kiểm tra.

### 5.2. Chọn đủ scoping để không match ngoài ý muốn

Biểu thức `execution(public * approve(..))` đứng riêng có thể match `approve()` của report, inventory hoặc user administration. Biểu thức `within(example.payment..*)` đứng riêng có thể match mọi method trong module thanh toán. Ghép bằng `&&` diễn đạt intent “phê duyệt thuộc payment”.

Spring AOP hỗ trợ **method execution trên Spring beans**, không hỗ trợ tất cả AspectJ designators. Đối với Spring proxy-based AOP, các lựa chọn thường gặp là `execution`, `within`, `this`, `target`, `args`, `@annotation` và `bean`. Đặc biệt `bean(...)` là phần mở rộng **riêng của Spring AOP**; không nên chuyển expression đó nguyên dạng sang AspectJ native weaving.

## 6. Ví dụ Java 17+ chạy được: một tên pointcut, hai loại advice

Ví dụ sử dụng:

- **Spring Framework 6.2.19**, Java **17+**, **AspectJ Weaver 1.9.25.1**.
- Một lớp khai báo named pointcuts **không phải Spring bean**.
- Một aspect được đăng ký **bằng `@Bean`**, có cả `@Before` và `@AfterReturning` cùng tham chiếu **một tên nghiệp vụ**.
- Hai Spring service beans và ba lời gọi để kiểm tra phạm vi positive/negative.
- `@EnableAspectJAutoProxy` với class-based proxy để dễ lấy concrete type trong chương trình minh họa.

### 6.1. Cấu trúc dự án

~~~text
spring-intent-pointcut-demo/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── Main.java
    ├── aop/
    │   ├── PaymentPointcuts.java
    │   └── PaymentAuditAspect.java
    ├── payment/
    │   └── PaymentService.java
    └── report/
        └── ReportService.java
~~~

### 6.2. Maven: `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>example</groupId>
    <artifactId>spring-intent-pointcut-demo</artifactId>
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

Spring Framework BOM quản lý các module `spring-*` để không lệch phiên bản. AspectJ Weaver là dependency cho xử lý pointcut biểu thức trong Spring AOP `@AspectJ`; sự hiện diện của artifact không tự bật bytecode weaving.

### 6.3. Named pointcuts: `PaymentPointcuts.java`

~~~java
package example.aop;

import org.aspectj.lang.annotation.Pointcut;

public class PaymentPointcuts {

    @Pointcut("within(example.payment..*)")
    public void inPaymentModule() {}

    @Pointcut("execution(public * approve(..))")
    public void publicApprovalMethod() {}

    /**
     * Phê duyệt thanh toán: public approve method trên type
     * thuộc package example.payment hoặc các package con.
     */
    @Pointcut("inPaymentModule() && publicApprovalMethod()")
    public void paymentApprovalOperation() {}
}
~~~

Ba named pointcuts ở trên là **khai báo metadata**; không có lời gọi Java thông thường đến những method rỗng này. Tên tổng hợp `paymentApprovalOperation` sẽ được dùng trong advice. Nếu thay đổi cách biểu diễn module hay phương thức, nơi sửa nằm ở `PaymentPointcuts`, không rải trong mỗi aspect.

Vì các signature trên là `public`, chúng có thể được tham chiếu bằng **fully qualified name** từ lớp `@Aspect` khác. Không cần đánh dấu `PaymentPointcuts` bằng `@Component` hoặc đăng ký nó làm Spring bean chỉ để tham chiếu những pointcut definitions đã khai báo.

### 6.4. Aspect: `PaymentAuditAspect.java`

~~~java
package example.aop;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class PaymentAuditAspect {

    @Before("example.aop.PaymentPointcuts.paymentApprovalOperation()")
    public void beforePaymentApproval() {
        System.out.println("audit: before payment approval");
    }

    @AfterReturning("example.aop.PaymentPointcuts.paymentApprovalOperation()")
    public void afterPaymentApproval() {
        System.out.println("audit: after payment approval");
    }
}
~~~

Hai advice có cách thức thực thi khác nhau nhưng chia sẻ **một ý định chọn method**. Nếu bạn đổi expression tại `PaymentPointcuts.paymentApprovalOperation()`, cả hai advice đều cùng nhận phạm vi mới.

Chỉ `@Aspect` trên class này chưa đủ để Spring scan thành bean; ví dụ đăng ký `PaymentAuditAspect` bằng method `@Bean` trong `AppConfig`.

### 6.5. Service payment: `PaymentService.java`

~~~java
package example.payment;

public class PaymentService {

    public void preview() {
        System.out.println("payment: preview");
    }

    public void approve() {
        System.out.println("payment: approve");
    }
}
~~~

Method `approve()` nằm trong payment module và là public method, còn `preview()` không có tên approve. Cả hai đều được gọi qua cùng một bean, nhưng chỉ một method nên nhận audit.

### 6.6. Service report: `ReportService.java`

~~~java
package example.report;

public class ReportService {

    public void approve() {
        System.out.println("report: approve");
    }
}
~~~

ReportService cũng có method `approve()`, nhưng nằm ngoài package `example.payment` nên **không đáp ứng** `inPaymentModule()`. Đây là negative case giúp chứng minh tên pointcut không chỉ là cách đặt tên đẹp, mà phải đi kèm **biểu thức hẹp đúng ý định**.

### 6.7. Spring configuration: `AppConfig.java`

~~~java
package example;

import example.aop.PaymentAuditAspect;
import example.payment.PaymentService;
import example.report.ReportService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public PaymentService paymentService() {
        return new PaymentService();
    }

    @Bean
    public ReportService reportService() {
        return new ReportService();
    }

    @Bean
    public PaymentAuditAspect paymentAuditAspect() {
        return new PaymentAuditAspect();
    }
}
~~~

Nhờ `@Bean`, Spring container có một aspect bean. Spring AOP nhận diện pointcut/advice của bean ấy, tìm target class/method phù hợp và tạo proxy khi cần. `proxyTargetClass=true` giúp chương trình lấy bean qua class cụ thể; đặt CGLIB **không làm thay đổi ý nghĩa của named pointcut**.

### 6.8. Chạy chương trình: `Main.java`

~~~java
package example;

import example.payment.PaymentService;
import example.report.ReportService;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            PaymentService payment = context.getBean(PaymentService.class);
            ReportService report = context.getBean(ReportService.class);

            System.out.println("payment proxy: " + AopUtils.isAopProxy(payment));
            System.out.println("report proxy: " + AopUtils.isAopProxy(report));

            System.out.println("-- payment preview --");
            payment.preview();

            System.out.println("-- report approval --");
            report.approve();

            System.out.println("-- payment approval --");
            payment.approve();
        }
    }
}
~~~

Từ thư mục có `pom.xml` chạy:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
~~~

**Các dòng do chương trình in ra theo đúng thứ tự:**

~~~text
payment proxy: true
report proxy: false
-- payment preview --
payment: preview
-- report approval --
report: approve
-- payment approval --
audit: before payment approval
payment: approve
audit: after payment approval
~~~

### 6.9. Đọc kết quả như một phép kiểm chứng hợp đồng tên

- **`payment proxy: true`:** Spring tìm thấy advisor có thể áp dụng cho ít nhất một method của `PaymentService`.
- **`report proxy: false`:** Không có advisor nào trong cấu hình tối giản này match `ReportService`; Spring không cần proxy nó cho mục đích AOP ấy.
- **`payment.preview()`:** Caller gọi trên AOP proxy, nhưng method `preview` không khớp named pointcut tổng hợp, nên không có audit advice.
- **`report.approve()`:** Tên method đúng nhưng package không nằm trong payment module, nên không có audit advice.
- **`payment.approve()`:** Cả `inPaymentModule` và `publicApprovalMethod` đều khớp. `@Before` chạy trước business method; `@AfterReturning` chạy sau khi method hoàn tất bình thường.

Nếu một advisor khác được thêm vào app (caching, transaction, security, custom aspect), `report` có thể nhận proxy vì nguyên nhân khác. Vì vậy `isAopProxy == false` được dự đoán dựa trên **đúng cấu hình ví dụ**, không phải quy luật của mọi app.

## 7. Chia sẻ named pointcut giữa nhiều Spring aspects

Một tên ý định như `paymentApprovalOperation()` có thể được **nhiều aspects** dùng chung: audit `@Before`, metrics `@AfterReturning` hoặc tracing `@Around`. Các aspect vẫn có trách nhiệm riêng, nên dùng chung tập match không tự biến nhiều policies thành một policy hoặc xác định thứ tự advice.

Nếu chỉ một aspect cần điều kiện riêng, nó có thể kết hợp `PaymentPointcuts.paymentApprovalOperation() && bean(primaryPaymentService)` mà không sửa ý định chung cho các consumers khác. Cần kiểm tra bean name thật và lưu ý `bean(...)` là designator Spring AOP riêng, không portable sang native AspectJ weaving.

## 8. Khi nào nên đặt named pointcut vào lớp riêng?

Tạo class `PaymentPointcuts` hoặc `CommonPointcuts` khi nhiều advice/aspects thực sự cùng phụ thuộc **một tập lời gọi có ý nghĩa ổn định**. Giữ pointcut nội bộ `private` nếu chỉ một aspect cần, công bố `public` khi có consumers bên ngoài; không cần tách mỗi biểu thức một dòng thành public catalog.

Đặt catalog gần domain/module sở hữu chính sách để code review thấy ai chịu trách nhiệm khi đổi expression. Một thư viện chung quá lớn dễ làm những modules không liên quan phụ thuộc lẫn nhau.

## 9. Refactor an toàn: đổi biểu thức mà không đổi ý định

Giả sử hệ thống ban đầu định nghĩa:

~~~java
@Pointcut("within(example.payment..*) && execution(public * approve(..))")
public void paymentApprovalOperation() {}
~~~

Sau refactor, thay vì dựa vào tên method và package, nhóm phát triển muốn đánh dấu các operation bằng `@PaymentApproval`. Ý định có thể vẫn là “những thao tác phê duyệt thanh toán”, nhưng **tập method được match có thể thay đổi**.

Một phương án có chủ đích:

~~~java
@Pointcut(
    "within(example.payment..*)"
    + " && @annotation(example.aop.PaymentApproval)"
)
public void paymentApprovalOperation() {}
~~~

Ở đây `@PaymentApproval` là **annotation tự định nghĩa**. Mẫu annotation cần có `@Target(ElementType.METHOD)` và `@Retention(RetentionPolicy.RUNTIME)`; đặt nó đúng trên các method thực sự cần audit. Các snippet chỉ minh họa hướng refactor, **không thuộc project độc lập** ở mục 6.

Nếu một method chưa được gắn annotation, nó sẽ không còn được advice. Nếu một method không có tên `approve` được gắn annotation thì nó có thể bắt đầu được advice. **Không được coi hai biểu thức là tương đương chỉ vì tên method `paymentApprovalOperation` không đổi**.

### 9.1. Quy trình refactor khuyến nghị

1. Ghi một danh sách **positive matches** theo hợp đồng nghiệp vụ, ví dụ PaymentService.approve.
2. Ghi **negative matches**, ví dụ PaymentService.preview, ReportService.approve.
3. Kiểm tra trong context với bean registration và proxy strategy thật.
4. Chỉ thay expression ở định nghĩa named pointcut sau khi hiểu tập match mong muốn.
5. Chạy integration tests để xác minh cả advice behavior và **không can thiệp nhầm** bean ngoài scope.
6. Cập nhật Javadoc/contract nếu ý định nghiệp vụ đã thay đổi.
7. Review thứ tự advisor nếu nhiều aspect sử dụng cùng named pointcut.

Kết quả kỳ vọng: code ở các advice vẫn đọc được theo intent và không đòi sửa hàng loạt khi cần đổi chi tiết nhận diện.

## 10. Đừng nhầm tên named pointcut với bảo đảm bảo mật

Tên như `secureOperation()`, `authorizedPayment()` hoặc `trustedMethod()` có thể khiến người đọc tưởng một chính sách **đã được thực thi**, trong khi `@Pointcut` chỉ định nghĩa **nơi advice có khả năng chạy**.

Ví dụ:

~~~java
@Pointcut("execution(public * example.payment.PaymentService.approve(..))")
public void authorizedPayment() {}
~~~

Method trên không tự kiểm tra authorization, không biết ai gọi, không từ chối quyền truy cập. Trừ khi advice phù hợp thực sự thực thi security rules đúng cách, một pointcut tên `authorizedPayment` không chứng minh thao tác đã được bảo vệ.

Nên dùng tên mô tả **tập lời gọi** như `paymentApprovalOperation()`; để advice mang trách nhiệm riêng về audit, authorization, metrics hoặc transaction và kiểm thử hành vi từng lớp độc lập.

Với security và transaction quan trọng, không nên chỉ dựa vào naming convention hoặc phạm vi pointcut chưa được kiểm chứng; phải test hệ thống thực tế và cả trường hợp gọi nội bộ/self-invocation.

## 11. Những lỗi thường gặp

Các lỗi đặt tên cần bắt lúc review: tên `allPaymentOperations()` nhưng chỉ match `approve()`; `secureOperation()` khiến người đọc tưởng authorization đã được thực thi; tên ký tự expression không cho biết mục đích; hoặc đổi tên pointcut nhưng quên các chuỗi tham chiếu fully-qualified trong advice/XML.

Sau khi đổi implementation từ package/method pattern sang `@annotation(PaymentApproval)`, **giữ cùng tên không bảo đảm giữ cùng tập match**. Dùng positive cases (`PaymentService.approve`) và negative cases (`PaymentService.preview`, `ReportService.approve`) để kiểm chứng ý định, đồng thời xem lại bean registration và proxy boundary.

## 12. Đặt tên có thể giúp review thay đổi AOP

Khi review một pull request Spring Aspect, người đọc nên thấy ngay:

~~~java
@Before("example.aop.PaymentPointcuts.paymentApprovalOperation()")
public void auditPaymentApproval() {
    // ...
}
~~~

Tên cho biết **nơi advice có ý định chạy**; tên advice cho biết **hành vi chạy tại đó**. Chúng giải quyết hai phần khác nhau.

Một review tốt cần xét:

- Ý định nghiệp vụ của `paymentApprovalOperation()` có còn đúng không?
- Expression hiện tại có thật sự chọn **chỉ** những operation ấy?
- Việc thêm module hoặc method mới có vô tình làm mở rộng tập match không?
- Có method phải được advice nhưng không nằm trong phạm vi package/bean/annotation đã chọn không?
- Nhiều aspect sử dụng shared pointcut có bị ảnh hưởng nếu người khác sửa expression không?
- Cấu hình proxy của Spring Boot/Spring Framework có thay đổi từ JDK sang CGLIB và ảnh hưởng method visibility không?
- Có những lời gọi tự gọi nội bộ qua `this` hoặc object được tạo bằng `new` khiến advice không chạy?
- Positive **và** negative tests có đủ để bảo vệ hợp đồng không?

Đặt tên theo ý định giúp việc review **đặt đúng câu hỏi**, nhưng tests và kiểm tra proxy/runtime mới xác nhận hành vi thực tế.

## 13. Tình huống luyện tập

**Tình huống A.** Hai advice đều chứa nguyên chuỗi `within(example.payment..*) && execution(public * approve(..))`. Làm thế nào giảm lặp mà vẫn đọc được rõ ý định?

**Đáp án:** Tạo `@Pointcut` tên `paymentApprovalOperation()`, đặt biểu thức ở một nơi, rồi dùng tên trong cả hai advice.

**Tình huống B.** Pointcut `paymentApprovalOperation()` chỉ chứa `execution(public * approve(..))`. Nó có đúng tên không?

**Đáp án:** Chưa đủ bảo đảm. Nó còn chọn method public tên approve trong nhiều package khác. Bổ sung scoping/payment condition hoặc đổi tên để phản ánh phạm vi thực tế.

**Tình huống C.** Một `PaymentPointcuts` class chứa `@Pointcut` public nhưng không có `@Component`. Named pointcut có dùng từ một Spring-managed `@Aspect` khác được không?

**Đáp án:** Có, Spring @AspectJ support cho phép tham chiếu named pointcuts được công bố trong class khác; lớp chỉ chứa signatures không cần thành bean để được tham chiếu. Điều cần đăng ký là aspect bean có advice.

**Tình huống D.** `@Pointcut` method là `private` trong class A, class B muốn tham chiếu nó bằng tên đầy đủ. Có nên làm thế không?

**Đáp án:** Không. Java visibility áp dụng cho named pointcut references. Nếu cần chia sẻ ngoài class, dùng method public và định nghĩa hợp đồng rõ ràng.

**Tình huống E.** Sau khi đổi implementation sang `@annotation(PaymentApproval)`, tên `paymentApprovalOperation()` không thay đổi. Có thể bỏ kiểm thử phạm vi không?

**Đáp án:** Không. Tập method match có thể khác rất nhiều, dù tên pointcut giữ nguyên.

**Tình huống F.** Một method `payment.preview()` trên bean có proxy không nhận advice. Có phải vì named pointcut không được nạp?

**Đáp án:** Không nhất thiết. Nếu expression chỉ chọn `approve()`, đây là hành vi đúng: một bean có proxy không có nghĩa mọi method đều match cùng advisor.

## 14. Nguồn tài liệu chính thức

- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): chữ ký phương thức `@Pointcut`, tham chiếu tên, tổ hợp biểu thức, visibility, dedicated CommonPointcuts và cách viết good pointcuts.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): `@Before`, `@AfterReturning`, cách tham chiếu named pointcut trong annotation advice.
- [Spring Framework — Pointcut API](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html): `ClassFilter`, `MethodMatcher` và tái sử dụng/composition của Spring pointcut.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): điều kiện đăng ký aspect bean và bật Spring AOP.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): ranh giới JDK/CGLIB proxies và giới hạn self-invocation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reusable-pointcuts-007">Loại trừ bean hạ tầng, aspect và mã ghi nhận dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# Loại trừ bean hạ tầng, aspect và mã ghi nhận dùng chung trong Spring AOP

## 1. Mục tiêu

Một `@Aspect` trong Spring thường xử lý chức năng cắt ngang như audit, ghi log, đo thời gian hoặc theo dõi lời gọi của **Spring service beans**. Nếu pointcut quá rộng, advice không chỉ chạy trên nghiệp vụ mà còn có thể chạy trên **recorder, adapter hạ tầng, logger, metrics collector** – chính những bean mà advice phải gọi đến.

Hãy xét một advice được Spring dùng để ghi nhận mỗi lời gọi:

~~~text
Client -> OrderService proxy
              |
              +-> @Before advice
                       |
                       +-> AuditRecorder.record()
                                    |
                                    +-> Nếu recorder cũng bị advise
                                           |
                                           +-> @Before advice
                                                  |
                                                  +-> AuditRecorder.record()
                                                           |
                                                           +-> ...
~~~

Nếu recorder được gọi **qua Spring AOP proxy** và cũng khớp chính advisor đó, advice có thể tái nhập không kiểm soát và dẫn tới vòng lặp hoặc `StackOverflowError`. Những cấu hình có dependency ngược giữa aspect, recorder và service còn có thể gây lỗi khi khởi tạo container.

Bài học tập trung vào **tổ chức pointcut trong Spring Framework** để loại bỏ đúng các bean kỹ thuật. Sau khi hoàn thành, bạn sẽ:

- Phân biệt chính xác **business bean**, **infrastructure bean**, **aspect bean** và **shared recorder bean**.
- Biết Spring AOP **tự loại class `@Aspect` khỏi auto-proxying với vai trò target**, nhưng **không tự loại mọi collaborator** mà aspect inject vào.
- Xây dựng **allowlist** nghiệp vụ và các biểu thức phủ định bằng `within`, `bean`, `execution`.
- Ghép và chia sẻ `@Pointcut` có tên với `&&`, `||`, `!`.
- Viết ví dụ Spring Framework thuần, **Java 17+**, có output kiểm chứng service được proxy trong khi recorder, inspector và aspect không bị proxy bởi concern này.
- Nhận diện giới hạn của loại trừ theo tên bean, package, class, annotation và những advisor khác.

**Phạm vi:** Chỉ nói về **Spring AOP dựa trên proxy**, không chuyển sang AspectJ weaving hoặc trình bày lại AOP tổng quát.

## 2. Bốn loại bean và trách nhiệm của pointcut

| Loại | Ví dụ | Hành vi mong muốn với business audit |
| --- | --- | --- |
| Spring bean nghiệp vụ | `OrderService`, `InvoiceService` | Được advice khi method thuộc phạm vi audit |
| Bean hạ tầng ứng dụng | `HealthInspector`, `CacheSupport` | Thường được loại, trừ khi cần quan sát hạ tầng |
| Bean `@Aspect` | `LoggingAspect` | Spring AOP không dùng nó làm target để aspect khác advice |
| Bean ghi nhận dùng chung | `AuditRecorder`, `MetricsSink` | Nên loại khỏi chính advisor đang gọi nó |

**Chú ý:** `AuditRecorder` là một Spring bean bình thường. Chỉ vì nó có tên “recorder” hoặc được inject vào `LoggingAspect` **không có nghĩa Spring tự động loại nó khỏi mọi advisor**. Nếu `AuditRecorder.record()` khớp pointcut, recorder có khả năng được bọc proxy.

Ngược lại, theo tài liệu chính thức về **Declaring an Aspect**, class đánh dấu `@Aspect` được Spring AOP nhận diện là một aspect và **loại khỏi auto-proxying với vai trò target**. Điều này không xóa advice của aspect; Spring vẫn lấy advice từ aspect bean để áp dụng lên những business beans phù hợp.

Với bean hạ tầng **do ứng dụng định nghĩa**, đừng dựa vào giả định tất cả bean có annotation `@Component`, `@Configuration` hay tên kết thúc `Manager` đều được framework miễn advice. Một số loại Spring infrastructure nội bộ được tạo/xử lý ở giai đoạn đặc biệt, nhưng chúng không thay thế chính sách loại trừ pointcut của ứng dụng.

## 3. Hai cách tiếp cận: chọn đúng từ đầu hay phủ định sau

### 3.1. Pointcut quá rộng

~~~java
@Before("execution(public * demo..*.*(..))")
public void beforeAnyPublicMethod(JoinPoint jp) {
    recorder.record(jp.getSignature().getName());
}
~~~

Đây là **fragment minh họa nguy cơ**, không phải file Java chạy độc lập. Expression có thể bao phủ nhiều public method của các bean trong namespace `demo`: business service, recorder, helper, kiểm tra health và cấu hình ứng dụng. Nếu recorder cũng được Spring tạo proxy do advisor này, lời gọi `record` từ advice có thể kích hoạt chính advice đó.

Không thử expression nguy hiểm này trong production để “xem có vòng lặp không”. Nên đánh giá bằng test tách biệt, hoặc sửa pointcut theo phương án phía dưới.

### 3.2. Allowlist: chỉ chọn nghiệp vụ

~~~java
@Pointcut("execution(public * demo.business..*.*(..))")
public void businessMethods() {
}
~~~

Một allowlist như trên chọn những public method có type pattern thuộc `demo.business` và các package con. Các bean `demo.shared.AuditRecorder` và `demo.infra.HealthInspector` **không thuộc phạm vi chọn ngay từ đầu**.

Ưu điểm:

1. Khi ứng dụng bổ sung recorder mới ngoài `demo.business`, không cần cập nhật mọi danh sách loại trừ.
2. Nhìn biểu thức là hiểu **loại code được giám sát**.
3. Có thể tái sử dụng điều kiện cho logging, metrics, audit nếu các concern cần phạm vi giống nhau.
4. Có thể viết test khẳng định **business methods được advice**, và **technical methods không được advice**.

Nhược điểm: tổ chức package là một phần của hợp đồng kiến trúc. Nếu bean kỹ thuật bị đặt sai trong `demo.business`, nó vẫn có thể bị chọn; nếu service nghiệp vụ chuyển sang package khác, nó có thể bị bỏ qua. Bởi vậy vẫn phải có test.

### 3.3. Denylist: khi module cũ khó tách package

Khi chưa refactor được package, có thể dùng:

~~~java
@Pointcut(
    "execution(public * demo..*.*(..)) && " +
    "!within(demo.infra..*) && " +
    "!within(demo.shared..*) && " +
    "!within(demo.aop..*)"
)
public void applicationExceptTechnical() {
}
~~~

Điều kiện bắt đầu từ phạm vi rộng, rồi loại các type thuộc package kỹ thuật. Nó có ích khi muốn tiến từng bước, nhưng **nguy cơ bỏ sót technical package mới** lớn hơn so với allowlist nghiệp vụ.

## 4. Loại theo type/package: !within(...)

Spring hỗ trợ pointcut designator `within(...)` để hạn chế phạm vi type chứa method execution. Toán tử `!` đảo điều kiện.

Ví dụ:

~~~text
execution(public * demo..*.*(..))
&& !within(demo.shared..*)
&& !within(demo.infra..*)
~~~

Cách hiểu:

- `execution(public * demo..*.*(..))`: lọc public method signatures trong type pattern thuộc `demo` và subpackages.
- `!within(demo.shared..*)`: loại method execution thuộc những type ở `demo.shared` và package con.
- `!within(demo.infra..*)`: loại nhóm ứng dụng hạ tầng.

Có thể loại một class đơn lẻ:

~~~text
!within(demo.shared.AuditRecorder)
~~~

Nhưng nếu sau đó thêm `MetricRecorder` hoặc `TraceWriter`, phải cập nhật chính sách. Lọc theo cả package thường dễ quản lý hơn trong module được tổ chức tốt.

Một số hierarchy phức tạp (method ở interface, implementation ở package khác, inheritance/bridge method) khiến **declaring type** và method mà proxy kiểm tra không hoàn toàn hiển nhiên. Nếu yêu cầu loại đối tượng theo **runtime target type**, có thể cân nhắc `target(demo.shared.SomeType)` dưới dạng điều kiện, rồi kiểm thử trên bean/proxy thực tế.

Không có điều kiện phủ định package nào vượt qua giới hạn cơ bản: Spring AOP chỉ intercept **những lời gọi qua Spring proxy** và chỉ trên các method hợp lệ theo JDK/CGLIB.

## 5. Loại theo bean name: !bean(...)

`bean(...)` là designator **riêng Spring AOP**, match theo tên bean trong Spring container. Nó hỗ trợ tên cụ thể và wildcard `*` trong bean name.

~~~text
!bean(auditRecorder)
!bean(*Recorder)
!bean(healthInspector)
~~~

Có thể ghép:

~~~text
execution(public * demo..*.*(..))
&& !bean(*Recorder)
&& !bean(healthInspector)
~~~

Khác biệt với `within`:

| Điều kiện | Quyết định theo | Điểm mạnh | Rủi ro |
| --- | --- | --- | --- |
| `!within(demo.shared..*)` | Type/package của method execution | Bảo vệ cả nhóm class kỹ thuật | Phụ thuộc cấu trúc package |
| `!within(demo.shared.AuditRecorder)` | Type cụ thể | Chính xác theo class | Quên loại recorder khác |
| `!bean(auditRecorder)` | Tên Spring bean | Chọn/loại một registration cụ thể | Rename bean làm thay đổi match |
| `!bean(*Recorder)` | Convention tên Spring bean | Loại nhiều recorder cùng quy ước | Không loại bean có tên `auditSink` |

`bean` hoạt động ở mức **Spring bean name**, không phải Java method name, và **không phải** một phần của cú pháp `execution`. Native AspectJ weaving không có Spring bean factory để match designator này; tránh mang nguyên expression này sang weaving mode.

Kiểm tra tên bean thực tế khi dùng `@Component("...")`, `@Bean(name = "...")`, alias và cơ chế đặt tên tùy chỉnh. Không được suy ra bean name chỉ từ class name trong mọi cấu hình.

## 6. Loại bằng annotation và contract nghiệp vụ

Một chiến lược khác là **opt-in**: chỉ advice các method có annotation chủ đích.

~~~text
@annotation(demo.audit.AuditedOperation)
~~~

Trong đó `demo.audit.AuditedOperation` là custom annotation do ứng dụng tạo, với retention runtime và target METHOD. Ví dụ định nghĩa:

~~~java
package demo.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditedOperation {
}
~~~

Một service đánh dấu:

~~~java
@AuditedOperation
public void submit(String id) {
    // Phương thức nghiệp vụ cần audit
}
~~~

Đoạn submit là **fragment method**, cần class và import thích hợp; chưa phải file Java độc lập. Spring AOP có thể dùng `@annotation` để chọn method được đánh dấu.

Nếu annotation nằm trên interface thay vì implementation, hoặc có proxy/inheritance phức tạp, cần integration test cách Spring resolve annotation metadata. Không suy luận chỉ vì interface có annotation là mọi kiểu proxy sẽ có cùng kết quả.

Lựa chọn `@within` có thể ràng buộc annotation trên type. Cân nhắc kỹ vì nó khác `@annotation` trên method. Với các application infrastructure classes, việc thiếu annotation nghiệp vụ cũng là một dạng allowlist tự nhiên.

## 7. Named @Pointcut: đóng gói các điều kiện thay vì sao chép

Spring hỗ trợ toán tử:

| Toán tử | Ý nghĩa |
| --- | --- |
| `&&` | Giao: mọi điều kiện đúng |
| `||` | Hợp: ít nhất một điều kiện đúng |
| `!` | Phủ định điều kiện |
| `(...)` | Nhóm điều kiện khi cần |

Có thể viết:

~~~java
@Pointcut("execution(public * demo..*.*(..))")
public void publicApplicationMethod() {
}

@Pointcut(
    "!within(demo.shared..*) && !within(demo.infra..*)"
)
public void notTechnicalType() {
}

@Pointcut("!bean(*Recorder)")
public void notRecorderName() {
}

@Pointcut(
    "publicApplicationMethod() && " +
    "notTechnicalType() && notRecorderName()"
)
public void auditableMethod() {
}
~~~

Đây là **fragment các method đặt trong một class pointcut** có import `org.aspectj.lang.annotation.Pointcut`, không phải file độc lập. Các method `@Pointcut` có **return type void và thân rỗng**: Spring đọc metadata, không gọi các method như logic nghiệp vụ.

Có thể đặt pointcut methods vào `CommonPointcuts` rồi tham chiếu đầy đủ từ aspect:

~~~java
@Before("demo.aop.CommonPointcuts.auditableMethod()")
public void beforeBusiness(JoinPoint jp) {
    // ...
}
~~~

Method pointcut được tham chiếu ở class khác phải có Java visibility phù hợp, chẳng hạn public. Tách thành một class chính sách chung giúp nhóm tránh mỗi aspect có một phiên bản denylist riêng.

## 8. Dự án Java 17+ chạy được

Ta dùng **Spring Framework 6.2.12**, **Java 17+**, **Maven 3.9+**, không cần Spring Boot. Dự án có hai service, một recorder, một infrastructure bean và một `@Aspect`. Pointcut đủ rộng để có ý nghĩa loại trừ, nhưng rõ những nhóm không được advise.

### 8.1. Cấu trúc dự án

~~~text
spring-aop-exclusion/
├── pom.xml
└── src/main/java/demo/
    ├── App.java
    ├── config/AppConfig.java
    ├── business/OrderService.java
    ├── business/ReportService.java
    ├── shared/AuditRecorder.java
    ├── infra/HealthInspector.java
    ├── aop/CommonPointcuts.java
    └── aop/LoggingAspect.java
~~~

### 8.2. Maven

**pom.xml**

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-exclusion</artifactId>
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

`spring-context` cung cấp ApplicationContext và Spring AOP dependency phù hợp; `aspectjweaver` hỗ trợ phân tích expression/annotation `@AspectJ`. Ví dụ dùng **Spring AOP proxy-based**, không phải AspectJ compile-time hoặc load-time weaving.

### 8.3. Hai business beans

**src/main/java/demo/business/OrderService.java**

~~~java
package demo.business;

import org.springframework.stereotype.Service;

@Service
public class OrderService {

    public void place(String orderId) {
        System.out.println("BUSINESS place " + orderId);
    }
}
~~~

**src/main/java/demo/business/ReportService.java**

~~~java
package demo.business;

import org.springframework.stereotype.Service;

@Service
public class ReportService {

    public void generate(String period) {
        System.out.println("BUSINESS generate " + period);
    }
}
~~~

Cả hai là **Spring-managed service beans** do component scanning đăng ký. Hai class không final và public methods không final, nên Spring có thể dùng **class-based CGLIB proxy** khi advisor match. Chúng không triển khai interface; ta lấy trực tiếp bean theo concrete class.

### 8.4. Bean recorder được aspect sử dụng

**src/main/java/demo/shared/AuditRecorder.java**

~~~java
package demo.shared;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class AuditRecorder {

    private final AtomicInteger count = new AtomicInteger();

    public void record(String event) {
        count.incrementAndGet();
        System.out.println("AUDIT " + event);
    }

    public int size() {
        return count.get();
    }
}
~~~

`AuditRecorder` là một Spring `@Component` thông thường, được Spring tạo với scope singleton mặc định. Nó không tự được loại khỏi mọi advisor. Điều kiện loại trừ `demo.shared` và tên bean `*Recorder` sẽ ngăn **chính LoggingAspect** dùng nó làm mục tiêu advice trong ví dụ này.

`AtomicInteger` dùng để đếm an toàn ở mức thao tác tăng đơn lẻ. Đối với hệ thống audit thật, cần chọn storage và xử lý concurrency/lỗi phù hợp; không dùng singleton counter làm kho audit production.

### 8.5. Bean infrastructure bình thường

**src/main/java/demo/infra/HealthInspector.java**

~~~java
package demo.infra;

import org.springframework.stereotype.Component;

@Component
public class HealthInspector {

    public void ping() {
        System.out.println("INFRA ping");
    }
}
~~~

Mục tiêu là chứng minh một `@Component` kỹ thuật vẫn được Spring quản lý nhưng **không có nghĩa sẽ phải bị audit**. `HealthInspector` không mang annotation `@Aspect`; nó bị loại theo policy ứng dụng, không phải cơ chế tự loại aspect.

### 8.6. CommonPointcuts dùng cho nhiều aspect

**src/main/java/demo/aop/CommonPointcuts.java**

~~~java
package demo.aop;

import org.aspectj.lang.annotation.Pointcut;

public class CommonPointcuts {

    @Pointcut("execution(public * demo..*.*(..))")
    public void publicApplicationMethod() {
    }

    @Pointcut(
        "!within(demo.infra..*) && " +
        "!within(demo.shared..*) && " +
        "!within(demo.aop..*) && " +
        "!within(demo.config..*)"
    )
    public void notTechnicalType() {
    }

    @Pointcut("!bean(*Recorder) && !bean(healthInspector)")
    public void notTechnicalBeanName() {
    }

    @Pointcut(
        "publicApplicationMethod() && " +
        "notTechnicalType() && notTechnicalBeanName()"
    )
    public void auditableApplicationMethod() {
    }
}
~~~

**Cách đọc**:

1. `publicApplicationMethod` xác định phạm vi sơ bộ: public method execution của các type trong `demo`.
2. `notTechnicalType` loại bốn package kỹ thuật: infrastructure, recorder/shared, AOP helper và config.
3. `notTechnicalBeanName` loại bean name theo wildcard `*Recorder` và bean chính xác `healthInspector`.
4. `auditableApplicationMethod` kết hợp ba điều kiện bằng **giao logic**.

Điều kiện theo tên bean **có phần trùng** với điều kiện package trong ví dụ đơn giản này. Điều đó có chủ đích để minh họa **hai lớp bảo vệ**; không có quy tắc bắt buộc viết cả hai trong mọi Spring application.

`CommonPointcuts` **không cần** là một Spring bean hay mang `@Aspect`. Đây là class chứa các named `@Pointcut` public để aspect khác có thể tham chiếu bằng tên class đầy đủ. Các method pointcut có thân rỗng, không tự được gọi như business methods.

### 8.7. LoggingAspect inject recorder

**src/main/java/demo/aop/LoggingAspect.java**

~~~java
package demo.aop;

import demo.shared.AuditRecorder;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

    private final AuditRecorder recorder;

    public LoggingAspect(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    @Before("demo.aop.CommonPointcuts.auditableApplicationMethod()")
    public void beforeBusiness(JoinPoint jp) {
        recorder.record("before " + jp.getSignature().getName());
    }
}
~~~

Spring inject một instance `AuditRecorder` vào constructor aspect. Advice dùng instance đó để ghi một sự kiện **trước mỗi business method đã match**. Việc gọi `recorder.record(...)` không bị **chính advisor này** advice vì recorder nằm ngoài phạm vi pointcut.

**Chú ý:** Spring AOP tự tránh auto-proxying aspect bean như một target, nhưng điều kiện `!within(demo.aop..*)` còn có tác dụng với **helper bean khác trong cùng package** nếu các helper không mang `@Aspect`.

### 8.8. ApplicationContext và AOP

**src/main/java/demo/config/AppConfig.java**

~~~java
package demo.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackages = "demo")
@EnableAspectJAutoProxy
public class AppConfig {
}
~~~

`@ComponentScan` đăng ký hai business beans, recorder, inspector và aspect. `@EnableAspectJAutoProxy` bật hạ tầng tự động phát hiện advice và tạo proxy khi có advisor match. `@Aspect` một mình không tự đăng ký bean; trong ví dụ `LoggingAspect` có thêm `@Component`.

### 8.9. Main kiểm tra cả mục tiêu và loại trừ

**src/main/java/demo/App.java**

~~~java
package demo;

import demo.aop.LoggingAspect;
import demo.business.OrderService;
import demo.business.ReportService;
import demo.config.AppConfig;
import demo.infra.HealthInspector;
import demo.shared.AuditRecorder;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            OrderService orders = context.getBean(OrderService.class);
            ReportService reports = context.getBean(ReportService.class);
            AuditRecorder recorder = context.getBean(AuditRecorder.class);
            HealthInspector inspector = context.getBean(HealthInspector.class);
            LoggingAspect aspect = context.getBean(LoggingAspect.class);

            System.out.println(
                    "SERVICE_PROXY " + AopUtils.isAopProxy(orders));
            System.out.println(
                    "RECORDER_PROXY " + AopUtils.isAopProxy(recorder));
            System.out.println(
                    "INFRA_PROXY " + AopUtils.isAopProxy(inspector));
            System.out.println(
                    "ASPECT_PROXY " + AopUtils.isAopProxy(aspect));

            orders.place("INV-105");
            reports.generate("weekly");

            recorder.record("manual");
            inspector.ping();

            System.out.println("AUDIT_COUNT " + recorder.size());
        }
    }
}
~~~

Chạy trong thư mục có `pom.xml`:

~~~bash
mvn -q clean compile exec:java
~~~

**Stdout logic kỳ vọng**, không tính các dòng Maven hay framework logging:

~~~text
SERVICE_PROXY true
RECORDER_PROXY false
INFRA_PROXY false
ASPECT_PROXY false
AUDIT before place
BUSINESS place INV-105
AUDIT before generate
BUSINESS generate weekly
AUDIT manual
INFRA ping
AUDIT_COUNT 3
~~~

**Giải thích kết quả**:

- `SERVICE_PROXY true`: `OrderService` có method nằm trong pointcut và được Spring bọc AOP proxy.
- `RECORDER_PROXY false` và `INFRA_PROXY false`: không có advisor nào trong context demo áp dụng cho các bean kỹ thuật ấy.
- `ASPECT_PROXY false`: đúng với cơ chế Spring AOP không auto-proxy class `@Aspect` như một target.
- `AUDIT before place` và `AUDIT before generate`: chính LoggingAspect được Spring gọi trước hai service methods.
- `AUDIT manual`: App tự gọi recorder; không có `AUDIT before record` vì recorder không khớp advisor audit.
- `INFRA ping`: inspector không có advice audit.
- `AUDIT_COUNT 3`: hai sự kiện từ aspect và một sự kiện manual; không có recursion phát sinh từ recorder.

Kết quả `RECORDER_PROXY false` **không phải** quy tắc chung cho mọi context. Nếu ứng dụng thực còn có advisor bảo mật hoặc transaction khác áp dụng cho recorder, `AopUtils.isAopProxy(recorder)` có thể true, dù pointcut của LoggingAspect đã loại recorder đúng cách. Phải kiểm tra **hành vi của chính advisor cần test** chứ không suy luận mọi proxy đều không tồn tại.

## 9. Vì sao package riêng giúp tránh advice tái nhập?

Thiết kế dễ gây lỗi:

~~~text
demo.service.OrderService
demo.service.ReportService
demo.service.AuditRecorder
demo.service.MetricWriter
~~~

Pointcut:

~~~text
execution(public * demo.service..*.*(..))
~~~

có thể chọn cả recorder và writer vì tất cả ở cùng package service. Khi aspect gọi recorder qua Spring proxy, nó có thể gặp advisor của chính mình.

Thiết kế rõ ràng hơn:

~~~text
demo.business.OrderService
demo.business.ReportService
demo.observability.AuditRecorder
demo.observability.MetricWriter
demo.infra.HealthInspector
demo.aop.LoggingAspect
~~~

Có thể chỉ cần:

~~~text
execution(public * demo.business..*.*(..))
~~~

để tạo một **positive allowlist**. Lúc đó technical beans mới thêm vào `demo.observability` không tự bị chọn.

Nếu hệ thống di sản chưa cho phép refactor package, dùng `!within` và `!bean` tạm thời cùng integration tests, rồi lên kế hoạch thu hẹp expression theo business layer.

## 10. Không nhầm loại trừ @Aspect với loại trừ phụ thuộc của aspect

Spring hiểu `@Aspect` và không dùng bean đó làm target để những Spring aspects khác proxy nó. **Nhưng recorder không tự được miễn**, ngay cả khi chỉ được tạo để phục vụ aspect.

Một aspect thường inject:

- `AuditRecorder`: ghi các sự kiện.
- `EventPublisher`: phát thông báo.
- `Clock`: lấy thời điểm.
- `LogFormatter`: format sự kiện.
- `MetricRegistry`: ghi metrics.

Nếu pointcut đủ rộng và những dependency này được Spring quản lý, Spring có thể xem chúng là target advisor. Phải phân tích **cả dependency graph của advice**, không chỉ tên class aspect.

Bản thân việc `LoggingAspect` không bị proxy **không** ngăn `AuditRecorder` bị proxy và lặp lại advice. Đây là lý do loại trừ shared observability code là một phần quan trọng của **pointcut policy**.

## 11. Một advisor khác vẫn có thể proxy technical bean

Pointcut quyết định phạm vi **của một advisor**, không phải một lệnh cấm mọi cơ chế proxy. Ví dụ:

- Audit aspect của bạn loại `AuditRecorder`.
- Một transaction/security advisor khác lại áp dụng cho method `record`.
- `AuditRecorder` vẫn có thể là Spring AOP proxy.
- Nhưng nếu advice của audit aspect không match recorder, riêng concern audit sẽ không tự tái nhập vì method đó.

Vì vậy không đặt mục tiêu “mọi bean recorder phải luôn `AopUtils.isAopProxy == false`” trong một ứng dụng có nhiều advisor. Mục tiêu đúng là:

1. Service cần audit được advisor audit thực thi.
2. Method recorder không bị **cùng advisor audit** chọn.
3. Số lượng sự kiện tương ứng số business operations mong muốn.
4. Không có vòng gọi từ advice vào chính nó thông qua các dependencies.

Cần phân biệt **tính hiện diện của proxy** với **tính phù hợp của pointcut cụ thể**.

## 12. Kiểm thử tích hợp positive và negative

Để xác thực policy, không chỉ đọc expression. Với cùng context của ví dụ, có thể viết một đoạn test trong một phương thức Java:

~~~java
if (!AopUtils.isAopProxy(orders)) {
    throw new AssertionError("OrderService must be proxied");
}
if (AopUtils.isAopProxy(recorder)) {
    throw new AssertionError("Recorder should not be proxied here");
}
if (AopUtils.isAopProxy(inspector)) {
    throw new AssertionError("Inspector should not be proxied here");
}
if (AopUtils.isAopProxy(aspect)) {
    throw new AssertionError("Aspect should not be proxied");
}

int before = recorder.size();
orders.place("T-1");
int after = recorder.size();

if (after != before + 1) {
    throw new AssertionError("Expected one audit event");
}
~~~

Đây là **fragment kiểm thử**, cần các biến `orders`, `recorder`, `inspector`, `aspect` đã lấy từ context và import `AopUtils` trong một class Java hợp lệ. Không chạy fragment trực tiếp từ command line như một chương trình độc lập.

Với ứng dụng thật có nhiều advisor, việc assert recorder không phải proxy có thể quá mạnh. Khi ấy nên ghi nhận chính xác hiệu ứng của **LoggingAspect** (ví dụ bộ đếm riêng cho advice) để xác nhận recorder không kích hoạt thêm invocation của aspect đó.

### Những trường hợp cần test

| Trường hợp | Mong muốn |
| --- | --- |
| Gọi business method qua Spring bean | Advice chạy đúng số lần |
| Gọi `AuditRecorder.record()` trực tiếp | Không kích hoạt LoggingAspect |
| Gọi `HealthInspector.ping()` | Không kích hoạt LoggingAspect |
| Gọi method của helper ngoài business package | Không kích hoạt audit business policy |
| Gọi `this.method()` nội bộ ở target | Không suy ra advice chạy; kiểm tra proxy boundary |
| Thêm bean có tên `sharedAuditSink` | Kiểm tra package/type policy, vì `*Recorder` không match tên ấy |
| Đổi `@Component` bean name | Chạy lại test vì `bean(...)` có thể thay đổi |
| Chuyển service sang package mới | Chạy lại test allowlist để không bỏ sót nghiệp vụ |

## 13. @Order và @Around không phải công cụ loại bean

`@Order` có thể quyết định **ưu tiên advisor/aspect** khi nhiều advice match cùng invocation. Nó không làm thay đổi việc method recorder có match pointcut hay không.

Tương tự:

- Đổi `@Before` thành `@Around` không tự chặn vòng nếu advice vẫn gọi recorder bị advisor cùng concern can thiệp.
- `ProceedingJoinPoint.proceed()` tiếp tục invocation hiện tại, không phải phép “bỏ qua mọi advice khác”.
- Tăng thread-safety của recorder với `AtomicInteger` không giải quyết vòng gọi qua proxy.
- Bật `proxyTargetClass = true` chuyển sang CGLIB nhưng không phải cách loại recorder khỏi pointcut.
- Tắt `@EnableAspectJAutoProxy` để tránh advice recorder sẽ đồng thời tắt hạ tầng Spring AOP trong context đó, ảnh hưởng cả service cần audit.

Cách sửa chính xác là **thu hẹp phạm vi pointcut**, đồng thời xem lại dependency graph và đường đi lời gọi.

## 14. Khi nào nên dùng !within, !bean hoặc @annotation?

| Bối cảnh | Ưu tiên |
| --- | --- |
| Có package business rõ | `execution(public * demo.business..*.*(..))` |
| Module cũ chứa cả business và technical types | Allowlist nếu có thể, nếu chưa thì phủ định `!within` chính xác |
| Cùng class nhưng nhiều Spring bean registrations có vai trò khác nhau | `bean(...)` để chọn hoặc loại bean name cụ thể |
| Audit chỉ những operation được developer đánh dấu | `@annotation` với annotation nghiệp vụ |
| Nhiều aspect dùng chung cùng business layer | `CommonPointcuts` public named methods |
| Recorder bị advice bởi chính aspect gọi nó | Bắt đầu bằng loại recorder khỏi pointcut đó, rồi kiểm tra graph |
| Chỉ muốn “aspect không bị advice bởi aspect khác” | Spring AOP đã có auto-exclusion cho `@Aspect`; không coi đây là chính sách loại recorder |

Đừng chọn một strategy duy nhất cho mọi dự án. Hệ thống có nhiều module và naming conventions khác nhau có thể cần kết hợp type package và bean-name filters; điều quan trọng là **viết ý định rõ ràng và kiểm thử trên context thật**.

## 15. Các lỗi thường gặp

| Triệu chứng | Nguyên nhân khả dĩ | Hướng xử lý |
| --- | --- | --- |
| Lặp log liên tục hoặc `StackOverflowError` | Advice gọi recorder; recorder match cùng advisor | Thu hẹp business allowlist hoặc loại recorder rõ ràng |
| Startup lỗi dependency giữa aspect và service | Dependency vòng qua collaborator | Vẽ dependency graph, tách recorder độc lập khỏi service đang được advice |
| Aspect không phải proxy | Spring AOP auto-exclusion với bean `@Aspect` | Hành vi bình thường, không phải lỗi |
| Recorder vẫn là proxy sau khi loại khỏi audit | Có advisor khác chọn recorder | Kiểm tra advisor cụ thể; không dựa vào proxy Boolean tổng quát |
| `!bean(*Recorder)` không loại bean ghi audit | Bean tên khác convention `*Recorder` | Kiểm tra tên bean thực tế, dùng type/package hoặc đổi convention |
| `!within(demo.shared..*)` không loại implementation nằm nơi khác | Type/package matching khác target runtime type | Test hierarchy/proxy, cân nhắc target/type restriction |
| Service mới không có audit | Allowlist theo package hoặc annotation chưa bao phủ | Cập nhật policy/test khi thêm hoặc chuyển service |
| Mọi infrastructure bean đều bị proxy | Pointcut quá rộng, thiếu boundary | Tách package và lọc theo business operations |
| Đổi `@Order` vẫn lặp advice | Ordering không đổi phạm vi match | Sửa pointcut |
| `@Aspect` class có nhưng advice không chạy | Aspect chưa được đăng ký như bean hoặc thiếu hạ tầng | `@Component`/ `@Bean`, `@EnableAspectJAutoProxy` |
| Method khớp chữ ký nhưng advice không chạy | Self-invocation hoặc caller dùng instance `new` | Kiểm tra đường gọi qua Spring proxy |
| Copy `bean(...)` sang weaving mode | Spring bean designator không có trong native AspectJ | Chỉ dùng trong Spring AOP pointcut |

## 16. Checklist thiết kế pointcut để tránh ghi nhận chính mã ghi nhận

1. Xác định **mọi dependency mà advice gọi**, không chỉ business target.
2. Vẽ sơ bộ call graph: Service proxy → Aspect → Recorder/Publisher → các dependencies tiếp theo.
3. Bắt đầu bằng **positive business allowlist** nếu kiến trúc cho phép.
4. Với phạm vi rộng, thêm `!within` cho technical packages/types và `!bean` cho registrations phù hợp.
5. Không trông đợi framework tự miễn các `@Component` helper chỉ vì chúng phục vụ aspect.
6. Đừng thêm mọi exclusion tràn lan nếu service cần thiết bị loại; dùng named pointcuts dễ đọc.
7. Xác minh `@Aspect` được đăng ký Spring bean và AOP được bật.
8. Kiểm tra **cả trường hợp nên match và không nên match** trong integration test.
9. Không coi `AopUtils.isAopProxy` là bằng chứng một advisor cụ thể đang hoặc không đang chạy.
10. Khi thay đổi package, bean name, annotation hay aspect dependency, chạy lại test để kiểm soát policy.
11. Tránh việc recorder tự gọi vào service được advise nếu không có chủ đích vì đó cũng có thể gây tái nhập thông qua một đường nghiệp vụ khác.
12. Nếu có nhiều concerns, thiết kế phạm vi pointcut riêng của từng concern thay vì dùng một pointcut cực rộng cho tất cả.

## 17. Câu hỏi tự đánh giá

**1. Vì sao Spring không tự loại `AuditRecorder` khỏi mọi Spring aspect?**

Vì `AuditRecorder` là Spring bean thông thường. Spring không biết theo tên class rằng advisor nào được phép can thiệp vào recorder nào; ứng dụng phải định nghĩa pointcut policy.

**2. Spring AOP có loại bean mang `@Aspect` khỏi auto-proxying như target không?**

Có. Nhưng bean đó vẫn cung cấp advice cho các bean nghiệp vụ và dependency của aspect không được miễn tương tự.

**3. `!within(demo.shared..*)` khác `!bean(*Recorder)` thế nào?**

Một điều kiện loại theo package/type method execution, điều kiện kia loại theo Spring bean name và convention.

**4. Tại sao allowlist `demo.business` thường dễ bảo trì hơn phủ định hàng chục package?**

Nó chỉ mở phạm vi tới business types rõ ràng, giúp technical beans mới ở ngoài package không bị match ngoài ý muốn.

**5. Có phải `AopUtils.isAopProxy(recorder)` luôn phải false sau khi bạn loại nó khỏi audit aspect?**

Không. Các advisor khác vẫn có thể proxy recorder. Điều cần kiểm tra là advisor audit có chạy khi recorder được gọi hay không.

**6. Dùng `@Order` có ngăn advice tự gọi recorder không?**

Không. `@Order` chỉ điều chỉnh thứ tự, không thay đổi tập join points mà pointcut match.

**7. Có thể gom nhiều điều kiện vào một class CommonPointcuts không?**

Có. Dùng các named `@Pointcut` public và để các aspect tham chiếu bằng tên đầy đủ.

**8. Có nên đánh dấu mọi bean hạ tầng là `@Aspect` để Spring bỏ qua chúng?**

Không. `@Aspect` thể hiện một vai trò AOP thực sự; lạm dụng nó để bỏ qua auto-proxy là sai mô hình. Hãy sửa policy bằng `execution`, `within`, `bean` hoặc annotation phù hợp.

## 18. Tài liệu Spring Framework

- Spring Framework 6.2 — **Declaring a Pointcut** (execution, within, bean, toán tử logic, named pointcuts): https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html
- Spring Framework 6.2 — **Declaring an Aspect** (aspect bean được loại khỏi auto-proxying với vai trò target): https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/at-aspectj.html
- Spring Framework — **@AspectJ Support**: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — **Proxying Mechanisms**: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — **Declaring Advice**: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html

**Kết luận:** Spring AOP tự nhận diện `@Aspect` và không biến aspect bean thành target proxy của aspect khác. Nhưng **recorder, helper và infrastructure `@Component` vẫn là beans có thể được advisor xem xét**. Muốn tránh advice bao trùm mã ghi nhận của chính nó, hãy bắt đầu bằng **allowlist business methods**, tổ chức `CommonPointcuts`, bổ sung `!within`/`!bean` khi cần và kiểm thử cả đối tượng được chọn lẫn đối tượng phải loại.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reusable-pointcuts-008">Kiểm thử ca được khớp và ca không được khớp</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử ca được khớp và ca không được khớp của pointcut trong Spring AOP

## 1. Mục tiêu: kiểm chứng cả “match” và “không match”

Trong Spring AOP, một biểu thức pointcut cho biết **những method execution trên Spring bean** sẽ được advice quan tâm. Kiểm thử chỉ một method có advice chạy là chưa đủ: một pointcut viết quá rộng có thể vô tình intercept method khác, gây log thừa, chậm ứng dụng hoặc áp dụng chính sách sai nơi.

Mục tiêu của bài thực hành là tổ chức pointcut thành **hai điều kiện có tên**, kết hợp chúng bằng `&&`, rồi dùng **JUnit 5 và Spring ApplicationContext thực** để khẳng định:

1. **Ca dương (match):** method đáp ứng *cả hai* điều kiện, được gọi qua Spring proxy → `@Before` chạy đúng một lần, trước target.
2. **Ca âm 1:** method đúng phạm vi class/chữ ký nhưng **không mang annotation** → không advice.
3. **Ca âm 2:** method có annotation nhưng **không đúng kiểu trả về mà pointcut yêu cầu** → không advice.
4. **Ca âm 3:** method có annotation nhưng thuộc **class khác** → không advice.
5. **Ca âm ranh giới proxy:** gọi annotated method thông qua `this.method()` từ bên trong target → không advice cho lời gọi nội bộ.
6. **Ca âm đối tượng tự tạo:** cùng class/method nhưng dùng `new` ngoài Spring → không có Spring auto-proxy.

Bài học tập trung vào **Spring Framework - Aspect**, cụ thể là cách **kiểm thử pointcut đã tổ chức**; không đi sâu vào lý thuyết AspectJ ngoài Spring. Ký pháp `@Aspect`/`@Pointcut` có nguồn gốc AspectJ nhưng Spring AOP thực thi bằng **JDK/CGLIB proxy** trên Spring-managed beans, không dùng bytecode weaving.

## 2. Hai tầng kiểm thử pointcut

### Tầng 1: thử biểu thức có match một method không

Spring cung cấp `org.springframework.aop.aspectj.AspectJExpressionPointcut` để biểu diễn một pointcut dùng cú pháp AspectJ. Với một `Method` và một target class, có thể kiểm tra `matches(method, targetClass)`. Đây là phép kiểm tra **khả năng match theo phương thức**, hữu ích để phát hiện pattern quá rộng/hẹp.

~~~java
AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
pointcut.setExpression(
    "execution(public void demo.PaymentService.*(..))"
    + " && @annotation(demo.Measured)"
);
boolean matches = pointcut.matches(
    PaymentService.class.getMethod("pay"),
    PaymentService.class
);
~~~

Đây là **đoạn minh họa đặt trong method Java** với import `org.springframework.aop.aspectj.AspectJExpressionPointcut`; các class tương ứng được định nghĩa ở project đầy đủ bên dưới. Lúc này chưa chứng minh Spring đã **tạo proxy hoặc thực thi advice**, chỉ kiểm tra expression với chữ ký method và metadata.

### Tầng 2: integration test gọi method qua Spring bean proxy

Một integration test dùng `AnnotationConfigApplicationContext` cần kiểm chứng lần lượt:

- Context bật `@EnableAspectJAutoProxy`; aspect đã được đăng ký làm bean.
- Service lấy từ context thực sự là AOP proxy khi có advisor áp dụng (`AopUtils.isAopProxy`).
- Gọi method **qua reference bean Spring cấp**.
- Quan sát **marker nằm trong advice**, thứ tự marker so với method target, và số lần advice chạy.
- Gọi các phương thức *không* match để assert **không có marker advice**, không chỉ assert target vẫn chạy.

**Hai tầng không thay thế cho nhau.** `pointcut.matches(...) == true` không bảo đảm advice sẽ chạy khi gọi qua `this` hoặc object `new`. Ngược lại, thấy proxy tồn tại không khẳng định mọi method trong class đều khớp pointcut.

## 3. Thiết kế ma trận ca kiểm thử trước khi viết aspect

Giả sử pointcut mục tiêu là:

~~~text
execution(public void demo.PaymentService.*(..))
    &&
@annotation(demo.Measured)
~~~

Ý nghĩa:

- Điều kiện A: method `public`, trả về `void`, được khai báo trong `demo.PaymentService`.
- Điều kiện B: method được đánh dấu `@Measured`.
- Toán tử `&&`: cả A **và** B phải thỏa.

Ma trận cho project ở phần 4:

| Method được gọi | Đúng class + public void? (A) | Có @Measured? (B) | Có advice khi qua proxy? |
| --- | --- | --- | --- |
| `PaymentService.pay()` | Có | Có | **Có: 1 lần** |
| `PaymentService.preview()` | Có | Không | **Không** |
| `PaymentService.refund()` (trả `String`) | Không | Có | **Không** |
| `OtherService.pay()` | Không, class khác | Có | **Không** |
| `PaymentService.callPayInside()` | Có | Không | **Không** tại entry method |
| `this.pay()` bên trong `callPayInside()` | Đúng chữ ký và có annotation | Có | **Không**, vì self-invocation không qua proxy |
| `new PaymentService(...).pay()` | Đúng chữ ký và có annotation | Có | **Không**, vì object không do Spring auto-proxy |

Hai hàng cuối phân biệt **không match theo expression** với **không có join point đi qua proxy**: method có thể đúng về annotation/chữ ký nhưng không được AOP intercept trong lời gọi cụ thể.

Nếu chỉ test `pay()` mà không test `preview()`/`refund()`/`OtherService.pay()`, rất khó phát hiện việc bỏ nhầm một trong hai ràng buộc của pointcut.

## 4. Project Maven/Java 17+ chạy được

Ta viết **một project nhỏ** có đầy đủ mã và test. Không cần Spring Boot, web server, database, mock framework hay Spring TestContext vì `AnnotationConfigApplicationContext` đủ cho integration test minh họa.

Cấu trúc thư mục:

~~~text
spring-pointcut-match-test/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── Measured.java
    │   ├── EventLog.java
    │   ├── PaymentService.java
    │   ├── OtherService.java
    │   ├── MatchAspect.java
    │   └── AopConfig.java
    └── test/java/demo/
        └── PointcutMatchTest.java
~~~

Yêu cầu: JDK 17+, Maven 3.9+ và có thể resolve dependency. Các phiên bản cố định dùng cho bài thực hành: **Spring Framework 6.2.0**, **AspectJ Weaver 1.9.22**, **JUnit Jupiter 5.11.0**; không coi đó là phiên bản mới nhất của thư viện.

### 4.1. Tạo `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-pointcut-match-test</artifactId>
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
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.0</version>
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
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

- `spring-context` cung cấp container và Java configuration.
- `spring-aop` cung cấp auto-proxy, `AopUtils` và `AspectJExpressionPointcut`.
- `aspectjweaver` cung cấp các kiểu thư viện cần để Spring phân tích cú pháp `@AspectJ`; **không** tự kích hoạt weaving.
- `junit-jupiter` là JUnit 5 để viết `@Test` và assertions; `maven-surefire-plugin` chạy chúng qua lệnh Maven.

### 4.2. Annotation đánh dấu method

Tạo `src/main/java/demo/Measured.java`:

~~~java
package demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Measured {
}
~~~

`@Retention(RUNTIME)` giữ annotation có thể được dùng khi AOP xét method ở runtime. `@Target(METHOD)` ngăn dùng nhầm annotation trên class/field. Nó chỉ là dấu mốc metadata; bản thân annotation không thực thi advice.

### 4.3. EventLog độc lập để không phụ thuộc logger hay stdout

Tạo `src/main/java/demo/EventLog.java`:

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
}
~~~

Project test chạy từng context riêng và mỗi phép kiểm tra gọi method tuần tự, nên `ArrayList` đủ dùng. Trong ứng dụng concurrent thực tế, cần logger/thread-safe event sink hoặc test synchronization phù hợp. `snapshot()` tạo danh sách không sửa được ở ngoài, để assertions không vô tình thay đổi kết quả.

### 4.4. Service được advice và những ca không match

Tạo `src/main/java/demo/PaymentService.java`:

~~~java
package demo;

public class PaymentService {
    private final EventLog log;

    public PaymentService(EventLog log) {
        this.log = log;
    }

    @Measured
    public void pay() {
        log.add("PAY");
    }

    public void preview() {
        log.add("PREVIEW");
    }

    @Measured
    public String refund() {
        log.add("REFUND");
        return "ok";
    }

    public void callPayInside() {
        log.add("CALL_INSIDE");
        this.pay();
    }
}
~~~

Những chi tiết có chủ ý:

- `pay()`: `public void` và `@Measured` → **match cả A và B**.
- `preview()`: `public void` nhưng không annotation → chỉ thỏa A.
- `refund()`: có `@Measured` nhưng return type `String` → chỉ thỏa B.
- `callPayInside()`: entry method không annotated; `this.pay()` trong target không đi qua Spring proxy → advice không chạy cho internal call. Event `PAY` vẫn được thêm vì business method vẫn thực thi.
- Class và method không `final`, dễ được Spring bọc bằng class-based proxy.

Tạo `src/main/java/demo/OtherService.java`:

~~~java
package demo;

public class OtherService {
    private final EventLog log;

    public OtherService(EventLog log) {
        this.log = log;
    }

    @Measured
    public void pay() {
        log.add("OTHER_PAY");
    }
}
~~~

`OtherService.pay()` có **cùng annotation** và đúng kiểu trả về nhưng không thuộc class `PaymentService`. Đây là ca âm chống việc vô tình chỉ viết `@annotation(demo.Measured)` quá rộng.

### 4.5. Tách điều kiện thành hai named pointcuts

Tạo `src/main/java/demo/MatchAspect.java`:

~~~java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class MatchAspect {
    private final EventLog log;

    public MatchAspect(EventLog log) {
        this.log = log;
    }

    @Pointcut("execution(public void demo.PaymentService.*(..))")
    public void paymentVoidMethod() {
    }

    @Pointcut("@annotation(demo.Measured)")
    public void measuredMethod() {
    }

    @Before("paymentVoidMethod() && measuredMethod()")
    public void beforeMeasuredPayment() {
        log.add("ADVICE_PAY");
    }
}
~~~

Điều cần nhớ về việc **tổ chức pointcut** trong Spring AOP:

1. `paymentVoidMethod()` là **pointcut signature** (tên để tham chiếu); biểu thức `execution(...)` mới quyết định loại method execution được xét. Method định nghĩa `@Pointcut` phải có kiểu trả về `void`, thân rỗng.
2. `measuredMethod()` đại diện cho điều kiện method có annotation `@Measured`.
3. `@Before("paymentVoidMethod() && measuredMethod()")` tái sử dụng hai named pointcuts; nếu muốn thu hẹp thêm package/bean name, có thể ghép điều kiện mới mà vẫn giữ tên rõ nghĩa.
4. `ADVICE_PAY` chỉ được thêm trong method advice, nên một event này là bằng chứng advice thực sự thực thi.
5. Một bean khác có `@Measured` nhưng không nằm trong `PaymentService` không được advice, vì phép `&&` đòi hỏi **cả hai** điều kiện.
6. `@Aspect` mới chỉ đánh dấu class aspect; nó phải được đăng ký làm Spring bean ở phần cấu hình dưới đây.

### 4.6. Đăng ký Spring-managed beans và bật AOP

Tạo `src/main/java/demo/AopConfig.java`:

~~~java
package demo;

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
    public PaymentService paymentService(EventLog log) {
        return new PaymentService(log);
    }

    @Bean
    public OtherService otherService(EventLog log) {
        return new OtherService(log);
    }

    @Bean
    public MatchAspect matchAspect(EventLog log) {
        return new MatchAspect(log);
    }
}
~~~

`@EnableAspectJAutoProxy(proxyTargetClass = true)` bật Spring AOP và yêu cầu class-based proxy cho bean cần proxy. Vì `PaymentService` không implement interface, class proxy là cách phù hợp để lấy bean bằng `PaymentService.class`. Tùy hạ tầng/kiểu bean, Spring có thể chọn class-based proxy ngay cả khi `proxyTargetClass` không được đặt; cờ `true` làm lựa chọn rõ ràng trong demo.

**Không phải mọi bean trong context đều bị proxy.** Spring auto-proxy creator sẽ xét advisor. `OtherService` không có method khớp điều kiện `paymentVoidMethod() && measuredMethod()`; do đó bài không dựa vào việc `OtherService` có proxy hay không, chỉ khẳng định **không có advice từ `MatchAspect`** cho nó.

`EventLog` được inject vào service và aspect, là cùng một singleton bean trong một context. Mỗi test sẽ tạo **context mới** để không rò rỉ sự kiện giữa các test.

## 5. Tập integration tests với JUnit Jupiter

Tạo `src/test/java/demo/PointcutMatchTest.java`:

~~~java
package demo;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PointcutMatchTest {

    @Test
    void positiveCaseAnnotatedPublicVoidPaymentMatches() {
        try (var context =
                new AnnotationConfigApplicationContext(AopConfig.class)) {
            PaymentService service = context.getBean(PaymentService.class);
            EventLog log = context.getBean(EventLog.class);

            assertTrue(AopUtils.isAopProxy(service));

            service.pay();

            assertEquals(List.of("ADVICE_PAY", "PAY"), log.snapshot());
        }
    }

    @Test
    void negativeCaseMissingAnnotationDoesNotMatch() {
        try (var context =
                new AnnotationConfigApplicationContext(AopConfig.class)) {
            PaymentService service = context.getBean(PaymentService.class);
            EventLog log = context.getBean(EventLog.class);

            service.preview();

            assertEquals(List.of("PREVIEW"), log.snapshot());
        }
    }

    @Test
    void negativeCaseWrongReturnTypeDoesNotMatch() {
        try (var context =
                new AnnotationConfigApplicationContext(AopConfig.class)) {
            PaymentService service = context.getBean(PaymentService.class);
            EventLog log = context.getBean(EventLog.class);

            assertEquals("ok", service.refund());

            assertEquals(List.of("REFUND"), log.snapshot());
        }
    }

    @Test
    void negativeCaseAnnotationOnDifferentClassDoesNotMatch() {
        try (var context =
                new AnnotationConfigApplicationContext(AopConfig.class)) {
            OtherService other = context.getBean(OtherService.class);
            EventLog log = context.getBean(EventLog.class);

            other.pay();

            assertEquals(List.of("OTHER_PAY"), log.snapshot());
        }
    }

    @Test
    void negativeCaseSelfInvocationSkipsAdvice() {
        try (var context =
                new AnnotationConfigApplicationContext(AopConfig.class)) {
            PaymentService service = context.getBean(PaymentService.class);
            EventLog log = context.getBean(EventLog.class);

            assertTrue(AopUtils.isAopProxy(service));

            service.callPayInside();

            assertEquals(
                    List.of("CALL_INSIDE", "PAY"),
                    log.snapshot());
        }
    }

    @Test
    void negativeCasePlainJavaObjectHasNoAutoProxy() {
        EventLog log = new EventLog();
        PaymentService plain = new PaymentService(log);

        assertFalse(AopUtils.isAopProxy(plain));

        plain.pay();

        assertEquals(List.of("PAY"), log.snapshot());
    }

    @Test
    void staticPointcutMethodMatchesAreCorrect() throws Exception {
        var pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression(
                "execution(public void demo.PaymentService.*(..))"
                + " && @annotation(demo.Measured)");

        assertTrue(pointcut.matches(
                PaymentService.class.getMethod("pay"),
                PaymentService.class));

        assertFalse(pointcut.matches(
                PaymentService.class.getMethod("preview"),
                PaymentService.class));

        assertFalse(pointcut.matches(
                PaymentService.class.getMethod("refund"),
                PaymentService.class));

        assertFalse(pointcut.matches(
                OtherService.class.getMethod("pay"),
                OtherService.class));
    }
}
~~~

Đây là **bảy test methods hoàn chỉnh**. Mỗi test chỉ kiểm tra một ý, tránh test quá nhiều thao tác rồi kết luận không biết advice nào đã gây thay đổi.

### Phân tích các assertion

**Test dương:**

~~~java
service.pay();
assertEquals(List.of("ADVICE_PAY", "PAY"), log.snapshot());
~~~

Khẳng định **cả có advice và đúng thứ tự**: `@Before` thêm `ADVICE_PAY` trước khi business method thêm `PAY`. Nếu pointcut không match hoặc gọi không qua proxy, danh sách chỉ có `PAY` và test fail.

**Test âm thiếu annotation:**

~~~java
service.preview();
assertEquals(List.of("PREVIEW"), log.snapshot());
~~~

Nếu vô tình xóa điều kiện `measuredMethod()` hoặc thay bằng biểu thức rộng, `ADVICE_PAY` sẽ xuất hiện trước `PREVIEW` và assertion sẽ fail.

**Test âm sai kiểu trả về:**

~~~java
service.refund();
assertEquals(List.of("REFUND"), log.snapshot());
~~~

`refund` vẫn mang `@Measured`, nhưng biểu thức `execution(public void ...)` loại nó do trả `String`. Nếu viết pointcut chỉ có `@annotation(demo.Measured)`, test này sẽ fail.

**Test âm sai class:**

~~~java
other.pay();
assertEquals(List.of("OTHER_PAY"), log.snapshot());
~~~

Nếu bỏ giới hạn `demo.PaymentService` khỏi named pointcut, advice có thể lan sang class khác mang annotation tương tự.

**Test âm self-invocation:** `callPayInside()` vào target rồi `this.pay()`, method `pay` vẫn ghi `PAY` nhưng internal call không đi qua proxy. Test giúp phân biệt **mismatch pointcut** với **đường gọi không qua Spring proxy**.

**Test âm object thường:** `new PaymentService(log)` không phải Spring bean, dù method thỏa cú pháp pointcut. `@EnableAspectJAutoProxy` không tự lắp interception cho raw Java object.

**Test kiểm tra trực tiếp pointcut:** `AspectJExpressionPointcut.matches(...)` kiểm tra “hình dạng” method theo biểu thức. Kết hợp với integration tests phía trên để chứng minh vừa **match chính xác**, vừa **Spring AOP thực thi advice ở runtime**.

Điều cần chú ý: test `staticPointcutMethodMatchesAreCorrect` không tạo container, nên nó chỉ kiểm tra biểu thức, không thay thế sáu test kiểm tra hành vi. Nếu static check báo true, không tự suy rằng lời gọi qua `this` hay `new` sẽ được advice.

## 6. Chạy test và đọc kết quả thật

Chạy từ thư mục chứa `pom.xml`:

~~~bash
mvn clean test
~~~

Nếu sử dụng Maven đã tải đủ dependency và mọi test pass, kết quả cần tìm trong báo cáo Surefire tương tự:

~~~text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
~~~

Tên file báo cáo thường là:

~~~text
target/surefire-reports/demo.PointcutMatchTest.txt
~~~

Không cố định toàn bộ stdout của Maven vì phiên bản Maven/JDK, terminal và logger có thể chèn các dòng khác. **Lệnh phải kết thúc với exit code `0`** và báo cáo `7` tests pass; khi `mvn clean test` trả nonzero hãy xem assertion failure hoặc root cause trước khi tuyên bố hoàn tất.

Để chỉ chạy một test cụ thể:

~~~bash
mvn -Dtest=PointcutMatchTest#positiveCaseAnnotatedPublicVoidPaymentMatches test
~~~

Có thể chọn một test âm để xác nhận một giới hạn riêng:

~~~bash
mvn -Dtest=PointcutMatchTest#negativeCaseWrongReturnTypeDoesNotMatch test
~~~

Chạy riêng một test thuận tiện khi debug nhưng **không đủ** để tuyên bố toàn bộ pointcut đúng: cần chạy lại đủ các test dương/âm.

Nếu IDE không nhận JUnit 5, kiểm tra đúng `src/test/java`, dependency `junit-jupiter` có `scope=test`, và plugin `maven-surefire-plugin`. Nếu Spring không tạo AOP proxy, kiểm tra `@EnableAspectJAutoProxy`, bean `MatchAspect`, dependency `aspectjweaver` và pointcut.

## 7. Kiểm thử kiểu “mutation”: cố tình làm pointcut sai

Một cách hay để đánh giá bộ test là **sửa sai có chủ đích**, chạy test để thấy assertion phát hiện được, rồi **khôi phục đúng cấu hình**.

Các thay đổi dưới đây chỉ dùng **một lần một thay đổi** trên `MatchAspect.java`:

### Mutation A: bỏ điều kiện annotation

Thay:

~~~java
@Before("paymentVoidMethod() && measuredMethod()")
~~~

bằng:

~~~java
@Before("paymentVoidMethod()")
~~~

Khi đó các method `public void` trong `PaymentService` có thể được advice dù không có `@Measured`. Test `negativeCaseMissingAnnotationDoesNotMatch` sẽ thất bại (nhận `ADVICE_PAY` rồi `PREVIEW` thay vì chỉ `PREVIEW`). Test `negativeCaseSelfInvocationSkipsAdvice` có thể cũng thất bại vì entry method `callPayInside()` nay bị advice ngay khi caller vào proxy; điều này không chứng minh self-invocation được sửa.

### Mutation B: bỏ điều kiện class và return type

Thay:

~~~java
@Before("paymentVoidMethod() && measuredMethod()")
~~~

bằng:

~~~java
@Before("measuredMethod()")
~~~

Khi đó `PaymentService.refund()` và `OtherService.pay()` (cùng annotated) cũng có thể kích hoạt advice. Hai negative tests tương ứng phát hiện việc mở rộng phạm vi.

### Mutation C: vô hiệu hóa auto-proxy

Bỏ `@EnableAspectJAutoProxy(proxyTargetClass = true)` khỏi `AopConfig` (với giả định project tối thiểu không có cấu hình AOP khác). `positiveCaseAnnotatedPublicVoidPaymentMatches` thất bại vì `AopUtils.isAopProxy(service)` không còn true và danh sách sự kiện không có `ADVICE_PAY`.

Mutation này kiểm tra **hạ tầng Spring AOP**, không phải chỉ tính đúng đắn cú pháp pointcut. Sau từng mutation hãy khôi phục file chuẩn rồi chạy lại toàn bộ `mvn clean test`.

### Mutation D: đổi `&&` thành `||`

Thay hai điều kiện AND bằng OR:

~~~java
@Before("paymentVoidMethod() || measuredMethod()")
~~~

Bây giờ các method chỉ thỏa A **hoặc** B cũng được advice; các negative tests về annotation/class/return type sẽ phát hiện được. Đây là ví dụ điển hình tại sao test chỉ một positive match **không bảo vệ được phạm vi** của pointcut.

Trong project team, có thể dùng mutation testing framework để tự động hóa nhưng không cần thêm tool cho bài này: tự sửa, chạy Maven và hoàn nguyên là đủ để thấy giá trị của ma trận test.

## 8. Tổ chức pointcut để dễ kiểm thử và tái sử dụng

Khi project lớn, một class `@Aspect` có thể sử dụng các named pointcut riêng:

~~~java
@Pointcut("within(demo..service..*)")
public void serviceLayer() {
}

@Pointcut("@annotation(demo.Measured)")
public void annotatedOperation() {
}

@Before("serviceLayer() && annotatedOperation()")
public void before() {
    // ...
}
~~~

Đây là **snippet minh họa đặt trong một `@Aspect`**, không phải thay thế chạy độc lập cho project test trên. Package `demo..service..*` cần sửa cho đúng package thực tế. Named pointcuts có phương thức `void` với thân rỗng; chúng là cách đặt tên và **kết hợp biểu thức**, không phải callback Java được caller gọi trực tiếp.

Nếu nhiều aspects cùng dùng pointcut, có thể chuyển các khai báo `@Pointcut` sang một class `public @Aspect` kiểu `CommonPointcuts`, rồi tham chiếu bằng tên đầy đủ:

~~~java
@Before("demo.CommonPointcuts.serviceLayer()"
      + " && demo.CommonPointcuts.annotatedOperation()")
public void before() {
    // ...
}
~~~

Đây là đoạn định hướng tổ chức code, **`demo.CommonPointcuts` không thuộc project ví dụ đã khai báo** nên chưa thể biên dịch riêng. Trong ứng dụng thật phải định nghĩa `CommonPointcuts`, đặt visibility phù hợp và bảo đảm biểu thức tham chiếu đúng FQCN.

Dù tái sử dụng pointcut, cần có **test âm của từng điều kiện**: một ràng buộc theo package, một ràng buộc theo annotation, một ràng buộc theo visibility/return type, v.v. Các test âm ngăn thay đổi không chủ ý làm pointcut quá rộng và giúp review code dễ dàng hơn.

## 9. Các nguồn gây false positive/false negative trong test Spring AOP

### 9.1. Chỉ nhìn `isAopProxy()`

`AopUtils.isAopProxy(service)` cho biết bean là một AOP proxy. **Nó không cho biết advice cụ thể của bạn đã khớp method nào**. Một bean có thể được proxy vì transaction, cache hoặc advisor khác. Hãy assert marker nằm **trong advice cần kiểm thử** và logic business target đúng thứ tự.

### 9.2. Dùng object `new` thay vì Spring bean

Nếu tạo:

~~~java
PaymentService service = new PaymentService(new EventLog());
service.pay();
~~~

đây là Java object thường, không chạy qua `AnnotationConfigApplicationContext` nên không được Spring auto-proxy. Khi test runtime aspect, phải lấy `context.getBean(PaymentService.class)` hoặc inject bean. Dùng `new` vẫn hữu ích làm **ca âm** nhưng không thể là phép thử dương cho advice Spring AOP.

### 9.3. Self-invocation

Từ một method đã vào target, lời gọi `this.pay()` bỏ qua proxy nên advice trên `pay()` không chạy. Điều này **không có nghĩa `pay()` không match pointcut**; nó chỉ cho thấy đường gọi Java nội bộ không đi qua interceptor. Muốn kiểm thử match, phải gọi trực tiếp `service.pay()` trên Spring bean proxy.

### 9.4. Lỗi đặt annotation trên interface/override

Java annotation trên method không tự “kế thừa” giống một method override. Khi project dùng interface-based JDK proxy, annotation có thể được đặt ở interface method hoặc implementation method. Cơ chế resolution của Spring/AspectJ có các chi tiết liên quan đến method cụ thể/bridge methods. Để test đáng tin, xác minh annotation **được Spring AOP nhận diện trong cấu hình proxy thật**; trong demo đặt `@Measured` trực tiếp trên implementation method để không tạo biến số.

### 9.5. Method không thể được chặn qua proxy

- Với JDK proxy, method cần được expose qua interface để caller thông thường gọi bằng interface type.
- Với CGLIB, class `final` không thể subclass; method `final` hoặc `private` không thể override để intercept theo cơ chế proxy lớp.
- Static method, constructor và field access không phải các method execution join point mà Spring AOP tự chặn tương tự method instance trên proxy.

Một static pointcut matcher có thể xem method metadata, nhưng nếu runtime không có đường gọi qua proxy thì không có advice. Đừng viết assertion chỉ dựa vào annotation mà bỏ qua điều kiện proxy.

### 9.6. Thứ tự advice khi nhiều aspects cùng match

Ví dụ chỉ có một `@Before` advice nên `["ADVICE_PAY", "PAY"]` là thứ tự dễ kiểm chứng. Trong hệ thống có nhiều advice/transaction advisors, cần cấu hình hoặc kiểm tra thứ tự bằng `@Order`/`Ordered` khi order là yêu cầu nghiệp vụ. Không assert thứ tự ngẫu nhiên của nhiều advisors nếu application chưa định nghĩa ordering.

### 9.7. Test rò rỉ trạng thái

Nếu dùng static counter trong aspect, test chạy chung JVM có thể ảnh hưởng nhau; nếu dùng singleton context được cache cho nhiều test, event list có thể còn dữ liệu từ test trước. Demo giải quyết bằng **mỗi test tạo một context riêng**. Với integration tests lớn, cân nhắc reset spy/recorder và quản lý context lifecycle rõ ràng; không dựa vào thứ tự chạy method của JUnit.

## 10. Mở rộng phạm vi test theo dự án thật

Khi dùng các pointcut tổ chức theo package/annotation/bean name, hãy lập ma trận gồm ba trục:

- **Vùng áp dụng:** service class đúng package, service class ngoài package, interface vs implementation, bean được quản lý vs raw object.
- **Dấu hiệu chọn:** annotation có/không có, tên method, visibility, kiểu trả về, argument type, bean name có/không khớp.
- **Đường gọi:** external caller qua proxy, self-invocation, method final/private, các advisor khác cùng áp dụng.

Đừng tạo test cho mọi tổ hợp vô nghĩa. Nên có ít nhất một ca dương cho mỗi nhánh dự kiến và **một ca âm riêng cho từng ràng buộc** dễ bị bỏ sót. Nếu pointcut chứa `A && B && C`, tối thiểu cân nhắc kiểm thử: `A∧B∧C` (match), `¬A∧B∧C`, `A∧¬B∧C`, `A∧B∧¬C` (không match), cùng một số ranh giới proxy quan trọng.

Ví dụ test cho biểu thức:

~~~text
execution(public * demo..service..*.*(..))
    &&
@annotation(demo.Measured)
    &&
bean(*Service)
~~~

Một matrix tốt sẽ có: đúng package + annotation + bean name; ngoài package; không có annotation; bean name khác; self-invocation; object tự new. Mỗi ca phải nêu **mong đợi trước khi chạy test**, không điều chỉnh expected để phù hợp ngẫu nhiên với kết quả.

Có thể dùng `@SpringJUnitConfig`/Spring TestContext để giảm công tạo context ở một ứng dụng nhiều bean, nhưng khi dùng context cache cần chú ý reset recorder. Trong bài tối thiểu này, `AnnotationConfigApplicationContext` là đủ và không đòi hỏi thêm dependency `spring-test`.

## 11. Checklist rà soát một bộ test pointcut

- [ ] Pointcut có tên, scope đủ rõ; các điều kiện được ghép đúng `&&`/`||`/`!`.
- [ ] Mỗi điều kiện độc lập có **ca không match**; có ít nhất một ca match toàn bộ.
- [ ] Test chạy trên **Spring-managed bean proxy** thay vì tự `new` object khi kiểm tra advice dương.
- [ ] `@Aspect` và target đều là bean của đúng `ApplicationContext`, AOP đã được bật.
- [ ] Test dương xác nhận marker advice thực sự xuất hiện, đúng **trước/sau** target theo loại advice, không chỉ `isAopProxy`.
- [ ] Test âm xác nhận **không có marker advice** nhưng business method vẫn chạy nếu cần.
- [ ] Đã kiểm tra trường hợp annotation/method đúng nhưng class/return type sai.
- [ ] Có đối chứng self-invocation và raw Java object khi chúng là rủi ro của ứng dụng.
- [ ] Test độc lập về state, không phụ thuộc thứ tự chạy hay log ngẫu nhiên.
- [ ] Kết quả Maven/Surefire ghi nhận tất cả tests pass; khi fail, tìm assertion hoặc exception gốc.
- [ ] Không đồng nhất static method matcher với advice runtime trên proxy.

## 12. Tài liệu Spring và JUnit để tham khảo

- [Spring Framework 6.2 — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html): named pointcut, expression composition và method execution join points.
- [Spring Framework 6.2 — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): `@Before` và cách tham chiếu pointcut trong advice.
- [Spring Framework 6.2 — Pointcut API](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/pointcuts.html): `AspectJExpressionPointcut` và static/dynamic matching.
- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB proxy và self-invocation.
- [Spring Framework 6.2 — Unit Testing](https://docs.spring.io/spring-framework/reference/6.2/testing/unit.html): phân biệt unit tests độc lập với Spring context và kiểm thử tích hợp.
- [JUnit 5 User Guide](https://junit.org/junit5/docs/5.11.0/user-guide/): JUnit Jupiter `@Test` và assertion APIs.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reusable-pointcuts-009">Giữ pointcut ổn định khi đổi gói, tên lớp hoặc tên phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ pointcut ổn định khi đổi gói, tên lớp hoặc tên phương thức trong Spring AOP

## 1. Vì sao refactor dễ làm Spring Aspect ngừng hoạt động?

Trong Spring AOP, nhiều pointcut được viết dưới dạng **chuỗi biểu thức** bên trong `@Pointcut`, `@Before`, `@Around`... Chẳng hạn:

```java
@Before("execution(* com.example.orders.OrderService.submit(..))")
public void auditSubmission() {
    // ...
}
```

Biểu thức này phụ thuộc vào **tên package**, **tên class** và **tên method**. Khi đội phát triển refactor:

- `com.example.orders` chuyển sang `com.example.fulfillment`.
- `OrderService` đổi thành `FulfillmentCoordinator`.
- `submit()` đổi thành `dispatch()`.

Java source code của bean và caller có thể được IDE cập nhật để vẫn **biên dịch thành công**, nhưng chuỗi pointcut có thể **không được cập nhật tương ứng**. Kết quả nguy hiểm nhất là: ứng dụng vẫn chạy nghiệp vụ bình thường, trong khi Spring Aspect không còn áp dụng audit, metrics hoặc các chính sách dùng chung tại method mới.

Cần phân biệt hai tình huống:

1. **Biểu thức không hợp lệ:** Spring có thể báo lỗi khi parse pointcut/khởi tạo advisor, khiến application context không khởi động.
2. **Biểu thức vẫn hợp lệ nhưng không còn khớp:** Spring không nhất thiết báo lỗi. Bean có thể không được tạo proxy vì không còn advisor phù hợp, hoặc có proxy vì advisor khác nhưng advice đang quan tâm **không chạy**. Đây là nguy cơ mà integration test phải bắt.

Mục tiêu không phải viết một biểu thức sẽ tự hiểu **mọi refactor trong tương lai**. Mục tiêu là thiết kế **hợp đồng chọn join point rõ ràng, ít phụ thuộc chi tiết dễ đổi**, tập trung chỗ sửa và kiểm thử được.

**Giới hạn:** Bài này chỉ nói về **Spring Aspect và pointcut trong Spring proxy-based AOP**. Spring AOP hỗ trợ method execution trên Spring-managed beans; không dùng lời gọi Java thuần ngoài proxy hoặc AspectJ weaving làm đối tượng chính.

## 2. Mức độ phụ thuộc refactor của các loại pointcut

| Cách chọn method | Ví dụ | Khi đổi package | Khi đổi class | Khi đổi method | Đánh đổi |
| --- | --- | --- | --- | --- | --- |
| Tên đầy đủ cố định | `execution(* com.example.orders.OrderService.submit(..))` | Dễ mất match | Dễ mất match | Dễ mất match | Rất cụ thể nhưng brittle. |
| Package convention | `execution(public * com.example..service.*.*(..))` | Phụ thuộc cấu trúc package | Ít phụ thuộc tên class | Ít phụ thuộc tên method | Dễ ăn rộng hoặc bỏ sót package mới. |
| Stable interface contract | `target(com.example.api.OrderOperations)` | Bền hơn nếu interface giữ nguyên | Không buộc tên implementation | Không lọc riêng method | Cần API ổn định và điều kiện method bổ sung. |
| Annotation trên method | `@annotation(com.example.aop.MonitoredOperation)` | Không phụ thuộc package của target | Không phụ thuộc tên class target | Không phụ thuộc tên method target | Phải giữ annotation trên đúng method. |
| Annotation trên class | `@target(com.example.aop.MonitoredComponent)` | Không phụ thuộc package target | Không phụ thuộc tên class target | Chọn tập method rộng nếu không kết hợp điều kiện khác | Hợp đồng ở cấp class. |
| Bean-name convention | `bean(*Service)` | Có thể không bị ảnh hưởng | Phụ thuộc tên bean thực tế | Không lọc riêng method | Spring-specific, phụ thuộc bean naming. |
| Named pointcut | `@Pointcut("...") void businessOperation() {}` | Tập trung nơi sửa | Tập trung nơi sửa | Tập trung nơi sửa | Tự nó **không** làm nội dung expression bất biến. |

Không có công thức nào luôn tốt nhất. Nếu chính sách thật sự chỉ áp dụng riêng một method cụ thể, việc đổi tên method có thể **cần** cập nhật pointcut; đó là thay đổi có chủ đích. Nếu chính sách áp dụng cho một **vai trò nghiệp vụ ổn định**, chọn theo annotation, interface hoặc convention rõ nghĩa thường bền hơn so với tên class cụ thể.

**Một named pointcut chỉ giảm số nơi phải chỉnh sửa.** Ví dụ `@Pointcut("execution(* OldService.save(..))")` vẫn bị ảnh hưởng khi `OldService` đổi tên, dù 10 advice cùng dùng `businessSave()`.

## 3. Chiến lược nền tảng: đặt tên cho ý định, không cho cú pháp

Spring Framework khuyến nghị ghép các biểu thức phức tạp từ những **named pointcuts nhỏ**. Một class chia sẻ thường có ba lớp ý nghĩa:

```text
publicMethod()           -> method signature phù hợp
inBusinessLayer()        -> ranh giới module/kiểu bean
businessOperation()      -> vai trò nghiệp vụ dùng cho aspect

businessOperation() = publicMethod() && inBusinessLayer()
```

Ví dụ:

```java
package com.example.aop;

import org.aspectj.lang.annotation.Pointcut;

public class CommonPointcuts {

    @Pointcut("execution(public * *(..))")
    public void publicMethod() {}

    @Pointcut("within(com.example..service..*)")
    public void inServiceLayer() {}

    @Pointcut("publicMethod() && inServiceLayer()")
    public void serviceOperation() {}
}
```

Đây là Java class khai báo pointcuts; **không cần tạo `@Bean` của `CommonPointcuts`** chỉ để các aspect khác tham chiếu tên pointcut.

Trong aspect khác:

```java
@Before("com.example.aop.CommonPointcuts.serviceOperation()")
public void beforeService() {
    // ...
}
```

Lợi ích: khi di chuyển package service, chỉ sửa `inServiceLayer()` tại một nơi. Các advice sử dụng `serviceOperation()` giữ nguyên tên và vai trò.

Hạn chế: nếu package mới **không** còn thuộc `com.example..service..`, `inServiceLayer()` vẫn bỏ sót method cho đến khi bạn sửa expression. **Đặt tên không tạo tính ổn định tự động; nó tạo một điểm quản trị tập trung.**

Một lưu ý Java visibility: pointcut method `private` đủ dùng trong cùng aspect; pointcut chia sẻ giữa nhiều class nên `public`. Visibility quyết định **nơi được tham chiếu**, không quyết định target method được match có `public` hay không. Muốn chỉ match public target methods phải viết `execution(public ...)` trong expression.

## 4. Chiến lược annotation: gắn chính sách với ý nghĩa của method

Khi một chính sách audit/metrics cần tiếp tục áp dụng dù đội phát triển đổi tên package, class hoặc method, một **annotation có ý nghĩa** trên method có thể là hợp đồng ổn định:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MonitoredOperation {
}
```

Và Spring aspect:

```java
@Pointcut(
    "execution(public * *(..))"
    + " && @annotation(com.example.aop.MonitoredOperation)"
)
public void monitoredOperation() {}
```

Đây là **các fragments** cần imports/package và Spring bean registration; project runnable ở phần 8 cung cấp phiên bản đầy đủ.

Giả sử:

```java
@MonitoredOperation
public String submit(String id) { /* ... */ }
```

được refactor thành:

```java
@MonitoredOperation
public String dispatch(String id) { /* ... */ }
```

Nếu annotation vẫn hiện diện trên method mới và method vẫn có thể được gọi qua Spring proxy, pointcut `@annotation(MonitoredOperation)` không phụ thuộc vào hai tên `submit` và `dispatch`.

### 4.1. Annotation là contract, không phải phép màu

Annotation marker chỉ có tác dụng nếu:

1. Annotation có **`@Retention(RetentionPolicy.RUNTIME)`** nếu cơ chế matching cần metadata runtime.
2. Marker được đặt ở **method/class thích hợp** và được duy trì khi refactor.
3. Aspect có `@Pointcut`/`@Before` hợp lệ, được đăng ký thành Spring bean trong context đang xử lý target.
4. Target là Spring bean và lời gọi đi qua AOP proxy.
5. Method nằm trong giới hạn có thể chặn của Spring proxy (JDK interface contract hoặc CGLIB subclassing theo cấu hình).

Nếu developer **xóa marker** khi đổi tên method, Spring không thể đoán rằng method mới vẫn phải được audit. Vì vậy annotation phải trở thành một phần của **review checklist và integration tests**.

Nếu annotation được khai báo trên interface method nhưng implementation method không có marker, việc có match hay không cần được kiểm tra theo loại proxy, cách Spring/AspectJ lấy metadata và phiên bản framework. **Không mặc định annotation method luôn được kế thừa từ interface.** Java `@Inherited` dành cho annotation trên class và không tạo tính kế thừa cho method annotations. Đối với policy quan trọng, nên có một quy ước đặt marker rõ ràng và test trên bean/proxy thật.

### 4.2. Dùng `@target` hay `@within` khi scope theo class?

Nếu policy áp dụng cho **mọi public method của một class có vai trò nhất định**, có thể dùng annotation ở **cấp class**:

```java
@Pointcut(
    "execution(public * *(..))"
    + " && @target(com.example.aop.MonitoredComponent)"
)
public void monitoredComponentOperation() {}
```

- `@annotation(...)` chọn method có annotation phù hợp.
- `@target(...)` chọn theo annotation trên class của **target object**.
- `@within(...)` chọn join points trong những type có annotation phù hợp; trong Spring AOP cần chú ý type/method đang được kiểm tra.
- `target(SomeInterface)` chọn target tương thích interface hoặc type, **không dựa vào annotation**.

Các designators đều thuộc phạm vi được Spring AOP hỗ trợ, nhưng **không thay thế nhau**. Nếu chỉ một vài method cần audit, method-level `@annotation` thường chính xác hơn type-level annotation vốn chọn rộng hơn.

### 4.3. Giữ annotation package ổn định

Dùng `@annotation(com.example.aop.MonitoredOperation)` loại phụ thuộc vào package của **target**, nhưng lại giới thiệu sự phụ thuộc mới vào **FQCN của annotation type**. Vì vậy:

- Đặt annotation policy ở package/module API ổn định, ví dụ `com.example.platform.aop`.
- Giữ tên và semantic của annotation rõ ràng; tránh đổi annotation theo từng refactor service.
- Khi phải đổi annotation type/package, sửa expression tương ứng và chạy lại integration tests.
- Không trộn tên annotation vào code domain nếu policy thuần kỹ thuật và kiến trúc yêu cầu domain module không phụ thuộc hạ tầng; khi đó cân nhắc stable interface contract hoặc convention khác.

**Tính bền vững là chọn thứ ít biến động để làm hợp đồng**, chứ không thể loại bỏ mọi phụ thuộc.

## 5. Chiến lược interface: bám contract thay vì class triển khai

Khi application đã có **business interface ổn định**:

```java
public interface PaymentOperations {
    Receipt process(PaymentRequest request);
}
```

Có thể viết pointcut dựa vào **type contract**:

```java
@Pointcut(
    "execution(public * *(..))"
    + " && target(com.example.api.PaymentOperations)"
)
public void paymentApiOperation() {}
```

Đây là fragment trong aspect; cần class interface tồn tại trong ứng dụng.

`target(PaymentOperations)` trong Spring AOP xét **target object** có thuộc type contract đó không. Vì vậy đổi concrete implementation `LegacyPaymentService` thành `NewPaymentCoordinator` vẫn có thể giữ match nếu hai class implement cùng interface và bean proxy hoạt động bình thường.

Nhưng có hai ranh giới:

1. **Nếu đổi chính interface FQCN**, pointcut vẫn phải sửa. Đặt contract ở API package ít biến động.
2. `target(PaymentOperations)` chọn các method executions phù hợp mà target thực hiện, **không chỉ `process`**. Nếu policy chỉ dành cho một subset, cần thêm điều kiện method hoặc marker.

Ví dụ:

```java
@Pointcut(
    "target(com.example.api.PaymentOperations)"
    + " && @annotation(com.example.aop.MonitoredOperation)"
)
public void monitoredPaymentOperation() {}
```

Khi kết hợp, interface giới hạn **đối tượng nhận chính sách**; annotation giới hạn **method mang ý nghĩa cần giám sát**.

### `this` và `target` trong Spring

Trong Spring AOP:

- `this(MyType)` xét **Spring proxy reference** có tương thích type hay không.
- `target(MyType)` xét **target object được proxy**.

Với JDK proxy theo interface, proxy không nhất thiết là instance của concrete implementation; do đó `this(ConcreteClass)` có thể khác `target(ConcreteClass)`. Khi refactor class/interface, phải xác định mục đích là chọn theo **proxy contract** hay theo **target type**. Đây là đặc thù của Spring proxy-based AOP cần kiểm thử trên proxy strategy thực tế.

## 6. Khi nào nên dùng naming convention theo package hoặc Spring bean name?

### 6.1. Package convention

Biểu thức:

```text
execution(public * com.example..service..*.*(..))
```

có ích nếu tổ chức có quy ước package **bền vững**, chẳng hạn mọi service đều nằm dưới các package `service` của module.

Ưu điểm: không cần đánh dấu từng method; đổi tên class/method trong package thường không ảnh hưởng match.

Điểm yếu:

- Chuyển class ra khỏi package thỏa pattern -> **âm thầm mất match**.
- Đặt một class không mong muốn vào package khớp -> **được advice ngoài dự kiến**.
- Nếu structure đổi từ layer-based sang feature-based (ví dụ `orders.application`), pattern cũ có thể không còn phù hợp.
- Broad wildcard có thể match các method không thuộc trách nhiệm thực tế của policy.

Cách cải thiện: dùng named pointcut `applicationServiceOperation()` tập trung expression, document package convention và thêm regression tests khi di chuyển module.

### 6.2. Spring-specific `bean(...)` pointcut

Spring AOP hỗ trợ `bean(namePattern)` để chọn theo **Spring bean name**, ví dụ:

```java
@Pointcut("bean(*Service) && execution(public * *(..))")
public void namedServiceOperation() {}
```

Đây là fragment trong Spring `@Aspect`. `bean` là **phần mở rộng Spring-specific**, chọn theo **bean instance name**, không dựa vào Java class FQCN. Nó hữu ích khi tên bean tuân thủ convention được kiểm soát.

Tuy nhiên, **đổi tên class có thể kéo theo đổi tên bean mặc định** trong component scanning:

```text
OrderService     -> bean name mặc định: orderService
OrderCoordinator -> bean name mặc định: orderCoordinator
```

Nếu pointcut dùng `bean(*Service)`, đổi class `OrderService` thành `OrderCoordinator` có thể khiến bean không còn match, dù class vẫn thực hiện nghiệp vụ cũ. Muốn convention bền hơn, khai báo bean name rõ ràng khi thật sự cần, hoặc dùng marker/interface contract.

`bean(...)` chỉ dành cho Spring AOP, **không phải AspectJ weaving pointcut designator chuẩn**. Không dựa vào `bean` nếu có kế hoạch chuyển cùng expression sang AspectJ weaving bên ngoài Spring.

### 6.3. So sánh điểm chịu tác động khi refactor

| Thao tác | Hard-coded `execution` FQCN | `within` package convention | `@annotation` policy marker | `target(StableApi)` | `bean(*Service)` |
| --- | --- | --- | --- | --- | --- |
| Đổi tên method | Dễ đứt match | Thường không ảnh hưởng | Không ảnh hưởng nếu giữ marker | Không ảnh hưởng nếu vẫn là method qua proxy | Thường không ảnh hưởng |
| Đổi tên implementation class | Dễ đứt match | Thường không ảnh hưởng | Không ảnh hưởng nếu giữ marker | Không ảnh hưởng nếu giữ interface | Có thể ảnh hưởng tên bean mặc định |
| Di chuyển package implementation | Dễ đứt match | Có thể đứt nếu ra ngoài pattern | Không ảnh hưởng nếu marker FQCN ổn định | Không ảnh hưởng nếu interface FQCN ổn định | Không ảnh hưởng khi bean name giữ nguyên |
| Chuyển policy sang method khác | Phải sửa expression | Dễ match quá rộng | Chủ động chuyển marker | Cần lọc method/marker thêm | Cần lọc method/marker thêm |
| Đổi chính annotation/interface/API | Không nhất thiết | Không nhất thiết | Phải cập nhật marker + expression | Phải cập nhật interface + expression | Không nhất thiết |

Cách đọc bảng: **“không ảnh hưởng” chỉ nói về text pattern**, không bảo đảm Spring AOP sẽ chạy nếu bean không còn do Spring quản lý hoặc lời gọi đi ngoài proxy.

## 7. Đừng dùng wildcard quá rộng chỉ để “sống sót qua refactor”

Một số lựa chọn tưởng là giải pháp:

```java
@Before("execution(* *(..))")
public void anyMethod() {}
```

hoặc:

```java
@Before("within(com.example..*)")
public void anyApplicationClassMethod() {}
```

Đây là fragments minh họa, không phải khuyến nghị áp dụng production.

Các điểm cần cảnh giác:

- Pattern rộng dễ áp dụng advisor lên những bean chưa từng có policy trước đây.
- Advice có thể xuất hiện ở những method không liên quan, làm tăng chi phí thực thi, logging volume và coupling.
- Policy bảo mật hoặc audit có thể vô tình đi vào infrastructure methods, dẫn đến hệ quả khó nhận biết.
- Nếu giữ chung với những dynamic conditions như `args(...)`, không đặt scope rõ còn có thể tăng chi phí matching trên nhiều beans.
- Một wildcard rất rộng không chứng minh nó “bền”: vẫn phụ thuộc **bean có đi qua proxy**, scope/context, method visibility và runtime mechanism.

Spring docs khuyến nghị **pointcut có scope đủ hẹp**, ưu tiên cấu thành bằng những named pointcuts có ý nghĩa. Khi dùng annotation marker ổn định trong nhiều package, biểu thức `execution(public * *(..)) && @annotation(Marker)` có thể hợp lý vì annotation đã là bộ lọc explicit. Nhưng phải kiểm tra annotation có bị dùng ngoài dự kiến và application có performance constraints hay không.

**Stability không đồng nghĩa với maximum match breadth.** Một refactor an toàn là refactor giữ nguyên **tập join points dự kiến**, không phải refactor làm advice chọn mọi thứ.

## 8. Ví dụ Java 17+ chạy được: class và method đổi tên, policy vẫn giữ

Ví dụ mô phỏng hai service thuộc **hai package khác nhau** và có **tên class/method khác nhau**:

- Service cũ: `com.example.orders.LegacyOrderService.submit()`.
- Service mới: `com.example.fulfillment.RenamedFulfillmentCoordinator.dispatch()`.
- Cả hai có method được đánh dấu `@MonitoredOperation`.
- Spring `@Aspect` dùng một **named pointcut có ý nghĩa** theo annotation, không hard-code hai tên class/method.
- Một method không mang marker là đối chứng âm: nó phải chạy nghiệp vụ **mà không chạy advice**.

Đây là mô phỏng *trước/sau refactor bằng hai service cùng tồn tại* để tiện so sánh, không phải yêu cầu triển khai song song hai implementation trên production.

Phiên bản project: Java 17+, Spring Framework **6.1.14**, `aspectjweaver` **1.9.22.1**, Maven. Các version này được cố định để học, **không tuyên bố là mới nhất**.

### 8.1. Cấu trúc thư mục

```text
spring-stable-pointcuts/
├── pom.xml
└── src/main/java/com/example/
    ├── aop/MonitoredOperation.java
    ├── aop/ObservationAspect.java
    ├── orders/LegacyOrderService.java
    ├── fulfillment/RenamedFulfillmentCoordinator.java
    └── app/AppConfig.java
    └── app/DemoApp.java
```

### 8.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-stable-pointcuts</artifactId>
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

`spring-context` mang các thành phần Spring IoC và dependency Spring AOP cần thiết trong cây phụ thuộc; `aspectjweaver` phục vụ việc Spring phân tích các `@AspectJ` pointcut expressions. Ví dụ sử dụng **Spring proxy-based AOP**, không có AspectJ compiler hoặc weaving agent.

### 8.3. `src/main/java/com/example/aop/MonitoredOperation.java`

```java
package com.example.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MonitoredOperation {
}
```

Marker có retention ở runtime và chỉ áp dụng cho method. Đặt tại package `com.example.aop` ổn định, thay vì đặt nó trong package của service dự kiến sẽ đổi.

### 8.4. `src/main/java/com/example/orders/LegacyOrderService.java`

```java
package com.example.orders;

import com.example.aop.MonitoredOperation;

public class LegacyOrderService {

    @MonitoredOperation
    public String submit(String id) {
        System.out.println("[ORDER] submit " + id);
        return "submitted:" + id;
    }

    public String preview(String id) {
        System.out.println("[ORDER] preview " + id);
        return "preview:" + id;
    }
}
```

`submit` mang marker; `preview` không mang marker. Hai method đều public và được gọi ngoài bean qua Spring AOP proxy trong ví dụ.

### 8.5. `src/main/java/com/example/fulfillment/RenamedFulfillmentCoordinator.java`

```java
package com.example.fulfillment;

import com.example.aop.MonitoredOperation;

public class RenamedFulfillmentCoordinator {

    @MonitoredOperation
    public String dispatch(String id) {
        System.out.println("[FULFILLMENT] dispatch " + id);
        return "dispatched:" + id;
    }
}
```

Class mới nằm ngoài `com.example.orders`, không còn tên `OrderService` và method không còn tên `submit`. **Chỉ policy marker được giữ**. Một pointcut cứng `execution(* com.example.orders.LegacyOrderService.submit(..))` sẽ không khớp method `dispatch()`; pointcut theo marker vẫn có thể khớp.

### 8.6. `src/main/java/com/example/aop/ObservationAspect.java`

```java
package com.example.aop;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class ObservationAspect {
    private final AtomicInteger calls = new AtomicInteger();

    @Pointcut("execution(public * *(..))")
    public void publicMethod() {}

    @Pointcut(
        "publicMethod()"
        + " && @annotation(com.example.aop.MonitoredOperation)"
    )
    public void monitoredOperation() {}

    @Before("monitoredOperation()")
    public void beforeMonitoredCall(JoinPoint jp) {
        calls.incrementAndGet();
        System.out.println("[AOP] " + jp.getSignature().getName());
    }

    public int calls() {
        return calls.get();
    }
}
```

**Điểm ổn định nằm ở đâu?**

- `publicMethod()` mô tả loại method Spring AOP cần chặn.
- `monitoredOperation()` đặt tên cho điều kiện business/technical policy: method được đánh dấu marker.
- `@Before("monitoredOperation()")` không chứa tên `LegacyOrderService`, `RenamedFulfillmentCoordinator`, `submit` hay `dispatch`.
- Khi đổi class/package/method, chỉ cần giữ annotation trên method mục tiêu và bảo đảm bean vẫn được proxy đúng.
- `JoinPoint.getSignature().getName()` in **tên method thật đã được gọi**; vì vậy trước refactor in `submit`, sau refactor in `dispatch` mà không cần đổi logic advice.

`ObservationAspect` là Spring bean singleton trong context vì được đăng ký ở `AppConfig`; counter `AtomicInteger` đo số lần advice, không đo số bean target hay số lần pointcut signature method được gọi.

### 8.7. `src/main/java/com/example/app/AppConfig.java`

```java
package com.example.app;

import com.example.aop.ObservationAspect;
import com.example.fulfillment.RenamedFulfillmentCoordinator;
import com.example.orders.LegacyOrderService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public LegacyOrderService legacyOrderService() {
        return new LegacyOrderService();
    }

    @Bean
    public RenamedFulfillmentCoordinator fulfillmentCoordinator() {
        return new RenamedFulfillmentCoordinator();
    }

    @Bean
    public ObservationAspect observationAspect() {
        return new ObservationAspect();
    }
}
```

`@EnableAspectJAutoProxy` bật Spring auto-proxy infrastructure; `proxyTargetClass=true` chọn CGLIB để demo inject/look up hai concrete service classes không implement interface. **Đây không phải điều kiện bắt buộc cho marker policy**: hệ thống có business interfaces vẫn có thể dùng JDK proxy với những điều chỉnh phù hợp.

Khi refactor một bean, code đăng ký `@Bean` hoặc component scanning cũng phải được cập nhật, nếu không bean mới chưa chắc có mặt trong context. Pointcut bền vững **không thể bù cho bean registration sai**.

### 8.8. `src/main/java/com/example/app/DemoApp.java`

```java
package com.example.app;

import com.example.aop.ObservationAspect;
import com.example.fulfillment.RenamedFulfillmentCoordinator;
import com.example.orders.LegacyOrderService;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApp {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            LegacyOrderService oldService =
                    context.getBean(LegacyOrderService.class);
            RenamedFulfillmentCoordinator newService =
                    context.getBean(RenamedFulfillmentCoordinator.class);
            ObservationAspect aspect =
                    context.getBean(ObservationAspect.class);

            System.out.println("oldProxy="
                    + AopUtils.isAopProxy(oldService));
            System.out.println("newProxy="
                    + AopUtils.isAopProxy(newService));

            System.out.println("oldResult=" + oldService.submit("O-1"));
            System.out.println("previewResult=" + oldService.preview("O-2"));
            System.out.println("newResult=" + newService.dispatch("F-3"));

            System.out.println("adviceCalls=" + aspect.calls());
        }
    }
}
```

Hai bean đều được lookup từ `ApplicationContext` và gọi bằng references do Spring trả về. Không có `new` target trực tiếp trong caller và không có self-invocation làm nhiễu kết quả.

### 8.9. Lệnh chạy và stdout kỳ vọng

Yêu cầu JDK 17+, Maven tải được dependencies. Tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.app.DemoApp
```

Các dòng do chương trình và advice in ra theo đúng thứ tự (không tính log Maven/Spring nếu môi trường có):

```text
oldProxy=true
newProxy=true
[AOP] submit
[ORDER] submit O-1
oldResult=submitted:O-1
[ORDER] preview O-2
previewResult=preview:O-2
[AOP] dispatch
[FULFILLMENT] dispatch F-3
newResult=dispatched:F-3
adviceCalls=2
```

Diễn giải:

1. Hai Spring beans có các marked methods nên auto-proxy creator đã tạo AOP proxies tương ứng.
2. `submit` được marker, advice chạy trước target.
3. `preview` không được marker, nên **không có dòng `[AOP]`** cho lời gọi đó dù `LegacyOrderService` đã là một proxy.
4. `dispatch` thuộc **class và package khác**, tên method cũng khác, nhưng marker giữ policy nên advice vẫn chạy.
5. `adviceCalls=2` chứng minh chỉ hai method được chọn; không phải cứ gọi mọi method trên một proxied bean thì advice chạy.
6. Nếu xóa `@MonitoredOperation` khỏi `dispatch` sau refactor, Spring có thể vẫn khởi động bình thường nhưng test **phải thất bại** khi kỳ vọng advice xuất hiện cho method mới.

Đây là thí nghiệm về **policy semantics tồn tại qua refactor**, không phải yêu cầu giữ hai service có vai trò tương tự trong cùng ứng dụng thực tế.

## 9. Phương án thay thế khi không muốn annotate từng method

Không phải codebase nào cũng muốn gắn custom annotation ở application/service code. Có ba chiến lược khác:

### 9.1. Dùng stable interface hoặc class annotation ở module API

Nếu nhiều implementations cùng tuân theo `BillingOperations`, dùng `target(com.example.api.BillingOperations)`. Khi đổi class implementation, pointcut có thể vẫn đúng.

Nếu policy áp dụng cho toàn bộ public methods của một bean class, annotation trên class + `@target` có thể giảm số marker so với annotate từng method.

Điểm cần kiểm tra: việc đổi interface/class annotation type, biến đổi bean scopes/proxy interfaces và số methods bị match có thể làm thay đổi tập join points.

### 9.2. Naming convention kèm test

Trong một kiến trúc module hóa ổn định, có thể chọn `execution(public * com.example..application.*.*(..))`. Quy tắc tổ chức package phải được ghi nhận thành contract và được enforce trong review, architecture tests hoặc code checks. Khi chuyển package khỏi pattern, test phải phát hiện thiếu advice.

Không nên để package name là một giả định ngầm mà chỉ người viết aspect ban đầu biết.

### 9.3. Explicit bean name cho Spring-specific selection

Một số ứng dụng muốn định danh một bean cụ thể bất kể implementation class:

```java
@Service("primaryBillingService")
public class BillingAdapter {
    // ...
}
```

Có thể kết hợp `bean(primaryBillingService)` với `execution(public * *(..))`. Nếu tiếp tục giữ Spring bean name ổn định trong refactor, đổi class/package không nhất thiết ảnh hưởng rule.

Đây là **fragments**, không phải project runnable. Chỉ phù hợp khi bean name thực sự là một phần contract được kiểm soát. Việc duplicate bean names hoặc nhiều aliases cần được xem xét; không coi `bean(...)` như giải pháp universal cho mọi aspect.

## 10. Thiết kế test để bắt regression khi refactor

Một test Spring AOP cần kiểm chứng **hành vi advice**, không chỉ application context load thành công. Nên có cả **positive** và **negative** cases.

### 10.1. Ma trận trước/sau

| Scenario | Trước refactor | Sau refactor | Kết quả advice mong đợi |
| --- | --- | --- | --- |
| Method có `@MonitoredOperation` | `LegacyOrderService.submit()` | `RenamedFulfillmentCoordinator.dispatch()` | Vẫn chạy 1 lần trên mỗi lời gọi qua proxy. |
| Method không có marker | `preview()` | Method tương đương không marker | Không chạy policy này. |
| Bean chuyển package | `orders` | `fulfillment` | Vẫn chạy nếu annotation/contract ổn định và bean được đăng ký. |
| Bean không còn thuộc Spring context | Trước: Spring-managed | Sau: instance `new` | Spring AOP không chạy; phải sửa kiến trúc bean boundary. |
| Method bị gọi qua `this` | Cross-bean call trước đây | Self-invocation sau refactor | Advice có thể bị bypass; test phát hiện. |
| Concrete method thành `final` trên class proxy | Method intercept được | CGLIB không override được | Test phải phát hiện thiếu interception hoặc lỗi proxying. |

Không cần kiểm tra mọi permutation của application nếu quá tốn kém. Nhưng **các method thực sự mang chính sách quan trọng** cần được đưa vào một tập regression tests có tên rõ ràng.

### 10.2. Test qua Spring proxy chứ không tạo target bằng `new`

Một test cần dùng:

```java
LegacyOrderService service = context.getBean(LegacyOrderService.class);
service.submit("T-1");
```

chứ không thay bằng:

```java
new LegacyOrderService().submit("T-1");
```

Đây là **fragments**. Trường hợp thứ hai chỉ kiểm tra logic target Java, **không chứng minh được gì về Spring AOP proxy**.

Nếu sử dụng JDK proxy, nên lookup/inject theo business interface thay vì concrete type, hoặc cấu hình class proxy có chủ đích. Đừng thay đổi kiểu proxy để làm test “xanh” mà không xét hợp đồng injection của production.

### 10.3. Kiểm tra chính xác số lần và scope lời gọi

Một integration test có thể dùng counter/event recorder an toàn để assert:

```text
markedOperation()    -> adviceCount +1
unmarkedOperation()  -> adviceCount +0
renamedMarkedMethod()-> adviceCount +1
```

Nếu có nhiều aspects/advisors, đếm riêng từng policy. Không lấy tổng số Spring proxies hoặc số advisor làm bằng chứng một `@Before` đang chạy đúng: một bean có thể được proxy vì transaction/security/metric advice khác.

Có thể dùng `AopUtils.isAopProxy(bean)` như kiểm tra bổ trợ, nhưng **assert hành vi invocation** mới xác minh được pointcut.

### 10.4. Kiểm tra build-time và static review

Chuỗi AspectJ pointcut ở `@Pointcut` không phải một Java symbol reference đơn giản. IDE rename refactoring có thể cập nhật một số chuỗi hoặc không, tùy IDE/tooling. Quy trình an toàn:

1. Tìm toàn bộ `@Pointcut`, `@Before`, `@After*`, `@Around`, XML `<aop:...>` chứa biểu thức.
2. Tìm exact old FQCN/method names trong pointcut expression strings, không chỉ ở Java imports/references.
3. Kiểm tra marker annotations, stable interfaces, bean names và module boundaries.
4. Chạy unit tests nghiệp vụ và **Spring AOP integration tests**.
5. Đọc Spring startup warnings, nhưng **không dựa vào việc startup thành công** để kết luận pointcut còn đúng.

Ví dụ tìm kiếm tại repository đang sử dụng `rg`:

```bash
rg -n '@Pointcut|@Before|@Around|@AfterReturning|@AfterThrowing' src/main/java
rg -n 'OldService|oldMethod|com\.example\.oldpackage' src/main/java src/main/resources
rg -n '<aop:|pointcut-ref|pointcut=' src/main/resources
```

Các lệnh này chỉ là ví dụ thao tác source code, không thay đổi bất kỳ file nào. Khi dùng PowerShell, `rg` cần được cài đặt riêng; có thể dùng IDE search tương đương.

## 11. Khi thay đổi package hoặc class name, thực hiện refactor theo từng bước

Một workflow Spring AOP refactor đáng tin:

1. **Định nghĩa policy:** xác định chính xác những method nào *phải* chạy advice và những method nào *không được* chạy.
2. **Chọn stable anchor:** marker annotation, stable interface, class annotation, hoặc package/bean convention có quản trị.
3. **Gom logic chọn vào named pointcut:** ví dụ `monitoredOperation()` thay vì sao chép expression khắp advice classes.
4. **Viết behavior tests trước khi refactor:** assert advice event/counter, target result, exception và trường hợp không match.
5. **Thực hiện rename/move** và cập nhật Spring bean registration/scan/import XML tương ứng.
6. **Xác minh marker/contract còn giữ nguyên:** đặc biệt annotation trên implementation methods, target class/interface và bean naming.
7. **Kiểm tra Spring proxy boundary:** đảm bảo caller vẫn đi qua Spring bean reference; không tạo `new` service, không biến cross-bean call thành self-invocation.
8. **Rà soát mọi expression còn tên cũ:** kể cả XML và strings trong Java annotations, chính sách khác `@Transactional`/caching nếu dựa vào proxy.
9. **Chạy lại integration tests** với đúng Spring version, proxy type và active profiles.
10. **Đo tác động ngoài dự kiến:** khi chuyển từ hard-coded class pattern sang annotation/marker, bảo đảm không có thêm bean/method được đánh dấu sai.

Một named pointcut **không ngăn việc đổi policy ngoài ý muốn**. Nó chỉ cung cấp một điểm neo để thảo luận, review và test. Đặc biệt với audit/security, thay đổi tập join points là thay đổi hành vi của hệ thống, cần được xem như một thay đổi functional quan trọng.

## 12. Lỗi phổ biến và cách chẩn đoán

| Triệu chứng sau refactor | Nguyên nhân khả dĩ | Hành động |
| --- | --- | --- |
| Spring vẫn khởi động nhưng audit advice không chạy | Expression chứa old class/method name không còn match | Tìm string pointcuts và chạy positive interception test. |
| Annotation marker đã giữ nhưng advice mất | Bean chưa được scan, marker đặt trên vị trí khác, proxy/call boundary đổi | Kiểm tra Spring bean registry, annotation metadata trên implementation, proxy và invocation path. |
| Method bị đổi tên nhưng named pointcut vẫn tên cũ | Tên `businessOperation()` ổn định nhưng expression bên trong hard-code old method | Chỉnh expression ở một nơi, chạy test; named pointcut không tự đổi nghĩa. |
| Advice bất ngờ áp dụng lên quá nhiều methods | Thay old exact signature bằng wildcard quá rộng | Giới hạn scope theo business policy/marker và thêm negative tests. |
| Dùng `within(com.example..service..*)` rồi chuyển class sang `application` | Package convention không còn khớp | Đổi named scope rule hoặc chọn stable annotation/interface. |
| Dùng `bean(*Service)` và đổi class thành `Coordinator` | Component-scan bean name mặc định cũng thay, không còn match pattern | Dùng explicit stable bean name hoặc chọn policy marker. |
| Pointcut `target(OldInterface)` không còn match | Target không còn implement interface cũ hoặc interface FQCN đã đổi | Đánh giá contract và sửa điểm neo có chủ đích. |
| `@annotation` không match sau khi chuyển annotation sang interface method | Method annotation metadata không được duy trì như giả định | Quy ước marker trên concrete implementation và test JDK/CGLIB theo cấu hình. |
| Method bị đổi thành `private` hoặc `final` | Proxy không intercept được như trước | Xem API/proxy contract, thiết kế lại public entry point khi cần. |
| Advice mất sau khi gộp hai services | Cross-bean call biến thành `this.method()` | Tách trách nhiệm qua bean khác hoặc redesign method boundary. |
| Pointcut expression không parse được | Tên referenced pointcut/type sai, cú pháp `&&`/`@annotation` hỏng | Đọc exception lúc context tạo advisor, sửa expression. |
| Bean vẫn `isAopProxy=true` nhưng policy audit không chạy | Proxy đang có advisor khác, audit pointcut không match | Quan sát policy counter/event và advisors, không chỉ proxy boolean. |
| Test cũ chỉ assert response nên không phát hiện mất audit | Không kiểm tra behavior advice | Bổ sung positive/negative interception assertions cho policy. |

## 13. Những giới hạn không thể “giải quyết” chỉ bằng pointcut ổn định

Dù dùng semantic marker và named pointcuts, Spring AOP vẫn có những ranh giới:

- **Chỉ Spring-managed beans** đi qua hạ tầng proxy mới được áp dụng kiểu advice này.
- **Self-invocation** trong target, như `this.someMethod()`, không đi qua Spring proxy.
- **Object tạo trực tiếp bằng `new`** ngoài container không được auto-proxy theo cấu hình thường dùng.
- **JDK proxy** dựa trên interfaces, vì thế method ngoài interface có thể không có đường gọi qua proxy.
- **CGLIB class proxy** không override được class/method `final`, method `private` và những method bị giới hạn bởi Java/module visibility.
- **Spring context boundary** quan trọng: auto-proxy creator trong một context không tự xử lý beans chỉ tồn tại ở context khác.
- **Annotation marker không tự đăng ký aspect bean:** class `@Aspect` cần được Spring quản lý, chẳng hạn `@Component` hoặc `@Bean`, cùng cơ chế AOP được bật.

Nếu một refactor thay đổi chính các ranh giới này, pointcut dù viết rất “bền” vẫn có thể không hoạt động. Cần thiết kế lại cách ứng dụng gọi method, không chỉ sửa chuỗi expression.

## 14. Checklist thiết kế trước khi merge refactor

- [ ] Pointcut biểu đạt **ý nghĩa ổn định** của policy thay vì phụ thuộc vào tên class/method tùy ý.
- [ ] Các biểu thức được gom thành **named pointcuts** có tên dễ hiểu khi tái sử dụng.
- [ ] Annotation/interface/bean-name convention được chọn làm anchor có chủ sở hữu và được document.
- [ ] Không có old FQCN/method names trong strings pointcuts hoặc XML còn hiệu lực.
- [ ] Marker annotations vẫn ở vị trí Spring AOP có thể nhận diện, với retention thích hợp.
- [ ] Bean mới vẫn được Spring đăng ký và lời gọi từ caller đi qua proxy.
- [ ] Proxy type JDK/CGLIB, method visibility, `final`, và context/scope không bị thay đổi trái ý.
- [ ] Positive test chứng minh advice chạy trên method đã đổi tên/di chuyển.
- [ ] Negative test chứng minh advice **không** chạy trên method không thuộc policy.
- [ ] Test kiểm tra đúng số lần advice, kết quả nghiệp vụ và exceptions.
- [ ] Không dùng wildcard rộng khiến chính sách lan ngoài phạm vi.
- [ ] Startup/logs được kiểm tra nhưng **không thay thế** behavior tests.

### Tài liệu Spring Framework chính thức

- Declaring a Pointcut, Combining Pointcut Expressions, Sharing Named Pointcut Definitions: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Declaring Advice: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Schema-based AOP Support: https://docs.spring.io/spring-framework/reference/core/aop/schema.html

**Kết luận:** Giữ pointcut ổn định khi refactor trong Spring AOP là thiết kế một **contract chọn method rõ ràng**: tên pointcut dùng lại được, anchor ít biến động (annotation/interface/convention) và integration tests kiểm chứng đúng những lời gọi phải được advice. Đừng đánh đổi tính ổn định bằng wildcard quá rộng; điều cần bảo toàn là **tập hành vi nghiệp vụ được Spring Aspect áp dụng**, không chỉ một chuỗi pointcut vẫn parse thành công.


</details>

- [Quay lại đầu trang](#back-to-top)
