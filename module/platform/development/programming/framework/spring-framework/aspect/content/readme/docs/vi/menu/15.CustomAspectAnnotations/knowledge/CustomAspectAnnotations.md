<a id="back-to-top"></a>

# Annotation tùy chỉnh

## Menu
- [Định nghĩa annotation đánh dấu dùng với Spring AOP](#custom-aspect-annotations-001)
- [Chọn @Target và @Retention phù hợp để đọc lúc chạy](#custom-aspect-annotations-002)
- [Ràng buộc giá trị annotation vào advice](#custom-aspect-annotations-003)
- [Annotation trên interface, phương thức triển khai và lớp](#custom-aspect-annotations-004)
- [Annotation tổng hợp trong Spring và giới hạn khớp pointcut cần kiểm chứng](#custom-aspect-annotations-005)
- [Phân biệt đọc metadata bằng API Spring với khớp biểu thức AspectJ](#custom-aspect-annotations-006)
- [Thiết kế thuộc tính annotation rõ nghĩa và dễ kiểm thử](#custom-aspect-annotations-007)
- [Không biến annotation thành nơi chứa quy tắc nghiệp vụ khó theo dõi](#custom-aspect-annotations-008)
- [Giữ API annotation ổn định khi tái sử dụng aspect giữa các module](#custom-aspect-annotations-009)

## <a id="custom-aspect-annotations-001">Định nghĩa annotation đánh dấu dùng với Spring AOP</a>

<details>
<summary>Xem chi tiết</summary>

# Định nghĩa annotation đánh dấu dùng với Spring AOP

## 1. Annotation đánh dấu có vai trò gì trong Spring Aspect?

Trong một ứng dụng Spring, ta thường muốn áp dụng một chính sách ngang nhiều service methods — ví dụ audit những thao tác nghiệp vụ quan trọng, theo dõi latency hoặc kiểm tra một loại operation — mà không phải liệt kê cố định mọi tên phương thức trong biểu thức pointcut.

Một **custom marker annotation** có thể đóng vai trò **hợp đồng khai báo**: lập trình viên đánh dấu các method được phép/cần áp dụng chính sách; Spring AOP aspect định nghĩa một pointcut phù hợp, rồi advice chỉ chạy khi một **method execution qua Spring proxy** khớp với điều kiện.

```java
@TrackCall("payment")
public String charge(String reference) {
    return "charged:" + reference;
}
```

Đây là **method fragment**, cần định nghĩa `@TrackCall` và đặt trong một Spring service bean.

Chú ý ba việc độc lập:

1. **Định nghĩa annotation**: tạo một Java annotation type có `@Target` và `@Retention` thích hợp.
2. **Đặt annotation trên method**: biểu thị ý định nghiệp vụ, không tự thực thi code.
3. **Khai báo Spring `@Aspect`/advice**: xác định join point được chọn bằng `@annotation`, rồi xử lý logic khi lời gọi **đi qua Spring AOP proxy**.

Một annotation tự tạo **không tự bật Spring AOP**, **không tự biến class thành Spring bean**, và **không tự gây interception**. Chính hạ tầng Spring AOP cùng advice/pointcut mới làm điều đó.

Phạm vi bài này chỉ tập trung **Spring Framework Aspect** dùng annotation-style `@AspectJ` qua proxy, theo ràng buộc của chủ đề Spring Aspect. Kiến thức Java annotation chung được dùng đúng mức cần thiết để cấu hình Spring AOP, không mở rộng sang AspectJ weaving độc lập.

## 2. Định nghĩa annotation tối thiểu có thể dùng ở runtime

Annotation tối thiểu cho các method:

```java
package com.example.marker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackCall {
}
```

Đây là **định nghĩa Java annotation hoàn chỉnh**; có thể lưu ở `TrackCall.java` trong đúng package.

Ý nghĩa:

- `public @interface TrackCall` định nghĩa một annotation type, không phải Java interface thông thường cần method implementation.
- `@Target(ElementType.METHOD)` cho phép dùng trên method declarations; tránh đặt marker sai trên class/field/parameter.
- `@Retention(RetentionPolicy.RUNTIME)` giữ annotation có thể tra cứu ở runtime, phục vụ reflection và matching/binding của Spring AOP.
- Chỉ khi method được gọi **qua Spring AOP proxy phù hợp** và có matching advisor, marker mới tác động đến thực thi.

Với một marker không có thuộc tính, cách dùng là `@TrackCall`, không cần ngoặc.

Trong ứng dụng thực tế, nếu muốn ghi thêm một nhãn/loại sự kiện, annotation có thể khai báo elements:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackCall {
    String value();
    boolean logResult() default false;
}
```

Đây là **định nghĩa thay thế**, không đặt cùng lúc với định nghĩa phía trên trong một project. Khi có `String value()` không có default, người dùng phải cung cấp nhãn: `@TrackCall("payment")` hoặc `@TrackCall(value = "payment")`. `logResult()` có default `false` nên có thể bỏ qua.

Các attribute là **metadata cấu hình cho advice**, không phải mutable variables. Không nên lưu password, token, PII, raw SQL hay dữ liệu secret trong annotation attributes — metadata annotation thường hiện diện trong bytecode và có thể được reflection đọc.

## 3. Những meta-annotation nên và không nên dùng

### 3.1. `@Retention(RUNTIME)` — lựa chọn phù hợp cho Spring AOP

| Retention | Khi nào metadata còn tồn tại? | Phù hợp Spring `@annotation`? |
| --- | --- | --- |
| `SOURCE` | Trong source, không còn trong bytecode | **Không** để làm runtime marker |
| `CLASS` | Trong class file, nhưng không được JVM cung cấp cho runtime reflection theo cơ chế thông thường | **Không** cho thiết kế cần runtime matching |
| `RUNTIME` | Được JVM giữ để tra cứu runtime | **Có**, nên dùng |

Khi quên `@Retention`, Java mặc định dùng `RetentionPolicy.CLASS`. Đây là một lỗi dễ gặp: code đặt `@TrackCall` vẫn biên dịch nhưng Spring AOP pointcut dựa trên annotation có thể **không match như mong đợi**.

Không giải quyết bằng cách đổi tên method advice hoặc đổi `@Before` thành `@Around`; phải sửa định nghĩa annotation rồi **rebuild**.

### 3.2. `@Target(METHOD)` — chọn điểm gắn đúng hợp đồng

Nếu annotation dùng để chọn **method executions** trong Spring AOP, `ElementType.METHOD` là mục tiêu tự nhiên.

Nếu muốn tạo **composed annotations** về sau:

```java
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackCall {
    String value();
}
```

`ANNOTATION_TYPE` cho phép annotation này xuất hiện trên định nghĩa của annotation khác. Đây là **biến thể thiết kế**, không bắt buộc cho marker trực tiếp.

Cần phân biệt:

- `@annotation`: match annotation trên **method execution**.
- `@target`: match annotation gắn trên target **class/type**.
- `@within`: match các method executions thuộc type đã annotate theo semantics Spring.
- `@args`: xét annotation của **runtime argument types**.
- `args`: xét/bind runtime argument values/types, **không phải** chính annotation của method.

Không sửa `@Target` thành `TYPE` chỉ vì aspect đặt trên một service class. Chọn designator theo chỗ thực sự gắn marker.

### 3.3. `@Documented` — tùy chọn nhưng hữu ích

```java
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackCall {
    String value();
}
```

`@Documented` giúp annotation xuất hiện trong Javadoc của thành phần được annotate, nếu công cụ Javadoc tương ứng xử lý. Nó **không** quyết định Spring pointcut matching.

### 3.4. Không trông chờ `@Inherited` giải quyết method annotation inheritance

Java `@Inherited` áp dụng inheritance semantics cho **class-level annotation lookup**, không có nghĩa annotations trên interface method hoặc superclass method tự được tất cả overridden methods kế thừa theo `Method.getAnnotation()`.

Nếu policy cần tìm annotation trên interface/superclass/implementation method, xem phần 10 về `AopUtils.getMostSpecificMethod` và `AnnotatedElementUtils`; đừng nghĩ thêm `@Inherited` vào một method marker là đủ.

### 3.5. `@Repeatable` không phải cơ chế aspect mặc định đọc mọi bản

Một Java repeatable annotation có thể chứa nhiều instance của cùng annotation type. Tuy nhiên, đừng giả định một pointcut `@annotation(TrackCall)` sẽ **bind tất cả** instances theo ý bạn. Nếu cần nhiều policy records trên một method, phải định nghĩa container/repeatability và kiểm thử đúng Spring version/API; nhiều khi một annotation mang array values đơn giản hơn.

## 4. Một thiết kế marker annotation có metadata an toàn

Một ví dụ thực dụng:

```java
package com.example.marker;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackCall {
    String value();
    boolean logResult() default false;
}
```

Metadata:

- `value()` biểu thị **tên hành động** ổn định như `charge`, `cancel`; tránh phụ thuộc package/class/method name nếu chúng có thể refactor.
- `logResult() default false` tạo **opt-in** rõ ràng trước khi in kết quả. Trong production, vẫn cần chính sách data classification/redaction; không phải cứ true là có quyền log PII.
- `@Documented` hữu ích cho tài liệu API.
- `METHOD` và `RUNTIME` giữ định nghĩa nhất quán với pointcut Spring.

Cách dùng:

```java
@TrackCall(value = "charge", logResult = true)
public String charge(String reference) {
    return "charged:" + reference;
}

@TrackCall("cancel")
public String cancel(String reference) {
    throw new IllegalStateException("already finalized");
}
```

Các method fragments này sẽ có project chạy đầy đủ tại phần 8.

Nên đặt annotation ở một **package API ổn định** và chọn tên mang ý nghĩa hành vi. Tránh dùng `@Audit` vừa để đánh dấu “được phép gọi”, vừa dùng để biểu thị “phải ghi log toàn bộ PII”, nếu các ý nghĩa ấy cần policy khác nhau. Marker chỉ là **intent/metadata**, không thay thế authorization hoặc business validation.

## 5. Spring pointcut `@annotation` là cầu nối đến marker

Spring Framework hỗ trợ `@annotation` trong AspectJ-style pointcut expressions dành cho Spring AOP. Nó chọn method executions có annotation tương ứng.

### 5.1. Match theo annotation type

```java
@Before(
    "execution(public * com.example.marker.PaymentService.*(..))"
    + " && @annotation(com.example.marker.TrackCall)"
)
public void beforeTracked() {
    // Chạy trước method được đánh dấu.
}
```

Đây là **fragment advice**, chưa có class `@Aspect` và imports. Trong biểu thức:

- `execution(public * com.example.marker.PaymentService.*(..))` giới hạn các public methods của một service cụ thể.
- `@annotation(com.example.marker.TrackCall)` chọn method có marker đúng loại.
- `&&` yêu cầu **cả hai điều kiện cùng đúng**.
- Dùng tên fully-qualified trong string pointcut giúp tránh mơ hồ khi parser không có ngữ cảnh import Java.

Marker type **không phải pointcut expression bằng chính tên method**. Ví dụ chỉ đổi tên `charge()` thành `submitCharge()` mà vẫn giữ `@TrackCall` có thể không làm thay đổi chính sách nếu phần `execution` đủ rộng.

### 5.2. Bind annotation instance vào advice

Có thể dùng named binding:

```java
@Around(
    value = "execution(public * com.example.marker.PaymentService.*(..))"
          + " && @annotation(marker)",
    argNames = "marker"
)
public Object trace(ProceedingJoinPoint pjp, TrackCall marker)
        throws Throwable {

    System.out.println("action=" + marker.value());
    return pjp.proceed();
}
```

Đây là **fragment** cho một class `@Aspect`. Với pointcut này:

- `marker` trong `@annotation(marker)` là **binding identifier**.
- `TrackCall marker` trong Java advice signature là formal parameter nhận annotation object.
- `argNames="marker"` giúp Spring map tên annotation binding rõ ràng.
- `ProceedingJoinPoint` đứng **đầu advice parameter list**, nên tên của nó có thể bỏ khỏi `argNames` theo quy tắc Spring.
- `marker.value()` và `marker.logResult()` cho phép advice lấy **metadata type-safe** mà không tự dùng reflection.
- `pjp.proceed()` thực thi phần chain phía trong; advice phải return giá trị phù hợp method contract.

**Không dùng `@annotation(TrackCall)` rồi khai báo parameter `marker` mà mong Spring bind tự động theo tên không liên quan.** Khi cần annotation object, dùng named binding thống nhất giữa expression, `argNames` và signature.

### 5.3. Pointcut riêng hoặc named pointcut tái sử dụng?

Một named pointcut có thể gom điều kiện scope:

```java
@Pointcut("execution(public * com.example.marker.PaymentService.*(..))")
public void paymentOperations() {}
```

Sau đó advice kết hợp:

```java
@Before(
    value = "paymentOperations() && @annotation(marker)",
    argNames = "marker"
)
public void before(TrackCall marker) {
    System.out.println(marker.value());
}
```

Đây là hai fragments đặt trong **cùng aspect class**. Nếu nhiều aspects sử dụng, named pointcut nên đặt trong class ổn định có accessibility thích hợp và tham chiếu theo Java package/class khi cần.

Không bắt buộc tạo named pointcut cho một ví dụ chỉ dùng một lần. Mục tiêu chính là **nội dung pointcut rõ và đủ hẹp**, không tạo thêm lớp abstraction vô ích.

## 6. Chọn loại advice nào để sử dụng marker?

| Nhu cầu | Advice thích hợp | Lưu ý |
| --- | --- | --- |
| Chỉ log trước khi method được gọi | `@Before` | Không cần điều khiển `proceed()`. |
| Quan sát return bình thường | `@AfterReturning` | Bind `returning` nếu cần; không thay reference trả về bằng gán lại parameter. |
| Quan sát exception | `@AfterThrowing` | Chỉ khi method invocation thoát bằng exception phù hợp. |
| Cleanup/tổng hợp sau mọi kết quả sync | `@After` | Có finally semantics; phải hiểu advisor ordering. |
| Đo thời gian bao quanh, có success/error trong cùng lời gọi | `@Around` | Giữ start time/actor ở **local variables**, trả đúng kết quả/exception. |
| Đổi arguments/result hoặc short-circuit | `@Around` | Chỉ khi business policy cho phép, đảm bảo type và side effects. |

Một marker `@TrackCall` **không bắt buộc gắn với `@Around`**. Chọn loại advice ít quyền nhất đủ đáp ứng yêu cầu. Ví dụ demo dùng `@Around` để quan sát **both success/failure paths** và có thể đọc `logResult`, không phải vì annotation chỉ hoạt động với `@Around`.

Nếu có nhiều advices cùng match annotated method, cần xét Spring ordering và `@Order`; **không suy thứ tự từ vị trí method trong `@Aspect` class**.

## 7. Spring thực sự cần những gì để marker có hiệu lực?

Chuỗi điều kiện:

```text
Java annotation type
    @Retention(RUNTIME), @Target(METHOD)
                |
                v
Spring service method mang marker
                |
          Caller dùng bean
                |
                v
          Spring AOP proxy
                |
       pointcut @annotation match
                |
                v
         Spring @Aspect advice
                |
         target method execution
```

Các bước thiết lập bắt buộc:

1. Service là Spring-managed bean (ví dụ `@Bean` hoặc `@Service` được component scan).
2. Aspect class có `@Aspect` và **được đăng ký làm Spring bean** (ví dụ `@Bean` hoặc `@Component`). `@Aspect` đơn lẻ không phải annotation stereotype đăng ký bean.
3. Bật hạ tầng Spring AOP bằng `@EnableAspectJAutoProxy` hoặc Spring Boot auto-configuration phù hợp. Không cần thêm cả hai một cách máy móc.
4. Có dependency AspectJ hỗ trợ Spring pointcut parsing, thông thường `org.aspectj:aspectjweaver`.
5. Pointcut thực sự match method và annotation placement, không bị điều kiện scope loại bỏ.
6. Caller gọi qua reference **Spring proxy**, không tự `new` target hoặc chỉ gọi `this.annotatedMethod()` nội bộ.

`@EnableAspectJAutoProxy` sử dụng **Spring proxy-based AOP**, không tự kích hoạt full AspectJ compile-time/load-time weaving.

## 8. Project Maven Java 17+ chạy được, có success, failure và method không đánh dấu

Mục đích demo:

- Định nghĩa annotation `@TrackCall` có `value` và `logResult`.
- Spring chỉ advise hai methods mang annotation.
- Advice bind annotation instance và lấy metadata.
- Method success có thể in return result khi opt-in.
- Method fail được audit rồi **throw exception gốc**.
- Method không annotate chỉ chạy target, không in dòng audit.
- Cuối cùng assert số advised calls để kiểm tra **pointcut đúng phạm vi**.

Môi trường cố định: **Java 17+, Spring Framework 6.1.14, AspectJ Weaver 1.9.22.1, Maven**. Đây là phiên bản minh họa, không tuyên bố là mới nhất.

### 8.1. Cấu trúc project

```text
spring-aop-marker/
├── pom.xml
└── src/main/java/com/example/marker/
    ├── TrackCall.java
    ├── PaymentService.java
    ├── TrackCallAspect.java
    ├── AppConfig.java
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
    <artifactId>spring-aop-marker</artifactId>
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

`spring-context` cung cấp `ApplicationContext` và các Spring AOP dependencies phù hợp qua dependency tree; `aspectjweaver` hỗ trợ phân tích `@AspectJ` pointcut expressions. Demo không dùng full weaving agent.

### 8.3. `TrackCall.java`

```java
package com.example.marker;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackCall {
    String value();
    boolean logResult() default false;
}
```

Đây là annotation **không tự chứa advice logic**. Hai method annotation elements được đọc khi Spring bind `TrackCall marker` trong aspect.

### 8.4. `PaymentService.java`

```java
package com.example.marker;

public class PaymentService {

    @TrackCall(value = "charge", logResult = true)
    public String charge(String reference) {
        System.out.println("[TARGET] charge " + reference);
        return "charged:" + reference;
    }

    @TrackCall("cancel")
    public String cancel(String reference) {
        System.out.println("[TARGET] cancel " + reference);
        throw new IllegalStateException("already finalized");
    }

    public String status() {
        System.out.println("[TARGET] status");
        return "UP";
    }
}
```

Chỉ `charge` và `cancel` được đánh dấu. `status()` không mang `@TrackCall`. Mỗi method là public, non-final và Spring quản lý target instance qua `@Bean` ở phần tiếp theo.

Trong production, đừng đánh dấu operation chỉ để log **toàn bộ payment parameters**. Demo cố ý chỉ in một test reference và một kết quả chuỗi không nhạy cảm.

### 8.5. `TrackCallAspect.java`

```java
package com.example.marker;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TrackCallAspect {

    private final AtomicInteger advisedCalls = new AtomicInteger();

    @Around(
        value = "execution(public * com.example.marker.PaymentService.*(..))"
              + " && @annotation(marker)",
        argNames = "marker"
    )
    public Object around(ProceedingJoinPoint pjp, TrackCall marker)
            throws Throwable {

        advisedCalls.incrementAndGet();
        String action = marker.value();
        System.out.println("[AUDIT] enter action=" + action);

        try {
            Object result = pjp.proceed();

            if (marker.logResult()) {
                System.out.println("[AUDIT] success action=" + action
                        + " result=" + result);
            } else {
                System.out.println("[AUDIT] success action=" + action);
            }
            return result;
        } catch (Throwable error) {
            System.out.println("[AUDIT] failure action=" + action
                    + " type=" + error.getClass().getSimpleName());
            throw error;
        }
    }

    public int advisedCalls() {
        return advisedCalls.get();
    }
}
```

Các điểm cần kiểm chứng:

- `@annotation(marker)` bind **annotation instance** có trên method đang được intercept.
- `argNames="marker"` cung cấp tên parameter tường minh; `pjp` là leading `ProceedingJoinPoint` không cần liệt kê.
- `pjp.proceed()` được gọi một lần cho mỗi matching invocation trong demo.
- Success branch trả **đúng kết quả** target; failure branch ném lại **đúng exception**.
- `advisedCalls` là shared aggregate counter nên dùng `AtomicInteger`; không có mutable field lưu user hoặc join point của lời gọi gần nhất.
- `logResult` chỉ dùng để minh họa attribute; trong production cần data classification/redaction, tránh ghi toàn bộ result object hoặc `toString()` có side effects.

### 8.6. `AppConfig.java`

```java
package com.example.marker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public PaymentService paymentService() {
        return new PaymentService();
    }

    @Bean
    public TrackCallAspect trackCallAspect() {
        return new TrackCallAspect();
    }
}
```

`@EnableAspectJAutoProxy(proxyTargetClass=true)` bật Spring AOP và dùng CGLIB class-based proxy cho `PaymentService` (không implement business interface). Aspect được đăng ký là một Spring bean qua `@Bean`. Chỉ có annotation type `@TrackCall` trên classpath không tạo proxy nếu không có advisor.

### 8.7. `DemoApp.java`

```java
package com.example.marker;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApp {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            PaymentService payment =
                    context.getBean(PaymentService.class);
            TrackCallAspect aspect =
                    context.getBean(TrackCallAspect.class);

            System.out.println("isProxy=" + AopUtils.isAopProxy(payment));

            System.out.println("[CALLER] charge");
            String charged = payment.charge("P-1");
            System.out.println("[CALLER] result=" + charged);

            System.out.println("[CALLER] cancel");
            try {
                payment.cancel("P-2");
            } catch (IllegalStateException error) {
                System.out.println("[CALLER] caught=" + error.getMessage());
            }

            System.out.println("[CALLER] status");
            System.out.println("[CALLER] health=" + payment.status());
            System.out.println("advisedCalls=" + aspect.advisedCalls());

            if (aspect.advisedCalls() != 2) {
                throw new AssertionError("Expected exactly two advised calls");
            }
        }
    }
}
```

Caller dùng **bean từ Spring context**, không tự khởi tạo `new PaymentService()` bên ngoài container. Phép đếm cuối giúp phát hiện pointcut match quá rộng hoặc không match.

### 8.8. Lệnh chạy và stdout dự kiến

Từ thư mục chứa `pom.xml` với JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.marker.DemoApp
```

Các dòng do chương trình in ra (không tính log khác của Maven/Spring nếu môi trường có):

```text
isProxy=true
[CALLER] charge
[AUDIT] enter action=charge
[TARGET] charge P-1
[AUDIT] success action=charge result=charged:P-1
[CALLER] result=charged:P-1
[CALLER] cancel
[AUDIT] enter action=cancel
[TARGET] cancel P-2
[AUDIT] failure action=cancel type=IllegalStateException
[CALLER] caught=already finalized
[CALLER] status
[TARGET] status
[CALLER] health=UP
advisedCalls=2
```

Giải thích thứ tự:

1. Spring tạo proxy: `isProxy=true`.
2. `charge` mang `@TrackCall` nên aspect chạy trước body: `[AUDIT] enter...`, rồi target in `[TARGET]`.
3. Target trả chuỗi, attribute `logResult=true` khiến aspect in success kèm result; caller nhận cùng chuỗi.
4. `cancel` cũng mang marker, aspect chạy rồi target ném `IllegalStateException`. Advice bắt/ghi event và throw lại, caller bắt cùng exception. `logResult` không liên quan nhánh lỗi.
5. `status` không mang annotation, nên **chỉ target** in log; không có `[AUDIT]` cho method này.
6. `advisedCalls=2` cho thấy marker giới hạn đúng hai invocations.

Đây là **stdout dự kiến từ code đã rà soát tĩnh**, không khẳng định Maven đã được chạy trên môi trường cụ thể. Nếu sửa Spring version, cấu hình proxy, JDK, annotation placement hoặc bổ sung advisors, nên chạy lại integration test.

## 9. Spring proxy boundary: marker đúng vẫn có thể không có advice

### 9.1. Self-invocation

```java
public class PaymentService {
    public String perform() {
        return charge("internal");
    }

    @TrackCall("charge")
    public String charge(String reference) {
        return "charged:" + reference;
    }
}
```

Đây là **fragment cấu hình thay thế**, không copy lên cạnh `PaymentService.java` demo mà không đổi class tương ứng. Khi caller gọi `proxy.perform()` nhưng target tự gọi `this.charge()`, lời gọi nội bộ thường **không đi qua proxy**. Aspect nhắm `charge` có thể **không chạy**, dù annotation được đặt đúng.

Tương tự, `new PaymentService().charge(...)` bên ngoài Spring container không được auto-proxy chỉ vì method có marker.

### 9.2. JDK dynamic proxy và CGLIB

- **JDK proxy** expose interfaces; cần gọi qua proxied interface methods.
- **CGLIB proxy** là subclass proxy, không intercept `final`/`private` method theo cách override thông thường; final class không thể được CGLIB subclass.
- Với annotation nằm trên concrete implementation còn interface không có, cơ chế resolution/matching cần được kiểm thử khi đổi proxy strategy.
- Nếu method chỉ có package visibility hoặc protected, behavior/khả năng gọi qua proxy khác public API; nên thiết kế marker cho **public business operations** khi không có lý do đặc biệt.

Spring Boot và Spring Framework core có thể chọn proxy strategy mặc định khác nhau theo cấu hình. Không gắn lời hứa “luôn JDK” hoặc “luôn CGLIB” vào Java annotation type.

### 9.3. Aspect bean không được đăng ký

Sai:

```java
@Aspect
public class TrackCallAspect {
    @Before("@annotation(com.example.marker.TrackCall)")
    public void before() {}
}
```

Nếu không có `@Component` được scan hoặc `@Bean` registration, Spring có thể **không có instance aspect** để tạo advisor. Annotation trên target method vẫn có, nhưng không có advice.

Giải pháp: bật `@EnableAspectJAutoProxy`/Boot AOP auto-configuration phù hợp; đăng ký aspect dưới Spring bean lifecycle, xác minh đúng `ApplicationContext`.

### 9.4. Kiểm chứng bean/proxy trước khi debug expression

```java
System.out.println(AopUtils.isAopProxy(payment));
System.out.println(AopUtils.isJdkDynamicProxy(payment));
System.out.println(AopUtils.isCglibProxy(payment));
```

Đây là fragment, `payment` là bean reference do Spring trả về. `isAopProxy=true` cho biết proxy tồn tại, **không chứng minh mọi advice mong muốn đều match**: bean có thể đã được proxy bởi transaction hoặc advisor khác. Luôn assert event count của đúng aspect trên đúng method.

## 10. Annotation ở interface, implementation và class

Một marker gắn trực tiếp lên implementation method là trường hợp dễ kiểm chứng nhất với Spring AOP. Khi đặt marker lên interface method, method override hoặc trên class, cần phân biệt:

- Java reflection không tự kế thừa annotation trên interface method sang implementation method; `@Inherited` không giải quyết method annotation.
- `@annotation` nhắm tới method execution; `@within` và `@target` xét type theo semantics tương ứng.
- Với JDK proxy, `MethodSignature.getMethod()` có thể chỉ ra interface method. Nếu phải đọc implementation metadata, dùng `AopUtils.getMostSpecificMethod(...)` và Spring annotation utilities.

Nếu nhiều nơi cùng khai báo marker nhưng khác giá trị, quy định nơi nào có ưu tiên và test với cả JDK/CGLIB. Nếu pointcut không match từ đầu, lookup đặt bên trong advice cũng không thể làm advice chạy.

## 11. Composed annotations và `@AliasFor`

Khi một annotation khác được meta-annotated bằng `@TrackCall`, type gốc phải có `@Target` cho phép `ANNOTATION_TYPE` và cả hai loại annotation phải giữ `RUNTIME`. Spring có thể đọc metadata qua `AnnotatedElementUtils.findMergedAnnotation(...)`; `@AliasFor` diễn đạt cách ghi đè thuộc tính trong mô hình merged annotations.

Tuy nhiên, **Spring merged annotation lookup và AspectJ-style `@annotation` pointcut không phải cùng một thuật toán**. Đừng mở rộng lời hứa của marker sang composed annotations nếu chưa có integration test đo được số lần advice. Khi bắt buộc hỗ trợ nhiều lớp composition, cân nhắc matcher/advisor dựa trên API Spring hoặc một pointcut `execution` đủ hẹp rồi phân giải metadata.

## 12. Giữ phạm vi pointcut có chủ đích

`@annotation(marker)` có thể khớp nhiều bean hơn nhóm nghiệp vụ cần xử lý. Một phạm vi kiểu `execution(public * com.example.payment..*Service.*(..)) && @annotation(marker)` làm ranh giới rõ hơn; nhưng khi đổi package hoặc tên class, integration test phải phát hiện pointcut không còn khớp.

Chọn scope dựa trên contract và phương thức gọi thực tế. Spring AOP chỉ can thiệp method executions đi qua Spring proxy; nó không chặn field access hay constructor chỉ vì annotation có `@Target` tương ứng.

## 13. Marker không phải sự bảo đảm an ninh hoặc nghiệp vụ

`@TrackCall("transfer")` chỉ mô tả ý định áp dụng tracking, không xác thực quyền truy cập, không bảo đảm transaction đã commit và không tự làm thao tác có thể retry an toàn. Nếu advice quyết định short-circuit hoặc gọi `proceed()` nhiều lần, đó là một thay đổi business contract cần kiểm thử tường minh. Không dùng annotation audit làm hàng rào bảo mật duy nhất khi self-invocation hoặc object ngoài Spring có thể bỏ qua proxy.

## 14. Thiết kế attribute nhỏ và có nghĩa

Các element của annotation chỉ có thể là primitive, `String`, `Class`, enum, annotation hoặc mảng các kiểu được phép. Chẳng hạn `String value()` nên là tên sự kiện ổn định, còn `boolean logResult() default false` chỉ bật ghi kết quả theo chính sách dữ liệu đã kiểm soát. Tránh password, token và trạng thái request trong metadata; nếu giá trị là loại thao tác hữu hạn, `enum` thường rõ hơn chuỗi tùy ý.

Thêm một element bắt buộc hoặc đổi default của element cũ là thay đổi API/hành vi đối với các method đã được annotate. Cần quản lý compatibility khi các module dùng chung annotation.

## 15. Kiểm thử hai lớp hành vi

Kiểm thử **metadata** để xác nhận `@Target`, `@Retention(RUNTIME)`, element bắt buộc/default và placement. Sau đó kiểm thử **Spring context** bằng lời gọi qua bean proxy thật, bao gồm:

1. Method có marker nhận advice đúng một lần và đúng giá trị annotation; method không có marker không bị advice.
2. Return value và exception của nghiệp vụ được `@Around` giữ nguyên, kể cả nhánh lỗi.
3. Hai method có giá trị marker khác nhau không dùng nhầm metadata; aspect singleton không lưu request/JoinPoint vào field mutable.
4. Self-invocation và `new Target()` bên ngoài container không được tính là những lần intercept bởi Spring AOP.
5. Nếu hỗ trợ interface, overload, composed annotations hoặc nhiều advisors, có positive/negative tests cho từng trường hợp và từng proxy strategy thực sự dùng.

Thử đúng nơi Spring thực thi chính sách sẽ tránh nhầm kết quả reflection hoặc sự tồn tại của proxy với việc advice cụ thể đã chạy.

## 16. Những lỗi phổ biến

| Triệu chứng | Nguyên nhân thường gặp | Hướng sửa |
| --- | --- | --- |
| Marker đặt trên method nhưng advice không chạy | Quên `@Retention(RUNTIME)`, mặc định `CLASS` | Thêm retention đúng, rebuild. |
| Annotation đặt trên class nhưng dùng `@annotation` | `@annotation` xét method join point, không phải marker trên type | Đặt marker lên method hoặc đổi PCD như `@target`/`@within` khi phù hợp. |
| `@Target` không cho đặt trên method | Annotation chỉ cho `TYPE`/`FIELD` | Thêm `ElementType.METHOD` đúng nhu cầu. |
| Có `@TrackCall` nhưng không có AOP proxy | Chưa bật AOP, chưa đăng ký aspect bean, caller dùng `new` | Kiểm tra `@EnableAspectJAutoProxy`/Boot config và Spring bean. |
| Self-invocation không được advice | `this.method()` bypass proxy | Refactor bean boundary/call qua Spring proxy phù hợp. |
| JDK proxy không intercept concrete-only method | Method không nằm trên proxied interface | Gọi qua interface contract phù hợp hoặc chọn class proxy khi cần. |
| CGLIB không intercept method `final` | CGLIB không override được | Đổi method/class contract hoặc chọn chiến lược khác. |
| `@annotation(marker)` không bind | Sai `argNames`/parameter type hoặc expression | Đồng bộ identifier và advice signature. |
| Method không annotate vẫn bị advice | Pointcut quá rộng, thiếu `&& @annotation(...)` | Giới hạn expression, thêm negative test. |
| Interface annotate nhưng implementation lookup direct trả null | Method-level annotation không tự được kế thừa như class annotation | Quy định placement và dùng Spring annotation utilities khi cần. |
| Composed marker không hoạt động như mong đợi | Nhầm direct `@annotation` matching với merged annotation lookup | Test riêng; dùng `findMergedAnnotation`/matcher thích hợp. |
| Gán result mới vào parameter `@AfterReturning` nhưng caller không thấy | Advice parameter là local reference | Dùng `@Around` nếu phải return replacement. |
| Mỗi caller chỉ gọi một lần nhưng audit lặp | Multiple proxies/advisors hoặc `proceed()` nhiều lần | Chẩn đoán chain, ordering và retry. |
| Log chứa token/PII | `logResult=true` mà không có policy redaction | Thiết kế default-safe, audit allowlist. |
| Aspect shared field bị lẫn user | Singleton aspect giữ per-invocation state | Dùng local variables và thread-safe event sink. |
| Annotation bị xem như authorization | Marker không tự bảo vệ đường gọi | Dùng framework security và test proxy bypass/edge cases. |

## 17. Checklist review trước khi đưa custom marker vào module

- [ ] Tên annotation diễn đạt **ý nghĩa nhất quán**, không ôm quá nhiều trách nhiệm.
- [ ] Có `@Retention(RUNTIME)`.
- [ ] Có `@Target(METHOD)` hoặc target set cụ thể phù hợp policy.
- [ ] Có `@Documented` nếu hữu ích cho API docs.
- [ ] Các elements như `value()`/`logResult()` có defaults an toàn và API rõ.
- [ ] Annotation không lưu credentials hoặc runtime mutable state.
- [ ] Aspect có `@Aspect` **và** được quản lý bởi Spring.
- [ ] Spring AOP auto-proxy đã được bật và `aspectjweaver` có trên classpath.
- [ ] Pointcut `@annotation` được bind đúng type/name, có scope hợp lý.
- [ ] Caller sử dụng Spring proxy, không dựa vào self-invocation.
- [ ] Có positive/negative integration tests cho methods có/không có marker.
- [ ] Đã kiểm tra exception path và return contract của advice.
- [ ] Có quy ước rõ annotation đặt ở interface hay implementation.
- [ ] Nếu dùng composed annotations, đã test `@AliasFor`/merged semantics và pointcut matching riêng.
- [ ] Nếu nhiều aspects cùng match, đã kiểm soát Spring ordering.
- [ ] Không suy marker đủ thay thế business validation, authorization, transaction hoặc idempotency.

### Tài liệu Spring chính thức

- Spring Framework — Declaring a Pointcut (`@annotation` và các PCD): https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Spring Framework — Declaring Advice (binding annotation instance, `argNames`, `ProceedingJoinPoint`): https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — Enabling `@AspectJ` Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — Proxying Mechanisms (JDK/CGLIB, self-invocation): https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — `AopUtils` và target method resolution: https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/aop/support/AopUtils.html
- Spring Framework — `AnnotatedElementUtils`, merged annotations: https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/annotation/AnnotatedElementUtils.html

**Kết luận:** Để có một annotation đánh dấu thực sự dùng được với Spring AOP, hãy tạo annotation type có **`@Target(METHOD)` và `@Retention(RUNTIME)`**, gắn lên Spring-managed service methods, rồi khai báo một **Spring `@Aspect` bean** với pointcut `@annotation` (và binding khi cần đọc metadata). Annotation **chỉ đánh dấu ý định**; interception phụ thuộc Spring AOP proxy, phạm vi pointcut, advisor configuration và đường gọi thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-aspect-annotations-002">Chọn @Target và @Retention phù hợp để đọc lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn `@Target` và `@Retention` phù hợp cho annotation dùng với Spring AOP

## 1. Mục đích

Khi xây dựng custom annotation để kích hoạt một advice của **Spring AOP**, hai meta-annotation Java đặc biệt quan trọng:

- `@Target` quy định annotation **được phép khai báo ở vị trí nào** trong source code, chẳng hạn trên method hoặc class.
- `@Retention` quy định annotation **được giữ lại đến giai đoạn nào**. Advice Spring AOP chạy trong JVM, nên marker mà Spring/AOP phải tìm lúc runtime thông thường cần `RetentionPolicy.RUNTIME`.

Lựa chọn này liên quan trực tiếp đến pointcut Spring như `@annotation`, `@within`, `@target`, đến cách Spring đọc metadata bằng reflection/merged-annotation APIs, và đến ranh giới proxy. Đây là hướng dẫn về **Aspect trong Spring**, không phải bài tổng quan độc lập về AspectJ weaving.

## 2. Chọn `@Target` theo vị trí của marker

| Cách thiết kế | Khai báo phù hợp | Cách advice thường được áp dụng |
| --- | --- | --- |
| Đánh dấu một method nghiệp vụ | `@Target(ElementType.METHOD)` | `@annotation(...)` cho method execution được Spring AOP proxy intercept |
| Đánh dấu toàn bộ lớp service | `@Target(ElementType.TYPE)` | `@within(...)` hoặc `@target(...)` với semantics tương ứng, cần kiểm tra kiểu target/proxy |
| Cho phép trên cả method và class | `@Target({ElementType.METHOD, ElementType.TYPE})` | Thiết kế hai nhánh pointcut rõ ràng; không giả định một `@annotation` sẽ bắt class annotation |
| Đánh dấu annotation khác để tạo composed annotation | `@Target(ElementType.ANNOTATION_TYPE)` hoặc thêm vị trí cần thiết | Metadata APIs của Spring có thể hỗ trợ merged annotations; matching pointcut AspectJ có giới hạn riêng cần test |

`ElementType.PARAMETER` đánh dấu tham số khai báo trong method, không khiến `@annotation` tự bắt một method execution. `ElementType.TYPE_USE` hoặc `TYPE_PARAMETER` cũng không biến Spring AOP thành pointcut bắt type-use. Spring AOP tiêu chuẩn tập trung vào **method-execution join points qua proxy**.

### Annotation dùng trên method

~~~java
package demo;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String event() default "operation";
}
~~~

Dùng trên method được Spring quản lý:

~~~java
@Service
class OrderService {
    @Audited(event = "place-order")
    public String place(String orderId) {
        return "accepted:" + orderId;
    }
}
~~~

Nếu `@Target(TYPE)` mà lại đặt lên method, Java compiler báo lỗi ở declaration site. Nếu `@Target(METHOD)` mà đặt lên class, compiler cũng từ chối; đây là lợi ích của metadata contract rõ ràng.

### Annotation dùng trên class

~~~java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MonitoredService {}
~~~

~~~java
@MonitoredService
@Service
class PaymentService {
    public String pay() { return "paid"; }
}
~~~

Một `@annotation(demo.MonitoredService)` chỉ kiểm tra annotation tại method và không phải lựa chọn phù hợp để lọc class marker. Cần chủ động chọn `@within`/`@target` theo loại target, semantics static/runtime của pointcut và hành vi proxy.

## 3. Chọn `@Retention`: SOURCE, CLASS hay RUNTIME?

| Policy | Có trong bytecode? | Có thể tìm bằng reflection ở runtime? | Phù hợp marker Spring AOP runtime? |
| --- | --- | --- | --- |
| `RetentionPolicy.SOURCE` | Thường bị compiler loại khi tạo class | Không | Không |
| `RetentionPolicy.CLASS` | Có trong class file | Không xuất hiện qua reflection runtime thông thường | Không |
| `RetentionPolicy.RUNTIME` | Có | Có | **Có, khi pointcut/proxy tương ứng match** |

Nếu không khai báo `@Retention`, mặc định của Java là `RetentionPolicy.CLASS`. Vì vậy marker không định nghĩa retention rõ ràng thường dẫn đến hiện tượng “annotation nhìn thấy trong source nhưng advice không chạy”. Để Spring đọc annotation từ `AnnotatedElement` trong JVM, cách chọn thông thường là `RUNTIME`.

`RUNTIME` là **điều kiện cần**, không phải điều kiện đủ. Advice còn phụ thuộc:

1. Có bean `@Aspect` được Spring nhận diện và hạ tầng `@EnableAspectJAutoProxy`/Boot AOP được thiết lập.
2. Pointcut đúng loại annotation và đúng method/class được kiểm tra.
3. Method call đi qua Spring AOP proxy; `this.someMethod()` bên trong target bỏ qua proxy.
4. JDK dynamic proxy và CGLIB class-based proxy có khác biệt trong cấu trúc method/interface và những method intercept được.
5. Chỉ định pointcut phù hợp nếu annotation nằm trên interface hay implementation; kiểm thử proxy strategy thực tế.

## 4. Ví dụ Spring chạy được, Java 17 + Maven

Cấu trúc project:

~~~text
spring-aop-marker-meta/
  pom.xml
  src/main/java/demo/Audited.java
  src/main/java/demo/OrderService.java
  src/main/java/demo/AuditAspect.java
  src/main/java/demo/Application.java
~~~

**pom.xml**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-aop-marker-meta</artifactId>
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

**Audited.java**

~~~java
package demo;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String event() default "operation";
}
~~~

`@Documented` cho phép JavaDoc đưa marker vào tài liệu API; nó không làm Spring AOP match mạnh hơn.

**OrderService.java**

~~~java
package demo;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    @Audited(event = "placed")
    public String place(String id) {
        return "accepted:" + id;
    }

    public String status() {
        return "ready";
    }
}
~~~

**AuditAspect.java**

~~~java
package demo;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    @Before("@annotation(audited)")
    public void audit(Audited audited) {
        System.out.println("audit:" + audited.event());
    }
}
~~~

Aspect dùng `@annotation(audited)` để bind marker thực tế vào tham số `audited`. Có thể dùng `argNames` khi compilation không giữ thông tin tên tham số như mong muốn; trong ví dụ một annotation-typed argument này Spring AOP/AspectJ thường suy ra binding, nhưng đối với project nhiều tham số nên khai báo rõ.

**Application.java**

~~~java
package demo;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan("demo")
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class Application {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Application.class)) {
            OrderService service = context.getBean(OrderService.class);
            System.out.println("proxy=" + AopUtils.isAopProxy(service));
            System.out.println(service.place("o-7"));
            System.out.println(service.status());
        }
    }
}
~~~

Chạy:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.Application
~~~

Kết quả mong đợi từ `System.out` của ứng dụng:

~~~text
proxy=true
audit:placed
accepted:o-7
ready
~~~

`status()` không được đánh dấu `@Audited` nên advice này không áp dụng. `place()` đi qua Spring proxy, marker runtime có thể match và giá trị annotation được bind. Đây là **expected output kiểm tra tĩnh**, không phải lời khẳng định đã thực thi Maven tại máy hiện tại.

## 5. Nếu marker khai báo trên interface thì sao?

Xem hai khai báo:

~~~java
public interface ReportApi {
    @Audited(event = "generate")
    String generate();
}
~~~

~~~java
@Service
class ReportService implements ReportApi {
    public String generate() { return "done"; }
}
~~~

Java annotation trên interface method **không tự động trở thành annotation của implementation method**. Java `@Inherited` chỉ có hiệu lực giới hạn trên class-level inheritance, không kế thừa method annotation hay interface annotation theo cách Java reflection thông thường mong đợi.

Trong Spring AOP, framework và matcher có thể chọn method phản ánh trên interface/proxy hoặc method implementation tùy advisor/pointcut. Không giả định annotation chỉ khai trên interface sẽ match giống class-based proxy. Với policy quan trọng, thường đặt annotation nhất quán ở implementation method, hoặc xây một advisor/custom matcher sử dụng Spring metadata APIs có kiểm thử cho JDK/CGLIB.

Kiểm thử nên bao gồm:

- Interface method có marker, implementation không có.
- Implementation có marker, interface không có.
- Annotation trên class và trên method, với pointcuts riêng.
- JDK dynamic proxy so với CGLIB class proxy.
- Invocation qua Spring bean proxy so với gọi `this.generate()` nội bộ.

## 6. Meta-annotation trong Spring và giới hạn khớp pointcut

Spring cung cấp `AnnotatedElementUtils`, `MergedAnnotations`, `AnnotationUtils` cùng `@AliasFor` để đọc các annotation tổng hợp trong nhiều ngữ cảnh của framework. Nhưng sự tồn tại của annotation tổng hợp không đồng nghĩa `@annotation(SomeMarker)` của AspectJ-style pointcut tự mở rộng sang mọi annotation được meta-annotated.

Ví dụ:

~~~java
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Audited(event = "trace")
public @interface Traced {}
~~~

Mẫu trên chỉ hợp lệ khi `@Audited` cho phép `ANNOTATION_TYPE` trong `@Target`. Nếu bản `Audited` ở demo chỉ cho `METHOD`, compiler từ chối `@Audited` trên declaration `@Traced`. Điều này cho thấy cần thiết kế `@Target` theo kế hoạch sử dụng ngay từ đầu.

Một annotation tổng hợp `@Traced` có thể được Spring metadata inspection nhìn thấy qua merged lookup, trong khi AspectJ `@annotation(demo.Audited)` vẫn cần được kiểm chứng bằng integration tests. Với meta-annotation conventions phức tạp, cân nhắc advisor `StaticMethodMatcherPointcut` riêng dùng Spring `MergedAnnotations` và test matching tường minh.

## 7. Scope proxy quan trọng hơn annotation declaration

Spring AOP không weave trực tiếp vào mọi method call của JVM. Marker đúng `RUNTIME` mà không thấy advice vẫn có thể là một trong các lý do:

~~~java
@Service
class CheckoutService {
    public void outer() {
        inner(); // self-invocation: không gọi lại qua Spring AOP proxy
    }

    @Audited
    public void inner() {
        // ...
    }
}
~~~

Một lời gọi từ bên ngoài vào `outer()` (không có marker) rồi tự gọi `inner()` sẽ không kích hoạt `@Before("@annotation(...")` cho lần inner call đó. Giải quyết bằng cấu trúc service phù hợp, tách bean và gọi qua dependency Spring proxy hoặc đưa concern lên entry point phù hợp, thay vì thay đổi retention.

Ngoài ra:

- Class-based proxy thông thường không advice được `final`/private method.
- JDK proxy chỉ exposes interface contract ở reference của caller.
- Target tạo bằng `new` không qua container sẽ không tự được Spring advice.
- Khi dùng `@Async` hoặc `@Transactional`, nhiều advisors có thứ tự và ranh giới khác nhau; kiểm tra method execution qua đúng proxy.
- Nếu annotation được dùng làm phân quyền hoặc chính sách bắt buộc, không coi marker + pointcut là lớp bảo vệ duy nhất: self-invocation và non-proxied calls có thể bỏ qua advice.

## 8. Kiểm thử `@Target` và `@Retention` bằng Java reflection

Bài test nhỏ này hoạt động cho `Audited` trong project demo:

~~~java
package demo;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

public class MetadataCheck {
    public static void main(String[] args) throws Exception {
        Class<Audited> type = Audited.class;
        Target target = type.getAnnotation(Target.class);
        Retention retention = type.getAnnotation(Retention.class);
        System.out.println("targetMethod=" +
            Arrays.asList(target.value()).contains(ElementType.METHOD));
        System.out.println("runtime=" +
            (retention.value() == RetentionPolicy.RUNTIME));
        System.out.println("markerPresent=" +
            OrderService.class.getMethod("place", String.class).isAnnotationPresent(Audited.class));
    }
}
~~~

Chạy:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.MetadataCheck
~~~

Output dự kiến:

~~~text
targetMethod=true
runtime=true
markerPresent=true
~~~

Khác với kiểm tra pointcut, reflection test này chỉ chứng minh marker tồn tại trên method cụ thể và giữ được đến runtime. Nó **không thay** integration test xác nhận advice thật sự match proxy.

## 9. Các lỗi thường gặp

**Có `@Target(METHOD)` nhưng annotation đặt ở class.** Java compiler báo annotation không áp dụng được tại vị trí này. Sửa declaration target cho đúng dự định, sau đó chỉnh lại pointcut tương ứng, tránh thêm vị trí vô tội vạ.

**Không khai báo `@Retention`, nên `@annotation` không chạy.** Mặc định retention là CLASS. Đối với Spring runtime advice, thường dùng `RUNTIME`.

**Khai báo `@Retention(RUNTIME)` nhưng advice không chạy.** Kiểm tra Spring AOP activation, bean managed, pointcut, interface/implementation annotations, final/private methods và self-invocation.

**Thêm `@Inherited` mong method marker lan sang implementation.** Java `@Inherited` không giải quyết method/interface metadata theo cách đó; kiểm thử target thật của Spring proxy.

**Composed annotation hiện trong `AnnotatedElementUtils` nhưng không match `@annotation`.** Spring merged metadata lookup và AspectJ pointcut matching không cùng thuật toán; cần integration test và có thể custom matcher.

**Đặt marker trên field, constructor hoặc parameter và mong Spring AOP advice bắt tại đó.** Spring AOP là proxy-based method execution. Cần thiết kế method-level annotation hoặc công cụ chuyên biệt nếu yêu cầu thật sự khác.

## 10. Checklist sử dụng tại dự án Spring

1. Viết rõ marker dành cho **method**, **type**, hay cả hai và dùng `@Target` hẹp, có chủ ý.
2. Chọn `@Retention(RUNTIME)` cho metadata mà advice runtime cần đọc.
3. Với composed annotation, thêm `ANNOTATION_TYPE` nếu declaration cần làm meta-annotation; kiểm thử Spring metadata API và AspectJ pointcut riêng.
4. Chạy integration test với Spring proxy trên đúng kiểu bean, kiểm tra interface/implementation, JDK/CGLIB và self-invocation.
5. Tránh trộn business policy phức tạp vào annotation; annotation chỉ nên mang metadata cấu hình rõ nghĩa cho concern Spring AOP.
6. Lưu `@Documented` nếu cần API docs, không coi nó là điều kiện matching.
7. Rà soát các advisor khác (`@Transactional`, `@Cacheable`, `@Async`) và kiểm tra thứ tự áp dụng phù hợp.

## Tài liệu đối chiếu

- Spring Framework Reference — Declaring a pointcut: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Spring Framework Reference — Proxying mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework Reference — Advice with annotation-style aspects: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Java SE 17 API: `java.lang.annotation.Target`, `Retention`, `RetentionPolicy`, `ElementType`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/package-summary.html

**Kết luận:** Đối với custom annotation phục vụ Spring AOP ở runtime, thường dùng `@Retention(RUNTIME)` và `@Target(METHOD)` nếu đánh dấu từng method, hoặc `TYPE` nếu đánh dấu cả class. Hãy kiểm tra Spring proxy, method metadata và pointcut cùng nhau; chỉ có meta-annotation đúng không bảo đảm advice sẽ chạy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-aspect-annotations-003">Ràng buộc giá trị annotation vào advice</a>

<details>
<summary>Xem chi tiết</summary>

# Ràng buộc giá trị annotation vào advice trong Spring AOP

## 1. Vấn đề cần giải quyết

Trong Spring AOP, một annotation tùy chỉnh có thể vừa đánh dấu phương thức cần được chặn, vừa cung cấp **cấu hình riêng cho từng phương thức**. Ví dụ, với các phương thức trong Spring service, ta muốn một aspect ghi log theo tên nghiệp vụ, độ ưu tiên hoặc quy tắc che thông tin do annotation khai báo:

~~~java
@TrackOperation(action = "order.create", sensitive = false)
public String create(String orderId) {
    return "created: " + orderId;
}
~~~

Một advice nên nhận trực tiếp đối tượng TrackOperation để gọi action() và sensitive(). Nhờ vậy, không cần viết một aspect khác cho từng action hay hard-code tên method trong advice.

**Phạm vi:** Cách Spring diễn giải biểu thức pointcut kiểu @AspectJ, liên kết đối tượng annotation với tham số advice, và các ranh giới khi Spring gọi bean qua AOP proxy. Kiến thức Java annotation/AOP chung chỉ được dùng ở mức cần thiết để triển khai chính xác trong Spring.

## 2. Hai nghĩa của @annotation: chọn phương thức và ràng buộc giá trị

Spring AOP hỗ trợ pointcut designator @annotation để khớp phương thức được gọi qua Spring proxy mà phương thức đó mang annotation chỉ định. Có hai cách thể hiện khác nhau:

**Chỉ lọc bằng tên kiểu annotation:**

~~~java
@Before("@annotation(demo.TrackOperation)")
public void beforeAnyTrackedMethod() {
    // Chạy khi phương thức được advice khớp với TrackOperation.
    // Không nhận đối tượng annotation trong tham số advice.
}
~~~

**Ràng buộc annotation vào tham số advice:**

~~~java
@Before(value = "@annotation(track)", argNames = "track")
public void beforeTrackedMethod(TrackOperation track) {
    System.out.println(track.action());
    System.out.println(track.sensitive());
}
~~~

Trong trường hợp thứ hai:

1. track trong chuỗi "@annotation(track)" là **tên biến binding**, không phải class name.
2. TrackOperation là kiểu Java thực tế của tham số advice. Spring/AspectJ expression xác định kiểu cần khớp và truyền annotation đã match vào tham số ấy.
3. argNames = "track" khai báo rõ tên biến trong biểu thức trùng với tham số advice; điều này giúp hoạt động ổn định khi biên dịch không giữ tên tham số Java.
4. action() và sensitive() lấy giá trị **khai báo tại method được gọi**, không phải giá trị tự đọc từ argument nghiệp vụ của method đó.
5. Các attribute annotation được khai báo bằng giá trị hợp lệ tại thời điểm biên dịch; advice nhận cấu hình metadata đó vào lúc Spring chặn lời gọi tương ứng.

Không dùng biểu thức "@annotation(demo.TrackOperation)" mà kỳ vọng Spring tự truyền TrackOperation vào một tham số không có binding. Muốn nhận object, cần dùng dạng tên biến và một tham số tương ứng.

## 3. Định nghĩa custom annotation phù hợp cho Spring proxy

~~~java
package demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrackOperation {
    String action();
    boolean sensitive() default false;
}
~~~

Trong ứng dụng, annotation ở trên đặt trên phương thức được Spring AOP proxy chặn:

~~~java
@Service
public class OrderService {
    @TrackOperation(action = "order.create")
    public String create(String id) {
        return "created: " + id;
    }

    @TrackOperation(action = "order.cancel", sensitive = true)
    public void cancel(String id) {
        // Xử lý nghiệp vụ
    }
}
~~~

- RetentionPolicy.RUNTIME là điều kiện quan trọng để annotation hiện diện tại runtime cho Spring AOP và các cơ chế reflection/annotation resolution.
- Target(METHOD) khẳng định bài toán là annotation ở **phương thức**. Nếu annotation đặt lên class, cần cân nhắc designator @within hoặc @target theo mục tiêu thay vì chờ @annotation match class.
- Trường action() bắt buộc tại điểm sử dụng; sensitive() có default nên có thể bỏ qua.
- Spring chỉ áp dụng advice cho các invocation **đi qua bean proxy** được cấu hình. Đánh dấu method không làm cho mọi lời gọi Java trực tiếp hoặc self-invocation tự động được chặn.

## 4. Ví dụ chạy được: binding TrackOperation trong @Around

Ví dụ dưới đây dùng **Java 17, Spring Framework 6.1.15 và Maven**. Nó cho thấy chính giá trị annotation của phương thức create/cancel được truyền vào một advice duy nhất, còn phương thức preview không có annotation sẽ không bị chặn.

Cấu trúc thư mục:

~~~text
spring-annotation-binding/
├── pom.xml
└── src/main/java/demo/
    ├── Application.java
    ├── TrackOperation.java
    ├── OrderService.java
    └── TrackingAspect.java
~~~

**pom.xml**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-annotation-binding</artifactId>
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
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>17</release>
                    <parameters>true</parameters>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>demo.Application</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/demo/TrackOperation.java**

~~~java
package demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrackOperation {
    String action();
    boolean sensitive() default false;
}
~~~

**src/main/java/demo/OrderService.java**

~~~java
package demo;

import org.springframework.stereotype.Service;

@Service
public class OrderService {

    @TrackOperation(action = "order.create")
    public String create(String orderId) {
        return "created: " + orderId;
    }

    public String preview(String orderId) {
        return "untracked: " + orderId;
    }

    @TrackOperation(action = "order.cancel", sensitive = true)
    public String cancel(String orderId) {
        throw new IllegalStateException("cancelled");
    }
}
~~~

**src/main/java/demo/TrackingAspect.java**

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TrackingAspect {

    @Around(value = "@annotation(track)", argNames = "track")
    public Object around(ProceedingJoinPoint joinPoint, TrackOperation track)
            throws Throwable {
        // Spring bind annotation của đúng method vào track.
        String action = track.action();
        boolean sensitive = track.sensitive();

        System.out.println("trace.enter: " + action
                + ", sensitive=" + sensitive);

        try {
            Object result = joinPoint.proceed();
            System.out.println("trace.success: " + action);
            return result;
        } catch (Throwable error) {
            System.out.println("trace.error: " + action
                    + ", " + error.getClass().getSimpleName());
            throw error;
        }
    }
}
~~~

**src/main/java/demo/Application.java**

~~~java
package demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan("demo")
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class Application {

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(Application.class)) {
            OrderService service = context.getBean(OrderService.class);

            System.out.println(service.create("O-42"));
            System.out.println(service.preview("O-42"));

            try {
                service.cancel("O-42");
            } catch (IllegalStateException error) {
                System.out.println("caught: " + error.getMessage());
            }
        }
    }
}
~~~

Tại thư mục có pom.xml, chạy:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do chương trình tự in theo thứ tự:

~~~text
trace.enter: order.create, sensitive=false
trace.success: order.create
created: O-42
untracked: O-42
trace.enter: order.cancel, sensitive=true
trace.error: order.cancel, IllegalStateException
caught: cancelled
~~~

Lệnh Maven có thể in thêm thông báo do môi trường Maven hoặc Spring Logging; các dòng chương trình nêu trên là kết quả logic dự kiến.

Giải thích:

- @EnableAspectJAutoProxy bật xử lý proxy-based @AspectJ của Spring. aspectjweaver cung cấp các API cần thiết để Spring phân tích AspectJ pointcut expression; ví dụ không dùng compile-time weaving.
- @ComponentScan đăng ký OrderService và TrackingAspect trong Spring context. Lời gọi context.getBean(...) lấy đối tượng có thể đã được Spring proxy.
- create và cancel có cùng loại annotation nhưng **giá trị action và sensitive khác nhau**. Tại mỗi join point, Spring truyền đối tượng TrackOperation tương ứng vào track.
- preview không mang TrackOperation nên không có log trace.
- Advice gọi proceed() **một lần** và trả nguyên kết quả, hoặc ném lại Throwable. Việc log lỗi không biến lỗi ứng dụng thành thành công.
- Tham số ProceedingJoinPoint đứng đầu signature advice được **bỏ qua trong chuỗi argNames** theo quy tắc binding của Spring. Vì thế argNames chỉ ghi "track".
- Compiler được cấu hình giữ parameter names, nhưng ví dụ vẫn dùng argNames tường minh để thể hiện chính xác mối liên hệ với pointcut.

## 5. Cách Spring ánh xạ tên binding vào tham số advice

Cấu trúc nguyên tắc:

~~~text
Pointcut expression:  @annotation(track)
                                  │
                                  │ bind object của annotation được match
                                  ▼
Advice parameter:     TrackOperation track
                                  │
                                  ├── track.action()
                                  └── track.sensitive()
~~~

Các tên binding thuộc **không gian tên của pointcut/advice**. Có thể chọn bất kỳ tên Java hợp lệ nào, miễn khớp nhất quán. Ví dụ muốn dùng tên policy:

~~~java
@Around(value = "@annotation(policy)", argNames = "policy")
public Object intercept(ProceedingJoinPoint pjp, TrackOperation policy)
        throws Throwable {
    System.out.println(policy.action());
    return pjp.proceed();
}
~~~

Về cách xác định tên tham số, Spring có thể dùng thông tin argNames, tên parameter lưu trong bytecode khi biên dịch với javac -parameters, hoặc cơ chế suy luận tên từ biểu thức pointcut theo tài liệu Spring. Nếu binding phức tạp, nên khai báo argNames rõ ràng để tránh lỗi "ambiguous binding", "argument names not available" hoặc binding vào sai tham số. Khi khai báo argNames và advice nhận JoinPoint/ProceedingJoinPoint ở tham số đầu tiên, thông thường không cần liệt kê tham số join point đó.

**Lỗi thường gặp: sai tên binding**

~~~java
// Sai: biến "track" trong pointcut không khớp argNames="label".
@Before(value = "@annotation(track)", argNames = "label")
public void wrong(TrackOperation label) {
    // Có thể lỗi xác định/bind tham số khi tạo advisor.
}
~~~

Sửa: dùng cùng một tên ở pointcut expression, argNames và signature Java.

### Ràng buộc nhiều đối tượng cùng lúc

Spring cho phép kết hợp annotation binding và method-argument binding. Ví dụ minh họa dưới đây giả định method phù hợp có đối số đầu là String:

~~~java
@Before(
    value = "@annotation(track) && args(orderId,..)",
    argNames = "track,orderId"
)
public void beforeWithArgument(TrackOperation track, String orderId) {
    System.out.println("action=" + track.action()
            + ", orderId=" + orderId);
}
~~~

- @annotation(track): lấy metadata của **phương thức**.
- args(orderId,..): match và bind **giá trị tham số nghiệp vụ đầu tiên** của lời gọi, theo kiểu dữ liệu được khai báo ở advice; .. cho phép thêm các đối số phía sau.
- Nếu method không có đối số đầu kiểu phù hợp thì điểm thực thi không match expression kết hợp.
- Không đọc orderId để thay thế track.action(): đây là hai nguồn dữ liệu khác nhau. Với thông tin nhạy cảm, cân nhắc track.sensitive() trước khi đưa arguments vào log.

Binding cũng có thể kết hợp với target(...) hoặc kiểu returned value/exception trong các advice tương ứng; mỗi biến bổ sung cần có tham số/điều kiện khớp đúng.

## 6. Tách @Pointcut để tái sử dụng annotation binding

Khi nhiều advice cùng dùng một điều kiện, có thể đặt tên pointcut. Đối tượng annotation **vẫn phải được khai báo như tham số của method @Pointcut**, rồi truyền tới advice gọi pointcut đó.

~~~java
@Aspect
@Component
public class ReusableTrackingAspect {

    @Pointcut(value = "@annotation(track)", argNames = "track")
    public void methodsWithTracking(TrackOperation track) {
        // Method này chỉ khai báo pointcut, không chứa nghiệp vụ.
    }

    @Before(value = "methodsWithTracking(track)", argNames = "track")
    public void before(TrackOperation track) {
        System.out.println("start: " + track.action());
    }

    @AfterReturning(
        value = "methodsWithTracking(track)",
        argNames = "track"
    )
    public void afterSuccess(TrackOperation track) {
        System.out.println("returned: " + track.action());
    }
}
~~~

Đoạn trên là **biến thể độc lập để minh họa**, không thêm đồng thời vào ví dụ ở mục 4 nếu không muốn log từ nhiều aspect chồng lên nhau.

Một điểm quan trọng: @AfterReturning chạy khi Java method trả bình thường; nó không tự đảm bảo nghiệp vụ bất đồng bộ chứa trong Future/Publisher đã hoàn tất. Nếu method trả đối tượng xử lý async, cần thêm cách quan sát phù hợp với kiểu trả về. Mục đích ở đây là minh họa việc đối tượng TrackOperation đã ràng buộc được chuyển qua named pointcut tới từng advice.

Có thể kết hợp vùng package/service để hạn chế số join point:

~~~java
@Pointcut(
    value = "within(demo..*) && @annotation(track)",
    argNames = "track"
)
public void trackedInDemo(TrackOperation track) {}
~~~

within(...) giới hạn nơi phương thức được khai báo/chạy theo biểu thức kiểu, còn @annotation(track) vừa lọc method vừa bind annotation. Việc thu hẹp phạm vi hữu ích nếu nhiều module sử dụng trùng annotation hoặc ứng dụng có nhiều bean.

## 7. So sánh các designator liên quan trong Spring

| Designator | Đối tượng được xét | Khi dùng |
| --- | --- | --- |
| @annotation(track) | Annotation gắn với method thực thi mà Spring AOP match | Bind cấu hình cho từng method vào advice |
| @annotation(demo.TrackOperation) | Kiểu annotation trên method, không khai báo biến binding | Chỉ cần lọc method |
| @within(trackType) | Annotation trên kiểu khai báo method được chặn | Áp dụng cấu hình theo class/kiểu |
| @target(trackType) | Annotation trên class runtime của đối tượng target | Áp dụng cấu hình theo target |
| @args(marker) | Annotation trên **kiểu runtime của argument** | Ràng buộc annotation kiểu của đối số, không phải annotation method |
| args(orderId,..) | Giá trị đối số khi gọi method | Bind dữ liệu input nghiệp vụ, có thể kết hợp @annotation |

Mỗi designator có quy tắc match khác nhau. Nếu annotation đặt ở class mà advice chỉ có @annotation(methodTag), không nên kỳ vọng annotation class tự nhiên trở thành methodTag. Tương tự, @args không lấy annotation trực tiếp từ method.

Một nhu cầu phổ biến: cấu hình class là mặc định, còn annotation ở method ưu tiên ghi đè. **Việc kết hợp và quyết định ưu tiên này là logic Spring aspect do ứng dụng tự thiết kế**, không tự xuất hiện chỉ vì đã viết @annotation. Có thể cân nhắc annotation lookup bằng tiện ích Spring (AopUtils, AnnotatedElementUtils) khi thật sự cần kết hợp annotation metadata, sau khi xác định join point và semantics mong muốn.

## 8. Ranh giới annotation metadata với Spring AOP proxy

### 8.1. Bean phải đi qua Spring proxy

~~~java
@Service
public class OrderFacade {
    @Autowired
    private OrderService service;

    public void externalCall() {
        service.create("O-1"); // Qua bean proxy; có thể chạy advice.
    }
}
~~~

Ngược lại, bên trong cùng một bean:

~~~java
@Service
public class InternalService {
    public void outer() {
        inner(); // this.inner(): lời gọi nội bộ bypass proxy.
    }

    @TrackOperation(action = "internal.inner")
    public void inner() {}
}
~~~

Nếu chỉ gọi outer() từ bên ngoài, inner() được gọi trực tiếp trên this nên advice target inner() thường không chạy trong Spring proxy mode. Hãy đặt ranh giới can thiệp ở lời gọi qua Spring-managed bean thích hợp hoặc cấu trúc lại service. Bản thân annotation không khắc phục self-invocation.

### 8.2. Vị trí annotation ở interface và implementation

Spring có thể tạo **JDK dynamic proxy** cho bean có interface hoặc **class-based proxy**. Kiểu proxy và phương thức thực tế được resolve có thể ảnh hưởng việc annotation có được nhìn thấy/match khi cấu hình đặt annotation ở interface, implementation, hoặc method được override.

Quy ước dễ kiểm tra là đặt annotation **trực tiếp trên method thực thi của Spring service**, như OrderService trong ví dụ, và viết integration test gọi qua đúng bean proxy mà production sử dụng. Nếu project cần annotation trên interface, hãy kiểm tra cụ thể hành vi khi đổi proxyTargetClass, inheritance và bridge methods; đừng giả định method annotation được Java kế thừa tự động. @Inherited của Java chỉ dành cho kế thừa annotation ở type, không giải quyết chung method annotation trên interface/override.

### 8.3. Phạm vi method có thể được advice

Spring AOP proxy hỗ trợ method execution join point thông qua bean proxy; nó không tự chặn constructor, field access, lời gọi đến đối tượng được new thủ công, hoặc bất kỳ gọi nào không qua proxy. Final class/final method có hạn chế đáng kể đối với class-based proxy. Các visibility và cấu hình proxy cụ thể cũng quyết định khả năng advice; không dựa vào method private như một join point thường gặp.

Với method overloaded cùng tên, metadata annotation của **đúng method** cần được match; không tự lấy annotation của method chỉ dựa trên tên method.

### 8.4. Retention và composed annotation

Thiếu @Retention(RUNTIME) có thể khiến advice không thấy annotation tại runtime. Với custom composed/meta-annotation (annotation A gắn trên annotation B), Spring có hệ thống merged annotation riêng phục vụ nhiều feature như @Transactional. Tuy nhiên, không mặc định suy rằng mọi tổ hợp meta-annotation sẽ bind bằng @annotation(track) giống annotation gắn trực tiếp trong mọi cấu hình/version. Khi cần semantics composed annotation, xác định rõ cơ chế matching và kiểm thử; nếu chỉ cần binding trực tiếp, khai báo annotation ngay trên method là đơn giản nhất.

## 9. Khi nào nên dùng @Before, @Around, @AfterReturning, @AfterThrowing?

Chỉ cần **đọc cấu hình từ annotation rồi log/kiểm tra điều kiện trước khi chạy**: @Before có parameter binding thường là đủ.

Cần đo thời gian đồng bộ, tự điều khiển proceed(), ghi nhận kết quả, hoặc chặn lời gọi theo chính sách được annotation mô tả: dùng @Around, nhưng phải xác định rõ liệu short-circuit, đổi arguments, đổi return value có nằm trong hợp đồng dịch vụ không. Advice @Around là thành phần mạnh, dễ gây thay đổi nghiệp vụ nếu quên gọi proceed() hoặc gọi nhiều lần.

Cần nhận annotation cùng giá trị trả về khi **method trả bình thường**:

~~~java
@AfterReturning(
    value = "@annotation(track)",
    returning = "result",
    argNames = "track,result"
)
public void afterReturn(TrackOperation track, Object result) {
    System.out.println(track.action() + ": " + result);
}
~~~

returning="result" bind **giá trị trả về của method**, khác với @annotation(track) bind **metadata annotation**. Nếu dùng tham số returning kiểu cụ thể (ví dụ String thay Object), Spring có thể giới hạn những method join point có giá trị trả về phù hợp với kiểu ấy.

Cần nhận annotation và lỗi được ném tại method invocation:

~~~java
@AfterThrowing(
    value = "@annotation(track)",
    throwing = "error",
    argNames = "track,error"
)
public void afterError(TrackOperation track, Throwable error) {
    System.out.println(track.action() + ": " + error.getClass().getSimpleName());
}
~~~

throwing="error" bind exception tương ứng, không thay cho annotation binding. Nếu method trả Mono/Future bình thường nhưng đối tượng async phát lỗi về sau, advice @AfterThrowing này không tự quan sát tín hiệu async. Cách xử lý phụ thuộc kiểu API thực tế, không coi annotation binding là cơ chế quan sát mọi vòng đời.

**Không đặt nhiều advice có side effect trùng nhau** chỉ để nhận cùng annotation nếu không cần. Nếu có nhiều aspects liên quan (ví dụ security/metrics/transaction), thứ tự advisor có thể làm thay đổi ngữ nghĩa trước/sau; dùng @Order và kiểm thử khi thứ tự thực sự quan trọng.

## 10. Thiết kế giá trị annotation: metadata của chính sách, không phải state toàn cục

Annotation phù hợp để lưu metadata tĩnh tại method, ví dụ:

~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditPolicy {
    String event();
    Level level() default Level.INFO;
    boolean includeResult() default false;

    enum Level { INFO, WARN }
}
~~~

Đoạn khai báo này độc lập với ví dụ TrackOperation ở trên. Spring advice có thể bind AuditPolicy để chọn mức log hoặc loại sự kiện:

~~~java
@Before(value = "@annotation(policy)", argNames = "policy")
public void onAuditPolicy(AuditPolicy policy) {
    System.out.println(policy.event() + " at " + policy.level());
}
~~~

Các giá trị action/event/level là cấu hình tại method được match. Chúng không thay đổi riêng theo từng invocation dựa vào request. Khi cần dữ liệu runtime (userId, orderId, tenant, correlation id), cần lấy từ đối số method, security context hoặc ngữ cảnh xử lý tương ứng; có thể kết hợp với annotation metadata, nhưng không nhét dữ liệu request vào một field mutable trong singleton @Aspect.

Ví dụ về chia sẻ trạng thái dễ sai:

~~~java
@Aspect
@Component
class WrongAspect {
    private String currentAction; // Sai: field mutable dùng chung giữa requests.

    @Before(value = "@annotation(track)", argNames = "track")
    public void before(TrackOperation track) {
        currentAction = track.action();
    }
}
~~~

Với nhiều thread xử lý song song, currentAction bị ghi đè dẫn đến log/correlation sai. Thay bằng biến cục bộ trong mỗi advice invocation; nếu cần shared counters, dùng cơ chế concurrency-safe phù hợp. Đối tượng annotation được bind là metadata của method; lưu nó vào field currentAction để "ghi nhớ" request hiện tại là không cần thiết.

Nếu advice thực thi chính sách thực tế (ủy quyền, kiểm soát dữ liệu), không coi annotation là bằng chứng rằng người dùng được cấp quyền; kiểm tra danh tính/quyền qua thành phần Spring Security hay policy service đã được cấu hình. Annotation chỉ mô tả quy tắc cần áp dụng.

## 11. Chẩn đoán lỗi binding trong ứng dụng Spring

| Triệu chứng | Nguyên nhân có thể | Cách xác minh và sửa |
| --- | --- | --- |
| Advice không chạy dù method có annotation | Bean không qua proxy, self-invocation, @EnableAspectJAutoProxy chưa hoạt động, annotation đặt ở vị trí khác | Lấy bean từ ApplicationContext, kiểm tra loại proxy và method thực thi |
| Spring báo lỗi không xác định được tham số binding | Tên trong pointcut/argNames/signature khác nhau, thiếu -parameters khi cần | Thêm argNames tường minh, kiểm tra tham số nhận đúng loại annotation |
| Annotation trong advice là null hoặc không match | Annotation không có RUNTIME, method/class/interface annotation resolution khác giả định | Dùng @Retention(RUNTIME), đặt trực tiếp trên method thử nghiệm, kiểm thử proxy thật |
| Dùng @annotation(demo.TrackOperation) nhưng muốn đọc action() | Biểu thức chỉ lọc type, không có biến binding | Dùng @annotation(track) với tham số TrackOperation track |
| @Around chạy nhưng nghiệp vụ không chạy | Quên gọi proceed() | Gọi proceed() đúng một lần trừ khi cố ý short-circuit theo hợp đồng |
| Method thực thi nhưng kiểu trả về sai | Advice trả object khác kiểu mong đợi hoặc không trả kết quả proceed() | Giữ return contract, không tự biến String thành Mono hay ngược lại |
| Advice ghi sai orderId trong nhiều request | Dùng field mutable trong singleton aspect | Dùng local variables và cơ chế context/counter an toàn |
| Không chặn method override hoặc interface method | Khác biệt method annotation lookup/proxy strategy | Test với JDK/class proxy thực tế; xác định nơi annotation cần đặt |
| Method lỗi nhưng @AfterReturning vẫn chạy | Method trả một Future/Publisher, lỗi xảy ra sau lần trả đồng bộ | Phân biệt return của Java method với kết quả bất đồng bộ |
| Chỉ muốn filter nhưng vô tình bind sai type | Dùng tên biến, advice nhận sai annotation class | Kiểm tra type, expression và signature thống nhất |

### Kiểm thử tối thiểu

Dùng Spring integration test (khởi tạo ApplicationContext hoặc @SpringJUnitConfig / @SpringBootTest tùy ứng dụng) và **lấy service từ context**, không tự new service. Kiểm thử:

1. Hai methods dùng cùng TrackOperation nhưng có action/sensitive khác nhau: advice nhận **đúng từng giá trị**, không dùng nhầm giá trị method khác.
2. Method không gắn annotation: không bị advice này chặn.
3. Advice @Around trả nguyên String và giữ nguyên exception từ target.
4. Gọi qua self-invocation: xác nhận giới hạn của Spring proxy, hoặc thiết kế service boundary khác.
5. Chạy song song nhiều method annotated: giá trị bind không bị chéo giữa các invocation.
6. Nếu sử dụng interface, overload, composed annotation hoặc nhiều advisors, kiểm thử đúng cấu hình proxy và thứ tự advisor sẽ chạy ở production.

Các test có thể thu nhận invocation bằng spy/collector an toàn thread thay vì so sánh println khi concurrency không bảo đảm thứ tự.

## 12. Quy trình áp dụng nhanh

1. Xác định custom annotation mang metadata nào và đặt retention RUNTIME; chọn METHOD khi cấu hình cho từng method.
2. Gắn annotation lên đúng method trong Spring bean; xác minh người gọi thực sự qua proxy.
3. Viết pointcut "@annotation(variable)" với advice parameter có type là annotation tương ứng.
4. Khai báo argNames trùng variable (bỏ tham số JoinPoint đầu tiên khỏi argNames nếu áp dụng).
5. Đọc attribute của annotation trong advice; lưu dữ liệu mỗi invocation bằng local, không gán vào shared mutable fields.
6. Chọn loại advice và bảo toàn proceed()/return/exception theo hợp đồng.
7. Kiểm thử annotation value theo nhiều methods, method không annotate, lỗi, proxy strategy và concurrency.

Điểm mấu chốt trong Spring: **@annotation(variable) vừa nhận diện method được Spring AOP proxy chặn, vừa ràng buộc đúng instance annotation vào tham số có tên tương ứng của advice**. Một aspect có thể xử lý nhiều methods với cấu hình khác nhau được khai báo ngay tại các annotation, trong khi Spring giữ nguyên cơ chế proxy và thời điểm method execution.

Tài liệu tham khảo chính thức:
- Spring Framework, Declaring a Pointcut: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Spring Framework, Declaring Advice (Passing Parameters to Advice / Explicit Argument Names): https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework, @AspectJ support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-aspect-annotations-004">Annotation trên interface, phương thức triển khai và lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Annotation trên interface, phương thức triển khai và lớp trong Spring AOP

## 1. Vì sao vị trí đặt annotation ảnh hưởng đến advice?

Trong Spring AOP, annotation có thể nằm ở **phương thức của interface**, **phương thức triển khai**, **lớp triển khai**, hoặc **chính interface**. Tuy cùng ghi một annotation tùy chỉnh, bốn vị trí này không tương đương khi viết pointcut và khi đọc metadata của lời gọi.

Lý do cốt lõi nằm ở ba lớp cơ chế:

1. Spring AOP dùng **proxy** để chặn các method execution trên Spring bean. Lời gọi cần đi qua proxy và pointcut phải khớp.
2. **Java reflection không tự sao chép annotation** từ interface sang lớp hoặc phương thức triển khai. `@Override` không thay đổi quy tắc này.
3. `@annotation`, `@within`, `@target` xét những vị trí khác nhau; cách một framework cụ thể tra cứu thêm metadata ở interface không đồng nghĩa Java có annotation inheritance cho interface.

Bài này tập trung vào **việc chọn và đọc annotation cho Spring AOP**, với ví dụ chạy được và kết quả quan sát cụ thể. Các khái niệm annotation Java thuần túy chỉ được nhắc đến tại điểm cần thiết để giải thích kết quả trong Spring.

### Các vị trí thường gặp

~~~java
// (A) Annotation trên phương thức của interface
interface PaymentPort {
    @Tracked("interface-method")
    void pay();
}

// (B) Annotation trên phương thức triển khai
class PaymentService implements PaymentPort {
    @Override
    @Tracked("implementation-method")
    public void pay() { }
}

// (C) Annotation trên lớp triển khai
@Tracked("implementation-class")
class ShippingService {
    public void ship() { }
}

// (D) Annotation trên chính interface
@Tracked("interface-type")
interface ShippingPort {
    void ship();
}
~~~

Đây là **đoạn minh họa vị trí** (chưa phải chương trình Java độc lập). Annotation `Tracked` phải có `@Retention(RUNTIME)` và `@Target({METHOD, TYPE})` nếu muốn đọc được bằng reflection lúc chạy.

## 2. Phân biệt matching pointcut với tra cứu annotation

### 2.1. Ba expression khác nhau

Spring AOP hỗ trợ các pointcut designator liên quan đến annotation:

| Expression | Ý nghĩa trong Spring AOP | Câu hỏi đặt ra |
| --- | --- | --- |
| `@annotation(com.example.Tracked)` | Khớp method execution có annotation trên phương thức làm subject của join point | Lời gọi method này có annotation phù hợp không? |
| `@within(com.example.Tracked)` | Giới hạn vào các method execution nằm trong kiểu được annotation | Phương thức được khai báo trong type được đánh dấu không? |
| `@target(com.example.Tracked)` | Kiểm tra annotation trên lớp của đối tượng target thực thi | Lớp target tại runtime có annotation phù hợp không? |

Với `execution(* ...)`, bạn chọn phương thức theo chữ ký; còn các biểu thức trên chọn dựa vào annotation. **Đừng giả định `@annotation` tự đồng nghĩa với “quét annotation trên method triển khai, mọi interface, lớp và lớp cha”**.

Ví dụ muốn đo mọi public method của các đối tượng có annotation trên **target class**:

~~~java
@Around("@target(com.example.annotationdemo.Tracked) && execution(public * *(..))")
public Object aroundTargetClass(ProceedingJoinPoint pjp) throws Throwable {
    return pjp.proceed();
}
~~~

Đoạn trên minh họa expression; nó cần được đặt trong một Spring `@Aspect` hợp lệ. `@target` không có nghĩa là lấy annotation gắn trên interface bằng cách “kế thừa” nó sang target class.

### 2.2. Hai kiểu proxy tạo ra những khác biệt quan trọng

| Yếu tố | JDK dynamic proxy | Class-based proxy (CGLIB trong Spring) |
| --- | --- | --- |
| Kiểu object client thường nhìn thấy | Proxy triển khai interface | Proxy kế thừa lớp target |
| Method client gọi được để advice | Public method phơi ra qua proxied interface | Các method có thể override/intercept; thông thường nên dùng public method |
| Chữ ký method thu được từ `MethodSignature.getMethod()` | Có thể là method khai báo ở interface | Có thể là method của lớp triển khai |
| Tìm annotation chỉ từ method của chữ ký proxy | Có thể bỏ qua annotation chỉ nằm ở implementation | Có thể bỏ qua annotation chỉ nằm trên interface |
| Self-invocation `this.method()` | Không qua proxy | Không qua proxy |

Spring Boot 3.x thông thường dùng class-based proxy mặc định cho AOP; có thể chọn JDK proxy bằng `spring.aop.proxy-target-class=false` nếu bean có interface phù hợp. Spring Framework thuần có thể có mặc định khác theo cấu hình. Không cần đoán proxy type: có thể dùng `AopUtils.isJdkDynamicProxy(bean)` hoặc `AopUtils.isCglibProxy(bean)` khi kiểm tra.

Lưu ý: `MethodSignature.getMethod()` cung cấp method tương ứng chữ ký của join point. Để nghiên cứu annotation của implementation, dùng `AopUtils.getMostSpecificMethod(signature.getMethod(), targetClass)` và kiểm tra trực tiếp class của **target**, không dựa riêng vào proxy class.

### 2.3. Java `@Inherited` không giải quyết chuyện interface method

`@Inherited` chỉ có ý nghĩa kế thừa annotation **trên lớp** theo chuỗi superclass trong cơ chế tra cứu của Java; nó không làm interface annotation trở thành annotation được khai báo trên lớp triển khai, và cũng không áp dụng cho annotation trên phương thức overridden. Không được xem `getDeclaredAnnotation()` trên implementation method như thể nó tự đọc annotation từ interface method.

Một số thành phần Spring như `@Transactional` dùng **cơ chế tìm metadata chuyên biệt của chính thành phần đó**, có thể hỗ trợ annotation khai báo trên interface trong chế độ proxy. Không suy diễn rằng custom `@Aspect` với `@annotation` có cùng chính sách tra cứu như transaction interceptor. Khi cần bảo đảm, hãy định nghĩa chính sách vị trí và kiểm chứng ở đúng phiên bản Spring đang dùng.

## 3. Quy tắc đề xuất để dùng annotation tùy chỉnh nhất quán

Giả sử nghiệp vụ muốn cho phép người viết annotation ở cả bốn vị trí. Có thể định nghĩa thứ tự ưu tiên rõ ràng:

1. Annotation trực tiếp trên **implementation method**.
2. Annotation trên **interface method** tương ứng.
3. Annotation trên **implementation class**.
4. Annotation trên **interface type**.

Đây là **quy tắc do ứng dụng tự chọn**, không phải thứ tự ưu tiên mặc định của mọi Spring annotation. Nó tránh tình huống annotation chung trên class ghi đè cấu hình cụ thể ở method, đồng thời vẫn hỗ trợ interface làm nơi khai báo contract.

Nếu nhiều interface có cùng signature nhưng mang annotation khác nhau, cần chính sách xung đột (ví dụ báo lỗi khởi động) hoặc thứ tự ưu tiên được công bố. Không nên phụ thuộc vào thứ tự `Class.getInterfaces()` để quyết định nghiệp vụ khi cấu hình mâu thuẫn. Ví dụ thực hành bên dưới mỗi service chỉ triển khai một interface, nên không có xung đột đó.

Một điểm đặc biệt: **advice phải được chọn trước thì mới thực hiện được resolver bên trong advice**. Nếu aspect chỉ dùng `@annotation(Tracked)` mà expression không khớp tại proxy boundary, việc viết mã reflection đầy đủ bên trong advice vẫn không làm method đó được intercept. Cách an toàn khi muốn tự định nghĩa chính sách bốn vị trí là chọn phạm vi method bằng `execution` (và giới hạn package/bean cần thiết), sau đó tự tra cứu annotation trong advice.

## 4. Chương trình Spring Boot chạy được: đủ cả bốn vị trí

Ví dụ dùng Java 17, Spring Boot 3.3.x và Spring AOP. Mỗi service có một method `execute()`, được đánh dấu ở **đúng một** vị trí, nhằm tránh nhầm lẫn khi quan sát. Advice dùng `execution` để chặn method rồi tìm annotation theo thứ tự trên.

Cấu trúc thư mục:

~~~text
pom.xml
src/main/java/com/example/annotationdemo/
    DemoApplication.java
    Tracked.java
    Services.java
    TrackedAspect.java
~~~

### 4.1. Dependencies

`pom.xml`:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-annotation-aop</artifactId>
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

`spring-boot-starter-aop` cung cấp Spring AOP cùng các thành phần AspectJ cần thiết để Spring phân tích pointcut expression. Ví dụ không dùng AspectJ compile-time weaving.

### 4.2. Annotation chỉ lưu metadata

`src/main/java/com/example/annotationdemo/Tracked.java`:

~~~java
package com.example.annotationdemo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Tracked {
    String value();
}
~~~

Annotation này không tự làm advice chạy. Spring phải có aspect được đăng ký và pointcut khớp lời gọi qua proxy thì metadata mới được sử dụng.

### 4.3. Bốn interface và bốn service

`src/main/java/com/example/annotationdemo/Services.java`:

~~~java
package com.example.annotationdemo;

import org.springframework.stereotype.Service;

// Tình huống 1: annotation tại interface method
interface InterfaceMethodPort {
    @Tracked("interface-method")
    String execute();
}

@Service
class InterfaceMethodService implements InterfaceMethodPort {
    @Override
    public String execute() {
        return "A";
    }
}

// Tình huống 2: annotation tại implementation method
interface ImplementationMethodPort {
    String execute();
}

@Service
class ImplementationMethodService implements ImplementationMethodPort {
    @Override
    @Tracked("implementation-method")
    public String execute() {
        return "B";
    }
}

// Tình huống 3: annotation tại implementation class
interface ClassLevelPort {
    String execute();
}

@Service
@Tracked("implementation-class")
class ClassLevelService implements ClassLevelPort {
    @Override
    public String execute() {
        return "C";
    }
}

// Tình huống 4: annotation tại interface type
@Tracked("interface-type")
interface InterfaceTypePort {
    String execute();
}

@Service
class InterfaceTypeService implements InterfaceTypePort {
    @Override
    public String execute() {
        return "D";
    }
}
~~~

Các interface và class trong tệp này có quyền truy cập package-private vì ứng dụng demo và aspect cùng package. Các method triển khai `execute()` đều `public`, không `final`; mỗi bean có đúng một interface. Spring quản lý các service thông qua `@Service`, nên những lời gọi nhận bean qua dependency injection có thể đi qua Spring AOP proxy.

### 4.4. Aspect tra cứu metadata ở bốn vị trí

`src/main/java/com/example/annotationdemo/TrackedAspect.java`:

~~~java
package com.example.annotationdemo;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TrackedAspect {

    @Around("execution(public * com.example.annotationdemo..*.execute(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Class<?> targetClass = pjp.getTarget().getClass();

        Method implementationMethod =
                AopUtils.getMostSpecificMethod(signature.getMethod(), targetClass);

        Tracked annotation = resolve(implementationMethod, targetClass);

        if (annotation != null) {
            System.out.println("tracked=" + annotation.value());
        }

        return pjp.proceed();
    }

    private Tracked resolve(Method method, Class<?> targetClass) {
        // 1. Annotation khai báo trực tiếp ở implementation method
        Tracked found = method.getDeclaredAnnotation(Tracked.class);
        if (found != null) {
            return found;
        }

        // 2. Annotation ở method tương ứng trên direct interface
        for (Class<?> contract : targetClass.getInterfaces()) {
            try {
                Method interfaceMethod = contract.getMethod(
                        method.getName(), method.getParameterTypes());

                found = interfaceMethod.getDeclaredAnnotation(Tracked.class);
                if (found != null) {
                    return found;
                }
            } catch (NoSuchMethodException ignored) {
                // Interface này không khai báo/không kế thừa method tương ứng.
            }
        }

        // 3. Annotation khai báo trực tiếp trên implementation class
        found = targetClass.getDeclaredAnnotation(Tracked.class);
        if (found != null) {
            return found;
        }

        // 4. Annotation khai báo trực tiếp trên interface type
        for (Class<?> contract : targetClass.getInterfaces()) {
            found = contract.getDeclaredAnnotation(Tracked.class);
            if (found != null) {
                return found;
            }
        }

        return null;
    }
}
~~~

**Tại sao dùng `execution`?** Biểu thức `execution(public * com.example.annotationdemo..*.execute(..))` bắt các method public tên `execute` trong package demo (hoặc các package con), không đòi hỏi Spring phải tự tìm `@Tracked` tại một vị trí nhất định lúc match pointcut. Trong advice, resolver mới quyết định annotation nào có hiệu lực.

**Tại sao phải tìm `implementationMethod`?** Khi gọi qua JDK proxy, `signature.getMethod()` có thể là method của interface, vốn không chứa annotation được đặt riêng tại implementation method. `AopUtils.getMostSpecificMethod(...)` chuyển về method cụ thể tương ứng của target class để đọc chính xác annotation khai báo tại đó.

Đây là **resolver có chủ ý giới hạn**: xử lý direct interface, method không generic/bridge phức tạp, và một annotation mỗi vị trí. Với interface kế thừa nhiều tầng, generic bridge method, meta-annotation, hoặc nhiều interface cùng khai báo một signature, hãy dùng chính sách rõ ràng và API như `BridgeMethodResolver`/`AnnotatedElementUtils` phù hợp. Không biến ví dụ tối giản thành cơ chế tra cứu chung cho mọi Spring annotation.

### 4.5. Chạy và đọc kết quả

`src/main/java/com/example/annotationdemo/DemoApplication.java`:

~~~java
package com.example.annotationdemo;

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
    CommandLineRunner examples(
            InterfaceMethodPort fromInterfaceMethod,
            ImplementationMethodPort fromImplementationMethod,
            ClassLevelPort fromClass,
            InterfaceTypePort fromInterfaceType) {

        return args -> {
            System.out.println("== Interface method ==");
            System.out.println("result=" + fromInterfaceMethod.execute());

            System.out.println("== Implementation method ==");
            System.out.println("result=" + fromImplementationMethod.execute());

            System.out.println("== Implementation class ==");
            System.out.println("result=" + fromClass.execute());

            System.out.println("== Interface type ==");
            System.out.println("result=" + fromInterfaceType.execute());
        };
    }
}
~~~

Chạy từ thư mục chứa `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

Bỏ qua log khởi động Spring Boot, kết quả demo là:

~~~text
== Interface method ==
tracked=interface-method
result=A
== Implementation method ==
tracked=implementation-method
result=B
== Implementation class ==
tracked=implementation-class
result=C
== Interface type ==
tracked=interface-type
result=D
~~~

Mỗi lời gọi `execute()` qua proxy vào advice một lần. Resolver biết cách tra annotation ở cả bốn vị trí theo thứ tự định nghĩa, nên các dòng `tracked=...` xuất hiện tương ứng. `proceed()` vẫn trả về đúng chuỗi `A`, `B`, `C`, `D` từ service.

Muốn thử JDK proxy trong Spring Boot, chạy lại với thuộc tính sau (hoặc đặt vào `application.properties`):

~~~bash
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.aop.proxy-target-class=false
~~~

Các bean demo đều có interface nên có thể dùng interface-based proxies. Vì `CommandLineRunner` nhận dependency theo **interface**, và aspect sử dụng `execution` rồi tự phân giải annotation từ target, **các dòng nghiệp vụ ở trên vẫn được kỳ vọng như nhau**. Để xem chính xác loại proxy, có thể thêm kiểm tra bằng `AopUtils.isJdkDynamicProxy(...)` hoặc `AopUtils.isCglibProxy(...)` trong runner. Không ép inject concrete class khi chọn JDK proxy: JDK proxy không phải subclass của implementation.

## 5. Nếu annotation xuất hiện ở nhiều vị trí cùng lúc?

Xét trường hợp method trên interface có `@Tracked("contract")`, implementation method có `@Tracked("implementation")`, và implementation class có `@Tracked("service-default")`.

Với resolver ở mục 4, kết quả là `tracked=implementation` vì implementation method được kiểm tra trước. Nếu bỏ annotation trên implementation method thì resolver trả `contract`; nếu bỏ tiếp annotation trên interface method thì trả `service-default`.

Đây là **chính sách ưu tiên riêng của ví dụ**, có thể thay đổi theo yêu cầu hệ thống. Một chính sách khác có thể ưu tiên annotation của implementation class hơn interface method để áp một default cho cả service. Dù chọn gì, nên dùng test mô tả rõ thứ tự vì không có một bảng ưu tiên chung áp dụng cho tất cả AOP annotation của Spring.

Không nên tổng hợp hai cấu hình trái ngược tùy ý (ví dụ `@Tracked(value="fast")` ở interface và `@Tracked(value="slow")` ở implementation) mà không có chính sách xung đột. Một resolver cho production có thể từ chối cấu hình mơ hồ tại startup, giúp phát hiện thay đổi contract sớm.

## 6. Điểm khác giữa custom `@Aspect` và annotation tích hợp của Spring

Ví dụ điển hình là `@Transactional`. Spring có `TransactionAttributeSource` để tìm metadata transaction ở method và class, bao gồm những trường hợp interface method được hỗ trợ trong **proxy mode**. Điều đó là hành vi của **hạ tầng transaction**, không phải vì mọi annotation Java tự được kế thừa hay mọi `@annotation` pointcut đều tự động dùng resolver như vậy.

Khi thiết kế annotation của riêng mình, hãy tách hai bước:

- **Bước chọn join point:** pointcut xác định lời gọi nào có thể chạy advice.
- **Bước chọn metadata:** logic resolver xác định annotation nào được đọc, vị trí nào ưu tiên và cách xử lý xung đột.

Trong ứng dụng đơn giản chỉ chấp nhận annotation trên implementation method, biểu thức `@annotation(com.example.annotationdemo.Tracked)` có thể là cách gọn hơn. Nếu hợp đồng API yêu cầu annotation trên interface **và** hỗ trợ chuyển đổi JDK/CGLIB proxy, đừng chỉ dựa vào một reflection call trên `MethodSignature.getMethod()`; thử chính xác các trường hợp mong muốn.

Với meta-annotation/composed annotation (ví dụ một `@AuditedCommand` được đánh dấu `@Tracked`), `getDeclaredAnnotation(Tracked.class)` sẽ không giải quyết toàn bộ mô hình Spring merged annotations. Khi đó có thể dùng `AnnotatedElementUtils.findMergedAnnotation(...)` hoặc `MergedAnnotations`, nhưng cần chủ động quy định thứ tự giữa method, type và interface thay vì giả định hai API này thay thế toàn bộ resolver.

## 7. Các lỗi thường gặp và cách kiểm tra

| Dấu hiệu | Nguyên nhân có thể | Hướng kiểm tra |
| --- | --- | --- |
| Gắn `@Tracked` trên interface method nhưng không có advice | Pointcut không khớp tại method execution do proxy thấy, hoặc lời gọi không qua proxy | Xác minh AOP được bật, proxy tồn tại và pointcut match; thử expression `execution` được giới hạn rõ |
| Advice chạy nhưng không tìm được annotation ở implementation method | Chỉ đọc method của interface từ `MethodSignature` | Lấy most-specific method trên target class trước |
| Annotation class không có trên method | Class annotation không đồng nghĩa method có annotation trực tiếp | Kiểm tra class riêng; chọn `@target`/`@within` hoặc resolver tùy mục đích |
| Interface type được đánh dấu nhưng target class không có annotation | Java không kế thừa annotation từ interface sang class | Tra interface type nếu policy cần |
| Advice chạy khi JDK proxy nhưng vắng khi đổi cấu hình proxy | Pointcut, visibility, exposed method và metadata source khác nhau | Test cả hai chế độ, kiểm tra method public/interface |
| Inner method có annotation nhưng không bị chặn | `this.inner()` gọi trực tiếp trong cùng target | Tách sang bean khác để lời gọi đi qua proxy |
| Dùng `@Inherited` mà interface method vẫn không được nhận diện | `@Inherited` không áp dụng cho method hoặc interface inheritance | Tra interface method tường minh |
| Nhiều interface cho kết quả phụ thuộc cấu trúc kế thừa | Resolver chưa có chính sách conflict | Khai báo precedence hoặc fail-fast; test interface hierarchy |
| Gắn annotation lên `private`/`final` method nhưng không có advice | Proxy không thể intercept phương thức phù hợp | Dùng public method qua proxy, hoặc thay thiết kế phù hợp |

Để xác minh thay vì suy đoán: lấy bean từ ApplicationContext, kiểm tra bằng `AopUtils.isAopProxy(bean)`, phân biệt JDK/CGLIB, xác nhận phương thức công khai được gọi **từ bean khác**, rồi kiểm tra pointcut có match và resolver đã tìm tới đúng nơi đặt annotation chưa.

## 8. Checklist khi đưa annotation custom vào dự án

1. Xác định rõ `@Target`, `@Retention(RUNTIME)`, tên và dữ liệu annotation; vị trí nào là contract công khai.
2. Chỉ rõ annotation được phép nằm ở interface method, implementation method, class và/hoặc interface type.
3. Chọn `@annotation`, `@within`, `@target` nếu vị trí đơn nhất đáp ứng yêu cầu; nếu cần chính sách hợp nhất nhiều vị trí thì giới hạn bằng `execution` và dùng resolver có kiểm thử.
4. Định nghĩa precedence và cách xử lý nhiều interface, method overload, generic bridge, inheritance và composed annotation.
5. Kiểm chứng đường gọi qua proxy trong cả cấu hình JDK/class-based đang được hỗ trợ; tránh self-invocation và các method không intercept được.
6. Kiểm tra advice giữ nguyên return value/exception của `proceed()`, đồng thời không tạo chi phí reflection lớn ở hot path. Có thể cache kết quả tra metadata theo method + target class, sau khi bảo đảm khóa cache phản ánh đúng chính sách.
7. Nếu annotation ảnh hưởng tới authorization, transaction hay bảo đảm nghiệp vụ, viết integration test cho các trường hợp khớp và không khớp; không coi log demo là bằng chứng đầy đủ về policy.

## 9. Kết luận

Trong Spring AOP, vị trí đặt annotation và đường gọi qua proxy là hai quyết định riêng. **Annotation trên interface không tự trở thành annotation trên implementation**, còn `@annotation`, `@within`, `@target` có ý nghĩa matching khác nhau. Khi cần hỗ trợ đồng thời interface method, implementation method, implementation class và interface type, hãy xác định chính sách tra metadata tường minh, dùng pointcut đủ bao phủ các lời gọi được phép và kiểm chứng trên proxy thực tế.

Tham khảo chính thức:
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html)
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — Using @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-aspect-annotations-005">Annotation tổng hợp trong Spring và giới hạn khớp pointcut cần kiểm chứng</a>

<details>
<summary>Xem chi tiết</summary>

# Annotation tổng hợp trong Spring và giới hạn khớp pointcut cần kiểm chứng

## 1. Vấn đề thực tế trong Spring AOP

Khi xây dựng annotation tùy chỉnh cho logging, audit hoặc kiểm soát truy cập, một dự án Spring thường muốn khai báo annotation cấp cao dễ hiểu với nghiệp vụ. Ví dụ, `@BusinessTraced` được xây dựng trên `@Traced`, rồi gắn lên các phương thức dịch vụ để nhận cùng hành vi tracing.

Điểm dễ nhầm là **Spring đọc được một meta-annotation không đồng nghĩa với mọi pointcut Spring AOP tự động khớp meta-annotation ấy**. Hai cơ chế liên quan nhưng khác nhau:

1. **Mô hình annotation tổng hợp của Spring**: `MergedAnnotations` và `AnnotatedElementUtils` có thể tìm annotation trực tiếp, meta-annotation và gộp thuộc tính, bao gồm giá trị được khai báo qua `@AliasFor`.
2. **Khớp pointcut dạng `@AspectJ`**: `@annotation(com.example.Traced)` dùng quy tắc khớp annotation của ngôn ngữ pointcut AspectJ, không mặc nhiên áp dụng mọi quy tắc tìm kiếm meta-annotation của Spring.
3. **Pointcut lập trình của Spring**: `AnnotationMatchingPointcut` có lựa chọn `checkInherited=true` để kiểm tra thêm annotation ở lớp cha, interface và meta-annotation theo cơ chế matcher tương ứng.

Bài học chỉ xét **Spring Framework / Spring AOP**: thiết kế annotation trên Spring bean, giới hạn khớp của advice và cách viết test để xác nhận hành vi thật. Không mở rộng sang các kiến thức AspectJ weaving độc lập ngoài phạm vi này.

### Mục tiêu

- Khai báo annotation `@Traced` và annotation tổng hợp `@BusinessTraced` theo đúng `@Target`, `@Retention`, `@AliasFor`.
- Hiểu sự khác biệt giữa **annotation được tìm thấy** và **lời gọi thực sự bị Spring AOP proxy chặn**.
- Viết test chứng minh annotation trực tiếp, annotation tổng hợp và phương thức không đánh dấu có hành vi khác nhau.
- Biết chọn giải pháp khi muốn hỗ trợ annotation tổng hợp mà không làm pointcut bắt nhầm phương thức.
- Xác định các giới hạn khác cần kiểm chứng: JDK proxy/CGLIB, annotation trên interface, self-invocation, scope phương thức và annotation ở class.

## 2. Spring nhận diện annotation tổng hợp như thế nào?

Một meta-annotation là annotation đặt **trên khai báo của một annotation khác**. Ví dụ:

~~~java
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Traced {
    String label() default "default";
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Traced
public @interface BusinessTraced {
    @AliasFor(annotation = Traced.class, attribute = "label")
    String label() default "default";
}
~~~

Đây là **hai khai báo minh họa**, muốn đưa vào dự án phải đặt trong các tệp `Traced.java`, `BusinessTraced.java`, có import `java.lang.annotation.*` và `org.springframework.core.annotation.AliasFor`. Phần 4 cung cấp test Java đầy đủ trong một file.

Điều kiện có ý nghĩa:

- `Traced` cho phép `ElementType.ANNOTATION_TYPE` thì mới có thể dùng làm meta-annotation cho `BusinessTraced`.
- `BusinessTraced` cho phép `ElementType.METHOD` vì bài toán đánh dấu method trên Spring bean.
- `RetentionPolicy.RUNTIME` giúp các API reflection/Spring đọc metadata khi chạy.
- `@AliasFor(annotation = Traced.class, attribute = "label")` khai báo thuộc tính `label` của annotation tổng hợp ghi đè thuộc tính `label` của annotation gốc trong **mô hình merged annotations của Spring**. Nó **không** tự thay đổi ngữ nghĩa khớp `@annotation` của AspectJ.
- Kiểu của hai thuộc tính phải tương thích, giá trị mặc định tương ứng cần đồng nhất; không đặt hai giá trị xung đột rồi kỳ vọng Spring tự chọn.

Khi viết:

~~~java
@BusinessTraced(label = "checkout")
public void checkout() {
    // business logic
}
~~~

Reflection Java theo cách tra **trực tiếp** `checkoutMethod.getAnnotation(Traced.class)` không thấy `@Traced` trên chính method đó. Spring có thể lấy `Traced` theo nhánh meta-annotation:

~~~java
Method method = CheckoutService.class.getMethod("checkout");

Traced direct = method.getAnnotation(Traced.class);
Traced merged = AnnotatedElementUtils.findMergedAnnotation(method, Traced.class);

System.out.println(direct == null);
System.out.println(merged.label());
~~~

Ở đây `CheckoutService` là class có method `checkout` trong ví dụ ngay trước, cần import `java.lang.reflect.Method` và `org.springframework.core.annotation.AnnotatedElementUtils`. Nếu hai annotation được khai báo đúng, hai dòng in ra lần lượt là:

~~~text
true
checkout
~~~

Đó là bằng chứng Spring **đọc được metadata**. Nó chưa chứng minh một `@Before`, `@Around` hay `@AfterReturning` được chạy.

### Các trường hợp sử dụng cơ chế này trong Spring

Spring có nhiều cơ chế riêng biết xử lý annotation tổng hợp, ví dụ các stereotype dựa trên `@Component`, mapping trong Spring MVC, hoặc một số cơ chế annotation-driven transaction/cache. **Không suy từ hành vi của chúng sang mọi pointcut tùy chỉnh**: mỗi subsystem có matcher và chiến lược tìm annotation của mình.

## 3. Phân biệt điểm khớp của Spring AOP

Trong chế độ proxy-based AOP, Spring chặn **method execution trên bean qua proxy**. Các dạng pointcut liên quan:

| Cú pháp | Đối tượng được đối chiếu | Điều không nên suy ra |
| --- | --- | --- |
| `@annotation(demo.Traced)` | Annotation trên method tại join point | Không tự đồng nghĩa với truy vấn `MergedAnnotations` theo meta-annotation |
| `@within(demo.ServiceTag)` | Annotation ở type chứa method được khớp | Không phải `@annotation` trên từng method |
| `@target(demo.ServiceTag)` | Annotation trên class của target runtime | Không dùng thay thế cho mọi trường hợp annotation ở interface |
| `execution(public * demo.CatalogueService.*(..))` | Chữ ký và phạm vi của method execution | Khớp phương thức dù có hay không có `@Traced` |

Dưới đây là ví dụ với **method annotation**:

~~~java
@Aspect
public class DirectTraceAspect {
    @Before("@annotation(demo.Traced)")
    public void record(JoinPoint joinPoint) {
        System.out.println(joinPoint.getSignature().getName());
    }
}
~~~

Aspect trên yêu cầu `demo.Traced` theo cú pháp `@annotation` của AspectJ. **Không được mặc nhiên giả định** rằng method chỉ gắn `@BusinessTraced` sẽ vào advice, dù Spring `findMergedAnnotation` nhận ra `Traced` ở method ấy. Với nhiều loại composed annotation, kết quả thực tế trong Spring AOP là method trực tiếp được intercept, còn composed method bị bỏ qua. Hãy chứng minh bằng test trên **đúng phiên bản Spring và cấu hình proxy của dự án**.

> Lưu ý: `@AliasFor` giải quyết cách Spring tổng hợp giá trị thuộc tính. Nó không thêm một biểu thức pointcut thay thế vào advice.

## 4. Test JUnit 5: metadata đúng nhưng pointcut có thể bỏ qua

Ví dụ này kiểm tra ba câu hỏi độc lập:

1. `MergedAnnotations` có nhìn thấy annotation gốc qua `@BusinessTraced` và đọc giá trị `label` đã override không?
2. Advice `@Before("@annotation(demo.Traced)")` có thực sự được gọi cho method đánh dấu trực tiếp, method đánh dấu bằng composed annotation và method thường không?
3. Một `AnnotationMatchingPointcut` với `checkInherited=true` có cho phép đánh dấu cả annotation trực tiếp và annotation tổng hợp không?

### Dependency

Với ứng dụng Spring Boot 3.x sử dụng Gradle Groovy DSL và đã có plugin Spring Boot cùng BOM/dependency management:

~~~groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

Đối với dự án Spring Framework thuần, cần `spring-aop`, `spring-context` khi tích hợp bean, `aspectjweaver` cho expression pointcut và JUnit Jupiter cho test; các phiên bản phải tương thích và được quản lý tường minh. `AspectJProxyFactory` và `ProxyFactory` trong ví dụ dưới đây là Spring AOP thật, dù test không chạy HTTP server.

### File test hoàn chỉnh

Tạo file `src/test/java/demo/ComposedAnnotationPointcutTest.java`:

~~~java
package demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.aopalliance.intercept.MethodInterceptor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.annotation.AnnotatedElementUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@interface Traced {
    String label() default "default";
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Traced
@interface BusinessTraced {
    @AliasFor(annotation = Traced.class, attribute = "label")
    String label() default "default";
}

class CatalogueService {
    @Traced(label = "direct")
    public String direct() {
        return "direct";
    }

    @BusinessTraced(label = "business")
    public String composed() {
        return "composed";
    }

    public String plain() {
        return "plain";
    }
}

@Aspect
class DirectAnnotationAspect {
    final List<String> invoked = new ArrayList<>();

    @Before("@annotation(demo.Traced)")
    public void before(JoinPoint joinPoint) {
        invoked.add(joinPoint.getSignature().getName());
    }
}

public class ComposedAnnotationPointcutTest {

    @Test
    void springCanReadAComposedAnnotation() throws Exception {
        Method method = CatalogueService.class.getMethod("composed");

        // Java reflection trực tiếp không thấy @Traced trên method.
        assertNull(method.getAnnotation(Traced.class));

        // Spring đi qua @BusinessTraced -> @Traced và gộp @AliasFor.
        Traced merged =
                AnnotatedElementUtils.findMergedAnnotation(method, Traced.class);
        assertNotNull(merged);
        assertEquals("business", merged.label());
    }

    @Test
    void aspectJStyleAnnotationPointcutOnlyRecordsDirectAnnotation() {
        DirectAnnotationAspect aspect = new DirectAnnotationAspect();
        AspectJProxyFactory factory = new AspectJProxyFactory(new CatalogueService());
        factory.addAspect(aspect);
        CatalogueService service = factory.getProxy();

        assertEquals("direct", service.direct());
        assertEquals("composed", service.composed());
        assertEquals("plain", service.plain());

        // Điểm quan trọng: test đo advice thực sự chạy qua Spring proxy.
        assertEquals(List.of("direct"), aspect.invoked);
    }

    @Test
    void springAnnotationPointcutCanInspectMetaAnnotations() {
        List<String> intercepted = new ArrayList<>();
        ProxyFactory factory = new ProxyFactory(new CatalogueService());

        AnnotationMatchingPointcut pointcut =
                new AnnotationMatchingPointcut(null, Traced.class, true);

        factory.addAdvisor(new DefaultPointcutAdvisor(
                pointcut,
                (MethodInterceptor) invocation -> {
                    intercepted.add(invocation.getMethod().getName());
                    return invocation.proceed();
                }));

        CatalogueService service = (CatalogueService) factory.getProxy();
        assertEquals("direct", service.direct());
        assertEquals("composed", service.composed());
        assertEquals("plain", service.plain());

        assertEquals(List.of("direct", "composed"), intercepted);
    }
}
~~~

Chạy riêng test:

~~~bash
./gradlew test --tests demo.ComposedAnnotationPointcutTest
~~~

Trên Windows:

~~~powershell
.\gradlew.bat test --tests demo.ComposedAnnotationPointcutTest
~~~

**Kỳ vọng test:**

| Phép thử | Kết luận được kiểm tra |
| --- | --- |
| `springCanReadAComposedAnnotation` | Reflection trực tiếp không thấy `Traced`; Spring tìm được meta-annotation và nhãn `business` |
| `aspectJStyleAnnotationPointcutOnlyRecordsDirectAnnotation` | Với Spring AOP/AspectJ-style pointcut mô tả, advice chỉ ghi lại `direct` |
| `springAnnotationPointcutCanInspectMetaAnnotations` | Với `checkInherited=true`, advisor bắt `direct` và `composed`, bỏ qua `plain` |

Không in stdout trong bộ test này; khi thành công Gradle báo task test thành công theo định dạng riêng của môi trường. Nếu assertion về matching không đúng với stack Spring đang dùng, hãy điều tra phiên bản và proxy đã tạo; **không sửa assertion chỉ để biến test thành xanh**. Ví dụ này dùng class-based proxy cho class không khai báo interface, các method đều public và không final, giúp tập trung kiểm tra semantics của annotation.

**Đây là test về hành vi Spring AOP**, không phải test đơn thuần về reflection. Cần đảm bảo `AspectJProxyFactory` thật sự tạo được proxy và aspect đã được áp dụng. Với ứng dụng thực, nên có thêm test `ApplicationContext` để xác nhận auto-proxying và bean wiring tương ứng.

## 5. Khi muốn annotation tổng hợp được intercept, chọn cách nào?

### 5.1. Giải pháp đơn giản: khai báo các annotation tường minh trong pointcut

Nếu số lượng annotation tổng hợp ít và được kiểm soát:

~~~java
@Before("@annotation(demo.Traced) || @annotation(demo.BusinessTraced)")
public void before(JoinPoint joinPoint) {
    // Theo dõi lời gọi được đánh dấu bằng một trong hai annotation.
}
~~~

Ưu điểm: tường minh, dễ hiểu và đơn giản trong một số dự án. Hạn chế: mỗi loại annotation tổng hợp mới cần được bổ sung vào biểu thức và được test; không phải cơ chế tìm mọi meta-annotation theo chiều sâu bất kỳ.

### 5.2. Dùng Spring programmatic advisor hỗ trợ tìm meta-annotation

Ở test mục 4, `AnnotationMatchingPointcut(null, Traced.class, true)` kiểm tra metadata cả mức method, đồng thời bật hỗ trợ annotation kế thừa/meta theo API của Spring. Kết hợp `DefaultPointcutAdvisor` với `MethodInterceptor` có thể tổ chức advice mà không cần viết `@Before("@annotation(...)")` cho từng annotation con.

Trong ứng dụng Spring-managed, có thể đăng ký một `Advisor` bean vào context thay cho việc tự tạo `ProxyFactory` cho từng đối tượng. Ví dụ **khung cấu hình**:

~~~java
@Configuration
class TraceAdvisorConfig {
    @Bean
    Advisor traceAdvisor(TraceInterceptor traceInterceptor) {
        return new DefaultPointcutAdvisor(
                new AnnotationMatchingPointcut(null, Traced.class, true),
                traceInterceptor);
    }
}
~~~

Ở đoạn này, `TraceInterceptor` là một lớp ứng dụng implement `MethodInterceptor`; `Advisor`, `@Configuration`, `@Bean` và các type liên quan phải được import. Đây là **khung tích hợp**, chưa phải file chạy độc lập. Khi áp dụng, đo xem `BeanFactory` có thực sự gắn advisor vào bean cần xử lý và lưu ý thứ tự advisor khi có transaction, security, caching.

### 5.3. Dùng pointcut đủ hẹp và đọc merged annotation bên trong advice

Nếu cần một advice `@Aspect` hiện hữu và danh sách composed annotations thường xuyên thay đổi, có thể dùng biểu thức `execution(...)` để khớp **phạm vi dịch vụ đã chọn**, rồi tra merged annotation trong advice:

~~~java
@Around("execution(public * demo.CatalogueService.*(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    Method signatureMethod = ((MethodSignature) pjp.getSignature()).getMethod();
    Method targetMethod = AopUtils.getMostSpecificMethod(
            signatureMethod, pjp.getTarget().getClass());

    Traced traced = AnnotatedElementUtils.findMergedAnnotation(
            targetMethod, Traced.class);
    if (traced == null) {
        return pjp.proceed();
    }

    recordTrace(traced.label()); // tích hợp tracing của ứng dụng
    return pjp.proceed();
}
~~~

Đây là **đoạn code minh họa** bên trong `@Aspect`, cần các import:
`org.aspectj.lang.ProceedingJoinPoint`, `org.aspectj.lang.annotation.Around`,
`org.aspectj.lang.reflect.MethodSignature`, `java.lang.reflect.Method`,
`org.springframework.aop.support.AopUtils` và
`org.springframework.core.annotation.AnnotatedElementUtils`.

Ưu điểm: quyền quyết định dựa trực tiếp vào mô hình merged annotations; có thể lấy thuộc tính `label` ngay. Hạn chế: proxy/pointcut có thể intercept nhiều method hơn số method cần tracing, tăng overhead. Nên giới hạn `execution` theo package, type hoặc method signature cụ thể, xử lý interface annotation theo chính sách dự án, và test cả trường hợp null. Cách này cũng không tự khắc phục self-invocation.

### Tiêu chí lựa chọn

| Nhu cầu | Phương án phù hợp |
| --- | --- |
| Ít annotation, tên ổn định | Pointcut ghép `@annotation(A) || @annotation(B)` và test cả hai |
| Muốn Spring tự tìm meta-annotation ở mức pointcut | `AnnotationMatchingPointcut` với `checkInherited` được cấu hình rõ |
| Cần giá trị tổng hợp `@AliasFor` và logic chọn động | Khớp phạm vi hẹp, dùng `AnnotatedElementUtils` trong advice |
| Cần hỗ trợ bean ngoài Spring hay method gọi nội bộ | Thiết kế khác ngoài giới hạn Spring proxy AOP; không thể chỉ chỉnh `@annotation` |

## 6. Các ranh giới cần kiểm chứng trên dự án thật

**1. Annotation đặt trên method hay class?** `@annotation` chỉ nói tới annotation của method. Nếu muốn đánh dấu cả class, hãy thiết kế annotation với `@Target(TYPE)` phù hợp và dùng đúng pointcut (`@within`/`@target`) hoặc class annotation matcher; đừng đưa method annotation tới class rồi mong nó match tự động.

**2. Annotation ở interface, method override và implementation:** JDK proxy nhìn qua interface; class-based proxy làm việc với class thực. Annotation có thể đặt ở interface method mà không khai báo lại ở implementation. Thứ tự lấy method và chiến lược tìm metadata có thể tạo kết quả khác nhau. Test phải đối chiếu **actual proxy, implementation method và interface method**. `@Inherited` của Java áp dụng cho type inheritance theo quy tắc riêng; không có nghĩa method annotation được tự động kế thừa theo mọi trường hợp.

**3. Proxy phải thật sự được gọi:** self-invocation (một method trong target gọi trực tiếp method khác của cùng target), tạo đối tượng bằng `new` ngoài Spring, final/private method hoặc cấu hình proxy sai có thể khiến advice không chạy. Bài test nào chỉ gọi `new CatalogueService().composed()` sẽ không chứng minh Spring AOP hoạt động.

**4. Thứ tự và điều kiện advisor:** khi service có transaction, caching hoặc authorization, thứ tự advice có thể làm thay đổi thời điểm logging. Một `@Transactional` composite annotation được Spring transaction infrastructure nhận diện không chứng minh `@Around("@annotation(Transactional)")` cũng bắt đúng như vậy.

**5. Annotation và giá trị thuộc tính:** `@AliasFor` chỉ được Spring áp dụng khi annotation được đọc qua API hỗ trợ gộp. Java reflection thuần không tự thực thi quy tắc alias. Hãy kiểm tra value thực sự nhận được, trường hợp default và cấu hình alias sai/xung đột.

**6. Với ứng dụng reactive:** Spring AOP chặn lời gọi method trả về `Mono`/`Flux`; advice chạy khi method trả publisher, khác thời điểm publisher phát tín hiệu. Nếu viết tracing cho Reactor, cần kiểm thử trên subscription/signal/context đúng cơ chế. Đây là ranh giới của Spring Aspect, không phải bằng chứng rằng một pointcut annotation tự động xử lý toàn bộ chuỗi reactive.

## 7. Ma trận kiểm thử khuyến nghị

| Case | Cấu hình đầu vào | Điều cần assert |
| --- | --- | --- |
| Method gắn `@Traced` trực tiếp | Proxy gọi `direct()` | Advice chạy đúng một lần |
| Method gắn `@BusinessTraced` | Proxy gọi `composed()` | Xác nhận matching theo **phương án đã chọn**; `MergedAnnotations` trả nhãn đã override |
| Method không annotation | Proxy gọi `plain()` | Không có side effect tracing |
| Annotation trên interface method | JDK proxy và class-based proxy | Không phụ thuộc giả định mơ hồ về vị trí annotation |
| Annotation trên class | Dùng annotation `TYPE`, chọn `@within`/`@target` hoặc class matcher | Xác nhận mức type khác method |
| Method tự gọi lẫn nhau | `outer()` gọi `inner()` trong cùng target | Chỉ tính advice tại những lời gọi thực sự đi qua proxy |
| Annotation ở composed nhiều cấp | `@Feature` -> `@BusinessTraced` -> `@Traced` | API merged nhận đúng giá trị; pointcut test riêng, không suy đoán |
| Alias sai hoặc xung đột | Cấu hình annotation không hợp lệ | Test chứng minh cấu hình được phát hiện, không âm thầm dùng sai nhãn |

Có thể tổ chức test `@SpringBootTest` riêng trên bean thật: gọi qua bean được inject, xác nhận `AopUtils.isAopProxy(bean)`, quan sát một recorder độc lập để biết advice đã chạy. Khi dùng cùng một test matrix cho JDK proxy/CGLIB, khai báo interface nếu cần và điều chỉnh `proxyTargetClass` một cách tường minh.

## 8. Lỗi thường gặp và cách chẩn đoán

- **Tìm thấy annotation bằng `findMergedAnnotation` nhưng advice không chạy:** kiểm tra biểu thức pointcut `@annotation`, proxy, vị trí annotation và liệu meta-annotation đã được liệt kê hay matcher có `checkInherited` chưa.
- **Advice chạy nhưng giá trị label vẫn là mặc định:** kiểm tra `@AliasFor`, `@Retention(RUNTIME)`, cách đọc annotation bằng reflection/merged API và mâu thuẫn giá trị mặc định.
- **`@annotation` trên class không match:** nó nhắm method; phân biệt với `@within` và `@target`.
- **Annotation trên interface hoạt động ở test này nhưng hỏng ở test khác:** xác định loại proxy và method thực sự được resolve; đừng giả định implementation luôn mang annotation của interface.
- **Test pass dù advice bị bỏ qua:** chỉ assert kết quả business mà không ghi nhận invocation của advice; thêm counter/recorder để test thất bại khi pointcut không match.
- **Tăng phạm vi `execution(* *(..))` để chữa lỗi:** có thể khiến quá nhiều bean/method bị proxy và nhiều lời gọi vào advice; cần chọn đúng scope và có test hiệu năng hoặc tích hợp khi mở rộng.
- **Dùng `@AliasFor` như công cụ mở rộng pointcut:** alias chỉ giải quyết metadata tổng hợp, không tự viết lại biểu thức AspectJ.

## 9. Checklist áp dụng

1. Ghi rõ annotation gốc và annotation tổng hợp được đặt ở **method** hay **type**, có `RUNTIME` retention hay không.
2. Quyết định rõ **bộ máy khớp pointcut**: AspectJ-style `@annotation` hay Spring `AnnotationMatchingPointcut`/custom advisor.
3. Tạo ít nhất ba method mẫu: đánh dấu trực tiếp, đánh dấu composite, không đánh dấu; assert **advice invocation** cho từng method.
4. Kiểm tra `MergedAnnotations`/`AnnotatedElementUtils` độc lập với kiểm tra proxy để biết vấn đề nằm ở metadata hay matching.
5. Test interface/class proxy, override và self-invocation khi cấu trúc ứng dụng có các trường hợp đó.
6. Kiểm tra nhãn/thuộc tính từ `@AliasFor`, cả giá trị tùy chỉnh và mặc định.
7. Đặt phạm vi pointcut đủ hẹp, tránh intercept toàn ứng dụng chỉ để tự lọc metadata sau đó.

**Tiêu chí hoàn thành:** Team có test cho thấy rõ phương thức nào bị Spring AOP advice intercept, phương thức nào chỉ được Spring annotation API nhận diện, và việc hỗ trợ composed annotation là quyết định thiết kế được xác minh bằng test.

## Tài liệu tham khảo

- Spring Framework — Declaring a Pointcut: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Spring Framework — `AliasFor` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.6/javadoc-api/org/springframework/core/annotation/AliasFor.html
- Spring Framework — `AnnotationMatchingPointcut` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.6/javadoc-api/org/springframework/aop/support/annotation/AnnotationMatchingPointcut.html
- Spring Framework — `MergedAnnotations` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.6/javadoc-api/org/springframework/core/annotation/MergedAnnotations.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-aspect-annotations-006">Phân biệt đọc metadata bằng API Spring với khớp biểu thức AspectJ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt đọc metadata bằng API Spring với khớp biểu thức AspectJ trong Spring AOP

## 1. Mục tiêu và phạm vi

Khi xây dựng annotation riêng cho một Spring bean, cần tách **hai việc**:

- **Đọc metadata bằng API Spring:** chủ động tra cứu xem một `Method`/`Class` mang annotation gì, giá trị thuộc tính là gì, có meta-annotation hay `@AliasFor` nào được Spring tổng hợp (merge) không.
- **Khớp biểu thức AspectJ trong Spring AOP:** khai báo pointcut để Spring chọn method execution nào sẽ chạy advice khi lời gọi **đi qua Spring proxy**. Ví dụ `@Around("@annotation(marker)")`.

**Tìm thấy annotation không đồng nghĩa advice chắc chắn chạy.** Hai cách có thể cùng xét annotation đó nhưng khác đầu vào, chính sách tìm kiếm và thời điểm kích hoạt.

Phạm vi chỉ gồm **Spring Aspect / Spring AOP dựa trên proxy** và API xử lý annotation của Spring; không trình bày lý thuyết AspectJ chung hoặc weaving của AspectJ compiler/load-time agent.

## 2. So sánh trực tiếp

| Tiêu chí | API Spring đọc metadata | Pointcut AspectJ trong Spring AOP |
|---|---|---|
| Mục tiêu | Lấy annotation hoặc giá trị cấu hình | Chọn join point mà advice áp dụng |
| Kích hoạt | Code của ứng dụng gọi API | Spring dựng advisor/proxy và intercept lời gọi phù hợp |
| Đối tượng xem xét | `AnnotatedElement`, `Method`, `Class<?>` | Method execution, target class và proxy |
| Công cụ điển hình | `AnnotatedElementUtils`, `AnnotationUtils`, `AopUtils`, `BridgeMethodResolver` | `@annotation`, `@within`, `@target`, `execution`, `bean` |
| Kết quả | Annotation, metadata hoặc `null` | Khớp/không khớp, có thể bind annotation vào advice |
| Thời điểm | Bất cứ khi nào code đọc, kể cả method chưa gọi | Khi cơ chế Spring AOP áp dụng advice cho lời gọi qua proxy |
| Annotation tổng hợp | `findMergedAnnotation` hiểu merge/meta-annotation/alias theo quy tắc Spring | Không mặc định tương đương chính sách “merged annotation” của API Spring |
| Lỗi hay nhầm | Đọc nhầm method từ interface hoặc proxy | Annotation tồn tại nhưng self-invocation, không có proxy, hoặc sai pointcut |

Ví dụ: `AnnotatedElementUtils.findMergedAnnotation(method, TraceAction.class)` có thể trả annotation trước khi `method` chạy. Trong khi đó, `@Around("@annotation(traceAction)")` chỉ chạy nếu Spring AOP thực sự intercept một method execution khớp.

## 3. Đọc metadata đúng cách với Spring

### 3.1. Reflection Java và API annotation của Spring

Với annotation đặt trực tiếp trên method, reflection thông thường có thể đọc:

```java
Method method = OrderService.class.getMethod("checkout", String.class);
TraceAction marker = method.getAnnotation(TraceAction.class);
```

Nếu ứng dụng dùng meta-annotation/composed annotation, alias thuộc tính hoặc cần tìm annotation theo hierarchy, Spring cung cấp các công cụ:

```java
TraceAction marker =
        AnnotatedElementUtils.findMergedAnnotation(method, TraceAction.class);
```

Một số API không đồng nhất về chính sách tìm:

- `AnnotatedElementUtils.getMergedAnnotation(...)`: lấy annotation đã merge từ phần tử với meta-annotation phù hợp.
- `AnnotatedElementUtils.findMergedAnnotation(...)`: tìm rộng hơn trên cấu trúc phân cấp tương ứng và tạo annotation đã merge.
- `AnnotationUtils.findAnnotation(...)`: tiện ích tra annotation theo cơ chế tìm riêng của Spring; không nên giả định nó luôn giống mọi hoạt động của `findMergedAnnotation`.
- `MergedAnnotations`: chọn `SearchStrategy` và duyệt metadata chi tiết khi quy tắc tìm kiếm cần rõ ràng.

`@AliasFor` chỉ có ý nghĩa theo cách xử lý synthesized/merged annotation của Spring. Một biểu thức `@annotation` trong AspectJ pointcut không tự động áp dụng y hệt mọi quy tắc tìm kiếm/merge của những API đó.

### 3.2. Class proxy khác class đích

Bean đã được bọc bởi Spring AOP có thể là JDK dynamic proxy hoặc subclass proxy. `bean.getClass()` không nhất thiết là lớp khai báo method nghiệp vụ.

Nếu cần đọc annotation trên implementation:

```java
Class<?> targetClass = AopUtils.getTargetClass(bean);
Method method = targetClass.getMethod("checkout", String.class);
TraceAction marker =
        AnnotatedElementUtils.findMergedAnnotation(method, TraceAction.class);
```

`AopUtils.getTargetClass` giúp lấy kiểu đích từ proxy Spring; từ đó đọc method triển khai thay vì nhầm method của proxy. Trong các ứng dụng có interface và overriding, vị trí đặt annotation vẫn phải được xem xét rõ.

Khi method ban đầu đến từ interface/signature, có thể tìm method cụ thể nhất và xử lý bridge do compiler sinh ra cho generic:

```java
Method interfaceMethod = /* Method đã có từ signature/interface */;
Class<?> targetType = AopUtils.getTargetClass(bean);
Method specificMethod =
        AopUtils.getMostSpecificMethod(interfaceMethod, targetType);
Method userMethod =
        BridgeMethodResolver.findBridgedMethod(specificMethod);
TraceAction marker =
        AnnotatedElementUtils.findMergedAnnotation(userMethod, TraceAction.class);
```

Đây là đoạn minh họa **nằm trong code đã có biến `bean` và `interfaceMethod`**, không phải chương trình Java độc lập. `AopUtils.getMostSpecificMethod` và `BridgeMethodResolver` xử lý hai vấn đề khác nhau: tìm method implementation và giải quyết bridge method.

### 3.3. Đọc được không có nghĩa là proxy intercept được

API metadata đọc một đối tượng phản chiếu, kể cả khi method không bao giờ được gọi. Spring AOP chỉ chạy advice khi method execution thuộc phạm vi pointcut và **lời gọi đi qua proxy**. Một đoạn startup scan đọc hết annotation trong package cũng không làm những method ấy được advice thực thi.

## 4. Khớp pointcut trong Spring Aspect

Đối với method annotation, cách rõ nhất là:

```java
@Around(value = "@annotation(traceAction)", argNames = "traceAction")
public Object trace(ProceedingJoinPoint pjp, TraceAction traceAction)
        throws Throwable {
    System.out.println("AOP: " + traceAction.value());
    return pjp.proceed();
}
```

- `@annotation(traceAction)` xác định method execution khớp annotation và **bind instance annotation** vào tham số `traceAction`.
- `argNames` giúp tên biến binding được xác định tường minh. `ProceedingJoinPoint` là tham số đặc biệt của around advice.
- `pjp.proceed()` cho method gốc tiếp tục thực thi. Bỏ lệnh đó trong `@Around` sẽ làm thay đổi hành vi method.
- Nếu chỉ lọc theo kiểu annotation, có thể dùng tên kiểu đầy đủ `@annotation(demo.TraceAction)`.

Các biểu thức Spring AOP hỗ trợ có liên quan:

| Biểu thức | Đối tượng khớp | Áp dụng |
|---|---|---|
| `@annotation(demo.TraceAction)` | Annotation gắn với method execution | Chính sách theo method marker |
| `@within(demo.AuditedType)` | Execution trong type được đánh dấu, theo quy tắc pointcut | Chính sách theo khai báo type |
| `@target(demo.AuditedType)` | Runtime target type có annotation tương ứng | Chính sách theo kiểu target |
| `execution(public * demo.service..*(..))` | Chữ ký method trong package | Giới hạn nhóm method |
| `bean(orderService)` | Spring bean có tên `orderService` | Giới hạn bean (mở rộng riêng của Spring) |

**Đặt `@TraceAction` trên class không có nghĩa nó khớp `@annotation` của method.** Nếu annotation thuộc type, hãy dùng đúng pointcut thuộc type và hiểu sự khác nhau giữa `@within`/`@target`.

Có thể phối hợp các biểu thức:

```java
@Around(value =
        "execution(public * demo.service..*(..)) && @annotation(traceAction)",
        argNames = "traceAction")
public Object trace(ProceedingJoinPoint pjp, TraceAction traceAction)
        throws Throwable {
    return pjp.proceed();
}
```

Đây là ví dụ đặt trong class `@Aspect` đã import các kiểu tương ứng. `demo.service` chỉ là package mẫu, cần đổi theo dự án thật. Kết hợp `execution` để tránh advice khớp quá rộng.

## 5. Ví dụ Java 17+ chạy được với Spring Boot

Ví dụ này dùng Spring Boot 3.3.x, Spring AOP và annotation `@TraceAction` để chứng minh hai đường xử lý độc lập. Các file đặt đúng đường dẫn; Java package chung là `demo`.

**`settings.gradle`**

```groovy
rootProject.name = 'spring-aop-metadata-demo'
```

**`build.gradle`**

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'demo'
version = '1.0.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
}
```

**`src/main/java/demo/TraceAction.java`**

```java
package demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TraceAction {
    String value();
}
```

`RetentionPolicy.RUNTIME` là yêu cầu để đọc annotation trong runtime và để pointcut dựa trên annotation hoạt động. `@Target(METHOD)` xác định đây là marker của method.

**`src/main/java/demo/OrderService.java`**

```java
package demo;

import org.springframework.stereotype.Service;

@Service
public class OrderService {
    @TraceAction("checkout")
    public String checkout(String orderId) {
        return "OK:" + orderId;
    }
}
```

**`src/main/java/demo/MetadataExplorer.java`**

```java
package demo;

import java.lang.reflect.Method;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

@Component
public class MetadataExplorer {
    private final OrderService orderService;

    public MetadataExplorer(OrderService orderService) {
        this.orderService = orderService;
    }

    public void showMetadata() throws NoSuchMethodException {
        Class<?> targetClass = AopUtils.getTargetClass(orderService);
        Method method = targetClass.getMethod("checkout", String.class);
        TraceAction marker =
                AnnotatedElementUtils.findMergedAnnotation(
                        method, TraceAction.class);
        System.out.println(
                "METADATA: " + (marker == null ? "missing" : marker.value()));
    }
}
```

`showMetadata()` chỉ đọc metadata của `checkout`; nó **không gọi `checkout`**, do đó không kích hoạt advice của method này.

**`src/main/java/demo/TrackingAspect.java`**

```java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TrackingAspect {
    @Around(value = "@annotation(traceAction)", argNames = "traceAction")
    public Object trace(ProceedingJoinPoint pjp, TraceAction traceAction)
            throws Throwable {
        System.out.println("AOP: " + traceAction.value());
        return pjp.proceed();
    }
}
```

Spring tạo advisor/proxy từ `@Aspect` khi `spring-boot-starter-aop` hiện diện và cấu hình AOP của Boot được kích hoạt. Advice chỉ áp dụng cho method execution khớp `@annotation`, có bind marker để đọc `value()`.

**`src/main/java/demo/AopMetadataDemoApplication.java`**

```java
package demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AopMetadataDemoApplication implements CommandLineRunner {
    private final MetadataExplorer explorer;
    private final OrderService orderService;

    public AopMetadataDemoApplication(
            MetadataExplorer explorer, OrderService orderService) {
        this.explorer = explorer;
        this.orderService = orderService;
    }

    public static void main(String[] args) {
        SpringApplication.run(AopMetadataDemoApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        explorer.showMetadata();
        System.out.println("RESULT: " + orderService.checkout("A1"));
    }
}
```

Chạy với Gradle Wrapper tương thích đã có của dự án:

```bash
./gradlew bootRun
```

Windows: `.\gradlew.bat bootRun`. Với dự án mới chưa có wrapper, phải tạo hoặc cung cấp `gradlew`/`gradlew.bat`; chỉ viết `build.gradle` không tự sinh wrapper.

Các **dòng ứng dụng** được in theo thứ tự (Spring Boot có thể in thêm log khởi động):

```text
METADATA: checkout
AOP: checkout
RESULT: OK:A1
```

Diễn giải: `METADATA` xuất hiện trước lời gọi phương thức vì Spring API đọc được annotation; `AOP` xuất hiện khi lời gọi `orderService.checkout` đi qua proxy và khớp pointcut; `RESULT` là giá trị method sau `pjp.proceed()`.

Nếu xóa `@TraceAction` khỏi `checkout` và không thêm advisor khác, metadata in `missing` và lời gọi không chạy advice `trace`. Nếu chỉ chạy `explorer.showMetadata()` mà không gọi `checkout`, vẫn đọc được metadata nhưng không có dòng `AOP`.

## 6. Các tình huống dễ gây hiểu sai

### 6.1. Self-invocation: annotation có nhưng advice không chạy

Giả sử thêm vào `OrderService`:

```java
public String process(String id) {
    return checkout(id); // tương đương this.checkout(id)
}
```

Một bean khác gọi `orderService.process("A1")`. Lời gọi nội bộ từ `process` đến `checkout` thực hiện trực tiếp trong target, không đi qua Spring AOP proxy; advice của `checkout` không chạy vì lời gọi này. Tuy nhiên `AnnotatedElementUtils.findMergedAnnotation` vẫn tìm được `@TraceAction` trên method `checkout`.

Thiết kế rõ ràng thường là đưa method được advice vào một bean mà caller gọi thông qua Spring proxy. Đây là đặc điểm **Spring AOP proxy-based interception**, không phải dấu hiệu annotation bị mất.

### 6.2. Annotation trên interface và implementation

Với JDK dynamic proxy, signature có thể biểu diễn method ở interface; annotation thực tế có thể nằm ở implementation hoặc ngược lại. Khi dùng `MethodSignature` trong advice để tra cứu, method trả về không nhất thiết là method của target.

Mẫu tra cứu bên trong advice (biến `pjp` đã có):

```java
Method signatureMethod =
        ((org.aspectj.lang.reflect.MethodSignature)
                pjp.getSignature()).getMethod();
Class<?> targetType =
        org.springframework.aop.support.AopUtils.getTargetClass(pjp.getTarget());
Method specific =
        org.springframework.aop.support.AopUtils.getMostSpecificMethod(
                signatureMethod, targetType);
Method bridged =
        org.springframework.core.BridgeMethodResolver.findBridgedMethod(specific);
TraceAction marker =
        org.springframework.core.annotation.AnnotatedElementUtils
                .findMergedAnnotation(bridged, TraceAction.class);
```

Đây là **đoạn minh họa trong advice**, không phải file Java độc lập. Với annotation chỉ đặt trên interface/hierarchy, phải test pointcut trên đúng kiểu proxy và vị trí khai báo. `findMergedAnnotation` có thể thấy annotation theo quy tắc tìm kiếm Spring, nhưng điều đó không tự đảm bảo `@annotation` khớp y hệt.

### 6.3. Composed annotation / meta-annotation

Nếu định nghĩa một composed annotation được Spring hợp nhất bằng `@AliasFor`, `findMergedAnnotation` có thể trả annotation tổng hợp mà reflection đơn giản không trả. Không vì thế mà giả định biểu thức `@annotation(demo.TraceAction)` cũng thừa hưởng toàn bộ ngữ nghĩa merged của Spring.

Có hai hướng thiết kế: (1) dùng annotation trực tiếp mà pointcut nhắm tới; (2) dùng pointcut `execution` phù hợp rồi lọc metadata trong advice bằng `AnnotatedElementUtils`. Hướng (2) có phạm vi intercept rộng hơn và cần cân nhắc hiệu năng. Chỉ chọn cách này khi thực sự cần ngữ nghĩa tìm/merge của Spring.

### 6.4. Giới hạn interception của Spring AOP

- Chỉ những bean do Spring quản lý mới được Spring AOP tạo proxy/advisor; `new OrderService()` không nhận advice của container.
- Spring AOP hỗ trợ method execution, không chặn field access, constructor execution hoặc join point khác như weaving AspectJ.
- Private method không thể được tư vấn qua cơ chế proxy; final method không thể override trong subclass proxy. Với interface-based proxy, các method được bộc lộ qua interface.
- Khớp annotation cần retention runtime; `@Retention(SOURCE)` hoặc `CLASS` không phù hợp để kiểm tra annotation theo runtime trong ví dụ.
- Annotation như `@Transactional` có hệ thống attribute lookup/advisor của Spring Transaction, không nên mô phỏng tùy tiện bằng một `@annotation(Transactional)` đơn giản rồi cho rằng kết quả giống hệt.

### 6.5. Có cần reflection trong mỗi lời gọi advice?

Nếu đã bind `@annotation(traceAction)`, dùng trực tiếp `traceAction.value()`. Chỉ truy ngược `Method` khi cần đọc thêm metadata của target class, alias/composed annotation, method generic hoặc các annotation khác. Với advice tần suất cao, tránh lặp reflection không cần thiết; cache theo method/target khi có đo lường và đảm bảo khóa cache đúng.

## 7. Checklist khi metadata có nhưng Spring Aspect không chạy

1. Lời gọi đến bean có đi **qua Spring proxy** không, hay gọi `new` / `this.method()`?
2. Pointcut dùng `@annotation` cho method hay `@within`/`@target` cho type? Annotation thực tế đặt ở đâu?
3. Annotation có `@Retention(RUNTIME)` và target element phù hợp chưa?
4. Có đang nhầm method trên interface/proxy với implementation method không? Đã kiểm tra `getMostSpecificMethod` và bridge method chưa?
5. Method có thuộc phạm vi Spring AOP hỗ trợ, có đủ khả năng được intercept theo proxy strategy không?
6. Nếu dùng meta-annotation hoặc `@AliasFor`, đã **kiểm thử pointcut** độc lập với kết quả `findMergedAnnotation` chưa?
7. `@Aspect` có là Spring bean và dependency/cấu hình Spring AOP đã kích hoạt chưa?
8. Có đang nhầm “đọc thấy annotation tại startup” với “method thực sự được gọi qua proxy” không?

**Điểm cần nắm:** API Spring trả lời “metadata có gì trên class/method tôi đang kiểm tra?”; pointcut AspectJ trong Spring AOP trả lời “lời gọi method nào qua Spring proxy khớp để chạy advice?”. Cùng annotation nhưng hai câu hỏi, hai cơ chế và những giới hạn khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-aspect-annotations-007">Thiết kế thuộc tính annotation rõ nghĩa và dễ kiểm thử</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế thuộc tính annotation rõ nghĩa và dễ kiểm thử cho Spring Aspect

## 1. Annotation là hợp đồng cấu hình giữa nghiệp vụ và Spring Aspect

Trong Spring AOP, annotation tùy chỉnh giúp người viết service **khai báo ý định** tại phương thức, còn `@Aspect` chịu trách nhiệm thực thi quy tắc liên quan. Ví dụ, `@Audited(operation = Operation.CREATE, tag = "book")` tuyên bố rằng khi phương thức này được gọi **qua Spring proxy**, aspect cần ghi nhận kết quả của thao tác tạo sách.

Annotation chỉ chứa **metadata bất biến**; bản thân nó không tự chặn lời gọi, không tự ghi log và không bảo đảm Spring AOP áp dụng được nếu lời gọi không đi qua proxy. Để annotation dễ hiểu, dễ đổi và dễ kiểm thử, hãy thiết kế đồng thời ba phần:

1. **Hợp đồng công khai:** thuộc tính nào có nghĩa gì, bắt buộc hay tùy chọn, giá trị mặc định là gì.
2. **Cách Spring Aspect diễn giải:** pointcut nào chọn phương thức, advice nào đọc thuộc tính, khi thành công/thất bại làm gì.
3. **Bằng chứng bằng test:** kiểm tra metadata riêng và kiểm tra hành vi đi qua Spring proxy bằng một ứng dụng Spring nhỏ.

Một annotation tốt cần cho người đọc hiểu tác dụng ngay tại nơi sử dụng. Nó không nên buộc người đọc đoán một chuỗi rỗng, một số `0` hay vài cờ boolean mơ hồ sẽ khiến aspect hành xử ra sao.

## 2. Bắt đầu từ các câu hỏi thiết kế

Giả sử nhóm cần ghi nhận thao tác tại service. Bảng sau cho thấy một hợp đồng có thể kiểm chứng được:

| Thuộc tính | Kiểu | Bắt buộc | Ý nghĩa | Cách xác minh |
| --- | --- | --- | --- | --- |
| `operation()` | `Operation` (enum) | Có | Loại thao tác nghiệp vụ được ghi nhận | Kiểm tra enum trong annotation và trong bản ghi của aspect |
| `tag()` | `String` | Không, mặc định `""` | Nhãn ổn định để phân nhóm; rỗng nghĩa là không có nhãn | Kiểm tra cả nhãn có giá trị và giá trị mặc định |
| `logFailures()` | `boolean` | Không, mặc định `true` | Có ghi nhận lần thực thi ném lỗi hay không | Tạo lời gọi lỗi với cả `true` và `false` |

Các nguyên tắc gắn với Spring Aspect:

- **Đặt tên theo ý nghĩa quan sát được.** `logFailures` nói rõ nó điều khiển việc ghi nhận lỗi; `enabled` có thể gây hiểu nhầm là tắt luôn proxy, chặn, bảo mật hay chỉ tắt log.
- **Một thuộc tính, một trách nhiệm.** Không dùng `mode = "CREATE:STRICT:FULL"` rồi tách chuỗi trong advice; enum/thuộc tính riêng mang kiểu và lỗi biên dịch hữu ích hơn.
- **Bắt buộc khi không có mặc định an toàn.** Nếu `operation` quyết định loại sự kiện, để không có `default` buộc nơi sử dụng chọn rõ.
- **Mặc định phải ổn định và được tài liệu hóa.** Thêm `logFailures() default true` mang nghĩa cụ thể; thay mặc định sau này có thể đổi hành vi mọi phương thức không khai báo thuộc tính ấy.
- **Không mặc định ghi tham số nhạy cảm.** Dữ liệu ghi nhận cần được chọn lọc ở aspect; một thuộc tính như `includeArgs = true` dễ vô tình đưa mật khẩu, token hoặc thông tin cá nhân vào log.
- **Dùng `value()` khi thực sự chỉ có một giá trị chính.** Khi có nhiều thuộc tính hoặc cần dễ đọc tại nơi gọi, `operation = ...` rõ nghĩa hơn tên rút gọn. Không tạo cả `value` và `operation` như hai tên đồng nghĩa nếu chưa thiết kế quy tắc alias rõ ràng.

### Hai cách viết và tác động đến người đọc

```java
// Khó hiểu: khó biết 2, true và "x" nghĩa gì ở aspect.
@AuditFlag(level = 2, enabled = true, value = "x")

// Rõ hơn: cấu hình nêu trực tiếp loại thao tác và trường hợp lỗi.
@Audited(operation = Operation.CREATE, tag = "book", logFailures = true)
```

Đây là đoạn minh họa ý tưởng, không phải hai khai báo annotation chạy độc lập. Điều cần tránh là đặt một thuộc tính trông rõ nghĩa nhưng thực tế bị aspect bỏ qua. **Mọi thuộc tính được công bố phải có một hành vi hoặc quy tắc kiểm chứng cụ thể.**

## 3. Khai báo annotation đúng phạm vi Spring AOP

Đối với annotation đánh dấu **phương thức service** để aspect chặn:

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {
    Operation operation();             // Bắt buộc
    String tag() default "";           // Rỗng: không có nhãn
    boolean logFailures() default true; // Ghi sự kiện thất bại
}
```

- `@Retention(RUNTIME)` cho phép Spring/AspectJ đọc metadata khi chạy. Với `SOURCE` hay `CLASS`, cấu hình pointcut dựa trên annotation tại runtime không thể hoạt động như mong đợi.
- `@Target(METHOD)` thu hẹp lời hứa: aspect chỉ đánh dấu method execution, không áp dụng tùy ý lên class, field hay parameter.
- `@Documented` đưa annotation vào tài liệu Javadoc của phần tử được chú thích; nó không tự tạo advice.
- Enum `Operation` giới hạn giá trị tại lúc biên dịch, tránh lỗi chính tả của các chuỗi `"CREATE"` / `"CRETAE"`.
- Tên `logFailures` mô tả chính xác việc **ghi sự kiện**. Dù `false`, exception của nghiệp vụ **vẫn phải được ném tiếp**; annotation không biến lỗi thành thành công.

**Giới hạn quan trọng:** Spring AOP dựa trên proxy và chủ yếu chặn method execution trên Spring beans. `private`, `final` và self-invocation (`this.someMethod()`) có giới hạn theo kiểu proxy. `@Target(METHOD)` cũng không có nghĩa mọi lời gọi Java vào method được đánh dấu đều bị chặn.

## 4. Ví dụ đầy đủ: annotation, aspect và service

Ví dụ sau dùng **Java 17+, Spring Framework 6.2.8 và JUnit 5**. Nó gồm một annotation có thuộc tính bắt buộc/mặc định, aspect đọc đúng các thuộc tính, một service trả kết quả, một service method ném lỗi và một method không có annotation. Bộ ghi nhận được lưu trong bộ nhớ để test có thể so sánh dữ liệu thực tế thay vì phụ thuộc cách hiển thị console log.

Cấu trúc dự án:

```text
spring-aop-annotation-contract/
├── pom.xml
├── src/main/java/example/AuditAnnotationApp.java
└── src/test/java/example/AuditAnnotationAppTest.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>spring-aop-annotation-contract</artifactId>
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
        <version>3.5.0</version>
      </plugin>
    </plugins>
  </build>
</project>
```

`src/main/java/example/AuditAnnotationApp.java`:

```java
package example;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class AuditAnnotationApp {
    enum Operation {
        CREATE, FIND
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Audited {
        Operation operation();
        String tag() default "";
        boolean logFailures() default true;
    }

    record AuditEvent(Operation operation, String tag, String outcome) {}

    static class AuditRecorder {
        private final List<AuditEvent> events = new CopyOnWriteArrayList<>();

        void record(AuditEvent event) {
            events.add(event);
        }

        List<AuditEvent> snapshot() {
            return List.copyOf(events);
        }
    }

    static class BookService {
        @Audited(operation = Operation.CREATE, tag = "book")
        public String create(String title) {
            return "book:" + title.toUpperCase(Locale.ROOT);
        }

        @Audited(operation = Operation.FIND, logFailures = false)
        public String find(String id) {
            throw new IllegalArgumentException("not-found:" + id);
        }

        public String plain() {
            return "plain";
        }
    }

    @Aspect
    static class AuditAspect {
        private final AuditRecorder recorder;

        AuditAspect(AuditRecorder recorder) {
            this.recorder = recorder;
        }

        @Around(value = "@annotation(audited)", argNames = "audited")
        public Object around(
                ProceedingJoinPoint joinPoint,
                Audited audited) throws Throwable {

            try {
                Object result = joinPoint.proceed();
                recorder.record(new AuditEvent(
                        audited.operation(), audited.tag(), "SUCCESS"));
                return result;
            } catch (Throwable error) {
                if (audited.logFailures()) {
                    recorder.record(new AuditEvent(
                            audited.operation(), audited.tag(), "FAILURE"));
                }
                throw error; // Giữ nguyên lỗi và hợp đồng của service.
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class Config {
        @Bean
        BookService bookService() {
            return new BookService();
        }

        @Bean
        AuditRecorder auditRecorder() {
            return new AuditRecorder();
        }

        @Bean
        AuditAspect auditAspect(AuditRecorder recorder) {
            return new AuditAspect(recorder);
        }
    }

    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {
            BookService service = context.getBean(BookService.class);

            System.out.println("result=" + service.create("spring"));

            try {
                service.find("404");
            } catch (IllegalArgumentException e) {
                System.out.println("caught=" + e.getMessage());
            }

            System.out.println("plain=" + service.plain());
            System.out.println("events="
                    + context.getBean(AuditRecorder.class).snapshot());
        }
    }
}
```

Chạy ứng dụng:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.AuditAnnotationApp
```

Kết quả stdout của logic ví dụ:

```text
result=book:SPRING
caught=not-found:404
plain=plain
events=[AuditEvent[operation=CREATE, tag=book, outcome=SUCCESS]]
```

Vì `create` mang `@Audited(operation = CREATE, tag = "book")`, aspect ghi một sự kiện thành công với nhãn `book`. Vì `find` đặt `logFailures = false`, phương thức **vẫn ném lỗi**, nhưng recorder không thêm sự kiện thất bại. Vì `plain` không có annotation, pointcut không chọn nó. Bài tập mở rộng đơn giản: bỏ `logFailures = false` ở `find`; mặc định `true` khiến recorder thêm `AuditEvent[operation=FIND, tag=, outcome=FAILURE]` khi method ném lỗi.

Đây là ví dụ ghi sự kiện trong bộ nhớ để kiểm chứng hợp đồng, không phải hệ thống audit production: ghi sự kiện vào kho lưu trữ thực tế cần xử lý độ bền, giao dịch, tải, dữ liệu nhạy cảm và thất bại của chính kênh audit theo yêu cầu nghiệp vụ.

## 5. Hai lớp kiểm thử: metadata và hành vi qua proxy

Một test chỉ dùng reflection có thể chứng minh `operation` hoặc `logFailures` được khai báo đúng; nó **chưa chứng minh Spring Aspect thực sự đọc và áp dụng** những thuộc tính đó. Ngược lại, một integration test chỉ gọi service có thể bỏ sót việc mặc định của annotation bị đổi ngoài ý muốn. Vì vậy cần cả hai.

`src/test/java/example/AuditAnnotationAppTest.java`:

```java
package example;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditAnnotationAppTest {

    @Test
    void annotationMetadataHasExplicitAndDefaultValues() throws Exception {
        Method create = AuditAnnotationApp.BookService.class
                .getMethod("create", String.class);
        AuditAnnotationApp.Audited creation =
                create.getAnnotation(AuditAnnotationApp.Audited.class);

        assertNotNull(creation);
        assertEquals(AuditAnnotationApp.Operation.CREATE, creation.operation());
        assertEquals("book", creation.tag());
        assertTrue(creation.logFailures()); // default true

        Method find = AuditAnnotationApp.BookService.class
                .getMethod("find", String.class);
        AuditAnnotationApp.Audited lookup =
                find.getAnnotation(AuditAnnotationApp.Audited.class);

        assertNotNull(lookup);
        assertEquals(AuditAnnotationApp.Operation.FIND, lookup.operation());
        assertEquals("", lookup.tag()); // default empty
        assertFalse(lookup.logFailures());

        Method plain = AuditAnnotationApp.BookService.class.getMethod("plain");
        assertNull(plain.getAnnotation(AuditAnnotationApp.Audited.class));
    }

    @Test
    void springProxyUsesAnnotationPropertiesAndPreservesErrors() {
        try (var context = new AnnotationConfigApplicationContext(
                AuditAnnotationApp.Config.class)) {
            var service = context.getBean(AuditAnnotationApp.BookService.class);
            var recorder = context.getBean(AuditAnnotationApp.AuditRecorder.class);

            assertEquals("book:SPRING", service.create("spring"));

            var failure = assertThrows(IllegalArgumentException.class,
                    () -> service.find("404"));
            assertEquals("not-found:404", failure.getMessage());

            assertEquals("plain", service.plain());

            assertEquals(
                    List.of(new AuditAnnotationApp.AuditEvent(
                            AuditAnnotationApp.Operation.CREATE,
                            "book",
                            "SUCCESS")),
                    recorder.snapshot());
        }
    }
}
```

Lệnh chạy:

```bash
mvn -q test
```

Hai test thể hiện hai lớp cam kết:

| Test | Bảo vệ hành vi nào? |
| --- | --- |
| `annotationMetadataHasExplicitAndDefaultValues` | Thuộc tính `operation` bắt buộc, `tag` mặc định rỗng, `logFailures` mặc định `true`, method không gắn annotation không bị đánh dấu |
| `springProxyUsesAnnotationPropertiesAndPreservesErrors` | Spring proxy chặn phương thức có annotation, aspect đọc thuộc tính, không ghi lỗi khi `logFailures=false`, giữ exception gốc và bỏ qua method thường |

**Test hợp đồng riêng biệt:** muốn xác minh mặc định bằng reflection một cách tường minh hơn, có thể đọc `Audited.class.getMethod("logFailures").getDefaultValue()` và so với `Boolean.TRUE`. Test hiện tại chứng minh mặc định qua metadata được Java cung cấp ở nơi sử dụng.

**Test đường đi proxy:** luôn lấy service bằng `context.getBean(...)`. Việc dùng `new BookService().create(...)` để test hành vi advice sẽ bỏ qua Spring proxy nên chỉ kiểm tra thân phương thức, không kiểm tra AOP.

## 6. Kiểm tra đầu vào và xử lý cấu hình không hợp lệ

Annotation là cấu hình viết tại thời điểm biên dịch, nhưng không phải kiểu dữ liệu nào cũng chặn được mọi giá trị không hợp lệ. Ví dụ `tag = "   "` là chuỗi hợp lệ của Java nhưng có thể không hợp lệ với hệ thống audit. Phải định nghĩa quy tắc một cách rõ ràng:

- Nếu `tag` **thực sự tùy chọn**, chuẩn hóa chuỗi rỗng thành “không gắn nhãn” ở một chỗ. Không diễn giải rỗng vừa là “bỏ qua audit” vừa là “lấy tên phương thức” ở những nhánh khác nhau.
- Nếu `tag` **bắt buộc**, bỏ `default` hoặc kiểm tra `isBlank()`. Đối với metadata cố định, kiểm tra sớm khi khởi tạo bean hoặc khi kiểm tra ứng dụng; không cần để lỗi trôi đến một request ngẫu nhiên.
- Nếu chỉ một tập giá trị hợp lệ, dùng `enum` thay chuỗi. Ví dụ `Operation.CREATE` được compiler kiểm tra; đổi tên enum sẽ bộc lộ các nơi dùng cần sửa.
- Nếu cần chọn đối tượng áp dụng chính sách, dùng thuộc tính nêu rõ phạm vi: `operation`, `resourceType`; tránh giá trị có nhiều nghĩa như `mode = 1`.
- Nếu lỗi cấu hình liên quan đến kiểm soát quyền hoặc bảo mật, thiết kế rõ nguyên tắc **từ chối hay cho qua khi lỗi**; không để aspect tự mặc định cho qua vì một thuộc tính trống.

**Phân biệt hai loại thất bại:** (1) giá trị annotation sai nên application không có cấu hình đáng tin; (2) method nghiệp vụ ném exception khi cấu hình hợp lệ. `logFailures=false` trong ví dụ chỉ điều khiển ghi bản ghi của loại (2), không tắt xác thực cấu hình và không nuốt exception.

### Ràng buộc tốt và không tốt

```java
// Dễ kiểm thử: enum có tập giá trị đóng và bắt buộc.
@Audited(operation = Operation.CREATE, tag = "book")

// Dễ gây tranh cãi: “default” là sự kiện CREATE, READ
// hay “tự suy ra tên method”?
// @Audited(value = "default", options = "true,false,2")
```

Phần thứ hai là phản ví dụ về thiết kế, không phải annotation đã được định nghĩa trong chương trình runnable.

## 7. Annotation trên interface, override và annotation tổng hợp

### Nên đặt annotation ở đâu?

Một hệ thống có interface service và implementation có thể đặt annotation ở một trong hai vị trí. Nhưng proxy JDK và proxy dựa trên lớp nhìn thấy các phương thức khác nhau; reflection bằng `joinPoint.getSignature()` có thể trả phương thức interface thay vì implementation. Cách tránh lỗi:

1. Quy định **vị trí chuẩn** của annotation (ví dụ trên implementation method) cho module.
2. Viết integration test chạy với **kiểu proxy mà ứng dụng thật dùng**.
3. Nếu advice cần tra cứu metadata từ method, dùng `MethodSignature` để lấy method, `AopUtils.getMostSpecificMethod(method, targetClass)` để tìm implementation, và API annotation của Spring phù hợp thay cho đoán rằng `getAnnotation()` ở interface luôn đủ.
4. Đừng coi `@Inherited` là giải pháp cho method override: `@Inherited` chỉ có tác dụng trong quy tắc kế thừa annotation **trên class**, không tự kế thừa annotation từ method cha sang method override.

`@annotation(audited)` trong ví dụ là **pointcut có ràng buộc giá trị annotation**, nên Spring cung cấp một đối tượng `Audited` đã match trực tiếp vào advice. Nếu cần xử lý nhiều nơi khai báo annotation (interface, implementation, meta-annotation), phải thiết kế **cả quy tắc pointcut lẫn quy tắc đọc metadata** đồng bộ.

### Composed annotation và `@AliasFor` không tự áp dụng cho mọi aspect

Spring cung cấp `@AliasFor` và API `MergedAnnotations` / `AnnotatedElementUtils` để hỗ trợ annotation tổng hợp, override và alias ở những thành phần được thiết kế để đọc theo cơ chế merged annotations. Tuy nhiên:

- Chỉ thêm một meta-annotation `@Audited` vào annotation mới **không đảm bảo** pointcut `@annotation(audited)` hiện tại chọn mọi method dùng annotation tổng hợp đó như khi gắn `@Audited` trực tiếp.
- Chỉ khai báo `@AliasFor` **không khiến Java reflection thông thường** (`getAnnotation()`) tự hợp nhất alias. Thành phần đọc phải dùng cơ chế merged annotations tương ứng của Spring.
- Nếu mở rộng `Audited` để làm meta-annotation, `@Target` của nó cần cho phép `ANNOTATION_TYPE` ngoài `METHOD` và thiết kế điểm match tương ứng.
- Với hệ thống có cả annotation trực tiếp và tổng hợp, nên dùng một phương thức giải nghĩa chính sách nhất quán, kiểm thử xung đột/default/alias; chỉ triển khai khi có nhu cầu thật sự.

Các trường hợp này ảnh hưởng **hợp đồng Spring AOP**, không chỉ cú pháp annotation Java. Việc thay `@annotation` bằng một pointcut khác mà không có test hồi quy có thể vô tình mở rộng hoặc thu hẹp phạm vi chặn.

## 8. Thay đổi annotation mà không phá vỡ các service đã sử dụng

Annotation là một API của module. Ví dụ:

```java
// V1
@interface Recorded {
    Operation operation();
}

// V2: thuộc tính mới có default giúp phần lớn source cũ tiếp tục biên dịch.
@interface Recorded {
    Operation operation();
    boolean logFailures() default true;
}
```

Với hai khai báo minh họa này, thêm một thuộc tính **không có mặc định** sẽ buộc cập nhật mọi nơi sử dụng `@Recorded` khi biên dịch lại. Ngay cả thuộc tính có `default`, đổi giá trị mặc định có thể đổi hành vi tất cả method cũ. Khi thay đổi hợp đồng:

- Giữ giá trị mặc định cũ nếu cần tương thích ngược.
- Tránh đổi tên thuộc tính hoặc enum constant vô cớ; những tên này là phần của API mà compiler sử dụng.
- Test cả nơi sử dụng **tường minh** và nơi **dựa vào mặc định**.
- Không biến thuộc tính từng chỉ dùng để log thành điều kiện cho phép thực thi mà không định nghĩa lại hợp đồng, vì đó là thay đổi ý nghĩa lớn.
- Nếu cần chính sách khác biệt thật sự, cân nhắc annotation mới hoặc enum rõ ràng thay cho tích lũy nhiều cờ boolean khó tổ hợp.

## 9. Danh sách kiểm thử nâng cao cho Spring Aspect

| Tình huống | Kết quả cần kiểm chứng |
| --- | --- |
| Có annotation, thuộc tính bắt buộc được khai báo | Advice đọc đúng enum và thực hiện đúng chính sách |
| Không khai báo thuộc tính tùy chọn | Aspect nhận giá trị `default` đúng tài liệu |
| Thuộc tính boolean `false` | Chỉ hành vi được chỉ rõ bị tắt, lỗi nghiệp vụ không bị nuốt |
| Method không có annotation | Không bị aspect tương ứng chặn |
| Lời gọi qua Spring bean/proxy | Advice thực sự được thực thi |
| Lời gọi `this.method()` trong cùng target | Không dựa vào đó để kiểm thử interception mặc định |
| Exception ở method | Advice dọn tài nguyên và ném lại lỗi; ghi hay không ghi theo chính sách |
| Method có overload / implementation trên interface | Chọn đúng phương thức và annotation với kiểu proxy thực tế |
| Annotation tổng hợp, alias, inheritance | Test khả năng match và độ ưu tiên; không tự giả định sẽ hoạt động |
| Nhiều advice cùng match | Thứ tự có thể tác động kết quả; kiểm tra `@Order`/advisor liên quan nếu cần |
| Bất đồng bộ hoặc reactive | Ghi nhận thời điểm trả object hay thời điểm tác vụ hoàn tất phải theo hợp đồng đã xác định |

Nếu annotation dùng để đo latency, định nghĩa rõ đơn vị và phạm vi: `@Around` với method đồng bộ có thể đo thời gian `proceed()`; với `CompletableFuture` hoặc reactive publisher, thời điểm trả future/publisher **không đồng nghĩa** thời điểm nghiệp vụ hoàn tất. Nếu yêu cầu ghi sự kiện khi hoàn tất bất đồng bộ, advice cần xử lý kết quả bất đồng bộ phù hợp, có test riêng.

Nếu annotation kiểm soát truy cập, test cả lời gọi bị từ chối và lỗi khi thu thập policy; không dùng ví dụ ghi audit làm minh chứng rằng một kiểm tra bảo mật đã an toàn. Khi nhiều aspect tương tác với `@Transactional`, `@Async` hoặc Spring Security, thứ tự advice và ranh giới proxy/luồng cần được kiểm tra trực tiếp.

## 10. Cách đánh giá một thiết kế hoàn chỉnh

Một annotation Spring Aspect được coi là rõ nghĩa khi người dùng đọc `@Audited(operation = CREATE, tag = "book")` hiểu ngay mục đích của nó; các mặc định có ý nghĩa cố định; Java compiler hạn chế nhập sai; aspect có một nơi giải nghĩa; lỗi được xử lý đúng hợp đồng; và test chứng minh cả metadata, đường đi proxy, trường hợp không match và tình huống exception.

Thực hành: thêm phương thức `remove(String id)` với `@Audited(operation = DELETE, tag = "book")` sau khi mở rộng enum `Operation`. Viết test kiểm tra có **một** sự kiện `DELETE` khi gọi qua bean và **không** có sự kiện khi gọi method không gắn annotation. Sau đó thêm một tùy chọn mới có `default`, viết test bảo đảm mọi lời gọi cũ giữ nguyên hành vi. Qua đó bạn kiểm tra được việc thiết kế annotation như **hợp đồng ổn định giữa service và Spring Aspect**, thay vì chỉ kiểm tra annotation có mặt bằng reflection.

### Tài liệu Spring chính thức

- Spring AOP và phạm vi proxy: https://docs.spring.io/spring-framework/reference/core/aop.html
- Pointcut `@annotation` và các designator Spring hỗ trợ: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Binding annotation và `argNames` trong advice: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Proxy và self-invocation: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring `@AliasFor` và cách diễn giải annotation tổng hợp: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/annotation/AliasFor.html
- `AnnotatedElementUtils`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/annotation/AnnotatedElementUtils.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-aspect-annotations-008">Không biến annotation thành nơi chứa quy tắc nghiệp vụ khó theo dõi</a>

<details>
<summary>Xem chi tiết</summary>

# Không biến annotation thành nơi chứa quy tắc nghiệp vụ khó theo dõi trong Spring AOP

Khi viết custom annotation cho **Spring AOP**, một sai lầm dễ gặp là gom các quyết định nghiệp vụ vào annotation rồi để aspect âm thầm thực thi. Cú pháp trông ngắn gọn, nhưng người đọc method không thấy điều kiện nào quyết định kết quả; việc kiểm thử, thay đổi chính sách và tìm lỗi cũng trở nên khó khăn. Annotation nên thể hiện **ý định gắn một mối quan tâm cắt ngang** (cross-cutting concern), còn quy tắc quyết định nghiệp vụ nên xuất hiện rõ trong service hoặc lớp policy chuyên trách.

Phạm vi bài này là cách thiết kế **custom annotation kết hợp Spring AOP proxy và advice**. Các khái niệm AOP tổng quát không cần học lại ở đây.

## 1. Ranh giới trách nhiệm trong ứng dụng Spring

| Thành phần | Trách nhiệm nên có | Điều nên tránh |
| --- | --- | --- |
| Custom annotation, ví dụ `@AuditAction("create-order")` | Đánh dấu điểm cần áp dụng audit; mang metadata nhỏ, ổn định, dễ hiểu | Mã hóa bảng giá, phân hạng khách hàng, nhiều điều kiện quyết định duyệt đơn |
| Spring `@Aspect` và advice | Xử lý logging/audit/tracing/metric theo cùng một cách tại các method được đánh dấu | Quyết định đơn hàng hợp lệ, tính phí, đổi kết quả trả về vì chính sách kinh doanh |
| `OrderService` | Điều phối ca sử dụng, gọi policy một cách **tường minh** | Trông cậy vào aspect để gọi hộ kiểm tra nghiệp vụ |
| `OrderPolicy` (hoặc domain service) | Đặt quy tắc ở một nơi có tên rõ ràng, kiểm thử độc lập với Spring proxy | Dồn quy tắc vào chuỗi tên annotation hay xử lý vòng quanh method một cách khó thấy |

Spring AOP thường áp dụng advice khi lời gọi method **đi qua Spring proxy**. Vì vậy nếu bảo vệ một bất biến nghiệp vụ chỉ bằng aspect, lời gọi không đi qua proxy có thể bỏ qua kiểm tra. Một service có lời gọi trực tiếp tới policy thì vẫn thể hiện quy tắc ngay trong luồng nghiệp vụ.

## 2. Phản ví dụ: annotation mang cả chính sách kinh doanh

Giả sử ứng dụng cần kiểm tra đơn hàng trên 200 đơn vị tiền tệ chỉ được tạo bởi khách premium. Thiết kế sau là **minh họa điều cần tránh**, không phải chương trình chạy độc lập:

```java
// API gây hiểu nhầm: chính sách được đẩy vào metadata trên method.
@AcceptOrder(maxAmountForBasicMember = 200, requirePremiumAboveLimit = true)
public String createOrder(int amount, boolean premium) {
    return "APPROVED";
}
```

Nếu một `@Around` advice đọc các tham số này rồi âm thầm chặn đơn hàng, thân `createOrder` có vẻ luôn chấp nhận. Vấn đề thực tế:

- **Luồng điều khiển bị giấu:** từ method không thể thấy việc kiểm tra hạng khách hàng, nơi phát sinh exception hoặc quy tắc ưu tiên giữa các điều kiện.
- **Chính sách khó thay đổi:** thêm khuyến mãi, hạn mức theo khu vực hoặc theo thời điểm buộc mở rộng annotation và aspect, tạo ra một bộ máy nghiệp vụ ngoài service.
- **Kiểm thử dễ sai tầng:** test gọi trực tiếp đối tượng service bằng `new` sẽ không chạy Spring advice; kết quả có thể khác khi bean đi qua proxy.
- **Tái sử dụng khó:** API khác cần cùng quy tắc nhưng không sử dụng cùng annotation hoặc proxy sẽ phải lặp logic.
- **Khó truy vết:** người bảo trì cần lần theo pointcut và advice mới biết vì sao thao tác bị từ chối.

Tham số annotation không tự động trở thành xấu: tên sự kiện audit, tag metric hay tên thao tác là metadata hợp lý. Điểm quyết định là **nó có điều khiển kết quả nghiệp vụ hay không**.

## 3. Thiết kế khuyến nghị: policy rõ ràng, annotation phục vụ audit

Ví dụ dưới đây dùng **Java 17+**, một ứng dụng **Spring Boot 3.x** có `spring-boot-starter-aop`. Cấu trúc:

```text
src/main/java/com/example/aopaudit/
├── DemoApplication.java
├── AuditAction.java
├── AuditAspect.java
├── OrderPolicy.java
└── OrderService.java
```

Trong dự án Spring Boot dùng Maven, thêm dependency này nếu chưa có (phiên bản được quản lý bởi Spring Boot parent/BOM):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

Với Gradle, dependency tương đương là `implementation 'org.springframework.boot:spring-boot-starter-aop'`. Dự án cũng cần Spring Boot starter để chạy ứng dụng. Với cấu hình Boot thông dụng, starter AOP cung cấp các annotation `@Aspect` và support cần thiết cho Spring proxy.

**Tệp `AuditAction.java` — annotation chỉ là dấu hiệu audit:**

```java
package com.example.aopaudit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditAction {
    String value();
}
```

`@Target(METHOD)` giới hạn annotation vào method. `@Retention(RUNTIME)` cho phép Spring/AspectJ nhận diện nó trong quá trình thực thi. `value` là tên hành động phục vụ quan sát, **không phải hạn mức hay điều kiện duyệt đơn**.

**Tệp `OrderPolicy.java` — nơi thực thi quyết định nghiệp vụ:**

```java
package com.example.aopaudit;

import org.springframework.stereotype.Component;

@Component
public class OrderPolicy {
    public void check(int amount, boolean premium) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount > 200 && !premium) {
            throw new IllegalArgumentException(
                "Premium required for amount over 200"
            );
        }
    }
}
```

Đây là ví dụ chính sách đơn giản có chủ ý: amount phải dương; nếu amount lớn hơn 200, khách phải premium. Trong hệ thống thật, quy tắc có thể sử dụng một domain model và các lớp policy chuyên biệt. Dù chính sách phức tạp hơn, cách gọi vẫn minh bạch.

**Tệp `OrderService.java` — gọi policy ngay trong method:**

```java
package com.example.aopaudit;

import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final OrderPolicy policy;

    public OrderService(OrderPolicy policy) {
        this.policy = policy;
    }

    @AuditAction("create-order")
    public String createOrder(int amount, boolean premium) {
        policy.check(amount, premium);
        return "APPROVED";
    }
}
```

Ngay cả khi bạn đọc riêng `OrderService`, bạn vẫn thấy `policy.check(...)` được gọi trước khi chấp nhận. Annotation chỉ yêu cầu bổ sung audit; bỏ annotation không làm thay đổi quy tắc duyệt đơn.

**Tệp `AuditAspect.java` — advice chỉ quan sát kết quả:**

```java
package com.example.aopaudit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    @Around("@annotation(auditAction)")
    public Object audit(ProceedingJoinPoint joinPoint, AuditAction auditAction)
            throws Throwable {
        String action = auditAction.value();
        System.out.println("AUDIT start " + action);
        try {
            Object result = joinPoint.proceed();
            System.out.println("AUDIT success " + action);
            return result;
        } catch (Throwable failure) {
            System.out.println(
                "AUDIT failure " + action + ": " +
                    failure.getClass().getSimpleName()
            );
            throw failure;
        }
    }
}
```

`@Around("@annotation(auditAction)")` chọn các method có `@AuditAction` và bind annotation vào tham số advice. Gọi `joinPoint.proceed()` đúng một lần; trả về giá trị gốc và ném lại lỗi gốc. Aspect **không thay đổi** điều kiện premium, không trả về trạng thái giả và không nuốt exception. `System.out` chỉ để minh họa đầu ra dễ quan sát; ứng dụng thực tế nên dùng logger/audit sink phù hợp, tránh ghi dữ liệu cá nhân hoặc bí mật.

**Tệp `DemoApplication.java` — chạy hai tình huống:**

```java
package com.example.aopaudit;

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
    CommandLineRunner demo(OrderService orderService) {
        return args -> {
            System.out.println(
                "RESULT " + orderService.createOrder(150, false)
            );

            try {
                orderService.createOrder(500, false);
            } catch (IllegalArgumentException failure) {
                System.out.println("REJECTED " + failure.getMessage());
            }
        };
    }
}
```

Chạy trong dự án Boot đã cấu hình dependency:

```bash
./mvnw spring-boot:run
# Hoặc với dự án Gradle:
./gradlew bootRun
```

Trên Windows dùng `mvnw.cmd spring-boot:run` hoặc `gradlew.bat bootRun`. Ngoài log khởi động của Spring, phần output do ví dụ tạo ra theo thứ tự:

```text
AUDIT start create-order
AUDIT success create-order
RESULT APPROVED
AUDIT start create-order
AUDIT failure create-order: IllegalArgumentException
REJECTED Premium required for amount over 200
```

Ở lần gọi đầu, policy cho phép 150 với khách thường. Ở lần hai, `OrderPolicy.check` ném lỗi; aspect ghi nhận thất bại rồi ném lại lỗi để caller xử lý. Không có quyết định nghiệp vụ nào được tạo trong aspect.

> Giả định khi chạy ví dụ: dùng dependency quản lý bởi Spring Boot 3.x và build có thông tin tên tham số để bind `auditAction` (cấu hình chuẩn của Spring Boot build thông thường). Nếu dùng build tùy biến và gặp lỗi không xác định tên tham số advice, bật compiler flag `-parameters` hoặc khai báo `argNames` theo hướng dẫn Spring AOP.

## 4. Refactor khi đã có annotation chứa nghiệp vụ

Nếu code hiện tại dùng annotation kiểu `@AcceptOrder(...)`, nên chuyển từng trách nhiệm về đúng chỗ:

1. **Liệt kê hành vi thật sự:** xác định điều kiện nào chấp nhận/từ chối đơn, exception nào được ném, phụ thuộc vào dữ liệu nào, và logging/audit đang làm gì.
2. **Trích xuất chính sách thành API có tên nghiệp vụ:** ví dụ `OrderPolicy.check(amount, premium)` hoặc `EligibilityPolicy.evaluate(customer, order)`, với input/output minh bạch.
3. **Gọi policy trực tiếp từ use case/service:** tất cả entry point tạo đơn cần đi qua nghiệp vụ này; viết test cho các trường hợp biên.
4. **Giữ annotation cho concern kỹ thuật:** nếu vẫn cần audit, đổi thành metadata kiểu `@AuditAction("create-order")`; aspect chỉ log thành công/thất bại.
5. **Xóa logic nghiệp vụ khỏi advice sau khi đã đối chiếu hành vi:** không để kiểm tra tồn tại hai nơi với hai chính sách khác nhau.

Ví dụ khác: `@ApplyDiscount(level = "VIP", percentage = 15)` khiến aspect tự sửa giá là dấu hiệu quy tắc giá bị che giấu. Service nên gọi `pricingPolicy.calculatePrice(...)`; một `@TrackOperation("price-quote")` có thể đánh dấu thời điểm cần metric mà không quyết định giá.

## 5. Đặc thù Spring AOP cần nhớ

- **Self-invocation:** gọi `this.createOrder(...)` từ method khác trong cùng bean thường không đi qua Spring proxy, vì thế `@AuditAction` có thể không kích hoạt advice. Bất biến nghiệp vụ vẫn phải dựa vào lời gọi `policy.check(...)` trong service. Đừng dùng AOP proxy như hàng rào duy nhất cho chính sách.
- **Method visibility và proxy:** Spring AOP advice chỉ hoạt động trên method có thể được proxy intercept; ví dụ ưu tiên public method trên bean do Spring quản lý. Các lời gọi qua `new OrderService(...)` không có audit advice, nhưng logic policy vẫn chạy.
- **Không dùng annotation để giả vờ bảo mật:** kiểm tra quyền truy cập có thể dùng cơ chế Spring Security như `@PreAuthorize` được cấu hình đúng, nhưng quy tắc miền như hạn mức chi tiêu hoặc khả năng đặt hàng cần có vị trí rõ ràng trong mô hình nghiệp vụ; đừng tự viết custom aspect mơ hồ để thay cơ chế bảo mật.
- **Thứ tự advice và transaction:** nếu audit cần lưu chắc chắn sau khi transaction commit, một `@Around` log “success” khi method trả về chưa chắc đồng nghĩa dữ liệu đã commit. Cân nhắc application event sau commit hoặc `@TransactionalEventListener(phase = AFTER_COMMIT)` theo nhu cầu thực tế.
- **Giữ metadata nhỏ và ổn định:** tên sự kiện, loại audit, tag observability có thể là attribute annotation; quy tắc thường xuyên thay đổi theo khách hàng, thị trường hoặc dữ liệu nên đặt trong service/policy.
- **Minh bạch lỗi:** advice quan sát thất bại, giữ nguyên exception và kết quả; chỉ biến đổi kết quả khi đó là hành vi cross-cutting được thiết kế, đặt tên và kiểm thử rõ ràng.

## 6. Kiểm thử để bảo đảm annotation không nắm quyền nghiệp vụ

Phần quyết định nên test **không cần Spring context**, nhờ đó kết quả không phụ thuộc cách gọi qua proxy. Ví dụ JUnit 5 cho `OrderService`, có sẵn nếu thêm `spring-boot-starter-test` vào scope test:

```java
package com.example.aopaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class OrderServiceTest {
    private final OrderService service =
        new OrderService(new OrderPolicy());

    @Test
    void regularCustomerCanPlaceSmallOrder() {
        assertEquals("APPROVED", service.createOrder(150, false));
    }

    @Test
    void regularCustomerCannotPlaceLargeOrder() {
        assertThrows(
            IllegalArgumentException.class,
            () -> service.createOrder(500, false)
        );
    }

    @Test
    void amountMustBePositive() {
        assertThrows(
            IllegalArgumentException.class,
            () -> service.createOrder(0, true)
        );
    }
}
```

Test này cố ý khởi tạo trực tiếp `OrderService`: không có proxy hay aspect, nhưng policy vẫn hoạt động. Test riêng dạng Spring integration, ví dụ `@SpringBootTest` cùng bean thật, để xác nhận advice bắt được annotation khi đi qua proxy và không làm đổi kết quả/exception. Đừng để audit log là bằng chứng duy nhất rằng đơn hàng đã được duyệt đúng.

## 7. Checklist rà soát thiết kế

- Khi đọc method service, có thấy lời gọi đến lớp quyết định nghiệp vụ, điều kiện và lỗi quan trọng không?
- Xóa hoặc tắt annotation audit có làm khác kết quả nghiệp vụ không? **Không nên.**
- Gọi service trực tiếp không qua Spring proxy có bỏ qua chính sách miền không? **Không được.**
- Aspect có chỉ thực thi concern đã được nêu rõ, và có trả kết quả/ném lỗi chính xác từ `proceed()` không?
- Attribute annotation có chỉ là metadata kỹ thuật, thay vì công thức/điều kiện dùng để tính giá, duyệt đơn hoặc thay đổi trạng thái không?
- Có unit test cho policy độc lập và integration test riêng cho Spring AOP interception không?

**Nguyên tắc áp dụng:** Với custom annotation trong Spring AOP, hãy để annotation trả lời *“method này cần áp dụng concern nào?”*, aspect trả lời *“concern đó được xử lý ra sao?”*, còn service/policy trả lời *“nghiệp vụ có cho phép và phải làm gì?”*. Nhờ vậy code thể hiện đúng luồng xử lý, test được cả khi không chạy Spring và ít phát sinh luật ngầm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-aspect-annotations-009">Giữ API annotation ổn định khi tái sử dụng aspect giữa các module</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ API annotation ổn định khi tái sử dụng Spring AOP aspect giữa các module

## 1. Vấn đề cần giải quyết

Khi nhiều module Spring dùng chung một aspect, annotation mà developer đặt lên service chính là **API công khai giữa module nghiệp vụ và hạ tầng AOP**. Ví dụ module đặt hàng dùng `@TrackedOperation("checkout")`, module thanh toán dùng `@TrackedOperation("charge")`; cả hai mong aspect nhận diện cùng một contract dù triển khai logging, metrics hoặc tracing thay đổi. Nếu đổi package, tên annotation, tên thuộc tính hay ý nghĩa thuộc tính một cách tùy tiện, source code có thể không biên dịch, code đã biên dịch có thể lỗi lúc chạy hoặc advice có thể ngừng áp dụng mà không báo lỗi rõ ràng.

Mục tiêu: module sử dụng annotation chỉ phụ thuộc vào **contract ổn định**, còn Spring AOP aspect và cách đăng ký bean có thể được cập nhật riêng; mọi thay đổi contract được kiểm tra và phát hành có kiểm soát. Trọng tâm ở đây là cách Spring AOP phát hiện và áp dụng annotation trong ứng dụng đa module.

## 2. Tách contract annotation khỏi Spring aspect

Một cách tổ chức với Java 17+/Spring Boot 3.x:

```text
operation-contract/            # JAR API: annotation công khai, không cần Spring
  com/example/contract/TrackedOperation.java

operation-spring/              # JAR runtime: @Aspect và Spring Boot auto-configuration
  com/example/spring/TrackedOperationAspect.java
  com/example/spring/TrackedOperationAutoConfiguration.java
  META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

checkout-app/                   # ứng dụng Spring Boot dùng annotation
  com/example/checkout/CheckoutService.java
  com/example/checkout/CheckoutApplication.java

billing-app/                    # một ứng dụng khác cũng có thể dùng cùng contract
```

Quan hệ phụ thuộc: `operation-spring → operation-contract`; từng ứng dụng phụ thuộc vào `operation-contract` để biên dịch và `operation-spring` để bật hành vi AOP. Ứng dụng cần `spring-boot-starter-aop` trong runtime (hoặc cấu hình Spring AOP tương đương). `operation-contract` là JAR nhỏ chỉ chứa contract; không đặt `@Aspect`, `@Component`, logger hay bean cấu hình vào JAR này.

Điểm ổn định là **fully qualified class name** (FQCN) của annotation cùng các phần tử và ngữ nghĩa của nó. Tên implementation `TrackedOperationAspect`, loại sink hoặc cách xuất log là chi tiết nội bộ của module Spring.

## 3. Khai báo contract cho proxy-based Spring AOP

Tệp `operation-contract/src/main/java/com/example/contract/TrackedOperation.java`:

```java
package com.example.contract;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrackedOperation {
    String value();
    String category() default "business";
}
```

- `RUNTIME` là điều kiện để Spring AOP có thể đọc annotation tại runtime. Đổi sang `CLASS` hoặc `SOURCE` sẽ khiến advice dựa vào `@annotation` không còn nhận ra annotation trong runtime thông thường.
- `METHOD` xác định lời hứa công khai: annotation áp dụng cho method. Nếu sau này muốn hỗ trợ cả class, phải đồng thời thiết kế pointcut phù hợp; thêm `TYPE` vào `@Target` **không tự động** khiến `@annotation` áp dụng cho mọi method trong class.
- `value()` là tên thao tác nghiệp vụ bắt buộc và cần giữ ngữ nghĩa nhất quán; `category()` có giá trị mặc định để code cũ dùng `@TrackedOperation("checkout")` tiếp tục hoạt động.
- `@Documented` hỗ trợ tài liệu JavaDoc, không phải điều kiện kích hoạt AOP. Không tự thêm `@Inherited` để mong annotation trên method được kế thừa: `@Inherited` của Java chỉ có ý nghĩa với annotation đặt trên class, không giải quyết proxy hoặc method override.

Nếu các module được phát hành độc lập, công bố contract trong artifact có phiên bản rõ ràng; giữ package `com.example.contract` và tên phần tử ổn định qua các bản nâng cấp tương thích.

## 4. Aspect Spring dùng chính contract đã công bố

Tệp `operation-spring/src/main/java/com/example/spring/TrackedOperationAspect.java`:

```java
package com.example.spring;

import com.example.contract.TrackedOperation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TrackedOperationAspect {

    @Around(value = "@annotation(tracked)", argNames = "tracked")
    public Object record(ProceedingJoinPoint joinPoint,
                         TrackedOperation tracked) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            System.out.printf(
                    "[track] %s category=%s status=SUCCESS%n",
                    tracked.value(), tracked.category());
            return result;
        } catch (Throwable error) {
            System.out.printf(
                    "[track] %s category=%s status=FAILURE type=%s%n",
                    tracked.value(), tracked.category(),
                    error.getClass().getSimpleName());
            throw error;
        }
    }
}
```

`@annotation(tracked)` là pointcut Spring AOP đối với method execution được proxy xử lý và có annotation tương ứng; `tracked` còn được bind vào advice để lấy thuộc tính từ contract. `joinPoint.proceed()` gọi logic nghiệp vụ đúng một lần và trả nguyên kết quả; khi lỗi, advice ném lại đúng `Throwable` ban đầu để không làm thay đổi hợp đồng lỗi của service. `argNames` giúp việc bind annotation rõ ràng, không lệ thuộc vào thông tin tên tham số của compiler.

Ví dụ dùng `System.out` nhằm thấy kết quả trực tiếp; trong sản phẩm thực tế, thay bằng logger, metrics hoặc một sink chuyên trách. Tránh log tham số chứa mật khẩu/dữ liệu nhạy cảm. Việc in `SUCCESS` tại lúc method trả về **không chứng minh giao dịch `@Transactional` đã commit**: thời điểm advice chạy phụ thuộc vào thứ tự interceptor. Nếu yêu cầu audit bền vững sau commit, thiết kế theo transaction/event riêng và kiểm tra thứ tự advice.

## 5. Đăng ký aspect ở đúng ranh giới module

Spring Boot 3.x có thể auto-configure aspect từ JAR `operation-spring`, không bắt từng ứng dụng quét thủ công package của library. Tệp `operation-spring/src/main/java/com/example/spring/TrackedOperationAutoConfiguration.java`:

```java
package com.example.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class TrackedOperationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TrackedOperationAspect.class)
    TrackedOperationAspect trackedOperationAspect() {
        return new TrackedOperationAspect();
    }
}
```

Tệp resource **đúng đường dẫn** `operation-spring/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` chứa duy nhất dòng:

```text
com.example.spring.TrackedOperationAutoConfiguration
```

Đóng gói JAR phải giữ resource này. Khi ứng dụng có dependency `operation-spring` và `spring-boot-starter-aop`, Spring Boot nạp auto-configuration và có cơ chế tạo Spring AOP proxy. Aspect được quản lý như một Spring bean, còn module nghiệp vụ chỉ cần import annotation từ `operation-contract`.

Điều kiện sử dụng: hai JAR phải cùng phiên bản contract tương thích; không trộn các bản chứa **hai class annotation khác FQCN** rồi mong `@annotation` tự hiểu chúng là một. Khi library cần cho phép vô hiệu hóa tính năng, bổ sung property có tên ổn định cùng `@ConditionalOnProperty`, công bố default và kiểm tra ứng dụng cũ trước khi đổi default. Nếu không dùng Boot auto-configuration, ứng dụng có thể `@Import` một `@Configuration` khai báo aspect; chọn một cơ chế nhất quán để tránh tạo hai bean/advice tương đương.

## 6. Ứng dụng sử dụng annotation mà không biết chi tiết aspect

Tệp `checkout-app/src/main/java/com/example/checkout/CheckoutService.java`:

```java
package com.example.checkout;

import com.example.contract.TrackedOperation;
import org.springframework.stereotype.Service;

@Service
public class CheckoutService {

    @TrackedOperation("checkout")
    public String checkout() {
        return "OK";
    }
}
```

Tệp `checkout-app/src/main/java/com/example/checkout/CheckoutApplication.java`:

```java
package com.example.checkout;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CheckoutApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckoutApplication.class, args);
    }

    @Bean
    CommandLineRunner demo(CheckoutService service) {
        return args -> System.out.println(service.checkout());
    }
}
```

Đây là các file Java cho ví dụ đa module; chúng chạy trong dự án Spring Boot đã cấu hình dependency và plugin build tương ứng, không phải chương trình có thể chạy bằng `javac` thuần túy vì sử dụng Spring và AspectJ. Khi Gradle của dự án đã thiết lập Boot và module path như trên, có thể chạy `./gradlew :checkout-app:bootRun` (Windows: `gradlew.bat :checkout-app:bootRun`).

Bên cạnh các dòng khởi động của Spring Boot, với cấu hình proxy hợp lệ sẽ thấy hai dòng do ví dụ sinh ra, theo đúng thứ tự:

```text
[track] checkout category=business status=SUCCESS
OK
```

Module `billing-app` có thể chỉ đặt `@TrackedOperation("charge")` và dùng các dependency tương ứng; không cần sao chép source `TrackedOperationAspect` hay tạo một annotation trùng tên trong package khác.

## 7. Các thay đổi contract: an toàn hay phá vỡ tương thích?

| Thay đổi | Rủi ro với các module sử dụng | Cách xử lý |
| --- | --- | --- |
| Giữ FQCN, chữ ký `value()`, `@Retention(RUNTIME)`, `@Target(METHOD)` và ngữ nghĩa | Thường tương thích | Có thể nâng implementation aspect riêng |
| Thêm phần tử `String category() default "business"` | Code dùng annotation trước đó vẫn có giá trị hợp lệ | Test lại hành vi khi default mới được áp dụng |
| Thêm phần tử **không** có default | Các lần biên dịch source annotation cũ bị lỗi thiếu phần tử; runtime với class cũ cũng có thể lỗi khi đọc | Coi là breaking change; thiết kế default hoặc major version |
| Đổi `value()` thành `operation()` hoặc xóa phần tử | Source dùng tên cũ hỏng; aspect và binary cũ có thể lỗi | Giữ alias cũ trong giai đoạn chuyển tiếp hoặc phát hành major version |
| Đổi package hoặc tên `TrackedOperation` | FQCN mới là một annotation khác; pointcut cũ không match | Có giai đoạn chấp nhận cả annotation cũ và mới, sau đó migrate có kiểm soát |
| Đổi `RUNTIME` thành `CLASS`; thu hẹp `@Target` | AOP không nhận diện hoặc source ngừng biên dịch | Không thay nếu vẫn cam kết chức năng cũ |
| Đổi giá trị default, ý nghĩa của `value`, tên operation được ghi nhận | Có thể không lỗi biên dịch nhưng đổi logs/metrics/tracing hoặc dashboard | Xem là thay đổi hành vi API, test và thông báo migration |
| Chỉ đổi implementation logging, sink, nội bộ advice | Annotation của caller không cần thay | Kiểm tra tương thích observable behavior và thứ tự advice |

Ngay cả khi một thay đổi hợp lệ về mặt binary Java, tương thích **hành vi Spring AOP** vẫn cần kiểm thử: aspect có thể đổi pointcut, điều kiện đăng ký bean hoặc thứ tự advisor mà không làm compile lỗi. Không xem việc build thành công là bằng chứng aspect còn thực thi.

### Chiến lược phát hành/migration

1. Xuất bản `operation-contract` như một API có phiên bản, ghi rõ vị trí đặt annotation, ý nghĩa từng thuộc tính, default và phạm vi áp dụng.
2. Ưu tiên **bổ sung phần tử tùy chọn có default**; không đổi hay xóa phần tử đang được caller dùng. Nếu cần thay nghĩa, đặt tên mới có ý nghĩa rõ và đánh dấu phần tử cũ `@Deprecated` trong thời gian chuyển đổi.
3. Khi buộc đổi annotation FQCN, duy trì annotation cũ và thêm handler rõ ràng cho cả hai trong phiên bản chuyển tiếp; kiểm tra tránh ghi nhận sự kiện hai lần nếu một method gắn cả hai. Chỉ bỏ đường cũ tại phiên bản major với hướng dẫn migration.
4. Với ứng dụng độc lập, khóa/căn chỉnh phiên bản `operation-contract` giữa compile classpath và runtime classpath; phát hành aspect Spring kèm dải phiên bản contract được hỗ trợ.
5. Giữ test tương thích với một ứng dụng dùng annotation theo cú pháp cũ; không chỉ test consumer sau khi đã sửa theo API mới.

## 8. Bẫy Spring AOP dễ làm nhầm thành lỗi API

Spring AOP dựa trên proxy, nên giữ nguyên annotation contract vẫn chưa đủ nếu lời gọi không đi qua proxy:

- **Self-invocation:** `this.checkout()` từ method khác trong cùng bean không đi qua proxy Spring AOP. Đưa thao tác cần intercept sang bean riêng hoặc gọi từ một bean bên ngoài.
- **Đối tượng không do Spring quản lý:** tự `new CheckoutService()` sẽ không có AOP proxy.
- **Final/private method và proxy:** private method không được Spring AOP intercept; final method không thể được CGLIB subclass proxy override. Với JDK dynamic proxy, lời gọi phải đi qua interface của proxy. Ưu tiên public method được gọi qua Spring bean.
- **Đặt annotation ở interface/implementation:** vị trí annotation và loại proxy có thể ảnh hưởng matching. Thống nhất quy ước đặt `@TrackedOperation` trên public method của concrete service trong ví dụ này và có integration test nếu ứng dụng dùng interface-based proxy.
- **`@annotation` và `@within`/`@target`:** `@annotation` dùng cho annotation của method được match; nếu mở rộng contract cho annotation ở class, phải viết pointcut tương ứng và xác định khi cả class và method cùng gắn annotation thì ưu tiên cái nào.
- **Đăng ký thiếu runtime:** dependency contract cho phép compile nhưng không tự bật aspect. Kiểm tra JAR auto-configuration, starter AOP, bean aspect và thực tế proxy. Nếu ứng dụng không có starter AOP, annotation trở thành metadata thuần và không tạo hành vi mong đợi.
- **Nhiều aspect/advisor:** cùng một method có thể có `@Transactional`, `@Async` và nhiều advice. Khi timing, transaction hoặc kết quả sau commit là phần contract, phải kiểm tra thứ tự với `@Order`/cấu hình advisor và hành vi runtime thực tế.

## 9. Checklist kiểm thử khi nâng phiên bản shared aspect

Kiểm thử tích hợp nên khởi động Spring context của một ứng dụng thật, inject service từ context và gọi qua bean proxy. Các trường hợp tối thiểu: method được annotate đi qua advice đúng một lần; method không annotate không bị ghi nhận; thuộc tính mặc định `category="business"` được đọc đúng; giá trị custom đi qua pointcut binding; kết quả trả về không đổi; ngoại lệ gốc được ném lại; hoạt động qua hai module consumer khác nhau; cấu hình bật/tắt (nếu public API hỗ trợ) nhất quán; bản consumer dùng cú pháp annotation cũ vẫn chạy sau khi nâng JAR.

Có thể bổ sung test Spring bằng `AopUtils.isAopProxy(service)` để phát hiện thiếu proxy, nhưng kiểm tra proxy **chưa đủ**: cần assert sự kiện/log/metric từ advice để chứng minh pointcut thật sự match. Nếu output là log, dùng appender/sink giả trong test thay vì phụ thuộc chuỗi startup log của Boot. Đối với phần tử annotation mới, dùng test giữ nguyên source caller cũ và kiểm tra default.

## 10. Quy tắc áp dụng thực tế

- **Contract ổn định:** FQCN, `@Retention`, `@Target`, phần tử, default và ý nghĩa của annotation được coi là API có phiên bản.
- **Spring runtime thay đổi có kiểm soát:** aspect, pointcut, bean registration, thứ tự advisor và khả năng proxy được kiểm thử ở cấp tích hợp.
- **Consumer độc lập:** code nghiệp vụ chỉ import một annotation từ artifact dùng chung; không phụ thuộc vào tên hoặc cách khởi tạo aspect.
- **Migration rõ ràng:** thêm thuộc tính có default khi có thể; hỗ trợ song song có thời hạn nếu đổi tên/package; không đổi ngữ nghĩa âm thầm.

Giữ các ranh giới này giúp nhiều Spring module tái sử dụng một annotation mà không phải đồng loạt sửa source mỗi khi triển khai aspect thay đổi.


</details>

- [Quay lại đầu trang](#back-to-top)
