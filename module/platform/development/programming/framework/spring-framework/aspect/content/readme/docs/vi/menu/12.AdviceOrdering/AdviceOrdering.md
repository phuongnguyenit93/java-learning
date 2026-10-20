<a id="back-to-top"></a>

# Thứ tự thực thi

## Menu
- [Nhiều aspect cùng khớp một phương thức](#advice-ordering-001)
- [Độ ưu tiên bằng @Order và Ordered](#advice-ordering-002)
- [Giá trị order nhỏ hơn và vị trí trong chuỗi advice](#advice-ordering-003)
- [Chiều đi vào và chiều đi ra của chuỗi interceptor](#advice-ordering-004)
- [Thứ tự giữa các loại advice trong cùng một @Aspect](#advice-ordering-005)
- [Giới hạn đảm bảo thứ tự giữa nhiều advice cùng loại](#advice-ordering-006)
- [@After như finally so với @AfterReturning và @AfterThrowing](#advice-ordering-007)
- [Không suy diễn thứ tự từ vị trí phương thức trong mã nguồn](#advice-ordering-008)
- [Thứ tự giữa aspect tự viết và advisor hạ tầng Spring](#advice-ordering-009)
- [Bảng vết thực thi để xác nhận thứ tự khi thành công và khi lỗi](#advice-ordering-010)

## <a id="advice-ordering-001">Nhiều aspect cùng khớp một phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Nhiều aspect cùng khớp một phương thức trong Spring AOP

## 1. Bài toán và kết quả cần hiểu

Một phương thức của Spring service có thể đồng thời khớp nhiều pointcut đến từ **nhiều `@Aspect` bean**. Ví dụ, khi `OrderService.submit(..)` được caller gọi qua Spring proxy, ứng dụng có thể áp dụng logging, đo thời gian, audit, transaction hoặc custom validation thông qua những advisors khác nhau.

Với **Spring AOP theo cú pháp @AspectJ**, Spring xây một **chuỗi interceptor/advisor** cho mỗi proxy invocation phù hợp, chứ không tạo một bản sao riêng của target method cho từng aspect. Một lần gọi target có thể khiến nhiều advice thực thi theo thứ tự có quy tắc.

Những câu hỏi phải giải quyết:

1. Khi hai hoặc nhiều Spring `@Aspect` cùng match một method, **advice nào được gọi trước**?
2. Vì sao `@Order(10)` có thể chạy **trước khi vào** target nhưng **sau khi ra** target so với `@Order(20)`?
3. Với `@Around`, chuyện gì xảy ra nếu một aspect không gọi `proceed()`, tự ném lỗi, bắt lỗi, retry hoặc thay return value?
4. Nếu một aspect chỉ có `@Before` còn aspect khác dùng `@AfterReturning` hoặc `@AfterThrowing`, cần phân biệt gì về ngữ nghĩa outcome?
5. Thứ tự giữa **các aspects khác nhau** khác thế nào với thứ tự **nhiều advice cùng loại trong cùng một aspect**?
6. Làm sao kiểm chứng bằng Spring `ApplicationContext`, không suy đoán từ thứ tự method xuất hiện trong file Java?

Bài tập trung vào **Spring Aspect/Spring AOP proxy**, sự kết hợp nhiều `@Aspect`, `@Order`/`Ordered`, invocation chain và kiểm thử. Những khái niệm AOP tổng quát chỉ được nêu khi cần giải thích Spring.

## 2. Một Spring method, nhiều aspects nhưng chỉ một proxy boundary

Giả sử ba aspects đều có expression phù hợp với:

```java
public String submit(String id) {
    return "submitted:" + id;
}
```

Sơ đồ conceptual khi Spring AOP proxy nhận invocation:

```text
External caller
     |
     | service.submit("A")     (reference do Spring container trả về)
     v
Spring AOP proxy
     |
     +--> Aspect Outer / @Around / @Order(10)
     |      enter
     |         |
     |         +--> Aspect Gate / @Before / @Order(15)
     |                   |
     |                   +--> Aspect Inner / @Around / @Order(20)
     |                             enter
     |                               |
     |                               +--> target.submit("A")
     |                               |
     |                             exit
     |      exit
     v
Caller nhận kết quả hoặc exception
```

**Cần phân biệt:**

- `@Aspect` là class khai báo các advices/pointcuts; **aspect phải được đăng ký làm Spring bean** để Spring AOP sử dụng.
- Spring tạo một **proxy** (JDK dynamic proxy hoặc CGLIB class proxy) cho target bean đủ điều kiện và kết hợp các advisors áp dụng. Không nên suy mỗi aspect tạo thêm một proxy riêng trong mọi ứng dụng.
- Một target method có thể được advice bởi nhiều aspects, nhưng `@Around` có khả năng ngăn hoặc lặp lời gọi target; vì vậy **số lần target body chạy không phải lúc nào cũng bằng số lần proxy entry**.
- `@Order` điều khiển **precedence của aspect trong Spring**, không làm pointcut match nhiều hay ít hơn.
- Các advisors của Spring transaction/cache/security có thể cùng tồn tại với custom aspects, nhưng một bài học về `@Order` của custom aspects không tự quy định order của mọi infrastructure advisor.

**Mô hình đúng:** một lời gọi bên ngoài đi vào Spring proxy, nơi Spring chọn advisor chain có thể áp dụng. Nếu caller bypass proxy (`new` object, `this.inner()` trên target), việc có mười aspects match chữ ký method vẫn không tự tạo interception.

## 3. Thứ tự mặc định: không có @Order thì không dựa vào thứ tự tình cờ

Tài liệu Spring Framework nêu rõ: khi **hai pieces of advice trong hai aspects khác nhau** cùng muốn chạy ở một join point, nếu **không chỉ định precedence** thì thứ tự thực thi **không được xác định**.

Không được suy thứ tự từ:

- Tên file Java theo alphabet;
- Tên class aspect;
- Thứ tự `@Bean` factory methods;
- Thứ tự component scanning thường thấy;
- Thứ tự các methods trong một `@Aspect` class;
- Việc aspect “logging” hay “security” nghe có vẻ phải trước;
- Kết quả một lần chạy locally không có assertion/regression test.

Nếu behavior **phụ thuộc vào thứ tự**, phải dùng precedence rõ ràng rồi kiểm tra bằng test trên **Spring version, proxy mode và advisor configuration thật**.

Một số tracing/logging có thể không cần thứ tự cố định; nhưng masking secrets trước logging, authorization trước business effects, transaction boundary với audit và metrics thường đòi hỏi xem xét thứ tự kỹ.

## 4. @Order và Ordered trong Spring AOP

Trong Spring, có hai cách phổ biến để đặt precedence:

**Annotation trên aspect class:**

```java
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(10)
public class OuterAspect {
    // Các advice của aspect này có precedence theo 10.
}
```

**Hoặc implement `Ordered`:**

```java
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;

@Aspect
public class OuterAspect implements Ordered {
    @Override
    public int getOrder() {
        return 10;
    }
}
```

Hai đoạn **là hai lựa chọn riêng**, không định nghĩa cùng tên class trong cùng project. Cần thêm Spring bean registration (`@Component` hoặc `@Bean`) và advice thực sự để ảnh hưởng runtime.

**Quy tắc Spring: số order càng nhỏ thì precedence càng cao.** Ví dụ `@Order(10)` cao hơn `@Order(20)`; `Ordered.HIGHEST_PRECEDENCE` (giá trị cực nhỏ) và `Ordered.LOWEST_PRECEDENCE` (giá trị cực lớn) là hai mốc tham khảo.

| Precedence | Thứ tự khi vào (“on the way in”) | Thứ tự khi ra (“on the way out”) |
| --- | --- | --- |
| Aspect A, `@Order(10)` | Vào **trước** aspect B | Ra **sau** aspect B |
| Aspect B, `@Order(20)` | Vào **sau** aspect A | Ra **trước** aspect A |

Suy nghĩ theo **cặp ngoặc lồng nhau**:

```text
A.enter
    B.enter
        target
    B.exit
A.exit
```

Không dùng một câu đơn giản như “order càng thấp thì aspect luôn chạy trước” để mô tả cả enter lẫn exit. Nó chỉ đúng **ở chiều vào**; chiều ra có thứ tự đảo ngược.

### 4.1. @Order ở class aspect, không phải “thứ tự của mọi method”

Trong Spring `@AspectJ`, đặt `@Order` trên **aspect type** là cách trực quan để xác định precedence giữa aspects. Nếu trong một aspect có hai `@Before` methods match cùng join point, thêm cùng `@Order` trên class **không tự quyết định method nào của hai advice chạy trước**.

Đừng dán `@Order(1)` lên mỗi advice method rồi suy Spring bảo đảm nó sẽ trở thành order toàn cục theo ý muốn. Hãy dùng **hai aspect classes riêng**, một advice duy nhất có logic thứ tự tường minh, hoặc một orchestration pointcut phù hợp.

### 4.2. Hai aspect có cùng order value

Nếu A và B cùng `@Order(10)`, không được suy Spring sẽ xử lý A trước B một cách ổn định giữa mọi môi trường. Nếu thứ tự business-critical, **đặt giá trị khác nhau có chủ đích** và regression test, không dựa vào thứ tự registration.

### 4.3. Ordered có thể đến từ bean instance

`Ordered.getOrder()` cho phép giá trị order ở instance của aspect bean. Khi nhiều Spring beans và auto-proxy behavior tham gia, cần bảo đảm Spring thực sự nhận giá trị mong muốn từ aspect bean; nếu đang dùng dynamic configuration, test runtime không chỉ đọc class annotation.

Không lạm dụng dynamic order để thay chính sách nghiệp vụ theo từng request: precedence của advisor chain là một đặc tính của hạ tầng proxy, không phải context dữ liệu per-invocation để tự thay đổi tùy tiện.

## 5. @Around quan sát chuỗi lồng nhau rõ nhất

Khi A (`@Order(10)`) và B (`@Order(20)`) đều dùng `@Around` trên cùng Spring target method:

```java
@Around("execution(* com.example.orders.OrderService.submit(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    before();
    try {
        Object value = pjp.proceed();
        afterSuccess();
        return value;
    } catch (Throwable error) {
        afterFailure(error);
        throw error;
    } finally {
        cleanup();
    }
}
```

Đoạn này **minh họa**, chưa là Java file standalone (thiếu class, imports, các helper methods). Nó cho thấy một aspect có thể chạy **cả ở chiều vào và chiều ra**.

Kết quả với hai aspects thường mô tả được bằng:

```text
A.before        (Order 10)
B.before        (Order 20)
target.submit
B.afterSuccess  (Order 20)
B.cleanup       (Order 20)
A.afterSuccess  (Order 10)
A.cleanup       (Order 10)
```

Nếu target ném exception:

```text
A.before
B.before
target.submit -> throw
B.afterFailure
B.cleanup
A.afterFailure
A.cleanup
Caller nhận exception
```

Đây là **sơ đồ logic** khi hai `@Around` luôn gọi `proceed()` đúng một lần và ném lại nguyên exception. Cách chắc chắn nhất để học/kiểm chứng là chạy demo Spring AOP thực tế ở mục 7.

### 5.1. Không có bảo đảm target luôn chạy

`@Around` có quyền **không gọi `proceed()`**, hoặc gọi nhiều lần. Nếu aspect ngoài cùng short-circuit, những advisors nằm sau nó và target **có thể không chạy**, kể cả khi pointcut của chúng hoàn toàn match method.

Ví dụ minh họa không nên dùng khi không có contract:

```java
@Around("execution(* com.example.orders.OrderService.submit(..))")
public Object unsafeSkip(ProceedingJoinPoint pjp) {
    return "ok"; // Không hề gọi target hoặc những interceptor downstream!
}
```

Một aspect có precedence cao hơn không chỉ “ghi log sớm hơn”: nó có thể quyết định liệu downstream advice có được vào chain hay không. Điều này liên quan trực tiếp tới thứ tự của security, transaction, retry và cache interceptors trong Spring.

## 6. Hai vấn đề cần tách biệt: matching và ordering

Một aspect chỉ tham gia invocation nếu:

1. Nó là **Spring-managed `@Aspect` bean** có advice được hạ tầng nhận diện;
2. Pointcut match method execution/bean trong Spring context;
3. Caller đi qua Spring proxy và method nằm trong phạm vi interception của JDK/CGLIB;
4. Những interceptors có precedence cao hơn **cho phép chain tiếp tục** tới nó;
5. Nếu advice có điều kiện động (`args`, `@annotation`, `@target`, `@within`...) thì điều kiện tương ứng đúng ở invocation đó.

**`@Order` chỉ giải quyết câu hỏi precedence sau khi các advisor đã được chọn.** Đổi `@Order(5)` thành `@Order(50)` không khiến một annotation `@AuditAction` đột nhiên xuất hiện trên target method.

### Một biểu thức match cả hai service methods

```java
@Around("execution(public * com.example.chain.TaskService.*(..))")
public Object aroundEveryPublicTaskMethod(
        ProceedingJoinPoint pjp) throws Throwable {
    return pjp.proceed();
}
```

Nếu target có `run()` và `fail()`, cả hai có thể bị advice khi caller gọi qua Spring proxy. Thứ tự giữa aspects là một câu chuyện khác, do `@Order`/`Ordered`.

Nếu pointcut chỉ chọn `run()`, advice không chạy khi caller gọi `fail()`, bất kể `@Order` là bao nhiêu. Vì vậy, khi thiếu log, kiểm tra **matching trước** rồi mới điều tra order.

## 7. Demo Spring Framework runnable: ba aspects cùng khớp hai methods

Demo dùng Java 17+, Spring Framework **6.2.19**, AspectJ Weaver **1.9.25.1**, Gradle. Đây là Spring Framework thuần, không dùng Spring Boot auto-configuration, để thứ tự `@Order` và yêu cầu `@EnableAspectJAutoProxy` được thể hiện rõ.

Gồm:

- `OuterAspect`: `@Order(10)`, `@Around`, vào đầu tiên và thoát cuối cùng.
- `GateAspect`: `@Order(15)`, `@Before`, chạy sau `OuterAspect.enter` nhưng trước `InnerAspect.enter`.
- `InnerAspect`: `@Order(20)`, `@Around`, bao sát target hơn `OuterAspect`.
- `TaskService`: một method thành công và một method ném exception để quan sát chiều ra.
- `Main`: lấy Spring proxy từ context, gọi hai methods và in stdout tất định.

### 7.1. Cây thư mục

```text
spring-multiple-aspects-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/chain/
    ├── TaskService.java
    ├── OuterAspect.java
    ├── GateAspect.java
    ├── InnerAspect.java
    ├── AppConfig.java
    └── Main.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'spring-multiple-aspects-demo'
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
    mainClass = 'com.example.chain.Main'
}
```

Spring Framework 6.x hỗ trợ Java 17+; `spring-context` cung cấp `ApplicationContext`/`@Configuration`; `spring-aop` và `aspectjweaver` phục vụ cấu hình Spring proxy `@AspectJ`. Việc có `aspectjweaver` trên classpath để parsing pointcuts **không đồng nghĩa** đang bật AspectJ load-time weaving.

### 7.2. Spring target bean

**`TaskService.java`**

```java
package com.example.chain;

public class TaskService {
    public String run(String id) {
        System.out.println("target:run");
        return "ok:" + id;
    }

    public String fail() {
        System.out.println("target:fail");
        throw new IllegalStateException("boom");
    }
}
```

Class và methods đều public/non-final, không interface, nên class-based proxy có thể chặn chúng. Hai methods không gọi nhau để không lẫn self-invocation với thứ tự aspects.

### 7.3. Aspect order 10: wrapper ngoài cùng

**`OuterAspect.java`**

```java
package com.example.chain;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(10)
public class OuterAspect {
    @Around("execution(public * com.example.chain.TaskService.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String name = pjp.getSignature().getName();
        System.out.println("outer:enter:" + name);
        try {
            Object result = pjp.proceed();
            System.out.println("outer:return:" + name);
            return result;
        } catch (Throwable error) {
            System.out.println(
                    "outer:throw:" + name + ":"
                    + error.getClass().getSimpleName()
            );
            throw error;
        } finally {
            System.out.println("outer:finally:" + name);
        }
    }
}
```

Chú ý:

- `@Order(10)` có precedence cao hơn `@Order(15)` và `@Order(20)`.
- `pjp.proceed()` chuyển quyền xuống những advisors còn lại, rồi mới tới target.
- `throw error;` giữ nguyên exceptional outcome của downstream invocation.
- `finally` chạy trên cả normal return lẫn throw.
- Advice không tự tạo fallback hoặc retry. Nếu cố return fake value trong `catch`, output caller và hành vi downstream có thể thay đổi.

### 7.4. Aspect order 15: một @Before

**`GateAspect.java`**

```java
package com.example.chain;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;

@Aspect
@Order(15)
public class GateAspect {
    @Before("execution(public * com.example.chain.TaskService.*(..))")
    public void before(JoinPoint jp) {
        System.out.println("gate:before:" + jp.getSignature().getName());
    }
}
```

`@Before` ở đây dùng để **quan sát entry**, không tự return kết quả. Nó chạy giữa `OuterAspect.enter` và `InnerAspect.enter` khi những advisors tham gia chuỗi như cấu hình.

Nếu `@Before` tự ném exception, các interceptors/target phía sau có thể không được gọi. Đừng dùng `@Before` để biến một lỗi hợp lệ thành return fallback; nếu cần authorization, hãy dùng hạ tầng Spring Security với precedence được kiểm tra phù hợp.

### 7.5. Aspect order 20: wrapper sát target

**`InnerAspect.java`**

```java
package com.example.chain;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(20)
public class InnerAspect {
    @Around("execution(public * com.example.chain.TaskService.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String name = pjp.getSignature().getName();
        System.out.println("inner:enter:" + name);
        try {
            Object result = pjp.proceed();
            System.out.println("inner:return:" + name);
            return result;
        } catch (Throwable error) {
            System.out.println(
                    "inner:throw:" + name + ":"
                    + error.getClass().getSimpleName()
            );
            throw error;
        } finally {
            System.out.println("inner:finally:" + name);
        }
    }
}
```

`InnerAspect` có precedence thấp nhất trong ba aspects nhưng vẫn **bao quanh target**: `inner:enter` trước `target`, `inner:return` hoặc `inner:throw` sau target, rồi `inner:finally`. Nó hoàn thành phần chiều ra **trước** `OuterAspect`.

### 7.6. Spring Java configuration

**`AppConfig.java`**

```java
package com.example.chain;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    TaskService taskService() {
        return new TaskService();
    }

    @Bean
    OuterAspect outerAspect() {
        return new OuterAspect();
    }

    @Bean
    GateAspect gateAspect() {
        return new GateAspect();
    }

    @Bean
    InnerAspect innerAspect() {
        return new InnerAspect();
    }
}
```

`@Aspect` **không tự đăng ký Spring bean**. Ở đây, ba aspect beans được khai báo bằng `@Bean`, không cần `@Component` hoặc component scanning. `@EnableAspectJAutoProxy(proxyTargetClass=true)` bật Spring @AspectJ auto-proxy và cho phép lấy `TaskService` qua concrete type trong demo.

**Không cần ba proxy objects** để chạy ba aspects. Spring có thể kết hợp các advisors phù hợp trong AOP proxy của bean `taskService`.

### 7.7. Main và lệnh chạy

**`Main.java`**

```java
package com.example.chain;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            TaskService service = context.getBean(TaskService.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(service));
            System.out.println("case=normal");
            System.out.println("result=" + service.run("A"));

            System.out.println("case=exception");
            try {
                service.fail();
            } catch (IllegalStateException error) {
                System.out.println("caught=" + error.getMessage());
            }
        }
    }
}
```

Ở thư mục root của project, với Java 17+ và Gradle đã cài:

```bash
gradle run --quiet
```

Nếu project có Gradle Wrapper: `./gradlew run --quiet` trên Linux/macOS hoặc `gradlew.bat run --quiet` trên Windows.

**Stdout của các lệnh `System.out` trong project** theo đúng thứ tự (không tính logging JVM/Spring/Gradle từ môi trường):

```text
proxy=true
case=normal
outer:enter:run
gate:before:run
inner:enter:run
target:run
inner:return:run
inner:finally:run
outer:return:run
outer:finally:run
result=ok:A
case=exception
outer:enter:fail
gate:before:fail
inner:enter:fail
target:fail
inner:throw:fail:IllegalStateException
inner:finally:fail
outer:throw:fail:IllegalStateException
outer:finally:fail
caught=boom
```

### 7.8. Giải thích từng dòng có tính quyết định

**Normal case:**

1. `outer:enter:run`: aspect `@Order(10)` đứng ngoài vào trước.
2. `gate:before:run`: `@Order(15)` được gọi khi chain tiến vào.
3. `inner:enter:run`: aspect `@Order(20)` vào sau nhưng trước target.
4. `target:run`: **target body chạy đúng một lần**.
5. `inner:return:run` và `inner:finally:run`: wrapper trong cùng hoàn tất chiều ra trước.
6. `outer:return:run` và `outer:finally:run`: wrapper ngoài cùng hoàn tất sau.
7. `result=ok:A`: caller nhận nguyên value do target trả, không bị aspects thay đổi.

**Exception case:**

1. Chuỗi enter giữ thứ tự `outer → gate → inner → target`.
2. `target:fail` ném `IllegalStateException("boom")`.
3. `InnerAspect` nhận exception trước, in `inner:throw` và `inner:finally` rồi ném lại.
4. `OuterAspect` nhận cùng exceptional flow, in `outer:throw` và `outer:finally` rồi ném lại.
5. Caller catch đúng `IllegalStateException` và in `caught=boom`.

**Điểm cần phân biệt:** Mỗi `@Around` in **hai hoặc ba dòng** cho một invocation, nhưng đó **không phải nhiều target calls**. Cùng exception đi qua hai layers nên được quan sát hai lần; không có bằng chứng target đã chạy hai lần.

### 7.9. Tại sao không dùng hai @Before không có @Order?

Nếu cả hai aspect đều có `@Before` và không có `@Order`, ứng dụng có thể in hai logs trước target, nhưng **thứ tự của hai logs không phải contract**. Demo cố ý dùng **ba `@Order` khác nhau** để stdout có lý do xác định rõ.

## 8. Khi hai advice trong cùng một @Aspect cùng khớp một method

Đây là câu hỏi **khác** với thứ tự giữa nhiều aspect classes.

Ví dụ minh họa:

```java
@Aspect
public class TwoBeforeAdvices {
    @Before("execution(* com.example.chain.TaskService.run(..))")
    public void first() {
        System.out.println("first");
    }

    @Before("execution(* com.example.chain.TaskService.run(..))")
    public void second() {
        System.out.println("second");
    }
}
```

Trong một class `@Aspect`, **hai advice cùng loại (`@Before`)** mà cùng match một join point **không có thứ tự được bảo đảm** chỉ vì `first` đứng trước `second` trong Java source. Java reflection không cung cấp declaration order ổn định mà Spring có thể dựa vào như một contract.

**Giải pháp khi cần thứ tự chắc chắn:**

- Gộp logic into **một `@Before` method** có thứ tự statements rõ ràng;
- Hoặc tách thành **hai `@Aspect` classes**, áp `@Order` khác nhau và test;
- Nếu là một flow có enter/exit phức tạp, cân nhắc một `@Around` method với `try/finally` theo contract rõ ràng.

Đoạn trên là **minh họa**, không thêm vào project demo mục 7 vì sẽ làm thay stdout.

### 8.1. Các advice khác loại trong cùng aspect

Spring Framework 6.2 mô tả precedence theo **loại advice** trong cùng `@Aspect`, từ cao xuống thấp:

```text
@Around > @Before > @After > @AfterReturning > @AfterThrowing
```

Tuy vậy, `@After` có semantics kiểu **after-finally**: trên thực tế nó được gọi **sau `@AfterReturning` hoặc `@AfterThrowing`** trong cùng aspect khi join point kết thúc. Đừng biến bảng precedence theo loại thành một danh sách thứ tự logging tuyến tính đơn giản từ trái sang phải; hãy xét **chiều vào/ra của interceptor chain**.

Hai advice **cùng loại trong cùng aspect** vẫn không có source-order guarantee. Đây là một chi tiết Spring-specific quan trọng khi refactor nhiều handlers ra chung một `@Aspect`.

### 8.2. Advice của cùng @Aspect có thể khác pointcut

Một `@Aspect` chứa nhiều `@Before` không có nghĩa **mọi** advice đó đều chạy cho mọi method. Cần xét từng expression:

```java
@Before("execution(* com.example.chain.TaskService.run(..))")
public void onRun() {}

@Before("execution(* com.example.chain.TaskService.fail(..))")
public void onFail() {}
```

Nếu caller gọi `run()`, advice chỉ nhắm `fail()` không match. Đừng nhầm thiếu event với vấn đề order khi pointcut thực sự không chọn join point.

## 9. Nhiều @AfterReturning và @AfterThrowing: phân biệt normal/exception

Khi các aspects khác nhau dùng các loại advice khác nhau, chính **outcome** quyết định advice nào đủ điều kiện được gọi:

| Advice | Normal return của target execution | Exceptional exit của target execution |
| --- | --- | --- |
| `@Before` | Có thể chạy trước target | Có thể chạy trước target |
| `@Around` | Có thể chạy cả trước/sau; có thể thay outcome | Có thể bắt/propagate/transform lỗi |
| `@AfterReturning` | Chạy nếu pointcut/return binding phù hợp | Không chạy khi join point thoát bằng exception |
| `@AfterThrowing` | Không chạy khi join point normal return | Chạy nếu exception/binding phù hợp |
| `@After` | Có semantics finally khi execution thoát | Có semantics finally khi execution thoát |

**Đừng suy rằng “aspect priority cao” khiến `@AfterReturning` chạy khi target throw lỗi.** Order không thay sự kiện mà loại advice lắng nghe.

### 9.1. Chữ ký advice có returning/throwing làm tập matches khác nhau

```java
@AfterReturning(
    pointcut = "execution(* com.example.chain.TaskService.*(..))",
    returning = "text",
    argNames = "text"
)
public void afterText(String text) {
    // ...
}
```

```java
@AfterThrowing(
    pointcut = "execution(* com.example.chain.TaskService.*(..))",
    throwing = "problem",
    argNames = "problem"
)
public void afterIllegalArgument(IllegalArgumentException problem) {
    // ...
}
```

Hai đoạn minh họa này có cùng `execution` scope nhưng **ràng buộc kiểu** khác nhau. `afterText` yêu cầu normal return tương thích `String`; `afterIllegalArgument` chỉ chạy trên exception kiểu `IllegalArgumentException`/subtype và **không** chạy cho `IllegalStateException` trong demo. Không phải mọi “advice không chạy” đều liên quan `@Order`.

### 9.2. @AfterThrowing không phải catch của các advice khác

Spring docs nhấn mạnh các distinct advice types của một aspect về mặt khái niệm áp lên **join point của target**; `@AfterThrowing` không nên được hiểu là một global catch handler cho exception do một `@After`/`@AfterReturning` method **khác trong cùng aspect** ném ra.

Nếu các advisors khác nhau ném/biến đổi lỗi, quá trình propagation còn phụ thuộc vị trí interceptor trong chain. Không tự động “sửa” bằng cách catch `Throwable` ở aspect ngoài rồi return `null`, vì điều đó có thể thay transaction/security semantics và làm mất lỗi gốc.

## 10. Quan hệ với Spring transaction, security, cache, async

Spring có nhiều infrastructure interceptors ngoài custom `@Aspect`. Khi cùng tác động lên một method, cần xem **advisor chain thực tế**, không tự đồng nhất “business aspects order” với “mọi framework component sẽ luôn tuân theo ba số order ví dụ”.

### 10.1. @Transactional và thứ tự với custom aspects

Một method:

```java
@Transactional
public void save(Order order) {
    // ...
}
```

Đây là **mã minh họa**; `Order` là domain type cần định nghĩa, và transaction management cần được bật riêng. Giả sử custom `@Around` ghi “success” khi `pjp.proceed()` normal return:

- Nếu aspect **bao ngoài transaction advisor**, `proceed()` của nó có thể bao gồm cả transaction interception (commit/rollback).
- Nếu aspect **nằm bên trong transaction advisor**, nó có thể thấy target return bình thường nhưng transaction commit xảy ra sau khi nó ra.
- Propagation, rollback rules, transaction manager và thứ tự advisor có thể thay kết quả.
- **Không dùng stdout `target:return` làm bằng chứng DB đã commit.**

Muốn side effect sau commit, dùng Spring transaction synchronization hoặc `@TransactionalEventListener(phase=AFTER_COMMIT)` theo cấu hình đúng. Không thay `@Order` một cách tùy hứng để “bảo đảm commit” khi chưa xác định transaction advisor order thực tế.

### 10.2. Method security và aspect có short-circuit

Nếu custom `@Around` precedence cao và **không gọi `proceed()`**, một method security interceptor **downstream** có thể không chạy. Điều này có thể vô tình bỏ qua `@PreAuthorize` hay policy khác, tùy thứ tự. Security-sensitive paths cần end-to-end tests cho access denied, bao gồm những nhánh cache/fallback/short-circuit.

Đừng gán một con số `@Order` bất kỳ cho custom security aspect rồi tự kết luận nó bảo vệ toàn bộ ứng dụng. Spring Security có cấu hình và advisors riêng; cần kiểm tra actual method security activation và proxy semantics.

### 10.3. Cache advisors

`@Cacheable` có thể trả giá trị từ cache mà không gọi target method. Nếu custom metrics aspect ở bên ngoài cache advisor, nó có thể thấy **proxy invocation** dù target không chạy; nếu ở bên trong, nó có thể không được gọi khi cache hit short-circuit. Đây là lý do số log entries của aspects có thể khác số target body executions **một cách hợp lệ**.

Nếu custom `@Around` tự cache/fallback, cẩn thận không cache một giá trị giả “success” từ lỗi backend nếu contract không cho phép.

### 10.4. @Async và Future/Publisher

`@Async`, `CompletableFuture` và reactive `Mono`/`Flux` làm thời điểm và thread execution phức tạp hơn. Thứ tự quan sát của logs giữa threads có thể xen kẽ, ngay cả khi precedence của các advisors trên **một invocation chain** rõ ràng. `@Around` có thể thấy **future/publisher wrapper return** trước khi computation/subscription kết thúc.

Không dùng một ví dụ đồng bộ như `TaskService.run()` để tuyên bố thứ tự logs toàn cục giữa nhiều threads hoặc success cuối cùng của các asynchronous tasks.

## 11. Self-invocation và các giới hạn Spring proxy

`@Order` chỉ có ý nghĩa khi Spring AOP thực sự intercept invocation.

### 11.1. Self-invocation vẫn bypass

```java
public class TaskService {
    public String outer() {
        return this.run("A"); // call nội bộ trên target
    }

    public String run(String id) {
        return "ok:" + id;
    }
}
```

Nếu target đã nhận call `outer()` thông qua proxy, lời gọi `this.run()` ở bên trong target **không quay lại proxy**. Do đó các advices nhắm `run(..)` không tự chạy thêm, dù mỗi aspect có `@Order` rõ ràng. Muốn advice trên `run`, gọi qua Spring proxy từ bean khác hoặc refactor phần cần interception sang collaborator bean.

### 11.2. JDK proxy và CGLIB

- Với **JDK dynamic proxy**, caller dùng interface method được proxy expose; implementation class có thể final nếu chọn interface proxy phù hợp.
- Với **CGLIB**, class phải subclass được và method cần advice phải override được; `final` class/method tạo giới hạn.
- `@EnableAspectJAutoProxy(proxyTargetClass=true)` chọn class proxy cho demo; nó **không sửa self-invocation**.
- `private` method không phải entry interceptable thông thường của Spring AOP proxy.
- Constructor, field read/write và AspectJ compiler weaving join points không được tự động hỗ trợ bởi Spring proxy AOP.
- Spring `@AspectJ` uses AspectJ annotation syntax nhưng runtime interception vẫn là Spring proxy.

### 11.3. Không dùng new target khi test

```java
TaskService service = new TaskService();
service.run("A");
```

Đây là Java object thuần, không phải Spring AOP proxy; không có advices chạy. Khi test thứ tự Spring aspects, luôn nhận target bean từ `ApplicationContext`/dependency injection và gọi qua proxy.

## 12. Những lỗi chẩn đoán dễ gặp

| Dấu hiệu | Kết luận dễ sai | Cách giải thích trong Spring |
| --- | --- | --- |
| Hai aspects đều có `@Before` | “Theo thứ tự khai báo source” | Không có guarantee khi không đặt precedence. |
| `@Order(10)` in log cuối | “Order bị đảo nên cấu hình sai” | Trong **chiều ra**, aspect precedence cao chạy **sau** aspect thấp hơn. |
| Có ba log entry nhưng một target log | “Target chạy ba lần” | Một target execution có thể đi qua nhiều advice; logs không phải target call count. |
| Aspect A không in gì | “Spring bỏ qua @Order” | Có thể pointcut không match, aspect chưa là bean, caller bypass proxy hoặc A outer short-circuit. |
| `@AfterReturning` không chạy khi exception | “Order của advice quá thấp” | Không có normal return; dùng `@AfterThrowing` cho exceptional exit. |
| Hai `@Before` trong cùng aspect đổi thứ tự ngẫu nhiên | “Cần đổi tên Java methods” | Cùng advice type không có source-order guarantee; tách aspects hoặc gộp logic. |
| Cache hit không có target log | “Target/Aspect bị Spring lỗi” | Cache interceptor có thể short-circuit; xem advisor precedence. |
| Outer `@Around` return null khi lỗi | “Logging vẫn thành công” | Có thể đã nuốt exception, thay contract và behavior transaction/security. |
| Aspect in success trước DB commit | “DB transaction chắc chắn thành công” | Thời điểm phụ thuộc vị trí transaction advisor; success log không bảo đảm commit. |
| Request có nhiều log lines | “Có nhiều HTTP requests” | Một invocation qua nhiều aspects tạo nhiều events. |
| Bean là proxy nhưng method không advice | “AOP proxy hỏng” | Pointcut, final/private, interface visibility và self-call cần kiểm tra. |
| Aspects có cùng order nhưng local chạy ổn | “Thứ tự được đảm bảo” | Equal precedence không phải contract trong mọi Spring setups. |

## 13. Debug một Spring AOP chain khi thứ tự khác kỳ vọng

### 13.1. Xác nhận proxy loại nào

```java
TaskService bean = context.getBean(TaskService.class);
System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
```

Đoạn minh họa dùng context của demo và import `org.springframework.aop.support.AopUtils`. Trong demo, cả hai là `true` do `proxyTargetClass=true` và method pointcut match.

**Proxy tồn tại không chứng minh một advice cụ thể match.** Dùng event recorder hoặc log có tên aspect/method để xác nhận mỗi advice thực thi.

### 13.2. Kiểm tra order value runtime

Có thể log hoặc inspect `@Order` trên các aspect classes hoặc `Ordered.getOrder()` khi aspects implement interface. Khi hạ tầng có nhiều advisors, muốn xem advisor list thường có thể inspect Spring `Advised` proxy:

```java
import org.springframework.aop.framework.Advised;

Object bean = context.getBean("taskService");
if (bean instanceof Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
```

Đây là **đoạn chẩn đoán minh họa**, không thêm vào `Main` nếu muốn giữ stdout ở mục 7.7. Advisor list giúp điều tra chuỗi proxy và hạ tầng, nhưng không tự chứng minh toàn bộ dynamic pointcut conditions match trên một invocation. Một advisor có thể match method theo static filter và cần runtime checks để advice chạy. Không nên mutate advisor list trong production chỉ để thử order.

### 13.3. Dùng event recorder thay vì log lẫn nhiều threads

Một recorder cho demo đồng bộ:

```java
public final class EventRecorder {
    private final java.util.List<String> events =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public void add(String event) {
        events.add(event);
    }

    public java.util.List<String> snapshot() {
        return java.util.List.copyOf(events);
    }
}
```

Đây là **một Java class minh họa độc lập**, nếu gắn vào project demo cần đăng ký recorder làm Spring bean và inject vào aspects. Sử dụng một recorder giúp test thứ tự chính xác hơn so với đọc console có nhiều sources.

Trong async/multithread environment, mỗi invocation nên có **correlation ID** riêng; `CopyOnWriteArrayList` thread-safe cho writes nhưng không biến event order giữa concurrent invocations thành một thứ tự tổng quát bảo đảm.

### 13.4. Kiểm tra pointcut matching

Xem các biểu thức `execution(...)`, `within(...)`, `@annotation(...)`, `bean(...)`; lưu ý `bean(...)` là extension Spring AOP, không hỗ trợ toàn bộ AspectJ language. Với pointcut phụ thuộc annotation/args, method được gọi phải thực sự mang metadata/arguments tương ứng.

Nếu aspect chưa được Spring register, thêm `@Aspect` không đủ: dùng `@Component` + component scan đúng package hoặc `@Bean` trong config. Với Spring Framework Java config, bật `@EnableAspectJAutoProxy` đúng context và có `aspectjweaver`.

## 14. Quy trình kiểm thử integration cho nhiều aspects

### 14.1. Các assertion cần có

Một integration test tốt nên chứng minh:

1. Spring trả về **proxy** cho target.
2. Cả ba aspects được Spring đăng ký và pointcuts thực sự match.
3. Đường normal return có thứ tự `outer enter → gate before → inner enter → target → inner return/finally → outer return/finally`.
4. Đường exceptional exit có thứ tự `outer enter → gate before → inner enter → target throw → inner catch/finally → outer catch/finally`.
5. Caller thấy result/exception gốc khi advices không được quyền thay outcome.
6. Khi đổi `@Order` có chủ đích, thứ tự kỳ vọng thay đổi theo contract.
7. Caller không qua proxy hoặc self-invocation không tạo thêm interception.
8. Aspects khác pointcut không được chạy ngoài ý muốn.
9. Test được trên Spring Framework/Boot version và proxy mode giống production.

### 14.2. Đối chiếu một output mẫu

Trong code demo, `Main` in stdout chi tiết. Một test có thể thay `System.out` trong advices bằng recorder để assert danh sách events. **Không dựa vào một substring “outer” hay số dòng log tổng**, vì hai `@Around` đều có multiple exit events.

Ví dụ expectation cho normal call:

```java
List<String> expected = List.of(
    "outer:enter:run",
    "gate:before:run",
    "inner:enter:run",
    "target:run",
    "inner:return:run",
    "inner:finally:run",
    "outer:return:run",
    "outer:finally:run"
);
```

Đoạn này **minh họa assertion data**, cần import `java.util.List`, recorder và một target method dùng recorder thay `System.out`. Không tự copy vào `Main` đang chạy nếu không có biến `expected` được sử dụng và binding tới events thực tế.

### 14.3. Test exception identity khi contract yêu cầu

Với `OuterAspect` và `InnerAspect` đều thực hiện `throw error;`, exception từ target có thể giữ identity xuyên chain khi không interceptor khác wrap. Một fixture Spring test riêng có thể cho target ném một `IllegalStateException` đã tạo sẵn và assert caller catch đúng object ấy (`==`), đồng thời assert order events.

Nhưng nhớ: trong production, JDK interface proxies, transactions hoặc library interceptors khác có thể wrap/mapping exceptions theo contract; test phải xét thực tế thay vì khẳng định mọi Spring AOP scenario đều giữ object identity bất kể chain.

### 14.4. Test với transaction hoặc method security

Nếu method có `@Transactional` hay `@PreAuthorize`, hãy thêm integration tests với các cấu hình activation thực, không chỉ load `@EnableAspectJAutoProxy`. Thử cả success/failure, rollback/commit, unauthorized caller, cache hit và short-circuit. Quan sát advisor ordering thực tế thay vì suy từ 10, 15, 20 của ví dụ thuần AOP.

## 15. Spring Boot và việc phối hợp nhiều aspects

Trong Spring Boot, khi AOP starter/auto-configuration phù hợp, thường không cần tự khai báo `@EnableAspectJAutoProxy`. Nhưng:

- Mỗi class `@Aspect` có advice phải là **Spring bean** (thường thêm `@Component` hoặc dùng `@Bean`).
- Dùng `@Order` trên aspect classes khi cần precedence giữa nhiều aspects.
- Đặt pointcut đủ hẹp để không proxy nhầm bean hạ tầng và không tăng overhead toàn hệ thống.
- Cấu hình `spring.aop.proxy-target-class` có thể ảnh hưởng JDK vs CGLIB proxy, đặc biệt với final methods hoặc interface injection.
- Transaction, caching, async và method security có infrastructures/activation và advisor precedence riêng; không bảo đảm custom aspect luôn “bọc” hay “bên trong” chúng chỉ bằng cách đặt một order value tùy ý.
- Khi có request concurrency, thứ tự events giữa **các invocation khác nhau** không được suy từ thứ tự advisors trong **một invocation**.

Dependency Gradle lựa chọn theo Boot major và dependency management (hai dòng là phương án riêng):

```gradle
// Spring Boot 3.x
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

```gradle
// Spring Boot 4.x
implementation 'org.springframework.boot:spring-boot-starter-aspectj'
```

Không thêm đồng thời cả hai nếu không có thiết kế dependency cụ thể. Khi Boot cung cấp BOM, để Boot chọn version Spring modules phù hợp, không cố trộn `6.2.19` với các version không tương thích.

### Khi dùng @Order và @Priority

Với Spring AOP aspects, tài liệu Spring mô tả `@Order` hoặc `Ordered` cho precedence. Đừng nhầm nó với `@Priority` của Java/Jakarta mà không kiểm tra cách Spring infrastructure cụ thể đọc annotation. Khi test, cách dễ chứng minh nhất vẫn là **quan sát actual advisor chain và event order**.

## 16. Checklist review trước khi đưa vào production

- [ ] Có thật sự **nhiều Spring `@Aspect` beans** có advice match cùng một target method?
- [ ] Target method được gọi **qua Spring proxy**, không phải `new`/self-invocation?
- [ ] Mỗi aspect được đăng ký bean bằng `@Component`/`@Bean` và Spring AOP @AspectJ support bật đúng context?
- [ ] Các pointcut expressions nhắm đúng target package/type/method và không match quá rộng?
- [ ] Nếu order quan trọng, aspects có `@Order` khác nhau hoặc `Ordered` rõ ràng?
- [ ] Hiểu **số nhỏ precedence cao**; vào trước nhưng ra sau khi dùng wrapper `@Around`?
- [ ] Không dựa vào source declaration order, bean registration order hoặc tên class để suy thứ tự?
- [ ] Không suy hai advice cùng loại trong cùng một aspect có thứ tự cố định?
- [ ] `@Around` downstream call `proceed()` đúng số lần theo contract, không short-circuit/retry ngoài dự tính?
- [ ] `@AfterReturning` chỉ quan sát normal return, `@AfterThrowing` chỉ exceptional exit đúng kiểu?
- [ ] Khi advice tự ném lỗi hoặc chuyển return, đã test semantics với transaction/security/cache advisors?
- [ ] Đã xem precedence của infrastructure interceptors thực tế thay vì áp quy tắc 10/15/20 máy móc?
- [ ] Với JDK/CGLIB proxy, method entry có expose/override được, class final/visibility phù hợp?
- [ ] Từng invocation có event correlation để tránh nhầm thứ tự concurrency với precedence?
- [ ] Integration tests bao gồm cả return lẫn exception, thứ tự events và contract return/throw?
- [ ] Không coi số lần advice chạy đồng nghĩa số lần target business logic được thực thi?

**Ghi nhớ:** Trong **Spring AOP**, nhiều aspects cùng match một method được Spring đưa vào **một invocation chain của proxy**. Khi cần kiểm soát thứ tự giữa các aspects, **`@Order` số nhỏ có precedence cao**: chạy trước khi đi vào target và **sau trên đường ra**. Thứ tự đó không làm pointcut match thêm, không ép `@Around` phải `proceed()`, không giải quyết self-invocation và không thay semantics của normal/exception outcomes. Mọi giả định về order phải được xác nhận trên **Spring proxy và advisors thực tế**.

## Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Declaring Advice: Advice Ordering và nhiều advice cùng join point](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework 6.2 — Declaring Pointcuts](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html)
- [Spring Framework 6.2 — Proxying Mechanisms, JDK/CGLIB và self-invocation](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework 6.2 — Schema-based AOP Advice Ordering](https://docs.spring.io/spring-framework/reference/6.2/core/aop/schema.html)
- [Spring Framework 6.2 — Transaction Management](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-ordering-002">Độ ưu tiên bằng @Order và Ordered</a>

<details>
<summary>Xem chi tiết</summary>

# Độ ưu tiên bằng @Order và Ordered trong Spring AOP

## 1. Mục tiêu bài học

Một Spring bean có thể phù hợp với nhiều lời advice của các lớp `@Aspect` khác nhau. Ví dụ, trước khi `TransferService.transfer()` thực hiện chuyển tiền, bạn cần có audit; đồng thời muốn thu thập metrics bao quanh cùng lời gọi. Nếu không quy định thứ tự, việc hai aspect cùng match một Spring AOP join point **không mang theo lời bảo đảm rằng Spring sẽ thực thi chúng theo thứ tự khai báo class, đăng ký bean hoặc vị trí trong source file**.

Spring Framework cung cấp hai cách quen thuộc để quy định **precedence giữa các aspect**:

- Đánh dấu class aspect bằng `@Order(value)` từ `org.springframework.core.annotation.Order`.
- Cho class aspect triển khai interface `org.springframework.core.Ordered` và trả về giá trị từ `getOrder()`.

**Quy tắc gốc: giá trị thứ tự càng nhỏ thì độ ưu tiên càng cao.** Một aspect có order `10` được xét có precedence cao hơn aspect order `20`. Khi cả hai advice match một method invocation trên Spring proxy, aspect có precedence cao thường chạy **trước khi đi vào** chuỗi bên trong, và **sau cùng khi đi ra**. Đây là mô hình **bao ngoài – bao trong**, không phải phép sắp xếp mọi dòng log theo số tăng dần.

Bài này tập trung vào **Spring Aspect/Spring AOP dạng proxy** với các mục tiêu:

1. Phân biệt thứ tự *trên đường vào* và *trên đường ra* của invocation.
2. Áp dụng đúng `@Order` và `Ordered.getOrder()` cho các **Spring-managed `@Aspect` beans**.
3. Hiểu `Ordered.HIGHEST_PRECEDENCE`, `Ordered.LOWEST_PRECEDENCE`, giá trị trùng nhau và mặc định khi không khai báo.
4. Phân biệt **thứ tự giữa các aspect** với **thứ tự advice bên trong cùng một aspect**.
5. Biết các giới hạn khi có Spring transaction/cache/security advisors, self-invocation và short-circuit.
6. Chạy một ví dụ **Maven/Java 17+** với một aspect dùng `@Order`, một aspect dùng `Ordered`, kiểm chứng cả success và exception.

**Special Note:** Chỉ giải thích hành vi cụ thể của Spring Aspect/Spring AOP. Các kiến thức Aspect chung đã có ở chủ đề khác nên không lặp lại.

## 2. Spring quyết định thứ tự ở đâu?

Một lời gọi Spring AOP bắt đầu từ **tham chiếu proxy** mà caller nhận được từ container, không phải một method của class gốc được gọi trực tiếp bằng `new`.

~~~text
Caller
   |
   v
Spring AOP proxy
   |
   +--> Aspect A: @Around, order = 10
   |       |  A: enter
   |       v
   |    Aspect B: @Around, order = 20
   |       |  B: enter
   |       v
   |    TargetService.method()
   |       |
   |       v
   |       B: exit
   |       |
   |       v
   |       A: exit
   |
   v
Caller nhận kết quả/exception
~~~

Trong hình, hai aspect đều có around advice và mỗi advice gọi `ProceedingJoinPoint.proceed()` đúng một lần. Khi **order 10 cao precedence hơn order 20**, tầng A bao quanh B.

Điều quan trọng là **order thuộc về aspect/advisor**; nó không thay đổi tên method, tham số, return value hay pointcut. Nếu pointcut của B không khớp, sẽ không có B trong chain bất kể giá trị order của nó. Nếu caller không đi qua proxy, cả hai lời advice đều có thể bị bỏ qua.

### 2.1. “Chạy trước” và “chạy sau” phụ thuộc chiều

Giả sử A có order `10` và B có order `20`:

| Thời điểm | Aspect có precedence cao hơn: A(10) | Aspect có precedence thấp hơn: B(20) |
| --- | --- | --- |
| Đi vào invocation | **Chạy trước B** | Chạy sau A |
| Khi chuyển tiếp qua `proceed()` | Đưa lời gọi đến phần chain có B | Đưa lời gọi đến target hoặc phần chain sâu hơn |
| Đi ra sau normal return | **Chạy sau B** | Chạy trước A |
| Đi ra trong exceptional exit | Finally/catch của A chạy **sau** phần tương ứng của B nếu cả hai đã vào | Finally/catch của B chạy trước A |

Do đó phát biểu “`@Order(10)` luôn thực thi tất cả các dòng code trước `@Order(20)`” là **sai**. Nó có precedence cao và là **wrapper bên ngoài**; những thao tác `finally`/hậu xử lý của nó diễn ra sau khi B đã kết thúc.

## 3. Cách 1: dùng @Order trên lớp Spring aspect

Ví dụ:

~~~java
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(10)
public class AuditAspect {

    @Around("execution(* example.TransferService.transfer(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("audit: enter");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("audit: exit");
        }
    }
}
~~~

Đây là class aspect minh họa; ví dụ runnable hoàn chỉnh nằm ở mục 7.

- Import **`org.springframework.core.annotation.Order`**, không phải một annotation có tên tương tự của framework khác.
- Đặt `@Order(10)` trên **class `@Aspect`** khi muốn quy định precedence so với những aspect khác.
- Spring phải **đăng ký aspect thành bean** (ví dụ `@Component` trong component scanning, hoặc `@Bean` trong cấu hình). `@Aspect` và `@Order` tự chúng **không** biến class thành Spring bean.
- `@Order` là metadata để Spring sắp xếp các advisor/aspect khi những advisor đó tham gia cùng một chain.
- `@Order` không ép tất cả target beans phải có proxy: chỉ các method/beans tương ứng với advisor mới có thể được advice.
- `@Order` **không điều khiển bean startup/khởi tạo singleton**; thứ tự khởi tạo chủ yếu phụ thuộc dependency graph và `@DependsOn` khi cần. Đây là vấn đề hoàn toàn khác với advice precedence.

Khi code chỉ cần một giá trị ưu tiên tĩnh, `@Order` thường đơn giản và dễ nhìn hơn `implements Ordered`.

## 4. Cách 2: implement Ordered và trả getOrder()

Spring cung cấp interface:

~~~java
package org.springframework.core;

public interface Ordered {
    int getOrder();
}
~~~

Đây là mô tả giản lược chữ ký quan trọng của API, không phải mã để đặt vào project nhằm định nghĩa lại interface Spring.

Trong một Spring aspect:

~~~java
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;

@Aspect
public class MetricsAspect implements Ordered {

    @Override
    public int getOrder() {
        return 20;
    }

    @Around("execution(* example.TransferService.transfer(..))")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("metrics: enter");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("metrics: exit");
        }
    }
}
~~~

Giá trị `20` ở đây nhỏ hơn hay lớn hơn **không mang nghĩa “tốt/xấu”**; Spring chỉ so sánh precedence. Vì `20 > 10`, MetricsAspect có precedence thấp hơn AuditAspect(10) trong ví dụ.

**Khi nên dùng `Ordered`:** khi aspect muốn cung cấp thứ tự theo một hợp đồng Java rõ ràng hoặc cần cấu hình order lúc khởi tạo bean. Tuy nhiên, đừng hiểu “dynamic” là có thể thay đổi `getOrder()` tùy tiện giữa các lời gọi để Spring tự rebuild chain: thứ tự advisor thường được thiết lập khi auto-proxy infrastructure chuẩn bị proxy/chain, nên thay đổi giá trị ở runtime không phải cách ổn định để đảo precedence. Nếu muốn đổi order của aspect, cấu hình trước khi context tạo proxy rồi kiểm thử lại.

**Tránh khai báo cả `@Order` và `Ordered` trên cùng một aspect** nếu không có lý do và không kiểm tra thuật toán resolve order của phiên bản Spring. Hai nguồn thông tin khác nhau dễ gây hiểu nhầm cho người bảo trì.

## 5. Quy tắc giá trị order

Spring `Ordered` định nghĩa các hằng:

~~~java
Ordered.HIGHEST_PRECEDENCE // Integer.MIN_VALUE
Ordered.LOWEST_PRECEDENCE  // Integer.MAX_VALUE
~~~

Trong ví dụ:

| Giá trị | Ý nghĩa tương đối |
| --- | --- |
| `Ordered.HIGHEST_PRECEDENCE` | Giá trị rất nhỏ; thuộc nhóm precedence cao nhất |
| `@Order(-100)` | Precedence cao hơn `@Order(0)` |
| `@Order(0)` | Precedence cao hơn `@Order(10)` |
| `@Order(10)` | Cao hơn `@Order(20)` |
| `Ordered.LOWEST_PRECEDENCE` | Giá trị rất lớn; thuộc nhóm precedence thấp nhất |

Bạn **không cần** luôn dùng 0 hoặc 1. Đặt một dải giá trị có chủ đích cho từng nhóm, ví dụ audit-tracing, business metrics, custom policy. Tuy nhiên, **đừng tự tuyên bố rằng mọi infrastructure advisor luôn có giá trị thấp/cao hơn nhóm của mình**: transaction/cache/security có thể có ordering và proxy configuration riêng.

### 5.1. Nếu không đặt @Order và không implement Ordered?

Spring không có thông tin precedence riêng để giải quyết giữa các aspect cùng match. Tài liệu Spring nói rõ khi advice nằm trong **những aspect khác nhau** và không cấu hình precedence, **thứ tự thực thi không xác định**. Vì vậy:

- Không dựa vào thứ tự file Java, tên class theo bảng chữ cái, hay thứ tự các method `@Bean`.
- Không dựa vào việc một lần chạy thấy A trước B rồi giả định mọi lần đều vậy.
- Nếu thứ tự ảnh hưởng behavior, phải khai báo rõ bằng `@Order` hoặc `Ordered`.

Ở nhiều API Spring, đối tượng không khai báo order có thể được xét tương đương mức precedence thấp theo comparator. Tuy nhiên, **điều đó không tạo thứ tự ổn định giữa các aspect cùng mức**. Không biến một chi tiết comparator thành hợp đồng business.

### 5.2. Nếu hai aspect cùng order?

Ví dụ A và B đều `@Order(10)`. Khi không có cơ chế tie-break được tài liệu bảo đảm, thứ tự **giữa hai aspect vẫn không nên được dựa vào**. Nếu cần A chắc chắn bao ngoài B, dùng **hai giá trị khác nhau** và viết integration test xác nhận điều đó.

Đừng đặt cùng một order rồi cố kiểm soát bằng tên bean hoặc thứ tự đăng ký trong cấu hình: những cách đó tạo coupling không rõ ràng và dễ gãy sau nâng cấp Spring.

## 6. @Order và Ordered có cần dùng cùng một kiểu không?

Không. Trong **hai aspect khác nhau**, một aspect có `@Order(10)` và aspect kia `implements Ordered { getOrder() -> 20; }` đều có thể tham gia cùng quy tắc precedence.

Ví dụ:

~~~text
AuditAspect:   @Aspect + @Order(10)
MetricsAspect: @Aspect + implements Ordered; getOrder() = 20

=> trên đường vào: audit trước metrics
=> trên đường ra: metrics trước audit
~~~

Bạn không cần đổi mọi class sang `Ordered` chỉ vì một aspect khác đang implement interface. Tuy nhiên, hãy giữ convention nhất quán trong một codebase để người đọc tìm cấu hình ordering dễ hơn.

**Phân biệt với `@Order` ở vị trí khác:** Spring `@Order` còn được một số API dùng để sắp xếp collection beans, event listeners hoặc infrastructure. Ở bài này ta chỉ xét **precedence của Spring AOP aspects**. Không được suy ra từ việc một bean có `@Order` rằng mọi callback và mọi bean phụ thuộc vào nó cũng sẽ khởi tạo hoặc chạy theo thứ tự đó.

## 7. Project Java 17+ chạy được: hai aspect, hai cách khai báo order

Chương trình bên dưới chứng minh:

- Spring Framework 6.2.19, AspectJ Weaver 1.9.25.1, Java 17+, Maven.
- Một `TransferService` là Spring-managed bean.
- `AuditAspect` dùng `@Order(10)` và `@Around`.
- `MetricsAspect` dùng `implements Ordered`, `getOrder() == 20` và `@Around`.
- Cả hai pointcut nhắm tới một public method `transfer(boolean fail)`.
- Normal return và exceptional exit cho thấy **chiều vào tăng dần order**, **chiều ra giảm dần**.
- Hai advice đều gọi `pjp.proceed()` đúng **một lần**, không thay đổi tham số/return/exception. `finally` được dùng để xác nhận đường ra kể cả lúc lỗi.

Đây là Spring AOP **proxy-based**, không cần cấu hình AspectJ weaving.

### 7.1. Cấu trúc project

~~~text
spring-aspect-order-demo/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── TransferService.java
    ├── AuditAspect.java
    ├── MetricsAspect.java
    └── Main.java
~~~

### 7.2. Maven: pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-aspect-order-demo</artifactId>
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

Dùng Spring Framework BOM để các module Spring thống nhất version. Với dự án Spring Boot, ưu tiên dùng dependency management của Boot; không tự ép version Spring trong một Boot application đã có parent/BOM.

### 7.3. TransferService.java

~~~java
package example;

public class TransferService {

    public String transfer(boolean fail) {
        System.out.println("target: begin");

        if (fail) {
            throw new IllegalArgumentException("denied");
        }

        System.out.println("target: success");
        return "OK";
    }
}
~~~

Method `transfer(false)` trả String `"OK"`; method `transfer(true)` ném `IllegalArgumentException("denied")`. Trong cả hai trường hợp, method được gọi qua một Spring AOP proxy.

### 7.4. AuditAspect.java — sử dụng @Order

~~~java
package example;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(10)
public class AuditAspect {

    @Around("execution(public * example.TransferService.transfer(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("audit(10): enter");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("audit(10): exit");
        }
    }
}
~~~

Vì AuditAspect có precedence **10**, nó sẽ bao ngoài MetricsAspect(20). `@Around` trả đúng giá trị của `proceed()` hoặc cho exception gốc tiếp tục lan truyền, không tự short-circuit.

### 7.5. MetricsAspect.java — triển khai Ordered

~~~java
package example;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;

@Aspect
public class MetricsAspect implements Ordered {

    @Override
    public int getOrder() {
        return 20;
    }

    @Around("execution(public * example.TransferService.transfer(..))")
    public Object metrics(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("metrics(20): enter");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("metrics(20): exit");
        }
    }
}
~~~

`Ordered.getOrder()` cung cấp precedence của chính aspect bean này. Trong ví dụ, value được hard-code để stdout chắc chắn; trong hệ thống thật có thể lấy từ cấu hình tại lúc bean được tạo, rồi giữ ổn định.

### 7.6. AppConfig.java — đăng ký aspect beans

~~~java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public TransferService transferService() {
        return new TransferService();
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
~~~

**Vì sao cần @Bean?** Hai class có `@Aspect` nhưng không có `@Component`. `@Bean` tạo **hai Spring aspect beans** để auto-proxy creator khám phá advisor của chúng. `@EnableAspectJAutoProxy` bật Spring AOP support. Với `proxyTargetClass = true`, Spring dùng class-based proxy để client lấy bean theo `TransferService.class`; không có interface bắt buộc trong ví dụ.

**Không cần đảo vị trí các phương thức @Bean để tạo thứ tự:** chính `@Order(10)` và `getOrder()==20` mới là hợp đồng ordering giữa hai aspect.

### 7.7. Main.java

~~~java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            TransferService service = context.getBean(TransferService.class);
            System.out.println("spring AOP proxy: "
                + AopUtils.isAopProxy(service));

            System.out.println("-- successful transfer --");
            System.out.println("caller: " + service.transfer(false));

            System.out.println("-- failed transfer --");
            try {
                service.transfer(true);
            } catch (IllegalArgumentException ex) {
                System.out.println("caller caught: " + ex.getMessage());
            }
        }
    }
}
~~~

Chạy trong thư mục có pom.xml:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
~~~

### 7.8. Stdout mong đợi

~~~text
spring AOP proxy: true
-- successful transfer --
audit(10): enter
metrics(20): enter
target: begin
target: success
metrics(20): exit
audit(10): exit
caller: OK
-- failed transfer --
audit(10): enter
metrics(20): enter
target: begin
metrics(20): exit
audit(10): exit
caller caught: denied
~~~

Maven hoặc Spring có thể xuất một vài thông báo môi trường; phần trên chỉ ghi những dòng `System.out.println` của project.

### 7.9. Phân tích theo chiều vào và ra

**Success path:**

1. Caller gọi `service.transfer(false)` trên Spring AOP proxy.
2. AuditAspect(10) được vào trước, in `audit(10): enter`.
3. `pjp.proceed()` của audit chuyển sang MetricsAspect(20), in `metrics(20): enter`.
4. `pjp.proceed()` của metrics tới target, in `target: begin` và `target: success`, rồi trả `"OK"`.
5. MetricsAspect chạy `finally`: in `metrics(20): exit`.
6. AuditAspect chạy `finally`: in `audit(10): exit`.
7. Caller nhận `"OK"`. Không aspect nào đã thay return value.

**Failure path:**

1. AuditAspect(10) rồi MetricsAspect(20) vào theo cùng thứ tự.
2. Target in `target: begin` và ném `IllegalArgumentException("denied")`.
3. MetricsAspect(20) thực thi `finally` trước.
4. AuditAspect(10) thực thi `finally` sau.
5. Caller bắt **cùng exception nghiệp vụ** và in `caller caught: denied`.

Hai nhánh đều xác nhận: precedence cao **vào trước**, **ra sau**. Nó không có nghĩa “audit hoàn tất tất cả việc của nó trước khi metrics bắt đầu”.

## 8. Thử đổi thứ tự để kiểm chứng

Thay một dòng trong file `AuditAspect.java`:

~~~java
@Order(30) // Thay cho @Order(10)
~~~

Giữ `MetricsAspect.getOrder() == 20`. **Lúc này MetricsAspect(20) có precedence cao hơn AuditAspect(30)**; tức là metrics bao ngoài audit. Sau khi sửa `@Order`, compile/run lại context mới.

Các dòng nội bộ của success path có thứ tự:

~~~text
metrics(20): enter
audit(10): enter
target: begin
target: success
audit(10): exit
metrics(20): exit
~~~

**Chú ý nhãn log:** nếu bạn chỉ sửa annotation thành `@Order(30)` nhưng **không sửa chuỗi `"audit(10)"` trong `System.out.println`**, chữ `audit(10)` ở output vẫn giữ nguyên — đây là nhãn literal trong code, **không phải order hiện tại**. Để output thể hiện đúng cấu hình đã thay, sửa nhãn thành `audit(30)` đồng thời:

~~~text
metrics(20): enter
audit(30): enter
target: begin
target: success
audit(30): exit
metrics(20): exit
~~~

**Đoạn này là biến thể cấu hình**, không phải stdout mặc định của project mục 7. Việc đảo thứ tự không nhất thiết cần sửa source code của MetricsAspect hay target service.

Tương tự, bạn có thể giữ `@Order(10)` nhưng cho `MetricsAspect.getOrder()` trả 5; khi đó MetricsAspect có precedence cao hơn AuditAspect. Giá trị và chiều so sánh là yếu tố quyết định, không phải cách biểu diễn bằng annotation hay interface.

## 9. Phân biệt ordering giữa aspects và bên trong cùng @Aspect

Spring Framework có quy tắc riêng cho advice methods **khác loại trong cùng một class `@Aspect`**.

Theo tài liệu Spring hiện hành, precedence giữa những advice type **trong cùng một aspect** được gán (từ cao đến thấp):

1. `@Around`
2. `@Before`
3. `@After`
4. `@AfterReturning`
5. `@AfterThrowing`

**Sắc thái đặc biệt:** Mặc dù `@After` có precedence ở trên `@AfterReturning` và `@AfterThrowing` trong cùng aspect, nó có **finally semantics**, nên khi thực thi trên đường ra nó **xuất hiện sau** `@AfterReturning` hoặc `@AfterThrowing` tương ứng. Đừng nhìn bảng precedence mà suy rằng dòng log `@After` luôn in trước các loại after khác.

### 9.1. Nhiều advice cùng loại trong cùng aspect

Nếu bạn khai báo **hai `@Before` hoặc hai `@After` cùng loại**, đều match một join point trong **cùng class `@Aspect`**, thứ tự giữa chúng **không được Spring bảo đảm** vì Java reflection không cung cấp thứ tự khai báo source đáng tin cậy.

Ví dụ không nên dựa vào:

~~~java
@Aspect
public class ProblematicAspect {

    @Before("execution(* example.TransferService.transfer(..))")
    public void stepOne() {
        System.out.println("one");
    }

    @Before("execution(* example.TransferService.transfer(..))")
    public void stepTwo() {
        System.out.println("two");
    }
}
~~~

Bạn không thể chỉ đặt `stepOne` trước `stepTwo` trong source file rồi tuyên bố `one` luôn in trước `two`.

**Cách sửa tốt hơn:**

- Nếu hai thao tác tạo thành một sequence nghiệp vụ, **gộp chúng vào một advice method** và gọi theo thứ tự tường minh.
- Nếu chúng là hai concern độc lập có thứ tự thực sự quan trọng, **tách thành hai Spring aspect classes** rồi dùng `@Order` / `Ordered` để chỉ định precedence.
- Không cố gắn `@Order` lên từng advice method rồi suy rằng Spring AOP sẽ sắp xếp các method ấy như hai aspect độc lập. Ordering được mô tả ở cấp **aspect** và theo **advice type**.

Các snippet trên minh họa lỗi tư duy, không thuộc project chạy hoàn chỉnh ở mục 7.

## 10. Advice precedence không phải thứ tự khởi tạo Spring beans

Dễ nhầm hai khái niệm:

| Câu hỏi | Cơ chế cần xét |
| --- | --- |
| Aspect nào chạy trước trên đường vào `TransferService.transfer()`? | `@Order` / `Ordered.getOrder()` và advisor ordering |
| Singleton bean A có được khởi tạo trước singleton B không? | Dependency injection graph, `@DependsOn` hoặc lifecycle infrastructure |
| Spring inject nhiều beans vào `List<Service>` theo thứ tự nào? | Quy tắc dependency ordering/annotations ở injection point |
| Advice nào chạy sau khi target trả về? | Chiều ra của interceptor chain và advice type |
| Controller nào xử lý request trước? | Web routing/handler mapping và cấu hình web, không phải `@Order` trên aspect |
| Một async job chạy trước job khác không? | Executor/scheduling/concurrency, không được bảo đảm bằng aspect precedence |

Ví dụ, `@Order(1)` trên aspect không bắt buộc Spring phải tạo bean aspect trước tất cả các service, và cũng không đảm bảo một event handler nghiệp vụ khác sẽ chạy theo order ấy.

Nếu muốn việc đăng ký advisor xảy ra đúng và ổn định, hãy đảm bảo aspect được Spring quản lý, auto-proxy được bật, và configuration được tải đầy đủ. Không dùng `@DependsOn` để “vá” thứ tự advice giữa các aspect khi vấn đề thật là precedence.

## 11. Kết hợp với transaction, cache và security advisors

Trong ứng dụng thực tế, Spring AOP proxy có thể mang cả:

- Custom aspect của bạn: audit, tracing, metrics, validation.
- Infrastructure advisors: declarative transactions, caching, method security và các cơ chế khác.
- Proxy/interceptor do framework integration hoặc code dùng `ProxyFactory` tạo ra.

Khi đặt `@Order(10)` trên `AuditAspect`, bạn **đã** mô tả precedence **của aspect ấy**. Nhưng chưa chứng minh được thứ tự cuối cùng giữa audit và **mọi** advisor transaction/cache/security trong hệ thống.

### 11.1. Ví dụ với transaction

Giả sử:

~~~text
Caller
  -> Audit @Around
      -> Transaction interceptor
          -> TransferService.transfer()
          -> commit/rollback
      -> audit exit
~~~

Ở cấu trúc này audit exit diễn ra sau khi transaction interceptor bên trong hoàn tất xử lý commit/rollback. Nhưng nếu thứ tự đảo:

~~~text
Caller
  -> Transaction interceptor
      -> Audit @Around
          -> TransferService.transfer()
      -> transaction commit/rollback
~~~

thì audit exit có thể xảy ra **trước** commit/rollback.

Hệ quả:

- Nếu audit đang log “transaction committed” chỉ vì target trả bình thường, nội dung log có thể sai khi audit nằm **bên trong** transaction advisor và commit xảy ra sau đó.
- Nếu timing aspect cần tính cả commit time, vị trí bên ngoài transaction interceptor có thể quan trọng.
- Nếu caching aspect shortcut phía ngoài, audit/transaction advisor ở trong có thể không được vào.
- Khi lỗi của target được inner transaction advisor xử lý/đổi exception, outer advice có thể quan sát kết quả khác với việc gọi target trực tiếp.

**Không đoán ordering** chỉ từ annotation trong source: xem cấu hình transaction/cache advisors và kiểm thử actual Spring context.

### 11.2. Giá trị mặc định của infrastructure advisors

Các infrastructure components có thể sử dụng `Ordered.LOWEST_PRECEDENCE` hoặc configurable order values, nhưng giá trị cụ thể có thể phụ thuộc annotation/configuration và phiên bản Spring. Ví dụ, `@EnableTransactionManagement(order = ...)` có thuộc tính order để điều chỉnh transaction advisor trong proxy mode.

Đừng tùy tiện đặt tất cả aspects thành `HIGHEST_PRECEDENCE`: một số cơ chế infrastructure như async, validation, transactions hoặc security có các ràng buộc ordering đặc thù. Cần đưa ra **mong đợi nghiệp vụ rõ ràng**, cấu hình chính xác, rồi kiểm thử.

## 12. Điểm giới hạn: chỉ aspect cùng thực sự vào lời gọi mới được sắp xếp

`@Order` không làm advice chạy khi những điều kiện AOP cơ bản không thỏa.

### 12.1. Pointcut không match

Nếu AuditAspect chỉ match `TransferService.transfer(..)` còn MetricsAspect chỉ match `PaymentService.pay(..)`, hai aspect **không cùng tác động** lên một invocation. So sánh 10 và 20 lúc đó không tạo ra trace lồng nhau cho một method đơn lẻ.

### 12.2. Aspect chưa là Spring bean

~~~java
@Aspect
@Order(1)
public class AuditAspect {
    // ...
}
~~~

Chỉ annotation trên class không tự đưa instance vào Spring container. Cần `@Component` trong component scanning hoặc `@Bean` (như project mục 7), cùng cấu hình Spring AOP phù hợp.

### 12.3. Self-invocation và target ngoài container

~~~java
public void outer() {
    this.transfer(false);
}
~~~

Nếu invocation bên ngoài nhắm `outer()`, còn `this.transfer(false)` là lời gọi nội bộ trên target, Spring AOP proxy **không được đi qua lần hai**. Order values không thể cứu self-invocation. Điều này vẫn đúng với JDK dynamic proxy hoặc CGLIB class-based proxy.

Tương tự, `new TransferService().transfer(false)` ở ngoài Spring container không tự chịu AOP advice của các Spring-managed aspects.

### 12.4. Around advice short-circuit

Một aspect có precedence cao hơn có thể dùng `@Around` và **không gọi `proceed()`**, ví dụ khi cache hit hoặc policy từ chối lời gọi. Lúc đó inner aspect và target có thể không chạy dù đã được gán order thấp hơn.

~~~java
// Snippet minh họa, không thuộc project chạy độc lập ở mục 7.
@Around("execution(* example.TransferService.transfer(..))")
public Object shortCircuit(ProceedingJoinPoint pjp) {
    return "CACHED";
}
~~~

Không được suy rằng “aspect B(20) luôn chạy sau A(10)” nếu A(10) đã trả về mà không proceed; B không hề được vào invocation ấy.

### 12.5. Nested proxies và @Order

Nếu lập trình viên **tự bọc một AOP proxy trong một AOP proxy khác** bằng `ProxyFactory`, đó là hai tầng vật lý. `@Order` chỉ sắp xếp các advisors ở nơi Spring quản lý chuỗi thích hợp; nó không tự đảo việc một target source của outer đang trỏ tới inner proxy. Phải xác định client đang giữ proxy nào, rồi đọc advisor chain và target source thực tế.

## 13. Kiểm tra advisor chain qua API của Spring

Trong những bean expose `Advised`, bạn có thể xem danh sách advisors ở **lớp proxy hiện tại**:

~~~java
// Đoạn bổ sung cho Main.java mục 7; không phải chương trình độc lập.
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;

if (service instanceof Advised advised) {
    int position = 0;
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println(position++ + ": "
            + advisor.getClass().getSimpleName());
    }
}
~~~

Các lưu ý:

- Đây là cách inspect runtime, **không dùng output class names làm hợp đồng business**; Spring version có thể thay đổi classes/metadata.
- `Advised.getAdvisors()` liệt kê advisor ở **proxy đang xét**, không nhất thiết các advisors của một inner proxy khác.
- Một Spring AOP proxy có thể chứa advisor đặc biệt hoặc infrastructure advisor khác.
- Dòng đầu tiên trong danh sách không phải lúc nào cũng là một `@Aspect` business advice.
- `AopUtils.isAopProxy(service) == true` không chứng minh method bất kỳ đều match một advisor.
- Một proxy có thể được cấu hình opaque và không expose `Advised`; dùng `instanceof` kiểm tra trước khi cast.
- Không sửa `Advised` hay target source của production proxy chỉ để xem thứ tự.

**Test có giá trị hơn đoán:** Dùng một Spring ApplicationContext thực, gọi method qua bean reference, ghi sự kiện vào một danh sách thread-safe/test sink, và assert chuỗi events trên normal/exceptional paths.

## 14. Kiểm thử precedence và sự ổn định khi nâng cấp Spring

### 14.1. Ma trận kiểm thử

| Trường hợp | Mong đợi |
| --- | --- |
| Hai aspects cùng match, A(10), B(20), target thành công | A enter → B enter → target → B exit → A exit |
| Hai aspects cùng match, A(10), B(20), target ném lỗi | A enter → B enter → target throw → B finally → A finally → caller catch |
| Đổi A thành 30, B giữ 20 | B bao ngoài A |
| B pointcut không match target | Chỉ A advice chạy |
| A return sớm không proceed | B và target không nhất thiết được gọi |
| Hai aspects cùng order nhưng cần thứ tự cố định | **Không được dựa vào**; đặt giá trị khác |
| A và B cùng loại @Before trong **cùng aspect** | Thứ tự method không được bảo đảm chỉ bởi thứ tự khai báo |
| Caller gọi method bằng `this` từ target | Không tạo thêm invocation qua Spring proxy |
| Advisor transaction được thêm vào context | Cần xét actual chain, không giữ giả định chỉ có A và B |

### 14.2. Test không chỉ dựa vào stdout

Trong project bài học, stdout là cách dễ quan sát. Trong codebase lớn, bạn có thể đưa sự kiện vào một `List<String>` trong test-scoped sink rồi assert:

~~~java
// Snippet kiểm thử ý tưởng, cần cấu hình và test sink riêng.
assertEquals(
    List.of(
        "audit-enter", "metrics-enter", "target",
        "metrics-exit", "audit-exit"
    ),
    events
);
~~~

Với failure path, nhớ assert cả **kiểu exception và trạng thái nghiệp vụ**. Không dùng việc các log in đúng thứ tự để suy ra transaction đã commit đúng, security đã chạy đúng hoặc business operation không bị gọi hai lần.

### 14.3. Nên định nghĩa order theo một hợp đồng dễ đọc

Nếu nhiều nhóm phát triển cùng viết aspects, nên thống nhất dải order và lý do, ví dụ:

~~~java
public final class AspectOrder {
    private AspectOrder() {}

    public static final int AUDIT = 10;
    public static final int METRICS = 20;
}
~~~

Sau đó:

~~~java
@Aspect
@Order(AspectOrder.AUDIT)
public class AuditAspect {
    // ...
}
~~~

Đây là **snippet thiết kế**, không thuộc project độc lập ở mục 7. Một hằng giúp tránh “magic numbers” trùng ngẫu nhiên. Tuy nhiên, các dải order chỉ có giá trị khi được review cùng **infrastructure ordering**; không coi chúng là chuẩn mặc định của Spring.

## 15. Những lỗi thường gặp

| Hiện tượng | Cách hiểu sai | Cách xử lý đúng |
| --- | --- | --- |
| @Order(10) in log exit sau @Order(20) | @Order bị đảo | Precedence cao **ra sau**; mô hình wrapper bình thường |
| @Order(10) và Ordered(20) không có thứ tự mong đợi | Annotation và interface không tương thích | Chúng cùng mô tả precedence; kiểm tra pointcut, bean registration và actual chain |
| Có @Order nhưng advice không chạy | Ordering quyết định matching | Check @Aspect bean, proxy, pointcut và đường gọi |
| Dùng @Order trên các advice methods cùng @Aspect | Nghĩ sẽ sắp xếp theo từng method | Dùng quy tắc advice type, gộp method hoặc tách aspects có order riêng |
| Hai aspects cùng @Order(10) | Tưởng order bằng nhau vẫn theo tên class | Khi cần thứ tự xác định, dùng giá trị khác và test |
| Aspect có @Order(1), service vẫn init trước aspect | Tưởng @Order là startup order | Dependency graph/@DependsOn xử lý khởi tạo; không gắn với aspect precedence |
| Audit log “committed” nhưng transaction rollback sau | Quên thứ tự với transaction advisor | Xác minh audit nằm trong/ngoài transaction và commit event đúng |
| Metrics không xuất hiện dù target đã chạy | Pointcut không match hoặc reference gọi bỏ qua proxy | Kiểm tra target bean, pointcut, self-invocation và actual proxy |
| Target không chạy dù outer aspect in “enter” | Inner advisor hoặc outer around short-circuit | Tìm return sớm/không gọi proceed |
| CGLIB đã bật nhưng self-call vẫn không có advice | Tưởng class proxy can thiệp mọi lệnh gọi nội bộ | Spring AOP vẫn proxy-based; gọi qua bean khác khi cần |
| Đổi @Order lúc runtime mà chain không đổi | Cho rằng Spring sắp xếp lại mỗi invocation | Cấu hình ổn định trước khởi tạo proxy, sau đó tạo context mới/test |
| Có hai lần audit | Tưởng do @Order | Kiểm tra duplicate aspect beans, nested proxies, repeated calls và advisors |
| @Order âm bị hiểu là thấp ưu tiên | Nhầm ý nghĩa số lớn hơn | Số nhỏ hơn mới có precedence cao hơn |
| Advice order khác sau nâng cấp | Dựa vào internals/reflection order | Viết integration tests theo contract và ưu tiên nguồn docs phiên bản đang chạy |

## 16. Câu hỏi tự luyện tập

**Câu 1.** AuditAspect có `@Order(5)`, MetricsAspect có `getOrder() == 50`. Aspect nào được vào trước khi caller gọi method qua proxy và cả hai pointcut đều match?

**Đáp án:** AuditAspect có precedence cao hơn vì `5 < 50`, nên được vào trước. Nếu cả hai dùng around/finally, AuditAspect hoàn tất sau MetricsAspect.

**Câu 2.** Vì sao `@Order(5)` “vào trước nhưng ra sau” chứ không phải tất cả code chạy trước?

**Đáp án:** Vì aspect precedence cao bao ngoài chain phía trong. Lệnh `proceed()` chuyển vào các interceptor còn lại, rồi returning/exception đi ngược qua các wrapper.

**Câu 3.** Có hai `@Before` methods trong cùng một `@Aspect`, khai báo `first()` trước `second()`. Spring bảo đảm first chạy trước không?

**Đáp án:** Không. Thứ tự khai báo Java không đủ làm contract. Gộp vào một advice hoặc tách hai aspect classes có precedence khác nhau.

**Câu 4.** Hai aspect khác nhau cùng `@Order(10)`, nhưng trong một lần chạy A in trước B. Có thể ghi quy tắc này vào tài liệu hệ thống không?

**Đáp án:** Không nên. Khi precedence bằng nhau, không dựa vào thứ tự quan sát ngẫu nhiên nếu business cần ổn định; đặt hai order values khác nhau.

**Câu 5.** `@Order(1)` trên một aspect có ép Spring khởi tạo aspect bean trước tất cả service beans không?

**Đáp án:** Không. Advice precedence khác với bean lifecycle ordering. Xem dependency graph hoặc lifecycle/depends-on nếu cần thứ tự tạo bean.

**Câu 6.** Aspect A(10) dùng `@Around` trả về cached value mà không gọi `proceed()`. Aspect B(20) có chắc chạy không?

**Đáp án:** Không. Nếu A bao ngoài B và return sớm, invocation không tới B; order chỉ có ý nghĩa khi phần chain ấy thực sự được vào.

**Câu 7.** Custom audit aspect được đặt `@Order(0)`. Điều này có bảo đảm audit chạy sau commit của transaction không?

**Đáp án:** Không. Phải biết precedence và vị trí của transaction advisor tương ứng; nếu audit chạy bên trong transaction, audit exit có thể trước commit.

**Câu 8.** Nếu `@After` và `@AfterReturning` cùng xuất hiện trong một @Aspect, có thể dùng cách sắp xếp giữa hai aspect để khẳng định @After chạy trước @AfterReturning không?

**Đáp án:** Không. Đó là quy tắc trong cùng aspect, với after-finally semantics: `@After` thực tế chạy sau after-returning hoặc after-throwing tương ứng trên đường ra theo Spring Framework 6.2.

**Câu 9.** Một method được gọi bằng `this.method()` trong target bean. Thay `@Order(10)` thành `@Order(-10)` có làm advice chạy không?

**Đáp án:** Không. Self-invocation vẫn bypass Spring proxy; đổi precedence không sửa đường gọi.

**Câu 10.** Vì sao dùng cả `@Order` và `Ordered` trên cùng một aspect thường không nên?

**Đáp án:** Dễ xuất hiện hai nguồn cấu hình trái nhau. Chọn một convention rõ, tạo bean/config đúng, và test precedence trong context thực.

## 17. Nguồn tham khảo chính thức

- [Spring Framework — Declaring Advice / Advice Ordering](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): precedence giữa các aspects; dùng @Order hoặc Ordered; order thấp hơn là precedence cao hơn; quy tắc đi vào/đi ra; thứ tự advice types cùng aspect.
- [Spring Framework — Schema-based AOP / Advice Ordering](https://docs.spring.io/spring-framework/reference/6.2/core/aop/schema.html): các quy tắc tương tự khi dùng AOP XML và phân biệt advisor ordering.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): khai báo @Aspect thành Spring bean và bật @EnableAspectJAutoProxy.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB proxies, self-invocation và ranh giới advice.
- [Spring Javadoc — Ordered](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/core/Ordered.html): getOrder, HIGHEST_PRECEDENCE và LOWEST_PRECEDENCE.
- [Spring Javadoc — @Order](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/core/annotation/Order.html): metadata precedence và phân biệt với singleton startup order.
- [Spring Framework — Transaction Management Configuration](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html): order của transaction advisor và giới hạn proxy mode.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-ordering-003">Giá trị order nhỏ hơn và vị trí trong chuỗi advice</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị order nhỏ hơn và vị trí trong chuỗi advice của Spring AOP

## 1. Kết luận cần nhớ

Khi **nhiều Spring Aspect** cùng có advice khớp một method execution của Spring bean, việc xác định thứ tự thực thi rất quan trọng. Spring AOP hỗ trợ `@Order` của Spring và interface `Ordered` để khai báo mức ưu tiên giữa **các aspect khác nhau**.

**Giá trị `order` càng nhỏ → độ ưu tiên (precedence) càng cao.** Với hai aspect:

~~~java
@Aspect
@Order(10)
public class AuditAspect { /* ... */ }

@Aspect
@Order(20)
public class MetricsAspect { /* ... */ }
~~~

`AuditAspect` có precedence **cao hơn** `MetricsAspect` vì `10 < 20`. Điều đó không có nghĩa **mọi dòng code** trong `AuditAspect` chạy trước mọi dòng code của `MetricsAspect`: Spring AOP advice được lồng vào **interceptor chain**:

- Khi **đi vào** join point, advice có precedence cao hơn chạy **trước** advice có precedence thấp hơn.
- Khi **đi ra** join point (sau return hoặc khi exception truyền ngược), advice có precedence cao hơn chạy **sau** phần xử lý đi ra của advice precedence thấp hơn.

Với hai `@Around` advice, hình dung:

~~~text
Caller
  |
  v
Order 10: trước proceed()
    |
    v
  Order 20: trước proceed()
      |
      v
   Target method
      |
      v
  Order 20: sau proceed()
    |
    v
Order 10: sau proceed()
  |
  v
Caller
~~~

**Quy tắc ngắn gọn:** số nhỏ đứng **ngoài** chuỗi (outer), số lớn hơn đứng **trong** chuỗi (inner). **Vào trước – ra sau.**

Trong Spring AOP, chuỗi này áp dụng khi caller thực sự gọi **Spring-managed bean qua AOP proxy** và các advisor cùng match method. `@Order` không làm mọi bean tự xuất hiện proxy, không thay đổi pointcut và không sửa vấn đề self-invocation.

## 2. Phân biệt “order”, “precedence” và “thứ tự hoàn thành”

Ba khái niệm cần được gọi đúng tên:

| Thuật ngữ | Ý nghĩa trong Spring Aspect | Ví dụ `10` so với `20` |
| --- | --- | --- |
| `order value` | Con số do `@Order` hoặc `Ordered.getOrder()` trả về | `10` nhỏ hơn `20` |
| `precedence` | Ưu tiên của advice/aspect khi tạo chuỗi xử lý | `10` có precedence cao hơn `20` |
| `on the way in` | Giai đoạn trước khi chuỗi đi đến target method | `10` chạy trước `20` |
| `on the way out` | Giai đoạn kết quả/exception đi ngược ra caller | `20` chạy phần “ra” trước `10` |
| `completion time` | Thời điểm code advice hoàn tất | Aspect ngoài (`10`) thường hoàn tất cuối cùng nếu thực hiện logic bao quanh |

Nếu chỉ quan sát hai `@Before` advices, dễ nói thiếu chính xác rằng “order nhỏ chạy trước”. Câu đó đúng **trong trường hợp before/on-the-way-in**, nhưng không diễn tả quy tắc tổng thể khi có `@After`, `@AfterReturning`, `@AfterThrowing` hoặc `@Around`.

Đặc biệt, với hai `@Around` và `try/finally`, chương trình quan sát được nesting chính xác, không phải thứ tự ngẫu nhiên phụ thuộc thread scheduling. Ví dụ đầy đủ ở phần 5 sẽ dùng `@Order(10)`, `@Order(20)` và `@Order(30)` trên ba aspect khác nhau.

### Ví dụ không có thứ tự rõ

~~~java
@Aspect
public class AuditAspect { /* advice cùng match */ }

@Aspect
public class MetricsAspect { /* advice cùng match */ }
~~~

Khi **hai aspect khác nhau** cùng khớp một join point mà không khai báo precedence, Spring **không bảo đảm một thứ tự xác định** giữa chúng. Kết quả hiện tại có vẻ ổn trên máy dev vẫn có thể thay đổi sau khi sửa cách đăng ký bean, build hoặc nâng framework. Nếu thứ tự có ý nghĩa nghiệp vụ, phải cấu hình `@Order`/`Ordered` và viết test.

## 3. Thiết lập order trong Spring Aspect

### 3.1. `@Order` ở cấp aspect class

Dạng dễ đọc nhất:

~~~java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.core.annotation.Order;

@Aspect
@Order(10)
public class OuterAspect {
    @Around("execution(* demo.WorkService.execute(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed();
    }
}
~~~

Đây là **class Java minh họa hợp lệ** nếu project có Spring AOP/AspectJ dependency và `WorkService` tương ứng. Bản chạy với logging đầy đủ sẽ ở phần 5.

`@Order` đánh dấu **thứ tự aspect**, không phải một cách lọc bean. Class này vẫn cần được **đăng ký thành Spring bean** qua `@Bean` hoặc `@Component` + component scanning; chỉ đặt `@Aspect`/`@Order` không tự đăng ký object vào `ApplicationContext`.

### 3.2. Interface `Ordered`

Một aspect có thể tự khai báo:

~~~java
@Aspect
public class ConfigurableAuditAspect
        implements org.springframework.core.Ordered {

    @Override
    public int getOrder() {
        return 10;
    }

    @Before("execution(* demo.WorkService.execute(..))")
    public void before() {
        // ...
    }
}
~~~

Đây là class minh họa; cần import `org.aspectj.lang.annotation.Before` và đăng ký bean trong Spring context. `Ordered.getOrder()` hữu ích khi mức ưu tiên cần được biểu diễn bằng API của Spring thay vì một annotation hằng số.

**Không cần dùng cả `@Order` và `Ordered` cho cùng một aspect** trong ví dụ học tập. Nếu kết hợp, phải xem quy tắc giải quyết precedence của Spring và cấu hình bean thực tế để tránh hiểu nhầm. Hãy chọn một cơ chế, nhất quán giữa các aspect.

### 3.3. Giá trị âm, 0 và các hằng

Spring dùng `int` để mô tả order. Ví dụ:

| Giá trị | So với 0 | Độ ưu tiên tương đối |
| --- | --- | --- |
| `-100` | Nhỏ hơn | Cao hơn `0`, `10`, `100` |
| `0` | Trung gian | Cao hơn `10`, thấp hơn `-100` |
| `10` | Lớn hơn `0` | Thấp hơn `0`, cao hơn `20` |
| `20` | Lớn hơn `10` | Thấp hơn `10` |
| `Ordered.HIGHEST_PRECEDENCE` | `Integer.MIN_VALUE` | Giá trị precedence cao nhất theo convention |
| `Ordered.LOWEST_PRECEDENCE` | `Integer.MAX_VALUE` | Giá trị precedence thấp nhất theo convention |

`@Order(0)` **không phải** “không có thứ tự”; nó là một mức ưu tiên cụ thể. Cũng không có quy tắc “order âm chỉ dành cho framework”. Chọn giá trị thể hiện tương quan mà ứng dụng cần, tránh sử dụng số tuyệt đối mang ý nghĩa thần kỳ.

**Nhớ:** precedence cao là **order nhỏ**; đừng đọc “order 100 lớn hơn order 10 nên chạy trước”.

### 3.4. `@Order` không sắp xếp thứ tự tạo bean

Trong Spring Framework, `@Order` được dùng nhiều nơi, nhưng trong bài này ta xét **thứ tự các Spring AOP aspects**. Không được nhầm với:

- Thứ tự gọi constructor hoặc `@Bean` factory method;
- Thứ tự dependency injection, vốn phụ thuộc dependency graph và container lifecycle;
- Thứ tự đăng ký `BeanPostProcessor`;
- Thứ tự methods trong source file của một class `@Aspect`;
- Thứ tự thực thi giữa hai lời gọi service độc lập hoặc giữa các thread.

Muốn xác định bean nào phải khởi tạo trước bean nào, đó là vấn đề dependencies/lifecycle (ví dụ constructor injection, `@DependsOn` khi phù hợp), **không phải cách sử dụng `@Order` của aspect**.

## 4. Vị trí thực tế trong chuỗi Spring AOP

Spring AOP kết hợp các `Advisor` khớp với method execution thành một interceptor chain. Một lời gọi bình thường qua Spring proxy đi từ caller vào các advice, đến target, rồi truyền return value hoặc exception ra ngoài.

Với ba `@Around` aspects:

~~~text
                         PRECEDENCE HIGH
Caller                   Order 10 (outermost)
  |                         |
  |                         v
  |                      Order 20
  |                         |
  |                         v
  |                      Order 30
  |                         |
  |                         v
  |                    WorkService.execute()
  |                         |
  |                    Order 30: finally
  |                         |
  |                    Order 20: finally
  |                         |
  |                    Order 10: finally
  v
Caller observes result/error
                         PRECEDENCE LOW at core
~~~

Trong đó:

- `@Order(10)` bọc **cả** `@Order(20)`, `@Order(30)` và target.
- `@Order(20)` nằm giữa.
- `@Order(30)` nằm gần target nhất, tức **inner**.
- Khi method trả thành công, kết quả truyền theo chiều `30 → 20 → 10 → caller`.
- Khi method ném exception, nếu các aspects dùng `try/finally`, khối `finally` cũng chạy theo chiều `30 → 20 → 10` trước khi exception đến caller.
- Một aspect `@Around` có quyền **không gọi `proceed()`**. Nếu outer aspect short-circuit, target và các advice inner **không chạy**; `@Order` không thể buộc inner được gọi.

Đây là **chuỗi advice của cùng một invocation**, không phải một hàng đợi phân công công việc theo thời gian hoặc thứ tự hoàn thành toàn ứng dụng.

## 5. Project Java 17+ kiểm chứng order nhỏ đi vào trước/đi ra sau

Một project Maven Spring Framework thuần kiểm tra hai nhánh:

1. Method trả kết quả bình thường: cả ba around advices và target chạy.
2. Method ném exception: cả ba `finally` thoát theo thứ tự ngược, exception vẫn đến caller.

Chúng ta ghi lại sự kiện vào một **trace bean** rồi assert **toàn bộ chuỗi** trước khi in kết quả. Không cần Spring Boot, database hoặc công cụ mock.

Phiên bản minh họa: Java 17+, Maven 3.9+, Spring Framework **6.2.0**, AspectJ Weaver **1.9.22**. Đây là phiên bản khóa cho tính tái lập bài học, không khẳng định mới nhất.

~~~text
spring-aop-order-chain/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── App.java
    │   ├── EventTrace.java
    │   ├── WorkService.java
    │   ├── OuterAspect.java
    │   ├── MiddleAspect.java
    │   └── InnerAspect.java
    └── test/java/demo/
        └── AdviceOrderTest.java
~~~

### 5.1. Maven dependencies và plugins

Tạo `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-order-chain</artifactId>
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

`spring-context` có Java config/`ApplicationContext`; `spring-aop` có proxy và `AopUtils`; `aspectjweaver` cung cấp những type/API cần cho Spring diễn giải `@AspectJ` pointcut. Chỉ có dependency `aspectjweaver` không bật AspectJ bytecode weaving. `junit-jupiter` và `maven-surefire-plugin` hỗ trợ bộ test thật ở mục 5.8.

### 5.2. Trace bean dùng chung

Tạo `src/main/java/demo/EventTrace.java`:

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

public class EventTrace {
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

Project tạo **một `EventTrace` singleton trong mỗi ApplicationContext**. Service và ba aspects cùng inject trace này. Ví dụ chỉ chạy tuần tự, nên `ArrayList` đủ; production có concurrent requests cần logger hoặc collector thread-safe. `snapshot()` trả bản sao để phép so sánh thứ tự không bị ảnh hưởng bởi lần add sau đó.

### 5.3. Target method có hai đường kết thúc

Tạo `src/main/java/demo/WorkService.java`:

~~~java
package demo;

public class WorkService {
    private final EventTrace trace;

    public WorkService(EventTrace trace) {
        this.trace = trace;
    }

    public String execute(boolean fail) {
        trace.add("TARGET:enter");

        if (fail) {
            trace.add("TARGET:throw");
            throw new IllegalStateException("boom");
        }

        trace.add("TARGET:return");
        return "ok";
    }
}
~~~

`execute(false)` thêm `TARGET:enter` rồi `TARGET:return` và trả `"ok"`. `execute(true)` thêm `TARGET:enter` rồi `TARGET:throw` và ném `IllegalStateException("boom")`. Không có self-invocation hoặc thread khác trong ví dụ, giúp quan sát đúng thứ tự advice trên một lời gọi.

### 5.4. Aspect order 10 — ngoài cùng

Tạo `src/main/java/demo/OuterAspect.java`:

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(10)
public class OuterAspect {
    private final EventTrace trace;

    public OuterAspect(EventTrace trace) {
        this.trace = trace;
    }

    @Around("execution(* demo.WorkService.execute(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        trace.add("10:in");
        try {
            return point.proceed();
        } finally {
            trace.add("10:out");
        }
    }
}
~~~

`10:in` chạy khi vào đầu outer aspect. `point.proceed()` chuyển invocation vào phần còn lại của chain, nơi có `@Order(20)` và `@Order(30)`. `finally` đảm bảo `10:out` chạy **sau khi inner chain hoàn tất** hoặc truyền exception ngược lên, ngay cả khi target ném `boom`.

### 5.5. Aspect order 20 — lớp giữa

Tạo `src/main/java/demo/MiddleAspect.java`:

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(20)
public class MiddleAspect {
    private final EventTrace trace;

    public MiddleAspect(EventTrace trace) {
        this.trace = trace;
    }

    @Around("execution(* demo.WorkService.execute(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        trace.add("20:in");
        try {
            return point.proceed();
        } finally {
            trace.add("20:out");
        }
    }
}
~~~

`@Order(20)` lớn hơn `10` nên có precedence thấp hơn `OuterAspect`. Code trong aspect này được vào **sau** `10:in`, nhưng `20:out` xuất hiện **trước** `10:out`.

### 5.6. Aspect order 30 — gần target nhất

Tạo `src/main/java/demo/InnerAspect.java`:

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(30)
public class InnerAspect {
    private final EventTrace trace;

    public InnerAspect(EventTrace trace) {
        this.trace = trace;
    }

    @Around("execution(* demo.WorkService.execute(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        trace.add("30:in");
        try {
            return point.proceed();
        } finally {
            trace.add("30:out");
        }
    }
}
~~~

Trong ba aspect này, `30` là order lớn nhất và thấp precedence nhất, nằm **trong cùng**. Nó đến ngay trước target trong chuỗi minh họa, và `30:out` là dòng “ra” đầu tiên khi kết thúc target.

### 5.7. Bật Spring AOP và xác nhận hai đường đi

Tạo `src/main/java/demo/App.java`:

~~~java
package demo;

import java.util.List;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class App {
    @Bean
    public EventTrace eventTrace() {
        return new EventTrace();
    }

    @Bean
    public WorkService workService(EventTrace trace) {
        return new WorkService(trace);
    }

    @Bean
    public OuterAspect outerAspect(EventTrace trace) {
        return new OuterAspect(trace);
    }

    @Bean
    public MiddleAspect middleAspect(EventTrace trace) {
        return new MiddleAspect(trace);
    }

    @Bean
    public InnerAspect innerAspect(EventTrace trace) {
        return new InnerAspect(trace);
    }

    public static List<String> expectedSuccess() {
        return List.of(
                "10:in", "20:in", "30:in",
                "TARGET:enter", "TARGET:return",
                "30:out", "20:out", "10:out");
    }

    public static List<String> expectedFailure() {
        return List.of(
                "10:in", "20:in", "30:in",
                "TARGET:enter", "TARGET:throw",
                "30:out", "20:out", "10:out");
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            WorkService service = context.getBean(WorkService.class);
            EventTrace trace = context.getBean(EventTrace.class);

            boolean proxied = AopUtils.isAopProxy(service);
            System.out.println("PROXY: " + proxied);
            if (!proxied) {
                throw new IllegalStateException("Missing Spring AOP proxy");
            }

            String result = service.execute(false);
            List<String> success = trace.snapshot();
            if (!"ok".equals(result) || !expectedSuccess().equals(success)) {
                throw new IllegalStateException("Success chain order wrong");
            }
            System.out.println("SUCCESS: " + String.join(" > ", success));

            trace.clear();

            try {
                service.execute(true);
                throw new IllegalStateException("Expected failure");
            } catch (IllegalStateException error) {
                if (!"boom".equals(error.getMessage())) {
                    throw error;
                }
            }

            List<String> failure = trace.snapshot();
            if (!expectedFailure().equals(failure)) {
                throw new IllegalStateException("Failure chain order wrong");
            }
            System.out.println("FAILURE: " + String.join(" > ", failure));

            System.out.println("CHECK: PASS");
        }
    }
}
~~~

Vì sao ví dụ chứng minh đúng câu hỏi?

1. `@EnableAspectJAutoProxy(proxyTargetClass = true)` bật Spring AOP auto-proxying; ba `@Aspect` đều được đăng ký thành Spring bean, không chỉ được gắn annotation.
2. Cả ba advice dùng **chung một pointcut** `execution(* demo.WorkService.execute(..))`, nên cạnh tranh đúng một method join point.
3. Mỗi aspect có **order khác nhau**, không có hòa điểm gây mơ hồ. `10` phải được vào trước `20`, rồi `30`.
4. `try/finally` ở mỗi `@Around` mô tả **chiều đi ra** rõ ràng và còn hoạt động trên nhánh exception.
5. `expectedSuccess()` và `expectedFailure()` xác định toàn bộ chuỗi trước; chương trình chủ động ném lỗi khi trace sai, không chỉ in một câu `PASS` vô điều kiện.
6. `trace.clear()` loại bỏ sự kiện success trước khi thử failure, tránh ghi đè kết luận do dữ liệu cũ.
7. Exception `"boom"` được bắt **ở caller**, sau khi ba khối `finally` đã chạy; advice chỉ ghi dấu, không nuốt hay biến đổi exception.
8. Đây là **Spring proxy-based AOP**: không có AspectJ compiler/agent hay load-time weaving.

### Lệnh chạy

~~~bash
mvn -q compile exec:java
~~~

Các dòng do ứng dụng `System.out.println` in ra khi chạy thành công:

~~~text
PROXY: true
SUCCESS: 10:in > 20:in > 30:in > TARGET:enter > TARGET:return > 30:out > 20:out > 10:out
FAILURE: 10:in > 20:in > 30:in > TARGET:enter > TARGET:throw > 30:out > 20:out > 10:out
CHECK: PASS
~~~

Maven và logger có thể in thêm dòng bên ngoài các dòng ứng dụng này.

**Hai điều nổi bật nhất**: order nhỏ xuất hiện trước ở đoạn `in`, nhưng xuất hiện sau ở đoạn `out`. Nếu chỉ assert `10:in` trước `20:in` mà bỏ các dấu `out`, test mới xác nhận **một nửa** semantics ordering.

### 5.8. Chạy integration tests bằng JUnit Jupiter

File `src/test/java/demo/AdviceOrderTest.java`:

~~~java
package demo;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdviceOrderTest {
    @Test
    void smallerOrderEntersFirstAndExitsLast() {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            WorkService service = context.getBean(WorkService.class);
            EventTrace trace = context.getBean(EventTrace.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals("ok", service.execute(false));
            assertEquals(App.expectedSuccess(), trace.snapshot());
        }
    }

    @Test
    void allFinallyBlocksExitInReverseOrderOnException() {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            WorkService service = context.getBean(WorkService.class);
            EventTrace trace = context.getBean(EventTrace.class);

            assertTrue(AopUtils.isAopProxy(service));

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> service.execute(true));

            assertEquals("boom", error.getMessage());
            assertEquals(App.expectedFailure(), trace.snapshot());
        }
    }
}
~~~

Chạy:

~~~bash
mvn clean test
~~~

Nếu cả hai test pass, trong báo cáo Surefire sẽ có chỉ số tương tự:

~~~text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
~~~

Không coi đây là log từ máy của người đọc: đây là **kết quả mong đợi** khi đã cài Maven/JDK đúng và mã chạy như khai báo. Chương trình có thể hiển thị thêm log build/framework, nhưng assertion phải đúng trên cả success và exception.

## 6. Với @Before và @After, thứ tự thể hiện thế nào?

Đối với hai **aspects khác nhau** có order khác nhau:

| Loại advice | Order 10 vs order 20 | Cách hiểu |
| --- | --- | --- |
| Hai `@Before` cùng match | `@Before` của `10` chạy trước `20` | “On the way in” |
| Hai `@AfterReturning` cùng match và target return bình thường | `@AfterReturning` của `20` chạy trước `10` | “On the way out” |
| Hai `@AfterThrowing` cùng match và target ném lỗi phù hợp | `@AfterThrowing` của `20` chạy trước `10` | Exception truyền ngược ra outer |
| Hai `@After` cùng match | `@After` của `20` chạy trước `10` | After-finally ở chiều đi ra |
| Hai `@Around` cùng match | `10:in → 20:in → target → 20:out → 10:out` | Outer/inner nesting |

Bảng này là mô hình giải thích **khi các advice đều áp dụng và thật sự chạy**. Với target ném exception, `@AfterReturning` không chạy; với target return bình thường, `@AfterThrowing` không chạy. `@After` có thể chạy trên cả hai đường. Với `@Around`, nếu một advice chủ động short-circuit hoặc thay exception/return value, kết quả của phần inner chain có thể thay đổi; không suy một chuỗi cố định khi advice không gọi `proceed()`.

### Advice ordering khác với “chạy trước/sau cùng một phương thức Java”

Ví dụ:

~~~java
@Aspect
@Order(10)
class A {
    @Before("execution(* demo.WorkService.execute(..))")
    public void before() { /* ... */ }
}

@Aspect
@Order(20)
class B {
    @AfterReturning("execution(* demo.WorkService.execute(..))")
    public void after() { /* ... */ }
}
~~~

Hai class này là **snippet minh họa**, chưa bao gồm package/import/@Bean và không nên thêm đồng thời vào project chính. Ở đây `A.before` chạy trước method và `B.after` chạy sau method, nhưng **không thể** dùng ví dụ đó để kết luận order nhỏ luôn chạy trước mọi advice: hai loại advice có thời điểm kích hoạt vốn khác nhau. Muốn quan sát đúng precedence giữa các aspects, so sánh hai advice cùng loại hoặc trace đầy đủ một nested `@Around` chain.

## 7. Trong cùng một @Aspect class, @Order không sắp xếp từng method advice

Một lỗi rất phổ biến là hiểu `@Order(10)` trên aspect class sẽ khiến method advice nằm trên cùng của source chạy trước method advice nằm dưới.

~~~java
@Aspect
@Order(10)
public class MixedAspect {
    @Before("execution(* demo.WorkService.execute(..))")
    public void first() {
    }

    @Before("execution(* demo.WorkService.execute(..))")
    public void second() {
    }
}
~~~

Đây là **class minh họa** chưa được đăng ký Spring bean. Cả `first()` và `second()` là `@Before` có cùng type và cùng khớp một join point **trong cùng aspect**. Spring **không bảo đảm thứ tự giữa hai advice cùng loại** chỉ dựa trên vị trí khai báo trong source file: reflection Java không bảo toàn thứ tự method khai báo theo nghĩa cần thiết để suy precedence.

Theo tài liệu Spring Framework 6.2, với các advice **khác loại trong cùng `@Aspect`**, precedence được thiết lập theo advice type, từ cao đến thấp:

~~~text
@Around > @Before > @After > @AfterReturning > @AfterThrowing
~~~

Nhưng `@After` có semantics **after-finally**, nên dù precedence type được liệt kê trước `@AfterReturning`/`@AfterThrowing`, khi join point đi ra nó **thực tế được gọi sau** các advice xử lý return/throw tương ứng. Đừng chỉ nhớ danh sách type precedence rồi suy máy móc thứ tự log.

Nếu cần thứ tự tường minh giữa hai lời xử lý `@Before`, lựa chọn thường dễ kiểm soát hơn là:

1. Gộp cả hai bước vào **một advice method** theo trình tự Java rõ ràng; hoặc
2. Đưa chúng vào **hai aspect classes** khác nhau, mỗi class một `@Order` rõ ràng và có integration test.

Không nên đặt `@Order` ở hai advice methods riêng rồi mặc định Spring AOP coi chúng như hai aspects khác nhau: ordering giữa aspects được định nghĩa theo annotation/`Ordered` của **aspect bean**, không phải các line number trong source.

## 8. Khi order bằng nhau hoặc không khai báo order

### 8.1. Hai aspect có cùng order

~~~java
@Aspect
@Order(10)
class FirstAspect { /* ... */ }

@Aspect
@Order(10)
class SecondAspect { /* ... */ }
~~~

Hai aspect này cùng precedence value. **Không dùng tên class, tên bean hoặc thứ tự method trong source để suy thứ tự luôn xác định**. Nếu logic cần `FirstAspect` bao `SecondAspect`, đặt giá trị order **khác nhau** và xác minh bằng test. Còn nếu thứ tự không có ý nghĩa, code phải độc lập với việc ai chạy trước.

### 8.2. Một aspect không khai báo order

Aspect không khai báo `@Order` hoặc `Ordered` không tạo ra cam kết precedence tương đối mà bạn có thể dựa vào với các aspect khác. Spring có convention default ordering, nhưng hệ thống còn có thể có advisors từ transaction, cache, security hoặc cơ chế tạo proxy khác.

Nếu order giữa custom aspects và infrastructure advisors có ý nghĩa, xác định **cấu hình order hiệu lực của từng advisor** (ví dụ một vài `@Enable...` infrastructure có thuộc tính `order` riêng) và test trên context thực, không dựa vào một “mặc định chắc chắn” chưa kiểm tra.

### 8.3. Đừng dùng mức order trùng nhau làm phụ thuộc ngầm

Một số thay đổi tưởng nhỏ như thêm aspect audit, đăng ký hạ tầng transaction hoặc sửa classpath có thể thay đổi thứ tự khi nhiều advisors có cùng precedence. Cách thiết kế an toàn hơn:

- Các advice có side effects nhạy cảm nên ít phụ thuộc thứ tự khi có thể.
- Nếu phải phụ thuộc thứ tự, đặt cấu hình tường minh, giải thích bằng test.
- Không dựa vào việc IDE liệt kê bean theo một thứ tự nào đó để suy thứ tự interceptor chain.

## 9. Tác động khi nhiều advisor cùng áp dụng: transaction, security, logging

Trong một ứng dụng Spring, không chỉ custom `@Aspect` mới tạo advice. Các cơ chế như `@Transactional`, cache và security có thể tham gia chuỗi proxy/advisor, tùy cấu hình.

Ví dụ lý thuyết (không phải project transaction chạy độc lập):

~~~text
Logging @Around        (outer)
    |
    v
Transaction interceptor (inner)
    |
    v
Business method
~~~

Nếu logging bao ngoài transaction interceptor:

- Phần `before` của logging xảy ra **trước khi** phần mở transaction ở trong được tiến hành.
- Phần `after/finally` của logging có thể xảy ra **sau khi** phần transaction interceptor trong cùng chain đã hoàn tất.
- Nếu đảo thứ tự, logger nằm **bên trong** transaction boundary và các dấu thời gian/phạm vi lỗi nó thấy có thể khác.

Không suy ra từ ví dụ này rằng **mọi cấu hình `@Transactional` đều tự động có order 20**. Order hiệu lực phụ thuộc Spring configuration, các advisor và ứng dụng. Spring Framework cho phép cấu hình thứ tự của transaction advisor thông qua hạ tầng transaction (ví dụ thuộc tính `order` ở `@EnableTransactionManagement` cho proxy advice), nhưng phải đọc cấu hình thực của dự án trước khi kết luận.

Khi cần xác minh:

1. Xác định bean target có thật sự là Spring AOP proxy.
2. Xác định các advisors nào match và thứ tự mà framework đã áp dụng.
3. Đặt `@Order` cho custom aspects nếu tương quan với advisor khác cần được ràng buộc.
4. Viết integration test qua Spring bean, không test object tự tạo bằng `new`.
5. Quan sát các mốc có ý nghĩa, ví dụ “transaction active trước/giữa/sau” bằng API phù hợp, thay vì suy qua một dòng log có thể chạy ở chỗ khác.

Thứ tự advisor không thay thế cấu hình transaction propagation/isolation/rollback. Đây là một khía cạnh của **chuỗi Spring AOP**, không phải toàn bộ ngữ nghĩa giao dịch.

## 10. Sai lầm và cách khắc phục

| Hiểu nhầm | Vì sao sai | Cách kiểm tra/sửa |
| --- | --- | --- |
| “`@Order(100)` ưu tiên hơn `@Order(1)` vì số lớn hơn.” | Quy tắc đảo lại: số **nhỏ hơn** precedence **cao hơn** | Dùng 10/20 test vào trước, ra sau |
| “Order nhỏ luôn là advice chạy xong trước.” | Outer advice hoàn tất sau inner nếu nó bao `proceed()` | Ghi cả `in` và `out` |
| “`@Order` thay thứ tự method trong cùng một `@Aspect`.” | `@Order` chủ yếu điều khiển precedence giữa aspects | Gộp advice hoặc tách thành aspects riêng |
| “Hai `@Before` trong cùng aspect chạy theo thứ tự dòng code.” | Reflection không cho phép dựa trên source declaration order | Không dựa vào line order |
| “Tôi đã đặt order nên aspect chắc chắn chạy.” | Vẫn cần aspect bean, Spring AOP proxy và pointcut match | Kiểm tra `AopUtils.isAopProxy` và event counter |
| “Order khác nhau bảo đảm target luôn chạy.” | `@Around` có thể không gọi `proceed()` | Test both target and advice events |
| “Order sửa được self-invocation.” | `this.otherMethod()` không qua Spring proxy | Gọi qua Spring-managed collaborator |
| “`@Order` định nghĩa bean nào được tạo trước.” | Đây không phải cơ chế quản lý dependency/lifecycle | Dùng constructor dependencies/bean lifecycle APIs |
| “Không đặt order vẫn có thứ tự cố định giữa mọi aspects.” | Không có guarantee đó cho precedence của khác aspects | Khai báo order và test |
| “Advice ngoài không liên quan exception.” | `@Around` outer có thể thấy/biến đổi exception của inner chain | Dùng `try/finally` và giữ error policy đúng |
| “Bật CGLIB thay cho JDK proxy sẽ sửa thứ tự.” | Proxy strategy khác ordering; mỗi vấn đề có cấu hình riêng | Kiểm tra `@Order`/`Ordered`, advisor chain |
| “Aspect order và transaction order luôn giống nhau.” | Transaction infrastructure có precedence configuration riêng | Inspect order trên context thật |

## 11. Cách debug khi thứ tự không như dự kiến

1. **Xác minh đúng method execution:** các pointcut có match cùng một Spring bean method không? Nếu một aspect không match, không có chuyện nó được xếp sai trong chain.
2. **Xác minh reference là proxy:** `AopUtils.isAopProxy(service)` trên reference caller dùng. Object tạo bằng `new` ngoài container không auto-proxy.
3. **Xem `@Order` trên class aspect**, không chỉ trên method advice hoặc file config chứa aspect.
4. **Xem cả `Ordered`** nếu aspect implement interface này; bảo đảm giá trị precedence mà Spring thực sự dùng.
5. **Kiểm tra trùng order:** hai aspects có cùng order sẽ không đáp ứng nhu cầu ordering cứng nếu thiếu cơ chế tie-break được công bố.
6. **Log đầy đủ cả đi vào lẫn đi ra:** với `@Around` in marker trước `proceed()` và trong `finally`. Chỉ log `@Before` không chứng minh được mô hình nesting.
7. **Thử nhánh exception:** success có thể đúng nhưng advice bỏ quên `finally`, thay exception hoặc return sớm khiến đường thất bại khác.
8. **Kiểm tra `proceed()`:** một outer aspect return sớm sẽ bỏ qua inner advisors. Order value không bắt buộc gọi target.
9. **Kiểm tra nhiều infrastructure advisors:** transaction/cache/security và custom aspects có thể nằm chung chain. Đừng chỉ so hai class do nhóm tự viết.
10. **Kiểm tra cùng aspect:** advice của cùng type trong một aspect không có order xác định dựa vào source line; tách class hoặc gom logic.
11. **Đừng suy từ bean creation time:** order trong chain không đồng nghĩa order tạo Spring beans.
12. **Đóng băng semantics bằng test:** assert toàn trace theo thứ tự trước/sau và exception propagation như project phần 5.

Đo proxy tại call site:

~~~java
Object bean = context.getBean("workService");

System.out.println(
        org.springframework.aop.support.AopUtils.isAopProxy(bean));
~~~

Đây chỉ là snippet để chèn vào method đã có `ApplicationContext context`. Nếu trả `false`, thứ tự aspects không phải nguyên nhân đầu tiên cần điều tra.

## 12. Bài tập tự kiểm tra

**Bài 1.** Hai Spring aspects cùng match method `execute()`: một có `@Order(-5)`, một có `@Order(100)`. Aspect nào đi vào trước?  
→ `-5` có precedence cao hơn, vào trước; nếu cả hai bao `proceed()`, nó đi ra sau.

**Bài 2.** Có ba around aspects 1/2/3. Nếu tất cả gọi `proceed()` một lần rồi mới thoát, thứ tự?  
→ `1:in → 2:in → 3:in → target → 3:out → 2:out → 1:out`.

**Bài 3.** `@Order(10)` @Around outer bỏ qua `proceed()`. Có chắc `@Order(20)` inner chạy không?  
→ Không. Inner chain/target chỉ được tiến vào khi outer chuyển tiếp bằng `proceed()` (hoặc thực hiện cơ chế khác ngoài ví dụ).

**Bài 4.** Tại sao hai `@AfterReturning` aspects order 10/20 thường được quan sát theo chiều 20 rồi 10?  
→ Đây là chiều kết quả đi **ra** khỏi target, aspect có precedence cao hơn ở outer nên chạy sau.

**Bài 5.** Hai `@Before` methods cùng nằm trong một `@Aspect @Order(10)`. Method khai báo trước trong source có luôn chạy trước không?  
→ Không có guarantee theo source declaration order. Gộp hoặc tách thành aspects có order riêng nếu thứ tự bắt buộc.

**Bài 6.** Nếu aspect order 10 và transaction advisor order 20 cùng bọc service method, logging ngoài transaction hay trong?  
→ Nếu cả hai advisor được sắp xếp theo precedence đó và đều match, logging `@Around` order 10 ở outer, transaction order 20 ở inner. Cần **xác minh cấu hình runtime** chứ không giả định transaction advisor luôn order 20.

**Bài 7.** Nếu chỉ đặt `@Order(10)` trên aspect nhưng không đăng ký aspect làm bean, advice chạy không?  
→ Không. `@Aspect`/`@Order` không tự đăng ký vào Spring IoC; phải có aspect bean và AOP enablement.

**Bài 8.** Vì sao cần test cả normal return lẫn thrown exception?  
→ Để chứng minh chain đi ra theo thứ tự ngược và outer advice có thể thực thi `finally` trên cả hai outcome.

## 13. Checklist nhanh

- [ ] Đã nêu rõ **giá trị nhỏ hơn = precedence cao hơn**.
- [ ] Đã phân biệt **on the way in** với **on the way out**.
- [ ] Các aspect liên quan đã đăng ký thành Spring beans và target là proxy.
- [ ] Order áp dụng cho **các aspects**, không suy từ vị trí method trong source.
- [ ] Không có tie order nếu ứng dụng đòi hỏi một thứ tự cứng.
- [ ] `@Around` gọi `proceed()` theo đúng business contract.
- [ ] Test trace cả `in`, target, `out` trên đường success.
- [ ] Test exception và `finally` trên đường failure.
- [ ] Nếu có transaction/cache/security advisors, đã xét thứ tự cấu hình thực tế.
- [ ] Không nhầm order với bean initialization hay self-invocation.
- [ ] Nội dung advice không làm rò rỉ dữ liệu hoặc vô tình thay exception khi chỉ cần logging.

## 14. Tài liệu chính thức

- [Spring Framework 6.2 — Declaring Advice / Advice Ordering](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): quy tắc precedence, order nhỏ hơn, chiều vào/ra và thứ tự advice cùng aspect.
- [Spring Framework 6.2 — Schema-based AOP / Advice Ordering](https://docs.spring.io/spring-framework/reference/6.2/core/aop/schema.html): ordering với `@Order`/`Ordered`, phạm vi XML và advisors.
- [Spring Framework 6.2 — @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html): Spring AOP sử dụng proxy với `@AspectJ` syntax.
- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): self-invocation, JDK/CGLIB proxy và các giới hạn.
- [Spring `Ordered` API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/Ordered.html): `getOrder()`, `HIGHEST_PRECEDENCE`, `LOWEST_PRECEDENCE`.
- [Spring `@Order` API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/annotation/Order.html): cách đánh dấu precedence bằng annotation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-ordering-004">Chiều đi vào và chiều đi ra của chuỗi interceptor</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP — Chiều đi vào và chiều đi ra của chuỗi interceptor

## 1. Điều cốt lõi: advice tạo thành những lớp bao quanh method đích

Trong **Spring AOP dựa trên proxy**, khi một consumer gọi method trên bean được proxy, Spring kiểm tra những advisor có pointcut phù hợp rồi thực thi chuỗi các interceptor/advice. Những phần xử lý này không phải một danh sách chạy từ đầu tới cuối đúng một lượt. Chúng có thể tạo ra một cấu trúc **lồng nhau**:

- **Chiều đi vào (inbound, before proceeding):** bắt đầu từ interceptor/advice ưu tiên cao hơn ở lớp ngoài, tiến dần tới interceptor lớp trong và cuối cùng là method đích.
- **Chiều đi ra (outbound, after proceeding):** khi target trả về hoặc ném exception, luồng lần lượt đi ngược ra các interceptor lớp trong rồi lớp ngoài. Interceptor đứng ngoài **hoàn tất sau** interceptor đứng trong nếu nó cho luồng đi tiếp một cách bình thường.
- Với `@Around`, hai chiều được nhìn thấy rõ ở các dòng lệnh **trước** và **sau** `ProceedingJoinPoint.proceed()`.
- Với `@Before`, `@AfterReturning`, `@AfterThrowing` và `@After`, Spring áp dụng semantics riêng của từng loại advice trong interceptor chain, không phải đơn thuần chạy tất cả `@Before` rồi đảo ngược một danh sách tùy ý.

Một ví dụ trực quan:

```text
Caller
  |
  v
@Around A  — in
  |
  +--> @Around B  — in
           |
           +--> Target.work()
           |
           +<-- @Around B  — out
  |
  +<-- @Around A  — out
  |
  v
Caller nhận return value
```

**Quy tắc nhớ:** *Aspect ưu tiên cao hơn thường vào trước và ra sau.* Trong Spring, nếu hai aspects được cấu hình bằng `@Order(1)` và `@Order(2)`, aspect có giá trị `1` có precedence cao hơn. Khi cả hai dùng `@Around` và đều gọi `proceed()` đúng một lần, lời gọi chạy theo mô hình:

```text
A before
B before
target
B after
A after
```

Đây là mô hình nền tảng để hiểu Spring AOP advice ordering, logging, metrics, transactions, caching và những hành vi khi có exception.

**Giới hạn phạm vi:** bài này chỉ bàn về **Spring Aspect / Spring AOP proxy**, advisor chain và cách ứng dụng quan sát thứ tự. Không mô tả AspectJ bytecode weaving như một cơ chế thay thế và không trình bày AOP ở mức khái niệm chung.

## 2. Vì sao Spring AOP có interceptor chain?

Spring-managed bean có thể chịu nhiều chính sách cắt ngang. Ví dụ cùng một method service có:

- advice đo thời gian;
- advice logging;
- advice kiểm tra điều kiện trước lời gọi;
- transaction interceptor;
- advice quan sát return value hoặc exception.

Khi consumer gọi qua **Spring AOP proxy**, những advisor phù hợp được sắp thứ tự và tham gia một invocation. Với dạng interceptor bao quanh, phần bên ngoài có thể chạy code, chuyển điều khiển cho phần phía trong, rồi xử lý kết quả hoặc lỗi khi lời gọi trở lại.

```text
Consumer
  |
  v
Spring AOP proxy
  |
  v
Advisor/interceptor số 1  [ngoài nhất]
  |    enter
  v
Advisor/interceptor số 2
  |    enter
  v
Advisor/interceptor số 3  [trong nhất]
  |    enter
  v
Target method execution
  |    return hoặc throw
  ^
Advisor/interceptor số 3
  |    exit / finally / error
  ^
Advisor/interceptor số 2
  |    exit / finally / error
  ^
Advisor/interceptor số 1
  |    exit / finally / error
  ^
Caller
```

Trong Spring AOP, `MethodInvocation.proceed()` chuyển execution tới **phần còn lại của chain**, không nhất thiết nhảy trực tiếp đến target. Tương tự, `ProceedingJoinPoint.proceed()` trong một `@Around` cho các interceptor bên trong và target có cơ hội chạy. Khi invocation bên trong kết thúc, control quay trở lại advice gọi `proceed()`.

### 2.1. Spring AOP proxy chặn những lời gọi nào?

Chuỗi advice không có mặt trong mọi lời gọi Java:

1. Target là Spring-managed bean.
2. Hạ tầng Spring AOP được bật.
3. Aspect/advisor được đăng ký và pointcut khớp method.
4. Consumer sử dụng **proxy do Spring công bố**, thường qua dependency injection.
5. Method có thể bị loại proxy thực tế intercept: JDK proxy dựa trên public interface method; CGLIB dựa trên subclass/override với giới hạn riêng.

Khi trong target có `this.inner()`, lời gọi nội bộ **bypass Spring proxy**. Nó không khởi tạo một interceptor chain mới cho `inner()` mặc dù pointcut có thể match method ấy.

### 2.2. “Chain” không đồng nghĩa với “mọi advisor phải chạy”

Một interceptor bên ngoài có thể **không gọi `proceed()`** hoặc ném exception sớm. Khi đó các interceptor bên trong và target không được thực thi theo đường bình thường. Một outer `@Around` cũng có thể catch exception, trả fallback và làm caller thấy thành công. Vì vậy, sơ đồ đảo chiều đầy đủ chỉ áp dụng khi từng lớp thực sự cho execution đi tiếp.

## 3. `@Around` là cách quan sát hai chiều trực tiếp nhất

Hai advices độc lập:

```java
@Aspect
@Component
@Order(1)
public class OuterAspect {
    @Around("execution(* demo.chain.WorkService.work(..))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("[outer] enter");
        Object result = pjp.proceed();
        System.out.println("[outer] exit");
        return result;
    }
}
```

```java
@Aspect
@Component
@Order(2)
public class InnerAspect {
    @Around("execution(* demo.chain.WorkService.work(..))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("[inner] enter");
        Object result = pjp.proceed();
        System.out.println("[inner] exit");
        return result;
    }
}
```

Đây là **hai fragments**, cần các imports, target, Spring config và các file riêng. Nếu `work()` kết thúc bình thường, trace là:

```text
[outer] enter
[inner] enter
[target] work
[inner] exit
[outer] exit
```

Không phải:

```text
[outer] enter
[inner] enter
[target] work
[outer] exit
[inner] exit
```

Lý do nằm ở call stack: `OuterAspect.trace()` **chưa kết thúc** khi nó gọi `pjp.proceed()`; nó đang đợi tất cả thành phần phía trong, bao gồm `InnerAspect.trace()`, trả điều khiển lại. Chỉ sau khi inner advice return, outer mới in `exit`.

### 3.1. `proceed()` trả về điều gì?

Trong Spring AOP, `ProceedingJoinPoint.proceed()` trả `Object` đại diện cho kết quả của **phần còn lại của chain**, không bắt buộc là kết quả nguyên bản từ target nếu một interceptor bên trong cố ý biến đổi return.

```java
@Around("execution(* demo.chain.WorkService.work(..))")
public Object preserveResult(ProceedingJoinPoint pjp) throws Throwable {
    Object result = pjp.proceed();
    return result;
}
```

Nếu chỉ muốn log/measure, cần chuyển **đúng result** ra ngoài. Nếu method target trả `int`, `Object` có thể chứa `Integer` do boxing; trả `null` có thể phá contract. Nếu method target trả `String`, việc advice đổi `String` thành một type khác có thể gây lỗi runtime.

### 3.2. `try/finally` khác với code chỉ đặt sau `proceed()`

```java
@Around("execution(* demo.chain.WorkService.work(..))")
public Object traceFinally(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("[trace] enter");
    try {
        return pjp.proceed();
    } finally {
        System.out.println("[trace] finally");
    }
}
```

Với success hoặc exception, `finally` **đều** chạy khi stack unwinds, trừ những trường hợp như JVM/process dừng bất thường. Nếu đặt:

```java
Object result = pjp.proceed();
System.out.println("[trace] exit");
return result;
```

dòng `exit` chỉ chạy khi `proceed()` hoàn tất bình thường. Nếu `proceed()` ném exception, Java bỏ qua dòng sau đó trừ khi được catch hoặc có finally.

**Không viết `return` trong `finally`**: nó có thể ghi đè return value và nuốt exception của phần bên trong. Khi cleanup có thể ném lỗi, cũng cần thiết kế để lỗi cleanup không vô tình che exception nghiệp vụ ban đầu.

### 3.3. Khi inner around advice trả fallback

Giả sử `InnerAspect` catch một `IllegalStateException` và trả `"FALLBACK"`, còn `OuterAspect` chỉ đo kết quả sau `proceed()`:

```text
outer enter
inner enter
target throws
inner catches and returns FALLBACK
outer receives FALLBACK
outer exit
caller receives FALLBACK
```

Đối với outer interceptor, inner chain đã kết thúc **bình thường** do inner recover. Không thể nói “target đã throw nên mọi outer @AfterThrowing đều chắc chắn chạy”: còn phụ thuộc **vị trí** observer so với interceptor thực hiện recovery.

## 4. Precedence của các aspects trong Spring

Spring AOP tuân theo quy tắc precedence cho các advices áp dụng cùng một join point:

- Trong **các aspects khác nhau**, nếu không cấu hình precedence, thứ tự không được bảo đảm. Không đoán theo tên class, thứ tự bean scan, file source hoặc tên method.
- Gắn `@Order(n)` trên aspect class hoặc triển khai `org.springframework.core.Ordered` để đặt precedence.
- **Số order nhỏ hơn = precedence cao hơn**.
- Trên đường **đi vào**, aspect ưu tiên cao hơn chạy trước.
- Trên đường **đi ra**, aspect ưu tiên cao hơn chạy sau, vì nó là lớp ngoài.

Ví dụ:

```java
@Aspect
@Component
@Order(10)
public class AuditAspect {
    @Around("execution(* demo.chain.WorkService.work(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("audit-in");
        Object result = pjp.proceed();
        System.out.println("audit-out");
        return result;
    }
}
```

```java
@Aspect
@Component
@Order(20)
public class MetricsAspect {
    @Around("execution(* demo.chain.WorkService.work(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("metrics-in");
        Object result = pjp.proceed();
        System.out.println("metrics-out");
        return result;
    }
}
```

Đây là fragments minh họa. Với invocation thành công:

```text
audit-in
metrics-in
target
metrics-out
audit-out
```

**Thay `@Order(10)` thành `@Order(30)`** có thể đổi aspect trở thành lớp trong, nếu các advisor khác vẫn giữ thứ tự như cũ. Điều này thay đổi điểm quan sát thời gian, return value và exception.

### `@Order` không phải thứ tự khởi tạo bean

`@Order` ở đây là thứ tự ưu tiên trong advisor/aspect ordering, không có nghĩa constructor, `@PostConstruct`, scheduled tasks hoặc HTTP filters luôn chạy theo cùng thứ tự. Thứ tự vòng đời bean và thứ tự invocation của Spring AOP là hai vấn đề khác.

## 5. Các loại advice xuất hiện ở chiều nào?

Không phải loại advice nào cũng có cả “enter” và “exit” trong cùng một method:

| Loại Spring advice | Vị trí quan sát | Điều kiện chạy |
| --- | --- | --- |
| `@Around` | Có phần trước và sau `proceed()` nếu tác giả viết như vậy | Theo pointcut và cách advice điều khiển `proceed()` |
| `@Before` | Trước target method execution | Lời gọi khớp pointcut đi vào nhánh đó của chain |
| `@AfterReturning` | Đường ra **thành công** | Method execution liên quan hoàn tất bình thường và return binding phù hợp |
| `@AfterThrowing` | Đường ra **lỗi** | Execution liên quan kết thúc bất thường với exception type phù hợp |
| `@After` | Khi rời join point theo semantics **finally** | Chạy cả khi success và error trong chain tương ứng |

Như vậy, đường ra **không phải lúc nào cũng chỉ có một `@After`**. Nếu target success, các advice `@AfterReturning` có thể chạy; nếu target throws, các advice `@AfterThrowing` có thể chạy; `@After` áp dụng theo finally semantics.

### 5.1. `@Before` không có quyền thay return

Một `@Before` chạy trên đường vào. Nó có thể quan sát input arguments bằng `JoinPoint` và ném exception khi cần chặn lời gọi. Nhưng không có `ProceedingJoinPoint.proceed()` và không thể return một giá trị thay thế cho target. Nếu `@Before` ném exception, các thành phần bên trong có thể chưa chạy, còn các interceptor bao ngoài có thể bắt lỗi nếu được thiết kế như vậy.

### 5.2. `@AfterReturning` và `@AfterThrowing` loại trừ nhau đối với cùng một kết thúc

Với một execution kết thúc bình thường, `@AfterReturning` liên quan có thể nhận return value. Với một execution ném exception, `@AfterThrowing` liên quan có thể nhận exception. Không xem chúng như hai callback tuần tự luôn chạy cùng nhau cho một execution.

Nếu target tự catch exception và return success, observer ở proxy boundary thấy **normal return**, không phải target-thrown exception. Nếu một inner `@Around` recover trước khi outer advisor quan sát lỗi, outer advisor cũng có thể chỉ thấy normal return. Phân tích phải dựa vào vị trí chain.

### 5.3. `@After` là finally, không phải after-returning

Một `@After` được thiết kế để cleanup logic kiểu finally; nó chạy dù method được advice return hay throw. Không dùng `@After` để giả định target return success. Muốn giá trị trả về, dùng `@AfterReturning(returning="value")` hoặc `@Around`.

### 5.4. Tương tác khi nhiều advice nằm trong **cùng @Aspect class**

Tài liệu Spring Framework mô tả các loại advice của một `@Aspect` có thứ tự precedence riêng khi cùng áp dụng cho một join point: `@Around`, `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`. Tuy nhiên, đây **không phải một danh sách thời điểm chạy tuyến tính**. Kể từ Spring Framework 5.2.7, `@After` tuân theo **finally semantics** và trong cùng aspect thực tế chạy **sau `@AfterReturning` hoặc `@AfterThrowing`**.

Bởi vậy, với một aspect chứa `@Before`, `@AfterReturning`, `@AfterThrowing` và `@After`, có thể quan sát:

```text
Thành công:
before
target returns
afterReturning
after (finally)

Thất bại:
before
target throws
afterThrowing
after (finally)
```

Ví dụ hoàn chỉnh ở mục 8 sử dụng Spring Framework 6.2.x để thể hiện đúng điều này. Với nhiều advice **cùng loại trong cùng aspect**, thứ tự tương đối không được suy từ thứ tự khai báo Java source do reflection không bảo đảm source declaration order. Khi thứ tự là yêu cầu bắt buộc, nên tách sang các aspects có order rõ hoặc hợp nhất logic.

## 6. Đọc thứ tự theo call stack thay vì chỉ theo tên annotation

Một invocation có ba lớp `@Around`:

```text
A.enter
  B.enter
    C.enter
      target()
    C.exit
  B.exit
A.exit
```

**Nếu target return:** đi ra từ C, rồi B, rồi A. Không có gì “chạy ngược” ở mức Java VM; chỉ là các stack frames return theo cơ chế hàm lồng nhau.

**Nếu target throw:**

```text
A.enter
  B.enter
    C.enter
      target throws
    C.catch/finally
  B.catch/finally
A.catch/finally
caller catches
```

Nếu các advice chỉ có lệnh `println("exit")` đặt **sau** `proceed()` mà không có catch/finally, các dòng exit đó **không xuất hiện** trên đường exception.

**Nếu B không gọi `proceed()`:**

```text
A.enter
  B.enter
  B.return cached-value
A.exit with cached-value
caller sees cached-value
```

C và target không chạy. Các `@Before` / `@After` bên trong B cũng không chạy vì execution không đi vào phạm vi của chúng.

**Nếu B gọi `proceed()` hai lần:** C và target có thể chạy hai lần, với side effects nhân đôi. Retry chỉ nên được thiết kế có chủ đích, kiểm soát idempotency, backoff và transaction scope.

### Phân biệt return của method với kết quả cuối caller nhìn thấy

Outer advice nhận kết quả của **chain bên trong**, có thể là:

- giá trị trả trực tiếp từ target;
- kết quả đã được một inner advice transform;
- cached value khi inner advice bỏ qua target;
- fallback khi inner advice catch exception.

Vì vậy muốn đo/log “target execution result nguyên thủy” và muốn đo/log “kết quả cuối của request qua AOP proxy” có thể là **hai vị trí đặt aspect khác nhau**.

## 7. Thứ tự tác động tới Spring transaction, cache và monitoring

### 7.1. Logging so với transaction boundary

Spring `@Transactional` thường được triển khai qua transaction advisor/interceptor, cũng tham gia vào pipeline method invocation. Một logging `@Around` bên **ngoài** transaction interceptor:

```text
logging enter
  transaction begin
    target method
  transaction commit / rollback
logging exit / catch
```

Nhưng nếu logging interceptor nằm **bên trong** transaction:

```text
transaction begin
  logging enter
    target method
  logging exit / catch
transaction commit / rollback
```

Hai cách đặt cho số liệu duration và khả năng thấy trạng thái transaction khác nhau. Trong thực tế, còn phụ thuộc transaction manager, advisor ordering, AOP config và các interceptor khác. **Không** suy đoán transaction đã mở hay chưa chỉ vì cùng tồn tại `@Around` và `@Transactional`.

### 7.2. Retry và transaction

Nếu retry advice đứng **ngoài** transaction advice, mỗi lần `proceed()` có thể tạo transaction mới (tùy propagation settings). Nếu transaction bao **ngoài** retry, nhiều attempts có thể cùng chịu một transaction boundary, với hậu quả khác về rollback-only và lock lifetime. Đây là một nguyên nhân cần kiểm soát order rõ ràng; không tùy tiện chỉnh `@Order` để “sửa” log order rồi vô tình đổi giao dịch.

### 7.3. Cache và timing

Một caching interceptor đứng ngoài metrics advice có thể trả cache hit **trước khi đi vào** phần metrics/target. Metrics bên trong khi đó không quan sát cache hit. Nếu metrics đứng ngoài cache thì quan sát cả cache hits, nhưng duration đo bao gồm việc kiểm tra cache. Chọn order theo mục tiêu quan sát.

### 7.4. Security và authorization

Nếu một kiểm tra quyền truy cập nằm trong một advice bên trong, một interceptor bên ngoài có thể return sớm mà không gọi `proceed()` khiến kiểm tra bên trong không chạy. Không thiết kế bảo mật ứng dụng chỉ dựa vào giả định “aspect chắc chắn chạy” nếu có thể bypass proxy hoặc outer advice short-circuit. Dùng các Spring Security boundaries thích hợp và kiểm chứng chain thực tế.

## 8. Ví dụ Maven Java 17 Spring Framework 6.2 — nhìn rõ cả hai chiều

Ví dụ đầy đủ, chạy trên **Spring Framework thuần** không cần Boot. Ta có:

- `OuterAspect`: `@Around`, `@Order(1)`, ngoài cùng.
- `InnerAspect`: `@Around`, `@Order(2)`, phía trong Outer.
- `EventAspect`: `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After`, `@Order(3)`. Bốn advice **khác loại** trong cùng một aspect để minh họa Spring 6.2 semantics.
- `WorkService`: `work("OK")` trả thành công, `work("FAIL")` ném `IllegalStateException`. `batch()` tự gọi `this.work("INNER")`, nên lời gọi nội bộ không tạo interceptor chain mới.

Pointcut của ba aspects **chỉ** match `WorkService.work(..)`, không match `batch()`. Tất cả print logs đều xác định được theo thứ tự và không phụ thuộc thứ tự giữa nhiều advice cùng loại.

### 8.1. Cấu trúc file

```text
spring-aop-chain-order/
├── pom.xml
└── src/main/java/demo/chain/
    ├── AppConfig.java
    ├── WorkPort.java
    ├── WorkService.java
    ├── OuterAspect.java
    ├── InnerAspect.java
    ├── EventAspect.java
    └── Main.java
```

### 8.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-chain-order</artifactId>
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

Các dependency trên dùng cho Spring IoC, AOP proxy và parsing của `@AspectJ` pointcuts. Không cần bật AspectJ compile-time weaving hoặc load-time weaving để thực hiện ví dụ.

### 8.3. `AppConfig.java`

```java
package demo.chain;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {
}
```

`@ComponentScan` đăng ký service và các aspects có `@Component`. `@EnableAspectJAutoProxy` bật Spring AOP auto-proxy. Với target triển khai interface và `proxyTargetClass=false`, bean được Spring công bố trong ví dụ sẽ là **JDK dynamic proxy**.

### 8.4. `WorkPort.java`

```java
package demo.chain;

public interface WorkPort {
    String work(String input);
    void batch();
}
```

Consumer cần inject/tra cứu `WorkPort`, không ép JDK proxy thành concrete `WorkService`.

### 8.5. `WorkService.java`

```java
package demo.chain;

import org.springframework.stereotype.Service;

@Service
public class WorkService implements WorkPort {
    @Override
    public String work(String input) {
        System.out.println("[target] work " + input);
        if ("FAIL".equals(input)) {
            throw new IllegalStateException("boom");
        }
        return "DONE-" + input;
    }

    @Override
    public void batch() {
        System.out.println("[target] batch");
        this.work("INNER"); // Không qua Spring AOP proxy.
    }
}
```

`work("OK")` in một dòng và trả `"DONE-OK"`. `work("FAIL")` in dòng target rồi ném exception có message `"boom"`. `batch()` tự gọi `work("INNER")` từ target nên lời gọi này bypass advice.

### 8.6. `OuterAspect.java` — lớp ngoài `@Order(1)`

```java
package demo.chain;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(1)
public class OuterAspect {
    @Around("execution(* demo.chain.WorkService.work(..))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("[outer] in");
        try {
            Object result = pjp.proceed();
            System.out.println("[outer] out " + result);
            return result;
        } catch (Throwable ex) {
            System.out.println("[outer] caught " + ex.getMessage());
            throw ex;
        }
    }
}
```

Ở đường success, outer advice in `out` sau khi **toàn bộ** inner chain hoàn tất. Ở đường error, outer bắt exception do phần bên trong ném ra, in `caught` rồi **rethrow** để caller vẫn nhận lỗi. Nó không tự phục hồi.

### 8.7. `InnerAspect.java` — lớp trong `@Order(2)`

```java
package demo.chain;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(2)
public class InnerAspect {
    @Around("execution(* demo.chain.WorkService.work(..))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("[inner] in");
        try {
            Object result = pjp.proceed();
            System.out.println("[inner] out " + result);
            return result;
        } catch (Throwable ex) {
            System.out.println("[inner] caught " + ex.getMessage());
            throw ex;
        }
    }
}
```

Cả hai aspects gọi `proceed()` **chính xác một lần** trên mỗi invocation và truyền return/exception ra ngoài. Do `@Order(2)` thấp precedence hơn `@Order(1)`, `InnerAspect` đi vào sau nhưng đi ra trước Outer.

### 8.8. `EventAspect.java` — nhóm advice theo outcome

```java
package demo.chain;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(3)
public class EventAspect {
    @Before("execution(* demo.chain.WorkService.work(..))")
    public void beforeWork() {
        System.out.println("[event] before");
    }

    @AfterReturning(
        pointcut = "execution(* demo.chain.WorkService.work(..))",
        returning = "result"
    )
    public void returned(String result) {
        System.out.println("[event] afterReturning " + result);
    }

    @AfterThrowing(
        pointcut = "execution(* demo.chain.WorkService.work(..))",
        throwing = "ex"
    )
    public void failed(IllegalStateException ex) {
        System.out.println("[event] afterThrowing " + ex.getMessage());
    }

    @After("execution(* demo.chain.WorkService.work(..))")
    public void afterWork() {
        System.out.println("[event] after finally");
    }
}
```

Các advice type và mục đích:

- `@Before` in ở chiều vào, ngay trước phần execution tiếp theo.
- `@AfterReturning` nhận `String result` khi `work(..)` return bình thường.
- `@AfterThrowing` nhận `IllegalStateException` khi `work(..)` ném exception phù hợp.
- `@After` chạy theo finally semantics cho cả hai luồng, và với Spring 6.2 ở **cùng aspect này** thực tế được gọi **sau afterReturning/afterThrowing** tương ứng.

Lưu ý `@Order(3)` điều khiển precedence **so với các aspects khác**. Các lời gọi advice bên trong một class `@Aspect` còn theo quy tắc cho advice types của Spring, không phải thứ tự code trong file.

### 8.9. `Main.java`

```java
package demo.chain;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            WorkPort service = context.getBean(WorkPort.class);
            System.out.println("jdkProxy=" + AopUtils.isJdkDynamicProxy(service));

            System.out.println("[caller] begin OK");
            String result = service.work("OK");
            System.out.println("[caller] result " + result);

            System.out.println("[caller] begin FAIL");
            try {
                service.work("FAIL");
            } catch (IllegalStateException ex) {
                System.out.println("[caller] caught " + ex.getMessage());
            }

            System.out.println("[caller] begin batch");
            service.batch();
        }
    }
}
```

Biên dịch và chạy tại thư mục có `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.chain.Main
```

**Stdout dự kiến từ `System.out` trong chương trình**, không bao gồm log build/framework nếu môi trường có:

```text
jdkProxy=true
[caller] begin OK
[outer] in
[inner] in
[event] before
[target] work OK
[event] afterReturning DONE-OK
[event] after finally
[inner] out DONE-OK
[outer] out DONE-OK
[caller] result DONE-OK
[caller] begin FAIL
[outer] in
[inner] in
[event] before
[target] work FAIL
[event] afterThrowing boom
[event] after finally
[inner] caught boom
[outer] caught boom
[caller] caught boom
[caller] begin batch
[target] batch
[target] work INNER
```

### 8.10. Giải thích thứ tự của nhánh OK

1. Main gọi `service.work("OK")` trên Spring-managed JDK proxy.
2. Aspect ưu tiên cao nhất `@Order(1)` bắt đầu: `[outer] in`.
3. Outer gọi `proceed()` nên đi vào `@Order(2)`: `[inner] in`.
4. Inner gọi `proceed()`, đi vào các advice thuộc `EventAspect @Order(3)`: `[event] before`.
5. Target `WorkService.work("OK")` in `[target] work OK` rồi return `"DONE-OK"`.
6. Advice `@AfterReturning` thấy normal completion: `afterReturning DONE-OK`.
7. Advice `@After` của cùng aspect chạy theo finally semantics sau đó: `after finally`.
8. Điều khiển quay về Inner, in `[inner] out DONE-OK` và return result.
9. Điều khiển quay về Outer, in `[outer] out DONE-OK` và return result.
10. Caller nhận `"DONE-OK"` và in `[caller] result DONE-OK`.

**Bài học:** chiều vào đi Outer → Inner → Event → target; chiều ra từ target → Event outcome/finally → Inner → Outer.

### 8.11. Giải thích thứ tự của nhánh FAIL

1. Outer in → Inner in → Event before → target như nhánh OK.
2. Target in `[target] work FAIL` rồi ném `IllegalStateException("boom")`.
3. `@AfterReturning` **không chạy**, vì method không return bình thường.
4. `@AfterThrowing` match `IllegalStateException` và in `afterThrowing boom`.
5. `@After` trong cùng aspect chạy finally và in `after finally`.
6. Exception tiếp tục đi ra Inner, khối catch của Inner in `caught boom` rồi rethrow.
7. Exception tiếp tục đi ra Outer, catch của Outer in `caught boom` rồi rethrow.
8. Caller catch và in `[caller] caught boom`.

Nếu Inner hoặc Outer **không catch** mà chỉ đặt code log sau `proceed()`, các dòng `out` tương ứng không xuất hiện. Nếu một advice ném exception mới, outer có thể nhìn thấy loại lỗi khác; nếu một advice catch rồi trả fallback, outer có thể thấy normal return.

### 8.12. Giải thích self-invocation trong batch

`batch()` không match pointcut của các aspects và được gọi qua proxy, nên Main in `[caller] begin batch` rồi target in `[target] batch`. Bên trong target, `this.work("INNER")` không đi lại qua proxy; chỉ in `[target] work INNER`.

Vì vậy không có dòng `[outer] in`, `[inner] in` hay `[event] before` cho INNER. **Đổi JDK proxy thành CGLIB không tự sửa self-invocation**.

## 9. Thử nghiệm thay đổi thứ tự và kết quả

### 9.1. Đổi order Outer và Inner

Nếu hoán đổi thành `OuterAspect @Order(2)` và `InnerAspect @Order(1)`, với hai `@Around` vẫn được đăng ký như ví dụ, thứ tự khi success sẽ có dạng:

```text
[inner] in
[outer] in
[event] before
[target] work OK
[event] afterReturning DONE-OK
[event] after finally
[outer] out DONE-OK
[inner] out DONE-OK
```

Không thay đổi logic target hoặc pointcut, nhưng thứ tự logging **ở cả hai chiều** được đảo tương ứng.

### 9.2. Bỏ `proceed()` của Inner

Giả sử Inner thay thành:

```java
@Around("execution(* demo.chain.WorkService.work(..))")
public Object cached(ProceedingJoinPoint pjp) {
    System.out.println("[inner] cached");
    return "CACHED";
}
```

Đây là **fragment thay thế**, không phải code để chạy cùng body Inner cũ. Outer gọi `proceed()` và đi vào Inner; Inner trả ngay `"CACHED"`, vì vậy Event/target không chạy:

```text
[outer] in
[inner] cached
[outer] out CACHED
```

Điều này hợp lệ về return type vì `work()` trả `String`, nhưng có thể sai nghiệp vụ nếu method thật cần side effects. Nếu áp dụng lên method trả `int`, return sai kiểu có thể gây lỗi runtime. Không dùng short circuit tùy tiện.

### 9.3. Inner bắt lỗi và phục hồi

Nếu Inner dùng:

```java
try {
    return pjp.proceed();
} catch (IllegalStateException ex) {
    System.out.println("[inner] recovered");
    return "FALLBACK";
}
```

đây là **fragment thay thế phần try/catch trong Inner**. Khi target ném lỗi, `EventAspect @AfterThrowing` (nằm phía trong Inner) có thể log exceptional exit, sau đó Inner catch và trả thành công. Outer lúc này **nhìn thấy success** và in dòng `out FALLBACK`; caller nhận `"FALLBACK"`. Không phải outer advisor nào cũng nhìn thấy exception khi inner đã xử lý nó.

### 9.4. `@After` cleanup thất bại

Nếu `@After` ném exception của riêng nó, exception ấy có thể thay đổi kết quả mà caller nhìn thấy và che lỗi trước đó. Đừng để logging/cleanup trong finally advice tự ý throw, trừ khi contract yêu cầu và có thiết kế exception rõ ràng. Một `@AfterThrowing` khác trong cùng aspect không phải “global catch” đáng tin cho exception do `@After` sinh ra.

### 9.5. Thêm hai `@Before` trong cùng aspect

Nếu `EventAspect` có hai method `@Before` khác nhau cùng match `work()`, không nên dựa vào thứ tự khai báo của hai method trong class Java để dự đoán dòng log. Spring không đảm bảo source declaration ordering qua reflection. Hãy gộp vào một advice theo trình tự mong muốn hoặc tách chúng thành hai aspects với `@Order` khác nhau.

## 10. Giới hạn khi suy luận order từ logs

### 10.1. Multiple proxy layers

Một Spring bean có thể tham gia nhiều cơ chế proxy/advisor. Có thể có nested proxies trong ứng dụng cấu hình đặc thù, bean scoped proxies, hoặc interceptor từ những module khác. Khi thấy log interleave không giống ví dụ, xác minh **đúng proxy reference**, advisor chain, scope và loại proxy. Không suy luận “@Order bị Spring bỏ qua” chỉ vì có interceptor khác đứng ngoài.

### 10.2. Thực thi bất đồng bộ và thread đổi

Nếu method trả một `CompletableFuture<T>`, reactive publisher hoặc được xử lý qua `@Async`, phần Spring AOP proxy có thể return một wrapper trước khi công việc async kết thúc. Một `@AfterReturning` tại method-execution boundary không tự đồng nghĩa “business đã hoàn thành”, và thứ tự logs across threads không phản ánh một single call stack giống ví dụ đồng bộ.

Nếu quan tâm hoàn thành future hoặc publisher, dùng completion/error operators và context propagation thích hợp. Không dùng mô hình linear synchronous chain để kết luận timeline của mọi async operation.

### 10.3. Transaction rollback dù outer advice trả fallback

Nếu transaction interceptor nằm phía trong Outer, method ném exception khiến nó đánh dấu rollback trước khi Outer catch và return `"FALLBACK"`, việc Outer trả success **không tự hoàn tác rollback**. Việc transaction có commit hay không phụ thuộc boundary, propagation, rules và status rollback-only.

### 10.4. Same-class advice precedence khác inter-aspect order

Trong một class `@Aspect`, có logic precedence theo advice types và đặc biệt `@After` theo finally semantics. Trong **các aspect classes khác nhau**, `@Order` định thứ tự giữa aspects. Đừng chuyển thẳng câu “@Before chạy trước @After” thành cách định thứ tự giữa hai aspects khi mỗi aspect có những loại advice khác nhau — phải xét **thứ tự lớp bao ngoài/lớp trong** trước.

### 10.5. Bean initialization không phải intercepted business invocation

Constructor, `@PostConstruct` và lifecycle callbacks thông thường diễn ra trong quá trình khởi tạo target trước khi ứng dụng dùng proxy hoàn chỉnh. Không đặt kỳ vọng `@Order` của aspects quy định thứ tự constructor hay advice chạy cho mọi callback startup.

## 11. Checklist chẩn đoán thứ tự Spring Aspect

| Hiện tượng | Nguyên nhân có thể | Cách kiểm tra / sửa |
| --- | --- | --- |
| Outer in trước Inner nhưng out sau Inner | Đúng nested interceptor semantics | Đọc `proceed()` như một nested call |
| Log chỉ có `in` khi target ném lỗi | Code `out` đặt sau `proceed()`, thiếu catch/finally | Dùng `try/finally` cho cleanup/logging cần chạy cả khi lỗi |
| `@AfterReturning` không chạy khi lỗi | Target/interceptor trong kết thúc bằng exception | Dùng `@AfterThrowing` cho lỗi hoặc `@After` cho finally |
| `@AfterThrowing` không chạy khi có fallback | Exception đã được inner advice xử lý trước khi observer nhìn thấy | Xem advisor order và actual outcome tại observer |
| `@After` chạy sau `@AfterReturning` trong cùng aspect | Spring 5.2.7+ finally semantics | Đây là hành vi đúng, không phải ngẫu nhiên |
| `@Order(1)` thấy duration dài hơn `@Order(2)` | Aspect outer đo cả inner chain | Chọn lớp metrics theo mục tiêu |
| Đổi `@Order` nhưng target logs không đổi | Target logic độc lập; thay đổi thứ tự các aspects | So sánh enter/exit/finally logs |
| Các aspects không có `@Order` chạy bất định | Spring không bảo đảm inter-aspect ordering | Đặt `@Order` nếu thứ tự là requirement |
| Hai `@Before` cùng aspect không có thứ tự mong muốn | Java reflection không bảo đảm thứ tự khai báo | Gộp logic hoặc tách aspect/classes |
| Target bị gọi hai lần | Một around advice gọi `proceed()` nhiều lần | Rà soát retry policy/idempotency |
| Target không được gọi | Outer advice short-circuit hoặc ném trước `proceed()` | Xem cache/security/validation chain |
| `this.inner()` không tạo logs advice | Self-invocation bypass proxy | Tách Spring bean nếu cần policy riêng |
| Order thay đổi khi có transactions | Có transaction/cache/security advisors khác | Kiểm tra actual advisor chain và precedence |
| Code outside Spring không có log aspect | Object không là Spring-managed proxy | Inject bean qua context |
| JDK proxy không advice một method | Method không được interface công bố | Dùng interface API phù hợp hoặc cấu hình proxy có cân nhắc |
| CGLIB không advice `final` method | Không override được | Đổi ranh giới method/bean hoặc dùng JDK interface proxy phù hợp |

**Quy trình xác minh:**

1. Xác nhận target và aspect là Spring beans; AOP hạ tầng đã bật.
2. Kiểm tra `AopUtils.isAopProxy(bean)`, `isJdkDynamicProxy(...)` hoặc `isCglibProxy(...)` trên reference thực tế.
3. Xác minh pointcut có match method và caller có đi qua proxy không.
4. Liệt kê aspects nào cùng áp dụng, có `@Order` hay `Ordered`, có advice cùng loại trong một aspect hay không.
5. Theo `proceed()` của từng around advice: lớp nào in `enter`, lớp nào catch/finally và return kết quả gì.
6. Thử cả case success và case exception; không chỉ nhìn một chiều của log.
7. Kiểm tra các interceptors hạ tầng như transaction, cache, security, async có làm thứ tự thay đổi không.
8. Dùng integration tests trên Spring context thật khi order ảnh hưởng nghiệp vụ hoặc transaction semantics.

## 12. Câu hỏi tự kiểm tra

**1. `@Order(1)` và `@Order(2)` cùng là `@Around` thì ai đi vào trước?** Aspect có số 1, vì precedence cao hơn. Nếu cả hai proceed và return bình thường, aspect số 1 đi ra sau aspect số 2.

**2. Lời gọi `pjp.proceed()` nghĩa là nhảy ngay vào target?** Không nhất thiết. Nó gọi phần còn lại của Spring AOP interceptor chain, có thể chứa các advices khác rồi mới đến target.

**3. Tại sao đặt `println("after")` sau `proceed()` lại không in khi target throws?** Vì exception bỏ qua các dòng sau `proceed()`, trừ khi được catch/finally.

**4. `@After` có tương đương `@AfterReturning` không?** Không. `@After` theo finally semantics, áp dụng cho cả normal và exceptional exit; `@AfterReturning` chỉ dành cho completion bình thường.

**5. Trong cùng Spring `@Aspect`, `@After` có thể chạy sau `@AfterThrowing` không?** Có. Spring Framework 5.2.7+ áp dụng finally semantics, và đây là behavior được tài liệu chính thức xác nhận.

**6. Nếu InnerAspect catch exception rồi return fallback, OuterAspect có bắt được original exception không?** Thông thường không, nếu fallback return bình thường trước khi đến Outer; Outer thấy kết quả của inner chain.

**7. Nếu around advice không gọi `proceed()`, các advices phía trong có chạy không?** Không theo đường invocation đó; target cũng có thể bị bỏ qua.

**8. `@Order` có kiểm soát Java constructor order không?** Không. Nó đặt precedence cho cơ chế ordering áp dụng, không là lịch khởi tạo toàn ứng dụng.

**9. Vì sao `this.work()` không chạy interceptor chain dù có pointcut?** Vì self-invocation gọi trực tiếp target, bỏ qua Spring AOP proxy.

**10. Muốn đo thời gian bao gồm transaction commit/rollback thì đặt metrics aspect ở đâu?** Thường cần metrics bên ngoài transaction interceptor, nhưng phải kiểm tra advisor ordering và transaction configuration thực tế.

## Tham khảo Spring Framework chính thức

- [Spring Framework — Declaring Advice: Advice Ordering](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html)
- [Spring Framework 6.2 — Advice Ordering, @After finally semantics](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework — AOP Advice API / MethodInterceptor.proceed()](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html)
- [Spring Framework — Proxying Mechanisms, JDK/CGLIB/self-invocation](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — @AspectJ support, enabling Spring AOP](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html)
- [Spring Framework — Declarative transaction rollback](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-ordering-005">Thứ tự giữa các loại advice trong cùng một @Aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự giữa các loại advice trong cùng một @Aspect của Spring AOP

## 1. Câu hỏi cần giải quyết

Một Spring `@Aspect` có thể chứa cùng lúc nhiều method mang `@Around`, `@Before`, `@After`, `@AfterReturning` và `@AfterThrowing`. Khi tất cả các advice đều có pointcut khớp một lời gọi service, **advice nào chạy trước, advice nào chạy sau, và vì sao `@After` lại xuất hiện cuối cùng dù được xếp ở mức precedence cao hơn `@AfterReturning`?**

Đây là câu hỏi về **thứ tự trong interceptor chain của Spring AOP**, không đơn thuần là thứ tự những method được viết trong file Java.

Theo tài liệu Spring Framework hiện đại, khi nhiều **loại advice khác nhau trong cùng một lớp `@Aspect`** đều khớp một method-execution join point, Spring gán **precedence từ cao xuống thấp**:

1. `@Around`
2. `@Before`
3. `@After`
4. `@AfterReturning`
5. `@AfterThrowing`

**Ngoại lệ dễ nhầm:** `@After` có ngữ nghĩa *after finally*, nên khi method kết thúc, **`@After` thực sự được gọi sau `@AfterReturning` hoặc `@AfterThrowing` trong cùng aspect**. Precedence quy định **vị trí advice trong interceptor chain**, chứ không phải danh sách thứ tự những dòng log được in theo chiều thời gian.

Trong một method invocation chạy bình thường, bạn thường quan sát:

```text
@Around (trước proceed)
    @Before
        target method
    @AfterReturning
    @After
@Around (sau proceed / finally)
```

Nếu target method ném exception:

```text
@Around (trước proceed)
    @Before
        target method -> throw
    @AfterThrowing
    @After
@Around (catch/finally)
```

Hai flow trên dành cho **một lớp `@Aspect` duy nhất có các loại advice tương ứng**, tất cả cùng nhắm vào một Spring bean method, với `@Around` gọi `proceed()` một lần và không cố ý biến đổi kết quả/exception.

**Phạm vi:** Spring Framework `@AspectJ`-style **proxy-based AOP**. Bài chỉ nói về cách Spring tạo và chạy advisor/interceptor từ một `@Aspect`; không mở rộng sang thứ tự bytecode weaving/constructor/field join points của AspectJ compiler độc lập.

### Mục tiêu sau bài

- Thuộc precedence giữa **các loại advice** trong **cùng một `@Aspect`**.
- Phân biệt precedence **trên đường vào** và thứ tự callback **trên đường ra**.
- Giải thích tại sao `@After` chạy sau `@AfterReturning` hoặc `@AfterThrowing`.
- Biết thứ tự **hai advice cùng loại trong cùng một `@Aspect` là không xác định**.
- Phân biệt order trong **cùng aspect** với `@Order`/`Ordered` dùng cho **nhiều aspects**.
- Chạy thử Java 17/Spring Framework 6.1 với cả normal return, `null`, thrown exception và exception được bắt trong target.
- Hiểu giới hạn: self-invocation, `@Around` bỏ qua `proceed()`, advisor khác chặn đường gọi và exception phát ra từ advice khác.

## 2. Precedence không phải thứ tự dòng code

### 2.1. “On the way in” và “on the way out”

Spring AOP dựng một **chuỗi interceptor** ở phía sau Spring-managed proxy. Khi lời gọi đi từ client vào target:

- Advice có **precedence cao hơn** nằm ở phía **ngoài** của chuỗi.
- Advice phía ngoài có thể quan sát lời gọi **trước** những advice phía trong.
- Khi phần chuỗi phía trong hoàn thành và call stack unwind, advice phía ngoài thường được quay lại **sau cùng**.

Ví dụ hai around interceptors giả định có precedence A cao hơn B:

```text
A:before
    B:before
        target
    B:after
A:after
```

A được gọi trước “on the way in” và sau “on the way out”. Đây là quy tắc chung của nested interceptors.

Tuy nhiên, từng advice type có semantics riêng. Một `@Before` chạy ở phần trước invocation, còn `@AfterReturning` chỉ chạy khi phần join point phù hợp **return bình thường**. Không thể suy ra thứ tự log chỉ bằng cách đọc danh sách precedence như một hàng đợi FIFO.

### 2.2. Thứ tự chuẩn trong một @Aspect (Spring Framework 6.1)

| Precedence | Advice type | Thời điểm thực thi |
| ---: | --- | --- |
| 1 — cao nhất | `@Around` | Bắt đầu trước phần còn lại của chain; gọi `proceed()` để tiếp tục; có thể quan sát kết quả/exception sau đó |
| 2 | `@Before` | Chạy trước target method execution khi interceptor chain tới nó |
| 3 | `@After` | Có vị trí precedence cao hơn các after advice chuyên biệt nhưng callback chạy theo `finally`, **sau** `@AfterReturning`/`@AfterThrowing` của cùng aspect |
| 4 | `@AfterReturning` | Chỉ chạy nếu execution kết thúc **bình thường**, kể cả trả `null` hoặc `void` |
| 5 — thấp nhất | `@AfterThrowing` | Chỉ chạy nếu execution kết thúc bằng **exception thoát ra** |

**Lưu ý phiên bản:** Quy tắc phân loại precedence giữa các advice type trong cùng một `@Aspect` đã được Spring Framework làm rõ từ **5.2.7** và vẫn được tài liệu 5.3/6.x mô tả. Đừng áp câu “thứ tự mọi advice trong cùng aspect là bất định” từ những bản tài liệu Spring cũ cho mọi phiên bản hiện đại. Phần **hai advice cùng loại** vẫn bất định, như mục 7.

### 2.3. Vì sao `@After` có precedence cao hơn nhưng chạy muộn?

Spring xử lý `@After` theo semantics tương đương:

```java
try {
    // Chuyển lời gọi xuống những interceptor phía trong.
} finally {
    // Thực hiện @After.
}
```

Các `@AfterReturning`/`@AfterThrowing` ở phía trong chỉ hoàn thành khi chain bên trong unwind, sau đó `finally` của `@After` mới được chạy.

Do đó **xếp `@After` thứ ba trong danh sách precedence nhưng đặt log của `@After` sau các advice chuyên biệt là hoàn toàn nhất quán**. Đó chính là khác biệt giữa:
- **Precedence:** thứ tự xếp **interceptor chain**;
- **Event timing:** thứ tự thực tế của các callback khi call stack chạy và unwind.

Không đổi `@Order` chỉ để ép `@After` in trước `@AfterReturning` trong cùng aspect. `@Order` chủ yếu dùng để xác định precedence **giữa các aspect khác nhau**, không thay semantics after-finally.

## 3. Hai đường hoàn tất chính: normal và throwing

### 3.1. Method trả bình thường

Một method có thể trả:
- Một `String` hoặc object có giá trị.
- `null`.
- Không có giá trị nếu return type `void`.
- Một status thất bại về nghiệp vụ như `"REJECTED"` nhưng không ném exception.

Đây đều là **normal completion** đối với Spring AOP nếu exception không thoát ra khỏi method execution. Khi tất cả các advice của cùng aspect match và `@Around` gọi `proceed()`, đường bình thường đi qua:

```text
around:enter
before
target returns value
afterReturning(value)
after
around:return(value)
around:finally
caller sees value
```

`@AfterThrowing` không chạy ở đường này.

**Không đồng nhất normal return với business success.** `return null` hoặc `return "DECLINED"` vẫn là một lần hoàn tất bình thường về mặt Java, nhưng có thể là thất bại nghiệp vụ. Dùng metric names phản ánh đúng semantics, không tự gọi mọi lần `@AfterReturning` là “success business”.

### 3.2. Method ném exception ra ngoài

Nếu target ném `IllegalStateException` và exception thoát ra theo interceptor chain:

```text
around:enter
before
target throws
afterThrowing(exception)
after
around:catch(exception)
around:finally
caller sees exception
```

`@AfterReturning` không chạy ở đường này. `@AfterThrowing` có thể bind exception bằng `throwing` parameter, còn `@After` vẫn được chạy sau đó theo after-finally semantics.

`@Around` ở bên ngoài có thể catch/rethrow exception; thứ tự log `around:catch` xuất hiện sau `after` trong ví dụ này vì `proceed()` đã unwind xong interceptor phía trong.

### 3.3. Target tự catch exception rồi return

Ví dụ:

```java
public String safeWork() {
    try {
        return remoteOperation();
    } catch (IOException ex) {
        return "FALLBACK";
    }
}
```

Đây là snippet minh họa, chưa định nghĩa `remoteOperation`. Nếu exception được target bắt nội bộ và method **return bình thường**, Spring AOP chỉ quan sát normal completion của join point:
- `@AfterReturning` chạy;
- `@AfterThrowing` không chạy cho exception đã bị bắt;
- `@After` chạy.

Spring AOP không intercept từng câu lệnh `throw`/`catch` *bên trong* method. Đó không phải lỗi về thứ tự advice.

### 3.4. Throw từ advice khác không giống throw từ target

Tài liệu Spring cảnh báo: các loại advice trong cùng một aspect được thiết kế **để áp dụng vào join point**, vì vậy `@AfterThrowing` không được hiểu là “bộ bắt mọi exception từ `@After` hoặc `@AfterReturning` của chính aspect đó”.

Ví dụ `@AfterReturning` tự ném một exception khi xử lý kết quả. Không nên thiết kế `@AfterThrowing` trong cùng aspect với kỳ vọng bắt được exception ấy như một error handler chung. Hãy quản lý lỗi instrumentation trong chính advice, bảo toàn original business outcome khi cần, hoặc tạo policy exception rõ ràng bằng `@Around`. Tương tác với những advisor **khác aspect** phụ thuộc vị trí trong chain.

## 4. @Around giữ vai trò bao ngoài nhưng có thể đổi flow

`@Around` là advice có precedence cao nhất trong cùng aspect, nhưng có khả năng **điều khiển** có chạy target hay không.

### 4.1. Dạng bảo toàn outcome

```java
@Around("execution(* com.example.app.service..*.*(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("around:enter");
    try {
        return pjp.proceed();
    } finally {
        System.out.println("around:finally");
    }
}
```

Đây là snippet trong một Spring `@Aspect`, cần import và Spring AOP configuration. `proceed()` tiếp tục phần chain phía trong đúng một lần. `finally` chạy khi `proceed()` return hoặc throw theo Java semantics thông thường.

### 4.2. @Around không gọi proceed

```java
@Around("execution(public String com.example.app.service.LookupService.lookup(..))")
public Object shortCircuit(ProceedingJoinPoint pjp) {
    return "CACHED";
}
```

Nếu code trả kết quả mà không gọi `proceed()`, những advice nằm **phía trong** cũng không được chạy, và method target không được thực thi. Kết quả đó không phải “Spring bỏ qua @Before một cách ngẫu nhiên”; around advice đã chủ động dừng chain.

Nếu code logging của bạn muốn `@Before`, `@AfterReturning`, `@After` vẫn chạy, **đừng short-circuit ở around ngoài chúng**.

### 4.3. @Around catch rồi trả fallback

```java
@Around("execution(public String com.example.app.service.LookupService.lookup(..))")
public Object recover(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (IllegalStateException ex) {
        return "FALLBACK";
    }
}
```

Đây là snippet minh họa. Nếu target throw `IllegalStateException`, phần advice phía trong của **cùng aspect** có thể đã ghi nhận `@AfterThrowing` trước khi around advice ở phía ngoài catch rồi return `"FALLBACK"`. Lời gọi nhìn từ bên ngoài around lại kết thúc bình thường. Vì vậy:
- **Target/inner chain outcome:** exception.
- **Caller-visible proxy outcome:** normal return `"FALLBACK"`.

Không diễn giải mọi dòng log `@AfterThrowing` là một exception chắc chắn đã tới caller. Khi có fallback, cache, transaction hoặc retry, cần định nghĩa **vị trí đo lường** của từng metric.

### 4.4. @Around throw trước proceed

Nếu around advice ném exception **trước** khi gọi `proceed()`, những advice bên trong chưa được đi tới. Vì vậy `@AfterThrowing` cùng aspect có thể không chạy cho exception được tạo từ chính around advice ngoài. Không cố dùng một advice trong cùng aspect như bộ xử lý lỗi toàn cục của mọi advice khác.

## 5. Ví dụ chạy được: xem đúng thứ tự bằng stdout

Ví dụ sau dùng:
- **Java 17+**;
- **Spring Framework 6.1.14**;
- **AspectJ Weaver 1.9.22.1**;
- Gradle;
- Một `@Aspect` có đủ **năm loại advice khác nhau** nhắm tới cùng một target method.

Bốn tình huống:
1. `work("ok")` trả `"OK"`.
2. `work("empty")` trả `null`.
3. `work("fail")` ném `IllegalStateException`.
4. `work("recovered")` tự catch `IllegalStateException` rồi trả `"FALLBACK"`.

Tất cả các lời gọi từ `main` đi qua Spring-managed AOP proxy. Ví dụ cố ý **không dùng nhiều advice cùng loại**, để thứ tự in log được quyết định bởi các semantics của Spring 6.1, không rơi vào trường hợp thứ tự không xác định.

### 5.1. Cấu trúc project

```text
spring-aop-advice-order/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/order/
    ├── WorkService.java
    ├── OrderingAspect.java
    ├── AppConfig.java
    └── DemoApplication.java
```

### 5.2. Gradle

`settings.gradle`:

```groovy
rootProject.name = 'spring-aop-advice-order'
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
    mainClass = 'demo.order.DemoApplication'
}
```

`spring-context` cung cấp `AnnotationConfigApplicationContext` và Java configuration; `spring-aop` cung cấp proxy infrastructure; `aspectjweaver` cho phép Spring phân tích pointcut của `@Aspect`. Đây là Spring Framework thuần, không có Spring Boot logging hay auto-configuration để làm nhiễu ví dụ.

### 5.3. Service có ba kiểu outcome

`src/main/java/demo/order/WorkService.java`:

```java
package demo.order;

public class WorkService {

    public String work(String mode) {
        System.out.println("target:" + mode);

        if ("ok".equals(mode)) {
            return "OK";
        }
        if ("empty".equals(mode)) {
            return null;
        }
        if ("fail".equals(mode)) {
            throw new IllegalStateException("broken");
        }
        if ("recovered".equals(mode)) {
            try {
                throw new IllegalStateException("temporary");
            } catch (IllegalStateException ex) {
                return "FALLBACK";
            }
        }
        return "UNKNOWN";
    }
}
```

Đoạn `recovered` cố ý catch ngay trong target, nên method execution **không** ném exception ra khỏi Spring AOP proxy. Đây là một test phân biệt “đã từng có throw trong code Java” với “method execution kết thúc bằng exception”.

### 5.4. Một @Aspect với đúng năm advice types

`src/main/java/demo/order/OrderingAspect.java`:

```java
package demo.order;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class OrderingAspect {

    private static final String POINTCUT =
        "execution(public String demo.order.WorkService.work(String))";

    @Around(POINTCUT)
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String mode = (String) pjp.getArgs()[0];
        System.out.println("around:enter:" + mode);

        try {
            Object result = pjp.proceed();
            System.out.println(
                "around:return:" + String.valueOf(result));
            return result;
        } catch (Throwable ex) {
            System.out.println(
                "around:catch:" + ex.getClass().getSimpleName());
            throw ex;
        } finally {
            System.out.println("around:finally:" + mode);
        }
    }

    @Before(POINTCUT)
    public void before(JoinPoint jp) {
        String mode = (String) jp.getArgs()[0];
        System.out.println("before:" + mode);
    }

    @AfterReturning(
        pointcut = POINTCUT,
        returning = "result",
        argNames = "result"
    )
    public void afterReturning(Object result) {
        System.out.println(
            "afterReturning:" + String.valueOf(result));
    }

    @AfterThrowing(
        pointcut = POINTCUT,
        throwing = "error",
        argNames = "error"
    )
    public void afterThrowing(Throwable error) {
        System.out.println(
            "afterThrowing:" + error.getClass().getSimpleName());
    }

    @After(POINTCUT)
    public void afterFinally(JoinPoint jp) {
        String mode = (String) jp.getArgs()[0];
        System.out.println("after:" + mode);
    }
}
```

**Vì sao ví dụ này không phụ thuộc vào `-parameters`?**
- `@Around` chỉ nhận `ProceedingJoinPoint` làm tham số.
- `@Before`/`@After` chỉ nhận `JoinPoint` đứng đầu.
- `@AfterReturning` khai báo `returning="result"` và `argNames="result"` tường minh.
- `@AfterThrowing` khai báo `throwing="error"` và `argNames="error"` tường minh.

Từ Spring AOP standpoint:
- `@Around` mở đường vào và là nơi cuối cùng unwind trong ví dụ.
- `@Before` chạy trước khi target được gọi.
- `@AfterReturning` chạy cho `ok`, `empty` và `recovered`.
- `@AfterThrowing` chỉ chạy cho `fail`.
- `@After` chạy sau advice theo nhánh return/throw, nhưng trước khi `proceed()` hoàn thành và `@Around` in các dòng sau.

**Chú ý:** Dùng `private static final String POINTCUT` như trên hợp lệ khi annotation attribute là compile-time constant (`static final String` được ghép từ string literal). Đây không phải biến tính tại runtime.

### 5.5. Bật Spring AOP và đăng ký bean

`src/main/java/demo/order/AppConfig.java`:

```java
package demo.order;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public WorkService workService() {
        return new WorkService();
    }

    @Bean
    public OrderingAspect orderingAspect() {
        return new OrderingAspect();
    }
}
```

`@Aspect` **không** tự động đăng ký bean; ở đây `@Bean orderingAspect()` làm điều đó. `@EnableAspectJAutoProxy(proxyTargetClass = true)` kích hoạt Spring AOP, đồng thời dùng class-based proxy vì `WorkService` không implement interface trong demo.

### 5.6. Entry point

`src/main/java/demo/order/DemoApplication.java`:

```java
package demo.order;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApplication {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            WorkService service = context.getBean(WorkService.class);
            System.out.println("proxy=" + AopUtils.isAopProxy(service));

            invoke(service, "ok");
            invoke(service, "empty");
            invoke(service, "fail");
            invoke(service, "recovered");
        }
    }

    private static void invoke(WorkService service, String mode) {
        try {
            String result = service.work(mode);
            System.out.println("caller:" + mode + ":" + result);
        } catch (IllegalStateException ex) {
            System.out.println(
                "caller:" + mode + ":throws:"
                + ex.getClass().getSimpleName());
        }
    }
}
```

### 5.7. Chạy ứng dụng

Ở thư mục chứa `build.gradle`, dùng Gradle hỗ trợ Java 17:

```bash
gradle run
```

Nếu có Gradle Wrapper, dùng `./gradlew run` (Linux/macOS) hoặc `gradlew.bat run` (Windows).

**Output do chương trình Java in ra**, không tính các dòng Gradle hay logging môi trường:

```text
proxy=true
around:enter:ok
before:ok
target:ok
afterReturning:OK
after:ok
around:return:OK
around:finally:ok
caller:ok:OK
around:enter:empty
before:empty
target:empty
afterReturning:null
after:empty
around:return:null
around:finally:empty
caller:empty:null
around:enter:fail
before:fail
target:fail
afterThrowing:IllegalStateException
after:fail
around:catch:IllegalStateException
around:finally:fail
caller:fail:throws:IllegalStateException
around:enter:recovered
before:recovered
target:recovered
afterReturning:FALLBACK
after:recovered
around:return:FALLBACK
around:finally:recovered
caller:recovered:FALLBACK
```

### 5.8. Diễn giải kết quả

| Input | `@Before` | Nhánh completion | `@After` | Đường `@Around` |
| --- | --- | --- | --- | --- |
| `ok` | `before:ok` | `afterReturning:OK` | `after:ok` | `enter` → `return:OK` → `finally` |
| `empty` | `before:empty` | `afterReturning:null` | `after:empty` | `enter` → `return:null` → `finally` |
| `fail` | `before:fail` | `afterThrowing:IllegalStateException` | `after:fail` | `enter` → `catch` → `finally` |
| `recovered` | `before:recovered` | `afterReturning:FALLBACK` | `after:recovered` | `enter` → `return:FALLBACK` → `finally` |

Những kết luận có thể kiểm tra trực tiếp:

1. `@Around` in `around:enter` **trước** `@Before`; sau khi target và các advice trong chain hoàn tất, around mới in phần `return/catch/finally`.
2. `@After` xuất hiện **sau** `@AfterReturning` hoặc `@AfterThrowing`, dù nó có precedence cao hơn hai loại ấy trong bảng precedence.
3. `return null` vẫn là normal return, không làm `@AfterThrowing` chạy.
4. Exception được target bắt và chuyển thành `"FALLBACK"` không thoát ra khỏi method execution, nên đi theo `@AfterReturning`.
5. Ngoại lệ `IllegalStateException` của `fail` được `@AfterThrowing` quan sát và vẫn truyền tới caller vì `@Around` rethrow nguyên exception.
6. Output chỉ chứng minh thứ tự trong **cùng một `@Aspect`** với **mỗi advice type xuất hiện một lần**, không chứng minh thứ tự hai `@Before` khác nhau trong cùng aspect.

## 6. Mô hình interceptor chain giải thích output

Spring xử lý lời gọi theo một chuỗi kết hợp. Với demo, có thể hình dung đơn giản như sau:

```text
Client
  |
  v
Spring AOP proxy
  |
  +-- Around interceptor [precedence 1]
  |       enter
  |       try {
  |           |
  |           +-- Before interceptor [2]
  |           |       before
  |           |       |
  |           |       +-- After(finally) interceptor [3]
  |           |       |       try {
  |           |       |           |
  |           |       |           +-- AfterReturning [4]
  |           |       |           |      |
  |           |       |           |      +-- AfterThrowing [5]
  |           |       |           |              |
  |           |       |           |              v
  |           |       |           |            target
  |           |       |           |
  |           |       |           +-- normal: afterReturning
  |           |       |           +-- throw: afterThrowing
  |           |       |       } finally {
  |           |       |           after
  |           |       |       }
  |           |       |
  |       } finally {
  |           around:finally
  |       }
  |
  v
Caller
```

Đây là **sơ đồ lý giải semantics**, không phải mô hình triển khai nội bộ được đảm bảo mọi phiên bản hoặc interceptor implementation đều giống từng node. Nhưng với Spring `@AspectJ` advice ordering được mô tả trong tài liệu, mô hình cho thấy đúng lý do `@After` log sau completion-specific advice.

### Vì sao không viết “@After chạy trước @AfterReturning”?

Vì câu ấy chỉ dựa trên danh sách precedence và bỏ qua cơ chế finally. **Precedence cao hơn** tức advice có quyền *bao ngoài* các advice thấp hơn; với after-finally, callback đặt tại **bước thoát** khỏi phạm vi bao ngoài, nên thực sự được gọi muộn hơn.

### Vì sao không viết “@After luôn là dòng cuối cùng”?

Vì `@Around` có precedence cao hơn, nằm ngoài `@After` và có thể có phần xử lý sau `proceed()`; trong ví dụ nó in `around:return` và `around:finally` **sau `@After`**. Nếu còn aspects khác, transaction/cache/retry hoặc interceptor tùy chỉnh, vị trí log cũng có thể đổi.

## 7. Hai advice cùng loại trong cùng một @Aspect: thứ tự không xác định

Một điểm khác dễ nhầm:

```java
@Aspect
public class DoubleBeforeAspect {

    @Before("execution(* demo.order.WorkService.work(..))")
    public void firstLog() {
        System.out.println("A");
    }

    @Before("execution(* demo.order.WorkService.work(..))")
    public void secondLog() {
        System.out.println("B");
    }
}
```

Đây là **snippet minh họa**, không dán vào project ở mục 5 nếu muốn giữ exact stdout.

Không được cam kết `A` luôn in trước `B` chỉ vì `firstLog()` được viết ở dòng trên trong Java source. Với hai advice cùng loại trong cùng một `@Aspect`, Spring reference nêu **ordering không xác định**, do Java reflection trên class do `javac` biên dịch không cung cấp thứ tự khai báo nguồn đáng tin cậy.

### Cách khắc phục

**Cách 1 — Gộp vào một advice** nếu hai hành động luôn phải đi cùng nhau:

```java
@Before("execution(* demo.order.WorkService.work(..))")
public void logInOrder() {
    System.out.println("A");
    System.out.println("B");
}
```

Bây giờ thứ tự `A` rồi `B` là thứ tự **các statement trong cùng một Java method**, nên rõ ràng.

**Cách 2 — Tách thành hai aspects** nếu cần policy độc lập và ordering qua `@Order`:

```java
@Aspect
@Order(10)
public class FirstAspect {
    @Before("execution(* demo.order.WorkService.work(..))")
    public void first() { System.out.println("A"); }
}
```

```java
@Aspect
@Order(20)
public class SecondAspect {
    @Before("execution(* demo.order.WorkService.work(..))")
    public void second() { System.out.println("B"); }
}
```

Đây là snippets minh họa, cần import `@Aspect`, `@Order`, `@Before` và đăng ký cả hai thành Spring beans. Aspect có giá trị `@Order` **thấp hơn** có precedence **cao hơn**: `FirstAspect` được gọi trước trên đường vào.

**Cách không giải quyết:** Đặt `@Order(1)` lên **một aspect chứa cả hai `@Before`** không làm thứ tự giữa hai method advice cùng loại trở nên xác định. `@Order` ở aspect level không phải annotation đánh số thứ tự từng method trong cùng `@Aspect`.

## 8. Cùng aspect so với nhiều aspect: không đánh đồng

### 8.1. Quy tắc trong một aspect

Trong **một** lớp `@Aspect` và với các **loại advice khác nhau**, Spring có precedence theo loại:

```text
@Around > @Before > @After > @AfterReturning > @AfterThrowing
```

Đó là **precedence**, không phải thứ tự in log after phase. `@After` thực sự chạy sau completion-specific advice do finally semantics.

### 8.2. Quy tắc giữa những aspects

Giữa **hai hoặc nhiều lớp `@Aspect`**, nếu không chỉ định, Spring không bảo đảm thứ tự giữa các aspects khi nhiều advice cùng match.

Dùng:
- `@Order(int)` trên class aspect;
- Hoặc implements `org.springframework.core.Ordered` trên bean aspect.

Giá trị order **nhỏ hơn có precedence cao hơn**. Với hai around aspects:

```text
@Order(1): around A before
    @Order(2): around B before
        target
    @Order(2): around B after
@Order(1): around A after
```

Advice precedence bên trong từng aspect và aspect-level ordering là **hai chiều phân loại liên quan nhưng không đồng nhất**. Khi viết integration test nên ghi rõ đang kiểm tra dimension nào.

### 8.3. Khi có transaction, cache, retry

`@Transactional`/`@Cacheable` trong proxy mode đóng góp interceptor/advisor từ hạ tầng Spring khác. Những advisors ấy không nhất thiết là advice methods trong **chính lớp `@Aspect` của bạn**. Vì vậy không được lấy bảng precedence 5 loại advice rồi tuyên bố logging luôn ở ngoài transaction hoặc luôn ở trong cache.

Trong một proxy có nhiều advisors:
- Thứ tự Spring `@Order` và thứ tự advisor infrastructure có thể ảnh hưởng scope đo thời gian.
- Cache hit có thể short-circuit trước khi một advice phía trong chạy.
- Retry có thể làm target chạy nhiều lần dù caller chỉ gọi proxy một lần.
- Transaction rollback dựa trên exception rules và thứ tự transaction interceptor quan sát outcome.

Cần inspect advisor ordering hoặc test theo ApplicationContext thực tế, không phỏng đoán từ thứ tự code.

## 9. @AfterReturning, @AfterThrowing và @After không thay thế nhau

### 9.1. @AfterReturning chỉ khi normal completion

```java
@AfterReturning(
    pointcut = "execution(* demo.order.WorkService.work(..))",
    returning = "value",
    argNames = "value"
)
public void completed(Object value) {
    // Kể cả null vẫn là normal completion.
}
```

`returning` có thể bind result; kiểu parameter advice là một ràng buộc kiểu. Đổi `Object` thành `String` có thể giới hạn những trường hợp phù hợp hơn. Không dùng after-returning để thay reference trả về cho caller; muốn đổi kết quả dùng `@Around`.

### 9.2. @AfterThrowing chỉ khi exceptional completion

```java
@AfterThrowing(
    pointcut = "execution(* demo.order.WorkService.work(..))",
    throwing = "error",
    argNames = "error"
)
public void failed(Throwable error) {
    // Exception thoát ra từ execution.
}
```

Nếu method target tự catch exception trước khi return fallback, `@AfterThrowing` không được gọi cho exception đã được catch bên trong target. Nếu advice parameter chỉ có kiểu `IllegalArgumentException` thì những exception khác không được bind. Dùng `Throwable` khi cần quan sát cả checked/unchecked, nhưng không swallow lỗi ở advice chỉ có vai trò ghi log.

### 9.3. @After theo finally

```java
@After("execution(* demo.order.WorkService.work(..))")
public void finished() {
    // Cả normal và exceptional completion.
}
```

`@After` không biết kết quả cuối là business success hay failure; nó chỉ thực hiện hook theo finally. Nếu cần biết kết quả và exception trong cùng một scope, dùng `@Around` và kiểm soát `proceed()` cẩn thận.

### 9.4. Một @Before ném exception thì sao?

`@Before` có thể ném exception, khiến phần chain phía trong không được tiếp tục. Khi đó target method có thể **chưa chạy**, và các completion advice đứng phía trong có thể không được gọi. Đừng suy ra mọi call đã vào proxy chắc chắn có đủ các dòng `@After`/`@AfterThrowing` của cùng aspect nếu phần chain bị cắt trước khi chúng được vào.

Một câu đúng hơn là: “`@After` có finally semantics **nếu Spring đã đi vào interceptor tương ứng**.” Không phải: “`@After` luôn chạy với mọi exception ở bất kỳ vị trí nào trong proxy.”

## 10. Những lỗi đọc log rất thường gặp

| Hiện tượng | Hiểu đúng | Cách kiểm tra |
| --- | --- | --- |
| `@After` log sau `@AfterReturning` dù có precedence cao hơn | After-finally interceptor bao ngoài completion advice | Phân biệt chain precedence và callback event time |
| `@Around` log sau `@After` | Around advice đang unwind sau `proceed()` | Xem `try/finally` trong `@Around` |
| `@AfterThrowing` không chạy khi return `null` | `null` là normal return | Kiểm tra method có throw ra ngoài hay không |
| `@AfterThrowing` không thấy exception target đã catch | Exception không thoát khỏi method execution | Ghi nhận ở target catch khi cần |
| Hai `@Before` cùng aspect đảo thứ tự giữa môi trường | Same-type advice ordering không xác định | Gộp method hoặc tách thành aspects có `@Order` |
| Đặt `@Order` lên một aspect nhưng hai `@Before` vẫn không ổn định | `@Order` quản precedence giữa aspects, không sắp xếp method cùng loại | Thiết kế lại tổ chức advice |
| `@After` không chạy khi `@Before` ném exception | Chain chưa vào after-finally interceptor | Kiểm tra phần nào của chain đã được chạy |
| `@After` không chạy khi around return trước `proceed` | Inner advisors không được đi tới | Kiểm tra branch short-circuit |
| Log thứ tự khác do transaction/cache | Infrastructure advisors có precedence riêng | Kiểm tra `Advised.getAdvisors()` và effective order |
| `@AfterThrowing` trong cùng aspect không bắt exception do `@AfterReturning` tạo | Advice type áp dụng vào join point, không phải global error handler | Xử lý exception trong advice gây lỗi hoặc quanh phạm vi phù hợp |
| Gọi `this.otherMethod()` nhưng không thấy advice | Self-invocation bypass Spring proxy | Tách bean hoặc gọi qua dependency đã proxy |
| Aspect hoạt động trên method này, không trên method kia | Pointcut/visibility/proxy type khác nhau | Xác minh method signature, `final`/`private`, JDK vs CGLIB |
| `@Aspect` annotation có nhưng advice không chạy | Aspect chưa thành Spring bean, chưa bật AOP hoặc bean target không proxy | Đăng ký `@Bean`/`@Component` và bật `@EnableAspectJAutoProxy` |

## 11. Kiểm thử đúng thứ tự trong cùng @Aspect

### 11.1. Kiểm thử có chủ đích

Với một integration test, nên ghi nhận event list, ví dụ:

```text
around.enter
before
target
afterReturning
after
around.exit
```

Rồi so sánh với expected events **trên một Spring-managed proxy**. Không unit test trực tiếp `new WorkService()` và kỳ vọng event list từ `@Aspect` được thêm vào.

Ở trường hợp exception:

```text
around.enter
before
target.throw
afterThrowing
after
around.catch
around.finally
```

Không so sánh thứ tự hai advice cùng loại trừ khi bạn đã thiết kế bảo đảm bằng gộp code hoặc tách aspects.

### 11.2. Có cần dùng @Order trong cùng aspect không?

Không cần để đạt precedence **theo loại advice** như ở phần 2. Nếu chỉ có một `@Around`, một `@Before`, một `@After`, một `@AfterReturning` và một `@AfterThrowing` đều khớp join point, Spring áp quy tắc loại advice. `@Order` chỉ phát huy khi **có nhiều aspects/advisors khác** cần xác định vị trí tương đối.

### 11.3. Cách inspect advisor chain

Nếu bean có Spring AOP proxy hỗ trợ `Advised`, có thể chẩn đoán:

```java
import org.springframework.aop.framework.Advised;
import org.springframework.aop.Advisor;

Object bean = context.getBean("workService");

if (bean instanceof Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
```

Đây là **snippet chẩn đoán** cần biến `context` có sẵn, không phải file standalone. Nó cho biết chain có advisor nào; tên lớp hạ tầng hoặc thứ tự phát biểu từ `toString()` có thể phụ thuộc Spring version, vì vậy **không** dùng tên class generated làm hợp đồng logic. Nếu cần lý giải sâu, inspect advisor instances và pointcuts cùng với event log thực tế.

### 11.4. Test cả null, exception và side effects

Đối với hệ thống thật, thêm test:
- `return null` có đi vào `@AfterReturning`?
- Exception checked/unchecked được `@AfterThrowing` bind bằng type phù hợp?
- Target tự catch exception rồi return có được tính normal completion?
- Advice `@Around` có giữ nguyên result/exception khi không có policy fallback?
- Khi nhiều advisors có transaction/cache/retry, thứ tự event có được mô tả chính xác ở điểm quan sát?
- Với `@Before` throw hoặc `@Around` không `proceed()`, các advice phía trong có được bỏ qua như dự kiến?
- Nếu có nhiều `@Aspect`, precedence `@Order` có được đăng ký đúng không?

## 12. Version notes và phạm vi đảm bảo

### 12.1. Spring Framework 5.2.7 trở về sau

Quy tắc precedence theo loại advice trong cùng một `@Aspect` được Spring nêu rõ từ phiên bản **5.2.7**:

```text
@Around > @Before > @After > @AfterReturning > @AfterThrowing
```

Trong đó `@After` có after-finally semantics, nên **callback chạy sau completion-specific advice**. Nếu bạn duy trì ứng dụng rất cũ, đọc đúng Spring reference theo phiên bản đó trước khi đưa ra kết luận về order.

### 12.2. Spring Framework 6.x

Project phần 5 dùng `spring-context:6.1.14` và `spring-aop:6.1.14`, vì vậy không phụ thuộc những thay đổi mặc định Spring Boot. Với Spring 6.2 hoặc 7.0, nên kiểm tra tài liệu cùng phiên bản khi có vấn đề đặc thù, nhưng nguyên tắc **phân biệt precedence với finally callback timing** được tài liệu Spring hiện đại duy trì.

### 12.3. Spring Boot không đổi nghĩa @After

Spring Boot có thể cấu hình Spring AOP tự động khi đủ dependencies và có các properties ảnh hưởng loại proxy. Nhưng Boot không biến `@After` thành một callback before `@AfterReturning`, cũng không biến source declaration order của các advice methods thành cơ chế sắp xếp được bảo đảm.

### 12.4. Khác biệt giữa @AspectJ style và XML AOP

Đối với một `@Aspect` class dùng annotation style, Spring reference nêu precedence theo loại advice như trên. Trong **schema-based XML AOP**, việc sắp xếp advice elements trong một `<aop:aspect>` có quy tắc riêng theo **thứ tự phần tử XML được khai báo**. Không trộn hai cơ chế và kết luận vì XML có thứ tự khai báo nên Java `@Aspect` cũng phải chạy theo thứ tự source methods.

## 13. Checklist khi xây dựng một @Aspect nhiều advice

- [ ] Chỉ sử dụng các advice types thực sự cần thiết cho cross-cutting concern.
- [ ] Nắm rõ precedence trong một `@Aspect`: `@Around`, `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`.
- [ ] Nhớ `@After` chạy **sau** `@AfterReturning`/`@AfterThrowing` của cùng aspect khi unwind.
- [ ] Với normal return, kể cả `null`, dùng `@AfterReturning`; với exception thoát ra, dùng `@AfterThrowing`.
- [ ] Không suy luận business success chỉ từ `@AfterReturning`.
- [ ] `@Around` gọi `proceed()` khi cần target và inner chain thực thi; không nuốt exception trừ khi có chính sách.
- [ ] Không có hai advice cùng loại cần thứ tự cố định trong **cùng** `@Aspect`; nếu có, gộp hoặc tách aspect.
- [ ] Dùng `@Order`/`Ordered` khi phải điều khiển precedence **giữa** aspects.
- [ ] Đã phân biệt code source order với advisor precedence.
- [ ] Có integration test qua Spring-managed proxy cho normal, null, thrown và recovered completion.
- [ ] Không nhầm transaction/cache/retry advisor với advice method cùng `@Aspect`.
- [ ] Có test hoặc quan sát thực cho self-invocation, short-circuit và exception do advice khác phát sinh.
- [ ] Không sửa logic chỉ để “ép” log theo danh sách precedence; hiểu call stack vào/ra mới là nền tảng.

## 14. Kết luận

Trong **cùng một Spring `@Aspect`**, thứ tự **precedence** giữa các advice types từ cao đến thấp là:

**`@Around` → `@Before` → `@After` → `@AfterReturning` → `@AfterThrowing`.**

Nhưng khi method hoàn tất, **`@After` được thực thi sau `@AfterReturning` hoặc `@AfterThrowing`**, vì nó là một finally-style advice bao ngoài hai nhánh completion. `@Around` vẫn có thể thực hiện phần cleanup/catch sau khi `proceed()` quay lại. Đây là lý do không thể đọc precedence thành “các log luôn xuất hiện theo thứ tự từ trái qua phải”.

Với **hai advice cùng loại trong cùng một `@Aspect`**, thứ tự không được bảo đảm; hãy gộp chúng nếu có dependency về thứ tự, hoặc tách thành các `@Aspect` có `@Order` thích hợp. Với những **aspects khác nhau**, cần xét order của aspect, các advisors khác và call path qua Spring proxy.

### Tài liệu chính thức

- [Spring Framework — Declaring Advice / Advice Ordering](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): precedence các advice types trong một `@Aspect`, after-finally semantics, same-type order không xác định và `@Order` giữa aspects.
- [Spring Framework 5.3 — AOP Advice Ordering](https://docs.spring.io/spring-framework/docs/5.3.x/reference/html/core.html): mốc làm rõ từ Spring Framework 5.2.7 và thứ tự các loại advice.
- [Spring Framework — AOP Concepts](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html): `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After` và `@Around`.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): bật Spring AOP, đăng ký `@Aspect` thành Spring bean.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): ranh giới JDK/CGLIB proxy và self-invocation.
- [Spring Framework — Schema-based AOP Support](https://docs.spring.io/spring-framework/reference/core/aop/schema.html): lưu ý khác biệt về thứ tự advice elements trong `<aop:aspect>` XML so với annotation-based `@Aspect`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-ordering-006">Giới hạn đảm bảo thứ tự giữa nhiều advice cùng loại</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn đảm bảo thứ tự giữa nhiều advice cùng loại trong Spring AOP

## 1. Bài học giải quyết vấn đề gì?

Khi nhiều Spring AOP advice cùng khớp một method execution của Spring bean, người học thường nhìn vào thứ tự các method được viết trong một class rồi cho rằng Spring sẽ gọi chúng từ trên xuống dưới. **Đó không phải một bảo đảm của Spring AOP.**

Đặc biệt, **hai hoặc nhiều advice cùng loại** – ví dụ hai `@Before`, hai `@After`, hai `@AfterReturning`, hai `@AfterThrowing` hoặc hai `@Around` – **được khai báo trong cùng một class `@Aspect` và cùng áp dụng cho một join point** thì **thứ tự giữa chúng không được định nghĩa** theo tài liệu Spring Framework. Java reflection không cung cấp một hợp đồng đáng tin cậy để truy hồi thứ tự khai báo trong source của các methods mà `javac` biên dịch.

Một ví dụ dễ mắc lỗi:

~~~java
@Aspect
@Component
public class OrderChecksAspect {

    @Before("execution(* demo.JobApi.run(..))")
    public void validate() {
        System.out.println("VALIDATE");
    }

    @Before("execution(* demo.JobApi.run(..))")
    public void audit() {
        System.out.println("AUDIT");
    }
}
~~~

Đây là **fragment minh họa**: cần các imports, `JobApi` và Spring context phù hợp. **Không được kết luận chắc rằng VALIDATE sẽ luôn xuất hiện trước AUDIT** chỉ vì `validate()` được viết phía trên `audit()`.

Nếu `audit()` giả định `validate()` đã kiểm tra quyền/đầu vào, kết quả có thể không như mong đợi khi thay phiên bản compiler, Spring hoặc môi trường triển khai.

Mục tiêu của README này:

1. Nêu đúng **bảo đảm thứ tự** mà Spring AOP cung cấp, và phần **không được bảo đảm**.
2. Phân biệt advice **cùng loại trong một aspect** và advice **ở các aspect khác nhau**.
3. Hiểu `@Order` / `Ordered` **điều khiển precedence cấp aspect** thay vì “đánh số thứ tự source methods” trong cùng aspect.
4. Nắm quy tắc **trên đường vào** và **trên đường ra** khi advice bọc nhau; tránh đọc sai `@After`.
5. Tổ chức lại thành **một advice có các bước tuần tự**, hoặc thành **nhiều aspect có order khác nhau**.
6. Chạy ví dụ **Java 17+ / Spring Framework 6.2.x / Maven** cho hai `@Before` nằm ở **hai aspect được sắp thứ tự**, có stdout có thể kiểm chứng.
7. Biết cách viết test: khẳng định hợp đồng được bảo đảm và **không viết test phụ thuộc vào hành vi tình cờ**.

**Special Note/phạm vi:** Chỉ tập trung vào **Spring Framework proxy-based AOP** và `@AspectJ` style mà Spring hỗ trợ. Các khái niệm chung của AspectJ chỉ được nhắc khi cần diễn giải quy tắc Spring; không chuyển sang bytecode weaving độc lập.

## 2. Hai trục cần phân biệt: loại advice và nơi khai báo

Có hai câu hỏi khác nhau:

- **Các advice thuộc cùng loại hay khác loại?** Ví dụ hai `@Before` là **cùng loại**; `@Before` và `@AfterReturning` là **khác loại**.
- **Chúng nằm trong cùng class `@Aspect` hay trong hai `@Aspect` class khác nhau?**

Bảng phân loại:

| Trường hợp | Spring bảo đảm điều gì? | Cách xử lý khi thứ tự là yêu cầu nghiệp vụ |
| --- | --- | --- |
| Hai `@Before` cùng class `@Aspect`, cùng match | **Không xác định thứ tự giữa hai method advice** | Gộp thành một `@Before` và gọi các bước tuần tự, hoặc tách aspect |
| Hai `@AfterReturning` cùng class `@Aspect`, cùng match | **Không xác định thứ tự giữa hai advice cùng loại** | Gộp hoặc tách aspect có `@Order` |
| Hai `@Around` cùng class `@Aspect`, cùng match | **Không xác định thứ tự giữa hai advice cùng loại** | Gộp logic vào một `@Around` hoặc tách aspect |
| Hai advice cùng loại ở **hai aspect khác nhau**, không order | **Thứ tự không xác định**, trừ khi có cấu hình precedence khác | Cấp precedence tường minh |
| Hai advice ở hai aspects, `@Order(10)` và `@Order(20)` | Aspect có **giá trị nhỏ hơn ưu tiên cao hơn** | Dùng khi cần ranh giới concern rõ và thứ tự được kiểm soát |
| Các advice **khác loại** trong cùng `@Aspect` | Spring xác định precedence theo loại advice; thời điểm thực thi phụ thuộc ngữ nghĩa trước/sau | Hiểu `@After` dạng finally và đường vào/ra |

**Không nên đồng nhất “thứ tự không xác định” với “Spring sẽ chọn ngẫu nhiên ở mỗi request”.** Một JVM có thể cho ra thứ tự trông ổn định trong hàng nghìn lần gọi, nhưng điều đó **không tạo hợp đồng** để phụ thuộc khi nâng cấp hoặc tái cấu trúc.

## 3. Chính xác Spring nói gì về advice cùng loại?

Tài liệu Spring Framework phần **Declaring Advice → Advice Ordering** chỉ rõ:

- Khi hai advice cùng loại trong **cùng một class `@Aspect`** đều cần chạy tại cùng join point, **thứ tự không được định nghĩa**.
- Lý do: thứ tự khai báo methods trong source Java không thể truy xuất một cách tin cậy qua reflection đối với classes do `javac` biên dịch.
- Spring khuyến nghị **gộp những advice liên quan vào một method** ở mỗi aspect/join point, hoặc **tách chúng thành các `@Aspect` classes riêng** rồi dùng `Ordered` hoặc `@Order` ở cấp aspect.

Chú ý điều kiện **“cùng áp dụng cho một join point”**. Nếu hai `@Before` có pointcut **loại trừ lẫn nhau**, chúng không tranh nhau thứ tự trên cùng lời gọi.

Ví dụ:

~~~java
@Before("execution(* demo.JobApi.run(..))")
public void beforeRun() {
    // Chỉ khi run(...) match.
}

@Before("execution(* demo.JobApi.cancel(..))")
public void beforeCancel() {
    // Chỉ khi cancel(...) match.
}
~~~

Nếu `run` và `cancel` là hai phương thức khác nhau, cùng một invocation sẽ không kích hoạt cả hai advice trong trường hợp điển hình; không có tranh chấp về thứ tự cần giải quyết.

Ngược lại, khi hai pointcuts giao nhau:

~~~java
@Before("execution(* demo.JobApi.*(..))")
public void beforeAnyMethod() {
    // Match mọi method trên JobApi.
}

@Before("execution(* demo.JobApi.run(..))")
public void beforeRunOnly() {
    // Match cả run(...), nên giao với beforeAnyMethod().
}
~~~

Lời gọi `run(...)` có thể kích hoạt **cả hai `@Before`**. Nếu chúng ở cùng class `@Aspect`, không được dựa vào thứ tự khai báo của hai method advice.

## 4. @Order không đánh số thứ tự từng method advice trong cùng @Aspect

Trong Spring, cách phổ biến đặt precedence giữa **hai aspects** là:

~~~java
@Aspect
@Component
@Order(10)
public class FirstAspect {
    @Before("execution(* demo.JobApi.run(..))")
    public void before() {
        System.out.println("FIRST");
    }
}
~~~

và một aspect khác:

~~~java
@Aspect
@Component
@Order(20)
public class SecondAspect {
    @Before("execution(* demo.JobApi.run(..))")
    public void before() {
        System.out.println("SECOND");
    }
}
~~~

**Đây là các fragments cần đặt ở hai file tương ứng**, không phải mã hoàn chỉnh của chương trình. Với hai aspect có precedence khác nhau:

- `@Order(10)` có **precedence cao hơn** `@Order(20)` vì số nhỏ hơn.
- Hai `@Before` match cùng invocation và **không ném ngoại lệ** sẽ được gọi trên đường vào theo thứ tự precedence: **FIRST rồi SECOND**, sau đó tới target.
- Khi chạy code thật, Spring còn phải tạo bean/aspect/advisor và caller phải gọi qua **Spring AOP proxy**.

**Đặt `@Order` trên từng method advice trong một `@Aspect` không phải giải pháp được Spring tài liệu hóa để bảo đảm thứ tự giữa các advice cùng loại trong chính class đó.** Cách an toàn là **sắp thứ tự ở cấp aspect** hoặc **gom các bước cần thứ tự vào một advice**.

Cũng không dùng:

- Tên method `aFirst`/`zLast` để ép thứ tự.
- Vị trí method trong source.
- Thứ tự import hoặc thứ tự class files.
- Thứ tự xuất hiện trong log của một lần chạy.
- Annotation tự tạo không được Spring advisor chain đọc làm precedence.

Nếu `@Order` có giá trị **bằng nhau**, đừng coi thứ tự hai aspects là một hợp đồng ổn định. Muốn thứ tự cụ thể, cho chúng **hai mức precedence khác nhau**.

## 5. Thứ tự trên đường vào và đường ra

Spring AOP làm việc theo chuỗi interceptor quanh một method execution:

~~~text
Client
   |
   v
Aspect order=10 (ưu tiên cao)
   |
   v
Aspect order=20 (ưu tiên thấp hơn)
   |
   v
TARGET METHOD
   |
   v
Aspect order=20 (hoàn tất phía trong)
   |
   v
Aspect order=10 (hoàn tất phía ngoài)
   |
   v
Caller
~~~

Hệ quả:

- **Trên đường vào:** advice có precedence cao thường chạy **trước**.
- **Trên đường ra:** advice có precedence cao thường chạy **sau**, vì nó bao quanh phần phía trong.
- Đây là quy tắc trực giác của **lồng interceptor**, không nên suy ra rằng mọi advice đều chạy ở cả hai chiều. `@Before` chỉ xử lý trước; `@AfterReturning` chỉ chạy trên nhánh trả bình thường, v.v.

Với hai `@Around` nằm ở hai aspects:

~~~text
ORDER 10: around ENTER
  ORDER 20: around ENTER
    TARGET
  ORDER 20: around EXIT
ORDER 10: around EXIT
~~~

Nếu cả hai gọi `proceed()` đúng một lần và target trả về bình thường, thứ tự này được giải thích bằng cấu trúc lồng. Nếu aspect bên ngoài **không gọi `proceed()`**, advisor phía trong và target **không được đi tới**.

**Lưu ý:** Không dùng `@Order` để “buộc một `@AfterReturning` chạy như `@Before`”. `@Order` quyết định precedence giữa aspects; **loại advice** quyết định khi nào code chạy.

## 6. Precedence trong cùng @Aspect khi các loại advice khác nhau

Spring Framework 6.2 công bố precedence giữa **các loại advice khác nhau** trong một `@Aspect` (cao đến thấp):

1. `@Around`.
2. `@Before`.
3. `@After`.
4. `@AfterReturning`.
5. `@AfterThrowing`.

**Nhưng phải hiểu ngữ nghĩa thực thi, không đọc dãy trên như thứ tự in log tuyến tính.** Theo Spring, `@After` mang ngữ nghĩa kiểu **finally** và **thực tế được gọi sau `@AfterReturning` hoặc `@AfterThrowing`** trong cùng aspect.

Ví dụ minh họa một aspect có nhiều loại advice:

~~~java
@Aspect
@Component
public class TraceAspect {

    @Around("execution(* demo.JobApi.run(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("AROUND enter");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("AROUND exit");
        }
    }

    @Before("execution(* demo.JobApi.run(..))")
    public void before() {
        System.out.println("BEFORE");
    }

    @AfterReturning("execution(* demo.JobApi.run(..))")
    public void afterSuccess() {
        System.out.println("AFTER RETURNING");
    }

    @After("execution(* demo.JobApi.run(..))")
    public void afterFinally() {
        System.out.println("AFTER FINALLY");
    }
}
~~~

Đoạn này là **fragment class minh họa**, cần imports và service/app context. Với **target trả về bình thường, không có advice khác làm thay đổi flow**, có thể hiểu logic bao quanh như:

~~~text
AROUND enter
BEFORE
TARGET
AFTER RETURNING
AFTER FINALLY
AROUND exit
~~~

Nếu target ném exception, `@AfterReturning` không chạy, còn `@After` theo finally semantics vẫn chạy khi invocation đi tới phạm vi của nó. `@Around` có thể catch/transform exceptions nên phải xét cả mã của advice.

Điểm trọng tâm của bài không phải học thuộc một danh sách advice type chung, mà là **phân biệt thứ tự được Spring định nghĩa giữa các loại khác nhau với thứ tự *không được định nghĩa* giữa nhiều advice cùng loại trong một class**.

## 7. Ví dụ Maven chạy được: hai @Before ở hai aspects có thứ tự rõ ràng

Để có stdout **có thứ tự được bảo đảm**, ví dụ chính dùng:

- Một Spring service bean triển khai `JobApi`.
- `FirstBeforeAspect` là một `@Aspect` có `@Order(10)`.
- `SecondBeforeAspect` là một `@Aspect` khác có `@Order(20)`.
- Cả hai cùng có **một `@Before`**, match phương thức `JobApi.run(..)`.
- Client gọi qua proxy từ `AnnotationConfigApplicationContext`.

**Môi trường:** Java 17+, Maven 3.9+, Spring Framework 6.2.12 và AspectJ Weaver 1.9.24.

### 7.1. Cấu trúc dự án

~~~text
spring-aop-advice-order/
├── pom.xml
└── src/main/java/demo/
    ├── JobApi.java
    ├── JobService.java
    ├── FirstBeforeAspect.java
    ├── SecondBeforeAspect.java
    ├── AppConfig.java
    └── App.java
~~~

### 7.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-advice-order</artifactId>
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

`spring-context` cung cấp ApplicationContext và kéo theo các module Spring AOP cần thiết. `aspectjweaver` cho Spring đọc `@AspectJ` annotations/pointcut expressions. Không có AspectJ compiler hoặc weaving trong ví dụ.

### 7.3. JobApi.java

~~~java
package demo;

public interface JobApi {
    String run(String jobId);
}
~~~

Đây là contract công khai. Spring có thể tạo JDK dynamic proxy khi implementation có interface và không ép CGLIB.

### 7.4. JobService.java

~~~java
package demo;

import org.springframework.stereotype.Service;

@Service
public class JobService implements JobApi {

    @Override
    public String run(String jobId) {
        System.out.println("TARGET run " + jobId);
        return "done:" + jobId;
    }
}
~~~

`@Service` được component scan đăng ký trong Spring context. Code target chỉ in một dòng và trả String; không cần database/network để thấy thứ tự.

### 7.5. FirstBeforeAspect.java

~~~java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(10)
public class FirstBeforeAspect {

    @Before("execution(* demo.JobApi.run(..))")
    public void before() {
        System.out.println("BEFORE priority10");
    }
}
~~~

`@Order(10)` đặt ở **class aspect**. Advice này có precedence cao hơn aspect `@Order(20)`. Đừng hiểu `10` là “chạy mười lần”, “chờ mười mili giây” hoặc “thứ tự dòng 10”.

### 7.6. SecondBeforeAspect.java

~~~java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(20)
public class SecondBeforeAspect {

    @Before("execution(* demo.JobApi.run(..))")
    public void before() {
        System.out.println("BEFORE priority20");
    }
}
~~~

Cả hai `@Before` cùng match `JobApi.run(..)`, nhưng nằm ở **hai class `@Aspect` khác nhau**. Vì precedence tường minh nên có thể dự đoán thứ tự.

### 7.7. AppConfig.java

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

`@ComponentScan` đăng ký service và hai aspects. `@EnableAspectJAutoProxy` tạo AOP advisor/proxy thích hợp. Nếu chỉ đặt `@Aspect` mà không đăng ký bean qua `@Component` hoặc `@Bean` thì Spring không tự đưa advice vào context.

### 7.8. App.java

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            JobApi jobs = context.getBean(JobApi.class);

            System.out.println(
                    "IS_PROXY " + AopUtils.isAopProxy(jobs));

            String result = jobs.run("J-17");
            System.out.println("RESULT " + result);
        }
    }
}
~~~

Caller lấy Spring-managed bean theo interface `JobApi`, không dùng `new JobService()` và không cast JDK proxy về concrete implementation class.

Chạy tại thư mục có `pom.xml`:

~~~bash
mvn -q clean compile exec:java
~~~

### 7.9. Stdout logic kỳ vọng

~~~text
IS_PROXY true
BEFORE priority10
BEFORE priority20
TARGET run J-17
RESULT done:J-17
~~~

Kết quả trên là **stdout logic kỳ vọng của ví dụ**, bỏ qua Maven/Spring logging. Hai dòng `BEFORE` có thứ tự dựa trên **`@Order` cấp aspect**, không phải thứ tự source methods.

Giải thích:

1. `IS_PROXY true`: Spring trả một AOP proxy vì advisor phù hợp với service.
2. `BEFORE priority10` chạy trước nhờ precedence cao hơn.
3. `BEFORE priority20` chạy tiếp.
4. `TARGET run J-17` chỉ xuất hiện sau hai before advices trả về bình thường.
5. `RESULT done:J-17` là giá trị do target trả về và proxy chuyển tiếp.

Nếu advice priority10 **ném exception**, priority20 và target có thể không được đi tới ở đường gọi này. Precedence là **trình tự khi control flow tiếp tục**, không phải cam kết mọi advice sẽ luôn chạy bất kể exception.

## 8. Phản ví dụ: hai @Before trong cùng một aspect

Giả sử bạn **thay thế** hai aspect `FirstBeforeAspect` và `SecondBeforeAspect` bằng một aspect:

~~~java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AmbiguousBeforeAspect {

    @Before("execution(* demo.JobApi.run(..))")
    public void alpha() {
        System.out.println("ALPHA");
    }

    @Before("execution(* demo.JobApi.run(..))")
    public void beta() {
        System.out.println("BETA");
    }
}
~~~

Đây là **file riêng có thể dùng khi bạn đổi cấu trúc demo**. Để test, **loại hai original aspect beans khỏi context** hoặc tắt registration của chúng rồi mới đăng ký `AmbiguousBeforeAspect`. Nếu đồng thời giữ cả ba, có thêm advice và stdout sẽ khác.

Bạn có thể quan sát một trong hai cách sắp xếp:

~~~text
ALPHA
BETA
TARGET run J-17
~~~

hoặc:

~~~text
BETA
ALPHA
TARGET run J-17
~~~

**Hai đoạn trên chỉ là các khả năng minh họa, không phải “expected output” độc nhất.** Tài liệu Spring không cam kết chọn phương án nào. Nếu vài lần chạy luôn cho ra `ALPHA` rồi `BETA`, đó vẫn **không phải hợp đồng** để production dựa vào.

Việc thêm `@Order(1)` cho **class `AmbiguousBeforeAspect`** chỉ ảnh hưởng precedence của aspect đó **so với aspects khác**, không biến `alpha()` và `beta()` thành một trình tự xác định trong cùng class.

## 9. Phương án 1: gộp thành một advice và gọi helper tuần tự

Nếu hai bước **thuộc cùng một concern** và có dependency rõ ràng “phải kiểm tra trước khi audit”, giải pháp đơn giản nhất là một advice:

~~~java
@Aspect
@Component
public class CombinedChecksAspect {

    @Before("execution(* demo.JobApi.run(..))")
    public void validateThenAudit() {
        validate();
        audit();
    }

    private void validate() {
        System.out.println("VALIDATE");
    }

    private void audit() {
        System.out.println("AUDIT");
    }
}
~~~

Đây là **fragment class** cần imports `@Aspect`, `@Component` và `@Before`. Nó không tạo hai advice cùng loại nữa; có **một `@Before`** và hai Java helper calls theo thứ tự **được Java language semantics bảo đảm**:

~~~text
VALIDATE
AUDIT
TARGET run ...
~~~

Nếu `validate()` ném exception, `audit()` không chạy và target có thể bị ngăn, phù hợp với chính sách “không audit một tác vụ bị chặn”, nếu đó thật sự là yêu cầu.

Nhưng nếu audit **phải ghi cả những lần validation thất bại**, bạn cần thiết kế `try/catch/finally` theo ý định nghiệp vụ; **không** chỉ dựa vào thứ tự hay loại advice để tự có behavior mong muốn.

**Ưu điểm gộp:** ít cấu hình order, logic nội bộ dễ đọc, dễ unit test với helper/dependency injection.

**Đánh đổi:** hai trách nhiệm nếu thuộc hai module độc lập có thể trở nên gắn chặt trong một aspect; khi đó tách thành aspects khác nhau có thể hợp lý hơn.

## 10. Phương án 2: tách aspects để có thứ tự concern rõ ràng

Khi validation và audit là hai concerns độc lập:

~~~text
ValidationAspect @Order(10)
           |
           v
AuditAspect @Order(20)
           |
           v
Business service
~~~

Đây chính là mô hình project ở mục 7. Mỗi aspect có mục tiêu, test và owner riêng; Spring sử dụng precedence giữa aspects.

Mẫu khai báo:

~~~java
@Aspect
@Component
@Order(10)
class ValidationAspect {
    @Before("execution(* demo.JobApi.run(..))")
    public void check() {
        // validation
    }
}

@Aspect
@Component
@Order(20)
class AuditAspect {
    @Before("execution(* demo.JobApi.run(..))")
    public void record() {
        // audit
    }
}
~~~

Đây là **fragment minh họa gồm hai class package-private**, cần imports và khai báo trong các file phù hợp. Không nên chép thêm vào project mục 7 khi chưa loại original aspects, vì nó tạo thêm advisors.

Dùng **giá trị order khác nhau** để thể hiện ranh giới. Nếu công việc yêu cầu security phải chạy trước một action có side effect, hãy kiểm tra cả **advisor ordering với Spring Security/transaction/cache aspects**, không chỉ so hai aspects tự viết.

**Lưu ý lớn:** `@Order` có precedence trên Spring aspects nhưng không định nghĩa trọn vẹn thứ tự của mọi BeanPostProcessor, servlet filter, method security interceptor hoặc transactional resource lifecycle. Mỗi infrastructure layer có cách tích hợp riêng; cần integration test trên context thật.

## 11. Phương án 3: một @Around thực hiện nhiều bước có chủ đích

Khi yêu cầu vừa kiểm soát thứ tự “trước” và “sau”, một `@Around` có thể chứa trình tự rõ ràng:

~~~java
@Around("execution(* demo.JobApi.run(..))")
public Object trace(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("STEP-1 validate");
    System.out.println("STEP-2 start timer");

    try {
        Object result = pjp.proceed();
        System.out.println("STEP-3 success audit");
        return result;
    } finally {
        System.out.println("STEP-4 finish");
    }
}
~~~

Đây là **fragment method trong `@Aspect`**, cần imports. Khi target trả bình thường, thứ tự logic do **Java statements** bảo đảm:

~~~text
STEP-1 validate
STEP-2 start timer
TARGET ...
STEP-3 success audit
STEP-4 finish
~~~

Nếu `pjp.proceed()` ném exception, `STEP-3 success audit` không chạy nhưng `finally` vẫn thực thi.

Cần cẩn trọng:

- `@Around` có thể short-circuit nếu không gọi proceed, khiến target và advisor phía trong bị bỏ qua.
- `proceed()` có thể được gọi nhiều lần, làm target/side effects lặp lại.
- Advice trả `Object` phải chuyển tiếp đúng return contract.
- Không dùng `@Around` chỉ để ép thứ tự khi một `@Before` duy nhất đã đủ.

Gộp nhiều bước vào một method là một cách diễn đạt yêu cầu **trình tự cứng**, nhưng làm logic AOP phức tạp hơn khi nó vừa ghi log, vừa cache, vừa retry, vừa quản lý transaction.

## 12. @After, @AfterReturning, @AfterThrowing và thứ tự cần hiểu

Nếu hai advice **cùng loại** trong cùng `@Aspect`, ví dụ:

~~~java
@AfterReturning("execution(* demo.JobApi.run(..))")
public void recordSuccess() {
    System.out.println("SUCCESS");
}

@AfterReturning("execution(* demo.JobApi.run(..))")
public void updateMetric() {
    System.out.println("METRIC");
}
~~~

thì **SUCCESS trước METRIC** không phải bảo đảm. Nếu metric phải được ghi sau audit thành công, gộp thành một `@AfterReturning` hoặc tách thành aspects có `@Order`.

Với hai `@AfterThrowing` cùng match một join point, nguyên tắc tương tự: **thứ tự giữa chúng trong cùng aspect không xác định**. Không dùng thứ tự source method để giả định handler nào “xử lý exception trước”.

Với hai `@After` trong cùng aspect, cũng **không xác định thứ tự** giữa hai method advice `@After` ấy.

**Khác loại** có semantics riêng:

- `@AfterReturning`: chạy khi method execution hoàn tất **bình thường**.
- `@AfterThrowing`: chạy khi invocation ném exception phù hợp; không nhất thiết chạy nếu exception bị `@Around` phía trong bắt và chuyển thành giá trị bình thường.
- `@After`: kiểu finally, chạy sau execution khi invocation đã được advice đi tới, **bất kể hoàn tất bình thường hay ngoại lệ**, ngoại trừ các đường gọi bị advice ngoài short-circuit trước khi nó tham gia.
- `@Around`: tự quyết định có gọi `proceed()` và có xử lý/biến đổi kết quả/exception hay không.

**Không nên viết một bài test assert một danh sách log toàn cục cố định** nếu context có nhiều aspects cùng loại không đặt order. Thứ tự khác loại có quy tắc nhưng thứ tự các concerns và exception flow phải được xét theo advisor chain thực.

## 13. Tại sao @After thường in sau @AfterReturning?

Tài liệu Spring Framework 6.2 phân biệt **precedence assignment** với **thời điểm callback**. `@After` có **after finally semantics**, do đó trong cùng aspect nó **thực tế chạy sau** `@AfterReturning` hoặc `@AfterThrowing` của invocation tương ứng.

Nếu đọc thứ tự precedence từ cao đến thấp:

~~~text
@Around > @Before > @After > @AfterReturning > @AfterThrowing
~~~

rồi vẽ ra:

~~~text
TARGET
@After
@AfterReturning
~~~

thì đó là cách diễn giải **không đúng** thứ tự callback trong Spring cho trường hợp cùng aspect. Precedence quy định interceptor lồng nhau; `@After` chạy ở nhánh finally khi continuation trong phạm vi của nó kết thúc.

Với câu hỏi của bài này, cần nhớ hai kết luận riêng:

1. **Các loại advice khác nhau:** Spring có quy tắc precedence/semantics.
2. **Nhiều advice cùng loại trong cùng aspect:** **không có bảo đảm thứ tự**.

Không dùng quy tắc loại advice để giải quyết hai `@Before` hoặc hai `@After` có cùng type.

## 14. Trường hợp hai aspects không khai báo @Order

Ví dụ hai aspects đều không khai báo order:

~~~java
@Aspect
@Component
class LoggingAspect {
    @Before("execution(* demo.JobApi.run(..))")
    public void before() {
        System.out.println("LOGGING");
    }
}

@Aspect
@Component
class TrackingAspect {
    @Before("execution(* demo.JobApi.run(..))")
    public void before() {
        System.out.println("TRACKING");
    }
}
~~~

Đây là fragment gồm **hai top-level class riêng**; cần imports và Spring config. Hai aspects khác nhau vẫn có thể **không có thứ tự được bảo đảm** nếu không gán precedence.

Không đủ để nói “Spring scan `LoggingAspect` trước nên LOGGING chắc chạy trước”. Component scanning hay alphabet của class names **không phải một hợp đồng ordering giữa các aspects**.

Thay bằng:

~~~java
@Aspect
@Component
@Order(10)
class LoggingAspect {
    // ...
}

@Aspect
@Component
@Order(20)
class TrackingAspect {
    // ...
}
~~~

Đây là **mẫu khai báo**, cần thêm advice bodies từ đoạn trước nếu áp dụng. `@Order(10)` được ưu tiên cao hơn `@Order(20)` trên đường vào.

## 15. Nếu hai aspects có cùng giá trị @Order

Một cấu hình dễ gây hiểu nhầm:

~~~java
@Aspect
@Component
@Order(100)
class AspectA {
    // ...
}

@Aspect
@Component
@Order(100)
class AspectB {
    // ...
}
~~~

Hai aspects được đặt **cùng priority 100**. Đừng đọc con số ấy như một số thứ tự duy nhất. Khi có tie, không dựa vào thứ tự kết quả tình cờ để đưa ra yêu cầu business “A trước B”.

Muốn một contract rõ ràng:

~~~java
@Aspect
@Component
@Order(100)
class AspectA {
    // ...
}

@Aspect
@Component
@Order(200)
class AspectB {
    // ...
}
~~~

Class AspectA có precedence cao hơn AspectB. Các con số có thể chọn theo convention của module; không nhất thiết phải là 1 và 2, miễn quan hệ so sánh rõ ràng.

Trong hệ thống lớn, tránh dùng số order rải rác ở nhiều modules mà không có một tài liệu quy ước. Có thể tạo constants như `SECURITY_ORDER` và `AUDIT_ORDER` nếu muốn thống nhất, nhưng phải hiểu advisors từ Spring framework/starter khác cũng có thứ tự riêng.

## 16. Khi advice trước có ngoại lệ hoặc around short-circuit

Thứ tự được bảo đảm **không đồng nghĩa tất cả advice nhất định phải chạy**.

### 16.1. @Before ưu tiên cao ném exception

~~~text
@Order(10) @Before: kiểm tra quyền -> throw AccessDenied
@Order(20) @Before: ghi dấu sự kiện
TARGET: xử lý nghiệp vụ
~~~

Nếu advice ưu tiên cao ném exception, chain có thể dừng trước advice ưu tiên thấp và target. Không viết test bắt buộc `@Order(20)` chạy ở tình huống exception chặn sớm.

### 16.2. @Around ưu tiên cao bỏ qua proceed

~~~java
@Around("execution(* demo.JobApi.run(..))")
public Object gate(ProceedingJoinPoint pjp) throws Throwable {
    if (maintenanceMode) {
        return "maintenance";
    }
    return pjp.proceed();
}
~~~

Đây là **fragment** cần `maintenanceMode` và imports thích hợp. Nếu `@Around` ở phía ngoài trả về trước mà **không gọi `proceed()`**, các advice nằm phía trong cùng target có thể **không được kích hoạt**.

### 16.3. Hai @Around cùng loại trong một @Aspect

Nếu cùng một class có hai `@Around` match cùng operation mà một method đôi khi return sớm, thứ tự chưa được bảo đảm có thể ảnh hưởng cực lớn: method nào được bọc ngoài quyết định code nào có cơ hội chạy.

Trường hợp này **đặc biệt nên** gộp thành một around advice hoặc tách thành aspects có order rõ ràng và integration tests. Không coi hai `@Around` là “hai handlers độc lập chắc chắn đều sẽ được gọi”.

## 17. Sự khác biệt giữa @Order và @Priority/Ordered

Trong Spring AOP advice ordering:

- `@Order(value)`: cách phổ biến gán thứ tự cho **class aspect** như demo.
- `Ordered`: aspect có thể implement `org.springframework.core.Ordered` và cung cấp `getOrder()`.
- **Giá trị số càng nhỏ → precedence càng cao**.
- Khi advice nằm trong **cùng class aspect và cùng loại**, thứ tự giữa chúng vẫn **không xác định**.
- Một bean có thể chịu nhiều loại ordering trong Spring context (BeanPostProcessor, event listener, transaction interceptor…). Không suy luận tất cả cùng một loại contract.

Ví dụ skeleton:

~~~java
@Aspect
@Component
public class OrderedAspect implements Ordered {

    @Override
    public int getOrder() {
        return 10;
    }

    @Before("execution(* demo.JobApi.run(..))")
    public void before() {
        System.out.println("ORDERED ASPECT");
    }
}
~~~

Đây là **fragment class**, cần import `org.springframework.core.Ordered` và các annotations. `Ordered` là cách thay thế `@Order` trên aspect trong ví dụ; không nên đồng thời sao chép thêm vào project runnable nếu muốn giữ expected stdout.

Nếu yêu cầu order dựa vào runtime config, implement `Ordered` có thể hữu dụng. Nhưng dynamic ordering phức tạp dễ khó debug; dùng constants hoặc `@Order` tường minh thường đủ.

## 18. XML schema AOP có quy tắc riêng

Bài này tập trung vào `@Aspect` annotation style, nhưng cần tránh nhầm với Spring **schema-based AOP**:

~~~xml
<aop:aspect id="example" ref="exampleBean">
    <aop:before pointcut-ref="serviceOp" method="one"/>
    <aop:before pointcut-ref="serviceOp" method="two"/>
</aop:aspect>
~~~

Đây chỉ là **XML fragment**, chưa phải file Spring XML hợp lệ đầy đủ. Tài liệu Spring ghi nhận rằng **trong cùng một `<aop:aspect>` của schema-based AOP**, precedence được xác định theo **thứ tự các phần tử advice được khai báo**. Quy tắc này **khác** annotation-style `@Aspect` dùng Java reflection.

**Không lấy thứ tự XML elements để chứng minh thứ tự hai annotated Java methods.** Nếu đang dùng `@Aspect` trên Spring bean, hãy áp dụng quy tắc ở các phần trước. Nếu đang dùng schema-based AOP, xem đúng phần tài liệu dành cho XML configuration.

## 19. Kiểm thử Spring AOP ordering đúng cách

Một bộ test đáng tin nên phân biệt:

- **Trường hợp có order được định nghĩa:** test output order cụ thể.
- **Trường hợp order không được định nghĩa:** không hardcode một thứ tự tình cờ; test rằng cả hai advices **đã chạy** khi được phép chạy, nhưng đừng kiểm tra vị trí tương đối giữa chúng.
- **Trường hợp không có proceed/exception:** test rằng chỉ các advice trong đường điều khiển được thực thi.
- **Trường hợp nhiều advisors:** kiểm tra cả application context thực tế, không chỉ một aspect unit test isolated.

### 19.1. Test project mục 7

Giả sử lấy biến `jobs` từ `ApplicationContext`:

~~~java
String result = jobs.run("T-1");

if (!"done:T-1".equals(result)) {
    throw new AssertionError("Wrong result");
}
~~~

Đoạn này là **fragment kiểm tra kết quả**, không phải một class độc lập. Để assert thứ tự `BEFORE priority10` và `BEFORE priority20`, tốt hơn là inject một `TraceRecorder` vào hai aspects, lưu events và so thứ tự trong integration test, thay vì dựa vào console log dễ chịu ảnh hưởng logging từ hệ thống khác.

### 19.2. TraceRecorder cho integration test

Một recorder tối giản:

~~~java
@Component
public class TraceRecorder {
    private final List<String> events = new CopyOnWriteArrayList<>();

    public void record(String event) {
        events.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }
}
~~~

Đây là **fragment class**, cần imports `java.util.List`, `java.util.concurrent.CopyOnWriteArrayList` và `org.springframework.stereotype.Component`. Nó có thể được hai aspects inject qua constructor và ghi events.

Sau một lời gọi hợp lệ, một test có thể assert:

~~~java
List<String> expected = List.of(
        "priority10",
        "priority20",
        "target"
);

if (!expected.equals(recorder.snapshot())) {
    throw new AssertionError("Incorrect advice order");
}
~~~

Lưu ý: để đoạn này có ý nghĩa, target cũng phải được sửa để record event `target`. Nếu project thực có những advisors khác chặn exception hoặc ghi thêm events, kỳ vọng phải được điều chỉnh theo **chain thực**. Những fragments này minh họa kỹ thuật kiểm thử chứ **không phải bản source chạy độc lập** đã được tạo ở mục 7.

### 19.3. Không nên test undefined order bằng một giá trị cố định

Nếu chỉ có `AmbiguousBeforeAspect` với hai advices `alpha` và `beta` cùng loại, một test như sau là sai về giả định:

~~~java
assertEquals(
    List.of("ALPHA", "BETA"),
    recorder.snapshot()
);
~~~

Đây là **fragment JUnit**, cần JUnit dependency và recorder trong test. Nó có thể pass trên máy hiện tại nhưng fail ở môi trường khác dù Spring AOP vẫn đúng contract. Nếu chưa có order, chỉ test sự **hiện diện** của cả hai events (nếu chúng thực thi bình thường), không yêu cầu thứ tự.

## 20. Các lỗi thường gặp

| Hiện tượng | Nguyên nhân thường gặp | Hướng giải quyết |
| --- | --- | --- |
| Hai `@Before` đổi thứ tự sau build khác | Cùng loại trong cùng `@Aspect` không có thứ tự bảo đảm | Gộp advice hoặc tách aspects có `@Order` |
| `@Order` trên class nhưng hai method cùng loại vẫn đảo | `@Order` không xếp thứ tự nội bộ cùng loại | Một advice với helper calls tuần tự |
| Hai aspects chạy “theo alphabet” trên dev nhưng khác ở CI | Chưa gán precedence giữa aspects | `@Order` khác nhau |
| Hai aspects đều `@Order(5)` | Tie precedence, không thể giả định thứ tự | Dùng giá trị khác nhau |
| `@After` được mong chạy trước `@AfterReturning` | Hiểu sai precedence thành callback order | Nhớ `@After` có finally semantics |
| Aspect order thấp hơn lại chạy trước ở đường ra | Precedence cao bọc ngoài nên thoát sau | Vẽ interceptor nesting |
| Advice ưu tiên thấp không chạy | Advice ngoài ném exception hoặc không proceed | Kiểm tra exception/continuation |
| Target không chạy dù @Before matched | `@Around` ở ngoài đã short-circuit | Kiểm tra code around và order |
| Method private/final không được advice | Giới hạn Spring proxy | Thiết kế method interceptable |
| Advice không chạy trong `this.method()` | Self-invocation bypass proxy | Gọi qua Spring-managed dependency |
| Có `@Aspect` nhưng không có advice | Aspect chưa là Spring bean hoặc AOP chưa bật | `@Component`/`@Bean` và `@EnableAspectJAutoProxy` |
| Cùng một business operation in nhiều log | Nhiều advisors cùng pointcut hoặc method thực sự gọi nhiều lần | Quan sát advisor/pointcut và call path |
| Unit test ordering pass còn integration test fail | Context thật có additional advisors và ordering | Test với Spring context đúng production |
| Copy thứ tự XML vào annotation style | Schema-based AOP có quy tắc khác | Áp dụng tài liệu đúng cấu hình |
| Hai `@Around` trong một aspect làm side effect khó hiểu | Nested order không bảo đảm và proceed có thể bỏ qua/lặp | Gộp thành một around hoặc split + @Order |
| Không biết security hay transaction chạy trước | Các infrastructure advisors có order riêng | Kiểm chứng precedence và integration behavior |
| Thay JDK proxy sang CGLIB để “sửa thứ tự” | Loại proxy không tạo source-declaration order | Sửa thiết kế aspect/advice, không đổi proxy chỉ vì ordering |

## 21. Quy trình quyết định khi nhiều advice cùng match

**Bước 1 — Xác định join point.** Các pointcuts có thật sự cùng chọn **một Spring-managed method execution** không? Nếu không, không có vấn đề ordering tại cùng invocation.

**Bước 2 — Xác định loại advice.** Hai `@Before` hay `@AfterReturning` là cùng loại; `@Before` và `@Around` là khác loại.

**Bước 3 — Xác định nơi khai báo.** Cùng class `@Aspect`, hay các class aspects khác nhau?

**Bước 4 — Xem yêu cầu business.** Nếu thứ tự chỉ mang tính thẩm mỹ logging và không có dependency, có thể không cần ép order. Nếu audit/security/validation/retry/cache phụ thuộc kết quả của nhau, thứ tự phải tường minh.

**Bước 5 — Chọn cách sửa.**

- Hai thao tác phụ thuộc nhau trong **một concern** → **một advice** với các bước Java theo thứ tự.
- Hai concerns độc lập → **tách aspects** và cấp precedence qua `@Order` hoặc `Ordered`.
- Có nhiều advice types → hiểu Spring precedence và actual callback semantics, nhất là `@After`.
- Có third-party advisors → xem configuration/order của chúng; đừng chỉ đặt order trên aspects riêng mà bỏ qua framework infrastructure.

**Bước 6 — Dùng integration test.** Assert những thứ tự **được bảo đảm**, và test nhánh exception, retry, short-circuit.

**Bước 7 — Tài liệu hóa convention.** Giữ một thứ tự ưu tiên chung cho security, validation, audit, transaction, tracing nếu business yêu cầu; tránh các values trùng không có chủ đích.

## 22. Checklist trước khi đưa vào production

1. Có nhiều advice cùng type và cùng match một join point không?
2. Chúng có nằm trong cùng `@Aspect` class không?
3. Nếu có, đã tránh dựa vào thứ tự Java source/reflection chưa?
4. Nếu logic cần thứ tự, đã gộp trong một advice hoặc tách aspects chưa?
5. Nếu tách aspects, mỗi aspect có `@Order`/`Ordered` riêng, không tie không?
6. Đã hiểu **giá trị order nhỏ hơn là precedence cao hơn**?
7. Có kiểm tra thứ tự trên đường vào và đường ra thay vì chỉ một chiều không?
8. Với `@After` và `@AfterReturning`, đã phân biệt finally semantics chưa?
9. Nếu `@Around` bỏ proceed hoặc throw, những advice phía trong có bị bỏ qua không?
10. Có tự gọi `this.method()` bỏ qua Spring proxy không?
11. Có advisor khác của Spring Security, transaction hoặc cache tham gia context không?
12. Có test bằng Spring ApplicationContext đầy đủ và actual proxy type không?
13. Có test nhánh thành công và ngoại lệ, không chỉ một happy path?
14. Có bị nhầm schema-based XML AOP ordering với annotation-based `@Aspect` không?
15. README/test có tránh khẳng định một stdout cố định cho các advice cùng loại **không được sắp thứ tự** không?

## 23. Câu hỏi tự đánh giá

**1. Hai `@Before` trong cùng class `@Aspect` có chắc chạy theo vị trí khai báo không?**

Không. Nếu cả hai cùng match một join point, Spring Framework không định nghĩa thứ tự giữa chúng.

**2. Một class `@Aspect` có `@Order(1)` thì hai `@Before` trong class ấy được sắp thứ tự nội bộ không?**

Không. `@Order` đặt precedence cấp aspect so với những aspects khác; nó không biến thứ tự hai methods cùng loại thành một contract.

**3. Cách đơn giản nhất khi phải validate rồi audit theo thứ tự cứng là gì?**

Gộp thành một advice và gọi `validate()` rồi `audit()` theo thứ tự Java, nếu đó thực sự là một concern phù hợp.

**4. Khi nên tách aspects và dùng @Order?**

Khi hai concerns tương đối độc lập nhưng có precedence rõ ràng giữa chúng.

**5. Giữa `@Order(10)` và `@Order(20)`, aspect nào ưu tiên cao hơn?**

`@Order(10)`, vì trong Spring giá trị order nhỏ hơn có precedence cao hơn.

**6. Trên đường ra của hai `@Around` nested, aspect ưu tiên cao hoàn tất trước hay sau?**

Thường hoàn tất **sau** aspect ưu tiên thấp hơn, vì nó bọc ngoài, miễn cả hai gọi proceed và không thay đổi flow theo cách khác.

**7. `@After` trong cùng aspect có chạy trước `@AfterReturning` chỉ vì precedence list viết @After trước không?**

Không. `@After` có finally semantics và thực tế được gọi sau `@AfterReturning`/`@AfterThrowing` khi những advice đó áp dụng.

**8. Nếu hai aspects không có `@Order`, Spring cam kết thứ tự nào?**

Không có thứ tự được bảo đảm, trừ khi precedence được cấu hình qua cơ chế khác.

**9. Hai aspects cùng `@Order(100)` có bảo đảm theo tên class không?**

Không. Nếu thứ tự có ý nghĩa, phải dùng precedence riêng hoặc gộp logic phù hợp.

**10. Nếu around advice ưu tiên cao không gọi `proceed()`, before advice ở phía trong có được chạy không?**

Không được continuation đi tới, vì around đã short-circuit trước khi gọi vào chain phía trong.

**11. Vì sao chạy chương trình 1.000 lần thấy cùng order vẫn không đủ?**

Vì behavior có thể ổn định một cách tình cờ nhưng không được Spring API/tài liệu bảo đảm, nên không thể coi là contract qua môi trường khác.

**12. Schema-based AOP có cùng quy tắc thứ tự advice method annotation style không?**

Không hoàn toàn. Với `<aop:aspect>` XML, thứ tự khai báo các advice elements có vai trò trong precedence; quy tắc này không áp dụng nguyên xi cho Java `@Aspect`.

## 24. Tài liệu Spring chính thức

- **Spring Framework 6.2 — Declaring Advice / Advice Ordering:** https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html
- **Spring Framework — Declaring an Aspect:** https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html
- **Spring Framework — @AspectJ-style AOP Support:** https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- **Spring Framework — Proxying Mechanisms:** https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- **Spring Framework 6.2 — Schema-based AOP / Advice Ordering:** https://docs.spring.io/spring-framework/reference/6.2/core/aop/schema.html
- **Spring Framework — Order annotation:** https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/annotation/Order.html
- **Spring Framework — Ordered interface:** https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/Ordered.html

**Kết luận:** Trong Spring `@AspectJ`-style AOP, **nhiều advice cùng loại trong một `@Aspect` áp dụng cho cùng method execution không có thứ tự được bảo đảm**. Để tạo trình tự nghiệp vụ đáng tin cậy, **gộp các bước vào một advice**, hoặc **tách thành nhiều aspects và chỉ định precedence bằng `@Order`/`Ordered`**. Khi nhiều loại advice cùng tham gia, hãy xét **đường vào/đường ra, finally semantics và `proceed()`**, thay vì coi thứ tự source code là quy tắc của Spring.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-ordering-007">@After như finally so với @AfterReturning và @AfterThrowing</a>

<details>
<summary>Xem chi tiết</summary>

# `@After` như `finally` so với `@AfterReturning` và `@AfterThrowing` trong Spring AOP

## 1. Phân biệt đúng ba loại advice

Trong **Spring Framework AOP**, một method của Spring bean có thể kết thúc theo hai hướng:

1. **Normal return:** phương thức trả giá trị hoặc hoàn thành bình thường đối với method `void`.
2. **Exceptional exit:** phương thức thoát bằng cách ném một `Throwable` ra khỏi ranh giới method execution mà Spring AOP đang intercept.

Ba annotation dễ bị nhầm vì đều chứa chữ “After”:

- **`@AfterReturning`:** chỉ chạy khi method execution được chọn **kết thúc bình thường**. Có thể bind return value bằng `returning`.
- **`@AfterThrowing`:** chỉ chạy khi method execution được chọn **thoát bằng exception**. Có thể bind exception bằng `throwing` và dùng kiểu tham số để lọc exception.
- **`@After`:** chạy khi method execution được chọn **kết thúc theo cả hai hướng**; trong Spring AOP, nó là **after (finally) advice**, tương tự một khối `finally`.

Vì vậy, nếu mục tiêu là “luôn giải phóng tài nguyên hoặc ghi nhận đã kết thúc một method”, `@After` thích hợp. Nếu mục tiêu là “chỉ ghi kết quả thành công” hoặc “chỉ ghi thông tin lỗi”, hai loại còn lại rõ ý định hơn.

**Giới hạn của bài:** nội dung tập trung vào **Spring Aspect, thứ tự Spring AOP advice và phương thức gọi qua Spring proxy**, không trình bày lại kiến thức AOP tổng quát hoặc AspectJ compile-time weaving.

## 2. Bảng sự kiện — loại nào thực sự chạy?

Giả sử cả ba advice dùng **cùng một pointcut**, không tự ném exception và method được gọi từ client **qua Spring AOP proxy**:

| Kết quả method | `@AfterReturning` | `@AfterThrowing` | `@After` |
| --- | --- | --- | --- |
| `return "OK"` | Chạy | Không chạy | Chạy |
| `return null` | Chạy nếu binding tương thích | Không chạy | Chạy |
| Method `void` hoàn thành | Chạy nếu không có binding/kiểu hạn chế loại bỏ | Không chạy | Chạy |
| `throw IllegalArgumentException` | Không chạy | Chạy nếu kiểu exception match | Chạy |
| `throw IOException` | Không chạy | Chạy nếu kiểu exception match | Chạy |
| Method bắt lỗi bên trong rồi `return "RECOVERED"` | Chạy | Không chạy | Chạy |
| Method trả một object báo lỗi, nhưng không `throw` | Chạy | Không chạy | Chạy |
| Method chưa được gọi qua proxy, hoặc pointcut không match | Không có advice cho lời gọi đó | Không có advice cho lời gọi đó | Không có advice cho lời gọi đó |

**Điều kiện ở dòng cuối là tối quan trọng:** `@After` không phải cơ chế Java tự động chen vào bất cứ method nào. Spring AOP tạo **proxy/advisor chain**; nếu method không đi qua proxy được advice, không loại nào được kích hoạt bởi lời gọi đó.

### Minh họa đơn giản theo nhánh

```text
                      Client gọi proxy.method()
                                  |
                                  v
                           Spring AOP proxy
                                  |
                                  v
                           Target.method()
                             /         \
                            /           \
                     NORMAL RETURN    THROWS ERROR
                           |              |
                           v              v
                  @AfterReturning    @AfterThrowing
                           |              |
                           +-------+------+
                                   |
                                   v
                            @After (finally)
                                   |
                                   v
                        Client nhận value/error
```

Sơ đồ mô tả thứ tự **các advice cùng một `@Aspect` trong Spring hiện đại**, với pointcut và điều kiện binding đều phù hợp. Khi có nhiều aspects, around advice hay advisor hạ tầng xen vào, cần xét thêm **thứ tự thực sự của interceptor chain**; không được dùng hình này như cam kết mọi advice ở mọi aspect luôn chạy theo thứ tự tuyệt đối.

## 3. `@After` thật sự giống `finally` ở điểm nào?

Spring document gọi `@After` là **after finally advice**. Một ví dụ:

```java
@Aspect
public class CompletionAspect {

    @After("execution(* com.example.app.service..*.*(..))")
    public void completed(JoinPoint jp) {
        System.out.println("COMPLETED " + jp.getSignature().getName());
    }
}
```

Đây là **snippet cần được đăng ký thành Spring `@Aspect` bean**; `JoinPoint` đến từ `org.aspectj.lang.JoinPoint`, `@After` từ `org.aspectj.lang.annotation.After`.

Dòng `COMPLETED` xuất hiện khi một method execution phù hợp:

- Trả bình thường, kể cả khi return value là `null`.
- Kết thúc do unchecked exception.
- Kết thúc do checked exception được propagate theo contract của Java/proxy.
- Bắt một exception nội bộ và vẫn trả bình thường: chỉ thấy **normal completion** của method, nhưng `@After` vẫn chạy vì đó cũng là một kiểu kết thúc.

### So với Java `finally`

Một Java method có thể dùng:

```java
try {
    // business operation
} finally {
    // dọn dẹp trong chính method
}
```

Còn Spring `@After` là logic **ngoài method target**, được kích hoạt bởi proxy khi pointcut match. Hai thứ giống nhau ở ý nghĩa “chạy sau khi phần thực thi liên quan hoàn tất theo bất kỳ hướng thoát nào”, nhưng **không giống nhau về cơ chế**:

| Đặc điểm | Java `finally` | Spring AOP `@After` |
| --- | --- | --- |
| Nằm ở đâu? | Trong mã Java của khối `try/finally` | Trong Spring `@Aspect` bean |
| Có cần Spring proxy? | Không | Có (trong proxy-based Spring AOP) |
| Áp dụng cho method được chọn bằng pointcut? | Không phải mục đích của syntax | Có |
| Tự chạy khi `new Target().method()`? | Nếu code target có `finally` thì có | Không, nếu đối tượng không đi qua Spring AOP proxy |
| Tự chạy khi target gọi `this.inner()`? | Theo control flow của mã trong target | Advice trên `inner()` không được kích hoạt thêm nếu self-invocation bypass proxy |
| Có bind return value qua `returning`? | Không theo annotation này | Không; dùng `@AfterReturning` |
| Có bind thrown exception qua `throwing`? | Không theo annotation này | Không; dùng `@AfterThrowing` |

**Quan trọng:** `@After` không có attribute `returning` hay `throwing` giống hai advice chuyên dụng. Nếu cần phân biệt thành công/thất bại và dữ liệu tương ứng, hãy chọn đúng advice hoặc `@Around`.

### Chọn đúng mục đích

- **Release resource / “method đã kết thúc” audit:** `@After`.
- **Chỉ đếm success / quan sát return value:** `@AfterReturning`.
- **Chỉ đếm failure / quan sát thrown exception:** `@AfterThrowing`.
- **Cần điều khiển execution, catch để trả fallback, thay return value:** `@Around`.

Nên chọn loại advice ít quyền lực nhất đủ để giải quyết nhu cầu, nhằm tránh vô tình thay đổi flow của business method.

## 4. `@AfterReturning`: chỉ chạy khi return bình thường

Khai báo:

```java
@AfterReturning(
    pointcut = "execution(* com.example.app.service..*.confirm(..))",
    returning = "result",
    argNames = "result"
)
public void successful(Object result) {
    System.out.println("SUCCESS result=" + result);
}
```

Đây là **method mẫu trong `@Aspect` bean**.

- `returning = "result"` là **tên binding parameter** của advice method, không phải tên trường hay tên method của target.
- `argNames = "result"` giúp Spring giải quyết binding name rõ ràng trong trường hợp compiler không giữ parameter names.
- `Object result` nhận reference của return value nếu kiểu phù hợp. Đặt kiểu cụ thể hơn có thể giới hạn methods/value được match.
- Return value được bind cho advice **không phải điểm để thay object trả cho client**. Muốn thay kết quả, dùng `@Around`.

Ví dụ target:

```java
public String confirm(String mode) {
    if ("bad".equals(mode)) {
        throw new IllegalArgumentException("invalid");
    }
    return "OK:" + mode;
}
```

Khi `confirm("good")` trả `OK:good`, advice có thể ghi `SUCCESS result=OK:good`. Nhưng với `confirm("bad")`, advice này không chạy.

### “Thành công” ở đây là thành công theo Java method exit

Nếu method trả `"DECLINED"` hay một `Result.failure(...)` nhưng không `throw`, Spring vẫn coi là **normal return** và `@AfterReturning` có thể chạy. Điều này không có nghĩa giao dịch kinh doanh thực tế thành công.

Muốn phân biệt status nghiệp vụ trong return object, kiểm tra dữ liệu trả về trong advice phù hợp và đặt tên metric rõ ràng, ví dụ `method.completedNormally` thay vì mặc định dùng `business.success`.

## 5. `@AfterThrowing`: chỉ chạy khi exception thoát khỏi method

Khai báo:

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app.service..*.confirm(..))",
    throwing = "failure",
    argNames = "failure"
)
public void failed(IllegalArgumentException failure) {
    System.out.println(
        "FAILURE " + failure.getClass().getSimpleName()
            + ":" + failure.getMessage()
    );
}
```

Đây là **method mẫu**, không phải class độc lập.

Điểm cần nhớ:

1. `throwing="failure"` ràng buộc throwable vào tham số Java tên `failure`.
2. **Kiểu tham số** `IllegalArgumentException` còn là điều kiện filter. Nếu method thoát bằng `IllegalStateException`, advice này không chạy.
3. Nếu muốn bao phủ mọi subtype của `Exception`, dùng `Exception`. Nếu muốn bao phủ mọi subtype của `Throwable` (kể cả `Error`), dùng `Throwable` — nhưng cần cân nhắc nội dung log cho lỗi nghiêm trọng.
4. Lời gọi target tự catch lỗi rồi trả bình thường **không kích hoạt** advice này.
5. Trong mẫu thông thường, advice này **không nuốt exception**; caller vẫn nhận exception gốc sau khi advice thực thi nếu advice không tự làm thay đổi flow.

### Không dùng `@AfterThrowing` làm catch/recovery thay cho caller

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app.service..*.confirm(..))",
    throwing = "failure"
)
public void pretendRecovery(RuntimeException failure) {
    System.out.println("RECOVER?");
}
```

Đây là **phản ví dụ về kỳ vọng sai**. Log `RECOVER?` không biến exception thành return value. Nếu muốn bắt `IllegalArgumentException` và trả giá trị khác, dùng `@Around` với `try/catch` và `pjp.proceed()`; phải bảo đảm return value tương thích method signature.

**Lưu ý chính thức của Spring:** `@AfterThrowing` không được định nghĩa như một callback bắt mọi exception do mọi advice trong chuỗi tạo ra. Đặc biệt, advice trong cùng `@Aspect` không được suy rằng `@AfterThrowing` sẽ nhận lỗi do `@After` hoặc `@AfterReturning` của chính aspect đó ném ra. Ngữ nghĩa dự kiến là lỗi thoát ra từ **join point (target method execution)**.

## 6. Quan hệ precedence và thứ tự thực thi thực tế

Đây là trọng tâm của bài: **mức ưu tiên advisor** khác với **thứ tự dòng log khi method trở về**.

Theo tài liệu Spring Framework 6.2, với các advice **khác loại trong cùng một `@Aspect`** đang match một join point, độ ưu tiên từ cao đến thấp là:

```text
@Around > @Before > @After > @AfterReturning > @AfterThrowing
```

Tuy nhiên, **`@After` dùng ngữ nghĩa finally**, nên khi execution thoát ra, một `@After` trong cùng aspect **thực tế được gọi sau `@AfterReturning` hoặc `@AfterThrowing`**.

Điều này không mâu thuẫn. Precedence quyết định cách những interceptors được **lồng vào nhau**. Advice mang ý nghĩa `finally` hoàn thành khi phần thực thi nằm **bên trong nó** đã kết thúc:

```text
LỜI GỌI THÀNH CÔNG, cùng một aspect
----------------------------------
@Around enter
  @Before
    TARGET returns normally
    @AfterReturning
  @After (finally)
@Around exit normally
CLIENT receives result

LỜI GỌI THẤT BẠI, cùng một aspect
----------------------------------
@Around enter
  @Before
    TARGET throws
    @AfterThrowing (nếu kiểu exception match)
  @After (finally)
@Around rethrows/handles
CLIENT receives exception or fallback
```

Đây là **mô hình thứ tự từ semantics Spring 6.2** với advisor hợp lệ, không phải bảo đảm tự động về mọi aspect trong một hệ thống có hạ tầng transaction/cache/async và advisor tùy biến.

### Phân biệt ba loại “thứ tự”

| Thuật ngữ | Ý nghĩa | Không được nhầm với |
| --- | --- | --- |
| Precedence giữa **các loại advice cùng aspect** | Thứ tự thiết lập và lồng advisor theo type | Thứ tự method xuất hiện trong file Java |
| Precedence giữa **các aspect** | Được điều khiển bằng `@Order` hoặc `Ordered` | Có chung pointcut nghĩa là tự có thứ tự |
| Thời điểm chạy trên **nhánh return/throw** | Finally logic diễn ra khi phần phía trong hoàn tất | Một advice có precedence cao chắc chắn in log trước trên mọi chiều |

Với nhiều aspects, quy tắc phổ quát là: **aspect có precedence cao hơn chạy trước ở chiều vào và sau ở chiều ra** (đối với những advice tương ứng), giống các tầng interceptor lồng nhau.

### `@Order` dùng thế nào?

```java
@Aspect
@Order(1)
public class OuterAspect {
    // advice bao ngoài
}

@Aspect
@Order(2)
public class InnerAspect {
    // advice nằm phía trong khi cả hai cùng match
}
```

Các class là **ví dụ annotation**, cần Spring-managed aspect beans và imports phù hợp. Giá trị số nhỏ hơn có precedence cao hơn.

Khi `OuterAspect` có `@Around`, phía trong là `InnerAspect` có `@AfterReturning` và `@After`, log có thể diễn ra:

```text
OUTER enter
TARGET
INNER afterReturning
INNER afterFinally
OUTER return
OUTER finally
```

Không nên dự đoán thứ tự giữa hai `@After` trong **cùng aspect** chỉ dựa trên thứ tự khai báo source Java; Spring không bảo đảm reflection giữ declaration order. Nếu cùng loại advice cần thứ tự rõ, gộp chúng hoặc tách thành các aspects khác nhau kèm `@Order`.

## 7. Demo Maven Java 17+: ghi log cả thành công và lỗi

Ví dụ dưới đây là **project Spring Framework 6.2.6 runnable**, không cần Spring Boot. Nó tạo:

- Spring-managed bean `CheckoutService` với implementation `CheckoutServiceImpl`.
- `LifecycleAspect` mang **`@Before`, `@AfterReturning`, `@AfterThrowing` và `@After`** trên cùng method `confirm(..)`.
- `OuterTraceAspect` mang `@Around`, được `@Order(1)` để bao bên ngoài `LifecycleAspect` (`@Order(2)`).
- Bốn đường gọi: success, exception bị bắt ngay trong target, `IllegalArgumentException` thoát khỏi target, và `IllegalStateException` không khớp kiểu của `@AfterThrowing`.
- Trace phân biệt *normal completion*, *exceptional exit* và *finally completion*.

### 7.1. Cấu trúc project

```text
spring-after-advice-order/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── AfterAdviceOrderDemo.java
```

### 7.2. Tệp `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-after-advice-order</artifactId>
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
                    <mainClass>demo.AfterAdviceOrderDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 7.3. Tệp `src/main/java/demo/AfterAdviceOrderDemo.java`

```java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;

public class AfterAdviceOrderDemo {

    public interface CheckoutService {
        String confirm(String mode);
    }

    public static class CheckoutServiceImpl implements CheckoutService {
        @Override
        public String confirm(String mode) {
            System.out.println("TARGET confirm " + mode);

            if ("bad".equals(mode)) {
                throw new IllegalArgumentException("invalid order");
            }
            if ("state".equals(mode)) {
                throw new IllegalStateException("unavailable");
            }
            if ("handled".equals(mode)) {
                try {
                    throw new IllegalArgumentException("internal failure");
                } catch (IllegalArgumentException ignored) {
                    System.out.println("TARGET handled internally");
                    return "RECOVERED";
                }
            }
            return "OK:" + mode;
        }
    }

    @Aspect
    @Order(1)
    public static class OuterTraceAspect {
        @Around("execution(* demo..*.confirm(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            String mode = String.valueOf(pjp.getArgs()[0]);
            System.out.println("OUTER enter " + mode);

            try {
                Object result = pjp.proceed();
                System.out.println("OUTER return " + result);
                return result;
            } catch (Throwable ex) {
                System.out.println(
                    "OUTER throw "
                        + ex.getClass().getSimpleName()
                        + ":" + ex.getMessage()
                );
                throw ex;
            } finally {
                System.out.println("OUTER finally");
            }
        }
    }

    @Aspect
    @Order(2)
    public static class LifecycleAspect {
        @Before("execution(* demo..*.confirm(..))")
        public void before() {
            System.out.println("BEFORE");
        }

        @AfterReturning(
            pointcut = "execution(* demo..*.confirm(..))",
            returning = "result",
            argNames = "result"
        )
        public void returned(String result) {
            System.out.println("AFTER_RETURNING " + result);
        }

        @AfterThrowing(
            pointcut = "execution(* demo..*.confirm(..))",
            throwing = "failure",
            argNames = "failure"
        )
        public void thrown(IllegalArgumentException failure) {
            System.out.println(
                "AFTER_THROWING "
                    + failure.getClass().getSimpleName()
                    + ":" + failure.getMessage()
            );
        }

        @After("execution(* demo..*.confirm(..))")
        public void finished() {
            System.out.println("AFTER_FINALLY");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class Config {
        @Bean
        public CheckoutService checkoutService() {
            return new CheckoutServiceImpl();
        }

        @Bean
        public OuterTraceAspect outerTraceAspect() {
            return new OuterTraceAspect();
        }

        @Bean
        public LifecycleAspect lifecycleAspect() {
            return new LifecycleAspect();
        }
    }

    private static void runCase(CheckoutService service, String mode) {
        System.out.println("=== " + mode + " ===");
        try {
            System.out.println("CLIENT result=" + service.confirm(mode));
        } catch (RuntimeException ex) {
            System.out.println(
                "CLIENT caught "
                    + ex.getClass().getSimpleName()
                    + ":" + ex.getMessage()
            );
        }
    }

    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {
            CheckoutService service = context.getBean(CheckoutService.class);
            System.out.println("AOP=" + AopUtils.isAopProxy(service));

            runCase(service, "good");
            runCase(service, "handled");
            runCase(service, "bad");
            runCase(service, "state");
        }
    }
}
```

Điểm triển khai:

1. `@EnableAspectJAutoProxy(proxyTargetClass=false)` bật Spring AOP trong context thuần. Vì target implement `CheckoutService`, bean có thể được JDK dynamic proxy.
2. Các aspects đều được đăng ký tường minh qua `@Bean`. `@Aspect` **không tự đăng ký Spring bean** nếu không có component scan hoặc factory method.
3. `@Order(1)` khiến `OuterTraceAspect` được ưu tiên hơn `LifecycleAspect @Order(2)` ở chiều vào; nhờ đó outer around advice bao quá trình xử lý phía trong.
4. Trong `LifecycleAspect`, các advice dùng cùng `execution(* demo..*.confirm(..))`. Chỉ những nhánh exit thích hợp được áp dụng cho `@AfterReturning` hoặc `@AfterThrowing`, còn `@After` áp dụng cho cả hai.
5. `@AfterThrowing` nhận riêng `IllegalArgumentException`, nên `IllegalStateException` không kích hoạt advice đó.
6. Cả code kinh doanh lẫn các advice đều in những prefix riêng để quan sát trực tiếp thứ tự.

### 7.4. Chạy chương trình

Trong thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java
```

Các dòng **stdout do chương trình tự in** dựa trên flow và thứ tự aspect được khai báo (không tính log riêng từ Maven/Spring):

```text
AOP=true
=== good ===
OUTER enter good
BEFORE
TARGET confirm good
AFTER_RETURNING OK:good
AFTER_FINALLY
OUTER return OK:good
OUTER finally
CLIENT result=OK:good
=== handled ===
OUTER enter handled
BEFORE
TARGET confirm handled
TARGET handled internally
AFTER_RETURNING RECOVERED
AFTER_FINALLY
OUTER return RECOVERED
OUTER finally
CLIENT result=RECOVERED
=== bad ===
OUTER enter bad
BEFORE
TARGET confirm bad
AFTER_THROWING IllegalArgumentException:invalid order
AFTER_FINALLY
OUTER throw IllegalArgumentException:invalid order
OUTER finally
CLIENT caught IllegalArgumentException:invalid order
=== state ===
OUTER enter state
BEFORE
TARGET confirm state
AFTER_FINALLY
OUTER throw IllegalStateException:unavailable
OUTER finally
CLIENT caught IllegalStateException:unavailable
```

**Ghi chú:** đây là stdout dự kiến từ mã Spring Framework 6.2 trình bày, không phải khẳng định một phiên Maven đã được chạy tại đây. Thư viện logging hoặc phiên bản/configuration khác có thể bổ sung log; ví dụ không hardcode tên lớp proxy runtime.

### 7.5. Phân tích từng tình huống

**Nhánh `good`:** method target trả `OK:good`. `@AfterReturning` nhận giá trị; `@AfterThrowing` bỏ qua; `@After` in `AFTER_FINALLY` **sau** dòng `AFTER_RETURNING`. Sau đó outer around advice mới ghi return và finally, rồi caller nhận result.

**Nhánh `handled`:** method target có ném `IllegalArgumentException` bên trong, nhưng bắt ngay tại `catch` và trả `RECOVERED`. Vì exception **không thoát khỏi method execution**, Spring AOP quan sát một normal return: `@AfterReturning` chạy, `@AfterThrowing` không chạy, và `@After` vẫn chạy.

**Nhánh `bad`:** target ném `IllegalArgumentException` ra ngoài. `@AfterReturning` không chạy; `@AfterThrowing` được bind đúng exception type nên in log; `@After` chạy tiếp theo dù method thất bại. Outer advice bắt chỉ để in rồi **ném lại nguyên lỗi**, nên caller vẫn bắt được.

**Nhánh `state`:** target ném `IllegalStateException`. Pointcut của `@AfterThrowing` match method nhưng **không match exception type**, vì vậy không có dòng `AFTER_THROWING`. Tuy nhiên `@After` vẫn chạy do semantics finally. Caller nhận exception như cũ.

Chính sự khác biệt của hai nhánh lỗi cho thấy: **`@After` không cần biết kiểu exception**, trong khi `@AfterThrowing(IllegalArgumentException)` lọc kiểu ngoài điều kiện pointcut.

## 8. Tại sao `@After` phải thực thi sau `@AfterReturning` / `@AfterThrowing`?

Spring dựng mỗi advice thành advisor/interceptor để sắp vào một **invocation chain**. Với advice của cùng một aspect, Spring 6.2 tài liệu hóa precedence của các type khác nhau. Vì `@After` có semantics giống finally, implementation của nó thực hiện cleanup sau khi phần invocation phía trong hoàn tất — bao gồm cả after-returning/after-throwing phù hợp ở phía trong.

Có thể hình dung như Java pseudocode (không phải mã của Spring):

```java
Object invokeInnerAdviceAndTarget() throws Throwable {
    try {
        // before advice
        // target execution
        Object result = target();
        // after-returning advice nếu normal return
        return result;
    } catch (Throwable targetFailure) {
        // after-throwing advice nếu targetFailure phù hợp
        throw targetFailure;
    }
}

Object invokeAfterFinally() throws Throwable {
    try {
        return invokeInnerAdviceAndTarget();
    } finally {
        // @After chạy ở đây
        afterFinally();
    }
}
```

Đây là **pseudocode về ý nghĩa lồng nhau**, không phải API hoặc source code Spring; không dùng nó để biên dịch. Nó nhấn mạnh hai điều:

1. Cleanup cuối cùng nằm trong `finally` chạy **sau** khi phần gọi bên trong đã hoàn tất hoặc đang thoát vì lỗi.
2. Một thứ có precedence cao hơn ở **chiều vào** không nhất thiết in log trước trên **chiều ra**.

### Không nhầm `@After` với callback “sau khi client đã nhận kết quả”

Dòng `AFTER_FINALLY` chạy **trước khi lời gọi proxy trả quyền điều khiển hoàn toàn cho client**, không phải callback chạy bất đồng bộ sau khi client nhận kết quả. Advice synchronous nằm trong invocation stack của lời gọi hiện tại. Trừ khi code advice tự thiết kế async handoff, mọi side effect xảy ra trước khi client tiếp tục sau lời gọi đó.

## 9. Khi có nhiều aspects: `@Order` và nesting

Giả sử có hai aspects:

```java
@Aspect
@Order(1)
class SecurityTracingAspect {
    // @Around
}

@Aspect
@Order(2)
class BusinessEventsAspect {
    // @After, @AfterReturning, @AfterThrowing
}
```

Đây là **snippet** không chạy độc lập. Trong Spring, order số nhỏ hơn ưu tiên cao hơn:

```text
SecurityTracingAspect @Order(1)
     |
     +-- enter
     |
     +---- BusinessEventsAspect @Order(2)
     |             |
     |             +-- TARGET
     |             +-- @AfterReturning hoặc @AfterThrowing
     |             +-- @After (finally)
     |
     +-- exit
```

Nếu đảo precedence, thứ tự log có thể thay đổi vì interceptor chain lồng nhau theo chiều khác.

### `@Order` không sắp từng method advice riêng lẻ trong cùng aspect

Trong một `@Aspect` bean, `@Order` đặt precedence của **aspect** so với aspect khác. Độ ưu tiên giữa các loại advice trong **cùng aspect** theo quy tắc của Spring; còn các advice **cùng loại** trong một aspect không có thứ tự xác định bằng vị trí method trong source code.

Để có sequence ổn định:

- Nếu nhiều `@After` cùng muốn làm cleanup trên một join point, cân nhắc **một** `@After` method điều phối theo thứ tự rõ ràng.
- Nếu hai cleanup concerns độc lập cần precedence khác nhau, đặt trong **hai aspects** và gắn `@Order` phù hợp.
- Viết integration test kiểm tra log/event sequence khi thứ tự thật sự ảnh hưởng hành vi.

Không coi tên method `firstAfter()`, `secondAfter()` hoặc vị trí xuất hiện trước/sau trong file Java là một ordering contract.

## 10. Những trường hợp đặc biệt liên quan exception

### 10.1. Exception bị target tự bắt

```java
public String work() {
    try {
        throw new IllegalStateException("transient");
    } catch (IllegalStateException ignored) {
        return "RECOVERED";
    }
}
```

Khi gọi qua Spring proxy:

- `@AfterReturning` chạy vì method trả bình thường.
- `@AfterThrowing` không nhận exception này.
- `@After` vẫn chạy.

**Đừng đếm “số exception được throw trong thân method” bằng `@AfterThrowing`.** Advice chỉ nhìn thấy exceptional exit của join point.

### 10.2. `@AfterThrowing` lọc theo kiểu

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app..*.work(..))",
    throwing = "failure",
    argNames = "failure"
)
public void onlyIo(IOException failure) {
    // Chỉ nhận IOException và các subtype.
}
```

Đây là snippet; cần import `java.io.IOException`. Nếu target ném `IllegalArgumentException`, advice không chạy. Trong khi đó, `@After` với cùng pointcut vẫn chạy nếu execution kết thúc bằng một exception thuộc bất kỳ kiểu nào.

**`throwing` không phải `throws`.** `throwing="failure"` là tên binding; `IOException` của advice parameter là điều kiện match kiểu throwable tại runtime. Method có `throws IOException` nhưng trả bình thường không làm `@AfterThrowing` chạy.

### 10.3. Nếu advice `@AfterReturning` tự ném exception

Một `@AfterReturning` có thể gặp lỗi nội bộ khi ghi metric, gửi network request hoặc truy cập recorder. Lỗi này là lỗi của **advice**, không phải exceptional exit nguyên thủy của target method. Spring documentation nói `@AfterThrowing` không được xem là callback tổng quát nhận exception từ `@After` hoặc `@AfterReturning` đồng hành trong cùng aspect.

Ở góc nhìn client, exception mới từ advice vẫn có thể làm lời gọi proxy kết thúc bất thường. Do đó:

- Đừng giả định `@AfterThrowing` đồng aspect chắc chắn log lỗi được ném bởi `@AfterReturning`.
- Đừng để một lỗi telemetry thứ cấp vô tình che mất kết quả/lỗi nghiệp vụ quan trọng.
- Nếu cần quản lý luồng lỗi **của toàn bộ interceptor chain**, một outer `@Around` với `try/catch/finally` và thứ tự được điều khiển rõ ràng có khả năng quan sát rộng hơn; đó vẫn không phải lý do để tùy tiện catch và nuốt exceptions.

### 10.4. Nếu `@After` tự ném exception

Trong Java `finally`, một exception ném tại `finally` có thể thay thế exception hoặc return đang được truyền đi. Với Spring `@After`, việc advice ném lỗi cũng có thể ảnh hưởng exception/result client nhận. Vì vậy cleanup trong `@After` nên đơn giản và có chiến lược xử lý lỗi rõ ràng.

Ví dụ **không nên làm một cách vô ý**:

```java
@After("execution(* com.example.app..*.work(..))")
public void cleanup() {
    throw new IllegalStateException("telemetry unavailable");
}
```

Đây là **mẫu phản ví dụ**: log/metric thất bại có thể biến một business call thành lỗi hoặc che lỗi gốc. Không dùng `@After` làm nơi tự ném exception bất kể kết quả.

### 10.5. `@After` có chạy khi `@Before` ném exception không?

Câu trả lời cần xét **vị trí trong interceptor chain**. Trong một aspect có precedence cho advice khác loại, một before advice có thể nằm ngoài after/finally advice. Nếu `@Before` ném exception **trước khi chain tiến vào `@After` advisor**, `@After` bên trong chưa được vào, nên không thể khẳng định nó chắc chắn chạy.

Do đó, phát biểu chuẩn là: `@After` chạy khi **matched join point execution mà nó bao quanh** kết thúc theo normal/exception path; không phải “luôn chạy cho bất kỳ lỗi nào phát sinh ở bất kỳ advice nào trong mọi cấu hình”.

Nếu cần dọn dẹp ngay cả khi **một outer precondition advice** thất bại trước khi target được gọi, hãy thiết kế một `@Around` outermost với `try/finally`, có order và test riêng.

## 11. Tình huống gắn với Spring transaction, cache và async

### 11.1. `@Transactional`: `@After` chạy khi nào so với commit/rollback?

Spring transaction dùng một **transaction interceptor/advisor** riêng. `@After` và `@AfterThrowing` do aspect custom của bạn khai báo là **những advisor khác** trong cùng hoặc khác proxy chain.

Nếu custom aspect nằm **bên trong** transaction advice, log after/finally có thể xuất hiện **trước khi transaction interceptor kết thúc commit/rollback**. Nếu nằm bên ngoài, nó có thể quan sát kết quả khác. Không suy từ chữ `After` rằng “transaction chắc chắn đã commit/rollback”.

Cũng không suy rằng một `@AfterThrowing` chạy thì Spring bắt buộc rollback. Transaction rollback rules phụ thuộc vào type exception và cấu hình như `rollbackFor` / `noRollbackFor`.

### 11.2. `@Cacheable`: cache hit có thể bỏ qua target

Caching advisor có thể trả giá trị từ cache mà **không gọi target method**. Kết quả custom after advice nhìn thấy phụ thuộc vị trí tương đối với caching interceptor:

- Nếu custom advice ở **bên ngoài** interceptor cache, nó có thể nhìn thấy cache hit như normal return từ phần chain bên trong.
- Nếu custom advice ở **bên trong** và cache hit làm phần bên trong không được gọi, custom advice có thể không chạy.

Không mặc định rằng `@AfterReturning` luôn đồng nghĩa “target method đã thực thi” khi có cache, fallback hoặc outer around advice.

### 11.3. `@Async`: return của proxy không nhất thiết là completion của task

Một `@Async` method có thể trả `Future` hoặc `CompletableFuture`, hay chạy trong thread executor. `@AfterReturning` tại một proxy boundary có thể được gọi khi nhận **future object** hoặc trạng thái mà advisor expose, không đồng nghĩa business task đã thực sự thành công.

Tương tự, `@After` “method execution tại ranh giới proxy đã kết thúc” không thay thế completion callback trên future. Khi muốn đo task hoàn tất, kết nối `whenComplete` hoặc cơ chế async-specific phù hợp.

### 11.4. Retry: có bao nhiêu lần `@After` chạy?

Một retry interceptor/outer around advice có thể gọi phần chain/target nhiều lần. Nếu custom `@After` nằm **bên trong vòng retry**, nó có thể chạy sau mỗi attempt. Nếu nằm **ngoài**, nó có thể chạy một lần cho lời gọi client sau khi retry kết thúc.

Vì vậy định nghĩa metric phải rõ: **attempt count** hay **logical client call count**, và order của các interceptor phải tương thích với mục tiêu đo lường.

## 12. Dùng `@After` để cleanup mà không phá nghiệp vụ

Một aspect cleanup cần chú ý:

1. **Tính idempotent:** side-effect cleanup có thể được thực thi nhiều lần khi có retry hoặc nhiều tầng service proxy.
2. **Không phá flow do lỗi logger/metric:** telemetry code cần có error policy riêng; tránh ném lỗi không chủ đích trong `finally`.
3. **Tránh giữ shared mutable state không an toàn:** `@Aspect` bean thường singleton; không lưu trạng thái “start time”/method-specific vào field dùng chung khi nhiều threads gọi đồng thời.
4. **Dữ liệu liên quan riêng invocation:** nếu cần đo thời gian, ưu tiên local variable trong `@Around` với `try/finally`. Trường hợp `@Before` và `@After` tách method cần thiết kế context storage/thread propagation đúng.
5. **Không log secrets:** `JoinPoint.getArgs()` có thể chứa credentials hoặc PII; chỉ ghi metadata an toàn, không dump toàn bộ argument objects.
6. **Hạn chế vùng pointcut:** target cụ thể, service package hay annotation cần thiết, tránh auto-proxy advice vô ý trên chính infrastructure logging.
7. **Có positive và negative tests:** normal return, checked/unchecked exception, exception bị catch trong target, advice riêng không match và self-invocation.
8. **Hiểu thứ tự transaction/cache/async:** “after method” không luôn đồng nghĩa “sau tất cả transaction/async business events”.

Một ví dụ cleanup thuần ghi event:

```java
@Aspect
public class CompletionAspect {
    @After("execution(public * com.example.app.service..*.*(..))")
    public void onExit(JoinPoint jp) {
        System.out.println(
            "COMPLETE " + jp.getSignature().toShortString()
        );
    }
}
```

Đây là snippet trong môi trường Spring đã bật `@AspectJ`; không phải cấu hình đầy đủ. Nó ghi event **trên cả hai nhánh return/throw**, nhưng không bind result hay exception.

## 13. Chẩn đoán khi log không đúng kỳ vọng

| Triệu chứng | Kiểm tra |
| --- | --- |
| `@After` không chạy dù method được gọi | Có proxy Spring AOP? Pointcut match? Caller có dùng `new` hoặc self-invocation không? |
| `@AfterReturning` không chạy | Method có ném exception? Kiểu tham số `returning` có hạn chế match không? |
| `@AfterThrowing` không chạy | Method có ném lỗi ra ngoài? Binding type có là supertype của exception không? |
| Có `AFTER_FINALLY` dù `@AfterThrowing` không chạy | Exception có kiểu không phù hợp với `throwing`? Đây có thể là hành vi đúng |
| Thấy `AFTER_FINALLY` sau `AFTER_RETURNING` | Đây là after-finally semantics theo Spring 6.2 trong cùng aspect |
| Thứ tự giữa hai aspects không như mong muốn | Kiểm tra `@Order`/`Ordered`, advisor precedence và thứ tự vào/ra |
| Có hai `@After` trong cùng aspect in không đúng source order | Source declaration order không là ordering contract; gộp hoặc tách aspects |
| Exception từ log che mất lỗi target | Advice `@After` hoặc advice khác có throw lỗi mới không? |
| `@After` báo hoàn tất nhưng transaction chưa commit | Kiểm tra relative ordering với transaction interceptor |
| Cache hit không in log giống cache miss | Check ordering giữa caching interceptor và custom advice |
| Async method log quá sớm | Proxy return khác task completion; quan sát future/executor |
| Retry tạo log nhiều lần | Advice ở trong hay ngoài retry loop? |
| Dùng JDK/CGLIB khác nhau | Kiểm tra interface methods/final/private, `AopUtils.isJdkDynamicProxy` / `isCglibProxy` |

### Cách kiểm chứng Spring bean có được proxy

```java
Object bean = context.getBean("checkoutService");

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
System.out.println(AopUtils.getTargetClass(bean).getName());
```

Đoạn này **không phải chương trình độc lập**. Nó dùng `org.springframework.aop.support.AopUtils`, và `context` phải là Spring context hợp lệ. Có proxy không có nghĩa **mọi method** trên bean đều được custom advice match; phải kiểm tra pointcut và actual caller path.

### Integration test quan trọng nhất

Để chứng minh semantics:

- Gọi Spring bean qua `ApplicationContext` thật.
- Kiểm tra success: `@AfterReturning` → `@After`.
- Kiểm tra matching failure: `@AfterThrowing` → `@After`.
- Kiểm tra non-matching failure: **chỉ `@After`**, không có `@AfterThrowing` cụ thể.
- Kiểm tra handled exception: `@AfterReturning` → `@After` vì target đã recover.
- Test thêm một aspect `@Order` cao hơn để xác minh nesting khi có nhiều aspects.
- Tránh khẳng định dựa chỉ trên business return value; dùng recorder/mock/spy hoặc capture logger để assert **các advice event**.

## 14. Tự kiểm tra

**Câu 1.** Method trả `null` bình thường. Loại advice nào có thể chạy?  
**Đáp án:** `@AfterReturning` (nếu binding tương thích) và `@After`. `@AfterThrowing` không chạy chỉ vì return là null.

**Câu 2.** Target ném `IllegalArgumentException` ra client; `@AfterThrowing` nhận `IllegalArgumentException` và `@After` dùng cùng pointcut. Advice nào chạy?  
**Đáp án:** Cả hai; trong cùng aspect Spring hiện đại, `@AfterThrowing` thích hợp chạy rồi `@After` như finally.

**Câu 3.** Target ném `IllegalStateException`, nhưng `@AfterThrowing` chỉ nhận `IllegalArgumentException`. `@After` có chạy không?  
**Đáp án:** Có, vì kiểu exception không hạn chế `@After`; `@AfterThrowing` bị lọc bởi parameter type.

**Câu 4.** Target ném exception trong khối `try` rồi tự `catch` và trả `"OK"`. `@AfterThrowing` có chạy không?  
**Đáp án:** Không; target method execution hoàn thành bình thường, `@AfterReturning` và `@After` có thể chạy.

**Câu 5.** Trong Spring 6.2, cùng một aspect có `@After` và `@AfterReturning`. `@After` có in log trước `@AfterReturning` chỉ vì nó có precedence cao hơn không?  
**Đáp án:** Không. Theo after-finally semantics, `@After` được gọi sau after-returning phù hợp trên đường ra.

**Câu 6.** `@AfterThrowing` có nhận mọi exception do advice `@AfterReturning` trong cùng aspect tự ném không?  
**Đáp án:** Không. Spring không định nghĩa nó như general exception callback cho companion after advice.

**Câu 7.** Đặt `@After` trong `@Aspect` bean có nghĩa mọi method Java trong chương trình đều được cleanup?  
**Đáp án:** Không. Cần Spring-managed target, AOP proxy entry và matching pointcut.

**Câu 8.** Hai `@After` trong cùng aspect có thể được sắp xếp bằng thứ tự viết trong file Java không?  
**Đáp án:** Không có bảo đảm. Gộp logic hoặc tách aspects với `@Order` rõ ràng.

**Câu 9.** Một outer `@Around` bắt lỗi và trả fallback. Outer `@AfterThrowing` có chắc thấy exception gốc không?  
**Đáp án:** Không, vì phần chain có thể đã chuyển exceptional exit thành normal return trước khi outer advice quan sát.

**Câu 10.** `@After` chạy có chứng minh database transaction đã commit hoặc rollback không?  
**Đáp án:** Không. Cần xét transaction interceptor relative order và rollback rules.

## 15. Tài liệu Spring Framework chính thức

- [Spring Framework 6.2 — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): semantics `@After`, `@AfterReturning`, `@AfterThrowing` và phần *Advice Ordering* mô tả precedence theo advice type và after-finally thực tế.
- [Spring Framework — AOP Concepts](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html): phân biệt after returning, after throwing, after finally và around advice.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html): đăng ký Spring `@Aspect` beans và kích hoạt Spring AOP.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): giới hạn proxy, JDK/CGLIB, self-invocation.
- [Spring Framework — Declarative Transaction Rollback](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/rolling-back.html): rollback rules riêng với việc custom aspect quan sát exception.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-ordering-008">Không suy diễn thứ tự từ vị trí phương thức trong mã nguồn</a>

<details>
<summary>Xem chi tiết</summary>

# Không suy diễn thứ tự thực thi từ vị trí phương thức trong mã nguồn Spring Aspect

## 1. Kết luận cốt lõi

Trong Spring AOP, việc một phương thức advice nằm **phía trên** phương thức advice khác trong cùng class `@Aspect` **không bảo đảm** phương thức thứ nhất sẽ chạy trước phương thức thứ hai.

Đặc biệt, **hai advice cùng loại** trong **cùng một `@Aspect`** mà đều khớp một method execution có **thứ tự không xác định**. Spring Framework không thể dùng thứ tự khai báo Java source như một hợp đồng, vì Java reflection trên mã đã biên dịch bằng `javac` không cung cấp thứ tự source declaration đáng tin cậy.

Ví dụ:

```java
@Aspect
public class DangerousAuditAspect {
    @Before("execution(* com.example.ordering.TaskService.run(..))")
    public void validate() {
        System.out.println("VALIDATE");
    }

    @Before("execution(* com.example.ordering.TaskService.run(..))")
    public void audit() {
        System.out.println("AUDIT");
    }
}
```

**Không được kết luận** `VALIDATE` luôn xuất hiện trước `AUDIT` chỉ vì `validate()` được khai báo trước `audit()` trong file Java. Đây là **fragment minh họa lỗi thiết kế ordering**, chưa phải ứng dụng độc lập. Nếu audit bắt buộc chỉ xảy ra sau validation, cần một cơ chế sequencing có hợp đồng rõ ràng.

Để thiết lập thứ tự:

- Khi các thao tác thuộc **cùng một trách nhiệm**, gom vào **một advice method** và gọi các helper theo thứ tự Java tường minh.
- Khi chúng thuộc **các cross-cutting concerns độc lập**, tách thành **nhiều aspect classes** và dùng Spring `@Order` hoặc `Ordered`.
- Khi có các advice **khác loại** trong cùng `@Aspect`, hiểu **quy tắc precedence theo loại advice** và ngữ nghĩa `finally` của `@After`, không suy từ vị trí khai báo.
- Với cấu hình **Spring XML AOP**, nhận biết ngoại lệ: thứ tự của các advice elements trong **cùng `<aop:aspect>`** có ý nghĩa, không giống Java `@Aspect` declaration order.

Bài này chỉ tập trung **Spring Aspect bằng `@AspectJ` annotations trên Spring-managed beans thông qua Spring AOP proxy** và điểm khác biệt liên quan tới cấu hình Spring XML, không đi sâu vào AspectJ compile-time/load-time weaving độc lập.

## 2. Vì sao source position không phải quy tắc thứ tự?

Java code có thể hiển thị methods theo thứ tự rất rõ trong IDE, nhưng Spring đọc annotation metadata từ class đã được JVM nạp. Phương thức `Class.getDeclaredMethods()` và những cơ chế reflection tương tự **không cam kết giữ nguyên thứ tự methods trong tệp `.java`**.

Những thông tin sau **không được** dùng làm căn cứ ordering advice:

1. Số dòng của method trong source.
2. Thứ tự class methods mà IDE hiển thị.
3. Tên method theo alphabet, như `aValidate()` trước `zAudit()`.
4. Thứ tự các `@Bean` methods trong `@Configuration`.
5. Thứ tự các class được `@ComponentScan` tìm thấy.
6. Thứ tự import các aspect classes.
7. Thứ tự các log quan sát trong **một lần chạy thử** mà chưa có ordering contract.

Đổi chỗ hai methods trong class rồi thấy log “có vẻ đúng” không phải là cách ổn định để điều khiển Spring AOP. Một bản JDK, compiler, Spring version hoặc class layout khác có thể cho kết quả khác nếu bạn dựa vào thứ tự mà framework không hứa hẹn.

**Ngoại lệ cần ghi rõ:** Spring Framework từ **5.2.7** có quy tắc precedence giữa **các advice khác loại trong cùng class**. Điều đó **không** bảo đảm thứ tự giữa hai `@Before` hoặc hai `@After` trong cùng `@Aspect`.

## 3. Thứ tự trên đường vào và đường ra khác nhau

Spring proxy AOP dùng interceptor chain. Hãy hình dung hai `@Around` advices ở hai aspects khác nhau:

```text
Caller
  -> Spring AOP proxy
     -> OuterAspect.around()       @Order(1)
        -> InnerAspect.around()    @Order(10)
           -> Target.run()
        <- InnerAspect returns
     <- OuterAspect returns
  <- Caller receives return value
```

Trong Spring, **giá trị `@Order` nhỏ hơn có precedence cao hơn**. Aspect precedence cao hơn thực thi **sớm hơn trên đường vào** và **muộn hơn trên đường ra**. Đây là mô hình lồng nhau, không phải danh sách callbacks chạy theo thứ tự tuyến tính một chiều.

```text
[OUTER] enter
[INNER] enter
[TARGET] run
[INNER] exit
[OUTER] exit
```

Khi caller nhận exception, luồng trở ra cũng unwind ngược chiều:

```text
[OUTER] enter
[INNER] enter
[TARGET] throw
[INNER] catch/finally
[OUTER] catch/finally
```

Vì vậy, **không thể đọc `@Order(1)` và kết luận mọi dòng log của aspect đó sẽ luôn xuất hiện trước mọi dòng log của `@Order(10)`**.

### `@Order` dùng cho aspect classes

```java
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(1)
public class HighPriorityAspect {
    // Các advice thuộc aspect này.
}
```

Một aspect khác:

```java
@Aspect
@Order(10)
public class LowPriorityAspect {
    // Các advice thuộc aspect này.
}
```

Đây là class fragments. Thứ tự nằm ở **cấp aspect**, không phải lời hứa rằng `@Order` đặt trên từng method advice riêng sẽ quyết định thứ tự hai `@Before` trong cùng một class.

Spring cũng hỗ trợ aspect bean implement `org.springframework.core.Ordered`:

```java
@Aspect
public class HighPriorityAspect implements org.springframework.core.Ordered {
    @Override
    public int getOrder() {
        return 1;
    }
}
```

Chọn **một cơ chế ordering rõ ràng** cho aspect. Nếu có nhiều aspects trùng order mà lại cần thứ tự bắt buộc giữa chúng, nên dùng **các order values khác nhau** và integration-test kết quả.

## 4. Thứ tự advice khác loại trong cùng `@Aspect`

Theo tài liệu Spring Framework 6.x, khi nhiều advice **khác loại** trong **cùng một `@Aspect` class** cùng khớp một join point, mức precedence **cao xuống thấp** là:

| Precedence | Advice | Chức năng |
| --- | --- | --- |
| 1 | `@Around` | Bao quanh invocation; có thể gọi `proceed()`. |
| 2 | `@Before` | Chạy trước method execution phía trong. |
| 3 | `@After` | After-finally advice. |
| 4 | `@AfterReturning` | Quan sát normal return. |
| 5 | `@AfterThrowing` | Quan sát exception path. |

**Chú ý `@After`:** dù nằm trước `@AfterReturning`/`@AfterThrowing` trong bảng precedence, do ngữ nghĩa **after-finally**, `@After` **thực tế thực thi sau** `@AfterReturning` hoặc `@AfterThrowing` trong cùng aspect, tương tự khối Java `finally`.

Ví dụ:

```java
@Aspect
public class OutcomeAspect {
    @After("execution(* com.example.ordering.TaskService.run(..))")
    public void cleanup() {
        System.out.println("FINALLY");
    }

    @AfterReturning("execution(* com.example.ordering.TaskService.run(..))")
    public void success() {
        System.out.println("SUCCESS");
    }

    @Before("execution(* com.example.ordering.TaskService.run(..))")
    public void before() {
        System.out.println("BEFORE");
    }
}
```

Cố ý viết `cleanup()` **trước** `before()` trong source. Trong một method trả bình thường và không có advisor bổ sung làm thay đổi điều khiển, các sự kiện chính của aspect vẫn theo nghĩa:

```text
BEFORE
target execution
SUCCESS
FINALLY
```

Khi target ném exception, `@AfterReturning` không chạy; `@AfterThrowing` tương thích (nếu có) chạy rồi `@After` finally chạy.

**Phân biệt hai quy tắc:** precedence giữa **các loại advice** trong cùng aspect có bảo đảm của Spring từ 5.2.7; giữa **hai advice cùng loại** trong cùng aspect thì **không xác định**. Không gom cả hai thành kết luận sai “mọi advice cùng class đều không có thứ tự”.

## 5. Hai `@Before` cùng class: sửa thiết kế thế nào?

Giả sử quy tắc nghiệp vụ yêu cầu **validate trước audit**. Thay đổi vị trí hoặc tên Java methods chỉ thay đổi source code; nó không tạo ra ràng buộc thứ tự trong Spring.

**Nếu cùng một trách nhiệm**, dùng một advice điều phối các helper theo thứ tự tường minh:

```java
@Before("execution(* com.example.ordering.TaskService.run(..))")
public void before(JoinPoint jp) {
    validate(jp.getArgs());
    audit(jp.getSignature().getName());
}
```

Đây là fragment trong lớp `@Aspect`, giả định có hai helper `validate` và `audit` được định nghĩa cùng lớp. Nếu validation ném lỗi, bước audit và target không tiếp tục trừ khi code chủ động xử lý exception.

**Nếu là hai trách nhiệm độc lập**, khai báo hai lớp `@Aspect` riêng: `ValidationAspect @Order(10)` và `AuditAspect @Order(20)`, mỗi lớp có `@Before` match cùng method và đều được đăng ký làm Spring bean. Spring chọn ValidationAspect trước trên đường vào nếu advice không dừng chain.

Đổi thứ tự `@Bean`, đổi tên method theo alphabet hoặc thêm `@Order` lên một advice method trong cùng aspect đều không thay thế được hai cách trên. Bài về **giới hạn giữa các advice cùng loại** giải thích sâu hai phương án và cách kiểm thử.

## 6. Ví dụ Maven/Java 17+ có thể chạy: hai `@Around` và một aspect kết quả

Ví dụ dùng **Spring Framework 6.1.14**, **AspectJ Weaver 1.9.22.1**, **JDK 17+** và Maven. Mã chỉ dùng **Spring proxy-based AOP**, không yêu cầu AspectJ compile-time/load-time weaving.

Thiết kế:

- `OuterAspect` dùng `@Around`, `@Order(1)`.
- `InnerAspect` dùng `@Around`, `@Order(10)`.
- `OutcomeAspect` dùng `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After` và `@Order(20)`.
- Các advice ở `OutcomeAspect` được viết **không theo thứ tự cần chạy**, nhằm chứng minh source position không quyết định.
- `TaskService.run("ok")` trả bình thường; `run("fail")` ném `IllegalStateException`.
- Client chỉ gọi trên **bean được lấy từ `ApplicationContext`**, do đó lời gọi đi qua Spring proxy.

### 6.1. Cấu trúc thư mục

```text
spring-aspect-order/
├── pom.xml
└── src/main/java/com/example/ordering/
    ├── TaskService.java
    ├── OuterAspect.java
    ├── InnerAspect.java
    ├── OutcomeAspect.java
    ├── AppConfig.java
    └── DemoApp.java
```

### 6.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-aspect-order</artifactId>
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

`spring-context` cung cấp `ApplicationContext` và các Spring AOP dependencies phù hợp trong cây phụ thuộc của ví dụ; `aspectjweaver` giúp Spring hiểu pointcut expressions `@AspectJ`.

### 6.3. `src/main/java/com/example/ordering/TaskService.java`

```java
package com.example.ordering;

public class TaskService {
    public String run(String mode) {
        System.out.println("[TARGET] " + mode);
        if ("fail".equals(mode)) {
            throw new IllegalStateException("simulated");
        }
        return "ok:" + mode;
    }
}
```

Target là một class có public method không `final`, cho phép class-based proxy của Spring intercept.

### 6.4. `src/main/java/com/example/ordering/OuterAspect.java`

```java
package com.example.ordering;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(1)
public class OuterAspect {
    @Around("execution(public * com.example.ordering.TaskService.run(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("[OUTER] enter");
        try {
            Object result = pjp.proceed();
            System.out.println("[OUTER] return");
            return result;
        } catch (Throwable error) {
            System.out.println("[OUTER] throw "
                    + error.getClass().getSimpleName());
            throw error;
        }
    }
}
```

`@Order(1)` cho outer aspect precedence cao nhất trong demo. Lệnh in `return` nằm **sau `proceed()`** nên sẽ chạy trên đường trở ra, không phải đầu chuỗi.

### 6.5. `src/main/java/com/example/ordering/InnerAspect.java`

```java
package com.example.ordering;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(10)
public class InnerAspect {
    @Around("execution(public * com.example.ordering.TaskService.run(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("[INNER] enter");
        try {
            Object result = pjp.proceed();
            System.out.println("[INNER] return");
            return result;
        } catch (Throwable error) {
            System.out.println("[INNER] throw "
                    + error.getClass().getSimpleName());
            throw error;
        }
    }
}
```

Inner aspect nằm sau `OuterAspect` trên đường vào và trước `OuterAspect` trên đường ra.

### 6.6. `src/main/java/com/example/ordering/OutcomeAspect.java`

```java
package com.example.ordering;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;

@Aspect
@Order(20)
public class OutcomeAspect {
    // Phương thức finally xuất hiện ĐẦU file có chủ đích.
    @After("execution(public * com.example.ordering.TaskService.run(..))")
    public void onFinally() {
        System.out.println("[EVENT] finally");
    }

    @AfterThrowing(
        pointcut = "execution(public * com.example.ordering.TaskService.run(..))",
        throwing = "error",
        argNames = "error"
    )
    public void onError(Throwable error) {
        System.out.println("[EVENT] after-throwing "
                + error.getClass().getSimpleName());
    }

    @AfterReturning("execution(public * com.example.ordering.TaskService.run(..))")
    public void onSuccess() {
        System.out.println("[EVENT] after-returning");
    }

    // Phương thức before xuất hiện CUỐI file có chủ đích.
    @Before("execution(public * com.example.ordering.TaskService.run(..))")
    public void before() {
        System.out.println("[EVENT] before");
    }
}
```

Các loại advice **khác nhau** có semantics precedence đã được Spring quy định. Trên normal return: `before` → target → `after-returning` → `finally`. Trên exception: `before` → target throw → `after-throwing` → `finally`. Không cần giả định về thứ tự methods trong file.

### 6.7. `src/main/java/com/example/ordering/AppConfig.java`

```java
package com.example.ordering;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    public TaskService taskService() {
        return new TaskService();
    }

    @Bean
    public OuterAspect outerAspect() {
        return new OuterAspect();
    }

    @Bean
    public InnerAspect innerAspect() {
        return new InnerAspect();
    }

    @Bean
    public OutcomeAspect outcomeAspect() {
        return new OutcomeAspect();
    }
}
```

`@EnableAspectJAutoProxy` bật Spring auto-proxy infrastructure. `proxyTargetClass=true` chọn CGLIB cho `TaskService` không có business interface. Các class `@Aspect` được đăng ký thành **Spring beans** bằng `@Bean`; `@Aspect` một mình không tự đăng ký bean.

Thứ tự `@Bean` definitions trong `AppConfig` **không phải** điều kiện tạo thứ tự advice. Đừng chỉnh vị trí ba `@Bean` methods để điều khiển execution chain.

### 6.8. `src/main/java/com/example/ordering/DemoApp.java`

```java
package com.example.ordering;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApp {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            TaskService service = context.getBean(TaskService.class);
            System.out.println("isAopProxy="
                    + AopUtils.isAopProxy(service));

            System.out.println("[CALLER] success");
            String result = service.run("ok");
            System.out.println("[CALLER] result=" + result);

            System.out.println("[CALLER] failure");
            try {
                service.run("fail");
            } catch (IllegalStateException ex) {
                System.out.println("[CALLER] caught " + ex.getMessage());
            }
        }
    }
}
```

### 6.9. Chạy và stdout dự kiến

Tại thư mục chứa `pom.xml` với JDK 17+:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.ordering.DemoApp
```

Kết quả do code tự in ra, bỏ qua các log khác của Maven/Spring nếu có:

```text
isAopProxy=true
[CALLER] success
[OUTER] enter
[INNER] enter
[EVENT] before
[TARGET] ok
[EVENT] after-returning
[EVENT] finally
[INNER] return
[OUTER] return
[CALLER] result=ok:ok
[CALLER] failure
[OUTER] enter
[INNER] enter
[EVENT] before
[TARGET] fail
[EVENT] after-throwing IllegalStateException
[EVENT] finally
[INNER] throw IllegalStateException
[OUTER] throw IllegalStateException
[CALLER] caught simulated
```

Giải thích:

1. `OuterAspect @Order(1)` ở ngoài `InnerAspect @Order(10)`, cả hai ở ngoài `OutcomeAspect @Order(20)`.
2. Dòng `[EVENT] before` xuất hiện **trước target**, dù method `before()` viết ở **cuối class**.
3. Dòng `[EVENT] finally` xuất hiện **sau after-returning** trên normal return, dù method `onFinally()` viết ở đầu class.
4. Trường hợp exception dùng `@AfterThrowing` và `@After`, không dùng `@AfterReturning`.
5. Catch blocks của `InnerAspect` rồi `OuterAspect` chạy theo thứ tự **unwind**; precedence cao hơn **không** đồng nghĩa log cuối của outer được in sớm.
6. Mọi lời gọi đi qua **Spring AOP proxy**; nếu client tự `new TaskService()` để gọi trực tiếp, các advice Spring không áp dụng như demo.

Output được lập theo các rules của Spring 6.1 và code đã viết; **chưa phải kết quả Maven đã chạy thực tế**. Khi triển khai project, hãy xác minh với Spring/JDK version đang sử dụng.

## 7. Ngoại lệ cấu hình XML: thứ tự các advice elements trong cùng `<aop:aspect>`

Không được nói “Spring **không bao giờ** dùng thứ tự khai báo”. Với **Spring AOP XML schema**, tài liệu chính thức chỉ rõ:

> Trong cùng một `<aop:aspect>`, precedence giữa các advice declarations được xác định theo thứ tự các **XML advice elements** (từ trước tới sau).

Ví dụ:

```xml
<aop:config>
    <aop:pointcut id="operation"
        expression="execution(* com.example.ordering.TaskService.run(..))"/>

    <aop:aspect ref="xmlLoggingAspect">
        <aop:around method="around" pointcut-ref="operation"/>
        <aop:before method="before" pointcut-ref="operation"/>
    </aop:aspect>
</aop:config>
```

Đây là **XML fragment**, chưa có `beans` root, namespaces/schema declarations hoặc bean `xmlLoggingAspect`. Trong cùng `<aop:aspect>` này, `<aop:around>` được đặt trước `<aop:before>` và được Spring XML AOP diễn giải có precedence cao hơn.

Điều đó **khác bản chất** với hai Java methods mang `@Before` trong cùng `@Aspect`; không thể lấy XML rule áp dụng ngược cho annotation-style.

Với nhiều XML aspects, Spring còn hỗ trợ `order` trên `<aop:aspect>` hoặc `@Order`/`Ordered` trên backing aspect bean theo cấu hình được hỗ trợ. `<aop:advisor>` cũng có thuộc tính `order`. Khi dự án dùng hỗn hợp Java `@Aspect` và XML advisors, cần xét **tổng advisor chain trong cùng `ApplicationContext`**, không suy từ vị trí text trong một file.

## 8. Khi có `@Transactional`, caching hoặc security advice

Một service có thể đồng thời được áp dụng `@Transactional`, `@Cacheable`, method security và custom Spring `@Aspect`. Thứ tự ở đây không tự được quyết định bởi vị trí annotation trong source.

Ví dụ:

```java
@Transactional
public String pay(String paymentId) {
    // ...
}
```

và:

```java
@Aspect
@Order(10)
public class AuditAspect {
    @Before("execution(* com.example.payment.PaymentService.pay(..))")
    public void audit() {
        // ...
    }
}
```

Đây là **fragments**, không phải runnable demo. Câu hỏi “audit diễn ra trước, trong hay sau transaction?” cần đối chiếu:

1. Order của **transaction advisor** trong ứng dụng.
2. Order của custom audit aspect.
3. Transaction đã có ở **caller** chưa.
4. Advisor nào **ở ngoài** advisor nào.
5. Có `@Around` nào short-circuit, catch exception hoặc retry bằng multiple `proceed()` không.

Ví dụ nếu retry aspect ở **ngoài** transaction interceptor và không có ambient transaction, mỗi lần retry có thể có transaction boundary mới. Nếu transaction interceptor ở **ngoài** retry, nhiều attempts có thể chạy trong **một** transaction. Việc đổi vị trí methods trong class `@Aspect` không giải quyết câu hỏi này.

Không coi `@AfterReturning` của service là “sau commit” trong mọi cấu hình. Nếu nghiệp vụ thực sự yêu cầu action **after transaction commit**, dùng cơ chế transaction synchronization/event phù hợp của Spring, không suy từ advice name.

## 9. Những trường hợp khác không có bảo đảm thứ tự

### 9.1. Nhiều aspects không có `@Order`

Hai aspects khác nhau cùng match join point nhưng **không được khai báo precedence**: Spring docs nói thứ tự giữa chúng **không xác định**. Muốn một aspect có precedence rõ ràng, đặt `@Order` hoặc implement `Ordered`.

### 9.2. Hai aspects có cùng giá trị order

Nếu cả hai cùng `@Order(10)` và bạn vẫn cần A trước B, giá trị bằng nhau **không biểu thị yêu cầu đó**. Đưa ra distinct precedence values và test. Không suy ra tie-breaker từ tên bean hay thứ tự registration.

### 9.3. `@Around` gọi `proceed()` nhiều lần

Một outer `@Around` có thể gọi `proceed()` **0, 1 hoặc nhiều lần**. Khi gọi hai lần, các inner advices/target có thể chạy hai lần trong **một** proxy invocation từ caller. Điều này không mâu thuẫn với `@Order`: precedence quyết định **vị trí trong chain**, còn logic `proceed()` quyết định **số lần chuyển tiếp**.

### 9.4. Exception có thể thay đổi đường ra

Nếu target hoặc advice phía trong ném lỗi, những `@AfterReturning` tương ứng có thể không chạy; `@AfterThrowing` và `@After` có thể chạy theo contract. Một outer `@Around` có thể catch và biến exception thành một return value, làm thay đổi những advice bên ngoài nó quan sát được. Vì thế thứ tự log trên exception path khác normal path là điều bình thường.

### 9.5. Các bean/proxy khác context

Auto-proxy creator là infrastructure của **`ApplicationContext`**. Nếu aspects đăng ký ở context này còn target bean ở context khác, không thể dùng thứ tự `@Order` để kết luận rằng chúng cùng áp dụng lên một target. Trước khi debug order, phải xác nhận **advice thực sự có mặt trong proxy chain**.

## 10. Kiểm chứng precedence qua Spring AOP proxy

Khi debug, bắt đầu bằng bean do container trả về:

```java
Object bean = context.getBean("taskService");

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
```

Đây là fragment, cần context có sẵn. Nếu `isAopProxy=false`, đừng bắt đầu tranh luận advice nào chạy trước: có thể aspect chưa là bean, pointcut không match hoặc caller không dùng proxy.

Với proxy implement Spring `Advised`, có thể xem advisors:

```java
Object bean = context.getBean("taskService");

if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (org.springframework.aop.Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
```

Đây là fragment quan sát infrastructure. Thứ tự advisor listing có thể hỗ trợ chẩn đoán, nhưng cần **test invocation thực tế** để xác nhận nested `@Around`, dynamic matching, exception path và số lần `proceed()`.

### Sử dụng event recorder trong integration test

Một integration test nên thu events vào danh sách **cục bộ cho test invocation**, sau đó assert thứ tự:

```text
outer-enter
inner-enter
before
target
after-returning
finally
inner-return
outer-return
```

Đây là sequence đúng cho nhánh normal return của demo. Đừng dùng `ArrayList` mutable chung trong singleton aspect rồi chạy test concurrent và kết luận rằng Spring ordering bị lỗi khi events từ nhiều threads xen nhau.

Phân biệt **request-level correlation id** và **advice invocation**: nếu hệ thống xử lý song song, log của hai requests có thể interleave dù thứ tự trong **mỗi request** vẫn đúng theo precedence.

Một test đủ mạnh cần kiểm tra:

- Success và exception branches.
- Hai aspects với distinct `@Order`.
- Cùng một join point được cả hai aspects match.
- Advice types và `@After` finally semantics.
- Số lần target thực sự chạy.
- Lời gọi qua Spring-managed proxy, không qua `new` hoặc self-invocation.
- Sự hiện diện của transaction/cache/security advisors nếu ứng dụng có.

## 11. Các lỗi phổ biến

| Biểu hiện | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| Đổi vị trí hai `@Before` methods nhưng thứ tự không đổi | Source order không có precedence contract | Gộp steps hoặc tách thành aspects có `@Order`. |
| Test thỉnh thoảng thay đổi thứ tự log | Test đang dựa vào thứ tự không xác định hoặc events từ nhiều threads xen nhau | Khai báo order explicit và cô lập event collector theo invocation. |
| `@Order(1)` aspect in dòng “exit” sau `@Order(10)` | Aspect precedence cao nằm **ngoài**, unwind sau | Phân biệt đường vào và đường ra. |
| `@After` in sau `@AfterReturning` | `@After` là after-finally advice | Đây là semantics của Spring; đừng suy từ type precedence table thiếu ngữ cảnh. |
| Hai aspects khác nhau có thứ tự khó đoán | Không có `@Order` hoặc cùng order | Đặt distinct `@Order` values khi nghiệp vụ phụ thuộc order. |
| `@Order` đặt trên advice method không cho kết quả mong đợi | Điều khiển sai cấp | Đặt `@Order` trên aspect class/bean hoặc dùng `Ordered`. |
| XML aspect có thứ tự khác Java `@Aspect` | XML advice elements có precedence theo declaration order | Áp dụng đúng quy tắc cho đúng configuration style. |
| Đổi `@Bean` methods mà advice order vẫn vậy | Bean lifecycle không phải advice chain precedence | Đọc advisors, dùng `@Order`. |
| Method `@AfterReturning` không chạy trên lỗi | Chỉ áp dụng normal return | Dùng `@AfterThrowing` cho lỗi, `@After` cho finally. |
| Cùng một request nhưng target chạy hai lần | Outer `@Around` gọi `proceed()` nhiều lần | Kiểm tra retry/replay và side effects. |
| Custom audit xảy ra ngoài transaction | Transaction advisor có vị trí khác mong đợi | Cấu hình/test advisor precedence và transaction lifecycle. |
| Không có advice nào dù class có `@Aspect` | Aspect chưa là Spring bean, AOP chưa bật, pointcut không match, caller bypass proxy | Kiểm tra context, `AopUtils`, đường gọi và bean registration. |

## 12. Checklist trước khi merge thay đổi thứ tự advice

- [ ] Không dựa vào **vị trí khai báo methods** trong tệp `.java`.
- [ ] Không yêu cầu thứ tự cố định giữa **hai advice cùng loại trong cùng `@Aspect`**.
- [ ] Nếu cùng một concern cần sequence, đã gom steps vào **một advice method**.
- [ ] Nếu nhiều concerns cần precedence, đã dùng **nhiều aspects với `@Order`/`Ordered` rõ ràng**.
- [ ] Nhớ **order value nhỏ hơn nghĩa là precedence cao hơn**.
- [ ] Hiểu `@Around` bao ngoài và chạy sau trên đường unwind.
- [ ] Phân biệt quy tắc **advice-type precedence** với quy tắc không xác định giữa same-type advice.
- [ ] Hiểu `@After` mang **finally semantics**, không chỉ normal success.
- [ ] Phân biệt Spring annotation-style và XML `<aop:aspect>` ordering.
- [ ] Đã xét advisors từ transaction/cache/security nếu cùng proxy chain.
- [ ] Aspect và target đúng `ApplicationContext`; caller đi qua Spring proxy.
- [ ] Integration tests xác minh thứ tự trên cả success và exception paths.
- [ ] Nếu `@Around` có thể retry hoặc short-circuit, đã kiểm tra số lần `proceed()`.
- [ ] Không viết tests dựa trên những thứ tự Spring **không bảo đảm**.

### Tài liệu chính thức

- Spring Framework — **Declaring Advice / Advice Ordering**: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework 6 Reference — **Advice type precedence từ Spring 5.2.7**: https://docs.spring.io/spring-framework/docs/6.0.2/reference/html/core.html
- Spring Framework — **Schema-based AOP Support / XML ordering**: https://docs.spring.io/spring-framework/reference/core/aop/schema.html
- Spring Framework — **Enabling @AspectJ Support**: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — **Proxying Mechanisms**: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring `@Order` Javadoc: https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/annotation/Order.html

**Kết luận:** Spring AOP xác định thứ tự bằng **advice type semantics, aspect precedence và interceptor chain**, chứ không phải vị trí các methods trong Java source. Với **hai advice cùng loại trong cùng class `@Aspect`**, thứ tự **không xác định**; hãy thay đổi thiết kế thay vì dựa vào thứ tự tình cờ quan sát được.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-ordering-009">Thứ tự giữa aspect tự viết và advisor hạ tầng Spring</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự giữa aspect tự viết và advisor hạ tầng Spring

## 1. Câu hỏi trung tâm

Khi một Spring service method vừa khớp `@Aspect` tự viết, vừa có `@Transactional`, `@Cacheable`, Spring Method Security hoặc `@Async`, **advice nào thực thi trước, advice nào thực thi sau và advice nào có thể ngăn method đích chạy?**

Trong **Spring AOP theo mô hình proxy**, nhiều advisor/interceptor có thể cùng tham gia xử lý một lần gọi method. Điều cần phân biệt không chỉ là tên annotation mà là **vị trí của từng advisor trong chuỗi gọi**, thứ tự “đi vào”/“đi ra”, nơi có thể short-circuit, và **cơ chế đặt order của từng infrastructure feature**.

Ví dụ: với custom `@Around` audit bao **ngoài** transaction interceptor, audit có thể ghi log sau khi `proceed()` kết thúc **bao gồm bước commit**. Nhưng với custom aspect đặt **bên trong** transaction interceptor, log “method returned” có thể xuất hiện **trước khi transaction interceptor commit**. Vì vậy hai log “success” ở những vị trí khác nhau **không mang cùng ý nghĩa**.

**Phạm vi bài này:** Spring `@AspectJ`/Spring AOP proxy, `@Order`/`Ordered`, transaction/cache/method-security/async advisors và cách quan sát chính xác thứ tự. Các ví dụ tập trung vào Spring Framework, không đi sâu AspectJ weaving hay lý thuyết AOP chung.

## 2. Bản chất của thứ tự trong Spring AOP

Một Spring-managed service có thể được trả ra dưới dạng proxy. Caller đi qua reference proxy, Spring áp dụng các advisors có thể match vào method execution:

```text
Other Spring bean / caller
         |
         | workService.run("A")
         v
Spring AOP proxy
         |
         +--> Custom @Around audit            (order 10)
         |        |
         |        +--> Spring transaction advice (order 20)
         |                  |
         |                  +--> Custom @Around metric (order 30)
         |                            |
         |                            +--> target.run("A")
         |                            |
         |                            +-- return / throw
         |                  |
         |                  +-- commit / rollback
         |        |
         |        +-- audit exit
         v
Result / exception to caller
```

**Số order nhỏ hơn có precedence cao hơn** theo Spring `Ordered` và `@Order`. Trên đường đi vào method, advisor có precedence cao thường được gọi **trước**. Trên đường đi ra, wrapper có precedence cao kết thúc **sau**, vì nó nằm **ngoài** wrapper thấp hơn.

Nếu mọi interceptor đều cho chain tiếp tục:

```text
Audit.enter    (10)
  Tx.begin     (20)
    Metric.enter (30)
      target.run()
    Metric.exit
  Tx.commit
Audit.exit
```

Với exception khiến Spring transaction mặc định rollback:

```text
Audit.enter
  Tx.begin
    Metric.enter
      target.run() -> throw RuntimeException
    Metric.throw
  Tx.rollback
Audit.throw -> caller
```

Đây là **sơ đồ định hướng**, không phải thứ tự mặc định của mọi dự án. Các giá trị 10/20/30 là cấu hình **được chọn tường minh trong demo**, không phải giá trị Spring tự chọn cho transaction, security hoặc cache.

### Hai phân biệt quyết định

1. **Match hay không match:** pointcut của custom `@Aspect`; annotation `@Transactional`, `@Cacheable`, policy bảo mật; method/proxy eligibility; runtime conditions. Một advisor không match thì `order` của nó không khiến nó xuất hiện.
2. **Sau khi match, ai nằm ngoài ai:** Spring áp precedence/order; nhưng một số infrastructure features dùng cách thêm advisor/BeanPostProcessor riêng và có thể tạo nhiều proxy layers. Không xem mọi cấu hình `order` đều là số trên một danh sách duy nhất.

Một advisor ngoài cùng có thể **không gọi downstream** (`@Around` trả sớm, cache hit, security deny, exception). Như vậy một custom aspect nằm trong chain có thể không thấy invocation dù pointcut của nó phù hợp.

## 3. @Order trên custom aspect: ý nghĩa và giới hạn

Ví dụ:

```java
@Aspect
@Order(10)
public class AuditAspect {
    @Around("execution(public * com.example.work..*Service.*(..))")
    public Object observe(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("audit:enter");
        try {
            Object result = pjp.proceed();
            System.out.println("audit:return");
            return result;
        } catch (Throwable error) {
            System.out.println("audit:throw");
            throw error;
        }
    }
}
```

Đây là **mẫu minh họa**, cần imports, Spring bean registration và application context thích hợp. `@Order(10)` đặt precedence cho **aspect bean/advice** do Spring AOP dựng từ class này. Aspect `@Order(30)` khác thường có precedence thấp hơn và sẽ được gọi sau ở chiều vào.

Các quy tắc:

- Spring cho phép dùng `@Order(int)` hoặc implement `org.springframework.core.Ordered` trong aspect.
- **Số nhỏ có precedence cao.** `Ordered.HIGHEST_PRECEDENCE` và `LOWEST_PRECEDENCE` là hai mốc.
- Precedence cao chạy **trước trên đường vào, sau trên đường ra** đối với wrapper theo chuỗi.
- Nếu hai aspects có cùng order và thứ tự rất quan trọng, **đừng trông cậy vào thứ tự bean registration/tên class**.
- `@Order` không làm thay đổi pointcut expression, không khiến `@Aspect` tự thành Spring bean, không sửa self-invocation.
- Nếu nhiều advice **cùng loại trong cùng một `@Aspect`** cùng match, không suy thứ tự Java declaration là cố định. Dùng các aspect classes khác nhau hoặc gom logic cần thứ tự trong một method advice.

**Quan trọng:** precedence trong Spring không có nghĩa “audit luôn nhìn thấy mọi call”. Cache/security/async/transaction advisors khác có thể kết thúc sớm, đổi outcome hoặc thay đổi threading.

## 4. Infrastructure advisor là gì trong bài này?

Nhiều tính năng Spring được triển khai bằng **AOP advisor/interceptor infrastructure** (trong proxy mode). Không nên đồng nhất mọi thứ với class có `@Aspect`:

| Feature | Kích hoạt/configuration điển hình | Advisor/interceptor quan trọng | Ý nghĩa khi cùng tồn tại với custom aspect |
| --- | --- | --- | --- |
| Declarative transaction | `@EnableTransactionManagement` hoặc Spring Boot transaction auto-configuration tương ứng | Transaction advisor/`TransactionInterceptor` | Bắt đầu/tham gia transaction, quyết định commit/rollback theo outcome nó nhìn thấy. |
| Spring caching | `@EnableCaching` hoặc Boot caching configuration khi được enable | Cache advisor/`CacheInterceptor` | Cache hit có thể trả ngay, không gọi target/downstream. |
| Method security | `@EnableMethodSecurity` của Spring Security | Authorization interceptors cho `@PreAuthorize`, `@PostAuthorize`, filter, v.v. | Có thể deny trước method, kiểm tra result sau method, hoặc filter args/result theo policy. |
| Async execution | `@EnableAsync` | `AsyncAnnotationAdvisor`/`AnnotationAsyncExecutionInterceptor`, thường thêm qua `AsyncAnnotationBeanPostProcessor` | Có thể chuyển execution sang executor/thread khác; order có ý nghĩa khác với transaction/cache trong config. |
| Custom `@Aspect` | `@Aspect` + Spring bean + `@EnableAspectJAutoProxy` / Boot AOP | Advisors sinh từ `@Around`, `@Before`, ... | Quan sát hoặc thay call/result; precedence đặt trên aspect bằng `@Order`/`Ordered`. |

**Điều kiện chung:** tính năng phải được enable đúng Spring context, method/annotation đủ điều kiện, lời gọi đi qua Spring proxy. Có `@EnableAspectJAutoProxy` **không tự bật** caching, transaction management hay Spring Method Security. `@Transactional` cũng cần `PlatformTransactionManager` hoặc transactional infrastructure tương ứng. Mỗi feature có yêu cầu của riêng nó.

## 5. Transaction advisor: ví dụ order có thể kiểm soát trực tiếp

Spring Framework `@EnableTransactionManagement` có thuộc tính `order` dành cho **transaction advisor**:

```java
@Configuration
@EnableTransactionManagement(order = 20)
@EnableAspectJAutoProxy(proxyTargetClass = true)
class WorkConfiguration {
    // @Bean PlatformTransactionManager transactionManager() ...
    // @Bean custom @Aspect beans ...
}
```

Đây là đoạn **cấu hình minh họa**, chưa phải project chạy độc lập. Điểm cần nhớ:

- `@EnableTransactionManagement(order=20)` đặt vị trí ưu tiên của **transaction advisor** khi cùng match.
- Mặc định `@EnableTransactionManagement.order` là `Ordered.LOWEST_PRECEDENCE`. Giá trị mặc định không bảo đảm transaction sẽ nằm bên ngoài custom `@Order(10)` hoặc các advisors khác có same precedence; phải xem cụ thể.
- `@Order(10)` trên custom aspect có precedence cao hơn transaction advisor có order 20, **bao ngoài transaction interceptor**.
- `@Order(30)` trên custom aspect nằm bên trong transaction advisor order 20 theo thứ tự tương ứng.
- Transaction semantics/rollback rules vẫn do Spring transaction infrastructure quyết định. `@Order` không tạo database connection hoặc bảo đảm commit thành công.
- Spring `@Transactional` có proxy semantics, không tự áp khi một target gọi `this.innerTransactionalMethod()`.

### Quan sát “sau commit” cho đúng

Giả sử:

```text
@Order(10) Audit.enter
  TransactionInterceptor.begin
    @Order(30) Metrics.enter
      Target returns normally
    @Order(30) Metrics.return   <-- thường trước commit
  TransactionInterceptor.commit
@Order(10) Audit.return        <-- sau khi proceed() qua tx interceptor kết thúc
```

Trong mô hình này, khi `AuditAspect` thực sự **ở ngoài** transaction advisor và `proceed()` đi qua nó, lúc `AuditAspect` nhận normal result thì transaction interceptor phía trong đã xử lý commit/cleanup theo cấu hình. Nhưng không tổng quát hóa thành:

- Mọi `@AfterReturning` trong mọi Spring aspect đều là event **sau commit**;
- Transaction của caller/outer transaction cũng đã commit;
- Các `REQUIRES_NEW`/`REQUIRED`/nested transactions đều có cùng thời điểm;
- Event đang xử lý trên thread khác đã hoàn tất;
- Database/business side effects thực sự đạt mức durability theo mọi storage/configuration.

Nếu cần xử lý **chỉ sau commit thành công** trong transaction phù hợp, sử dụng Spring `TransactionSynchronization` hoặc `@TransactionalEventListener(phase = AFTER_COMMIT)`, với contract và configuration rõ ràng.

### @Transactional method return vẫn có thể bị rollback

Một method có thể return bình thường nhưng transaction interceptor sau đó nhận exception trong commit, hoặc transaction đã được đánh dấu rollback-only và kết thúc bằng `UnexpectedRollbackException`. Nếu metrics aspect **nằm trong** transaction interceptor, nó có thể in `metric:return` trước một rollback/commit failure. Nếu audit aspect **ở ngoài**, nó có thể thấy một exception từ transaction completion thay vì normal return của target.

**Do đó:** cần phân biệt **target returned** và **transactional invocation completed**.

## 6. @EnableCaching.order và nguy cơ cache hit bỏ qua advice

Spring caching cho phép:

```java
@Configuration
@EnableCaching(order = 20)
class CacheConfiguration {
    @Bean
    CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("catalog");
    }
}
```

Đây là **đoạn minh họa**, cần import `CacheManager` và `ConcurrentMapCacheManager` cùng các Spring beans thích hợp. `@EnableCaching.order` là order của **cache advisor**; mặc định `Ordered.LOWEST_PRECEDENCE` trong Spring Framework.

Giả sử `CatalogService.find(..)` có `@Cacheable("catalog")` và một custom `@Around`:

| Chuỗi order giả định | Cache miss | Cache hit |
| --- | --- | --- |
| Custom audit **ngoài** cache advisor | Audit.enter → cache → target → cache → audit.exit | Audit.enter → cache trả từ cache → audit.exit; target không chạy. |
| Cache advisor **ngoài** custom audit | Cache → audit.enter → target → audit.exit → cache | Cache trả từ cache; **custom audit không được gọi**. |

**Đây là trường hợp rất quan trọng:** “Không có log audit” không nhất thiết là thiếu pointcut. Cache hit có thể **short-circuit trước khi vào advice** nếu vị trí advisor là như vậy.

Nhưng điều này **không có nghĩa** đặt audit ngoài cache tự động bảo đảm mọi success metric đúng. Khi cache hit, audit có thể đo **proxy request** chứ không đo **target business execution**. Phải đặt tên metric theo đúng đại lượng.

### Caching và transaction cùng dùng LOWEST_PRECEDENCE

Mặc định transaction advisor và caching advisor đều có thể dùng `Ordered.LOWEST_PRECEDENCE`. Nếu nghiệp vụ phụ thuộc ai bao ngoài ai, **không coi tie là thứ tự có hợp đồng**. Hãy cấu hình order có chủ đích và integration test:

- Cache hit có vào transaction không?
- Cache được put/evict trước hay sau outcome nào?
- Nếu commit fail, cache có chứa dữ liệu stale hay không?
- Advisor có thể thay thế return value/exception không?

Các câu hỏi cuối cần xét cache manager, transaction semantics, event handling và feature integration cụ thể; một con số `@Order` không tự giải quyết tính nhất quán DB-cache.

## 7. Spring Security method advisors: không tự gán cùng công thức order

Spring Security Method Security khi được bật bằng `@EnableMethodSecurity` cung cấp **nhiều advisors/interceptors chuyên biệt**, ví dụ `@PreAuthorize`, `@PostAuthorize`, `@PreFilter` và `@PostFilter`. Chúng có **vị trí mặc định trong chain**, không phải một “SecurityAspect” chung có một số order.

Trong tài liệu Spring Security hiện đại, có ví dụ `@PreFilter` ở precedence 100, `@PreAuthorize` ở 200; các advisors khác có thứ tự tương ứng và có thể chịu cấu hình. Phiên bản/configuration thay đổi thì cần **đọc chính docs của bản Security đang chạy**; đừng lấy số từ một README để suy thành hằng số bất biến cho mọi bản.

`@EnableMethodSecurity(offset = ...)` (ở các bản hỗ trợ) có thể dịch vị trí precedence của các method-security interceptors theo cấu hình, nhưng đây là **quy tắc của Spring Security infrastructure**, không phải lời khuyên tùy tiện thay offset để khắc phục lỗi proxy.

### Security đặt trước hay sau custom @Around?

Giả sử custom `@Around` có precedence cao hơn `@PreAuthorize` advisor:

```text
CustomAround.enter
   SecurityAdvisor checks permission
      -> DENIED (throw)
CustomAround.catch/exit
Caller nhận denial
```

Nếu custom aspect **bỏ qua `proceed()`** và tự trả fallback “ok”, security advisor **bên trong có thể không được thực thi**. Điều này đặc biệt nguy hiểm nếu custom advice được dùng như cache hoặc degradation fallback.

Nếu security advisor bao ngoài custom aspect, authorization có thể deny **trước khi** custom aspect nhận invocation. Việc thiếu audit log ở custom aspect khi deny có thể là expected behavior, không phải security không chạy.

**Đối với security:** thay vì chỉ assert một chuỗi log, test **user không đủ quyền không thể gây side effect**, kể cả với cache hit, fallback, exception, async và các entry paths khác. Không dùng custom aspect thứ tự tùy hứng để thay thế cấu hình method security đúng.

### Post-authorization khác Pre-authorization

`@PostAuthorize` cần nhìn result để kiểm tra policy sau invocation. Vị trí và outcome semantics có thể khác `@PreAuthorize`; vì vậy không kết luận chung “security phải trước target trong mọi trường hợp”. Một method có thể chạy xong rồi bị post-authorization từ chối. Nếu method ghi dữ liệu trước đó, transaction và side effects cần được thiết kế theo security policy thực tế.

## 8. @EnableAsync.order không giống transaction/cache advisor order

**Đây là bẫy Spring-specific dễ gặp.** Với:

```java
@EnableAsync(order = Ordered.LOWEST_PRECEDENCE)
```

Thuộc tính `order` của `@EnableAsync` nói về **thứ tự áp dụng `AsyncAnnotationBeanPostProcessor`**, **không trực tiếp cùng ý nghĩa** với `@EnableTransactionManagement(order=...)` hoặc `@EnableCaching(order=...)`.

Tài liệu Spring mô tả mặc định `LOWEST_PRECEDENCE` để Async post-processor chạy sau các post-processors khác và có thể thêm async advisor vào proxy hiện có thay vì gây double-proxy không cần thiết. Mặt khác, `AsyncAnnotationBeanPostProcessor` có xu hướng **đưa async advisor vào trước các advisors đang có** để chuyển sang async execution sớm trong chain.

Do đó **không làm phép so sánh máy móc**:

```text
@Order(10) của custom aspect
so với
@EnableAsync(order=20)
=> custom aspect chắc chắn nằm ngoài async interceptor
```

Kết luận ấy **không được đảm bảo**, vì hai order values ở đây có thể thuộc **khác cơ chế** (advisor precedence so với BeanPostProcessor order). Khi cần biết code audit đang chạy trên **caller thread hay executor thread**, hãy xem actual proxy/advisor structure và test thread names:

- Caller gọi `@Async` method qua Spring proxy.
- Async interceptor có thể submit phần downstream execution sang executor.
- Aspect nằm ngoài async interception có thể ghi log lúc **submit**, trước task completion.
- Aspect nằm trong async execution có thể ghi log trên worker thread và quan sát target execution tại thời điểm khác.
- `CompletableFuture<T>`/`Future<T>` là handle; một normal return của wrapper không đảm bảo computation hoàn tất thành công.
- `@EnableAsync` cần executor/configuration phù hợp. Self-invocation (`this.asyncMethod()`) vẫn bypass proxy trong mặc định proxy mode.

**Không lấy thứ tự log giữa hai threads làm chứng minh thứ tự advisors của cùng synchronous invocation.** Dùng correlation ID, completion synchronization và thực tế order của proxies.

## 9. Bảng phân biệt nhanh các cơ chế đặt order

| Cấu hình | Điều khiển cái gì? | Default/ngữ nghĩa cần nhớ |
| --- | --- | --- |
| `@Order(10)` trên Spring `@Aspect` | Precedence của aspect/advice trong chain | Số nhỏ precedence cao; vào trước, ra sau. |
| `Ordered.getOrder()` trên aspect | Precedence lấy từ bean instance | Cùng quy tắc số nhỏ hơn ưu tiên hơn. |
| `@EnableTransactionManagement(order=...)` | Order của transaction advisor | Mặc định `LOWEST_PRECEDENCE`. |
| `@EnableCaching(order=...)` | Order của cache advisor | Mặc định `LOWEST_PRECEDENCE`. |
| `@EnableMethodSecurity(offset=...)` | Dịch precedence của nhóm method-security interceptors trong những phiên bản có hỗ trợ | Có nhiều advisors khác nhau; xem bản Spring Security và actual advisor order. |
| `@EnableAsync(order=...)` | Thứ tự áp dụng Async annotation **BeanPostProcessor** | **Không đồng nghĩa** advisor order; async advisor có thể được đưa trước existing advisors. |
| `@Transactional(propagation=...)` | Transaction participation/creation semantics | **Không** phải tham số chọn vị trí AOP advisor. |
| `@Order` trên class service thông thường | Order của bean trong một số Spring collections/processes | Không tự ra lệnh mọi interceptors của service xếp theo số đó. |

Khi có nhiều proxies lồng nhau hoặc post-processors riêng, không ép tất cả order settings thành một phép sort toàn cục. Quan sát actual instance được inject và loại advisor/proxy chain.

## 10. Ví dụ chạy được: custom aspects nằm hai bên transaction advisor

Demo Spring Framework dưới đây **chỉ dùng transaction infrastructure** để minh họa thứ tự một cách có thể lặp lại. Nó không cần database: một `TraceTransactionManager` giả lập ranh giới transaction và **in sự kiện begin/commit/rollback**. Manager này **không quản lý tài nguyên thật** và tuyệt đối **không dùng production**; mục đích là quan sát thứ tự của Spring transaction interceptor.

Cấu hình:

- Custom `OuterAuditAspect`: `@Order(10)`.
- `@EnableTransactionManagement(order=20)`: transaction advisor.
- Custom `InnerMetricAspect`: `@Order(30)`.
- `WorkService` có hai `@Transactional` methods: một return bình thường, một throw `IllegalStateException`.
- Tất cả beans do Spring tạo và method calls đi qua class-based proxy.

Môi trường: **Java 17+**, **Spring Framework 6.2.19**, **AspectJ Weaver 1.9.25.1**, Gradle. Đây là Spring Framework thuần, không phụ thuộc Boot auto-configuration.

### 10.1. Cấu trúc

```text
spring-advisor-order-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/advisororder/
    ├── AppConfig.java
    ├── WorkService.java
    ├── OuterAuditAspect.java
    ├── InnerMetricAspect.java
    ├── TraceTransactionManager.java
    └── Main.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'spring-advisor-order-demo'
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
    implementation 'org.springframework:spring-tx:6.2.19'
    implementation 'org.aspectj:aspectjweaver:1.9.25.1'
}

application {
    mainClass = 'com.example.advisororder.Main'
}
```

Spring `spring-tx` cung cấp `@Transactional`, `@EnableTransactionManagement` và transaction interceptor, `spring-context` cung cấp context/Java config. `aspectjweaver` dùng cho Spring `@AspectJ` pointcut parsing, **không** tự bật AspectJ weaving.

### 10.2. Target bean với hai @Transactional methods

**`WorkService.java`**

```java
package com.example.advisororder;

import org.springframework.transaction.annotation.Transactional;

public class WorkService {
    @Transactional
    public String ok(String id) {
        System.out.println("target:ok");
        return "done:" + id;
    }

    @Transactional
    public String fail() {
        System.out.println("target:fail");
        throw new IllegalStateException("boom");
    }
}
```

Target class/methods không final, methods public và không có self-invocation. `@Transactional` ở từng method bảo đảm transaction advisor có lý do match cả hai; custom aspects cũng cùng match.

### 10.3. Transaction manager chỉ ghi sự kiện, không có DB

**`TraceTransactionManager.java`**

```java
package com.example.advisororder;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

public class TraceTransactionManager
        extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(
            Object transaction, TransactionDefinition definition) {
        System.out.println("tx:begin");
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        System.out.println("tx:commit");
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        System.out.println("tx:rollback");
    }
}
```

`AbstractPlatformTransactionManager` đảm nhiệm flow phân định transaction; bốn hooks được implement để tạo transaction object tối giản và ghi sự kiện.

**Giới hạn có chủ đích:** manager không bind JDBC connection hoặc resource vào thread, không thực hiện database commit/rollback thật, không đại diện cho khả năng rollback của một storage engine. Vì `doGetTransaction()` tạo object mới và không báo một transaction có sẵn, demo chỉ nhắm **mỗi external invocation độc lập**. Không dùng implementation này để xác nhận propagation với DB, nested transactions, isolation, read-only semantics hoặc recovery.

Trong ứng dụng thật dùng `DataSourceTransactionManager`/`JdbcTransactionManager`, `JpaTransactionManager` hoặc manager thích hợp và xác nhận database state.

### 10.4. Audit aspect precedence cao, nằm ngoài transaction

**`OuterAuditAspect.java`**

```java
package com.example.advisororder;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(10)
public class OuterAuditAspect {
    @Around("execution(public * com.example.advisororder.WorkService.*(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getName();
        System.out.println("audit:enter:" + method);
        try {
            Object result = pjp.proceed();
            System.out.println("audit:return:" + method);
            return result;
        } catch (Throwable error) {
            System.out.println("audit:throw:" + method);
            throw error;
        }
    }
}
```

`@Order(10)` nhỏ hơn transaction advisor order 20: audit enter trước `tx:begin`; khi `proceed()` kết thúc bình thường nó sẽ in `audit:return` sau `tx:commit` trong setup này. `throw error` giữ original exception.

### 10.5. Metrics aspect precedence thấp, nằm trong transaction

**`InnerMetricAspect.java`**

```java
package com.example.advisororder;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(30)
public class InnerMetricAspect {
    @Around("execution(public * com.example.advisororder.WorkService.*(..))")
    public Object metric(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getName();
        System.out.println("metric:enter:" + method);
        try {
            Object result = pjp.proceed();
            System.out.println("metric:return:" + method);
            return result;
        } catch (Throwable error) {
            System.out.println("metric:throw:" + method);
            throw error;
        }
    }
}
```

Ở đây, `metric:return` diễn ra **trước `tx:commit`**. Vì thế nếu commit fail, một event “target returned” trong metrics không đủ để kết luận transaction hoàn tất thành công. Aspect này cố ý chỉ quan sát và bảo toàn result/exception, không short-circuit hay retry.

### 10.6. AppConfig: bật cả Spring AOP và transaction management

**`AppConfig.java`**

```java
package com.example.advisororder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableTransactionManagement(order = 20)
public class AppConfig {
    @Bean
    WorkService workService() {
        return new WorkService();
    }

    @Bean
    OuterAuditAspect outerAuditAspect() {
        return new OuterAuditAspect();
    }

    @Bean
    InnerMetricAspect innerMetricAspect() {
        return new InnerMetricAspect();
    }

    @Bean
    PlatformTransactionManager transactionManager() {
        return new TraceTransactionManager();
    }
}
```

Không cần `@ComponentScan` vì các beans được tạo bằng `@Bean`. Tên `transactionManager` rõ ràng giúp Spring tìm bean manager cho declarative transactions. `@EnableAspectJAutoProxy` và `@EnableTransactionManagement` tham gia hạ tầng proxy/advisor của cùng context; không có lời gọi self-invocation.

### 10.7. Main: quan sát call path thực qua Spring proxy

**`Main.java`**

```java
package com.example.advisororder;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            WorkService service = context.getBean(WorkService.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(service));
            System.out.println("case=ok");
            System.out.println("result=" + service.ok("A"));

            System.out.println("case=fail");
            try {
                service.fail();
            } catch (IllegalStateException error) {
                System.out.println("caught=" + error.getMessage());
            }
        }
    }
}
```

Chạy ở root project với JDK 17+ và Gradle đã cài:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper: `./gradlew run --quiet` (Linux/macOS) hoặc `gradlew.bat run --quiet` (Windows).

**Các dòng `System.out` của demo theo thứ tự** (không tính logging ngoài của JVM/Gradle/Spring):

```text
proxy=true
case=ok
audit:enter:ok
tx:begin
metric:enter:ok
target:ok
metric:return:ok
tx:commit
audit:return:ok
result=done:A
case=fail
audit:enter:fail
tx:begin
metric:enter:fail
target:fail
metric:throw:fail
tx:rollback
audit:throw:fail
caught=boom
```

### 10.8. Chứng minh ba vị trí trong một invocation

Trường hợp bình thường:

1. `audit:enter:ok` trước `tx:begin` vì aspect `@Order(10)` nằm **ngoài** transaction advisor order 20.
2. `tx:begin` trước `metric:enter:ok` vì transaction advisor precedence cao hơn metric `@Order(30)`.
3. `metric:enter:ok` trước `target:ok` vì target nằm sâu nhất.
4. `metric:return:ok` sau target nhưng **trước commit hook**: ở thời điểm này transaction interceptor chưa hoàn tất.
5. `tx:commit` in sau khi downstream return bình thường.
6. `audit:return:ok` xuất hiện sau khi transaction interceptor `proceed()` hoàn tất.
7. Caller nhận `result=done:A`.

Trường hợp exception:

1. Vào vẫn là `audit → tx:begin → metric → target`.
2. `target:fail` ném `IllegalStateException`.
3. `metric:throw:fail` thấy exception trước khi transaction interceptor xử lý rollback.
4. `tx:rollback` được gọi do exception runtime (và default rollback rules).
5. `audit:throw:fail` thấy exceptional outcome của transaction-wrapped invocation.
6. Caller catch `IllegalStateException("boom")`.

**Ranh giới của demo:** `tx:commit`/`tx:rollback` là các sự kiện do **dummy transaction manager** in ra, không chứng minh DB đã commit/rollback. Điều minh họa là **ordering thực thi advisor**. Đối với dữ liệu thật, dùng manager thật và integration tests với database.

### 10.9. Nếu bỏ @EnableTransactionManagement(order=20) thì sao?

Mặc định transaction advisor thường dùng `Ordered.LOWEST_PRECEDENCE`. Khi đó `@Order(10)` và `@Order(30)` của hai custom aspects có precedence cao hơn transaction advisor theo numeric order: cả hai có thể **bao ngoài transaction interception**, nên `metric:return` có thể xuất hiện **sau commit hook**, không còn cùng chuỗi như mục 10.7.

Đó là điểm bài học: **không tự suy “transaction luôn bọc mọi custom aspect”**. Nếu thứ tự phải là một phần của contract, cấu hình `order` có chủ đích và test.

Không thay phiên bản `@EnableTransactionManagement(order=20)` trong code demo mà vẫn dùng y nguyên expected stdout; khi thay order cần tính lại và kiểm chứng.

## 11. Cách kiểm tra các advisors thực sự nằm trên proxy

### 11.1. AopUtils chứng minh có proxy, chưa chứng minh order

```java
Object bean = context.getBean("workService");
System.out.println(AopUtils.isAopProxy(bean));
```

Đây là đoạn minh họa dựa vào Spring context của demo. Kết quả true xác nhận bean được proxied nhưng **không chỉ ra** advisor nào đứng trước/đứng sau.

### 11.2. Inspect Advised.getAdvisors()

Nhiều Spring AOP proxies implements `org.springframework.aop.framework.Advised`. Có thể dùng:

```java
Object bean = context.getBean("workService");

if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName()
                + " -> " + advisor.getAdvice().getClass().getName());
    }
}
```

Đây là **đoạn chẩn đoán minh họa**, không thêm vào `Main` nếu muốn giữ stdout ở mục 10.7. `Advised.getAdvisors()` hiển thị advisors của proxy đang inspect; nếu có nested proxies, phải lần theo cấu trúc tương ứng một cách an toàn thay vì suy mọi advisor luôn ở một danh sách.

Lưu ý:

- `getAdvisors()` chỉ cho bạn danh sách thành phần được lắp vào proxy; các pointcut có thể có **runtime matching conditions**.
- Một interceptor có thể short-circuit nên advisor phía trong **không nhận invocation**, dù nó có trong danh sách.
- `Advised` là API infrastructure; không sửa danh sách advisors lúc production chạy chỉ để ép order.
- Một số proxy config bị `frozen`/không cho mutate; đọc để chẩn đoán thường thích hợp hơn can thiệp.
- Bean khác hoặc child `ApplicationContext` có thể dùng advisor infrastructure khác; phải inspect **đúng bean mà caller được inject**.

### 11.3. Dùng event recorder thay println trong test

Có thể thay `System.out` bằng một Spring bean `EventRecorder`:

```java
public final class EventRecorder {
    private final java.util.List<String> events =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public void record(String event) {
        events.add(event);
    }

    public java.util.List<String> snapshot() {
        return java.util.List.copyOf(events);
    }
}
```

Đây là **mã minh họa riêng**, muốn tích hợp vào demo phải tạo `@Bean EventRecorder`, inject vào cả hai aspects, target và dummy manager. Thứ tự đồng bộ có thể assert theo `List<String>`; không trộn events từ nhiều threads/request vào cùng một thứ tự toàn cục rồi suy advisor precedence.

## 12. Những lỗi thiết kế thường gặp

### 12.1. “@Order(0) luôn là lớp ngoài mọi thứ”

Sai. Một số advisor infrastructure có `HIGHEST_PRECEDENCE`/numeric order nhỏ hơn 0; async BeanPostProcessor có cơ chế thêm advisor khác; nhiều proxies có thể được lồng. `@Order(0)` không tự bảo đảm custom aspect bao hết Spring Security/cache/transaction ở mọi môi trường.

**Đúng:** dùng order cấu hình thực, inspect advisors/proxies và test.

### 12.2. Dùng transaction order để sửa lỗi self-invocation

`@Transactional` trên `inner()` bị bypass nếu `outer()` trên cùng target gọi `this.inner()`. Đổi `@EnableTransactionManagement(order=5)` không tạo proxy hop. Cần tách collaborator bean và gọi qua injected Spring proxy hoặc chọn operation boundary phù hợp.

### 12.3. Không bật tính năng nhưng mong nó xuất hiện trong chain

`@Aspect` không tự bật `@Transactional`, `@Cacheable`, `@Async` hoặc Method Security. Mỗi feature cần activation, required dependencies và infrastructure beans riêng.

### 12.4. Tưởng @Order điều khiển commit

`@Order` điều khiển tương quan interceptors; commit/rollback còn phụ thuộc transaction semantics, propagation, rollback rules, manager implementation, exception/outcome. Order không thể thay business transaction contract.

### 12.5. Nhầm cache hit với thiếu advice

Khi cache advisor ở ngoài, nó có thể trả từ cache và không cho custom aspect bên trong chạy. Cần đếm proxy invocation, cache hit và target execution **tách riêng** để hiểu metrics.

### 12.6. Security advisor nằm sâu trong @Around short-circuit

Một custom `@Around` trả sớm mà không `proceed()` có thể bỏ qua downstream method security check. Nếu đây không phải behavior có chủ đích, đó là vấn đề bảo mật nghiêm trọng; đừng chọn fallback “ok” cho exception authorization.

### 12.7. @EnableAsync(order=...) bị dùng như @Order(aspect)

Thuộc tính `order` ở `@EnableAsync` là BeanPostProcessor order. So nó trực tiếp với `@Order` trên custom aspect để dự đoán thread thực thi là sai. Kiểm tra `AsyncAnnotationBeanPostProcessor`, proxy chain và behavior thực.

### 12.8. Hai advisors cùng LOWEST_PRECEDENCE

Đừng khẳng định transaction luôn chạy trước cache hoặc ngược lại chỉ từ hai default order values bằng nhau. Khi behavior phụ thuộc order, cấu hình rõ, regression test, kiểm tra context đã kích hoạt đúng hai features.

### 12.9. Gọi getter của advisor là “bằng chứng đã advice”

`getAdvisors()` cho thấy proxy có advisor; không đảm bảo một runtime call match dynamic pointcut hoặc tới được advisor sâu trong chain. Cần event traces kèm method/args/outcome.

### 12.10. Một thành công giả do outer advice nuốt exception

```java
@Around("execution(* com.example.advisororder.WorkService.*(..))")
public Object unsafe(ProceedingJoinPoint pjp) {
    try {
        return pjp.proceed();
    } catch (Throwable error) {
        return null; // có thể che rollback/security/caching failure
    }
}
```

Đây là **anti-pattern minh họa**, không có trong demo runnable. Nếu inner transaction advisor đã rollback, outer advice trả `null` có thể làm caller tưởng normal result. Nếu nó bao trong transaction, outcome mà transaction advisor nhìn thấy có thể thành normal return. Không suy hệ quả commit một cách tuyệt đối mà phải xét vị trí advisor và test transaction thật.

## 13. Quy trình debug nhanh trên hệ thống Spring thực tế

### Bước 1 — Chốt đúng entry method và call path

Ghi lại Spring bean nào được inject vào caller, method signature, target annotation và nơi lời gọi bắt đầu. Xác nhận không dùng raw object từ `new` hoặc self-invocation.

### Bước 2 — Liệt kê các feature thực sự bật

Xem custom `@Aspect` beans, `@EnableAspectJAutoProxy`/Boot AOP, `@EnableTransactionManagement`, `@EnableCaching`, `@EnableAsync`, `@EnableMethodSecurity` và beans manager/executor/security config tương ứng. Đừng coi annotation trong source là bằng chứng advisor đã được đăng ký.

### Bước 3 — Ghi rõ mỗi cơ chế order

Tạo bảng cho project: aspect `@Order`, transaction advisor `order`, cache advisor `order`, security interceptors/offset, async BeanPostProcessor/advisor placement. Khi có default LOWEST value, đừng kết luận thứ tự tie.

### Bước 4 — Quan sát thứ tự thực tế ở một invocation

Instrument `enter`, `target`, `return/throw`, `commit/rollback` bằng test recorder. Với async, đặt correlation ID và đồng bộ futures khi assert. Không dùng tổng thứ tự logs đa luồng làm một single call chain.

### Bước 5 — Test short-circuit

Kiểm tra cache hit, denied permission, `@Around` skip `proceed()`, exception trong target và exception trong commit. Mỗi nhánh có thể dừng ở advisor khác nhau.

### Bước 6 — Kiểm chứng outcome nghiệp vụ

Nếu cần đảm bảo transaction, nhìn DB state và synchronization callbacks; nếu cần đảm bảo authorization, chứng minh unauthorized caller không gây side effects; nếu cần cache correctness, test stale cache, eviction, concurrent calls.

### Bước 7 — Lock behavior bằng integration test

Viết test chạy đúng Spring/Boot/Security versions và proxy mode production. Tránh hard-code một “thứ tự chung” từ tài liệu vào tất cả dự án không có cùng infrastructure.

## 14. Test case nên có

| Case | Cần assert | Tại sao |
| --- | --- | --- |
| Custom outer aspect, transaction advisor, custom inner aspect | `outer.enter → tx.begin → inner.enter → target → inner.exit → tx.commit → outer.exit` | Xác nhận precedence và nesting. |
| Target throw RuntimeException | Inner throw → rollback → outer throw → caller nhận lỗi | Xác nhận exceptional flow và default rollback behavior. |
| Target return nhưng commit fail | Outer nhìn exception sau tx commit attempt; inner đã ghi return | Tránh nhầm target success với committed transaction. |
| Cache miss | Target chạy, cache populate theo config | Không nhầm cache advisor với custom aspect. |
| Cache hit | Target không chạy; custom aspect outer/inner khác nhau | Đếm đúng proxy calls so với target executions. |
| Method security deny | Target không có side effect ngoài policy | Chứng minh security advisor không bị custom short-circuit bỏ qua. |
| Async completion | Caller thread vs worker thread, future failure | Phân biệt advisor order và BeanPostProcessor/threading. |
| Self-invocation | Inner method advice không tự xuất hiện | Khẳng định Spring proxy boundary. |
| Two aspects same order | Không assert ordering không có contract | Tránh test brittle. |
| JDK vs CGLIB proxy | Method được expose/override được | Không đổ lỗi cho ordering khi interception không xảy ra. |

### Regression test đơn giản cho demo

Một integration test có thể thay console prints bằng recorder và assert ordered events sau khi gọi `ok("A")`:

```java
java.util.List<String> expected = java.util.List.of(
    "audit:enter:ok",
    "tx:begin",
    "metric:enter:ok",
    "target:ok",
    "metric:return:ok",
    "tx:commit",
    "audit:return:ok"
);
```

Đây là **đoạn dữ liệu test minh họa**, cần tích hợp `EventRecorder` vào các components để lấy actual events. Đừng cố assert danh sách này từ `System.out` khi app có logging đa luồng khác xen vào.

Quan trọng hơn, với transaction manager thật, test outcome database/state, không dùng `tx:commit` từ dummy manager để tuyên bố business correctness.

## 15. Spring Boot và việc đặt order đúng phạm vi

Với Spring Boot đã có Spring AOP auto-configuration và các starters phù hợp:

- `@Aspect` tự viết vẫn cần trở thành Spring bean qua `@Component`/`@Bean`.
- `@Order` vẫn dùng để xác định relative precedence giữa custom aspects và advisors khi cùng chain.
- `@Transactional`, `@Cacheable`, `@Async`, method security có những kích hoạt/cấu hình riêng; Boot không tự kích hoạt mọi feature chỉ vì AOP starter xuất hiện.
- Không ép phiên bản Spring Framework trong Boot project trái Boot BOM. Kết quả advisors/proxy có thể chịu ảnh hưởng auto-configuration order và các BeanPostProcessors khác.
- Spring Boot 3.x thường dùng `spring-boot-starter-aop`, Spring Boot 4.x dùng `spring-boot-starter-aspectj` nếu cần starter AspectJ phù hợp. Hai lựa chọn này là **theo major version**, không chép đồng thời.
- `spring.aop.proxy-target-class` ảnh hưởng việc dùng class proxy hay interface proxy. `final` class/method và method interface exposure cần kiểm tra, nhưng chuyển proxy mode **không chữa self-invocation**.
- Với Spring Security, đọc tài liệu tương ứng đúng version của dự án; `@EnableMethodSecurity(offset=...)` là policy order của nhóm Security advisors ở các phiên bản hỗ trợ, không phải “cách bật AOP” chung.

**Điểm quan trọng:** các ví dụ `@EnableTransactionManagement(order=20)`/`@Order(10)`/`@Order(30)` trong README nhằm minh họa một cấu hình cụ thể. Trong production, không sao chép các số ấy mà thiếu kiểm tra tương quan với advisor của thư viện đang dùng.

## 16. Checklist áp dụng

- [ ] Đã xác nhận custom aspects và target beans đều do Spring quản lý, AOP proxy active?
- [ ] Các transaction/cache/security/async features được **enable độc lập** và có manager/executor/config phù hợp?
- [ ] Biết rõ từng `order` thuộc **aspect, advisor hay BeanPostProcessor**?
- [ ] `@Order` trên custom aspects có numeric precedence riêng, không tạo tie với advisor cần ordering?
- [ ] Hiểu rằng số nhỏ ưu tiên cao, **vào trước, ra sau** ở nested interceptor wrappers?
- [ ] Đã đọc default `LOWEST_PRECEDENCE` của transaction/cache thay vì giả sử transaction luôn ngoài?
- [ ] Không so trực tiếp `@EnableAsync.order` với aspect `@Order`?
- [ ] Khi có method security, đã kiểm tra advisors/offset trên Spring Security version thực tế?
- [ ] Không suy normal return của inner aspect tương đương transaction committed?
- [ ] Đã xác định cache hits/security denies có thể short-circuit và bỏ qua advisors sâu trong chain?
- [ ] `@Around` được kiểm tra không tự skip `proceed()` hoặc nuốt security/transaction exceptions ngoài policy?
- [ ] Đã xét self-invocation, object `new`, method `final`, JDK/CGLIB proxy khi missing advice?
- [ ] Chẩn đoán actual `Advised.getAdvisors()`/event recorder thay vì chỉ `AopUtils.isAopProxy=true`?
- [ ] Có integration tests cho success, failure, commit failure, rollback, cache hit, access deny và async completion nếu ứng dụng dùng chúng?
- [ ] Không biến thứ tự log đa luồng thành lời khẳng định về thứ tự advisor trong một invocation?
- [ ] Thay đổi order/configuration được coi là thay đổi behavior có regression tests?

**Ghi nhớ:** Trong Spring AOP, **thứ tự giữa custom `@Aspect` và infrastructure advisors có thể thay đổi điều mà mỗi advice thực sự quan sát**: target return, transaction commit/rollback, cache hit, security deny hay async scheduling. `@Order` số nhỏ có precedence cao, nhưng **không phải mọi cấu hình `order` đều là cùng một loại số advisor**; đặc biệt `@EnableAsync.order` thuộc BeanPostProcessor. Cách đúng là đặt order có chủ đích ở feature hỗ trợ, inspect Spring proxy/advisors, và khóa behavior bằng integration tests.

## Tài liệu chính thức

- [Spring Framework 6.2 — Advice Ordering trong @AspectJ](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework 6.2 — Advising Transactional Operations](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/applying-more-than-just-tx-advice.html)
- [Spring Framework 6.2 — @Transactional: proxy mode, order và rollback](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html)
- [Spring Framework 6.2 — Annotation-driven Caching: @EnableCaching và advisor order](https://docs.spring.io/spring-framework/reference/6.2/integration/cache/annotations.html)
- [Spring Framework 6.2 — AsyncAnnotationBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/AsyncAnnotationBeanPostProcessor.html)
- [Spring Framework 6.2 — @EnableAsync và BeanPostProcessor order](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Spring Security — Method Security: advisor ordering và offset](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
- [Spring Framework 6.2 — Proxying Mechanisms và self-invocation](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-ordering-010">Bảng vết thực thi để xác nhận thứ tự khi thành công và khi lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng vết thực thi để xác nhận thứ tự khi thành công và khi lỗi trong Spring AOP

## 1. Mục tiêu của bảng vết

Khi có nhiều Spring `@Aspect` cùng can thiệp vào một method của Spring bean, chỉ nhìn tên `@Before`, `@After`, `@AfterReturning` hoặc `@AfterThrowing` chưa đủ để biết **dòng nào xuất hiện trước**. Một phương thức có thể đi qua nhiều lớp interceptor; thứ tự ở **chiều vào** khác thứ tự ở **chiều thoát**, và khi method ném exception thì một số callback thành công hoàn toàn không chạy.

**Bảng vết thực thi** (*execution trace table*) là cách ghi lại từng mốc theo thứ tự quan sát được, ví dụ:

~~~text
Caller
  -> Spring AOP proxy
       -> OuterAspect @Around: enter
            -> InnerAspect @Around: enter
                 -> OutcomeAspect @AfterReturning / @AfterThrowing
                 -> method đích
            <- InnerAspect @Around: finally
       <- OuterAspect @Around: finally
  <- Caller nhận giá trị hoặc ngoại lệ
~~~

Lưu ý hình trên là **sơ đồ tầng**, không phải thứ tự log: callback `@AfterReturning` hoặc `@AfterThrowing` chạy **sau khi method đích kết thúc**, trước khi chuỗi trả quyền điều khiển về các around advice ở bên ngoài.

Bài học này tập trung **riêng Spring Aspect/Spring AOP proxy-based**, trong nhóm **Thứ tự thực thi**. Người học sẽ:

1. Xây dựng trace với cột **bước, tầng, sự kiện, nguyên nhân, nhánh kết quả**.
2. Hiểu `@Order`/`Ordered`: **số nhỏ hơn = precedence cao hơn**, vào trước và thoát sau.
3. Lập bảng riêng cho **normal return** và **exceptional exit**.
4. Phân biệt `@AfterReturning`, `@AfterThrowing`, `@After` và `finally` trong `@Around`.
5. Viết ví dụ **Maven + Java 17+** có ba Spring aspect được sắp xếp tường minh, collector sự kiện và assertions tự kiểm tra.
6. Debug các trường hợp log thiếu, log lặp, advice kết thúc sớm, tự gọi `this.method()` hoặc exception bị xử lý bên trong method.

**Mục tiêu không phải** học lý thuyết Aspect tổng quát mà là xác minh **trình tự thực tế trên Spring AOP proxy của một Spring-managed bean**.

## 2. Luật đọc bảng vết của Spring

Trước khi ghi bảng, phải xác định **ai là caller và caller giữ object nào**. Nếu caller gọi một plain Java object do `new` tạo ngoài Spring, hoặc một lời gọi nội bộ `this.method()` của target bypass proxy, advice có thể hoàn toàn không xuất hiện.

Giả sử ba aspect có thứ tự:

| Aspect | Cách khai báo | Thứ tự/precedence | Vai trò |
| --- | --- | --- | --- |
| `OuterTraceAspect` | `@Aspect @Order(10)` | **Cao nhất** trong ba aspect | `@Around`, log enter, after, finally |
| `InnerTraceAspect` | `@Aspect @Order(20)` | Ở giữa | `@Around`, log enter, after, finally |
| `OutcomeAspect` | `@Aspect @Order(30)` | Thấp nhất | `@AfterReturning` khi thành công; `@AfterThrowing` khi lỗi |

Khi **cả ba pointcut đều match cùng invocation**:

- 10 được vào trước 20; 20 được vào trước 30.
- Outer(10) bao ngoài Inner(20), còn Outcome(30) nằm sâu hơn.
- Khi result hoặc exception đi ngược ra, Outcome(30) xử lý nhánh phù hợp trước.
- Sau đó Inner(20) chạy phần code ở phía sau `proceed()` nếu thành công, rồi `finally`.
- Cuối cùng Outer(10) chạy phần code sau `proceed()` nếu thành công, rồi `finally`.
- Caller chỉ nhận result/exception sau khi các interceptor đã đi qua hoàn tất.

**Điểm dễ sai:** “10 ưu tiên cao hơn 20” không nghĩa rằng **mọi câu lệnh của aspect 10** xuất hiện trước **mọi câu lệnh của aspect 20**. Về cấu trúc, aspect 10 là **wrapper ngoài**, nên phần sau của nó diễn ra **sau** aspect 20.

## 3. Từ interceptor chain đến bảng có thể kiểm chứng

Mô hình một Spring proxy:

~~~text
                       ====== CHIỀU VÀO ======>
Caller -> [A: order 10] -> [B: order 20] -> [C: order 30] -> Target
Caller <- [A: finally] <- [B: finally] <- [C: outcome] <- Target result/throw
                       <====== CHIỀU RA =======
~~~

Trong ví dụ, C không dùng `@Around`; nó có hai lời advice **chọn một nhánh**:

- `@AfterReturning` khi target execution trả về bình thường.
- `@AfterThrowing` khi target execution ném exception thoát ra.

Vì C là interceptor trong hơn B và A, cả callback C thành công lẫn callback C thất bại đều xuất hiện **trước** phần thoát của B và A.

### 3.1. Bảng vết thành công lý tưởng

| Bước | Vị trí | Sự kiện | Vì sao |
| ---: | --- | --- | --- |
| 1 | A(10) | `A: enter` | Spring proxy đi vào aspect ưu tiên cao |
| 2 | B(20) | `B: enter` | A gọi `proceed()` để vào tầng B |
| 3 | Target | `target: begin` | B gọi `proceed()`, chuỗi tới method |
| 4 | Target | `target: return OK` | Method hoàn tất bình thường |
| 5 | C(30) | `C: returned OK` | `@AfterReturning` nhận giá trị String |
| 6 | B(20) | `B: after` | `proceed()` của B đã trả bình thường |
| 7 | B(20) | `B: finally` | Java finally của B được thực thi |
| 8 | A(10) | `A: after` | `proceed()` của A đã trả bình thường |
| 9 | A(10) | `A: finally` | Java finally của A được thực thi |
| 10 | Caller | `caller result: OK` | Lời gọi proxy đã trả kết quả |

Hai dòng “after” trong bảng là **code đặt sau `proceed()` trong `@Around`**, không phải annotation `@After`. Nếu không phân biệt, người đọc dễ tưởng Spring đang sắp xếp advice sai.

### 3.2. Bảng vết khi target ném exception

| Bước | Vị trí | Sự kiện | Vì sao |
| ---: | --- | --- | --- |
| 1 | A(10) | `A: enter` | Aspect ngoài nhận lời gọi |
| 2 | B(20) | `B: enter` | A đã proceed |
| 3 | Target | `target: begin` | B đã proceed |
| 4 | Target | `target: throw boom` | Method ném IllegalStateException |
| 5 | C(30) | `C: throwing IllegalStateException` | `@AfterThrowing` match exception |
| 6 | B(20) | `B: finally` | `proceed()` của B ném lỗi; finally vẫn chạy |
| 7 | A(10) | `A: finally` | `proceed()` của A ném lỗi; finally vẫn chạy |
| 8 | Caller | `caller caught: boom` | Caller bắt exception gốc |

**Không có `B: after`, `A: after` hoặc `C: returned OK`** vì `proceed()` không trả bình thường. Thay vào đó, finally của những around advice **đã được đi vào** vẫn chạy khi stack unwind.

**Đây là khác biệt trung tâm cần có trong trace:** Khi test chỉ xác minh rằng “A enter trước B enter”, bạn chưa kiểm tra được trật tự ở nhánh lỗi. Phải assert riêng **chuỗi hoàn chỉnh** của cả hai nhánh.

## 4. Những điều kiện để trace trên thật sự đúng

Một bảng vết đáng tin cần ghi rõ các tiền đề:

1. `OuterTraceAspect`, `InnerTraceAspect` và `OutcomeAspect` đều là **Spring-managed aspect beans**.
2. Spring Framework có `@EnableAspectJAutoProxy` hoặc hạ tầng AOP tương đương được bật.
3. Target là **Spring bean** có Spring AOP proxy, không phải object tự tạo bằng `new`.
4. Cả ba pointcut đều match đúng method được caller gọi.
5. Các aspects có order **khác nhau**: 10, 20, 30.
6. Cả hai `@Around` gọi `ProceedingJoinPoint.proceed()` **đúng một lần**.
7. Advice không làm biến đổi result/exception, không shortcut và không tự ném lỗi logging.
8. Project minh họa không đăng ký advisor transaction/cache/security khác cho target.
9. Mọi lời gọi trong demo là **đồng bộ** và thực hiện trên cùng thread.
10. Sự kiện được ghi theo đúng thứ tự lời gọi đồng bộ, không dùng asynchronous logging làm thay đổi thứ tự hiển thị.

Nếu thay đổi tiền đề, bảng vết có thể cần thay đổi. Đừng tuyên bố một thứ tự cố định khi các aspect không khai báo `@Order`/`Ordered` hoặc một advisor phía ngoài có thể kết thúc sớm.

## 5. Project Maven/Java 17+ chạy được

Ta xây dựng ứng dụng Spring Framework nhỏ để **quan sát** và **xác nhận bằng code** thứ tự nêu ở hai bảng.

### 5.1. Cấu trúc thư mục

~~~text
spring-aop-trace-demo/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── TraceEvents.java
    ├── TransferService.java
    ├── OuterTraceAspect.java
    ├── InnerTraceAspect.java
    ├── OutcomeAspect.java
    └── Main.java
~~~

Môi trường minh họa: **Java 17+**, Maven, **Spring Framework 6.2.19**, **AspectJ Weaver 1.9.25.1**. Spring AOP sử dụng AspectJ annotation syntax/pointcut expression nhưng **không dùng compile-time hoặc load-time bytecode weaving** trong ví dụ.

### 5.2. Maven `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-aop-trace-demo</artifactId>
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

Spring Framework BOM giữ các module Spring cùng phiên bản. Với ứng dụng Spring Boot, sử dụng dependency management của Boot thay vì buộc một bộ version lấy từ project ví dụ.

### 5.3. Bộ ghi sự kiện `TraceEvents.java`

~~~java
package example;

import java.util.ArrayList;
import java.util.List;

public final class TraceEvents {
    private final List<String> events = new ArrayList<>();

    public void record(String event) {
        events.add(event);
        System.out.println(event);
    }

    public List<String> drain() {
        List<String> result = List.copyOf(events);
        events.clear();
        return result;
    }
}
~~~

Class này lưu lại đúng chuỗi events rồi in ra console. `Main` sẽ lấy snapshot bằng `drain()` và dùng `List.equals(...)` để kiểm tra **thứ tự**, không chỉ kiểm tra sự kiện có tồn tại.

**Phạm vi:** Demo gọi đồng bộ, một thread, một invocation tại một thời điểm; `ArrayList` và `drain()` **không an toàn** để trộn nhiều request concurrent. Với production/integration tests song song nên dùng collector tách theo invocation ID (hoặc thread-local scope được cleanup đúng cách), và chỉ assert trên event sequence thuộc **cùng một invocation**. Một global `Collections.synchronizedList` đơn độc cũng không giải quyết vấn đề events từ nhiều invocation xen kẽ.

### 5.4. Target `TransferService.java`

~~~java
package example;

public class TransferService {

    private final TraceEvents trace;

    public TransferService(TraceEvents trace) {
        this.trace = trace;
    }

    public String transfer(boolean fail) {
        trace.record("target: begin");

        if (fail) {
            trace.record("target: throw boom");
            throw new IllegalStateException("boom");
        }

        trace.record("target: return OK");
        return "OK";
    }
}
~~~

Một target duy nhất có hai nhánh:

- `transfer(false)` trả `String "OK"`;
- `transfer(true)` ném `IllegalStateException("boom")`.

Dữ liệu nghiệp vụ và lời exception được giữ nguyên trong mọi aspect. Không có sleep, random, network call hay concurrency để stdout bị nondeterministic.

### 5.5. Aspect ngoài `OuterTraceAspect.java`

~~~java
package example;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(10)
public class OuterTraceAspect {

    private final TraceEvents trace;

    public OuterTraceAspect(TraceEvents trace) {
        this.trace = trace;
    }

    @Around("execution(public * example.TransferService.transfer(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        trace.record("A: enter");

        try {
            Object result = pjp.proceed();
            trace.record("A: after");
            return result;
        } finally {
            trace.record("A: finally");
        }
    }
}
~~~

Giải thích:

1. Order 10 là precedence cao nhất trong ba aspect của demo.
2. `A: enter` được in **trước** việc gọi `proceed()`.
3. `proceed()` tiến sang những advisors phía trong, không nhất thiết gọi target ngay lập tức.
4. `A: after` được in **chỉ khi proceed trả bình thường**.
5. `A: finally` được in ở cả success/failure khi around advice đã được vào.
6. Method dùng `return result` để trả nguyên giá trị, không biến đổi.
7. Method khai báo `throws Throwable` và không nuốt exception của target.

Khi exception thoát từ B/C/target, lệnh `trace.record("A: after")` bị bỏ qua, nhưng Java `finally` vẫn được thực thi.

### 5.6. Aspect giữa `InnerTraceAspect.java`

~~~java
package example;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(20)
public class InnerTraceAspect {

    private final TraceEvents trace;

    public InnerTraceAspect(TraceEvents trace) {
        this.trace = trace;
    }

    @Around("execution(public * example.TransferService.transfer(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        trace.record("B: enter");

        try {
            Object result = pjp.proceed();
            trace.record("B: after");
            return result;
        } finally {
            trace.record("B: finally");
        }
    }
}
~~~

Aspect B có cấu trúc giống A, chỉ khác order 20. Vì `20 > 10` nên B được A **bao bên ngoài**. Cách viết rõ ràng giúp thấy:

- Trên đường vào: A enter rồi B enter.
- Trên đường ra thành công: B after/finally rồi A after/finally.
- Trên đường ra thất bại: B finally rồi A finally.

**Không** nhầm nhãn code `B: after` với một lời advice `@After`. Đây là một câu lệnh trong `@Around` chạy sau `proceed()`.

### 5.7. Aspect phân biệt outcome `OutcomeAspect.java`

~~~java
package example;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(30)
public class OutcomeAspect {

    private final TraceEvents trace;

    public OutcomeAspect(TraceEvents trace) {
        this.trace = trace;
    }

    @AfterReturning(
        pointcut = "execution(public * example.TransferService.transfer(..))",
        returning = "result",
        argNames = "result"
    )
    public void onReturned(String result) {
        trace.record("C: returned " + result);
    }

    @AfterThrowing(
        pointcut = "execution(public * example.TransferService.transfer(..))",
        throwing = "error",
        argNames = "error"
    )
    public void onThrown(IllegalStateException error) {
        trace.record("C: throwing " + error.getClass().getSimpleName());
    }
}
~~~

Vì `OutcomeAspect` có precedence 30, các callbacks C nằm trong A và B trên một invocation bình thường của demo.

- Khi target trả `String "OK"`, `@AfterReturning` với `returning = "result"` bind kết quả vào `String result`, in `C: returned OK`.
- Khi target ném `IllegalStateException`, `@AfterReturning` **không chạy**, còn `@AfterThrowing` với `throwing = "error"` bind exception vào `IllegalStateException error` và in loại exception.
- Hai advice **khác loại** dùng để xử lý hai nhánh **loại trừ nhau** của cùng target method, nên demo không dựa vào thứ tự của hai advice cùng loại trong một class.
- `argNames` chỉ rõ tên binding theo thuộc tính `returning` / `throwing`, tránh phụ thuộc vào việc compiler có lưu tên method parameters.
- `@AfterThrowing` **không nuốt exception** chỉ vì method advice trả `void`; exception của target vẫn đi lên A/B/caller nếu không được interceptor khác xử lý.

**Phạm vi typed binding:** `String` và `IllegalStateException` thu hẹp điều kiện của từng advice; không nên tuyên bố rằng chúng sẽ chạy cho **mọi** return type hoặc throwable type trong ứng dụng.

### 5.8. Spring configuration `AppConfig.java`

~~~java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public TraceEvents traceEvents() {
        return new TraceEvents();
    }

    @Bean
    public TransferService transferService(TraceEvents trace) {
        return new TransferService(trace);
    }

    @Bean
    public OuterTraceAspect outerTraceAspect(TraceEvents trace) {
        return new OuterTraceAspect(trace);
    }

    @Bean
    public InnerTraceAspect innerTraceAspect(TraceEvents trace) {
        return new InnerTraceAspect(trace);
    }

    @Bean
    public OutcomeAspect outcomeAspect(TraceEvents trace) {
        return new OutcomeAspect(trace);
    }
}
~~~

**Quan trọng với Spring:**

- `@Aspect` đánh dấu class chứa advice, nhưng bản thân nó **không tự đăng ký Spring bean**. Các method `@Bean` tạo aspect instances.
- `@EnableAspectJAutoProxy` bật annotation-style AOP trong Spring Framework.
- `proxyTargetClass = true` chọn class-based Spring proxy để demo có thể yêu cầu bean bằng `TransferService.class`. **Không nên hiểu CGLIB làm thay đổi logic của `@Order`.**
- `TraceEvents` chỉ là collector; pointcut trong ba aspects chỉ match `TransferService.transfer(..)`, không match methods của TraceEvents.
- Thứ tự `@Bean` xuất hiện trong file **không quyết định** order của advice. Precedence dựa trên `@Order(10)`, `@Order(20)` và `@Order(30)`.
- Nếu thêm advisor transaction/cache/security ở một ứng dụng lớn, actual chain có thể thêm tầng cần quan sát; project này chủ động không khai báo chúng.

### 5.9. Kiểm tra tự động và chạy: `Main.java`

~~~java
package example;

import java.util.List;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    private static void check(
            String scenario,
            List<String> actual,
            List<String> expected) {

        if (!expected.equals(actual)) {
            throw new AssertionError(
                scenario + " trace mismatch.\n"
                + "expected: " + expected + "\n"
                + "actual:   " + actual
            );
        }
        System.out.println("verified: " + scenario);
    }

    public static void main(String[] args) {
        try (var context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            TransferService service = context.getBean(TransferService.class);
            TraceEvents trace = context.getBean(TraceEvents.class);

            System.out.println("spring AOP proxy: "
                + AopUtils.isAopProxy(service));

            System.out.println("-- success --");
            String returned = service.transfer(false);
            System.out.println("caller result: " + returned);

            check("success", trace.drain(), List.of(
                "A: enter",
                "B: enter",
                "target: begin",
                "target: return OK",
                "C: returned OK",
                "B: after",
                "B: finally",
                "A: after",
                "A: finally"
            ));

            System.out.println("-- failure --");
            try {
                service.transfer(true);
                throw new AssertionError("Expected IllegalStateException");
            } catch (IllegalStateException ex) {
                System.out.println("caller caught: " + ex.getMessage());
            }

            check("failure", trace.drain(), List.of(
                "A: enter",
                "B: enter",
                "target: begin",
                "target: throw boom",
                "C: throwing IllegalStateException",
                "B: finally",
                "A: finally"
            ));
        }
    }
}
~~~

Phương thức `check` không chỉ xác minh tập events; `List.equals` còn xác minh **từng vị trí theo thứ tự** và **số lượng sự kiện**, nên phát hiện log thừa, log thiếu, đảo A/B, hoặc in “after” sai ở nhánh lỗi.

Bộ collector được `drain()` sau mỗi case nên trace của failure không bị lẫn với success. Cách phân đoạn theo case này là một cách thay thế đơn giản cho correlation ID **chỉ trong demo một luồng**.

### 5.10. Lệnh chạy

Tại thư mục `spring-aop-trace-demo` chứa `pom.xml`:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
~~~

Khi các dependency đã được giải quyết và project được chạy với Java 17+, các dòng `System.out.println` có thứ tự:

~~~text
spring AOP proxy: true
-- success --
A: enter
B: enter
target: begin
target: return OK
C: returned OK
B: after
B: finally
A: after
A: finally
caller result: OK
verified: success
-- failure --
A: enter
B: enter
target: begin
target: throw boom
C: throwing IllegalStateException
B: finally
A: finally
caller caught: boom
verified: failure
~~~

Maven hoặc framework có thể phát ra thông báo riêng không được liệt kê ở trên. Các dòng `verified` chỉ xuất hiện khi **toàn bộ thứ tự events đúng**; nếu sai, `Main.check(...)` ném `AssertionError` và in expected/actual để debug.

### 5.11. Kiểm chứng từng bước qua code

**Trường hợp success:**

1. Caller thực hiện `service.transfer(false)` qua Spring proxy.
2. A(10) in `enter`, rồi proceed.
3. B(20) in `enter`, rồi proceed.
4. C(30) không chạy trước target vì chỉ có after-returning/after-throwing advice; Spring chuyển tiếp đến method target.
5. Target ghi begin và return OK.
6. C nhận normal return và in `returned OK`.
7. B nhận kết quả trả về từ proceed, in after, rồi finally.
8. A nhận kết quả từ B, in after, rồi finally.
9. Caller nhận `OK` và check xác nhận đúng chín events trong collector.

**Trường hợp failure:**

1. A(10) enter rồi B(20) enter.
2. Target ghi begin, ghi throw, rồi ném `IllegalStateException("boom")`.
3. C(30) nhận exception trong `@AfterThrowing` và ghi loại ngoại lệ.
4. B không vào dòng after vì proceed ném lỗi; `finally` của B vẫn ghi.
5. A cũng không vào dòng after; `finally` của A vẫn ghi.
6. Caller bắt exception, rồi `check` so sánh đủ bảy events.
7. Cả hai trường hợp, target được gọi **một lần**; hai around advices không đổi arguments hoặc return/throw.

## 6. Phân biệt “after” trong bảng vết

Bốn khái niệm có thể xuất hiện cùng chữ “after” nhưng khác ngữ nghĩa:

| Hình thức | Khi nào xuất hiện? | Trong bảng vết |
| --- | --- | --- |
| Code đặt sau `pjp.proceed()` trong `@Around` | Chỉ khi proceed trả bình thường, nếu không có catch/finally bao ngoài | `A: after`, `B: after` |
| `@AfterReturning` | Method execution được advice trả về bình thường | `C: returned OK` |
| `@AfterThrowing` | Method execution được advice ném exception thoát ra và kiểu binding phù hợp | `C: throwing IllegalStateException` |
| `@After` | Method execution kết thúc bình thường **hoặc** exceptional exit | Có thể thêm vào trace, không có trong project để tránh nhiễu |
| `finally` của Java nằm trong `@Around` | Khối try trong advice được đi vào và rời theo normal/exception | `A: finally`, `B: finally` |

**Nhầm lẫn thường gặp:** Dòng `B: after` **không xuất hiện khi target lỗi** không phải vì Spring quên chạy `@After`. Trong demo không hề có annotation `@After` ở B; `B: after` là một lệnh Java bình thường sau proceed, nên khi proceed throw, Java bỏ qua lệnh đó.

**Muốn làm cleanup trong @Around:** dùng `try/finally`. **Muốn logic chạy sau method trên cả hai nhánh mà không cần biến trạng thái riêng:** cân nhắc `@After`. **Chỉ muốn outcome:** dùng `@AfterReturning` hoặc `@AfterThrowing`.

## 7. Nếu thêm @After vào cùng aspect thì sao?

Spring Framework mô tả advice ordering **trong cùng một class `@Aspect`**. Từ precedence cao xuống thấp:

1. `@Around`
2. `@Before`
3. `@After`
4. `@AfterReturning`
5. `@AfterThrowing`

Tuy nhiên, `@After` có **after-finally semantics** và **thực tế xuất hiện sau `@AfterReturning` hoặc `@AfterThrowing`** tương ứng khi join point kết thúc trong cùng aspect. Đây là lý do phải phân biệt **precedence lúc hình thành interceptor chain** với **thứ tự câu lệnh được in trên đường thoát**.

Ví dụ snippet bổ sung trong `OutcomeAspect`:

~~~java
@After("execution(public * example.TransferService.transfer(..))")
public void onFinished() {
    trace.record("C: finally");
}
~~~

Đoạn này **không nằm trong project nguyên bản**, vì nếu thêm nó thì bảng expected/output mục 5 phải được cập nhật.

Nếu thêm và chạy **trong cùng OutcomeAspect**, thứ tự nhánh thành công của lớp C về mặt outcome sẽ là:

~~~text
target: return OK
C: returned OK
C: finally
B: after
B: finally
A: after
A: finally
~~~

Nhánh lỗi:

~~~text
target: throw boom
C: throwing IllegalStateException
C: finally
B: finally
A: finally
~~~

Điểm học: `@After` ở đây **không** có nghĩa “chỉ after returning”; nó chạy ở cả hai nhánh. Điều này được Spring Framework mô tả rõ trong hướng dẫn chính thức về advice ordering.

**Không nhầm với hai lời advice cùng loại trong cùng aspect:** ví dụ hai `@After` methods cùng match một join point sẽ không có thứ tự bảo đảm chỉ nhờ vị trí khai báo trong source Java. Để trace xác định được, nên hợp nhất logic vào một advice hoặc tách thành các aspects có order rõ.

## 8. Nếu đổi @Order thì bảng vết phải đổi thế nào?

Giả sử thay:

~~~java
@Order(10)
public class OuterTraceAspect { /* ... */ }
~~~

bằng:

~~~java
@Order(40)
public class OuterTraceAspect { /* ... */ }
~~~

Mặc định ba lớp trong demo lúc đó có thứ tự:

~~~text
B: 20 (cao precedence nhất)
C: 30
A: 40 (thấp precedence nhất)
~~~

Vì C là after advice, A và B vẫn là around advice nhưng **B giờ bao ngoài A**. Trace khi success (sau khi cấu hình lại) có các sự kiện:

~~~text
B: enter
A: enter
target: begin
target: return OK
A: after
A: finally
C: returned OK
B: after
B: finally
~~~

Ở đây C(30) nằm **giữa B(20) và A(40)**. Vì C after-returning advice bao quanh lời gọi vào A(40), khi A hoàn tất thì C mới quan sát normal return. C không nhất thiết nằm sát target khi order đổi.

Đây là một phép thử suy luận hữu ích: **thứ tự thực thi không chỉ phụ thuộc vào loại advice**, mà còn phụ thuộc **lớp nào nằm ngoài/lớp nào nằm trong**.

**Không coi các dòng trên là stdout project mặc định**: chúng là dự đoán sau khi đổi order và khởi tạo **một ApplicationContext mới**. Không dựa vào thay annotation khi proxy đang sống để cho rằng chain tự cập nhật.

## 9. Khi có nhiều infrastructure advisors

Một Spring bean có thể có nhiều advisors hơn ba aspects do lập trình viên khai báo:

- Transaction interceptor qua `@Transactional`.
- Cache interceptor qua `@Cacheable`.
- Method security interceptor.
- Async/proxy infrastructure.
- Các `ProxyFactory` wrappers do code hoặc thư viện thứ ba tạo.

Giả sử:

~~~text
Caller
 -> Trace A
    -> TransactionInterceptor
       -> Trace B
          -> Target
       <- B finally
    <- commit/rollback
 <- A finally
~~~

Nếu A nằm **ngoài** transaction interceptor, A finally có thể xuất hiện **sau** transaction commit/rollback. Nếu A nằm **trong** transaction interceptor thì A finally có thể xuất hiện **trước** commit/rollback. Cách ghi bảng trace cần đánh dấu ranh giới này thay vì chỉ dựa vào “target trả OK” để kết luận transaction đã commit.

**Không tự khẳng định transaction có order cố định trong mọi Spring context.** Spring cung cấp cấu hình precedence cho một số infrastructure advisors (ví dụ `@EnableTransactionManagement(order = ...)`). Cần đọc cấu hình thật và kiểm tra advisor chain/test integration khi thứ tự ảnh hưởng nghiệp vụ.

Một trace chỉ dùng `System.out.println` từ các custom aspects **không cho thấy tự động** transaction begin/commit hoặc cache hit. Nếu mục tiêu là xác nhận ordering với transaction hoặc cache, nên thêm instrumentation/test transaction thật ở **lớp đúng** thay vì diễn giải quá mức từ log A/B.

## 10. Trường hợp có around advice kết thúc sớm

Giả sử một aspect ưu tiên cao hơn có logic:

~~~java
@Around("execution(public * example.TransferService.transfer(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    if (cached()) {
        return "CACHED";
    }
    return pjp.proceed();
}
~~~

Đây là **snippet minh họa**, với method `cached()` do ứng dụng tự định nghĩa, không thuộc project runnable.

Nếu tầng ngoài trả `"CACHED"` mà **không gọi proceed**, những tầng bên trong **không được đi vào**. Vì vậy bảng trace có thể là:

~~~text
outer: enter
outer: cache hit
caller result: CACHED
~~~

Không có dòng “target begin” không nhất thiết là lỗi; một lời advice đã có chủ đích short-circuit. Nhưng nếu bạn đang debug audit/security bắt buộc chạy, cần kiểm tra vị trí interceptor và tính đúng đắn của việc kết thúc sớm.

**Phân biệt ba trường hợp:**

1. Outer **chưa vào** → không có log outer.
2. Outer **đã vào nhưng không proceed** → chỉ có log từ outer hoặc các tầng đã vào trước đó.
3. Outer **đã proceed** nhưng một inner advisor shortcut → có log outer + một số tầng trong, **không có log target**.

Chỉ khi lập bảng vết đầy đủ mới phân biệt được “Spring AOP không hoạt động”, “pointcut không match”, “proxy bị bỏ qua” và “interceptor shortcut đúng thiết kế”.

## 11. Trường hợp exception không xuất phát từ target

Bảng failure ở mục 3 và 5 giả định **target method tự ném ngoại lệ**. Nhưng exceptional exit của một Spring AOP invocation có thể phát sinh ở nhiều vị trí:

| Nguồn exception | Target có nhất thiết đã chạy? | Các mốc có thể xuất hiện |
| --- | --- | --- |
| Target ném lỗi | Có, lời gọi đã tới target | target throw; callback on-throw phù hợp; outer finally |
| Inner `@Before` advice ném lỗi | **Không** | outer enter, có thể inner enter, rồi outer finally |
| Outer `@Around` ném lỗi trước `proceed()` | **Không** | các tầng ngoài đã vào; tầng trong có thể chưa vào |
| `@AfterReturning`/`@After` advice tự ném lỗi | Target có thể đã return bình thường | outer catch/finally có thể quan sát lỗi advice |
| Metrics backend/logging trong advice ném lỗi | Phụ thuộc vị trí ném | trace có thể bị thiếu hoặc ngoại lệ gốc bị che |
| Transaction commit ném lỗi sau target | Target có thể đã trả bình thường | Thứ tự tùy transaction advisor nằm ở đâu |

**Quy tắc an toàn:** `@AfterThrowing` không phải exception handler toàn cục cho mọi lỗi phát sinh từ các advice khác. Theo hướng dẫn Spring, `@AfterThrowing` hướng vào exceptional exit của **method join point** và không tự cam kết nhận exception do một `@After`/`@AfterReturning` khác sinh ra.

Nếu cần một trace phân biệt lỗi từ target, advice hay transaction commit, nên log rõ **vị trí exception được quan sát** và không chỉ nhìn loại exception. Một outer `@Around` có `catch(Throwable t)` có thể quan sát exception đi lên từ **bất kỳ phần chain phía trong** nào đã ném, không chỉ target.

## 12. “Ngoại lệ được bắt trong target” tạo trace nào?

Ví dụ:

~~~java
public String transferRecovering() {
    try {
        riskyOperation();
    } catch (IllegalStateException ex) {
        return "RECOVERED";
    }
}
~~~

Đoạn này là **snippet**, `riskyOperation()` là method minh họa chưa định nghĩa trong project mục 5.

Nếu method **bắt toàn bộ exception nội bộ rồi return `RECOVERED`**, từ góc nhìn Spring AOP method execution đã **kết thúc bình thường**. Hệ quả:

- `@AfterReturning` phù hợp có thể chạy với `"RECOVERED"`.
- `@AfterThrowing` không chạy cho exception đã bị bắt nội bộ.
- Code sau `proceed()` trong outer `@Around` có thể chạy.
- `finally` của những around advice đã vào vẫn chạy.

Không nên thấy dòng “operation failed internally” trong target rồi ngạc nhiên vì `@AfterThrowing` không xuất hiện; điểm phân biệt là **exception có thoát khỏi method join point hay không**.

Một outer interceptor cũng có thể bắt exception từ tầng trong rồi trả fallback. Khi ấy một aspect **ở ngoài** interceptor đó có thể quan sát **normal return**, còn tầng sâu hơn đã thấy exception. Chính vì thế bảng trace cần nêu rõ **tầng** quan sát outcome chứ không chỉ một nhãn “success/failure” toàn cục.

## 13. Self-invocation và những dòng trace bị thiếu

Trong Spring AOP proxy-based:

~~~java
public void outer() {
    this.inner();
}
~~~

Lời gọi `this.inner()` bên trong target **không tự quay trở lại Spring proxy**. Nếu pointcut của bạn chỉ nhắm `inner()`, nó có thể chạy advice khi caller gọi `service.inner()` trực tiếp qua proxy, nhưng **không** được advice cho lời gọi phát ra từ `this.inner()`.

Ví dụ bảng:

| Đường gọi | Trace AOP có thể thấy |
| --- | --- |
| `client -> springProxy.inner()` | aspect enter → inner target → aspect exit |
| `client -> springProxy.outer() -> target.this.inner()` | Có thể có aspect cho **outer**, nhưng **không tự có aspect riêng cho inner** |
| `client -> new Service().inner()` | Không có advice của Spring-managed proxy cho object tự tạo |

**@Order không giải quyết self-invocation.** Chuyển JDK proxy sang CGLIB cũng không tự sửa vì đây là ranh giới mô hình proxy của Spring AOP.

Cách sửa thường được ưu tiên: tách method cần advice sang **Spring bean khác** và gọi qua dependency injection. Đừng tự unwrap `Advised.getTargetSource()` rồi gọi target để debug; nó có thể cố tình bỏ qua tất cả lời advice bạn đang muốn xác minh.

## 14. @After và @AfterReturning/Throwing trong cùng aspect

Spring quy định precedence **bên trong một `@Aspect`** cho các advice khác loại, nhưng có **đặc thù về finally semantics**:

| Advice type | Chiều áp dụng chính | Ghi chú thứ tự |
| --- | --- | --- |
| `@Around` | Bao quanh invocation | Có thể chạy trước/sau `proceed()` |
| `@Before` | Trước join point | Không tự biết result |
| `@After` | Khi join point kết thúc theo hai nhánh | Về hiệu ứng, chạy **sau** after-returning/after-throwing cùng aspect |
| `@AfterReturning` | Khi join point trả bình thường | Không chạy nếu exception thoát method |
| `@AfterThrowing` | Khi join point ném exception | Không chạy khi method tự catch lỗi rồi return |

Spring mô tả precedence của distinct advice types trong cùng aspect theo thứ tự từ cao xuống thấp: `@Around`, `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`. Nhưng **không** được chuyển bảng precedence thành một bảng stdout một chiều: vì tính chất `finally`, `@After` **thực tế được gọi sau** `@AfterReturning`/`@AfterThrowing` tương ứng trong cùng aspect.

Nếu có **hai advice cùng một loại** trong **cùng aspect** và cả hai match cùng method, thứ tự giữa chúng không được bảo đảm bằng thứ tự khai báo method trong source Java. Java reflection không cung cấp một thứ tự nguồn đủ tin cậy để dựa vào.

**Để trace xác định**, đừng thiết kế bài test cần thứ tự cố định giữa hai `@Before` cùng aspect; gộp xử lý tuần tự vào một advice hoặc tách thành hai aspects rồi đặt `@Order` khác nhau.

## 15. Mẫu bảng vết dùng cho integration test

Khi cần debug trong Spring Boot hay Spring Framework application thật, có thể tạo bảng với những cột sau:

| Trường | Ví dụ | Tác dụng |
| --- | --- | --- |
| `invocationId` | `op-17` | Phân nhóm events cùng một lời gọi, tránh trộn request |
| `sequence` | `1,2,3,...` | Thứ tự quan sát trong cùng invocation |
| `layer` | `outerAspect(10)` | Biết advisor/tầng nào ghi nhận |
| `phase` | `enter/after/throw/finally` | Phân biệt đường vào và ra |
| `method` | `TransferService.transfer` | Nhận diện method được advice |
| `outcome` | `normal/exceptional/unknown` | Tách loại kết quả quan sát |
| `exceptionType` | `IllegalStateException` | Phân loại loại lỗi, không cần log secrets |
| `thread` | Tên/ID thread | Hỗ trợ phát hiện events bị xen kẽ |
| `advisorOrder` | `10/20/30` | Giải thích precedence nếu cấu hình chính xác |

**Không biến bảng trace thành nơi ghi dữ liệu nhạy cảm:** tên operation và loại exception thường đủ. Không log mặc định mật khẩu, token, nội dung giao dịch, raw method arguments hoặc PII. Với trace production, dùng logging/tracing có correlation context đã được quản trị tốt.

### 15.1. Bảng expected/actual khi test sai

Một test nên đưa ra cả hai chuỗi:

~~~text
Expected:
[A: enter, B: enter, target: begin, C: ..., B: finally, A: finally]

Actual:
[B: enter, A: enter, target: begin, C: ..., A: finally, B: finally]
~~~

Nếu khác ở **chiều vào**, kiểm tra `@Order`/`Ordered` hoặc actual advisor chain. Nếu chỉ khác ở **chiều ra**, kiểm tra vị trí `finally`, lời `@After` và các nhánh `catch`. Nếu mất các dòng target, kiểm tra `proceed()` hoặc cache short-circuit. Nếu mất toàn bộ advice, kiểm tra pointcut, bean/proxy và self-invocation.

### 15.2. Assertions phải bao phủ cả nhánh lỗi

Sai lầm thường gặp là chỉ assert:

~~~java
assertTrue(events.contains("A: enter"));
assertTrue(events.contains("B: enter"));
~~~

Hai assertions ấy vẫn pass khi trình tự đảo ngược. Để xác minh ordering, dùng **so sánh cả danh sách theo thứ tự**, như `List.equals(...)` trong demo hoặc `assertEquals(expectedEvents, actualEvents)` trong JUnit.

Cần assert thêm:

- Result business vẫn chính xác trong normal path.
- Exception **type** và message/identity khi contract yêu cầu.
- Số lượt target được gọi đúng một lần nếu không có retry.
- Không xuất hiện callback `@AfterReturning` ở exceptional path.
- Không xuất hiện callback `@AfterThrowing` ở normal path.
- Finally của mỗi around advice **đã vào** xuất hiện đủ số lần.
- Không có events từ invocation khác lẫn vào.
- Những methods/beans ngoài pointcut không bị advice vô ý.

## 16. Những giới hạn khiến bảng vết khác kỳ vọng

### 16.1. Asynchronous logging và nhiều thread

Một lời gọi Spring AOP đồng bộ có thứ tự Java rõ ràng, nhưng log exporter bất đồng bộ có thể gửi hoặc hiển thị events với thứ tự khác. Nếu hai requests chạy song song, dòng `A: enter` của request 2 có thể nằm giữa `A: enter` và `B: enter` của request 1.

Vì vậy **không dùng một global list không đồng bộ** làm test cho nhiều lời gọi concurrently. Dùng collection per-invocation hoặc trace context mang correlation ID đúng vòng đời; phân nhóm trước rồi mới so sánh thứ tự.

### 16.2. Method trả Future/reactive publisher

`@AfterReturning` trên method trả `CompletableFuture` có thể chạy ngay khi method **trả Future object**, không có nghĩa công việc nền đã hoàn tất. `@AfterThrowing` không mặc nhiên quan sát lỗi phát sinh **sau đó** trong async computation. Tương tự, method trả `Mono`/`Flux` có thể kết thúc trước khi pipeline reactive được subscribe.

Nếu yêu cầu trace **toàn bộ** vòng đời async, phải dùng completion hooks hoặc lifecycle operators đúng abstraction; đừng đọc “method returned” thành “request fully completed”.

### 16.3. Cấu hình proxy và method không intercept được

- JDK proxy chỉ expose các interfaces thích hợp; class-based CGLIB proxy dùng subclass.
- Class/method final hoặc private có thể không được advice theo cơ chế class proxy.
- `@Aspect` class cần được đăng ký làm bean.
- Bean có AOP proxy không bảo đảm **mọi method** match mọi advisor.
- Self-invocation vẫn bỏ qua proxy với cả JDK/CGLIB.
- Infrastructure advisor bên thứ ba có thể ở ngoài custom @Aspect.

Một trace thiếu event không chứng minh Spring AOP bug; trước tiên xác nhận target bean, proxy reference và pointcut eligible.

## 17. Chẩn đoán theo triệu chứng

| Hiện tượng | Giả thuyết ưu tiên | Cách kiểm chứng |
| --- | --- | --- |
| A enter trước B enter nhưng A finally cũng trước B finally | Có thể log được ghi khác tầng hoặc code finally đặt khác kỳ vọng | Xem cấu trúc `proceed()` và thực tế interceptor chain |
| C returned xuất hiện khi tưởng target lỗi | Target hoặc inner interceptor đã bắt exception và trả fallback | Xác định outcome tại boundary của C |
| Không có C throwing khi target ném lỗi | Throwable type không match, caller bypass proxy hoặc một tầng đã xử lý | Kiểm tra throwing binding và target method execution |
| B after không xuất hiện nhưng B finally có | Lời `proceed()` của B ném exception | Đây có thể là hành vi đúng |
| A và B đều không có log | Aspect không là Spring bean, pointcut không match hoặc caller không qua proxy | Kiểm tra `@Bean`, AOP config, `AopUtils.isAopProxy` |
| Có A enter nhưng không có B enter | A chưa proceed hoặc advisor tầng trước B short-circuit | Xem early return, cache hit, precondition |
| Target chạy hai lần | Retry/multiple `proceed()`/duplicate caller | Đếm invocation và kiểm tra idempotency |
| Trace đúng success, sai failure | Bỏ sót `finally` hoặc xử lý exception khác nhau | Assert riêng exceptional path |
| Trace thay đổi sau thêm `@Transactional` | Thêm infrastructure advisor với order riêng | Kiểm tra advisor chain, transaction boundary |
| Chỉ thấy events khi gọi từ controller, không thấy khi service tự gọi | Self-invocation | Đường gọi `this.method()` bypass Spring proxy |
| Log bị đảo ngẫu nhiên giữa requests | Các request song song hoặc backend log async | Phân nhóm theo invocationId, không so toàn cục |
| Hai advice cùng loại trong cùng aspect đảo vị trí sau build | Thứ tự method source không được bảo đảm | Gộp advice hoặc tách aspect classes và gắn order |
| @Order nhỏ nhưng advice không chạy | Order không quyết định pointcut matching | Kiểm tra bean/method và đường gọi |
| Advice onThrow tự ném exception mới | Caller có thể thấy exception bị thay | Hạn chế lỗi instrumentation, kiểm tra exception contract |

### Checklist khắc phục có trình tự

1. Xác định **method, target bean và caller reference thực**.
2. Đọc cấu hình Spring AOP và tên `@Aspect` beans đã đăng ký.
3. Xác nhận pointcuts và những advisors eligible cho method.
4. Ghi order **số cụ thể** cho những aspects phải có precedence cố định.
5. Tách event thành **enter / target / result hoặc throw / after / finally**.
6. Chạy một invocation success, một invocation failure, không đồng thời.
7. So sánh **full expected vs actual sequences**, không chỉ sự hiện diện của log.
8. Thêm biến thể method tự catch exception rồi return nếu nghiệp vụ có fallback.
9. Khi có transaction/cache/async, mở rộng bảng trace cho các tầng thật.
10. Kiểm tra behavior: return value, exception, side effects, invocation count.
11. Chỉ sau khi đường gọi đơn luồng đúng mới mở rộng test concurrency/log aggregation.
12. Nếu thay đổi order, tạo context/test run mới và xác minh lại chain.

## 18. Câu hỏi luyện tập

**Câu 1.** A có `@Order(5)`, B có `@Order(50)` và hai around advice đều proceed. Aspect nào enter trước, aspect nào finally sau?

**Đáp án:** A(5) có precedence cao hơn, **enter trước** và **finally sau** B(50).

**Câu 2.** Có log A enter → B enter → target throw → B finally → A finally nhưng không có “A after”. Spring AOP bị lỗi?

**Đáp án:** Không. Lệnh after đặt **sau `proceed()`** trong around advice không chạy khi proceed ném lỗi. Finally của cả A/B vẫn chạy.

**Câu 3.** `@AfterReturning` và `@AfterThrowing` cùng match method signature nhưng target trả bình thường. Chạy cả hai?

**Đáp án:** Không. Chỉ after-returning phù hợp chạy ở normal exit; after-throwing chỉ chạy khi exception thoát method và kiểu throwable khớp.

**Câu 4.** Method target tự catch exception rồi return `"RECOVERED"`. Bảng trace nào đúng?

**Đáp án:** Từ góc nhìn Spring AOP, đó là **normal return**, nên after-returning phù hợp có thể chạy. Lỗi bị bắt nội bộ không tự kích hoạt after-throwing.

**Câu 5.** Outer around không gọi proceed vì cache hit. Có bắt buộc target và các inner advice chạy?

**Đáp án:** Không. Chỉ các tầng đã được đi vào trước khi shortcut mới có thể có events. Cần xác minh đây là hành vi có chủ đích hay quên proceed.

**Câu 6.** Có hai `@Before` cùng class `@Aspect`, cùng match join point. Code source khai báo first trước second; thứ tự được bảo đảm?

**Đáp án:** Không. Nếu cần thứ tự xác định, gộp logic hoặc dùng hai aspect classes có order khác nhau.

**Câu 7.** Trong một aspect có `@After` và `@AfterReturning` cùng match method thành công. Vì @After có precedence cao hơn nên dòng @After luôn in trước @AfterReturning?

**Đáp án:** Không. Spring quy định `@After` có **after-finally semantics** và thực tế chạy sau after-returning hoặc after-throwing phù hợp **trong cùng aspect**.

**Câu 8.** Chỉ cần assert `events.contains("A: enter")` và `events.contains("B: enter")` là chứng minh được A trước B?

**Đáp án:** Không. Phải assert **thứ tự phần tử**, ví dụ `List.equals(expectedSequence)` hoặc kiểm tra indexes có điều kiện rõ ràng.

**Câu 9.** Caller tạo `new TransferService(trace)` và gọi method. Bảng A/B/C có áp dụng không?

**Đáp án:** Không tự áp dụng. Instance ngoài Spring container không phải bean proxy được Spring AOP tạo.

**Câu 10.** Trace business có thể kết luận “transaction committed” chỉ vì `@AfterReturning` xuất hiện sau target?

**Đáp án:** Không. Commit có thể xảy ra trong transaction interceptor ở vị trí khác; phải kiểm tra actual transaction boundary và advisor ordering.

## 19. Tài liệu tham khảo chính thức

- [Spring Framework — Declaring Advice / Advice Ordering](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): quy tắc thứ tự giữa aspects, `@Order`/`Ordered`, đường vào/đường ra, after-returning/after-throwing và `@After` finally semantics.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB, self-invocation, proxy boundaries.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): đăng ký Spring aspect beans và bật proxy AOP.
- [Spring Framework — Declaring Pointcuts](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): lựa chọn method execution phù hợp, tránh tác động bean khác.
- [Spring Framework — AOP Concepts](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html): Spring AOP join point và các advice type.
- [Spring Framework — Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html): quan sát advisor chain của một Spring AOP proxy.
- [Spring Javadoc — Ordered](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/Ordered.html): thứ tự giá trị precedence.


</details>

- [Quay lại đầu trang](#back-to-top)
