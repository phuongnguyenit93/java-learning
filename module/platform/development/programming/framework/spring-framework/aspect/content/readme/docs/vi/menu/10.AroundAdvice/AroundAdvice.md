<a id="back-to-top"></a>

# Around Advice

## Menu
- [@Around và vai trò ProceedingJoinPoint](#around-advice-001)
- [proceed() để tiếp tục chuỗi interceptor và phương thức đích](#around-advice-002)
- [Hành vi khi không gọi proceed()](#around-advice-003)
- [Hành vi khi gọi proceed() nhiều lần](#around-advice-004)
- [proceed(Object&#91;&#93;) và thay đổi đối số theo quy ước Spring AOP](#around-advice-005)
- [Giữ đúng giá trị trả về và kiểu dữ liệu của phương thức đích](#around-advice-006)
- [Bao quanh đường thành công và ngoại lệ bằng try/catch/finally](#around-advice-007)
- [Đo thời gian thực thi mà không làm thay đổi kết quả nghiệp vụ](#around-advice-008)
- [Rủi ro lặp thao tác có tác dụng phụ khi triển khai retry bằng aspect](#around-advice-009)
- [Tránh nuốt ngoại lệ hoặc trả giá trị mặc định ngoài hợp đồng](#around-advice-010)

## <a id="around-advice-001">@Around và vai trò ProceedingJoinPoint</a>

<details>
<summary>Xem chi tiết</summary>

# @Around và vai trò ProceedingJoinPoint trong Spring AOP

## 1. @Around giải quyết điều gì trong Spring Aspect?

Trong Spring AOP, một `@Aspect` có thể chạy code trước hoặc sau một method của **Spring bean được proxy**. Nếu chỉ cần ghi log trước method, `@Before` thường đủ. Nếu chỉ cần xem kết quả bình thường, có `@AfterReturning`. Nhưng khi muốn **bao cả quá trình gọi**, giữ trạng thái cục bộ trước–sau, quyết định có cho target chạy hay không, thay tham số, thay giá trị trả về hoặc xử lý exception theo chính sách rõ ràng, ta sử dụng **`@Around`**.

Ví dụ một Spring service có phương thức `find(String key)`:
- Có thể đo thời gian của toàn bộ lời gọi và in thời gian ở `finally`.
- Có thể trả trực tiếp một kết quả đã cache mà **không gọi** service target.
- Có thể chuẩn hóa tham số trước khi gọi target bằng `proceed(newArgs)`.
- Có thể để nguyên exception hoặc, khi nghiệp vụ cho phép, biến exception thành một kết quả fallback.
- Có thể dùng retry có kiểm soát, mặc dù thực tế nên đánh giá một cơ chế retry chuyên trách.

**Khác biệt cốt lõi** của `@Around` nằm ở đối tượng **`ProceedingJoinPoint`** và lời gọi **`proceed()`**. Không có `proceed()` thì Spring AOP interceptor chain ở phía trong sẽ không tự đi tiếp chỉ vì method target đã được khai báo.

Tuy nhiên, `@Around` cũng là loại advice có quyền can thiệp mạnh nhất, do đó dễ tạo bug nhất. Tài liệu Spring khuyên **chọn loại advice ít quyền nhất mà vẫn đáp ứng nhu cầu**. Bài này chỉ tập trung vào `@Around` trong **Spring Framework, Spring `@AspectJ` và cơ chế proxy**, không mở rộng sang bytecode weaving hoặc các join point ngoài method execution.

### Mục tiêu học tập

Sau bài này, bạn sẽ:
1. Giải thích vị trí `@Around` trong Spring AOP interceptor chain.
2. Viết method advice có `ProceedingJoinPoint` đứng đầu, trả `Object` và khai báo `throws Throwable`.
3. Phân biệt `proceed()`, `proceed(Object[])` và `getArgs()`.
4. Hiểu hậu quả của việc gọi `proceed()` **0 lần, 1 lần hoặc nhiều lần**.
5. Phân biệt việc target hoàn tất với kết quả cuối cùng mà client của proxy nhận được.
6. Sử dụng `try/catch/finally` chính xác khi đo thời gian, ghi log, bắt và truyền exception.
7. Chạy ví dụ Java 17 + Spring Framework để quan sát cả bình thường, thay argument, short-circuit, throw và self-invocation.
8. Tránh các lỗi quan trọng khi phối hợp `@Around` với transaction, cache, retry và các Spring advisors khác.

## 2. @Around ở vị trí nào trong Spring AOP?

Spring AOP áp dụng advice cho **method-execution join point** của Spring-managed bean bằng JDK dynamic proxy hoặc CGLIB proxy. Một đường gọi được đơn giản hóa như sau:

```text
Client
  |
  v
Spring AOP proxy
  |
  v
@Around advice
  |
  +-- code BEFORE proceed()
  |
  +-- pjp.proceed() ------------------------------+
  |                                               |
  |                                      các advisors phía trong
  |                                               |
  |                                               v
  |                                         target.method(...)
  |                                               |
  |                               return value hoặc exception
  |                                               |
  +<----------------------------------------------+
  |
  +-- code AFTER proceed() (khi return bình thường)
  +-- catch (khi exception đi qua)
  +-- finally (cả hai đường)
  |
  v
Client thấy value/exception mà advice cho đi ra ngoài
```

Hình này cho thấy hai ý nghĩa của "around":

- **Bao quanh về thời gian:** advice có thể chạy trước, rồi chạy sau invocation.
- **Bao quanh về quyền điều khiển:** advice quyết định có gọi phần bên trong của interceptor chain không, gọi với đối số nào và trả kết quả gì.

Lời gọi `proceed()` **không phải** cú pháp Java để gọi trực tiếp `target.method()`. Trong Spring, nó tiếp tục phần còn lại của **AOP interceptor chain**, có thể gồm nhiều advisors khác trước khi tới method target. Vì thế kết quả/exception của `proceed()` đôi khi đã bị một interceptor phía trong thay đổi hoặc short-circuit.

### 2.1. Điều kiện tiên quyết

Để `@Around` có hiệu lực trong một ứng dụng Spring Framework thuần:

- Lớp khai báo advice phải có `@Aspect` và được **đăng ký thành Spring bean** (qua `@Component`, `@Bean` hoặc tương đương).
- `@EnableAspectJAutoProxy` phải được áp dụng trong `ApplicationContext` tạo bean target, hoặc dùng Spring Boot AOP auto-configuration tương ứng.
- Các thư viện AspectJ cần thiết để Spring phân tích pointcut phải có trên classpath (thường cung cấp qua `spring-boot-starter-aop` hoặc `aspectjweaver`).
- Bean target phải được Spring quản lý, có method khớp pointcut và được gọi **qua AOP proxy**.
- JDK/CGLIB proxy phải có khả năng intercept method tương ứng; self-invocation, `final`/`private` methods có thể khiến advice không hoạt động như kỳ vọng.

`@Aspect` **không tự đăng ký Spring component**, và một method chỉ được gắn `@Around` mà thiếu hạ tầng Spring AOP cũng không tự chạy.

## 3. Cấu trúc chuẩn của @Around advice

Mẫu thông dụng:

```java
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TimingAspect {

    @Around("execution(* com.example.service..*.*(..))")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        long started = System.nanoTime();

        try {
            return pjp.proceed();
        } finally {
            long elapsedNanos = System.nanoTime() - started;
            System.out.println("elapsedNs=" + elapsedNanos);
        }
    }
}
```

Đây là **snippet minh họa**, không phải ứng dụng độc lập. Cần các Spring beans và cấu hình AOP phù hợp; `elapsedNs` sẽ khác ở mỗi lần chạy nên không được coi là stdout cố định.

### 3.1. Tại sao tham số đầu là ProceedingJoinPoint?

Spring yêu cầu first parameter của `@Around` advice có kiểu **`org.aspectj.lang.ProceedingJoinPoint`**. Nó mở rộng `JoinPoint` và bổ sung khả năng `proceed()`. Các advice như `@Before` có thể nhận `JoinPoint` để đọc ngữ cảnh nhưng không có quyền gọi tiếp theo cách của `ProceedingJoinPoint`.

### 3.2. Tại sao trả Object?

Tài liệu Spring khuyến nghị around advice trả `Object` vì giá trị **do advice trả** trở thành giá trị mà caller của method được proxy nhìn thấy. Ví dụ:

```java
Object result = pjp.proceed();
return result;
```

là cách giữ nguyên giá trị trả về khi không cần biến đổi.

**Không khai báo around advice trả `void` nếu method target cần giá trị trả về:** Spring lưu ý advice có kiểu trả `void` khiến caller nhận `null`, dù target đã trả giá trị. Điều này có thể gây lỗi khó hiểu khi caller mong `String`, `Integer` hoặc một object khác.

Một advice `Object` có thể bao quanh cả method target `void`; lúc đó kết quả hợp lý khi dùng `proceed()` thường được biểu diễn là `null` trong interceptor. Code nên giữ nguyên `return pjp.proceed();` và không tự suy diễn nó thành một giá trị nghiệp vụ.

### 3.3. Tại sao throws Throwable?

`proceed()` khai báo `throws Throwable`. Advice thường cũng khai báo `throws Throwable` để truyền nguyên exception ra ngoài:

```java
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    return pjp.proceed();
}
```

Việc chỉ khai báo `throws Exception` không thể bao quát đầy đủ `Throwable` theo signature mà compiler cần, trừ khi bạn tự bắt/xử lý phần còn lại.

### 3.4. Method trả về hoặc ném exception gì?

Trong `@Around`, ba trường hợp cần phân biệt:

| Hành vi advice | Target hoặc interceptor chain phía trong | Caller bên ngoài proxy |
| --- | --- | --- |
| `return pjp.proceed();` | Chạy phần chain phía trong, trừ khi một advisor khác short-circuit | Nhận value/exception được truyền qua |
| `return "CACHED";` không gọi `proceed()` | Không đi vào phần chain phía trong | Nhận `"CACHED"` nếu phù hợp return contract |
| `throw ex;` trong advice | Có thể target chưa chạy hoặc đã ném ex | Caller thấy exception do advice đưa ra |
| `return transform(pjp.proceed());` | Chain có thể chạy, sau đó advice đổi kết quả | Caller nhận kết quả đã chuyển đổi |
| Catch exception rồi `return fallback` | Chain đã chạy đến nơi ném exception; advice chuyển outcome | Caller có thể thấy normal return thay vì exception |

**Giữ đúng hợp đồng Java method:** Nếu target method trả `int`, trả `null` từ advice có thể gây lỗi unboxing. Nếu target method trả `String`, trả object không tương thích có thể gây `ClassCastException`. Spring không tự bảo đảm fallback của bạn có kiểu nghiệp vụ hợp lệ.

## 4. ProceedingJoinPoint cung cấp những gì?

### 4.1. Các API thường dùng

| API | Ý nghĩa trong Spring AOP | Lưu ý |
| --- | --- | --- |
| `pjp.proceed()` | Tiếp tục invocation bằng các argument gốc | Gọi khi muốn phần chain phía trong chạy |
| `pjp.proceed(Object[] args)` | Tiếp tục với **bộ argument được cung cấp** | Trong Spring AOP, số lượng và vị trí phải phù hợp method execution |
| `pjp.getArgs()` | Các argument hiện có của method invocation | Không cần reflection để biết argument |
| `pjp.getSignature()` | Thông tin chữ ký method | Có thể dùng `getName()`, hoặc ép kiểu sang `MethodSignature` |
| `pjp.getThis()` | Đối tượng Spring AOP proxy | Khác `getTarget()`, nhất là JDK proxy |
| `pjp.getTarget()` | Target object phía sau proxy | Không gọi method trực tiếp trên nó nếu muốn giữ advice |
| `pjp.getStaticPart()` | Thông tin tĩnh của join point | Phù hợp khi không cần giữ object context |
| `pjp.toShortString()` / `toString()` | Mô tả join point | Phục vụ chẩn đoán, đừng phụ thuộc format log bất biến |

Trong Spring, `getThis()` là **proxy object** và `getTarget()` là **target object**. Chúng có thể khác cả identity lẫn runtime class. Ví dụ JDK proxy chỉ implement interface, còn target là instance của implementation class.

### 4.2. Đọc tên method và argument

```java
@Around("execution(* com.example.service..*.*(..))")
public Object trace(ProceedingJoinPoint pjp) throws Throwable {
    String name = pjp.getSignature().getName();
    Object[] args = pjp.getArgs();

    System.out.println("method=" + name + ", args=" + args.length);
    return pjp.proceed();
}
```

Đây là snippet cần đặt trong lớp `@Aspect` và đăng ký vào context. `getArgs()` cho phép đọc dữ liệu, nhưng **không nên log dữ liệu bí mật** như token, mật khẩu, thông tin cá nhân, thẻ thanh toán hoặc object chứa payload lớn. Với các hệ thống xử lý dữ liệu nhạy cảm, log định danh an toàn hoặc kiểu dữ liệu thay vì in toàn bộ argument values.

### 4.3. Đọc chữ ký typed

```java
import org.aspectj.lang.reflect.MethodSignature;

MethodSignature signature = (MethodSignature) pjp.getSignature();
String methodName = signature.getMethod().getName();
Class<?> returnType = signature.getReturnType();
Class<?>[] parameterTypes = signature.getParameterTypes();
```

Snippet trên nằm trong `@Around` advice; các giá trị dùng để chẩn đoán khi cần biết signature. Khi method được khai báo trên interface nhưng annotation nằm trên implementation, cần cân nhắc Spring method resolution và proxy type; đừng giả định method phản chiếu từ proxy luôn là method implementation có đầy đủ annotation như source code.

## 5. proceed(): đi tiếp một lần, không đi tiếp, hoặc đi tiếp nhiều lần

`ProceedingJoinPoint.proceed()` **tiếp tục phần interceptor chain nằm bên trong advice đang chạy**. Nó không nhất thiết gọi target ngay: một advice phía trong còn có thể trả kết quả sớm, hoặc ném ngoại lệ.

| Số lần gọi | Điều xảy ra với phần chain phía trong | Cần lưu ý |
| --- | --- | --- |
| **Một lần** | Chuyển tiếp một lượt; thường đến target nếu không bị interceptor khác chặn | Đây là cách mặc định khi chỉ muốn quan sát hoặc đo lường |
| **Không lần nào** | Không đi tiếp qua nhánh invocation này | Advice phải trả giá trị phù hợp với contract hoặc chủ động ném ngoại lệ; ví dụ cache hit/authorization gate |
| **Nhiều lần** | Có thể chạy lại các interceptor phía trong và target theo từng lượt | Chỉ phù hợp với retry/replay có giới hạn, điều kiện lỗi rõ và side effect an toàn |

Ví dụ một advice chỉ quan sát mà không thay nghiệp vụ:

~~~java
@Around("execution(* demo.GreetingService.greet(..))")
public Object observe(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("enter");
    return pjp.proceed();
}
~~~

Đây là fragment cần nằm trong `@Aspect` do Spring quản lý. Nếu target ném exception, `proceed()` truyền lỗi ra caller; nó không âm thầm chuyển thành kết quả mặc định. Bài về việc tiếp tục chuỗi đi sâu vào một lượt; bài về short-circuit và nhiều lượt giải thích riêng ảnh hưởng lên advisor, target và các tác dụng phụ.

## 6. proceed(Object[]): thay argument khi gọi

Overload `pjp.proceed(Object[] replacement)` cho phép Spring AOP **chuyển tiếp invocation với argument mới**. Trong Spring AOP dựa trên proxy, mảng này tương ứng đầy đủ với các tham số của method đích, theo đúng thứ tự và kiểu tương thích; không phải mảng gồm các biến được bind trong chữ ký advice.

Ví dụ trong `@Around` advice dành riêng cho method có tham số đầu là `String`:

~~~java
Object[] oldArgs = pjp.getArgs();
Object[] nextArgs = oldArgs.clone();
nextArgs[0] = ((String) oldArgs[0]).trim();
return pjp.proceed(nextArgs);
~~~

Nếu method nhận `(String, int)`, mảng chuyển tiếp vẫn cần **hai phần tử** và phần tử thứ hai phải tương thích `int` (thường là `Integer` boxed). `clone()` chỉ sao chép mảng nông; nó không sao chép các object mutable bên trong. Quy tắc argument binding của AspectJ compiler/weaver có thể khác Spring proxy AOP, nên không mang nguyên ví dụ qua hai cơ chế mà thiếu kiểm chứng. Bài `proceed(Object[])` chuyên sâu sẽ xử lý arity, binding và các trường hợp sai kiểu.

## 7. return, throw, catch và finally trong @Around

Giá trị mà `@Around` trả về trở thành kết quả caller quan sát tại ranh giới proxy. Với mục tiêu quan sát thuần túy, **trả đúng giá trị của `proceed()` và để ngoại lệ truyền đi** là lựa chọn an toàn; đừng vô tình trả `null` cho một method `int` hoặc làm mất thông tin lỗi.

Khi cần cleanup hoặc đo latency cho cả đường thành công và thất bại, bao `proceed()` bằng `try/finally`:

~~~java
long start = System.nanoTime();
try {
    return pjp.proceed();
} finally {
    long elapsedNs = System.nanoTime() - start;
    System.out.println("elapsedNs=" + elapsedNs);
}
~~~

Đây là fragment đo thời gian; số nanosecond không cố định. `finally` chạy khi phần `try` đã được vào và `proceed()` trả hoặc ném exception, nhưng bản thân code trong `finally` cũng phải được thiết kế để không làm hỏng outcome nghiệp vụ.

`catch` chỉ nên chuyển lỗi thành fallback khi **hợp đồng method cho phép** và loại lỗi đó đã được quy định rõ. Trả fallback sẽ khiến các interceptor phía ngoài nhìn thấy normal return thay vì exception. Các bài về return contract, `try/catch/finally` và chống nuốt ngoại lệ sẽ lần lượt phân tích hệ quả với primitive, checked exception và transaction.

## 8. Ví dụ chạy độc lập: năm tình huống rõ ràng

### 8.1. Mục tiêu và môi trường

Project sau dùng **Java 17+**, **Spring Framework 6.1.14**, **AspectJ Weaver 1.9.22.1** và Gradle; không cần Spring Boot hay database. Một aspect xử lý chính xác một `GreetingService` bean với các trường hợp:

1. `greet("Bob")`: gọi `proceed()` bình thường.
2. `greet("alias")`: thay input bằng `"Alice"` khi gọi `proceed(Object[])`.
3. `greet("skip")`: trả `"SKIPPED"` mà không gọi target.
4. `fail()`: target ném `IllegalStateException`, aspect log rồi rethrow.
5. `outer()`: gọi nội bộ `this.greet("inside")` nên không phát sinh `@Around` lần hai cho lời gọi bên trong.

Log được thiết kế **deterministic**, không in duration thay đổi theo môi trường; dễ so với stdout dự kiến.

### 8.2. Cấu trúc project

```text
spring-around-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/around/
    ├── GreetingService.java
    ├── AroundAspect.java
    ├── AppConfig.java
    └── DemoApplication.java
```

### 8.3. Gradle

`settings.gradle`:

```groovy
rootProject.name = 'spring-around-demo'
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
    mainClass = 'demo.around.DemoApplication'
}
```

`spring-context` cung cấp IoC, `AnnotationConfigApplicationContext`, `@Bean` và `@Configuration`; `spring-aop` chứa proxying infrastructure; `aspectjweaver` là thư viện cần để Spring phân tích `@AspectJ` pointcut.

### 8.4. Service target

Tệp `src/main/java/demo/around/GreetingService.java`:

```java
package demo.around;

public class GreetingService {

    public String greet(String name) {
        System.out.println("target:greet:" + name);
        return "Hello " + name;
    }

    public void fail() {
        System.out.println("target:fail");
        throw new IllegalStateException("boom");
    }

    public void outer() {
        System.out.println("target:outer");
        greet("inside");
    }
}
```

`greet`, `fail` và `outer` đều public và non-final, có thể được class-based proxy advise nếu đi qua proxy. Dòng `greet("inside")` trong `outer` là **self-invocation** của target; nó không quay lại proxy.

### 8.5. @Around aspect và ProceedingJoinPoint

Tệp `src/main/java/demo/around/AroundAspect.java`:

```java
package demo.around;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class AroundAspect {

    @Around("execution(public * demo.around.GreetingService.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getName();
        System.out.println("around:before:" + method);

        try {
            if ("greet".equals(method)
                    && "skip".equals(pjp.getArgs()[0])) {
                System.out.println("around:short-circuit:greet");
                return "SKIPPED";
            }

            Object result;
            if ("greet".equals(method)
                    && "alias".equals(pjp.getArgs()[0])) {

                Object[] newArgs = pjp.getArgs().clone();
                newArgs[0] = "Alice";
                result = pjp.proceed(newArgs);
            } else {
                result = pjp.proceed();
            }

            System.out.println("around:after:" + method);
            return result;
        } catch (Throwable ex) {
            System.out.println("around:throw:" + method
                    + ":" + ex.getClass().getSimpleName());
            throw ex;
        } finally {
            System.out.println("around:finally:" + method);
        }
    }
}
```

**Vì sao đoạn code an toàn trong phạm vi demo:**
- `method.equals("greet")` được biểu diễn bằng `"greet".equals(method)` và điều kiện `&&` short-circuit, nên code chỉ truy cập `pjp.getArgs()[0]` cho `greet`, method có một parameter. `fail`/`outer` không truy cập index không tồn tại.
- `"skip"` trả trực tiếp trước `proceed`; `finally` vẫn chạy bởi Java semantics.
- `"alias"` tạo một bản sao mảng argument rồi gọi `proceed(newArgs)`; target nhận `"Alice"` dù caller truyền `"alias"`.
- Case thông thường gọi `proceed()` đúng một lần và giữ nguyên result.
- Case target throw được catch để log, sau đó rethrow; advice không nuốt exception.
- `finally` in một dòng cho mọi đường return/throw mà advice thực sự đã tham gia.
- Pointcut chỉ áp dụng cho `GreetingService`, không tự advise `AroundAspect` hoặc Spring infrastructure.

Trong production, đừng rẽ nhánh theo string method name kiểu demo để thực hiện chính sách khác nhau; hãy dùng **named pointcuts, method annotation hoặc advisor riêng**. Demo cố tình gom các nhánh vào một advice để người học nhìn thấy từng khả năng của `ProceedingJoinPoint`.

### 8.6. Đăng ký Spring beans và bật AOP

Tệp `src/main/java/demo/around/AppConfig.java`:

```java
package demo.around;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public GreetingService greetingService() {
        return new GreetingService();
    }

    @Bean
    public AroundAspect aroundAspect() {
        return new AroundAspect();
    }
}
```

`@Bean aroundAspect()` đăng ký aspect thành Spring bean; `@Aspect` nói với Spring cách đọc advice. `@EnableAspectJAutoProxy(proxyTargetClass=true)` bật auto-proxy creator và ưu tiên proxy theo lớp, nhờ đó ta lấy service theo `GreetingService.class`. Đây là Spring Framework thuần, nên không phụ thuộc mặc định của Spring Boot.

### 8.7. Main chạy các nhánh

Tệp `src/main/java/demo/around/DemoApplication.java`:

```java
package demo.around;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApplication {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            GreetingService service =
                    context.getBean(GreetingService.class);

            System.out.println(
                    "proxy=" + AopUtils.isAopProxy(service));

            System.out.println("result=" + service.greet("Bob"));
            System.out.println("result=" + service.greet("alias"));
            System.out.println("result=" + service.greet("skip"));

            try {
                service.fail();
            } catch (IllegalStateException ex) {
                System.out.println(
                    "caller:fail:" + ex.getMessage());
            }

            service.outer();
        }
    }
}
```

### 8.8. Lệnh chạy và stdout chính xác

Từ thư mục chứa `build.gradle`:

```bash
gradle run
```

Nếu đã có Gradle Wrapper, dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows.

Các dòng bên dưới là **output do chương trình Java tự in**, không tính Gradle hoặc logging hệ thống:

```text
proxy=true
around:before:greet
target:greet:Bob
around:after:greet
around:finally:greet
result=Hello Bob
around:before:greet
target:greet:Alice
around:after:greet
around:finally:greet
result=Hello Alice
around:before:greet
around:short-circuit:greet
around:finally:greet
result=SKIPPED
around:before:fail
target:fail
around:throw:fail:IllegalStateException
around:finally:fail
caller:fail:boom
around:before:outer
target:outer
target:greet:inside
around:after:outer
around:finally:outer
```

### 8.9. Đối chiếu từng lời gọi với proceed

| Caller gọi | Advice thực hiện | Target có chạy? | Caller nhìn thấy |
| --- | --- | --- | --- |
| `greet("Bob")` | `proceed()` với args gốc | Có: `target:greet:Bob` | `Hello Bob` |
| `greet("alias")` | `proceed(new Object[]{"Alice"})` | Có: `target:greet:Alice` | `Hello Alice` |
| `greet("skip")` | Return sớm, không `proceed` | **Không** | `SKIPPED` |
| `fail()` | `proceed()` rồi log/rethrow | Có, target throw | `IllegalStateException("boom")` |
| `outer()` | `proceed()`, target gọi `this.greet` | `outer` và `greet` đều chạy trong target | Không có around advice lồng cho `greet("inside")` |

**Chú ý đường `outer()`:** Spring AOP proxy advice khớp method `outer` được caller gọi. Nhưng lời gọi `greet("inside")` phát sinh **trong target** nên không có một dòng `around:before:greet` thứ hai. Không phải `@Around` không biết đến `greet`; đường gọi nội bộ đã bypass proxy.

**Chú ý đường `skip`:** Dù method `greet` là một Spring bean method và có pointcut phù hợp, Spring không tự ép target chạy. Advice đã trả kết quả từ trước `proceed()`, chứng minh quyền điều khiển của `@Around`.

## 9. @Around khác gì @Before, @AfterReturning, @AfterThrowing và @After?

| Nhu cầu | Loại advice nên xem trước | Vì sao |
| --- | --- | --- |
| Chỉ log trước khi method chạy | `@Before` | Không cần tự quản lý `proceed()` |
| Chỉ nhận normal return value | `@AfterReturning` | Có `returning` binding, không cần bao quanh invocation |
| Chỉ log exception thoát ra | `@AfterThrowing` | Có `throwing` binding, nêu rõ failure outcome |
| Cleanup khi method hoàn tất bình thường/exception | `@After` | Semantics after-finally, không cần đổi flow |
| Đo thời gian của đúng một invocation trong một scope | `@Around` | Biến `started` local đi qua trước–sau, dùng `finally` |
| Thay input trước khi target chạy | `@Around` | `proceed(newArgs)` |
| Trả cache/fallback thay target | `@Around` | Có thể short-circuit |
| Retry có kiểm soát | `@Around` hoặc retry interceptor chuyên trách | Có thể gọi `proceed()` nhiều lần |

Nguyên tắc Spring: **dùng advice ít quyền nhất đáp ứng nhu cầu**. Không có lý do dùng `@Around` chỉ để `System.out.println("before")` nếu `@Before` đã đủ.

## 10. Phối hợp @Around với Spring transaction, cache và retry

Một Spring bean có thể đồng thời chịu ảnh hưởng của `@Around` do ứng dụng khai báo và các interceptor do transaction, caching hoặc retry infrastructure cung cấp. **Vị trí trong chain quyết định điều gì được đo và điều gì được chạy lại.**

- **Cache:** nếu cache interceptor ở phía trong trả cache hit mà không gọi tiếp, `@Around` nằm ngoài cache vẫn chạy, nhưng target không chạy. Nếu cache nằm ngoài và short-circuit, advice ở trong có thể không được gọi.
- **Transaction:** khi retry nằm ngoài transaction advice, mỗi attempt *có thể* bước qua transaction interceptor riêng theo propagation/transaction configuration; transaction nằm ngoài retry lại có thể bao nhiều attempts trong một transaction. Không suy ra mỗi attempt luôn có transaction mới.
- **Retry:** gọi `proceed()` nhiều lần có thể lặp việc ghi DB, gửi message hoặc gọi dịch vụ thanh toán. Ngoại lệ xảy ra sau side effect không chứng minh thao tác trước đó chưa thành công.
- **Thứ tự:** `@Order`/advisor order có ảnh hưởng đến ai vào trước và quan sát kết quả nào. Thứ tự annotation trong source không phải chứng cứ về runtime order.

Khi kiểm tra một aspect, đo riêng **số lần caller gọi proxy**, **số lần advice vào**, và **số lần target hoặc side effect thực thi**. Những bài về multiple-`proceed()`, retry side effects và bảo toàn hợp đồng nghiệp vụ mở rộng các tình huống này bằng phép thử cụ thể.

## 11. Những lỗi Spring @Around thường gặp

| Triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Target method không chạy nhưng caller nhận giá trị | Advice trả kết quả mà không gọi `proceed()` | Kiểm tra nhánh return sớm; chỉ short-circuit khi chủ đích |
| Caller nhận `null` dù target trả `String` | Around advice khai báo `void` hoặc bỏ return kết quả | Dùng `Object`, `return pjp.proceed()` |
| Exception bị biến mất | `catch` và return fallback vô tình | Rethrow exception trừ khi policy fallback hợp lệ |
| Log sau `proceed()` không xuất hiện khi target throw | Code sau proceed chỉ chạy ở normal return | Đưa cleanup/metric tổng thời gian vào `finally` |
| Lỗi `ArrayIndexOutOfBoundsException` trong advice | Dùng `pjp.getArgs()[0]` cho method không có params | Giới hạn pointcut/arity hoặc kiểm tra args trước |
| `IllegalArgumentException` khi `proceed(newArgs)` | Sai số args hoặc kiểu không tương thích | Cung cấp đủ argument đúng thứ tự và kiểu |
| `ClassCastException` ở caller | Advice return object không tương thích target return type | Bảo toàn return contract |
| Hai lần side effect | Gọi `proceed()` nhiều lần | Cân nhắc idempotency, retry boundary |
| Aspect không chạy khi `this.method()` | Self-invocation bypass Spring proxy | Tách bean cộng tác, gọi qua proxy |
| Aspect không chạy trên bean tạo bằng `new` | Không có Spring AOP post-processing | Inject bean từ context |
| `final`/`private` method không được CGLIB advice | Không override được | Chọn thiết kế method/proxy phù hợp |
| Đo thời gian đột nhiên bao cả retry/cache | Advisor order thay đổi phạm vi đo | Định nghĩa metric boundary và test ordering |
| `@Around` method không khởi tạo | Aspect không phải Spring bean hoặc chưa bật AOP | Kiểm tra `@Bean`/`@Component` và `@EnableAspectJAutoProxy` |
| Pointcut quá rộng | `execution(* *(..))` không giới hạn type/package | Kết hợp `within`/service pattern |
| AspectJ weaving chạy khác khi thay `proceed(Object[])` | Spring proxy và AspectJ compiled advice có quy tắc args khác | Tham khảo tài liệu về binding đúng thứ tự nếu cần portability |

### Không log mọi argument không kiểm soát

`pjp.getArgs()` trả object thực tế; nhiều service arguments có thể chứa password, bearer token, nội dung cá nhân hoặc cấu trúc lớn. Đừng tự động `Arrays.toString(pjp.getArgs())` trên toàn tầng service trong production. Sử dụng allowlist, masking và metadata tối thiểu, đồng thời quan tâm overhead serialization/toString.

## 12. Checklist debug @Around có vẻ không hoạt động

1. **Aspect đã được Spring đăng ký?** Lớp có `@Aspect` và được tạo bằng `@Component`/`@Bean`? `@Aspect` một mình không đăng ký bean.
2. **AOP đã bật ở context sở hữu target?** Có `@EnableAspectJAutoProxy` hoặc Boot AOP auto-configuration đúng không?
3. **Proxy có thật không?** Kiểm tra `AopUtils.isAopProxy(bean)` và nếu cần `isJdkDynamicProxy`/`isCglibProxy`.
4. **Pointcut có match method?** `execution(...)`, package/type/visibility/overload có đúng?
5. **Call path qua proxy?** Client gọi Spring-managed bean hay tự `new`? Target có dùng `this.otherMethod()` không?
6. **Method được proxy hóa hợp lệ?** `final` class, `private`/`final` method hoặc interface không expose method có gây giới hạn không?
7. **Advice đã gọi `proceed()`?** Có nhánh `return` trước đó hoặc exception từ code before không?
8. **Kiểu và số args của `proceed(newArgs)` đúng?** Đặc biệt khi method có nhiều parameters hoặc varargs.
9. **Advice trả đúng result/throw đúng exception?** Tránh `void` around hoặc swallow exception vô chủ đích.
10. **Advisor order/caching/transaction có thay kết quả?** Có một interceptor bên ngoài short-circuit khiến around không được gọi?
11. **Có multiple proceed hoặc retry?** Kiểm tra số lần target chạy và side effects.
12. **Integration test có dùng Spring context thật?** Unit test gọi trực tiếp target bằng `new` không chứng minh AOP có hay không.

Ví dụ snippet chẩn đoán (giả sử đã có `ApplicationContext context` và bean tên `greetingService`):

```java
Object bean = context.getBean("greetingService");

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
```

Cần import `org.springframework.aop.support.AopUtils`. Có proxy chưa đủ để khẳng định **mọi method** hoặc **mọi lời gọi** đều đi qua advice; vẫn phải kiểm tra pointcut và đường gọi.

## 13. Quy tắc thiết kế @Around an toàn

- **Giữ đơn giản:** nếu chỉ cần pre/post event, cân nhắc `@Before`, `@AfterReturning`, `@AfterThrowing` hoặc `@After`.
- **Mặc định proceed đúng một lần:** chỉ đổi sang 0/n lần với mục tiêu và tests rõ ràng.
- **Bảo toàn result và exception:** `return pjp.proceed()` trong đường thông thường; `throw ex` sau khi log nếu không chủ đích fallback.
- **Dùng `finally` cho cleanup:** đảm bảo advice không bỏ phần đo thời gian khi method throw.
- **Đúng API:** first parameter `ProceedingJoinPoint`, return `Object`, `throws Throwable`.
- **Thay argument có kiểm soát:** clone array, đúng arity/type và test tác động cache/transaction/advisors.
- **Tránh state không thread-safe:** dùng biến local trong mỗi invocation; nếu aspect singleton có mutable field, quản lý concurrency.
- **Bảo vệ dữ liệu:** không log payload nhạy cảm từ `getArgs()` hoặc result/exception không giới hạn.
- **Xác định điểm quan sát:** timing bên ngoài cache/retry khác timing target attempt.
- **Test cả đường bình thường và lỗi:** return value, null, exception, short-circuit, replacement và self-invocation.
- **Không tự tạo object target bằng `new`:** để Spring bean proxy quản lý cross-cutting behavior.

## 14. Kết luận

Trong Spring Aspect, **`@Around` là advice bao quanh method invocation và `ProceedingJoinPoint` là công cụ điều khiển phần chain phía trong**. Ba thao tác quyết định semantics là:

- `proceed()`: tiếp tục với original arguments;
- `proceed(Object[])`: tiếp tục với arguments được thay thế hợp lệ;
- **không gọi `proceed()`**: có thể chủ động bỏ qua target và trả kết quả/exception do advice tạo.

Advice có thể quan sát cả return và throw, dùng `finally` để dọn dẹp, nhưng cũng có thể gây lỗi khó phát hiện nếu vô tình nuốt exception, trả sai kiểu, thay tham số ngoài dự kiến hoặc lặp target có side effect.

Với Spring AOP, hãy luôn đặt câu hỏi **“lời gọi có đi qua Spring-managed proxy không?”** rồi mới xét `@Around`. `ProceedingJoinPoint` không thay thế proxy boundary, không sửa self-invocation và không biến constructor/private/final methods thành join points được Spring AOP intercept.

### Tài liệu Spring chính thức

- [Spring Framework — Declaring Advice: Around Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): kiểu method `@Around`, `ProceedingJoinPoint`, `proceed()`/`proceed(Object[])`, return values, semantics zero/one/many proceed, advice ordering.
- [Spring Framework — AOP Concepts](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html): các loại advice và vai trò quyền điều khiển của around advice.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): kích hoạt proxy và đăng ký Spring `@Aspect`.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB proxy, self-invocation và các giới hạn.
- [Spring Framework — Advice API](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html): `MethodInterceptor` và cách `proceed` đi tiếp trong interceptor chain.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-002">proceed() để tiếp tục chuỗi interceptor và phương thức đích</a>

<details>
<summary>Xem chi tiết</summary>

# proceed() để tiếp tục chuỗi interceptor và phương thức đích trong Spring AOP

## 1. Vấn đề cốt lõi

Trong **Spring AOP proxy-based**, `@Around` advice có quyền chạy mã trước và sau một lời gọi phương thức được pointcut chọn, đồng thời quyết định **có tiếp tục đường gọi ấy hay không**. Điểm quyết định quan trọng nhất chính là:

~~~java
Object result = pjp.proceed();
~~~

Trong đoạn này, `pjp` là `org.aspectj.lang.ProceedingJoinPoint`, do Spring chuyển vào phương thức advice. `proceed()` **chuyển điều khiển tới phần còn lại của chuỗi Spring AOP interceptor/advice**, và cuối chuỗi sẽ tới method thực của target **nếu không có một interceptor phía trong chủ động trả về hoặc ném lỗi trước đó**.

Đặc biệt, không nên hiểu `proceed()` là một lệnh đơn giản tương đương `target.method()`:

- Nó đi qua **những advisor/interceptor còn lại** có liên quan tới invocation hiện tại.
- Một interceptor phía trong có thể ghi log, bắt đầu transaction, cache hit, chặn lời gọi hoặc ném exception.
- Chỉ khi chuỗi bên trong chuyển tiếp tới cuối thì method target mới thật sự được gọi.
- Giá trị trả về và exception đi ngược từ chuỗi phía trong về `@Around`, rồi cuối cùng tới caller, trừ khi advice ngoài thay đổi chúng.
- `proceed()` có thể được gọi **một lần, nhiều lần hoặc không lần nào** về mặt API; lựa chọn này thay đổi hành vi nghiệp vụ rất đáng kể.

**Phạm vi bài học:** chỉ tập trung vào `@Around` và chuỗi Spring Aspect/Spring AOP trên Spring-managed beans; không lặp lại kiến thức Aspect tổng quát hay giải thích AspectJ bytecode weaving như một cơ chế tương đương.

## 2. Ranh giới của một lời gọi Spring AOP

Sơ đồ minh họa một bean `GreetingService` được hai advisor can thiệp:

~~~text
Caller
   |
   v
Spring AOP proxy
   |
   +--> Outer @Around advice
   |      |
   |      +--> trước proceed()
   |      |
   |      +--> pjp.proceed()
   |                |
   |                +--> Inner @Before advice
   |                |
   |                +--> Advisor/interceptor khác, nếu có
   |                |
   |                +--> target.greet(...)
   |                |
   |                +--> trả result hoặc ném Throwable
   |      |
   |      +--> sau proceed() / catch / finally
   |
   v
Caller nhận result hoặc Throwable
~~~

Lúc Spring chuẩn bị bean, auto-proxy creator tìm **advisor phù hợp** dựa trên `@Aspect` beans, pointcut và type/method. Khi client gọi method **qua proxy**, Spring triển khai interceptor chain cho invocation đó. Thứ tự các advisors có thể ảnh hưởng ai nằm ngoài, ai nằm trong.

Tại `pjp.proceed()`:

1. Chuỗi xử lý tiến tới advisor kế tiếp có thể áp dụng.
2. Nếu advisor kế tiếp là `@Before`, logic của nó chạy trước khi chuyển tiếp.
3. Nếu một `@Around` bên trong **không gọi proceed**, lời gọi có thể dừng tại đây mà target không chạy.
4. Khi không còn interceptor nào, Spring gọi method đích trên target.
5. Kết quả hoặc exception quay về qua các interceptor đã đi vào.

Vì vậy nên nói **“proceed tiếp tục chuỗi interceptor hướng tới target”**, không nói “proceed chắc chắn gọi trực tiếp target ngay lập tức”.

## 3. Một @Around advice đúng cấu trúc

Ví dụ trong một Spring-managed `@Aspect`:

~~~java
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TimingAspect {

    @Around("execution(* example.GreetingService.greet(..))")
    public Object aroundGreet(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long elapsed = System.nanoTime() - start;
            System.out.println("duration nanos: " + elapsed);
        }
    }
}
~~~

Điểm cần hiểu:

- `@Around` khai báo pointcut để Spring biết **lời gọi nào được bao quanh**.
- Tham số **đầu tiên** của around advice phải là `ProceedingJoinPoint`.
- Kiểu trả về `Object` cho phép advice truyền hoặc biến đổi return value. Với method target có giá trị trả về, không nên khai báo around advice trả về `void` vì Spring có thể trả `null` cho caller.
- `throws Throwable` cho phép exception từ method/advisor bên trong tiếp tục truyền ra ngoài; tránh bắt rồi nuốt lỗi vô ý.
- `pjp.proceed()` là điểm đi vào chuỗi phía trong.
- `finally` vẫn chạy khi chuỗi phía trong trả về hoặc ném exception, miễn là invocation đã đi vào advice này.
- Biến `start` là biến cục bộ **theo từng invocation**, không bị nhiều thread ghi đè như một field mutable chung trên singleton aspect.

Đây là **snippet minh họa**, chưa phải toàn bộ project độc lập; project đầy đủ ở mục 6 sẽ cho stdout xác định được.

## 4. So sánh “có proceed” và “không proceed”

### 4.1. Có `proceed()`

~~~java
@Around("execution(* example.GreetingService.greet(..))")
public Object trace(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("before");
    Object value = pjp.proceed();
    System.out.println("after");
    return value;
}
~~~

Với một target `greet()` trả về bình thường, chuỗi log thường có dạng:

~~~text
before
<advice phía trong, nếu có>
<method target>
after
~~~

Đoạn “after” sau `proceed()` **không chạy nếu `proceed()` ném exception**, trừ khi bạn xử lý riêng qua `catch` hoặc `finally`.

### 4.2. Không gọi `proceed()`

~~~java
@Around("execution(* example.GreetingService.cached(..))")
public Object cachedValue(ProceedingJoinPoint pjp) {
    return "CACHED";
}
~~~

Nếu advice này được gọi và trả về `"CACHED"` mà **không proceed**:

- Caller nhận giá trị do advice tạo ra.
- Các interceptor/advice **ở phía trong** chưa được gọi qua lần invocation này.
- Method target **không được gọi** trong đường đi đó.
- Đây có thể là hành vi **có chủ đích** của cache hit, policy chặn lời gọi hoặc mock/test interception.
- Nếu bỏ quên `proceed()` do lỗi lập trình, nghiệp vụ có thể bị “biến mất” một cách rất khó nhận diện.

**Không nhầm**: nếu một outer advice đã chạy, logic “after/finally” của chính outer vẫn có thể chạy dù nó shortcut. Câu “mọi advice bị bỏ qua” là sai; chỉ những tầng không được chuyển tiếp tới mới không chạy.

### 4.3. Có `proceed()` nhưng không trả lại kết quả

~~~java
// Cách viết này không phù hợp với method có return value:
@Around("execution(* example.GreetingService.greet(..))")
public Object incorrect(ProceedingJoinPoint pjp) throws Throwable {
    pjp.proceed();
    return null;
}
~~~

Method target **có thể đã chạy thành công**, nhưng caller nhìn thấy `null` thay vì kết quả thật. Nếu return type bên ngoài là primitive, việc Spring cố chuyển `null` về primitive có thể gây lỗi. Mặc định nên dùng `return pjp.proceed();`, trừ khi **cố ý** biến đổi hoặc thay thế kết quả theo hợp đồng rõ ràng.

## 5. Giá trị trả về và exception đi qua proceed()

### 5.1. Trả về bình thường

~~~java
Object result = pjp.proceed();
return result;
~~~

Giá trị từ các tầng phía trong được truyền về caller. Một around advice cũng có thể làm hậu xử lý:

~~~java
Object result = pjp.proceed();
return ((String) result).trim();
~~~

Đoạn trên **chỉ an toàn** khi pointcut thực sự giới hạn vào method trả `String` không null và việc trim nằm trong hợp đồng nghiệp vụ. Không cast/trimming một cách mù quáng trên pointcut chọn mọi method.

### 5.2. Target hoặc interceptor phía trong ném exception

~~~java
@Around("execution(* example.GreetingService.fail(..))")
public Object traceFailure(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("before");
    try {
        return pjp.proceed();
    } finally {
        System.out.println("finally");
    }
}
~~~

Nếu `fail()` ném `IllegalStateException`, khối `finally` chạy rồi exception tiếp tục đi tới caller. Có thể bắt exception để log:

~~~java
try {
    return pjp.proceed();
} catch (Throwable failure) {
    System.out.println("failed: " + failure.getClass().getSimpleName());
    throw failure; // Giữ nguyên hành vi lan truyền lỗi.
}
~~~

**Không nên** thay exception bằng kết quả thành công trong một advice dùng chung nếu không có chính sách nghiệp vụ cụ thể. Việc nuốt lỗi có thể phá vỡ transaction rollback, retry policy, HTTP error mapping hoặc các cơ chế infrastructure khác.

### 5.3. Thứ tự xử lý khi xảy ra lỗi

Nếu có outer `@Around` và inner `@Before`:

- Outer có thể đã in “before”.
- Inner `@Before` chạy khi outer `proceed()`.
- Target ném exception.
- Không có log “after” đặt ngay sau `proceed()` nếu không xử lý exception.
- Outer `finally` vẫn chạy.
- Caller nhận exception nếu outer không chuyển đổi nó.

Nếu **inner advice ném exception trước khi target chạy**, outer `finally` vẫn có thể thực thi, nhưng target method **không được gọi**. Không lấy sự xuất hiện của outer finally làm bằng chứng target đã thực thi.

## 6. Ví dụ Java 17+ chạy được: bốn trường hợp của proceed()

Đây là project Maven độc lập để kiểm tra thực tế:

- `GreetingService` là Spring bean.
- `FlowAspect`: outer `@Around`, cố ý dùng `@Order(1)`.
- `TraceAspect`: inner `@Before`, dùng `@Order(2)` để bảo đảm thứ tự **giữa hai aspect khác nhau**.
- `greet(String)`: outer gọi `proceed(Object[])` với đối số đã biến đổi.
- `cached()`: outer **không proceed**, trả về kết quả shortcut.
- `fail()`: `proceed()` đưa lời gọi đến target ném exception; outer `finally` vẫn chạy.
- `callInternally()`: outer proceed cho method ngoài; method target gọi `this.greet(...)` nên invocation bên trong **không đi lại qua Spring proxy**.

Sử dụng **Spring Framework 6.2.19**, **AspectJ Weaver 1.9.25.1** và **Java 17+**. Không cần database, web server hay Spring Boot.

### 6.1. Cấu trúc thư mục

~~~text
spring-around-proceed-demo/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── GreetingService.java
    ├── FlowAspect.java
    ├── TraceAspect.java
    └── Main.java
~~~

### 6.2. `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-around-proceed-demo</artifactId>
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

Spring Framework BOM quản lý các phiên bản module Spring nhất quán; AspectJ Weaver phục vụ phân tích biểu thức `@AspectJ` trong Spring AOP. Sự hiện diện của `aspectjweaver` **không tự bật AspectJ bytecode weaving**.

### 6.3. `GreetingService.java` — đối tượng nghiệp vụ

~~~java
package example;

public class GreetingService {

    public String greet(String name) {
        System.out.println("target: greet " + name);
        return "Hello " + name;
    }

    public String cached() {
        System.out.println("target: cached");
        return "FRESH";
    }

    public String fail() {
        System.out.println("target: fail");
        throw new IllegalStateException("boom");
    }

    public String callInternally() {
        System.out.println("target: callInternally");
        return this.greet("Self");
    }
}
~~~

Method `cached()` **có code thật** để kiểm tra rằng target không được gọi khi shortcut. Với `callInternally()`, lời gọi `this.greet("Self")` sẽ giúp nhận diện self-invocation đã vượt ngoài đường gọi qua AOP proxy.

### 6.4. `FlowAspect.java` — outer around advice

~~~java
package example;

import java.util.Locale;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(1)
public class FlowAspect {

    @Around("execution(* example.GreetingService.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getName();
        System.out.println("outer: before " + method);

        try {
            if ("cached".equals(method)) {
                System.out.println("outer: shortcut");
                return "CACHED"; // Không proceed: inner/target không chạy.
            }

            Object result;
            if ("greet".equals(method)) {
                Object[] args = pjp.getArgs().clone();
                args[0] = ((String) args[0]).toUpperCase(Locale.ROOT);
                result = pjp.proceed(args);
            } else {
                result = pjp.proceed();
            }

            System.out.println("outer: after " + method);
            return result;
        } finally {
            System.out.println("outer: finally " + method);
        }
    }
}
~~~

**Điểm học chính:**

- Với `greet`, advice tạo một **bản sao** array arguments, sửa argument đầu và gọi `pjp.proceed(args)`. Trong **Spring AOP**, mảng truyền vào phải tương ứng với các **tham số của method đích**.
- Với `cached`, advice trả `CACHED`, không có `proceed`. `finally` của chính outer vẫn chạy theo quy tắc Java.
- Với `fail` và `callInternally`, `pjp.proceed()` không đối số giữ nguyên arguments của caller.
- Dòng `outer: after` chỉ chạy khi `proceed` trả về bình thường; dòng `outer: finally` chạy cả khi target/advisor bên trong ném exception.
- `@Order(1)` đặt aspect này **ngoài** aspect `@Order(2)` trong đường đi vào của cùng join point.

**Giới hạn minh họa:** Chương trình intentionally dựa vào tên method để chọn branch cho dễ nhìn stdout. Trong mã production, nên đặt pointcut hẹp/annotation/bean scope và tách advice theo hợp đồng method, không đặt một switch lớn trên tên mọi method của service.

### 6.5. `TraceAspect.java` — advisor phía trong

~~~java
package example;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;

@Aspect
@Order(2)
public class TraceAspect {

    @Before("execution(* example.GreetingService.*(..))")
    public void before(JoinPoint jp) {
        System.out.println("inner: before "
            + jp.getSignature().getName());
    }
}
~~~

Aspect này giúp chứng minh `proceed()` **không gọi thẳng vào target**. Đường đi bình thường sẽ thấy `inner: before ...` **sau** `outer: before ...` và **trước** dòng `target: ...`.

Nếu outer quyết định shortcut thì inner advice không được gọi cho invocation ấy. Không có mặc định rằng tất cả advisors luôn thực thi.

### 6.6. `AppConfig.java` — đăng ký Spring beans

~~~java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public GreetingService greetingService() {
        return new GreetingService();
    }

    @Bean
    public FlowAspect flowAspect() {
        return new FlowAspect();
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }
}
~~~

Cả hai lớp `@Aspect` được **đăng ký Spring beans bằng `@Bean`**, vì vậy không cần thêm `@Component`. Spring AOP phát hiện advice và tạo proxy cho `GreetingService` vì pointcut phù hợp.

`proxyTargetClass = true` khiến ví dụ dùng class-based proxy để có thể lấy service trực tiếp bằng `GreetingService.class`. Dù dùng JDK hay CGLIB, **cách `proceed` chuyển tiếp chuỗi advisor vẫn theo nguyên tắc Spring AOP proxy-based**.

### 6.7. `Main.java` — gọi bốn trường hợp

~~~java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {
            GreetingService service = context.getBean(GreetingService.class);

            System.out.println("proxy: " + AopUtils.isAopProxy(service));

            System.out.println("-- greet with modified argument --");
            System.out.println("caller: " + service.greet("An"));

            System.out.println("-- shortcut --");
            System.out.println("caller: " + service.cached());

            System.out.println("-- exception --");
            try {
                service.fail();
            } catch (IllegalStateException failure) {
                System.out.println("caller caught: " + failure.getMessage());
            }

            System.out.println("-- self invocation --");
            System.out.println("caller: " + service.callInternally());
        }
    }
}
~~~

Tại thư mục có `pom.xml`:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
~~~

### 6.8. Stdout mong đợi của chương trình

Các dòng in bởi chính những class trong project, theo đúng thứ tự:

~~~text
proxy: true
-- greet with modified argument --
outer: before greet
inner: before greet
target: greet AN
outer: after greet
outer: finally greet
caller: Hello AN
-- shortcut --
outer: before cached
outer: shortcut
outer: finally cached
caller: CACHED
-- exception --
outer: before fail
inner: before fail
target: fail
outer: finally fail
caller caught: boom
-- self invocation --
outer: before callInternally
inner: before callInternally
target: callInternally
target: greet Self
outer: after callInternally
outer: finally callInternally
caller: Hello Self
~~~

Maven/Spring có thể xuất thêm log của môi trường; đoạn trên chỉ bao gồm các `System.out.println` của ví dụ.

### 6.9. Giải thích lần lượt

**Greet, có proceed(Object[]):**

1. Outer in `outer: before greet`.
2. Outer đổi argument `"An"` thành `"AN"` và gọi `pjp.proceed(args)`.
3. Spring chạy inner `@Before` rồi target `greet("AN")`.
4. Method trả `"Hello AN"`, đi về outer.
5. Outer in `after`, rồi `finally`, sau đó caller nhận `Hello AN`.

Đây là bằng chứng trực tiếp rằng `proceed` đã chuyển qua **advisor khác rồi mới tới target**, không chỉ gọi target ngay lập tức.

**Cached, không proceed:**

- Outer nhận invocation rồi trả `CACHED`.
- Không có `inner: before cached` hoặc `target: cached`.
- `finally` của outer vẫn chạy trước khi caller thấy kết quả.
- Đây là **short-circuit có chủ đích**, không phải lỗi Spring quên gọi target.

**Fail, exception:**

- Outer gọi `proceed()`, inner chạy, target in `target: fail` rồi ném `IllegalStateException("boom")`.
- Dòng `outer: after fail` **không xuất hiện** vì `proceed` không trả bình thường.
- `outer: finally fail` vẫn xuất hiện.
- Caller bắt exception và in `caller caught: boom`; advice không nuốt lỗi.

**CallInternally, self-invocation:**

- Caller gọi `callInternally()` qua proxy, nên outer và inner chạy cho **method ngoài này**.
- Trong target, `this.greet("Self")` là lời gọi nội bộ; nó **không quay lên AOP proxy**.
- Vì thế không có `outer: before greet`, không có `inner: before greet`, và đối số vẫn là `Self` (không bị upper-case).
- Kết quả `Hello Self` minh họa ranh giới proxy; việc outer từng gọi `proceed()` **không biến các self-call sau đó thành các invocation qua proxy**.

## 7. proceed() và proceed(Object[]) khác nhau ra sao?

Theo tài liệu Spring Framework:

| API | Đối số truyền tiếp | Ứng dụng |
| --- | --- | --- |
| `pjp.proceed()` | Dùng arguments gốc của invocation đang được advice | Tiếp tục bình thường mà không can thiệp tham số |
| `pjp.proceed(new Object[] { ... })` | Trong Spring AOP, dùng các phần tử của mảng làm **method arguments** của phần invocation được chuyển tiếp | Normalize input, điều chỉnh tham số có kiểm soát |
| Không gọi proceed | Không gửi invocation tới chain phía trong | Cache hit, access denial, fallback có chủ đích |
| Gọi proceed nhiều lần | Chạy lại phần chain phía trong nhiều lần nếu cấu hình cho phép | Retry, nhưng dễ nhân đôi side effects |

**Điều kiện của `proceed(Object[])`:**

1. Mảng phải có số phần tử **phù hợp với số method parameters** tại join point; ví dụ target `greet(String)` cần đúng một phần tử.
2. Các phần tử phải có kiểu tương thích; `greet(String)` không thể nhận một `Integer` tùy ý.
3. Không dùng `pjp.getArgs()` rồi sửa trực tiếp nếu mục đích chỉ là biến đổi cục bộ và bạn không muốn gây side effect lên array tham số đang được các interceptor khác quan sát. Trong ví dụ dùng `clone()`.
4. Nếu pointcut match nhiều method có chữ ký khác nhau, việc tự sửa phần tử `args[0]` có thể ném `ArrayIndexOutOfBoundsException` hoặc `ClassCastException`. Hãy giới hạn pointcut và kiểm tra chữ ký rõ ràng.
5. Khi method nhận primitive parameter, mảng `Object[]` chứa các wrapper values phù hợp như `Integer` cho `int`; không truyền null cho primitive.
6. Việc thay args có thể thay đổi dữ liệu nhìn thấy bởi các interceptor phía trong; nên áp dụng chỉ khi business contract cho phép.

**Khác biệt cần nhớ giữa Spring AOP và AspectJ compiler/weaver:** Với Spring AOP proxy-based, mảng `proceed(Object[])` tương ứng với **arguments của method phía dưới**. Với một số dạng around advice được biên dịch bằng AspectJ truyền thống, ngữ nghĩa binding/thay thế vị trí có thể khác. Đừng sao chép code `proceed(args)` từ cơ chế weaving khác mà không đọc hướng dẫn tương thích của Spring Framework. Bài này chỉ sử dụng **Spring AOP**.

## 8. “Tiếp tục chuỗi” chứ không nhất thiết tới target

Xem đường đi:

~~~text
Outer @Around
   |
   | proceed()
   v
Inner @Around
   |
   +-- cache hit --> return cached value
   |
   +-- cache miss --> proceed()
                        |
                        v
                    Inner @Before
                        |
                        v
                   Target method
~~~

Nếu cache hit ở inner:

- Outer **đã gọi `proceed()`**.
- Inner đã nhận lời gọi và quyết định return sớm.
- Target không được gọi.
- Outer vẫn có thể nhận giá trị và in log `after`.

Do đó khẳng định “outer đã gọi proceed nên target chắc chắn chạy” là **sai**.

Ngược lại, nếu inner `@Before` ném exception, outer `proceed()` cũng ném ra ngoài, nhưng target chưa được gọi. Cần debug **từng tầng**, không chỉ kiểm tra một lệnh proceed trong source.

Một bean còn có thể chịu infrastructure advisors phục vụ transaction, caching, security hoặc tracing. Thứ tự thực tế cần được kiểm tra từ Spring context/proxy advisor chain; **không giả định tất cả advisor đều đến từ @Aspect người dùng viết**.

## 9. Có thể gọi proceed nhiều lần không?

Spring Framework documentation nêu rõ around advice có thể gọi `proceed()` **không lần nào, một lần hoặc nhiều lần**. Điều này hợp lệ về mặt API, nhưng mỗi lần proceed có thể kích hoạt lại **cả phần chuỗi phía trong**.

Ví dụ **snippet minh họa**, không thuộc project chạy độc lập ở mục 6:

~~~java
@Around("execution(* example.PaymentService.send(..))")
public Object retryOnce(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (TransientOperationException ex) {
        return pjp.proceed();
    }
}
~~~

Đoạn trên giả định `TransientOperationException` và `PaymentService` tồn tại. Nó minh họa điều gì?

- Nếu lần đầu fail, lần thứ hai có thể chạy lại **inner interceptors và target**.
- Nếu operation có side effects như tạo đơn hàng, trừ tiền, gửi email hoặc publish message, lặp lại invocation có thể gây **thực thi hai lần**.
- Nếu các interceptor có transaction boundary, retry nằm **ngoài hoặc trong** transaction interceptor có thể mang ngữ nghĩa rất khác.
- Không phải mọi exception là transient; lọc đúng lỗi và quy định số lần retry, delay/backoff, idempotency, observability.
- Một `@Around` chứa retry thiếu kiểm soát có thể nguy hiểm hơn nhiều so với dùng Spring Retry/Resilience infrastructure được cấu hình đúng.

**Không dùng code mẫu này nguyên xi trong production**. Không nên dùng while vô hạn hoặc gọi proceed lặp lại chỉ để “chắc chắn method chạy”. Những nghiệp vụ không idempotent phải thiết kế retry cẩn trọng, không dựa duy nhất vào cú pháp của AOP.

## 10. Thứ tự advisor và @Order

Khi nhiều advice ở các aspect khác nhau cùng match một method, Spring có các quy tắc precedence. Trong ví dụ:

~~~java
@Aspect
@Order(1)
public class FlowAspect { /* outer @Around */ }

@Aspect
@Order(2)
public class TraceAspect { /* inner @Before */ }
~~~

Với @Order:

- **Số nhỏ hơn** có precedence cao hơn.
- Aspect precedence cao hơn thường chạy **trước khi đi vào join point**, và **sau khi đi ra**.
- Vì vậy outer around ghi `before` trước inner before; outer after/finally chạy khi chuỗi phía trong đã trả về hoặc ném lỗi.

Nếu không quy định order giữa hai aspects, không nên cam kết stdout cố định cho các log tương ứng. Với nhiều advice cùng loại trong cùng một aspect, Java reflection không bảo đảm giữ thứ tự khai báo method; không dùng thứ tự code nguồn để suy ra thứ tự runtime.

### 10.1. Thay @Order có làm proceed gọi target khác đi không?

`@Order` chủ yếu ảnh hưởng **vị trí tương đối của advisor/aspect trong chuỗi**, không thay pointcut và không sửa target bean. Nhưng điều này có thể thay đổi việc một shortcut xảy ra **trước hay sau** các infrastructure advisors.

Ví dụ với transaction/caching, thứ tự advisor có thể ảnh hưởng việc around advice chạy bên trong hay bên ngoài transaction; từ đó exception/rollback/commit có thể được quan sát ở những thời điểm khác nhau. Không thay toàn bộ ordering trong ứng dụng chỉ vì muốn log in theo thứ tự đẹp. Hãy kiểm thử ngữ nghĩa nghiệp vụ khi điều chỉnh precedence.

## 11. Khi nào nên dùng @Around thay vì advice đơn giản hơn?

Spring Framework khuyến nghị **chọn loại advice ít quyền lực nhất đáp ứng yêu cầu**.

| Nhu cầu | Advice nên cân nhắc | Lý do |
| --- | --- | --- |
| Ghi log trước khi method chạy | `@Before` | Không cần chủ động gọi proceed |
| Xử lý kết quả thành công | `@AfterReturning` | Chỉ nhận nhánh normal return |
| Theo dõi exception thoát ra | `@AfterThrowing` | Chỉ nhận nhánh exceptional exit |
| Dọn dẹp khi method kết thúc cả hai nhánh | `@After` | Finally semantics không cần thao tác proceed |
| Đo thời gian bằng biến start/end cục bộ cùng invocation | **`@Around`** | Dùng proceed và try/finally với state riêng |
| Có cache hit/shortcut chủ đích | **`@Around`** | Có quyền bỏ qua proceed và trả kết quả thay thế |
| Thay đối số trước khi gọi method | **`@Around`** | Có `proceed(Object[])` |
| Retry có chính sách và idempotency | `@Around` hoặc hạ tầng chuyên dụng | Mỗi proceed có thể chạy lại chuỗi phía trong |
| Chỉ thay message log | Thường `@Before` hoặc `@After` | Không cần xâm nhập return/throw path |

Không phải mọi advice logging đều cần @Around. Việc quên proceed trong một @Around logging có thể **ngăn thực thi nghiệp vụ** — rủi ro mà @Before đơn giản không tạo ra theo cách này.

## 12. Điểm lưu ý với method trả về void, primitive, Future

### 12.1. Method trả về void

Với method target `void doWork()`, thông thường `pjp.proceed()` trả về `null` ở dạng Object; advice khai báo `Object` rồi `return pjp.proceed();` vẫn là cấu trúc phù hợp. Không tùy tiện biến thành `void` around advice khi cùng pointcut còn match method có return value.

### 12.2. Method trả về primitive

Nếu target trả `int`, giá trị đi qua advice là đối tượng wrapper thích hợp (ví dụ `Integer`) ở mức giao diện `Object`. Nếu advice return null cho primitive return type, caller/proxy có thể ném exception vì không thể unbox null. Nếu shortcut, giá trị thay thế phải có kiểu tương thích với method.

### 12.3. Method trả CompletableFuture hoặc Publisher

Giả sử method trả một `CompletableFuture<T>` hoặc `Mono<T>`. `pjp.proceed()` có thể trả **đối tượng đại diện cho công việc** trước khi công việc phía trong thật sự hoàn tất.

- Around advice đo thời gian từ trước proceed đến sau proceed có thể chỉ đo **thời gian tạo/trả Future/Publisher**, không phải toàn bộ latency nghiệp vụ.
- Lỗi từ asynchronous computation có thể xuất hiện khi future hoàn thành hoặc khi stream được subscribe, không nhất thiết bị catch bởi `try/catch` xung quanh chính `pjp.proceed()`.
- Khi cần metrics hoặc cleanup gắn với completion, dùng callback/operator đúng cho abstraction trả về, hoặc sử dụng Spring integration phù hợp.
- Không gọi proceed nhiều lần chỉ để “chờ kết quả” của future.

Bài này không mở rộng sang cấu hình Reactive/Async, chỉ nhấn mạnh rằng **Spring AOP proceed ở method execution boundary** không đồng nghĩa bảo đảm công việc bất đồng bộ đã kết thúc.

## 13. Proxy boundary và self-invocation

Ngay cả khi outer advice gọi `proceed()`, những lời gọi nội bộ của target bằng `this.innerMethod()` vẫn **không quay lại proxy**.

~~~java
public String callInternally() {
    return this.greet("Self");
}
~~~

Trong project mục 6, `callInternally()` được advice vì client gọi nó **qua proxy**, nhưng lệnh `this.greet("Self")` là lời gọi Java trực tiếp trong target. Do đó:

- Không có thêm một `FlowAspect.around` mới cho `greet` nội bộ.
- Không có `TraceAspect.before` cho `greet` nội bộ.
- Đối số `"Self"` không bị upper-case như khi gọi `service.greet("An")` trực tiếp từ bên ngoài proxy.

Cách khắc phục thông dụng là tách method cần Spring advice thành **Spring bean khác** và gọi qua tham chiếu bean được dependency injection cung cấp. Không dùng `new Bean()` rồi chờ advisor tự xuất hiện. Sử dụng `AopContext.currentProxy()` hoặc self-injection chỉ khi thật sự hiểu coupling và cấu hình proxy, không coi đó là mặc định.

Spring AOP hỗ trợ cả JDK dynamic proxy và CGLIB class-based proxy. **Chuyển chiến lược JDK ↔ CGLIB không tự sửa self-invocation**.

## 14. proceed() trong @Around và invocation.proceed() cấp thấp

Ngoài `@Aspect` style, Spring AOP còn cung cấp `org.aopalliance.intercept.MethodInterceptor`:

~~~java
// Snippet minh họa bên trong class Implements MethodInterceptor.
@Override
public Object invoke(MethodInvocation invocation) throws Throwable {
    System.out.println("interceptor: before");
    Object value = invocation.proceed();
    System.out.println("interceptor: after");
    return value;
}
~~~

Hai đường dùng khác nhau:

| Dạng Spring AOP | Tham số nhận | Lệnh chuyển tiếp | Điển hình |
| --- | --- | --- | --- |
| Annotation `@Around` trên Spring-managed `@Aspect` | `ProceedingJoinPoint` | `pjp.proceed()` / `pjp.proceed(Object[])` | AOP annotations, dùng pointcut expression |
| Spring AOP programmatic/Advisor infrastructure | `MethodInvocation` | `invocation.proceed()` | `ProxyFactory`, custom MethodInterceptor, infrastructure advice |

Chúng cùng ý tưởng chuyển tiếp chuỗi nhưng **không cùng kiểu API**. Đừng lẫn `ProceedingJoinPoint` và `MethodInvocation` khi viết signature advice. Ví dụ MethodInterceptor ở trên **không phải file Java độc lập**; cần implements interface, imports và đăng ký vào Spring AOP proxy qua `ProxyFactory`, advisor hoặc hạ tầng liên quan.

Một invocation có thể đi qua cả interceptor cấp thấp do framework đăng ký **và** advice khai báo bằng `@Aspect`. Vì vậy kết luận “proceed chỉ gọi advice do tôi tự viết” là thiếu chính xác.

## 15. Chẩn đoán khi proceed() không tạo kết quả mong muốn

| Triệu chứng | Khả năng nguyên nhân | Cách kiểm tra |
| --- | --- | --- |
| Có log “outer: before”, không có log target | Outer bỏ qua proceed hoặc một inner interceptor shortcut | Kiểm tra branch `return` và tầng interceptors phía trong |
| Target chạy, caller nhận null | Advice quên `return pjp.proceed()` hoặc cố ý return null | So khớp return contract, đặc biệt primitive |
| Target chạy hai lần | `proceed()` bị gọi nhiều lần hoặc retry layer | Theo dõi invocation count và tính idempotent |
| `outer: after` mất khi target ném lỗi | Code sau proceed không nằm trong finally | Đưa cleanup vào try/finally, giữ nguyên exception |
| `outer: finally` có nhưng không thấy target | Inner advisor ném exception trước target hoặc đã shortcut | Log từng tầng, đừng suy finally ⇒ target |
| Gọi trực tiếp service qua Spring proxy có log, `this.greet()` không có | Self-invocation | Tách sang bean khác và gọi qua DI |
| `proceed(args)` ném lỗi do kiểu | Mảng arguments sai số phần tử/kiểu | Giới hạn pointcut và kiểm tra method signature |
| Nhiều aspects cho stdout khác dự kiến | Chưa đặt precedence hoặc infrastructure advisor khác | Xem `@Order` / `Ordered` và actual proxy advisor chain |
| `@Around` không chạy | Aspect chưa là bean, AOP chưa bật, pointcut không match | Kiểm tra `@Aspect @Component` / `@Bean`, dependency, proxy |
| `AopUtils.isAopProxy(bean) == true` nhưng advice không chạy cho method cụ thể | Proxy có thể do advisor khác hoặc method không match | Kiểm tra eligible advisors và method-level matching |
| Cache hit khiến lời gọi không xuống target | Around shortcut đúng theo thiết kế | Phân biệt hợp đồng caching với lỗi thiếu proceed |
| Retry làm trùng transaction/message | Nhiều proceed chạy lại side-effect | Dùng retry policy, idempotency, transaction boundaries phù hợp |

### Checklist chẩn đoán

1. Lấy bean từ `ApplicationContext` hoặc constructor injection, không dùng instance `new` thay thế.
2. Kiểm tra `AopUtils.isAopProxy(bean)`, nhớ rằng boolean này **không đủ** để chứng minh mọi method bị advice.
3. Xác minh `@Aspect` class đã được đăng ký thành Spring bean và hỗ trợ `@AspectJ` đang bật.
4. Xem pointcut có match target class/method không.
5. Đặt log trước `proceed()`, trước target và sau `proceed()`; có branch return sớm không?
6. Nếu có nhiều advisors, xem thứ tự `@Order` và hạ tầng transaction/cache.
7. Nếu có exception, kiểm tra `catch`/`finally` và propagation tới caller.
8. Nếu gọi nhiều lần, đếm số proceed và số lần target thực thi.
9. Nếu thay args, so khớp argument count/type và dữ liệu mà inner interceptors nhìn thấy.
10. Kiểm thử success, shortcut, failure và self-invocation như ví dụ độc lập, không chỉ happy path.

## 16. Bài tập áp dụng

**Tình huống A:** Around advice in “before” rồi gọi `pjp.proceed()`; một inner cache advice trả giá trị cache và không proceed. Target method có chạy không?

**Đáp án:** Không ở lần gọi đó. Outer đã tiếp tục tới **inner interceptor**, nhưng inner chặn đường xuống target. Caller vẫn có thể nhận cached value.

**Tình huống B:** Target `sum(int,int)` trả primitive `int`; around advice gọi proceed nhưng trả `null`. Có đảm bảo caller nhận kết quả không?

**Đáp án:** Không. Null không tương thích với primitive return contract và có thể gây lỗi. Trả kết quả của proceed hoặc một value thay thế đúng kiểu.

**Tình huống C:** Một advice `@Around` gọi `proceed()` hai lần để “chắc chắc lưu thành công”. Có thể xảy ra tác động gì?

**Đáp án:** Target hoặc các inner advisors có thể chạy hai lần, gây duplicate writes/emails/transactions. Retry phải có chính sách và idempotency.

**Tình huống D:** Outer around đã in “before”, target ném exception. Code “after” nằm sau proceed không chạy. Làm sao bảo đảm một log cleanup vẫn xuất hiện?

**Đáp án:** Đặt cleanup trong `finally` bao quanh proceed. Nếu chỉ cần after-finally behavior, cân nhắc `@After` thay vì @Around.

**Tình huống E:** `pjp.proceed(new Object[]{"A"})` được gọi với method `greet(String)` trong Spring AOP. Target nhận argument nào?

**Đáp án:** Trong Spring AOP, method target phía dưới nhận `"A"` làm argument ở vị trí duy nhất. Mảng phải khớp signature của target.

**Tình huống F:** Một caller gọi `service.callInternally()` qua proxy. Trong target, nó gọi `this.greet("Self")`. Ngoài `callInternally`, có thêm một around invocation dành riêng cho `greet` không?

**Đáp án:** Không. Lời gọi nội bộ trên `this` không qua Spring proxy, nên không đi vào chuỗi advisor riêng cho `greet` lần nữa.

**Tình huống G:** Outer around shortcut nhưng vẫn in log “outer: finally”. Có mâu thuẫn không?

**Đáp án:** Không. `finally` thuộc chính outer advice đã chạy; những advisor phía trong và target mới là phần bị bỏ qua do không proceed.

**Tình huống H:** Một method trả CompletableFuture chưa hoàn tất, nhưng around advice đã in “after”. Có phải proceed đã chờ toàn bộ công việc chạy nền?

**Đáp án:** Không. Proceed có thể chỉ chờ method trả Future object. Hoàn tất async là một mốc khác, cần theo dõi bằng API completion phù hợp.

## 17. Nguồn tài liệu Spring chính thức

- [Spring Framework 6.2 — Declaring Advice / Around Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): `ProceedingJoinPoint.proceed()`, return value, shortcut, proceed nhiều lần, `proceed(Object[])` và advice ordering.
- [Spring Framework — Spring AOP Advice API](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html): `MethodInterceptor` và `MethodInvocation.proceed()` chuyển tiếp interceptor chain.
- [Spring Framework — AOP Concepts](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html): ngữ nghĩa around advice và lời khuyên chọn loại advice ít quyền lực nhất.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB AOP proxies, self-invocation, giới hạn phương thức.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): `@EnableAspectJAutoProxy` và đăng ký aspect bean.
- [Spring Framework — AspectJ Pointcuts](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html): Spring pointcut expression và phạm vi method execution.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-003">Hành vi khi không gọi proceed()</a>

<details>
<summary>Xem chi tiết</summary>

# Hành vi khi không gọi proceed() trong @Around Advice của Spring AOP

## 1. Điều quan trọng nhất

Trong **Spring AOP**, một advice khai báo bằng `@Around` có quyền quyết định **có tiếp tục lời gọi đang được chặn hay không**. Phương thức `ProceedingJoinPoint.proceed()` là bước tiếp tục **phần còn lại của chuỗi interceptor/advice** tiến tới phương thức target. Nếu một nhánh của `@Around` **không gọi `proceed()`**, nhánh đó **short-circuit** lời gọi: không chuyển quyền điều khiển xuống phần còn lại của chuỗi ở phía trong advice ấy, nên **phương thức nghiệp vụ đích không chạy theo đường gọi đó**.

Điều này là hành vi **hợp lệ theo API của Spring AOP**, không mặc nhiên là lỗi. Tuy nhiên, nó mang ý nghĩa nghiệp vụ rất lớn: target method không được thực thi, side effect dự kiến không xảy ra, và người gọi có thể nhận **giá trị do aspect tự trả về** hoặc **ngoại lệ do aspect ném ra**.

Mục tiêu của bài:

1. Hiểu chính xác `proceed()` trong Spring `@Around` có vai trò gì.
2. Phân biệt **không gọi proceed**, **gọi một lần** và **gọi nhiều lần**.
3. Biết caller nhận gì khi advice tự `return` hoặc `throw`.
4. Viết fallback phù hợp với phương thức trả `String`, primitive `int` và `void`.
5. Nhìn ra hệ quả đối với audit, transaction, persistence, cache, external API, exception và interceptor ordering.
6. Kiểm chứng bằng ví dụ chạy được trên **Spring Framework thuần** với Java 17+, Maven và Spring AOP proxy.

**Phạm vi:** Chỉ tập trung vào `@Around` và `ProceedingJoinPoint` trong **Spring-managed `@Aspect`**, không chuyển thành bài về AspectJ weaving nói chung.

## 2. Luồng gọi khi có và không có proceed

Một Spring service bean có thể được expose dưới dạng proxy:

~~~text
Client
  |
  v
Spring AOP proxy
  |
  v
@Around advice
  |
  +-- NHÁNH A: pjp.proceed()
  |                 |
  |                 v
  |       Các interceptor còn lại
  |                 |
  |                 v
  |           Target method
  |                 |
  |                 v
  |        Kết quả hoặc ngoại lệ
  |                 |
  |                 v
  |        @Around tiếp tục chạy
  |                 |
  |                 v
  |               Caller
  |
  +-- NHÁNH B: return fallback, không proceed
  |                 |
  |                 v
  |     Bỏ qua chuỗi phía trong và target
  |                 |
  |                 v
  |               Caller
  |
  +-- NHÁNH C: throw exception, không proceed
                    |
                    v
         Ngoại lệ lan ra caller
         (trừ khi lớp ngoài xử lý)
~~~

Ví dụ tối giản:

~~~java
@Around("execution(* demo.CheckoutApi.checkout(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("Before");

    if (maintenanceMode) {
        return "temporarily-unavailable";
    }

    Object result = pjp.proceed();
    System.out.println("After");
    return result;
}
~~~

Đây là **fragment trong một class aspect**, cần biến `maintenanceMode`, import `@Around` và `ProceedingJoinPoint`, Spring AOP config và bean phù hợp. Trong nhánh bảo trì:

- Advice vẫn chạy nên có dòng `Before`.
- `proceed()` không được gọi.
- Không có lời gọi tới method `checkout(...)` của target qua continuation đó.
- Dòng `After` không được thực hiện do nhánh `return` sớm.
- Caller nhận chuỗi `temporarily-unavailable`, miễn là giá trị ấy phù hợp với chữ ký return của method.

Trong nhánh bình thường, `proceed()` tiếp tục gọi method, kết quả đi ngược về advice, rồi advice chuyển tiếp kết quả cho caller.

## 3. proceed() không phải một method nghiệp vụ thông thường

`ProceedingJoinPoint` là tham số bắt buộc theo quy ước của Spring `@Around`. Ví dụ:

~~~java
@Around("execution(* demo.CheckoutApi.checkout(..))")
public Object intercept(ProceedingJoinPoint pjp) throws Throwable {
    return pjp.proceed();
}
~~~

Trong Spring AOP:

- `pjp.proceed()` **tiếp tục lời gọi hiện tại** với các tham số gốc.
- `pjp.proceed(new Object[] { ... })` có thể dùng để tiếp tục với một mảng argument khác, với số lượng, kiểu và thứ tự phù hợp method target.
- `pjp.getArgs()` cung cấp argument hiện tại.
- `pjp.getSignature()` mô tả method/join point đang được advice.
- `pjp.getThis()` trả về Spring AOP proxy; `pjp.getTarget()` trả về đối tượng target, nhưng **gọi trực tiếp method trên target không thay thế việc gọi proceed**.

Quan trọng: `proceed()` không đơn thuần tương đương `target.method()`. Nó chuyển việc thực thi **xuống chain** mà proxy đã chuẩn bị. Nếu còn interceptor/advice khác ở phía trong, chúng cũng có cơ hội chạy khi `proceed()` được gọi. Một lệnh `return` sớm có thể bỏ qua cả chúng.

Tài liệu Spring nêu rõ `proceed()` có thể được gọi **không lần nào, một lần hoặc nhiều lần** bên trong `@Around`. Cả ba trường hợp đều hợp lệ về mặt cơ chế; **hợp lệ không đồng nghĩa hợp lý cho mọi nghiệp vụ**.

## 4. Bảng so sánh ba trường hợp

| Trong @Around | Target chạy? | Giá trị người gọi nhận | Rủi ro |
| --- | --- | --- | --- |
| `return pjp.proceed();` | Có, nếu chain bên trong cũng tiếp tục | Thường là giá trị target | Hành vi bình thường |
| `return fallback;` không gọi proceed | Không, qua nhánh này | Fallback do aspect cung cấp | Bỏ qua side effect, che giấu công việc chưa làm |
| `throw new IllegalStateException(...);` không gọi proceed | Không, qua nhánh này | Caller nhận exception | Thay đổi exception contract của method |
| Gọi `pjp.proceed()` hai lần | Có thể thực thi target hai lần | Do code advice quyết định | Nhân đôi giao dịch hoặc side effect |
| Không gọi proceed nhưng `return null;` | Không | null, nếu return contract cho phép | Với primitive, có thể lỗi khi proxy unbox/khôi phục return |
| Không gọi proceed nhưng `return 0;` cho method trả int | Không | Integer được proxy unbox thành `int` | Dễ biến “chưa thực thi” thành kết quả hợp lệ giả |

Không được kết luận “không proceed thì **không có bất kỳ advice nào chạy**”. Chính around advice đó đã chạy; các interceptor **bọc phía ngoài nó** vẫn có thể chạy trước/sau khi lời gọi trả về hoặc ném lỗi. Chỉ các bước **nằm phía trong tại vị trí continuation bị bỏ qua** và target method không được đi tới qua nhánh đó.

Khi có nhiều advisor, kết quả quan sát được phụ thuộc vào thứ tự chain. Không suy luận rằng tất cả `@Before` đều bị bỏ qua hoặc tất cả `@AfterReturning` đều sẽ chạy nếu chưa xác định advisor ở phía nào.

## 5. Return contract khi bỏ qua target

### 5.1. Method trả đối tượng

Giả sử service interface:

~~~java
String checkout(String orderId);
~~~

Aspect có thể trả:

~~~java
if (orderId.startsWith("SKIP-")) {
    return "skipped:" + orderId;
}
~~~

Caller nhận String này thay cho kết quả của target. Không có gì trong Java compiler tự xác nhận nhánh `@Around` trả đúng kiểu String của mọi method mà pointcut match, vì method `@Around` thường khai báo **Object**.

Nếu pointcut quá rộng và cùng nhánh `return "skipped"` áp dụng cho phương thức trả `int`, bạn có thể gặp lỗi runtime do kiểu giá trị trả không phù hợp.

### 5.2. Method trả primitive

Giả sử:

~~~java
int fee(String orderId);
~~~

Aspect nên trả giá trị có thể chuyển thành `int`, ví dụ:

~~~java
if (shouldSkip(orderId)) {
    return 0;
}
~~~

Với advice trả `Object`, giá trị `0` được boxing thành `Integer` rồi proxy trả về cho caller theo chữ ký `int`.

**Không trả null** cho method có primitive return type. Vì null không thể chuyển thành primitive, quá trình hoàn tất invocation có thể thất bại (Spring AOP có đường kiểm tra trường hợp null cho primitive, và tùy proxy còn có nguy cơ unboxing lỗi).

Cũng cần xem xét ý nghĩa nghiệp vụ: `fee = 0` có thể có nghĩa “miễn phí hợp lệ”, **không phải** “chưa tính phí”. Nếu đây là hai trạng thái khác nhau, hãy thiết kế return type biểu diễn được sự khác biệt, chẳng hạn kết quả có status, hoặc ném ngoại lệ phù hợp.

### 5.3. Method trả void

Giả sử:

~~~java
void notifyCustomer(String orderId);
~~~

Advice khai báo kiểu `Object` có thể dùng:

~~~java
if (shouldSkip(orderId)) {
    return null;
}
return pjp.proceed();
~~~

Với phương thức target trả `void`, caller không nhận một giá trị Java có ý nghĩa. `return null` của advice chỉ là kết thúc continuation qua proxy.

**Nhưng return void không đồng nghĩa có thể bỏ qua tùy ý.** Nếu thông báo là bắt buộc, caller vẫn tưởng call đã hoàn thành bình thường. Cần quy ước rõ rằng việc bỏ qua được chấp nhận, hoặc thể hiện kết quả bằng return type/exception.

### 5.4. Vì sao @Around nên trả Object, không trả void?

Spring khuyến nghị phương thức `@Around` **khai báo `Object` làm return type** và thường `return` kết quả của `proceed()`. Nếu method advice lại khai báo `void`, caller sẽ không nhận được giá trị trả từ continuation theo cách mong đợi.

Để một aspect áp dụng cho cả String, int và void methods, dùng `public Object around(ProceedingJoinPoint pjp) throws Throwable`, rồi đảm bảo các nhánh trả về hợp đồng phù hợp từng method. Khi logic trở nên phức tạp, nên chia thành **nhiều pointcut/advice có kiểu và trách nhiệm rõ ràng** thay vì viết một switch chứa hàng trăm method.

## 6. Ví dụ hoàn chỉnh: bỏ qua ba kiểu method có kiểm chứng

Ta tạo ứng dụng **Spring Framework 6.2.12**, **Java 17+** và **Maven 3.9+**. Không cần Spring Boot hoặc cơ sở dữ liệu.

Một Spring `CheckoutService` cung cấp ba method:

- `String checkout(String orderId)`: thực hiện checkout và trả kết quả.
- `int fee(String orderId)`: tính phí.
- `void notifyCustomer(String orderId)`: gửi thông báo.

Một Spring `GateAspect` có `@Around` cho cả ba. Nếu orderId bắt đầu bằng `SKIP-`, advice **không gọi proceed** và cung cấp fallback tương ứng; ngược lại, advice gọi `proceed()` đúng một lần.

Một `ExecutionProbe` được target inject để đếm số lần **target method thật sự chạy**. Điều này giúp kiểm chứng rõ rằng dòng `AOP bypass` không chỉ là một log trong khi target vẫn chạy ngầm.

### 6.1. Cấu trúc thư mục

~~~text
spring-aop-around-shortcircuit/
├── pom.xml
└── src/main/java/demo/
    ├── CheckoutApi.java
    ├── CheckoutService.java
    ├── ExecutionProbe.java
    ├── GateAspect.java
    ├── AppConfig.java
    └── App.java
~~~

### 6.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-around-shortcircuit</artifactId>
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

`spring-context` cung cấp ApplicationContext và các module Spring AOP cần thiết. `aspectjweaver` cung cấp các annotation/pointcut type mà Spring sử dụng cho `@AspectJ` style. Ví dụ hoạt động bằng **Spring proxy**, không có compile-time hoặc load-time weaving.

### 6.3. Interface công bố ba phương thức

**src/main/java/demo/CheckoutApi.java**

~~~java
package demo;

public interface CheckoutApi {
    String checkout(String orderId);
    int fee(String orderId);
    void notifyCustomer(String orderId);
}
~~~

Có interface giúp Spring Framework thuần mặc định có thể chọn **JDK dynamic proxy**. App sẽ lấy bean theo `CheckoutApi`, không cast proxy sang implementation.

### 6.4. Probe đếm số lần target được thực thi

**src/main/java/demo/ExecutionProbe.java**

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class ExecutionProbe {

    private final AtomicInteger targetCalls = new AtomicInteger();

    public void increment() {
        targetCalls.incrementAndGet();
    }

    public int count() {
        return targetCalls.get();
    }
}
~~~

Đây là **bean phụ trợ**, không phải target của pointcut `CheckoutApi`. Nó không bị `GateAspect` match chỉ vì cũng được Spring quản lý. `AtomicInteger` làm phép tăng/đọc bộ đếm an toàn về mặt đồng thời cho minh họa.

### 6.5. Target thực hiện nghiệp vụ

**src/main/java/demo/CheckoutService.java**

~~~java
package demo;

import org.springframework.stereotype.Service;

@Service
public class CheckoutService implements CheckoutApi {

    private final ExecutionProbe probe;

    public CheckoutService(ExecutionProbe probe) {
        this.probe = probe;
    }

    @Override
    public String checkout(String orderId) {
        probe.increment();
        System.out.println("TARGET checkout " + orderId);
        return "paid:" + orderId;
    }

    @Override
    public int fee(String orderId) {
        probe.increment();
        System.out.println("TARGET fee " + orderId);
        return 25;
    }

    @Override
    public void notifyCustomer(String orderId) {
        probe.increment();
        System.out.println("TARGET notifyCustomer " + orderId);
    }
}
~~~

Target tăng bộ đếm ở đầu mỗi method. Nếu `GateAspect` không gọi `proceed()`, các lệnh `probe.increment()` **không được target thực hiện**. Kết quả quan sát không chỉ dựa vào lời in từ advice.

Trong một ứng dụng thật, target có thể ghi database, gọi external API hoặc phát sự kiện; bỏ qua proceed đồng nghĩa các side effect ấy **không xảy ra ở invocation bị chặn**, trừ các hoạt động ngoài target đã thực hiện từ trước ở một nơi khác.

### 6.6. GateAspect: ba kiểu fallback, không proceed

**src/main/java/demo/GateAspect.java**

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class GateAspect {

    @Around("execution(* demo.CheckoutApi.*(..))")
    public Object aroundCheckoutApi(ProceedingJoinPoint pjp)
            throws Throwable {

        String methodName = pjp.getSignature().getName();
        String orderId = (String) pjp.getArgs()[0];

        System.out.println("AOP enter " + methodName + " " + orderId);

        if (orderId.startsWith("SKIP-")) {
            System.out.println("AOP bypass " + methodName);

            return switch (methodName) {
                case "checkout" -> "skipped:" + orderId;
                case "fee" -> 0;
                case "notifyCustomer" -> null;
                default -> throw new IllegalStateException(
                        "No fallback for " + methodName);
            };
        }

        Object result = pjp.proceed();
        System.out.println("AOP proceeded " + methodName);
        return result;
    }
}
~~~

Đây là trọng tâm:

1. `@Aspect @Component` làm class vừa khai báo aspect vừa được Spring đăng ký thành bean.
2. `@Around("execution(* demo.CheckoutApi.*(..))")` chọn những method execution phù hợp với interface CheckoutApi mà Spring proxy xử lý.
3. `pjp.getArgs()[0]` đọc orderId đầu tiên; demo giả định ba method có cùng signature `(String)`. **Không được chép một cách máy móc** sang interface có method không tham số hoặc tham số đầu không phải String.
4. Khi orderId có prefix `SKIP-`, nhánh `return switch` **không gọi proceed**.
5. `checkout` trả String `"skipped:" + orderId` phù hợp contract.
6. `fee` trả literal `0` (được boxing thành Integer), tương thích method trả primitive int.
7. `notifyCustomer` trả null ở tầng advice, phù hợp method đích trả void.
8. Các orderId khác dùng `pjp.proceed()` **chính xác một lần**, trả kết quả target.
9. `System.out.println("AOP proceeded ...")` ở sau proceed chỉ chạy nếu continuation hoàn tất bình thường; nếu target ném exception và advice không bắt, dòng ấy không xuất hiện.

**Lưu ý thiết kế:** Việc chọn fallback theo tên method là cách trình diễn ngắn gọn cho ba return types. Trong production, nên tách thành các advice có pointcut hẹp hơn hoặc một policy riêng có test, để khi thêm method vào interface không vô tình có fallback thiếu/không đúng kiểu.

### 6.7. Kích hoạt Spring AOP

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

`@ComponentScan` đăng ký `CheckoutService`, `ExecutionProbe` và `GateAspect`. `@EnableAspectJAutoProxy` bật hạ tầng đọc `@Aspect` và xây dựng Spring AOP proxy khi có advisor phù hợp. `@Aspect` **không tự đăng ký bean nếu thiếu component registration hoặc `@Bean`**.

### 6.8. Main: gọi đủ nhánh bình thường và bỏ qua

**src/main/java/demo/App.java**

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

    private static void invokeAll(CheckoutApi checkout, String orderId) {
        String result = checkout.checkout(orderId);
        System.out.println("CALLER checkout=" + result);

        int fee = checkout.fee(orderId);
        System.out.println("CALLER fee=" + fee);

        checkout.notifyCustomer(orderId);
        System.out.println("CALLER notified");
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            CheckoutApi checkout = context.getBean(CheckoutApi.class);
            ExecutionProbe probe = context.getBean(ExecutionProbe.class);

            System.out.println(
                    "IS_PROXY " + AopUtils.isAopProxy(checkout));

            System.out.println("=== NORMAL ===");
            invokeAll(checkout, "OK-1");

            System.out.println("=== SKIP ===");
            invokeAll(checkout, "SKIP-9");

            System.out.println("TARGET_CALLS " + probe.count());
        }
    }
}
~~~

Chạy trong thư mục chứa pom.xml:

~~~bash
mvn -q clean compile exec:java
~~~

### 6.9. Stdout logic kỳ vọng

~~~text
IS_PROXY true
=== NORMAL ===
AOP enter checkout OK-1
TARGET checkout OK-1
AOP proceeded checkout
CALLER checkout=paid:OK-1
AOP enter fee OK-1
TARGET fee OK-1
AOP proceeded fee
CALLER fee=25
AOP enter notifyCustomer OK-1
TARGET notifyCustomer OK-1
AOP proceeded notifyCustomer
CALLER notified
=== SKIP ===
AOP enter checkout SKIP-9
AOP bypass checkout
CALLER checkout=skipped:SKIP-9
AOP enter fee SKIP-9
AOP bypass fee
CALLER fee=0
AOP enter notifyCustomer SKIP-9
AOP bypass notifyCustomer
CALLER notified
TARGET_CALLS 3
~~~

**Phân tích output:**

- Ở nhóm `NORMAL`, có ba dòng `TARGET`, tương ứng ba lần `proceed()` được thực hiện.
- Ở nhóm `SKIP`, có các dòng `AOP enter` và `AOP bypass` nhưng **không có dòng `TARGET`**.
- Mỗi caller vẫn tiếp tục chạy: nhận String fallback, int fallback hoặc kết thúc method void không có ngoại lệ.
- `TARGET_CALLS 3` xác nhận chỉ ba method trong nhánh bình thường chạm target. Ba lời gọi ở nhánh SKIP **không** tăng counter.
- `IS_PROXY true` chỉ chứng minh bean được Spring proxy; chính output advice/target và probe chứng minh behavior của `@Around` cụ thể.

Bài mô tả **stdout logic** theo chương trình, không tính thông báo Maven hoặc Spring logging. Ví dụ dùng một aspect và một set advisor cố định nên không yêu cầu suy đoán thứ tự giữa nhiều aspects.

## 7. Điều gì vẫn chạy khi target bị bỏ qua?

Một nhầm lẫn là cho rằng “không proceed tức là toàn bộ ứng dụng không làm gì”. Không đúng.

Khi advice bỏ qua target:

- **Code đã chạy trong chính advice trước `return`** vẫn tạo side effect, ví dụ log `AOP enter`, tăng metric hoặc đọc cache.
- **Code của caller trước khi gọi service** đã chạy.
- **Code của caller sau khi lời gọi proxy trả về bình thường** có thể tiếp tục chạy.
- **Interceptor/advice nằm phía ngoài trong chain** có thể chạy và quan sát kết quả/exception theo thứ tự thực tế.
- **Target và những interceptor nằm phía trong vị trí bị short-circuit** không được continuation đi tới qua nhánh đó.

Ví dụ một `@Around` ở phía ngoài ghi thời gian gọi proxy, còn một `@Around` phía trong trả fallback. Outer around vẫn có thể nhận kết quả khi nó gọi proceed để chuyển quyền tới inner around, trong khi target không chạy. Không thể từ việc thiếu dòng `TARGET` mà suy ra “không có transaction/security/logging advice nào chạy”.

Khi có nhiều advisor, việc xác định aspect nào ở ngoài hay ở trong phụ thuộc ordering thực tế. Có thể dùng `@Order` hoặc `Ordered` để xác định ưu tiên giữa các aspects/advisors phù hợp; cần kiểm thử khi logic short-circuit có ý nghĩa nghiệp vụ.

## 8. Throw exception thay vì trả fallback

Một lựa chọn khác là chặn lời gọi và ném ngoại lệ:

~~~java
@Around("execution(* demo.CheckoutApi.checkout(..))")
public Object guard(ProceedingJoinPoint pjp) throws Throwable {
    String orderId = (String) pjp.getArgs()[0];

    if (orderId.startsWith("DENY-")) {
        throw new IllegalStateException("Denied: " + orderId);
    }

    return pjp.proceed();
}
~~~

Đây là **fragment aspect thay thế**, không thêm cùng `GateAspect` trong demo chính nếu muốn giữ stdout mục 6. Nhánh `DENY-` **không gọi proceed**, target không được thực thi qua nhánh đó và caller nhận `IllegalStateException`, trừ khi một interceptor/caller phía ngoài bắt hoặc chuyển đổi ngoại lệ.

So sánh:

~~~java
return "skipped:" + orderId;
~~~

với:

~~~java
throw new IllegalStateException("Denied: " + orderId);
~~~

Trường hợp đầu báo cho caller **thành công ở mức Java call** với một giá trị thay thế; trường hợp sau **báo lỗi qua exception contract**. Chọn hướng nào phải dựa vào nghiệp vụ thật, không chỉ để khiến method chạy “êm”.

Nếu ứng dụng có transaction interceptor ở phía ngoài, một exception thoát ra có thể ảnh hưởng rollback theo transaction rules. Nếu around nuốt exception hoặc trả kết quả bình thường, hành vi transaction khác đi. Vì vậy Spring AOP short-circuit cần được review cùng advisor chain, không được suy luận rằng “không gọi target thì mọi transaction đã có cũng không tồn tại”.

## 9. Cache hit là ví dụ phổ biến của không proceed

Một use case hợp lệ của `@Around` là cache:

~~~java
@Around("execution(* demo.LookupService.find(..)) && args(key)")
public Object aroundFind(ProceedingJoinPoint pjp, String key)
        throws Throwable {

    Object cached = cache.getIfPresent(key);
    if (cached != null) {
        return cached; // Không proceed: trả thẳng kết quả trong cache
    }

    Object result = pjp.proceed();
    cache.put(key, result);
    return result;
}
~~~

Đây là **fragment minh họa**, giả định có `cache`, `LookupService` và cấu hình Spring AOP thích hợp; không phải file chạy được độc lập. Cache hit không gọi target, cache miss gọi target rồi lưu kết quả.

Những điều cần xác nhận khi áp dụng thật:

1. Giá trị trong cache **phải tương thích** return type được advise.
2. `null` trong cache có thể biểu thị “không có dữ liệu”, nhưng cũng có thể là kết quả cache hợp lệ; cần phân biệt **cache miss** với **cached null**.
3. Nếu caller kỳ vọng side effect ở mỗi invocation, không được tự ý trả cached result và bỏ side effect.
4. Nếu nhiều thread cùng cache miss, có thể có nhiều lần proceed; chính sách concurrency của cache cần được thiết kế.
5. Dùng annotation cache sẵn có của Spring khi phù hợp thay vì tự viết AOP cache khi không có yêu cầu đặc thù.

Một cache aspect độc lập vẫn phải loại trừ các bean cache-support thích hợp khỏi pointcut để tránh advice tái nhập qua chính collaborator của nó.

## 10. Authorization và maintenance: short-circuit nên rõ ràng

Với các thao tác bị từ chối vì quyền hoặc bảo trì, việc **không gọi target** có thể chính là mục tiêu. Tuy nhiên, không nên mặc định trả giá trị “trông như thành công” nếu hành động bị ngăn.

Ví dụ:

~~~java
if (!permissionService.allowed(user, operation)) {
    throw new SecurityException("Operation not allowed");
}

return pjp.proceed();
~~~

Đây là **fragment code**, các biến và bean `permissionService`/ `user`/`operation` cần được khai báo trong ngữ cảnh phù hợp. Từ chối bằng exception truyền tín hiệu rõ hơn `return true` trên một method thực chất chưa hoàn thành.

Nếu đây là Spring Security, nên cân nhắc các cơ chế bảo vệ method có sẵn thay vì tự tạo permission advice khó kiểm chứng. Nếu là maintenance mode, nên quy định cách trả về error/status chuẩn tại tầng API. Không nên dùng `@Around` để âm thầm bỏ qua ghi nhận thanh toán hoặc cập nhật dữ liệu bắt buộc.

**Quy tắc nghiệp vụ:** Short-circuit có thể là một cơ chế đúng, nhưng luôn cần định nghĩa rõ **hành động nào không được thực hiện** và **caller được thông báo ra sao**.

## 11. Nguy cơ trả kết quả thay thế sai kiểu

Một advice kiểu Object không được Java compiler buộc mọi nhánh tương thích với mọi method match pointcut:

~~~java
@Around("execution(* demo.CheckoutApi.*(..))")
public Object bad(ProceedingJoinPoint pjp) {
    return "SKIPPED";
}
~~~

Đây là **fragment minh họa lỗi thiết kế**; không dùng chung với demo chính. Nó có thể trả String cho phương thức `fee(String)` trả int hoặc `notifyCustomer(String)` trả void. Với `fee`, kiểu trả về không tương thích có thể gây lỗi runtime khi proxy cố trả kết quả theo signature int.

Các cách tránh:

- Dùng pointcut hẹp cho **một method hoặc một nhóm có cùng return contract**.
- Thiết kế một kiểu kết quả riêng như `OperationResult` nếu caller cần biết `EXECUTED`, `SKIPPED` hoặc `DENIED`.
- Nếu method trả primitive, trả wrapper phù hợp và không dùng null.
- Nếu method trả void, nhớ rằng caller không thể đọc giá trị fallback; nên chọn exception/event rõ ràng khi bỏ qua không phải kết quả hợp lệ.
- Test **return type** ở cả nhánh proceed và nhánh không proceed.

## 12. Không gọi proceed không đồng nghĩa target đã bị hủy

Không gọi proceed **chặn một invocation cụ thể**. Nó không:

- Hủy bean trong `ApplicationContext`.
- Ngăn target được tạo hoặc inject dependencies lúc startup.
- Tự rollback mọi thao tác đã chạy trước đó.
- Dừng các công việc async đã được lên lịch từ trước ở những chỗ khác.
- Chặn method khác trên cùng bean nếu method đó không match pointcut.
- Biến private/internal self-invocation thành lời gọi qua proxy.
- Buộc những interceptor ở phía ngoài ngừng chạy.
- Đảm bảo những dữ liệu external đã được ghi trước lời gọi sẽ được phục hồi.

Trong ví dụ, Spring đã tạo một `CheckoutService` bean dù cả ba lời gọi SKIP không hề chạy body target. `ExecutionProbe` vẫn tồn tại và giữ số đếm từ ba lời gọi bình thường.

Nếu method vừa ghi database vừa phát message, việc không proceed sẽ bỏ qua **toàn bộ thân method target** trên đường gọi đó. Nhưng nếu trước khi đi qua proxy có một phần workflow khác đã ghi database, short-circuit không tự đảo ngược phần đó.

## 13. Proceed nhiều lần: khác hẳn không proceed

Spring cho phép `proceed()` được gọi nhiều lần:

~~~java
@Around("execution(* demo.TaskApi.run(..))")
public Object retry(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (TransientFailureException first) {
        return pjp.proceed();
    }
}
~~~

Đây là **fragment mô tả ý tưởng retry**, không compile độc lập nếu chưa định nghĩa `TaskApi` và `TransientFailureException`, và cần cấu hình AOP. Nó có thể thực thi target **hai lần**, kèm side effect lặp lại, nên chỉ dùng với tác vụ có chính sách retry/idempotency phù hợp.

Phân biệt ba policy:

~~~text
proceed 0 lần -> bypass target
proceed 1 lần -> thực hiện bình thường
proceed 2 lần -> có thể thực hiện lại target (nếu đi hết chain)
~~~

Retry còn phụ thuộc ngoại lệ được ném ra ở vị trí nào trong chain và advisor ordering; không suy luận target đã chạy một lần chỉ vì catch được exception.

Nếu method trả về thông tin về một giao dịch, việc chạy hai lần có thể tạo double charge hoặc double publish. Hãy cân nhắc retry framework chuyên dụng, kiểm tra idempotency và khóa giao dịch; không coi gọi proceed nhiều lần là “vô hại”.

## 14. Thứ tự advice: short-circuit có thể thay đổi những advice khác

Giả sử hai aspects:

- `MetricsAspect` đo duration.
- `GateAspect` quyết định không proceed với `SKIP-`.

Nếu MetricsAspect là advisor **bọc ngoài** GateAspect, nó có thể in metrics sau khi GateAspect trả fallback. Nếu GateAspect bọc ngoài MetricsAspect và không proceed, MetricsAspect không được gọi qua continuation.

Điểm cần nhớ:

| Vị trí so với short-circuit | Khả năng chạy |
| --- | --- |
| Around advice đang quyết định bypass | Đã chạy để đưa ra quyết định |
| Advice nằm phía ngoài và đã gọi proceed vào short-circuit advice | Có thể hoàn tất logic trước/sau như bình thường |
| Advice nằm phía trong short-circuit advice | Không được đi tới nếu không gọi proceed |
| Target method | Không được chạy qua invocation bị bypass |
| Caller sau proxy | Chạy tiếp nếu proxy trả bình thường; xử lý lỗi nếu exception |

**Không có quy tắc “mọi @Before đều chạy ngay cả khi @Around skip” hoặc “mọi @After đều không chạy”.** Thứ tự chain mới là dữ kiện quyết định.

Spring hỗ trợ thứ tự giữa aspects/advisors bằng `@Order` và cơ chế ordering tương ứng. Khi business requirement liên quan transaction/security/audit, phải kiểm thử thứ tự thực, không suy đoán từ thứ tự method khai báo trong source code.

## 15. Self-invocation là vấn đề khác

Nếu một method trên target gọi:

~~~java
public void outer() {
    this.inner();
}
~~~

thì Spring AOP **thông thường không intercept lời gọi `this.inner()`** vì nó không đi qua proxy. Đó là **giới hạn proxy**, khác với một `@Around` đã intercept method `inner` rồi chủ động không gọi proceed.

Hai tình huống:

1. **No interception:** không có lời gọi proxy cho `inner` nên advice của inner không được thực thi.
2. **Interception rồi skip:** advice `@Around` của inner đã chạy, nhưng không gọi continuation.

Điểm phân biệt là có hay không log/side effect **trong advice**. Nếu gặp case không thấy log `AOP enter`, đừng vội kết luận advice đã short-circuit; có thể pointcut không match hoặc lời gọi bypass proxy hoàn toàn.

## 16. Cách kiểm thử hành vi không proceed

### 16.1. Positive/negative test theo side effect

Với demo mục 6, cần assert:

~~~java
int before = probe.count();
String skipped = checkout.checkout("SKIP-T1");

if (!"skipped:SKIP-T1".equals(skipped)) {
    throw new AssertionError("Wrong fallback");
}

if (probe.count() != before) {
    throw new AssertionError("Target should not have executed");
}

String executed = checkout.checkout("OK-T2");

if (!"paid:OK-T2".equals(executed)) {
    throw new AssertionError("Wrong target result");
}

if (probe.count() != before + 1) {
    throw new AssertionError("Target should have executed once");
}
~~~

Đây là **fragment kiểm thử bổ sung** cần biến `checkout` (kiểu `CheckoutApi`) và `probe` (kiểu `ExecutionProbe`) được lấy từ context. Nó không phải một file Java độc lập.

Test chỉ nhìn dòng `AOP bypass` không đủ; cần xác minh **hành vi target thật sự bị bỏ qua** bằng counter, spy hoặc trạng thái persistence phù hợp.

### 16.2. Test return contract theo từng signature

- `checkout("SKIP-...")` phải trả String fallback phù hợp.
- `fee("SKIP-...")` phải trả đúng primitive int, không lỗi unboxing.
- `notifyCustomer("SKIP-...")` phải hoàn tất bình thường nếu skip được phép.
- Trong trường hợp từ chối bằng exception, caller phải nhận **exception đã thiết kế**.
- `checkout("OK-...")` phải chạy target đúng một lần.
- Nếu có nhiều aspects, cần test theo **cấu hình advisor order thật**.

### 16.3. Test điểm cắt có quá rộng không

Nếu `@Around` đang dùng `execution(* demo..*.*(..))` và trong lời advice lại gọi các Spring beans như `ExecutionProbe`, `AuditRecorder`, `GateService`, chúng cũng có nguy cơ bị advisor chọn, dẫn tới advice tái nhập hoặc kết quả không mong muốn. Trong demo, pointcut giới hạn theo interface `CheckoutApi`, nên `ExecutionProbe` nằm ngoài phạm vi.

Đừng dựa vào việc Spring loại `@Aspect` khỏi auto-proxying để suy ra mọi collaborator của aspect cũng được loại.

## 17. Lỗi thường gặp

| Hiện tượng | Nguyên nhân khả dĩ | Hướng xử lý |
| --- | --- | --- |
| Không thấy log target nhưng caller vẫn nhận kết quả | Around advice tự trả fallback, không proceed | Tìm các nhánh return trong @Around và kiểm tra contract |
| Caller nhận `null` bất ngờ | Advice short-circuit mà không trả fallback hợp lý | Xác định return type và giá trị thay thế có chủ đích |
| Method trả int bị lỗi khi bypass | Advice trả null hoặc String không tương thích | Trả Integer hợp lệ hoặc thiết kế lại return type |
| Method void không chạy mà caller vẫn tưởng xong | Advice return null và giấu trạng thái skip | Thiết kế status/exception hoặc event phù hợp |
| Không có audit/transaction advice mong đợi | Around advisor phía ngoài đã short-circuit trước khi đi vào chain bên trong | Kiểm tra advisor order và continuation |
| Có một số advice vẫn chạy khi target bị skip | Chúng nằm phía ngoài vị trí short-circuit | Phân tích thứ tự chain, không suy đoán toàn bộ bị bỏ |
| Advice gây nhiều side effect hơn target | Advice tự chạy logic, dù không proceed | Kiểm tra code của advice và collaborators |
| Lời gọi tạo thanh toán hai lần | Retry gọi proceed nhiều lần với method không idempotent | Thiết kế idempotency/retry có kiểm soát |
| Không thấy `AOP enter` | Method không khớp pointcut, bean chưa proxy hoặc self-invocation | Kiểm tra bean, pointcut và caller path |
| Return fallback khác hợp đồng API | Aspect dùng một return cho nhiều signatures | Thu hẹp pointcut hoặc tách advice theo method |
| Không có exception khi nghiệp vụ bị từ chối | Advice trả success-like fallback | Trả status thể hiện skip hoặc ném exception thích hợp |
| Method target đã tạo bean/dependency nhưng không chạy | Bean lifecycle xảy ra trước invocation | Phân biệt bean construction và method execution |
| Advice gọi recorder rồi lặp vô hạn | Recorder cũng nằm trong pointcut | Loại collaborator khỏi pointcut hoặc dùng allowlist business |
| @Around có return type void làm mất kết quả | Advice không truyền return của proceed | Dùng Object và return kết quả phù hợp |

## 18. Checklist trước khi chủ động bỏ proceed()

1. **Vì sao method không nên chạy?** Cache hit, maintenance, authorization denial hay một chính sách có chủ đích khác?
2. **Caller được thông báo thế nào?** Giá trị cached/fallback, status explicit hay exception?
3. **Return type có đúng không?** Đặc biệt primitive, void, kiểu nullable và generic?
4. **Những side effect nào bị bỏ qua?** Ghi database, gửi message, cập nhật trạng thái, metric?
5. **Có side effect nào đã xảy ra trước khi gọi proxy không?** Nếu có, short-circuit không tự hoàn tác.
6. **Advisor ordering có đúng không?** Transaction, security, audit và tracing ở vị trí nào?
7. **Aspect có nên chuyển trách nhiệm sang Spring feature chuyên dụng không?** Ví dụ caching, security hay retry?
8. **Pointcut đã giới hạn đúng business methods chưa?** Tránh chọn nhầm recorder hoặc infra beans.
9. **Đường gọi chắc chắn đi qua Spring proxy không?** Không được nhầm với self-invocation.
10. **Có test cả nhánh proceed và nhánh skip không?** Kiểm tra side effect và kết quả thật, không chỉ log.
11. **Nếu proceed gọi nhiều lần, target có idempotent không?** Đừng tự retry tác vụ có side effect không thể lặp.
12. **Logic fallback có rõ nghĩa không?** Không được giả định `0`, `null` hoặc chuỗi `"OK"` biểu diễn đúng tình trạng nghiệp vụ.

## 19. Câu hỏi tự đánh giá

**1. Một @Around không gọi proceed thì target có được chạy không?**

Không qua invocation bị short-circuit ấy. Advice có thể trực tiếp làm việc khác, nhưng continuation tới target không được thực hiện.

**2. Không gọi proceed có phải lỗi Spring AOP không?**

Không. Spring cho phép gọi `proceed()` 0, 1 hoặc nhiều lần. Đó là quyền lực của around advice nhưng cần dùng có chủ đích.

**3. Ai trả kết quả cho caller khi around không proceed?**

Giá trị mà advice tự `return`, hoặc ngoại lệ advice `throw`. Giá trị phải tương thích method return contract.

**4. Nếu target method trả void thì around nên trả gì khi skip?**

`@Around` vẫn nên có return type Object và có thể `return null`. Nhưng app phải coi đây là hành động bỏ qua có chủ đích, không mặc định xem như nghiệp vụ hoàn thành.

**5. Nếu target method trả int mà advice trả null thì sao?**

Kết quả không tương thích primitive, có thể phát sinh lỗi lúc proxy hoàn tất invocation. Cần trả một Integer thích hợp hoặc thiết kế kết quả rõ ràng hơn.

**6. Một advice phía ngoài có thể chạy khi advice bên trong skip target không?**

Có. Outer advice có thể đã chạy và sẽ tiếp tục xử lý giá trị/exception của inner advice. Không kết luận tất cả advice đều bị bỏ qua.

**7. Khác gì với self-invocation?**

Self-invocation có thể bỏ qua proxy ngay từ đầu nên @Around không được gọi. Short-circuit là @Around đã được gọi và không gọi `proceed()`.

**8. Có thể dùng proceed() hai lần không?**

Có về mặt cơ chế. Nhưng nó có thể thực thi target/side effect lặp lại và phải được quản lý như retry, với idempotency/transaction đúng.

**9. Vì sao một fallback `0` cho phí thanh toán dễ gây hiểu nhầm?**

Vì `0` có thể nghĩa “phí thật bằng không” thay vì “chưa tính phí”. Kết quả nên biểu thị chính xác trạng thái.

**10. Bằng chứng tốt nhất rằng target bị bỏ qua là gì?**

Dùng counter/spy/trạng thái hệ thống để so số lần target được thực thi ở nhánh proceed và skip, cộng với kiểm tra caller result.

## 20. Tài liệu Spring chính thức

- **Spring Framework — Declaring Advice / Around Advice**: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- **Spring Framework — AOP Concepts / Around Advice**: https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html
- **Spring Framework — Advice API / MethodInterceptor**: https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html
- **Spring Framework — Proxying Mechanisms**: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- **Spring Framework — @AspectJ Support**: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html

**Kết luận:** Trong Spring `@Around`, **không gọi `proceed()` là quyết định chủ động bỏ qua continuation và target method của invocation hiện tại**. Advice được phép trả fallback hoặc ném exception thay cho target, nhưng phải bảo toàn return contract, minh bạch trạng thái nghiệp vụ và kiểm thử side effect. Chỉ chọn cách này khi thật sự cần quyền kiểm soát “có thực hiện target hay không”; nếu chỉ cần xử lý trước/sau, hãy ưu tiên loại advice đơn giản hơn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-004">Hành vi khi gọi proceed() nhiều lần</a>

<details>
<summary>Xem chi tiết</summary>

# Hành vi khi gọi `proceed()` nhiều lần trong Spring AOP

## 1. Câu trả lời trọng tâm

Trong **Spring AOP**, phương thức `@Around` nhận một `ProceedingJoinPoint` và có quyền quyết định **có chuyển tiếp lời gọi tới phần còn lại của interceptor chain hay không, chuyển tiếp bao nhiêu lần và trả về kết quả nào**.

Tài liệu Spring Framework xác nhận: trong body của `@Around`, gọi `proceed()` **không lần nào, một lần hoặc nhiều lần đều hợp lệ**. Gọi nhiều lần là nền tảng của một số chính sách như **retry khi có lỗi tạm thời**. Tuy nhiên, nó **không phải** một thao tác quan sát vô hại: mỗi lần chuyển tiếp có thể khiến **target method và những advice nằm bên trong advice hiện tại thực thi thêm một lần**.

```java
@Around("execution(* com.example.service.PaymentService.charge(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    Object first = pjp.proceed();   // Một lượt vào phần chain phía trong.
    Object second = pjp.proceed();  // Một lượt nữa vào phần chain phía trong.
    return second;                  // Caller chỉ nhận kết quả advice trả ra.
}
```

Đây là **fragment minh họa**, cần imports và một class `@Aspect` được Spring đăng ký. Nếu cả hai lần `proceed()` thực sự đến target và target không lỗi, `charge()` có thể bị gọi **hai lần**, tạo **hai khoản phí** nếu nghiệp vụ không được thiết kế an toàn.

Do đó, câu trả lời đúng không chỉ là “được phép gọi nhiều lần” mà còn phải trả lời:

- Những advice nào chạy một lần, những advice nào chạy lại?
- Nếu lần đầu ném exception thì lần sau có thể chạy được không?
- Caller nhận return value của lần nào?
- Có tạo lại Spring bean, proxy hoặc transaction không?
- Có thực hiện lại các tác dụng phụ như ghi database, gửi email, publish event hoặc trừ tiền không?
- Có thể truyền **arguments mới** cho mỗi lần `proceed` không?
- Khi nào không nên dùng kỹ thuật này?

Bài chỉ tập trung vào **Spring Aspect / Spring proxy-based AOP**, không triển khai lý thuyết AspectJ weaving độc lập như một chủ đề riêng.

## 2. Mô hình thực thi: lặp phần chain **phía trong**, không khởi chạy lại toàn bộ

Giả sử cùng một Spring service được bao bởi hai `@Around` advices:

- **RetryAspect** có precedence cao hơn nên nằm **ngoài**.
- **TraceAspect** có precedence thấp hơn nên nằm **trong**.
- Target bean là `WorkService`.

```text
Caller
  |
  v
Spring AOP proxy
  |
  v
RetryAspect.around()            <-- vào MỘT lần từ caller
  |
  +---- proceed() lần 1 ----> TraceAspect.around()
  |                              |
  |                              +--> target.execute() lần 1
  |                              |
  |                          return hoặc throw
  |
  +---- proceed() lần 2 ----> TraceAspect.around()
  |                              |
  |                              +--> target.execute() lần 2
  |                              |
  |                          return hoặc throw
  |
  +---- RetryAspect chọn kết quả/exception cuối
  |
  v
Caller
```

Khi `RetryAspect` gọi `proceed()`, Spring chuyển sang **phần invocation chain nằm sau advice đó**. Nếu `TraceAspect` và target nằm phía trong, chúng được thực thi lại mỗi lần chuyển tiếp.

**Không suy luận rằng một `proceed()` thứ hai sẽ tự gọi lại advice bên ngoài hoặc toàn bộ caller.** `RetryAspect.around()` tiếp tục thực thi trong **cùng Java invocation** do caller tạo; local variables như `attempt` vẫn thuộc lần gọi đó.

Cần phân biệt:

1. **Một lần caller gọi proxy:** `service.execute("retry")).
2. **Một lần vào outer around advice:** `RetryAspect.around(...)`.
3. **Hai lần `proceed()`:** có thể vào downstream advices hai lần.
4. **Hai lần target method được thực thi:** nếu mỗi lần `proceed` đến target và không bị một downstream advice khác short-circuit.

Cụm “có thể đến target” là quan trọng: một advice phía trong cũng có quyền **không gọi `proceed()`**, chẳng hạn trả về cached value. Vì vậy số lần `proceed()` của outer advice là **số lần chuyển tiếp vào downstream chain**, không phải một cam kết toán học rằng target luôn thực thi đúng chừng đó lần.

## 3. Bảng hành vi khi gọi `proceed()` 0, 1 hoặc nhiều lần

| Số lần `proceed()` trong một `@Around` | Downstream chain | Target method | Caller nhận gì? |
| --- | --- | --- | --- |
| **0 lần** | Không được chuyển tiếp từ advice đó. | Không chạy qua đường này. | Giá trị do `@Around` tự trả hoặc exception nó ném; ví dụ cache hit. |
| **1 lần** | Được chuyển tiếp một lượt. | Thường chạy một lần nếu không bị short-circuit. | Giá trị `@Around` chọn trả; thường là giá trị của `proceed()`. |
| **2 lần** | Có hai lượt chuyển tiếp độc lập. | Có thể chạy hai lần; mọi side effect có thể lặp. | Không tự động nhận “cả hai”; caller nhận **một** giá trị do advice trả. |
| **N lần có điều kiện** | Lặp theo loop/branch của advice. | Có thể chạy đến N lần nếu attempts tiếp tục và xuống được target. | Kết quả của lần thành công được `return`, fallback value hoặc exception cuối theo code. |
| **Gọi lần đầu ném exception** | Lượt đầu bị gián đoạn do exception. | Target đã có thể thực thi một phần. | Nếu `@Around` bắt lỗi rồi gọi lại, caller có thể thấy kết quả lượt sau; nếu không bắt, exception truyền ra. |

**`proceed()` không tự động retry, không tự rollback và không tự quyết định return value.** Chính code trong `@Around` phải lựa chọn điều kiện lặp, số lần lặp, loại exception được phép retry và giá trị trả ra.

## 4. `proceed()` nhiều lần có gọi lại các advice khác không?

Có, **nếu các advice đó nằm phía trong** current around advice và vẫn match invocation.

Ví dụ thứ tự xác định bằng `@Order`:

```java
@Aspect
@Order(0)
public class OuterRetryAspect {
    @Around("execution(* com.example..WorkService.execute(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        try {
            return pjp.proceed();
        } catch (TemporaryFailure ex) {
            return pjp.proceed();
        }
    }
}
```

```java
@Aspect
@Order(10)
public class InnerTraceAspect {
    @Around("execution(* com.example..WorkService.execute(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("trace-in");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("trace-out");
        }
    }
}
```

Đây là **fragments minh họa** cần các imports, các type `WorkService`/`TemporaryFailure` và Spring configuration; bản chạy hoàn chỉnh ở phần 7.

Trong Spring, `@Order` có **giá trị nhỏ hơn thì precedence cao hơn**. Aspect precedence cao hơn nằm ngoài: chạy trước trên đường vào, chạy sau trên đường ra. Vì thế với `@Order(0)` và `@Order(10)`, `InnerTraceAspect` có thể in **hai cặp log** dù caller chỉ gọi service **một lần** và `OuterRetryAspect` được vào một lần.

Nếu đảo thứ tự, để trace nằm **ngoài** retry, trace thường in một cặp log cho một lần proxy invocation; phần retry bên trong có thể gọi target nhiều lần. Khi giải thích số lần advice chạy, phải hỏi **advice nằm ở đâu trong chain**, không chỉ đếm annotation.

Đừng dựa vào **thứ tự khai báo source code** giữa các aspects. Với nhiều aspects, dùng `@Order` hoặc `Ordered` và kiểm chứng chain trong integration test. Những advisors do transaction, security hoặc caching tạo ra cũng có ordering riêng.

## 5. Nếu lần đầu thành công nhưng vẫn gọi lần hai thì sao?

Đây là khác biệt giữa **retry** và **replay**:

### Retry có điều kiện

```java
Object result;
try {
    result = pjp.proceed();
} catch (TemporaryFailure ex) {
    result = pjp.proceed();
}
return result;
```

Nếu lượt đầu thành công, lượt thứ hai **không diễn ra**. Nếu lượt đầu ném đúng loại lỗi được bắt, lời gọi được thử lại. Đây là fragment áp dụng khi lỗi **có thể retry an toàn** và lời gọi qua Spring proxy đúng.

### Replay cố ý, kể cả thành công

```java
Object first = pjp.proceed();
Object second = pjp.proceed();
return second;
```

Nếu hai lượt đều thành công, method phía trong vẫn có thể chạy **hai lần**. Caller thấy kết quả thứ hai vì `@Around` trả `second`, nhưng tác dụng phụ của **lần thứ nhất không tự bị xóa**.

Nếu đổi thành `return first`, caller sẽ nhận kết quả thứ nhất dù target có thể đã thực thi lần thứ hai. Nếu `return new SomeResult(...)`, caller lại nhận giá trị hoàn toàn do aspect dựng lên, miễn tương thích method return type. `@Around` có quyền **chọn return value**; không có quy tắc bắt buộc nó trả giá trị từ lời `proceed` cuối cùng.

### Nếu lần hai thất bại?

```java
Object first = pjp.proceed();
Object second = pjp.proceed(); // Nếu ném lỗi, lệnh return ở dưới không chạy.
return second;
```

Một lỗi ở lượt thứ hai có thể khiến caller nhận exception, **dù lượt thứ nhất đã hoàn thành thành công và gây side effect**. Không xem `proceed` nhiều lần như một transaction tự động bao trọn nhiều lượt.

## 6. `proceed(Object[] args)`: thay tham số ở mỗi lượt

Spring AOP cung cấp hai overload:

```java
Object proceed() throws Throwable;
Object proceed(Object[] args) throws Throwable;
```

`proceed()` chuyển tiếp theo arguments hiện có của join point. `proceed(Object[] args)` cho phép chuyển tiếp với **một bộ arguments mới** đến method phía trong.

Ví dụ một service có method `String send(String value)`:

```java
Object originalResult = pjp.proceed();

Object[] newArgs = {"normalized"};
Object replacementResult = pjp.proceed(newArgs);

return replacementResult;
```

Đây là fragment trong `@Around` của Spring `@Aspect`. Nếu cả hai lượt đến target:

- Lượt đầu nhận arguments đang có của invocation.
- Lượt hai nhận argument `"normalized"`.
- Caller thấy giá trị do advice `return`; không tự động nhận hai kết quả.

**Các ràng buộc:**

- Array phải tương thích **số lượng và kiểu** arguments của underlying method; sai kiểu hoặc sai độ dài có thể dẫn tới lỗi invocation.
- `null` không hợp lệ cho tham số primitive mà cơ chế gọi cần unbox.
- Nếu dự định thử lại với **cùng dữ liệu**, tạo snapshot an toàn của arguments nếu cần; đừng âm thầm mutate một `Object[]` dùng chung và giả định hai lượt đồng nhất.
- Khi arguments chứa **mutable objects**, `proceed()` không copy sâu các objects đó. Nếu target sửa argument tại lượt đầu, lượt sau có thể quan sát state mới dù array reference không đổi.
- Spring docs lưu ý semantics của `proceed(Object[])` trong Spring proxy AOP **khác chi tiết** so với AspectJ weaving thuần; bài này chỉ sử dụng quy tắc Spring: array ứng với **arguments của underlying method**.

Không gọi `proceed` để “xem thử” target sẽ trả gì rồi gọi lại “cho thật” trừ khi operation thực sự cho phép lặp mà không gây thiệt hại.

## 7. Ví dụ runnable Java 17+: retry lỗi một lần và gọi thành công hai lần

Ví dụ này chỉ dùng **Spring Framework 6.1.14**, `aspectjweaver 1.9.22.1`, **JDK 17+** và Maven. Đây là bộ phiên bản cố định để minh họa, không khẳng định phiên bản mới nhất.

Mục tiêu quan sát:

1. Một caller gọi `service.execute("retry")` đúng **một lần**.
2. `ReplayAspect` bên ngoài gọi `proceed()` lần thứ nhất; target **ném `TemporaryFailure`**.
3. Aspect bắt lỗi và gọi `proceed()` lần hai; target thành công.
4. `TraceAspect` ở phía trong chạy **hai lần** cho hai attempts.
5. Caller gọi `service.execute("twice")` đúng **một lần** nhưng advice cố ý gọi `proceed()` **hai lần dù cả hai đều thành công**.
6. Đếm tổng số lần target method được thực thi: **4**, dù caller chỉ có **hai** lời gọi.

Ví dụ `"twice"` chỉ dùng để phơi bày rủi ro **duplicate successful execution**, không phải pattern khuyến nghị cho ghi dữ liệu sản xuất.

### 7.1. Cấu trúc project

```text
spring-multiple-proceed/
├── pom.xml
└── src/main/java/com/example/replay/
    ├── WorkService.java
    ├── ReplayAspect.java
    ├── TraceAspect.java
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
    <artifactId>spring-multiple-proceed</artifactId>
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

`spring-context` mang container và các Spring AOP dependencies phù hợp theo cây phụ thuộc của ví dụ; `aspectjweaver` cần để Spring xử lý `@Aspect`/pointcut. Đây vẫn là **proxy-based Spring AOP**, không yêu cầu AspectJ weaving agent.

### 7.3. `src/main/java/com/example/replay/WorkService.java`

```java
package com.example.replay;

import java.util.concurrent.atomic.AtomicInteger;

public class WorkService {
    private final AtomicInteger calls = new AtomicInteger();

    public static class TemporaryFailure extends RuntimeException {
        public TemporaryFailure(String message) {
            super(message);
        }
    }

    public String execute(String label) {
        int n = calls.incrementAndGet();
        System.out.println("[TARGET] " + label + " call=" + n);

        if ("retry".equals(label) && n == 1) {
            throw new TemporaryFailure("once");
        }

        return label + "#" + n;
    }

    public int invocationCount() {
        return calls.get();
    }
}
```

Target chủ động ném một `TemporaryFailure` **chỉ ở lần thực thi đầu tiên của `execute("retry")`**. Trạng thái `calls` có thể được kiểm tra qua method `invocationCount()`.

`AtomicInteger` ở đây tránh data race khi đếm; nhưng `execute` được chạy tuần tự trong ví dụ. Mọi kết quả stdout bên dưới giả định chỉ một `DemoApp` sử dụng instance và chạy các lệnh theo đúng thứ tự.

### 7.4. `src/main/java/com/example/replay/ReplayAspect.java`

```java
package com.example.replay;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(0)
public class ReplayAspect {

    @Around("execution(public * com.example.replay.WorkService.execute(..))")
    public Object replay(ProceedingJoinPoint pjp) throws Throwable {
        String label = (String) pjp.getArgs()[0];

        if ("retry".equals(label)) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                System.out.println("[OUTER] attempt " + attempt);
                try {
                    return pjp.proceed();
                } catch (WorkService.TemporaryFailure ex) {
                    System.out.println("[OUTER] caught " + ex.getMessage());
                    if (attempt == 2) {
                        throw ex;
                    }
                }
            }
            throw new IllegalStateException("Unreachable retry branch");
        }

        if ("twice".equals(label)) {
            System.out.println("[OUTER] call A");
            Object first = pjp.proceed();

            System.out.println("[OUTER] call B");
            Object second = pjp.proceed();

            System.out.println("[OUTER] first=" + first
                    + "; return=" + second);
            return second;
        }

        return pjp.proceed();
    }
}
```

Aspect này mô tả **hai chính sách khác nhau chỉ để dạy về số lần `proceed()`**:

- Với `"retry"`, nó thử tối đa **hai lần**, chỉ retry khi gặp `TemporaryFailure`; nếu thành công sớm, `return` kết thúc loop ngay.
- Với `"twice"`, nó **cố ý gọi target hai lần** dù lượt đầu thành công. Điều đó phù hợp về mặt API, nhưng rất nguy hiểm cho method có side effects.
- Với label khác, nó chỉ gọi `proceed()` một lần.

Vì aspect có `@Order(0)`, nó được đặt **ngoài** `TraceAspect` có `@Order(10)` trong cùng Spring advisor chain của demo.

### 7.5. `src/main/java/com/example/replay/TraceAspect.java`

```java
package com.example.replay;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(10)
public class TraceAspect {

    @Around("execution(public * com.example.replay.WorkService.execute(..))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("[INNER] before");
        try {
            Object result = pjp.proceed();
            System.out.println("[INNER] after=" + result);
            return result;
        } catch (Throwable ex) {
            System.out.println("[INNER] threw="
                    + ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
```

`TraceAspect` chỉ cần gọi `proceed()` **một lần trong mỗi lần advice được kích hoạt**. Nhưng nó có thể được kích hoạt **hai lần** nếu outer aspect gọi `proceed()` hai lần.

Đây chính là điểm dễ nhầm: **một advice chỉ có một dòng `pjp.proceed()` vẫn có thể chạy nhiều lần cho một lời gọi proxy bên ngoài**, khi một around advice có precedence cao hơn lặp phần chain của nó.

### 7.6. `src/main/java/com/example/replay/AppConfig.java`

```java
package com.example.replay;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public WorkService workService() {
        return new WorkService();
    }

    @Bean
    public ReplayAspect replayAspect() {
        return new ReplayAspect();
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }
}
```

`@EnableAspectJAutoProxy` kích hoạt Spring auto-proxy creator. `proxyTargetClass = true` dùng CGLIB class proxy cho `WorkService` không implement business interface. Hai `@Aspect` classes được đăng ký **thành Spring beans** thông qua `@Bean`. Chỉ đặt `@Aspect` mà không đăng ký bean thì Spring chưa chắc nhận diện advice.

### 7.7. `src/main/java/com/example/replay/DemoApp.java`

```java
package com.example.replay;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApp {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            WorkService service = context.getBean(WorkService.class);
            System.out.println("isAopProxy="
                    + AopUtils.isAopProxy(service));

            String retryResult = service.execute("retry");
            System.out.println("caller.retry=" + retryResult);

            String twiceResult = service.execute("twice");
            System.out.println("caller.twice=" + twiceResult);

            System.out.println("targetCalls=" + service.invocationCount());
        }
    }
}
```

Caller dùng bean từ `ApplicationContext`, không tự tạo `new WorkService()` để gọi. Đây là điều kiện để lời gọi đi vào Spring AOP proxy, nơi hai aspects đã được tạo thành advisor chain.

### 7.8. Chạy và stdout dự kiến

Với JDK 17+, Maven có thể tải dependencies, tại thư mục có `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.replay.DemoApp
```

Các dòng do code ví dụ tự in theo đúng thứ tự (không bao gồm thông báo Maven/Spring/SLF4J khác tùy môi trường):

```text
isAopProxy=true
[OUTER] attempt 1
[INNER] before
[TARGET] retry call=1
[INNER] threw=TemporaryFailure
[OUTER] caught once
[OUTER] attempt 2
[INNER] before
[TARGET] retry call=2
[INNER] after=retry#2
caller.retry=retry#2
[OUTER] call A
[INNER] before
[TARGET] twice call=3
[INNER] after=twice#3
[OUTER] call B
[INNER] before
[TARGET] twice call=4
[INNER] after=twice#4
[OUTER] first=twice#3; return=twice#4
caller.twice=twice#4
targetCalls=4
```

**Giải thích theo số lần thực thi:**

- Caller gọi `execute("retry")` **một lần**, nhưng `ReplayAspect` thực hiện hai `proceed()` do lần đầu gặp lỗi. `TraceAspect` vào hai lần và target vào hai lần.
- Lần đầu target tăng `calls` thành 1 **trước khi ném exception**; retry không tự hoàn tác việc tăng counter đó. Lần hai tăng thành 2 và thành công.
- Caller chỉ nhận `retry#2`; exception đầu tiên đã được outer aspect bắt và không truyền ra nữa.
- Caller gọi `execute("twice")` **một lần**, outer aspect gọi `proceed()` hai lần dù lần đầu thành công, nên target tăng `calls` thành 3 rồi 4.
- Lần đầu của chế độ `twice` trả `twice#3`; lần hai trả `twice#4`. Outer aspect **trả `second`**, vì thế caller chỉ thấy `twice#4`, dù side effect tăng counter ở cả hai lượt.
- `targetCalls=4` chứng minh **số lời gọi từ caller (2)** không bằng **số lần target được chạy (4)**.

Ví dụ này kiểm chứng hành vi **Spring AOP trên một proxy được quản lý**, không mô tả AspectJ compile-time weaving. Stdout là kết quả dự kiến theo source code đã trình bày; khi mang vào dự án thật, chạy integration test trên đúng Spring/JDK version đang triển khai.

## 8. Ứng dụng thực tế: retry phải có giới hạn và điều kiện rõ

Spring Framework dùng retry các lỗi concurrency làm ví dụ tiêu biểu cho `@Around` gọi `proceed()` nhiều lần. Nhưng ứng dụng sản xuất cần nhiều bảo vệ hơn một vòng `for` đơn giản.

Một retry policy hợp lý cần trả lời:

1. **Lỗi gì có thể retry?** Chỉ bắt nhóm lỗi transient đã xác định, không tự retry `IllegalArgumentException`, lỗi validation hoặc lỗi logic vĩnh viễn.
2. **Bao nhiêu attempts tối đa?** Có limit rõ ràng; không `while (true)` vô hạn.
3. **Chờ giữa các attempts?** Dùng backoff/jitter hợp lý theo hệ thống; không bắt buộc ngủ với mọi lỗi.
4. **Operation có idempotent không?** Nếu gọi lại có thể trừ tiền/gửi email hai lần, retry cần idempotency key, transactional/outbox mechanism hoặc protocol cho phép deduplication.
5. **Attempt trước đã làm gì?** Lỗi xảy ra **sau khi** ghi DB hoặc gọi remote API có thể để lại side effect trước khi exception đến around advice.
6. **Exception cuối đi đâu?** Khi hết attempts, phải propagate hoặc chuyển thành lỗi có hợp đồng rõ ràng, không âm thầm nuốt lỗi.
7. **Retry có khả năng thành công không?** Lỗi vĩnh viễn không nên được lặp.
8. **Metrics thế nào?** Đếm caller requests, retry attempts, target executions và final outcome **riêng biệt**.
9. **Cancellation/timeouts?** Nếu invocation bị hủy hoặc deadline hết, không tiếp tục retry vô điều kiện.
10. **Thread safety?** Lưu attempt counter trong local variable của method advice, không trong mutable field của singleton aspect nếu state cần riêng cho từng request.

Một cấu trúc retry dạng fragment:

```java
@Around("execution(* com.example.service.SomeService.retryableOperation(..))")
public Object retry(ProceedingJoinPoint pjp) throws Throwable {
    int maxAttempts = 3;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        try {
            return pjp.proceed();
        } catch (TransientBusinessException ex) {
            if (attempt == maxAttempts) {
                throw ex;
            }
            // Áp dụng backoff/cancellation policy nếu cần.
        }
    }
    throw new IllegalStateException("Unreachable");
}
```

Đây là **fragment**, không chạy độc lập vì `SomeService` và `TransientBusinessException` phải được định nghĩa. Nó chỉ mô tả retry cho method **đã được xác nhận an toàn để thử lại**; đừng áp dụng pointcut quá rộng lên mọi `service.*` chỉ vì code có thể chạy.

### 8.1. Retry và side effect không an toàn

Ví dụ nguy hiểm:

```java
public Receipt charge(String paymentId) {
    remotePaymentGateway.charge(paymentId);
    database.saveAudit(paymentId);
    return new Receipt(paymentId);
}
```

Giả sử remote gateway đã xử lý thành công, nhưng database save thất bại và method ném lỗi. Nếu một `@Around` retry **toàn bộ method**, lượt tiếp theo có thể gọi remote gateway **lần hai**.

`proceed()` không cung cấp transaction phân tán, không suy ra một lần trước có side effect hay chưa và không tự tạo idempotency key. Phải thiết kế retries phù hợp nghiệp vụ, transaction boundaries và hành vi hệ thống bên ngoài.

## 9. Vị trí retry advice so với `@Transactional` rất quan trọng

Nếu một phương thức được áp dụng cả retry và transaction interceptor, ordering ảnh hưởng tới phạm vi transaction.

### Trường hợp A: retry **ở ngoài** transaction

```text
Caller
  -> RetryAround
       -> proceed attempt #1
            -> TransactionInterceptor (begin tx #1)
                 -> target
            -> rollback/commit tx #1
       -> proceed attempt #2
            -> TransactionInterceptor (begin tx #2)
                 -> target
            -> rollback/commit tx #2
```

Khi đúng cấu hình và **không có transaction bên ngoài đang được tham gia**, mỗi attempt có thể có transaction mới. Đây là cách tài liệu Spring minh họa retry concurrency failure để mong mỗi retry là một transaction độc lập.

### Trường hợp B: transaction **ở ngoài** retry

```text
Caller
  -> TransactionInterceptor (begin tx)
       -> RetryAround
            -> proceed attempt #1 -> target
            -> proceed attempt #2 -> target
  -> commit/rollback một tx bao ngoài
```

Các attempts nằm dưới một transaction boundary bên ngoài. Với exception đã khiến transaction bị đánh dấu rollback-only, việc retry ngay trong transaction ấy có thể không giúp transaction commit được. Do đó **không được coi hai sơ đồ là tương đương**.

**Những điều cần kiểm tra trong Spring:**

- Retry aspect có precedence đủ cao để ở **ngoài** transaction advisor không?
- `@EnableTransactionManagement(order = ...)` hoặc transaction infrastructure của ứng dụng đang có thứ tự nào?
- Có transaction từ caller đã tồn tại trước khi vào service không?
- Propagation mode là `REQUIRED`, `REQUIRES_NEW` hay mode khác?
- Có callback/DB connection/resource nào còn giữ state sau attempt lỗi không?
- Exception có bị bắt ở vị trí khiến transaction interceptor không quan sát được lỗi không?

Dù retry đứng ngoài transaction advisor, nếu caller đã có một transaction bao ngoài và transaction propagation tham gia transaction đó, không thể khẳng định mỗi attempt có một transaction độc lập. Phải kiểm thử transaction IDs/commit/rollback trong cấu hình thật.

Spring có những abstraction chuyên dụng như Spring Retry hoặc resilience patterns khác tùy stack; khi ứng dụng cần policy phức tạp, đừng tự triển khai một vòng lặp `proceed()` thiếu backoff, timeout và quan sát lỗi.

## 10. `proceed()` nhiều lần không đồng nghĩa lặp toàn bộ HTTP request

Trong một ứng dụng Spring MVC/REST:

```text
HTTP request -> Filter chain -> DispatcherServlet
              -> Controller -> Spring service proxy
                                  |
                                  +-- Around.proceed() #1 -> target
                                  +-- Around.proceed() #2 -> target
```

Nếu around advice áp dụng ở **service bean**, việc gọi lại `proceed()` thường chỉ lặp phần method invocation chain của **service**, không lặp:

- Toàn bộ Servlet filter chain.
- DispatcherServlet từ đầu.
- Mapping/authentication/request binding ở tầng controller đã hoàn tất.
- Những hoạt động trước khi caller gọi service proxy.

Tương tự, một transaction interceptor hoặc inner logging aspect **có thể** nằm bên trong method chain và bị lặp, nhưng middleware ở **ngoài** method advice không tự chạy lại.

Đây là lý do cần phân biệt **request-level metrics** và **attempt-level metrics**: một HTTP request có thể sinh một lời gọi service proxy, nhưng bốn lần target execution nếu retry/replay có bốn attempts.

## 11. Trường hợp asynchronous hoặc reactive return type

Nếu method được advice trả về `CompletableFuture<T>`, `CompletionStage<T>`, `Mono<T>` hoặc `Flux<T>`, `proceed()` chuyển tiếp **Java method invocation**; nó không tự biến thành một lệnh đợi toàn bộ công việc bất đồng bộ hoàn tất.

Ví dụ:

```java
CompletableFuture<Result> first =
        (CompletableFuture<Result>) pjp.proceed();
CompletableFuture<Result> second =
        (CompletableFuture<Result>) pjp.proceed();
return second;
```

Đây là fragment. Nó có thể tạo **hai futures / hai luồng xử lý bất đồng bộ**, tùy implementation của method, chứ không phải “thử lại sau khi future thứ nhất đã lỗi”. Future thứ nhất vẫn có thể đang chạy khi caller nhận future thứ hai.

Với Reactor publisher, việc gọi method trả về `Mono<T>` hai lần có thể tạo hai publishers; **subscription** sau đó mới kích hoạt công việc nếu publisher cold, còn hot publisher có hành vi khác. Vì vậy không được suy rằng hai lần `proceed()` bằng hai lần subscription hoặc hai lần kết quả cuối.

Nếu retry cần dựa trên **lỗi async completion**, sử dụng đúng cơ chế như `CompletionStage` composition, Reactor `retryWhen` hoặc một thư viện retry/resilience phù hợp, đồng thời xét idempotency và transaction semantics của reactive stack. Không viết một vòng `for` đồng bộ quanh `pjp.proceed()` rồi giả định nó xử lý mọi lỗi xảy ra về sau.

## 12. Những giới hạn của Spring proxy vẫn áp dụng

Việc `proceed()` hỗ trợ gọi nhiều lần **không khắc phục giới hạn proxy-based Spring AOP**:

- Chỉ áp dụng khi caller dùng **Spring-managed proxy**. `new WorkService().execute(...)` không được auto-proxy chỉ nhờ có `@Aspect`.
- **Self-invocation** như `this.execute()` bên trong target bypass Spring AOP proxy; pointcut của method nội bộ không được Spring gọi qua proxy chỉ nhờ `proceed()` trong một method khác.
- Với JDK dynamic proxy, method cần được gọi qua proxied interface thích hợp. Với CGLIB, `final`/`private` method không được intercept bằng subclass proxy như public overridable method.
- Các `@Aspect` classes phải là **Spring beans**; `@Aspect` một mình chưa đăng ký bean.
- Auto-proxy creator và aspects có **phạm vi ApplicationContext**; bật AOP ở context khác không bảo đảm bean hiện tại được proxy.
- Aspect singleton có thể phục vụ nhiều requests đồng thời; tránh giữ `attempt`, `lastError` hoặc `ProceedingJoinPoint` trong shared mutable fields.
- Không nên giữ `ProceedingJoinPoint` để gọi `proceed()` **sau khi advice đã return**, hoặc chuyển nó sang thread khác rồi kỳ vọng toàn bộ interceptor/transaction/thread-local context còn hợp lệ. Nó đại diện cho invocation đang được xử lý, không phải một “callback lâu dài” tùy ý chạy lại.

Một test chỉ gọi target trực tiếp bằng `new` sẽ không chứng minh gì về khả năng multiple-proceed của Spring AOP. Phải gọi qua bean/proxy trong `ApplicationContext`.

## 13. Kiểm thử và chẩn đoán số lần thực thi

Một integration test có thể tách ít nhất ba loại counter:

| Counter | Nó đếm gì? | Kết quả demo `retry` | Kết quả demo `twice` |
| --- | --- | --- | --- |
| Caller invocation count | Số lần code bên ngoài gọi service proxy | 1 | 1 |
| Outer around invocation count | Số lần chính retry/replay advice được vào từ proxy | 1 | 1 |
| `proceed()` attempt count | Số lần outer around chuyển tiếp vào chain | 2 | 2 |
| Inner trace count | Số lần advice phía trong chạy | 2 | 2 |
| Target execution count | Số lần target thực sự thực hiện body | 2 | 2 |
| Caller-visible result | Giá trị được outer around return | `retry#2` | `twice#4` |

Bảng áp dụng cho **project demo đã nêu**, nơi mỗi `proceed()` đều tới target. Nếu có cache/short-circuit phía trong, các counts có thể khác.

Kiểm thử nên gồm:

1. **Không lỗi:** retry policy `return pjp.proceed()` ngay ở attempt 1, target chạy một lần.
2. **Lỗi transient rồi thành công:** target chạy hai lần; caller chỉ nhận một thành công.
3. **Lỗi mãi:** dừng ở max attempts và propagate exception đúng loại.
4. **Lỗi không nằm trong allowlist:** không retry.
5. **Method có side effect:** xác nhận idempotency/deduplication và số lần ghi gửi thật.
6. **Nhiều aspects:** xác nhận `@Order`, log/metric ở inner và outer theo ý muốn.
7. **Arguments:** `proceed()` giữ input, `proceed(newArgs)` chuyển đúng arguments mong muốn.
8. **Return type:** caller nhận một value hợp lệ, không ép kiểu lỗi với primitive/generic.
9. **Transaction:** xác nhận từng attempt có commit/rollback đúng khi có Spring transaction advisor.
10. **Concurrency:** mỗi invocation có attempt counter riêng, không chia sẻ sai với invocation khác.
11. **Proxy boundary:** `AopUtils.isAopProxy(bean)` và lời gọi thật qua Spring bean.
12. **Timeout/cancel:** không chạy lại khi request/deadline đã hết theo policy nghiệp vụ.

Khi chẩn đoán “một API call mà log target lặp”, kiểm tra **có outer `@Around` nào gọi `pjp.proceed()` nhiều lần không**, rồi mới xem HTTP client gửi trùng request. Log tương ứng request ID và **attempt number** giúp phân biệt replay trong cùng request với một request thứ hai thực sự.

## 14. Các lỗi thường gặp

| Triệu chứng | Nguyên nhân khả dĩ | Hướng xử lý |
| --- | --- | --- |
| Target chạy hai lần, caller chỉ gọi service một lần | Outer `@Around` gọi `proceed()` hai lần | Đếm attempts, kiểm tra branch và idempotency. |
| Trace `@Around` chạy hai lần dù chỉ có một caller | Trace nằm **trong** retry/replay around | Kiểm tra `@Order` và chain nesting. |
| Target không chạy dù lời gọi đã vào proxy | `@Around` không gọi `proceed()` hoặc inner advice short-circuit | Kiểm tra chính sách cache/guard/return sớm. |
| Caller nhận giá trị lượt hai, lượt một vẫn đã ghi DB | Return value và side effect là hai vấn đề khác nhau | Bảo đảm rollback/compensation/idempotency; không chỉ đổi `return first`. |
| Lỗi lần đầu biến mất | Around bắt exception và retry thành công | Đây là retry được viết rõ; cần logging/metrics attempts và policy cho lỗi cuối. |
| Hết retries nhưng caller lại nhận `null` | Advice tự trả null/nuốt exception sau loop | Propagate exception hoặc trả fallback có hợp đồng đúng. |
| Advice `void` quanh method có return làm mất giá trị | `@Around` không trả `Object` đúng contract | Dùng `Object` return và `return pjp.proceed()` hoặc replacement hợp lệ. |
| Retry trong một transaction vẫn rollback | Transaction advisor ở ngoài hoặc transaction hiện hành đã rollback-only | Xem ordering/propagation, test transaction boundaries. |
| Gửi email/charge hai lần | Operation không idempotent hoặc lỗi sau side effect | Dùng idempotency key, dedupe/outbox/compensation; giới hạn retry. |
| `proceed(newArgs)` ném lỗi | Arguments array sai số lượng, kiểu hoặc null primitive | Kiểm tra underlying method signature và kiểu runtime. |
| Retry future không bắt lỗi async | `proceed()` đã return future trước khi async task lỗi | Retry qua async/reaction operators hoặc return wrapper xử lý completion. |
| Không có `@Around` dù pointcut đúng | Caller bypass Spring proxy hoặc aspect chưa được đăng ký bean | Kiểm tra `@EnableAspectJAutoProxy`, context, bean refs, self-invocation. |
| Thấy outer logger chỉ in một lần nhưng target hai lần | Outer logger nằm ngoài replay advice | Phân biệt số lần caller, outer advice, downstream chain, target. |

## 15. Checklist trước khi đưa multiple-`proceed()` vào sản xuất

- [ ] Có lý do cụ thể để dùng `@Around` thay vì `@Before`/`@AfterReturning`.
- [ ] Operation được chọn bởi pointcut **hẹp và rõ**, không vô tình retry toàn bộ service layer.
- [ ] Chỉ retry những lỗi transient đã xác nhận có thể phục hồi.
- [ ] Có giới hạn attempts, backoff và deadline/cancellation policy.
- [ ] Đã đánh giá idempotency và mọi side effects của mỗi attempt.
- [ ] Đã phân biệt request-level metrics với attempt-level metrics.
- [ ] `@Order`/transaction advisor ordering đã được kiểm tra khi có transaction.
- [ ] Không có transaction ambient khiến các attempts vô tình chung một transaction không mong muốn.
- [ ] Arguments và mutable payload có chiến lược snapshot/khôi phục nếu cần.
- [ ] Exception cuối không bị nuốt; return value đúng kiểu method contract.
- [ ] Không lưu `ProceedingJoinPoint` hoặc per-call retry state trong mutable field của singleton aspect.
- [ ] Có integration tests chạy qua Spring proxy và xác minh số lần target thật sự chạy.
- [ ] Với async/reactive methods, retry được đặt ở đúng lifecycle của completion/subscription.
- [ ] Có cách quan sát attempts/side effects trên hệ thống thực tế.

### Tài liệu Spring chính thức

- Spring Framework — **Declaring Advice / Around Advice** (xác nhận `proceed()` có thể được gọi 0, 1 hoặc nhiều lần): https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — **An AOP Example** (retry lỗi concurrency bằng multiple `proceed()`, ordering với transaction advice): https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/example.html
- Spring Framework — **Schema-based AOP Support** (around advice và retry example trong XML): https://docs.spring.io/spring-framework/reference/core/aop/schema.html
- Spring Framework — **Proxying Mechanisms** (giới hạn lời gọi qua proxy): https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — **AOP Concepts** (quyền điều khiển method invocation của around advice): https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html

**Kết luận:** Trong Spring AOP, `proceed()` nhiều lần là **lặp lại phần còn lại của invocation chain** tại vị trí của `@Around`, không phải tự động gọi lại toàn bộ request hoặc toàn bộ proxy từ bên ngoài. Kỹ thuật này giúp triển khai retry, nhưng cũng có thể làm downstream advices và target chạy nhiều lần, với side effects, transaction boundaries, exception và return values riêng. Chỉ sử dụng khi đã kiểm chứng đầy đủ khả năng retry an toàn của nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-005">proceed(Object&#91;&#93;) và thay đổi đối số theo quy ước Spring AOP</a>

<details>
<summary>Xem chi tiết</summary>

# proceed(Object[]) và thay đổi đối số theo quy ước Spring AOP

## 1. Mục tiêu bài học

Trong Spring AOP, advice `@Around` nhận một `ProceedingJoinPoint` và có quyền quyết định **khi nào, với đối số nào, bao nhiêu lần** method đích thực sự chạy. Phần này tập trung vào hai cách tiếp tục lời gọi:

- **`pjp.proceed()`:** chuyển tiếp đến chuỗi interceptor/method đích với **các đối số hiện có của lời gọi**. Khi chưa có advice trước đó thay đổi chúng, đó là các đối số mà caller ban đầu truyền vào.
- **`pjp.proceed(Object[] arguments)`:** tiếp tục lời gọi với **mảng đối số thay thế**, mỗi phần tử ứng với **một tham số của method đang được advice**, **cùng vị trí và thứ tự**.

Quy ước thứ hai là điểm cực kỳ quan trọng: **trong Spring AOP, mảng truyền cho `proceed(Object[])` đại diện cho argument list của method đích, không phải danh sách tham số của method advice**. Nếu method đích nhận hai tham số thì mảng mới phải cung cấp hai giá trị, kể cả khi method advice chỉ bind một trong hai tham số đó.

Bài này tập trung vào Spring Framework và Spring Aspect, không trình bày lại đầy đủ AspectJ weaving. Ta sẽ xây dựng project Maven Java 17+ chạy qua Spring bean proxy, quan sát đối số trước và sau khi advice thay đổi, đồng thời chỉ ra những lỗi thường gặp về **arity (số đối số), thứ tự, kiểu, `null`, binding và exception propagation**.

### Hình dung ngắn

~~~text
Caller:
    service.submit(" alice ", 2)
               |
               v
       Spring AOP proxy
               |
               v
    @Around: pjp.getArgs() -> [" alice ", 2]
               |
               | pjp.proceed(new Object[] {"ALICE", 3})
               v
       OrderService.submit("ALICE", 3)
               |
               v
       kết quả "order:ALICE:3"
               |
               v
       Advice trả lại kết quả
               |
               v
             Caller
~~~

Caller vẫn **đã gọi `submit(" alice ", 2)`**, nhưng target nhận `"ALICE", 3`. Chỉ các lời gọi **đi qua Spring AOP proxy** mới được advice này thực hiện; một object cùng class được tạo bằng `new` ngoài container không tự nhận advice.

## 2. Điều kiện để `proceed(Object[])` có tác dụng trong Spring

### 2.1. Around advice phải là Spring-managed aspect

Ví dụ cấu hình:

~~~java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopConfig {
    @Bean
    public OrderService orderService() {
        return new OrderService();
    }

    @Bean
    public RewriteAspect rewriteAspect() {
        return new RewriteAspect();
    }
}
~~~

Đây là **đoạn cấu hình minh họa**, chưa phải file Java hoàn chỉnh. Project đầy đủ nằm ở phần 5. Các yếu tố bắt buộc:

1. Class aspect được đánh dấu `@Aspect` **và** được đăng ký thành Spring bean (bằng `@Bean` hoặc component scanning phù hợp).
2. Target là Spring-managed bean. Spring AOP auto-proxy creator xét advisor, tạo proxy khi method phù hợp và cho caller sử dụng reference proxy.
3. Method execution phải khớp `@Around` pointcut. `proceed(Object[])` chỉ được gọi **bên trong advice đang thực thi**.
4. Method muốn được advice phải phù hợp với cơ chế proxy JDK/CGLIB. Với class proxy, `final`/`private` methods không thể được intercept theo kiểu override; self-invocation vẫn có thể bỏ qua proxy.
5. `ProceedingJoinPoint` phải là **tham số đầu của `@Around` advice**. Chỉ advice loại `@Around` nhận loại join point có `proceed`; `@Before`/`@AfterReturning` không có quyền tự quyết định method đích được gọi ra sao.

### 2.2. Hai overload

~~~java
Object result = pjp.proceed();
~~~

Không truyền mảng mới; method tiếp tục với argument list đang có trong join point. Đây là lựa chọn nên ưu tiên nếu chỉ cần quan sát, ghi log, đo thời gian hoặc làm thứ gì đó không thay đổi đối số.

~~~java
Object result = pjp.proceed(
        new Object[] { "ALICE", Integer.valueOf(3) }
);
~~~

Dùng argument list mới. Trong ví dụ `submit(String customer, int quantity)`, phần tử thứ nhất là `String`, phần tử thứ hai là `Integer` (Java wrapper được unbox thành `int` khi gọi method). **Mảng có đúng hai phần tử**, không thừa/thiếu.

Dùng `Object` cho return type của advice và `return pjp.proceed(...)` là cách thông thường để giữ nguyên hợp đồng trả về. Với method đích `void`, advice vẫn thường khai báo trả `Object` để giữ khả năng hoạt động thống nhất qua interceptor chain.

### 2.3. Khi nào *không* nên dùng overload nhận mảng?

Nếu chỉ muốn ghi log trước lời gọi:

~~~java
@Around("execution(* demo.OrderService.passthrough(..))")
public Object trace(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("AROUND: pass unchanged");
    return pjp.proceed();
}
~~~

Không cần sao chép hay tạo array mới. Thậm chí nếu chỉ cần log *trước* method, một `@Before` thường là advice **ít quyền can thiệp hơn**, dễ hiểu hơn `@Around`. Chỉ sử dụng `@Around` vì một yêu cầu thực tế cần bao cả invocation hoặc cần sửa argument/return.

## 3. Quy ước của Spring AOP: mảng đối số theo **method đích**

Giả sử target method:

~~~java
public String submit(String customer, int quantity) {
    return "order:" + customer + ":" + quantity;
}
~~~

Với Spring AOP, `proceed(Object[])` được hiểu như sau:

| Vị trí | Kiểu tham số của target | Đối số ban đầu | Đối số thay thế |
| --- | --- | --- | --- |
| `0` | `String customer` | `" alice "` | `"ALICE"` |
| `1` | `int quantity` | `2` (được boxed khi đọc qua `Object[]`) | `3` (dưới dạng `Integer` trong `Object[]`) |

~~~java
Object[] original = pjp.getArgs();
Object[] replacement = original.clone();

replacement[0] = ((String) original[0]).trim()
        .toUpperCase(java.util.Locale.ROOT);
replacement[1] = ((Integer) original[1]) + 1;

return pjp.proceed(replacement);
~~~

Đây là snippet **bên trong `@Around` advice**. Nó không dùng `Object[]` làm danh sách *tham số của advice*, mà dùng **đúng hai đối số của `submit(String,int)`**.

Vì sao nên `clone()` trước khi sửa?

- `pjp.getArgs()` dùng để **quan sát danh sách đối số** của join point; không nên dựa vào việc chỉnh trực tiếp mảng nó trả về sẽ luôn có ngữ nghĩa ổn định.
- Bản sao giúp biểu đạt rõ **đối số gốc** và **đối số chuyển xuống target**.
- Clone mảng là **shallow copy**: các object bên trong vẫn là cùng reference. Nếu một phần tử là object mutable, việc sửa thuộc tính của object đó không được giải quyết chỉ bằng `clone()`.
- Caller vẫn giữ reference tới object đầu vào ban đầu. Thay phần tử trong mảng gửi đến `proceed` khác với sửa object đầu vào tại chỗ.

**Số lượng phần tử phải đúng số tham số của method được intercept**, không phải số binding variables ở lời khai báo advice. Spring AOP thực thi method-execution join points; điều này đơn giản và trực tiếp hơn một số ngữ nghĩa của ngôn ngữ AspectJ được biên dịch/weave.

## 4. Khác biệt với quy ước truyền `proceed(Object[])` của AspectJ compiler

Đây là cảnh báo nổi bật trong tài liệu Spring chính thức.

### 4.1. Spring AOP proxy-based

Trong Spring AOP, `proceed(new Object[]{...})` nhận **toàn bộ đối số của method đích** theo thứ tự tham số. Ví dụ:

~~~java
public String inspect(String label, int units)
~~~

thì cần cung cấp **hai** đối số nếu thay đổi trước khi tiếp tục lời gọi:

~~~java
return pjp.proceed(new Object[] { "BOB", 6 });
~~~

### 4.2. AspectJ compiled around advice có quy tắc khác

Với advice được biên dịch theo **ngôn ngữ AspectJ truyền thống**, số lượng giá trị truyền cho `proceed` liên quan đến **số tham số được bind vào around advice**, chứ không mặc nhiên là toàn bộ tham số method đích. Giá trị ở vị trí bound thay thế entity tương ứng tại join point.

Vì thế, **đừng sao chép một ví dụ weaving/AspectJ compiler có bind một argument** rồi suy rằng `proceed(new Object[]{oneArg})` là đủ cho method đích hai tham số trong Spring AOP.

Tài liệu Spring đề xuất cách viết có thể tương thích giữa hai cơ chế: **bind đầy đủ các tham số method đích theo đúng thứ tự trong advice signature**, rồi cung cấp mảng mới có cùng giá trị/số phần tử theo thứ tự ấy. Ví dụ dạng một tham số:

~~~java
@Around(
    value = "execution(* demo.Repository.findByName(..)) && args(name)",
    argNames = "name"
)
public Object normalizeName(
        ProceedingJoinPoint pjp, String name) throws Throwable {
    String normalized = name.trim();
    return pjp.proceed(new Object[] { normalized });
}
~~~

Đây là snippet **minh họa**: `demo.Repository.findByName` không có trong project bên dưới, nhưng giả định nó có đúng **một tham số `String`**. Vì advice bind đúng một tham số của target, `new Object[]{normalized}` phù hợp quy tắc Spring AOP và cũng là hình dạng được tài liệu đề xuất để tránh khác biệt khi di chuyển sang cơ chế AspectJ compiler/weaver.

Đối với method đích **hai tham số**, nếu cần khả năng chuyển đổi tương thích, hãy bind đủ **hai tham số theo thứ tự**. Với Spring AOP thuần, không bắt buộc advice phải bind tất cả nếu bạn lấy đủ mảng bằng `pjp.getArgs()` và truyền đúng mảng khi `proceed`.

### 4.3. Minh họa trường hợp chỉ bind một tham số của method hai tham số

~~~java
@Around(
    value = "execution(* demo.OrderService.inspect(..)) && args(label,..)",
    argNames = "label"
)
public Object inspect(
        ProceedingJoinPoint pjp, String label) throws Throwable {
    Object[] current = pjp.getArgs();   // Method đích có 2 đối số.
    Object[] next = current.clone();
    next[0] = label.trim().toUpperCase(java.util.Locale.ROOT);
    next[1] = ((Integer) current[1]) + 2;

    return pjp.proceed(next);            // PHẢI truyền đủ 2 phần tử.
}
~~~

Ở đây advice signature chỉ có `String label` bên cạnh `ProceedingJoinPoint`, nhưng method được intercept là `inspect(String,int)`. **Mảng `next` có độ dài 2**, bởi `proceed(Object[])` theo quy ước Spring AOP tham chiếu đến method đích. Dạng này nhấn mạnh khác biệt và **không phải mẫu di động nguyên xi sang AspectJ compiler** với ngữ nghĩa binding khác.

Bài thực hành ở phần 5 sử dụng chính tình huống này, vì nó làm lộ lỗi “nhầm số tham số advice với số tham số target” một cách trực tiếp.

## 5. Dự án Spring Framework Java 17+ hoàn chỉnh

Ta chạy bốn case tách biệt, dùng bốn method khác tên để tránh chồng các `@Around` advice:

1. `passthrough(String)`: dùng `proceed()` và giữ nguyên đối số.
2. `submit(String,int)`: sửa **hai đối số** bằng `proceed(Object[])`.
3. `inspect(String,int)`: advice **chỉ bind một argument** (`label`) nhưng vẫn truyền **đủ hai giá trị**.
4. `fail(String)`: sửa argument trước khi target ném exception, sau đó **giữ nguyên exception propagation**.

### 5.1. Thư mục

~~~text
spring-aop-proceed-args/
├── pom.xml
└── src/main/java/demo/
    ├── App.java
    ├── OrderService.java
    └── RewriteAspect.java
~~~

Yêu cầu Java 17+, Maven 3.9+, Spring Framework 6.2.0 và AspectJ Weaver 1.9.22. Các phiên bản được khóa để tái lập ví dụ, không tuyên bố là phiên bản mới nhất.

### 5.2. File `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-proceed-args</artifactId>
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
                    <mainClass>demo.App</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` cung cấp `ApplicationContext`/Java configuration, `spring-aop` chứa hạ tầng proxy và `AopUtils`; `aspectjweaver` là thư viện hỗ trợ parsing các `@AspectJ` pointcut expressions của Spring AOP. Không có `-javaagent` hoặc weaving trong project này.

### 5.3. File `src/main/java/demo/OrderService.java`

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

public class OrderService {
    private final AtomicInteger targetCalls = new AtomicInteger();

    public String passthrough(String input) {
        targetCalls.incrementAndGet();
        System.out.println("TARGET_PASS: [" + input + "]");
        return "pass:" + input;
    }

    public String submit(String customer, int quantity) {
        targetCalls.incrementAndGet();
        System.out.println(
                "TARGET_SUBMIT: " + customer + " x" + quantity);
        return "order:" + customer + ":" + quantity;
    }

    public String inspect(String label, int units) {
        targetCalls.incrementAndGet();
        System.out.println(
                "TARGET_INSPECT: " + label + " x" + units);
        return "inspect:" + label + ":" + units;
    }

    public String fail(String customer) {
        targetCalls.incrementAndGet();
        System.out.println("TARGET_FAIL: " + customer);
        throw new IllegalStateException("declined:" + customer);
    }

    public int getTargetCalls() {
        return targetCalls.get();
    }
}
~~~

Các method này là `public`, class không `final` và không tự gọi lẫn nhau, nhằm giữ ví dụ tập trung vào việc **thay đổi argument list trong Spring AOP**. `targetCalls` ghi nhận số lần **thực sự vào target**; ngay cả method `fail` cũng tăng counter trước khi ném exception.

Mỗi method trả `String` (hoặc ném exception trước khi trả), giúp `@Around` xử lý kết quả kiểu `Object` mà vẫn trả về một `String` hợp lệ cho caller.

### 5.4. File `src/main/java/demo/RewriteAspect.java`

~~~java
package demo;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class RewriteAspect {
    private final AtomicInteger adviceCalls = new AtomicInteger();

    @Around("execution(String demo.OrderService.passthrough(..))")
    public Object aroundPass(ProceedingJoinPoint pjp) throws Throwable {
        adviceCalls.incrementAndGet();
        System.out.println("AROUND_PASS: original arguments");
        return pjp.proceed();
    }

    @Around("execution(String demo.OrderService.submit(..))")
    public Object aroundSubmit(ProceedingJoinPoint pjp) throws Throwable {
        adviceCalls.incrementAndGet();

        Object[] original = pjp.getArgs();
        Object[] changed = original.clone();

        String customer = (String) original[0];
        int quantity = (Integer) original[1];

        changed[0] = customer.trim().toUpperCase(Locale.ROOT);
        changed[1] = quantity + 1;

        System.out.println("AROUND_SUBMIT: modified 2 args");
        return pjp.proceed(changed);
    }

    @Around(
        value = "execution(String demo.OrderService.inspect(..))"
              + " && args(label,..)",
        argNames = "label"
    )
    public Object aroundInspect(
            ProceedingJoinPoint pjp, String label) throws Throwable {
        adviceCalls.incrementAndGet();

        Object[] changed = pjp.getArgs().clone();

        changed[0] = label.trim().toUpperCase(Locale.ROOT);
        changed[1] = ((Integer) changed[1]) + 2;

        System.out.println("AROUND_INSPECT: 1 binding, 2 args");
        return pjp.proceed(changed);
    }

    @Around("execution(String demo.OrderService.fail(..))")
    public Object aroundFail(ProceedingJoinPoint pjp) throws Throwable {
        adviceCalls.incrementAndGet();

        String customer = (String) pjp.getArgs()[0];
        Object[] changed = {
            customer.trim().toUpperCase(Locale.ROOT)
        };

        System.out.println("AROUND_FAIL: modified customer");
        return pjp.proceed(changed);
    }

    public int getAdviceCalls() {
        return adviceCalls.get();
    }
}
~~~

Giải thích từng advice:

- `aroundPass`: chỉ chuyển tiếp, không thay đầu vào. Nó dùng `proceed()` vì không cần mảng đối số mới.
- `aroundSubmit`: `getArgs()` cho hai phần tử, copy mảng, xử lý `String` (trim + uppercase) và tăng `int` từ `2` lên `3`. `proceed(changed)` truyền đủ hai đối số xuống target.
- `aroundInspect`: expression có `args(label,..)` chỉ **bind phần tử đầu** vào advice parameter `String label`. `argNames="label"` chỉ giúp Spring nhận diện tên binding trong advice signature. Method đích vẫn có `(String,int)`, nên `proceed(changed)` phải chứa **hai** phần tử. `argNames` **không** đổi quy ước độ dài mảng của Spring.
- `aroundFail`: thay input rồi gọi target. Exception từ target **đi xuyên qua advice** tới caller, vì advice không bắt/nuốt/đổi exception. Đây là cách giữ hợp đồng lỗi của business method.
- Mọi advice trả `Object` và `return pjp.proceed(...)` để giữ kết quả; không tạo `null` ngoài dự kiến.
- Các pointcut được giới hạn theo **tên method cụ thể** và không trùng nhau, giúp mỗi invocation tương ứng đúng một advice trong ví dụ.

**Đừng bỏ qua `throws Throwable`** khi gọi `ProceedingJoinPoint.proceed(...)`. Nó được khai báo trên API và đảm bảo các exception có thể được propagate qua advice. Trong ứng dụng thật, không nuốt exception chỉ vì method advice là tầng cross-cutting.

### 5.5. File `src/main/java/demo/App.java`

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class App {
    @Bean
    public OrderService orderService() {
        return new OrderService();
    }

    @Bean
    public RewriteAspect rewriteAspect() {
        return new RewriteAspect();
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            OrderService service = context.getBean(OrderService.class);
            RewriteAspect aspect = context.getBean(RewriteAspect.class);

            boolean proxied = AopUtils.isAopProxy(service);
            System.out.println("PROXY: " + proxied);

            String pass = service.passthrough("keep");
            System.out.println("RESULT_PASS: " + pass);

            String submit = service.submit(" alice ", 2);
            System.out.println("RESULT_SUBMIT: " + submit);

            String inspect = service.inspect(" bob ", 4);
            System.out.println("RESULT_INSPECT: " + inspect);

            boolean failed = false;
            try {
                service.fail(" bad ");
            } catch (IllegalStateException ex) {
                failed = "declined:BAD".equals(ex.getMessage());
                System.out.println("CAUGHT: " + ex.getMessage());
            }

            int advice = aspect.getAdviceCalls();
            int target = service.getTargetCalls();
            System.out.println(
                    "COUNTS: advice=" + advice + ",target=" + target);

            if (!proxied
                    || !"pass:keep".equals(pass)
                    || !"order:ALICE:3".equals(submit)
                    || !"inspect:BOB:6".equals(inspect)
                    || !failed || advice != 4 || target != 4) {
                throw new IllegalStateException(
                    "Spring AOP proceed arguments verification failed");
            }

            System.out.println("CHECK: PASS");
        }
    }
}
~~~

Tại sao ví dụ hoạt động theo đúng thiết kế Spring?

1. `@EnableAspectJAutoProxy(proxyTargetClass = true)` bật Spring AOP và tạo class-based proxy khi `OrderService` có advisor phù hợp. `OrderService` không implement interface nên cách lấy bằng `getBean(OrderService.class)` thuận tiện.
2. `@Bean rewriteAspect()` biến `RewriteAspect` thành Spring bean, để Spring nhận diện bốn `@Around` advisors.
3. Caller chỉ sử dụng **reference lấy từ ApplicationContext**, do đó method được gọi qua proxy, nơi advice có thể thay argument list.
4. `service.getTargetCalls()` là method không khớp bốn pointcut, nhưng vẫn có thể được gọi qua proxy; nó trả giá trị bộ đếm target mà không làm tăng advice counter.
5. Lời gọi `fail(" bad ")` vẫn truyền exception sau khi sửa argument thành `"BAD"`. `failed` xác nhận cả **loại lỗi** và message đúng nội dung.
6. Assertion tổng hợp buộc kết quả match và **chính xác bốn lần vào target**. Nếu `proceed` bị bỏ quên trong một advice hoặc giá trị mảng sai, chương trình không in `CHECK: PASS`.

### 5.6. Chạy Maven và stdout

Tại thư mục `spring-aop-proceed-args`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do mã ví dụ chủ động in ra, theo thứ tự:

~~~text
PROXY: true
AROUND_PASS: original arguments
TARGET_PASS: [keep]
RESULT_PASS: pass:keep
AROUND_SUBMIT: modified 2 args
TARGET_SUBMIT: ALICE x3
RESULT_SUBMIT: order:ALICE:3
AROUND_INSPECT: 1 binding, 2 args
TARGET_INSPECT: BOB x6
RESULT_INSPECT: inspect:BOB:6
AROUND_FAIL: modified customer
TARGET_FAIL: BAD
CAUGHT: declined:BAD
COUNTS: advice=4,target=4
CHECK: PASS
~~~

Maven hoặc logger framework có thể phát thêm thông báo tùy cấu hình môi trường; đoạn trên chỉ liệt kê `System.out.println` từ ứng dụng.

Bốn phép đối chứng:

- `passthrough("keep")`: **`proceed()`**, target thấy `"keep"` không đổi.
- `submit(" alice ", 2)`: **`proceed(Object[])`**, target nhận `("ALICE", 3)` chứ không phải `(" alice ", 2)`.
- `inspect(" bob ", 4)`: advice **chỉ bind label** nhưng vẫn cần mảng target đầy đủ `("BOB", 6)`; target trả `inspect:BOB:6`.
- `fail(" bad ")`: target nhận `"BAD"` trước khi ném exception, caller vẫn bắt đúng `declined:BAD`.

Kết quả `CHECK: PASS` chỉ xuất hiện khi proxy tồn tại, các kết quả và exception hợp lệ, và số lượt advice/target đều bằng bốn. Nếu dùng `proceed()` trong `aroundSubmit` thay vì `proceed(changed)`, target sẽ nhận các đối số không sửa và assertion thất bại.

## 6. Những lỗi thường gặp với `proceed(Object[])`

### 6.1. Mảng ít hoặc nhiều phần tử hơn số argument của method đích

Với method `submit(String customer, int quantity)`:

~~~java
return pjp.proceed(new Object[] { "ALICE" });
~~~

**Sai** trong Spring AOP: mảng chỉ có một phần tử nhưng method đích nhận hai tham số.

~~~java
return pjp.proceed(new Object[] { "ALICE", 3, "extra" });
~~~

**Sai** vì dư phần tử. Trong triển khai Spring AOP, việc chuyển tiếp với độ dài argument list không tương ứng có thể dẫn đến `IllegalArgumentException`/lỗi xử lý invocation từ framework. Đừng phụ thuộc exact exception class/message cho mọi biến thể, nhưng **không thử chạy target với mảng sai arity** trong logic production.

Đúng:

~~~java
return pjp.proceed(new Object[] { "ALICE", 3 });
~~~

Các snippet trong phần này là **những biểu thức minh họa bên trong `@Around` advice**, không thêm chúng đồng thời vào project chính.

### 6.2. Mảng đúng độ dài nhưng đảo thứ tự hoặc kiểu

~~~java
return pjp.proceed(new Object[] { 3, "ALICE" });
~~~

Mảng có hai phần tử nhưng method đích yêu cầu `(String,int)`. Cách này sai về **vị trí/kiểu** và có thể gây lỗi khi framework gọi target bằng reflection/proxy, ví dụ lỗi argument type mismatch.

Lưu ý Java primitive khi dùng `Object[]`:

- `int` được biểu diễn qua wrapper `Integer`.
- `long` thường dùng `Long`, `boolean` dùng `Boolean`.
- Không kỳ vọng chuyển đổi mọi wrapper số tùy ý tự động thành primitive mong muốn; tốt nhất dùng đúng wrapper tương ứng.
- `null` không phải giá trị hợp lệ cho tham số primitive như `int`; nhưng `null` có thể dùng cho tham số reference nếu business contract cho phép.
- Cần kiểm tra downstream validators/contracts: một String được trim/uppercase vẫn có thể làm thay đổi ý nghĩa dữ liệu, định danh, chữ ký bảo mật hoặc quy tắc nghiệp vụ.

Nếu advice không biết chính xác chữ ký method mà chỉ áp dụng wildcard rộng, đừng tự suy rằng phần tử thứ hai của mọi invocation đều là `Integer`. Giới hạn pointcut hoặc kiểm tra method signature/arguments trước khi biến đổi.

### 6.3. Nhầm `pjp.getArgs()` với `Object[]` truyền cho proceed

~~~java
Object[] args = pjp.getArgs();
args[0] = "CHANGED";

return pjp.proceed();
~~~

Đây **không phải** cách an toàn/di động để bảo đảm target nhận `"CHANGED"`. `proceed()` tiếp tục với argument list hiện có trong join point; hành vi khi sửa trực tiếp array trả về từ `getArgs()` không nên là hợp đồng bạn dựa vào giữa các adapter/version. Nếu muốn thể hiện một phép thay đổi rõ ràng theo API Spring AOP, **tạo mảng thay thế và gọi `proceed(newArgs)`**.

Dạng rõ ý định:

~~~java
Object[] original = pjp.getArgs();
Object[] changed = original.clone();
changed[0] = "CHANGED";
return pjp.proceed(changed);
~~~

Nếu Spring/AspectJ ở một phiên bản có hành vi khác khi sửa `getArgs()` array tại chỗ, dạng clone + `proceed(changed)` vẫn là cách biểu đạt trực tiếp việc thay argument list, có thể code review và kiểm thử.

### 6.4. Bỏ quên `proceed`

~~~java
@Around("execution(* demo.OrderService.submit(..))")
public Object broken(ProceedingJoinPoint pjp) {
    return "fake-result";
}
~~~

Đoạn này **hợp lệ về mặt khả năng của `@Around`**, nhưng nếu yêu cầu là chuyển tiếp rồi sửa input thì đây là lỗi logic: target không chạy. `@Around` có quyền short-circuit, do đó chỉ được bỏ `proceed` nếu đó là chính sách được thiết kế rõ ràng (ví dụ cache hit, authorization failure có fallback hợp lệ).

### 6.5. Gọi `proceed` hai lần không kiểm soát

~~~java
Object first = pjp.proceed(args1);
Object second = pjp.proceed(args2);
return second;
~~~

Một `@Around` có thể gọi target nhiều lần, nhưng các thao tác ghi dữ liệu, thanh toán, gửi email có thể bị lặp. Không dùng `proceed(Object[])` để tạo retry tùy tiện. Nếu có yêu cầu retry, phải kiểm soát idempotency, số lần tối đa, exception, transaction và argument list cho từng lần thử. Nhiều hệ thống nên dùng integration chuyên biệt cho retry thay vì một aspect tùy biến.

### 6.6. Nuốt exception hoặc trả sai result type

~~~java
try {
    return pjp.proceed(changed);
} catch (Throwable error) {
    return null;
}
~~~

Đoạn này có thể làm mất exception gốc và thay nó bằng giá trị `null`, phá contract của method đích. Nếu chỉ sửa arguments, để exception propagate tự nhiên bằng `return pjp.proceed(changed);`. Nếu buộc phải xử lý exception/fallback, cần định nghĩa đó là requirement độc lập, bảo đảm kiểu return tương thích và kiểm thử.

`@Around` nên khai báo return type `Object`. Nếu advice khai báo `void`, Spring không có result value để trả lại giống method có return value; đây là nguyên nhân thường gặp của lỗi kết quả bất ngờ khi copy-paste advice.

## 7. Các khía cạnh khác khi truyền `Object[]` trong Spring AOP

### 7.1. Varargs method

Với một method Java:

~~~java
public String send(String topic, String... tags) {
    return topic + ":" + java.util.Arrays.toString(tags);
}
~~~

Varargs được biểu diễn bằng **một tham số mảng** (`String[]`) ở bytecode. Khi Spring advice một method như vậy, danh sách method parameters về mặt reflection là `(String, String[])`, không phải một tham số mới cho mỗi tag.

Trong quy ước method signature, mảng thay thế nên có hai phần tử:

~~~java
return pjp.proceed(
        new Object[] { "NEWS", new String[] { "java", "spring" } }
);
~~~

Đây là code minh họa bên trong advice; class `send` không có trong project runnable. Không trải phẳng `String... tags` thành ba hay bốn phần tử `Object[]` tùy số tag; điều đó không đúng số tham số method đích ở JVM.

### 7.2. Overloaded methods

Nếu class có:

~~~java
public void notify(String value) {
}

public void notify(String value, int retries) {
}
~~~

một pointcut quá rộng như `execution(* *.notify(..))` có thể chọn **cả hai overloads**. Advice giả định `args.length == 2` sẽ lỗi với overload một tham số. Cần:

- Giới hạn pointcut theo signature cụ thể khi có thể;
- Hoặc kiểm tra `pjp.getArgs().length` và `pjp.getSignature()` trước khi tạo mảng;
- Hoặc để nguyên lời gọi bằng `proceed()` nếu invocation không nằm trong phạm vi biến đổi được thiết kế.

Điều này quan trọng vì `proceed(Object[])` không tự suy kiểu tham số từ số phần tử mong muốn; **method đích đã được chọn** trước khi mảng thay thế đi vào.

### 7.3. Generic/bridge method và interface proxy

Ở Spring AOP, caller có thể đi qua JDK dynamic proxy hoặc class-based CGLIB proxy. Method signature mà framework xử lý có thể liên quan tới interface, implementation, generic bridge hoặc target method. Với method có nhiều overload hoặc generic erasure, test thực sự trên proxy configuration ứng dụng, không chỉ tính toán dựa trên source code một class.

Nếu business method chỉ được expose qua interface, đảm bảo lời gọi tới method đó thực sự đi qua Spring AOP proxy. Đừng tin rằng `proceed(Object[])` có thể sửa arguments cho object được gọi trực tiếp bằng `new`, self-invocation `this.method()`, `final`/`private` method không được class proxy intercept.

### 7.4. Null, immutable và mutable objects

`proceed(Object[])` **thay giá trị reference được chuyển xuống target**; nó không tự deep-copy các object. Ví dụ:

~~~java
Object[] before = pjp.getArgs();
Object[] next = before.clone();

next[0] = java.util.List.copyOf(
        (java.util.List<?>) before[0]
);

return pjp.proceed(next);
~~~

Nếu tham số đầu target là `List`, đoạn minh họa trên thay reference thành immutable copy (và `List.copyOf` không chấp nhận `null` element). Điều này có thể làm thay đổi hành vi target nếu target định sửa list; vì thế transformation phải có lý do rõ ràng. Dùng `Object[]` không có nghĩa mọi element đều tự động được serialize/clone.

Chọn cách copy và validate dựa trên hợp đồng business: một DTO chứa mutable nested object có thể cần defensive copy sâu hoặc tuyệt đối không được thay đổi.

### 7.5. Side effects và transaction/security context

Nếu Spring AOP proxy còn có advisor cho `@Transactional`, cache hoặc security, các advice nằm trong **interceptor chain** với thứ tự phụ thuộc precedence. Việc thay arguments bằng `proceed(Object[])` có thể ảnh hưởng logic được gọi phía sau (và có thể ảnh hưởng key/policy của một advisor ở trong chain), nhưng không nên tuyên bố mọi advisor luôn nhìn thấy cùng một argument list nếu không kiểm tra ordering.

Đặc biệt:

- Không tự thay đối số mang nghĩa identity/authentication chỉ để “chuẩn hóa” nếu hợp đồng bảo mật không cho phép.
- Cẩn thận với caching: thay arguments ở một vị trí trong chain có thể khiến cache key được xây từ đối số trước hoặc sau phép thay tùy ordering.
- Không gọi `proceed` nhiều lần trong transaction mà chưa kiểm soát semantics và idempotency.
- Nếu logic chỉ cần validation và ném exception trước khi target chạy, `@Before` có thể đủ; chỉ dùng `@Around` để thay arguments nếu thật sự cần.

## 8. Kiểm thử `proceed(Object[])` trong dự án thật

Không nên dừng ở test kiểm tra advice có chạy. Cần kiểm chứng **giá trị target đã nhận** và caller đã thấy gì.

Ma trận tối thiểu:

| Ca | Đối số caller truyền | Đối số target cần nhận | Kỳ vọng |
| --- | --- | --- | --- |
| Giữ nguyên | `passthrough("keep")` | `"keep"` | Không đổi vì `proceed()` |
| Thay `String` + primitive | `submit(" alice ",2)` | `("ALICE",3)` | Đúng cả hai vị trí |
| Chỉ bind một argument trong advice | `inspect(" bob ",4)` | `("BOB",6)` | Spring dùng đủ hai method arguments |
| Exception propagation | `fail(" bad ")` | `"BAD"` rồi throw | Caller nhận exception gốc |
| Sai arity | `submit("A",1)` nhưng advice truyền mảng độ dài 1 | Không có invocation hợp lệ | Test cần xác nhận reject, không chạy business target |
| Sai vị trí/kiểu | `submit("A",1)`, mảng `[1,"A"]` | Không tương thích `(String,int)` | Không được xem là behavior hợp lệ |
| Primitive nhận `null` | `submit("A",1)`, mảng `["A",null]` | `int` không nhận `null` | Phải validate trước khi proceed |
| Caller dùng `new OrderService()` | Bất kỳ | Tự gọi target, không proxy | Không tự chạy around advice |
| Self-invocation | `this.submit(...)` bên trong target | Lời gọi Java nội bộ | Không qua Spring proxy, dù có pointcut |

Để test âm mà không khiến code production có hành vi nguy hiểm, tạo một **test-only aspect** hoặc unit test chấp nhận exception theo kiểu đủ rộng và kiểm tra target counter không tăng. Cần nhìn root cause của phiên bản Spring đang dùng thay vì đóng đinh exact exception message.

Trong project phần 5, bộ đếm `adviceCalls` và `targetCalls`, giá trị trả về và message của exception cung cấp bốn bằng chứng riêng biệt. Bài thực hành không phụ thuộc log framework hay kết quả timing khó dự đoán.

### Một JUnit test tối giản bổ sung cho project phần 5

Nếu muốn biến kiểm tra đã có trong `App.main()` thành integration test tự động, thêm `junit-jupiter` test dependency cùng `maven-surefire-plugin` phiên bản hỗ trợ JUnit 5, rồi tạo:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProceedArgumentsTest {
    @Test
    void replacementArgumentsReachTargetThroughSpringProxy() {
        try (var context =
                new AnnotationConfigApplicationContext(App.class)) {
            OrderService service = context.getBean(OrderService.class);
            RewriteAspect aspect = context.getBean(RewriteAspect.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals(
                "order:ALICE:3",
                service.submit(" alice ", 2));
            assertEquals(
                "inspect:BOB:6",
                service.inspect(" bob ", 4));
            assertEquals(2, aspect.getAdviceCalls());
            assertEquals(2, service.getTargetCalls());
        }
    }
}
~~~

Đây là **file `src/test/java/demo/ProceedArgumentsTest.java` hoàn chỉnh**, nhưng cần các dependency JUnit/Surefire bổ sung như đã nêu, nên **chưa chạy được chỉ với `pom.xml` tối thiểu** của phần 5. Các giá trị assertion xác minh hai cách sửa arguments khác nhau trong cùng một Spring ApplicationContext.

Cần bảo đảm test thực hiện lời gọi qua `context.getBean(...)`, không phải tự `new OrderService()`, vì bài cần kiểm chứng semantics **Spring AOP**, không chỉ test một Java helper xử lý `Object[]`.

## 9. Tổ chức việc bind tham số cho advice có khả năng di chuyển

Trong Spring AOP, đoạn này hợp lệ cho `inspect(String,int)`:

~~~java
@Around(
    value = "execution(* demo.OrderService.inspect(..)) && args(label,..)",
    argNames = "label"
)
public Object around(ProceedingJoinPoint pjp, String label)
        throws Throwable {
    Object[] all = pjp.getArgs().clone();
    all[0] = label.trim();
    return pjp.proceed(all);
}
~~~

Nhưng vì advice chỉ bind **một** tham số của target, nó không phải lựa chọn mạnh nhất nếu muốn dùng cùng source `@AspectJ` aspect qua **cả Spring AOP và AspectJ compiler/weaver**.

Nếu mục tiêu là một pattern tương thích hơn, bind đầy đủ theo method:

~~~java
@Around(
    value = "execution(* demo.OrderService.inspect(String,int))"
          + " && args(label,units)",
    argNames = "label,units"
)
public Object around(
        ProceedingJoinPoint pjp, String label, int units)
        throws Throwable {
    return pjp.proceed(new Object[] {
        label.trim(),
        units + 2
    });
}
~~~

Ở dạng này, advice có hai binding variables cùng thứ tự tương ứng với hai tham số method. Việc `argNames` kê tên đầy đủ giúp Spring phân giải binding chính xác; Java `-parameters` compile flag có thể hỗ trợ khám phá tên nhưng không nên là giả định ngầm khi chia sẻ thư viện.

Đây là **hai cách lựa chọn để viết lại `aroundInspect`** trong class aspect; không đặt cả hai cùng lúc. Pattern thứ hai theo sát khuyến nghị của tài liệu Spring cho việc giảm khác biệt `proceed(Object[])` giữa Spring proxy AOP và AspectJ compiler/weaver. Bài này vẫn chỉ thực thi bằng **Spring AOP**; lưu ý tương thích nhằm tránh lỗi khi tái sử dụng advice.

## 10. Chẩn đoán lỗi tại runtime

Khi thấy target nhận đối số sai, exception lạ hoặc advice không chạy:

1. **Xác nhận pointcut:** expression `execution(...)` match method thực sự trong Spring bean không? Nếu không match, mọi logic `proceed` đều không được chạy.
2. **Xác nhận Spring proxy:** `AopUtils.isAopProxy(service)` trên reference thực sự caller dùng. Object tạo ngoài container và self-invocation không tự được advice.
3. **In số lượng đối số:** kiểm tra `pjp.getArgs().length` và đối chiếu **số tham số của method target**, không đếm số argument được bind vào advice.
4. **Xem từng vị trí và kiểu:** nếu method nhận `(String,int)`, mảng phải có `String` ở index `0` và `Integer` ở index `1`.
5. **Sao chép mảng trước khi sửa:** tránh phụ thuộc side effect từ việc chỉnh `getArgs()` trực tiếp.
6. **Không thay argument mà không cần:** nếu chỉ logging/metrics, giữ `proceed()` hoặc cân nhắc advice hẹp hơn `@Before`.
7. **Kiểm tra return value:** quanh method trả `String`, advice nên trả đúng kết quả `Object` mà `proceed` tạo ra, hoặc fallback có kiểu phù hợp nếu requirement cho phép.
8. **Kiểm tra exception propagation:** advice có catch rồi nuốt lỗi không? Nếu có, đây là thay đổi business contract cần phê duyệt, không phải tác động mặc định của `proceed(Object[])`.
9. **Kiểm tra overload, varargs, generic bridge:** method được chọn có đúng chữ ký mong đợi không? `String...` là `String[]` tại ranh giới JVM.
10. **Xem ordering:** nếu nhiều `@Around`/advisors cùng khớp, lời gọi `proceed` chuyển tiếp vào phần **còn lại của interceptor chain**, không nhất thiết vào target ngay. Test với đúng Spring context và thứ tự advisors khi arguments liên quan cache/transaction/security.
11. **Xác nhận raw vs transformed:** ghi marker trong target hoặc dùng spy/test counter. Chỉ ghi marker trước `proceed` không chứng minh target nhận được mảng mới.
12. **Đọc root cause thay vì đoán exception message:** độ dài/kiểu sai gây invocation failure, nhưng wrapper và nội dung lỗi có thể thay đổi theo phiên bản/framework integration.

Đo nhanh tại call site với một Spring context đã có `OrderService` bean:

~~~java
OrderService service = context.getBean(OrderService.class);
System.out.println(
    org.springframework.aop.support.AopUtils.isAopProxy(service)
);
~~~

Đây là đoạn chèn vào phương thức có biến `context`, không phải file Java độc lập. Nếu trả `false`, hãy kiểm tra bean/aspect/pointcut trước khi phân tích array replacement.

## 11. Câu hỏi tự kiểm tra

**1. Với method `transfer(String from, String to, int amount)` và advice chỉ bind `amount`, mảng `proceed(Object[])` của Spring AOP cần mấy phần tử?**  
**Ba** phần tử, tương ứng `from`, `to`, `amount`. Không dùng mảng một phần tử chỉ vì advice có một bound parameter.

**2. Khi nào nên gọi `proceed()` thay vì `proceed(Object[])`?**  
Khi không cần thay arguments và chỉ muốn method chạy với danh sách đối số hiện có.

**3. Nếu lời gọi `submit(" alice ",2)` đổi thành `proceed(new Object[]{"ALICE",3})`, caller có tự thấy biến String đầu vào chuyển thành `"ALICE"`?**  
Không. Advice chuyển danh sách giá trị/reference mới cho invocation xuống target; nó không tự đổi các biến local của caller.

**4. Có thể truyền `new Object[]{"ALICE",null}` tới method `submit(String,int)` không?**  
Không hợp lệ cho primitive `int`. Phải truyền wrapper số tương thích và theo đúng contract.

**5. Vì sao dùng `args(label,..)` chỉ bind một tham số mà advice vẫn có thể sửa cả hai đối số method?**  
Vì `pjp.getArgs()` cho danh sách arguments của method join point; `proceed(Object[])` nhận danh sách method arguments đầy đủ theo quy ước Spring AOP.

**6. Có nên sửa trực tiếp `pjp.getArgs()[0]` rồi gọi `proceed()`?**  
Không nên dựa vào side effect của array trả về; thể hiện rõ thay đổi bằng `clone()` và `proceed(changed)`.

**7. `@Around` có được bỏ `proceed`?**  
Được về mặt khả năng, nhưng target sẽ không chạy nếu advice short-circuit. Chỉ làm khi requirement thật sự yêu cầu và return/exception policy được xác định.

**8. Dùng `proceed(Object[])` có tự khiến method `final` trên target được intercept không?**  
Không. Nó chỉ hoạt động **bên trong around advice đã được gọi qua proxy**, không vượt giới hạn method interception của Spring.

**9. Varargs `send(String topic, String... tags)` yêu cầu số phần tử thế nào?**  
Theo signature JVM là **hai** tham số: `String` và `String[]`, không trải số tag thành các phần tử riêng của `Object[]`.

**10. Khác biệt lớn nhất giữa Spring AOP và advice được AspectJ compiler biên dịch là gì?**  
Spring `proceed(Object[])` dùng **arguments của underlying method** theo thứ tự; AspectJ compiler traditional around advice định vị arguments theo **các binding của advice**. Muốn code tương thích, bind đầy đủ method arguments theo thứ tự và tạo mảng đúng cấu trúc.

## 12. Checklist thực hành

- [ ] Spring bean được lấy từ `ApplicationContext` và proxy đã có advisor match.
- [ ] `@Around` nhận `ProceedingJoinPoint` ở tham số đầu, method advice khai báo `throws Throwable` nếu chuyển tiếp exception.
- [ ] Nếu không đổi arguments, ưu tiên `proceed()`; nếu chỉ log trước, cân nhắc `@Before`.
- [ ] Nếu đổi arguments, dùng `proceed(Object[])` và truyền **đủ số lượng/thứ tự của method target**.
- [ ] Các giá trị trong mảng có kiểu phù hợp, kể cả primitive wrappers và `null`.
- [ ] Nếu bind một phần qua `args(...)`, không nhầm số binding của advice với số arguments method.
- [ ] Không phụ thuộc mutation trực tiếp vào `pjp.getArgs()`; tạo mảng thay thế tường minh.
- [ ] Trả kết quả của `proceed` khi không chủ đích transform; không nuốt exception ngoài yêu cầu.
- [ ] Không gọi `proceed` nhiều lần khi side effects chưa được kiểm soát.
- [ ] Đã test return value, target-observed arguments, exception và proxy boundary.
- [ ] Khi tái sử dụng code qua Spring AOP và AspectJ weaving, áp dụng quy ước bind đầy đủ arguments theo thứ tự theo khuyến nghị Spring.

## 13. Tài liệu chính thức Spring Framework

- [Spring Framework 6.2 — Declaring Advice, Around Advice và Proceeding with Arguments](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): giải thích trực tiếp `proceed()`, `proceed(Object[])`, sự khác biệt với AspectJ compiler và pattern bind arguments để tương thích.
- [Spring Framework 6.2 — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html): `execution(...)`, `args(...)`, Spring AOP method-execution semantics.
- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB proxy và giới hạn self-invocation.
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): đăng ký aspect và auto-proxying trong Spring context.
- [ProceedingJoinPoint JavaDoc (AspectJ)](https://www.javadoc.io/doc/org.aspectj/aspectjrt/1.9.22/org/aspectj/lang/ProceedingJoinPoint.html): API `proceed()`/`proceed(Object[])` mà Spring sử dụng qua `@Around`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-006">Giữ đúng giá trị trả về và kiểu dữ liệu của phương thức đích</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP `@Around`: giữ đúng giá trị trả về và kiểu dữ liệu của phương thức đích

## 1. Vấn đề: method chạy đúng nhưng caller nhận sai kết quả

Trong **Spring AOP dựa trên proxy**, `@Around` là advice có khả năng đứng bao quanh lời gọi một method của Spring-managed bean. Advice quyết định có gọi `ProceedingJoinPoint.proceed()` hay không, và **giá trị mà advice trả về chính là giá trị cuối cùng caller nhìn thấy**, miễn giá trị đó tương thích với method contract.

Một lỗi dễ mắc:

```java
@Around("execution(* demo.returns.PricingService.label(..))")
public Object trace(ProceedingJoinPoint pjp) throws Throwable {
    Object result = pjp.proceed();
    System.out.println("result = " + result);
    return null; // SAI: làm mất giá trị mà method đích vừa trả về.
}
```

Target có thể đã trả `"LABEL-A1"`, nhưng caller lại nhận `null`. Chỉ một chữ `return null` trong advice đủ đổi ý nghĩa API, dù code của target không thay đổi.

Cách mặc định an toàn khi chỉ cần đo thời gian, logging hoặc telemetry:

```java
@Around("execution(* demo.returns.PricingService.label(..))")
public Object trace(ProceedingJoinPoint pjp) throws Throwable {
    Object result = pjp.proceed();
    System.out.println("result = " + result);
    return result; // Giữ nguyên kết quả method đích.
}
```

Hai đoạn trên là **fragment** để minh họa, không phải file chạy độc lập. Trong ví dụ thực tế cần có `@Aspect`, aspect là Spring bean, hạ tầng AOP và bean target được quản lý bởi Spring.

**Quy tắc quan trọng nhất:** với một `@Around` chỉ quan sát, hãy `return pjp.proceed()` hoặc giữ kết quả trong biến rồi `return result`. Đừng cố ý sửa kết quả nếu bài toán không đòi hỏi thay đổi contract.

Bài chỉ tập trung vào **Spring Aspect/Spring AOP proxy** và những ranh giới khi giữ đúng kiểu/kết quả method. Đây không phải phần giới thiệu AOP nói chung hay AspectJ bytecode weaving.

## 2. Giá trị trả về đi theo đường nào?

```text
Caller
  |
  | PricingOperations.priceInCents(3)
  v
Spring AOP proxy (JDK hoặc CGLIB)
  |
  | pointcut match
  v
@Around advice
  |
  | Object value = pjp.proceed()
  v
Các interceptor còn lại / target.priceInCents(3)
  |
  | int 450
  v
proceed() trả Integer(450) do boxing trong API Object
  |
  | advice return chính giá trị đó
  v
Spring AOP proxy kiểm tra/trao lại kiểu theo method contract
  |
  v
Caller thấy int 450
```

Một số điểm cần hiểu chính xác:

1. `ProceedingJoinPoint.proceed()` có kiểu trả về Java là `Object` để hỗ trợ nhiều loại target methods.
2. Kiểu trả về **ở chữ ký method đích** vẫn là contract mà caller sử dụng, ví dụ `String`, `int`, `void`, `Optional<String>` hoặc `List<String>`.
3. Nếu target trả primitive như `int`, giá trị được biểu diễn dưới dạng wrapper `Integer` trong kết quả `Object`. Proxy cần đưa giá trị tương thích trở lại caller.
4. Với `void`, `proceed()` thông thường trả `null`; caller không sử dụng return value. Nhưng điều đó **không** có nghĩa nên trả `null` cho **mọi** method.
5. Trả một object không đúng kiểu có thể lỗi ở runtime; phương thức target chạy xong không bảo đảm caller nhận được giá trị đúng.
6. Nếu target ném exception, `proceed()` ném exception ấy và không trả value. Advice phải truyền tiếp hoặc xử lý exception có chủ đích; không được vô tình nuốt lỗi bằng một `finally return`.

### Tại sao Spring khuyến nghị advice method trả `Object`?

Tài liệu Spring Framework về `@Around` ghi rõ phương thức advice nên trả `Object` và tham số đầu tiên phải là `ProceedingJoinPoint`. Nếu **khai báo chính method advice có return type `void`**, Spring sẽ đưa **`null`** về caller, bỏ qua kết quả thực của `proceed()`. Do đó `void` cho advice là cái bẫy nghiêm trọng khi pointcut áp dụng cho các method không-void.

**Đúng:**

```java
@Around("execution(* demo.returns..*Service.*(..))")
public Object preserveResult(ProceedingJoinPoint pjp) throws Throwable {
    return pjp.proceed();
}
```

**Sai khi áp dụng cho non-void target:**

```java
@Around("execution(* demo.returns..*Service.*(..))")
public void discardResult(ProceedingJoinPoint pjp) throws Throwable {
    pjp.proceed(); // Method target chạy nhưng kết quả không được chuyển về caller.
}
```

Một `@Around` trên method có return type `String`, `int`, `List`... mà advice tự khai báo `void` có thể khiến caller nhận `null` hoặc phát sinh lỗi tương thích kiểu. Hãy khai báo `Object` và trả về kết quả theo contract.

## 3. Ma trận các kiểu trả về quan trọng

| Chữ ký target | `proceed()` trả qua `Object` | Giá trị advice nên trả khi không biến đổi | Nếu advice trả sai |
| --- | --- | --- | --- |
| `String label()` | `String` hoặc `null` | Đúng `String` gốc | Trả `Integer`: sai kiểu; `null` có thể vi phạm nghiệp vụ |
| `int total()` | `Integer` boxed | Chính `Integer` từ proceed | `null` không thể unbox thành `int`; `"42"` không là `int` |
| `long amount()` | `Long` boxed | `Long` | `Integer` không tự thành `Long` chỉ vì số tương đương |
| `boolean allowed()` | `Boolean` boxed | `Boolean` | `null` hoặc `"true"` không phù hợp |
| `void notifyUser()` | Thông thường `null` | `null` từ `proceed()` | Không dựa vào một return value của void method |
| `Optional<String> find()` | `Optional<String>` | Cùng `Optional` | `null` khác `Optional.empty()`; `String` không đúng contract |
| `List<String> list()` | `List<String>` ở mức object | Cùng `List` | Trả `Set` sai API; list có phần tử sai generic gây lỗi về sau |
| `CompletableFuture<String> loadAsync()` | `CompletableFuture<String>` | Chính future | Trả `String` trực tiếp sai method contract |
| `Mono<String> stream()` nếu dùng Reactor | Publisher `Mono<String>` | Chính publisher | Trả payload `String` thay publisher không hợp lệ |

Trong bảng, tên hàm chỉ để diễn giải; các API asynchronous/reactive chỉ có khi dependencies tương ứng được dùng. Không phải mọi loại lỗi đều ném cùng một exception trong mọi proxy implementation: **ClassCastException**, exception unboxing hoặc **Spring `AopInvocationException`** có thể xuất hiện theo vị trí/type mismatch. Điều quan trọng là **kiểu trả về cuối cùng phải tương thích**.

### 3.1. Reference type và nullability

`String`, domain DTO và các reference types có thể trả `null` về mặt ngôn ngữ Java, nhưng API nghiệp vụ có thể cấm null. Trả `null` vì advice quên `return result` là **phá contract** ngay cả khi không ném exception tức thì.

Trường hợp target trả `Optional.empty()` thì advice cần giữ một **Optional rỗng**, không thay bằng `null`. Caller hợp lý có thể gọi `result.isPresent()` mà không kiểm tra null, vì contract `Optional` kỳ vọng object Optional.

### 3.2. Primitive và boxed types

```java
public int calculate() {
    return 450;
}
```

Bên trong advice:

```java
Object result = pjp.proceed(); // Thường là Integer(450).
return result;                  // Spring trả int 450 đến caller.
```

Đây là các fragment. Nếu đổi thành `return null;`, `int` không có giá trị null. Spring AOP có thể phát sinh exception do null return trên primitive method. Nếu đổi thành `return "450";`, chuỗi không phải Integer tương thích với primitive int; Java không tự parse chuỗi.

Tránh hardcode trả `0` thay giá trị target chỉ vì để caller không lỗi: nó có thể làm dữ liệu đúng bị thay bằng kết quả sai mà không phát tín hiệu.

### 3.3. `void` có đặc điểm riêng

```java
public void announce(String id) {
    System.out.println("announce " + id);
}
```

Advice vẫn nên viết một cách thống nhất:

```java
@Around("execution(* demo.returns.PricingService.announce(..))")
public Object preserveVoid(ProceedingJoinPoint pjp) throws Throwable {
    Object result = pjp.proceed(); // Đối với void: kết quả là null.
    return result;                 // Hợp lệ cho void method.
}
```

Một advice dạng `Object` có thể áp dụng cho cả method `void` lẫn non-void. **Không** viết `return "DONE"` chỉ vì muốn báo log thành công cho caller: void method không có giá trị trả về theo Java interface. Kết quả công việc cần phản ánh bằng side effect hoặc domain API được thiết kế rõ ràng.

## 4. Ví dụ lỗi phổ biến của `@Around`

### 4.1. Quên `return result`

```java
@Around("execution(* demo.returns..*Service.*(..))")
public Object broken(ProceedingJoinPoint pjp) throws Throwable {
    Object result = pjp.proceed();
    logResult(result);
    return null; // Sai với mọi non-void method cần giá trị.
}
```

Đây là fragment có `logResult` giả định. Lỗi không nhất thiết nằm ở target; phải xem stack trace và aspect chain để phát hiện advice đã **ghi đè** return.

### 4.2. Dùng `finally` để `return`

```java
@Around("execution(* demo.returns..*Service.*(..))")
public Object veryDangerous(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } finally {
        return null; // Sai: thay cả return đúng lẫn exception của target.
    }
}
```

Trong Java, `return` ở `finally` có thể **đè** return của try, đồng thời **nuốt exception**. Đây là lỗi đặc biệt nguy hiểm với transaction và security checks.

Dùng finally chỉ để cleanup/logging:

```java
@Around("execution(* demo.returns..*Service.*(..))")
public Object safeCleanup(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } finally {
        clearTemporaryContext();
    }
}
```

Ở đây `clearTemporaryContext()` là fragment giả định; nếu chính cleanup ném exception, nó vẫn có thể che lỗi/return trước đó. Code cleanup nên được thiết kế để không vô tình thay đổi kết quả nghiệp vụ.

### 4.3. Dùng sai phương thức advice

```java
@AfterReturning(
    pointcut = "execution(* demo.returns.PricingService.label(..))",
    returning = "value"
)
public String tryReplace(String value) {
    return "NEW-" + value;
}
```

Đây là fragment minh họa một ý tưởng **sai về semantics**: Spring `@AfterReturning` có thể bind value để quan sát, nhưng không dùng method return của advice này để thay hoàn toàn reference mà caller nhận. Nếu mục tiêu cần thay return, dùng `@Around` theo contract phù hợp. Trong thực hành, `@AfterReturning` nên là `void` nếu chỉ log/metric.

### 4.4. Ép kiểu một giá trị mà `proceed()` không bảo đảm

```java
Object result = pjp.proceed();
String text = (String) result; // Sai nếu pointcut cũng match int/List/void.
return text;
```

Với pointcut rộng, target có thể có return type khác String. Không cast cứng trừ khi expression và method signature bảo đảm. Mặc định tốt nhất là **return trực tiếp kết quả `proceed()`**.

### 4.5. Gọi `proceed()` hai lần ngoài ý muốn

```java
@Around("execution(* demo.returns..*Service.*(..))")
public Object duplicateCall(ProceedingJoinPoint pjp) throws Throwable {
    Object first = pjp.proceed();
    pjp.proceed(); // Gọi target lần hai nếu chain cho phép.
    return first;
}
```

Trong Spring AOP, `proceed()` có thể được gọi nhiều lần; nhưng với một method ghi dữ liệu, gửi email, thanh toán, lần thứ hai có thể tạo **side effect kép**. Một advice chỉ đo/log kết quả phải gọi `proceed()` **một lần**, không vô tình nhân đôi operation.

### 4.6. Dùng `return null` để “bỏ qua” execution

`@Around` có thể chủ động **không gọi `proceed()`**, ví dụ cache hit. Nhưng nếu lời gọi target được bỏ qua, advice phải trả **một giá trị tương thích với method**:

```java
@Around("execution(* demo.returns.PricingService.label(..))")
public Object cacheHit(ProceedingJoinPoint pjp) throws Throwable {
    String cached = lookupCache(pjp.getArgs()[0]); // Fragment, helper giả định.
    if (cached != null) {
        return cached; // Đúng kiểu String; cần đảm bảo đúng ngữ nghĩa.
    }
    return pjp.proceed();
}
```

Bỏ qua target không nhất thiết an toàn với `@Transactional`, logging, authorization hoặc side effects bên trong method. Cache chỉ phù hợp với operation có contract cho phép tái sử dụng kết quả; policy phải đảm bảo cache key, invalidation và dữ liệu nhạy cảm.

## 5. Chỉ thay đổi return khi có yêu cầu rõ ràng

Một `@Around` có quyền bọc hoặc thay thế return value. Nhưng đây là hành vi **có chủ đích**, không phải hiệu ứng phụ của tracing advice.

### 5.1. Thay string thành string

```java
@Around("execution(String demo.returns.PricingService.label(..))")
public Object uppercaseLabel(ProceedingJoinPoint pjp) throws Throwable {
    String result = (String) pjp.proceed();
    return result.toUpperCase(java.util.Locale.ROOT);
}
```

Đây là fragment mà pointcut giới hạn return type `String`. Nếu business contract cho phép đổi chuỗi, caller vẫn nhận `String` hợp lệ. Tuy nhiên, giữ **đúng kiểu** chưa chắc giữ **đúng giá trị/ngữ nghĩa**; viết test xác nhận đó là kết quả mong muốn.

### 5.2. Fallback phải đúng kiểu khi catch exception

```java
@Around("execution(* demo.returns.PricingService.maybeLookup(..))")
public Object fallback(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (IllegalStateException ex) {
        return java.util.Optional.empty();
    }
}
```

Nếu target có contract `Optional<String>`, fallback `Optional.empty()` hợp lệ. Trả `null` hoặc chuỗi `"NOT_FOUND"` phá API. Với method `int calculate()`, fallback hợp lệ về mặt type có thể là một `Integer` tương ứng với `int`, nhưng **chọn số fallback** phải có ý nghĩa nghiệp vụ; không lặng lẽ trả 0 nếu đó là kết quả sai.

### 5.3. Không làm mất cùng reference khi chỉ muốn quan sát

Nếu một service trả domain object mutable, advice `return result` giữ **cùng reference**. Trả một DTO được tạo lại hoặc một copy có thể gây khác biệt về identity, lazy relations, proxy entities hoặc state mà caller trông đợi, kể cả khi field values có vẻ tương đương.

Một logging advice **không nên** map result sang String hoặc JSON rồi vô tình trả String thay object nghiệp vụ. Muốn serialize để log thì serialize thành biến khác, vẫn `return result`.

### 5.4. Generic return types

Giả sử method trả `List<String>`. Ở runtime, Java generics có type erasure; một raw `List<Integer>` cũng có thể đi qua một số cast đối với `List`, rồi gây lỗi khi caller đọc phần tử dưới dạng String. Vì vậy, check chỉ `result instanceof List` chưa bảo đảm giữ **generic contract**. Lựa chọn an toàn cho cross-cutting advice là chuyển nguyên object từ `proceed()`, không thay collection contents.

Với `Optional<User>`, `ResponseEntity<DTO>`, `Page<T>` hoặc các wrapper có metadata, giữ wrapper và nội dung trừ khi policy cụ thể đòi hỏi transformation đúng type.

## 6. Ví dụ hoàn chỉnh: một `@Around` bảo toàn nhiều kiểu trả về

Dự án sau dùng **Java 17, Spring Framework 6.2.x, Maven** không cần Spring Boot. Một advice duy nhất match nhiều method khác kiểu và gọi `proceed()` đúng một lần, sau đó trả nguyên object. Ví dụ nhằm kiểm chứng:

- `String` không bị thành null.
- `int` vẫn là `int` đối với caller dù `proceed()` trả boxed `Integer`.
- Method `void` vẫn chạy side effect; `proceed()` cho kết quả null.
- `Optional.empty()` là object Optional, không bị thay bằng null.
- `List<String>` không bị map sang loại object khác.
- Proxy vẫn phải bảo đảm Spring quản lý target và aspect; consumer lấy bean qua context.

### 6.1. Cấu trúc dự án

```text
spring-around-return-values/
├── pom.xml
└── src/main/java/demo/returns/
    ├── AppConfig.java
    ├── PricingOperations.java
    ├── PricingService.java
    ├── ReturnTraceAspect.java
    └── Main.java
```

### 6.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-around-return-values</artifactId>
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
package demo.returns;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {
}
```

`@ComponentScan` tìm `@Service` và `@Aspect @Component` trong package. `@EnableAspectJAutoProxy` bật proxying. Target có interface và `proxyTargetClass=false` nên Spring Framework thuần dùng JDK interface proxy trong ví dụ.

### 6.4. `PricingOperations.java`

```java
package demo.returns;

import java.util.List;
import java.util.Optional;

public interface PricingOperations {
    String label(String id);
    int priceInCents(int quantity);
    void announce(String id);
    Optional<String> maybeLookup(String id);
    List<String> codes();
}
```

Các methods được công bố qua interface để caller giữ JDK proxy có thể gọi. Mỗi method trả một kiểu khác, đúng trọng tâm bảo toàn result của `@Around`.

### 6.5. `PricingService.java`

```java
package demo.returns;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PricingService implements PricingOperations {
    @Override
    public String label(String id) {
        System.out.println("[target] label " + id);
        return "LABEL-" + id;
    }

    @Override
    public int priceInCents(int quantity) {
        System.out.println("[target] priceInCents " + quantity);
        return quantity * 150;
    }

    @Override
    public void announce(String id) {
        System.out.println("[target] announce " + id);
    }

    @Override
    public Optional<String> maybeLookup(String id) {
        System.out.println("[target] maybeLookup " + id);
        return "NONE".equals(id)
                ? Optional.empty()
                : Optional.of("SKU-" + id);
    }

    @Override
    public List<String> codes() {
        System.out.println("[target] codes");
        return List.of("A", "B");
    }
}
```

Service không chứa advice logic và không tự tạo proxy. Spring đăng ký bean nhờ `@Service` và component scan.

### 6.6. `ReturnTraceAspect.java`

```java
package demo.returns;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ReturnTraceAspect {
    @Around("execution(* demo.returns.PricingService.*(..))")
    public Object preserve(ProceedingJoinPoint pjp) throws Throwable {
        String name = pjp.getSignature().getName();
        System.out.println("[around] enter " + name);

        Object result = pjp.proceed();

        String display = result == null ? "<null>" : result.toString();
        System.out.println("[around] exit " + name + " -> " + display);

        return result; // Chủ đích bảo toàn return value lẫn object identity.
    }
}
```

Khai báo `@Aspect` mô tả aspect, `@Component` đăng ký aspect thành Spring bean, `@Around` match các method trên `PricingService`. Advice dùng `Object` để không buộc result phải là String. Nó **không return `display`**, chỉ dùng `display` để in log. Với result null của void method, log `<null>` nhưng `return result` là hợp lệ.

Đây là demo quan sát kiểu, không phải logging recipe production: `result.toString()` trên object nhạy cảm/lazy-loaded/chi phí lớn có thể gây side effect hoặc rò dữ liệu. Với sản phẩm thật, log method name và metadata an toàn thay cho toàn bộ giá trị.

### 6.7. `Main.java`

```java
package demo.returns;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            PricingOperations port = context.getBean(PricingOperations.class);

            System.out.println("jdkProxy=" + AopUtils.isJdkDynamicProxy(port));
            System.out.println("[caller] label=" + port.label("A1"));
            System.out.println("[caller] price=" + port.priceInCents(3));
            port.announce("B1");
            System.out.println("[caller] lookup=" + port.maybeLookup("NONE"));
            System.out.println("[caller] codes=" + port.codes());
        }
    }
}
```

Từ thư mục có `pom.xml`, chạy:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.returns.Main
```

**Stdout xác định từ các lệnh `System.out` của ví dụ** (không kể Maven/Spring logs nếu có):

```text
jdkProxy=true
[around] enter label
[target] label A1
[around] exit label -> LABEL-A1
[caller] label=LABEL-A1
[around] enter priceInCents
[target] priceInCents 3
[around] exit priceInCents -> 450
[caller] price=450
[around] enter announce
[target] announce B1
[around] exit announce -> <null>
[around] enter maybeLookup
[target] maybeLookup NONE
[around] exit maybeLookup -> Optional.empty
[caller] lookup=Optional.empty
[around] enter codes
[target] codes
[around] exit codes -> [A, B]
[caller] codes=[A, B]
```

### 6.8. Vì sao output theo đúng thứ tự này?

1. Context lấy `PricingOperations` do Spring quản lý, aspect match service, JDK proxy phù hợp interface nên `jdkProxy=true`.
2. `port.label("A1")` đi qua advice: in `enter`, target trả `"LABEL-A1"`, advice in `exit` rồi **trả nguyên String**, caller in đúng giá trị.
3. `port.priceInCents(3)`: target trả primitive `int 450`; qua `Object` của `proceed()` được boxing thành `Integer(450)`, advice trả lại; caller có `int 450`.
4. `port.announce("B1")`: target là `void` nên `proceed()` có kết quả null; advice log `<null>` và caller không nhận business return.
5. `port.maybeLookup("NONE")`: target trả `Optional.empty()`; advice giữ nguyên Optional thay vì null.
6. `port.codes()`: target trả `List.of("A","B")`, advice giữ nguyên List; `toString()` của List tạo biểu diễn `[A, B]`.
7. Advice không gọi `proceed()` nhiều lần, không biến đổi giá trị, không tạo một error recovery trái với contract.

## 7. Kiểm tra return type khi cố ý transform dữ liệu

Nếu yêu cầu nghiệp vụ **cho phép** một `@Around` thay đổi value, hãy dựa trên return type thực tế của method đích. Từ `ProceedingJoinPoint.getSignature()` có thể lấy thông tin phương thức; Spring có helper để resolve method/target method trong trường hợp JDK proxy và bridge methods.

Một cách kiểm tra cơ bản dành cho method execution:

```java
MethodSignature signature = (MethodSignature) pjp.getSignature();
Class<?> returnType = signature.getReturnType();
System.out.println("Declared return type: " + returnType.getName());
```

Đây là fragment trong `@Around` và cần import `org.aspectj.lang.reflect.MethodSignature`. Nó giúp đọc kiểu return **được khai báo**, nhưng không phải validation đầy đủ của generics, nullability, custom serialization, transaction state hoặc object identity.

### 7.1. Tình huống method trả primitive

```java
if (returnType == int.class && result == null) {
    throw new IllegalStateException("Null result for primitive int");
}
```

Fragment này giúp bắt trường hợp rõ ràng, nhưng một framework proxy vẫn có quyền ném exception riêng khi advice trả null cho primitive. **Đừng** gán fallback `0` chỉ để biến lỗi thành success.

### 7.2. Tình huống method trả Optional

```java
Object result = pjp.proceed();
if (result instanceof java.util.Optional<?> optional) {
    System.out.println("present=" + optional.isPresent());
}
return result;
```

Không tự unwrap Optional rồi return thành String: caller đang mong `Optional<String>`. Nếu cần một fallback, trả `Optional.empty()` hoặc Optional hợp lệ theo contract, không return null.

### 7.3. Tình huống thay arguments

Spring cho phép `pjp.proceed(Object[] args)`. Mảng mới cần **đúng số phần tử, vị trí và kiểu tương thích với các parameters của method đích** trong ngữ cảnh Spring AOP:

```java
Object[] original = pjp.getArgs();
Object[] replacement = original.clone();
replacement[0] = "NORMALIZED";
return pjp.proceed(replacement);
```

Đây là fragment chỉ phù hợp với method có argument đầu tiên kiểu String. Đổi input không được làm biến đổi return contract; method vẫn có quyền trả/throw theo chữ ký. Không dùng `proceed(new Object[0])` cho method cần parameters, và không tưởng rằng việc thay args tự động tạo return type hợp lệ.

### 7.4. Tình huống future/reactive return type

Nếu method trả `CompletableFuture<String>`, `proceed()` có thể trả một future **chưa hoàn thành**. Một `@Around` chạy đồng bộ không có sẵn payload String của future; return `"ready"` để thay future là sai kiểu.

Khi cần transform kết quả asynchronous, có thể dùng:

```java
Object result = pjp.proceed();
if (result instanceof java.util.concurrent.CompletableFuture<?> future) {
    return future.thenApply(value -> value); // Giữ wrapper Future.
}
return result;
```

Đây là fragment minh họa, không phải một giải pháp tổng quát cho tất cả futures/generics; với `CompletableFuture<T>`, transformation phải giữ đúng `T` và error/completion semantics. Với Reactor `Mono<T>`/`Flux<T>` cần sử dụng operators và types tương ứng, không return payload trực tiếp thay publisher.

## 8. Exceptions và return value: không thể bảo toàn một cách máy móc

Advice có thể vừa giữ giá trị trả về bình thường vừa bảo đảm exception **không bị nuốt**:

```java
@Around("execution(* demo.returns..*Service.*(..))")
public Object preserveOutcome(ProceedingJoinPoint pjp) throws Throwable {
    long started = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        long elapsed = System.nanoTime() - started;
        System.out.println("elapsed ns=" + elapsed);
    }
}
```

Advice này không tự trả một fallback khi target ném exception. `throws Throwable` cho phép đưa exception từ `proceed()` đi tiếp. Lưu ý `System.nanoTime()` chỉ dùng đo khoảng thời gian tương đối, không dùng làm wall-clock.

Nếu muốn recovery:

```java
@Around("execution(* demo.returns.PricingService.maybeLookup(..))")
public Object recoverOptional(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (IllegalStateException ex) {
        return java.util.Optional.empty();
    }
}
```

Đây là một decision **thay đổi outcome có chủ đích**, chỉ phù hợp khi API nghiệp vụ cho phép chuyển lỗi cụ thể thành trạng thái không tìm thấy. Không catch `Throwable` rồi return null trên pointcut rộng: nó có thể che lỗi nghiêm trọng, đổi exception thành result sai và phá giao dịch.

### 8.1. Return đúng kiểu nhưng không đúng transaction state

Spring `@Transactional` thường dùng một advisor/interceptor khác. Một `@Around` đứng ngoài transaction interceptor có thể catch exception sau khi transaction đã bị đánh dấu rollback. Trả một DTO “success” từ outer advice không làm rollback-only tự biến mất. Vì vậy, **đúng return type chưa đủ** để bảo đảm transaction commit thành công.

### 8.2. Retry có thể tạo return cuối khác return ban đầu

Gọi `proceed()` hai hoặc nhiều lần để retry là một nghiệp vụ khác, không phải logging. Kết quả từ lần thực thi nào được trả cho caller, side effects có lặp không, exception cuối được ném thế nào đều phải được quy định rõ. Với method idempotent mới nên cân nhắc retry; transaction boundaries, timeout và logging cần được kiểm thử.

### 8.3. Advice khác có thể biến đổi return trước bạn

Trong interceptor chain có thể có caching, security, transactions, async proxy hoặc nhiều `@Around` advices. `pjp.proceed()` trả kết quả của **phần còn lại của chain**, không luôn là “return trực tiếp từ code target chưa qua biến đổi”. Nếu một interceptor bên trong chủ động trả cached value thì outer advice cũng sẽ nhận cached value. Vì vậy cần xác định ordering/chain thực tế khi behavior không giống target method độc lập.

## 9. JDK proxy, CGLIB và giới hạn lời gọi

Giữ return value trong `@Around` chỉ có ý nghĩa khi advice thực sự được gọi.

- **JDK dynamic proxy:** caller dùng public methods thuộc interface mà proxy thực hiện. Nếu implementation có method khác không ở interface, JDK proxy không tự expose method đó.
- **CGLIB class-based proxy:** có thể intercept method mà proxy subclass override được; không override được `final` hay `private`; không subclass được `final class`.
- **Self-invocation:** `this.someMethod()` bên trong target bypass proxy, kể cả method public; một advice match method ấy không được chạy trên lời gọi nội bộ.
- **Object tạo bằng `new` ngoài container:** không nhận Spring AOP proxy; advice không tự xuất hiện.
- **Pointcut không khớp:** method vẫn chạy bình thường, nhưng `@Around` không được áp dụng.
- **Aspect không đăng ký bean:** `@Aspect` một mình không làm Spring component scan tự đăng ký; cần `@Component` hoặc `@Bean` tương ứng.

Không đổi `proxyTargetClass=true` chỉ để sửa lỗi `return null` trong advice; loại proxy không thay thế nghĩa vụ trả đúng value của `@Around`.

## 10. Checklist chẩn đoán khi giá trị sau aspect sai

| Hiện tượng | Nguyên nhân điển hình | Cách xử lý |
| --- | --- | --- |
| Target in kết quả đúng, caller nhận null | Around advice quên `return result` hoặc khai báo `void` | Advice `Object` + `return pjp.proceed()` hoặc `return result` |
| Method `int` báo lỗi sau khi advice trả null | Primitive không thể có null | Trả đúng boxed value từ `proceed()` |
| Return `String` bị lỗi kiểu | Advice trả một kiểu khác, ví dụ `Integer` | Hạn chế pointcut hoặc giữ nguyên value |
| Caller của `Optional<String>` bị NPE | Advice thay `Optional.empty()` bằng null | Giữ `Optional` object |
| Generic List gây ClassCastException khi đọc phần tử | Advice thay List contents sai generic | Tránh thay đổi collection không được contract cho phép |
| `void` method không in return value | Đây là Java `void` bình thường | Log side effect trong advice; không đòi return |
| Target chạy hai lần | Advice gọi `proceed()` hai lần | Mặc định gọi đúng một lần |
| Exception biến mất | `catch` quá rộng hoặc `return` trong `finally` | Truyền exception đi tiếp; không return trong finally |
| Caching advice return sai DTO | Cache chứa object không đúng method contract | Thiết kế cache key/type và kiểm tra trước return |
| Trả `String` thay `CompletableFuture<String>` | Nhầm payload với wrapper | Giữ future/publisher và transform bằng API tương ứng |
| Trả đúng kiểu mà caller thấy state sai | Copy/mutate object trong logging advice | Giữ nguyên reference/immutable policy |
| Lỗi chỉ sau khi thêm logging | `result.toString()` có side effect hoặc ném exception | Log metadata an toàn; không để logging phá flow |
| @Around không chạy trên method nội bộ | Self-invocation bypass Spring proxy | Gọi qua Spring bean khác được DI |
| Advice chạy sai thứ tự với transaction/cache | Advisor ordering và nested chain | Test chain và thiết kế precedence |
| Target ném exception nhưng advice vẫn return "success" | Advice catch và nuốt lỗi | Chỉ dùng fallback nếu nghiệp vụ xác nhận |
| Một số method không có advice | Pointcut, proxy type, aspect bean registration | Kiểm tra Spring context, method visibility, loại proxy |

**Quy trình rà soát có hệ thống:**

1. Xác nhận target/aspect là Spring beans và hạ tầng Spring AOP đã bật.
2. Trên reference thực được DI hoặc lấy từ context, dùng `AopUtils.isAopProxy(bean)` và xác định JDK/CGLIB proxy.
3. Xác nhận pointcut chọn đúng method và caller đi qua proxy.
4. Xem chữ ký target: return type, primitive/reference, nullability, wrapper/generic, exceptions.
5. Xem advice khai báo `Object`, gọi `proceed()` bao nhiêu lần và có `return result` đúng ở mọi nhánh hay không.
6. Kiểm tra các nhánh `catch`/`finally`; tránh return trong finally và tránh nuốt exception ngoài policy.
7. Nếu biến đổi result, test đầy đủ success/failure/empty/null/async với type contract.
8. Kiểm tra các advisors khác như cache/transaction/security để xác định kết quả trong chain.

## 11. Câu hỏi ôn tập

**1. Vì sao `@Around` nên khai báo return type `Object`?** Vì `proceed()` trả `Object` và target methods có nhiều kiểu khác nhau. Lấy và trả chính kết quả ấy giúp proxy giữ contract caller.

**2. Nếu method trả `int 42` nhưng advice trả `null` thì sao?** Primitive không chứa null; proxy/unboxing có thể lỗi runtime. Không dùng null cho primitive.

**3. Method trả `Optional.empty()` thì advice có được đổi thành `null` không?** Về business contract thường là không. Caller kỳ vọng một Optional object; giữ nguyên Optional hoặc biến đổi theo policy xác định.

**4. Có phải cứ `pjp.proceed()` chạy xong là caller nhận target result?** Không. Giá trị cuối là **return của advice**. Nếu advice không trả result hoặc return khác, caller nhận value khác.

**5. Với target `void`, `proceed()` trả gì?** Thông thường `null` qua API `Object`; advice có thể trả chính null đó và caller vẫn thấy một void invocation hợp lệ.

**6. Vì sao `return result` tốt hơn `return result.toString()`?** Giá trị mới là String, có thể khác kiểu declared return; kể cả khi đúng String cũng có thể đổi semantics hoặc ném lỗi từ `toString()`.

**7. Có được gọi `proceed()` nhiều lần không?** Spring AOP cho phép về kỹ thuật, nhưng mỗi lần có thể thực thi lại operation. Nếu không chủ ý retry, chỉ gọi một lần.

**8. `@AfterReturning` có thể thay thế một reference hoàn toàn mới làm kết quả của target không?** Không theo contract thông thường; `@Around` mới có thể chủ động chọn return value khác.

**9. Khi method trả `CompletableFuture<T>`, advice nên trả `T` hay Future?** Phải trả type tương thích với method, tức future tương ứng, không phải payload trực tiếp.

**10. Return đúng kiểu có bảo đảm transaction commit không?** Không. Transaction interceptor có thể đã rollback/đánh dấu rollback-only trước khi outer advice catch exception; phải xét ordering và transaction boundary.

## Tài liệu Spring chính thức

- [Spring Framework — Declaring Advice: Around Advice, proceeds and return value](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html)
- [Spring Framework 6.2 — Declaring Advice / Around Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework — Spring AOP Advice API / MethodInterceptor](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html)
- [Spring Framework — AOP Concepts và role của Around Advice](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html)
- [Spring Framework — Proxying Mechanisms: JDK/CGLIB/self-invocation](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — Declarative Transaction Rollback](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-007">Bao quanh đường thành công và ngoại lệ bằng try/catch/finally</a>

<details>
<summary>Xem chi tiết</summary>

# Bao quanh đường thành công và ngoại lệ bằng try/catch/finally trong Spring @Around

## 1. Vấn đề: một invocation có thể return, throw hoặc được phục hồi

Khi một Spring service được bao bởi `@Around` advice, lời gọi đến method có thể có các kết cục khác nhau:

- **Normal return:** method thực hiện xong, trả một giá trị, kể cả `null`.
- **Exceptional completion:** method hoặc một interceptor phía trong ném ngoại lệ ra ngoài.
- **Recovered completion:** phần bên trong có ném ngoại lệ, nhưng `@Around` bắt và chủ động trả một giá trị fallback hợp lệ.
- **Early return:** `@Around` quyết định không gọi `proceed()`; target và các interceptor phía trong không được chạy ở invocation đó.

Dùng `try/catch/finally` đúng cách trong `@Around` giúp ta **quan sát từng đường đi, giữ nguyên kết quả/exception theo mặc định và luôn thực hiện bước cleanup** khi execution đi ra khỏi advice theo quy tắc Java thông thường.

Đây là kiến thức **Spring Aspect**: `@Around` nằm trong interceptor chain của **Spring AOP proxy**, không phải một khối `try` bao quanh mọi lời gọi Java ở mọi nơi trong chương trình.

### Quy tắc cốt lõi

```java
@Around("execution(* com.example.app.service..*.*(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    long started = System.nanoTime();

    try {
        Object result = pjp.proceed();
        return result;
    } catch (Throwable failure) {
        throw failure;
    } finally {
        long duration = System.nanoTime() - started;
        // Ghi nhận duration hoặc dọn trạng thái do advice quản lý.
    }
}
```

Đây là **snippet minh họa**, cần đặt trong Spring `@Aspect` bean có các import và cấu hình AOP. Nó thể hiện ba việc quan trọng:

1. `pjp.proceed()` yêu cầu **phần còn lại của Spring interceptor chain** tiếp tục; chỉ khi chain tiến tới target thì method nghiệp vụ mới thật sự chạy.
2. `catch` có thể quan sát và xử lý ngoại lệ thoát ra từ `proceed()`; nếu không có chủ đích chuyển kết quả, hãy **throw lại** exception.
3. `finally` chạy khi control flow đi ra khỏi `try` qua return/throw, bất kể caller cuối cùng nhận normal value hay exception. Nó không nhất thiết chỉ chạy ở đường lỗi.

**Phạm vi:** Spring Framework, Spring `@AspectJ` annotation style, proxy-based AOP, `ProceedingJoinPoint` và các kết cục do caller của proxy nhìn thấy. Không mở rộng sang AspectJ compile-time weaving hoặc các join point không được Spring AOP hỗ trợ như constructor/field access.

## 2. Sơ đồ luồng đúng của Spring @Around

```text
Client
  |
  v
Spring AOP proxy
  |
  v
@Around advice
  |
  +-- Code trước try/proceed
  |
  +-- try {
  |      pjp.proceed() ---------> Các advisors phía trong
  |                                  |
  |                                  v
  |                              Target method
  |                                  |
  |                         +--------+--------+
  |                         |                 |
  |                      return              throw
  |                         |                 |
  |                         v                 v
  |                   try nhận result   catch nhận exception
  |                         |                 |
  |                   return result     rethrow hoặc fallback
  |                         |                 |
  |                         +--------+--------+
  |                                  |
  +-- finally: dọn trạng thái / ghi tổng thời gian
  |
  v
Caller nhận value hoặc exception do advice đưa ra ngoài
```

Có hai tầng kết cục cần phân biệt:

- **Kết cục của `proceed()`:** phần interceptor chain phía trong trả normal result hoặc ném exception.
- **Kết cục của invocation nhìn từ bên ngoài advice:** có thể khác nếu `catch` trả fallback, advice đổi result, hoặc advice tự throw exception mới.

Chẳng hạn, target ném `IllegalStateException` nhưng `@Around` nằm ngoài bắt exception và return `"FALLBACK"`: caller có thể **không nhận exception**, dù target đã thất bại. Nếu Spring transaction interceptor nằm bên trong và đã xử lý rollback thì việc return fallback ở advice phía ngoài **không hoàn tác rollback**. Ngược lại, khi transaction interceptor nằm ngoài và chỉ thấy normal return, trạng thái rollback có thể khác. Vì vậy xử lý exception trong aspect không chỉ là quyết định ghi log.

### Spring proxy là điều kiện bắt buộc

`@Around` không chạy nếu:
- Bạn tự tạo target bằng `new Service()` ngoài Spring.
- Target gọi `this.otherMethod()`: self-invocation đi thẳng vào target.
- Method không khớp pointcut hoặc target không được AOP proxy.
- Method không thể được proxy intercept vì ràng buộc visibility/type, như `private`/`final` trên CGLIB.
- Một interceptor **phía ngoài** short-circuit trước khi đi tới `@Around` của bạn.

Vì thế, khi debug “finally không chạy”, kiểm tra **advice có được gọi ngay từ đầu hay không**, không chỉ kiểm tra cú pháp `finally`.

## 3. Vai trò chính xác của try, catch và finally

### 3.1. try: chạy phần chain và giữ kết quả

```java
Object result = pjp.proceed();
return result;
```

`proceed()` không đơn giản là gọi một phương thức Java thông thường; trong Spring AOP, nó đi tiếp qua các advisor còn lại rồi mới tới target nếu không có interceptor short-circuit.

Ở đường normal:
- `proceed()` trả về giá trị tương thích target method contract.
- `return result` giữ nguyên reference mà caller của proxy nhìn thấy.
- `finally` vẫn chạy **trước khi** caller nhận kết quả.

Ở đường exceptional:
- `proceed()` ném exception.
- Các statement nằm sau nó trong `try` không được thực hiện.
- Java tìm `catch` phù hợp; `finally` vẫn chạy khi thoát phạm vi.

### 3.2. catch: phân loại exception và chủ động quyết định

```java
try {
    return pjp.proceed();
} catch (IOException ex) {
    // Trường hợp failure từ I/O.
    throw ex;
} catch (RuntimeException ex) {
    // Trường hợp unchecked failure.
    throw ex;
}
```

Đây là snippet cần import `java.io.IOException` và đặt trong `@Around` method `throws Throwable`. Chọn kiểu catch là lựa chọn có ý nghĩa:
- `catch(IOException ex)` chỉ bắt `IOException` hoặc subtype.
- `catch(RuntimeException ex)` bắt các runtime exceptions.
- `catch(Throwable ex)` có thể bắt cả `Error` và mọi throwable; rất rộng nên phù hợp với quan sát + **rethrow nguyên vẹn**, không phù hợp để nuốt tất cả lỗi.

**Thông lệ cho tracing/metrics:** nếu không có chính sách phục hồi thực sự, `catch` chỉ ghi nhận exception rồi `throw ex`; giữ nguyên loại exception, stack trace và nguyên nhân. Không tự đổi thành `new RuntimeException(ex)` vì thay đổi hợp đồng lỗi.

### 3.3. finally: có chạy ngay cả khi return trong catch không?

Có. Java thực thi `finally` khi đi ra khỏi khối `try`/`catch` bằng `return` hoặc `throw` trong những điều kiện unwind thông thường.

```java
try {
    return pjp.proceed();
} catch (IllegalStateException ex) {
    return "FALLBACK";
} finally {
    System.out.println("cleanup");
}
```

Trong trường hợp `proceed` throw `IllegalStateException`, advice vào `catch`, chọn giá trị `"FALLBACK"`, **chạy `finally`**, rồi mới return kết quả ấy cho caller.

### 3.4. Đường nào chạy callback nào?

| Tình huống trong `@Around` | `catch` | `finally` | Caller thấy |
| --- | --- | --- | --- |
| `proceed()` return `"OK"` | Không | Có | `"OK"` |
| `proceed()` return `null` | Không | Có | `null` |
| `proceed()` throw `IOException`, advice rethrow | Catch phù hợp | Có | `IOException` |
| `proceed()` throw `IllegalStateException`, advice rethrow | Catch phù hợp | Có | `IllegalStateException` |
| `proceed()` throw `IllegalStateException`, advice return fallback | Catch phù hợp | Có | Giá trị fallback |
| Advice return ngay trong `try` mà không gọi `proceed()` | Không | Có | Kết quả do advice tạo |
| Advice throw ngay trong `try` trước `proceed()` | Catch phù hợp nếu có | Có | Exception hoặc fallback |
| Interceptor phía ngoài không gọi tiếp vào advice này | Không vào advice | Không | Tùy interceptor bên ngoài |

Đây là các kết quả **theo vị trí advice đang xét**. Nếu có advice khác ở ngoài và bên trong, chúng có thể nhìn thấy kết cục khác nhau.

## 4. Công thức đo thời gian và logging đúng

### 4.1. Dùng finally, tránh chỉ log sau proceed

Cách thiếu sót:

```java
long started = System.nanoTime();
Object result = pjp.proceed();
System.out.println("elapsed=" + (System.nanoTime() - started));
return result;
```

Nếu `proceed()` ném exception, dòng ghi duration bị bỏ qua. Lúc đó metrics chỉ bao gồm normal invocations; những lời gọi thất bại (thường mất nhiều thời gian hơn) lại vắng mặt.

Cách đúng:

```java
long started = System.nanoTime();
try {
    return pjp.proceed();
} finally {
    long elapsed = System.nanoTime() - started;
    System.out.println("elapsedNs=" + elapsed);
}
```

Nếu cần phân biệt normal/failed cũng có thể thêm biến local hoặc `catch`:

```java
long started = System.nanoTime();
boolean failed = false;
try {
    return pjp.proceed();
} catch (Throwable ex) {
    failed = true;
    throw ex;
} finally {
    record(pjp.getSignature().getName(),
           failed,
           System.nanoTime() - started);
}
```

Ở đây `record(...)` là hàm giả định, không phải API Spring; nó phải được định nghĩa trong aspect hoặc helper phù hợp. **Dùng biến local per-invocation**, không dùng một field singleton mutable như `boolean failed` trong Spring `@Aspect`; cùng một aspect instance có thể được nhiều request threads dùng đồng thời.

### 4.2. Đo ở phía ngoài hay bên trong cache/transaction?

Giả sử cùng method được advice bởi logging `@Around` và caching:

```text
Client -> logging @Around -> cache interceptor -> target
```

Nếu `@Around` nằm ngoài cache, nó có thể ghi nhận cả cache hit mà không cần target chạy. Nếu cache interceptor nằm ngoài, cache hit có thể trả kết quả ngay và **không đi vào logging advice** phía trong.

Tương tự, duration ở ngoài transaction interceptor có thể bao gồm thời gian xử lý transaction; duration ở trong có thể chỉ đo một phạm vi hẹp. Vì thế phải đặt tên metric đúng boundary, ví dụ `service_proxy_duration` khác với `target_method_duration`.

Không tự kết luận “@Around luôn ngoài transaction/cache” nếu chưa xác định **advisor precedence** bằng `@Order`/`Ordered` hoặc cấu hình order tương ứng.

## 5. Catch rồi rethrow: quan sát lỗi mà không đổi hợp đồng

Một observability aspect nên có chính sách mặc định:

```java
@Around("execution(* com.example.app.service..*.*(..))")
public Object observe(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (Throwable ex) {
        System.out.println("errorType="
            + ex.getClass().getSimpleName());
        throw ex;
    } finally {
        System.out.println("invocationFinished");
    }
}
```

Ví dụ trên có thể quan sát cả checked và unchecked exceptions từ `proceed()`. Không nên làm như sau:

```java
catch (Throwable ex) {
    System.out.println("error");
    return null;
}
```

vì hành vi này **nuốt mọi throwable**, bao gồm cả lỗi hệ thống nghiêm trọng, và trả `null` dù caller có thể mong primitive hay kết quả bắt buộc khác.

### Checked và unchecked exceptions trong Spring proxy

- `IOException` là checked exception; interface/target method nên khai báo `throws IOException` nếu nó là phần hợp đồng.
- `IllegalStateException` là unchecked; không cần `throws`.
- Với JDK dynamic proxy, một checked exception không thuộc interface method `throws` có thể bị bao bởi `UndeclaredThrowableException` khi ra khỏi proxy. Đây là một lý do nên giữ đúng hợp đồng Java và kiểm tra nguyên nhân exception khi dùng JDK proxy.
- Không nhầm việc `@AfterThrowing` có thể bind `Throwable` với việc mọi checked exception sẽ luôn ra caller dưới dạng type gốc trong mọi thiết kế proxy.

## 6. Catch rồi return fallback: khi nào chính đáng?

Một fallback có thể hợp lệ khi:
- Method là **read-only query** và cho phép trả dữ liệu dự phòng.
- Một remote service tạm mất kết nối nhưng nghiệp vụ có giá trị cache an toàn.
- Caller contract cho phép giá trị thay thế và có cách đánh dấu nó không phải kết quả mới nhất.
- Đã có chính sách loại ngoại lệ được phép phục hồi, thời hạn dữ liệu, logging và cảnh báo.

Ví dụ:

```java
@Around("execution(public String com.example.app.service.LookupService.lookup(..))")
public Object fallback(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (IllegalStateException ex) {
        return "NOT_AVAILABLE";
    } finally {
        System.out.println("lookupFinished");
    }
}
```

Đây là đoạn minh họa **chỉ hợp lệ cho một method trả `String` khi nghiệp vụ chấp nhận `NOT_AVAILABLE`**. Nếu method trả `int`, trả string sẽ vi phạm return contract. Nếu method cập nhật database hoặc chuyển tiền, nuốt exception có thể khiến caller tưởng thao tác đã thành công.

Một fallback có chủ đích nên được đo thành hai chỉ số khác nhau:
- `inner_failure_count`: phần `proceed()` đã ném exception.
- `proxy_normal_return_count`: advice đã đổi exception thành normal return cho caller.

Chúng có thể **cùng tăng ở một invocation**. Không dùng một nhãn success/failure đơn giản mà bỏ mất sự khác biệt này.

### Điều gì xảy ra với @AfterReturning và @AfterThrowing?

Trong một Spring AOP proxy, những advice ở **vị trí khác nhau** nhìn thấy outcome tại vị trí của chúng trong interceptor chain:

```text
Outer @AfterReturning / @AfterThrowing
         |
         v
Fallback @Around
         |
         v
Inner @AfterReturning / @AfterThrowing
         |
         v
Target
```

Khi target throw, inner `@AfterThrowing` có thể thấy exception. Nếu fallback around bắt nó và return, outer `@AfterReturning` có thể thấy normal completion thay vì exception.

Do đó không thể phát biểu “cứ target throw thì mọi `@AfterThrowing` đều chạy”; phải nói rõ advisor nằm ở đâu và liệu chain có được đi qua hay không.

### Không dùng return trong finally

Ví dụ sau là anti-pattern nghiêm trọng:

```java
try {
    return pjp.proceed();
} finally {
    return "OVERRIDDEN";
}
```

`return` trong `finally` **ghi đè** cả kết quả return và exception đang được truyền ra. Nó có thể nuốt `IOException` hoặc `IllegalStateException`, làm lạc mất nguyên nhân sự cố, hỏng rollback và tạo kết quả giả. Nếu cleanup cũng throw exception, exception cleanup có thể che khuất exception gốc.

Để giữ semantics, chỉ làm cleanup/logging không phá vỡ luồng trong `finally`; khi cleanup có thể fail, cần thiết kế xử lý lỗi/suppressed exceptions phù hợp, tránh tự động thay kết cục của method nghiệp vụ.

## 7. Ví dụ chạy được: normal, null, checked, unchecked, fallback

Ví dụ dưới đây dùng **Java 17+, Spring Framework 6.1.14, AspectJ Weaver 1.9.22.1 và Gradle**. Dùng Spring Framework thuần để nhìn rõ proxy/advice, không phụ thuộc AOP auto-configuration của Spring Boot.

### 7.1. Cấu trúc dự án

```text
spring-around-flow/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/flow/
    ├── TaskService.java
    ├── FlowAspect.java
    ├── AppConfig.java
    └── DemoApplication.java
```

### 7.2. Cấu hình Gradle

`settings.gradle`:

```groovy
rootProject.name = 'spring-around-flow'
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
    mainClass = 'demo.flow.DemoApplication'
}
```

`spring-context` cung cấp IoC container; `spring-aop` chứa Spring AOP proxy infrastructure; `aspectjweaver` cung cấp những thành phần cần để Spring phân tích pointcut. Không cần web server hay database.

### 7.3. Target trả kết quả hoặc ném hai loại exception

`src/main/java/demo/flow/TaskService.java`:

```java
package demo.flow;

import java.io.IOException;

public class TaskService {

    public String execute(String mode) throws IOException {
        System.out.println("target:" + mode);

        if ("ok".equals(mode)) {
            return "SAVED";
        }
        if ("empty".equals(mode)) {
            return null;
        }
        if ("checked".equals(mode)) {
            throw new IOException("offline");
        }
        if ("unchecked".equals(mode)) {
            throw new IllegalStateException("broken");
        }
        if ("recover".equals(mode)) {
            throw new IllegalStateException("retryable");
        }
        return "UNKNOWN";
    }

    public void selfCall() throws IOException {
        System.out.println("target:selfCall");
        String value = execute("ok");
        System.out.println("target:selfResult:" + value);
    }
}
```

`TaskService` là Spring bean target, chưa phải proxy khi được tạo bằng `new` trong `@Bean`. `execute` là public non-final method và ném checked `IOException` theo hợp đồng. `selfCall()` cũng public, nhưng `execute("ok")` bên trong nó là **self-invocation** trực tiếp trên target.

### 7.4. Aspect bao quanh năm đường với try/catch/finally

`src/main/java/demo/flow/FlowAspect.java`:

```java
package demo.flow;

import java.io.IOException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class FlowAspect {

    @Around(
        "execution(public String demo.flow.TaskService.execute(String))"
    )
    public Object aroundExecute(ProceedingJoinPoint pjp) throws Throwable {
        String mode = (String) pjp.getArgs()[0];
        System.out.println("around:enter:" + mode);

        try {
            Object result = pjp.proceed();
            System.out.println("around:normal:" + mode
                    + ":" + String.valueOf(result));
            return result;

        } catch (IOException ex) {
            System.out.println("around:checked:" + mode
                    + ":" + ex.getMessage());
            throw ex;

        } catch (IllegalStateException ex) {
            System.out.println("around:unchecked:" + mode
                    + ":" + ex.getMessage());

            if ("recover".equals(mode)) {
                System.out.println("around:fallback:" + mode);
                return "FALLBACK";
            }
            throw ex;

        } finally {
            System.out.println("around:finally:" + mode);
        }
    }
}
```

Ý nghĩa từng nhánh:
- `try` gọi `proceed()` **đúng một lần**. Lời gọi này tiến vào phần chain bên trong rồi tới target.
- `"ok"` trả `"SAVED"`, advice log `around:normal:ok:SAVED`, rồi return chính kết quả đó.
- `"empty"` trả `null`, advice log `around:normal:empty:null` và return `null`. Đây vẫn là normal completion.
- `"checked"` ném `IOException`, advice vào đúng catch checked, log rồi **rethrow**.
- `"unchecked"` ném `IllegalStateException`, advice log rồi **rethrow** vì không phải chế độ recover.
- `"recover"` ném `IllegalStateException`, advice log và trả `"FALLBACK"` theo chính sách minh họa. Caller **không nhận** `IllegalStateException` này.
- `finally` log cho cả năm trường hợp đã đi vào advice, kể cả return từ catch.
- Các biến `mode`/`result` chỉ tồn tại trong invocation; không có trạng thái mutable chia sẻ giữa nhiều threads trong singleton aspect.

Cách tổ chức catch này mang tính **giảng dạy**. Trong production, không nên quyết định fallback bằng một string mode chung chung nếu chính sách nghiệp vụ không cho phép; hãy dùng exception type riêng, marker annotation, pointcut phạm vi hẹp hoặc abstraction retry/fallback rõ ràng.

### 7.5. Cấu hình ApplicationContext và bật AOP

`src/main/java/demo/flow/AppConfig.java`:

```java
package demo.flow;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public TaskService taskService() {
        return new TaskService();
    }

    @Bean
    public FlowAspect flowAspect() {
        return new FlowAspect();
    }
}
```

`@Aspect` chỉ đánh dấu lớp chứa advice; `@Bean` đăng ký aspect instance trong Spring container. `@EnableAspectJAutoProxy` kích hoạt auto-proxy creator. `proxyTargetClass=true` yêu cầu class-based proxy nên client có thể lấy bean bằng `TaskService.class`. Không cần component scan.

### 7.6. Main gọi qua proxy và tự bắt exception để in kết quả

`src/main/java/demo/flow/DemoApplication.java`:

```java
package demo.flow;

import java.io.IOException;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApplication {

    public static void main(String[] args) throws IOException {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            TaskService service = context.getBean(TaskService.class);
            System.out.println("proxy=" + AopUtils.isAopProxy(service));

            invoke(service, "ok");
            invoke(service, "empty");
            invoke(service, "checked");
            invoke(service, "unchecked");
            invoke(service, "recover");

            service.selfCall();
        }
    }

    private static void invoke(TaskService service, String mode) {
        try {
            String result = service.execute(mode);
            System.out.println("caller:" + mode + ":" + result);
        } catch (IOException | IllegalStateException ex) {
            System.out.println("caller:" + mode + ":throws:"
                    + ex.getClass().getSimpleName()
                    + ":" + ex.getMessage());
        }
    }
}
```

Các lời gọi `invoke(...)` đi qua Spring-managed `service` proxy. Method `selfCall()` không khớp pointcut của `FlowAspect` (pointcut chỉ chọn `execute`), và trong method này lời gọi `execute("ok")` không đi qua proxy.

### 7.7. Lệnh chạy và stdout chính xác

Tại thư mục chứa `build.gradle`:

```bash
gradle run
```

Nếu project có Gradle Wrapper, dùng `./gradlew run` (Linux/macOS) hoặc `gradlew.bat run` (Windows).

**Các dòng từ Java code**, không tính logs từ Gradle hoặc môi trường:

```text
proxy=true
around:enter:ok
target:ok
around:normal:ok:SAVED
around:finally:ok
caller:ok:SAVED
around:enter:empty
target:empty
around:normal:empty:null
around:finally:empty
caller:empty:null
around:enter:checked
target:checked
around:checked:checked:offline
around:finally:checked
caller:checked:throws:IOException:offline
around:enter:unchecked
target:unchecked
around:unchecked:unchecked:broken
around:finally:unchecked
caller:unchecked:throws:IllegalStateException:broken
around:enter:recover
target:recover
around:unchecked:recover:retryable
around:fallback:recover
around:finally:recover
caller:recover:FALLBACK
target:selfCall
target:ok
target:selfResult:SAVED
```

### 7.8. Đọc kết quả theo từng kết cục

| Input | Kết cục của `proceed()` | `catch` | `finally` | Kết cục caller thấy |
| --- | --- | --- | --- | --- |
| `ok` | Return `"SAVED"` | Không | Có | `"SAVED"` |
| `empty` | Return `null` | Không | Có | `null` |
| `checked` | Throw `IOException("offline")` | `catch IOException`, rethrow | Có | `IOException` |
| `unchecked` | Throw `IllegalStateException("broken")` | `catch IllegalStateException`, rethrow | Có | `IllegalStateException` |
| `recover` | Throw `IllegalStateException("retryable")` | `catch IllegalStateException`, return fallback | Có | `"FALLBACK"` |

Dòng `caller:recover:FALLBACK` cho thấy một exception thực sự đã xuất hiện trong phần target/inner chain, nhưng được `@Around` biến thành normal return tại ranh giới proxy. Điều này **không tương đương** với việc target chưa từng ném exception.

Cuối chương trình:

```text
target:selfCall
target:ok
target:selfResult:SAVED
```

không có các dòng `around:enter:ok`/`around:finally:ok` thêm lần nữa. Điều đó minh họa lời gọi nội bộ `this.execute(...)` bỏ qua Spring AOP proxy. Đổi `try/catch/finally` không khắc phục self-invocation; muốn advice chạy cần lời gọi đi qua một Spring-managed proxy thích hợp.

## 8. So sánh với @AfterReturning, @AfterThrowing và @After

Có ba loại advice Spring hữu ích khi không cần điều khiển flow:

| Mục tiêu | Loại advice phù hợp | Ghi chú |
| --- | --- | --- |
| Quan sát method trả bình thường | `@AfterReturning` | Return `null` vẫn là normal return |
| Quan sát exception thoát ra ngoài method execution | `@AfterThrowing` | `throwing` có thể bind `Throwable`/subtype |
| Dọn dẹp hoặc ghi nhận kết thúc cả hai đường | `@After` | Tương đương after-finally; không tự đổi return hoặc swallow exception |
| Giữ cùng biến đo giữa trước và sau, hoặc điều khiển kết quả | `@Around` | Có quyền `proceed`/short-circuit/catch/rethrow |

Ví dụ:

```java
@AfterReturning(
    pointcut = "execution(* com.example.app.service..*.*(..))",
    returning = "value",
    argNames = "value"
)
public void afterNormal(Object value) {
    // Normal completion, kể cả null.
}
```

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app.service..*.*(..))",
    throwing = "error",
    argNames = "error"
)
public void afterThrown(Throwable error) {
    // Exceptional completion, checked hoặc unchecked.
}
```

```java
@After("execution(* com.example.app.service..*.*(..))")
public void afterFinally() {
    // Khi method execution khớp đã kết thúc.
}
```

Đây là **các snippets minh họa** phải đặt trong `@Aspect` và đăng ký vào context; không nên dán đồng thời với demo mà không cân nhắc precedence của nhiều advisors.

Một khác biệt thực tiễn:
- `@AfterReturning` không được dùng để thay reference trả cho caller bằng return value mới từ advice.
- `@AfterThrowing` không phải cơ chế tổng quát để catch rồi return fallback.
- `@After` không phân loại “success” hay “failure”.
- `@Around` có thể điều khiển return/throw nên phải bảo toàn contract khi không muốn đổi nghiệp vụ.

### Vì sao @AfterReturning và @AfterThrowing không nhất thiết cùng đo “target outcome”?

Khi các advisors lồng nhau, một `@Around` phía trong có thể biến exception thành fallback. `@AfterReturning` phía ngoài chỉ quan sát normal return; `@AfterThrowing` phía trong có thể đã quan sát exception trước đó. Ngược lại, cache/retry interceptor bên ngoài có thể không đi vào một advice nằm bên trong.

Để đánh giá số lượt “thành công”, hãy xác định mình đang đo **target attempts** hay **caller-visible proxy calls**. Nếu hai hệ thống metrics đo hai lớp khác nhau, số lượng không nhất thiết trùng khớp.

## 9. Các sai lầm thường gặp với finally

### 9.1. Throw trong finally che mất exception gốc

```java
try {
    return pjp.proceed();
} finally {
    throw new IllegalStateException("logging failed");
}
```

Đây là anti-pattern: kể cả target return thành công hoặc throw `IOException`, caller vẫn nhận `IllegalStateException("logging failed")`, mất outcome gốc. Nếu logging, tracing hoặc cleanup có thể thất bại, cần thiết kế lỗi phụ **không che giấu lỗi nghiệp vụ**; có thể catch lỗi logging có chọn lọc, dùng logger/counter an toàn hoặc giữ suppressed cause theo policy.

### 9.2. Return trong finally làm mất kết quả

```java
try {
    return pjp.proceed();
} finally {
    return "ALWAYS";
}
```

Tương tự, code này ghi đè mọi return và exception. Không dùng `return` trong `finally` của around advice trừ khi có chính sách đặc biệt được review rất kỹ; trong phần lớn trường hợp đây là bug.

### 9.3. Đặt cả code sau proceed và finally rồi đếm hai lần

```java
Object result = pjp.proceed();
incrementCompleted();
return result;
```

đếm chỉ normal returns, còn:

```java
try {
    Object result = pjp.proceed();
    incrementCompleted();
    return result;
} finally {
    incrementCompleted();
}
```

lại **đếm hai lần trên normal return** và một lần trên exception. Nếu muốn đếm tổng số lượt hoàn tất, chỉ increment trong `finally`; nếu muốn hai metrics riêng, đặt tên rõ `normal_count` và `total_completed_count`.

Các snippets ở đây dùng `incrementCompleted()` như helper giả định; không phải API chuẩn của Spring.

### 9.4. Dùng field singleton để lưu trạng thái mỗi invocation

Sai:

```java
@Aspect
public class UnsafeAspect {
    private long started;

    @Around("execution(* com.example.app.service..*.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        started = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            System.out.println(System.nanoTime() - started);
        }
    }
}
```

Nếu aspect là singleton (phổ biến trong Spring), nhiều threads ghi đè `started`. Hãy dùng **biến local** `long started` trong method advice hoặc một context được quản lý đúng theo invocation, không dùng state chia sẻ không đồng bộ.

### 9.5. Gọi finally bên ngoài vùng try

Nếu muốn cleanup cho mọi nhánh **trong advice**, đặt code nhạy cảm vào `try`. Ví dụ:

```java
Object[] args = pjp.getArgs();
String first = ((String) args[0]).trim(); // Có thể throw trước try.
try {
    return pjp.proceed(new Object[] { first });
} finally {
    cleanup();
}
```

Nếu code xử lý argument nằm trước `try` ném `ClassCastException` hoặc `ArrayIndexOutOfBoundsException`, `finally` này **không chạy**, vì control flow chưa vào `try`. Đặt ranh giới `try` bao cả những thao tác mà bạn muốn cleanup bao phủ và kiểm tra argument arity/type trước khi dùng.

## 10. Tương tác với transaction, cache, retry và exception handler

### 10.1. Spring transaction không đồng nghĩa catch/rethrow semantics

Với Spring `@Transactional` ở proxy mode, rollback được quyết định bởi transaction interceptor, loại exception và cấu hình rollback. Theo quy tắc mặc định phổ biến trong Spring Framework, unchecked `RuntimeException` và `Error` kích hoạt rollback; checked exception không mặc định rollback trừ khi cấu hình `rollbackFor`/cơ chế tương ứng.

**Không suy ra “catch rồi rethrow checked exception” luôn rollback.** Spring transaction rules vẫn áp dụng độc lập.

Nếu một `@Around` nằm **bên trong** transaction interceptor và catch exception rồi return fallback, transaction interceptor ở ngoài có thể xem lần gọi là normal completion. Nếu `@Around` nằm **bên ngoài**, transaction interceptor có thể đã quyết định rollback dựa trên exception từ inner method trước khi outer around trả fallback. Cấu trúc thật phụ thuộc advisor order.

### 10.2. Cache hit cũng là normal completion ở một số vị trí

Một cache interceptor có thể trả cache hit mà **không tiếp tục gọi target**. `@Around` phía ngoài cache vẫn thấy `proceed()` return bình thường (dù target chưa chạy). `@Around` phía trong cache có thể không chạy trên cache hit.

Vì thế `around:normal` trong logging aspect có thể nghĩa “proxy invocation trả bình thường”, không nhất thiết “target method vừa chạy thành công”. Chọn tên log/metric phù hợp với điểm quan sát.

### 10.3. Retry có thể làm cùng một caller invocation có nhiều exceptions

Khi retry interceptor hoặc `@Around` gọi `proceed()` nhiều lần, target có thể thất bại hai lần rồi thành công lần thứ ba. Một advice phía ngoài retry có thể thấy một normal return; advice phía trong có thể thấy các attempts đã throw. Phải xác định rõ cần đo **request outcome** hay **attempt outcome**.

Để retry an toàn, cân nhắc:
- Giới hạn số lần thử, backoff và timeout;
- Idempotency, tránh ghi đôi hoặc gửi hai lần;
- Exception nào được retry;
- Transaction boundary cho từng attempt;
- Tương tác với logging/trace và metric counters.

Không thay retry policy bằng catch-all rồi gọi `proceed()` vô hạn.

### 10.4. Spring MVC exception handler là lớp khác

`@ControllerAdvice`/`@ExceptionHandler` của Spring MVC có thể xử lý exception **sau khi** proxy của controller/service ném exception ra ngoài. Chúng không làm exception “biến mất” đối với `@Around` của service nếu service interceptor chain đã quan sát và rethrow exception trước đó.

Ngược lại, nếu `@Around` của service catch rồi return fallback, web layer có thể không có exception để handler xử lý. Phân biệt rõ:
- **Spring AOP @Around:** điều khiển một method invocation qua proxy.
- **Spring MVC exception handling:** xử lý exception trong request dispatch/web handler flow.

Đừng nhập hai cơ chế thành một câu “catch trong AOP là ControllerAdvice”.

## 11. Bảng chẩn đoán đường thành công/lỗi

| Triệu chứng | Nguyên nhân có thể | Kiểm tra/cách sửa |
| --- | --- | --- |
| `finally` không xuất hiện khi gọi service | Lời gọi không qua Spring AOP proxy; pointcut không khớp; outer advisor short-circuit | Kiểm tra `AopUtils.isAopProxy`, bean registration, call path, advisor chain |
| `finally` không xuất hiện khi exception ném trước `try` | Code gây lỗi chạy ngoài vùng try | Đặt vùng try bao đúng phần cần cleanup |
| Catch không chạy với exception đã được target nuốt | Target tự `catch` và normal return | Log ở target catch hoặc thiết kế advice khác |
| Catch chỉ thấy `IllegalStateException` mà không thấy `IOException` | Bắt subtype cụ thể | Thêm catch phù hợp hoặc `Throwable` + rethrow khi cần observability |
| Caller không nhận exception mà target đã throw | Advice catch rồi return fallback | Xem chính sách fallback và advisor order |
| Caller nhận exception lạ từ logging | Finally/catch ném lỗi mới che exception gốc | Tách lỗi instrumentation, giữ original failure |
| Caller nhận `null` dù target return non-null | Advice trả `void` hoặc chủ động return `null` | Dùng `Object` và `return pjp.proceed()` |
| Duration chỉ có cho success | Đặt logging sau `proceed()`, không ở `finally` | Đo trong finally |
| Counter success tăng khi trả `null` | `null` vẫn là normal return | Phân biệt normal completion và business success |
| Counter ghi hai lần khi return | Increment trong cả try và finally | Tách normal/total hoặc chỉ một nơi |
| Self-invocation không có advice | `this.method()` bypass proxy | Tách bean cộng tác hoặc gọi qua proxy phù hợp |
| Method `final`/`private` không được advice | CGLIB không override được | Thiết kế API/proxy lại |
| Catch không thấy exception tạo ở advisor phía ngoài | Advice đang nằm bên trong, ngoại lệ từ phía ngoài không đi qua catch này | Xác định vị trí advisor/preference |
| Exception checked bị wrap khi dùng JDK proxy | Interface method không khai báo checked exception | Sửa method contract và kiểm tra cause |
| Aspect log nhạy cảm | In `pjp.getArgs()`/exception payload không lọc | Masking/allowlist, tránh log credentials/PII |

## 12. Quy trình kiểm thử và debug có hệ thống

1. **Đăng ký Spring AOP:** `@Aspect` class đã là Spring bean chưa? `@EnableAspectJAutoProxy` hoặc Boot AOP auto-config có bật trong đúng context không?
2. **Lấy proxy thật:** client gọi `context.getBean(...)` hoặc injected dependency? Không dùng `new Target()` để kiểm tra advice.
3. **Xác định proxy type:** `AopUtils.isJdkDynamicProxy` hay `isCglibProxy`? Method có proxy được và visible không?
4. **Kiểm tra pointcut:** `execution(...)` có đúng class/method/return type/parameters? Có bị package naming đổi không?
5. **Kiểm tra self-invocation:** có `this.otherMethod()` hay call từ constructor/init không?
6. **Xác định ranh giới try:** lỗi xảy ra **trước** hay **trong** try? Catch type có khớp exception không?
7. **Kiểm tra result/exception:** return type có tương thích? Có swallow hoặc wrap exception không cần thiết không?
8. **Kiểm tra finally:** có return/throw từ finally che original outcome? Cleanup có thể throw không?
9. **Xác định interceptor order:** caching/transaction/retry có làm target không chạy hoặc đổi outcome không?
10. **Test năm trường hợp:** return object, return null, checked exception, unchecked exception, catch→fallback.
11. **Test self-invocation:** call method từ một target method khác; xác nhận lời gọi nội bộ không đi qua proxy.
12. **Test concurrency:** nếu aspect là singleton, mọi state per-invocation phải local hoặc quản lý thread-safe.

Một snippet kiểm tra nhanh khi đã có Spring `context`:

```java
Object bean = context.getBean("taskService");

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
```

Cần import `org.springframework.aop.support.AopUtils`; đây là **snippet chẩn đoán**, không phải file Java độc lập. `isAopProxy=true` chứng minh bean có proxy, nhưng chưa chứng minh mọi method hoặc mọi call path đều đi qua advice.

## 13. Checklist thiết kế try/catch/finally trong @Around

- [ ] Advice dùng `ProceedingJoinPoint` làm tham số đầu, trả `Object`, và `throws Throwable` khi truyền exception từ `proceed()`.
- [ ] Mặc định gọi `proceed()` đúng một lần; 0/n lần chỉ khi có chính sách rõ.
- [ ] `try` bao trọn phần code mà bạn muốn quản lý cleanup/metrics.
- [ ] `catch` chọn đúng exception type; không nuốt `Throwable` tùy tiện.
- [ ] Nếu không chủ đích fallback, **rethrow original exception**.
- [ ] `finally` không `return`/`throw` ngoài ý muốn, không che exception nghiệp vụ.
- [ ] Không log dữ liệu nhạy cảm từ `getArgs()` hoặc error message không kiểm soát.
- [ ] Tách metric normal return, exception, recovered fallback và completed.
- [ ] Chọn vị trí aspect so với cache, transaction, retry có chủ đích và có test.
- [ ] Dùng biến local per invocation, tránh mutable state không đồng bộ trong singleton aspect.
- [ ] Đảm bảo fallback đúng return type và nghiệp vụ cho phép.
- [ ] Đã thử cả return `null`, checked/unchecked exception và self-invocation.
- [ ] Với logic chỉ cần after-return/after-throw/finally mà không cần flow control, cân nhắc `@AfterReturning`, `@AfterThrowing`, `@After` đơn giản hơn.

## 14. Kết luận

Trong Spring `@Around`, `try/catch/finally` giúp bạn nắm cả hai đường **trả bình thường** và **ném ngoại lệ**, đồng thời thực hiện cleanup khi advice unwind:

- **`try` + `proceed()`:** tiếp tục Spring interceptor chain và nhận normal result hoặc exception.
- **`catch`:** quan sát exception; rethrow nếu không có chính sách chuyển đổi được xác nhận.
- **`finally`:** ghi metric tổng/dọn trạng thái ở cả normal return, rethrow và fallback return.

Điều quan trọng hơn cú pháp là **định nghĩa chính xác outcome ở vị trí của advice trong proxy chain**. Caller-visible result có thể khác target outcome nếu quanh nó có fallback, cache hoặc retry. `@Around` có thể giúp hệ thống vững chắc hơn, nhưng cũng có thể làm lỗi nghiêm trọng khó phát hiện nếu nuốt exception, đổi return type hoặc làm sai transaction semantics.

Bài toán chỉ thuộc Spring AOP khi invocation đi qua **Spring-managed proxy** và pointcut khớp. Một `try/finally` hoàn hảo không thể thay thế điều kiện proxy, không sửa self-invocation và không tự chặn mọi Java method.

### Tài liệu Spring chính thức

- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): Spring `@Around`, `ProceedingJoinPoint.proceed()`, return value, throwing, `@After` và advice ordering.
- [Spring Framework — AOP Concepts](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html): các loại advice, normal/exceptional completion và around advice.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): đăng ký và kích hoạt aspect trên Spring beans.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB, self-invocation và ranh giới proxy.
- [Spring Framework — Rolling Back a Declarative Transaction](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html): rollback rules và exception handling, độc lập với cách aspect log lỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-008">Đo thời gian thực thi mà không làm thay đổi kết quả nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Đo thời gian thực thi mà không làm thay đổi kết quả nghiệp vụ trong Spring AOP

## 1. Vấn đề cần giải quyết

Bạn muốn biết một method nghiệp vụ mất bao nhiêu thời gian mà không phải chèn các câu lệnh đo vào từng service, đồng thời **không được thay đổi giá trị trả về, kiểu ngoại lệ hay số lần chạy phương thức**. Trong Spring AOP, một advice dạng `@Around` có thể bao quanh method execution trên Spring-managed bean, ghi dấu thời gian trước khi gọi `ProceedingJoinPoint.proceed()` và ghi nhận thời gian sau khi invocation kết thúc.

Mẫu cốt lõi:

~~~java
@Around("execution(public * example.TaskService.*(..))")
public Object measure(ProceedingJoinPoint pjp) throws Throwable {
    long startedAt = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        long elapsedNanos = System.nanoTime() - startedAt;
        // Ghi metrics ở đây, không thay đổi kết quả từ proceed().
    }
}
~~~

**Ba cam kết quan trọng:**

1. **Normal return:** giá trị mà chuỗi interceptor phía trong trả về được chuyển nguyên vẹn tới caller qua `return pjp.proceed()`.
2. **Exceptional exit:** exception từ chuỗi phía trong được truyền ra ngoài; `finally` chạy nhưng advice không tự chuyển exception thành kết quả “thành công”.
3. **Chỉ một invocation:** chỉ gọi `proceed()` **một lần**; không retry, không sửa arguments và không short-circuit nếu mục đích chỉ là đo thời gian.

Bài học chỉ tập trung vào **Spring Aspect và Spring AOP proxy-based**: cách triển khai `@Around`, ranh giới phép đo, metadata metrics và các giới hạn của Spring proxy. Những kiến thức Aspect tổng quát nằm ngoài phạm vi.

## 2. Vì sao `@Around` phù hợp cho bài toán đo thời gian?

Spring Framework mô tả around advice là loại advice có thể thực hiện logic **trước và sau** method execution, đồng thời giữ trạng thái cục bộ theo từng invocation an toàn hơn khi nhiều thread cùng gọi. Đo thời gian cần cùng một cặp mốc bắt đầu/kết thúc, nên `@Around` với biến `startedAt` cục bộ diễn đạt ý định rõ ràng.

| Cách viết | Đặc điểm | Phù hợp với đo duration? |
| --- | --- | --- |
| `@Before` đơn lẻ | Chạy trước invocation | Không có mốc kết thúc trong cùng advice |
| `@After` đơn lẻ | Chạy khi method thoát bình thường hoặc ném lỗi | Không có mốc bắt đầu trong cùng advice |
| Hai advice `@Before` + `@After` dùng field `long start` của singleton aspect | Field có thể bị ghi đè bởi các invocation/thread khác | **Không an toàn** cho nhiều lời gọi đồng thời |
| `@Around` với biến `long startedAt` cục bộ và `try/finally` | Giữ một cặp mốc thời gian theo invocation; ghi nhận cả success/failure | **Nên dùng** |
| `@Around` nhưng bỏ `proceed()` | Không chuyển lời gọi đến chuỗi phía trong | **Sai mục tiêu** nếu chỉ muốn quan sát |
| `@Around` nhưng bắt lỗi và trả giá trị mặc định | Che giấu lỗi nghiệp vụ | **Sai mục tiêu** nếu cần giữ nguyên kết quả |

**Lưu ý:** `@Around` có quyền thay đổi luồng gọi, nhưng trong bài toán đo thời gian thuần túy ta **cố ý không sử dụng** quyền đó. Quan sát là phụ; contract nghiệp vụ của method phải được giữ nguyên.

### 2.1. Sơ đồ thực thi

~~~text
Caller
   |
   v
Spring AOP proxy cho TaskService
   |
   +-- @Around bắt đầu: startedAt = System.nanoTime()
   |
   +-- pjp.proceed()
   |        |
   |        +-- những advisors/interceptors còn lại
   |        |
   |        +-- method target, nếu invocation đến được target
   |        |
   |        +-- trả result hoặc ném Throwable
   |
   +-- finally: elapsed = System.nanoTime() - startedAt
   |             ghi nhận metrics
   |
   v
Caller nhận đúng result hoặc Throwable của đường gọi được bao quanh
~~~

Cụm “đo thời gian thực thi” ở đây cần hiểu là **đo khoảng thời gian lời gọi đi qua đoạn chuỗi Spring AOP nằm bên trong advice**. Nó có thể gồm thời gian của advisor phía trong và target, không mặc nhiên bằng thời gian CPU thực tế chỉ chạy các câu lệnh của thân method.

## 3. Chọn đồng hồ: `System.nanoTime()`, không phải `currentTimeMillis()`

Khi cần đo **thời lượng**, sử dụng:

~~~java
long start = System.nanoTime();
// Gọi thao tác được đo.
long elapsedNanos = System.nanoTime() - start;
~~~

`System.nanoTime()` cung cấp nguồn thời gian phù hợp để đo **khoảng chênh lệch** (*elapsed time*). Mốc trả về là tùy ý, **không phải timestamp** có ý nghĩa lịch/giờ.

Phân biệt:

| API | Mục đích hợp lý | Không nên suy ra |
| --- | --- | --- |
| `System.nanoTime()` | Đo khoảng thời gian giữa hai mốc trong cùng JVM | Không suy ra thời điểm ngày/giờ, không bảo đảm độ chính xác đo đến từng nano-giây |
| `System.currentTimeMillis()` | Lấy thời gian lịch theo epoch millisecond | Không tối ưu để so độ dài vì clock hệ thống có thể bị điều chỉnh |
| `Instant.now()` | Timestamp phục vụ log sự kiện hoặc audit thời điểm | Không phải lựa chọn mặc định cho benchmark elapsed time |

**Đơn vị:** `elapsedNanos` có đơn vị nanosecond theo giá trị API; đổi sang mili-giây bằng:

~~~java
double elapsedMillis = elapsedNanos / 1_000_000.0;
~~~

Không nên làm tròn về số nguyên milli-second cho một method ngắn rồi kết luận “0 ms tức là không tốn thời gian”. Đồng hồ có độ phân giải hữu hạn, overhead của đo đạc/logging và JIT/GC/scheduling đều ảnh hưởng kết quả.

Phép trừ `System.nanoTime() - start` là cách Java khuyến nghị để đo khoảng thời gian ngắn; không so sánh trực tiếp giá trị nanoTime tuyệt đối với một timestamp khác. Không sử dụng cách này làm clock phân tán giữa các JVM hoặc các host.

## 4. Cấu trúc around advice bảo toàn nghiệp vụ

Mẫu đầy đủ ở dạng một class Spring AOP aspect (snippet; dự án chạy độc lập ở mục 6):

~~~java
package example;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TimingAspect {

    private final TimingMetrics metrics;

    public TimingAspect(TimingMetrics metrics) {
        this.metrics = metrics;
    }

    @Around("execution(public * example.TaskService.*(..))")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        String operation = pjp.getSignature().getName();
        long startedAt = System.nanoTime();

        try {
            return pjp.proceed();
        } finally {
            long elapsed = System.nanoTime() - startedAt;
            metrics.record(operation, elapsed);
        }
    }
}
~~~

Trong code này, `TimingMetrics` là một bean lưu số lần gọi và thời gian đo; định nghĩa chính thức nằm ở mục 6. Điểm quan trọng nằm ở **cấu trúc luồng gọi**:

- `@Around` kết nối Spring advice với **method execution** được pointcut chọn.
- Tham số đầu tiên là `ProceedingJoinPoint`.
- `Object` là kiểu trả về được khuyến nghị cho around advice dùng chung nhiều kiểu return.
- `throws Throwable` giúp truyền cả checked lẫn unchecked Throwable do invocation phía trong phát ra.
- `return pjp.proceed();` trả nguyên đối tượng kết quả, không `toString()`, không biến đổi, không wrap/cast.
- `finally` thực hiện ghi nhận trong cả **normal return** và **exceptional exit**.
- Không sửa `pjp.getArgs()`, không dùng `proceed(Object[])`, không gọi `proceed()` nhiều lần.
- `startedAt` và `operation` là biến cục bộ theo invocation nên không có race do cùng một field start-time trong singleton aspect.

**Giới hạn rất quan trọng:** `finally` bảo đảm code ghi nhận **được thử thực thi** trên hai nhánh, nhưng nếu `metrics.record(...)` tự ném exception, nó **có thể thay đổi hành vi mà caller thấy** — chẳng hạn biến một method thành công thành lỗi, hoặc che mất exception gốc. Muốn quan sát thật sự không làm thay đổi contract nghiệp vụ, phải thiết kế metrics sink đủ an toàn và đặt chính sách xử lý lỗi instrumentation rõ ràng (xem mục 8).

## 5. Ba mức “không thay đổi kết quả nghiệp vụ”

Cụm này thường bị hiểu quá hẹp thành “phương thức vẫn trả đúng chuỗi”. Trong Spring AOP có ít nhất ba lớp cần giữ:

### 5.1. Giữ nguyên return value và exception

~~~java
try {
    return pjp.proceed();
} finally {
    metrics.record(operation, System.nanoTime() - startedAt);
}
~~~

Trong trường hợp `metrics.record` hoàn tất bình thường:

- Nếu phần invocation bên trong trả `String "OK"`, caller nhận đúng `"OK"`.
- Nếu phần invocation bên trong trả về **cùng một object instance**, caller nhận chính instance đó; timing advice không tạo một wrapper kết quả mới.
- Nếu phần invocation bên trong ném `IllegalStateException("boom")`, caller nhận cùng exception theo luồng lan truyền thông thường; timing advice không chuyển nó thành `null` hoặc một result giả.
- Nếu target có return type `void`, lời gọi proceed ở tầng `Object` thường trả `null`; advice vẫn giữ semantic của method void.
- Nếu target trả primitive, Spring xử lý boxing/unboxing ở ranh giới thích hợp; lời advice không được trả `null` hoặc kiểu khác thay kết quả primitive.

### 5.2. Giữ nguyên arguments và số lần gọi

~~~java
Object result = pjp.proceed();
return result;
~~~

Đoạn này không thay thế arguments. Nếu gọi `pjp.proceed(modifiedArgs)`, bạn đang thay data của invocation. Nếu gọi `pjp.proceed()` **hai lần**, có thể target được thực thi hai lần và gây side effects. Hai cách đều không phù hợp khi yêu cầu chỉ quan sát.

### 5.3. Không biến metrics thành tác dụng phụ đáng kể

Một advice chỉ “đo” nhưng ghi log đồng bộ lên console, gọi mạng trong mỗi request, ném exception khi exporter lỗi, log toàn bộ arguments hoặc gắn thread state sai cũng có thể ảnh hưởng hành vi vận hành. Do đó:

- Không gửi dữ liệu nhạy cảm vào labels/logs.
- Không dùng metrics backend có thể ném lỗi tùy ý lên caller nếu business cần độc lập với observability.
- Không giữ mutable start-time field dùng chung trên aspect singleton.
- Tránh high-cardinality labels từ user ID, full URL, email, request payload.
- Hạn chế blocking I/O trên hot path.
- Có metrics counter/timer phù hợp với mức độ chính xác cần thiết, không tự suy đoán từ một vài lần đo.

## 6. Ví dụ Java 17+ hoàn chỉnh: đo ba lời gọi và giữ nguyên kết quả

Ví dụ dưới đây có thể chạy trong **Spring Framework 6.2.19**, Java **17+**, Maven, AspectJ Weaver **1.9.25.1**. Đây là một dự án độc lập (không cần Spring Boot, web server, database hoặc test container).

Ba đường gọi cần quan sát:

1. `taskService.greet("Lan")` trả `"Hello Lan"` bình thường.
2. `taskService.fail()` ném `IllegalStateException("boom")`; metrics vẫn nhận một sample.
3. `taskService.callInternally()` gọi `this.greet("Self")`; Spring AOP đo **lời gọi ngoài `callInternally`** nhưng không có sample riêng cho `greet` bên trong do self-invocation.

Để stdout có thể kiểm chứng, project **lưu giá trị nano-giây thật** nhưng chỉ in **nhãn sự kiện** và số lượng samples. Giá trị số nano-giây được lưu trong registry và khác nhau ở mỗi lần chạy, nên không bịa ra một con số duration cố định.

### 6.1. Cấu trúc thư mục

~~~text
spring-aop-timing/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── TaskService.java
    ├── TimingMetrics.java
    ├── TimingAspect.java
    └── Main.java
~~~

### 6.2. Maven: `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-aop-timing</artifactId>
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

Spring Framework BOM giúp những dependency `spring-*` tương thích phiên bản. Project Spring Boot thực tế nên để Boot BOM hoặc starter quản lý dependency, không sao chép version của demo một cách máy móc.

### 6.3. `TaskService.java`

~~~java
package example;

public class TaskService {

    public String greet(String name) {
        System.out.println("target: greet " + name);
        return "Hello " + name;
    }

    public void fail() {
        System.out.println("target: fail");
        throw new IllegalStateException("boom");
    }

    public String callInternally() {
        System.out.println("target: callInternally");
        return this.greet("Self");
    }
}
~~~

`TaskService` là target Spring bean; không có logic đo thời gian trong service. `greet` và `fail` để kiểm tra normal/exception flows; `callInternally` thể hiện giới hạn self-invocation Spring AOP.

### 6.4. `TimingMetrics.java` — lưu duration và sample count an toàn cho concurrency

~~~java
package example;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

public final class TimingMetrics {

    private final ConcurrentMap<String, LongAdder> counts =
        new ConcurrentHashMap<>();

    private final ConcurrentMap<String, LongAdder> totalNanos =
        new ConcurrentHashMap<>();

    public void record(String operation, long elapsedNanos) {
        counts.computeIfAbsent(operation, key -> new LongAdder()).increment();
        totalNanos.computeIfAbsent(operation, key -> new LongAdder())
            .add(elapsedNanos);

        // Chỉ in nhãn để ví dụ có stdout ổn định.
        System.out.println("metric: " + operation + " recorded");
    }

    public long count(String operation) {
        LongAdder value = counts.get(operation);
        return value == null ? 0L : value.sum();
    }

    public long totalNanos(String operation) {
        LongAdder value = totalNanos.get(operation);
        return value == null ? 0L : value.sum();
    }
}
~~~

**Lý do thiết kế:**

- `LongAdder` phù hợp để tích lũy counters/tổng thời gian trong các thao tác concurrent đơn giản; tránh một mutable `long` bị race khi nhiều thread ghi.
- `ConcurrentHashMap` cho phép sử dụng đồng thời nhiều operation names.
- `record` chỉ được gọi từ timing aspect khi invocation đi tới nhánh finally.
- `totalNanos(operation)` giữ **giá trị số thật** của tổng duration; in số này ra console sẽ khác nhau giữa các lần chạy.
- Đây là instrumentation toy registry: việc cập nhật **hai map độc lập không phải một transaction nguyên tử**. Nếu cần snapshot count và duration đồng thời nhất quán hoặc histogram/percentiles chính xác, dùng thư viện observability/metrics chuyên dụng.
- Hàm `record` in log để minh họa; **production không nên System.out.println ở mọi invocation**, nhất là hot path.
- Một out-of-memory error hoặc exception hiếm trong chính metrics subsystem vẫn có thể làm sai contract; bài này dùng sink in-memory nhỏ để minh họa trường hợp thành công bình thường, không hứa “không thể xảy ra lỗi instrumentation”.

### 6.5. `TimingAspect.java` — chỉ đo, không chỉnh business result

~~~java
package example;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TimingAspect {

    private final TimingMetrics metrics;

    public TimingAspect(TimingMetrics metrics) {
        this.metrics = metrics;
    }

    @Around("execution(public * example.TaskService.*(..))")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        String operation = pjp.getSignature().getName();
        long startedAt = System.nanoTime();

        try {
            return pjp.proceed();
        } finally {
            long elapsedNanos = System.nanoTime() - startedAt;
            metrics.record(operation, elapsedNanos);
        }
    }
}
~~~

Ở đây:

- `@Aspect` đánh dấu class có Spring AOP advice; nó **không tự khiến class thành Spring bean**.
- `@Around` chọn các **public method executions** thuộc `example.TaskService`, bao gồm `greet`, `fail`, `callInternally`.
- `pjp.proceed()` được gọi đúng **một lần**.
- `return` chuyển nguyên kết quả của chain phía trong về caller.
- Không catch exception, không tạo exception mới, không đổi arguments hoặc target.
- `finally` ghi nhận metric ở cả hai nhánh thoát của invocation **đã đi vào advice**.
- `operation` chỉ là method name cố định của class, không chứa arguments hay nội dung nhạy cảm. Với nhiều service hoặc overloaded methods cần tên được chuẩn hóa rõ hơn để tránh gộp nhầm các phép đo.

**Ranh giới đo:** Mốc kết thúc được lấy **trước** lệnh `metrics.record`, nên duration không gồm chi phí ghi metric của chính advice. Nó vẫn có thể gồm thời gian của những interceptors nằm **bên trong** advice và công việc target.

### 6.6. `AppConfig.java`

~~~java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public TaskService taskService() {
        return new TaskService();
    }

    @Bean
    public TimingMetrics timingMetrics() {
        return new TimingMetrics();
    }

    @Bean
    public TimingAspect timingAspect(TimingMetrics timingMetrics) {
        return new TimingAspect(timingMetrics);
    }
}
~~~

Spring Framework thuần cần kích hoạt `@EnableAspectJAutoProxy` và có thư viện AspectJ cần thiết trên classpath. `TimingAspect` được đăng ký **qua `@Bean`**, không cần thêm `@Component`. Target được Spring đăng ký và nhận proxy khi có advisor match.

`proxyTargetClass = true` ép class-based proxy để chương trình có thể lấy bean theo `TaskService.class`. CGLIB được chọn vì thuận tiện demo; nó **không làm self-invocation chạy timing advice**.

### 6.7. `Main.java`

~~~java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {
            TaskService task = context.getBean(TaskService.class);
            TimingMetrics metrics = context.getBean(TimingMetrics.class);

            System.out.println("spring AOP proxy: " + AopUtils.isAopProxy(task));

            System.out.println("-- success --");
            System.out.println("caller: " + task.greet("Lan"));

            System.out.println("-- exception --");
            try {
                task.fail();
            } catch (IllegalStateException ex) {
                System.out.println("caller caught: " + ex.getMessage());
            }

            System.out.println("-- self invocation --");
            System.out.println("caller: " + task.callInternally());

            System.out.println("count greet: " + metrics.count("greet"));
            System.out.println("count fail: " + metrics.count("fail"));
            System.out.println("count callInternally: "
                + metrics.count("callInternally"));
            System.out.println("total samples: "
                + (metrics.count("greet") + metrics.count("fail")
                   + metrics.count("callInternally")));
        }
    }
}
~~~

Chạy tại thư mục có `pom.xml`:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
~~~

### 6.8. Stdout xác định được từ code

~~~text
spring AOP proxy: true
-- success --
target: greet Lan
metric: greet recorded
caller: Hello Lan
-- exception --
target: fail
metric: fail recorded
caller caught: boom
-- self invocation --
target: callInternally
target: greet Self
metric: callInternally recorded
caller: Hello Self
count greet: 1
count fail: 1
count callInternally: 1
total samples: 3
~~~

**Vì sao không in duration cố định?** Giá trị `System.nanoTime()` phụ thuộc môi trường chạy, JIT, GC, scheduling, trạng thái CPU và các tầng interceptor; không thể dự đoán chính xác. Giá trị thật được lưu trong `TimingMetrics.totalNanos("greet")` v.v., người học có thể tự in chúng khi chạy để quan sát.

### 6.9. Giải thích ba đường gọi

**Lời gọi thành công:**

1. Caller gọi `task.greet("Lan")` qua Spring AOP proxy.
2. Timing aspect ghi `startedAt` rồi `proceed()`.
3. Target in `target: greet Lan` và trả `"Hello Lan"`.
4. Finally đo elapsed, gọi `metrics.record("greet", ...)` và in nhãn metric.
5. Caller nhận đúng `"Hello Lan"`: timing advice **không sửa return value**.

**Lời gọi ném exception:**

1. Proxy vào timing aspect và sau đó chạy target `fail()`.
2. Target ném `IllegalStateException("boom")`.
3. `finally` vẫn ghi một sample `fail`.
4. Cùng exception tiếp tục đi tới caller và được bắt ở `Main`.
5. Timing aspect **không biến lỗi thành một giá trị mặc định**, nên hành vi lỗi nghiệp vụ được giữ nguyên.

**Self-invocation:**

1. Caller gọi `task.callInternally()` qua proxy, nên timing aspect bắt đầu một phép đo cho method **callInternally**.
2. Bên trong target, code gọi `this.greet("Self")` — **không đi qua proxy lần hai**.
3. Do vậy không có dòng `metric: greet recorded` thứ hai; chỉ có `metric: callInternally recorded`.
4. Caller nhận `"Hello Self"` như bản thân business method trả về. Sau ba lời gọi ngoài, count `greet=1`, `fail=1`, `callInternally=1`.

Điểm mấu chốt: **một lời gọi nội bộ vẫn tiêu tốn thời gian thực thi**, nhưng vì không tạo thêm một invocation qua proxy cho method con, nó **không sinh thêm một timing sample riêng** theo pointcut.

## 7. Đo thời gian thành công và thất bại mà không đổi exception

Có hai cách thường thấy.

### 7.1. `try/finally` — đo cả hai nhánh, không phân biệt outcome

~~~java
long start = System.nanoTime();
try {
    return pjp.proceed();
} finally {
    metrics.record(operation, System.nanoTime() - start);
}
~~~

Đơn giản, phù hợp khi chỉ cần **duration và số lượt method đã đi qua advice**, không cần nhãn success/failure. Với ví dụ, case `fail()` có một sample dù target ném exception.

### 7.2. `try/catch/finally` — nếu metrics còn cần trạng thái kết quả

~~~java
// Snippet bổ sung; metrics.record là API minh họa, không phải project mục 6.
long started = System.nanoTime();
boolean failed = false;
try {
    return pjp.proceed();
} catch (Throwable error) {
    failed = true;
    throw error;
} finally {
    recordOutcome(operation, System.nanoTime() - started, failed);
}
~~~

Trong snippet, `recordOutcome(...)` là hàm giả định của metrics subsystem (cần được định nghĩa riêng). Đây **không phải mã độc lập** để sao chép và chạy ngay. Nó minh họa nguyên tắc:

- Nếu `proceed()` ném `Throwable` thì gắn outcome=failed.
- **Rethrow chính Throwable đó**, không tạo exception khác nếu mục tiêu là preserve behavior.
- `finally` vẫn chạy một lần.
- Tránh log toàn bộ exception message hoặc arguments, vì có thể chứa dữ liệu nhạy cảm.

**Sắc thái:** Nếu target tự bắt exception và return bình thường thì từ góc nhìn outer proxy, `proceed()` trả bình thường: state “failed” trong snippet là `false`. Nếu một interceptor ở trong bắt exception rồi trả fallback thì outer cũng quan sát fallback (không biết lỗi phía trong đã được xử lý). Muốn phân biệt cấp độ nghiệp vụ cần một hợp đồng trạng thái riêng chứ không suy từ Throwable duy nhất.

## 8. Tránh lỗi instrumentation làm thay đổi nghiệp vụ

Mặc dù `return pjp.proceed()` và `finally` đúng về luồng, **code đo/ghi metric cũng là Java code**, có thể gây lỗi hoặc blocking. Ví dụ:

~~~java
finally {
    metricsBackend.sendHttp(elapsedNanos); // Có thể timeout hoặc throw!
}
~~~

Nếu backend metrics ném exception, caller có thể thấy lỗi instrumentation thay vì kết quả/exception của target. Để giữ nghiệp vụ độc lập, có thể sử dụng **best-effort instrumentation** với một sink được thiết kế non-throwing, đo đạc rõ ràng và đường ghi có backpressure/timeout phù hợp.

Snippet minh họa cho lỗi thông thường:

~~~java
finally {
    long elapsed = System.nanoTime() - startedAt;
    try {
        metrics.record(operation, elapsed);
    } catch (RuntimeException instrumentationFailure) {
        // Best effort: báo lỗi bằng kênh an toàn, không làm hỏng nghiệp vụ.
        // Không log vô hạn hoặc truy xuất mạng đồng bộ ở đây.
    }
}
~~~

**Lưu ý thật rõ:**

- Đoạn trên **cố ý không nuốt Throwable từ `proceed()`**; nó chỉ xử lý lỗi của phần metrics trong `finally`.
- Một `Error` nghiêm trọng như OutOfMemoryError hoặc JVM termination không thể được hứa sẽ xử lý “an toàn tuyệt đối” bằng một `catch(RuntimeException)`.
- Empty catch không nên được sao chép vào production mà thiếu chính sách observability; cần một kênh giám sát không làm hỏng luồng nghiệp vụ, nhưng không tái tạo lỗi vào caller.
- Sử dụng metric recording nhanh, thread-safe và ít side effects. Với Java service thật nên cân nhắc **Micrometer Observation/Timer** hoặc SDK observability của hệ thống, thay vì tự làm bộ đếm in-memory cho production.
- Việc in log ngay trong `record()` của ví dụ nhỏ là để giúp học cơ chế; không phải thiết kế hiệu năng cho endpoint thường xuyên được gọi.

Như vậy “không làm thay đổi kết quả nghiệp vụ” là **một mục tiêu thiết kế có điều kiện**, không phải điều được `@Around` tự động bảo đảm trong mọi tình huống.

## 9. Dữ liệu nào nên gắn vào metric?

Các label gợi ý:

| Label | Ví dụ | Đánh giá |
| --- | --- | --- |
| Operation name cố định | `TaskService.greet` | Thường phù hợp, cardinality hữu hạn |
| Outcome | `success` / `failure` | Phù hợp nếu xác định rõ tại proxy boundary |
| Service/module | `payment`, `checkout` | Phù hợp với tập giá trị hữu hạn |
| Exception type | `IllegalStateException` | Có thể cân nhắc, nhưng kiểm soát cardinality |
| User email hoặc request payload | `user@example.com`, JSON body | **Không đưa vào metrics label** |
| Customer ID, order ID, full dynamic URL | Giá trị duy nhất mỗi request | Tránh high cardinality, nguy cơ lộ dữ liệu |
| Raw argument có thông tin bí mật | Token/password/API key | **Không log hoặc gắn metrics** |

Trong Spring Aspect, `pjp.getSignature().getName()` là cách đơn giản lấy tên method, nhưng method overload cùng tên sẽ chia sẻ một label. Trong production, bạn có thể chuẩn hóa key theo **class/method được allowlist** hoặc annotation nghiệp vụ riêng, thay vì nối `pjp.getArgs()` hoặc `toString()` của object đầu vào.

**Không sử dụng full arguments để “phân biệt” mỗi metric sample**: điều đó làm cardinality nở theo số request, gây quá tải metric backend và có thể tiết lộ dữ liệu.

## 10. Đặt pointcut đủ hẹp, tránh đo cả hạ tầng Spring

Trong ví dụ, pointcut là:

~~~java
@Around("execution(public * example.TaskService.*(..))")
~~~

Nó giới hạn vào các public method executions của class `TaskService`, không match mọi bean trong context. Một expression quá rộng như:

~~~java
@Around("execution(* *(..))")
~~~

có thể làm Spring xét/match rất nhiều target methods không thuộc nhu cầu profiling, thêm proxy hoặc overhead ngoài ý muốn. Trong app lớn, nó còn khiến log/metrics bị trộn giữa nghiệp vụ và infrastructure.

Một số cách xây scope rõ hơn:

~~~java
// Snippet trong một Spring-managed @Aspect:
@Around(
    "within(com.acme.payment.service..*)"
    + " && execution(public * *(..))"
)
public Object timePaymentService(ProceedingJoinPoint pjp) throws Throwable {
    long started = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        recordPaymentLatency(System.nanoTime() - started);
    }
}
~~~

Trong snippet này, `recordPaymentLatency` là method minh họa cần định nghĩa ở class aspect; đoạn code **không phải project độc lập**.

Nếu chỉ một số method được đánh dấu rõ, có thể dùng một custom annotation opt-in:

~~~java
@Around("@annotation(com.acme.metrics.TimedOperation)")
~~~

Đây chỉ là biểu thức annotation placeholder cho một annotation type do dự án tự định nghĩa (cần `@Target(METHOD)`, `@Retention(RUNTIME)`). Khi dùng JDK proxy hoặc annotation trên interface/implementation, cần **test match thực tế** trong Spring AOP context; đừng dựa vào tên annotation ở source mà suy ra tự động match trong mọi chiến lược proxy.

**Quy tắc:** Chọn cả positive targets và negative targets. Một test chỉ xác nhận “TaskService.greet được đo” chưa chứng minh không có bean khác bị đo nhầm.

## 11. Đo thời gian ở vị trí nào trong chuỗi advisor?

Một Spring-managed bean có thể chịu nhiều advisors:

~~~text
Client
 -> [Timing @Around advisor]
     -> [Transaction interceptor]
         -> [Cache / Security / Other interceptors]
             -> TargetService.method()
~~~

Nếu timing advice ở **ngoài** transaction interceptor, khoảng thời gian measured có thể bao gồm cả thời gian begin/commit/rollback được thực hiện **trong** transaction interceptor, tùy advisor order.

Nếu timing advice ở **trong** transaction interceptor:

~~~text
Client
 -> [Transaction interceptor]
     -> [Timing @Around advisor]
         -> TargetService.method()
~~~

thì phép đo có thể **không bao gồm** một số chi phí mở/đóng transaction ở ngoài advice. Vì vậy cùng một service có thể có “method duration” và “transaction-inclusive duration” khác nhau, không phải vì hệ đo bị hỏng.

### 11.1. `@Order`

Spring AOP cho phép sắp xếp aspect bằng `@Order` hoặc `Ordered`:

~~~java
@Aspect
@Order(10)
public class TimingAspect {
    // ...
}
~~~

Đây là snippet, cần import `org.springframework.core.annotation.Order` và class body hợp lệ. Số order **nhỏ hơn** có precedence cao hơn đối với aspects; trên đường đi vào, aspect precedence cao hơn chạy trước, và trên đường quay ra nó chạy sau.

Tuy vậy, thứ tự với infrastructure advisors như `@Transactional`, `@Cacheable` còn phụ thuộc cấu hình Spring thực tế. Không giả định `@Order(10)` có nghĩa “luôn đo cả transaction” trong mọi context. Hãy ghi rõ định nghĩa latency, sau đó xác minh advisor chain và hành vi qua integration test.

### 11.2. Đo thời gian thực thi target hay đo latency caller quan sát?

Cần phân biệt:

- **Target body duration:** lý tưởng chỉ tính thời gian trong code nghiệp vụ.
- **Inner chain duration:** tính target cùng một số interceptor phía trong timing advice.
- **End-to-end request latency:** có thể gồm HTTP routing, authentication, serialization, network waits và hàng đợi không thuộc phạm vi method advice.
- **Async completion latency:** tính đến lúc future/publisher/task thật sự hoàn thành.

`System.nanoTime()` quanh `pjp.proceed()` trên Spring AOP đo **một lời gọi method theo ranh giới proxy**, không tự đại diện cho tất cả bốn chỉ số. Nên đặt tên metric phản ánh đúng phạm vi đo để tránh diễn giải sai dashboard.

## 12. Spring AOP proxy boundaries: trường hợp không được đo riêng

### 12.1. Self-invocation qua `this`

~~~java
public String outer() {
    return this.inner();
}
~~~

Nếu caller gọi `outer()` trên Spring proxy, timing aspect có thể đo lời gọi `outer`. Nhưng trong target, `this.inner()` **không đi qua proxy lần nữa**, nên `inner` không tự có một timing sample riêng. Ví dụ mục 6 cho thấy rõ điều này qua `callInternally()`.

CGLIB **không chữa** self-invocation theo cơ chế Spring AOP proxy-based. Nếu phải đo riêng `inner`, một cách thông dụng là tách nó sang **Spring bean khác** và gọi qua Spring-injected proxy; cần kiểm tra pointcut match đúng bean mới.

### 12.2. Tự `new` instance ngoài Spring

~~~java
TaskService service = new TaskService();
service.greet("Lan");
~~~

Method nghiệp vụ vẫn chạy, nhưng đối tượng này không phải bean proxy mà Spring AOP tạo. Timing advice trên Spring context **không tự áp dụng** lên object tự new ngoài container.

### 12.3. Method không thể proxy hóa hoặc pointcut không match

- JDK dynamic proxy chủ yếu can thiệp các public method trên interfaces được proxy expose.
- Class-based CGLIB không thể override method `final` và không thể intercept `private` theo cơ chế subclass.
- Nếu pointcut chọn `TaskService.greet(..)`, các method khác không tự nhận metric chỉ vì bean đã có proxy.
- Bean có AOP proxy từ transaction/cache/advisor khác **không đồng nghĩa** timing advice đang match.

### 12.4. Inner interceptor short-circuit

Một `@Around`/cache interceptor nằm **bên trong** timing advice có thể trả kết quả mà không gọi target. Timing aspect vẫn có thể ghi một sample cho **interceptor-chain latency**, vì `proceed()` đã đi tới inner interceptor và trả về. Điều đó **không chứng minh target đã thực thi**.

Nếu timing advice nằm **bên trong** interceptor shortcut và inner timing layer chưa được đi vào, **không có sample**. Đây là lý do phải hiểu advisor order khi định nghĩa metric.

## 13. Method bất đồng bộ và reactive: duration nào được ghi nhận?

Một method Spring bean có thể trả về `CompletableFuture<T>`, `Future<T>`, `Mono<T>` hoặc `Flux<T>`. Đặc biệt:

~~~java
public CompletableFuture<String> startJob() {
    return CompletableFuture.supplyAsync(() -> performBusinessJob());
}
~~~

Đây là snippet minh họa, giả định `performBusinessJob()` tồn tại. Với một method trả CompletableFuture **nhanh**, `pjp.proceed()` có thể trả về **future object** trước khi job thực sự hoàn tất; `finally` trong `@Around` lập tức ghi duration cho **thời gian method trả future**.

Tương tự, một method reactive trả về `Mono`/`Flux` có thể chỉ tạo publisher; công việc bắt đầu khi subscriber subscribe. Duration đo bằng `proceed()` **không tự bằng thời gian publisher hoàn tất**.

**Cần xác định đo cái gì:**

| Chỉ số mong muốn | Cách phù hợp |
| --- | --- |
| Thời gian method tạo/trả Future hoặc Publisher | `@Around` quanh `proceed()` |
| Thời gian cho đến Future hoàn thành | Hook hoàn tất như `whenComplete` hoặc công cụ instrumentation của async abstraction |
| Thời gian đến khi một reactive pipeline kết thúc/cancel | Operator lifecycle như Reactor `doFinally` trong pipeline phù hợp |
| Thời gian end-to-end của web request | HTTP/server observation thay vì chỉ method advice |

Không tự attach callbacks vào một Future rồi thay thế return object nếu hợp đồng business yêu cầu giữ chính object gốc; instrumentation phải xem xét identity và exception/timeout semantics. Với method `@Async` của Spring, advisor ordering và kiểu proxy cũng có thể làm metric scope khác với dự tính; cần kiểm thử trong context thật.

## 14. Ngưỡng hiệu năng, phân phối thời gian và độ chính xác

Đo thời gian một lần chưa đủ kết luận tối ưu hóa. Đặc biệt đối với code ngắn:

- JIT compilation, class loading và warm-up làm những lần đầu thường khác các lần sau.
- Garbage collection và OS scheduling ảnh hưởng wall-clock elapsed time.
- `System.nanoTime()` có overhead; precision được biểu diễn bằng nanoseconds nhưng resolution thực tế tùy nền tảng.
- Trong ứng dụng nhiều thread, mỗi invocation có state start/end riêng nhưng việc tổng hợp metrics vẫn có chi phí.
- Average không đủ cho tail latency; có thể cần p95/p99 hoặc histogram nếu yêu cầu SLO.
- Cache hit và cache miss có thể làm phân phối thời gian khác nhau đáng kể; nên có nhãn outcome hữu hạn và phân nhóm đúng ngữ nghĩa.
- Thời gian của transaction commit, remote I/O và queueing có thể nằm ngoài hoặc trong khoảng đo tùy vị trí advisor.

Với production, sử dụng thư viện được quản lý như **Micrometer Timer/Observation** phù hợp Spring Boot ecosystem sẽ hữu ích hơn tự triển khai nhiều aggregator. Nhưng nguyên tắc Spring AOP vẫn là: `@Around` mở mốc, `proceed()`, đo trong `finally`, và **không thay return/throw contract**.

## 15. Kiểm thử “không làm thay đổi nghiệp vụ”

Một bài test đầy đủ phải kiểm cả **kết quả nghiệp vụ** lẫn **số lượng samples**.

| Test case | Kỳ vọng nghiệp vụ | Kỳ vọng timing |
| --- | --- | --- |
| Caller gọi `task.greet("Lan")` qua proxy | Đúng chuỗi `"Hello Lan"` | `count("greet")` tăng một |
| Caller gọi `task.fail()` qua proxy | Exception `IllegalStateException("boom")` tiếp tục ném | `count("fail")` tăng một |
| Caller gọi `task.callInternally()` | Trả `"Hello Self"` | `count("callInternally")` tăng một; `count("greet")` **không** tăng thêm |
| Caller tạo `new TaskService()` và gọi `greet()` | Kết quả Java bình thường | Không ghi thêm vào metrics của Spring context |
| Một method khác không nằm trong pointcut | Kết quả nghiệp vụ bình thường | Không có timing sample cho method đó |
| Metrics sink báo lỗi | Kết quả mong muốn do chính sách instrumentation quyết định | Test xác minh lỗi không làm hỏng business theo policy đã định |
| Gọi concurrent | Mỗi lời gọi có kết quả đúng | Số samples bằng số invocation qua proxy; counter không lost updates |
| Method trả Future | Future object/behavior không bị đổi | Duration quanh `proceed()` không mặc nhiên đo đến async completion |

### 15.1. Assertions minh họa trong context

Sau khi tạo `AnnotationConfigApplicationContext` và lấy `TaskService` cùng `TimingMetrics` như mục 6, ta có thể kiểm tra:

~~~java
String result = task.greet("Mai");
if (!"Hello Mai".equals(result)) {
    throw new AssertionError("return value changed");
}
if (metrics.count("greet") != 1L) {
    throw new AssertionError("wrong timing sample count");
}
~~~

Đây là **snippet kiểm thử bổ sung**, không phải chương trình độc lập và không được trộn trực tiếp vào `Main.java` nếu bạn muốn giữ stdout ở mục 6. Với exception, dùng `try/catch` hoặc `assertThrows` trong JUnit; test cả exception type và message/identity khi contract yêu cầu.

**Không assert giá trị nano-giây chính xác**: duration là số đo runtime. Nếu cần kiểm tra phương pháp ghi metric hoàn toàn xác định, có thể inject một clock abstraction giả lập và một metrics sink in-memory cho test đơn vị, nhưng integration test vẫn nên chứng minh advice thực sự được Spring AOP áp dụng.

## 16. Những lỗi Spring AOP profiling phổ biến

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| Method trả `null` dù target vốn có kết quả | `@Around` quên `return pjp.proceed()` hoặc method advice khai báo `void` | Trả nguyên value từ proceed |
| Không thấy metric và target cũng không chạy | Around advice return sớm, không gọi proceed | Kiểm tra điều kiện shortcut, không dùng shortcut cho timing-only advice |
| Lỗi từ target bị biến thành giá trị mặc định | Around advice `catch` nhưng không rethrow | Rethrow original Throwable nếu cần preserve exception contract |
| Exception từ metrics thay thế exception gốc | `metrics.record` ném lỗi trong `finally` | Thiết kế sink best-effort và policy lỗi instrumentation |
| Thời gian của mọi thread trộn lẫn sai | Lưu `startTime` làm field mutable chung trên singleton aspect | Dùng local variable trong around advice |
| Count ghi hai lần cho một method | Gọi `proceed()` hai lần, đăng ký aspect trùng hoặc có nested proxies | Kiểm tra invocation/advisor chain và lần gọi thật |
| Target method chưa chạy nhưng vẫn có metric | Inner interceptor/cache short-circuit; timing advice đo chain | Xác định metric scope và advisor order |
| `this.method()` không có timing sample riêng | Spring AOP self-invocation bỏ qua proxy | Tách method thành bean khác và gọi qua DI nếu cần |
| Bean không được đo dù có `@Aspect` | Aspect chưa thành Spring bean, AOP chưa bật hoặc pointcut không khớp | Kiểm tra `@Bean`/`@Component`, `@EnableAspectJAutoProxy`/Boot auto-config |
| Value `elapsedMillis` in thành 0 | Ép số nguyên làm mất độ phân giải với operation ngắn | Lưu nano-giây, dùng phân số khi chuyển đơn vị |
| Stopwatch dùng `currentTimeMillis` đôi lúc có số âm/bất thường | Clock hệ thống bị điều chỉnh; không phù hợp làm elapsed timer | Dùng `System.nanoTime()` |
| Timing sau `proceed()` nhưng không trong `finally` | Exceptional exit làm bỏ qua record | Đặt record trong finally |
| Timing của async task quá ngắn | `proceed()` chỉ trả Future/Publisher | Đo completion bằng lifecycle callback phù hợp |
| Cardinality tăng không kiểm soát | Dùng user ID/request body làm metric label | Chuẩn hóa labels hữu hạn |
| Tăng latency nhiều sau khi thêm metric | Ghi log/I/O đồng bộ hoặc pointcut quá rộng | Giảm overhead, dùng metrics backend phù hợp và narrow pointcut |

## 17. Câu hỏi vận dụng

**Câu 1.** Vì sao đoạn `long start=System.nanoTime(); return pjp.proceed();` chưa đo hoàn chỉnh khi target có thể ném exception?

**Đáp án:** Chưa ghi nhận mốc kết thúc. Nếu ghi record bằng câu lệnh sau proceed mà không có finally, exceptional exit sẽ bỏ qua câu lệnh đó. Dùng `try/finally` để record cả hai nhánh.

**Câu 2.** Aspect timing trả `"OK"` bất kể `pjp.proceed()` trả gì. Có đáp ứng “không thay đổi kết quả nghiệp vụ” không?

**Đáp án:** Không. Nó thay return value của caller. Timing-only advice phải trả nguyên kết quả của proceed.

**Câu 3.** `pjp.proceed()` đã được gọi một lần, nhưng timing sample có thể xuất hiện khi target không chạy không?

**Đáp án:** Có. Một interceptor phía trong, chẳng hạn cache, có thể trả kết quả sớm. Timing đo khoảng thời gian chuỗi bên trong mà advice đi qua, không tự chứng minh target body đã chạy.

**Câu 4.** Trong singleton aspect, lưu `startTime` ở một field rồi @Before set, @After subtract. Cách này có an toàn concurrency không?

**Đáp án:** Không nếu field dùng chung cho nhiều invocation. Một thread có thể ghi đè start time của thread khác. Dùng biến cục bộ trong một @Around advice.

**Câu 5.** Target ném `IllegalStateException("boom")`; metrics record thành công trong finally. Caller có còn nhận exception không?

**Đáp án:** Có, nếu timing advice không catch/đổi exception. Finally record xong và exception gốc tiếp tục lan truyền.

**Câu 6.** `metrics.record(...)` ném RuntimeException trong finally sau khi target đã trả về bình thường. Caller có chắc chắn nhận kết quả không?

**Đáp án:** Không. Lỗi từ instrumentation có thể thay thế kết quả. Để giữ contract, metrics sink và chính sách xử lý lỗi phải tránh propagate lỗi đo đạc ngoài ý muốn.

**Câu 7.** `callInternally()` gọi `this.greet()` và được caller gọi qua Spring proxy. Timing có đếm riêng `greet` bên trong không?

**Đáp án:** Không. Self-invocation không tạo một lời gọi mới qua proxy cho greet, nên chỉ có sample của method ngoài nếu advisor match nó.

**Câu 8.** Có nên đo thời gian giữa hai lần `System.currentTimeMillis()` nếu yêu cầu độ ổn định của elapsed time không?

**Đáp án:** Nên dùng `System.nanoTime()` cho elapsed duration. Clock lịch có thể bị hiệu chỉnh và thường có độ phân giải thấp hơn.

**Câu 9.** Nếu advisor timing ở ngoài transaction interceptor, mẫu đo có thể gồm transaction commit không?

**Đáp án:** Có thể có, tùy các interceptor nằm bên trong timing advice. Muốn định nghĩa “method time” hay “transaction-inclusive time” thì phải kiểm tra advisor order và cách transaction thực sự thực thi.

**Câu 10.** Method trả `CompletableFuture`, timing finally đã chạy nhưng future còn pending. Có lỗi không?

**Đáp án:** Không. Nó đo thời điểm method trả Future, không nhất thiết là hoàn tất công việc async. Dùng completion instrumentation nếu cần latency của toàn bộ tác vụ.

## 18. Tài liệu chính thức và hướng tìm hiểu tiếp

- [Spring Framework 6.2 — Declaring Advice / Around Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): around advice dùng `ProceedingJoinPoint`, tiến hành `proceed()`, lưu state theo invocation và bảo toàn return value.
- [Spring Framework — An AOP Example](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/example.html): quanh method để ghi profiling và điều phối behavior.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): ranh giới Spring proxies, JDK/CGLIB và self-invocation.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): bật Spring AOP, yêu cầu aspect bean và classpath.
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html): giới hạn method execution để không tạo overhead ngoài phạm vi.
- [Oracle Java SE API — System.nanoTime()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#nanoTime()): clock dùng đo elapsed time, mốc tùy ý, độ phân giải phụ thuộc nền tảng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-009">Rủi ro lặp thao tác có tác dụng phụ khi triển khai retry bằng aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Rủi ro lặp thao tác có tác dụng phụ khi triển khai retry bằng aspect trong Spring AOP

## 1. Bản chất của vấn đề

Trong **Spring AOP**, một phương thức advice mang `@Around` có thể gọi `ProceedingJoinPoint.proceed()` nhiều lần. Đây là nền tảng để tạo logic **retry**: khi target ném exception, advice gọi lại continuation của Spring proxy. Tuy nhiên, **mỗi lần proceed có thể chạy lại toàn bộ target method, bao gồm những thao tác đã có tác dụng phụ**.

Ví dụ quy trình thanh toán:

~~~text
Client -> Spring service proxy
          -> RetryAspect @Around
             -> proceed #1
                -> gọi hệ thống thanh toán
                -> hệ thống đã ghi nợ
                -> response/ACK bị mất, ném timeout
             -> catch timeout
             -> proceed #2
                -> hệ thống có thể ghi nợ lần nữa
             -> caller nhận một kết quả thành công
~~~

Người dùng thấy **một request trả thành công** không chứng minh chỉ có **một lần ghi nợ**. Đây là nguy cơ quan trọng nhất khi viết retry bằng `@Aspect`.

Sau bài này, bạn sẽ:

1. Hiểu chính xác `proceed()` làm gì trong Spring AOP, vì sao retry có thể lặp cả target và các interceptor bên trong.
2. Phân biệt lỗi **trước side effect**, **sau side effect**, và **không biết side effect đã commit chưa**.
3. Viết bounded retry chỉ bắt lỗi được phân loại cụ thể, không nuốt lỗi cuối hoặc trả thành công giả.
4. Hiểu sự khác biệt giữa **idempotency**, retry attempt và transaction rollback.
5. Chạy ví dụ Spring Framework thuần Java 17+ so sánh luồng **không chống trùng** và luồng có **idempotency key trong một JVM**.
6. Phân tích thứ tự Spring retry aspect và transaction advice, self-invocation, pointcut, concurrency, timeout và observability.
7. Biết khi nào phải yêu cầu idempotency/reconciliation phía hệ thống đích thay vì retry mù.

**Phạm vi:** Chỉ bàn về `@Around` trong **Spring-managed `@Aspect`/Spring AOP proxy**. Những nguyên tắc idempotency và distributed systems được giải thích ở mức cần thiết để thiết kế aspect đúng, không thay thế tài liệu giao dịch/thanh toán chuyên biệt.

## 2. Một proxy invocation không đồng nghĩa một lần vào target

Spring AOP sử dụng JDK dynamic proxy hoặc class-based CGLIB proxy. Khi client gọi một method qua proxy và pointcut khớp, advice `@Around` có thể kiểm soát continuation:

~~~text
Caller
  |
  +--> Spring AOP Proxy
           |
           +--> RetryAspect
                   |
                   +--> pjp.proceed(): attempt 1
                   |          |
                   |          +--> inner advice (nếu có)
                   |          +--> Target.method()
                   |          +--> side effect có thể đã xảy ra
                   |          +--> ném TemporaryFailureException
                   |
                   +--> catch exception phù hợp
                   |
                   +--> pjp.proceed(): attempt 2
                              |
                              +--> inner advice chạy lại
                              +--> Target.method() chạy lại
                              +--> side effect có thể lặp
~~~

Theo tài liệu Spring Framework, `proceed()` trong `@Around` có thể gọi **không lần nào, một lần hoặc nhiều lần**. Điều đó hợp lệ về kỹ thuật, nhưng Spring **không tự đánh giá target có an toàn để thực thi hai lần không**.

Một đoạn mã minh họa:

~~~java
@Around("execution(* demo.ChargeApi.charge(..))")
public Object retry(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (TemporaryFailureException first) {
        return pjp.proceed();
    }
}
~~~

Đây là **fragment trong class aspect**, cần thêm các imports, khai báo exception, bean service và cấu hình Spring AOP. Nó có thể gọi target tối đa hai lần nhưng chưa có idempotency, chính sách exception đầy đủ, backoff, transaction ordering hay giới hạn tổng thời gian. Không sử dụng trực tiếp cho lệnh ghi nợ hoặc gửi message thật.

Điểm cần phân biệt:

- **Client proxy invocation count:** người gọi có thể chỉ gọi một lần.
- **Retry attempts:** số lần `proceed()` được kích hoạt.
- **Target execution count:** có thể nhiều hơn một nếu continuation đi đến target mỗi lần.
- **Committed side effects:** không nhất thiết bằng số attempts; có thể đã thành công ở lần báo lỗi hoặc bị rollback riêng.
- **Advice execution count:** tùy advisor ordering, advice ngoài retry có thể chạy một lần còn advice trong retry chạy mỗi attempt.

## 3. Exception không chứng minh thao tác trước chưa xảy ra

| Thời điểm xảy ra lỗi | Ví dụ | Kết luận trước khi retry |
| --- | --- | --- |
| Trước side effect | Input validation không hợp lệ, lỗi trước khi gửi request | Có thể chưa có side effect, nhưng lỗi validation thường không nên retry |
| Sau side effect | DB đã commit, lỗi khi gọi bước tiếp theo; payment đã ghi nợ nhưng response mất | Retry có thể **lặp tác động** |
| Trạng thái unknown | Network timeout sau khi gửi request, không biết remote đã xử lý chưa | Không thể an toàn chỉ dựa vào exception |
| Transaction rollback được xác nhận | Lỗi trong một local DB transaction bị rollback đúng luật | Local DB side effect có thể chưa commit, nhưng remote side effect ngoài transaction vẫn có thể tồn tại |

**Timeout không đồng nghĩa thao tác không hoàn thành.** Nếu một API thanh toán trả timeout, client không có bằng chứng “chưa ghi nợ”. Muốn retry an toàn cần API phía nhận có idempotency key hoặc cần tra cứu kết quả theo operation ID trước khi thử lại.

Ví dụ dễ gặp khác:

- Gửi email thành công nhưng exception phát sinh khi ghi log kết quả.
- Publish event lên broker thành công nhưng xác nhận gửi bị mất.
- Ghi database thành công, rồi gọi dịch vụ phụ trợ thất bại.
- Tăng bộ đếm hoặc trừ số dư trước khi ném một runtime exception.
- Gửi yêu cầu tạo tài nguyên có request ID mới ở mỗi attempt.

Một vòng lặp retry không hiểu ranh giới side effect có thể khiến lỗi quan sát được **ít hơn** trong khi duplicate behavior **nhiều hơn**.

## 4. Bounded retry là điều kiện cần, không đủ

Ví dụ về cấu trúc retry có giới hạn:

~~~java
@Around("execution(* demo.ExternalOperation.execute(..))")
public Object retry(ProceedingJoinPoint pjp) throws Throwable {
    final int maxAttempts = 3;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        try {
            return pjp.proceed();
        } catch (TemporaryFailureException ex) {
            if (attempt == maxAttempts) {
                throw ex;
            }
        }
    }

    throw new IllegalStateException("Unreachable");
}
~~~

Đây là **fragment**; cần khai báo `TemporaryFailureException`, `ExternalOperation` và một class Spring aspect hợp lệ.

Các quy tắc quan trọng:

- `maxAttempts = 3` nghĩa là tối đa **ba attempts tính cả lần đầu**, không phải ba lần retry thêm.
- `proceed()` phải nằm trong loop; nếu gọi ngoài loop sẽ không thực thi lại target.
- Chỉ catch lỗi **đã được phân loại**; không retry mặc định mọi `RuntimeException` hoặc `Throwable`.
- Khi hết attempts, **ném lại exception cuối**, không báo kết quả thành công giả.
- Nếu thành công, trả chính kết quả `pjp.proceed()`, giữ return contract của method.
- Nếu mỗi attempt có thể tạo side effect, **giới hạn 3 attempts chỉ giới hạn tối đa số lần gây tác động**, không bảo đảm có đúng một side effect.
- Nếu caller đã có deadline, những attempts + backoff không được vượt budget theo thiết kế.

Hãy xác định **operation có idempotent không** trước khi áp dụng retry. Không coi bounded retry là giải pháp chống double charge.

## 5. Idempotency khác gì retry?

**Retry** lặp lại một operation khi gặp lỗi. **Idempotency** bảo đảm việc gửi cùng logical operation nhiều lần không nhân lên hiệu ứng nghiệp vụ mong muốn.

Ví dụ:

~~~text
logicalPayment = charge(order-12, amount=100)
    |
    +-- attempt 1 (key=order-12-charge)
    |
    +-- attempt 2 (key=order-12-charge)
    |
    +-- attempt 3 (key=order-12-charge)
~~~

Cả ba attempts dùng **cùng một idempotency key**. Nếu mỗi attempt tạo một key mới, hệ thống phía nhận có thể coi đó là ba operations độc lập.

Những lưu ý bắt buộc:

1. Key xác định **logical operation** chứ không phải một lần gọi HTTP riêng.
2. Key phải được tái sử dụng khi retry cùng operation, nhưng không được dùng chung cho hai operations thực sự khác nhau.
3. Có thể phải ràng buộc key với tenant/account, loại operation và fingerprint của request body.
4. Khi cùng key nhưng body khác nhau, cần trả conflict rõ ràng, không vô tình trả một kết quả của operation khác.
5. Idempotency cần được áp dụng **ở nơi quyết định side effect** hoặc bởi bên nhận có contract tương ứng.
6. Cần xử lý trạng thái **in progress**, completed, failed, unknown và policy TTL/retention.
7. Nếu nhiều replicas cùng nhận key, cần coordination hoặc durable storage. Một cache trong mỗi JVM không đủ.
8. Nếu ghi key và tác động bên ngoài không atomic, vẫn có cửa sổ lỗi; cần reconciliation hoặc cơ chế hỗ trợ của hệ thống đích.

**Idempotent không đồng nghĩa target không được gọi lại**: target có thể chạy hai lần nhưng lần sau đọc kết quả đã hoàn tất và không thực hiện side effect thứ hai.

## 6. Ví dụ Java 17+ Spring AOP: retry hai lần, so sánh số side effects

Ta tạo ứng dụng **Spring Framework 6.2.12**, **Java 17+**, **Maven 3.9+**, dùng Spring context thông thường. Không có DB hoặc network thật, nhưng tái tạo deterministic tình huống **báo lỗi sau khi đã tác động**.

Hai method trên Spring service:

- `unsafeCharge(requestId)`: mỗi lần target chạy sẽ tăng một bộ đếm side effect, **không có chống trùng**.
- `guardedCharge(requestId)`: cùng request ID tra cứu một kết quả đã lưu trong `ConcurrentHashMap`, side-effect counter chỉ tăng **một lần trong một JVM**.

Một `AfterEffectFailureGate` cố ý ném `TemporaryFailureException` **ở lần đầu sau side effect**. `RetryAspect` thực hiện tối đa hai attempts. Kết quả cho thấy **cả hai method đều được target gọi hai lần nhưng số side effects khác nhau**.

> Cơ chế `ConcurrentHashMap` chỉ là mô phỏng có kiểm soát, **không phải giải pháp đúng cho thanh toán phân tán hoặc lưu trữ bền vững**. Mục tiêu là nhìn được tác hại của retry và vị trí cần idempotency.

### 6.1. Cấu trúc project

~~~text
spring-aop-retry-side-effects/
├── pom.xml
└── src/main/java/demo/
    ├── ChargeApi.java
    ├── ChargeLedger.java
    ├── TemporaryFailureException.java
    ├── AfterEffectFailureGate.java
    ├── ChargeService.java
    ├── RetryAspect.java
    ├── AppConfig.java
    └── App.java
~~~

### 6.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-retry-side-effects</artifactId>
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

`spring-context` cung cấp ApplicationContext và các module Spring AOP cần thiết. `aspectjweaver` cung cấp annotation/pointcut parser mà Spring sử dụng cho `@AspectJ` style; ví dụ này **proxy-based**, không dùng AspectJ compile-time/load-time weaving.

### 6.3. ChargeApi.java

~~~java
package demo;

public interface ChargeApi {
    String unsafeCharge(String requestId);
    String guardedCharge(String requestId);
}
~~~

Hai method được công bố qua interface để caller sử dụng **JDK dynamic proxy** khi Spring auto-proxy phù hợp. Cách gọi đi qua proxy quan trọng: nếu chỉ `new ChargeService(...)` rồi gọi, aspect không tự intercept đối tượng đó.

### 6.4. ChargeLedger.java

~~~java
package demo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class ChargeLedger {

    private final AtomicInteger unsafeEffects = new AtomicInteger();
    private final AtomicInteger guardedEffects = new AtomicInteger();

    private final ConcurrentMap<String, String> processed =
            new ConcurrentHashMap<>();

    public String unsafe(String requestId) {
        int number = unsafeEffects.incrementAndGet();
        System.out.println("LEDGER unsafe side_effect=" + number);
        return "charged:" + requestId;
    }

    public String guarded(String requestId) {
        return processed.computeIfAbsent(requestId, key -> {
            int number = guardedEffects.incrementAndGet();
            System.out.println("LEDGER guarded side_effect=" + number);
            return "charged:" + key;
        });
    }

    public int unsafeCount() {
        return unsafeEffects.get();
    }

    public int guardedCount() {
        return guardedEffects.get();
    }
}
~~~

`unsafe` tăng side-effect counter **mỗi lần được gọi**. `guarded` dùng `computeIfAbsent`: với cùng key và entry còn tồn tại trong map này, nó tái sử dụng kết quả, không thực hiện lại lambda tạo giá trị. Điều này mô phỏng một idempotency ledger **cùng process**, không tương đương commit atomic với remote API.

`AtomicInteger` giúp phép tăng count là atomic, **không làm `unsafe` trở thành idempotent**.

### 6.5. TemporaryFailureException.java

~~~java
package demo;

public class TemporaryFailureException extends RuntimeException {

    public TemporaryFailureException(String message) {
        super(message);
    }
}
~~~

Trong demo, exception này chỉ là tín hiệu lỗi được thiết kế để retry aspect bắt. Trong ứng dụng thật, một tên exception như “Temporary” **không chứng minh operation là an toàn để retry**.

### 6.6. AfterEffectFailureGate.java

~~~java
package demo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class AfterEffectFailureGate {

    private final Set<String> failedKeys =
            ConcurrentHashMap.newKeySet();

    public void failAfterEffectOnce(String method, String requestId) {
        String key = method + ":" + requestId;

        if (failedKeys.add(key)) {
            System.out.println("GATE lost-ack " + method);
            throw new TemporaryFailureException(
                    "Simulated acknowledgement loss after effect");
        }
    }
}
~~~

Ở lần đầu một cặp `method:requestId` xuất hiện, gate in `GATE lost-ack` rồi ném exception. Lần tiếp theo cùng cặp key không ném nữa. Gate chỉ dùng để tạo lỗi **sau khi ledger đã thực hiện side effect**, không dùng network thật, không có độ trễ ngẫu nhiên.

### 6.7. ChargeService.java

~~~java
package demo;

import org.springframework.stereotype.Service;

@Service
public class ChargeService implements ChargeApi {

    private final ChargeLedger ledger;
    private final AfterEffectFailureGate gate;

    public ChargeService(ChargeLedger ledger,
                         AfterEffectFailureGate gate) {
        this.ledger = ledger;
        this.gate = gate;
    }

    @Override
    public String unsafeCharge(String requestId) {
        System.out.println("TARGET unsafeCharge " + requestId);
        String result = ledger.unsafe(requestId);
        gate.failAfterEffectOnce("unsafeCharge", requestId);
        return result;
    }

    @Override
    public String guardedCharge(String requestId) {
        System.out.println("TARGET guardedCharge " + requestId);
        String result = ledger.guarded(requestId);
        gate.failAfterEffectOnce("guardedCharge", requestId);
        return result;
    }
}
~~~

Mỗi method làm ba bước: in `TARGET`, thực hiện ledger side effect (hoặc dedupe), sau đó gate **có thể ném exception**. Một exception ở bước cuối không xóa side effect ở bước trước. Retry sẽ chạy lại toàn bộ method.

Không có dependency vòng: `RetryAspect` không inject `ChargeService` hoặc ledger; nó chỉ khai báo pointcut. Các Spring beans ledger/gate không phải target của expression `ChargeApi.*(..)`.

### 6.8. RetryAspect.java

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RetryAspect {

    @Around("execution(* demo.ChargeApi.*(..))")
    public Object retry(ProceedingJoinPoint pjp) throws Throwable {
        String methodName = pjp.getSignature().getName();
        final int maxAttempts = 2;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            System.out.println(
                    "RETRY " + methodName + " attempt=" + attempt);

            try {
                return pjp.proceed();
            } catch (TemporaryFailureException ex) {
                System.out.println(
                        "RETRY caught " + methodName +
                        " attempt=" + attempt);

                if (attempt == maxAttempts) {
                    throw ex;
                }
            }
        }

        throw new IllegalStateException("Unreachable retry state");
    }
}
~~~

Điểm đáng chú ý:

- `@Aspect` mô tả advice, `@Component` làm bean được đăng ký khi scan.
- `@Around` chỉ chọn phương thức thuộc contract `ChargeApi`.
- `pjp.proceed()` **nằm trong vòng lặp** và có thể gọi lại target.
- Không bắt mọi loại exception: chỉ retry `TemporaryFailureException`.
- Không nuốt lỗi ở attempt cuối; exception được chuyển cho caller.
- Không thay đổi arguments giữa các attempts; request ID vẫn giống nhau.
- Giá trị trả từ target được trả lại cho caller khi thành công.
- Không có delay trong demo để output có thứ tự xác định.

Trong production, cần giới hạn thêm tổng duration/backoff và đảm bảo operation thực sự có idempotency. Aspect này **cố ý không an toàn** cho `unsafeCharge` để minh họa rủi ro.

### 6.9. AppConfig.java

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

`@ComponentScan` đăng ký service, ledger, gate và aspect. `@EnableAspectJAutoProxy` kích hoạt hạ tầng tự tạo Spring AOP proxy. Nếu chỉ có `@Aspect` mà class không phải Spring-managed bean, Spring không tự áp dụng advice.

### 6.10. App.java

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            ChargeApi charges = context.getBean(ChargeApi.class);
            ChargeLedger ledger = context.getBean(ChargeLedger.class);

            System.out.println(
                    "IS_PROXY " + AopUtils.isAopProxy(charges));

            System.out.println("=== UNSAFE ===");
            String unsafe = charges.unsafeCharge("U-1");
            System.out.println("RESULT unsafe=" + unsafe);
            System.out.println("UNSAFE_EFFECTS " + ledger.unsafeCount());

            System.out.println("=== GUARDED ===");
            String guarded = charges.guardedCharge("G-1");
            System.out.println("RESULT guarded=" + guarded);
            System.out.println("GUARDED_EFFECTS " + ledger.guardedCount());
        }
    }
}
~~~

Chạy từ thư mục chứa `pom.xml`:

~~~bash
mvn -q clean compile exec:java
~~~

### 6.11. Stdout logic kỳ vọng

~~~text
IS_PROXY true
=== UNSAFE ===
RETRY unsafeCharge attempt=1
TARGET unsafeCharge U-1
LEDGER unsafe side_effect=1
GATE lost-ack unsafeCharge
RETRY caught unsafeCharge attempt=1
RETRY unsafeCharge attempt=2
TARGET unsafeCharge U-1
LEDGER unsafe side_effect=2
RESULT unsafe=charged:U-1
UNSAFE_EFFECTS 2
=== GUARDED ===
RETRY guardedCharge attempt=1
TARGET guardedCharge G-1
LEDGER guarded side_effect=1
GATE lost-ack guardedCharge
RETRY caught guardedCharge attempt=1
RETRY guardedCharge attempt=2
TARGET guardedCharge G-1
RESULT guarded=charged:G-1
GUARDED_EFFECTS 1
~~~

Những dòng này là **stdout logic dự kiến từ mã mẫu**, chưa bao gồm log riêng của Maven và Spring. Chương trình chạy hai kịch bản tuần tự, nên không có ordering bất định giữa chúng.

**UNSAFE:** Một lời gọi caller, hai lần `proceed()`, hai lần target, **hai side effects**. Lần đầu đã ghi nhận một side effect trước khi exception thoát ra. Kết quả thành công cuối cùng không xóa lần thực thi đầu.

**GUARDED:** Một lời gọi caller, hai lần `proceed()`, hai lần target, **một side effect được đếm**. Key `G-1` được giữ nguyên; lần hai map trả kết quả cũ. Đây là **idempotency mô phỏng trong cùng một JVM**, không bảo đảm hệ thống thanh toán thực không ghi nợ hai lần.

## 7. Vì sao in-memory guard không đủ cho production?

`ConcurrentHashMap` ở ví dụ chỉ bảo vệ key trong process đang chạy:

- Sau **restart**, dữ liệu key mất.
- Khi chạy **hai replicas**, mỗi process có map riêng.
- Nếu một side effect xảy ra ở remote service rồi JVM crash trước khi lưu kết quả key, app không có bằng chứng đã hoàn tất.
- Nếu request body khác nhau nhưng dùng trùng key, map có thể trả kết quả của operation không tương đương.
- Khi key hết hạn hoặc bị xóa, một retry muộn có thể thực thi lại operation.
- Nếu bên dưới còn gọi nhiều hệ thống không chia sẻ transaction, một key ở app layer không bảo đảm mọi side effect atomic.

Giải pháp cần được đặt ở **execution boundary**:

| Loại side effect | Hướng thiết kế cần xét |
| --- | --- |
| Thao tác DB nội bộ | Unique constraint, transaction và kết quả ghi theo operation ID |
| API thanh toán bên ngoài | Idempotency key **được bên nhận hỗ trợ theo contract** |
| Message/event | Outbox, message key, idempotent consumer và cơ chế dedupe |
| Gửi email/SMS | Tracking message ID, trạng thái gửi/ACK và quy tắc resend |
| Workflow nhiều hệ thống | Reconciliation, state machine và xử lý trạng thái unknown |

Không nên gọi tất cả các giải pháp trên là “exactly-once” tuyệt đối. Cần mô tả **phạm vi side effect**, nơi uniqueness được bảo đảm, và tình huống crash/replay thực tế.

## 8. Transaction advice và thứ tự retry aspect

Đây là vấn đề đặc biệt của **Spring AOP** khi service có `@Transactional`.

### 8.1. Retry bọc ngoài transaction advice

~~~text
RetryAspect (outer)
  |
  +-- proceed attempt 1
  |       |
  |       v
  |    TransactionInterceptor
  |       |
  |       +-- begin transaction #1
  |       +-- target ném lỗi
  |       +-- rollback theo policy
  |
  +-- proceed attempt 2
          |
          v
       TransactionInterceptor
          |
          +-- begin transaction #2
          +-- target chạy lại
          +-- commit nếu hợp lệ
~~~

Spring Framework có ví dụ **retry aspect với precedence cao hơn transaction advice** để mỗi lần thử có transaction mới. Cấu hình thứ tự có thể dùng `@Order` hoặc `Ordered`, nhưng kết quả còn phụ thuộc propagation mode và transaction đang tồn tại từ caller. Không chỉ thêm `@Order` rồi coi mọi attempt chắc chắn mở transaction mới.

### 8.2. Transaction bọc ngoài retry advice

~~~text
TransactionInterceptor (outer)
  |
  +-- một transaction context
           |
           +-- RetryAspect (inner)
                   |
                   +-- attempt 1: lỗi
                   +-- attempt 2: thử lại
~~~

Trong bố trí này, retries có thể ở **cùng một transaction**. Sau lỗi đầu, transaction hoặc database resource có thể đã bị đánh dấu rollback-only hay rơi vào trạng thái không an toàn để tiếp tục. Attempt thứ hai thành công ở mức method **không chắc** khiến toàn transaction commit.

Đồng thời, **DB rollback không thu hồi được một remote charge, email hoặc HTTP request đã hoàn tất**. Vì thế đặt retry ngoài transaction có thể cần thiết nhưng **không đủ** để bảo vệ external side effects.

### 8.3. Mẫu khai báo thứ tự

~~~java
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RetryAspect {
    // ...
}
~~~

Đây là **fragment minh họa cách khai báo thứ tự**, cần import `org.springframework.core.Ordered` và `org.springframework.core.annotation.Order`. Không đồng thời thêm class thứ hai tên RetryAspect vào project mẫu ở mục 6. Thứ tự ưu tiên không phải cơ chế chống duplicate; nó chỉ ảnh hưởng vị trí advice trong chain.

## 9. Logging, metrics và transaction có thể lặp theo số attempt khác nhau

Nếu `MetricsAspect` nằm **ngoài** `RetryAspect`:

~~~text
MetricsAspect
  |
  +-- RetryAspect
         |
         +-- proceed #1 -> target
         +-- proceed #2 -> target
~~~

Metrics có thể đo một logical invocation, trong khi target chạy hai lần.

Nếu `MetricsAspect` nằm **trong** retry:

~~~text
RetryAspect
  |
  +-- proceed #1 -> MetricsAspect -> target
  +-- proceed #2 -> MetricsAspect -> target
~~~

Metrics advice có thể chạy hai lần. Do đó cần đo **request count**, **retry attempt count** và **committed effect count** riêng biệt. Đừng suy ra số side effects chỉ từ số dòng log của một aspect khi chưa hiểu advisor ordering.

Một `@AfterThrowing` hoặc transaction advisor nằm phía trong cũng có thể được thực thi mỗi attempt. Nếu advice ở phía ngoài, nó có thể chỉ thấy kết quả/exception **sau cùng** từ retry aspect. Spring không đảm bảo thứ tự giữa các aspects khác nhau nếu ứng dụng không cấu hình rõ.

## 10. Bắt loại lỗi nào và kết thúc ra sao?

Một anti-pattern:

~~~java
try {
    return pjp.proceed();
} catch (Exception anything) {
    return pjp.proceed();
}
~~~

Đây là fragment **không an toàn**. Nó retry cả validation lỗi, permission denied, duplicate key không nên retry và lỗi do code. Nếu operation có side effect, kiểu catch này còn gây nhân tác động.

Một policy retry cần xác định:

1. **Exception whitelist hoặc predicate** cho lỗi thật sự tạm thời.
2. **Maximum attempts** và deadline tổng, không retry vô hạn.
3. **Backoff/jitter** để tránh các request đồng loạt đánh vào dịch vụ đang quá tải.
4. **Idempotency/reconciliation** cho trạng thái kết quả unknown.
5. **Phản hồi cuối cùng** khi hết attempts: exception hoặc status thất bại minh bạch, không tự trả chuỗi thành công.
6. **Observability**: correlation ID và attempt number.
7. **Cancel/interruption**: không nuốt trạng thái hủy hoặc interrupted thread.
8. **Giới hạn phạm vi pointcut** vào business operations đã được review là retryable.

Không dùng `catch(Throwable)` theo mặc định để retry mọi lỗi nghiêm trọng. Một exception tên “Transient” do thư viện ném ra vẫn cần được xem xét với **tác dụng phụ của method thực tế**.

## 11. Backoff, timeout budget và retry storm

Giả sử 100 lời gọi client gặp lỗi mạng cùng lúc. Nếu mỗi invocation có 3 attempts retry tức thì, số lượt target có thể tăng tới 300 trong một khoảng ngắn, khiến dịch vụ vốn đã quá tải càng chậm.

Hướng xử lý:

- Dùng bounded retry, không retry vô hạn.
- Cân nhắc backoff tăng dần và jitter khi nhiều clients cùng retry.
- Dành tổng **timeout budget** cho cả retries, không cộng từng attempt vô hạn.
- Nếu failure rate cao, cân nhắc circuit breaker/rate limiting phù hợp.
- Không dùng `Thread.sleep` dài trên thread xử lý HTTP một cách tùy tiện trong synchronous `@Around`.
- Xác nhận upstream/downstream có tự retry hay không để tránh **retry amplification nhiều tầng**.

Backoff chủ yếu điều chỉnh **tải và xác suất hồi phục**, không ngăn duplicate side effect; idempotency vẫn phải được thiết kế riêng.

## 12. Proxy boundary và self-invocation

Spring AOP chỉ advice những lời gọi đủ điều kiện **đi qua proxy**. Nếu một method trong target gọi method retryable bằng `this.method()`:

~~~java
public void process(String id) {
    this.unsafeCharge(id);
}
~~~

lời gọi nội bộ này không quay qua Spring AOP proxy, nên `RetryAspect` có thể **không được kích hoạt** cho unsafeCharge. Đó là tình huống khác với around advice đã chạy rồi gọi proceed hai lần.

Những hạn chế cần nhớ:

- **JDK dynamic proxy** expose các public interface methods phù hợp.
- **CGLIB** có thể advice các instance methods có thể override khi gọi qua class proxy, nhưng không override private/final methods.
- Bean tự tạo bằng `new` không được Spring AOP tự proxy.
- Pointcut quá rộng có thể chọn cả helper, recorder và gateway mà advice gọi tới, tạo recursion/retry lồng nhau.
- Nếu cần retry cho lời gọi nội bộ, ưu tiên tách operation sang Spring bean khác rồi inject proxy phù hợp, không tự ý dựa vào `AopContext.currentProxy()`.

Trong ví dụ mục 6, pointcut chỉ chọn các method thuộc `ChargeApi` để tránh tác động vào `ChargeLedger` và `AfterEffectFailureGate`.

## 13. Các bài test cần có

| Kịch bản | Phải kiểm chứng |
| --- | --- |
| Target thành công ngay attempt đầu | Target chạy đúng một lần, không có retry |
| Lỗi trước side effect rồi thành công | Retry đúng exception/attempt budget |
| **Lỗi sau side effect** rồi thành công | Phát hiện số side effects bị nhân lên khi không có idempotency |
| Cùng key được retry với guard | Số committed effects không tăng trong phạm vi bảo đảm |
| Exception không thuộc whitelist | Không retry |
| Hết attempts đều thất bại | Exception cuối thoát ra, không báo thành công giả |
| Cùng key nhưng payload khác | Có rule conflict rõ ràng |
| Hai logical operations khác nhau | Không bị dedupe nhầm |
| Concurrency cùng key | Kiểm tra atomicity thật ở storage/bên nhận |
| Nhiều replicas hoặc restart | Kiểm chứng durability, không dựa vào map một JVM |
| Có transaction advice | Verify advisor order và boundary từng attempt |
| Self-invocation | Không giả định retry sẽ chạy |
| Retry nested ở nhiều layers | Đo amplification và deadline |

Với ví dụ đang chạy, bổ sung trong `App.main` sau cả hai kịch bản:

~~~java
if (ledger.unsafeCount() != 2) {
    throw new AssertionError("Unsafe operation should have run twice");
}
if (ledger.guardedCount() != 1) {
    throw new AssertionError("Guarded effect should be recorded once");
}
~~~

Đây là **test fragment**, không phải file Java độc lập. Hai assertion là kết quả chủ đích của bài học để so sánh, **không phải khuyến nghị chấp nhận double charge ở hệ thống thật**.

## 14. Những lỗi phổ biến khi viết RetryAspect

| Triệu chứng | Nguyên nhân điển hình | Cách xử lý |
| --- | --- | --- |
| Một lệnh thanh toán bị tính hai lần | Retry sau side effect khi ACK bị mất | Idempotency tại provider hoặc reconciliation |
| Service trả thành công nhưng DB/event bị lặp | Caller thấy kết quả attempt cuối, không thấy side effect attempt đầu | Theo dõi committed effects và operation ID |
| Có maxAttempts=2 nhưng vẫn double action | Bounded retry không tự chống trùng | Bổ sung idempotency |
| Mỗi attempt tạo một UUID idempotency mới | Downstream coi mỗi attempt là operation khác | Giữ key ổn định cho logical operation |
| Guard hoạt động ở local nhưng không ở cluster | In-memory map theo JVM | Durable shared store hoặc idempotency của bên nhận |
| Retry cả lỗi validation | Catch quá rộng | Chỉ retry exception đã phân loại |
| Method thành công nhưng transaction rollback | Retry bên trong transaction đã rollback-only | Kiểm tra transaction advice order/boundary |
| DB rollback nhưng email vẫn gửi | External side effect ngoài transaction | Thiết kế outbox/idempotency và reconciliation |
| Retry không chạy khi gọi nội bộ | Self-invocation bỏ qua proxy | Refactor sang Spring bean khác |
| Retry aspect chạy cả trên recorder | Pointcut quá rộng | Restrict service API/package/annotation |
| Logging aspect đếm một request nhưng target hai lần | Logging nằm ngoài retry | Đếm logical requests và attempts riêng |
| Bị quá tải nặng hơn sau lỗi | Retry storm, không backoff/budget | Backoff/jitter/circuit breaker, giới hạn requests |
| Lỗi đã hết attempts nhưng caller vẫn nhận OK | Advice nuốt exception/trả fallback sai | Propagate error/status rõ ràng |

## 15. Checklist triển khai Spring AOP retry an toàn

- Đã liệt kê mọi side effect: DB, remote API, broker, email, cache, counter?
- Có thể khẳng định exception xảy ra **trước** tác động không? Nếu không, coi trạng thái là unknown.
- Operation có idempotency key ổn định qua các attempts không?
- Idempotency có được bên **thực hiện side effect** tôn trọng và lưu bền vững không?
- Có xử lý khi cùng key nhưng request body khác hoặc operation đang chạy không?
- Có giới hạn attempts, tổng deadline và backoff khi cần không?
- Chỉ bắt những exception được phân loại là retryable?
- Khi hết retries, có chuyển exception/status thật tới caller?
- Advisor ordering với `@Transactional` đã được kiểm chứng chưa?
- Các remote side effects có nằm ngoài local transaction không?
- Lời gọi có thật sự đi qua Spring proxy, không phải self-invocation?
- Pointcut có loại recorder, infrastructure và những method không retryable?
- Có test đặc biệt **lỗi sau side effect** với counter hoặc trạng thái hệ thống?
- Có test concurrency, restart và retries từ nhiều layers?
- Có metrics riêng cho **attempts** và **committed side effects**?

## 16. Câu hỏi tự đánh giá

**1. Vì sao gọi `pjp.proceed()` hai lần có thể nguy hiểm?**

Vì continuation của Spring AOP có thể đưa lời gọi đến target hai lần và các thao tác ghi dữ liệu/gửi yêu cầu trong target có thể bị nhân đôi.

**2. Timeout có nghĩa giao dịch trước chắc chắn chưa thành công không?**

Không. Phản hồi có thể mất sau khi bên nhận đã commit.

**3. maxAttempts=2 bảo đảm điều gì và không bảo đảm điều gì?**

Nó giới hạn số attempts tối đa theo retry aspect đó, nhưng **không bảo đảm mỗi logical operation chỉ có một side effect**.

**4. Có nên thay idempotency key ở attempt thứ hai không?**

Không. Các lần thử của cùng logical operation phải có cùng key theo contract.

**5. Vì sao `ConcurrentHashMap` trong demo không đủ cho production?**

Nó chỉ ở một process, mất khi restart, không đồng bộ giữa replicas và không atomic với remote side effects.

**6. Khi retry nằm ngoài Spring transaction advice thì có thể nhận transaction mới cho mỗi attempt không?**

Có thể, nếu ordering và transaction boundaries phù hợp; phải kiểm chứng với propagation/caller thực tế.

**7. DB rollback có thu hồi một email đã gửi không?**

Thông thường không; đó là side effect ngoài transaction DB.

**8. Retry aspect có hoạt động cho `this.method()` trong cùng target không?**

Không qua self-invocation thông thường, vì lời gọi ấy bỏ qua Spring AOP proxy.

**9. Bằng chứng tốt nhất cho việc guard hoạt động là gì?**

Kiểm tra **committed side effect count hoặc trạng thái hệ thống**, không chỉ response hay số dòng log của advice.

**10. Nếu remote operation không có idempotency và kết quả timeout unknown thì nên làm gì?**

Không retry mù; cần tra cứu/reconcile trạng thái hoặc thiết kế idempotency với bên nhận trước.

## 17. Nguồn tham khảo chính thức

- Spring Framework — **Declaring Advice / Around Advice**, cách `proceed()` có thể gọi nhiều lần: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — **An AOP Example**, retry aspect và thứ tự ưu tiên trước transaction advice: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/example.html
- Spring Framework — **Proxying Mechanisms**, JDK/CGLIB và self-invocation: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — **Declarative Transaction Management**, transaction advice: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
- Spring Framework — **@AspectJ Support**: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html

**Kết luận:** Với Spring `@Around`, retry bằng cách gọi `ProceedingJoinPoint.proceed()` lần nữa có thể kích hoạt lại target và những advice phía trong. Nếu lần đầu đã tạo side effect rồi mới thất bại, caller chỉ thấy một kết quả thành công nhưng tác động nghiệp vụ có thể xảy ra nhiều lần. Phân loại exception, giới hạn attempts, kiểm soát transaction advisor và backoff là quan trọng; **idempotency/reconciliation ở đúng nơi thực hiện side effect** mới giải quyết nguy cơ duplicate thực sự.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-010">Tránh nuốt ngoại lệ hoặc trả giá trị mặc định ngoài hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh nuốt ngoại lệ hoặc trả giá trị mặc định ngoài hợp đồng trong Spring AOP @Around

## 1. Vấn đề: advice có thể thay đổi hợp đồng của Spring service

`@Around` là loại advice của **Spring AOP theo cú pháp @AspectJ** có quyền bao quanh một method execution được proxy, gọi hoặc không gọi `ProceedingJoinPoint.proceed()`, quan sát kết quả và quyết định cuối cùng **giá trị hoặc ngoại lệ mà caller nhận được**.

Quyền này hữu ích để đo latency, ghi audit, thực hiện retry hoặc có một cơ chế fallback đã được thiết kế. Nhưng cũng dễ tạo bug âm thầm: một `catch` trong advice ghi log rồi `return null`, `return 0` hoặc `return new Object()`, biến **thất bại thật** thành **một lần trả về bình thường** hoặc phá kiểu trả về của method.

Ví dụ sai về mặt hợp đồng:

```java
@Around("execution(* com.example.orders.OrderService.submit(..))")
public Object unsafe(ProceedingJoinPoint pjp) {
    try {
        return pjp.proceed();
    } catch (Throwable error) {
        System.err.println(error.getMessage());
        return null; // Nuốt lỗi, dù submit() chưa chắc cho phép null.
    }
}
```

Đoạn này là **minh họa anti-pattern**, cần class `@Aspect`, imports và Spring AOP setup để chạy; nó không thuộc demo hoàn chỉnh ở phần sau. Nếu method đích công bố `String submit(...)` và caller kỳ vọng **trả mã xác nhận hoặc ném lỗi**, lời advice trên đã bí mật thêm outcome `null`. Nếu return type là primitive `int`, `null` có thể gây lỗi unboxing khi quay qua proxy, làm che mất nguyên nhân gốc.

**Mục tiêu bài học** là viết `@Around` Spring AOP sao cho:

1. **Mặc định giữ nguyên contract**: `proceed()` một lần, trả đúng result, ném lại chính `Throwable` gốc.
2. Nếu làm fallback, **phải có đặc tả rõ**: lỗi nào được chuyển đổi, kiểu/value hợp lệ, phạm vi retry, transaction và test.
3. Xác nhận outcome **qua Spring proxy thật**, không chỉ unit test trực tiếp gọi method trong aspect.
4. Phân biệt **normal return**, `null`, `void`, boxed primitive, checked/unchecked exceptions và async handles.

Theo ghi chú của module, nội dung chỉ tập trung vào **Spring Aspect và Spring AOP proxy**; không mở rộng sang AspectJ weaving hoặc AOP tổng quát.

## 2. Hợp đồng quan sát được của method là gì?

Đối với một public Spring service operation, contract bao gồm nhiều hơn signature Java:

| Phần contract | Ví dụ trong Spring service | Rủi ro khi advice làm sai |
| --- | --- | --- |
| Return type | `Receipt`, `String`, `int`, `void`, `Optional<Receipt>` | Trả object sai kiểu gây `ClassCastException`; `null` với primitive có thể gây unboxing failure. |
| Normal value | Mã giao dịch được phát hành, số lượng hàng còn tồn | Giá trị mặc định `0`/`false` giả thành công hoặc báo sai nghiệp vụ. |
| Nullability | Method có cho phép `null` hay không | `null` không được công bố khiến caller NPE/mất dữ liệu. |
| Failure types | `IllegalArgumentException`, `PaymentDeclinedException` | Advice nuốt lỗi, đổi loại ngoại lệ, làm caller không vào đúng `catch`. |
| Side effects | Ghi database, gửi message, gọi remote API | Retry hoặc `proceed()` lặp làm thực hiện nghiệp vụ nhiều lần. |
| Transaction | Lệnh ghi cần rollback khi ném một lỗi nhất định | Chuyển exception thành success có thể làm khác rollback semantics, tùy advisor ordering. |
| Security | Không cho phép caller trái quyền hoặc chuyển lỗi authorization | Fallback có thể che lỗi kiểm soát truy cập nếu xử lý quá rộng. |
| Async/reactive | Trả `CompletableFuture<T>`/`Mono<T>` | Advice chỉ thấy wrapper return; không tự quan sát completion cuối cùng. |

**Nguyên tắc:** không có quyền “nuốt một lỗi rồi trả giá trị bất kỳ” chỉ vì chữ ký advice là `Object around(...)`. `Object` là kiểu trả về của **advice**, còn method Java được proxy vẫn có kiểu trả về và semantics riêng.

### 2.1. Vì sao test chỉ thấy HTTP 200 chưa đủ?

Controller phía ngoài có thể:

- Dùng response mapping chuyển `null` thành một dạng response khác.
- Có exception handlers chỉ hoạt động nếu exception đúng kiểu được propagate.
- Trả HTTP 200 cho fallback nhưng nghiệp vụ đã bỏ qua thao tác lưu/gửi.
- Ghi metric “success” trên normal return của proxy dù target thất bại.
- Cố retry vì tưởng không có exception, dẫn đến trạng thái dữ liệu không mong muốn.

Vì vậy, kiểm tra Spring AOP cần assert **kết quả hoặc exception ở method boundary**, sau đó kiểm tra side effects (DB/message/transaction/security) khi nghiệp vụ cần.

## 3. Spring proxy và chỗ @Around có quyền quyết định outcome

```text
External caller / other Spring bean
                |
                | orderService.submit("A")
                v
       Spring AOP proxy
                |
                v
     @Around advice invoked
                |
                +-- pjp.proceed()
                |      |
                |      v
                |  target service method
                |      |
                |      +-- return value ----> advice ----> caller
                |      |
                |      +-- throw failure ---> advice ----> caller
                |
                +-- advice không proceed():
                |      +-- return value tự chọn hoặc throw lỗi tự chọn
                |
                +-- advice catch failure:
                       +-- throw nguyên failure (giữ contract)
                       +-- transform / return fallback (CHỈ khi contract cho phép)
```

Spring AOP là **proxy-based**; `@Around` không tự áp vào một service tạo bằng `new`, một method gọi bằng `this.inner()` trên target hoặc một class/method không proxy được. Muốn advice chạy, aspect phải là Spring bean, có Spring @AspectJ support, target là Spring-managed bean và lời gọi phải đi qua proxy.

Trong Spring Framework Java configuration, thường dùng:

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class AopConfiguration {
    @Bean
    MonitoringAspect monitoringAspect() {
        return new MonitoringAspect();
    }
}
```

Đây là đoạn minh họa; `MonitoringAspect` là class `@Aspect` phải tồn tại. Trong Spring Boot có AOP auto-configuration phù hợp, thường không cần tự khai báo `@EnableAspectJAutoProxy`. Việc bật `proxyTargetClass=true` không xử lý self-invocation, và CGLIB không advice được `final` method.

## 4. Mẫu an toàn mặc định: proceed, quan sát, trả lại hoặc ném lại nguyên trạng

```java
@Aspect
public class ContractPreservingAspect {
    @Around("execution(public * com.example.orders..*Service.*(..))")
    public Object preserve(ProceedingJoinPoint pjp) throws Throwable {
        try {
            Object result = pjp.proceed();
            recordSuccess(pjp);
            return result; // Giữ nguyên object/value mà proceed() trả về.
        } catch (Throwable failure) {
            recordFailureSafely(pjp, failure);
            throw failure; // Giữ nguyên kiểu và chính Throwable gốc.
        }
    }

    private void recordSuccess(ProceedingJoinPoint pjp) {
        // Ghi metric không ném lỗi.
    }

    private void recordFailureSafely(
            ProceedingJoinPoint pjp, Throwable failure) {
        // Quan sát lỗi và không làm mất Throwable gốc.
    }
}
```

Đây là **mẫu minh họa**, không phải project runnable độc lập; các helper cần implementation và imports `@Aspect`, `@Around`, `ProceedingJoinPoint`. Trong code thật, instrumentation cũng có thể ném lỗi. Nếu `recordSuccess` ném, chính `catch(Throwable)` sẽ bắt lỗi instrumentation ấy và `recordFailureSafely` có thể xử lý nhầm đó là lỗi của target. Để giữ đúng semantics, nên **tách pha `proceed()` khỏi pha ghi số liệu** như ví dụ chạy hoàn chỉnh ở mục 7.

Một phiên bản bảo vệ lỗi instrumentation tốt hơn:

```java
@Around("execution(public * com.example.orders..*Service.*(..))")
public Object preserve(ProceedingJoinPoint pjp) throws Throwable {
    final Object result;
    try {
        result = pjp.proceed();
    } catch (Throwable original) {
        try {
            observeFailure(original);
        } catch (RuntimeException telemetryError) {
            // Theo policy: ghi nhận telemetryError qua kênh lỗi độc lập.
        }
        throw original;
    }

    try {
        observeSuccess(result);
    } catch (RuntimeException telemetryError) {
        // Theo policy: không biến result hợp lệ thành lỗi nếu telemetry best-effort.
    }
    return result;
}
```

Đây là đoạn code minh họa trong `@Aspect` và giả định `observeFailure`/`observeSuccess` tồn tại. Không coi `catch(RuntimeException)` là giải pháp bảo vệ trước **mọi** lỗi: chúng ta đang chủ động chọn policy cho những lỗi instrumentation bình thường; errors nghiêm trọng không nên bị nuốt chỉ để giữ illusion thành công.

Nếu telemetry/audit **bắt buộc** phải ghi bền vững thì “best-effort ignore” cũng có thể sai contract; khi ấy cần thiết kế rõ transactional/outbox/reliable audit, không chỉ thay một dòng `catch`.

### 4.1. Vì sao chữ ký advice khai báo throws Throwable?

`ProceedingJoinPoint.proceed()` khai báo `throws Throwable`. Để propagation giữ nguyên mọi loại checked/unchecked exceptions phù hợp với method target, mẫu Spring `@Around` thường là:

```java
public Object around(ProceedingJoinPoint pjp) throws Throwable
```

`throws Throwable` trên **method advice** không làm Java source contract của target đổi thành `throws Throwable` cho caller. Nó cho phép advice thực hiện `throw original` mà không ép mọi lỗi thành `RuntimeException`. Đối với JDK interface proxy và checked exception không được khai báo, quy tắc proxy/JDK có thể dẫn đến wrapping như `UndeclaredThrowableException`; điều này không cho phép advice tùy tiện ném checked exception mới ngoài contract.

`@Around` không nên đổi mọi `Exception` thành `new RuntimeException(error)` chỉ để compiler chấp nhận. Đổi exception type làm thay exception handlers, transaction rollback decisions và log diagnostics. Nếu có mapping nghiệp vụ, hãy định nghĩa mapping rõ và test trên loại proxy thực tế.

## 5. Các anti-pattern phổ biến

### 5.1. Ghi log rồi return null

```java
@Around("execution(* com.example.orders..*Service.*(..))")
public Object swallow(ProceedingJoinPoint pjp) {
    try {
        return pjp.proceed();
    } catch (Throwable error) {
        logger.error("Service failed", error);
        return null;
    }
}
```

Sai mặc định vì:

- `null` không tương thích với `int`, `boolean` hoặc các primitive return types.
- Với `String`/DTO method có contract non-null, caller nhận outcome không hợp lệ.
- Exception handler bên ngoài không biết lời gọi thực sự lỗi.
- Transaction/cache/metrics interceptors khác có thể nhận normal return, tùy vị trí advice trong chain.
- Advice đang bắt cả `Throwable`, bao gồm `Error`, nhưng lại không propagation.

Đây là **mã minh họa không chạy độc lập** vì chưa khai báo logger và aspect infrastructure.

### 5.2. Return giá trị mặc định “cho an toàn”

```java
catch (Exception error) {
    return 0;
}
```

Nếu contract `int remainingStock()` định nghĩa `0` là thật sự hết hàng, giá trị `0` giả từ lỗi database sẽ dẫn tới quyết định nghiệp vụ sai. Nếu contract `boolean authorized()`, `false` giả có thể che lỗi hệ thống — dù trong một số chính sách “fail closed” được chủ động quyết định, vẫn phải phân biệt “deny” với “authorization service unavailable”.

Đừng chọn fallback theo kiểu giá trị dễ biên dịch. Cần phân biệt:

- **No data hợp lệ**: có thể `Optional.empty()` hoặc một result type đã định nghĩa.
- **Failure nghiệp vụ**: exception hoặc explicit failure result theo contract.
- **Infrastructure failure**: timeout, DB unavailable; có thể retry, circuit breaker, lỗi cho caller, hoặc fallback rõ ràng theo policy.
- **Fatal error**: không được convert âm thầm thành business success.

### 5.3. Catch rồi ném một lỗi mới không giữ cause

```java
catch (Exception original) {
    throw new IllegalStateException("failed");
}
```

Đoạn code trên làm mất cause chain trừ khi lưu cause. Nếu **phải** dịch exception vì contract của boundary, tối thiểu:

```java
catch (RemoteServiceException original) {
    throw new OrderGatewayException("Remote order gateway failed", original);
}
```

Đây là ví dụ minh họa; `RemoteServiceException` và `OrderGatewayException` là domain/infrastructure types cần được định nghĩa trong ứng dụng. Mọi change type phải có chủ đích, vì caller/transaction/security có thể phân nhánh theo exception class.

Nếu không có policy chuyển đổi, **`throw original;`** bảo toàn thông tin tốt hơn.

### 5.4. Ném Exception sai thời điểm

```java
Object result = pjp.proceed();
doAudit(); // Có thể ném và thay một normal return bằng exception
return result;
```

Nếu audit được định nghĩa là **best-effort**, việc `doAudit()` ném lỗi có thể vi phạm contract của method đã thực thi thành công. Nếu audit được định nghĩa là **must-succeed**, đó không nhất thiết là bug nhưng phải được thể hiện trong business/transaction contract.

Đặt rõ loại audit (best-effort hay mandatory) và cách xử lý failure; đừng mặc định rằng audit phải “bất kể exception” hay “luôn được bỏ qua”.

### 5.5. Không gọi proceed()

```java
@Around("execution(* com.example.orders..*Service.*(..))")
public Object skip(ProceedingJoinPoint pjp) {
    return "ok";
}
```

`@Around` có quyền không gọi `proceed()`, nhưng như vậy **target chưa chạy**: DB update, validation, audit hay publish message trong target không xảy ra. Nếu chưa có contract về short-circuit (cache hit, access denied hoặc idempotency), đây là bug nghiêm trọng.

`@Before` hoặc `@AfterReturning` thích hợp hơn khi chỉ cần quan sát, không cần điều khiển call path.

### 5.6. Gọi proceed() hai lần vô tình

```java
Object a = pjp.proceed();
Object b = pjp.proceed();
return b;
```

Code này có thể khiến **target thực thi hai lần** theo semantics quanh advice; nếu method không idempotent, nó có thể trừ tiền hai lần, gửi hai sự kiện hay ghi DB hai lần. Không lạm dụng Spring AOP `@Around` để retry mù.

Retry chỉ hợp lệ khi:

- Operation được xác định là idempotent hoặc có idempotency key/deduplication.
- Có policy chỉ retry lỗi **transient** rõ ràng.
- Số attempts, backoff, timeout/deadline và cancellation hợp lý.
- Transaction boundaries, request scope, security context và side effects đã được kiểm chứng.
- Có integration tests xác minh chính xác số lần downstream được gọi.

## 6. Trả đúng kiểu và đúng giá trị: primitive, void, null, Optional

### 6.1. Method trả primitive

Giả sử target có:

```java
public int available() {
    return 12;
}
```

Trong `@Around`, `pjp.proceed()` trả `Object` chứa giá trị boxed `Integer` (hoặc giá trị đã được interceptor khác xử lý tương thích). Mẫu an toàn là **trả nguyên `Object` đó** để Spring proxy unbox theo return type `int`.

```java
Object result = pjp.proceed();
return result;
```

**Không trả `null`**, string `"0"`, hay `Integer` bất kỳ nếu contract đòi hỏi số lượng thực. Với target trả `boolean`, một `null` cũng không thể unbox thành primitive.

### 6.2. Method trả void

Với target:

```java
public void refresh() {
    // Thực hiện thao tác thật.
}
```

`proceed()` của around advice thường trả `null` trên normal completion của `void` method; đây **không phải fallback**. Returning `null` từ `@Around` sau khi `proceed()` thành công là hợp lệ đối với method `void`, khác hoàn toàn với việc cố tình `return null` trong `catch` sau một lỗi của target.

### 6.3. Method chủ đích cho phép null

```java
public String findNullable(String id) {
    return null;
}
```

Nếu contract công bố có thể trả `null`, around advice phải **giữ nguyên `null`** khi target return bình thường. Không được tự ý đổi `null` thành `"UNKNOWN"` nếu consumer dựa vào `null` như “không có dữ liệu”.

### 6.4. Optional và Result type

```java
public Optional<Order> find(String id) {
    // Optional.empty() được định nghĩa là "không tìm thấy".
}
```

Khi contract là `Optional<Order>`, một `Optional.empty()` hợp lệ; `return null` là khác hoàn toàn và thường làm sai contract.

Một result wrapper như `OperationResult<T>` có thể biểu đạt `SUCCESS`/`NOT_FOUND`/`FAILED` tường minh nếu business API được thiết kế như vậy. Nhưng advice không được bí mật thay một service vốn `throws` bằng `Result.fail(...)` khi method signature không hỗ trợ hoặc caller không được cập nhật.

### 6.5. Generics và runtime casts

`@Around` thường khai báo `Object` do `proceed()` trả `Object`. Java compiler không kiểm tra được mọi kiểu return của service tại compile time; Spring/JDK proxy có thể cast/unbox theo declared return type. Một method `List<Order> findAll()` không thể nhận `"error"`; lỗi type mismatch có thể xuất hiện **sau** khi advice return.

Để giảm rủi ro:

- Mặc định trả `pjp.proceed()` trực tiếp.
- Không ép `String.valueOf(...)` cho tất cả results.
- Khi transformation thật sự cần, giới hạn pointcut vào method cụ thể và kiểm thử kiểu trả về.
- Dùng typed result/contract trong service thay vì một aspect chung cố “chuẩn hóa” mọi return values.

## 7. Java 17+ Spring AOP demo: giữ nguyên normal return và ngoại lệ

Demo gồm một Spring-managed target, một Spring-managed aspect và một client lấy target bean từ `AnnotationConfigApplicationContext`. Nó kiểm tra:

- `String` normal return không đổi;
- `int` primitive unboxing không đổi;
- `void` normal completion;
- `null` hợp lệ của method nullable không bị thay;
- `IllegalArgumentException` được truyền nguyên trạng tới caller;
- advice quan sát **4 normal returns và 1 exceptional exit**.

Demo dùng Java 17+, Spring Framework 6.2.19, AspectJ Weaver 1.9.25.1. Đây là Spring Framework thuần, không dựa vào Spring Boot auto-configuration.

### 7.1. Cấu trúc project

```text
spring-around-contract/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/contract/
    ├── Main.java
    ├── AppConfig.java
    ├── OrderService.java
    └── ContractAspect.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'spring-around-contract'
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
    mainClass = 'com.example.contract.Main'
}
```

`spring-context` mang container/Java-config; `spring-aop` và `aspectjweaver` hỗ trợ @AspectJ pointcuts. `@EnableAspectJAutoProxy` kích hoạt Spring proxy AOP, không phải AspectJ load-time weaving. Trong dự án Spring Boot nên dùng BOM tương thích với major version Boot thay vì cố ép versions demo.

### 7.2. Target service với các outcomes xác định

**`OrderService.java`**

```java
package com.example.contract;

public class OrderService {
    public String accept(String id) {
        return "accepted:" + id;
    }

    public int quantity() {
        return 12;
    }

    public void refresh() {
        System.out.println("target:refresh");
    }

    public String findNullable(String id) {
        return null;
    }

    public String reject(String id) {
        throw new IllegalArgumentException("invalid:" + id);
    }
}
```

Các method đều public, không final, class cũng không final. Đây là điều kiện đơn giản để Spring CGLIB proxy có thể intercept. `reject(..)` cố ý ném một exception từ target để kiểm tra advice không nuốt lỗi.

### 7.3. @Around advice không đổi contract

**`ContractAspect.java`**

```java
package com.example.contract;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class ContractAspect {
    private final AtomicInteger normalReturns = new AtomicInteger();
    private final AtomicInteger exceptionalExits = new AtomicInteger();

    @Around("execution(public * com.example.contract.OrderService.*(..))")
    public Object preserve(ProceedingJoinPoint pjp) throws Throwable {
        final Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable original) {
            exceptionalExits.incrementAndGet();
            throw original;
        }
        normalReturns.incrementAndGet();
        return result;
    }

    public int normalCount() {
        return normalReturns.get();
    }

    public int failureCount() {
        return exceptionalExits.get();
    }
}
```

Trong ví dụ, hai `AtomicInteger` không ném lỗi bình thường, nên instrumented outcome đúng với số `proceed()` hoàn tất hoặc ném lỗi. Spring AOP có thể có **các interceptors khác** nằm trong chain; nên gọi tên counter là “outcome quan sát tại `proceed()`” nếu ứng dụng thật có retry/transaction/security interceptors khác.

Điểm quan trọng:

1. `proceed()` được gọi **một lần** cho mỗi invocation.
2. Khi bình thường, `result` được trả về **nguyên trạng**, kể cả boxed primitive hoặc `null`.
3. Khi lỗi, lời advice làm một phép tăng đếm rồi `throw original;`, không tạo new exception.
4. `throws Throwable` trên chữ ký advice cho phép propagation không ép wrap exception.
5. Không có logic biến failure thành `null`, `0`, `false` hay object giả.

### 7.4. Đăng ký aspect và target với Spring

**`AppConfig.java`**

```java
package com.example.contract;

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
    ContractAspect contractAspect() {
        return new ContractAspect();
    }
}
```

`@Aspect` không tự đăng ký bean; `@Bean` trên config đăng ký `ContractAspect` và `OrderService`. `proxyTargetClass=true` chọn class-based proxy để `context.getBean(OrderService.class)` hợp lệ và thuận tiện cho demo. Không cần component scan vì tất cả beans đã khai báo tường minh.

### 7.5. Chạy và kiểm chứng outcome ngay tại client

**`Main.java`**

```java
package com.example.contract;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService service = context.getBean(OrderService.class);
            ContractAspect aspect = context.getBean(ContractAspect.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(service));
            System.out.println("accepted=" + service.accept("A"));
            System.out.println("quantity=" + service.quantity());

            service.refresh();

            System.out.println(
                "nullableIsNull=" + (service.findNullable("missing") == null)
            );

            try {
                service.reject("X");
            } catch (IllegalArgumentException ex) {
                System.out.println("caught=" + ex.getMessage());
            }

            System.out.println("normalReturns=" + aspect.normalCount());
            System.out.println("exceptionalExits=" + aspect.failureCount());
        }
    }
}
```

Chạy tại root project với JDK 17+ và Gradle đã cài:

```bash
gradle run --quiet
```

Nếu đã có Gradle wrapper: `./gradlew run --quiet` trên Linux/macOS, `gradlew.bat run --quiet` trên Windows.

**Các dòng `System.out` của demo theo đúng thứ tự** (không kể logging khác của Gradle/Spring/JVM):

```text
proxy=true
accepted=accepted:A
quantity=12
target:refresh
nullableIsNull=true
caught=invalid:X
normalReturns=4
exceptionalExits=1
```

### 7.6. Đối chiếu kết quả với contract

| Lời gọi | Kết quả target | Caller thực sự nhận | Counter |
| --- | --- | --- | --- |
| `accept("A")` | `"accepted:A"` | Chuỗi y hệt | normal +1 |
| `quantity()` | `12` kiểu `int` | `12`, không bị đổi thành `0`/`null` | normal +1 |
| `refresh()` | `void` normal completion | Không có returned value | normal +1 |
| `findNullable("missing")` | `null` là outcome hợp lệ | `null` giữ nguyên | normal +1 |
| `reject("X")` | Throw `IllegalArgumentException("invalid:X")` | Bị bắt đúng kiểu và đúng message | exceptional +1 |
| **Tổng** | 5 method calls | **4 normal, 1 exception** | normal=4, exceptional=1 |

Demo này không minh họa `@AfterThrowing` trực tiếp; nó cho thấy **`@Around` có thể bảo tồn exceptional flow** bằng `throw original`. Không biến các counters thành “business success/failure” một cách vô điều kiện: method `findNullable` return `null` bình thường nhưng có thể biểu đạt “không tìm thấy”.

## 8. Vì sao không thay exception bằng fallback vô điều kiện?

Giả sử method khai báo:

```java
public Receipt charge(String orderId)
        throws PaymentGatewayException;
```

Contract có thể nói rằng `Receipt` chỉ tồn tại khi giao dịch đã được xác nhận, còn `PaymentGatewayException` báo rằng chưa xác nhận được trạng thái. Nếu advice bắt mọi lỗi và trả `new Receipt("fake")`, caller có thể coi giao dịch đã được xử lý, mặc dù tiền chưa được ghi nhận.

Đây là **ví dụ nghiệp vụ minh họa**; `Receipt` và `PaymentGatewayException` cần được định nghĩa trong dự án thực. Điều Spring-specific cần nhớ là **`@Around` cho phép thay cả return lẫn throw**. Chính khả năng đó đòi hỏi ràng buộc contract.

### 8.1. Fallback có chủ đích phải có ba điều kiện

1. **Policy rõ:** lỗi nào được xử lý, lỗi nào phải propagation. Không catch tất cả `Throwable` rồi coi là “không tìm thấy”.
2. **Giá trị/kiểu hợp lệ:** fallback thuộc miền return và được API công bố, ví dụ `Optional.empty()` cho một lookup cho phép thiếu dữ liệu.
3. **Side effects an toàn:** biết chắc target đã/chưa commit, có thể retry hay không, và cache/transaction/security advisors nhìn outcome thế nào.

Chỉ khi cả ba điều kiện được đáp ứng, short-circuit hoặc fallback trong `@Around` mới là một **hành vi nghiệp vụ được thiết kế**, không phải kỹ thuật né exception.

### 8.2. Mẫu chỉ fallback trên lỗi cụ thể

```java
@Around("execution(* com.example.catalog.CatalogService.find(..))")
public Object cacheFallback(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (CatalogUnavailableException ex) {
        return Optional.empty();
    }
}
```

**Đây là snippet minh họa**, không chạy độc lập: `CatalogService` và `CatalogUnavailableException` cần được định nghĩa, method `find(..)` **phải công bố `Optional<...>`** như một return type hợp lệ, và policy phải cho phép coi “catalog không khả dụng” là “không tìm thấy” — một quyết định mà nhiều hệ thống **không cho phép** vì hai trạng thái không tương đương.

Ví dụ này chỉ cho thấy **cách giới hạn catch theo exception type**. Không được dùng như một lời khuyên production tự động để hợp nhất `unavailable` và `absent`. Phần lớn trường hợp đúng hơn là propagation exception hoặc trả một result type có trạng thái **UNAVAILABLE** tường minh.

### 8.3. Return type fallback phải tương thích Java

`Optional.empty()` hợp lệ khi target method return `Optional<T>`. Nó **không** hợp lệ khi target method return `T`, `String` hoặc `int`. Với generic types và erased runtime, một mismatch có thể chỉ nổ ở caller, nên test cả compile-time contract và runtime proxy behavior.

### 8.4. Đừng “sửa lỗi” kiểu bằng ép cast liều

```java
return (Receipt) (Object) "fallback";
```

Đây là mã minh họa hành vi sai, có thể ném `ClassCastException`. Nó không tạo một `Receipt` hợp lệ. Nếu muốn fallback cho `Receipt`, phải tạo ra một **Receipt đúng nghĩa** theo contract, không đánh tráo ý nghĩa thành công chỉ bằng cách làm compiler chấp nhận.

## 9. Retry và proceed(): một thay đổi có thể nhân đôi side effects

`@Around` cho phép `proceed()` nhiều lần. Cơ chế này có thể dùng cho retry ở tầng AOP nhưng **chỉ khi đã định nghĩa rõ ràng semantics**.

Ví dụ nguy hiểm:

```java
@Around("execution(* com.example.payment.PaymentService.charge(..))")
public Object badRetry(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (RuntimeException ex) {
        return pjp.proceed(); // Thực thi lại, có thể charge thêm một lần.
    }
}
```

Nếu lần đầu DB commit xong nhưng network response bị mất, lần thứ hai có thể tạo giao dịch trùng. Spring AOP không tự phân biệt “failed before side effect” với “failed after side effect”. Retry cũng có thể tác động `@Transactional`/`@Cacheable`/`@Async` interceptors khác tùy advisor order.

**Thay vì retry mù**, cần:

- Phân loại transient/permanent errors.
- Idempotency key hoặc deduplication mechanism.
- Bounded attempts, backoff, timeouts.
- Quan sát transaction boundary; tránh retry bên trong một transaction đã rollback-only.
- Xác định exception cuối cùng vẫn được trả cho caller nếu hết attempts.
- Integration tests đo số lần downstream được gọi và trạng thái DB/message.
- Bảo đảm user/request context và security identity được truyền phù hợp khi async.

Nếu bài toán chỉ cần logging, đo thời gian hoặc counter, **không retry**; `proceed()` một lần là mặc định an toàn nhất.

## 10. Transaction và @Around: nuốt exception có thể đổi rollback semantics

Giả sử một Spring service dùng transaction interceptor:

```java
@Service
public class LedgerService {
    @Transactional
    public void transfer(String from, String to, long amount) {
        // Business operation với quy tắc transaction.
    }
}
```

Đây là code minh họa, không đầy đủ dependency/transaction manager. Nếu target method ném `RuntimeException` nhưng `@Around` catch rồi trả bình thường, chuyện transaction rollback tùy **thứ tự advisors**, tình trạng transaction hiện có, điểm lỗi và propagation:

- Nếu Spring transaction advice nhìn thấy **exception** trước khi exception bị aspect ngoài cùng nuốt, transaction có thể đã đánh dấu rollback hoặc kết thúc theo quy tắc riêng.
- Nếu transaction interceptor nhìn thấy **normal return** do một aspect nằm trong chain chuyển từ lỗi thành fallback, behavior có thể khác.
- `@Transactional` tự nó cần transaction manager và configuration, không được bật chỉ vì `@EnableAspectJAutoProxy`.

**Không kết luận “nuốt lỗi chắc chắn commit” hay “chắc chắn rollback”.** Điều đúng là **Spring transaction advisor quyết định trên outcome/exception nó thật sự quan sát được**, và `@Around` có thể làm thay outcome đó. Muốn xác nhận phải kiểm tra transaction manager, `@Order`, rollback rules và actual data state sau integration test.

### Sau commit có khác after-returning không?

Có. Một `@Around` quan sát target return bình thường hoặc một `@AfterReturning` chạy không phải cam kết DB transaction đã commit. Nếu cần side effect đáng tin sau commit, sử dụng transaction synchronization hoặc `@TransactionalEventListener(phase = AFTER_COMMIT)` theo đúng use case; không dùng một đoạn `catch` trong aspect để giả lập commit confirmation.

## 11. Spring security, caching và async: không biến lỗi thành “thành công”

### 11.1. Method security

Nếu Spring Method Security áp `@PreAuthorize` hoặc interceptor khác lên service, một `@Around` quá rộng có thể vô tình bao gồm method security policy. Không catch exception authorization rồi trả fallback `true` hoặc default DTO. **Security denial cần được giữ nguyên trừ khi có contract và policy bảo mật rất rõ.**

Thứ tự giữa custom aspect và method security advisors cần được kiểm tra; nếu advice đặt ngoài security interceptor mà cố bỏ `proceed()`, nó có thể **không cho security interceptor cơ hội chạy**, làm sai boundary. Tránh pointcut rộng áp lên tất cả framework beans/security methods.

### 11.2. Caching

Một cache aspect hoặc `@Cacheable` interceptor có thể short-circuit method theo contract cache. Nếu bạn thêm `@Around` trả giá trị mặc định khi backend lỗi, value đó có thể bị cache tùy thứ tự advisor chain/configuration; hậu quả là “fake success” tồn tại sau khi backend phục hồi.

Cần phân biệt “cache miss”, “data legitimately absent” và “backend unavailable”. Đừng coi ba trạng thái là `null` giống nhau.

### 11.3. CompletableFuture và async handle

Với method:

```java
public CompletableFuture<String> loadAsync(String id) {
    // Trả future ngay, công việc có thể kết thúc về sau.
}
```

`@Around` thấy `pjp.proceed()` trả một `CompletableFuture` **bình thường**, ngay cả nếu future ấy hoàn tất exceptional sau đó. `catch(Throwable)` quanh `proceed()` không tự bắt exception phát sinh bất đồng bộ bên trong future. Nếu policy cần error handling ở completion, phải dùng `whenComplete`/`handle` hoặc công cụ async tương ứng.

**Không thay một future bị fail bằng `CompletableFuture.completedFuture("ok")`** trừ khi API contract/policy thực sự định nghĩa fallback này.

### 11.4. Reactor Mono/Flux

Với reactive method trả `Mono<T>` hoặc `Flux<T>`, advice proxy nhìn thấy một **publisher object** lúc return. Lỗi có thể xuất hiện **khi subscribe**, sau khi `proceed()` đã trả bình thường. Dùng Reactor operators như `onErrorResume`/`doOnError` theo semantics reactive và lựa chọn policy rõ ràng; không mong `try/catch` trong `@Around` đồng bộ bắt mọi reactive error.

## 12. @AfterThrowing và @AfterReturning có phải lựa chọn tốt hơn?

Nếu yêu cầu chỉ là **quan sát** outcome mà không muốn thay return hoặc exception:

- `@AfterReturning`: chạy khi method execution đã kết thúc bình thường; có thể bind return value bằng `returning`.
- `@AfterThrowing`: chạy khi method execution thoát bằng exception; có thể bind thrown exception bằng `throwing`.
- `@After`: mang semantics finally.
- `@Around`: bao quanh và có quyền chủ động chuyển đổi outcome, nên cần cẩn trọng nhất.

Spring Framework hướng dẫn ưu tiên **advice ít quyền nhất đáp ứng yêu cầu**, vì như vậy tránh cả lớp bug quên `proceed()` hoặc trả sai value. Nếu chỉ cần đếm exception, `@AfterThrowing` an toàn thiết kế hơn `@Around` có `catch` tự chế.

Ví dụ observer với returning và throwing (không ghép vào demo nếu muốn giữ stdout):

```java
@AfterReturning(
    pointcut = "execution(* com.example.contract.OrderService.*(..))",
    returning = "result",
    argNames = "result"
)
public void afterResult(Object result) {
    // Quan sát kết quả trả về, không thay value.
}

@AfterThrowing(
    pointcut = "execution(* com.example.contract.OrderService.*(..))",
    throwing = "error",
    argNames = "error"
)
public void afterError(Throwable error) {
    // Quan sát lỗi, không tự biến exceptional exit thành success.
}
```

Đây là đoạn minh họa trong Spring-managed `@Aspect` có imports phù hợp. Tập method phù hợp của binding `Object result` có thể khác với advice không bind return type, nhất là `void`/primitives/generics; dùng integration test trên Spring version đang chạy.

Nếu cần recovery có chủ đích, `@Around` hoặc domain-level handler mới có quyền cần thiết, nhưng policy phải explicit và đủ test coverage.

## 13. Tách business fallback khỏi infrastructure advice khi có thể

Một thiết kế ổn định là để Spring service tự diễn đạt domain outcome:

```java
public Optional<InventoryItem> findAvailable(String sku) {
    // Optional.empty(): mặt hàng thực sự không có.
    // InventoryBackendUnavailableException: backend lỗi.
}
```

Như vậy Spring AOP `@Around` có thể **giữ nguyên returned Optional hoặc thrown backend exception**. Caller phân biệt legitimate absence và lỗi hạ tầng mà không phải đoán từ `null`/`false`.

Đây là đoạn minh họa; `InventoryItem`/`InventoryBackendUnavailableException` cần được định nghĩa trong ứng dụng thật. Nếu client yêu cầu fallback khi backend unavailable, đặt nó trong **một boundary có tên và contract**, ví dụ `InventoryQueryFacade.findCachedOrUnavailable()`, và kiểm thử:

- Value mới hay stale cache?
- Timeout bao nhiêu?
- Lỗi nào cho fallback, lỗi nào phải fail?
- Permission checks vẫn được thực thi?
- Response có biểu đạt degraded status?
- Những side effects nào đã hoặc chưa xảy ra?

Cách này tránh một `@Around` “tổng quát” âm thầm điều khiển mọi method của cả hệ thống.

## 14. Vì sao cùng @Around lại có thể chạy/không chạy?

Ngay cả một advice giữ nguyên contract cũng vô dụng nếu Spring AOP không intercept lời gọi.

| Hiện tượng | Giải thích trong Spring | Hướng kiểm tra |
| --- | --- | --- |
| `@Aspect` tồn tại nhưng không có advice | Class aspect chưa là bean hoặc AOP support chưa bật | `@Aspect` + `@Component` trong scan, hoặc `@Bean` + `@EnableAspectJAutoProxy`/Boot AOP starter |
| Method được gọi từ `this.method()` trên chính target | Self-invocation không quay qua Spring proxy | Tách sang injected collaborator bean hoặc đưa advice lên outer entry |
| Client dùng `new Service()` | Bỏ qua Spring container | Inject service bean từ context |
| `final` class với CGLIB | Không thể subclass | Dùng interface/JDK proxy nếu tương thích hoặc thay thiết kế |
| `final` method với CGLIB | Không override được method | Dùng entry method interceptable, kiểm thử |
| Spring trả JDK proxy nhưng caller muốn gọi method không có trên interface | Proxy không expose method ấy | Đưa vào interface hoặc chọn proxy strategy thích hợp |
| `AopUtils.isAopProxy(bean)=true` nhưng advice không chạy cho một method | Advisor/pointcut không match hoặc call path khác | Kiểm tra expression, bean target type, actual advice invocation |
| Exception bị mất sau thêm @Around | Advice catch rồi trả fallback/sai loại exception | Trace catch/return branch, assert expected throwable ở caller |
| Service trả sai type sau advice | Advice tạo default incompatible với target declared type | Return original proceed result, test primitive/generic/null branches |

### Phép đo tối thiểu

```java
OrderService bean = context.getBean(OrderService.class);
System.out.println(AopUtils.isAopProxy(bean));
bean.reject("probe");
```

Đây là đoạn minh họa **có thể ném IllegalArgumentException** nên cần try/catch nếu không muốn test kết thúc với exception; nó dùng `OrderService` và `AopUtils` từ demo. Proxy true chỉ chứng minh có Spring AOP proxy, không khẳng định pointcut của aspect đang xét được dùng — phải quan sát counter/exception outcome.

## 15. Viết integration tests để khóa đúng contract

### 15.1. Không unit-test riêng aspect rồi cho rằng Spring AOP đã được kiểm chứng

`new ContractAspect().preserve(fakePjp)` có thể test logic Java của advice, nhưng không xác nhận:

- `@Aspect` có được Spring đăng ký bean không;
- `@EnableAspectJAutoProxy` có hoạt động không;
- Method target có được proxy không;
- Pointcut có match không;
- Exception/return type có vượt qua JDK/CGLIB proxy đúng không.

Cần ít nhất một integration test dùng `ApplicationContext` thật và gọi bean qua Spring reference.

### 15.2. Assertions nền tảng

Với demo `AppConfig`:

```java
try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
    OrderService service = context.getBean(OrderService.class);
    ContractAspect aspect = context.getBean(ContractAspect.class);

    if (!"accepted:X".equals(service.accept("X"))) {
        throw new AssertionError("String return changed");
    }
    if (service.quantity() != 12) {
        throw new AssertionError("Primitive result changed");
    }
    service.refresh();
    if (service.findNullable("missing") != null) {
        throw new AssertionError("Nullability changed");
    }

    try {
        service.reject("Y");
        throw new AssertionError("Expected IllegalArgumentException");
    } catch (IllegalArgumentException ex) {
        if (!"invalid:Y".equals(ex.getMessage())) {
            throw new AssertionError("Wrong failure propagated", ex);
        }
    }

    if (aspect.normalCount() != 4 || aspect.failureCount() != 1) {
        throw new AssertionError("Wrong @Around outcome observations");
    }
}
```

Đoạn này là **biến thể test minh họa** dùng classes trong demo và import `AnnotationConfigApplicationContext`; không ghép vào `Main` nếu muốn giữ stdout mục 7.5.

### 15.3. Kiểm chứng cùng exception object nếu cần

Trong tình huống muốn xác nhận tuyệt đối `throw original` giữ object identity, có thể cấu hình target throw một preconstructed `RuntimeException` instance (thay vì tạo mới trong method), rồi caller dùng `==` để so exception object. Nhưng phải làm fixture test riêng, tránh giữ exception có stack trace cũ dùng chung trong production.

`==` chứng minh identity của reference; so message/type chỉ chứng minh behavior quan sát được phần nào. Cả hai đều có giá trị trong kiểm thử đúng mục tiêu.

### 15.4. Tests cho short-circuit/retry/fallback

Nếu ứng dụng **có chủ đích** thay return/exception:

- Assert điều kiện nào bỏ `proceed()`, target call count = 0 cho nhánh ấy.
- Assert retry attempts được giới hạn và số side effects chính xác.
- Assert lỗi “permanent” không retry và vẫn propagate.
- Assert timeout/cancellation không chuyển thành success giả.
- Assert fallback đúng API type, nullability và business status.
- Assert transaction rollback/commit và security denial thực sự.
- Assert future/reactive completion behavior bằng test async/reactive, không dựa chỉ vào synchronous advice output.

Không chấp nhận test chỉ gọi happy path rồi kết luận aspect đủ an toàn.

## 16. Checklist code review cho @Around

- [ ] `@Aspect` class và target service là Spring-managed beans, pointcut match và caller dùng proxy?
- [ ] Advice `@Around` có `ProceedingJoinPoint`, return type `Object`/compatible và khai báo `throws Throwable`?
- [ ] Nếu không chủ động short-circuit, luôn gọi `proceed()` **đúng một lần**?
- [ ] Normal return được giữ nguyên (`String`/DTO/`int`/`boolean`/`void`/`null`/`Optional`)?
- [ ] Target exception được ném lại **nguyên type, message và cause** khi không có explicit policy dịch lỗi?
- [ ] Có `catch(Throwable)` nhưng không `return null`/`0`/`false` vô điều kiện?
- [ ] Không chuyển exception authorization/transaction thành normal return ngoài contract?
- [ ] Instrumentation best-effort có error policy rõ, không tự tạo exception làm biến outcome?
- [ ] Nếu transform/fallback, API contract và return type đã cho phép và được test?
- [ ] Nếu retry, có idempotency key, bounded attempts và test side effects/transaction?
- [ ] Đã phân biệt null hợp lệ với null do lỗi bị nuốt, void normal với short-circuit?
- [ ] Đã kiểm tra Spring proxy kind và giới hạn self-invocation/final method?
- [ ] Với future/reactive, không nhận nhầm “method trả wrapper” là “tác vụ hoàn tất thành công”?
- [ ] Integration test dùng Spring context thật và assert cả return value, exception, counters, side effects?
- [ ] Pointcut không quá rộng và không áp nhầm security/framework beans?

**Ghi nhớ:** `@Around` trong Spring AOP là một **ranh giới có thể thay kết quả và ngoại lệ**. Mẫu an toàn khi chỉ quan sát là **`return pjp.proceed()` hoặc giữ nguyên result; catch rồi `throw original` nếu cần ghi lỗi**. Không trả mặc định chỉ để “tránh lỗi” nếu API contract chưa cho phép. Chỉ short-circuit, retry, fallback hoặc đổi exception khi **nghiệp vụ yêu cầu rõ và kiểm thử đầy đủ**.

## Tài liệu Spring chính thức

- [Spring Framework 6.2 — Declaring Advice / Around Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework 6.2 — AOP Concepts and Advice Types](https://docs.spring.io/spring-framework/reference/6.2/core/aop/introduction-defn.html)
- [Spring Framework 6.2 — Proxying Mechanisms and Self-invocation](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework 6.2 — Transaction-bound Events](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/event.html)


</details>

- [Quay lại đầu trang](#back-to-top)
